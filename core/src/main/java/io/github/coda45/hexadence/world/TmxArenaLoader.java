package io.github.coda45.hexadence.world;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;

// Builds an ArenaDefinition straight from a TMX file and its TSX tilesets.
//
// ArenaMap uses libGDX's TmxMapLoader, which also loads tileset textures and
// so needs an OpenGL context. The dedicated server has no window, so it uses
// this loader instead. It reads only what the simulation needs: the collision
// properties of tiles and the Spawns object layer.
//
// It follows the same rules as ArenaMap so both produce the same arena:
// - the same four layers contribute collision, keeping the strongest value;
// - "halfBlocked" wins over "blocked" on the same tile;
// - y is flipped so row 0 is the bottom, as TmxMapLoader does;
// - spawn naming and ordering go through ArenaMap.collectSpawnPoints.
public final class TmxArenaLoader {

    // Opens a file by its path, such as "maps/dungeon_map.tmx". Paths that a
    // TMX file references are resolved relative to that file's folder first.
    @FunctionalInterface
    public interface FileOpener {
        InputStream open(String path) throws IOException;
    }

    // Tiled stores flip and rotation flags in the top bits of a tile ID.
    private static final int GID_FLAG_MASK = 0xF0000000;

    private static final String[] COLLISION_LAYER_NAMES = {
        ArenaMap.GROUND_LAYER_NAME,
        ArenaMap.DECORATION_GROUND_LAYER_NAME,
        ArenaMap.WALLS_LAYER_NAME,
        ArenaMap.DECORATION_OVERLAY_LAYER_NAME
    };

    private TmxArenaLoader() {
    }

    public static ArenaDefinition load(String mapPath, FileOpener opener) {
        try {
            Element map = parse(opener, mapPath);

            int width = intAttribute(map, "width");
            int height = intAttribute(map, "height");
            int tileHeight = intAttribute(map, "tileheight");

            Map<Integer, Float> blockedByGid =
                readTilesetCollision(map, parentFolder(mapPath), opener);

            ArenaCollision collision = new ArenaCollision(
                readCollision(map, width, height, blockedByGid)
            );

            MapLayer spawns = readSpawnLayer(map, height * tileHeight);

            return new ArenaDefinition(
                collision,
                ArenaMap.collectSpawnPoints(spawns, "player"),
                ArenaMap.collectSpawnPoints(spawns, "dummy")
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                "Could not load map " + mapPath + ": " + e.getMessage(),
                e
            );
        }
    }

    // ---- Tilesets ----

    // Maps each global tile ID that has collision to its blocked height.
    private static Map<Integer, Float> readTilesetCollision(
        Element map,
        String mapFolder,
        FileOpener opener
    ) throws IOException {
        Map<Integer, Float> blockedByGid = new HashMap<>();

        for (Element tilesetRef : children(map, "tileset")) {
            int firstGid = intAttribute(tilesetRef, "firstgid");
            String source = tilesetRef.getAttribute("source");

            // A tileset is either embedded in the TMX or in its own TSX file.
            Element tileset = source.isEmpty()
                ? tilesetRef
                : parse(opener, resolve(mapFolder, source));

            for (Element tile : children(tileset, "tile")) {
                float blockedHeight = blockedHeight(tile);

                if (blockedHeight > 0f) {
                    blockedByGid.put(
                        firstGid + intAttribute(tile, "id"),
                        blockedHeight
                    );
                }
            }
        }

        return blockedByGid;
    }

    // Same rule as ArenaMap.markBlockedTiles.
    private static float blockedHeight(Element tile) {
        boolean fullyBlocked = false;
        boolean halfBlocked = false;

        for (Element properties : children(tile, "properties")) {
            for (Element property : children(properties, "property")) {
                boolean value = Boolean.parseBoolean(
                    property.getAttribute("value")
                );

                switch (property.getAttribute("name")) {
                    case "blocked" -> fullyBlocked = value;
                    case "halfBlocked" -> halfBlocked = value;
                    default -> { }
                }
            }
        }

        if (halfBlocked) {
            return 0.5f;
        }

        return fullyBlocked ? 1f : 0f;
    }

    // ---- Tile layers ----

    private static float[][] readCollision(
        Element map,
        int width,
        int height,
        Map<Integer, Float> blockedByGid
    ) throws IOException {
        float[][] blockedHeights = new float[width][height];

        for (String layerName : COLLISION_LAYER_NAMES) {
            Element layer = findByName(map, "layer", layerName);

            if (layer == null) {
                continue;
            }

            int[] gids = readLayerData(layer, width * height);

            for (int row = 0; row < height; row++) {
                // TMX row 0 is the top; the world's y = 0 is the bottom.
                int y = height - 1 - row;

                for (int x = 0; x < width; x++) {
                    int gid = gids[row * width + x] & ~GID_FLAG_MASK;
                    Float blocked = blockedByGid.get(gid);

                    if (blocked != null) {
                        blockedHeights[x][y] = Math.max(
                            blockedHeights[x][y],
                            blocked
                        );
                    }
                }
            }
        }

        return blockedHeights;
    }

    private static int[] readLayerData(Element layer, int tileCount)
        throws IOException {

        Element data = children(layer, "data").stream()
            .findFirst()
            .orElseThrow(() -> new IOException(
                "Layer " + layer.getAttribute("name") + " has no data"
            ));

        if (!children(data, "chunk").isEmpty()) {
            throw new IOException("Infinite maps are not supported");
        }

        String encoding = data.getAttribute("encoding");
        String text = data.getTextContent().trim();
        int[] gids = new int[tileCount];

        if (encoding.equals("csv")) {
            String[] values = text.split("\\s*,\\s*");

            if (values.length != tileCount) {
                throw new IOException(
                    "Layer " + layer.getAttribute("name")
                        + " has " + values.length
                        + " tiles, expected " + tileCount
                );
            }

            for (int i = 0; i < tileCount; i++) {
                // Parse as long: flipped tiles exceed Integer.MAX_VALUE.
                gids[i] = (int) Long.parseLong(values[i].trim());
            }

            return gids;
        }

        if (encoding.equals("base64")) {
            byte[] bytes = Base64.getMimeDecoder().decode(text);

            bytes = switch (data.getAttribute("compression")) {
                case "" -> bytes;
                case "zlib" -> new InflaterInputStream(
                    new ByteArrayInputStream(bytes)
                ).readAllBytes();
                case "gzip" -> new GZIPInputStream(
                    new ByteArrayInputStream(bytes)
                ).readAllBytes();
                default -> throw new IOException(
                    "Unsupported layer compression: "
                        + data.getAttribute("compression")
                        + " (use CSV or zlib in Tiled's map properties)"
                );
            };

            ByteBuffer buffer = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < tileCount; i++) {
                gids[i] = buffer.getInt();
            }

            return gids;
        }

        throw new IOException(
            "Unsupported layer encoding: '" + encoding
                + "' (use CSV in Tiled's map properties)"
        );
    }

    // ---- Spawns ----

    // Converts the Spawns object layer into the libGDX shape that
    // ArenaMap.collectSpawnPoints reads, so spawn rules live in one place.
    private static MapLayer readSpawnLayer(Element map, int mapHeightPixels) {
        Element group = findByName(
            map,
            "objectgroup",
            ArenaMap.SPAWNS_LAYER_NAME
        );

        if (group == null) {
            throw new IllegalStateException(
                "TMX map is missing the Spawns object layer"
            );
        }

        MapLayer layer = new MapLayer();

        for (Element element : children(group, "object")) {
            MapObject object = new MapObject();
            object.setName(element.getAttribute("name"));

            float x = floatAttribute(element, "x");
            float y = floatAttribute(element, "y");
            float height = floatAttribute(element, "height");

            // TmxMapLoader flips y and moves it to the object's bottom edge.
            // Spawns are points, so their height is 0.
            object.getProperties().put("x", x);
            object.getProperties().put("y", mapHeightPixels - y - height);

            layer.getObjects().add(object);
        }

        return layer;
    }

    // ---- XML helpers ----

    private static Element parse(FileOpener opener, String path)
        throws IOException {

        try (InputStream in = opener.open(path)) {
            if (in == null) {
                throw new IOException("File not found: " + path);
            }

            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            // Map files never need external entities; refusing them keeps
            // the parser from reading other files or the network.
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
            );
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);

            return document.getDocumentElement();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid XML in " + path, e);
        }
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);

            if (
                node.getNodeType() == Node.ELEMENT_NODE
                && node.getNodeName().equals(tag)
            ) {
                result.add((Element) node);
            }
        }

        return result;
    }

    private static Element findByName(Element map, String tag, String name) {
        for (Element element : children(map, tag)) {
            if (element.getAttribute("name").equals(name)) {
                return element;
            }
        }

        return null;
    }

    private static int intAttribute(Element element, String name) {
        return Integer.parseInt(element.getAttribute(name));
    }

    private static float floatAttribute(Element element, String name) {
        String value = element.getAttribute(name);

        return value.isEmpty() ? 0f : Float.parseFloat(value);
    }

    // ---- Paths ----

    private static String parentFolder(String path) {
        int slash = path.lastIndexOf('/');

        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    // Joins "maps/" and "../tiles/a.tsx" into "tiles/a.tsx".
    static String resolve(String folder, String relative) {
        Deque<String> parts = new ArrayDeque<>();

        for (String part : (folder + relative).split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }

            if (part.equals("..") && !parts.isEmpty()) {
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }

        return String.join("/", parts);
    }
}
