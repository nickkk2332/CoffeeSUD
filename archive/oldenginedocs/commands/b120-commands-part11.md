# Commands, part 11: Shout to Time

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b120` (Commands [part 11/12: Shout .. Time]) · 30 files · 7238 lines of Java.

## Summary

This bucket is the base class `StdCommand` plus 29 command classes, from SHOUT through TIME. Most classes are thin: they take the token list, check a permission, then post a `CMMsg` into the room or call a library. The large ones are `Stat` (2074 lines), an inspection command that also prints a per-item breakdown of attack, armour, damage, speed and hit points, and `Skills`, the shared parser and lister behind SKILLS, SPELLS and SONGS. The mechanics worth knowing are the action-cost model (0.20 actions out of combat and 1.0 in combat by default, with any command that does not override its cost paying nothing unless configured), SLEEP UNTIL as a tick-driven effect, a SHUTDOWN countdown that announces at each half-way point, and a plain-text subscription set for journals and channels. The admin and multi-user parts (SNOOP, SWITCH, TELL, SUBSCRIBE, SHUTDOWN, TICKTOCK, TEMPLATE, TEST) mostly do not fit a single-player game. The cost model, STAT's breakdown, SLEEP UNTIL, THROW and SPLIT are worth borrowing.

## Key files and types

All paths are relative to the CoffeeMud folder. In the running text, `Commands/...`, `Common/...`, `Libraries/...` and `MOBS/...` mean `com/planet_ink/coffee_mud/...`. Lines are the file's total length.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Commands/Stat.java` | STAT (extends Skills): player self-stats, admin inspection of mobs, items, rooms, areas, clans; statistics-table reports; combat breakdown | 2074 |
| `com/planet_ink/coffee_mud/Commands/Skills.java` | SKILLS base class (extends StdCommand): shared parser for domain, type and level range; ability lister | 589 |
| `com/planet_ink/coffee_mud/Commands/Template.java` | TEMPLATE [builder]: list, add, delete and toggle sharing of builder template objects | 357 |
| `com/planet_ink/coffee_mud/Commands/StdCommand.java` | Base class of every command: ID, access words, cost model, `executeInternal`, security hook | 316 |
| `com/planet_ink/coffee_mud/Commands/Shutdown.java` | SHUTDOWN [admin]: immediate, timed or scheduled shutdown with a tickable countdown | 297 |
| `com/planet_ink/coffee_mud/Commands/Subscribe.java` | SUBSCRIBE: notification subscriptions to journals and channels | 279 |
| `com/planet_ink/coffee_mud/Commands/Test.java` | TEST [sysop]: runs the CMTest suites in `Tests/` | 272 |
| `com/planet_ink/coffee_mud/Commands/Tell.java` | TELL / T: private messages, TELL LAST, intermud tells | 261 |
| `com/planet_ink/coffee_mud/Commands/Switch.java` | SWITCH [account or sysop]: move between characters or to another host | 233 |
| `com/planet_ink/coffee_mud/Commands/Sleep.java` | SLEEP / SL, SLEEP UNTIL (a tickable auto-waker effect) | 221 |
| `com/planet_ink/coffee_mud/Commands/Take.java` | TAKE: privileged take-from-mob; otherwise hands off to TAKE OFF (Remove) or GET | 216 |
| `com/planet_ink/coffee_mud/Commands/TickTock.java` | TICKTOCK [admin]: advance the game clock or tick a subsystem | 212 |
| `com/planet_ink/coffee_mud/Commands/Throw.java` | THROW / TOSS: throw a held item at a target or in a direction | 191 |
| `com/planet_ink/coffee_mud/Commands/Split.java` | SPLIT: share money with the group, or take N of an item | 149 |
| `com/planet_ink/coffee_mud/Commands/Snoop.java` | SNOOP [admin]: watch another player's session | 144 |
| `com/planet_ink/coffee_mud/Commands/Teach.java` | TEACH: teach a skill or expertise to a visible student | 144 |
| `com/planet_ink/coffee_mud/Commands/Sniff.java` | SNIFF / SMELL: sniff the room, yourself or an object | 131 |
| `com/planet_ink/coffee_mud/Commands/Sounds.java` | SOUNDS / MSP: negotiate MSP (telnet sound) | 124 |
| `com/planet_ink/coffee_mud/Commands/Sit.java` | SIT / REST / R | 120 |
| `com/planet_ink/coffee_mud/Commands/Stand.java` | STAND / ST / STA / STAN | 109 |
| `com/planet_ink/coffee_mud/Commands/Time.java` | TIME / DATE: area time and the next birthday | 94 |
| `com/planet_ink/coffee_mud/Commands/Starboard.java` | STARBOARD / STB: ship-only move east (NE or SE with north or south) | 92 |
| `com/planet_ink/coffee_mud/Commands/Shout.java` | SHOUT / SHOUTTO / SHOUTAT (extends Say) | 91 |
| `com/planet_ink/coffee_mud/Commands/SysMsgs.java` | SYSMSGS: toggles the SYSOPMSGS attribute | 80 |
| `com/planet_ink/coffee_mud/Commands/Southeast.java` | SOUTHEAST / SE move (extends Go) | 78 |
| `com/planet_ink/coffee_mud/Commands/Southwest.java` | SOUTHWEST / SW move (extends Go) | 78 |
| `com/planet_ink/coffee_mud/Commands/South.java` | SOUTH / S move (extends Go) | 76 |
| `com/planet_ink/coffee_mud/Commands/Spells.java` | SPELLS / SP: spell list (extends Skills) | 74 |
| `com/planet_ink/coffee_mud/Commands/Songs.java` | SONGS: song list (extends Skills) | 73 |
| `com/planet_ink/coffee_mud/Commands/SocialsCmd.java` | SOCIALS: prints the full socials table | 63 |

Related classes outside this bucket that the code leans on: `Commands/Say.java` (the speech engine behind SHOUT), `Commands/Go.java` (the movement base of the direction commands), `Libraries/CMJournals.java` (journal notices), `Libraries/CoffeeTables.java` and `Common/DefaultCoffeeTableRow.java` (statistics tables), `Common/DefaultTimeClock.java` (calendar), `core/CMProps.java` (command cost settings).

## How it works

### 1. The base class and the cost model

- Each command's ID is its class name, taken in the constructor (`com/planet_ink/coffee_mud/Commands/StdCommand.java:40-48`). Access words are passed through `I()` (`StdCommand.java:78-83`), which applies command-word translation.
- `execute(mob, commands, metaFlags)` receives the raw tokens as a mutable `List<String>` and edits it in place. Most commands start with `commands.remove(0)` and then peel trailing words off (`Commands/Throw.java:61-64`, `Commands/Take.java:62-80`).
- `executeInternal` (`StdCommand.java:107-122`) builds such a list from typed arguments. Other code uses it to run a command programmatically; `Tell.executeInternal` (`Commands/Tell.java:213-244`) also answers "how many tells matched" queries without sending anything.
- Cost. `StdCommand.actionsCost` returns `CMProps.getCommandActionCost(ID(), 0.0)` (`StdCommand.java:223-227`), so the default is zero unless `coffeemud.ini` lists an exception for that command. The one-argument accessor used by many subclasses defaults to `DEFCMDTIME` divided by 100 (`core/CMProps.java:1183-1186`). The shipped setting is `DEFCMDTIME=0.20` and `DEFCOMCMDTIME=1.0` (`coffeemud.ini:539-546`). `checkedActionsCost` chooses the combat figure when the mob is in combat, or when its ship is (`StdCommand.java:241-262`).
- Consequence: commands that override the cost (SHOUT, SIT, STAND, SLEEP, TAKE, THROW, SPLIT, TEACH, SNIFF, SWITCH) pay about 0.20 actions out of combat. Commands that do not override it (STAT, SKILLS, SPELLS, SONGS, TIME, SUBSCRIBE, TEMPLATE, TEST, TICKTOCK, SNOOP, SHUTDOWN, SOUNDS, SYSMSGS, SOCIALS) pay nothing out of combat unless `coffeemud.ini` lists them as exceptions. TELL overrides only the combat figure (`Commands/Tell.java:249-253`), and its comment says why: players should use SAY, and pay for it, when coordinating in a fight.
- Ordering. `canBeOrdered()` defaults to true (`StdCommand.java:264-268`). The overrides that return false here are TELL (`Tell.java:255-259`), SWITCH (`Switch.java:211-214`), SUBSCRIBE (`Subscribe.java:274-277`), SHUTDOWN (`Shutdown.java:194-197`), TEMPLATE (`Template.java:344-347`) and TEST (`Test.java:262-265`).
- Security. `securityCheck(mob)` defaults to true (`StdCommand.java:270-274`) and decides whether the command appears for a given mob. Overrides in this bucket: TEMPLATE needs any of CMDITEMS, CMDROOMS, CMDEXITS or CMDMOBS (`Template.java:350-356`); SHUTDOWN needs the SHUTDOWN flag (`Shutdown.java:200-203`); SNOOP needs SNOOP (`Snoop.java:139-142`); TICKTOCK needs TICKTOCK (`TickTock.java:207-210`); SYSMSGS needs SYSMSGS or the attribute itself (`SysMsgs.java:76-79`); SWITCH needs the account system or sysop (`Switch.java:229-232`); TEST needs sysop (`Test.java:268-271`); SOUNDS is refused when the MSP feature is disabled by `DisFlag.MSP` (`Sounds.java:120-123`). Flags are defined in `core/CMSecurity.java:2341` (`SecFlag`).

### 2. Command catalogue

Cost is written as out-of-combat / in-combat, in actions. "Default" means the `coffeemud.ini` values above. Tags: player, admin (SecFlag), sysop (`isASysOp`), builder (room, item, mob or exit flags), account (account system).

| Command (access words) | Syntax | What it does | Cost | Tag and restrictions |
|---|---|---|---|---|
| SHOUT, SHOUTTO, SHOUTAT (`Shout.java`) | `shout <text>`, `shout <dir> <text>`, `shoutto <who> <text>`, `shoutat <who> <text>` | Yells in the room and in adjacent rooms with open exits | default / default | player; orderable |
| TELL, T (`Tell.java`) | `tell <who> <text>`, `tell last <n>` | Private message to an online character, or to `name@mud` over intermud | 0 / 1.0 | player; not orderable; blocked while your own QUIET attribute is set |
| STAT (`Stat.java`) | `stat`, `stat <code>`, `stat <name>`, `stat item/room/area/exit/clan <x>`, `stat <n> days/weeks/months/years [category]`, `stat <ability-type or report>` | Shows stats and reports | 0 / 0 | player for own stats; admin for others, rooms, areas and reports; orderable |
| SKILLS, SK (`Skills.java`) | `skills [domain] [lo-hi]`, `skills <skill>` | Lists thief, skill and common-skill abilities with proficiency | 0 / 0 | player |
| SPELLS, SP (`Spells.java`) | `spells [domain] [level]`, `spells <spell>` | Spell list | 0 / 0 | player |
| SONGS (`Songs.java`) | `songs [domain] [level]`, `songs <song>` | Song list | 0 / 0 | player |
| SIT, REST, R (`Sit.java`) | `sit [on <thing>]` | Sits, or sits on furniture or an exit; `sit <mob>` hands off to MOUNT | default / default | player |
| STAND, ST, STA, STAN (`Stand.java`) | `stand [ifnecessary] [quietly]` | Stands up | default / default | player |
| SLEEP, SL (`Sleep.java`) | `sleep [on <thing>]`, `sleep until day/night/dawn/dusk/healed/full/rested` | Sleeps; the UNTIL form adds an auto-waker | default / default | player |
| TAKE (`Take.java`) | `take <n> <item> from <mob>`, `take off <item>`, `take <item>` | From-mob form takes items off a non-player; OFF hands to REMOVE; plain form hands to GET | default / default | the from-mob form is builder or admin (ORDER, CMDMOBS or CMDROOMS); quest-point form `take n qp from <mob>` |
| THROW, TOSS (`Throw.java`) | `throw <item> <direction or target>` | Throws a held or wielded item | default / default | player |
| SPLIT (`Split.java`) | `split <amount> <coins>`, `split <item> <n>` | Divides money among eligible group members, or runs GET n FROM item | default / default | player |
| TEACH (`Teach.java`) | `teach <who> <skill>` | Teaches a skill or expertise through `ExpertiseLibrary` | default / default | player; the teaching rules are in the core-verbs chapter |
| SNIFF, SMELL (`Sniff.java`) | `sniff [self/me/<thing>] [unobtrusively]` | Sniffs the room, yourself or an object | default / 0.25 (hard-coded) | player |
| TIME, DATE (`Time.java`) | `time [global]` | Describes the local or global time, plus the next birthday | 0 / 0 | player |
| SUBSCRIBE, SUBSCRIPTIONS (`Subscribe.java`) | `subscribe [list]`, `subscribe add <name>`, `subscribe remove <name>` | Notification subscriptions for journals and channels | 0 / 0 | player; not orderable |
| SOUNDS, MSP (`Sounds.java`) | `sounds [force]`, `sounds off` | Negotiates MSP sound with the client | 0 / 0 | player; refused when `DisFlag.MSP` is set |
| SOCIALS (`SocialsCmd.java`) | `socials` | Prints the socials table | 0 / 0 | player |
| STARBOARD, STB (`Starboard.java`) | `starboard [north/south]` | Ship movement east, or northeast or southeast | from Go | ship interiors only |
| SOUTH, S / SOUTHEAST, SE / SOUTHWEST, SW (`South.java`, `Southeast.java`, `Southwest.java`) | `south`, `southeast`, `southwest` | Compass movement | from Go | compass rooms only; SE and SW also need more than six directions |
| SYSMSGS (`SysMsgs.java`) | `sysmsgs [on/off]` | Toggles extended messages for the staff | 0 / 0 | sysop or the SYSMSGS flag |
| SNOOP (`Snoop.java`) | `snoop <name>`, `snoop` (with no name, stops current snoops) | Watches another session | 0 / 0 | admin; not for sysops unless you are an archon |
| SWITCH (`Switch.java`) | `switch <character>`, `switch override <character>`, `switch <port>` | Moves between characters on one account, or to another local host | default / default | account system or sysop; 5-minute lockout after a PK fight, not for sysops |
| SHUTDOWN (`Shutdown.java`) | `shutdown [noprompt] [for <reason>]`, `shutdown in <n> <unit>`, `shutdown at <time>`, `shutdown cancel`, `shutdown check` | Shuts the game down now or on a countdown | 0 / 0 | admin; not orderable |
| TICKTOCK (`TickTock.java`) | `ticktock <n>`, `ticktock <n> <period>`, `ticktock clantick/smtp/propertytax`, `ticktock set <date>`, `ticktock <library or thread>` | Advances the game clock or ticks a subsystem | 0 / 0 | admin (TICKTOCK) |
| TEMPLATE (`Template.java`) | `template list [mobs/items/exits/rooms] [mask]`, `template add <id> <thing>`, `template delete <id>`, `template toggle <id>` | Stores and shares builder templates | 0 / 0 | builder; not orderable |
| TEST (`Test.java`) | `test list [category]`, `test <category or id> [args]` | Runs CMTest suites | 0 / 0 | sysop; not orderable |

### 3. STAT

#### 3.1 Data model and parsing

- Ability-type constants run from `ABLETYPE_EQUIPMENT = -2` to `ABLETYPE_OBJECTS = -16` (`com/planet_ink/coffee_mud/Commands/Stat.java:52-66`). Each has an alias row in `ABLETYPE_DESCS` (`Stat.java:68-84`), for example `{"INVENTORY","INVEN","INV"}`, `{"QUESTWINS","QUESTS","QUEST","QUESTWIN"}` and `{"AFFECTS","EFFECTS","EFF"}`.
- `getPrivilegedStat` matches the first token against those aliases and stores `-2 - i` for alias row `i` (`Stat.java:895-902`). A name that is not a report falls through to the ability-type names in `Ability.ACODE.DESCS` (`Stat.java:909-918`), and those go to `getAbilities` (`Stat.java:933`).
- Non-admins. `STAT` with no arguments prints the option list (`Stat.java:1404-1447`). `STAT <code>` reports only the caller's own stats. When `FULLSTATS` is disabled and a list file is set, the codes are restricted to that list (`Stat.java:1399-1403`).
- Admins (SecFlag STAT). `STAT` alone shows today's table (`Stat.java:1453-1458`). `STAT <n> DAY/WEEK/MONTH/YEAR [category]` runs the statistics report with scales 1, 7, 30 and 365 days (`Stat.java:1466-1489`). `STAT ROOM`, `STAT AREA`, `STAT ITEM`, `STAT EXIT` and `STAT CLAN` target objects (`Stat.java:1502-1561` and following). Several branches open the builder editor through `CMLib.genEd()` with the magic argument `-950` (`Stat.java:1504`, `1556`, `1590`, `1641`); the meaning of -950 is not traced.
- `executeInternal` (`Stat.java:2052-2061`) runs `STAT <name> <mob>` with the auth check overridden, and returns the text, so other code can fetch a stat block.

#### 3.2 Control flow of `execute`

1. Help: `STAT ?` (`Stat.java:1404-1449`).
2. Admin-only reports, when the caller has the STAT flag in this room: `TODAY`, `SKILLUSE`, `SOCUSE`, `CMDUSE`, `QUEST`, `CRIME`, `AREA` and `N DAYS` forms (`Stat.java:1451-1492`). Otherwise the privileged ability-type path runs (`Stat.java:1491`).
3. Targets, admin only: ROOM, AREA, CLAN, EXIT, ITEM and ROOM/AREA by name, then a fall-through to mob, item, exit, room, then area (`Stat.java:1494-1691`). Quest-in-use notices are printed for any object (`Stat.java:1548-1554`, `1633-1640`).
4. Stat tokens, one at a time (`Stat.java:1693-2014`). The words are first normalised: `MAX` and `BASE` are merged into the next token (`Stat.java:1693-1708`), and spaces are removed (`1709-1710`). The lookup order for each token is: special words (`MAXHUNGER`, `XP`, `ATTRIBUTES`, `RESISTS`, `HEALTH`, `TRAINS`, `PRACTICES`, `ATTACK`, `ARMOR`, `DAMAGE`, `QUESTPOINTS`, `XPTNL`, `XPFNL`, `XPSTATS`, `STINK`; `Stat.java:1724-1845`), exact current-state codes, `MAX<code>` where charstats add `BASEMAXSTAT` to the value (`Stat.java:1846-1857`, `1878-1880`), `BASE<code>` with the race effect applied (`Stat.java:1858-1871`), charstats, physstats (SENSES and DISPOSITION are rendered as lists, `Stat.java:1893-1897`), factions (`Stat.java:1907-1918`), generator stats through `CMLib.coffeeMaker().isAnyGenStat` (`Stat.java:1921-1926`), and finally prefix matches (`Stat.java:1928-2011`). A token that matches nothing prints `*UNKNOWN:<token>*` (`Stat.java:2012-2013`).
5. Output is sent with `wraplessPrintln` to the session (`Stat.java:2016-2017`).

#### 3.3 Combat breakdown (`STAT <mob> COMBAT`)

This is the most distinctive mechanic in the bucket (`Stat.java:1141-1374`). The procedure is:

- Copy the target's base charstats, physstats and state into a fresh `StdMOB` (`Stat.java:1206-1209`), then print the base attack, armour, damage, speed and hit points.
- For each source, reset the scratch mob (`recoverMOB`, `Stat.java:2021-2027`), apply the source with `testMOB` (`Stat.java:2029-2034`, which calls `affectCharStats`, `affectPhyStats` and `affectCharState`), and print the difference. Items not in inventory are sources (`Stat.java:1214-1225`), and so are the target's effects (`Stat.java:1227-1238`).
- Each difference line is `padRight(name,40) + ": ^W+" + diff` (`Stat.java:2036-2043`). The "Other Stuff" line is the live total minus the base, which captures anything not attributed to an item or effect (`Stat.java:1241`, `1274`, `1307`).
- The totals come from the live combat library: `adjustedAttackBonus` (`Stat.java:1243`), `adjustedArmor` (`Stat.java:1276`) and `adjustedDamage` (`Stat.java:1309`). Speed is `Math.round(speed*100)` (`Stat.java:1311-1341`), and hit points come from the max state (`Stat.java:1343-1372`).

Side effect: the target itself is recovered at 1210, 1239, 1272, 1305, 1338 and 1373. `recoverMOB` ends in `resetToMaxState()` (`Stat.java:2021-2027`), which copies max state into current state (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:1039-1043`). So running COMBAT on a mob in play also restores its current hit points, mana and movement to the maximum.

#### 3.4 Statistics tables

- `showTableStats` (`Stat.java:106-727`) reads raw rows from `CMLib.coffeeTables()` (`Stat.java:114-115`), which is `StatisticsLibrary`, implemented in `com/planet_ink/coffee_mud/Libraries/CoffeeTables.java`. It then buckets rows into windows of `scale` days, walking back from today (`Stat.java:262-283` and the loops after it).
- Each bump increments one slot of a per-key array (`com/planet_ink/coffee_mud/Common/DefaultCoffeeTableRow.java:149-165`). Keys are prefixed by kind: `X` plus area name (`DefaultCoffeeTableRow.java:249`), `A` plus ability ID (`:272`), `S` plus social base name (`:278`), `M` plus command ID (`:281`), and others for quests, classes, races and clans. `totalUp` sums every key with a given prefix (`DefaultCoffeeTableRow.java:166-180`).
- Slot indices (`com/planet_ink/coffee_mud/Common/interfaces/CoffeeTableRow.java:160-198`): LOGINS 0, TICKSONLINE 1, NEWPLAYERS 2, LEVELSGAINED 3, DEATHS 4, PKDEATHS 5, MARRIAGES 6, BIRTHS 7, DIVORCES 8, CLASSCHANGE 9, PURGES 10, SKILLUSE 11, SOCUSE 12, CMDUSE 13, WARRANTS 14, ARRESTS 15, PAROLES 16, JAILINGS 17, EXECUTIONS 18, TOTAL 19.
- Default daily columns are CONs, HIGH, ONLN, AVGM, NEWB, DTHs, PKDs, CLAS, PURG and MARR (`Stat.java:243-253`). AREA mode prints one row per area (`Stat.java:600-659`), and TICKSONLINE is converted to minutes per day (`Stat.java:586`, `645`, `709`).
- The SKILLUSE, SOCUSE and CMDUSE tables list every ability, social or command with a non-zero count (`Stat.java:265-333`, `336-402`, `403-470`). The social table has a bug (Problems, item 2).

### 4. SKILLS, SPELLS and SONGS (`Skills.java`)

- `Skills.execute` (`Skills.java:554-581`) prints thief skills, skills and common skills for SKILLS, `Ability.ACODE_SPELL` for SPELLS and `Ability.ACODE_SONG` for SONGS (`Spells.java:49-66`, `Songs.java:49-65`). A single name argument is resolved first with `parsedOutIndividualSkill` (`Skills.java:65-157`).
- Proficiency is the ability's `proficiency()` plus `PROF+` adjustments keyed by ID, ACODE name, domain name and `PROF+*` (`Skills.java:126-130`), capped at 100 (`Skills.java:131-132`). It is shown as a number, or as words from `CMLib.help().getRPProficiencyStr` when the combat-prowess setting asks for words (`Skills.java:133-138`).
- Listing rules. Only abilities at or below the caller's level are shown (`lowestLevel = level + 1`, `Skills.java:442`, `457`). They are laid out in two columns (`MAX_COLS = 2`, `Skills.java:465`), and the footer reads "Use QUALIFY to see additional skills you can GAIN." (`Skills.java:549`). Non-savable abilities are dimmed unless the viewer is a sysop (`Skills.java:140-149`, `500-510`).
- Domain and level arguments. A domain is matched as a prefix of a `Ability.DOMAIN` name and stored shifted left by five bits (`Skills.java:266-279`). A level argument may be `N` or `lo-hi`, and an open upper bound becomes `LASTPLAYERLEVEL` (`Skills.java:180-221`).

### 5. SHOUT and the Say engine

- `Shout` extends `Say` (`Commands/Shout.java:35`). The access word decides the mode. A word ending in TO sets SHOUTTO, and one ending in AT sets SHOUTAT (`Shout.java:61-68`). The call is `exec(mob, toFlag, true, ...)` (`Shout.java:70`), and the third argument, `true`, turns on multi-room delivery.
- Every token is upper-cased (`Shout.java:58-59`), and the text is later built from the same list (`Say.java:322`). Shouted text is therefore delivered in capitals. The code does not comment on this.
- Multi-room delivery. `Say.exec` collects each room that has an open exit (`Say.java:129-139`). If the first word is a compass direction (`getGoodCompassDirectionCode`, `Say.java:230`), it yells in that direction and one ring further (`Say.java:234-251`).
- Messages. The room sees `<S-NAME> shout(s) '...'` (`Say.java:334-335` chooses the verb, and the message is built after it). Adjacent rooms see `You hear someone shout '...' <direction>.` (`Say.java:379`).
- Errors. A bare SHOUT gets "Shout what?" (`Say.java:125`). A SHOUTTO or SHOUTAT whose target is not visible gets "you don't see X here to speak to." (`Say.java:261`).
- Cost. Shout overrides both costs with the defaults (`Shout.java:73-83`).

### 6. TELL

- A player with QUIET set cannot send (`Tell.java:61-63`). Fewer than two words after TELL gets "Tell whom what?" (`Tell.java:67-71`).
- TELL LAST n replays from `PlayerStats.getTellStack()`, with each entry's age rounded to whole seconds (`Tell.java:74-115`). Empty history gives "No telling." (`Tell.java:80-83`).
- Target lookup tries online characters by name, then the characters of the target's account (`Tell.java:117-142`).
- Delivery. Spaces in the message are quoted (`Tell.java:143-148`), the SAYFILTER is applied (`Tell.java:155`), and players get the secondary input filter (`Tell.java:156-159`). An offline target with `@mud` goes to `CMLib.intermud().imudTell` when an intermud link is up (`Tell.java:160-171`). A target in QUIET mode is refused, with a different message if they are cloaked (`Tell.java:176-183`). The message is sent through `CMLib.commands().postSay` (`Tell.java:190`). An AFK target gets their AFK message back (`Tell.java:198-207`).

### 7. SLEEP and SLEEP UNTIL (`Sleep.java`)

- `WaitUntil` is an enum of DAY, NIGHT, DAWN, DUSK, HEALED, FULL and RESTED (`Sleep.java:51-54`). The two words after UNTIL are matched by name first, then by the prefixes DAYTIME and NIGHTTIME (`Sleep.java:70-89`). The help-style message lists DAY, NIGHT, DAWN, DUSK, HEALED, RESTED and FULL (`Sleep.java:86`).
- The UNTIL form first calls `delAbility` on whatever "AutoWaker" it finds in the ability list (`Sleep.java:90`), which is not where the waker lives (see Problems item 17). It then creates an `ExtendableAbility` with the ID "AutoWaker", stores the condition name as its text, makes it non-savable, sets `Sleep` itself as its tickable, and adds it as a non-uninvokable effect (`Sleep.java:91-98`). Problems item 17 describes a defect in this path.
- `tick` (`Sleep.java:153-220`). If the mob is no longer asleep the effect is removed (`Sleep.java:180-183`). DAWN, DAY, DUSK and NIGHT compare `clock.getTODCode()` (`Sleep.java:187-200`). FULL needs hit points, mana and movement all at maximum (`Sleep.java:201-205`). HEALED needs hit points at maximum (`Sleep.java:206-208`). RESTED needs fatigue at or below zero (`Sleep.java:209-211`). On the wake condition the mob enqueues `WAKE` (`Sleep.java:213-218`).
- Messages: "<S-NAME> lay(s) down and take(s) a nap." (`Sleep.java:102`); "You are already asleep!" (`Sleep.java:63`).

### 8. SHUTDOWN (`Shutdown.java`)

- Options are read from the end of the argument list backwards (`Shutdown.java:89-165`). FOR keeps its own word in the sentence: `reason = " " + combine(commands, i)` (`Shutdown.java:97-102`), so the text reads "will be shutting down in 5 minutes for maintenance" (`Shutdown.java:64`).
- NOPROMPT skips the confirmation (`Shutdown.java:104-108`). CANCEL and STOP clear the schedule and the tick (`Shutdown.java:110-123`). CHECK does nothing (`Shutdown.java:92-95`).
- IN n unit takes the unit from `CMLib.english().getMillisMultiplierByName` (`Shutdown.java:125-145`). AT time takes a wall-clock time through `CMLib.time().string2TimeFuture` (`Shutdown.java:147-164`).
- Timing. The end time is now plus n times the unit, minus one millisecond (`Shutdown.java:141`). The first announcement is due at half the time (`Shutdown.java:142`, `161`). Each later announcement is also due at half of what remains (`Shutdown.java:247`), so the warnings bunch toward the end.
- The countdown is registered on the area tick group (`TICKID_AREA`) with period argument 1 (`Shutdown.java:184`). When the time passes, `startShutdown` starts a thread that calls `MUD.globalShutdown` (`Shutdown.java:205-219`).

### 9. Item and money commands

#### TAKE (`Take.java`)

- The from-mob form needs ORDER, CMDMOBS or CMDROOMS on the room (`Take.java:53-56`). The victim must be visible (`Take.java:69-74`), and a player can be taken from only if ORDER is allowed everywhere (`Take.java:75-79`).
- Quest points are taken with `TAKE n QP FROM` (`Take.java:104-124`). Items are unworn and removed from their container (`Take.java:147-151`), and a MSG_GIVE is sent: "<T-NAME> take(s) <O-NAME> from <S-NAMESELF>." (`Take.java:162`).
- Without the privilege, `TAKE OFF` hands to REMOVE (`Take.java:173-179`) and anything else hands to GET (`Take.java:180-193`).

#### THROW (`Throw.java`)

- In combat, a one-argument THROW targets the victim (`Throw.java:54-55`). The item must be held or wielded (`Throw.java:65-77`).
- Direction throws need an open exit, unless the thrower is floating freely (`Throw.java:79-99`). A sideways throw between two outdoor rooms is refused with "That's too far to throw X." unless the destination is open air or the throw is up or down (`Throw.java:100-110`).
- A weapon thrown at a mob is an attack (`MSG_WEAPONATTACK`, `Throw.java:124-128`). So is a spell-holder whose spell is malicious (`Throw.java:129-141`).
- Messages: "<S-NAME> throw(s) <O-NAME> at <T-NAMESELF>." (`Throw.java:143`); "<S-NAME> throw(s) <O-NAME> <direction>." and "<O-NAME> fl(ys) in from <direction>." for the room and the room beyond (`Throw.java:154-155`).

#### SPLIT (`Split.java`)

- Money path (`Split.java:58-107`). The amount and currency are parsed (`Split.java:58-62`). Eligible recipients are the group members other than the payer who are players (not monsters) and in the same room, found through `getGroupMembers` (`Split.java:65-78`; `com/planet_ink/coffee_mud/MOBS/StdMOB.java:4888-4904`). Each share is the total divided by eligible count plus one (`Split.java:85-86`), so the payer keeps one share. The total is converted to coins (`Split.java:92`) and a MSG_GIVE goes to each recipient (`Split.java:94-107`).
- The removal inside the for-each is safe, because `SHashSet.remove` replaces the backing set and does not touch the iterator (`com/planet_ink/coffee_mud/core/collections/SHashSet.java:283-300`).
- Item path. `SPLIT <item> <n>` is rewritten as `GET n FROM "item"` (`Split.java:110-122`).

#### TEACH (`Teach.java`)

- The student must be visible in the room (`Teach.java:64-70`). The skill is looked up first among the mob's own abilities, then among expertises by exact then partial name (`Teach.java:73-119`). Teaching itself is `CMLib.expertises().confirmAndTeach` (`Teach.java:119`, `123`); the rules are covered in the core-verbs chapter.

### 10. SUBSCRIBE and journal notices

- Storage. Subscriptions are a `Set<String>` returned by `PlayerStats.getSubscriptions()` (`Subscribe.java:58`). When the character is on an account, the account's set is returned instead (`com/planet_ink/coffee_mud/Common/DefaultPlayerStats.java:636-640`), so subscriptions follow the account.
- Format. Entries are `" <tag> :<NAME>"`. The tags seen in the code are P (journal, in-game notice), C (channel) and E (journal with e-mail as well). The listing takes the name from entries whose colon sits at index 3 (`Subscribe.java:70-72`). ADD writes `" P :NAME"` for a journal (`Subscribe.java:223`) and `" C :NAME"` for a channel (`Subscribe.java:234`).
- Lookup. ADD searches forum journals, command journals and channels by exact name, then by substring, and refuses a journal the caller cannot read (`Subscribe.java:97-214`).
- Readers. `CMJournals.notifyPosting` tells each online subscriber "<journal> Notification: <author> just added a new message." (`com/planet_ink/coffee_mud/Libraries/CMJournals.java:1556-1572`). `notifyReplying` does the same for a reply to one's own post (`CMJournals.java:1580-1596`). The MOTD lists P and C subscriptions (`com/planet_ink/coffee_mud/Commands/MOTD.java:333-337`, `382`).

### 11. TEMPLATE, TEST, SNOOP, SWITCH, SOUNDS, SYSMSGS

- TEMPLATE. Listing groups entries by a one-character prefix: a space for private, `+` for your shared and `*` for others' shared (`Template.java:115-148`, headings at `168-174`). ADD resolves `here` or `room`, a direction, a room ID, or an object or mob in the room (`Template.java:299-310`), and stores the copy through `CMLib.catalog().addNewBuilderTemplateObject` (`Template.java:316`). The catalog is `Libraries/interfaces/CatalogLibrary.java`.
- TEST. `getMapped` groups CMTest classes by their group tags (`Test.java:71-85`). `TEST <category or id>` runs each test's `doTest` between two `cleanupTest` calls and prints "Passed" or the failure (`Test.java:233-250`). There are about 85 test classes in `com/planet_ink/coffee_mud/Tests/`, with group tags such as all, core, all_properties, all_zappers, all_masks, info_all and spaceall (counts not tallied).
- SNOOP. Any SNOOP first stops every snoop this session holds (`Snoop.java:66-78`). A target must be in the game (`Snoop.java:102-103`). Only archons may snoop sysops (`Snoop.java:105-106`). The code walks the existing snoop chain to refuse loops (`Snoop.java:109-125`).
- SWITCH. Four paths: another local host by public port (`Switch.java:93-139`); a character on the same account, or any character for a sysop using OVERRIDE (`Switch.java:140-166`); a live swap of sessions when the target is in the game (`Switch.java:167-185`); and logout and login as the target (`Switch.java:186-206`). Lockouts: 5 minutes after a PK fight, not for sysops (`Switch.java:78-85`), and the LOGOUTMASK zapper (`Switch.java:86-92`).
- SOUNDS. Telnet MSP negotiation: up to five 500 ms waits for the client (`Sounds.java:79-88`). FORCE sets the telnet modes whether or not the client confirmed (`Sounds.java:95-101`). OFF hands to NoSounds (`Sounds.java:58-61`).
- SYSMSGS. Toggles the SYSOPMSGS attribute (`SysMsgs.java:53-65`). Readers include the librarian's message filter (`com/planet_ink/coffee_mud/MOBS/StdLibrarian.java:535`), the deity's (`com/planet_ink/coffee_mud/MOBS/StdDeity.java:497`) and the possession notice (`com/planet_ink/coffee_mud/Commands/Possess.java:199`).

### 12. TICKTOCK (`TickTock.java`)

- A bare number advances the area clock by that many hours (`TickTock.java:67-75`). Number plus unit bumps a `TimePeriod` (`TickTock.java:77-86`). CLANTICK ticks all clans (`TickTock.java:92-97`), SMTP runs the SMTP tick through the host command (`TickTock.java:99-103`), PROPERTYTAX runs each area's property-tax tick (`TickTock.java:141-157`).
- SET takes `Y#/M#/D#/H#` or `M#/D#/Y# H#h` (`TickTock.java:104-139`; the help text is at `TickTock.java:128-130`).
- Anything else is matched first to a library service client name, then to a tick thread (`TickTock.java:158-187`). The fallback message is "Ticktock what?  Enter a number of mud-hours, months, days, years, or clanticks, or thread id." (`TickTock.java:188`). Exceptions print "Ticktock failed: ..." (`TickTock.java:192-195`).

### 13. Smaller commands

- SIT. "You are already sitting!" (`Sit.java:56`); "<S-NAME> sit(s) down and take(s) a rest." (`Sit.java:66`); `sit <mob>` is passed to MOUNT (`Sit.java:83-88`).
- STAND. Flags IFNECESSARY and QUIETLY (`Stand.java:52-60`). "You may not stand up." when the session is stopped (`Stand.java:68-69`). A flying sleeper gets "<S-NAME> wake(s) up." instead of standing (`Stand.java:77-78`).
- SNIFF. UNOBTRUSIVELY suppresses the room message (`Sniff.java:53-60`). "sniff e..." is refused as "Sniff what?", so that the direction word E is not taken as an object (`Sniff.java:99-103`). Anything other than a room is sniffed with hands (`Sniff.java:79-87`). The combat cost is 0.25 regardless of configuration (`Sniff.java:122`).
- TIME. The area description, then the global one with GLOBAL (`Time.java:55-58`). If the character has a birthday, the next one is computed (`Time.java:59-84`), see Problems item 14.
- SOCIALS prints `CMLib.socials().getSocialsBaseTable()` (`SocialsCmd.java:52-53`).
- SPELLS, SONGS. See section 4.
- The four direction commands extend `Go` and call `standIfNecessary`, then `CMLib.tracking().run` when AUTORUN is set or `walk` otherwise (`South.java:52-57`). Starboard turns east into northeast or southeast when given north or south (`Starboard.java:52-61`).

## Content worth mining

These are the concrete tables and strings a text game could reuse.

**STAT report names** (`Stat.java:68-84`, aliases in brackets):

| Report | Aliases |
|---|---|
| EQUIPMENT | EQ, EQUIP |
| INVENTORY | INVEN, INV |
| QUESTWINS | QUESTS, QUEST, QUESTWIN |
| TATTOOS | TATTOO, TATT |
| COMBAT | none |
| SCRIPTS | none |
| TITLES | TITLE |
| ROOMSEXPLORED | none |
| AREASEXPLORED | none |
| WORLDEXPLORED | none |
| FACTIONS | FACTION |
| CHARSTATISTICS | CSTAT, CHARSTATS |
| LEVELTIMES | LVLS |
| AFFECTS | EFFECTS, EFF |
| POBJECTS | none |

**SLEEP UNTIL conditions** (`Sleep.java:51-54`, tests at `Sleep.java:187-211`):

| Word | Wakes when |
|---|---|
| DAWN, DAY, DUSK, NIGHT | the local time-of-day code matches |
| HEALED | hit points are at max |
| FULL | hit points, mana and movement are all at max |
| RESTED | fatigue is at or below zero |

**Subscription tags** (`Subscribe.java`, `Libraries/CMJournals.java`): `P` journal notice, `C` channel, `E` journal with e-mail (read but never written in this repository).

**Builder template groups** (`Template.java:168-174`): "Personal Private template objects", "Personal Shared template objects", "Other folks shared template objects".

**Short messages** (all quoted from source):
- "<S-NAME> lay(s) down and take(s) a nap." (`Sleep.java:102`)
- "<S-NAME> sit(s) down and take(s) a rest." (`Sit.java:66`)
- "<S-NAME> wake(s) up." and "<S-NAME> stand(s) up." (`Stand.java:78`, `80`)
- "<S-NAME> sniff(s) around." (`Sniff.java:61`, `106`)
- "<S-NAME> throw(s) <O-NAME> at <T-NAMESELF>." (`Throw.java:143`)
- "You hear someone shout '...' <direction>." (`Say.java:379`)
- "That's too far to throw X." (`Throw.java:108`)
- "No telling." (`Tell.java:83`)
- "<mud name> will be shutting down" followed by " in <time>" (or " now") and the reason text (`Shutdown.java:56-65`).
- "@x1 Notification: @x2 just added a new message." (`Libraries/CMJournals.java`)
- "Use QUALIFY to see additional skills you can GAIN." (`Skills.java:549`)

**Statistics columns** (`Stat.java:243-253`): CONs, HIGH, ONLN, AVGM, NEWB, DTHs, PKDs, CLAS, PURG, MARR.

**Throw and spell messages**: a weapon thrown at a mob is treated as an attack (`Throw.java:124-128`), which is a ready-made rule for improvised weapons.

## Notable design ideas

- **A cost table with defaults and exceptions.** One default cost, one combat default, and a list of exceptions in the config file (`coffeemud.ini:539-546`, `core/CMProps.java:1143-1186`). Commands that do not opt in are free. This keeps the tuning in data and makes every command's cost visible in one place.
- **Breakdown by source.** STAT COMBAT recomputes a derived number on a scratch copy once per source and prints each source's delta (`Stat.java:1206-1238`). The total and the parts always come from the same functions, so the report explains the game rather than a parallel model.
- **Tickable "until" effects.** SLEEP UNTIL is an effect that runs once per tick, checks a named condition, and removes itself. It needs no timers or callbacks (`Sleep.java:91-98`, `153-220`).
- **Halving countdown.** SHUTDOWN announces when half the remaining time has passed, then again at half of what is left (`Shutdown.java:142`, `247`). That gives a readable countdown with no per-second scheduling.
- **Keyword-as-sentence.** FOR keeps its keyword so the announcement reads naturally (`Shutdown.java:97-102`).
- **Upper-case shouting.** Shouts are delivered in capitals by normalising the tokens (`Shout.java:58-59`), a cheap way to give a command a distinct voice.
- **Multi-room yell with direction.** Adjacent rooms with open exits hear a shout, and a direction word aims it one ring further (`Say.java:129-139`, `230-251`). Hearing is described by the direction the sound came from (`Say.java:379`).
- **Disambiguation guard.** "sniff e" is refused because E is also the east direction (`Sniff.java:99-103`).
- **Cycle check on chains.** SNOOP walks the snoop chain before adding a link (`Snoop.java:109-125`).
- **Tagged strings in a set.** Subscriptions are a plain set of strings with a one-letter tag (`Subscribe.java:70-72`, `223`, `234`), which is easy to persist and to query.
- **Movement by room type.** The same direction command is allowed only in rooms of a given movement space: compass rooms for SOUTH, and ship interiors for STARBOARD (`South.java:68-75`, `Starboard.java:78-85`).

## Problems and cruft

Each item was found by reading. Items marked "by inspection" were not run.

1. **STAT EXIT is inverted.** When no item matches, the exit is looked up, then the command returns true without showing anything (`Stat.java:1533-1538`). When an item does match, the reply is "You can't stat exit". The EXIT branch never opens the editor; an exit is reached only by the untagged default path (`Stat.java:1630-1641`).
2. **Every other social is dropped.** The first social of each pair is tested with `STAT_SKILLUSE` (`Stat.java:377`), but its value and the social bumps use `STAT_SOCUSE` (12). Social keys only fill the SOCUSE slot (`DefaultCoffeeTableRow.java:278`, `149-165`), so the first test is always false and that social is skipped. The second social of each pair is tested with SOCUSE (`Stat.java:389`, `393`) and prints correctly.
3. **Armour residual uses the wrong base.** The "Other Stuff" line in the armour section subtracts `basePhyStats().attackAdjustment()` (`Stat.java:1274`), where the armour base (`Stat.java:1245`) would use `armor()`. The line is wrong by inspection.
4. **STAT COMBAT changes the target.** The target is recovered six times, and each recovery ends in `resetToMaxState()` (`Stat.java:1210`, `1239`, `1272`, `1305`, `1338`, `1373`; `StdMOB.java:1039-1043`). Running the report on a fighting mob refills its current state.
5. **Debug output in STAT DAMAGE.** `M.tell(""+M.phyStats().damage())` sends a bare number to the viewer before the formatted line (`Stat.java:1791`).
6. **Unreachable MAXHUNGER branch.** The second MAXHUNGER test (`Stat.java:1824-1828`) sits in the same chain as the first (`Stat.java:1724-1728`), so it can never run.
7. **BASE and race effects disagree.** The exact `BASE<code>` path applies the race effect (`Stat.java:1864-1865`). The prefix path has that line commented out (`Stat.java:1958`).
8. **Dead code.** `averageDamage` runs 5000 simulated damage rolls and has no callers in the repository (`Stat.java:729-738`).
9. **Possible exception on empty lists.** AREASEXPLORED and ROOMSEXPLORED call `substring(0, length-2)` on a string that may be empty when nothing matched (`Stat.java:1119`, `1135`). By inspection this throws.
10. **Cosmetic label damage.** For EFFECTS and TATTOOS with nothing to list, the trailing `deleteCharAt` removes the colon from the label (`Stat.java:1003-1006`, `1026-1029`).
11. **Hard-coded epoch.** Leveling records older than 1529122205 (about mid-2018) are silently skipped (`Stat.java:1055`). The reason is not commented.
12. **Magic number.** `-950` is passed to the editor in many places (`Stat.java:1388`, `1504`, `1556`, `1590`, `1606`, `1641`, `1650`, `1679`). The meaning is not traced.
13. **SHOUTAT doubles the verb.** `tverb = averb + L("shout(s) at")` (`Shout.java:66`) with `averb = "shout(s)"` (`Shout.java:54`) gives "shout(s)shout(s) at", with no space. The message is delivered with that verb (`Say.java:334`).
14. **Birthday weekday is wrong.** `Time.java:73-75` computes the day index with `(bmonth-1) * daysInMonth`, then adds `bmonth` where the day of month belongs. Compare `DefaultTimeClock.java:257-258` and `334-335`, which use `getMonth() * daysInMonth + getDayOfMonth()`. The stored value is `clock.getMonth()` (`com/planet_ink/coffee_mud/Common/DefaultPlayerStats.java:997-999`), which `DefaultTimeClock` uses unshifted, so the `-1` is also inconsistent. `Common/DefaultScriptingEngine.java:7248-7250` repeats the same formula, and at `7253` it indexes month names with `bmonth-1`, where `Time.java:79-80` indexes with the stored value. The two disagree.
15. **Birthday fields swapped in one place.** `Abilities/Spells/Spell_Youth.java:118-119` reads `BIRTHDEX_MONTH` into `bday` and `BIRTHDEX_DAY` into `bmonth`. Elsewhere the order is day at index 0 and month at index 1 (`Common/interfaces/PlayerStats.java:1285-1287`, `Commands/Time.java:65-66`, `Libraries/CommonMsgs.java:975-976`). This file is outside this bucket.
16. **Type name uses a stray shift.** `Skills.parseTypeInfo` sets `typeName` from `type[0] >> 5` (`Skills.java:377-378`), but `type[0]` was never shifted. The string is only tested for emptiness (`Abilities.java:62`, `68`), so the bug is latent.
17. **Second SLEEP UNTIL can be dropped.** `Sleep.java:90` looks for "AutoWaker" in the ability list, but the waker lives in the effect list, so the removal does nothing. `addNonUninvokableEffect` returns early if an effect with that ID exists (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:5241-5242`). A second SLEEP UNTIL issued before the first waker is removed (on the tick after waking) is silently ignored. Removal happens only on that tick (`Sleep.java:180-183`).
18. **Throw with AT.** "THROW SWORD AT GOBLIN" leaves "AT" in the item name, because `what` is built from everything but the last word (`Throw.java:61-64`). The help text documents only `THROW [ITEM NAME] [DIRECTION/MOB NAME]` (`resources/help/help.ini:2759-2766`). Item lookup on "SWORD AT" is unverified, but the natural phrasing probably fails.
19. **Success message on the failure path.** The quest-point take reports success through `postCommandFail` (`Take.java:121`). The effect on the client is unverified.
20. **Unused subscription branch.** E entries are read and removed (`Subscribe.java:79`, `218`, `253`, `260`) but nothing in the repository writes one (literal search: only `Subscribe.java`).
21. **Typo in player-facing text.** "subcribe" appears in two messages (`Subscribe.java:112`, `119`).
22. **Hard-coded combat cost.** SNIFF returns 0.25 for combat cost (`Sniff.java:122`), which bypasses the configured `DEFCOMCMDTIME` and its exceptions.
23. **Repeated classes.** SOUTH, SOUTHEAST, SOUTHWEST and STARBOARD differ only in direction, guard and access words. They could be one table-driven class.
24. **Empty-list exceptions in TEST.** `getCategoriesList` and `getTestsList` call `substring(0, length-2)` and throw on an empty list (`Test.java:94`, `106`, `187`). This is minor, since the test list is never empty in practice.

## Takeaways for a Python SUD

**Borrow**

- **Cost table with defaults.** Keep the two defaults (out of combat and in combat) in one place, a dict of exceptions, and a per-command flag for whether it pays the default at all:

  ```python
  DEFAULT_COST = {"out": 0.20, "combat": 1.00}   # coffeemud.ini DEFCMDTIME / DEFCOMCMDTIME
  EXCEPTIONS = {("sniff", "combat"): 0.25}       # only the exceptions are listed
  def action_cost(name, in_combat, pays_default=True):
      phase = "combat" if in_combat else "out"
      if (name, phase) in EXCEPTIONS:
          return EXCEPTIONS[(name, phase)]
      return DEFAULT_COST[phase] if pays_default else 0.0
  ```

  Whether these are actions or turns depends on the undecided real-time or turn-based question. The point is the data shape.
- **Explain-by-source for derived numbers.** A `stat` or `inspect` command can recompute an attribute with each source removed, which gives the same breakdown STAT COMBAT prints:

  ```python
  def explain(player, fn):
      total = fn(player)
      return {src.name: total - fn(player, without=src) for src in player.sources()}
  ```

  This is useful for players and for balance debugging, and it needs no second model.
- **Wait-until conditions as an enum.** Use an enum of waits (`DAWN`, `DAY`, `DUSK`, `NIGHT`, `HEALED`, `FULL`, `RESTED`) and one predicate per member, checked on each tick. A single `rest_until` action can then set the wait and be cleared when it is met.
- **Halving warnings for timed events.** For a storm or a curfew that is N ticks away, warn when half the remaining time has passed, then half again. It is a cheap, readable schedule.
- **Neighbour broadcast.** A shout is a message to the current room plus the open neighbours, with the direction it came from. An NPC in a neighbouring room can hear it and react.
- **Money split including the payer.** Divide by the number of recipients plus one, so the payer keeps a share (`Split.java:85-86`).
- **Throw into an adjacent room.** Use an open exit as the gate and a "flies in from" line for arrivals.
- **Keyword-as-sentence.** A reason keyword can stay in the text it introduces, so `shutdown for maintenance` reads as one sentence.

**Simplify**

- Fold the four direction commands into one table of directions with a movement-space check.
- Replace the four STAT report paths and the coffee tables with one player sheet and one `inspect` command.
- Drop the statistics tables entirely. Nothing in a single-player game needs daily counters for each command.
- The TICKTOCK options worth keeping are "advance the clock" and "tick now", as a developer command. Drop SMTP, clan and property-tax ticks.

**Drop**

- TELL, SNOOP, SWITCH, SUBSCRIBE (except as notifications for in-game notices), SHUTDOWN (replace with save and quit), SOUNDS (MSP), SYSMSGS, TEST (use the host's test runner), TEMPLATE (unless you want data-file prototypes, in which case load them from JSON or YAML).

**Rethink for single-player**

- Several rules assume other people: "Take from a mob" needs a privilege (`Take.java:53-56`); SPLIT assumes a follower group (`Split.java:65-78`); TELL needs a second person; SNOOP needs a second session; SWITCH needs a second character on the account. A single-player game needs ownership and companions instead of privileges and sessions.
- SLEEP UNTIL keeps working as a timed rest, but the "healed" and "full" conditions need the vitals to exist as data.

## Coverage

- **Read in full (body):** all 30 files. `Commands/Stat.java` (1-2074), `Commands/Skills.java`, `Commands/Shutdown.java`, `Commands/Tell.java`, `Commands/Shout.java`, `Commands/Sleep.java`, `Commands/Sit.java`, `Commands/Stand.java`, `Commands/Take.java`, `Commands/TickTock.java`, `Commands/Throw.java`, `Commands/StdCommand.java` were read from line 1. The other 18 were read from their first code line, after the license and import block.
- **Skimmed:** none.
- **Skipped:** none within this bucket. The license and import blocks at the top of each file were not read in detail.
- **Outside this bucket, read in part:** `Commands/Say.java` (lines 117-400, the `exec` path behind SHOUT), `core/CMProps.java` (1143-1197), `coffeemud.ini` (539-546), `resources/help/help.ini` (2759-2766), `Common/DefaultCoffeeTableRow.java` (149-181, 272-281), `Common/interfaces/CoffeeTableRow.java` (160-198), `Common/DefaultTimeClock.java` (257-258, 334-339), `Common/DefaultScriptingEngine.java` (7236-7256), `MOBS/StdMOB.java` (1039-1043, 4888-4904, 4989-4993, 5083, 5237-5247), `core/collections/SHashSet.java` (277-300), `Libraries/CMJournals.java` (1556-1596), `Libraries/CoffeeTables.java` (62), `Abilities/Spells/Spell_Youth.java` (116-120), `Common/interfaces/PlayerStats.java` (1285-1293), `Common/DefaultPlayerStats.java` (636-640, 997-999), `Libraries/CommonMsgs.java` (975-976), `Libraries/EnglishParser.java` (415-424), `Commands/MOTD.java` (333-337, 382), `Commands/Abilities.java` (61-68), `Commands/Possess.java` (199), `MOBS/StdLibrarian.java` (535), `MOBS/StdDeity.java` (497), `core/CMSecurity.java` (2341).
- **Not traced:** the -950 mode argument to the genEd editor; the statistics library's own write path; the content of `Ability.ACODE.DESCS`; the English matcher's behaviour on "X AT Y" (described from the code path only).
