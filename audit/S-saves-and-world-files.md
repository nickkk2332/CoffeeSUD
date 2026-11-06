# Saves and world files

What survives a save and restore, what content lives in world/ (resources overlay, world.ini, clans, cron), save rules, and the format of world files. Part of the [builder capabilities audit](README.md).

<a id="s1"></a>

## S1. Quest state that survives SAVE and RESTORE

*Kind:* quest feature · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** The save keeps a quest's name, winners, step, time left and quest mobs and items (`WorldState.questsXML`). It loses `MPQSET` variables (read with `QVAR`), the wait of a quest not yet running (so reloading rerolls it), whom `Prop_QuestGiver` already offered to, `QuestChat`'s said-once lines, and which ONCE_PROGs a SAVABLE personal quest script has already run, so those run again. `MPSETVAR` variables already survive: quest scripts use the quest name as their var scope, and every VARSCOPE table is saved.

**Add.** The quest XML gets `<VARS>` (the quest's vars, without object stats) and, for quests not running, `<WAIT>`. `setQuests` puts them back after the step. `Prop_QuestGiver` keeps its offers as quest vars `OFFERED:<name>`. `QuestChat` reads back the `CHAT:<name>` vars it already writes. SAVABLE scripts save `<SONCE>` next to SSVAR: the ONCE_PROGs and QUEST_TIME_PROGs already run.

**Example.** Each broken wall in 'Siege of Brenn' does `MPQSET * WALLS ++`, and step 2 starts at `QVAR(* WALLS >= 3)`. After two walls, a save and a restore, WALLS is still 2 and 'You have joined the siege' does not repeat.

*See also:* Q7 (Both save which quests Prop_QuestGiver has offered (REOFFER offers vs OFFERED:\<name> quest vars)); S7 (S1's SONCE (ONCE_PROGs already run in SAVABLE scripts) is S7's script-state save applied to player scripts); S9 (S1 moves Prop_QuestGiver offers and QuestChat lines into quest vars, which S9 says then need no STATE hook).

<!-- from: quests/qst-save-vars -->

<a id="s2"></a>

## S2. Save rules: save points, no-save zones, no saving in combat, autosave

*Kind:* world option, property · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** SAVE works anywhere and at any time, even mid-fight. `Save.execute` calls `SaveGame.save` directly and sends no message, so neither CNCLMSG_PROG nor a property can refuse it. A save made mid-fight keeps the boss's HP but not who is fighting whom, pending MPALARMs or behaviour state, so a reload quietly ends the fight. Autosave can be scripted today: an ENTRY_PROG that runs `mpforce $n save auto`, with a tattoo so it runs once.

**Add.** `SAVEMODE=ANYWHERE|NOTINCOMBAT|SAVEPOINTS|IRONMAN`, chosen at new game and kept in the save header. `Prop_SavePoint` (room or area, `MSG=`) allows saving there under SAVEPOINTS. `Prop_NoSave` (room or area, `MSG=`) refuses saving there. NOTINCOMBAT refuses while any party member is fighting. `Prop_AutoSave` saves on entry (`ONCE=CHAR` or every entry, `SLOT=auto` or a rotating slot, `MSG="[Autosaved]"`). `Save.execute` checks all of these before saving and says why it refuses.

**Example.** The Sunken Temple has `Prop_NoSave MSG="The water's whispering drowns your thoughts."`, and only the shrine at its centre autosaves. Inns and campfires carry `Prop_SavePoint`.

*See also:* ST6 (Both make SAVE refusable (a GAME_PROG SAVE returning cancel, or save rules), which needs one check in Save.execute); S23, C7 (Three autosave designs (Prop_AutoSave, AUTOSAVE=n, a checkpoint's SAVE=AUTO), and S23's CHECKPOINT command shares C7's name).

<!-- from: properties/pr-savepoint, encounters-combat/enc-save-rules -->

<a id="s3"></a>

## S3. world/world.ini for the settings that define the game

*Kind:* world option, builder tool · *Value:* high · *Effort:* small · *Decision:* yes

**Status (docs audit, 2026-11):** world.ini is not part of the world checksum (world.sum does not cover it); it is read at every start. The text below is the original proposal.

**Today.** Start, death and morgue rooms, the calendar, the starting kit, race and class rules and similar keys sit in `coffeemud.ini` beside the engine's own settings. `SUD.readSettings` reads only that file and the command line. A world can ship a wrapper such as `sud.sh START=Oasis#1 DEATH=Oasis#Shrine`, or a full ini selected with `BOOT=`, which replaces the whole file. No command edits ini keys, and `CREATE DISABLEFLAG` lasts only until restart. A world built for custom races breaks under someone else's ini.

**Add.** Read `WORLDDIR/world.ini` after `coffeemud.ini` and before the command line, for a documented whitelist: `START*` (with `START_MASK`), `DEATH*`, `MORGUE*`, `STARTINGITEMS`, `STARTHP`, `STARTMANA`, `STARTMOVE`, `STARTSTAT`, `AWARERANGE`, the default `TIMEMODE`, calendar keys, race and class allow lists, currency, `CHARCREATIONSCRIPTS`, `DEITYPOLICY`, `FACTIONS`, `MUDTHEME`, `DISABLE`, `ENABLE`, `ADDCHARSTAT_*`, `REPLACECHARSTAT_*`, `NEWACODES`, `NEWDOMAINS`, `DEFAULTABILITYARGS`, `EXPRATE`, `FORMULA_*`, `LASTPLAYERLEVEL`, `MUD_NAME`, `INJURYSYSTEM` and `MANACOST`. Other keys are reported and ignored. In builder mode, `WORLD SET key=value` writes the file and `LIST WORLDINI` shows each value in effect and where it came from. `world.sum` covers the file.

**Example.** `MUD_NAME=The Ashen Reach`, `START=Emberfall#0`, `START_DWARF=Deepforge#12`, `DISABLE=STDRACES,STDCLASSES,AUTOAWARDS` and `HOURSINDAY=12`, committed with the areas.

*See also:* S6, P10 (P10's world/rules.ini, S14 (now in S6)'s rules.ini and S3's world.ini are one world rule file).

<!-- from: builder-commands/bc-world-ini, data-content/dc-world-settings -->

<a id="s4"></a>

## S4. Riding kept in saves and world files

*Kind:* save format · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Area files write `MRIDE` for a mob riding something, and `CoffeeMaker` links it again through `mobRideTable`, so a placed mounted guard loads mounted. An owned vessel's team is hitched again from the character's tables. But `world.xml` records no riding for PLAYER, FOLLOWER, MOB or ITEM entries. A restore leaves the rower in the lake without the logboat, sets the rider down and unhitches the cart. Area XML has no `IRIDE`, so a builder cannot place a wagon that is already hitched to its oxen.

**Add.** `RIDING=<index or name>` on the MOB, PLAYER, FOLLOWER and ITEM entries of `world.xml`. The links are made after everything is placed, as `mobRideTable` does. Add `IRIDE` to item XML in area files. Party members riding with the leader keep their places.

**Example.** A trade road has a `GenCaravan` hitched to two oxen, ready to drive away. The player saves while galloping across a plain on a warhorse and is still mounted after the restore.

*Re-checked against the code after writing: accurate.*

<!-- from: vehicles-travel-instances/vti-save-riding -->

<a id="s5"></a>

## S5. SAVE keeps conquests

*Kind:* save format · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `Conquerable` writes the ruler, the previous ruler, the date and the clan items to CMPDAT (`CONQITEMS`), but only on shutdown or room reset. `declareWinner` writes nothing, and control points live only in memory. `SaveGame.save` writes the players, the clocks and `world.xml`, but it never asks legal behaviours to write their state, and QUIT saves before the shutdown write. A conquest made since the last reset is therefore lost.

**Add.** At SAVE, every `LegalBehavior` writes its state: ruler, previous ruler, conquest date, control points per clan, revolt counters and clan items. The state goes into CMPDAT before the tables are copied, or into `world.xml`. RESTORE puts it back before ticks start. Law warrants get the same treatment if they are affected (see S12).

**Example.** The party's Gray Company takes the bandit fort and saves. After a restore the fort is still theirs, with its control points, and the bandits have not quietly taken it back.

*See also:* F3 (S5 saves the control points that F3's MPCONQUER changes); S12 (S5 says law warrants should be saved the same way as conquests, which is S12).

<!-- from: organizations-factions/org-conquest-in-save -->

<a id="s6"></a>

## S6. A world/resources overlay for every data file

*Kind:* world option, data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `Resources.makeFileResourceName` puts `resources/` in front of every path. So all of these sit among the stock files: Scriptable and quest `LOAD=`, `MPSCRIPT LOAD=`, `IMPORT MOBS`, RandomMonsters and RandomItems files, `movingroom.xml`, the generator entry points (`GENERATE FROM`, RandomQuests `PATH`, `GENERATIONFILEPATH`, `FROMGENFILE`), `FILE=` texts, chat files, help (`MUDHelp` lists `resources/help`), factions, recipes, achievements, titles, socials and the `text/` screens. The shipped `world/quests/holidays.quest` is only a `LOAD=` back into `resources/`. `CREATE HELP`, achievements and holidays write into `resources/`. In a normal game the same commands change it for good, and the building warning does not say so. Some routes exist, but each is partial: inline parameters and scripts, a quest's `<FILE>` blocks, `LOAD="/world/..."` inside generator XML, a `LISTFILE` list, `PRIVATERESOURCES` (one file at a time), class-path lines that name `world/classes/`, and extension files `.2` to `.9` (in `resources/` only).

**Add.** `WORLDDIR/resources/`, laid out like `resources/` and searched first, also by direct `CMFile` reads such as `CharCreation`'s. A file with a stock file's name replaces it, and `<name>.add` appends to it. World help files merge after the stock help. The world `lists.ini` joins `LISTFILE`. World translation and parser rules run before the stock ones, and `#STOCK OFF` drops the stock rules for a section. `WORLDDIR/classes/*` join the class paths at boot. `CHATDEFAULT=<file|NONE>` replaces the stock `chat.dat` default group. A recipe may name `CATALOG:name` as its result. In builder mode, the HELP, ACHIEVEMENT, TITLE, AWARD, FACTION, SOCIAL, HOLIDAY, EXPERTISE, COMPONENT and PLANE editors write here. In a normal game those editors are refused or kept in the save only, and the warning says which. Quest export keeps a quest's `world/quests/<name>/` folder with it. If WORLDCHECK and DEVWATCH are adopted, they cover the overlay, and WORLDCHECK lists files that shadow stock ones. These files are read from disk when used, so editing them needs no cache rebuild.

**Example.** A world ships `progs/ferryman.script`, `help/lore.ini`, `factions/house_varn.ini`, `skills/blacksmith.txt.add` with three recipes, and `text/intro.txt`, all under `world/resources/`.

**Also merged here, from S14 (Progression files and rule keys that travel with the world).** `world/rules.ini` overlays a whitelist of these rule keys. World copies of `achievements.ini`, `titles.ini`, `awards.txt`, `factions/*.ini`, `skills/allqualifylist.txt`, `skills/expertises.txt`, `lists.ini` (merged key by key, as `LISTFILE` already allows), `text/*.txt` and `classes/*.js` (loaded automatically) override the stock ones. Builder-mode editors write the world copies, as `PRIVATERESOURCEPATH` already makes them do for listed files. They need not count in `world.sum`, since they are not cached in the database. This part is the list of progression files and keys that S6 and S3 must cover, and `rules.ini` is the same file as `world.ini`.

*See also:* JS5 (Two ways to keep scripts and JS classes in world/: JS5's world: prefix and world/js class folders, or S6's overlay and WORLDDIR/classes); L9, S20 (Three designs for world socials and dropping stock ones (vocab/socials files, the world resources overlay, DISABLE=STDSOCIALS)); L14 (Both load world help after the stock files and point the help editors at the world (vocab/help vs world/resources/help)); S3, P10 (P10's world/rules.ini, S14 (now in S6)'s rules.ini and S3's world.ini are one world rule file).

<!-- from: quests/qst-world-folders, behaviors-world/bw-worlddata, builder-commands/bc-world-overlay, generators/world-generators-dir, data-content/dc-world-data-overlay, items-puzzles/ip-world-recipes, npc-dialogue/dlg-world-chatfiles, sp-dev-tooling/spdev-world-resources, presentation/pr-world-text-overlay, vocabulary-languages/vl-world-vocab-folder, character-progression/cp-world-rules-files -->

<a id="s7"></a>

## S7. Script engine state in the save: ONCE, TIME, DAY, DELAY, alarms, local variables

*Kind:* script trigger, save format, debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A restore builds every script engine from scratch. ONCE_PROG fires again, TIME_PROG and DAY_PROG can fire twice in the same hour, and DELAY_PROG countdowns, pending MPALARMs and MPNOTRIGGER cooldowns are lost, because `oncesDone`, `lastTimeProgsDone`, `lastDayProgsDone`, `delayTargetTimes`, `que` and `noTrigger` are kept in memory only. INDIVIDUAL-scope variables (`MPSETINTERNAL SCOPE *`, `MPSCRIPT INDIVIDUAL`) on NPCs, items and rooms are also lost, though the guide says they are saved. `getLocalVarXML` exists, but `WorldState` never calls it. The idiom that works today is to guard the body with a saved variable: `IF VAR($i DONE == 1)` ... `MPSETVAR $i DONE 1`. It fails for objects that share a name.

**Add.** A `<SCRIPTSTATE>` in `world.xml` for each scripted mob, item and room. It holds the local variables, which ONCE_PROGs and QUEST_TIME_PROGs are done, when each TIME_PROG and DAY_PROG last fired, the DELAY countdowns, queued MPALARMs (trigger, line, and source and target by name or room ID) and the cooldowns, with times as game time left. It is keyed by a hash of the script text, so editing a script drops its stale state. It is restored before the first tick. ONCE_PROG then means once per game, and `ONCE_PROG BOOT` means once per load. `SAVES CHECK` lists what a save would not keep: summoned mobs without a leader, space objects, and unsavable hosts with scripts. The guide documents the idiom and drops the wrong line.

**Example.** The throne room's ONCE_PROG loads the lich king and echoes 'The crypt doors slam shut!'. The player kills him, saves and restores. The doors do not slam again and the lich stays dead.

*See also:* ST24 (ST24 wants its saved next-fire time to share S7's saved script state (ONCE, DELAY, alarms)); SC4 (S7 saves the INDIVIDUAL-scope variables that SC4 says a save drops; SC4's per-object store is the other answer to the same loss); SC7 (SC7's saved pending waits and S7's saved queued alarms belong in one script-state section of the save); S1 (S1's SONCE (ONCE_PROGs already run in SAVABLE scripts) is S7's script-state save applied to player scripts); C5 (Both save pending MPALARMs (C5's named alarms, S7's queued alarms)).

<!-- from: mobprog-triggers/mt-once-prog-saved, mobprog-language/mpl-save-script-state, sp-dev-tooling/spdev-script-state-save -->

<a id="s8"></a>

## S8. Generated quests survive save and restore

*Kind:* quest feature, generator · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `RandomQuests` marks each quest it makes as a copy (`setCopy(true)`), and both `questsXML` and `setQuests` skip copies. On RESTORE the quest is gone, and `getPlayerPersistentQuests` ends and deletes the player's accepted script as an orphan. `Prop_QuestGiver QUEST="GENERATE:..."` keeps its quest only in a field, so it generates a different one. A quest made with GENERATE QUEST in a normal game is lost as well: it is not in the content, so the restore skips it. PLAN 4d lists only spawned copies of spawnable quests as a known gap.

**Add.** The save's `<QUESTS>` holds each quest made at run time with a `GENERATED` flag, its script text, its generator vars, its host (the mob or area that offered it), its step, its time left, and its quest mobs and items. RESTORE rebuilds and starts these quests from their scripts, then applies their state. `RandomQuests` and `Prop_QuestGiver` take them back by name instead of generating new ones.

**Example.** Elder Maren's generated quest 'Kill 6 marsh trolls' is still active after a restore, at 4 of 6.

**Also merged here, from CR23 (Craft orders: RandomQuests orders that survive saves).** No new CraftOrders behaviour. Instead, save and restore RandomQuests quests with the game. Document the `source_faction` and `source_faction_amt` variables. Optionally add an order target by recipe name that ignores the material.

*See also:* Q14, G2 (Q14's quest board needs G2's RandomQuests fix and S8's saved generated quests).

<!-- from: quests/qst-generated-save, generators/gen-persist-quests, crafting-gathering-housing/cgh-craft-orders -->

<a id="s9"></a>

## S9. A save hook for behaviour state: contracts, bribes, routes, dialogue

*Kind:* behaviour, save format · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The save keeps a mob's `TEXT` (its behaviour parameters), stats, HP, inventory and effects, but not the state its behaviours build up while running. A restore loses all of it. A `Hireling` loses its contract (`workingFor`, `onTheJobUntil`, `partials`); with `onTheJobUntil` at 0 its tick returns at once, so it follows for free forever. Also lost: `Aggressive` bribes, a `BribeGateGuard` tab, a `Patroller` step, `MOBEater`'s stomach, `Emoter`'s expiry, `MudChat` group switches, the lines `QuestChat` has said, the offers `Prop_QuestGiver` has made, and the LLM's memory. `StdBehavior` has only the CLASS and TEXT stats. A Scriptable NPC can keep a contract in a timed tattoo, but the built-in behaviours cannot.

**Add.** `getStat("STATE")` returns the runtime state of a behaviour or property, and `setStat("STATE", xml)` takes it back. The `StdBehavior` default is empty. `WorldState` writes `<BSTATE ID=...>` on saved mobs, items and rooms, with times as game time left, and restores it after load. Implement it for Hireling, Aggressive, BribeGateGuard, Patroller, Decay, Emoter, Concierge, Taxi, Nanny, MOBEater, MudChat (groups per speaker), QuestChat, Prop_QuestGiver and the LLM session (the last N exchanges, replayed as context). New behaviours (Schedule, Companion, BossPhases, Morale) must have it. State that S1 moves into quest vars needs no hook.

**Example.** Hire a sellsword for 2 game hours, save after one hour and restore. He says 'I've still got an hour on your coin' and leaves when the hour runs out.

*See also:* N11 (N11's per-speaker MudChat groups are saved through S9's behaviour STATE hook); N21 (N21's saved Hireling contract is S9's Hireling STATE); N26 (later) (N26 (later)'s saved LLM transcript is S9's LLM session state); S17, T16 (T16 saves MovingRoom's position, which S17 assigns to S9's STATE stat); DT19 (S9's getStat(STATE) and DT19's getStat(DEBUG) both expose a behaviour's runtime state; design them together); S1 (S1 moves Prop_QuestGiver offers and QuestChat lines into quest vars, which S9 says then need no STATE hook).

<!-- from: behaviors-npc/npc-state-save, npc-dialogue/dlg-persist-state -->

<a id="s10"></a>

## S10. Templates, NPC clans, cron jobs, board posts and VFS files in world/

*Kind:* world option, builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Only the content tables become world files and reach a new game: areas, rooms, exits, room mobs and items, custom races, classes, abilities and commands, and quests. Builder templates are kept in CMPDAT, NPC clans in CMCLAN, `CREATE CRON` jobs and notice-board posts in CMJRNL, and files written with SHELL `::` in CMVFS. A new normal game starts with all of these empty. They live only in the cache, which is not in git, and EXPORT has no option for clans, journals or templates. The only workaround is a script that makes them again at game start.

**Add.** `WorldFiles` writes and reads four more kinds of file. `world/templates.cmare` holds all templates, shared, not tied to a builder's name. `world/clans.cmare` holds clans with no player members. `world/journals/NAME.xml` holds journals named by a world `StdJournal` item, plus `SYSTEM_CRON`. `world/vfs/...` holds one file per VFS entry. `exportAll` writes them, the rebuild imports them, and a new game copies them in as content. Saves then add to them.

**Example.** The builder writes five lore posts on the town notice board and creates an 'Orc Horde' clan that conquers the Plains. A new game has both.

*See also:* I3 (Both put book and board pages in world/journals (seed text files vs journal XML)); F7, F18 (F7 and S10 both add world/clans.cmare, which F18's CREATE GUILD writes); P8 (Both use world/templates.cmare, for player templates and for builder templates); S21 (Both move CREATE CRON jobs into world/ (as the SYSTEM_CRON journal vs world/cron files on game time)).

<!-- from: builder-commands/bc-content-not-in-files -->

<a id="s11"></a>

## S11. One file per custom object, with scripts beside it

*Kind:* data file, builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** All custom races, classes, abilities and commands sit in one file, `world/custom.cmare`. `GenAbility.getAllXML` escapes every field, so a script with `<SCRIPT>` JS blocks or `<` comparisons is stored as `&lt;` text that is hard to edit, diff or review. `LOAD=` in a GenAbility or GenCommand script reads from `resources/`, outside the world. Only quest scripts can use the quest file's embedded `<FILE>` sections.

**Add.** `WorldFiles` writes and reads one file per object: `world/custom/races/<ID>.cmare`, and likewise under `classes/`, `abilities/` and `commands/`. A SCRIPT field longer than one line goes to a sidecar file, `<ID>.script` (or `<ID>.js` for pure JavaScript). The field refers to it as `SCRIPT=FILE:<ID>.script`, and the file is read raw, with no escaping. The old single `custom.cmare` is still read, and the checksum covers all these files.

**Example.** `world/custom/abilities/Spell_Emberbrand.cmare` holds the fields and `Spell_Emberbrand.script` holds a readable 40-line script. A git diff shows which line of the spell changed.

*See also:* S25 (Both make world files readable (scripts in sidecar files vs expanded CDATA)); B3 (B3's EXTRACT SCRIPT and S11's sidecar files both move inline scripts into their own files).

<!-- from: data-content/dc-custom-split -->

<a id="s12"></a>

## S12. Warrants, fines and criminal records in the save

*Kind:* save format · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Warrants and old warrants (which mark repeat offenders) are lists kept in memory in `DefaultLawSet`, and fines are a table keyed by the MOB object. `world.xml` has no section for law, so a save and restore wipes a murder warrant or an unpaid fine. A punishment already under way survives, because Prisoner, Shaming and Banishment are timed effects and are saved with their remaining ticks.

**Add.** A `<LAW>` section in `WorldState`, holding for each legal area: each warrant (criminal, victim, witness, crime, punishment, state, offence count, and last offence in game time), the old warrants, and the fines owed. They are restored once the characters are back. Fines are keyed again by character name, since the MOB objects are new.

**Example.** The player kills a guard in Riverton and saves. After a restore the guards still hunt them, and the TaxCollector still demands the unpaid fine.

*See also:* S5 (S5 says law warrants should be saved the same way as conquests, which is S12).

<!-- from: world-systems/ws-law-save -->

<a id="s13"></a>

## S13. World version in saves, a compatibility report and RESTORE ... FRESH=

*Kind:* builder tool, debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A save does not record which world it was made with. After world edits, a restore silently drops the contents of rooms that no longer exist. A restore empties every room and rebuilds each saved mob and item from its saved TEXT. So mobs and items added since the save never appear in a long playtest save, and restored ones keep their old behaviours, properties and inline scripts. Only `LOAD=` scripts pick up edits. The workaround is a new game.

**Add.** `header.properties` holds the world checksum and one checksum per area. Restoring a save from another world prints a report: areas changed, saved rooms missing and what was in them, mobs and items of unknown class, and quests gone or changed. `RESTORE name FRESH=CHANGED|area1,area2|ALL` takes the room contents of those areas from the current world. The player, what they carry, the party, quests and variables stay as saved, and quest-owned things are left to the quest. `SAVES` marks older saves '(older world)'.

**Example.** After adding a quest-giver to the Tavern, `RESTORE act2 FRESH=Town` lets the 15-hour playtest save meet her.

*See also:* S23 (Both show which world version a save was made with (a compatibility report vs SAVES INFO)).

<!-- from: sp-dev-tooling/spdev-save-compat -->

<a id="s15"></a>

## S15. Vessels that save and restore exactly

*Kind:* save format · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** These faults come from reading the code; a run has not confirmed them yet. `WorldState.capture` writes a placed vessel's interior twice: once in the ship item's TEXT (`SSAREA`, with crew and cargo) and again as ROOM entries, because the room list includes ship interiors. Restore rebuilds the ship from its TEXT, then the ROOM entries add the same mobs and items again. A vessel the player bought or built is a room item in `world.xml` and also a row in the character's item table, so two ships can share interior room IDs. Course, anchor, facing and the ship followed are plain fields, and `anchorDown` starts true, so a ship under way stops. A builder cannot work around this.

**Add.** Write the interior once: skip the ROOM entries for boardable areas, or build the ship empty and let the ROOM entries fill it. Leave out room items the character tables already save (owned boardables and titles). Add a `VESSEL` tag for each navigable item with `ANCHOR`, `COURSE`, `FACING`, `FOLLOWING` and `TICKSSINCEMOVE`, applied after the ship is rebuilt. The smoke test saves and restores a crewed ship under way.

**Example.** 'The Gull' is a ferry with a captain and two sailors on a Patroller route. Saved mid-crossing and restored, it still has three crew and still sails for Port Azure.

*See also:* T11 (Both deal with vessels whose interiors share room IDs (copies in T11, double-saved owned ships in S15)).

<!-- from: vehicles-travel-instances/vti-save-vessels -->

<a id="s16"></a>

## S16. Crops and crafts in progress survive a save

*Kind:* save rule · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A planted crop is a Farming effect on the room; a craft in progress is an effect on the crafter. A save keeps only the effect's ID, TEXT, ticks and proficiency (WorldState.putEffects). CommonSkill's stat codes are TICKUP, PCTREMAIN and NAME, with nothing for Farming's crop item, room or good ticks, or CraftingSkill's buildingI. After a restore the crop fails at its sixth tick, and a craft ends with nothing though its materials were spent. SAVE does not check for activities in progress.

**Add.** A CommonSkill stat code, ACTIVITYXML: the found or building item as XML, the activity room ID, tickUp, goodticks and the recipe line. WorldState.effectsXML writes it for common skills and putEffects reads it back. For skills that lack it, SAVE finishes or cancels personal crafting, refunds the materials, and says so.

**Example.** The player sows moonwheat in the home garden, saves and quits. Next session they restore, wait a few game hours and harvest it.

<!-- from: crafting-gathering-housing/cgh-save-activities -->

<a id="s17"></a>

## S17. Rooms and areas changed in play survive saves

*Kind:* save format · *Value:* high · *Effort:* large · *Decision:* yes

**Today.** A save keeps room contents, door states, timed effects and area weather. The room tables are content, rebuilt from `world/` at boot. So RESTORE undoes `MPSET` of DISPLAY, DESCRIPTION, ATMOSPHERE or CLIMATE, `MPBEHAVE`, lasting properties from `MPAFFECT`, `MPLINK` exits, `FieryRoom`'s charred text and `Chant_Flood`'s atmosphere. It also undoes building in a normal game: a `Prop_RoomForSale` owner, new titles, walls, doors, lock keys and `Prop_RoomView`. Rooms made by STAIRS, EXCAVATE or lot expansion are unknown at boot, so their contents are skipped, and the lot, now unowned, gives the items on its floor expiry times. `TemporaryAffects` comes back with empty text. The workaround is to keep state in script variables and branch the text with `<MASK -IF "VAR(...)">` in VARIES.

**Add.** At boot, fingerprint each room and area: title, description, class, climate, atmosphere, behaviours with their parameters, lasting effects and exits. Also mark rooms that play writes through `DBUpdateRoom`, `DBUpdateExits`, `DBCreateRoom` or `DBDeleteRoom`. SAVE writes a `<ROOMSHAPES>` section: the full room XML of each changed or created room, the area changes, and the IDs of destroyed rooms. RESTORE creates, replaces and deletes rooms and links exits before it puts contents back. S9's STATE stat covers `FieryRoom`'s burn ticks, `Emoter`'s EXPIRES and `MovingRoom`'s position, and `TemporaryAffects` saves its text. A character's start room that was built in play then still exists after a restore.

**Example.** The burnt bandit camp stays 'A Charred Ruin', with the cellar exit made by `MPLINK`. A bought 'Millhouse' keeps its new rooms, its lock, its owner and the loot stored in it.

*See also:* S9, T16 (T16 saves MovingRoom's position, which S17 assigns to S9's STATE stat); I13, W7 (I13's cleared blockages and W7's room states are kept through S17's room-delta save).

<!-- from: behaviors-world/bw-save-roomdelta, presentation/pr-room-state-persist, crafting-gathering-housing/cgh-save-room-shape -->

<a id="s18"></a>

## S18. Generated dungeons survive a save

*Kind:* generator and save rule · *Value:* high · *Effort:* large · *Decision:* yes

**Today.** In builder mode, GENERATE AREA output is ordinary content and goes to world/areas/*.cmare on SAVE or quit, so a dungeon baked while building persists. In play it does not. Rooms made by GENERATE, StdAutoGenInstance instances and AutoGrid children are written to the save, but WorldState.apply drops every saved room whose ID no longer exists. StdAutoGenInstance's OTHERVARS has PASSIVEMINS=\<n> and NORESET=true, but neither survives a save.

**Add.** A \<GENERATED> entry in the save's world.xml for each generated area or room set: generator file, tag ID, variables, seed (from gen-seed), gate room and direction. On restore, regenerate each with its seed before room state is applied, so dead mobs, taken items and open doors match. PERSIST=true in OTHERVARS, reusing PASSIVEMINS, keeps one instance per party. The gen-seed reproducibility caveats apply.

**Example.** A Shifting Mines entrance uses StdAutoGenInstance with PERSIST=true. The player clears half the mine, saves, quits and restores: same corridors, same dead goblins.

*See also:* G1 (S18 regenerates generated areas from their seed on restore, and S18 and T13 (now in G1) both propose PERSIST=true for one instance per save).

<!-- from: generators/gen-persist-areas -->

<a id="s19"></a>

## S19. Instances and planes survive a save

*Kind:* save rule · *Value:* high · *Effort:* large · *Decision:* yes

**Today.** Saving inside an instance child (StdThinInstance, InstanceArea, StdAutoGenInstance) or on a plane stores the character's start room as the location (MOBloader), so a restore puts them at home. Party members keep the instance room ID in WorldState's PLAYER ROOM. Party.bringIn finds nothing there and leaves each one where they last were, so the party splits. Child rooms are written but dropped on restore: child names come from in-memory counters (instIDNum, instanceCounter, planeIDNum) and nothing re-creates a child. Dead bosses and opened chests are lost. Builders have no workaround.

**Add.** Capture each live child in world.xml: name, parent area, instance or planar type ID and parameters, members, ticks left (childTimeout or DURATION), and the visited rooms. On restore, rebuild each child under the same name (the createRedirectArea or SubThinInstance path) before rooms are applied. Then save the real instance room as the location.

**Example.** Midway through a Triumph run of the Sunken Crypt, the player saves and quits. On restore they stand in the same corridor. The lich's lieutenant is still dead and the timer still runs.

*See also:* G1 (T13 (now in G1)'s ENTRY seed needs S19's saved instances, as T13 (now in G1) says); S22 (Both keep instance state across a restore (instance children vs entry limits)).

<!-- from: vehicles-travel-instances/vti-save-instances -->

<a id="s20"></a>

## S20. Opt out of stock achievements, titles and socials; wildcard disables

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A custom world inherits about 1,200 stock achievements, the stock titles.ini and 338 socials. DISABLE has STDRACES, STDCLASSES and AUTOAWARDS (all awards.txt auto-awards, such as the monthly XP bonuses tied to stock classes; NPCAUTOAWARDS for NPCs only). RACE_, CHARCLASS_, ABILITY_, COMMAND_ and FACTION_ disable exact IDs. Only EXPERTISE_ takes a trailing *, so hiding a spell family means listing hundreds of IDs. Holidays are already content: deleting world/quests/holidays.quest turns them off. Classes keep stock spells from players, but scrolls, wands and NPC casters still use them.

**Add.** DISABLE flags STDACHIEVEMENTS, STDTITLES and STDSOCIALS in world.ini. Each skips the stock file and keeps only the world's own. They need world/ to hold its own copies of those files, which is a separate item. Add wildcards to isAbilityDisabled, isCommandDisabled and isRaceDisabled, for example ABILITY_Spell_*.

**Example.** A low-magic world sets DISABLE=STDACHIEVEMENTS,AUTOAWARDS,ABILITY_Spell_*,ABILITY_Chant_*. Its own 12 GenAbility rituals and its own achievements remain.

*See also:* L9, S6 (Three designs for world socials and dropping stock ones (vocab/socials files, the world resources overlay, DISABLE=STDSOCIALS)).

<!-- from: data-content/dc-stock-opt-out -->

<a id="s21"></a>

## S21. World cron jobs in files, on the game clock

*Kind:* world option and data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** CREATE CRON makes an interval script job. It goes in the SYSTEM_CRON journal, which is not a world content table, so it never reaches world/. CMJournals times it with System.currentTimeMillis, so it fires while the game is paused and ignores turn-based time. Game-clock events already work two other ways, both in world/: DAY_PROG and TIME_PROG on an area or room Scriptable, and quests in world/quests/ that repeat with SET WAIT and SET DURATION.

**Add.** Either move CRON to the game clock and world files, or retire it and document the alternatives. Moving it means world/cron/\<name>.cron files with INTERVAL in game time ("1 day", "6 hours", parsed by parseTickExpression), an optional MASK on the world state, and SCRIPT. They run on GameTime, and the save keeps each job's next run. In builder mode CREATE and MODIFY CRON write these files.

**Example.** world/cron/market_day.cron fills the Emberfall market with travelling merchants every 7 game days. Today, DAY_PROG 1 8 15 22 on the area comes close.

*See also:* ST24, JS19 (Three ways to run something on a game-clock interval that survives saves; ST24 also suggests moving cron to GameTime, which is S21); S10 (Both move CREATE CRON jobs into world/ (as the SYSTEM_CRON journal vs world/cron files on game time)).

<!-- from: data-content/dc-world-cron -->

<a id="s22"></a>

## S22. Save instance limits and planar visit timers

*Kind:* save rule · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** InstanceArea's LIMIT (for example 1/DAY) keeps its entry counters in the character's class variable map (getClassVariableMap), timed with GameTime.now(). DefaultPlayerStats never writes that map. Any restore, even of the same save, clears the counters, so the player can farm a once-a-day dungeon by reloading. The planar travel recentVisits map lives on the ability and is not saved either. It only raises difficulty for repeat visits within an hour, so it matters less.

**Add.** Write the LIMIT counters into the character's saved data, either the playerstats XML or the profile's world.xml, and read them back on restore. As a minor follow-up, save recentVisits the same way.

**Example.** A Triumph instance allows one run per game day. It stays locked after the player restores a save made after the run.

*See also:* S19 (Both keep instance state across a restore (instance children vs entry limits)).

<!-- from: vehicles-travel-instances/vti-instance-limits-persist -->

<a id="s23"></a>

## S23. Save tools: info, delete, copy, diff, checkpoints, autosave

*Kind:* debug tool and world option · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** SAVE name, RESTORE name (or RESTORE=name at launch), SAVES and the save offer at QUIT exist, so a checkpoint is SAVE cp1. Each save is a folder saves/NAME/ with header.properties, world.xml and fakedb tables, so the file system can delete or copy one. In game nothing deletes, copies or describes a save (SaveSlots.delete is never called). There is no autosave. Comparing two runs means reading world.xml by hand.

**Add.** SAVES INFO name shows the header, the party with levels and locations, the game date, running quests, script variable count, world version and dev flag. Add SAVES DELETE name (confirmed), SAVES COPY a b and SAVES DIFF a b (stats, inventory, variables, quests, rooms). CHECKPOINT rotates slots _cp1 to _cp3, and ROLLBACK restores the latest. AUTOSAVE=n (game minutes or ON-AREA-CHANGE) works with AUTOSAVEKEEP=3. tools/saveinspect.py prints a slot offline and can set a variable, hit points or position.

**Example.** SAVES DIFF before-boss after-boss shows the boss script never set VAR Hero BOSSDEAD.

*See also:* S2, C7 (Three autosave designs (Prop_AutoSave, AUTOSAVE=n, a checkpoint's SAVE=AUTO), and S23's CHECKPOINT command shares C7's name); S13 (Both show which world version a save was made with (a compatibility report vs SAVES INFO)).

<!-- from: sp-dev-tooling/spdev-save-tools -->

<a id="s24"></a>

## S24. Space objects in world files and saves

*Kind:* data file and save rule · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Ships, stars, moonlets, asteroids and stations placed in space as items, not in a room, are not in world/ (planets and moons built as areas are, with their coordinates). WorldFiles exports areas, custom objects, the catalog and quests only, and PLAN 4f notes that a rebuild loses them. WorldState captures rooms, the interiors of every loaded ship included, docked or not, but not where anything in CMLib.space() is or how it moves. An unowned ship moving through space returns to its content position on restore. Only ships the player or a clan owns survive, with the character's or the clan's tables, through a SPACE.x,y,z room ID. This matters only for a science-fiction world.

**Add.** Export the space contents to world/space.cmare and import it on rebuild: each object's XML with coordinates, speed, facing and interior. The save's world.xml gets a SPACE section with each unowned object's class, TEXT, coordinates, speed and direction, restored before the player logs in.

**Example.** The builder places a derelict station orbiting the gas giant and a patrol cutter on its course. Both survive a rebuild and a save and restore.

*Re-checked against the code after writing: corrected.*

<!-- from: vehicles-travel-instances/vti-space-in-files -->

<a id="s25"></a>

## S25. Readable world files and a world diff

*Kind:* data file and builder tool · *Value:* medium · *Effort:* large · *Decision:* yes

**Today.** In .cmare files a room's, mob's or item's own data (RTEXT, MTEXT, ITEXT: descriptions, behaviours, effects, scripts, Prop_ parameters) is entity-escaped XML on one line, and base stats are a pipe-separated PROP array. WorldFiles.layOut gives one tag per line only to the outer XML. Scripts already have a clean route: Scriptable with LOAD=progs/x.script keeps the script in its own file. Property and behaviour parameters have none, and Shell's COMPAREFILES is a plain text diff.

**Add.** layOut expands RTEXT, MTEXT, ITEXT, EXDAT and CATADATA into indented child elements marked xml="1", with inline scripts in CDATA. The reader escapes them back for CoffeeMaker, so old files still load. PROP can optionally be written as named attributes when it reads back exactly. The round-trip test stays. tools/worlddiff compares two revisions or folders of world/ by object: "Town#12: description changed; mob baker: +Scriptable".

**Example.** The owner fixes a word in the baker's speech in town.cmare. The git diff is one line instead of a 4 KB escaped blob.

*See also:* S11 (Both make world files readable (scripts in sidecar files vs expanded CDATA)); B2 (Both add a world diff (tools/worlddiff vs WORLD DIFF against the export)).

<!-- from: builder-commands/bc-readable-world-files, sp-dev-tooling/spdev-readable-files -->
