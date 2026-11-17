package io.github.coda45.hexadence.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.badlogic.gdx.utils.Disposable;

import io.github.coda45.hexadence.audio.SpellSoundLibrary.LoopCue;
import io.github.coda45.hexadence.audio.SpellSoundLibrary.PlayingSound;
import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.BeamStrikeEvent;
import io.github.coda45.hexadence.world.WorldEvent;
import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Client-only presentation. It derives cues from stable world entities and
// semantic events already used by rendering. It never changes simulation and
// never asks the server for sound-specific messages.
public final class SpellAudioSystem implements Disposable {

    private static final float COOLDOWN_EPSILON = 0.0001f;
    private static final float MIN_LOOP_INTERVAL = 0.05f;

    private static final Set<Power> MAPPED_CAST_POWERS = Set.of(
        Powers.FIREBALL,
        Powers.EMBER_CIRCLE,
        Powers.WATER_SHOTGUN,
        Powers.ROCK_SHARDS,
        Powers.STONE_WALL,
        Powers.LIGHTNING_BOLT,
        Powers.VORTEX,
        Powers.VOID_ROT
    );

    private static final Map<ZoneKind, Power> ZONE_CAST_POWERS = Map.of(
        ZoneKinds.EMBER_CIRCLE, Powers.EMBER_CIRCLE,
        ZoneKinds.STONE_WALL, Powers.STONE_WALL
    );

    private final SpellSoundLibrary library;
    private final GameAudio gameAudio;
    private final Predicate<Power> hasCast;
    private final Consumer<Power> playCast;
    private final Consumer<Power> playImpact;
    private final Map<Integer, ProjectileSample> previousProjectiles =
        new HashMap<>();
    private final Map<Integer, ZoneKind> previousZoneKinds = new HashMap<>();
    private final Map<Integer, FieldLoop> fieldLoops = new HashMap<>();
    private final Map<Long, CooldownSample> previousCooldowns = new HashMap<>();

    public SpellAudioSystem(GameAudio gameAudio) {
        this.gameAudio = gameAudio;
        library = new SpellSoundLibrary();
        hasCast = library::hasCast;
        playCast = power -> library.playCast(power, gameAudio.sfxVolume());
        playImpact = power -> library.playImpact(power, gameAudio.sfxVolume());
    }

    // Keeps cue detection testable without initializing a libGDX audio device.
    SpellAudioSystem(Consumer<Power> playCast) {
        this(playCast, ignored -> { });
    }

    SpellAudioSystem(
        Consumer<Power> playCast,
        Consumer<Power> playImpact
    ) {
        library = null;
        gameAudio = null;
        hasCast = MAPPED_CAST_POWERS::contains;
        this.playCast = playCast;
        this.playImpact = playImpact;
    }

    public void update(
        ArenaWorld world,
        List<WorldEvent> frameEvents,
        float presentationDelta
    ) {
        Set<CastKey> playedCasts = new HashSet<>();

        playBeamCasts(frameEvents, playedCasts);
        observeProjectiles(world, playedCasts);
        observeZones(world, playedCasts);
        playCooldownFallbacks(world, playedCasts);
        updateFieldLoops(world, Math.max(0f, presentationDelta));
    }

    void update(ArenaWorld world, List<WorldEvent> frameEvents) {
        update(world, frameEvents, 0f);
    }

    private void playBeamCasts(
        List<WorldEvent> frameEvents,
        Set<CastKey> playedCasts
    ) {
        for (WorldEvent event : frameEvents) {
            if (
                event instanceof BeamStrikeEvent strike
                    && strike.power() == Powers.LIGHTNING_BOLT
                    && strike.branchIndex() == 0
            ) {
                playCastOnce(
                    new CastKey(strike.ownerPlayerId(), strike.power()),
                    playedCasts
                );
            }
        }
    }

    private void observeProjectiles(
        ArenaWorld world,
        Set<CastKey> playedCasts
    ) {
        Map<Integer, ProjectileSample> current = new HashMap<>();

        for (Projectile projectile : world.getProjectiles()) {
            ProjectileSample sample = new ProjectileSample(
                projectile.getPower(),
                projectile.getOwnerPlayerId()
            );
            current.put(projectile.getId(), sample);

            if (!previousProjectiles.containsKey(projectile.getId())) {
                playCastOnce(
                    new CastKey(sample.ownerPlayerId(), sample.power()),
                    playedCasts
                );
            }
        }

        for (Map.Entry<Integer, ProjectileSample> entry
            : previousProjectiles.entrySet()) {
            if (
                !current.containsKey(entry.getKey())
                    && entry.getValue().power() == Powers.FIREBALL
            ) {
                playImpact.accept(Powers.FIREBALL);
            }
        }

        previousProjectiles.clear();
        previousProjectiles.putAll(current);
    }

    private void observeZones(
        ArenaWorld world,
        Set<CastKey> playedCasts
    ) {
        Map<Integer, ZoneKind> current = new HashMap<>();

        for (Zone zone : world.getZones()) {
            ZoneKind kind = zone.getKind();
            current.put(zone.getId(), kind);

            if (previousZoneKinds.get(zone.getId()) == kind) {
                continue;
            }

            Power castPower = ZONE_CAST_POWERS.get(kind);

            if (castPower != null) {
                playCastOnce(
                    new CastKey(zone.getDamageOwnerPlayerId(), castPower),
                    playedCasts
                );
            } else if (kind == ZoneKinds.NULL_ZONE) {
                playImpact.accept(Powers.NULL_ZONE);
            }
        }

        previousZoneKinds.clear();
        previousZoneKinds.putAll(current);
    }

    private void playCooldownFallbacks(
        ArenaWorld world,
        Set<CastKey> playedCasts
    ) {
        Set<Long> liveSlots = new HashSet<>();

        for (Map.Entry<Integer, Player> entry : world.getPlayers().entrySet()) {
            int playerId = entry.getKey();
            Player player = entry.getValue();

            for (int slot = 0; slot < Loadout.SIZE; slot++) {
                long slotKey = slotKey(playerId, slot);
                Power power = player.getPower(slot);
                float cooldown = player.getCooldownRemaining(slot);
                CooldownSample previous = previousCooldowns.put(
                    slotKey,
                    new CooldownSample(power, cooldown)
                );
                liveSlots.add(slotKey);

                if (
                    power != Powers.LIGHTNING_BOLT
                        && hasCast.test(power)
                        && previous != null
                        && previous.power() == power
                        && cooldown > previous.remaining() + COOLDOWN_EPSILON
                ) {
                    playCastOnce(
                        new CastKey(playerId, power),
                        playedCasts
                    );
                }
            }
        }

        previousCooldowns.keySet().retainAll(liveSlots);
    }

    private void playCastOnce(CastKey cast, Set<CastKey> playedCasts) {
        if (hasCast.test(cast.power()) && playedCasts.add(cast)) {
            playCast.accept(cast.power());
        }
    }

    private void updateFieldLoops(ArenaWorld world, float delta) {
        if (library == null) {
            return;
        }

        Set<Integer> liveLoopIds = new HashSet<>();

        for (Zone zone : world.getZones()) {
            LoopCue cue = library.fieldCue(zone.getKind());

            if (cue == null) {
                continue;
            }

            int zoneId = zone.getId();
            liveLoopIds.add(zoneId);
            FieldLoop loop = fieldLoops.get(zoneId);

            if (loop == null || loop.kind != zone.getKind()) {
                if (loop != null) {
                    loop.stop();
                }

                loop = new FieldLoop(zone.getKind(), cue);
                fieldLoops.put(zoneId, loop);
            }

            loop.update(delta);
        }

        var iterator = fieldLoops.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, FieldLoop> entry = iterator.next();

            if (!liveLoopIds.contains(entry.getKey())) {
                entry.getValue().stop();
                iterator.remove();
            }
        }
    }

    private static long slotKey(int playerId, int slot) {
        return ((long) playerId << 32) | (slot & 0xFFFFFFFFL);
    }

    public void setEnabled(boolean enabled) {
        if (library != null) {
            library.setEnabled(enabled);

            if (!enabled) {
                for (FieldLoop loop : fieldLoops.values()) {
                    loop.stop();
                }
                fieldLoops.clear();
            }
        }
    }

    @Override
    public void dispose() {
        for (FieldLoop loop : fieldLoops.values()) {
            loop.stop();
        }
        fieldLoops.clear();

        if (library != null) {
            library.dispose();
        }

        previousProjectiles.clear();
        previousZoneKinds.clear();
        previousCooldowns.clear();
    }

    private final class FieldLoop {

        private final ZoneKind kind;
        private final LoopCue cue;
        private final List<ActiveFieldSound> activeSounds = new ArrayList<>();
        private float secondsUntilNext;

        private FieldLoop(ZoneKind kind, LoopCue cue) {
            this.kind = kind;
            this.cue = cue;
            secondsUntilNext = playNext();
        }

        private void update(float delta) {
            for (int index = activeSounds.size() - 1; index >= 0; index--) {
                ActiveFieldSound active = activeSounds.get(index);
                active.remainingSeconds -= delta;

                if (active.remainingSeconds <= 0f) {
                    activeSounds.remove(index);
                }
            }

            secondsUntilNext -= delta;

            while (secondsUntilNext <= 0f) {
                float interval = playNext();

                if (!Float.isFinite(interval)) {
                    secondsUntilNext = Float.POSITIVE_INFINITY;
                    break;
                }

                secondsUntilNext += interval;
            }
        }

        private float playNext() {
            PlayingSound playing = library.playField(
                cue,
                gameAudio.sfxVolume()
            );

            if (playing == null) {
                return Float.POSITIVE_INFINITY;
            }

            activeSounds.add(new ActiveFieldSound(
                playing,
                playing.durationSeconds()
            ));
            float interval = Math.max(
                MIN_LOOP_INTERVAL,
                playing.durationSeconds() - cue.overlapSeconds()
            );
            return interval;
        }

        private void stop() {
            for (ActiveFieldSound active : activeSounds) {
                library.stop(active.playing);
            }

            activeSounds.clear();
        }
    }

    private static final class ActiveFieldSound {

        private final PlayingSound playing;
        private float remainingSeconds;

        private ActiveFieldSound(
            PlayingSound playing,
            float remainingSeconds
        ) {
            this.playing = playing;
            this.remainingSeconds = remainingSeconds;
        }
    }

    private record CastKey(int ownerPlayerId, Power power) {
    }

    private record ProjectileSample(Power power, int ownerPlayerId) {
    }

    private record CooldownSample(Power power, float remaining) {
    }
}
