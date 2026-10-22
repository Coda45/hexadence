package io.github.coda45.hexadence.net;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

// Generates and normalizes the short codes players type to join a room.
//
// The alphabet leaves out characters that are easy to confuse when read
// aloud or off a screen (0/O, 1/I). Four characters give about a million
// codes, so guessing someone's active room is impractical.
public final class RoomCodes {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 4;

    private final SecureRandom random = new SecureRandom();

    // A new code not already in use.
    public String next(Set<String> inUse) {
        while (true) {
            StringBuilder code = new StringBuilder(LENGTH);

            for (int i = 0; i < LENGTH; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }

            String candidate = code.toString();

            if (!inUse.contains(candidate)) {
                return candidate;
            }
        }
    }

    // What a player typed, in the form codes are stored: trimmed and
    // uppercase, so "ab3k " finds room "AB3K".
    public static String normalize(String typed) {
        return typed.trim().toUpperCase(Locale.ROOT);
    }

    // Whether a character can appear in a code, after uppercasing.
    public static boolean isCodeCharacter(char c) {
        return ALPHABET.indexOf(Character.toUpperCase(c)) >= 0;
    }
}
