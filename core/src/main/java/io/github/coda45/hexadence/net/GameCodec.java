package io.github.coda45.hexadence.net;

import java.util.ArrayList;
import java.util.List;

import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.spell.SpellType;
import io.github.coda45.hexadence.world.LightningStrikeEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

// Turns the three network messages into bytes and back. Server and client both
// call install() so they always agree on the format.
//
// Every message on the wire looks like:
//   [4-byte length][1-byte type][fields...]
// The length prefix lets the receiver cut the byte stream into whole messages
//
// Enums travel as their ordinal numbers, so server and client must be built
// from the same version of the code.
public final class GameCodec {

    private static final byte TYPE_COMMAND = 1;
    private static final byte TYPE_WELCOME = 2;
    private static final byte TYPE_SNAPSHOT = 3;

    private static final int LENGTH_FIELD_BYTES = 4;
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

    // Object to bytes. Handles PlayerCommand, Welcome, and WorldSnapshot.
    private static final class Encoder extends MessageToByteEncoder<Object> {
        @Override
        protected void encode(
            ChannelHandlerContext ctx,
            Object message,
            ByteBuf out
        ) {
            if (message instanceof PlayerCommand command) {
                writeCommand(out, command);
            } else if (message instanceof Welcome welcome) {
                out.writeByte(TYPE_WELCOME);
                out.writeInt(welcome.playerId());
            } else if (message instanceof WorldSnapshot snapshot) {
                writeSnapshot(out, snapshot);
            } else {
                throw new IllegalArgumentException(
                    "Unsupported message: " + message.getClass()
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
                case TYPE_COMMAND -> out.add(readCommand(in));
                case TYPE_WELCOME -> out.add(new Welcome(in.readInt()));
                case TYPE_SNAPSHOT -> out.add(readSnapshot(in));
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
            out.writeFloat(p.emberCooldown());
            out.writeFloat(p.waterCooldown());
            out.writeFloat(p.lightningCooldown());
        }

        out.writeShort(snapshot.dummies().size());
        for (WorldSnapshot.DummyState d : snapshot.dummies()) {
            out.writeFloat(d.x());
            out.writeFloat(d.y());
            out.writeFloat(d.health());
        }

        out.writeShort(snapshot.projectiles().size());
        for (WorldSnapshot.ProjectileState p : snapshot.projectiles()) {
            out.writeByte(p.spellType().ordinal());
            out.writeFloat(p.x());
            out.writeFloat(p.y());
            out.writeFloat(p.directionX());
            out.writeFloat(p.directionY());
        }

        out.writeShort(snapshot.zones().size());
        for (WorldSnapshot.ZoneState z : snapshot.zones()) {
            out.writeByte(z.type().ordinal());
            out.writeFloat(z.x());
            out.writeFloat(z.y());
            out.writeBoolean(z.electrified());
        }

        out.writeShort(snapshot.events().size());
        for (LightningStrikeEvent e : snapshot.events()) {
            out.writeFloat(e.startX());
            out.writeFloat(e.startY());
            out.writeFloat(e.endX());
            out.writeFloat(e.endY());
            out.writeByte(e.endReason().ordinal());
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
                in.readFloat(),
                in.readFloat()
            ));
        }

        int dummyCount = in.readUnsignedShort();
        List<WorldSnapshot.DummyState> dummies = new ArrayList<>(dummyCount);
        for (int i = 0; i < dummyCount; i++) {
            dummies.add(new WorldSnapshot.DummyState(
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
                SpellType.values()[in.readUnsignedByte()],
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
                Zone.Type.values()[in.readUnsignedByte()],
                in.readFloat(),
                in.readFloat(),
                in.readBoolean()
            ));
        }

        int eventCount = in.readUnsignedShort();
        List<LightningStrikeEvent> events = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            events.add(new LightningStrikeEvent(
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                LightningStrikeEvent.EndReason.values()[in.readUnsignedByte()]
            ));
        }

        return new WorldSnapshot(
            tick, players, dummies, projectiles, zones, events
        );
    }
}