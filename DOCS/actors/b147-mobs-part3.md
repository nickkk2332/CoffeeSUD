# Special-purpose MOBs: bankers, librarians, craft brokers, deities and factory mobs

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b147` (MOBS [part 3/4: StdBanker .. StdMOB]) · 6 files · 10773 lines of Java.

## Summary

This bucket holds the specialised mob types that sit on top of the generic creature. `StdBanker` runs deposit accounts that accrue interest once a mud month, lends coins against item collateral, and seizes an account's items into the bank's own stock when its balance goes negative. `StdLibrarian` lends catalogue items for a fixed period, charges late fees that scale with each item's pawn value, and, once an item is reclaimed, leaves the borrower owing at least its pawn value. `StdCraftBroker` is a request board: a player posts a wanted item as a zapper mask with a count and price, and a matching sale creates a pickup listing for the requester. `StdDeity` is the largest class: it turns worship into a faith score with tiers, watches the message flow for ritual phrases, grants blessings, curses and temporary powers, runs timed group services, and rebukes lapsed worshippers. `StdFactoryMOB` is a pooled, renameable placeholder actor that spells, behaviours and stat code borrow. `StdMOB` itself is covered in depth by b173. The three shopkeepers keep their state under a named chain, so several branch NPCs can share one ledger.

## Key files and types

Citation convention: `MOBS/StdBanker.java:297` means `com/planet_ink/coffee_mud/MOBS/StdBanker.java`, line 297, relative to the CoffeeMUD folder.

| File | Role | Lines |
|---|---|---|
| `MOBS/StdMOB.java` | Base of every mob. Depth is in b173; only the hooks used here are cited | 6287 |
| `MOBS/StdDeity.java` | Gods: faith, worship, rituals, blessings, curses, powers, services, rebukes | 1589 |
| `MOBS/StdBanker.java` | Bank: accounts, deposits, loans, monthly interest pass, defaults | 1104 |
| `MOBS/StdLibrarian.java` | Lending library: checkouts, due and reclaim dates, overdue fees | 914 |
| `MOBS/StdCraftBroker.java` | Request board and pickup shelves between players | 742 |
| `MOBS/StdFactoryMOB.java` | Pooled placeholder actor with a volatile name | 137 |

Supporting files read in part: `MOBS/StdShopKeeper.java` (the shop base, 655 lines); `MOBS/interfaces/Banker.java` (248); `MOBS/interfaces/Librarian.java` (324); `MOBS/interfaces/CraftBroker.java` (111); `MOBS/interfaces/Deity.java` (767); `core/collections/PrioritizingLimitedMap.java` (372); `core/CMClass.java` (3971, the MOB pool); `Libraries/CoffeeLevels.java:1078` (`postExperience`); `Libraries/MUDLaw.java:703` (cleric infusion); `core/database/PDataLoader.java:726` and `core/database/ADataLoader.java:629` (row inserts); `guides/database/coffeemuddbhsql.sql:430` (the `CMPDAT` table).

### Mob types in this bucket

| Type (ID) | Base | Constructor defaults | What it adds | Distinctive mechanic |
|---|---|---|---|---|
| `StdBanker` (`StdBanker`, ID line 43) | `StdShopKeeper` | alignment GOOD (`StdBanker.java:58`); deal mask BANKER = 12 (60); weight 150 (61); INT 16, CHA 25 (64-65); HP 1000 (69) | Bank accounts and loans | Monthly ledger pass with storage fees, loan installments and seizure (233-382) |
| `StdLibrarian` (ID line 47) | `StdShopKeeper` | GOOD (69); deal masks READABLES 33 and BOOKS 32 (71-72); weight 150; INT 18, CHA 7 (76-77); HP 1000 (81) | Lending library | Checkout records with due and reclaim dates; late fees; a derived stock view (360-393) |
| `StdCraftBroker` (ID line 52) | `StdShopKeeper` | GOOD (68); money 0 (69); WIS 16, CHA 8 (73-74); HP 1000 (78) | Player request board | Requests are item masks; pickup shelves; 10% commission (`StdCraftBroker.java:60`) |
| `StdDeity` (ID line 45) | `StdMOB` | weight 700 (81); ability 200 (82); attack adjustment 1000 (84); damage 1000 (85); race Spirit (86) | Gods | Faith tiers; rituals over the message flow; blessings, curses, powers; services; rebukes |
| `StdFactoryMOB` (ID line 46) | `StdMOB` | none; name kept in `volatileName` (41) | Temporary actor | Pooled through `CMClass` (`CMClass.java:3862-3913`) |

Grouping. The first three are one family. All extend `StdShopKeeper` (`StdBanker.java:38`, `StdLibrarian.java:42`, `StdCraftBroker.java:47`), each overrides `okMessage` and `executeMsg` with a switch on the message target type, and each stores its state under a chain name. `StdDeity` and `StdFactoryMOB` are different in kind: one is a worship engine on a plain `StdMOB`, the other a helper object. Their generic siblings `GenBanker`, `GenLibrarian`, `GenCraftBroker` and `GenDeity` are in bucket b146 and are not read here.

## How it works

### 1. The shopkeeper family

- **Dispatch.** Each class's `okMessage` and `executeMsg` switch on `msg.targetMinor()` (`StdBanker.java:502` and `853`; `StdLibrarian.java:539` and `793`; `StdCraftBroker.java:383` and `579`). When a case needs the plain mob behaviour instead of the shop rules, it calls `stdMOBokMessage` or `stdMOBexecuteMsg` (`StdShopKeeper.java:268-283`), for example `StdCraftBroker.java:692`, `698`, `StdBanker.java:1096` and `StdLibrarian.java:894`.
- **Chains.** The class's text field holds a chain name, set through `setMiscText` (`StdMOB.java:2054-2063`): `setBankChain` (`StdBanker.java:99-103`), `setLibraryChain` (`StdLibrarian.java:221-225`), `setBrokerChain` (`StdCraftBroker.java:92-96`). The chain names the storage. Bank rows use the chain as the player-data section (`StdBanker.java:137`). The library uses `LIBRARY_RECORDS_<CHAIN>` and `LIBRARY_SHOP_<CHAIN>` (`StdLibrarian.java:89-97`). The broker uses `BROKER_REQ_<CHAIN>` and `BROKER_SHOP_<CHAIN>` (`StdCraftBroker.java:108`, `256`). Two NPCs with the same chain share one ledger, which is how branch banks and branch libraries work.
- **Schedules are per chain, not per NPC.** The bank runs once per banking period under a shared timer (`StdBanker.java:246-259`). The library runs once per mud day, with its timer in a shared two-slot array (`StdLibrarian.java:328-336`, `421-424`). The broker runs once per mud day through a static map (`StdCraftBroker.java:55`, `344-346`).
- **Persistence.** Rows are written by `DBCreatePlayerData`, which is a plain SQL `INSERT` (`core/database/PDataLoader.java:726-746`) into a table keyed by (player, section, key) (`guides/database/coffeemuddbhsql.sql:430-436`). Key collisions matter; see problem 3.

### 2. StdBanker: accounts, interest and loans

**Constants** (`StdBanker.java:46-48`). `coinInterest` is zero (written `-0.000`). `itemInterest` is `-0.0001`. `loanInterest` is `0.01`. The pass runs once per banking period, where `timeInterval()` is hours per day times milliseconds per mud hour times days per month (`StdBanker.java:176-181`; the mud hour defaults to 600000 ms, `core/CMProps.java:682`). With the defaults, stored items cost 0.01% of their value each month and each loan accrues 1% of its outstanding amount each month.

**Accounts.** An account is keyed by depositor name, or by clan ID for a clan bank (`getBankClientName`, `StdBanker.java:471-494`). It holds one merged `Coins` item as the balance (rebuilt by `makeBestCurrency`, `StdBanker.java:534`, `361`), any deposited items, and debt records.

**Deposits** (executeMsg `504-594`; okMessage `855-910`).
- Coins merge into the balance (`StdBanker.java:533-534`). A ledger line "Deposit of ..." is written (`535`), and the banker's own cash is removed so it does not accumulate (`559-562`). The reply is "Ok, your new balance is X." (`543`).
- Items are stored as copies with their containers (`575`), and the originals are destroyed (`585`). The reply is "Thank you, X is safe with us." (`584`).
- An item is refused unless the balance covers a tenth of the account's stored items plus a tenth of the new one: `minbalance = totalItemsWorth/10 + value/10` (`StdBanker.java:900-901`; `MIN_ITEM_BALANCE_DIVISOR = 10.0`, `MOBS/interfaces/Banker.java:56`). Items are refused outright when the item rate is above 0.5 (`882-886`), and enspelled or burning items and caged animals are refused (`893-897`).

**Withdrawals** (executeMsg `622-720`; okMessage `911-1000`).
- Coins cannot take the balance below `totalItemsWorth/10` (`StdBanker.java:977-997`). Messages: "you may only withdraw X at this time" (`993`) and "I am holding other items in trust, so you may not withdraw funds at this time." (`995`).
- An item that is collateral against a debt cannot be taken (`965-976`).
- Withdrawn items are placed in the room and then handed over. If the hand-over fails they stay in the room (`696-715`).

**Loans** (BORROW: okMessage `1007-1061`; executeMsg `595-621`).
- Only coins can be borrowed (`1011-1015`). The borrower needs an account (`1024-1036`) and no outstanding debt (`1037-1047`).
- Collateral: `collateralRemaining = coins - totalItemsPawningWorth` must be zero or less (`1048-1059`), where the pawn value comes from the shop's pricing.
- Term: the loop starts at two months and adds one month while `amount / months > 250` (`614-616`), capped at ten years of months. The due date is now plus months times the period (`617`). The debt records the loan rate and due date (`618`).

**The monthly pass** (`ProcessAccounts`, `StdBanker.java:233-382`; scheduled at `246-259`).
1. Accounts whose owner no longer exists are erased with no ledger entry, unless the name is a clan (`263-280`).
2. Per account, the balance becomes `coins × (1 + coinInterest) + itemsValue × itemInterest` (`297-302`). With the defaults, storage fees come out of the coin balance.
3. Per debt, the interest is `rate × owed` (`313`).
   - If the debt is past due and the balance is below owed plus interest, the account defaults (`315-316`).
   - If it is not yet due, the installment is `(owed + interest) / (timeRemaining / period)` (`319`). It is paid when the balance covers it (`320-325`). Otherwise only the interest is added to the debt (`327`).
4. **Default** (`newBalance < 0`, `332-348`). Land titles lose their owner (`337-342`). Every non-coin item goes into the bank's own shop stock (`343-344`). All deposits and debts of the account are erased (`346-347`), so the coin balance is lost too.
5. Otherwise the new coin balance is written, with a ledger line "Interest paid" or "Interest charged" (`349-364`).
6. The items read from the database are destroyed (`365-370`), and debts left without an account are cleared (`372-373`).

Note on step 3: the installment divides by whole periods. In the final period it divides by zero, see problem 8.

**Clans and liege.** `DEAL_CLANBANKER` (23, `core/interfaces/ShopKeeper.java:91`) makes the account the clan's. Deposits are credited to the clan (`StdBanker.java:548-550`), and messages name the clan (`541`, `680`). A married player with no account of their own deposits into the liege's account (`522-531`), and can withdraw from it when their own balance is short (`631-644`, `936-950`). The married-to-liege link is also used by property law (`Libraries/MUDLaw.java:470`).

**Statement** (LIST, `StdBanker.java:729-836`). "Account balance at 'chain'.", the deposited items in two columns (`762-788`), "Your balance is X." (`795`), each debt with its monthly rate and months left (`809-811`), and the interest policy as "They pay/charge X% interest monthly on money deposited here." (`815-826`).

### 3. StdLibrarian: checkouts, due dates and late fees

**Constants** (`MOBS/interfaces/Librarian.java:57-98`). Base late charge 1.0. Plus 10% of the item's pawn value. Per late day, 1.0 plus 10% of value. Due after 3 mud days, reclaimed after 15. Borrowing limit 5. The stock is books and readables (`StdLibrarian.java:71-72`).

**Catalogue and view** (`StdLibrarian.java:360-393`). The master `shop` is the catalogue. A player sees a copy in which one copy of each checked-out title has been removed (`381-383`). The copy is rebuilt when a change is newer than the last build (`371`). The view is used only after the first non-staff player acts in the room (`532-536`, `362`). Before that, borrowing gets "Please come back a little later." (`869-874`). A restock only clears the cache (`348-351`).

**Borrowing** (okMessage `835-876`; executeMsg `674-718`). It is refused when the thing is not an item (`840-844`), is not in stock ("We don't stock anything like that.", `849`), the borrower has overdue charges (`852-858`), the borrower is at the limit (`859-863`, "I'm sorry, but you may only borrow X items."), or the borrower already has that title (`864-868`). Otherwise one copy is taken from the view (`697`), placed in the room and handed over (`699-710`), and a record is stored (`711-712`). Due date is now plus 3 mud days and reclaim is now plus 15 (`685-692`). The reply is "There ya go! This is due back here by X!" (`698`).

**Late fees** (tick `407-467`; `processCheckedOutRecord` `469-518`).
- Once per mud day (`421-424`). Records for players who no longer exist are dropped (`446-454`).
- After the due date the charge is `base + pct × value`. For each whole late day `d` it adds `d × (daily + dailyPct × value)` (`482-497`). With the defaults the total is `(1 + d) × (1 + 0.1 × value)`. The value is the pawn price of the item in the master catalogue (`482`).
- The charge is recalculated from the due date on each pass, not accumulated (`498-502`).
- After the reclaim date the charge is raised to at least the item's pawn value (`504-511`), and the item name is cleared (`512`). The record then stands for money owed, with no item out.

**Returns** (GIVE: okMessage `795-834`; executeMsg `541-664`).
- Accepted only for an item that is in stock or has a record under its name (`817-818`). Staff can add stock (`819-824`). Otherwise "That item was not checked out here." (`828`).
- The record is looked up by the returner's name, then the leader's name, then the property owner's name for NPCs (`600-608`), then any record for that item name, with "I assume you are returning this for X." (`609-622`). If nothing matches, the answer is "What is this?!" and the library keeps the item (`625-627`).
- The item is destroyed (`629`). If charges are due and the customer's funds cover them, they are paid and the record is removed (`631-653`; "Charges due for this were X. Thank you!", `646`). If not, the record stays as a charge-only record and the customer is told to pay in person (`634-642`).
- Coins given against overdue charges are applied record by record, in list order, with change returned (`552-596`).

**Donations.** SELL and VALUE are refused unless the giver passes `contributorMask` (`877-884`): "I'm afraid you lack the credentials to contribute to our stock."

**Records** (`StdLibrarian.java:99-133`, `187-213`). Each copy is a `CheckedOutRecord` (`Librarian.java:106-127`). Records are stored as XML objects (`xml.fromPOJOtoXML`, `StdLibrarian.java:202`) in chain-keyed area data, and loaded once into a process-wide resource (`102-106`).

### 4. StdCraftBroker: a request board

**Defaults** (`StdCraftBroker.java:57-60`). Listings last 900 mud days. A player may hold 5 listings. Commission is 10%. The `currency` field is never used (problem 4).

**Requests.** A player posts "count, item mask, price" with the `BROKERADD` message (executeMsg `492-527`). The item mask is a zapper mask (`CMLib.masking().parseSpecialItemMask`, `StdCraftBroker.java:507`; matched at `183-186`). The price is parsed as money in the broker's currency (`506`, `598-606`). Each request is one row keyed by the requester (`149-159`), with `NUM`, `PRICE` and an `EXPIRE` stamp (`137-147`). "CANCEL" removes all of the player's requests (`500`). Malformed postings get "Your new listing is too badly formatted to list." (`611`). A good posting is confirmed with "Your new listing is posted and will remain for the next N days." (`515-516`).

**Fulfilment** (TYP_SELL: okMessage `644-679`; executeMsg `394-439`).
- The broker must deal in the item (`649-653`). It refuses stolen goods unless it is evil and not lawful (`655-662`). The item must match a request, otherwise "That item does not fulfill any listed request. Try LIST." (`671-677`).
- The broker buys wholesale (`416`) and takes the payout from its budget (`419`). Units are counted by kind: a package counts its contents, raw material counts its weight, a bundle counts its size (`406-415`). Each unit removes one count from the request (`423-424`).
- A pickup is added for the requester at `request price + 10% of the payout` (`427-428`). It is saved to the requester's own shelf (`434-435`). Requests with no count left are removed (`429-433`).

**Pickups and listings.** BUY takes from the buyer's own shelf (`456-491`). LIST shows the public "Item Requests" and the viewer's private "Item Pickups" (`534-562`). VIEW describes a stocked item (`440-455`).

**Expiry.** Once per mud day the broker deletes rows past their `EXPIRE` stamp, in both sections (`347-363`).

### 5. StdDeity: gods, worship and rituals

**5.1 Faith tiers.** Faith is `CharStats.STAT_FAITH` (`Common/interfaces/CharStats.java:136`). The code uses four thresholds.
- 25: the worshipper sees the worship requirements (`Commands/Deities.java:99`).
- 100: a cleric. Clerics are held to the cleric requirements (`StdDeity.java:293`), receive cleric rituals and powers (`496`, `572`, `797`), and receive every blessing and curse (`496-505`, `572-580`).
- 1000: may start a service without an infused room (`StdDeity.java:832-833`).
- Sources: the cleric class adds 150 (`CharClasses/Cleric.java:384`); False Faith adds 25 (`Abilities/Thief/Thief_FalseFaith.java:111`).

**5.2 Serving.** `TYP_SERVE` is refused if the player already worships someone (`StdDeity.java:277-292`). A cleric must pass the cleric mask (`295`); anyone else the worship mask (`304`). Refusals read "You are unworthy of serving X." (`297`) or "You are unworthy of X." (`306`). On success the worship ID is set to the deity's `name()` (`711`).

**5.3 Rituals.** Each deity owns a `Triggerer` with six ritual types (`MOBS/interfaces/Deity.java:145-153`). A ritual is a trigger string such as `SAY Bless me <name>`. Defaults are set in `StdDeity.java:161` and `193`. The default service is `SAY Bless us <name>&wait 10&wait 10&ALLSAY Amen.&SAY Go in peace` (`215`). The deity sees the messages that come through its room (the comment at `787` says it sees them "through many many eyes"). For each message whose source is a worshipper, it checks whether each ritual is being tracked and whether it has been completed (`786-847`). ENTER, LEAVE, LOOK and RITUAL messages are ignored (`67-72`, `789`). A `wait` pauses a ritual, and the tick resumes finished waits by replaying an OK_VISUAL message for each (`1242-1248`). The `Triggerer` internals were not traced.

**5.4 Blessings, curses and powers.**
- **Blessings** (`bestowBlessings`, `StdDeity.java:480-532`). A worshipper gets each blessing once (`486`). Clerics and sysops receive all of them (`496-505`). Other worshippers receive one at random (`508-520`). The message is "You feel the presence of X in Y." (`495`).
- **Delivery** (`bestowBlessing`, `325-405`). The deity walks to the worshipper's room (`340`), casts the blessing on the worshipper, on the wielded, held, worn or carried item, then returns home (`386-397`). If the room is infused with this deity, the blessing's duration is multiplied by 1.5 (`339`, `380-383`).
- **Curses** (`bestowCurses`, `557-608`). Clerics receive all of them. Others receive one random non-cleric curse (`572-596`). The message is "You feel the wrath of X in Y." (`571`).
- **Powers** (`bestowPowers`, `534-555`). For a cleric with none present, one random power is picked (gate at `817-827`, pick at `539-542`). The copy is granted at the mob's maximum proficiency and marked not savable (`412-414`). It is granted only when the ability mapper gives the mob no qualifying level (`409-410`). The comment at `1504` says the powers are meant to be ones the cleric cannot qualify for. Because they are not savable, `removePowers` removes them when a cleric is rebuked (`627-654`).

**5.5 Rebukes and blackmarks.** Every 31 ticks the deity checks every online player (`rebukeCheckDown` at `1122-1125`, player loop at `1126-1131`). A worshipper who fails the requirement gets a blackmark (`1181-1182`). A cleric who fails gets one too (`1141-1142`). When the count passes 30 (the 31st failed check), the cleric is rebuked (`1142-1149`). Blessings and powers are removed (`724`, `727`), and one class level is taken (`728-730`). A non-cleric instead loses experience, starting at `xpwrath = 100` (`StdDeity.java:48`, `734-736`). Every rebuked worshipper receives a religious-doubt effect for one day (`738-740`). A worshipper gets a warning on the first failed check: "Worshipper, you have disappointed X. Make amends or face my wrath!" (`1203-1204`). Meeting the requirements clears the mark (`1171-1176`, `1208-1215`).

Blackmarks are stored in a `PrioritizingLimitedMap` of 20 entries, with a 30-minute touch limit and a 60-minute maximum age (`StdDeity.java:59`; `core/collections/PrioritizingLimitedMap.java:84-93`). Marks therefore expire when nobody touches them for half an hour.

**5.6 Services.** A service is a group rite (`startServiceIfNecessary` `850-919`; `finishService` `996-1049`; `cancelService` `1051-1099`).
- A cleric (faith 100 or more) must be in a room infused with this deity (`StdDeity.java:829-833`, using `Libraries/MUDLaw.java:703-729`). A worshipper with faith 1000 or more who worships this deity may also start one.
- A new service is refused if another is in progress in the same area, or a completed one was held there within 30 minutes (`854-861`; the window is `1000*60*30` at `945`). The message is "Services have already been performed here for X recently." (`859`).
- **Start.** "X begin(s) to hold services for Y here." (`865-867`). The room's infusion ability is set to code 1 (`880-882`). Players who worship this deity within radius `5 + level/5` are told that services are starting (`887`, `899-900`). NPC worshippers in that radius are made to track the cleric to the room, up to `5 + level/2` of them (`886`, `901-911`). Players are counted only when the service ends (`1005-1015`).
- **Finish.** With no worshipper present the service is cancelled, "because no one showed up for it." (`1016-1019`). Otherwise the completion is announced with the count (`1020-1024`). Each player worshipper in the room other than the cleric gets 50 experience (`1035`), and their skill convert ability is made long lasting (`1039-1041`). The cleric gets `round(totalLevels / cleric level × 10)` experience, where player worshippers count three times their level (`1036-1038`, `1046-1047`).
- A service whose ritual has stopped tracking is cancelled by the tick (`1218-1240`).
- The default service text has two waits of 10 (`215`). The units are set by the `Triggerer`, which was not traced.

**5.7 Holy events and the favor faction.** The `HolyEvent` enum (`MOBS/interfaces/Deity.java:127-136`) names what happened: SERVICE_BEGIN, SERVICE, SERVICE_CANCEL, CURSING, BLESSING, REBUKE and DISAPPOINTED. The name travels in the message's `othersMessage` (`StdDeity.java:493`, `569`, `868`, `1023`, `1078`, `1147`, `1159`, `1187`, `1198`) and is parsed back on receipt (`748`). The example faction `resources/examples/deityreaction.ini` keys its changes on these same names (lines 234-247; see the content section).

**5.8 Where the god stands.** A deity is a MOB with a home room. It leaves home only to cast, and returns afterwards. Blessing delivery restores the home position in a `finally` block (`386-397`). The curse path does not (problem 12).

### 6. StdFactoryMOB: a pooled placeholder actor

- **Identity.** `ID()` is `StdFactoryMOB` (`StdFactoryMOB.java:44-47`). The name lives in `volatileName` (`41`). `name()` returns the physical-stat name when one is set, and `volatileName` otherwise (`77-82`).
- **Pooling.** `finalize()` returns the object to the MOB cache (`85-95`). `destroy()` resets the stats and then calls `finalize()` (`97-136`; the call is at `130`). `CMClass.getFactoryMOB()` takes the first cached MOB, or makes a new one when the cache is empty (`CMClass.java:3900-3913`). `returnMob` adds any MOB to the cache, up to `MAX_MOBS` (`3862-3873`; the cache is at `237`).
- **Uses.** Spells and glyphs need a caster that is not a real creature. `Spell_WardArea.java:265-277` makes a caster named "the thin air", at the invoker's level plus the extra-level bonus, with the invoker as liege. The warding glyph prayers also create a `StdFactoryMOB` caster (`Prayers/Prayer_LesserWardingGlyph.java:258`, `Prayer_GreaterWardingGlyph.java:247`, `Prayer_ImprovedWardingGlyph.java:269`); their setup was not read. `Spell_WallOfAir.java:133-141` makes a short-lived attacker named "The wall of air". It makes a weapon attack against the shooter and is destroyed. Behaviours use it as a speaker (`Behaviors/Emoter.java:258`, `Behaviors/Mime.java:156`, `Behaviors/Concierge.java:147`). Stat code uses it as a fake body (`Races/StdRace.java:1316`, `CharClasses/StdCharClass.java:1146`).

### 7. StdMOB: the hooks this bucket depends on

`StdMOB` is not re-read for this chapter. Its depth is in b173, which read the whole file. The hooks used here are:
- `ID()` returns `StdMOB` (`StdMOB.java:58-61`).
- `cloneFix` copies base stats on respawn (`608-640`). `StdDeity` extends it to copy blessings, curses and powers (`StdDeity.java:119-131`).
- `isGeneric()` is false (`780-783`). The generic siblings in b146 override it.
- `setMiscText` and `text` hold the chain name (`2054-2066`).
- `okMessage` (`2459`) and `executeMsg` (`3412`) are the veto and apply phases that each subclass overrides and calls through `super`.

## Content worth mining

### Deity roster (`resources/examples/deities.cmare`)

The file holds nine `GenDeity` records. Each has a cleric ritual "SAY Bless me <name>" with a `TIME` wait, a power ritual "SAY Grant me your favor" (every record except Frigga), and a power `Prayer_DivineFavor` (eight records). No record sets a worship ritual, and only Hel and Aegir set a worship requirement. Domains below are paraphrased from the descriptions.

| Deity | Domain (paraphrased) | Cleric mask | Blessings (ability IDs) | Worship requirement |
|---|---|---|---|---|
| Hel | death; source of disease | `-class +cleric +necromancer +doomsayer +templar` | `Prayer_ProtGood`, `Disease_Cold` | `TIME 0` |
| Aegir | sea and storms | `-class +cleric +purist +doomsayer +missionary` | `Prayer_CreateWater`, `Spell_WaterBreathing` | `TIME 4` |
| Surtur | atmosphere and weather (description repeats Frigga's) | `-class +cleric +oracle +purist +missionary` | `Prayer_ProtFire` | none |
| Idun | spring and eternal youth | `-class +cleric +Oracle +Healer +Shaman` | `Prayer_SenseLife` | none |
| Bragi | poetry, eloquence, song | `-class +cleric +Oracle +Purist +Shaman` | `Spell_Friends` | none |
| Loki | mischief, strife, fire | `-class +cleric +Templar +missionary +healer` | `Spell_Polymorph` | none |
| Thor | thunder | `-class +cleric +missionary +shaman +templar` | `Prayer_CreateFood`, `Spell_ResistElectricity` | none |
| Odin | king of the gods; sends Valkyries for the valiant dead | `-class +cleric +oracle +purist +missionary` | `Prayer_ProtEvil` | none |
| Frigga | atmosphere and weather | `-class +cleric +oracle +purist +missionary` | `Chant_PredictWeather` (no power) | none |

Cleric ritual waits run from 0 to 5 across the records. The masks are zapper strings (`MOBS/interfaces/Deity.java:155-158`). `TIME` is a mask word that was not traced.

### Favor faction (`resources/examples/deityreaction.ini`)

The file defines "Favor with <NAME>" as a faction with a range of -10000 to 10000 (`:29`, `:31`), and a rate modifier of 1.0 (`:94`).

| Band | Range | Code suffix |
|---|---|---|
| Hated | -10000 to -7501 | `_ENEMY` (`:105`) |
| Infidel | -5000 to -2501 | `_INFIDEL` (`:106`) |
| Neutral | -2500 to 2500 | `_NEUTRAL` (`:107`) |
| Favored | 2501 to 7500 | `_FAVORED` (`:108`) |
| Beloved | 7501 to 10000 | `_AVATAR` (`:109`) |

The band list skips `RANGE5` (problem 16). Default favor (`:65`) is 10000 for the deity's own GenDeity mob and -5000 for other GenDeity mobs. The change rows (`:234-247`) are as follows. The amounts are as written; the unit is defined by the faction library, which was not traced.

| Row | Event (source) | Direction | Amount | Mask names |
|---|---|---|---|---|
| CHANGE1 | BLESSING holy event | UP | 5% | `-Cleric` |
| CHANGE6 | BLESSING holy event | UP | 10% | `+Cleric` |
| CHANGE2 | SERVICE holy event | UP | 10% | `-Cleric` |
| CHANGE7 | SERVICE holy event | UP | 100% | `+Cleric` |
| CHANGE5 | MSG_SERVE (target deity) | UP | 500% | `+Cleric` |
| CHANGE12 | MSG_SERVE (target deity) | UP | 500% | `-Cleric` |
| CHANGE8 | CURSING holy event | DOWN | 250% | none |
| CHANGE4 | REBUKE holy event | DOWN | 2500% | `+Cleric` |
| CHANGE10 | REBUKE holy event | DOWN | 1000% | `-Cleric` |
| CHANGE3 | MSG_REBUKE (target deity) | DOWN | 5000% | `+Cleric` |
| CHANGE9 | MSG_REBUKE (target deity) | DOWN | 1000% | `-Cleric` |
| CHANGE11 | MSG_FOLLOW | DOWN | 100% | trailing mask `-NPC -FACTION +<CODE>_INFIDEL +<CODE>_ENEMY` (no Cleric token) |
| CHANGE13 | DISAPPOINTED holy event | DOWN | 50% | `+Cleric` |
| CHANGE14 | DISAPPOINTED holy event | DOWN | 50% | `-Cleric` |

"Mask names" says whether the row's mask names `+Cleric` or `-Cleric`, copied from the file.

### Deal categories used by these types

From `core/interfaces/ShopKeeper.java`: BANKER = 12 (`:69`), CLANBANKER = 23 (`:91`), BOOKS = 32 (`:109`), READABLES = 33 (`:111`). `INVENTORYONLY` = 7 (`:59`).

### Placements and sample text

- Random towns spawn bankers from `resources/randareas/town.xml`. One is a clan banker with the bank chain "Random Inc" and `WHATISELL="CLAN BANKER"` (line 1077). Another is a plain banker (line 1374).
- Short player-facing messages worth reusing as they are, with the source line:
  - "Ok, your new balance is X." (`StdBanker.java:543`)
  - "You'll need a total balance of X for me to hold that." (`906`)
  - "I'm sorry, you have only X in that account." (`985`)
  - "There ya go! This is due back here by X!" (`StdLibrarian.java:698`)
  - "Charges due for this were X. Thank you!" (`646`)
  - "I'm sorry, but you may only borrow X items." (`861`)
  - "Your new listing is posted and will remain for the next N days." (`StdCraftBroker.java:515-516`)
  - "That item does not fulfill any listed request. Try LIST." (`671-677`)
  - "You feel the presence of X in Y." (`StdDeity.java:495`)
  - "You feel the wrath of X in Y." (`571`)
  - "Worshipper, you have disappointed X. Make amends or face my wrath!" (`1204`)
  - "X takes N of experience from you." (`736`)
  - "Services for X are now starting at Y." (`900`)

## Notable design ideas

- **Chains as shared ledgers.** Each shopkeeper's state is keyed by a name stored in its own text field (`StdBanker.java:94-103`, `StdLibrarian.java:215-225`, `StdCraftBroker.java:86-96`). Branch NPCs share one ledger without sharing an object, and timers are held per chain (`StdBanker.java:246-259`).
- **Storage fees as the bank's only collection rule.** Items cost a small monthly fee taken from coins. If the balance goes negative, the bank takes the items into its stock and erases the account (`StdBanker.java:297-348`). It is a simple debt-collection loop with no extra rules.
- **Loan term from amount.** The term grows until the monthly installment is at most 250 (`StdBanker.java:614-616`).
- **Derived stock view.** The library's stock is the catalogue minus checked-out copies, rebuilt only when the records change (`StdLibrarian.java:360-393`). There is no separate inventory to keep in step.
- **Fees tied to value.** A late fee is set by the item's pawn value and the days late (`StdLibrarian.java:482-497`). Reclaim sets the debt to at least the item's value (`504-511`), so an item cannot be kept without being paid for.
- **Requests as masks.** A wanted item is a zapper mask (`StdCraftBroker.java:183-186`, `507`), so a request can say "any item that is ..." instead of naming one item.
- **Faith as a number with tiers.** One stat (`STAT_FAITH`) gates the worship text, the cleric role, rituals, powers and services (`Commands/Deities.java:96-99`; `StdDeity.java:293-833`).
- **Rituals over the message flow.** A ritual is a list of trigger strings checked against messages the deity receives, with waits resumed by the tick (`StdDeity.java:786-847`, `1242-1248`). The rule is a pattern over the event stream, not a command.
- **The god walks.** A deity casts by moving into the worshipper's room and back (`StdDeity.java:340`, `386-397`). That is a cheap way to make a blessing feel like it comes from the god.
- **Infusion bonus.** A blessing lasts 50% longer in a room infused with the same deity (`StdDeity.java:380-383`).
- **Powers that leave with faith.** Granted powers are non-savable, so losing faith removes them with no extra logic (`StdDeity.java:412-414`, `627-654`).
- **Favor as a faction.** Deity standing is an ordinary faction with bands and event-driven changes (`resources/examples/deityreaction.ini`), so the deity code does not track favor itself.

## Problems and cruft

Each item was checked by reading the cited lines unless marked "unverified".

1. **Event type carried as text.** The holy-event type is a string in `othersMessage`, parsed back with `CMath.s_valueOf` (`StdDeity.java:493`, `748`; enum at `Deity.java:127-136`). The faction file depends on these strings (`deityreaction.ini:234-247`). Renaming an event breaks the faction silently.
2. **Inverted null check.** `(msg.targetMessage()!=null) || (msg.targetMessage().length()>0)` (`StdCraftBroker.java:495` and `588`) throws on null and accepts an empty string. It should use `&&`. Null is probably not reachable from the command path (unverified).
3. **One request per player per chain.** The request key is the shop key, the requester's name, and that name's hash (`StdCraftBroker.java:149-158`). It has no per-request part. `DBCreatePlayerData` is a plain `INSERT` (`core/database/PDataLoader.java:739-746`) into a table with primary key (player, section, key) (`guides/database/coffeemuddbhsql.sql:430-436`). A second listing from the same player should therefore fail, yet the broker still says "Your new listing is posted" (`515-522`). The five-listing cap (`59`, `614-623`) can never be reached. The exact failure path depends on how the database layer reports duplicate keys (unverified).
4. **Unused field.** `currency` (`StdCraftBroker.java:57`) is never read. The currency comes from `getFinalCurrency()`.
5. **Discarded results.** `CMStrings.getSayFromMessage(msg.targetMessage());` is called and its result dropped (`StdCraftBroker.java:531`; `StdLibrarian.java:730`).
6. **Payments revert.** Coins applied to an overdue item that is still out are overwritten the next time the record is processed (`StdLibrarian.java:553-581` against `476-503`). The daily pass and LIST both recalculate the charge from the due date (`747`). A payment therefore sticks only when the item comes back. This follows from reading the code and was not run.
7. **Installment divides by zero in the last period.** `timeRemaining/timeInterval` is integer division (both `long`, `StdBanker.java:248`, `314`, `319`). In the final whole period it is zero. `CMath.div(double, long)` then divides by zero and yields infinity (`core/CMath.java:469-472`). No installment is taken that month (`320-327`), and the full amount is due at the first pass after the due date (`315-316`).
8. **Default erases coins.** On default, every deposit row is erased, including the coin balance (`StdBanker.java:346-347`). The coin balance is not handled separately. This is a design choice that the code does not mark as one.
9. **Item-only accounts (unverified reachability).** The storage fee comes out of coins (`297-302`). If there are no coins and any stored item has value, the balance is negative and the account defaults (`332`). Whether normal play can produce such an account was not traced.
10. **Departed players' money is erased silently.** The monthly pass erases accounts whose owner does not exist, with no ledger line (`StdBanker.java:263-280`).
11. **Curse path has no `finally`.** `bestowCurse` moves the deity to the target's room (`StdDeity.java:431`) and returns it at `462-470` with no `try`/`finally`. Blessing delivery uses one (`337-397`). If the invoke throws, the deity stays in the worshipper's room.
12. **Penalty field overwritten.** `xpwrath` is reset from the return value of `postExperience` (`StdDeity.java:734`). That method returns 0 when the message is vetoed or the class or race is `expless` (`Libraries/CoffeeLevels.java:1081-1097`). After such a 0, every later rebuke takes 0 experience (unverified in play).
13. **Dead branch.** The cleric branch gives a "dirtied" message when the count is zero (`StdDeity.java:1164-1165`). The count was incremented at `1142` and is at least 1 in that branch, so the message cannot print.
14. **Mixed name comparisons.** Worship IDs are compared with `name()` at `711` and `1123`, and with `Name()` at `716`, `790-792`, and `1068`. The two differ only when a physical name override is set (unverified impact).
15. **Blackmark expiry.** Idle marks expire after 30 minutes (`StdDeity.java:59`; `PrioritizingLimitedMap.java:84-93`). The exact touch semantics were not traced. A worshipper who logs off for half an hour starts with a clean count.
16. **Skipped range.** The favor file defines RANGE1 to RANGE4 and RANGE6, with no RANGE5 (`deityreaction.ini:105-109`). Whether the loader needs contiguous numbering was not traced.
17. **Duplicate description.** The Surtur record's description repeats Frigga's with only the name changed (`resources/examples/deities.cmare`).
18. **Volatile name survives destroy.** `destroy()` clears `_name` but not `volatileName` (`StdFactoryMOB.java:97-136`). A recycled instance keeps its old name until `setName` is called. Callers that display or match the name set it first (`Behaviors/Mime.java:156`, `Behaviors/Emoter.java:258`). Callers that only compute stats do not use it (`StdRace.java:1316`, `StdCharClass.java:1146`). Latent.
19. **Pooling by finalizer.** The pool relies on `finalize()` (`StdFactoryMOB.java:84-95`). That method is deprecated in Java. `CMClass.getFactoryMOB` also uses an exception for the empty-cache case (`CMClass.java:3904-3912`).
20. **Misnamed variable.** `updateCheckedOutRecords` builds XML in a variable named `json` (`StdLibrarian.java:195-205`).
21. **Partly built list published.** The library publishes its record list to `Resources` before filling it (`StdLibrarian.java:105-106` before `108-130`). Another thread could read a partial list (unverified in practice).
22. **Unused assignment.** `owner` is assigned in the withdrawal path and never read (`StdBanker.java:925`, `946`, `958`).
23. **Multiplayer-only logic.** Much of this bucket serves other people: clan accounts and liege sharing, player-to-player listings, branch libraries shared between players, group services with parishioners and shared experience, and the rebuke loop over all online players. None of it applies to a single-player game.

## Takeaways for a Python SUD

**Banker.** Keep the account, the storage fee, the loan with a term from amount, and the seizure on default. Seized items become sellable stock, which gives the default a use. Fix the final-period installment (problem 7). A sketch that mirrors `StdBanker.java:297-348` with the final period paid in full:

```python
def monthly_pass(bank, acct):
    # storage fees come out of coins (StdBanker.java:297-302)
    balance = acct.coins * (1 + bank.coin_rate) - acct.item_value * bank.storage_rate
    for loan in acct.loans:
        loan.owed += loan.rate * loan.owed                  # 1% a month (313)
        months_left = loan.due_month - bank.month
        if months_left < 0 and balance < loan.owed:         # overdue and short (315)
            return bank.seize(acct)                         # items to stock, account erased
        pay = loan.owed if months_left <= 0 else loan.owed / months_left
        if balance >= pay:
            balance -= pay
            loan.owed -= pay
    if balance < 0:
        return bank.seize(acct)
    acct.coins = balance
```

Drop the clan accounts, liege sharing, land titles, multi-currency handling, and the silent purge of departed accounts.

**Library.** Keep the catalogue as the single source, the derived view of available copies, and the checkout record with two dates. Keep the late fee, which with the defaults is `(1 + days) × (1 + 0.1 × value)`:

```python
def late_fee(value: float, days_late: int) -> float:
    """Mirrors StdLibrarian.processCheckedOutRecord with the default constants."""
    if days_late < 0:
        return 0.0
    fee = 1.0 + 0.10 * value                        # base charge plus pct of pawn value
    if days_late >= 1:
        fee += days_late * (1.0 + 0.10 * value)     # per whole late day
    return fee
```

Keep the rule that reclaim raises the debt to at least the item's value (`StdLibrarian.java:504-511`). Fix problem 6 by computing charges from a stored amount that payments reduce, not by recomputing over it. Drop the leader and property-owner lookups for returns, and the chain sharing.

**Request board.** Keep the idea of a wanted list as masks with counts and prices (`StdCraftBroker.java:149-186`). A single-player version is an NPC who posts requests and pays a reward on delivery. The pickup shelf can become a plain reward payment. Drop the per-player database rows, the expiry sweep, the commission unless the economy needs it, and the stolen-goods rule. Problem 3 shows why one dict per request beats keys built from the requester's name.

**Deity.** The most worth borrowing. In Python:
- A `Deity` object with `favor` (an int, moved by a table of event deltas and read through bands loaded from data, as in `deityreaction.ini`), `rituals` (a list of trigger strings, or better, a small list of steps such as `("say", "Bless me X")` and `("wait", n)`), `blessings` and `curses` (timed effects), and `powers` (non-persistent abilities).
- Tiers as data: `FAITH_WORSHIP, FAITH_CLERIC, FAITH_ADEPT = 25, 100, 1000` (`Commands/Deities.java:99`, `StdDeity.java:293`, `832`).
- Rebuke after a fixed count of failed checks: `REBUKE_AFTER = 31` (`StdDeity.java:1142`), counted in game turns rather than the 4-second tick.
- Holy events as an `Enum` passed as an object, not as text (fixes problem 1).
- The god walks to the worshipper to cast, then returns home (problem 11 is the bug to avoid: use a `try`/`finally`).
- Services become a timed rite at a shrine with a game-time cooldown of 30 minutes per area (`StdDeity.java:945`), with no parishioners and no shared experience.
- Drop the PvP and clan parts of the mask system, the database-free ID checks, and the single-player-irrelevant rebuke loop over all online players.

**Factory actor.** A small `Actor` value object (name, level, location) created per effect, with no pool, since Python's garbage collector handles the cleanup. Always set a name explicitly (problem 18).

**Multiplayer assumptions to rethink.** Brokers assume other players as buyers and sellers. Bankers assume clan and married accounts. Libraries assume several borrowers. Services assume a crowd. Rebukes loop over everyone online. A single-player game keeps the state, drops the crowd, and keeps the rules that tie faith, favor and debt to the player.

## Coverage

- **Read in full:** `MOBS/StdFactoryMOB.java` (1-137), `MOBS/StdCraftBroker.java` (1-742), `MOBS/StdLibrarian.java` (1-914), `MOBS/StdBanker.java` (1-1104), `MOBS/StdDeity.java` (1-1589).
- **Skimmed:** `MOBS/StdMOB.java` (6287 lines). Its declaration index was checked (296 declarations), along with the hooks at lines 58-61, 608-640, 780-783, 2054-2080, 2459 and 3412. Full depth is in b173.
- **Read in part, for interactions:** `MOBS/StdShopKeeper.java` (1-40, 262-301); `MOBS/interfaces/Librarian.java` (51-127); `MOBS/interfaces/Banker.java` (50-56); `MOBS/interfaces/Deity.java` (120-160); `MOBS/interfaces/CraftBroker.java` (name only); `Common/interfaces/CharStats.java` (136, 897); `core/collections/PrioritizingLimitedMap.java` (33-45, 84-114); `core/CMClass.java` (237, 3862-3913); `Libraries/CoffeeLevels.java` (1078-1107); `Libraries/MUDLaw.java` (470, 698-729); `core/CMath.java` (the division overloads, 433-484 and 676-691); `core/database/DBInterface.java` (1405-1419); `core/database/PDataLoader.java` (726-746); `core/database/ADataLoader.java` (629-651); `Common/DefaultCoffeeShop.java` (249-252, 297-305, 325-345); `Libraries/BeanCounter.java` (1510-1524); `CharClasses/Cleric.java` (372-385); `Commands/Deities.java` (88-140); `Abilities/Thief/Thief_FalseFaith.java` (111); `Abilities/Spells/Spell_WardArea.java` (260-285); `Abilities/Spells/Spell_WallOfAir.java` (128-150); the three warding-glyph prayers (caller lines only); `Behaviors/Mime.java`, `Behaviors/Emoter.java`, `Behaviors/Concierge.java`, `Behaviors/MOBEater.java`, `Races/StdRace.java`, `CharClasses/StdCharClass.java`, `Commands/Visible.java` (caller lines only); `guides/database/coffeemuddbhsql.sql` (the `CMPDAT` table); `resources/examples/deities.cmare` (all nine records); `resources/examples/deityreaction.ini` (its non-comment lines); `resources/randareas/town.xml` (lines 1077 and 1374, by search).
- **Not read:** the ritual engine (`Triggerer` and `DefaultTriggerer`); the faction library's change semantics (the units of `UP;5%`); the zapper mask parser; `CoffeeShops` buying, selling and pawn internals beyond the calls cited (b173 covers pricing); the database layer's handling of duplicate keys; `GenBanker`, `GenLibrarian`, `GenCraftBroker` and `GenDeity` (bucket b146); and the `Clan` helper functions.
- **Skipped:** none of the six assigned files.
