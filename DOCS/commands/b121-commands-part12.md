# Commands, part 12: Title to Yell

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b121` (Commands [part 12/12: Title .. Yell]) · 36 files · 7621 lines of Java.

## Summary

These 36 command classes are the tail of the typed verbs. They cover the character's own titles, money and standing (Title, Wealth, Worth, Vassals, WizList), the public boards (Who, WhoIs, Top, WillQualify), speech and shop or bank verbs (Whisper, Yell, WizEmote, View, Value, Withdraw), a route finder (TrailTo), some everyday actions (Unlock, Undress, Wake, Wimpy, Visible, Train, Wear, Wield), and a large set of staff tools (Where, Transfer, Unload, WizInv, UnLink). Most follow the pattern described in b170: parse the words, build a `CMMsg`, let the room veto it, then send it. The distinctive mechanics are the mortal `where`, which ranks areas by level fit, population and alignment; the staff `where`, which searches by text, zapper mask or MQL query and can save changes; `transfer`, whose destination grammar reaches rooms, areas, the inventory, space and another MUD; and `willqualify`, which prints a class's skill table level by level from the ability mapper. For a single-player SUD the shop and bank grammar, the title list, the route filters, the level table and the area recommendation are worth keeping; the boards, staff, speech-to-others and intermud verbs are multiplayer or builder tools.

## Key files and types

Java paths omit the `com/planet_ink/coffee_mud/` prefix, as in b170. Supporting classes outside this bucket are listed after the 36 bucket files and cited where used.

| File or class | Role | Lines |
|---|---|---|
| `Commands/Where.java` | Staff text, mask, MQL and update search; mortal "best areas to explore" ranking | 885 |
| `Commands/Train.java` | Spend trains and practices (deep dive in b170) | 699 |
| `Commands/Transfer.java` | Move characters and items to a room, player, area, inventory, space or another MUD | 681 |
| `Commands/Unload.java` | Staff reloads and unloads (class, help, VFS, ini, area, user, faction, resource, file); firearm ammunition for players | 629 |
| `Commands/Who.java` | Who list, with sort, filters, friends, PK and account views | 452 |
| `Commands/WillQualify.java` | Skill and expertise table for a class and level | 445 |
| `Commands/TrailTo.java` | Route finder with tracking filters, fallback and per-area or per-room sweeps | 293 |
| `Commands/Top.java` | Top-ten boards from text templates (legacy and XML forms) | 292 |
| `Commands/Wear.java` | Wear, hold or wield with body-location parsing (core verb, b170) | 203 |
| `Commands/Whisper.java` | Speech to one person in the room; mount whisper | 176 |
| `Commands/Withdraw.java` | Bank and post-box withdrawals | 166 |
| `Commands/Title.java` | Title menu and random-title toggle | 159 |
| `Commands/WizEmote.java` | Staff broadcast to all, a room, an area, a clan or a match | 142 |
| `Commands/Unlock.java` | Unlock exits (both sides) and items | 141 |
| `Commands/TypeCmd.java` | Type text into a computer or terminal (extends `Go`) | 137 |
| `Commands/View.java` | Look at shop stock | 137 |
| `Commands/Wield.java` | Wield a weapon (core verb, b170) | 137 |
| `Commands/Undress.java` | Remove a worn item from a follower or bound mob | 136 |
| `Commands/Visible.java` | Cancel wizard invisibility and any hiding effect | 135 |
| `Commands/Value.java` | Offer items to a shopkeeper for appraisal | 132 |
| `Commands/WhoIs.java` | One character in detail, prefix lists, finger and intermud | 130 |
| `Commands/Wake.java` | Wake self or another sleeping mob | 124 |
| `Commands/Wealth.java` | Carried coins and bank balances (extends `Inventory`) | 114 |
| `Commands/WizInv.java` | Wizard invisibility on and off | 114 |
| `Commands/WizList.java` | Staff list | 97 |
| `Commands/Vassals.java` | Characters whose liege is the caller | 96 |
| `Commands/Yell.java` | Yell, yell-to, yell-at (extends `Say`) | 91 |
| `Commands/Wimpy.java` | Flee threshold | 80 |
| `Commands/Ver.java` | Version, uptime, pending shutdown notice | 78 |
| `Commands/Weather.java` | Current climate, or all areas | 78 |
| `Commands/Up.java` | Up (extends `Go`) | 76 |
| `Commands/West.java` | West (extends `Go`) | 76 |
| `Commands/Worth.java` | Carried coins only (extends `Inventory`) | 76 |
| `Commands/Topics.java` | Help topic index (extends `ATopics`) | 75 |
| `Commands/UnLink.java` | Remove one exit (rewrites to `DESTROY ROOM`) | 71 |
| `Commands/Xml.java` | Deprecated; hidden | 68 |
| `Commands/Skills.java` | Base of WillQualify: level-range parser, UNIQUE flag, uniqueness test | 589 |
| `Commands/Say.java` | Base of Yell: speech, multi-room spread, verb forms | 434 |
| `Commands/Go.java` | Base of Up and West; movement cost | 283 |
| `Commands/At.java` | Base of Transfer | 135 |
| `Commands/Inventory.java` | Base of Wealth and Worth | 220 |
| `Commands/Destroy.java` | Target of UnLink (direction branch, `:426-470`) | 2466 |
| `Libraries/interfaces/TrackingLibrary.java` | `TrackingFlag` (23 route filters), `TrailFlag`, `getTrailToDescription` | 1150 |
| `Libraries/interfaces/WorldHuntLibrary.java` | `findWorldRoomLiberally`, used by Transfer | 305 |
| `core/CMSecurity.java` | Security flags (WHERE, TRANSFER, TRAILTO, LOADUNLOAD and others) | 3068 |
| `core/CMProps.java` | Command cost getters (`:1143-1197`) | 3965 |
| `MOBS/StdMOB.java` | `isMarriedToLiege` and liege fields (`:984-996`) | 6287 |
| `core/database/MOBloader.java` | `vassals` query (`:1826-1838`) | 3706 |
| `Libraries/CMPlayers.java` | Random title roll in the player service (`:3485-3497`, `:3525-3545`) | 3640 |
| `Common/DefaultPlayerStats.java` | Title list, random flag (`getTitleRandom`, `:780`) | 2540 |
| `Abilities/interfaces/Ability.java` | Type words `ACODE.DESCS` (`:706`) and `DOMAIN.DESCS` (`:893`) | 1109 |
| `Common/interfaces/PrideStats.java` | Six or seven pride stats (`:51-60`) | 78 |
| `Common/interfaces/TimeClock.java` | `TimePeriod` enum (`:736-744`) | 977 |
| `MOBS/StdLibrarian.java` | Shows that a librarian is not a `Banker` or `PostOffice` (`:42`) | 914 |

## How it works

### 1. Catalogue

Cost key. **free** means the class overrides neither cost method, so `StdCommand` returns 0.0 (`Commands/StdCommand.java:223-233`). **default** means the class calls the one-argument `CMProps.getCommandActionCost(ID())`, which is 0.20 out of combat and 1.00 in combat (`coffeemud.ini:545-546`, `core/CMProps.java:1183-1197`). **Go** means the movement cost of `Go` (`Commands/Go.java:261-276`): 0.20, halved with AUTORUN, and the same figure in combat (b170, Problem 1). Scope: **player** means any character. **builder** or **admin** means the verb is gated by a security flag (`core/CMSecurity.java`). The shipped staff groups are in `coffeemud.ini:245-266`.

| Verb (access words) | Syntax | What it does | Cost | Restrictions and notes | Scope |
|---|---|---|---|---|---|
| Title (TITLE) | `title` | Numbered menu of titles. A number makes that title current and moves it to the front. `R` toggles the current title in the random pool. | free | Refused while the current title is in braces (locked). Cannot be ordered (`Commands/Title.java:155-158`). | player |
| Ver (VERSION, VER) | `version` | Server version, copyright line, project contact lines, uptime. Then runs `Shutdown` internally for a pending-shutdown notice. | free | None. Contact lines are not reproduced here (`Commands/Ver.java:53-57`). | player |
| Wealth (WEALTH) | `wealth` | Carried coins, then the caller's balance at each bank chain. | free | Clan bankers are skipped (`Commands/Wealth.java:67`). | player |
| Worth (WORTH) | `worth` | Carried coins only. "You are Broke!" when there are none. | free | None (`Commands/Worth.java:62-64`). | player |
| Vassals (VASSALS) | `vassals` | Characters whose liege is the caller, with race, class, level and experience. | free | Offline entries come from a database query on the liege field (`core/database/MOBloader.java:1834`). | player |
| WizList (WIZLIST) | `wizlist` | Staff list headed "The Administrators of <mud>". Last login is shown to system operators only. | free | None (`Commands/WizList.java:58-59`). | player (public) |
| Who (WHO, WH) | `who`; `who friends`; `who pk`; `who acct`; `who @mud` | Online characters, staff first. Cloaked names are in parentheses; AFK names carry idle time. | free | `acct` needs CMDPLAYERS and the account system (`Commands/Who.java:352-356`). `@mud` needs intermud (`:264-274`). | player; `acct` admin |
| WhoIs (WHOIS, FINGER) | `whois <name>`; `whois <name>@<mud>`; `whois @<mud>` | One match in full, several as a short list. The remote forms use intermud. | free | `@all` and `@coffeemuds` need I3 security (`Commands/WhoIs.java:68-70`). | player; I3 admin |
| Top (TOP) | `top [players/accounts] [previous]` | Top-ten boards for pride stats, this month or all time. | free | `accounts` only with the account system (`Commands/Top.java:185`). An unknown word returns true (`:199`). | player |
| Topics (TOPICS) | `topics` | Help topic index. | free | `securityCheck` is true (`Commands/Topics.java:70-74`). | player |
| Weather (WEATHER) | `weather [world]` | Current weather. `world` lists every accessible area, outdoors only. | free | Indoors, `world` is ignored (`Commands/Weather.java:55`). | player |
| WillQualify (WILLQUALIFY) | `willqualify [unique] [next/level/lo-hi] [class/allclass] [skill, type, domain or expertise; NO-prefix excludes]` | Abilities and expertises a class and race can get at each level in range. | free | Secret entries are hidden; masked entries need their mask. Only themed classes are listed for allclass (`Commands/WillQualify.java:54-173`, `:413-421`). | player |
| Whisper (WHISPER) | `whisper [target] <text>` | Speech to one visible person in the room. Others see only that a whisper happened. When riding, the words reach the other riders of that mount. | default | Words under four letters must equal the target's full name (`Commands/Whisper.java:66`). Text passes SAYFILTER (`:88`). | player |
| Yell (YELL, YELLTO, YELLAT, Y) | `yell <text>`; `yellto <name> <text>`; `yellat <name> <text>` | Heard in the room and in rooms behind open exits (`Commands/Say.java:127-135`). | default | Every word is upper-cased (`Commands/Yell.java:56-59`). The YELLAT verb text is malformed (Problems). | player |
| WizEmote (WIZEMOTE) | `wizemote <all/here/room id/area/clan/name> <text>` | Staff message to all players, a room, an area, a clan, or the first matching session. | free | `securityCheck` WIZEMOTE at location (`Commands/WizEmote.java:137-140`). | admin |
| View (VIEW) | `view [n] <item> from <shopkeeper>` | Shows shop stock (MSG_VIEW), one message per match. | default | Target must be a shopkeeper (`Commands/View.java:62-66`). | player |
| Value (VALUE, VAL, V) | `value [n] <item> with <shopkeeper>` | Offers items to a shopkeeper for appraisal (MSG_VALUE). | default | Items must be carried and unworn (`Commands/Value.java:89`). | player |
| Withdraw (WITHDRAW) | `withdraw <amount/all/item> from <banker/postmaster/librarian>` | Takes coins from an account, or a parcel from a post box. | default | Cannot be ordered (`Commands/Withdraw.java:161-164`). A librarian passes the shop check but nothing is ever found (Problems). A married character can use the partner's account (`:91-113`). | player |
| Unlock (UNLOCK, UNL, UN) | `unlock <direction/exit/item>` | Unlocks an exit and, if unlocked, reports the far side. Unlocks an item. | default | Target must be seen. Silent when the exit is already unlocked (`Commands/Unlock.java:77-80`). | player |
| TypeCmd (TYPE, =) | `type <text> into <item>`; in a console, `type <text>` | Writes text into a computer or terminal (MSG_WRITE). Extends `Go`. | default | Target must be `Technical` (`Commands/TypeCmd.java:102-106`). | player |
| Undress (UNDRESS) | `undress <whom> <item>` | Takes a worn item off a follower or a bound or held mob and puts it in your hands. | default | Refused in combat on either side. Target must follow your orders or be bound or held (`Commands/Undress.java:58-62`, `:77`). The item is the last word only (`:63-65`). | player |
| TrailTo (TRAILTO) | `trailto <room id/everyroom/everyarea/text> [flags]` | Path from here to a room, an area or a text match, or to every room or area. | free | `securityCheck` TRAILTO at location (`Commands/TrailTo.java:287-291`). | builder; admin |
| Up (UP, U) | `up` | Moves up. Autorun uses `run`, otherwise `walk` (`Commands/Up.java:52-57`). | Go | Players cannot use it on a ship (`Commands/Up.java:67-75`). | player |
| West (WEST, W) | `west` | Moves west, same pattern as Up. | Go | Players need a compass-type room (`Commands/West.java:67-75`). | player |
| UnLink (UNLINK) | `unlink <direction>` | Removes the exit in one direction from this room, by rewriting its words to `DESTROY ROOM <dir>` (`Commands/UnLink.java:52-55`). | free | Needs CMDEXITS at location (`:65-69`). | builder |
| Train (TRAIN, TR, TRA) | `train` (menu); `train <what> [teacher] [noprompt]` | Spends trains and practices on hit points, mana, movement, training points, an attribute or a class change. | default | Cannot be ordered (`Commands/Train.java:695-698`). Deep dive in b170. Teacher default is a problem (Problems). | player |
| Wimpy (WIMPY) | `wimpy <n/n%>` | Sets the flee threshold, as an absolute value or a percent of base hit points. | free | None (`Commands/Wimpy.java:57-63`). | player |
| Wake (WAKE) | `wake`; `wake <sleeping mob>` | Wakes the caller, or another visible sleeping mob in the room. | default | Target must be asleep. A noisy message is sent first (`Commands/Wake.java:94-98`). | player |
| Visible (VISIBLE, VIS) | `visible` | Cancels wizard invisibility and any effect that makes the caller invisible or hidden. | default out of combat; 0.25 in combat (fixed) | None (`Commands/Visible.java:117-127`). | player |
| Wear (WEAR) | `wear <item(s)> [on <location>]` | Wears, holds or wields, with the message chosen from the body location. | default | Core verb, deep dive in b170 (`Commands/Wear.java:59-87`). | player |
| Wield (WIELD) | `wield <item>` | Wields a weapon. With several matches it wields only the first that can be wielded (`Commands/Wield.java:67-80`). | default | Core verb, deep dive in b170. | player |
| Where (WHERE) | `where`; `where !`; `where <text>`; `where mob/item/room/exit <text>`; `where mobmask/itemmask <mask>`; `where area <name>`; `where #tag`; `where select:<MQL>`; `where update:<MQL>`; mortal `where [good/neutral/evil] [level]` | Staff: finds rooms, exits, items, mobs and shop stock. UPDATE previews a change and saves it on confirmation. Mortal: lists the best areas to explore. | free | Staff search needs WHERE for each room and area access (`Commands/Where.java:121`, `:491-493`). `where !` forces the mortal view (`:116-118`). | player (mortal view); admin |
| Transfer (TRANSFER) | `transfer [all] [item/items] <name> <destination>` | Moves characters or items to a room, a player, an area, the inventory, a space location or another MUD. | free | `securityCheck` TRANSFER at location (`Commands/Transfer.java:675-679`), and TRANSFER on both rooms (`:597-600`, `:638-642`). | admin |
| Unload (UNLOAD) | `unload <word>`; player form `unload <firearm>` | Staff: unloads or reloads a class, help, VFS, ini, area, user, faction, resource or file. Player: empties a firearm of its ammunition. | default | Staff words need LOADUNLOAD. CLASS and ALL need sysop (`Commands/Unload.java:63`, `:196`, `:288`). | admin; player form |
| WizInv (WIZINVISIBLE, WIZINV, NOWIZINV) | `wizinv [nocloak]`; `wizinv off`; `nowizinv` | Makes the caller invisible to players. Cloaked unless NOCLOAK. | free | `securityCheck` WIZINV at location (`Commands/WizInv.java:109-112`). | admin |
| Xml (XML) | `xml` | "This command is deprecated, and no longer functions." | free | `securityCheck` returns false, so it is hidden (`Commands/Xml.java:62-66`). | none |

Shipped staff groups that hold these flags (`coffeemud.ini:245-290`): GROUP_ASSISTANT gives TRANSFER; GROUP_ASSISTANT2 adds WHERE; GROUP_ASSISTANT3 and GROUP_IMPLEMENTOR add TRAILTO; GROUP_WIZI gives WIZINV and CLOAK; GROUP_SYSOP holds LOADUNLOAD, CMDAREAS, CMDPLAYERS, I3 and TRAILTO. GROUP_SUBOP grants several of the same flags by area (`AREA ...`). The shipped groups were read by grep; the full grant list was not checked.

### 2. Shared shapes

- **Message verbs.** View, Value, Withdraw, Unlock, TypeCmd, Undress, Wake and Whisper build a `CMMsg`, ask the room's `okMessage` and then `send`. A refusal goes to `postCommandRejection` (`Commands/Unlock.java:115-119`, `Commands/Whisper.java:150-153`). Failure texts go to `postCommandFail`.
- **Report verbs.** Who, WhoIs, Top, Vassals, Weather, Ver, Wealth, Worth, WizList, Topics and the mortal half of Where build text and send it with `tell`.
- **Direct editors.** Where UPDATE, Transfer, Unload, WizInv, Visible, TrailTo and UnLink change state at once and send no message. UnLink hands its work to `Destroy` by rewriting its words (`Commands/UnLink.java:52-55`).
- **Shop quantity grammar.** View and Value share one grammar (`Commands/View.java:68-83`, `Commands/Value.java:62-96`). A leading number above zero sets a maximum and turns the list into `all`. `ALL.x` and `x.ALL` become `ALL x`. Matches are walked with `.2`, `.3` suffixes. Withdraw reads `all` as `Integer.MAX_VALUE` (`Commands/Withdraw.java:68-69`).

### 3. Where: two commands in one

**Staff mode** (`Commands/Where.java:116-738`). It runs when the caller has WHERE for the room and did not type `!`.

- Plain `where` lists online characters and their rooms (`:127-156`).
- `where area <name>` restricts the search to one area (`:160-164`, `:472-476`).
- `where #tag` lists every tagged object (`:165-182`).
- `where select:<MQL>` runs `CMLib.percolator().doMQLSelectObjects` and prints each row (`:183-231`).
- `where update:<MQL>` runs `doMQLUpdateObjects`, prints OLD and NEW pairs, and asks "Save the above changes (y/N)?" (`:233-359`). On yes it writes changed rooms, items and mobs to the database (`:262-324`). An update that sets `class` to `null` destroys the object instead (`:262`).
- `where mobmask=...` and `where itemmask=...` use zapper masks (`:402-439`). The `2` variants pass the raw text to `maskCheck` (`:441-469`).
- Plain text matches a room's name or description, its exits (including door names), its items, its mobs and their carried items, and shop stock (`:381-727`, `:658-725`).
- Each room must pass `CMSecurity.isAllowed(mob,R,WHERE)`, and the caller must be able to access its area (`:491-493`).

**Mortal mode** (`Commands/Where.java:739-876`). This is a recommendation, not a search. It works in five steps.

1. An alignment word (good, neutral or evil) sets the reference alignment to the median faction value for that band (`:745-761`). Otherwise the caller's own alignment is used (`:741`).
2. A number adds to the caller's level (`:764`). The working level is `level + adjust` (`:768`).
3. For each accessible area that is not an instance child, the median is its player level, or its MED_LEVEL when that is zero (`:776-778`). The area enters the level list only if the median is within six levels of the working level (`:780-782`). Its value is `round(9 * median / level)` when the caller is at or above the median, otherwise `round(10 * level / median)` (`:783-788`). Areas outside the window score 0 and are dropped (`:817`).
4. Population (`Area.Stats.POPULATION`) goes into a second list (`:791`). The distance `|alignment - MED_ALIGNMENT|` goes into a third (`:793-795`).
5. Each list is kept in ascending order by `whereAdd` (`:55-69`). An area's score is the sum of its 1-based positions in the three lists (`:819-826`). The 15 highest scores are kept (`:839`), re-sorted by how far each area's median is from the caller's level (`:842-858`), and printed with name, level, alignment band and population (`:859-871`).

The alignment term as written adds more for a larger difference, so areas far from the chosen alignment rank higher (Problems, item 1).

### 4. Transfer: one verb, several destinations

The form is `transfer [ALL] [ITEM[S]] <name> <destination>`, and it needs at least three words (`Commands/Transfer.java:109-113`).

**Finding the thing to move.** Characters are searched in this order (`:232-293`): an online character, a character in the room, `name.N` in the caller's area, `name.N` in the world, then a saved character loaded from the database (`:290-293`). Items are searched in the room, then `name.N` in the area and the world, then space objects and ship items (`:166-200`, `:296-385`). When nothing matches and more words remain, the verb retries once with ITEM inserted (`:386-392`).

**Where it goes.** The destination is the first of these that matches:

- `inventory`: items move to the caller's inventory (`:398-400`, `:602-603`).
- `here` or `.`: the caller's room (`:403-405`).
- A remote target in the form `transfer X <remote name> @user$password$server[:port]` (`:407-514`). The port defaults to 27733 (`:433`). The code logs in over a raw socket, sends `BLOCK`, waits for `[OK ...]`, then sends `IMPORT <ITEMS>` or `IMPORT <MOBS>` with an XML copy (`:447-499`).
- `space:` followed by coordinates `x,y,z`, `A->B` (a point beside A on the side away from B, at A's radius plus the distance), or a space object name (`:515-569`). The default distance is 1000 (`:108`).
- A player's name: the room that player is in (`:571-572`).
- An area name (a random proper room, `:574-575`), a room ID (`:577-578`), a direction (`:580-581`), or a fuzzy text search through `CMLib.hunt().findWorldRoomLiberally(mob, text, "RIPME", 100, 120000)` (`:583`).

**Moving.** Items use `moveItemTo` and space objects stop (`:606-608`). Ships dock (`:609-610`). Characters are moved with `bringMobHere`, after leave and enter messages that use the caller's own transfer "poof" texts (`:648-653`). A player who is not in the game is simply given the new location (`:644-645`), and a player who arrives gets a look (`:654-655`). `fixSpaceCoords` pushes a destination out of the way of nearby space objects, by their radii plus the distance, in random directions (`:71-100`).

### 5. Unload: reloads, unloads and the user case

The staff word list is `CLASS, HELP, USER, AREA, FACTION, ALL, FILE, RESOURCE, INIFILE, ACHIEVEMENTS, [FILENAME], VFS, INI, SETTINGS, AWARDS` (`Commands/Unload.java:50-54`). Words in the list are only treated as staff words when the caller has LOADUNLOAD (`:63-82`). Anything else goes to the firearm path (`:105-135`), which makes ammunition with `makeAmmunition` and sends MSG_UNLOAD.

| Word | Effect | Guard and place |
|---|---|---|
| AREA | Empties each room, deletes each room and the area, and removes it from the map. Asks first. | CMDAREAS (`:137-193`) |
| CLASS | Removes a class from the class loader. Checks the object type. | sysop (`:195-234`) |
| HELP | Unloads the help file cache and refreshes the stock clan government descriptions. | HELP directory writable (`:236-247`) |
| VFS | Clears the virtual file cache. | (`:249-253`) |
| INI, SETTINGS, INIFILE | Reloads the ini, then channels and journals. | (`:255-274`) |
| ACHIEVEMENTS; AWARDS | Shuts the subsystem down. | (`:276-286`) |
| ALL | Clears all resources, resets security and system variables, unloads help. | sysop (`:288-297`) |
| USER/PLAYER | Saves, stops the session, removes the character and destroys it (see below). | `all` needs CMDPLAYERS (`:300-383`) |
| FACTION | Removes one faction, or all. | CMDFACTIONS (`:386-405`) |
| EXPERTISE | Recompiles the expertise list. | EXPERTISE (`:407-413`) |
| RESOURCE, FILE | Removes resource keys, with `@` path navigation. | (`:415-554`) |

**The user case** (`Commands/Unload.java:300-383`). The caller is asked "Save first (Y/n)?" (`:321`). Each target's session is stopped with escalating force. Each step waits up to 100 attempts of 100 ms (`:331-351`). The character is then removed from the player list and destroyed (`:373-377`). Before that, the save path calls `delAllEffects(true)` on the character (`:360`).

### 6. Who, WhoIs and Top: the public boards

**Who** (`Commands/Who.java`). The columns are race, class and level, then name, with widths 12, 12, 7 and 40 (`:57-65`). Columns for race, class or level disappear when the matching `DisFlag` is set (`:67-79`). Cloaked characters appear in parentheses and only to viewers with CLOAK or WIZINV security anywhere whose level is at least theirs (`:203-209`, `:179-189`). Characters with a soulmate are shown as the soulmate (`:221-223`). The default sort puts system operators first, then characters at or above the last player level, then the rest (`:277-306`). Characters marked looking-for-group get a note (`:135-136`, `:173-174`).

**WhoIs** (`Commands/WhoIs.java`). One match prints the full block, several print the short list (`:109-119`). A name is matched by prefix, case-insensitive, for characters with level above zero (`:97-107`). `whois <name>@<mud>` fingers a remote character (`:88-90`). `whois @all` and `whois @coffeemuds` query every MUD in turn and sleep for one second plus the slowest previous response between each (`:72-81`). That sleep runs in the command's own thread.

**Top** (`Commands/Top.java`). Two forms exist. The text form reads `resources/text/topplayers.txt` (or `-prev`) or `topaccounts.txt` and renders it through the web macro filter with a WIDTH value (`:209-234`). The XML form, used by other code, writes every `PrideStat` for every period (`:134-167`). The legacy table shows the top ten of six stats in two rows, for "This Month" and "All Time" (`:77-132`). Large numbers are shortened by `fixI`, for example 1234567 becomes `1234k` (`:63-75`).

### 7. WillQualify: a class's skill table

`WillQualify` (`Commands/WillQualify.java:259-444`) prints the table that a class and race will supply, level by level. It reads the same mapping that grants skills, so the table cannot drift from the rules.

- **Source.** For each level it walks the class's mappings and the race's mappings from `CMLib.ableMapper().getClassAbles(classID, true)` and `getClassAbles(raceID, false)` (`:74-76`). A mapping is included when its qualifying level equals the level (`:80`). SECRET mappings are skipped and MASKED ones must pass their mask (`:82-86`). The ability is created with `CMClass.getAbility` (`:88`).
- **Filters.** Ability type and domain words filter the list. An ability is listed only when its special skill limit for the caller is above zero (`:93-96`) and it is unique when UNIQUE was given (`:98`, `Commands/Skills.java:322-340`).
- **Expertises.** Each expertise qualifies at the larger of its own minimum level and the mapping's level (`:121-123`). Its cost text is printed (`:143`).
- **Output.** Columns are level, name and requirements, two per line. Auto-granted mappings are marked with `*`, and the footer explains the mark (`:105-107`, `:438`).
- **Input.** `WILLQUALIFY NEXT` means one level above the current class level (`:275-280`). A range such as `5-9`, or a single number meaning 0 to N, is parsed by `parseOutLevelRangeFromParm` (`:283-300`, `Commands/Skills.java:180-`). Words are matched against the type list, the domain list and the ability and expertise names, with spaces allowed as underscores (`:175-257`). A word starting with NO is an exclusion (`:337-343`).

### 8. TrailTo: route finder

`TrailTo` (`Commands/TrailTo.java:50-279`) wraps the tracking library. Each option is a word on the command line.

- Tracking filters are the `TrackingFlag` enum (`Libraries/interfaces/TrackingLibrary.java:1114-1135`), listed under Content. They restrict which exits and rooms a path may use.
- `TrailFlag` values CONFIRM (also write the result to the system log) and AREANAMES (`:1100-1104`).
- `RADIUS=n`, `MINSIZE=n` (which also sets NOHOMES), `IGNOREROOMS=a,b`, `ROOMIDS` (print room IDs along the path), and `JUSTTHEFACTS` (drop the label) (`:61-139`).
- `FALLBACK` drops the filters named on the line, one at a time in the order given, until a path appears (`:245-256`, `:160-172`, `:194-205`).
- Time limits come from the `maxSecs` argument of `getTrailToDescription`, which is 300 seconds for `everyarea` and 5 seconds per room or target (`:157`, `:193`, `:242`; `Libraries/interfaces/TrackingLibrary.java:275`).
- Typed from a session, the answer is printed. A trailing QUIETLY stores the answer in the caller's word list instead, so code can read it back (`:270-274`).

### 9. Titles

`Title` (`Commands/Title.java`) works on the player's title list. Index 0 is the current title (`:56`).

- A title in braces is locked and cannot be changed (`:57-61`). The `*` placeholder is replaced by the character's name when shown (`:89-92`). The title `*` alone means no title (`:89-90`).
- Choosing a number moves that title to the front (`:127-133`).
- `R` toggles the random flag of the current title (`:114-124`). A background pass runs `selectRandomTitle` for every character in the game and rolls one title from those marked random, then moves it to the front (`Libraries/CMPlayers.java:3485-3497`, `:3525-3545`).

### 10. Speech, banking and shops

**Whisper** (`Commands/Whisper.java:51-156`). Four cases.

- No target, not riding: a message to the room that says the caller whispers to himself or herself, with no words (`:96-106`). Others hear only that much.
- No target, riding: the caller whispers around the mount. Riders of that mount hear the words, others hear "whisper(s) something" (`:107-142`).
- A target: the target hears the words. The room hears "whisper(s) something to <target>" as quiet movement (`:144-154`, `:146-149`).
- Names are wrapped in a `WHISPER` markup tag for clients (`:147-148`).

**Yell** (`Commands/Yell.java:49-71`, `Commands/Say.java:117-135`). Yell calls `Say.exec` with multi-room on (`Yell.java:70`), so the line is also heard in each room behind an open exit (`Say.java:127-135`). `yellto` and `yellat` set the verb forms (`Yell.java:53-68`). Say uses the target verb form when a target is named (`Say.java:334`).

**Withdraw** (`Commands/Withdraw.java:49-146`). Banks are `Banker` shops and post offices are `PostOffice` shops. The caller's account name comes from `getBankClientName` or `getSenderName` (`:76`, `:118`). When the caller has no balance and is married to a liege, the partner's account is tried (`:91-113`). `isMarriedToLiege` is true only when the liege relation is mutual (`MOBS/StdMOB.java:984-996`).

**View and Value** share the quantity grammar described in section 2 (`Commands/View.java:49-117`, `Commands/Value.java:49-112`). Each sends its shop message to the shopkeeper, and the shop's own code decides the price or the description.

### 11. Invisibility, cloaks and Visible

- **WizInv** (`Commands/WizInv.java:49-100`). Adds the `Prop_WizInvis` effect with NOT_SEEN and CLOAKED bits. NOCLOAK keeps only NOT_SEEN (`:57-60`). The effect's misc text is set to UNINVOKABLE so ordinary removal cannot clear it (`:91`). Turning it off uninvokes the effect (`:61-68`).
- **Who, Where and WhoIs filter cloaked characters.** A cloaked character is shown only to viewers with CLOAK or WIZINV security anywhere whose level is at least the target's (`Commands/Who.java:203-209`, `Commands/Where.java:71-81`, `Commands/WhoIs.java:100-104`).
- **Visible** (`Commands/Visible.java:48-115`). It first runs `WIZINV OFF` if the caller has the effect (`:78-89`). Then `returnOffensiveAffects` tries each uninvokable effect alone on a scratch mob and keeps the ones that make it invisible or hidden (`:48-72`). Each kept effect is removed through Skill_Revoke, which sends a MSG_THINK check first (`:98-110`).

### 12. Short verbs

- **Wake.** With no argument it wakes the caller with MSG_SIT and MSG_STAND (`Commands/Wake.java:53-75`). With a target it sends a noisy "attempt(s) to wake" message, then runs the same self path as the target with the ORDER flag (`:94-98`).
- **Undress.** Only a follower or a bound or held mob can be undressed. The item must be worn and the target not in combat. The worn item moves off the target and is picked up by the caller (`Commands/Undress.java:58-111`).
- **Unlock.** Finds the exit by direction or name, or an item. An exit sends one message to the room and the paired exit gets the same message. The far room is told when both sides are unlocked (`Commands/Unlock.java:60-112`).
- **TypeCmd.** Extends `Go` but is only about typing. Inside a console it types into the console. Otherwise it needs "into" followed by an item in the room or in an open container (`Commands/TypeCmd.java:54-96`).
- **Wimpy.** A percent of base hit points, or an integer (`Commands/Wimpy.java:57-63`). Score shows the value (`Commands/Score.java:363`). The death-guard prayers compare hit points with 1.30 times the value (`Abilities/Prayers/Prayer_DeathGuard.java:102`). The automatic flee trigger was not found in this search.
- **WizEmote.** Targets by `all`, `here`, room ID, area prefix, clan name, or the first matching name or area (`Commands/WizEmote.java:54-123`).
- **Vassals.** Lists the players whose liege field names the caller (`core/database/MOBloader.java:1826-1838`).
- **Train, Wear, Wield.** These are covered in b170. The Train details that are new are listed under Problems.

## Content worth mining

**Route filters** (`Libraries/interfaces/TrackingLibrary.java:1114-1135`). Twenty-three named predicates that a path may be restricted by: NOHOMES, OPENONLY, UNLOCKEDONLY, PASSABLE, AREAONLY, NOTHINAREAS, NOHIDDENAREAS, NOEMPTYGRIDS, NOAIR, NOPRIVATEPROPERTY, NOWATER, WATERSURFACEONLY, WATERSURFACEORSHOREONLY, SHOREONLY, UNDERWATERONLY, FLOORSONLY, CEILINGSSONLY, NOCLIMB, NOCRAWL, OUTDOORONLY, INDOORONLY, DRIVEABLEONLY, WILDERNESSONLY.

**Pride stats** (`Common/interfaces/PrideStats.java:51-60`). PVPKILLS, AREAS_EXPLORED, ROOMS_EXPLORED, EXPERIENCE_GAINED, MINUTES_ON, QUESTS_COMPLETED, QUESTPOINTS_EARNED. The legacy top page omits AREAS_EXPLORED; the XML form includes it.

**Time periods** (`Common/interfaces/TimeClock.java:736-744`). HOUR, DAY, WEEK, MONTH, SEASON, YEAR, ALLTIME.

**Training messages** (`Commands/Train.java:563-631`). "You feel more powerful!" (mana), "You feel more rested!" (movement), "You feel more trainable!" (training points), "You feel more educatable!" (practices), "You feel even healthier!" (hit points), then one line per attribute ("You feel stronger!", "You feel smarter!", "You feel more dextrous!", "You feel healthier!", "You feel more charismatic!", "You feel wiser!"), and "You have undergone <class> training!" for a class change.

**Ability type words** (`Abilities/interfaces/Ability.java:706-711`). SKILL, SPELL, PRAYER, SONG, TRAP, PROPERTY, THIEF SKILL, LANGUAGE, CHANT, COMMON SKILL, DISEASE, POISON, SUPERPOWER, ARCHON SKILL, TECH SKILL. Domains are listed from `Ability.java:893` and begin with NOTHING, DIVINATION, ABJURATION and ILLUSION.

**Shop and bank quantity forms.** `N item`, `all`, `all.item`, `item.all`, `item.2`, `all item` (see section 2).

**Short messages** (for tone). "You don't see '<x>' here." (Unlock, Wake, TypeCmd), "Undress whom? What would you like to remove?" (`Commands/Undress.java:55`), "Not while you are in combat!" (`:60`), "@x1 won't let you." (`:114`), "Huh" style "Transfer whom where? Try all or a mob name..." (`Commands/Transfer.java:111`), "You can't find anyone or anywhere by that name." (`Commands/WizEmote.java:122`), "That doesn't appear to be available. Try LIST." (`Commands/Withdraw.java:135`), "You are Broke!" (`Commands/Worth.java:64`), "You feel more educatable!" (Train).

**Staff groups** (`coffeemud.ini:245-290`). The shipped groups, from least to most power: GROUP_TRAVELER (GOTO), GROUP_WIZI (WIZINV, CLOAK), ASSISTANT (TRANSFER and others), ASSISTANT2 (adds WHERE), ASSISTANT3 and IMPLEMENTOR (add TRAILTO), SYSOP (adds LOADUNLOAD, CMDAREAS, CMDPLAYERS, I3).

## Notable design ideas

- **A recommendation that needs no map search.** Mortal `where` ranks areas with three cheap lists and a window of six levels (`Commands/Where.java:765-871`). It answers "where should I go" without any pathfinding.
- **One destination resolver.** Transfer tries a fixed sequence of interpretations for its destination: inventory, here, remote, space, player, area, room ID, direction, fuzzy text (`Commands/Transfer.java:397-583`). Each step is a few lines, and the order makes the result predictable.
- **The skill table comes from the rules.** WillQualify reads the same mappings the classes use (`Commands/WillQualify.java:74-98`). Help output cannot disagree with the rules.
- **Filters as predicates, with fallback.** `TrackingFlag` implements `RFilter` (`Libraries/interfaces/TrackingLibrary.java:1114`). TrailTo's FALLBACK drops filters in the order given until a path exists (`Commands/TrailTo.java:245-256`), so a route degrades instead of failing.
- **Find the cause by testing each cause alone.** Visible puts each effect on a scratch mob and sees which ones hide it (`Commands/Visible.java:48-72`). It then removes only those, through a skill the caller must pass (`:98-110`).
- **Quiet speech.** A whisper tells the room only that it happened (`Commands/Whisper.java:146-149`). Riding a mount whispers to fellow riders (`:107-142`).
- **Spreading speech.** Yell carries into rooms through open exits (`Commands/Say.java:127-135`).
- **Titles as templates.** A title can contain `*` for the name, a whole title can be locked in braces, and a random pool rotates the current title (`Commands/Title.java:57-92`, `Libraries/CMPlayers.java:3485-3497`).
- **Undress needs cooperation.** Only a follower or a bound or held mob can be stripped, which makes a rule of consent out of a verb (`Commands/Undress.java:77`).
- **Unlock mirrors.** Unlocking one side sends the same message through the paired exit (`Commands/Unlock.java:93-102`).
- **Offline transfer is silent.** A player who is not in the game is moved by setting the location, without leave or arrival messages (`Commands/Transfer.java:644-645`).

## Problems and cruft

Each item was confirmed by reading the code unless it says "unverified".

1. **Where, alignment sign.** The alignment list is sorted by `|difference|` ascending (`Commands/Where.java:793-795`), and the score adds the 1-based index (`:824-826`). A larger difference therefore scores higher. Whether this is intended is unverified.
2. **Where, MOBMASK2 and ITEMMASK2 order.** The mask description is computed before the keyword is removed (`:445` and `:460`, before the strip at `:453` and `:468`). The "anyone" guard at `:446` and `:461` therefore tests the description of the whole typed line. How `maskDesc` treats the keyword is unverified.
3. **Where, dead branch.** Both arms of the `roomOnly||exitOnly` ternaries are the same (`:471`, `:474`).
4. **Transfer, intermud copies.** The remote send writes an XML copy and never removes or destroys the local object (`Commands/Transfer.java:451-499`). Whether the remote side or another path removes the original is unverified.
5. **Transfer, plain-text credentials.** The login line with the password is written to a raw socket with no encryption in sight (`:447`). The password is typed on the command line.
6. **Transfer, two messages on failure.** After an I/O failure in the intermud branch, the catch prints the error (`:506-509`). Control then falls through to "Transfer where? ... is unknown." (`:585-589`).
7. **Transfer, blocking search.** The fuzzy world search can run for up to 120 seconds on the command's thread (`:583`).
8. **Unload, self-target.** The caller gets "Can't unload yourself" (`:353-354`), but the save and `delAllEffects(true)` steps after it still run for the caller (`:356-362`). The effect on a live caller is unverified.
9. **Unload, prefix.** `"EXPERTISE".startsWith(word)` means any leading letters of the word trigger an expertise reload (`:407`).
10. **Withdraw, librarians never work.** The shop check admits librarians (`Commands/Withdraw.java:57`), but neither the banker nor the post-office branch handles them (`:74-131`). `StdLibrarian` is neither a `Banker` nor a `PostOffice` (`MOBS/StdLibrarian.java:42`). The result is always "That doesn't appear to be available" (`:133-137`).
11. **Unlock, silent no-op.** Unlocking an already unlocked exit prints nothing (`Commands/Unlock.java:79`).
12. **TypeCmd, double failure.** An unknown "into" target posts "You don't see ..." (`Commands/TypeCmd.java:94`), then the code falls through and posts it again (`:115`).
13. **Yell, malformed verb.** `tverb = averb + L("yell(s) at")` gives "yell(s)yell(s) at" (`Commands/Yell.java:66`, with `averb` set at `:54`). The verb is used directly for a targeted yell (`Commands/Say.java:334`), so the malformed text reaches the room.
14. **Yell, all-caps text.** Every word is upper-cased before the speech is built (`Commands/Yell.java:56-59`). This may be intended, but it is not documented.
15. **Visible, duplicate and fixed cost.** `recoverRoomStats` is called twice (`Commands/Visible.java:112-113`). The combat cost is a fixed 0.25 (`:126`), outside the `DEFCOMCMDTIME` scheme.
16. **WizInv, no null check.** `recoverRoomStats` is called on `mob.location()` without a null test (`Commands/WizInv.java:94`). The show call on the line before is guarded (`:83`).
17. **WizEmote, one recipient.** For a name or area match the loop breaks after the first session (`Commands/WizEmote.java:116-118`). Only one person receives the message.
18. **WizEmote, clan branch.** The clan branch does not test `S.mob().location()` for null before `isAllowed` (`:99-101`). Whether `isAllowed` tolerates null is unverified.
19. **WillQualify, NO prefix.** Any typed word starting with NO is treated as an exclusion (`Commands/WillQualify.java:337-343`). The domain NOTHING (`Abilities/interfaces/Ability.java:893-894`) therefore cannot be requested as a positive word.
20. **Train, any inhabitant is a teacher.** With no named teacher, the first other inhabitant of the room is used (`Commands/Train.java:340-348`). The check does not test for a trainer. See b170 Problems 7 and 8 for the rest of the train path.
21. **Train, prefix order.** Typing `m` matches MANA before MOVES, because the enum order decides (`Commands/Train.java:301-315`). This is minor.
22. **WhoIs, sleeps on the command thread.** `CMLib.s_sleep(time + 1000)` runs between MUDs (`Commands/WhoIs.java:75`).
23. **Undress, word removal.** `commands.remove(what)` removes the first equal word, which may not be the last one if the target's name repeats it (`Commands/Undress.java:64-65`). This is minor.
24. **Top, return value.** An unknown word prints a message and returns true (`Commands/Top.java:198-199`), where other failures return false. This is minor.
25. **Who and Top are multiplayer.** They list other people by design, and the account view exposes account names (`Commands/Who.java:352-413`).

## Takeaways for a Python SUD

- **One resolver for "N item", "all", "item.2" and "from X".** Write it once and use it for buy, sell, view, withdraw and drop. The shop grammar in `Commands/View.java:68-83` is a complete specification.

  ```python
  def parse_quantity(words):
      """'3 sword' -> (3, 'sword', None); 'sword.2' -> (None, 'sword', 2)."""
      count = int(words.pop(0)) if words and words[0].isdigit() else None
      name, _, idx = " ".join(words).rpartition(".")
      if idx.isdigit():
          return count, name, int(idx)
      return count, " ".join(words), None
  ```

- **Keep a recommendation, but fix its sign.** The three-list rank sum with a six-level window (`Commands/Where.java:780-858`) is a good "where next" hint for a single player. Rank alignment closeness, not distance, and keep the window.
- **Store skill tables as data.** Keep `class -> level -> [skill, auto-granted]` in a file, and let a `willqualify`-style command print a range from it. The printed form is `[Lvl] Name Requires`.

  ```python
  def qualifies(table, cls, lo, hi):
      for level in range(lo, hi + 1):
          for skill, auto in table.get(cls, {}).get(level, []):
              yield level, skill, auto
  ```

- **Route filters as named predicates, with a fallback.** A dict of predicates, a breadth-first search with a time budget, and an ordered list of filters to drop when no path exists. Use the 23 names as a starting list, and drop the water, air and property filters if the SUD has no such terrain.
- **Titles as a list with index 0 current.** Use `*` for the name, a locked flag for braces, and a random flag per title, rolled by the game tick rather than by a service thread.
- **Drop the multiplayer verbs.** Who, WhoIs, Top, Vassals, WizList, the `@mud` and `@all` forms, WizEmote, Transfer to another MUD, Yell and Whisper aimed at other people, and Unload of users and sessions have no single-player use.
- **Keep the debugging tools behind a flag.** Transfer (room and area only), Where (text and tag search), Unload (data reloads) and Visible (undo a status effect) are worth having for a developer. Keep them out of the player's verb list.
- **Keep consent and the two-message rule.** Undress's rule (only followers or bound targets) and the message-then-effect split used across the bucket are both worth copying. Do not copy the teacher default, the NO-prefix rule or the intermud copy.

## Coverage

- **Read in full (36 of 36).** Every bucket file: `Commands/Where.java`, `Transfer.java`, `Unload.java`, `Who.java`, `WillQualify.java`, `TrailTo.java`, `Top.java`, `Whisper.java`, `Withdraw.java`, `Title.java`, `WizEmote.java`, `Unlock.java`, `TypeCmd.java`, `View.java`, `Undress.java`, `Visible.java`, `Value.java`, `WhoIs.java`, `Wake.java`, `Wealth.java`, `WizInv.java`, `WizList.java`, `Vassals.java`, `Yell.java`, `Wimpy.java`, `Ver.java`, `Weather.java`, `Up.java`, `West.java`, `Worth.java`, `Topics.java`, `UnLink.java`, `Xml.java`, `Train.java` (read in two parts; deep dive in b170), `Wear.java` and `Wield.java` (core verbs, b170).
- **Read in part (for interactions).** `Commands/Skills.java` (level-range parser, UNIQUE flag, uniqueness test); `Commands/Say.java` (`exec`, multi-room, verb forms, cost); `Commands/Go.java` (cost, `:261-276`); `Commands/Destroy.java` (direction branch, `:360-470`); `Libraries/interfaces/TrackingLibrary.java` (enums and the `getTrailToDescription` signature); `Libraries/interfaces/WorldHuntLibrary.java` (`findWorldRoomLiberally`); `core/CMProps.java` (cost getters); `core/CMSecurity.java` (flag names); `MOBS/StdMOB.java` (`isMarriedToLiege`, liege fields); `core/database/MOBloader.java` (`vassals`); `Libraries/CMPlayers.java` (random title roll); `Common/DefaultPlayerStats.java` (`getTitleRandom`); `Abilities/interfaces/Ability.java` (type and domain lists); `Common/interfaces/PrideStats.java`, `Common/interfaces/TimeClock.java` (enums); `core/collections/XVector.java` (null handling, checked for Wake); `coffeemud.ini` (cost defaults and staff groups).
- **Grep only.** `Commands/At.java` and `Commands/ATopics.java` (`securityCheck` only); `Commands/Inventory.java` (no cost override); `Commands/Shutdown.java`; `Commands/Score.java` and the death-guard prayers (for the wimpy value); `Items/interfaces/Wearable.java` (body-location names, not listed).
- **Not read.** `Commands/Command.java` and `Commands/StdCommand.java` in full (covered by b170); `Libraries/AutoAwards.java` (title awards); `Libraries/interfaces/AbilityMapper.java` (mapping fields are taken from call sites); `doTopics` in `Commands/ATopics.java`.
- **Not verified.** The flee trigger for Wimpy; the sign intent of the alignment term; the effect on a live caller of Unload's self-branch; and the null behaviour of `CMSecurity.isAllowed` for WizEmote's clan branch.
