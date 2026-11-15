# Synced Shulker Boxes

**Link shulker boxes so they share one inventory: take something out of one, and it's gone from all of them.**

A **server-side mod for Fabric and NeoForge** (Minecraft 26.3). Players join with an unmodded client, and the world
stays a valid vanilla world: remove the mod, and at worst the vanilla behaviour comes back.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

> [!NOTE]
> **Written with AI.** Most of this project's code, texts and docs were written by an AI coding assistant (Claude by
> Anthropic), directed and partly tested by NR137. Not every feature has been tested in depth, so some things may not
> work as expected.

## ✨ Features

- **Syncable boxes** come from the smithing table: an echo shard (template), an empty shulker box of any colour and an
  eye of ender give a *Syncable Shulker Box*.
- **Linking at the anvil:** two syncable boxes make a new group (a name typed there goes onto both). A linked box in
  the first slot and a new syncable box in the second add the new one to the group; it gets the first box's name, or a
  name typed for it. Up to 8 boxes per group (configurable), a few experience levels per box.
- **One inventory:** every box of a group opens the same 27 slots, from anywhere, also at the same time (no dupes).
  The screen's title shows the group's name, the box's place and the group's code: *Base 2/3 · K7QF*.
- **Easy to tell apart:** the name in the box's colour (*Synced Red Shulker Box*), a tooltip line with the group's code
  and the box's place (*Group K7QF · Box 2/3*), tooltips show the shared contents (also in tooltip mods), coloured
  trails shoot out when a box is opened, and optionally particles around placed boxes all the time.
- **No automation:** hoppers, hopper minecarts, droppers and crafters can't move items in or out, like ender chests.
  Comparators read the shared contents.
- **Destroyed boxes** (lava, fire, cactus, explosions, the void, despawning) leave their group; the others keep the
  items. Only when the last box of a group is destroyed are its items gone, as with a vanilla box.
- **Optional on the client:** with the mod installed on a client too, synced boxes get the enchantment glint (which
  vanilla doesn't draw on shulker boxes, MC-69683), and the client decides about the particles itself. Without it,
  everything works the same.

## ⌨️ Commands and options

| Command | Who | What |
| --- | --- | --- |
| `/syncedshulkers particles [opening\|always on\|off]` | everyone | which particles the server sends you |
| `/syncedshulkers config [<option> <value>]` | admins | the server's options (below), saved right away |
| `/syncedshulkers held` | admins | the group of the box in your hand |
| `/syncedshulkers group <code>` | admins | a group's boxes and contents; `give` copies the contents to you, `remove <member>` forgets a lost box |
| `/syncedshulkers give <count>` | admins | a new group of linked boxes |
| `/syncedshulkersclient [glint\|openingParticles\|ambientParticles on\|off]` | clients with the mod | this client's settings |

**Server options** (`config/synced_shulker_boxes.json`; in singleplayer also in the settings screen):

| Option | Default | What |
| --- | --- | --- |
| `maxGroupSize` | 8 (2–64) | boxes per group |
| `linkCostLevels` | 5 (0–19) | experience levels per box linked |
| `openingParticles` | on | send the trails when a box is opened |
| `ambientParticles` | on | send particles around placed synced boxes all the time (clients with the mod draw their own instead) |
| `permissions` | see below | who has each permission without a permission mod |

**Client settings** (only with the mod on the client; `config/synced_shulker_boxes-client.json`, Mod Menu on Fabric,
the mod list on NeoForge): glint (on), opening particles (on), particles always (off).

## 🔒 Permissions

Every command and gated action has a permission node, which a permission mod such as LuckPerms can manage (through
Fabric's or NeoForge's permission API). Without one, the defaults below apply; change them in the config or with
`/syncedshulkers config permission <node> everyone|operators`.

| Node | Default | What |
| --- | --- | --- |
| `synced_shulker_boxes.craft` | everyone | the smithing recipe |
| `synced_shulker_boxes.link` | everyone | linking at the anvil |
| `synced_shulker_boxes.open` | everyone | opening a linked box |
| `synced_shulker_boxes.settings` | everyone | `/syncedshulkers particles` |
| `synced_shulker_boxes.admin` | operators | the admin commands |

## 🧹 Removing the mod

The world stays a valid vanilla world. Every linked box is an ordinary shulker box again, with a harmless mark in its
`custom_data`. The oldest box of each group (the anchor) holds a copy of the group's items, the others are empty: the
mod keeps that copy up to date while it runs. The mod's own files (`data/synced_shulker_boxes/`) are ignored by
vanilla. The glint, the colours and the tooltips were only ever sent to clients and are gone.

## 🛠️ Development

**Needs:** [Docker](https://docs.docker.com/get-docker/) (with Compose) · Minecraft 26.3 to play on the dev server

Everything else runs in one dev image (`docker/Dockerfile`): Java and Python in the versions of
[`.tool-versions`](.tool-versions) (the only place they are set), Gradle from its wrapper, a virtual screen for the
client tests. [`compose.yaml`](compose.yaml) runs it with your checkout mounted and the Gradle cache in a volume.
Compose rebuilds the image by itself when `docker/Dockerfile` or `.tool-versions` change.

**VS Code:** open the folder and run **Dev Containers: Reopen in Container** (extension *Dev Containers*). Terminal,
Gradle and Java tooling then run in the container, the commands below work without `docker compose run --rm dev`, and
the pre-commit hook is turned on.

The build and test commands live in [`tasks.sh`](tasks.sh):

```bash
docker compose run --rm dev ./tasks.sh build      # JARs in fabric/build/libs/ and neoforge/build/libs/
docker compose run --rm dev ./tasks.sh test       # the quick tests
docker compose run --rm dev ./tasks.sh all        # everything CI runs
docker compose run --rm dev                       # a shell in the dev image
docker compose up server                          # Fabric dev server (fabric/runs/server/); LOADER=neoforge for NeoForge
```

Connect with Minecraft to `localhost:25565`. The first start of a dev server stops until you accept Mojang's EULA in
`<loader>/runs/server/eula.txt`.

### Tests

| Task | What |
| --- | --- |
| `test` | The quick tests (`test-java`) |
| `test-java` | JUnit tests of `common/` (`common/src/test`) |
| `gametest` | Game tests on each loader's game test server (`common/src/gametest`, registered in `fabric/src/gametest` and `neoforge/src/gametest`) |
| `smoke` | The built JARs on the official Fabric and NeoForge servers; a **vanilla client** (the official client, no mods) joins and plays; then each world opens on the **official vanilla server without the mod** and must load, keep what it should and save without errors (needs `build` first; see [`scripts/README.md`](scripts/README.md)) |
| `client` | Singleplayer: each loader's dev client opens a copy of the smoke test's world (needs `smoke` first) |
| `changelog` | `CHANGELOG.md` is ready for a release: its top section is `mod_version` with a date, and there is no Unreleased section. With a base ref (`./tasks.sh changelog origin/main`), a change to the mod JAR must also raise `mod_version` |
| `changes` | What the changes since a base ref touch: `jar`, `tools` or `docs` (see Releasing) |
| `versions` | Every version the project uses against the newest one (Minecraft, loaders, Mixin, Java, Gradle and its plugins, JUnit, the dev image's tools, the workflows' actions and runners); fails when something can be updated |

The `tests` workflow (`.github/workflows/tests.yml`) runs them in the same image: on draft pull requests only the quick
tests, on other pull requests, pushes to `main` and before every release all of them. A pull request that only changes
texts skips them. The `versions` workflow runs `versions` on every pull request into `main` and by hand; it is not
required to merge.

A pre-commit hook runs the Java tests when a commit touches the code, in the dev container or through Docker. The dev
container turns it on; in another clone, run `git config core.hooksPath .githooks` once. `git commit --no-verify`
skips it.

### Releasing

The `release` workflow (`.github/workflows/release.yml`) only runs when started by hand: **Actions → release → Run
workflow**, or `gh workflow run release.yml`. It runs the tests first; if one fails, nothing is built or released.

Every change that changes the mod JAR is a release; the rest goes into `main` without one. `./tasks.sh changes` sorts
a pull request by its files:

| Kind | Files | Tests | Release |
| --- | --- | --- | --- |
| `jar` | `common/`, `fabric/`, `neoforge/`, `build-logic/`, the Gradle files, `LICENSE`, `.tool-versions` (but not the tests in them) | all | yes: `mod_version` must be raised |
| `tools` | the tests in those folders, `scripts/`, `docker/`, `.github/`, `compose.yaml`, `tasks.sh`, … | all | no |
| `docs` | `*.md` outside those folders, `docs/` | skipped (the checks pass) | no |

1. For a release, set `mod_version` in `gradle.properties` and give its `CHANGELOG.md` section a date
   (`## [x.y.z] - YYYY-MM-DD`), on the pull request's branch before merging.
2. Run the workflow without "draft release" to only build: the two JARs and the release notes are attached to the run
   for a day.
3. Run it with "draft release" to create a draft GitHub release `v<mod_version>+<minecraft_version>` with both JARs and
   that version's `CHANGELOG.md` section as notes; check it, then publish it on GitHub.

### Project layout

One codebase builds both loaders (based on [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)):

| Folder | What |
| --- | --- |
| `common/` | Everything shared, including the mixins. Compiled against plain Minecraft, so it cannot use Fabric or NeoForge APIs. |
| `fabric/` | Fabric entry point, `FabricPlatform`, `fabric.mod.json` |
| `neoforge/` | NeoForge entry point, `NeoForgePlatform`, `neoforge.mods.toml` |
| `build-logic/` | Gradle scripts shared by the three projects |
| `docker/`, `compose.yaml`, `.devcontainer/` | The dev image, its tasks and the VS Code dev container |
| `scripts/` | Smoke, vanilla and singleplayer tests, version check ([README](scripts/README.md)) |
| `docs/design.md` | How the mod works inside (groups, the anchor, what clients get) |

Loader-specific needs go through the small `Platform` interface in `common/`; each loader passes its implementation to
`SyncedShulkerBoxes.init` at startup.

## 🧰 Built with

- **Mod:** Java with [Gradle](https://gradle.org), [Fabric Loom](https://github.com/FabricMC/fabric-loom) and
  [ModDevGradle](https://github.com/neoforged/ModDevGradle), in the project layout of
  [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (CC0)
- **Tests:** [JUnit](https://junit.org), Minecraft's game tests, [Python](https://www.python.org); the tests run with
  [Lithium](https://modrinth.com/mod/lithium) (LGPL-3.0, downloaded for testing, not part of the mod)
- **Optional:** [Mod Menu](https://modrinth.com/mod/modmenu)'s API (MIT) for the settings button on Fabric, only compiled
  against
- **Dev environment:** [Docker](https://www.docker.com)

## 📄 License

MIT License, see [LICENSE](LICENSE). © NR137. The JAR contains no files of the game. Minecraft and the game's names
belong to Mojang and Microsoft.
