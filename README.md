# Trodden

A standalone Paper plugin that makes the ground remember footsteps. Grass a player keeps walking on
turns to coarse dirt, then a path, with a small speed bonus on the finished path. Left alone, a path
slowly grows back through coarse dirt to grass. Alongside that: stone ages (moss near water or in
rain, cracks near lava, cleanable with a brush), leaves litter the ground under trees in autumn,
dirt paths turn to mud in the rain and dry back out, snow keeps footprints, and ridden horses/
donkeys/mules/camels wear the ground too.

Trodden was built for and runs on a real, Ukrainian-language Minecraft server (where it shipped
under the name MatsuriWear) and is now maintained here as its own project. The `/paths` command,
its Ukrainian aliases (`стежки`, `stezhky`) and subcommand words, and the Ukrainian/English
player-facing text are unchanged — they're what live players already type and read.

## Design constraint: no world scans

This plugin never walks the world looking for blocks to change. Everything it does is triggered by
one of three things:

- **Player steps.** `WearListener` reacts to `PlayerMoveEvent` (and ridden-mount movement) block by
  block, as they happen.
- **Probes near online players.** Once every few seconds, `Sampler` picks a handful of random
  positions around each online player — the same idea as vanilla's random tick, scoped down —  and
  offers them to the aging/litter/mud probes. No chunk is ever scanned.
- **The plugin's own recorded positions.** Regrowth only ever revisits blocks this plugin already
  changed, tracked per chunk, and only in chunks that happen to be loaded already.

This was a deliberate choice for a server on a host with tight memory, where an unbounded scan can
stall the whole game. See `Sampler` and `RegrowthTask` for the two places this is enforced in code.

## Claims support: what's actually been run

Trodden checks a claims plugin (if one is installed) before trampling, aging, littering, or
mudding a block, so it never changes ground outside claims the owner opted in for. Five claim
plugins are supported through a shared `Claims` interface, but they have not all seen the same
amount of real use:

| Plugin | Status |
|---|---|
| **HuskClaims** | The only one tested on a live server. This is what the plugin was built and shipped against. |
| WorldGuard | Compiles against the WorldGuard/WorldEdit 7.x API. Not run on a live server by us. |
| GriefPrevention | Compiles against the GriefPrevention API. Not run on a live server by us. |
| Lands | Compiles against LandsAPI. Not run on a live server by us. |
| Towny | Compiles against the Towny API. Not run on a live server by us. |

If you use one of the four untested adapters, please treat it as beta and report back — the adapter
code follows each plugin's documented API, but "compiles and looks right" is not the same claim as
"has trampled grass in front of real players."

`claims.prefer` in `config.yml` picks which installed plugin is used, in order; leave it empty for
the default order (HuskClaims, Lands, Towny, GriefPrevention, WorldGuard). If none of the five are
installed, or none of the ones you listed are, wear applies everywhere with no claim checks at all.

## Folia

The code follows Folia's region-scheduler rules: block edits and PDC writes go through region-aware
scheduling (`Sched`) rather than the global scheduler. That is a statement about how the code is
written, not a claim about a runtime it has ever been started on — it has only ever run on Paper, so
`plugin.yml` deliberately does **not** declare `folia-supported`, and Folia will refuse to load it.
There are also known paths that still touch world state from the global scheduler
(`WearStore.flushAllNow` at shutdown, and the `player.getLocation()`/`isChunkLoaded` reads in
`Sampler.run` and `RegrowthTask.sweepLoaded`), which would have to be moved before support could be
declared honestly. If you want it on Folia, that is the work — please report back what you find.

## Building

Two ways to build, producing the same classes and running the same `TestMain` checks either way.
Gradle is the portable one — use it unless you're the maintainer building against the live Matsuri
server's own jars.

### Gradle (recommended for contributors and CI)

```bash
./gradlew build
```

Produces `build/libs/Trodden-1.0.0.jar`, the shaded jar with bStats bundled and relocated.
`./gradlew build` runs `TestMain`'s server-free checks as part of `check`, and fails the build
if any of them fail.

Needs a JDK that can supply a Java 25 toolchain (Gradle will provision one via its toolchain
resolver if none is installed locally) and network access to:

- `repo.papermc.io` — Paper API
- `maven.enginehub.org` — WorldGuard, WorldEdit
- `jitpack.io` — GriefPrevention, LandsAPI
- `repo.glaremasters.me` — Towny
- `repo.william278.net` — HuskClaims
- Maven Central — bStats, the shadow plugin

### `build.sh` (maintainer's local fast path)

```bash
./build.sh
```

This is what actually ships to the live server it was built for, and is not meant to be portable:
it compiles straight with `javac` against that server's `libraries/` folder (`SERVER`, defaulting to
the maintainer's own server path, can be overridden to point at any similarly-laid-out Paper install)
plus the claim-plugin jars in `libs/` (not committed — vendor your own if you want to build the four
untested adapters), runs `TestMain`, and only writes the jar if every check passes. It does not
include bStats: that class lives outside `src/main/java` specifically so this path never needs it
(see `src/bstats/java/.../PluginMetrics.java` for why).

## Configuration

See `config.yml` — every option is commented in place. The short version: thresholds for grass to
coarse dirt to path, which blocks count as grass, per-world enable, the claims preference order, and
independent on/off switches for regrowth, aging, litter, mud, snow tracks, and mount wear.

Player-facing text lives in `lang/en.yml` and `lang/uk.yml`, selected by `language` in `config.yml`.
Players toggle trampling in their own claim with `/paths on|off|state`.

## bStats

The Gradle build shades and relocates [bStats](https://bstats.org) (`org.bstats` ->
`dev.thathunky.trodden.libs.bstats`), starting standard metrics only — no custom charts. Before
shipping a build you intend to actually run, register the plugin at
[bstats.org/what-is-my-plugin-id](https://bstats.org/what-is-my-plugin-id) and put the id it gives
you into `PLUGIN_ID` in `src/bstats/java/dev/thathunky/trodden/stats/PluginMetrics.java` — it ships as a
placeholder (`0`) that makes the plugin log a warning and skip starting metrics instead of reporting
under someone else's id.

To turn metrics off on a running server regardless of that id, set `enabled: false` in the server's
`plugins/bStats/config.yml` — that switch is global to bStats, not specific to this plugin.

## License

MIT, see `LICENSE`. Copyright ThatHunky.
