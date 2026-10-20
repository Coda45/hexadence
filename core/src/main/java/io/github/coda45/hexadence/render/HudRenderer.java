package io.github.coda45.hexadence.render;

import java.util.EnumMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.spell.SpellType;

public class HudRenderer {

    private static final float HUD_WIDTH = 640f;
    private static final float HUD_HEIGHT = 360f;
    private static final float ABILITY_WIDTH = 35f;
    private static final float ABILITY_HEIGHT = 44f;
    private static final float ABILITY_GAP = 12f;
    private static final float ABILITY_BOTTOM = 8f;
    private static final float COOLDOWN_RADIUS = 16f;
    private static final int COOLDOWN_SEGMENTS = 48;

    private static final SpellType[] HUD_SPELLS = {
        SpellType.EMBER,
        SpellType.WATER,
        SpellType.LIGHTNING
    };

    private final ShapeRenderer shapeRenderer;

    private final EnumMap<SpellType, AbilityIcon>
        abilityIcons = new EnumMap<>(SpellType.class);

    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final Texture pixelTexture;

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

        abilityIcons.put(
            SpellType.EMBER,
            new AbilityIcon(
                loadUiTexture("ui/bg_fireball.png"),
                loadUiTexture("ui/rim_orange.png"),
                "LMB"
            )
        );

        abilityIcons.put(
            SpellType.WATER,
            new AbilityIcon(
                loadUiTexture("ui/bg_water_bolt.png"),
                loadUiTexture("ui/rim_blue.png"),
                "RMB"
            )
        );

        abilityIcons.put(
            SpellType.LIGHTNING,
            new AbilityIcon(
                loadUiTexture("ui/bg_lightning.png"),
                loadUiTexture("ui/rim_yellow.png"),
                "Q"
            )
        );

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);

        pixmap.setColor(Color.WHITE);
        pixmap.fill();

        pixelTexture = new Texture(pixmap);
        pixmap.dispose();
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void render(Player player) {
        viewport.apply();

        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // First layer: regular HUD and ability backgrounds.
        batch.begin();

        drawScore(player);
        drawHealth(player);

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

        drawAbilityBackgrounds();

        batch.end();

        // Second layer: transparent radial cooldown masks.
        drawCooldownMasks(player);

        // Final layer: rims and input labels.
        batch.begin();

        drawAbilityRims();

        batch.end();
    }

    private void drawAbilityBackgrounds() {
        for (int index = 0; index < HUD_SPELLS.length; index++) {
            SpellType spellType = HUD_SPELLS[index];
            AbilityIcon icon = abilityIcons.get(spellType);

            float x = abilityX(index);

            batch.draw(
                icon.background(),
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
            Color.LIME
        );
    }

    private float abilityX(int index) {
        float totalWidth =
            HUD_SPELLS.length * ABILITY_WIDTH
            + (HUD_SPELLS.length - 1) * ABILITY_GAP;

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

        for (int index = 0; index < HUD_SPELLS.length; index++) {
            SpellType spellType = HUD_SPELLS[index];

            float remaining = player.getCooldownRemaining(spellType);

            float remainingFraction = remaining / spellType.getCooldownSeconds();

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

    private void drawAbilityRims() {
        for (int index = 0; index < HUD_SPELLS.length; index++) {
            SpellType spellType = HUD_SPELLS[index];
            AbilityIcon icon = abilityIcons.get(spellType);

            float x = abilityX(index);

            batch.draw(
                icon.rim(),
                x,
                ABILITY_BOTTOM,
                ABILITY_WIDTH,
                ABILITY_HEIGHT
            );

            font.draw(
                batch,
                icon.binding(),
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
            20f,
            150f
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

    private record AbilityIcon(
        Texture background,
        Texture rim,
        String binding
    ) {
        private void dispose() {
            background.dispose();
            rim.dispose();
        }
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
        pixelTexture.dispose();
        shapeRenderer.dispose();

        for (AbilityIcon icon : abilityIcons.values()) {
            icon.dispose();
        }
    }
}