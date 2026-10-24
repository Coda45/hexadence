package io.github.coda45.hexadence.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

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
