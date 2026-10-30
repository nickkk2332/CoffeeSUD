# Commands, part 5: builder tools, item and group verbs, speech-driven NPC verbs, and intermud

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b114` (Commands [part 5/12: FactionList .. Ignore]) · 30 files · 6487 lines of Java.

## Summary

This bucket holds 30 typed verbs from FACTIONS to IGNORE. Five of them (Get, Give, Go, Follow and Flee) are core verbs that chapter b170 covers in depth, so they appear here mainly where they differ or where a defect sits. The rest are secondary verbs for taking and giving things, grouping, multi-step movement, talking to hired help, listing standings and help, and three intermud connectors. Three are substantial. GMODIFY is a search-and-patch language that matches live rooms, items and mobs and rewrites their fields. GENERATE builds content from random-area XML templates, and GenCommand defines a whole verb as data with an optional script. Speech-driven verbs (HIRE, FIRE, GAIN) send one line of speech, and NPC behavior reacts to keywords in it, so the verb itself stays tiny. For a single-player game the useful parts are the builder query language, the data-defined verb, the consider ladder in GCONSIDER and the keyword-reactive NPCs; the friends, ignore, group-leadership, intermud and network-address checks are multiplayer-only. Several concrete defects are listed under Problems, the most serious being a ClassCastException in GMODIFY REBALANCE on mobs and a GET path that moves a non-gettable container before the refusal.

## Key files and types

Java paths omit the `com/planet_ink/coffee_mud/` prefix, as chapter b170 does, so `Commands/Get.java` means `com/planet_ink/coffee_mud/Commands/Get.java`. Non-Java paths are given in full. Line counts come from the manifest. The command contract (`Commands/interfaces/Command.java`) and its base class (`Commands/StdCommand.java`) are described in b170 and are only summarized here. Get, Give, Go, Follow and Flee are also core verbs in b170, so their rows below cover only what this bucket adds.

| File | Access words and role | Lines |
|---|---|---|
| `Commands/GModify.java` | `GMODIFY`. Builder search-and-patch over rooms, areas, the world, players or tagged objects | 1352 |
| `Commands/Generate.java` | `GENERATE`. Builder: instantiate areas, rooms, mobs, items, quests and scripts from XML templates | 447 |
| `Commands/GenCommand.java` | Base for verbs defined as data (`CLASS`, `ACCESS`, `SCRIPT`, costs, security mask) | 296 |
| `Commands/Get.java` | `GET`, `G`. Take items from the room or from containers, with `all`, counts and `UNOBTRUSIVELY` | 284 |
| `Commands/Go.java` | `GO`, `WALK`. Walk, compound and repeated directions, queued steps. Base of the direction verbs and of Flee | 283 |
| `Commands/Group.java` | `GROUP`, `GR`. Group status table; `GROUP LEADER` hands leadership to a follower | 283 |
| `Commands/GConsider.java` | `GCONSIDER`, `GCOS`, `GCO`. Group-versus-target odds, given as a phrase | 235 |
| `Commands/Give.java` | `GIVE`, `GI`. Hand items or coins to a visible mob | 231 |
| `Commands/I3Cmd.java` | `I3`. Interface to the I3 intermud network | 212 |
| `Commands/Follow.java` | `FOLLOW`, `FOL`, `FO`, `F`. Follow a mob, or stop following | 210 |
| `Commands/GTell.java` | `GTELL`, `FTELL`, `GT`. Group chat, follower-tree chat, replay of recent group tells | 210 |
| `Commands/Flee.java` | `FLEE` (extends Go). Leave combat by a random or chosen exit, with an XP penalty and reward | 184 |
| `Commands/Grapevine.java` | `GRAPEVINE`, `GV`. Interface to the Grapevine intermud network | 180 |
| `Commands/Fill.java` | `FILL`. Fill a container from another container, or from a drink source in the room | 179 |
| `Commands/Ignore.java` | `IGNORE`. Personal ignore list with categories | 176 |
| `Commands/FactionList.java` | `FACTIONS`, `FAC`. Standing with each faction, drawn as a range bar | 171 |
| `Commands/Goto.java` | `GOTO` (extends At). Admin teleport by room id, player, area or text, with a `PREVIOUS` stack | 159 |
| `Commands/Feed.java` | `FEED`. Give food or drink to an ordered or bound mob, which then eats or drinks it | 153 |
| `Commands/Formation.java` | `FORMATION`. Assign followers to combat rows | 142 |
| `Commands/Hold.java` | `HOLD`, `HOL`, `HO`, `H`. Hold or wield items | 137 |
| `Commands/Friends.java` | `FRIENDS`. Personal friends list | 127 |
| `Commands/IMC2.java` | `IMC2`. Interface to the IMC2 intermud network | 126 |
| `Commands/Help.java` | `HELP`, `?`. Topic lookup with see-also links and a search fallback | 118 |
| `Commands/Foreward.java` | `FOREWARD`, `FORE` (extends Go). Hidden "forward" move for rooms that do not use compass directions | 92 |
| `Commands/Fire.java` | `FIRE`. Say "You are fired!" to a named mob | 90 |
| `Commands/HelpList.java` | `HELPLIST`, `HLIST`. Search help topics by pattern | 90 |
| `Commands/Hire.java` | `HIRE`. Say a hiring line, to a named mob or to the room | 86 |
| `Commands/Gain.java` | `GAIN`. Turns its words into a SAY, for trainers | 80 |
| `Commands/Gait.java` | `GAIT`, `NOGAIT`. Sets movement gait by invoking the Gait ability | 77 |
| `Commands/History.java` | `HISTORY`. Shows the session's recent input lines | 77 |

Sum of the Java line counts: 6487, matching the manifest.

Other files read for context: `Commands/Train.java` (grep only, for GAIN), `Commands/Qualify.java` (grep only), `Behaviors/Hireling.java`, `Libraries/CommonMsgs.java`, `Items/Basic/StdItem.java`, `Locales/StdRoom.java`, `Libraries/EnglishParser.java`, `core/CMParms.java`, `Libraries/CoffeeMaker.java`, `Libraries/MUDFight.java`, `core/CMProps.java`, `Libraries/Sense.java`, `Libraries/WorldHuntUtils.java`, `Libraries/IntermudClient.java` (stopIntermud only), `coffeemud.ini`, `resources/help/help.ini` and `resources/help/arc_help.ini`.

## How it works

### The shared contract

Every verb returns its access words from `getAccessWords()`, built with `I(...)` so the words are translated (for example `Commands/Get.java:41`, `Commands/StdCommand.java:78-83`). `ID()` is the class's simple name (`Commands/StdCommand.java:40-48`). The StdCommand defaults are an instant cost, orderable, and always securable (`Commands/StdCommand.java:223-274`). Most verbs here override only what differs.

Three cost styles appear, and the difference matters for play:

- **Zero.** No override, so the verb is instant unless configured. GMODIFY, GENERATE, GROUP, GTELL, FORMATION, FRIENDS, IGNORE, FACTIONS, GOTO, HELP, HELPLIST, GAIT, HISTORY and the intermud verbs use this.
- **One-argument defaults.** `CMProps.getCommandActionCost(ID())` is DEFCMDTIME/100, which is 0.20 by default. The combat variant is DEFCOMCMDTIME/100, which is 1.00 by default (`core/CMProps.java:1185`, `core/CMProps.java:1196`, `coffeemud.ini:545-546`). GET, GIVE, FOLLOW, FILL, FEED, HOLD, FIRE, HIRE, GAIN and GCONSIDER use this.
- **Movement.** Go uses DEFCMDTIME/100 for combat as well, not the combat default, and divides it by four when the mob has AUTORUN (`Commands/Go.java:260-276`). Flee and Foreward inherit this, so a step taken while fleeing costs 0.20 actions, not 1.00.

Several verbs expose a typed entry point through `executeInternal`, with a table of allowed argument signatures (`Commands/Get.java:43-49, 258-283`, `Commands/Give.java:50-55, 217-230`, `Commands/Hold.java:48-52, 102-117`, `Commands/Follow.java:49, 182-190`). `checkArguments` (`Commands/StdCommand.java:150-174`) matches arity and types, logs a mismatch and returns false. AI code and scripts can therefore call these verbs with objects instead of words.

Actions follow the message pattern: build a `CMMsg`, ask the room `okMessage`, then `send` it (`Commands/Give.java:185-197`, `Commands/Follow.java:109-115`, `Commands/Hold.java:80-100`). The one exception is covered under Problems.

Six verbs cannot be ordered by a leader or charmer: GMODIFY (`Commands/GModify.java:1341-1345`), GENERATE (`Commands/Generate.java:437-440`), FOLLOW (`Commands/Follow.java:205-208`), FRIENDS (`Commands/Friends.java:122-125`), GAIN (`Commands/Gain.java:76-79`) and GAIT (`Commands/Gait.java:73-76`). GenCommand is orderable unless its ORDEROK field says otherwise (`Commands/GenCommand.java:47, 136-139`).

### Command catalogue

Scope: **Player** means no extra check. **Builder** and **Admin** mark commands that need a security flag (`CMSecurity`). Costs are `out of combat / in combat`, in actions.

| Command (words) | Syntax | What it does | Cost | Restrictions | Scope |
|---|---|---|---|---|---|
| GMODIFY | `GMODIFY ROOM/AREA/WORLD/PLAYERS/#TAG [WHEN] FIELD op VALUE [&&/\|\| ...] [CHANGE=FIELD=VALUE ...]`; `GMODIFY ?` lists fields | Finds objects by field and optionally patches them | 0 / 0 | Refused to NPCs (`Commands/GModify.java:831-834`); not orderable | Builder. Flag GMODIFY, anywhere (`Commands/GModify.java:1347-1351`) |
| GENERATE | `GENERATE [NOSAVE] TYPE ID [FROM FILE] [VAR=VALUE ...] [DIRECTION]` | Builds content from a template file | 0 / 0 | Area and room need a direction; not orderable | Builder. Flag CMDAREAS (`Commands/Generate.java:443-446`) |
| (GenCommand verbs) | Set by the verb's ACCESS field | Runs the verb's `EXECUTE` script function | 0.20 / 1.00, or ACTCOST/CBTCOST | Security mask from SECMASK | Builder-made |
| GET, G | `GET [N/all/ALL.name/name.N] [FROM] container [UNOBTRUSIVELY]` | Takes items, with containers and bundles (also b170) | 0.20 / 1.00 | Visible items only | Player |
| GO, WALK | `GO dir`, `GO exit`, `GO OUT`, `GO 3 n`, `GO 3n`, `GO n,e,s` | Walks one step, or queues several (also b170) | 0.20 / 0.20 (AUTORUN 0.05) | Stands first (`Commands/Go.java:52-80`) | Player |
| GROUP, GR | `GROUP` or `GROUP LEADER name` | Lists the group, or hands leadership to a follower | 0 / 0 | New leader must not be NOFOLLOW, and must have room for the group | Player |
| GCONSIDER, GCOS, GCO | `GCONSIDER name/SELF/ALL` | Gives a phrase on group-versus-target odds | 0.20 / 1.00 | Target must be visible | Player |
| GIVE, GI | `GIVE item/N coins/all [TO] who` | Gives to a visible mob (also b170) | 0.20 / 1.00 | Ordered or forced gives cannot move property-owned items | Player |
| I3 | `I3 LIST [COFFEEMUD]/CHANNELS/ADD/DELETE/LISTEN/SILENCE/PING/LOCATE/INFO/RESTART/STOP` | I3 intermud network | 0 / 0 | Management needs flag I3 (`Commands/I3Cmd.java:98-165`); STOP is not checked | Mixed. Admin for management |
| IMC2 | `IMC2 LIST/INFO/LOCATE/CHANNELS/RESTART/STOP` | IMC2 intermud network | 0 / 0 | CHANNELS and RESTART need flag IMC2; STOP is not checked | Mixed. Admin for management |
| GRAPEVINE, GV | `GV LIST/CHANNELS/ADD/DELETE/LISTEN/LOCATE/INFO/RESTART` | Grapevine intermud network | 0 / 0 | ADD, DELETE, LISTEN and RESTART need flag GRAPEVINE | Mixed. Admin for management |
| FOLLOW, FOL, FO, F | `FOLLOW who` or `FOLLOW SELF` | Follows a mob, or stops following (also b170) | 0.20 / 1.00 | A player cannot follow a monster; NOFOLLOW blocks; not orderable | Player |
| GTELL, FTELL, GT | `GTELL text`, `GTELL LAST n` | Messages the group (FTELL: the follower tree) | 0 / 0 | Text starting with `,` or `:` may become a social | Player |
| FLEE | `FLEE [dir]` | Leaves combat | 0.20 / 0.20 (inherited from Go) | Players must be in combat | Player |
| FILL | `FILL item [FROM] source`, or `FILL item` in a drink room | Fills a container | 0.20 / 1.00 | Dust-type containers take a separate branch | Player |
| IGNORE | `IGNORE LIST/ADD [cat.]name/REMOVE name` | Personal ignore list | 0 / 0 | Categories MAIL, TELL, TEACH, RIDE or a channel name | Player |
| FACTIONS, FAC | `FACTIONS [HERE/standing word/faction]` | Standing bars | 0 / 0 | None | Player |
| GOTO | `GOTO room-id/player/area/text/PARENT/PREVIOUS` | Teleports | 0 / 0 | Flag GOTO (`Commands/Goto.java:153-157`) | Admin |
| FEED | `FEED who what` | Feeds an ordered or bound mob | 0.20 / 1.00 | Not in combat; food or drink from inventory | Player |
| FORMATION | `FORMATION`, or `FORMATION who row/FRONT` | Sets a follower's combat row | 0 / 0 | Leader must be in the formation | Player |
| HOLD, HOL, HO, H | `HOLD item/all` | Holds or wields | 0.20 / 1.00 | Multi-item holds take only items that can be held | Player |
| FRIENDS | `FRIENDS [LIST/ADD name/REMOVE name]` | Friends list | 0 / 0 | Not orderable; ADD checks the player exists | Player |
| HELP, ? | `HELP [topic]` | Help lookup | 0 / 0 | Admin topics come from the arc help file (`Commands/Help.java:66-69`) | Player. Admins see more |
| FOREWARD, FORE | `FOREWARD [E/W]` | Forward move; E and W give diagonals | 0.20 / 0.20 (inherited from Go) | Hidden from the command list; only in rooms with a non-compass direction type (`Commands/Foreward.java:78-91`) | Player |
| FIRE | `FIRE who` | Says "You are fired!" to a mob | 0.20 / 1.00 | Short names must match exactly | Player |
| HELPLIST, HLIST | `HELPLIST pattern` | Searches help topics | 0 / 0 | Needs a pattern | Player |
| HIRE | `HIRE [who]` | Says a hiring line | 0.20 / 1.00 | Short names must match exactly | Player |
| GAIN | `GAIN words` | Turns its words into a SAY | 0.20 / 1.00 | Not orderable | Player |
| GAIT, NOGAIT | `GAIT style`, `NOGAIT` | Sets gait through the Gait ability | 0 / 0 | Not orderable | Player |
| HISTORY | `HISTORY [n]` | Shows the last n session input lines (default 20) | 0 / 0 | None | Player |

GOTO is admin-only. GMODIFY and GENERATE are builder tools. I3, IMC2 and GRAPEVINE mix open read-only subcommands with admin-gated management subcommands. HELP shows extra topics to admins.

### GMODIFY: a search-and-patch language over live objects

**Syntax and data model.** The first word picks the scope: ROOM, AREA, WORLD, PLAYERS or `#TAG` (`Commands/GModify.java:843-906`). Conditions follow as `FIELD op VALUE`, where op is one of `=`, `!=`, `>`, `<`, `>=`, `<=` or `$` (a regex) (`Commands/GModify.java:56-73`). Clauses are joined with `&&` or `||`. CHANGE= starts the patch clauses. Each condition is a `GMod` holding its key, comparator, value, flags and compiled pattern (`Commands/GModify.java:791-816`). The flags are CASESENSITIVE (1), SUBSTRING (2), OR (4), AND (8) and INSIDE (16) (`Commands/GModify.java:50-54`).

**Field access.** `getStat` and `setStat` read and write through the object's own stat codes, with special keys handled first (`Commands/GModify.java:75-145`, `160-411`). The special keys are CLASSTYPE, REJUV, RESOURCE, MATERIALTYPE, GENDER, DESTROY, ADDABILITY, DELABILITY, ADDBEHAVIOR, DELBEHAVIOR, ADDAFFECT, DELAFFECT, DELFACTION and REBALANCE (`Commands/GModify.java:742-745`). Players also expose CHARCLASS and the CharStats and CharState codes (`Commands/GModify.java:746-766`). The valid key list is built once from prototype objects of every kind and cached as the resource `SYSTEM_ALL_STAT_KEYS` (`Commands/GModify.java:699-771`).

**Matching.** `tryModfy` tests every condition, left to right (`Commands/GModify.java:448-593`). There is no precedence between `&&` and `||`: each clause's trailing operator decides how it joins the next one (`Commands/GModify.java:585-592`). The `=` and `!=` operators compare text (`Commands/GModify.java:481-528`). The ordering operators compare numerically when both sides are numbers, and as text otherwise (`Commands/GModify.java:537-580`). The modifiers are `[ss]` before a value (the field must contain the value), `FIELD[ss]` (the value must contain the field) and `[cs]` before a value (case sensitive) (`Commands/GModify.java:1033-1046`, `1054-1058`).

**Patching.** Changes run only when the conditions hold (`Commands/GModify.java:594-663`). DESTROY=true removes the object: a room is obliterated, a player is obliterated, anything else is destroyed (`Commands/GModify.java:601-631`). Otherwise each field is set through `setStat` and the actor is told "The FIELD field on NAME in ROOM was changed from X to Y." (`Commands/GModify.java:654-656`). Afterwards the stats are recomputed and the catalog is updated (`Commands/GModify.java:664-678`).

`CLASS=` is a type swap, not an edit. The old object is replaced by a new instance that keeps only level, ability and misc text (`Commands/GModify.java:185-197`).

**Scope and order.** A ROOM or AREA run visits the area, then each room's items, then its mobs, the mobs' inventories and any shop shelves (`Commands/GModify.java:1180-1299`). WORLD visits every area, checking the permission per room. PLAYERS loads each saved player in turn, patches them, saves changed records and unloads the ones that were not loaded before (`Commands/GModify.java:1078-1122`). Each object visited sleeps one millisecond first (`Commands/GModify.java:433-441`).

**Search and change differ in what they touch.** With no CHANGE clause, each room is replaced by a fresh copy read from the database through `CMLib.coffeeMaker().makeNewRoomContent` (`Commands/GModify.java:1204-1205`, `Libraries/CoffeeMaker.java`), and the copy is destroyed afterwards (`Commands/GModify.java:1307-1312`). A search therefore sees saved state, not the live room. A change run freezes the area, resets the live room (`Commands/GModify.java:1206-1211`) and writes it back (`Commands/GModify.java:1300-1305`). What `resetRoom` does was not traced.

Example from the help text: `gmodify area class=genmob&&name=the cityguard change=name=the guard` (`resources/help/arc_help.ini:1880-1889`).

### GENERATE and GenCommand: content and verbs as data

**Generate** reads one XML template file (default `randareas/example.xml`, `Commands/Generate.java:119`), indexes every defined id with `CMLib.percolator().buildDefinedIDSet` (`Commands/Generate.java:127-129`), and builds the requested object from the matching tag (`Commands/Generate.java:178-241`). The types are STRING, AREA, MOB, ROOM, ITEM, QUEST and SCRIPT (`Commands/Generate.java:45-54`). The type word matches by prefix, but the loop keeps the last match, so `S` selects SCRIPT, not STRING (`Commands/Generate.java:132-149`).

Area and room types must end in a direction, which links the new room back by the opposite direction. A direction already occupied is refused (`Commands/Generate.java:151-166`, `64-88`). `ALL` generates every id of the type, by recursion (`Commands/Generate.java:182-205`). `LIST` shows the defined ids (`Commands/Generate.java:208-227`). Results are saved unless NOSAVE is given (`Commands/Generate.java:95-104`, `386-418`). A quest is registered and started (`Commands/Generate.java:340-366`). A SCRIPT is attached to the object named last (`Commands/Generate.java:168-177`, `370-376`).

**GenCommand** is a Command whose identity and behavior are fields: CLASS (its ID, re-registered on change, `Commands/GenCommand.java:212-220`), ACCESS (a comma list, `Commands/GenCommand.java:226`), HELP, SCRIPT, ORDEROK, SECMASK (a zapper mask, `Commands/GenCommand.java:142-147`), ACTCOST and CBTCOST (`Commands/GenCommand.java:120-133`). `execute` compiles the script once, copies it for each call, and calls its `EXECUTE` function with the words as parameters (`Commands/GenCommand.java:74-117`). `getAllXML` and `parseAllXML` serialize every field (`Commands/GenCommand.java:270-295`).

### Movement: GO, FLEE, FOREWARD

**Go** first stands up if necessary, by running the Stand command with IFNECESSARY (`Commands/Go.java:52-80`). OUT finds the one exit that leads outdoors (`Commands/Go.java:100-127`). Direction words are resolved with the room's direction type (`Commands/Go.java:95-133`). Words that are not directions are tried as a rideable item (which enters it) or as an exit name (`Commands/Go.java:137-170`). Multi-step input splits on commas (`Commands/Go.java:176-186`) and accepts a count, as `3 n` or `3n` (`Commands/Go.java:188-206`). Players queue the steps through `prequeCommands` (`Commands/Go.java:230-255`); monsters walk at once (`Commands/Go.java:208-227`).

**Flee** works only in combat for players (`Commands/Flee.java:62-69`). It decides whether the fleer loses XP by checking whether any combatant can still act (`Commands/Flee.java:71-86`). With no direction given and XP at stake, it prefers the room the fleer came from if that exit is open. Failing that, it picks a random open exit and treats UP as a last resort (`Commands/Flee.java:88-130`). If no XP is at stake, it says "You stop fighting." and moves nowhere (`Commands/Flee.java:103-107`). Consequences come from the PLAYERFLEE setting, which is EXPERIENCE by default (`coffeemud.ini:399`, `core/CMProps.java:2753`). The penalty is `max(10, 10 + 5 * (fleer level - foe level))` XP (`Commands/Flee.java:157-159`). The foe gains `round(penalty * (1 - fleer HP fraction)) / 4` XP (`Commands/Flee.java:162-171`), unless the two sessions share a network address. That check is multiplayer-only.

**Foreward** is a relative move. Its default is north, and E or W make it a diagonal (`Commands/Foreward.java:52-60`). It is available to monsters, and to players only in rooms whose direction type is not compass (`Commands/Foreward.java:78-85`). It is hidden from the command list (`Commands/Foreward.java:88-91`).

### Taking and giving: GET, GIVE, FILL, FEED, HOLD

**Get.** `get()` sends a MSG_REMOVE when the item is not in inventory (`Commands/Get.java:74-80`), then a MSG_GET. For a container, the MSG_GET targets the container with the item as the tool, and a second MSG_GET targets the item (`Commands/Get.java:81-93`). Batches come from `all`, `ALL.name`, `name.N` and counts, through `parseMaxToGive` (`Commands/Get.java:127-190`). Raw-material bundles are split (`Commands/Get.java:170-181`). `UNOBTRUSIVELY` suppresses the message (`Commands/Get.java:114-118`). Several items are batched with MASK_OPTIMIZE (`Commands/Get.java:193`).

**Give.** The recipient must be visible in the room (`Commands/Give.java:75-80`). `all` skips coins unless the purse is the only thing carried (`Commands/Give.java:148-152`). A worn held item is unwielded first, with a MSG_REMOVE (`Commands/Give.java:128-146`). `give()` sends MSG_GIVE with "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>." (`Commands/Give.java:185-197`). An ordered or forced give cannot move a property-owned item unless the actor has flag ORDER (`Commands/Give.java:109-115`, `159-165`).

**Fill.** FILL with one argument in a drink room draws from the room (`Commands/Fill.java:79-80`). An item not owned by the actor is taken first (`Commands/Fill.java:141-151`). The count and ALL loop is defective (see Problems).

**Feed.** The target must follow the actor's orders or be bound or held (`Commands/Feed.java:77`). Neither the actor nor the target may be in combat (`Commands/Feed.java:72-76`, `95-99`). The item must be food or drink in inventory (`Commands/Feed.java:85-94`). The target takes the item, eats or drinks it if it still owns it (MSG_EAT or MSG_DRINK), and drops and returns any remainder to the feeder (`Commands/Feed.java:100-127`).

**Hold.** Each item is held, or wielded if it has a wield slot and a wield position is free (`Commands/Hold.java:80-90`).

### Groups and followers: GROUP, FOLLOW, FORMATION, GTELL, GCONSIDER

**Follow.** FOLLOW drops the current leader first (`Commands/Follow.java:109`), then sends MSG_FOLLOW. A player cannot follow a monster (`Commands/Follow.java:163-166`). A NOFOLLOW flag on the target or its group leader refuses (`Commands/Follow.java:168-177`). FOLLOW SELF stops following (`Commands/Follow.java:151-156`).

**Group LEADER.** The new leader must be a follower, must not be NOFOLLOW, and must have enough follower slots for the whole group (`Commands/Group.java:199-210`). Every follower is re-pointed, and the change is announced through GTELL with FTELL (`Commands/Group.java:211-224`). The group listing also repairs follow links that have come out of step (`Commands/Group.java:251-269`).

**Formation.** A row is an integer. `processFormation` walks the follower tree and puts each follower in the row equal to its stored order (`Libraries/MUDFight.java:1317-1340`). Row 0 is the front, and FRONT is accepted as an abbreviation (`Commands/Formation.java:82-84`). `addFollower(who, row)` stores it (`Commands/Formation.java:128`). MUDFight calls `getFormation` at several points (`Libraries/MUDFight.java:1359`, `1381`, `2897`, `3150`). What each call does is outside this bucket.

**GTell.** The group is `getGroupMembers`. FTELL uses an explicit stack to walk the follower tree (`Commands/GTell.java:137-157`). `GTELL LAST n` replays the stored history (`Commands/GTell.java:62-79`). A message starting with `,` or `:` becomes a social, if a social matches (`Commands/GTell.java:84-120`). Each recipient's `okMessage` and `executeMsg` are run, and the line is also stored in their history (`Commands/GTell.java:160-199`). Clients that negotiated GMCP receive a `comm.channel` message (`Commands/GTell.java:128-135`).

**GConsider.** This estimates the odds of your group against one mob. Each group member's damage per round is `((hit chance) * (damage / 2) + 1) * speed`, where hit chance is `normalizeBy5(round(50 * attack / target armor)) / 100` (`Commands/GConsider.java:60-68`). The target's damage per round is counted only against the group leader (`Commands/GConsider.java:68`), which matches the help text (`resources/help/help.ini:1411-1416`). Survival is HP divided by the other side's damage per round (`Commands/GConsider.java:69-70`). The result is `sign * round(sqrt(|(target survival - leader survival) / 2|))` (`Commands/GConsider.java:73-76`). `doGConsider` adds the level difference, halves the sum, and picks a phrase from a ladder whose step is theta. Theta is 2 up to level 20, 3 up to 40, 4 up to 60, 5 up to 80 and 6 above (`Commands/GConsider.java:79-93`). HP is read from `baseState()` (`Commands/GConsider.java:57-58`). Whether that is current or maximum HP was not verified. No zero-armor guard is visible at line 66 or 68.

### Speech-driven verbs: HIRE, FIRE, GAIN

HIRE and FIRE do little more than speak. HIRE says "I'm looking to hire some help." or, when a mob is named, "Are you for hire?" (`Commands/Hire.java:60-62`). FIRE says "You are fired!" to the named mob (`Commands/Fire.java:65`). Both refuse a name shorter than four characters unless it matches exactly (`Commands/Fire.java:57-58`, `Commands/Hire.java:54-55`).

The NPC side is the Hireling behavior (`Behaviors/Hireling.java:284-333`). It listens only to speech from players. HIRE, HIRELING, WORK, WORKS, TIME or AVAILABLE gets an offer: "I'm for hire. Just give me [price] and I'll work for you for [hours] "hours"." If the speaker is the employer, FIRED ends the job with "Suit yourself. Goodbye." SKILLS lists the NPC's abilities. The verb is therefore just a sentence, and the NPC decides what it means.

GAIN is a forwarder. It inserts SAY at the front of the command and re-runs it (`Commands/Gain.java:58-59`), and the help-text prompt points to QUALIFY (`Commands/Gain.java:55`, `Commands/Qualify.java:577`). The trainer side lives in `Commands/Train.java`, covered in b170.

### Player lists and information

**Friends** stores names in the PlayerStats friend set (`Commands/Friends.java:55`). ADD capitalizes the name and checks the player exists (`Commands/Friends.java:78-87`). The sentinel "All" is accepted without a check (`Commands/Friends.java:79-81`). REMOVE compares the raw text (`Commands/Friends.java:105`).

**Ignore** keeps a set of entries. An entry is a name, a `category.name`, or an account-wide name ending in `*` (`Commands/Ignore.java:60`, `70-71`, `116-117`, `125`). Categories are MAIL, TELL, TEACH and RIDE, or a channel name (`Commands/Ignore.java:49-51`, `96-110`).

**Factions.** FACTIONS lists standings sorted by name. HERE lists the factions special to the room (`Commands/FactionList.java:55-65`). A word filters by the prefix of the standing name (`Commands/FactionList.java:80-86`). Each line shows a 25-star bar from the faction's minimum to its maximum (`Commands/FactionList.java:150-163`). The standing is a word or a number, depending on COMBATPROWESS (`Commands/FactionList.java:106-107`).

**History** replays the session's input lines, default 20 (`Commands/History.java:54-66`).

**Help.** The topic is matched in the help file. Admins also search the arc help file (`Commands/Help.java:60-69`). Up to five see-also topics are appended (`Commands/Help.java:74-82`, `85-87`). With no match, the command offers search matches, or points to COMMANDS, TOPICS or HELPLIST (`Commands/Help.java:90-104`). HELPLIST lists the matches (`Commands/HelpList.java:52-79`).

**Goto** resolves its target in order: room id; PARENT; PREVIOUS; area name; `AREA x` text; then world text search with time percentage 100 and a 120000 ms limit (`Commands/Goto.java:70-99`, `Libraries/WorldHuntUtils.java:552`). Space objects come next (`Commands/Goto.java:103-116`). The flag check is at `Commands/Goto.java:123`. Each admin keeps a stack of up to 11 prior rooms, under a resource named for them (`Commands/Goto.java:59-69`, `133-137`).

### Intermud connectors: I3, IMC2, GRAPEVINE

The three verbs follow one pattern: a subcommand word, with management verbs behind a security flag (`Commands/I3Cmd.java:50-56`, `98-146`; `Commands/IMC2.java:95-107`; `Commands/Grapevine.java:50-56`, `92-140`). Mud lists are highlighted when the mudlib name starts with "CoffeeMud" (`Commands/I3Cmd.java:89`). The services are in `Libraries/IntermudClient.java`, which was not read beyond `stopIntermud` (`Libraries/IntermudClient.java:146-158`). None of this applies to a single-player game.

## Content worth mining

**GCONSIDER phrase ladder** (`Commands/GConsider.java:98-150`). Here `@x1` is the target's pronoun. Theta is shown at the bottom.

| Band | Phrase |
|---|---|
| Within theta of zero | "The perfect match!" |
| Negative, above -2 theta | "@x1 might give you a fight." |
| Negative, above -3 theta | "@x1 is hardly worth your while." |
| Negative, above -4 theta | "@x1 is a pushover." |
| Negative, beyond | "@x1 is not worth the effort." |
| Positive, below 2 theta | "@x1 looks a little tough." |
| Positive, below 3 theta | "@x1 is a serious threat." |
| Positive, below 4 theta | "@x1 will clean your clock." |
| Positive, beyond | "@x1 WILL KILL YOU DEAD!" |

Theta is 2, 3, 4, 5 or 6 for level bands up to 20, 40, 60, 80 and above.

**FLEE messages.** "You can only flee while in combat." (`Commands/Flee.java:66`), "You stop fighting." (`Commands/Flee.java:105`), "Flee where?!" (`Commands/Flee.java:136`), and the XP line "You lose @x1 experience points for withdrawing." (`Commands/Flee.java:161`).

**GENERATE types** (`Commands/Generate.java:45-54`, `247-378`).

| Type | Builds |
|---|---|
| STRING | Text, sent to the actor |
| AREA | A new area, linked by a direction |
| MOB | Mobs, brought to life in the room |
| ROOM | One room, linked by a direction |
| ITEM | Items, placed in the room |
| QUEST | A quest script, registered and started |
| SCRIPT | A script attached to the object named last |

**GenCommand stat codes.** CLASS, HELP, ACCESS, SCRIPT, ORDEROK, SECMASK, ACTCOST, CBTCOST (`Commands/GenCommand.java:149-158`). This is a ready-made schema for a verb table.

**Access-word aliases** worth keeping in a parser: GET/G, GO/WALK, GROUP/GR, GCONSIDER/GCOS/GCO, GIVE/GI, FOLLOW/FOL/FO/F, GTELL/FTELL/GT, FACTIONS/FAC, HOLD/HOL/HO/H, HELP/?, HELPLIST/HLIST, FOREWARD/FORE, GRAPEVINE/GV, GAIT/NOGAIT.

**Hireling keywords and lines** (`Behaviors/Hireling.java:299-321`). The keyword set is HIRE, HIRELING, WORK, WORKS, TIME and AVAILABLE, with FIRED as the sacking word.

**Distinctive help text.** The GCONSIDER help explains that the group's damage counts in full, while the target's returns hit only the leader (`resources/help/help.ini:1405-1420`). The GMODIFY help documents the operators and modifiers (`resources/help/arc_help.ini:1880-1930`).

## Notable design ideas

- **Verbs as data.** GenCommand (`Commands/GenCommand.java:41-296`) makes a verb a record: words, help, costs, mask and one script function. That keeps builders out of Java.
- **A query and patch language over live objects.** GMODIFY's WHEN/CHANGE form, with comparators, regex, substring and case modifiers, scoped to a room, area, world, player set or tag (`Commands/GModify.java:448-663`), is a clean model for editing content in bulk.
- **Consider as simulated rounds to kill.** GCONSIDER compares survival times in both directions and compresses the difference with a square root (`Commands/GConsider.java:73-76`). It gives a player a usable number from stats alone.
- **Flee pays the foe.** The foe's XP reward grows with the fleer's missing HP (`Commands/Flee.java:162-165`). Because of the division by four, the foe receives at most a quarter of the penalty, so fleeing is never a pure gift.
- **Speech as the interface for NPCs.** Hire, Fire and Gain are sentences. The NPC's behavior parses the words (`Behaviors/Hireling.java:288-333`), so new verbs need no new code on the player side.
- **Feed with leftovers returned.** Feeding runs through ordinary messages: give, consume, drop, and the feeder picks up what is left (`Commands/Feed.java:100-127`).
- **One word-list parser for all item verbs.** `all`, `all.name` and `name.N` are handled by `fetchFlags` (`Libraries/EnglishParser.java:3095-3120`). Counts (`N name`) and the FROM keyword are handled by `parseMaxToGive` (`Libraries/EnglishParser.java:2846-2859`). Tokenising is in `core/CMParms.java:788-830`.
- **A verb can be hidden from the list.** Foreward is available only in certain rooms and is hidden from the command list (`Commands/Foreward.java:78-91`), so context decides what the player can see.
- **The word ALL is a wildcard in the matcher.** `containsString` returns true for the search word ALL (`Libraries/EnglishParser.java:1608`), and `fetchFlags` turns `ALL.N` into the Nth match. GCONSIDER ALL therefore walks every visible mob through `ALL`, `ALL.2`, `ALL.3` and so on (`Commands/GConsider.java:170-196`). The help text's `gconsider all` example works for this reason.

## Problems and cruft

Confirmed by reading the code. None of these has been run.

1. **GMODIFY REBALANCE on a mob throws ClassCastException.** The cast to Item is applied to every Physical, and a mob is Physical but not an Item (`MOBS/interfaces/MOB.java:44-45`, `core/interfaces/PhysicalAgent.java:35`). A numeric level fails at `Commands/GModify.java:218`. The plain form runs the mob branch at lines 226-227 and then fails at line 230. Nothing in this file catches the exception, so the rest of the run is abandoned.
2. **Quoted multi-word values in GMODIFY keep their quote marks.** `CMParms.parse` returns quoted text as one token without the quotes (`core/CMParms.java:788-840`). `Commands/GModify.java:926-927` wraps any token that contains a space in quotes again, and nothing removes them. A quoted clause then starts with a quote character and fails the CHANGE= and WHEN= tests (`Commands/GModify.java:919-921`, `938`, `943`). Unquoted multi-word input works, as the help example shows. Unverified at run time.
3. **FILL ALL and FILL N take only the first match.** The loop test is `(maxToFill<addendum)` (`Commands/Fill.java:123`), where Get and Give use `addendum<=max` (`Commands/Get.java:154`, `Commands/Give.java:124`). parseMaxToGive defaults to Integer.MAX_VALUE (`Libraries/EnglishParser.java:2848`), so after the first match the test is false and the loop stops. A count is first turned into ALL (`Libraries/EnglishParser.java:2855`). The one exception is a count of 1: then 1 < addendum stays true, and every matching item is taken.
4. **GET can move a non-gettable container.** For a container on the floor, `get()` sends MSG_REMOVE before the MSG_GET check (`Commands/Get.java:74-80`). The container's handler calls `handleBeingRemoved`, which for a container calls `handleBeingGetted` and moves it into the actor's inventory (`Items/Basic/StdItem.java:1718-1719`, `Libraries/CommonMsgs.java:2760-2762`, `2668-2671`). For a floor item, the REMOVE veto checks only the NOREMOVE flag (`Items/Basic/StdItem.java:1435`, `Libraries/Sense.java:307-310`). The GET veto that refuses NOTGET comes later, with "You can't get X." (`Items/Basic/StdItem.java:1396`, `Libraries/Sense.java:295-298`). The result is a carried container together with a refusal message. Unverified at run time.
5. **I3 STOP and IMC2 STOP have no permission check.** The admin hint lists them, but the branches do not test a flag (`Commands/I3Cmd.java:154-158`, `Commands/IMC2.java:101-105`). Neither class overrides `securityCheck`.
6. **GENERATE FROM with no file name throws.** With four words, `commands.get(4)` is out of range (`Commands/Generate.java:112-114`). Nothing in the command catches it.
7. **GIVE fails silently for too few coins.** The check at `Commands/Give.java:116-117` returns false with no message.
8. **Friends and Ignore REMOVE are case-sensitive,** while ADD normalizes the name (`Commands/Friends.java:78`, `105`; `Commands/Ignore.java:115`, `146`).
9. **FOLLOW's self-name test uses the raw input.** `mob.name().toUpperCase().startsWith(whomToFollow)` (`Commands/Follow.java:151-152`) matches only upper-case input. The later path in `processFollow` still stops following, so the effect is small.
10. **GOTO's duplicate check relies on String identity.** `stack.get(top) != mob.location().roomID()` (`Commands/Goto.java:135`) uses `!=`. It works for ordinary rooms only because `roomID()` returns the same field instance (`Locales/StdRoom.java:123-126`) and `getExtendedRoomID` returns that value unchanged (`Libraries/CMMap.java:616-630`). Grid children never match, so they are always pushed. The cap is 11 entries, not 10 (`Commands/Goto.java:68-69`).
11. **GenCommand's lazy script engine is not volatile.** The double-checked lock on `engine` (`Commands/GenCommand.java:77-91`) is unsafe publication without `volatile`.
12. **Get recalculates room stats twice when not optimized** (`Commands/Get.java:205-207`), and `executeInternal` reads `args[1]` where the loop variable `args[i]` is meant (`Commands/Get.java:272`). Harmless with the current signatures.
13. **Go hands a reused list to STAND,** which may change it. The list is rebuilt whenever its size is not two (`Commands/Go.java:58-67`). Works, but fragile.
14. **Hire and Fire's name rule is a hack.** A name shorter than four characters must match exactly (`Commands/Fire.java:57-58`, `Commands/Hire.java:54-55`), which guards against accidental matches.
15. **Gain rewrites its command** by inserting SAY into the caller's list (`Commands/Gain.java:58`). Works, but mutates the argument.
16. **Multiplayer-only assumptions.** The network-address check for XP reward (`Commands/Flee.java:166-170`), account-wide ignore (`Commands/Ignore.java:116-117`), group leadership and FTELL, and the intermud verbs.

## Takeaways for a Python SUD

- **Borrow the verb shape, not the class tree.** A `Verb` object with `words`, `cost`, `combat_cost`, `orderable`, `allowed(player)` and `run(actor, args)`, registered by word. Keep the cost defaults from DEFCMDTIME and DEFCOMCMDTIME as two config values, and let each verb override them.
- **Borrow GenCommand as data.** Define each extra verb in TOML or YAML with `words`, `help`, `cost`, `flags` and an optional function name for its script. A Python callable or a small restricted expression can play the EXECUTE role.
- **Check before you mutate.** Do every refusal test before the first side effect, as the veto pattern intends. Problem 4 shows the cost of splitting one action into a pre-step and a check.
- **Write one target parser.** A function `pick(items, text, count=None, want_all=False)` that handles `all`, `all.name`, `name.2`, `3 name` and `from`. It replaces the duplicated loops in Get, Give, Fill and Hold.
- **Keep a GMODIFY-style tool for content, out of game.** A developer command that takes `where` and `change` dictionaries over the object lists for rooms, areas, the world and tags. Keep the search-versus-change split: search from saved data, change live data.
- **Port GCONSIDER as a pure function.** `consider(player, mob)` with rounds-to-kill in both directions, square-root compression, the level-banded theta and the nine phrases. For companions, sum their damage per round, and count the mob's damage only against the party leader.
- **Port Flee's XP rule as a number, not an engine.** `penalty = max(10, 10 + 5 * (you - foe))`, with the foe receiving a share scaled by your missing HP. If the game has no XP, the same formula can be a morale or resource cost.
- **Make NPCs keyword-reactive.** Speech goes to nearby NPCs as text. A HIRE/WORK/AVAILABLE keyword set gives an offer; FIRED ends the contract. This needs no new verbs.
- **Keep multi-step movement as a queue.** Parse `go 3 n` and `go n,e,s` into a list of steps. Each step then costs one turn or one action in the real-time mode.
- **Keep HELP with see-also and search fallback, HISTORY, and a FACTIONS-style bar** for reputation. Drop friends, ignore, group chat, group leadership, NOFOLLOW, the intermud verbs and the network-address check. GOTO is worth keeping only as a developer tool.

## Coverage

- **Read in full (all 30 files):** `Commands/GModify.java`, `Generate.java`, `GenCommand.java`, `Get.java`, `Go.java`, `Group.java`, `GConsider.java`, `Give.java`, `I3Cmd.java`, `Follow.java`, `GTell.java`, `Flee.java`, `Grapevine.java`, `Fill.java`, `Ignore.java`, `FactionList.java`, `Goto.java`, `Feed.java`, `Formation.java`, `Hold.java`, `Friends.java`, `IMC2.java`, `Help.java`, `Foreward.java`, `Fire.java`, `HelpList.java`, `Hire.java`, `Gain.java`, `Gait.java`, `History.java`. The license headers and import blocks were skipped.
- **Read in full for context:** `Commands/interfaces/Command.java`, `Commands/StdCommand.java`.
- **Read in part or by grep:** `Behaviors/Hireling.java` (284-333), `Libraries/CommonMsgs.java` (2649-2680, 2754-2765), `Items/Basic/StdItem.java` (executeMsg 1630-1720, okMessage 1346-1450), `Locales/StdRoom.java` (executeMsg 906-1010, send and sendAndExec 1688-1740, fetchInhabitant 2113), `Libraries/EnglishParser.java` (2846-2860, fetchFlags 3095-3120, fetchEnvironmental 3175), `core/CMParms.java` (parse 788-830), `Libraries/CoffeeMaker.java` (makeNewRoomContent), `Libraries/MUDFight.java` (1317-1360), `core/CMProps.java` (1185-1197, 2753), `Libraries/Sense.java` (295-310), `Libraries/WorldHuntUtils.java` (552), `Libraries/IntermudClient.java` (146-158), `coffeemud.ini` (399, 545-546), `resources/help/help.ini` (1405-1420), `resources/help/arc_help.ini` (1880-1889), `Commands/Train.java` and `Commands/Qualify.java` (grep only).
- **Not traced:** what `CMLib.map().resetRoom` does, what `CMLib.dice().normalizeBy5` does, what `CMLib.tracking().walk` does with its flee flag, and the intermud service code.
