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
(`PR21`, a side panel in the character windows, since taken: see 2b's
notes). The *later* items are
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
  `Prop_RoomView` ATTACK, and others in `DOCS/conditions/b075` to `b078`).
  Taken in 2b, at the owner's word: `audit/property-defects.md` says what
  became of each item of the four chapters' lists. 38 behaviours are fixed,
  each with a check in `tools/fixtures/fixes/p-properties.js` that fails on
  the code before it; about twenty smaller ones (messages, help texts,
  typos, guards) are fixed without one; the rest are not defects (the
  chapter misread the code: a room's sleeping flag, ReqCapacity's ROOMS) or
  are left with the reason (the item balancer's tone-up and tone-down
  formulas, which want one rule for every adjuster, and features of
  several players). Found on the way: a script could not use the time
  library (the core interface named the library's own `TimeDelta`, which
  then loaded twice), and JRUN said nothing of a script's errors as against
  its exceptions; both fixed.

#### 2b. Saves and world files (24)

What a save still loses (quest state, conquests, warrants, vessels,
generated places, behaviour state, script engine state), and the rest of the
world's content moved into `world/`. This finishes what phase 4 began: every
later feature keeps its state across a save.

S1, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, S12, S13, S15, S16, S17, S18,
S19, S20, S21, S22, S23, S24, S25

Status: done; what each item left undone is in its bullet below. Played
tests of what a save keeps are the five `saves` groups of `tools/fixes.sh`
(`tools/fixes.sh saves` runs them, `tools/fixes.sh saves3` one of them).

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
- S7 done, with S9's hook. Behaviours have a STATE stat (`getStat` and
  `setStat`, `StdBehavior.getRunState`/`setRunState`, empty by default);
  `world.xml` keeps each saved mob's, item's, room's and area's in a
  `<STATES>` tag: a BSTATE per behaviour (by ID and place) and an SSTATE per
  script (by its text). Scriptable's STATE, and a script's, is its engine's:
  which ONCE_PROGs and QUEST_TIME_PROGs have run (but `ONCE_PROG BOOT`, once
  a load), when each TIME_PROG and DAY_PROG last ran, DELAY_PROG ticks left,
  MPNOTRIGGER game time left, queued MPALARMs and delayed responses (their
  lines, and who they were about by name), its own scope and, for INDIVIDUAL
  scope, its variables; all tied to a hash of the script. While a restored
  game boots, scripts wait until the saved world is back, since bringing a
  mob to life ticks it at once and would run its ONCE_PROG again. `SAVES
  CHECK` lists what a save would not keep (unsavable mobs, things in space).
- S9 done. STATE for Hireling (whom it works for, time left, extra paid),
  Aggressive (bribes paid, and for how long), BribeGateGuard (balances not
  kept in its journal), Patroller (its step), Decay (whether it is running;
  its ticks left were already in its parameters), Emoter (ticks to expiry),
  Concierge (trips quoted and not paid, put back when the asker comes, and
  going home), Nanny (its charges and payments, found again in its room),
  MOBEater (what is in its stomach) and MudChat (groups a conversation
  switched to, for a while). QuestChat and Prop_QuestGiver keep theirs as
  quest variables (S1). Not done: there is no Taxi behaviour in the engine,
  and the LLM session's memory goes with N26, in `LATER.md`. New behaviours
  keep their state the same way.
- S5 and S12 done, as STATE of the law behaviours (Conquerable extends
  Arrest). Arrest keeps its area's warrants and old warrants (criminal,
  victim and witness by name, crime, punishment, jail, offences, last
  offence) and fines owed; they go back once the one each names is back in
  the game (the player logs in after the world is back), a warrant under
  way going back to SEEKING, so the guards hunt again. Areas under the same
  laws share the lists, so a warrant goes back once. Conquerable adds the
  ruling clan, the previous one, the conquest date, control points per clan,
  the revolt counters, and its clan items by their numbers in the save; a
  restored game takes the items as they are in the world rather than
  reloading them from the database, where the shutdown write would have
  made a second set.
- The saves tests are two groups that run side by side.
- S6 in part. `WORLDDIR/resources/` is laid out like `resources/` and read
  first, through CMFile: a file there is read instead of the stock one,
  `NAME.add` is read after the stock NAME, and a folder lists the world's
  files with the stock ones (so world help files load with the stock help).
  In builder mode, writes to `resources/` go to the world's copy, so the
  in-game editors write the world's files. `WORLDDIR/classes/<kind>/` join
  the class paths. A path starting `world/` is the world folder, wherever
  WORLDDIR is. `CHATDEFAULT=<file|NONE>` (in `world.ini` too). With the
  overlay, S20's DISABLE flags STDACHIEVEMENTS, STDTITLES and STDSOCIALS
  are done: the stock file reads as missing unless the world has a copy.
  The rest of S6 is done too. A world's copy of the translation or parser
  rules (`resources/parser_en_TX.properties` and the translation file) is
  read before the stock file, a section of the same name in both holding
  the world's rules first, and `#STOCK OFF` in a world section leaves out
  the stock rules of that section. A recipe's result may be `CATALOG:name`
  (all the crafting skills, through `CommonSkill.makeRecipeItem`), a copy of
  the catalog's item with its effects and scripts. In a normal game, the
  editors of the game's data files (HELP, SOCIAL, ACHIEVEMENT, TITLE, AWARD,
  FACTION, HOLIDAY, EXPERTISE, COMPONENT, PLANE) are refused, since every
  game reads what they write and a save does not keep it, and the building
  warning says so, and now also that rooms, exits and areas changed stay in
  the saves (S17). The export leaves a quest's folder in `world/quests/`
  alone. Found on the way: since the readable files of S25 differ from the
  old ones in their layout alone, the first export after them took every
  area for changed, and wrote each to the database cache again; under the
  full suite's load, a game after one such export once started with a room
  missing its exits and items. An export now compares the files as the
  engine reads them, so only an area that changed goes to the cache.
- S8 done, with CR23. A quest made in play (generated by RandomQuests,
  GENERATE QUEST or a quest giver, or a quest's copy), that is any quest
  not given by the world's files at boot, is saved whole: its script, with
  its state as other quests have it; a restore makes it again, starts it
  and puts its state back. RandomQuests keeps the names of its quests in
  its STATE and takes them back rather than generating others, and a
  `Prop_QuestGiver QUEST="GENERATE:..."` finds the quest it generated by the
  QUESTGIVER variable it sets on it. Craft orders (CraftOrders through
  RandomQuests) come with it; SOURCE_FACTION and SOURCE_FACTION_AMT are
  in RandomQuests' help. Not taken: the optional order by recipe name. An
  order's ZAPPER mask matches an item's name exactly, and a crafted item
  does not record the recipe it was made by, so an order by recipe would
  need a mark on each crafted item first; the name serves the same orders.
- The gap found in 2a is closed: when builder mode writes the world files
  (at SAVE and at quit), an area whose file changed is also written to the
  database cache from the live world, as the builder commands would, so a
  change made by JRUN without the database is in the next game too.
- S10 done. Builder mode's export also writes `world/templates.cmare` (every
  builder template, imported shared), `world/clans.cmare` (clans with no
  player members), `world/journals/NAME.xml` (each journal a journal item
  in the world reads, such as a notice board, and SYSTEM_CRON, which holds
  CREATE CRON's jobs) and `world/vfs/...` (one file per file in the
  database's file system, with an index of their flags and authors). A new
  game, and builder mode's rebuild, put them into the database; a restored
  game has them in its save, with what play added. `world.sum` covers them.
- S13 done. A save's header records the world it was made with: the
  world's checksum and each area file's. Restoring it into a changed world
  tells the player, on coming in, what changed: area files changed, new and
  gone, rooms gone with what was saved in them, mobs and items of classes
  gone, quests gone. `RESTORE name FRESH=CHANGED|area,area|ALL` (or
  `RESTORE=name FRESH=...` at launch) takes those areas' rooms as the world
  has them now, and keeps the player, what they carry, the party, quests
  and variables as saved. SAVES marks saves of another world "(older world)".
- S23 done. `SAVES INFO name` (who, where, when, game date, time and save
  mode, party, quests running, variables, world version), `SAVES DELETE`
  (asks first), `SAVES COPY a b`, `SAVES DIFF a b` (header, the character's
  room, hit points and what they carry, variables, quests, each room's mobs
  and items). `CHECKPOINT` saves in checkpoint1 to checkpoint3 in turn
  (named so, since a save's name starts with a letter or digit) and
  `ROLLBACK` restores the newest. `AUTOSAVE=n` (game minutes) or
  `ON-AREA-CHANGE`, with `AUTOSAVEKEEP=3`, in coffeemud.ini or world.ini.
  `tools/saveinspect.py` prints a save outside the game and can set a
  variable, the hit points or the room. The "dev flag" in SAVES INFO came
  with DT5's `DEV=1`.
- S22 done. A character's entry limits (InstanceArea's `LIMIT`, such as one
  run a day, kept in the character's class variables, which the character's
  tables do not hold) go in its PLAYER tag (`<LIMITS>`), each with its count
  and the game time left, and finished ones are dropped. The recent visits of
  each planar travel ability (which make a quick return harder for an hour)
  go with them, through the ability's internal `VISITS` stat.
- S15 done. A run confirmed most of the audit's reading. A vessel's area
  makes all aboard unsavable (its own text holds them), so the save skipped
  the crew, but wrote the cargo again in the rooms: a restore brought it back
  twice. A vessel a character owns, kept by the character's tables, was
  written as a room item as well, so it came back twice; the save now leaves
  out every item a character's or clan's tables keep. A vessel's rooms are
  marked (`<ROOM SHIP="1">`) and hold its crew (by their own savable flag)
  and cargo; on restore the vessel is made from its text, then each of its
  rooms is emptied and filled from the save, so the crew comes back as it
  was (hit points, effects, states). The rooms of a character's own vessel
  wait until the character is in. `<VESSELS>` keeps each navigable vessel's
  anchor, course, facing, time since it moved and turned, and the vessel it
  follows, by area name (`NavigableItem.getTravelStateXML`), put back once
  every vessel is.
- S16 done. A common skill under way (a crop on its room, a craft on its
  crafter) keeps what it works on through an internal `ACTIVITY` stat on
  `CommonSkill`, written in the effect's `<ACTIVITY>` and put back once the
  effect is. It holds every field of the skill's class and the classes it
  extends down to `CommonSkill` that is neither static nor final, so all the
  crafting and gathering skills have it: numbers, words and choices as they
  are, rooms by ID, an item not yet in the world (the crop, the piece being
  made) whole, and a mob or item in the world by its number in the save.
  Fields of other kinds are named in `<SKIPPED>` (for crafts,
  `last25items`, the recently made list). A crop now grows and is harvested
  after a restore, and a craft finishes. Later: number arrays, lists and
  arrays of strings, mobs and items are kept too (so a craft's recently made
  list is), and SAVE says which of a character's activities it kept only in
  part ("Kept in part: Carpentry (helpingAbility) under way."): an ability
  field, the one kind left. Nothing is refunded, as nothing is cancelled.
- S17 done. Once a normal game has booted, before a save is put back, the
  shape of each room and area the world's files gave is noted (a hash of
  the room's class, texts, behaviours with their parameters, lasting
  effects, scripts and exits, from the new
  `GenericBuilder.getRoomShapeXML`, which reads nothing from the database;
  and of the area's XML without its rooms). A save's `<ROOMSHAPES>` holds
  the shape of each room made in play (`NEWROOM`) or changed (`CHANGEDROOM`),
  the ID of each room destroyed (`GONEROOM`), and each area changed
  (`CHANGEDAREA`). A restore destroys, makes and reshapes them before their
  contents go back: a changed room's behaviours, lasting effects and
  scripts are replaced, not added to, then its exits are linked as saved,
  so MPSET texts, MPBEHAVE, MPLINK exits, a burnt room and a cellar dug in
  play all come back, and a start room made in play is there for the
  character. `FieryRoom` keeps its burn ticks in its STATE. Rooms of
  vessels, grid cells and areas made in play are left to S15, S18 and S19;
  a room a thin area loads after the boot has no shape to compare, and is
  skipped. Later: a room whose class changed in play is made that class
  again on restore, as MODIFY ROOM CLASS does it; `TemporaryAffects` keeps
  what it holds in its ACTIVITY (a `+ID ticks parms` line for each effect,
  behaviour, ambiance and attribute, and its bindings), as its text is spent
  on adding them; and `MovingRoom` keeps its place on its route (stop, next
  stop, status, direction) in its STATE, its rooms' exits being kept as
  changed rooms.
- S19 done, with S18. An area made in play (an instance child, a plane, a
  generated area) is written to the save's `<ROOMSHAPES>` as a `NEWAREA`
  (class, name, the area's XML without rooms, its timed effects), and its
  loaded rooms as `NEWROOM`s. A restore makes the area again before its
  rooms: an instance parent (`StdThinInstance`, `StdAutoGenInstance`) makes
  its child again by name and takes it back into its list
  (`Area.makeInstanceChildAgain`, new), and any other area is made from its
  class, a `SubThinInstance` with the room numbers of the area it copies.
  Rooms a thin child copied from its parent when asked for are emptied
  before their saved contents go in. Once the characters are in, the
  area's timed effects are back (an instance's timer runs on), each effect
  is told (internal stat `ADOPT`: `InstanceArea` rejoins its parent's list
  with its rooms counted as done, and it and the planar abilities number new
  instances and planes past it), and the parent keeps the child for the
  characters and followers in it (`Area.addInstanceChildMember`), so
  walking out and back in leads to the same instance. The character is put
  back in their saved room, as the character's tables keep a start room
  for an instance or plane. A generated dungeon so comes back as it was,
  without being generated again from a seed. Later: S18's `PERSIST=true` in
  a `StdAutoGenInstance`'s OTHERVARS keeps each group's dungeon however long
  it is left empty, until it is reset; `PASSIVEMINS` stays among the
  variables, so writing them out (the area file, a save) no longer drops it
  (NORESET never was dropped). An instance's own effect keeps its leader and
  best faction value in its ACTIVITY, timed or not.
- S21 done. Cron jobs (CREATE CRON, already in `world/journals/` since
  S10) run on the game clock: `CMJournals` compares a job's next run with
  `GameTime.now()`, and CREATE and MODIFY CRON set it from it, so a job
  waits while the game is paused and runs as turn-based time passes. The
  next run is kept in the save's database. On load, no job waits longer
  than its interval, since a restored clock can be behind the time a job
  was set at. LIST CRON shows the game time left to the next run. The
  played test runs a two-minute job by `WAIT 60` (four game minutes in
  about a second), which the old code, on the system clock, did not.
- S24 done. The things in space and in no room (an item that is a space
  object: a ship, a station, a star, an asteroid) that no character or clan
  owns go to `world/space.cmare` in builder mode, each as item XML, whose
  text holds where it is, its speed and its course. As no database table
  holds them, they are put in space at every boot (`WorldFiles.loadSpace`).
  A save's `<SPACE>` section has each of them as it is; a restore takes out
  what the boot put in space and puts the saved ones back, before the rooms,
  so that a ship's own rooms are there to fill. A vessel a character owns
  in space comes back with the character, as before. The checksum covers
  the file.
- S11 done. The custom races, classes, abilities and commands are one file
  each, `world/custom/races|classes|abilities|commands/<id>.cmare`, holding
  the element the old `custom.cmare` had. An ability's or command's SCRIPT
  of more than one line goes beside it, `<id>.script`, as it is (only the
  angle brackets the XML escapes come back, which escape again to the very
  same text), and the element names it (`SCRIPT` of `FILE:<id>.script`).
  The old `custom.cmare` is still read, and builder mode replaces it with the
  new files. The checksum covers them. The played test makes an ability with
  a script comparing with `<`, and a game on an empty database, built from
  the files, has the same script.
- S25 done. Builder mode's export writes each room's, mob's, item's and
  exit's own text (RTEXT, MTEXT, ITEXT, EXDAT) and the catalog's CATADATA as
  XML, an element to a line, marked `xml="1"`, when the text is plain XML
  (`GenericBuilder.setReadableText` and `textTag`, new; the text comes from
  the objects, since an escaped text cannot always be read back to the one
  it came from). The world files' reader escapes such an element back onto
  one line, less the layout's line breaks, before anything else reads it
  (`WorldFiles.collapseText`), so older files read as before. A builder
  session that changes nothing writes the files again the same, and a game
  on an empty database built from them has the mobs as they were.
  `tools/worlddiff.py OLD NEW` (or `--git REV`) compares two worlds by
  object: a room's kind, title, description, behaviours, effects and exits,
  and its mobs and items by name with their behaviours and effects
  ("New Area#1: title 'Rocky Room' -> 'Mossy Ledge'"). Later: a script (or
  any value of more than one line holding angle brackets) inside a thing's
  text is written as CDATA, as typed, unless it holds `]]>`; a PROP of the
  eleven base stats is written as named attributes (`<PROP ABILITY="0"
  ARMOR="100" ... />`); the reader turns both back. The file system's index
  (`vfs/.index`) keeps a file's line as it was when its data is the same,
  in the order of the paths, so a session that changes nothing writes it
  the same. The world files' own check leaves out `vfs/`, since the engine
  writes some of those files again in another order (such as the tech
  manufacturers). A script read back has its lines ended `\r\n`, with or
  without CDATA, as the engine's storage of a room gives them.

- PR21 done, at the owner's word (it was undecided). `WINDOWPANEL=STATUS,MAP,PARTY`
  (coffeemud.ini, world.ini or the command line) or `WINDOWS PANEL ON` (or
  the sections, in their order) gives each character's window a panel beside
  its text, drawn again at each prompt: STATUS is `world/text/panel.txt`
  with the prompt's codes and colours (or the character's points, room and
  area), MAP the rooms about the character as REGIONAL AWARENESS draws them,
  PARTY each of the party with a bar of their hit points. The prompt has
  `%{VAR name}` (the character's script variable, else one of that name on
  any host) and `%{VAR host name}`, ahead of PR14's other tokens. The played
  test runs on a virtual display (`xvfb-run`): it opens a window, shows and
  hides a panel, and has a window session draw its character's panel at the
  prompt. `tools/fixes.sh st1` and `pr5` now run those groups alone.

#### 2c. Developer and builder tools (50)

Inspecting, tracing, checking and testing the world, and faster building.
Everything after this is built and tested with them.

DT1, DT2, DT3, DT4, DT5, DT6, DT7, DT8, DT9, DT10, DT11, DT12, DT13, DT14,
DT15, DT16, DT17, DT18, DT19, DT20, DT21, DT22, DT23, DT24, DT25, DT26,
DT27, DT28, DT29, DT30, DT31, DT32, DT33, DT34, DT35, DT36, DT37, B1, B2,
B3, B4, B5, B6, B7, B8, B9, B10, B11, B12, B13

Status: built, each item with its checks in `tools/fixes.sh`. Left: DT33's
listing and console, which wait for I12's properties, and what an item's
note below says it leaves (B14, CHATDRAFT, is for later).

- DT1 done. `VARS LIST [scope] [host] [mask]` (scope * for all, GLOBAL,
  QUEST name, a scope's name, or INDIVIDUAL for the engines that keep their
  own, listed with their host), `VARS SCOPES`, `VARS SET host name value
  [SCOPE s]` (as MPSETVAR, so ++ and +5 add), `VARS CLEAR host [name|*]`,
  `VARS WATCH host name` (each change told with the scope, the script's files
  and the room it ran in; `VARS UNWATCH`), `VARS DUMP file`; and the script
  function `$%VARNAMES(host prefix)%`, also a condition. In AHELP VARS. Found
  on the way: `MPSETINTERNAL SCOPE GLOBAL` (an engine's scope set to the
  global one) put the engine's variables in a separate store named
  `VARSCOPE-`, not the one unscoped scripts share; it uses the shared one.
- DT2 done. `QUESTINFO name` shows a quest on one screen: state, step, time
  left or to its start, variables, the designated MOB, ITEM, ROOM and AREA
  and groups with where each is, the mobs and items it holds, what it added
  and to what, winners with when, and the last errors. `MODIFY QUEST name`
  takes `STEP n`, `WIN char`, `UNWIN char`, `RESETWINNERS`, `VAR name value`
  and `RELOAD` (the quest's script and the scripts it loads read again, from
  their files, and a running quest started again at its step, with its
  variables and its variables' scope). A step that fails (no duration, a
  script error) now says so in the quest's errors, not only in the log.
  Left: `STEP label` waits for Q6's named steps, and `WIN char OUTCOME w`
  for the quest outcomes of 2e.
- DT3 done. While the world files are read, what the engine logs as going
  wrong in each (an object of an unknown class skipped, say: "Unknown affect
  Prop_Smelll on New Room, skipping") is kept by file (`Log.startCapture`),
  in `worldproblems.txt` beside the database so that a game on the cache
  still knows. It is printed at startup, told to the builder at login, and
  listed by `WORLD PROBLEMS`. Builder mode does not write such a file (it
  would be written short) until it is fixed and the world read again from
  it, or `SAVE FORCE` writes it as the world has it.
- DT4 done. `CHATTEST npc sentence` shows what an NPC with MudChat would make
  of a sentence without anyone speaking: its base and active groups, what
  would stop it answering, each entry the words lead to with its pattern and
  whether it matches, and the first match's replies with their weights and
  variables filled in. `CHATLINT file` reports, with line numbers, patterns
  that do not parse, malformed `${}` lines, `@` links to groups not above
  them, replies with no pattern, patterns with no replies, unreadable
  includes and skipped lines. `CHAT RELOAD [file]` reads a chat file again
  for every NPC talking from it (a generation counter makes each drop what
  it worked out), and builder mode reads an edited chat file again by
  itself. Left: entries hidden by broader earlier patterns, tick odds, and
  `.dlg` dialogue trees, which this world does not have yet (N items).
- DT5 done. `DEV=1` (coffeemud.ini or the command line) starts a developer's
  game: a normal game, with saves, in which each character has the security
  group `DEVGROUP` names (`DEV` by default, defined in coffeemud.ini: GOTO,
  AT, TRANSFER, STAT, WHERE, LISTADMIN, CMDQUESTS, CMDMOBS, JSCRIPTS, AHELP
  and the resources folder, so VARS, QUESTINFO, CHATTEST and JRUN, but not
  IMMORT or ORDER) without a change of class; a game without `DEV=1` takes
  it away again. Saves made in it have `dev=true` in their header and show
  `[dev]` in SAVES and the startup list, and SAVES INFO says so (S23's dev
  flag). Scripts ask `ISDEV()`. Not in builder mode, which has every power.
- DT6 done. `GOD [cheat|power|ALL ...] [ON|OFF]`, in a `DEV=1` game or
  builder mode, keeps a character's cheats in `Prop_DevCheats` (saved like
  any effect): `NOCLIP` (closed and locked doors and the `Prop_Req*` entry
  checks do not stop a walk, which costs no movement), `NOCOST` (no mana,
  movement or hit point cost and no wait), `NODAMAGE`, `NODEATH` (hit points
  stop at 1), `SEEALL` (dark, hidden, invisible, sneaking, never blind) and
  `NOAGGRO`. Where a sysop power already gave part of one (GOTO walked
  through doors, which DT5's dev group has; ALLSKILLS skipped the wait;
  IMMORT; ORDER or CMDROOMS kept `Aggressive` and `VeryAggressive` off),
  turning the cheat OFF takes that away too, and a power's own name with OFF
  suspends it for the character (`CMSecurity.setSuspended`, even for an
  Archon): `GOD ALL OFF` plays as a mortal. `GOD` alone lists them, `GOD
  RESET` forgets them. Found on the way: a JRUN script that names an engine
  class through `Packages` gets a second copy of it, with its own statics,
  since CMClass is a class loader of its own; tests reach the game's copy
  through an instance from CMClass.
- DT7 done. `SCRIPT` with a target as for STAT (a mob or item here or
  carried, ROOM, AREA, name@room), reaching the engines a host has and those
  in Scriptable behaviours (`ScriptingEngine.baseEngine()`): `SCRIPT LIST`
  numbers its triggers with the file and line each is written at (inline or
  a LOADed file), `[off]` and `[done]`; `SCRIPT FIRE trigger [words] [AS who]
  [WITH item]` runs the triggers of a kind now as if who had caused them,
  whatever their own conditions and odds, those answering to the words when
  given (a word of a SPEECH_PROG, the hour of a TIME_PROG, a P pattern), the
  words being `$g` and the item `$o`; `SCRIPT CALL function [args]` runs a
  FUNCTION_PROG and says what it returned; `SCRIPT RESET [ONCE|DELAY|TIME|
  ALL]` lets ONCE_PROGs, DELAY_PROG waits and this hour's TIME_PROGs and
  DAY_PROGs run again, for this host alone; `SCRIPT DISABLE|ENABLE [trigger]`
  turns triggers off until turned on (as MPNOTRIGGER, saved with the engine's
  state); `SCRIPT ALARMS [LIST|CLEAR]` shows or forgets what is queued
  (MPALARMs, triggers waiting their tick).
- DT8 done, the core of it. `PLAYTEST ON|OFF` in builder mode: while on
  (`Prop_Playtest`, not saved), every check in CMSecurity says no to the
  builder (`CMSecurity.setPlaytest`: sysop, staff, each flag), so the one
  shared exemption test is the security check itself and Aggressive,
  VeryAggressive, Follower, Prop_Doppleganger, the variable equipment and
  Prop_Uncampable all treat them as a mortal, and DT6's cheats fall back to
  mortal defaults. The prompt starts `(playtest)`. A death brings them back,
  whole, to the room the playtest began in. `BUILD command` runs one command
  with the powers back (`CMSecurity.runAsBuilder`); it is a command only in a
  playtest, since BUILD is also the Building skill's word (BUILDING in a
  playtest). OFF gives the powers back, makes them whole and makes peace.
  Left: setting party and difficulty as a new game at ON; restore points
  beyond the room (inventory, experience); `PLAYTEST GAME` (export and
  restart in process as a normal game, and plain `BUILD` back); `PARTY
  MORTAL|ARCHON name` for makeArchon; an editor that asks for input after
  its command returns runs without the powers.
- DT9 done but for the window. `core/Log` takes listeners
  (`Log.addListener`), which hear every line, whatever the log's settings
  send to mud.log, exceptions with their class. `DEVLOG ON [ERROR|WARN|INFO|
  DEBUG] [MODULE mask]` in builder mode or a `DEV=1` game tells the builder
  each line as one coloured line, `[ERROR Scripting] ...`, a repeat told once
  and its count when something else comes (`core/DevLog`); `DEVLOG LAST [n]
  [level] [MODULE mask]` replays the last 500 kept, repeats counted, and
  `DEVLOG CLEAR` forgets them. Builder mode keeps the setting beside the
  database (`devlog.txt`); the ini's `DEVLOG=WARN Scripting,Quest*` gives
  it otherwise. Left: `DEVLOG WINDOW` (a separate CharacterWindow), and
  `LIST LOG ERRORS SINCE BOOT` by source (`DEVLOG LAST n ERROR MODULE x`
  does the same from the lines kept).
- DT10 done. A script error reads `file:line COMMAND Type: what (host @
  room)`, the file and line found from the parsed triggers following LOAD=
  (`scriptLocation`; `inline:n` for an object's own script), and a
  JavaScript error gives the file's line (the block is handed to Rhino from
  the line after `<SCRIPT>`, with one line ending each: the game's `\n\r`
  counted twice). Both go to one channel, `core/ScriptErrors`: kept (200)
  for the `SCRIPTERRORS [n] [text]` command, told to the builder in builder
  mode and a DEV=1 game, and in a normal game as `SCRIPTERRORS=LOG|NOTICE|
  SHOW` says (LOG by default), no longer to the room. An MP word that is no
  command, or the start of several (`MPSE`), is an error and no longer runs
  as a game command. Found on the way: the shipped quest template
  `auto_escort1.quest` had `mpechoto` for `mpechoat`.
- DT11 done but for its headless form, argument counts and unset
  variables. `SCRIPTCHECK file|target|AREA|ALL` (`core/ScriptCheck`) reads
  scripts without running them, with LOAD= followed and split as the
  engine's parser splits them (`;`, `~`, `#`), and lists by file:line
  unknown triggers, commands (no prefix matching: `MPSE` is told to be
  written in full; a game command, social or skill word is a command) and
  functions in conditions and `$%FUNC()%`; IF/ELSE/ENDIF, SWITCH/CASE/
  DEFAULT/ENDSWITCH and FOR/NEXT out of balance; IF and MPWHILE
  parentheses; `<SCRIPT>` without `</SCRIPT>`, JavaScript that does not
  compile (Rhino, at the file's line) and, where JSCRIPTS asks for it,
  blocks not approved; missing LOAD= files; MPCALLFUNC to no FUNCTION_PROG;
  written-out room IDs in MPGOTO, MPTRANSFER and MPOLOADROOM that do not
  exist. AREA and ALL cover areas, rooms, exits, mobs, their items and
  room items, a shared file told of once. Builder SAVE says how many
  problems SCRIPTCHECK ALL finds. The shipped world has none. Left: the
  headless form (DT13's WORLD CHECK), argument counts, variables read and
  never set, and quest scripts (DT32).
- DT12 done, with keys from the help. `PROPCHECK [ROOM|AREA [name]|ALL|
  CATALOG|target]` (`core/PropCheck`) reads again the text of each effect
  set on things (savable, not uninvoked: what the builder gave, not what is
  cast) and lists, as `Room#12 / iron chest / Prop_X: ...`, what the
  class itself logs as it reads the text (DT3's `Log.startCapture` around a
  fresh instance's `setMiscText`), keys its AHELP entry does not name (the
  help's `KEY=` words stand for the declared keys DT19 would add), room IDs
  that do not resolve, unknown abilities in a `Prop_SpellAdder` list, and
  effects on what `canAffect` refuses. The shipped world has none. Found on
  the way: `Prop_Transporter` worked on mobs, items and areas, as its help
  says, but told the editors it affects only exits and rooms. Left: zapper
  masks that do not compile (the masking library does not say), and
  Prop_HaveAdjuster's complaints when it is not on anything (it logs them
  later).
- DT13 done, the core of it. `WORLD CHECK (AREA [name]) (LOG) (ONLY=...)`
  (`core/WorldCheck`), in builder mode or a DEV=1 game, lists errors then
  warnings, each with its part and room: EXITS (exits to nowhere; doors with
  no way back), REACH (start rooms, START and START_*, that do not exist;
  rooms nothing leads to from them, by area), KEYS (locked doors and
  containers whose key is in no room, mob, shop or catalog entry), TEXT (the
  new room's description, a room, generic mob or item with its class's
  text), SHOPS (shopkeepers with nothing to sell), DATA (DT3's WORLD
  PROBLEMS), SCRIPTS (DT11) and PROPS (DT12). `sud.sh CHECK=1` loads the
  world as a new game, on a scratch copy, checks it with no player, prints
  the report and exits 1 on errors. The shipped world has no errors and two
  warnings, both its first room's (its description). Left: unknown IDs in
  masks and custom objects beyond what the loader reports, achievements,
  recipes, help for custom objects, quests that fail to start, unreferenced
  catalog items, areas without clock or climate, Mobile mobs free to leave,
  and DEVIATIONS.
- DT14 done but for variables, REQUIRES and unused ids. `GENERATE CHECK
  file` (`core/GenerateCheck`) loads a generator file as GENERATE does,
  with LOAD and FROM includes (the percolator's own `buildDefinedIDSet`,
  its complaints captured), generates nothing, and lists by line and tag id
  INSERT ids not defined, LIKE ids not defined or of another tag, CLASS
  values (attribute or child tag) that are no class of their kind (catalog:
  and metacraft excepted), unknown layouts and SELECT modes (a number or an
  expression is one). Found in the shipped files, and left as they are for
  their authors: `rats.xml` inserts `prison_junk`, which only `prison.xml`
  defines, so its spirits' junk fails one time in ten; `planet.xml` defines
  `randomtheme`, `hallway_rooms`, `interior_rooms` and `leaf_rooms` without
  MERGE after loading files that define them with it, so its own are
  ignored (logged on every generate), and writes `pick="any-1"` (no such
  attribute; `select` is meant) on `randomatmo` and `randomtheme`. Left:
  `$variables` never defined, REQUIRES that cannot be met, unused ids,
  unknown attributes (`pick=` would show), and the smoke test calling it.
- DT15 done but for the in-game runner and DT38's WORLD TEST. Playtest
  files `world/tests/*.play`: one command a line, `#expect TEXT`, `#reject
  TEXT`, `#assert HERE|HAS|VAR|QUEST ...`, `#wait n`, `#save slot`, `#seed
  n`, `#realtime`, `#builder`, `#char Name,Race,Class[,Gender[,Alignment]]`
  and `#restore slot`. `tools/playtest.sh [files]` (`playtest.py`, over
  sudplay.py) plays each in a fresh game on a scratch copy of the world, in
  turns with seed 42 and as a DEV=1 game, marks each step (`ASSERT MARK n`
  prints `PLAYSTEP n`) so that each check reads only its step's output,
  prints PASS or FAIL with each failing line, and exits 1 on a failure. The
  dev-only `ASSERT` answers `ASSERT ok: ...` or `ASSERT FAIL: ...` and why;
  quests tell their step (`Quest.stepNumber()`). The world ships
  `tests/start.play`. Left: `PLAYTEST RUN file` in the game, and DT38's
  `.test` files and JavaScript test files.
- DT16 done. Kits `world/kits/NAME.kit`, one setting a line: race, class,
  gender, alignment, level, gold, room, item (a class or `catalog:NAME`,
  worn if it can be), ability ID PROFICIENCY, faction ID VALUE, var NAME
  VALUE (a global script variable of the character); an unknown setting is
  told with its line. `TESTCHAR name (KIT=k) (PARTY)`, in builder mode or a
  DEV=1 game, makes a character of the profile with no questions (an
  average human apprentice of level 1 unless the kit says otherwise),
  levelled by the experience each level needs as CHARGEN levels, saved,
  resting in the profile or, with PARTY, brought in as PARTY ADD does. `KIT
  k (target)` gives a kit to a character, follower or mob here. Left: one
  format with P7's starting kits and P8's ready-made characters, when they
  come.
- DT17 done but for dead FILE= links and Prop_Details keywords. `PREVIEW
  (ROOM) [DAY|NIGHT|DAWN|DUSK] [season] [weather] [FROM dir|FROM UNKNOWN]
  [VISITED|FIRSTVISIT] [AS character] [VAR name=value]` shows this room's
  title and description under those conditions and changes nothing: the
  VARIES parser asks `core/TextPreview` (one thread's conditions) before the
  world's clock, climate and the reader's visits and last room; a VAR is set
  for the reading and put back. Only rooms' texts vary in this engine, so
  PREVIEW ITEM and MOB say so. `CHECKTEXT [ROOM|AREA|WORLD]`
  (`core/TextCheck`) lists variation tags in a text without `<VARIES>`
  first, tags that are none, tags in small letters (the parser finds their
  end only in capitals), tags not closed or closed and not opened, a MASK
  whose mask holds `>`, and a colour code left open. `<FROMUNKNOWN>` is now
  the same as the code's `<FROMUNKOWN>`.
- DT18 done but for masks, absent PCs, passive areas and registration as
  reasons. `SCRIPTTRACE target|ROOM|AREA|OFF [trigger]` (`core/ScriptTrace`)
  traces to the builder's own session, not saved: each trigger's chance as
  rolled (`GREET_PROG 50: rolled 73 of 50, skipped`, one helper now rolling
  for all twelve chance triggers, the dice drawn as before), a trigger off
  (MPNOTRIGGER, SCRIPT DISABLE), a host too busy to be triggered, each line
  run and each variable changed. `MPSETINTERNAL TRACE TRUE` starts it from a
  script (builder mode or DEV=1), and `ISBUILDER()` (condition and value) is
  added, so ST6 need not add it again.
- DT19 done, the general part. Each behaviour answers `getStat("DEBUG")`:
  by default its parameters and its saved state (S9's STATE), and a timed
  one (`ActiveTicker`, 34 of them) adds in how many ticks it acts, how
  often, its chance and when it last acted. `STAT BEHAVIORS mob|item|HERE`
  lists them, with keys of the parameters that the behaviour's help (AHELP
  BEHAVIOR_ID, the same `PropCheck.keysOf` as DT12) does not name.
  `TRACEB target [ticks]` tells the builder each decision of the host's
  timed behaviours for a while: acts (with the roll), could not act, or
  lost the roll. Left: each behaviour's own state in words (route step,
  schedule entry, target, morale), which each class adds to its
  `debugState()` as needed.
- DT20 done. For builders (builder mode, DEV=1, or CMDAREAS): `WEATHER SET
  weather [AREA name] [NEXT]` sets the next weather and forces the change,
  so its messages and WEATHER_PROGs come, or with NEXT only sets what comes
  next; `TIME SET hour|DAWN|DAY|DUSK|NIGHT` runs the area's clock forward
  hour by hour as TICKTOCK does, its events running; `TIME SEASON season`
  runs it a month at a time to the season; `LOOK AS conditions` is DT17's
  PREVIEW; `HAZARDS [area]` lists the area's own behaviours and effects
  (spawners, weather) and its rooms', exits' and items' behaviours, traps
  and Prop_InstantDeath, Prop_NarrowLedge and Prop_Weather, with their state
  (DT19's DEBUG) or parameters. W12 and W20's weather commands can build on
  WEATHER SET.
- DT21 done but for LAW TEST and LAW SET. `LAW` (builder mode, DEV=1 or
  ABOVELAW) shows the area's legal behaviour and law file, the officers and
  judges it found among the area's mobs, and each mob named as one but not
  one, and why (the law takes as officers only mobs that wander, with
  Mobile, and as judges only mobs that stay put: a stationary "cityguard"
  is silently no officer), the jail and release rooms each name found or
  FOUND NOWHERE, the taxes, and the counts of crimes and warrants. `LAW
  CRIMES [text]`, `LAW WARRANTS [name]`, `LAW PARDON name [crime|ALL]` and
  `LAW ACCUSE name crime` handle warrants. Left: `LAW TEST crime` (whether
  an act here is a crime, and why) and `LAW SET KEY=VALUE` (W21's custom
  law as world content).
- DT22 done. `PRICECHECK item [AT shopkeeper] [FOR character] [SELL]`
  (builder mode, DEV=1 or CMDMOBS) lists the shop's price settings, each
  with where it came from (the shopkeeper, its area, a parent area or the
  ini, and a running quest's GIVE STAT, through the new `Quest.gaveStat`),
  then each step of the price as `CoffeeShops` works it out: its pricing
  code notes each step while PRICECHECK asks (`explainPrice`), so the steps
  are the game's own sums, then both prices, the budget left and the time
  to the next stock reset (new `ShopKeeper` getters). Fixed on the way: an
  area with no prejudice of its own fell back to the ini's IGNOREMASK, not
  PREJUDICE.
- DT23 done. `GameTime` has a rate: `TIMESCALE x` (builder mode or
  DEV=1, 0.1 to 20) or `TIMESCALE=` in the ini sets how many game
  milliseconds pass in each real one, from now, so everything that reads
  the clock follows (ticks, cooldowns, decay, effects, the calendar). The
  scheduler sleeps `GameTime.realMs` of a wait on the game clock and is
  woken when the rate changes. Pause and turn-based time work as before,
  and the prompt shows `(x8)` while the rate is not 1. Not saved: a
  session setting, or the ini's.
- DT24 done. `MPDEBUG text` (index 102) writes `[debug host@room
  file:line] text`, its $-codes filled in, to the debug log through DT9's
  developer log, which tells the watcher it whatever level and modules it
  watches and keeps it for `DEVLOG LAST`. `debug(x)` does the same in
  `<SCRIPT>`, `JRUN` and `JCONSOLE`, with the script's line, and objects
  and arrays as JSON. In a normal game both do nothing. JS6's debug() is
  this one.
- DT25 done. `RECORD INPUT [file|OFF]` (builder mode or DEV=1; the
  Archon's RECORD skill keeps its other words) and `RECORDINPUT=1`
  write each line typed, in every window, to a `.play` file in the saves
  folder's recordings (`core/InputRecord`): a header with the seed
  (`#seed`, or a note that the dice were not seeded), `#realtime`,
  `#builder`, `#restore slot` or `#new` and `#char`, the world's
  checksum and the game time, then a comment of the character and game
  time before each line. `RECORD MARK text` adds a comment. A line typed
  with `#` first is written as a comment, since the runner could not send
  it. Exact replay of a recording made mid-game needs DT39.
- DT26 done. `NOTE [HERE] text`, in any game, appends to
  `notes/playtest.txt` (`NOTESFILE=`), outside world/ and the saves: the
  date, the save (or builder mode), the character, the room's ID and
  name, the area's game time and the text, with HERE the room's exits,
  mobs, items, effects and behaviours. `NOTES [ALL]` lists them
  numbered, `NOTES DONE|UNDONE n` marks one, and `NOTES GOTO n` (builder
  or DEV=1) goes to its room. PR7 wanted NOTES as JOURNAL's alias: NOTES
  is DT26's now, so PR7's journal keeps the name JOURNAL.
- DT27 done. Area files in `world/dev/` are loaded only in builder mode
  and DEV=1 games (`WorldFiles.loadDevAreas`, after the map), tagged DEV,
  with a checksum of their own (`devChecksum`), so they do not make a
  normal game rebuild. Builder mode plays on the cache and so keeps them
  there: a normal game takes them out of its working copy, and builder
  mode keeps the cache's while `dev.sum` matches the files (edits not yet
  saved are kept), else loads them again. SAVE writes DEV-tagged areas to
  dev/ (only if they loaded whole), `WORLD DEV [area] ON|OFF` tags one,
  `DEVSTART=` is the start room in builder mode and DEV=1, and WORLD
  CHECK warns of exits from shipped areas into dev areas.
- DT28 done. `VESSEL STATUS name` (builder mode, DEV=1 or CMDAREAS) shows
  where a vessel is and is docked, facing, anchor, course, top speed and
  pace, hull, owner, home port, the behaviours on it, its decks and those
  aboard with DT19's DEBUG state (a route's step is a behaviour's state),
  and its crew and passengers. `LIST SHIPS` gains Anchor, Course and Owner
  columns. `LIST INSTANCES` lists each instance child with its parent,
  class, members (the new `Area.instanceMembers`), ticks to its time-out,
  rooms and a GOTO room. T11 adds the other VESSEL words.
- DT29 done. `VALIDATE RECIPES [skill|ALL]` (builder mode, DEV=1 or
  CMDRECIPES) checks each row of a skill's recipe file and its `.txt.N`
  extensions (`AbilityParameters.validateRecipes`: each cell with its
  column editor's `confirmValue`, as MODIFY RECIPE takes it, and the
  column counts), then hunting.txt, fishing.txt and components.txt, and
  reports every problem with file and row. It found, in the shipped
  files: hunting.txt's four FUR rows had a tab doubled (so no wolf or
  bear fur could ever be hunted) and fishing.txt's "Catfish 25" a space
  for a tab, both fixed; left for review, clancraft.txt rows 4 and 45 have
  no CLAN_ITEM_CODENUMBER.
- DT30 done. `VOCAB TEST text` and `VOCAB INPUT line` (builder mode,
  DEV=1 or CMDAREAS) run the language library's own sums with each
  matching rule noted (`explainTranslation`, `explainInput`; AUTOIGNORE
  writes nothing while VOCAB asks), `VOCAB LIST key` the lists.ini value
  in effect and its file, `VOCAB RELOAD` the unloads in one, and `VOCAB
  ADD section rule` (builder mode) appends to the world's rule file.
- DT31 done, for the sources the languages have now (the dictionary and
  the word lists; L5's suffixes and syllables will add theirs). `LANGUAGE
  PREVIEW id [prof] text` (builder mode, DEV=1 or CMDABILITIES) shows what
  StdLanguage's own `scrambleAll` and `messChars` do to it for one who does
  not know the language, a listener, the speaker and a reader, and each
  word's translation and source. `LANGUAGE COVERAGE id file|room|area|HERE`
  counts dictionary hits and names the commonest misses. Other LANGUAGE
  words go to LANGUAGES as before.
- DT32 done. `QUESTCHECK quest|file|ALL` (builder mode, DEV=1 or
  CMDQUESTS; `core/QuestCheck`) reads a quest's script without making
  anything: unknown commands, SET variables (the quest's own stat codes
  and the object kinds) and GIVE, TAKE, LOAD and IMPORT kinds; no SET NAME
  or DURATION; WAIT with no INTERVAL; LOAD= and IMPORT files neither in its
  FILE sections nor the resources; and SCRIPTCHECK on each script it gives
  (inline or its own file). A quest that is LOAD=file is read through to
  the file. Builder mode prints the count for world/quests at the start.
  The quest engine has no step labels, so nothing checks them.
- DT33's CHECK done (`core/PuzzleCheck`, `PUZZLE CHECK [AREA name]`):
  room IDs of MPGOTO, MPAT and MPTRANSFER, the exits, doors and locks of
  MPOPEN, MPCLOSE, MPLOCK and MPUNLOCK in the host's room, and MPOLOAD and
  MPMLOAD names, over every script in the world with its LOAD= files; it
  runs once at the first login and logs to mud.log. Left: the listing and
  console, which need I12's switch, plate and controller properties.
- DT34 done. `SCRIPTPROF ON|OFF|RESET|REPORT [n] [AREA]` (builder mode,
  DEV=1 or CMDROOMS; `core/ScriptProf`) times each trigger's whole run in
  `DefaultScriptingEngine.execute` (nested runs count in their trigger's),
  by file:line and trigger, with runs, total, longest and its host.
  `SCRIPTSLOW=ms` warns in mud.log of each slower run, and an MPWHILE cut
  off at its 4-second cap is now a script error with its file and line.
- DT35 done. `COMBATDEBUG ON|OFF [target]` and `COMBATDEBUG LOG file|OFF`
  (builder mode, DEV=1 or CMDMOBS; `core/CombatDebug`): MUDFight notes the
  hit roll (the same dice, with `hitScore` split out), each damage formula
  and what was dealt, and StdMOB notes each effect or item that changed or
  stopped a watched character's damage, all on one line per attack.
- DT36 done, for the sources there are. `PROGRESSION class [race] [to
  level]` (builder mode, DEV=1 or CMDCLASSES) builds a sample of each level
  with `fillOutMOB`: XP, HP, mana, moves, practices, trains and the skills
  gained (given ones marked). `PROGRESSION XP [area]` adds up achievement
  XP, literal MPEXP amounts in each area's scripts (LOAD= files read) and
  Prop_EnterAdjuster experience, with the level reached in order. There is
  no Prop_Discovery yet; expertises are left out of the curve.
- DT37 done, but for positions with no powers and INNERMASKs no one can
  pass. `ORGCHECK [faction file|CLANS|GOVERNMENTS|ALL]` (builder mode,
  DEV=1 or CMDFACTIONS; `core/OrgCheck`) reads faction files line by
  line: keys given twice, band overlaps and gaps, CHANGE triggers that
  match nothing (as DefaultFaction's setEventID takes them), REACTION and
  AFFBEHAV IDs, REACTION ranges and RELATION factions; the world files'
  mobs with an unknown CLAN; and SCRIPTCHECK on governments' ENTRYSCRIPTs.
  In the shipped inclination.ini it found four triggers that never fired:
  CHANT_EARTHFEED (the chant's ID is Chant_Bury) and MEDIDATE (MEDITATE),
  both fixed, and CLEAN * and BUTT *, which name no social (BUTT has only
  a targeted form), taken out.
- B1 done. `SAVE ROOM` in builder mode is `SAVE ROOM HOME`: the room's
  items as before, and as its mobs only those whose start room it is,
  wherever in the area they stand (a wanderer is saved, not destroyed by
  clearDebri); a mob standing there whose home is elsewhere is left out
  and named. `SAVE ROOM ALL` saves as before, and `SAVE AREA ROOM` does
  either for each room.
- B2 done. Builder mode's export no longer writes over or deletes a world
  file changed outside the game: `WorldFiles` keeps each file's checksum
  as the game loaded or last wrote it (at boot, and after each export for
  the files it settled), and a file edited, added or deleted since is left
  as it is, with what the export would write beside it as NAME.builder,
  unless SAVE FORCE. While a file is kept, world.sum and dev.sum are
  deleted, so the next game builds from the files. `WORLD STATUS` lists the
  files changed outside the game and what SAVE would write, delete and
  leave (the export run dry, `WorldFiles.plan`); `WORLD DIFF file` shows a
  file against what SAVE would write, as a line diff.
- B3 done. `core/TextEditor` edits a text in the line editor (the
  journals' message maker), a window (`EDITOR=WINDOW`) or the program
  `EDITORCOMMAND=` names (`EDITOR=EXTERNAL`, or $VISUAL or $EDITOR); a
  script on one line is a statement to a line (split at its unescaped
  ';', not inside `<SCRIPT>`) and is joined so again, and on save it is
  checked by SCRIPTCHECK (a quest's by QUESTCHECK): edit again, keep, or
  cancel. `EDIT SCRIPT target [N]` (a LOAD=-only script opens its file,
  and is read again), `EDIT DESC target` and `EXTRACT SCRIPT target [N]
  [file]` (to resources/progs/NAME.script, LOAD= in its place) are
  `EditCmd`. GenAbility and GenTrap's Scriptable Parm, GenCommand's
  field (now "Script", its FUNCTION_PROG EXECUTE check reading LOAD=
  in: `ScriptCheck.withLoads`), a Scriptable behaviour added, MODIFY
  QUEST's script and a description of more than one line (or + at its
  prompt) use it, and promptStatMsg now says why a check failed.
- B4 done. `core/Unsaved` marks, in builder mode, each room whose mobs
  or items a building command (CREATE, MODIFY, DESTROY, COPY, TRANSFER,
  RESTRING, EVERY, GENERATE: StdMOB.doCommand) puts in or takes out
  (StdRoom's add and remove), and the builder's room for MODIFY or
  RESTRING of a mob or item. `LIST UNSAVED` lists them with the commands
  and the change in their counts from `DBCountRoomMobsItems`; `SAVE
  CHANGED` saves exactly those (SAVE ROOM HOME), then the world files;
  SAVE ROOM and SAVE AREA take theirs off. Plain SAVE names them, QUIT
  asks "save them first (Y/n), or cancel (c)?", GMODIFY CHANGE= asks
  before its reset drops them, `BUILDERAUTOSAVE=1` saves after each
  command, and `%U` in a prompt is their number.
- B5 done. `WORLD RELOAD` loads world files again into the running game
  (WorldCmd, with `WorldFiles.loadAreaFile`, `reloadCatalog`,
  `questScriptIn` and `markLoaded`): `[AREA] name` (by its name or its
  file's; players and their followers wait outside and come back, exits
  from other areas are linked again by room ID, refused while B4 has
  unsaved rooms in it unless FORCE), `SCRIPT file|ALL [RESET]` (the
  parsed scripts forgotten and those naming the file set again, DT7's
  resetTriggers with RESET), `QUEST name` and `QUESTS` (stopped, read
  from world/quests, started at their step, as DT2's RELOAD, and saved),
  `CATALOG` (entries put in place of those of their names, or added) and
  `CHANGED` (each file WORLD STATUS lists). In a normal game it saves to
  the world-reload slot and restores it. `DEVWATCH ON [seconds]` polls
  the world files (B2's sums) and resources' scripts (their times) on a
  thread of its own and queues the matching WORLD RELOAD for the builder,
  each noted in DEVLOG.
- B6 done. `core/UndoLog` logs, in builder mode, the last 50 building
  commands (B4's and GMODIFY, from StdMOB.doCommand), each with the XML
  (getRoomXML with content) of the rooms it changed as they were: the
  builder's room at its start, and each room before its mobs or items
  first change (StdRoom's hooks); an entry whose rooms are unchanged is
  dropped. They are kept in world/.history/ too. `UNDO [n]` makes each
  room again from its XML (obliterated, unpacked and saved, as IMPORT
  does, players waiting outside and exits into it linked again), newest
  first; `UNDO LIST` lists them, as HISTORY was the typed commands'.
  The export copies each file it writes over or deletes to
  world/.backup/PATH.TIME, the last `BACKUPS=` (5) of each kept; `WORLD
  BACKUPS [file]` lists them and `WORLD RESTOREFILE file [time]` puts one
  back, changed outside the game (B2), for WORLD RELOAD (B5).
- B7 done. MODIFY's Effects field (`genAffects`) numbers the effects;
  `=ID` or a number edits one's parameters in place (`effectParms`, the
  current ones kept on an empty line), `-ID` removes, a plain ID adds,
  and one the host has already is offered `Prop_MultiEffects` with both
  copies (`doubleEffect`; one that has a Prop_MultiEffects is told to
  add to it). `?P` lists the properties, and Abilities/Misc effects,
  whose canAffect takes the host, each with a line of its help.
- B8 done. GMODIFY's fields take exits' and area types' stat codes, and
  it changes each room's exits as it does its items (tryModfy), saving
  them with DBUpdateExits. MODIFY UPDATE: (MQL) saves a changed exit with
  each room it leads from and an area with DBUpdateArea; a mob or item it
  cannot save directly marks its room unsaved for SAVE CHANGED (B4).
- B9 done, as `WORLD DIG` and `WORLD PATH` (`core/DigMode`): BUILD is
  the Building skill outside a playtest (DT8), so the audit's BUILD DIG
  and PATH are WORLD's. `WORLD DIG ON [TYPE=] [LINK=ONE-WAY]` makes a
  room through Create's CREATE ROOM where the builder walks and there is
  no exit (MUDTracker.move); `WORLD PATH dirs [TYPE=] [NAME=] [DESC=]`
  lays a chain, following exits already there, the builder staying put.
  Rooms made are saved and tagged TODO; WORLD CHECK's TEXT part lists
  them, and EDIT DESC takes the tag off.
- B10 done, as `WORLD MAP` (MAP is Skill_Map's word): `core/AreaMap`
  lays the rooms out on a grid by walking their compass exits from the
  builder's room within the area (rooms without an ID, such as skies,
  left out), `RADIUS n` (5) or `AREA`; `IDS` numbers and lists them,
  `FLAGS` marks one-way exits, locks, exits to other areas, rooms with
  scripts, and the rooms nothing reaches; `HTML=file` writes a page to
  world/maps/. The Swing window is left for later.
- B11 done. `RUNFILE name [args]` (RunFileCmd) runs world/macros/NAME.cmd,
  a command a line through doCommand ($1..$9, $room, $area; # comments;
  STOPONERROR), and sums up what ran and what failed (no command to take
  it, or an exception). JRUN's scope has `builder` (`core/JSBuilder`):
  here, room, dig, link, create, saveRoom, forEachRoom and cmd.
- B12 done, with PREVIEW as `SANDBOX PREVIEW` (PREVIEW is DT17's):
  `core/Sandbox` makes the Sandbox area, four bare rooms in a row, area
  and rooms IS_UNSAVABLE (exportAll skips it, nothing is in the
  database); SANDBOX ENTER, LEAVE and CLEAR; PREVIEW copies a catalog
  mob or item or a template there and shows it, its gear and its wares,
  gone on LEAVE; `GENERATE ... SANDBOX` runs GENERATE NOSAVE there and
  marks an area it makes unsavable, for CLEAR to take away.
- B13 done. `AHELP BUILDING` (arc_help.ini) tells the workflow: the world
  folder and the cache, SAVE ROOM first of all (and B4's LIST UNSAVED and
  SAVE CHANGED), UNDO and the backups, files edited outside the game
  (B2, B5), the tools that make and those that look, and what is not in
  the world folder. Builder mode's login ends with
  `SaveGame.builderSummary`: whether the world files loaded whole, files
  changed outside the game or rooms unsaved, and AHELP BUILDING.

#### 2d. Scripting (59)

Script triggers, the script language, and JavaScript: the hooks most later
items use.

ST2, ST3, ST4, ST5, ST6, ST7, ST8, ST9, ST10, ST11, ST12, ST13, ST14, ST15,
ST16, ST17, ST19, ST20, ST21, ST22, ST24, ST25, ST26, ST27, ST29, SC1, SC2,
SC3, SC4, SC5, SC6, SC7, SC8, SC9, SC10, SC11, SC12, SC13, SC15, SC16, SC17,
JS3, JS5, JS6, JS7, JS8, JS9, JS10, JS11, JS12, JS13, JS14, JS15, JS16,
JS17, JS18, JS19, JS20, JS23

- ST2 and ST3 done, with `core/ScriptEvents`: an event that is not a message
  goes to the scripts a message in the room would reach (the room, its items,
  its mobs and what they carry, the area and its parents) through a new
  `ScriptingEngine.eventTrigger`, and only if some parsed script has the
  trigger. `SKILL_PROG LEARN|IMPROVE|MASTER|FAIL|ANY skill|* [mask]`, for
  characters: LEARN from `addAbility` once in the world, and the race's or
  clan's skills a level brings; IMPROVE from a gain by use or practice;
  MASTER on reaching the most; FAIL from a failed proficiency roll. $t is the
  skill, $g the proficiency. `DISCOVER_PROG ROOM|AREA [PARTY] [mask]` fires
  when a room visit is first marked (StdRoom, and the Bard's, Sailor's and
  Prop_RoomRedirect's own marks); $g is the room's ID. @GLOBAL waits for ST12.
- ST4 done. New message types WEATHERCHANGE ("new old forecast"), TODCHANGE
  (DAWN, DAY, DUSK or NIGHT) and DATECHANGE ("day month season year") go
  to the scripts of every room of the area, what is in them, and the area
  (`ScriptEvents.deliver`, not through the rooms, so the area hears each
  once), from `DefaultClimate.weatherTick` (so WEATHER SET too) and the
  clock's `tickTock` (TIME SET too). `WEATHER_PROG new|ANY [old]`: $g the new
  weather, $0 the old, $1 the forecast (the event's own values go in $0 and
  on: `eventTrigger` takes them). `MOON_PROG phase|ANY` on the day the
  clock's moon changes. A restore's `setCurrentWeatherType` fires nothing.
- ST5 done. Hosts are noted per trigger as their scripts are parsed
  (`ScriptEvents.register`, held weakly), for the events heard anywhere.
  `QUEST_PROG START|ACCEPT|STEP|WIN|FAIL|END|ANY quest|* [detail]` from
  `DefaultQuest`: START when a run begins, ACCEPT after DO_ACCEPT, STEP
  (detail the step's number), WIN from `declareWinner`; when a run ends
  (stopped, run out, or stepped past its last step) END WON if anyone won
  in it, otherwise FAIL and END with EXPIRED or STOPPED; QUESTS DROP gives
  END DROPPED. $n the player or "the quest", $g the quest, $0 the detail.
  QUEST_TIME_PROG's -1 and -2 are in its help line.
- ST6 done, with ST23, ST28 and JS4 in it. `GAME_PROG NEWGAME|RESTORE|SAVE|
  NEWCHAR|SWITCHTO|SWITCHFROM|PAUSE|RESUME|TIMEMODE|ANY [mask]`, heard
  anywhere: NEWGAME or RESTORE at the first login after the game starts,
  NEWCHAR after character creation, SWITCHFROM and SWITCHTO from SWITCH,
  PAUSE, RESUME and TIMEMODE from their commands, SAVE from `SaveGame.write`
  before the snapshot, run at once (`eventCheck`), RETURN CANCEL refusing it.
  `TURN_PROG [every N] [mask]` at each party turn's end in turn-based time
  (WAIT alone too) and after a player's command that takes time in real
  time, for hosts with it in the leader's area; the count is in the save.
  ISTURNBASED(), ISPAUSED(), SAVESLOT(), GAMETIME() and TURNS() (ISDEV and
  ISBUILDER were there). `core/WorldHost`: world/scripts/*.script on a hidden
  item WORLD held by the player's room, ticked every tick. `core/WorldJS`:
  world/js/init.js's onBoot, onNewGame, onRestore, onBeforeSave (false
  refuses), onPlayerEnter, onSwitch and onBuilderStart; onPartyChange waits
  for ST10, `cm` for JS6. CHARCREATIONSCRIPTS is left as it was; GAME_PROG
  NEWCHAR does what it was for, with the character in the world.
- ST7 done. `VERB_PROG verb[,alias] [P phrase | keywords]` is looked at in
  `enqueCommand` and `prequeCommand`, before the command lookup, for a
  character's line, on what is within reach (carried, in the room, its
  mobs, exits, the room, the area); verbs match whole; it fires when the
  rest names the object (a room or area needs none) or has the phrase or a
  keyword; the first script that fits runs at once (`eventRun`), and
  RETURN CONTINUE hands the line back. `Prop_Verbs` (VERBS, MSG, OTHERS)
  does it without a script; `LIST VERBS` lists a room's. CMDFAIL_PROG is
  left as it is.
- ST10 done (with SC8's functions). `PARTY_PROG JOIN|LEAVE|REST|LEAD|SWITCH|
  ANY [mask]`, heard anywhere, from PARTY ADD's join, PARTY REMOVE,
  `Party.rest`, `handOver` and SWITCH to a member; $n the member, $t the
  leader; and onPartyChange(leader) in world/js/init.js. PARTYSIZE(),
  INPARTY(), PARTYLEADER() and $%PARTY()%: the group, the leader first
  (`ScriptEvents.group`).
- ST11 done. TIME_PROG takes DAWN, DAY, DUSK and NIGHT (the clock's own
  hours) and ranges with steps, DAY_PROG ranges. `SEASON_PROG`, `MONTH_PROG`,
  `YEAR_PROG` and `WEEKDAY_PROG` fire from the clock's day change (with
  ST4's DATECHANGE) for hosts with them in areas on that clock and the
  world's scripts; $g the new one, $0 the one before. ISWEEKDAY().
- ST9 done but DUESLATE (no dues fall due in the engine) and the
  government's EVENTSCRIPT (left for the F items). `DefaultClan.addMember`
  runs the government's entry script when one becomes a member (not an
  applicant), however it comes about (CLANACCEPT, CLANAPPLY, MPSETCLAN, a
  GuildMaster), and `delMember` the exit script; the commands' own copies
  are gone. `CLAN_PROG JOIN|LEAVE|PROMOTE|DEMOTE|EXILE|RELATION|CONQUER|
  LOSEAREA|LEVEL|ALL [clan]` from those, CLANEXILE, `setClanRelations`,
  the clan's level changes and Conquerable's win and loss, for hosts in
  the member's room, the clan's recall room and the area.
- ST12 done. `@GLOBAL` first on EXECMSG_PROG (the message type's category),
  DEATH_PROG and KILL_PROG (DEATH, with a mask on the dead or the killer)
  registers the host for that world-wide category when the script is
  parsed; such a line hears each message once (`firstGlobalHearing`),
  though a host in the room hears it twice. Event triggers take it too
  (`ScriptEvents.fireAround`): SKILL_PROG and FACTION_PROG hear the hosts
  around the event, and @GLOBAL lines anywhere. A host with an @GLOBAL
  line on a category hears that category's messages with its other lines
  too.
- ST13 done. `CRAFT_PROG skill|* words|ALL [RUINED|SUCCESS]` on the
  ITEMGENERATED message (ruined is a GenRuinedItem), `RECIPE_PROG skill|*
  words|ALL` on RECIPELEARNED; $o the thing, $g the skill's ID.
- ST14 done. `FACTION_PROG faction range|ANY|UP|DOWN [mask]` from
  `StdMOB.adjustFaction` (a standing that was there before, in the world):
  a range name fires only on coming into it; $g the new range, $0 the
  change, $1 the range before.
- ST15 done. `COMBAT_PROG START|END|FLEE|ANY [mask on the opponent]`: START
  and END from the fight as it was at the host's last tick (a mob's own, an
  item's owner's, any in a room; END with both alive), FLEE from the FLEE
  message. As ticks go, a fight with no player in the room is not seen.

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
