package io.github.coda45.hexadence.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.HexadenceGame;
import io.github.coda45.hexadence.net.MatchEnded;
import io.github.coda45.hexadence.net.NetClient;
import io.github.coda45.hexadence.net.NetSession;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.render.PlayerTextColors;

// Shown when a match's timer runs out: every player's kills, deaths and
// damage dealt, best first. RETURN TO LOBBY goes back to the same room for a
// rematch, on the same connection; MAIN MENU leaves the room.
//
// If the host has left, the room is closed and RETURN TO LOBBY is disabled.
public class ResultsScreen implements Screen {

    private static final String TITLE_TEXT = "MATCH OVER";
    private static final String LOBBY_TEXT = "[ RETURN TO LOBBY ]";
    private static final String MENU_TEXT = "[ MAIN MENU ]";
    private static final float MENU_WIDTH = 640f;
    private static final float MENU_HEIGHT = 360f;
    private static final float NAME_COLUMN_WIDTH = 140f;
    private static final float STAT_COLUMN_WIDTH = 80f;

    private final HexadenceGame game;
    private final NetSession session;
    // The powers this player used, so the lobby can pre-select them.
    private final Loadout lastLoadout;

    private final BitmapFont font;
    private final BitmapFont smallFont;
    private final Texture image;
    private final Stage stage;
    private final TextButton lobbyButton;
    private final Label statusLabel;

    // Set by button listeners and handled after stage.act(), so the stage is
    // never disposed while it is still processing input.
    private boolean lobbyRequested;
    private boolean menuRequested;
    // True once another screen owns the connection.
    private boolean sessionHandedOff;

    public ResultsScreen(
        HexadenceGame game,
        NetSession session,
        MatchEnded results,
        Loadout lastLoadout
    ) {
        this.game = game;
        this.session = session;
        this.lastLoadout = lastLoadout;

        image = new Texture("host_lobbytest2.png");
        font = new BitmapFont();
        font.getData().setScale(1.5f);
        smallFont = new BitmapFont();

        stage = new Stage(new FitViewport(MENU_WIDTH, MENU_HEIGHT));

        // Styles built in code, so no skin file is needed.
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.overFontColor = Color.GOLD;
        buttonStyle.disabledFontColor = Color.DARK_GRAY;

        Label.LabelStyle titleText = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle bannerText = new Label.LabelStyle(font, Color.GOLD);
        Label.LabelStyle headerText = new Label.LabelStyle(smallFont, Color.GOLD);
        Label.LabelStyle whiteText = new Label.LabelStyle(smallFont, Color.WHITE);

        List<MatchEnded.PlayerResult> ranked = rank(results.results());
        int localPlayerId = session.client().getPlayerId();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(image));

        root.add(new Label(TITLE_TEXT, titleText)).padBottom(4).row();
        root.add(new Label(banner(ranked, localPlayerId), bannerText)).padBottom(12).row();

        // The stats table: one header row, then one row per player.
        Table stats = new Table();
        addCell(stats, "PLAYER", headerText, NAME_COLUMN_WIDTH, Align.left);
        addCell(stats, "KILLS", headerText, STAT_COLUMN_WIDTH, Align.center);
        addCell(stats, "DEATHS", headerText, STAT_COLUMN_WIDTH, Align.center);
        addCell(stats, "DAMAGE", headerText, STAT_COLUMN_WIDTH, Align.center);
        stats.row().padTop(4);

        for (MatchEnded.PlayerResult result : ranked) {
            boolean you = result.playerId() == localPlayerId;
            String name = result.color().name() + (you ? " (YOU)" : "");
            Label.LabelStyle nameText =
                new Label.LabelStyle(smallFont, PlayerTextColors.of(result.color()));

            addCell(stats, name, nameText, NAME_COLUMN_WIDTH, Align.left);
            addCell(stats, String.valueOf(result.kills()), whiteText, STAT_COLUMN_WIDTH, Align.center);
            addCell(stats, String.valueOf(result.deaths()), whiteText, STAT_COLUMN_WIDTH, Align.center);
            addCell(
                stats,
                String.valueOf(Math.round(result.damageDealt())),
                whiteText,
                STAT_COLUMN_WIDTH,
                Align.center
            );
            stats.row().padTop(2);
        }

        root.add(stats).padBottom(12).row();

        statusLabel = new Label("", whiteText);
        statusLabel.setAlignment(Align.center);
        root.add(statusLabel).padBottom(6).row();

        lobbyButton = new TextButton(LOBBY_TEXT, buttonStyle);
        TextButton menuButton = new TextButton(MENU_TEXT, buttonStyle);

        lobbyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                lobbyRequested = true;
            }
        });

        menuButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                menuRequested = true;
            }
        });

        Table buttons = new Table();
        buttons.add(lobbyButton).padRight(24);
        buttons.add(menuButton);
        root.add(buttons);

        stage.addActor(root);
        updateLobbyAvailability();
    }

    // Most kills first; fewer deaths, then more damage, break ties.
    private static List<MatchEnded.PlayerResult> rank(List<MatchEnded.PlayerResult> results) {
        List<MatchEnded.PlayerResult> ranked = new ArrayList<>(results);

        ranked.sort(
            Comparator.comparingInt(MatchEnded.PlayerResult::kills).reversed()
                .thenComparingInt(MatchEnded.PlayerResult::deaths)
                .thenComparing(
                    Comparator.comparingDouble(MatchEnded.PlayerResult::damageDealt).reversed()
                )
        );

        return ranked;
    }

    private static String banner(List<MatchEnded.PlayerResult> ranked, int localPlayerId) {
        if (ranked.isEmpty()) {
            return "";
        }

        MatchEnded.PlayerResult best = ranked.get(0);

        if (ranked.size() > 1) {
            MatchEnded.PlayerResult second = ranked.get(1);

            if (
                best.kills() == second.kills()
                && best.deaths() == second.deaths()
                && Math.round(best.damageDealt()) == Math.round(second.damageDealt())
            ) {
                return "DRAW";
            }
        }

        return best.playerId() == localPlayerId
            ? "VICTORY!"
            : best.color().name() + " WINS";
    }

    private static void addCell(
        Table table,
        String text,
        Label.LabelStyle style,
        float width,
        int align
    ) {
        Label label = new Label(text, style);
        label.setAlignment(align);
        table.add(label).width(width);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            menuRequested = true;
        }

        updateLobbyAvailability();

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        stage.act(delta);
        stage.draw();

        NetClient client = session.client();

        if (client.isGameStarted()) {
            // Cannot normally happen (the host needs everyone locked in), but
            // if a rematch did start, join it rather than miss it.
            handOffTo(new ArenaScreen(game, session));
        } else if (lobbyRequested && lobbyOpen()) {
            handOffTo(
                session.isHost()
                    ? new HostLobbyScreen(game, session, lastLoadout)
                    : new JoinLobbyScreen(game, session, lastLoadout)
            );
        } else if (menuRequested) {
            game.setScreen(new MainMenuScreen(game));
            dispose();   // leaves the room
        }

        lobbyRequested = false;
    }

    private void handOffTo(Screen next) {
        sessionHandedOff = true;
        game.setScreen(next);
        dispose();
    }

    private boolean lobbyOpen() {
        NetClient client = session.client();
        return !client.isRoomClosed() && !client.isDisconnected();
    }

    private void updateLobbyAvailability() {
        NetClient client = session.client();
        boolean open = lobbyOpen();

        if (lobbyButton.isDisabled() == open) {
            lobbyButton.setDisabled(!open);
        }

        if (client.isRoomClosed()) {
            statusLabel.setText("THE HOST LEFT - THE LOBBY IS CLOSED");
        } else if (client.isDisconnected()) {
            statusLabel.setText("LOST CONNECTION TO THE SERVER");
        } else {
            statusLabel.setText("");
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        if (!sessionHandedOff) {
            session.close();
        }

        stage.dispose();
        font.dispose();
        smallFont.dispose();
        image.dispose();
    }
}
