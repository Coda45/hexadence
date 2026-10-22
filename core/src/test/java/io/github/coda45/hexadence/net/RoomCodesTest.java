package io.github.coda45.hexadence.net;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RoomCodesTest {

    @Test
    void codesUseOnlyTheUnambiguousAlphabet() {
        RoomCodes codes = new RoomCodes();

        for (int i = 0; i < 500; i++) {
            String code = codes.next(Set.of());

            assertEquals(RoomCodes.LENGTH, code.length());

            for (char c : code.toCharArray()) {
                assertTrue(RoomCodes.ALPHABET.indexOf(c) >= 0, "bad character in " + code);
            }
        }
    }

    @Test
    void neverReturnsACodeAlreadyInUse() {
        RoomCodes codes = new RoomCodes();
        Set<String> inUse = new HashSet<>();

        for (int i = 0; i < 2000; i++) {
            assertTrue(inUse.add(codes.next(inUse)));
        }
    }

    @Test
    void normalizesWhatPlayersType() {
        assertEquals("AB3K", RoomCodes.normalize("  ab3k "));
    }

    @Test
    void recognizesCodeCharacters() {
        assertTrue(RoomCodes.isCodeCharacter('a'));
        assertTrue(RoomCodes.isCodeCharacter('7'));
        assertFalse(RoomCodes.isCodeCharacter('O'));
        assertFalse(RoomCodes.isCodeCharacter('0'));
        assertFalse(RoomCodes.isCodeCharacter('-'));
    }

    @Test
    void parsesServerAddresses() {
        assertEquals(new ServerAddress("localhost", GameServer.DEFAULT_PORT), ServerAddress.parse("localhost"));
        assertEquals(new ServerAddress("10.0.0.5", 7800), ServerAddress.parse(" 10.0.0.5:7800 "));
    }
}
