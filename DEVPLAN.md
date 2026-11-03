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
- ST16 done, with PR4. `ScriptEvents.moved` from `MUDTracker.move` and the
  login: when the areas differ, `AREA_LEAVE_PROG` and `AREA_ENTER_PROG`
  ([ALL] [pct|mask]) for hosts with them in the area left or entered (ALL
  counts a move between an area and one inside it); $g the other area, $0
  the other room. `Prop_AreaEntry` (ENTER, FIRST, LEAVE, TITLE or SHOWNAME,
  TEXT, DESC, ONCE, MASK, COLOR) and `Prop_AreaBanner` (the area's name and
  description by default) tell a player. Prop_Sounder is left as it is.
- ST17 done. `LEVEL_PROG [GAIN|LOSS|ANY] [pct|mask]`, GAIN when not said;
  $g the new level; a message heard from the room and the world fires
  once. The class is CLASS($n): $G cannot differ from $g, and $0 to $9
  stay the trigger's words, as SC14 made them.
- ST8 done. `BOARD_PROG` and `DISEMBARK_PROG [mask]` from ST16's area border
  hook when one side is a vessel's own area (the vessel, its rooms and crew,
  and the room outside hear it); `SAIL_PROG COURSE|ANCHOR|STEER|ALL` when one
  allowed to gives a navigable vessel an order (before it is carried out);
  `DOCK_PROG` when its anchor drops (no seaport test: any room);
  `VESSEL_ARRIVE_PROG room|area|ALL` for the vessel and crew, and
  `VESSEL_GREET_PROG words|ALL` for the room, after each step of `navMove`.
  $t is the vessel; JavaScript waits for JS8.
- ST19 done. `READ_PROG words|P phrase|ALL` in the engine's okMessage on
  READ: on an item only its own reading; it runs before the reading, so
  RETURN CANCEL keeps the item's text back. $o the item, $n the reader.
- ST20 done. After GENERATE makes an area or room, `ScriptEvents.generated`:
  a GENERATED message to its scripts and what is in it, `GENERATED_PROG [pct]`
  ($g the generator's ID), the generator's string variables kept for
  `GENVAR(area var)` (not saved), and an AREA tag's `onGenerate="..."` run
  once. Other generators (MPGENERATE, quests) do not send it yet.
- ST21 done but TALK (N9's). An NPC with scripts records each character it
  sees arrive, as GREET_PROG does, in the game's script variables (so saved):
  times met, first and last meeting. `FIRSTGREET_PROG [pct]`, `REGREET_PROG
  days [pct]`, `MEMORY(npc who MET|COUNT|LASTSEEN|DAYSSINCE|LASTTOPIC)`;
  `ScriptEvents.talked` is there for N9 to record a topic.
- ST22 done. `SOCIAL_PROG [EXACT] [ME] name`: EXACT matches the whole name
  (or its first word for a bare name), ME only a social aimed at the
  scripted mob; $g is the social's full name.
- ST24 done. `EVERY_PROG N [HOURS|DAYS|WEEKS|MONTHS] [CATCHUP]` on the area
  clock's hours, the next time in the engine's saved state (<EVERY>); with
  CATCHUP $g is how many intervals passed. Cron is left on its own clock
  (S21's).
- ST25 done. `VAR_PROG host|*|quest var cmp value` from `setVar`, for every
  host with it, when the change makes the condition true; $g the new value,
  $0 the old.
- ST26 done. `AFFECT_PROG ADD|REMOVE|ANY ability|*` from `addEffect` and
  `delEffect` on mobs, items and rooms (not the permanent properties), for
  the thing, its room and an item's owner, only when some script has it.
- ST27 done. `DESTROY_PROG reason|words|ALL` and `DECAY_PROG` at the start of
  `StdItem.destroy`, run at once (`eventCheck`) on the item, its room and
  owner; $g is DECAY (an item past its expiry) or JUNKED (MPJUNK), else empty.
- ST29 done. `ACHIEVE_PROG id|* [mask]` from `giveAwards`, for every host
  with it; $g the achievement's ID.
- SC1 done. Lists are words (quotes keeping words together), or the items
  between commas if there are any (`ScriptLib.listOf`). `FOR $0 IN LIST
  text` puts the text in first, then splits it. `MPLIST host var
  ADD|ADDUNIQUE|DEL|CLEAR|SHUFFLE|SORT words`; `LISTJOIN`, `LISTHAS`,
  `LISTCOUNT`, `LISTGET`, `LISTFIND`, `LISTPICK`. The newer functions and
  commands are in one place (`extraValue`, `extraTest`, `extraCommand`);
  any function that returns a value may be compared in an IF.
- SC2 done. `DATETIME(STAMP|DAYOFYEAR|WEEKDAY)`, `GAMETIME(HOURS|DAYS|
  MONTHS|YEARS)`, `ELAPSED(stamp unit)` (and as a test), `WEEKDAY()`,
  `TICKS(n unit)` and `MPSETVAR host var NOW`, on the area clock's hours
  since its year 0. ISWEEKDAY came with ST11. The ISRL tests still read the
  computer's clock.
- SC3 done. A CATALOG source for MPMLOAD (`CATALOG [FORCE] name [count]`),
  MPOLOAD (`INTO` as before), MPOLOADROOM and the shop forms, quest `LOAD
  MOB|MOBGROUP|ITEM|ITEMGROUP [n] CATALOG [FORCE] name` (no IMPORT needed)
  and RandomMonsters `;catalog:name,name`. Copies go through
  `changeCatalogUsage`, and none is made past an entry's cap (0 is none)
  unless FORCE; `$b` is the last copy. `CATALOG(name)` and
  `CATALOGCOUNT(name)`. FROMFILE and FROMGENFILE's `ALL` now load all. Shop
  stock is checked against the cap but not counted, as before. The
  uniqueness rules of item 1 are not there yet.
- SC4 done. The host `@code` (`MPSETVAR @$i BRIBED 1`, `$<@$i BRIBED>`,
  `VAR(@$i BRIBED == 1)`) is the object itself: its variables are kept on
  it, in its ExtraData effect (`core/ObjectVars`), so each copy has its own
  and a save writes them with it. `MPSTATE thing name value|CLEAR` and
  `STATE(thing name)` use the same store; `MPSETINTERNAL VARHOST INSTANCE`
  (kept in the engine's saved state) makes a bare `$i` mean `@$i`. A mob
  that comes back to life loses them, unless its variable PERSIST is set.
  JavaScript's `state()` waits for JS17.
- SC5 done. The script's context keeps the message (`MPContext.cmsg`):
  CNCLMSG_PROG's live one, EXECMSG_PROG's a copy, as messages are reused.
  `MSG(TYPE|VALUE|SOURCE|TARGET|TOOL|TOOLID|STEXT|TTEXT|OTEXT|SMINOR|TMINOR|
  OMINOR)`, as a value and a test; `MPMSG VALUE n|+n|-n|*x|/x`, `MPMSG TEXT
  S|T|O text|NULL`, `MPMSG TARGET thing`. The `=S|T|O STAT=VALUE` filters
  take `*` wildcards, and `=S|T|O MASK=...` a zapper mask. `$q` was
  already the scripted thing's name, so the tool's is `MSG(TOOL)`.
- SC6 done. `MPCALLFUNC target func args` and `CALLFUNC(target func args)`
  call a function of another thing's scripts (when the first word is not a
  function of the script's own; `ScriptEvents.callFunction`, through the
  engines' `callFunction`). `MPSIGNAL target name [args] [DELAY ticks]`
  fires `SIGNAL_PROG name|ALL` ($g the args, $n the actor, $t the sender,
  $o too if an item), queued in each target engine (so saved), the target
  a thing, a room's ID, an exit (`room:north`, `here:north`),
  `TAG:tag[@WORLD]`, ROOM, AREA or WORLD (`CMMap.scriptHosts`). JavaScript's
  `signal()` waits for JS17.
- SC7 done. `MPWAIT ticks`, `MPWAIT n HOURS|DAYS|WEEKS|MONTHS` (area
  clock) and `MPWAIT UNTIL (condition) [max ticks]` (each tick, up to 1000
  by default). The engine keeps the bodies running on each thread
  (`frames`); a wait queues what is left of the body and of each IF and
  SWITCH block it is in as one continuation, run on the game thread like
  any queued response, with $n, $t, $o, $g and $0-$9. Saved with the
  engine's state (`<Q W="1">`, its words in $0-$9 kept). A FOR or
  MPWHILE body, or a function another line called, cannot pause: it says
  so and goes on. `MPSETINTERNAL CANCELWAITS` drops the pending waits.
- SC8 done. `$u`/`$U` is the character at the terminal (`Party.terminalOf`
  of the source's player, or the game's one terminal); `ISPLAYED(who)`;
  `INPARTY(who)` with one argument is the terminal character's party;
  ST10 gave PARTYSIZE, PARTYLEADER and PARTY. With windows off, MPECHOAT to
  a party member in the background also goes to the terminal, as
  `[Name] text`, and MPPROMPT, MPCONFIRM and MPCHOOSE ask there, named. Not
  MPECHOAROUND, whose others in the room include the one played. The guide
  (SC15) says that the account tattoos are the profile's.
- SC9 done. The lines HELP SCRIPTABLE lacked, from the engine's `progs`,
  `funcs` and `methods`: the -ING and other triggers (CMDFAIL_PROG among
  them), 28 functions, 19 commands and MPSETINTERNAL's settings.
  MESSAGE_TYPES is written from `CMMsg.TYPE_DESCS` and points at LIST
  MSGTYPES. The review fixed what the lines would otherwise have had to
  call broken: ISSPEAKING(who COMMON), ISRLMONTH() from 1, CANSEE() and
  CANHEAR() values, HASACCTATTOO's $ codes, CLANQUALIFIES (never true;
  now as CLANAPPLY checks), and CMDFAIL_PROG with a command alone or the
  first and last words after it. Left as they are, and said so in their
  lines: ITEMCOUNT counting things in containers twice, MPRLOAD doing
  nothing, DROPPING_PROG stopped by MPNOTRIGGER DROP_PROG. The
  single-player notes and CMDFAIL_PROG's example are in the guide (SC15).
- SC10 done. `UPPER`, `LOWER`, `CAP`, `LEN`, `TRIM`, `SUBSTR`, `REPLACE`,
  `PADRIGHT`, `ANAME`, `PLURAL`, `STRPOS`, `STRSTARTS`, `STRENDS`. A
  script's quotes reach the functions as backticks, as the script parser
  keeps them; the functions read both.
- SC11 done. `DICE`, `PICK`, `WPICK`, `ROLLNORMAL`, `CLAMP`, `ABS`;
  `NUMBER()` takes signs and decimals. MPSETVAR's arithmetic is on longs,
  with `%N` (the remainder) and `^N` (the power); `=value` sets the value as
  it is, so `=-5` is not a subtraction.
- SC12 done. `NUMTAGGED(tag)`, `TAGGED(tag)`, `TAGS(thing)` and `MPTAG thing
  ADD|DEL|TOGGLE tag`; HASTAG's error names it.
- SC13 done. `LOAD=file KEY=value...`: the file is the first word, the rest
  `CMParms.parseEQParms` pairs; `ARGDEFAULT KEY=value` lines in the file
  give defaults (and are taken out before parsing). `ARG(name)` as a value
  and a test; each engine reads its own (`loadArgs`), and since the object's
  text holds them, the parse cache key differs too. The FUNCTION_PROG
  pattern goes in the guide (SC15).
- SC15 done. `guides/ScriptingSUD.html`, linked at the top of
  `Scriptable.html`: what is different alone (logins and GAME_PROG, the
  profile's tattoos, the party, channels, time), what a save keeps, a
  quick reference by purpose with what 2d added, a cookbook (once ever, a
  cutscene, a brazier puzzle, a timed return, catalog spawns, one script
  for many guards, five guards with their own bribes), CMDFAIL_PROG, and
  the less known ($@x, timed tattoos, MPAT, MPSCRIPT EXECUTE, MATH's
  operators, HASTAG, MUDDAY, CHARCREATIONSCRIPTS). Scriptable.html itself
  is not rewritten entry by entry.
- SC16 done. `+ANYINGROUP mask` and `+ALLINGROUP mask` (and `-` forms, the
  answer turned around) apply the rest of a mask to the subject's group in
  the room (`ScriptEvents.group`), as FOLLOW applies it to the leader; a
  player's record, with no group, is checked alone. Every mask user gets
  them.
- SC17 done. `ELSEIF c` and `ELSE IF c` read as ELSE and an IF with one
  more ENDIF (`expandElseIf`, when the script is parsed). `WHILE (c) [max]`
  ... `ENDWHILE`, a body cached on the line, at most 1000 passes by
  default; `CONTINUE` in WHILE and FOR. `MPLOCAL name value` and `$(name)`
  (`MPContext.locals`, shared by the blocks of one run, kept by MPWAIT);
  `FUNCTION_PROG name A B` binds `$(A)` and `$(B)` from the call's words, in
  its own locals. Plain comparisons in IF are not done.
- JS3 done. `core/JSScope.prepare`, called by every JavaScript scope (a
  script's <SCRIPT>, JRUN, JCONSOLE, quests, cron, world/js/init.js, JS
  classes, the JavaScript engine), installs Rhino's CommonJS `require`,
  sandboxed, from world/js/lib then resources/js/lib; modules are compiled
  once (`SoftCachingModuleScriptProvider`) and read again when their file
  changes; `JSScope.reload()` drops them. `<SCRIPT SRC=...>` is left to
  JS15's MPJSFILE.
- JS5 done. `world/js/<kind>/` folders (behaviors, abilities, properties,
  items, mobs, races, charclasses, commands, common, locales, exits, areas)
  join the class paths at boot (`SUD.worldClassPaths`, with S6's
  `world/classes/`). `LOAD=world:path` reads a world file (`world/scripts/`).
  `world/js` and `world/scripts` are in the world's checksum and in LIST
  SCRIPTS CUSTOM; WORLD CHECK has a JS part (`JSScope.checkWorldClasses`: an
  //extends naming no class, a file that does not compile). Templates in
  `resources/js/templates/`; `Behaviors/JSWorldBehavior` (rooms and areas)
  calls onEnter, onLeave, onWeather and onDaypart, with echo, roll, spawn,
  room, area and parm. Problems show at boot in the log and in WORLD CHECK,
  not at the builder's login.
- JS6 done. Every scope is an ImporterTopLevel (importPackage,
  importClass) and `JSScope.prepare(cx, scope, host)` gives it `cm`
  (`core/JSCm`: finding, loading from the catalog, saying and doing,
  `cm.mp`, dice, variables and stats, the player and party, and world
  members clock, passTime, advance, weather, law, shop and bank over what
  DT20, DT21 and DT22 use), `sud` (JS22's time, party, save, find, vars and
  on), `include(path)` (resources/ or world:, no `..`, compiled once while
  unchanged) and `debug(x)` where the scope has none. `core/JSEvents`:
  `cm.on(event, fn [, key])` for hour, daypart, day, month, season, year and
  weather of the player's area (looked at each tick) and GAME_PROG's events
  (a save listener returning false refuses), keyed by the function's text
  when no key is given; `sud.time.waitUntil`. JCONSOLE sets here, area and
  target for each line. `guides/JavaScriptSUD.html`. cm.holiday and
  cm.market wait for W6 and W27 (later).
- JS7 done. `core/JSStore`: `cm.store(key)` (and `cm.storeKey`,
  `cm.stores`), one live object per key until a restore; keys are strings or
  `room:ID`, `mob:startRoom/name`, `item:where/name`, `area:name`,
  `quest:name`. Saved as JSON in world.xml's `<JSSTATE>` (after
  SCRIPTVARS), put back at the end of `WorldState.apply`, before onRestore;
  a new game reads `world/js/state.json`. Only what JSON writes is kept.
  `JS STATE [key] [= {value} | DELETE]` in builder mode (a value is JSON or
  a JavaScript object, as the command line drops JSON's quotes). SAVE INFO
  counts the stores. The smoke test sets one before a save and reads it
  after the restore.
- JS8 done. `cm.on` hears world events wherever they happen:
  death and beforeDeath (`MUDFight.postDeath`; false leaves the victim at 1
  hit point), levelUp (`CoffeeLevels.level`, after the level is applied),
  questWin and questEnd (`ScriptEvents.quest`), enterRoom and enterArea
  (`ScriptEvents.moved`), beforeEnterRoom (`MUDTracker`, after the
  room's okMessage; GOTO is not stopped), itemGained and itemLost
  (`StdMOB.addItem`/`delItem` for a mob in a room, looked at only while
  something listens), factionChange (`ScriptEvents.faction`) and turn
  (`ScriptEvents.turn`). Names are in any case; `cm.off(event, fn)` takes
  away a listener added without a key. hour, day, weather, save and
  restore were JS6's.
- JS9 done. `core/JSHooks` runs a file named by SCRIPT= (resources/
  or world:, compiled once while unchanged, `JSScope.compileFile`, which
  include() now uses) in a scope of its own per property or behaviour, with
  the other parameters as `parms` (numbers as numbers) and CMMsg. The
  property `Prop_JScript` (mobs, items, exits, rooms, areas) calls
  affectPhyStats, affectCharStats, affectCharState, okMessage (false
  refuses), executeMsg, tick, onAdded and onRemoved; the behaviour
  `JSBehavior` the message and tick hooks, onAdded and onRemoved. What a
  hook changes in parms is written back to the text, so it is saved with
  the host (the stat hooks' changes are not). A hook that throws is
  reported (JS ERRORS) and not called again until the text is set again.
- JS10 done. `core/JSReload`: `JS RELOAD [file|ALL]` in builder mode takes
  the world/js files changed since the game started or their last reload (or
  the one named, or all). Each is compiled first; one that does not compile
  is reported as file:line and left. init.js is read again
  (`WorldJS.reload`, onReload(old) with the old scope; onBoot is not run
  again); lib/ drops the require cache (a scope keeps what it required); a
  JS5 class folder recompiles the class (`CMClass.loadClass`) and replaces
  its behaviours, effects and mobs' abilities in the world, text and
  parameters copied (other kinds: those made later use it); other files are
  read again by each `Prop_JScript` and `JSBehavior` running them
  (`JSReload.Holder`, `JSHooks.reload`, onReload(old)). `JS WATCH ON
  [seconds]|OFF` looks on a thread, as DEVWATCH does, and has the builder JS
  RELOAD. JScriptable (JS11) will join the holders.
- JS11 done, as the behaviour `JScriptable` (mobs, items, exits, rooms,
  areas) over `JSScope` and JS7's stores rather than by finishing the
  unused `Common/JavaScriptingEngine`, which is left as it was (JS18 need
  not choose again). Parameters `LOAD=world:js/npc/x.js [SCOPE=name]` or the
  script itself; the scope has me, cm, CMMsg and `state` (cm.store of the
  host, or of `scope:name` when shared). Only the handlers it defines are
  called: onLoad, onGreet, onArrive, onLeave, onSpeech, onGive, onGet,
  onDrop, onWear, onRemove, onOpen, onClose, onLock, onUnlock, onDeath,
  onKill, onDamage, onCast, onLook, onTick, onHour, onDay, canMessage (false
  refuses), afterMessage, onReload; onCommand(who, verb, rest) through
  `ScriptEvents.verb` (before the command's own; true handles it). Its
  other functions answer MPCALLFUNC and CALLFUNC, of its own host (when no
  thing of that name is found) or another (`ScriptEvents.jsFunction`).
  SCOPE=GLOBAL or a name shares one scope among hosts, me and cm set for
  each call; JS RELOAD makes a shared scope again once. Saved as a
  behaviour, so the engine choice in `<SCRPT>` is not needed.
- JS12 done. `JCONSOLE @mob|item|here|area|me` (core/JSConsole)
  binds a console, kept by builder and target, to the target: its MOBPROG
  calls run as the target ($i), the builder as source, and me, here and cm
  are the target's. Dot commands: `.trigger GREET|SPEECH text|GIVE
  item|LOOK|TICK` delivers the event to the target (its JScriptable and its
  scripts, whose queued responses run at once), `.load file`, `.reset`,
  `.inspect expr` (name, class, room, stat codes; a JS object as JSON),
  `.time on|off`, `.history [n]` and `!n`. In builder mode the history is
  kept in world/.history/jconsole-NAME.txt (the last 200 lines).
- JS13 done. `JSScope.prepare` puts a `Math.random` over
  `CMLib.dice().getRandomizer()` in every scope, so RANDOMSEED= and the
  save's dice state cover JavaScript too. `cm.ticks()` (game time over the
  tick length, also `sud.time.ticks()`) and `cm.realNow()`; `cm.now()` and
  `cm.calendar()` were JS6's. `Date` is left on the real clock: making
  `Date.now()` and `new Date()` read GameTime could break code that wants
  real dates, and cm.now() already gives game time.
- JS14 done. Mask entries `-JS "expr"` (requires it true, as -IF)
  and `+JS "expr"` (passes what it is true for, as +IF) in
  `MUDZapper` (description, compile, check, records, separating), over
  `core/JSMask`: compiled once with the mask, run in one scope kept for
  masks under the time limit, seeing mob (or the item's owner), item, room,
  target (what is checked) and cm. An error counts as no match and is
  reported once for each expression; a player record (not in the world)
  never matches. zappermasks.txt lists them.
- JS15 done. `JS(expr)` (173) and `JS$(expr)` (174): in IF, true when
  the expression is truthy; in `$%JS(...)%`, its value as text. They run in a
  scope kept by the engine, with a <SCRIPT>'s functions set for each call
  (host(), source(), target(), cm), approved as a <SCRIPT> is.
  `MPJSFILE file args` (114) runs a file of resources/ or world: as a
  <SCRIPT>, with `args`. In a <SCRIPT> or such a file `cancel()`, or a last
  value of "cancel", ends the prog with CANCEL, so CNCLMSG_PROG refuses its
  message. The <SCRIPT> scope is made in one place (`jsEventScope`).
- JS16 done. `JS HELP expr` (`JSConsole.describe`): a Java object's
  methods, each with the numbers of arguments it takes, and a thing's stat
  codes and values; a class's static methods; a JavaScript object's
  properties. guides/JavaScriptSUD.html gains a table of where JavaScript
  runs and what each place has, errors and time limits, the old tricks, and
  worked examples (a shopkeeper, a cursed sword, a ferryman; JS18 adds a
  quest).
- JS17 done. A <SCRIPT> (and MPJSFILE's file, and JS()) also has
  `msg()` (SC5's message of a message trigger, or null), `room(id)`,
  `findItem(where, name)` (a room or its ID, a mob, a container),
  `door(roomId, dir, op)` (OPEN, CLOSE, LOCK or UNLOCK on both sides, no
  message: I2's MPDOOR is not built yet), `signal('target name args')`
  (MPSIGNAL) and `state(thing, name[, value])` (STATE and MPSTATE);
  cancel() was JS15's. Written up in guides/Scriptable.html's <SCRIPT>
  section and JavaScriptSUD.html.
- JS18 done. `core/JSQuests`, from `ScriptEvents.quest`: a quest's
  JavaScript is its `<JSQUEST>` block (kept by `DefaultQuest.setScript`, skipped
  when the quest runs) or the file `SET JSFILE` names; its scope (made again
  when the text changes) has quest, quests and state, `cm.store('quest:name')`,
  saved with the game. Handlers onStart(state), onStep(n, state),
  onTimeUp(state) (FAIL EXPIRED), onWin(player), onAccept(player), onStop().
  The JScriptables of the quest's mobs and items get onQuestAccept,
  onQuestStep, onQuestWin, onQuestFail and onQuestEnd
  (`JSFunctions.callHandler`). The `quests` helper: get, start, step, win,
  fail, stop, running, stepNumber, status, objective (INSTRUCTIONS),
  journal, getVar and setVar (the quest's own variables). JavaScriptingEngine
  stays unused, as JS11 decided; quest <SCRIPT> keeps its old helpers.
- JS19 done. `core/JSTimers`, looked at each tick by JSEvents' ticker:
  `cm.every(spec, fn [, key])` (ticks, or N ticks|hours|days on the clock),
  `cm.at(spec, fn [, key])` (hour, day of the month, month, season, in any
  mix; it fires as the clock comes to it), `cm.after(ticks, fn [, data])`,
  `cm.stopTimer(key)` and `cm.timers()`. every and at are code, not state:
  register them in init.js's onBoot. An after() is kept in the store
  `js:timers` (JS7's JSSTATE) as its due game time, its function's text
  and its data; in the same game the function itself runs, after a restore
  (`JSStore.fromXML`) it is made again from its text in a scope of its own.
- JS20 done. `cm.command(name, opts, fn)` (`core/JSCommand`, a
  StdCommand put in CMClass's commands, so its cost and combatCost are taken
  as a command's are; `name ?` shows its help; a later one of the name
  replaces it; `cm.uncommand`). `cm.verb(thing, phrase, fn)`
  (`core/JSVerbs`, in `ScriptEvents.verb` before the game's commands): the
  first word the command's, the phrase's other words all among what
  follows, near what is within reach; elsewhere "You can't do that here."
  when the word is no command or social; `cm.unverb`. Both are code: register
  them in init.js's onBoot. CMDFAIL_PROG is now written up in
  guides/Scriptable.html too.
- JS23 done. `core/JSProgression`: world/scripts/progression.js,
  read again when it changes, in the usual scope, each function called if
  defined. xpGain(mob, source, amount) in `CoffeeLevels.postExperience`, for
  gains only (a loss is not passed), its number (not under 0) the gain;
  practicesForLevel(mob) and trainsForLevel(mob) in `level`, their numbers
  in place of the usual; onLevel(mob, level) after them;
  onLearn(mob, abilityId) with ST2's LEARN
  (`ScriptEvents.skill` and `levelGrant`); onTrain(mob, what, amount) after
  TRAIN (MANA, MOVES, GAIN, PRACTICES, HITPOINTS, a stat's name or a class's
  ID); onCreated(mob) before GAME_PROG NEWCHAR; onCreationStep(mob, step,
  answer) after the race, gender, stats, class, factions and deity steps,
  next to the charcr scripts; statRoll(mob, bonus) for `STARTSTAT=SCRIPT`,
  which sets the stats or returns them by name, abbreviation or a name's
  start, or as a list in the base stats' order (the others at BASEMINSTAT).
  P17's formulas, when built, are the other way to the per-level numbers.

#### 2e. Quests (15)

Q1, Q2, Q3, Q4, Q5, Q6, Q7, Q8, Q10, Q11, Q12, Q13, Q14, Q15, Q16

- Q1 done. A quest with no WAIT, INTERVAL or DATE is no longer deleted
  when it stops (`DefaultQuest.enterDormantState`): it stays listed,
  FINISHED once it has run, with its winners and variables, and the save
  keeps it so (`<FINISHED>` in world.xml's quests); only DESTROY QUEST
  takes it away. Copies (generated and spell quests) still go. `SET
  RESTART ALWAYS` (as before: it may be started again), `NEVER` (it does
  not start again) or a game time (it starts again by itself that long
  after, and not before); `SET ENDONWIN TRUE` (a win stops it, with its
  cleanup); `SET AUTOSTART BOOT` (once, a tick after the game begins, then
  finished). `MODIFY QUEST x REOPEN` makes a finished quest one that has
  not run. LIST QUESTS, QUESTINFO and the JavaScript quests helper say
  finished.
- Q2 done. `Prop_QuestMarker` on a mob or item (QUEST=name or *,
  AVAILABLE=, ACTIVE=, READY=, READYFUNC=) puts a mark after it in room
  listings, for each viewer (`core/QuestMarkers`, from
  `Sense.getDispositionBlurbs`): AVAILABLE ((!)) while they may accept the
  quest (its CAN_ACCEPT, as Prop_QuestGiver asks), ACTIVE ((?)) once they
  carry its script, READY (a bright (?)) when READYFUNC, a FUNCTION_PROG of
  the host's run with them as $n, returns something. With *, the running
  quests it has a script from, is first quest mob or item of, or gives by
  Prop_QuestGiver. Only a player's characters see marks. `QUESTMARKERS=AUTO`
  (coffeemud.ini or world.ini) marks every running quest's first quest mob
  and every Prop_QuestGiver host without the property.
- Q3 done. `core/QuestJournal`: each character's quest journal, kept
  as JSON in a hidden Prop_QuestLog, so it is saved with them: for each
  quest, objectives (an ID, a count to reach, the count, DONE or FAILED,
  hidden or not, the text) and entries dated on the game calendar.
  `MPQOBJECTIVE who quest ADD id target text`, `SET`/`BUMP id (n)` (the
  count reached is DONE), `DONE`/`FAIL`/`HIDE`/`SHOW id`, `CLEAR (id)`;
  `MPQJOURNAL who quest text`; `QOBJECTIVE(who quest id)`, the count, DONE
  or FAILED, as a test or a value. A quest step's `SET OBJECTIVE id target
  text` and `SET JOURNAL text` go to those on the quest when the step
  starts, and to whoever accepts it (`acceptQuest`) during the step. QUESTS
  groups by SET CATEGORY, main first, and shows each quest's objectives
  ([x], [ ], [-]) and time left in game hours or days; OBJECTIVEn and
  OBJECTIVEnDONE variables of the quest's script are shown the same way.
  `QUESTS name` adds the objectives and the history; `QUESTS LOG` the
  latest entries. A "Quest updated:" line tells each change, unless
  QUESTNOTICES=OFF (coffeemud.ini or world.ini).
- Q4 done. `core/QuestRecords`: how each quest stands for each
  character, saved with the world (world.xml's QUESTRECORDS): ACTIVE once
  accepted (`acceptQuest`, or carrying the quest's script with no record),
  WON, FAILED, ABANDONED (QUESTS DROP) or EXPIRED, an outcome word and when
  on the game clock, and the wins (Q5). `MPQUESTWIN who quest OUTCOME
  word`; `MPQUESTFAIL who quest (reason)` records it, fires the new
  QUESTFAIL_PROG quest|* and QUEST_PROG FAIL, and ends and takes away the
  character's quest scripts as MPENDQUEST who does. `QUESTSTATUS(who quest)`
  and `QUESTOUTCOME(who quest)`, as tests and values. Masks `-QUESTFAIL
  +quest` and `+QUESTFAIL -quest`, `-QUESTOUTCOME +quest:word` and
  `+QUESTOUTCOME -quest:word` (as -QUESTWIN and +QUESTWIN go). `GIVE STAT
  KEYPLAYER FAIL` (QuestBound): the mob's death fails the quest for each
  character on it, reason KEYPLAYER, and the quest goes on; TRUE still
  resets it. QUESTS WON shows the outcomes, QUESTS FAILED the rest.
- Q5 done. `SET REPEATABLE NO|YES|<game time>` (a character who
  won it may take it again never, at once, or that long after their last
  win) and `SET MAXWINS n`, asked by `canAcceptQuest` before CAN_ACCEPT,
  by Q4's records: each win counts and is timed on the game clock (a
  winner from before the count is one win). `QUESTWINS(who quest)` and
  `QUESTWINAGE(who quest)` (game hours since the last win, -1 if none);
  masks `-QUESTWINS +quest:>=10` and `+QUESTWINS -quest:>=10` (>=, <=, >,
  <, = or !=). QUESTS WON shows the wins. QUESTINFO shows the settings.
- Q6 done. `STEP name` and `STEP AUTO name` name the step that
  follows (`DefaultQuest.stepLabels`); `STEP GOTO name`, anywhere in a
  step, makes that step the next when this one steps on, without ending
  the step, and is no step's end where steps are counted (the quest
  library's too). `MPSTEPQUEST quest =name`, `MODIFY QUEST x STEP name`,
  `QVAR(quest STEPNAME)` and `getStat("STEPOF:name")`; QUESTINFO shows the
  step's name. A save keeps the name with the number (`<STEPNAME>`), and
  the name wins on restoring, so an edited file restores to the right
  step. QUESTCHECK reports a STEP GOTO to no step, and knows the newer SET
  names (JSFILE, RESTART, ENDONWIN, AUTOSTART, OBJECTIVE, JOURNAL,
  REPEATABLE, MAXWINS); its WAIT-without-INTERVAL note now says the quest
  is kept FINISHED (Q1), not deleted. The STEP line has a help entry at
  last, in the quest script's.
- Q7 done. Prop_QuestGiver `REOFFER=ALWAYS|ONCE|<game time>` on any
  target (`mayOffer`): whenever the trigger comes, once in a run of the
  quest, or that long after the last offer; without it, as before (an area
  once, the rest each time). Offers are noted with their game time in the
  quest's variables (OFFERED:NAME, and SHOWN:NAME for one only listed), so
  the save keeps them (`QuestRecords.offered`, `shown`). A quest board's
  entry read is shown too. `QUESTS OFFERED` lists those offered or shown
  and still open to the character (`canAcceptQuest`); `QUESTS ACCEPT
  n|name` takes one on (`acceptQuest`).
- Q8 done. `Prop_QuestItem QUEST=name (NODROP NOSELL NOGIVE)
  (KEEPONDEATH) (REMOVEON=...)` on an item: drop, sell and give are refused
  with a message; KEEPONDEATH keeps it at death (Prop_ItemBinder's way, the
  item-death-keeper sense); it goes when the holder's quest leaves ACTIVE
  as REMOVEON says (`core/QuestItems`, from Q4's records: WON, FAILED,
  ABANDONED, EXPIRED; ENDED when the quest stops, but not while a save is
  being put back), ALL by default, NONE never. `QUESTS name` lists what
  the character carries for it.
- Q10 done. `SET REWARDS "awards"` (`core/QuestRewards`): the
  achievements' award words (`AchievementLibrary.parseAwardsList`, public
  now, and `giveAwards`), and N% XP (of a level), FACTION:id:+n and
  ITEM:catalog_name. `MPQUESTWIN who quest REWARD` gives them with one
  message ("Your reward: ..."); `MPREWARD who "awards"` gives ad-hoc
  ones. Prop_QuestGiver's details, quest boards and `QUESTS name` show
  Reward:. QUESTCHECK knows SET REWARDS.
- Q11 done. `MPQTIMER who quest <game time>|OFF`
  (`core/QuestTimers`): a character's own deadline for a quest, due on the
  game clock (so pause and turn-based time hold it), kept in their quest
  journal (Q3) and so in the save, gone when the quest ends for them (won,
  failed, dropped). A ticker from the game's start checks the deadlines;
  when one comes, the new `QUESTTIMEOUT_PROG (quest|*)` runs at once on
  the character's scripts (a bare one on those the quest gave them,
  `ScriptEvents.questTimeout`), busy or not; with none, the quest fails for
  them, EXPIRED (`QuestRecords.fail`). QUESTS and `QUESTS name` show the
  time left, theirs or the quest's duration, the sooner.
- Q12 done. `SET SHARED NONE|PARTY|PROFILE` (`core/QuestSharing`).
  PARTY: `acceptQuest` hands the quest on to the party members in the room
  (the players' characters of the group), each through DO_ACCEPT and the
  step's journal; MPQUESTWIN (with OUTCOME and REWARD) and MPQUESTFAIL
  (`QuestRecords.fail`) go to every party member on it. PROFILE:
  `whenLastWon`, and so QUESTWINNER and the QUESTWIN masks, and Q4's and
  Q5's records (QUESTSTATUS, QUESTOUTCOME, QUESTWINS, QUESTWINAGE, the
  masks, REPEATABLE, MAXWINS) answer for any character of the profile; the
  journal's entries (now timed on the game clock) are read by all of them,
  and `QUESTS name` shows the quest to one who does not carry it. QUESTINFO
  and QUESTCHECK know SET SHARED.
- Q13 done. Six story templates for CREATE QUEST's Quest Maker
  (resources/quests/templates/story_*.quest): story_talk1 (speak with a
  mob, optionally handing over an item the giver gives), story_chain1
  (three stages, each a mob, an objective and a journal entry; the Quest
  Maker has no repeat, so it is three, and a longer chain copies a stage),
  story_choice1 (MPCHOOSE between two outcomes, each with its words,
  MPREWARD reward and MPQUESTWIN OUTCOME word; the mob may die by the
  second), story_useitem1 (drop or put the given item in a room),
  story_explore1 (reach a room) and story_bounty1 (kills of a mask
  counted as an objective, paid at the giver, SET REPEATABLE cooldown).
  Each offers with Prop_QuestGiver, marks the giver and turn-ins with
  Prop_QuestMarker (an empty mark, AVAILABLE=, now shows nothing), keeps
  objectives and the journal, and fails with MPQUESTFAIL if its other mob
  is gone. `#!QUESTMAKER_MODE MULTIPLAYER` on the competitive_* templates
  leaves them out of the list (named, they still load). FakeSession's
  timed yes/no no longer calls itself for ever.
- Q14 done. `Prop_QuestBoard` on an item or a room (`core/QuestBoards`):
  COUNT jobs (3) generated through RandomQuests (TEMPLATE, FILE as its
  PATH, TAGID and its other parameters passed on, VARS opened into more
  variables), listed on LOOK (READ for a readable item), noted as shown for
  QUESTS OFFERED, and taken with the new `ACCEPT n` command (the board read
  last, else QUESTS ACCEPT). At each REFRESH on the game clock (1 mudday,
  or MUDDAY:1) the jobs done or given up go and new ones come up to COUNT;
  with none taken the oldest is replaced. The generator gets the reader's
  PLAYER_LEVEL, PARTY_SIZE, PLAYER_FACTION_[ID] and QUESTS_DONE
  (`QuestRecords.questsWon`), and with LEVELRANGE=n the target area levels
  around it. Jobs wait with no expiry unless EXPIRE= is given, and carry
  QUESTBOARD and POSTED quest variables, so a saved game (S8) keeps them
  with their board.
- Q15 done. `SET DROPPABLE FALSE`: QUESTS DROP refuses the quest
  (`Quest.canDropQuest`). A `FUNCTION_PROG CAN_DROP`, on the character's
  script for the quest or its accept script, that returns something
  refuses too, with its words. `FUNCTION_PROG DO_DROP` now runs on the
  confirmed drop (`Quest.dropQuest`), on both scripts, before the quest's
  scripts go; the quest is ABANDONED for them (Q4). QUESTINFO and
  QUESTCHECK know SET DROPPABLE.
- Q16 done. A journal objective's place: `AT=<room ID|area|QUESTOBJ:n>`
  after MPQOBJECTIVE ADD's ID (and so in a step's SET OBJECTIVE), or
  `MPQOBJECTIVE who quest AT id <where>` later; QUESTOBJ:, QUESTMOB: and
  QUESTITEM: (a number or a name) follow the quest's mob or item. QUESTS
  shows an open objective's place as (area, room); `QUESTS WHERE n` gives
  the first step toward it through the tracking library, or says it is
  here, or that the way is not known (`QuestJournal.where`). The world
  option QUESTTRACK=OFF|AREA|PATH (PATH by default) says how much.

#### 2f. NPC behaviour and dialogue (21)

N1, N2, N3, N4, N5, N6, N7, N8, N9, N10, N11, N12, N15, N16, N17, N20, N21,
N22, N24, N28, N29

- N1 done. Any behaviour may have `WHEN="<zapper mask>"`, tested on its
  host (mob, item, room, area or exit), `TOD=DAWN|DAY|DUSK|NIGHT` (several
  with commas) and `HOURS=a-b` (on the host's clock, round midnight when a
  is past b). While the condition fails, the behaviour neither ticks nor
  hears messages (`core/BehaviorWhen`, asked by StdMOB, StdItem, StdRoom,
  StdArea and StdExit before they tick a behaviour or pass it okMessage or
  executeMsg), so all of them have it without a change of their own;
  scripts are not gated. STAT BEHAVIORS says whether it is on now, and its
  unknown-key check knows the three keys. For the opposite case, a second
  copy with the opposite condition.
- N2 done. `MPMENU who FUNCTION "prompt" key:"label"[IF cond] ... [default]`
  (`core/ScriptMenus`): the choices numbered, those whose condition fails
  left out; the script goes on at once. The character's next line answers
  it (through the session's own non-blocking prompt, so it is no command
  and takes no time in turn-based play), by number or key; Enter, or an
  answer that is none of them, takes the default, and with no default it
  asks again. Then that FUNCTION_PROG of the same script runs with the key
  as $0 and $g, the character as $n. One menu at a time per character.
  MPDIALOG waits for N14 (in `LATER.md`).
- N3 done. Behaviour `Schedule`: ';'-separated entries `WHEN: PLACE
  [ACTIVITY...] [SAY/EMOTE text]`, the first whose WHEN holds in force.
  WHEN: DAWN, DAY, DUSK, NIGHT or hours, with DAYS=, SEASON=, WEATHER= or
  MASK= after it (HOLIDAY= is a MASK). PLACE: a room ID (to the first
  activity word, so IDs with spaces work), HOME or START, or
  ANY:"room mask". Activities: SLEEP or SIT (on an item), WANDER n (now
  and then a step, within n rooms of the place), SHOP (trading only there,
  SHOPMSG= elsewhere),
  WORK "emote|emote", LOCK (the place's doors, unlocked when the entry
  ends, kept in the save), HIDE. It walks by the tracking library, holds
  Mobile off on the way, goes back after a fight, and goes back to sleep
  when woken; with no player in either area it is put there at once, so a
  restore, a time jump or an empty area finds it in place (that is N8's
  catch-up for Schedule). With no entry in force it stays put. STAT($i
  SCHEDULE) is the entry in force: STAT() now also reads a stat of the
  host's behaviours.
- N4 done. Behaviour `Sleeper`: ALWAYS (the default) or `SLEEPS=` a time
  of day or hours (not TOD= or HOURS=, N1's switch for the whole
  behaviour), `BED=`, `WAKEON=ATTACK,NOISE,ENTER,LIGHT,TOUCH` and
  `WAKECHANCE=n` per event: a fight here raises it, one next door less;
  an entrant sneaking or hidden takes off their Sneak or Hide; a light
  adds. `DEEP`: only an attack. On waking `GRUMBLE=`, `AWAKEFOR=n` ticks
  before it sleeps again, and `ALARM` (`core/NpcAlarm`): a shout (a NOISE
  message, for EXECMSG_PROG) that wakes the NPCs within the radius, by its
  Sentry's ALARM= if it has one (N5) or six rooms. LOOK says it sleeps
  soundly, or stirs after a near miss. Woken NPCs stay up a while.
- N5 done. Behaviour `Sentry`: it sees an intruder here or next door (a
  player's character or their follower, or `INTRUDERS=` a mask); a hidden
  or sneaking one at `SPOT=n`% a tick less their Hide or Sneak, half next
  door. Then, or when attacked, the alarm (`core/NpcAlarm`): `ALARM="RADIUS=
  MASK= MSG="`, a shout EXECMSG_PROG can catch; the NPCs within the radius
  that fit the mask wake and get the `Alerted` effect for `ALERTTICKS=n`:
  they head for the alarm room or the intruder and attack them or their
  party, with Schedule, Patroller and Mobile held off. `ALARMITEM=` is a
  room item anyone may PUSH or PULL to raise it. Once per ALERTTICKS.
  Sleeper's ALARM uses the same alarm.
- N6 done. Behaviour `Memory`: per character MET (FIRSTMET, LASTMET),
  ATTACKED (once a fight), HURT, HEALED, GAVE (value), STOLE (a thief's
  act on it), KILLEDKIN (`KIN=` a mask), TALKED, and `TRACK=NAME:regex`
  (TRACK1-9) on what it sees them do. Each is a script variable
  `MEM_<FACT>_<Name>` of the NPC (set again from the save on its first
  tick after a restore), kept in its run state. `FORGET=30d|12h` fades a
  fact that long after it last grew (game time). `HOSTILEIF=`,
  `GREETIF=`+`GREET=`, `REFUSEIF=`+`REFUSE=` (no LIST/BUY/SELL/VALUE/
  teach); conditions FACT>N etc joined by & or |. `SHARE=AREA` or a mask:
  the area's other Memory NPCs learn it too.
- N7 done. `GenBehavior`: CREATE BEHAVIOR <ID> in builder mode makes a
  behaviour defined as data and opens its editor (`core/GenBehaviors`):
  NAME, HELP (AHELP <ID>), CANIMPROVE, PARMS with defaults, SCRIPT (MOBPROG,
  <SCRIPT> JavaScript, LOAD=), ACCOUNT, VARSCOPE. The script reads
  `PARM(name)` (also `$%PARM()%`, IF PARM(), `parm('name')` in JS); ARG()
  reads them too. `TollKeeper price=5` goes wherever a behaviour ID goes;
  each copy has its own engine and values, all share the definition, so
  MODIFY BEHAVIOR (or `MODIFY BEHAVIOR <ID> <FIELD> <value>`) changes every
  one. WHEN=/TOD=/HOURS= gate it (N1). DESTROY BEHAVIOR. The database keeps
  them with the generic commands (base class GenBehavior); the world files
  in custom/behaviors/, the script beside each.
- N8 done. When an area wakes (PASSIVE to ACTIVE: StdArea.okMessage on a
  player's move, or setAreaState; StdArea keeps when it went passive),
  `core/AreaCatchUp` tells the area, its rooms, mobs and loose items:
  `Behavior.catchUp(host, ticks)` and `Ability.catchUp` (default methods,
  nothing), the wanderers (MobileBehavior) first; a ScriptingEngine
  behaviour's or a script's `FUNCTION_PROG CATCHUP` ($g the ticks); the JS
  hook `catchUp(host, ticks)` (JSBehavior, Prop_JScript). Mobile: up to 10
  steps by ways it may take, quietly; Patroller: the route steps it missed
  while it did not tick (it ticks when passive, so mostly none); Schedule:
  in place at once; Sleeper: asleep or up by the hour; Prop_ClosedDayNight
  with HOME=: home when closed, start room when open. ResetWhole and
  respawns keep time when passive, so need none. Tag NOCATCHUP, world
  option CATCHUP=OFF. AHELP CATCHUP.
- N9 done. `TALK [npc] [ABOUT topic]` (Commands/Talk): `TALK_PROG
  [words|P phrase|ALL]` runs at once on the NPC (`ScriptEvents.talk`), $n
  the speaker, $g and $0 the topic (not $t: that is a thing). With none
  that fits, the speaker says the topic or "Hello" to it (SAYTO, for MudChat
  and SPEECH_PROG; no stop, as SPEECH_PROG matches whole words), and with
  no topic its services are a menu (N2's, a choice running a command):
  trade or banking (LIST), lessons (QUALIFY), hire, quests (QUESTS
  OFFERED), goodbye. The NPC is the speaker's partner (`core/Conversations`):
  a bare SAY or ASK from a player's character goes to it while both are in
  the room. A bare TALK means the partner, or the only NPC here outside the
  party; TALK BYE ends it. ST21's record and LASTTOPIC are kept. The
  dialogue-tree step (N14) is left out, as N14 is.
- N10 done. `DIALOGUETIME=INSTANT|TICKS` (coffeemud.ini, world.ini;
  `TIMEMODE DIALOGUE INSTANT|TICKS|DEFAULT`), INSTANT by default in
  turn-based time (`core/DialogueTime`). Then after each command of a
  player's character (queued or free, StdMOB) the replies it drew in its
  room run before the next prompt, without moving the clock: what scripts
  queued for SPEECH_PROG, GIVE_PROG, BRIBE_PROG, SOCIAL_PROG, TALK_PROG
  (`ScriptingEngine.runRepliesNow`) and MudChat's queue
  (`Behavior.repliesNow`), up to three rounds of replies to replies.
  MudChat takes `DELAY=n` ticks (2). A WAIT of several ticks stops when an
  NPC speaks to the one waiting (`DialogueTime.heard`, from StdMOB). The
  LLM part waits for N25.
- N11 done. MudChat: `?<condition>` before an entry (a script IF
  condition, $t and $n the speaker, $i the NPC, parsed once); a reply
  `&<MOBPROG line>` run in its turn with the NPC as host and the speaker as
  $n; `$@group` switches per speaker (kept while others speak),
  `$@!group` until changed, in the NPC's saved variable
  MUDCHAT_GROUP_<SPEAKER>, read back by a new copy of the NPC; `$@-` back
  to the base group. lintChat knows `?`. An emptied group list is filled
  with the base group again.
- N12 done. `Prop_NPCLines` (mobs, areas): `KEY="text"|"text"[mask]` or
  `"stock line"="text"`; postSay (CommonMsgs) asks `core/NpcLines.reword`
  first, so every stock line said through it (CoffeeShops, bankers,
  postmen, Hireling...) can be reworded without touching those classes.
  The stock line, @x values and all, is matched whole (after L(), so in
  English); @x1... and $n go into the new words; masked entries (on the
  one spoken to) first; an empty text says nothing; the NPC's own, then
  its area's. 26 short keys (NOTBUYING, STOLEN, OFFER, HIRED, TIMEUP...).
- N15 done. `Prop_Affinity` (mobs, areas; `core/NpcAffinity`): START,
  DECAY a game day toward 0, GIFT(item)/TALK(word, once a day)/QUESTWIN
  (quest, counted lazily from QuestRecords.wins)/ATTACKED (once an hour)/
  STOLE/KILLED(mob) rules, BRIBE points a gold; bands HOSTILE <-50, COLD
  <0, NEUTRAL, FRIENDLY >=25, DEVOTED >=75; SHOPFACTOR=band:x (CoffeeShops'
  prices, divided when the shop buys) and REFUSE=band with REFUSEMSG (no
  trade, said through postSay). Kept in the global script variables
  AFFINITY_<CHAR> of the NPC or the area (written with SC11's "=" so a
  negative value is not taken as a subtraction). `AFFINITY(npc who
  [BAND])`, `MPAFFINITY npc who +n|-n|=n`, Prop_NPCLines' `[AFFINITY >=25]`
  mask, TALK's topic. The zapper mask +AFFINITY is not added: a mask has
  no NPC to ask.
- N16 done. Patroller steps `WAIT=n` (ticks, counted on each tick
  before it acts), `DO=command` (forced), `EMOTE=text`, `IF=mask` on the
  host (else to the next RESTART or the start); none of them walked back.
  `ROUTE=NAME:first;...` named routes: the time of day's (DAWN/DAY/DUSK/
  NIGHT of the host's clock), else DEFAULT/ALL, else the first, restarted
  when it changes. `RESUME=true`: after a fight it tracks back to the room
  it was in before, one step a tick. N8's catch-up skips the new steps.
- N17 done. Aggressive (and so VeryAggressive and MobileAggressive's
  inner one) takes `WARN="line|line"`, `WARNTICKS=n` (the number of lines),
  `BACKOFF=true`, `NEAR=1` (`core/AggroWarning`): the first time it would
  attack someone it warns instead, then a line each tick (counted every
  tick, as Aggressive only looks again when the room's count changes),
  then the attack if they are still there. Backing off (sitting, asleep,
  nothing at the wield place) holds it; ending it warns afresh; leaving
  forgets them. NEAR: "From the east: ..." next door. WimpyAggressive,
  which attacks only sleepers, has none.
- N20 done. Behaviour `Escortee`: DEST=, MODE=FOLLOW|LEAD (LEAD: stops
  following its leader and tracks to DEST a step a tick), WAITFOR=LEADER
  with WAITCALL=n and WAITMSG=, PANIC=FLEE|HIDE|COWER (after the attack
  lands, by a trailing message of its own), AVOID=room ids (its own ENTER
  refused), TALK=/TALK1-9= room:line, ONARRIVE=/ONDEATH=/ONTIMEOUT= with
  TIMEOUT=n: MOBPROG lines with the leader as $n. The leader is whom it
  follows when the escort begins; leader, arrival, ticks and remarks in
  its run state. AVOID takes room IDs, not a mask.
- N21 done. Hireling terms as KEY=value after price;minutes (read out
  before the old parse; a ; or space before each): KEYWORDS=, MSG_OFFER/
  MSG_HIRED/MSG_REFUSE/MSG_EXPIRE/MSG_NOORDER, CONTRACT=MINUTES|DAYS|
  "QUEST:name"|UNTILFIRED (QUEST ends on the employer's WON, FAILED,
  ABANDONED or EXPIRED, QuestRecords.status), UPKEEP= a game day (two
  unpaid days and it leaves), FLEEHP= unless NpcAffinity >= LOYAL= (25),
  REFUSE= order verbs (its ORDER message refused). Wages kept in the run
  state (PAIDAGO, UNPAID). HIRE <mob> opens its terms as an N2 menu whose
  choice runs HIRE <mob> PAY (the price taken and given as coins).
- N22 done. Sounder's bark table (core/Barks reads it; Sounder hears the
  events): EVENT[(arg)]: [CHANCE=] [COOLDOWN=] [ONCEPERPLAYER] lines with
  weights, {mask}s and SAY/EMOTE/SOUND, inline among the old codes or one to a
  line in FILE=. Events from messages (GREET, GREET_FIRST, SEE_MOB, SEE_ITEM,
  KILL, ALLY_DIES, FLEE, FIGHT, SHOP_LIST, SHOP_BUY, BRIBED, WIELD and the
  other item events) and from the tick (time of day and weather as they
  change, HURT<n once until healed, IDLE). Lines drawn with the seeded dice.
  Whom it greeted and its once-per-character barks are kept in the run state.
- N24 done. core/NpcTopics: [[topic]] and [[shown|topic]] in an NPC's SAY
  (Say, for a speaker with no playerStats) and postSay become ^H...^? and
  teach the topic to the player characters who hear it, kept in their
  global variable KNOWN_TOPICS (written with "="). ASK npc ABOUT and TALK
  npc ABOUT with no topic list the known topics the NPC answers to: its
  SPEECH_PROG and TALK_PROG words (ScriptingEngine.triggerLines, not ALL)
  and MudChat entries found by the topic's own words (ChattyBehavior.
  answersTopic, so a catch-all entry does not count); a plain TALK lists
  them when there are any. HINTTOPICS (ini or world.ini) or the NPC tag
  HINTTOPICS: the first TALK with it tells and teaches its topics (scripts'
  words, MudChat's own file's entries), once (TOPICS_HINTED). Journal
  entries wait for PR7.
- N28 done. Behaviors/Scene on a room or NPC: statements (; or FILE=
  lines): ACTOR name=mask|name-words (SELF on an NPC), "name: command" (SAY
  and EMOTE take the rest whole), ECHO, PAUSE n, DO MOBPROG ($n the
  character), IF= script condition, WHEN=/TOD=/HOURS= (N1), ONCE (default)
  or REPEAT=n game days, DELAY=, INTERRUPTIBLE, NAME=. Starts on a tick when
  a player character it is due to is there and its cast is ready; a step a
  tick. Heard to the end: SCENE_<NAME> = game day on each player there.
  Stops (not heard) when the character or an actor goes or fights; spoken
  to (INTERRUPTIBLE), actors turn to the speaker, held for them until they
  leave. Position kept in the run state; DEBUG shows it and the parameter
  problems. Rumours wait for N23 (later), journal notes for PR7.
- N29 done. CHECK(who STAT|skill difficulty [modifier]) (DSE function
  182): a d20 on the shared dice, plus (stat-10)/2 (a base stat by name,
  short name or first letters) or proficiency/10, plus the modifier (a
  number or sum), against the difficulty. CHECKMARGIN set on the character
  with "=". CHECKROLLS (coffeemud.ini or world.ini): OFF, BRIEF ("[Charisma
  check: success]", the default) or FULL (the roll and bonuses). CHECK= on
  dialogue choices waits for N14 (later).

#### 2g. Combat and encounters (24)

C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11, C12, C13, C14, C15, C16, C17,
C18, C19, C20, C21, C22, C23, C25

- C14 done. FORMULA_NPCARMOR, FORMULA_NPCATTACK, FORMULA_NPCDAMAGE,
  FORMULA_NPCSPEED, FORMULA_NPCREJUV (game minutes) and FORMULA_NPCMONEY in
  coffeemud.ini (FORMULA_ keys are world settings too): @x1 level, @x2
  LASTPLAYERLEVEL, @x3 ABILITY, @x4 the attack bonus a level (NPCATTACK),
  @x5 and @x6 the 1d(level) and 1d10 rolls (NPCMONEY). CoffeeLevels'
  getLevelAttack, getLevelMOBArmor (a half rounds down, as 102-round(x)
  did), getLevelMOBDamage, getLevelMOBSpeed and fillOutMOB work them out,
  compiled once while the text is unchanged; a formula that does not
  compile is logged once and gives 0. The defaults give the old numbers
  (the test compares levels 1-200), so RESET MOBCOMBATSTATS, Prop_Doppleganger
  and the summons follow an edit.
- C13 done. core/GameDifficulty: DIFFICULTY=NORMAL (ini, world.ini) for a
  new game; presets STORY, EASY, NORMAL, HARD, or DIFFICULTY_<NAME>=
  "NPCDAMAGE=n NPCHP=n PLAYERDAMAGE=n PLAYERXP=n D=x" (world prefix
  DIFFICULTY_). The DIFFICULTY command (not in a fight) changes it, kept in
  the save's header and restored with the turns. Damage between the
  player's side (characters and their followers) and NPCs is scaled in the
  target's okMessage after checkDamageSaves, so the damage words follow;
  NPC maximum hit points in recoverMaxState (all NPCs worked out again,
  as hurt as they were, on a change); XP gains in postExperience after
  JS23's xpGain. CMath: @xP (thread-local, set by MUDFight.sideMath for
  the 19 attack, armor, damage and crit formulas, else 0) and @xD (D).
  The smoke test saves on Hard and finds it restored.
- C15 done. core/MobRanks reads MOBRANK_<NAME> (world prefix MOBRANK_):
  HP+n% DAMAGE+n% ATTACK+n ARMOR-n SPEED+n XP*x POWER=x TAG= RESIST="..."
  (saving throws); defaults MINION, NORMAL, ELITE, BOSS. Prop_MobRank
  RANK=name (or the bare name) applies them in affectPhyStats, CharStats
  and CharState each recover, so an edit retunes every mob of the rank;
  the kill's XP through MobRanks.xpFor in postExperience (before JS23 and
  the difficulty); LOOK/EXAMINE a trailer line with the tag, CONSIDER a
  line too. DSE RANK (183) as a function and, through extraTest, a
  comparison; masks -RANK/+RANK (MUDZapper description, compile, check,
  records), zappermasks.txt. POWER (MobRanks.powerOf) is for C9 and C25.
- C1 done. core/SupportPicker: SUPPORT=SELF|LEADER|GROUP|MASK:zapper (and
  Healer's TARGET=), allies most hurt first, isHeal (FLAG_HEALINGMAGIC),
  CUREFIRST by effect kind (POISON, DISEASE, or a word of the ID) and the
  ability's ID or name, pick(): cures, heals under HEALAT, buffs lacked.
  CombatAbilities (and its presets): options read in startBehavior; useSkill
  picks support first, the random loop's beneficial target is helpedBy()
  (the leader as before, nobody for SELF, the neediest ally for GROUP and
  MASK), and a heal waits until its target is under HEALAT; HEALAT,
  CUREFIRST and BUFFOUTOFCOMBAT turn on the out-of-fight precast every 3
  ticks. Healer: SPELLS=, HEALAT=, TARGET=, the old random way without
  them. NiceAbilities: HEALAT= before the ;.
- C10 done. Behaviors/TargetPriority: PREFER= (HEALER and CASTER by
  counting abilities, LOWESTHP by percent, LOWESTARMOR, LEADER of a group,
  LASTHITME from its damage messages, STRONGEST by getPowerLevel, MASK:
  last), SWITCH= percent a tick, STICKY= ticks, TAUNTABLE first takes
  Threat.clearLead (C18). Foes: Threat.enemies it can see.
- C18 done. core/Threat: Threat.Holder (CombatAbilities implements it: its
  aggro map, adjustAggro) or a light table per NPC (WeakHashMap), fed by
  MUDFight.handleBeingDamaged (twice the damage) and handleBeingHealed
  (three times a heal of the one an NPC here fights or their group), the
  entries of those gone dropped when it grows. DSE THREAT (184), TOPTHREAT
  (185), WEAKEST (186, lowest HP% among those fighting it or its group, or
  whom it fights), OPPONENT (187, a mask), each a name for MPKILL; THREAT
  compares through extraTest. MPTHREAT (122) +n, -n, =n, *x, through the
  holder so a CombatAbilities mob retargets on a clear lead.
- C22 done. CombatAbilities reads +SKILL(EVERY= FIRST= ONCE BELOW=% COOLDOWN=
  WEIGHT= SELF TARGET=CASTER) and OPENER= (readRotation; the old word loop
  skips the brackets). useSkill counts rounds; the opener on round 1, then
  the first scheduled rule due (rotationPick), before C1's support; the
  random loop picks the +list by WEIGHT, skips scheduled skills and those
  cooling down. The round of each try (a cooldown starts with it, as a
  fizzle spends it too) and a log of tries (ONCE counts tries); both
  cleared when the fight ends.
- C11 done. Behaviors/Pursue: a FLEE message from its victim (or one who
  fights it) starts a chase at CHANCE= (MASK=); each tick after DELAY= it
  walks into the next room if the quarry is there (an open exit), attacks
  them there (postAttack), and gives up past MAXROOMS= (counted over the
  whole chase, until the fight ends) or LEASH= rooms from its start room
  or when the trail is lost (GIVEUPMSG=, then
  WanderHomeLater ONCE unless STAY). REMEMBER=n: the one who fled, coming
  back into its room within n ticks, is attacked the next tick. Its
  options are read with CMParms.parseExactParms (one word or one quoted
  string a value, bare words flags): getParmStr's forgiving search finds a
  key inside another word or a quoted message (LEADER in BREAK=LEADERDEAD),
  and parseEQParms takes a flag after a value into it (LEASH=1 STAY).
  getParmStr/Int/Bool(Map, key, default) read the map.
- C21 done. Behaviors/EncounterReset (mobs, rooms): engaged while it (or,
  ROOM=true or on a room, an NPC of the room) fights, its foes kept; then
  when none is within RADIUS= rooms (a walk through exits) for IDLE=
  ticks: home (bringMobHere), HEAL=%, malicious uninvokable effects
  undone, followers with no start room or the tag SUMMONED destroyed
  (DESPAWNADDS=), Threat forgotten, each other behaviour told
  (setStat ENCOUNTERRESET, for C3), and RESET_PROG (101) through
  ScriptEvents.encounterReset.
- C2 done. Behaviors/Morale: breaking() from FLEEAT=, OUTNUMBERED= (Threat
  enemies against its side: group, leader's group, same-race NPCs here
  who fight),
  BREAK=LEADERDEAD (a death message from its leader: LEADER= mask or whom
  it follows, remembered each tick, since a dying leader's followers are
  let go before they hear of it), n%DEAD and HP<n% (against the side at the fight's start);
  BRAVE= mask holds. At CHANCE= a tick: MSG=, then ACTION=FLEE (the FLEE
  command; RALLY= room ID or HOME: autoTrack there, REGROUP= ticks, the
  free NPCs there follow it back, and it attacks one it fled from when it
  is back in the fight's room), SURRENDER or BERSERK (a Prop_Adjuster, damage +30%
  of base, attack -10%, removed when the fight ends). SURRENDERAT=: drops
  the weapon, peace both ways, SURRENDERMSG=, SURRENDER_PROG (102).
- C17 done. Prop_Essential: MODE=YIELD (under AT=%, after the damage)
  or KNOCKOUT (okMessage cuts a killing blow to leave 1 hit point; asleep
  until TICKS run out); then peace both ways, MSG=, the kill's experience
  through dispenseExperience unless NOXP (the plan's NOXPPENALTY made
  plain: whether beating it pays), malicious messages at it refused with
  REFUSEMSG= for TICKS=, and YIELD_PROG (103) or DEFEAT_PROG (104).
  Options by exact key (CMParms.parseExactParms), so AT= is not found in
  a message.
- C3 done. Behaviors/BossPhases: statements (; and , outside quotes) of
  ONENGAGE, AT=n%, AT=ROUND n, AT=ADDSDEAD, ONDEATH with SAY/EMOTE/YELL,
  CAST, SUMMON (ScriptLib.catalogCopies, or a mob class; tagged SUMMONED,
  following, attacking), BEHAVE/UNBEHAVE, AFFECT/REMOVE, IMMUNE (damage to
  0 in okMessage), HEAL, MOVETO, ROOM EMOTE/AFFECT, CALL (FUNCTION_PROG of
  its scripts). Each fires once; AT= ones advance the phase and run
  PHASE_PROG (105, ScriptEvents.phase); PHASE() (188). RESET=FULLHEAL,
  LEAVECOMBAT, LEASH (C21's setStat ENCOUNTERRESET), NEVER; death rearms;
  run state PHASE, ROUND, FIRED.
- C5 done. MPALARM NAME=name ticks command: its body's first line is
  FUNCTION_PROG ALARM_NAMED_<NAME> (Telegraph.NAMED_ALARM), so S7's saved
  queue keeps the name; one of a name replaces it; dequeResponses drops a
  named alarm of a mob that cannot act (Telegraph.canAct: dead, asleep,
  held, bound). MPALARMCANCEL name (all the host's engines, through
  ScriptEvents.engines, now public), ALARM(who name) (189) the ticks left;
  ScriptingEngine.alarmTicksLeft/cancelAlarm. SCRIPT ALARMS LIST names
  them. MPTELEGRAPH [NAME=] ticks "warning" command [INTERRUPT= AVOID=
  INTERRUPTMSG=]: Prop_Telegraph on the mob (a Telegraph.Effect: warning
  each tick, LOOK trailer, interrupts on can't-act, DAMAGE>n% taken, MOVE;
  AVOID=HIDE, COMMAND:word through the HUH message) and a named alarm
  whose body is MPTELEGRAPH FIRE name command: it runs the command once a
  foe not avoiding (Telegraph.foesHere), $n each. GenAbility WINDUP (37)
  and WINDUPMSG (38), at the end of MODIFY's list: a monster's invoke puts
  on Prop_Telegraph ABILITY= TARGET=, which uses it (Telegraph.release, so
  it does not wind up again) when the ticks run out.
- C4 done. core/SpawnRules: RandomMonsters and RandomItems read CATALOG=
  "name=weight,..." (entries picked by weight; catalogCopy counts and caps),
  WHEN= (a zapper mask on the room: RandomMonsters on a room spawns nothing
  while it fails; on an area, pickRoom finds a room it passes), DESPAWN=
  WHENFALSE/DAWN/TICKS=n (checked every tick, before canAct; the spawn goes
  only unseen: no PC in its room, not fighting), NEAR=PARTY:n (rooms
  within n open exits of the player's side in the area), MAXAREA=n
  (spawns tagged SPAWNED in the area) and, for mobs, LEVEL=PARTY[+n|-n|+-n]
  (the side's highest level, fillOutMOB, after bringToLife, which makes a
  catalog copy over from the catalog). CATALOG= alone needs no file.
- C9 done. core/Encounters: tables in world/encounters/NAME.enc, one
  entry a line: MOBS/ITEM/EMOTE/TRAP/MERCHANT/FUNC, what (name*dice lists
  for mobs and items, from the catalog or a class), WEIGHT=, LEADER=,
  MASK= (on the one who came in), LEVEL=lo-hi (the party's highest),
  ONCE and COOLDOWN=n (kept in the game's variables, host ENCOUNTERS),
  FROM=HERE/HIDDEN/ADJACENT (walk in from a neighbour), AMBUSH (each
  postAttacks first). Tagged ENCOUNTER. BUDGET: more MOBS entries until
  MobRanks.powerOf of what came reaches pct of the party's. Behaviors/
  RandomEncounters (areas, rooms): TABLE=, CHANCE=, COOLDOWN=n rooms or
  nDAYS, LOCALES=, TRIGGER=, MAXACTIVE=, DESPAWN= ticks with no party
  near (not fighting), BUDGET=; an ENTER by the party's leader queues a
  roll for its next tick (trailer runnables run off the game thread).
  ENCOUNTER LIST/SHOW/ADD/DEL/ROLL/CHECK/RESET (Commands/EncounterCmd).
  MPMLOAD ENCOUNTER table ($b the leader), quest LOAD ENCOUNTER table
  (its mobs the quest's), and catalog:name wherever CATALOG name goes.
- C20 done. Behaviors/WaveSpawner (rooms): WAVEn= "name*dice+..." (catalog:
  optional; Encounters.make) or encounter:table (Encounters.roll); NEXT=
  CLEAR or TICKS=n; START=ENTER (the next tick after a party ENTER) or CALL
  (MPWAVE START, 125: setStat WAVECOMMAND START/NEXT/STOP/RESET); LOCKEXITS
  refuses the party's LEAVE and FLEE while it runs; win: REWARD= to the
  winner, ONWIN= FUNCTION_PROG of the room, ARENAWIN_PROG (107), back to
  IDLE after RESETAFTER=; no party left in the room: the wave leaves,
  ARENALOSE_PROG (108); ONLOSE=SPARRING(:room) adds Prop_SparringRoom while
  it runs. WAVE_PROG n (106) on the room's and its NPCs' scripts. Run
  state STATE/WAVE/T/R; restored, the wave is the room's mobs tagged WAVE.
- C23 done. Prop_Ambush (rooms): a party leader's ENTER (CHANCE=, MASK=)
  queues it; it ticks itself (TICKID_PROPERTY_SPECIAL) while it has work.
  MOBS= through Encounters.spawnSpec, tagged AMBUSH. DETECT=parts:dc: the
  party's best d20 + stat (n-10)/2 + skill proficiency/10. Not spotted:
  MSG=, each ambusher postAttacks, Prop_Surprised TICKS=SURPRISE on the
  party (no move, flee, strike, malicious act or cast). Spotted: SPOTMSG=
  to the party, a WAIT= standoff: a fight ends it, the party leaving
  despawns them (AVOIDED), else they strike. ONCE or RESET=n. AMBUSH_PROG
  (109) with the outcome, on the room's and its NPCs' scripts.
- C19 done. Prop_RejuvWhen (mobs, items): WHEN= mask, ELSE=WAIT/NEVER/
  REPLACE:name. core/RejuvRules.verdict, asked by StdMOB's dead tick before
  the rebirth (the mob checked as if in its start room) and by ItemRejuv
  before it copies an item back (a factory mob in the room): WAIT resets
  the rejuv count, NEVER destroys the mob or drops the item's entry,
  REPLACE brings Encounters.make(name) there for good (a mob as resident).
- C6 done. Prop_PartyScaler (mobs; rooms and areas hand a copy, HANDED=true,
  to an NPC in a malicious exchange with the player's side): on its first
  tick in combat it counts the party (its foes and their groups in the
  room), then affectCharState scales max HP and affectPhyStats damage,
  attack and LEVEL=AVG|MAX+n (MIN=, MAX=) by members-BASE; ADDS= name/
  MEMBER-ABOVE-n through Encounters.spawnSpec, tagged SUMMONED, following.
  Two ticks out of combat undo it (a handed copy goes). core/PartyScale
  for CONSIDER: "<mob> scales to your party" (Prop_Doppleganger too).
- C16 done. Prop_DeathRules (rooms, areas): core/DeathRules.playerDeath,
  mobDeath and itemLootPolicy read each key from the room's, else its
  area's (each if its MASK= fits the one who dies), else coffeemud.ini's; MUDFight's death and
  knock-out code and CoffeeUtensils' loot policy ask them. TO=room: its
  okMessage refuses a player's side TYP_DEATH (as Prop_SparringRoom does),
  stops the fight, moves the gear to GEAR=CONTAINER:room/container, and
  bringMobHere(..., true) takes the character and its followers to TO,
  with HEAL=% of their hit points; MSG= shows.
- C7 done. core/Checkpoints: a character's respawn point is a tattoo,
  CHECKPOINT:room id (saved with them, kept through death, apart from the
  recall room). Prop_Checkpoint (rooms): ON=ENTER (executeMsg), REST (SIT
  or SLEEP there) or COMMAND:word (the HUH message, consumed) sets it for
  the player's side in the room, HEAL=% (hit points, mana, moves),
  RESPAWNAREA= (threads().rejuv of the area's rooms, mobs), SAVE=AUTO or a
  slot (SaveGame.autoSave). DEATH=CHECKPOINT (getDefaultDeathRoom): the
  checkpoint, else the start room. MPSETRESPAWN (126) who room|HERE|CLEAR
  and RESPAWNROOM(who) (190). Fixed on the way: CMUniqNameSortSVec did not
  override compareToLowerStarts, so findStartsWith (findTattooStartsWith)
  compared class ids and never found a tattoo by the start of its name.
- C8 done. PLAYERDEATH's DOWNED x y (global, or a room's or area's
  Prop_DeathRules): postDeath asks core/Downed first, and one of the
  player's side falls instead of dying: Prop_Downed TICKS=x HEAL=y (sitting,
  acts not, is not attacked; the foes that downed them stop). REVIVE
  (Commands/Revive) by an ally, or a heal of y or more, brings them back;
  when the foes that downed them are gone or dead and no one of the party
  there fights, they get up at 1; when x runs out, Downed.die posts the
  death again, past DOWNED, and the rest of the list applies. When all of
  the party in the room is down or dead (one at least down; checked on a
  downing and in handleDeath): "Your party has fallen", PARTYDEFEAT_PROG
  (110) on the room's and area's scripts that have one, else coffeemud.ini
  PARTYDEFEAT, the first that applies: RESTORE (the last save, offered),
  CHECKPOINT (the leader's, all together, PARTYDEFEATGOLD % of the gold),
  WAIT, DEATH (the default and the last word).
- C12 done. Prop_AddDamage: TARGETMASK= (the extra damage, and its
  reflected kind, only on victims it fits) and LEECH=n% (the wielder heals
  that share of the hit plus the extra), read by exact key and taken out
  of the words before the old parse, so the text keeps them.
  SOURCEMASK= on Prop_AbsorbDamage (its okMessage lets an attacker it does
  not fit through) and Prop_HaveResister (so Prop_WearResister too: its
  resistances only against attackers it fits; MASK= is still whom it
  protects).
- C25 done. CONSIDER, or CONSIDER ALL (CONSIDER ALL.name as before):
  core/FightOdds.report: the party (the asker's group here) against the
  foes here (those fighting the party, or with an Aggressive behaviour;
  else every NPC not on the player's side), by CoffeeLevels'
  getPowerLevel, the foes' times their rank's POWER (C15): trivial, an
  easy, fair, hard or very hard fight, or deadly; names counted
  ("four guards"), rank tags kept, and "They scale to your party." when
  core/PartyScale says so.

#### 2h. Items, properties and puzzles (23)

I2, I3, I4, I5, I6, I7, I8, I9, I10, I11, I13, I14, I15, I16, I17, I20, I21,
I22, I23, I24, I25, I26, I27

- I2 done. core/Doors changes a door as a character would: the
  messages go through roomAffectFully (both sides; the far side's room
  sees the message too), so the exit's relock timer starts; a locked door
  is unlocked first, an open one closed before it is locked; a container
  in the room by its name works too. The door's name is put in the
  message before it is sent (<T-NAME> on an exit came out as "you" to a
  character in the room). `MPDOOR OPEN|CLOSE|LOCK|UNLOCK|TOGGLE
  room dir|container [QUIET|"message"]` (127; HERE, a room ID, or a thing
  whose room it is) and `DOORSTATE(room dir|container)` (191: open,
  closed, locked or nothing). JavaScript's door() (JS17) goes through it,
  quietly. MPOPEN and its kin are left one-sided, and their help says so.
- I22 done. core/Transform puts a new item in an old one's place: the
  same owner (a mob, or a room), and by KEEP (CONTAINER,WORN by default;
  ALL, NONE) the same container and worn place, its ObjectVars (STATE),
  tags and uses; what was in it moves into the new one, or falls out
  where it was. `MPTRANSFORM item name [KEEP=...]` (128): the catalog's
  item, an item class, or an item the script can find by that name,
  copied; `$p` is the new item for the rest of the script. JavaScript's
  `transform(item, name, keep)` (an item, or how a script names one).
  I5's Prop_Transform and I27's ItemUpgrader use it.
- I9 done. Prop_Switch (items, exits) implements core.interfaces.
  VerbTaker, which ScriptEvents.verb now asks of each effect on what is
  within reach, after VERB_PROG and Prop_Verbs and before the game's
  commands (so PULL, PUSH and TURN are its own). VERBS, STATES (words or
  n-m), START, DESC_x (a trailer to LOOK), MSG_x, ACTION_x, RESET, REQUIRES
  or MASK with REQMSG, ONCE; "verb thing to x", ACTIVATE and DEACTIVATE.
  The state is in its text (STATE=, MOVED=). core/Actions runs actions as
  a FUNCTION_PROG on the thing (an exit's on the room), MP put back where
  it was left off. SWITCH_PROG (111) on the thing's and its room's
  scripts; SWITCHSTATE(thing|room:dir|dir) (192).
- I16 done. Prop_ComboLock (exits, containers), a VerbTaker: TURN DIAL n
  TO x (VERB=), SET DIALS a b c, ENTER code (left to the game when it is
  not a code); ON thing. CODE (dashes, spaces or one character a symbol),
  SYMBOLS (words or n-m), DIALS, SHOW (LOOK), TRIES and ONWRONG, ONOPEN,
  RELOCK, MSG. It opens with core/Doors (Doors.change now takes the thing
  as well as its name), on both sides of a door. The dials are kept in
  its text (READ=); CODE=RANDOM rolls DIALS symbols when there is no GOT=
  (a new game: the world file has none), kept in its text, and VAR= puts
  it in the global script variable COMBO name. It keeps its own dials;
  I9's switch state was not needed for it.
- I15 done. Prop_LimitedContents was extended rather than a new
  property added: LOCKIN (and LOCKMSG) refuses getting from it, CONSUME
  destroys what is put in after counting it (TALLY=, kept in the text),
  ONPUT, ONFULL (each limit over 0 met, counting the tally; fired on the
  change to full) and ONEMPTY (the last thing taken out) run actions as
  Prop_Switch's do. The text splits at semicolons outside quotes, so
  actions with semicolons fit. Prop_ItemSlot was left as it is. Fixed on
  the way: a -TAGS or +TAGS mask on an item looked at a stand-in mob's
  tags, never the item's, so it never matched an item (MUDZapper).
- I25 done. Prop_PressurePlate on a room (its mobs with what they
  carry, and its items) or a fixture (contents and riders), core/Weights.
  WEIGHT= and COUNT= tests (>=, >, <=, <, =, or at least), MASK=, ON/OFF
  actions, MSGON/MSGOFF, DELAY before it comes up. It weighs the change a
  message makes as it is made (enter, leave, put, get from it, sit,
  stand), and a drop or get on a room's floor on its next tick. ROOMWEIGHT
  (193) and CONTENTWEIGHT (194). Riding is counted on a fixture that is
  Rideable.
- I10 done. Commands/Use (USE x [ON|WITH|IN y]) and Commands/Combine
  (COMBINE a WITH|AND b; a second thing of the same name is found).
  USE_PROG and COMBINE_PROG (112, 113) on either thing first (eventRun:
  the first script that fits takes it); then core/Combinations, the rows
  of world/combinations.txt (A | B | RESULT | keys; read again when the
  file changes): A and B a name, tag:X or catalog:X, either order;
  VERB=, CONSUME=A,B|BOTH, MASK=, SKILL=, FAILMSG=, MSG=, ACTIONS=; else
  "Nothing happens." No new message type: the scripts and rows show what
  they will.
- I11 done. Prop_Discoverable (items, exits, mobs): IS_NOT_SEEN until
  found, and a hidden exit refuses those who go through it. FOUNDBY
  examine:x, look:x (seen in executeMsg, as rooms pass messages to their
  items and exits), search: a VerbTaker for SEARCH, the room's first
  hidden thing searching for all; a character with Searching or
  Thief_Search who finds nothing is handed to the skill. CHECK "STAT n"
  or STAT/DC: d20 + Prop_Ambush.bonus (the stat, Searching, Thief_Search)
  against the number. MSG, HINT (a trailer to a LOOK at a FOUNDBY thing),
  STAYS and RESET. FOUND=true is kept in the text.
- I13 done. Prop_BlockedExit (exits), a VerbTaker for CLEAR, DIG,
  SMASH, HIT, ATTACK and BURN with the direction or its NAME: it refuses
  ENTER and LEAVE through the exit while it stands, adds a line to LOOK
  at the exit, takes 1d6 + Prop_Ambush.bonus(SKILL) a try (TOOL held or
  wielded), FIRE and a lit Light for BURN; MSG, CLEARMSG (both rooms),
  ONCLEAR actions, RESPAWN. LEFT= in the text; a Prop_BlockedExit on the
  paired exit is set the same. Attacking it with the combat system was
  not done: ATTACK is one of its words instead.
- I14 done. Prop_Detector (items) ticks on its own (TICKID_PROPERTY_
  SPECIAL, from setAffectedOne) every EVERY ticks, looks through the
  rooms out to RANGE (0-3) for a mob fitting DETECT or an item fitting
  DETECTITEM, when WHERE (WORN, HELD, ANY) holds; while it finds one it
  adds AMBIANCE, and on the change it tells the holder (or the room) MSG
  or STOPMSG, $p the item.
- I17 done. Behaviors/Riddler (mobs, items): RIDDLE/ANSWERS and RIDDLE1-9
  with ANSWERS1-9, RANDOM, FUZZY (an edit distance), ASK=GREET (an enter
  of its room), LOOK, COMMAND (RIDDLE); the answer said (TYP_SPEAK, the
  quoted text) or ANSWER text. TRIES, ONRIGHT/ONWRONG/ONOUT actions,
  RIGHTMSG/WRONGMSG; ONCE=CHARACTER (a tattoo RIDDLED:name), SAVE
  (SOLVED=true in its parameters) or NONE. ScriptEvents.verb now asks the
  behaviours of what is within reach too, when they are VerbTakers.
- I20 done. GenTrap: TRIGGERS (22; message type names, LEAVE:dir and
  the like on a room, any of them by anyone in the room), DISARM (23;
  verb:phrase, GenTrap is a VerbTaker) and ONCE (24; the effect is taken
  off after it springs), in its per-ID values and stats, and prompted by
  MODIFY ABILITY. StdTrap.defaultTriggers() lets TRIGGERS replace the
  class's own. Its script now runs outside the ability's branch, so a
  trap with only a script works. RESET was there already (PERMRESET).
  AHELP GENTRAP is new.
- I21 done. core/Keys: StdExit's and StdContainer's key checks (and the
  container's relock check) go through Keys.fits (a key code split at
  semicolons, a code ending in * a prefix, matched without case, and the
  lock's Prop_LockRules KEYS), Keys.reachable (carried, or in a
  CONTAIN_KEYS container, however deep that is) and Keys.usable (its
  Prop_KeyRules MASK). Prop_KeyRules (items): CONSUME, BREAK, RETURN
  (moved to the room) on the UNLOCK or LOCK whose tool it is; MASK with
  MSG refuses such a message before the lock picks its key, when no other
  key carried fits and may be used. Prop_LockRules (exits, containers):
  PICK=NEVER|LEVEL:n against a thief skill (or an ability with PICK in its
  ID) aimed at it, KEYS, RELOCK (core/Doors), MSG.
- I3 done. Items/Basic/GenTome (a GenReadable, Paged and a VerbTaker):
  pages from its readable text, PAGE blocks (TITLE, LANG, LORE), or
  FILE=world/texts/x.txt (or a resources file) with "=== title [| keys]
  ===" sections. READ shows contents, READ n|NEXT|PREV|title a page (the
  place kept for each reader while the game runs), handled in its
  executeMsg (the message then carries CANCEL so the stock reading stays
  quiet); LANG needs the language ability; <VARIES> pages go through
  parseVaries; LORE gives the tattoo LORE:key (PR6's codex will read it).
  READ_PROG PAGE n|title (ScriptEvents.readMatches with the reader and
  request). TEAR PAGE n FROM book: a GenReadable page (tag PAGE:n), the
  book tagged TORN:n. ScriptEvents.verb asks the thing itself when it is a
  VerbTaker. core/JournalSeeds fills an empty GenBook or board from
  world/journals/NAME.txt (=== subject ===, AUTHOR:) the first time it is
  read in a run.
- I4 done. Prop_When (rooms, areas, exits, items, mobs) ticks on its
  own (TICKID_PROPERTY_SPECIAL from setAffectedOne). PROPS (semicolons
  outside brackets and quotes; each an ability or a behaviour, CMClass
  tried in that order) or ID/PARMS; WHEN on CHECK's subjects; EVERY;
  CHANCE; COOLDOWN (ticks, or nH from the game hour's length; COOLLEFT=
  kept in the text); ONCE=CHAR (a tattoo), PROFILE (an account tattoo),
  WORLD (SPENT=true in the text); ONSTART/ONEND actions; ELSEMSG on an
  ENTER while off. What it adds is not savable (it adds them again after
  a load). N1's WHEN= on behaviours stays as it was.
- I5 done. Prop_Transform (items, mobs) ticks on its own: AFTER or DAYS
  (game hour in ticks x hours a day; LEFT= in the text), START (GET,
  DROP, WEAR, ALWAYS; STARTED= kept), RESET, ONLYIN; or WHEN (DAWN, DAY,
  DUSK, NIGHT, FULLMOON, a season, else a mask on a stand-in in its room).
  INTO through core/Transform (KEEP as MPTRANSFORM's, DAMAGE as USES),
  MOB (Encounters.make, bringToLife, KEEP=FOLLOW the last holder), SET in
  place (NAME, DISPLAY, DESC, PROPS). REVERT with WHEN: SET puts the old
  words back (WAS); INTO or MOB put the old thing away in memory and give
  the new one a Prop_Transform BACK=true that brings it back when WHEN
  ends. What is put away is not in a save: a game saved at night keeps
  the gargoyle, which then stays a mob.
- I6 done. The threshold fix first: Prop_HaveAdjuster.setCheck gives
  each piece's set state from its own ALLSET number. Prop_ItemSet (items)
  ticks on its own: core/ItemSets counts the owner's pieces of the SET
  (worn, or carried with COUNT=HELD); the definition is the piece's own,
  another piece's with TIERn, or a line of world/itemsets.txt; the first
  counted piece carries the met tiers' properties (unsaved), the others
  none. ONMSG/OFFMSG, MASK, PIECES; LOOK (a trailer) shows the count and
  tiers. SET_PROG (114) on the piece and the wearer; SETCOUNT (195).
  Not done: ALLSET on Prop_WearSpellCast, Prop_WearEnabler,
  Prop_WearResister and Prop_AddDamage (the tiers give those now), and
  IDENTIFY's text.
- I7 done. Prop_Cursed (items), DOMAIN_CURSING in its classification,
  so Prayer_Bless.isCursed and endLowerCurses see it; its unInvoke lifts
  it only when a Remove Curse aimed at it (or its wearer) came from one
  of LEVEL or more (seen in executeMsg). HIDDEN until the first WEAR,
  HOLD or WIELD (SHOWN= kept), REVEAL a trailer then; NOREMOVE and NODROP
  as sense flags in affectPhyStats; PENALTY properties added unsaved;
  BREAK steps SAY: and ENTER: (STEP= kept) or BREAKMASK checked each tick;
  ONBREAK KEEP, CRUMBLE or BECOME: (core/Transform). Its penalties come
  off with it. IDENTIFY's "seems normal below LEVEL" was not done.
- I8 done. Prop_Invokable (items): WORD said by the holder (a speech
  message, the word whole), or VERB (a VerbTaker; INVOKE when there is no
  WORD); SPELLS (comma between, ;LEVEL=), invoked auto on SELF, the
  ENEMY (the user's victim) or nothing (ROOM); CHARGES with LEFT= kept;
  RECHARGE=DAWN (the area clock turning to dawn), DAILY, TICKS=n; COOLDOWN
  in its own ticks; REQUIRE; MASK; MSG; EMPTYMSG. accountForYourself (what
  identifying shows) tells the spells, charges, recharge and word.
- I23 done. Prop_Attunement (items) ticks on its own: HOW=REST:n
  (sitting or sleeping holder), RITUAL (INROOM and SAY, on a speech
  message), ON=EQUIP (WEAR, HOLD or WIELD); LIMIT counts the attuned
  items the character carries; BIND sleeps the powers in another's
  hands; MSG; MASK. PROPS are added unsaved when attuned (again after a
  load, from ATTUNED= in its text). Commands/Unattune (UNATTUNE item).
  accountForYourself says "Requires attunement" or to whom.
- I24 done. Prop_ItemGrowth (items): KILL (a death message whose tool is
  the holder, the item worn or wielded, KILLMASK on the slain), HIT (a
  damage message from the holder with it as the tool), USE (any message
  from the holder with it as the tool), WORNTICK (its own ticks, worn);
  XP a formula with @x1. STAGES split before each "n:"; NAME and DISPLAY
  are set on the item (so saved as its own), PROPS added unsaved and again
  from STAGE= after a load. GROWN=, STAGE=, OWNER= in the text; BIND;
  accountForYourself shows the way to the next stage.
- I26 done. core/Durability: ITEMWEAR (a percent) scales the wear of
  StdArmor (the drop in uses from its table, scaled after) and of
  MUDFight.postItemDamage (StdWeapon's 1% wear goes through it), with a
  random rounding; ITEMBREAK=BREAK (or Prop_Durability BREAK/BROKENNAME)
  breaks rather than destroys: uses 0, unworn, Prop_Broken (no armour or
  damage, refuses WEAR/WIELD/HOLD, ambiance "(broken)", ORIGINAL= name;
  gone when the uses are above 0 again, by any mending). Prop_Durability:
  MULT, UNBREAKABLE, NOREPAIR (a MEND or REPAIR ability, or a GIVE to an
  ItemMender, refused), "(worn)" below half.
- I27 done. Behaviors/ItemUpgrader (mobs, items), a VerbTaker for
  UPGRADE item [CONFIRM]: rows UPGRADE1-9 and the file UPGRADES (a line
  each), FROM matched as I10's combinations (name, tag:, catalog:), TO
  through core/Transform (KEEP; SOCKETS has no meaning here) or ADJ (dam,
  att, arm, lvl on the item's base stats), COST (beanCounter), MATERIALS
  (carried, by name or tag, xN, destroyed), LEVEL, MSG. Prop_UpgradePath
  on an item is its own row, and goes once an ADJ is done.

#### 2i. World systems (25)

Time, weather, environment, economy and law.

W1, W2, W3, W4, W5, W6, W7, W8, W9, W10, W11, W12, W13, W14, W15, W16, W17,
W18, W19, W20, W21, W22, W23, W28, W29

- W1 done. core/Laws reads and changes the law of the host's room (else
  the character's room's, else their start room's): `ISWANTED(who
  [crime])` (196; a crime by its name, or a word of it), `WARRANTS(who)`
  (197) and `FINESOWED(who)` (198). `MPPARDON who [crime|ALL]` (129) takes
  warrants away quietly; `MPFINE who +n|-n|n` (130) changes the fines owed
  (what TaxCollector collects); `MPRELEASE who` (131) ends a prisoner's
  time, so the guards walk them to the release room, and lets go of one
  being taken in; `MPSUPPRESSLAW crime|ALL n [TICKS|HOURS|DAYS|WEEKS|
  MONTHS]|OFF` (132) makes no warrants for it a while, on the game clock,
  kept in the save with the law's state. Masks `+WANTED` and `-WANTED
  [crime ...]`, by the law where the character is (a record is wanted
  nowhere), so IGNOREMASK, VARIES and guards can use them. JavaScript's
  cm.law(): wanted, finesOwed, addFine, release and suppress.
- W2 done. `resources/currencies.ini` (a world's own copy in
  world/resources/ read instead; read at the world's load): `CODE=`
  definition lines make currencies as an area's CURRENCY does, kept
  whatever areas later do, and `DEFAULT=CODE` is the currency of every
  area with none of its own, in place of the gold coins and Archon notes
  (a built-in code such as GOLD can be named). coffeemud.ini or world.ini
  can set `CURRENCY_<CODE>=` and `DEFAULTCURRENCY=` too, after the file. A
  code defined again, differently, keeps the first definition, and the log
  says so. VALUE() takes every code that resolves (GOLD, COPPER and the
  other built-in ones failed before). The stock file is comments only, so
  nothing changes until a world sets it.
- W16 done. A denomination in a currency's definition (the currency
  file's, world.ini's or an area's) may end in braces: `12 silver
  shilling(s) (ss) {MATERIAL=SILVER WEIGHT=0.01 DESC="..."}`, semicolons
  inside them kept. StdCoins takes the material (else, as before, the
  first material word of the name), the weight of one coin (else a
  hundred to the pound, paper and cloth nothing) and the description, its
  "Equal to" line after it.
- W17 done. `MONEYSTR(amount [CODE] [LONG|SHORT])` (199; the host's
  currency when no code is given), `PRICE(item [shopkeeper])` (200; what
  the shop asks $n, by the item or a name in its stock, in its coin names;
  the host, else the first shopkeeper in its room) and `DENOMNAME(CODE value
  [count])` (201). JavaScript calls them as `MONEYSTR$(...)` and the rest,
  as it calls every function. `core/Shops` holds the shop lookups W14 uses.
- W14 done. `MPSHOP shopkeeper REMOVE item [n|ALL]` (one when no count),
  `STOCK item n` (a shelf's count; an item not stocked, one the script can
  find or the catalog's, is copied onto a shelf), `PRICE item amount|-1`,
  `CLEAR`, `RESET` (the stock put back as built, as INVRESETRATE does;
  generic shopkeepers) and `BUDGET amount` (what is left of the buying
  budget) (133; the host, or the first shopkeeper in its room, when the
  word names none). `SHOPPRICE(shopkeeper item [customer])` (202; $n
  when no customer is named) and `SHOPBUDGET(shopkeeper)` (203; -1 for no
  limit). ShopKeeper's new `setBudgetLeft` and `resetStock`. JavaScript's
  cm.shop(): remove, stock, setPrice, clear, reset, budget and setBudget.
- W15 done. `BANKBALANCE(who chain [currency])` (204) and `DEBT(who
  owedTo [DUE])` (205; with DUE the game hours until it falls due, below
  0 once past due). `MPBANK who chain +n|-n [reason]` (134) pays in
  (opening the account, as a deposit would) or takes out, with a dated
  ledger line; `MPDEBT who owedTo +n|-n [INTEREST pct] [DUE n DAYS] [FOR
  reason]` (135; a minus pays off, the first due first) and `MPDEBT who
  owedTo CLEAR`. A name not in the world is taken as it is (core/Banks;
  the money library's own records, which a save keeps). JavaScript's
  cm.bank(): pay, debt, lend and forgive. Fixed on the way: a debt changed
  but not paid off was written under the money library's name, not the
  debtor's, so a banker's loan payments never lowered what was owed.
- W29 done. MoneyChanger's `RATE.<CODE>=+n|-n` (percent; `RATE.DEFAULT`)
  is a premium or discount on what coins of that currency fetch, on top of
  CUT, read before the rest (whose parser skips signs) and no fee, so the
  changer still takes every currency. `MPEXCHANGE <CODE>|DEFAULT <pct>`
  (136) sets what coins of a currency fetch at every money changer, in
  percent of their value (100 ends it), kept in the save's header;
  `EXCHANGE(CODE)` (206) reads it. `MoneyChanger.exchangeValue` works out
  what is given back, for both the check and the change.
- W12 done, with W20's MPWEATHER and FORECAST, as one set over DT20's
  WEATHER SET (`core/WeatherControl`). `MPWEATHER area|HERE weather [NEXT]
  [FOR n HOURS|DAYS] [QUIET]` (137): now, with the usual end and start
  messages and WEATHER_PROGs (QUIET: no messages, the scripts still hear
  it), or with NEXT only what comes next; FOR holds it, each change of
  weather coming back to it until then, and a save keeps the hold with the
  area's weather. For builders, `WEATHER SET weather [FOR n HOURS]`,
  `WEATHER FORECAST` (now, next, a hold, and the weighted odds of the step
  after, from the climate's own chart, `Climate.weatherChances`) and
  `WEATHER NEXT` (one step now). JavaScript's cm.weather(): hold and
  chances.
- W20 done (its MPWEATHER and FORECAST are W12's). Named climates in
  `world/climates/<name>.ini` (else resources/climates/): a weight for each
  weather in each season (SPRING= to WINTER=, ALL= for the rest), the
  area's climate bits still added; `TRANSITION=RAIN->FOG=30` in place of the
  built-in change; `HOLD=` the fewest game hours a weather lasts before it
  changes on its own (a forced change does not wait); `ALLOW=` the only
  weathers it has; `TEXT_<WEATHER>=` and `STOP_<WEATHER>=` lines (| between
  alternatives, NONE for silence) in place of lists.ini's. An area picks one
  with `MODIFY AREA WEATHERPROFILE name|NONE`, kept as an area blurb flag
  (so in the .cmare), and child areas inherit it (`core/ClimateProfiles`,
  in `DefaultClimate.weatherChances`, the tick and the texts). A missing
  file or a bad line is logged; WEATHER FORECAST names the climate.
- W13 done. `Prop_SkyText` on an area or a room (core/SkyText): `DAWN=`,
  `DAY=`, `DUSK=` and `NIGHT=` under the sky, `INDAWN=` to `INNIGHT=`
  indoors, `WEATHER_<STATE>=` (the weather's description as it starts and
  as LOOK and WEATHER tell it), `STOP_<STATE>=` (its ending line) and
  `MOON=` (TIME's moon line); | between alternatives, NONE for silence.
  A room's own come first (a change of weather is told in its own words
  there), then its area's, then the parents'; what is unset comes from the
  climate file (W20), then lists.ini. lists.ini's sun now rises in the east
  and sets in the west.
- W8 done, on the engine's tide model (low in the hour the day starts,
  high in the dusk hour, spring or neap by the moon; RISING and EBBING
  would need a new one): `core/Tides`, with an optional span of hours
  either side (`HIGH:2`). `ISTIDE(room HIGH|LOW|NONE|SPRING|NEAP[:span])`
  (211); masks `-TIDE +HIGH` (only there) and `+TIDE -HIGH` (not there),
  where one stands; VARIES `<HIGHTIDE>` and `<LOWTIDE>`. `Prop_TidalExit`
  (`OPEN=LOW|HIGH SPAN= MSG=`) lets one through only at that tide.
  `TidalRoom`, a preset of RoomStates: a swimming room (Chant_Flood) at high
  tide (`SPAN=`, `TITLE=`, `DESC=`, `FLOODMSG=`), its air water at the spring
  tides with `DEEP`, `WASH=n` washing away the loose things weighing up to
  n as the water comes; itself again at the ebb. ROOMSTATE() and
  MPROOMSTATE reach it (and W19's Floodable) too.
- W10 done. `ISDARK(room)` and `ISLIT(room)` (207, 208; HERE, a room ID
  or a thing's room). Mask keys `+ROOMDARK` and `-ROOMDARK`, seen from the
  character's side: the room they are in is dark and they cannot see in
  the dark (StdRoom keeps IS_DARK from what reaches its occupants, so
  -DISPOSITION could not tell). `Prop_DarkDanger` (rooms, areas), for a
  player the room is dark to: `LOST=n%` a move goes a random open way
  (MSG=), `STUMBLE=n%` a move fails and costs movement (STUMBLEMSG=),
  `LURKER=name LURK=n%` a catalog mob (or a mob class) comes out of the
  dark as they enter (LURKMSG=); a light, or seeing in the dark, ends it.
  It acts for the characters played (they have player stats).
- W11 done. Prop_RoomDark (rooms, areas) takes `ABSOLUTE=TRUE`: as
  Spell_DarknessGlobe, the room's recovery turns off the lights of its
  inhabitants and items before they light it, and anything that may light
  it again (entering, holding, wearing, dropping) recovers it after;
  `LIGHTMASK=` is the lights that still work (a mask on the item, so
  `-TAGS +DAWNLANTERN`), `SEEMASK=` who still sees in it (it adds seeing
  in the dark to them), `SNUFF=n` the chance a lit light goes out as one
  comes in (SNUFFMSG=, @x1 the light).
- W3 done. `Prop_Hazard` (rooms, areas), on its own tick: `TYPE=` COLD,
  HEAT, ACID, GAS, POISON, ELECTRIC, HOLY, UNHOLY, DISEASE or THINAIR,
  `DAMAGE=` dice (1d6+2) or a formula (@x1 the victim's level) dealt as
  that type's damage message, so resistances count; `SAVE=` a type (or a
  save stat) whose roll halves it (the type's own by default); `DRAIN=MOVE|
  MANA` takes it from that pool instead (THINAIR's default); `AFFECT="id
  pct%"` may give an effect; `ROOMS=` and `WHEN=` masks on the victim each
  tick (where they are, the weather: an area's hazard on its mountains
  alone, a pass only in a blizzard), `PROTECT=` the mask of those spared;
  `EVERY=`, `ONSET=` ticks first, `ESCALATE=` percent more each time in a
  row, `WARN=` once as it starts, `DARKONLY`, `LITONLY`, `NONPC` (players
  only), `NAME=` what hurts and `MSG=`. Fixed on the way: Prop_InstantDeath
  on an area did nothing, as it woke only on messages aimed at the area;
  it wakes when a room of the area is entered.
- W18 done. `core/Breath`: where a mob cannot breathe (no air, or the
  room's water or gas), each tick spends a tick of breath before it chokes
  or drowns: `BREATHTICKS=` (coffeemud.ini, world.ini; a formula, @x1
  constitution, @x2 level; `@x1/3` by default, 0 for none) plus the
  `TICKS=` of each `Prop_AirSupply` carried (an air bladder). It warns at
  half, at a quarter and when it is gone; air it can breathe fills it
  again, so an air pocket needs nothing new. `BREATH(who)` (209) and masks
  `+BREATH <3` and `-BREATH <3` (the ticks left, compared).
- W7 done, but for CLASS (a room's class means a new room object; ADD of
  a swimming effect, Chant_Flood, does that part). The `RoomStates`
  behaviour (rooms, areas): clauses with a semicolon between, `STATE=name`
  with `TITLE=`, `DESC=`, `ATMOSPHERE=` (a resource, WATER, AIR), `CLIMATE=`,
  `ADD="Prop_RoomDark();WaterCurrents(east)"` (effects and behaviours it has
  in the state, taken away after), `BLOCK=north,down` (ways out refused),
  `OPEN=up` (doors opened, quietly) and `ENTERMSG=`; `TO=name` with `FROM=`,
  `WHEN="mask"` (on someone standing there: the weather, the time) and
  `AFTER=` ticks in a row, `TICKS=` in the state, `ONVAR="[host] name==value"`;
  `START=`. What a state leaves unset is the room's own again. On an area,
  its rooms change together (`ROOMS=` IDs for some). `MPROOMSTATE room|area
  state` (138) and `ROOMSTATE(room|area)` (210); the behaviour's saved state
  keeps the state and puts it back. Its WHEN= is its own: N1's
  BehaviorWhen leaves it on (`BehaviorWhen.OWNWHEN`), where it read the
  first WHEN= as the behaviour's on and off.
- W19 done. `Floodable`, a preset of RoomStates: levels 0 dry, 1
  ankle-deep, 2 waist-deep (a swimming room, Chant_Flood; the loose things
  weighing up to `FLOAT=` float off through `FLOWTO=`, or away), 3 full
  (its air water: one drowns as W18's breath runs out). While its
  condition holds (`RISE=` a mask on someone there, `ONVAR=`, `TIDE=`; any
  of them) the water rises a level each `RATE=` ticks, else it drains a
  level each `DRAIN=` ticks; rising water puts out lights. `TITLE1=` to
  `TITLE3=`, `DESC1=` to `DESC3=` and `MSG1=` to `MSG3=` per level.
  `FLOODLEVEL(room)` (212).
- W4 done, with T1's WAIT UNTIL ARRIVAL and MPINTERRUPT (`core/Waits`).
  `WAIT n TICKS|HOURS|DAYS|WEEKS` and `WAIT UNTIL DAWN|DAY|MORNING|DUSK|
  NIGHT|HOUR h|DAY d|ARRIVAL|STOP|DOCK` (the next one; ARRIVAL and STOP
  when the vessel or mount one is on stops after moving or docks, DOCK
  when it docks, from ST8's dock), and for builders `WAIT UNTIL VAR host
  name op value` and `WAIT UNTIL QUEST name STEP n|DONE`: at most three
  game days, the game's own ticks running (so one heals and things
  respawn), a line each game hour, ended as WAIT always was (hurt,
  attacked, moved, spoken to). In real time the clock pauses for the wait
  and starts again after (`GameTime.requestAdvance(ms, until, after)`, the
  hook run when the time is taken or dropped). `LONGWAIT=ALL|BUILDERS`
  (coffeemud.ini, world.ini) says who may wait more than 100 ticks.
  `MPPASSTIME n HOURS|DAYS | UNTIL ... [INTERRUPTIBLE] [QUIET]` (139) and
  `MPINTERRUPT who [message]` (140; the waiter or their party); JavaScript's
  `cm.passHours(n)` and `cm.interrupt(who, msg)`. `Prop_InnRoom` (HOURS=,
  MSG=): a wait there of that long ends in a full rest.
- W28 done. `Prop_TimeMode` (rooms, areas; `core/TimeLock`, from the move
  hook ST16 calls after each move): `MODE=TURNS|REALTIME` while one played
  is in it, switched as TIMEMODE does (its GAME_PROG TIMEMODE too), the
  mode before put back as they leave (`RESTORE=TRUE`, the default),
  `ONLYCOMBAT=TRUE` only while they fight there, `MSG=` as it switches;
  TIMEMODE there says time is held. A save keeps the mode before.
- W9 done. ResetWhole `DAYS=n` (game days of the area's calendar, in
  place of TICKS), `EXCEPT=mask` (a mob or item it names that is gone when
  the reset comes, a slain boss or taken loot, stays gone; one still there
  stays, once; a name with spaces as `+'the Goblin King'`, which it gives
  the masks as `"+the Goblin King"`), and `MSG=` shown to the first who
  comes in after a reset (the move's trailer, after the room's look).
  Its time since the last visit and a waiting message are kept in a save
  (its STATE); a party member with no window counts as a visit. RLHOUR is
  noted in the help as meaning nothing with pause and turn-based time.
- W5 done. An area's calendar as content: the area stat `CALENDAR` (kept in
  its data, so in its .cmare; `MODIFY AREA CALENDAR HOURS=10 DAYS=20
  MONTHS="Leafwake,Bloomtide" WEEK= YEARS= DAWNHR= DAYHR= DUSKHR= NIGHTHR=
  START=812/2/3/0`, NONE takes it away, alone shows it; the area editor's
  calendar screen writes it). `core/Calendars` gives such an area a clock of
  its own, shared by its child areas; what it leaves out comes from its
  parent's clock, else the global one. A new game starts it at START, a
  saved one goes on from its date: its TIMECLOCK record holds only the date
  now. `STARTDATE=Y/M/D/H` in coffeemud.ini (or world.ini) is the global
  clock's START.
- W22 done. More CALENDAR fields: `SEASONS="the Burn:SUMMER:1-2,the
  Rains:SPRING:Flood-Silt"` (each season's name, the built-in season it
  counts as for the weather and the like, and its months, numbers from 1
  or names), `SEASONOFFSET=n` (an area's own: its seasons n months on, on
  its parent's clock, for a southern land), `DAYLIGHT="WINTER:2-4"` (a
  season's dawn and night hours, or all four), `MOONS="Selune:24,Ember:9:3"`
  (each moon's name, cycle in days and offset; the first is the moon the
  tides and ISMOON(FULL) follow). ISSEASON(the Burn), ISMOON(Ember full)
  (ISMOON(Ember): in the sky), `-SEASON "+the Burn"`, VARIES blocks in a
  season's own name (`<the Burn>...</the Burn>`) and TIME know them.
- W6 done. `core/Holidays`. A quest (a holiday is one) starts on more
  than a MUDDAY: `SET WEEKDAY name`, `SET EVERY n DAYS FROM y/m/d`, `SET
  SEASONSTART season` (its first day; a calendar's own season by name too)
  and `SET MOON [moon] phase` (the first day of that phase; a calendar's
  named moon, else the first); `SET DATE m-d` follows the game's calendar
  now, and DAYS and HOURS in a DURATION are game ones. `SET HOLIDAY name`
  has a quest run exactly while that holiday does. ISHOLIDAY(name),
  HOLIDAY() (the names on now), `+HOLIDAY [name ...]` and `-HOLIDAY`,
  VARIES `<HOLIDAY name>...</HOLIDAY>`, `cm.holiday(name)` and
  `cm.holidays()`; CALENDAR HOLIDAYS shows each game-dated one, on now or
  in how many game days. A clock's copy keeps its area's CALENDAR, so a
  quest's timing and an instance's seasons and moons follow it.
- W23 done. `core/Schedules`: a quest's `SET SCHEDULE "rules"`, all of
  which must hold: `HOUR a-b`, `WEEKDAY name`, `EVERY n DAYS [AT DAWN|AT
  HOUR h]`, `SEASON name`, `MONTH name|n`, `MOON [moon] phase`, `HOLIDAY
  name`, `WEATHER state` (in the quest's area). Each tick the quest looks at
  its window: it starts as the window opens (once in each), and `SET
  DURATION UNTIL DUSK`, `UNTIL HOUR h` or `UNTIL END` ends it at dusk, at
  that hour, or as the window closes; a DURATION of its own counts down as
  ever.
- W21 done. An area's own law (Arrest `custom`) as world content:
  `world/laws/[area].ini` (named as the area's file is). In builder mode
  the law book's edits (updateLaw) are written there as well as to the
  working database, and the file is the law; in a game the saved law
  (CMPDAT AREA/ARREST, save state as before, such as after a conquest)
  wins, and a new game, having none, starts from the file. The help gives
  the `file;KEY=VALUE` route too.

#### 2j. Generators and random content (16)

G1, G3, G4, G5, G6, G7, G8, G9, G10, G11, G12, G13, G14, G15, G16, G17

- G1 done. `core/GenSeed`: a seeded run keeps the shared generator's
  state, seeds it, builds, and puts the state back, so the game's own rolls
  go on as before. `GENERATE ... SEED=n` builds the same thing each time;
  with no SEED it picks one, says it, and keeps it (an area's `GENSEED`
  flag, `$GEN_SEED` in the template). `seed="..."` on an AREA, ROOM, MOB
  or ITEM tag (math with the variables; `$GAME_DAY` is set, for a dungeon of
  the day). StdAutoGenInstance's and AutoGrid's `SEED` variable: a number,
  `GAME` (the save's own seed, kept in its header, with the area's name),
  `ENTRY` (a new one each time, kept on the copy). A seeded run skips the
  item cache, metacraft tries 20 times rather than for a second of real
  time, and the BRANCH layout shuffles with the shared generator. A
  seeded run rolls on its own stream on the thread building, so the
  game's other threads rolling meanwhile cannot change what it makes
  (they did, now and then, for a large build). Post-processing (the
  parts that wait for the rooms to be linked) starts again from the seed,
  whatever ran since the build, and the crafting skills' one-time worker
  and fake fire no longer take rolls from the first build to craft. Still
  open: after other generating in the same session, the first build of a
  seed whose loot is crafted (metacraft) can still differ from the next;
  some other one-time setup on that path is not found yet.
- G3 done. A breadth-first walk from the entrance over the layout's nodes
  gives each room template `ROOMTAG_DEPTH`, `ROOMTAG_DEPTHPCT` (0 to 100),
  `ROOMTAG_ISDEEPEST`, `ROOMTAG_INDEX`, `ROOMTAG_X` and `ROOMTAG_Y`, and the
  area `AREA_MAXDEPTH` and `AREA_NUMROOMS`. An AREA's `order="depth|reverse-
  depth|random"` sets the order its room groups are built in (after the
  entrance), so a counter-gated room lands deep or near.
- G5 done. `core/GenCalls`: generation from a template file at play time,
  a fresh roll each call. `MPGENERATE ITEM|MOB|ROOM|AREA|QUEST file id
  [VAR=VALUE ...]`, with `INTO container`, `TO mob` or `HERE` for an item,
  `AT room` for a mob, a direction for a room or area (and `SEED=n`); `$b`
  is the first thing made. `GENSTRING(file id [VAR=VALUE ...])` returns
  text. Every call defines PLAYER_LEVEL and PARTY_SIZE (of the one who set
  the script off, if a player), AREA_LEVEL, AREA_THEME, HOUR, SEASON,
  WEATHER and GAME_DAY, unless given. `FROMGENFILE ... FRESH` rolls anew
  rather than reusing the kept roll.
- G11 done. JavaScript's `gen` object, in every scope (JRUN, JCONSOLE,
  a MOBPROG `<SCRIPT>`, JS_PROG...; `core/JSGen` over G5's GenCalls):
  `gen.item(file, id, vars)` and `gen.mob(...)` (arrays of live objects,
  placed nowhere), `gen.string(...)`, `gen.room(file, id, dir)` and
  `gen.area(file, id, dir, seed, vars)` (linked off the host's room),
  `gen.layout(name, size, seed)` (the nodes: index, x, y, type, exits, as
  GENERATE would lay them out with that seed). vars is an object.
- G6 done. `Prop_LootTable` (mobs, items): `FILE= ID= ROLLS=` (math,
  `0?2`) `ON=DEATH|OPEN|SPAWN|RESET CHANCE= GOLD=`, each roll fresh (G5's
  GenCalls) with $mob_level, $area_level, $theme and $player_level set. A
  mob's DEATH roll goes into its corpse; a container's OPEN roll is made the
  first time it is opened, and it stays rolled (`ROLLED=TRUE` in the
  property's text, `getStat ROLLED`, so a save keeps it) until its contents
  reset, which brings it back new; SPAWN (or RESET) rolls as the host comes
  into the world. DEVPLAN item 1's planned container rule is to share that
  state. A `class="catalog"` entry keeps the catalog's cap (past it, it
  gives nothing). ItemGenerator now picks from the first item (it began at
  the third). `resources/randareas/loot.xml`: `goblin` and `chest` tables,
  with tiers by pickweight.
- G10 done. `core/Affixes`: random enchantment as data, `world/affixes.xml`,
  else `resources/randareas/affixes.xml` (or the world's own copy): each
  `<AFFIX id name pos="prefix|suffix" kinds="weapon,armor,ammo,item,any"
  level="5-30" pickweight power cursed>` holds `<AFFECT class parms>` tags.
  An item gets at most one prefix and one suffix that fit its kind and
  level, by weight: the name takes them (a/an set again, "of" before a
  suffix, the display text too), the level rises by power (1 by default),
  the value by a tenth for each, cursed makes it unremovable. enchant()
  uses them when the file has any (ItemGenerator ENCHANTED=, digsites), as
  do `AFFIXES=` on Prop_LootTable and RandomItems and `AFFIXES="0?2"` on a
  template ITEM. A sample file of eight ships.
- G12 done. `core/NameStyles`: name styles as data, one file per style,
  `world/namegen/STYLE.ini`, else `resources/namegen/STYLE.ini` (or the
  world's own copy), read again when the file changes. VOWELS= and
  CONSONANTS= hold syllables in the old makeRandomNameSets format (digit
  flags 4 middle, 2 first, 1 last), with START=, SYLLABLES=, PREFIXES= and
  SUFFIXES= (with chances), MALE=, FEMALE= and NEUTER= given names
  (GIVENPCT=), FAMILY= or FAMILYSTART= and FAMILYEND=, and TITLES= for the
  full form. RACES= and LANGUAGES= tie a style to races and languages; a
  GenRace's or GenLanguage's NAMESTYLE (saved with it, in both editors)
  comes first. The old table is `default` (generateRandomName gives the
  same names as before); elvish, dwarvish and goblin ship. Templates write
  `SYSTEM_RANDOM_NAME:2-4:style[:FULL]` (a style, race or language; an
  unknown one is an error); with none, a NAMESTYLE the template or theme
  defines, else the mob's race's. `RANDNAME([style] [min max] [M|F|N]
  [FULL])` in scripts (216). `NEW * dwarf` and a login `* elvish` offer a
  name in that style; the name is confirmed before the race is picked, so
  plain `NEW *` stays default. True Name and hired crew use the race's
  style. Left for P8: `NAME=RANDOM` on player templates. Also fixed: the
  guide's `$SYSTEM_RANDOM_NAME:2-4` was never looked up in template text
  (only as a tag name, and the colon ended the variable), so it threw
  "Unknown variable"; it works now, braced or not. And the language
  editor's Translation Verb prompt set nothing (TRANVERB).
- G13 done. `GENERATE AREA ... [DIR] UNLINKED` builds with no gate and
  says the entrance, for LINK later. `GEN_BACKTICKS=false` (ini or
  world.ini) puts real apostrophes back in what GENERATE makes (names,
  display texts, descriptions, exits), after it is built: the templates'
  conditions still need the backticks inside. An area made by GENERATE
  AREA (or MPGENERATE AREA, gen.area) keeps a `GENSOURCE` flag, a blurb
  saved in its .cmare: the file, the AREA id, the seed and the variables
  given (`core/GenSource`). `REGENERATE [NOSAVE] AREA [SEED=n|SAME]
  [VAR=VALUE ...]` builds the builder's area again from it with the area
  out of the map, so the new rooms take the same IDs; rooms marked GENLOCK
  (`Prop_GenLock`, `REGENERATE LOCK`/`UNLOCK`) are kept with their exits,
  the rest replaced, every exit from inside or outside (the gate) pointed
  at the room of its ID (one to a room the build lacks removed, a kept
  room's exits given their way back where the slot is free, the rest
  counted as one-way in the report), players
  and their followers moved to the new room, and the post-processing run
  in the seeded run after. `REGENERATE` alone says the source and the
  kept rooms. A building command for LIST UNSAVED.
- G14 done. `Behaviors/Regenerate` on an area: `FILE= ID= VARS="..."` (or
  the GENSOURCE when left out), `SEED=RANDOM|DAILY|WEEKLY|SAME|n`,
  `WHEN=MUDDAY:n,MOON:phase,ONEMPTY,SCRIPT` (SCRIPT alone by default),
  `KEEP=` room IDs. It builds as REGENERATE AREA does (G13), keeping GENLOCK
  rooms, the KEEP rooms and the rooms a running quest uses (the room, or
  someone or something in it), waits while a player or follower is inside,
  and the area's GENERATED_PROG (ST20) runs after, as it now does after
  REGENERATE too. Its STATE (the day counted from, the moon last seen, the
  party inside, a re-roll waiting and its seed) goes in a save; the rooms
  are rooms changed in play (S17). `MPREGENERATE area|HERE [SEED=n|SAME]`
  (142) asks the behaviour, or with none re-rolls from the GENSOURCE at
  once when no player is inside.
- G9 done. `GENERATE FILL roomtemplate [FROM file] [VAR=VALUE ...]
  ROOM|AREA [KEEPTEXT] [SEED=n]` (`core/GenFill`): each target room (the
  builder's, or all of its area, in walk order from the entrance) gets the
  layout's tags from its real exits: ROOMTAG_NODETYPE (leaf, street,
  square), NODERUN, NODEEXITS, NODEFLAGS (corner, tee, intersection, gate),
  the neighbours' NODETYPE_x, ROOMTITLE_x and ROOMLINK_x, and NODEGATEEXIT
  and GATEEXITROOM for an exit out of the area; and G3's depth tags (DEPTH,
  DEPTHPCT, ISDEEPEST, INDEX, X, Y; AREA_MAXDEPTH, AREA_NUMROOMS) from a
  walk from the entrance. The percolator's new `fillRoom` runs the ROOM
  template's choice on the room itself: title and description unless
  KEEPTEXT or GENLOCK, stats, mobs, items, effects and behaviours; class
  and exits stay. A seeded run; post-processing after each room.
- G7 done. An AREA's `<ENSURE>` tags run after layout and fill. With
  `name=` the mobs and items whose names hold the words are counted where
  they may be (`where="any|reachable|reachable-without:keyname=KEY"`, the
  last a walk from the entrance not through a door locked to that key),
  one in the wrong place moved to a right one, then the `fallback=` ITEM
  or MOB tag built and put there (`in="mob|container|room"`) until `min=`,
  and `max=` removes extras, the misplaced first; with `var=` the counter
  is read and set. `<ENSURE room="id" count="n" replace="id">` makes sure
  that many rooms came from that ROOM template (the percolator now notes
  each room's template ids): the deepest rooms, of the REPLACE template if
  given, are emptied and filled from it (G9's fillRoom), their exits kept.
- G8 done. `layout="MAP"` (`Libraries/layouts/MapLayout`): the AREA's
  `<MAP>` text or its `MAPFILE=` (a `world/` path or a resource), the
  indentation its lines share taken off. Characters other than links and
  spaces are rooms: `.` street, `o` interior, `L` leaf, `@` the entrance
  (first), `^`/`v` stairs, others by their links' count (leaf, street,
  square); `<GLYPH char="B" room="id">` builds that glyph from a ROOM
  template. Rooms side by side join; `-` and `|` join rooms a space apart,
  `+` with a door (a GenDoor both ways, after the layout), `/` and `\`
  diagonally; a line of `=` starts the floor above, a `^` joining the
  nearest `v` in its column there. Straight streets get their run, and the
  layout's flags are filled in as for the others. Rooms also get
  `ROOMTAG_GLYPH` and `ROOMTAG_DOORS`. The size is the rooms drawn.
- G16 done. Five layouts in `Libraries/layouts`, on a new
  `GridLayoutBase` (its own cell map, links both ways, the entrance the
  room furthest toward the way in, types by links, straight runs, then the
  layout's flags): CAVE (a grid filled at random and smoothed, its largest
  region grown to the size and walked from the way in, a quarter of the
  other neighbours joined too), CLUMP (a square block, the entrance
  outside, dead ends of one to three rooms), LINEAR (a road two thirds of
  the size, spurs of one or two rooms), RING (a square loop, the entrance
  outside, spokes in, or out on a small ring) and ISLANDS (blobs in a row,
  each joined to the next by one bridge room; about the size). `layout=`
  takes a comma list and picks one at random. `levels="n"` on an AREA or
  a ROOM with a layout makes the layout n times, the size shared out, each
  floor joined to the next by a stair at a room that is not a leaf nor
  the floor's first (DOWN and UP back, or `levelsdir="UP"`); rooms get
  `ROOMTAG_LEVEL` (MAP floors too) and `ROOMTAG_STAIRS`, the area
  `AREA_LEVELS`.
- G4 done. `GENLAYOUT layout size [dir] [SEED=n] [LEVELS=n]` draws a
  layout with LayoutTester's drawer, now `drawText` (the name, size,
  whether all is joined, a count of each node type, the map), seeded, a
  floor each for LEVELS, and lists the layouts with none given.
  `GENERATE PREVIEW type id [FROM file] [vars] [SEED=n] [dir]`
  (`core/GenPreview`) builds an AREA unlinked, a ROOM in a free (or the
  given) direction, or MOBs and ITEMs in the builder's room, NOSAVE,
  lists it with the seed (rooms: ID, title, node type, exits, mobs with
  levels, items; mobs: race, class, gear; items: level, value) and
  destroys it; `GENERATE PREVIEW KEEP` runs the real GENERATE with the
  same words and seed, an area unlinked unless a direction was given.
- G15 done. Data: seven themes in `resources/randareas` (crypt, ruins,
  castle, volcano, sky, mine, sewer), each one file: its `randomtheme`
  choice, one AREA `<theme>_dungeon` with a layout list (G16) and its name
  once in `requires`, merged into `random_dungeon`'s choices, three room
  kinds merged into the shared street, inner and leaf groups for it (a
  boss in the deepest leaf, G3), four mobs, junk and treasure, all from
  the shared word banks. `inc_setpieces.xml`: a riddle chest (locked,
  `Prop_OpenPassword` with the answer to the riddle on its lid), a lever
  room (`EXECMSG_PROG PULL` loads a cache with MPOLOAD FROMGENFILE), a
  healing shrine (GREET_PROG MPCAST), a trapped corridor (`RandomTraps`)
  and a collapsed passage (something hidden under rubble), each gated by
  `__setpiece` and `ROOMTAG_DEPTH`. `theme_skeleton.xml` with a comment on
  each slot; `example.xml` loads the new themes. RandomAreas.html: the
  layouts listed (BRANCH, MESH, SPOTTEDMESH and the new ones), repeat-X
  told as picks with replacement, and the skeleton and set pieces.
  Also: GENERATE CHECK now reads G16's comma list of layouts, each one
  checked, where it called the list unknown; and a container a template
  makes now starts as its DEFCLOSED and DEFLOCKED say (it was always made
  open), so a locked chest is locked.
- G17 done. `${js:expr}` in a template's text and `CONDITION="js:..."`
  and `VALIDATE="js:..."` on its tags (core/GenJs): the expression sees
  `defined` (the variables by any case, numbers as numbers, a string tag
  filled in, set to define), `obj` (what is being built), `test(cond)`
  (the template grammar) and JSScope's `cm`, `gen`, `sud`, `require()`,
  `include()` and `debug()`. One scope a build (kept as `_JS_SCOPE` in
  the variables), so a library's state lasts while an area is made;
  `Math.random` is the seeded dice. An error stops the build with its
  line and goes in JS ERRORS; in a condition it is false. Merged tags'
  js: conditions join with `&&`, a plain one in `test()`. GENERATE CHECK
  compiles each. JSCRIPTS=0 turns it off. Also:
  `XMLManager.restoreAngleBrackets` never restored `&amp;` (it compared
  five characters with the four of `amp;`), dropped the `&` of anything
  else, and could throw on a short `&qu` at the end; all three fixed.
  Not done: a `%` followed by two hex digits is still read as an escape
  in an attribute (a space after it avoids that).

#### 2k. Crafting, gathering and housing (21)

CR1, CR2, CR3, CR4, CR5, CR6, CR7, CR8, CR9, CR10, CR11, CR12, CR13, CR14,
CR15, CR16, CR17, CR18, CR19, CR20, CR21

- CR1 done. `core/ProfileOwner`: every character of the profile owns
  what another of them owns, and what the reserved owner `@PROFILE` owns,
  in `doesHaveWeakPrivilegesWith`, `doesOwnThisProperty`,
  `doesOwnThisLand` and `canAttackThisProperty`, so nothing is theft
  between them and any of them can build there. `@PROFILE` always exists
  (`isProperlyOwned` of the lots, private items, titles and vessels, and
  the lot owner check), so a world file can ship
  `Prop_RoomForSale("@PROFILE/0")`; it is no one character for
  `getPropertyOwner`. `PROFILEPROPERTY=true` in the ini (or world.ini)
  records land and vessels bought as `@PROFILE`. From T15: `MPGRANT item
  who|NOBODY [PROFILE] [NAME=new name]` gives a deed (the lot changes
  hands, the deed goes to them), a vessel (registered with the character
  so it saves with them, renamed through `rename` with no prompt, which
  fills a `[NAME]` marker as a shipyard's stock has, docked near the
  script's room by `StdBoardable.dockNearest` if it is not in a room) or
  a private item; NOBODY takes it back.
- CR6 done. Script functions `OWNER(room|area|HERE|$n|$i)` (the owner,
  `@PROFILE`, or nothing; a mob's room, an item's own record),
  `ISOWNER($n [place])` (CR1's profile rule; the room $n is in by
  default) and `LANDTITLE(place)` (the lot and price). Commands
  `MPDEED place $n|@PROFILE [price]` (owner set, back taxes cleared, the
  title and lot updated as a sale does) and `MPUNDEED place` (for sale
  again, its room reset as a lost lot is). INROOM's BEACON was already
  fixed in SC14; now `INROOM($n == MYPROPERTY)` and `BEACON` ask of the
  mob on the left, where they asked of the scripted mob.
- CR7 done. `Prop_Homestead` on a room or area: MASK= (on the
  builder), SKILLS= (by ID or name), CLAIM=true|false|PROFILE,
  MAXROOMS=, PRICE= and NOTAX. BuildingSkill: when the builder may not
  build and the room has no title, the homestead's first build claims it
  (a `Prop_LotsForSale` owned by the builder or `@PROFILE`, priced PRICE,
  1 by default, which property tax skips; MAXROOMS as a
  `Prop_ReqCapacity` rooms limit) and the build runs again as the owner's,
  so every existing rule applies; a build that does not start takes the
  claim back. CLAIM=false lets them build with no title. Found while
  testing: a build costs movement, and a tired builder is refused with
  only a say.
- CR13 done. `Prop_HomeBeacon` on a room: SLEEP there (the Sleep
  command calls `Prop_HomeBeacon.slept`) or `HOME SET` makes it the start
  room of every character of the party with privileges there (the party:
  Party's characters and the leader's group). `HOMEBEACONS=true` makes
  every owned room a home. `HOME` (Commands/Home) takes the whole party,
  with followers, to the leader's home after `HOMERECALL` turns (3 when
  blank, 0 for none) through the `GoingHome` effect, which a fight in the
  party stops; not in combat, and only to a room that is still a home.
- CR5 done. `core/RecipeKeys`, for every crafting skill (CraftingSkill):
  the keys come from GenCraftSkill's KEY_VALUE_PARMS column or any
  column that starts with a key (a stock skill's extra last column).
  `addRecipes` drops the rows whose MASK= the crafter fails (so LIST and
  making leave them out); `matchingRecipes` (the old body is now
  `matchingRecipesAll`) leaves out those whose ROOMMASK= (a mask on the
  crafter, as -LOCATION +ROOMID(x)) or TOOL= (held, carried or in the
  room) fails, and the skill's "You don't know how to" is replaced by
  FAILMSG= (or a plain refusal); `dropAWinner` gives XP= and, the first
  time, TATTOO=, for the recipe the item was made from. GenCraftSkill
  no longer sets them as item stats. Only player crafters are held to
  them, not the game's own crafting (loot, stock, a factory worker). A
  filtered LIST (LIST bench) also leaves out a ROOMMASK or TOOL recipe
  that fails there.
- CR3 done. `Prop_Workstation` on an item or room (TAG=, SKILLS=,
  SPEED=, PROF=, MAXLEVEL=, QUALITY=) and `core/Workstations`: a crafter
  in the room gets the best SPEED (CommonSkill.getDuration), PROF (a new
  CommonSkill.proficiencyCheck) and MAXLEVEL (xlevel) of the stations
  helping their skill; QUALITY waits for CR17. An item tagged forge
  (TAGS) is a station with no bonus. STATION= is a recipe key (CR5), and
  GenCraftSkill has a STATION field (stat 15, in its editor) for all its
  recipes. Components: `TAG(name)` (CompType.TAG, an item with that tag)
  and the fate `wear(n)` (kept, n uses off when the components are
  spent, broken at none), both kept by the editor's encoding. Also: core
  code cannot test `instanceof` against classes the game loads itself
  (abilities, items), so Workstations knows stations by the interface
  `Workstations.Station`, and CR1's MPGRANT now docks a vessel through
  `dockNearest` by name (its `instanceof StdBoardable` never held).
- CR20 done. `Prop_Furnishing` on an item: ROOMEFFECT=ID(PARMS) and
  ROOMBEHAVIOR=ID(PARMS) (read to the closing parenthesis, spaces and
  all) go on the room, not saved with it, while the item is placed there
  (not gettable, as decorated) or lies on the floor of an owned room, and
  come off when it leaves or is destroyed (checked on its stats and the
  room's messages); COMFORT= sums into `Prop_Furnishing.comfortOf(room)`,
  read by the new script function `COMFORT(place)`; ACTS= makes it a
  `Workstations.Station` of those names, and a fire for crafts that need
  one (`getRequiredFire` takes `Workstations.itemFor(room, "fire")`).
  Masks do not read comfort; a script can.
- CR4 done. `Prop_KnownRecipes` on a character (implements Recipes and
  `KnownRecipes.Known`), its text SKILL|name or SKILL|whole line a line,
  saved with them: `addExtRecipes` adds its whole lines (and, under
  `PROFILERECIPES=true`, those of the profile's other characters in the
  world) to the skill's recipes, through the new `addExtRecipeLines`.
  Recipe key `HIDDEN=1` (CR5's RecipeKeys, which now take the skill): a
  row only for those who know it by name. `LEARN FROM [BOOK]` (the Learn
  command; STUDY is the Studying skill's word) copies a recipe book's
  lines in; script command `MPLEARNRECIPE $n skill name|line` (\t in a
  line) and function `KNOWSRECIPE($n skill name)`.
- CR21 done. GenCraftSkill fields CANLEARN (stat 16, false by default)
  and LEARNMASK (stat 17, an item mask), in its editor: CANLEARN turns
  `supportsDeconstruction` on, LEARN [ITEM] goes to `doLearnRecipe`, and
  `unInvoke` has the LEARNING step (the item taken apart into the blank
  recipe book by `deconstructRecipeInto`, or a failure that ruins it);
  LEARNMASK narrows `mayILearnToCraft`. AHELP GENCRAFTSKILL is new.
- CR18 done. `[SKILL] EXPERIMENT [THING]...` for every crafting skill
  that calls `CraftingSkill.checkInfo` (35 of them; `core/Experiments`):
  a recipe of the skill marked HIDDEN=1 with `EXPERIMENT=a,b,c` is found
  when the crafter, at its level or above, offers just those things
  (items carried, by name); it goes into Prop_KnownRecipes (CR4) and
  gives XP= or ten a level. Half or more of them is a near miss that
  tells HINT=; any other try uses up one of the things offered.
  `CRAFTEXPERIMENTS` (10 a game day when blank, 0 for no limit) counts
  tries in memory, so a restart (or a restore) gives them back. Rather
  than read each skill's own components column, the recipe names what
  reveals it.
- CR17 done. Quality tiers for what players craft (`core/CraftQuality`),
  from `resources/skills/craftquality.txt` (a world's own copy first),
  read again when the file changes. A tier line is NAME, CHANCE (a
  formula over the level margin, proficiency and the station's QUALITY),
  multipliers for value, armour, damage and uses, a level bonus and
  effects. `CraftingSkill.dropAWinner` rolls one for a player's success
  only, before ITEMGENERATED: the tier's word goes in the item's name
  (not its brand, so the maker's mark is unchanged). The shipped file
  has its tiers in comments, so nothing changes until a world gives
  some; a data skill's QUALITY (on by default) or a recipe's
  NOQUALITY=1 turns it off.
- CR12 done. Crafting and gathering XP for every class (`core/CraftXP`,
  heard in `StdMOB.executeMsg`): a player character's ITEMGENERATED
  from a crafting, cooking or building skill earns `CRAFTXP` percent
  of its XP value, and an ITEMSGENERATED from a gathering skill earns
  `GATHERXP` a unit; both are 0 when blank. `Prop_CraftXP(PCT= GATHER=
  SKILLS= MASK=)` adds more, on the character, an item they carry, the
  room or an item in it, or the area or a parent. It goes through
  `postExperience`. An Artisan earns it on top of its own award, so a
  world that wants artisans ahead keeps CRAFTXP under 100. A building
  skill's XP value is half the pounds of material it used.
- CR19 done. `Prop_SalvageYield(SKILL= YIELD= XP= DESTROY=)` on an
  item or catalog entry (`core/SalvageYield`): YIELD is a list of
  `resource:n`, `resource(variant):n` and `catalog:name:n`, each with
  an optional `@pct%`. Scrapping uses it when SKILL (Scrapping when not
  given) names it: just that item, any material, enchanted or not, and
  DESTROY=false leaves the item with the property gone. Every crafting
  skill that calls `checkInfo` gets `[SKILL] DISMANTLE [ITEM]`: the
  item's own Prop_SalvageYield for that skill, or, for an item it could
  make, `DISMANTLEPCT` (50 when blank) percent of its recipe's
  materials times proficiency, in the item's material. It is done at
  once, with no time taken. The variant is the resource's subtype until
  CR15 makes variants data.
- CR2 done. `world/materials.txt` (`core/WorldMaterials`, read by
  `RawMaterial.CODES` right after the built-ins and before the ini's
  ADDMATERIAL_): NAME, FAMILY, VALUE, FREQUENCY, HARDNESS, BUOYANCY,
  CATEGORY, SMELL, EFFECTS, DESCRIPTION and CODE, between tabs. A row
  named as a built-in changes it; the rest are placed at their CODE or
  sorted by name, a gap before a CODE filled with an unused resource.
  Item XML writes a world material's name (MTRALN) next to MTRAL, and
  loading prefers the name, so a renumbering repairs itself; built-ins
  never move, so their items are written as before. The description
  goes on the raw resource. Codes have room for 256 resources (210 are
  built in), and the file is read at start-up only.
- CR15 done. `world/resources.txt` (`core/ResourceVariants`, read
  again when it changes): SUBTYPE, BASE_RESOURCE (a resource or a
  family), DISPLAY_NAME, VALUE, WEIGHT_PER_UNIT, DESCRIPTION, SMELL,
  EFFECTS and NOURISHMENT, between tabs as in world/materials.txt. The
  three raw resource classes apply the row when their subtype is set,
  which every path that names a variant goes through, and
  `makeItemResource` once it has its material; `adjustResourceName`
  uses its name and description, so bundling keeps them, and SNIFF its
  smell. Loading an item from XML does not apply it, so a saved item
  keeps its own name. WEIGHT_PER_UNIT is in the format but not used: a
  raw resource's weight is its amount everywhere in crafting. CR2's
  material description moved into `adjustResourceName` as well.

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
