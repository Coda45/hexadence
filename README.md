# Hexadence

Hexadence is a 2D multiplayer arena game for up to four players.

Players battle using elemental magic such as fire, water, stone, lightning, wind, and frost. Spells can interact with other spells, players, and the arena itself, creating new effects and changing the battlefield during combat.

The game is currently in development as a software engineering student project.

## Current Goals

* Up to 4-player multiplayer
* Fast real-time arena combat
* Elemental spell interactions
* Reactive arena environments
* Pixel-art visuals
* PC support

## Requirements

* **Java Development Kit (JDK) 21**

  * Eclipse Temurin 21 is recommended, but any compatible JDK 21 distribution should work.
  * https://adoptium.net/temurin/releases/?os=any&version=21&mode=filter&arch=x64

A separate Gradle installation is **not required**. This repository includes the Gradle Wrapper, which downloads and uses the correct Gradle version automatically.

To verify that Java is installed:

```bash
java -version
```

The reported Java version should be 21.

## Getting Started

Clone the repository and enter the project directory:

```bash
git clone https://github.com/Coda45/hexadence.git
cd hexadence
```

### Build and Run

**Windows**

```powershell
.\gradlew.bat build
.\gradlew.bat lwjgl3:run
```

**Linux / macOS**

```bash
./gradlew build
./gradlew lwjgl3:run
```
### Multiplayer (prototype)

One player hosts and plays, and up to three others join. Build the jar once, then start each copy from the `assets` folder. Start the host first.

**Windows (PowerShell)**

```powershell
.\gradlew.bat lwjgl3:jar
cd assets

# Host
java "-Dhexadence.host=true" -jar ..\lwjgl3\build\libs\Hexadence-1.0.0.jar

# Joiner (in another terminal, also from assets; use the host's IP instead of localhost on a different machine)
java "-Dhexadence.join=localhost:7777" -jar ..\lwjgl3\build\libs\Hexadence-1.0.0.jar
```

**Linux / macOS**

```bash
./gradlew lwjgl3:jar
cd assets

# Host
java -Dhexadence.host=true -jar ../lwjgl3/build/libs/Hexadence-1.0.0.jar

# Joiner (in another terminal, also from assets)
java -Dhexadence.join=localhost:7777 -jar ../lwjgl3/build/libs/Hexadence-1.0.0.jar
```

On macOS, add `-XstartOnFirstThread` before `-jar`. The port defaults to 7777 (`-Dhexadence.port=...` on the host changes it). Without either option the game runs as normal single-player.

To play across machines on the same network, joiners use the host's local IP address instead of localhost. To play over the internet, use the dedicated server below.

### Dedicated server

The `server` module runs the game server on its own, with no window and no local player. Every player joins it as a client, so start each game copy with `-Dhexadence.join=<server-address>:7777`.

Run one locally:

```bash
./gradlew server:run
# then, from assets, in two other terminals:
java -Dhexadence.join=localhost:7777 -jar ../lwjgl3/build/libs/Hexadence-1.0.0.jar
```

`./gradlew server:jar` builds `server/build/libs/hexadence-server.jar`, a single file with the maps bundled. Options: `-Dhexadence.port=` (or the `HEXADENCE_PORT` environment variable) and `-Dhexadence.map=`. The server and every client must run the same build, because clients are only accepted when their protocol version and map match the server's.

**Deploying to the VPS** (Ubuntu, from Linux, macOS, or Git Bash on Windows):

```bash
./deploy/deploy.sh ubuntu@<vps-ip> --setup   # first time: installs Java, the service, and the firewall rules
./deploy/deploy.sh ubuntu@<vps-ip>           # later: rebuild, upload, restart
```

On the VPS, `sudo systemctl status hexadence-server` shows whether it's running and `journalctl -u hexadence-server -f` follows its output. The server restarts on its own after a crash or a reboot, and closes connections that send nothing for 30 seconds.

## Development Tools

The following tools are used during development but not required to build and run the game:

* **Tiled** - creation and editing of arena maps
* **Pixquare** - creation of 2D pixel art and animations 

## Project Template

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.

## Asset Credits

- **Free RPG Basic Tileset** - Pixel Poem
  - Source: https://pixel-poem.itch.io/free-rpg-tileset
  - License: Custom license; free and commercial use and modification permitted. Redistribution/resale prohibited. Attribution optional.
  - Used for: Arena tiles