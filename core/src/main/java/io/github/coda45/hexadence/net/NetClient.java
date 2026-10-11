package io.github.coda45.hexadence.net;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.world.WorldEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

// The game's side of a connection to a GameServer. Every networked player uses
// one, including a host connected to an embedded server in the same process.
//
// Netty delivers messages on its own thread, while the game reads them from
// the render thread. The fields below are all thread-safe holders, so the game
// can poll them at any time without locking.
public class NetClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final String host;
    private final int port;
    private final String mapId;

    // Netty lifecycle resources, owned by this client.
    private EventLoopGroup group;
    private Channel channel;

    // Cross-thread state written by Netty and read by the render thread.
    private volatile int playerId = -1;
    private final AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
    private final ConcurrentLinkedQueue<WorldEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    public NetClient(String host, int port, String mapId) {
        this.host = host;
        this.port = port;
        this.mapId = mapId;
    }

    // Blocks until connected or until the attempt fails (the exception says why).
    public void connect() throws InterruptedException {
        group = new NioEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();

            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        GameCodec.install(ch.pipeline());
                        ch.pipeline().addLast(new Handler());
                    }
                });

            channel = bootstrap.connect(host, port).sync().channel();
        } catch (Exception e) {
            // Do not leave the event loop thread running after a failed attempt.
            group.shutdownGracefully();
            throw e;
        }
    }

    // Sends one tick of input. Safe to call from the render thread.
    public void send(PlayerCommand command) {
        if (isConnected()) {
            channel.writeAndFlush(command);
        }
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    // -1 until the server has said which player this client controls.
    public int getPlayerId() {
        return playerId;
    }

    // Null until the first snapshot arrives.
    public WorldSnapshot getLatestSnapshot() {
        return latestSnapshot.get();
    }

    // True once the game has everything it needs to draw: an ID and a snapshot.
    public boolean isReady() {
        return playerId >= 0 && latestSnapshot.get() != null;
    }

    // Returns every reliable event received since the last call, oldest first.
    public List<WorldEvent> drainEvents() {
        List<WorldEvent> drained = new ArrayList<>();
        WorldEvent event;

        while ((event = pendingEvents.poll()) != null) {
            drained.add(event);
        }

        return drained;
    }

    public void close() {
        if (channel != null) {
            channel.close();
        }

        if (group != null) {
            group.shutdownGracefully();
        }
    }

    // Receives whatever the server sends. The decoder has already turned the
    // bytes into Welcome and WorldSnapshot objects.
    private final class Handler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(
                new ClientHello(GameCodec.PROTOCOL_VERSION, mapId)
            );
        }

        @Override
        public void channelRead(
            ChannelHandlerContext ctx,
            Object msg
        ) {
            if (msg instanceof Welcome welcome) {
                if (welcome.protocolVersion() != GameCodec.PROTOCOL_VERSION) {
                    System.err.println(
                        "Server uses incompatible protocol version "
                            + welcome.protocolVersion()
                    );
                    ctx.close();
                    return;
                }

                playerId = welcome.playerId();
            } else if (msg instanceof WorldSnapshot snapshot) {
                latestSnapshot.set(snapshot);
            } else if (msg instanceof WorldEvent event) {
                pendingEvents.add(event);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("Disconnected from server");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
