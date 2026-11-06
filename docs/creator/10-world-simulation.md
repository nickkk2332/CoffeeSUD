# World simulation: time, calendars, weather, seasons, tides, hazards and darkness

[Handbook](README.md) · Feature family [W](feature-index.md#w)

This chapter is about the simulated world that goes on around the player:
the game clock and how it can run, pause or be spent; calendars, moons,
seasons and holidays; weather and climate; tides, floods and room states;
darkness, deep darkness, breathing and typed hazards; whole-area resets; and
the scheduled tasks that run on the game clock. It is written for world
creators (who set it up with in-game commands and world files), scripters
(who read and change it from MOBPROG and JavaScript) and engine developers
(who need to know where the pieces live). Each section says which of those
it is for when it matters.

Law, fines, money and shops are in [09-society.md](09-society.md). Travel
time, journeys and vehicles are in
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md). The
single-player model of pause, turns and party turn order is in
[31-saves-and-party.md](31-saves-and-party.md).

## In this chapter

- [Concepts](#concepts)
- [The game clock and time modes](#the-game-clock-and-time-modes)
- [Letting time pass](#letting-time-pass)
- [Calendars](#calendars)
- [Seasons, day length and moons](#seasons-day-length-and-moons)
- [Holidays](#holidays)
- [Quest schedules](#quest-schedules)
- [Weather and climate](#weather-and-climate)
- [Sky and time-of-day text](#sky-and-time-of-day-text)
- [Tides](#tides)
- [Room states](#room-states)
- [Flooding](#flooding)
- [Darkness](#darkness)
- [Breath and underwater rooms](#breath-and-underwater-rooms)
- [Hazards](#hazards)
- [Whole-area resets](#whole-area-resets)
- [Scheduled tasks on the game clock](#scheduled-tasks-on-the-game-clock)
- [Reading time and weather from scripts](#reading-time-and-weather-from-scripts)
- [Reference](#reference)
- [Worked examples](#worked-examples)
- [Persistence](#persistence)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## Concepts

There are two layers of time, and most confusion comes from mixing them.

**The game clock** ([GameTime](../../CoffeeMud/com/planet_ink/coffee_mud/core/GameTime.java))
counts milliseconds. Everything that is timed in the world (ticks, cooldowns,
effect durations, decay, command pacing, the calendar, cron jobs) reads it
instead of the system clock. It normally runs with real time. It stands still
while the game is paused or turn-based, it can run faster or slower
(`TIMESCALE`), and it can be moved forward on request (`WAIT`, `MPPASSTIME`,
`TICKTOCK`).

**The calendar clock** (a `TimeClock`, class
[DefaultTimeClock](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultTimeClock.java))
turns game milliseconds into hours, days, months, years, seasons, moon
phases and tides. The world has one global calendar clock; an area can have
its own (see [Calendars](#calendars)). One game hour is
`MILLISPERMUDHOUR` milliseconds of game time; the stock value is 600000, ten
minutes.

Vocabulary used throughout:

| Term | Meaning |
| --- | --- |
| tick | The game's unit of action: 4 seconds of game time by default. Weather steps, mob behaviors and effects run on ticks. |
| mud hour, game hour | `MILLISPERMUDHOUR` ms of game time (default 10 minutes, 150 ticks). The clock's hour counter goes from 0 to hours-in-day minus 1. |
| time of day | DAWN, DAY, DUSK or NIGHT, from the clock's four boundary hours (`DAWNHR`, `DAYHR`, `DUSKHR`, `NIGHTHR`). |
| season | WINTER, SPRING, SUMMER or FALL, by month; a calendar can give seasons its own names. |
| climate | The weather tendency of an area: five bits (wet, cold, windy, hot, dry), optionally a named climate file. |
| weather | One of 14 states: CLEAR, CLOUDY, WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT, COLD, FOG. |
| room state | A named version of a room (dry, flooded, collapsed) that a behavior switches between. |

### Attaching properties and behaviors

Most of this chapter's tools are properties (effects such as `Prop_Hazard`)
and behaviors (such as `RoomStates`). A line like
`Prop_Hazard TYPE=COLD DAMAGE=1d6` is the property's ID followed by its
parameter text. To attach one:

- In game, `MODIFY ROOM AFFECTS` or `MODIFY ROOM BEHAVIORS` opens the effects
  or behaviors editor of the room you stand in; `MODIFY AREA AFFECTS` and
  `MODIFY AREA BEHAVIORS` do the same for the area, and the `MODIFY EXIT`
  and `MODIFY ITEM` editors have the same entries. In the editor you type
  the ID and then its parameter text. See
  [20-areas-rooms-exits.md](20-areas-rooms-exits.md).
- In a script, `MPAFFECT` and `MPBEHAVE` attach them at run time (see
  [27-mobprog-commands.md](27-mobprog-commands.md)).
- In world files, the object's effects and behaviors are saved with it, so a
  property you attach in builder mode and save is part of every new game.

Where a property says "Targets: Rooms, Areas", put it on one room for that
room only, or on an area for every room in it (and its child areas).
Parameters are `KEY=value` words; values with spaces go in double quotes.

Everything below runs on the game clock, so it is consistent under pause,
turn-based play, `TIMESCALE` and long waits. The one exception is the
`RLHOUR` option of [ResetWhole](#whole-area-resets), which reads the real
clock and is deprecated for single-player worlds.

CoffeeSUD adds to inherited CoffeeMud nearly everything in this chapter that
is marked W below: per-area calendars, custom seasons and moons, holiday
recurrences and tests by name, quest schedules, weather commands, climate
files, sky text, tides for builders, room states, floodable rooms,
darkness dangers, deep darkness, breath, typed hazards, `ResetWhole`
options, `Prop_TimeMode`, game-clock cron jobs and `TIMESCALE`.

## The game clock and time modes

Audience: creators and scripters.

### Real time, pause and turns

There are two time modes for the game as a whole.

- **REALTIME** (the default): the clock runs with the system clock. The
  world ticks on its own every 4 seconds. `PAUSE` stops the clock; `RESUME`
  (or `PAUSE` again) starts it.
- **TURNS** (turn-based): the clock stands still. It moves only when the
  player does something that takes time (by as long as it takes: a step is a
  fifth of a tick by default, an action in combat a whole tick; see
  `DEFCMDTIME` and `DEFCOMCMDTIME` in `coffeemud.ini`) or when the player
  uses `WAIT`. Everything due on the way (ticks, weather, TIME_PROGs, cron
  jobs) happens in order as the clock is moved a step at a time.

To pick the mode a game starts in, set `TIMEMODE` in `coffeemud.ini` or in
the world's `world.ini` (a world key):

```ini
TIMEMODE=TURNS
```

The accepted values are `TURNS` and `REALTIME`. In play, the player changes
mode with the `TIMEMODE` command:

```text
TIMEMODE
TIMEMODE TURNS
TIMEMODE REALTIME
TIMEMODE DIALOGUE INSTANT
TIMEMODE DIALOGUE TICKS
TIMEMODE DIALOGUE DEFAULT
```

`TIMEMODE` alone says which mode is in force. `TURN`, `TURNBASED` and
`TURN-BASED` are accepted for `TURNS`; `REAL` and `REAL-TIME` for
`REALTIME`. `TIMEMODE DIALOGUE` chooses whether NPC replies come at once and
take no time (`INSTANT`, the default in turn-based time) or on the NPCs' own
ticks (`TICKS`, the default in real time); `DIALOGUETIME` in the ini sets it
at start. `PAUSE` in turn-based time just says the world already waits.
Pause, turns and how a party takes turns in turn-based time are explained in
[31-saves-and-party.md](31-saves-and-party.md).

Scripts hear a change of mode as `GAME_PROG TIMEMODE` and can test it with
`ISTURNBASED()` and `ISPAUSED()`; JavaScript has `cm.isTurnBased()`.

Source: [TimeMode](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/TimeMode.java),
[Pause](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Pause.java).

### TIMESCALE

`TIMESCALE` runs the game clock faster or slower than real time while it
runs. It is a testing and pacing tool: at 8, eight game seconds pass in each
real second, so ticks, cooldowns, decay, effects and the calendar all come
eight times as soon. The range is 0.1 to 20. `PAUSE` and turn-based time work
as before (the rate waits until the clock runs again). The prompt shows
`(x8)` while the rate is not 1.

```text
TIMESCALE
TIMESCALE 8
TIMESCALE 1
```

The command is available only in builder mode or a `DEV=1` game. The start
value is `TIMESCALE=` in `coffeemud.ini` (default 1); it is not a
`world.ini` key. `TIMESCALE` changes the pace of the world, not the units
of authored timing: a `DURATION` in game hours is still game hours.

Source: [TimeScaleCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/TimeScaleCmd.java),
[GameTime](../../CoffeeMud/com/planet_ink/coffee_mud/core/GameTime.java).

### Prop_TimeMode: zones locked to one mode

A room or area with `Prop_TimeMode` switches the game to a time mode while
the played character is inside, and puts the earlier mode back on leaving.
Use it for a flooding tomb that must run in real time, or a puzzle hall that
must be turn-based.

| Parameter | Meaning |
| --- | --- |
| `MODE=TURNS` or `MODE=REALTIME` | The mode to hold (`TURNS` if absent; anything starting with `REAL` is real time). |
| `ONLYCOMBAT=TRUE` | Hold the mode only while the character fights there. |
| `RESTORE=TRUE` or `FALSE` | Put the earlier mode back on leaving (default true). With `FALSE` the zone's mode stays. |
| `MSG="text"` | Said as the mode switches. |

```text
Prop_TimeMode MODE=REALTIME MSG="Time seems to quicken; the water is rising."
```

While a zone holds the mode, `TIMEMODE TURNS` or `TIMEMODE REALTIME` answers
"Time is held ... here, and cannot be changed until you leave." A room's
property wins over its area's. See
[Prop_TimeMode](23-properties-reference.md#prop_timemode).

Source: [Prop_TimeMode](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_TimeMode.java),
[TimeLock](../../CoffeeMud/com/planet_ink/coffee_mud/core/TimeLock.java).

## Letting time pass

Audience: creators (commands), scripters (`MPPASSTIME`), JS authors.

Time can be spent without a busy script loop. All of these run the game's
own ticks, so characters heal, shops restock, weather changes, mobs follow
schedules and `TIME_PROG`s fire; they do not just move the calendar.

### WAIT

```text
WAIT
WAIT 10
WAIT 8 HOURS
WAIT 2 DAYS
WAIT 1 WEEKS
WAIT UNTIL DAWN
WAIT UNTIL NIGHT
WAIT UNTIL HOUR 14
WAIT UNTIL DAY 15
WAIT UNTIL ARRIVAL
```

- `WAIT` or `WAIT n` (ticks): lets one tick, or n ticks, pass while the clock
  is stopped (turn-based time, or paused). In real time it says time passes
  on its own. A wait of several ticks ends early if the character is hurt,
  attacked, moved, drawn into a fight, or spoken to.
- `WAIT n HOURS|DAYS|WEEKS` and `WAIT UNTIL ...` work in real time or turn
  time. A running clock is paused for the wait and started again after. The
  world goes on as it would, the player is told "An hour passes. It is now N
  o'clock." for each game hour, and the wait ends early as a plain wait does.
  A wait is capped at 72 game hours (the code says "You can wait three days
  at most", which is right only for 24-hour days); longer asks are cut to
  that.
- `WAIT n` with n over 100 ticks is treated as a long wait.
- `WAIT UNTIL` takes `DAWN`, `DAY` (or `MORNING`), `DUSK`, `NIGHT`, `HOUR n`
  (0 up to hours-in-day minus 1), `DAY n` (day of the month), and on a
  vessel or mount `ARRIVAL`, `STOP` or `DOCK`. If it is already that time of
  day, it waits for the next one. In builder mode and `DEV=1` it also takes
  `VAR [HOST] [NAME] [OP] [VALUE]` (OP is `==`, `!=`, `<`, `>`, `<=`, `>=`)
  and `QUEST [NAME] STEP [N]` or `QUEST [NAME] DONE`.
- In a party in turn-based time, only the leader's `WAIT` lets time pass
  (other members' waits sit out the leader's next turn); see
  [31-saves-and-party.md](31-saves-and-party.md).

`LONGWAIT` in `coffeemud.ini` (or `world.ini`) says who may wait more than
100 ticks or use hours/days/`UNTIL`:

```ini
LONGWAIT=ALL
```

`ALL` (the default) lets everyone; `BUILDERS` limits it to builder mode and
`DEV=1` games, which is the right choice for a world where resting through
the night would trivialise a survival loop.

Source: [Wait](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Wait.java),
[Waits](../../CoffeeMud/com/planet_ink/coffee_mud/core/Waits.java).

### Resting at an inn

A room with `Prop_InnRoom` makes a wait there restorative. A `WAIT` or
`MPPASSTIME` of `HOURS` game hours or more (1 by default) ends with the one
who waited fully rested (hit points, mana and movement) and `MSG` said.

```text
Prop_InnRoom HOURS=6 MSG="You wake in a soft bed, well rested."
```

See [Prop_InnRoom](23-properties-reference.md#prop_innroom).

### MPPASSTIME and MPINTERRUPT

From a MOBPROG:

```text
MPPASSTIME 8 HOURS
MPPASSTIME 1 DAYS INTERRUPTIBLE
MPPASSTIME UNTIL DAWN QUIET
MPPASSTIME UNTIL HOUR 6 INTERRUPTIBLE QUIET
MPINTERRUPT $n You are woken by shouting.
```

`MPPASSTIME` has the same limits and `UNTIL` conditions as `WAIT`.
`INTERRUPTIBLE` ends it if the player is hurt, attacked or moved; `QUIET`
suppresses the hourly lines. It logs an error if time is already passing.
`MPINTERRUPT [CODE] ([MESSAGE])` ends the wait or journey of that character
or their party. Reference entries: [MPPASSTIME and MPINTERRUPT](27-mobprog-commands.md#mppasstime).
JavaScript has `cm.passHours(n)` (the game's ticks pass, like `MPPASSTIME`),
`cm.passTime(hours[, area])` (the calendar goes forward hour by hour with its
events, like `TIME SET`), `cm.advance(ticks)` (turn-based time only) and
`cm.interrupt(who, message)`.

### Moving the calendar without running the world

These move the calendar clock forward hour by hour, running the clock's own
events (time-of-day changes, `TIME_PROG`, date changes) but not the ticks in
between, so nothing heals or respawns:

```text
TICKTOCK 10
TICKTOCK 2 MONTHS
TICKTOCK SET 812/2/3/4
TIME SET DUSK
TIME SET 14
TIME SEASON WINTER
```

- `TICKTOCK [N]` advances the area's clock N hours (negative goes back);
  `TICKTOCK [N] [HOURS|DAYS|WEEKS|MONTHS|YEARS]` bumps by that period.
  `TICKTOCK SET Y/M/D/H` or `M/D/Y Hh` sets the date. It needs the
  `TICKTOCK` security flag (a builder or `DEV=1` game has it). `TICKTOCK
  CLANTICK` and `TICKTOCK` with a service name also exist (see the generated
  [tool reference](tool-reference.md)).
- `TIME SET [HOUR|DAWN|DAY|DUSK|NIGHT]` runs the area's clock forward, hour by
  hour as `TICKTOCK` does, to that hour (it cannot go back; it wraps to the
  next day). `TIME SEASON [SPRING|SUMMER|FALL|WINTER]` runs it forward a month
  at a time until that season (at most 24 months). Both need builder mode, a
  `DEV=1` game, or the `CMDAREAS` flag. Because they replay clock events,
  use them to test `TIME_PROG`, season text and tidal changes.

Source: [TickTock](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/TickTock.java),
[EnvToolsCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/EnvToolsCmd.java).

### What happens to areas nobody is in

Passive areas "sleep". When the player returns, NPC routines catch up on what
they would have done meanwhile unless `CATCHUP=OFF` (an area tagged
`NOCATCHUP` never does). See [06-npcs-dialogue.md](06-npcs-dialogue.md).
