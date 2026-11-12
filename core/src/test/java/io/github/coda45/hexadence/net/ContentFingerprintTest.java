package io.github.coda45.hexadence.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.power.LoadoutRules;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;

class ContentFingerprintTest {

    @Test
    void coversEveryPowerAndZone() {
        String content = ContentFingerprint.describeContent();

        for (Power power : Powers.ALL) {
            assertTrue(content.contains(power.name()), power.name());
        }

        for (ZoneKind kind : ZoneKinds.ALL) {
            assertTrue(content.contains(kind.name()), kind.name());
        }
    }

    @Test
    void coversNewPowerAndZoneDefinitionFields() {
        String content = ContentFingerprint.describeContent();
        String shotgun = content.lines()
            .filter(line -> line.startsWith("power " + Powers.WATER_SHOTGUN.id() + " "))
            .findFirst()
            .orElseThrow();
        String voidRot = content.lines()
            .filter(line -> line.startsWith("power " + Powers.VOID_ROT.id() + " "))
            .findFirst()
            .orElseThrow();
        String beamOfLight = content.lines()
            .filter(line -> line.startsWith("power " + Powers.BEAM_OF_LIGHT.id() + " "))
            .findFirst()
            .orElseThrow();
        String ember = content.lines()
            .filter(line -> line.startsWith("zone " + ZoneKinds.EMBER_CIRCLE.id() + " "))
            .findFirst()
            .orElseThrow();
        String stoneWall = content.lines()
            .filter(line -> line.startsWith("zone " + ZoneKinds.STONE_WALL.id() + " "))
            .findFirst()
            .orElseThrow();
        String vortex = content.lines()
            .filter(line -> line.startsWith("zone " + ZoneKinds.VORTEX.id() + " "))
            .findFirst()
            .orElseThrow();
        String inferno = content.lines()
            .filter(line -> line.startsWith("zone " + ZoneKinds.INFERNO.id() + " "))
            .findFirst()
            .orElseThrow();

        assertTrue(shotgun.contains("pellets=" + Powers.WATER_SHOTGUN.pelletCount()));
        assertTrue(shotgun.contains("spread=" + Powers.WATER_SHOTGUN.spreadDegrees()));
        assertTrue(voidRot.contains("dotDamage=" + Powers.VOID_ROT.dotDamagePerTick()));
        assertTrue(voidRot.contains("dotTick=" + Powers.VOID_ROT.dotTickSeconds()));
        assertTrue(voidRot.contains("dotDuration=" + Powers.VOID_ROT.dotDurationSeconds()));
        assertTrue(beamOfLight.contains(
            "channelTick=" + Powers.BEAM_OF_LIGHT.channelTickSeconds()
        ));
        assertTrue(ember.contains("innerRadius=" + ZoneKinds.EMBER_CIRCLE.innerRadius()));
        assertTrue(stoneWall.contains("solidHalfLength=" + ZoneKinds.STONE_WALL.solidHalfLength()));
        assertTrue(stoneWall.contains("maxHealth=" + ZoneKinds.STONE_WALL.maxHealth()));
        assertTrue(vortex.contains("captures=true"));
        assertTrue(vortex.contains("pushImpulse=" + ZoneKinds.VORTEX.pushImpulse()));
        assertTrue(inferno.contains("driftSpeed=" + ZoneKinds.INFERNO.driftSpeed()));
        assertTrue(content.contains(
            "loadoutBasicRange " + LoadoutRules.MIN_BASIC_POWERS
                + " " + LoadoutRules.MAX_BASIC_POWERS
        ));
    }

    @Test
    void isStableWithinABuild() {
        assertEquals(
            ContentFingerprint.describeContent(),
            ContentFingerprint.describeContent()
        );
    }

    @Test
    void helloCarriesThisBuildsFingerprint() {
        assertEquals(
            ContentFingerprint.CURRENT,
            ClientHello.createRoom("maps/dungeon_map.tmx").contentFingerprint()
        );
    }
}
