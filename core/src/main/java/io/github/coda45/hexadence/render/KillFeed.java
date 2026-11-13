package io.github.coda45.hexadence.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.world.PlayerKilledEvent;

// The recent kills, shown on the left of the HUD above the K/D line. Each
// line fades in, stays a few seconds, then fades out.
final class KillFeed {

    private static final int MAX_ENTRIES = 5;
    private static final float FADE_IN_SECONDS = 0.15f;
    private static final float VISIBLE_SECONDS = 4f;
    private static final float FADE_OUT_SECONDS = 1f;
    private static final float LINE_HEIGHT = 16f;

    private static final Color ACTION_COLOR = new Color(0.85f, 0.85f, 0.85f, 1f);

    // Newest last.
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final GlyphLayout layout = new GlyphLayout();
    private final Color drawColor = new Color();

    private record Part(String text, Color color) {
    }

    private static final class Entry {
        private final Part[] parts;
        private float age;

        private Entry(Part... parts) {
            this.parts = parts;
        }
    }

    // Names come from the players still in the world when the event arrives.
    // The local player is shown as YOU.
    void add(PlayerKilledEvent event, Map<Integer, Player> players, int localPlayerId) {
        Part victim = name(event.victimPlayerId(), players, localPlayerId);

        Entry entry;

        if (event.hasKiller()) {
            entry = new Entry(
                name(event.killerPlayerId(), players, localPlayerId),
                new Part(" defeated ", ACTION_COLOR),
                victim
            );
        } else {
            entry = new Entry(victim, new Part(" died", ACTION_COLOR));
        }

        entries.addLast(entry);

        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    void update(float deltaSeconds) {
        Iterator<Entry> iterator = entries.iterator();

        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            entry.age += deltaSeconds;

            if (entry.age >= VISIBLE_SECONDS + FADE_OUT_SECONDS) {
                iterator.remove();
            }
        }
    }

    // Newest line at bottomY, older lines stacked above it. Call between
    // batch.begin() and batch.end().
    void draw(SpriteBatch batch, BitmapFont font, float x, float bottomY) {
        float y = bottomY;
        Iterator<Entry> newestFirst = entries.descendingIterator();

        while (newestFirst.hasNext()) {
            Entry entry = newestFirst.next();
            float alpha = alpha(entry.age);
            float cursorX = x;

            for (Part part : entry.parts) {
                drawColor.set(part.color());
                drawColor.a *= alpha;
                font.setColor(drawColor);
                layout.setText(font, part.text());
                font.draw(batch, layout, cursorX, y);
                cursorX += layout.width;
            }

            y += LINE_HEIGHT;
        }

        font.setColor(Color.WHITE);
    }

    void clear() {
        entries.clear();
    }

    private static float alpha(float age) {
        if (age < FADE_IN_SECONDS) {
            return age / FADE_IN_SECONDS;
        }

        if (age > VISIBLE_SECONDS) {
            return Math.max(0f, 1f - (age - VISIBLE_SECONDS) / FADE_OUT_SECONDS);
        }

        return 1f;
    }

    private static Part name(int playerId, Map<Integer, Player> players, int localPlayerId) {
        Player player = players.get(playerId);
        PlayerColor color = player == null ? null : player.getColor();
        Color tint = PlayerTextColors.of(color);

        if (playerId == localPlayerId) {
            return new Part("YOU", tint);
        }

        return new Part(color == null ? "SOMEONE" : color.name(), tint);
    }
}
