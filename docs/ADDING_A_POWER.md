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
| `zone(kind)` | Zone left behind (zone powers); its size and lifetime live in `ZoneKinds` | |

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

5. **Try it:** host a room on a local server (see the README), pick it in the
   lobby's power draft, lock in, and start. Every power in `Powers.ALL` shows
   up in the draft. Slot 1 is left mouse, slot 2 right mouse, slot 3 Q.

6. **Open a pull request.** After it merges, the server must be redeployed so it
   runs the same definitions as everyone's game.

## Art

- **HUD icon:** `icon(background, rim)` in the definition.
- **Projectile sprite:** chosen by element in `GameTextures.getProjectileFrame()`.
  Elements without their own art use the fireball for now.

## Zones and reactions

Zones live in `zone/ZoneKinds.java`, and the interaction matrix lives in
`zone/Reactions.java`.

- **Base zones** are placed by zone powers, for example the Puddle from
  Water Puddle. Basic powers react with them.
- **Derived zones** are made by reactions, for example Steam or Lava. They
  never react further.

**Tuning a zone:** change its numbers in `ZoneKinds.java`.

| Field | Meaning | Unit |
|---|---|---|
| `radius` | Size | tiles |
| `duration` | How long it lasts | seconds |
| `damagePerSecond` | Damage to anyone standing in it, creator included | HP / second |
| `speedMultiplier` | Movement inside it (1 = normal, 0.5 = half) | |
| `coversPlayers()` | Drawn over players, like a cloud | |
| `tint` | Draw color, `0xRRGGBBAA` | |
| `planned(...)` | A designed effect the engine doesn't do yet (notes only) | |

Where zones overlap, only the **newest** one counts at that spot.

**Changing a reaction:** each filled cell of the matrix is one line in
`Reactions.ALL`:

```java
transform(FIRE, WATER, ZoneKinds.STEAM),   // Fire hits a Water zone -> Steam
charge(STORM, WATER),                      // Storm hits a Water zone -> charged
```

To add a new result, define its derived zone in `ZoneKinds` (next unused ID,
add it to `ZoneKinds.ALL`), then add the line here. The tests check that:
- an element never reacts with its own field
- each basic power reacts with at most two fields
- every derived zone is produced by some reaction

## Not covered yet

- Zone effects beyond damage and slow: steam blocking vision, Crystal Wall
  blocking movement, and Singularity pulling players. These zones exist, but
  for now they only show and expire.
- The draft allows any three different powers for now. Once at least two
  zone powers exist, set `REQUIRE_ONE_BASIC_TWO_ZONES` in
  `power/LoadoutRules.java` to `true` to enforce one basic and two zone powers.
