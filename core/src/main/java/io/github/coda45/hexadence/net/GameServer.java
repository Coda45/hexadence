package io.github.coda45.hexadence.net;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.coda45.hexadence.input.CastRequest;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

// The authoritative game server. It hosts any number of rooms, each its own
// match with its own ArenaWorld (see GameRoom), and advances every running
// room at 60 Hz.
//
// It uses no libGDX: the map is loaded elsewhere and passed in as an
// ArenaDefinition, which is plain data. Every room uses that same arena.
//
// Threading: every ServerHandler callback and every tick runs on the single
// "game thread" below. The fields in this class, and every GameRoom, are
// only touched from that thread, so they need no locks.
public class GameServer {

    public static final int DEFAULT_PORT = 7777;
    public static final int MAX_PLAYERS = 4;
    // Caps memory use: each room holds a world even before its match starts.
    static final int MAX_ROOMS = 50;

    // Clients send input every tick in a match and a KeepAlive every few
    // seconds otherwise, so a connection silent this long is dead (closed
    // laptop, lost Wi-Fi) or never was a game client (port scanners on the
    // internet). Closing it frees the player slot.
    static final int READ_TIMEOUT_SECONDS = 30;

    // One simulation step is 1/60 s, matching ArenaWorld.STEP.
    private static final long TICK_NANOS = 1_000_000_000L / 60L;

    private final int port;
    private final String mapId;
    private final ArenaDefinition definition;

    // Game-thread state.
    private final Map<String, GameRoom> rooms = new HashMap<>();
    private final RoomCodes codes = new RoomCodes();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup gameThread;
    private Channel serverChannel;

    public GameServer(
        ArenaDefinition definition,
        String mapId,
        int port
    ) {
        this.port = port;
        this.mapId = Objects.requireNonNull(mapId);
        this.definition = Objects.requireNonNull(definition);
    }

    // ---- Called by ServerHandler, always on the game thread ----

    boolean usesMap(String requestedMapId) {
        return mapId.equals(requestedMapId);
    }

    // A new empty room, or null if the server already has MAX_ROOMS.
    GameRoom createRoom() {
        if (rooms.size() >= MAX_ROOMS) {
            return null;
        }

        String code = codes.next(rooms.keySet());
        GameRoom room = new GameRoom(code, definition, mapId);
        rooms.put(code, room);

        System.out.println("[room " + code + "] Created ("
            + rooms.size() + " room(s) open)");
        return room;
    }

    // The room with this code, or null. Accepts codes as players type them.
    GameRoom findRoom(String typedCode) {
        return rooms.get(RoomCodes.normalize(typedCode));
    }

    void receive(GameRoom room, int playerId, PlayerCommand command) {
        room.receive(playerId, command);
    }

    void start(GameRoom room, int playerId) {
        room.start(playerId);
    }

    void selectLoadout(GameRoom room, int playerId, SelectLoadout selection) {
        room.selectLoadout(playerId, selection);
    }

    void leave(GameRoom room, int playerId) {
        room.leave(playerId);
        forgetIfFinished(room);
    }

    // A room nobody joined, for example because its creator's Welcome
    // could not be sent, must not linger.
    void forgetIfFinished(GameRoom room) {
        if (room.isFinished() && rooms.get(room.code()) == room) {
            rooms.remove(room.code());
            System.out.println("[room " + room.code() + "] Removed ("
                + rooms.size() + " room(s) open)");
        }
    }

    int roomCount() {
        return rooms.size();
    }

    // ---- Lifecycle ----

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        gameThread = new DefaultEventExecutorGroup(1);

        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            // Send small packets immediately instead of waiting to batch them.
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(
                        new ReadTimeoutHandler(READ_TIMEOUT_SECONDS)
                    );
                    GameCodec.install(ch.pipeline());
                    // Passing gameThread runs this handler on the game thread
                    // instead of the connection's own I/O thread.
                    ch.pipeline().addLast(
                        gameThread,
                        new ServerHandler(GameServer.this)
                    );
                }
            });

        try {
            ChannelFuture bound = bootstrap.bind(port).sync();
            serverChannel = bound.channel();
        } catch (Exception e) {
            // Port already in use, for example. Do not leave threads running.
            stop();
            throw e;
        }

        gameThread.next().scheduleAtFixedRate(
            this::tickSafely,
            0,
            TICK_NANOS,
            TimeUnit.NANOSECONDS
        );

        System.out.println("Hexadence server listening on port " + boundPort());
    }

    // The port actually in use. Differs from the requested one only when
    // the server was created with port 0, which tests use to get a free port.
    public int boundPort() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    // Blocks until the server channel closes
    public void awaitClose() throws InterruptedException {
        serverChannel.closeFuture().sync();
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }

        if (gameThread != null) {
            gameThread.shutdownGracefully();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    // ---- The simulation loop ----

    // A repeating task stops forever if it throws once, so catch everything,
    // and catch per room so one broken match cannot stop the others.
    private void tickSafely() {
        List<GameRoom> snapshot = new ArrayList<>(rooms.values());

        for (GameRoom room : snapshot) {
            try {
                room.tick();
            } catch (Throwable t) {
                System.err.println("[room " + room.code() + "] Tick failed");
                t.printStackTrace();
            }
        }
    }

    // Combines every command received from one client since the last tick.
    // Movement and aim keep the newest value; the first cast request for each
    // slot is retained so a click is never lost or redirected if two commands
    // arrive before one tick. This matches PlayerInputController's latching.
    static final class PendingCommand {
        // At 60 Hz this allows approximately 250 ms without a new command.
        static final int TIMEOUT_TICKS = 15;

        private float moveX;
        private float moveY;
        private float aimX;
        private float aimY;
        private final CastRequest[] casts = new CastRequest[Loadout.SIZE];
        private boolean receivedSinceLastTake;
        private int ticksWithoutCommand = TIMEOUT_TICKS;

        void merge(PlayerCommand command) {
            moveX = command.moveX();
            moveY = command.moveY();
            aimX = command.aimX();
            aimY = command.aimY();

            for (CastRequest cast : command.casts()) {
                // Preserve the target associated with the first request for
                // this slot. Later movement-only packets cannot redirect it.
                if (casts[cast.slot()] == null) {
                    casts[cast.slot()] = cast;
                }
            }
            receivedSinceLastTake = true;
        }

        PlayerCommand take() {
            if (receivedSinceLastTake) {
                ticksWithoutCommand = 0;
            } else {
                ticksWithoutCommand = Math.min(
                    TIMEOUT_TICKS,
                    ticksWithoutCommand + 1
                );
            }

            PlayerCommand command;

            if (ticksWithoutCommand >= TIMEOUT_TICKS) {
                command = PlayerCommand.IDLE;
            } else {
                List<CastRequest> requestedCasts = new ArrayList<>(Loadout.SIZE);

                for (int slot = 0; slot < casts.length; slot++) {
                    if (casts[slot] != null) {
                        requestedCasts.add(casts[slot]);
                    }
                }

                command = new PlayerCommand(
                    moveX,
                    moveY,
                    aimX,
                    aimY,
                    requestedCasts
                );
            }
            receivedSinceLastTake = false;
            // Cast requests are one-shot. Movement receives the timeout grace.
            for (int slot = 0; slot < casts.length; slot++) {
                casts[slot] = null;
            }

            return command;
        }
    }
}
