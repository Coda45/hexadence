package io.github.coda45.hexadence.screen;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.LoadoutRules;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;

// The lobby's power draft: one row per loadout slot, with arrows to cycle
// through every power, and a LOCK IN button. Both lobby screens use it.
//
// It only holds the player's choice. The screen sends the choice to the server
// whenever takeLockChange() reports the player locked in or unlocked.
final class LoadoutPicker extends Table {

    private static final String[] BINDINGS = { "LMB", "RMB", "Q" };
    private static final String LOCK_TEXT = "[ LOCK IN ]";
    private static final String UNLOCK_TEXT = "[ LOCKED - CHANGE ]";
    // Measured with the default font at normal size: the longest current name
    // is about 120 wide. Longer names are cut with "..." instead of pushing
    // the arrows off the screen.
    private static final float NAME_WIDTH = 150f;
    static final float WIDTH = 260f;

    private final List<Power> choice = new ArrayList<>(Loadout.DEFAULT.slots());
    private final Label[] powerLabels = new Label[Loadout.SIZE];
    private final TextButton lockButton;
    private final Label problemLabel;

    private boolean locked;
    private boolean lockChanged;

    LoadoutPicker(
        TextButton.TextButtonStyle buttonStyle,
        Label.LabelStyle whiteText,
        Label.LabelStyle goldText
    ) {
        add(new Label("YOUR POWERS", whiteText)).colspan(4).padBottom(4).row();

        for (int slot = 0; slot < Loadout.SIZE; slot++) {
            final int index = slot;

            TextButton previous = new TextButton("<", buttonStyle);
            TextButton next = new TextButton(">", buttonStyle);
            powerLabels[slot] = new Label("", goldText);
            powerLabels[slot].setEllipsis(true);

            previous.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    cycle(index, -1);
                }
            });

            next.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    cycle(index, 1);
                }
            });

            add(new Label(BINDINGS[slot], whiteText)).width(32f);
            add(previous).padRight(6);
            add(powerLabels[slot]).width(NAME_WIDTH);
            add(next).padLeft(6).padBottom(2).row();
        }

        lockButton = new TextButton(LOCK_TEXT, buttonStyle);
        lockButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleLock();
            }
        });

        problemLabel = new Label("", whiteText);
        problemLabel.setWrap(true);
        problemLabel.setAlignment(Align.center);

        add(lockButton).colspan(4).padTop(8).row();
        add(problemLabel).colspan(4).width(WIDTH).padTop(2);

        refresh();
    }

    // The player's three powers as currently chosen.
    Loadout loadout() {
        return new Loadout(choice);
    }

    boolean isLocked() {
        return locked;
    }

    // True once after the player locks in or unlocks, so the screen can tell
    // the server.
    boolean takeLockChange() {
        boolean changed = lockChanged;
        lockChanged = false;
        return changed;
    }

    // Back to unlocked without telling the server, for a new connection.
    void resetLock() {
        locked = false;
        lockChanged = false;
        refresh();
    }

    private void cycle(int slot, int direction) {
        if (locked) {
            return;
        }

        List<Power> all = Powers.ALL;
        int current = all.indexOf(choice.get(slot));
        int next = Math.floorMod(current + direction, all.size());

        choice.set(slot, all.get(next));
        refresh();
    }

    private void toggleLock() {
        // Only a legal loadout can be locked in; unlocking is always allowed.
        if (!locked && LoadoutRules.problem(loadout()) != null) {
            return;
        }

        locked = !locked;
        lockChanged = true;
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < Loadout.SIZE; slot++) {
            powerLabels[slot].setText(choice.get(slot).name().toUpperCase());
        }

        lockButton.setText(locked ? UNLOCK_TEXT : LOCK_TEXT);

        String problem = LoadoutRules.problem(loadout());
        problemLabel.setText(locked || problem == null ? "" : problem);
    }
}
