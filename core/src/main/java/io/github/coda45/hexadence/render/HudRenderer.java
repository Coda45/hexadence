package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.spell.SpellType;

public class HudRenderer {

    private static final float HUD_WIDTH = 640f;
    private static final float HUD_HEIGHT = 360f;

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

        Pixmap pixmap =
            new Pixmap(1, 1, Pixmap.Format.RGBA8888);

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

        batch.begin();

        drawHealth(player);

        drawCooldown(
            player,
            SpellType.EMBER,
            "Ember [LMB]",
            260f,
            Color.ORANGE
        );

        drawCooldown(
            player,
            SpellType.WATER,
            "Water [RMB]",
            220f,
            Color.CYAN
        );

        drawCooldown(
            player,
            SpellType.LIGHTNING,
            "Lightning [Q]",
            180f,
            Color.YELLOW
        );

        batch.end();
    }

    private void drawHealth(Player player) {
        float x = 20f;
        float y = 315f;
        float width = 180f;
        float height = 14f;

        float healthFraction =
            player.getHealth() / player.getMaxHealth();

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

    private void drawCooldown(
        Player player,
        SpellType spellType,
        String label,
        float y,
        Color color
    ) {
        float x = 20f;
        float width = 150f;
        float height = 10f;

        float remaining =
            player.getCooldownRemaining(spellType);

        float readyFraction =
            1f - remaining / spellType.getCooldownSeconds();

        String status = remaining <= 0f
            ? "READY"
            : String.format("%.2f s", remaining);

        font.draw(
            batch,
            label + ": " + status,
            x,
            y + 24f
        );

        drawBar(
            x,
            y,
            width,
            height,
            readyFraction,
            color
        );
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

    public void dispose() {
        batch.dispose();
        font.dispose();
        pixelTexture.dispose();
    }
}