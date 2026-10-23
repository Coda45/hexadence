# Adding or changing a power

Every power is defined in one file:
`core/src/main/java/io/github/coda45/hexadence/power/Powers.java`

You never need to touch the networking code (`net/`) to add or tune a power.

## Tuning a power

Change its numbers in `Powers.java`. Units:

| Field | Meaning | Unit |
|---|---|---|
| `damage` | Health removed on a direct hit (players have 100) | HP |
| `cooldown` | Time before it can be cast again | seconds |
| `speed` | How fast it travels (powers that travel) | tiles / second |
| `range` | How far it travels or reaches | tiles |
| `radius` | Projectile size, or half a beam's width | tiles |
| `zone(type, radius, duration)` | Zone left behind (zone powers) | tiles, seconds |

For scale: the map is 30 × 20 tiles and players move 5 tiles per second.

## Adding a power

1. **Pick its behavior** (`power/Behavior.java`):
   - `PROJECTILE`: travels in a line and hits the first thing it touches (Fireball)
   - `BEAM`: hits the first thing in its path instantly (Lightning Bolt)
   - `THROWN_ZONE`: travels like a projectile, then leaves a zone where it stops (Water Puddle)

   If none fit, the power needs a new behavior. That is a code change in
   `ArenaWorld.tryCast()`, so ask before starting.

2. **Copy an existing definition with the same behavior** in `Powers.java`, then:
   - give it a **new ID** (next unused number, 1–255). Never change or reuse an ID.
   - set its name, element, form (`BASIC` or `ZONE`), and numbers from the spell card
   - set its HUD icon images (`icon(...)`, paths inside `assets/`)

3. **Add it to `Powers.ALL`** at the bottom of the file.

4. **Run the tests:** `.\gradlew.bat build`. They check IDs, required fields, and
   the one-basic-and-one-zone-per-element rule. A broken definition also stops
   the game at startup with a message saying what is missing.

5. **Try it:** put it in a slot of `Loadout.DEFAULT` (`power/Loadout.java`) and
   run the game. Slot 1 is left mouse, slot 2 right mouse, slot 3 Q.

6. **Open a pull request.** After it merges, the server must be redeployed so it
   runs the same definitions as everyone's game.

## Art

- **HUD icon:** `icon(background, rim)` in the definition.
- **Projectile sprite:** chosen by element in `GameTextures.getProjectileFrame()`.
  Elements without their own art use the fireball for now.

## Not covered yet

- Zone reactions (which basic power changes which zone, and how) are still
  written by hand in `ArenaWorld`. They are moving into a reaction table built from
  the interaction matrix.
- Choosing a loadout in the lobby. Until then, everyone uses `Loadout.DEFAULT`.
