# Quest templates for builders

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c07` (content: Quest templates for builders) · 74 files · 860,060 bytes of data.

## Summary

The 74 `.quest` files in `resources/quests/templates/` are QuestMaker templates: quest definitions with blanks that a builder fills in through a wizard (in game or on the web), or that the random-area generator fills from XML. Each file has three zones: a wizard header that declares the blanks, a short body in a small command language (`set`, `give`, `import`, `load`), and embedded XML item and mob lists plus MOBPROG-style `.script` files that are written out when the quest is created. The templates cover ten goal types (capture, collect, delivery, dispel, escort, invasion, killer, mystery, protect, travel) in three start modes: `auto` (the world assigns the quest to one random eligible player on a timer), `normal` (an announcer NPC offers it and each player says "accept") and `competitive` (an announcer offers it to many players and the first to finish wins).

These matter because they are a tested library of errand archetypes with tunable counts, timers, rewards and factions, and because the mystery template is a small deduction game whose clues come from a hidden solution. The design is data-first, which suits a Python SUD. The implementation is weaker: it relies on textual variable substitution and an embedded scripting language, the templates are hard to test, and at least one mystery variant cannot be won.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `resources/quests/templates/*.quest` | 74 templates: `auto` 23, `normal` 26, `competitive` 25. 26,696 lines in total, 202 to 988 per file | whole file |
| `Libraries/Quests.java` | `getQuestTemplate()` parses the wizard header into pages and blanks; `questMakerCommandLine()` is the in-game builder; the fill step writes the quest text; `parseQuestSteps()` splits quest text | 1452-1500, 1504-1660, 1836-1880, 2152-2171 |
| `Libraries/MUDPercolator.java` | `buildQuestTemplate()` instantiates a template for a random-area `<QUEST QUEST_TEMPLATE_ID=...>` tag; `replaceLineStartsWithIgnoreCase()` rewrites the duration line | 3328-3401 |
| `Libraries/interfaces/QuestManager.java` | `QMCommand` enum of the wizard blank types; `QM_COMMAND_OPTIONAL` flag | 477-515 |
| `Common/DefaultQuest.java` | Interpreter for the body: `SET`, `GIVE`, `LOAD`, `IMPORT`, `TAKE`, `STEP`, `OPTION` selection, the stat setter table, `pcmobgroup` selection | 827-849, 868-883, 1106-1178, 1202, 1507-1580, 3509-3513, 4060-4065, 6088-6126, 6222-6231, 6422-6534 |
| `Common/DefaultScriptingEngine.java` | Runs the embedded MOBPROG-style programs; inline `LOAD=` parsing; `mpsetvar ... ++` | 1073-1100, 2983-2994, 10168-10190 |
| `Abilities/Misc/QuestBound.java` | Effect behind `give stat keyplayer true`: resets the owning quest when the key mob dies, or on room reset, shutdown or expiry | 630-709 |
| `Libraries/interfaces/XMLLibrary.java` | `FILE_XML_BOUNDARY` (`<?xml version="1.0"?>`) that starts the embedded file section | 34 |
| `Common/interfaces/ScriptingEngine.java` | Documents the `QUEST_TIME_PROG` trigger values | 158-175 |
| `WebMacros/QuestMaker.java` | Web wizard; lists and reads templates through `getQuestTemplate` | 65, 144 |
| `resources/quests/random/*.xml` | Random-area quests that reference templates by `QUEST_TEMPLATE_ID`: 49 files, one per `auto` and `normal` template, plus `all.xml` and `inc_questcommon.xml`. Outside this bucket | n/a |
| `guides/Scriptable.html` | Reference for the program triggers (`rand_prog`, `delay_prog`, `all_greet_prog`, `quest_time_prog`, `mpalarm`) | entries by name |
| `core/CMProps.java` | `TIME_TICK = 4000` ms, the tick every timer uses | 681 |

## How it works

### 1. File anatomy

**Zone 1, wizard header.** The file opens with `#!QUESTMAKER_START_SCRIPT <name>` and closes its header with `#!QUESTMAKER_END_SCRIPT <name>`. Plain `#` lines are help text shown to the builder. `#!QUESTMAKER_PAGE <title>` opens a wizard page (4 to 10 pages per file). `#$NAME=$TYPE=default` declares a blank, and `#$NAME=($TYPE)=default` declares an optional one. Parsing is at `Quests.java:1541-1602`: the type word is looked up in the `QMCommand` enum, and a parenthesised type sets the optional flag (`Quests.java:1589-1593`, `QuestManager.java:482`). The enum has 22 entries (`QuestManager.java:491-515`): `$EXPRESSION`, `$UNIQUE_QUEST_NAME`, `$CHOOSE`, `$ITEMXML`, `$STRING`, `$ROOMID`, `$AREA`, `$MOBXML`, `$NAME`, `$LONG_STRING`, `$MOBXML_ONEORMORE`, `$ITEMXML_ONEORMORE`, `$ITEMXML_ZEROORMORE`, `$ZAPPERMASK`, `$ABILITY`, `$MEFFECT`, `$EXISTING_QUEST_NAME`, `$HIDDEN`, `$FACTION`, `$TIMEEXPRESSION`, plus the page-internal `$TITLE` and `$LABEL`.

Blank types in use across the templates:

| Type | Meaning in the templates |
|---|---|
| `$UNIQUE_QUEST_NAME` | quest ID, e.g. `normal_collect_1` (underscore form, while `questtype` uses `normal_collect1`) |
| `$TIMEEXPRESSION` | times such as `90 minutes`; the validator accepts ticks, minutes, hours, mud-days and so on (`Quests.java:2658-2676`) |
| `$EXPRESSION` | integer maths with a random operator, so `1 + 1?3` gives 2 to 4 (`normal_mystery2.quest:87`) |
| `$ZAPPERMASK` | who qualifies, in the zapper-mask language (`-JAVACLASS +GenResource`, `+Elf` style) |
| `$ITEMXML*`, `$MOBXML*` | a builder creates or selects an object; the result is stored as CoffeeMaker XML |
| `$CHOOSE=A,B` | fixed choices, e.g. `YES,NO,PREV` for `$MULTIPLE` (all 74 files) |
| `$HIDDEN` | computed at run time from quest state (`$%QVAR(* AGENT)%`) |

**Zone 2, body.** Short commands, one per line, using the blanks as text:

```
set name $QUEST_ID
set questtype normal_collect1
set wait 1
give stat keyplayer true
give script LOAD=$QUEST_ID_announcer.script
import items $QUEST_ID_items.xml
set duration 0
```

**Zone 3, embedded files.** A line `<?xml version="1.0"?>` (the constant at `XMLLibrary.java:34`) starts the section. It holds `<FILE><NAME>...</NAME><DATA>...</DATA></FILE>` blocks. XML blocks carry item or mob lists (`$REWARD_ITEMS`, `$COLLECTABLES_ITEMS`, `$SUSPECTS_MOBS`) that `import` and `load` read. `.script` blocks hold MOBPROG-style programs, each closed by a line containing only `~`. The programs seen in the templates are `GREET_PROG`, `ENTRY_PROG`, `ALL_GREET_PROG`, `SPEECH_PROG` (keyword triggers), `GIVE_PROG` and `GIVING_PROG`, `EXECMSG_PROG GET/DROP`, `DEATH_PROG`, `CNCLMSG_PROG` (cancel-hooks for death or entry), `CASTING_PROG`, `ONCE_PROG`, `RAND_PROG n`, `DELAY_PROG a b`, `QUEST_TIME_PROG * n` and `FUNCTION_PROG` helpers. Their trigger rules are in `guides/Scriptable.html`.

Abbreviated skeleton of `normal_collect1.quest`:

```
#!QUESTMAKER_START_SCRIPT Normal Collect #1
#!QUESTMAKER_PAGE Quest Name/Duration
#$QUEST_ID=$UNIQUE_QUEST_NAME=normal_collect_1
#$COLLECTABLES_ITEMS=$ITEMXML_ONEORMORE
#!QUESTMAKER_END_SCRIPT Normal Collect #1
set name $QUEST_ID
...
<?xml version="1.0"?>
<FILE><NAME>$QUEST_ID_items.xml</NAME><DATA><ITEMS>$COLLECTABLES_ITEMS</ITEMS></DATA></FILE>
<FILE><NAME>$QUEST_ID_announcer.script</NAME>
<DATA>
SPEECH_PROG p i accept
	...
~
</DATA></FILE>
```

Each template therefore builds a small package: one quest definition, its announcer NPC script, a per-player tracking script, and any item or mob lists.

### 2. From template to running quest

- **Wizard, in game.** `Quests.questMakerCommandLine()` (`Quests.java:1836-1880`) lists the templates (sorted by name, `Quests.java:1643-1658`) and prompts for one. The values are then written into the text in page order (`Quests.java:2155-2166`) with `CMStrings.replaceAll`, which is a literal replacement of every occurrence (`core/CMStrings.java:927`). `$#AUTHOR` becomes the builder's name (`Quests.java:2167`). After confirmation the text becomes a `DefaultQuest`.
- **Wizard, web.** `WebMacros/QuestMaker.java:65` and `:144` read the same templates through `getQuestTemplate`.
- **Random areas.** A random-area XML entry such as `<QUEST QUEST_TEMPLATE_ID="auto_collect2">` is handled by `MUDPercolator.buildQuestTemplate()` (`MUDPercolator.java:3343-3401`). It loads `quests/templates/<id>.quest` (`:3352-3354`), keeps the text after the last `#!QUESTMAKER_END_SCRIPT` (`:3358-3365`), replaces `$#AUTHOR` with "CoffeeMud" (`:3366`), rewrites the `set duration` line from `DURATION` or `EXPIRATION` (`:3367-3372`), and then replaces each header blank in declaration order with its value from the XML (`:3373-3391`). The random XML files reference all 23 `auto` and all 26 `normal` templates. None of the 25 `competitive` templates is used by the random generator.
- **Runtime.** `Quests.parseQuestSteps()` (`Quests.java:1452-1500`) splits the text at the `<?xml` boundary (`:1464`) and at `STEP`. `DefaultQuest` then runs the body commands.

### 3. Body interpreter

- **Commands.** `OPTIONAL`, `QUIET`, `STEP` (with `BREAK`, `BACK`, `AUTO`), `RESET`, `SET`, `GIVE`, `LOAD`, `IMPORT` and `TAKE` (`DefaultQuest.java:1106-1178`, `:1202`, `:3351`, `:3509-3518`, `:3791`, `:4219`). `SET` subjects include `AREA`, `ROOM`, `ROOMGROUP`, `MOB`, `MOBGROUP`, `PCMOBGROUP`, `ITEM`, `ITEMGROUP`, `ABILITY`, `FACTION`, `AGENT`, `AGENTGROUP`, `TARGET`, `TOOL`, `MOTIVE`, `WHEREHAPPENED` and `WHENHAPPENED`.
- **Plain `set NAME value`.** A name that is not a subject falls through to the stat setter (`DefaultQuest.setStat`, `DefaultQuest.java:6422-6534`). Index 2 is `DURATION` (a tick expression), 3 `WAIT`, 4 `MINPLAYERS`, 5 `PLAYERMASK`, 9 `INTERVAL`, 13 `PERSISTANCE`, 15 `EXPIRATION` (sets the duration and marks the instance as a copy, `:6495-6513`), 16 `QUESTTYPE` and 17 `CATEGORY`. Any other name is stored as a quest variable (`:6520-6527`).
- **`<OPTION>` groups.** Consecutive `<OPTION>` blocks are a random choice. One block is picked uniformly and spliced in (`DefaultQuest.java:868-883`; parsed at `:6222-6231`). The clue files depend on this (see mystery below).
- **`LOAD=file args`.** Loads another text, either a `FILE` block or a resource, and passes arguments that the text reads as `ARG1`, `ARG2` and so on (`DefaultQuest.java:3509-3513`, `parseLoadScripts` at `:6088-6126`). Example: `LOAD=$QUEST_ID_toolagentmap.quest wherehappened toolgroup-tool agentgroup-agent` in `competitive_mystery1.quest:368`.
- **Picking players.** `set pcmobgroup reselect any mask=...` walks all online sessions (`DefaultQuest.java:1539-1556`), keeps those that match the zapper mask and any area or room group, and removes players already held by another quest unless they are reselectable (`filterOutThoseInUse`, `DefaultQuest.java:827-849`). If nobody qualifies, the step fails with `!mobgroup` (`:1573-1577`).
- **Program engine.** `~` ends a block (`DefaultScriptingEngine.java:1099` for inline `LOAD=`). `mpsetvar X ++` adds one to a variable (`DefaultScriptingEngine.java:2988-2993`).

### 4. Three start modes

| Mode | Count | Start and clock | Who takes it | How it ends |
|---|---|---|---|---|
| `auto` | 23 | `set wait $FREQUENCY` (for example `auto_collect5.quest:86`), `set duration $DURATION` (`:95`) | `set pcmobgroup reselect any mask=$QUEST_CRITERIA` (`:92`) gives it to one random eligible online player, who receives `playertrack.script`. No accept step | Goal reached, or timer runs out |
| `normal` | 26 | `set wait 1`, `set duration 0`: open-ended | An announcer NPC (`give stat keyplayer true`, `normal_collect1.quest:142`) offers it. Each player says "accept" (`SPEECH_PROG p i accept`, `normal_collect1.quest:249-255`), which runs `CAN_ACCEPT` then `DO_ACCEPT` | Goal reached, or the announcer dies (see section 5) |
| `competitive` | 25 | `set wait $FREQUENCY`, `set minplayers 1` (`competitive_collect1.quest:165`), `set playermask $QUEST_CRITERIA`, `set duration $DURATION` | Many players accept. Each has its own counters (`$QUEST_ID_ACCEPTED`, `_COLLECTED`, `_GOT`) | The first player to finish wins. `mpendquest *` closes the instance for all (`competitive_collect1.quest:291-293`) |

Other cross-cutting features:

- `$MULTIPLE` (`YES`, `NO`, `PREV`) is in all 74 files. Each win check tests `QUESTWINNER($n *)` unless `YES` is chosen. `PREV` requires a prior win (`QUESTWINNER($n previous)`).
- `$NEXT_QUEST` appears only in the 25 `competitive` files. A win runs `mpstartquest $NEXT_QUEST` (`competitive_collect1.quest:296`), which makes a chain of quests.
- The `auto` family defines `CAN_ACCEPT` and `DO_ACCEPT` but never calls them (see Problems).

### 5. The key-entity guard

`give stat keyplayer true` attaches a `QuestBound` effect to the announcer (`DefaultQuest.java:4060-4065`). The effect resets the owning quest when the key is killed, when its room is reset, on shutdown, or on expiry (`QuestBound.java:642-665`, reset in `:667-689`). A tick check also resets when the key mob is dead or destroyed (`:692-702`). The reset calls `Q.resetQuest(5)` (`:679`) and removes the effect once no quests remain attached. The templates add their own checks as well. For example, each `playertrack` `RAND_PROG` runs `IF !isquestmobalive(1 '$QUEST_ID') MPENDQUEST` (`normal_delivery1.quest` playertrack block; `normal_dispel1.quest` likewise).

### 6. Mechanics by goal type

Counts are per the table in Content worth mining.

**Collect (15 files).**
- Items are imported from an XML list and placed on mobs, in rooms, or on the ground (`competitive_collect1.quest:182`, `load itemgroup $COLLECTABLES_CREATED any`).
- Progress is tracked per player. `auto_collect1.quest` uses `EXECMSG_PROG GET ALL` and `EXECMSG_PROG DROP ALL` on the player (`:252` and `:262`), and `competitive_collect1.quest` uses `DROP_PROG all` (`:358`). The announcer's `GIVE_PROG` counts handed-in items and pays out at `$NUM_COLLECTABLES` (`normal_collect5.quest:192`). The coin version uses `BRIBE_PROG` instead (`normal_collect5.quest:154`): it adds the pile's amount, consumes the coins with `mpjunk` (`:157`), and checks the target.
- Coins are matched by currency and amount: `$%GSTAT($o CURRENCY)%` against the player's currency and `$%GOLDAMT($o)%` (`auto_collect5.quest:135-150`). The `auto` coin variant does not hand the money in (see Problems).
- The default collectable is `-JAVACLASS +GenResource` (`$COLLECTABLE_ITEMS`), so any generic resource qualifies. The description says these are obtained "from your existing world, or generated with common skills".

**Delivery (12 files).**
- The deliverer gives a custom item to the player (`DO_ACCEPT` loads it from the item XML and marks it `QUESTOBJ`, `normal_delivery1.quest` `DO_ACCEPT`).
- Recipients match `$DELIVEREE_MASK` and are greeted with an announcement line (the player-track `GREET_PROG`, `normal_delivery1.quest:278`).
- `GIVING_PROG` checks that the item is the right one and records the recipient as delivered (`normal_delivery1.quest:295`).
- The announcer's `GREET_PROG` pays out when the delivered count reaches `$DELIVEREE_COUNT` (`normal_delivery1.quest:177-199`).
- The `delivery2` variant delivers into a container or room. The `delivery4` variant delivers money. Both are summarised, not traced in detail.

**Capture (11 files), `auto_capture1.quest`.**
- Targets match `$CAPTURABLES_MASK`. Behaviour options are `$CAPTURABLES_FOLLOW` (`VIOLENT` or `PEACEFUL`, `:74`) and `$CAPTURABLES_ATTACK` (`NO-USE-DEFAULT_BEHAVIOR` or `YES-ATTACK-ON-SIGHT`, `:82`).
- Three paths capture a target: `GREET_PROG` on sight (`:190`), `ENTRY_PROG` when the player walks in (`:208`), and `CNCLMSG_PROG STO=DEATH` (`:271`). The last one is the key trick. A lethal blow is cancelled, the target's hits are set to 1, it stops fighting, says its surrender line, and then follows the player.
- `CHECK_DONE` (`:234`) fires when the count reaches `$NUM_CAPTURABLES`. It tells each captured follower to stop following (`mpforce $1 follow self`) and purges it (`mppurge $1`, `:239-240`), so captured mobs are removed from the world on completion.

**Killer (6 files).**
- `competitive_killer1.quest` `killable.script` counts deaths in `DEATH_PROG` (`:340`). When the killer is an NPC follower, the kill is credited to its leader (`isfollow`).
- The announcer's `ALL_GREET_PROG` (`:246`) checks `KILLED >= NUM_KILLABLES` (`:267`) and only then pays out. Kills alone never finish the quest. The player must walk back into the announcer's room, because `all_greet_prog` runs on any arrival (`guides/Scriptable.html`).

**Escort (6 files).**
- The escorted NPC is told to follow the player (`mpforce $b follow $n`, `normal_escort1.quest:219`).
- Every tick, `RAND_PROG 99` (`competitive_escort1.quest:325`) checks whether the escorted NPC is in `$ESCORT_DESTINATION`. If it is, the PC it follows (`$f`) is paid out. If it is not, then with `$ATTACKER_PCT_CHANCE` percent chance (default 5, `:130`) the program spawns `$ATTACKER_SIZE` attackers, once per instance. The attackers yell `$ATTACKER_SCREAM` (default "Found you! Charge!", `:135`) and attack the escort.
- The escort's `DEATH_PROG` (`:316`) fails the quest. Arrival pays out, calls `mpquestwin $f *` (`:375`) and then `mpendquest *`.

**Protect (5 files).**
- Attackers are spawned by `RAND_PROG $ATTACKER_PCT_CHANCE` (`auto_protect1.quest:310`). They only spawn while the player is in the protected room (`INROOM` checks in the same block).
- Each attacker's death increments `DEFEATED` (`auto_protect1.quest:360`). The win is in `attacker.script` (`competitive_protect1.quest:367-369`).
- The protected NPC's death fails the quest.

**Invasion (2 files, competitive only), `competitive_invasion1.quest`.**
- Invaders are loaded with `load mobgroup all` and given `give behavior Mobile min=1 max=10 chance=99` (`:202`). The invader's `DEATH_PROG` adds to `KILLS` (`:340-353`, inside `invader.script`).
- The first player to reach `$INVADER_KILLS` (default 3, `:110`) wins (`:244`, `mpquestwin` at `:264`).
- At timeout, `QUEST_TIME_PROG * -1` in the announcer (`:220-222`) plays `$INVADER_WINS_RESPONSE_TEXT`, so the invaders win.

**Travel (10 files).**
- `competitive_travel1.quest` `travelroom.script` uses `ENTRY_PROG 100` (`:260`). The first accepted player to enter the destination room who has not yet been rewarded wins (`mpquestwin` at `:283`, text `$TRAVEL_RESPONSE`).
- The `travel2` and `travel4` variants send the player to a destination NPC (`destination.script`). `travel4` also requires a verbal message. `normal_travel3` requires the player to use a named skill there (`import abilities destinationskill.xml`, `$DESTINATION_SKILL_ID`). The skill details were not traced.

**Dispel (3 files).**
- Afflicted mobs (`$HELPABLES_MASK`) get a `TemporaryAffects` effect bound to the announcer (`normal_dispel1.quest:316`, `MPCASTEXT TemporaryAffects $1 +BINDTO $i`).
- The affliction spreads to nearby eligible mobs in `DISTRIBUTE_DISEASE` (`:308`) and on the player's entry (`CNCLMSG_PROG ENTER ALL`, `:328`).
- The player cures a mob by casting `$HELPABLE_ABILITY` on it (`CASTING_PROG P`, `:378`). Cures count toward `$NUM_HELPABLES`.
- `competitive_dispel1` instead applies a real ability with `give affect $DISPEL_SPELL $DISPEL_SPELL_PARMS` (`:221`; default `Disease_Plague`, `:46`). See Problems for a substitution bug there.

**Mystery (4 files: normal 1 and 2, competitive 1 and 2).**
- **Hidden solution.** A victim is set up with `VICTIM_NAME` and rejuvenation (`competitive_mystery1.quest` victim block). `VICTIM_DIES` (`TRUE` or `FALSE`, `:101`) controls whether the victim really dies. `set wherehappened room` and `set whenhappened -1?-10` (`:326-327`) define when and where the offence happened.
- **Three categories.** `NUM_SUSPECTS` (3, `:205`) suspects are "agents", `NUM_TOOLS` (3) tools sit in `TOOLS_ROOM`, and `NUM_MOTIVES` (3) motives are drawn from `$MOTIVE_LIST` (`:246`). The two-level variants use only agent and motive.
- **Per-player answer.** The playertrack `ONCE_PROG` in `normal_mystery1.quest` picks one suspect, tool and motive for each player (`QVAR(* AGENTGROUP#?)`, `MOTIVEGROUP#?` and so on). How that draw relates to the clue-generating solution was not traced.
- **Clues.** The player's tracking script (`normal_mystery1.quest`, from line 400) has `AskedAboutAgent`, `AskedAboutTool` and `AskedAboutMotive` (`:442`, `:544`, `:636`). When the player's speech names a suspect, tool or motive, `SPEAK_PROG all` (`:728-748`) calls the matching function. A witness in the room that matches `$WITNESS_MASK` and has not yet spoken to the player then gives a gossip line about the suspect and a linked tool or motive. The competitive variant adds placed evidence from the `toolagentmap`, `agentmotivemap` and `toolmotivemap` sub-files (`competitive_mystery1.quest:642`, `:764`, `:836`), whose `<OPTION>` blocks pick one line at random; `normal_mystery1` has no such files.
- **Repeat control.** `DELAY_PROG 15 30` clears the "already spoken to" list (`normal_mystery1.quest:434`), and `DELAY_PROG 150 300` clears the "evidence already given" list (`:438`). Delays are in ticks (`guides/Scriptable.html`, `delay_prog`).
- **Guessing.** `SPEECH_PROG all` (`normal_mystery1.quest:248`) checks whether the player's speech contains a candidate's name. A match records the guess and calls `winner` (`:353`). A win needs `CORRECT >= 3` (`:356`). A wrong full guess sets a lockout with `mpalarm 150` (`:386`), which is 150 ticks, or about 10 minutes at 4 seconds a tick, and the message says "Try again in 10 minutes".

### 7. Timing and numbers

- The tick is 4000 ms (`CMProps.java:681`). `mpalarm` counts ticks of that length (`guides/Scriptable.html`, `mpalarm`).
- `$FREQUENCY` and `$DURATION` default to `90 minutes` in 48 files (every `auto` and `competitive` template).
- `$EXP` defaults to `10%` in all 74 files, meaning a percentage of experience to the next level (described in the header).
- `QUEST_TIME_PROG * 1` gives a "1 minute remaining" warning in 71 files. `QUEST_TIME_PROG * -1` gives the end message. The guide says `-1` fires when a quest step completes and `-2` when the script is removed (`guides/Scriptable.html`, `quest_time_prog`; `ScriptingEngine.java:158-175`).
- The in-game settings editor has its own defaults, `DURATION` 900 ticks and `WAIT` 100 ticks (`Quests.java:997`, `:1031`). The templates do not use these.

### 8. Messages shown to players

Short, verbatim examples from the templates:

- Ten-minute lockout: "I'm sorry $n, your guess is incorrect.  Try again in 10 minutes." (`normal_mystery1.quest:385`)
- Warning: "You have 1 minute to complete my quest." (`competitive_collect1.quest` `QUEST_TIME_PROG * 1`)
- End: `The quest "$QUEST_NAME" has ended.` (`auto_collect5.quest:197`)
- Surrender default: "I surrender" (`auto_capture1.quest:87`)
- Attack default: "Found you! Charge!" (`competitive_escort1.quest:135`)
- Travel completion default: "Your journey is complete." (`competitive_travel1.quest:137`)

## Content worth mining

### Goal types by start mode

Counts from the 74 file names.

| Goal type | auto | normal | competitive | Player's task |
|---|---|---|---|---|
| Capture (11) | 4 | 4 | 3 | Subdue or peacefully take existing or created mobs. Captured mobs follow, then are removed on completion |
| Collect (15) | 5 | 5 | 5 | Gather items or money scattered through the world, then return them |
| Delivery (12) | 4 | 4 | 4 | Carry a custom item or money to named mobs, a container or a room |
| Dispel (3) | 1 | 1 | 1 | Cure afflicted mobs by casting a named ability on them |
| Escort (6) | 2 | 2 | 2 | Lead an NPC to a room or area while attackers may ambush |
| Invasion (2) | 0 | 0 | 2 | Kill a set number of invaders before the timer runs out |
| Killer (6) | 2 | 2 | 2 | Kill target mobs and return to the announcer |
| Mystery (4) | 0 | 2 | 2 | Work out who (and, in the three-level variants, with what tool) and why, from witnesses |
| Protect (5) | 1 | 2 | 2 | Keep a mob alive against spawned attackers |
| Travel (10) | 4 | 4 | 2 | Reach a room, or deliver a message to an NPC, possibly using a skill there |

### Defaults the templates ship with

| Blank | Default | Where |
|---|---|---|
| `$FREQUENCY`, `$DURATION` | `90 minutes` | 48 files |
| `$EXP` | `10%` | all |
| `$ATTACKER_PCT_CHANCE` | `5` | `competitive_escort1.quest:130` |
| `$ATTACKER_SCREAM` | "Found you! Charge!" | `competitive_escort1.quest:135` |
| `$INVADER_KILLS` | `3` | `competitive_invasion1.quest:110` |
| `$DISPEL_SPELL` | `Disease_Plague` | `competitive_dispel1.quest:46` |
| `$COLLECTABLE_ITEMS` | `-JAVACLASS +GenResource` | `auto_collect4`, `normal_collect4`, `competitive_collect4` |
| `$MOTIVE_LIST` | "drunken anger", "jealous love", "insulted honor" | `competitive_mystery1.quest:246` (one variant adds "insurance money") |
| `$CAPTURABLES_SURRENDER` | "I surrender" | `auto_capture1.quest:87` |
| `$TRAVEL_RESPONSE` | "Your journey is complete." | `competitive_travel1.quest:137` |
| `$NUM_*` counts | 1, 3, 5 or 100 | per file |

### Mystery vocabulary

- **Gossip frames.** The witness gossip is a 12-way switch (`normal_mystery1.quest:486-508`). Paraphrased: "X is scared of Y", "X hates Y", "X thinks Y is creepy", "X thinks Y is clumsy and awkward", "X never touches Y", "X won't use Y", "X doesn't like Y", "X wouldn't be caught dead using Y", "X is too proud to use Y", "X doesn't know how to use Y", "X is too dumb to figure out Y", "X is far too skilled for Y".
- **Crowd lines.** Ambient "news" lines in the `toolagentmap` evidence sub-file (`competitive_mystery1.quest:836` onward), such as "Everyone is talking about it." and "Just terrible...." These are short and reusable.
- **Motive strings.** "drunken anger", "jealous love", "insulted honor", "insurance money". Short and readable as a set of motives for any murder or theft plot.

### Other content

- The witness radius (default 5), witness mask (`$ZAPPERMASK`, no default) and witness names (default `ANY MASK=+INT`, a zapper mask that selects by intelligence) define which NPCs can speak in the mystery.
- The `auto_*` quest text is generic enough to reuse as errand descriptions. The header of each template holds a one-sentence plain-English description (for example, "The player needs one or more other existing mobs captured. A reward is granted upon completion of the task."). These are good seeds for a Python quest catalogue.

## Notable design ideas

- **Mode is separate from goal.** Ten goals times three starts gives 30 combinations. The templates realise 27 of them (`auto` has no mystery or invasion, `normal` has no invasion). Similar goal scripts (`ENTRY_PROG`, `GIVE_PROG`, the kill counters) recur across modes. The start mode decides who gets the quest and how the clock runs.
- **Quests as packages.** Each template emits one quest, its announcer, its player tracker and its item or mob lists, so a template is a complete module.
- **Key-entity lifecycle.** A quest holds a key NPC through an effect. If the NPC dies, the quest resets, so cleanup follows from the world model rather than explicit cancel code (`QuestBound.java:642-702`).
- **Mystery as a solvable puzzle.** The solution is three categories of three. The clues are pairwise facts (agent with tool, agent with motive, tool with motive). Each witness reveals a clue, and each player gets a fresh draw of the answer. Each category guess is checked on its own, three correct category guesses win, and a wrong full guess locks out for ten minutes. This is a clean constraint puzzle that a single-player game can reuse.
- **Random variant text.** Consecutive `<OPTION>` blocks give a cheap random choice, so one template can produce several wordings or effects (`DefaultQuest.java:868-883`).
- **Dice-like expressions for counts and times.** `1 + 1?3` gives 2 to 4 and `-1?-10` gives a random offset. The `?` operator is a compact way to express ranges in data (`Quests.java:2658-2676`).
- **Chance-based spawns with a warning.** Attackers spawn with a per-tick percentage and announce themselves with a line before they attack (`competitive_escort1.quest:130-135`).
- **Capture by surrender.** Converting a lethal blow into a surrender (`auto_capture1.quest:271`) makes non-lethal capture a first-class option, and a peaceful mode covers lost NPCs.

## Problems and cruft

1. **`normal_mystery2` cannot be won.** The file increments `CORRECT` only twice, once for the agent (`normal_mystery2.quest:245`) and once for the motive (`:276`). `winner` requires `CORRECT >= 3` (`:305`). After both answers are given, the wrong-guess branch fires (`:334`), so a correct answer is reported as incorrect. The competitive two-level variant uses `>= 2` (`competitive_mystery2.quest:485`), so the normal one looks like a copy error. Confirmed by listing every `CORRECT` reference in the file; not run.
2. **Wrong count in a wizard label.** `competitive_mystery2.quest:227` says the reward is for "the three correct answers", but its win is at two (`:485`).
3. **Blank-name collision in `competitive_dispel1`.** `$DISPEL_SPELL` is declared at line 46, before `$DISPEL_SPELL_PARMS` at line 48. Substitution is literal and runs in declaration order (`MUDPercolator.java:3373-3391`, `Quests.java:2155-2166`). Replacing `$DISPEL_SPELL` therefore also rewrites the start of `$DISPEL_SPELL_PARMS` on line 221, so `give affect $DISPEL_SPELL $DISPEL_SPELL_PARMS` becomes `give affect Disease_Plague Disease_Plague_PARMS` and the parameter is lost. Found by reading the substitution code; not run. A scripted scan of all 74 headers found no other case of this pattern.
4. **Coin collection can be inflated.** `auto_collect5.quest` header line 2 says the money must be "brought to him/her", but the code only counts pickups. `EXECMSG_PROG GET ALL` (`:135-150`) adds each pile's amount to the player's total. There is no `DROP` handler (the file has no `DROP` at all) and no hand-in step. Dropping the coins and picking them up again adds them again, and the quest completes on the running total. Compare `normal_collect5` (`BRIBE_PROG`, `:154`, which consumes the coins) and `competitive_collect1` (`DROP_PROG`, `:358`).
5. **Dead code in all 23 `auto` templates.** `FUNCTION_PROG CAN_ACCEPT` and `DO_ACCEPT` are defined (for example `auto_capture1.quest:139` and `:150`), but no `auto` file calls them. They were copied from the `normal` skeleton.
6. **Duplicate line.** `auto_protect1.quest` sets `set duration $DURATION` twice (`:139` and `:147`). Harmless, because the value is the same.
7. **Duration and expiration interplay.** `MUDPercolator.java:3367-3372` runs `replaceLineStartsWithIgnoreCase` for `DURATION`, then again for `EXPIRATION` on the same "set duration" text. The second call matches the line the first call wrote, so when both are given only `EXPIRATION` survives. `SET EXPIRATION` is a valid setter (`DefaultQuest.java:6495-6513`), and no template or random XML sets it (grep), so the path is latent.
8. **Fragile naming by substitution.** `$QUEST_ID` is substituted first because it is declared first, which deliberately gives each quest a name prefix. For example `$QUEST_ID_COLLECTED` becomes `auto_collect_5_COLLECTED` (`Quests.java:2164`, `MUDPercolator.java:3387`). It works only because of declaration order, and item 3 is the one case where the order breaks it.
9. **Timer semantics.** Templates use `QUEST_TIME_PROG * -1` as the end-of-quest message (`auto_collect5.quest:196`), while the guide says `-1` fires when a step completes and `-2` when the script is removed (`guides/Scriptable.html`). For single-step quests these usually coincide. Not verified at run time.
10. **Killer completion surprise.** Kills alone never finish a killer quest. The check lives in the announcer's `ALL_GREET_PROG` (`competitive_killer1.quest:246-267`). The header says "returning to the announcer", so this is documented, but it is easy to miss.
11. **Multiplayer assumptions.** Selection walks all online sessions (`DefaultQuest.java:1539-1556`). Race rules (first winner closes the quest for everyone with `mpendquest *`) and the `MULTIPLE`/`PREV` gating assume other players. A single-player port needs to replace these.
12. **Template count and reach.** 25 `competitive` templates have no random-area XML, so only the wizard can create them. `resources/quests/random/` has 51 files: 49 named after `auto` and `normal` templates, plus `all.xml` and `inc_questcommon.xml`.
13. **Typos.** "Optinal parameters" (`competitive_dispel1.quest:47`), "from mobs you also you create" (`normal_protect1.quest:2`), and a double space in the `auto_collect5` name (`:1`). Cosmetic.
14. **No tests.** None of the 87 files in `com/planet_ink/coffee_mud/Tests/` loads or checks a quest template, so nothing checks that a win condition is reachable. Item 1 is the kind of bug such a test would catch.

## Takeaways for a Python SUD

- **Model the template as data plus classes.** Replace the text DSL with a `QuestTemplate` dataclass and a set of goal classes, one per goal type. Each goal class defines what counts as progress and what counts as completion, using events on the world model (enter room, pick up, die, give, speak). Templates can be TOML or YAML validated against a schema, with the typed blanks (`Count`, `Duration`, `Mask`, `Reward`) as the schema types.
- **Keep the three starts, reinterpret them.** `assigned` (the world hands an errand to one player on a timer), `offered` (an NPC gives it and the player accepts), and `race` (single-player: a rival NPC or a world clock stands in for other players, or drop it). The `PREV` and `NEXT_QUEST` chains map cleanly onto a "completed" set in the player's state.
- **Port the mystery directly, with a test.** Generate the three-by-three-by-three solution first. Generate clues only from true pairs. Witnesses give one clue per conversation and remember whom they have talked to. A guess is a full match, and a wrong guess sets a game-time cooldown (here 10 minutes). Add a test that the solution is derivable from the clue set and that the win threshold is reachable. That test would have caught `normal_mystery2`.
- **Use an explicit key-entity invariant.** A quest stores the NPC or room it depends on. An event (death, room removed, area reset) cancels or resets it. In Python this is an observer or event-bus hook, not string-matched cleanup.
- **Keep the random expression idea, not the string machinery.** A small parser for `1 + 1?3` and `90 minutes` covers counts and durations. Parse it once when the template loads.
- **Drop the multiplayer machinery.** Remove the session walk, the per-player "already accepted" bookkeeping and the first-winner-ends-all rule. Keep per-quest state for the single player.
- **Use game time, not ticks.** The 4-second tick is an engine default. A Python SUD can measure time in game minutes and pick its own units, so `90 minutes` becomes a configuration value.
- **Avoid these patterns.** Textual `$VAR` substitution with declaration-order dependence, copy-pasted skeletons (much of the announcer and tracker code is repeated across the 74 files, so a Python port should derive it from a shared base class), dead functions, and a DSL that is only checked at run time.
- **Borrow the content.** The goal list, the default blanks, the mystery gossip frames and the motive list are all short, neutral and reusable. The bug list is a checklist for the Python port's tests.

## Coverage

- **Read in full:** `auto_capture1.quest` (body), `auto_collect5.quest` (whole file), `normal_delivery1.quest` (body), `competitive_collect1.quest` (body).
- **Read in large part:** `normal_collect1.quest`, `normal_dispel1.quest`, `competitive_escort1.quest`, `competitive_invasion1.quest`, `competitive_killer1.quest`, `competitive_travel1.quest`, `normal_mystery1.quest` (setup, guess handling, clue functions), `normal_mystery2.quest` (guess handling and winner), `competitive_mystery1.quest` (setup and sub-file list), `competitive_mystery2.quest` (header and winner), `auto_protect1.quest` (parts).
- **Scanned across all 74:** headers and description lines, blank declarations, `set`, `give`, `import` and `load` directives, `QUEST_TIME_PROG`, `mpquestwin`, `CORRECT` counters, `DROP` and `GET` handlers, `CALLFUNC` uses, embedded file names, and a scan for personal data (none found; `@` hits were script syntax) and mature content (violence and a murder mystery with a possibly dying victim; no sexual content and no references to children).
- **Code read:** `Quests.java` (1452-1500, 1504-1660, 1836-1880, 2152-2171, 2658-2676, 985-1060), `MUDPercolator.java` (3328-3401), `QuestManager.java` (477-516), `DefaultQuest.java` (827-849, 868-883, 1106-1210, 1507-1580, 3341-3348, 3509-3518, 4052-4070, 6088-6126, 6222-6231, 6422-6534), `DefaultScriptingEngine.java` (1073-1100, 2976-3001, 10168-10190), `QuestBound.java` (630-709), `ScriptingEngine.java` (158-175), `XMLLibrary.java:34`, `CMStrings.java:927`, `CMProps.java:681`, and the `guides/Scriptable.html` entries for `rand_prog`, `delay_prog`, `all_greet_prog`, `quest_time_prog` and `mpalarm`.
- **Skipped:** the remaining body lines of most templates (they repeat the same skeleton), the `travel3` and `travel4` skill and message details, the full clue tables in `competitive_mystery1.quest`, the `invader.script` body, the `resources/quests/random/` XML (another bucket), and the holiday quests.
