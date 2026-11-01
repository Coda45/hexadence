package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.LoadoutRules;

// Non-pausing in-game overlay. ArenaScreen keeps stepping underneath it and
// substitutes idle gameplay input while this Stage owns the mouse and keys.
final class PauseMenu {

    private static final float VIRTUAL_WIDTH = 640f;
    private static final float VIRTUAL_HEIGHT = 360f;
    private static final float PANEL_WIDTH = 560f;
    private static final float PANEL_HEIGHT = 320f;

    private final Stage stage;
    private final BitmapFont titleFont;
    private final BitmapFont font;
    private final Texture panelTexture;
    private final LoadoutPicker picker;

    private boolean resumeRequested;
    private boolean quitRequested;

    PauseMenu(boolean training, Loadout initialLoadout) {
        stage = new Stage(new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT));
        titleFont = new BitmapFont();
        titleFont.getData().setScale(1.6f);
        font = new BitmapFont();

        TextButton.TextButtonStyle mainButtonStyle =
            new TextButton.TextButtonStyle();
        mainButtonStyle.font = titleFont;
        mainButtonStyle.fontColor = Color.WHITE;
        mainButtonStyle.overFontColor = Color.GOLD;

        TextButton.TextButtonStyle smallButtonStyle =
            new TextButton.TextButtonStyle();
        smallButtonStyle.font = font;
        smallButtonStyle.fontColor = Color.WHITE;
        smallButtonStyle.overFontColor = Color.GOLD;

        Label.LabelStyle titleText = new Label.LabelStyle(titleFont, Color.WHITE);
        Label.LabelStyle whiteText = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle goldText = new Label.LabelStyle(font, Color.GOLD);

        panelTexture = createPanelTexture();
        NinePatch panelPatch = new NinePatch(panelTexture, 1, 1, 1, 1);

        Table panel = new Table();
        panel.setBackground(new NinePatchDrawable(panelPatch));
        panel.pad(18f);

        Label note = new Label(
            "THE SIMULATION CONTINUES WHILE THIS MENU IS OPEN",
            goldText
        );
        note.setAlignment(Align.center);

        panel.add(new Label("GAME MENU", titleText)).padBottom(5f).row();
        panel.add(note).padBottom(10f).row();

        if (training) {
            picker = new LoadoutPicker(
                smallButtonStyle,
                whiteText,
                goldText,
                false
            );
            picker.setLoadout(initialLoadout);
            panel.add(picker).width(LoadoutPicker.WIDTH).padBottom(8f).row();
        } else {
            picker = null;
            Label multiplayerNote = new Label(
                "You will leave the current server room when you quit.",
                whiteText
            );
            multiplayerNote.setAlignment(Align.center);
            panel.add(multiplayerNote).padTop(35f).padBottom(45f).row();
        }

        TextButton resumeButton = new TextButton("[ RESUME ]", mainButtonStyle);
        TextButton quitButton = new TextButton(
            "[ QUIT TO MAIN MENU ]",
            mainButtonStyle
        );

        resumeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                resumeRequested = true;
            }
        });

        quitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                quitRequested = true;
            }
        });

        panel.add(resumeButton).padTop(2f).row();
        panel.add(quitButton).padTop(4f);

        Table root = new Table();
        root.setFillParent(true);
        root.add(panel).width(PANEL_WIDTH).height(PANEL_HEIGHT);
        stage.addActor(root);
    }

    private static Texture createPanelTexture() {
        Pixmap pixmap = new Pixmap(3, 3, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.015f, 0.02f, 0.035f, 0.88f);
        pixmap.fill();
        pixmap.setColor(0.85f, 0.68f, 0.2f, 0.95f);
        pixmap.drawRectangle(0, 0, 3, 3);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    InputProcessor inputProcessor() {
        return stage;
    }

    boolean ownsInputProcessor(InputProcessor processor) {
        return processor == stage;
    }

    void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    boolean takeResumeRequest() {
        boolean requested = resumeRequested;
        resumeRequested = false;
        return requested;
    }

    boolean takeQuitRequest() {
        boolean requested = quitRequested;
        quitRequested = false;
        return requested;
    }

    Loadout takeLoadoutChange() {
        if (picker == null || !picker.takeChoiceChange()) {
            return null;
        }

        Loadout selected = picker.loadout();
        return LoadoutRules.problem(selected) == null ? selected : null;
    }

    void dispose() {
        stage.dispose();
        titleFont.dispose();
        font.dispose();
        panelTexture.dispose();
    }
}
