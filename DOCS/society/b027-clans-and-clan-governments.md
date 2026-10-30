# Clans and clan governments

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b027` (Clans and clan governments) · 8 files · 10101 lines of Java.

## Summary

A clan is a persistent player organisation with members, ranks, a shared bank, an XP pool, a set of diplomatic relations and a "government" that decides what each rank may do. The government is data: `resources/clangovernments.xml` defines 11 stock types (Gang, Guild, Union, Fellowship, Theocracy, Family, Anarchy, Profession, Co-op, Meritocracy, Plutocracy). Each is a ladder of ranked positions with a 29-entry permission table. The per-clan object (`DefaultClan`) adds taxes and dues, XP levels that unlock abilities and effects by rank, war and alliance states, ballots that turn restricted actions into member votes, and a daily maintenance tick that promotes, warns and eventually deletes inactive clans. The library `Clans` holds the registry, parses the government file, runs the trophy and dues sweeps and gates every restricted command.

For a single-player game this is the most reusable part of CoffeeMud's social layer: rules as data, one permission table, and a clean way to run NPC factions. The parts that assume other humans (elections among players, inactivity purges, email warnings) need rethinking.

## Key files and types

File references in this chapter are relative to `com/planet_ink/coffee_mud/` unless they begin with `resources/` or `coffeemud.ini`.

| File or class | Role | Lines |
|---|---|---|
| `Common/DefaultClan.java` | Per-clan state and rules: XP and levels, tax, dues, relations, membership and privileges, profile text, the daily maintenance tick (promotion, fading, purge, votes) | 3038 |
| `Libraries/Clans.java` | Library `CMLib.clans()`: registry, relation queries, privilege checks, `goForward` (the vote gateway), government XML parse and write, trophy scan, dues sweep, trophy prizes | 2443 |
| `Common/DefaultClanGovernment.java` | One government type: rule fields, positions array, promotion mode, XP formula, per-level ability and effect lists, generated help | 1547 |
| `Common/interfaces/Clan.java` | Clan interface plus enums and constants: `Function` (29), `Authority`, `AutoPromoteFlag`, `Trophy` (15), relation states and matrix, `ClanVote`, `MemberRecord` | 1318 |
| `Common/interfaces/ClanGovernment.java` | Government interface (the data of one type) | 615 |
| `Libraries/interfaces/ClanManager.java` | Library interface used by commands and mobs | 558 |
| `Common/DefaultClanPosition.java` | One rank: role ID, rank, seat limit, inner mask, permission chart, titles | 378 |
| `Common/interfaces/ClanPosition.java` | Position interface | 204 |
| `resources/clangovernments.xml` | Stock government definitions (not in this bucket; summarised, see Content) | 1626 |
| `coffeemud.ini` lines 1544-1665 | Clan timing, trophy prizes, vote keys, category limits | - |

## How it works

### 1. Data model: governments, positions and permissions

**Government** (`ClanGovernment`, implemented by `DefaultClanGovernment`). The fields that matter: `ID` (type number), `name` (also the key for its ability mappings), `category` (`Common/DefaultClanGovernment.java:57-100`), `autoRole` (rank given to applicants), `acceptPos` (rank given to accepted members), `requiredMaskStr` (zapper mask a player must pass to apply), `isRivalrous` (war and PvP allowed), `isFamilyOnly`, `overrideMinMembers`, the conquest flags (`:85-90`), `maxVoteDays`, `voteQuorumPct`, `autoPromoteBy`, and the compiled XP formula.

**Position** (`ClanPosition`, implemented by `DefaultClanPosition`). Fields: `ID` (a string such as `BOSS`), `roleID` (a stable number), `rank` (lower is more senior; `doesOutRank` at `Common/DefaultClan.java:2558-2569` relies on this), `name` and `pluralName`, `max`, `innerMaskStr` (zapper mask a candidate must pass), `isPublic` (listed in the profile), `functionChart` (one `Authority` per function) and `titleAwards` (`Common/DefaultClanPosition.java:49-68`). The `max` field is a double. A value of 1 or more is a seat count. A value from 0 up to 1 is a share of membership (`Common/interfaces/ClanPosition.java:124-131`).

**Permissions.** `Clan.Function` has 29 entries: membership (ACCEPT, REJECT, ASSIGN, EXILE), property (SET_HOME, SET_DONATE, PROPERTY_OWNER, MORGUE), money (DEPOSIT, WITHDRAW, DEPOSIT_LIST, TAX), politics (DECLARE, PREMISE, VOTE_ASSIGN, VOTE_OTHER, ENCHANT), orders (ORDER_UNDERLINGS, ORDER_CONQUERED), records and channels (CHANNEL, READ_MOTD, CREATE_MOTD, JOURNAL, CREATE_LAW, CREATE_CALENDAR) and benefits (CLAN_BENEFITS, CLAN_TITLES, HOME_PRIVS, LIST_MEMBERS) (`Common/interfaces/Clan.java:1241-1301`). Each position holds an `Authority` for each function: `CAN_NOT_DO`, `CAN_DO` or `MUST_VOTE_ON` (`Clan.java:1190-1195`). The third value is the interesting one. The action is allowed only after a ballot.

**Role IDs are array indices.** `getAuthority` reads `govt().getPositions()[roleID]` (`Common/DefaultClan.java:1320-1325`). The XML loader fills `posArray[roleID]` and rejects duplicate or out-of-range IDs (`Libraries/Clans.java:1331-1347`, `:1421-1432`). Role order is not rank order. In Meritocracy the top rank is role 5 and the applicant role is 0.

**Loading.** `Clans.getStockGovernments` (`Libraries/Clans.java:970-999`) parses the XML once and caches it as a resource under `parsed_clangovernments`. If the file is missing it builds a sample government instead. `parseGovernmentXML` (`Clans.java:1204-1517`) resolves `AUTOPOSITION` and `ACCEPTPOSITION` by position ID, reads each `POWER` into the function chart, and applies the `<VOTING>` list. A listed function becomes `MUST_VOTE_ON` for a position only if that position also holds `VOTE_ASSIGN` (for ASSIGN) or `VOTE_OTHER` (for everything else) (`Clans.java:1246-1316`). An explicit `POWER` tag on the position then sets `CAN_DO`.

**Government stats as flat keys.** `DefaultClanGovernment.getStat` and `setStat` (`:683-1069`) expose the same fields, plus the ability and effect lists, under numbered keys such as `NUMRABLE`, `GETRABLE3` and `GETREFFROLE0`. The web and in-game editors use these keys, so the editing surface is a string map rather than typed setters.

### 2. The clan object and its persistence

`DefaultClan` (`Common/DefaultClan.java:72-121`) keeps: name, which is also its ID (`:1411-1414`); category override; premise; recall, morgue and donation room IDs; the enforced class; level; tax rate; dues; XP; the vote list; clan-kill timestamps; relations; the government ID; trophy bits; monthly counters; status and `lastStatusChange`; and a set of `ClanFlag`s (only `NOTROPHY` exists, `Clan.java:1221-1234`).

Persistence goes through the database layer:
- Member rows are in table `CMCHCL`, with a semicolon-joined stats string `mobpvps;playerpvps;donatedGold;donatedXP;joinDate;dues` (`core/database/MOBloader.java:2216`).
- Votes and kill logs are player-data slots `CLANVOTES` and `CLANKILLS` (`DefaultClan.java:357`, `:191`).
- The politics block (`getDataXML` and `setDataXML`, `DefaultClan.java:1488-1643`) carries the government ID, tax, dues, XP, level, auto-position, category, rivalry, monthly counters, achievements, tattoos and relations.

### 3. Membership and privileges

`addMember` (`DefaultClan.java:819-827`) stores the role on the mob, writes the member row, runs `updateClanPrivileges` and bumps the monthly new-member counter. `delMember` sets the role to -1 (`:829-836`). Applicants hold the government's `autoRole`. A newly accepted member gets `acceptPos`.

`updateClanPrivileges` (`DefaultClan.java:839-985`) runs on every change and does four things:
- Sets the mob's class to the clan's enforced class, if one is set and the clan benefits permit it (`:849-857`).
- Grants clan-level auto-abilities and removes any auto-gained ability the member no longer qualifies for (`:869-881`).
- Grants titles: government title awards first, then position title awards. The clan name fills `@x1` and the position name fills `@x2` (`:892`, `:894`). Titles that belong to other positions are removed (`:904-940`). The stock title pattern is `*, @x2 of @x1`.
- When the member is no longer in the clan, moves its ClanItem objects to the donation room or the recall room, or destroys them (`:942-981`).

`Libraries/Clans.goForward` (`Clans.java:654-758`) is the gate for every restricted command. `CAN_DO` runs at once. `CAN_NOT_DO` refuses. `MUST_VOTE_ON` starts a ballot, but only if the member's role holds the matching vote power:
- A starter may have at most 5 open votes (`:684`).
- A matter already under vote is refused (`:689-694`).
- If the clan has exactly one voter, the action runs at once (`:700-702`).
- Otherwise the player is asked "This matter must be voted upon. Would you like to start the vote now (y/N)?" (`:704`). If they decline, the reply is "Without a vote, this command can not be executed." (`:756`).
- After a ballot starts, the clan is told which roles should vote, and to use CLANVOTE (`:716-747`).

### 4. Votes

A ballot is a `ClanVote`. It holds the starter, status (`VSTAT_STARTED`, `VSTAT_FAILED`, `VSTAT_PASSED`), a start time, the matter as a raw command string, the function ordinal, and a list of (name, yes or no) pairs (`Common/interfaces/Clan.java:925-956`). Ballots are stored as XML (`DefaultClan.java:331-360`).

Resolution happens inside the clan tick (`DefaultClan.java:2457-2545`):
- **Duration** is the government's `MAXDAYS` (default 54 if zero or less, `:2470-2472`) times `TICKSPERMUDDAY` times the tick length. The unit is mud days.
- **Quorum** is round(QUORUMPCT / 100 × voters), with a floor of 2, or 1 when there is a single voter (`:2478-2483`).
- A ballot closes when every voter has voted or the time runs out (`:2489-2494`). Fewer votes than quorum means FAILED. Yes must beat no, so a tie fails (`:2499-2512`).
- A passing ballot runs its stored command text. It does so through a factory mob at level 1000, placed in a start room and set to the top role for that function, with `METAFLAG_FORCED` (`:2515-2529`). A ballot is therefore a deferred, forced command rather than a recorded decision.
- A closed ballot stays for one more full duration measured from closing (`:2497`, `:2535-2540`). The ini note says votes "linger", and this is what it means.

### 5. Experience, levels and clan benefits

**Levels.** `DefaultClan.setExp` (`:596-632`) moves the level in a loop. While XP exceeds `f(level)` the level rises. While XP is below `f(level-1)` the level falls. `f` is the government's compiled formula with `@x1` set to the current level. The default is `(500 * @x1) + (1000 * @x1 * @x1 * @x1)` (`Common/DefaultClanGovernment.java:114`). No stock government overrides it: every `XPPERLEVELFORMULA` in the XML is empty. From the default, the thresholds are 1,500 XP for level 2, 9,000 for level 3, 28,500 for level 4 and 66,000 for level 5. Each step announces, bumps an achievement and saves (`DefaultClan.java:612-614`).

**Benefits.** `DefaultClanGovernment.getClanLevelAbilities` (`:1286-1380`) registers each ability in the government's list as a dynamic mapping in the ability mapper, keyed by the government name (`:1296-1328`). Each mapping carries a zapper mask `-CLAN "+*(<position IDs>)"` (`:1303-1316`). A `QUALIFYONLY` ability must be earned rather than granted (`:1322-1326`).

Effects use `getClanLevelEffectsList` (`:1382-1441`). They are wrapped in a `ChameleonList` (`:1480-1546`) that rebuilds lazily when the member's role, the clan level or the government changes (signaler at `:1500-1543`). Each granted effect is copied, bound to the member and made non-savable (`:1490-1496`).

**Help is generated from the same data.** `getHelpStr` (`:1071-1283`) prints the authority chart (positions across, functions down, `X` for CAN_DO and `v` for a ballot) and the benefits per level. `DefaultClan.getDetail` (`:1019-1281`) prints the profile: level and next threshold (`:1028-1036`), qualification masks (`:1040-1050`), tax, dues, rooms, members grouped by public position (`:1089-1106`), relations (`:1108-1141`), controlled areas with revolt chance (`:1168-1197`), trophies, achievements and clan benefits (`:1231-1279`).

### 6. Money: tax, shared XP, dues and banking

**Tax.** `taxRate` is a fraction from 0 to 1. `Commands/ClanTax.java:144` divides the typed percent by 100. `getDetail` shows `round(rate * 100)%` (`DefaultClan.java:1054-1055`).

`applyExpMods` (`DefaultClan.java:2611-2640`) takes the clan's share of a gain: `clanshare = round(exp × rate)` when exp is above 1. The clan receives it through `adjExp`, which feeds the level loop. The player keeps the rest. The monthly player-XP counter then counts the post-tax amount (`:2626`). Each trophy the clan holds applies its XP bonus in turn (`:2628-2635`, see section 8). The caller is `CoffeeLevels.gainClanExperience` (`Libraries/CoffeeLevels.java:604-615`), which runs for each clan the mob belongs to when the mob's level is at least `MINCLANLEVEL` and the amount is above 2.

On a loss, `CoffeeLevels.loseClanExperience` (`Libraries/CoffeeLevels.java:533`) uses the same share. The clan's share is subtracted from the mob's loss, and the clan's XP drops by the same amount (`:541-546`). The clan therefore absorbs its tax share of losses as well as taking its share of gains.

Conquered areas add a third route. For XP that a non-resident earns in an area a clan controls, `Behaviors/Conquerable.java:1025-1031` takes `value × rate` from the player's gain and credits the holding clan `adjustedExperience × rate`. The two bases differ (raw value and leveller-adjusted value), so the clan can earn slightly more than the player loses.

**Dues.** `Libraries/Clans.clanDues` (`Clans.java:1760-1887`) runs in the support tick (`:2384-2386`). It runs once per mud year. The next run is the last run plus `MILLIS_PER_MUDHOUR × hours-per-day × days-per-year` of the home clock (`:1763-1765`). Each member owes the clan's dues plus any arrears (`:1791`). The sweep takes the amount from coins on hand, including offline players' coins, and then from bank accounts, leaving 1.0 in each account (`:1838-1843`). Unpaid balances carry over (`:1851-1855`). A clan with dues but no bank account is told it needs one (`:1779`).

**Banking.** `getPreferredBanking` (`DefaultClan.java:2798-2824`) returns the bank chain with the largest positive balance. The result is cached for 10 mud hours (`:2820`). Dues and the coin prize both use it.

### 7. Relations and war

There are five declared states (`Clan.java:1054-1062`): NEUTRAL 0, WAR 1, HOSTILE 2, FRIENDLY 3 and ALLY 4. Each clan keeps its declared state towards every other clan (`DefaultClan.java:97`, `:703-729`). The combined state comes from `RELATIONSHIP_VECTOR` (`Clan.java:1066-1072`), indexed by the two declared states (row is A, column is B):

| A declares \ B declares | NEUTRAL | WAR | HOSTILE | FRIENDLY | ALLY |
|---|---|---|---|---|---|
| NEUTRAL | NEUTRAL | NEUTRAL | NEUTRAL | NEUTRAL | NEUTRAL |
| WAR | NEUTRAL | WAR | HOSTILE | NEUTRAL | NEUTRAL |
| HOSTILE | NEUTRAL | HOSTILE | HOSTILE | NEUTRAL | NEUTRAL |
| FRIENDLY | NEUTRAL | NEUTRAL | NEUTRAL | FRIENDLY | FRIENDLY |
| ALLY | NEUTRAL | NEUTRAL | NEUTRAL | FRIENDLY | ALLY |

So war needs both sides to declare it. Friendship needs both sides to be at least friendly. Alliance needs both to be allies. A one-sided declaration of war gives NEUTRAL, but a one-sided war against a hostile clan gives HOSTILE.

`getCommonClanRelations` (`Libraries/Clans.java:297-318`) adds a pact rule. If A is allied with a third clan C that is at war with B, or the reverse, then A and B are at war (condition at `:311-315`). Its precedence is loose; see Problems.

`isClanFriendly` (`Clans.java:87-125`) treats two members of the same rivalrous clan as friendly, and two members of rivalrous clans that are friendly or allied as friendly too. `isAtClanWar` (`:75-84`) checks rivalrous pairs for WAR. Rivalry has a government default (`DefaultClanGovernment.java:82`) and a per-clan override (`DefaultClan.java:789-804`). The interface describes rivalrous as "PvP is enabled between them, and war can be declared" (`Clan.java:331-337`). The combat code that reads it is outside this bucket.

### 8. Trophies

The `Trophy` enum has 15 entries. Each has a bit flag of 2^ordinal (`Common/interfaces/Clan.java:1094-1183`). Eight are permanent: control points, clan XP, areas, clan kills, members, median member level, player minutes and player levels gained. Seven are monthly, and reset on a calendar-month change.

`clanTrophyScan` (`Libraries/Clans.java:1889-2321`) runs in the support tick. For each trophy it finds the winner. The criteria are most members, highest median level with admins excluded (`:1604-1619`), most clan XP, most rival kills (`DefaultClan.java:384`, 365-day window), most levels gained, most player minutes, most areas and most control points. Clans with `NOTROPHY` are skipped. The winner gets the bit and an announcement on all CLANINFO channels (`Clans.java:1925`). The former holder loses the bit with a message (`:1935`). On ties, the comparisons are strict, so the current holder or the first clan found in table order keeps the trophy.

Prizes (`awardTrophyPrize`, `Clans.java:1634-1758`) are set in the ini:
- `EXPERIENCE` is a per-player XP bonus, applied in `adjustXPAward` (`Clans.java:2324-2359`).
- `COINS` or `GOLD` deposits money into the clan's bank account.
- `PRACTICES` and `TRAINS` place one pill per point in the donation room. Each pill carries a `Prop_HaveZapper` mask `-CLAN +"<clan name>"`, so only members can use it (`Clans.java:1709-1720`, `:1741-1752`).

XP bonuses compound. `adjustXPAward` multiplies by (1 + percent) for each held XP trophy, one after another (`Clans.java:2324-2359`, `DefaultClan.java:2628-2635`). With the default percentages and all eight permanent trophies held, a post-tax gain is multiplied by about 1.50 (1.10 × 1.05 × 1.05 × 1.10 × 1.03⁴; computed from `coffeemud.ini:1631-1638`).

**Monthly cycle.** When the calendar month changes, the monthly trophies are awarded and the monthly counters reset (`Clans.java:2259-2318`). Clans with zero monthly player minutes are set to STAGNANT (`:2315-2316`). The tick never clears STAGNANT. It is cleared when a member logs in (`Libraries/CharCreation.java:4462-4465`). `Commands/ClanList.java:130-133` shows FADING and STAGNANT clans as "Inactive".

### 9. Maintenance tick: promotion, fading and purge

`DefaultClan.tick` (`DefaultClan.java:1967-2555`) is registered for every clan when it is added (`Libraries/Clans.java:512`). It therefore runs every `TICKTIME` (4000 ms by default, `coffeemud.ini:2151`).

**Timing.** Computed from `core/CMProps.java:2720-2726` and the defaults: 15 ticks per real minute and 21,600 per real day. The daily work runs when `tickUp` reaches `getTicksPerDay()` (`DefaultClan.java:2022-2024`). Every `getTicksPerMudHour()` ticks, which is 150 with `MILLISPERMUDHOUR=600000` (`coffeemud.ini:1099`), the online members' minutes are added to the player-time trophies (`DefaultClan.java:2000-2019`).

**Daily thresholds:**
- **Activity window.** `DAYSCLANDEATH` (default 2160 mud days, `coffeemud.ini:1573`). A member is active if they logged in within that span (`DefaultClan.java:2026`, `:2030-2038`). Zero disables the purge (`:2036`).
- **Overthrow window.** `DAYSCLANOVERTHROW` (default 952, `coffeemud.ini:1584`). Used to choose leaders who are still around (`DefaultClan.java:2027`, `:2056-2073`).
- **Purge grace.** Three real days (`DefaultClan.java:2028`).

**Auto-promotion** (`DefaultClan.java:2044-2368`) runs only if the government's `autoPromoteBy` is not NONE and enough members are active. The candidate pool is members active inside the overthrow window. Applicants are excluded when the government has a separate accept role and someone holds the ACCEPT power (`:2056-2066`, `:2070`). If nobody qualifies, the pool widens to everyone, then to all members (`:2074-2099`). Candidates are sorted by the mode:
- LEVEL: highest level.
- RANK: most senior role.
- GOLD or XP: most donated gold or XP.
- JOINDATE: earliest join date.

The `_OVERWRITE` variants let the sorted candidates displace incumbents. Without the suffix, only empty seats are filled (`:2107-2197`, `:2251-2272`, `:2274-2291`). Only top-ranked roles that have ASSIGN are filled. If no role has ASSIGN, every non-applicant role is filled (`:2207-2221`). The seat count comes from `getMostInRole` (`:1926-1939`), which is a share of membership with a minimum of one seat, or an absolute number. A candidate must pass the position's inner mask (`:2298-2302`, `:1305-1317`). A displaced incumbent moves to the accept role (`:2313-2328`). Every change is announced ("X is now a Y of the ...", `:2341`) and logged (`:2342-2348`).

**Fading and purge** (`DefaultClan.java:2369-2438`). If active members are below the minimum, or no active member holds a top role, and the clan is not protected, it goes FADING. A clan is protected when its minimum is zero or the player library's no-purge check passes (`:1960-1965`). On the first pass, `setStatus` records `lastStatusChange` (`:1652-1657`). The clan emails its top-role members and announces a warning (`:2392-2428`). If it is still fading three real days later, the clan is destroyed and the log lists the membership (`:2373-2387`). When the clan recovers, FADING and PENDING return to ACTIVE (`:2440-2452`). A protected clan that is not active is set to ACTIVE (`:2432-2437`). A new clan starts PENDING (`Commands/ClanCreate.java:256`) and activates once it has enough members.

**Votes** run after the promotion and fading steps (`:2457-2545`, see section 4). The tick ends by saving the clan and its items (`:2547-2548`).

### 10. Messages players see

| Situation | Text (abridged) | Where |
|---|---|---|
| Level up | "<Gov> <Clan> has attained clan level N!" | `DefaultClan.java:612` |
| Level down | "... has reverted to clan level N!" | `DefaultClan.java:626` |
| Auto-promotion | "<Name> is now a <Position> of the <Gov> <Clan>." | `DefaultClan.java:2341` |
| Purge warning | "... is in danger of being deleted if more members do not log on within 24 hours." | `DefaultClan.java:2428` (see Problems) |
| Recovery | "... is no longer in danger of being deleted." | `DefaultClan.java:2444` |
| Restricted action | "This matter must be voted upon. Would you like to start the vote now (y/N)?" | `Libraries/Clans.java:704` |
| Vote started | "Your vote has started. Use CLANVOTE to cast your vote." | `Clans.java:749` |
| Trophy won | "The <Gov> <Clan> has been awarded the trophy for <criterion>." | `Clans.java:1925` and others |
| Dues, no bank | "The <Gov> <Clan> requires a bank account to collect dues." | `Clans.java:1779` |

## Content worth mining

### Stock governments

The 11 types in `resources/clangovernments.xml`. The format is Name (role/rank/seats), listed top to bottom. Rank 0 is most senior. A seat count that is left out, or shown as ∞, means unlimited (the file uses 999999 or the Java maximum).

| # | Type (category) | Ladder | Promotion | Votes (days, quorum %) | Character |
|---|---|---|---|---|---|
| 0 | Gang | Boss (6/0/1), Lieutenant (5/1/∞), Treasurer (4/2/1), Enchanter (3/3/3), Staff (2/4), Member (1/5/∞), Applicant (0/6) | Rank | none | One boss with 27 of 29 powers, no vote powers |
| 1 | Guild | Guildmaster (6/0/5), Secretary (5/1/∞), Treasurer (4/2/1), Enchanter (3/3/3), Chief (2/4/∞), Member (1/5), Applicant (0/6) | Rank | 24, 100 (Assign, Set Donate, Declare, Set Home, Premise, Tax) | Several bosses; the big decisions go to ballot |
| 2 | Union | Senator (6/0/5), Secretary (5/1/1), Treasurer (4/2/1), Enchanter (3/3/3), Sheriff (2/4/∞), Citizen (1/5), Applicant (0/6) | Rank | 48, 66 (Guild's list plus Accept, Reject, Exile) | Elected leaders; accepts Citizens |
| 3 | Fellowship | First Citizen (6/0/1), Manager (5/1/∞), Treasurer (4/2/∞), Enchanter (3/3/∞), Soldier (2/4/∞), Citizen (1/5), Applicant (0/6) | Rank | 100, 33 (Union's list) | Everything by vote; accepts Citizens |
| 4 | Theocracy | High Priest (6/0/1), Priest (5/1/∞), Treasurer (4/2/1), Enchanter (3/3/3), Crusader (2/4/∞), Believer (1/5/∞), Applicant (0/6) | Level, overwrite | none | Clerics; masks require cleric class or the same deity; conquest by worship |
| 5 | Family | Patron (6/0/1) and Matron (5/0/1), Mistress (4/1), Mister (3/1), Neuter (7/1), Cousin (2/2), Child (1/3), Blacksheep (0/4) | none | none | Family-only; minimum 1 member; seats split by gender masks |
| 6 | Anarchy | Freeman (0/0/∞) | none | none | Anyone joins; all 20 powers |
| 7 | Profession (CLASSCLAN) | Officer (2/0/∞), Member (1/1/∞), Applicant (0/2) | none | 10, 66 (ten functions) | Not public, not rivalrous, no conquest; grants a class; no benefits |
| 8 | Co-op | House Mother (3/0/1) and House Father (2/0/1), gender masks; Member (1/1); Applicant (0/2) | Rank | none | Two heads; "neither boss can tax, order underlings, or do clan relations" |
| 9 | Meritocracy | High Entrepreneur (5/0/2%), Entrepreneur (4/1/5%), Diligent Worker (3/2/10%), Hard Worker (2/3/50%), Worker (1/4/∞), Applicant (0/5) | XP, overwrite | none | Seats are shares of membership; top seats go to the biggest XP donors |
| 10 | Plutocracy | Tycoon (5/0/1%), Mogul (4/1/3%), Landlord (3/2/5%), Tennant (2/3/50%), Serf (1/4/∞), Applicant (0/5) | Gold, overwrite | none | Top seats go to the biggest gold donors |

Benefits are sized to match. Most types grant 15 or 16 abilities and 17 effects. Profession grants none. Gang's list shows the pattern:

| Clan level | Grant | Mode |
|---|---|---|
| 2 | Spell_ClanDonate | Automatic |
| 3 | Spell_ClanHome; Prop_Adjuster (ATTACK+1, HITPOINTS+4) | Automatic |
| 4 to 10 | Ten equipment-enchant spells (Spell_CEq…) | Earned (qualify-only) |
| 5 | Spell_ClanWard | Earned |
| 11 | Spell_Flagportation | Earned |
| Several levels from 3 to 20 | Prop_Adjuster stat bonuses | Effect |
| 20 | Spell_ImprovedClanWard | Earned |
| 21 and 22 | Prop_ModExperience (+10% to self) | Effect |
| 23 to 25 | Prop_ModFaction (reaction changes) | Effect |

### Permission functions

The 29 functions group naturally, and a small game would need only a subset. Membership: ACCEPT, REJECT, ASSIGN, EXILE. Property: SET_HOME, SET_DONATE, PROPERTY_OWNER, MORGUE. Money: DEPOSIT, WITHDRAW, DEPOSIT_LIST, TAX. Politics: DECLARE, PREMISE, VOTE_ASSIGN, VOTE_OTHER, ENCHANT. Orders: ORDER_UNDERLINGS, ORDER_CONQUERED. Records and channels: CHANNEL, READ_MOTD, CREATE_MOTD, JOURNAL, CREATE_LAW, CREATE_CALENDAR. Benefits: CLAN_BENEFITS, CLAN_TITLES, HOME_PRIVS, LIST_MEMBERS.

### Trophies and prizes

| Trophy (ini key) | Measure | Default award |
|---|---|---|
| Control points (CLANTROPCP) | Most control points from conquest | 5% XP |
| Clan experience (CLANTROPEXP) | Most clan XP | 5% XP |
| Areas (CLANTROPAREA) | Most areas controlled | 10% XP |
| Clan kills (CLANTROPPK) | Most rival kills (365-day window) | 10% XP |
| Members (CLANTROPMB) | Most members | 3% XP |
| Median level (CLANTROPLVL) | Highest median member level, admins excluded | 3% XP |
| Player levels (CLANTROPPLVL) | Most member level-ups | 3% XP |
| Player time (CLANTROPPTIME) | Most member minutes | 3% XP |
| Seven monthly trophies (CLANTROPMONTHLY…) | Same measures, for the calendar month | 2% XP each |

Source: `coffeemud.ini:1631-1645`. Prize types are EXPERIENCE, PRACTICES, TRAINS and COINS (`coffeemud.ini:1622-1630`).

### Constants and defaults

| Setting | Value | Source |
|---|---|---|
| Tick length | 4000 ms | `coffeemud.ini:2151` |
| Mud hour | 600000 ms (10 real minutes) | `coffeemud.ini:1099` |
| Min members to stay alive | 1 (0 means never purged) | `coffeemud.ini:1558` |
| Min clan level to earn clan XP | `MINCLANLEVEL` = 1 | `coffeemud.ini:1551` |
| Death window | 2160 mud days | `coffeemud.ini:1573` |
| Overthrow window | 952 mud days | `coffeemud.ini:1584` |
| Max categories per player | `MAXCLANCATS` = 1 | `coffeemud.ini:1664` |
| Gold to found a clan (`CLANCOST`) | 10000 | `coffeemud.ini:1655`, `Commands/ClanCreate.java:85` |
| Enchant cost | 1000 clan XP per point | `coffeemud.ini:1650` |
| Default XP formula | (500L) + (1000L³) | `Common/DefaultClanGovernment.java:114` |
| Default vote days | 54 (if MAXDAYS ≤ 0) | `DefaultClan.java:2472` |
| Purge grace | 3 real days | `DefaultClan.java:2028` |
| Ally pact rule | ally of an enemy is an enemy | `Libraries/Clans.java:311-315` |

## Notable design ideas

1. **A permission table as the whole rulebook.** Twenty-nine functions times ranks times {no, yes, ballot} is a compact rules engine. Help text (`getHelpStr`) and profile text (`getDetail`) are generated from the same table, so they cannot drift from the rules.
2. **A ballot is a stored command.** A passing vote re-runs its matter text as a forced action at level 1000 under the top role (`DefaultClan.java:2515-2529`). This is elegant and easy to extend, but it re-parses text at the moment of execution.
3. **Promotion modes with overwrite variants and fallback pools.** Each mode is a sort key. The overwrite flag decides whether candidates may displace incumbents. The fallback pools (`:2074-2099`) make sure a clan that loses its active leaders still has someone in charge.
4. **Benefits by role through the ability mapper.** Role gating reuses the general zapper language (`-CLAN "+*(...)"`, `DefaultClanGovernment.java:1303-1316`). The lazily rebuilt `ChameleonList` (`:1480-1546`) keeps granted effects in step with role and level without explicit refresh calls.
5. **Five-state diplomacy from one table.** A 5×5 vector plus the ally-inherits-war rule gives a full diplomacy model with almost no numbers to tune.
6. **Tax on both gains and losses.** The clan literally shares the risk of XP, which gives the government a financial stake in its members' play.
7. **Trophies with in-world prizes.** Practice and training pills are real items with a clan zapper mask. Coin prizes go into a bank account, so the prize is spendable inside the economy.
8. **A visible grace path.** FADING gives warnings and three real days to recover. STAGNANT clears itself when a member logs in. Players can see the state and act on it.
9. **Conquest by ruling organisation.** Control is a property of areas (`LegalBehavior` ruling organisation, `DefaultClan.java:511-527`). The theocracy's worship-based conquest (`Clan.java:801`, `Conquerable.java:1015`) is a second route to the same state.
10. **Dues as debt.** Dues accrue as arrears and carry over partial payments (`Clans.java:1791`, `:1851-1855`). Nothing is lost by a short payment.

## Problems and cruft

Each item is a bug or a doc or code mismatch found by reading. Impact is stated where it was checked.

**Bugs**
1. `Libraries/Clans.java:71`: `isCommonClanRelations` passes `clanID1` twice to `getCommonClanRelations`. The method has no callers in the tree. Latent.
2. `Libraries/Clans.java:2244`: the Points (control points) trophy awards the **Areas** prize (`Trophy.Areas`).
3. `Common/DefaultClan.java:2653` and `:2659`: `level` is set to -1 and never updated. The "responsible member" is therefore the last top-role member with any level, not the highest. This value feeds `getLoadResponsibleMember` (`:2642-2645`), the worship ID substituted into requirement masks (`:1334-1350`), and the `TOPMEMBER` stat (`:2925-2929`).
4. `Common/DefaultClan.java:1387`: `getRealMemberList` returns `members` instead of the filtered `realMembers`. No callers.
5. `Common/DefaultClan.java:1575-1577`: `setDataXML` assigns `exp` directly and then calls `setExp` with the same value. The level-change loop never runs, so the stored LEVEL is trusted without checking XP.
6. `Common/DefaultClan.java:1854`: `rec.dues` is set to the delta rather than the new total. The database update itself is right (`core/database/MOBloader.java:2215`). Impact is in memory only and small.
7. `Common/DefaultClanPosition.java:336`: `MAX` is parsed with `CMath.s_int`, which returns 0 for "5%" or "0.05" (`core/CMath.java:2104-2114`). The XML path uses `s_pct` (`Libraries/Clans.java:1300-1303`). Which editor path reaches this setter was not confirmed.
8. `Libraries/Clans.java:1383-1385`: `AutoPromoteFlag.valueOf` throws on an unknown or null name, so the null check after it is dead. A hand-written file without a `VOTING` block (`:1236-1238`) or a `POSITIONS` block (`:1241-1242`) throws a NullPointerException.
9. `Common/DefaultClanGovernment.java:705` and `:707`, and `Common/DefaultClan.java:1299` and `:1309`: bounds checks use `>` where `>=` is meant, so an out-of-range ID throws. Only reachable with bad data.
10. `Common/DefaultClanGovernment.java:602-612`: `delPosition` shifts array indices but keeps the role IDs. Lookups that index by role (`DefaultClan.java:1322-1324`, `:2227-2228`, `:2337-2338`) then read the wrong position. Editors can reach this.
11. `Libraries/Clans.java:311-315`: the ally-pact condition has a precedence problem. The second `||` branch is not guarded by `C != C1` or `C != C2`. In practice this is harmless if self-relations are neutral, which was not verified.
12. `Libraries/Clans.java:2141-2142`: the player-minutes trophy's debug line prints the player-levels figure.

**Documentation that disagrees with the code**
13. `Common/interfaces/Clan.java:595-596`: dues are "per rl day". The code collects once per mud year (`Libraries/Clans.java:1763-1765`).
14. `Common/interfaces/Clan.java:583`: tax rate is documented as "0-100.0". It is a fraction (`Commands/ClanTax.java:144`, and the editor prompt "X 100%" at `Libraries/CMGenEditor.java:12093`).
15. `Common/interfaces/Clan.java:154-155` and `:164-165`: "See also MAXCLANS". No such key exists. The real key is `MAXCLANCATS` (`coffeemud.ini:1664`, `core/CMProps.java:3138`).
16. `Common/interfaces/ClanGovernment.java:261-262`: `@x2` is documented as the government name. The code passes the position name (`DefaultClan.java:892`, `:894`), which is what the stock title `*, @x2 of @x1` needs.
17. `Common/interfaces/Clan.java:127-131`: "clans can't change their names". `setStat("NAME")` allows it (`DefaultClan.java:3020-3021`), and the database key follows the name.
18. `coffeemud.ini:1597-1599`: `CLANVOTEO`, `CLANVOTED` and `CLANVOTER` are read by no Java code. Votes use the government's `MAXDAYS` and `QUORUMPCT` (`DefaultClan.java:2470-2478`).
19. `coffeemud.ini:1607-1608`: the rival-kill trophy is described as "the last rl month". The code keeps kills for 365 real days (`DefaultClan.java:384`).
20. `coffeemud.ini:1571-1573`: "24 days equals 1 RL day". With `MILLISPERMUDHOUR=600000` (`coffeemud.ini:1099`) and 24 mud hours per mud day, the real ratio would be 6 mud days per real day. The setter for `TICKSPERMUDDAY` was not located, so this is unverified.

**Hard-coded values that should be settings**
21. `Libraries/Clans.java:684` (5 open votes per starter), `DefaultClan.java:2472` (54-day vote default), `:2028` (3-day purge grace), `:2480` (quorum floor of 2), `:384` (365-day kill window), `:2820` (10-hour bank cache).
22. `Libraries/Clans.java:2342-2343`: percentage XP bonuses are parsed as integers, so "2.5%" truncates. `:2355` is unreachable, because the award type is never a `Long`.
23. `Libraries/Clans.java:1889-2321`: about 430 lines of near-identical trophy blocks. One helper parameterised by the criterion would replace them.

**Multiplayer-only assumptions**
24. Inactivity purge and fading depend on members logging in (`DefaultClan.java:2030-2038`). Elections depend on many voters. Email warnings (`:2404-2411`). Cross-host lookups (`Libraries/Clans.java:365-438`). Per-character worship in masks (`DefaultClan.java:1348`). Clan channels (`:2582-2584`). None of these have a single-player meaning as written.

## Takeaways for a Python SUD

**What to borrow**
- **Government as data.** Keep the stock governments as TOML or YAML, and validate them at load time with the checks the Java parser already makes (`Libraries/Clans.java:1331-1347`, `:1353-1376`, `:1421-1432`). The stock file is a ready-made template for NPC factions.
- **A permission table.** Use an `Enum` for functions (trim to 12 to 15) and an `Enum` for `Authority` (no, yes, ballot). Store each position's permissions as a dict. Use it for both checks and generated help text.
- **Vote-gated actions.** A ballot is a small dataclass holding the function, the starter, the votes, the quorum and a callable to run on pass. Resolve it on the in-game day boundary. Pass the callable rather than re-parsing command text, which the Java version does.
- **Levels from XP.** Derive the level from XP every time rather than storing it:

```python
def level_for_xp(xp: int) -> int:
    level = 1
    while xp > 500 * level + 1000 * level ** 3:   # DefaultClan.setExp, default formula
        level += 1
    return level

def tax_share(xp: int, rate: float) -> int:
    share = int(xp * rate + 0.5)                  # Java Math.round semantics for positives
    return share if xp > 1 else 0
```

- **Diplomacy.** A 5×5 table for the combined state, plus the ally-inherits-war rule as a separate pass:

```python
from enum import IntEnum

class Rel(IntEnum):
    NEUTRAL = 0; WAR = 1; HOSTILE = 2; FRIENDLY = 3; ALLY = 4

N, W, H, F, A = Rel.NEUTRAL, Rel.WAR, Rel.HOSTILE, Rel.FRIENDLY, Rel.ALLY
COMBINED = [          # row: A's declaration, column: B's (Clan.java:1066-1072)
    [N, N, N, N, N],
    [N, W, H, N, N],
    [N, H, H, N, N],
    [N, N, N, F, F],
    [N, N, N, F, A],
]
```

- **Benefits by rank.** Store each benefit as (level, grant, role set, earned-or-automatic). Compute the granted list from the level and the member's role. Do not cache it across changes unless you invalidate it on every change, which is what the Java `ChameleonList` is for.
- **Position objects with stable IDs.** Give each position a permanent role ID and key the dict by it. Never index a list by role ID, and forbid deletion, or re-key on delete. This is the mistake in Problems 10 and 9.
- **Taxes on both sides.** Apply the same share function on gains and on losses.
- **Dues as arrears.** Carry partial payments forward. It is simpler than the Java bank walk and avoids losing money on short payments.

**What to simplify**
- Replace the database strings with plain objects serialised with the rest of the save data. The Java XML-in-a-string design is there for multi-host persistence, which a single-player game does not need.
- Use one clock: in-game days for everything. Drop the real-time grace period, the email warnings and the mud-hour and real-day mix.
- Collapse the trophy scan into one loop over a `Trophy` enum with a scoring function, and use Python's `enum.Flag` for the held trophies.
- Drop channels and cross-host lookups. Use an event log that the UI can read.

**What to drop**
- Clan-to-clan PvP and rivalry flags as stored settings. Keep the diplomacy table, and let combat read it.
- The purge as written. Replace it with succession: when a faction's leader is dead or gone for long enough, the next eligible NPC takes the seat. Inactivity becomes "the leader has not been seen for N in-game days". Fading becomes a faction that has lost all its leaders and disbands.

**Single-player implications**
- The player is the only human, so a clan the player belongs to is either the player's own faction or an NPC faction they join. Keep the applicant → member path, with masks as predicates over the character (`Callable[[Character], bool]`). That maps directly onto the inner and requirement masks.
- A vote with one voter is decided at once (`Libraries/Clans.java:700-702`). A player alone in a faction gets decrees, not ballots. Ballots only make sense when NPC members vote.
- Trophies become a reputation or leaderboard across NPC factions. The XP bonuses are the part most worth keeping, because they reward belonging to a faction that is doing well.

## Coverage

- **Read in full (all 8 bucket files):** `Common/interfaces/Clan.java`, `Common/interfaces/ClanGovernment.java`, `Libraries/interfaces/ClanManager.java`, `Common/interfaces/ClanPosition.java`, `Common/DefaultClanPosition.java`, `Common/DefaultClanGovernment.java`, `Common/DefaultClan.java`, `Libraries/Clans.java`.
- **Data read:** `resources/clangovernments.xml`: the GANG type read in full. All 11 types were summarised with a script that parses the file, not read line by line. `coffeemud.ini` clan keys read at lines 1544-1665 and 2145-2151.
- **Read in part, for interactions:** `Libraries/CoffeeLevels.java` 533-557 and 604-615; `Behaviors/Conquerable.java` 1012-1034; `Commands/ClanTax.java` 140-153; `Commands/ClanList.java` 128-136; `Libraries/CharCreation.java` 4460-4470; `core/database/MOBloader.java` 2192-2232; `core/CMProps.java` 2716-2730 and 3536-3575; `core/CMath.java` 2104-2114.
- **Grep only:** the other 22 `Commands/Clan*.java` files (3752 lines), `Commands/ClanCreate.java`, `Commands/ClanApply.java`, `Commands/Create.java`, `Items/ClanItems/StdClanItem.java`, `WebMacros/grinder/GrinderClans.java`, `Libraries/CMGenEditor.java` and the consumers of trophies and conquest.
- **Skipped:** the clan items and clan spells beyond their names; the database implementation beyond the two functions named above; achievements, journals and law behaviour beyond their calls into this code.
- **Unverified:** the `TICKSPERMUDDAY` derivation (Problem 20); which editor paths call the position `setStat` for `MAX` (Problem 7); whether `DBGetClanMember` can return null in `getCurrentClanKills` (`DefaultClan.java:406-407`).
