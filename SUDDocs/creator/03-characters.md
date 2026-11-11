# Characters, game start and progression

[Handbook](README.md) · Feature families [P](feature-index.md#p-characters-and-progression), [S](feature-index.md#s-saves-and-world-files)

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
  - [cultures.ini: cultures](#culturesini-cultures)
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
- [Command quick reference](#command-quick-reference)
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
| 7a | `CULTURE` | Only when listed in `STEPS`: asked when `cultures.ini` has a culture with `CREATION=YES` the character may take up | [cultures.ini](#culturesini-cultures) |
| 8 | `KIT` | A menu for each `choose` group of starting kits that fits the character; a lone kit in a group is taken without asking | [Kits](#kits) |
| 9 | `TRAITS` | Take or drop traits and flaws from `perks.ini` by name, then `DONE`; the points must not be overspent (skipped when no trait is open to the character, or `TRAITS=RANDOM`/`SKIP`) | [perks.ini](#perksini-perks-traits-and-flaws) |
| 10 | World questions | `creation.ini` `[NAME]` sections, wherever `STEPS` puts them | [creation.ini](#creationini-steps-and-questions) |
| 11 | `FACTIONS` | For each faction that offers a choice for this character, a pick. Factions with a single choice or an automatic default are set silently. Runs `FACTIONS` scripts. Then: class start-up (class abilities, first-level practices and trains), the class outfit, the start room, birthday and age, the class's starting money | faction files, [09-society.md](09-society.md) |
| 12 | `DEITY` | Only when `DEITYPOLICY` has a `REQUIREDMASK` the character meets and some deity qualifies | `DEITYPOLICY` |
| 13 | `REVIEW` | Only when `REVIEW` is listed in `STEPS` | [Review, BACK and QUICK](#review-back-and-quick) |
| 14 | Finish | The gifts below, the `newchardone.txt` screen, then the character enters the world | |

Steps 3 to 6, 11 and 12 keep this relative order whatever `creation.ini` says
(each needs the one before; for example, class qualification needs the stats).
`ORIGIN`, `CULTURE`, `KIT`, `TRAITS` and the world's questions can be placed anywhere that
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
2. `CHARCREATIONSCRIPTS` `ORIGIN:` scripts, then the culture chosen at the
   `CULTURE` step, taken up by the same rules as later (after the origin's
   `CULTURE`, so it is the one held).
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

Some gifts come earlier, from the class and race themselves: the race's
abilities (when gender is settled), the race outfit and `STARTINGITEMS` (at the
class step), and the class's abilities, first-level practices and trains,
outfit and starting money (end of the `FACTIONS` step).

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
`RACE`, `GENDER`, `STATS`, `CLASS`, `ORIGIN`, `FACTIONS`,
`CHARLOGIN`, `ALLCHARLOGIN` and `END`; the default is `END`. (`AUTHENTICATE` is
accepted but nothing runs it.) A code may be
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
| `@DeityList@` | Every deity in the world (not filtered by the character) |
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
| `MASK` | Zapper mask: who may take it, as `-RACE +Human +HalfElf` |
| `UNLOCK` | A tattoo the character or its profile must have (for example one a profile achievement gives); otherwise the origin is hidden |
| `START` | A room ID (`Area#12`): the character's start room. Logged if no such room |
| `ITEMS` | Comma list of catalog item names or item class IDs, a count first for more than one (`2 Torch, a travelling cloak`). The catalog is tried before the class IDs; the count is limited to 1 to 100. Logged if neither exists |
| `ABILITIES` | Comma list of `ID proficiency` (`Prayer_Bless 25`). Proficiency defaults to the ability's own. A skill the character has is raised, never lowered |
| `FACTIONS` | Comma list of `faction ID-or-name amount`; the amount is added to the character's standing |
| `TATTOOS` | Comma- or space-separated tattoos to add (uppercased) |
| `MONEY` | A number, in the character's currency |
| `SCRIPT` | A MOBPROG, `ONCE_PROG 100;mpecho Welcome home.;~` (lines between semicolons) or `LOAD=file`. Run once, as the character's creation script |
| `CULTURE` | A culture of `cultures.ini` the character is of from the start, adopted by the same rules as later ([cultures](#culturesini-cultures)), so it can be left again. Logged if it cannot be adopted |

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
MASK=-RACE +Human +HalfElf
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

### cultures.ini: cultures

Source: [Cultures.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Cultures.java).
Fixture: [p11-cultures.js](../../tools/fixtures/fixes/p11-cultures.js).
In-game help: `HELP CULTURE`.

An origin is where a character comes from, once. A culture is how they live:
a source of skills and languages of its own, which they can take up and leave
in play. `world/cultures.ini` has an `[ID]` section for each, in the format of
`origins.ini`:

| Key | Meaning |
| --- | --- |
| `NAME`, `DESCRIPTION` | As for origins |
| `MASK` | Who may adopt it |
| `ABILITIES` | Comma list of `ID proficiency`, languages too (`Elvish 75, Fishing 40`) |
| `TATTOOS` | Tattoos it gives |
| `ADOPT` | `COMMAND`: the player may take it up with `CULTURE ADOPT`. Otherwise it comes from an origin, a script or a mentor (`MPCULTURE`) |
| `LEAVE` | `NEVER`: it cannot be left, nor another adopted, but by a script's `FORCE` |
| `KEEP` | Abilities that stay on leaving, as a mother tongue |
| `TRAINED` | `KEEP` (the default): an ability trained past what the culture gave stays on leaving, at its trained proficiency. `LOSE`: it goes too |
| `SPEAKS` | A language the character speaks once they take the culture up (one it gave, or theirs already) |
| `CREATION` | `YES`: offered at the `CULTURE` step of character creation (list `CULTURE` in `creation.ini`'s `STEPS`) |

The rules, the same at creation and later:

- A character holds one culture at a time, as the tattoo `CULTURE_<ID>` (for
  masks: `-TATTOOS +CULTURE_RIVERFOLK`). Adopting another leaves the one held
  first, under its rules.
- Adopting gives only what the character lacks, and records what it gave in
  their story records (saved with them). An ability they had already, or learn
  otherwise, is theirs: the culture neither raises it nor takes it.
- Leaving takes away exactly what it gave, but `KEEP` and what was trained past
  the culture's gift (unless `TRAINED=LOSE`), and the tattoos it gave.
  Adopting again gives again what is missing.
- Each adopting and leaving, and how (`origin`, `command`, `script`, `force`),
  is kept in the story records' history (the last 20).

```ini
# world/cultures.ini
[RIVERFOLK]
NAME=River folk
DESCRIPTION=Boat people of the delta.
ABILITIES=Fishing 40, Elvish 75, Skill_Swim 60
KEEP=Elvish
ADOPT=COMMAND

[CLOISTER]
NAME=The cloister
ABILITIES=Skill_Climb 30
LEAVE=NEVER
```

`CULTURE` shows the character's culture and what it gave; `CULTURE LIST` every
culture and whether they may adopt it, or why not. A script adopts with
`MPCULTURE $n ADOPT CLOISTER` and leaves with `MPCULTURE $n LEAVE` (`FORCE`
past `LEAVE=NEVER`); JavaScript with `cm.adoptCulture(mob, id, force)` and
`cm.leaveCulture(mob, force)`. `WORLD CHECK` names an unknown ability and an
origin's `CULTURE` that names no culture.

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
| `TREE` | none | The tree the perk is in (see [Build trees](#build-trees)) |
| `ROOT` | `NO` | `YES`: a way into its tree, taken without a linked perk |
| `LINKS` | none | Perks of the same tree it is next to; a link goes both ways |
| `REQUIRESANY` | none | Perk IDs (or tattoos) of which one is needed |
| `GROUP` | none | Only one perk of a group can be held (`GROUP=AFFINITY`) |
| `EFFECTS` | none | Effects held while the perk is (`Prop_SpellAdder` syntax: `Prop_Adjuster;STR+1`) |
| `REFUND` | as `REFUNDS=` | `YES` or `NO`: whether `PERK REFUND` can give it back |

Two more lines: `REFUNDS=YES` lets every perk (but traits) be given back unless
its own `REFUND=NO` says otherwise; without it, none can be unless its own
`REFUND=YES` says so.

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
PERK_PACKMULE NAME="Pack mule" POOL=GENERAL HELP="Carry more." REWARDS="1 STRENGTH"
PERK_IRONWILL NAME="Iron will" POOL=GENERAL MASK="-LEVEL +>=3" REQUIRES=PERK_PACKMULE COST=1
PERK_SHIELDWALL NAME="Shield wall" POOL=CLASS MASK="-CLASS +Fighter"
TRAIT_LUCKY NAME="Lucky" POOL=TRAIT COST=2 REWARDS="1 CHARISMA"
TRAIT_KEEN NAME="Keen eyed" POOL=TRAIT COST=1
FLAW_BADBACK NAME="Bad back" POOL=TRAIT COST=-1
```

At level 1 a Fighter has one `CLASS` pick and no `GENERAL` pick. `PERKS`
lists what the character has, can take and cannot yet (with why), and the
picks left.

#### Build trees

Fixture: [a05-perks.js](../../tools/fixtures/fixes/a05-perks.js).

Perks with the same `TREE` make a tree. A `ROOT` perk can be taken at any time
(with a pick and its other rules met); any other perk of the tree only when the
character holds a perk it `LINKS` to, or that links to it. A tree needs at least
one root. A pool can be given to one tree, so the tree has picks of its own.

```ini
# world/perks.ini
REFUNDS=YES
PERK_SPARK  NAME="Spark"  POOL=FIRE TREE=Fire ROOT=YES LINKS=PERK_FLAME EFFECTS="Prop_Adjuster;STR+1"
PERK_FLAME  NAME="Flame"  POOL=FIRE TREE=Fire LINKS=PERK_BLAZE,PERK_EMBER
PERK_BLAZE  NAME="Blaze"  POOL=FIRE TREE=Fire GROUP=AFFINITY
PERK_EMBER  NAME="Ember"  POOL=FIRE TREE=Fire GROUP=AFFINITY
PERK_WARD   NAME="Ward"   POOL=GUILD REQUIRESANY=PERK_BLAZE,PERK_EMBER REFUND=NO
```

| Rule | What the player sees when it is not met |
| --- | --- |
| Next to a perk held (`LINKS`) | `not next to a perk you have in Fire (Spark or Blaze or Ember)` |
| One of several (`REQUIRESANY`) | `needs one of Blaze, Ember` |
| One of a group (`GROUP`) | `you have Blaze, and only one of affinity can be held` |

**Held effects.** A perk's `EFFECTS` are put on when it is taken and come off
when it goes. As with a rank, a character already holding an effect of the same
kind from elsewhere keeps that one, and only the perk's own effect is ever taken
off. When perks.ini changes, the effects follow at the next login or `PERKS`:
an effect added to a perk is put on, one taken off it comes off.

**Giving perks back.** `PERK REFUND name` gives a perk back, and its pick with
it, when the perk can be refunded and nothing the character holds stands on it:
a perk that `REQUIRES` it, one whose `REQUIRESANY` only it meets, or a perk of
its tree that would be cut off from every root without it
(`first give back what stands on it: Blaze`). Give back the leaves first. The
perk's `REWARDS` stay; its effects go.

**A pool of its own (secondary trees).** A pool need not come from levels:
`MPPERK $n PICKS FIRE +1` gives a pick of `FIRE` (from a boss's death, a
track's reward script, a quest), `-1` takes one, and a plain number sets how
many scripts have given. `MPPERK $n CLEAR GUILD` takes back every `GUILD` perk
(when the character leaves the guild, say) and leaves the other pools alone;
`MPPERK $n PICKS GUILD 0` takes the guild's picks too.

**A perk taken out of perks.ini.** The character keeps its tattoo (masks and
`HASPERK` still see it), its effects come off at the next login or `PERKS`, its
pick is free again, and `PERKS` lists it under `No longer in this world`.

**Inspection.** `PERKS` marks each perk's tree and which can be given back.
`PERKS TREE Fire` shows the tree: `[x]` held, `[ ]` can be taken, `[-]` not
yet (with why), the ways in, and what each perk is next to. `WORLD CHECK`
(the `DATA` part) reports a tree with no root, a link to a perk that is not
there or is in another tree, `ROOT` or `LINKS` without a `TREE`, a trait in a
tree or group, a perk defined twice and a key it does not know.

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
| 1 to 7 | Open to players in those themes: 1 fantasy, 2 technological, 3 fantasy and technological, 4 heroic, 5 heroic and fantasy, 6 heroic and technological, 7 all |
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
| 24 | CharState Adjustments | `ASTATE` | none | Adjustments to maximum hit points, mana, moves, hunger, thirst, fatigue. A hunger or thirst maximum above about a billion (`Integer.MAX_VALUE/2`, as the stock plant and stone races use) disables them; a fatigue maximum below `Long.MIN_VALUE/2` disables fatigue |
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

Not in the editor: `HELP` (a help entry stored in the record). Extra fields can
be added to generic races and classes with `EXTVAR_GenRace=NAME,NAME` and
`EXTVAR_GenCharClass=NAME` in `coffeemud.ini`; they are saved with the record
(unverified whether the race and class editors list them). Fields 21 to
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

## Stats and derived values

A character has two copies of its stats. The **base** stats (`baseCharStats`,
`basePhyStats`, `baseState`) are what training, creation and level-ups change.
The **effective** stats (`charStats`, `phyStats`, `curState`) are rebuilt
whenever something changes, by starting from the base and applying, in turn,
the race, the class, the ageing rules, every effect, every worn item and
anything the world adds with `STATEFFECT_` rules. Scripts and abilities
should change the base value only when the change is permanent; a spell
changes the effective one by being an effect.

**Base stats.** Six: `STRENGTH`, `INTELLIGENCE`, `DEXTERITY`, `CONSTITUTION`,
`CHARISMA`, `WISDOM`. Each has a ceiling, `BASEMAXSTAT` (18) plus the
**max-stat adjustment** of race, class, ageing and effects (the `MAX STRENGTH
ADJ.` charstats): training can take a stat up to that ceiling and no further.
`BASEMINSTAT` is the floor a stat starts from.

**Other charstats.** Gender; about twenty-five saving throws (paralysis, fire, cold,
water, gas, mind, general, justice, acid, electricity, poison, undead, magic,
disease, traps, detection, overlooking, doubt, blunt, pierce, slash, spells,
prayers, songs, chants, polymorph); age and weight adjustment; critical chance
and damage (weapon and magic); faith; recovery rate; an experience adjustment
percent (`XP ADJUSTMENT PCT`). Races, classes, items and effects adjust these
by name (`SAVE VS FIRE`, `CRIT WEAPON CHANCE PCT`...). A saving throw is the
stat plus a contribution of the base stats (for paralysis, the average of
Constitution and Strength); see
[29-combat-and-encounters.md](29-combat-and-encounters.md) for how saves,
attack and armor are used.

**Derived pools.**

| Value | How it is worked out | Settings |
| --- | --- | --- |
| Hit points | `STARTHP` at level 1; each level then adds one result of the class's `HITPOINTSFORMULA` (at least 1) to the base. Racial and class `CharState` adjustments and `STATEFFECT_` hit point parts are added on top | `STARTHP`, class formula |
| Mana | `STARTMANA` at level 1, then each level adds the mana formula's result | `STARTMANA`, class formula |
| Moves | `STARTMOVE` at level 1, then each level adds the movement formula's result | `STARTMOVE`, class formula |
| Attack bonus | each level adds floor(attack attribute / 18) + the class's `LVLATT` to the base attack, +1 more with the attribute at 22 or more, +2 at 25 or more | class `ATTATT`, `LVLATT` |
| Damage | +1 base damage every `LVLDAM` class levels | class `LVLDAM` |
| Practices and trains | see [What a level gives](#what-a-level-gives) | formulas |
| Carrying weight | `FORMULA_MAXCARRY` (default `@x1 + ((@x2 + 10.0) * @x2 * @x1 / 150.0) + (@x2 * 5.0)`; @x1 base body weight, @x2 current Strength, @x3 base Strength, @x4 maximum trainable Strength) | `FORMULA_MAXCARRY` |
| Items carried | `FORMULA_MAXITEMS` (default `(2 * @x1) + (2 * @x3) + (2 * @x2)`; @x1 number of wear locations, @x2 level, @x3 current Dexterity, ...) | `FORMULA_MAXITEMS` |
| Followers | `FORMULA_MAXFOLLOW` (default `1 + ((@x2 - 6.0) / 3.0)`; @x1 level, @x2 current Charisma) | `FORMULA_MAXFOLLOW` |
| Proficiency gain by use | `FORMULA_PROFGAIN` (default `(100 - (@x3 * 50)) * ((@x1 + 1 - @x2) / ((@x1 * 2) + (10 * @x2)))`; @x1 last player level, @x2 the skill's qualifying level, @x3 1 if fatigued) | `FORMULA_PROFGAIN` |

The `FORMULA_` keys can be set in `world.ini` (the prefix `FORMULA_` is
allowed). The combat formulas (`FORMULA_ATTACKADJUSTMENT` and the others) are
in [29-combat-and-encounters.md](29-combat-and-encounters.md).

### Adding, renaming and giving effects to stats

Source: [StatEffects.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/StatEffects.java),
[CharStats.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/CharStats.java).
Fixture: [p26-stateffects.js](../../tools/fixtures/fixes/p26-stateffects.js).
In-game help: `AHELP STATEFFECTS`.

A world can add stats and rename them in `coffeemud.ini` or `world.ini`:

| Key | Value |
| --- | --- |
| `ADDCHARSTAT_<NAME>` | `type,abbreviation,description,adjective`. The type is `BASE` (a new base stat, bought and rolled like the six), `SAVE` (a saving throw), or `OTHER`. The abbreviation is unique, two letters. Example: `ADDCHARSTAT_LUCK=BASE,lu,luck,lucky` |
| `REPLACECHARSTAT_<OLD>` | `new name,abbreviation,description,adjective`. Example: `REPLACECHARSTAT_CHARISMA=PRESENCE,pr,presence,commanding` makes Charisma read as Presence in the score, the creation screens and `@StatList@` |

A new base stat takes part in creation (point buy, random roll, `MAXSTATS`),
in `TRAIN`, in stat achievements and in `@StatList@`. A stat that no formula,
effect or script reads does nothing; give it a purpose:

`STATEFFECT_<STAT>="per point over 10: +1 SAVES, +2 ATTACK"` gives a stat its
effects. The head is `per point over N`, `per K points over N` or `per point`
(over 0). The character gets the parts for each whole step of K points above
N. Each part is `+N NAME` or `-N NAME`, where NAME is:

| NAME | What it changes |
| --- | --- |
| a char stat name (`STRENGTH`, `SAVE_FIRE`, `CRIT_CHANCE_PCT_WEAPON`, or any stat, including an added one) | that stat |
| `SAVES` | every saving throw |
| `CRITCHANCE` (a trailing `%` is ignored) | weapon and magic critical chance |
| `ATTACK`, `DAMAGE`, `SPEED` | the effective attack bonus, damage, attack speed |
| `ARMOR` | armor; `+N` is N better |
| `HITPOINTS` (`HP`), `MANA`, `MOVES` (`MOVEMENT`) | maximum hit points, mana, moves |

```ini
ADDCHARSTAT_LUCK=BASE,lu,luck,lucky
STATEFFECT_LUCK="per point over 10: +1 SAVES, +1 CRITCHANCE%"
STATEFFECT_WISDOM="per 2 points over 12: +1 DAMAGE, +3 CRITCHANCE%"
```

The rules are re-read about every three seconds, and applied each time a
character's stats are worked out. In formulas (the level, practice, train and
hit point formulas, and `FORMULA_MAXCARRY`, `MAXITEMS`, `MAXFOLLOW`),
`@STAT(NAME)` reads the effective stat of the mob the formula is for, and
`@BASESTAT(NAME)` the base stat: `FORMULA_TRAINSPERLEVEL=1 + (@STAT(LUCK) / 10)`.

## Abilities, properties, diseases, poisons and trades

Everything a character can do, and everything that can be done to it, is an
ability: spells, skills, prayers, chants, songs, common (crafting and
gathering) skills, languages, properties, diseases, poisons and traps. This
chapter covers how a *class or race* qualifies for abilities. How abilities
themselves are authored (`CREATE ABILITY`, generic abilities with
`TARGETMODE`, `ACTIONCOST` and `COMPONENTS`, summons, lifecycle hooks such as
`CANCAST`/`ONCAST`/`ONTICK`/`ONEXPIRE`/`ONLEARN`, diseases with `GenDisease`,
poison, trade-skill and tweak scripts), their flags, qualities and domains,
and teaching and granting, are in
[25-abilities-and-effects.md](25-abilities-and-effects.md). The properties a
creator attaches to rooms, items and mobs (including `Prop_Trainer`,
`Prop_Respec`, `Prop_Discovery`, `Prop_PlayerTemplate`, `Prop_Recruitable`)
are in [23-properties-reference.md](23-properties-reference.md).

## Advancement and discovery

### Levels and experience

Source: [CoffeeLevels.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeLevels.java).

A player advances one level at a time. The experience a level-*L* character
needs before it becomes level *L*+1 is entry *L* of `EXP_CHART` in
`resources/lists.ini` (a world's own `world/resources/lists.ini` replaces it):

```text
EXP_CHART= 0, 500, 1100, 1800, 2600, 3500, 4500, 5600, 6800, 8100, 9500, ...
```

So a level-1 character levels at 500 experience, a level-2 at 1100, and so on.
Past the last entry the chart extends by repeating the final difference.
`LASTPLAYERLEVEL` (91) is the highest level mundane experience reaches.

Experience comes from:

| Source | Notes |
| --- | --- |
| Combat | A killed mob pays through `FORMULA_TOTALCOMBATXP` and `FORMULA_INDCOMBATXP` (below), scaled by `EXPRATE` and the level difference |
| Scripts | `MPEXP who amount` (an amount, or a percent of a level, or `NEXTLEVEL`), quest rewards, achievement `XP` rewards, perk and creation `EFFECTS` |
| Discovery | `Prop_Discovery` and `DISCOVERYXP` |
| Role-play | `RPAWARDS` |
| Abilities | Skills that award experience when used |
| Death | A negative amount, set by `PLAYERDEATH` |

Every gain passes through a chain of modifiers: the class's and the race's
`adjustExperienceGain` for the whole group, the race's `XPADJ` percent, the
`XPMOD` setting (and any `Prop_ModExperience` on the character, its items, its
room or area; see [23-properties-reference.md](23-properties-reference.md#prop_modexperience)),
the `progression.js` `xpGain` function, and the experience deferral. A race or
class flagged `EXPLESS` earns nothing; `DISABLE=EXPERIENCE` and
`DISABLE=LEVELS` turn gaining experience and levels off for the whole game.
Each gain has a source tag that `XPMOD`'s `;;TYPE` and `xpGain` see:
`COMBAT:<victim level>` (with `P` appended for a player victim), `DEATH:`,
`DISCOVERY:<tattoo>` and `DISCOVERY:ROOM`, `SCRIPT:...`, `ABILITY:<id>`,
`COMMAND:TEMPLATE` and `CHARCREATION:NEWCHARLEVEL`.

**Combat experience.** Two formulas in `coffeemud.ini` (and `world.ini`, as
`FORMULA_` keys) decide the amount:

```ini
# @x1 killers dividing the pool, @x2 sum of their squared levels, @x3 sum of their levels,
# @x4 number of killers, @x5 highest killer level, @x6 killed mob's level
FORMULA_TOTALCOMBATXP=100 + ((25*@x1) - (@x1*((25*@x1)^.5)))
# @x1 the pool, @x2 this killer's level squared, @x3 sum of squared levels, @x4 this killer's level, ...
FORMULA_INDCOMBATXP=(@x1 * (@x2 / @x3)) < 100
```

Then `EXPRATE` (default 6) scales the share by level difference. A victim more
than `EXPRATE` levels below the killer gives nothing. If the highest-level
character in the room is more than `EXPRATE` levels above the victim, or above
the killer, the share shrinks by `(EXPRATE / difference)` squared, so
powerlevelling a low-level character is penalised. Otherwise the share is
raised or lowered in proportion to `(victim level - killer level) / EXPRATE`. Setting
`EXPRATE=0` is documented as "no bonus or penalty", but see the notes in
[Troubleshooting](#troubleshooting).

**Level caps.** `LASTPLAYERLEVEL` caps mundane levels. A class's `LEVELCAP`
caps levels *in that class*. `EXPLESS`, `LEVELLESS` and `CLASSLESS` flags
(on race and class) switch parts of the system off for what has them.

### What a level gives

When a character reaches a level (`CoffeeLevels.level`) the engine, in order:

1. Raises the level and the class level, and adds the class's hit point,
   mana and move gains (shown in the level message).
2. Calls the JavaScript `levelup` event and writes a `LEVEL` line in the
   character's chronicle if `JOURNALAUTO` has `LEVEL`.
3. Gives practices and trains (below), then calls `progression.js` `onLevel`.
4. Mentions a pick to spend if there are perks to choose (`PERKS`).
5. Checks rank ladders (`Ranks.check`) so that a rank whose `REQUIRES` is now
   met is awarded.
6. Grants every class and race ability whose level is now reached, and any
   achievement-granted skills and expertises, and starts auto-invoked ones.

Practices and trains per level are:

| Setting | Meaning |
| --- | --- |
| `FORMULA_PRACTICESPERLEVEL` | Practices a level gives, a formula of `@x1` the new level, `@x2` Wisdom (capped at its max), `@x3` Intelligence, `@x4` the class's `LVLPRAC`, `@x5` the race's `LVLPRAC`. Blank (default): Wisdom / 6 plus both `LVLPRAC`s, at least 1 |
| `FORMULA_TRAINSPERLEVEL` | Trains a level gives, same variables (`@x4` the class's, `@x5` the race's `LVLTRAIN`). Blank: 1 plus both `LVLTRAIN`s |
| `TRAINSATLEVELS` | Extra trains at certain levels: `"1:3, 5:2, 10:2"` (level 1's come as creation ends). Always added |
| class and race first-level fields | `FSTPRAC`, `FSTTRAN` at creation |
| `progression.js` | `practicesForLevel(mob)` and `trainsForLevel(mob)`, when defined, give the number in place of all of the above |

```ini
FORMULA_PRACTICESPERLEVEL=2
FORMULA_TRAINSPERLEVEL=(@x1+1)%2
TRAINSATLEVELS=1:3, 5:2
```

(A formula result is rounded down and not below zero.) A lost level takes back
what it gave: `MPLEVELDOWN` and the level-loss effects undo the hit points,
practices and trains of the level.

### Starting level for later characters

`NEWCHARLEVEL` is the level a character made later starts at, reached by real
level-ups as creation ends, so hit points, practices, trains and level-granted
skills are earned: `1` (default), a number, `PARTYMIN`, `PARTYAVG` (average of
the profile's other characters) or `LEADER` (the one played last), each with
an optional `+N` or `-N` (`PARTYMIN-1`). It never exceeds `LASTPLAYERLEVEL`.
`PARTYMIN`, `PARTYAVG` and `LEADER` give 1 when the profile has no other
character. `NEWCHARXP=FLOOR` then sets
the experience to the least the level needs; `KEEP` (default) leaves what the
level-ups gave. Template characters keep their template level.

### Scripted level changes

| Tool | Effect |
| --- | --- |
| `MPLEVELUP who (N)` | Exactly N levels (1 by default), each by the experience that level needs, wherever the character is within its level |
| `MPLEVELDOWN who (N)` | N levels lost, with the experience set back |
| `MPEXP who NEXTLEVEL` | Exactly the next level |
| `EXPTONEXT(who)` | The experience needed for the next level (0 when none); `$%EXPTONEXT($n)%` or in a comparison |
| JavaScript | `Packages.com.planet_ink.coffee_mud.core.LevelUps.up(mob, n, 'SCRIPT:name')`, `.down(mob, n)`, `.toNext(mob)` |

Directly setting `LEVEL` is not equivalent: it skips the gains, hooks and
grants above. See [27-mobprog-commands.md](27-mobprog-commands.md#mplevelup) and
[28-mobprog-functions.md](28-mobprog-functions.md#exptonext).
Fixture: [p2-levelups.js](../../tools/fixtures/fixes/p2-levelups.js).

### Experience over time and by role-play

`EXPDEFER` (engine ini only) holds earned experience back until the player
takes an action. `EXPDEFER=` is blank for normal behaviour; otherwise it is:

```text
EXPDEFER=<mud hours> <percent> [$]<COMMAND>(<argument>) [<zapper mask>]
```

How often experience is released (in game hours), the percentage to hold
back, the command that releases it (and its argument; `*` is a wildcard),
and an optional mask of who must be in the same room, which may use player
variables in braces such as `{RACE}`, `{CHARCLASS}` and `{LEVEL}`. A `$` before
the command name still runs the command after the experience is paid. Example
from the ini: `6 200% SAY(Give me XP!) -EFFECTS +MOBTeacher -ANYCLASS
+{CHARCLASS}`. Held experience is paid when the player enters that command (the
engine runs it through `DeferCmd`). With no command given, the first command a
player enters after the interval has passed collects it. In CoffeeSUD the interval runs on the
**game clock**, so a paused or turn-based game releases experience by game
time (fixture [p28-xptime.js](../../tools/fixtures/fixes/p28-xptime.js)).

`RPAWARDS` (engine ini only) pays experience for role-play actions:

```text
RPAWARDS=<percent of a level> <seconds between awards>, <ACTION> <amount>, ...
```

The first item is the most role-play experience that may be earned per level
(a percent or amount) and the minimum seconds between awards (default 8). Each
later item is an action and a nominal amount (negative takes some away). The
actions are `INTRODUCE-PC`, `INTRODUCE-NPC`, `SAY-PC`, `SAY-NPC`, `SOCIAL-PC`,
`SOCIAL-NPC`, `SOCIAL-OTH`, `EMOTE-PC`, `EMOTE-NPC`, `EMOTE-OTH`, `GOAFK`,
`CHANNEL` and `CHANNEL-NAMED(NAME1,NAME2)`. No role-play experience is earned
while fatigued, hungry or thirsty.

```ini
RPAWARDS=25% 8, INTRODUCE-PC 20, INTRODUCE-NPC 5, SAY-PC 5, SAY-NPC 1, SOCIAL-PC 10
```

### Discovery experience

Source: [Discovery.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Discovery.java),
[Prop_Discovery](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Discovery.java).
Fixture: [p11-discovery.js](../../tools/fixtures/fixes/p11-discovery.js).

`Prop_Discovery` on a room, exit, item or mob pays experience, a message, a
tattoo and a journal line the first time a character does something there. Its
parameters (full reference in
[23-properties-reference.md](23-properties-reference.md#prop_discovery)):

| Parameter | Meaning |
| --- | --- |
| `XP=N` or `XP=N%` | Experience, or a percent of the character's current level span |
| `ON=` | `ENTER`, `LOOK`, `READ`, `OPEN`, `GET`, `FIND` or `TALK` (several with `\|` or commas). Default: `ENTER` on a room, `OPEN` on an exit, `GET` on an item, `TALK` on a mob, `LOOK` otherwise |
| `ONCE=` | `CHARACTER` (default), `PARTY` or `PROFILE`: who counts as having made the discovery |
| `MESSAGE="..."` | Told to the discoverer |
| `TATTOO=NAME` | The tattoo that records it (made up from the thing's identity if not given) |
| `MASK="..."` | Who can make the discovery |
| `JOURNAL="..."` | A line added to the character's quest journal |

The experience is tagged `DISCOVERY:<tattoo>`, so `XPMOD` can scale it. For an
exploration formula across the whole world, `DISCOVERYXP` pays each *new room* a
character enters:

```ini
DISCOVERYXP=5+@x1
```

(`@x1` the character's level, `@x2` the area's median level; tagged
`DISCOVERY:ROOM`.) Test discovery rewards with two party members and with two
separate campaigns to catch scope mistakes: `CHARACTER`, `PARTY` and `PROFILE`
scopes differ in who is marked.

### Training, practice and teachers

A character spends **trains** on stats, pools and classes, and **practices** on
skill proficiency. Both are earned at levels (above) and counted in the
score.

**TRAIN.** `TRAIN` alone shows the character's trains and what each thing
costs. `TRAIN <thing> (teacher)` buys one, with a willing teacher in the room (usually a
mob with `Prop_Trainer`; see below). For a stat, the teacher's own score in it
must be higher than yours. `TRAIN GAIN` trades something else (7 practices by
default) for a train. A thing is a stat name (`STRENGTH`...), `HIT POINTS`,
`MANA`, `MOVEMENT`, `PRACTICES`, `GAIN`, or a class name (changing class; see
[class systems](#class-systems-subclasses-and-multi-classing)). `NOPROMPT` at the
end skips the confirmation. With several trainers in the room, a trainer who
refuses the student (see teacher rules) is skipped.

| Setting | Format | Default |
| --- | --- | --- |
| `STATCOSTS` | Comma list of `<condition on the current stat> cost [type]`: `<18 1, <22 2, <25 3, <99 5`. The cost type defaults to `TRAIN`; others are `PRACTICE`, `MANA`, `MOVEMENT`, `HITPOINT`, `XP`, `GOLD`, `QP`, a denomination or currency (quote words: `<99 5 "GOLD BIT"`) | `<18 1, <22 2, <25 3, <99 5` |
| `TRAINCOSTS` | Comma list of `<thing> <amount gained> <cost> <cost type>`; things are `HITPOINTS`, `MANA`, `MOVES`, `GAIN`, `PRACTICES`, `CCLASS`, `ATTRIBUTE`. An amount of 0 turns an entry off. `HITPOINTS 10 1 TRAIN` means one train buys 10 hit points; `GAIN 1 7 PRACTICE` means 7 practices buy one train | `HITPOINTS 10 1 TRAIN, MANA 20 1 TRAIN, MOVES 20 1 TRAIN, GAIN 1 7 PRACTICE, PRACTICES 5 1 TRAIN` |
| `SKILLCOST`, `COMMONCOST`, `LANGCOST` | `<number> TRAIN\|PRACTICE\|XP\|GOLD`, with per-ability overrides prefixed by the ability ID; `@x1` the skill's level, `@x2` the player's: `1 TRAIN, Spell_Sleep 50*@x1 GOLD` | `1 TRAIN`, `1 TRAIN`, `3 PRACTICE` |
| `PRACMAXPCT` | Highest proficiency a practice point can reach | `75` |
| `DISABLE=CLASSTRAINING` | Removes class changes from `TRAIN` | off |

**Learning a skill.** A character *qualifies* for a skill by class, race,
cultural, qualify-all or achievement grants (`QUALIFY` lists them,
`WILLQUALIFY` previews). To learn one it is **taught**: anyone with at least 25%
proficiency can `TEACH <student> <skill>`, and a mob with `MOBTeacher` teaches
when asked (`SAY teach me <skill>`, or `GAIN <skill>`). The student pays the
skill's cost (`SKILLCOST` and friends), then `PRACTICE <skill> (teacher)`
raises proficiency by spending practices, up to `PRACMAXPCT` (75% by default).
Further proficiency comes by use, at the chance `FORMULA_PROFGAIN` gives. The
teaching mob must itself know the skill sufficiently.

**Teacher rules.** `MOBTeacher` (see
[24-behaviors-and-masks.md](24-behaviors-and-masks.md#mobteacher)) and
`Prop_Trainer` accept, in any order among their usual parameters:

| Parameter | Meaning |
| --- | --- |
| `STUDENTMASK="mask"` | Whom it teaches at all. Others hear `REFUSEMSG` |
| `REFUSEMSG="text"` | What it says to refused students |
| `SKILLMASK.<ABILITY ID>="mask"` | Whom it teaches that one skill |
| `PRICE="5 per level"` or `PRICE=50` | Money taken for each lesson, on top of the usual cost |
| `HIDDEN=ID,ID` | Skills it teaches but leaves off `TEACHLIST` |

They gate `TEACH ME`, `GAIN`, `PRACTICE` and `TRAIN`. `TEACHLIST (teacher)`
shows a student what a teacher offers them, with its price, and what it will not
teach. A trainer's `Prop_Trainer` parameters (`STR=25 INT=25 ...`, class names,
expertise names, `NOTEACH`) set the stats it trains; it can train a class by
having level 0 in it. Fixture: [p16-teach.js](../../tools/fixtures/fixes/p16-teach.js).
Make the teacher rules agree with the `SKILLTREE` chains and hints so a
player is never pointed at a skill nobody will teach.

**Expertises.** An expertise is a level of improvement to an ability's
numbers (range, duration, cost, damage) bought with trains by `GAIN`; the list
is `resources/skills/expertises.txt` (`LIST EXPERTISES`, `CREATE EXPERTISE`);
`EXPERTISES` shows a character's.

### Respec and refunds

Source: [Prop_Respec](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Respec.java),
[RespecCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/RespecCmd.java).
Fixture: [p23-respec.js](../../tools/fixtures/fixes/p23-respec.js).

The engine records (in a hidden saved effect, `Prop_Purchases`) what a character
bought with trains and practices: skills learned and practised, stats trained,
expertises, class changes. Where a character may retrain, `RESPEC` lists what it
may undo and what each cost, `RESPEC <thing>` undoes one and gives back what it
cost, and `RESPEC ALL` undoes them all. A place is anything that carries the
property `Prop_Respec` (a room, an item, or a mob):

| Parameter | Meaning |
| --- | --- |
| `WHAT=` | Kinds it undoes, by commas: `STATS`, `SKILLS`, `EXPERTISES`, `CLASS`, or `ALL` (default) |
| `COST=` | Money: `"500"` for the whole respec, or `"500 per skill"` for each thing undone |
| `ONCE=YES` | Once per character at that place (tracked by a tattoo) |
| `MASK="..."` | Who may retrain there |

```text
WHAT="SKILLS,STATS" COST="5 per skill" ONCE=YES
```

`MPRESPEC who (SKILLS|EXPERTISES|STATS|CLASS|ALL) (name|ALL)` does it from a
script, free; from JavaScript use
`Packages.com.planet_ink.coffee_mud.core.Purchases.respec(mob, 'SKILLS', 'ALL')`.
Skills that come by themselves (class, race and achievement grants) stay.
`RETRAIN` is a synonym of `RESPEC`.

### Scripted progression (progression.js)

Source: [JSProgression.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/JSProgression.java).
Full JavaScript conventions are in [05-javascript.md](05-javascript.md).

If `world/scripts/progression.js` exists it is read (and read again when it
changes), under the usual JavaScript scope and time limit. It may define any of
these functions, each called only if present:

| Function | When | Effect of the result |
| --- | --- | --- |
| `onCreated(mob)` | A character has been made | none |
| `onCreationStep(mob, step, answer)` | After each creation step: `race`, `gender`, `stats`, `class`, `factions`, `deity`, `origin`, `kit`, `traits`, `template`, and the lower-case name of each `creation.ini` question | none |
| `statRoll(mob, bonus)` | `STARTSTAT=SCRIPT`: set the stats yourself, or return `{ strength: 14, dexterity: 12 }` (names, abbreviations or name starts) or a list in base stat order; stats left out are `BASEMINSTAT` | the stats |
| `xpGain(mob, source, amount)` | Each positive experience gain, with its source tag | the experience given instead; not below 0 |
| `onLevel(mob, level)` | After a level's gains | none |
| `practicesForLevel(mob)`, `trainsForLevel(mob)` | A level's practices and trains | used in place of the usual number |
| `onLearn(mob, abilityId)` | A character gets a skill | none |
| `onTrain(mob, what, amount)` | After `TRAIN` | none |

```js
// world/scripts/progression.js
function xpGain(mob, source, amount) {
    if (String(source).indexOf("COMBAT:") === 0) return Math.round(amount * 0.5);
    return amount;
}
function trainsForLevel(mob) { return mob.basePhyStats().level() % 5 === 0 ? 3 : 1; }
```

### Checking the curve

`PROGRESSION <class> (<race>) (<to level>)` (builder mode, a `DEV=1` game or the
`CMDCLASSES` flag) prints a class's level curve for a Human by default, to level 10 unless a
number (at most 100) is given: for
each level the experience it takes, hit points, mana, moves, practices, trains
and skills gained (those given at once are marked `*`). `PROGRESSION XP (<area>)`
adds up the experience the content gives: the achievements', then each area's
literal `MPEXP` amounts and `Prop_EnterAdjuster` experience, with the level a
character would reach having done it all, area by area. It cannot prove the
rewards of arbitrary dynamic scripts. Use `CHARGEN` (`CHARGEN COMBAT <class>
<level>`) to compare example characters with mobs of the same level, and
`TESTCHAR` for a quick test character (see [13-testing-release.md](13-testing-release.md)).

## Make advancement discoverable

### Skill trees and hints

Source: [SkillTree.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/SkillTree.java),
[SkillTreeCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/SkillTreeCmd.java).
Fixture: [p24-skilltree.js](../../tools/fixtures/fixes/p24-skilltree.js).

`SKILLTREE` (short form `STREE`) shows a class's skills arranged by what each
needs first. A skill is *under* another when its class-ability pre-requisites
(`GETCABLEPREQ`, [Qualifying abilities](#qualifying-abilities)) name it. The
forms are:

```text
SKILLTREE            your own class
SKILLTREE ranger     another class
SKILLTREE camouflage the chain that leads to one skill
```

Each skill is marked one of:

| Mark | Meaning |
| --- | --- |
| `(learned, 75%)` | Known, with the proficiency |
| `(learnable: ...)` | Qualified for now; the text is what it costs |
| `(locked: level 7)` | The class level is not reached |
| `(locked: needs Skill_Trip(50))` | A pre-requisite skill is missing |
| `(locked: you do not qualify)` | The requirement mask is not met (or the hint, if there is one) |
| not shown | `SECRET`, or `MASKED` with the mask unmet, and no hint |

A hint is a line pointing the player to where a skill is taught. Hints live in
`resources/skills/skillhints.ini`, and a world's own
`world/resources/skills/skillhints.ini` is read in its place. One line each,
`KEY=VALUE`, where the key is an ability ID or `CLASSID.ABILITYID` (the class
form wins) and the value is `HINT="..."` with an optional `SHOW=HINT`:

```ini
# world/resources/skills/skillhints.ini
Spell_DetectInvisible=HINT="seek the Druid circle in the Fernwood"
Ranger.Spell_Infravision=HINT="for elves alone" SHOW=HINT
```

A hint shows after a locked or learnable skill (`- seek the Druid circle...`).
A secret skill with a hint shows as `??? - <hint>` so the player knows there is
something to find; with `SHOW=HINT` it shows its name, `Infravision
(locked: for elves alone)`. A hint should point to a real teacher, quest or
place, not merely expose a forbidden ability ID. `TEACHLIST` and the teacher
rules should agree with the chains.

### Perks after creation

See [perks.ini](#perksini-perks-traits-and-flaws). During play, `PERKS` shows
what the character has, can take now and cannot yet (with the reason), and the
picks left in each pool; `PERK <name>` takes one, `PERK REFUND <name>` gives one
back and `PERKS TREE <name>` shows a tree ([Build trees](#build-trees)). A level-up that leaves a
pick to spend says so. `HASPERK`, `MPPERK` and the perk tattoo let quests and
shops respond to a choice.

### Rank ladders

Source: [Ranks.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Ranks.java),
[RanksCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/RanksCmd.java).
Fixture: [p25-ranks.js](../../tools/fixtures/fixes/p25-ranks.js).
In-game help: `AHELP RANKLADDERS`.

A rank ladder is a named progression beside levels: a guild, an order, a
reputation. Ladders are `[KEY]` sections in `world/ranks.ini` (re-read when the
file changes):

| Key | Meaning |
| --- | --- |
| `NAME=` | Display name (the key, capitalised, if missing) |
| `RANKS="Footpad, Cutpurse, Shadow, Master"` | The ranks, lowest first, numbered from 1 |
| `FACTION=` | A faction ID whose standing `RANKS` shows beside the rank |
| `RANK<n>.REQUIRES="mask"` | A zapper mask. When the character meets it, they rise to rank *n* **by themselves** (at level-up, when they look at `RANKS`, or on `MPRANK who KEY CHECK`). With no `REQUIRES`, only a script raises a character to that rank |
| `RANK<n>.REWARDS="2 QP 100 XP"` | Achievement-style rewards, given **once** the first time the rank is reached (a hidden tattoo `RANKREWARD_<KEY>_<n>` remembers) |
| `RANK<n>.EFFECTS=Prop_Smell;lamp oil` | Effects held while at the rank (`Prop_SpellAdder` syntax: ID, `;`, parameters), taken off when the rank is left. A character already holding an effect of the same kind from elsewhere keeps that one, and leaving the rank does not take it off |
| `RANK<n>.TITLE="*, the Cutpurse"` | A title added while at the rank (`*` is the character's name) |
| `RANK<n>.NEXT=text` | What `RANKS` says the next rank needs |

A character's rank is the tattoo `KEY_n` (`GUILD_2`), so masks
(`-TATTOOS +GUILD_3`), `Prop_ReqTattoo` and shops can gate on it. `RANKS` lists
the ladders the character is on, or can climb by themselves (a ladder with a
rank-1 `REQUIRES` or `NEXT`), with the rank, how far up, what the next rank
needs and the standing in the ladder's faction.

| Tool | Effect |
| --- | --- |
| `MPRANK who KEY +N`, `-N`, `=N`, `CHECK` | Move up or down (`+1` if no amount), go to rank N (`=0` leaves the ladder), or raise as far as `REQUIRES` allow. See [27-mobprog-commands.md](27-mobprog-commands.md#mprank) |
| `RANK(who KEY)` | The rank number, 0 for none: `$%RANK($n GUILD)%`. See [28-mobprog-functions.md](28-mobprog-functions.md#rank) |
| achievement event | `EVENT="RANKUP" NUM=<rank> LADDER=<KEY>` is won on reaching a rank |
| JavaScript | `Packages.com.planet_ink.coffee_mud.core.Ranks.change(mob, key, "+1")` |

```ini
# world/ranks.ini
[LANTERN]
NAME=Order of the Lantern
RANKS="Initiate, Acolyte, Lantern-bearer, Warden, Keeper"
FACTION=alignment.ini
RANK1.REQUIRES="-TATTOOS +SWORN_AT_SHRINE"
RANK1.NEXT=an oath at the shrine
RANK2.REWARDS="2 QP"
RANK3.EFFECTS=Prop_Smell;lamp oil
RANK3.TITLE=*, Lantern-bearer
RANK4.NEXT=the Vigil of Ash
```

Moving down removes the rank's effects and title, and does not repeat the
rewards on the way back up.

## Ageing

Source: [Ageing.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Ageing.java),
[CommonMsgs.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CommonMsgs.java) (`tickAging`).
Fixture: [p29-ageing.js](../../tools/fixtures/fixes/p29-ageing.js).
In-game help: `AHELP AGEING`.

A player's **age** is in years of the game calendar of the character's start
room. A character is born on the day it is created, with the age of its race's
*young adult* entry (`AGING`), and has a birthday on that date each year, on
which its age rises by one ("Happy Birthday!"). A character who has been away
for years ages only about a year (`DISABLE=SLOW_AGEING` makes it age once per
elapsed year instead). Builders with the `IMMORT` flag do not age;
`DISABLE=ALL_AGEING` turns the whole system off. The race's ageing chart gives
the age category (infant, toddler, child, young adult, adult, mature, old,
venerable, ancient; see [the chart](#body-parts-wear-locations-and-ageing-charts)).
Time and the calendar are in [10-world-simulation.md](10-world-simulation.md).

**Effects of age.** By default (coded in `StdRace`): infants and toddlers get
worse mind saves and lower maximum Strength, Intelligence and Wisdom; children a
little less; young adults and adults are unchanged; mature (`MIDDLEAGED`)
characters lose maximum Strength, Constitution and Dexterity and gain maximum
Intelligence, Wisdom and Charisma, and more so at old, venerable and ancient
(which also lowers the save against undead). These adjust the *maximum* of each
stat, not the stat itself. A character whose maximum falls below 1 dies. From
the venerable age a small chance each ageing check brings cancer, and from the
ancient age arthritis and Alzheimer's (`DISABLE=AUTODISEASE` turns these off).

**Ageing as data.** CoffeeSUD lets a world replace those rules:

| Setting | Meaning |
| --- | --- |
| GenRace `AGEEFFECTS` | `MIDDLEAGED: STR-1 DEX-1 WIS+1; VENERABLE: STR-2 CON-2`. In place of the coded effects *for that race*: each age's stat changes, as `STAT+N` or `STAT-N` pairs (any stat by name, or by its first letters), `;` between ages. An age not named has no change. The ages are `INFANT`, `TODDLER`, `CHILD`, `YOUNGADULT`, `MATURE`, `MIDDLEAGED`, `OLD`, `VENERABLE`, `ANCIENT` (or the displayed words, except that the word `Mature` reads as `MATURE`, index 4; write `MIDDLEAGED` for the age shown as Mature). These change the stat itself, not its maximum |
| `AGEDISEASES` (ini or world.ini) | `"VENERABLE:Disease_Cancer:0.01, ANCIENT:Disease_Arthritis:1"`. In place of the coded diseases for everyone: from each age on, a percent chance at each ageing check to catch the disease (1 if no number is given). `DISABLE=AUTODISEASE` still stops them |
| `STARTAGE_<NAME>` | `STARTAGE_VETERAN="-TATTOOS +ORIGIN_VETERAN:35"`: a zapper mask, a colon, and an age in years. A new character who meets the mask starts at that age (the first that fits). Applied at the end of creation (not to template characters) |
| `AGE_PROG` | A trigger: on each birthday, scripts on the character and those around it run `AGE_PROG [BIRTHDAY\|CATEGORY\|<age name>\|ALL]`, with `$0` the age category, `$1` the age, `$2` `BIRTHDAY` or `CATEGORY` (the latter when the age category changed). See [26-mobprog-triggers.md](26-mobprog-triggers.md#age_prog) |
| `AGECATEGORY` | Achievement event `EVENT="AGECATEGORY" CATEGORY=OLD`, won on reaching that age |

Keep starting ages and age charts consistent: a race with `AGING` of
`0,1,3,15,35,53,70,74,78` that starts at 15 and a `STARTAGE_` of 5 would start a
character as a child.

## Death, rebirth and remort

How combat kills and what a downed state is belong to
[29-combat-and-encounters.md](29-combat-and-encounters.md). The settings that act
on the *character* are:

| Setting | W | Meaning |
| --- | --- | --- |
| `PLAYERDEATH` | W | Comma list of consequences of dying (shipped value `(@x1 * 50) < 2000`). Entries: `EXPERIENCE` (lose 100 experience a level), a number (that much experience), an expression with `@x1` the player's level and `@x2` the killer's, `UNLEVEL X` (lose X levels), `LOSESKILL` (lose a random skill), `RETAIN` (keep the equipment), `PURGE X` (erased after X deaths), `ASTRAL`, `ASTRAL_RES` (an astral spirit, the latter may resurrect itself), `OUT X` (unconscious X ticks), `RECALL` (back to the death room with no body), and CoffeeSUD's `DOWNED X Y` (a player or follower falls for X rounds; an ally's `REVIVE`, or a heal of Y hit points, brings them back, and they get up at 1 hit point when the fight is over; when X runs out the rest of the list applies) |
| `PARTYDEFEAT`, `PARTYDEFEATGOLD` | | When everyone in a room is down or dead and no `PARTYDEFEAT_PROG` handles it: `RESTORE` (offer the last save), `CHECKPOINT` (wake at the leader's checkpoint at 1 hit point, `PARTYDEFEATGOLD` percent of gold gone), `WAIT`, `DEATH` (default) |
| `MOBDEATH` | W | What happens when a non-player dies (`DEATH`, or `OUT (expression)`) |
| `FLEE` | | Cost of fleeing combat; the same entries as `PLAYERDEATH`; blank for none |
| `DEATH`, `DEATH_<key>`, `DEATH_MASK<n>` | W | Where a dead character wakes: a room ID, `START` (its recall room, the default), `MORGUE` (where the body is), `HERE` (not recommended), `CHECKPOINT` (its last checkpoint from `Prop_Checkpoint` or `MPSETRESPAWN`, else recall). Qualified keys use the same grammar as `START_` |
| `MORGUE`, `MORGUE_<key>` | W | Where the bodies of dead characters go: `HERE` (default), a room, `DEATH` or `START` |
| `CORPSEGUARD` | | Who may loot a dead player's body (`ALL`, `SELFONLY`, `PKONLY`) |
| `ITEMLOOTPOLICY` | | What becomes of a dead mob's items (`20% RUIN`...) |

**Remort and retire.** `REMORT` (inherited) strips a character of levels,
skills, stats and personal attributes, keeping equipment, banks, properties
and clan, so it can try another direction. Who may is `REMORTMASK`
(`-LEVEL +>60`), and what it keeps is `REMORTRETAIN`: a comma list of
`HITPOINTS`, `MANA`, `MOVES`, `FACTIONS`, `LEVELS`, `ATTACK`, `QUESTPOINT`,
`DAMAGE`, `SKILLS`, `SKILLSAT100`, `DEFENSE`, `EXPERTISES`, `BONUSSTATPOINTS`,
and the specials `RACE`, `GENDER`, `CHARCLASS`, `STATS`, `WARRANTS`,
`VISITATION`, each optionally with a percent (`100% FACTIONS`). An achievement
with the `REMORT` flag is attainable again afterwards. `RETIRE` deletes the
character after asking the player to re-enter the character's password, which
single-player characters never see (so RETIRE is probably unusable there; unverified). Both are engine-ini settings; they are inherited behaviour that is
not exercised by CoffeeSUD's own fixtures, so test them in your world before
relying on them.

## Achievements and titles

Source: [Achievements.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/Achievements.java),
[CustomAwards.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/CustomAwards.java).
Fixture: [p5-awards.js](../../tools/fixtures/fixes/p5-awards.js).
In-game help: `AHELP ACHIEVEMENTREWARDS`.

An achievement is an automatically checked goal tracked on the character (or
the profile) as a tattoo. They are defined in `resources/achievements.ini`; a
world's own `world/resources/achievements.ini` replaces it, and
`DISABLE=STDACHIEVEMENTS` turns the stock ones off. The file has the sections
`[PLAYER]` (a character earns it), `[ACCOUNT]` (the profile earns it) and
`[CLAN]`. Each achievement is one line:

```text
TATTOONAME= EVENT="KILLS" NUM=50 ZAPPERMASK="-RACE +Orc" DISPLAY="Orc slayer" TITLE="Orc-bane" REWARDS="100 XP 1 QP"
```

`TATTOONAME` is the unique tattoo that marks it (it must not begin with a digit).
Common fields: `EVENT`, `DISPLAY`, `TITLE`, `REWARDS`, `PLAYERMASK` (who can
progress it), `VISIBLEMASK`, `DURATION` (ticks), `FLAGS` (`REMORT`). The events
most tied to characters include `LEVELSGAINED`, `CLASSLEVELSGAINED`,
`LEVELUP`/`CLASSLEVELUP`/`LEVELDOWN`/`CLASSLEVELDOWN`, `STATVALUE` (a stat
above or below a value), `SKILLPROF`, `SKILLUSE`, `RANKUP`, `AGECATEGORY`,
`TIMEPLAYED`, `DEATHS`, `REMORT`, `RETIRE`, `FACTION`, `FACTIONS`, `EXPLORE`,
`ROOMENTER`, `KILLS`, `QUESTOR`, `ACHIEVER`, `CHARACTERS` and `SCRIPTED`; the
header of `achievements.ini` gives each event's own parameters.

**Rewards.** `REWARDS` is a list of `amount word` entries:

| Reward | Example |
| --- | --- |
| Experience, quest points, money | `10 XP`, `1 QP`, `10 "gold coins"` |
| A stat | `2 MAXSTRENGTH`, or a bonus such as `10 BONUSCOMMON`, `BONUSCRAFT`, `BONUSNONCRAFT`, `BONUSLANGS`, `BONUSCHARSTATS` (extra point-buy points), `BONUSCHARLIMIT`; prefix `ACCOUNT` for the profile (`"ACCOUNT BONUSCOMMON"`) |
| An ability, from a level | `17 Spell_Light`, or with settings `21 "Spell_Light(autogain=false)"` (keys `AUTOGAIN`, `MASK`, `PARMS`, `PREREQS`) |
| An expertise | `7 VEXING1`, `12 VEXING1(autogain=false)` |
| Trains and practices | `1 PRACTICES 1 TRAINS` |
| Protection from purging | `1 NOPURGE` |
| A tattoo | `1 "TATTOO(ID=RACE_ELF DESC=\"create an elf character\")"`; `ACCOUNT=true` puts it on the profile (this is how races and classes are unlocked) |
| An item or a follower from the catalog | `1 "ITEM(ID=\"a small mace\")"`, `1 "MOB(ID=\"a faithful hound\")"` |
| CoffeeSUD: a script | `1 SCRIPT(LOAD=progs/x.script)` or `1 SCRIPT(ONCE_PROG 100;mpecho Well done.;~)`, run once on the character: its `ONCE_PROG`, then its `FUNCTION_PROG ONACHIEVE` |
| CoffeeSUD: an effect | `1 EFFECT(ID=Prop_Adjuster PARMS="con+1" TICKS=0)`: for good with `TICKS=0` (kept with the character), otherwise for that many ticks |
| CoffeeSUD: standing | `500 FACTION(ID=elves.ini)` or `1 FACTION(ID=elves.ini AMOUNT=500)` |
| CoffeeSUD: a script variable | `1 VAR(NAME=chapter VALUE=2)` (`WHO=` for another name, `*` for the game's) |
| CoffeeSUD: a known recipe | `1 RECIPE(SKILL=Weaponsmithing NAME="rune blade")` |

Quotes inside a `REWARDS="..."` value are escaped with backslashes; the header
of `achievements.ini` shows the escaping. The same reward syntax is used by
perks, creation answers, ranks, `MPREWARD` and quest rewards. On an `[ACCOUNT]` achievement, rewards go to each *later*
character of the profile, so make once-only and repeatable grants deliberate.
`ACHIEVEMENTS` shows what each gives (`ACHIEVEMENTS ALL`, `NOW`, `FUTURE`,
`AWARDS`...). `CREATE ACHIEVEMENT` and `MODIFY ACHIEVEMENT` edit the definitions (security
flag `ACHIEVEMENTS`).

**Titles.** A title is text shown with a character's name. They come from
achievements' `TITLE`, rank ladders' `RANK<n>.TITLE`, `MPTITLE who (-) text`
(adds or removes one), and `resources/titles.ini`:

```text
{* the dude}=-area "+new area" +midgaard
Father *=1:-area "+new area" +midgaard
```

Each line is `TITLE=[MAX HOLDERS:]ZAPPER MASK`: `*` is the character's name;
a title in braces is mandatory; with a maximum the most idle holder loses the
title when it is exceeded. Titles are re-evaluated every game hour. The `TITLE`
command lets a player pick a prefix and a suffix title, or mark the current
one for the random rotation. `STDTITLES` in `DISABLE` turns the stock file off.

## Profile, party and recruitment

One profile owns multiple playable characters. `PROFILE` lists them; `SWITCH`
changes the controlled character. `PARTY ADD` brings in a profile character,
`PARTY REMOVE` removes them from the group but leaves them active, and `PARTY
REST` sends them back to the profile. `AS name command` orders one character.
Windows are views of characters in the same running world; they do not create
independent campaigns. The full model, the commands and the turn rules are in
[31-saves-and-party.md](31-saves-and-party.md) and
[the party rules](../party-rules.md); the settings that bear on characters are:

| Setting | Meaning |
| --- | --- |
| `PROFILEMAX` | Most characters in the profile (`0` unlimited; `1` for a one-character world) |
| `NEWCHARS` | `ALWAYS`, `FIRSTONLY`, `CHEAT` (with `CHEAT ON`) or `MASK` (`NEWCHARMASK`, checked on the character played last, as `-LOCATION +ROOMSTR(Adventurers)`); `NEWCHARMESSAGE` says where to go when one is refused. The first character is always made |
| `PARTYMAX`, `ACTIVECHARMAX` | Most in the party at once; most profile characters in the world at once (`0` unlimited). Lowering a limit does not delete existing characters |
| `PARTYSUMMON`, `PARTYSUMMONMASK`, `PARTYSUMMONMESSAGE` | Whether remote characters can be called in (`ALLOW`, `MEET`, `MASK`) |
| `PARTYROLES` | `"healer:-BASECLASS +Cleric +Druid; scout:-BASECLASS +Thief"`: `PARTY` and `NEW` say which roles no character fills |

`MPRECRUIT npc player (PARTY|REST) (NAME=name)` turns a met NPC into a
character of the player's profile (keeping its race, class and level, stats,
abilities, effects, inventory, factions, tattoos and description) and removes
it from the world as an NPC; it joins the party (the default) or rests. It
refuses, telling the player, when the profile or party is full or the name is
taken. `Prop_Recruitable` on an NPC gives players a `RECRUIT` command route:
`MASK`, `COST`, `MESSAGE`, `NAME` (see
[23-properties-reference.md](23-properties-reference.md#prop_recruitable)). From
JavaScript: `Packages.com.planet_ink.coffee_mud.core.Recruits.recruit(npc, player,
'PARTY', name)`. Ordinary followers, hired help and summoned mobs remain distinct
from recruited player characters. Training that consumes game time (P30) is not
implemented; teachers, followers, affinity, hirelings and recruitment are
available now.

## Command quick reference

Player commands (all forms are as `getAccessWords()` registers them):

| Command | Use |
| --- | --- |
| `NEW [name] (QUICK (template))` | Profile menu: make a character |
| `SCORE` (`SC`), `EXPERIENCE` (`XP`, `EXP`, `EXPER`) | Stats, levels, experience (and deferred experience) |
| `TRAIN (thing) (teacher) (NOPROMPT)` (`TR`, `TRA`) | Spend trains: stats, pools, practices, class change, `GAIN` |
| `PRACTICE [skill] (teacher)` (`PRAC`) | Raise a skill's proficiency |
| `GAIN [skill]`, `TEACH [student] [skill]`, `TEACHLIST (teacher)` | Learn and teach skills and expertises |
| `QUALIFY (kind)` (`QUAL`), `WILLQUALIFY (level) (class)` | What you can learn now, and at a level or in a class |
| `SKILLS`, `EXPERTISES` (`EXPS`), `SKILLTREE (class or skill)` (`STREE`) | What you know, and the tree |
| `PERKS`, `PERK [name]` | Perks and picks |
| `RANKS` | Rank ladders |
| `RESPEC (thing\|ALL)` (`RETRAIN`) | Undo purchases where a place allows |
| `TITLE` | Choose titles |
| `ACHIEVEMENTS (...)` | Achievements and their rewards |
| `RECRUIT [name]` | Ask an NPC to join your profile |
| `REMORT`, `RETIRE` | Start over; delete the character |

Builder and developer commands:

| Command | Use |
| --- | --- |
| `CREATE\|MODIFY\|DESTROY RACE <ID>`, `CREATE MIXEDRACE <MOTHER> <FATHER>` | Generic races (flag `CMDRACES`) |
| `CREATE\|MODIFY\|DESTROY CLASS <ID>` | Generic classes (flag `CMDCLASSES`) |
| `CREATE\|MODIFY ALLQUALIFY ...`, `LIST ALLQUALIFYS` | Skills every class (or each of several) can qualify for |
| `LIST RACES`, `LIST RACECATS`, `LIST CLASSES`, `LIST EXPERTISES`, `LIST TITLES` | Inspect |
| `CATALOG CATEGORY PLAYERTEMPLATE`, `CATALOG <mob>` | File a ready-made character |
| `KIT <name> (who)`, `TESTCHAR <name> (KIT=<kit>) (PARTY)` | Apply a kit; make a test character (builder mode or `DEV=1`) |
| `PROGRESSION <class> (race) (level)`, `PROGRESSION XP (area)` | Level curve; experience in the content |
| `CHARGEN COMBAT\|NEW\|PLAYER\|EQUIP (class) (level)` | Example characters for balance comparison |
| `MPEXP`, `MPLEVELUP`, `MPLEVELDOWN`, `MPPERK`, `MPRANK`, `MPRESPEC`, `MPRECRUIT`, `MPTITLE` | Script commands; see [27-mobprog-commands.md](27-mobprog-commands.md) |
| `HASPERK()`, `RANK()`, `EXPTONEXT()`, `HASTATTOO()` | Script functions; see [28-mobprog-functions.md](28-mobprog-functions.md) |
| `WORLD SET KEY=value` | Write a key to `world/world.ini` in builder mode (takes effect at the next start) |

## Worked examples

### 1. A campaign opening with origins, kits, a question and perks

Goal: players choose between a temple orphan and a dockside sailor, may pick a
starting kit, answer one personality question, and spend two points on traits;
later characters start at the level of the profile's lowest character.

`world/world.ini`:

```ini
START=Ashford#1
STARTHP=24
NEWCHARLEVEL=PARTYMIN
NEWCHARXP=FLOOR
PROFILEMAX=6
PARTYMAX=4
CLASSSYSTEM=SUB
SHOWLOCKED=YES
```

`world/creation.ini`:

```ini
STEPS=RACE,GENDER,STATS,CLASS,ORIGIN,KIT,TEMPER,TRAITS,FACTIONS,REVIEW

[TEMPER]
PROMPT=How do you meet trouble?
CHOICE=NAME="Boldly" HELP="You step forward first." EFFECTS="1 STRENGTH"
CHOICE=NAME="Carefully" HELP="You look before you leap." EFFECTS="1 WISDOM"
```

`world/origins.ini`:

```ini
[ORPHAN]
NAME=Temple Orphan
DESCRIPTION=Raised beside the temple library.
MASK=-RACE +Human +HalfElf
START=Ashford#12
ABILITIES=Prayer_Bless 25
TATTOOS=ORPHAN
MONEY=5

[SAILOR]
NAME=Dockside Sailor
DESCRIPTION=Hauled rope since you could walk.
START=Ashford#40
ABILITIES=Skill_Recall 50
MONEY=15
```

`world/kits/warm-clothes.kit` and `world/kits/travel-pack.kit`:

```text
starting all
choose gear
title Warm clothes and a lantern
item 2 Torch
```

```text
starting all
choose gear
title A pedlar's travel pack
item 3 Ration
gold 10
```

`world/perks.ini`:

```ini
PICKS=GENERAL every 5 levels
TRAITS=2
TRAIT_LUCKY NAME="Lucky" POOL=TRAIT COST=2 REWARDS="1 CHARISMA"
TRAIT_KEEN NAME="Keen eyed" POOL=TRAIT COST=1
FLAW_BADBACK NAME="Bad back" POOL=TRAIT COST=-1
PERK_PACKMULE NAME="Pack mule" POOL=GENERAL REWARDS="1 STRENGTH"
```

To test: start a new game, `NEW Tester`, and walk the flow as an ordinary
player. You are asked race, gender, stats and class, then the origin menu
(the orphan appears only for humans and half-elves), the kit menu (two kits in
`gear`), `TEMPER`, the traits (Lucky costs the whole budget; Bad back gives a
point back; `DONE` must not overspend), factions, and the review sheet where
`BACK CLASS` returns you to the class step. After the review: the origin's
room, the kit, the answer's `1 STRENGTH`, the trait rewards and the starting
age are applied. Then create a second character and check it starts at the
first one's level with the experience that level requires (`NEWCHARXP=FLOOR`).
Check `HASTATTOO` for `ORIGIN_ORPHAN` and `CHARCR_TEMPER_BOLDLY` in a script
of the temple, and `PERKS` in play.

### 2. A new race and a new class

Goal: an Emberkin race, locked until the player earns an oath, and a
Pyromancer class that Emberkin and humans can take.

In builder mode (see [18-installing-and-running.md](18-installing-and-running.md)):

```text
CREATE RACE Emberkin
```

In the editor set (type the number, then the value; Enter on the main menu to
finish):

| Field | Value |
| --- | --- |
| Racial Category | `new Emberkin` |
| Availability | `9` (Fantasy skills only: not offered until unlocked) |
| Unlock hint | `Swear the Ashen Oath in the Cinder Hall` |
| Aging Chart | `0,1,3,15,60,90,130,150,170` |
| Base Weight, Weight Variance | `140`, `40` |
| Base Male/Female Height, Height Variance | `66`, `64`, `10` |
| CharStat Adjustments | `STRENGTH` `1`, `SAVE VS FIRE` `25`, `SAVE VS COLD` `-10` |
| Cultural Abilities | add `Common` (proficiency `100`, character level `0`, auto-gained `Y`) |
| Racial Effects | add `Prop_Resistance`, level `1`, parameters `fire 25% cold -10%` |
| Extra Practices/1stLvl | `1` |

Then the class:

```text
CREATE CLASS Pyromancer
```

| Field | Value |
| --- | --- |
| Base Class | `Pyromancer` |
| Availability | `1` |
| Required Races | add `All` |
| Level Cap | `-1` |
| HP/Level Formula | `((@x6<@x7)/4)+(1*(1?4))` |
| Mana/Level Formula | `((@x4<@x5)/3)+(1*(1?8))` |
| Attack Attribute | `INTELLIGENCE` |
| Armor Restr. | `CLOTH` |
| Min. Stats | add `INTELLIGENCE` `12` |
| Class Abilities | `Spell_Light` level 1, proficiency 50, auto-gained, public; `Spell_BurningHands` level 1, proficiency 25, auto-gained; `Spell_Fireball` level 5, proficiency 0, **not** auto-gained, pre-requisite `Spell_BurningHands(50)` |
| Sub Class Switch Rule | `NONE` |

Check it: `SKILLTREE Pyromancer` shows Fireball under Burning Hands, locked by
level for a level-1 character. `LIST CLASSES` and `LIST RACES` list both. With
`SHOWLOCKED=YES`, `NEW` shows `Emberkin (locked: Swear the Ashen Oath in the
Cinder Hall)`. Give the oath as an achievement reward in
`world/resources/achievements.ini`, on a profile achievement so it unlocks the
race for later characters, or give the tattoo `RACE_EMBERKIN` from a script with `MPTATTOO who RACE_EMBERKIN`. Save
(builder mode writes `world/custom/races/Emberkin.cmare` and
`world/custom/classes/Pyromancer.cmare`).

### 3. A guild ladder, a retraining shrine and a discovery

Goal: a guild with ranks that rise on their own early and are given later by
a warden; a place to retrain; and a hidden grotto that pays experience once
per profile.

`world/ranks.ini` (see [Rank ladders](#rank-ladders)):

```ini
[LANTERN]
NAME=Order of the Lantern
RANKS="Initiate, Acolyte, Lantern-bearer, Warden"
RANK1.REQUIRES="-LEVEL +>=3"
RANK1.NEXT=reach level 3
RANK2.REQUIRES="-LEVEL +>=8"
RANK2.REWARDS="2 QP"
RANK3.TITLE=*, Lantern-bearer
RANK3.NEXT=speak to the warden at the vigil
```

The warden (a mob with this script) promotes a Lantern-bearer who asks:

```text
SPEECH_PROG p I seek the vigil
  if hastattoo($n LANTERN_3)
    mpechoat $n Keep the lamp lit, Lantern-bearer.
    mprank $n LANTERN +1
  else
    mpechoat $n You are not yet ready for the Vigil.
  endif
~
```

A retraining shrine: on the shrine room (or an NPC) add the effect
`Prop_Respec` with `WHAT="SKILLS,STATS" COST="5 per skill" ONCE=YES`. A player
standing there types `RESPEC`, sees what was bought and what each undo costs,
and `RESPEC ALL` refunds the trains and practices; each skill undone costs 5
gold.

A discovery: on the grotto room add `Prop_Discovery` with `XP=150
ONCE=PROFILE MESSAGE="Behind the falls lies a hidden grotto!"
TATTOO=FOUND_GROTTO`. The first character of a profile to enter pays; the
profile tattoo stops later characters collecting again. Use `ONCE=PARTY` if the
whole party should share one reward and `ONCE=CHARACTER` for one each.

## Persistence notes

| Thing | Lives in | Notes |
| --- | --- | --- |
| `world.ini` character settings | `world/world.ini` | Read at start; `WORLD SET KEY=value` writes it in builder mode and applies at the next start |
| `coffeemud.ini` settings | `coffeemud.ini` | Engine ini; restart to change |
| `creation.ini`, `origins.ini`, `perks.ini`, `ranks.ini`, kits | `world/` | World files, re-read as used (creation, perks and ranks cache by file time). They are not part of a saved campaign (see [state-and-persistence.md](state-and-persistence.md)) |
| Generic races and classes | `world/custom/races/`, `world/custom/classes/` | Built into the database cache at start; builder-mode SAVE writes them back |
| Ready-made characters | `world/catalog.cmare` | Catalog mobs in the category `PLAYERTEMPLATE` |
| Achievements, titles, hints, `EXP_CHART` | `world/resources/...` copies of `resources/...` | A world's own copy replaces the stock file |
| `progression.js` | `world/scripts/` | Read again when it changes |
| A character's results | Its record in the campaign save | Race, class and levels, stats, practices and trains, abilities, expertises, factions, tattoos (`ORIGIN_`, `PERK_`, `CHARCR_`, `<KEY>_<n>` ranks, `RANKREWARD_`, `DISCOVERY_`...), titles, age and birthday, purchase records, deferred experience |
| Profile tattoos | The profile | Shared by every character in it |

Editing an origin, a kit, a perk, a question or a template changes only
characters made afterwards; it does not rebuild or re-gift existing ones. The
marks a character holds are names: renaming an origin, a perk, a question or a
choice, or a rank ladder's key, orphans the marks existing characters have, so
keep IDs stable and add new ones instead. Saved characters refer to their race
and class by ID: do not delete or rename a generic race or class a saved
character uses. Changing a stat definition (`ADDCHARSTAT_`, `REPLACECHARSTAT_`)
needs a restart. A new campaign (`NEW`) always starts from the world files; a restore uses
the saved characters. Test across a save and restore as in
[state-and-persistence.md](state-and-persistence.md).

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| A new class is chosen by nobody, or missing in `NEW` | **Required Races** is empty (an empty list admits no race; add `All`); **Availability** is 0; its **Base Class** is the default `Commoner` and `CLASSSYSTEM=SUB` only offers base classes and `ANY` classes at creation; its **Min. Stats** or **Qualifications** exclude the stats rolled; or `MUDTHEME` excludes its theme |
| A new race is missing in `NEW` | **Availability** is 0 (the default for a new race), or has the skill-only bit (8 to 15), or its theme does not meet `MUDTHEME`; or `DISABLE=RACE_<ID>` / `STDRACES` |
| The race list text names races the game does not offer | `races.txt` and `classes.txt` are hand-written; use `@RaceList@` / `@ClassList@`. The stock text still describes Drow, which is skill-only |
| A race or class kept behind a tattoo does not show the hint | `SHOWLOCKED=YES` and an **Unlock hint** are both needed |
| Origin or kit items are blank | `ITEMS=GenItem` and `item GenItem` make a blank generic item; use catalog names or real item classes. A name that is not an item class or in the catalog is logged and skipped |
| A kit lowers a skill | `ability ID n` sets proficiency to n even if the character had more; leave the number off to give 100 |
| A question's class mask never matches | The question comes before `CLASS` in `STEPS`; place it after |
| `STEPS` is ignored in part | `RACE`, `GENDER`, `STATS`, `CLASS`, `FACTIONS`, `DEITY` keep the engine's order; the log says so |
| The review never appears | `REVIEW` is not in `STEPS`, or the character is a template or `QUICK` |
| `STARTINGITEMS` ignores an item | A name that is also a race ID starts a per-race list; use a catalog name instead |
| `TRAIN GAIN` does nothing | `TRAINCOSTS` has no `GAIN` entry. The ini comment says `TRAINS 1 7 PRACTICE`, but the code (and the default) use `GAIN` |
| No role-play experience | `RPAWARDS` is blank; no role-play experience is earned while fatigued, hungry or thirsty; the code's name for the emote-yourself award is `EMOTE-OTH` (the ini comment says `EMOTE-OTHER`) |
| Lower-level victims give no experience at `EXPRATE=0` | As the code reads, a victim more than `EXPRATE` levels below the killer gives nothing, so at 0 any lower-level victim pays 0, while equal or higher ones pay the unscaled amount; the ini comment says 0 means no level scaling. A large `EXPRATE` approximates "no scaling" |
| A rank does not rise by itself | The next rank has no `REQUIRES`, or the mask is not met; ranks are re-checked at level-up, on `RANKS`, and by `MPRANK ... CHECK` |
| A perk's reward is given twice | `MPPERK who -PERK` takes the perk away but its rewards stay, and taking the perk again gives them again; do not remove and re-grant a perk whose rewards are stat or experience gifts |
| `SKILLTREE` shows few skills | The class's abilities have no pre-requisites (the tree is flat), or they are `SECRET`/`MASKED` with no hint |
| Level-ups give the wrong practices or trains | A formula that does not compile is logged and falls back to the usual; check the log for `LevelGains` |
| `NEWCHARLEVEL=PARTYAVG` gives level 1 | The profile has no other character yet |
| A template character lacks the origin, kit or question gifts | By design: templates skip those steps (see [templates](#ready-made-characters-player-templates)) |
| Everything works as the builder but not as a player | Builder mode makes the player an Archon; test `NEW` in an ordinary game |

## See also

- [Abilities and effects](25-abilities-and-effects.md): generic abilities, diseases, poisons, expertises, teaching and granting.
- [Saves and party](31-saves-and-party.md) and [party rules](../party-rules.md): the profile, party, windows, `NEW` limits, saved games.
- [Combat and encounters](29-combat-and-encounters.md): attack, armor, saves, downed states and death rules.
- [Properties reference](23-properties-reference.md): `Prop_Trainer`, `Prop_Respec`, `Prop_Discovery`, `Prop_PlayerTemplate`, `Prop_Recruitable`, `Prop_ModExperience`.
- [Behaviors and masks](24-behaviors-and-masks.md): `MOBTeacher` and the zapper masks used throughout this chapter.
- [MOBPROG commands](27-mobprog-commands.md), [functions](28-mobprog-functions.md) and [triggers](26-mobprog-triggers.md): `MPEXP`, `MPLEVELUP`, `MPPERK`, `MPRANK`, `MPRESPEC`, `MPRECRUIT`, `HASPERK`, `RANK`, `EXPTONEXT`, `AGE_PROG`.
- [JavaScript](05-javascript.md): `progression.js`, JS-defined races and classes.
- [Mobs](21-mobs.md) and [items](22-items.md): building template characters, outfits and catalog gifts.
- [Society](09-society.md): factions, clans and organisations that creation and ranks use.
- [World simulation](10-world-simulation.md): the calendar that ages characters.
- [World settings](world-settings.md), [configuration](19-configuration-and-administration.md), [state and persistence](state-and-persistence.md), [tool reference](tool-reference.md).
