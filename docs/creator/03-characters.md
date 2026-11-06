# Characters, game start and progression

[Handbook](README.md) · Feature families [P](feature-index.md#p), [S](feature-index.md#s)

This chapter covers everything that makes a player character: races, character
classes, the creation flow and the data files that shape it (creation steps,
origins, kits, perks and traits, ready-made characters), stats and the numbers
derived from them, levels and experience, training and practices, teachers,
skill trees, rank ladders, respec, ageing, the death and rebirth settings that
act on characters, and the achievements and titles that reward them. It is
written for world creators (in-game editors and world files), scripters
(MOBPROG and JavaScript hooks) and engine developers (where the behaviour
lives in Java). Abilities, properties, diseases and poisons are the subject of
[25-abilities-and-effects.md](25-abilities-and-effects.md); the profile, party
and saved games are the subject of [31-saves-and-party.md](31-saves-and-party.md).

## In this chapter

- [How a character is made](#how-a-character-is-made)
- [Design the opening contract](#design-the-opening-contract)
- [Character creation](#character-creation)
  - [The steps, in order](#the-steps-in-order)
  - [What creation gives, and when](#what-creation-gives-and-when)
  - [Start rooms](#start-rooms)
  - [Creation settings reference](#creation-settings-reference)
  - [Creation scripts](#creation-scripts)
  - [Creation texts and macros](#creation-texts-and-macros)
- [Creation data](#creation-data)
  - [creation.ini: steps and questions](#creationini-steps-and-questions)
  - [origins.ini: origins](#originsini-origins)
  - [Kits](#kits)
  - [perks.ini: perks, traits and flaws](#perksini-perks-traits-and-flaws)
  - [Ready-made characters (player templates)](#ready-made-characters-player-templates)
  - [Review, BACK and QUICK](#review-back-and-quick)
- [Races and classes](#races-and-classes)
  - [Stock races](#stock-races)
  - [Stock classes](#stock-classes)
  - [Who may be what: availability and unlocking](#who-may-be-what-availability-and-unlocking)
  - [Creating, converting and destroying](#creating-converting-and-destroying)
  - [GenRace fields](#genrace-fields)
  - [GenCharClass fields](#gencharclass-fields)
  - [Class systems, subclasses and multi-classing](#class-systems-subclasses-and-multi-classing)
  - [Qualifying abilities](#qualifying-abilities)
  - [Body parts, wear locations and ageing charts](#body-parts-wear-locations-and-ageing-charts)
- [Stats and derived values](#stats-and-derived-values)
- [Abilities, properties, diseases, poisons and trades](#abilities-properties-diseases-poisons-and-trades)
- [Advancement and discovery](#advancement-and-discovery)
  - [Levels and experience](#levels-and-experience)
  - [What a level gives](#what-a-level-gives)
  - [Starting level for later characters](#starting-level-for-later-characters)
  - [Scripted level changes](#scripted-level-changes)
  - [Experience over time and by role-play](#experience-over-time-and-by-role-play)
  - [Discovery experience](#discovery-experience)
  - [Training, practice and teachers](#training-practice-and-teachers)
  - [Respec and refunds](#respec-and-refunds)
  - [Scripted progression (progression.js)](#scripted-progression-progressionjs)
  - [Checking the curve](#checking-the-curve)
- [Make advancement discoverable](#make-advancement-discoverable)
  - [Skill trees and hints](#skill-trees-and-hints)
  - [Perks after creation](#perks-after-creation)
  - [Rank ladders](#rank-ladders)
- [Ageing](#ageing)
- [Death, rebirth and remort](#death-rebirth-and-remort)
- [Achievements and titles](#achievements-and-titles)
- [Profile, party and recruitment](#profile-party-and-recruitment)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## How a character is made

A player character is a mob with a `PlayerStats` record. What the engine
considers its identity is made of a small number of pieces, all of which you
can author:

| Piece | Where it comes from | Chapter section |
| --- | --- | --- |
| Race | A stock Java race or a generic race (`GenRace`) | [Races and classes](#races-and-classes) |
| Class and class level | A stock class or a generic class (`GenCharClass`) | [Races and classes](#races-and-classes) |
| Six base stats, saves and the other charstats | Rolled or bought at creation, then adjusted by race, class, effects and ageing | [Stats and derived values](#stats-and-derived-values) |
| Level, experience, practices, trains | Earned in play; formulas and files set the pace | [Advancement and discovery](#advancement-and-discovery) |
| Tattoos | Free-text marks on the character or on its profile; masks and scripts read them | origins, perks, ranks and answers are all stored as tattoos |
| Factions | Standing with organisations (`alignment.ini` and the like) | [09-society.md](09-society.md) |
| Abilities and expertises | Granted by race, class, kits, origins, teachers, quests | [25-abilities-and-effects.md](25-abilities-and-effects.md) |
| Start room | `START` rules, origin, answers | [Start rooms](#start-rooms) |

CoffeeSUD is single-player: the logins and accounts of the original engine are
replaced by a local **profile** that owns several playable characters. The
profile menu's `NEW` command starts character creation. Inherited steps that
only mattered for network play (password, email, terminal negotiation) are
handled by the single-player start-up and are not described here. Everything
from the theme question onward is the creation flow documented below.

Many "unlocks" and "gifts" in this chapter work through **tattoos**. A
character tattoo belongs to one character; a profile tattoo (what the original
engine called an account tattoo) belongs to the whole profile and is visible to
every character in it. Masks (`-TATTOOS +NAME`, see
[24-behaviors-and-masks.md](24-behaviors-and-masks.md)) and the script
functions `HASTATTOO` and `HASPERK` read them.

## Design the opening contract

Choose before you build whether the game has a fixed hero, offered templates,
or free creation; whether new characters can join later; and how death and
saving work. These choices affect every tutorial, quest reward and encounter.
Put world-owned rules in `world/world.ini` (the keys a world may set are listed
in [world-settings.md](world-settings.md)). The engine-only keys (database,
logs, `REMORTMASK`, `EXPDEFER`, `RPAWARDS`, skill and language costs) belong in
`coffeemud.ini`; see [19-configuration-and-administration.md](19-configuration-and-administration.md).

| Design | How to get it |
| --- | --- |
| One fixed hero, no choices | `CREATIONTEMPLATES=ONLY` with a single template that has `LOCK=NAME,GENDER`; `NEWCHARS=FIRSTONLY`; `PROFILEMAX=1` |
| A party of authored heroes | `CREATIONTEMPLATES=ONLY`, one template each; `PARTYMAX`/`PROFILEMAX` as needed |
| Free creation with flavour | Default steps plus `origins.ini`, kits and `creation.ini` questions |
| Point-buy or scripted stats | `STARTSTAT=-1` (point buy) or `STARTSTAT=SCRIPT` with `progression.js` |
| Later characters join at the party's level | `NEWCHARLEVEL=PARTYAVG` (or `PARTYMIN-1`, `LEADER`) |
| Hard mode | `PLAYERDEATH` with `UNLEVEL`/`LOSESKILL`/`PURGE`, `PARTYDEFEAT=DEATH` |

`CREATIONTEMPLATES=OFFER` offers authored player templates alongside custom
creation; `ONLY` restricts creation to templates; `NO` never offers them.
Templates can carry race, class/level, abilities, stats, factions, tattoos and
equipment, with locked name and gender. Test the actual `NEW` flow: editing a
template does not rebuild an existing character, and builder privileges can
hide policy mistakes, so play the flow as an ordinary character as well.

Set the start room with `START`, with qualified `START_...` rules where needed.
Origin-specific `START` values, `creation.ini` answers and creation scripts
can send a character somewhere else. See [Start rooms](#start-rooms).

## Character creation

Source: [CharCreation.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CharCreation.java),
[CreationSteps.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CreationSteps.java).

At the profile menu, `NEW [name]` asks for confirmation and then runs the
creation flow. `NEW name QUICK` takes every step at random, and `NEW name QUICK
template` makes that ready-made character (see
[Review, BACK and QUICK](#review-back-and-quick)). `NEW *` makes up a name in
the style of a race or language (`NEW * dwarf`); the profile limits
(`PROFILEMAX`, `NEWCHARS`, `NEWCHARMASK`) are checked first, and the first
character of a profile is always allowed. See
[31-saves-and-party.md](31-saves-and-party.md) for those rules.

### The steps, in order

| # | Step | What happens | World levers |
| --- | --- | --- | --- |
| 1 | Theme | Asked only when `MUDTHEME` allows more than one theme (3, 5, 6 or 7). Fixes the theme used to filter races and classes. Runs `THEME` scripts | `MUDTHEME` |
| 2 | Ready-made characters | Offered when templates exist and `CREATIONTEMPLATES` is not `NO`. Picking one skips to the end; `0` makes your own (not in `ONLY` mode) | `CREATIONTEMPLATES`, [templates](#ready-made-characters-player-templates) |
| 3 | `RACE` | Pick from the races open to the player (`@RaceList@`). Runs `RACE` scripts | availability, `RACE_` tattoos, `SHOWLOCKED`, `DISABLE`/`ENABLE` |
| 4 | `GENDER` | M, F or N. Runs `GENDER` scripts | `creation.ini` `GENDER=RANDOM` |
| 5 | `STATS` | Random roll, point buy, fixed or scripted, by `STARTSTAT`. Runs `STATS` scripts | `STARTSTAT`, `MAXSTATS`, `BASEMINSTAT`, `BASEMAXSTAT` |
| 6 | `CLASS` | Pick from the classes whose requirements the stats and race meet. Sets level 1, gives `STARTINGITEMS`, the race outfit and a random allergy. Runs `CLASS` scripts | `CLASSSYSTEM`, class qualifications, `DISABLE=CLASSES` |
| 7 | `ORIGIN` | Asked when `origins.ini` has an origin open to the character | [origins.ini](#originsini-origins) |
| 8 | `KIT` | A menu for each `choose` group of starting kits that fits the character; a lone kit in a group is taken without asking | [Kits](#kits) |
| 9 | `TRAITS` | Take or drop traits and flaws from `perks.ini` by name, then `DONE`; the points must not be overspent (skipped when no trait is open to the character, or `TRAITS=RANDOM`/`SKIP`) | [perks.ini](#perksini-perks-traits-and-flaws) |
| 10 | World questions | `creation.ini` `[NAME]` sections, wherever `STEPS` puts them | [creation.ini](#creationini-steps-and-questions) |
| 11 | `FACTIONS` | For each faction that offers a choice for this character, a pick. Factions with a single choice or an automatic default are set silently. Runs `FACTIONS` scripts. Then: class start-up (class abilities, first-level practices and trains), the class outfit, the start room, birthday and age, the class's starting money | faction files, [09-society.md](09-society.md) |
| 12 | `DEITY` | Only when `DEITYPOLICY` has a `REQUIREDMASK` the character meets and some deity qualifies | `DEITYPOLICY` |
| 13 | `REVIEW` | Only when `REVIEW` is listed in `STEPS` | [Review, BACK and QUICK](#review-back-and-quick) |
| 14 | Finish | The gifts below, the `newchardone.txt` screen, then the character enters the world | |

Steps 3 to 6, 11 and 12 keep this relative order whatever `creation.ini` says
(each needs the one before; for example, class qualification needs the stats).
`ORIGIN`, `KIT`, `TRAITS` and the world's questions can be placed anywhere that
keeps that order; by default they come right after `CLASS`.

Races, genders, stats and classes can each be left unasked: `creation.ini`
lists them with `RACE=RANDOM` (or `SKIP`), or leaves them out of `STEPS`, and
the engine draws one at random. The `DISABLE` flags `CHARCRRACE`,
`CHARCRGENDER`, `CHARCRSTAT` and `CHARCRCLASS` do the same for the whole game
(see [19-configuration-and-administration.md](19-configuration-and-administration.md)).

### What creation gives, and when

When the last step is done (before the character first appears) the engine
applies, in this order:

1. The origin's gifts (`ORIGIN`): start room, items, abilities, standing,
   tattoos, money and script.
2. `CHARCREATIONSCRIPTS` `ORIGIN:` scripts.
3. Every starting kit that fits and is not a menu choice, and the kits chosen.
4. The world questions' answers (`EFFECTS`, `FACTION`, `START`, `SCRIPT`).
5. The traits' and flaws' rewards.
6. The race's `FSTTRAN` and `FSTPRAC` extra trains and practices, and any
   `TRAINSATLEVELS` entry for level 1.
7. `STARTAGE_` (the starting age).
8. `NEWCHARLEVEL`: the character is raised to its starting level by real
   level-ups.
9. After the character is in the world: `CHARCREATIONSCRIPTS` `END:` scripts.

A ready-made character (a player template) skips the questions after the
template menu and all of the gifts above: the template's own contents are
applied instead, and then the character gets a start room and a birthday (see
[templates](#ready-made-characters-player-templates)).

Two earlier gifts come from the class and race themselves: the class's
abilities, first-level practices and trains, outfit and starting money (end of
step 11), and the race's outfit and abilities (at the class step).

### Start rooms

A new character's start room is chosen by `getDefaultStartRoom`. The first
rule that gives a room wins:

1. In builder mode and `DEV=1` games, `DEVSTART` if it names a real room.
2. The first `START_MASK...` entry whose zapper mask the character passes.
3. `START_<race ID>` (for example `START_DWARF`).
4. `START_<racial category>` (spaces become `_`).
5. `START_<alignment name>`.
6. `START_<class ID>`.
7. `START_<faction range name>`, for each range the character is in.
8. `START_<deity>`.
9. `START_<level>`: a number key covers that level and every higher level
   up to `LASTPLAYERLEVEL + 10`, unless a higher number key exists.
10. `START` itself.
11. A room literally named `START` if one exists, then any room.

A `START_MASK` entry is `START_MASK1=RoomID=zapper mask` (any text after
`MASK` makes the key unique): the room ID comes first, then `=`, then the
mask.

```ini
START=New Area#0
START_DWARF=Deepholm#1
START_FIGHTER=Barracks#4
START_MASK1=Docks#3=-TATTOOS +ORIGIN_SAILOR
```

`DEATH_...` and `MORGUE_...` keys use the same grammar (see
[Death, rebirth and remort](#death-rebirth-and-remort)). An origin's `START`,
a creation answer's `START` and a template's room override the result for that
character.

### Creation settings reference

Keys marked **W** may be set in `world/world.ini`; the rest belong in
`coffeemud.ini` only. Defaults are those of the shipped
[coffeemud.ini](../../CoffeeMud/coffeemud.ini).

| Key | W | Default | Meaning |
| --- | --- | --- | --- |
| `MUDTHEME` | W | `1` | Bit mask of themes: 1 fantasy, 2 technological, 4 heroic (so 3 = fantasy and tech, 5, 6, 7). Races and classes are filtered by their availability against it. 0 refuses new characters |
| `START` | W | `New Area#0` | Start room ID. See [Start rooms](#start-rooms) for qualified forms |
| `STARTHP` | W | `20` | Hit points at level 1. May be `20,+(@x1/5)`: text after the comma is appended to every class's hit point formula |
| `STARTMANA` | W | `100` | Mana at level 1. Same comma addendum for the mana formula |
| `STARTMOVE` | W | `100` | Moves at level 1. Same comma addendum for the movement formula |
| `STARTSTAT` | W | `0` | `0` random roll (total from `MAXSTATS`), `-1` point buy, `N` every stat starts at N (random bonus points added), `SCRIPT` ask `progression.js` `statRoll` |
| `MAXSTATS` | W | `70` | Total of the six base stats for a random roll or point buy (clamped between `(BASEMINSTAT+1)*6` and `(BASEMAXSTAT-1)*6`) |
| `BASEMINSTAT` | W | `3` | Lowest a base stat starts at |
| `BASEMAXSTAT` | W | `18` | Base ceiling per stat (race and class add to it) |
| `STATCOSTS` | W | `<18 1, <22 2, <25 3, <99 5` | Trains to raise a base stat, by current value. See [Training](#training-practice-and-teachers) |
| `TRAINCOSTS` | W | `HITPOINTS 10 1 TRAIN, MANA 20 1 TRAIN, MOVES 20 1 TRAIN, GAIN 1 7 PRACTICE, PRACTICES 5 1 TRAIN` | What TRAIN can buy |
| `STARTINGITEMS` | W | `1 Ration, 1 Waterskin, 1 Torch` (the shipped file also has per-race lists) | Items every new character gets. See below |
| `CLASSSYSTEM` | W | `SUB` | How classes are chosen and changed. See [Class systems](#class-systems-subclasses-and-multi-classing) |
| `RACEMIXING` | W | blank | Which race results when two races are mixed by magic (`FATHER`, `MOTHER`, or `Orc+Human=Gnome,MOTHER Elf+Human=HalfElf\|Elf`) |
| `CHARCREATIONSCRIPTS` | W | blank | Scripts run during creation. See [Creation scripts](#creation-scripts) |
| `CREATIONTEMPLATES` | W | `OFFER` | `OFFER`, `ONLY` or `NO` |
| `SHOWLOCKED` | W | `NO` | `YES` lists locked races and classes (those with an `UNLOCKHINT`) with their hint |
| `DEITYPOLICY` | W | blank | `REQUIREDMASK="..."`, `DEITYMASK="..."`, `NOREBUKE=TRUE`, `NOCONVERT=TRUE` |
| `NEWCHARLEVEL`, `NEWCHARXP` | W | `1`, `KEEP` | See [Starting level](#starting-level-for-later-characters) |
| `NEWCHARS`, `NEWCHARMASK`, `NEWCHARMESSAGE`, `PROFILEMAX` | W | `ALWAYS`, blank, blank, `0` | Who may make more characters. See [31-saves-and-party.md](31-saves-and-party.md) |
| `FORMULA_PRACTICESPERLEVEL`, `FORMULA_TRAINSPERLEVEL`, `TRAINSATLEVELS` | W | blank | See [What a level gives](#what-a-level-gives) |
| `STARTAGE_<name>` | W | none | A starting age by mask. See [Ageing](#ageing) |
| `AGEDISEASES` | W | blank | Ageing diseases. See [Ageing](#ageing) |
| `DISCOVERYXP` | W | blank | XP for entering a room for the first time |
| `LASTPLAYERLEVEL` | W | `91` | Highest level mundane experience can reach |
| `EXPRATE` | W | `6` | Level-difference scaling of combat XP; 0 for none |
| `XPMOD` | W | blank | Global experience modifiers (same arguments as `Prop_ModExperience`) |
| `PLAYERDEATH`, `MOBDEATH` | W | see [Death](#death-rebirth-and-remort) | |
| `ADDCHARSTAT_<name>`, `REPLACECHARSTAT_<name>`, `STATEFFECT_<stat>` | W | none | New, renamed and effective stats. See [Stats](#stats-and-derived-values) |
| `ENABLE`, `DISABLE` | W | `DISABLE=DBERRORQUESTART, COMMAND_POSE, QUITREASON, RETIREREASON, CHANNELAUCTION` | Switch races, classes and subsystems on or off |
| `MAXCOMMONSKILLS`, `MAXCRAFTINGSKILLS`, `MAXNONCRAFTINGSKILLS`, `MAXLANGUAGES` | | `0`, `2`, `5`, `3` | Limits on skills qualified for by every class (0 removes a limit). A class can override each |
| `SKILLCOST`, `COMMONCOST`, `LANGCOST` | | `1 TRAIN` (with `Skill_WildernessLore 1 PRACTICE, PlayInstrument 3 PRACTICE`), `1 TRAIN`, `3 PRACTICE` | What learning a skill costs; `@x1` skill level, `@x2` player level |
| `PRACMAXPCT` | | `75` | Highest proficiency a practice point can reach |
| `REMORTMASK`, `REMORTRETAIN` | | `-LEVEL +>60`, `100% FACTIONS, 100% QUESTPOINT, RACE, GENDER` | `REMORT` rules |
| `EXPDEFER`, `RPAWARDS` | | blank | See [Experience over time](#experience-over-time-and-by-role-play) |
| `FLEE` | | `EXPERIENCE` | What fleeing costs (same list as `PLAYERDEATH`) |
| `DEFAULTPLAYERFLAGS` | | blank | Player flags (`AUTOLOOT`, `AUTOEXITS`, ...) on at creation |

**`STARTINGITEMS`** is a comma list of item class IDs, catalog names or
counted entries (`2 Torch`). A race ID in the list starts a separate list for
that race: entries before the first race are the default set, and a race with
its own set gets that set instead of the default. A lone `,` gives no items.
Beware an item whose name is also a race ID: it is read as a race.

**`STARTSTAT=-1`** (point buy) starts every stat at `BASEMINSTAT` and gives
the player `MAXSTATS - 6 * BASEMINSTAT` points, plus any bonus points from
achievements, to spend with no stat above `BASEMAXSTAT`. With `0` the engine
rolls a total of `MAXSTATS` points over the six stats, retrying up to 50 times
until some class qualifies, and offers a re-roll. With a number `N`, every stat
starts at `N` and the player is not asked.

### Creation scripts

`CHARCREATIONSCRIPTS` is a comma list. Each entry is a MOBPROG script text or
`LOAD=file`, optionally prefixed by a code and a colon. Only the `ONCE_PROG`
of the script runs. The valid codes are `PASSWORD`, `EMAIL`, `ANSI`, `THEME`,
`RACE`, `GENDER`, `STATS`, `CLASS`, `ORIGIN`, `FACTIONS`, `AUTHENTICATE`,
`CHARLOGIN`, `ALLCHARLOGIN` and `END`; the default is `END`. A code may be
shortened to a prefix (`FACT:`). `CHARLOGIN` runs each time a character comes
into the world, and `ALLCHARLOGIN` also when switching characters.

```ini
CHARCREATIONSCRIPTS=FACTIONS:LOAD=progs/chardesc.script, END:LOAD=progs/welcome.script
```

For richer hooks (a function called after every step, or the stat roll
itself), use [progression.js](#scripted-progression-progressionjs). The engine
has no `DEITY` script code; use the JavaScript `onCreationStep`.

### Creation texts and macros

The screens creation shows are text files in `resources/text/` (a world's own
copy in `world/resources/text/` is read instead): `newchar.txt`, `themes.txt`,
`races.txt`, `stats.txt`, `classes.txt`, `newchardone.txt`. They are filtered
before they are shown, for the character being made:

| Macro | Expands to |
| --- | --- |
| `@RaceList@` | The races open to this player, and those the character or its profile has unlocked with a `RACE_` tattoo |
| `@ClassList@` | The same for classes (`CHARCLASS_` tattoos) |
| `@DeityList@` | The deities that qualify |
| `@StatList@` | The stats, including `ADDCHARSTAT_` ones and renames |
| `@RaceList?FORMAT=TABLE@` (also the other lists) | One entry a line, each with the first line of its help |
| `@if?Tattoo=NAME@ ... @else@ ... @endif@` | Text shown only if the character or profile has the tattoo (`Tattoo=!NAME` for not) |
| `@MudInfo?NAME@`, `@INIValue?INI=KEY@` | Game name, a setting's value |

With `SHOWLOCKED=YES` a race or class that is kept behind a tattoo and has an
`UNLOCKHINT` is listed as `Drow (locked: reach the Underdark)` in those macros
and in the prompts, and typing its name says the hint. The stock `races.txt`
and `classes.txt` are hand-written lists (they still describe Drow and Duergar,
which are not open to players by default); replace them with the macros when
your content differs.

