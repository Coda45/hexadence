package io.github.coda45.hexadence.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.net.LobbyConnection.Status;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.SpawnPoint;

// Rooms end to end: a real server on a free local port and real clients
// connecting through LobbyConnection, the same path the lobby screens use.
class RoomsTest {

    private static final String MAP = "test_map";
    private static final long TIMEOUT_MILLIS = 5000;

    private GameServer server;
    private ServerAddress address;
    private final List<LobbyConnection> connections = new ArrayList<>();

    @BeforeEach
    void startServer() throws InterruptedException {
        ArenaDefinition arena = new ArenaDefinition(
            new ArenaCollision(new float[12][12]),
            List.of(
                new SpawnPoint(2f, 2f),
                new SpawnPoint(9f, 2f),
                new SpawnPoint(2f, 9f),
                new SpawnPoint(9f, 9f)
            ),
            List.of()
        );

        server = new GameServer(arena, MAP, 0);
        server.start();
        address = new ServerAddress("127.0.0.1", server.boundPort());
    }

    @AfterEach
    void stopServer() {
        connections.forEach(LobbyConnection::close);
        server.stop();
    }

    @Test
    void hostGetsARoomCodeAndJoinerFindsTheRoom() {
        LobbyConnection host = createRoom();
        String code = host.roomCode();

        assertNotNull(code);
        assertEquals(RoomCodes.LENGTH, code.length());

        // Codes are typed by people, so lowercase and spaces must work.
        LobbyConnection joiner = joinRoom(" " + code.toLowerCase() + " ");
        awaitStatus(joiner, Status.IN_LOBBY);
        assertEquals(code, joiner.roomCode());

        await(() -> host.playerCount() == 2 && joiner.playerCount() == 2);
        assertEquals(GameServer.MAX_PLAYERS, host.maxPlayers());
    }

    @Test
    void eachHostGetsTheirOwnRoom() {
        LobbyConnection first = createRoom();
        LobbyConnection second = createRoom();

        assertNotEquals(first.roomCode(), second.roomCode());
    }

    @Test
    void onlyTheHostCanStartTheMatch() {
        LobbyConnection host = createRoom();
        LobbyConnection joiner = joinRoom(host.roomCode());
        awaitStatus(joiner, Status.IN_LOBBY);

        joiner.requestStart();
        pause(300);
        assertEquals(Status.IN_LOBBY, host.status());
        assertEquals(Status.IN_LOBBY, joiner.status());

        lockIn(host);
        lockIn(joiner);
        await(host::everyoneLocked);
        host.requestStart();
        awaitStatus(host, Status.STARTED);
        awaitStatus(joiner, Status.STARTED);

        // Once started, the room ticks and both players receive snapshots.
        NetClient hostClient = host.handOff().client();
        NetClient joinerClient = joiner.handOff().client();
        await(() -> hostClient.isReady() && joinerClient.isReady());
        assertNotEquals(hostClient.getPlayerId(), joinerClient.getPlayerId());

        hostClient.close();
        joinerClient.close();
    }

    @Test
    void wrongCodeIsRejected() {
        createRoom();

        LobbyConnection joiner = joinRoom("ZZZZ");
        awaitStatus(joiner, Status.REJECTED);
        assertEquals(JoinRejected.Reason.ROOM_NOT_FOUND, joiner.rejection());
    }

    @Test
    void fifthPlayerIsRejected() {
        LobbyConnection host = createRoom();

        for (int i = 0; i < 3; i++) {
            awaitStatus(joinRoom(host.roomCode()), Status.IN_LOBBY);
        }

        LobbyConnection fifth = joinRoom(host.roomCode());
        awaitStatus(fifth, Status.REJECTED);
        assertEquals(JoinRejected.Reason.ROOM_FULL, fifth.rejection());
    }

    @Test
    void cannotJoinAMatchInProgress() {
        LobbyConnection host = createRoom();
        lockIn(host);
        await(host::everyoneLocked);
        host.requestStart();
        awaitStatus(host, Status.STARTED);

        LobbyConnection late = joinRoom(host.roomCode());
        awaitStatus(late, Status.REJECTED);
        assertEquals(JoinRejected.Reason.GAME_IN_PROGRESS, late.rejection());
    }

    @Test
    void hostLeavingTheLobbyClosesTheRoom() {
        LobbyConnection host = createRoom();
        String code = host.roomCode();
        LobbyConnection joiner = joinRoom(code);
        awaitStatus(joiner, Status.IN_LOBBY);

        host.close();
        awaitStatus(joiner, Status.ROOM_CLOSED);

        // The code no longer leads anywhere.
        LobbyConnection late = joinRoom(code);
        awaitStatus(late, Status.REJECTED);
        assertEquals(JoinRejected.Reason.ROOM_NOT_FOUND, late.rejection());
    }

    @Test
    void joinerLeavingTheLobbyUpdatesTheCount() {
        LobbyConnection host = createRoom();
        LobbyConnection joiner = joinRoom(host.roomCode());
        await(() -> host.playerCount() == 2);

        joiner.close();
        await(() -> host.playerCount() == 1);
        assertEquals(Status.IN_LOBBY, host.status());
    }

    @Test
    void wrongMapIsRejected() {
        LobbyConnection other = LobbyConnection.createRoom(address, "maps/other.tmx");
        connections.add(other);

        awaitStatus(other, Status.REJECTED);
        assertEquals(JoinRejected.Reason.WRONG_MAP, other.rejection());
    }

    @Test
    void unreachableServerIsReported() {
        LobbyConnection nowhere = LobbyConnection.createRoom(
            new ServerAddress("127.0.0.1", 1),
            MAP
        );
        connections.add(nowhere);

        awaitStatus(nowhere, Status.UNREACHABLE);
    }

    // ---- Drafting ----

    private static final Loadout BEAM_FIRST = new Loadout(List.of(
        Powers.LIGHTNING_BOLT,
        Powers.FIREBALL,
        Powers.WATER_PUDDLE
    ));

    @Test
    void startWaitsUntilEveryoneIsLockedIn() {
        LobbyConnection host = createRoom();
        LobbyConnection joiner = joinRoom(host.roomCode());
        awaitStatus(joiner, Status.IN_LOBBY);

        lockIn(host);
        await(() -> host.lockedCount() == 1);
        assertFalse(host.everyoneLocked());

        host.requestStart();
        pause(300);
        assertEquals(Status.IN_LOBBY, host.status());

        lockIn(joiner);
        await(host::everyoneLocked);

        host.requestStart();
        awaitStatus(host, Status.STARTED);
        awaitStatus(joiner, Status.STARTED);
    }

    @Test
    void unlockingLowersTheCount() {
        LobbyConnection host = createRoom();

        lockIn(host);
        await(() -> host.lockedCount() == 1);

        host.selectLoadout(Loadout.DEFAULT, false);
        await(() -> host.lockedCount() == 0);
    }

    @Test
    void illegalLoadoutIsIgnored() {
        LobbyConnection host = createRoom();

        Loadout duplicates = new Loadout(List.of(
            Powers.FIREBALL, Powers.FIREBALL, Powers.FIREBALL
        ));
        host.selectLoadout(duplicates, true);
        pause(300);

        assertEquals(0, host.lockedCount());
    }

    @Test
    void draftedPowersAreUsedInTheMatchAndCannotChange() {
        LobbyConnection host = createRoom();
        host.selectLoadout(BEAM_FIRST, true);
        await(host::everyoneLocked);

        host.requestStart();
        awaitStatus(host, Status.STARTED);

        NetClient client = host.handOff().client();
        await(client::isReady);
        assertEquals(BEAM_FIRST.slots(), slotPowers(client));

        // Changing powers after the start is ignored.
        client.selectLoadout(Loadout.DEFAULT, true);
        pause(300);
        assertEquals(BEAM_FIRST.slots(), slotPowers(client));

        client.close();
    }

    private static void lockIn(LobbyConnection connection) {
        connection.selectLoadout(Loadout.DEFAULT, true);
    }

    private static List<Power> slotPowers(NetClient client) {
        int me = client.getPlayerId();

        return client.getLatestSnapshot().players().stream()
            .filter(player -> player.id() == me)
            .findFirst()
            .orElseThrow()
            .slots().stream()
            .map(WorldSnapshot.SlotState::power)
            .toList();
    }

    // ---- Helpers ----

    private LobbyConnection createRoom() {
        LobbyConnection connection = LobbyConnection.createRoom(address, MAP);
        connections.add(connection);
        awaitStatus(connection, Status.IN_LOBBY);
        return connection;
    }

    private LobbyConnection joinRoom(String code) {
        LobbyConnection connection = LobbyConnection.joinRoom(address, MAP, code);
        connections.add(connection);
        return connection;
    }

    private static void awaitStatus(LobbyConnection connection, Status expected) {
        await(() -> {
            connection.poll();
            return connection.status() == expected;
        }, () -> "expected " + expected + " but was " + connection.status());
    }

    private static void await(BooleanSupplier condition) {
        await(condition, () -> "condition not met");
    }

    private static void await(
        BooleanSupplier condition,
        java.util.function.Supplier<String> failure
    ) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;

        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }

            pause(10);
        }

        fail("Timed out: " + failure.get());
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
