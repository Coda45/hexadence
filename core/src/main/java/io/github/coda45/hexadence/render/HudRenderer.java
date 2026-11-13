package io.github.coda45.hexadence.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.world.PlayerKilledEvent;
import io.github.coda45.hexadence.world.WorldEvent;

public class HudRenderer {

    private static final float HUD_WIDTH = 640f;
    private static final float HUD_HEIGHT = 360f;
    private static final float ABILITY_WIDTH = 35f;
    private static final float ABILITY_HEIGHT = 44f;
    private static final float ABILITY_GAP = 12f;
    private static final float ABILITY_BOTTOM = 8f;
    private static final float COOLDOWN_RADIUS = 16f;
    private static final int COOLDOWN_SEGMENTS = 48;
    // The K/D line; the kill feed stacks upward from just above it.
    private static final float SCORE_X = 20f;
    private static final float SCORE_Y = 150f;
    private static final float KILL_FEED_BOTTOM = SCORE_Y + 22f;
    // The timer turns red for the final seconds.
    private static final float TIMER_WARNING_SECONDS = 10f;

    // Key shown on each loadout slot's icon.
    private static final String[] SLOT_BINDINGS = { "LMB", "RMB", "Q" };

    private final ShapeRenderer shapeRenderer;

    // Icon images by path, loaded the first time a power using them appears.
    private final Map<String, Texture> iconTextures = new HashMap<>();

    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final Texture pixelTexture;
    private final KillFeed killFeed = new KillFeed();
    private final GlyphLayout layout = new GlyphLayout();

    // Seconds left in a timed match, or negative for no timer.
    private float matchSecondsRemaining = -1f;

    public HudRenderer() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(
            HUD_WIDTH,
            HUD_HEIGHT,
            camera
        );

        batch = new SpriteBatch();
        font = new BitmapFont();

        shapeRenderer = new ShapeRenderer();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);

        pixmap.setColor(Color.WHITE);
        pixmap.fill();

        pixelTexture = new Texture(pixmap);
        pixmap.dispose();
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    // Negative hides the timer (single-player, or before the first snapshot).
    public void setMatchTimer(float secondsRemaining) {
        matchSecondsRemaining = secondsRemaining;
    }

    // Feeds this frame's world events to the kill feed. Call before render.
    public void recordEvents(
        List<WorldEvent> events,
        Map<Integer, Player> players,
        int localPlayerId
    ) {
        for (WorldEvent event : events) {
            if (event instanceof PlayerKilledEvent killed) {
                killFeed.add(killed, players, localPlayerId);
            }
        }
    }

    public void render(Player player, float deltaSeconds) {
        killFeed.update(deltaSeconds);

        viewport.apply();

        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // First layer: regular HUD and ability backgrounds.
        batch.begin();

        drawScore(player);
        killFeed.draw(batch, font, SCORE_X, KILL_FEED_BOTTOM);
        drawHealth(player);
        drawSpawnShield(player);
        drawMatchTimer();

        if (!player.isAlive()) {
            font.draw(
                batch,
                String.format(
                    "Respawning in %.1f",
                    Math.max(
                        0f,
                        player.getRespawnRemaining()
                    )
                ),
                250f,
                190f
            );
        }

        drawAbilityBackgrounds(player);

        batch.end();

        // Second layer: transparent radial cooldown masks.
        drawCooldownMasks(player);

        // Final layer: rims and input labels.
        batch.begin();

        drawAbilityRims(player);

        batch.end();
    }

    private void drawAbilityBackgrounds(Player player) {
        for (int index = 0; index < Loadout.SIZE; index++) {
            Power power = player.getPower(index);

            float x = abilityX(index);

            batch.draw(
                iconTexture(power.iconPath()),
                x,
                ABILITY_BOTTOM,
                ABILITY_WIDTH,
                ABILITY_HEIGHT
            );
        }
    }

    private void drawHealth(Player player) {
        float x = 20f;
        float y = 315f;
        float width = 180f;
        float height = 14f;

        float healthFraction = player.getHealth() / player.getMaxHealth();

        font.draw(
            batch,
            "Health: "
                + Math.round(player.getHealth())
                + " / "
                + Math.round(player.getMaxHealth()),
            x,
            y + 28f
        );

        drawBar(
            x,
            y,
            width,
            height,
            healthFraction,
            player.isLowHealth() ? Color.RED : Color.LIME
        );
    }

    private void drawSpawnShield(Player player) {
        if (!player.isAlive() || !player.hasSpawnShield()) {
            return;
        }

        font.setColor(Color.CYAN);
        font.draw(
            batch,
            String.format("Spawn shield %.1fs", player.getSpawnShieldRemaining()),
            20f,
            308f
        );
        font.setColor(Color.WHITE);
    }

    // m:ss at the top center.
    private void drawMatchTimer() {
        if (matchSecondsRemaining < 0f) {
            return;
        }

        int totalSeconds = (int) Math.ceil(matchSecondsRemaining);
        String text = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);

        font.getData().setScale(1.5f);
        font.setColor(
            matchSecondsRemaining <= TIMER_WARNING_SECONDS ? Color.RED : Color.WHITE
        );
        layout.setText(font, text);
        font.draw(batch, layout, (HUD_WIDTH - layout.width) / 2f, HUD_HEIGHT - 10f);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private float abilityX(int index) {
        float totalWidth =
            Loadout.SIZE * ABILITY_WIDTH
            + (Loadout.SIZE - 1) * ABILITY_GAP;

        float startX = (HUD_WIDTH - totalWidth) / 2f;

        return startX + index * (ABILITY_WIDTH + ABILITY_GAP);
    }

    private void drawCooldownMasks(Player player) {
        Gdx.gl.glEnable(GL20.GL_BLEND);

        Gdx.gl.glBlendFunc(
            GL20.GL_SRC_ALPHA,
            GL20.GL_ONE_MINUS_SRC_ALPHA
        );

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(
            0f,
            0f,
            0f,
            0.65f
        );

        for (int index = 0; index < Loadout.SIZE; index++) {
            float remaining = player.getCooldownRemaining(index);

            float remainingFraction =
                remaining / player.getPower(index).cooldownSeconds();

            remainingFraction = Math.max(
                0f,
                Math.min(1f, remainingFraction)
            );

            if (remainingFraction <= 0f) {
                continue;
            }

            float centerX = abilityX(index)  + ABILITY_WIDTH / 2f;
            float centerY = ABILITY_BOTTOM   + ABILITY_HEIGHT / 2f;

            drawCooldownSector(
                centerX,
                centerY,
                COOLDOWN_RADIUS,
                remainingFraction
            );
        }

        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawCooldownSector(
        float centerX,
        float centerY,
        float radius,
        float remainingFraction
    ) {
        float sweepDegrees = 360f * remainingFraction;

        int segments = Math.max(1, (int) Math.ceil(COOLDOWN_SEGMENTS * remainingFraction));

        for (int segment = 0; segment < segments; segment++) {
            float firstProgress =  (float) segment / segments;
            float secondProgress = (float) (segment + 1) / segments;
 
            // Begin at twelve o'clock and sweep clockwise.
            float firstAngle  = 90f - sweepDegrees * firstProgress;
            float secondAngle = 90f - sweepDegrees * secondProgress;
            float firstX      = centerX + MathUtils.cosDeg(firstAngle) * radius;
            float firstY      = centerY + MathUtils.sinDeg(firstAngle) * radius;
            float secondX     = centerX + MathUtils.cosDeg(secondAngle) * radius;
            float secondY     = centerY + MathUtils.sinDeg(secondAngle) * radius;

            shapeRenderer.triangle(
                centerX,
                centerY,
                firstX,
                firstY,
                secondX,
                secondY
            );
        }
    }

    private void drawAbilityRims(Player player) {
        for (int index = 0; index < Loadout.SIZE; index++) {
            Power power = player.getPower(index);

            float x = abilityX(index);

            batch.draw(
                iconTexture(power.iconRimPath()),
                x,
                ABILITY_BOTTOM,
                ABILITY_WIDTH,
                ABILITY_HEIGHT
            );

            font.draw(
                batch,
                SLOT_BINDINGS[index],
                x + 25f,
                ABILITY_BOTTOM + 12f
            );
        }
    }

    private void drawBar(
        float x,
        float y,
        float width,
        float height,
        float fraction,
        Color color
    ) {
        float clampedFraction =
            Math.max(0f, Math.min(1f, fraction));

        batch.setColor(Color.DARK_GRAY);
        batch.draw(
            pixelTexture,
            x,
            y,
            width,
            height
        );

        batch.setColor(color);
        batch.draw(
            pixelTexture,
            x,
            y,
            width * clampedFraction,
            height
        );

        batch.setColor(Color.WHITE);
    }

    private void drawScore(Player player) {
        font.draw(
            batch,
            "Kills: " + player.getKills()
                + "  Deaths: " + player.getDeaths(),
            SCORE_X,
            SCORE_Y
        );
    }

    private Texture loadUiTexture(String path) {
        Texture texture = new Texture(path);

        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        return texture;
    }

    private Texture iconTexture(String path) {
        return iconTextures.computeIfAbsent(path, this::loadUiTexture);
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
        pixelTexture.dispose();
        shapeRenderer.dispose();

        for (Texture texture : iconTextures.values()) {
            texture.dispose();
        }
    }
}