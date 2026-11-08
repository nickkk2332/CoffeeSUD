# Commands, part 2: CALENDAR, CATALOG, CHARGEN, channels and the clan verbs

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b111` (Commands [part 2/12: CalendarCmd .. ClanVote]) · 28 files · 7801 lines of Java.

## Summary

These 28 classes are typed verbs for five areas: the calendar (`CALENDAR`), the builder catalog tool (`CATALOG`), character generation and combat simulation (`CHARGEN`), chat (every channel name is its own verb, plus `CHANNELS` and `CHANWHO`), and clan governance (21 `CLAN*` verbs). Most clan verbs are thin front ends. Each finds the member's clan and role, asks the clan whether that role may perform the function (now, or by starting a ballot), and then changes state. Each verb re-implements its own argument parsing and prompt chain, which is where most of the defects in this bucket sit. `CHARGEN COMBAT` is the most distinctive piece: it pits a class against a reference opponent for levels 1 to 91 and prints percentile statistics, so the game carries its own balance harness. This chapter catalogues the verbs. The engines behind them are covered in `NEWOVERVIEW/society/b027-clans-and-clan-governments.md` (clans), `NEWOVERVIEW/society/b033-journals-channels-and-socials.md` (channels, journals, calendar) and `NEWOVERVIEW/commands/b170-commands-the-base-class-and-the-core-verbs.md` (the `Command` base).

## Key files and types

Paths are relative to the CoffeeMud folder. Java files in `Commands/` omit the `com/planet_ink/coffee_mud/` prefix. Java files elsewhere are given in full.

| File | Role | Lines |
|---|---|---|
| `Commands/CharGen.java` | `CHARGEN`: averaged reference character, class-vs-mob combat simulator, weapon preview, starter gear | 1546 |
| `Commands/CalendarCmd.java` | `CALENDAR`: list windows of events, add (prompted or direct), remove | 977 |
| `Commands/Catalog.java` | `CATALOG`: builder tool for the mob and item catalog and room catalog upkeep | 936 |
| `Commands/Channel.java` | Dispatcher for every channel name used as a verb: send, backlog, search, undo, off | 381 |
| `Commands/ClanDues.java` | Set annual dues, list arrears, forgive a member | 309 |
| `Commands/ClanCreate.java` | Found a clan: cost, name, government style, charge | 280 |
| `Commands/ClanVote.java` | List, view, cast or cancel ballots; runs a passed ballot | 253 |
| `Commands/ClanAssign.java` | Change a member's role, with seat caps and a last-officer guard | 209 |
| `Commands/ClanDeclare.java` | Set the clan's stance toward another clan; one mud month between changes | 191 |
| `Commands/Channels.java` | `CHANNELS`: list, ON, OFF, COPY a mask from another player | 185 |
| `Commands/ClanAccept.java` | Move applicants (one or All) to the accept position | 185 |
| `Commands/ClanQual.java` | Set the clan's acceptance (qualification) mask | 178 |
| `Commands/ClanReject.java` | Remove applicants | 171 |
| `Commands/ClanApply.java` | Apply to join: family, category cap, masks, class, level, size | 165 |
| `Commands/ClanTax.java` | Set the experience tax (0 to 25 percent on the prompt path) | 163 |
| `Commands/ClanExile.java` | Remove a member, running the exit script | 161 |
| `Commands/ClanList.java` | `CLANLIST` / `CLANS`: public clans with status and war flag | 159 |
| `Commands/ClanMOTD.java` | Set or clear the clan message of the day (a journal entry) | 152 |
| `Commands/ClanPremise.java` | Set the clan premise text | 145 |
| `Commands/ClanKills.java` | Top members by rival-clan kills (mob and player) | 129 |
| `Commands/ClanPVPKills.java` | Top members by rival-clan player kills | 129 |
| `Commands/ClanMorgueSet.java` | Set the clan morgue to the current room (must be owned by the clan) | 128 |
| `Commands/ClanDonateSet.java` | Set the clan donation room | 127 |
| `Commands/ClanHomeSet.java` | Set the clan home (recall) room | 127 |
| `Commands/ClanResign.java` | Leave a clan after confirmation | 125 |
| `Commands/ChanWho.java` | `CHANWHO`: who would hear a channel, locally or on an intermud `@mud` | 117 |
| `Commands/ClanDetails.java` | `CLANDETAILS` / `CLAN`: clan details; switches the default clan | 100 |
| `Commands/Chants.java` | `CHANTS`: lists known chants by domain (subclass of `Skills`) | 73 |

Principal engine types these verbs call: `Common/interfaces/Clan.java` (roles, `Function`, `Authority`, statuses, relations), `Common/DefaultClan.java`, `Libraries/Clans.java` (`goForward`, the ballot gate), `Libraries/CMChannels.java`, `Libraries/CMJournals.java` (calendar entries and recurrence), `Libraries/CMCatalog.java` and `Libraries/interfaces/CatalogLibrary.java` (`CataSpawn`, `CataData`).

## How it works

### Shared command contract

- Every verb extends `StdCommand` (`Commands/StdCommand.java:35`). Access words are the verb (`CHARGEN`), a list (`CLANLIST`, `CLANS`), or, for `Channel`, the names of all configured channels (`Commands/Channel.java:42-46`). `I()` passes each word through the language table (`StdCommand.java:78-83`), so command words can be translated.
- `execute(mob, commands, metaFlags)` receives the typed words with the verb at index 0. Most verbs remove the verb and parse positionally. The last word is often the value, and a clan name is whatever text sits between verb and value (`Commands/ClanTax.java:55-63`, `Commands/ClanAccept.java:57-58`). Clan names match by `CMLib.english().containsString`, and the first membership that matches wins with no prompt (`Commands/ClanVote.java:68-76`).
- Monsters: `CHARGEN` (`Commands/CharGen.java:1418`) and `CLANAPPLY` (`Commands/ClanApply.java:56`) return immediately. `CLANVOTE` (`Commands/ClanVote.java:84`) and `CHANTS` (`Commands/Chants.java:62`) skip output only.
- Cost: no verb here overrides `actionsCost`, so it uses the `CMProps.getCommandActionCost` default of 0 (`StdCommand.java:224-227`). `Channel` alone overrides the combat cost (`Commands/Channel.java:376-380`).
- Restrictions: `securityCheck` is overridden only by `CHARGEN` (`Commands/CharGen.java:1542-1545`, flag `CHARGEN`) and `CATALOG` (`Commands/Catalog.java:932-935`, flag `CATALOG`). `canBeOrdered()` returns false for 19 verbs: `CATALOG` and 18 of the 21 clan verbs. The other nine return true. The ORDER path itself is outside this bucket.
- Multi-step input uses `InputCallback` chains. The verb sets a prompt on the session and returns; the next line typed calls `callBack()`, which sets the next prompt (`Common/DefaultSession.java:1748-1758`). `CLANCREATE` nests five levels this way (`Commands/ClanCreate.java:94-270`), and `CALENDAR ADD` nests four (`Commands/CalendarCmd.java:667-960`).

### Catalogue of verbs

Syntax is abbreviated. "Ballot" means the verb may be turned into a clan vote when the actor's role lacks the authority (see the clan section). Scope says who normally uses it: **builder/admin** verbs are marked.

| Verb | Syntax (abbreviated) | What it does | Restrictions | Scope |
|---|---|---|---|---|
| `CHARGEN` | `CHARGEN [NEW] [PLAYER] <class> <level>`; `CHARGEN ALL <level>`; `CHARGEN EQUIP <name>`; `CHARGEN WEAPON ...`; `CHARGEN COMBAT ...` | Averages many generated characters into a reference MOB and prints its score block; `NEW` puts one in the room; `EQUIP` re-equips a player; `WEAPON` previews weapon stats; `COMBAT` runs the simulator | `CHARGEN` security flag; monsters refused; orderable | **builder/admin** |
| `CALENDAR` | `CALENDAR [NOW\|SOON\|RECENT]`; `CALENDAR NEXT\|PAST [n] [period]`; `CALENDAR ADD [clan]`; `CALENDAR REMOVE <subject>` | Lists events by window. ADD is prompted or takes four arguments. REMOVE deletes an entry you own, or one your clan authority covers | ADD for a clan may need a ballot (`CREATE_CALENDAR`); SYSTEM entries need the `JOURNALS` flag; orderable | player; system entries admin |
| `CATALOG` | `CATALOG <name>\|SELF`; `CATALOG ROOM\|AREA\|WORLD [MOBS\|ITEMS]`; `CATALOG LIST\|CATEGORY\|DELETE\|EDIT\|SCAN\|DBSCAN\|OVERLOOK\|DBOVERLOOK\|CLEAN ...` | Adds a mob or item in the room to the catalog; auto-catalogs a room, area or world; lists, edits or deletes entries; reports or cleans mismatches between world and catalog | `CATALOG` security flag; not orderable | **builder/admin** |
| Channel names (see the table in Content) | `<CHANNEL> <message>`; `<CHANNEL> last [n]`; `<CHANNEL> search:<text>`; `<CHANNEL> undo`; `<CHANNEL> on\|off`; bare `<CHANNEL>` | Sends a message; replays backlog (default 10, at most 100 unless privileged); searches backlog; deletes your own last message; turns the channel off by handing off to `NoChannel`. Bare name shows the last 5 | Mask per channel; `QUIET` blocks; READONLY, PLAYERREADONLY, ARCHONREADONLY, CLANONLY flags; combat cost; orderable | player; WIZ-type channels are sysop by mask |
| `CHANNELS` | `CHANNELS`; `CHANNELS ON`; `CHANNELS OFF`; `CHANNELS COPY <player>` | Lists audible channels with on or off state. ON clears all filters. OFF sets every bit. COPY copies another player's mask after a Y/N prompt | orderable | player |
| `CHANWHO` | `CHANWHO <channel>`; `CHANWHO <channel>@<mud>` | Lists online players who would hear your messages on the channel. With `@mud` asks the intermud library | Cloaked players hidden unless you hold `CLOAK` or `WIZINV` and are at least their level; orderable | player; visibility is admin |
| `CLANCREATE` | `CLANCREATE` (prompts only) | Founds a clan. Checks cost, takes Y/N, name (30 characters, not a player name, not `All`), government style, and the founder's qualification for that style. Charges and creates the clan as PENDING | Cost `CLANCOST` (default 10000, `coffeemud.ini:1655`); one clan per category (`MAXCLANCATS=1`, `coffeemud.ini:1664`); not orderable | player |
| `CLANAPPLY` | `CLANAPPLY <clan>` | Applies to join. Checks family-only, category cap, basic requirement mask, class gate, acceptance mask, `MINCLANLEVEL`, `MAXCLANMEMBERS`. Auto-accepts if the auto position is the accept position, else waits for review | Not for monsters; not orderable | player |
| `CLANACCEPT` | `CLANACCEPT <name>\|All [clan]` | Moves applicants to the accept position and runs the government's entry script (`CLANENTRY`) | `ACCEPT` authority or ballot; not orderable | clan officer |
| `CLANREJECT` | `CLANREJECT <name>\|All [clan]` | Removes applicants. This deletes membership; it does not ban | `REJECT` authority or ballot | clan officer |
| `CLANASSIGN` | `CLANASSIGN <member> <role> [clan]` | Changes a member's role. Demotes seat-holders when a cap is full. Refuses to demote the last top officer | `ASSIGN` authority or ballot | clan officer |
| `CLANEXILE` | `CLANEXILE <member> [clan]` | Removes a member and runs the exit script (`CLANEXIT`) | `EXILE` authority or ballot | clan officer |
| `CLANRESIGN` | `CLANRESIGN [clan]` | Leaves a clan after a Y/N prompt, running the exit script | Any member | member |
| `CLANDECLARE` | `CLANDECLARE [your clan] <other clan> <WAR\|HOSTILE\|NEUTRAL\|FRIENDLY\|ALLY>` | Sets your clan's stance toward another. Both clans must be rivalrous. One mud month between changes | `DECLARE` authority or ballot | clan officer |
| `CLANDUES` | `CLANDUES [clan]`; `CLANDUES [clan] <amount>`; `CLANDUES FORGIVE <name> [clan]` | Shows dues and arrears; sets annual dues (in the bank currency unless you hold the role directly); forgives arrears | `TAX` authority; the clan needs a bank account to collect (`ClanDues.java:147-152`) | clan officer |
| `CLANTAX` | `CLANTAX [clan] <0-25>` | Sets the experience tax as a percent | `TAX` authority or ballot. The 0 to 25 check applies only on the prompt path (see Problems) | clan officer |
| `CLANVOTE` | `CLANVOTE`; `CLANVOTE <n>`; `CLANVOTE [clan] <n>` | Lists ballots you may vote on, shows one, casts Y or N. The starter may cancel | Voting rights come from `VOTE_ASSIGN` or `VOTE_OTHER`; not orderable | member |
| `CLANQUAL` | `CLANQUAL [clan] [mask]` | Sets the acceptance mask in zapper syntax, after a preview and a confirm. `?` shows mask help | `PREMISE` authority or ballot | clan officer |
| `CLANPREMISE` | `CLANPREMISE [clan] [text]` | Sets the public premise | `PREMISE` authority or ballot | clan officer |
| `CLANMOTD` | `CLANMOTD [clan] [text]`; `CLANMOTD [clan] DELETE` | Sets one message of the day, stored as a journal entry, or clears it | Mixed function codes; clear skips the ballot (see Problems) | clan officer |
| `CLANLIST` / `CLANS` | `CLANLIST` | Table of publicly listed clans: type, members, status (Active, Pending, Inactive), war flag | Orderable | player |
| `CLANDETAILS` / `CLAN` | `CLANDETAILS [clan]` | Shows the clan's detail text; if you belong, makes that clan your default | Not orderable | player |
| `CLANKILLS` | `CLANKILLS [clan]` | Top members of the clan by rival-clan kills (mob and player) | Must be a member | player |
| `CLANPVPKILLS` | `CLANPVPKILLS [clan]` | As CLANKILLS, player kills only | Must be a member | player |
| `CLANMORGUESET` | `CLANMORGUESET [clan]` | Sets the morgue to your current room | Clan must be ACTIVE; room must be owned by the clan; `SET_HOME` authority or ballot | clan officer |
| `CLANDONATESET` | `CLANDONATESET [clan]` | Sets the donation room to your current room | As above, with `SET_DONATE` | clan officer |
| `CLANHOMESET` | `CLANHOMESET [clan]` | Sets the clan home (recall) room | As above, with `SET_HOME` | clan officer |
| `CHANTS` | `CHANTS [chant or domain]` | Lists known chants grouped by domain, or details one chant (logic in `Skills`) | Orderable | player |

### CHARGEN: the averaged character and the combat simulator

**Averaged character.** `CHARGEN <class> <level>` does not build a character from rules. It builds many throwaway characters and averages them.

- `levelMOBup` (`Commands/CharGen.java:166-233`) creates a placeholder human named "Average Joe" (`:170`). Every base stat is 10, and STR, WIS and DEX are 11 (`:175-179`). HP is 20, movement 100 and mana 100 (`:185-187`).
- It then levels the mob with `CMLib.leveler()` (`:215-218`). On every third level (`lvl % 3 == 0`, `:197`) it adds one point to whichever stat has the most headroom to its cap (`:198-213`). Ties go to the later stat because the test is `>=`.
- It equips the mob (`:223`), sets every known ability to proficiency 100 (`:224-227`) and restores it to full (`:231`).
- `AverageClassMOB` (`:264-282`) takes `numTries` samples and divides by the count (`averageout`, `:235-248`). Without `NEW` that count is 100 (`:1514`). `CHARGEN NEW` uses one sample and places the result in the room (`:1514`, `:1522-1523`).
- `AverageAllClassMOB` (`:284-312`) is used for `CHARGEN ALL`. It loops 20 rounds over every available class, sampling 40 times for each (`:1516`), then adds the class averages together (see Problems for the divisor).
- Output is a score block from `CMLib.commands().getScore` (`:1526-1528`).

**Starter gear.** `equipPlayer` (`:49-164`) gives an item to every wearable slot except inventory, held, mouth and floating-nearby slots (`:53-58`, `:130`). Each is a `GenWeapon` or `GenArmor` of weight 8 at the mob's level (`:122-126`, `:156-159`). The weapon and the material follow the class's `allowedWeaponLevel()` and `allowedArmorLevel()` codes (see the table in Content).

**Combat simulator.** `CHARGEN COMBAT` (`combatRun`, `:328-1126`) runs a head-to-head simulation:

1. Parameters are parsed (`:368-474`): class names, `ITERATIONS=`, `SKIPLEVELS=`, `MOB=<name>` for a catalogued opponent, `FAILCHECK`, `EXPORT=<file>`, and one or two level numbers.
2. Each class runs on its own thread (`:520-1050`), and the main thread waits on a latch (`:1051-1053`).
3. For each level and each iteration, a fresh player (`GOODGUY`) and opponent (`BADGUY`) are built (`:622-734`). The player uses the class's behaviour: `CombatAbilities`, or `Bardness`, `Clericness`, `Druidness`, `Mageness`, `Thiefness` or `Fighterness` by base class (`:338-346`). The player is given pre-cast ticks (`:678-683`).
4. Both are removed from the normal tick scheduler (`CMLib.threads().deleteTick`, `:640`, `:662`, `:709`, `:729`) and the simulator calls `tick` on each side by hand every round (`:837-838`). Time in the simulation is therefore round-based, not wall-clock.
5. Rounds rotate through Woods, CaveRoom and CityStreet (`:596-607`), and the time of day is randomised per iteration (`:617`).
6. Each round records damage as a fraction of the other side's maximum HP (`:820-830`). A round's score is the damage dealt minus the damage taken (`:845-846`).
7. A fight ends when either side dies, at 1000 rounds (`:802`), or after 100 consecutive zero-damage rounds, which aborts it as stale (`:857-865`).
8. Per level it reports medians and quartiles (`:953-987`): median score, median hit percentage, the 75th percentile of rounds in losses, the 25th percentile of rounds in wins, and median physical damage done and taken. It also keeps the fastest win and the most damaging round.
9. Skill scores are the average net score across winning fights, top five printed (`:912-919`, `:1022-1043`). With `FAILCHECK`, a skill is listed when it fails more than 20 percent of the time (`:1004-1019`).

Default parameters:

| Parameter | Default | Where |
|---|---|---|
| Levels | 1 to 91 | `CharGen.java:316-317` |
| Step | 15 for the full range; otherwise 1 | `:478-483` |
| Iterations per level | 100 | `:318`, `:388-393` |
| Round cap | 1000 | `:802` |
| Stale abort | 100 consecutive zero-damage rounds (warning at 20) | `:857-865` |
| Export | tab-separated file, one block per metric, one row per class, plus averages by base class | `:1061-1123` |

**Weapon preview.** `CHARGEN WEAPON` (`weaponRun`, `:1237-1412`) takes fields `class`, `type`, `level`, `hands`, `reach`, `weight` and `material` (`:1239-1249`). Numbered prefixes add rows, up to 100 (`:1275-1291`). Each row is priced with `CMLib.itemBuilder().timsItemAdjustments` (`:1364-1368`), and the table shows the resulting attack and damage. With no arguments it prints the field list only (`:1257-1261`).

### CALENDAR

**Data model.** Events are `JournalEntry` rows in the `SYSTEM_CALENDAR` journal (`Commands/CalendarCmd.java:483`, `:649`; see b033 section 18). The fields are used as follows:

- `from`: the owner key. A player's name for personal events, a clan ID for clan events, `Holiday` for quest-driven holidays, or `SYSTEM` for system events (`:522-565`).
- `to`: `ALL`.
- `subj`: the description. The prompt asks for 40 characters, and nothing enforces it (`:938`, `:955`).
- `date` and `expiration`: milliseconds. For game-time events the expiration is `date + millisPerMudHour * hours` (`:638`). For real-time events it is `date + MILI_HOUR * hours` (`:636`).
- `dateStr`: the start as a game-clock code, or as milliseconds for real time (`:621`, `:632`).
- `data`: XML-like tags, `<HOURS>n</HOURS>` and, when repeating, `<PERIOD>n UNIT</PERIOD>` (`:639-645`). The sweep in `CMJournals.java:923` reads these tags to schedule the next occurrence.

**Windows.** The window for each subcommand is set in code:

| Subcommand | Window | Source |
|---|---|---|
| `NOW` | one tick either side of now | `CalendarCmd.java:316-317` |
| `SOON` | one tick before now to two days after | `:333-337` |
| `RECENT` | two days before now to one tick after | `:369-370` |
| `NEXT [n] [period]` | from one tick ago to now plus n periods | `:434-435` |
| `PAST [n] [period]` | from now minus n periods to one tick after | `:439-440` |

The default is one `WEEK`. Periods are `TimeClock.TimePeriod` values, with `RL`, `REAL` or `REAL LIFE` as a prefix for real time (`:92`, `:191-216`). Visibility is global events and holidays (`:107-141`), plus the viewer's own name and every clan on whose member list the viewer has `LIST_MEMBERS` (`:143-157`).

**ADD.** Two routes:

- Direct. If the caller holds a clan role or is a clan member, `CALENDAR ADD` takes four arguments in order: subject, `START:` or `REAL-START:`, `DURATION/HOURS:`, `REPEAT:` (`:598-653`). Clan authority is checked with `goForward(... CREATE_CALENDAR ...)` (`:558`, `:593`). The ballot path is used for non-admins.
- Prompted. `ADD` with no arguments walks four prompts: subject (`:949-959`), start (`:770-930`), duration in hours from 1 to 9999 (`:755-757`), then repeat as `n PERIOD` with n from 1 to 999 (`:697-700`). An empty repeat means none (`:690-694`). The start format is `y/m/d/h`. Adding `REAL` or `RL` switches to real-world dates, shown with the local time zone (`:783-785`).

Validation for game time (`:899-926`) uses the game clock, where the month is 1-based (`DefaultTimeClock.java:90`, `:926`), and it is correct. Validation for real time (`:851-898`) has an off-by-one (see Problems).

**REMOVE.** Matches the subject by fetch-reflective name, exact then partial (`:464-466`). Holidays are protected (`:472-480`). Your own entries are deleted directly. Clan entries need a clan role that covers `CREATE_MOTD` (`:505`) or are deleted directly if the caller holds the clan role (`:496-503`).

**Lifecycle.** A sweep (`CMJournals.java:1036-1056`) processes expired entries that carry `<PERIOD>` (recurrence, `processCalendarExpiration` at `:923`) and deletes entries older than one year (`:1044-1053`). Entries with a period that expire within the hour get a scheduled runnable (`:1203`). The engine detail is in b033 section 18.

### CATALOG: the mob and item catalog

The catalog is a library of prototypes. Each entry records its category, spawn rule, rate, cap, optional zapper mask and reference count (`Libraries/interfaces/CatalogLibrary.java:574-600`). Spawn modes are `CataSpawn.NONE`, `LIVE` (equipment on a living mob), `DROP` (on a corpse only) and `ROOM` (randomly in a room) (`:544-570`).

Verb flows:

- **Single target** (`Catalog.java:889-917`): finds the object in the room or on yourself (`SELF`), runs `catalog()`, then writes the room's database record through the start room.
- **`catalog()`** (`:55-128`): refuses objects that cannot save a database ID (`:60-65`) and objects whose Java class differs from the existing entry (`:66-71`). When the entry exists and differs, it asks for confirmation. The default answer there is Y (`:114`). When the entry exists and is identical, it only offers a category change, defaulting to N (`:94-98`).
- **Auto-catalog** (`:307-362`): `CATALOG ROOM|AREA|WORLD MOBS|ITEMS` copies each room's content into a temporary room (`CoffeeMaker.makeNewRoomContent`, `:329`), catalogues each item, writes the room back and resets the room. AREA and WORLD ask for confirmation twice (`:318-322`), ROOM once.
- **EDIT** (`:598-712`): chooses the spawn mode (`N`, `L` and `D` for items, `R` for both), then the cap, the rate as a percent above zero, and the mask (`:627-697`). It writes `CATALOG_ITEMS` or `CATALOG_MOBS` (`:699-710`).
- **DELETE** (`:538-596`): prints the reference count before the confirm. The keywords `everydamnmob`, `everydamnitem` and `everydamnthing` select the whole catalog (`:544-556`).
- **SCAN, DBSCAN, OVERLOOK, DBOVERLOOK** (`:714-822`): report which world objects are or are not cataloged. The `DB` forms copy the room first.
- **CLEAN** (`:824-888`): drops the cataloged flag from objects whose catalog entry has gone, and writes the room back.
- **Categories** (`:365-438`): the per-player current category lives in `currentCats` (`:53`). `GLOBAL`, `NONE` and `UNCATEGORIZED` are reserved (`:394-395`). A new name asks for confirmation before it is created (`:399-422`).

### CHANNELS and CHANWHO

**Channel as verb.** `Channel.getAccessWords()` returns `CMLib.channels().getChannelNames()` (`Commands/Channel.java:42-46`), so each configured channel is a verb with no table entry. `channel()` (`:111-368`) then runs these steps in order:

1. **Mask toggle** (`:119-137`). If the channel's bit is set in the player's mask, typing the name turns it back on. The bit is cleared with `mask & (mask - bit)` (`:134`). That is correct only because the bit is known to be set.
2. **QUIET** (`:145-149`) and channel availability through `maskCheck` (`:151-156`).
3. **Bare name** (`:158-175`) shows the last five backlog messages, then falls through to `LAST 5`. Monsters get "what?" instead.
4. **Quoting** (`:177-182`). Words containing spaces are re-quoted before the message is rebuilt.
5. **CLANONLY** (`:184-192`) needs the `CHANNEL` clan function.
6. **One-word subcommands** (`:194-241`): `last` or `list` (`:196-199`), `off` (`:202-217`, hands over to `NoChannel`), `undo` or `delete` (`:220-240`).
7. **`search:<text>`** (`:244-267`), capped at 100 matches unless the viewer holds `JOURNALS` everywhere (`:251`).
8. **`last <n>`** (`:268-346`). Pages of 50 are read from the end of the backlog (`:291-318`), and the count is capped at 100 (`:282-284`).
9. **Read-only flags** (`:347-364`): `READONLY`, `PLAYERREADONLY` (players only), `ARCHONREADONLY` (sysops may still post).
10. **Send** (`:365-367`), through `CMLib.channels().createAndSendChannelMessage`.

Backlog lines are rewritten. `showBacklogMsg` (`:87-109`) copies the message, inserts a suffix such as "(3 minutes ago)" before the closing `^</CHANNEL^>` tag (`reTimeAgo`, `:77-85`), and retags the channel code before delivery (`:102-108`).

**Channel bits.** `getChannelCodeNumber` returns `1<<index` (`Libraries/CMChannels.java:523-535`). `CMath.isSet` takes an index (`core/CMath.java:977-981`). `Channels OFF` sets `Integer.MAX_VALUE` (`Commands/Channels.java:75`), which means bits 0 to 30. That caps the mask at 31 channels, and the default list uses 10 (see Content).

**CHANNELS.** Without arguments it lists the audible channels in columns of three, each as a clickable `CHANNELS 'NO...'` tag (`Commands/Channels.java:139-174`). `ON` clears the mask (`:59-67`). `OFF` sets it to all bits (`:69-76`). `COPY` loads another player's mask, asks Y or N, and copies it (`:78-138`).

**CHANWHO.** For a local channel, it walks every online session (`Commands/ChanWho.java:89-103`) and asks each session's channel library whether the requester's messages reach that session (`:97`, `Libraries/CMChannels.java:383-`). A listed player must be in the game and not cloaked, or the viewer must hold `CLOAK` or `WIZINV` and be at least that level (`:100-101`). The output is "Listening on X" followed by names. With `@mud` it hands off to the intermud library (`:58-78`).

### Clan verbs and the governance flow

The clan verbs share one flow. The clan library does the work (see b027 for the engine). The verbs add the front end.

**Authority and the ballot gate.** `Libraries/Clans.java:654-758` (`goForward`) decides whether an actor may do something now, may propose it, or must be refused:

1. If the role's authority for the function is `CAN_DO`, the action goes ahead (`:663`). `CAN_NOT_DO` refuses (`:665`).
2. Otherwise the function is ballot-gated. For `ASSIGN` the actor needs `VOTE_ASSIGN`, and for anything else `VOTE_OTHER` (`:667-674`).
3. With `voteIfNecessary` false, the call only reports that the actor may propose (`:675-676`).
4. A starter may have at most five open ballots (`:684`). An identical open matter is refused (`:689-694`).
5. If the clan has exactly one voter, the action runs at once (`:701-702`).
6. Otherwise the actor is asked to confirm, a `ClanVote` is created with the command text as its matter, roles are announced (`:704-754`), and the verb returns without acting.

**Two-phase use.** Each verb calls `goForward` twice. The first call (`voteIfNecessary` false) validates authority before any argument checks. The second (true) happens at the commit point, just before the change. Examples: `Commands/ClanAssign.java:96` and `:132`, `Commands/ClanDeclare.java:122` and `:161`.

**Ballot as stored command.** A ballot stores its matter as a command string. A passing ballot re-parses it and runs it as a forced mob at level 1000 holding a clan role (`Commands/ClanVote.java:222-233`). The tick path does the same with the top role for the ballot's own function (`Common/DefaultClan.java:2516-2528`). The ballot is therefore a command with a vote in front of it, and any verb can be put to a vote without special cases.

**Applicants and membership.** Each government names an auto position for applicants and an accept position for members (`Commands/ClanApply.java:104-112`, `Commands/ClanAccept.java:96` and `:130`). A clan whose auto position is the applicant seat puts the applicant in review. A clan whose auto position is already the accept position admits them at once (`ClanApply.java:106-127`). `CLANREJECT` deletes membership (`Commands/ClanReject.java:129`). `CLANEXILE` and `CLANRESIGN` run the exit script first and then delete membership (`Commands/ClanExile.java:114-133`, `Commands/ClanResign.java:95-111`).

**Status gate on rooms.** `CLANHOMESET`, `CLANDONATESET` and `CLANMORGUESET` refuse unless the clan status is `CLANSTATUS_ACTIVE` (`Commands/ClanHomeSet.java:92`). The constants are ACTIVE 0, PENDING 1, FADING 2, STAGNANT 3 (`Common/interfaces/Clan.java:1038-1044`), so a pending clan cannot set a room either. The room must also be owned by the clan (`isPropertyOwnersName`, `ClanHomeSet.java:99`).

**Function codes used by these verbs.** The verbs use the following `Clan.Function` values (enum at `Common/interfaces/Clan.java:1241`):

| Function | Used by |
|---|---|
| `ACCEPT` | `CLANACCEPT` |
| `REJECT` | `CLANREJECT` |
| `ASSIGN` | `CLANASSIGN` (voting uses `VOTE_ASSIGN`) |
| `EXILE` | `CLANEXILE` |
| `DECLARE` | `CLANDECLARE` |
| `TAX` | `CLANDUES`, `CLANTAX` |
| `PREMISE` | `CLANPREMISE`, `CLANQUAL` |
| `READ_MOTD`, `CREATE_MOTD` | `CLANMOTD` (mixed; see Problems) |
| `SET_HOME` | `CLANHOMESET`, `CLANMORGUESET` (permission) |
| `SET_DONATE` | `CLANDONATESET` |
| `MORGUE` | `CLANMORGUESET` (clan selection only) |
| `CHANNEL` | clan channels (`Commands/Channel.java:187`) |
| `CREATE_CALENDAR` | `CALENDAR ADD`, `CALENDAR REMOVE` for clans |
| `LIST_MEMBERS` | calendar visibility (`CalendarCmd.java:151`) |

**Dues and tax.** `CLANDUES` sets `C.setDues` (`Commands/ClanDues.java:297`) and forgives by a database delta (`:184`). Collection happens in the clan library, once per mud year, from members' coin and bank balances (`Libraries/Clans.java:1760-`). `CLANTAX` sets the experience tax (`Commands/ClanTax.java:151`).

**Relations.** `CLANDECLARE` writes only the actor's side (`Common/DefaultClan.java:722-729` via `Commands/ClanDeclare.java:166`). The effective relation between two clans is combined from both stances with `Clan.RELATIONSHIP_VECTOR` (`Common/interfaces/Clan.java:1066-1072`, `Libraries/Clans.java:298-318`). A mud month must pass between changes (`ClanDeclare.java:148-155`).

## Content worth mining

**Channels as configured in `coffeemud.ini`.** The default `CHANNELS` list (`coffeemud.ini:1454-1464`) has ten channels. Flags are plain words after the name.

| Channel | Flags (as configured) | Reading for a text game |
|---|---|---|
| `GOSSIP` | none | general chatter |
| `GRATZ` | none | congratulations feed |
| `CHAT` | none | general chat |
| `OOC` | `ACCOUNTOOC NOLANGUAGE` | out-of-character talk, language filtered off |
| `QUESTCHAT` | `PLAYERREADONLY` | quest chat; read-only to players, who cannot post |
| `WIZ` | `+SYSOP -NAMES` | staff channel by mask |
| `CLANTALK` | `CLANONLY CLANINFO CONQUESTS LOGINS LOGOFFS` | clan members only, with clan events |
| `INFO` | `READONLY CALENDAR EXECUTIONS LEVELS NEWPLAYERS BIRTHS MARRIAGES ACHIEVEMENTS DIVORCES CHRISTENINGS` | read-only event feed; calendar announcements go here |
| `WIZINFO` | `READONLY LOGINS LOGOFFS DETAILEDLEVELS DETAILEDDEATHS PLAYERPURGES +SYSOP -NAMES` | staff event feed |
| `ICOFF` | `NOLANGUAGE I3=coffeemud_universe -LEVELS +>91` | off-topic channel bridged to an intermud network |

Event flags (for example `LEVELS`, `DEATHS`) make the engine post game events into a channel. The full flag list is in `coffeemud.ini:1429-1452`.

**CHARGEN starter gear by class** (`Commands/CharGen.java:69-121`, weapon codes from `CharClasses/interfaces/CharClass.java:689-711`):

| Weapon code | Starter weapon | Notes |
|---|---|---|
| ANY, THIEFLIKE, BURGLAR, NEUTRALCLERIC | steel sword | default |
| DAGGERONLY | dagger | piercing |
| NATURAL | wooden sword | oak |
| ROCKY | stone sword | |
| MAGELIKE | staff | bashing |
| GOODCLERIC | staff | sets alignment good |
| EVILCLERIC | steel sword | sets alignment evil |
| ALLCLERIC | staff if good, otherwise the default sword | |
| FLAILONLY | flail | |

Armour material by class armour code (`CharGen.java:136-152`): CLOTH gives cotton, LEATHER gives leather, NONMETAL, VEGAN and OREONLY give oak, and ANY and METALONLY keep steel.

**CHARGEN WEAPON vocabulary** (`Items/interfaces/Weapon.java:87-170`). Classes: AXE, BLUNT, EDGED, FLAILED, HAMMER, KARATE, POLEARM, RANGED, SWORD, DAGGER, STAFF, THROWN. Types: NATURAL, SLASHING, PIERCING, BASHING, BURNING, BURSTING, SHOOTING, FROSTING, GASSING, MELTING, STRIKING, LASERING.

**CALENDAR vocabulary** (`Commands/CalendarCmd.java:53-62`): NEXT, SOON, PAST, RECENT, NOW, ADD, REMOVE (alias DELETE). Periods: ALLTIME, DAY, HOUR, MONTH, SEASON, WEEK, YEAR (`:225-238`).

**Catalog spawn modes** (`Libraries/interfaces/CatalogLibrary.java:544-570`, EDIT keys at `Commands/Catalog.java:615-621`):

| Mode | Key | Meaning |
|---|---|---|
| NONE | N | no spawn rules |
| LIVE | L | equipment on a living mob (items) |
| DROP | D | on a corpse at death (labelled "Dead mobs" in the prompt) |
| ROOM | R | placed randomly in a room |

**World events that the calendar can list** (`resources/quests/holidays/holidays.quest`). Four holiday quests run through the quest engine. Drought, famine and harsh winter are season and weather triggered. Each sets a room mask and adds MudChat lines, and each changes shop price masks: drought raises drink prices by 20.0, famine raises food prices by 10.0, harsh winter raises wooden, coal and cloth goods (10.0 and 5.0). New Year fires at `MUDDAY 1-1` with a happy-new-year MudChat and lowers most prices to 0.9. Holiday entries reach the calendar through `CMLib.quests().getHolidayEntries` (`Commands/CalendarCmd.java:110`).

**Stock clan governments** are tabulated in `NEWOVERVIEW/society/b027-clans-and-clan-governments.md` (section "Stock governments"). The data is in `resources/clangovernments.xml`.

## Notable design ideas

1. **A ballot is a command.** A pending vote stores the command text; a passing vote re-runs it as a forced clan actor (`Commands/ClanVote.java:222-233`). Any verb can be put to a vote without special cases.
2. **Check, then vote, then commit.** Each verb validates authority first and commits later with the same helper (`Commands/ClanAssign.java:96`, `:132`). The first call can run before any argument checks, which keeps refusals cheap.
3. **A balance harness inside the game.** `CHARGEN COMBAT` runs a class against a reference opponent over 91 levels. It rotates rooms and randomises time of day to remove bias (`CharGen.java:596-617`). The simulation owns the clock by taking both mobs off the tick scheduler and ticking them by hand (`:640`, `:837-838`). It reports percentiles rather than means (`:953-987`) and flags stale fights (`:857-865`).
4. **Typical character by averaging.** `AverageAllClassMOB` (`CharGen.java:284-312`) produces a reference mob by averaging many generated ones. A designer gets a "typical level-N character" without writing a rule for it.
5. **One table for personal, clan and world events.** The calendar's `from` field is the owner key: a player name, a clan ID, `Holiday` or `SYSTEM` (`CalendarCmd.java:522-565`, `:143-189`). Visibility then follows from ownership.
6. **Channels as verbs.** Every configured channel is a verb through `getAccessWords()` (`Commands/Channel.java:42-46`). New channels need configuration only.
7. **Events as a feed.** Channel flags such as `LEVELS` or `CALENDAR` turn the channel into a log of game events (`coffeemud.ini:1429-1452`).
8. **Game time or real time.** The same calendar verbs take an `RL` or `REAL` prefix to switch between game-clock and real-world windows (`CalendarCmd.java:92`, `:191-216`).

## Problems and cruft

Each item is a code finding unless marked "unverified" or "design".

**Correctness**

1. **`CHARGEN NEW` and `CHARGEN PLAYER` with no class crash.** After `NEW` or `PLAYER` is removed, the code reads `commands.get(0)` on an empty list (`Commands/CharGen.java:1439-1451`). The dispatcher catches it and tells the player "Oops-- you ran into a game bug!" (`MOBS/StdMOB.java:2281-2289`).
2. **Calendar real-time date check is off by one.** The check compares a 1-based month with `Calendar.MONTH - 1` (`Commands/CalendarCmd.java:881` and `:889`). Days and hours earlier than today are therefore accepted in the current month. The month check at `:872` is correct.
3. **`CHANNELS COPY` unload logic is inverted.** `unloadAfter` is set from `isLoadedPlayer`, so it is true only for a player already in the cache (`Commands/Channels.java:89`; `Libraries/CMPlayers.java:925-936`). The `finally` block then calls `unloadOfflinePlayer` on that player if they have no session (`Channels.java:128-134`). That path destroys the mob and deletes its extended items (`Libraries/CMPlayers.java:578-600`). The `finally` runs before the answer arrives, because `Session.prompt` returns at once (`Common/DefaultSession.java:1748-1758`). The effect is that a cached, offline player can be destroyed while the copy prompt is open.
4. **`CLANTAX` skips its own range check on the member path.** The 0 to 25 check is only in `possiblySetClanTaxRate` (`Commands/ClanTax.java:135`). A member who holds the role directly goes through `CMath.s_int(...)/100` with no bound (`:97-98`), so `CLANTAX 500` sets 500 percent.
5. **Dues message prints a placeholder.** `Commands/ClanDues.java:174` passes no argument, so `@x1` stays in the text. `CMStrings.replaceVariables` returns early when there are no values (`core/CMStrings.java:1959`).
6. **`CLANAPPLY` refuses a class-restricted clan silently.** The class gate returns with no message (`Commands/ClanApply.java:90-92`).
7. **Re-applying to the same clan can demote a member.** `CLANAPPLY` checks only the category cap (`:71-82`), not membership in this clan. With `MAXCLANCATS` above 1 (default 1, `coffeemud.ini:1664`), a current member passes, `addMember` uses the applicant role (`:105`), and `StdMOB.setClan` overwrites an existing role (`MOBS/StdMOB.java:5225-5228`).
8. **`CLANMOTD DELETE` skips the ballot.** Clearing runs at once (`Commands/ClanMOTD.java:120-124`), while writing goes to a ballot (`:129`). The function codes are also mixed: `READ_MOTD` selects the clan (`:67`) and runs the ballot (`:129`), but `CREATE_MOTD` is the gate (`:84`).
9. **MOTD journal key does not match the clan delete.** The verb writes and clears `CLAN_MOTD_` plus the clan ID (`Commands/ClanMOTD.java:123`, `:141`). Clan deletion removes `CLAN_MOTD` plus the clan ID with no underscore (`Common/DefaultClan.java:1002`). MOTD rows survive a deleted clan.
10. **`CLANMORGUESET` picks a clan with one function and checks another.** Selection uses `MORGUE` (`Commands/ClanMorgueSet.java:66`); permission and ballot use `SET_HOME` (`:98`, `:105`).
11. **No last-officer guard on exile or resignation.** `CLANASSIGN` refuses to demote the last top officer (`Commands/ClanAssign.java:142-160`). `CLANEXILE` (`:89-134`) and `CLANRESIGN` (`:58-111`) have no such check. Whether a leaderless clan is handled elsewhere is unverified.
12. **Two ballot resolution paths with different rules.** `CLANVOTE` resolves when the last voter votes, paying out under the top `ASSIGN` role for every ballot (`Commands/ClanVote.java:213-233`, especially `:224`). The clan tick resolves with quorum and expiry, paying out under the top role of the ballot's own function (`Common/DefaultClan.java:2478-2528`). The outcome is the same when everyone votes, but the paying identity differs.
12a. **Calendar REMOVE gates clan entries on the wrong function.** `CALENDAR REMOVE` asks for `CREATE_MOTD` (`Commands/CalendarCmd.java:505`), while `CALENDAR ADD` asks for `CREATE_CALENDAR` (`:558`, `:593`). A member with calendar rights but no MOTD rights cannot remove a clan event, and a member with MOTD rights but no calendar rights can.
13. **Catalog `EDIT` cancel leaves memory changed.** The spawn mode is set on the cached entry before the prompts (`Commands/Catalog.java:628`). Cancelling the cap or rate prompt returns before the database write (`:642-645`, `:659-660`, `:699-710`). Memory and database disagree until restart.
14. **Bulk catalog delete has no per-item confirmation above ten.** `(del.length>10) || confirm(...)` short-circuits (`Commands/Catalog.java:575-576`, `:588-589`). The `everydamn...` keywords (`:544-556`) therefore delete more than ten entries silently.
15. **Calendar subject length is not enforced.** The prompt says 40 characters (`Commands/CalendarCmd.java:938`); the callback accepts any length (`:949-955`).
16. **Channel one-word quirks.** Any single word that is a prefix of `last` or `list` becomes a backlog request (`Commands/Channel.java:196-199`), so `CHAT list` cannot post the word. A single word that is a prefix of `undo` or `delete` with four or more letters is treated as a delete (`:220-240`). The word `undo` therefore cannot be posted, but `de` can.
17. **Catalog `SCAN` has no null check on the room** when not using the database (`Commands/Catalog.java:731-734`). `OVERLOOK` does check (`:786-787`). Whether `roomContent` copes with null is unverified.
18. **CHARGEN averaging divides by one less than it sums.** `AverageAllClassMOB` increments `numClasses` for every class sample (`Commands/CharGen.java:296`) and decrements it for the first one (`:301`), but that first sample is still summed (`:304`). The divisor at `:310` is therefore N-1 for N samples, a small upward bias of about 1/N.
19. **CHARGEN "LostRounds" repeats Losses.** Index 12 receives the same loss count as index 4 (`Commands/CharGen.java:966`, `:981`). The label is misleading.
20. **CHARGEN template copy registers a catalog reference and never releases it.** `MOB=` copies the catalog mob and calls `changeCatalogUsage(M, true)` (`Commands/CharGen.java:404-408`), which calls `addReference` (`Libraries/CMCatalog.java:769-772`). `combatRun` destroys only its per-fight mobs (`CharGen.java:945-946`). The effect on the reference count shown by `CATALOG LIST` is unverified.
21. **CHARGEN threads share state.** The per-class threads share one temporary area (`Commands/CharGen.java:485-487`) whose clock they set (`:614-617`), and one `Hashtable` that one thread clears (`:1020`). Impact is unverified.
22. **`CHANNELS` mask arithmetic is correct but cryptic.** `mask & (mask - bit)` clears a set bit (`Commands/Channel.java:134`).

**Cruft**

23. `Commands/ClanKills.java:86` and `Commands/ClanPVPKills.java:86` are the same routine with one term different. `ClanMorgueSet`, `ClanDonateSet` and `ClanHomeSet` differ only in function, setter and wording.
24. `Commands/ClanList.java:123-125` is an empty `else` branch.
25. `Commands/CalendarCmd.java:472-473` has a redundant second condition.
26. The channel script key is built from the Java object's `toString` (`Commands/Channel.java:127`, and the same pattern in `Commands/NoChannel.java`). It works only because command objects are singletons (`StdCommand.java:277-280`).
27. `Commands/Catalog.java:53` and `:72` keep categories in memory on the verb singleton, keyed by player name. They are lost at restart.
28. `Commands/CharGen.java:747-757` and `:1087-1088` are commented-out code. `:1028-1029` prints a debug score for `Spell_Lightning` to the console.
29. `Commands/Chants.java:35` is 73 lines because its logic lives in `Skills` (not read here).
30. `Libraries/Clans.java:71` passes `clanID1` twice. There are no callers in the tree; see b027 for the same finding.

**Design (not bugs)**

31. `CLANDETAILS` switches your default clan as a side effect (`Commands/ClanDetails.java:78-83`).
32. `CLANDECLARE` writes only the actor's side. The effective relation is combined from both stances (see above).

**Cross-bucket, seen while checking dates (unverified in play).** `DefaultTimeClock` indexes month names with a 1-based month: `getMonthNames()[month]` (`Common/DefaultTimeClock.java:274-276`), while the field starts at 1 (`:90`) and the hour arithmetic uses `month-1` (`:926`). That would print "the 2nd month" for month 1.

## Takeaways for a Python SUD

- **Borrow the balance harness.** A headless `simulate_matchup(actor, level, opponent, n, seed)` that returns the median and quartiles of rounds to win, damage taken and skill use would tune the game's combat without playtesting. Keep the simulator's own clock and seeded RNG, and run classes in parallel with `multiprocessing`. Port the stale-fight guard (`CharGen.java:857-865`).
- **Replace the per-verb parsers.** Give each verb a typed argument spec and one validation path, instead of positional parsing per class. Model multi-step input as a generator that yields prompts, not nested callbacks (`ClanCreate.java:94-270`).
- **Keep "typical character" as a test tool.** Averaging generated characters is useful for regression tests. Fix the divisor (Problems 18).
- **Starter kits as data.** Map class to allowed weapon families and armour materials to concrete items, in a table (see Content), not an if-chain (`CharGen.java:69-152`).
- **Calendar.** Use `Event(name, start, duration, repeat)` with game time only. Compute windows as functions of game time. Recurrence is "advance the clock by n periods until after now". Drop real-world dates and the `Holiday` special case, which a single-player game does not need.
- **Channels become one event log.** One object with named feeds, each a bounded deque with a filter. The verbs are `say`, `last N`, `search`, `undo` (your own last line) and `off` or `on`. The `LEVELS`-style flags map to game events posted by the engine.
- **Catalog becomes a prototype registry.** Each prototype has a spawn mode (`none`, `carried`, `corpse`, `room`), a rate, a cap and a predicate. Skip reference counting unless you need it.
- **Ballots and authority: keep the check-then-commit shape, drop the vote.** Use a role-to-permissions table. For a single player, the only social roles are NPC hierarchies, and an NPC council vote is an optional feature.
- **Stored commands as data.** A deferred action should store `(verb, args)`, not the text. The clan ballot re-parses text (`CMParms.parse`), which is fragile around quotes (`ClanVote.java:232`).
- **Multiplayer assumptions to drop or rethink:** `CHANWHO`, `CHANNELS COPY`, `CLANTALK`, applicant review, dues collected from other players' banks, PvP kill tables (`CLANKILLS`, `CLANPVPKILLS`), intermud (`ICOFF`) and the morgue and donation rooms. A single-player game keeps the calendar, the catalog idea and the simulator.

## Coverage

- **Read in full (28 of 28):** every `Commands/*.java` file in the bucket. The package, import and licence headers were skimmed only where needed to confirm imports.
- **Read in part (supporting code):** `Commands/StdCommand.java` (full); `Commands/NoChannel.java` (most); `MOBS/StdMOB.java` (`doCommand`, `setClan`); `Common/interfaces/Clan.java` (constants and enums); `Common/DefaultClan.java` (relations, membership, destroy, the tick vote section); `Libraries/Clans.java` (`goForward`, relation helpers, `clanDues` entry); `Libraries/CMChannels.java` (`mayReadThisChannel`, `getChannelCodeNumber`); `Libraries/CMJournals.java` (calendar sweep and recurrence); `Libraries/CMCatalog.java` (`changeCatalogUsage`); `Libraries/CMPlayers.java` (`isLoadedPlayer`, `getLoadPlayer`, `unloadOfflinePlayer`); `Libraries/MUDZapper.java` (operators and `levelHelp`); `core/CMStrings.java` (`replaceVariables`); `core/CMath.java` (`isSet`); `Common/DefaultSession.java` (`prompt`); `core/database/MOBloader.java` (`DBUpdateClanDonates`); `Common/DefaultTimeClock.java` (excerpts); `coffeemud.ini` (channel, clan and backlog keys); `resources/clangovernments.xml` (summarised with a script, not read by hand); `resources/quests/holidays/holidays.quest` (most of its 58 lines).
- **Skimmed:** `Commands/Chants.java` only through its calls into `Skills`; the behaviour classes `Behaviors/CombatAbilities.java` and its subclasses (names and mapping only); `TimsLibrary.timsItemAdjustments` (called, not read); `CatalogLibrary` (enums and `CataData` only).
- **Skipped:** `Commands/Skills.java` (the base of `CHANTS`); the command dispatcher beyond `StdMOB.doCommand`; the clan tick beyond the ballot section (covered by `society/b027`).
