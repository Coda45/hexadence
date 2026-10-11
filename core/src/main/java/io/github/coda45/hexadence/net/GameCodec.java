package io.github.coda45.hexadence.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.spell.SpellType;
import io.github.coda45.hexadence.world.LightningStrikeEvent;
import io.github.coda45.hexadence.world.ZoneChargeChangedEvent;
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

    public static final int PROTOCOL_VERSION = 4;

    private static final byte TYPE_COMMAND = 1;
    private static final byte TYPE_WELCOME = 2;
    private static final byte TYPE_SNAPSHOT = 3;
    private static final byte TYPE_LIGHTNING_EVENT = 4;
    private static final byte TYPE_CLIENT_HELLO = 5;
    private static final byte TYPE_ZONE_CHARGE_CHANGED = 6;

    private static final int LENGTH_FIELD_BYTES = 4;
    private static final int MAX_MAP_ID_BYTES = 128;
    private static final int KNOWN_COMMAND_FLAGS = 0b111;

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
            } else if (message instanceof PlayerCommand command) {
                writeCommand(out, command);
            } else if (message instanceof Welcome welcome) {
                out.writeByte(TYPE_WELCOME);
                out.writeInt(welcome.protocolVersion());
                out.writeInt(welcome.playerId());
            } else if (message instanceof WorldSnapshot snapshot) {
                writeSnapshot(out, snapshot);
            } else if (message instanceof LightningStrikeEvent event) {
                writeLightningEvent(out, event);
            } else if (message instanceof ZoneChargeChangedEvent event) {
                out.writeByte(TYPE_ZONE_CHARGE_CHANGED);
                out.writeInt(event.zoneId());
                out.writeBoolean(event.electrified());
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
                case TYPE_CLIENT_HELLO -> out.add(new ClientHello(in.readInt(), readMapId(in)));
                case TYPE_COMMAND -> out.add(readCommand(in));
                case TYPE_WELCOME -> out.add(new Welcome(in.readInt(), in.readInt()));
                case TYPE_SNAPSHOT -> out.add(readSnapshot(in));
                case TYPE_LIGHTNING_EVENT -> out.add(readLightningEvent(in));
                case TYPE_ZONE_CHARGE_CHANGED -> out.add(new ZoneChargeChangedEvent(in.readInt(), in.readBoolean()));
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

        // The three cast booleans share one byte.
        int flags =
            (command.castEmber() ? 1 : 0)
            | (command.castWater() ? 2 : 0)
            | (command.castLightning() ? 4 : 0);

        out.writeByte(flags);
    }

    private static PlayerCommand readCommand(ByteBuf in) {
        float moveX = in.readFloat();
        float moveY = in.readFloat();
        float aimX = in.readFloat();
        float aimY = in.readFloat();
        int flags = in.readUnsignedByte();

        if ((flags & ~KNOWN_COMMAND_FLAGS) != 0) {
            throw new IllegalArgumentException(
                "Unknown PlayerCommand flags: " + flags
            );
        }

        return new PlayerCommand(
            moveX,
            moveY,
            aimX,
            aimY,
            (flags & 1) != 0,
            (flags & 2) != 0,
            (flags & 4) != 0
        );
    }

    // ---- WorldSnapshot ----

    private static void writeSnapshot(ByteBuf out, WorldSnapshot snapshot) {
        out.writeByte(TYPE_SNAPSHOT);
        out.writeLong(snapshot.tick());

        out.writeShort(snapshot.players().size());
        for (WorldSnapshot.PlayerState p : snapshot.players()) {
            out.writeInt(p.id());
            out.writeFloat(p.x());
            out.writeFloat(p.y());
            out.writeFloat(p.aimX());
            out.writeFloat(p.aimY());
            out.writeBoolean(p.moving());
            out.writeFloat(p.health());
            out.writeFloat(p.respawnRemaining());
            out.writeInt(p.kills());
            out.writeInt(p.deaths());
            out.writeFloat(p.emberCooldown());
            out.writeFloat(p.waterCooldown());
            out.writeFloat(p.lightningCooldown());
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
            out.writeByte(spellTypeId(p.spellType()));
            out.writeInt(p.ownerPlayerId());
            out.writeFloat(p.x());
            out.writeFloat(p.y());
            out.writeFloat(p.directionX());
            out.writeFloat(p.directionY());
        }

        out.writeShort(snapshot.zones().size());
        for (WorldSnapshot.ZoneState z : snapshot.zones()) {
            out.writeInt(z.id());
            out.writeByte(zoneTypeId(z.type()));
            out.writeFloat(z.x());
            out.writeFloat(z.y());
            out.writeBoolean(z.electrified());
            out.writeInt(z.damageOwnerPlayerId());
        }
    }

    private static WorldSnapshot readSnapshot(ByteBuf in) {
        long tick = in.readLong();

        int playerCount = in.readUnsignedShort();
        List<WorldSnapshot.PlayerState> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new WorldSnapshot.PlayerState(
                in.readInt(),
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
                in.readFloat(),
                in.readFloat()
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
                spellTypeFromId(in.readUnsignedByte()),
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
                zoneTypeFromId(in.readUnsignedByte()),
                in.readFloat(),
                in.readFloat(),
                in.readBoolean(),
                in.readInt()
            ));
        }

        return new WorldSnapshot(tick, players, dummies, projectiles, zones);
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

    private static int spellTypeId(SpellType type) {
        return switch (type) {
            case EMBER -> 1;
            case WATER -> 2;
            case LIGHTNING -> 3;
        };
    }

    private static SpellType spellTypeFromId(int id) {
        return switch (id) {
            case 1 -> SpellType.EMBER;
            case 2 -> SpellType.WATER;
            case 3 -> SpellType.LIGHTNING;
            default -> throw new IllegalArgumentException(
                "Unknown SpellType ID: " + id
            );
        };
    }

    private static int zoneTypeId(Zone.Type type) {
        return switch (type) {
            case PUDDLE -> 1;
            case STEAM -> 2;
        };
    }

    private static Zone.Type zoneTypeFromId(int id) {
        return switch (id) {
            case 1 -> Zone.Type.PUDDLE;
            case 2 -> Zone.Type.STEAM;
            default -> throw new IllegalArgumentException(
                "Unknown Zone.Type ID: " + id
            );
        };
    }

    private static int lightningEndReasonId(
        LightningStrikeEvent.EndReason reason
    ) {
        return switch (reason) {
            case RANGE_LIMIT -> 1;
            case TERRAIN -> 2;
            case PUDDLE -> 3;
            case DUMMY -> 4;
            case PLAYER -> 5;
        };
    }

    private static LightningStrikeEvent.EndReason
    lightningEndReasonFromId(int id) {
        return switch (id) {
            case 1 -> LightningStrikeEvent.EndReason.RANGE_LIMIT;
            case 2 -> LightningStrikeEvent.EndReason.TERRAIN;
            case 3 -> LightningStrikeEvent.EndReason.PUDDLE;
            case 4 -> LightningStrikeEvent.EndReason.DUMMY;
            case 5 -> LightningStrikeEvent.EndReason.PLAYER;
            default -> throw new IllegalArgumentException(
                "Unknown Lightning end-reason ID: " + id
            );
        };
    }

    private static void writeLightningEvent(
        ByteBuf out,
        LightningStrikeEvent event
    ) {
        out.writeByte(TYPE_LIGHTNING_EVENT);
        out.writeFloat(event.startX());
        out.writeFloat(event.startY());
        out.writeFloat(event.endX());
        out.writeFloat(event.endY());
        out.writeByte(lightningEndReasonId(event.endReason()));
    }

    private static LightningStrikeEvent readLightningEvent(
        ByteBuf in
    ) {
        return new LightningStrikeEvent(
            in.readFloat(),
            in.readFloat(),
            in.readFloat(),
            in.readFloat(),
            lightningEndReasonFromId(in.readUnsignedByte())
        );
    }
}
