package io.github.coda45.hexadence.net;

import java.util.Objects;

// First message sent by a client. The server does not create a player until
// this protocol version has been accepted.
public record ClientHello(
    int protocolVersion,
    String mapId
) {
    public ClientHello {
        Objects.requireNonNull(mapId);

        if (mapId.isBlank()) {
            throw new IllegalArgumentException(
                "Map ID cannot be blank"
            );
        }
    }
}