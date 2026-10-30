# Commands, part 1: Command to Buy

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b110` (Commands [part 1/12: Command .. Buy]) · 48 files · 7581 lines of Java.

## Summary

This bucket is the first twelfth of the typed verbs, in alphabetical order from the `Command` interface through `Buy`: 47 command classes plus the interface that defines them. Most verbs are short. They parse the typed words, check a flag, and either print a status view or switch one of about twenty per-character settings on or off (the `Auto*` family, `ANSI`, `BRIEF`, `AFK`). Four parts are distinctive: the live auction (a single global, bid-driven, timed sale with escrow and a house cut), the staff verbs `AS` (take over another character's session) and `AT` (run a command in another room), the delayed and background verbs `AFTER` and `ASYNC`, and the status views `AFFECT` and `ACHIEVEMENTS`. Staff verbs are gated by `CMSecurity` flags. For a single-player SUD, the settings table, the effect and status listings, the alias model and a tick-based scheduler carry over; the possession, broadcast, multi-account and live-auction parts assume many human players and would be dropped or reshaped.

## Key files and types

Conventions. Java paths omit the leading `com/planet_ink/coffee_mud/`, so `Commands/Auction.java` means `com/planet_ink/coffee_mud/Commands/Auction.java`. The "Lines" column is the file's total line count. Action costs are the `StdCommand` defaults unless stated (see How it works, 2). "Staff" marks a verb gated by a `CMSecurity` flag or meaningful only to builders and admins; the flag is named. "Not orderable" means `canBeOrdered()` returns false, so a charmed or grouped follower cannot be told to run it.

### Contract (this bucket) and base class (b170)

| File or class | Role | Lines |
|---|---|---|
| `Commands/interfaces/Command.java` | Contract: access words (`:46`), action costs (`:68-94`), `canBeOrdered` (`:101`), `securityCheck` (`:108`), `execute` (`:132`), `preExecute` (`:154`), `executeInternal` (`:179`). Covered in depth in b170. | 181 |
| `Commands/StdCommand.java` (not in bucket; b170) | Base class: ID from class name (`:40-48`), default costs (`:223-233`), default `canBeOrdered` and `securityCheck` true (`:264-274`), `L()` translation (`:73-76`). | 316 |

### Player information

| Verb (access words) | File | Lines | Syntax | What it does; cost and restrictions |
|---|---|---|---|---|
| ACHIEVEMENTS | `Commands/Achievements.java` | 590 | `ACHIEVEMENTS [NOW/WON/ALL] [AWARDS] [ANNOUNCE] [PLAYER/ACCOUNT/CLAN] [who]`; `ACHIEVEMENTS REMORT/RETIRE/FUTURE` | Lists achievements won, in progress, or all, for self, another player, the account or clans. The REMORT, RETIRE and FUTURE forms preview awards without granting them (`:211-212`, `:243-271`). Pauses 4 s after an evaluation (`:152-158`). Another player's record with the PRIVACY attribute needs staff rights (CMDPLAYERS or ACHIEVEMENTS, `:170-178`). Cost 0. |
| AFFECT (AFFECTS, AFF, AF) | `Commands/Affect.java` | 379 | `AFFECT [NAME/TYPE/DOMAIN/REMAIN] [type or domain] [here/area/name]` | Your posture, needs and active effects, optionally sorted (`:262-353`). Staff with CMDMOBS can view a room, an area or any object (`:313-340`). Base class of `Score` (b170). Cost 0. |
| AUTOAFFECTS (AUTOAFF, AAF) | `Commands/AutoAffects.java` | 167 | `AUTOAFFECTS [name]` | Lists passive auto-invoked effects on you, or on a room object for staff (CMDMOBS, `:85`). Not orderable (`:162`). |
| ABILITIES (ABILITYS, ABLES) | `Commands/Abilities.java` | 103 | `ABILITIES [type or domain] [level]` | Your known abilities grouped by type (`:61-93`). Extends `Skills` (not in this bucket), whose player-visible type list is `Commands/Skills.java:46-55`. Cost 0. |
| ACCOUNT | `Commands/Account.java` | 312 | `ACCOUNT [name] [sort fields]` | Characters on your account, with name, race, level and class, plus `*` for online, `(tells)`, `(email)` and `(post)` markers and total hours played (`:54-119`, `:193-202`). Needs the account system (`:308-311`). Staff with CMDPLAYERS may view any account or a character's account (`:213-243`). Cost 0. |
| AREAS | `Commands/Areas.java` | 309 | `AREAS [SORT=NAME/REV/LEVEL/AUTHOR/<stat>] [EXPLORED] [filter]` | Areas you can access, with level, author and optional explored percentage, filtered by an expression over area stats (`:253-274`). Sysops see areas coloured by state (`:235-251`). EXPLORED needs ROOMVISITS enabled (`:101`). Cost 0. |
| ATOPICS (ARCTOPICS) | `Commands/ATopics.java` | 103 | `ARCTOPICS` | Index of archon help topics, cached after the first call (`:48-73`). Staff: AHELP security (`:98-101`). |
| AHELP (ARCHELP) | `Commands/AHelp.java` | 189 | `AHELP [topic]` | Archon help entry with a See-also line (`:154-163`). Staff: AHELP security (`:184-187`). `AHELP MORE` cannot reach its listings (see Problems). |
| AUTOINVOKE | `Commands/AutoInvoke.java` | 320 | `AUTOINVOKE`, then at the prompt a name, `*name`, `name*`, `INVOKE name` or `UNINVOKE name` | Interactive list of auto-invoked passives, each ACTIVE or INACTIVE, switched on or off (`:104-183`, `:206-266`). Not orderable (`:315`). |

### Commerce and auction

| Verb (access words) | File | Lines | Syntax | What it does; cost and restrictions |
|---|---|---|---|---|
| BUY | `Commands/Buy.java` | 169 | `BUY [n] item [FROM] shopkeeper`; `BUY item FOR person`; `ALL.item`, `item.ALL` | Buys from a shopkeeper, with quantity and ALL batching (`:82-118`). Costs 0.20 out of combat and 1.0 in combat by default (`:153-162`). Not orderable (`:165`). |
| BID | `Commands/Bid.java` | 153 | `BID amount [n] item WITH auctioneer` | Bids on stock held by an Auctioneer-type shopkeeper. This is not the live auction (`:61`). Costs 0.20 / 1.0 (`:137-146`). Not orderable. |
| BORROW | `Commands/Borrow.java` | 124 | `BORROW amount FROM banker`; `BORROW item FROM librarian` | Borrows coins from a Banker (`ALL` means the maximum, `:74-75`) or an item from a Librarian (`:86-91`). Costs 0.20 / 1.0 (`:107-116`). Not orderable (`:119`). |
| AUCTION | `Commands/Auction.java` | 531 | `AUCTION [LIST]`, `UP [price] item`, `BID amount`, `CLOSE`, `INFO`, `CHANNEL text`, or plain text for the channel | The one global live auction (How it works, 6). Turns the AUCTION channel back on if it was off (`:336-341`). Listing fee comes from AUCTIONRATES, which is free by default (`coffeemud.ini:1863`). Disabled when CHANNELAUCTION is disabled (`:526-530`). Cost 0 out of combat and 1.0 in combat, from `Commands/Channel.java:377-380`. Not orderable (`:519`). |
| ACTIVATE (ACT, A, >) | `Commands/Activate.java` | 156 | `ACTIVATE item [text]` | Switches on Electronics (not Software) in the room, in an open container, or the Computer you ride (`:63-111`). The rest of the line is passed as the tool text (`:131`). Costs 0.20 / 1.0 (`:140-149`). |

### Movement

| Verb (access words) | File | Lines | Syntax | What it does; cost and restrictions |
|---|---|---|---|---|
| ABOVE | `Commands/Above.java` | 77 | `ABOVE` | Climbs up, running or walking as AUTORUN says (`:52-57`). Ship only (`:68-75`). Cost 0.20, or 0.05 with AUTORUN, from `Go` (`Commands/Go.java:261-276`). |
| BELOW | `Commands/Below.java` | 76 | `BELOW` | Goes down; same rules as ABOVE (`:52-57`, `:68-75`). Ship only. Same cost as ABOVE. |
| AFT | `Commands/Aft.java` | 92 | `AFT [east/west]` | Moves toward the ship's aft: default south, with east or west giving southeast or southwest (`:52-60`). Ship only (`:78-85`). Kept out of the command list (`putInCommandlist`, `:88-91`). Same cost as ABOVE. |
| BACK | `Commands/Back.java` | 92 | `BACK [east/west]` | The same as AFT for caravans (`:78-85`). Caravan only. Kept out of the command list. Same cost as ABOVE. |

### Communication

| Verb (access words) | File | Lines | Syntax | What it does; cost and restrictions |
|---|---|---|---|---|
| ANNOUNCE (ANNOUNCETO, ANNOUNCEMSG) | `Commands/Announce.java` | 170 | `ANNOUNCE msg`; `ANNOUNCETO ALL/HERE/name msg`; `ANNOUNCEMSG prefix` | Broadcasts to local sessions, with a voice chosen by alignment or the sender's own prefix (`:49-76`, `:113-151`). Staff: ANNOUNCE security for the whole verb (`:165-168`). |
| ASK | `Commands/Ask.java` | 76 | `ASK person message` | A Say aimed at one person, through `Say.exec` with the to-flag set (`:52-55`; `Commands/Say.java:117`). Costs 0.20 / 1.0 (`:59-68`). |
| ALIAS | `Commands/Alias.java` | 373 | `ALIAS` (menu); `ALIAS DEFINE key text`; `ALIAS DEFINE key` (deletes); `ALIAS COPY name`; `ALIAS TEST key` | Player macros (How it works, 11). Not orderable (`:366-370`). Cost 0. |

### Combat and party

| Verb (access words) | File | Lines | Syntax | What it does; cost and restrictions |
|---|---|---|---|---|
| ASSIST | `Commands/Assist.java` | 140 | `ASSIST person` | Joins a person's fight by running Kill on their victim (`:69-86`), helps with their crafting (`:87-105`), or repeats a Push or Pull they are doing (`:107-115`). Costs 0.20 / 1.0 (`:122-131`). |

### Settings (toggles)

Most of these verbs take no argument to toggle, `ON` or `OFF` to set, and reject anything else with "Try ON or OFF, or nothing to toggle". ANSI and AFK take their own arguments (see their rows). "Set means" gives the state of the attribute bit, which matters for the reversed bits (How it works, 3).

| Verb (access words) | File | Lines | Bit (set means) | Where the setting is read |
|---|---|---|---|---|
| ANSI (COLOR, COLOUR) | `Commands/ANSI.java` | 254 | ANSI, ANSI16ONLY, ANSI256ONLY (on) | Colour depth. `ANSI OFF` hands off to NOANSI (`:58-64`). Saved to the account (`:232-237`) and to the telnet session modes (`:238-243`). |
| AUTOGOLD | `Commands/AutoGold.java` | 79 | AUTOGOLD (on) | Kill beneficiaries in the death room are treated as gold looters (`Libraries/MUDFight.java:1555-1560`). GoldLooter behaviour sets it (`Behaviors/GoldLooter.java:61`). |
| AUTOLOOT | `Commands/AutoLoot.java` | 78 | AUTOLOOT (on) | The killer loots the corpse (`Libraries/MUDFight.java:1645-1650`). CorpseLooter behaviour sets it (`Behaviors/CorpseLooter.java:61`). |
| AUTOEXITS | `Commands/AutoExits.java` | 79 | AUTOEXITS (on) | Exits shown with the room view (`Commands/Look.java:288`, `:316`). |
| AUTOASSIST | `Commands/AutoAssist.java` | 83 | AUTOASSIST (reversed: set means off) | Whether you join allies' fights (`Libraries/MUDFight.java:2905-2910`). NoCombatAssist behaviour sets it to disable assisting (`Behaviors/NoCombatAssist.java:61`). |
| AUTOMELEE | `Commands/AutoMelee.java` | 82 | AUTOMELEE (reversed) | Repositioning to weapon range in combat (`Libraries/MUDFight.java:3207-3238`). The command's messages disagree with the engine (Problems). |
| AUTOGUARD (GUARD) | `Commands/AutoGuard.java` | 83 | AUTOGUARD (on) | Stand guard and stop following a leader. `Behaviors/Nanny.java:700` clears it. |
| AUTODRAW | `Commands/AutoDraw.java` | 79 | AUTODRAW (on) | Draws a weapon when one is handy and sheathes it after peace (`MOBS/StdMOB.java:4223`). |
| AUTORUN | `Commands/AutoRun.java` | 79 | AUTORUN (on) | Direction verbs run rather than walk (`Commands/Above.java:54`, `Commands/Aft.java:64`, `Commands/Down.java:54`). |
| AUTOWEATHER | `Commands/AutoWeather.java` | 79 | AUTOWEATHER (on) | Weather messages (`Behaviors/WeatherAffects.java:768`, `:939`). |
| AUTOIMPROVEMENT | `Commands/AutoImprovement.java` | 79 | AUTOIMPROVE (on) | Skill improvement notices (`Abilities/StdAbility.java:1855`). |
| AUTONOTIFY | `Commands/AutoNotify.java` | 78 | AUTONOTIFY (on) | Notice when a friend arrives (`Libraries/CharCreation.java:973`). |
| AUTOFORWARD | `Commands/Autoforward.java` | 84 | AUTOFORWARD (reversed) | Email forwarding. Refused unless EMAILFORWARDING is set (`:52-56`). Read by `Commands/Email.java:106`. |
| AUTOMAP | `Commands/AutoMap.java` | 85 | AUTOMAP (reversed) | Suppresses the map after a look (`Libraries/CommonMsgs.java:2119-2120`). The verb is hidden unless AWARERANGE is above 0 (`:80-83`; `coffeemud.ini:551`). |
| AUTOATTACK | `Commands/AutoAttack.java` | 91 | AUTOATTACK (reversed) | Set means auto-attack is blocked (`Libraries/Sense.java:160-163`). Refused under turn-based or manual combat when turning it off (`:66-71`). |
| AUTOTELLNOTIFY | `Commands/AutoTellNotify.java` | 93 | account flag AUTOTELLNOTIFY | Tell notification. Needs the account system (`:56-58`). Consumer not traced. |
| BRIEF | `Commands/Brief.java` | 78 | BRIEF (on) | Brief room descriptions (`Commands/Look.java:105`, `:292`). |
| AFK | `Commands/AFK.java` | 75 | session AFK flag | Toggles AFK with optional text (`:54-62`). Gives RP experience if RP_GOAFK is set (`:63-64`). |

### Staff and administration

| Verb (access words) | File | Lines | Syntax | What it does; cost and restrictions |
|---|---|---|---|---|
| AS | `Commands/As.java` | 298 | `AS person/PLAYERS/PORT n [HERE or .] command` | Runs a command as another character by taking over their session (How it works, 8). Staff: AS security everywhere (`:61`, `:293-296`). PLAYERS needs ORDER (`:103-104`). Not orderable (`:287`). |
| AT | `Commands/At.java` | 135 | `AT roomid/person/area/PLAYERS/text command` | Runs a command in one or more other rooms, then returns the caller (How it works, 9). Staff: AT security anywhere (`:130-133`) and per room (`:106`). |
| AFTER | `Commands/After.java` | 200 | `AFTER [EVERY] n unit command`; `AFTER LIST`; `AFTER STOP` | Delayed or repeating command (How it works, 10). Staff: AFTER security in the room (`:169-172`). Not orderable (`:163`). |
| ASYNC | `Commands/ASync.java` | 103 | `ASYNC command` | Runs the command on a separate thread (`:64-87`). Staff: ASYNC security (`:59`, `:98-101`). Not orderable (`:92`). |
| BAN | `Commands/Ban.java` | 86 | `BAN ip-or-name-mask` | Adds a mask to banned.ini (`:60`; `core/CMSecurity.java:2158-2175`). Staff: BAN security (`:81-84`). |
| BOOT | `Commands/Boot.java` | 108 | `BOOT name/address` | Disconnects a session matched by name or address (`:63-66`). Cannot boot yourself (`:68-71`). Staff: BOOT security (`:103-106`). |
| BEACON | `Commands/Beacon.java` | 131 | `BEACON [name]` | Sets the start room (respawn point) of yourself, of a named online character, or of a ship's home port (`:55-98`, `:102-113`). Staff only: the whole verb needs BEACON security in the current room (`:126-129`). |

## How it works

### 1. The shape of a verb

Each verb extends `StdCommand` and overrides `getAccessWords()`, `execute()` and, where needed, `securityCheck`, `canBeOrdered` and the cost methods. `execute` receives the typed words as a mutable list. Most verbs drop word 0 at once (for example `Commands/After.java:78`, `Commands/At.java:52`). A common idiom reads the argument string as `parm = commands.size()>1 ? CMParms.combine(commands,1) : ""` (for example `Commands/AutoRun.java:52`). Output goes through `mob.tell()` or `session().colorOnlyPrint*`, and every literal is wrapped in `L()` for translation (`Commands/StdCommand.java:73-76`). Colour codes are inline (`^H`, `^N`, `^w`, `^!`).

Errors use the engine's shared paths, `CMLib.commands().postCommandFail(...)` and `postCommandRejection(...)`, which pass the original words so the message can be attributed to the command (for example `Commands/Buy.java:60`, `Commands/Activate.java:56`, `:135`).

### 2. Action costs: one difference between verbs

`StdCommand.actionsCost` returns `CMProps.getCommandActionCost(ID(), 0.0)` (`Commands/StdCommand.java:223-227`), so a verb costs 0 out of combat unless its ID appears in the `DEFCMDTIME` exception list. `combatActionsCost` does the same with `DEFCOMCMDTIME` (`:229-233`). Six verbs in this bucket instead call the one-argument getters, which fall back to the shipped defaults: `CMProps.getCommandActionCost(ID())` uses `DEFCMDTIME/100` (`core/CMProps.java:1183-1185`), which is 0.20 (`coffeemud.ini:545`), and the combat getter uses `DEFCOMCMDTIME/100`, which is 1.0 (`core/CMProps.java:1194-1196`, `coffeemud.ini:546`). The six are Ask (`Commands/Ask.java:59-68`), Activate (`Commands/Activate.java:140-149`), Assist (`Commands/Assist.java:122-131`), Bid (`Commands/Bid.java:137-146`), Borrow (`Commands/Borrow.java:107-116`) and Buy (`Commands/Buy.java:153-162`). Four movement verbs (ABOVE, BELOW, AFT, BACK) inherit `Go`'s cost methods, which read `DEFCMDTIME` directly, so they cost 0.20 and 0.20 in combat, and a quarter of that when AUTORUN is set (`Commands/Go.java:261-276`). Channel's combat cost uses the combat getter, so AUCTION costs 1.0 in combat and 0 otherwise (`Commands/Channel.java:377-380`). Say's cost overrides match Ask's (`Commands/Say.java:417-426`). Everything else in the bucket costs 0. The split is an accident of which getter a verb calls, and a change to one default changes only some verbs.

### 3. The settings family and its reversed bits

Settings are bits in `MOB.Attrib` (`MOBS/interfaces/MOB.java:1055-1090`). Each bit carries an `autoReverse` flag, and `isAutoReversed()` is documented as "setting it turns it off, while clearing it turns it on" (`MOBS/interfaces/MOB.java:1119-1125`). Five bits in this bucket are reversed: AUTOASSIST (`MOB.java:1060`), AUTOMELEE (`:1063`), AUTOFORWARD (`:1074`), AUTOMAP (`:1080`) and AUTOATTACK (`:1087`). The engine readers agree with the flag: `Libraries/Sense.java:160-163` returns false for auto-attack when AUTOATTACK is set, `Libraries/CommonMsgs.java:2119-2120` skips the map when AUTOMAP is set, and `Libraries/MUDFight.java:2908` says "! means they DO autoassist" next to a negated test.

Most toggle verbs have the same three branches: a "turn on" branch for the state the bit must be in (or an explicit ON), a "turn off" branch for the mirror state (or an explicit OFF), and an error branch for any other argument. The pattern is clearest in `Commands/AutoRun.java:52-69`. For the reversed bits the branches are written with the sense inverted (`Commands/AutoAssist.java:53-67`). AutoMap breaks the pattern (Problems, 1).

NPC behaviours reuse the same bits. GoldLooter sets AUTOGOLD (`Behaviors/GoldLooter.java:61`), CorpseLooter sets AUTOLOOT (`Behaviors/CorpseLooter.java:61`), and NoCombatAssist sets AUTOASSIST true (`Behaviors/NoCombatAssist.java:61`), which under the reversed reading disables assisting.

### 4. Status and effect listings

**Affect.** `getMOBState` (`Commands/Affect.java:55-128`) builds the state lines. Hygiene is `hygiene / HYGIENE_DELIMIT`, where `HYGIENE_DELIMIT` is 5000 (`Common/interfaces/PlayerStats.java:1303`), and the five bands are at `Commands/Affect.java:66-78`. Posture comes next (`:89-104`), then bound, playerkill, invisible, hidden, sneaking and QUIET (`:82-119`), then fatigue, hunger and thirst (`:121-126`). Fatigue is "above `FATIGUED_MILLIS`", which is `TICKMS * 3000` (`Common/interfaces/CharState.java:75`). Hunger and thirst are "below 1" (`:123-126`).

`getAffects` (`:130-259`) lays out two columns, 36 characters wide (`:133-134`). The sort keys are NAME, TYPE, DOMAIN and REMAIN (`:138-189`). REMAIN subtracts the wall-clock time since the invoker last ticked from each expiry (`:176-182`), so the order is an estimate between ticks. An effect whose display text is in parentheses shows its time left through `CMLib.time().date2EllapsedTime` (`:219-229`). Ability type and domain filters compare classification bits (`:196-208`); a domain is encoded as `(typ+1) << 5` (`:294`).

**AutoAffects** uses the same idea for passives only: effects with empty display text that are auto-invoked and still on the mob (`Commands/AutoAffects.java:57-76`), in 25-character columns (`:55`).

**Account.** `showCharLong` (`Commands/Account.java:54-119`) builds one row per character. The markers are `*` for an online session (`:67-69`), `(tells)` for an AFK session with pending tells (`:70-85`), `(email)` for unread journal mail in the mailbox (`:86-89`) and `(post)` for post-office items (`:90-116`). The footer sums `player.age()` in minutes and divides by 60 (`:193-202`; `Libraries/interfaces/PlayerLibrary.java:1094-1098`).

**Areas.** Options are read word by word (`Commands/Areas.java:65-111`). Sorting uses any `Area.Stats` value (`:175-211`). Sysops get a colour by area state: ACTIVE, PASSIVE, FROZEN and STOPPED (`:235-251`). The filter is a string expression over the stat names and AUTHOR, evaluated by `CMStrings.parseStringExpression` (`:253-274`).

### 5. Achievements

Each agent (player, account, clan) yields a set of tattoos the character holds, and the union becomes `wonList` (`Commands/Achievements.java:364-388`). With no agent words the defaults are ACCOUNT, PLAYER and CLAN (`:351-356`), and the default list is WON (`:315`).

Tattoo series collapse. `getLowestNumberedTattoos` (`:83-111`) reads the trailing digits of a tattoo name and keeps one representative per numbered family, so a ladder of achievements shows one unearned rung in NOW and ALL.

Progress shows as `score/target` (`:466-475`, `:528`). A STATVALUE target is shifted by one depending on whether the floor is the goal (`:448-449`).

The preview forms are a dry run. `CMLib.achievements().fakeBumpAchievement(whoM, E, 1)` (`:211-212`) returns the awards a remort or retirement would grant, and the code lists them without changing state (`:213-241`). FUTURE shows the account awards that apply to new characters, and is only offered when COMMONACCOUNTSYSTEM is above 1 (`:245`). Event types come from `Libraries/interfaces/AchievementLibrary.java:71` onward.

### 6. The live auction

The auction is one global slot, `getLiveData()` (`Commands/Auction.java:73-82`), and the state machine uses constants `STATE_START` (0) through `STATE_CLOSED` (5) (`:84-89`).

- **Listing.** `AUCTION UP [price] item` (`:365-427`). The item must be in inventory, not worn, and an empty container (`:390-399`). A listing fee is taken from the policy (`:400-419`). The policy reads AUCTIONRATES: field 0 is the live listing fee, field 3 the live house cut (`Common/DefaultAuctionPolicy.java:59`, `:62`). The shipped value `0,2,0.1%,10%,5%,4,600` (`coffeemud.ini:1863`) makes listing free and the house cut 10%. The item is removed from the seller at once (`:270`).
- **Opening.** The opening bid sets `bid` to the price and `highBid` to one less, and starts the tick (`:262-267`).
- **Bidding.** `CMLib.coffeeShops().bid` (`Libraries/CoffeeShops.java:2608-2653`) rejects a bid below the current price (`:2622-2627`). An accepted bid refunds the previous high bidder (`:2633`), charges the new bidder the full amount (`:2644`), and sets the displayed price to the old high bid plus one (`:2642`).
- **Timing.** Each phase lasts `15000 / TICKMS` ticks (`Commands/Auction.java:99-103`). With `TICKTIME=4000` (`coffeemud.ini:2151`) that is 3 ticks, about 12 s. START lasts until 75 s (`:114`). If no one has bid by 45 s, the state jumps silently to RUNOUT (`:119-121`). After that the states run RUNOUT, ONCE, TWICE, THREE and CLOSED, with a call-out each time (`:126-144`). A bid made after START resets the state to RUNOUT (`:277-278`), which adds a phase.
- **Closing.** If someone other than the seller holds the top bid, the sale goes ahead. The price is `bid`, and the winner is refunded the difference between their high bid and that price (`:161`). The house takes `floor(bid * liveCutPct)` (`:159`), and the seller receives the rest (`:160`, `:162`). The item is moved to the winner's room with robbery suppression on (`:158`, `:168-172`) and then picked up with `postGet` (`:176`). With no winner the item goes back to the seller (`:216-222`). The seller's `CLOSE` returns the item and refunds the high bid (`:471-473`).

### 7. Shop verbs: BUY, BID, BORROW

Buy, Bid and Borrow share the same parsing. A leading number is a quantity (`Commands/Buy.java:82-88`), `ALL.` and `.ALL` widen the match (`:92-102`), and a loop collects matching stock up to the quantity using `shop.getStock` and `numberInStock` (`:107-118`). Bid repeats the loop with an Auctioneer-only check (`Commands/Bid.java:61`, `:98-113`).

Each action is a `CMMsg` offered to the room with `okMessage` and then sent (`Commands/Buy.java:137-141`, `Commands/Bid.java:123-128`). The message types are `MSG_BUY`, `MSG_BID` and `MSG_BORROW` (`Commands/Borrow.java:99`). Borrow reads a Banker's loan as currency (`makeCurrency`, `:84`) or a Librarian's stock as an item (`:90`).

### 8. Possession: AS

`AS` checks security everywhere (`Commands/As.java:61`, `:293-296`), refuses a target who is already possessed (`:162-166`), refuses sysops unless the caller is one (`:168-172`), and requires ORDER for players (`:173-180`). Possession is a session swap. The target gets the caller's session (`M.setSession(mySession)`, `mySession.setMob(M)`), and `M.setSoulMate(mob)` links them (`:226-228`). The command then runs as the target (`:248`). The `finally` block restores the original session and clears the link (`:252-270`). Each swap sleeps 100 ms (`:245`, `:271`). `AS PORT n` finds a host by public port and runs on that host's thread group (`:70-100`, `:278-281`). `AS HERE` or `AS .` brings the target to the caller's room first (`:230-236`).

### 9. Other rooms: AT

Targets are resolved in order. `PLAYERS` gives every online player's room (`Commands/At.java:62-86`). A room ID goes to `CMLib.map().getRoom` (`:89`). Anything else goes to `findWorldRoomLiberally(mob, cmd, "APMIR", 100, 120000)` (`:89-91`), which searches areas, players, mobs, items and rooms within 2 minutes (`Libraries/WorldHuntUtils.java:552`, flag switch `:770-800`). Each room must pass `CMSecurity.isAllowed(mob, room, AT)` (`Commands/At.java:106`). The caller is moved there, the command runs, and the caller is moved back if the command did not move them (`:113-118`).

### 10. Delayed and background commands: AFTER and ASYNC

`AFTER` keeps an `AfterCommand` (start time, duration, repeat flag, mob, words) in an in-memory list (`Commands/After.java:55-63`, `:149-156`) and starts a tick (`:157`). The tick runs each due command as its mob (`:175-199`), and EVERY restarts the timer from now (`:186-190`). Units are matched by prefix: ticks, seconds, minutes, hours, days or weeks (`Libraries/EnglishParser.java:2276-2297`). Timing uses `System.currentTimeMillis()` (`Commands/After.java:150`, `:183`), so the schedule is in wall-clock time. `ASYNC` runs the command on another thread through `CMLib.threads().executeRunnable` (`Commands/ASync.java:64-87`), logging an exception and telling the mob its message (`:75-78`).

### 11. Aliases

Aliases are stored per character in a map keyed by uppercase name. `getAlias` returns "" when there is none (`Common/DefaultPlayerStats.java:707-712`). Names must be letters and digits and may not be an access word (`Commands/Alias.java:75-87`). Values have `<` and `&` removed on entry (`:346-347`).

Expansion happens in `CMLib.utensils().deAlias` (`Libraries/CoffeeUtensils.java:3844-3876`). The definition is split on `~` (`CMParms.parseSquiggleDelimited`). A leading `noecho` suppresses the echo. If the definition contains `@x`, positional variables are replaced (`CMStrings.replaceVariables`). Otherwise the typed arguments are appended to every sub-command. `ALIAS TEST key` prints the expansion without running it (`Commands/Alias.java:176-205`).

`ALIAS COPY name` asks for confirmation and then copies aliases the caller does not already have from another character (`:93-175`). The interactive menu appears when the verb has no words (`:206-322`).

### 12. Auto-invoked passives and the opt-out list

`AUTOINVOKE` lists the mob's abilities that are `isAutoInvoked()`, skipping language and property classes and racial effects (`Commands/AutoInvoke.java:104-131`). An entry is ACTIVE when the effect is currently on the mob and not savable (`:133-141`).

Turning one off adds its ID to the character's opt-out list (`:70-71`) and removes the effect. Turning one on removes the ID from the list and forces `autoInvocation` (`:89-91`). `StdAbility.autoInvocation` skips listed IDs unless forced (`Abilities/StdAbility.java:2368-2378`). Sysops and staff with ALLSKILLS get a configured list seeded at class grant (`CharClasses/StdCharClass.java:1336-1343`).

### 13. Vehicle-bound direction verbs

ABOVE and BELOW call `standIfNecessary`, then `tracking().run` or `walk` in the UP or DOWN direction (`Commands/Above.java:52-57`). AFT and BACK do the same toward the aft direction, and EAST or WEST map to the diagonal (`Commands/Aft.java:52-67`). The security check is the vehicle type: SHIP for ABOVE, BELOW and AFT, CARAVAN for BACK (`Commands/Above.java:68-75`, `Commands/Aft.java:78-85`, `Commands/Back.java:78-85`). AFT and BACK are kept out of the command list (`:88-91`). All four extend `Go` (b170).

### 14. Speech and announcements

ASK extends Say and calls `Say.exec(mob, toFlag=true, multiRoom=false, ...)` (`Commands/Ask.java:49-55`). ANNOUNCE broadcasts to every local session whose mob is in a room and whose location gives the caller ANNOUNCE security (`Commands/Announce.java:124-130`). `ANNOUNCETO name` matches one session by name or `HERE` (`:136-148`). The voice in `sendAnnounce` depends on alignment (`:56-72`), unless the sender has set their own prefix with ANNOUNCEMSG (`:52-53`, `:100-111`).

### 15. Help and topics

AHELP looks up the archon help file, with See-also (`Commands/AHelp.java:154-163`). With no argument it reads `help/arc_help.txt` (`:61`). ATOPICS builds its index from the archon help table, keeping entries whose value is long or not another key (`Commands/ATopics.java:56-63`). The index is cached in `Resources` (`:50`, `:69`).

### 16. Smaller mechanisms

- **ANSI.** Three colour levels are kept as separate bits: 16-colour only, 256-colour only, and neither for true colour (`Commands/ANSI.java:66-209`). The bits are written to the account (`:232-237`) and to the session's telnet modes (`:238-243`).
- **BAN.** `CMSecurity.ban` writes the mask to banned.ini and returns -1 when the mask is new (`core/CMSecurity.java:2158-2175`). A repeated mask reports its list number (`Commands/Ban.java:60-68`).
- **BOOT.** Matches a session by name or by address (`Commands/Boot.java:63-66`), stops it, and tears the session down completely when it has no history and the character is not in the game (`:81-86`).
- **AFK.** Stores the AFK text and flag on the session (`Commands/AFK.java:54-62`). A player who goes AFK gets an RP experience award when RP_GOAFK is set (`:63-64`).

## Content worth mining

- **Need and posture lines** (`Commands/Affect.java:58-126`). A short, ready-made status vocabulary for a survival layer: five hygiene bands from "could use a bath" to a severe one, posture (sleeping, resting, swimming, climbing, flying, standing), bound, invisible, hidden, sneaking, fatigued, hungry and thirsty. The bands are fixed thresholds on a 5000-unit scale, so they port directly.
- **Live auction call-outs** (`Commands/Auction.java:130-144`, `:151-166`, `:221`). The "going ONCE / going TWICE / last chance" sequence, the "almost done" warning, the sold and no-sale lines. They make a good model for NPC auctioneer lines in a single-player game.
- **Alignment announcement voices** (`Commands/Announce.java:65`, `:68`, `:71`). Three voices for evil, good and neutral speakers. Paraphrase rather than copy in any new text.
- **Achievement triggers** (`Libraries/interfaces/AchievementLibrary.java:71` onward). An enumerated trigger list with parameter names: kills, stat values, factions, exploration, charity, room entry, crafting, mending, deconstruction, skill use, skill proficiency, effects, socials, quests, levels gained, time played, deaths, remorts, retirements, item collection and births. Each carries its own parameters, such as a zapper mask or an ability ID.
- **Area states** (`Commands/Areas.java:237-251`). ACTIVE, PASSIVE, FROZEN and STOPPED, shown by colour, a useful admin vocabulary for a world that is paused or running.
- **Settings catalogue** (How it works, 3 and the Settings table). About twenty per-character preferences, each with on and off text, which is itself a ready list of options for a single-player game.
- **Passive-effect opt-out** (`Commands/AutoInvoke.java`). A list of passives the player has switched off, kept across sessions.

## Notable design ideas

- **One toggle grammar for two dozen settings.** Each setting is a bit with an optional reversed flag (`MOBS/interfaces/MOB.java:1119-1125`), and almost every verb follows the same ON, OFF, toggle, error contract. Reversed bits are a clever compatibility trick, though they are easy to get wrong (Problems, 1 and 10).
- **A second-price sale with escrow and a bid-reset timer.** Charging the full bid up front, refunding the outbid, and displaying one unit above the runner-up makes the winner pay roughly the second bid (`Libraries/CoffeeShops.java:2633-2644`, `Commands/Auction.java:159-162`). A bid late in the countdown restarts it (`:277-278`).
- **Dry-run previews.** REMORT and RETIRE show what would be granted without changing anything (`Commands/Achievements.java:211-241`).
- **Possession by swapping the session and a soul-mate link,** restored in a `finally` block (`Commands/As.java:226-270`).
- **Time-boxed search.** `AT` hands the world hunt a budget of 120 seconds (`Commands/At.java:91`, `Libraries/WorldHuntUtils.java:552`).
- **Alias chains with appended arguments** (`Libraries/CoffeeUtensils.java:3844-3876`).
- **Verbs built from other verbs.** ASSIST invokes KILL with a synthesized word list (`Commands/Assist.java:84-85`). ASK reuses SAY (`Commands/Ask.java:55`). ABOVE, BELOW, AFT and BACK share the movement base class (b170).
- **Roleplay hook.** Going AFK can earn RP experience (`Commands/AFK.java:63-64`).
- **Cost by getter.** A verb's cost is set by which of the two cost getters it calls (How it works, 2), which is a quiet way to make some verbs cost time.

## Problems and cruft

1. **AUTOMAP's bare toggle does nothing when the map is on.** Both the first and second branch test `isAttributeSet(AUTOMAP) && parm empty` (`Commands/AutoMap.java:54` and `:60`). When the map is on the bit is clear, so a bare `AUTOMAP` matches neither branch and prints nothing. Only `AUTOMAP OFF` works in that state. The sibling verbs use `!isAttributeSet` in the second test (`Commands/Autoforward.java:64`). Also, the command is hidden unless AWARERANGE is above 0 (`:80-83`), which the shipped value does not set (`coffeemud.ini:551`).
2. **AHELP MORE cannot reach its listings.** The block at `Commands/AHelp.java:59-62` runs only when there are no words, and it then tests `helpStr.equalsIgnoreCase("more")` (`:62`), which is never true. `AHELP MORE` falls through to a topic lookup for "MORE" (`:152-163`). The property, disease, poison, superpower, tech and behaviour listings (`:64-146`) are dead code.
3. **BUY's rejection message can never print.** It sits inside the loop, where `buyItemsV.size()==0` is false (`Commands/Buy.java:128`, `:133-147`). BID's equivalent check (`Commands/Bid.java:130`) works.
4. **ALIAS DEFINE on a missing key reports success.** The test at `Commands/Alias.java:63-64` checks `alias==null`, but `getAlias` returns "", so the "No such alias" branch is unreachable (`Common/DefaultPlayerStats.java:707-712`).
5. **ACHIEVEMENTS lists one clan only.** The clan loop `break`s after its first iteration (`Commands/Achievements.java:421-427`), although every clan counts toward `wonList` (`:379-388`). This looks unintended.
6. **AREAS SORT=LEVEL comparator breaks its contract.** It returns 1 when two levels are equal (`Commands/Areas.java:141-145`). A comparator must return 0 for equal keys. This may make `Collections.sort` throw "Comparison method violates its general contract" on some inputs (not run).
7. **Possible money loss on a self-bid at auction.** Nothing prevents the auctioneer bidding on their own sale (`Libraries/CoffeeShops.java:2608-2653`, `Commands/Auction.java:277-288`). If they hold the top bid at close, the no-winner branch returns the item but never refunds their escrow (`Commands/Auction.java:214-223`). Read from the code, not run.
8. **No null check on the winner's location.** `winnerM.location().moveItemTo(...)` (`Commands/Auction.java:158`) assumes a bidder still has a room. Whether a departed bidder has one was not checked.
9. **The AUTOMELEE messages contradict the engine.** Turning it on (bit cleared) says "enter melee combat normally" (`Commands/AutoMelee.java:53-56`). Turning it off (bit set) says "no longer charge into melee" (`:61-64`). The engine repositions the fighter, advancing or retreating to weapon range, only when the bit is set (`Libraries/MUDFight.java:3207-3238`), which is the reverse of the text. Read from the code, not run.
10. **Reversed bits are easy to misread.** AUTOATTACK, AUTOASSIST, AUTOMELEE, AUTOFORWARD and AUTOMAP store "off" in a bit named for "on" (`MOBS/interfaces/MOB.java:1060-1087`). The flag is documented, but every new reader has to remember it.
11. **ANSI 16 and ANSI 256 are independent.** Each toggles its own bit (`Commands/ANSI.java:98`, `:147`), so both can be set. The message prefers 16. The session effect was not traced.
12. **AFTER**'s `name()` returns "SysOpSkills" (`Commands/After.java:38-41`), a copy-paste leftover. An unknown time unit reports "Time may not be 0" (`:136-139`). Timing is wall-clock (`:150`, `:183`).
13. **AFFECT changes state while reading.** A sysop viewing their own state has hygiene reset to 0 (`Commands/Affect.java:62-63`).
14. **AUTOINVOKE repeats and swallows.** The stat recovery calls appear twice (`Commands/AutoInvoke.java:268-275`). The re-run of the verb after "Enter to continue" swallows every exception (`:297-303`), and the menu nests prompts recursively (`:276-307`).
15. **ALIAS COPY may unload its source early.** `Session.prompt` returns at once (`Common/DefaultSession.java:1748-1759`), so the `finally` at `Commands/Alias.java:165-171` may unload the source character before the Y/N answer is read at `:147-160`. Whether the cached data survives depends on `unloadOfflinePlayer`, which was not read.
16. **Smaller items.** `toggle` is unused (`Commands/Account.java:124`, `:199`). The typo "Non-existant" is at `Commands/AutoAffects.java:110`, and "Asyncronously" at `Commands/ASync.java:56`. The name `reverseList` in `Commands/ATopics.java:55` is misleading. `SORT=NAME` only strips its word and labels the output, and does not sort (`Commands/Areas.java:92-97`). ACHIEVEMENTS blocks the calling thread for 4 s (`Commands/Achievements.java:156`), as its own comment admits (`:157-158`).
17. **Multiplayer assumptions.** AS, AT PLAYERS, ANNOUNCE, BOOT, BAN, the ACCOUNT multi-character listing, the tell, email and post markers, achievement agents for accounts and clans, the single live auction, and BEACON on other characters all assume other people are present.

## Takeaways for a Python SUD

- **Keep one toggle function and one settings table.** Store each setting as a plain boolean with its on and off text, and write one function for ON, OFF and toggle. The reversed-bit trap and AutoMap's bug disappear by construction:

  ```python
  def toggle(player, key, arg=""):
      arg, cur = arg.strip().lower(), player.settings[key]
      if arg == "on" or (arg == "" and not cur):
          player.settings[key] = True
          return SETTINGS[key].on_text
      if arg == "off" or (arg == "" and cur):
          player.settings[key] = False
          return SETTINGS[key].off_text
      return "Try ON or OFF, or nothing to toggle."
  ```

- **Keep the per-verb cost as data, with one default.** Store `cost[verb]` in a table defaulting to 0, and write it down. Do not reproduce the two-getter split, which costs some verbs 0.2 and others 0 by accident.
- **Keep the effect and status listings.** `describe_effects(entity, sort_by)` returning lines with remaining time in game ticks is a good fit. Take the hygiene, posture and need lines as a survival status block, rewritten in your own words.
- **Borrow the auction as an NPC event, not a market.** Use the phase sequence (START, RUNOUT, ONCE, TWICE, THREE, CLOSED) and the bid-resets-countdown rule, with a timer in game ticks rather than wall-clock milliseconds. The NPC auctioneer makes bids from a seeded rule. Drop the global slot and the listing fee. If you keep escrow, make sure the seller's and the winner's refunds are tested.
- **Keep achievements as tracker objects with a dry-run preview.** An `Achievement(trigger, target, rewards)` with `preview(player)` returning what would be granted. Drop the account and clan agents, and the privacy checks.
- **Keep aliases as `;`-separated (or `~`) sequences with argument appending, and keep the TEST command.** Store them in the save file. The variable form (`@x`) can be a plain format string.
- **Replace the scheduler with tick-based timers.** `after(delay_ticks, command, every=False)`, run from the main loop, with `every` restarting the timer. Do not use wall-clock time (`Commands/After.java:150`, `:183`), since a paused or turn-based game would miscount it.
- **Keep the passive-effect opt-out list.** Store a set of ability IDs on the character, skip them when auto-applying passives, and let the player edit it with a small menu.
- **Keep vehicle-bound direction words as data.** A direction table with a context tag (ship, caravan, none). Above, below and aft are then rows, not classes.
- **Keep `AT` as a developer tool.** `at <room_id> <command>` is useful for testing. Give it a search by name only if it is cheap.
- **Drop** AS (possession), BOOT, BAN, ANNOUNCE, ASYNC (no threads needed), the ACCOUNT multi-character listing, the tell, email and post markers, the AUTOTELLNOTIFY account flag, and BEACON on other characters. A single-player game has no other sessions to take over, boot or hear.
- **Drop the ANSI tiers.** Keep one colour switch or a plain-text flag. Terminal capability negotiation is out of scope.
- **Rethink the RP-for-AFK reward.** It is a hook that a single-player game may want for something else, but it is a design choice to make on purpose.

## Coverage

- **Read in full** (class body, from the `public class` line to the end; package, import and licence headers not read line by line): all 47 command classes in the bucket, and `Commands/interfaces/Command.java`.
- **Read in condensed form** (blank lines, brace-only lines and boilerplate `getAccessWords` and `@Override` lines removed, original line numbers kept): `AutoRun`, `AutoWeather`, `AutoLoot`, `AutoNotify`, `Brief`, `Above`, `Ask`, `Below`, `AFK`. The logic of each is complete in that view.
- **Read in part or by search, outside the bucket:** `Commands/StdCommand.java` (whole file, b170), `core/CMProps.java` (1140-1200, 3095-3101), `coffeemud.ini` (537-546, 551, 1853-1863, 2151), `Commands/Skills.java` (46-55, 353-375), `Commands/Channel.java` (37-57, 117-137), `Commands/Say.java` (117-119), `Commands/Go.java` (52), `Common/DefaultAuctionPolicy.java` (40-66), `Libraries/CoffeeShops.java` (2594-2653), `Libraries/CoffeeUtensils.java` (3844-3876), `Libraries/WorldHuntUtils.java` (552-554, 770-800), `Libraries/EnglishParser.java` (2276-2297), `Common/interfaces/Session.java` (1196-1365), `Common/DefaultSession.java` (1748-1759), `Common/DefaultPlayerStats.java` (707-712, 2407-2410), `MOBS/interfaces/MOB.java` (1055-1125), `MOBS/StdMOB.java` (4220-4226), `Libraries/MUDFight.java` (1555-1560, 1645-1650, 2905-2910, 3190-3262), `Libraries/CommonMsgs.java` (2114-2122), `Libraries/Sense.java` (156-165), `Libraries/CharCreation.java` (973), `Commands/Look.java` (105, 288, 292, 316), `Commands/Email.java` (106), `Abilities/StdAbility.java` (1855, 2368-2378), `CharClasses/StdCharClass.java` (1336-1343), `Behaviors/GoldLooter.java` (61), `Behaviors/CorpseLooter.java` (61), `Behaviors/NoCombatAssist.java` (61), `Behaviors/Nanny.java` (700), `Behaviors/WeatherAffects.java` (768, 939) and `Libraries/interfaces/AchievementLibrary.java` (71-100).
- **Not followed:** the ability and skill classes that the listing verbs read, the internals of the channel and say machinery beyond the calls shown, `MUDCmdProcessor`, the achievement library's internals, and the other command buckets. The Problems marked "read from the code, not run" are unverified at runtime.
