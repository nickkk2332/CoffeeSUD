# NPC life and dialogue

[Handbook](README.md) · Feature family [N](feature-index.md#n) · Source audit:
[N: NPC behaviour and dialogue](../../audit/N-npc-behaviour-and-dialogue.md)

This chapter is about making a non-player character feel like a person who
lives somewhere and can be talked to. It covers the behaviours that give an NPC
a day (schedules, sleep, lookouts, wandering, patrols), a memory and an opinion
of each character, and jobs (escort, hire); and everything that makes it talk
(MudChat and its chat files, the TALK command and its menu, topics the player
learns, numbered menus, skill checks inside conversations, shopkeeper wording,
barks, ambient lines and overheard scenes). It ends with five worked examples:
a shopkeeper with a routine and greetings, an informant who teaches topics, a
hireling, an escort quest NPC and an ambient town crier.

Who it is for: world creators will use the parameter tables and the examples;
scripters will find how each system meets MOBPROG; engine developers get the
source file for every feature. Combat AI, bosses and encounters moved to the
[combat and encounters chapter](29-combat-and-encounters.md).

## In this chapter

- [How NPC life and dialogue fit together](#how-npc-life-and-dialogue-fit-together)
- [Attaching behaviours, properties and scripts](#attaching-behaviours-properties-and-scripts)
- [Part 1: NPC life](#part-1-npc-life)
  - [WHEN, TOD and HOURS: a condition on any behaviour](#when-tod-and-hours-a-condition-on-any-behaviour)
  - [Schedule: a daily routine](#schedule-a-daily-routine)
  - [Sleeper: sleeping NPCs woken by noise](#sleeper-sleeping-npcs-woken-by-noise)
  - [Sentry and alarms](#sentry-and-alarms)
  - [Wandering and patrols](#wandering-and-patrols)
  - [Warnings before aggression](#warnings-before-aggression)
  - [Memory: what an NPC remembers](#memory-what-an-npc-remembers)
  - [Prop_Affinity: an opinion of each character](#prop_affinity-an-opinion-of-each-character)
  - [Escortee: an NPC you take somewhere](#escortee-an-npc-you-take-somewhere)
  - [Hireling: paid help](#hireling-paid-help)
  - [Followers and companions](#followers-and-companions)
  - [Catch-up when an area wakes](#catch-up-when-an-area-wakes)
  - [GenBehavior: your own parameterised behaviour](#genbehavior-your-own-parameterised-behaviour)
  - [Inspecting and testing NPC life](#inspecting-and-testing-npc-life)
- [Part 2: Dialogue](#part-2-dialogue)
  - [Who hears whom](#who-hears-whom)
  - [MudChat](#mudchat)
  - [The chat file format](#the-chat-file-format)
  - [Chat tools: CHATTEST, CHATLINT, CHAT RELOAD](#chat-tools-chattest-chatlint-chat-reload)
  - [TALK and TALK_PROG](#talk-and-talk_prog)
  - [Topics: [[words]], ASK and hints](#topics-words-ask-and-hints)
  - [MPMENU: numbered choices](#mpmenu-numbered-choices)
  - [CHECK: skill checks in dialogue](#check-skill-checks-in-dialogue)
  - [Prop_NPCLines: shopkeeper and service wording](#prop_npclines-shopkeeper-and-service-wording)
  - [Barks and ambient lines: Sounder and Emoter](#barks-and-ambient-lines-sounder-and-emoter)
  - [Scene: exchanges the player overhears](#scene-exchanges-the-player-overhears)
  - [Replies in turn-based time: DIALOGUETIME](#replies-in-turn-based-time-dialoguetime)
  - [Dialogue with MOBPROG](#dialogue-with-mobprog)
- [Reference](#reference)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [What is not in CoffeeSUD](#what-is-not-in-coffeesud)
- [See also](#see-also)

## How NPC life and dialogue fit together

An NPC is a mob that is not a player character. Everything in this chapter is
attached to it as one of four kinds of thing:

| Kind | What it is | Examples here |
| --- | --- | --- |
| Behaviour | Java code that ticks and hears messages. Added with a parameter string. Reference: [24](24-behaviors-and-masks.md). | Schedule, Sleeper, Sentry, Memory, Escortee, Hireling, MudChat, Sounder, Emoter, Scene, Patroller, Mobile |
| Property (effect) | A permanent effect on the NPC or its area. Reference: [23](23-properties-reference.md). | Prop_Affinity, Prop_NPCLines |
| Script | A MOBPROG program on the `Scriptable` behaviour (or JavaScript). See [04](04-mobprog.md). | TALK_PROG, SPEECH_PROG, FUNCTION_PROG, MPMENU |
| Data file | A text file a behaviour reads. | chat files for MudChat, bark tables, scene files |

The pieces divide the work like this:

- **Where is the NPC, and what is it doing?** Schedule (routine), Sleeper,
  Sentry, Patroller, Mobile, Escortee, Hireling.
- **What does it know about the player?** Memory (facts about what each
  character did) and Prop_Affinity (a single opinion number).
- **What does it say when spoken to?** MudChat (pattern replies from a file),
  scripts (SPEECH_PROG, TALK_PROG), MPMENU (numbered choices), and for
  shopkeepers and the like Prop_NPCLines.
- **What does it say unprompted?** Sounder and Emoter (barks and ambience),
  MudChat tick entries, Scene (a staged exchange).

Pick the simplest tool that does the job. A guard that grunts at arrivals is a
Sounder line. A villager that answers "hello" and "weather" is MudChat. An NPC
that gives a quest, or answers differently once the quest is done, is MOBPROG
(or MudChat guarded by a script condition). Behaviours can be combined freely on
one NPC; the worked examples combine five or six.

**CoffeeSUD and inherited CoffeeMud.** MudChat, Sounder (old form), Emoter,
Patroller, Mobile, Hireling (basic form), Aggressive and Prop_ClosedDayNight
are inherited from CoffeeMud. Schedule, Sleeper, Sentry, Memory, Escortee, Scene,
GenBehavior, Prop_Affinity, Prop_NPCLines, the WHEN/TOD/HOURS gate, TALK, topics,
MPMENU, CHECK, bark tables, DIALOGUETIME, area catch-up, CHATTEST/CHATLINT and
the chat-file guards, `&` replies and `$@!` groups are CoffeeSUD additions (feature
ids N1 to N29 in the [feature index](feature-index.md#n)). Where a CoffeeSUD
addition extends an inherited behaviour (Patroller steps, Hireling terms, Sounder
barks, Aggressive warnings) the extension is marked.

Single-player notes: there are no other players to talk to, so "the speaker" is
always your active character (or a party member you CONTROL). MudChat answers
characters the game treats as player-controlled, never other NPCs.

## Attaching behaviours, properties and scripts

**In game (builder mode).** `CREATE MOB GenMob` (see [21](21-mobs.md)), then
`MODIFY MOB <name>`. The editor lists numbered fields. Two of them matter here.

- The **Behaviors** field prompts `Enter a behavior to add/remove (?)`. Type a
  behaviour ID (`Schedule`) and the editor then asks `Enter any behavior
  parameters (?)`; type the parameter string, or `?` for the behaviour's help.
  Typing the ID of a behaviour the NPC already has removes it. `?` at the first
  prompt lists every behaviour. For `Scriptable` the editor opens the script
  editor instead of a one-line prompt.
- The **Effects** field (CoffeeMud calls properties effects) prompts `Enter an
  effect to add, -ID to remove, =ID or its number to edit, ?P for the
  properties that fit`. Type `Prop_Affinity`, then its text.

At run time a script can add or remove behaviours with `MPBEHAVE <behavior>
<mob> <parms>` and `MPUNBEHAVE <behavior> <mob>` ([27](27-mobprog-commands.md#mpbehave)).
Quests can do the same through GIVE BEHAVIOR ([08](08-quests.md)).

**In a world file** the behaviour and its parameters are part of the mob's
`.cmare` XML; edit them in game and `SAVE`, rather than by hand, unless you know
the XML ([02](02-world-workflow.md)).

**Asking for help.** `AHELP BEHAVIOR_SCHEDULE`, `AHELP BEHAVIOR_MUDCHAT`,
`AHELP PROP_AFFINITY` and the like show the shipped help text; the same text is
in [tool-reference.md](tool-reference.md).

**Parameter strings.** Each behaviour parses its own parameter string. Common
rules:

- `KEY=value` pairs are separated by spaces. A value with spaces needs double
  quotes: `GREET="Welcome back, $n."`.
- Several behaviours (Schedule, Patroller, Sounder, Emoter, Scene, Hireling)
  also use `;` to separate entries. A `;` inside a quoted value is safe in
  Schedule (which splits outside quotes); in the others avoid it in text.
- Parameter matching of keywords is case-insensitive; text is kept as written.
- A key a behaviour does not know is ignored, but `STAT BEHAVIORS` tells you
  which keys its help does not name (see
  [Inspecting and testing](#inspecting-and-testing-npc-life)).

## Part 1: NPC life

### WHEN, TOD and HOURS: a condition on any behaviour

Source: [BehaviorWhen](../../CoffeeMud/com/planet_ink/coffee_mud/core/BehaviorWhen.java).
Help: `AHELP BEHAVIOR_WHEN`. Fixture: [n1-when.js](../../tools/fixtures/fixes/n1-when.js).

Any behaviour accepts three extra keys in its parameter string. While the
condition does not hold the behaviour neither ticks nor hears messages.

| Key | Meaning |
| --- | --- |
| `WHEN="<zapper mask>"` | A [zapper mask](24-behaviors-and-masks.md) tested on the **host** (the mob, item, room, area or exit the behaviour is on), for example `WHEN="+WEATHER -THUNDERSTORM"` or `WHEN="-NPC"`. |
| `TOD=DAWN,DAY,DUSK,NIGHT` | One or more times of day on the host's clock, comma separated. |
| `HOURS=a-b` | Hours of the day on the host's clock. If `a` is greater than `b` the range runs through midnight (`HOURS=22-5`). |

All keys present must hold. For the opposite case give the NPC a second copy of
the behaviour (you cannot add the same behaviour ID twice through the editor,
so use two different behaviours, a GenBehavior, or a script).

```text
MobileAggressive TOD=NIGHT -NPC
Mobile TOD=DAY min=20 max=40
Aggressive WHEN="+WEATHER -THUNDERSTORM"
```

Rules and exceptions:

- `Scriptable` (MOBPROG) is **not** gated: its parameters are the script. Gate
  inside the script instead, or use `Prop_When` ([23](23-properties-reference.md#prop_when))
  to wrap a property or behaviour, or the TIME_PROG/EVERY_PROG triggers.
- RoomStates, TidalRoom and Floodable use `WHEN=` for their own purposes and
  are not gated.
- A generic behaviour ([GenBehavior](#genbehavior-your-own-parameterised-behaviour))
  is gated.
- Do not use `TOD=` or `HOURS=` to say when a Sleeper sleeps. They would switch
  the whole behaviour off (it could not even wake); use Sleeper's own `SLEEPS=`.
- Schedule's entries have their own time-of-day grammar; do not combine it with
  `TOD=` unless you want the whole routine suspended.
- `STAT BEHAVIORS <mob>` says whether the condition is met ("it is on now" or
  "off now").

### Schedule: a daily routine

Source: [Schedule](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Schedule.java).
Reference: [24 Schedule](24-behaviors-and-masks.md#schedule). Help: `AHELP BEHAVIOR_SCHEDULE`.
Fixture: [n3-schedule.js](../../tools/fixtures/fixes/n3-schedule.js). Hosts: mobs only.

Schedule gives an NPC a day over several places. The parameter string is a list
of entries separated by `;`:

```text
WHEN: PLACE [ACTIVITY ...] [SAY text | EMOTE text]
```

The **first entry whose WHEN holds** is in force. The NPC walks to its place,
does its activity, and goes back to it after a fight. With no entry in force
the NPC stays where it is.

**WHEN** is the first word before the colon, optionally followed by qualifiers
that must all hold:

| Part | Meaning |
| --- | --- |
| `DAWN`, `DAY`, `DUSK`, `NIGHT` | Time of day on the NPC's area clock. |
| `7` or `2-5` | An hour, or an hour range (through midnight if the first is larger: `22-5`). |
| `DAYS=a-b` | Days of the month (or one day). |
| `SEASON=name` | A season name (SPRING, SUMMER, FALL, WINTER). |
| `WEATHER=name` | A weather name at the NPC's room: CLEAR, CLOUDY, WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT, COLD or FOG. |
| `MASK="zapper mask"` | A mask tested on the NPC itself. |

Example of a qualified WHEN: `DAY SEASON=WINTER: Market#5 SHOP`. Put the more
specific entries before the general ones.

**PLACE** is read up to the first activity word, so a room ID may contain
spaces (`Brenn Village#4`):

| Place | Meaning |
| --- | --- |
| A room ID | `Mill#2`, `Brenn Village#4`. Must exist or the log shows `Schedule: no room ...`. |
| `HOME` or `START` | The NPC's start room (where it was placed). |
| `ANY:<room mask>` | A room of the NPC's area that fits the zapper mask; the NPC keeps its room if it already fits, otherwise one is picked at random. |

**ACTIVITY** words (any number, then optional SAY/EMOTE last):

| Activity | Meaning |
| --- | --- |
| `SLEEP [item]` | Lie down on the named furniture if it is in the room, else on the floor. Re-checked every ten ticks, so a woken NPC goes back to sleep. |
| `SIT [item]` | Likewise, sitting. |
| `WANDER n` | After arriving, now and then take a step but stay within n rooms of the place. Not used while sleeping or sitting. |
| `SHOP` | The NPC trades only while it is here with this entry in force. Elsewhere, on the way, or asleep it refuses LIST, BUY, SELL, VALUE, BID, DEPOSIT, WITHDRAW, BORROW and VIEW, saying the `SHOPMSG` (or "looks asleep" if it sleeps). |
| `WORK "emote\|emote"` | Now and then (about 15 percent of ticks) show one of the `\|`-separated emotes ("pours grain into the hopper"). |
| `LOCK` | Close and lock the place's lockable doors on arrival; unlock and open them when the entry ends. |
| `HIDE` | Be hidden while there. |
| `SAY text` or `EMOTE text` | Said or emoted on arriving. Must be last; it runs to the end of the entry. |

A separate entry with no colon sets the message for closed hours:

```text
SHOPMSG="The mill is shut till morning."
```

**Example** (from the help text):

```text
DAWN: Mill#2 WORK "pours grain into the hopper|wipes flour from his hands"; DAY: Market#5 SHOP; DUSK: Inn#1 SIT "stool" SAY Long day.; NIGHT: Mill#9 SLEEP "straw bed" LOCK
```

How it moves:

- It suspends `Mobile` (and other mobility behaviours) while walking, and uses
  the map's path finder. If there is no path it is placed at the destination as
  if by a route the map does not show.
- **If no player character is in the area of either the current or the
  destination room, the NPC is put at its place at once** instead of walking.
  So a restored game, a time jump (`WAIT 8 HOURS`) or an area nobody is in
  always finds it where the clock says.
- While an NPC is **Alerted** (see [Sentry](#sentry-and-alarms)) or in combat,
  Schedule waits.
- `STAT($i SCHEDULE)` in a script reads the entry in force (see
  [28](28-mobprog-functions.md#stat)); `STAT BEHAVIORS` shows it too.
- Doors locked by LOCK are remembered in a saved game, so unlocking works after
  a restore.

Design notes:

- Prefer Schedule to a TIME_PROG that fires `MPTRACKTO` once an hour: TIME_PROG
  fires only on the hour and must cope with fights and failed paths itself.
- The older inherited property `Prop_ClosedDayNight` (open at the start room,
  closed at `HOME=`, with SLEEP, SIT, LOCKUP, SHOPMSG, HOURS) still exists
  ([23](23-properties-reference.md#prop_closeddaynight)); use Schedule when you
  need more than two places.
- Test with `WAIT UNTIL NIGHT` and `WAIT UNTIL DAWN` (see `AHELP WAIT`), and
  look at the NPC with `STAT BEHAVIORS`.

### Sleeper: sleeping NPCs woken by noise

Source: [Sleeper](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Sleeper.java).
Reference: [24 Sleeper](24-behaviors-and-masks.md#sleeper). Fixture: [n4-sleeper.js](../../tools/fixtures/fixes/n4-sleeper.js).

An NPC that lies asleep (always, or at a time of day) and can be woken by what
the player does. LOOK at it says "is sleeping soundly", or "stirs" when
something nearly woke it.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `ALWAYS` or `SLEEPS=DAWN\|DAY\|DUSK\|NIGHT\|a-b` | always | When it sleeps, on the area clock. Hours may wrap midnight (`22-6`). |
| `BED="item"` | none | Sleep on this furniture if it is in the room. |
| `WAKEON=ATTACK,NOISE,ENTER,LIGHT,TOUCH` | all five | What may wake it. |
| `WAKECHANCE=n` | 50 | Percent chance per event. |
| `DEEP` | off | Only an attack wakes it. |
| `GRUMBLE="text"` | none | Said on waking. |
| `AWAKEFOR=n` | 20 | Ticks it stays up before it sleeps again. |
| `ALARM` | off | On waking, raise the alarm (see [Sentry](#sentry-and-alarms)). |

What each wake source means:

- **ATTACK**: any malicious act aimed at it wakes it at once (also with `DEEP`).
- **TOUCH**: anything done to it except looking, examining or sniffing:
  the chance is `WAKECHANCE` doubled (at most 100).
- **ENTER**: someone entering its room. A sneaking or hidden entrant takes off
  their Sneak or Hide proficiency (at least 50 percent) from the chance.
- **LIGHT**: a character carrying a light adds 20 to an entering chance.
- **NOISE**: a fight in its room (chance + 20), a noise message, or a weapon
  attack in the room; a fight next door counts for half of `WAKECHANCE`.

A failed roll makes it "stir" (visible on LOOK) without waking. `ROUSED` (a stat
the alarm code sets) keeps it up for AWAKEFOR ticks.

```text
Aggressive
Sleeper ALWAYS WAKEON=NOISE,ENTER WAKECHANCE=40 GRUMBLE="Who disturbs Grok?" ALARM
```

(`Aggressive` and `Sleeper` are two behaviours on the same ogre.) A thief who
sneaks past may get through; a fight next door may wake him.

Sleeper and Schedule can coexist, but pick one source of sleep per NPC unless
you mean it: Schedule's `SLEEP` is time-of-day only and does not react to noise
by itself; Sleeper reacts to noise but only knows `SLEEPS=`.

### Sentry and alarms

Sources: [Sentry](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Sentry.java),
[NpcAlarm](../../CoffeeMud/com/planet_ink/coffee_mud/core/NpcAlarm.java),
[Alerted](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Misc/Alerted.java).
Reference: [24 Sentry](24-behaviors-and-masks.md#sentry). Fixture: [n5-sentry.js](../../tools/fixtures/fixes/n5-sentry.js).

A lookout. Each tick, if it is awake and can see, it looks in its own room and
the adjacent rooms for **intruders**: a player character or a follower of one
(or whoever `INTRUDERS="mask"` fits). Visible intruders in its room are noticed
at once (next door too, if that room is lit); hidden or sneaking ones are
noticed with `SPOT` percent a tick, less their Sneak/Hide proficiency, and half
that next door. Noticed, or when the sentry itself is attacked, it raises the
alarm.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `SPOT=n` | 25 | Percent per tick to notice hidden or sneaking intruders. |
| `INTRUDERS="mask"` | player characters and their followers | Zapper mask on who counts. |
| `ALARM="RADIUS=n MASK=... MSG=..."` | radius 6, everyone, "Intruders!" | See below. |
| `ALERTTICKS=n` | 40 | How long roused NPCs stay alert; also the sentry's quiet time after raising an alarm. |
| `ALARMITEM="item name"` | none | An item in the sentry's room that anyone (the player too) can PUSH or PULL to raise the alarm. |

`ALARM` text: `RADIUS=n` is how many rooms the alarm carries, `MASK=` (to the end or
to `MSG=`) is a zapper mask on who hears it, `MSG=` (to the end or to `MASK=`)
is the shout. Both MASK and MSG may contain spaces.

On an alarm the shout is a NOISE message in the alarm room (an
`EXECMSG_PROG` script can catch it), and every NPC within the radius that fits
the mask is woken (a Sleeper stays up) and given the **Alerted** effect for
`ALERTTICKS`. An Alerted NPC heads for the alarm room, or for the intruder if
known, and attacks them or their party; its Schedule, Patroller and Mobile wait
while it lasts. The sentry raises the alarm at most once per `ALERTTICKS`.

```text
Sentry SPOT=30 ALARM="RADIUS=6 MASK=-NAMES +bandit MSG=Intruders! To arms!" ALERTTICKS=40 ALARMITEM="a brass gong"
```

A Sleeper with `ALARM` and no Sentry uses the defaults; with a Sentry on the same
NPC it uses the Sentry's `ALARM=`. The design lets the player silence the
lookout first (kill, charm or sneak) and take the camp tent by tent.

### Wandering and patrols

Wandering and patrol behaviours are inherited; CoffeeSUD extended Patroller.

**Mobile** (`AHELP BEHAVIOR_MOBILE`, [24](24-behaviors-and-masks.md#mobile)):
`min=10 max=20 chance=75 wander opendoors leash=3 -ALL +CITY`. Time between
moves, percent chance each time, `wander` (needed to leave its area),
`opendoors`, `leash=n` (stay within n rooms of home) and locale restrictions
(`-ALL +CITY`, `-UNDERWATER`). A mob will not leave a room that holds a visible
archon/builder with the CMDMOBS flag.

**Patroller** (`AHELP BEHAVIOR_PATROLLER`, [24](24-behaviors-and-masks.md#patroller)).
Source: [Patroller](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Patroller.java).
Fixture: [n16-waypoints.js](../../tools/fixtures/fixes/n16-waypoints.js).
Parameters: `min=` `max=` `chance=` `rideok=true|only` `diameter=20`, then `;` and a
path of directions or room IDs. `RESTART` (or `REPEAT`) restarts the path;
without it the NPC retraces its path back. `.` pauses a step. A room-ID path is
tracked automatically even if not adjacent. CoffeeSUD adds:

| Step | Meaning |
| --- | --- |
| `WAIT=n` | Wait n ticks. |
| `DO=command` | Do a command (`DO=say Gate secure.`, `DO=lock door`). |
| `EMOTE=text` | Emote. |
| `IF=mask` | Go on only if the zapper mask fits the patroller; otherwise jump to the next `RESTART` (or the start). |
| `ROUTE=NAME:first step;...` | Start a named route that lasts to the next `ROUTE=`. The route named for the time of day (DAWN, DAY, DUSK, NIGHT) is walked, else `ROUTE=DEFAULT` (or `ALL`), else the first; it restarts from its start when the route changes. |
| `RESUME=true` (a parameter) | After a fight, walk back to where it left its route first. |

WAIT, DO, EMOTE and IF steps are not walked back on a route with no RESTART.

```text
min=3 max=3;Gate#1;WAIT=5;DO=say Gate secure.;Wall#4;EMOTE=peers over the battlements.;RESTART
min=3 max=3;ROUTE=DAY:Gate#1;Wall#4;RESTART;ROUTE=NIGHT:Barracks#1;Barracks#2;RESTART
```

**Choosing between Schedule, Patroller and Mobile.** Mobile is aimless
wandering. Patroller walks a path in a loop on a timer. Schedule moves between
places by the clock and does something there. A day-and-night guard is
usually a Patroller with `ROUTE=DAY`/`ROUTE=NIGHT`, or a Schedule with
`WANDER` and `SLEEP`. Do not put two independent movement controllers on one NPC
without a reason; Schedule holds Mobile off while it walks, but a Patroller
and a Schedule would fight (an Alerted NPC pauses both).

**Other inherited relatives.** `Follower` (follow any mob that leaves the room;
`group`, `nofollowers`, `wander`, `name=`), `Guard` (come to the aid of anyone
attacked in the room), `ProtectedCitizen` (scream for guards in combat), `Vagrant`
(nap anywhere), `Beggar`. See [24](24-behaviors-and-masks.md).

### Warnings before aggression

Source: [AggroWarning](../../CoffeeMud/com/planet_ink/coffee_mud/core/AggroWarning.java).
Fixture: [n17-warning.js](../../tools/fixtures/fixes/n17-warning.js). Reference:
[24 Aggressive](24-behaviors-and-masks.md#aggressive).

`Aggressive`, `VeryAggressive` and `MobileAggressive` accept a warning so the
player gets a chance to react.

| Parameter | Meaning |
| --- | --- |
| `WARN="line\|line"` | Warning lines, one per tick, said before it attacks. |
| `WARNTICKS=n` | How many ticks to warn (the number of lines by default). |
| `BACKOFF=true` | A character who sits down, or holds no weapon, is not attacked while they stay so, and is warned afresh if they stop. |
| `NEAR=1` | The warning is heard in neighbouring rooms too. |

```text
Aggressive WARN="The wolf bares its teeth.|The wolf snarls and crouches!" WARNTICKS=2 BACKOFF=true
```

The attack comes only if the intruder is still there when the warning ends.

### Memory: what an NPC remembers

Source: [Memory](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Memory.java).
Reference: [24 Memory](24-behaviors-and-masks.md#memory). Fixture: [n6-memory.js](../../tools/fixtures/fixes/n6-memory.js).

Memory records, per **player character** (by name), what that character did to
or near this NPC. Each fact is a number (or a date) and is also a script
variable of the NPC called `MEM_<FACT>_<NAME>`.

| Fact | Counts |
| --- | --- |
| `MET` | Times the character came into the NPC's room. `FIRSTMET` and `LASTMET` are the game dates (text). |
| `ATTACKED` | Fights the character started with it (one per fight, not per blow). |
| `HURT` | Damage the character did to it. |
| `HEALED` | Heals the character cast on it. |
| `GAVE` | Total value of items given to it. |
| `STOLE` | Thefts from it. |
| `KILLEDKIN` | Deaths of NPCs that fit `KIN="mask"` caused by the character, seen in the NPC's room. |
| `TALKED` | Things the character said to it or to the room (speech with it as target or untargeted). |
| `TRACK=NAME:regex` | Your own fact `NAME`: counts each visible action text of the character that matches the regex (case-insensitive). `TRACK1=` to `TRACK9=` give more. |

Parameters:

| Parameter | Meaning |
| --- | --- |
| `KIN="mask"` | Who counts as kin for `KILLEDKIN` (and the default of nothing else). |
| `SHARE=AREA` or `SHARE=mask` | Pass what it learns to the other Memory NPCs of its area (those the mask fits): rob one villager and the village knows. |
| `FORGET=30d` | A fact fades this long after it last grew: `d` game days, `h` game hours, or a game time expression. |
| `HOSTILEIF=cond` | It attacks the character on sight while the condition holds. |
| `GREETIF=cond GREET="text"` | When the character enters and the condition holds, it says the text once (`$n` is their name) until they leave. |
| `REFUSEIF=cond REFUSE="text"` | While the condition holds it refuses to buy, sell, list, value, bid, view or teach for the character, saying the text (default "I will not deal with you."). |

A condition is `FACT>N`, `FACT>=N`, `FACT=N`, `FACT<N`, `FACT<=N` or `FACT!=N`,
joined with `&` (and) and `|` (or); `&` binds tighter than `|`.

Read a fact from a script as `VAR(<npc> MEM_ATTACKED_<name>)`, for example
`VAR($i MEM_ATTACKED_$n > 0)`, or `$<$i MEM_GAVE_$n>` in text. `MEMORY(npc who
MET|COUNT|LASTSEEN|DAYSSINCE|LASTTOPIC)` is a separate, older record of meetings
and the last TALK topic ([28](28-mobprog-functions.md#memory)).

```text
KIN="-NAMES +villager" SHARE=AREA HOSTILEIF=KILLEDKIN>0 REFUSEIF=STOLE>0 REFUSE="Thief! Out!" GREETIF=HEALED>0 GREET="Welcome back, $n." FORGET=30d
```

Notes: facts are kept per NPC; use `SHARE` for village-wide memory. For group
grudges that apply to a whole faction, use factions ([09](09-society.md)).
`FORGET` is lazily applied when a condition is read.

### Prop_Affinity: an opinion of each character

Sources: [Prop_Affinity](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Affinity.java),
[NpcAffinity](../../CoffeeMud/com/planet_ink/coffee_mud/core/NpcAffinity.java).
Reference: [23 Prop_Affinity](23-properties-reference.md#prop_affinity). Fixture: [n15-affinity.js](../../tools/fixtures/fixes/n15-affinity.js).

Memory remembers *what happened*; Prop_Affinity keeps one *opinion number* per
character, moved by rules. Put it on an NPC for its own opinion, or on an
**area** for the whole village's opinion (shared by every NPC of the area that
has no Prop_Affinity of its own). Targets: mobs and areas.

The number starts at `START` (0). Bands:

| Band | Range |
| --- | --- |
| HOSTILE | below -50 |
| COLD | -50 to -1 |
| NEUTRAL | 0 to 24 |
| FRIENDLY | 25 to 74 |
| DEVOTED | 75 and up |

Parameters (all optional):

| Parameter | Meaning |
| --- | --- |
| `START=n` | Initial value. |
| `DECAY=n` | The value moves n points toward 0 each game day. |
| `GIFT(item)=+n` | An item given whose name contains the words, or that a `+/-` mask fits. `GIFT=+n` with no item matches any item. |
| `BRIBE=n` | Points per unit of money given. |
| `TALK(word)=+n` | A word said to it (via SAY to it, or TALK ABOUT): at most once per game day per rule. |
| `QUESTWIN(quest)=+n` | Each win of that quest by the character (counted when the value is next read). |
| `ATTACKED=-n` | An attack on it, at most once per game hour. |
| `STOLE=-n` (or `CAUGHT_STEALING`) | A theft from it. |
| `KILLED(mob)=-n` | A killing it sees of a mob the mask or name fits. |
| `SHOPFACTOR=BAND:factor,...` | Multiply shop prices by band: `FRIENDLY:0.9,DEVOTED:0.8`. |
| `REFUSE=BAND` | At or below that band it refuses to trade (BUY, SELL, LIST, VALUE, BID, VIEW, DEPOSIT, WITHDRAW, BORROW). |
| `REFUSEMSG="text"` | What it says when refusing (default "I won't deal with you."). |

```text
GIFT(rare ore)=+10 QUESTWIN(The Smith's Blade)=+30 ATTACKED=-40 SHOPFACTOR=FRIENDLY:0.9,DEVOTED:0.8 REFUSE=HOSTILE
```

Scripts read it with `AFFINITY($i $n)` (the number) or `AFFINITY($i $n BAND)` (the
band), compare with `AFFINITY(npc who > 25)` or `AFFINITY(npc who BAND ==
FRIENDLY)` ([28](28-mobprog-functions.md#affinity)), and change it with
`MPAFFINITY <npc> <character> +n|-n|=n` ([27](27-mobprog-commands.md#mpaffinity)).
Prop_NPCLines masks accept `[AFFINITY >=25]` or `[AFFINITY FRIENDLY]` (see
[Prop_NPCLines](#prop_npclines-shopkeeper-and-service-wording)); a MudChat guard
may use `?AFFINITY($i $t > 25)`. Hireling's `LOYAL=` also reads it.

The values are script variables `AFFINITY_<CHARACTER>` on the NPC (or the area),
so a campaign save keeps them. They are not shown by the FACTIONS command.

### Escortee: an NPC you take somewhere

Source: [Escortee](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Escortee.java).
Reference: [24 Escortee](24-behaviors-and-masks.md#escortee). Fixture: [n20-escortee.js](../../tools/fixtures/fixes/n20-escortee.js).

An NPC that has to be brought to a destination. It does nothing until it is
**escorted**: its leader is whoever it is following when the escort begins
(typically a script on it runs `MPFORCE $i follow $n` after the player agrees).
Until then it waits.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `DEST=room id` | required | The destination. The end of an ID also matches (`Riverford#1`). Arriving there ends the escort. |
| `MODE=FOLLOW\|LEAD` | FOLLOW | FOLLOW: it follows its leader and never walks on its own. LEAD: it stops following and walks to `DEST` itself, a step a tick; the party must keep up. |
| `WAITFOR=LEADER` | off | While the leader is not in its room it does not go on. |
| `WAITCALL=n` | 3 | Ticks the leader is away before it calls. |
| `WAITMSG="text"` | "Wait for me!" | What it calls. |
| `PANIC=FLEE\|HIDE\|COWER` | none | When attacked it flees, or dives for cover or cowers (both stop the fight). |
| `AVOID=id,id` | none | Rooms it will not enter ("I won't go there."). |
| `TALK=room:text`, `TALK1=` to `TALK9=` | none | A remark the first time it comes to that room. |
| `ONARRIVE="MOBPROG lines"` | none | Run on arrival, `;` between lines, the NPC as host and its leader as `$n`. |
| `ONDEATH="..."` | none | Run if it dies. |
| `TIMEOUT=n` and `ONTIMEOUT="..."` | none | After n ticks of escort, run the lines and end the escort. |

Lines in `ONARRIVE`, `ONDEATH` and `ONTIMEOUT` are MOBPROG commands, for
example `say Thank you, friends!;mpsetvar $n ESCORTED 1`, `mpcallfunc ...`, or
`mpquestwin $n <quest>` ([27](27-mobprog-commands.md)). On arrival the NPC stops
following. The leader, completion, tick count and remarks already made are kept
in a saved game.

```text
DEST=Riverford#1 MODE=LEAD WAITFOR=LEADER PANIC=COWER TALK="Bridge#3:They say trolls live under here." ONARRIVE="say Thank you, friends!;mpsetvar $n ESCORTED 1"
```

### Hireling: paid help

Sources: [Hireling](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Hireling.java),
[Hire](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Hire.java).
Reference: [24 Hireling](24-behaviors-and-masks.md#hireling). Fixture: [n21-hireling.js](../../tools/fixtures/fixes/n21-hireling.js).

An NPC that works for money. The parameter string starts with the basic terms
`price;minutes` (optionally `;zapper mask` on who may hire), followed by
CoffeeSUD `KEY=value` terms anywhere in the string (quote values with spaces).

Basic: `10000;30` means 10000 base-currency units for 30 minutes of (real-time)
service; the offer shows the term converted to game "hours". More money given
extends the time pro rata. When time runs out, the NPC
says goodbye and returns to its start room.

How the player hires:

```text
hire larry
hire larry pay
```

`HIRE <mob>` asks and, for a Hireling not already working, shows its terms as a
numbered menu (Hire / Not now). `HIRE <mob> PAY` pays its price at once, exactly as a GIVE
would. The player can also GIVE money to it, or say one of its keywords
(`hire`, `work`, `time`, ...) to hear its terms. Once hired it follows the
employer, and the employer controls it with `ORDER <mob> <command>`. Saying
"skills" makes it list its abilities; the employer saying "fired" to it
(`SAYTO larry fired`) ends the job.

CoffeeSUD terms:

| Key | Meaning |
| --- | --- |
| `KEYWORDS=WORD,WORD` | Words that make it state its terms. Default `HIRE,HIRELING,WORK,WORKS,TIME,AVAILABLE`. |
| `MSG_OFFER="..."` | Its offer. `@x1` is the price, `@x2` the term. |
| `MSG_HIRED="..."` | When hired. `@x1` is the term, `@x2` its skills. |
| `MSG_REFUSE="..."` | Refusing a payer the mask rejects. |
| `MSG_EXPIRE="..."` | When the job ends. |
| `MSG_NOORDER="..."` | When it refuses an order (see `REFUSE`). |
| `CONTRACT=` | `MINUTES` (the default, the second number of the basic terms), `DAYS` (that many game days), `QUEST:name` (until the employer's quest `name` is won, failed or dropped), `UNTILFIRED` (until the employer says "fired" to it). |
| `UPKEEP=n` | Wages a game day, taken from the employer. After two unpaid days it leaves ("No wages, no work."). |
| `FLEEHP=n` | Below n percent of its hit points in a fight it quits ("This isn't worth it!"), unless its opinion of the employer (Prop_Affinity) is at least `LOYAL`. |
| `LOYAL=n` | Affinity at which it stays in a fight (default 25). |
| `REFUSE=CMD,CMD` | Order verbs it will not take. |

`Prop_NPCLines` rewords the stock hireling lines too (keys FORHIRE, HIRED,
PRICE, ONTHEJOB, TIMEUP, GOODBYE, WONTWORK, SKILLS). The TALK menu offers
"Hire" for a Hireling ([TALK](#talk-and-talk_prog)).

```text
500;0;CONTRACT="QUEST:goblinwar" UPKEEP=5 MSG_HIRED="My blade is yours till the goblin king falls."
10;60;CONTRACT=UNTILFIRED FLEEHP=50 LOYAL=30
```

The contract, wages paid and unpaid days are kept in a saved game.

### Followers and companions

CoffeeSUD has no general "companion" behaviour (feature N13 is deferred; see
[What is not in CoffeeSUD](#what-is-not-in-coffeesud)). The ways an NPC ends up
with the party are:

- **Hireling** above: it follows its employer and takes ORDERs.
- **A script** that runs `MPFORCE $i follow $n` to recruit after a conversation
  or quest step. The party chapter explains
  recruited characters, CONTROL and AS ([31](31-saves-and-party.md)).
- **Escortee** with `MODE=FOLLOW`: follows its leader to a destination.
- **Follower** (inherited behaviour): follows any mob that leaves the room;
  with `group` it joins the target's group. Use its `name=` and mask to limit
  whom.

Followers in the player's group are never picked by a bare `TALK`.

### Catch-up when an area wakes

Sources: [AreaCatchUp](../../CoffeeMud/com/planet_ink/coffee_mud/core/AreaCatchUp.java).
Fixture: [n8-catchup.js](../../tools/fixtures/fixes/n8-catchup.js). Help: `AHELP CATCHUP`.

An area that no player has visited for its PASSIVEMINS game minutes goes
PASSIVE: wanderers (Mobile and its family), aggressive mobs and moving rooms
stop. Scripts, Prop_ClosedDayNight and most behaviours go on ticking.
When a player comes back the area wakes and everything in it **catches up at
once** on what it would have done:

- Mobile wanderers take the steps they would have (at most 10).
- Patroller takes the steps of its route it missed while it did not tick.
- **Schedule** puts NPCs at their current entry's place.
- **Sleeper** is asleep or up as the hour says.
- `Prop_ClosedDayNight` keepers are at home or at work.
- A script's `FUNCTION_PROG CATCHUP` runs with `$g` the number of ticks slept,
  on scripts of mobs, items, rooms and areas, and generic behaviours.
- A JavaScript file of `JSBehavior` or `Prop_JScript` may define
  `catchUp(host, ticks)` ([05](05-javascript.md)).

Wanderers go first and routines last, so a routine has the last word.

Controls: `CATCHUP=OFF` in coffeemud.ini or the world's world.ini turns it off
everywhere; the area tag `NOCATCHUP` leaves one area as it was
([world-settings.md](world-settings.md)). Catch-up is about the *NPC's place
and state*; it does not replay events. Put your own recovery in
`FUNCTION_PROG CATCHUP`:

```text
FUNCTION_PROG CATCHUP
if inroom($i Market#5)
  mpsetvar $i OPEN 1
endif
~
```

(`WAIT` in turn-based play moves every area, so waiting also advances routines.)

### GenBehavior: your own parameterised behaviour

Sources: [GenBehavior](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/GenBehavior.java).
Reference: [24 GenBehavior](24-behaviors-and-masks.md#genbehavior). Fixture: [n7-genbehavior.js](../../tools/fixtures/fixes/n7-genbehavior.js).

When many NPCs need the same scripted routine with different numbers, define a
**generic behaviour** instead of copying a script. In builder mode:

```text
CREATE BEHAVIOR TollKeeper
```

opens an editor with: NAME, HELP (what `AHELP TOLLKEEPER` shows), CANIMPROVE
(MOBS, ITEMS, ROOMS, AREAS, EXITS), PARMS with defaults (`price=20 exit=north`),
SCRIPT (MOBPROG, optionally with `<SCRIPT>` JavaScript, or `LOAD=file`),
ACCOUNT (what it says it is doing) and VARSCOPE (empty: variables belong to the
host; `*`: its own). The script reads parameters as `PARM(price)`, `$%PARM(price)%`
in text, `IF PARM(free)` (set and not 0 or false) and `parm('price')` in
JavaScript.

Then the behaviour goes wherever a behaviour ID goes: `TollKeeper price=5 exit=west`
in the editor, `MPBEHAVE`, a quest's GIVE BEHAVIOR, a faction or plane. Each copy
has its own engine and values; all share the definition, so
`MODIFY BEHAVIOR TollKeeper` changes every one. `MODIFY BEHAVIOR <ID> <FIELD>
<VALUE>` sets one field without the editor (`\n` in SCRIPT is a new line).
`DESTROY BEHAVIOR <ID>` removes it. `SAVE` writes it to the world's
`custom/behaviors/` folder with the script in a file beside it. `WHEN=`, `TOD=`
and `HOURS=` work in its parameters.

### Inspecting and testing NPC life

| Tool | Use |
| --- | --- |
| `STAT BEHAVIORS <mob>` | Each behaviour with its parameters and what it has built up (Schedule's entry in force, Memory's facts, Sentry's cooldown, Escortee's progress); for timed behaviours, when they act next; keys the help does not name are reported as unknown; whether `WHEN/TOD/HOURS` is met. `STAT BEHAVIORS HERE` and an item name work too. |
| `TRACEB <mob> [ticks]` | For timed behaviours, tells each decision (time comes, acts, could not act, lost its roll) for 20 ticks or the number given. `TRACEB OFF` stops it. |
| `WAIT UNTIL DAWN\|DAY\|DUSK\|NIGHT`, `WAIT n HOURS` | Move the clock to test routines. |
| `AHELP BEHAVIOR_<ID>` | The shipped help. |
| `CHATTEST`, `CHATLINT`, `CHAT RELOAD` | MudChat tools ([below](#chat-tools-chattest-chatlint-chat-reload)). |

Checklist when an NPC does not do its routine: is it in combat or Alerted? Does
the WHEN/TOD/HOURS condition hold (`STAT BEHAVIORS`)? Is the room ID right (the
log says `Schedule: no room ...`)? Is the area PASSIVE and catch-up off? Is a
second movement behaviour pulling it away?

## Part 2: Dialogue

### Who hears whom

What a player types reaches an NPC in four ways, and the systems below listen
at different points:

| Player types | Message | Who reacts |
| --- | --- | --- |
| `SAY hello` | Speech to the room. | MudChat answers only if **fewer than three player characters** are in the room; scripts' SPEECH_PROG fire for everyone who hears. |
| `SAYTO innkeeper hello`, `ASK innkeeper about mine` | Speech targeted at the NPC. | MudChat and SPEECH_PROG, even in a crowded room. `ASK` is SAY with the verb "ask". |
| `TALK innkeeper [ABOUT topic]` | Opens a conversation (see [TALK](#talk-and-talk_prog)). | `TALK_PROG` first; if none fits, the topic (or "Hello") is said to the NPC for MudChat and SPEECH_PROG. |
| A bare `SAY ...` after TALK | The NPC you are talking with becomes the target while you are both in the room. | As SAYTO. |

Other facts that apply to every dialogue system:

- **Languages.** An NPC that cannot understand the language spoken does not
  process the speech: a monolingual guard does not take a keyword from speech it
  cannot follow. MudChat, Hireling and scripts check this before matching. Use a
  `NOTUNDERSTOOD_PROG` script (or a translated or visual route) for a deliberate
  fallback. See [languages](../languages.md) and `AHELP NOTUNDERSTOOD_PROG`.
- **The NPC must see and hear the speaker**, be awake and free to act. A
  sleeping NPC (Sleeper, Schedule SLEEP) does not answer.
- **Another NPC speaking** makes MudChat hold back untargeted replies for eight
  ticks, so NPCs do not talk over each other.
- **Time.** In real-time play replies wait for the NPC's own ticks; in
  turn-based play they come before your next prompt
  ([DIALOGUETIME](#replies-in-turn-based-time-dialoguetime)).
- **Spoken text from NPCs is filtered**: `[[word]]` markup becomes a highlighted
  topic ([Topics](#topics-words-ask-and-hints)).

### MudChat

Source: [MudChat](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/MudChat.java).
Reference: [24 MudChat](24-behaviors-and-masks.md#mudchat). Help: `AHELP BEHAVIOR_MUDCHAT`.
Inherited manual: [MUDChat.html](../../CoffeeMud/guides/MUDChat.html) (read it for the
pattern language; CoffeeSUD kept that grammar and added the items marked below).
Fixtures: [n10-dialogue.js](../../tools/fixtures/fixes/n10-dialogue.js),
[n11-mudchat.js](../../tools/fixtures/fixes/n11-mudchat.js),
[n19-mudchat.js](../../tools/fixtures/fixes/n19-mudchat.js),
[dt4-chat.js](../../tools/fixtures/fixes/dt4-chat.js).

MudChat gives an NPC pattern-matched replies. A **chat file** holds groups of
entries; an entry has a pattern and weighted replies. The behaviour:

1. Hears speech or an action aimed at it (or untargeted, see above).
2. Finds its **group** in the chat file.
3. Tries the group's entries in file order; the **first entry that matches**
   wins (put specific entries first, ambiguous ones last).
4. Picks one of that entry's replies by weight and queues it for a few ticks.

**Parameters** (the behaviour's parameter string):

| Form | Meaning |
| --- | --- |
| *(empty)* | The default chat file (`CHATDEFAULT`, normally `chat.dat`); the group is chosen by the NPC's name, race or class. |
| `smurf` | Default file, group `smurf`. |
| `chat.dat=smurf` | File `chat.dat`, group `smurf`. |
| `mychat.dat=` | File `mychat.dat`; group chosen automatically. |
| `chat/miller.dat=miller` | A file in a sub-folder of `resources/` (or `world/resources/`). |
| `+(here there);9neither;5well, where?;1nowhere` | Extra entries written inline, `;` standing for a new line, on top of the default. |
| `chat.dat=smurf+(smurfy);9Smurfy!` | A file and group, plus inline entries. |
| `DELAY=n` | CoffeeSUD: ticks before it answers (default 2). Put it anywhere in the string. `DELAY=0` answers on its next tick. |

**Choosing the group.** In order: the group named in the parameters; a group
whose names match the NPC's name (leading article removed); its race name; its
class name; finally the first group in the file (the entries before any `>`
line). A group header can also hold zapper masks between slashes
(`>guard /-LEVEL +>10/`), so a group can match by mask instead of by name.
Matching is by whole name, not by word: `>miller` matches an NPC named "the
miller", not "Brenn the miller". For anything else, name the group in the
parameters.

**Where the files are.** Chat files live in `resources/`. A file of the same
name in `world/resources/` overlays it ([state-and-persistence.md](state-and-persistence.md)).
A file may also have extension parts `name.1` through `name.9`, which are read
and appended after it. Use your own file for your NPCs and keep `chat.dat` stock. In builder mode a
chat file edited on disk is read again within a couple of seconds; otherwise
use `CHAT RELOAD`.

**`CHATDEFAULT`** (coffeemud.ini or a world's world.ini) is the file used by
MudChat NPCs whose parameters name none. The default is `chat.dat`; `NONE` makes
such NPCs chat only with entries given in their parameters
([world-settings.md](world-settings.md)).

**When it speaks.** Replies are queued with the delay, one reply per tick, and
dropped if the NPC cannot act. The NPC does not answer the same speaker again
until its queue is empty. MudChat only reacts to characters the game plays
(the player and party), never to other NPCs, and never when the global
`MUDCHAT` disable flag is set.

### The chat file format

A chat file is plain text, line oriented. Lines are not wrapped; the original
header in `resources/chat.dat` documents the operators. This section is the
complete grammar as the parser reads it.

**Line types** (by first character, in a pass over the file):

| First character | Meaning |
| --- | --- |
| `#` | Comment. |
| `>` | Start of a group: `> name name2 /mask/`. Names are separated by spaces and compared case-insensitively; a `/mask/` is a zapper mask. |
| `(` | A **say** entry: matches speech aimed at the NPC. |
| `[` | A **targeted action** entry: matches an emote or social done *to* the NPC (the text matched is what the NPC sees). |
| `{` | An **untargeted action** entry: matches an emote or social done in the room. |
| `<` | A **tick** entry: tested every tick of the NPC instead of reacting to anything (see below). |
| `*` before the above | A **combat** entry: used only while the NPC or the speaker is in combat. Entries without `*` are used only out of combat. |
| `L` or `.` before the above | Inherited LLM markers: `L` entries apply only when an LLM integration is installed, `.` entries only when none is. CoffeeSUD does not ship one, so `.` entries are live and `L` entries never fire. |
| `?` | CoffeeSUD: a **guard** for the next entry only (see below). |
| `1` to `9` | A **reply** line for the entry above: the digit is the weight. |
| `${NAME=value}` | Define or override a variable for the group (`${NAME+value}` appends). |
| `@group` | Copy the entries of an earlier group into this one (`@default` is the first group in the file). Only earlier groups; avoid circular links. |
| `%file` | Include another file inline at this point. |
| `"` | Write the rest of the line to the log (debugging). |

**Patterns** go inside the brackets. Text matches **whole words** after the
speech is lower-cased and stripped of punctuation: `(coffeemud)` matches
"whats coffeemud?" but not "coffeemuds".

| Operator | Meaning |
| --- | --- |
| `a \| b` | a or b. |
| `a & b` | a and b. |
| `a ~ b` | a and not b. |
| `( ... )` inside | Grouping: `(kill ~ (yourself \| myself))`. |
| `^word` | The word must start the sentence. |
| `=word` | The whole sentence must be exactly that. `(=)` never matches; `()` matches anything. |
| `/zapper mask/` | The speaker must fit the mask: `(coffeemud & /-GENDER +FEMALE/)`. |

Because entries are tried in order and the first match wins, put `()`
catch-alls last. A reply to speech is never mixed from two entries.

**Replies.** `weight` (1 to 9; higher is likelier) then an optional operator
character, then the text. The weights are shares of a total, not percentages.

| Operator | Meaning |
| --- | --- |
| *(none)* | `sayto <speaker> text`. |
| `"` | `say "text"` (to the room). |
| `:` | `emote text`. |
| `!` | Run the text as a command (`!kill $t`, `!smirk $t`). |
| `&` | CoffeeSUD: run the rest as a **MOBPROG command** with the NPC as the host and the speaker as `$n` (`&mpsetvar $n JOB given`). |
| `` ` `` | Submit to an LLM (needs an integration; ignored here). |

Several replies can share one line, separated by `~`, each with its own
operator: `9"Clear the rats and I'll pay you.~:rubs his chin.~&mpsetvar $n JOB given`.
All of them run when that line is chosen. If a line starts with a digit that is
really text, put an operator first (`9"3 gold coins`).

**Variables in replies.**

| Code | Value |
| --- | --- |
| `$n` | The NPC's own name (as `chat.dat` documents it). |
| `$t` | The speaker's name. |
| `$r` | The rest of the speaker's sentence after the matched words. |
| `$w` | The whole sentence matched. |
| `$$` | A literal `$`. |
| `${STAT}` | A generic stat of the NPC (`${GENDERNAME}`, `${RACE}`; see `LIST STATS`), or a variable defined in the group. |
| `$<STAT>` | The same for the speaker (`$<ALIGNMENTDESC>`). |
| `$%FUNC(args)%` | A MOBPROG value function evaluated in place ([28](28-mobprog-functions.md)). |
| `$@group` | At the **end** of a reply segment: switch this speaker to that group (see below). |

`${THEMEDESC}`, `${ROOMDESC}` and `${PERSONALITY}` are provided specially.

**Tick entries** (`<`). `<30>` is true on 30 percent of the NPC's ticks
(a roll of 1 to 100 at or below 30); `|` inside is an or (`<10|90>`). An NPC
checks its tick entries once a tick and speaks the first one that matches; the
odds are per tick (and conditional on any guard holding), not a promise of a
line every so many ticks. A tick entry has no speaker, so give its replies the
`"` (say) or `:` (emote) operator; a plain reply would be a `sayto` aimed at the
NPC itself.

```text
>tavern drunk
<5>
9:hiccups loudly.
5"Another round! Barkeep!
```

**Guards (`?`).** CoffeeSUD. A line `?<condition>` immediately before an entry
makes the entry apply only when the condition holds. The condition is a MOBPROG
IF-style expression ([28](28-mobprog-functions.md)); `$t` (and `$n`) are the
speaker, `$i` is the NPC.

```text
?VAR($t FIXN11HERO == yes)
(hello)
9"You are the hero of the mine!
(hello)
9"Good day.
```

A guard covers one entry. Without it you would have to use a zapper mask in the
pattern, which is lower-cased and needs `)`, `&` and `|` escaped. Guards can read
anything a script can: quest state (`QUESTWON`, ...), variables, `AFFINITY($i $t > 25)`,
tattoos, Memory facts.

**Per-speaker groups (`$@`).** A reply that ends `$@group` switches the speaker
to another group of the same file:

| Form | Effect |
| --- | --- |
| `$@group` | This **speaker** uses `group` for six minutes (real time). Other speakers keep the base group. |
| `$@+group` | Add `group`'s entries to what the speaker already has instead of replacing. |
| `$@!group` | CoffeeSUD: until changed. Kept in the NPC's saved variable `MUDCHAT_GROUP_<SPEAKER>`. |
| `$@-` | Back to the base group (forgets a `$@!`). |

Put `$@` at the end of the last segment: `9"Back so soon? The rats!$@!foreman_quest`.
`$@` takes the group name as written in the header. A passing switch is part of
the saved state (time left); a kept one survives restarts through the variable.

**Complete example** (a mine foreman, `world/resources/chat/foreman.dat`):

```text
# first group: used by any MudChat NPC pointed at this file with no group
(hello|hi)
9"Mind the roof.

>foreman
?VAR($t MINEQUEST == done)
(hello|hi)
9"You are the hero of the mine!
(hello|hi)
9"Good day. Looking for work?
(work|job)
9"Clear the rats from the lower shaft and I'll pay you.~&mpsetvar $n MINEQUEST given$@!foreman_quest
(rats)
9"They came last spring, from the old [[flooded tunnel]].
()
5"Hm?
2:scratches his head.

>foreman_quest
(hello|hi)
9"Back so soon? The rats!
(done|finished|dead)
9"Then we are square.~&mpsetvar $n MINEQUEST done$@-
```

Attach with the behaviour `MudChat chat/foreman.dat=foreman`. Lint it first
(next section).

### Chat tools: CHATTEST, CHATLINT, CHAT RELOAD

Source: [ChatTools](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/ChatTools.java).
Help: `AHELP CHATTEST`. Fixture: [dt4-chat.js](../../tools/fixtures/fixes/dt4-chat.js).
Security: needs the CMDMOBS flag (builders and archons).

| Command | Does |
| --- | --- |
| `CHATTEST <npc> <what you would say>` | A dry run: the NPC's base and active chat groups, anything that would stop it answering (it cannot hear or see you, it answered you last, another NPC spoke, three or more players are here), each candidate entry and whether it matches, and the replies of the first that does with weights and variables filled in. Nobody speaks. |
| `CHATLINT <file>` | Checks a chat file as the parser reads it, each problem with its line number: patterns that do not parse, malformed `${}` lines, `@` links to groups not defined above them, replies with no pattern, patterns with no replies, unreadable files, lines the parser skips, empty guards, entries provably hidden by an earlier one, and replies of weight 0 (never chosen). Literal and OR shadowing can be proved; arbitrary guarded or masked patterns are checked conservatively. |
| `CHAT RELOAD [file]` | Read one chat file again (or all of them) for every NPC that talks from it. |

```text
chattest foreman hello there
chatlint chat/foreman.dat
chat reload chat/foreman.dat
```

### TALK and TALK_PROG

Sources: [Talk](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Talk.java),
[Conversations](../../CoffeeMud/com/planet_ink/coffee_mud/core/Conversations.java).
Reference: [26 TALK_PROG](26-mobprog-triggers.md#talk_prog). Help: `AHELP TALK`.
Fixture: [n9-talk.js](../../tools/fixtures/fixes/n9-talk.js).

`SAY` needs the player to guess a phrase. `TALK` is an explicit way to begin a
conversation with a creature the game plays:

```text
TALK [npc] [ABOUT topic]
TALK BYE
```

What it does, in order:

1. Finds the NPC (the named one, your current conversation partner, or the only
   NPC in the room who is not in your party; if several, it asks "Talk with whom?").
   You cannot TALK with player characters.
2. Makes the NPC your **partner**, so a bare `SAY ...` is aimed at it while
   both of you are in the room, however many others are there.
3. If the NPC "hints" topics (HINTTOPICS) and this is the first talk, it tells
   its topics ([Topics](#topics-words-ask-and-hints)).
4. `TALK npc ABOUT` with no topic lists the topics you know that it answers.
5. Runs the NPC's `TALK_PROG` at once (a MOBPROG trigger: `TALK_PROG ALL` for any
   talk, `TALK_PROG mine ore` for those words, `TALK_PROG P old mine` for a
   phrase). `$n` is the character, `$g` and `$0` the topic (empty for a bare TALK).
6. If **no** TALK_PROG fits, the character says the topic (or "Hello") to the NPC
   as if by SAYTO, so MudChat and SPEECH_PROG answer. A bare TALK then also offers
   the NPC's **services menu**.

**The services menu** is built from what the NPC is. Each entry runs the normal
command, and "Goodbye" runs `TALK BYE`:

| NPC is | Menu entry | Runs |
| --- | --- | --- |
| A shopkeeper | "Trade (LIST, BUY, SELL)" | `LIST` |
| A banker | "Banking (LIST, DEPOSIT, WITHDRAW)" | `LIST` |
| Has `MOBTeacher` | "Learn" | `QUALIFY` |
| Has `Hireling` | "Hire (its terms)" | `SAYTO <npc> hire` |
| A quest giver (a quest marker) | "Quests" | `QUESTS OFFERED` |

No menu appears for an NPC with none of these. The menu uses the same
mechanism as [MPMENU](#mpmenu-numbered-choices): it does not block, an Enter
takes the default (Goodbye).

```text
TALK_PROG ALL
mpecho $i looks up from the ledger.
~
TALK_PROG mine ore
mpecho $i spits. 'Nobody mines there since the collapse.'
~
```

`MEMORY($i $n LASTTOPIC)` returns the last topic of a TALK
([28](28-mobprog-functions.md#memory)). A TALK counts as a meeting in the `MEMORY()` record and feeds Prop_Affinity
`TALK(word)` rules.

### Topics: [[words]], ASK and hints

Source: [NpcTopics](../../CoffeeMud/com/planet_ink/coffee_mud/core/NpcTopics.java).
Help: `AHELP NPCTOPICS`. Fixture: [n24-topics.js](../../tools/fixtures/fixes/n24-topics.js).

Topics let the game tell the player what is worth asking. In anything an NPC
says (a `SAY`, a MudChat reply, a script `say`, a shopkeeper or hireling line)
you can mark words:

```text
They came from the old [[flooded tunnel]].
The [[the Captain|captain]] takes their coin.
```

`[[mine]]` shows the words highlighted and teaches the topic `mine`;
`[[the old mine|mine]]` shows "the old mine" and teaches `mine`. Each player
character who hears the line is told "(You can ask about mine now.)". Topics
are stored lower-case in the character's script variable `KNOWN_TOPICS`
(topics separated by `|`), saved with the game; `VAR($n KNOWN_TOPICS)` reads
them. Topics do not teach themselves from emotes, only from spoken text.

Using them:

- `ASK <npc> ABOUT <topic>` (or `TALK <npc> ABOUT <topic>`) says the topic to
  the NPC; whatever answers to those words (SPEECH_PROG words, TALK_PROG words,
  MudChat entries) replies.
- `ASK <npc> ABOUT` or `TALK <npc> ABOUT` with **no topic** lists the topics the
  character knows that this NPC answers to. A plain `TALK` lists them too, when
  there are any. An NPC "answers" a topic if: a SPEECH_PROG word or phrase (not `ALL`
  or `*`), a TALK_PROG word, or a MudChat say entry (not a combat one) that
  matches the topic phrase and is found through one of the topic's own words. A
  catch-all entry such as `()` does not count, and guards are not consulted.
- **Hints.** `HINTTOPICS=true` in coffeemud.ini or world.ini, or the **tag**
  `HINTTOPICS` on an individual NPC, makes the NPC tell, and teach, its topics
  the first time a character talks with it (via TALK): its scripts' words and
  the first word or phrase of each say entry in its own chat file or inline
  entries (never the default `chat.dat`'s). The NPCs that have
  already told a character are kept in `TOPICS_HINTED`.
- **Journal.** With `JOURNALTALK=true` in coffeemud.ini, what a character
  learns is also written to their journal ("People" chronicle), once per new
  topic.

Design: end each topic's answer with the next topic. A good informant NPC
ladders three or four topics ("old mine" leads to "flooded tunnel" leads to
"the foreman") and never leaves the player guessing.

### MPMENU: numbered choices

Source: [ScriptMenus](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptMenus.java).
Reference: [27 MPMENU](27-mobprog-commands.md#mpmenu). Fixture: [n2-menu.js](../../tools/fixtures/fixes/n2-menu.js).
Example with a complete file: the [cookbook](cookbook.md).

`MPMENU <pc> <FUNCTION> "<prompt>" <key>:"<label>"[IF <condition>] ... [default key]`
shows a numbered menu **without blocking**. The script carries on at once; the
character's next line answers the menu, by number or by key. Then the script's
`FUNCTION_PROG <FUNCTION>` runs with the key as `$0` (and `$g`), `$n` the
character and `$i` the host.

```text
GREET_PROG 100
MPMENU $n SAGE "The old sage regards you." tower:"Ask about the tower" ring:"Show him the ring"[IF HAS($n ring)] bye:"Leave" bye
~
FUNCTION_PROG SAGE
switch $0
case tower
  mpechoat $n The sage points to the north.
case ring
  mpechoat $n The sage turns the ring over and over.
case bye
  mpechoat $n The sage turns back to his books.
endswitch
~
```

Rules:

- The prompt is quoted. Each choice is `key:"label"`, optionally followed by
  `[IF condition]` (a script condition; a choice whose condition fails is not shown).
  A last bare word is the **default key**: Enter, or an answer that matches
  nothing, takes it. With no default, a bad answer asks again.
- The player sees `1. label` lines and `Choose 1-N (Enter: label):`.
- Answering a menu is not a command: it costs no time in turn-based play.
- **One menu at a time per character**; opening another replaces it.
- If the FUNCTION does not exist the script reports an error and no menu opens.
- Work that depends on the answer belongs in the answer's FUNCTION_PROG, not in
  the lines after MPMENU.
- Pending menus persist across save and restore (options and binding); a
  restore reissues the displayed menu unless its source changed or is gone.
- MPCHOOSE, MPPROMPT and MPCONFIRM are the older commands that *block* waiting
  for the player; prefer MPMENU.

### CHECK: skill checks in dialogue

Reference: [28 CHECK](28-mobprog-functions.md#check). Fixture: [n29-check.js](../../tools/fixtures/fixes/n29-check.js).

`CHECK(<who> <STAT or skill id> <difficulty> [modifier])` is true if a d20 on
the game's dice, plus (stat - 10) / 2 for a stat (`CHA`, `CHARISMA`, ...) or the
skill's proficiency / 10 for a skill (`Skill_Haggle`), plus the modifier (a
number or sum, such as `$%AFFINITY($i $n)%/10`), is at least the difficulty.
After it the character's variable `CHECKMARGIN` holds total minus difficulty,
so a script can tell a narrow success from a decisive one. The character is
told "[Charisma check: success]"; `CHECKROLLS` in coffeemud.ini (or world.ini)
sets OFF, BRIEF (default) or FULL (with the roll and bonuses).

```text
SPEECH_PROG p let me through
if CHECK($n CHA 14 $%AFFINITY($i $n)%/10)
  mpechoat $n The guard grunts and steps aside.
  mpsetvar $n GATE_PASS 1
else
  mpechoat $n 'Not today.'
  mpsetvar $n GATE_TRIES ++
endif
~
```

Avoid a dead end on failure unless that is meant: give the failure branch a
different route (a bribe, another topic, coming back later).

### Prop_NPCLines: shopkeeper and service wording

Sources: [Prop_NPCLines](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_NPCLines.java),
[NpcLines](../../CoffeeMud/com/planet_ink/coffee_mud/core/NpcLines.java).
Reference: [23 Prop_NPCLines](23-properties-reference.md#prop_npclines). Fixture: [n12-npclines.js](../../tools/fixtures/fixes/n12-npclines.js).

Shopkeepers, bankers, postmen, trainers and hirelings say fixed English lines
("I'm not interested."). Prop_NPCLines replaces them per NPC, or for a whole
town when put on an **area**. It applies to the NPC's stock lines (the ones the
engine speaks with `postSay`), not to MudChat or script replies.

Text: entries `KEY="text"`, where the key is a short name (below) or the original
line in double quotes:

- Alternatives: `"one"|"two"` picks one at random.
- `@x1`, `@x2`, ... are the stock line's own values (a price, an item); `$n` is
  the one spoken to.
- A trailing `[mask]` makes it that customer's alone: a zapper mask on the
  customer, or `[AFFINITY >=25]` / `[AFFINITY FRIENDLY]` for the NPC's opinion
  of them. Masked entries are tried first.
- An empty text (`ACCOUNTCLOSED=""`) makes the NPC say nothing.
- An NPC's own entries come before its area's.

```text
NOTBUYING="Bah! I've no use for that junk."|"Take that rubbish elsewhere." STOLEN="I know where that came from, $n." NOTBUYING="For you, friend, still no."[AFFINITY FRIENDLY]
```

| Key | Stock line it replaces |
| --- | --- |
| `NOTBUYING` | I'm not interested. |
| `NOTBUYINGTHOSE` | I'm sorry, I'm not buying those. |
| `STOLEN` | I don't buy stolen goods. |
| `OUTOFSTOCK` | I don't have that in stock. Ask for my LIST. |
| `TOOPOOR` | You can't afford to buy @x1. |
| `SHOPPOOR` | Sorry, I can't afford that right now. Try back later. |
| `TOODEAR` | That's way out of my price range! Try AUCTIONing it. |
| `OFFER` | I'll give you @x1 for @x2. |
| `FIRM` | I'm afraid my prices are firm. |
| `TOOADVANCED` | That's too advanced for you, I'm afraid. |
| `TOOLOW` | You aren't experienced enough to buy @x1. |
| `CLANSONLY` | I only sell to clans. |
| `BALANCE` | Ok, your new balance is @x1. |
| `NOLOANS` | I'm sorry, I don't loan money. |
| `ACCOUNTCLOSED` | I have closed that account. Thanks for your business. |
| `ONLYCURRENCY` | I'm sorry, I only deal in @x1. |
| `SAFE` | Thank you, @x1 is safe with us. |
| `DELIVER` | I'll deliver that for ya right away! |
| `FORHIRE` | I'm for hire. Just give me @x1 and I'll work for you for @x2 "hours". |
| `HIRED` | Ok. You've got me for at least @x1 "hours". My skills include: @x2. I'll follow you. Just ORDER me to do what you want. |
| `PRICE` | My price is @x1. Give me @x2 more and I'll work for you for @x3 "hours". |
| `ONTHEJOB` | I'm on the job for @x1 more. |
| `TIMEUP` | Your time is up. Goodbye! |
| `GOODBYE` | Suit yourself. Goodbye. |
| `WONTWORK` | I wouldn't work for the likes of you. |
| `SKILLS` | My skills include: @x1. |

Any other line an NPC says through the stock channel can be named by quoting it
exactly. Memory's `REFUSE=`, Prop_Affinity's `REFUSEMSG` and Hireling's `MSG_*`
terms have their own text and are not rewritten by NPCLines unless you quote the
resulting line.

### Barks and ambient lines: Sounder and Emoter

Sources: [Sounder](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Sounder.java),
[Emoter](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Emoter.java),
[Barks](../../CoffeeMud/com/planet_ink/coffee_mud/core/Barks.java),
[Ambience](../../CoffeeMud/com/planet_ink/coffee_mud/core/Ambience.java).
Reference: [24 Sounder](24-behaviors-and-masks.md#sounder), [24 Emoter](24-behaviors-and-masks.md#emoter).
Fixture: [n22-barks.js](../../tools/fixtures/fixes/n22-barks.js).

**Emoter** performs random emotes on a timer. **Sounder** performs emotes on
*events* and, since CoffeeSUD, has a **bark table**: lines the NPC says or emotes
when something happens, with chances and cooldowns. Both work on mobs, items,
rooms and areas. For NPCs, Sounder barks are the cheap way to make a guard say
"Move along." to each newcomer.

*Emoter parameters.* The first `;`-separated field holds flags and numbers, the
rest are emote strings:

```text
min=10 max=20 chance=25;wiggles his bottom.;smiles evilly.;burps!
smell chance=25 expires=10;smells horrible!;is really stinky!
chance=100 inroom="Here#1,Here#2";looks horrible!;is bad!
broadcast sound chance=25;grumbles!;eeeks!
min=1 max=2 chance=25;sound grumbles!;visual leers broadly.;social smile
```

| Field | Meaning |
| --- | --- |
| `min=` `max=` `chance=` | Ticks between tries, then percent chance. |
| `expires=n` | Total emotes before the behaviour removes itself. |
| `inroom="a,b"` | Only in those rooms. |
| `sound`, `smell`, `broadcast`, `PRIVATE`, `EMOTEONLY` | The default type of emote (sound or smell based; `broadcast` also to adjacent rooms). |
| `MASK="zapper"` | Only for whom the mask fits. |
| `NOREPEAT=n` | Do not repeat any of the last n lines in a room. |
| `RETRY` | Try another eligible line when the picked one is not eligible. |
| `QUIETAFTER=n` | CoffeeSUD: stay quiet during combat, and for n ticks after a player in the room read a page or examined a scenery detail. |

A string may start with `SIGHT`, `SOUND`, `SMELL`, `NOISE` or `SOCIAL <name>` to
set its own type, and, CoffeeSUD, with **condition prefixes** in capitals: `DAY`,
`NIGHT`, `DAWN`, `DUSK`, `SPRING`, `SUMMER`, `FALL`, `WINTER`, `RAIN`, `SNOW`,
`FOG`, `INDOORS`, `OUTDOORS`, `IFVAR name=value` (a world variable; comparators
`== != > < >= <=`, or just a name for set and not 0/false), `MASK="zapper"`,
`ONCE` (once per character, remembered by a tattoo) and `WEIGHT=n`. In an area's
Emoter each room's own conditions are tested. A line's own first word is text
unless it is written in capitals as one of these words.

```text
min=8 max=20 chance=40 RETRY NOREPEAT=2;NIGHT SOUND an owl hoots.;IFVAR WOLVES=1 WEIGHT=3 ONCE SOUND wolves howl.
```

*Sounder, old form.* An optional `min=/max=` field, then entries `CODE text`:
`sound 80 wiggles his bottom.` (`SOUND <pct>` on a timer), and item/room event
codes (`GET`, `GET_ROOM`, `DROP`, `PUSH`, `PULL`, `EAT`, `MOUNT`, `DRINK`, `SIT`,
`SLEEP`, `WEAR`, `OPEN`, `CLOSE`, `HOLD`, `WIELD`, `REMOVE`, `PORTAL_ENTER`,
`PORTAL_EXIT`, `DAMAGE`, `FIGHT`, each with a `_ROOM` variant that tells the
others). In text `$p` is the item, `$n` the source, `$e` he/she, `$s` his/her.

*Sounder, bark table (CoffeeSUD).* Entries mixed among the old ones, separated by
`;`, or one per line in `FILE=path` (lines starting `#` are comments):

```text
EVENT[(arg)]: [CHANCE=n] [COOLDOWN=n] [ONCEPERPLAYER] line | line | ...
```

- `CHANCE=n` is the percent chance of barking at all (default 100). `COOLDOWN=n`
  is ticks before that event barks again. `ONCEPERPLAYER` is once per character.
- A **line** is `[weight] [{mask}] [SAY|EMOTE|SOUND] text`: a leading number is
  the weight, `{mask}` a zapper mask on the one the bark is about, `SAY text` is
  said (so are lines starting `"`), `EMOTE text` or `:text` is emoted (the
  default), `SOUND text` is a sound. `$n` is the one it is about. One line is
  drawn by weight among those whose mask and conditions fit. Lines accept the
  Emoter condition prefixes above. Lines cannot contain `|` or `;`.
- `EVENT(arg)` narrows an event by the person or item it is about: a zapper mask
  (starting `+` or `-`) or words of the name, as in `SEE_MOB(mask)`, `SEE_ITEM(mask)`,
  `WIELD(mask)`. `HURT<N` (or `HURT(N)`) gives the hit point percentage.

| Event | Barks when |
| --- | --- |
| `GREET` | A character arrives (every time, subject to cooldown). |
| `GREET_FIRST` | The first time it sees that character (`GREET` is used if there is none). |
| `SEE_MOB(mask or words)` | Anyone arriving who fits the mask or has the words in their name. |
| `SEE_ITEM(mask or words)` | A character arrives carrying such an item. |
| `KILL` | It kills `$n`. |
| `ALLY_DIES` | One of its group or race dies; `$n` is the killer. |
| `FLEE` | It flees. |
| `FIGHT` | It is attacked. |
| `HURT<N` | It falls below N percent of its hit points (once until it heals). |
| `IDLE` | Each tick; give it a `CHANCE=`. |
| `DAWN`, `DAY`, `DUSK`, `NIGHT` | Each begins. |
| `RAIN`, `SNOW`, `CLEAR` | That weather begins. |
| `SHOP_LIST`, `SHOP_BUY` | A character lists or buys. |
| `BRIBED` | A character gives it money. |
| `WIELD(mask)`, `WEAR`, `HOLD`, `GET`, `DROP` | An item is wielded, worn, held, picked up or dropped by anyone in the room. |

```text
GREET_FIRST: SAY Welcome to Brenn, stranger.;GREET: COOLDOWN=20 2 SAY Move along. | 1 SAY Keep your blade sheathed.;WIELD(+WEAPONCLASS SWORD): SAY Put that away in the city!;NIGHT: SAY Curfew's at the bell.;HURT<30: SAY To me! Guards!;SEE_MOB(-NAMES +thief): EMOTE eyes $n warily.
```

A saved game keeps whom it has greeted and its once-per-character barks.

### Scene: exchanges the player overhears

Source: [Scene](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Scene.java).
Reference: [24 Scene](24-behaviors-and-masks.md#scene). Fixture: [n28-scene.js](../../tools/fixtures/fixes/n28-scene.js).

A Scene stages a short exchange between NPCs for a player character who walks
in. It goes on a **room** or on an **NPC** (where `SELF` is that NPC). Statements
are separated by `;` or written one per line in `FILE=path`:

| Statement | Meaning |
| --- | --- |
| `NAME=name` | Names the scene. Each character who hears it to the end gets the variable `SCENE_<NAME>` set to the game day. |
| `ACTOR key=mask-or-words` | Defines an actor: a zapper mask (starts with `+` or `-`) or words of an NPC's name in the room. On an NPC, `SELF` is the NPC itself. |
| `key: say text` | The actor says it. |
| `key: emote text` | The actor emotes. |
| `key: <command>` | The actor does any command. |
| `ECHO text` | The room is told. |
| `PAUSE n` | n ticks of silence. |
| `DO <MOBPROG line>` | Run a script command with `$n` the character it plays for (`mpsetvar`, `mpqjournal`, `mpcallfunc`, ...). |
| `IF=condition` | A script condition (`$n` the character, `$i` the first actor) that must hold to begin. |
| `ONCE` / `REPEAT=n` | ONCE (default): once per character. `REPEAT=n`: again after n game days. |
| `DELAY=n` | Ticks between lines (default 1). A line is one tick, one turn in turn-based time. |
| `INTERRUPTIBLE` | A character who speaks to an actor stops the scene and the actors turn to them; it plays again when they have left and come back. |
| `WHEN=`, `TOD=`, `HOURS=` | Hold it back (the behaviour gate). |

It begins when a player character is in the room who has not heard it, all
actors are there, awake and not fighting, and `IF=` holds. It stops, unheard,
if the character or an actor leaves or an actor fights. In a line said by an
actor, `$n` is the character's name and `[[topic]]` teaches the topic.

```text
NAME=bribes;ACTOR guard1=tall guard;ACTOR guard2=-NAMES +"short guard";guard1: say Heard the bandits hit the mill again.;guard2: emote spits.;PAUSE 1;guard1: say The [[captain]] takes their coin, they say.;DO mpsetvar $n HEARD_BRIBES yes;TOD=NIGHT
```

Scenes are for guards chatting or an arrival moment, played linearly. It is
not a branching dialogue or a cutscene system: an actor that vanishes, leaves
or is interrupted ends the scene, so keep important story delivery somewhere
the player cannot miss it. The state of a scene in progress is kept in a saved
game; `STAT BEHAVIORS` shows where it is and any statement it did not understand.

### Replies in turn-based time: DIALOGUETIME

Source: [DialogueTime](../../CoffeeMud/com/planet_ink/coffee_mud/core/DialogueTime.java).
Help: `AHELP TIMEMODE`. Fixture: [n10-dialogue.js](../../tools/fixtures/fixes/n10-dialogue.js).

In real-time play an NPC replies on its own ticks: a script reply after about a
tick, a MudChat reply after two (or `DELAY=`). In turn-based play (`TIMEMODE
TURNS`) that would mean the answer arrives several actions later.

`DIALOGUETIME` fixes it. With `INSTANT` (the default in turn-based time), after
each command of a player's character the replies it drew in the room run
**before the next prompt without moving the clock**: replies queued by MudChat
and by scripts for SPEECH_PROG, GIVE_PROG, BRIBE_PROG, SOCIAL_PROG and TALK_PROG.
Up to three rounds of replies to replies are run. `WAIT` stops early when an NPC
speaks to the waiting character.

| Where | How |
| --- | --- |
| `DIALOGUETIME=INSTANT\|TICKS` | In coffeemud.ini or a world's world.ini. Blank: INSTANT in turn-based time, TICKS in real time. |
| `TIMEMODE DIALOGUE INSTANT\|TICKS\|DEFAULT` | Changes it for the running game. |
| `MPSETINTERNAL NODELAY TRUE` | In a script's ONCE_PROG: that script's triggers run at once, not on the next tick. |
| `DISABLE=SCRIPTABLEDELAY` | In the ini: scripts never delay. |

### Dialogue with MOBPROG

MOBPROG is the general tool; the systems above are shortcuts for the common
cases. [04](04-mobprog.md) teaches the language; reference chapters are
[26 triggers](26-mobprog-triggers.md), [27 commands](27-mobprog-commands.md),
[28 functions and `$` codes](28-mobprog-functions.md).

| Need | Use |
| --- | --- |
| React to a word | `SPEECH_PROG [P] words` ([26](26-mobprog-triggers.md#speech_prog)). |
| React to TALK | `TALK_PROG` ([26](26-mobprog-triggers.md#talk_prog)). |
| React to arrival, first meeting, return | `GREET_PROG`, `ALL_GREET_PROG`, `FIRSTGREET_PROG`, `REGREET_PROG` (days away). |
| React to gifts or money | `GIVE_PROG`, `BRIBE_PROG`. |
| Menus | `MPMENU` plus `FUNCTION_PROG`. |
| Reusable branches | `FUNCTION_PROG name` and `MPCALLFUNC name`. |
| Catch an NPC's own stock line or message | `CNCLMSG_PROG`/`EXECMSG_PROG` (or Prop_NPCLines for wording). |
| Quest hand-off | `MPSTARTQUEST`, `MPQUESTWIN`, `QUESTSCRIPTED()` (see [08](08-quests.md)). |

**Mixing MudChat and scripts on one NPC.** Both can listen to the same speech.
Order is not guaranteed between them, and both may answer. Two ways to avoid
doubling:

- Keep each topic in one place: scripts for quest-sensitive topics, MudChat for
  flavour, with a MudChat guard (`?VAR($t ...)`) that switches the generic
  entries off while a quest step is active.
- Or do everything in MudChat: guards read quest and variable state, `&` replies
  run MOBPROG commands, `$@!group` switches per-speaker groups that persist.

MudChat's `&` line is a MOBPROG command run with the NPC as host and the speaker
as `$n`; it is not a `FUNCTION_PROG` (use `&mpcallfunc FUNCNAME` to run one).

**What a script reply costs.** A script reply waits for the NPC's tick unless
DIALOGUETIME is INSTANT in turn-based time. A `FUNCTION_PROG` reached from a
menu answer runs at once.

**Talking to the player's party.** `$n` is the actor. `IF ISGROUP`-style tests
and `HASTATTOO($n ...)` read the *character*; party members are separate
characters ([31](31-saves-and-party.md)).

## Reference

### Commands

| Command | Who | What |
| --- | --- | --- |
| `TALK [npc] [ABOUT topic]`, `TALK BYE` | player | Begin or end a conversation; see [TALK](#talk-and-talk_prog). Aliases for ending: `TALK END`, `TALK STOP`. |
| `SAY`, `SAYTO <npc> text`, `ASK [npc] text`, `ASK [npc] ABOUT` | player | Speech; `ASK ... ABOUT` alone lists known topics. `'` is a SAY shortcut. |
| `HIRE <mob>`, `HIRE <mob> PAY` | player | A hireling's terms as a menu; pay its price. |
| `ORDER <mob> <command>` | player | Give a hired NPC an order. |
| `CHATTEST <npc> <speech>`, `CHATLINT <file>`, `CHAT RELOAD [file]` | builder (CMDMOBS) | MudChat dry run, file check, reload. |
| `STAT BEHAVIORS [mob/item/HERE]`, `TRACEB <mob> [ticks]`, `TRACEB OFF` | builder | Inspect and trace behaviours. |
| `TIMEMODE DIALOGUE INSTANT\|TICKS\|DEFAULT` | player | Whether NPC replies come before the next prompt. |
| `WAIT UNTIL DAWN\|DAY\|DUSK\|NIGHT`, `WAIT n HOURS` | player | Advance time to test routines. |
| `CREATE BEHAVIOR <ID>`, `MODIFY BEHAVIOR <ID> [FIELD VALUE]`, `DESTROY BEHAVIOR <ID>` | builder | Generic behaviours. |
| `MODIFY MOB <name>` | builder | Behaviors and Effects fields. |

### Behaviours and properties used in this chapter

| ID | Kind | Hosts | Purpose | Section |
| --- | --- | --- | --- | --- |
| [Schedule](24-behaviors-and-masks.md#schedule) | behaviour | mobs | Daily routine | [Schedule](#schedule-a-daily-routine) |
| [Sleeper](24-behaviors-and-masks.md#sleeper) | behaviour | mobs | Sleep, woken by events | [Sleeper](#sleeper-sleeping-npcs-woken-by-noise) |
| [Sentry](24-behaviors-and-masks.md#sentry) | behaviour | mobs | Lookout and alarm | [Sentry](#sentry-and-alarms) |
| [Memory](24-behaviors-and-masks.md#memory) | behaviour | mobs | Facts about each character | [Memory](#memory-what-an-npc-remembers) |
| [Escortee](24-behaviors-and-masks.md#escortee) | behaviour | mobs | Escort to a destination | [Escortee](#escortee-an-npc-you-take-somewhere) |
| [Hireling](24-behaviors-and-masks.md#hireling) | behaviour | mobs | Paid service | [Hireling](#hireling-paid-help) |
| [Patroller](24-behaviors-and-masks.md#patroller) | behaviour | mobs, items | Walk a path | [Wandering](#wandering-and-patrols) |
| [Mobile](24-behaviors-and-masks.md#mobile) | behaviour | mobs | Random wandering | [Wandering](#wandering-and-patrols) |
| [Aggressive](24-behaviors-and-masks.md#aggressive) (also VeryAggressive, MobileAggressive) | behaviour | mobs | Attack, with `WARN=` | [Warnings](#warnings-before-aggression) |
| [GenBehavior](24-behaviors-and-masks.md#genbehavior) | behaviour | per CANIMPROVE | Behaviour defined as data | [GenBehavior](#genbehavior-your-own-parameterised-behaviour) |
| [MudChat](24-behaviors-and-masks.md#mudchat) | behaviour | mobs | Pattern replies | [MudChat](#mudchat) |
| [QuestChat](24-behaviors-and-masks.md#questchat) | behaviour | mobs | MudChat with once-per-player quest phrases (`pattern::VAR`, read by `QVAR(quest CHAT:NAME)`) | [08](08-quests.md) |
| [Sounder](24-behaviors-and-masks.md#sounder) | behaviour | mobs, items, rooms, exits, areas | Event emotes and bark tables | [Barks](#barks-and-ambient-lines-sounder-and-emoter) |
| [Emoter](24-behaviors-and-masks.md#emoter) | behaviour | mobs, items, rooms, areas | Random emotes | [Barks](#barks-and-ambient-lines-sounder-and-emoter) |
| [Scene](24-behaviors-and-masks.md#scene) | behaviour | rooms, mobs | Overheard exchanges | [Scene](#scene-exchanges-the-player-overhears) |
| [Scriptable](24-behaviors-and-masks.md#scriptable) | behaviour | any | MOBPROG host | [04](04-mobprog.md) |
| [Prop_Affinity](23-properties-reference.md#prop_affinity) | property | mobs, areas | Opinion of each character | [Prop_Affinity](#prop_affinity-an-opinion-of-each-character) |
| [Prop_NPCLines](23-properties-reference.md#prop_npclines) | property | mobs, areas | Stock-line wording | [Prop_NPCLines](#prop_npclines-shopkeeper-and-service-wording) |
| [Prop_ClosedDayNight](23-properties-reference.md#prop_closeddaynight) | property | mobs | Two-place day/night keeper (older than Schedule) | [Schedule](#schedule-a-daily-routine) |

### Settings (coffeemud.ini or a world's world.ini)

See [world-settings.md](world-settings.md) for which keys a world may set.

| Key | Values | Effect |
| --- | --- | --- |
| `CHATDEFAULT` | file name, `NONE`, blank | Chat file for MudChat NPCs whose parameters name none. Blank means `chat.dat`. |
| `DIALOGUETIME` | `INSTANT`, `TICKS`, blank | NPC replies before the next prompt. Blank: INSTANT in turn-based time. |
| `HINTTOPICS` | `true`, blank | Every NPC tells its topics the first time a character talks with it. |
| `JOURNALTALK` | `true`/`false` | Record newly learned topics in the journal. |
| `CATCHUP` | `OFF`, blank | OFF turns off area catch-up everywhere. |
| `CHECKROLLS` | `OFF`, `BRIEF`, `FULL` | What a script's `CHECK()` tells the character. |
| `DISABLE=MUDCHAT` | flag | Turn MudChat off. `DISABLE=EMOTERS` turns Emoter and bark tables off; `DISABLE=SCRIPTABLEDELAY` removes script reply delay. |

Tags (on a mob or area): `HINTTOPICS` (one NPC hints its topics), `NOCATCHUP`
(an area does not catch up).

### MOBPROG items for NPC life and dialogue

| Item | Where | Use |
| --- | --- | --- |
| [TALK_PROG](26-mobprog-triggers.md#talk_prog) | trigger | Conversation begins with TALK. |
| [SPEECH_PROG](26-mobprog-triggers.md#speech_prog) | trigger | Words or phrase said. |
| [GREET_PROG](26-mobprog-triggers.md#greet_prog), [FIRSTGREET_PROG](26-mobprog-triggers.md#firstgreet_prog) | trigger | Arrivals; first meeting; `REGREET_PROG` on return. |
| [GIVE_PROG](26-mobprog-triggers.md#give_prog), [BRIBE_PROG](26-mobprog-triggers.md#bribe_prog) | trigger | Items or money given. |
| [FUNCTION_PROG](26-mobprog-triggers.md#function_prog) | trigger | Named block; `FUNCTION_PROG CATCHUP` runs on area wake with `$g` the ticks. |
| [MPMENU](27-mobprog-commands.md#mpmenu) | command | Numbered menu. |
| [MPAFFINITY](27-mobprog-commands.md#mpaffinity) | command | Change an NPC's opinion. |
| [MPBEHAVE](27-mobprog-commands.md#mpbehave), [MPUNBEHAVE](27-mobprog-commands.md#mpunbehave) | command | Add or remove a behaviour. |
| [MPFORCE](27-mobprog-commands.md#mpforce) | command | Make an NPC do a command (`follow`, `say`). |
| [AFFINITY](28-mobprog-functions.md#affinity) | function | Opinion number or band. |
| [CHECK](28-mobprog-functions.md#check) | function | Skill check. |
| [MEMORY](28-mobprog-functions.md#memory) | function | Meetings and last TALK topic. |
| [STAT](28-mobprog-functions.md#stat) | function | `STAT($i SCHEDULE)`. |
| [VAR](28-mobprog-functions.md#var) | function | Variables, including `MEM_*`, `KNOWN_TOPICS`. |

### Saved variables

| Variable | Holder | Meaning |
| --- | --- | --- |
| `MEM_<FACT>_<NAME>` | the Memory NPC | A fact about a character. |
| `AFFINITY_<NAME>`, `AFFINITYAT_<NAME>`, `AFFINITYQ_...`, `AFFINITYLAST_...` | the NPC (or area) with Prop_Affinity | Value, last update, quest wins counted, rule cooldowns. |
| `KNOWN_TOPICS` | the character | Topics learned, `\|`-separated. |
| `TOPICS_HINTED` | the character | NPCs that already told their topics. |
| `MUDCHAT_GROUP_<SPEAKER>` | the MudChat NPC | A kept `$@!` group. |
| `SCENE_<NAME>` | the character | Game day a Scene was played to them. |
| `<NAME>_TOPIC` (via `MEMORY`) | the NPC | Last TALK topic. |

## Worked examples

The room IDs and area names below are placeholders: replace `Brenn Village#2` and
the others with real IDs from your world (LIST ROOMS or the map tools,
[20](20-areas-rooms-exits.md)). Create each NPC with `CREATE MOB`, set its
description and level ([21](21-mobs.md)), add the behaviours and effects with
`MODIFY MOB`, then test as the sections say. Put new chat files in
`world/resources/chat/`.

### Example 1: a shopkeeper with a routine and greetings

Hobb the miller sells flour. By day he is at his market stall and open; at
dawn he works at the mill; at dusk he sits at the inn; at night he sleeps at
home with the door locked. He greets customers by name, treats friends better,
and remembers thieves.

Create `a GenShopkeeper` named "Hobb the miller" with his stock ([21](21-mobs.md)).
Start him in the stall room. Add:

**Behaviour `Schedule`:**

```text
DAWN: Brenn Village#2 WORK "pours grain into the hopper|wipes flour from his hands"; DAY: Brenn Village#5 SHOP SAY Morning, all! The mill's turning.; DUSK: Brenn Village#1 SIT "stool" SAY Long day.; NIGHT: Brenn Village#9 SLEEP "straw bed" LOCK; SHOPMSG="The stall is shut. Come back after dawn."
```

**Behaviour `Sounder`** (greetings; one entry list in a single parameter):

```text
GREET_FIRST: SAY Fresh flour, stranger! Ask what I [[grind]].;GREET: COOLDOWN=30 CHANCE=60 3 SAY Good day, $n. | 1 EMOTE nods to $n.;SHOP_BUY: SAY A fine choice.;BRIBED: ONCEPERPLAYER SAY I saw nothing, friend.
```

**Behaviour `MudChat`:** `chat/hobb.dat=hobb`, with
`world/resources/chat/hobb.dat`:

```text
# fallback if the group is not found
(hello)
9"Hello.

>hobb
?AFFINITY($i $t > 24)
(hello|hi|morning)
9"Ah, $t! My best customer. Flour today?
(hello|hi|morning)
9"Morning. Flour is on the shelf; ask for my LIST.
(grind|grinding|mill)
9"Wheat, mostly. Barley when the cart comes. Sweetest flour in the valley.
(thief|robbed|stolen)
5"Keep your hands to yourself, $t.
4:glares.
()
3"Hm?
```

**Behaviour `Memory`:**

```text
REFUSEIF=STOLE>0 REFUSE="I remember you, thief. Out of my stall." GREETIF=MET>2 GREET="Back again, $n?" FORGET=30d
```

**Effect `Prop_Affinity`:**

```text
GIFT(flower)=+5 ATTACKED=-40 DECAY=1 SHOPFACTOR=FRIENDLY:0.9,DEVOTED:0.8 REFUSE=HOSTILE REFUSEMSG="Get out of my stall."
```

**Effect `Prop_NPCLines`:**

```text
NOTBUYING="Bah, I deal in grain, not junk."|"No use for that here." NOTBUYING="Not today, friend. Ask me again tomorrow."[AFFINITY FRIENDLY] TOOPOOR="Not enough coin for @x1, my friend."
```

How the pieces cooperate:

- Schedule decides where Hobb is and whether he trades. At the mill or at night
  a LIST gets the SHOPMSG; asleep, "Hobb looks asleep." The `SHOP` activity
  restricts trading only to the `DAY` entry.
- Sounder barks greet arrivals when he is awake and in the room; the first greeting
  teaches the topic "grind", and MudChat has an entry for it.
- The MudChat guard `?AFFINITY($i $t > 24)` (FRIENDLY starts at 25) gives friends the warmer greeting.
  Gifts of flowers raise Affinity; `SHOPFACTOR` discounts friends; Prop_NPCLines
  (also masked by affinity) rewords stock lines for friends.
- Memory's `STOLE` fact (theft from him) makes him refuse to deal; `GREETIF=MET>2`
  greets someone who has come in several times. Both Memory and Prop_Affinity
  refuse trade; keep one of them (Memory for a flat grudge, Affinity for bands).
- `TALK hobb` opens a "Trade" menu automatically because he is a shopkeeper.

Test: `WAIT UNTIL DAWN`, `WAIT UNTIL DAY`, `WAIT UNTIL NIGHT`, `STAT BEHAVIORS hobb`
at each step; try `LIST` at night; `CHATLINT chat/hobb.dat`; `CHATTEST hobb
hello`; steal from him (Memory then refuses); `GIVE` him a flower and re-read
`STAT BEHAVIORS`.

### Example 2: an informant with topics

Mara the fishwife knows the story of the wrecked ship. Each of her answers
points to the next thing to ask, using `[[topic]]` markup, so the player builds up
a list with `ASK mara ABOUT`. Flags on the player change what she says.

Create "Mara the fishwife" (a plain GenMob), add `MudChat chat/mara.dat=mara`:

```text
>mara
?VAR($t HEARD_WRECK == yes)
(wreck|ship|marigold)
9"You know it already, $t. Ask the [[harbourmaster]] who signed her out.
(hello|hi|morning)
9"Fresh catch, $t! Mind your feet. They say the [[old lighthouse]] shows a light again.
(old lighthouse|lighthouse|light)
9"Nobody has kept it since the [[wreck]]. A light at night, they say.
(wreck|shipwreck|marigold|ship)
9"The Marigold, three winters back, all hands lost.~&mpsetvar $n HEARD_WRECK yes~"Ask the [[harbourmaster]] who signed her out.
(harbourmaster|harbor master)
9"Old Venn. Drinks at the Anchor. He will not speak of it sober.
()
4"Eh? Buy a fish or move along.
1:gutters a herring.
```

Notes on the file: the guard covers only the entry right after it; the first
entry for "wreck" is the repeat version once `HEARD_WRECK` is set. The second,
unguarded one sets it with a `&` reply (a MOBPROG command run as Mara, `$n` the
player) and continues with another say. `[[...]]` in each `"` reply teaches the
topic.

Play it:

```text
talk mara
ask mara about old lighthouse
ask mara about
ask mara about wreck
ask mara about
chattest mara wreck
```

The first TALK falls through to MudChat as "Hello", teaching "old lighthouse".
`ASK mara ABOUT` then lists "old lighthouse"; after the wreck answer the list
grows to include "wreck", then "harbourmaster". The repeat answer appears once
the flag is set. `CHATTEST` shows which entry fires and why.

To hand her topics out without markup, set the tag `HINTTOPICS` on her instead:
the first time a character talks with her she lists, and teaches, the first
word or phrase of each of her chat entries (including "hello" and "wreck"). The
markup is cleaner for a quest chain, because it reveals each topic only when the
story reaches it. If a topic should open a numbered menu
instead, answer it with a script: `SPEECH_PROG harbourmaster` that runs `MPMENU`.

### Example 3: a hireling

Tarn the sellsword sits in the inn for hire: 150 gold for three days, wages of
2 a day, quits in a fight below 30 percent unless he likes you, will not drop
or give away your things. Giving him ale builds loyalty.

Behaviour `Hireling`:

```text
150;3;KEYWORDS=SWORD,MERCENARY,HIRE CONTRACT=DAYS UPKEEP=2 FLEEHP=30 LOYAL=40 MSG_OFFER="A sword for @x1, @x2. Ask me what I can do." MSG_HIRED="Done, @x1. I can do: @x2. Lead on." MSG_EXPIRE="My days are up. It's been good steel." REFUSE=DROP,GIVE MSG_NOORDER="That's not what you pay me for."
```

Effect `Prop_Affinity` (so loyalty can be earned): `GIFT(ale)=+10 TALK(brave)=+5 DECAY=1`.

Effect `Prop_NPCLines`: `ONTHEJOB="Still on your coin, boss."|"Three days, I said." SKILLS="Here is what I can do: @x1."`

Behaviour `MudChat` with a short file. Keep its words away from Hireling's own
keywords (`sword`, `mercenary`, `hire`, `work`, `time`, ...), or both would answer:

```text
>tarn
(fights|battles|past)
9"Forty fights, no regrets. Ask me about the [[goblin war]] if you want a story.
(goblin war|goblins)
9"Burned three villages before we broke them. I kept this scar.
```

Play it:

```text
talk tarn          (the menu offers "Hire (its terms)")
hire tarn          (terms as a menu; choose 1)
order tarn kill rat
say hire           (on the job: he says how long)
wait 2 days        (watch the wages; give him ale)
```

Things to check: he follows you; refusing `ORDER tarn drop sword`; below 30
percent of his hit points in a fight he quits unless `AFFINITY` is at least 40
(give him ale first); after two unpaid days he leaves; at expiry he returns to his
start room (`allDone` walks him home). Remember that `CONTRACT=DAYS` makes the
second number days, not minutes.

### Example 4: an escort quest NPC

Pell the courier must reach Riverford. He waits at the inn until the player
agrees, follows, remarks at a bridge, cowers if attacked, avoids the bog road,
and on arrival thanks the party and sets a flag the quest can check.

On Pell, behaviour `Escortee`:

```text
DEST=Riverford#1 MODE=FOLLOW WAITFOR=LEADER WAITCALL=3 WAITMSG="Wait for me!" PANIC=COWER AVOID=Bog#1 TALK="Bridge#3:They say wolves hunt near the bridge." TIMEOUT=900 ONTIMEOUT="say I have lost the day. Farewell.;mpsetvar $n ESCORT_FAILED 1" ONDEATH="mpsetvar $n ESCORT_FAILED 1" ONARRIVE="say Thank you, friends! Here is what I promised.;mpsetvar $n ESCORTED 1"
```

Behaviour `Scriptable` with the start of the escort:

```text
SPEECH_PROG riverford
if VAR($n ESCORTED == 1)
  say You already brought me, $n. Thank you again.
else
  say I must reach [[Riverford]] by the river road. Keep close, I will follow you.
  mpforce $i follow $n
endif
~
```

(To pay a reward through a quest, add `mpquestwin $n <quest name>` to
`ONARRIVE`; the quest itself is defined as in [08](08-quests.md). Quests can
also start the escort with `MPFORCE`.)

Test cases: stand still (after 3 ticks he calls "Wait for me!" and does not go
on without you); attack him (he cowers); try the bog road (he refuses with "I
won't go there."); reach Riverford#1 (ONARRIVE runs, he stops following); let
the timeout run; save and restore mid-escort (leader, tick count and remarks
survive). For a courier who walks ahead instead, use `MODE=LEAD`; he then walks
the shortest way to `DEST` himself, a step a tick, and `WAITFOR=LEADER` makes
him stop when you fall behind.

### Example 5: an ambient town crier

The crier walks the square by day ringing a bell and calling news, barks at the
dawn, dusk and rain, chats with a guard now and then for someone who is
listening, and goes to bed at night.

On the crier:

**`Patroller`** (day only, using the behaviour gate; the path is room IDs of the square):

```text
TOD=DAWN,DAY,DUSK min=8 max=8 chance=100;Brenn Village#10;WAIT=2;EMOTE=rings his handbell.;DO=say Hear ye, hear ye!;Brenn Village#11;WAIT=2;Brenn Village#12;RESTART
```

**`Schedule`** (so that he is in bed at night; he stays where he is by day, when
no entry is in force):

```text
NIGHT: Brenn Village#14 SLEEP "cot"
```

**`Sounder`** (barks; one parameter):

```text
DAWN: SAY Hear ye! A new day dawns on Brenn!;DUSK: SAY The gates close at dark!;RAIN: SAY Mind the mud, good folk!;GREET_FIRST: SAY Hear ye, stranger! The [[harvest fair]] opens soon!;IDLE: CHANCE=2 COOLDOWN=40 3 SAY Hear ye, hear ye! | 1 EMOTE rings a handbell.
```

**`Emoter`** (ambience when nobody is talking; quiet during combat or when the
player is reading):

```text
min=30 max=60 chance=60 NOREPEAT=1 QUIETAFTER=3;rings a handbell.;clears his throat.;DAWN SOUND a bell tolls in the square.
```

**`Scene`** (he and a guard bicker for anyone who walks in; once every two days;
daytime only; the player can interrupt by speaking to either):

```text
NAME=crier;ACTOR crier=SELF;ACTOR guard=-NAMES +"town guard";crier: say Hear ye! The [[harvest fair]] opens on Saturday!;guard: say Again? He said that an hour ago.;PAUSE 1;crier: emote glares at the guard.;REPEAT=2;INTERRUPTIBLE;TOD=DAY
```

**`MudChat`** (questions): `chat/crier.dat=crier` with

```text
>crier
(fair|harvest)
9"Saturday at noon, in the square! Pies, races, and a prize for the best [[scarecrow]].
(scarecrow)
9"Straw and old coats. Entries to the reeve, by Friday.
(news|hear)
9"Hear ye: the road to Riverford is clear, the bog road is not.
```

Notes: the `[[harvest fair]]` topic is taught by the Sounder greeting and by the
Scene, and the MudChat file answers it. Since the Patroller is gated by `TOD`, it
stops entirely at night while the Schedule's `NIGHT` entry takes the crier to
bed; by day Schedule has no entry in force and leaves him alone. Come back
from the dungeon after a night and the area catch-up places him: `Schedule`
puts him at the cot or the Patroller picks up the steps it missed.
Test with `WAIT UNTIL DAWN`, `WAIT UNTIL DUSK`, `WAIT UNTIL NIGHT`, `TRACEB crier 40`
and `STAT BEHAVIORS crier`.

## Persistence notes

| What | Where it lives | A reload or restore does |
| --- | --- | --- |
| Behaviour parameters, Prop_* text | The mob's definition in the world file (`.cmare`), or in the campaign save for mobs created in play | World load reads them; `SAVE` writes them. |
| What a behaviour has built up (Schedule's locked room, Memory's facts, Escortee's leader and progress, Hireling's contract and wages, Sounder's greeted names, Scene's progress, Patroller's step, MudChat's temporary group switches) | The campaign save (the behaviour's STATE) | Restored with the save; not in world files. |
| Memory facts | NPC script variables `MEM_*` (mirrored) | Kept in a saved game. |
| Prop_Affinity values | NPC/area script variables `AFFINITY_*` | Kept in a saved game. |
| Topics a character knows | Character variable `KNOWN_TOPICS` | Kept with the character. |
| Kept `$@!` chat groups | NPC variable `MUDCHAT_GROUP_*` | Kept; read back after a restore. |
| Pending MPMENU choices | Saved with the displayed options and source | Reissued on restore, or visibly cancelled if the source changed or is gone. |
| Chat files, bark `FILE=`, scene `FILE=` | `resources/` or `world/resources/` | Read when first needed; reload with `CHAT RELOAD` (chat) or restart. Builder mode re-reads an edited chat file. |
| Generic behaviours | `custom/behaviors/` in the world folder | `SAVE` writes them; `DESTROY BEHAVIOR` leaves copies in place until the world is loaded again. |

Run-state is saved when the campaign is saved, not when the world is saved with
`SAVE ROOM`; see [state-and-persistence.md](state-and-persistence.md) and
[31](31-saves-and-party.md). Edit a behaviour's parameters and an existing
instance reparses them; a changed schedule starts again from its new entries.

## Troubleshooting and gotchas

- **The NPC never answers MudChat.** Run `CHATTEST npc what-you-said`. Common
  causes: three or more players in the room and you used SAY (use SAYTO, ASK or
  TALK); another NPC just spoke; the NPC cannot see or hear you or is asleep;
  the group was not found and the first group's entries do not match; an earlier
  entry matched first; the language is not understood; `DISABLE=MUDCHAT`.
- **Wrong group.** Group names match the whole NPC name (article removed), then
  race, then class. Give the group in the parameters: `file=group`.
- **Two replies.** MudChat and a SPEECH_PROG both matched. See
  [mixing](#dialogue-with-mobprog).
- **A tick line comes out as "sayto" a name.** Tick entries need `"` or `:` on
  their replies.
- **`$@` seems ignored.** It must end the last reply segment, and the group
  must exist in the same file under that name.
- **Case in zapper masks inside patterns.** Speech is lower-cased before
  matching, so `/+NAME Bob/` style masks in a pattern may not match; use a
  guard line (`?`) with a script condition instead.
- **`Schedule: no room ...` in the log.** The room ID is wrong, or the area is
  not loaded.
- **The shopkeeper is closed all day.** With `SHOP` in some entries only those
  entries open the shop. No entry in force or one without `SHOP` is closed;
  see `STAT BEHAVIORS`.
- **A routine breaks after a fight.** The NPC goes back to it after a fight;
  an Alerted NPC pauses Schedule and Patroller for the alert time.
- **TOD= makes my Sleeper never sleep.** Use `SLEEPS=NIGHT`, not `TOD=NIGHT`,
  inside Sleeper's parameters.
- **A condition on a script behaviour does nothing.** `Scriptable` ignores
  `WHEN/TOD/HOURS`; put the test in the script.
- **Topics do not list.** `ASK npc ABOUT` lists only topics the character
  knows *and* the NPC answers (a SPEECH_PROG word, TALK_PROG word, or a MudChat
  entry with those words). A `()` catch-all does not count.
- **Menu answers do nothing.** MPMENU runs a `FUNCTION_PROG` of the same script
  with the key as `$0`; the function name must match exactly. See the pending
  menu notes above. One menu per character at a time.
- **Hireling price is read as 100.** The first parameter must be a number; the
  `KEY=value` terms come after `price;minutes;`.
- **The hireling has no `HIRE` menu.** The menu appears only for a Hireling that
  is not already working.
- **Replies feel late in turn-based play.** Check `TIMEMODE DIALOGUE`.
  `INSTANT` is the default in turn-based time.
- **A bark never fires.** Check the event name against the table, the cooldown,
  the chance and the mask; `IDLE` needs a `CHANCE=`; bark text cannot contain
  `;` or `|`.
- **Escort stalls.** The leader is whoever it follows; if it never followed
  anyone (`mpforce $i follow $n` never ran) it is "not yet escorted" (see
  `STAT BEHAVIORS`). In `FOLLOW` mode it never walks on its own.
- **Facts in Memory disappeared.** `FORGET=` fades them; they are read lazily.

## What is not in CoffeeSUD

These appear in the audit as planned and are deferred; they are **not
implemented**, so do not rely on them (the planning notes are in the
[audit](../../audit/N-npc-behaviour-and-dialogue.md)):

- A **Companion behaviour** for NPC party members who take orders and comment
  (N13). Use Hireling, scripts and the party features instead.
- **Dialogue trees**: a `Dialogue` behaviour, `.dlg` files and `MPDIALOG` (N14).
  Use MudChat with guards, MPMENU and scripts. The TALK command already falls
  back to MudChat.
- A **rumour** list with gossiping NPCs (N23).
- **LLM NPCs**: off-thread calls, saved memory per player, whitelisted actions
  (N25 to N27). The inherited MudChat file syntax still has `L`, `.` and the
  `` ` `` reply operator for an LLM integration, but CoffeeSUD does not provide
  one; entries marked `.` are active and those marked `L` never fire.
- Chat between players, the web and telnet front ends, and anything else removed
  with multiplayer ([18](18-installing-and-running.md)).

## See also

- [21 NPCs and mobs](21-mobs.md): creating the NPC itself, shopkeepers, bankers, MOBTeacher.
- [24 behaviours and masks](24-behaviors-and-masks.md) and [23 properties](23-properties-reference.md): the reference entries linked throughout.
- [04 MOBPROG](04-mobprog.md), [26 triggers](26-mobprog-triggers.md), [27 commands](27-mobprog-commands.md), [28 functions](28-mobprog-functions.md): scripting dialogue.
- [05 JavaScript](05-javascript.md): `catchUp` hooks and JS behaviours.
- [08 quests](08-quests.md): quest givers, MPSTARTQUEST, MPQUESTWIN, markers.
- [09 society](09-society.md): factions, law and shops (group opinions, prices).
- [10 world simulation](10-world-simulation.md): the clock, weather and seasons that WHEN/Schedule read.
- [11 presentation and language](11-presentation-language.md) and [languages](../languages.md): speech, translation and what NPCs understand.
- [29 combat and encounters](29-combat-and-encounters.md): combat AI, bosses, morale (formerly the second half of this chapter).
- [31 saves and party](31-saves-and-party.md): party members, CONTROL, what a save keeps.
- [13 testing](13-testing-release.md): playtest files that drive conversations.
- [cookbook](cookbook.md): a complete MPMENU conversation in the museum pack.
- The inherited [MUDChat manual](../../CoffeeMud/guides/MUDChat.html) and [ScriptingSUD](../../CoffeeMud/guides/ScriptingSUD.html).
