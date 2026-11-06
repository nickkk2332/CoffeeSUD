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

### CorpseEater

Hosts: mobs. Timing default 5/20/75. The mob appears to eat any corpse it comes
across. It does not eat the items in the corpse unless `EATITEMS` is given, and
ignores player corpses unless `+PLAYER` is given. `-MOB` or `-NPC` stops it
eating mob corpses. Any other text is a mask (allowed is eaten, disallowed is
not) to give it a specific diet.

```text
CorpseEater min=10 max=20 chance=75 -LEVEL +<17
CorpseEater min=10 max=20 chance=75 -RACE +Elf
```

### CorpseLooter

Hosts: mobs. No parameters. Turns on the mob's `AUTOLOOT` flag so it loots the
items (not the gold) from corpses it kills.

### Decay

Hosts: items, mobs. Timing default 50/50/100 (the countdown is the timing).
The host vanishes when the countdown ends and the chance roll succeeds. The
countdown starts on a trigger: for a mob when it is attacked, for an item when
it is picked up, for a weapon when first wielded, for armor when first worn, for
a rideable when mounted. The remaining time is kept in a `remain=N` parameter
(written back into the saved parameters).

| Parameter | Meaning |
| --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing. |
| `USE2=true` | Use the second trigger set: a scroll or book read, a drink drunk, food eaten, a pipe smoked, a rideable mob mounted. |
| `ANSWER="..."` | Text appended to the item's name when it decays (default ` vanishes!`). |
| `NOTRIGGER` | Start decaying at once. |
| `REMAIN=n` | Ticks left (maintained by the behavior; you can set it). |
| mask | The countdown only starts for a triggerer the mask allows; disallowed is ignored. |

```text
Decay min=10 max=20 chance=75 answer=" goes poof!"
Decay notrigger answer=" " use2=true
Decay min=10 max=20 chance=75 -CLASS +Paladin
```

### DelayedTransporter

Hosts: items, mobs, rooms. Timing default 5/5/100. Defines destination rooms
separated by `;`; the first section may hold the timing and options.
When the countdown ends and the chance roll succeeds, every mob in the same room
as the host is teleported to one of the listed rooms, chosen at random.

```text
DelayedTransporter min=10 max=20 chance=25;Midgaard#3001;Sewers#6020;Midgaard#3020
```

CoffeeSUD options go in the first section, before the first `;`:

| Option | Meaning |
| --- | --- |
| `PARTY=true/LEADER/EACH` | `true` shares a group countdown and keeps a follower from leaving alone; `LEADER` charges the leader; `EACH` charges individual shares. |
| `FARE=[price]` | Fare. |
| `PERHEAD=true` | Fare per eligible character. |
| `MASK="[passenger mask]"` | Who may ride (a zapper mask). |
| `DEST=ORDER/RANDOM` | Destinations in listed order, or random. |
| `LOOK=true` | Passengers are shown the destination. |
| `DEPARTMSG="[text]"`, `ARRIVEMSG="[text]"` | Messages. |
| `ENTRY=CHECK` | Check every passenger's leaving and entering first; move none if any is refused. |

Without `PARTY`, a character goes with their followers. The move is a forced
teleport whatever the rooms would allow. All payment (and with `ENTRY=CHECK`,
entry) checks complete before anyone moves or pays. The countdown and ordered
destination progress are saved.

### DoorwayGuardian

Hosts: mobs. The mob does not allow any other mob or player to open or move
through a doored exit; it must be killed to get by. Parameter text:

```text
[NOSNEAK] [ALWAYS] [direction ...] [mask] ; [rejection message]
```

| Word | Meaning |
| --- | --- |
| a direction (`north`, `east`...) | Guards only that exit (several allowed). With none it guards all exits. |
| `NOSNEAK` | The guard cannot be sneaked past. |
| `ALWAYS` | The guard still blocks when in combat or badly hurt. |
| mask words | Who is blocked; with no mask it blocks everyone. Allowed passes. |
| `;[message]` | Overrides the rejection message (default `<S-NAME> won't let <T-NAME> through there.`). Use `\;` for a literal semicolon. |

```text
DoorwayGuardian always north
DoorwayGuardian nosneak east -class +mage -race +elf -evil
DoorwayGuardian east north;<S-NAME> smack(s) <T-NAME> and push(es) <T-HIM-HER> back!
```

### Drowness

Hosts: mobs. No parameters. A complete kit (weapons, armor, spells, combat
behavior) whose details differ between male and female drow. Do not use it with
any CombatAbilities behavior, and do not add equipment to the mob.

### Druidness

Hosts: mobs. A [CombatAbilities](#combatabilities) preset: the mob becomes a
Druid by class with druid skills suited to its level. Parameters as in
[Fighterness](#fighterness).

```text
Druidness SkyWatcher DEFENSIVE
```

### Emoter

Hosts: mobs, items, rooms, areas, exits. Timing default 10/30/50. A series of random emotes separated by `;`
(or by `/` if there is no `;`). The first field, which may be empty, holds the
settings:

| First-field setting | Meaning |
| --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing (chance is per countdown). |
| `EXPIRES=n` | Number of emotes before the behavior removes itself (0 = never; the count left is saved). |
| `INROOM="ID,ID"` | The host must be in one of these rooms. |
| `MASK="zapper"` | CoffeeSUD: only for a viewer the mask fits. |
| `SOUND` (or `NOISE`), `SMELL` (or `AROMA`), `SIGHT` (or `VISUAL`), `SOCIAL`, `PUFF` | The type of emote; default sight. |
| `BROADCAST` / `NOBROADCAST` | Also emote to adjacent rooms. |
| `PRIVATE` / `NOPRIVATE` | Keep emotes private. |
| `EMOTEONLY` | Do not respect visibility, smellability and hearing. |
| `NOREPEAT=n` | CoffeeSUD: do not repeat the last n lines. |
| `RETRY` | CoffeeSUD: if the line chosen cannot be used, pick another. |
| `QUIETAFTER=n` | CoffeeSUD: quiet during combat or reading for n ticks. |

Each following field is one emote. It may start with a type word (`SIGHT`,
`SMELL`, `NOISE`, `SOCIAL`, `PUFF`) or end with one, which gives that one emote a
different type than the default. CoffeeSUD lets each line begin with
conditions before its type: `DAY`, `NIGHT`, `DAWN`, `DUSK`, a season, `RAIN`,
`SNOW`, `FOG`, `INDOORS`, `OUTDOORS`, `IFVAR name=value`, `MASK="zapper"`,
`ONCE` (saved per character), `WEIGHT=n`. Area emotes test each room's own
conditions.

```text
Emoter min=10 max=20 chance=25;wiggles his bottom.;smiles evilly.;burps!
Emoter smell chance=25 expires=10;smells horrible!;is really stinky!
Emoter chance=100 inroom="Here#1,Here#2";looks horrible!;is bad!
Emoter broadcast sound chance=25;grumbles!;eeeks!
Emoter min=1 max=2 chance=25;sound grumbles!;visual leers broadly.;social smile
Emoter min=8 max=20 chance=40 RETRY NOREPEAT=2;NIGHT SOUND an owl hoots.;IFVAR WOLVES=1 WEIGHT=3 ONCE SOUND wolves howl.
```

### EncounterReset

Hosts: mobs, rooms. CoffeeSUD. A boss the party walks away from resets. After
a fight, when none of those it fought has been within `RADIUS` rooms for `IDLE`
ticks, the mob goes back to its start room, heals, sheds what was cast on it to
harm it, loses the adds it summoned, forgets its threat, and its scripts'
`RESET_PROG` runs.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `RADIUS=n` | Rooms from the boss within which a former foe still counts (0 is its own room). | 1 |
| `IDLE=n` | Ticks with no foe within the radius. | 5 |
| `HEAL=n` | Percent of hit points restored (all mana and moves too). | 100 |
| `HOME=true/false` | Return to its start room. | true |
| `DESPAWNADDS=true/false` | Remove NPCs following it that have no home, or are tagged `SUMMONED`. | true |
| `ROOM=true/false` | Reset every NPC of its start room together. On a room, every NPC there is reset together. | false |

```text
EncounterReset RADIUS=1 IDLE=5 HEAL=100
EncounterReset IDLE=10 ROOM=true
```

### Escortee

Hosts: mobs. CoffeeSUD. An NPC escorted to a destination. Its leader is the one
it follows when the escort begins (typically a quest's `MPFORCE $i follow $n`);
until then it waits. Arrived, it stops following. The leader, arrival and
remarks are saved.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `DEST=[ROOM ID]` | Destination. | required |
| `MODE=LEAD/FOLLOW` | `FOLLOW`: follows its leader. `LEAD`: stops following and walks the way itself, a step a tick, and the party follows. | FOLLOW |
| `WAITFOR=LEADER` | While its leader is not with it, it does not go on. | off |
| `WAITCALL=n` | Ticks before it calls for the leader. | 3 |
| `WAITMSG="..."` | What it calls. | `Wait for me!` |
| `PANIC=FLEE/HIDE/COWER` | When attacked: flee, dive for cover or cower (the last two stop the fight). | none |
| `AVOID=[ROOM ID],...` | Rooms (by ID, or the end of one) it will not enter. | none |
| `TALK="[ROOM ID]:[TEXT]"` (also `TALK1=` to `TALK9=`) | A remark the first time it comes to that room. | none |
| `ONARRIVE="[MOBPROG]"`, `ONDEATH="[MOBPROG]"` | Script lines, `;` between them, run with the leader as `$n`: a word, `MPSETVAR`, `MPCALLFUNC`, `MPQUESTWIN`. | none |
| `TIMEOUT=n` with `ONTIMEOUT="[MOBPROG]"` | After n ticks run the line. | 0 (off) |

```text
Escortee DEST=Riverford#1 MODE=LEAD WAITFOR=LEADER PANIC=COWER TALK="Bridge#3:They say trolls live under here." ONARRIVE="say Thank you, friends!;mpsetvar $n ESCORTED 1"
```

### EvilExecutioner

Hosts: mobs. The mob attacks any good or paladin non-player mob it detects. With
the parameter `PLAYERS` it attacks players too.

### FaithHelper

Hosts: mobs, rooms, areas. On a mob, gives the mob a deity and makes it join a
fight on the side of a mob of the same faith. On a room or area, gives mobs of
the area a particular faith at spawn time only. Parameters:
`[DEITY NAME] ([MAX COMBATANTS]) (MSG="[ATTACK MESSAGE]")`; a maximum of 1
effectively turns off the combat part.

```text
FaithHelper Odin 5 msg="OMG ATTACK!"
```

### FasterRecovery

Hosts: rooms, areas, items (per the code; the help also lists mobs, and the
effect reaches a mob that carries the item or rides the host). Gives all
affected mobs extra recovery ticks every normal tick, as opposed to the usual
regeneration roughly once per 5 ticks. All parameters are optional.

| Parameter | Meaning |
| --- | --- |
| `HEALTH=n` | The number of regeneration ticks the mob gets each normal tick. 2 gives 1 extra regen per tick, 3 gives 2 extra, and so on; values of 1 or less give none. |
| `BURST=n` | Bursts of extra speed equal to the number given. |
| `HITS=n`, `MANA=n`, `MOVE=n` | The same, for one pool. |

Value forms, for any of the above: a plain number (a count of full recovery
ticks); `+n` or `-n` (an adjustment of exactly that amount rather than another
full recovery tick); `n%` (a percentage of the current recovery amount). Any of
them may be followed by `/ticks` to temper it: `MANA=2/5` is a single extra
burst of mana every 5 ticks, and a percentage as the first number is the chance
that the regeneration happens.

```text
FasterRecovery burst=2
FasterRecovery health=50%
FasterRecovery hits=2/5 mana=50% move=50%/1
FasterRecovery mana=+5 hits=+10
FasterRecovery burst=2 health=5
```

### FieryRoom

Hosts: rooms. Timing default 5/10/100. Makes a room burn: damage to
inhabitants, flammable equipment burns and non-flammable heats up (so is
dropped), burning emotes, and finally the room becomes a burnt-out room.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `DAMAGE=n` | Damage to inhabitants each behavior tick. | 10 |
| `TITLE="..."` | Room title when burnt out. | `A Charred Ruin` |
| `DESCRIPTION="..."` | Room description when burnt out. | `Whatever was once here is now nothing more than ash.` |
| `EQCHANCE=n` | Chance that equipment bursts into flames or heats up. | 0 (no equipment destruction) |
| `BURNTICKS=n` | Behavior ticks to keep burning. | 12 |
| `NOSTOP` | Never burn out (title, description, burnticks are then moot). | off |
| `NONPC` | No damage or equipment burning to NPCs. | off |
| `NOFIRETEXT` | No burning emotes. | off |

```text
FieryRoom min=5 max=10 chance=100;damage=77 Title="A Horribly Blackened Spot" Description="This place was once just like any part of the road.  Now it is nothing more than a sooty pit." NOSTOP eqchance=50
```

### FightFlee

Hosts: mobs. Timing default 1/1/33. In combat the mob flees according to the
timing and chance.

```text
FightFlee min=10 max=20 chance=75
```

### Fighterness

Hosts: mobs. A [CombatAbilities](#combatabilities) preset: the mob becomes a
Fighter by class with fighter skills suited to its level. After the behavior
first runs a `RESET` is often needed for level changes to take effect. It also
modifies the mob's stats (see `NOSTATS`, `NOCOMBATSTAT`), so after adding or
removing a preset reset the room, save, and check the stats. The presets
(Fighterness, Mageness, Clericness, Druidness, Thiefness, Bardness) take:

| Parameter | Meaning |
| --- | --- |
| a class name | A subclass of the preset's class to become (`Paladin`, `Templar`, `Assassin`, `Illusionist`...). |
| `PROFICIENT` | All skills at 100%. |
| `NOSTATS` | Leave the mob's stats alone. |
| `NOCOMBATSTAT` | Leave the combat stats alone. |
| `OFFENSIVE`, `DEFENSIVE`, `MIXEDOFFENSIVE`, `MIXEDDEFENSIVE` | Influences skill choice. |
| `MINTICKS=n MAXTICKS=n CHANCE=n` | As listed in the help; the presets run on the 1/1/100 per-round timing (in the code the `MIN`/`MAX`/`CHANCE` words are skipped when reading skill names). |
| `BSIZE=n` | Rotation size. |
| `+SKILLID`, `-SKILLID`, `OPENER=`, rotation rules, `SUPPORT=`, `HEALAT=`... | All the [CombatAbilities](#combatabilities) options. |

Thiefness also gives non-combat abilities: sneaking, hiding, backstabbing,
picking pockets and, at high levels, stealing.

```text
Fighterness Paladin
Mageness Illusionist MIXEDDEFENSIVE
Thiefness Assassin MIXEDOFFENSIVE
```

### Floodable

Hosts: rooms (and areas). CoffeeSUD, built on [RoomStates](#roomstates). A room
that floods in levels: 0 dry, 1 ankle-deep, 2 waist-deep (one swims; loose
things weighing up to `FLOAT` float away through `FLOWTO`), 3 full (the air is
water: one drowns once one's breath runs out). While any of `RISE`, `ONVAR` or
`TIDE` holds the water rises a level each `RATE` ticks; otherwise it drains one
level each `DRAIN` ticks. Rising water puts out lights. Each level may have its
own title, description and message. `FLOODLEVEL([ROOM ID/HERE/CODE])` reads the
level in scripts. The state is saved.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `RISE="[MASK]"` | A mask on someone standing there. | none |
| `ONVAR="[HOST] [VAR]==[VALUE]"` | A script variable. | none |
| `TIDE=HIGH` | At high tide. | none |
| `RATE=n` | Ticks per rising level. | 10 |
| `DRAIN=n` | Ticks per draining level. | the value of `RATE` |
| `FLOWTO=[DIR]` | Direction loose things float off through (otherwise they float away). | none |
| `FLOAT=n` | Weight that floats. | 2 |
| `TITLE1=` to `TITLE3=`, `DESC1=` to `DESC3=`, `MSG1=` to `MSG3=` | Per-level texts. | none |

```text
Floodable RISE="-WEATHER +RAIN +THUNDERSTORM" RATE=15 DRAIN=30 FLOWTO=east
Floodable ONVAR="pump sluice==open" RATE=5
```

### Follower

Hosts: mobs, items. Timing is 0/0/100: following is instant by default (min
and max -1 and no `GROUP` also mean instant). The mob follows any other mob
that leaves the room.

| Parameter | Meaning |
| --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing of non-grouping following; `CHANCE` 1 to 100 randomises the opportunity. |
| `GROUP` | Join the target's group instead of just following. |
| `NOFOLLOWERS` | Do not trigger when the mob has someone following it. |
| `WANDER` | May leave its home area. |
| `INVENTORY` (or `INV`) | For an item: reappear in the inventory of the first person to pick it up; otherwise it reappears in the room of the first it meets. |
| `NAME=[name]` | Only follow that name. |
| mask | Allowed is followed, disallowed ignored. |

An item with Follower follows mobs around; items with it cannot be sold to
shopkeepers.

```text
Follower min=1 max=1 chance=50 -race +elf
Follower group nofollowers -race +elf +gnome
Follower min=4 max=20 chance=100 inv -race +elf +gnome
```

### GateGuard

Hosts: mobs. When standing next to a doorway and a player enters, the mob
opens the door for the player and closes it after them. It also locks the door
at night if there is a lock. The guard acts according to whether any player in
the room meets the mask. With no parameters it always acts.

| Flag | Meaning |
| --- | --- |
| `KEEPLOCKED` | If the door has a lock, keep it locked. |
| `ALLNIGHT` | Does not behave differently at night than by day. |

Only one of the two flags is read: the parser stops at the first it finds, so
give just one. The rest of the text is the mask.

```text
GateGuard -class +mage -race +elf -evil
```

### GenBehavior

Hosts: set by its `CANIMPROVE` (MOBS, ITEMS, ROOMS, AREAS, EXITS). CoffeeSUD:
a behavior defined as data in builder mode. `CREATE BEHAVIOR TollKeeper` makes
one and opens its editor with these fields:

| Field | Meaning |
| --- | --- |
| `NAME` | The behavior ID. |
| `HELP` | What `AHELP TOLLKEEPER` shows. |
| `CANIMPROVE` | Valid hosts, comma separated: MOBS, ITEMS, ROOMS, AREAS, EXITS. |
| `PARMS` | Parameters and their defaults, such as `price=20 exit=north`. |
| `SCRIPT` | MOBPROG text, optionally with `<SCRIPT>` JavaScript, or `LOAD=[FILE]`. |
| `ACCOUNT` | What it says it is doing. |
| `VARSCOPE` | Empty: its variables are its host's, as a Scriptable's. `*`: its own. |

The script reads parameters with `PARM(price)`, `$%PARM(price)%` in text,
`IF PARM(free)` (set, and not 0 or false) and `parm('price')` in JavaScript.
Then `TollKeeper price=5 exit=west` goes wherever a behavior ID goes: on a mob
or item, `MPBEHAVE`, a quest's `GIVE BEHAVIOR`, a faction or a plane. Each copy
has its own engine and values; all share the definition, so `MODIFY BEHAVIOR
TollKeeper` changes every one at once. `MODIFY BEHAVIOR [ID] [FIELD] [VALUE]`
sets one field without the editor (`\\n` in a `SCRIPT` is a new line).
`WHEN=`, `TOD=` and `HOURS=` work in its parameters. `DESTROY BEHAVIOR [ID]`
removes it (what has it keeps it until the world is loaded again). `SAVE`
writes it to `world/custom/behaviors/`, its script in a file beside it. See
[chapter 4](04-mobprog.md).

```text
CREATE BEHAVIOR TollKeeper
TollKeeper price=5 exit=west
```

### GetsAllEquipped

Hosts: mobs. Deprecated. On reset the mob grabs everything in its default room
and tries to wear it. It does the same whenever someone in the room dies, so it
doubles as autoloot.

### GoldLooter

Hosts: mobs. No parameters. Turns on the mob's `AUTOGOLD` flag so it loots the
gold from corpses it kills.

### GoodExecutioner

Hosts: mobs. The mob attacks any evil or thief non-player mob it detects. With
`PLAYERS` it attacks players too.

### GoodGuardian

Hosts: mobs. No parameters. If the mob detects an evil mob attacking a good one
in the same room or an adjacent room, it attacks the evil one. If evil mobs are
fighting each other it does nothing; if two good or neutral mobs are fighting
it stops the fight.

### Guard

Hosts: mobs. If any mob or player in the room is attacked, this mob comes to
the victim's defence. A parameter limits it to a victim with that name.

```text
Guard Bob
```

### GuildMaster

Hosts: mobs. CoffeeSUD (no shipped help entry). Makes the mob the master of a
clan-style guild: players speak to it in the room, and it answers. Spoken
commands: `join`, `rank`, `advance`, `leave`, `dues`, `kit`, `kit replace`. The
guild is a clan (government) of the world; ranks and dues come from the
organisations system ([chapter 9](09-society.md)).

| Parameter | Meaning | Default |
| --- | --- | --- |
| `CLAN=[name]` | The guild's clan (required; without it the mob says "This guild is not configured."). | none |
| `JOINMASK="[mask]"` | A mask a joiner must also meet (single quotes may be used for names with spaces). | the clan's basic requirement |
| `JOINQUEST=[quest]` | A quest the joiner must have won. | none |
| `JOINFEE=n` | Gold to join. | 0 |
| `REFUSEMSG="..."` | Said when a joiner is refused. | `You have not met our requirements.` |
| `RANKS=MERIT` | Ranks are by merit (`rank` or `advance` recalculates). | MERIT |
| `DUES=TRUE/FALSE` | Whether `dues` pays dues here. | TRUE |
| `LEAVEMSG="..."` | Said on leaving. | `You have left [clan].` |
| `KIT.[ROLE]="[catalog item],[catalog item]"` | Catalog items issued to a member of that role at `kit`. | none |
| `KITFEE=n` | Fee for `kit replace` (a replacement kit). | 0 |

```text
GuildMaster CLAN="Iron Guard" JOINFEE=50 KIT.MEMBER="iron badge" KITFEE=10
```
