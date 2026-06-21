# PaintAndSeek — example datapack

This is a ready-to-edit copy of PaintAndSeek's default round scripts. The mod ships
these same functions built in; dropping this datapack into a world lets you
**override and customise** them without touching the mod.

## Install
1. Copy the `paintandseek-defaults` folder into your world's `datapacks/` folder
   (`.minecraft/saves/<world>/datapacks/`), or zip its *contents* and drop the zip there.
2. In game, run `/reload` (or `/datapack enable`).

## Customise
Edit any `.mcfunction` under `data/paintandseek/function/`. The mod runs these at
round lifecycle points; your versions take priority over the built-in defaults.

| Function | When it runs | Context |
|----------|--------------|---------|
| `on_start`        | a round begins                         | server |
| `setup_seeker`    | round start, once per seeker           | as the seeker (`{arrows}`, `{hide}` provided) |
| `setup_hider`     | round start, once per hider            | as each hider |
| `start_seek`      | the seek phase begins                  | server |
| `on_found`        | a hider is hit by a spectral arrow     | as that hider |
| `on_seeker_win`   | all hiders found                       | server |
| `on_hider_win`    | seek timer ends with survivors         | server |
| `cleanup`         | a round ends, once per participant     | as each participant |
| `reset`           | a new round starts                     | server |

## Participant tags (target these in your own commands/functions)
- `paintandseek.player` — everyone in the round
- `paintandseek.seeker` — the seeker
- `paintandseek.hider`  — the hiders
- `paintandseek.found`  — hiders who've been found

The mod handles the engine (roles, timers, FOV scoring, found detection, win
conditions, hidden nametags, locator-bar hiding, scoreboard).
