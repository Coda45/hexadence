package io.github.coda45.hexadence.net;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

// The authoritative game server. It owns the one real ArenaWorld, advances it
// at 60 Hz, and sends every connected client a snapshot after each tick.
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

    // One simulation step is 1/60 s, matching ArenaWorld.STEP.
    private static final long TICK_NANOS = 1_000_000_000L / 60L;

    private final int port;
    private final ArenaWorld world;

    // Game-thread state: which connection and which pending input belong
    // to each player ID.
    private final Map<Integer, Channel> channels = new LinkedHashMap<>();
    private final Map<Integer, PendingCommand> pending = new LinkedHashMap<>();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup gameThread;
    private Channel serverChannel;

    public GameServer(ArenaDefinition definition, int port) {
        this.port = port;
        this.world = new ArenaWorld(definition);

        // ArenaWorld always creates player 0 for single-player. The server has
        // no local player; every player here is a connected client.
        world.removePlayer(0);
    }

    // ---- Called by ServerHandler, always on the game thread ----

    int join(Channel channel) {
        if (channels.size() >= MAX_PLAYERS) {
            System.out.println("Rejected " + channel.remoteAddress() + ": game is full");
            channel.close();
            return -1;
        }
        int id = world.addPlayer();

        channels.put(id, channel);
        pending.put(id, new PendingCommand());

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

        WorldSnapshot snapshot = SnapshotFactory.capture(world, result);

        for (Channel channel : channels.values()) {
            // Skip a client whose connection is backed up rather than
            // letting its queue grow without limit.
            if (channel.isWritable()) {
                channel.writeAndFlush(snapshot);
            }
        }
    }

    // Combines every command received from one client since the last tick.
    // Movement and aim keep the newest value; cast requests are OR-ed so a
    // click is never lost if two commands arrive before one tick. This is the
    // same latching PlayerInputController does for mouse clicks.
    private static final class PendingCommand {
        private float moveX;
        private float moveY;
        private float aimX;
        private float aimY;
        private boolean castEmber;
        private boolean castWater;
        private boolean castLightning;

        void merge(PlayerCommand command) {
            moveX = command.moveX();
            moveY = command.moveY();
            aimX = command.aimX();
            aimY = command.aimY();

            castEmber |= command.castEmber();
            castWater |= command.castWater();
            castLightning |= command.castLightning();
        }

        PlayerCommand take() {
            PlayerCommand command = new PlayerCommand(
                moveX, moveY, aimX, aimY,
                castEmber, castWater, castLightning
            );

            // Held movement and aim carry over; one-shot casts do not.
            castEmber = false;
            castWater = false;
            castLightning = false;

            return command;
        }
    }
}