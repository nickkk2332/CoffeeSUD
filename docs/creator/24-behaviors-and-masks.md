# Behaviors and zapper masks

This chapter is the reference for two things that appear all over CoffeeSUD
content. Part 1 documents every behavior: the small Java (or JavaScript) classes
that make a mob, item, room, area or exit do something on its own, with the full
parameter syntax of each. Part 2 documents the zapper mask language: the
`-RACE +Elf`, `+LEVEL ->5` style expressions that behaviors, properties,
catalog rules, quests and scripts use to say "who or what does this apply to".
It is written for world creators who configure behaviors, scripters who add
them from MOBPROG or JavaScript, and engine developers who write new ones.

## In this chapter

- [How behaviors work](#how-behaviors-work)
  - [Attaching a behavior](#attaching-a-behavior)
  - [Parameters](#parameters)
  - [Timing and the common parameters](#timing-and-the-common-parameters)
  - [The WHEN, TOD and HOURS gate](#the-when-tod-and-hours-gate)
  - [Locale restriction words](#locale-restriction-words)
  - [How behaviors save](#how-behaviors-save)
  - [Inspecting and debugging](#inspecting-and-debugging)
- [Behaviors by purpose](#behaviors-by-purpose)
- [Behavior reference](#behavior-reference) (one entry per behavior, A to Z)
- [Zapper masks](#zapper-masks)
  - [Mask syntax](#mask-syntax)
  - [How a mask is evaluated](#how-a-mask-is-evaluated)
  - [Mask key reference](#mask-key-reference)
  - [Where masks are used](#where-masks-are-used)
  - [Mask examples](#mask-examples)
  - [Mask gotchas](#mask-gotchas)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

# Part 1: Behaviors

## How behaviors work

A behavior is a `Behavior` object (interface
[Behavior](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/interfaces/Behavior.java),
base class
[StdBehavior](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/StdBehavior.java))
attached to a host. The host can be a mob, an item, a room, an area or an exit,
though each behavior only makes sense on the hosts it was written for (see
"Hosts" in each entry). A behavior has three working parts:

1. A parameter string (free text, parsed by the behavior itself).
2. A `tick` method, called by the host every game tick, in which a timed
   behavior decides whether to act.
3. Message hooks (`okMessage` and `executeMsg`) through which it sees and can
   veto what happens around its host, such as a get, a movement, an attack.

A host can carry any number of behaviors, including several of the same ID.
Behaviors are different from properties (the `Prop_*` abilities of
[chapter 23](23-properties-reference.md)): a property is an effect that changes
stats or messages, a behavior is a proactive actor. In practice the two overlap,
and many behaviors install a property on their host while they run.

### Attaching a behavior

| Way | How |
| --- | --- |
| Builder editor on a mob, item or room | `CREATE` or `MODIFY` the thing; the editor has a `Behaviors` line. Enter a behavior ID to add it; the editor then asks for its parameters (`?` there prints the behavior's help). Entering the ID of a behavior the thing already has removes it, so to change parameters remove it, then add it again. `MODIFY ROOM BEHAVIORS` and `MODIFY AREA BEHAVIORS` go straight to that line. `?` at the `Enter a behavior to add/remove` prompt lists every behavior class (the ones valid for this host are highlighted). |
| MOBPROG | `MPBEHAVE [BEHAVIOR] [MOB NAME] [PARMS]` adds, `MPUNBEHAVE [BEHAVIOR] [MOB NAME]` removes. See [chapter 27](27-mobprog-commands.md). The `ISBEHAVE([CODE] [BEHAVIOR CLASS])` condition tests for one. |
| Quests | A quest script can give a behavior to a mob it designates (`GIVE BEHAVIOR`). See [chapter 8](08-quests.md). |
| World files | Saved as a `BEHAVES` block of the host's XML: `<BEHAVES><BHAVE><BCLASS>Mobile</BCLASS><BPARMS>...</BPARMS></BHAVE></BEHAVES>`. Every mob, item, room, area and exit in `world/` carries one; an empty one is `<BEHAVES />`. See [chapter 2](02-world-workflow.md). |
| JavaScript | `var b = CMClass.getBehavior("Mobile"); b.setParms("min=10 max=20"); mob.addBehavior(b);` (the same calls the test fixtures use). See [chapter 5](05-javascript.md). |
| Other behaviors and properties | `BossPhases` (`BEHAVE` action), `Faction`/plane definitions and `RoomStates` (`ADD=`) attach behaviors on their own. |

A behavior flagged unsavable (for example one a quest or `RoomStates` added
temporarily) is not written to the world file.

Hosts a behavior is valid for come from its `canImproveCode()`. The editor uses
this to highlight the right ones and quests check for mob hosts; the engine
does not otherwise stop you attaching a behavior to a host it was not meant for,
it simply does nothing useful there.

### Parameters

Every behavior receives one raw text string through `setParms`. There is no
schema: each class parses what it wants and ignores the rest. The conventions
that most classes share:

- `KEY=value` pairs, key case-insensitive. A value with spaces goes in double
  quotes: `MESSAGE="Die, fiend!"`. The reader is deliberately loose: a key
  matches by its start (so `min` also matches `minitems=3` when no earlier
  `min=` exists, see [Troubleshooting](#troubleshooting)), it must not be
  preceded by a letter, and an unquoted value ends at a space, `;` or `:`.
- Bare flag words: `WANDER`, `OPENDOORS`, `MOBKILL`. Case-insensitive.
- A zapper mask ([Part 2](#zapper-masks)) as the tail of the string, such as
  `min=10 max=20 chance=75 -RACE +Elf`.
- Some behaviors split the string at the first `;` into "settings;list": the
  `Emoter`, `Sounder`, `DelayedTransporter` and `Patroller` families, and
  `RandomMonsters`, `RandomItems`, `RandomTraps`, `Scavenger`, the
  `*Abilities` behaviors and `ProtectedCitizen(s)`.
- A few take the whole text as one value (`PuddleMaker`: a percent;
  `LinkedWeather`: an area name; `Scriptable`: a script).

The parameter text is saved verbatim, so what you typed is what you get back.
`STAT BEHAVIORS [mob|item|HERE]` tells you when a key in the parameters is not
one the behavior's help names.

### Timing and the common parameters

Most behaviors that act "every so often" extend `ActiveTicker`, which reads
these parameters:

| Parameter | Meaning | Default of ActiveTicker |
| --- | --- | --- |
| `MIN=n` | Fewest ticks between acts | 10 |
| `MAX=n` | Most ticks between acts | 30 |
| `CHANCE=n` | Percent chance the act happens when the countdown ends | 100 |
| `MINGRP=n` | The behavior only acts once per n countdowns (rarely used) | 1 |

A tick is the engine's basic time unit: 4000 milliseconds unless the
`TICKTIME` setting in coffeemud.ini says otherwise. Each time the countdown ends
the next one is picked at random from MIN to MAX, then a percentage roll
against CHANCE decides whether the act happens. An asleep, bound or otherwise
incapacitated mob host does not act. Subclasses override the defaults; each
entry below lists its own as `min/max/chance`.

The behaviors that extend ActiveTicker are: AntiVagrant, CombatAbilities and
its presets, CorpseEater, Decay, DelayedTransporter, Emoter, FieryRoom,
FightFlee, Follower, GetsAllEquipped, Healer, InstantDeath, ItemGenerator,
MOBEater, MOBHunter, MOBReSave, Mime, Mobile and its subclasses, MovingRoom,
the three `*Abilities`, Patroller, ProtectedCitizen(s), RandomItems,
RandomMonsters, RandomQuests, RandomTeleporter, RandomTraps, ResourceOverride,
Scavenger, TargetPlayer and WaterCurrents.

### The WHEN, TOD and HOURS gate

CoffeeSUD adds three parameters that work on any behavior except a plain
`Scriptable` (class
[BehaviorWhen](../../CoffeeMud/com/planet_ink/coffee_mud/core/BehaviorWhen.java)):

| Parameter | Meaning |
| --- | --- |
| `WHEN="[ZAPPER MASK]"` | A mask tested on the host (mob, item, room, area or exit). Use time keys such as `-HOUR`, `-SEASON`, `-WEATHER`, `-HOLIDAY`. |
| `TOD=DAWN,DAY,DUSK,NIGHT` | One time of day, or several joined by commas, on the host's clock. |
| `HOURS=a-b` | A range of hours on the host's clock; if `a` is past `b` it runs round midnight (`HOURS=22-4`). |

While any condition fails the behavior neither ticks nor hears messages (it is
switched off, not removed). There is no "unless", so for the opposite case give
the host a second copy with the opposite condition: a werewolf with
`MobileAggressive TOD=NIGHT` and `Mobile TOD=DAY`. `STAT BEHAVIORS` says whether
each gate is open now. `RoomStates`, `TidalRoom` and `Floodable` use `WHEN=`
for their own purpose and are not gated; `Regenerate` also uses `WHEN=`, but
its words are not a mask (they compile to an empty mask, which always passes),
so it is effectively never gated. For a generic (`GenBehavior`) behavior the
gate keys are among its normal parameters.

```text
MobileAggressive TOD=NIGHT -NPC
Aggressive WHEN="+WEATHER -THUNDERSTORM"
Emoter HOURS=22-4 min=5 max=10 chance=50;howls at the moon.
```

### Locale restriction words

Several movement and spawning behaviors (Mobile, MobileAggressive,
MobileGoodGuardian, RandomTeleporter, ItemGenerator, RandomItems,
RandomMonsters, RandomTraps) accept locale words in their parameters. They are
`+WORD` and `-WORD` tokens, where WORD is the start of a room type name:

- `-ALL` forbids every locale; `+ALL` clears the list.
- `-CITY` forbids city rooms; `+CITY` removes that restriction. The usual
  recipe is `-ALL +CITY +WOODS` ("only city and woods rooms").
- The names (prefix-matched, case-insensitive) are the outdoor types `CITY`,
  `WOODS`, `ROCKY`, `PLAINS`, `UNDERWATER`, `AIR`, `WATERSURFACE`, `JUNGLE`,
  `SWAMP`, `DESERT`, `HILLS`, `MOUNTAINS`, `SPACEPORT`, `SEAPORT` and the
  indoor types `STONE`, `WOODEN`, `CAVE`, `MAGIC`, `IN_UNDERWATER`, `GAP`,
  `CAVELAKESURFACE`, `METAL`, `INNERSEAPORT`, `CAVESEAPORT`.

These are not zapper masks (although `-ALL` looks like one). In the spawner
behaviors the locale words stand in the settings part before the first `;`
(RandomMonsters and RandomItems read them from the part after the file field;
see their entries).

### How behaviors save

There are two layers.

1. **Definition.** A behavior on a mob, item, room, area or exit is saved as
   its ID and its parameter string (`BCLASS` and `BPARMS`) in the host's XML,
   both in world files and in campaign saves. A reload or restore recreates it
   from that and calls `setParms` again.
2. **Run state.** What a behavior has built up while running and that is not in
   its parameters (a patrol's position, a countdown, who was greeted, a
   hireling's contract) is written by the behavior's `STATE` stat (the
   protected `getRunState` method) and put back by `setRunState`. A campaign
   save writes one `<BSTATE ID="..." N="...">` for each behavior with state,
   under the host's `STATES` block, keyed by the behavior's ID and its position
   among behaviors of the same ID. Times in it are game time left, so they mean
   the same after a restore. World files do not carry run state.

Behaviors with run state: Aggressive (and VeryAggressive and WimpyAggressive:
paid bribes), Arrest,
Conquerable, BossPhases, BribeGateGuard, CombatAbilities (and presets: round
and rotation history), Concierge, Decay, DelayedTransporter, Emoter, Escortee,
FieryRoom, Hireling, MOBEater, Memory, MovingRoom, MudChat, Nanny, Patroller,
RandomQuests, Regenerate, ResetWhole, RoomStates (and TidalRoom, Floodable),
Scene, Schedule, Scriptable (script state), Sounder,
TransportLine and WaveSpawner. Others restart from their parameters. See
[state and persistence](state-and-persistence.md) for the wider picture.

### Inspecting and debugging

| Command | What it shows |
| --- | --- |
| `STAT BEHAVIORS [MOB/ITEM/HERE]` | Each behavior with its parameters and run state; for a timed one in how many ticks it acts, its range, its chance and when it last acted; whether its WHEN/TOD/HOURS gate is open; parameter keys its help does not name are listed as unknown. |
| `TRACEB [MOB/ITEM/HERE] ([TICKS])` | For 20 ticks (or the number given), each decision of the host's timed behaviors: time came, acted, could not act (asleep or bound), or lost its chance roll. `TRACEB OFF` stops it. |
| `LIST BEHAVIORS` | Every behavior class installed. |
| `AHELP BEHAVIOR_[ID]` | The shipped help for one behavior, for example `AHELP BEHAVIOR_MOBILE` (the help source is [arc_behaviors.ini](../../CoffeeMud/resources/help/arc_behaviors.ini)). |

## Behaviors by purpose

| Purpose | Behaviors |
| --- | --- |
| Wandering and movement | [Mobile](#mobile), [MobileAggressive](#mobileaggressive), [MobileGoodGuardian](#mobilegoodguardian), [Follower](#follower), [Patroller](#patroller), [RandomTeleporter](#randomteleporter), [Pursue](#pursue), [WaterCurrents](#watercurrents), [Vagrant](#vagrant), [Escortee](#escortee), [Schedule](#schedule) |
| Aggression | [Aggressive](#aggressive), [VeryAggressive](#veryaggressive), [WimpyAggressive](#wimpyaggressive), [MobileAggressive](#mobileaggressive), [ClanEnemyAggressive](#clanenemyaggressive), [EvilExecutioner](#evilexecutioner), [GoodExecutioner](#goodexecutioner), [MOBHunter](#mobhunter), [MOBEater](#mobeater), [TargetPlayer](#targetplayer), [InstantDeath](#instantdeath), [ROMGangMember](#romgangmember) |
| Fleeing and morale | [Wimpy](#wimpy), [FightFlee](#fightflee), [Morale](#morale), [EncounterReset](#encounterreset), [BossPhases](#bossphases) |
| Combat AI | [CombatAbilities](#combatabilities), [Fighterness](#fighterness), [Mageness](#mageness), [Clericness](#clericness), [Druidness](#druidness), [Thiefness](#thiefness), [Bardness](#bardness), [Drowness](#drowness), [NastyAbilities](#nastyabilities), [NeutralAbilities](#neutralabilities), [NiceAbilities](#niceabilities), [Healer](#healer), [TargetPriority](#targetpriority), [TargetPlayer](#targetplayer), [NoCombatAssist](#nocombatassist), [Sailor](#sailor) |
| Helping others in a fight | [Guard](#guard), [MOBHelper](#mobhelper), [PlayerHelper](#playerhelper), [BrotherHelper](#brotherhelper), [RaceHelper](#racehelper), [AlignHelper](#alignhelper), [ClanHelper](#clanhelper), [FaithHelper](#faithhelper), [CombatAssister](#combatassister), [GoodGuardian](#goodguardian), [ProtectedCitizen](#protectedcitizen), [ProtectedCitizens](#protectedcitizens), [ROMPatrolman](#rompatrolman) |
| Guarding places and things | [GateGuard](#gateguard), [BribeGateGuard](#bribegateguard), [DoorwayGuardian](#doorwayguardian), [ObjectGuardian](#objectguardian), [Sentry](#sentry), [Sleeper](#sleeper), [AntiVagrant](#antivagrant) |
| Social, dialogue and presence | [MudChat](#mudchat), [QuestChat](#questchat), [Memory](#memory), [Riddler](#riddler), [Scene](#scene), [Beggar](#beggar), [Mime](#mime), [CommonSpeaker](#commonspeaker), [Emoter](#emoter), [Sounder](#sounder), [Hireling](#hireling), [Escortee](#escortee) |
| Economy and services | [MoneyChanger](#moneychanger), [ItemMender](#itemmender), [ItemRefitter](#itemrefitter), [ItemIdentifier](#itemidentifier), [ItemUpgrader](#itemupgrader), [MOBTeacher](#mobteacher), [Healer](#healer), [Nanny](#nanny), [PokerDealer](#pokerdealer), [Concierge](#concierge), [TaxiBehavior](#taxibehavior), [TaxCollector](#taxcollector), [GuildMaster](#guildmaster), [Hireling](#hireling) |
| Law and society | [Arrest](#arrest), [Conquerable](#conquerable), [TaxCollector](#taxcollector), [GuildMaster](#guildmaster), [ClanHelper](#clanhelper), [ClanEnemyAggressive](#clanenemyaggressive), [FaithHelper](#faithhelper) |
| Travel and transport | [Concierge](#concierge), [TaxiBehavior](#taxibehavior), [TransportLine](#transportline), [DelayedTransporter](#delayedtransporter), [MovingRoom](#movingroom), [Sailor](#sailor), [Patroller](#patroller), [RandomTeleporter](#randomteleporter) |
| Generation and spawning | [RandomMonsters](#randommonsters), [RandomItems](#randomitems), [ItemGenerator](#itemgenerator), [RandomTraps](#randomtraps), [RandomQuests](#randomquests), [RandomEncounters](#randomencounters), [WaveSpawner](#wavespawner), [ResourceOverride](#resourceoverride), [Regenerate](#regenerate), [ResetWhole](#resetwhole), [MOBReSave](#mobresave), [Decay](#decay) |
| Environment and weather | [WeatherAffects](#weatheraffects), [PuddleMaker](#puddlemaker), [LinkedWeather](#linkedweather), [WaterCurrents](#watercurrents), [FieryRoom](#fieryroom), [RoomStates](#roomstates), [TidalRoom](#tidalroom), [Floodable](#floodable), [FasterRecovery](#fasterrecovery) |
| Looting and items | [Scavenger](#scavenger), [GetsAllEquipped](#getsallequipped), [GoldLooter](#goldlooter), [CorpseLooter](#corpselooter), [CorpseEater](#corpseeater), [Decay](#decay), [Follower](#follower) (items), [Patroller](#patroller) (items) |
| Scripting | [Scriptable](#scriptable), [ScriptableEverymob](#scriptableeverymob), [JScriptable](#jscriptable), [JSBehavior](#jsbehavior), [JSWorldBehavior](#jsworldbehavior), [GenBehavior](#genbehavior) |
| Base classes (not for direct use) | [StdBehavior](#stdbehavior), [ActiveTicker](#activeticker) |
| CoffeeSUD additions | [BossPhases](#bossphases), [ClanEnemyAggressive](#clanenemyaggressive), [EncounterReset](#encounterreset), [Escortee](#escortee), [Floodable](#floodable), [GenBehavior](#genbehavior), [GuildMaster](#guildmaster), [ItemUpgrader](#itemupgrader), [JSBehavior](#jsbehavior), [JScriptable](#jscriptable), [JSWorldBehavior](#jsworldbehavior), [Memory](#memory), [Morale](#morale), [Pursue](#pursue), [RandomEncounters](#randomencounters), [Regenerate](#regenerate), [Riddler](#riddler), [RoomStates](#roomstates), [Scene](#scene), [Schedule](#schedule), [Sentry](#sentry), [Sleeper](#sleeper), [TargetPriority](#targetpriority), [TidalRoom](#tidalroom), [TransportLine](#transportline), [WaveSpawner](#wavespawner), and the CoffeeSUD options added to existing ones (listed in their entries). |

Not behaviors: the `Dialogue` and `Companion` behaviors proposed in the audit
(N13, N14) do not exist in this build; use [MudChat](#mudchat), [Scene](#scene),
[Hireling](#hireling) and the TALK command ([chapter 6](06-npcs-dialogue.md)).

## Behavior reference

Entries are alphabetical (case-insensitive). Each gives hosts, what the
behavior does, its parameters with defaults, and an example. "Timing" means the
ActiveTicker parameters `MIN`, `MAX`, `CHANCE` (see
[above](#timing-and-the-common-parameters)); where a default is given as
`min/max/chance` it replaces ActiveTicker's 10/30/100. Where an entry says
"see the help", the full text is the matching `BEHAVIOR_[ID]` entry in
[arc_behaviors.ini](../../CoffeeMud/resources/help/arc_behaviors.ini).

### ActiveTicker

Hosts: all (base class). Not for direct use. It supplies `MIN`, `MAX`, `CHANCE`
and `MINGRP` to the behaviors that extend it (see
[Timing](#timing-and-the-common-parameters)). Attaching it alone does nothing.

### Aggressive

Hosts: mobs. Makes the mob attack any player it can detect in its room, from
the moment the player arrives (or the mob does). Badly wounded mobs are not
aggressive, and mobs ignore archons and builders who hold the `CMDMOBS` security
flag. With no parameters it attacks anyone. A mask (Part 2) decides who:
allowed is attacked, disallowed is spared.

| Parameter | Meaning |
| --- | --- |
| `MESSAGE=[msg]` | Said by the mob when it starts a fight. |
| `DELAY=x` | Ticks to wait before looking for a victim (default 0). |
| `MOBKILL` | Attack other mobs as well as players (`MOBKILLER` also works). |
| `NOGANG` | Skip players already in combat (`NOGANGUP` also works). |
| `MISBEHAVE` | Attack even when wounded or following someone. |
| `CHECKLEVEL` | Only attack those within 6 levels below the mob. |
| `WANDER` | Flag read by the mob-moving subclasses. |
| `BRIBE=[AMOUNT],[TICKS OF PEACE],[TICKS TO PAY]` | The mob demands a bribe instead. Amount is base gold in the mob's currency (default 1), peace is how many ticks a payment buys (default 100), pay window is how long a new arrival has before ordinary aggression resumes (default 20000). |
| `BRIBEPAYMSG="..."` | Told to newly met players; `@x1` is the amount. Default "Pay me @x1 or I will kill you." |
| `BRIBEPAIDMSG="..."` | Told to a payer; `@x1` is the peace time. Default "Ok then, you are safe for @x1." |
| `WARN="[LINE]\|[LINE]"` | CoffeeSUD: a warning first, one line a tick, then the attack if the target is still there. |
| `WARNTICKS=[N]` | How many ticks the warning lasts (defaults to the number of lines). |
| `BACKOFF=true` | One who sits down or holds no weapon is not attacked while they stay so, and is warned afresh if they stop. |
| `NEAR=1` | The rooms next door hear the warning. |

Any word starting with `+` or `-` begins the mask; the mask runs to the end of
the text. VeryAggressive, WimpyAggressive and MobileAggressive take the same
flags (MobileAggressive reads its own copy). Paid bribes are saved.

```text
Aggressive +elf -fighter
Aggressive message="All must die!!" -elf +fighter
Aggressive MOBKILL MISBEHAVE +elf -fighter
Aggressive WARN="The wolf bares its teeth.|The wolf snarls and crouches!" WARNTICKS=2 BACKOFF=true +ITEM "-raw meat"
Aggressive BRIBE=50,300,40 BRIBEPAYMSG="Give me @x1 or I will clobber you!"
```

### AlignHelper

Hosts: mobs. Joins a fight on the side of anyone of the same alignment as the
mob. Parameters: an optional bare number for the most combatants that may join
this way, and `MSG="[ATTACK MESSAGE]"`.

```text
AlignHelper 5 msg="OMG I'M ATTACKING!"
```

### AntiVagrant

Hosts: mobs. Wakes any mob sleeping on a city street (a `CityStreet` locale)
and stands up anyone sitting there. Timing default 2/3/99. Flags:

| Flag | Meaning |
| --- | --- |
| `ANYWHERE` | Works in any locale, not only city streets. |
| `KICKOUT` (any word starting `KICK`) | Also kicks the person out of the room. |

```text
AntiVagrant min=2 max=3 chance=75 ANYWHERE KICKOUT
```

### Arrest

Hosts: areas. Sets up law enforcement for the area: judges, officers, jails,
laws, punishments and messages come from a laws file. The parameter is the name
of an ini file in `resources/` (default `laws.ini`), optionally followed by
`;KEY=VALUE` overrides. The word `custom` makes the law editable with a
`StdLawBook` or `GenLawBook` in builder mode.

CoffeeSUD stores a law as world content: the file name and overrides are kept
in the area's `BEHAVES`. A custom law edited with a law book in builder mode is
written to `world/laws/[AREA].ini` (the area's name), which then is the law. In
a running game the law as saved wins (changes after a conquest stay in the
save), and a new game starts from the file. Arrest keeps run state (warrants,
fines, jail and case records). See [chapter 9](09-society.md).

```text
Arrest
Arrest custom
Arrest laws_mycity.ini
Arrest custom;JUDGE="justice judge" OFFICERS="cityguard police"
Arrest laws_riverton.ini;SALESTAX=5 OFFICERS="cityguard"
```

### Bardness

Hosts: mobs. A [CombatAbilities](#combatabilities) preset that makes the mob a
Bard by class and gives it songs suited to its level. Parameters as for the
presets (see [Fighterness](#fighterness)).

```text
Bardness Dancer MIXEDOFFENSIVE
```

### Beggar

Hosts: mobs. No parameters. The mob begs for money from players and says thank
you when rewarded.

### BossPhases

Hosts: mobs. CoffeeSUD. A boss's phases, each firing once per fight.
Parameters: triggers with actions, separated by `;`, plus an optional reset
rule.

```text
[TRIGGER]: [ACTION], [ACTION]...; [TRIGGER]: ...; (RESET=FULLHEAL/LEAVECOMBAT/LEASH/NEVER)
```

| Trigger | Fires when |
| --- | --- |
| `ONENGAGE` | The fight starts. |
| `AT=[N]%` | Its hit points drop under N percent. |
| `AT=ROUND [N]` | The fight's Nth round. |
| `AT=ADDSDEAD` | Everything it summoned is dead. |
| `ONDEATH` | It dies. |

| Action | Effect |
| --- | --- |
| `SAY`, `EMOTE`, `YELL [TEXT]` | Speech or an emote. |
| `CAST [ABILITY]` | On its foe, or on itself if the ability helps. |
| `SUMMON [CATALOG NAME or mob class] x[N]` | Summons N; they follow it, attack its foe, are tagged `SUMMONED`, and go when the phases rearm. |
| `BEHAVE [BEHAVIOR] ([PARMS])`, `UNBEHAVE [BEHAVIOR]` | Add or remove a behavior. |
| `AFFECT [ABILITY] ("[PARMS]") ([TICKS])`, `REMOVE [ABILITY]` | On itself. |
| `IMMUNE [TICKS]` | No damage for that long. |
| `HEAL [N]%` | Heal itself. |
| `MOVETO [ROOM ID]` | Move. |
| `ROOM EMOTE [TEXT]`, `ROOM AFFECT [ABILITY] ([TICKS])` | Act on the room. |
| `CALL [FUNCTION]` | Runs a `FUNCTION_PROG` of its scripts. |

It starts in phase 1; each `AT=` that fires starts the next, and runs its
scripts' `PHASE_PROG [N]` (the `PHASE($i)` function reads the phase). `RESET`
says when the phases rearm: `FULLHEAL` (default, when it is whole again out of a
fight), `LEAVECOMBAT`, `LEASH` (when [EncounterReset](#encounterreset) resets
it) or `NEVER`. It returns to phase 1 when it dies. The phase and fight
progress are saved. See [chapter 29](29-combat-and-encounters.md).

```text
BossPhases ONENGAGE: SAY "Fools!"; AT=60%: SUMMON skeleton warrior x2; AT=25%: AFFECT Prop_AbilityImmunity "WEAPONS" 3, MOVETO Crypt#9, HEAL 20%; ONDEATH: ROOM EMOTE "The phylactery cracks."
```

### BribeGateGuard

Hosts: mobs. Works like a [GateGuard](#gateguard), but you must have at least
the price on your tab for it to open the gate (or to let you open it from that
side). Overpayment is retained, as with a bank chain, and every guard in the
same Gate System shares the tab. Do not put one on each side of a door.
Parameters: `PRICE=[amount]` (default 5) and `GATES=[system name]` (default
`General`), for example `price=5 gates=Avalon`. To let players sneak by, make sure the guard cannot see
sneaking or invisible players; a hiding player must wait for someone else to
open the door. Tabs are saved.

### BrotherHelper

Hosts: mobs. Joins a fight on the side of an identical mob, or a mob from the
same start room. Parameters, all optional: a number (most fighters that may
assault one target), `NAMEONLY` (do not use the "same start room" rule),
`MSG="[ATTACK MESSAGE]"`. On official legal enforcers set by an
[Arrest](#arrest) or [Conquerable](#conquerable) behavior it only triggers when
aiding another legal official, or when sure it is not aiding an aggressive mob.

```text
BrotherHelper 5 nameonly msg="omg! Attack!"
```

### ClanEnemyAggressive

Hosts: mobs. CoffeeSUD (no shipped help entry). The mob attacks anyone in its
room who belongs to a clan its clan is in a given relation with. Flag
`FLAG_POTENTIALLYAGGRESSIVE`.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `CLAN=[name]` | The clan whose relations count. If the mob is not a member, the named clan is still used. Empty means any clan the mob is in. | any |
| `STATES=[a,b]` | Clan relation names that count as enemy (the engine's relation descriptions, such as `WAR`, `HOSTILE`). | `WAR,HOSTILE` |
| `CHANCE=n` | Percent chance per tick it looks. | 100 |
| `CHECKLEVEL` | Skip targets more than 5 levels above the mob. | off |
| `MESSAGE=[msg]` | Said when it attacks. | none |

```text
ClanEnemyAggressive CLAN="Iron Guard" STATES=WAR CHANCE=50 CHECKLEVEL
```

### ClanHelper

Hosts: mobs. Joins a fight on the side of a mob of the named clan.
Parameters: `[CLAN NAME] ([MAX COMBATANTS]) (MSG="[ATTACK MESSAGE]")`.

```text
ClanHelper McElvy 5 msg="OMG ATTACK!"
```

### Clericness

Hosts: mobs. A [CombatAbilities](#combatabilities) preset: the mob becomes a
Cleric by class and gets prayers suited to its alignment and level. A `RESET`
is often needed after alignment or level changes. Parameters as in
[Fighterness](#fighterness). Cleric-specific stat shaping applies.

```text
Clericness Doomsayer OFFENSIVE
```

### CombatAbilities

Hosts: mobs (the class itself allows any host). Gives the mob the urge to use
its skills, spells, prayers, songs and other abilities in combat. Do not combine
it with any of its presets on the same mob. Timing is fixed at 1/1/100 per
combat round.

| Parameter | Meaning |
| --- | --- |
| `+SKILLID` | Allow this ability in combat even if not normally allowed. |
| `-SKILLID` | Never use this ability in combat even if the mob has it. |
| `RANDOM`, `DEFENSIVE`, `OFFENSIVE`, `MIXEDOFFENSIVE`, `MIXEDDEFENSIVE`, `ONLYALWAYS` | Combat mode (default random). A word that begins a mode name selects it. `ONLYALWAYS` uses only the `+` abilities. |
| `PROFICIENT` | All skills at 100% instead of level-based. |
| `NOSTATS` | Do not reshape the mob's stats to fit the class. |
| `NOCOMBATSTAT` | Do not set the combat stats (attack, armor, damage, hit points, mana, move). |
| `BSIZE=[N]` | Limit the size of the skill rotation used. |
| `MINTICKS`, `MAXTICKS`, `CHANCE` | Accepted by the presets as written in their help. |
| `SUPPORT=LEADER` (default) / `SELF` / `GROUP` / `MASK:"[ZAPPER MASK]"` | CoffeeSUD: whom it helps. LEADER is itself and the mob it follows, GROUP its group in the room, MASK anyone there the mask fits (NPCs too). |
| `HEALAT=[N]` | CoffeeSUD: it heals only those under N% of their hit points, the most hurt first, and before anything else. |
| `CUREFIRST=Poison,Disease` | CoffeeSUD: it cures those first, with abilities whose names say so. |
| `BUFFOUTOFCOMBAT` | CoffeeSUD: out of a fight it buffs those it helps who lack the effect. `HEALAT`, `CUREFIRST` and `BUFFOUTOFCOMBAT` make it look every 3 ticks out of a fight too. |
| `OPENER=[SKILL]` | CoffeeSUD: its first round's skill. |
| `+SKILL(rules)` | CoffeeSUD rotation rules in brackets, below. |

Rotation rules for a `+SKILL(...)` entry:

| Rule | Meaning |
| --- | --- |
| `EVERY=[N]` | Every N rounds of the fight. |
| `FIRST=[N]` | Not before round N (with `EVERY`, its first round). |
| `ONCE` | Tried once a fight, whether it works or not. |
| `BELOW=[N]%` | Only while its own hit points are under N%. |
| `COOLDOWN=[N]` | N rounds between tries (a fizzle too). |
| `WEIGHT=[N]` | Picked N times as often as the others. |
| `SELF` | On itself. |
| `TARGET=CASTER` | On the foe here with the most spells, prayers, chants or songs. |

A skill with `EVERY`, `FIRST`, `ONCE` or `BELOW` is used only when its turn
comes, before anything else, the first due in list order; the rest are picked at
random by `WEIGHT`. The counts start again with each fight. Stock skills take
rules too. In turn-based combat CombatAbilities only works if the NPC has more
than one attack and can make its one normal weapon attack. The round and
rotation history are saved.

```text
CombatAbilities -Spell_Sleep
CombatAbilities OPENER=Skill_Trip +Poison_Sting(EVERY=2) +Fighter_Kick(WEIGHT=3) +Spell_Fly(ONCE BELOW=25%)
Clericness MIXEDDEFENSIVE SUPPORT=MASK:"-RACE +Goblin" HEALAT=60
```

### CombatAssister

Hosts: mobs. Comes to the defence of any other mob attacked in its presence
that fits the mask in the parameters. Allowed is assisted, disallowed is ignored.

```text
CombatAssister -RACE +Elf
CombatAssister -NAME "+My Buddies"
CombatAssister -CLANS "+My Clan"
```

### CommonSpeaker

Hosts: mobs. Keeps the mob speaking Common even if its race or a polymorph
would say otherwise. An optional parameter names another language ID.

```text
CommonSpeaker Orcish
```

### Concierge

Hosts: mobs, items, rooms, areas (and exits). Makes the host give directions to
rooms or areas, for a price. With no price (or last price 0) it is free. The
parameter is a list separated by `;` of entries:

- a bare number: the price for everything named after it (the base price);
- `[ROOM ID or AREA NAME]=[PRICE]`: a price for that place;
- `[SETTING]=[VALUE]` from the table.

| Setting | Meaning |
| --- | --- |
| `AREAONLY=true` | Only knows places in its own area. |
| `MAXRANGE=n` | Most rooms away it knows. |
| `GREETING=msg` | Greeting to everyone who enters the room. |
| `ENTERMSG=msg` | Told to a player who mounts it (rideables only). |
| `TALKERNAME=name` | Changes the apparent speaker name. |
| `PERROOM=n` | Adds a price per room of distance. |
| `NOWATER`, `NOAIR`, `NOLOCKS`, `NOHOMES`, `NOINDOOR`, `NOCLIMB`, `NOCRAWL` `=true` | Trails avoid those (water, air, locked doors, private homes, indoor rooms, climbable exits, crawlable exits). By default trails already skip empty grid rooms and private homes. |
| `PORTAL=true` | Creates a magical portal rather than giving directions. |
| `CLAN=[name]` | Destinations limited to that clan's conquered or property rooms. |
| `MOBILE=true` | The mob makes the player follow it to the destination. |
| `PARTY`, `PETS`, `CARGO` | Accepted and skipped here; the transport behaviors ([TransportLine](#transportline)) read them. |

Run state (what it has been asked) is saved. Default greeting: "Need directions?
Just name the place and I'll name the price!" See [chapter 30](30-travel-vehicles-instances.md).

```text
Concierge 1.5
Concierge Midgaard#3001=1.5; Smurfville=2.0; =70 ; AREAONLY=true
Concierge Midgaard#3032; =100 ; AREAONLY=false; MAXRANGE=100
```

### Conquerable

Hosts: areas. Extends [Arrest](#arrest). Makes the area conquerable by clans
using items made with ClanCraft (see CONQUEST help and
[chapter 9](09-society.md)).

| Parameter | Meaning |
| --- | --- |
| `JOURNAL="[NAME]"` | Log conquest events to that journal. |
| `LAW=TRUE/FALSE` | Whether conquering clans can set up a system of law here. |
| `OWNERSHIP=TRUE/FALSE` | True: all private property signs over to the conquerors. |
| `LOYALTY=[PCT]` | 0 to 100: how much loyalty the populace gains toward its rulers (default 0). |
| `LEVELDIFF=[NUM]` | How far below the killer a target's level may be and still give points; default is the coffeemud.ini `EXPRATE` times 2. |

```text
Conquerable JOURNAL="The Conquest Times" LAW=FALSE OWNERSHIP=FALSE
```
