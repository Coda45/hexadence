package io.github.coda45.hexadence.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.SpawnPoint;
import io.github.coda45.hexadence.world.StepResult;

class SpellAudioSystemTest {

    @Test
    void playsOneCueForEachFireballAndLightningCast() {
        ArenaWorld world = emptyWorld();
        List<Power> played = new ArrayList<>();
        SpellAudioSystem audio = new SpellAudioSystem(played::add);

        // Seed cooldown tracking before the first cast.
        audio.update(world, List.of());

        StepResult fireball = world.step(castSlotOne());
        audio.update(world, fireball.events());
        audio.update(world, List.of());

        assertEquals(List.of(Powers.FIREBALL), played);

        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.LIGHTNING_BOLT,
            Powers.WATER_PUDDLE,
            Powers.FIREBALL
        )));
        audio.update(world, List.of());

        StepResult lightning = world.step(castSlotOne());
        audio.update(world, lightning.events());

        assertEquals(
            List.of(Powers.FIREBALL, Powers.LIGHTNING_BOLT),
            played
        );
    }

    @Test
    void deduplicatesPelletCastsAndObservesFireballImpact() {
        ArenaWorld world = emptyWorld();
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_SHOTGUN,
            Powers.FIREBALL,
            Powers.NULL_ZONE
        )));
        List<Power> casts = new ArrayList<>();
        List<Power> impacts = new ArrayList<>();
        SpellAudioSystem audio = new SpellAudioSystem(casts::add, impacts::add);

        audio.update(world, List.of());

        StepResult shotgun = world.step(castSlotOne());
        audio.update(world, shotgun.events());

        StepResult fireball = world.step(castSlotTwo());
        audio.update(world, fireball.events());

        for (int step = 0; step < 120; step++) {
            StepResult result = world.step(PlayerCommand.IDLE);
            audio.update(world, result.events());
        }

        assertEquals(List.of(Powers.WATER_SHOTGUN, Powers.FIREBALL), casts);
        assertEquals(List.of(Powers.FIREBALL), impacts);
    }

    private static ArenaWorld emptyWorld() {
        return new ArenaWorld(new ArenaDefinition(
            new ArenaCollision(new float[12][12]),
            new SpawnPoint(2f, 2f),
            List.of()
        ));
    }

    private static PlayerCommand castSlotOne() {
        return new PlayerCommand(
            0f,
            0f,
            1f,
            0f,
            true,
            false,
            false
        );
    }

    private static PlayerCommand castSlotTwo() {
        return new PlayerCommand(
            0f,
            0f,
            1f,
            0f,
            false,
            true,
            false
        );
    }
}
