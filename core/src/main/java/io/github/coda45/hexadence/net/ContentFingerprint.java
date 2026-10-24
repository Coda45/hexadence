package io.github.coda45.hexadence.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.LoadoutRules;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.Reactions;
import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;

// A short number that changes whenever the game's content changes: any power,
// zone kind, reaction, or loadout rule. The game sends it when joining, and
// the server turns away a game whose content differs from its own.
//
// Merges to main deploy the server automatically, so after a teammate tunes a
// power, anyone still running an older build gets a clear "update your game"
// message instead of a match where their screen disagrees with the server.
public final class ContentFingerprint {

    public static final int CURRENT = compute();

    private ContentFingerprint() {
    }

    // Visible for tests: the text the fingerprint is computed from.
    static String describeContent() {
        StringBuilder text = new StringBuilder();

        for (Power p : Powers.ALL) {
            text.append("power ").append(p.id()).append(' ').append(p.name())
                .append(' ').append(p.element()).append(' ').append(p.weapon())
                .append(' ').append(p.form()).append(' ').append(p.behavior())
                .append(' ').append(p.damage()).append(' ').append(p.cooldownSeconds())
                .append(' ').append(p.speed()).append(' ').append(p.range())
                .append(' ').append(p.radius())
                .append(' ').append(p.zoneKind() == null ? 0 : p.zoneKind().id())
                .append(' ').append(p.iconPath()).append(' ').append(p.iconRimPath())
                .append('\n');
        }

        for (ZoneKind z : ZoneKinds.ALL) {
            text.append("zone ").append(z.id()).append(' ').append(z.name())
                .append(' ').append(z.origin()).append(' ').append(z.element())
                .append(' ').append(z.weapon()).append(' ').append(z.radius())
                .append(' ').append(z.duration()).append(' ').append(z.damagePerSecond())
                .append(' ').append(z.speedMultiplier()).append(' ').append(z.coversPlayers())
                .append(' ').append(z.tint())
                .append('\n');
        }

        for (Reactions.Reaction r : Reactions.ALL) {
            text.append("reaction ").append(r.weapon()).append(' ').append(r.cast())
                .append(' ').append(r.field()).append(' ').append(r.result())
                .append(' ').append(r.into() == null ? 0 : r.into().id())
                .append('\n');
        }

        text.append("discharge ").append(Reactions.CHARGE_DISCHARGE_DAMAGE).append('\n');
        text.append("strict ").append(LoadoutRules.REQUIRE_ONE_BASIC_TWO_ZONES).append('\n');
        text.append("loadout ").append(Loadout.SIZE).append('\n');

        return text.toString();
    }

    private static int compute() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(describeContent().getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.wrap(hash).getInt();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
