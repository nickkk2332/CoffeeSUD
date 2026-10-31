# Builder tools

Commands and editors for building in builder mode: tracking and protecting changes, reload, undo, editing, digging, mass changes, macros and sandbox. Part of the [builder capabilities audit](README.md).

<a id="b1"></a>

## B1. SAVE ROOM HOME: save each mob into its own start room

*Kind:* builder tool · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** `SAVE ROOM` saves every savable mob standing in the room, with no start-room check (`DBUpdateMOBs`), so a mob that wandered in from elsewhere is saved a second time. `clearDebri`, which runs first, destroys this room's own mobs that have wandered out, so a new mob that walked off is lost. `RESET ROOM` sends strays home but drops unsaved work. Mobs can already be kept still with `PAUSE AREA` or `CREATE DISABLEFLAG MOBILITY` (`DISABLE=MOBILITY` in coffeemud.ini), so no FREEZE command is needed.

**Add.** `SAVE ROOM HOME`, the default in builder mode. Each mob is saved into its `getStartRoom()`, wherever it stands. Mobs whose home is elsewhere are left out and listed. This room's own wanderers are saved here, not destroyed.

**Example.** A guard wanders from Gate#1 into the tavern as the builder types `SAVE ROOM` there. Today the next start has two guards. With HOME the guard stays saved at Gate#1, and the save reports "skipped: the gate guard (home Gate#1)".

<!-- from: builder-commands/bc-startroom-safe-save -->

<a id="b2"></a>

## B2. Protect hand edits: per-file checks, WORLD STATUS and WORLD DIFF

*Kind:* builder tool · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** On `SAVE` and on quit, builder mode rewrites each world file whose generated text differs from the disk (`exportAll`, `writeOrDelete`). It never checks whether the file changed since boot, so a .cmare or .quest file edited by hand during the session is replaced by the older database copy. `writeFolder` deletes, without asking, any such file with no live object, even one just added by hand. `bootSum` covers the whole world, not each file. Only git guards this today.

**Add.** Record each file's checksum at boot and after each export. A file changed on disk since then is not overwritten or deleted: the export goes to NAME.cmare.builder beside it, with the warning "areas/castle.cmare was edited outside the game: WORLD RELOAD castle, or SAVE FORCE to overwrite". `WORLD STATUS` lists files edited outside the game and files the next export would write or delete. `WORLD DIFF file` shows a line diff against what the export would write.

**Example.** The builder fixes twenty descriptions in castle.cmare during a session, then saves a room. Today the fixes vanish. With this, `SAVE` warns, and `WORLD DIFF castle` shows the changed lines.

*See also:* S25 (Both add a world diff (tools/worlddiff vs WORLD DIFF against the export)); DT3 (Both stop builder SAVE overwriting a world file that is not what was loaded, with SAVE FORCE to override); B5 (B5's WORLD RELOAD CHANGED works from B2's per-file checksums and WORLD STATUS); B6 (Both change how exportAll overwrites and deletes world files (refuse vs back up)).

<!-- from: builder-commands/bc-file-conflict-guard, sp-dev-tooling/spdev-builder-conflict -->

<a id="b3"></a>

## B3. Multi-line editing of scripts and descriptions

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Behaviour parameters (an inline Scriptable script among them), descriptions (`genDescription`) and the GenAbility and GenTrap SCRIPT fields (`promptStatStr`) are one-line prompts, with ';' and '~' separators. The line editor (`CMJournals.makeMessage`, through `promptStatMsg`) serves only GenCommand. It labels its SCRIPT field "Help Text" and wants a literal FUNCTION_PROG EXECUTE line, so a script that is only `LOAD=progs/x.script` is refused. The usual workaround is a file in resources/progs attached with `LOAD=`.

**Add.** Use `promptStatMsg` for Scriptable parameters, long descriptions, quest scripts and every SCRIPT field. Label GenCommand's field "Script", expand `LOAD=` (`parseLoads`) before its check, and show parse errors on save. `EDIT SCRIPT <target> (N)` opens the Nth script, `EDIT DESC <target>` the description. Optional `EDITOR=WINDOW` (a Swing text area) or `EDITOR=EXTERNAL` ($EDITOR on a temporary file). `EXTRACT SCRIPT <target>` moves an inline script to progs/NAME.script (in the world folder once it holds scripts) and replaces it with `LOAD=`.

**Example.** `EDIT SCRIPT the innkeeper` shows the 30-line greeting script line by line. `.r 12` replaces line 12 and `.s` saves. `EXTRACT SCRIPT` then lets the other innkeepers share it.

*See also:* S11 (B3's EXTRACT SCRIPT and S11's sidecar files both move inline scripts into their own files).

<!-- from: builder-commands/bc-script-editor, data-content/dc-editor-script-fields -->

<a id="b4"></a>

## B4. Track unsaved changes: LIST UNSAVED, SAVE CHANGED, warning on quit

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `CREATE`, `MODIFY` and `DESTROY` of mobs and items, and `TRANSFER`, change only the live room. World files are written from the database copy (`getRoomXML`), so anything not followed by `SAVE ROOM` is lost on `SAVE` or quit. `GMODIFY` with `CHANGE=` resets each live room first, dropping unsaved work. `SAVE ROOM` reports the count change, `SAVE AREA` saves a whole area and `LIST CONTENTS` shows what the files will get. Nothing lists which rooms differ.

**Add.** Mark a room dirty when a building command (Create, Modify, Destroy, Copy, Transfer, Restring, Every, MQL UPDATE) touches it or its contents. `LIST UNSAVED` shows each such room, what changed and +N/-N counts (`DBCountRoomMobsItems`). `SAVE CHANGED` saves exactly those rooms. QUIT, plain `SAVE` and `GMODIFY CHANGE=` warn first. Optional `BUILDERAUTOSAVE=1` saves after each command. A prompt code `%U` shows the count.

**Example.** The builder creates a blacksmith and two anvils in Town#12 and quits. QUIT asks "1 room has unsaved mobs/items: Town#12 (+1 mob, +2 items). Save them first? (Y/n)".

*See also:* B6 (Both record each building command's change (dirty rooms vs an undo log)); B8 (B8's exit and area changes mark rooms dirty for B4's SAVE CHANGED).

<!-- from: builder-commands/bc-unsaved-tracking -->

<a id="b5"></a>

## B5. WORLD RELOAD of areas, scripts, quests and the catalog, and DEVWATCH

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** World files are read at boot only. `LOAD AREA` reads the database cache, not the file. `IMPORT NOPROMPT /world/areas/castle.cmare` rebuilds an area from its file and relinks other areas' exits, but leaves the builder outside. `UNLOAD FILE progs/x.script` clears a parsed script. `MODIFY QUEST name STOP` then `START` rereads a `LOAD=` target, but not the world/quests/*.quest file. catalog.cmare cannot be imported: WorldFiles writes a layout IMPORT does not read.

**Add.** `WORLD RELOAD`, a front end over these. `AREA name` moves the builder out and back and keeps the new checksum. `SCRIPT file|ALL`, with `RESET` to clear script state. `QUEST name` or `QUESTS` stops the quest, rereads the .quest file and its `LOAD=` target, and restarts it. `CATALOG` reloads in place. `CHANGED` does every file `WORLD STATUS` lists as edited. In a normal game it saves to a temporary slot and restores it. `DEVWATCH ON` polls world/ and resources/ every few seconds and runs the matching reload, noted in DEVLOG.

**Example.** The builder replaces "Lord Varn" with "Lady Varn" across castle.cmare in an editor, types `WORLD RELOAD castle`, and sees the change in the throne room at once.

*See also:* JS10 (Both reload changed files in the running world and add an automatic file watch (JS WATCH, DEVWATCH)); B2 (B5's WORLD RELOAD CHANGED works from B2's per-file checksums and WORLD STATUS); DT2 (Both reload a quest from its world/quests file and LOAD= targets and restart it).

<!-- from: builder-commands/bc-world-reload, sp-dev-tooling/spdev-reload -->

<a id="b6"></a>

## B6. UNDO, HISTORY and backups of world files

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** There is no undo. A wrong `MODIFY`, `DESTROY`, `COPY` or `GMODIFY CHANGE=` is permanent once saved, and `GMODIFY` only writes to Log.sysOut. `writeOrDelete` and `writeFolder` overwrite or delete world files in place, with no backup, so a destroyed area's file goes on the next `SAVE`. `RESET ROOM` or `RESET AREA` reverts unsaved live changes only. Git covers only committed states.

**Add.** Before a building command changes an object, its XML (`getMobXML`, `getItemXML`, `getRoomXML` with content, exit XML) goes into an undo log of the last 50 actions, in memory and in world/.history/. `UNDO (n)` puts changes back. `HISTORY` lists them ("MODIFY MOB the guard @Gate#1"). `exportAll` copies each file it overwrites or deletes to world/.backup/NAME.TIMESTAMP, keeping the last N, with `WORLD BACKUPS` and `WORLD RESTOREFILE name (timestamp)`.

**Example.** The builder runs `gmodify area class=genmob change=level=30`, meaning only the guards, and the whole town is level 30. `UNDO` puts all 40 mobs back as they were.

*See also:* B2 (Both change how exportAll overwrites and deletes world files (refuse vs back up)); B4 (Both record each building command's change (dirty rooms vs an undo log)).

<!-- from: builder-commands/bc-undo-history -->

<a id="b7"></a>

## B7. Effects editor: edit in place, filtered list, duplicates

*Kind:* builder tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** In the effects field of `MODIFY` (`CMGenEditor.genAffects`), typing an existing ID removes it. Changing one parameter means removing the effect and adding it again, starting from the default text, not the current one. '?' lists every ability, highlighting those whose `canAffect` is true, and '?' at the parameters prompt shows the class's help. A second copy of the same Prop is silently ignored (`addNonUninvokableEffect`).

**Add.** '=ID', or picking by number, edits that effect's parameters with the current text filled in. '-ID' removes it. '?P' lists only property classes (`Ability.ACODE_PROPERTY`) and misc effects that can affect this host, each with its one-line help. Adding an ID already present offers to wrap both copies in `Prop_MultiEffects`.

**Example.** Raising a `Prop_WearAdjuster` from str+2 to str+3 becomes one edit: `=Prop_WearAdjuster`, change the 2, done.

<!-- from: properties/pr-effects-editor -->

<a id="b8"></a>

## B8. GMODIFY and MQL UPDATE for exits and areas

*Kind:* builder tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `GMODIFY` already changes and saves an area's own fields (`tryModfy`, `DBUpdateArea`), but it checks field names against mob, item and locale stat codes, so area-only fields may be refused. It has no loop over exits: GenDoor names and door messages cannot be found or changed. MQL can select EXITS and AREAS, but `MODIFY UPDATE:` saves only rooms, items and mobs, so exit and area changes are lost at the next start.

**Add.** Exit stat codes and an exit loop in `GMODIFY`, saving with `DBUpdateExits`, as in `GMODIFY AREA class=GenDoor&&name=[ss]door change=name=[ss]stone door`. Area-only stat codes in its allowed fields. In the save step of `Modify.doMql`, Exit (`DBUpdateExits` on its room) and Area (`DBUpdateArea`), with the rooms marked dirty for `SAVE CHANGED`.

**Example.** Every door in the dwarf hold should read "stone door" and get a new closed message. One `GMODIFY` on the area does it, and it is still there after the next start.

*See also:* B4 (B8's exit and area changes mark rooms dirty for B4's SAVE CHANGED).

<!-- from: builder-commands/bc-gmodify-exits-areas -->

<a id="b9"></a>

## B9. Dig mode and BUILD PATH for fast room creation

*Kind:* builder tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `CREATE ROOM <dir> <type>` takes a locale class or a builder template (`getBuilderTemplateObject`), whose title, description, behaviours, effects and contents are copied. `COPY <dir>` clones the current room (`COPY <roomid> <dir>` clones any room), and `COPY 5 <dir>` lays a straight chain of five linked copies in one command; `LINK` sets exits, and `GENERATE` builds from randareas XML. Still, a path that bends or changes room type takes a command per leg and a walk to its end, and one made from a locale class gets the placeholder '\<Locale>-' and no description. An `ALIAS` with '~'-separated commands only approximates a path.

**Add.** `BUILD DIG ON|OFF (TYPE=locale|template) (LINK=two-way|one-way)`: while on, moving where there is no exit creates a room of that type and links it. Plain `DIG` is taken by the player command (Commands/Dig.java). `BUILD PATH e,e,n,ne (TYPE=x) (NAME="A forest trail") (DESC="...")` lays out a chain in one command. New rooms are marked dirty or saved, and tagged TODO so `WORLD CHECK` lists them until edited.

**Example.** `BUILD DIG ON TYPE=forestpath`, then e, e, n, n, ne: five linked rooms copied from the forestpath template, tagged TODO for their own descriptions later.

*Re-checked against the code after writing: corrected.*

<!-- from: builder-commands/bc-dig-mode -->

<a id="b10"></a>

## B10. MAP of an area, in the terminal and as HTML

*Kind:* presentation · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** The LOOK mini-map shows a few rooms around the player (AWARERANGE, default 3), without room IDs. A GenMap item whose map-areas field (`RoomMap.setMapArea`) names an area can be READ to see the whole area laid out by `StdMap.placeRooms`, but with no IDs and no marks for problems. `LIST LINKAGES` gives the connections as text. The MUDGrinder map went with the web server; its GrinderFlatMap layout survives only in the archive.

**Add.** `MAP (RADIUS n) (AREA) (IDS) (FLAGS)`: an ASCII map from `Skill_RegionalAwareness.getMiniMap` or StdMap's layout, with room-number labels and marks: > one-way exit, + locked door, @ exit to another area, s room with scripts, ! unreachable room. `EXPORT MAP area file.html` writes a static clickable map (names, IDs, exits) from the GrinderFlatMap layout and `getHTMLTable`. Optionally, a Swing window beside the character windows.

**Example.** After `GENERATE` builds a 40-room maze, `MAP AREA IDS` shows that Maze#17 to #22 hang off a one-way exit. `EXPORT MAP` gives maze.html for planning the descriptions.

*See also:* PR9 (Two MAP commands (a player map with fog of war vs a builder map with IDs), both clashing with Skill_Map's word).

<!-- from: builder-commands/bc-builder-map -->

<a id="b11"></a>

## B11. Macro files (RUNFILE) and a builder helper for JRUN

*Kind:* script command and JavaScript hook · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** `ALIAS DEFINE key cmd1~cmd2` gives per-character macros with @x1 arguments. `MPRUN LOAD=macros/shop.script` runs a script's ONCE_PROG lines as the builder's own commands. `JRUN file.js args` offers only mob(), getParm(), getParms(), numParms(), toJavaString() and getCMType(); anything else needs `Packages.com.planet_ink...` boilerplate (see resources/examples/Lemming.js). There is no plain file of commands to replay, and no helpers for common building steps.

**Add.** `RUNFILE name (args)` runs world/macros/NAME.cmd: one game command per line, $1..$9, $room and $area substituted, '#' comments, a `STOPONERROR` option and a summary of failed lines. JRUN gets a `builder` object: `room(id)`, `dig(dir, locale)`, `link(fromId, dir, toId)`, `create('mob', catalogName, roomId)`, `saveRoom(id)`, `forEachRoom(area, fn)` and `cmd('modify room name X')`, so generation scripts are short JS files in the world folder.

**Example.** world/macros/shop.cmd creates a catalog shopkeeper, sets the shop type from $1, stocks three items from catalog category $2 and saves the room. The builder types `RUNFILE shop weaponsmith weapons` in each of four towns.

*See also:* JS6 (B11's JRUN builder object and JS6's cm object both add helpers to the JRUN scope).

<!-- from: builder-commands/bc-macros -->

<a id="b12"></a>

## B12. SANDBOX scratch area and PREVIEW of catalog objects

*Kind:* builder tool · *Value:* low · *Effort:* small · *Decision:* yes / no

**Today.** `GENERATE NOSAVE` skips the database, but the area stays in the live world, is not marked unsavable, and can reach the world files. A StdAutoGenInstance area rebuilds its instance children from a randareas file on each entry, and `exportAll` skips those children, so one generator can be previewed that way. The editor's disposition list does not offer `PhyStats.IS_UNSAVABLE`, so a scratch area cannot be marked unsavable by hand.

**Add.** `SANDBOX (ENTER|LEAVE|CLEAR)`: a builder-only area with a few empty rooms, marked IS_UNSAVABLE, which `exportAll` already skips. `GENERATE ... SANDBOX` puts results there. `PREVIEW <catalog or template name>` makes a temporary copy in the sandbox, shows LOOK, STAT, its equipment and shop list, and destroys it when the builder leaves.

**Example.** The builder runs `GENERATE AREA maze_dungeon FROM randareas/example.xml areasize=30 SANDBOX` five times with different themes, walks each, then generates the chosen one into the real world.

*See also:* G4 (Both preview generated content in a throwaway scratch area); DT17 (Both define a PREVIEW command (catalog objects vs conditional text)).

<!-- from: builder-commands/bc-sandbox-preview -->

<a id="b13"></a>

## B13. AHELP BUILDING and a builder summary at login

*Kind:* presentation · *Value:* low · *Effort:* small · *Decision:* yes / no

**Today.** The only builder-mode help is in the SAVE entry of help.ini, and the login says only "Builder mode makes you an Archon, with the in-game editors." Nothing tells a new builder that mobs and items need `SAVE ROOM`, that hand edits during a session are overwritten, where templates and JS approvals live, or how to check the world. guides/ArchonGuide.html explains saving mobs, but not BUILDER=1 or the world folder.

**Add.** An `AHELP BUILDING` topic: create, `SAVE ROOM` or `SAVE CHANGED`, catalog and templates, `GMODIFY` and MQL, `IMPORT` and `GENERATE`, the world folder and what is not in it, `PLAYTEST`, `WORLD CHECK`, and existing aids (`MPUNLOADSCRIPT`, `IMPORT /world/areas/...`, `LIST CONTENTS`, `LIST LINKAGES`, `PAUSE AREA`, ERRMSGS=BOTH, JSCRIPTS=2, GenMap). At builder login, three lines: whether the world files loaded whole (`WORLD PROBLEMS`), unsaved rooms from last time, and "AHELP BUILDING for the workflow".

**Example.** On a first start with BUILDER=1 the owner reads `AHELP BUILDING`. It says up front that a created mob must be saved with `SAVE ROOM`, the first trap most builders fall into.

*See also:* DT3 (B13's builder login summary shows DT3's WORLD PROBLEMS).

<!-- from: builder-commands/bc-builder-guide -->

<a id="b14"></a>

## B14. CHATDRAFT: LLM drafts of fixed NPC dialogue

*Kind:* builder tool · *Value:* low · *Effort:* small · *Decision:* yes / no

**Today.** In builder mode the `LLM <text>` command (needs LISTADMIN and an installed LLM) answers the Archon, with a session per character. A builder can paste an NPC's description, ask for MudChat entries in chat.dat syntax and copy them into a file by hand. MUDPercolator's LLM="true" values serve random areas only. An LLM at play time would cost money, need the network and change from play to play.

**Add.** `CHATDRAFT <mob> [topics...] [TO world/chat/<file>]` gathers the NPC's name, description, race, area and lore files, prompts the configured LLM, and writes a MudChat group, optionally with a .dlg skeleton. It shows a diff and asks before writing. `CHATDRAFT VARIANTS <file> <entry>` adds alternative replies to an entry. The result is plain data, so the game never needs the LLM at play time.

**Example.** `chatdraft blacksmith weapons armor prices gossip to world/chat/riverford.dat` gives a blacksmith group of four entries, with three to five weighted replies each in his gruff voice, for the owner to trim.

<!-- from: npc-dialogue/dlg-llm-draft -->
