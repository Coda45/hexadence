package io.github.coda45.hexadence.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.input.CastRequest;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;
import io.github.coda45.hexadence.world.BeamChannelEndEvent;
import io.github.coda45.hexadence.world.BeamStrikeEvent;
import io.github.coda45.hexadence.world.PlayerKilledEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

// Turns the network messages into bytes and back. Server and client both
// call install() so they always agree on the format.
//
// Every message on the wire looks like:
//   [4-byte length][1-byte type][fields...]
// The length prefix lets the receiver cut the byte stream into whole messages
//
// Enums travel as explicit stable IDs.
public final class GameCodec {

    // 6: rooms and the lobby (room codes, JoinRejected, LobbyState,
    //    StartGame, GameStarted, RoomClosed, KeepAlive).
    // 7: powers travel by ID and players by loadout slot, so adding or
    //    tuning a power never changes this file.
    // 8: zones travel by ZoneKind ID, so adding a zone kind never changes
    //    this file either.
    // 9: lobby drafting (SelectLoadout, locked count in LobbyState).
    // 10: ClientHello carries a content fingerprint (see ContentFingerprint).
    // 11: commands carry bounded per-slot cast requests with preserved targets.
    // 12: beam events identify their power so clients select the right visual.
    // 13: held beams identify their caster and send an explicit release event.
    // 14: zone snapshots carry health for destructible solid zones.
    // 15: beam events identify primary and crystal-split branches.
    // 16: timed matches (timer in snapshots, MatchEnded with results),
    //     spawn shields in snapshots, and kill events for the kill feed.
    public static final int PROTOCOL_VERSION = 16;

    private static final byte TYPE_COMMAND = 1;
    private static final byte TYPE_WELCOME = 2;
    private static final byte TYPE_SNAPSHOT = 3;
    private static final byte TYPE_BEAM_EVENT = 4;
    private static final byte TYPE_CLIENT_HELLO = 5;
    // 6 was the zone charge event, retired with the CHARGE reaction.
    private static final byte TYPE_JOIN_REJECTED = 7;
    private static final byte TYPE_LOBBY_STATE = 8;
    private static final byte TYPE_START_GAME = 9;
    private static final byte TYPE_GAME_STARTED = 10;
    private static final byte TYPE_ROOM_CLOSED = 11;
    private static final byte TYPE_KEEP_ALIVE = 12;
    private static final byte TYPE_SELECT_LOADOUT = 13;
    private static final byte TYPE_BEAM_CHANNEL_END = 14;
    private static final byte TYPE_PLAYER_KILLED = 15;
    private static final byte TYPE_MATCH_ENDED = 16;

    private static final int LENGTH_FIELD_BYTES = 4;
    private static final int MAX_MAP_ID_BYTES = 128;
    private static final int MAX_ROOM_CODE_BYTES = 16;
    // Far larger than any real snapshot; guards against garbage length values.
    private static final int MAX_FRAME_BYTES = 1 << 20;

    private GameCodec() {
    }

    // Adds framing, encoder, and decoder to a connection's pipeline.
    // Order matters: outbound data runs through the handlers right to left,
    // so the encoder runs before the length prefix is added.
    public static void install(ChannelPipeline pipeline) {
        pipeline.addLast(
            new LengthFieldPrepender(LENGTH_FIELD_BYTES),
            new Encoder(),
            new LengthFieldBasedFrameDecoder(
                MAX_FRAME_BYTES,
                0,
                LENGTH_FIELD_BYTES,
                0,
                LENGTH_FIELD_BYTES
            ),
            new Decoder()
        );
    }

    // Encodes every supported protocol message into one framed payload.
    private static final class Encoder extends MessageToByteEncoder<Object> {
        @Override
        protected void encode(
            ChannelHandlerContext ctx,
            Object message,
            ByteBuf out
        ) {
            if (message == null) {
                throw new IllegalArgumentException(
                    "Cannot encode a null message"
                );
            }
            if (message instanceof ClientHello hello) {
                out.writeByte(TYPE_CLIENT_HELLO);
                out.writeInt(hello.protocolVersion());
                writeMapId(out, hello.mapId());
                writeRoomCode(out, hello.roomCode());
                out.writeInt(hello.contentFingerprint());
            } else if (message instanceof PlayerCommand command) {
                writeCommand(out, command);
            } else if (message instanceof Welcome welcome) {
                out.writeByte(TYPE_WELCOME);
                out.writeInt(welcome.protocolVersion());
                out.writeInt(welcome.playerId());
                writeRoomCode(out, welcome.roomCode());
            } else if (message instanceof WorldSnapshot snapshot) {
                writeSnapshot(out, snapshot);
            } else if (message instanceof BeamStrikeEvent event) {
                writeBeamEvent(out, event);
            } else if (message instanceof BeamChannelEndEvent event) {
                out.writeByte(TYPE_BEAM_CHANNEL_END);
                out.writeInt(event.ownerPlayerId());
                writePower(out, event.power());
            } else if (message instanceof PlayerKilledEvent killed) {
                out.writeByte(TYPE_PLAYER_KILLED);
                out.writeInt(killed.victimPlayerId());
                out.writeInt(killed.killerPlayerId());
            } else if (message instanceof MatchEnded ended) {
                writeMatchEnded(out, ended);
            } else if (message instanceof JoinRejected rejected) {
                out.writeByte(TYPE_JOIN_REJECTED);
                out.writeByte(rejectReasonId(rejected.reason()));
            } else if (message instanceof LobbyState lobby) {
                out.writeByte(TYPE_LOBBY_STATE);
                out.writeByte(lobby.playerCount());
                out.writeByte(lobby.maxPlayers());
                out.writeByte(lobby.lockedCount());
            } else if (message instanceof StartGame) {
                out.writeByte(TYPE_START_GAME);
            } else if (message instanceof GameStarted) {
                out.writeByte(TYPE_GAME_STARTED);
            } else if (message instanceof RoomClosed) {
                out.writeByte(TYPE_ROOM_CLOSED);
            } else if (message instanceof KeepAlive) {
                out.writeByte(TYPE_KEEP_ALIVE);
            } else if (message instanceof SelectLoadout select) {
                out.writeByte(TYPE_SELECT_LOADOUT);
                out.writeByte(select.loadout().slots().size());
                for (Power power : select.loadout().slots()) {
                    writePower(out, power);
                }
                out.writeBoolean(select.locked());
            } else {
                throw new IllegalArgumentException(
                    "Unsupported message: " + message.getClass().getName()
                );
            }
        }
    }

    // One complete frame of bytes to an object. The frame decoder before this
    // handler guarantees the buffer holds exactly one whole message.
    private static final class Decoder extends MessageToMessageDecoder<ByteBuf> {
        @Override
        protected void decode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out
        ) {
            byte type = in.readByte();

            switch (type) {
                case TYPE_CLIENT_HELLO -> out.add(new ClientHello(
                    in.readInt(),
                    readMapId(in),
                    readRoomCode(in),
                    in.readInt()
                ));
                case TYPE_COMMAND -> out.add(readCommand(in));
                case TYPE_WELCOME -> out.add(new Welcome(
                    in.readInt(),
                    in.readInt(),
                    readRoomCode(in)
                ));
                case TYPE_SNAPSHOT -> out.add(readSnapshot(in));
                case TYPE_BEAM_EVENT -> out.add(readBeamEvent(in));
                case TYPE_BEAM_CHANNEL_END -> out.add(new BeamChannelEndEvent(
                    in.readInt(),
                    readPower(in)
                ));
                case TYPE_PLAYER_KILLED -> out.add(new PlayerKilledEvent(
                    in.readInt(),
                    in.readInt()
                ));
                case TYPE_MATCH_ENDED -> out.add(readMatchEnded(in));
                case TYPE_JOIN_REJECTED -> out.add(new JoinRejected(
                    rejectReasonFromId(in.readUnsignedByte())
                ));
                case TYPE_LOBBY_STATE -> out.add(new LobbyState(
                    in.readUnsignedByte(),
                    in.readUnsignedByte(),
                    in.readUnsignedByte()
                ));
                case TYPE_START_GAME -> out.add(new StartGame());
                case TYPE_GAME_STARTED -> out.add(new GameStarted());
                case TYPE_ROOM_CLOSED -> out.add(new RoomClosed());
                case TYPE_KEEP_ALIVE -> out.add(new KeepAlive());
                case TYPE_SELECT_LOADOUT -> out.add(readSelectLoadout(in));
                default -> throw new IllegalArgumentException(
                    "Unknown message type: " + type
                );
            }
        }
    }

    // ---- PlayerCommand ----

    private static void writeCommand(ByteBuf out, PlayerCommand command) {
        out.writeByte(TYPE_COMMAND);
        out.writeFloat(command.moveX());
        out.writeFloat(command.moveY());
        out.writeFloat(command.aimX());
        out.writeFloat(command.aimY());

        if (command.casts().size() > Loadout.SIZE) {
            throw new IllegalArgumentException("Too many cast requests");
        }

        out.writeByte(command.casts().size());

        for (CastRequest cast : command.casts()) {
            out.writeByte(cast.slot());
            out.writeFloat(cast.targetOffsetX());
            out.writeFloat(cast.targetOffsetY());
        }
    }

    private static PlayerCommand readCommand(ByteBuf in) {
        float moveX = in.readFloat();
        float moveY = in.readFloat();
        float aimX = in.readFloat();
        float aimY = in.readFloat();
        int castCount = in.readUnsignedByte();

        if (castCount > Loadout.SIZE) {
            throw new IllegalArgumentException(
                "Too many cast requests: " + castCount
            );
        }

        List<CastRequest> casts = new ArrayList<>(castCount);

        for (int i = 0; i < castCount; i++) {
            casts.add(new CastRequest(
                in.readUnsignedByte(),
                in.readFloat(),
                in.readFloat()
            ));
        }

        return new PlayerCommand(
            moveX,
            moveY,
            aimX,
            aimY,
            casts
        );
    }

    // ---- WorldSnapshot ----

    private static void writeSnapshot(ByteBuf out, WorldSnapshot snapshot) {
        out.writeByte(TYPE_SNAPSHOT);
        out.writeLong(snapshot.tick());
        out.writeFloat(snapshot.matchSecondsRemaining());

        out.writeShort(snapshot.players().size());
        for (WorldSnapshot.PlayerState p : snapshot.players()) {
            out.writeInt(p.id());
            out.writeByte(playerColorId(p.color()));
            out.writeFloat(p.x());
            out.writeFloat(p.y());
            out.writeFloat(p.aimX());
            out.writeFloat(p.aimY());
            out.writeBoolean(p.moving());
            out.writeFloat(p.health());
            out.writeFloat(p.respawnRemaining());
            out.writeInt(p.kills());
            out.writeInt(p.deaths());
            out.writeFloat(p.spawnShieldRemaining());

            out.writeByte(p.slots().size());
            for (WorldSnapshot.SlotState slot : p.slots()) {
                writePower(out, slot.power());
                out.writeFloat(slot.cooldownRemaining());
            }
        }

        out.writeShort(snapshot.dummies().size());
        for (WorldSnapshot.DummyState d : snapshot.dummies()) {
            out.writeInt(d.id());
            out.writeFloat(d.x());
            out.writeFloat(d.y());
            out.writeFloat(d.health());
        }

        out.writeShort(snapshot.projectiles().size());
        for (WorldSnapshot.ProjectileState p : snapshot.projectiles()) {
            out.writeInt(p.id());
            writePower(out, p.power());
            out.writeInt(p.ownerPlayerId());
            out.writeFloat(p.x());
            out.writeFloat(p.y());
            out.writeFloat(p.directionX());
            out.writeFloat(p.directionY());
        }

        out.writeShort(snapshot.zones().size());
        for (WorldSnapshot.ZoneState z : snapshot.zones()) {
            out.writeInt(z.id());
            out.writeByte(z.kind().id());
            out.writeFloat(z.x());
            out.writeFloat(z.y());
            out.writeFloat(z.radius());
            out.writeFloat(z.health());
            out.writeInt(z.damageOwnerPlayerId());
        }
    }

    private static WorldSnapshot readSnapshot(ByteBuf in) {
        long tick = in.readLong();
        float matchSecondsRemaining = in.readFloat();

        int playerCount = in.readUnsignedShort();
        List<WorldSnapshot.PlayerState> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new WorldSnapshot.PlayerState(
                in.readInt(),
                playerColorFromId(in.readUnsignedByte()),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readBoolean(),
                in.readFloat(),
                in.readFloat(),
                in.readInt(),
                in.readInt(),
                in.readFloat(),
                readSlots(in)
            ));
        }

        int dummyCount = in.readUnsignedShort();
        List<WorldSnapshot.DummyState> dummies = new ArrayList<>(dummyCount);
        for (int i = 0; i < dummyCount; i++) {
            dummies.add(new WorldSnapshot.DummyState(
                in.readInt(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat()
            ));
        }

        int projectileCount = in.readUnsignedShort();
        List<WorldSnapshot.ProjectileState> projectiles =
            new ArrayList<>(projectileCount);
        for (int i = 0; i < projectileCount; i++) {
            projectiles.add(new WorldSnapshot.ProjectileState(
                in.readInt(),
                readPower(in),
                in.readInt(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat()
            ));
        }

        int zoneCount = in.readUnsignedShort();
        List<WorldSnapshot.ZoneState> zones = new ArrayList<>(zoneCount);
        for (int i = 0; i < zoneCount; i++) {
            zones.add(new WorldSnapshot.ZoneState(
                in.readInt(),
                readZoneKind(in),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readInt()
            ));
        }

        return new WorldSnapshot(
            tick,
            matchSecondsRemaining,
            players,
            dummies,
            projectiles,
            zones
        );
    }

    // ---- MatchEnded ----

    private static void writeMatchEnded(ByteBuf out, MatchEnded ended) {
        if (ended.results().size() > GameServer.MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many match results");
        }

        out.writeByte(TYPE_MATCH_ENDED);
        out.writeByte(ended.results().size());

        for (MatchEnded.PlayerResult result : ended.results()) {
            out.writeInt(result.playerId());
            out.writeByte(playerColorId(result.color()));
            out.writeInt(result.kills());
            out.writeInt(result.deaths());
            out.writeFloat(result.damageDealt());
        }
    }

    private static MatchEnded readMatchEnded(ByteBuf in) {
        int count = in.readUnsignedByte();

        if (count > GameServer.MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many match results: " + count);
        }

        List<MatchEnded.PlayerResult> results = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            results.add(new MatchEnded.PlayerResult(
                in.readInt(),
                playerColorFromId(in.readUnsignedByte()),
                in.readInt(),
                in.readInt(),
                in.readFloat()
            ));
        }

        return new MatchEnded(results);
    }

    private static void writeMapId(ByteBuf out, String mapId) {
        byte[] bytes = mapId.getBytes(StandardCharsets.UTF_8);

        if (bytes.length == 0 || bytes.length > MAX_MAP_ID_BYTES) {
            throw new IllegalArgumentException(
                "Invalid map ID byte length: " + bytes.length
            );
        }

        out.writeShort(bytes.length);
        out.writeBytes(bytes);
    }

    private static String readMapId(ByteBuf in) {
        int byteCount = in.readUnsignedShort();

        if (byteCount == 0 || byteCount > MAX_MAP_ID_BYTES) {
            throw new IllegalArgumentException(
                "Invalid map ID byte length: " + byteCount
            );
        }

        return in.readCharSequence(byteCount, StandardCharsets.UTF_8).toString();
    }

    // Room codes may be empty: a ClientHello with no code asks for a new room.
    private static void writeRoomCode(ByteBuf out, String roomCode) {
        byte[] bytes = roomCode.getBytes(StandardCharsets.UTF_8);

        if (bytes.length > MAX_ROOM_CODE_BYTES) {
            throw new IllegalArgumentException(
                "Invalid room code byte length: " + bytes.length
            );
        }

        out.writeByte(bytes.length);
        out.writeBytes(bytes);
    }

    private static String readRoomCode(ByteBuf in) {
        int byteCount = in.readUnsignedByte();

        if (byteCount > MAX_ROOM_CODE_BYTES) {
            throw new IllegalArgumentException(
                "Invalid room code byte length: " + byteCount
            );
        }

        return in.readCharSequence(byteCount, StandardCharsets.UTF_8).toString();
    }

    private static int rejectReasonId(JoinRejected.Reason reason) {
        return switch (reason) {
            case ROOM_NOT_FOUND -> 1;
            case ROOM_FULL -> 2;
            case GAME_IN_PROGRESS -> 3;
            case SERVER_FULL -> 4;
            case WRONG_VERSION -> 5;
            case WRONG_MAP -> 6;
            case WRONG_CONTENT -> 7;
        };
    }

    private static JoinRejected.Reason rejectReasonFromId(int id) {
        return switch (id) {
            case 1 -> JoinRejected.Reason.ROOM_NOT_FOUND;
            case 2 -> JoinRejected.Reason.ROOM_FULL;
            case 3 -> JoinRejected.Reason.GAME_IN_PROGRESS;
            case 4 -> JoinRejected.Reason.SERVER_FULL;
            case 5 -> JoinRejected.Reason.WRONG_VERSION;
            case 6 -> JoinRejected.Reason.WRONG_MAP;
            case 7 -> JoinRejected.Reason.WRONG_CONTENT;
            default -> throw new IllegalArgumentException(
                "Unknown JoinRejected reason ID: " + id
            );
        };
    }

    private static int playerColorId(PlayerColor color) {
        return switch (color) {
            case CYAN -> 1;
            case RED -> 2;
            case GREEN -> 3;
            case GOLD -> 4;
        };
    }

    private static PlayerColor playerColorFromId(int id) {
        return switch (id) {
            case 1 -> PlayerColor.CYAN;
            case 2 -> PlayerColor.RED;
            case 3 -> PlayerColor.GREEN;
            case 4 -> PlayerColor.GOLD;

            default -> throw new IllegalArgumentException(
                "Unknown PlayerColor ID: " + id
            );
        };
    }

    // Powers travel as their ID from Powers, so the codec never needs to
    // know which powers exist.
    private static void writePower(ByteBuf out, Power power) {
        out.writeByte(power.id());
    }

    private static Power readPower(ByteBuf in) {
        int id = in.readUnsignedByte();
        Power power = Powers.byId(id);

        if (power == null) {
            throw new IllegalArgumentException(
                "Unknown power ID: " + id + " (is the other side running the same build?)"
            );
        }

        return power;
    }

    // A malformed loadout (wrong size) throws, which closes the connection.
    private static SelectLoadout readSelectLoadout(ByteBuf in) {
        int count = in.readUnsignedByte();
        List<Power> powers = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            powers.add(readPower(in));
        }

        return new SelectLoadout(new Loadout(powers), in.readBoolean());
    }

    private static List<WorldSnapshot.SlotState> readSlots(ByteBuf in) {
        int count = in.readUnsignedByte();
        List<WorldSnapshot.SlotState> slots = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            slots.add(new WorldSnapshot.SlotState(readPower(in), in.readFloat()));
        }

        return slots;
    }

    private static ZoneKind readZoneKind(ByteBuf in) {
        int id = in.readUnsignedByte();
        ZoneKind kind = ZoneKinds.byId(id);

        if (kind == null) {
            throw new IllegalArgumentException(
                "Unknown zone kind ID: " + id + " (is the other side running the same build?)"
            );
        }

        return kind;
    }

    private static int beamEndReasonId(
        BeamStrikeEvent.EndReason reason
    ) {
        return switch (reason) {
            case RANGE_LIMIT -> 1;
            case TERRAIN -> 2;
            case WALL -> 6;
            case ZONE_REACTION -> 3;
            case DUMMY -> 4;
            case PLAYER -> 5;
        };
    }

    private static BeamStrikeEvent.EndReason beamEndReasonFromId(int id) {
        return switch (id) {
            case 1 -> BeamStrikeEvent.EndReason.RANGE_LIMIT;
            case 2 -> BeamStrikeEvent.EndReason.TERRAIN;
            case 3 -> BeamStrikeEvent.EndReason.ZONE_REACTION;
            case 4 -> BeamStrikeEvent.EndReason.DUMMY;
            case 5 -> BeamStrikeEvent.EndReason.PLAYER;
            case 6 -> BeamStrikeEvent.EndReason.WALL;
            default -> throw new IllegalArgumentException(
                "Unknown beam end-reason ID: " + id
            );
        };
    }

    private static void writeBeamEvent(
        ByteBuf out,
        BeamStrikeEvent event
    ) {
        out.writeByte(TYPE_BEAM_EVENT);
        out.writeInt(event.ownerPlayerId());
        writePower(out, event.power());
        out.writeFloat(event.startX());
        out.writeFloat(event.startY());
        out.writeFloat(event.endX());
        out.writeFloat(event.endY());
        out.writeByte(beamEndReasonId(event.endReason()));
        out.writeByte(event.branchIndex());
    }

    private static BeamStrikeEvent readBeamEvent(
        ByteBuf in
    ) {
        return new BeamStrikeEvent(
            in.readInt(),
            readPower(in),
            in.readFloat(),
            in.readFloat(),
            in.readFloat(),
            in.readFloat(),
            beamEndReasonFromId(in.readUnsignedByte()),
            in.readUnsignedByte()
        );
    }
}
