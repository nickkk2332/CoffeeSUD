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
behaviors they are written in the settings part before the first `;`
(RandomMonsters and RandomItems also read them from after a second `;`).

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

### Healer

Hosts: mobs. Timing default 10/20/100. The mob gains Cure Disease, Cure Light
Wounds, Cure Serious Wounds, Bless and Sanctuary, and at random casts them on
player mobs in the room. CoffeeSUD adds choice of whom and what.

| Parameter | Meaning |
| --- | --- |
| `SPELLS=[ID],[ID]` | Its own spells instead of the stock set (the help says "those six"). |
| `HEALAT=[N]` | Heal only those under N% of their hit points, the most hurt first (its cures, for poison and disease, first of all). Default 0 (off). |
| `TARGET=GROUP` / `TARGET=MASK:"[ZAPPER MASK]"` | Whom it heals; NPCs too. |

With any of `SPELLS`, `HEALAT` or `TARGET` it chooses whom it heals rather than
at random.

```text
Healer min=10 max=20 chance=75
Healer min=1 max=2 chance=100 HEALAT=70 SPELLS=Prayer_CureSerious,Prayer_CureDisease TARGET=MASK:"-NPC"
```

### Hireling

Hosts: mobs. Lets a mob hire itself out for money for a time. When the time
expires it returns to its start room. The parameter is

```text
price;minutes(;mask) KEY=value ...
```

The first number is the price to hire (in the mob's base currency), the second
is the minutes of service it buys (default 30 if omitted), and an optional third
section is a zapper mask of who it will work for. Additional money extends the
time. The CoffeeSUD terms are `KEY=value` words after those, extracted first
(quote a value with spaces):

| Key | Meaning |
| --- | --- |
| `KEYWORDS=[WORD],...` | Words that ask its terms (default `HIRE`, `HIRELING`, `WORK`, `WORKS`, `TIME`, `AVAILABLE`). |
| `MSG_OFFER` | Its offer; `@x1` is the price, `@x2` the term. |
| `MSG_HIRED` | When hired; `@x1` is the term, `@x2` its skills. |
| `MSG_REFUSE`, `MSG_EXPIRE`, `MSG_NOORDER` | Other lines. (`Prop_NPCLines` rewords the stock ones too.) |
| `CONTRACT=MINUTES` (the second number), `DAYS` (that many game days), `"QUEST:[NAME]"` (until the employer's quest is won, failed or dropped), `UNTILFIRED` (until the employer says `FIRED`) | The kind of contract. |
| `UPKEEP=[N]` | Wages per game day, taken from the employer; after two unpaid days it leaves. |
| `FLEEHP=[N]` | Below N% of its hit points in a fight it quits, unless its opinion of the employer (`Prop_Affinity`) is at least `LOYAL=[N]` (25). |
| `REFUSE=[COMMAND],...` | Orders it will not take. |

`HIRE [MOB]` shows its terms as a menu; `HIRE [MOB] PAY` pays its price. The
contract and wages are saved.

```text
Hireling 10000;30
Hireling 500;0;CONTRACT="QUEST:goblinwar" UPKEEP=5 MSG_HIRED="My blade is yours till the goblin king falls."
```

### InstantDeath

Hosts: mobs, items, rooms, areas (and exits). Timing default 1/1/100. The host's
owner or inhabitants are killed. MOBs do not kill until attacked; food kills
when eaten, drink when sipped; items when picked up; weapons when first
wielded; armor when first worn. Rooms merely need to be entered, and kill all
their inhabitants. `MASK="[zapper mask]"`: allowed is killed, disallowed spared.

```text
InstantDeath min=10 max=20 chance=75 mask="-RACE +ORC"
```

### ItemGenerator

Hosts: areas, rooms, mobs, items (containers). Generates items from the standard
common (crafting) skills' item lists, adding random enchantments and curses a
percentage of the time. Items are chosen pseudo-randomly, weighted toward the
least valuable ones (to avoid a plethora of the most valuable). Items taken
away are replaced, even if looted. Parameters before the first `;` describe
rate and amount; after it, a mask selects which items may be generated
(allowed is generated).

| Parameter | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing: it tries every min to max ticks, if the chance roll succeeds. | 10/30/100 |
| `MINITEMS`, `MAXITEMS` | At least and at most this many items. | 1 and 1 |
| `MAXDUPS` | Most copies of one item. | unlimited |
| `ENCHANTED=n` | Percent of items enchanted or cursed. | 10 |
| `MOBS` | Put the items on a mob if possible (items on mobs are worn if possible). Automatic when the host is a mob. On a shopkeeper the shop stocks them. | off |
| locale words | `-ALL +CITY ...` on an area: which room types may get items (see [Locale restriction words](#locale-restriction-words)). With none, every room qualifies. | all |

On an area a random room is picked for each item.

```text
ItemGenerator min=10 max=20 chance=75 minitems=1 maxitems=10 maxdups=0;-LEVELS +>10
ItemGenerator minitems=10 maxitems=20 MOBS -ALL +CITY;-MATERIAL +IRON +BRONZE
ItemGenerator minitems=10 maxitems=20 enchanted=20;-NAME "+Short Sword"
```

### ItemIdentifier

Hosts: mobs. For a price the mob identifies items. It does not work for archons.
`COST="[FORMULA]"` changes the price formula; variables: `@x1` level, `@x2`
value, `@x3` condition (0-100), `@x4` magical (1.0) or non-magical (0.0).

```text
ItemIdentifier COST="100+@x1"
```

### ItemMender

Hosts: mobs. For a price the mob repairs damaged weapons and armor. It does not
work for archons. `COST="[FORMULA]"` as in [ItemIdentifier](#itemidentifier).

```text
ItemMender COST="100+@x1"
```

### ItemRefitter

Hosts: mobs. For a price the mob resizes armor to fit the customer. Not for
archons. `COST="[FORMULA]"` as in [ItemIdentifier](#itemidentifier).

### ItemUpgrader

Hosts: mobs, items. CoffeeSUD. A smith, or a workbench, that upgrades items
along set paths. Each row is `UPGRADE1` to `UPGRADE9`, or one per line in the
file named by `UPGRADES=` (for example `world/upgrades.txt`), using the same
keys inside the row:

| Key in a row | Meaning |
| --- | --- |
| `FROM=` | The items it takes: a name, `tag:X` or `catalog:X`, `\|` between alternatives. |
| `TO=catalog:[ITEM]` | A catalog item it becomes in its place (held or worn as it was; `KEEP` as `MPTRANSFORM`'s). |
| `ADJ="dam+N att+N arm+N lvl+N"` | Instead of `TO`: the same item better. |
| `COST=[GOLD]` | Gold cost. |
| `MATERIALS="[NAME] x[N], [NAME]"` | What is used up (`x3` for three). |
| `LEVEL=[N]` | The least item level. |
| `KEEP=[KEEPS]` | What survives the change (as `MPTRANSFORM`'s KEEP, such as `TAGS,STATE`). |
| `MSG="[TEXT]"` | What is seen. |

An item with the `Prop_UpgradePath` property carries its own row.
`UPGRADE [ITEM]` tells what it would take; `UPGRADE [ITEM] CONFIRM` does it.

```text
ItemUpgrader UPGRADE1="FROM=notched TO=\"catalog:Starfall, the reforged blade\" COST=2000 MATERIALS=\"starmetal ingot x3\" LEVEL=20 KEEP=TAGS,STATE"
```

### JSBehavior

Hosts: mobs, items, exits, rooms, areas. CoffeeSUD. Runs a JavaScript file as
`Prop_JScript` does. Parameter: `SCRIPT=[FILE] ([NAME]=[VALUE])...`. The script
may define `okMessage(host, msg)` (false refuses the message), `executeMsg(host,
msg)`, `tick(host, tickID)`, `onAdded(host)`, `onRemoved(host)` and
`catchUp(host, ticks)`. A behavior is not asked for stat changes (`Prop_JScript`
has those hooks). Whatever a hook changes in `parms` is written back to the
behavior's parameters. See [chapter 5](05-javascript.md).

```text
JSBehavior SCRIPT=world:js/props/bellringer.js EVERY=12
```

### JSWorldBehavior

Hosts: rooms, areas. CoffeeSUD (no shipped help entry). The base class for a
JavaScript class in `world/js/behaviors/` that extends
`com.planet_ink.coffee_mud.Behaviors.JSWorldBehavior` (a `//extends` line),
giving a room or area event hooks. The hooks, all optional: `onEnter(mob,
room)`, `onLeave(mob, room)` (someone enters or leaves a room it is on, or one of
its area's), `onWeather(area, was, now)` and `onDaypart(area, was, now)` (time of
day `DAWN`, `DAY`, `DUSK`, `NIGHT`). Helpers: `echo(text)` to the host (a room,
or every occupied room of an area), `roll("3d6")`, `spawn(catalogName)` (a
catalog mob copy in its room, the catalog's cap holds), `room()`, `area()` and
`parm(name)` (a `KEY=value` of its own parameters). See
[chapter 5](05-javascript.md) and [chapter 32](32-engine-programming.md).

### JScriptable

Hosts: mobs, items, exits, rooms, areas. CoffeeSUD. Scripts its host wholly in
JavaScript. Parameter: `LOAD=[FILE] (SCOPE=[INDIVIDUAL/GLOBAL/NAME])`, or the
script text itself. A `SCOPE` other than `INDIVIDUAL` shares one script among
all hosts that name it. The script defines the handlers it wants:
`onLoad`, `onGreet(who)`, `onArrive(room)`, `onLeave(who)`, `onSpeech(who,
text)`, `onGive(who, item)`, `onGet`, `onDrop`, `onWear`, `onRemove` (who, item),
`onOpen`, `onClose`, `onLock`, `onUnlock` (who, thing), `onDeath(killer)`,
`onKill(victim)`, `onDamage(who, amount)`, `onCast(who, ability, target)`,
`onLook(who)`, `onTick`, `onHour(hour)`, `onDay(day)`, `onCommand(who, verb,
rest)` (true handles a command typed near the host), `canMessage(msg)` (false
refuses it), `afterMessage(msg)` and `onReload(old)`. Its other functions answer
`MPCALLFUNC` and `CALLFUNC`. The script has `me`, `cm`, `CMMsg` and `state`, the
host's `cm.store`, saved with the game. See
[chapter 5](05-javascript.md).

```text
JScriptable LOAD=world:js/npc/ferryman.js
```

### LinkedWeather

Hosts: areas. Forces the area to mimic the weather of another area. The
parameter is the area name; followed by `;ROLLING` the area instead takes on the
linked area's weather at its next weather change, so weather appears to roll in.

```text
LinkedWeather My Area
LinkedWeather My Area;ROLLING
```

### MOBEater

Hosts: mobs. Timing default 4/8/50. The mob eats other mobs or players and
digests them. Another player must kill the mob to free those inside. It does not
work unless the mob weighs at least twice as much as the target. `ACIDPCT=n` is
the percent of a victim's hit points the stomach acid takes (default 50). Run state is saved.

```text
MOBEater min=4 max=8 chance=5 acidpct=50
```

### MOBHelper

Hosts: mobs. The mob comes to the defence of any non-player mob attacked in its
presence. Optional: a number (most combatants that may join) and
`MSG="[ATTACK MESSAGE]"`.

```text
MOBHelper 5 MSG="OMG ATTACK!"
```

### MOBHunter

Hosts: mobs. Timing default 600/1200/100. The mob seeks a victim within
`RADIUS` rooms (default 15) and hunts and assassinates it. `MIN`/`MAX` is the
wait between hunts and `CHANCE` the chance a hunt begins then. The remaining
text is a mask of who makes a good victim (allowed is hunted, disallowed
ignored).

```text
MOBHunter min=600 max=1200 chance=99 radius=50 -RACE +Human
MOBHunter min=50 max=1200 chance=75 -NAME "+Evil Wizards"
```

### MOBReSave

Hosts: mobs. Timing default 140/140/100. Updates the mob in the engine's mob
database at the given interval. In CoffeeSUD, where worlds are saved as world
files and campaign saves, its use is doubtful; see the audit notes. It writes
the mob's base character stats (`STR=`, `INT=`... abbreviations) into its own
parameters and restores them.

### MOBTeacher

Hosts: mobs. Makes the mob able to teach skills, spells, prayers, songs or
expertises. The parameter is a list of tokens:

| Token | Meaning |
| --- | --- |
| a character class name | Teach abilities of that class (not base classes: list every subclass for a whole base class). Any class may always practice with a MOBTeacher; the mob teaches only those abilities of the classes named. |
| an ability ID | Teach that ability. |
| an expertise name | Teach that expertise. |
| `[N]%` | Chance that following classes or spells are known by the teacher; a later percentage overrides it. |
| `NOTEACH` | The mob neither teaches nor uses the skills. |
| `NOCOMMON` | Does not teach common skills. |
| `NOEXPS` | Does not teach expertises. |
| empty | Teaches all basic skills; expertises only if it otherwise qualifies; class changes only if it is that class. |

CoffeeSUD teacher rules, in any order among the rest:

| Key | Meaning |
| --- | --- |
| `STUDENTMASK="[ZAPPER MASK]"` | Whom it teaches at all; `REFUSEMSG="[TEXT]"` is what it says to others. |
| `SKILLMASK.[ABILITY ID]="[ZAPPER MASK]"` | Whom it teaches that skill. |
| `PRICE="[N] per level"` or `PRICE=[N]` | Money taken for each lesson, on top of the usual cost. |
| `HIDDEN=[ID],[ID]` | Skills it teaches but leaves off TEACHLIST. |

These gate `TEACH ME`, `GAIN`, `PRACTICE` and `TRAIN` with it; `TRAIN` with no
trainer named passes over one who refuses. `TEACHLIST` shows a student what this
teacher offers them. Single quotes may be used inside the masks
(`'+Temple of Arden>=ACOLYTE'`).

```text
MOBTeacher mage evoker illusionist transmuter
MOBTeacher 100% mage 10% Thief_Steal NOCOMMON
MOBTeacher Spell_Sleep 5% Spell_Wish
MOBTeacher Prayer_Bless Prayer_Sanctuary STUDENTMASK="-CLANRANK '+Temple of Arden>=ACOLYTE'" SKILLMASK.Prayer_Sanctuary="-CLANRANK '+Temple of Arden>=PRIEST'" PRICE="5 per level"
```

### Mageness

Hosts: mobs. A [CombatAbilities](#combatabilities) preset: the mob becomes a
Mage by class with spells suited to its level. Parameters as in
[Fighterness](#fighterness).

```text
Mageness Illusionist MIXEDDEFENSIVE
```

### Memory

Hosts: mobs. CoffeeSUD. The NPC remembers what each character did, as script
variables of the NPC named `MEM_[FACT]_[NAME]` (so a script reads `VAR($i
MEM_ATTACKED_$n)` or `$<$i MEM_GAVE_$n>`); they are saved. Facts recorded:
`MET` (with `FIRSTMET` and `LASTMET`, game dates), `ATTACKED` (once a fight),
`HURT` (the damage), `HEALED`, `GAVE` (the value given), `STOLE`, `KILLEDKIN`
(of those `KIN` fits), `TALKED`, and `TRACK` facts.

| Parameter | Meaning |
| --- | --- |
| `KIN="[MASK]"` | What counts as the NPC's kin for `KILLEDKIN`. |
| `TRACK=[NAME]:[REGEX]` (also `TRACK1=` to `TRACK9=`) | A count of what it sees the character do that the regex fits. |
| `FORGET=30d` (or `12h`) | A fact fades that long, in game time, after it last grew. |
| `HOSTILEIF=[COND]` | It attacks them on sight. |
| `GREETIF=[COND] GREET="[TEXT]"` | It greets them when they come in (`$n` is their name). |
| `REFUSEIF=[COND] REFUSE="[TEXT]"` | It will not buy, sell or teach for them. |
| `SHARE=AREA` or a mask | It tells what it learns to the other Memory NPCs in its area (those the mask fits). |

A condition is `FACT>N`, `FACT>=N`, `FACT=N` or `FACT<N`, joined by `&` or `|`.
The script function `MEMORY([NPC] [WHO] [MET/COUNT/LASTSEEN/DAYSSINCE/LASTTOPIC]
== [VALUE])` reads some of it.

```text
Memory KIN="-NAMES +villager" SHARE=AREA HOSTILEIF=KILLEDKIN>0 REFUSEIF=STOLE>0 REFUSE="Thief! Out!" GREETIF=HEALED>0 GREET="Welcome back, $n." FORGET=30d
```

### Mime

Hosts: mobs, items, exits, rooms. Timing default 1/1/100. Makes the thing
mimic any emote or social a player in the same room performs, back at that
player.

### Mobile

Hosts: mobs. Timing default 20/60/100. The mob travels in a random available
direction. It does not leave a room holding a visible archon or builder with
the `CMDMOBS` flag. When an area wakes after being passive it takes the steps it
would have wandered meanwhile (at most 10), unseen.

| Parameter | Meaning |
| --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing. |
| `WANDER` | Required if the mob may leave its area. |
| `OPENDOORS` | Open closed doors, and unlock locked ones if it has the key. |
| `LEASH=n` | Do not get farther than n rooms from home. |
| locale words | `-ALL`, `+CITY`, `-UNDERWATER`... see [Locale restriction words](#locale-restriction-words). |

```text
Mobile min=10 max=20 chance=75 wander -air -underwater
Mobile min=10 max=20 chance=75 opendoors
Mobile min=10 max=20 chance=75 leash=3 opendoors
```

### MobileAggressive

Hosts: mobs. Extends [Mobile](#mobile) (timing 20/60/100). Wanders like Mobile
and attacks any player it comes across; especially aggressive toward players
within 16 levels in adjacent rooms. With no mask it attacks anyone within 8
levels. It ignores archons and builders with `CMDMOBS`. Parameters: all of
Mobile's, plus the aggression flags of [Aggressive](#aggressive): `MESSAGE=`,
`DELAY=`, `MOBKILL`, `NOGANG`, `MISBEHAVE`, `CHECKLEVEL`, `BRIBE=`,
`BRIBEPAYMSG=`, `BRIBEPAIDMSG=`, and a mask (allowed is attacked).

```text
MobileAggressive min=10 max=20 chance=75 -underwater -thief +elf
MobileAggressive min=10 max=20 chance=75 message="aha!" -air -elf
MobileAggressive min=10 max=20 chance=75 mobkill misbehave
```

### MobileGoodGuardian

Hosts: mobs. Extends [Mobile](#mobile). Like [GoodGuardian](#goodguardian) but
the mob also wanders the area. Parameters as for Mobile. It does not leave
rooms holding a `CMDMOBS` archon.

```text
MobileGoodGuardian min=10 max=20 chance=75 wander -air -underwater
```

### MoneyChanger

Hosts: mobs. When a player gives the mob coins it gives back a bag holding an
equivalent amount in its native currency, less its cut.

| Parameter | Meaning |
| --- | --- |
| `CUT=n` | Percent the changer keeps (default 5). |
| `DEFAULT=n` | Percent cut for currencies not otherwise named. |
| `[CURRENCY]=n` | A percent cut for a named currency (`GOLD=5 INGITS=10`). |
| `SPACEMAXCUT=n` | For interstellar currencies: the percent cut for an unknown or maximum-distance currency. |
| `SPACEMAXDISTANCE=n` | Percentage of the galactic radius used as the maximum distance. |
| `RATE.[CURRENCY]=+n%` / `-n%` | CoffeeSUD: a premium (+) or discount (-) on what coins of that currency fetch, before the cut. It is no fee, so the changer still takes every currency. `RATE.DEFAULT` for the default currency. |

`MPEXCHANGE` sets a rate for every money changer in the world, kept in the
save.

```text
MoneyChanger cut=5
MoneyChanger default=0 GOLD=5 INGITS=10
MoneyChanger cut=5 spacemaxcut=90 spacemaxdistance=35
MoneyChanger cut=5 RATE.CROWN=-30 RATE.LEAF=+10
```

### Morale

Hosts: mobs. CoffeeSUD. The mob's nerve breaks: when its hit points fall under
`FLEEAT`, when its foes here outnumber its side `OUTNUMBERED` to one, or as
`BREAK` says. Its side is its group, its leader's, and NPCs of its race here who
fight. Then, at `CHANCE` percent a tick, it says `MSG` (`:text` for an emote) and
does `ACTION`.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `FLEEAT=[N]%` | Break when hit points drop under N%. | 0 (off) |
| `OUTNUMBERED=[X]` | Break when foes outnumber its side X to one. | 0 (off) |
| `BREAK=LEADERDEAD,[N]%DEAD,HP<[N]%` | Comma list: its leader dies (the one `LEADER` names, or the mob it follows), that share of its side has fallen, its side's hit points together. | none |
| `LEADER="[ZAPPER MASK]"` | Who counts as its leader. | the mob it follows |
| `ACTION=FLEE/SURRENDER/BERSERK` | `FLEE` goes to `RALLY` and waits `REGROUP` ticks, then returns with the NPCs it finds there and attacks those it fled from. `SURRENDER`: surrenders. `BERSERK`: a third more damage and a tenth less attack until the fight ends. | FLEE |
| `CHANCE=[N]` | Percent chance per tick. | 100 |
| `MSG="[TEXT]"` | What it says. | none |
| `RALLY=[ROOM ID]/HOME` | Where it flees to. | none |
| `REGROUP=[N]` | Ticks waiting at the rally point. | 10 |
| `SURRENDERAT=[N]%` | Surrenders whatever else: drops its weapon, foes and it make peace, it says `SURRENDERMSG`, its scripts' `SURRENDER_PROG` runs (`$n` one it fought). | 0 (off) |
| `SURRENDERMSG="[TEXT]"` | | `I yield! Mercy!` |
| `BRAVE="[ZAPPER MASK]"` | Those the mask fits never break. | none |

```text
Morale FLEEAT=40% BREAK=LEADERDEAD ACTION=FLEE RALLY=Warren#12 REGROUP=15
Morale SURRENDERAT=20% SURRENDERMSG="Mercy! I'll show you the loot!"
```

### MovingRoom

Hosts: rooms. A room that moves between stops: subways, buses, caravans,
elevators, ships, stargates. The classic form of the parameter (help ID
`BEHAVIOR_MOVINGROOM`, title "Mover"):

```text
min=3 max=3 chance=100;[xmlsection];[ticks at stops];[ticks between stops]
```

It reads the named section of `resources/movingroom.xml` (the shipped example is
`SUBWAY`). Requirements: the rooms the mover opens to must already exist; the
XML section must be written first; a moving room cannot override an existing
exit (if room A links south to B, the mover cannot link north to A). In the XML
the strings `$disproom`, `$inopendir`, `$outopendir` and `$traveldir` are
replaced on the fly. `<STOPS>` lists the room IDs (`<STOP1>`...);
`<NORMALDIRECTION>` and `<REVERSEDIRECTION>` define going out and coming back
(`TRAVELDIRECTION`, `DOORSDIRECTION`, `<INSIDE>`/`<OUTSIDE>` with `ARRIVALINFO`
and `DEPARTINFO`); `<ROOMDESCRIPTIONS>` holds the text appended with
`DOOROPENED` and `DOORCLOSED`.

CoffeeSUD adds timetable-style keys:

| Key | Meaning |
| --- | --- |
| `ROUTE=[file under world/routes/]` or `ROUTE="[inline XML]"` | The route. |
| `LINE=[section]` | The section of the route file. |
| `WAIT=[ticks]` | Ticks at stops (default 10). |
| `TRAVEL=[ticks]` | Ticks per leg (default 10). |
| `DEPART=[game hours, comma separated]` | Departure hours. |
| `SIGN="[item name]"` | An item whose text shows the timetable. |
| `DISPLOC=true/false` | Add the timetable to the room description. |

Position, direction and remaining time are saved. Boarding exits and
descriptive text are temporary overlays excluded from export. `TIMETABLE` shows
the line and position. See [chapter 30](30-travel-vehicles-instances.md).

```text
MovingRoom min=3 max=3 chance=100;SUBWAY;3;10
```

### MudChat

Hosts: mobs. Makes the mob speak and answer speech from a chat file. The
parameter is `(([data file]=)[group])(+[extra entries])`.

- `chat.dat` (in `resources/`) holds templates for different speech behaviors;
  MudChat chooses one from the mob's name unless a group is named. The default
  patterns include: default, dog fido beastly fido_dog, cat kitten lion, pig
  boar, doll toy, papa_smurf smurf, monster, smurfette, healer cleric doctor.
- `smurf` names a group in the default file; `mychat.dat=cityguard` names a file
  and a group; `mychat.dat=` the file with the group chosen by name.
- A leading `+` introduces a `;`-delimited list of chat entries in `chat.dat`
  format; a `+` after the file or group adds entries to it.
- `DELAY=[N]` anywhere in the parameters: ticks it waits before it answers
  (default 2). When replies come at once (the `DIALOGUETIME` setting,
  `TIMEMODE DIALOGUE`) its answer comes before the player's next prompt
  whatever the delay.
- CoffeeSUD chat-file extensions: a line `?[CONDITION]` before an entry is a
  script condition, as after `IF`, which must hold for it (`$t` and `$n` are
  the speaker, `$i` the mob), for example `?QUESTSCRIPTED($t mine)` and `?VAR($t
  MET == 1)`. A reply starting with `&` runs the rest as a MOBPROG line with the
  mob as host and the speaker as `$n` (`9"Clear the rats.~&mpsetvar $n JOB
  given`). `$@[GROUP]` at a reply's end switches to that group for that speaker
  alone for six minutes; `$@![GROUP]` until it is changed (kept in the saved
  variable `MUDCHAT_GROUP_[SPEAKER]` of the mob); `$@-` back to the base group.

The chat file format is covered in [chapter 6](06-npcs-dialogue.md).

```text
MudChat smurf
MudChat chat.dat=smurf+(smurfy);9Smurfy!
MudChat mychat.dat=cityguard
MudChat +(here there);9neither;5well, where?;1nowhere
```

### Nanny

Hosts: mobs. Makes the mob a protector and watcher of mobs and items for money
(a stable, nursery, parking place). Parameters:

```text
RATE=[PRICE] NAME="[PLACE NAME]" WATCHES="[CODES]" (|~| [XML])
```

| Parameter | Meaning | Default |
| --- | --- | --- |
| `RATE=n` | Base gold value charged for each thing dropped off, per hour. | 2.0 |
| `NAME="..."` | A description of the place he runs. | `nursery` |
| `WATCHES="a,b"` | Comma-separated kinds (no spaces) he will watch: `babies` (baby items from breeding), `children`, `mounts` (rideable mobs), `cars` (land-rideable non-wagon items), `aircars`, `wagons`, `followers` (mobs that are not children or mounts), `boats`. | `Babies,Children` |
| `NETWORK=[name]` | CoffeeSUD: links sister stables. Owners can collect mounts from another keeper in the network, paying accrued keep plus the send fee. | none |
| `SENDFEE=[price]` | CoffeeSUD: the remote retrieval fee. | 0 |

The mob saves the items and mobs in the room he watches and keeps them from
decaying. Combat against him or mobs under protection is forbidden, as is
removing any mob or item not paid for. The first mud-hour is free; protection
does not start until the owner leaves. `STABLE <mount>`, `STABLE LIST` and
`RETRIEVE <mount>` manage explicit custody; these requests also work in speech.
Custody and payment records survive saves. Do not put Nanny on a mob that is in
the CATALOG, because of the way it stores its records.

```text
Nanny rate=2.5 name="The Stables" watches="mounts,wagons"
```

### NastyAbilities

Hosts: mobs. Timing default 10/20/100. The mob uses any of its malicious
abilities (spells, prayers, songs...) to the detriment of random player mobs in
the same room. Parameters before the optional `;`, then a mask (the nasty acts
only on those it allows).

| Parameter | Meaning |
| --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing. |
| `FIGHTOK` | The spells may start combat (otherwise they do not). |
| `CHECKLEVEL` | Do not pick on lower level creatures (the help also calls this `LEVELCHECK`; the code reads `CHECKLEVEL`). |

```text
NastyAbilities min=10 max=20 chance=75 fightok
NastyAbilities min=1 fightok ; -RACE +ELF
```

### NeutralAbilities

Hosts: mobs. Timing default 10/20/100. The mob uses any of its indifferent
abilities on random player mobs in the room, optionally limited to the matching
of a mask after `;`. `SKILLS="[id],[id],..."` limits the skills used.

```text
NeutralAbilities min=10 max=20 chance=75
NeutralAbilities min=1 SKILLS="Spell_Light,Spell_Fly" ; -RACE +ELF
```

### NiceAbilities

Hosts: mobs. Timing default 10/20/100. The mob uses its benevolent abilities on
player mobs in the room, only for those a mask after `;` allows. CoffeeSUD:
`HEALAT=[N]` (before the `;`) has it heal the most hurt of those it helps who
are under N% of their hit points, and nothing else.

```text
NiceAbilities min=10 max=20 chance=75
NiceAbilities min=1 max=1 HEALAT=60 ; -RACE +Goblin
```

### NoCombatAssist

Hosts: mobs. No parameters. Makes sure the mob does not assist in combat when it
follows a player or another mob.

### ObjectGuardian

Hosts: mobs. Prevents any other mob from taking or removing any object in the
room (you have to kill it). Dropping is also forbidden unless `DROPOK`.

| Parameter | Meaning |
| --- | --- |
| `SENTINAL` (or `SENTINEL`) | Even more vigilant: combat and severe damage do not deter it. |
| `DROPOK` | Dropping items is allowed. |
| `MOBMASK="[MASK]"` | Zapper mask filtering which mobs it stops. |
| `ITEMMASK="[MASK]"` | Zapper mask filtering which items it protects. |

```text
ObjectGuardian SENTINAL
```

### Patroller

Hosts: mobs, items. Timing default 5/10/100. The host walks the path given in
the parameters. Parameters: `settings;step;step;...`.

| Setting (before the first `;`) | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing. | 5/10/100 |
| `RIDEOK=true/only/false` | Rideable mobs and items stop patrolling when ridden; `true` makes them continue though passengers can drive them; `only` keeps them on track and prevents driving. | false |
| `DIAMETER=n` | Enlarge when the patroller can get more than 20 rooms off track. | 20 |
| `RESUME=true` | After a fight, walk back to where it left its route before going on. | false |

| Step | Meaning |
| --- | --- |
| a room ID | Tracks there from its current position even if not adjacent. |
| a direction | Must be valid from the current position. |
| `RESTART` | Start the path from the beginning. With no `RESTART` the object retraces its steps back to the beginning. |
| `.` | Pause one step. |
| `WAIT=[N]` | CoffeeSUD: wait N ticks. |
| `DO=[COMMAND]` | Do a command (`DO=lock door`, `DO=say Gate secure.`). |
| `EMOTE=[TEXT]` | Emote. |
| `IF=[MASK]` | Go on only if the mask fits the patroller, else to the next `RESTART` (or the start). |
| `ROUTE=[NAME]:[FIRST STEP]` | Start a named route; its steps run to the next `ROUTE=`. The one named for the time of day (`DAWN`, `DAY`, `DUSK` or `NIGHT`) is walked, else `ROUTE=DEFAULT`, else the first; from its start when it changes. |

The new steps are not walked back on a route with no `RESTART`. Mobs unlock and
open doors on the route; items ignore doors. See `Prop_StayAboard` for patrollers
with riders ([chapter 23](23-properties-reference.md)).

```text
Patroller min=10 max=20 chance=75 rideok=true;Midgaard#3001;#3002;#3003;RESTART
Patroller min=10 max=20 chance=15;east;.;north;north;east
Patroller min=3 max=3;Gate#1;WAIT=5;DO=say Gate secure.;Wall#4;EMOTE=peers over the battlements.;RESTART
Patroller min=3 max=3;ROUTE=DAY:Gate#1;Wall#4;RESTART;ROUTE=NIGHT:Barracks#1;Barracks#2;RESTART
```

### PlayerHelper

Hosts: mobs. Comes to the defence of any player mob against any non-player mob.
Optional: a number (most combatants that can join) and `MSG="[ATTACK MESSAGE]"`.

```text
PlayerHelper 5 msg="OMG ATTACK!"
```

### PokerDealer

Hosts: mobs (the help also lists rooms; the code allows mobs). Makes the host a
dealer of poker, managing hands, betting, draws and more. The dealer cannot
play, so at least 2 players are needed.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `GAME=[n or name]` | `0` Straight Poker, `1` Five Card Stud, `2` Seven Card Stud, `3` Draw Poker (or the name). | 0 |
| `MINPLAYERS=n` | Minimum players to start a game. | 2 |
| `ANTI=amount` | Currency required to ante and join. | 1.0 |

```text
PokerDealer game=2 minplayers=4 anti=5.0
```

### ProtectedCitizen

Hosts: mobs. Timing default 1/3/99. The mob screams for help during combat.
Parameters: `settings;guardian mask;yell strings`.

| Setting | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | Ticks between screams, and the percent chance. | 1/3/99 |
| `RADIUS=n` | Rooms around the mob searched for a guardian. | 7 |
| `MAXASSISTS=n` | Most mobs that will assist. | 1 |
| `WANDER` | Allow mobs from adjacent areas to be drawn in. | off |

Whenever it screams the system searches `RADIUS` rooms for another mob that fits
the mask after the first semicolon (the guardian mask: allowed is assisted). If
the mob has a Mobile-family behavior, it is moved one room closer to the
screamer; arrived, it joins the defence. The remaining `;` separated strings are
what the screamer yells; start one with a comma to have it emoted instead.
Once a mob has come to its defence it stops screaming.

```text
ProtectedCitizen min=1 max=5 chance=25 radius=7 maxassists=1;-NAME +cityguard;HELP ME!!!;Save me!
ProtectedCitizen WANDER radius=20;-NAME +cityguard;HELP ME!!!;,screams for his life!
```

### ProtectedCitizens

Hosts: mobs, rooms, areas. As [ProtectedCitizen](#protectedcitizen) but the host
also has a citizen mask: the settings are followed by `;citizen mask;guardian
mask;yell strings`. Mobs the citizen mask fits scream for help. Same defaults
(1/3/99, radius 7, maxassists 1).

```text
ProtectedCitizens radius=5 maxassists=7;-NAME +citizen;-NAME +cityguard;HELP ME!!!;Save me!
```

### PuddleMaker

Hosts: rooms, areas. After rain, sleet, thunderstorm or similar, the host
generates a puddle or some snow (or dust after a dust storm) on the given
percent chance. They evaporate after a time. The only parameter is the percent
(default 50).

```text
PuddleMaker 34
```

### Pursue

Hosts: mobs. CoffeeSUD. The mob chases one who flees it.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `CHANCE=n` | Percent chance it gives chase. | 60 |
| `MASK="[ZAPPER MASK]"` | Whom it chases. | anyone |
| `MAXROOMS=n` | Rooms in all before it gives up. | 3 |
| `DELAY=n` | Ticks before each room step (a room a tick after that). | 1 |
| `LEASH=n` | Gives up past n rooms from home. | 0 (none) |
| `STAY` | On giving up, stay where it is instead of walking home. | off |
| `GIVEUPMSG="..."` | Said on giving up. | `<S-NAME> give(s) up the chase.` |
| `REMEMBER=n` | One who fled and comes back within n ticks is attacked on sight. | 0 |

It attacks them where it catches them; it also gives up when it loses the trail.

```text
Pursue CHANCE=80 MAXROOMS=4 LEASH=8
Pursue CHANCE=50 MAXROOMS=10 MASK="-RACES +Goblin" REMEMBER=30
```

### QuestChat

Hosts: mobs. Extends [MudChat](#mudchat) (same parameters) with a feature for
quests. When the first character of the parameters is a `+`, a `;` delimited
list of chat data follows; the trigger pattern of an entry may be followed by
`::` and immediately a variable string, which makes that pattern answered only
once for each player. The string is added to a list under the player's name in
any quest object, available through the `QVAR(questname CHAT:PLAYERNAME)`
function; this lasts for the run of the quest, and a saved game keeps it.

```text
QuestChat +(speak | talk | words::DONE_SPEAKING);9only said this to YOU once.
```

### ROMGangMember

Hosts: mobs. Supports ROM area files. The mob attacks any other mob that has
this behavior but is in a different gang. Not active when a mob with
[ROMPatrolman](#rompatrolman) is in the room. Parameters:
`[GANG NAME](;[ATTACK MESSAGE]...)`: the first is the gang, the rest are things
the mob says before attacking.

```text
ROMGangMember Troll;I`m gonna kill you!;I`ve got you now!;DIE!
```

### ROMPatrolman

Hosts: mobs. No parameters. Supports ROM area files. The mob complains whenever
a fight goes on in its room, and may do nothing, join the fight, or break it up.

### RaceHelper

Hosts: mobs. Joins a fight on the side of an identical mob or one of the same
race. Optional: a number (most combatants) and `MSG="[ATTACK MESSAGE]"`.

```text
RaceHelper 5 msg="OMG ATTACK!"
```

### RandomEncounters

Hosts: areas, rooms. CoffeeSUD. As the party moves, each room it enters may
bring an encounter from the table `world/encounters/NAME.enc` (`AHELP
ENCOUNTER`), rolled on the next tick for the one who leads the party in (if
still there).

| Parameter | Meaning | Default |
| --- | --- | --- |
| `TABLE=[NAME]` | The encounter table. | required |
| `CHANCE=[N]%` | Percent per room entered. | 10 |
| `COOLDOWN=[N]` or `[N]DAYS` | N rooms must go by after an encounter, or N game days. | 0 |
| `LOCALES="[KIND],[KIND]"` | Kinds of room (WOODS, PLAINS, CITY...). | all |
| `TRIGGER="[ZAPPER MASK]"` | A mask on the one who came in. | none |
| `MAXACTIVE=[N]` | Monsters of its encounters about at once. | 6 |
| `DESPAWN=[N]` | Ticks they stay once the party has left them, unless fighting. | 10 |
| `BUDGET=[N]%` | Adds the table's MOBS entries until the monsters' power is that share of the party's (mob ranks count). | 0 |

See [chapter 29](29-combat-and-encounters.md).

```text
RandomEncounters TABLE=darkwood CHANCE=10% COOLDOWN=6
RandomEncounters TABLE=moors CHANCE=15% LOCALES="PLAINS,HILLS" TRIGGER="-LEVEL +>3" BUDGET=80%
```

### RandomItems

Hosts: areas, rooms, mobs, items (containers). Reads a list of items from a
`.cmare` file made with `EXPORT AREA ITEMS` or `EXPORT ROOM ITEMS`; the path is
the last parameter, after a `;`, relative to the `resources` directory. The
XML itself (`<ITEMS><ITEM>...`) may be pasted in instead. Parameters before the
`;` give the rate and amount.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | It tries to generate every min to max ticks if the chance roll succeeds. | 10/30/100 |
| `MINITEMS`, `MAXITEMS` | At least and at most this many items. Duplicates allowed. | 1 and 1 |
| `MOBS` | Put the items on a mob if possible (worn if possible); on a shopkeeper the items go up for sale. Automatic on a mob host. | off |
| locale words | On an area, restrict room types (see [Locale restriction words](#locale-restriction-words)). | all rooms |
| `AFFIXES=[...]` | CoffeeSUD: item affixes applied to generated items (see [chapter 12](12-generation.md)). | none |

Items taken away are replaced, even when looted. On a room all items appear in
that room; on an area a random room is picked.

CoffeeSUD spawn rules (also in RandomMonsters), written before the `;`, or with
no `;` and no file:

| Rule | Meaning |
| --- | --- |
| `CATALOG="name=weight,name=weight"` | Picks catalog entries by weight (counted and capped as `catalog:` is). |
| `WHEN="[ZAPPER MASK]"` | Tested on the room a spawn would go to (`-SEASON +WINTER`, `-HOUR +20`, `-LOCATION +ROOMSTR(moonlit)`...); nothing spawns where it fails. |
| `DESPAWN=WHENFALSE / DAWN / TICKS=[N]` | Makes a spawn leave, unseen: `WHENFALSE` when the mask no longer passes where it is, `DAWN` at dawn or by day, `TICKS=N` N of the behavior's ticks after it came. Not while a player is in its room, nor while it fights. |
| `NEAR=PARTY:[N]` | On an area: spawns only within N rooms of the player's side. |
| `MAXAREA=[N]` | On an area: spawns nothing while the area holds N spawns (of any spawner). |

For RandomItems the rules for rooms (`WHEN`, `NEAR`, `MAXAREA`, `DESPAWN`) are
for items left in rooms.

```text
RandomItems min=10 max=20 chance=75 minitems=1 maxitems=10;myitems.cmare
RandomItems minitems=10 maxitems=20 MOBS -ALL +CITY;stuff.cmare
RandomItems maxitems=3 CATALOG="pine cone=5,amber=1" DESPAWN=TICKS=200
```

### RandomMonsters

Hosts: areas, rooms. As [RandomItems](#randomitems) but for monsters. The list
is read from a `.cmare` made with `EXPORT AREA MOBS` or `EXPORT ROOM MOBS`; the
last parameter may be pasted `<MOBS>` XML, or `catalog:NAME,NAME` (the catalog's
mobs: their copies are counted, and none is made past an entry's cap).

| Parameter | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing. | 10/30/100 |
| `MINMONSTERS`, `MAXMONSTERS` | At least and at most this many. | 1 and 1 |
| locale words | Restrict room types on an area. By default non-flying mobs are never generated in air rooms, nor non-swimmers underwater. | all rooms |
| `LEVEL=PARTY / PARTY+[N] / PARTY-[N] / PARTY+-[N]` | CoffeeSUD: each spawn gets the party's highest level (give or take), its stats by the level formulas. | off |
| `CATALOG`, `WHEN`, `DESPAWN`, `NEAR`, `MAXAREA` | CoffeeSUD spawn rules, as in [RandomItems](#randomitems). | none |

Monsters that die are replaced, even if they wander out of the room or area. On
a room all appear there; on an area a random room is picked.

```text
RandomMonsters min=10 max=20 chance=75 minmonsters=1 maxmonsters=10;monsters.cmare
RandomMonsters minmonsters=10 maxmonsters=20 -ALL +CITY;criminals.cmare
RandomMonsters minmonsters=2 maxmonsters=4;catalog:a goblin,a goblin shaman
RandomMonsters maxmonsters=6 CATALOG="grey wolf=4,dire wolf=1" WHEN="-SEASON +WINTER" DESPAWN=DAWN NEAR=PARTY:3 LEVEL=PARTY+-2
```

### RandomQuests

Hosts: areas, mobs. Timing default 10/20/100. Generates random quests inside
the host area, or on behalf of a host mob. Be generous with the timing; quest
generation is CPU-intensive.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | How often an attempt is made. | 10/20/100 |
| `MINQUESTS`, `MAXQUESTS` | How many quests are generated at one time; elapsed quests are replaced. If several are offered by one mob the player must complete them in order. | 1 and 3 |
| `PATH` | XML document the random quest data is read from. | `randareas/example.xml` |
| `TAGID` | Tag of the QUEST object in the document. | `all_quests` |
| `EXPIRE` | How long a quest runs before expiring forever, even if accepted moments before. | `3 hours` |
| `MAXATTEMPTS` | Attempts to generate one quest (not all areas or people support every type). | 3 |
| `INLINE` | (code only, not in the help) read the quest data in-line. | false |

Other `VAR=value` pairs are passed to the generator (defined by
`resources/quests/random/*.xml`): `TEMPLATE` (default `normal`), `AVOIDSCANS`,
`AREA_NAME`, `SOURCE_NAME`, `TARGETAREA_NAME`, `MULTIAREA`, `TARGETNAME`,
`NUM_TARGETS`, `AGGRESSION`, `XP_AMOUNT_AWARDED`, `GOLD_AMOUNT_AWARDED`,
`SOURCE_FACTION` and `SOURCE_FACTION_AMT`, `QUEST_POINT_AWARDED`,
`QUEST_MULTIPLE` (YES, NO or PREV), `ANNOUNCECHANNEL`, `TARGET_MIN_AREALEVEL`,
`TARGET_MAX_AREALEVEL`. A saved game keeps the quests this behavior generated,
whole, with their progress; a restore makes them again, and the behavior takes
them back rather than generating others. See [chapter 8](08-quests.md).

```text
RandomQuests minquests=3 maxquests=5 template=normal_capture1
```

### RandomTeleporter

Hosts: mobs. Timing default 1/5/100. The mob teleports itself to a random room on
the map, by default anywhere in the world.

| Parameter | Meaning |
| --- | --- |
| `NOWANDER` | Keep it in its area. |
| locale words | Restrict the room types (`-air -underwater`). |
| `"POOFIN=[MESSAGE]"` | Message when it arrives (spacing and quotation sensitive). |
| `"POOFOUT=[MESSAGE]"` | Message when it leaves. Known defect: in the code `POOFOUT=` assigns the poof-in message (see the audit notes). |

```text
RandomTeleporter min=10 max=20 chance=75 -air -underwater
RandomTeleporter min=10 max=20 chance=75 nowander "poofin=<S-NAME> Arrives!"
```

### RandomTraps

Hosts: areas, rooms. Timing default 10/30/100. Generates traps in the room or
area.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `MIN`, `MAX`, `CHANCE` | Frequency and chance. | 10/30/100 |
| `MINTRAPS`, `MAXTRAPS` | Number of traps generated. | 1 and 1 |
| `ROOMS=YES/NO` | Allow trapping rooms. | NO |
| `EXITS=DOORS/LOCKED/NO` | `DOORS`: all doors; `LOCKED`: only locked doors; `NO`: none. A `Y` start also means DOORS. | `LOCKED` (only closed, locked doors) |
| `ITEMS=YES/CONT/LID/LOCK/NO` | `YES`: any items; `CONT`: containers only; `LID`: containers with lids; `LOCK`: containers with locks; `NO`: never. | NO |
| locale words | Which room types are checked (`-ALL +CityStreet`). | all |

The settings go before the first `;`.

```text
RandomTraps min=10 max=20 chance=25 mintraps=1 maxtraps=10 EXITS="YES" ITEMS="YES" ROOMS="YES"
RandomTraps min=10 max=20 chance=25 ROOMS="YES" ITEMS="NO" -ALL +CityStreet
```

### Regenerate

Hosts: areas. CoffeeSUD. The area re-rolls itself. It is built again from `FILE`
and `ID` (with `VARS`), or from its `GENSOURCE` (what `GENERATE AREA` recorded)
when they are left out, as `REGENERATE AREA` does. Rooms marked `GENLOCK`, rooms
named in `KEEP`, rooms a running quest uses (or with someone or something in
them) stay as they are; the rest are made again, and the gate and every other
exit go to the rooms of the same IDs. It never re-rolls while a player, or one
following a player, is inside: it waits for them to leave. The area's
`GENERATED_PROG` runs after.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `FILE=[TEMPLATE]` | Template file. | the area's GENSOURCE |
| `ID=[AREA ID]` | Template ID. | the area's GENSOURCE |
| `VARS="[VAR]=[VAL] ..."` | Variables. | none |
| `SEED=RANDOM/DAILY/WEEKLY/SAME/[N]` | `RANDOM` is new each time; `DAILY`/`WEEKLY` one seed for the game day or week; `SAME` the GENSOURCE's; or a number. | RANDOM |
| `WHEN=` a comma list of `MUDDAY:N`, `MOON:FULL` (or another phase: NEW, WAXCRESCENT... as it begins), `ONEMPTY`, `SCRIPT` | When it re-rolls: every N days of the area's calendar; at that moon phase; as the party leaves; or only when `MPREGENERATE` asks. | SCRIPT |
| `KEEP="[ROOM ID] ..."` | Rooms to keep. | none |

Its state (when the days count from, the moon last seen, a re-roll waiting) is
saved; the rooms are kept as rooms changed in play. See
[chapter 12](12-generation.md).

```text
Regenerate WHEN=MOON:FULL SEED=RANDOM
Regenerate WHEN=ONEMPTY SEED=DAILY KEEP="Old Tunnels#0"
```

### ResetWhole

Hosts: rooms, areas. A different way of resetting rooms and areas: the system
totally resets a room (mobs and items) that no player has visited for the given
time. An area with this behavior skips rooms that have it too, so rooms can
have their own total reset times. It does not override the usual rejuvenation of
mobs and items; to use ResetWhole exclusively set the rejuv rate of every mob and
item to 0.

| Parameter | Meaning | Default |
| --- | --- | --- |
| a bare number | Ticks of inactivity before the reset. | 30000 ms (see below) |
| `TICKS=n` | The same. A tick is `TICKTIME` ms. | |
| `DAYS=n` | CoffeeSUD: game days on the area's calendar after the party was last there, instead of ticks. | |
| `RLHOUR=h` | Real-life hour (0 to 23) of the reset. Reads the real clock, which means nothing with pause and turn-based time; use `MUDHOUR` or `DAYS`. | |
| `MUDHOUR=h` | Game hour of the day. | |
| `EXCEPT="[MASK]"` | CoffeeSUD: mobs and items that never come back once gone: a slain boss or taken loot stays gone; one still there at the reset stays. Names with spaces in single quotes: `+'the Goblin King'`. | none |
| `MSG="[TEXT]"` (or `RESETMSG=`) | Shown to the first who comes in after a reset. | none |
| `ONREENTRY=true` | CoffeeSUD: resets on return after everyone has left. | false |
| `MINAWAY=[game hours]` | Minimum absence. | 0 |
| `SKIPROOMS="[room mask]"` | Matching rooms are left unchanged, for example `SKIPROOMS="-LOCATION '+ROOMSTR(Sanctum)'"`. | none |

Last access and absence survive saves. `InstanceArea INPLACE=true` reapplies its
type adjustments after resetting the real rooms, using the entering leader's
level.

```text
ResetWhole 4000
ResetWhole DAYS=7 EXCEPT="-NAME +'the Goblin King'" MSG="Fresh tracks lead into the tunnels."
```

### ResourceOverride

Hosts: rooms, areas, mobs. Timing default 10/30/100 (its countdown starts at
1). Overrides the resources that rooms generate for common skills (mining,
chopping, farming...), or those corpses give when butchered. It randomly picks
from the list given instead of the normal list. On an area, name room types to
apply the new resources to.

Parameters: `MIN`, `MAX`, `CHANCE`; resource names or code numbers;
room types (`STONE`, `WOODEN`, `CAVE`, `MAGIC`, `UNDERWATER`, `AIR`,
`WATERSURFACE`, `METAL`, `CITY`, `WOODS`, `ROCKY`, `PLAINS`, `JUNGLE`, `SWAMP`,
`DESERT`, `HILLS`, `MOUNTAINS`); a resource in double quotes followed by a space
and a sub-type (`"CORN WIERDCORN"`); and a custom resource by putting the name
in quotes followed by the resource type and sub-type in parentheses
(`"some weird corn(CORN WIERDCORN)"`).

CoffeeSUD calendar keys, by the room's area calendar:

| Key | Meaning |
| --- | --- |
| `SPRING=`, `SUMMER=`, `AUTUMN=` (or `FALL=`), `WINTER=` | Each gives a season its own comma list of resources (`none` for nothing), swapped in as the season turns. A season with no list uses the plain list, if there is one, else the rooms' own. |
| `SEASON=`, `MONTH=`, `HOUR=` | Comma lists, with ranges as `20-23`; limit when it applies at all. Outside them the rooms have their own resources back. |
| `MSG="[TEXT]"` (or `MESSAGE=`) | Shown in a room when the calendar changes its resource. |
| `MOBMASK=[MASK]` | (not in the help) a mask of mobs it applies to; must come last, the rest of the text is the mask. |

When changing the parameters of an existing ResourceOverride on an area use
`RESET AREA` to change back rooms previously affected.

```text
ResourceOverride MIN=5 MAX=5 CHANCE=100 CORN WHEAT RICE
ResourceOverride CORN "some weird corn(CORN WIERDCORN)" "CORN WIERDCORN"
ResourceOverride SPRING=flowers,herbs SUMMER=blueberries AUTUMN=apples WINTER=none MSG="The orchard turns with the season."
ResourceOverride MUSHROOMS SEASON=autumn HOUR=20-23,0-4
```

### Riddler

Hosts: mobs, items. CoffeeSUD. A mob or thing that asks riddles.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `RIDDLE="[TEXT]"`, `ANSWERS="[ANSWER]\|[ANSWER]"` | The riddle and its accepted answers. More with `RIDDLE1` to `RIDDLE9` and `ANSWERS1` to `ANSWERS9`. | none |
| `RANDOM=true` | Pick one riddle at random. | the first |
| `FUZZY=[N]` | Forgive N wrong letters (or two swapped). Case, a, an, the and punctuation never count. | 0 |
| `ASK=GREET,LOOK,COMMAND` | Who is asked: one of the player's side who comes in, one who looks at it, one who types `RIDDLE`. | all three |
| `TRIES=[N]` | Wrong answers before `ONOUT` runs. | 3 |
| `ONRIGHT`, `ONWRONG`, `ONOUT` `="[ACTIONS]"` | Script commands run on each right answer, each wrong one, and when tries run out (`MP` may be left off; `$n` who answered; as `Prop_Switch`'s). | none |
| `RIGHTMSG`, `WRONGMSG` | Told on a right or wrong answer. | `That is right.` / `That is not right.` |
| `ONCE=CHARACTER/SAVE/NONE` | `CHARACTER`: asks each character until they solve it. `SAVE`: until anyone does (kept with it). `NONE`: always. | CHARACTER |

Answer by saying it (`SAY`) or with `ANSWER [TEXT]`.

```text
Riddler RIDDLE="What has roots nobody sees, and is taller than trees?" ANSWERS="mountain|mountains" FUZZY=1 TRIES=3 ONRIGHT="door open here north; exp $n 100" ONOUT="force $i kill $n"
```

### RoomStates

Hosts: rooms, areas. CoffeeSUD. Named versions of a room, declared in one place;
clauses go with a `;` between them. A saved game keeps the state; `MPROOMSTATE`
sets it and `ROOMSTATE()` reads it.

```text
STATE=[NAME] (TITLE=) (DESC=) (ATMOSPHERE=) (CLIMATE=) (ADD="[ID]([PARMS]);...") (BLOCK=[DIRS]) (OPEN=[DIRS]) (ENTERMSG=); TO=[NAME] (FROM=[NAMES]) (WHEN="[MASK]") (AFTER=[TICKS]) (TICKS=[N]) (ONVAR="[HOST] [VAR]==[VALUE]"); START=[NAME]; (ROOMS=[ROOM IDS])
```

A `STATE` sets the room's title, description, atmosphere (`WATER`, `AIR`...) and
climate (`WET`, `COLD`...), gives it effects and behaviors (`ADD`, with their
parameters in brackets; taken away when the state ends), refuses ways out
(`BLOCK`), opens doors (`OPEN`) and says `ENTERMSG` to those coming in. What it
leaves unset is the room's own. A transition (`TO`) goes from the states named
(`FROM`, or any) when `WHEN` holds (a mask on someone standing there, such as
`-WEATHER +RAIN`), `AFTER` ticks in a row, after `TICKS` in the state, or when
a script variable holds (`ONVAR`, host `*` if not given). `START` names the
first state. On an area all its rooms (or `ROOMS=`) change together.
[TidalRoom](#tidalroom) and [Floodable](#floodable) are built on it.

```text
RoomStates STATE=dry TITLE="A dusty shaft"; STATE=collapsed TITLE="A collapsed shaft" ADD="Prop_RoomDark()" BLOCK=down; TO=collapsed ONVAR="mine collapse==1"; START=dry
```

### Sailor

Hosts: mobs. Turns a mob into a member of the crew of a large boardable sailing
ship. With no arguments the mob stands idle until its ship is in combat, then
loads and aims weapons it finds. By default it acts more often by level (1 to
30). If the mob also has the Shipwright behavior (a crafting skill) it mends the
ship when not in combat or when the ship is badly hurt; a mob with Grapples and
Mobile throws grapples and boards enemy ships. See
[chapter 30](30-travel-vehicles-instances.md).

| Parameter | Meaning | Default |
| --- | --- | --- |
| `TICKDELAY=n` | Ticks between actions, in combat or not. | 30 less `TICKBONUS` less level/4 |
| `TICKBONUS=n` | Subtracted from the delay. | 0 |
| `PEACEMOVER=true/false` | Sails the ship around at random when not in combat. | false |
| `AREAONLY=true/false` | The ship stays in the area when sailing around. | true |
| `FIGHTMOVER=true/false` | Sails around during combat. | true |
| `WIMPY=true/false` | With `FIGHTMOVER`, runs away when losing. | true |
| `FIGHTTECH=true/false` | Loads and aims weapons in combat. | true |
| `AGGRO=true/false` | Targets other ships it meets and starts fights. | false |
| `AGGROMOBS=true/false` | With `AGGRO`, also fights mob-only ships. | false |
| `AGGROLEVELCHECK=true/false` | With `AGGRO`, only near-level enemy ships. | false |
| `AGGROMASK=[MASK]` | With `AGGRO`, a zapper mask to filter enemies. | none |
| `DEFENDER=true/false` | Seeks the deck to defend against boarders. | false |
| `BOARDER=true/false` | Seeks the enemy ship to attack its crew. | false |

```text
Sailor tickdelay=10 peacemover=true areaonly=false
```

### Scavenger

Hosts: mobs. Timing default 1/10/99. The mob picks up and takes away random
objects in its room, but does not do its work while a player character is in
the room. When "filled up" it destroys the items unless told otherwise.
Parameters before an optional `;`, then an item mask (allowed is picked up).

| Parameter | Meaning |
| --- | --- |
| `MIN`, `MAX`, `CHANCE` | Timing. |
| `TRASH=[ROOMID]` | When full, track to that room and put the things in any ungettable container there, or on the ground. `TRASH="no"`: when full, do nothing more (it keeps what it has). |
| `ATNIGHT=true` | (not in the help) at night it works every tick and dumps its haul. |

```text
Scavenger min=10 max=20 chance=75 trash="Midgaard#3021"
Scavenger min=10 max=20 chance=75 ; -JAVACLASS +GENWEAPON
```

### Scene

Hosts: rooms, mobs. CoffeeSUD. NPCs play out a scene for a player character to
overhear. The parameter is statements separated by `;`, or one per line in
`FILE=[PATH]`.

| Statement | Meaning |
| --- | --- |
| `NAME=[NAME]` | The scene's name (the saved variable `SCENE_[NAME]`). |
| `ACTOR [NAME]=[MASK OR NAME]` | A part, and how to find the NPC in the room: a zapper mask (starting with `+` or `-`), or words of its name; on an NPC host, `SELF` is the NPC itself. |
| `[NAME]: [COMMAND]` | A line: `say ...`, `emote ...`, or any command the NPC can do; `$n` is the character's name; `[[topic]]` in a line said teaches the topic (`AHELP NPCTOPICS`). One line a tick (a turn in turn-based time). |
| `PAUSE [N]` | N ticks of silence. |
| `ECHO [TEXT]` | Text for the room. |
| `DO [MOBPROG]` | Run a script line with `$n` the character (`MPSETVAR`, `MPQJOURNAL`, `MPCALLFUNC`...). |
| `IF=[CONDITION]` | A script condition (`$n` the character, `$i` the first actor). |
| `ONCE` / `REPEAT=[DAYS]` | Played once to each character (default), or again after N game days. |
| `DELAY=[N]` | Ticks between lines. |
| `INTERRUPTIBLE` | A character who speaks to an actor stops it and the actors turn to them; it plays to them again when they have left and come back. |
| `WHEN=`, `TOD=`, `HOURS=` | Hold it back (the gate above). |

It begins when a player character is in the room who it has not played to, all
its actors are there, awake and not fighting, and `IF` holds. Played to the end,
each player character there has heard it: the variable `SCENE_[NAME]` on them is
the game day. It stops, not heard, when the character or an actor goes, or an
actor fights. Where it is in a scene is saved. `STAT BEHAVIORS` shows it and any
statement it did not understand.

```text
Scene NAME=bribes;ACTOR guard1=tall guard;ACTOR guard2=-NAMES +"short guard";guard1: say Heard the bandits hit the mill again.;guard2: emote spits.;PAUSE 1;guard1: say The [[captain]] takes their coin, they say.;DO mpsetvar $n HEARD_BRIBES yes;TOD=NIGHT
```

### Schedule

Hosts: mobs. CoffeeSUD. A daily routine over several places. Entries are
separated by `;`; the first whose `WHEN` holds is the one in force.

```text
[WHEN]: [PLACE] ([ACTIVITY]...) (SAY [TEXT]/EMOTE [TEXT]); ...; (SHOPMSG="[TEXT]")
```

| Part | Meaning |
| --- | --- |
| `WHEN` | `DAWN`, `DAY`, `DUSK` or `NIGHT`, or hours (`2-5`, or `7`, on the area's clock), then any of `DAYS=[FROM]-[TO]` (days of the month), `SEASON=`, `WEATHER=` (`CLEAR`, `RAIN`, `SNOW`...) and `MASK="[ZAPPER MASK]"` (on the mob). |
| `PLACE` | A room ID (it runs to the first activity word, so an ID with spaces is fine), `HOME` or `START` (where the mob starts), or `ANY:"[ROOM MASK]"` (a room of its area that fits). |
| Activities | `SLEEP` or `SIT`, each with an optional item to do it on; `WANDER [N]`; `SHOP` (it trades only while here: elsewhere, or on the way, it says `SHOPMSG`); `WORK "[EMOTE]\|[EMOTE]"` (now and then); `LOCK` (closes and locks the place's doors while there, and unlocks them when the entry ends); `HIDE`. |
| `SAY` / `EMOTE` | Last; said on arriving. |

It walks there, holding [Mobile](#mobile) off on the way, and goes back to the
routine after a fight; woken, it goes back to sleep after a while. When no
player is in the area of either room, it is put there at once, so a restored
game, a time jump or an area nobody is in finds it where its routine says. With
no entry in force it stays where it is. `STAT($i SCHEDULE)` is the entry in
force, and `STAT BEHAVIORS` shows it.

```text
Schedule DAWN: Mill#2 WORK "pours grain into the hopper|wipes flour from his hands"; DAY: Market#5 SHOP; DUSK: Inn#1 SIT "stool" SAY Long day.; NIGHT: Mill#9 SLEEP "straw bed" LOCK
```

### Scriptable

Hosts: mobs, items, rooms (areas and exits use the other scripting hooks, see
[chapter 4](04-mobprog.md)). Runs a MOBPROG script on its host. The parameter is
the script: complete scripts and/or `LOAD=[PATH]` lines.

- With a file, each command line ends with a line feed or a `;`. If the whole
  script is in the parameters, every line ends with `;`.
- Several scripts with several triggers may be included; separate each script
  and trigger with `~`.
- The first line of each script is a trigger and its parameters (for example
  `GREET_PROG 100`); the rest is the body (`IF`, commands, `$` codes).
- `LOAD=progs/my.script~` loads another file (files under `resources/progs/`
  ship as examples).
- The `WHEN`, `TOD` and `HOURS` gate does not apply to it: its parameters are
  the script.

The scripting language is documented in four chapters: the guide
([chapter 4](04-mobprog.md)), the triggers
([chapter 26](26-mobprog-triggers.md)), the commands
([chapter 27](27-mobprog-commands.md)), and the functions and `$` codes
([chapter 28](28-mobprog-functions.md)). Script variables and trigger state are
saved with the game. ScriptableEverymob and the `Scriptable` of
[GenBehavior](#genbehavior) build on it.

```text
Scriptable LOAD=progs/mrinfo.script~
```

### ScriptableEverymob

Hosts: areas, rooms. Gives every mob native to the area or room a script
automatically; mobs that already have Scriptable behaviors are unaffected.
Parameters are those of [Scriptable](#scriptable).

```text
ScriptableEverymob LOAD=progs/mrinfo.script~
```

### Sentry

Hosts: mobs. CoffeeSUD. A lookout. It sees an intruder here or next door: a
player's character, or one following them, or whoever `INTRUDERS` fits. One
hidden or sneaking it spots at `SPOT` percent a tick, less their Hide or Sneak
proficiency, and half that next door. Then, or when it is attacked, it raises the
alarm: a shout of `MSG` (a NOISE message, which `EXECMSG_PROG` can catch), and
every NPC within `RADIUS` rooms that fits `MASK` wakes (a Sleeper stays up) and
is alert for `ALERTTICKS`: it heads for the alarm room, or for the intruder if
they are near, and attacks them or their party, while its Schedule or Patroller
waits. It raises the alarm at most once in `ALERTTICKS`.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `SPOT=[N]` | Percent chance per tick of spotting a hidden or sneaking intruder. | 25 |
| `INTRUDERS="[MASK]"` | Whom it counts as an intruder (besides players' characters). | players' side |
| `ALARM="RADIUS=[N] MASK=[MASK] MSG=[TEXT]"` | The alarm: how many rooms, which NPCs, what is shouted. | radius 6 |
| `ALERTTICKS=[N]` | How long NPCs are alert. | 40 |
| `ALARMITEM="[ITEM NAME]"` | An item in its room that anyone, a player too, may `PUSH` or `PULL` to raise the alarm. | none |

```text
Sentry SPOT=30 ALARM="RADIUS=6 MASK=-NAMES +bandit MSG=Intruders! To arms!" ALERTTICKS=40 ALARMITEM="a brass gong"
```

### Sleeper

Hosts: mobs. CoffeeSUD. A sleeping NPC that noise wakes.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `ALWAYS` or `SLEEPS=[DAWN/DAY/DUSK/NIGHT/FROM-TO]` | When it sleeps: a time of day or hours on the area's clock (do not use `TOD=` or `HOURS=`: they would switch the whole behavior off). | `ALWAYS` |
| `BED="[ITEM]"` | Sleeps on that item if it is there. | none |
| `WAKEON=[ATTACK,NOISE,ENTER,LIGHT,TOUCH]` | What may wake it. `TOUCH` is anything done to it but looking. | all |
| `WAKECHANCE=[N]` | How likely each is. A fight in the room makes it likelier, one next door less likely; someone entering sneaking or hidden takes off their Sneak or Hide proficiency; one carrying a light adds to it. | 50 |
| `DEEP` | Only an attack wakes it. | off |
| `GRUMBLE="[TEXT]"` | Said when woken. | none |
| `AWAKEFOR=[TICKS]` | How long it stays up before sleeping again. | 20 |
| `ALARM` | When woken it shouts an alarm that wakes the NPCs round about: its [Sentry](#sentry)'s `ALARM=` if it has one, else six rooms and "Intruders!". | off |

`LOOK` at it says it is sleeping soundly, or that it stirs when something nearly
woke it.

```text
Sleeper ALWAYS WAKEON=NOISE,ENTER WAKECHANCE=40 GRUMBLE="Who disturbs Grok?" ALARM
```

### Sounder

Hosts: rooms, mobs, items, areas, exits. Default timing min=max=23 ticks.
An alternative to [Emoter](#emoter): very simple emoting triggers based on a
percent chance each tick, or on event triggers such as getting an item or leaving
a room. The parameter is `;` separated. The first field may set the ticks
(`min=20 max=20`); each other field is `[CODE] [STRING]`. Strings may use `$p`
(the item with the behavior), `$n` (the source of the trigger), `$e` (he or she
of `$n`) and `$s` (his or her of `$n`).

| Code | Fires and emotes to |
| --- | --- |
| `SOUND [PCT]` | Every min/max ticks, this percent of the time. |
| `GET`, `DROP`, `PUSH`, `PULL`, `EAT`, `MOUNT`, `DRINK`, `SIT`, `SLEEP`, `WEAR`, `OPEN`, `CLOSE`, `HOLD`, `WIELD`, `REMOVE` | If an item is gotten, dropped etc. (or THE item, when on an item), to the actor. |
| `GET_ROOM`, `DROP_ROOM`, `PUSH_ROOM`, `PULL_ROOM`, `EAT_ROOM`, `MOUNT_ROOM`, `DRINK_ROOM`, `SIT_ROOM`, `SLEEP_ROOM`, `WEAR_ROOM`, `OPEN_ROOM`, `CLOSE_ROOM`, `HOLD_ROOM`, `WIELD_ROOM`, `REMOVE_ROOM` | The same, to the room (not the actor). |
| `PORTAL_ENTER`, `PORTAL_EXIT` | If the room is entered or left: to the one entering or leaving. |
| `PORTAL_ENTER_ROOM`, `PORTAL_EXIT_ROOM` | To the others in the room. |
| `DAMAGE`, `FIGHT` | If the object is hurt or attacking (or used to hurt or attack): to the one hurting or attacking. |
| `DAMAGE_ROOM`, `FIGHT_ROOM` | To the others in the room. |

CoffeeSUD bark table: entries `EVENT: [CHANCE=N] [COOLDOWN=N] [ONCEPERPLAYER] line
| line ...`, among the codes above or one to a line in a file named by
`FILE=[PATH]`. A line is `[WEIGHT] [{MASK}] [SAY|EMOTE|SOUND] text` (`"text` is
said, `:text` emoted, an emote by default; `$n` the one it is about). One line
is drawn by weight with the game's dice, of those whose mask fits `$n`.
`CHANCE=` is the percent chance it barks at all, `COOLDOWN=` the ticks before
that event barks again, `ONCEPERPLAYER` once for each character. Lines also
accept Emoter's conditional prefixes (`DAY`/`NIGHT`, seasons, weather,
`INDOORS`/`OUTDOORS`, `IFVAR name=value`), `MASK="..."`, `ONCE` and `WEIGHT=n`;
idle `ONCE` lines are selected for each reader.

| Bark event | Fires when |
| --- | --- |
| `GREET` | A character arrives. `GREET_FIRST`: the first time it sees them (`GREET` if it has none). |
| `SEE_MOB(MASK)` | Anyone arriving who fits the mask or has the words in their name. |
| `SEE_ITEM(MASK)` | A character arrives carrying such an item. |
| `KILL` | It kills `$n`. |
| `ALLY_DIES` | One of its group or race dies; `$n` is the killer. |
| `FLEE` | It flees. |
| `FIGHT` | It is attacked. |
| `HURT<N` | Below N% of its hit points, once until it heals. |
| `IDLE` | Each tick (give it a `CHANCE=`). |
| `DAWN`, `DAY`, `DUSK`, `NIGHT` | As each begins. |
| `RAIN`, `SNOW`, `CLEAR` | As each begins. |
| `SHOP_LIST`, `SHOP_BUY`, `BRIBED` | A shop is listed, a purchase, given money. |
| `WIELD(MASK)`, `WEAR`, `HOLD`, `GET`, `DROP` | An item is so handled, by anyone in the room. |

A saved game keeps whom it has greeted and its once-per-character barks.

```text
Sounder sound 80 wiggles his bottom.;sound 60 smiles evilly.;sound 20 burps!
Sounder min=20 max=20;wear $p looks good on you.;remove Now you don't look so good.;remove_room $n doesn't look so good.
Sounder GREET: COOLDOWN=20 2 SAY Move along. | 1 SAY Keep your blade sheathed.;WIELD(+WEAPONCLASS SWORD): SAY Put that away in the city!;NIGHT: SAY Curfew's at the bell.;HURT<30: SAY To me! Guards!
```

### StdBehavior

Hosts: mobs. The base class of every behavior; it does nothing (the standard
ID, empty `tick`, `executeMsg` and `okMessage`). Its built-in stats are `CLASS`
(the ID) and `TEXT` (the parameters), plus the internal `NAME`, `STATE` (run
state, see [How behaviors save](#how-behaviors-save)) and `DEBUG` (what `STAT
BEHAVIORS` shows). Engine developers extend it; see
[chapter 32](32-engine-programming.md).

### TargetPlayer

Hosts: mobs. Timing default 3/12/100. In combat the mob targets the weakest
player in its enemies' group.

```text
TargetPlayer min=2 max=3 chance=99
```

### TargetPriority

Hosts: mobs. CoffeeSUD. Chooses which of its foes the monster attacks (its foes
are those it or they fight, whom it can see).

| Parameter | Meaning | Default |
| --- | --- | --- |
| `PREFER=` a comma list | In order, what it goes for; the first that names someone here wins. See below. | |
| `SWITCH=[N]` | Percent chance each tick that it looks again. | 30 |
| `STICKY=[N]` | Ticks after a switch before it may switch again. | 3 |
| `TAUNTABLE` | One with a clear lead in its threat (half as much again as the next: damage, healing, `MPTHREAT`, a taunt) comes before the list. | off |

`PREFER` values: `HEALER` (the most healing abilities), `CASTER` (the most
spells, prayers, chants or songs), `LOWESTHP` (the least hit points left),
`LOWESTARMOR` (the worst protected), `LEADER` (the party's leader), `LASTHITME`
(whoever hit it last), `STRONGEST` (the most powerful), `MASK:[ZAPPER MASK]` (the
first one the mask fits; last in the list, and the list in quotes when the mask
has spaces).

```text
TargetPriority PREFER=CASTER,LOWESTHP SWITCH=50
TargetPriority PREFER=LASTHITME STICKY=4
TargetPriority PREFER="LOWESTHP,MASK:-CLASS +Cleric" TAUNTABLE
```

### TaxCollector

Hosts: mobs. A very annoying and somewhat dangerous collector of money from
players and mobs. By default it demands 10% of any money held by a random person
it meets as a "citizens tax". The person has `WAIT` to give it over before it
charges them with tax evasion or attacks. If paid (mobs always pay) they have
`GRACE` before money is demanded again. It never demands from a member of its own
clan. In areas with law it charges the Citizens Tax rate instead of 10%, and any
back property taxes.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `WAIT=[TIME]` | Milliseconds to pay. | 120000 (2 minutes) |
| `GRACE=[TIME]` | Milliseconds before it asks again. | 3600000 (1 hour) |

```text
TaxCollector WAIT=60000 GRACE=600000
```

### TaxiBehavior

Hosts: mobs, items (rideables). Extends [Concierge](#concierge): the host
carries riders to places for a price. It takes the same parameter syntax as
Concierge (`[price]/[place]=[price]/[setting]=[value]` separated by `;`), with
its own defaults: a base price of 10 and 1 per room, and trails limited to open,
outdoor routes. `GREETING`, `ENTERMSG` (told to a rider after they mount) and
`TALKERNAME` (default "the driver" on an item) apply; `AREAONLY`, `MAXRANGE`,
`PERROOM`, `NOWATER`, `NOAIR`, `NOLOCKS`, `NOHOMES`, `NOCLIMB` and `NOCRAWL` as
for Concierge. The help also lists `INDOOROK=true` to allow indoor rooms (see the
audit notes: the parser maps it to the same flag as `NOINDOOR`).

```text
TaxiBehavior 1.5
TaxiBehavior Midgaard#3001=1.5; Smurfville=2.0; 70 ; AREAONLY=true
TaxiBehavior Midgaard#3032; 100 ; INDOOROK=false; MAXRANGE=100
```

### Thiefness

Hosts: mobs. A [CombatAbilities](#combatabilities) preset: the mob becomes a Thief
by class with thief skills suited to its level, including non-combat abilities:
sneaking and hiding, backstabbing, picking pockets, and stealing at high levels.
Parameters as in [Fighterness](#fighterness).

```text
Thiefness Assassin MIXEDOFFENSIVE
```

### TidalRoom

Hosts: rooms (and areas). CoffeeSUD, built from [RoomStates](#roomstates). A
shore room the tide floods. At high tide (`SPAN` hours either side) it is a
swimming room with its own title and description, and `FLOODMSG` for those who
come in; with `DEEP` its air is water at the spring tides; `WASH` washes away
the loose things there that weigh up to that much as the water comes. At the ebb
it is itself again. `ROOMSTATE()` says `ebb`, `flooded` or `deep`.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `SPAN=[HOURS]` | Hours either side of high tide it is flooded. | 0 |
| `TITLE=`, `DESC=` | The room's title and description while flooded. | the room's own |
| `FLOODMSG=` | Told to those who come in while flooded. | `The tide is in: the sea swirls about you.` |
| `DEEP` | Its air is water at the spring tides. | off |
| `WASH=[WEIGHT]` | Washes away loose things up to that weight. | 0 |

```text
TidalRoom SPAN=1 DESC="The sea fills the cove." WASH=2 DEEP
```

### TransportLine

Hosts: items, mobs. CoffeeSUD (no shipped help entry). A scheduled transport: the
host (an item vehicle, or a mob, or the thing a mob rides) travels between stops
on a route, with a fare and a timetable. It adds the properties
`Prop_TicketTaker` (the fare) and, for a navigable item, `Prop_HelmRestrict`
(only passengers, no players at the helm) while it runs. Position, direction and
timing are saved.

| Parameter | Meaning | Default |
| --- | --- | --- |
| `STOPS="[ROOM ID];[ROOM ID];..."` | The stops (at least two). | required |
| `ROUND=LOOP/BACK` | Loop round to the first, or go back and forth. | `LOOP` |
| `WAIT=[ticks]` | Ticks at each stop. | 10 |
| `DEPART=[hours]` | Comma list of game hours to depart; the next one overrides `WAIT`. | none |
| `FARE=[price]` | Fare. | 0 |
| `FARES="[STOP ID]:[price],..."` | A different fare when leaving that stop. | none |
| `PARTY=LEADER` etc. | Passed to `Prop_TicketTaker` (who pays for the group). | `LEADER` |
| `PETS`, `CARGO` | Passed to `Prop_TicketTaker`. | 0 |
| `PASSENGERONLY=true/false` | Install `Prop_HelmRestrict NAVMASK="-PLAYERS"`. | true |
| `BOARDCALL`, `DEPARTMSG`, `ARRIVEMSG` | Announcements; `$stop` and `$next` are replaced by the stop and next stop names. | none |
| `SIGN="[item name]"` | An item at each stop whose readable text becomes the timetable. | none |

`TIMETABLE` shows the line. See [chapter 30](30-travel-vehicles-instances.md).

```text
TransportLine STOPS="Gate#1;Pier#4;Fort#2" WAIT=100 ROUND=BACK FARE=2 PARTY=LEADER PASSENGERONLY=true
```

### Vagrant

Hosts: mobs. No parameters. The mob spontaneously goes to sleep wherever it is,
and wakes after a while.

### VeryAggressive

Hosts: mobs. Extends [Aggressive](#aggressive). Attacks any player it detects in
its room as soon as it enters, and also detects players in adjacent rooms and
moves to attack them, but only when they meet all the criteria and are within 16
levels. With no other parameters it attacks anyone within 8 levels. Ignores
archons and builders with `CMDMOBS`. Parameters: `message=`, `delay=`,
`mobkill`, `nogang`, `misbehave`, `BRIBE=`, `BRIBEPAYMSG=`, `BRIBEPAIDMSG=` and
the mask, as for Aggressive.

```text
VeryAggressive -race +elf message="die! You!"
VeryAggressive mobkill misbehave -race +elf
```

### WaterCurrents

Hosts: areas, rooms. Timing default 3/5/75. The mobs in the room are drawn along
in the directions given (and, with `BOATS`, boats). If an exit is available in
the first direction it is chosen, else the second, and so on.

| Parameter | Meaning |
| --- | --- |
| `GO=[DIRECTION] [DIRECTION] ...` | The directions, in preference order. Each direction word as `east=true` also works. Default `north` then `east`. |
| `INMSG="..."` | Message to the room it enters (`<S-NAME>`, `<S-IS-ARE>`, `@x1` the direction it comes from). |
| `OUTMSG="..."` | Message to the room it leaves (`@x1` the direction). |
| `BOATS` | (not in the help) also moves boats. |

```text
WaterCurrents min=1 max=3 chance=99 go=east south
WaterCurrents east=true south=true inmsg="<S-NAME> enter(s) from @x1."
```

### WaveSpawner

Hosts: rooms. CoffeeSUD. An arena of waves.

| Parameter | Meaning |
| --- | --- |
| `WAVE1="[WAVE]"`, `WAVE2=`... | Each wave is `name*dice` joined with `+` (from the catalog, `catalog:` being optional, or a mob class), or `encounter:[TABLE]`, a roll of an encounter table (`AHELP ENCOUNTER`). |
| `NEXT=CLEAR` / `NEXT=TICKS=[N]` | The next wave comes when the last is dead (default), or after N ticks. |
| `START=ENTER` (default) / `START=CALL` | Starts when the party walks in, or when a script runs `MPWAVE START` (`MPWAVE` also takes `NEXT`, `STOP` and `RESET`). |
| `LOCKEXITS=true` | Keeps the party in until it is done. |
| `ONWIN=[FUNCTION]/UNLOCK` | When the last wave is dead: the `FUNCTION_PROG` of that name in the room's scripts runs (`$n` the party's leader), or `UNLOCK` (the default) only frees the exits. |
| `REWARD="[ITEM]"` | The winner gets this item. |
| `ONLOSE=RESET/SPARRING(:[ROOM ID])` (default `RESET`) | If the party leaves or falls, what is left of the wave leaves; `SPARRING` puts `Prop_SparringRoom` on the room while it runs, so a defeat is not death (the room ID is where the loser wakes). |
| `RESETAFTER=[N]` | Ticks before it can start again after a win (default 0). |

`WAVE_PROG [N]`, `ARENAWIN_PROG` and `ARENALOSE_PROG` run on the room's scripts
and its NPCs' (an arena master's). The wave is saved. See
[chapter 29](29-combat-and-encounters.md).

```text
WaveSpawner WAVE1="catalog:goblin*3" WAVE2="goblin*2+goblin archer*2" WAVE3="encounter:goblin_boss" LOCKEXITS=true REWARD="a laurel" ONLOSE=SPARRING:Arena#2
```

### WeatherAffects

Hosts: rooms, areas. Handles most of the effects of weather on players and the
environment; change the parameters to tune, tweak or switch off effects.
Defaults from the code:

| Parameter (code key) | Meaning | Default |
| --- | --- | --- |
| `windsheer` | Percent chance that a ranged attack or thrown item is blown off course. | 10 |
| `puddlepct` | Percent chance, after wet weather, that puddles, snow piles or dust form. | 50 |
| `rainslipchance` | Percent chance during wet weather that someone slips while moving. | 1 |
| `snowSlipChance` | Same, during snow. | 5 |
| `sleetSlipChance` | Same, during sleet. | 10 |
| `iceoverchance` | Percent chance, in cold weather after cold weather, that an available water surface freezes over for a time. (The help names this `freezeOverChance`; the code reads `iceoverchance`.) | 50 |
| `droughtFireChance` | Percent chance, during a drought, that a random item in the available rooms ignites. | 1 |
| `boatslipchance` | (not in the help) the chance for boats. | 20 |
| `botherticks` | Ticks between reminders of current weather. | 50 (one third of 150) |
| `diseaseticks` | Ticks between small chances of a weather-related disease (cold, flu, frostbite, heat). | 300 |
| `rustticks` | Ticks between chances that a wet player's item rusts and is damaged. | 30 |
| `lightningticks` | Ticks between chances, in thunderstorms, that someone in a room is struck by lightning. | 1200 |
| `rumbleticks` | Ticks between more personal weather reminders. | 37 |
| `gustticks` | Ticks between chances, in windy weather, of a gust buffeting someone. | 300 |
| `tornadoticks` | Ticks between chances, in thunderstorm and wind, of a tornado touching down. | 2250 |
| `hailticks` | Ticks between chances, in hail, of being buffeted. | 150 |
| `dustticks` | Ticks between chances, in dust storms, of dirt in the eye. | 50 |
| `weather=[name]` | Forces that weather's effects, even indoors. Names: `CLEAR`, `CLOUDY`, `WINDY`, `RAIN`, `THUNDERSTORM`, `SNOW`, `HAIL`, `HEAT`, `SLEET`, `BLIZZARD`, `DUST`, `DROUGHT`, `COLD`, `FOG`. | none |
| `skyweather=[name]` | The same, outdoors only. | none |
| `shipdmgpct=[formula]` | Percent chance (0 to 100) that a ship or boat passing through takes damage. Formula variables: `@x1` rooms in the ship (boats are 0), `@x2` wind level (0, 1, 2), `@x3` wet level (0, 1 rain, 2 storm, 3 storm and wet). | none |
| `dmgship=[formula]` | Amount of damage to the ship (the help calls this `shipdmg`; the code reads `dmgship`). It must be given with `shipdmgpct` or neither takes effect. | none |
| `dmgname="[NAME]"` | The source name given for ship damage. | none |

(The tick defaults are multiples of `Climate.WEATHER_TICK_DOWN`, 150 ticks.)

```text
WeatherAffects windsheer=10 puddlepct=20
```

### Wimpy

Hosts: mobs. The mob flees any player it detects in the same room; with no
parameters it flees anyone. Mask: allowed is fled from, disallowed is not.

| Parameter | Meaning |
| --- | --- |
| `DELAY=x` | Ticks to wait before looking for someone to fear. |
| `VERY=1` | Flee even if not in combat. |

```text
Wimpy -race +elf
```

### WimpyAggressive

Hosts: mobs. Extends [Aggressive](#aggressive). Attacks any sleeping player it
detects in the room. Ignores archons and builders with `CMDMOBS`. With no other
parameters it attacks anyone. Parameters: `message=`, `delay=`, `mobkill`,
`misbehave`, `BRIBE=`, `BRIBEPAYMSG=`, `BRIBEPAIDMSG=` and the mask, as for
Aggressive.

```text
WimpyAggressive -race +elf misbehave mobkill
```

# Part 2: Zapper masks

## Zapper masks

A zapper mask is a short text expression that answers one question about one
thing: does this mob, item, room or area qualify? Masks are the one shared
filtering language of the engine. They appear in behavior parameters
(`Aggressive -RACE +Elf`), property parameters (`Prop_ReqEntry`, the
`SOURCEMASK=`/`TARGETMASK=` of the damage properties), spawn rules, catalog and
shop rules, clan and deity requirements, class and skill qualifications,
quests, script conditions (`ISLIKE`, `=S MASK=`) and JavaScript
(`CMLib.masking().maskCheck(mask, host, true)`). The parser and evaluator are
in
[MUDZapper](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/MUDZapper.java);
the key list is the `ZapperKey` enum in
[MaskingLibrary](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/interfaces/MaskingLibrary.java);
the in-game help text is
[zappermasks.txt](../../CoffeeMud/resources/help/zappermasks.txt) (shown by
`AHELP ZAPPERMASKS`, where `<WORD>` reads "disallow").

### Mask syntax

A mask is a list of space-separated tokens. The text is compiled in upper case,
so case never matters (the one exception: the expression of `JS` keeps its
case). A mask has two kinds of token.

**Keys** start with `-` or `+` and name a test: `-RACE`, `+LEVEL`, `-NAMES`,
`+TATTOO`. The sign of a key sets the default direction of the test:

| Form | Reading |
| --- | --- |
| `-KEY value value...` | Disallow everything, except those who fit one of the values. In short: require. |
| `+KEY value value...` | Allow everything, except those who fit one of the values. In short: forbid. |

The values after a key carry the opposite sign: `-RACE +Elf +Dwarf` (only elves
and dwarves), `+RACE -Elf -Dwarf` (anyone but elves and dwarves). A value list
runs until the next key; any word in it that lacks the expected sign is silently
skipped. (The lists of `CLASS`, `BASECLASS`, `RACE`, `RACECAT`, `ALIGNMENT`,
`GENDER`, `WEAPONTYPE` and `WEAPONCLASS` instead end at the first word that is not
one of their names, and that word is then read as the next token of the mask.) Many keys also have plural spellings (`-RACES`, `-NAMES`).

Exceptions to the "minus requires" rule are flagged in the tables below: a few
tests with no values (`+ISHOME`, `+ROOMDARK`, `+OFFICER`, `+JUDGE`,
`+AREAINSTANCE`, `+WANTED`, `+HOLIDAY`, `+BREATH`) require when written with
`+` and forbid with `-`, and the numeric keys differ in which side of the number
they pass.

**Bare words** are shorthand for a single "forbid" test and need no key. A `-`
word naming a character class, a race, an alignment (`-Good`, `-Neutral`,
`-Evil`), a gender (`-Male`, `-Female`, `-Neuter`), a faction range code, or a
level test (`->5`, `->=7`, `-<13`, `-<=20`, `-=3`) disallows that class, race
and so on. `Aggressive -Elf -Mage` spares elves and mages. Bare words must be
written with `-`; a bare `+Elf` is not understood (it logs "Bad Zappermask
key"), so write `-RACE +Elf`. Names of classes, races, alignments and genders are
matched by their start (`-Mag` is `-Mage` if no earlier name begins with `Mag`).

**Quoting.** A value with spaces is wrapped in double quotes, sign inside:
`-NAMES "+Short Sword" +Bob`. A backslash escapes the next character. Where a
mask is itself a quoted parameter of a behavior or property, the inner quotes are
written `\"`, or with single quotes in the places that convert them
(`Prop_HelmRestrict`, `Prop_StockZapper`, `DelayedTransporter`, `GuildMaster`,
`ResetWhole`, and the rules of `Travel` and `Waypoints`): `EXCEPT="-NAME
+'the Goblin King'"`.

**Where a mask ends.** Parsing stops at the first word that is not a key, not a
bare word, and does not start with `+` or `-` (it is where the mask's text
ends). A behavior that puts a mask last in its parameters (`Follower`,
`Aggressive`...) hands the compiler everything from the first `+` or `-` word to
the end. Anything after a list value is swallowed by that list.

**Combining.** Tests are joined by AND, evaluated left to right. `+OR` and `-OR`
join groups: `A +OR B` passes if A or B passes; `A -OR B` passes if A passes or B
fails ("`-OR` negates the following condition(s)").

**Nesting.** `-RIDE`, `+RIDE`, `-FOLLOW`, `+FOLLOW`, `+ANYINGROUP`,
`-ANYINGROUP`, `+ALLINGROUP` and `-ALLINGROUP` apply the *rest of the mask* to
something else (the mount, the leader, the group here) and end the evaluation
with that answer.

### How a mask is evaluated

`maskCheck(mask, host, actual)` takes the mask text (or a pre-compiled mask), the
host (a mob, item, room, area or exit) and an `actual` flag. `actual=true` reads
base values (a mob's real class and race, and real name); `false` reads current
values (what a disguise or polymorph shows). Behaviors and properties that use a
mask call it with `true` unless they say otherwise.

- An empty mask, or a null host, always passes.
- Compiled masks are cached by lower-cased text (200 entries, ten minutes).
- Each token of the AND group is a gate: it either fails the whole mask at
  once or lets evaluation go on; reaching the end means the mask passes.
- A few gates succeed at once and skip the rest: `+PLAYER`, `+MOB` (`+NPC`),
  `+SYSOP`, `+SUBOP`, `+JS` and `+IF` return true as soon as they match. Put
  them first and deliberately.
- A key whose subject is a mob, tested on an item, room or area host, is tested
  on a blank stand-in mob; a key about items is tested on the host itself when
  it is an item, and otherwise on the first item of the mob or a blank item.
  Area hosts read an outdoor room of the area for room keys.
- Keys about places (`AREA`, `DOMAIN`, `WEATHER`, `SEASON`, `HOUR`...) use the
  host's room; time keys use the room's area clock.

### Mask key reference

Tables list every key. "Pass" is what lets the thing through. Unless the row
says otherwise, `-KEY` requires a listed value and `+KEY` forbids one. Aliases
in parentheses work the same way.

#### Who: type, name and identity

| Key | Values | Meaning |
| --- | --- | --- |
| `-PLAYER` (`-PLAYERS`) | none | Fails for a player; passes NPCs. |
| `+PLAYER` (`+PLAYERS`) | none | Passes at once for a player (the rest of the mask is skipped); no effect on NPCs. |
| `-NPC` (`-MOB`, `-MOBS`) | none | Fails for an NPC; passes players. |
| `+NPC` (`+MOB`, `+MOBS`) | none | Passes at once for an NPC (rest skipped); no effect on players. |
| `-CHANCE` | `n` | Passes `100-n` percent of the time (fails when a d100 roll is under `n`). No `+CHANCE`. |
| `-NAMES` (`-NAME`) | `+name` ... | Requires the host's name to equal one of them exactly (case-insensitive). Quote names with spaces. Uses the real name when `actual`. |
| `+NAMES` (`+NAME`) | `-name` ... | Forbids those names. |
| `-SUBNAMES` | `+text` ... | Requires the name to contain the text. `*` wildcards: `+*ending`, `+start*`, `+*inside*`. |
| `+SUBNAMES` | `-text` ... | Forbids those partial names. |
| `-ACCOUNTS` (`-ACCOUNT`) | `+name` ... | Requires the player's account name (CoffeeSUD: the profile name) to equal one of them; for a non-player, the host name. |
| `+ACCOUNTS` | `-name` ... | Forbids those accounts. |
| `-TATTOOS` (`-TATTOO`) | `+tattoo` ... | Requires one of the tattoos on the mob, or an area blurb flag of the room's area of that name. |
| `+TATTOOS` | `-tattoo` ... | Forbids those tattoos. |
| `-TAG` (`-TAGS`) | `+tag` ... | Requires the host (an item's own tags, else the mob's) to carry a tag (see `MPTAG`). |
| `+TAG` | `-tag` ... | Forbids those tags. |
| `-RANK` (`-RANKS`) | `+rank` ... | Requires a mob rank from `Prop_MobRank`: `MINION`, `NORMAL` (none), `ELITE`, `BOSS` or one the world adds. |
| `+RANK` | `-rank` ... | Forbids those ranks. |
| `-JAVACLASS` | `+ID` or `+.Name` | Requires the host's class ID to equal `ID` (`+GENMOB`), or, with a leading dot, its class or any ancestor class or interface to end with `.Name` (`+.Drink`). |
| `+JAVACLASS` | `-ID` | Forbids those. |
| `-CLASSTYPE` | `+JavaType` ... | Requires the host to be an instance of the named Java type (class or interface name, searched in the engine packages). |
| `+CLASSTYPE` | `-JavaType` ... | Forbids those. |
| `-EFFECT` (`-EFFECTS`) | `+name` ... | Requires an effect (an ability ID or name) or a behavior of that ID or name on the host. |
| `+EFFECT` | `-name` ... | Forbids those. |

#### Who: class, race, alignment, gender

| Key | Values | Meaning |
| --- | --- | --- |
| `-CLASS` (`-CLASSES`) | `+class` ... | Requires the current class. Class names or IDs; prefix-matched. With `actual`, the real class; otherwise the displayed one. |
| `+CLASS` | `-class` ... | Forbids those classes. |
| `-BASECLASS` (`-BASECLASSES`) | `+base` ... | Requires the base class (`Fighter`, `Mage`...). |
| `+BASECLASS` | `-base` ... | Forbids those. |
| `-ANYCLASS` (`-ANYCLASSES`) | `+class` ... | Requires levels in one of these classes (multi-classing). |
| `+ANYCLASS` | `-class` ... | Forbids having levels in any of them. |
| `-RACE` (`-RACES`) | `+race` ... | Requires the race (name or ID, prefix-matched). |
| `+RACE` | `-race` ... | Forbids those races. |
| `-RACECAT` (`-RACECATS`) | `+category` ... | Requires the racial category (`humanoid`, `undead`, `insect`...). |
| `+RACECAT` | `-category` ... | Forbids those categories. |
| `-ALIGNMENT` (`-ALIGNMENTS`, `-ALIGN`) | `+good` `+neutral` `+evil` | Requires the alignment (also matches inclination names). |
| `+ALIGNMENT` | `-good`... | Forbids those. |
| `-GENDER` (`-GENDERS`) | `+male` `+female` `+neuter` | Requires the gender. |
| `+GENDER` | `-male`... | Forbids those. |
| `-FACTION` | `+faction.rangecode` ... | Requires the mob to stand in the given faction range (a range code such as `GOOD`, or `faction.range`). |
| `+FACTION` | `-rangecode` ... | Forbids those ranges (a bare `-rangecode` is the same). |
| `-DEITY` | `+Name` ... or `+ANY` | Requires worship of the named deity (`+ANY`: any deity). |
| `+DEITY` | `-Name` ... | Forbids worshippers of them. |
| `-MOOD` (`-MOODS`) | `+mood` ... | Requires the mood from the `Mood` effect (`NORMAL` when none). |
| `+MOOD` | `-mood` ... | Forbids those. |

#### Who: levels and stats

Level ranges are tokens `=N`, `>N`, `>=N`, `<N` and `<=N`, signed opposite to
the key: `-LEVEL +>5 +=2` (levels above 5 or exactly 2), `+LEVEL ->10` (nothing
above 10).

| Key | Values | Meaning |
| --- | --- | --- |
| `-LEVEL` (`-LEVELS`) | `+range` ... | Requires the host's level (base level when `actual`) to fit one range. Works on mobs, items and anything with a level. |
| `+LEVEL` | `-range` ... | Forbids levels that fit any range. |
| `-CLASSLEVEL` / `+CLASSLEVEL` | `+range` / `-range` | The same, for the level in the current class. |
| `-MAXCLASSLEVEL` / `+MAXCLASSLEVEL` | `+range` / `-range` | The same, for the highest class level held. |
| `-ANYCLASSLEVEL` | `+class +range ...` | Requires, for one of the classes named, a level in range (a class, then its ranges, then the next class). |
| `+ANYCLASSLEVEL` | `-class -range ...` | Forbids that. |
| bare `->N`, `->=N`, `-<N`, `-<=N`, `-=N` | | Forbids levels above N, at or above N, below N, at or below N, or equal to N. |
| `-QUALLVL` | `Ability [adjustment]` | Requires the mob's class level to be no more than the level at which it qualifies for the ability, plus the adjustment (still before qualifying). |
| `+QUALLVL` | `Ability [adjustment]` | Requires the mob to have reached the level it qualifies for the ability plus the adjustment. |
| `-STR` `-INT` `-WIS` `-DEX` `-CON` `-CHA` (`-STRENGTH`...) | `n` | Requires the base stat to be at most `n`. |
| `+STR` ... | `n` | Requires the base stat to be at least `n`. |
| `-ADJSTR` `-ADJINT` `-ADJWIS` `-ADJDEX` `-ADJCON` `-ADJCHA` (`-ADJSTRENGTH`...) | `n` | As above, but the current (adjusted) stat: at most `n`. |
| `+ADJSTR` ... | `n` | The current stat: at least `n`. |
| `-GROUPSIZE` / `+GROUPSIZE` | `n` | Requires a group of at most / at least `n` members (the group of the mob; for an area, its population). |
| `-VALUE` / `+VALUE` | `n` | Requires value or money at least / at most `n` (for a mob, all its money; for an item, its base gold value). |
| `-WEIGHT` / `+WEIGHT` | `n` | Weight at least / at most `n`. |
| `-ARMOR` / `+ARMOR` | `n` | Armor adjustment at least / at most `n`. |
| `-DAMAGE` / `+DAMAGE` | `n` | Damage adjustment at least / at most `n`. |
| `-ATTACK` / `+ATTACK` | `n` | Attack adjustment at least / at most `n`. |
| `-ABILITY` (`-ABLE`, `-ABILITIES`) / `+ABILITY` | `n` | Magical ability (item "plus") at least / at most `n`. |

#### Who: skills, standing and organisations

| Key | Values | Meaning |
| --- | --- | --- |
| `-SKILLS` (`-SKILL`) | `+Ability_ID` or `+Ability_ID(75)` ... | Requires the mob to know one of the abilities, at proficiency at least the number in brackets (default 0). On a wand or staff, requires one of the spells in its spell list. |
| `+SKILLS` | `-Ability_ID(n)` | Forbids. |
| `-SKILLFLAG` (`-SKILLFLAGS`) | `+flag` ... | Requires the mob to have a skill of an ability type (`SPELL`, `PRAYER`, `SONG`...), a domain (`ENCHANTMENT`...), a flag or a quality. Combine with `&` and `!` (`+ENCHANTMENT&!MALICIOUS`). |
| `+SKILLFLAG` | `-flag` ... | Forbids. |
| `-EXPERTISES` (`-EXPERTISE`) | `+id` ... | Requires one of the expertises. |
| `+EXPERTISES` | `-id` ... | Forbids. |
| `-CLAN` (`-CLANS`) | `+Clan` / `+Clan(ROLE)` / `+Clan(FUNCTION)` / `+*` | Requires membership; with a role or function in brackets, requires that position (a clan position name) or a clan function the position can do. `+*` is any clan. On a clan item, the item's clan. |
| `+CLAN` | `-Clan` / `-Clan(ROLE)` | Forbids. |
| `-CLANLEVEL` (`-CLANLEVELS`) | `+clan +range ...` | Requires the clan's own level in range; `+*` is any clan the mob is in, `+1` its first clan. |
| `+CLANLEVEL` | `-clan -range ...` | Forbids that. |
| `-CLANRANK` / `+CLANRANK` | `"+Clan Name>=RANK"` | CoffeeSUD: requires / forbids a rank condition in a clan or organisation: a name, an operator (`>=`, `<=`, `==`, `!=`, `>`, `<`) and a rank name (quote the value; see `Organizations.rankMask`). |
| `-RULER` / `+RULER` | `+Clan` or `+*` | CoffeeSUD: requires / forbids the host being in territory ruled by that clan (`*`: by any ruler). |
| `-SECURITY` (`-SEC`, `-SECURITIES`) | `+flag` ... | Requires one of the security flags (`CMDROOMS`, `AREA`, `CMDITEMS`...; spaces as underscores) for the mob in the room. |
| `+SECURITY` | `-flag` ... | Forbids. |
| `-ACCCHIEVES` (`-ACCCHIEVE`) | `+id` ... | Requires an achievement earned by the account (the profile). |
| `+ACCCHIEVES` | `-id` ... | Forbids. |
| `-QUESTWIN` | `+quest` ... | Requires having won one of the quests. |
| `+QUESTWIN` | `-quest` ... | Forbids having won. |
| `-QUESTFAIL` / `+QUESTFAIL` | `+quest` / `-quest` | CoffeeSUD: requires / forbids having failed the quest. |
| `-QUESTOUTCOME` / `+QUESTOUTCOME` | `+quest:word` / `-quest:word` | CoffeeSUD: the outcome word that `MPQUESTWIN` gave, or the failure reason. |
| `-QUESTWINS` / `+QUESTWINS` | `+quest:>=10` / `-quest:>=10` | CoffeeSUD: the number of times the quest was won, with a comparison (`=`, `>`, `>=`, `<`, `<=`). |
| `-WANTED` | `[crime ...]` | Forbids (fails for) one wanted by the law where they stand, or for one of the crimes named. **`+WANTED` requires it.** |
| `-OFFICER` / `+OFFICER` | none | Forbids / requires being an officer of the law of the mob's start area (`+OFFICER` also fails for players). |
| `-JUDGE` / `+JUDGE` | none | Forbids / requires being a judge. |
| `-BREATH` | `<3` etc. | Forbids those whose breath left (in ticks) compares so: `<`, `<=`, `>`, `>=`, `=`, `!=`. **`+BREATH` requires it.** |
| `-SYSOP` (`-SYSOPS`) | none | Fails for archons (sysops). |
| `+SYSOP` | none | Passes at once for an archon, skipping the rest. |
| `-SUBOP` / `+SUBOP` | none | The same for an area staff member (a sub-op of the area) or an archon. |

#### Where: area, room and environment

| Key | Values | Meaning |
| --- | --- | --- |
| `-AREA` (`-AREAS`) | `+name` ... | Requires the host's room to be in the area. Names match the area's name exactly; a leading `*` matches the end of the name (`+*Forest`). |
| `+AREA` | `-name` ... | Forbids those areas. |
| `-PARENTAREA` / `+PARENTAREA` | `+name` / `-name` | The same, but an area also counts if a parent area has that name. |
| `-AREAINSTANCE` | none | Forbids being in an area instance. **`+AREAINSTANCE` requires it.** |
| `-AREABLURB` / `+AREABLURB` | `+blurb` or `"+blurb value"`; `+*end`, `+start*` | CoffeeSUD: requires / forbids the area's blurb flag with that name (and value, if a value is given). |
| `-HOME` / `+HOME` | `+area` / `-area` | Requires / forbids that the mob's home (start or beacon) area, or a plane of that name, is the one named; `*` suffix allowed. |
| `-ISHOME` | none | Forbids being in the home area (fails when at home). **`+ISHOME` requires being at home.** |
| `-LOCATION` | `+OWNED` `+CLANOWNED` `+PRIV` `+ROOMID(text)` `+ROOMSTR(text)` | Requires one of: the room owned by the mob (for a non-mob: owned by anyone); owned by a clan the mob belongs to; the mob has privileges there; the room ID starts with the text; the room's title or description contains the text. |
| `+LOCATION` | `-OWNED` ... | Forbids those. |
| `-DOMAIN` / `+DOMAIN` | `+CITY` `+WOODS` ... | Requires / forbids the room's locale type: a locale name (the outdoor and indoor names under [Locale restriction words](#locale-restriction-words)) or a number. |
| `-PLANE` / `+PLANE` | `+name` | Requires / forbids being on the named plane (`Prime Material` when none). |
| `-PORT` / `+PORT` | `+n` | Inherited: tests the number of the game's server port. There is no networking in CoffeeSUD, so this is always the same value; do not use. |
| `-ROOMDARK` | none | Forbids being in a room dark to the mob (dark, and it cannot see in the dark). **`+ROOMDARK` requires it.** |
| `-WEATHER` / `+WEATHER` | `+RAIN` ... | Requires / forbids the area's current weather: `CLEAR`, `CLOUDY`, `WINDY`, `RAIN`, `THUNDERSTORM`, `SNOW`, `HAIL`, `HEAT`, `SLEET`, `BLIZZARD`, `DUST`, `DROUGHT`, `COLD`, `FOG` (or the number). |
| `-TIDE` / `+TIDE` | `+HIGH` ... | CoffeeSUD: requires / forbids the tide: `HIGH`, `LOW`, `NONE`, or a phase such as `SPRING_HIGH`, `NEAP_LOW`; add `:n` for a span of n hours either side of the turn (`+HIGH:2`). |
| `-HOLIDAY` | `[name ...]` | Forbids it while a holiday (any, or one of those named) is on. **`+HOLIDAY` requires one.** |

#### When: the clock

All time keys read the host's area clock. Their values are numbers, signed
opposite to the key: `-HOUR +20 +21 +22`. A value with a space must be quoted
and takes one of two extra forms: `"+3 of 5"` (the value modulo 5 equals 3) and
`"+3rd 5"` (the 3rd block of 5).

| Key | Values | Meaning |
| --- | --- | --- |
| `-HOUR` (`-HOURS`) / `+HOUR` | `+n` / `-n` | Hour of the day. |
| `-DAY` / `+DAY` | | Day of the month. |
| `-DAYOFYEAR` / `+DAYOFYEAR` | | Day of the year. |
| `-WEEK` (`-WEEKS`) / `+WEEK` | | Week of the month. |
| `-WEEKOFYEAR` (`-WEEKSOFYEAR`) / `+WEEKOFYEAR` | | Week of the year. |
| `-MONTH` (`-MONTHS`) / `+MONTH` | | Month number. |
| `-YEAR` (`-YEARS`) / `+YEAR` | | Year. |
| `-SEASON` (`-SEASONS`) / `+SEASON` | `+SPRING` ... | `SPRING`, `SUMMER`, `FALL`, `WINTER` (or a number, or a season name of the area's own calendar). |
| `-BIRTHDAY`, `-BIRTHDAYOFYEAR`, `-BIRTHWEEK`, `-BIRTHWEEKOFYEAR`, `-BIRTHMONTH`, `-BIRTHYEAR`, `-BIRTHSEASON` (and `+` forms) | as the above | The same, on the date the mob was born (the age calendar), not now. |

Times of day and `-HOUR`: `-HOUR +20 +21 +22 +23 +0 +1 +2 +3` is night hours.

#### What: items and equipment

Item keys test the host if it is an item, or, for a mob host, the things it
carries or wields as stated.

| Key | Values | Meaning |
| --- | --- | --- |
| `-ITEM` | `+name` ... | Requires the mob to have an item of that name. |
| `+ITEM` | `-name` ... | Forbids having it. |
| `-WORN` | `+name` ... | Requires the mob to be wearing (not just carrying) that item. For an item host: that it is being worn. |
| `+WORN` | `-name` ... | Forbids wearing it. |
| `-MATERIAL` / `+MATERIAL` | `+WOODEN` ... | Requires / forbids a material type (`CLOTH`, `METAL`, `WOODEN`, `GLASS`...). |
| `-RESOURCES` (`-RESOURCE`) / `+RESOURCES` | `+OAK` ... | Requires / forbids a resource (exact resource name, or a resource flag name that expands to its resources). |
| `-WORNON` / `+WORNON` | `+TORSO` ... | Requires / forbids items wearable on those locations. |
| `-WEAPONTYPE` / `+WEAPONTYPE` | `+SLASHING` ... | The type of the wielded weapon (`SLASHING`, `PIERCING`, `BASHING`...). |
| `-WEAPONCLASS` / `+WEAPONCLASS` | `+SWORD` ... | The class of the wielded weapon (`SWORD`, `AXE`, `DAGGER`...). |
| `-WEAPONAMMO` / `+WEAPONAMMO` | `+arrow` ... | The ammunition type the wielded weapon uses. |
| `-DISPOSITION` / `+DISPOSITION` | `+ISHIDDEN` ... | Requires / forbids a disposition flag: `ISUNSEEN`, `ISHIDDEN`, `ISINVISIBLE`, `ISEVIL`, `ISGOOD`, `ISSNEAKING`, `ISBONUS`, `ISDARK`, `ISGOLEM`, `ISSLEEPING`, `ISSITTING`, `ISFLYING`, `ISSWIMMING`, `ISGLOWING`, `ISCLIMBING`, `ISFALLING`, `ISLIGHT`, `ISBOUND`, `ISCLOAKED`, `ISUNSAVABLE`, `ISCATALOGED`, `ISUNATTACKABLE`, `ISCUSTOM`, `ISUNHELPFUL`. |
| `-SENSES` / `+SENSES` | `+CANSEEDARK` ... | Requires / forbids a sense flag: `CANNOTSEE`, `CANSEEHIDDEN`, `CANSEEINVISIBLE`, `CANSEEEVIL`, `CANSEEGOOD`, `CANSEESNEAKERS`, `CANSEEBONUS`, `CANSEEDARK`, `CANSEEINFRARED`, `CANNOTHEAR`, `CANNOTMOVE`, `CANNOTSMELL`, `CANNOTTASTE`, `CANNOTSPEAK`, `CANNOTBREATHE`, `CANSEEVICTIM`, `CANSEEMETAL`, `CANNOTTHINK`, `CANNOTTRACK`, `CANNOTAUTOATTACK`, `CANNOTBECAMPED`, `CANGRUNTWHENSTUPID`, `CANSEEITEMSHIDDEN`. |
| `-IFSTAT` / `+IFSTAT` | `+STAT=VALUE` ... | Requires / forbids that one of the host's generic stats (the names of the generic editor: `LEVEL`, `NAME`, `RACE`...) equals the value, case-insensitively. Quote a value with spaces: `"+DESCRIPTION=a red door"`. |

#### Logic: scripts and structure

| Key | Values | Meaning |
| --- | --- | --- |
| `-IF` | `"condition"` ... | Requires a MOBPROG condition to be true (the text of an `IF`, such as `"ISNPC($i)"`, evaluated with the host as the scripted object; in a cast mask `$t` is the target). Each space-separated (so, quoted) token after the key is a separate condition and any one true is enough; the text is read in upper case. |
| `+IF` | `"condition"` ... | Passes at once when a condition is true (rest skipped). |
| `-JS` | `"expr"` | Requires the JavaScript expression to be true (`mob`, `item`, `room`, `target`, `cm` are defined; an error counts as false). |
| `+JS` | `"expr"` | Passes at once when the expression is true. |
| `+OR`, `-OR` | none | Joins groups of tests: `+OR` is plain OR, `-OR` ORs with the negation of what follows. |
| `-RIDE` | rest of the mask | The rest of the mask must be true of what the host is riding (fails if not riding). |
| `+RIDE` | rest of the mask | The rest must not be true of what it rides (passes if not riding). |
| `-FOLLOW` / `+FOLLOW` | rest of the mask | The same, for the mob it follows. |
| `+ANYINGROUP` | rest of the mask | True if the host or anyone of its group in the room fits the rest. |
| `+ALLINGROUP` | rest of the mask | True if the host and everyone of its group in the room fit the rest. |
| `-ANYINGROUP` | rest of the mask | True if no one of them fits. |
| `-ALLINGROUP` | rest of the mask | True if not everyone fits. |

### Where masks are used

Masks are accepted wherever the matching help or chapter says "zapper mask" or
"mask". A grep for the masking library finds users in:

- **Behaviors** (this chapter): the aggression and helper families, `Follower`,
  `GateGuard`, `DoorwayGuardian`, `ObjectGuardian`, `CorpseEater`, `Decay`,
  `InstantDeath`, `Scavenger`, `MOBHunter`, `Wimpy`, `Pursue`, `Morale`,
  `TargetPriority`, `Sentry`, `Healer`, `Hireling`, `MOBTeacher`,
  `GuildMaster`, `Schedule`, `Scene`, `Sounder`, `Emoter`, the spawners'
  `WHEN`, `RandomEncounters` `TRIGGER`, `ResetWhole` `EXCEPT`, and the `WHEN`
  gate of every behavior.
- **Properties** ([chapter 23](23-properties-reference.md)): the `Prop_Req*`
  family (`Prop_ReqEntry` and its relatives), `Prop_AddDamage` `TARGETMASK=`,
  `Prop_AbsorbDamage`, `Prop_HaveResister` and `Prop_WearResister`
  `SOURCEMASK=`, `Prop_StockZapper`, `Prop_HelmRestrict`.
- **Scripts**: `ISLIKE([CODE] [MASK])`, `=S MASK=[ZAPPER MASK]` in
  `EXECMSG_PROG` and `CNCLMSG_PROG`, and the optional `(MASK)` of triggers such as
  `GAME_PROG`, `SKILL_PROG` and `DEATH_PROG @GLOBAL`
  ([chapters 26 to 28](26-mobprog-triggers.md)).
- **Catalog, quests, clans, deities, classes and skills**: catalog rules in the
  `CATALOG` command, quest scripts, clan requirements (`CLANQUAL`), deity cleric
  and worshipper requirements, character class and skill qualifications,
  factions, achievements, perks, origins, waypoints, encounter tables, death
  rules and rejuvenation rules.
- **JavaScript**: `CMLib.masking().maskCheck(mask, host, true)` and
  `maskDesc(mask)` (see [chapter 5](05-javascript.md)); `-JS` and `+JS` inside a
  mask.

`maskDesc(mask)` returns the English reading of a mask ("Allows only the following
races: Elf, Dwarf"); builders see it in several editors.

### Mask examples

```text
-RACE +Elf +Dwarf
```

Only elves and dwarves.

```text
+RACE -Elf -Dwarf -CLASS +Thief
```

Anyone but elves and dwarves, and among those only thieves.

```text
-LEVEL +>=10 +<=20 -NAMES "+Captain Brand" +Marlow
```

A host level 10 to 20, named Captain Brand or Marlow.

```text
-PLAYER
```

Never players (only NPCs get through).

```text
-HOUR +20 +21 +22 +23 +0 +1 +2 +3 -SEASON +WINTER
```

Night hours, in winter only.

```text
-AREA "+Sylvan Wood" +*Forest -WEATHER +RAIN +THUNDERSTORM
```

In the area named Sylvan Wood or any whose name ends in Forest, while it rains.

```text
-ITEM "+raw meat" +OR -TATTOO +BAITED
```

Carrying raw meat, or tattooed BAITED.

```text
-CLASS +Paladin +OR -ALIGNMENT +good -LEVEL +>5
```

A paladin, or a good-aligned host above level 5.

```text
+WANTED THEFT ASSAULT
```

Only those wanted for theft or assault where they are.

```text
-FOLLOW -RACE +Human
```

Only mobs that follow a human.

```text
-IFSTAT +LEVEL=20
```

Only a host whose `LEVEL` stat is 20.

```text
-JS "cm.store('world').bellRung && cm.clock().hour() < 4"
```

True after the bell is rung and before four in the morning.

A complete property example (a door only the guild's members may use):

```text
Prop_ReqEntry: -CLAN "+Silver Hand"
```

and a behavior example, an aggressive dog that spares elves, women and anyone
carrying a bone:

```text
Aggressive -Elf -Female +ITEM -bone
```

### Mask gotchas

- Values are matched by `-KEY +value`, not `KEY value`. A forgotten sign makes the
  value skipped without any message.
- A bare `+word` is an error; use a key. Bare `-word` is a forbid test.
- A class, race, alignment or gender abbreviation matches the first name that
  starts with it in alphabetical order.
- A list swallows everything up to the next key. Put plain-text parameters (like
  `message="..."` of `Aggressive`) before the mask, not inside it.
- `+PLAYER`, `+MOB`, `+SYSOP`, `+SUBOP`, `+IF` and `+JS` pass immediately.
- `-CHANCE n` passes `100-n` percent of the time (it is "disallow n percent").
- `-ISHOME`, `-ROOMDARK`, `-OFFICER`, `-WANTED`... read against the standard
  direction: check the tables before relying on them.
- Mob keys on a non-mob host test a blank mob; if a mask on a room says
  `-RACE +Elf`, no room fits it.
- Time keys use the area clock; a mask on a mob outside any area has none.
- `-ALL` and `+ALL` are locale words of the behaviors that take them, not mask
  keys.
- The key set is large. Mistyped keys log "Bad Zappermask key" or "Bad ZapperMask
  parm" in the log and the test is dropped.

## Worked examples

### A night-prowling beast

A wolf that is aggressive only at night, wanders, ignores elves and warns first:

```text
MobileAggressive TOD=NIGHT min=10 max=20 chance=75 WARN="The wolf bares its teeth.|The wolf snarls and crouches!" WARNTICKS=2 BACKOFF=true -RACE +Human +Dwarf
Mobile TOD=DAY min=20 max=60 wander
```

`TOD=NIGHT` gates the first behavior; the second takes over in daylight. The mask
`-RACE +Human +Dwarf` makes only humans and dwarves a target. `STAT BEHAVIORS
wolf` shows which of the two is on now.

### A city gate

A gate guard who lets in only some visitors, a lookout who raises the alarm, and a
sleeping guard the alarm can wake. Three mobs, three behaviors:

```text
GateGuard keeplocked -RACE +Human +Elf -ALIGNMENT +good
Sentry SPOT=30 ALARM="RADIUS=5 MASK=-NAMES +guard MSG=Intruders at the gate!" ALERTTICKS=30
Sleeper SLEEPS=NIGHT WAKEON=NOISE,ENTER WAKECHANCE=40 ALARM
```

Use `MODIFY MOB [name]` and add each behavior at the `Behaviors` line. The guard
opens and closes the door for players who fit the mask (the guard counts
players it can see in its room who pass it), and keeps the door locked
otherwise. The sentry spots hidden or sneaking intruders at 30% a tick and, when
it does, shouts to every NPC within five rooms whose name has "guard"; the sleeper
sleeps through the night and is woken by noise or an entrance, and then raises
its own alarm.

### A tavern with barks and a rumour keeper

```text
Sounder GREET: COOLDOWN=30 2 SAY Welcome, friend. | 1 SAY Mind the dog.;SEE_MOB(-RACE +Elf): SAY We don't get many of your kind in here.;NIGHT: SAY Last call soon!
MudChat
Memory GREETIF=MET>0 GREET="Welcome back, $n." FORGET=30d
```

The innkeeper has three behaviors: barks (a greeting drawn by weight, a remark
about elves, and a night line), a MudChat that answers speech from the chat
file (picked by the mob's name), and a Memory that greets returning customers.

## Persistence notes

- A behavior and its parameter text are part of its host's definition: they are
  written to world files (`BEHAVES`) by `SAVE` in builder mode and carried into
  campaign saves with the host.
- Run state (patrol position, hireling contract, spawn counts...) is written only
  to campaign saves, as `BSTATE` entries, and restored once the behaviors are
  recreated on their hosts after a restore. Definition changes in the world files
  therefore win for parameters, while a saved game keeps its run state.
- `Decay` writes its remaining countdown into its parameters (`remain=`), so
  that survives even a world save.
- `GenBehavior` definitions live in `world/custom/behaviors/`; destroying a
  definition leaves behaviors that carry it on their hosts until the next load.
- Masks are not saved separately: they are text in the parameters (or property
  text) that contains them, and are recompiled from text.
- Run state belongs to a behavior on a host that loads; a behavior removed from a
  host loses its state with it.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| A behavior does nothing | The host is not a valid type (`canImproveCode`), the mob is asleep or bound (timed behaviors do not act), the `WHEN`/`TOD`/`HOURS` gate is closed (`STAT BEHAVIORS`), or the countdown has not ended (use `TRACEB`). |
| `minitems=10` makes ticks too long | Key matching is by start: `min` matches `minitems`. Put `min=` and `max=` before the item keys, or give them explicitly. |
| A mask lets everyone through | The mask text is empty (no key found), a `+PLAYER` or similar passed early, or an unknown key was dropped; check the log for "Bad Zappermask key". |
| A mask lets no one through | A `-KEY` list with no valid values for it (all skipped); a mob key on a non-mob host; a typo in a value (`+Elph`). |
| `Aggressive +elf` aggresses nothing different | A bare `+word` is not a mask key. Write `-RACE +Elf`. |
| Two behaviors fight (Fighterness with Mageness) | Only one `CombatAbilities`-family behavior per mob. |
| After removing a combat preset the mob keeps strange stats | The presets rewrite the mob's stats; reset the room and check them by hand. |
| `GateGuard keeplocked allnight` ignores `allnight` | Only the first of the two flags is read. |
| `WeatherAffects shipdmg=...` has no effect | The code reads `dmgship` (with `shipdmgpct`). |
| STAT BEHAVIORS lists a key as unknown | It is not named in the behavior's help (it may still work: for example `ATNIGHT` of Scavenger). |

## See also

- [Properties reference](23-properties-reference.md): the `Prop_*` abilities,
  including the `Prop_Req*` masks.
- [MOBPROG guide](04-mobprog.md), [triggers](26-mobprog-triggers.md),
  [commands](27-mobprog-commands.md), [functions](28-mobprog-functions.md): the
  language behind `Scriptable`, `GenBehavior` and `-IF`.
- [NPCs and dialogue](06-npcs-dialogue.md): MudChat files, schedules, sentries,
  barks, scenes.
- [Combat and encounters](29-combat-and-encounters.md): morale, phases, waves,
  encounter tables, threat.
- [Society](09-society.md): law, clans, economy.
- [Travel, vehicles and instances](30-travel-vehicles-instances.md): transport
  behaviors.
- [Generation](12-generation.md): random spawners and area regeneration.
- [JavaScript](05-javascript.md): `JSBehavior`, `JScriptable`, `-JS` masks.
- [Engine programming](32-engine-programming.md): writing a new behavior class.
- [State and persistence](state-and-persistence.md).
- [Tool reference](tool-reference.md): the generated help text, including
  `BEHAVIOR_*` and `ZAPPERMASKS`.
