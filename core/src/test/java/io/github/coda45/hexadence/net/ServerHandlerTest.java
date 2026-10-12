package io.github.coda45.hexadence.net;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.SpawnPoint;
import io.netty.channel.embedded.EmbeddedChannel;

// ServerHandler on an in-memory channel: no sockets, no threads.
class ServerHandlerTest {

    private static final String MAP = "test_map";

    private static GameServer newServer() {
        return new GameServer(
            new ArenaDefinition(
                new ArenaCollision(new float[10][10]),
                List.of(new SpawnPoint(2f, 3f), new SpawnPoint(6f, 6f)),
                List.of()
            ),
            MAP,
            0
        );
    }

    private static EmbeddedChannel channelFor(GameServer server) {
        return new EmbeddedChannel(new ServerHandler(server));
    }

    private static ClientHello hello() {
        return new ClientHello(GameCodec.PROTOCOL_VERSION, MAP);
    }

    private static PlayerCommand move(float x, float y) {
        return new PlayerCommand(x, y, 1f, 0f, false, false, false);
    }

    @Test
    void acceptedHandshakeIsAnsweredWithWelcome() {
        EmbeddedChannel channel = channelFor(newServer());

        channel.writeInbound(hello());

        Welcome welcome = assertInstanceOf(Welcome.class, channel.readOutbound());
        assertEquals(GameCodec.PROTOCOL_VERSION, welcome.protocolVersion());
        assertTrue(welcome.playerId() >= 0);
        assertTrue(channel.isOpen());
    }

    @Test
    void wrongProtocolVersionClosesTheChannelWithoutWelcome() {
        EmbeddedChannel channel = channelFor(newServer());

        channel.writeInbound(new ClientHello(GameCodec.PROTOCOL_VERSION + 1, MAP));

        assertNull(channel.readOutbound());
        assertFalse(channel.isOpen());
    }

    @Test
    void wrongMapClosesTheChannelWithoutWelcome() {
        EmbeddedChannel channel = channelFor(newServer());

        channel.writeInbound(new ClientHello(GameCodec.PROTOCOL_VERSION, "other_map"));

        assertNull(channel.readOutbound());
        assertFalse(channel.isOpen());
    }

    @Test
    void anythingButAHandshakeFirstClosesTheChannel() {
        EmbeddedChannel channel = channelFor(newServer());

        channel.writeInbound(move(1f, 0f));

        assertNull(channel.readOutbound());
        assertFalse(channel.isOpen());
    }

    @Test
    void validCommandAfterHandshakeKeepsTheChannelOpen() {
        EmbeddedChannel channel = channelFor(newServer());
        channel.writeInbound(hello());
        channel.readOutbound();

        channel.writeInbound(move(1f, -1f));

        assertTrue(channel.isOpen());
    }

    @Test
    void outOfRangeCommandClosesTheChannel() {
        EmbeddedChannel channel = channelFor(newServer());
        channel.writeInbound(hello());
        channel.readOutbound();

        channel.writeInbound(move(2f, 0f));

        assertFalse(channel.isOpen());
    }

    @Test
    void nanCommandClosesTheChannel() {
        EmbeddedChannel channel = channelFor(newServer());
        channel.writeInbound(hello());
        channel.readOutbound();

        channel.writeInbound(move(Float.NaN, 0f));

        assertFalse(channel.isOpen());
    }

    @Test
    void unexpectedMessageAfterHandshakeClosesTheChannel() {
        EmbeddedChannel channel = channelFor(newServer());
        channel.writeInbound(hello());
        channel.readOutbound();

        channel.writeInbound(hello());   // a second handshake is not a command

        assertFalse(channel.isOpen());
    }

    @Test
    void disconnectingFreesTheSeat() {
        GameServer server = newServer();

        EmbeddedChannel first = channelFor(server);
        first.writeInbound(hello());
        first.readOutbound();
        EmbeddedChannel second = channelFor(server);
        second.writeInbound(hello());
        second.readOutbound();

        EmbeddedChannel third = channelFor(server);
        third.writeInbound(hello());
        assertNull(third.readOutbound());           // 2-spawn map is full

        first.close();                              // channelInactive -> leave

        EmbeddedChannel fourth = channelFor(server);
        fourth.writeInbound(hello());
        assertInstanceOf(Welcome.class, fourth.readOutbound());
    }
}
