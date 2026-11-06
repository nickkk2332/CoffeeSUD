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

CoffeeSUD adds to inherited CoffeeMud much of what this chapter covers:
per-area calendars, custom seasons and moons, holiday
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
- `WAIT n` with n over 100 ticks is treated as a long wait (so `LONGWAIT`
  applies; where it is `BUILDERS`, others are held to 100 ticks).
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
  `TICKTOCK [N] [HOUR|DAY|WEEK|MONTH|SEASON|YEAR]` (plurals work) bumps by that period.
  `TICKTOCK SET Y/M/D/H` or `M/D/Y Hh` sets the date. It needs the
  `TICKTOCK` security flag. `TICKTOCK
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

## Calendars

Audience: creators (settings, `MODIFY AREA CALENDAR`), scripters (what scripts can read).

### The global calendar

The world's calendar is content. Set it in the `world.ini` of the world
folder (or in `coffeemud.ini`; `world.ini` is read after it, and the command
line wins over both). These are world keys (listed by `WORLD` or
`LIST WORLDINI`; in builder mode `WORLD SET KEY=VALUE` changes one in
`world.ini`):

| Key | Meaning | Default |
| --- | --- | --- |
| `MILLISPERMUDHOUR` | Game milliseconds in one game hour. | 600000 (ten minutes) |
| `HOURSINDAY` | Hours in a day; the hour counter runs 0 to this minus 1. | 6 |
| `DAYSINMONTH` | Days in a month; days count from 1. | 24 |
| `MONTHSINYEAR` | The month **names**, comma separated, in order. The number of names is the number of months. | seven months named "the 1st month" to "the 7th month" |
| `DAYSINWEEK` | The weekday **names**, comma separated. Empty means the world has no weekdays. | empty |
| `DAWNHR`, `DAYHR`, `DUSKHR`, `NIGHTHR` | The hour each time of day begins, in increasing order. `-1` for a day that has no daytime or no night. Night runs until `DAWNHR`. | 0, 1, 4, 5 |
| `YEARDESC` | Names for the years, comma separated, cycling when used up; `#` marks where the year number goes. | `year #` |
| `STARTDATE` | The date a new game starts at: `YEAR/MONTH/DAY/HOUR`. Months and days count from 1; a missing hour is dawn. | empty |

```ini
HOURSINDAY=10
DAYSINMONTH=30
MONTHSINYEAR=Leafwake,Bloomtide,Highsun,Frostfall
DAYSINWEEK=Sunday,Moonday,Fireday
DAWNHR=0
DAYHR=1
DUSKHR=8
NIGHTHR=9
YEARDESC=Year # of the Sundering
STARTDATE=812/2/3/1
```

Notes:

- `MILLISPERMUDHOUR` also sets how long a "game day" lasts in real time:
  `MILLISPERMUDHOUR x HOURSINDAY`. Weather steps are counted in ticks, not
  game hours, so changing `MILLISPERMUDHOUR` changes how many weather steps
  fit in a game day (see [Weather and climate](#weather-and-climate)).
- With no `STARTDATE` and no saved date, the global clock starts at hour -1
  of the 1st day of the second month of year 1000; its first tick moves it to
  hour 0.
- The seasons come from the month count (see
  [Seasons](#seasons-day-length-and-moons)); a world with 8 months has two
  months per season, and one with 7 months has one month each of winter,
  spring and summer and four of fall.

### An area's own calendar

An area can have a calendar of its own. Its child areas share it. What it
leaves out comes from its parent area's clock, else from the global clock. A
new game starts that clock at the calendar's `START` date; a saved game goes
on from its own date.

```text
MODIFY AREA CALENDAR
MODIFY AREA CALENDAR HOURS=10 DAYS=30 MONTHS="Leafwake,Bloomtide,Highsun,Frostfall" DAWNHR=0 DAYHR=1 DUSKHR=8 NIGHTHR=9 START=812/2/3
MODIFY AREA CALENDAR SEASONOFFSET=2
MODIFY AREA CALENDAR NONE
```

With no values, `MODIFY AREA CALENDAR` describes the current calendar (the
clock, the season and each moon's phase). `NONE` takes the calendar away. The
command acts on the area you stand in. If a key is unknown or a value
unreadable, the command prints the problems and changes nothing. After a
change the clocks are rebuilt (`Calendars.applyAll`), so a new calendar takes
effect at once. The area editor's calendar screen offers the basic settings
too (see [20-areas-rooms-exits.md](20-areas-rooms-exits.md)).

The calendar is stored as the area's `CALENDAR` blurb flag, in the area's
data in its `.cmare` file, so it ships with the world.

| Key | Value |
| --- | --- |
| `HOURS` | Hours in a day (1 or more). |
| `DAYS` | Days in a month (1 or more). |
| `MONTHS` | Month names, comma separated, in quotes if they contain spaces: `MONTHS="Leafwake,Bloomtide"`. |
| `WEEK` | Weekday names, comma separated. |
| `YEARS` | Year names with `#` for the number. |
| `DAWNHR`, `DAYHR`, `DUSKHR`, `NIGHTHR` | The four time-of-day hours (numbers). |
| `START` | `YEAR/MONTH/DAY` or `YEAR/MONTH/DAY/HOUR`, counted from 1; the hour left out is the dawn hour. Used only when the clock has no saved date. |
| `SEASONS`, `SEASONOFFSET`, `DAYLIGHT`, `MOONS` | See [Seasons, day length and moons](#seasons-day-length-and-moons). |

A child area that has its own `CALENDAR` keeps it and takes the keys it
leaves out from its parent. `SEASONOFFSET` alone does not make a new clock;
it only shifts that area's seasons on the parent's clock.

An area of class `StdTimeZone` (and `StdPlanet`, which extends it) always
owns a clock of its own, saved with the campaign. `CALENDAR` is how a time
zone's hours, months and start date become part of the world definition.

The date itself is **save state**, not content: it lives in the campaign
save (a `TIMECLOCK` record per clock). The world files hold only the
calendar's rules and `START`. A date set in builder mode (`TICKTOCK SET`,
`TIME SET`) is therefore not part of the shipped world; set `START` or
`STARTDATE` for that.

Source: [Calendars](../../CoffeeMud/com/planet_ink/coffee_mud/core/Calendars.java),
[DefaultTimeClock](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultTimeClock.java).

### Reading the date

```text
TIME
TIME GLOBAL
CALENDAR NOW
CALENDAR HOLIDAYS
```

- `TIME` shows the time of day and hour, the date (with the weekday if the
  calendar has weekday names), the season, and, at night under an open sky,
  the moon's phase (or why the moon cannot be seen: rain, cloud, fog, dust;
  `Prop_SkyText` `MOON=` replaces the phase line; a calendar with `MOONS` lists
  each moon). `TIME GLOBAL` shows the world clock. `DATE` is the same command.
- `CALENDAR` (the player's calendar) lists events: `CALENDAR NOW`, `SOON`,
  `RECENT`, `NEXT [n] YEAR|MONTH|DAY|WEEK`, `PAST ...`, `ADD`, `REMOVE`. In
  CoffeeSUD `CALENDAR HOLIDAYS` also lists each holiday with a game date,
  "on now" or "in n days".

Scripts see the date through `TIME_PROG`, `DAY_PROG`, `ISHOUR()`, `ISDAY()`,
`ISTIME()`, `ISSEASON()`, `ISMOON()` and `ISHOLIDAY()` (reference:
[28-mobprog-functions.md](28-mobprog-functions.md)), and JavaScript through `cm.clock(area)` (see
[Reading time and weather from scripts](#reading-time-and-weather-from-scripts)).

## Seasons, day length and moons

Audience: creators and scripters.

### The built-in seasons

With no `SEASONS` key, the year is cut into four equal seasons of
`floor(months / 4)` months each, in the order WINTER, SPRING, SUMMER, FALL,
with any leftover months going to FALL. (Month 1 is the first month of
winter.) The built-in moon follows the day of the month: new moon on the
first day, full moon about the middle, back to a waning crescent at the end.
Day length is one set of hours for the whole year.

### SEASONS

`SEASONS` names seasons, says which built-in season each counts as, and gives
its months:

```text
SEASONS="the Burn:SUMMER:1-2,the Rains:SPRING:Flood-Silt"
```

Each entry is `NAME:COUNTS:MONTHS`. `COUNTS` is `SPRING`, `SUMMER`, `FALL` or
`WINTER`: the built-in season the named one counts as for weather, `Prop_ClosedSeason`, the climate
tables and the like. `MONTHS` is one month or a range `from-to`, each a
month number counted from 1 or a month's name; a range that runs past the end
of the year wraps (`11-2`). Months no entry covers keep the built-in season.
Entries are tried in order.

Where a named season is understood:

- `ISSEASON(the Burn)` and `ISSEASON(summer)` (a script function; the built-in name matches too)
- the mask keys `-SEASON "+the Burn"` and `+SEASON ...`
- `<the Burn>...</the Burn>` blocks in `<VARIES>` description text (as well as
  `<SUMMER>...</SUMMER>` by the season it counts as); see
  [11-presentation-language.md](11-presentation-language.md)
- the `TIME` command ("It is the Burn.") and `MODIFY AREA CALENDAR`
- `SET SEASONSTART the Burn` for holidays and `SEASON the Burn` in a quest
  `SCHEDULE`
- `TIME SEASON` (by the built-in season it counts as)

### SEASONOFFSET

`SEASONOFFSET=n` shifts that area's seasons by n months on its parent's
clock, so a southern land can be in summer while its parent is in winter.
It belongs to the area, not to a clock, so setting it alone does not give the
area a clock of its own; child areas take it from their nearest parent that
has one. `Prop_ClosedSeason`, weather odds, `ISSEASON`, masks and `VARIES` use
the offset; `DAYLIGHT` does not (day length follows the clock's season).

### DAYLIGHT

`DAYLIGHT` gives a season its own dawn and night hours:

```text
DAYLIGHT="the Burn:0-5,the Rains:2-4"
DAYLIGHT="WINTER:2-8,SUMMER:0-10"
```

Each entry is `SEASON:DAWN-NIGHT` or `SEASON:DAWN-DAY-DUSK-NIGHT`. The
season is a named season or a built-in one. With two numbers, the day starts
one hour after dawn (or at night if sooner) and dusk is the hour before night
(or night's hour if there is no gap). The hours replace the calendar's
`DAWNHR`...`NIGHTHR` for time of day while that season lasts. Tides still use the
calendar's own day and dusk hours.

### MOONS

`MOONS` names moons:

```text
MOONS="Selune:24,Ember:9:3"
```

Each is `NAME:CYCLE` or `NAME:CYCLE:OFFSET`, in days. A moon has the eight
phases of the built-in moon (NEW, WAXCRESCENT, WAXQUARTER, WAXGIBBOUS, FULL,
WANEGIBBOUS, WANEQUARTER, WANECRESCENT), worked out from the days since the
calendar began plus the offset. **The first moon listed is "the moon"**: tides,
`MOON_PROG`, `ISMOON(FULL)` and the moon-altering effects follow it. The others are read by name: `ISMOON(Ember full)`. At night `TIME` tells
each moon's phase.

### Putting it together

```text
MODIFY AREA CALENDAR HOURS=6 DAYS=10 MONTHS="Ash,Cinder,Flood,Silt" SEASONS="the Burn:SUMMER:1-2,the Rains:SPRING:Flood-Silt" DAYLIGHT="the Burn:0-5,the Rains:2-4" MOONS="Selune:24,Ember:9:3" START=1/1/8/0
```

This is the calendar the `w22-seasons` test fixture uses
([w22-seasons.js](../../tools/fixtures/fixes/w22-seasons.js)). Test your own
with `TIME SEASON` and `TIME SET` (see [Letting time pass](#letting-time-pass)).

### Seasonal content

A calendar gives seasons a name; these tools make the world react:

| Tool | Does |
| --- | --- |
| `<VARIES>` text with `<WINTER>`, `<SPRING>`, `<SUMMER>`, `<FALL>`, `<DAY>`, `<NIGHT>`, `<DAWN>`, `<DUSK>`, weather names, `<HIGHTIDE>`, `<LOWTIDE>`, `<HOLIDAY name>` and your own season names | A room, mob or item description that changes with time and weather. Chapter [11](11-presentation-language.md) has the tag reference; preview with `LOOK AS` (below). |
| [Prop_ClosedSeason](23-properties-reference.md#prop_closedseason) | Hides things, darkens a room, or closes an exit in the seasons, times or weathers it names. |
| [Prop_ClosedDayNight](23-properties-reference.md#prop_closeddaynight) | Closes shops, rooms and exits by time of day or by `HOURS=a-b`. |
| `ResourceOverride` behavior | Swaps the resources a room offers by `SPRING=`, `SUMMER=`, `FALL=`, `WINTER=`, `SEASON=`, `MONTH=`, `HOUR=`. See [24-behaviors-and-masks.md](24-behaviors-and-masks.md#resourceoverride). |
| `Schedule` behavior | An NPC's daily routine by `SEASON=`, `DAYS=`, `WEATHER=` as well as the hour. See [06-npcs-dialogue.md](06-npcs-dialogue.md). |
| Holidays and `SCHEDULE` | Quests and holidays that start or run in a season. |

**Prop_ClosedSeason** takes one or more of `SUMMER SPRING WINTER FALL DAY NIGHT DUSK DAWN RAIN SLEET SNOW CLEAR FOG HEATWAVE THUNDERSTORM BLIZZARD WINDY DROUGHT DUSTSTORM COLD HAIL CLOUDY`
and an optional `MASK=[zapper mask]` last. While any named condition holds,
the thing it is on is "closed": a mob or item becomes unseen, silent and
unable to move or hear; a room becomes dark; an exit's door closes.
`Prop_ClosedSeason WINTER` on a mountain pass exit closes it in winter; add
`BLIZZARD` for blizzards in any season. The season tests use the season a
named season counts as. Mobs in combat are not hidden.

```text
Prop_ClosedSeason WINTER
Prop_ClosedSeason SPRING SUMMER FALL BLIZZARD
Prop_ClosedSeason NIGHT MASK=-RACE +ELF
```

**Prop_ClosedDayNight** (default: closed at night; `DAY` for closed by day;
`HOURS=a-b` for an hour window; `SLEEP`, `SIT`, `LOCKUP`, `HOME=`, `MASK=`,
`SHOPMSG=`, `WARNMSG[hour]=`) is the shopkeeper-friendly cousin; its full
syntax is in the properties reference.

## Holidays

Audience: creators (the holiday editor and the file), scripters (tests by name).

A holiday is a quest without a script. It starts by the calendar, applies to
the areas and mobs it names, and for its duration changes behaviors,
properties and stats such as shop prices. The stock file is
[holidays.quest](../../CoffeeMud/resources/quests/holidays/holidays.quest):
three weather holidays (`drought`, `famine`, `harsh winter`), which apply to
any area that meets their season and weather test, and `newyear` on day 1 of
month 1. The world's `world/quests/holidays.quest` is the one line
`LOAD=quests/holidays/holidays.quest`.

### Editing holidays in game

```text
LIST HOLIDAYS
CREATE HOLIDAY Moonfeast
MODIFY HOLIDAY Moonfeast
DESTROY HOLIDAY Moonfeast
```

`LIST HOLIDAYS` shows every defined holiday and its areas. `CREATE HOLIDAY
name` makes a new holiday that applies to the area you stand in, then opens
the editor. `MODIFY HOLIDAY` opens a numbered editor with: name; schedule
type (random interval, MUD-day, or real-life day), with its wait or date and
its duration; the area list; a mask for the mobs it affects; a mood; an
aggressive setting; shop pricing; MudChat lines; behaviors; and properties.
All three need the `CMDQUESTS` security flag. They edit the holiday quest, so a holiday set that ships with the
world should live in the world folder (below).

### The holiday file

Holidays are steps of one quest named `holidays`, using the quest script
language ([08-quests.md](08-quests.md#the-quest-script-format)). Each holiday
is a block of `SET` lines ending `STEP BREAK`:

```text
SET NAME newyear
SET MUDDAY 1-1
SET DURATION 900+(1?400)
quiet
set room
SET MOBGROUP RESELECT MASK=+INT 3
GIVE BEHAVIOR MUDCHAT +()\;9Happy New Year!\;9Happy New Year!\;\;(year | new year)\;9Happy New Year!\;\;
GIVE AFFECT MOOD happy
set mobgroup RESELECT mask=-JAVACLASS +StdShopkeeper +GenShopkeeper
GIVE STAT PRICEMASKS 0.9 +JAVACLASS -WandArchon
STEP BREAK
```

`CREATE HOLIDAY` appends its block to the holiday file in effect. To change
the stock set for one world, copy the stock file and put your own at
`world/resources/quests/holidays/holidays.quest` (the world's copy of a
resource file is read instead of the stock one; see
[19-configuration-and-administration.md](19-configuration-and-administration.md)
and [02-world-workflow.md](02-world-workflow.md)).

### When a holiday starts

| Setting | Starts |
| --- | --- |
| `SET WAIT n` / `SET INTERVAL n` | After a random wait of about n ticks, repeatedly. |
| `SET MUDDAY m-d` | Every game year on month m, day d (the game calendar; months and days count from 1). |
| `SET DATE m-d` | The same, on the game calendar in CoffeeSUD (it was a real-life date in CoffeeMud). |
| `SET WEEKDAY name` | Every week on that weekday (the world needs weekday names: `DAYSINWEEK` or `WEEK=`). |
| `SET EVERY n DAYS FROM y/m/d` | Every n game days counted from that date (`EVERY 7 FROM 1/1/1` also reads). |
| `SET SEASONSTART season` | The first day of that season (a built-in name or a calendar's own season name). |
| `SET MOON phase` or `SET MOON name phase` | The first day of that moon phase: `SET MOON FULL`, `SET MOON Ember FULL`. |
| `SET HOLIDAY name` | Runs exactly while another holiday runs. |

`SET DURATION` can be ticks, an expression such as `900+(1?400)`, or game
time: `2 DAYS` is two game days and `3 HOURS` is three game hours. The quest
chapter's [time expressions](08-quests.md#time-expressions) apply. The
holiday editor's choices (random interval, MUD-day, RL-day) do not offer
`WEEKDAY`, `EVERY`, `SEASONSTART` or `MOON`: write those in the file by hand
(and test that the editor keeps them if you also use it; see the audit notes).

### Testing a holiday by name

A holiday is "on" while its quest runs. Everything keys on the quest's name:

| Where | Form |
| --- | --- |
| Script condition | `if isholiday(moonfeast)` |
| Script value | `$%HOLIDAY()%`: the names on now |
| Zapper mask | `+HOLIDAY moonfeast` (while it is on), `+HOLIDAY` (while any holiday is on); `-HOLIDAY moonfeast` the opposite |
| Description text | `<HOLIDAY moonfeast>Lanterns hang from every eave.</HOLIDAY>` inside `<VARIES>` |
| Quest | `SET HOLIDAY moonfeast` |
| JavaScript | `cm.holiday("moonfeast")`, `cm.holidays()` |
| Players | `CALENDAR HOLIDAYS` |

A quest named `moonfeast#2` still counts as `moonfeast`.

## Quest schedules

A quest (not only a holiday) can run in a window of game time:

```text
SET NAME fair
SET AREA Riverton
SET SCHEDULE "WEEKDAY Fireday"
SET DURATION UNTIL DUSK
```

`SET SCHEDULE "rules"` holds when **every** rule holds; the quest starts as
the window opens, once each time. Rules:

| Rule | Holds |
| --- | --- |
| `HOUR a-b` | The hour is from a to b on the quest's clock (a range that wraps, `22-3`, works). |
| `WEEKDAY name` | It is that weekday. |
| `EVERY n DAYS` | The day number is a multiple of n. Optional `AT DAWN` or `AT HOUR h` waits until that hour of such a day. |
| `SEASON name` | The season (a named one, or the built-in one it counts as). |
| `MONTH name-or-number` | That month. |
| `MOON phase` or `MOON name phase` | The moon is in that phase. |
| `HOLIDAY name` | That holiday is on. |
| `WEATHER state` | The quest area's weather begins with that word (`WEATHER RAIN`). |

Separate rules with spaces or `AND`. `SET DURATION UNTIL DUSK` ends the quest
at dusk, `UNTIL HOUR h` at that hour, `UNTIL END` as the window closes; a
`DURATION` in ticks or game days counts down as usual. The quest checker
(`SCRIPTCHECK`/`QUESTCHECK`) reports a `SCHEDULE` with no rules or an
unreadable `HOUR` or `EVERY`.

Source: [Schedules](../../CoffeeMud/com/planet_ink/coffee_mud/core/Schedules.java),
[Holidays](../../CoffeeMud/com/planet_ink/coffee_mud/core/Holidays.java).

## Weather and climate

Audience: creators (climate, commands, files), scripters (`MPWEATHER`, triggers), engine developers.

### How weather works

Each area has a climate object that keeps the weather *now* and the weather
*next*. About every 150 ticks (ten minutes of game time with the default 4
second tick, one game hour at the stock `MILLISPERMUDHOUR`) the next weather
becomes the current one, a new next weather is rolled, and everyone under the
sky is told ("It starts to rain", "The rain stops"). The roll depends on
the weather that is coming, the area's season, and the area's climate bits.

The 14 weather states, as `WEATHER` and the commands name them:

`CLEAR`, `CLOUDY`, `WINDY`, `RAIN`, `THUNDERSTORM`, `SNOW`, `HAIL`, `HEAT`,
`SLEET`, `BLIZZARD`, `DUST`, `DROUGHT`, `COLD`, `FOG`.

`<VARIES>` tags and `Prop_ClosedSeason` spell `HEAT` as `HEATWAVE` and
`DUST` as `DUSTSTORM`; the weather commands accept both spellings. Masks use
the list above (`-WEATHER +BLIZZARD +SNOW`).

Rules that are worth knowing:

- Indoor rooms, and rooms with no sky, always read as `CLEAR`; only rooms
  under a sky are told about the weather.
- At night, and in a blizzard or dust storm, a room under the sky is dark
  unless a light is lit in it (`DISABLE=DARKNIGHTS` and `DISABLE=DARKWEATHER`
  turn those two causes off).
- An area whose climate has the *void* bit is always `CLEAR`.
- `DISABLE=WEATHER` turns the whole system off (everything stays `CLEAR`),
  `DISABLE=WEATHERCHANGES` stops it changing, and `DISABLE=WEATHERNOTIFIES`
  and `DISABLE=TODNOTIFIES` silence the "it starts to rain" and "the sun
  rises" lines. These are `DISABLE` flags in `world.ini` (see
  [19-configuration-and-administration.md](19-configuration-and-administration.md)).
- The effects of weather on the world (slipping in rain, lightning, rust,
  wind, hail, gusts, puddles, ships damaged by storms) belong to the
  `WeatherAffects` behavior. It is added to every standard area
  automatically; `AUTOWEATHERPARMS` in `coffeemud.ini` sets its parameters
  for all areas, or `NO` to keep it off. Its parameters are listed in
  [24-behaviors-and-masks.md](24-behaviors-and-masks.md#weatheraffects).

### Climate bits

An area has five climate bits and a "void" bit: wet, cold, windy, hot, dry
(and void). A room can override its area's (`-1` or "inherit" means use the
area's). They add weight to the weather odds: a wet area rains more, a cold
one snows, a hot one has heat waves.

```text
MODIFY AREA CLIMATE RC
MODIFY AREA CLIMATE WET|COLD
MODIFY ROOM CLIMATE HOT|DRY
```

The one-line letters are `R` rainy/wet, `H` hot, `C` cold, `W` windy, `D` dry,
`N` normal. **Bug:** in `MODIFY AREA CLIMATE`, the `D` letter sets the windy
bit, not dry; use the named form (`DRY|HOT`) for exactness (see
[20-areas-rooms-exits.md](20-areas-rooms-exits.md)). Locale classes bring
their own default climate where an area leaves it open.

### Named climates: climate files

An area can instead use a **climate file**, which gives exact weights for
every weather in every season. This is the way to make a monsoon coast, a
desert that never snows, or a cave that only has fog.

```text
MODIFY AREA WEATHERPROFILE monsoon
MODIFY AREA WEATHERPROFILE NONE
```

The name picks `world/climates/monsoon.ini`, else
`resources/climates/monsoon.ini`. Child areas inherit the climate; the
area's climate bits still add to the file's weights. The file is plain text,
one `KEY=value` per line; `#` starts a comment:

```ini
# world/climates/monsoon.ini
SUMMER=RAIN 40, THUNDERSTORM 25, CLOUDY 20, CLEAR 10, FOG 5
WINTER=CLEAR 50, CLOUDY 30, WINDY 20
ALL=CLEAR 40, CLOUDY 30, RAIN 20, FOG 10
TRANSITION=RAIN->THUNDERSTORM=30, CLEAR->RAIN=10
HOLD=3
TEXT_RAIN=Warm monsoon rain drums on the reeds.|Monsoon rain hammers the thatch.
STOP_RAIN=The monsoon rain thins to a drizzle.
```

| Key | Meaning |
| --- | --- |
| `SPRING`, `SUMMER`, `FALL`, `WINTER` | A season's weights: `WEATHER N` pairs, comma separated (`:` and `=` also work between name and number). |
| `ALL` | The weights for every season the file does not give. |
| `TRANSITION` | `FROM->TO=N` pairs: the chance of moving from one weather to another, in place of the built-in one (a negative number all but forbids it). |
| `HOLD` | The fewest game hours a weather lasts before it changes on its own. Forced changes (`WEATHER SET`, `NEXT`) ignore it. |
| `ALLOW` | The only weathers this climate has (`ALLOW=FOG,CLEAR`); an odd weather is never rolled. |
| `TEXT_[WEATHER]` | The weather's description, as LOOK and WEATHER tell it, in place of `lists.ini`'s. Alternatives are separated by `\|`; `NONE` for nothing. |
| `STOP_[WEATHER]` | The line told as that weather ends. Same form. |

Weather names are those of the list above; `STORM` and `THUNDER` read as
THUNDERSTORM, `DUSTSTORM` as DUST, `WINTER`/`WINTERCOLD` as COLD. An
unreadable line or a missing file is logged (`mud.log`) and the area keeps
the built-in climate. `WEATHER FORECAST` names the climate in use. Which
season's weights apply is the season the area is in *as it counts for the
weather*, so a named season (`the Rains` counting as `SPRING`) uses the SPRING
line. The files are cached; restart or reload the world to re-read them.

Source: [ClimateProfiles](../../CoffeeMud/com/planet_ink/coffee_mud/core/ClimateProfiles.java),
[DefaultClimate](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultClimate.java).

### Pinning and linking weather

- [Prop_Weather](23-properties-reference.md#prop_weather) on an area pins the
  weather: `Prop_Weather RAIN` keeps it raining; `Prop_Weather RAIN CLIMATE_WET`
  also sets climate bits (`CLIMATE_NORMAL`, `CLIMATE_WET`, `CLIMATE_COLD`,
  `CLIMATE_WINDY`, `CLIMATE_HOT`, `CLIMATE_DRY`, `CLIMATE_VOID`). It sets the
  current and next weather every time the area recalculates, so a pinned area
  does not change and `WEATHER SET` is overridden. In a script,
  `MPAFFECT Prop_Weather $a 300TICKS THUNDERSTORM` pins it for 300 ticks.
- [LinkedWeather](24-behaviors-and-masks.md#linkedweather) makes an area copy
  another's weather (`LinkedWeather Hidden Area`), or with `;ROLLING` take it
  on the next change. A hidden "climate template" area plus linked areas is
  a cheap way to give a region one weather.

### Commands

Builder commands need builder mode, a `DEV=1` game, or the `CMDAREAS` flag.

```text
WEATHER
WEATHER WORLD
WEATHER SET THUNDERSTORM
WEATHER SET SNOW NEXT
WEATHER SET FOG FOR 3 HOURS
WEATHER SET RAIN AREA Riverton
WEATHER FORECAST
WEATHER NEXT
```

| Command | Does |
| --- | --- |
| `WEATHER` | Everyone: the weather where you stand, in words. |
| `WEATHER WORLD` | Everyone, outdoors: each area's weather. |
| `WEATHER SET [weather] (FOR [n] HOURS\|DAYS\|WEEKS\|MONTHS\|TICKS) (AREA [name]) (NEXT)` | Changes the area's weather *now*, as a natural change does (the end and start messages, `WEATHER_PROG` and `EXECMSG_PROG WEATHERCHANGE` fire). With `NEXT`, sets only the weather that comes next. With `FOR`, holds it there: each change comes back to it until the time is up, then the weather goes on its own. An unknown word answers `'X' is no weather` and lists them. |
| `WEATHER FORECAST` | Builders: the weather now and next, the climate file in use, a hold with the game hours left, and the odds of each weather the step after that. |
| `WEATHER NEXT` | Builders: makes the next change happen now. |

`WEATHER FORECAST` is the quick check after editing a climate or a calendar.
A sample answer:

```text
Weather in Riverton: rain, then fog.
Climate: monsoon (climates/monsoon.ini)
After that: rain 35%, thunderstorm 25%, cloudy 20%, clear 10%, fog 10%.
```

From a MOBPROG:

```text
MPWEATHER HERE THUNDERSTORM FOR 2 HOURS
MPWEATHER $a RAIN NEXT
MPWEATHER Riverton CLEAR QUIET
```

`MPWEATHER [AREA/HERE] [WEATHER] (NEXT) (FOR [N] HOURS/DAYS) (QUIET)`: the
area is `HERE` (the host's area), an area name, or an object code whose area is
used. It sends the normal messages and `WEATHER_PROG`s unless `QUIET`. A hold
survives a save. Reference: [27-mobprog-commands.md](27-mobprog-commands.md#mpweather).
JavaScript: `cm.weather(area)` has `get()`, `next()`, `describe()`,
`forecast()`, `set(word)`, `setNext(word)`, `hold(word, hours)` and
`chances()`.

Scripts react to weather with `WEATHER_PROG [WEATHER/ANY] (OLD WEATHER)`
(`$g` the new weather, `$0` the old, `$1` the forecast),
`ISWEATHER(RAIN)`, and the mask key `-WEATHER +RAIN +THUNDERSTORM`. They also
react to the moon with `MOON_PROG`. Reference: [26-mobprog-triggers.md](26-mobprog-triggers.md#weather_prog).

## Sky and time-of-day text

The lines the world says as dawn breaks, as it starts to snow, or as the moon
rises come from `resources/lists.ini` (`TOD_CHANGE_OUTSIDE`,
`TOD_CHANGE_INSIDE`, `WEATHER_*` and `WEATHER_ENDS`) for the whole world. Those
lists are shared by a cave city and a desert, so each area can override them
with [Prop_SkyText](23-properties-reference.md#prop_skytext) on the area or a
room:

| Key | Text |
| --- | --- |
| `DAWN`, `DAY`, `DUSK`, `NIGHT` | Told under the sky as the time of day changes. |
| `INDAWN`, `INDAY`, `INDUSK`, `INNIGHT` | The same, indoors (in rooms with no sky). |
| `WEATHER_[STATE]` | That weather's description, as it starts and as LOOK and WEATHER tell it (`WEATHER_SNOW`, `WEATHER_RAIN`, ...). |
| `STOP_[STATE]` | That weather's ending line. |
| `MOON` | `TIME`'s moon line. |

```text
Prop_SkyText DAWN="The red sun drags itself over the ash dunes." WEATHER_SNOW="Grey ash begins to fall."
Prop_SkyText DAWN=NONE NIGHT="The glowcaps dim as night falls on the surface far above."
```

Alternatives separated by `|` are picked at random each time; `NONE` says
nothing. A room's own line comes before its area's, an area's before its
parents'; what none of them sets comes from the area's climate file
(`TEXT_` and `STOP_`), then `lists.ini`. The three ways to give an area its
own text are `Prop_SkyText` (per object, in the world), climate files (per
climate) and `Prop_LocalLists`; pick one so they do not fight. To go
further, `DISABLE=TODNOTIFIES` or `WEATHERNOTIFIES` silences the stock
lines and your own `TIME_PROG`/`WEATHER_PROG` scripts can `MPECHO` instead.

Source: [Prop_SkyText](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_SkyText.java),
[SkyText](../../CoffeeMud/com/planet_ink/coffee_mud/core/SkyText.java).

### Previewing a room under conditions

`LOOK AS [CONDITIONS]` (builders, same flags as the weather commands) shows
the room's title and description under made-up conditions, as `PREVIEW` does:

```text
LOOK AS NIGHT FOG
LOOK AS DAY
LOOK AS WINTER SNOW
```

Conditions are time of day (`DAY`, `NIGHT`, `DAWN`, `DUSK`), season, weather,
`FROM [DIRECTION]`, `VISITED`/`FIRSTVISIT`, `AS [CHARACTER]` and
`VAR [NAME]=[VALUE]`. It is the fastest way to test `<VARIES>` text.

## Tides

Audience: creators and scripters.

CoffeeMud's clock already worked out a tide; CoffeeSUD makes it usable by
builders. The tide in a room comes from its area's clock and moon:

- **Low tide** in the hour the day begins (`DAYHR`), **high tide** in the
  dusk hour (`DUSKHR`), and **no tide** the rest of the time.
- Its strength comes from the moon (the first named moon, if the calendar
  has `MOONS`): SPRING tides at new and full moon, NORMAL between, NEAP at the
  quarters. The phases are `SPRING_HIGH`, `SPRING_LOW`, `NORMAL_HIGH`,
  `NORMAL_LOW`, `NEAP_HIGH`, `NEAP_LOW` and `NO_TIDE`.
- Tide-altering spells and effects on the room or area shift the phase.

With the stock six-hour day that is one game hour of low tide and one of high
tide per day, so a bare check is brief. Everything below therefore takes a
**span**: the number of hours either side of the tide's hour that still count.
A span of 2 on a six-hour day makes it high tide for most of the evening.

### Reading the tide

| Where | Form |
| --- | --- |
| Script condition | `ISTIDE(HERE HIGH)`, `ISTIDE(Cove#3 LOW)`, `ISTIDE(HERE SPRING_HIGH)`, `ISTIDE(HERE HIGH:2)` |
| Zapper mask | `-TIDE +HIGH` (passes at high tide), `+TIDE -HIGH` (passes unless it is high tide); `LOW`, `NONE`, `SPRING`, `NEAP`, `NORMAL`, a full phase name, and `:span` |
| `<VARIES>` text | `<HIGHTIDE>...</HIGHTIDE>`, `<LOWTIDE>...</LOWTIDE>` |

The words are `HIGH`, `LOW`, `NONE` (or `NO_TIDE`), `SPRING`, `NEAP`, `NORMAL` or a
phase name; `HIGH:2` means "high tide within two hours". Reference:
[ISTIDE](28-mobprog-functions.md#istide).

### Prop_TidalExit: a way open at one tide

[Prop_TidalExit](23-properties-reference.md#prop_tidalexit) goes on an exit:
the exit can be used only at one tide.

| Parameter | Meaning |
| --- | --- |
| `OPEN=LOW` or `OPEN=HIGH` | The tide at which the exit works (default `LOW`). |
| `SPAN=[hours]` | Hours either side of the tide's hour that count (default 0, only the tide's own hour). |
| `MSG="text"` | Said to someone who tries it the rest of the time (default "The way is under water; it is passable only at low tide."). |

```text
Prop_TidalExit OPEN=LOW SPAN=1 MSG="The causeway is under water."
```

### TidalRoom: a shore room the tide floods

[TidalRoom](24-behaviors-and-masks.md#tidalroom) is a [RoomStates](#room-states)
preset on a room. At high tide it is a swimming room with its own title and
description; with `DEEP`, at the spring tides its air is water; at the ebb it
is itself again.

| Parameter | Meaning |
| --- | --- |
| `SPAN=[hours]` | Hours either side of high tide that count as flooded. |
| `TITLE="..."`, `DESC="..."` | The room's title and description while flooded. |
| `FLOODMSG="..."` | What those who come in while it is flooded are told (default "The tide is in: the sea swirls about you."). |
| `DEEP` | At spring high tides the room's atmosphere becomes water (a drowning room; see [Breath](#breath-and-underwater-rooms)). |
| `WASH=[weight]` | Loose, gettable things weighing up to this are washed away as the water comes. |

The state names are `ebb`, `flooded` and `deep`; `ROOMSTATE(HERE)` reads
them. See [Worked example 1](#example-1-a-coastal-town-with-tides-and-storms).
Source: [Tides](../../CoffeeMud/com/planet_ink/coffee_mud/core/Tides.java),
[Prop_TidalExit](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_TidalExit.java),
[TidalRoom](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/TidalRoom.java).

## Room states

Audience: creators, scripters.

A room (or a whole area) can have named versions that a behavior switches
between: a mine shaft that is dry, flooded, collapsed or cleared; a market
square that is a fairground on festival days. [RoomStates](24-behaviors-and-masks.md#roomstates)
declares the versions and the rules for moving between them in one place.

The parameters are **clauses separated by semicolons** (a semicolon inside
double quotes does not split). There are three kinds.

**A state** — `STATE=name` with any of:

| Key | Meaning |
| --- | --- |
| `TITLE="..."`, `DESC="..."` | The room's display text and description in this state. |
| `ATMOSPHERE=` | `WATER`, `AIR`, or any raw material name. `AIR`, `NORMAL` and `DEFAULT` restore the normal air. A state in `WATER` makes the room drown people unless they can breathe it. |
| `CLIMATE=` | Climate bits: `WET`, `COLD`, `WINDY`, `HOT`, `DRY`, comma separated. |
| `ADD="Prop_RoomDark();WaterCurrents(go=east)"` | Effects and behaviors the room has while in this state, as `ID(parameters)`, separated by `;` inside the quotes. They are removed when the state ends. |
| `BLOCK=north,down` | Ways out that are refused ("The way north is blocked.") while in this state. |
| `OPEN=up` | Doors that open in this state. |
| `ENTERMSG="..."` | Said to those who come in. |

What a state leaves unset is the room's own. The first state is the start state.

**A transition** — `TO=name` with any of:

| Key | Meaning |
| --- | --- |
| `FROM=a,b` | Only from these states (default any). |
| `WHEN="mask"` | A zapper mask checked on a character in the room each tick. |
| `AFTER=n` | The `WHEN` must hold for n ticks in a row (without `WHEN`: n ticks in the state). |
| `TICKS=n` | After this many ticks in the current state. |
| `ONVAR="[host] name==value"` | A script variable test; the host `*` if not given. |

A transition fires when all its given conditions hold. The first that does wins.

**Settings**: `START=name` the first state (the first declared if omitted);
`ROOMS=Area#1,Area#2` (on an area) the rooms that change; without it every
room in the area changes together.

```text
RoomStates STATE=dry TITLE="A dusty shaft"; STATE=flooded TITLE="A flooded shaft" ADD="Chant_Flood()" BLOCK=down ENTERMSG="Cold water swirls round your knees."; TO=flooded WHEN="-WEATHER +RAIN" AFTER=2; TO=dry FROM=flooded TICKS=300; START=dry
```

**Changing and reading a state from a script:**

```text
MPROOMSTATE HERE flooded
MPROOMSTATE Deepdelve cleared
MPSETVAR $i SHAFT $%ROOMSTATE(HERE)%
IF ROOMSTATE(HERE == flooded)
```

`MPROOMSTATE [ROOM ID/HERE/AREA NAME] [STATE]` switches at once (a script
error is logged if no `RoomStates` is there or the state is unknown).
`ROOMSTATE([ROOM ID/HERE/AREA NAME])` reads the state of the room's (else
its area's) behavior. `VARIES` masks can test it too. A transition that
needs a script variable fires without you calling `MPROOMSTATE`. See
[ROOMSTATE](28-mobprog-functions.md#roomstate) and
[MPROOMSTATE](27-mobprog-commands.md#mproomstate).

The current state is saved with the campaign (as a room change), so a
collapsed shaft stays collapsed after a restore. The room's original title,
description, atmosphere and climate are remembered and come back when the
state sets none.

Source: [RoomStates](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/RoomStates.java).

## Flooding

[Floodable](24-behaviors-and-masks.md#floodable) is a RoomStates preset for a
room whose water level rises and falls by itself. It has four levels:

| Level | State | Effect |
| --- | --- | --- |
| 0 | `dry` | Nothing. |
| 1 | `ankle` | Ankle-deep (text only unless you give `DESC1`). |
| 2 | `waist` | A swimming room (`Chant_Flood`); lit torches are put out; loose things weighing up to `FLOAT` float off. |
| 3 | `full` | The room's air is water: whoever is in it drowns once their breath runs out. |

While its condition holds the water rises one level every `RATE` ticks; when
it does not, it drains one level every `DRAIN` ticks.

| Parameter | Meaning |
| --- | --- |
| `RISE="mask"` | The water rises while someone in the room fits the mask, e.g. `"-WEATHER +RAIN +THUNDERSTORM"`. |
| `ONVAR="[host] name==value"` | Rises while the script variable holds. |
| `TIDE=HIGH` | Rises at high tide (same words as `ISTIDE`). |
| `RATE=[ticks]` | Ticks per level rising (default 10). |
| `DRAIN=[ticks]` | Ticks per level draining (default = `RATE`). |
| `FLOWTO=[dir]` | Where floated-off things go; without it they are destroyed. |
| `FLOAT=[weight]` | Loose things this light or lighter float off (default 2). |
| `TITLE1`..`TITLE3`, `DESC1`..`DESC3` | The title and description at levels 1 to 3. |
| `MSG1`..`MSG3` | What those coming in are told at that level. |

Any one of `RISE`, `ONVAR`, `TIDE` holding makes the water rise. Read the
level with `FLOODLEVEL(HERE)` (0 to 3) in a script.

```text
Floodable RISE="-WEATHER +RAIN +THUNDERSTORM" RATE=15 DRAIN=30 FLOWTO=east DESC2="Storm water swirls waist-deep through the drain."
Floodable ONVAR="pump sluice==open" RATE=5
```

For a trap that floods once and drains, use `Trap_FloodRoom`; for a timed
spell-like flood, `Chant_Flood` (both in
[25-abilities-and-effects.md](25-abilities-and-effects.md)). Reference:
[FLOODLEVEL](28-mobprog-functions.md#floodlevel).

Source: [Floodable](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Floodable.java).

## Darkness

Audience: creators, scripters.

A room is **dark** when its dark disposition is set and no light overcomes
it. In the stock rules darkness comes from:

- night, in a room under the sky (`DISABLE=DARKNIGHTS` turns this off), and
  blizzards and dust storms (`DISABLE=DARKWEATHER`);
- a locale that is dark by nature (caves, many underground and underwater
  locales);
- [Prop_RoomDark](23-properties-reference.md#prop_roomdark) on a room or area;
- [Prop_ClosedSeason](23-properties-reference.md#prop_closedseason) or
  [Prop_ClosedDayNight](23-properties-reference.md#prop_closeddaynight) on a room;
- spells such as darkness.

A **lit light** (a torch, a lantern, a glowing item) in the room, carried or
lying there, ends ordinary darkness. A character who **sees in the dark**
(darkvision, infravision, a spell) sees anyway. Darkness *to a character*
means: the room is dark, and that character cannot see in the dark.

### Testing darkness

| Where | Form |
| --- | --- |
| Script condition | `ISDARK(HERE)`, `ISLIT(HERE)`, `ISDARK(Cave#5)`; also as values `$%ISDARK(HERE)%` |
| Zapper mask | `+ROOMDARK` passes those the room they stand in is dark to; `-ROOMDARK` passes everyone else. (This key reads the room from the character's side, so it works as a `WHEN` in properties like `Prop_Hazard`.) |
| Description text | `Prop_DarkDescription` and `<DARK>`/`<LIT>` blocks (see below) |

References: [ISDARK and ISLIT](28-mobprog-functions.md#isdark).

[Prop_DarkDescription](23-properties-reference.md#prop_darkdescription) gives a
dark room a description that does not reveal its contents:
`TITLE="Somewhere dark" DESC="Cold water laps at your knees."`; `<VARIES>` is
allowed in it. Alternatively put `DARK` and `LIT` blocks in the room's
description text ([11-presentation-language.md](11-presentation-language.md)).

### Prop_RoomDark

Ordinary darkness, optionally only at certain hours, or deep darkness that
lights cannot beat.

| Parameter | Meaning |
| --- | --- |
| `HOURS=1,4,0` | Dark only in these hours of the day (comma separated numbers, on the area's clock). Without it the room is always dark. |
| `ABSOLUTE=TRUE` | Deep darkness. The lights of those in the room, and of what lies there, go dark while they are in it. Everything ordinary light can do is overruled until they leave. |
| `LIGHTMASK="mask"` | With `ABSOLUTE`, the lights that still work: an item mask such as `"-TAGS +DAWNLANTERN"` (a tag set in the item editor's Tags field). |
| `SEEMASK="mask"` | Who still sees in the dark: a mask on the character, such as `"-RACE +Dwarf"`. |
| `SNUFF=[pct]` | The percent chance that each lit light a character carries goes out as they walk in. |
| `SNUFFMSG="text"` | What they are told (`@x1` is the light's name; default "Your @x1 gutters out in the dark."). |

```text
Prop_RoomDark HOURS=1,4,0
Prop_RoomDark ABSOLUTE=TRUE LIGHTMASK="-TAGS +DAWNLANTERN" SEEMASK="-RACE +Dwarf" SNUFF=30
```

Plain `Prop_RoomDark` is beaten by any torch (lights are applied after room
effects). `ABSOLUTE=TRUE` reapplies the darkness after people and items have
been counted, and switches off their light source flag, as the darkness-globe
spell does. A torch can still burn (it is lit, it just gives no light) and
lights again outside.

### Prop_DarkDanger: dangers in the dark

[Prop_DarkDanger](23-properties-reference.md#prop_darkdanger) goes on a room or
area. It acts only on a **player** who is in a room that is dark to them (a lit
torch or darkvision ends it).

| Parameter | Meaning |
| --- | --- |
| `LOST=[pct]%` | A move goes a random open way instead (`MSG` says so; default "You lose your way in the dark."). |
| `STUMBLE=[pct]%` | A move fails and costs 5 movement (`STUMBLEMSG`; default "You stumble in the dark and fall."). |
| `LURKER=[catalog mob]` | With `LURK=[pct]%` (3% if only `LURKER` is given): as they come in, a copy of this catalog mob (or mob class) comes out of the dark (`LURKMSG`; default "Something comes at you out of the dark!"). A `catalog:` prefix is allowed. |

```text
Prop_DarkDanger LOST=10% STUMBLE=5% LURKER=ghoul LURK=3%
```

`STUMBLE` is checked before `LOST`. The mob must exist in the catalog (or be
a valid mob class) or an error is logged.

Dark rooms also pair well with [Prop_Hazard](#hazards) (`DARKONLY`).

Source: [Prop_RoomDark](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_RoomDark.java),
[Prop_DarkDanger](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_DarkDanger.java).

## Breath and underwater rooms

Audience: creators, scripters.

**Atmosphere** is a room's (or area's) breathable medium: the raw material
that fills it. Normal rooms hold air; an underwater room holds water. A
character who cannot breathe the room's atmosphere (a land-dweller in water, an
air-breather in a gas, an undead in air that is not theirs, whatever the race
says) is "short of air". There are three ways to make a place hold water or
a gas:

- pick an underwater locale (`UnderWater`, `UnderSaltWater`, `IndoorUnderWater`,
  grid and maze versions; `LIST LOCALES`);
- set the room's or area's atmosphere stat: `MODIFY ROOM ATMOSPHERE SALT WATER`
  (a raw material name, or `-1` to inherit from the area; see
  [20-areas-rooms-exits.md](20-areas-rooms-exits.md));
- let a state or behavior change it ([RoomStates](#room-states) `ATMOSPHERE=WATER`,
  [Floodable](#flooding) level 3, [TidalRoom](#tides) `DEEP`).

### The breath count

CoffeeSUD gives everyone a held breath before choking or drowning starts.
Each tick a character is short of air they spend a tick of breath. At half
and at a quarter of the count they are warned ("You have half your breath
left", "Your lungs burn: you are nearly out of breath!"); at zero ("Your
breath is gone!") the old rules apply: damage every tick ("`<name>` can't
breathe!" or "is drowning in water!"). Breathable air fills the breath again
("You gasp in a breath of air."). Golems do not breathe.

The size of the breath is the **`BREATHTICKS`** setting plus carried air:

```ini
BREATHTICKS=@x1/3
```

`BREATHTICKS` is a formula (`@x1` the character's constitution, `@x2` the
level); the stock value is `@x1/3`, so constitution 12 holds breath for 4 ticks. `0`
turns the count off (immediate damage, as before). It is a world key
(`coffeemud.ini` or `world.ini`).

[Prop_AirSupply](23-properties-reference.md#prop_airsupply) on an **item**
adds to it: whoever carries the item can hold their breath `TICKS` ticks
longer.

```text
Prop_AirSupply TICKS=6
```

Put that on an air bladder, a diving bell hood or a bottle of sweet air. The
effects add: an air bladder of 6 on a character with a breath of 4 gives 10.

Magic and races that let a character breathe the medium (water breathing, gills,
the room's atmosphere matching their race) mean they are never short of air.

### Reading breath

| Where | Form |
| --- | --- |
| Script value | `$%BREATH(who)%`: ticks of breath left (all of it where they can breathe) |
| Zapper mask | `+BREATH <2` passes those with fewer than 2 ticks left; `-BREATH <2` passes everyone else; also `<=`, `>`, `>=`, `=`, `!=` |

References: [BREATH](28-mobprog-functions.md#breath). The mask makes a good
`WHEN` or `PROTECT` in a [Prop_Hazard](#hazards).

Source: [Breath](../../CoffeeMud/com/planet_ink/coffee_mud/core/Breath.java),
[Prop_AirSupply](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_AirSupply.java).

## Hazards

Audience: creators, scripters.

A **hazard** is a place that hurts those in it. CoffeeSUD gives you one
typed, tunable property for it, [Prop_Hazard](23-properties-reference.md#prop_hazard),
and a command that lists every hazard in an area.

### Prop_Hazard

On a room, `Prop_Hazard` hurts everyone in the room. On an area, it hurts
everyone in any room of the area (and its children) that fits the filter. It
ticks every game tick for as long as it is attached.

| Parameter | Meaning |
| --- | --- |
| `TYPE=` | `COLD`, `HEAT`, `ACID`, `GAS`, `POISON`, `ELECTRIC`, `HOLY`, `UNHOLY`, `DISEASE` or `THINAIR` (default `COLD`). The damage is dealt as that type, so the type's saving throw and resistances (spells, `Prop_Resistance`, race) count. |
| `DAMAGE=` | Dice (`1d6`, `2d4+2`) or a formula where `@x1` is the victim's level (`@x1/2`, `3+@x1`). |
| `SAVE=` | The saving throw that halves it (default: the type's own: cold, fire, acid, gas, poison, electric, justice, undead, disease; `THINAIR` uses gas). Give a type name or any save stat name. The chance is the victim's save value as a percent. |
| `DRAIN=MOVE` or `DRAIN=MANA` | Takes DAMAGE from movement or mana instead of hit points (the default for `THINAIR`; damage defaults to 1d6 if only DRAIN is given). `DRAINMSG="..."` is what they are told. |
| `AFFECT="[ID] [pct]%"` | An effect to put on the victim that percent of the time (100 if omitted) unless they already have it: `AFFECT="Disease_FrostBite 20%"`. |
| `ROOMS="mask"` | On an area: a mask run on each victim that picks which rooms count, e.g. `"-DOMAIN +MOUNTAINS"`. |
| `WHEN="mask"` | A mask checked on each victim every tick, e.g. `"-WEATHER +BLIZZARD +SNOW"` or `"-SEASON +WINTER"`. |
| `PROTECT="mask"` | Victims who fit this mask are spared, e.g. `"-EFFECTS +Spell_ResistCold"` or `"-NAMES +Fred"`. |
| `EVERY=[ticks]` | Hurt once in this many ticks (default 1). |
| `ONSET=[ticks]` | Ticks of exposure before it starts. |
| `ESCALATE=[pct]` | Each time in a row, the damage grows by this percent of the base (a 20 means 1.2x, 1.4x, 1.6x...). Leaving and coming back starts again. |
| `WARN="text"` | Said once, as they start being exposed. |
| `DARKONLY`, `LITONLY` | Only when the room is dark to the victim, or when it is not. |
| `NONPC` | Players only; NPCs are spared. |
| `NAME="text"` | What hurts (default by type: "the biting cold", "the searing heat", "the acid", "the foul gas", "the poison", "the lightning", "the holy light", "the unholy taint", "the sickness", "the thin air"). |
| `MSG="text"` | The damage line (default `<S-NAME> <DAMAGES> <T-NAME>!`). |

```text
Prop_Hazard TYPE=COLD DAMAGE=1d6 SAVE=COLD WHEN="-WEATHER +BLIZZARD +SNOW" PROTECT="-EFFECTS +Spell_ResistCold" ESCALATE=20 WARN="The wind bites through your clothes."
Prop_Hazard TYPE=HEAT DAMAGE=@x1/2 ROOMS="-DOMAIN +DESERT" WARN="The sand burns through your boots."
Prop_Hazard TYPE=THINAIR DAMAGE=10 EVERY=2
Prop_Hazard TYPE=GAS DAMAGE=2d4 DARKONLY NONPC
```

Notes:

- Immortal characters (those with the `IMMORT` flag) are never hurt.
- A victim's time in the hazard resets when they leave for a tick; `ONSET`
  and `ESCALATE` count again on return.
- `WHEN` and `ROOMS` and `PROTECT` are masks on *the victim*, so they can
  test race, level, names, effects, weather, season, `ROOMDARK` and `BREATH`.
- Test a hazard with the weakest character you expect to meet it, and with
  `ESCALATE` on.
- For a hazard that depends on a script variable, put the test in a
  `RoomStates` and attach the hazard in that state's `ADD`.

### Other hazards

| Tool | Does |
| --- | --- |
| [Prop_InstantDeath](23-properties-reference.md#prop_instantdeath) | Kills (or damages) anyone who enters the room or area, attacks the mob or uses the item; `MASK=`, `DAMAGE=`, `DMGPCT=`, `MSG=`. Untyped, and only on entry. |
| `Prop_NarrowLedge` | A fall check on entering a room or exit (`check=`, `name=`, `room=`). |
| `FieryRoom` behavior | A room that burns, burning equipment. |
| Traps (`Trap_*`) | Sprung once; see [25-abilities-and-effects.md](25-abilities-and-effects.md). |
| `Prop_ClosedSeason` | Closes or darkens by weather and season. |
| Weather itself | With `WeatherAffects`. |

Exposure and body-temperature simulation (clothing, shelter, warmth) is
**not implemented** (deferred); `Prop_Hazard` with `WHEN`/`PROTECT` is the
supported way to make cold and heat matter.

### HAZARDS

```text
HAZARDS
HAZARDS Frostfang Pass
```

For builders (builder mode, a `DEV=1` game, or `CMDAREAS`/`CMDROOMS`).
Lists what an area holds that acts on those in it: the area's own behaviors
and effects (spawners such as `RandomMonsters`, weather), and in its rooms,
exits and items their behaviors, traps, `Prop_InstantDeath`,
`Prop_NarrowLedge` and `Prop_Weather`, each with its state (as `STAT BEHAVIORS`
gives it) or parameters. **Gap:** on rooms, exits and items it lists only
those traps and the three properties named; `Prop_Hazard`, `Prop_RoomDark`,
`Prop_DarkDanger` and other effects on a *room* are not listed (only effects
on the area itself are all listed). Check rooms with the effects editor or
`STAT`. Use `HAZARDS` before shipping to find forgotten spawners and traps.

Source: [Prop_Hazard](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Hazard.java),
[EnvToolsCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/EnvToolsCmd.java).

## Whole-area resets

Audience: creators, scripters.

The normal respawn is per mob and item (`Prop_RejuvWhen` and the rejuvenation
rates). **ResetWhole** instead rebuilds a room or area wholesale, from the
world, once no player has been there for a while: all mobs and items come
back as the world file has them. Details are in
[24-behaviors-and-masks.md](24-behaviors-and-masks.md#resetwhole) and
[21-mobs.md](21-mobs.md); the CoffeeSUD simulation options are:

| Parameter | Meaning |
| --- | --- |
| a bare number, or `TICKS=[n]` | Reset after n ticks with no player in the room or area. |
| `DAYS=[n]` | Reset after n game days (on the area's calendar) with no player there, in place of `TICKS`. |
| `MUDHOUR=[hour]` | Reset at that game hour of the day. |
| `RLHOUR=[hour]` | Reset at a real-life hour. Real-clock only; meaningless with pause and turn-based time, so use `MUDHOUR` or `DAYS`. |
| `EXCEPT="mask"` | Mobs and items that never come back once gone (a slain boss, taken loot): a mask on them; names with spaces go in single quotes, e.g. `EXCEPT="-NAME +'the Goblin King'"`. Those still there at the reset stay. |
| `MSG="text"` (or `RESETMSG=`) | Said to the first player who walks in after a reset. |
| `ONREENTRY=true` | Reset when the party returns after everyone has left (rather than while they are away). |
| `MINAWAY=[game hours]` | With `ONREENTRY`, the least time away that counts. |
| `SKIPROOMS="room mask"` | Rooms to leave unchanged, e.g. `SKIPROOMS="-LOCATION '+ROOMSTR(Sanctum)'"`. |

```text
ResetWhole DAYS=7 EXCEPT="-NAME +'the Goblin King'" MSG="Fresh tracks lead into the tunnels."
ResetWhole ONREENTRY=true MINAWAY=48 SKIPROOMS="-LOCATION '+ROOMSTR(Sanctum)'"
```

An area's `ResetWhole` skips rooms that carry a `ResetWhole` of their own, so
a boss lair can keep a different timetable. A camped room (a player sitting
in it where camping is unwelcome) is not reset. `ResetWhole` does not stop the
ordinary per-mob rejuvenation; to use it alone, set the rejuvenation rate on
the mobs and items to 0. Last-visit times and absence survive saves. For
`InstanceArea`'s `INPLACE=true` reset see
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md); unique items
and once-only loot are in [22-items.md](22-items.md).

## Scheduled tasks on the game clock

There are four ways to make something happen at a game time. They all run on
the game clock, so they respect pause, turn-based time and `TIMESCALE`.

| Tool | Best for | Notes |
| --- | --- | --- |
| `TIME_PROG`, `DAY_PROG`, `DELAY_PROG` on a mob, item or room | Things tied to an object: the baker opens at dawn | `TIME_PROG [hours]` takes hour numbers, `DAWN`, `DAY`, `DUSK`, `NIGHT`, ranges `6-18`, and steps `6-18/3`. `DAY_PROG` takes days of the month and ranges. `DELAY_PROG low high` a random number of ticks. |
| `Schedule` behavior | An NPC's daily routine | See [06-npcs-dialogue.md](06-npcs-dialogue.md). |
| Quest `SET SCHEDULE`, holidays | A story window, a festival | [Quest schedules](#quest-schedules) and [Holidays](#holidays) above. |
| **Cron jobs** | World-wide maintenance: restock a market weekly, run a script every game day | Below. |
| JavaScript `cm.every`, `cm.at`, `cm.after`, `cm.waitUntil` | Mod code | Below, and [05-javascript.md](05-javascript.md). |

### Cron jobs

A cron job is a list of commands or a script run again and again at an
interval on the game clock. The CoffeeSUD cron runs on game time (not while
the game is paused, and in turn-based time as game time passes), is checked
once a game minute, and keeps its next run in the saved game.

```text
CREATE CRON weekly-restock 7 days
MODIFY CRON weekly-restock SCRIPT
MODIFY CRON weekly-restock INTERVAL 3 days
LIST CRON
LIST CRON 1
DESTROY CRON weekly-restock
```

- `CREATE CRON [NAME] [INTERVAL]`: the interval is a time such as `10 minutes`,
  `6 hours`, `1 day`, a formula, or a boundary word (`hourly`, `daily`,
  `monthly`); the units are game time on the creator's clock.
- `MODIFY CRON [NAME/#] SCRIPT` opens a message editor for the job's commands.
  A line is a command run as the job's creator; a block between `<MOBPROG>`
  and `</MOBPROG>` is a MOBPROG script, and one between `<SCRIPT>` and
  `</SCRIPT>` is JavaScript (CoffeeSUD).
- `INTERVAL` changes how often; `LIST CRON [n]` shows the job and the time to
  its next run in game time. (`MODIFY CRON ... SUBJECT` is documented but
  sets the name to the word SUBJECT; to rename a job, destroy it and create
  it again.)
- All of these need the `CMDCRON` security flag (`LIST CRON` also
  `LISTADMIN`). The jobs themselves live in the world's files
  (`world/journals`), written in builder mode.

Cron is an archon-level tool. It is checked and run as the character who
created it; if that character cannot be found the job logs an error and does
nothing.
For anything tied to an object prefer a `TIME_PROG`; for one-shots a quest
or `cm.after`.

### JavaScript timers

In JavaScript (see [05-javascript.md](05-javascript.md)):

```js
cm.every("1 day", function () { /* each game day */ }, "daily-tax");
cm.at("hour 6", function () { /* at dawn */ }, "dawn-bell");
cm.at("season winter", function () { /* ... */ });
cm.after(10, function (data) { /* 10 ticks on, kept in the save */ }, { id: 3 });
cm.waitUntil(function () { return cm.weather().get() == "rain"; }, function () { /* then */ });
cm.stopTimer("daily-tax");
```

`every` and `at` are registered at boot (in `init.js`'s `onBoot`) and are not
saved; `after` is saved with the game and, after a restore, the function is
rebuilt from its text, so it must use its `data` argument, not captured
variables. `cm.timers()` lists them. Specs are ticks (`6`), `"6 hours"`,
`"1 day"`; `at` takes `"hour 6"`, `"day 1"`, `"month 3"`, `"season winter"`,
or several (`"day 1 hour 8"`).

## Reading time and weather from scripts

A summary of the hooks; each links to its full entry.

**Triggers** ([26-mobprog-triggers.md](26-mobprog-triggers.md)):

| Trigger | Fires |
| --- | --- |
| `TIME_PROG [hours]` | At listed hours (numbers, `DAWN`/`DAY`/`DUSK`/`NIGHT`, ranges, steps). |
| `DAY_PROG [days]` | On listed days of the month. |
| `DELAY_PROG low high` | Every low to high ticks. |
| `WEATHER_PROG [WEATHER/ANY] (OLD)` | When the area's weather changes. `$g` new, `$0` old, `$1` forecast. |
| `MOON_PROG [PHASE/ANY]` | When the moon becomes the phase. `$g` new phase, `$0` old. |
| `EXECMSG_PROG` with `WEATHERCHANGE`, `TODCHANGE`, `DATECHANGE` | A change of weather, of the time of day, a new day. |
| `GAME_PROG [NEWGAME/RESTORE/SAVE/PAUSE/RESUME/TIMEMODE/...]` | Game-level events. |

**Conditions and values** ([28-mobprog-functions.md](28-mobprog-functions.md)):
`ISTIME`, `ISHOUR`, `ISDAY`, `ISMONTH`, `ISYEAR`, `ISSEASON`, `ISWEATHER`,
`ISMOON`, `ISWEEKDAY`, `WEEKDAY`, `DATETIME`, `GAMETIME`, `ISHOLIDAY`,
`HOLIDAY`, `ISTIDE`, `ISDARK`, `ISLIT`, `ROOMSTATE`, `FLOODLEVEL`, `BREATH`,
`ISTURNBASED`, `ISPAUSED`.

**Commands** ([27-mobprog-commands.md](27-mobprog-commands.md)):
`MPWEATHER`, `MPROOMSTATE`, `MPPASSTIME`, `MPINTERRUPT`, and for pinning
weather `MPAFFECT Prop_Weather`.

**JavaScript `cm`**: `cm.clock(area)` (`hour()`, `day()`, `month()`, `year()`,
`daypart()`, `season()`, `moon()`, `absHours()`, `hoursInDay()`, `daysInMonth()`,
`monthsInYear()`, `text()`, `pass(hours)`), `cm.calendar()` (the engine's
`TimeClock`), `cm.weather(area)`, `cm.holiday(name)`, `cm.holidays()`,
`cm.isTurnBased()`, `cm.passTime`, `cm.passHours`, `cm.advance`, `cm.interrupt`.
`daypart()` returns `dawn`, `day`, `dusk` or `night`; `season()` returns the
built-in season a named season counts as.

## Reference

### Settings (`world.ini` / `coffeemud.ini`)

World keys can be set in the world's `world.ini` as well as `coffeemud.ini`
(`world.ini` is read after the ini and the command line wins). See
[world-settings.md](world-settings.md) and
[19-configuration-and-administration.md](19-configuration-and-administration.md).

| Key | World key? | Meaning |
| --- | --- | --- |
| `TIMEMODE` | yes | `REALTIME` (default) or `TURNS` at start. |
| `MILLISPERMUDHOUR` | yes | Game ms per game hour (600000). |
| `HOURSINDAY`, `DAYSINMONTH`, `MONTHSINYEAR`, `DAYSINWEEK`, `DAWNHR`, `DAYHR`, `DUSKHR`, `NIGHTHR`, `YEARDESC` | yes | The global calendar. |
| `STARTDATE` | yes | `Y/M/D/H` a new game starts at. |
| `LONGWAIT` | yes | `ALL` (default) or `BUILDERS`: who may `WAIT` hours/days/`UNTIL`. |
| `BREATHTICKS` | yes | Breath formula (`@x1/3`); `0` for none. |
| `CATCHUP` | yes | `OFF` keeps an area that wakes as it was. |
| `DIALOGUETIME` | yes | `INSTANT`, `TICKS` or blank. |
| `DISABLE` | yes | Flags `WEATHER`, `WEATHERCHANGES`, `WEATHERNOTIFIES`, `TODNOTIFIES`, `DARKNIGHTS`, `DARKWEATHER`, among others. |
| `TIMESCALE` | no (`coffeemud.ini` only) | Clock rate, 0.1 to 20 (1). |
| `AUTOWEATHERPARMS` | no | `WeatherAffects` parameters for all areas, or `NO`. |
| `AUTOAREAPROPS` | no | Properties/behaviors added to every area, e.g. a world-wide `Prop_SkyText`. |

### Commands

| Command | Who | Does |
| --- | --- | --- |
| `TIME`, `DATE`, `TIME GLOBAL` | everyone | Local or world date and time. |
| `TIME SET [hour\|DAWN\|DAY\|DUSK\|NIGHT]` | builders (`CMDAREAS`, builder mode, `DEV=1`) | Run the area's clock forward to that hour. |
| `TIME SEASON [season]` | builders | Run the clock forward a month at a time to that season. |
| `TICKTOCK [n] ([period])`, `TICKTOCK SET Y/M/D/H` | `TICKTOCK` flag | Move the calendar; running its events but not the world's ticks. |
| `WAIT`, `WAIT n`, `WAIT n HOURS\|DAYS\|WEEKS`, `WAIT UNTIL ...` | everyone (`LONGWAIT`) | Let game time pass. |
| `TIMEMODE [REALTIME\|TURNS]`, `TIMEMODE DIALOGUE ...` | everyone | Show or change the time mode. |
| `PAUSE`, `RESUME`, `UNPAUSE` | everyone | Stop and start the clock. |
| `TIMESCALE [rate]` | builder mode, `DEV=1` | Clock rate. |
| `CALENDAR ...`, `CALENDAR HOLIDAYS` | everyone | The calendar of events; game-dated holidays. |
| `MODIFY AREA CALENDAR ...` | builders | The area's calendar. |
| `MODIFY AREA WEATHERPROFILE [name\|NONE]` | builders | The area's climate file. |
| `MODIFY AREA\|ROOM CLIMATE ...`, `ATMOSPHERE ...` | builders | Climate bits and atmosphere. |
| `WEATHER`, `WEATHER WORLD` | everyone | Current weather. |
| `WEATHER SET\|FORECAST\|NEXT` | builders | Change, inspect, step the weather. |
| `LOOK AS [conditions]` | builders | Preview a room's text. |
| `HAZARDS ([area])` | builders | List hazards and spawners. |
| `LIST\|CREATE\|MODIFY\|DESTROY HOLIDAY` | `CMDQUESTS` | Holidays. |
| `LIST\|CREATE\|MODIFY\|DESTROY CRON` | `CMDCRON` | Cron jobs. |
| `WORLD`, `WORLD SET KEY=VALUE` | builders | Show and edit `world.ini`. |

### Properties and behaviors

| ID | On | Section |
| --- | --- | --- |
| [Prop_TimeMode](23-properties-reference.md#prop_timemode) | room, area | [Prop_TimeMode](#prop_timemode-zones-locked-to-one-mode) |
| [Prop_InnRoom](23-properties-reference.md#prop_innroom) | room | [Resting at an inn](#resting-at-an-inn) |
| [Prop_Weather](23-properties-reference.md#prop_weather) | area | [Pinning and linking weather](#pinning-and-linking-weather) |
| [LinkedWeather](24-behaviors-and-masks.md#linkedweather) | area | same |
| [WeatherAffects](24-behaviors-and-masks.md#weatheraffects) | room, area | [How weather works](#how-weather-works) |
| [Prop_SkyText](23-properties-reference.md#prop_skytext) | room, area | [Sky and time-of-day text](#sky-and-time-of-day-text) |
| [Prop_ClosedSeason](23-properties-reference.md#prop_closedseason) | item, mob, room, exit | [Seasonal content](#seasonal-content) |
| [Prop_ClosedDayNight](23-properties-reference.md#prop_closeddaynight) | item, mob, room, exit | same |
| [Prop_TidalExit](23-properties-reference.md#prop_tidalexit) | exit | [Tides](#tides) |
| [TidalRoom](24-behaviors-and-masks.md#tidalroom) | room | [Tides](#tides) |
| [RoomStates](24-behaviors-and-masks.md#roomstates) | room, area | [Room states](#room-states) |
| [Floodable](24-behaviors-and-masks.md#floodable) | room | [Flooding](#flooding) |
| [Prop_RoomDark](23-properties-reference.md#prop_roomdark) | room, area | [Darkness](#darkness) |
| [Prop_DarkDanger](23-properties-reference.md#prop_darkdanger) | room, area | [Darkness](#darkness) |
| [Prop_DarkDescription](23-properties-reference.md#prop_darkdescription) | room | [Darkness](#darkness) |
| [Prop_AirSupply](23-properties-reference.md#prop_airsupply) | item | [Breath](#breath-and-underwater-rooms) |
| [Prop_Hazard](23-properties-reference.md#prop_hazard) | room, area | [Hazards](#hazards) |
| [Prop_InstantDeath](23-properties-reference.md#prop_instantdeath) | mob, item, room, area | [Other hazards](#other-hazards) |
| [ResetWhole](24-behaviors-and-masks.md#resetwhole) | room, area | [Whole-area resets](#whole-area-resets) |

### Zapper mask keys

Masks are the condition language of the engine (reference in
[24-behaviors-and-masks.md](24-behaviors-and-masks.md)). The keys that read the
simulated world, and what a mask passes. Keys differ in which sign names the
condition, so test a new mask with the game's mask description or a
`Prop_Hazard` on a spare room before you rely on it.

| Key | Passes |
| --- | --- |
| `-WEATHER +RAIN +THUNDERSTORM` | While the weather *in the character's room* is one of these. Indoor rooms always read `CLEAR`. |
| `-SEASON +WINTER` | In that season (a calendar's own season name too: `-SEASON "+the Burn"`). `+SEASON -WINTER` passes outside it. |
| `-HOUR`, `-MONTH`, `-DAY`, `-WEEK`, `-YEAR` | By the clock; `+X -N` forms select only that value (see `AHELP ZAPPERMASK`). |
| `+HOLIDAY` / `+HOLIDAY name` | While any holiday / that holiday is on. `-HOLIDAY` the opposite. |
| `-TIDE +HIGH` | At high tide. `+TIDE -HIGH` at any other time. `LOW`, `NONE`, `SPRING`, `NEAP`, `NORMAL`, `SPRING_HIGH`..., and `HIGH:2` for a span. |
| `+ROOMDARK` | The room is dark to them. `-ROOMDARK` everyone else. |
| `+BREATH <2` | Fewer than 2 ticks of breath. `-BREATH <2` everyone else. `<`, `<=`, `>`, `>=`, `=`, `!=`. |
| `-DOMAIN +MOUNTAINS` | In that room domain (used by `Prop_Hazard ROOMS=`). |

### Files

| File | Holds |
| --- | --- |
| `world.ini` (world folder) | The world keys above. |
| `world/areas/*.cmare` | Areas, with the `CALENDAR` and `WEATHERPROFILE` flags, climate/atmosphere bits and every property and behavior in this chapter. |
| `world/climates/NAME.ini` | A named climate (else `resources/climates/NAME.ini`). |
| `world/quests/holidays.quest` | One line, `LOAD=quests/holidays/holidays.quest`. |
| `resources/quests/holidays/holidays.quest` (or the world's copy in `world/resources/quests/holidays/`) | The holidays. |
| `resources/lists.ini` | The global time-of-day and weather lines (`TOD_CHANGE_OUTSIDE`, `TOD_CHANGE_INSIDE`, `WEATHER_*`). |
| `world/journals` | Cron jobs, written in builder mode. |

## Worked examples

### Example 1: a coastal town with tides and storms

Saltmarsh Quay has a causeway to St. Brine's Isle that is open only at low
tide, a shore cove the sea fills, a harbour stair that floods in storms, and
a harbourmaster who shouts when a thunderstorm comes.

**1. A calendar with room in the day for a tide.** The stock six-hour day gives
one game hour of each tide. Give the area twelve hours and a span:

```text
MODIFY AREA CALENDAR HOURS=12 DAYS=30 MONTHS="Brine,Gale,Calm,Ebb" DAWNHR=0 DAYHR=2 DUSKHR=9 NIGHTHR=11 START=1/1/1/0
MODIFY AREA CLIMATE RW
MODIFY AREA WEATHERPROFILE saltmarsh
```

Low tide falls in hour 2 (`DAYHR`), high tide in hour 9 (`DUSKHR`). `RW` is
wet and windy.

**2. A climate file** `world/climates/saltmarsh.ini`, edited in a text editor
(start the game again, or reload, to read it):

```ini
# Saltmarsh Quay: wet, with summer storms
SUMMER=RAIN 30, THUNDERSTORM 25, CLOUDY 20, WINDY 15, CLEAR 10
ALL=CLOUDY 30, RAIN 25, FOG 20, CLEAR 15, WINDY 10
TRANSITION=CLEAR->THUNDERSTORM=-5
HOLD=2
TEXT_THUNDERSTORM=Black clouds pile over the harbour and the sea heaves.|Thunder rolls across the bay.
STOP_THUNDERSTORM=The storm blows itself out over the sea.
```

**3. The causeway** is an exit, open only around low tide (`SPAN=2` makes it
hours 0 to 4):

```text
Prop_TidalExit OPEN=LOW SPAN=2 MSG="The causeway is under the grey water; you must wait for the ebb."
```

Add it to the exit with `MODIFY EXIT` (the effects entry of the exit
editor) on both ends.

**4. The cove** is a shore room the tide floods, washing away small things:

```text
TidalRoom SPAN=2 DESC="The sea fills the cove to the cliff foot, and weed swirls at your waist." FLOODMSG="Cold sea water surges round you." WASH=2
```

Give its normal description the other tide: `Quiet pools lie among the rocks.`
The `ROOMSTATE(HERE)` of the cove reads `ebb` or `flooded`. A `<LOWTIDE>` or
`<HIGHTIDE>` block in the harbour's `<VARIES>` text shows the tide in the
description (see [11-presentation-language.md](11-presentation-language.md)).

**5. The harbour stair** floods when the tide is high or a storm blows:

```text
Floodable RISE="-WEATHER +THUNDERSTORM +RAIN" TIDE=HIGH RATE=10 DRAIN=20 FLOWTO=west DESC2="Grey water slops waist-deep up the harbour stair." MSG3="The stair is under water!"
```

The stair must be an outdoor room: the weather mask reads `CLEAR` in a room
with no sky.

**6. The warning.** A Scriptable behavior on the harbourmaster:

```text
WEATHER_PROG THUNDERSTORM
 mpecho The harbourmaster bellows: "Storm coming in! Lash down the boats!"
~
```

**7. Test it** in builder mode:

```text
WEATHER FORECAST
WEATHER SET THUNDERSTORM
TIME SET 2
TIME SET 9
WAIT 3 HOURS
```

`WEATHER SET THUNDERSTORM` fires the `WEATHER_PROG` and starts the stair
rising. `TIME SET 2` and `TIME SET 9` step the clock to low and then high
tide (watch the cove change and the causeway answer; `TIME SET` only goes
forward and wraps to the next day). The stair counts ticks, not hours, so
`WAIT 3 HOURS` is what actually lets the water rise.

### Example 2: a winter-only pass

The Frost Road crosses a frozen lake. It can be crossed only in winter;
in blizzards it hurts the unprepared.

**1. A calendar with eight months**, so that winter is two of them:

```text
MODIFY AREA CALENDAR MONTHS="Frostmoon,Thaw,Greenleaf,Highsun,Harvest,Leaffall,Snowfall,Deepwinter"
```

With eight months each built-in season is two months, in the order WINTER,
SPRING, SUMMER, FALL: Frostmoon and Thaw are winter. (If you would rather winter
were Snowfall to Frostmoon, name the seasons: `SEASONS="Deepcold:WINTER:Snowfall-Thaw"`.)

**2. A gate that is shut except in winter.** `Prop_ClosedSeason` closes a
door; the exit must be a door, such as a `Gate` or `StdClosedDoorway`, and it
locks if it has a lock:

```text
Prop_ClosedSeason SPRING SUMMER FALL
```

**3. The description** follows the season. In the lake rooms:

```text
<VARIES><WINTER>The lake is a sheet of white ice, booming under your boots.</WINTER> A grey lake stretches away.</VARIES>
```

Preview with `LOOK AS WINTER SNOW` and `LOOK AS SUMMER`.

**4. The danger** is on the area:

```text
Prop_Hazard TYPE=COLD DAMAGE=1d6 SAVE=COLD WHEN="-WEATHER +BLIZZARD +SNOW +COLD" PROTECT="-EFFECTS +Spell_ResistCold" ESCALATE=20 WARN="The wind bites through your clothes."
```

Without a cold-resistance effect, the longer they stay in snow or a blizzard
the harder it bites.

**5. An alpine climate** `world/climates/alpine.ini`:

```ini
WINTER=SNOW 35, BLIZZARD 25, COLD 20, CLOUDY 10, CLEAR 10
ALL=CLEAR 40, CLOUDY 30, WINDY 15, FOG 15
HOLD=2
```

`MODIFY AREA WEATHERPROFILE alpine`. Use `HAZARDS` to list what you set,
`TIME SEASON WINTER` to reach winter, `WEATHER SET BLIZZARD FOR 6 HOURS` to
hold a storm while you test the hazard, and `WEATHER FORECAST` to read the odds.

### Example 3: a cave with deep darkness and breath

The Drowned Vault: a flooded temple where torches die, only the Lantern of
Dawn lights the way, and the air runs out.

**1. The flooded hall.** Set the room's atmosphere to water (or use an
underwater locale), and make its darkness absolute:

```text
MODIFY ROOM ATMOSPHERE SALT WATER
Prop_RoomDark ABSOLUTE=TRUE LIGHTMASK="-TAGS +DAWNLANTERN" SNUFF=100 SNUFFMSG="Your @x1 drowns in the black water."
Prop_DarkDanger LOST=10% STUMBLE=5% LURKER=drowned_priest LURK=4% LURKMSG="A drowned priest drifts out of the black water."
```

`drowned_priest` is a mob you saved to the catalog under that name. Give
the Lantern of Dawn (a light item) the tag `DAWNLANTERN` in the item
editor's Tags field. Torches go out as one swims in (`SNUFF=100`); the lantern
works, so the character can see. Without it the hall is dark and `Prop_DarkDanger`
sends them astray.

**2. The breath.** The character is in water, so they spend breath. At
`BREATHTICKS=@x1/3` and constitution 12, they have 4 ticks. Give the vault
a way to last:

```text
Prop_AirSupply TICKS=6
```

On an "air bladder" item found at the temple door. A room beyond the hall with
the normal atmosphere (an air pocket) refills the breath ("You gasp in a
breath of air").

**3. Gentle warnings.** On the air pocket's Scriptable, a script that tells the
player their state:

```text
GREET_PROG 100
 if BREATH($n < 3)
  mpecho $n gasps, lungs burning.
 endif
~
```

**4. Make the cold bite as well** (optional, `DARKONLY` so the lantern's
bearer is spared the cold *and* sees):

```text
Prop_Hazard TYPE=COLD DAMAGE=1d4 DARKONLY WARN="The water numbs you."
```

**Test:** `LOOK AS NIGHT` on the hall; walk in with a torch (it goes out) and
then with the lantern; `WAIT 4` in the water to watch the breath warnings;
`$%BREATH($n)%` in a quick script to read the count.

### Example 4: a festival holiday

Moonfeast in Riverton: two days on every full moon, with lanterns in the
square, happy townsfolk and cheaper food.

**1. Give Riverton moons and weekdays** (weekday names for `WEEKDAY` tests;
the first moon is the one the holiday follows):

```text
MODIFY AREA CALENDAR MOONS="Selune:24" WEEK="Sunday,Moonday,Fireday"
```

(If you set only these keys, the other calendar values come from the
world's.)

**2. The holiday**, appended to the holiday file (put your world's copy at
`world/resources/quests/holidays/holidays.quest`). The first lines are the
settings; the `GIVE` lines change the town while it lasts:

```text
SET NAME moonfeast
SET AREAGROUP "Riverton"
SET MOON FULL
SET DURATION 2 DAYS
QUIET
SET MOBGROUP RESELECT MASK=+INT 3
GIVE BEHAVIOR MUDCHAT +()\;9Happy Moonfeast!\;9The lanterns are lit tonight!\;\;(feast | moonfeast)\;9Come and eat, friend!\;\;
GIVE AFFECT MOOD happy
SET MOBGROUP RESELECT MASK=-JAVACLASS +StdShopKeeper +GenShopKeeper
GIVE STAT PRICEMASKS 0.9 -JAVACLASS +StdFood +GenFood
STEP BREAK
```

`MODIFY HOLIDAY moonfeast` shows the same settings in the numbered editor.

**3. Make the square change.** In the square's description, as a `<VARIES>`
block:

```text
<VARIES><HOLIDAY moonfeast>Paper lanterns hang from every eave, and the smell of roast fish drifts over the stalls.</HOLIDAY> The market square is quiet.</VARIES>
```

**4. Let NPCs act on it.** A baker who sets up a stall only during the feast,
with a `TIME_PROG` on a Scriptable:

```text
TIME_PROG DAWN
 if ISHOLIDAY(moonfeast)
  mpecho The baker hangs a string of lanterns over her stall.
 endif
~
```

**5. Check it.**

```text
CALENDAR HOLIDAYS
```

shows "moonfeast: in N days (moon full)" until it begins, and "on now"
during it. To see it start, let the game days pass with `WAIT 2 DAYS`; a
holiday quest counts its wait in ticks, so a plain `TICKTOCK` that only moves the
calendar is not a reliable way to start it (unverified; test with `WAIT`).

A second quest can ride along: `SET HOLIDAY moonfeast` runs exactly while the
feast does, for example to open a stage in the square.

## Persistence

| What | Where | On a reload or restore |
| --- | --- | --- |
| Global calendar rules (`HOURSINDAY`...) | `world.ini` / `coffeemud.ini` | Read at start. |
| An area's `CALENDAR`, climate and atmosphere bits, `WEATHERPROFILE`, `Prop_*` and behaviors | The area's `.cmare` in the world folder (saved with `SAVE` in builder mode) | Part of every new game. |
| The current date and hour of each clock | Campaign save (`TIMECLOCK` record per clock) | Restored; a clock with no saved date starts at `START` / `STARTDATE`. Dates set by `TICKTOCK SET` are not content. |
| Time mode, pause, `TIMESCALE` | Campaign save; ini default | Restored (the mode; the rate resets to the ini value unless set in play). Treat the rate as a test tool. |
| Current and next weather, a hold (`WEATHER SET ... FOR`) | Campaign save | Restored. A weather put back by a restore fires no `WEATHER_PROG`. |
| `RoomStates`/`Floodable`/`TidalRoom` current state | Campaign save (as a room change) | Restored. |
| `ResetWhole` last visit and absence | Campaign save | Restored. |
| Holidays and quest windows | Quest state in the campaign save | A running holiday resumes; the file is read again. |
| Cron jobs | Definition in `world/journals`; next run in the campaign save | Resume on the game clock. |
| JS `cm.every` / `cm.at` | Not saved | Re-register in `init.js`. |
| JS `cm.after` | Saved | Rebuilt from its text. |
| Climate files | `world/climates/` | Cached; re-read at start. |

Breath is transient (it refills when you reload). The campaign-save details
are in [31-saves-and-party.md](31-saves-and-party.md) and
[state-and-persistence.md](state-and-persistence.md).

## Troubleshooting

- **Nothing seems to happen.** In turn-based time the world moves only when
  you act or `WAIT`. A test that waits for the weather to change needs
  `WAIT 100` or `TIMESCALE 20` in real time.
- **`WAIT 8 HOURS` says only builders may.** `LONGWAIT=BUILDERS`.
- **`WEATHER SET` has no effect.** The area has `Prop_Weather` (pinned), a
  `LinkedWeather`, or the climate has the void bit; or you are not a builder.
  `DISABLE=WEATHER` turns weather off for the whole world.
- **A weather mask or `Floodable RISE=` never matches.** Masks read the weather
  in the character's *room*; indoor rooms always read `CLEAR`. Use an outdoor
  locale.
- **The causeway is hardly ever open.** Without `SPAN` a tide lasts one game
  hour. Give `Prop_TidalExit` and `TidalRoom` a `SPAN`, and a day of more
  than six hours.
- **Seasons are uneven.** With 7 months, fall has four. Name seasons with
  `SEASONS`, or use a multiple of 4 months.
- **`ISSEASON(Burn)` is false.** A named season matches its full name
  (`the Burn`); use quotes in masks (`-SEASON "+the Burn"`).
- **My `Prop_ClosedSeason` exit is still open.** The exit must have a door
  (Gate, StdClosedDoorway...). An open doorway cannot close.
- **A torch still lights a `Prop_RoomDark` room.** Plain `Prop_RoomDark` is
  beaten by any light; add `ABSOLUTE=TRUE`.
- **`Prop_DarkDanger` does nothing.** It acts only on players the room is dark
  to; a lit torch, darkvision or a daylight room ends it.
- **A character drowns at once.** `BREATHTICKS=0`, or a breath of 0 (low
  constitution: `@x1/3` of 2 is 0). Give `Prop_AirSupply` items.
- **The hazard never stops** after the weather clears. `Prop_Hazard` needs a
  `WHEN`; without one it hurts always.
- **`MODIFY AREA CLIMATE D` makes the area windy.** Known bug; use `DRY`.
- **A holiday did not start.** `SET WEEKDAY` needs weekday names; check
  `CALENDAR HOLIDAYS`; make sure the holiday editor did not rewrite a
  hand-written recurrence.
- **`TIME SET` cannot go backwards.** It runs the clock forward and wraps to
  the next day. Use `TICKTOCK -n` to move it back.
- **Climate edits do not show.** The files are cached. `WEATHER FORECAST`
  prints "(no file: the built-in climate)" if the name is wrong.
- **`WORLD CHECK` or the quest checker complains of `SET WEEKDAY`...** The
  quest checker's word list predates the new settings (see the quest
  chapter); play-test instead.

## See also

- [20-areas-rooms-exits.md](20-areas-rooms-exits.md): areas, climate bits, atmosphere, locales.
- [23-properties-reference.md](23-properties-reference.md) and
  [24-behaviors-and-masks.md](24-behaviors-and-masks.md): every property,
  behavior and the mask language.
- [04-mobprog.md](04-mobprog.md),
  [26-mobprog-triggers.md](26-mobprog-triggers.md),
  [27-mobprog-commands.md](27-mobprog-commands.md),
  [28-mobprog-functions.md](28-mobprog-functions.md): scripting the clock and weather.
- [05-javascript.md](05-javascript.md): `cm` timers and world members.
- [06-npcs-dialogue.md](06-npcs-dialogue.md): NPC schedules.
- [08-quests.md](08-quests.md): quest scripts, holidays and schedules.
- [09-society.md](09-society.md): law, fines, money, shops.
- [11-presentation-language.md](11-presentation-language.md): `<VARIES>` text for time and weather.
- [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md): travel time, vehicles and weather speed.
- [31-saves-and-party.md](31-saves-and-party.md): pause, turns, saves.
- [32-engine-programming.md](32-engine-programming.md): ticks and the game thread.
- [world-settings.md](world-settings.md), [state-and-persistence.md](state-and-persistence.md).
