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
player-facing text are unchanged — they're what live players already type and read. Other languages
were added on top; see [Languages](#languages).

## Supported versions

One jar for Paper **1.21.4 through 1.21.11, 26.1.x, 26.2 and 26.3**. It is Java 21 bytecode, so it
loads on the Java 21 servers of the 1.21 line as well as on the Java 25 servers of 26.x, and
`plugin.yml` declares `api-version: '1.21.4'`, so older servers refuse it instead of half-working.

1.21.4 is the floor because it is the oldest version this jar was checked against, and the code
already depends on the 1.21.3+ attribute API (`Attribute` as registry-backed interface,
`Attribute.MOVEMENT_SPEED`, modifiers keyed by `NamespacedKey`). One feature needs a newer server:
autumn leaf litter uses the vanilla `leaf_litter` block from 1.21.5, so on 1.21.4 the litter probe
logs one line at startup and stays off; everything else works the same. Blocks that don't exist on
every version are looked up by name at runtime (`Compat`), never linked as `Material` constants.

How that is checked:

- `./gradlew compatCheck` (part of `check`) compiles the sources against paper-api 1.21.4, 1.21.8,
  1.21.11, 26.1.2, 26.2 and 26.3, so a method or block a supported version lacks fails the build.
- `.github/workflows/compat.yml` boots each of those Paper versions headless in CI with the built jar
  (flat world, offline, the matching Java), waits for `Done`, and fails if Trodden did not enable or
  logged an exception.

Only 26.2 has run on a live server with players; the other versions have been started, not played.

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

Produces `build/libs/Trodden-1.1.0.jar`. `./gradlew build` runs `TestMain`'s server-free checks
and the per-version compile checks (`compatCheck`) as part of `check`, and fails the build if any of
them fail.

Needs a JDK that can supply a Java 25 toolchain (Gradle will provision one via its toolchain
resolver if none is installed locally) and network access to:

- `repo.papermc.io` — Paper API
- `maven.enginehub.org` — WorldGuard, WorldEdit
- `jitpack.io` — GriefPrevention, LandsAPI
- `repo.glaremasters.me` — Towny
- `repo.william278.net` — HuskClaims
- Maven Central

### `build.sh` (maintainer's local fast path)

```bash
./build.sh
```

This is what actually ships to the live server it was built for, and is not meant to be portable:
it compiles straight with `javac` against that server's `libraries/` folder (`SERVER`, defaulting to
the maintainer's own server path, can be overridden to point at any similarly-laid-out Paper install)
plus the claim-plugin jars in `libs/` (not committed — vendor your own if you want to build the four
untested adapters), runs `TestMain`, and only writes the jar if every check passes. It emits Java 21
bytecode like the Gradle build, but compiles against one API only; the per-version checks are Gradle's.

## Configuration

See `config.yml` — every option is commented in place. The short version: thresholds for grass to
coarse dirt to path, which blocks count as grass, per-world enable, the claims preference order, and
independent on/off switches for regrowth, aging, litter, mud, snow tracks, and mount wear.

Players toggle trampling in their own claim with `/paths on|off|state`.

## Languages

Bundled: English (`en`), Ukrainian (`uk`), German (`de`), Spanish (`es`), French (`fr`), Polish
(`pl`), Brazilian Portuguese (`pt_BR`), Japanese (`ja`) and Simplified Chinese (`zh_CN`).

Each player reads messages in their game client's language (`Player#locale()`), looked up as the
exact tag first (`pt_BR`), then the bare language (`de_AT` gets `de`), then another file of the same
language (`pt_PT` gets `pt_BR`, `zh_TW` gets `zh_CN`). A player whose language has no file gets
`language` from `config.yml`, and English after that. The console always uses `language`. The
choice is made per message, so a player who switches language in the client sees it right away.

The file for `language` is copied to the plugin folder's `lang/` on first start so you can edit it.
Any `lang/<tag>.yml` in that folder overrides the bundled file of the same name, or adds a language
the jar doesn't have. A key missing from a file falls back to the bundled copy of that language, then
to `language`, then to English, with one warning per key in the console.

The command stays `/paths` in every language; `/стежки` and `/stezhky` are aliases, and the
subcommands accept `on`/`off`/`state` as well as `увімкнути`/`вимкнути`/`стан`. `TestMain` checks
that every bundled file has exactly the keys of `lang/en.yml`, with the same tags and placeholders.

## License

MIT, see `LICENSE`. Copyright ThatHunky.
