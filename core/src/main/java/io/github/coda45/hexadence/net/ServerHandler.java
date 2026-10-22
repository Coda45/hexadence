package io.github.coda45.hexadence.net;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;

// One instance per connected client. It handles that client's handshake,
// which puts it in a room, then passes everything it sends to that room.
//
// All event methods run on GameServer's single game thread, the same thread
// that runs each simulation tick, so none of them can overlap.
public class ServerHandler extends ChannelInboundHandlerAdapter {

    private final GameServer server;
    private GameRoom room;          // null until the handshake is accepted
    private int playerId = -1;

    public ServerHandler(GameServer server) {
        this.server = server;
    }

    @Override
    public void channelRead(
        ChannelHandlerContext ctx,
        Object msg
    ) {
        if (room == null) {
            handleHandshake(ctx, msg);
            return;
        }

        // The decoder already turned the bytes into objects, so there is
        // nothing to release here.
        if (msg instanceof PlayerCommand command) {
            if (!PlayerCommandValidator.isValid(command)) {
                System.err.println(
                    "Rejected invalid command from "
                        + ctx.channel().remoteAddress()
                );
                ctx.close();
                return;
            }

            server.receive(room, playerId, command);
        } else if (msg instanceof StartGame) {
            server.start(room, playerId);
        } else if (msg instanceof KeepAlive) {
            // Nothing to do: receiving it already reset the read timeout.
        } else {
            System.err.println(
                "Rejected unexpected message from "
                    + ctx.channel().remoteAddress()
            );
            ctx.close();
        }
    }

    private void handleHandshake(
        ChannelHandlerContext ctx,
        Object msg
    ) {
        if (!(msg instanceof ClientHello hello)) {
            System.err.println(
                "Rejected client that did not send a protocol handshake"
            );
            ctx.close();
            return;
        }

        if (hello.protocolVersion() != GameCodec.PROTOCOL_VERSION) {
            System.err.println(
                "Rejected incompatible protocol version "
                    + hello.protocolVersion()
            );
            reject(ctx, JoinRejected.Reason.WRONG_VERSION);
            return;
        }

        if (!server.usesMap(hello.mapId())) {
            System.err.println(
                "Rejected client using incompatible map: "
                    + hello.mapId()
            );
            reject(ctx, JoinRejected.Reason.WRONG_MAP);
            return;
        }

        GameRoom target;

        if (hello.wantsNewRoom()) {
            target = server.createRoom();

            if (target == null) {
                reject(ctx, JoinRejected.Reason.SERVER_FULL);
                return;
            }
        } else {
            target = server.findRoom(hello.roomCode());

            if (target == null) {
                reject(ctx, JoinRejected.Reason.ROOM_NOT_FOUND);
                return;
            }

            JoinRejected.Reason problem = target.joinProblem();

            if (problem != null) {
                reject(ctx, problem);
                return;
            }
        }

        room = target;
        playerId = room.join(ctx.channel());

        ctx.writeAndFlush(new Welcome(
            GameCodec.PROTOCOL_VERSION,
            playerId,
            room.code()
        ));
        room.broadcastLobbyState();
    }

    // Tells the client why, then hangs up once that message is sent.
    private static void reject(
        ChannelHandlerContext ctx,
        JoinRejected.Reason reason
    ) {
        ctx.writeAndFlush(new JoinRejected(reason))
            .addListener(ChannelFutureListener.CLOSE);
    }

    // The connection closed, whether the client quit or the network dropped.
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (room != null) {
            server.leave(room, playerId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            System.out.println(
                "Closing silent connection from " + ctx.channel().remoteAddress()
            );
        } else {
            cause.printStackTrace();
        }

        ctx.close();   // closing triggers channelInactive, which removes the player
    }
}
