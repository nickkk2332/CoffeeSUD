# CoffeeSUD: developer tools and engine capabilities

`PLAN.md` turned CoffeeMud into a single-player game. This file plans what
comes next on the engine side: the capabilities a world needs that the
engine lacks or only half has, and the tools to build with them. The world
itself is the owner's to write (see `PLAN.md`, phase 6).

Each item says what the engine has now, what is missing, the design, and
where it stands. Ground rules are those of `PLAN.md`: every commit keeps the
build green and `tools/check.sh` passing (the fixes suite and the smoke
test), the end of each step also the engine suite (`ENGINE=1
tools/check.sh`), and changes to the engine's behaviour need a reason.

## 1. Unique and rare items: planned

A builder wants, say, one unique sword in the world, with a rare chance to
be found in a certain kind of chest.

### What the engine has

The catalog (the `CATALOG` command in builder mode, kept in
`world/catalog.cmare`). A catalog item can have a spawn rule:

- `LIVE`: a mob that matches the rule's mask spawns carrying it.
- `DROP`: it may be on a matching mob's corpse when the mob dies.
- `ROOM`: it appears in a random matching room, on the catalog's timer
  (`CMCatalog.tick`).

Each rule has a rate (the chance), a mask (a zapper mask saying which mobs
or rooms) and a cap (the most copies in the world at once), so a cap of 1
already makes an item unique, at a time. An item has one rule.

### What is missing

1. **Containers.** No rule puts an item in a container: "a rare chance to be
   in an iron chest" cannot be said.
2. **The cap across saves.** The catalog counts a copy when one is made by
   copying (`CMCatalog.newInstance`, from `copyOf`), not when one is loaded:
   a copy restored from a save, lying in a room or in a character's pack,
   is not counted, so after a restore a second "unique" item can appear.
3. **Unique ever.** The cap means at most so many at once: a copy that is
   destroyed or lost can appear again. There is no "once only".
4. **One rule per item.** "Drops from the chieftain, or is rarely found in
   iron chests" needs two.

### Design

Both choices below are made per item, not for the whole game.

- **How unique.** Each catalog item chooses one of:
  - *at a time* (as now): at most the cap's number of copies in the world
    at once; one lost can appear again;
  - *once ever*: once the cap's number have appeared, no more, even if all
    are destroyed;
  - *once for a while*: as once ever, but a copy lost or destroyed can
    appear again after a given time (in game days).

  The game keeps, for each item, how many copies have ever appeared and
  when the last was lost, and a save holds that with the rest of the game.
- **Counting every copy.** Copies are counted as they come into the world
  from anywhere: made, loaded with the world or a save, or carried in by a
  character. The cap then holds after a restore.
- **A container rule.** `CONTAINER`, with a mask on containers (by name,
  kind, area and the like), a rate and the item's cap. When it rolls, per
  rule:
  - *on opening*: the first time a matching container is opened, the rate
    is rolled once; a container rolled stays rolled until its contents
    reset, so opening it again does not roll again;
  - *on a timer*: as `ROOM`, the item is put in a random matching container
    somewhere in the world, to be found;
  - or both.
- **Several rules per item**, each with its own spawn kind, rate and mask,
  sharing the item's cap and uniqueness.
- **Authoring.** The `CATALOG` editor in builder mode sets all of it, and
  `world/catalog.cmare` holds it, readable and editable by hand.

### Tests

A smoke session: a catalog item with a container rule, on opening, at a rate
of 100% and a cap of 1. Opening a matching chest finds it; opening a second
does not; a save and a restore do not make a second; destroyed, a once-ever
item does not come back, and a once-for-a-while one does after its time.

## 2. Builder capabilities: from the audit

What a builder can do without editing the engine's Java, and what is
missing, was audited across the engine: [`audit/README.md`](audit/README.md)
has 409 proposed additions in 18 themes, each with what exists today, what
to add and an example.

The owner went through them on 2026-10-31: 388 yes, 20 later and 1 undecided
(`PR21`, a side panel in the character windows). The *later* items are
wanted but out of the current scope; they were moved to
[`LATER.md`](LATER.md). The *yes* items are planned here, in the steps
below, each item as the audit describes it. Before an item is built it is
checked against the code again, since about one in eight overstates the gap;
if it turns out smaller, or already possible, the owner is told.

### Proposed order

Proposed, for the owner to change: the first four steps lay foundations the
others use; the rest follow the audit's themes and can be taken in any
order, by what the world needs first. Within a step, items go by value, then
effort, as listed. Each item, or a few small ones together, is one commit
that keeps the build green and the smoke test passing, with a smoke check of
its own where it can have one.

#### 2a. Fixes and JavaScript defaults (15)

Confirmed bugs in what the engine already has, and JavaScript that runs
without approval, with a time limit and readable errors. Small, and much of
the rest stands on them.

ST1, ST18, SC14, N18, N19, I1, I18, I19, G2, F5, Q9, PR5, JS21, JS1, JS2

Status: done but for one part of SC14. Each fix has a test in
`tools/fixes.sh` (JavaScript tests run with JRUN, and played sessions where
a fix needs real play), which runs in a few minutes.

New for builders: `DISMOUNT_PROG` and `REMOVING_PROG`; `FACTIONVALUE` and
`FACTIONBAND`, and band codes for `MPFACTION`; text forms of `ISNPC`,
`ISPC`, `ISIMMORT`, `EVAL` and `STRCONTAINS`; `GAMENAME`,
`world/text/title.txt` and `world/text/prologue.txt` (see the ini); and
`JSTIMELIMIT` and `JS ERRORS` (see `AHELP JS`). `JSCRIPTS=2` is the default.

- JS21: Rhino is 1.7.15.1 (the owner put it in `lib/js.jar`), and scripts
  are ES6: arrow functions, template strings, `let` and `const`, and the
  newer built-ins. Script blocks keep their quotes and backticks as
  written. Two limits: JavaScript classes (`//extends`) are compiled to
  Java, so `JSTIMELIMIT` does not cover them; and lines typed at JCONSOLE
  still turn backticks into quotes, so template strings work in JRUN files
  and script blocks but not there.
- SC14's last part, `scriptableTest.script` as an automated check, moves to
  2c: its harness is gone, and it needs time to pass and fixtures that the
  playtest runner (DT15) will give it.
- Found on the way, for 2b: a builder's change made through JRUN without
  going through the database (as builder commands do) is written to the
  world files at quit, but the next game loads the database cache, which
  lacks it.
- DOCS lists more property defects than I1 named (area `Prop_InstantDeath`,
  `Prop_LanguageSpeaker` HOMEONLY, `Prop_WeaponImmunity` LEVEL,
  `Prop_RoomView` ATTACK, and others in `DOCS/conditions/b076` and `b078`);
  they are left for the owner to decide on.

#### 2b. Saves and world files (24)

What a save still loses (quest state, conquests, warrants, vessels,
generated places, behaviour state, script engine state), and the rest of the
world's content moved into `world/`. This finishes what phase 4 began: every
later feature keeps its state across a save.

S1, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, S12, S13, S15, S16, S17, S18,
S19, S20, S21, S22, S23, S24, S25

Status: under way. Played tests of what a save keeps are the `saves` group
of `tools/fixes.sh` (`tools/fixes.sh saves` runs them alone).

- S1 done. The save keeps each quest's MPQSET variables (`<VARS>`) and the
  wait of a quest not running (`<WAIT>`). `Prop_QuestGiver` keeps whom it
  offered the quest to as the quest variable `OFFERED:<name>`, and
  `QuestChat` reads back its `CHAT:<name>` variable; both last for the run
  of the quest, so a quest that runs again offers and says them again. A
  player's savable scripts keep which ONCE_PROGs and QUEST_TIME_PROGs have
  run (`<SSTATE>`, tied to a hash of the script), the start of S7's script
  state. Found on the way: QuestChat lines with a said-once code
  (`(hello::greet)`) never matched, since MudChat looked them up by the
  word `hellogreet`; fixed.
- S2 done. `SAVEMODE=ANYWHERE|NOTINCOMBAT|SAVEPOINTS|IRONMAN` (ini or
  command line) is taken by a new game and kept in its saves' headers.
  `Prop_SavePoint` (MSG= on saving), `Prop_NoSave` (MSG= on refusing) and
  `Prop_AutoSave` (ONCE=CHAR, SLOT=, ROTATE=, MSG=) go on rooms and areas.
  SAVE, QUIT's offer and autosaves all ask `SaveGame.whyNotSave`, the one
  check ST6's refusable SAVE will hook into. Under SAVEPOINTS a fight also
  refuses, and an ironman game has one save, written at every QUIT, with
  RESTORE refused in play. An autosave skips fights but not places, since a
  builder put it there.
- S3 done. `WORLDDIR/world.ini` is read after `coffeemud.ini` and before the
  command line, for the keys in `WorldSettings` (the audit's list, the
  calendar, SAVEMODE and the party settings, and a few more game rules such
  as COMBATSYSTEM, HUNGER and DIRECTIONS; START_, DEATH_, MORGUE_, FORMULA_,
  ADDCHARSTAT_ and REPLACECHARSTAT_ keys); others are reported at launch and
  ignored. There is no currency key in the ini (a currency is an area's),
  and no race or class allow list, which DISABLE and CHARCREATIONSCRIPTS
  cover. `WORLD` and `LIST WORLDINI` show each key's value and where it came
  from; `WORLD SET KEY=VALUE` (builder mode) rewrites its line. `world.sum`
  covers the file.
- S20 in part: an ABILITY_, COMMAND_, RACE_, CHARCLASS_ or FACTION_ disable
  flag may end in `*`. The STDACHIEVEMENTS, STDTITLES and STDSOCIALS flags
  wait for S6, which gives the world its own copies of those files. Found on
  the way: reloading the DISABLE flags left disabled abilities from before.
- S4 done. Each mob, character and item in `world.xml` has a number (SID),
  and a `<RIDES>` section says who rides what; the rides are put back once
  both ends are, the characters' after they log in. Area files have `IRIDE`
  for an item riding something, so a cart can be placed hitched to its
  team. Found on the way: a mount counts as unsavable while anyone riding it
  is, and characters are, so a save left out the player's own horse; the
  save now asks without the player's characters on it.

#### 2c. Developer and builder tools (50)

Inspecting, tracing, checking and testing the world, and faster building.
Everything after this is built and tested with them.

DT1, DT2, DT3, DT4, DT5, DT6, DT7, DT8, DT9, DT10, DT11, DT12, DT13, DT14,
DT15, DT16, DT17, DT18, DT19, DT20, DT21, DT22, DT23, DT24, DT25, DT26,
DT27, DT28, DT29, DT30, DT31, DT32, DT33, DT34, DT35, DT36, DT37, B1, B2,
B3, B4, B5, B6, B7, B8, B9, B10, B11, B12, B13

#### 2d. Scripting (59)

Script triggers, the script language, and JavaScript: the hooks most later
items use.

ST2, ST3, ST4, ST5, ST6, ST7, ST8, ST9, ST10, ST11, ST12, ST13, ST14, ST15,
ST16, ST17, ST19, ST20, ST21, ST22, ST24, ST25, ST26, ST27, ST29, SC1, SC2,
SC3, SC4, SC5, SC6, SC7, SC8, SC9, SC10, SC11, SC12, SC13, SC15, SC16, SC17,
JS3, JS5, JS6, JS7, JS8, JS9, JS10, JS11, JS12, JS13, JS14, JS15, JS16,
JS17, JS18, JS19, JS20, JS23

#### 2e. Quests (15)

Q1, Q2, Q3, Q4, Q5, Q6, Q7, Q8, Q10, Q11, Q12, Q13, Q14, Q15, Q16

#### 2f. NPC behaviour and dialogue (21)

N1, N2, N3, N4, N5, N6, N7, N8, N9, N10, N11, N12, N15, N16, N17, N20, N21,
N22, N24, N28, N29

#### 2g. Combat and encounters (24)

C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11, C12, C13, C14, C15, C16, C17,
C18, C19, C20, C21, C22, C23, C25

#### 2h. Items, properties and puzzles (23)

I2, I3, I4, I5, I6, I7, I8, I9, I10, I11, I13, I14, I15, I16, I17, I20, I21,
I22, I23, I24, I25, I26, I27

#### 2i. World systems (25)

Time, weather, environment, economy and law.

W1, W2, W3, W4, W5, W6, W7, W8, W9, W10, W11, W12, W13, W14, W15, W16, W17,
W18, W19, W20, W21, W22, W23, W28, W29

#### 2j. Generators and random content (16)

G1, G3, G4, G5, G6, G7, G8, G9, G10, G11, G12, G13, G14, G15, G16, G17

#### 2k. Crafting, gathering and housing (21)

CR1, CR2, CR3, CR4, CR5, CR6, CR7, CR8, CR9, CR10, CR11, CR12, CR13, CR14,
CR15, CR16, CR17, CR18, CR19, CR20, CR21

#### 2l. Factions and organisations (18)

F1, F2, F3, F4, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18,
F20

#### 2m. Travel, vehicles and instances (16)

T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T14, T16, T17, T18, T19

#### 2n. Languages and vocabulary (14)

L1, L2, L3, L4, L5, L6, L7, L8, L9, L10, L11, L12, L13, L14

#### 2o. Characters, progression and custom abilities (29)

P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17,
P18, P19, P20, P21, P22, P23, P24, P25, P26, P27, P28, P29

#### 2p. Presentation (18)

PR1, PR2, PR3, PR6, PR7, PR8, PR9, PR10, PR11, PR12, PR13, PR14, PR15, PR16,
PR17, PR18, PR19, PR20

Item 1 (unique and rare items) can go with 2h, or before it; `G6` (loot
tables) builds on it.

## More to come

Items to come, as the owner raises them. Things the owner wants but that
are out of the current scope are kept in [`LATER.md`](LATER.md).
