package io.github.coda45.hexadence.net;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Maverick;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.SpawnPoint;
import io.netty.channel.embedded.EmbeddedChannel;

// The room's match clock decides when the Maverick appears and when it has
// to stop casting. Ticked by hand here, so the timing is exact.
class MaverickRoomTest {

    private static final int ONE_SECOND = Math.round(1f / ArenaWorld.STEP);

    @Test
    void maverickAppearsWhenThirtySecondsRemain() {
        GameRoom room = startedRoom(31f, 3f);

        // One tick short of a second in: 30 s and one tick remain.
        tick(room, ONE_SECOND - 1);
        assertNull(room.world().getMaverick());

        tick(room, 1);
        assertNotNull(room.world().getMaverick());

        // A shorter match has it from the start.
        GameRoom shortRoom = startedRoom(10f, 3f);

        tick(shortRoom, 1);
        assertNotNull(shortRoom.world().getMaverick());
    }

    @Test
    void maverickStopsCastingDuringTheEndingAndLeavesAfterIt() {
        // Ember Circle is cast every 2.5 s: once before this clock runs out,
        // and it would be cast again 2 s into the ending.
        GameRoom room = startedRoom(3f, 3f);
        Set<Integer> zoneIds = new LinkedHashSet<>();

        for (int i = 0; i < 6 * ONE_SECOND - 1; i++) {
            room.tick();

            for (Zone zone : room.world().getZones()) {
                if (zone.getDamageOwnerPlayerId() == Maverick.SOURCE_ID) {
                    zoneIds.add(zone.getId());
                }
            }
        }

        assertEquals(GameRoom.State.PLAYING, room.state());
        assertNotNull(room.world().getMaverick());
        assertEquals(1, zoneIds.size());

        // The rematch starts without it.
        tick(room, 1);

        assertEquals(GameRoom.State.LOBBY, room.state());
        assertNull(room.world().getMaverick());
    }

    static GameRoom startedRoom(float matchSeconds, float endingSeconds) {
        GameRoom room = new GameRoom(
            "AB3K",
            new ArenaDefinition(
                new ArenaCollision(new float[12][12]),
                List.of(new SpawnPoint(2f, 2f), new SpawnPoint(9f, 9f)),
                List.of()
            ),
            "test_map",
            matchSeconds,
            endingSeconds
        );

        int host = room.join(new EmbeddedChannel());
        room.selectLoadout(host, new SelectLoadout(Loadout.DEFAULT, true));
        room.start(host);

        assertEquals(GameRoom.State.PLAYING, room.state());
        return room;
    }

    private static void tick(GameRoom room, int ticks) {
        for (int i = 0; i < ticks; i++) {
            room.tick();
        }
    }
}
