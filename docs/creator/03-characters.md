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

## Creation data

| File/system | Purpose | Important distinction |
| --- | --- | --- |
| `world/creation.ini` | Step order, steps left unasked, and the world's own questions | Absent: the default order, no review |
| `world/origins.ini` | Background choices with a start room, items, skills, standing, tattoos, money and a script | Sectioned file at the world root, not under `data/` |
| `world/kits/*.kit` | Starting equipment and selectable kit groups | The same files serve `TESTCHAR` and `KIT` |
| `world/perks.ini` | Perks, traits and flaws, picks per level, trait points | One line per perk, not sectioned |
| Catalog category `PLAYERTEMPLATE` | Ready-made characters | Catalog mobs with `Prop_PlayerTemplate` |
| `CHARCREATIONSCRIPTS` | MOBPROG hooks around engine steps | Not a file format |
| `world/scripts/progression.js` | JavaScript hooks for creation and progression | Return values can change XP, practices, trains and stat rolls |

All the `.ini` files above share one syntax: a line ending in `\` continues on
the next line, and `#` or `;` at the start of a line starts a comment. They
are read from the world folder, re-read when they change (`creation.ini`,
`perks.ini`, `ranks.ini`) or at each use (`origins.ini`, kits), so you can
edit them while testing `NEW`.

### creation.ini: steps and questions

Source: [CreationSteps.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CreationSteps.java).
Fixture: [p12-creation.js](../../tools/fixtures/fixes/p12-creation.js).

The file has two kinds of line. Before any `[SECTION]` header they are
settings; after a header they belong to a question.

**Top-level settings**

| Line | Meaning |
| --- | --- |
| `STEPS=RACE,GENDER,STATS,CLASS,ORIGIN,KIT,TRAITS,FACTIONS,DEITY` | The steps to run, in this order. This is also the default when the file or the line is missing. Add `REVIEW` for the review sheet. Names of your own questions go here, uppercase, spaces as `_` |
| `<STEP>=ASK` or `RANDOM` or `SKIP` | How a listed standard step is taken. `RANDOM` picks for the player. `SKIP` (or `NO`) skips `ORIGIN`, `KIT`, `TRAITS`, `FACTIONS`, `DEITY` and the world's questions, but for `RACE`, `GENDER`, `STATS`, `CLASS` it also means "random", because the character needs them |
| `GENDER=RANDOM` | Example of the above |

A standard step left out of `STEPS` is not asked: `RACE`, `GENDER`, `STATS`
and `CLASS` are drawn at random, the rest skipped. `RANDOM` for `TRAITS` takes
no traits. `RACE`, `GENDER`, `STATS`, `CLASS`, `FACTIONS` and `DEITY` keep the
engine's order; if `STEPS` lists them differently the engine logs a message and
uses its own order. A `STEPS` name that is neither a standard step nor a
`[NAME]` question is logged.

**A question** is a section with these lines:

| Line | Meaning |
| --- | --- |
| `[NAME]` | Header; the name is uppercased with spaces as `_`. It may not be the name of a standard step |
| `PROMPT=text` | The question shown |
| `INTRO=file` | A text file shown first: looked up in `world/`, then `resources/` |
| `SHOWIF=zapper mask` | The question is asked only of characters who pass the mask (race, class and stats are already known when it comes after them) |
| `MODE=RANDOM` | The engine answers at random from the choices open to the character |
| `CHOICE=NAME="..." HELP="..." MASK="..." EFFECTS="..." FACTION="..." START="..." SCRIPT="..."` | One answer; repeat the line for each. `NAME` is required. Quote values that contain spaces |

A choice's fields:

| Field | Meaning |
| --- | --- |
| `NAME` | What the player types (number, name, or the start of the name) |
| `HELP` | Shown beside the name in the menu |
| `MASK` | Zapper mask; the choice is offered only to characters who pass |
| `EFFECTS` | Achievement-style rewards, as `"1 CHARISMA 50 XP"` (see [Achievements](#achievements-and-titles)) |
| `FACTION` | A faction ID or name and an amount to add: `"alignment.ini 100"` |
| `START` | A room ID: the character's start room |
| `SCRIPT` | A script, as `"ONCE_PROG 100;mpechoat $i Welcome home;~"` (lines between semicolons) or `LOAD=file` |

The answer is kept as the tattoo `CHARCR_<QUESTION>_<CHOICE>` (uppercase;
every run of other characters becomes `_`), so masks and scripts can read it
(`-TATTOOS +CHARCR_UPBRINGING_NOBLE`, `HASTATTOO(...)`). The choice's
`EFFECTS`, `FACTION`, `START` and `SCRIPT` are applied at the end of creation
(see [What creation gives](#what-creation-gives-and-when)); the script runs
once in the character's start room.

```ini
# world/creation.ini
STEPS=RACE,GENDER,STATS,CLASS,UPBRINGING,ORIGIN,KIT,FACTIONS,REVIEW
GENDER=RANDOM

[UPBRINGING]
PROMPT=Where were you raised?
SHOWIF=-RACE +Human
CHOICE=NAME="Noble" HELP="Born in the manor." EFFECTS="1 CHARISMA" FACTION="alignment.ini 100" START="Manor#1"
CHOICE=NAME="Street urchin" HELP="The docks raised you." MASK="-CLASS +Thief"
CHOICE=NAME="Farmhand" HELP="Fields and cows."
```

(The faction file name `alignment.ini` is the stock alignment faction; use the
ID of a faction your world loads, see [09-society.md](09-society.md).)
An unknown key in a question section is logged and ignored. Two cautions:
EFFECTS rewards are real rewards, so a choice that gives experience or a
tattoo is given once per character made, not once per profile; and a mask on
`SHOWIF` is evaluated when the question comes up, so put it after the steps
whose results it tests.

### origins.ini: origins

Source: [Origins.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Origins.java).
Fixture: [p6-origins.js](../../tools/fixtures/fixes/p6-origins.js).
In-game help: `AHELP ORIGINS`.

An origin is a background the player picks after the class. Each is a `[ID]`
section of `KEY=VALUE` lines (spaces in the ID become `_`). When any origin is
open to the character, creation asks for one by number, ID or name (or the
start of either). The choice is kept as the tattoo `ORIGIN_<ID>` (uppercase),
for masks (`-TATTOOS +ORIGIN_ORPHAN`) and scripts; a character holds only one.

| Key | Meaning |
| --- | --- |
| `NAME` | Name in the menu (the ID if missing) |
| `DESCRIPTION` | One line shown beside the name |
| `MASK` | Zapper mask: who may take it, as `-RACE +Human +Half-Elf` |
| `UNLOCK` | A tattoo the character or its profile must have (for example one a profile achievement gives); otherwise the origin is hidden |
| `START` | A room ID (`Area#12`): the character's start room. Logged if no such room |
| `ITEMS` | Comma list of catalog item names or item class IDs, a count first for more than one (`2 Torch, a travelling cloak`). The catalog is tried before the class IDs; the count is limited to 1 to 100. Logged if neither exists |
| `ABILITIES` | Comma list of `ID proficiency` (`Prayer_Bless 25`). Proficiency defaults to the ability's own. A skill the character has is raised, never lowered |
| `FACTIONS` | Comma list of `faction ID-or-name amount`; the amount is added to the character's standing |
| `TATTOOS` | Comma- or space-separated tattoos to add (uppercased) |
| `MONEY` | A number, in the character's currency |
| `SCRIPT` | A MOBPROG, `ONCE_PROG 100;mpecho Welcome home.;~` (lines between semicolons) or `LOAD=file`. Run once, as the character's creation script |

Unknown keys are ignored. The origin's gifts come at the end of creation,
first of all, after the class outfit and start room are set, so an origin's
`START` overrides the `START_` rules. `CHARCREATIONSCRIPTS` `ORIGIN:` scripts
run just after the origin's gifts. `ORIGIN=RANDOM` in `creation.ini` picks one
at random; with no origin open to a character the step is skipped.

```ini
# world/origins.ini
[ORPHAN]
NAME=Temple Orphan
DESCRIPTION=Raised beside the temple library.
MASK=-RACE +Human +Half-Elf
START=Ashford#12
ITEMS=a worn prayer book, 2 Torch
ABILITIES=Prayer_Bless 25
FACTIONS=alignment.ini 100
TATTOOS=ORPHAN
MONEY=10
SCRIPT=ONCE_PROG 100;mpechoat $i The temple bells ring for you.;~

[EXILE]
NAME=Exile
DESCRIPTION=Cast out, \
  and far from home.
UNLOCK=EXILE_UNLOCKED
```

Use catalog item names (create them in the catalog first; see
[22-items.md](22-items.md)) for meaningful gifts. An item class such as
`GenItem` creates a blank item.

### Kits

Source: [StartingKits.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/StartingKits.java),
[KitCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/KitCmd.java).
Fixture: [p7-kits.js](../../tools/fixtures/fixes/p7-kits.js).

A kit is a text file `world/kits/NAME.kit` of directive lines (a word, then
its value; `#` starts a comment). Kits serve two purposes. For builders and
testing, `KIT NAME [character or mob]` applies a kit, and `TESTCHAR NAME
KIT=NAME (PARTY)` makes a whole character from one (builder mode or a `DEV=1`
game). For creation, a kit with a `starting` line is given to new characters.

| Directive | Meaning | Used by |
| --- | --- | --- |
| `starting all` or `starting <zapper mask>` | Makes it a starting kit, given to every new character it lets in (`starting -BASECLASS +Fighter`, `starting -TATTOOS +ORIGIN_NOBLE`). Without it the kit is for `KIT`/`TESTCHAR` only | creation |
| `choose GROUP` | Kits with the same group are a menu in the `KIT` step; one is taken. A group of one is taken without a question. Kits without `choose` are all given | creation |
| `title text` | The kit's name in the menu (the file name if missing) | creation |
| `wear no` | Do not put the kit's items on | creation |
| `item [count] <item class, catalog name, or catalog:NAME>` | An item. A leading number repeats it (1 to 100). The item class is tried first, then the catalog; `catalog:` forces the catalog. Items are worn if they can be, unless `wear no` | all |
| `gold N` | Money, added to what the character has | all |
| `ability ID [proficiency]` | Gives the skill at that proficiency (100 if not given), even setting it lower than before | all |
| `faction ID value` | Sets standing with that faction ID | all |
| `var NAME value` | A global script variable of the character | all |
| `race`, `class`, `gender`, `alignment`, `level`, `room` | Shape a test character | `KIT`, `TESTCHAR` only (ignored for new characters) |

Matching automatic kits and the kits the player chose are all applied, after
the origin. A problem (no such item or ability) is logged and the rest of the
kit still applies.

```text
# world/kits/scout.kit
starting -BASECLASS +Fighter +Thief
choose equipment
title A scout's travelling kit
item Dagger
item 2 Torch
gold 20
ability Skill_Recall 50
```

```text
# world/kits/pack.kit  (the alternative in the same group)
starting all
choose equipment
title A pedlar's pack
item catalog:a pedlar's pack
wear no
```

### perks.ini: perks, traits and flaws

Source: [Perks.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Perks.java),
[PerkCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/PerkCmd.java).
Fixture: [p13-perks.js](../../tools/fixtures/fixes/p13-perks.js).
In-game help: `AHELP PERKSINI`.

Perks are choices spent from pools of picks. Traits and flaws are a special pool
bought at creation. The file has three kinds of line (one per line; `\`
continues a line):

| Line | Meaning |
| --- | --- |
| `PICKS=pool every N levels; pool at L1,L2,...` | How many picks each pool gives. `;` separates entries; several `PICKS=` lines add up. `GENERAL every 3 levels` gives a pick at level 3, 6, 9...; `CLASS:Fighter at 1,5,10` gives picks at those levels *of that class* (a `CLASS:name` pool counts that class's own levels, other pools the character level). The word after the number (`levels`) is ignored |
| `TRAITS=N` | Trait points to spend at creation |
| `ID key=value ...` | A perk: its ID, then keys below. The ID is uppercased and becomes the tattoo a character gets |

Perk keys (quote values with spaces):

| Key | Default | Meaning |
| --- | --- | --- |
| `NAME` | the ID without `PERK_`, underscores as spaces, capitalised | Shown name; the perk can be taken by name, ID, or the start of its name |
| `POOL` | `GENERAL` | Which pool pays for it: `GENERAL`, `CLASS`, any name of your own, or `TRAIT` for creation traits and flaws |
| `MASK` | none | Zapper mask the character must pass (`-LEVEL +>=3`) |
| `REQUIRES` | none | Perk IDs (or tattoos) needed first; commas or spaces between |
| `COST` | `1` | Picks (points for `TRAIT`) it costs. A flaw has a cost below 0 and gives points back. A cost of 0 is free |
| `REWARDS` | none | Achievement-style rewards given when taken (`"1 CHARISMA 50 XP"`) |
| `HELP` | none | A line shown in `PERKS` |

A character's picks in a pool are the picks its levels earned, less the cost
of the perks of that pool it took. Perks other than traits are taken after
creation with `PERK NAME`, once enough picks are left; traits are taken in the
`TRAITS` step (take or drop by typing the name, then `DONE`) and their rewards
come at the end of creation. Taking a perk adds its ID as a tattoo, so
`HASPERK(who PERK)` (see [28-mobprog-functions.md](28-mobprog-functions.md#hasperk))
and `-TATTOOS +PERK_PACKMULE` masks work. `MPPERK who PERK` gives a perk from a
script and `MPPERK who -PERK` takes it off (its rewards stay; see
[27-mobprog-commands.md](27-mobprog-commands.md#mpperk)).

```ini
# world/perks.ini
PICKS=GENERAL every 3 levels; CLASS:Fighter at 1,5,10
TRAITS=2
PERK_PACKMULE NAME="Pack mule" POOL=GENERAL HELP="Carry more." REWARDS="50 MAXCARRY"
PERK_IRONWILL NAME="Iron will" POOL=GENERAL MASK="-LEVEL +>=3" REQUIRES=PERK_PACKMULE COST=1
PERK_SHIELDWALL NAME="Shield wall" POOL=CLASS MASK="-CLASS +Fighter"
TRAIT_LUCKY NAME="Lucky" POOL=TRAIT COST=2 REWARDS="1 CHARISMA"
TRAIT_KEEN NAME="Keen eyed" POOL=TRAIT COST=1
FLAW_BADBACK NAME="Bad back" POOL=TRAIT COST=-1
```

At level 1 a Fighter has one `CLASS` pick and no `GENERAL` pick. `PERKS`
lists what the character has, can take and cannot yet (with why), and the
picks left.

### Ready-made characters (player templates)

Source: [PlayerTemplates.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/PlayerTemplates.java),
[Prop_PlayerTemplate](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_PlayerTemplate.java).
Fixture: [p8-templates.js](../../tools/fixtures/fixes/p8-templates.js).
In-game help: `AHELP PLAYERTEMPLATES`.

A template is a catalog mob in the category `PLAYERTEMPLATE` that carries the
property `Prop_PlayerTemplate`. To make one:

1. Build a mob with `CREATE MOB` or `MODIFY MOB`: the race, gender, class and
   class level you want, level, base stats, abilities (with proficiencies),
   expertises, factions, tattoos, inventory (worn items stay worn), money and
   description (see [21-mobs.md](21-mobs.md)). Its name is the template's name
   in the menu.
2. Add the effect `Prop_PlayerTemplate` in its effects editor, with parameters:

   | Parameter | Meaning |
   | --- | --- |
   | `PITCH="..."` | The one-line description in the menu |
   | `LOCK=NAME,GENDER` | Keep the template's name and/or gender; the player is not asked. A `LOCK=NAME` template can be made only once (it is refused if a character of that name exists) |
   | `NAME=RANDOM` | Draw a name in the style of the character's race |

3. Put it in the catalog in that category:

   ```text
   CATALOG CATEGORY PLAYERTEMPLATE
   CATALOG Hale
   ```

`NEW` then lists the templates (`Ready-made characters:`) with `0) Make your own`
unless `CREATIONTEMPLATES=ONLY`. Picking one gives the new character the
template's race, gender, class and level (reached by real level-ups, so hit
points, practices and trains are earned), base stats, abilities (at the higher
of the two proficiencies), expertises, factions, tattoos, items, money and
description. The typed name is kept unless `LOCK=NAME` or `NAME=RANDOM` says
otherwise; the gender is asked unless `LOCK=GENDER`. The `Prop_PlayerTemplate`
itself is not given to the character. Origins, kits, traits, creation
questions and `NEWCHARLEVEL` do not apply to a template character, and its
start room comes from the `START` rules. `JavaScript onCreationStep` is called
with the step `template`.

```ini
# world.ini
CREATIONTEMPLATES=ONLY
START=Yard#1
```

Example: a dwarven inspector, level 3, who cannot be renamed: `PITCH="A tired
inspector of the Yard." LOCK=NAME,GENDER`.

### Review, BACK and QUICK

Source: [CreationReview.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CreationReview.java).
Fixture: [p27-review.js](../../tools/fixtures/fixes/p27-review.js).

Listing `REVIEW` in `STEPS` makes the engine show a character sheet before the
gifts are given: race, class, stats, standing, deity, origin, kits, answers and
the start room. The prompt is `BACK [step], or DONE`. `DONE` (or Enter, `D`,
`Y`) finishes creation. `BACK CLASS` puts the character back as it was when
the class step began and asks that step and every step after it again (stats
are kept); the steps you can go back to are named in the prompt. The review
shows the sheet only to a character made by the questions: template characters
and `QUICK` characters skip it.

`NEW name QUICK` takes every step at random with no review. `NEW name QUICK
template` makes that ready-made character (the name of one of the offered
templates). With `CREATIONTEMPLATES=ONLY` and no template named, a random
template is used.

## Races and classes

A race says what a character is built like and born with: stat adjustments,
senses, body parts, size, lifespan, natural weapons, racial and cultural
abilities, immunities and the clothes it starts in. A class says what a
character does for a living: how its hit points, mana, moves, practices and
trains grow, which skills it qualifies for at which level, what it may wear
and wield, and who may take it.

Both come in two kinds. **Stock** races and classes are Java classes
(`Races/Dwarf.java`, `CharClasses/Fighter.java`). **Generic** ones (`GenRace`,
`GenCharClass`) are data: every field below is stored in the record, so
a world can add new ones or replace stock ones without Java. JavaScript can
also define races and classes (see [05-javascript.md](05-javascript.md)), and
Java can add new kinds (see [32-engine-programming.md](32-engine-programming.md)).

### Stock races

The engine ships about 250 race classes in
[Races/](../../CoffeeMud/com/planet_ink/coffee_mud/Races/): the players' races,
many monster races (animals, dire and giant versions, undead, elementals,
mephits, golems, the were-creatures), and the base classes they share
(`StdRace`, `GenRace`, `PlayerRace`, `Humanoid`, `Animal`...). Only eight are
open to players by default, because their availability does not carry the
"skill only" bit:

| Race | Notes |
| --- | --- |
| `Human` | The only stock race open in fantasy and technological themes |
| `Dwarf`, `Elf`, `Gnome`, `Halfling`, `HalfElf`, `HalfOgre` | Fantasy |
| `Duergar` | Inherits `Dwarf`'s availability, so it is open too, although `Dwarf` is the usual dwarf |

Every other stock race is "skill only": it exists for NPCs, for
polymorph and mixed-race effects, and for characters who unlock it (see
[availability](#who-may-be-what-availability-and-unlocking)). For example `Drow`
is skill-only in this build, although the stock `races.txt` screen still
describes it. `LIST RACES` lists every race the game knows, with its
category; `HELP` shows a race's help text.

### Stock classes

About fifty classes are in
[CharClasses/](../../CoffeeMud/com/planet_ink/coffee_mud/CharClasses/).
They are organised in families by their **base class** (`BASE`):

| Base class | Classes |
| --- | --- |
| `Fighter` | `Fighter`, `Barbarian`, `Cavalier`, `Monk`, `Paladin`, `Ranger` |
| `Mage` | `Mage`, `Abjurer`, `Alterer`, `Conjurer`, `Diviner`, `Enchanter`, `Evoker`, `Illusionist`, `Transmuter`; `Wizard` and `SpecialistMage` are not open to players |
| `Thief` | `Thief`, `Arcanist`, `Assassin`, `Burglar`, `Gypsy`, `Pirate`, `Reliquist`, `Trapper` |
| `Cleric` | `Cleric`, `Doomsayer`, `Healer`, `Missionary`, `Necromancer`, `Oracle`, `Purist`, `Shaman`, `Templar` |
| `Druid` | `Druid`, `Beastmaster`, `Delver`, `Gaian`, `Mer`, `SkyWatcher` |
| `Bard` | `Bard`, `Charlatan`, `Jester`, `Minstrel`, `Prancer` |
| `Commoner` | `Apprentice`, `Artisan`, `Gaoler`, `Sailor`, `Scholar` |
| (each its own) | `Archon` (builders), `PlayerClass` (used when classes are disabled), `Qualifier` and `StdCharClass` (engine placeholders) |

`Apprentice` is the class for "not sure yet": it has the sub-class rule `ANY`,
so under the default `CLASSSYSTEM=SUB` a character may start as an Apprentice
and later train into any class. Use `LIST CLASSES` to list the classes loaded.

### Who may be what: availability and unlocking

Each race and class has an **availability** value: a combination of the theme
bits (fantasy 1, technological 2, heroic 4) and a "skill only" bit (8). It is
the **Availability** field in the editors, with these values:

| Value | Meaning |
| --- | --- |
| 0 | Unavailable |
| 1 to 7 | Open to players in those themes (1 fantasy, 2 tech, 3 fantasy and tech, 4 heroic, 5, 6, 7 all) |
| 8 to 15 | The same themes, but "skill only": not offered to players |

A race or class is offered at creation when its theme bits meet `MUDTHEME` and
it is not skill-only (or has been enabled), and it is not disabled. A skill-only
race can still reach a player three ways:

- **`ENABLE=RACE_OGRE, CHARCLASS_FIGHTER OGRE`** in `coffeemud.ini` or
  `world.ini`: the race is open to everyone, and the second entry lets Ogres be
  Fighters. `RACE_*` and `CHARCLASS_*` flags work the same way in `DISABLE`
  (`DISABLE=RACE_HALFELF`, `CHARCLASS_SKYWATCHER`, or with a `*` ending to take
  every ID with that start). `DISABLE=STDRACES` and `STDCLASSES` close all
  stock ones, which suits a world that builds its own.
- **An unlock tattoo.** A tattoo named `RACE_<ID>` (for example `RACE_DROW`) or
  `CHARCLASS_<ID>` on the character or its profile opens that race or class for
  it; `CHARCLASS_ALL` opens every class. An achievement can give such a tattoo
  with a `TATTOO(ID=RACE_DROW DESC=...)` reward (the `REWARDS` notes at the top
  of `resources/achievements.ini` show the quoting; see
  [Achievements](#achievements-and-titles)). The `CHARCLASS_<ID>+<STAT>` form
  waives that stat requirement, and `CHARCLASS_<ID>+RACE_<ID>` waives the race
  requirement.
- **A template.** A ready-made character takes its race and class directly, so
  a template can be of a race that creation does not offer.

With `SHOWLOCKED=YES`, a race or class that is kept behind such a tattoo and has
an **Unlock hint** appears in the creation lists as `Name (locked: hint)`. Write
the hint as a lead, not a spoiler ("reach the Underdark").

### Creating, converting and destroying

All of these need the security flag `CMDRACES` or `CMDCLASSES`. The editors
write to the world files only in builder mode (see
[02-world-workflow.md](02-world-workflow.md)), where the player is an Archon.

| Command | Effect |
| --- | --- |
| `CREATE RACE <ID>` | A new generic race with that ID (no spaces), and the editor opens. If `<ID>` is a stock race, asks to convert it to a `GenRace` first ("special functionality of the race may be lost") |
| `MODIFY RACE <ID>` | Edit a generic race. A stock race is refused with "Use CREATE RACE `<ID>` to convert it" |
| `DESTROY RACE <ID>` | Remove a generic race; destroying a converted stock race restores the Java one |
| `CREATE MIXEDRACE <MOTHER ID> <FATHER ID>` | Make (and keep) the mixed race the engine would invent for that pair; `RACEMIXING` controls what mixing yields |
| `CREATE CLASS <ID>` | The same for classes: a new `GenCharClass`, or conversion of a stock class (`makeGenCharClass`) |
| `MODIFY CLASS <ID>` | Edit a generic class |
| `DESTROY CLASS <ID>` | Remove it |
| `LIST RACES`, `LIST RACECATS`, `LIST CLASSES` | See what is loaded |

Converting a stock race or class copies what the Java code exposes into the
fields, and the Java behaviours it does not expose are lost (the dwarf's
infravision, a thief's level-based defence...). The editor's **Stat-Modifying
Class**, **Special Events Class**, **Health Race**, **Event Race** and **Weapons
Race** fields let a generic copy borrow the original's Java behaviour: give
the original's full class name (`com.planet_ink.coffee_mud.Races.Dwarf`) when the
generic one has taken its ID.

In builder mode, SAVE (and quitting) writes each generic race and class to
`world/custom/races/<ID>.cmare` and `world/custom/classes/<ID>.cmare` (an
older single `world/custom.cmare` is still read); on a normal start they are
loaded with the world. Edit them in the game rather than by hand; see
[Persistence notes](#persistence-notes).

### GenRace fields

Source: [GenRace.java](../../CoffeeMud/com/planet_ink/coffee_mud/Races/GenRace.java),
editor: `modifyGenRace` in [CMGenEditor.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CMGenEditor.java).
Each field is a numbered prompt; type its number to edit it, Enter to leave.
The stat code is what scripts use with `race.setStat(code, value)` and what the
record stores. Indexed codes carry a trailing number (`GETRABLE0`, `GETRABLE1`).

| # | Prompt | Stat code | Default | Meaning |
| --- | --- | --- | --- | --- |
| 1 | Name | `NAME` | the ID | Name shown to players. The record's `ID` is fixed at creation |
| 2 | Racial Category | `CAT` | `Unknown` | Group shared by related races (`Elf`, `Dwarf`, `Human`...). Used by class race lists, `START_<category>`, `RACEMIXING`, name styles, masks. `new <name>` makes a category; `?` lists them |
| 3 | Base Weight | `BWEIGHT` | 60 | Minimum weight, pounds |
| 4 | Weight Variance | `VWEIGHT` | 10 | Up to this much more is added at random |
| 5 | Base Male Height | `MHEIGHT` | 24 | Minimum height, inches |
| 6 | Base Female Height | `FHEIGHT` | 24 | Minimum female height |
| 7 | Height Variance | `VHEIGHT` | 5 | Random addition |
| 8 | Availability | `AVAIL` | 0 | Theme and skill-only bits; see above. A new race is **unavailable** until you set this. In a file or script the value may be a number or bit names (`FANTASY\|SKILLONLY`) |
| 9 | Disabled | `DISFLAGS` | none | Toggle flags by name: `CLASSLESS` (no classes), `LEVELLESS` (never levels), `EXPLESS` (earns no experience), `CHARMLESS`, `CHILDLESS` (cannot breed), `INFATIGUEABLE` (never tires). Numbers 1, 2, 4, 8, 16, 32 |
| 10 | Can breathe | `BREATHES` | AIR | Resources the race can breathe (`AIR`, `WATER`, `ANYTHING`...). Add or remove by name; `?` lists. Be kind |
| 11 | Leaving text | `LEAVE` | `leaves` | Verb when it leaves a room |
| 12 | Arriving text | `ARRIVE` | `arrives` | Verb on arrival |
| 13 | Health Race | `HEALTHRACE` | none | A race whose health descriptions in combat this race uses |
| 14 | Event Race | `EVENTRACE` | none | A Java race whose special events this one runs too (a chicken laying eggs). Usually blank |
| 15 | Body Parts | `BODY` | torso 1, head 1, neck 1, arms 2, hands 2, legs 2, feet 2, eyes 2, ears 2, nose 1, mouth 1, waist 1 | How many of each body part exist (for wearing and amputation); see [Body parts](#body-parts-wear-locations-and-ageing-charts) |
| 16 | UNWearable locations | `WEAR` | none | Wear locations this body cannot use even with the limbs |
| 17 | Aging Chart | `AGING` | 0,1,3,15,35,53,70,74,78 | Nine ascending years, the age each age category starts |
| 18 | Never create corpse | `BODYKILL` | false | The body vanishes at death |
| 19 | Rideable Player | `CANRIDE` | false | Characters of this race can be ridden |
| 20 | XP Adjustment % | `XPADJ` | 0 | Percent added to (or, negative, taken from) experience gained or lost, including role-play experience |
| 21 | PhysStat Adjustments | `ESTATS` | none | Adjustments to level, senses (`SENSES`), armor, damage, attack, disposition (`DISPOSITION`), weight, height. Type a stat name, then a value |
| 22 | CharStat Adjustments | `ASTATS` | none | Adjustments to base stats, saves, max-stat adjustments, crit, age, faith and the like (descriptions such as `STRENGTH`, `SAVE VS FIRE`, `MAX STRENGTH ADJ.`) |
| 23 | CharStat Settings | `CSTATS` | none | The same stats, but set to a value rather than adjusted |
| 24 | CharState Adjustments | `ASTATE` | none | Adjustments to maximum hit points, mana, moves, hunger, thirst, fatigue. Hunger or thirst above two billion disables them; fatigue below minus two billion disables fatigue |
| 25 | New Player CharState Adj. | `STARTASTATE` | none | The same, applied once when a new character of the race starts |
| 26 | Resources | `NUMRSC`, `GETRSCID<n>`, `GETRSCPARM<n>` | none | Raw materials the corpse yields when butchered. `new <item>` adds a copy of an item from your inventory; name one to remove it |
| 27 | Outfit | `NUMOFT`, `GETOFTID<n>`, `GETOFTPARM<n>` | none | Items a new character of the race starts in, and `OUTFIT` gives. `new <item>` from your inventory |
| 28 | Natural Weapon(s) | `WEAPONCLASS`, `WEAPONXML` | none (bare hands) | The weapon(s) used unarmed; when several are listed one is chosen at random. `new <weapon>` from your inventory |
| 29 | Weapons Race | `WEAPONRACE` | none | A race whose random natural weapon is used |
| 30 | Racial Abilities | `NUMRABLE`, `GETRABLE<n>`, `GETRABLELVL<n>`, `GETRABLEQUAL<n>`, `GETRABLEPROF<n>`, `GETRABLEPARM<n>` | none | Abilities members have. See below |
| 31 | Cultural Abilities | `NUMCABLE`, `GETCABLE<n>`, `GETCABLEPROF<n>`, `GETCABLELVL<n>`, `GETCABLEGAIN<n>`, `GETCABLEPARM<n>` | none | Skills the culture teaches. See below |
| 32 | Racial Effects | `NUMREFF`, `GETREFF<n>`, `GETREFFLVL<n>`, `GETREFFPARM<n>` | none | Properties the race wears permanently. See below |
| 33 | Racial Immunities | `NUMIABLE`, `GETIABLE<n>` | none | Ability IDs this race is wholly and silently immune to |
| 34 | Name style | `NAMESTYLE` | none | The style used when a name is drawn for this race (`NEW *`, `NAME=RANDOM`, generators); see [12-generation.md](12-generation.md) |
| 35 | Unlock hint | `UNLOCKHINT` | none | Text shown beside the race when `SHOWLOCKED=YES` |
| 36 | Extra Trains/1stLvl | `FSTTRAN` | 0 | Trains added at creation |
| 37 | Extra Practices/1stLvl | `FSTPRAC` | 0 | Practices added at creation |
| 38 | Extra Prac/Level | `LVLPRAC` | 0 | Practices added each level |
| 39 | Extra Trains/Level | `LVLTRAIN` | 0 | Trains added each level |
| 40 | Script | `SCRIPT` | none | A MOBPROG (or `LOAD=file`) that runs on each mob of the race as a hidden effect; its triggers (`RAND_PROG`, `GREET_PROG`...) run with `$i` the mob. Never saved with the character. See `AHELP SKILLSCRIPTS` |
| 41 | Age effects | `AGEEFFECTS` | none | Stat changes by age category in place of the coded ones; see [Ageing](#ageing) |
| 42 | Health chart | `HEALTHCHART` | none | Eleven comma-separated combat health lines (worst first), `<MOB>` for the name, `\|` for alternatives |
| 43 | Corpse descriptions | `CORPSEBLURBS` | none | `CAUSE=phrase\|alternative; ALL=phrase`, the corpse text by cause of death, for example `BURNING=molten gears;ALL=a heap of springs` |

Not in the editor: `HELP` (a help entry stored in the record). Fields 21 to
25 take stat names; the editor lists the valid ones on a bad entry. Racial and
cultural abilities, effects, and the fields that need an item in your inventory
(`new <item>`) are edited by naming the entry to remove it or a new name to add
it, with follow-up prompts.

**Racial Abilities** are skills with command words. Each has: the *level* (a
player level, not a class level) at which it is gained, whether it is
*automatically gained* (answer N to merely qualify for it), the permanent
*proficiency*, and default *parameters*. They are given when the race starts,
and from the level named. Auto-invoked abilities cannot be named here, except
languages.

**Cultural Abilities** are mapped to the race like a class's abilities: each has
a default proficiency, a *character level* (0 means from the start), whether it
is auto-gained, and parameters. The character keeps them. Use these for a
culture's language and crafts (the stock Dwarf has `Dwarven` at 100 and `Mining`
at 50).

**Racial Effects** are abilities (usually properties such as `Prop_...` or
`Skill_Stonecunning`) worn permanently from a player level, with parameters. This
is how a race gets traits that are not stat numbers.

An ability without command words cannot be a Racial Ability (the editor says
it "has no trigger/command words"); make it a Racial Effect instead.

### GenCharClass fields

Source: [GenCharClass.java](../../CoffeeMud/com/planet_ink/coffee_mud/CharClasses/GenCharClass.java),
editor: `modifyGenClass`. As with races, fields are numbered prompts (the numbers
move with the number of class names and security sets).

| Prompt | Stat code | Default | Meaning |
| --- | --- | --- | --- |
| Number of Class Names | `NUMNAME` | 1 | How many display names the class has |
| Class Name / Class Name #n | `NAME<n>` | the ID | Display names; with several, each applies from its class level |
| Class Name #n class level | `NAMELEVEL<n>` | 0, +1 each | The class level at which that name starts. Must be above the previous one |
| Level Cap | `LEVELCAP` | -1 | The most levels in this class (-1 unlimited) |
| Base Class | `BASE` | `Commoner` | The class family. For a new base class set it to the class's own ID; a subclass names its base. See [class systems](#class-systems-subclasses-and-multi-classing) |
| Availability | `PLAYER` | 0 | As for races. A new class is **unavailable** until set |
| HP/Level Formula | `HITPOINTSFORMULA` | `((@x6<@x7)/3)+(1*(1?6))` | Hit points per level; variables below |
| Mana/Level Formula | `MANAFORMULA` | `((@x4<@x5)/6)+(1*(1?3))` | Mana per level |
| Prac/Level | `LVLPRAC` | 0 | Extra practices per level |
| Extra Trains/Level | `LVLTRAIN` | 0 | Extra trains per level |
| Attack/Level | `LVLATT` | 0 | Extra attack bonus per level, on top of the attribute's |
| Attack Attribute | `ATTATT` | Strength | The stat that drives the attack bonus (and the mana formula's `@x6`) |
| Practices/1stLvl | `FSTPRAC` | 5 | Practices at creation |
| Trains/1stLvl | `FSTTRAN` | 3 | Trains at creation |
| Levels/Dmg Pt | `LVLDAM` | 10 | One extra point of base damage per this many levels |
| Move/Level Formula | `MOVEMENTFORMULA` | `5*((@x2<@x3)/18)` | Moves per level |
| Armor Restr. | `ARMOR` | ANY | `ANY`, `CLOTH`, `LEATHER`, `NONMETAL`, `VEGAN`, `METALONLY`, `OREONLY`: armour that makes the class's skills fumble |
| Armor restricts only spells | `ARMORMINOR` | false | If true, the restriction applies only to spells, chants and prayers |
| Limitations | `STRLMT` | none | Free text for the help screen (limits are not enforced by it) |
| Bonuses | `STRBON` | none | Free text for the help screen |
| Qualifications | `QUAL` | none | A zapper mask a character must pass to take the class |
| Min. Stats | `NUMMINSTATS`, `GETMINSTAT<n>`, `GETSTATMIN<n>` | none | Minimum base stats (after the race's adjustment) to take the class |
| Required Races | `RACQUAL` | none | Race IDs, names or categories allowed, `All`, or `-name` to exclude. **An empty list admits no race** |
| PhysStat Adjustments | `ESTATS` | none | As for races |
| CharStat Adjustments | `ASTATS` | none | As for races (including max-stat adjustments) |
| CharStat Settings | `CSTATS` | none | As for races |
| CharState Adjustments | `ASTATE` | none | Maximum hit points, mana, moves |
| New Player CharState Adj. | `STARTASTATE` | none | Added once to a new character |
| Extra CharClass Flags | `DISFLAGS` | 0 | 1 raceless, 2 leveless, 4 expless, 8 "thin" qualify list. The editor toggles the first three |
| Weapon Restr. | `NUMWEP`, `GETWEP<n>` | none | Weapon classes the class fumbles with: `AXE`, `BLUNT`, `EDGED`, `FLAILED`, `HAMMER`, `KARATE`, `POLEARM`, `RANGED`, `SWORD`, `DAGGER`, `STAFF`, `THROWN` |
| Weapon Materials | `NUMWMAT`, `GETWMAT<n>` | none | Material types the class's weapons must be made of to avoid fumbling |
| Outfit | `NUMOFT`, `GETOFTID<n>`, `GETOFTPARM<n>` | none | Items given at creation |
| Starting Money | `MONEY` | none | A number or `10 gold`; given at creation |
| Unlock hint | `UNLOCKHINT` | none | Shown with `SHOWLOCKED=YES` |
| Script | `SCRIPT` | none | A MOBPROG that runs on each mob of the class as a hidden effect |
| Stat-Modifying Class | `STATCLASS` | none | A Java class whose special stat code runs too (use the full class name when a generic class has taken the ID) |
| Special Events Class | `EVENTCLASS` | none | A Java class for special events |
| Sub Class Switch Rule | `SUBRUL` | `BASEONLY` | `NONE`, `ANY`, `BASEONLY`; see below |
| Max Non-Crafting Skills | `MAXNCS` | from ini | Cap on common skills qualified for by every class, other than crafting |
| Max Crafting Skills | `MAXCRS` | from ini | Same for crafting skills |
| Max All-Common Skills | `MAXCMS` | from ini | Same for all common skills together |
| Max Languages | `MAXLGS` | from ini | Cap on languages learnable (other than the race's) |
| Class Abilities | `NUMCABLE`, `GETCABLE<n>`... | none | See [Qualifying abilities](#qualifying-abilities) |
| Number of Security Code Sets | `NUMSSET`, `SSET<n>`, `SSETLEVEL<n>` | 0 | Security flags (see [19-configuration-and-administration.md](19-configuration-and-administration.md)), each set applying from a class level; for builder classes like `Archon` |
| (no prompt) | `HELP` | none | Stored help text |

The three formulas use the `AHELP MATH_FORMULA` syntax: `+ - * /`, brackets,
`a<b` (the lesser), `a>b` (the greater), and `a?b` (a random number from `a` to
`b`). A formula is worked strictly left to right, so bracket everything. The
variables (`@x1`, `@x2`...) differ by formula, and are stats after all
adjustments; a "max" is `BASEMAXSTAT` plus the character's max-stat adjustment
for that stat:

| Formula | @x1 | @x2 / @x3 | @x4 / @x5 | @x6 / @x7 | @x8 | @x9 |
| --- | --- | --- | --- | --- | --- | --- |
| Hit points (`HITPOINTSFORMULA`) | level | Strength / its max | Dexterity / its max | Constitution / its max | Wisdom | Intelligence |
| Mana (`MANAFORMULA`) | level | Wisdom / its max | Intelligence / its max | Attack attribute / its max | Charisma | Constitution |
| Moves (`MOVEMENTFORMULA`) | level | Strength / its max | Dexterity / its max | Constitution / its max | Wisdom | Intelligence |

The default hit point formula, `((@x6<@x7)/3)+(1*(1?6))`, is a third of
Constitution (capped at its max) plus a d6, each level. A world can add to
every class's formulas with the text after the comma in `STARTHP`, `STARTMANA`
and `STARTMOVE` (`STARTHP=20,+(@x1/20)`); `@STAT(NAME)` and `@BASESTAT(NAME)`
read other stats (see [Stats](#stats-and-derived-values)).

A minimal new base class:

```text
CREATE CLASS Boxer
```

then in the editor: Base Class `Boxer`, Availability `1`, Required Races `All`,
Attack Attribute `STRENGTH`, then add class abilities. Without Required Races the
class is selectable by nobody, and with the default Base Class `Commoner` it
would not appear at creation under `CLASSSYSTEM=SUB`.

### Class systems, subclasses and multi-classing

`CLASSSYSTEM` (in `coffeemud.ini` or `world.ini`) decides how a character
chooses and changes class. It is a word, optionally followed by a number or
group, and the comma options `GAIN=n` (trains to gain a new class, default 1)
and `SWITCH=n` (trains to switch back to a class you have, default 1):

| Value | Rule |
| --- | --- |
| `SUB` (default) | Start in any base class (or a class whose sub-class rule is `ANY`), and later train into other classes of the same base class. `SUB-n`: at most `n` classes |
| `NO` | No multi-classing; pick any player class and keep it |
| `NO-BASE` | As `NO`, but only base classes may be picked |
| `NO-SUBONLY` | As `NO`, but only sub-classes may be picked |
| `NO-<class>` | Everyone must be that one class |
| `NO-GRP-<base class>` | Everyone must pick within that class family |
| `MULTI`, `MULTI-n` | Any class at any time (at most `n`) |
| `APP-SUB`, `APP-NO`, `APP-MULTI` | Everyone starts as an Apprentice, then as `SUB`, `NO` (one change allowed) or `MULTI` |
| `DISABLED` | No classes (the same as `DISABLE=CLASSES`; characters get `PlayerClass`) |

Each class carries a **sub-class switch rule**:

| `SUBRUL` | Meaning |
| --- | --- |
| `BASEONLY` (default) | Characters of this class may train only to a class of the same base |
| `ANY` | May train to any class (the Apprentice) |
| `NONE` | Dead end: cannot train away |

In play a player changes class with `TRAIN <class>`, which costs a training
session (`CLASSSYSTEM` `GAIN=`) and starts the new class at level 0; from then
on levels go into the new class, and the character keeps the skills of the old
one up to the level at which it switched. `QUALIFY` lists what the character
qualifies for and `WILLQUALIFY` shows what a class and level will give.
`DISABLE=CLASSTRAINING` removes `TRAIN <class>` so that classes can change only
by scripts. To design a class tree, make a base class and give each subclass
`BASE=<base>`; under `SUB` they can be reached from the base, while `Min. Stats`,
`Required Races` and `Qualifications` can further limit who qualifies.

### Qualifying abilities

A class lists its **Class Abilities**: each is a skill, spell, prayer, song or
other ability with command words. The editor shows a table with the columns
Lvl, Skill, Proff, Gain, Secret, Parm, Preq, Mask; adding an ability asks for:

| Prompt | Stat code | Meaning |
| --- | --- | --- |
| level of this skill | `GETCABLELVL` | Class level at which the character qualifies (1 or more; 0 aborts) |
| (default) proficiency | `GETCABLEPROF` | Proficiency when first gained (default 0) |
| (maximum) proficiency | `GETCABLEMAXP` | Highest proficiency practice can raise it to (default 100) |
| automatically gained | `GETCABLEGAIN` | Y: the character is given the skill at that level. N: only *qualified for*; it must be learned from a teacher, a scroll or a quest |
| Public, Secret or Masked | `GETCABLESECR` | `PUBLIC` is listed and taught normally; `SECRET` is unknown to characters and teachers; `MASKED` is secret until the mask is met |
| properties | `GETCABLEPARM` | Parameters the ability starts with |
| pre-requisites | `GETCABLEPREQ` | A comma list of skill IDs (a trailing `*` for a family) with an optional minimum proficiency in brackets: `Skill_Trip(50),Fighter_*(10)`. These chains are what `SKILLTREE` draws |
| requirement mask | `GETCABLEMASK` | A zapper mask the character must also pass |

Auto-gained abilities are given as soon as the class level is reached; the
rest appear in `QUALIFY`. Races can
qualify for abilities in the same way through cultural abilities, and a
**qualify-all** list, edited with `CREATE ALLQUALIFY`, `MODIFY ALLQUALIFY` and
`LIST ALLQUALIFYS` and stored in `resources/skills/allqualifylist.txt`, makes
an ability available to every class or to each of several (the way every class
gets common skills). Ability lists for the other kinds of grant are in
[25-abilities-and-effects.md](25-abilities-and-effects.md).

A world's achievements can grant abilities too (`17 Spell_Light` gives a
spell at level 17, optionally with `AUTOGAIN`, `MASK`, `PARMS`, `PREREQS`),
which is how a class-less skill can be rewarded; see
[Achievements](#achievements-and-titles).

### Body parts, wear locations and ageing charts

**Body parts** (`BODY`) count how many of each part a member has, in this order:
`ANTENNA`, `EYE`, `EAR`, `HEAD`, `NECK`, `ARM`, `HAND`, `TORSO`, `LEG`, `FOOT`,
`NOSE`, `GILL`, `MOUTH`, `WAIST`, `TAIL`, `WING`. The editor asks for a part name
and then a number (0 removes it). The counts decide which locations can be
worn on and how many limbs can be lost. A snake would have a head, a torso and
no arms; an arachnid has many legs. Together with **UNWearable locations**
(`WEAR`) this decides what can be worn: a race that has hands but wears no
gloves lists the glove location there.

**Ageing chart** (`AGING`): nine years, ascending, for the age categories:

| Index | Category | Stock default (years) |
| --- | --- | --- |
| 0 | Infant | 0 |
| 1 | Toddler | 1 |
| 2 | Child | 3 |
| 3 | Young adult | 15 |
| 4 | Adult (`MATURE`) | 35 |
| 5 | Mature (`MIDDLEAGED`) | 53 |
| 6 | Old | 70 |
| 7 | Venerable | 74 |
| 8 | Ancient | 78 |

A character's age category is the last entry its age has reached; "ancient"
characters are shown with a Roman numeral for every further `Ancient -
Venerable` span. A new character begins at the *young adult* age, born on the
day it is created (by the game calendar of its start room). A value below zero
in a file means "never" (`Integer.MAX_VALUE`). See [Ageing](#ageing) for
effects.

