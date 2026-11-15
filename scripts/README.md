# Scripts

| Folder or file | What it does |
|---|---|
| `smoke_test/` | The smoke test with the vanilla client and the vanilla server, and the singleplayer test; see below. |
| `versions/` | `check_versions.py` compares every version the project uses with the newest one (`./tasks.sh versions`; only Python's standard library, so it also runs outside the dev image). |
| `new_mod.py` | Only in the template: turns a copy of it into a new mod (see the main README). |

All of them use only Python's standard library and run in the dev image (`docker/Dockerfile`, see the main README).
In the VS Code dev container run them directly; otherwise through compose:
`docker compose run --rm dev python3 scripts/...`.

## Smoke test

Checks the release JARs the way a server owner runs them, and that the two rules of every mod hold: players without
the mod can play, and the world stays valid without the mod.

```
docker compose run --rm dev ./tasks.sh build
docker compose run --rm dev ./tasks.sh smoke            # both loaders
docker compose run --rm dev ./tasks.sh smoke neoforge   # one
```

For each loader `smoke_test.py`:

1. checks that the JAR holds no files of the game (Minecraft's classes, textures, sounds, shaders), then starts the
   official Fabric server (with Fabric API) or NeoForge server for the versions in `gradle.properties` with the JAR
   on a fresh flat world, and waits for the mod's "is running" line;
2. runs the mod's scenario (`scenario.py`, `with_mod`): console commands that leave in the world what the mod saves;
3. starts the **official vanilla client** (`vanilla_client.py`: the client JAR, its libraries and assets from Mojang,
   without sounds, started offline on a virtual screen with `--quickPlayMultiplayer`), which joins the server. It
   stays a while, the scenario's `with_client` part runs, and it must still be connected; its log must have no
   network or registry errors;
4. saves, stops the server and checks its log for errors and warnings from the mod or its mixins;
5. opens a copy of the world on the **official vanilla server** (the mod removed): it must start, pass the scenario's
   `on_vanilla` checks (e.g. "the item still has its `custom_data`"), save and stop without a single warning or error
   in its log.

`--no-client` skips step 3 (for a machine without `xvfb-run`). The servers, the client and their downloads are kept in
`scripts/.cache/smoke/`; worlds, config and mods are written fresh every run. Logs: `scripts/.cache/smoke/*/smoke-test.log`
and `scripts/.cache/smoke/vanilla-client-*/game/client.log`.

## Singleplayer test

`client_test.py` checks the mod on a client's integrated server. It starts each loader's dev client
(`./gradlew :<loader>:runSmokeClient`, run folder `<loader>/build/run/smokeClient/`) on a virtual screen (Xvfb, OpenGL
in software through EGL), which opens a copy of the smoke test's world directly (`--quickPlaySingleplayer`). The mod
must run, the player must be in the world, and the client's log must have no errors from the mod or its mixins.

```
docker compose run --rm dev ./tasks.sh client            # both loaders; needs the smoke test first
docker compose run --rm dev ./tasks.sh client neoforge   # one
```
