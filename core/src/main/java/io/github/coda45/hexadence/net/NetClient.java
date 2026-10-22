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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

// The game's side of a connection to a GameServer.
//
// Netty delivers messages on its own thread, while the game reads them from
// the render thread. The fields below are all thread-safe holders, so the game
// can poll them at any time without locking.
public class NetClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    // Well under the server's 30 s read timeout.
    private static final int KEEP_ALIVE_SECONDS = 5;

    private final String host;
    private final int port;
    private final ClientHello hello;

    // Netty lifecycle resources, owned by this client.
    private EventLoopGroup group;
    private Channel channel;

    // Cross-thread state written by Netty and read by the render thread.
    private volatile int playerId = -1;
    private volatile String roomCode;
    private volatile JoinRejected.Reason rejection;
    private volatile LobbyState lobbyState;
    private volatile boolean gameStarted;
    private volatile boolean roomClosed;
    private volatile boolean disconnected;
    private final AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
    private final ConcurrentLinkedQueue<WorldEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    public NetClient(String host, int port, ClientHello hello) {
        this.host = host;
        this.port = port;
        this.hello = hello;
    }

    // Blocks until connected or until the attempt fails (the exception says why).
    // The handshake then continues in the background: poll getRoomCode() or
    // getRejection() to see how it went.
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
                        // Fires an event when nothing has been sent for a
                        // while; Handler answers it with a KeepAlive.
                        ch.pipeline().addLast(
                            new IdleStateHandler(0, KEEP_ALIVE_SECONDS, 0)
                        );
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

    // Asks the server to start the match. Only the host's request counts.
    public void requestStart() {
        if (isConnected()) {
            channel.writeAndFlush(new StartGame());
        }
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    // -1 until the server has said which player this client controls.
    public int getPlayerId() {
        return playerId;
    }

    // Null until the server accepts this client into a room.
    public String getRoomCode() {
        return roomCode;
    }

    // Null unless the server refused to let this client in.
    public JoinRejected.Reason getRejection() {
        return rejection;
    }

    // Null until the first lobby update arrives.
    public LobbyState getLobbyState() {
        return lobbyState;
    }

    // True once the host has started the match.
    public boolean isGameStarted() {
        return gameStarted;
    }

    // True if the host left the lobby and the room no longer exists.
    public boolean isRoomClosed() {
        return roomClosed;
    }

    // True once the connection has ended for any reason.
    public boolean isDisconnected() {
        return disconnected;
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
    // bytes into message objects.
    private final class Handler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(hello);
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
                    rejection = JoinRejected.Reason.WRONG_VERSION;
                    ctx.close();
                    return;
                }

                playerId = welcome.playerId();
                roomCode = welcome.roomCode();
            } else if (msg instanceof WorldSnapshot snapshot) {
                latestSnapshot.set(snapshot);
            } else if (msg instanceof WorldEvent event) {
                pendingEvents.add(event);
            } else if (msg instanceof LobbyState state) {
                lobbyState = state;
            } else if (msg instanceof GameStarted) {
                gameStarted = true;
            } else if (msg instanceof JoinRejected rejected) {
                rejection = rejected.reason();
            } else if (msg instanceof RoomClosed) {
                roomClosed = true;
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object event)
            throws Exception {

            if (event instanceof IdleStateEvent) {
                ctx.writeAndFlush(new KeepAlive());
            } else {
                super.userEventTriggered(ctx, event);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            disconnected = true;
            System.out.println("Disconnected from server");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
