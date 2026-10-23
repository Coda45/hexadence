package io.github.coda45.hexadence.net;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.world.LightningStrikeEvent;
import io.github.coda45.hexadence.world.ZoneChargeChangedEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;

class GameCodecTest {

    @Test
    void roundTripsClientHelloThatCreatesARoom() {
        ClientHello expected =
            ClientHello.createRoom("maps/dungeon_map.tmx");

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsClientHelloThatJoinsARoom() {
        ClientHello expected =
            ClientHello.joinRoom("maps/dungeon_map.tmx", "AB3K");

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsWelcome() {
        Welcome expected =
            new Welcome(GameCodec.PROTOCOL_VERSION, 42, "AB3K");

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void roundTripsEveryJoinRejectedReason() {
        for (JoinRejected.Reason reason : JoinRejected.Reason.values()) {
            JoinRejected expected = new JoinRejected(reason);

            assertEquals(expected, roundTrip(expected));
        }
    }

    @Test
    void roundTripsLobbyMessages() {
        assertEquals(new LobbyState(3, 4), roundTrip(new LobbyState(3, 4)));
        assertEquals(new StartGame(), roundTrip(new StartGame()));
        assertEquals(new GameStarted(), roundTrip(new GameStarted()));
        assertEquals(new RoomClosed(), roundTrip(new RoomClosed()));
        assertEquals(new KeepAlive(), roundTrip(new KeepAlive()));
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
            List.of(
                new WorldSnapshot.PlayerState(
                    7,
                    PlayerColor.GREEN,
                    1f,
                    2f,
                    1f,
                    0f,
                    true,
                    80f,
                    -1f,
                    3,
                    2,
                    List.of(
                        new WorldSnapshot.SlotState(Powers.FIREBALL, 0.1f),
                        new WorldSnapshot.SlotState(Powers.WATER_PUDDLE, 0.2f),
                        new WorldSnapshot.SlotState(Powers.LIGHTNING_BOLT, 0.3f)
                    )
                )
            ),
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
                    Powers.FIREBALL,
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
                    0.8f,
                    true,
                    4
                )
            )
        );

        assertEquals(expected, roundTrip(expected));
    }

    @Test
    void everyPowerSurvivesTheNetwork() {
        for (Power power : Powers.ALL) {
            WorldSnapshot.ProjectileState expected =
                new WorldSnapshot.ProjectileState(1, power, 2, 3f, 4f, 1f, 0f);

            WorldSnapshot snapshot = new WorldSnapshot(
                1L, List.of(), List.of(), List.of(expected), List.of()
            );

            assertEquals(snapshot, roundTrip(snapshot), power.name());
        }
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