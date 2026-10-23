package io.github.coda45.hexadence.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.input.PlayerCommand;

class PendingCommandTest {

    @Test
    void keepsMovementDuringShortCommandGap() {
        GameServer.PendingCommand pending =
            new GameServer.PendingCommand();

        pending.merge(movingCommand(1f, 0f));

        assertEquals(1f, pending.take().moveX());

        for (
            int i = 1;
            i < GameServer.PendingCommand.TIMEOUT_TICKS;
            i++
        ) {
            assertEquals(1f, pending.take().moveX());
        }
    }

    @Test
    void becomesIdleAfterCommandTimeout() {
        GameServer.PendingCommand pending =
            new GameServer.PendingCommand();

        pending.merge(movingCommand(1f, 0f));
        pending.take();

        for (
            int i = 1;
            i < GameServer.PendingCommand.TIMEOUT_TICKS;
            i++
        ) {
            pending.take();
        }

        assertEquals(PlayerCommand.IDLE, pending.take());
        assertEquals(PlayerCommand.IDLE, pending.take());
    }

    @Test
    void freshCommandRestoresControlAfterTimeout() {
        GameServer.PendingCommand pending =
            new GameServer.PendingCommand();

        pending.merge(movingCommand(1f, 0f));

        for (
            int i = 0;
            i <= GameServer.PendingCommand.TIMEOUT_TICKS;
            i++
        ) {
            pending.take();
        }

        pending.merge(movingCommand(0f, -1f));

        PlayerCommand resumed = pending.take();

        assertEquals(0f, resumed.moveX());
        assertEquals(-1f, resumed.moveY());
    }

    @Test
    void castRequestsAreConsumedOnlyOnce() {
        GameServer.PendingCommand pending =
            new GameServer.PendingCommand();

        pending.merge(
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                true, false, false
            )
        );

        assertTrue(pending.take().castSlot1());
        assertFalse(pending.take().castSlot1());
    }

    private static PlayerCommand movingCommand(
        float moveX,
        float moveY
    ) {
        return new PlayerCommand(
            moveX,
            moveY,
            1f,
            0f,
            false,
            false,
            false
        );
    }
}