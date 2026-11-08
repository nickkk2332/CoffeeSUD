# Shops, money and auctions

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b030` (Shops, money and auctions) · 14 files · 10589 lines of Java.

Citation convention: every Java path below is relative to the CoffeeMud folder with the package prefix `com/planet_ink/coffee_mud/` dropped after this note, so `Libraries/CoffeeShops.java:706` means `com/planet_ink/coffee_mud/Libraries/CoffeeShops.java:706`. Files outside the package (`coffeemud.ini`, `resources/laws.ini`) are cited from the CoffeeMud folder. "Default" values are the shipped `coffeemud.ini` values unless stated.

## Summary

Shopkeepers are any object that implements `ShopKeeper` (a mob, an item, or an ability attached as an effect; `Libraries/CoffeeShops.java:91-128`). Each one owns a `CoffeeShop` stock list and a set of economic settings: prejudice by customer group, item pricing masks, a cash budget, stock devaluation, reset timing and a currency. `CMLib.coffeeShops()` (`ShoppingLibrary`) prices every sale and buy-back from a base value, the customer's charisma, the shopkeeper's wisdom, stock on hand and the local sales tax. `CMLib.beanCounter()` (`MoneyLibrary`) defines currencies as short text strings, creates and splits real coin items, and runs bank accounts, loans and debt records on mud-time ticks. Auctions are a single global live auction driven by a channel command, plus timed auctions held by NPC auctioneers whose listings are stored as journal records. The shop pricing model (a stock-sensitive price curve, personality-driven multipliers and a per-period cash budget) is the most borrowable part; the auction settlement code has several defects listed under Problems.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Libraries/CoffeeShops.java` | `ShoppingLibrary` implementation: prices, acceptance tests, transactions, VIEW text, bid logic, auction storage | 2909 |
| `Libraries/BeanCounter.java` | `MoneyLibrary` implementation: currency parsing, coin items, abbreviation, bank accounts, ledger, debts | 1958 |
| `Libraries/interfaces/MoneyLibrary.java` | Money API; `MoneyDefinition`, `MoneyDenomination`, `BankDepositEntry`, `DebtItem` | 1680 |
| `Common/DefaultCoffeeShop.java` | Standard stock: enumerable inventory, shelves, providers, adjusters, XML save and load | 961 |
| `Libraries/interfaces/ShoppingLibrary.java` | Shop API; `BuySellFlag` (INFO, RETAIL, WHOLESALE) | 579 |
| `Common/interfaces/CoffeeShop.java` | Stock API; `ShelfProduct`, `ShelfPriceFlag`, `ShopProvider`, `ShelfAdjuster` | 547 |
| `Common/AuctionCoffeeShop.java` | Shop view of an auction house; live listings presented as stock | 337 |
| `Common/DefaultAuction.java` | `AuctionData` record: item, seller, high bidder, bids, currency, times | 290 |
| `core/interfaces/Economics.java` | Economic settings: prejudice, pricing masks, budget, devalue, reset, currency | 280 |
| `core/interfaces/ShopKeeper.java` | `DEAL_*` shop types (0-36), `ViewType`, shopkeeper contract | 245 |
| `Common/interfaces/AuctionData.java` | Auction record API | 227 |
| `Common/DoubleCoffeeShop.java` | Two-layer shop: own stock plus the host's second shop | 213 |
| `Common/DefaultAuctionPolicy.java` | Auction fees and cuts, read from `AUCTIONRATES` | 206 |
| `Common/interfaces/AuctionPolicy.java` | Auction policy API | 157 |

Outside the bucket, the chapter follows these: `Commands/Auction.java` (live auction), `MOBS/StdAuctioneer.java` (timed auctions), `MOBS/StdShopKeeper.java` (shopkeeper mob), `MOBS/StdBanker.java` (banks and loans), `Abilities/Skills/Skill_Haggle.java`, `Abilities/Common/Merchant.java`, `Areas/StdArea.java` (setting inheritance), `Behaviors/StockMarket.java` (the only other user of shelf adjusters and flags).

## How it works

### 1. Data model

**Shop types (`ShopKeeper`).** A shopkeeper's trade is a bitmask of `DEAL_*` constants (`core/interfaces/ShopKeeper.java:45-117`), described for display by `DEAL_DESCS` (129-140). Mutually exclusive types are listed in `DEAL_CONFLICTS` (120-126): postman vs clan postman, banker vs clan banker, four property and ship sellers, trainer vs caster. A zapper mask (`setWhatIsSoldZappermask`, 226-244) further narrows what is bought. `ViewType` (145-150) has three values: `BASIC` (full detail), `FALSE` (the shopkeeper lies, see 2.6) and `IDENTIFY` (adds secret identity text).

**Settings inheritance.** Every economic setting has a *raw* value on the shopkeeper and a *final* value that falls back to the start area, then its parent areas, then the global default (`MOBS/StdShopKeeper.java:494-600`; `Areas/StdArea.java:791-800`, `893-931`, `974-980`). The defaults are in `coffeemud.ini`: `PREJUDICE=` empty (1786), `BUDGET=500000 MONTH` (1825), `DEVALUERATE=20 0.2` (1839), `INVRESETRATE` (64800 ticks, about three real days), `AUCTIONRATES=0,2,0.1%,10%,5%,4,600` (1863). Stock resets to its original populated XML every `INVRESETRATE` ticks (`MOBS/StdShopKeeper.java:200-226`).

**Stock (`CoffeeShop`).** Two lists:
- `enumerableInventory`: the fixed "inventory only" list, used for shops with `DEAL_INVENTORYONLY` (7).
- `storeInventory`: `ShelfProduct` entries (`Common/interfaces/CoffeeShop.java:439-490`). Each has a product, a count, a price code, a currency and a set of `ShelfPriceFlag`s.

Price codes (`Common/interfaces/CoffeeShop.java:150-154`; `Libraries/CoffeeShops.java:2139-2154`):

| Price code | Meaning |
|---|---|
| `-1` | Native value of the item (see 2.2) |
| `0` or more | Gold in the shop's currency (a shelf price of 0 becomes a 1 gp minimum at sale, 2.3) |
| `-100` down to `-999` | Quest points, cost = `-code - 100` |
| `-1000` and below | Experience, cost = `-code - 1000` |

`ShelfPriceFlag` (`Common/interfaces/CoffeeShop.java:404-430`): `NO_CHARISMA`, `NO_TAXES`, `NO_PREJUDICE`, `NO_MAGIC_DISCOUNT`, `NO_DEPRECIATION`, `BEST_PRICE`. In the tree only `Behaviors/StockMarket.java` sets them (`NO_TAXES` and `NO_DEPRECIATION` at 1015-1016; `NO_DEPRECIATION` for certificates at 85 and 144). `NO_MAGIC_DISCOUNT` is declared but never tested (see Problems).

**Providers and adjusters.** A `ShopProvider` supplies stock just in time (`getStock(buyer, shop, room)`, `claim(...)`), and a `ShelfAdjuster` rewrites a shelf entry on each lookup (`Common/interfaces/CoffeeShop.java:492-539`). The real-estate provider (`Libraries/CoffeeShops.java:1930-2137`) lists land titles for sale, and `Behaviors/StockMarket.java:88` and `940-942` install a stock-market provider and adjuster.

**Shop classes.**

| Class | Used by | Behaviour |
|---|---|---|
| `DefaultCoffeeShop` | `MOBS/StdShopKeeper.java:50`, `Abilities/Common/Merchant.java:105`, `MOBS/StdCraftBroker.java:163` | Full stock, shelves, providers, adjusters, XML persistence (`makeXML` 775-803, `buildShopFromXML` 805-924) |
| `DoubleCoffeeShop` | `MOBS/StdCraftBroker.java:257` | Own stock plus the host's `getShop()`; reads fall through to the second shop (`Common/DoubleCoffeeShop.java:76-212`) |
| `AuctionCoffeeShop` | `MOBS/StdAuctioneer.java:91` | No shelves. `getStock` presents live auctions as stock; `numberInStock` returns 1 (`Common/AuctionCoffeeShop.java:226-229`) |

**Stock matching.** `shopCompare` (`Common/DefaultCoffeeShop.java:216-234`) matches door keys with `sameAs`, generic items by name, and everything else by class ID. Two non-generic items of the same class are therefore the same stock for lookup, counting and devaluation. `addStoreInventory` (248-388) merges by name and adds to the count; a positive price code overwrites the shelf price (371-377). Items taken in are copied, their owner cleared and any land-title ship handled (`preSaleCopyFix`, 297-322).

### 2. Pricing

#### 2.1 Native value

`rawSpecificGoldPrice` (`Libraries/CoffeeShops.java:557-599`) gives the base value used when a shelf price is `-1`:
- Item: `value()` (560-561).
- Ability: `lowestQualifyingLevel(ID) * 100` at a trainer, `* 75` elsewhere (563-568).
- Mob (follower or slave): the `Prop_Retainable` text if set, else `(25 + level) * level` (571-594).
- Anything else: `lowestQualifyingLevel(ID) * 25` (597).

#### 2.2 Selling to a customer: `sellingPrice`

`Libraries/CoffeeShops.java:706-776`. In order:
1. Base = shelf price, or native value when the code is `-1` (713-724).
2. Prejudice times item pricing adjustments, unless `NO_PREJUDICE` (734-740).
3. Charisma and wisdom curve, only when the shopkeeper is a monster (not a golem) and the item lacks `NO_CHARISMA` (742-755):

   ```
   customer pays = base * (2 - x)      x = (c - w) / (0.8 * (c + w))
   c = max(1, customer CHA)            w = max(3, shopkeeper WIS)
   ```
   (`Libraries/CoffeeShops.java:748-753`.)

4. Sales tax as a percentage of the home-room law's `SALESTAX`, unless `NO_TAXES` or the caller passes `includeSalesTax=false` (757-762).
5. Floor: a price at or below zero becomes 1.0 (763-764). Otherwise it is rounded with `abbreviatedRePrice` (766).
6. Magic discount: if the buyer's base disposition has `PhyStats.IS_BONUS` and the item is `MiscMagic` and the price is above 2, the price is halved (769-772). The bonus is set by aura-type classes, for example `CharClasses/Arcanist.java:233`, `Reliquist.java:404` and `Gypsy.java:339`.
7. `BEST_PRICE` caps the price at base (773-774).

Worked values, computed from the formula above (customer pays, as a multiple of base):

| Customer CHA (c) | Shopkeeper WIS (w) | Customer pays (sell) | Shop pays (buy, 2.3) |
|---|---|---|---|
| 1 | 3 | 2.63 | 0.19 |
| 1 | 10 | 3.02 | at or below 0 (refused) |
| 10 | 10 | 2.00 | 0.50 |
| 20 | 10 | 1.58 | 0.71 |
| 35 | 10 | 1.31 | 0.85 |
| 27 | 3 | 1.00 | 1.00 |
| 90 | 10 | 1.00 | 1.00 |

The price is exactly base times 1 when CHA = 9 x WIS. The comment at `Libraries/CoffeeShops.java:744` says "200% at 0 charisma, and 100% at 35". That does not match the code: at CHA 1 and WIS 3 the multiplier is 2.63, and at CHA 35 and WIS 3 it is 0.95.

#### 2.3 Buying from a customer: `pawningPrice`

`Libraries/CoffeeShops.java:1000-1087`. In order:
1. Count of units: packaged items, raw-material weight, bundle size, else 1 (`getProductCount` 976-988). For raw materials the copy is re-priced per pound (1012-1023).
2. Base = shelf price or native value, as in 2.2 (1032-1034).
3. `WHOLESALE` flag: no customer, so base times count with no modifiers (1036-1040; `MOBS/StdCraftBroker.java:416`).
4. Prejudice (SELL entries) times item pricing adjustments (1041-1047).
5. Charisma and wisdom, buy side, monster shopkeepers only (1050-1061):

   ```
   shop pays = base * (0.5 + 0.5 * x)     x = (c - w) / (0.8 * (c + w))
   c = max(1, customer CHA)               w = max(3, shopkeeper WIS)
   ```

6. Devaluation, unless the item is an `Ability` or the shelf has `NO_DEPRECIATION` (1062-1064): `shop pays *= (1 - devalue)`, see 4.
7. Cap: never more than the shop's own sale price to the same customer, computed without sales tax (1065-1072).
8. Times the count (1074). A result at or below zero becomes 1.0 (1076-1077). `BEST_PRICE` raises it to base (1078-1079).

At equal stats the shop sells at 2.00 and buys at 0.50 of base, a 4x spread. The two curves meet at 1.0 when CHA = 9 x WIS.

Acceptance (`pawnEvaluation`, 1106-1222) runs in this order. Each refusal is a short line said by the shopkeeper:
- Not something the shop deals in, or coins: "I'm sorry, I'm not buying those." (1114-1118)
- Level outside the shop's band: "I'm sorry, that's out of my level range." (1121-1148). The band is `RANGE=` from the prejudice string, as a number or a percentage of the area's median level.
- Stolen goods, for a lawful or non-evil shopkeeper: "I don't buy stolen goods." (1149-1155)
- Value under 2 gold: "I'm not interested." or, for a lot, "I'm not interested in the whole lot, but maybe a smaller count..." (1157-1165)
- Enchanted or burning: "I won't buy that in it's present state." (1166-1171)
- Above the budget remaining: "Sorry, I can't afford that right now.  Try back later.", or "That's way out of my price range! Try AUCTIONing it." if it is above the budget maximum (1172-1179)
- Abilities are never bought back (1180-1184).
- A locked container must contain its key: "I won't buy that back unless you put the key in it." Locked containers and their contents are also checked for stolen or enchanted items (1185-1214).
- The seller must be able to drop the item (1215-1220).

#### 2.4 Prejudice and pricing masks

- **Prejudice string** (`prejudiceFactor`, `prejudiceValueFromPart`, 601-673). Entries are separated by `;` and classified by their first token, `BUY=` (the customer buys, shop sells) or `SELL=` (the customer sells). The first matching group wins. A bare number, such as `BUY=1.0`, applies to everyone (653-658). Group names are matched against faction ranges, tattoos, alignment, gender, class, race, racial category and the current class title at the customer's level (618-627). `coffeemud.ini:1771-1786` gives the example `BUY=5.5 SELL=0.8 Elf Dwarf;BUY=2.0 SELL=0.9 Human;BUY=1.0;SELL=.99`. As the code reads, an entry is classified by its first token only, so the embedded `SELL=0.8` in the first entry is never used for selling. `CMath.s_double("SELL=0.8")` returns 0 (`core/CMath.java:2016-2026`). This is a reading of the code, not a test against live data.
- **Pricing masks** (`itemPriceFactor`, 675-703). Each entry is `factor zapper-mask`, for example `0.5 -MATERIAL +WOODEN`. Every matching mask multiplies the price. While the masks are checked, the item's owner is temporarily set to the room (686-687, 698-699), so a mask can test where the item is.
- **Ignore mask** (`ignoreIfNecessary`, 2250-2271). A shopkeeper refuses a customer who fails the mask, is asleep, is dead or is in combat, with messages such as "<S-NAME> appear(s) to be too busy right now."

#### 2.5 Selling restrictions

`sellEvaluation` (1225-1373) checks, in order: the shop has the item in stock for this customer ("I don't have that in stock.  Ask for my LIST."); XP, QP and gold affordability ("You can't afford to buy @x1."); level ("That's too advanced for you, I'm afraid."); item count and carry weight ("You can't carry any more items.", "You are too encumbered to get @x1."); clan ownership for property ("I only sell to clans."); follower limits; and for trainers and casters, whether the customer may learn or receive the spell.

#### 2.6 Lies (VIEW with `FALSE`)

`getViewDescription` (`Libraries/CoffeeShops.java:200-500`) shows the item's stats. With `ViewType.FALSE`, each number is nudged by an offset derived from `E.name().hashCode()` (216; `plus` and `plusOrMinus` at 183-197). A fake ability name comes from scanning class IDs by the same hash (229-238). The IDENTIFY text picks one of eight bonus phrases by hash (459-476). The offset depends only on the name, so repeated views agree.

### 3. Haggling

`Abilities/Skills/Skill_Haggle.java` (triggers at 69, `invoke` at 90-144):
- `HAGGLE BUY|SELL <item> [shopkeeper]` is parsed, then `proficiencyCheck` decides the roll.
- On success the skill adds a non-savable copy of itself to the caster. It grants `CHARISMA += 10 + XLEVEL` (83-87), forces the normal BUY or SELL command (122-130), and removes the copy (134).
- On failure the caster gets "<S-NAME> haggle(s) with <T-NAMESELF>, but <S-IS-ARE> unconvincing." (140).

Because the curves read the customer's CHA, the bonus moves prices on both sides. At CHA 10 against WIS 10, the customer's sell multiplier falls from 2.00 to 1.58 and the shop's buy multiplier rises from 0.50 to 0.71 (values from 2.2 and 2.3).

### 4. Stock sensitivity: devaluation and lot limits

`getStockSizeDevaluation` (`Libraries/CoffeeShops.java:932-950`):

```
n = numberInStock(null, product) + round(floor(count / 2))
d = min(1, rate * n)            rate = rates[1] for RawMaterial, else rates[0]
shop pays = base * (1 - d)
```

`DEVALUERATE=20 0.2` (coffeemud.ini:1839) is parsed by `CMath.s_pct`, which divides by 100 (`core/CMath.java:1083-1090`). So a normal item loses 20% per unit already held and a raw material 0.2% per unit. For a normal item, when the shop already holds 0, 1, 2, 3 or 4 units it pays 100%, 80%, 60%, 40% or 20% of base. At 5 or more the price reaches zero, is raised to the 1 gp floor, and the shop refuses ("I'm not interested.").

`isLotTooLarge` (952-974) flags a lot when the shop's current stock is still under 95% devalued but adding half the lot takes it to 95% or more. Then the shop says "I'm not interested in the whole lot, but maybe a smaller count..." (1158-1163).

### 5. Budgets: the shop's cash

- `StdShopKeeper` keeps `budgetRemaining` and `budgetMax` (`MOBS/StdShopKeeper.java:54-55`). `doBudgetReset` (228-241) sets `budgetRemaining` to the budget amount and `budgetMax` to the same value. It sets the next reset to `TicksPerMudHour * hours in the period` (238). `tick` runs the reset when the countdown ends (244-258).
- Every purchase by the shop reduces `budgetRemaining` by the amount paid (`executeMsg` TYP_SELL, 393-396). `okMessage` passes both numbers to `pawnEvaluation` (298). A sale is refused if the price is above `budgetRemaining`, and the refusal is "way out of price range" if it is also above `budgetMax`.
- `parseBudget` (`Libraries/CoffeeShops.java:855-896`): an empty string means no budget (unlimited). The literal `0` means a budget of zero, so the shop buys nothing. A numeric zero written any other way, such as `0.0`, means unlimited (879-881). With no period given, the period is HOUR (871, 890).
- A month is `hoursInDay * millisPerMudHour * daysInMonth` for the area's clock (`MOBS/StdBanker.java:176-181`).
- `Abilities/Common/Merchant.java:478-481` uses the merchant's own money on hand as the budget, with a maximum of 100 times that.

### 6. Transactions

**Sale to a customer.** `transactMoneyOnly` (`Libraries/CoffeeShops.java:1701-1757`):
1. Price with sales tax is taken from the buyer (1710-1714).
2. If the area has a tax treasury, the tax part (total minus the price before tax) is put into it (1716-1735).
3. If the seller is a monster standing on owned land, the same amount is deposited in the owner's local bank (1736-1748).
4. The shopkeeper's own purse is credited the same amount (1749-1750).

Steps 3 and 4 both credit the proceeds (see Problems). `purchaseItems` (1760-1784) drops the items in the room and sends a `MSG_GET` with the shopkeeper as the tool, which lets the shop's own rules (such as the room's okMessage) allow or veto the pickup. `purchaseMOB` (1787-1826) handles followers and slaves. Slave shops (`DEAL_SLAVES`) add a `Skill_Enslave` effect and make the buyer the owner (1795-1816). `purchaseAbility` (1829-1903) gives trainers a `postTeach`; other shops have the shopkeeper cast the ability on the buyer, or on the buyer's wielded, held or worn item (1867-1889).

**Buy-back from a customer.** `pawnEvaluation` (2.3), then `transactPawn` (1604-1698):
1. The shop pays the customer with `giveSomeoneMoney` (1631-1632). A payer that is not a shopkeeper is debited first (1630-1631).
2. Removes the item from the seller: unwear, leave the container, strip any `Prop_PrivateProperty` effect (1640-1652).
3. Puts the item on the shelf at native price (`addStoreInventory(..., -1)`, 1653). Contents the shop also sells go back on the shelf; the rest are destroyed (1654-1669).
4. Returns the amount paid, or `Double.MIN_VALUE` if the shop does not buy the item (1697). `StdShopKeeper.java:394` tests `paid > Double.MIN_VALUE`.

### 7. Currencies and coins

**Definitions.** A currency is text: `NAME=amount full name (abbr);amount full name (abbr);...`. Denominations are kept in ascending order of value, a `notrade` token marks a currency that cannot be exchanged, and the value unit is base gold (`Libraries/BeanCounter.java:146-219`; `Libraries/interfaces/MoneyLibrary.java:52-66`). The built-in currencies are (`Libraries/BeanCounter.java:53-58`):

| Currency | Denominations (value in base units) | Notes |
|---|---|---|
| default (blank name) | gold coin 1, golden note 100, whole note 10000, Archon note 1000000 | No abbreviations; used when an area has no currency |
| `GOLD` | copper piece 0.01 (cp), silver 0.1 (sp), gold 1.0 (gp), platinum 5.0 (pp) | Abbreviated |
| `COPPER` | copper bit 1 (cb), silver bit 10 (sb), gold bit 100 (gb), platinum bit 500 (pb), aluminum bit 10000 (ab) | Abbreviated |
| `CREDIT` | credit 1 (c), megacredit 1000 (mc), gigacredit 1000000 (gc) | Abbreviated |
| `DOLLAR` | cent 1 (c), dollar 100 ($), grand 100000 (k$) | Abbreviated |
| `VICTORY` | victory point 1 | `notrade` |

**Coins are items.** `StdCoins` holds a denomination, a count and a currency; its total value is denomination times count (`Items/Basic/StdCoins.java:153-156`). `makeCurrency` creates a stack (`Libraries/BeanCounter.java:875-887`). `makeAllCurrency` splits an amount into stacks largest first, using `getBestDenominations` (473-498, 902-920).

**Best fit.** `getBestDenomination` (434-451) returns the largest denomination not above the amount, provided the remainder is smaller than the lowest denomination. `makeBestCurrency` (890-899) then returns a single stack. Bank balances use this, so a balance can lose a fraction below the lowest denomination (1497).

**Display.** `abbreviatedPrice` and `abbreviatedRePrice` (349-377) use `lowestAbbreviatedDenomination` (313-346). That scans denominations from the largest down and takes the first one with an abbreviation that divides the amount to within 5% and gives at least 10 coins. Otherwise it uses the lowest abbreviated denomination. For example, 1234.5 gp under `GOLD` is shown as 247pp.

**Moving money.** `addMoney` (923-967) creates stacks and merges each into an existing stack of the same denomination (`putCoinsBack`, 988-1013). `subtractMoney` (1657-1675) destroys the holder's coins in that currency and re-adds the remainder. If funds are short it removes everything and adds nothing, with no error, so callers must check first. `giveSomeoneMoney` (1016-1042) adds money directly when the giver and receiver are the same mob, and otherwise hands each stack over through a `MSG_GIVE`, dropping it if the room refuses.

**Which currency applies.** `getCurrency` (`Libraries/BeanCounter.java:1835-1869`): a shopkeeper's own currency if one is set; else the currency of the mob's start-room area, walking up through parent areas (`Areas/StdArea.java:164-177`); else the default. `isCurrencyMatch` (1777-1793) compares only the codes.

**Legacy MOB money.** A MOB has a numeric money field. `getMoney` (1678-1691) and `getMoneyItems` (1801-1807) turn it into coin items as a side effect, so a read can change the mob.

### 8. Banks, loans and debts

**Accounts.** A bank account is one `COINS;` record per owner per bank chain, in the player-data section named after the chain. `getBankBalance` (`Libraries/BeanCounter.java:1113-1139`) reads it. `modifyBankGold` (1489-1507) recomputes it with `makeBestCurrency` and appends a ledger line. The ledger (`addToBankLedger`, 1076-1096) keeps about 4 KB per owner and chain and trims at `;|;` markers.

**Deposit boxes.** Items are stored as `classID;...` records. Contents of a container are stored with `CONTAINER=key;` (`packBankItemData`, 1192-1218). Finding, withdrawing and rebuilding containers are at 1278-1486.

**Interest and fees.** `StdBanker` runs once per bank interval, one month (`MOBS/StdBanker.java:240-262`, interval 176-181). Coins gain `coinInterest`, default 0 (46). Deposited items lose `itemInterest`, default -0.0001, a storage fee of 0.01% per month (47; applied at 297-302). A banker with an item interest above 0.5 refuses deposit boxes (882).

**Loans.** A loan is coins handed over with an IOU (`MOBS/StdBanker.java:613-618`). The rate is `loanInterest`, default 0.01 per month (48). The term starts at 2 months and adds one month for each step while amount divided by months exceeds 250, up to ten years of months. Repayment is taken on each run as the total due divided by the intervals left (`310-327`). If the loan is overdue and the balance cannot cover it, the banker moves every deposited non-coin item into its own shop stock, releases owned land titles and deletes the debt (335-350).

**Debts.** Debt records are player data keyed `NAME-DEBT-LENDER` (`Libraries/BeanCounter.java:711-836`). Each holds the amount, the reason, the real-time due date in milliseconds and the interest rate. `adjustDebt` (750-836) merges a matching record or creates a new one.

### 9. Auctions

#### 9.1 Live auction (`Commands/Auction.java`)

- **One slot.** The live auction is a single `DefaultAuction` singleton (73-82). A second `AUCTION UP` gets "A live auction is already underway.  Do AUCTION LIST to see it." (368-371).
- **Listing.** `AUCTION UP <item> [opening bid]` charges `liveListingPrice` (default 0; 400-419), removes the item from the seller (270) and makes the opening bid the current bid. The reserve is set to the opening bid minus 1 (`doLiveAuction`, 243-268), so the first bid at the opening price is accepted.
- **Bids and close.** `AUCTION BID <amount>` goes to `CoffeeShops.bid` (2.4 below). `AUCTION CLOSE` by the seller returns the item and the high bidder's reserve (452-479). The listing fee is not refunded.
- **Phases.** `setLiveAuctionState` sets the countdown to `15000 / tickMillis` ticks (99-103). At the default 4000 ms tick (`coffeemud.ini:2151`) that is 3 ticks, about 12 seconds. The start phase repeats until 45 seconds pass with no bid, or 75 seconds in all (114-125). Then the auction moves through RUNOUT, ONCE, TWICE, THREE and CLOSED (84-89, 126-145). The announcements are "going ONCE!", "going TWICE!" and "Last chance!" (130-143).
- **Settlement.** The winner pays the nominal bid. The house keeps `floor(bid * liveFinalCutPct)`, 10% by default (`AUCTIONRATES` fourth value; 157-160). The seller gets the rest (162). The winner gets back the reserve minus the bid (161).
- **Delivery.** The item is dropped on the winner's floor and the winner must pick it up with `postGet`. If that fails, the item goes back to the seller and both are told to contact each other (176-201). The money has already moved by then (161-162).

#### 9.2 Timed auctions (`MOBS/StdAuctioneer.java`)

- **Listing.** The seller chooses a duration between the minimum and maximum days (default 4 and 600; `AUCTIONRATES` sixth and seventh values, `mergeAuctioneerPolicy` 107-121). The fee is `timedListingPrice + timedListingPct * days * base value`, default 2 gp plus 0.1% of base value per day (`StdAuctioneer.java:347-348`). For example, 10 days on a 500 gp item costs 2 + 5 = 7 gp. The item is saved as a journal record and then destroyed (608-609). Expiry is `now + days * hoursInDay * millisPerMudHour + 60000 ms` (373).
- **Owner's buy-out price.** The owner may set one. Anything at or below zero becomes -1, meaning no buy-out (594-600).
- **Settlement.** The auctioneer checks for expired auctions once per mud hour per house (204-207). When a bid exists, the seller and winner are told (214-251 region). The seller is paid only when the winner claims the item (BUY, 643-670): the seller gets `bid - floor(bid * 5%)`, the winner gets `highBid - bid` back, then the item. A month after expiry the auction is cancelled (`TimeManager.MILI_MONTH`, 220).
- **Buy-out.** Before expiry a buyer with enough money pays the buy-out price (`okMessage` 527-537). The seller gets `buyOut - floor(buyOut * 5%)` and the buyer gets the item (677-687).
- **Seller cancel.** Before expiry the owner can cancel and get the item back (635); the high bidder is refunded.

#### 9.3 Bidding as proxy with escrow (`Libraries/CoffeeShops.java:2607-2687`)

- The bidder's maximum (the reserve, `highBid`) is taken from the bidder at once (2644). The previous high bidder's reserve is refunded at once (2633).
- The public bid (`bid`) is set to the previous reserve plus 1 (2642), and the new maximum is stored as the reserve (2643). A bid below the current reserve is answered with "You have been outbid by proxy for ^[item^]." (2679-2685).
- A bid below the public price is refused: "Your bid of @x1 is insufficient." (2622-2627).
- A bid equal to the public price, when a high bidder exists, is refused with "You must bid higher than @x1 to have your bid accepted." (2662-2665).

#### 9.4 Storage and rates

- **Storage.** Each timed auction is a journal entry in `SYSTEM_AUCTIONS_<HOUSE>` (`saveAuction`, 2706-2737). The seller is the journal's `from`, the expiry time (in ms) is `to`, the item name (up to 38 characters) is the subject and the body is XML with `PRICE`, `BUYOUT`, `BIDDER`, `MAXBID`, `CURRENCY` and the item. `getAuctions` rebuilds `DefaultAuction` records from these (2739-2799).
- **Rates.** `AUCTIONRATES` has seven values: live listing fee, timed listing fee, timed listing percentage per day, live cut, timed cut, minimum days, maximum days (`Common/DefaultAuctionPolicy.java:54-68`). Auctioneers override only the timed fields (`mergeAuctioneerPolicy`, 107-121). Live fees always come from the global rates (112, 115).
- **Notices.** `auctionNotify` tells an online player with a message, and an offline one by email (`Libraries/CoffeeShops.java:2863-2884`).

## Content worth mining

- **Currency sets.** The six built-in currencies above. The text form is short enough to write as a Python constant, for example `"GOLD": [(0.01, "copper piece", "cp"), (1, "gold piece", "gp")]`.
- **Economy defaults.** Budget 500000 per month, devalue 20% per unit (0.2% for raw goods), stock reset every 64800 ticks (about three days), auction fees 2 gp plus 0.1% per day, cuts 10% live and 5% timed, auction length 4 to 600 days.
- **Shop types.** 37 `DEAL_*` types, grouped:

  | Group | Types (code) |
  |---|---|
  | Goods | ANYTHING (0), GENERAL (1), ARMOR (2), MAGIC (3), WEAPONS (4), LEATHER (6), JEWELLER (10), ALCHEMIST (11), ANYTECHNOLOGY (14), INSTRUMENTS (31), BOOKS (32), READABLES (33), CLOTHSPINNER (34) |
  | Raw resources and food | FOODSELLER (16), BUTCHER (17), GROWER (18), HIDESELLER (19), LUMBERER (20), METALSMITH (21), STONEYARDER (22) |
  | Services | TRAINER (8), CASTER (9), BANKER (12), CLANBANKER (23), INNKEEPER (24), POSTMAN (28), CLANPOSTMAN (29), AUCTIONEER (30), STOCKBROKER (36) |
  | Property and ships | LANDSELLER (13), CLANDSELLER (15), SHIPSELLER (25), CSHIPSELLER (26) |
  | Living things | PETS (5), SLAVES (27), CHILDREN (35) |

  `INVENTORYONLY` is 7 and `DEAL_CONFLICTS` is at `core/interfaces/ShopKeeper.java:119-126`. The living-thing types are selected with the same zapper machinery as other goods. The SLAVES and CHILDREN categories are mechanical only: they test `isASlave`, `isAgedChild` and the `Skill_Enslave` effect (`Libraries/CoffeeShops.java:2428-2447`, 2555-2558).
- **Shelf flags.** The six `ShelfPriceFlag` values, in 1.
- **Shopkeeper lines.** Short refusals in `Libraries/CoffeeShops.java`: "I'm not interested." (1161), "I don't have that in stock.  Ask for my LIST." (1371), "I don't buy stolen goods." (1153), "That's way out of my price range! Try AUCTIONing it." (1175), "Sorry, I can't afford that right now.  Try back later." (1177), "I won't buy that back unless you put the key in it." (1211).
- **Auction messages.** "New live auction: ^[item^].  The opening bid is @x2." (271), "Auction ended." (2907), "This will cancel your auction on @x1, are you sure (y/N)?" (`MOBS/StdAuctioneer.java`, cancel path).
- **Appraisal.** The VIEW lie system (2.6) is a deterministic way to give players a fuzzy appraisal, keyed on the item name.

## Notable design ideas

- **One data-driven pricing pipeline.** Every sale and buy-back runs through the same ordered steps (base, prejudice, masks, charisma, tax, floor, magic, cap). Each step is a flag or table lookup, so a shop's personality is data, not code.
- **Personality curves.** The charisma and wisdom formula makes the same item cost more to a weak buyer and less to a strong one, and gives the shop a built-in spread between buying and selling (2.2, 2.3). A single haggle skill that adds to CHA then runs the ordinary command is a neat way to reuse the same curve (3).
- **Stock-sensitive devaluation.** A shop that already holds a lot of an item pays less for more. The formula is one line, and it is what stops players farming a single item (4).
- **Budgets as cash flow.** A shop has a periodic cash allowance that refills on a timer, so a shop can run out of money and recover (5).
- **Currency as text.** A currency is a list of denominations written as one string, with an abbreviation per denomination. Display rounding picks a denomination that divides the amount cleanly (7).
- **Bank balance as one coin stack.** A balance is stored as the single best-fit stack of coins, so the account is data, not a list of items (8.1).
- **Proxy bidding with escrow.** Maximum bids are held until outbid or closed, and the public price rises by one above the previous maximum (9.3).
- **Auction house as a sink.** The house cut removes money from circulation, which slows inflation (9.1, 9.2).
- **Lies as hashes.** A shopkeeper can misstate an item's stats in a way that is consistent for each name (2.6).

## Problems and cruft

**Correctness problems (read from the code; not run).**
- **Buy-out keeps the standing bidder's escrow.** The buy-out branch (`MOBS/StdAuctioneer.java:677-687`) never refunds the current high bidder's reserve, which `CoffeeShops.bid` took at 2644. That money is lost.
- **Failed pickup after payment.** If the winner cannot take the item, the item goes back to the seller after the seller has been paid (`Commands/Auction.java:158-201`, with payment at 161-162). The buyer gets nothing. It may be meant as a manual fix, but nothing says so.
- **Proceeds credited twice.** For a monster seller on owned land, `transactMoneyOnly` deposits the proceeds in the owner's bank (`Libraries/CoffeeShops.java:1736-1748`) and then credits the same amount to the shop's purse (1749-1750). I could not confirm that the shop's purse is ever collected elsewhere.
- **Dead victim loops.** `purchaseAbility` saves and restores combat victims with loops whose condition is `x > victims.length`, so they never run (`Libraries/CoffeeShops.java:1857` and `1900-1901`).
- **"Most popular" bank currency is wrong.** `getBankChainCurrency` never updates `mostAmt`, so it returns the last currency seen with any count (`Libraries/BeanCounter.java:1887`, `1891-1892`). It has no callers in the tree.
- **Real-estate listing visibility.** The adjacency flag `skipThisOne` is reset by any later unowned or own neighbour, so a title next to someone else's property can still be listed (`Libraries/CoffeeShops.java:2031-2053`).
- **Timed listing fee is checked and charged in different currencies.** The check uses the auctioneer's currency (`MOBS/StdAuctioneer.java:350`), but the charge uses the seller's home currency (608). The settlement block also swallows exceptions (`StdAuctioneer.java:576-617`).

**Latent problems (no caller triggers them today).**
- `DefaultCoffeeShop.addShelfAdjuster` checks `hasShopProvider` (`Common/DefaultCoffeeShop.java:487`), and `delShelfAdjuster` removes from the provider map (495). Only StockMarket uses adjusters, and it never removes one.
- `DoubleCoffeeShop.inEnumerableInventory` uses `&&` (`Common/DoubleCoffeeShop.java:79`). `totalStockWeight` returns a count (131-134). `emptyAllShelves` clears only the second shop (199-203).
- `getAuctions` filters a MOB by comparing its name with the journal's `to` field, which holds the expiry time (`Libraries/CoffeeShops.java:2751-2752`, `2757`). All callers pass null, so the filter is never used.
- `DefaultAuction.daysRemaining` dereferences a null area if neither mob has a start area (`Common/DefaultAuction.java:110-115`).
- `DefaultCoffeeShop.contentHash` XORs element hashes (`Common/DefaultCoffeeShop.java:938`, `949`). Identical items cancel out, so two identical shelves can hash the same as an empty one.
- The hack in `AuctionCoffeeShop.getStock` writes a numbering value into `expirationDate` (`Common/AuctionCoffeeShop.java:248`). `StdAuctioneer` reads that same field as a name suffix, so the two can collide. Not verified in play.
- `removeSellableProduct` gives a locked container with no key a random key name, `Double.toString(Math.random())` (`Common/DefaultCoffeeShop.java:763`).
- `parseDebt` checks `xml` instead of the parsed list `V` (`Libraries/BeanCounter.java:617-618`).
- `getViewDescription` divides by `phyStats().level()` (`Libraries/CoffeeShops.java:477`). A level-0 item with a secret identity and the lie flag would throw. Not tested.

**Documentation and code disagree.**
- `ShoppingLibrary.getSalesTax` says the return is "a value from 0-1" (`Libraries/interfaces/ShoppingLibrary.java:162`). The code treats it as a percentage (`CoffeeShops.java:761`, `1521-1523`), and `resources/laws.ini:119` defaults it to 0 with the law book showing "%" (`Items/Basic/StdLawBook.java:933`).
- `ShoppingLibrary.parseDevalueRate` says "raw material followed by normal" (`ShoppingLibrary.java:347`). `Economics.java:129-137` and the code (`CoffeeShops.java:925`, `941`) use normal first, raw second, which matches the ini.
- `AUCTIONRATES` says the sixth and seventh values are for "a live auction" (`coffeemud.ini:1853-1862`). The code uses them for timed auctions (`DefaultAuctionPolicy.java:64-65`, `StdAuctioneer.java:325`).
- The charisma comment at `CoffeeShops.java:744` does not match the formula (see 2.2).
- `NO_MAGIC_DISCOUNT` exists (`Common/interfaces/CoffeeShop.java:421`) but the magic discount at `CoffeeShops.java:769-772` never checks it.
- The PREJUDICE help text and example in `coffeemud.ini:1771-1786` describe one entry carrying both BUY and SELL values, which the parser does not support (see 2.4).

**Cruft.**
- The legacy XML loader defaults the budget to `"1000000"` (`Common/DefaultCoffeeShop.java:852-854`). With no period that is 1,000,000 per HOUR, not the ini's 500000 per month.
- `StdShopKeeper` stores prejudice factors in `miscText` (`MOBS/StdShopKeeper.java:504-510`), and `doInventoryReset` also parses `miscText` as shop XML (210-221). How the two coexist is unverified.
- `transactPawn` returns `Double.MIN_VALUE` as a "not bought" sentinel (`Libraries/CoffeeShops.java:1697`).
- `getMoney` and `getMoneyItems` modify the mob as a side effect (`Libraries/BeanCounter.java:1803-1807`).
- Money is `double` throughout, and prices are rounded with `abbreviatedRePrice`, so fractional coins can be lost in bank balances (1497).
- The `TODO: yuck!` note in `checkInternalProviders` (`Common/DefaultCoffeeShop.java:505`) marks a hard-coded provider ID.

**Multiplayer assumptions.**
- A single global live auction slot (`Commands/Auction.java:73-82`, 368-371).
- Bidding is between players, with reserves held in escrow and refunded. Offline players are notified by email.
- Land and ship ownership, clan ownership of property, and the "stolen goods" test all assume other owners exist.
- Player debts are per lender and per player. Bank loans are issued by NPC bankers.

## Takeaways for a Python SUD

- **Keep the pricing pipeline, drop the flags.** An ordered list of pure functions (base, prejudice, charisma curve, tax, floor, cap) is easier to test than the flag soup. Keep only the flags a single-player game needs. `NO_MAGIC_DISCOUNT` and `NO_CHARISMA` can go.
- **Integer money.** Store money as integer copper, not floats. Store a currency as a list of `(value, name, abbr)` tuples, sorted by value, and make change greedily from the largest. Avoid the best-fit balance trick and keep balances exact.
- **Stock-sensitive pricing.** Devaluation by the number of units held is a one-line rule and gives the game its scarcity behaviour. A sketch, not CoffeeMud code:

  ```python
  def shop_buy_price(base: int, held: int, cust_cha: int, shop_wis: int, rate: float = 0.20) -> int:
      c, w = max(1, cust_cha), max(3, shop_wis)
      x = (c - w) / (0.8 * (c + w))
      price = base * (0.5 + 0.5 * x) * max(0.0, 1.0 - rate * held)
      return max(0, round(price))
  ```

- **Haggling as a temporary bonus.** Apply a CHA bonus for one purchase rather than a separate haggle system. The skill is a few lines in Python.
- **Budgets on the game calendar.** Refill each shop's budget once per in-game period, using the game's own day or month counter, and store the refill time with the shop. This is simpler than tick countdowns.
- **Auctions without escrow.** For a single-player game, an NPC auctioneer with a timer and a list of NPC bids is enough. Drop proxy bidding and the offline email path. Keep the fee formula (flat plus a per-day percentage of value) and the house cut, because the cut is a useful money sink.
- **Banks as simple accounts.** An integer balance per bank, with interest once per game month and a loan that requires collateral, covers what the code does without the journal and player-data layers. Keep the pro-rated repayment idea.
- **Drop the mechanics that do not fit.** The slave and child trade categories, and the victim save-and-restore code, are not needed. Keep the deterministic appraisal lie as an optional mechanic.
- **Single-player rethinking.** There are no other bidders, no clan ownership and no offline players, so remove the code that depends on them rather than porting it.

## Coverage

**Read in full (all 14 bucket files):** `Libraries/CoffeeShops.java` (2909 lines), `Libraries/BeanCounter.java` (1958), `Libraries/interfaces/MoneyLibrary.java` (1680), `Common/DefaultCoffeeShop.java` (961), `Libraries/interfaces/ShoppingLibrary.java` (579), `Common/interfaces/CoffeeShop.java` (547), `Common/AuctionCoffeeShop.java` (337), `Common/DefaultAuction.java` (290), `core/interfaces/Economics.java` (280), `core/interfaces/ShopKeeper.java` (245), `Common/interfaces/AuctionData.java` (227), `Common/DoubleCoffeeShop.java` (213), `Common/DefaultAuctionPolicy.java` (206), `Common/interfaces/AuctionPolicy.java` (157).

**Read in full, outside the bucket:** `Commands/Auction.java` (531), `Abilities/Skills/Skill_Haggle.java` (145).

**Read in part, outside the bucket:** `MOBS/StdShopKeeper.java` (190-655), `MOBS/StdAuctioneer.java` (200-251, 299-376, 466-711), `MOBS/StdBanker.java` (176-262, 296-352, 596-622, 876-890), `Areas/StdArea.java` (164-177, 791-800, 893-980), `Abilities/Common/Merchant.java` (478-481), `coffeemud.ini` and `resources/laws.ini` (specific keys), `core/collections/SVector.java` (declaration), `core/CMath.java` (`s_pct`, `s_double`), `Behaviors/StockMarket.java` (grep only), `Items/Basic/StdCoins.java` (value method only).

**Not read:** the rest of `StdShopKeeper`, `StdAuctioneer` (approx. 1-200 and 252-298, 376-465, 712-1008), `StdBanker` (the rest), `StdCraftBroker`, `Law` and treasury internals, the journal and database layer, `CMLib.english` parsing, and `Gypsy`, `Reliquist`, `Arcanist` beyond the disposition grep.
