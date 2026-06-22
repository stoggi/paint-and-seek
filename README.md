# PaintAndSeek

**Hide-and-seek in Minecraft where hiders paint their own skin, in-game, to blend into the world around them.**

This mod was inspired by Meccha Chameleon ([on Steam](https://store.steampowered.com/app/4704690/MECCHA_CHAMELEON/)). I wanted to play it with my kids after seeing some of our favourite Minecraft content creators playing the game together ([on YouTube](https://www.youtube.com/playlist?list=PLDP3UVRoiapQu6ty4xWZUtM6LcFJaqYuE)), but Meccha is Windows only. So over a weekend, with the help of Claude Code, I put together this mod to play a similar style of game in Minecraft. :)

![A player painting their skin to blend into a village](https://cdn.modrinth.com/data/cached_images/087a136a9bb92f6e8338334a8696f001e0c9661f_0.webp)

## Requirements

You need:

- This mod
- [Fabric Loader](https://fabricmc.net/use/) (≥ 0.19.3)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- Minecraft **26.2** and **Java 25**

Drop the jar into your `mods/` folder on both the client and the server (it runs on a dedicated server or a LAN/single-player host).

## How to play

Start a multiplayer game and pick a starting location — a village is a great first round. Then an operator runs:

```
/paintandseek newround
```

A seeker is chosen automatically and given **blindness** (so they can't peek), a **paintbrush**, and a **bow with spectral arrows**. Everyone else becomes a hider and gets a **paintbrush**. The round has two phases:

1. **Hide** — the seeker is blind while hiders scatter, paint themselves, and strike a pose to blend in.
2. **Seek** — the seeker's blindness lifts and the hunt begins.

The longer a hider stays in the seeker's view — and the closer they are while in view — the more points they rack up. The seeker must hit a hider with a **spectral arrow** to stop that hider's point counter. If hiders survive until the seek timer runs out, the **hiders win**; if every hider is tagged first, the **seeker wins**.

### Painting

Right-click with the paintbrush in hand to open the paint screen:

- **Right-click** — eye-dropper a colour from anywhere on screen (paint with the exact colour of the block you want to hide against).
- **Left-click** — paint that colour onto your skin.
- **W / A / S / D** — orbit the camera around your model.
- Pick a **pose** from the side panel — `Default`, `T-Pose`, `Arms Up`, `Arms Fwd`, `Legs Out`, `Star`, `Starfish`, `Lie Flat`, `Ball`, `Sit` — to flatten yourself against a wall, lie on the ground, or curl into a block shape.

Your painted skin and pose sync live to everyone who can see you, so you can blend in real time.

## Commands

All commands require permission level 2 (game-master / op).

| Command | Description |
| --- | --- |
| `/paintandseek newround` | Start a round with a random seeker and default timings. |
| `/paintandseek newround <seeker>` | Choose the seeker. |
| `/paintandseek newround <seeker> <hideTime>` | Also set the hide phase length (seconds). |
| `/paintandseek newround <seeker> <hideTime> <seekTime>` | Also set the seek phase length (seconds). |
| `/paintandseek newround <seeker> <hideTime> <seekTime> <arrows>` | Also set how many spectral arrows the seeker gets. |
| `/paintandseek endround` | End the current round immediately and reveal final scores. |

Defaults: **120 s** hide, **300 s** seek, **20** arrows.

## How it works

The mod splits responsibilities between a server-side "engine" and editable datapack scripts:

- **Authoritative skin store (server).** The server holds each player's painted skin as a 64×64 ARGB image and is the source of truth. Paint strokes travel the wire as small "dirty rectangle" patches (`SubmitSkinPatch`), and full images (`SubmitSkinSnapshot`) are sent on open/commit. The server validates and applies them, then rebroadcasts only to players who can currently see the painter. Late joiners and players walking into range are synced the full skin automatically via entity tracking.
- **Live client rendering.** Each painted skin backs a GPU dynamic texture. Client mixins swap the player's body texture to it and re-read it every frame, so strokes appear instantly. Painted skins are rendered as a depth-writing cutout layer so they sort correctly against clouds and water.
- **Eye-dropper colour recovery.** The eye-dropper divides the sampled pixel by the GPU lightmap colour to recover the block's true albedo, so the colour you paint matches the surface regardless of the current light level.
- **Scoring.** Each tick the server checks every hider against the seeker's view: within a ~70° cone, within 64 blocks, and not occluded by terrain. Points accrue faster the closer the hider is. Scores are flushed to the sidebar only every 30 seconds so the seeker can't read exact sightings off the scoreboard.
- **Detection & concealment.** A hider is "found" only when struck by the seeker's spectral arrow. During a round, participant nametags are hidden (scoreboard teams) and players are removed from the locator bar (waypoint transmit range is zeroed), so the seeker has to find hiders by eye.

## Customising rounds with a datapack

The mod runs the game engine, but everything cosmetic — items handed out, titles, sounds, effects, win announcements — is delegated to **editable `.mcfunction` hooks** in the `paintandseek` namespace. A drop-in example datapack ships alongside the jar (`paintandseek-datapack-<version>.zip`); copy it into your world's `datapacks/` folder and edit the functions, or override them from your own datapack.

The engine tags participants so your functions can target them with selectors:

| Tag | Applied to |
| --- | --- |
| `paintandseek.player` | every participant |
| `paintandseek.seeker` | the seeker |
| `paintandseek.hider` | every hider |
| `paintandseek.found` | a hider who has been hit |

The lifecycle hooks (under `data/paintandseek/function/`):

| Function | When it runs | Context |
| --- | --- | --- |
| `reset` | at the start of a new round, before setup | server |
| `on_start` | once when a round starts | server |
| `setup_seeker` | once per seeker at round start; receives `$(arrows)` and `$(hide)` (seconds) | as the seeker |
| `setup_hider` | once per hider at round start | as each hider |
| `start_seek` | once when the seek phase begins | server |
| `on_found` | the moment a hider is hit by a spectral arrow | as that hider |
| `on_seeker_win` | all hiders found before time ran out | server |
| `on_hider_win` | seek time expired with survivors | server |
| `cleanup` | when a round ends | as each participant |

For example, **`start_seek.mcfunction`** lifts the seeker's blindness and flashes the phase titles:

```mcfunction
# Runs once when the seek phase begins (server context). Edit freely.
effect clear @a[tag=paintandseek.seeker] minecraft:blindness
title @a times 10 70 20
title @a[tag=paintandseek.seeker] title {"text":"SEEK!","bold":true,"color":"gold"}
title @a[tag=paintandseek.hider] title {"text":"HIDE!","bold":true,"color":"green"}
```

## Building from source

The build provisions JDK 25 automatically (via the Foojay toolchain resolver) — no system-wide JDK install is required.

```
./gradlew build
```

This produces the mod jar and the example datapack zip in `build/libs/`.

## License

PaintAndSeek is released under the [MIT License](LICENSE). Feel free to learn from it, modify it, and build on it.
