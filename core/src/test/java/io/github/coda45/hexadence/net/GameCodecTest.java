package io.github.coda45.hexadence.net;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.spell.SpellType;
import io.github.coda45.hexadence.world.LightningStrikeEvent;
import io.github.coda45.hexadence.world.ZoneChargeChangedEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;

class GameCodecTest {

    @Test
    void roundTripsClientHello() {
        ClientHello expected =
            new ClientHello(
                GameCodec.PROTOCOL_VERSION,
                "maps/dungeon_map.tmx"
            );

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsWelcome() {
        Welcome expected =
            new Welcome(GameCodec.PROTOCOL_VERSION, 42);

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsLightningStrikeEvent() {
        LightningStrikeEvent expected =
            new LightningStrikeEvent(
                1f,
                2f,
                5f,
                6f,
                LightningStrikeEvent.EndReason.PLAYER
            );

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsSnapshotEntityIds() {
        WorldSnapshot expected = new WorldSnapshot(
            99L,
            List.of(),
            List.of(
                new WorldSnapshot.DummyState(
                    11,
                    2f,
                    3f,
                    75f
                )
            ),
            List.of(
                new WorldSnapshot.ProjectileState(
                    22,
                    SpellType.EMBER,
                    4,
                    5f,
                    6f,
                    1f,
                    0f
                )
            ),
            List.of(
                new WorldSnapshot.ZoneState(
                    33,
                    Zone.Type.PUDDLE,
                    7f,
                    8f,
                    true,
                    4
                )
            )
        );

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsZoneChargeChangedEvent() {
        ZoneChargeChangedEvent expected =
            new ZoneChargeChangedEvent(33, true);

        assertEquals(expected, roundTrip(expected));
    }

    private static Object roundTrip(Object expected) {
        EmbeddedChannel channel = new EmbeddedChannel();
        GameCodec.install(channel.pipeline());

        try {
            assertTrue(channel.writeOutbound(expected));

            ByteBuf lengthPrefix = channel.readOutbound();
            ByteBuf payload = channel.readOutbound();

            assertTrue(channel.writeInbound(lengthPrefix, payload));
            return channel.readInbound();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}