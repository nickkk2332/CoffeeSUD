# Commands, part 3: ClanWho to Description

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b112` (Commands [part 3/12: ClanWho .. Description]) · 19 files · 6672 lines of Java.

## Summary

This bucket holds nineteen command objects. Most are small player verbs (CLOSE, DEACTIVATE, CONSIDER, COMPARE, DEITIES, CONFIG, COLORSET, CRAWL, CLOAK, COMPRESS, DESCRIPTION, CREDITS, COMMANDS, DEPOSIT, CLANWHO). Two are larger: CREATE (`Commands/Create.java`, 2682 lines) is the in-game content editor, where each noun (ITEM, MOB, ROOM, RACE, COMMAND and so on) creates a data record and hands it to the generic editor, and COPY clones mobs, items, rooms, exits, areas and abilities. CommandJournal turns each configured journal (BUG, TYPO, IDEA, TASK, STUCK by default) into its own verb for player-to-staff messages. The most reusable rules are CONSIDER's survival-round threat estimate, COMPARE's item rules, DEITIES' faith-gated information, and the deferred experience pool that DEFERCMD pays out. Clans, mail, channels, hiding from other players and copying another player's settings are multiplayer features that a single-player Python game would drop.

## Key files and types

Paths are relative to the CoffeeMUD folder. Java paths omit `com/planet_ink/coffee_mud/`, so `Commands/Create.java` means `com/planet_ink/coffee_mud/Commands/Create.java`.

| File or class | Role | Lines |
|---|---|---|
| `Commands/ClanWho.java` | Clan roster, or one member's record, gated by clan LIST_MEMBERS authority; extends `Who` | 168 |
| `Commands/Close.java` | Close an exit (and its paired exit) or a container or object | 160 |
| `Commands/Deactivate.java` | Switch off a powered device (not software) in the room, an open container, or the computer being ridden | 155 |
| `Commands/DeferCmd.java` | Internal wrapper that pays out deferred XP, then runs the command it wraps; has no access word | 125 |
| `Commands/Commands.java` | Lists every verb the player can use; `COMMANDS CLEAR` empties the queue | 120 |
| `Commands/Deposit.java` | Deposit coins or items with a Banker or PostOffice shopkeeper | 118 |
| `Commands/Cloak.java` | Makes the mob hidden by applying the `Prop_WizInvis` effect with the cloak bit | 109 |
| `Commands/Crawl.java` | Move one room lying down (extends `Go`) | 105 |
| `Commands/Compress.java` | Toggle the COMPRESS attribute used by item and mob lists | 92 |
| `Commands/Description.java` | Show or set the mob's own description | 79 |
| `Commands/Credits.java` | Show the credits text file | 73 |
| `Commands/Config.java` | View and toggle player options; HELP, COPY, LIST, LINEWRAP, PAGEBREAK, AUTOTELLNOTIFY | 559 |
| `Commands/ColorSet.java` | Interactive colour editor; stores only the slots that differ from the standard colours | 502 |
| `Commands/Copy.java` | Builder and admin cloning of mobs, items, rooms, exits, areas and abilities | 475 |
| `Commands/CommandJournal.java` | One verb per configured journal: post, REVIEW and TRANSFER | 432 |
| `Commands/Consider.java` | Threat estimate for a target, with mending-skill hints; also called by MSDP | 310 |
| `Commands/Compare.java` | Compare two items of the same kind (weapon, armour, container) | 200 |
| `Commands/Deities.java` | List deities, or describe one, with faith-gated detail | 208 |
| `Commands/Create.java` | Builder and admin content creator; about 40 sub-words plus a guessing fallback | 2682 |

Systems consulted only as far as needed for the interactions described: `Commands/interfaces/Command.java` and `Commands/StdCommand.java` (base contract, costs), `Libraries/CoffeeLevels.java` (experience deferral, lines 906-1045 and 1533-1641), `core/CMProps.java` (action-cost helpers and EXPDEFER parsing), `Libraries/CMJournals.java` (journal script hook and expiry), `Libraries/Achievements.java` (tattoo rule), `Libraries/AutoAwards.java` (award line format), `Libraries/AbilityComponents` (interface, `Libraries/interfaces/AbilityComponents.java`), `MOBS/interfaces/MOB.java` (the `Attrib` enum), `Libraries/CMEncoder.java` (password comparison), `Locales/StdRoom.java` and `Areas/StdArea.java` (room lookup and room-ID allocation).

## How it works

Java paths in this section are relative to `com/planet_ink/coffee_mud/`. Other paths are relative to the CoffeeMUD folder.

### 1. Shape of these commands

Most classes extend `StdCommand`. `ClanWho` goes through `Who` (which also extends `StdCommand`), and `Crawl` extends `Go`. Each returns its access words from an `I(...)` array, for example `Close.java:41` (`CLOSE`, `CLOS`, `CLO`, `CL`). `execute(mob, commands, metaFlags)` does the work. Failures go through `CMLib.commands().postCommandFail(...)` (for example `Close.java:116` and `:128`) or `postCommandRejection(...)` when a room or item vetoes a message (`Close.java:104`). Routing and the two-phase message flow are described in b170.

Gating happens in two places. `securityCheck` restricts the verb (`Cloak.java:103-107` uses `CMSecurity.SecFlag.CLOAK`; `Copy.java:470-472` uses `SECURITY_COPY_GROUP`; `Create.java:2677-2679` uses `SECURITY_CMD_GROUP`; `DeferCmd.java:119-123` requires a player and `EXPDEFER_PCT > 0`). `canBeOrdered()` decides whether a charmed or ordered mob may run it; the value per class is in the catalogue below.

Costs follow `StdCommand`. The base `actionsCost` returns `CMProps.getCommandActionCost(ID(), 0.0)` (`StdCommand.java:224-227`), so a command costs 0 actions unless `DEFCMDTIME` lists it as an exception. The classes that call the one-argument form (`Close.java:149-152`, `Compare`, `Consider`, `Deactivate`, `Deposit`, `DeferCmd`) get the shipped default instead. `coffeemud.ini:545` sets `DEFCMDTIME=0.20` and `:546` sets `DEFCOMCMDTIME=1.0`. `CMProps.java:1183-1196` converts these to actions. `StdCommand.checkedActionsCost` (`StdCommand.java:240-261`) picks the combat cost when the mob is in combat, or when its boarded ship is.

### 2. CREATE: dispatch and gating

`Create.execute` (`Create.java:1885-2668`) uppercases the second word and walks a long `if / else if` chain. Most branches first call `CMSecurity.isAllowed(mob, location, flag)`. WEBSERVER, SMTP and the intermud names check for a sysop instead. A denied user gets `errorOut` ("You are not allowed to do that here.", `:1878-1882`). An allowed user sees the emote "wave(s) arms" (for example `:1896`) before the handler checks its own arguments, so the flourish appears even when the arguments are wrong. The gate flag per noun is in the catalogue.

When the first word matches nothing, the fallback at `Create.java:2477-2666` guesses what the builder meant:

1. If the word names a builder template (`CMLib.catalog().getBuilderTemplateObject`) that is a mob, item, room or exit, it re-dispatches with the right noun inserted (`:2487-2528`).
2. If the last word is a direction and the name is a locale, exit or area type, it creates a room, exit or area in that direction (`:2551-2595`).
3. `CREATE <number> <material>` makes that many raw-material items. Each goes in the room with a `Player_Drop` expiry and is bundled every 10 items (`:2597-2615`, using `RawMaterial.CODES`).
4. `CREATE <number> <anything else>` runs the whole creation that many times (`:2619-2631`). A comment in the code calls this "some strange shit".
5. A race name becomes a mob (`:2635-2651`). Anything else gets the "You cannot create a ..." message with `listOfThings()` (`:2662`, `:1725-1732`).

### 3. CREATE EXIT: reciprocal wiring

`exits()` (`Create.java:58-147`) takes a direction and an exit type. The type comes from `CMClass.getExit` or the builder's template (`:82-98`). If an exit already exists that way, and both it and the reverse exit on the far side are generic with the same ID, the new exit is a copy of the reverse exit and is opened in the generic editor (`:107-114`). The exit is then placed with `setRawExit` (`:118`). A grid room is rebuilt (`:119-120`). The room sees "Suddenly a portal opens up <dir>." (`:121`), and the room is saved (`:123`).

The far side is mirrored (`:124-142`). If that room has no exit back and its door points at this room, a copy of the new exit is placed there too. Both areas are then refilled (`:143-145`), and the change is logged as "exits changed by" the builder (`:146`). This means a builder never has to link both ends by hand.

### 4. CREATE ITEM

`items()` (`Create.java:220-366`) takes `NAME [@ target]`.

- **Target.** `@ room` (the default), a mob in the room, or a container in the room (`:289-318`). The container is set as the item's container.
- **Name lookup, in order.** `CMClass.getItem(id)` (`:248`). Then coins: `parseNumPossibleGold` and `makeCurrency` (`:249-257`). Then the catalogue (`getNewCatalogItem`, `:262-264`). Then a builder template (`:267-273`). Archon-only items need a sysop (`:282-287`, message "NO!").
- **Space objects.** With `@ x,y,z` or `@ <distance> FROM <object>`, `makeSpaceLocation` (`:149-218`) parses the position. The "FROM" form picks a random direction with `roll(1,360,-1)` and `roll(1,180,-1)` (`:156`) and a distance from `parseSpaceDistance`. Without an `@`, a space object gets a random point and up to 20 retries while `getSpaceObjectsByCenterpointWithin(..., SolarSystemDiameter.dm)` still finds an object there (`:320-332`). Space objects other than weapons and ships go into space (`:336-342`). Weapons and ships go into the room.
- **Wear.** `subjectToWearAndTear` items get `setUsesRemaining(100)` (`:334-335`).
- **Generic items** go to the generic editor (`CMLib.genEd().genMiscSet`, `:358-359`). Space objects are written with `DBCreateThisItem("SPACE", ...)` (`:362`).
- **Messages.** "Suddenly, X drops from the sky." (`:348`), or "Suddenly, X drops into Y's arms." (`:355`).

### 5. CREATE MOB and the race, class and level spec

`mobs()` (`Create.java:622-860`) tries these in order:

1. The whole argument as a class ID (`CMClass.getMOB`, `:633`).
2. A catalogue entry (`getNewCatalogMob`, `:638`).
3. A spec of a race, a class and a level, in any order, with an optional leading "a" or "an" (`:645-774`). The parser tries the first word, then re-splits the rest. It accepts the spec only if it finds all three kinds (`:757-762`).
4. A builder template (`:821-829`).

For a spec, the mob is a `GenRideable` if the race uses a ride class, otherwise a `GenMob` (`:780-783`). Its name is "a <race> <class>" built with `startWithAorAn` (`:785-791`). A level-only spec gets the name `"a level @x1er"` (`:794`), which the code prints as "a level 5er". See the problems section.

When a class is given, the mob gets a `Prop_Trainer` effect (`:802-815`). Its misc text is `SKILLS <classID> NOTEACH BASEVALUE=10`, plus a stat ceiling for each non-zero class adjustment (`:805-809`). The ceiling is `BASEMAXSTAT` (default 18, `CMProps`) plus the class adjustment. The class's attack attribute gets the base ceiling when its own adjustment is zero (`:811-812`). This is how a class teaches its skills to a freshly made mob.

Defaults and finishing: a blank name becomes "A Standard MOB" (`:838-839`). The respawn interval (`setRejuv`) is `tpm + tpm + tpm/2`, that is 2.5 minutes of ticks, capped at 20 minutes (`:842-844`). Non-generic mobs are filled out by the leveller (`:845-846`). The room sees "Suddenly, X instantiates from the Java Plane." (`:856`).

### 6. CREATE ROOM and CREATE AREA

`rooms()` (`Create.java:436-516`) accepts the direction as the third word or as the last word (`:451-467`). It takes a room type from `CMClass.getLocale` or a template (`:469-491`). It assigns an ID with `area.getNewRoomID(room, direction)` (`:493`). If the ID is empty, it refuses with the grid-edge message (`:495-500`). If the room is savable it is written (`:501-502`). Then `CMLib.map().createNewExit` links it (`:504`). An error string from the link is shown, but the room is already saved (`:504-507`). The room sees "Suddenly a block of earth falls from the sky." (`:514`).

`getNewRoomID` (`Areas/StdArea.java:625-690`) searches for the first unused `<AREA>#n` number from the lowest used number up to 1000 past the highest. If that fails it returns a random number.

`areas()` (`Create.java:941-1019`) requires a unique name. It prompts for an area type up to 10 times, defaulting to `Planet` for space areas and `StdArea` otherwise (`:975-989`). Space areas get coordinates through `makeSpaceLocation` and are added to space (`:991-1002`). The first room is a `SpacePort` or a `StdRoom`. It takes its ID from `getNewRoomID(R, -1)` (`:1008-1009`), the builder is moved into it (`:1017`), and the area gets its automatic properties (`:1018`).

### 7. Copy-on-edit: turning standard content into data

Several creators follow one pattern. They are RACE (`Create.java:893-939`), CLASS (`:1734-1783`), ABILITY (`:1230-1267`), TRAP (`:1269-1307`), LANGUAGE (`:1354-1391`), CRAFTSKILL, WRIGHTSKILL, GATHERSKILL, POISON (`:1489-1643`), and COMMAND (`:1393-1487`).

- **Standard class or race.** For RACE and CLASS, a confirm prompt defaults to N. It warns that special behaviour "may be lost" and says the user can "undo this action by destroying the same race ID after creating it" (`:912-918`, `:1754-1759`). Then the standard object is converted to data (`makeGenRace`, `:919`; `makeGenCharClass`, `:1763`).
- **Standard ability.** For the ability-family nouns, an existing ID is announced as "already exists, and will be over-ridden" (`:1256`, `:1295`, `:1380`, `:1515`) and converted with `CMLib.ableParms().convertAbilityToGeneric`. The result is a `Gen*` record.
- **Then.** The record is edited by the generic editor (`CMLib.genEd().modifyGen...`, for example `:934`, `:1264`, `:1304`). It is written with `DBCreate...` (`:935`, `:1265`). Races also run `CMLib.utensils().swapRaces(GR, R)` to move existing mobs onto the new record (`:936-937`).
- **Exception.** TWEAK refuses to override an existing ability and says "Use a different ID to override it" (`:1669-1674`).

### 8. CREATE COMMAND: wrapping a Java command in script

`commands()` (`Create.java:1393-1487`) has two paths.

- **Existing Java command.** It builds a `GenCommand` whose script constructs the Java class with `new Packages.<class>()` and calls `execute(source(), list, 0)` (`:1423-1436`). The record copies the help text (`:1449`), the access words (`:1450`), `ORDEROK` from `canBeOrdered()` (`:1452`), and a `SECMASK` of "+SYSOP -NAMES" if a factory mob fails `securityCheck` (`:1437-1446`, `:1453`). Action costs are stored as -1.0 when they equal the default (`:1456-1463`).
- **New command.** It builds a `GenCommand` whose script only echoes its arguments (`:1472-1481`). This is a stub for the builder to rewrite.

Afterwards `CMClass.reloadCommandWords()` is called (`:1486`).

### 9. Other creators: cron, achievements, awards, titles, components, expertises, help

- **CRON** (`Create.java:1785-1826`). The interval is parsed as a tick expression with the home clock (`:1800`) and converted to milliseconds (`:1810`). The job is a journal entry in `SYSTEM_CRON`, with `update` set to now plus the interval (`:1816`, `:1822`) and the PROTECTED attribute (`:1819`). The builder then sets the script with MODIFY CRON, as the message at `:1823` says.
- **ACHIEVEMENT** (`:1309-1352`). The agent is PLAYER, ACCOUNT or ALL (`:1322-1329`). The tattoo may not contain a space (`:1338`) or end in a digit (`:1344-1349`). The file loader separately rejects tattoos that start with a digit (`Libraries/Achievements.java:959`). The code gives no reason for the trailing-digit rule.
- **AWARD** (`:1181-1198`). The usage is `PLAYER MASK:DATE MASK::PROPS`. The check at `:1184` needs exactly three parts from `split("::")`. The loader (`Libraries/AutoAwards.java:649-652`) also needs two `::` separators. See the problems section for the usage line.
- **TITLE** (`:1200-1228`). Format `TITLE=([MAX]:)[ZAPPER MASK]`. The rule is evaluated and appended to the auto-title file (`:1219-1226`).
- **COMPONENT** (`:1021-1103`). Two forms. A SKILL form creates an ability component record, which is a resource a skill needs each time it is used (see the `AbilityComponents` interface header, `Libraries/interfaces/AbilityComponents.java`). The skill must exist (`:1075-1078`) and must not already have components (`:1083-1088`). A SOCIAL form creates or edits a social as a component (`:1033-1073`).
- **EXPERTISE** (`:1139-1179`). Format `ID=PARAMS`. The ID is cleaned of `@X1` and `@X2` placeholders (`:1158-1159`) before the existence check (`:1160`). The line is checked by `confirmExpertiseLine` (`:1166`).
- **HELP and AHELP** (`:592-620`). The verb decides the file. AHELP writes `arc_newhelp.ini` (archon only), HELP writes `newhelp.ini` (`:600`, `:610`). An existing key is refused (`:603-609`).

### 10. COPY

`Copy.execute` (`Copy.java:50-461`) is the cloning tool. Its usage line is at `:57`.

- **Count.** The optional leading number is the copy count. The default is 1, and there is no upper limit (`:61-69`).
- **Destination.** `@ room`, `@ <mob in room>`, or `@ <container in room>` (`:73-97`).
- **Direction forms.** `NAME DIR` means the room in that direction or the exit that way. `ROOMID DIR` and `EXIT DIR DIR` / `ROOM DIR DIR` are also parsed (`:99-147`).
- **Search order** when no direction applies. Room items and mobs, then inventory (`:148-153`). Then `CMLib.hunt()` over the area, all rooms, shop stock and inventory, each limited to 50 hits (`:156-177`). Then an area by name (`:180`).
- **Ability clone** (`:182-217`). If nothing matched and the first word is an ability ID, the rest of the line (spaces removed) becomes the new ID. The clone is generic, copies stats, and is saved. The room sees "The skill of the world just increased!" (`:214`).
- **Mob** (`:227-264`). Requires COPYMOBS. A player-controlled mob becomes a `GenMOB` built from its generic stats (`:236-245`). Session and player stats are cleared (`:247-248`). The copy is brought to life in the room (`:253-255`).
- **Item** (`:265-300`). Requires COPYITEMS. An archon-only item may be copied only by a sysop on a MUD whose name contains "coffeemud" (`:267-268`). The copy is placed in the room, or in the destination mob's arms (`:279-286`).
- **Room** (`:302-361`). Requires COPYROOMS. It refuses if a room already exists that way (`:309-313`). The copy has its sky and grid cleared and its doors nulled (`:318-325`). Reciprocal exits are set (`:326-330`) and a new ID is assigned (`:331`). The rooms are saved and the area refilled (`:339-346`). With a count above 1, each copy is placed beyond the previous one (`:359`). So `COPY 3 ROOM NORTH` builds a chain of three rooms.
- **Exit** (`:363-393`). Places a copy of an exit into the opposite room when the source is the room's own exit (`:370-380`).
- **Area** (`:395-448`). Requires CMDAREAS and COPYROOMS. The name is made unique with "Copy of" (`:404-405`). Each room is copied with its ID prefixed by the new area name (`:418-425`). Neighbour pointers are translated through a map (`:412-427`, `:433-438`), so exits inside the area point at the copies and exits to outside rooms keep the originals.
- **Logging.** Mob, item, room and area copies are logged to `SysopUtils` with the copier's name (`Copy.java:262`, `:298`, `:355`, `:447`). Ability clones are not logged.

### 11. CommandJournal: journals as verbs

`CommandJournal` has one prototype per configured journal (`Commands/CommandJournal.java:38-66`). Each journal's `NAME()` becomes an access word. The list is cached in a static array on first use, so a journal added later at runtime does not appear until restart (unverified in practice; the cache is never reset in this file). The defaults are in the catalogue.

Each journal is configured by a flag string in `COMMANDJOURNALS` (`coffeemud.ini:1466-1501`). The flags the code reads are:

- **CONFIRM** asks Y or n before posting (`CommandJournal.java:387-412`).
- **SCRIPT=** runs a script over the message. `CMJournals.getScriptValue` runs the script with `VALUE` set to the message, and the returned value replaces the message (`Libraries/CMJournals.java:671-692`). The BUG journal uses this for a prompt for more detail.
- **CHANNEL=** echoes each new entry to a channel (`:380-381`).
- **ADDROOM** prefixes the room ID as a clickable tag (`:370-374`).
- **ASSIGN=** gives a list of category names used by TRANSFER (`getAssignFlags`, `:202-216`).
- **Zapper mask.** A mask on the whole verb is checked with `maskCheck` (`:346-351`).
- **EXPIRE=** and **ADMINECHO** are documented in the ini. Expiry is in `CMJournals.java:1020-1036`.

Posting (`:355-415`) writes the message from the player to `ALL`. The subject is the message padded to 20 characters (`:376-378`). The player sees "Your <name> message has been sent. Thank you." (`:379`).

REVIEW (`:218-309`) lists entries. A user without the JOURNALS security flag and without access to that journal sees only messages addressed to them (`:232-234`). Each entry is read through the journal item's `MSG_READ`. The item's return value decides whether to stop (0), shrink the total (negative), or move to the next entry (positive) (`:292-305`).

TRANSFER (`:68-200`) moves message N to another journal (delete and rewrite, `:173-185`) or re-addresses it to a player or an ASSIGN category (`:193-198`). Both read the list from `DBReadJournalMsgsByUpdateDate(journalID, true, ...)` and index it with `count-1`. There is a bug when no count is given (see the problems section).

### 12. CONSIDER: the threat estimate

`Consider.relativeLevelDiff` (`Commands/Consider.java:50-73`) compares how many rounds each combatant survives against the other.

```java
hit(a, d)   = (normalizeBy5(round(50*a.attack/d.armor))/100 * (a.damage/2) + 1) * a.speed   // lines 63-64
survTarget  = target.hp / hit(you, target)                       // rounds the target lasts against you
survYou     = you.hp / hit(target, you)                          // rounds you last against the target
levelDiff   = (survTarget - survYou) / 2
result      = sign(levelDiff) * round(sqrt(|levelDiff|))         // lines 69-72
```

The attack and armour values come from `CMLib.combat().adjustedAttackBonus` and `adjustedArmor` (`:54-57`). The square root compresses large gaps. A positive result means the target is the stronger one.

`doConsider` (`:75-209`) turns the result into text.

- **Level band.** `lvlDiff = target.level - you.level`. Zero is "is your equal". Beyond the EXPRATE setting (`CMProps` `EXPRATE`) it is "vastly inferior" or "far superior". Inside that band it is graded by `pct = |lvlDiff| / EXPRATE` with breakpoints at 0.5, 0.8 and 1.0 (`:104-133`).
- **Threshold.** `theDiff` is 2 at level 20 or below, 3 above 20, 4 above 40, 5 above 60 and 6 above 80 (`:91-99`).
- **Perfect match.** If `|realDiff| < theDiff`, the text says the target "is the perfect match!" (`:135-145`).
- **Target weaker** (realDiff below 0). Above `-2*theDiff`: "might actually give you a fight." Above `-3*theDiff`: "won't put up a big fight." Above `-4*theDiff`: "is basically a pushover." Otherwise: "is an easy kill." (`:147-162`).
- **Target stronger.** Below `2*theDiff`: "looks a little tough." Below `3*theDiff`: "is a serious threat." Below `4*theDiff`: "will clean your clock." Otherwise: "WILL KILL YOU DEAD!" (`:164-179`).
- **Mending hint.** Every known ability that is a `MendingSkill` and says it `supportsMending` for the target is listed, for example "You could probably help X out with your Y skill" (`:184-204`). The interface is `Abilities/interfaces/MendingSkill.java:47`.
- **Several targets.** Multiple targets make the room see "consider(s) several things." once. Each target gets its own line for the consider (`:270-279`).

`executeInternal` returns the level difference as an integer (`:283-290`). `CMProtocols.java:2387-2395` calls it for the MSDP OPPONENT_STRENGTH variable, so an external client can read it.

### 13. COMPARE

`Compare.execute` (`Commands/Compare.java:49-180`) finds the first item in inventory. If there is none it looks at the shop stock of shopkeepers in the room (`:60-87`), so a shop item can be compared without buying.

Slot matching uses `rawProperLocationBitmap`. A held item loses the held bit unless it is also a wield, which lets a shield and a weapon compare as the same kind (`:88-92`, `:104-108`). With one argument, the target is the equipped item in the same slot with the same `rawLogicalAnd`, preferred over an inventory copy (`:94-128`). With two arguments, the second item is found in inventory (`:129-130`).

- **Weapons** (`:137-150`). `cDmg = damage + round(((attackAdj_c - attackAdj_t)/100) * cDmg)`. Then the result is "looks better than", "looks worse than" or "look about the same".
- **Armour** (`:152-167`). Must share the same worn locations (`compareProperLocations`, `:154`). Compared by base `armor()`.
- **Containers** (`:169-176`). Both must have a capacity above 0. The comparison is by capacity.
- **Anything else** (`:177-178`). "You can't compare X and Y."

### 14. DEITIES and faith tiers

`Deities.getDeityInformation` (`Commands/Deities.java:69-153`) builds the text. The name carries alignment and inclination ranges (`:73-84`). Then the description, the cleric and worship requirements, the weapons (`getWeapons`, `:56-67`), the blessings, the granted powers and the service instructions.

What a mob may see depends on its faith. Faith is `CharStats.STAT_FAITH` (`Common/interfaces/CharStats.java:136`, value 44).

| Faith (`STAT_FAITH`) | Requirements shown | Blessing instructions | Powers and service |
|---|---|---|---|
| 100 or more | Cleric requirements only (`Deities.java:96-97`) | Cleric trigger text (`:127-128`) | Shown (`:133-151`) |
| 25 to 99 | Cleric and worship requirements (`:99-102`) | Worship trigger text (`:129-130`) | Hidden |
| Below 25 | Worship requirements only (`:104-105`) | Worship trigger text | Hidden |

A null mob (an internal call) sees everything (`:90`, `:121`, `:133`). `executeInternal` returns the text, and the `Boolean` argument asks for the name line only (`:155-165`).

### 15. CONFIG

`Config.execute` (`Commands/Config.java:54-551`) shows the option list, or changes one option.

- **Names.** The typed word is matched against `MOB.Attrib.getName()` (`:67-73`). That returns the display name, not the enum name (`MOB.java`, `Attrib.getName`, which returns the `desc` field). Options with a space in the display name (ANSI COLOR, 16 COLOR ONLY, 256 COLOR ONLY) cannot match a single typed word. See the problems section.
- **Quiet.** `CONFIG QUIET <flag>` suppresses the status list (`:59-63`, `:547-548`).
- **Toggle.** The bit is flipped (`:318`, `:438`). A switch on the attribute runs side effects (`:319-437`):
  - ANSI, ANSI16ONLY and ANSI256ONLY set client and server telnet modes (`:321-341`).
  - AUTOFORWARD writes the account flag to the database (`:350-357`).
  - AUTOATTACK refuses to turn on under turn-based or manual combat with the message "Because of the combat system, this config doesn't really apply." (`:373-383`).
  - MXP, SOUND (MSP) and TELNET_GA change telnet negotiation (`:396-434`).
- **Storage.** Each attribute has a bit of `2^ordinal` (`MOB.java:1095-1107`). An attribute marked `autoReverse` is shown as ON when its bit is clear (`Config.java:514-517`). This makes "default on" options store as cleared bits.
- **HELP.** `CONFIG HELP [filter]` reads the `CONFIG_HELP_OPTIONS` entry in `resources/help/misc_help.ini:59-90` (`Config.java:76-129`). Hidden options: SYSOPMSGS unless the user may see system messages (`:82-83`), and AUTOMAP unless the awareness range is above 0 (`:84-85`).
- **LINEWRAP.** Accepts an integer above 10, or DISABLED, OFF or 0 (`:252-274`). PAGEBREAK accepts an integer above 0, or DISABLED (`:276-292`).
- **COPY.** `CONFIG COPY <player>` compares attributes with another player, then asks for confirmation (`:131-234`). On Y it re-runs `CONFIG QUIET <name>` for each attribute that differs (`:196-213`). See the problems section about cleanup timing.
- **AUTOTELLNOTIFY** is account-level and only shown when the account system is on (`:294-311`, `:527-543`).
- **LIST.** Prints the option names (`:242-250`).

### 16. COLORSET

`ColorSet` (`Commands/ColorSet.java`) runs an interactive menu. It lists 19 fixed slots (`:265-285`), each keyed by a single code character, plus one slot for each channel that has a colour (`:286-291`, channel slots numbered 128 and up). The slot list is in the catalogue.

Each slot takes either a foreground colour from the basic set, or a background and a foreground pair (`:355-402` and `:403-487`). A background and foreground pair is stored as a single ANSI sequence. The source comment notes that bold limits which combinations look right (`:238-241`).

Storage is a diff, not a full table. `makeColorChanges` (`:125-149`) writes one entry per slot that differs from the standard lookups: the slot character, its code, and `#`. Channel slots use `(n)code#`. The result is saved to `PlayerStats.setColorStr`. `COLORSET DEFAULT` clears the string (`:162-168`). `COLORSET COPY <player>` copies another player's string after a confirmation (`:169-233`).

### 17. CLANWHO

`ClanWho` (`Commands/ClanWho.java:50-160`) extends `Who`.

- **No argument.** Lists every clan the viewer belongs to (`:88-97`).
- **Clan name.** Shows that clan's online members (`:98-155`).
- **Player name.** Loads the player only if the viewer holds a clan rank with LIST_MEMBERS authority in some clan (`:62-80`, `:66`). If the player is in that clan, the member record is printed: Name, Level, Race, Class, Rank, Joined, PC Kills, NPC Kills, $ Donated and XP Donated (`:105-130`).
- **Visibility.** A cloaked member is listed only if the viewer has CLOAK or WIZINV security and is at least as high a level (`:142-146`).

### 18. The small verbs

- **CLOSE** (`Close.java:51-106`, `:108-132`). For an exit, the close message is sent to the room (`:65`), then the paired exit is closed too (`:82-86`). The far room sees "<dir> <exit> closes." (`:92`). A closing sound is sent with `CMLib.protocol().msp("dooropen.wav", 10)` (`:55`). For a non-exit, the location's `okMessage` decides (`:98-104`). An exit that is already closed, or vetoed, returns with no message (`:61-105`). See the problems section.
- **DEACTIVATE** (`Deactivate.java:49-136`). The target must be `Electronics` and not `Software` (`:64`, `:80`). The search runs over the room, open containers, and the ridden computer (`:63-117`). The message is `MSG_DEACTIVATE` with the rest of the line as the target text (`:130`). Devices react to it. A panel deactivates the electronics inside it (`Items/BasicTech/StdElecPanel.java:201-207`), and software programs have their own handlers.
- **DEPOSIT** (`Deposit.java:49-98`). Only a `Banker` or a `PostOffice` accepts deposits (`:57`). Coins are parsed (`:70`, `:81-82`). The messages are "deposit(s) X into <his or her> account" for a banker and "mail(s) X" for a post office (`:85-91`).
- **CRAWL** (`Crawl.java:49-85`). Requires a direction (`:52-59`, `:81`). It sends a sit message if not already sitting (`:71-75`), then walks with `CMLib.tracking().walk(mob, dir, false, false, false)` (`:76`, `TrackingLibrary.java:768`). It costs 1.0 action out of combat and 2.0 in combat with the shipped config (`:87-97`, see section 1).
- **CLOAK** (`Cloak.java:49-95`). Applies a copy of `Prop_WizInvis` with the `IS_CLOAKED` bit (`:57-86`). The room sees "<S-NAME> become(s) cloaked!" (`:81`). `CLOAK OFF` removes it (`:59-66`). The same effect is used by `WIZINV` (`Commands/WizInv.java:41`) and `VISIBLE` (`Commands/Visible.java:80-88`).
- **COMPRESS** (`Compress.java:49-84`). Sets the COMPRESS attribute. The attribute changes list layout in `Inventory.java:174` and `Equipment.java:83`, and is read by `Painting.java:527` and `CommonMsgs.java:1646`.
- **DESCRIPTION** (`Description.java:52-71`). Shows or sets the mob's description. The limit is `128 * 1024` characters (`:43`, `:63-64`). HTML is converted to plain text (`:67`).
- **CREDITS** (`Credits.java:49-65`). Shows `resources/text/credits.txt` through the virtual page filter (`:52-55`). Without that file, it prints a one-line copyright notice (`:63`).
- **COMMANDS** (`Commands.java:48-113`). Lists the first access word of every command that is in the command list and passes its `securityCheck`, plus every ability trigger word the mob has. Four per row (`:102-104`). `COMMANDS CLEAR` (also `COMMANDS C`, `CL` and so on) empties the command queue (`:54-61`).

### 19. Experience deferral and DEFERCMD

Experience can be held back and paid out later. The rules are in `Libraries/CoffeeLevels.java`, and `DeferCmd` is the payout.

- **The pool.** When `EXPDEFER_PCT > 0`, an award goes to `deferredXP` instead of the mob, up to `MaxDeferredXP` (`CoffeeLevels.java:940-970`, `gainExperience` at `:906`). The same is done for role-play XP (`:1020-1045`, `gainRPExperience` at `:1003`). The mob is told "You've earned N deferred experience points." If no command trigger is set, and the time window has passed, the pool is paid out at once (`:949-958`).
- **The trigger.** `deferCommandCheck` (`CoffeeLevels.java:1533-1641`) decides whether the typed command is a trigger. It checks the command word, the argument pattern (an exact match, a `*` wildcard, or a suffix or prefix), the time window (`EXPDEFER_SECS`), and an optional room mask. The mask can use `{TAG}` placeholders filled from the player's generic stats (`:1601-1626`). If all pass, the command is replaced with a `DeferCmd` (`:1630-1641`).
- **Payout.** `DeferCmd.execute` moves the pool and role-play XP into experience, announces it, and levels the mob if needed (`DeferCmd.java:55-84`). It then runs the wrapped command only if pass-through is set (`:86-87`).
- **Pass-through.** `EXPDEFER_PASSTHRU` defaults to true (`CMProps.java:3800`). A command name without a leading `$` turns it off (`CMProps.java:3846-3850`). So with no `$`, the typed command is replaced and not run. The documented example is `EXPDEFER` in `coffeemud.ini:754-774`.

## Content worth mining

### Commands in this bucket

| Command (access words) | Syntax | What it does | Cost and restrictions | Tier |
|---|---|---|---|---|
| CLANWHO (CLANWHO, CLWH) | `CLANWHO [clan or player]` | Clan roster, or one member's record | 0 actions. Player lookup needs LIST_MEMBERS authority. canBeOrdered yes | Player |
| CLOSE (CLOSE, CLOS, CLO, CL) | `CLOSE <direction, exit or object>` | Closes an exit and its pair, or an object | 0.20 / 1.0 actions. Target must be seen. canBeOrdered yes | Player |
| DEACTIVATE (DEACTIVATE, DEACT, DEA, `<`) | `DEACTIVATE <device> [text]` | Switches off an electronic device | 0.20 / 1.0. Not software. canBeOrdered yes | Player |
| DEFERCMD (none) | internal | Pays out deferred XP, then runs the wrapped command | Cannot be typed. Needs EXPDEFER_PCT > 0 and a player | Internal |
| COMMANDS (COMMANDS) | `COMMANDS [CLEAR]` | Lists usable verbs; CLEAR empties the queue | 0. Not for NPCs. canBeOrdered yes | Player |
| DEPOSIT (DEPOSIT) | `DEPOSIT <amount or item> WITH <banker>` | Stores coins or items | 0.20 / 1.0. Only Banker or PostOffice. canBeOrdered no | Player |
| CLOAK (CLOAK) | `CLOAK [OFF]` | Hides the mob (`Prop_WizInvis`) | 0. Needs the CLOAK security flag. canBeOrdered yes | Staff |
| CRAWL (CRAWL, CR) | `CRAWL <direction>` | Moves one room while lying down | 1.0 out of combat, 2.0 in combat (shipped). canBeOrdered yes | Player |
| COMPRESS (COMPRESS) | `COMPRESS [ON or OFF]` | Compact lists of items and mobs | 0. Empty argument always sets ON | Player |
| DESCRIPTION (DESCRIPTION) | `DESCRIPTION [text]` | Shows or sets own description (max 131072 characters) | 0. canBeOrdered no | Player |
| CREDITS (CREDITS) | `CREDITS` | Shows the credits file | 0 | Player |
| CONFIG (CONFIG, AUTO, CONFIGURATION) | `CONFIG [QUIET] [flag, HELP, COPY, LIST, LINEWRAP, PAGEBREAK, AUTOTELLNOTIFY]` | Views and toggles options | 0. canBeOrdered no | Player |
| COLORSET (COLORSET) | `COLORSET [DEFAULT or COPY player]` | Colour editor | 0. canBeOrdered no | Player |
| COPY (COPY) | `COPY [n] name [@ target] [direction ...]` | Clones mob, item, room, exit, area or ability | Security group COPY plus per-type flags. canBeOrdered yes | Builder or admin |
| Journal verbs (default BUG, TYPO, IDEA, TASK, STUCK) | `<JOURNAL> message`; `REVIEW [name] [n]`; `TRANSFER [to] [n] [target]` | Posts to a staff journal; reviews or transfers entries | 0. Per-journal zapper mask. Review needs JOURNALS security or journal access. canBeOrdered no | Player (post); staff (review, transfer) |
| CONSIDER (CONSIDER) | `CONSIDER <target, SELF, ME or ALL>` | Threat estimate and mending hints | 0.20 / 1.0. canBeOrdered yes | Player |
| COMPARE (COMPARE, COMP) | `COMPARE <item> [item]` | Compares two items of one kind | 0.20 / 1.0. canBeOrdered yes | Player |
| DEITIES (DEITIES, GODS, DEITY) | `DEITIES [name]` | Lists or describes deities, by faith | 0. canBeOrdered yes | Player |
| CREATE (CREATE) | `CREATE <noun> ...` | In-game content builder (below) | 0. Security group `SECURITY_CMD_GROUP`, then one flag per noun. canBeOrdered no | Builder or admin |

### CREATE nouns

Each row is a branch of `Create.execute`. The gate is the `CMSecurity.SecFlag` checked before the branch runs.

| Noun (aliases) | Makes | Gate | Lines |
|---|---|---|---|
| EXIT | Exit linked both ways | CMDEXITS | 58-147 |
| ITEM (bare name) | Item, coins, catalogue item or template | CMDITEMS | 220-366 |
| MANUFACTURER | Manufacturer record | CMDITEMS | 368-401 |
| USER | New player character through the login flow | CMDPLAYERS | 403-434 |
| ROOM | Room in a direction, linked to here | CMDROOMS | 436-516 |
| ACCOUNT | Account (only with the account system) | CMDPLAYERS | 518-554 |
| MOB (bare race, class or level) | Mob from catalogue, spec or template | CMDMOBS | 622-860 |
| MIXEDRACE | Race mixing two parent races | CMDRACES | 862-891 |
| RACE | GenRace (copy-on-edit) | CMDRACES | 893-939 |
| CLASS | GenCharClass (copy-on-edit) | CMDCLASSES | 1734-1783 |
| AREA | Area and its first room (Planet for space) | CMDAREAS | 941-1019 |
| COMPONENT (SOCIAL) | Ability component, or social component | COMPONENTS | 1021-1103 |
| PLANE | Planar rule set | PLANES | 1105-1137 |
| EXPERTISE | Expertise definition | EXPERTISES | 1139-1179 |
| TITLE | Auto-title rule | TITLES | 1200-1228 |
| AWARD | Auto-award rule | AUTOAWARDS | 1181-1198 |
| ABILITY | GenAbility | CMDABILITIES | 1230-1267 |
| TRAP | GenTrap (level 1) | CMDABILITIES | 1269-1307 |
| LANGUAGE | GenLanguage | CMDABILITIES | 1354-1391 |
| CRAFTSKILL | GenCraftSkill | CMDABILITIES | 1489-1526 |
| WRIGHTSKILL | GenWrightSkill | CMDABILITIES | 1528-1565 |
| GATHERSKILL | GenGatheringSkill | CMDABILITIES | 1567-1604 |
| POISON | GenPoison | CMDABILITIES | 1606-1643 |
| TWEAK | GenTweakAbility (no override) | CMDABILITIES | 1645-1683 |
| ALLQUALIFY EACH or ALL | All-qualify mapping entry | CMDABILITIES | 1685-1723 |
| COMMAND | GenCommand wrapper or stub | CMDCOMMANDS | 1393-1487 |
| ACHIEVEMENT | Achievement tattoo (PLAYER, ACCOUNT or ALL) | ACHIEVEMENTS | 1309-1352 |
| CRON | Scheduled job (journal entry) | CMDCRON | 1785-1826 |
| SOCIAL | Social | CMDSOCIALS | 1828-1876 |
| HOLIDAY | Holiday, then MODIFY | CMDQUESTS | 2190-2211 |
| QUEST | Quest from a script line, or the quest wizard | CMDQUESTS | 2314-2352 |
| FACTION | Faction file under resources | CMDFACTIONS | 2213-2260 |
| NEWS | Entry in SYSTEM_NEWS | JOURNALS | 2270-2290 |
| POLL | Active poll | POLLS | 2300-2313 |
| HELP or AHELP | Help entry (archon file for AHELP) | CMDHELP | 592-620, 2354-2360 |
| CLAN | Clan | CMDCLANS | 2362-2397 |
| GOVERNMENT | Clan government | CMDCLANS | 2446-2476 |
| ENABLEFLAG, DISABLEFLAG | Turns a named security flag on or off | LISTADMIN | 2100-2128 |
| DEBUGFLAG | Turns a debug flag on; HTTP flags also change the web server | LISTADMIN | 2130-2180 |
| WEBSERVER | Starts a web server | sysop | 2399-2419 |
| SMTP and InterMUD protocol names | Starts the protocol | sysop | 2421-2444 |
| (anything else) | Guessing fallback (section 2) | per target | 2477-2666 |

### Config options

Option names are the `MOB.Attrib` display names (`MOB.java:1055-1090`). Grouped by purpose, from the help text in `resources/help/misc_help.ini:59-90`.

| Group | Options | Effect when on |
|---|---|---|
| Combat automation | AUTOASSIST, AUTOMELEE, AUTOATTACK, AUTODRAW, AUTOGUARD, AUTOLOOT, AUTOGOLD | Joins allies' fights, advances on targets, attacks each round (default on for AUTOATTACK), draws weapons, stays put as a follower, loots corpses |
| Combat text | NOBATTLESPAM | Summarised combat instead of blow-by-blow |
| Movement and display | AUTOEXITS, AUTORUN, BRIEF, AUTOMAP, COMPRESSED | Exits on look, runs between rooms, skips room descriptions, mini-map (only if awareness range above 0), shorter item and mob lists |
| Learning and groups | AUTOIMPROVE (AUTOIMPROVEMENT), NOTEACH, NOFOLLOW | Proficiency notices, no teaching, no one may group with you |
| Messaging | QUIET, AUTONOTIFY, AUTOTELLNOTIFY, AUTOFORWARD, PRIVACY, MOTD (DAILYMESSAGE), AUTOWEATHER, SYSOPMSGS (SYSMSGS) | Channels and tells off, friend notices, account tell notices, email forwarding, announcements, login message of the day, weather notices, staff system messages |
| Terminal and prompt | ANSI COLOR (ANSI), ANSI16ONLY, ANSI256ONLY, MXP, SOUNDS (SOUND), TELNET-GA (TELNET_GA), NOREPROMPT | Colour modes, MUD eXchange protocol, MSP sounds, go-ahead code, prompt only on input |
| Player combat | PLAYERKILL | Whether you can kill other players |

NOSPAM, LFG, AUTOMAP, ANSI256ONLY and SYSOPMSGS have no entry in `misc_help.ini`, so their meaning is taken from the code, not from help.

The three multi-word options and the alternate names are the ones that do not match a single typed word (see the problems section).

### Colour slots

The 19 fixed slots in `ColorSet.java:267-285`, with their code characters.

| Slot | Code | Slot | Code |
|---|---|---|---|
| Normal Text | N | Items | I |
| Highlighted Text | H | MOBs | M |
| Your Fight Text | f | Channel Colors (default for channels) | Q |
| Fighting You Text | e | Exits (explored) | D |
| Other Fight Text | F | Doors (explored) | d |
| Spells | S | Exits (unexplored) | U |
| Emotes | E | Doors (unexplored) | u |
| Says | T | Room Titles | O |
| Tells | t | Room Descriptions | L |
| Weather | J | | |

### Command journals (shipped defaults)

From `coffeemud.ini:1496-1501`.

| Journal | Flags | Notes |
|---|---|---|
| BUG | CONFIRM, SCRIPT=progs/bugreport.script | Script prompts for more detail (`resources/progs/bugreport.script`) |
| TYPO | ADDROOM | Prefixes the room ID |
| IDEA | none | |
| TASK | none | |
| STUCK | CHANNEL=WIZINFO, EXPIRE=2, ADMINECHO | Entries echo to a channel and expire after 2 days |

### Consider thresholds

From `Consider.java:91-99` and the message ladder at `:147-179`.

| Your level | `theDiff` | Perfect match if the absolute realDiff is below | Target weaker: realDiff above | Target stronger: realDiff below |
|---|---|---|---|---|
| 20 or less | 2 | 2 | -4, -6, -8 | 4, 6, 8 |
| 21 to 40 | 3 | 3 | -6, -9, -12 | 6, 9, 12 |
| 41 to 60 | 4 | 4 | -8, -12, -16 | 8, 12, 16 |
| 61 to 80 | 5 | 5 | -10, -15, -20 | 10, 15, 20 |
| 81 and up | 6 | 6 | -12, -18, -24 | 12, 18, 24 |

The weaker side uses breakpoints of -2, -3 and -4 times `theDiff`, and the stronger side uses 2, 3 and 4 times `theDiff`. Beyond the last breakpoint the text is the strongest one.

### Other content

- **Faith tiers** (section 14): 25 and 100 on `STAT_FAITH`.
- **Compare outcomes:** "looks better than", "looks worse than", "look about the same" (weapons), "looks like it holds more than" or "less than" (containers).
- **Consider wording:** the ladder in section 12 (for example "is basically a pushover.") is the most reusable text in this bucket.
- **Close sound:** `dooropen.wav` is sent through MSP with a duration of 10 (`Close.java:55`).
- **CREDITS and COMMANDS** have no reusable data beyond their text.

## Notable design ideas

- **Copy-on-edit to data.** A standard race, class or ability is converted to a data record that keeps its ID, so a builder can change it without losing the rest of the game. The warning tells the builder how to undo it: destroy the same ID (`Create.java:912-918`).
- **One noun, one branch.** CREATE keeps a flat table of nouns, each with its own gate flag and its own record type (section 2). The fallback re-dispatches guessed input rather than failing (`:2477-2666`).
- **Spec-driven mobs.** One line such as `race class level` builds a mob, and the class adds its own trainer effect with stat ceilings computed from class data (`:645-815`).
- **Reciprocal exits made for the builder.** Creating an exit also writes the far side (`:124-142`).
- **Survival-round threat.** A simple two-number comparison that is compressed with a square root and graded with level-scaled bands (`Consider.java:50-179`).
- **Held and wielded normalisation.** Comparison treats a held item and a wielded one as the same slot family (`Compare.java:88-92`).
- **Shop items as comparison targets.** A player can inspect shop stock without buying (`Compare.java:60-87`).
- **Faith as an information gate.** A stat decides how much of a deity's requirements and powers is shown (section 14).
- **Journals as declarative verbs.** A flag string per journal gives confirmation, scripting, channel echo, expiry and routing (`coffeemud.ini:1466-1501`).
- **Cron jobs as stored journal entries.** A scheduled task is a record with a next-run time, reusing the journal storage (`Create.java:1816-1822`).
- **Bit flags with inverted defaults.** Options default on by storing the cleared bit (`MOB.java:1095-1107`, `Config.java:514-517`).
- **Colour diffs.** Only changed slots are stored, which keeps the save small (`ColorSet.java:125-149`).
- **Clone with ID translation.** An area copy maps old rooms to new ones so internal exits point at copies (`Copy.java:412-438`).
- **Deferred rewards.** Experience sits in a pool and is paid when the player does something specific, with an optional room mask (section 19).

## Problems and cruft

Verified by reading the code. Nothing here was run.

### Bugs

1. **Config: multi-word option names cannot be typed.** `Config.java:67-73` matches the one typed word against the display name. `ANSI COLOR`, `16 COLOR ONLY` and `256 COLOR ONLY` contain spaces, and `CMParms.parse` splits on whitespace (`core/CMParms.java:788-835`). So `CONFIG ANSI` falls through to "Unknown configuration flag 'ANSI'" (`:313`). The help screen (`:122`) shows the display names, so the help and the command disagree. Only a quoted word would match.
2. **Config COPY: cleanup is inverted and runs too early.** `Config.java:141` sets `unloadAfter = isLoadedPlayer(...)`, which is true when the player was already loaded. `ColorSet.java:180` negates that. The cleanup in `finally` (`Config.java:224-230`, `ColorSet.java:223-229`) runs as soon as `sess.prompt(...)` returns. But `DefaultSession.prompt` is non-blocking (`Common/DefaultSession.java:1748-1759`), so the player's answer comes later. The copy then reads a player object that may already have been unloaded. The effect was not run.
3. **Compress: an empty argument can never turn the option off.** `Compress.java:53` treats an empty argument as ON. The OFF test at `:66` also tests `parm.length()==0`, so it is never reached for empty input. The help text "or nothing to toggle" (`:81`) is wrong.
4. **CommandJournal TRANSFER without a count.** `count` defaults to 0 (`CommandJournal.java:88`). Both the journal branch (`:173-174`) and the player or category branch (`:193-195`) call `journal2.get(count-1)`, which is `get(-1)`. The only guard is `count > size` (`:140`). So `IDEA TRANSFER <target> <journal>` with no count would throw an index exception. This is by reading. Nothing checks for a count below 1.
5. **Copy: a failed room copy leaves a link.** `Copy.java:326-330` sets `room.rawDoors()[dir] = newRoom` and the reverse links before the ID check at `:332-337`. If the ID is empty, the function returns with the room still pointing at an unsaved room. `StdRoom.getRoomInDir` reads `doors[]` (`Locales/StdRoom.java:1662-1666`), so the room now seems to have a neighbour, and the check at `:309-313` refuses later copies that way.
6. **Create ROOM: a room is saved before its exit is checked.** `Create.java:501-502` writes the room. `:504-507` shows any error from `createNewExit` but does not remove the room. A failed link leaves an orphan room record.
7. **Create ROOM: the display name is set before the ID.** `Create.java:489` sets the display text to `<class>-<roomID>`, but the ID is only assigned at `:493`. AREA does it in the right order (`:1009-1011`). The default name probably omits the ID (appears; not run).
8. **Create MOB: garbled name for a level-only spec.** `Create.java:794` builds `"a level @x1er"`, which gives "a level 5er".
9. **Create ACCOUNT: passwords are lowercased.** `Create.java:528` lowercases the password. For unhashed passwords, `CMEncoder.passwordCheck` compares with `equalsIgnoreCase` (`Libraries/CMEncoder.java:170-191`), so case is not significant. The hashed path was not examined. (No password is reproduced here.)
10. **Create COMPONENT: a cancelled edit may leave an entry.** The map entry is added at `Create.java:1090-1091`, before the generic editor runs (`:1092`). If the edit is abandoned, the blank entry stays in memory until restart. Not verified.
11. **Copy: no upper bound on count.** `Copy.java:61-69` accepts any number. `COPY 100000 sword` creates that many items, with nothing to stop it.
12. **Create TWEAK vs the other nouns.** Most nouns override an existing ID with a notice (`:1254-1258`). TWEAK refuses instead (`:1669-1674`).
13. **Close: no message for a closed or vetoed exit.** `Close.java:61-95` returns false when the exit is already closed or the room vetoes it. The `else` at `:97` is attached to the exit test, so it does not run for exits. The fall-through returns with no message (`:105`). No exit class in `Exits/` handles `MSG_CLOSE` (grep). A room-level listener might still speak; that was not checked.

### Dead or misleading code

14. **Deactivate.** `Deactivate.java:124-127` tests `item == null` when `E` is non-null. `item` is set whenever `E` is set, so the branch cannot run.
15. **Deposit.** `Deposit.java:57` rejects anything that is not a Banker or a PostOffice. The Librarian branches at `:69` and `:84-85` can then never run. `StdLibrarian` implements only `Librarian` (`MOBS/StdLibrarian.java:42`).
16. **Cloak.** `Cloak.java:53-54` inserts OFF when the verb does not start with C. Every access word starts with C (`:41`), so the branch cannot run.
17. **DEFERCMD and the `$` prefix.** A trigger without `$` silently swallows the typed command (`CMProps.java:3846-3850`, `DeferCmd.java:86-87`). This is documented in `coffeemud.ini:754-774`, but it surprises anyone who expects the command to run.
18. **Command journal access list.** `CommandJournal.getAccessWords` caches a static array (`:44-66`). A journal added after the first call does not get a verb (not verified).

### Messages and documentation

19. **Create ROOM usage** says "LINK [ROOM ID]" (`Create.java:482`). There is no LINK branch in `rooms()` (`:469-491`).
20. **Create listOfThings** (`:1727-1731`) lists COMMAND twice and omits AREA, ACCOUNT, SOCIAL, PLANE and EXPERTISE, which all exist.
21. **Create AWARD usage** (`:1187`) shows one colon between the player and date masks. The check (`:1184`) and the loader (`Libraries/AutoAwards.java:649-652`) need two `::` separators.
22. **Create ACHIEVEMENT** rejects tattoos that end in a digit (`:1344-1349`). The loader rejects tattoos that start with one (`Achievements.java:959`). The reason for the trailing rule is not in the code.
23. **Copy messages.** `Copy.java:133` has an unbalanced quote: "@x1' should be...". `:53` shows the wave emote before the usage check.
24. **ColorSet.** `ColorSet.java:192` has a stray backtick: "`s".
25. **Create SOCIAL** (`:1836`) starts "but fail to specify the proper fields".
26. **Commands** prints "flub(s) a spell" for many failures. This is flavour, not a bug.

### Hacks and multiplayer assumptions

27. **Copy archon items** are allowed only when the MUD's name contains "coffeemud" (`Copy.java:267-268`). That is a name check standing in for a real permission.
28. **Create's guessing fallback** (`:2477-2666`) re-enters `execute` with rewritten arguments, including a loop that runs the whole command N times (`:2627-2631`). It is hard to follow and hard to test.
29. **ClanWho** loads the named player and never unloads them (`ClanWho.java:68`), unlike Config and ColorSet. Impact not measured.
30. **Clan, mail, channel and other-player features** run through most of this bucket: ClanWho, COPY of another player's settings, the journal TRANSFER to a player, cloaking from others, and the sysop intermud and web-server branches.

## Takeaways for a Python SUD

**Borrow**

- **Threat estimate as a pure function.** Port `relativeLevelDiff` almost as written. Use a `Combatant` dataclass with `hp`, `attack`, `armor`, `damage` and `speed`, and map the signed result to text with the ladder in section 12. Replace the `normalizeBy5` dice call with a plain rounding to multiples of 5.

  ```python
  def hit_round(a, d):
      p = round(50 * a.attack / d.armor / 5) * 5 / 100   # normalise to 5s
      return (p * (a.damage / 2) + 1) * a.speed
  def rel_level(target, you):
      t = target.hp / hit_round(you, target)
      y = you.hp / hit_round(target, you)
      diff = (t - y) / 2
      return int(round(abs(diff) ** 0.5)) * (1 if diff >= 0 else -1)
  ```

- **Compare as a slot comparison.** Give each item a `slot` key. Normalise held and wielded to one family, as `Compare.java:88-92` does. Let a shop item be compared by reference without buying.
- **Content as data with overrides.** A race, class or ability is a YAML or TOML record. A builder edit can copy a standard definition, keep its ID, and warn about what the copy loses. This is the pattern at `Create.java:893-939`.
- **Reciprocal links.** Make `world.connect(a, dir, b, kind)` always write both sides. That is the CREATE EXIT behaviour (`Create.java:124-142`).
- **Area and room cloning** with an ID translation map (`Copy.java:412-438`) is a good basis for procedural zones: copy a region with fresh IDs, relink its internal exits, and leave external exits as they were.
- **Faith-style information gating.** Use a standing or knowledge value with two thresholds (25 and 100) to choose how much of a faction or deity's text is shown.
- **Deferred rewards.** A pool of banked experience that pays out on a chosen action, such as resting or sleeping, with a cap (`Libraries/CoffeeLevels.java:940-970`). The room mask and command-argument matching can go.
- **Config as a typed settings object.** A dataclass with defaults, and one setter per option for its side effects, is simpler than bit flags with a reversed default. Keep the "option is on by default" display rule as a field.
- **Colour theme as a diff.** A theme dict keyed by the 19 semantic slots above, storing only overrides.
- **Journals as a notes system.** A single "notes" file with categories, a TRANSFER-style move between categories, and an expiry, for player bug reports or a quest log. Drop the channels and ACL.

**Simplify**

- Command costs: one global action budget with per-verb overrides, using the shipped 0.20 and 1.0 pair as the starting point. For a turn-based game, use integer action points, as the engine configuration chapter (b002) notes.
- Replace `canBeOrdered` and the charm and follower checks with nothing. A single-player game has no ordered mobs.
- Replace the nested Create fallback with an explicit noun table. The table in section 2 maps directly onto a dict of handlers.

**Drop**

- Clans, ClanWho, channels, journal ASSIGN and REPLYALL routing, COPY of other players' settings, player-to-player TRANSFER, cloaking from other players, sysop intermud and web-server commands, accounts and password handling, and CREATE USER and ACCOUNT.
- Zapper masks in the Python version. Use plain predicates on the player state.

**Rethink for single player**

- Hiding (CLOAK) is a player-only concept here. A single-player game could keep it as a stealth state that only the NPC systems check.
- DEPOSIT is a bank. In a single-player game it is storage, and it can be a plain container with a fee rule.
- The Create tooling is a builder feature for a multiplayer world. A single-player SUD would author content in files and load it, with a small validation command in place of the in-game editor.

## Coverage

**Read in full (all 19 files, licence headers skipped):** `Commands/ClanWho.java`, `Close.java`, `Deactivate.java`, `DeferCmd.java`, `Commands.java`, `Deposit.java`, `Cloak.java`, `Crawl.java`, `Compress.java`, `Description.java`, `Credits.java`, `Config.java`, `ColorSet.java`, `Copy.java`, `CommandJournal.java`, `Consider.java`, `Compare.java`, `Deities.java`, and `Create.java` (all 2682 lines, read in sections).

**Skimmed:** none.

**Consulted only for the interactions described:** `Commands/StdCommand.java` (cost defaults, `:224-261`), `Libraries/CoffeeLevels.java` (`:906-1045`, `:1533-1641`), `core/CMProps.java` (`:1143-1196`, `:3796-3850`), `Libraries/CMJournals.java` (`:671-692`, `:1020-1036`), `Libraries/Achievements.java` (`:959`), `Libraries/AutoAwards.java` (`:649-652`), `MOBS/interfaces/MOB.java` (`:1055-1134`), `Locales/StdRoom.java` (`:1662-1680`), `Areas/StdArea.java` (`:625-690`), `Libraries/CMEncoder.java` (`:170-191`), `core/CMParms.java:788-835`, `Libraries/CMProtocols.java` (`:2387-2395`), `Common/DefaultSession.java` (`:1748-1759`), `coffeemud.ini` (`:537-546`, `:754-774`, `:1466-1501`), `resources/help/misc_help.ini` (`:59-90`), `resources/progs/bugreport.script`, and `Libraries/interfaces/AbilityComponents.java` (header).

**Not checked by running:** every behaviour in this chapter comes from reading the code. Items marked "appears" or "not verified" were not confirmed by execution.
