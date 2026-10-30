# Commands, part 4: Destroy to Exits

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b113` (Commands [part 4/12: Destroy .. Export]) · 28 files · 8789 lines of Java.

## Summary

This bucket holds 28 command classes that run from `DESTROY` through `EXPORT` and the verbs between them. Two groups stand out. The staff tools are `Destroy`, `Export`, `Every`, `Deviations`, `Expire` and `DumpFile`: `Destroy` is a 2,466-line hub that deletes almost every kind of game object, while `Deviations` is a balance report that compares each mob or item with the stats expected for its level. The player verbs cover item handling (`Draw`, `Dress`, `Empty`, `Eat`, `Display`), movement and riding (`Enter`, `Down`, `East`, `Dismount`, `Disembark`, `Exits`), perception (`Examine`, `Equipment`, `Experience`, `Expertises`), social play (`Duel`, `Emote`, `Email`) and two mechanics of their own: `Dig`, a timed action that builds a hole one tick at a time, and `DieRoll`, a difficulty ladder for stat and save checks. Most verbs follow the usual shape of checking words, building a `CMMsg` and letting the room veto it. For a single-player SUD, the reusable parts are the dice ladder, the timed digging action, the layered-clothing model in `Equipment`, the balance report, and the material rules that decide what a player can break. Mail, duels, consent-based dressing and the admin machinery can be dropped.

## Key files and types

Java paths below omit the `com/planet_ink/coffee_mud/` prefix, so `Commands/Destroy.java` means `com/planet_ink/coffee_mud/Commands/Destroy.java`. Paths outside Java are given relative to the CoffeeMUD folder.

| File or class | Role | Lines |
|---|---|---|
| `Commands/Destroy.java` | Player item breaking, and the staff `DESTROY` hub for about 50 subcommand words (listed at lines 70-74) | 2466 |
| `Commands/Export.java` | Staff XML export of rooms, areas, the world, catalogue, races, classes, abilities, players and accounts | 873 |
| `Commands/Deviations.java` | Staff balance report comparing mobs and items with level-expected stats | 700 |
| `Commands/Email.java` | Player mailbox (journal-backed), compose and reply, and the address change | 560 |
| `Commands/Equipment.java` | Lists worn items by location with clothing layers and tattoos | 434 |
| `Commands/Draw.java` | Draws a weapon from a sheath or pack; extends `Get` | 272 |
| `Commands/Every.java` | Staff macro: runs a command for each room, mob, item or exit | 264 |
| `Commands/Drop.java` | Drops items and coins (core verb; detail in b170) | 258 |
| `Commands/Empty.java` | Empties a container onto the floor, into a container, or pours liquid; extends `Drop` | 256 |
| `Commands/Duel.java` | Challenge and accept handshake that starts a duel | 213 |
| `Commands/Dress.java` | Puts an unworn item on another character | 197 |
| `Commands/Examine.java` | Detailed look at a thing, a direction or an exit | 191 |
| `Commands/Expire.java` | Staff: reads or sets account and player expiry | 190 |
| `Commands/Emote.java` | Free-form emote, social shortcut and roleplay XP award | 184 |
| `Commands/DieRoll.java` | Dice roller and stat or save check against a difficulty ladder | 179 |
| `Commands/Dig.java` | Timed digging action that makes or deepens a hole | 176 |
| `Commands/Eat.java` | Eats food, from the room or an open container | 157 |
| `Commands/Display.java` | Shows an item to another character without transfer | 156 |
| `Commands/Expertises.java` | Lists the character's expertises | 148 |
| `Commands/DumpFile.java` | Staff: sends file text to online sessions | 135 |
| `Commands/DrinkCmd.java` | Drinks from a liquid source or a container | 128 |
| `Commands/Dismount.java` | Gets off a mount or removes a rider | 121 |
| `Commands/Enter.java` | Enters an exit, item or direction; extends `Go` | 112 |
| `Commands/Experience.java` | Prints XP, hours online and account expiry | 100 |
| `Commands/Disembark.java` | Leaves what the character is riding | 94 |
| `Commands/Down.java` | Moves down; extends `Go` | 76 |
| `Commands/East.java` | Moves east; extends `Go` | 76 |
| `Commands/Exits.java` | Lists the exits of the room | 73 |
| `Commands/UnLink.java` (outside this bucket) | Delegates `UNLINK` to `Destroy` with the first word rewritten (lines 52-55) | 71 |
| `Commands/Go.java` (outside this bucket) | Base of the direction verbs; supplies the cost used by `Enter`, `Down` and `East` (lines 261-276) | 283 |
| `Commands/Get.java` (outside this bucket) | Base of `Draw`; supplies its combat cost (lines 241-250) | — |

## How it works

### Conventions shared by these commands

- **Action cost.** Each command either inherits the `StdCommand` default, which returns a per-command override or 0.0 (`Commands/StdCommand.java:224-233`), or calls `CMProps.getCommandActionCost(ID())`, which falls back to the configured defaults. Those are `DEFCMDTIME=0.20` out of combat and `DEFCOMCMDTIME=1.0` in combat (`coffeemud.ini:545-546`; `core/CMProps.java:1183-1196`). Eleven commands in this bucket never override the cost and so cost nothing: `Export`, `Deviations`, `Email`, `Equipment`, `Every`, `Expire`, `Expertises`, `DumpFile`, `Experience`, `Exits` and `DieRoll`.
- **Selecting one of several items.** `item.2` picks the second match, `ALL.` and `.ALL` select every match, and a leading number caps how many are taken (`Drop.java:126-141`; `Destroy.java:1454-1532`; `Draw.java:182-202`). The loop in `Destroy.execute` appends `.2`, `.3` and so on to the name until no match remains (`Destroy.java:1478-1532`).
- **Failure reports.** Errors go through `CMLib.commands().postCommandFail(mob, origCmds, text)` with the original words attached (`Dress.java:55`; `Draw.java:250`). The mechanism is covered in b170.
- **Room messages.** Output uses tokens such as `<S-NAME>`, `<T-NAME>` and `<O-NAME>`, sent with `show`, `showOthers` and `showHappens` on the room.

### Command catalogue

Audience is marked as player, builder or admin. "Orderable" means `canBeOrdered()` is true, so a leader or charmer can order a follower to do it (`interfaces/Command.java:97-101`). The default for `canBeOrdered()` is true (`StdCommand.java:265-268`).

| Command (access words) | Syntax and effect | Cost, action / combat | Restrictions | Audience |
|---|---|---|---|---|
| Destroy (DESTROY, JUNK, TEAR) | Player: `DESTROY [n] item` breaks glass, cloth, paper or liquid items. Staff: `DESTROY <kind> ...` runs a subcommand (table below). | 0.20 / 1.0 (`Destroy.java:2449-2458`) | Player: worn items must come off first (`1509-1515`). Not orderable (`2461`). | Player and staff |
| Export (EXPORT) | `EXPORT <ROOM, WORLD, AREA, CATALOG, CLASS, RACE, ABILITY, PLAYER, USER, ACCOUNT, REALESTATE> [ITEMS, MOBS, WEAPONS, ARMOR] <file, SCREEN, EMAIL, MEMORY>` writes XML. | 0 | EXPORT group (`Export.java:869-871`). PLAYER and ACCOUNT also need EXPORTPLAYERS (`219`). Orderable. | Builder |
| Deviations (DEVIATIONS) | `DEVIATIONS <MOBS, ITEMS, WEAPONS, ARMORS, BOTH> <name, ROOM, AREA, METRO, WORLD> [LOCATIONS] [SORTBY NAME, TYPE, LEVEL, PLEVEL]` prints a balance report. | 0 | CMDITEMS, LISTADMIN or CMDMOBS (`Deviations.java:694-699`). Orderable. | Builder |
| Email (EMAIL) | `EMAIL` changes the address. `EMAIL BOX [n or n-m]` reads mail. `EMAIL <player> [n-m]` sends mail. | 0 | Needs the MAILBOX property (`Email.java:515`). Not orderable (`555`). | Player |
| Equipment (EQUIPMENT, EQ, EQUIP) | `EQUIPMENT [LONG]` lists worn items by location, in layers. | 0 | A sleeping viewer sees nothing (`Equipment.java:74`). Orderable. | Player |
| Every (EVERY) | `EVERY ROOM, MOB, ITEM or EXIT [<MASK>..</MASK>] [INAREA or INWORLD] [+PLAYERS] <command with *>` runs a command for each target. | 0 | Needs EVERY (`Every.java:260-263`). Not orderable (`254`). | Builder |
| Draw (DRAW) | `DRAW [weapon] [container] [QUIETLY] [IFNECESSARY] [HELD]` takes a weapon from a sheath or the pack. | 0.20 / 1.0 (combat inherited from `Get`, `Get.java:241-244`) | Needs a weapon in a sheath or the pack. | Player |
| Drop (DROP, DRO) | `DROP [n] item`. Core verb; mechanics in b170. | 0.20 / 1.0 (`Drop.java:217-223`) | Worn items must come off first (`171-180`). "Drop all" skips coins unless the character carries only coins (`184-185`). | Player |
| Empty (EMPTY, EMP) | `EMPTY <container> [INTO target, HERE, ME, FLOOR]`. | 0.20 / 1.0 (`Empty.java:241-247`) | Drinks must be POURed (`162-166`). A drink as target gets the liquid through the Pour command (`191-192`). | Player |
| Duel (DUEL) | `DUEL <player> [UNFAIR]` challenges. `DUEL ACCEPT` or `DUEL <name>` accepts. | 0.20 / 1.0 | Monsters cannot duel (`124-125`). The window is `DUELTICKDOWN=5` ticks (`coffeemud.ini:449`). | Player |
| Dress (DRESS) | `DRESS <target> <item>` puts an unworn item on a target. | 0.20 / 1.0 | Not in combat (`58-62`). Target must follow the caller or be bound or held (`89-96`). Staff can force it (`98-101`). Orderable. | Player, staff override |
| Examine (EXAMINE, EXAM, EXA, LONGLOOK, LLOOK, LL, ID) | `EXAMINE [thing, direction, EXIT or SELF] [UNOBTRUSIVELY]`. | 1.0 flat action (`Examine.java:183`) / 1.0 combat | Visibility only. | Player |
| Expire (EXPIRE) | `EXPIRE <name> [days, NEVER or NOW]` reads or sets expiry. | 0 | CMDPLAYERS (`Expire.java:185-188`). Not orderable (`179`). | Admin |
| Emote (EMOTE, ",", ";", ":") | `EMOTE text`, or `,` followed by a social name. `/name` tokens point at people in the room. | 0.20 / 1.0 | Input runs through EMOTEFILTER (`Emote.java:100`). RP XP only if RPAWARDS is set (`coffeemud.ini:802`, empty by default). | Player |
| DieRoll (DIEROLL, DROLL) | `DIEROLL NdM`, `DIEROLL DM`, or `DIEROLL <stat or save> [difficulty]`. | 0 | None. | Player |
| Dig (DIG) | `DIG` starts digging. `DIG STOP` stops it. | 10/speed out of combat, 30/speed in combat (`Dig.java:154-163`) | The room must allow digging (`StdRoom.java:815-840`). Cancellable (`Dig.java:172-175`). | Player |
| Eat (EAT) | `EAT [FROM container] food`. | 0.20 / 1.0 | Food must be held when the character has hands (`Eat.java:101-106`). The container must be open (`85-89`). | Player |
| Display (DISPLAY, SHOW) | `SHOW item TO person` shows an item without giving it. | 0.20 / 1.0 | Recipient must be visible in the room (`Display.java:65-66`). | Player |
| Expertises (EXPERTISES, EXPS) | `EXPERTISES` lists expertises in columns. | 0 | Orderable. | Player |
| DumpFile (DUMPFILE) | `DUMPFILE [RAW] user-or-all file...` prints file text to online sessions. | 0 | DUMPFILE (`DumpFile.java:130-133`). Orderable. | Admin |
| DrinkCmd (DRINK, DR, DRI) | `DRINK [FROM] liquid or container`. | 0.20 / 1.0 | Can take a sip from a drink held in a container (`DrinkCmd.java:82-102`). | Player |
| Dismount (DISMOUNT) | `DISMOUNT [rider]` gets off a mount or removes a rider. | 0.20 / 1.0 | A rider must be held, bound or willing (`Dismount.java:91`). | Player |
| Enter (ENTER, EN) | `ENTER item, exit or direction`. Sits on a Rideable, or enters a dead body. | 0.20 / 0.20 (`Go.java:261-276`) | Must stand up first if sitting (`Enter.java:72-75`). | Player |
| Experience (EXPERIENCE, EXPER, XP, EXP) | Prints XP, hours online, XP to next level and account expiry. | 0 | Suppressed by the EXPERIENCE and SHOWXP disables (`Experience.java:48-49`). | Player |
| Disembark (DISEMBARK) | Leaves whatever the character rides. | 0.20 / 1.0 | Must be riding (`Disembark.java:55-67`). | Player |
| Down (DOWN, D) | Moves down, or runs with AUTORUN. | 0.20 / 0.20 (`Go.java`) | Non-NPCs cannot go down in a ship-type location (`Down.java:68-76`). | Player |
| East (EAST, E) | Moves east, or runs with AUTORUN. | 0.20 / 0.20 (`Go.java`) | Non-NPCs can go east only in compass-type locations (`East.java:68-76`). | Player |
| Exits (EXITS, EX) | `EXITS [SHORT or BRIEF]` lists exits. | 0 | None. | Player |

Staff subcommands of `DESTROY` are catalogued in the Destroy section below.

### Destroy: the player breaker and the staff hub

**Gate and dispatch.** `execute` first asks whether the caller holds any staff right: a command group, a kill group at the location, BAN, NOPURGE, or journal access (`Destroy.java:1430-1434`). If the caller holds none, the player branch runs (`1436-1601`). Otherwise the second word selects a staff subcommand (`1603-1607`). Command journals are checked first (`1609-1675`), then a long `if` chain (`1676-2446`). Each branch checks its own `CMSecurity` flag before doing anything.

**Player destruction by material.** The breakable set is decided by the item's material, not by its name (`Destroy.java:1328-1349`, `1487-1514`):

| Material | Message | Result |
|---|---|---|
| Liquid | "pours out" (`1337`) | Item removed. Containers are emptied first (`1360`). |
| Paper | "tears up" (`1341`) | Item removed. |
| Cloth | "tears up" (`1341`) | Item removed, and it leaves "a strip of [material]" for each unit of weight, each of weight 1, packed into a stack when the weight is above 1 (`1362-1388`). |
| Glass | "smashes" (`1344`) | Item removed. |
| Any other | refused: "can not be easily destroyed" (`1505`) | Nothing happens. |

A cloth item already at weight below 1, or a raw material, gets "is already in small pieces" (`1496`). A worn item must be removed first (`1509-1515`). If no item matches and a monster of that name is present, the command offers to run `KILL` after a y/N prompt (`1544-1591`).

**Staff subcommands.** The table lists each subcommand, its gate, and what it removes.

| Words | Gate | Removes or changes | Lines |
|---|---|---|---|
| EXIT | CMDEXITS | Wall in one direction of this room | `483-521`, `1676-1682` |
| HELP, AHELP | CMDHELP | A help entry | `523-549` |
| ITEM | CMDITEMS | Items in the room, inventory or a container. `N name` removes N. `name @ mob/room/container` | `568-696` |
| AREA | CMDAREAS per area | One area, or all areas but those named after `EXCEPT` | `698-778` |
| ROOM | CMDROOMS for the room, CMDEXITS for a direction | A room, or the exits in one direction (the UNLINK path) | `357-481` |
| RACE | CMDRACES | A generic race only (`797-802`) | `780-816` |
| COMMAND | CMDCOMMANDS | A generic command class only (`835-840`) | `818-862` |
| CLASS | CMDCLASSES | A generic character class only (`1005-1010`) | `988-1024` |
| ABILITY, LANGUAGE, CRAFTSKILL, GATHERSKILL, POISON, TWEAK, WRIGHTSKILL, TRAP | CMDABILITIES | A generic ability only (`1043-1048`) | `1026-1060` |
| ACHIEVEMENT | ACHIEVEMENTS | An achievement by tattoo. Rebuilds the tracker of loaded players | `1253-1285` |
| ALLQUALIFY | CMDABILITIES | An all-qualify mapping entry | `1396-1424` |
| COMPONENT (SOCIAL) | COMPONENTS | An ability component or social component | `864-907` |
| PLANE | PLANES | A planar universe | `909-929` |
| EXPERTISE | EXPERTISES | An expertise definition | `931-951` |
| TITLE | TITLES | An auto-title. Existing holders keep it (`953-958`) | `953-986` |
| AWARD | AUTOAWARDS | An auto-property award | `77-97` |
| USER, PLAYER | CMDPLAYERS | A player, after confirmation | `263-293` |
| ACCOUNT | CMDPLAYERS, account system on | An account and all its players | `228-261` |
| DELETE: | CMDAREAS everywhere, or SysOp | Matches of an MQL query (see below) | `1062-1251`, `1824-1830` |
| SOCIAL | CMDSOCIALS | A social, then saves the social file | `1287-1326` |
| DISABLEFLAG, ENABLEFLAG | LISTADMIN | Clears a disable or enable flag | `1848-1876` |
| DEBUGFLAG | LISTADMIN | Clears a debug flag. HTTP flags also switch the web server off | `1878-1928` |
| NOPURGE | NOPURGE | Removes a player's no-purge protection | `1930-1942` |
| HOLIDAY | CMDQUESTS | A holiday | `1944-1964` |
| TICKS | SysOp | Ticking objects by name, after confirmation | `1966-1992` |
| BAN | BAN | A ban entry by number | `1994-2010` |
| THREAD | SysOp | Stops a thread | `2012-2028` |
| SESSION | BOOT | Stops a session by number | `2030-2057` |
| JOURNAL, and BUG, TYPO, IDEA (command journals) | JOURNALS | All of a journal, or one entry by number | `2059-2099`, `1609-1675` |
| GRACES | CMDRACES | Prunes races no longer used by any saved character | `2101-2122` |
| FACTION | CMDFACTIONS | A faction file | `2124-2160` |
| MOB | CMDMOBS | Mobs in the room, with `ALL.` and `.ALL` | `99-147`, `2162-2168` |
| CRON | CMDCRON | A cron job | `178-226`, `2170-2176` |
| MANUFACTURER | CMDITEMS | A manufacturer; its goods move to ACME | `149-176`, `2178-2184` |
| WEBSERVER, SMTP, intermud protocols | SysOp | Stops the service | `2186-2229` |
| POLL | POLLS | A poll | `2231-2257` |
| QUEST | CMDQUESTS | A quest; a running quest is stopped first | `2259-2291` |
| CLAN, GOVERNMENT | CMDCLANS | A clan, or a clan government (not the last one) | `2293-2343` |

The `listOfThings` string (`Destroy.java:70-74`) lists the same words for the "you cannot destroy" reply.

**Deletion boundaries.** Destroy will not delete a built-in class. Races, commands, character classes and abilities all need `isGeneric()` (`797`, `835`, `1005`, `1043`). Generic objects are the ones built from data (`Gen*`), and compiled Java classes are protected. A deleted generic class is removed from the database and the class registry, and where a Java class of the same name exists, it is reloaded (`CMClass.loadClass`, `806`, `1014`).

**MQL bulk delete.** `DESTROY DELETE: <query>` (and `AREA DELETE:`) turns the words into an MQL statement and runs it through `CMLib.percolator().doMQLSelectObjects` (`1062-1069`). It then prints a preview of each match with its approximate room (`1072-1087`), and only after "Save the above deletions (y/N)?" does it act (`1215-1239`). Acting means removing an ability from its owner and saving the owner (`1103-1111`), deleting items, mobs, rooms or areas by database ID (`1183-1205`), and saving the room, owner or area that held them (`1155-1182`).

**Confirmation semantics and a hazard.** Area deletion is a loop over `areaNames` (`742-777`). The first answer of "Y" sets the shared `confirmed` flag (`768`), and the flag stays true for every later area in the list, so those areas are destroyed without a prompt. The check that stops the caller from destroying the area they stand in runs only inside the unconfirmed branch (`762`). It is therefore skipped once any earlier area was confirmed, and skipped entirely when the caller passes `CONFIRMED` (`707-715`, `758-771`). Details are in Problems and cruft.

### Export (builder)

- **Subject and output.** The first word after `EXPORT` picks the target. `DATA` is the default subtype. ITEMS, MOBS, WEAPONS and ARMOR are valid subtypes for ROOM, AREA and WORLD only (`Export.java:234-252`). The output is screen text (`SCREEN`, newlines flattened, `66-74`), an email to the mailbox journal (`EMAIL`, `76-106`, which needs EMAILFORWARDING, a MAILBOX and the player's address), an XML string returned to the caller (`MEMORY`, `311-321` and `856-857`), or a file (`322-336`).
- **File names.** A name with no dot gets `.cmare` appended (`Export.java:111-112`). When the name is an existing directory, each object is written to its own file under a folder such as `/room`, `/classes`, `/extras` (`fileNameCode 2`, for example `Export.java:524-525`, `853-855`).
- **Generic content.** Exporting a CLASS or a RACE collects the generic objects it needs. For each generic race or class, it also collects every generic ability whose qualifying level is at least 0 (`Export.java:549-564`, `572-587`, using `CMLib.ableMapper().getQualifyingLevel`). The export therefore carries the abilities a class uses, not just the class.
- **REALESTATE.** `EXPORT REALESTATE <clan or player>` finds the rooms in the current area whose land title belongs to that owner (`136-160`). For each one it teleports the caller there (`168`, `189`) and runs `EXPORT ROOM` again (`171-182`). Without a file name the room ID becomes the file name. With one, every room writes the same file (see Problems and cruft).

### Deviations (balance report)

- **Scope.** `ROOM` fills from one room, `AREA` from the area's filled rooms, `METRO` from the area and its child areas (`Deviations.java:499-512`), `WORLD` from every filled room (`514-521`). Any other word names one mob or item in the room or inventory (`483`, `522-532`).
- **Copies only.** Items are copied, given no container or owner, stripped of their effects and unticked before they are measured (`326-331`, `357-363`, `382-389`). The world is never changed. Copies are destroyed afterwards (`338`, `366`, `597`).
- **Expected values.** For mobs the expected values come from `CMLib.leveler()` for level attack, damage, armor, speed and money range (`609-630`). For items they come from `CMLib.itemBuilder().timsItemAdjustments(...)` (`552-557`), and the power level from `timsLevelCalculator` (`558`). Mob power level comes from `CMLib.leveler().getPowerLevel` (`602`).
- **Formula.** `getDeviation(val, val2)` returns `round(|val2 - val| / val2 * 100)` (`Deviations.java:408-417`). The sign is `-` when the actual value is below the expected one and `+` when above. Example: expected 10, actual 8 gives `-20%`; actual 12 gives `+20%`. Mob armor is compared as `100 - armor` (`614-615`).
- **Colour bands.** The power-to-level ratio picks a colour (`419-436`): `^r` above 2.5 times level, `^p` above 2.0, `^b` above 1.5, `^g` above 1.2, `^w` above 0.8, and `^W` otherwise.
- **Output.** Items show name, ID, level, power, attack, damage, armor, value, rejuvenation and weight. Mobs show name, level, power, attack, damage, armor, speed, rejuvenation, money deviation and a count of worn items (`603-638`). `LOCATIONS` adds the room IDs where each object sits (`591-596`).
- **Sorting.** `SORTBY NAME, TYPE, LEVEL or PLEVEL` (`225-237`, `451-465`). `LOCATIONS` must come before `SORTBY` in the line. The reverse order silently drops the sort key (see Problems and cruft).

### Every (staff macro)

- **Options.** Options come before the command, in any order (`Every.java:107-184`): `<MASK>..</MASK>` is a zapper mask (`163-167`, compiled with `CMLib.masking().maskCompile`); `INAREA` widens to the area's metro map (`135-147`); `INWORLD` widens to every room (`149-161`); `+PLAYERS` includes player characters (`169-181`).
- **Permissions.** The target kind sets the right: ROOM and EXIT need CMDROOMS, MOB needs CMDMOBS, ITEM needs CMDITEMS (`88-105`). INAREA needs that right in the current room. INWORLD needs it everywhere, and also EVERY everywhere (`151`).
- **Loop.** For each room, the caller is moved there (`204-205`), then `doCommand` runs with `*` replaced by the object's context name (`209`, `217`, `228`, `240`). Monsters are included, and the caller is excluded (`224-227`). At the end the caller returns to the starting room (`247-248`).
- **Replacement.** `makeNewCommands` replaces the first `*` token only (`Every.java:55-62`). If the command has no `*`, the same text runs once per object with no object name (by reading).

### Email (in-game mail)

- **Mailbox.** Messages live in the journal named by the MAILBOX property. The query for a reader matches messages to the reader, to `ALL`, or to `MASK=` recipients (`Email.java:58-61`; the SQL OR-clauses are built in `core/database/JournalLoader.java:760-786`). A `MASK=` recipient is then tested with the zapper (`66-69`).
- **Listing and reading.** The default list is the newest 10 (`521`). `EMAIL BOX n-m` picks message n from the newest m (`528-537`). After reading, the prompt offers D, H or R (`211-212`). Reply is offered only to a player who exists and is not the caller (`245-251`). Delete calls `DBDeleteJournal` (`261`).
- **Composing.** The subject prompt aborts on empty input or on a `<` character (`384`). The body is taken through `CMLib.journals().makeMessageASync` (`395`), and the send goes through `CMLib.smtp().emailOrJournal` (`414`).
- **Quota and forwarding.** The mailbox is full at MAXMAILBOX messages (`333-341`). AUTOFORWARD and NOAUTOFORWARD decide whether the confirm prompt says the mail will also be forwarded (`288-295`, `106-112`).
- **Address change.** EMAILREQ selects the mode (`coffeemud.ini:1275`, default OPTIONAL). DISABLED skips the address (`434-439`). OPTION skips all validation (`459`). Any other mode requires at least 6 characters, an `@`, and an identical re-entry (`461-472`). PASS mode generates a random password, stores it, writes it to the mailbox for the player, pauses one second and ends the session (`450-454`, `479-495`).

### Equipment (worn-layer view)

- **Layers.** Worn items are sorted by `Armor.getClothingLayer()`, highest first (`Equipment.java:56-70`). Items sharing a layer are kept together only when both carry LAYERMASK_MULTIWEAR (value 2, `Items/interfaces/Armor.java:68`). Otherwise they go into a new set per wear location (`136-203`).
- **Visibility.** An item with LAYERMASK_SEETHROUGH (value 1, `Armor.java:61`) does not count as covering its slot (`213-216`).
- **Tattoos.** Tattoos on a wear location show in proportion to the uncovered slots: `ceil(total * emptySlots / locations)` (`293-305`, `318`). Back tattoos are hidden when the torso or about-body is covered (`297-299`).
- **View modes.** EQVIEW DEFAULT lists by location for everyone. MIXED lists by location for oneself and as a paragraph for others. PARAGRAPH is always a paragraph (`coffeemud.ini:552-558`; `core/CMProps.java:3031-3040`; `Equipment.java:81-83`, `385-391`). The paragraph joins items with commas and "and" (`366-375`).

### Dig (timed action)

- **Start.** At elapsed second 0, `preExecute` refuses if the character is busy or fighting (`Dig.java:94-105`). It then offers a `MSG_DIG` message with value 1 to the room (`107-115`). The tool is the wielded item, or the natural weapon if none (`108-110`).
- **Tick.** Every 8 seconds (`secondsElapsed % 8 == 0`), a fight stops the dig with "You stop digging." (`121-125`). Otherwise `MSG_DIG` carries the depth from `getDiggingDepth` (`126-135`).
- **Depth.** The table is in the Content section below. Shovels are recognised by the word "shovel" in the name (`58`, `64`).
- **Hole.** `StdRoom` creates a `HoleInTheGround` on the first dig if there is none (`StdRoom.java:962-965`). The hole's capacity grows by each message's value (`Items/Basic/HoleInTheGround.java:147`), and digging clears its hidden and not-seen flags (`140-145`). `Thief_BuriedTreasure.java:129-141` also reacts to the same dig message.
- **Room rule.** Digging is refused where the room has no extended ID ("You can't really dig here.") or where the domain is not an outdoor one (`StdRoom.java:815-840`). Underwater and water-surface rooms allow it only when there is no room below.
- **Queue.** The command queue calls `preExecute` once a second, with `secondsElapsed` starting at 0 (`MOBS/StdMOB.java:181`, `2190-2216`). A cancel passes -1 (`2326`). Dig returns true to keep going (`Dig.java:137`), and returns false when it stops (`84-93`). `DIG STOP` in the history also stops it (`78-83`).

### Duel (challenge handshake)

Each duel is two tattoos with a timer: `IDUEL` on the challenger, `UDUEL` on the challenged. Both last `DUELTICKDOWN` ticks, which default to 5, about 20 seconds (`Duel.java:134-136`; `coffeemud.ini:449`).

| State | Result |
|---|---|
| No tattoos on either | Challenge issued, both tattoos set, the room is told (`132-140`). |
| Target already has IDUEL | "awaiting a response to a previous challenge" (`173-176`). |
| Target has UDUEL | "considering a response" (`179-183`). |
| Caller already has either tattoo | "previous challenge has not yet expired", with the seconds left (`185-190`). |
| Both pairs match (accept) | Accept message. A held weapon is wielded if none is (`143-164`). The `Dueler` ability starts the fight, with `UNFAIR=` read from its misc text (`165-170`; `Abilities/Misc/Dueler.java:145-148`, `243-251`). |

A monster cannot duel (`124-125`). A challenged character who is not visible is still reachable through the tattoo pair (`106-119`).

### DieRoll (check ladder)

- **Dice.** `NdM` or `DM` uses `CMLib.dice().roll(num, die, 0)` (`DieRoll.java:85-112`). The room sees the result as "make(s) a N on a NdM roll" (`107`).
- **Checks.** A base stat is tested with `roll(1, 25, 0) < stat - diff.adj` (`133-134`). A saving throw is tested with `roll(1, 100, 0) < save - diff.adj * 10` (`139-140`). The dice helper was not read, so the exact range of `roll` is assumed to be 1 to the die size.
- **Ladder.** `RUDIMENTARY(-4)` through `IMPOSSIBLE(8)` (`49-70`). The default is NORMAL, adjustment 0 (`115`).

```python
def stat_check(stat, adj):        # DieRoll.java:133-134
    return random.randint(1, 25) < stat - adj

def save_check(save, adj):        # DieRoll.java:139-140
    return random.randint(1, 100) < save - adj * 10
```

### Emote (roleplay XP and targeting)

- **Social shortcut.** A leading `,` tries a social first, then a social found by a looser match. The social is used only if `meetsCriteriaToUse` is true (`Emote.java:79-97`).
- **Targets.** A `/name` token marks a target. The suffix picks a pronoun, for example `-h` for his or her and `-e` for he or she (`117-133`; `Libraries/interfaces/TelnetFilter.java:52-66`).
- **Roleplay XP.** After a successful emote, the award depends on who is present: PC, NPC or only oneself (`143-157`). `awardRPXP` enforces a delay between awards (`48-60`). The whole system is off by default (`coffeemud.ini:802`).

### Item handling: Draw, Dress, Empty, Display, Eat, DrinkCmd

- **Draw.** Trailing flags are read from the end: QUIETLY, IFNECESSARY and HELD (`Draw.java:86-109`). IFNECESSARY returns silently if the wield slot is already full, and with HELD, the held slot (`110-120`). A bare `DRAW` takes a weapon from a sheath if there is one, otherwise any weapon (`127-158`). Otherwise the last word names the container and the rest the weapon (`161-179`). Each weapon goes through `Get.get(..., "draw")` (`214`) and then a `MSG_HOLD` or `MSG_WIELD` (`218-233`).
- **Sheaths.** A sheath is a worn-free container with capacity, a restricted contain-type set, and a weapon inside (`getSheaths`, `48-73`).
- **Dress.** The command needs at least three words (`53-57`) and refuses in combat (`58-62`). The last word is the item and the rest the target (`63-66`). A target who will not accept orders is refused, unless bound, held, or an animal-intelligence rideable NPC (`89-96`). Staff with ORDER, or with CMDROOMS or CMDMOBS on a monster, put the item on at once (`98-107`). Otherwise the item is dropped, taken by the target and worn in steps (`131-175`).
- **Empty.** The container argument can be `HERE`, `FLOOR`, `GROUND`, `ME`, `SELF` or `INVENTORY` (`Empty.java:63-80`). Each item in a container is moved with `Get.get` (`215`) and then dropped or put into the target (`218-226`). Liquid is poured out, or passed to Pour when the target is a drink (`187-200`).
- **Display.** `SHOW item TO person` sends a `MSG_LOOK` and tells the recipient. Nothing transfers (`Display.java:124-129`).
- **Eat.** Partial food says "take(s) a bite of" rather than "eat(s)" when the value is between 0 and the food's nourishment (`Eat.java:116-132`). Food is refused when the character has hands and does not own it (`101-106`). Without hands, eating from the ground is a separate message (`113`).
- **DrinkCmd.** A drink from the room or a held source gives the liquid name in the message (`80-85`). A container holding a raw drink gives a sip from that drink (`87-101`).

### Movement and riding: Enter, Dismount, Disembark, Down, East, Exits

- **Enter.** Words after "enter" are tried as a direction, then as an item or exit (`Enter.java:61-67`). A Rideable is sat on through the Sit command (`70-80`). A dead body can be entered only by a mob with height and weight at zero or below (`82-93`). Anything else goes to the tracking walker (`95-102`).
- **Dismount and Disembark.** Both send `MSG_DISMOUNT` with the mount's `dismountString` (`Dismount.java:70`; `Disembark.java:68`). Dismount can also remove a named rider, which must be held, bound or willing (`76-99`).
- **Down and East.** Both call `Go`'s `standIfNecessary` and then walk or run (`Down.java:55-65`). The security check uses the direction type: `COMPASS`, `SHIP` or `CARAVAN` (`core/Directions.java:40-45`). Ship locations use names such as "Foreward" and "Starboard" (`core/Directions.java:122-135`).
- **Exits.** `SHORT` or `BRIEF` as the last word sets MASK_OPTIMIZE (`Exits.java:59`).

### Perception: Examine, Experience, Expertises

- **Examine.** `UNOBTRUSIVELY` as the last word suppresses the message (`Examine.java:55-59`). `EXIT` alone shows the exits (`72-80`). `SELF` or `ME` examines the caller (`81-82`). Otherwise the room is searched, then open containers in the room (`106-121`), then a direction (`122-137`). Examine in a room says "around carefully" (`162-163`). With AUTOEXITS on, the exits follow (`155-156`).
- **Experience.** The text gives XP, hours online (`Experience.java:53`), XP to the next level, and "You will not gain further levels" when the cap is reached (`58-65`). An account-expiry line is added when that feature is on (`45-46`).
- **Expertises.** Each expertise is looked up in the expertise library. An unknown one is shown in a `?(name)` form (`Expertises.java:60-74`), so saved entries stay visible after their definition is removed.

### Expire and DumpFile (staff)

- **Expire.** Reads the remaining time, or sets days (`days * 1000 * 60 * 60 * 24`, `120`), or `NEVER` (sets the NOEXPIRE flag, `137-156`), or `NOW` (days 0, `112`). It is stored either on the account or on the player's security flags (`41-59`). It needs CMDPLAYERS (`185-188`).
- **DumpFile.** Reads one or more files and prints the text to each online session whose character is named (or all) and whose location allows the DUMPFILE right (`DumpFile.java:87-117`). Files that were not already cached are removed from the resource cache afterwards (`87-99`).

## Content worth mining

**Difficulty ladder** (`DieRoll.java:49-70`, adjustment added to the target):

| Level | Adj | Level | Adj | Level | Adj |
|---|---|---|---|---|---|
| Rudimentary | -4 | Hard | +1 | Ardous | +4 |
| Trivial | -3 | Difficult | +2 | Miraculous | +5 |
| Simple | -2 | Strenuous | +3 | Improbable | +6 |
| Easy | -1 | Normal | 0 | Incalculable | +7 |
| | | | | Impossible | +8 |

**Digging depth by material** (`Dig.java:53-68`, weight is the item's weight):

| Material | Shovel | Other tool |
|---|---|---|
| Metal, mithril, wood | 5 + weight | 1 + weight / 5 |
| Synthetic, rock, glass | 14 + weight | 1 + weight / 7 |
| Anything else (for example a natural weapon) | 1 | 1 |

**Destruction messages** (`Destroy.java:1337-1344`): "<S-NAME> pour(s) out <T-NAME>." for liquid, "tear(s) up" for paper and cloth, "smash(es)" for glass.

**Staff flavour lines** (short samples, all emitted to the room): "The sound of massive destruction rings in your ears." (`Destroy.java:422`); "A wall of inhibition falls @x1." (`477`); "The diversity of the world just changed?!" (`812`); "The superstition of the players just decreased!" (`90`).

**Emote pronoun suffixes** (`Libraries/interfaces/TelnetFilter.java:52-66`): `-h` his/her, `-m` him/her, `-s` self, `-e` he/she, `` `s `` your, plus `-hs` and `-ms` for the reflexive forms.

**Expertise and equipment words** come from the libraries these commands call (`Libraries/ColumbiaUniv.java`, `Wearable.CODES`), not read here.

## Notable design ideas

- **Materials decide destruction.** A player breaks an item because of what it is made of, not because of a flag on the item. Breaking cloth leaves strips that can be picked up (`Destroy.java:1362-1388`). The same rule covers everything, so a new material needs no new code.
- **Only data can be deleted.** Staff may remove the generic (data-built) races, classes, commands and abilities, but never the compiled Java ones (`Destroy.java:797`, `835`, `1005`, `1043`). This is a clean boundary between content and engine.
- **One predicate language.** Zapper masks (`<MASK>` in Every, `MASK=` recipients in Email) use the same compiler and matcher (`Every.java:112-130`; `Email.java:68`). That makes mail targeting and bulk admin share a rule language.
- **Analysis on copies.** Deviations copies every item and mob-inventory item before measuring, then discards the copies (`Deviations.java:326-331`). The report can run on a live world.
- **Flags from the end.** `DRAW` reads QUIETLY, IFNECESSARY and HELD from the end of the line (`Draw.java:86-109`). A script can say "draw only if your hands are free" with one word.
- **Fixed ladder for checks.** DieRoll uses one integer adjustment for all checks, scaled by 10 for saves (`DieRoll.java:134`, `140`). It is simple to read and tune.
- **Layers, not slots.** Equipment sorts garments by a numeric layer and two bits, multiwear and see-through (`Equipment.java:56-70`, `136-216`). Clothing can be modelled with three fields.
- **Timed actions as queued ticks.** Dig is a command that keeps running, one call per second, until it is stopped or interrupted (`Dig.java:94-137`; `StdMOB.java:2190-2216`). The hole grows on each tick (`HoleInTheGround.java:147`).
- **Unknown entries stay visible.** Expertises shows unknown entries as `?(name)` (`Expertises.java:66-73`), so bad or removed data is seen, not dropped.

## Problems and cruft

Each item below comes from reading the code. None was run.

1. **`DESTROY AREA ALL` can destroy every area at once** (`Destroy.java:718-777`). With five or more words and `ALL` as the first word, the list becomes every area in the world. `EXCEPT` is the only word that removes areas from the list (`725-738`), and any other word is ignored. The first "Y" to the prompt confirms every later area in the list, with no prompt and no guard for the area the caller stands in (`758-771`). Adding `CONFIRMED` removes the prompt and the guard together. `DESTROY AREA ALL EXCEPT <name> CONFIRMED` therefore destroys every other area in one command. Destroy would need an explicit list, a per-area prompt, and the guard on every area.
2. **UNLINK's room-ID exemption is defeated** (`Destroy.java:360`, `382` against `UnLink.java:52-55`). `UnLink` rewrites the first word to `DESTROY`, so the test for `UNLINK` never matches. Room IDs are looked up for `UNLINK <name>` too. The room-destroy prompt (`416-419`) is the only guard beyond CMDROOMS. The `UNLINK` usage line (`363-367`) is never shown.
3. **Email list skips entries after a removal** (`Email.java:62-73`). The loop increments `num` after `msgs.remove(num)`, so the entry that moves into the removed slot is never tested. A masked message that the reader does not match can stay in the list when it directly follows another rejected one. Only the sender and subject show, not the body.
4. **NOPURGE with no name** (`Destroy.java:1934`). `commands.get(2)` is read without a size check, so a bare `DESTROY NOPURGE` throws `IndexOutOfBoundsException`. How the caller handles that is not traced.
5. **Every without `*` repeats silently** (`Every.java:55-62`, `209-240`). The command runs once per object with no object name.
6. **Realestate export overwrites** (`Export.java:136-193`, `113`, `305`). When the caller gives a file name or directory, every room writes the same file, so only the last survives.
7. **Movement combat cost uses the wrong default** (`Go.java:270-276`). `combatActionsCost` reads `DEFCMDTIME`, not `DEFCOMCMDTIME`. `Enter`, `Down` and `East` therefore cost 0.20 in combat, while the verbs that use the default combat cost pay 1.0. This looks unintended.
8. **Dead colour branch in Expertises** (`Expertises.java:57-59`, `77-78`). Both lookups call the same method, so `baseDef != def` is false and the " *" marker never appears. The commented-out line at 58 looks like the intended "confirmed" lookup.
9. **Examine ignores the cost override** (`Examine.java:183`). It returns a flat 1.0, unlike every sibling, so the configuration cannot tune it.
10. **ACCOUNT directory suffix** (`Export.java:716`). The subtype path uses `/player`, while the data path at line 491 uses `/account`.
11. **Dead branches** (`Dismount.java:57-61`, `Disembark.java:57-61`). The `L`-word branch appears unreachable: the only caller found, `GenGangline.java:222` and `256`, passes the word `Dismount`. The `Leave` command, which does start with L, does not call these.
12. **Dead branch in Experience** (`Experience.java:80-84`). Under normal dispatch the command word is always present, so the empty-list branch that puts the text into the list instead of printing it does not run.
13. **Duplicate calls.** `Destroy.java:1598-1599` and `Empty.java:233-234` each call `recoverRoomStats` twice.
14. **Blocking pause** (`Email.java:493`). A one-second sleep inside a command holds the thread before the session stops.
15. **Dress syntax.** The help text reads "DRESS whom in what" (`Dress.java:55`). The parser takes the last word as the item and everything before it as the target name (`63-66`), so a typed "in" becomes part of the name ("BOB IN"). The help and the parser disagree.
16. **Deviations sort key order.** `SORTBY` is recognised only as the last two words (`Deviations.java:452-465`), and `LOCATIONS` only as the last word (`466-472`). In "SORTBY X LOCATIONS" the sort pair is not at the end, so the sort silently falls back to the default TYPE. "LOCATIONS SORTBY X" is the order that works.
17. **DumpFile dereference** (`DumpFile.java:107-109`). `S.mob()` is dereferenced without a null check. Whether `localOnlineIterable` can return sessions without a character was not verified.

**Multiplayer-only assumptions.** Duel, Dress (consent through `willFollowOrdersOf`), Display (showing to another player), Email, Every's `+PLAYERS` flag, DumpFile (broadcast to all online users), Export's PLAYER and ACCOUNT subtypes, Expire's account layer, Experience's account line, and Destroy's USER, ACCOUNT, SESSION, THREAD, WEBSERVER, SMTP and intermud subcommands.

## Takeaways for a Python SUD

**Borrow:**

- **The check ladder.** A `Difficulty` enum with the thirteen adjustments above, and two check functions for stats and saves (sketch in the DieRoll section). Use it for every skill roll, so tuning is one table.
- **Timed actions as ticks.** A `TimedAction` object with `tick(elapsed)` and `cancel()`, starting at 0. Dig's pattern is the model: a start tick, a repeating tick every N seconds that adds progress, and a cancel path. The hole's progress is stored on the object, not in the room.
- **Material rules as data.** A table that maps each material to the outcome for breaking it, with a default of "cannot be broken". Cloth leaves strips, glass shatters, liquid spills.
- **Garment layers.** `Garment(name, layer: int, multiwear: bool, seethrough: bool)`, and a worn list per slot. Sort by layer for display. Show a slot's tattoos or marks in proportion to the uncovered share.
- **A balance report.** A function that takes a level and returns expected stats, then compares each entity with the expected figure as a signed percentage. It should copy objects before measuring, so it never changes the world.
- **Export as data.** The XML export becomes `to_dict()` and JSON for saves. Keep the "return it as a string" mode for tests and for a debugging dump.
- **Expertise display.** Keep unknown or removed entries visible with a marker.

**Simplify:**

- The admin hub. Replace `Destroy` and its fifty subcommands with a small set of world-editing functions for development, not a command surface.
- The command surface. A single-player game needs one command per verb with a clear argument parser. The suffix-flag reader (QUIETLY, IFNECESSARY, HELD) is a good pattern to keep in a small form.
- Item selection. `item.2` and `ALL.` are useful for multi-item play; a plain index covers most needs.

**Drop:**

- Duel, Dress consent and Display to others: no other players.
- Email, its password flow and forwarding: no mail between characters.
- Every with `+PLAYERS`, and the roleplay XP award in Emote.
- Expire, DumpFile, the MQL delete, and the account and session machinery.
- The movement security checks by direction type, unless the game has ships.

## Coverage

**Read in full** (excluding the licence header and import block, which were skipped): all 28 bucket files. Lines read: `Destroy.java` 1-2466; `Export.java` 33-873; `Deviations.java` 36-700; `Email.java` 36-560; `Equipment.java` 36-435; `Draw.java` 36-272; `Every.java` 36-264; `Drop.java` 37-258; `Empty.java` 35-256; `Duel.java` 35-213; `Dress.java` 35-197; `Examine.java` 35-191; `Expire.java` 35-190; `Emote.java` 35-184; `DieRoll.java` 35-179; `Dig.java` 35-176; `Eat.java` 35-157; `Display.java` 35-156; `Expertises.java` 36-148; `DumpFile.java` 35-135; `DrinkCmd.java` 35-128; `Dismount.java` 35-121; `Enter.java` 35-112; `Experience.java` 27-100; `Disembark.java` 35-94; `Down.java`, `East.java`, `Exits.java` (complete bodies).

**Read in part, outside the bucket, to explain interactions:** `Commands/StdCommand.java` (cost, argument, ordering and security defaults, lines 95-110, 205-240, 265-270); `Commands/Go.java` (258-283); `Commands/Get.java` (signatures and cost, 57-62, 241-250); `Commands/Leave.java` (49-70); `Commands/UnLink.java` (48-71); `Commands/interfaces/Command.java` (96-101, 167); `core/CMProps.java` (1140-1200, 3031-3040); `coffeemud.ini` (530-566, 774-804, 446-449, 1268-1280); `MOBS/StdMOB.java` (2190-2235, 2326); `Locales/StdRoom.java` (815-840, 940-985); `Items/Basic/HoleInTheGround.java` (130-160); `Abilities/Misc/Dueler.java` (grep, 145-148, 243-251); `Abilities/Prayers/Prayer_Bury.java` (grep); `Libraries/CMMap.java` (grep, `obliterateMapArea`); `core/database/JournalLoader.java` (479-500, 760-800); `core/collections/FullConvertingList.java` (grep, list `remove`); `Libraries/ColumbiaUniv.java` (grep, `getDefinition`); `core/CMSecurity.java` (grep, the group definitions); `core/Directions.java` (40-50, 122-135); `Libraries/interfaces/TelnetFilter.java` (52-66); `Items/interfaces/Armor.java` (61, 68).

**Skimmed or not read:** the helper libraries that the commands call (`CMLib.map()`, `CMLib.database()` write methods, `CMLib.journals()`, `CMLib.smtp()`, `CMLib.dice()`, `CMLib.masking()`, `CMLib.leveler()`, `CMLib.itemBuilder()`, `CMLib.percolator()`, `CMLib.coffeeMaker()`), the `Wearable` wear codes, and the `Sit`, `Pour` and `Get` internals. Their behaviour is named where it matters but not verified.
