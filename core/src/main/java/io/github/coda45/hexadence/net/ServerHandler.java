package io.github.coda45.hexadence.net;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;

// One instance per connected client. It reacts to that client's connection
// events and hands everything to GameServer, which owns the world.
//
// All three event methods run on GameServer's single game thread, the same
// thread that runs each simulation tick, so none of them can overlap.
public class ServerHandler extends ChannelInboundHandlerAdapter {

    private final GameServer server;
    private int playerId = -1;

    public ServerHandler(GameServer server) {
        this.server = server;
    }

    // The client sent its input for a tick. The decoder already turned the
    // bytes into a PlayerCommand, so there is nothing to release here.
    @Override
    public void channelRead(
        ChannelHandlerContext ctx,
        Object msg
    ) {
        if (playerId < 0) {
            handleHandshake(ctx, msg);
            return;
        }
        if (!(msg instanceof PlayerCommand command)) {
            System.err.println(
                "Rejected unexpected message from "
                    + ctx.channel().remoteAddress()
            );

            ctx.close();
            return;
        }

        if (!PlayerCommandValidator.isValid(command)) {
            System.err.println(
                "Rejected invalid command from "
                    + ctx.channel().remoteAddress()
            );

            ctx.close();
            return;
        }

        server.receive(playerId, command);
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
            ctx.close();
            return;
        }

        if (!server.usesMap(hello.mapId())) {
            System.err.println(
                "Rejected client using incompatible map: "
                    + hello.mapId()
            );
            ctx.close();
            return;
        }

        playerId = server.join(ctx.channel());

        if (playerId >= 0) {
            ctx.writeAndFlush(new Welcome(
                GameCodec.PROTOCOL_VERSION,
                playerId
            ));
        }
    }

    // The connection closed, whether the client quit or the network dropped.
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (playerId >= 0) {
            server.leave(playerId);
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