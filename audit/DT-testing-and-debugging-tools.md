# Testing and debugging tools

Checkers and lints, inspectors, tracing and error display, playtest and developer modes, simulators and automated content tests. Part of the [builder capabilities audit](README.md).

<a id="dt1"></a>

## DT1. VARS: list, set, clear and watch script variables

*Kind:* debug tool and script function · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** No command lists script variables. `STAT <mob> SCRIPTS` shows them only for scoped engines on a mob's own scripts, so it is empty for the usual global scope. `LIST RESOURCES` shows the `VARSCOPE-*` keys but not their values. `JCONSOLE setVar` or `MPRUN mpsetvar` (after `mpsetinternal SCOPE GLOBAL`) changes one variable whose host and name you already know. `CREATE DEBUGFLAG SCRIPTVARS` only logs changes to mud.log.

**Add.** A `VARS` command built on `getScriptVarSet`. `VARS LIST [scope|GLOBAL|QUEST name] [host] [mask]` lists scope, host, name and value, including the individual-scope stores of live engines. Also `VARS SCOPES`, `VARS SET host name value [SCOPE s]`, `VARS CLEAR host [name|*]`, `VARS WATCH host name` and `VARS UNWATCH` (each change reported with the host and script file), and `VARS DUMP file` to compare two runs. Add the script function `$%VARNAMES(host prefix)%`.

**Example.** `VARS LIST * Hero QUEST_*` shows the hero's quest flags. `VARS SET Hero ACT 3` starts a playtest at the third act. `VARS WATCH * DRAGONMOOD` shows which script keeps resetting it.

<!-- from: mobprog-language/mpl-vars-tool, sp-dev-tooling/spdev-vars -->

<a id="dt2"></a>

## DT2. QUESTINFO and quest test controls

*Kind:* builder tool and quest feature · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `LIST QUESTS` shows only running, waiting or disabled. `Quest.getLastErrors` has no caller, and quest errors go to mud.log. Details can be dug out one at a time: `$%QVAR(bell STEPNUM)%` and `MOB_ROOMID` read the step and the mob's room; `MPRUN` with `mpstepquest`, `mpquestwin` or `mpqset` changes them; `JCONSOLE` can call the quest object. `MODIFY QUEST` STOP and START cannot restart at the current step, and STOP deletes an unscheduled quest.

**Add.** `QUESTINFO <quest>` on one screen: state, step number and label, time and wait remaining, quest vars, the designated MOB, ITEM, ROOM, AREA and groups with room IDs, the objects it holds and the scripts, behaviours and effects it added, winners with status, and `getLastErrors()`. New `MODIFY QUEST` verbs: `STEP <n|label>`, `WIN <char> (OUTCOME w)`, `UNWIN <char>`, `RESETWINNERS`, `VAR <name> <value>`, and `RELOAD`, which re-reads `world/quests/<name>.quest` and its LOAD= files, clears the parsed-script cache and restarts at the current step.

**Example.** `MODIFY QUEST bell STEP 4`, then `QUESTINFO bell` shows the ghost in Brenn#112 at 30 hp, WALLS=2, and the last error "no mob found named drowned priest".

*See also:* B5 (Both reload a quest from its world/quests file and LOAD= targets and restart it); Q6 (DT2's MODIFY QUEST STEP \<label> needs Q6's named steps).

<!-- from: quests/qst-inspector -->

<a id="dt3"></a>

## DT3. World load report, and no overwrite of partly loaded files

*Kind:* debug tool · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** When a hand-edited world file names an unknown item class, ability or behaviour, `CoffeeMaker` logs "Unknown ... skipping" and carries on. `WorldFiles.importArea` still counts the file as loaded whole, so the next builder `SAVE` writes it back without the skipped object: a typo becomes lost data. `WorldFiles.report` prints only fatal problems. The rest is in mud.log, seen with `LIST LOG` or `ERRMSGS=BOTH`.

**Add.** While `WorldFiles` imports, collect every CoffeeMaker and loader warning and error (unknown class or ability, aborted parse, bad exit target) with its file and room. Print a summary at startup and at the builder's first login, and list it with `WORLD PROBLEMS`. A file with skipped parts is not loaded whole: it is not rewritten until it is fixed and reloaded, or the builder confirms with `SAVE FORCE`.

**Example.** The owner types `<ACLASS>Spell_Fireblal</ACLASS>` on a mage. At the next start builder mode says "castle.cmare: unknown ability Spell_Fireblal on the court mage (Castle#14), skipped; this file will not be rewritten until fixed."

*See also:* B2 (Both stop builder SAVE overwriting a world file that is not what was loaded, with SAVE FORCE to override); B13 (B13's builder login summary shows DT3's WORLD PROBLEMS).

<!-- from: builder-commands/bc-load-report -->

<a id="dt4"></a>

## DT4. CHATTEST, CHATLINT and CHAT RELOAD

*Kind:* debug tool · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** When an NPC does not answer, the builder cannot see why: the wrong group, a failing mask, a parse error, the 3-tick start delay, or the busy check after another NPC spoke. Chat parse errors go only to `Log.debugOut`, malformed lines to a warning. There is no MUDCHAT debug flag, and `TEST` has no MudChat case. Only reloading exists: `UNLOAD RESOURCE` drops cached chat groups.

**Add.** `CHATTEST <mob> <sentence>` shows, without anyone speaking, the NPC's active groups, the entries the words map to, each match result with masks, `+IF` and `?` guards, the weighted replies with variables filled in, and whether timing or busy state would stop a reply. `CHATLINT <file>` reports parse errors with line numbers, malformed `${}` lines, `@` links to later groups, entries hidden by broader earlier patterns, and tick odds. `CHAT RELOAD [file]` re-parses, and builder mode reloads a changed file by itself. The same test and lint cover `.dlg` dialogue trees.

**Example.** `chattest foreman hello there` shows group "foreman_quest" (switched), entry "(hello)" rejected by `?VAR($t MINEQUEST)==done`, fell through to "@default" entry "(hello|hi)", weight 9.

<!-- from: npc-dialogue/dlg-chattest -->

<a id="dt5"></a>

## DT5. DEV=1: a normal game with saves and developer powers

*Kind:* world option · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** There are two modes: `BUILDER=1`, with no saves, and a normal game. A launch override already gives powers in a normal game: `sud.sh SYSOPMASK=-MOBS RESTORE=act2` makes every profile character a sysop, keeping class and saves, and `MODIFY USER` can grant security groups saved in `CMCHAR`. But a sysop holds every security flag: `IMMORT` keeps them from dying, and `ORDER`/`CMDROOMS` make `Aggressive` skip them, so the test is not like play. Nothing marks such a save, and scripts can only test the powers with `ISIMMORT($n)`, not whether this is a dev game.

**Add.** A `DEV=1` launch setting, in the ini or on the command line. Every profile character gets the security group named by `DEVGROUP=` (default `GROUP_DEV`, defined in coffeemud.ini like the other `GROUP_` lines) without a class change, and the plan's dev-only commands (`GOD`, `DEVLOG`, `VARS`, `SCRIPT` and others) are on. Saves made then get `dev=true` in header.properties and show `[dev]` in `SAVES` and the startup list. The script function `ISDEV()` tests it.

**Example.** `sud.sh DEV=1 RESTORE=act2`: play the real level-5 Ranger through act 2, `GOTO` back to check a fixed room, look at quest variables, and keep saving.

*Re-checked against the code after writing: corrected.*

*See also:* ST6, DT18 (ST6 adds ISDEV() and ISBUILDER(), DT5 adds ISDEV() and DT18 adds ISBUILDER(); add each once); DT8 (Alternatives for testing as a mortal: a normal game with dev powers, or a playtest mode inside builder mode); DT6, DT27 (DT6's GOD toggles and DT27's world/dev areas depend on DT5's DEV=1 mode).

<!-- from: sp-dev-tooling/spdev-devmode -->

<a id="dt6"></a>

## DT6. Prop_DevCheats and GOD toggles

*Kind:* property and debug tool · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Most cheats exist under other names, but only for sysops and all at once. `IMMORT` keeps you at 1 hp. `Prop_WeaponImmunity` with `+ALL` blocks damage. `WIZINV` sees hidden, dark and invisible things and turns malice away. `Aggressive` skips holders of `ORDER` or `CMDROOMS`. `ALLSKILLS` skips cooldowns, and `GOTO` moves you anywhere. Missing: passing closed or locked doors and `Prop_Req*` entry checks (`StdExit` has no bypass), zero mana and move costs, and turning one power off, such as `IMMORT` for one fight.

**Add.** `Prop_DevCheats`, saved with the character like any effect, with `NOCLIP` (ignores doors, locks, `Prop_Req*` checks and movement costs), `NOCOST` (no mana or move cost, no cooldown), and opt-out flags that suspend a sysop power for that character. A dev-only `GOD [flag|ALL] [ON|OFF]` toggles these and fronts the existing pieces as `NODAMAGE`, `NODEATH`, `SEEALL` and `NOAGGRO`.

**Example.** `GOD NOCLIP NOAGGRO ON`, then walk the 40-room Sunken Crypt checking every description and exit. `GOD NODAMAGE ON`, then let the lich drop below 25% to see its `HITPRCNT_PROG` phase change.

*See also:* DT5, DT27 (DT6's GOD toggles and DT27's world/dev areas depend on DT5's DEV=1 mode).

<!-- from: sp-dev-tooling/spdev-cheats -->

<a id="dt7"></a>

## DT7. SCRIPT FIRE, CALL, RESET: run a host's triggers on demand

*Kind:* debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Nothing runs a chosen trigger or `FUNCTION_PROG` on a chosen host as if a given character caused it. `TICKTOCK` makes a `TIME_PROG` fire, but it changes the clock. `MPRUN mpslay` fires a `DEATH_PROG` only by killing the mob. `MPRUN MPSCRIPT <target> INDIVIDUAL EXECUTE` runs new code on a target but cannot call its functions, and `MPCALLFUNC` reaches only its own host. `UNLOAD FILE` resets `ONCE_PROG` only for the first host that re-parses. `MPNOTRIGGER` works only from inside the host's script. Pending alarms cannot be listed.

**Add.** A `SCRIPT` command, with targets as for `STAT` (a mob or item here, `ROOM`, `AREA`, name@room): `SCRIPT FIRE <target> <trigger> [args] [AS source] [WITH item]`, for `SPEECH hello`, `GIVE`, `DEATH`, `GREET`, `TIME_PROG 5`; `SCRIPT CALL <target> <function> [args]`; `SCRIPT RESET <target> [ONCE|DELAY|ALL]`; `SCRIPT LIST <target>`, its triggers with file:line; `SCRIPT DISABLE|ENABLE <target> [trigger]`; `SCRIPT ALARMS <target> LIST|CLEAR`.

**Example.** `SCRIPT FIRE baker TIME_PROG 5 AS Bob` checks the dawn bread scene without waiting a day. After editing the innkeeper's welcome, `SCRIPT RESET innkeeper ONCE`, then walk in again.

*See also:* JS12 (Both fire a chosen trigger on a chosen host on demand (JCONSOLE .trigger, SCRIPT FIRE)).

<!-- from: mobprog-language/mpl-fire, sp-dev-tooling/spdev-scriptfire -->

<a id="dt8"></a>

## DT8. PLAYTEST: meet the world as a mortal from builder mode

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Builder mode makes every character that logs in an Archon (`SaveGame.makeArchon`), party members too. Holders of `ORDER`, `CMDROOMS` or `CMDMOBS` are spared by `Aggressive` and `Follower`, are not scaled to by `Prop_Doppleganger`, get no rolled variable equipment, and see `Prop_Uncampable` mobs respawn. Workarounds: `POSSESS` a GenMob with `SYSMSGS` off (no XP, quests or player prompt), which also lets `Mobile` mobs leave; or quit for a normal game, since `SaveGame.restore` refuses in builder mode.

**Add.** `PLAYTEST ON|OFF`. While on, one shared exemption test makes AI, scaling and spawning treat the builder as a mortal, and the prompt shows `(playtest)`. Building commands run with `BUILD <command>`. ON takes a restore point that death returns to, and can set party and difficulty as a new game. OFF restores HP and makes peace. Larger: `PLAYTEST GAME (NAME) (AT room) (LEVEL= CLASS= RACE= KIT=)` exports the world and restarts in process as a normal game; there, plain `BUILD` restarts into builder mode at that room. Also `PARTY MORTAL|ARCHON <name>`, respected by `makeArchon`, so the builder can `SWITCH` to a mortal tester.

**Example.** Build the bandit camp, `PLAYTEST ON`, sneak in, get ambushed at the party's level, `PLAYTEST OFF`, lower the sentry's SPOT=, try again.

*See also:* DT5 (Alternatives for testing as a mortal: a normal game with dev powers, or a playtest mode inside builder mode).

<!-- from: behaviors-npc/npc-playtest, builder-commands/bc-playtest, encounters-combat/enc-playtest -->

<a id="dt9"></a>

## DT9. DEVLOG: live errors and debug lines for the builder

*Kind:* debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Single-player forces every log stream to mud.log. Script errors show only to the room the host is in. `<SCRIPT>` JavaScript errors, `MPLOG`, `SCRIPTTRACE`, loader skips and quest failures go only to the file. Launching with `sud.sh ERRMSGS=BOTH WRNMSGS=BOTH` (the ini does not work, SUD overrides it) prints raw lines on the terminal, mixed with game output, never in character windows, and fixed for the session. `LIST LOG LAST n` and `LIST SCRIPTS ERRORS` read the file afterwards.

**Add.** A listener hook in `core/Log`, and `DEVLOG ON|OFF [ERROR|WARN|INFO|DEBUG] [MODULE mask]`, such as `Scripting,Quest*`. New lines go to the builder's own session as one compact coloured line, a repeat shown once with a count, or with `DEVLOG WINDOW` to a separate Swing window reusing `CharacterWindow`. `DEVLOG LAST n` replays, `DEVLOG CLEAR` clears. An ini key sets the default, and the setting persists in builder mode. `LIST LOG ERRORS SINCE BOOT` filters by source: Scripting, CoffeeMaker, Quests, WorldFiles.

**Example.** A script runs `MPGOTO Woods#99` two rooms away, and the terminal at once shows "[err] Scripting: the hermit/Woods#7/MPGOTO/no such room Woods#99". A JS TypeError in a `<SCRIPT>` block shows the same way.

*See also:* JS2, DT10 (All three route script and JavaScript errors, with file and line, to the builder; build one error channel); DT24, JS6 (JS22 (now in JS6)'s and DT24's debug() both write to DT9's developer log; one function).

<!-- from: builder-commands/bc-builder-error-echo, sp-dev-tooling/spdev-devlog -->

<a id="dt10"></a>

## DT10. Script errors with file and line, kept from the player

*Kind:* debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `logError` shows "Scripting Error: ..." to everyone in the room, so in a normal game the player reads engine errors. The message lists every LOAD= file of the object, not the one at fault, and gives no line. An unknown MP word is only logged; any other unknown word runs as a game command. An MP prefix such as `MPSE` resolves to the last match, `MPSETCLAN`. `<SCRIPT>` exceptions go only to mud.log. The aids are the `BADSCRIPTS` debug flag and `LIST SCRIPTS ERRORS`, after the fact.

**Add.** Record each script line's file and line when parsing, following LOAD=. Errors read `progs/guard.script:42 MPSETVAR Syntax: ...`. In builder mode they go to the builder's session and to a ring buffer shown by a `SCRIPTERRORS` command. In a normal game, ini `SCRIPTERRORS=LOG|NOTICE|SHOW` decides: logged only, one short line per session, or shown. An unknown or ambiguous MP word is an error. `<SCRIPT>` exceptions are reported the same way.

**Example.** A mistyped `mpechot $n` in a 300-line quest-giver script is reported to the builder as `progs/elder.script:212 unknown command MPECHOT`. The playtester never sees a raw engine error.

*See also:* SC14 (SC14 fixes the MP prefix fallback taking the last match, and DT10 makes an ambiguous MP word an error); JS2, DT9 (All three route script and JavaScript errors, with file and line, to the builder; build one error channel).

<!-- from: mobprog-language/mpl-errors -->

<a id="dt11"></a>

## DT11. SCRIPTCHECK: a static checker for MOBPROG and JavaScript

*Kind:* builder tool and debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Script mistakes surface only when a line runs. `parseScripts` only splits lines. IF conditions are parsed on first use, FOR without NEXT is found when it runs, and LOAD= files are read only then. An unknown trigger such as `GREETPROG` never fires and nothing reports it at load. A mistyped command runs as a game command. `LIST SCRIPTS ERRORS` reads the log afterwards, so the builder must play every branch.

**Add.** `SCRIPTCHECK <file|target|AREA|ALL>`, with a headless form. It parses with LOAD= expanded, runs nothing, and reports by file:line: unknown triggers, commands (strict, no prefix matching) and functions; wrong argument counts; unbalanced IF/ELSE/ENDIF, SWITCH/ENDSWITCH, FOR/NEXT and MPWHILE parentheses; `<SCRIPT>` without `</SCRIPT>`; missing LOAD= files; `MPCALLFUNC` to undefined `FUNCTION_PROG`s; room IDs in `MPGOTO`, `MPTRANSFER` and `MPOLOADROOM` that do not exist; Rhino compile errors and unapproved `<SCRIPT>` blocks. Variables read but never set are warnings. Builder `SAVE` runs it. It reuses the progs, methods and funcs tables in `ScriptingEngine`.

**Example.** Before playtesting the new village, `SCRIPTCHECK AREA` shows "progs/miller.script:14 unknown trigger GREETPROG", "quests/harvest.script:52 MPCALLFUNC PayFarmer: no such FUNCTION_PROG" and three unbalanced IFs.

*See also:* DT32 (DT32 runs DT11's MOBPROG checker on a quest's embedded scripts); DT13, DT12 (DT13's WORLD CHECK runs DT11's SCRIPTCHECK and DT12's PROPCHECK).

<!-- from: mobprog-language/mpl-lint, sp-dev-tooling/spdev-scriptcheck -->

<a id="dt12"></a>

## DT12. PROPCHECK: check every property string in the world

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Property parameters are free text, and most classes drop bad input silently. Only `Prop_HaveAdjuster` and `TriggeredAffects` collect errors; `Prop_AddDamage` and `Prop_Socials` write to the log. A wrong room ID can quietly freeze a mob under `Prop_LocationBound`, and a misspelled spell ID in a `Prop_SpellAdder` list does nothing. No command validates effects, and an ability does not declare its parameter keys.

**Add.** `PROPCHECK [AREA name|ROOM|ALL|CATALOG]` walks rooms, exits, areas, mobs, items and catalog entries, re-parses every effect's text and reports: keys the class does not know (each Prop gets a declared key list); room IDs that do not resolve (`Prop_Transporter`, `Prop_RoomRedirect`, `Prop_LocationBound`, `Prop_SparringRoom`); unknown ability IDs in spell lists; zapper masks that fail to compile; props on hosts they cannot affect (`canAffect` false). Lines read "Area#12 / iron chest / Prop_LimitedContents: unknown key MAXX". It can also run at builder `SAVE`.

**Example.** Before committing a new area, the owner runs `PROPCHECK AREA Dunmoor` and fixes three typos that would otherwise have done nothing.

*See also:* DT13, DT11 (DT13's WORLD CHECK runs DT11's SCRIPTCHECK and DT12's PROPCHECK); DT19 (Both need each property and behaviour to declare its parameter keys so unknown ones can be flagged).

<!-- from: properties/pr-propcheck -->

<a id="dt13"></a>

## DT13. WORLD CHECK: one lint of the whole world, also headless

*Kind:* debug tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The checks are separate commands with separate output: `LIST UNLINKEDEXITS`, `ONEWAYDOORS`, `ORPHANS`, `LINKAGES` (split room groups, not reach from START), `DEVIATIONS`, `LIST SCRIPTS ERRORS`, `LIST HELPFILEREQUESTS`, `LIST TAGS TODO`, `TRAILTO`. The boot import names a broken world file but stops at its first problem. Nothing checks keys, default text or data references, and nothing runs before a commit.

**Add.** `WORLD CHECK (AREA name) (LOG) (ONLY=exits,reach,keys,scripts,text,data,balance,quests)` wraps those lists in one report by severity, with room IDs and file:line, and adds: reach from START and the race and class start rooms; locked doors and containers whose key exists nowhere (room, mob, shop, catalog); empty shops; default text ("StdRoom-", "Brand new database room!", "A Standard MOB"); unknown classes, and unknown ability, race, class, faction, catalog or room IDs in custom objects, masks and scripts; masks that fail to compile; achievements that parse to "Error:"; short recipe rows; custom objects without help; quests that fail to start; unreferenced catalog items; areas with no clock or climate; `Mobile` mobs free to leave their area; `DEVIATIONS` above a threshold; `SCRIPTCHECK ALL` and `PROPCHECK ALL`. `sud.sh CHECK=1` runs it on a scratch copy and exits non-zero on errors.

**Example.** A git hook runs `sud.sh CHECK=1` and refuses the commit: "Crypt#7 east: locked with key 'bone key', which exists nowhere"; "Town#3: default description".

*See also:* DT11, DT12 (DT13's WORLD CHECK runs DT11's SCRIPTCHECK and DT12's PROPCHECK).

<!-- from: builder-commands/bc-world-lint, data-content/dc-content-check, sp-dev-tooling/spdev-worldcheck -->

<a id="dt14"></a>

## DT14. GENERATE CHECK: lint for generator XML

*Kind:* debug tool and generator · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Template errors in generator XML (unknown INSERT or LIKE ids, undefined variables, a misspelt layout or CLASS, REQUIRES that cannot be met, `<` in attributes) show only at generation time, as "Unable to fully generate", often deep in a run. Detail is in the log: load-time problems, and traces under `DEBUG=MUDPERCOLATOR`. `GENERATE <TYPE> LIST` prints the defined ids. The `random.xsd` the DOCS mention is gone from the fork.

**Add.** `GENERATE CHECK <file>` loads the file with its LOAD and FROM includes and reports, with tag id and line: duplicate ids without MERGE; INSERT, LIKE and FROM targets that do not exist; CLASS values `CMClass` cannot resolve (catalog: and metacraft: excepted); unknown layouts; `$variables` never defined and without default; REQUIRES that cannot be met; SELECT modes that cannot pick; unused ids. The smoke test can call it, so content mistakes fail the build.

**Example.** After writing crypt.xml, `GENERATE CHECK randareas/crypt.xml` says that "crypt_leaf_rooms" is inserted but never defined, and that class "GenSkeleton" does not exist.

<!-- from: generators/gen-check -->

<a id="dt15"></a>

## DT15. Playtest files with assertions, and a runner that fails

*Kind:* builder tool and data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `tools/sudplay.py` drives the real game from input lines and passes ini overrides (`TIMEMODE=TURNS`, `RANDOMSEED=`, a scratch `DBSERVICE=`, `SAVEDIR`, `WORLDDIR`, `RESTORE=`). But its `#expect` waits up to 30 minutes and never fails, so each test needs its own greps afterwards, as `smoke.sh` does. A builder cannot keep regression playtests of the world as content.

**Add.** `.play` files under `world/tests/`: one command per line, plus `#seed n`, `#new` with a `#char Name,Race,Class` block or `#restore slot`, `#expect TEXT` and `#reject TEXT` (fail on timeout or match), `#assert HERE roomid`, `#assert HAS item`, `#assert VAR host name op value`, `#assert QUEST name STEP n`, `#wait n`, `#save slot`. A small dev-only `ASSERT` command answers the asserts, so nothing is scraped. `tools/playtest.sh [files]`, on top of sudplay.py with scratch data as smoke.sh uses, defaults to turns and a fixed seed, prints PASS or FAIL per file and step, and exits non-zero on failure. In game: `PLAYTEST RUN <file>`, so it does not clash with DT8's `PLAYTEST ON|OFF`.

**Example.** `world/tests/bridge.play` goes to the troll, gives the toll, asserts `VAR Hero PAIDTROLL = 1`, crosses, asserts `HERE Moor#1`. Run before every commit, it catches the day an edit renamed the toll item.

**Also merged here, from DT38 (WORLD TEST: repeatable checks of the world's content).** WORLD TEST [name|ALL] in builder mode, for two kinds of file:
- world/tests/*.test: a throwaway character (LEVEL, CLASS, RACE, AT room), a list of commands, and EXPECT "text", EXPECT ROOM id, EXPECT VAR mob var=value, EXPECT ITEM name.
- world/js/tests/*.js: test(name, function(t){...}) with the helpers t.newPlayer, t.go, t.say, t.wait, t.eq and t.at.

Each run uses a throwaway copy of the world, turn-based time and a fixed RANDOMSEED, then resets the rooms. It reports pass or fail, with the output that failed. A script in tools/ can run the tests before a commit.

*Re-checked against the code after writing: corrected.*

*See also:* DT25, DT39 (later) (DT25 records .play files for DT15's runner, and exact replay of them needs DT39 (later)).

<!-- from: sp-dev-tooling/spdev-playtest, javascript/js-world-tests, builder-commands/bc-content-tests -->

<a id="dt16"></a>

## DT16. TESTCHAR and KIT: test characters and loadouts as data

*Kind:* builder tool and data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A new profile character goes through every creation question, as the long answer script in `smoke.sh` shows. `CHARGEN NEW` makes an averaged levelled mob in the room, not a profile character that can join the party or be saved. A kit can be written as a MOBPROG file run with `MPRUN` or a `CHARCREATIONSCRIPTS` hook, using `MPPLAYERCLASS`, `MPOLOAD`, `MPMONEY`, `MPFACTION`, `MPSETVAR` and `MPTRANSFER`, but there is no simple kit format.

**Add.** Kit files `world/kits/NAME.kit`, with lines such as `level 12`, `class Fighter`, `race Dwarf`, `item catalog:Mithril Axe`, `gold 500`, `ability Skill_Dodge 75`, `faction Merchants 2500`, `var ACT 3`, `room Crypt#1`. `TESTCHAR name KIT=k [PARTY]` (dev and builder) makes a profile character from the kit with no questions, levelled by CHARGEN's code, and can add it to the party. `KIT k [target]` applies a kit to an existing character or follower, as a thin interpreter over those MP commands. Kits live with the world in git.

**Example.** `TESTCHAR Tank KIT=act3-warrior PARTY`, `TESTCHAR Heals KIT=act3-cleric PARTY`, then `GOTO Crypt#1`: the act-3 dungeon is tested with a level-12 party in under a minute.

*See also:* P7, P8 (DT16's kit files and TESTCHAR overlap P7's starting kits (both under world/kits) and P8's ready-made characters; share one format).

<!-- from: sp-dev-tooling/spdev-testchar-kits -->

<a id="dt17"></a>

## DT17. PREVIEW and CHECKTEXT for conditional text

*Kind:* builder tool, presentation · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A builder sees a `<VARIES>` text only as it reads now, here, to them. To see the winter night version they must change the world: `TICKTOCK`, `Prop_Weather` on the area, or walking in from one side. Unknown or lowercase tags, open tags, `>` inside a `MASK` and open colour codes print silently as text. The engine has one such bug now: the code spells the tag `FROMUNKOWN`, where the ArchonGuide documents `FROMUNKNOWN`.

**Add.** `PREVIEW ROOM|ITEM name|MOB name [DAY|NIGHT|DAWN|DUSK] [SPRING..WINTER] [RAIN|SNOW|...] [FROM north] [VISITED|FIRSTVISIT] [AS charname] [VAR x=y]` shows the text under those conditions and changes nothing. `CHECKTEXT [ROOM|AREA|WORLD]` lists unknown or lowercase tags, unbalanced tags, masks with `>`, open colour codes, dead `FILE=` links, and `Prop_Details` keywords that also match real objects. `FROMUNKNOWN` becomes an alias.

**Example.** `PREVIEW ROOM NIGHT WINTER SNOW VAR BRIDGE=1`, then `CHECKTEXT AREA` reports `Mirefen#14: <nigth> is not a tag`.

*See also:* PR16 (Both deal with the FROMUNKOWN tag spelling (document it vs add FROMUNKNOWN as an alias)); B12 (Both define a PREVIEW command (catalog objects vs conditional text)); DT20 (DT20's LOOK AS uses DT17's PREVIEW code).

<!-- from: presentation/pr-preview-lint -->

<a id="dt18"></a>

## DT18. Script trace for one host, shown to the builder

*Kind:* debug tool, script command, script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `CREATE DEBUGFLAG SCRIPTTRACE` turns on a global trace that writes every line of every script to mud.log. It does not say why a trigger was skipped: the chance roll, a mask, `MPNOTRIGGER`, a busy host (`isFreeToBeTriggered`), no PC present, a passive area, or no global registration yet. `if isimmort($n)` with `mpechoat` gives builder-only output, but it needs a PC in context.

**Add.** `SCRIPTTRACE <target>|ROOM|AREA|OFF [trigger]`, shown only in the builder's session and not saved. It echoes each trigger considered with its result (`GREET_PROG 50: rolled 73, skipped`), each line run and each variable change. `MPSETINTERNAL TRACE TRUE` turns it on from inside a script. A new test, `ISBUILDER()`, calls `SaveGame.isBuilderMode()`. A builder-only `MPDEBUG` has its own item.

**Example.** With `SCRIPTTRACE AREA GREET_PROG` on, the builder walks into the bandit camp. The trace shows the chief's `GREET_PROG` was skipped because he is asleep, so the script itself is fine.

*See also:* ST6, DT5 (ST6 adds ISDEV() and ISBUILDER(), DT5 adds ISDEV() and DT18 adds ISBUILDER(); add each once).

<!-- from: mobprog-triggers/mt-trigger-trace, mobprog-language/mpl-trace -->

<a id="dt19"></a>

## DT19. STAT BEHAVIORS: an NPC's live behaviour state

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `STAT SCRIPTS <mob>` shows a mob's scripts and variables, and `MODIFY MOB` lists its behaviours with their parameter strings. Nothing shows a behaviour's live state. `StdBehavior.getStat` gives only `CLASS` and `TEXT` (`CombatAbilities` exposes more). Misspelt parameter keys are dropped silently, and the `DEBUGFLAG` traces go to the log.

**Add.** `STAT BEHAVIORS <mob|item|here>`, beside `STAT SCRIPTS`. For each behaviour it shows:

- the parameters as parsed, with unknown keys flagged;
- ticks to the next action, and the last action;
- the current target, route step, schedule entry, morale or remembered facts.

Each behaviour answers `getStat("DEBUG")`; the default gives tickDown and chance. `TRACEB <mob> [ticks]` echoes each decision to the builder for a while.

**Example.** The miller is not at the market. `STAT BEHAVIORS miller` shows `Schedule: entry DAY -> Market#5, path failed: locked door Mill#2 east`.

*See also:* S9 (S9's getStat(STATE) and DT19's getStat(DEBUG) both expose a behaviour's runtime state; design them together); DT12 (Both need each property and behaviour to declare its parameter keys so unknown ones can be flagged).

<!-- from: behaviors-npc/npc-inspect -->

<a id="dt20"></a>

## DT20. WEATHER SET, TIME SET, LOOK AS and HAZARDS

*Kind:* builder tool, debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `TICKTOCK` moves the calendar by any step from hours to years, and `TICKTOCK SET Y/M/D/H` sets it, but only plain `TICKTOCK n` runs the time-change events. No command sets the weather. The builder adds `Prop_Weather` with `MODIFY AREA`, or calls `setNextWeatherType` and `forceWeatherTick` from `JCONSOLE`. Nothing lists an area's hazards.

**Add.** In builder mode:

- `WEATHER SET <weather> [AREA <name>] [NEXT]`;
- `TIME SET <hour|DAWN|DUSK|NIGHT>` and `TIME SEASON <season>`, which run the change events;
- `LOOK AS <night|winter|rain|visited|from north...>`, which shows the room under made-up conditions, using the same code as `PREVIEW`;
- `HAZARDS`, which lists every hazard, spawner and condition behaviour in the area with its current state.

**Example.** The owner writes a moor room with `<NIGHT><FOG>` text and checks it with `LOOK AS night fog`. Then `WEATHER SET THUNDERSTORM` lets them watch the shepherd's `WEATHER_PROG`.

*See also:* W12, W20 (W12's MPWEATHER and WEATHER SET and FORECAST are repeated in W20 (MPWEATHER, FORECAST) and DT20 (WEATHER SET); build one set of weather commands); DT17 (DT20's LOOK AS uses DT17's PREVIEW code).

<!-- from: behaviors-world/bw-envtools -->

<a id="dt21"></a>

## DT21. LAW: inspect and test an area's legal system

*Kind:* builder tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The law finds its people and places by name (`OFFICERS=cityguard`, `JUDGE=justice judge`, `JAIL=The Jail$`). A name that matches nothing fails silently, and the reasons go only to the debug log, under the `ARREST` flag. `STAT CRIMES` gives daily counts, not current warrants. The only way to look inside is `JCONSOLE` with raw calls such as `CMLib.law().getLegalBehavior(...).getWarrantsOf(...)`.

**Add.** `LAW`, in builder mode, shows the law file in use, the officers, judges, jail and release rooms it found, the taxes and the number of crimes. `LAW CRIMES [filter]`, `LAW WARRANTS [name]`, `LAW PARDON <name> [crime|ALL]` and `LAW ACCUSE <name> <crime>` handle warrants. `LAW TEST <crime>` says whether doing it here and now is a crime, and why (home or indoors flags, witnesses). `LAW SET KEY=VALUE` writes the custom law, using the inline Arrest parameters that already exist.

**Example.** After building a town, `LAW` reports `0 officers found matching cityguard`, and the builder renames the guards.

*See also:* W1 (Both add pardoning and accusing per crime (MPPARDON and MPFINE vs LAW PARDON and LAW ACCUSE)); W21 (DT21's LAW SET writes the custom law that W21 saves as world content).

<!-- from: world-systems/ws-law-tool -->

<a id="dt22"></a>

## DT22. PRICECHECK: how a price was reached

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A price combines several factors:

- the stock price or base value;
- the first matching prejudice rule;
- every matching `PRICEMASKS` mask, multiplied together;
- charisma against the keeper's wisdom;
- sales tax, devaluation and rounding to coins.

Shopkeeper, area, parent area and ini settings stack on top. `VALUE` and the shop's `LIST` show only the result. The only breakdown is `JCONSOLE` with raw calls to `CMLib.coffeeShops()`.

**Add.** `PRICECHECK <item> [AT <shopkeeper>] [FOR <character>] [SELL]` prints each factor and where it came from (shopkeeper, area, parent or ini, and which mask or rule matched). It also prints the selling and buying prices, the budget left and the time to the next inventory reset. It reads the existing `getFinal*` settings and factor functions.

**Example.** `PRICECHECK cloak` shows `PRICEMASKS 10.0 -MATERIAL +CLOTH, set by quest harsh winter`, so the builder sees why cloaks cost ten times more.

<!-- from: world-systems/ws-price-debug -->

<a id="dt23"></a>

## DT23. TIMESCALE: run real time faster or slower

*Kind:* world option, builder tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** In real-time mode the clock runs at wall-clock speed, so watching a caravan route or a weather change means waiting. `GameTime.now()` has no rate. At launch, `sud.sh TICKTIME=500` makes ticks come about 8 times as often, and `MILLISPERMUDHOUR=60000` speeds up only the calendar. Neither one scales the millisecond durations read from `GameTime` (cooldowns, decay, effect timers), and the tick length is fixed at boot.

**Add.** A rate in `GameTime`, set by the dev and builder command `TIMESCALE x` (0.1 to 20) or by `TIMESCALE=` in the ini. `GameTime.now()` multiplies the real time elapsed since the last change, so everything that reads the clock follows. Pause and turn-based time work as now. The prompt shows `(x5)` while the rate is not 1.

**Example.** `TIMESCALE 8` to watch the river ferry cross and come back a few times, then `TIMESCALE 1`.

<!-- from: sp-dev-tooling/spdev-timescale -->

<a id="dt24"></a>

## DT24. MPDEBUG and a JavaScript debug() for builder-only output

*Kind:* script command, JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Players in the room see `MPECHO`, and a forgotten one ships with the world. `MPLOG DEBUG <module> <text>` does most of the job already: it expands $-variables and writes only to the debug log, and `sud.sh DBGMSGS=BOTH` shows it on the terminal. In `<SCRIPT>` JavaScript, `Packages.com.planet_ink.coffee_mud.core.Log.debugOut(...)` works but is long. No message says which host, room or line wrote it.

**Add.** `MPDEBUG text`: `MPLOG DEBUG` with a `[debug host@room file:line]` prefix. In builder or dev mode it goes to a developer log, on the terminal or in a window. In a normal game it does nothing, so leftover lines are harmless. The same function is `debug(x)` in the `<SCRIPT>`, `JRUN` and `JCONSOLE` scopes, with `toJavaString` for objects.

**Example.** In a quest's `SPEECH_PROG`, `MPDEBUG chose suspect=$<$i SUSPECT> motive=$<$i MOTIVE>` checks the mystery generator's choices without spoiling them in the room.

*See also:* DT9, JS6 (JS22 (now in JS6)'s and DT24's debug() both write to DT9's developer log; one function).

<!-- from: sp-dev-tooling/spdev-mpdebug -->

<a id="dt25"></a>

## DT25. RECORD INPUT: a replayable playtest file

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A bug that shows up after an hour of play cannot be replayed. `sud.sh DEBUG=INPUT` logs each typed line to mud.log as `TIN: <character>: '<line>'` with a timestamp, for the terminal and every window. `RECORD` (`Archon_Record`) writes input and output mixed together. Neither is a file a runner can replay, and neither records what game it started from.

**Add.** `RECORD INPUT file|OFF` (dev) and the ini setting `RECORDINPUT=1`. Every typed line is written with its character and game time, after a header: seed, time mode, the save or new game it started from, and the world checksum. The result is a `.play` file that the playtest runner can replay. Today that runner is `tools/sudplay.py`, which plays a script of input lines. `RECORD MARK text` puts a comment in the file when the bug is seen.

**Example.** While playing with `RECORDINPUT=1`, the blacksmith's script loops. `RECORD MARK loop here` marks the spot, and the file is replayed after each fix.

*Re-checked against the code after writing: accurate.*

*See also:* DT15, DT39 (later) (DT25 records .play files for DT15's runner, and exact replay of them needs DT39 (later)).

<!-- from: sp-dev-tooling/spdev-record-input -->

<a id="dt26"></a>

## DT26. NOTE: playtest notes that reach builder mode

*Kind:* builder tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The `BUG`, `TYPO` and `IDEA` journals were removed. `COMMANDJOURNALS=NOTE ADDROOM` in coffeemud.ini would make a `NOTE` journal with the room ID on each entry, and its `SCRIPT=` could copy entries to mud.log with `MPLOG INFO`. But entries are kept in `CMJRNL`, a table in that game's save. A note written in play never reaches builder mode, where the fix is made.

**Add.** `NOTE text` works in any mode and appends to `notes/playtest.txt`, outside world/ and the saves. Each note records the date, save slot, character, room ID and name, game time and the text. `NOTE HERE` adds what `STAT ROOM` shows. `NOTES` lists them numbered, `NOTES DONE n` marks one done, and `NOTES GOTO n` (builder or dev) goes to the room.

**Example.** While playing: `note the miller mentions a dog that is not in the room`. Next day in builder mode, `NOTES GOTO 4`, add the dog, `NOTES DONE 4`.

*See also:* PR7 (Both claim the NOTES command (PR7's JOURNAL alias vs DT26's playtest notes)).

<!-- from: sp-dev-tooling/spdev-notes -->

<a id="dt27"></a>

## DT27. world/dev/: areas loaded only for building and testing

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Every file in world/areas is imported into every game, and the default start room even ships the Archon's manual. `Prop_Hidden` on an area hides it from `GOTO`, area lists and tracking for normal players. But the area is still loaded and part of the world checksum, and its items are real copies, which matters for the planned unique-item caps. `WORLDDIR=` can point at a dev world, but each switch forces a full rebuild.

**Add.** Area files in `world/dev/*.cmare`, imported only with `BUILDER=1` or `DEV=1` (only `BUILDER` exists today). They have their own checksum, so they do not force rebuilds of the normal game. Builder mode writes areas flagged dev (an area flag or a `dev` tag in the file) back there. `DEVSTART=Hub#1` sets the dev start room. `WORLDCHECK` warns about exits from shipped areas into dev areas.

**Example.** `world/dev/hub.cmare` holds a Developer Hub with portals to Act1#1, Act2#1 and Act3#1, a `Prop_PracticeDummy`, and a chest with one of each catalog unique. Players never see it.

*See also:* DT5, DT6 (DT6's GOD toggles and DT27's world/dev areas depend on DT5's DEV=1 mode).

<!-- from: sp-dev-tooling/spdev-devonly-content -->

<a id="dt28"></a>

## DT28. VESSEL STATUS and LIST INSTANCES

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `LIST SHIPS` shows a vessel's name, place and tech registry key, and `STAT` on the vessel item adds owner, price, speed and uses. Nothing shows its course, anchor, facing, pacing, crew, passengers or route step. Instance children appear in `LIST AREAS` under names like `3_Area`, and `INSTANCEDISPLAY` only predicts which type a player would get. Nothing shows an instance's members or time left.

**Add.** `VESSEL STATUS <name>` shows course, anchor, facing, speed and pacing, hull, owner, home port, docked room, behaviours and route position, crew and passengers. `LIST SHIPS` gains `ANCHOR`, `COURSE` and `OWNER` columns. `LIST INSTANCES` lists each child area with its parent, type ID, members, ticks to timeout, room count and a `GOTO` hint.

**Example.** While testing the ferry line, `VESSEL STATUS gull` shows `stop 2/3 Saltmarsh, departs at 12, anchor down, 3 crew, 1 passenger`, and the builder sees why it is stuck.

*See also:* T11 (Both add a VESSEL builder command (RENAME, ENTER and HOME vs STATUS)).

<!-- from: vehicles-travel-instances/vti-debug-vessels-instances -->

<a id="dt29"></a>

## DT29. VALIDATE RECIPES

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A malformed inline component only logs, and the craft then costs nothing. A missing named component logs nothing, and a bad mob ID in hunting.txt shows only "no signs of life". `LIST RECIPES <skillID>` and `MODIFY RECIPE <skillID>` check the base recipe file column by column, but they stop at the first error. They skip the `.txt.N` extensions, components.txt and the gathering tables, and they do not check whether components resolve.

**Add.** `VALIDATE RECIPES [skill|ALL]` parses every recipe row, extension file and components.txt entry of the chosen skills. It reports every error with its file and row:

- unknown materials, resources and subtypes;
- unknown item classes, and unknown spell or behaviour IDs;
- malformed component expressions;
- bad mobs or races in hunting.txt and fishing.txt;
- rows with the wrong number of columns.

**Example.** After adding starsteel recipes, `VALIDATE RECIPES Blacksmithing` reports `blacksmith.txt.1 row 3: material starstel unknown`.

<!-- from: crafting-gathering-housing/cgh-recipe-validate -->

<a id="dt30"></a>

## DT30. VOCAB: test translation and parser rules

*Kind:* debug tool, builder tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Writing translation and parser rules is trial and error. Nothing shows which rule rewrote a line, what the command pre-processor makes of a typed command, or which file supplied a lists.ini value. Reloading works but needs the cache keys: `LIST RESOURCES`, then `UNLOAD RESOURCE PARSED_LISTFILE`, `TRANSLATION_`, `PARSER_` or `PARSED_SOCIALS`, and `UNLOAD HELP`.

**Add.**

- `VOCAB TEST <text>` shows the text after the session and final rules, and which rule matched.
- `VOCAB INPUT <command line>` shows what the pre-processor makes of it, including several commands if it splits.
- `VOCAB LIST <KEY>` shows the effective lists.ini value and the file or area that supplied it.
- `VOCAB RELOAD` runs those unloads in one command.
- `VOCAB ADD <section> replaceall "x" with "y"` appends to the world rule file.

**Example.** `VOCAB INPUT get sword and shield from chest` shows the two resulting commands before the builder ships a parser rule.

<!-- from: vocabulary-languages/vl-vocab-tools -->

<a id="dt31"></a>

## DT31. LANGUAGE PREVIEW and COVERAGE for invented languages

*Kind:* debug tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** To check a `GenLanguage`, a builder has to speak it to a test mob with set proficiencies. The `LANGUAGES` command lists what a character knows, `LIST LANGUAGES` lists the defined ones, and `MODIFY LANGUAGE` only edits. `StdLanguage`'s `translate`, `messChars` and `scrambleAll` are public, but only JavaScript can reach them. Nothing reports how much of a text the dictionary covers.

**Add.** `LANGUAGE PREVIEW <id> [proficiency] <text>` shows what a listener at that proficiency hears, the speaker's own garble and a reader's view. It also shows where each word came from: the dictionary, the suffix rules, the syllables or the word lists. `LANGUAGE COVERAGE <id> <file|room>` counts dictionary hits over a text, such as an area's descriptions.

**Example.** Before release, `LANGUAGE COVERAGE Dunetongue` over the oasis area's descriptions shows the missing words. The builder adds the 40 most common ones to the dictionary file.

*See also:* L5 (DT31's preview shows which of L5's sources (dictionary, suffixes, syllables) each word came from).

<!-- from: vocabulary-languages/vl-language-preview -->

<a id="dt32"></a>

## DT32. QUESTCHECK: check a quest script without starting it

*Kind:* debug tool, quest feature · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A quest's errors show only when it starts, and only in mud.log. `CREATE QUEST` and `MODIFY QUEST` check the name and duration, and say only that the script "contained errors". A typo in an embedded MOBPROG shows only when that line runs. The `QUESTSCRIPTS` debug flag and `LIST LOG LAST n` only help read the log.

**Add.** `QUESTCHECK <quest|file>` parses the script without making objects and reports by line:

- unknown commands or `SET` variables;
- a missing `NAME` or `DURATION`;
- `WAIT` without `INTERVAL`, which deletes the quest after its first stop;
- `LOAD=` and `IMPORT` files that do not exist;
- `<FILE>` names and step labels used but not defined.

It also runs the MOBPROG parser on each embedded script to catch unknown triggers, commands and functions. At startup, builder mode checks all of world/quests and prints a one-line summary.

**Example.** After a hand edit, `QUESTCHECK bell` reports `line 14: SET DURATON unknown; line 30: LOAD=bell_ghost.script not found`.

*See also:* DT11 (DT32 runs DT11's MOBPROG checker on a quest's embedded scripts).

<!-- from: quests/qst-lint -->

<a id="dt33"></a>

## DT33. PUZZLE command: see, test, reset and check puzzle wiring

*Kind:* builder tool · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** STAT on an object dumps its script variables as raw XML. LIST SCRIPTS (AREA) lists scripted objects, LIST SCRIPTS ERRORS pulls runtime script errors from the log, and LIST UNLINKEDEXITS and LIST ONEWAYDOORS check the map. Nothing shows a room's switches, plates, receptacles and controllers with their state and targets. Nothing checks that a room, direction, door or catalog name in a script or property exists. A bad one shows up only in play.

**Add.** A PUZZLE command in builder mode. PUZZLE and PUZZLE AREA list each puzzle prop with its state and targets. PUZZLE SET \<obj> \<state>, PUZZLE SIGNAL \<target> \<name> and PUZZLE RESET [room|area] work the puzzle by hand. PUZZLE CHECK validates every target, signal and catalog name in the world, MPAT and MPOPEN included. It also runs at world load and logs to mud.log. The listing and the console need the switch, plate and controller props, so they come after those. CHECK is useful now.

**Example.** After building the Hall of Bells, PUZZLE CHECK reports that lever3 targets 'Vault#2 west', which has no door.

*See also:* I12 (later) (DT33's PUZZLE command shows and works I12 (later)'s controller and the switch and plate props).

<!-- from: items-puzzles/ip-puzzle-tool -->

<a id="dt34"></a>

## DT34. SCRIPTPROF: timing per script and trigger, slow-script warnings

*Kind:* debug tool · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Everything runs on one game thread, so a slow script stalls the world and makes turn-based WAITs crawl. LIST TICKS shows tick times per ticking object (tickermillitotal, tickermilliavg, tickerlastduration), and a mob's RAND_PROG and TIME_PROG count in its tick. DEBUG=SCRIPTTRACE logs each trigger with timestamps, so rough timing can be done by hand. Nothing counts per script file or trigger line. Nothing times triggers run from messages, such as SPEECH_PROG and GREET_PROG. MPWHILE stops after 4 seconds without telling anyone.

**Add.** SCRIPTPROF ON|OFF|RESET|REPORT [n] [AREA]. For each script file and trigger line it counts runs, total ms and max ms, and names the host with the worst run. An ini setting, SCRIPTSLOW=50 (ms): a trigger run longer than that, or an MPWHILE cut off at its cap, logs a warning to mud.log naming the host, file and line.

**Example.** WAIT 1 DAY takes a minute. SCRIPTPROF REPORT 5 shows the RAND_PROG at line 30 of progs/townsfolk.script taking 70% of the time: a FOR over every room in the world on every tick.

<!-- from: sp-dev-tooling/spdev-scriptprof -->

<a id="dt35"></a>

## DT35. COMBATDEBUG: explain each roll while tuning

*Kind:* debug tool · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Combat numbers stack up: Prop_CombatAdjuster, resistances, Prop_AbsorbDamage, the fudge for level difference, crits. SHOWDAMAGE=YES/SOURCE/TARGET in the ini shows only the final damage. STAT COMBAT \<mob> shows the static attack, armor and damage figures. The debug flags have no combat entry but `SIEGECOMBAT`, for ship fights. When an encounter feels wrong, the builder cannot see why a hit missed or did only 3 damage.

**Add.** A builder command, COMBATDEBUG ON|OFF [target]. For each attack involving the target it prints the attack value, the armor, the hit chance and the roll, and the base damage. It then prints the result of each formula (TARGETED, FUDGE, CRIT), and each effect that changed the damage message (resistance, absorb, immunity) with what it changed. It can also log to a file, for SIMFIGHT runs.

**Example.** `[dbg] goblin -> Ana: atk 62 vs ac 41, hit 71% roll 34 HIT; dmg 7, fudge(-2 lvls) 6, Prop_AbsorbDamage(10% +SLASHING) -> 5.`

*Re-checked against the code after writing: corrected.*

*See also:* DT40 (later) (DT35's combat log is meant for DT40 (later)'s SIMFIGHT runs).

<!-- from: encounters-combat/enc-combat-debug -->

<a id="dt36"></a>

## DT36. PROGRESSION report: the level curve and the XP the content gives

*Kind:* debug tool · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** To balance progression, the owner reads EXP_CHART, the class formulas and allqualifylist by hand. WILLQUALIFY \<level> \<class> lists the skills and expertises gained per level and marks autogain. CHARGEN NEW or PLAYER \<class> \<level> makes a sample character whose SCORE shows HP, mana, moves, trains and practices. HELP \<class> prints the practice and train formulas. LIST ACHIEVEMENTS and LIST QUESTS show rewards one at a time. There is no table of the curve, and nothing adds up the XP the world gives.

**Add.** PROGRESSION \<class> [race] [to level] lists, for each level, the XP needed, HP, mana and moves, practices and trains, the skills gained (autogain or to learn) and the expertises unlocked. PROGRESSION XP [area] adds up, per area, the XP in achievements, in literal MPEXP amounts in quest and mob scripts, and in Prop_Discovery and Prop_EnterAdjuster. It also shows the level a character reaches after doing everything, area by area in order.

**Example.** Clearing the first three areas gives 6,200 XP (level 6), but the fourth area's mobs are level 9. A discovery reward or an EXP_CHART step needs changing.

<!-- from: character-progression/cp-progression-report -->

<a id="dt37"></a>

## DT37. ORGCHECK: check faction files, governments and clan references

*Kind:* builder tool · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Mistakes in faction and clan data fail silently, and there is no check command. A faction file is read into a property map, so a later duplicate key replaces an earlier one. resources/examples/reputation.ini sets RANGE6 three times, and only 'world hero' survives. Bands can overlap or leave gaps. PresenceReaction drops unknown IDs. CHANGE triggers on unknown ability IDs never match. A mob whose \<CLAN> names a missing clan loses the tag at load.

**Add.** ORGCHECK [faction file|clan|government|ALL] in builder mode. It re-reads the raw ini and reports:
- duplicate keys, and gaps or overlaps between bands;
- CHANGE triggers that match nothing, the stock ability-ID rules included;
- unknown REACTION and AFFBEHAV IDs, masks that do not compile and missing RELATION targets;
- positions with no powers, or with an INNERMASK no one can pass.

It also lists mobs in the world files with an unknown \<CLAN>, and governments whose ENTRYSCRIPT does not parse.

**Example.** ORGCHECK thieves.ini reports 'RANGE3 and RANGE4 both contain 500', 'REACTION7: unknown ability Mod' and '3 mobs in harbor.cmare name clan Thieve Guild, which does not exist'.

*See also:* F5 (F5 fixes the ability-ID faction CHANGE rules that DT37 reports as matching nothing).

<!-- from: organizations-factions/org-orgcheck-lint -->

<a id="dt39"></a>

## DT39. DETERMINISTIC=1: exact replay for playtests

*Kind:* world option · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#dt39).

<a id="dt40"></a>

## DT40. SIMFIGHT: simulate the real party against an encounter

*Kind:* debug tool · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#dt40).
