package io.github.coda45.hexadence.net;

import java.util.Objects;

// First message sent by a client. The server does not create a player until
// this has been accepted.
//
// An empty roomCode asks the server to create a new room, and the client
// becomes that room's host. Otherwise the client joins the room with that code.
//
// contentFingerprint identifies this build's powers, zones and reactions; the
// server refuses a game whose content does not match its own.
public record ClientHello(
    int protocolVersion,
    String mapId,
    String roomCode,
    int contentFingerprint
) {
    public ClientHello {
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(roomCode);

        if (mapId.isBlank()) {
            throw new IllegalArgumentException(
                "Map ID cannot be blank"
            );
        }
    }

    // A hello from this build, carrying its own content fingerprint.
    public ClientHello(int protocolVersion, String mapId, String roomCode) {
        this(protocolVersion, mapId, roomCode, ContentFingerprint.CURRENT);
    }

    // A hello without a room code asks for a new room. Kept so code written
    // before rooms existed still compiles.
    public ClientHello(int protocolVersion, String mapId) {
        this(protocolVersion, mapId, "");
    }

    // Asks for a new room.
    public static ClientHello createRoom(String mapId) {
        return new ClientHello(GameCodec.PROTOCOL_VERSION, mapId, "");
    }

    // Asks to join an existing room.
    public static ClientHello joinRoom(String mapId, String roomCode) {
        return new ClientHello(GameCodec.PROTOCOL_VERSION, mapId, roomCode);
    }

    public boolean wantsNewRoom() {
        return roomCode.isEmpty();
    }
}
