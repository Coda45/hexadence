package io.github.coda45.hexadence.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.entity.Player;

class PlayerInputControllerTest {

    @Test
    void zeroSizedViewportKeepsThePlayersLastFiniteAim() {
        PlayerInputController controller = new PlayerInputController();
        FitViewport minimizedViewport = new FitViewport(
            10f,
            10f,
            new OrthographicCamera()
        );
        Player player = new Player(2f, 3f);

        Vector2 target = controller.targetOffset(
            minimizedViewport,
            player,
            0,
            0
        );

        assertTrue(Float.isFinite(target.x));
        assertTrue(Float.isFinite(target.y));
        assertEquals(player.getAimX(), target.x, 0.0001f);
        assertEquals(player.getAimY(), target.y, 0.0001f);
    }
}
