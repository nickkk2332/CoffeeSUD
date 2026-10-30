# Time, calendar and climate

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b024` (Time, calendar and climate) · 6 files · 4840 lines of Java.

Citations give the file name and line. The Key files table gives each file's folder under `com/planet_ink/coffee_mud/`, and the outside-bucket files named in the text are listed after it, with their folders.

## Summary

Each CoffeeMud area owns a `TimeClock`: a configurable calendar (hours per day, days per month, named months, optional week names, dawn/day/dusk/night hours) plus a current date and hour. The clock advances on the area's own tick, at a rate fixed in real milliseconds per in-game hour (default ten real minutes). Each area also owns a `Climate`, a weighted weather chain over 14 states that depends on season, climate flags and a one-step forecast, and it broadcasts weather and time-of-day messages to the people in the area. Moon phase and tides are derived from the day of the month and the hour, and a few classes read them to adjust combat stats. The global clock, date formatting and parsing, and duration parsing such as "5 mudhours" live in the `CMLib.time()` library. For a single-player Python game, the reusable ideas are a calendar config kept separate from the clock state, a table-driven weather chain with a visible forecast, and elapsed time stored as one integer count of hours.

## Key files and types

| File (folder `com/planet_ink/coffee_mud/`) | Role | Lines |
|---|---|---|
| `Common/interfaces/TimeClock.java` | Interface for one calendar and clock. Also holds the enums `TimePeriod`, `MoonPhase`, `TidePhase`, `TimeOfDay` and `Season`. | 977 |
| `Common/DefaultTimeClock.java` | Default clock. Calendar fields, hour/day/month/year arithmetic, time of day, moon and tide, persistence, and the tick that advances time. | 1378 |
| `Common/interfaces/Climate.java` | Interface for weather state. Holds the `WEATHER_*` constants, `NUM_WEATHER` and `WEATHER_TICK_DOWN`. | 198 |
| `Common/DefaultClimate.java` | Weather model: seasonal and transition tables, the weighted roll, weather messages, weather effects on thirst and movement, sky visibility. | 639 |
| `Libraries/interfaces/TimeManager.java` | Interface for `CMLib.time()`: date and duration string formats, tick-expression parsing, local, home and global clock lookup, `MILI_*` constants. | 479 |
| `Libraries/CoffeeTime.java` | `CMLib.time()` implementation (`CoffeeTime extends StdLibrary`). Owns the global clock, formats and parses dates, and defines `TimeDelta`. | 1169 |

Outside the bucket, read only where the bucket's code calls them: `Areas/StdArea.java` (tick dispatch), `core/CMProps.java` (tick and mud-hour settings, list-file enum), `core/interfaces/Places.java` (climate bitmask), `core/interfaces/Tickable.java` (tick IDs), `Libraries/Dice.java` (`roll`), `resources/lists.ini` (all message text), `coffeemud.ini` (calendar defaults), `Abilities/Druid/Chant_AlterTime.java`, `Commands/TickTock.java`, `CharClasses/Mer.java`, `CharClasses/SkyWatcher.java`, `CharClasses/Scholar.java`, `CharClasses/Artisan.java`.

## How it works

### 1. The calendar and its state

**Data model** (`DefaultTimeClock.java:89-111`). The fields are `year` (default 1000), `month` (default 1), `day` (default 1), `time` (the hour, default 0), `hoursInDay` (6), `season`, and the derived values `weekOfMonth`, `weekOfYear`, `dayOfYear` and `daysInYear`. The code defaults are eight months named "the 1st month" to "the 8th month" (`:102-105`), 20 days per month (`:106`), dawn/day/dusk/night hours `{0,1,4,6}` (`:107`), no week names (`:108`) and year names `{"year #"}` (`:109`).

**Indexing is mixed.** `day` is 1-based (`getMin(DAY)` returns 1, `:1203-1225`). The month and the hour are 0-based: `timeDescription` indexes `getMonthNames()[getMonth()]` (`:339`), `getMin(MONTH)` is 0, and `bumpMonths` wraps to 0 (`:860-882`). The exceptions are `toHoursSinceEpoc` and `setFromHoursSinceEpoc` (section 3 and Problems). A fresh clock starts in month index 1, which is the second name (`:90`, and the default record at `:1107`).

**Derived fields.** `dayOfYear = month*daysInMonth + day` (`:497`). The weekday is `weekNames[x % daysInWeek]` where `x = year*monthsInYear*daysInMonth + month*daysInMonth + day` (`:333-336`). Because `x` counts absolute days, weekdays run on across years. With no week names configured, no weekday is shown (`:254`, `:331`).

**Seasons** (`setMonth`, `:414-429`; `getMonthsInSeason`, `:396-400`). `monthsInSeason = floor(monthsInYear / 4)`. Let `m` be that value. Then month < m is WINTER, < 2m is SPRING, < 3m is SUMMER, and anything else is FALL. The season is recomputed only when `setMonth` runs.

**Time of day** (`getTODCode`, `:514-528`). `dawnToDusk` holds the start hour of DAWN, DAY, DUSK and NIGHT, in that order (`TimeClock.java:300-304`). The code returns the latest period whose start hour has been reached, checking NIGHT first. A start hour of -1 disables that period. Hours before dawn count as NIGHT.

**Defaults that matter** (`coffeemud.ini:1099-1149`): 6 hours per day, 24 days per month, 7 months, dawn 0, day 1, dusk 4, night 5. In practice that gives hour 0 = dawn, hours 1-3 = day, hour 4 = dusk, hour 5 = night. The ini values are read by `initializeINIClock` (`:284-315`). I did not trace its call site, so I cannot confirm that they replace the code defaults for every clock.

**Real-time mapping** (defaults, `coffeemud.ini:1099` and `:2151`; `CMProps.java:681-682`). `MILLISPERMUDHOUR = 600000` and `TICKTIME = 4000`. From these, by arithmetic: one mud hour is 10 real minutes; one mud day (6 hours) is 1 real hour; one mud month (24 days) is 24 real hours; a season is one month (`floor(7/4) = 1`); a mud year (7 months, 168 days) is 7 real days.

### 2. Advancing the clock

**Dispatch.** `StdArea.tick` (`Areas/StdArea.java:1276-1303`) handles `TICKID_AREA` (`:1282`). It calls `getClimateObj().tick` (`:1292`) and then `getTimeObj().tick` (`:1294`). The area registers itself with `startTickDown(this, TICKID_AREA, 1)` (`:559-561`), so it runs once per tick-group period. That period defaults to `TICKTIME` (`ServiceEngine.java:349-351`).

**The tick** (`DefaultTimeClock.tick`, `:1083-1153`):
- It returns early unless the clock is loaded and more than one mud hour has passed since `lastTicked` (`:1087-1089`).
- Otherwise `timeToTick` is set when more than one mud hour has elapsed (`:1094`), and `lastTicked = now` (`:1095`).
- The first call loads the clock record from the database (`:1096-1110`). The default record sets `TIME` to -1 (`:1107`), so the first tick lands on hour 0.
- `if(timeToTick) tickTock(1)` (`:1150-1151`).

Consequences:
- Each mud hour takes the mud-hour length plus up to one tick. At defaults that is about 600 to 604 seconds.
- There is no catch-up. After a stall the clock moves one hour per call.
- Several areas can share one clock, and the GLOBAL clock is the usual example (`CoffeeTime.java:1052-1064`). Each area's tick calls `tick`, but the `lastTicked` guard lets only one advance happen per mud hour.
- A manual shift also restarts the timer. `tickTock` sets `lastTicked` (`:792`), so the next automatic hour is a full mud hour away.

**Shifting time** (`tickTock`, `:787-815`). The logic, paraphrased:

```
hour += n
if hour >= hoursInDay:  bumpDays(+1); extra = hour - hoursInDay; hour = extra % hoursInDay; if extra >= hoursInDay: bumpDays(+extra // hoursInDay)
if hour < 0:            bumpDays(-1); extra = |hour|; hour = hoursInDay - extra % hoursInDay; if extra >= hoursInDay: bumpDays(-extra // hoursInDay)
if moveTheSky and time of day changed: handleTimeChange()
```

The negative branch has a boundary bug, covered under Problems. `bumpHours` calls `tickTock(n, false)` and so does not move the sky (`:823-827`). Callers that move time are the admin `tick N` command, which also saves (`Commands/TickTock.java:69-74`), and the chant `Chant_AlterTime`. That chant calls `tickTock` with its stored integer, or with a random -1 or +1 if the stored value is zero (`Abilities/Druid/Chant_AlterTime.java:100-107`).

**Bumping by period** (`bumpDays` `:829-852`, `bumpMonths` `:860-882`, `bumpYears` `:884-888`, `bump` `:890-916`). A SEASON bump is `bumpMonths(monthsInSeason * n)` (`:904-905`).

### 3. Elapsed time as one number

`toHoursSinceEpoc` (`:918-929`) computes `hour + (day-1)*hoursInDay + (month-1)*hoursPerMonth + year*hoursPerYear`. `setFromHoursSinceEpoc` (`:931-959`) inverts it. The months here are 1-based, which disagrees with the rest of the class (see Problems). Other code uses the number as a timestamp:
- `Abilities/Common/Lecturing.java` (`:250`, `:283`) and `Abilities/Common/Publishing.java` (`:351`, `:596`) store and restore last-event times.
- `Abilities/Common/BookLoaning.java:694` and `MOBS/StdLibrarian.java:472` track loan due times. The librarian skips the check when the number is 0.
- `CharClasses/Scholar.java:449-451` awards class experience once more than one mud day has passed, using the home clock's `getHoursInDay()` as the threshold.
- `CharClasses/Artisan.java:355-357` awards experience on any new mud hour.

`compareTo` (`:1355-1377`) uses a different offset: it counts `day` from 1 and `month` from 0. The two numbers differ by a constant, so ordering is preserved but the values are not interchangeable.

### 4. Moon and tides

**Moon phase** (`getMoonPhase`, `:431-450`). The base index is `floor(day / (daysInMonth + 1) * 8)` (`:434`). Each active moon-altering effect on the room or area adds its `abilityCode()`, and the sum is taken mod 8 (`:441-447`). The result is `MoonPhase.values()[index]` (`:449`).

| Index | Phase (`TimeClock.java`) | Factor | Tide type (high/low) | Description (paraphrase of `:864-872`) |
|---|---|---|---|---|
| 0 | NEW (`:836`) | 1.0 | SPRING | a new moon is in the sky |
| 1 | WAXCRESCENT (`:837`) | 0.5 | NORMAL | waxing crescent |
| 2 | WAXQUARTER (`:838`) | 0.0 | NEAP | first quarter |
| 3 | WAXGIBBOUS (`:839`) | -0.5 | NORMAL | waxing gibbous, almost full |
| 4 | FULL (`:840`) | -1.0 | SPRING | a full moon |
| 5 | WANEGIBBOUS (`:841`) | -0.5 | NORMAL | waning gibbous |
| 6 | WANEQUARTER (`:842`) | 0.0 | NEAP | last quarter |
| 7 | WANECRESCENT (`:843`) | 0.5 | NORMAL | waning crescent |
| 8 | BLUE (`:844`) | 2.0 | SPRING | "BLUE MOON" text at `:872` |

Index 8 is never returned: the base index tops out at 7 because the day runs from 1 to `daysInMonth`, and the `% 8` wrap keeps the sum below 8. With the ini default of 24 days, each of the eight phases covers three days: NEW days 1-3, WAXCRESCENT 4-6, WAXQUARTER 7-9, WAXGIBBOUS 10-12, FULL 13-15, WANEGIBBOUS 16-18, WANEQUARTER 19-21, WANECRESCENT 22-24. I checked this arithmetic with a Python port of the formula.

**Tides** (`getTidePhase`, `:452-484`). At the hour that starts DAY the tide is the moon's low tide; at the hour that starts DUSK it is the moon's high tide; at every other hour it is `NO_TIDE` (`:457-463`). Tide-altering effects (`FLAG_TIDEALTERING`) shift the tide index mod 7 (`:469-480`). Tide factors (`TimeClock.java:900-906`): SPRING_HIGH 1.5, SPRING_LOW -1.5, NORMAL_HIGH 1.0, NORMAL_LOW -1.0, NEAP_HIGH 0.5, NEAP_LOW -0.5, NO_TIDE 0.0.

**Effects on characters.**
- `CharClasses/Mer.java:310` reads the tide factor to choose a message for characters at class level 5 or above.
- `Mer.java:462` and `:464` change the wearer's armor and attack adjustment by `-round(classLevel * factor)`. The code comments say the armor sign is good and the attack sign is bad.
- `CharClasses/SkyWatcher.java:379` and `:381` do the same with the moon factor.

**Moon visibility.** `canSeeTheMoon` (`DefaultClimate.java:228-239`) is true when the stars are visible or a moon-summoning effect applies.

### 5. Weather

**States** (`Climate.java:161-189`): CLEAR 0, CLOUDY 1, WINDY 2, RAIN 3, THUNDERSTORM 4, SNOW 5, HAIL 6, HEAT_WAVE 7, SLEET 8, BLIZZARD 9, DUSTSTORM 10, DROUGHT 11, WINTER_COLD 12, FOG 13. `NUM_WEATHER` is 14.

**Timing.** `WEATHER_TICK_DOWN = 150` area ticks (`Climate.java:191`, whose comment reads "150 = 10 minutes * 60 seconds / 4"). At defaults that is 10 real minutes, one mud hour.

**The step** (`DefaultClimate.weatherTick`, `:355-495`):
1. Base table. The seasonal table for the current season (`:117-124`, indexed by season times 14 plus target) is summed with one table for each climate bit the area has: cold (`:127-133`), hot (`:135-141`), dry (`:143-149`), wet (`:151-157`), windy (`:159-165`). The bits are defined in `core/interfaces/Places.java:91-105`. Tables are summed element-wise (`:369-385`).
2. Transition table. `changeMap[from*14 + target]` (`:167-183`), where `from` is the current forecast (`nextWeather`). A value of -100 blocks that transition.
3. Weight of each target = base + transition. Only positive weights count (`:403-411`).
4. Roll. `CMLib.dice().roll(1, total, -1)` returns a uniform value from 0 to total-1 (`Libraries/Dice.java:398-406`; `DefaultClimate.java:425`). The first target whose running total exceeds the roll wins (`:429-441`). If nothing has a positive weight, the forecast stays the same.
5. Apply. `currentWeather = nextWeather` and `nextWeather = winner` (`:445-449`). Players therefore always see the state that was forecast one step earlier.
6. Messages. `sayMap` (`:457-472`) picks between no message, the weather description, the stop text for the old weather (`WEATHER_ENDS`), or both. The text goes to every non-monster in sky rooms of the area (`:326-341`, `:490-493`), and to boardable ships in the area (`:343-352`). The per-person test is `canSee(mob) || currentWeather != oldWeather` (`:336-339`). At this call the weather has changed, so the second part is always true and everyone non-monster gets the text.

**Worked example** (computed from the tables). Forecast CLEAR in spring with no climate bits. Base row `[40,20,10,14,5,1,0,5,0,0,0,0,5,0]` plus transition row `[85,0,0,-100,-100,-100,-100,0,-100,-100,0,-20,0,-75]` gives positive weights CLEAR 125, CLOUDY 20, WINDY 10, HEAT_WAVE 5, WINTER_COLD 5, total 165. CLEAR therefore stays CLEAR with probability 125/165 (about 76%) per step. In one step a spring clear sky can only move to cloudy, windy, heat wave or winter cold. Rain, thunderstorm, snow, hail, sleet, blizzard, dust, drought and fog are all excluded.

**Forecast and forced weather.** `nextWeatherDescription` is used by `Abilities/Spells/Spell_PredictWeather.java` and `Abilities/Druid/Chant_PredictWeather.java`. `forceWeatherTick` (`:309-314`) sets the ticker to 1 and runs one step. Its callers include `Abilities/Prayers/Prayer_Weather.java`, `Abilities/Druid/Chant_CalmWind.java`, `Chant_SummonHeat.java` and `Chant_SummonRain.java`.

**Describing the weather** (`theWeatherDescription`, `:510-579`). The prefix index is chosen by climate and season: a cold bit or WINTER gives index 2, a hot bit or SUMMER gives 3, wet gives 1, dry gives 4, and anything else gives 0. The suffix is index 5 if the area is windy and index 6 otherwise. Some weathers also add an MSP sound cue (`:544-575`).

**Effects on the player.**
- `adjustWaterConsumption` (`:587-613`): drought x4, dust x3, heat wave x2, rain and thunderstorm halved.
- `adjustMovement` (`:615-632`): thunderstorm x2, hail x2, dust x3, blizzard x4.
- Both return the base value unchanged indoors (`:590`, `:618`). Each has three callers, which I did not trace.

**Sky visibility.**
- `canSeeTheStars` (`:242-263`): night or dusk, the room has sky, and the weather is not cloudy, fog, dust, rain, thunder, snow, sleet, hail or blizzard.
- `canSeeTheSun` (`:266-300`): day or dawn, not dark, the same weather exclusions. Fog blocks the sun only at dawn.

**Indoors and special cases.** `weatherType` returns CLEAR for rooms without sky (`:210-217`). A void climate forces CLEAR and returns early (`:357-362`). The CMSecurity flags `WEATHER`, `WEATHERCHANGES`, `WEATHERNOTIFIES` and `TODNOTIFIES` disable parts of this (`CMSecurity.java:2972-2974`).

### 6. Messages players see

- **Time-of-day, outside** (`resources/lists.ini:666-672`). Dawn offers three lines, chosen at random, two of which say the sun rises in the west. Day is silent. Dusk has one line about the sun setting in the east. Night has three lines.
- **Time-of-day, inside** (`lists.ini:674-680`). Only dawn ("It is now daytime.") and night ("It is nighttime.") produce text.
- **Who gets it** (`handleTimeChange`, `DefaultTimeClock.java:734-785`). Every non-monster inhabitant who is awake, can see and is under sky gets the outside text. Every other non-monster inhabitant gets the inside text (`:762-771`). Rooms with people or items get `recoverPhyStats`, and the rest get `recoverRoomStats` (`:747-749`, `:776-778`).
- **Weather start and end** (`lists.ini:792-806` for ends, `:828-1043` for starts).
- **Time description** (`timeDescription`, `DefaultTimeClock.java:322-381`). It shows the time of day, "(Hour: h/last)", the weekday if any, the date, the season sentence (`:343-351`), and at night the moon line, which weather can replace with a cloud, fog or dust message (`:353-379`).

### 7. Configuration, persistence and editing

- **Global calendar.** `initializeINIClock` (`DefaultTimeClock.java:284-315`) reads `HOURSINDAY`, `DAYSINMONTH`, `MONTHSINYEAR`, `DAYSINWEEK`, `YEARDESC` and the four dawn-to-night hours. It also sets `TICKSPERMUDDAY` and `TICKSPERMUDMONTH` (`:313-314`). Its call site is not traced.
- **Area clocks.** A clock loaded from the database takes any missing calendar fields from the global clock (`:1121-1134`). Otherwise it uses its own (`:1135-1146`).
- **Saving.** `save()` (`:1008-1026`) writes the hour, day, month, year, calendar lengths, month names, dawn-to-night hours, week names and year names to the area's `TIMECLOCK` record, but only after the clock has loaded. Direct callers are `Commands/TickTock.java:74` and `:85`, and `Commands/Modify.java:2893`, `:2908` and `:2923`. I did not find a periodic or shutdown save.
- **Editing.** In the calendar editor (`Libraries/CMGenEditor.java:1212-1254`), choosing "global" gives the area a copy of the global clock and deletes its stored clock record (`:1253-1254`). An area that inherits a parent's clock cannot be edited (`:1212-1222`).
- **Other area types** create their own clocks: `Areas/StdPlanet.java:52`, `Areas/StdTimeZone.java:47`, `:55`, `Areas/StdThinPlanet.java:51`, `Areas/StdSpaceShip.java:51`, `:395`. `StdSpaceShip.java:89` also creates a `DefaultClimate`.

### 8. Date text and duration parsing (`CoffeeTime`)

- **Real-time formats** (Gregorian, via `makeCalendar`). `date2String` gives "M/d/yyyy h:mm AM/PM" (`CoffeeTime.java:720-744`). `date2String24` gives "yyyy/M/d HH:mm" (`:754-775`). `date2BriefString` omits the date when it is today and appends am or pm (`:699-710`).
- **Minutes** are rounded to a multiple of five (`:644-663`). Minutes 55 to 59 all show as 55.
- **Elapsed real time.** `date2EllapsedTime` writes years, months, weeks, days, hours, minutes, seconds, in long or short form (`:785-853`). `date2SmartEllapsedTime` picks the coarsest unit that fits: days above two days, then hours, minutes, seconds (`:881-892`).
- **Elapsed mud time.** `date2EllapsedMudTime` (`:895-952`) converts milliseconds into mud years, months, weeks, days and hours using the clock's own lengths. `date2SmartEllapsedMudTime` (`:955-974`) picks the granularity.
- **Parsing dates.** `string2Date` (`:222-307`) accepts an SQL-style "YYYY-MM-DD HH:MM" and the format "M/d/yyyy h:mm a". With no time it assumes 5:00 PM (`:288-292`). `string2TimeFuture` (`:311-390`) reads "5pm" or "17:00" and returns the next occurrence, adding a day if the time has passed (`:381-382`).
- **Durations** (`parseTickExpression`, `:1081-1129`). Unit words are defined in `TimeDelta` (`:55-77`): real minutes, hours, days, weeks, ticks, and the mud units MUDHOUR, MUDDAY, MUDWEEK, MUDMONTH and MUDYEAR. The expression is evaluated and divided by the tick length (`:1127`). `TimeDelta` users include `Abilities/Thief/Thief_Runecasting.java` and `Abilities/Misc/ScriptLater.java`.
- **Clock lookup** (`localClock` `:1132-1146`, `homeClock` `:1149-1168`). An area uses its own clock, or the global clock if it has none. A room uses its area. An item uses its owner. A mob uses its location (local) or its start room (home).

## Content worth mining

### The default calendar

| Setting | Default | Source |
|---|---|---|
| Real minutes per mud hour | 10 | `coffeemud.ini:1097-1099` |
| Hours per mud day | 6 | `:1101-1104` |
| Days per mud month | 24 | `:1106-1109` |
| Months per year | 7, named "the 1st month" to "the 7th month" | `:1111-1116` |
| Weekday names | none | `:1118-1121` |
| Dawn, day, dusk, night (start hours) | 0, 1, 4, 5 | `:1123-1142` |
| Year name | "year #", cycling; `#` is replaced by the year | `:1144-1149` |

The shipped calendar has no names of its own. A Python game would define its own month and day names as data.

### Weather catalogue

Each weather has seven slots in `lists.ini`: five prefixes (normal, wet, cold, hot, dry), then a windy suffix and a calm suffix. The text below is a short sample from each entry.

| State | Normal prefix (sample) | Windy suffix / calm suffix |
|---|---|---|
| CLEAR | "The weather is clear." (also humid, cool, warm, dry variants) | none |
| CLOUDY | "Fluffy cloudbanks" | "move across the sky." / "obscure the sky." |
| WINDY | "A light wind" | "gusts through here." / "blows through here." |
| RAIN | "A light rain" | "swirls down from the sky." / "falls from the sky." |
| THUNDERSTORM | "A heavy and blusterous rainstorm" | "swirls all around you." / "pours down from above." |
| SNOW | "An unseasonable snow" | "swirls down from the sky." / "falls from the sky." |
| HAIL | "Light streams of hail" | "swirl down from above," / "fall from the sky." |
| HEAT_WAVE | "It is very hot." | none |
| SLEET | "An unseasonable sleet storm" | "swirls down from the sky." / "falls from the sky." |
| BLIZZARD | "A thunderous blizzard" | "swirls all around you." / "pours down from above." |
| DUSTSTORM | "An eye-stinging dust storm" | "swirls all around you." / "blows through the area." |
| DROUGHT | "There are horrible drought conditions." | none |
| WINTER_COLD | "It is cold." | none |
| FOG | "A soupy fog" | "fills the air." / "descends." |

Each weather also has an "ends" line (`lists.ini:792-806`), for example "The clouds dissipate." for cloudy and "The fog lifts." for fog.

### Seasonal base weights

Positive numbers add to a weather's chance in that season. This is the first table of the roll, before the transition modifiers and climate bits (`DefaultClimate.java:117-124`).

| State | Spring | Summer | Fall | Winter |
|---|---|---|---|---|
| CLEAR | 40 | 31 | 37 | 32 |
| CLOUDY | 20 | 20 | 10 | 15 |
| WINDY | 10 | 5 | 15 | 11 |
| RAIN | 14 | 10 | 15 | 4 |
| THUNDERSTORM | 5 | 12 | 10 | 2 |
| SNOW | 1 | 0 | 5 | 7 |
| HAIL | 0 | 0 | 2 | 3 |
| HEAT_WAVE | 5 | 20 | 5 | 0 |
| SLEET | 0 | 0 | 2 | 3 |
| BLIZZARD | 0 | 0 | 1 | 3 |
| DUSTSTORM | 0 | 1 | 0 | 0 |
| DROUGHT | 0 | 1 | 0 | 0 |
| WINTER_COLD | 5 | 0 | 10 | 20 |
| FOG | 0 | 0 | 0 | 0 |

The cold, hot, dry, wet and windy climate bits each add their own table to these numbers (`:127-165`). Lines are added element-wise, so an area's climate is a sum of data tables.

### Moon, tide and time-of-day text

- Moon descriptions (`TimeClock.java:864-872`): "There is a new moon in the sky.", "The moon is in its first quarter.", "There is a full moon in the sky.", and so on. The BLUE entry reads "There is a BLUE MOON! Oh my GOD! Run away!!!!!" but is unreachable (see Problems).
- Tide descriptions (`TimeClock.java:922-927`): "The tide is especially high.", "The tide is weak, but low.", "The tide is normal.", and so on.
- Time-of-day names (`TimeClock.java:946-949`): "It is dawn", "It is daytime", "It is dusk", "It is nighttime".
- Seasons (`DefaultTimeClock.java:345-348`): "It is fall.", "It is spring.", "It is summer.", "It is winter."

### Time-travel and calendar-linked effects

- `Chant_AlterTime` (`Abilities/Druid/Chant_AlterTime.java:96-107`). Casting message: "reality seems to start blurring". Result messages: "Time moves forwards!" or "Time moves backwards!". The shift is one hour, or a stored value.
- Class effects keyed to tides and moon phases (`Mer.java:462-464`, `SkyWatcher.java:379-381`).

## Notable design ideas

- **Calendar as configuration.** The whole calendar is a handful of numbers and lists (hours per day, days per month, month names, four hour thresholds, optional week and year names). Builders change the world's time system without code.
- **Weather as a weighted chain.** Each step adds a seasonal base to a transition row, blocks some transitions with -100, and rolls over the positive weights. One table encodes both climate and persistence. Summing tables gives climate bits and seasons a shared, composable form.
- **A forecast one step ahead.** The chain keeps `nextWeather` as well as `currentWeather`, so a forecast always exists. This is what the weather-prediction spells read (`nextWeatherDescription`).
- **Modular moon phase.** The phase is `floor(day/(days+1) * 8)`, so a lunar cycle fits exactly into a month. Moon-altering magic moves the phase by adding an offset mod 8. Magic therefore changes the sky through a single index.
- **Tides at hour boundaries.** The low tide comes at the start of day and the high tide at dusk, scaled by the moon. The factors feed class stats directly (Mer, SkyWatcher), so the sky changes how characters fight.
- **Text by composition.** Weather text is a prefix (climate, season) plus a suffix (wind). Fourteen weathers times seven slots covers every climate and season without a separate sentence for each combination.
- **Inside and outside messages.** Time-of-day text has an outside and an inside version, and an empty entry means silence. Dawn and night speak; day and dusk mostly stay quiet indoors.
- **One number for elapsed time.** `toHoursSinceEpoc` is the timestamp for loan due dates, publication times, lecture times and "once per day" experience (Scholar, Artisan).
- **Time as an object that spells can touch.** Admin commands, the time chant and weather chants all call the same clock and climate methods, so every time effect goes through one place.

## Problems and cruft

1. **Negative shifts that are exact multiples of the day length leave an un-normalized hour.** In the negative branch (`DefaultTimeClock.java:803-811`), a shift of -6 from hour 0 gives `hour = hoursInDay` (6 on a 6-hour day) and one extra day back. The absolute time is right, since `toHoursSinceEpoc` comes out the same, but the displayed hour reads "6/5" and `getTODCode` reports NIGHT where DAWN is correct. `isEqual` (`:692-704`) compares fields, so the same instant can compare unequal. Reachable with the admin `tick -6` command. I traced this by hand and confirmed it with a Python port of the branch.
2. **Epoch conversion uses 1-based months and an off-by-one loop.** `setFromHoursSinceEpoc` (`DefaultTimeClock.java:938-958`) starts `month` at 1 and uses strict `>` tests. Any time in the final month leaves `month == monthsInYear`, which is out of range for `getMonthNames()` and would throw in `timeDescription` (`:339`). An exact multiple leaves the hour at `hoursInDay`. `setFromHoursSinceEpoc(6)` gives the raw fields year 0, month 1, day 1, hour 6. My Python port gave `(month 7, day 23, hour 4)` for the last month of a 7-month year, and `(month 7, day 24, hour 6)` for an exact year. `toHoursSinceEpoc` (`:919-928`) subtracts 1 from month and day, so the pair is self-consistent, but it disagrees with the 0-based months used everywhere else. The disagreement is visible in the default start month (index 1) and in `compareTo`.
3. **The BLUE moon cannot occur.** The index is `% 8` (`DefaultTimeClock.java:446-449`) and the base never exceeds 7 (`:434`). `MoonPhase.BLUE` (`TimeClock.java:844`, factor 2.0) is therefore dead, and so is its message (`:872`). So the factor 2.0 never reaches `Mer` or `SkyWatcher`.
4. **The weather setters validate the wrong thing.** `setNextWeatherType` (`DefaultClimate.java:185-195`) tests the stored `nextWeather` rather than the argument `weatherCode`, and in its error branch it sets `currentWeather`. `setCurrentWeatherType` (`:197-207`) resets `currentWeather` to CLEAR on a bad code and then assigns the bad code anyway. Out-of-range values get stored.
5. **Defaults disagree.** Code defaults have 8 months (`DefaultTimeClock.java:102-105`), 20 days per month (`:106`) and night at hour 6 (`:107`). The shipped ini has 7 months (`coffeemud.ini:1115`), 24 days (`:1109`) and night at hour 5 (`:1142`). If the ini values are applied (the call site was not traced), they win. A clock built without them keeps the code defaults.
6. **Seasons are unequal with the shipped calendar.** With 7 months, `monthsInSeason` is 1 (`:396-400`). FALL then covers months 3 to 6, four of seven months, and WINTER is a single month (`:418-428`). With 8 months the split is even.
7. **The clock drifts and does not catch up.** Each hour is `period + up to one tick`, because the test is `>` and `lastTicked` is set to the current time (`:1087-1095`). A stall loses hours. A manual shift restarts the timer (`:792`).
8. **Thread safety.** `CoffeeTime.FORMAT` is a static `SimpleDateFormat` (`CoffeeTime.java:47`) and is used in `string2Date` (`:296`). `SimpleDateFormat` is not thread-safe, so concurrent parses can corrupt each other.
9. **Three overlapping period enums.** `TimeClock.TimePeriod` (`TimeClock.java:736-745`), `TimeManager.TimePeriod` (`TimeManager.java:457-478`) and `CoffeeTime.TimeDelta` (`CoffeeTime.java:55-77`) do similar jobs. `TimeManager.TimePeriod` is used only by `Libraries/EnglishParser.java`. `TimeClock.TimePeriod.SEASON` is a quarter of a real year (`TimeClock.java:742`), not a mud season, so the same name means different things in different places.
10. **Enum-order coupling.** `theWeatherDescription` indexes `CMProps.ListFile.values()` by `WEATHER_CLEAR.ordinal() + weather` (`DefaultClimate.java:518-519`). The list-file enum order (`core/CMProps.java:606-619`) must match the `Climate` constants, and nothing checks that.
11. **Null room.** `canSeeTheStars` calls `room.getArea()` before any null check (`DefaultClimate.java:244`).
12. **Dead or overwritten values.** The field `monthsInSeason` (`DefaultTimeClock.java:100`, `:159`) is assigned but never read. The getter recomputes it. `CoffeeTime.date2ZoneString` assigns a value and then overwrites it (`CoffeeTime.java:670-672`).
13. **Full-world walk on time-of-day change.** `handleTimeChange` visits every area and room bound to the clock, four times per mud day at defaults (`DefaultTimeClock.java:734-785`). That is acceptable for small worlds and wasteful for large ones.
14. **Multiplayer assumptions.** The messages go to every non-monster inhabitant in the area (`DefaultClimate.java:326-353`; `DefaultTimeClock.java:749-773`). Experience is paid per mud day to each player through their home clock (`Scholar.java:449-451`; `Artisan.java:355-357`). The GLOBAL clock is one object shared by many areas and advanced by whichever area ticks first (`CoffeeTime.java:1052-1064`).
15. **Text as written.** The outside dawn and dusk lines say the sun rises in the west and sets in the east (`resources/lists.ini:669`, `:671`). That may be intentional. I report it as written.

## Takeaways for a Python SUD

**Borrow**
- **A calendar config separate from the clock state.** Keep a frozen `Calendar` (hours per day, days per month, month names, dawn/day/dusk/night hours) and a clock that stores only one integer, hours since the epoch. Derive year, month, day and hour with `divmod`. Python's `divmod` floors for negative values, which avoids the boundary bugs in Problems 1 and 2. The sketch below is not run:

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class Calendar:
    hours_per_day: int = 6
    days_per_month: int = 24
    month_names: tuple = ("month 1", "month 2", "month 3", "month 4", "month 5", "month 6", "month 7")
    dawn: int = 0; day: int = 1; dusk: int = 4; night: int = 5   # start hours

    @property
    def hours_per_month(self): return self.hours_per_day * self.days_per_month
    @property
    def hours_per_year(self): return self.hours_per_month * len(self.month_names)

@dataclass
class WorldClock:
    cal: Calendar
    hours: int = 0                      # the only stored time

    def advance(self, n): self.hours += n            # negative is fine

    def fields(self):                   # 0-based year, month, day, hour
        year, rest = divmod(self.hours, self.cal.hours_per_year)
        month, rest = divmod(rest, self.cal.hours_per_month)
        day, hour = divmod(rest, self.cal.hours_per_day)
        return year, month, day, hour

    def time_of_day(self):
        h, c = self.fields()[3], self.cal
        if h >= c.night: return "night"
        if h >= c.dusk: return "dusk"
        if h >= c.day: return "day"
        if h >= c.dawn: return "dawn"
        return "night"                  # before dawn is still night

    def season(self):                   # four seasons, split as evenly as the months allow
        return (self.fields()[1] * 4) // len(self.cal.month_names)

    def moon_index(self):               # 0..7, same formula as DefaultTimeClock.getMoonPhase
        return (8 * (self.fields()[2] + 1)) // (self.cal.days_per_month + 1)
```

- **A table-driven weather chain with a forecast.** Store `BASE[season][state]` and `CHANGE[from][state]` as data, with a block value for impossible transitions. Each step, take `weights = max(0, BASE[season][t] + CHANGE[forecast][t])`, roll with `random.choices(range(14), weights)` (or a smaller set of states), and make the old forecast the current weather. Keeping the forecast gives the "predict the weather" skill a real answer.
- **Moon phase by the same formula, with BLUE left out.** One integer index gives a lunar cycle inside the month. Make a blue moon a deliberate event if you want one.
- **Text by composition.** Store weather text as prefix (climate or season) and suffix (wind) lists in YAML or JSON. Adding a new weather then means adding data.

**Simplify**
- **No tick machinery.** Advance the world explicitly: `world.advance(hours)`. Real-time play calls it with elapsed seconds divided by the seconds per game hour. Turn-based play calls it with the cost of each action. The same method serves both, which fits the undecided real-time versus turn-based choice.
- **Fewer weather states and climate bits.** CoffeeMud's 14 states and 5 climate tables are more than a single-player text game needs. A set of 8 to 10 states with one region-level table would keep the mechanic.
- **Seasons by fraction.** A split like `(month * 4) // months` gives four seasons as evenly as possible. CoffeeMud's `floor(months/4)` method does not.

**Drop**
- The Gregorian-time formatting, the tick-expression parser and the duration units, unless the game shows durations in text. Python's `datetime` covers the first two.
- The database save, the GLOBAL clock, the copy-on-edit builder code, and the space and ship variants.

**Single-player notes**
- Messages go to the player's current location only. The `isMonster()` checks and the broadcast to every inhabitant disappear. An "area broadcast" becomes "what the player can perceive from here".
- Experience per mud day and the library due dates become counters on the world or the player, not per-home-clock logic.
- A time-travel effect is just `world.advance(n)`. Be aware that it moves the weather chain forward too, unless you decide otherwise.

## Coverage

**Read in full** (all six bucket files):
- `Common/interfaces/TimeClock.java` (lines 1-977)
- `Common/interfaces/Climate.java` (1-198)
- `Libraries/interfaces/TimeManager.java` (1-479)
- `Common/DefaultTimeClock.java` (1-1378, in two reads)
- `Libraries/CoffeeTime.java` (1-1169, in two reads)
- `Common/DefaultClimate.java` (1-639)

**Read in part, outside the bucket**, only where the bucket's code calls them:
- `Areas/StdArea.java` (1270-1309 and 552-566)
- `core/CMProps.java` (2571-2593 and 3525-3545; tick constants at 681-682 and list-file enum entries at 569-571 and 606-620 through grep)
- `core/threads/ServiceEngine.java` (396-410; the default tick period at 349-351 through grep)
- `core/interfaces/Tickable.java` (95-100; other tick IDs through grep)
- `Libraries/Dice.java` (390-415)
- `resources/lists.ini` (660-694 and 788-1047)
- `coffeemud.ini` (1085-1164; the `TICKTIME` line at 2151 through grep)
- `Abilities/Druid/Chant_AlterTime.java` (60-117)
- `Commands/TickTock.java` (55-82)
- `CharClasses/Mer.java` (305-316 and 452-468)
- `CharClasses/SkyWatcher.java` (372-384)
- `CharClasses/Scholar.java` (444-456), `CharClasses/Artisan.java` (350-360)
- `Libraries/CMGenEditor.java` (1212-1257)

**Grep only** (location confirmed, not read): `core/interfaces/Places.java` (climate bits, 91-105), the callers of `adjustWaterConsumption`, `adjustMovement`, `getTidePhase`, `getMoonPhase`, `canSeeTheMoon`, `parseTickExpression`, `isTickExpression` and `TimeClock.save`, and the other area classes that create clocks.

**Skipped:** the other implementations of `Tickable` and the server's full tick scheduling, the database layer behind `DBReadAreaData` and `DBReCreateAreaData`, the XML loading of area data, and the help files for these commands.
