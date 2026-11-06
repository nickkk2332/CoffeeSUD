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

An area class `StdTimeZone` (and `StdPlanet`) also owns a clock of its own,
saved in the save state; in a real game it started from the global calendar,
so use `CALENDAR` to make a time zone's calendar part of the world
definition.

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

- `TIME` shows the local time of day, hour, date, season, the weather in
  words where there is a sky, and (at night) the moon phases of the area.
  `TIME GLOBAL` shows the world clock. `DATE` is the same command.
- `CALENDAR` (the player's calendar) lists events: `CALENDAR NOW`, `SOON`,
  `RECENT`, `NEXT [n] YEAR|MONTH|DAY|WEEK`, `PAST ...`, `ADD`, `REMOVE`. In
  CoffeeSUD `CALENDAR HOLIDAYS` also lists each holiday with a game date,
  "on now" or "in n days".

Scripts see the date through `TIME_PROG`, `DAY_PROG`, `ISHOUR()`, `ISDAY()`,
`ISTIME()`, `ISSEASON()`, `ISMOON()`, `ISHOLIDAY()` and `DATETIME`-style
variables (reference: [28-mobprog-functions.md](28-mobprog-functions.md)),
and JavaScript through `cm.clock(area)` (see
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
clock, so a southern land can be in summer while its parent is in winter
without a clock of its own. It is the area's own (not inherited by `HOURS`
and the like) but child areas inherit it from their nearest parent that has
one. `Prop_ClosedSeason`, weather odds, `ISSEASON`, masks and `VARIES` use
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
`MOON_PROG`, `ISMOON(FULL)`, `+MOON` masks and the moon-altering spells follow
it. The others are read by name: `ISMOON(Ember full)`. At night `TIME` tells
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

**Prop_ClosedSeason** takes one or more of `SUMMER SPRING WINTER FALL DAY NIGHT DUSK DAWN RAIN SLEET SNOW CLEAR HEATWAVE THUNDERSTORM BLIZZARD WINDY DROUGHT DUSTSTORM COLD HAIL CLOUDY`
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
These commands are builder commands (`CMDQUESTS`-class flags). They edit the
holiday file, so a copy that ships with the world should live in the world
folder (below).

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

To change the stock set for one world, put your own file at
`world/resources/quests/holidays/holidays.quest` (the world's copy of a
resource file is read instead of the stock one; see
[19-configuration-and-administration.md](19-configuration-and-administration.md)
and [02-world-workflow.md](02-world-workflow.md)). Use `NAME.add` style files
only for resources that support them.

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
