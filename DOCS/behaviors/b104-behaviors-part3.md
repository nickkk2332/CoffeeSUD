# Behaviors, part 3: chat, custody, stock, travel and services

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b104` (Behaviors [part 3/5: InstantDeath .. NastyAbilities]) · 21 files · 7818 lines of Java.

Citations are `path:line` relative to the CoffeeMud folder. A bare `Name.java:line` refers to a file in `com/planet_ink/coffee_mud/Behaviors/` (the Key files table gives each full path); every other folder is written out in full. Timing figures assume the default 4-second tick (`com/planet_ink/coffee_mud/core/CMProps.java:681`). Items marked "unverified" were not confirmed by running the code.

## Summary

This bucket is the third slice of CoffeeMud's `Behavior` classes (21 files). Each one attaches to a mob, item, room or area and acts without a player command, usually through the shared `ActiveTicker` timer (a random countdown window plus a percent chance) or by intercepting `CMMsg` messages before and after they run. The largest file, `MudChat`, is a data-driven conversation engine: patterns in `chat.dat` map what players say or do to weighted replies, forced commands and optional LLM lines. The rest cover a babysitting service that bills by the mud-hour (`Nanny`), a stock-keeping item spawner (`ItemGenerator`), a monster that swallows players into a real stomach room (`MOBEater`), a trainer (`MOBTeacher`), a subway-style vehicle built from a room (`MovingRoom`), wandering and guarding movement, paid repair, refit and identification services, and a few traps and effects. The timer, the keyword-driven dialogue, the service pricing and the vehicle route are worth borrowing for a Python SUD, but the bucket also contains more than twenty concrete defects (see Problems and cruft).

## Key files and types

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Behaviors/MudChat.java` | Keyword and pattern driven NPC dialogue; parses `chat.dat`, queues weighted replies and commands | 1755 |
| `com/planet_ink/coffee_mud/Behaviors/Nanny.java` | Babysitter and caretaker: watches babies, children, mounts and vehicles, bills per mud-hour, holds items until paid | 1095 |
| `com/planet_ink/coffee_mud/Behaviors/ItemGenerator.java` | Keeps an area, room, container, shop or mob stocked with random items drawn from all craft skills | 597 |
| `com/planet_ink/coffee_mud/Behaviors/MOBEater.java` | Swallows a victim into a private stomach room and digests it with acid damage | 593 |
| `com/planet_ink/coffee_mud/Behaviors/MOBTeacher.java` | Teaches abilities, classes and expertises when a player says "teach" or "gain" (extends `CombatAbilities`) | 562 |
| `com/planet_ink/coffee_mud/Behaviors/MovingRoom.java` | A room that travels between stop rooms, rewiring exits and descriptions at each stop | 545 |
| `com/planet_ink/coffee_mud/Behaviors/Mobile.java` | Base wandering behaviour for mobs: leash, locale and breathing limits, door opening | 385 |
| `com/planet_ink/coffee_mud/Behaviors/MoneyChanger.java` | Takes a cut to convert one currency into another | 314 |
| `com/planet_ink/coffee_mud/Behaviors/Mime.java` | Copies the last emote or social seen in the room and replays it | 205 |
| `com/planet_ink/coffee_mud/Behaviors/InstantDeath.java` | Trap: after the first offence, kills the occupants every tick | 189 |
| `com/planet_ink/coffee_mud/Behaviors/NastyAbilities.java` | NPC casts malicious abilities at PCs in its room, chance weighted by mana | 188 |
| `com/planet_ink/coffee_mud/Behaviors/ItemMender.java` | Repairs worn or wielded items for a formula price | 161 |
| `com/planet_ink/coffee_mud/Behaviors/ItemIdentifier.java` | Identifies an item for a price and reports material, weight, damage type and curses | 159 |
| `com/planet_ink/coffee_mud/Behaviors/ItemRefitter.java` | Resizes armour to the wearer for a formula price | 157 |
| `com/planet_ink/coffee_mud/Behaviors/MOBHunter.java` | Every few hundred ticks, picks prey within a radius and casts `Thief_Assassinate` on it | 157 |
| `com/planet_ink/coffee_mud/Behaviors/MobileAggressive.java` | Wandering mob that attacks targets chosen by a mask, delegating to `VeryAggressive` | 152 |
| `com/planet_ink/coffee_mud/Behaviors/MOBReSave.java` | Writes a mob's base stats to the database on a fixed interval | 135 |
| `com/planet_ink/coffee_mud/Behaviors/MobileGoodGuardian.java` | Wandering guardian that moves to rooms where it can keep the peace | 134 |
| `com/planet_ink/coffee_mud/Behaviors/Mageness.java` | Turns a mob into a caster: class, random abilities, a frail combat package (extends `CombatAbilities`) | 123 |
| `com/planet_ink/coffee_mud/Behaviors/MOBHelper.java` | Joins a fight to defend a monster who is attacked in its presence | 112 |
| `com/planet_ink/coffee_mud/Behaviors/LinkedWeather.java` | Area behaviour that copies another area's weather | 100 |

Base and support classes used throughout: `com/planet_ink/coffee_mud/Behaviors/StdBehavior.java` (base class, read in full) and `com/planet_ink/coffee_mud/Behaviors/ActiveTicker.java` (timer, read in full). Related files in the same folder but outside this bucket: `VeryAggressive.java`, `GoodGuardian.java`, `Aggressive.java`, and `CombatAbilities.java` (the base of MOBTeacher and Mageness).

## How it works

### 0. Shared machinery: StdBehavior and ActiveTicker

- `StdBehavior` supplies the no-op hooks. `okMessage()` returns true (`com/planet_ink/coffee_mud/Behaviors/StdBehavior.java:249-253`), `executeMsg()` does nothing (244-247), and `tick()` returns false for destroyed objects (297-302). Helpers resolve the behaviour's room or mob (`getBehaversRoom`/`getBehaversMOB`, 170-211). Parameters are a free-form string kept in `parms` (213-223).
- `ActiveTicker` is the timer that most of this bucket uses. Fields `minTicks=10`, `maxTicks=30`, `chance=100`, `minGroup=1` (`ActiveTicker.java:49-52`). The parameter string may set `min`, `max`, `chance` and `mingrp` (`setParms`, 74-82).
- `tickReset()` (57-72) draws the next countdown uniformly from `[minTicks, maxTicks]`. With `mingrp` set, only every `minGroup`-th reset draws a random window and the others wait `minTicks`, which staggers a group of mobs.
- `canAct()` (136 onward) decrements `tickDown` on mob, item-behaviour, room-behaviour and area ticks (areas only when the ticking object is an `Area`). When the count drops below 1 it resets, refuses mobs that cannot act at all (`canActAtAll`), refuses during shutdown, and then passes only if `canChance()` rolls at or below `chance` (`canChance`, 128-134; `rollPercentage` is 1 to 100, `com/planet_ink/coffee_mud/Libraries/Dice.java:409-412`).

Timers chosen by individual behaviours (default values in the constructors, before parameters are applied):

| Behaviour | Window (ticks) | Chance | Source |
|---|---|---|---|
| Mime | 1 to 1 | 100 | `Mime.java:61` |
| InstantDeath | 1 to 1 | 100 | `InstantDeath.java:56` |
| MOBEater | 4 to 8 | 50 | `MOBEater.java:75` |
| NastyAbilities | 10 to 20 | 100 | `NastyAbilities.java:60` |
| Mobile (and subclasses) | 20 to 60 | 100 | `Mobile.java:87` |
| MovingRoom | 10 to 30, then replaced by the XML timing | 100 | `MovingRoom.java:86` |
| MOBHunter | 600 to 1200 | 100 | `MOBHunter.java:78` |
| MOBReSave | 140 to 140 | 100 | `MOBReSave.java:64` |

At the default 4-second tick, a `Mobile` waits 80 to 240 seconds after each successful move, and a MOBHunter's timer runs for 40 to 80 minutes.

Two-phase messaging is used for effects on the world. A behaviour can veto an action in `okMessage()` (for example `Nanny.java:336-407`) or append trailer messages to a successful give, which carry the speech and item movement (for example `ItemMender.java:153-158`).

### 1. MudChat: a dialogue engine driven by chat.dat

**Data model.**
- `ChattyGroup` holds the group names, zapper masks, the speech `entries`, the per-tick `tickies`, and `varOverride` variables (`MudChat.java:279-308`).
- `ChattyEntry` holds a parsed match expression, its responses, flags for combat-only, LLM-only and non-LLM, and an `orderNum` for file order (246-271).
- `ChattyTestResponse` holds the reply weight, the reply list and an optional group switch (216-240). The weight is the first character of the response line, a digit (226).
- A match is a tree of `ChatMatch` leaves (flag `INSTR`, `TOP`, `EXACT` or `ZAPPER`, plus a string) joined by `ChatExpression` nodes with connectors `AND`, `OR` and `ANDNOT` (139-162).

**File syntax (what the parser accepts).**
- Entry headers: `(...)` matches speech, `[...]` a targeted emote, `{...}` an emote, and `<...>` a per-tick random roll (`getTypeAndCloser`, 1216-1230).
- Prefixes before a header: `*` makes the entry combat-only, `L` LLM-only, `.` non-LLM only (`parseChatData` 509-516).
- Directives: `>` starts a group (names or a `/mask/`), `@` copies the entries of another group (586-612), `%` includes a resource file (613-622), `${VAR=value}` sets a group variable (545-567), `"` writes the line to the log at load (503-505), `#` is a comment (506-508). See `parseChatData` at 481-650.
- Response prefixes: `:` emote, `!` raw command, `"` say, `` ` `` an LLM prompt, and anything else becomes `sayto "<target>" text` when a target exists (`queResponse` 1117-1196).
- A response ending in `$@group` switches the mob's active group, and `$@+group` adds one (`queResponse` 1094-1114). Switches expire after `TALK_SWITCH_EXPIRE` = 360000 ms, six minutes (`MudChat.java:92`).

**Matching.**
- Speech is considered only when a player speaks at the mob, or speaks unaimed in a room holding fewer than three PCs (`executeMsg` 1603-1609). Emotes take a separate path (1638-1676), using the targeted text for `TEMOTE` and the others text for `EMOTE`.
- Speech is indexed by keyword. Each match string contributes its longest word (`parseExpression` 1302-1313), and `ChatEntryGroup` maps each word to the entries that contain it (170-188). `getChatEntries` (813-826) collects only the entries that share a word with the utterance, plus entries with no word, and sorts them by `orderNum`.
- The first matching speech entry wins (`break` at 1629). The walk is in file order.
- Operators: `&` AND, `|` OR and `~` AND-NOT with nesting (`parseExpression` 1326-1337); `^` anchors to the start of the message (`TOP`, match at 1449-1458); `=` requires the whole message (`EXACT`, 1425-1433); `/mask/` runs a zapper mask via `CMLib.masking().maskCheck` (`ZAPPER`, 1459-1460); the default `INSTR` matches a whole word anywhere (1434-1448).
- Per-tick entries are matched against a percent roll in `match(MOB, ChatExpression, int)` (1465-1505). The documented meaning is a percent chance (`resources/chat.dat:20-21`), but the code's comparison is inverted; see Problems.

**Responses and output.**
- Weighted choice: `roll(1, total, 0)` and subtract weights until the value drops to zero or below (`queResponse` 1073-1090). A total weight of zero produces nothing (1076-1077).
- Each reply becomes a `ChattyResponse` with a delay of `RESPONSE_DELAY` = 2 ticks (`MudChat.java:90`, 1210). Identical commands already queued are not added twice (1200-1208).
- The queue drains at most one command per tick. Each command goes through the normal command processor as `doCommand(..., METAFLAG_FORCED)` (`tick` 1735-1750), so NPC speech and emotes take the same path as player commands.
- Substitutions in the reply text (`mcFilter` 965-1047): `$r` (the rest of the message after the match), `$w` (the whole message), `$t` (target name), `$n` (speaker name), `$$`, `$<VAR>` (a variable of the target), `${VAR}` (a variable of the speaker), and `$%...%` (a scripting-engine expression).
- `getVarValue` (851-963) resolves variables in this order: entry-level overrides, then group overrides, then the built-ins `THEMEDESC` (858-875; the medieval, modern or futuristic theme of the area), `ROOMDESC` (877-889), `PERSONALITY` (891-959), and finally a generic generator stat (961).

**LLM replies (optional).** A backtick reply runs its prompt on a separate thread through `CMLib.threads().executeRunnable` (`queResponse` 1144-1188). The reply is queued as a `say`, with a delay adjusted for the time the LLM took (1181-1183). The session is per mob and is recreated after six minutes without use (`getLLMSession` 1049-1068). Nothing is done unless an LLM is installed (`isLLMSupported` 310-315). The reset prompt is honoured only for staff with the CMDMOBS flag (1164).

**PERSONALITY.** `$<PERSONALITY>` builds a stable character sketch for each mob: `new Random(hash)` is seeded from the mob's name and its start-room ID (907-910), so the same mob always gets the same traits. Trait keys come from the alignment checks (CHAOTIC, LAWFUL or MODERATE; EVIL, GOOD or NEUTRAL, 916-935), and the rows come from the `PERSONALITY_TRAITS` list in `resources/lists.ini:2003-2048`. One to `maxTraits` traits are chosen (default 2, line 893, 945-950), and any `Mood` effect text on the mob is appended (951-953).

**Politeness and turn-taking.**
- When another NPC speaks, `talkDown` is set to `TALK_WAIT_DELAY` = 8 ticks (`executeMsg` 1633-1636, constant at `MudChat.java:91`). It counts down once per mob tick (1705-1706).
- `lastReactedToM` stops the mob replying to the same speaker twice; it is cleared when the reply queue empties (1610, 1731-1732).
- Tick entries are tried on each mob tick. An entry runs only if its combat flag matches whether the mob is in combat (`entry.combatEntry==combat`, 1717) and its LLM flags match the installed LLM state (1718-1719).

**Group selection.** `getMyBaseChatGroup` (736-768) picks the mob's group by the `chatGroup` parameter, then by its name (with articles removed), then by race, then by class. `DEFAULT` maps to the first group (712-713), and an unmatched mob falls back to the first group (766).

**Timing summary.** The first tick after a parameter change waits 3 ticks (`MudChat.java:381`, 1691-1699). Replies wait 2 ticks by default (90). Another NPC's speech suppresses reactions for 8 ticks (91). Switched groups last six minutes (92).

### 2. Nanny: babysitting, custody and fees

**Data model.**
- `DropOff` (mother's name, the baby object, a time stamp) and `Payment` (name and amount) live in `dropOffs`, `payments`, `associations` and `sayLaters` (`Nanny.java:63-100`).
- `associations` is the live list of things a customer has brought in. A drop-off is created when the baby is still in the room and its customer is not (917-944).

**What it watches.** Babies, children, mounts, wagons, cars, boats, air cars and followers, each controlled by a flag (`isDropOffable` 464-498). The default is "Babies,Children" (`Nanny.java:818`); the parameters are `RATE`, `NAME` and `WATCHES` (`setParms` 810-848).

**Control flow.**
- On `TYP_ENTER` into the room, the behaviour gathers the customer's group, ride buddies and riders (`executeMsg` 592-625), records associations, and queues a welcome message (639-642) that states the hourly rate.
- Each tick, when the customer is no longer in the game, their associations move to `dropOffs` with the current time (917-944). If the owner comes back, `getAllOwedBy` (182-204) computes the bill.
- The bill is the number of mud-hours elapsed, rounded up, times `hourlyRate`. The mud-hour length is `CMProps.getMillisPerMudHour()`, default 600000 ms, which is ten real minutes (`com/planet_ink/coffee_mud/core/CMProps.java:682`).
- While anything is unpaid, the behaviour refuses gets, pushes, pulls and casts on the dropped items (`okMessage` 360-384), refuses malicious acts against them or against the room (386-407, with the line "Not in my <place> you dont!" at 397), and refuses to let the customer leave with them (409-447). Summon or teleport magic aimed at a dropped-off object fizzles when the object is not in the room (415-428).
- Paying coins (`executeMsg` 657-716) adds to `payments`. Once paid, each owed mob is set to follow the customer again (699-707), and `clearTheSlate` (155-180) moves the records to `associations`. Surplus coins are returned as change (670-688).
- A staff member who says the keyword "report" to the Nanny gets a ledger of drop-offs, associations and payments (726-748).

**Persistence.** `getParms()` (763-807) writes state into the parameter string as XML-style `<DROP>` records, and `tick` reloads them lazily (858-906). Each change saves the room's mobs and items (`DBUpdateTheseMOBs` and `DBUpdateTheseItems` 969-1024). The lazy load keeps only drop-offs whose owner still exists (885).

**Flavour.** With probability 10% per tick (`Nanny.java:1026`), the Nanny chooses one dropped-off baby, child or mount and emits a short message: changing a nappy, playing, grooming, petting (1026-1092). The messages are gentle caretaking and contain nothing sexual.

### 3. ItemGenerator: stock keeping from every craft skill

- **Master list.** A background `ItemGenerationTicker` (214-317) takes each craft skill from `CMClass.craftorAbilities()`, skipping `ClanItems` and `LargeConstructions` (282-288). Each tick it crafts one skill's item sets (`craftAllItemSets(false)`, 304), filters them by the mask (311-312), and when every skill is done stores the list under `ITEMGENERATOR-ALLITEMS` (297). It is started with `startTickDown(..., 1234, 1)` (402), a 1234 ms period.
- **Per-mask cache.** `getCachedItems` keeps one filtered list per mask string under `ITEMGENERATOR-<MASK>` (385-412). If caching is disabled, `getItems` crafts two random skills on the spot instead, and the loop guard makes it two attempts, not three (354-379).
- **Weights.** `filterItems` gives each item a weight of `maxValue / (value + 1)` and sums them (line 343), so cheap items come up more often. `selectRandomItem` walks the list with a random pick (425-437).
- **Stock rules.** `avgItems` is the target count, drawn from `Dice.roll(1, maxItems-minItems, minItems)` (`ItemGenerator.java:165`). `Dice.roll` returns `1..die + modifier` (`com/planet_ink/coffee_mud/Libraries/Dice.java:398-406`), so with `maxitems` greater than `minitems` the target lies between `minitems+1` and `maxitems`. A maintained list is pruned each tick by `isStillMaintained` (186-212, 574-585). When the target is met, the next check is `maxTicks` away (588).
- **Placement.** A shop receives the item as stock (`addStoreInventory`, 457-465). A container or mob receives it directly (467-485), and a room or area receives it in a random metro room honouring `restrictedLocales` (487-513). A gettable item may also go to a random savable inhabitant of the room (521-537), and goes on the floor unless `MOBS` is set (538-542).
- **Enchantment.** `CMLib.itemBuilder().enchant(I, enchantPct)` gives a percent chance of a random bonus (default 10%; `com/planet_ink/coffee_mud/Libraries/TimsLibrary.java:1497-1623`). For ammunition and weapons the bonus is a stat change, a combat spell or a curse (the armour branch was not read).
- **Parameters.** `minitems`, `maxitems`, `maxdups`, `enchanted`, `MOBS`, locale tokens such as `+CITY` or `-ALL`, and a zapper mask after `;` (`setParms` 82-169).

### 4. MOBEater: a monster with a stomach room

- **The stomach.** `getStomach()` builds a separate `StoneRoom` inside a new `StdArea` the first time it is needed (86-98). It is not savable. `startBehavior` names it after the eater and gives it a description that tells the occupant they are being digested (101-112), and adds no-recall and no-teleport-out effects (113-114).
- **Swallowing.** In combat, the eater tries to swallow its current victim (`tick` 291-294). The victim must weigh less than a third of the eater (`baseWeight < weight/3`, 322-323), pass a 5% roll (`chanceToEat`, line 62 and 323), be no more than the eater's level plus `EXPRATE` allows (324), and the room must allow a `MSG_EAT` (335-337). The victim is then moved into the stomach, and the eater's hunger is set to its maximum (340-341). Players also get an area-visit achievement (345-346).
- **Digestion.** Every four ticks (`digestDown`, 59 and 272-276), each occupant takes damage equal to `dmgBonus` plus `curHP * acidpct / 100`, at least 2 (379-381), halved when the digestion message's value is non-zero (382-383). `dmgBonus` grows each cycle while the stomach is occupied and resets when it is empty (388-391). Monsters inside attack the walls each tick (281-288).
- **Escape by fighting.** A watcher on the stomach room intercepts a failed "attack"/"kill" command aimed at "here", "all", "stomach", "walls" or "all.*" (`startBehavior` 134-150). It requires a wielded weapon with no minimum range (152-160), is refused if the attacker's peace time is under one tick (161-165), and needs a successful hit roll (166). Damage is the adjusted damage divided by ten (174), applied to the eater (181-182).
- **Release.** The eater dying or leaving the game empties the stomach to the last known location (`executeMsg` 301-306, `kill()` 201-257).
- **Interface.** The class implements `MOBPossessor` and `ItemCollection` and forwards every call to the stomach room (396-592), so other code can see the stomach as the monster's inventory.

### 5. MOBTeacher: teaching by speech

- **Setup.** `ensureCharClass` (214-320) reads the parameter string. If it names a single ability it teaches only that (232-237). Otherwise it accepts a list of tokens: class names (`setTheCharClass` 72-92), ability IDs, expertise names, `N%` percentages that apply to the items after them (274-278), and the flags `NOCOMMON`, `NOEXPS`/`NOEXP`, `NOHLEXPS`/`NOHLEXP` and `NOTEACH`/`NOTEACHING` (246-265). The mob is set to the default class with intelligence and wisdom of 19 (227-228).
- **Expertises.** After four mob ticks (`tickDownToKnowledge`, 54 and 146-150), the mob receives its expertises once, unless `noExpertises` is set (the field comment says it "doubles as a done ticking flag", line 52). When everything is taught, every definition is added (155-160). Otherwise, base stats are temporarily set to 100 and qualifying expertises are added until none new appear (161-186), then the original stats are restored (187-188).
- **Speech.** A player whose speech contains "TEACH" or "GAIN " (379-383) has the request trimmed of quotes, "please" and "me" (387-423). "LIST" gets a pointer to the QUALIFY command, and "ALL" is refused (424-433). The teacher must see and be seen by the student (434-443).
- **Lookup order.** Exact or fuzzy name match among abilities the student qualifies for and does not yet have (448-462), then an expertise (472-523), with a special message when the word names a class (523-524).
- **Teaching an ability.** The student's proficiency is set to `(maxProficiency * PRACMAXPCT / 100) / 2` (551-553). The default `PRACMAXPCT` is 75 (`com/planet_ink/coffee_mud/core/CMProps.java:3108`), so a student learns to about 37.5% of the maximum. The word "please" raises the teacher's intelligence and wisdom to 25 for the lesson (543-548) and resets them to 19 (555-556).
- **NOTEACH.** The `okMessage` hook clears the mob's `NOTEACH` attribute unless the parameter set `noTeaching` (337-345). The `TRAIN` command checks `NOTEACH` (`com/planet_ink/coffee_mud/Commands/Train.java:373-378`). The speech path in `executeMsg` does not check it, so the flag controls TRAIN and not speech teaching.

### 6. MovingRoom: a vehicle built from a room

- **Data.** The XML section named in the parameters (for example `SUBWAY` or `BUSONE` in `resources/movingroom.xml`) gives the stop room IDs, travel and door directions, arrival and departure texts for the inside and outside, and the open and closed door description fragments (`parseMovingXML` 148-195). The parameter string is `min=N max=N chance=N;SECTION;dwell;travel` (`setParms` 97-124, documented in `guides/refs/behav.html`).
- **Route.** The car moves forward through the stop list and reverses at each end (`tick` 321-340). If the list is malformed, the behaviour removes itself (341-353).
- **Timing.** At a stop the car waits `roomInfos(1)` ticks (set on arrival, 500 and 537). Between stops it waits `roomInfos(2)` ticks (set on departure, 413 and 460). The example `SUBWAY;3;10` gives 3 ticks (12 seconds) at each stop and 10 ticks (40 seconds) between stops.
- **Plugging in.** On arrival, a new `StdOpenDoorway` exit is created in each direction between the car and the stop (`CMClass.getExit("StdOpenDoorway")`, 475-479 and 512-516). On departure, both exits are cleared (381-391 and 428-438). Door text is appended to the descriptions of both rooms (400-407 and 447-455), and removed again by `removeStubs` (256-270).
- **Text.** `fixOutputString` (197-254) substitutes `$disproom`, `$traveldir`, `$inopendir` and `$outopendir`, and leaves the text "bound" after `$traveldir` in place, so the XML's "$traveldirbound" prints as "westbound".
- **Gate.** The car moves only while at least one of its areas is `ACTIVE` (306-319), so an unattended line does not run.

### 7. Movement and aggression: Mobile and its relatives

- **Mobile.** Parameters are `LEASH=n`, `WANDER`, `OPENDOORS` and locale tags `+X` or `-X` (`setParms` 176-249). Each active tick (343) the mob does nothing in combat (332-334) or where activity is not allowed (349). A mob that cannot track skips 99 times in 100 (352-356). The behaviour collects the neighbouring rooms it may not enter (357-368) and calls `CMLib.tracking().beMobile(...)` (371), whose internals are in `com/planet_ink/coffee_mud/Libraries/MUDTracker.java:898` and were not read. If the mob did not move, `tickDown` is set to 0 so the mob retries on the next tick (372-373).
- **Leash.** `okRoomForMe` keeps a cached distance per room (`leashHash`, 99-125). An unseen current room is labelled 0, and an unseen destination one more than the room it is entered from (108-116). This looks like a distance from the room where the mob first ran, relaxed over time; unverified against the spawn room.
- **Breathing.** A mob may not enter a room whose atmosphere it cannot breathe, unless `ignoreAtmosphere` is set (126-129). `emergencyMove` (251-320) makes up to 30 attempts to get the mob out of such a room, using random radial searches and single steps toward rooms it can breathe in.
- **MobileAggressive.** Extends `Mobile`. Its parameters (`DELAY`, `MESSAGE`, `CHECKLEVEL`, `MOBKILL`, `NOGANG`, `MISBEHAVE` and a zapper mask) are handed to a `VeryAggressive` helper, after locale tags are removed (80-113). Each aggression tick is counted down in `aggressiveTickDown` (141-148) and calls the helper's `tickAggressively` and `tickVeryAggressively` (145-147).
- **MobileGoodGuardian.** A `Mobile` that moves only when it is free to act. It does not move when a ridden mount has riders (69-76), when it is following someone who is near (78-85), or when it cannot taste (81). Otherwise it keeps peace in its room and looks for an adjacent room with a victim to stop (`GoodGuardian.anyPeaceToMake` and `keepPeace`, 89-120), then walks there (126).
- **MOBHunter.** At long intervals (600 to 1200 ticks), it finds prey within `radius` (default 15, 75-80) by scanning rooms in breadth-first order that are open, in the same area, not empty grid cells, and not air or water (`findPrey` 101-127). It refills mana and movement (149-150) and invokes `Thief_Assassinate` at the prey (147-151).
- **MOBHelper.** When a player attacks a monster in the same room, the helper calls `Aggressive.startFight` on the attacker (`executeMsg` 75-111, call at 108). An optional count caps how many fighters may already be on the attacker (97-107).

### 8. Services: MoneyChanger, ItemMender, ItemIdentifier, ItemRefitter

- **Formula prices.** The three item services compile a formula string once (`setParms`) and evaluate it with a variable vector: `@x1` level, `@x2` value, `@x3` uses remaining, `@x4` magical flag, `@x5` base level, `@x6` base value (`ItemMender.java:55`, 51-84). Defaults are `(100-@x3)+@x1+(@x4*@x1)` for the mender (`ItemMender.java:71`), `500 + (@x1 * 20)` for the identifier (`ItemIdentifier.java:66`) and `(@x1*100)+(@x4*@x1*100)` for the refitter (`ItemRefitter.java:71`).
- **ItemMender.** Refuses items that cannot wear out (`okMessage` 104-108), items already above 100 uses (110-114) and items at 100 uses (116-120). It checks the customer can pay (121-126). On success it subtracts the cost (`executeMsg` 149), sets uses to 100 (152), and returns the item by trailer message with a line of speech (153-158).
- **ItemIdentifier.** Its report, spoken to the customer, lists the material (`RawMaterial.CODES.NAME`), armour size if any, weight (showing the empty weight of containers), weapon class and damage type, a curse flag, and the item's `secretIdentity()` (`executeMsg` 134-150). Givers with the CMDROOMS security flag are not charged (`okMessage` 93).
- **ItemRefitter.** Only armour qualifies (`okMessage` 104-108). An item whose base height is already 0 is refused as already fitting (110-114). A successful refit sets the base height to 0 (`executeMsg` 146), which is the "fits you" state.
- **MoneyChanger.** The default cut is 5% (`MoneyChanger.java:46`). Parameters are `CURRENCY=pct` pairs, `cut=`, `default=`, and for space currencies `spacemaxcut=` and `spacemaxdistance=` (`setParms` 147-204). A customer who gives coins gets the value less the cut, returned as change by trailer message (`executeMsg` 257-313). The okMessage check refuses amounts too small to change (`okMessage` 238-251). On start, the changer's base strength is set to 100 so it can carry coin (`startBehavior` 63).

### 9. Smaller behaviours

- **InstantDeath** (`InstantDeath.java:37-189`). The trap is inactive until a first offence (`executeMsg` 149-188). For a mob, a malicious act by a player activates it (158-159). For food and drink, eating or drinking activates it (163-168). For armour or weapons, wearing, holding or wielding does (171-178). Other items activate on get, push or pull (180-184). Any other target activates it at once (185-186). Once active, each tick kills the occupants of the mob's room (`killEveryoneHere` 78-98, which spares the mob itself and immortals at line 89), the owner of an item (116-131), the occupants of a room (133-134), or every metro room of an area (136-143).
- **NastyAbilities** (`NastyAbilities.java:37-188`). Each tick, the chance to act equals the mana fraction, and nothing happens below 50 mana (102-104), so a mob at full mana acts whenever its timer fires. It picks a random PC in the room (106-130). With no mask set, it skips a player whose group leader is a monster or is fighting this mob (118-124). It casts one of its malicious abilities (149-151) if `castingQuality` says the spell is malicious (152-153). Afterwards it restores each occupant's victim so the cast does not start a fight, unless `fightok` is set (170-180).
- **Mageness** (`Mageness.java:36-123`). `startBehavior` sets the mob to random-combat mode, applies a class, and calls `getSomeMoreMageAbilities`, which adds `level + 5` random abilities from the mob's class listings (72-102). Qualifying abilities are malicious or beneficial, not prayers the mob's faction cannot use (83-89), and are granted at half their maximum proficiency (96). Unless a preset cast-rate is given, it applies a combat package: attack -10, armour -50, damage -10, hit points -15, mana +50, move -50, and speed flag (117-121, applied by `setCombatStats` at `CombatAbilities.java:253-291`).
- **Mime** (`Mime.java:35-205`). `executeMsg` records the last emote or social that another player performed in the room (74-86). Each tick it replays that message as the mime (143-163). For a non-mob host it uses a stand-in mob named after the object or room (152-162). A social aimed at a mob is mirrored back at the player who sent it (165-171). A `disabled` flag stops the mime reacting to its own output (189-202).
- **LinkedWeather** (`LinkedWeather.java:35-100`). An area behaviour that copies another area's weather. Its target is named in the parameters, optionally followed by `;ROLLING`. Normally it copies both the current and the next weather (94-95). With `ROLLING` it copies only the next weather (91).
- **MOBReSave** (`MOBReSave.java:36-135`). The first tick applies the base stats from the parameters (113-124). Every 140 ticks it writes the mob to the database (125-129). `getParms()` (84-95) rebuilds the parameter string from the mob's current base stats, so the behaviour's parameters act as a save record.

## Content worth mining

**Personality trait pool** (`resources/lists.ini:2003-2048`). Six alignment groups, each a bag of adjectives for NPC sketches:

| Group | Traits | Examples |
|---|---|---|
| LAWFUL | 35 | Disciplined, Honorable, Organized, Stern, Pedantic |
| MODERATE | 40 | Balanced, Flexible, Pragmatic, Diplomatic, Witty |
| CHAOTIC | 35 | Impulsive, Rebellious, Free-spirited, Erratic, Whimsical |
| GOOD | 40 | Compassionate, Altruistic, Merciful, Noble, Cheerful |
| NEUTRAL | 43 | Self-interested, Practical, Survivalist, Cynical, Prudent |
| EVIL | 35 | Cruel, Malicious, Vengeful, Ruthless, Treacherous |

Several traits appear in more than one group (for example "Disciplined" in LAWFUL and MODERATE), so the groups overlap.

**Chat grammar and sample groups.** `guides/refs/behav.html` and `guides/MUDChat.html` document the group names in the default file: default, dog/fido/beastly, cat/kitten/lion, pig/boar, doll/toy, papa_smurf/smurf/monster/smurfette, and healer/cleric/doctor. The matching grammar, weights and `$@` switches in those guides are a ready-made design for keyword dialogue. The default data also contains crude and sexual social emotes (for example around `resources/chat.dat:123` and `resources/chat.dat:167-182`). They are not reproduced here.

**Transit data** (`resources/movingroom.xml`). Two sample lines. SUBWAY: "Subway Line #1", stops "Subway System#0" to "#4", with arrival and departure texts such as a car arriving on the platform and a recorded "Next stop" announcement (`movingroom.xml:77-133`). BUSONE: "Bus #1" with stops "City of Glass#9", "#5", "#10" and "#13" (`movingroom.xml:135-143`).

**Service formulas and messages.** The three price formulas above, and these lines: "You'll need @x1 for me to repair that" (`ItemMender.java:124`), "There she is, good as new!" (`ItemMender.java:155`), "You'll need @x1 for me to identify that" (`ItemIdentifier.java:101`), and the ItemIdentifier report lines "is made of ...", "It is a size ...", "It weighs ... pounds", "It is cursed" (`ItemIdentifier.java:134-149`).

**Nanny messages.** Paraphrased: a welcome that lists what the customer has left and the hourly rate (`Nanny.java:639-642`); a reminder that fees are still owed (374-382, 436-445); a protest at anyone who harms a dropped-off item or the room ("Not in my <place> you dont!", `Nanny.java:397`); and a change-making line (675).

**MOBTeacher messages.** "I can't teach you everything at once. Try the QUALIFY command." (`MOBTeacher.java:431`), "I can't see you, so I can't teach you." (436), "I'm sorry, but I've never heard of ..." (537).

**Caster template** (`Mageness`). The attack/armour/damage/hit-point/mana/move package above is a clean, data-like "frail caster" profile.

**Pricing and timing defaults.** The timer table above, the mana-scaled spell chance (`NastyAbilities.java:102-104`), and the ten-minute mud-hour (`com/planet_ink/coffee_mud/core/CMProps.java:682`).

## Notable design ideas

- **One timer for everything.** `ActiveTicker` gives every behaviour the same primitive: a random countdown in a window, then a percent chance (`ActiveTicker.java:57-72, 128-134, 136-160`). The `mingrp` option (lines 58-68) staggers a group, so a crowd of guards does not all act together.
- **Keyword index for dialogue.** Each speech pattern is indexed by its longest word, so only relevant entries are tested (`MudChat.java:170-188, 813-826`). Weighted replies and group switches (`$@`) give cheap but varied conversation with state.
- **Deterministic character sketches.** The personality generator seeds its random number generator from the mob's name and start room (`MudChat.java:907-910`), so an NPC keeps its personality across restarts without storing it.
- **A stomach that is a room.** `MOBEater` builds a real `Room` and makes it the monster's inventory (`MOBEater.java:86-98, 396-592`). Escape by fighting is handled by intercepting a failed command in that room (`134-150`).
- **A vehicle that is a room.** `MovingRoom` plugs itself into stop rooms by creating and removing exits and door text at each stop (`MovingRoom.java:381-479`), so the rest of the world needs no special code.
- **Custody as a timeline.** `Nanny` records association when the customer enters, turns it into a drop-off when they leave, and bills by elapsed mud-hours (`Nanny.java:530-569, 917-944, 182-204`).
- **Traps that wait for a trigger.** `InstantDeath` stays inert until a specific kind of offence, then acts every tick (`InstantDeath.java:149-186`).
- **Formula prices.** Each service has a formula string with named variables, compiled once (`ItemMender.java:51-84`).
- **Saving through parameters.** Nanny and MOBReSave write their state into the parameter string (`Nanny.java:763-807`, `MOBReSave.java:84-95`), so state lives with the object definition.

## Problems and cruft

These were found by reading the code. None was executed.

**Logic defects**

1. `MudChat.java:601`: the `@` include adds the original entry `CE` to `currentChatEntries`, not the copy `copyCE` with the new `fromGroup` and `orderNum` (the copy is built at 598-600 and used only for tick entries at 605-608). Included speech entries keep their source group's variables and ordering.
2. `MudChat.java:1468`: `match(MOB, ChatExpression, int)` fixes `conn` to `AND` and never updates it, so `|` and `~` inside `<...>` tick expressions behave as AND. The string matcher (1508-1553) tracks connectors correctly.
3. `MudChat.java:1481`: the tick test is `CMath.s_int(cm.str.trim()) <= val`, where `val` is a roll from 1 to 100 (`MudChat.java:1720`). A tick entry written `<30>` fires when the roll is 30 or more, about 71% of ticks, not 30%. The chat.dat header documents the opposite: `<` means "a number 1-100 representing percentage chance of triggering" (`resources/chat.dat:20-21`). No bundled chat.dat line starts with `<`, so the intended meaning is confirmed only by the header.
4. `MudChat.java:899`: `maxTraits = Math.max(0, CMath.s_int(var.substring(x+1).trim()))` uses the offset into `var` where it should use `parts[x]`. For `PERSONALITY:3` this parses "RSONALITY:3" as 0, `maxTraits` becomes 0, and `r.nextInt(maxTraits)` at line 910 throws `IllegalArgumentException`. A variable of this form would fail on any MOB reply that uses it.
5. `MudChat.java:947`: if no alignment is loaded and no override is given, `traits` is empty and `r.nextInt(traits.size())` throws in the same way (unverified whether an alignment is always loaded).
6. `MudChat.java:1559-1575`: `lastSpeakerM` is assigned only inside the `else if` that requires it to be non-null (line 1574), so it is never set. The expiry extension (1561-1563) and the group reset (1566-1573) are unreachable in practice.
7. `MudChat.java:1120`: an empty reply line `return`s from `queResponse`, dropping the rest of the reply set. The source comment reads "why not continue?".
8. `MudChat.java:1717-1725`: tick entries do not `break`, so the last matching tick entry wins. Speech entries (1629) take the first match. The two rules disagree.
9. `Nanny.java:246`, `248`, `250`: the plural suffix for mounts, friends and things tests `babies>1`, not their own counts.
10. `Nanny.java:273`: `abbreviatedPrice(currency, (t+hourlyRate))` adds the rate to the hour count instead of multiplying. Compare `Nanny.java:200` and `297`, which use `t*hourlyRate`. Line 271 rounds down while 198 and 295 round up.
11. `Nanny.java:865`: `parms=parms.substring(0,3)` cuts the stored parameter field to three characters during the first tick. The effect is hidden because `getParms()` is rebuilt from fields, but it is clearly unintended.
12. `Nanny.java:816-817` and `61`, `60`: `setParms` defaults the rate to 2.0 and the place to "nursery", while the field defaults are 1.0 and "Nursery".
13. `Nanny.java:197-200`: the bill charges the first, partly used mud-hour in full (rounded up). `guides/refs/behav.html` says "the first mud-hour is free". No free-hour logic exists in the file.
14. `MOBEater.java:219-236`: the second player loop tests `!these.contains(M)`, but every occupant of the stomach was added to `these` at 211-217. As written, the loop never acts on players inside the stomach. The occupants are moved by the loop at 246-253 instead.
15. `ItemGenerator.java:521-542`: a gettable item with a suitable inhabitant is added to that mob (533), and then, when `favorMobs` is false, the same object is also added to the room (541). `enchant()` returns its argument (`com/planet_ink/coffee_mud/Libraries/TimsLibrary.java:1623`), and `addItem` does not detach an item from its previous owner (`com/planet_ink/coffee_mud/Locales/StdRoom.java:2399-2407`, `com/planet_ink/coffee_mud/MOBS/StdMOB.java:4491-4499`). The item therefore sits in two containers, and it is recorded in `maintained` twice (535 and 540).
16. `ItemGenerator.java:428`: the weighted pick starts at index 2. Items 0 and 1 can never be picked, and their weight falls to the last item (the value left after the loop). With exactly two items nothing is picked (420, 428).
17. `ItemGenerator.java:450`: when the duplicate cap is hit, `return` abandons the whole fill rather than trying another item.
18. `ItemGenerator.java:496`: `Resources.removeResource("HELP_" + area name)` is an unexplained side effect inside item generation. Its purpose is unverified.
19. `ItemGenerator.java:165`: with `maxitems > minitems`, the target count is never `minitems` (see Dice, `com/planet_ink/coffee_mud/Libraries/Dice.java:398-406`).
20. `MOBHunter.java:84`: `isHunting` looks for `"Thief_Assasinate"`, but the ability ID is `"Thief_Assassinate"` (`com/planet_ink/coffee_mud/Abilities/Thief/Thief_Assassinate.java:41`, and the cast at `MOBHunter.java:147`). `isHunting` is therefore always false, and the hunter re-targets on every cycle.
21. `MOBHunter.java:116-123`: nothing excludes the hunter itself. The origin room is scanned first (`com/planet_ink/coffee_mud/Libraries/MUDTracker.java:563-575` adds it first), and with no mask `maskCheck` returns true for any mob (`com/planet_ink/coffee_mud/Libraries/MUDZapper.java:5698-5703`). The hunter may therefore select itself. The search also takes any mob, not only players.
22. `MoneyChanger.java:116-121`: the distance-scaled rate `amt` is computed and then discarded; line 120 stores the flat `cut`.
23. `MoneyChanger.java:187-188` and `198`: `default=` is stored under the empty key `""`, which no currency matches, so the default is never used (`getMyCut` 141-145). Because any rate entry makes `rates` non-empty, currencies not listed are refused (`doIExchangeThisCurrency` 136). The guide lists `DEFAULT` as a supported parameter.
24. `NastyAbilities.java:133-135`: the cache check compares `numAllAbilities()` with a cache keyed on `numAbilities()`. `numAllAbilities` also counts racial and clan abilities (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:5004-5006` and `5026-5032`), so the cache is rebuilt on every tick for any mob with such abilities.
25. `NastyAbilities.java:116`: the level window uses `EXPRATE`, the experience-rate knob (`coffeemud.ini:401-406` describes it as how fast experience changes with level difference). Borrowing it couples two unrelated settings.
26. `NastyAbilities.java:85`: the parameter is `CHECKLEVEL`, while the guide's text says "LEVELCHECK" (`guides/refs/behav.html`).
27. `InstantDeath.java:125-126`: the item-owner path kills the owner without the immortal check that `killEveryoneHere` applies at line 89. The inconsistency is unexplained.
28. `Mime.java:125-128`: `fixSNameTo` passes the other-message text as the source message. The method is unused in this bucket.
29. `Mime.java:82-84`: the `&&` and `||` in the capture condition bind so that any Social message is captured, including a social the mime performed itself. Effect unverified.
30. `Mobile.java:99-125`: the leash cache can label rooms relative to whichever room was queried first (unverified; see section 7).
31. `MOBEater.java:75` and `62`: the constructor's chance of 50 and the separate hard-coded 5% swallow roll are two gates. The guide describes a single chance and gives the example `chance=5` (`guides/refs/behav.html`).
32. `MOBTeacher.java:53`: `noHLExpertises` is set (258) and never read. `MOBReSave.java:56`: `roomsReset` is declared static and never used.
33. `MovingRoom.java:275-277`: `tick` reads the message vectors before anything checks that `setParms` has run. The vectors start empty, so a behaviour that never received parameters would throw on `get(0)`.

**Documentation and comment drift**

- `guides/MUDChat.html` says the LLM reply prefix is a single quote. The code and data use a backtick (`MudChat.java:1144`, `resources/chat.dat:45`).
- `guides/refs/behav.html` describes Nanny's first hour as free (see 13), and advertises `DEFAULT` for MoneyChanger (see 23).
- The file headers of several classes name their authors and forks. Those names are not repeated here.

**Message typos seen in game text** (cosmetic, but visible to players): "I'm don't accept" (`Nanny.java:352`), "If are here for your" (`Nanny.java:651`), "that's an class" (`MOBTeacher.java:524`), "Heres your change" (`Nanny.java:675`).

**Multiplayer assumptions** (to drop for a single-player SUD): the three-PC rule in `MudChat.java:1609`, the per-speaker reply lock (1610, 1641), the NPC-speech suppression `talkDown` (1633-1636, 1705-1706), the group and rider gathering when someone enters a room (`Nanny.java:597-600`), and the victim save and restore that keeps NPC casters from starting fights among several players (`NastyAbilities.java:170-180`).

**Machinery a single-player game does not need**: the two-phase `okMessage`/`executeMsg` trailer plumbing, the parameter-string persistence (`Nanny.java:763-807`), and the database writes on every change (`Nanny.java:969-1024`).

## Takeaways for a Python SUD

- **Timers.** Borrow the `ActiveTicker` idea as a small class. Each behaviour has a countdown range and a percent gate, and the engine calls it each world tick:

  ```python
  @dataclass
  class Timer:
      lo: int; hi: int; chance: int          # ticks, percent
      countdown: int = 0
      def due(self, rng) -> bool:
          self.countdown -= 1
          if self.countdown >= 1:
              return False
          self.countdown = rng.randint(self.lo, self.hi)
          return rng.randint(1, 100) <= self.chance
  ```

  This matches `ActiveTicker.canAct` (`ActiveTicker.java:136-160`) without the mob-only checks. Fix the chance direction (`chance` means "fires this percent of the time") from the start.
- **Dialogue.** Keep the parts that do not depend on several players: keyword-indexed triggers, weighted replies with `random.choices(replies, weights=...)`, `$t`/`$n`/`$r` substitution, and group switches for conversation state. Use a plain dictionary from keyword to entries, not a bespoke expression tree, unless you need `&`, `|` and `~` in data. Drop the talk-lock, reply-lock and three-player rules.
- **Personality.** Store the trait groups as data (YAML or JSON) keyed by alignment. Derive a sketch from a stable NPC ID with `random.Random(stable_id)`, which is the same trick as `MudChat.java:907-910`.
- **Prices.** Keep formula strings with named variables (`level`, `value`, `condition`, `magical`), and evaluate them with a restricted expression evaluator. Avoid the positional `@x1`..`@x6` encoding, and compute the price once per transaction (the services compute it in both `okMessage` and `executeMsg`).
- **Stock and loot.** Build the candidate pool once from a data table, weight by `max_value / (value + 1)` as `ItemGenerator` does, and draw with `random.choices`. Give each item exactly one owner (a location or a container), so the double-placement bug cannot arise. Track stock with a set of item IDs, pruned against ownership.
- **Containment as a place.** A stomach or cargo hold can be a `Location` with an owner link and its own exits list. The escape path can be a plain command handler on that location, not a message interceptor.
- **Vehicles.** A route with stops, dwell and travel times, and an exit that is attached on arrival and detached on departure. In a single-player game this is an edge in the world graph toggled by the schedule; no door-text stubs are needed.
- **Custody and fees.** A small ledger class per customer (`item`, `since`, `paid`) with one check at the exit. Persist it in the save file, not in a parameter string.
- **Drop.** Multiplayer leashing of NPC speech, cross-room group gathering, per-change database writes, and CMMsg trailers. Use explicit before-and-after hooks such as `before_leave(player)` instead.
- **Keep.** The "activation by first offence" trap, the mana-scaled chance for NPC spells (`NastyAbilities.java:102-104`), and the idea that an NPC may cast without starting a fight. Those mechanics read well as small rules.

## Coverage

- **Read in full (all 21 files):** `MudChat.java` (1-1755), `Nanny.java` (1-1095), `ItemGenerator.java` (1-597), `MOBEater.java` (1-593), `MOBTeacher.java` (1-562), `MovingRoom.java` (1-545), `Mobile.java` (1-385), `MoneyChanger.java` (1-314), `Mime.java`, `InstantDeath.java`, `NastyAbilities.java`, `ItemMender.java`, `ItemIdentifier.java`, `ItemRefitter.java`, `MOBHunter.java`, `MobileAggressive.java`, `MOBReSave.java`, `MobileGoodGuardian.java`, `Mageness.java`, `MOBHelper.java` and `LinkedWeather.java`. For the small files the reads began at line 20, after the package and import lines.
- **Support classes read in full:** `StdBehavior.java`, `ActiveTicker.java`.
- **Support code checked by reading the relevant lines** (all under `com/planet_ink/coffee_mud/`): `Libraries/Dice.java` (roll, percentage), `Libraries/TimsLibrary.java` (`enchant`, 1497-1623), `Libraries/MUDZapper.java` (`maskCheck`, 5698-5703), `Libraries/MUDTracker.java` (radiant rooms 563-600; `beMobile` signature 898 only), `Locales/StdRoom.java` and `MOBS/StdMOB.java` (`addItem`, `getGroupLeader`, `numAbilities`, `numAllAbilities`), `Abilities/Thief/Thief_Assassinate.java` (ID), `Behaviors/CombatAbilities.java` (`setCombatStats`, 253-291), `Commands/Train.java` (NOTEACH, 373-378), `core/CMProps.java` (TIME_TICK, MILLIS_PER_MUDHOUR, PRACMAXPCT defaults).
- **Data and documentation consulted:** `resources/chat.dat` (header and bracket lines), `resources/lists.ini` (PERSONALITY_TRAITS), `resources/movingroom.xml`, `coffeemud.ini` (EXPRATE), `guides/MUDChat.html`, `guides/refs/behav.html` (entries for this bucket).
- **Not read:** the internals of `TrackingLibrary.beMobile`, `VeryAggressive`, `GoodGuardian`, `Aggressive.startFight`, the body of `CombatAbilities`, `MaskingLibrary`, `ExpertiseLibrary.postTeach`, and the LLM protocol library. Statements about them are limited to their call sites.
