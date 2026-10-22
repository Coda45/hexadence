package io.github.coda45.hexadence.net;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;
import io.github.coda45.hexadence.world.WorldEvent;
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

// The authoritative game server. It owns one ArenaWorld, advances it at
// 60 Hz, and sends snapshots plus reliable semantic events to each client.
//
// It uses no libGDX: the map is loaded elsewhere and passed in as an
// ArenaDefinition, which is plain data.
//
// Threading: every ServerHandler callback and every tick runs on the single
// "game thread" below. The fields in this class are only touched from that
// thread, so they need no locks.
public class GameServer {

    public static final int DEFAULT_PORT = 7777;
    public static final int MAX_PLAYERS = 4;

    private static final int MAX_PENDING_EVENTS_PER_CLIENT = 64;
    // Clients send input every tick, so a connection that has been silent
    // this long is dead (closed laptop, lost Wi-Fi) or never was a game client
    // (port scanners on the internet). Closing it frees the player slot.
    // Generous, because a minimized game window may stop sending for a while.
    static final int READ_TIMEOUT_SECONDS = 30;
    // One simulation step is 1/60 s, matching ArenaWorld.STEP.
    private static final long TICK_NANOS = 1_000_000_000L / 60L;

    private final int port;
    private final String mapId;
    private final ArenaWorld world;

    // Game-thread state: which connection and which pending input belong
    // to each player ID.
    private final Map<Integer, Channel> channels = new LinkedHashMap<>();
    private final Map<Integer, PendingCommand> pending = new LinkedHashMap<>();
    private final Map<Integer, Deque<WorldEvent>> pendingEvents = new LinkedHashMap<>();

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
        this.world = new ArenaWorld(definition);

        // ArenaWorld always creates player 0 for single-player. The server has
        // no local player; every player here is a connected client.
        world.removePlayer(0);
    }

    // ---- Called by ServerHandler, always on the game thread ----

    int join(Channel channel) {
        int roomCapacity = Math.min(
            MAX_PLAYERS,
            world.getPlayerCapacity()
        );

        if (channels.size() >= roomCapacity) {
            System.out.println("Rejected " + channel.remoteAddress() + ": game is full");
            channel.close();
            return -1;
        }
        int id = world.addPlayer();

        channels.put(id, channel);
        pending.put(id, new PendingCommand());
        pendingEvents.put(id, new ArrayDeque<>());

        System.out.println("Player " + id + " joined from " + channel.remoteAddress());
        return id;
    }

    void receive(int playerId, PlayerCommand command) {
        PendingCommand slot = pending.get(playerId);

        if (slot != null) {
            slot.merge(command);
        }
    }

    void leave(int playerId) {
        channels.remove(playerId);
        pending.remove(playerId);
        world.removePlayer(playerId);
        pendingEvents.remove(playerId);

        System.out.println("Player " + playerId + " left");
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

        System.out.println("Hexadence server listening on port " + port);
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

    // A repeating task stops forever if it throws once, so catch everything.
    private void tickSafely() {
        try {
            tick();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void tick() {
        Map<Integer, PlayerCommand> commands = new LinkedHashMap<>();

        for (Map.Entry<Integer, PendingCommand> entry : pending.entrySet()) {
            commands.put(entry.getKey(), entry.getValue().take());
        }

        StepResult result = world.step(commands);

        if (channels.isEmpty()) {
            return;
        }

        queueEvents(result);

        WorldSnapshot snapshot = SnapshotFactory.capture(world, result);

        for (Map.Entry<Integer, Channel> entry : channels.entrySet()) {
            int playerId = entry.getKey();
            Channel channel = entry.getValue();

            // Snapshots are replaceable, so do not queue them for a slow client.
            if (!channel.isActive() || !channel.isWritable()) {
                continue;
            }

            // Send current persistent state first.
            channel.write(snapshot);

            // Then send every reliable event that client has not received.
            Deque<WorldEvent> events =
                pendingEvents.get(playerId);

            if (events != null) {
                while (!events.isEmpty()) {
                    channel.write(events.removeFirst());
                }
            }

            channel.flush();
        }
    }

    boolean usesMap(String requestedMapId) {
        return mapId.equals(requestedMapId);
    }

    private void queueEvents(StepResult result) {
        for (WorldEvent event : result.events()) {
            for (Map.Entry<Integer, Channel> entry : channels.entrySet()) {
                int playerId = entry.getKey();
                Channel channel = entry.getValue();

                Deque<WorldEvent> events =
                    pendingEvents.get(playerId);

                if (events == null) {
                    continue;
                }

                if (events.size() >= MAX_PENDING_EVENTS_PER_CLIENT) {
                    System.err.println(
                        "Disconnecting player "
                            + playerId
                            + ": reliable event queue overflow"
                    );
                    channel.close();
                    continue;
                }

                events.addLast(event);
            }
        }
    }

    // Combines every command received from one client since the last tick.
    // Movement and aim keep the newest value; cast requests are OR-ed so a
    // click is never lost if two commands arrive before one tick. This is the
    // same latching PlayerInputController does for mouse clicks.
    static final class PendingCommand {
        // At 60 Hz this allows approximately 250 ms without a new command.
        static final int TIMEOUT_TICKS = 15;

        private float moveX;
        private float moveY;
        private float aimX;
        private float aimY;
        private boolean castEmber;
        private boolean castWater;
        private boolean castLightning;
        private boolean receivedSinceLastTake;
        private int ticksWithoutCommand = TIMEOUT_TICKS;

        void merge(PlayerCommand command) {
            moveX = command.moveX();
            moveY = command.moveY();
            aimX = command.aimX();
            aimY = command.aimY();

            castEmber |= command.castEmber();
            castWater |= command.castWater();
            castLightning |= command.castLightning();
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
                command = new PlayerCommand(
                    moveX,
                    moveY,
                    aimX,
                    aimY,
                    castEmber,
                    castWater,
                    castLightning
                );
            }
            receivedSinceLastTake = false;
            // Cast requests are one-shot. Movement receives the timeout grace.
            castEmber = false;
            castWater = false;
            castLightning = false;

            return command;
        }
    }
}
