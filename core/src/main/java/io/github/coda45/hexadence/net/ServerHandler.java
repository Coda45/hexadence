package io.github.coda45.hexadence.net;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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

    // A client just connected: give it a player, then tell it which one.
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        playerId = server.join(ctx.channel());
        
        if (playerId >= 0) {
            ctx.writeAndFlush(new Welcome(playerId));
        }
    }

    // The client sent its input for a tick. The decoder already turned the
    // bytes into a PlayerCommand, so there is nothing to release here.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof PlayerCommand command) {
            server.receive(playerId, command);
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
        cause.printStackTrace();
        ctx.close();   // closing triggers channelInactive, which removes the player
    }
}