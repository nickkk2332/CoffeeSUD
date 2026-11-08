# Mob types part 2: auctioneers, service NPCs, deities, mounts, undead and creatures

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b146` (MOBS [part 2/4: GardenSnake .. StdAuctioneer]) · 68 files · 8011 lines of Java.

Conventions: file paths in the table are under `com/planet_ink/coffee_mud/`. In the text, `MOBS/X.java:n` is short for `com/planet_ink/coffee_mud/MOBS/X.java:n`, and `Libraries/`, `Common/`, `core/`, `Abilities/` and `Races/` work the same way. Files outside this bucket are cited only where a mechanic in this bucket depends on them.

## Summary

This bucket holds the concrete MOB classes from `GardenSnake` to `StdAuctioneer`. Most of them are thin: the `Gen*` service NPCs (banker, broker, postman, librarian, shopkeeper, deity) expose a list of named builder stats and leave their behaviour to `Std*` base classes outside this bucket. The auctioneer is the one service with real logic here (`StdAuctioneer.java`, 1008 lines): a timed auction house with a listing fee, a hidden reserve with proxy bidding, escrowed bids, a house cut, and settlement when the winner comes back to claim the item. The creatures are mostly one constructor each, a table of race, level, damage, armour and speed with a few traits, and a handful have distinct mechanics: a sting on a countdown, a prayer-casting shaman, a goblin whose look and weapon come from one seed, an invisible stalker, and milk animals whose supply either regenerates or is unlimited. Several defects are documented under Problems and cruft, including a builder stat table in `GenAuctioneer` whose names do not match its switch, and an auction buy-out that does not refund the current high bidder's escrow.

## Key files and types

| File (under `com/planet_ink/coffee_mud/`) | Role | Lines |
|---|---|---|
| `MOBS/StdAuctioneer.java` | Auction house NPC: listing, bidding, buy-out, claim, cancel, hourly settlement tick | 1008 |
| `MOBS/GenAuctioneer.java` | Data-driven auctioneer: builder stat codes, named auction house, prejudice and ignore masks | 262 |
| `MOBS/GenBanker.java` | Data-driven banker: bank chain and interest codes (behaviour in `StdBanker`) | 250 |
| `MOBS/GenCraftBroker.java` | Data-driven craft broker: listing limits, commission, budget (behaviour in `StdCraftBroker`) | 249 |
| `MOBS/GenPostman.java` | Data-driven postman: postage, hold fees, box fee, hold limit (behaviour in `StdPostman`) | 256 |
| `MOBS/GenLibrarian.java` | Data-driven librarian: overdue charges, loan limits (behaviour in `StdLibrarian`) | 272 |
| `MOBS/GenShopkeeper.java` | Data-driven shop; detail is in bucket b173, section 13 | 249 |
| `MOBS/GenDeity.java` | Data-driven deity: cleric and worship requirements, rituals (behaviour in `StdDeity`) | 174 |
| `MOBS/GenRideable.java` | Data-driven mount: ride basis, rider capacity, mount and dismount text | 203 |
| `MOBS/GenHorse.java` | Horse name on top of `GenRideable` | 49 |
| `MOBS/GenCow.java` | Milk-giving cow: a drink source that regenerates its supply | 333 |
| `MOBS/GenUndead.java` | Undead NPC body: Undead race hooks and death effects | 147 |
| `MOBS/GenRideableUndead.java` | Same code as `GenUndead` on top of `GenRideable` (an undead mount) | 147 |
| `MOBS/GenMob.java` | Base generic NPC whose state is an XML misc-text document; detail in b173, section 12 | 121 |
| `MOBS/Goblin.java` | Goblin whose look, weapon, stats and HP code come from one seed | 185 |
| `MOBS/GoatDoe.java` | Goat that gives unlimited milk | 186 |
| `MOBS/GiantScorpion.java` | Scorpion that stings on a five-tick countdown and applies Poison | 129 |
| `MOBS/LizardManShaman.java` | Cleric lizard man that casts prayers on a three-tick countdown | 134 |
| `MOBS/InvisibleStalker.java` | Invisible, aggressive energy creature | 77 |
| `MOBS/HeavenlyServent.java` | Level 10 servant (human race), wanders and chats | 76 |
| `MOBS/Sheep.java` | Sheep whose description and display text change with state | 90 |
| `MOBS/Hornet.java`, `Puppy.java`, `Kitten.java`, `Penguin.java`, `Spider.java`, `Scorpion.java` | Small creatures that differ only by numbers and behaviour list | 69–82 |
| `MOBS/Pegasus.java`, `PegasusGreater.java`, `Horse.java` | Riding creatures on `StdRideable` | 76–81 |
| Remaining creature classes (about 34 files, listed in the catalogue) | One constructor each: race, level, damage, armour, speed, traits | 46–84 |
| `MOBS/GoldDragon.java`, `GreenDragon.java`, `RedDragon.java`, `SilverDragon.java` | Colour variants of `Dragon` (`MOBS/Dragon.java`, outside this bucket) | 46–47 |

## How it works

### 1. The Gen pattern: a service NPC is a list of named stats

Every `Gen*` service class in this bucket follows the same pattern, so it is described once here.

- The constructor sets only a default name, description and display text. For example `GenBanker` sets `"a generic banker"` (`MOBS/GenBanker.java:52-54`), and `GenShopkeeper` sets no name at all (`MOBS/GenShopkeeper.java:53-54`).
- A private `MYCODES` array lists the builder-facing codes. `getStat` and `setStat` switch on the index that `getCodeNum` finds, and unknown codes fall through to `CMProps.getStatCodeExtensionValue` (`MOBS/GenBanker.java:117-119, 121-150, 153-206`).
- `text()` regenerates the misc text as XML through `CMLib.coffeeMaker().getEnvironmentalMiscTextXML` (`MOBS/GenAuctioneer.java:63-70`). `setMiscText` parses it back with `resetGenMOB` (`MOBS/GenAuctioneer.java:108-113`). Each NPC is therefore saved as a list of codes and values.
- `getStatCodes()` joins the generic `GenMOBCode` values with the class's own codes (`MOBS/GenAuctioneer.java:231-247`), and `sameAs` compares every code (`MOBS/GenAuctioneer.java:249-261`).
- Each service has a "chain" name that keys its shared store. The auction house name is the key for the auction journal, the bank chain names the bank's player-data section (`MOBS/StdBanker.java`, the player-data read in its interest run), and the postal, library and broker chains work the same way. Several NPCs with one chain name share one ledger. The defaults are `"GenBank"` for banks (`MOBS/GenBanker.java:46`), `"main"` for brokers, postal branches and libraries (`MOBS/GenCraftBroker.java:45`, `MOBS/GenPostman.java:45`, `MOBS/GenLibrarian.java:45`), and `""` for auctions (`MOBS/GenAuctioneer.java:45`).

The builder codes for each service, in the order they appear in the source:

| Generic class | Base class (outside bucket) | Builder codes | Defaults visible in the base class |
|---|---|---|---|
| `GenAuctioneer` (`MOBS/GenAuctioneer.java:115-121`) | `StdAuctioneer` | WHATISELL, PREJUDICE, AUCHOUSE, LIVEPRICE, TIMEPRICE, TIMEPCT, LIVECUT, TIMECUT, MAXADAYS, MINADAYS, IGNOREMASK, PRICEMASKS, ITEMMASK, SIVIEWTYPES | Policy from `AUCTIONRATES` (see section 2.1). Codes do not match the switch; see Problems. |
| `GenBanker` (`MOBS/GenBanker.java:117-119`) | `StdBanker` | WHATISELL, PREJUDICE, BANKCHAIN, COININT, ITEMINT, IGNOREMASK, LOANINT, PRICEMASKS, ITEMMASK, SIVIEWTYPES | Coin interest `-0.000`, item interest `-0.0001`, loan interest `0.01` (`MOBS/StdBanker.java:46-48`) |
| `GenCraftBroker` (`MOBS/GenCraftBroker.java:115-118`) | `StdCraftBroker` | WHATISELL, PREJUDICE, BROKERCHAIN, MAXLISTINGS, MAXDAYS, IGNOREMASK, PRICEMASKS, ITEMMASK, SIVIEWTYPES, BUDGET, COMPCT | Max listings 5, max days 900, commission 0.10 (`MOBS/StdCraftBroker.java:58-60`) |
| `GenPostman` (`MOBS/GenPostman.java:115-120`) | `StdPostman` | WHATISELL, PREJUDICE, POSTCHAIN, POSTMIN, POSTLBS, POSTHOLD, POSTNEW, POSTHELD, IGNOREMASK, PRICEMASKS, ITEMMASK, SIVIEWTYPES | Minimum postage 1.0, per pound 1.0, hold fee 1.0 per pound per month, new box 50.0, maximum hold 40 months (`MOBS/StdPostman.java:47-51`) |
| `GenLibrarian` (`MOBS/GenLibrarian.java:115-120`) | `StdLibrarian` | WHATISELL, PREJUDICE, LIBRCHAIN, LIBROVERCHG, LIBRDAYCHG, LIBROVERPCT, LIBDAYPCT, LIBMINDAYS, LIBMAXDAYS, LIBMAXBORROW, LIBCMASK, IGNOREMASK, PRICEMASKS, ITEMMASK, SIVIEWTYPES | Overdue charge 1.0 and 10%, daily overdue charge 1.0 and 10%, 3 to 15 overdue days, 5 items borrowed (constants in `MOBS/interfaces/Librarian.java:57-98`) |
| `GenShopkeeper` (`MOBS/GenShopkeeper.java:105-107`) | `StdShopKeeper` | WHATISELL, PREJUDICE, BUDGET, DEVALRATE, INVRESETRATE, IGNOREMASK, PRICEMASKS, ITEMMASK, SIVIEWTYPES | See b173, section 13 |
| `GenDeity` (`MOBS/GenDeity.java:80`) | `StdDeity` | CLERREQ, CLERRIT, WORREQ, WORRIT, SVCRIT | Empty cleric ritual becomes `SAY Bless me <name>` (`MOBS/StdDeity.java:161`) |
| `GenRideable` (`MOBS/GenRideable.java:81-83`) | `StdRideable` | RIDEBASIS, MOBSHELD, PUTSTR, MOUNTSTR, DISMOUNTSTR, STATESTR, STATESUBJSTR, RIDERSTR | Land-based, rider capacity 2 (`MOBS/StdRideable.java:43-44`) |
| `GenCow` (`MOBS/GenCow.java:243`) | `GenRideable` | QUENCHED, LIQUIDHELD, LIQUIDTYPE | See section 5 |

`GenBanker`, `GenPostman`, `GenLibrarian`, `GenCraftBroker` and `GenShopkeeper` check out against their own switch tables, so their codes line up with their getters and setters. `GenAuctioneer` is the exception (see Problems).

### 2. The auction house (`StdAuctioneer`)

#### 2.1 Data model and policy

The auctioneer's own fields are a set of optional overrides, each `-1` or `0` when unset: `timedListingPrice`, `timedListingPct`, `timedFinalCutPct`, `maxTimedAuctionDays`, `minTimedAuctionDays` (`MOBS/StdAuctioneer.java:47-51`). A static `lastCheckTimes` map records when each house was last settled (`MOBS/StdAuctioneer.java:56`). The auctioneer is created as a good-aligned NPC with intelligence 16 and charisma 25 and a flat 1000 hit points (`MOBS/StdAuctioneer.java:64-74`).

The rates come from a copy of `DefaultAuctionPolicy`, which reads one config string, `AUCTIONRATES`, with the default `"0,10,0.1%,10%,5%,1,168"` (`core/CMProps.java:2855`). The seven fields are, in order: live listing price (0), timed listing price (10), timed listing percent (0.1%), live final cut (10%), timed final cut (5%), minimum days (1) and maximum days (168) (`Common/DefaultAuctionPolicy.java:54-66`). Before each use, `mergeAuctioneerPolicy` copies the auctioneer's own override into the policy whenever the override is not negative (`Common/DefaultAuctionPolicy.java:107-119`). In that way a single NPC can have its own fees while still falling back to the global default.

Each auction is an `AuctionData` (`Common/DefaultAuction.java`, outside this bucket). The fields this bucket uses are the item, the seller, the currency, the nominal bid (`getBid`), the hidden reserve (`getHighBid`), the buy-out price (`getBuyOutPrice`, or `-1` for none), the high bidder, the close time (`getAuctionTickDown`) and a journal key. Auctions are saved with `CMLib.coffeeShops().saveAuction` and removed with `DBDeleteJournal` (for example `MOBS/StdAuctioneer.java:602, 670, 687`), so the house is a database journal, not a list in memory.

The auction house name is `text()` in the base class (`MOBS/StdAuctioneer.java:97-100`) and an explicit field in `GenAuctioneer` (`MOBS/GenAuctioneer.java:97-106`).

#### 2.2 Listing an item (SELL or GIVE)

The sale runs in two phases, as described in the brief. `okMessage` validates the request and asks the questions. `executeMsg` asks the price questions and does the work.

1. `okMessage` requires the seller to be an awake, unbound player (not a monster) and the item to be a non-empty container emptied and worn items removed (`MOBS/StdAuctioneer.java:300-322`).
2. The seller is asked for a number of local days inside the policy range (`MOBS/StdAuctioneer.java:325-346`). Zero cancels the sale.
3. The listing fee is computed from the policy, then the seller is checked against it and asked to confirm (`MOBS/StdAuctioneer.java:347-364`):

```java
double deposit=aRates.timedListingPrice();
deposit+=(aRates.timedListingPct()*(CMath.mul(days,I.baseGoldValue())));
```

With the default rates, the fee is 10 plus 0.1% of the days times the item's base value. For example, seven days on an item worth 500 costs 10 + 0.001 × 7 × 500 = 13.5 (worked from the formula, not from a run).

4. The pending sale is stored in the NPC's single `lastMsgData` field, and the close time is set (`MOBS/StdAuctioneer.java:365-373`):

```java
lastMsgData.setAuctionTickDown(System.currentTimeMillis()+(days*area.getTimeObj().getHoursInDay()*CMProps.getMillisPerMudHour())+60000);
```

So the auction runs for whole in-game days plus one real minute of grace. The day length comes from the area's clock, and a mud hour is `MILLIS_PER_MUDHOUR`, 600000 ms by default (`core/CMProps.java:682`).

5. `executeMsg` asks for an opening price and a buy-now price, both in the currency's lowest denomination. A buy-now of zero or less becomes `-1`, meaning no buy-out (`MOBS/StdAuctioneer.java:590-600`). It then saves the auction, announces it on the `AUCTION` channel (line 603), charges the listing fee (`subtractMoney`, line 608), and destroys the physical item (line 609). The item now exists only as a journal row.

#### 2.3 Bidding and the hidden reserve

A bid is a `BID` message whose target text quotes the amount. `parseBidString` takes the text between the first pair of single quotes (`MOBS/StdAuctioneer.java:269-283`). The item is found by its name, and when the item has an expiration date between 1 and 999 the name is suffixed with `.<date>` to tell identical items apart (`MOBS/StdAuctioneer.java:397-399`, repeated at 480, 625, 701, 765).

`okMessage` applies these rules (`MOBS/StdAuctioneer.java:377-465`):

- The bidder must be at or above the item's level. Otherwise the reply is "That's too advanced for you, I'm afraid." (lines 390-395).
- A seller may cancel only after the minimum number of days has passed, with a Y/N confirmation that defaults to N (lines 407-428).
- After the close time, only the current high bidder is told to use BUY, and everyone else is told the auction is closed (lines 430-439).
- Before it, the amount must be in the auction's currency and covered by the bidder's money (lines 442-461).

The bid itself is applied in `executeMsg` through `CMLib.coffeeShops().bid` (`MOBS/StdAuctioneer.java:736`) and saved if the nominal or reserve changed (lines 744-745). The bid logic in `Libraries/CoffeeShops.java:2608-2689` is a proxy-bidding scheme with two prices:

- `bid` (the nominal price, shown to everyone) and `highBid` (the hidden reserve, the most the current leader will pay).
- A bid below the nominal price is refused: "Your bid of X is insufficient." (`Libraries/CoffeeShops.java:2622-2627`).
- A bid above the reserve, or above the nominal price when there is no reserve, takes the lead (lines 2629-2661). The previous leader's reserve is refunded (line 2633). The new leader's full bid is taken as escrow (`returnMoney(..., -bid)`, line 2644). The nominal price becomes the previous reserve plus 1.0 (line 2642), or the previous nominal price plus 1.0 when there was no reserve (lines 2634-2641), and the reserve becomes the new bid (line 2643).
- A bid that is equal to the nominal price is refused with "You must bid higher than X" (line 2664).
- A bid between the nominal price and the reserve raises the nominal price to the bid, and the bidder is told they have been outbid by proxy (lines 2680-2684). The reserve holder keeps the lead.

The result is that the seller sees a visible price that climbs only as far as needed, and the leader's maximum is held in escrow.

#### 2.4 Buy-out, claim and cancel

- BUY before the close time needs a buy-out price above zero and enough money to pay it (`MOBS/StdAuctioneer.java:526-539`). The check does not compare the buy-out with the current reserve or nominal price.
- BUY before the close time, in `executeMsg`, pays the seller the buy-out less a house cut of `floor(buy-out × timedFinalCutPct)`, charges the buyer the buy-out, hands over the item, and deletes the journal row (`MOBS/StdAuctioneer.java:673-688`). See Problems for what this branch leaves out.
- BUY by the winner after the close time is the claim. The winner gets the difference between the reserve and the nominal price back as change (`returnMoney(mob, currency, highBid - bid)`, line 666). The seller is credited the nominal price less the house cut (lines 643-650), and the item is handed over (line 667). Money moves only at claim time, so a won auction that is never claimed leaves the winner's escrow in place until the tick cancels it.
- Cancelling is a BUY or BID by the seller. It calls `cancelAuction`, which returns the item to the seller and refunds the high bidder's reserve (`Libraries/CoffeeShops.java:2887-2908`, refund at line 2902).

#### 2.5 Hourly settlement tick

`tick` runs settlement at most once per mud hour per house (`MOBS/StdAuctioneer.java:196-259`). The guard is the static map keyed by house name, checked against `CMProps.getMillisPerMudHour()-5` (line 207). The map is also used as a lock: the code synchronises on `CMClass.getSync("AUCTION_HOUSE_"+house)` (lines 204-206). For each auction whose close time has passed:

- More than 30 real-life days after the close time (`TimeManager.MILI_MONTH`, `Libraries/interfaces/TimeManager.java:448`, which is `MILI_DAY*30` in real milliseconds), the auction is cancelled (lines 217-220).
- With a high bidder, both the seller and the winner get a notice, and nothing else happens. The winner must come back to claim (lines 225-237).
- Without a bidder, the item goes back to the seller's inventory. If the seller is offline, their stored items are written to the database (lines 240-247).

#### 2.6 Messages players see

The messages are short and in character. Examples, paraphrased rather than quoted in full: the auctioneer says the item cannot be sold while it is still contained (lines 315-316), refuses to take a value request with a line about letting the people decide and suggesting the SELL command (line 756), and tells a seller that a winner has claimed the item and that their money has been credited after the house cut (lines 646-650).

### 3. Other service NPCs: the rules in their base classes

These base classes are outside this bucket. Only the parts that the generic classes parameterise are summarised here.

- **Banker.** Interest is applied once per mud month, computed as hours per day times milliseconds per mud hour times days per month (`MOBS/StdBanker.java:176-181`). Deposits are scanned for each bank chain in a background run (`MOBS/StdBanker.java:247-259`). Each depositor's coins gain `coinInterest` and their item value gains `itemInterest` per period (`MOBS/StdBanker.java:294-302`). The default item interest is negative, so it works as a storage fee. Accounts of players who no longer exist are deleted (`MOBS/StdBanker.java`, the `playerExistsAllHosts` branch near line 270).
- **Postman.** Postage is `minimumPostage + postagePerPound × chargeableWeight`, where `chargeableWeight` is the item's weight minus one (`MOBS/StdPostman.java:418`, `MOBS/StdPostman.java:422-426`). The holding charge is the number of whole mud months held times `holdFeePerPound` times the chargeable weight (`MOBS/StdPostman.java:429-443`). Packages held longer than `maxMudMonthsHeld` are removed from the database (`MOBS/StdPostman.java:611-630`). A new box costs `feeForNewBox` (`MOBS/StdPostman.java:858-865`).
- **Librarian.** Loans have an overdue charge (minimum and percentage) and a daily overdue charge, a minimum and maximum number of overdue days, and a maximum number of items (`MOBS/interfaces/Librarian.java:57-98` for the defaults). The arithmetic is in `StdLibrarian` and was not traced.
- **Craft broker.** The base class defaults to at most 5 listings (`maxListings`), a maximum of 900 days, and a commission of 10% (`MOBS/StdCraftBroker.java:58-60`). The sweep runs on a timer kept in a static map (`MOBS/StdCraftBroker.java:55`).

### 4. Deities: rituals and worshipper discipline

`Deity` is an interface for a MOB that gives benefits and punishments to worshippers and clerics, and it defines the rituals as text (`MOBS/interfaces/Deity.java:37-49`). The concepts are:

- Holy events are the messages a deity sends: `SERVICE_BEGIN`, `SERVICE`, `SERVICE_CANCEL`, `CURSING`, `BLESSING`, `REBUKE` and `DISAPPOINTED` (`MOBS/interfaces/Deity.java:127-136`).
- Ritual slots are `WORSHIP_BLESSING`, `WORSHIP_CURSE`, `CLERIC_BLESSING`, `CLERIC_CURSE`, `SERVICE` and `POWER` (`MOBS/interfaces/Deity.java:145-153`).
- Requirements for worship and for the cleric role are zapper masks, the same language as other requirements (`MOBS/interfaces/Deity.java:155-211`).
- A ritual is a string of trigger steps joined by `&` (and) or `|` (or). The steps are the `Triggerer.TriggerCode` values: `SAY`, `TIME`, `PUTTHING`, `BURNMATERIAL`, `BURNTHING`, `EAT`, `DRINK`, `INROOM`, `RIDING`, `CAST`, `EMOTE`, `PUTVALUE`, `PUTMATERIAL`, `BURNVALUE`, `SITTING`, `STANDING`, `SLEEPING`, `READING`, `RANDOM`, `CHECK`, `WAIT`, `YOUSAY`, `OTHERSAY`, `ALLSAY`, `SOCIAL`, `INCLUDE` and `TIMEOUT` (`Common/interfaces/Triggerer.java:57` onward). The `Triggerer` engine is outside this bucket.

`GenDeity` only stores five of these strings and their requirements (`MOBS/GenDeity.java:80-101`). `StdDeity` registers each one with the triggerer (`MOBS/StdDeity.java:165-172`) and runs the worship check in `tick` (`MOBS/StdDeity.java:1118-1125`). Every 30 MOB ticks, the deity checks each online player who worships it. A player with a faith of at least 100 who fails the cleric requirement, or any other worshipper who fails the worship requirement, gains a black mark (`MOBS/StdDeity.java:1101-1112, 1139-1142, 1179-1182`). Black marks are kept in a map keyed by player name. At more than 30 marks the deity sends a `REBUKE` holy event and a rebuke message (`MOBS/StdDeity.java:1142-1150`). Below that, it sends `DISAPPOINTED` (`MOBS/StdDeity.java:1159`, with `REBUKE` at line 1147 for the cleric branch). A player who meets the requirement has their entry removed from the blacklist (`MOBS/StdDeity.java:1166-1170`). At the default 4-second tick (`core/CMProps.java:681`), and if MOB ticks fire on every tick (not verified), a check happens about every two minutes.

### 5. Mounts and milk

**Mounts.** `GenRideable` exposes the ride basis as a value of `Rideable.Basis`: `LAND_BASED`, `WATER_BASED`, `AIR_FLYING`, `FURNITURE_SIT`, `FURNITURE_SLEEP`, `FURNITURE_TABLE`, `ENTER_IN`, `LADDER`, `WAGON` and `FURNITURE_HOOK` (`core/interfaces/Rideable.java:44-55`). `setStat` for `RIDEBASIS` accepts the name or the list index (`MOBS/GenRideable.java:121-132`). The text codes control the mount, dismount, put and state messages. `Horse` sets land basis and capacity 2 (`MOBS/Horse.java`, constructor), and `Pegasus` and `PegasusGreater` set `AIR_FLYING` and the `IS_FLYING` disposition bit (`MOBS/Pegasus.java:55`). Pegasus is level 4 and the greater Pegasus is level 6, both with damage 8, speed 3.0 and armour 60 (`MOBS/Pegasus.java`, `MOBS/PegasusGreater.java`).

**Cows.** `GenCow` is a drink source with a supply and a regeneration rate (`MOBS/GenCow.java:44-48`):

- Thirst quenched per drink: 100. Supply held: 1000. Supply remaining at creation: 1000. Liquid: milk (`RawMaterial.RESOURCE_MILK`).
- Drinking reduces thirst and reports "You are no longer thirsty." or "You have drunk all you can." (`MOBS/GenCow.java:101-111`).
- Each tick the supply refills by `liquidHeld / ticksPerMudday`, where `ticksPerMudday = hoursInDay × MILLIS_PER_MUDHOUR / TICK_MILLIS` (`MOBS/GenCow.java:140-160`). Over one mud day the supply therefore refills by its full held amount. The fractional part is carried in `refill` (lines 152-159).

Filling a container from the cow is handled in `executeMsg` (`MOBS/GenCow.java:112-132`). When the target is a drink, the container's own FILL handler takes the liquid from the source (`Items/Basic/StdDrink.java:344-349`). The cow's own branch creates one milk resource item in a container that is not a drink, and it does not reduce the cow's supply. Whether a plain container can reach that branch is not settled: `Items/Basic/StdItem.java:1539-1544` accepts FILL as a target only for drinks and lanterns (see Problems).

`GenCow` is a copy of the milking logic in `Abilities/Misc/Milkable.java` (refill at lines 123-152, the same FILL branch at lines 318-345). `Races/Cow.java:95` grants `Milkable` to the cow race, so the real cow in `MOBS/Cow.java` (outside this bucket) gets the ability and `GenCow` duplicates it.

**Goats.** `GoatDoe` is a goat with infinite milk. `liquidHeld` and `liquidRemaining` return `Integer.MAX_VALUE - 1000`, and the setters do nothing (`MOBS/GoatDoe.java:121-130`). Milk is made as a `GenLiquidResource` item called "some goat milk" (`MOBS/GoatDoe.java:93-100`). Thirst quenched is 100 (`MOBS/GoatDoe.java:115-118`).

### 6. Undead bodies

`GenUndead` sets the NPC as an evil, undead creature of level 1 (`MOBS/GenUndead.java:46-76`):

- Undead race from `CMClass.getRace("Undead")` with charisma 2 (lines 57-63).
- Ability 10, armour 90, weight 150, money 10 (lines 53-68).
- Hit points `roll(level, 20, level)`, which is level d20 plus level (`Libraries/Dice.java:398-406`, line 70).

The class also re-applies the Undead race's effects on top of the base class (`MOBS/GenUndead.java:86-117`), but only when the current race is something other than the Undead race. This looks like a way to keep undead effects when the race is changed, but the intent is inferred from the code.

On death with a body, the class adds two named diseases and a smell effect (`MOBS/GenUndead.java:120-137`):

```java
if((name().toUpperCase().indexOf("DRACULA")>=0)||(name().toUpperCase().indexOf("VAMPIRE")>=0))
    body.addNonUninvokableEffect(CMClass.getAbility("Disease_Vampirism"));
```

A name containing "GHOUL" or "GHAST" gets `Disease_Cannibalism` instead. Every body gets `Prop_Smell` with the text "SMELLS HORRIBLE!". The disease is chosen by matching the name, not by a field.

`GenRideableUndead` is the same code on top of `GenRideable` (`MOBS/GenRideableUndead.java:36-147`). The two files differ only in the class declaration and the ID.

### 7. Creatures

#### 7.1 The constructor template

Every creature class is a constructor that sets the same fields in the same order. `MOBS/Rat.java:36-70` is typical: a name and a description, a display text, a faction alignment, money 0, a wimpy hit point value, damage, the race with `startRacing`, the base ability set to `CMProps.getMobHPBase()` (11 by default, `core/CMProps.java:309`), level, armour 90, hit points from `roll(level,20,level)`, and then the state recovery calls. Behaviours are added with `addBehavior(CMClass.getBehavior(...))`, for example `MOBS/InvisibleStalker.java:68-69`. Almost everything else about an animal (its verbs, its senses, its loot) comes from its race, so the classes hold little beyond numbers.

Two details in the template matter for the mechanics. The base ability is the hit-point code (see section 7.3 for Goblin), because `Libraries/CoffeeLevels.java:80-88` calls `CMLib.dice().rollHP(level, ability)`. `Libraries/Dice.java:80-86` sends codes below 32768 through the NPC hit-point formula `3 + @x1 + (@x1 × @x2)` (`core/CMProps.java:3226`), with the level and the code as the two variables.

#### 7.2 Catalogue

Fields are level, damage (`setDamage`), speed, armour, wimpy hit point, and the traits set in the constructor. A dash means the class does not set the field (the default from `StdMOB` applies). Alignment is from `Faction.Align` (`MOBS/Rat.java` and the others). Every creature file is in `MOBS/`.

Livestock, pets and small animals:

| Class | Lvl | Dmg | Speed | Armour | Wimpy | Align | Race | Traits and notes |
|---|---|---|---|---|---|---|---|---|
| Rat | 1 | 1 | – | 90 | 2 | Neutral | Rat | – |
| Mouse | 1 | 1 | – | 90 | 2 | Neutral | Mouse | – |
| Pig | 1 | 1 | – | 90 | 2 | Neutral | Pig | – |
| Rabbit | 1 | 2 | – | 90 | 2 | Neutral | Rabbit | Weight 5 to 9 (`MOBS/Rabbit.java:53`) |
| Squirrel | 1 | 2 | – | 90 | 2 | Neutral | Squirrel | Weight 4450 to 4454 (`MOBS/Squirrel.java:53`), an outlier |
| Monkey | 2 | 1 | – | 90 | 2 | Neutral | Monkey | – |
| Lizard | 1 | 1 | – | 90 | – | Neutral | Lizard | – |
| GardenSnake | 1 | 1 | – | 90 | – | Neutral | GardenSnake | Described as harmless |
| Snake | 1 | 4 | – | 90 | 2 | Neutral | Snake | – |
| Python | 1 | 7 | – | 90 | – | Neutral | Snake | Intelligence 1 |
| Rattlesnake | 2 | 4 | – | 90 | – | Neutral | Snake | Natural `Poison` ability and `CombatAbilities` |
| Seal | 1 | 4 | – | 90 | 2 | Neutral | Seal | – |
| Shark | 1 | 8 | – | 90 | 2 | Neutral | Shark | – |
| Goat | 1 | 1 | 1.0 | 90 | 0 | Neutral | Goat | – |
| GoatDoe | as Goat | | | | | | Goat | Female, unlimited milk (section 5) |
| Sheep | 1 | 1 | 1.0 | 90 | 0 | Neutral | Sheep | Random gender; state-dependent text (section 7.3) |
| Rooster | 1 | 1 | 2.0 | 90 | 0 | Neutral | Chicken | Male |
| Puppy | 1 | 4 | – | 99 | 2 | Neutral | Puppy | `Follower` with MINTICKS and MAXTICKS both -1, `MudChat` (`MOBS/Puppy.java:57-62`) |
| Kitten | 1 | 4 | – | 90 | 2 | Neutral | Kitten | `Follower`, `MudChat` |
| Penguin | 1 | 4 | – | 90 | 2 | Neutral | Penguin | `Follower`, `MudChat` |
| Parakeet | 1 | 1 | 1.0 | 90 | 0 | Neutral | SongBird | Flying |
| Raven | 1 | 4 | – | 90 | 2 | Neutral | Raven | – |
| Gorilla | 6 | 1 | – | 70 | 2 | Neutral | Gorilla | – |

Wild animals:

| Class | Lvl | Dmg | Speed | Armour | Wimpy | Align | Race | Traits and notes |
|---|---|---|---|---|---|---|---|---|
| Horse | 1 | 4 | – | 90 | 2 | Neutral | Horse | Mount, land basis, capacity 2 (`MOBS/Horse.java`) |
| Hawk | 2 | 1 | 3.0 | 90 | 0 | Neutral | Hawk | Flying, STR 9, DEX 15 |
| Owl | 2 | 1 | 3.0 | 90 | 0 | Neutral | Owl | Flying, STR 5, DEX 17 |
| LargeBat | 2 | 5 | 1.0 | 90 | 0 | Neutral | Bat | Flying, STR 12, DEX 17 |
| GiantBat | 4 | 8 | 1.0 | 80 | 0 | Neutral | (default) | Flying, STR 16, DEX 17 |
| Lion | 5 | 10 | 2.0 | 80 | – | Neutral | GreatCat | STR 13, DEX 17 |
| MountainLion | 5 | 10 | 2.0 | 80 | – | Neutral | GreatCat | Same numbers as Lion, different text |
| Panther | 4 | 8 | 2.0 | 80 | 2 | Neutral | GreatCat | STR 12, DEX 17. Weight set twice (`MOBS/Panther.java:53, 56`) |
| Jaguar | 4 | 8 | 2.0 | 80 | 2 | Neutral | GreatCat | Same as Panther (`MOBS/Jaguar.java:53, 56`) |
| Spider | 1 | 10 | 1.0 | 90 | 2 | Neutral | Spider | DEX 15. `Mobile`, `CombatAbilities` |
| Scorpion | 1 | 10 | 1.0 | 90 | 2 | Neutral | Scorpion | DEX 15. `Mobile` |
| GiantScorpion | 5 | 10 | 2.0 | 70 | 2 | Neutral | GiantScorpion | STR 13, DEX 9, INT 5 to 7. Sting countdown (section 7.3), `Mobile` |
| Hornet | 1 | 1 | – | 80 | 2 | Neutral | Insect | Flying, `Follower`, `CombatAbilities`, `Poison_Sting` at proficiency 100 (`MOBS/Hornet.java:69-73`) |

Humanoids and monsters:

| Class | Lvl | Dmg | Speed | Armour | Wimpy | Align | Race | Traits and notes |
|---|---|---|---|---|---|---|---|---|
| Orc | 1 | – | – | 90 | 0 | Evil | Orc | INT 6, CHA 2, a dagger |
| Goblin | 1 to 3 | – | – | 75 to 94 | 0 | Evil | Goblin | Seeded variant, section 7.3 |
| Gnoll | 2 | – | – | 90 | 0 | Evil | Gnoll | STR 22. Longsword or morning star, chosen by parity of a random number (`MOBS/Gnoll.java`) |
| LizardMan | 2 | 6 | 3.0 | 90 | 0 | Evil | LizardMan | STR 18, INT 6, CHA 2 |
| LizardManShaman | 3 | 6 | 3.0 | 80 | 0 | Evil | LizardMan | Cleric class, prayer countdown (section 7.3) |
| Ogre | 4 | 12 | 3.0 | 80 | 0 | Evil | Ogre | STR 22, INT 8, CHA 2 |
| Minotaur | 6 | 12 | 2.0 | 70 | – | Evil | Minotaur | STR 18, DEX 15, battle axe, `Aggressive` |
| HillGiant | 12 | 19 | 1.0 | 0 | – | Evil | Giant | STR 20, DEX 13, weight 3500 plus 1d1000, `Aggressive` |
| Naga | 4 | 7 | 2.0 | 80 | – | Evil | Naga | STR 12 to 17, DEX 15 to 20, INT 13 to 18, `Mobile`, money 20 |
| Skeleton | 1 | 5 | 1.0 | 90 | – | not set | Skeleton | Longsword. Extends `Undead` (`MOBS/Undead.java`, outside this bucket) |

Other:

| Class | Lvl | Dmg | Speed | Armour | Wimpy | Align | Race | Traits and notes |
|---|---|---|---|---|---|---|---|---|
| InvisibleStalker | 4 | 16 | 1.0 | 0 | – | Neutral | (default) | Invisible via disposition bit (`MOBS/InvisibleStalker.java:64`). STR 20, DEX 13, INT 12 to 14. `Aggressive`, `Mobile` |
| HeavenlyServent | 10 | 25 | – | 0 | 2 | Neutral | Human | `Mobile`, `MudChat`. Display name "an archon servant" |
| GoldDragon, GreenDragon, RedDragon, SilverDragon | (in `Dragon`) | | | | | | Dragon | Colour constants 9, 3, 4 and 8 (`MOBS/Dragon.java:71-77`), passed to the `Dragon(int)` constructor |

#### 7.3 Mechanics that differ from the template

**Goblin: one seed for everything.** `Goblin` rolls a number from 0 to 999 and stores it in the base ability (`MOBS/Goblin.java:48, 90`). That number selects the name, description, display text, weapon and armour from a table of hundred-wide bands (`MOBS/Goblin.java:95-172`):

| Seed | Name and look (paraphrased) | Gear |
|---|---|---|
| 0 to 99 | "a nasty Goblin" | Mace |
| 100 to 199 | "a Goblin", red-skinned | Mace |
| 200 to 299 | "an ugly Goblin" | Mace |
| 300 to 399 | "a Goblin female" | Nothing |
| 400 to 499 | "a mean Goblin", larger | Shortsword and chain mail |
| 500 to 599 | "a smelly Goblin" | Mace |
| 600 to 899 | Three looks, all named "a Goblin", differing only in display text | Mace |
| 900 to 999 | "an armed Goblin" | Shortsword |

The seed is also the hit-point code (section 7.1). It changes hit points only on paths that recompute them (Problems 12). The `tick` method re-runs `setMOBSpecifics` whenever the base ability no longer equals the seed (`MOBS/Goblin.java:61-67`):

```java
if(birthType!=basePhyStats().ability())
    setMOBSpecifics(basePhyStats().ability());
```

Re-running it deletes all items first (`MOBS/Goblin.java:79`). A goblin's identity is therefore one integer, and any code that changes its base ability changes its name and wipes its inventory (see Problems).

**Giant scorpion: sting on a countdown.** `stingDown` starts at 5 (`MOBS/GiantScorpion.java:43`). Each MOB tick in which the scorpion is alive counts it down, and at zero it resets to 5 and stings if it is in combat (`MOBS/GiantScorpion.java:84-96`). `sting()` rolls `Math.round(Math.random()*99)`, and a result below 20 is a hit (`MOBS/GiantScorpion.java:99-109`). A hit sends a fight message ("sting(s)") as a poison message, and if the message value is not blocked, it invokes the `Poison` ability on the victim (`MOBS/GiantScorpion.java`, the end of `sting`). The chance is therefore 20% every 5 MOB ticks while fighting. The constructor adds `CombatAbilities` only if the mob already has abilities (`MOBS/GiantScorpion.java:79`), and it adds none, so this does not happen (see Problems).

**Lizard man shaman: prayers on a countdown.** `spellDown` starts at 3 (`MOBS/LizardManShaman.java:43`). In combat, every third MOB tick it casts (`MOBS/LizardManShaman.java:101-112`). `castSpell` picks a random known prayer 70% of the time, and the other 30% it casts `Prayer_CureLight` at a proficiency of 5d10 plus 50 (`MOBS/LizardManShaman.java:118-127`). The class also defines four natural prayers in `addNaturalAbilities` (`MOBS/LizardManShaman.java:76-99`), but nothing calls that method (see Problems). The class is a cleric by class (`setCurrentClass(Cleric)`, `MOBS/LizardManShaman.java` constructor).

**Sheep: state in the description.** `Sheep` overrides `description()` to say the sheep looks cold while it has a `TemporaryImmunity` effect (`MOBS/Sheep.java:71-77`). That effect is the marker that `StdAbility.spreadImmunity` adds to a mob, with the ability's ID as its text (`Abilities/StdAbility.java:2351-2366`). Which callers add it, and so which ability marks the sheep, was not traced. `displayText` says the sheep is following the herd when more than one creature in the room is tagged `$sheep$` (`MOBS/Sheep.java:79-88`). Gender is 50/50 (`MOBS/Sheep.java:58`).

**Invisible stalker.** The invisibility is a disposition bit, `IS_INVISIBLE`, set on the base stats (`MOBS/InvisibleStalker.java:64`). Flying creatures use the same kind of bit, `IS_FLYING` (for example `MOBS/Hornet.java`, `MOBS/Hawk.java`, `MOBS/Pegasus.java`). A flag on the stats is the whole mechanism; nothing else in the class handles it.

**Goat doe and unlimited milk.** See section 5.

**Heavenly servant.** A human-race creature with damage 25 at level 10, wandering and chatting (`MOBS/HeavenlyServent.java:57-58`). It has no weapon and no special logic in this class.

## Content worth mining

- **The creature table** (section 7.2) is a complete starting roster for the SUD's monsters and animals, with levels from 1 to 12 and a clear split between harmless animals, dangerous wildlife and evil humanoids.
- **Goblin bands** (section 7.3) give a ready pattern for flavour variants: one seed picks name, look, gear and stats.
- **Auction policy.** The default config string (`core/CMProps.java:2855`) gives a complete fee model: a flat listing fee, a percentage of days times value, a percentage cut of the final price, and a day range.
- **Bid messages.** The proxy-bid replies in `Libraries/CoffeeShops.java:2622-2684` are useful templates for a bidding interface. Examples are "You have the new high reserve bid of X ... The current nominal high bid is Y" (line 2653) and "You have been outbid by proxy for X" (line 2684).
- **Deity rituals.** The 27 trigger codes (section 4) are a small language for devotion acts: say a phrase, eat a thing, drink a thing, sit, stand, sleep, wait, read, emote, and so on. A subset would make a good list of player-facing religious acts.
- **Holy events and rituals.** The seven holy events and six ritual slots (`MOBS/interfaces/Deity.java:127-153`) are a neat taxonomy for religious feedback.
- **Undead death effects.** The disease and smell effects on bodies (`MOBS/GenUndead.java:120-137`) are good, cheap flavour for corpses.
- **Sheep text.** "The poor scrawny creature looks cold" (`MOBS/Sheep.java:74`) and the herd line in `displayText` show how one description override can carry a state.
- **Dragon colours.** The four colour constants (`MOBS/Dragon.java:71-77`) are a ready list of named dragon types.

## Notable design ideas

- **Named chains.** Several NPCs that share one chain name share one ledger (auction journal, bank section, postal branch, library, broker). One NPC template can then serve as many shops in one economy (section 1).
- **Hidden reserve with proxy bidding.** The seller sees a nominal price that climbs only as far as needed. The leader's real maximum is held in escrow, and each outbid returns the escrow in full (`Libraries/CoffeeShops.java:2629-2644`). That is a clean way to run an auction with few players.
- **Settlement on claim.** Money moves when the winner returns, not when the auction ends (`MOBS/StdAuctioneer.java:225-237, 632-671`). Unclaimed items wait in the journal until the 30-day cancel rule (`MOBS/StdAuctioneer.java:219-220`).
- **Time-value listing fee.** The fee grows with both days and value, which discourages very long listings of expensive items (`MOBS/StdAuctioneer.java:347-348`).
- **Layered configuration.** A value on the NPC overrides the global default when it is set, and otherwise the default applies (`Common/DefaultAuctionPolicy.java:107-119`). The NPC-level getters for prejudice, ignore mask, pricing and inventory reset fall back to the start area's values (`MOBS/StdAuctioneer.java:829-873, 962-967`).
- **Renewable supply.** Milk refills by its full held amount over one mud day, with a carry for fractions (`MOBS/GenCow.java:150-159`). Supply is a rate, not a stock that someone must restock.
- **Seeded identity.** A goblin's whole identity is one integer that is also its hit-point code. This makes each generated goblin reproducible from its seed (section 7.3).
- **Countdowns in MOB ticks.** The sting and the prayer timers are plain counters (`MOBS/GiantScorpion.java:43, 84-96` and `MOBS/LizardManShaman.java:43, 101-112`). They are cheap and readable.
- **Deity discipline by counting.** Each worshipper accrues black marks while failing a requirement, and the deity rebukes at a threshold (`MOBS/StdDeity.java:1101-1150`). Meeting the requirement clears the record.

## Problems and cruft

Each item was checked against the code cited. Where a path is not fully traced, the item is marked as such.

1. **`GenAuctioneer` stat codes are shifted against its switch.** `MYCODES` lists 14 codes (`MOBS/GenAuctioneer.java:115-121`), but the getter and setter switches (lines 128-156 and 165-217) map them to different fields from index 3 on. `getStat("TIMEPRICE")` returns the timed listing percentage (`timedListingPct`, line 139), `getStat("MAXADAYS")` returns the ignore mask (line 147), and `getStat("LIVECUT")` returns the maximum days (line 143). `setStat("IGNOREMASK", ...)` calls `setWhatIsSoldZappermask`, which `StdAuctioneer` leaves empty (`MOBS/StdAuctioneer.java:950-953`), so the value is silently dropped. `ITEMMASK` and `SIVIEWTYPES` fall through to extension storage, and the live codes (`LIVEPRICE`, `LIVECUT`) are never read. The save and load round trip still works because each code is both written and read by name, so the saved XML keeps the mislabelled names. A builder who uses the names as labelled gets the wrong fields. The other `Gen*` classes in this bucket are aligned (checked against `GenBanker`, `GenPostman`, `GenLibrarian`, `GenCraftBroker` and `GenShopkeeper`).

2. **Buy-out does not refund the current high bidder.** In the buy-out branch before the close time, the code pays the seller, charges the buyer, hands over the item and deletes the journal row (`MOBS/StdAuctioneer.java:673-688`). It does not return `data.getHighBid()` to the high bidder, who still has the reserve held in escrow (`Libraries/CoffeeShops.java:2644`). Cancelling does refund it (`Libraries/CoffeeShops.java:2895-2902`), so the cancel path and the buy-out path disagree.

3. **A bid can exceed the buy-out price.** Neither the BID check (`MOBS/StdAuctioneer.java:377-465`) nor `CoffeeShops.bid` (`Libraries/CoffeeShops.java:2608-2689`) compares a bid with `getBuyOutPrice()`. Combined with item 2, a leader who bids above the buy-out can be undercut by a buy-out, and their escrow is then never returned. This is what the code shows; no run was made.

4. **Settlement code can dereference null.** In the SELL path, `area` is taken from `getStartArea(this)` and then from `getStartArea(msg.source())`, and it is used at line 373 without a null check (`MOBS/StdAuctioneer.java:370-373`). `getStartArea` in the same class discards the result of `CMLib.map().areaLocation(this)` (`MOBS/StdAuctioneer.java:820-822`), so the fallback to `CMLib.map().areas().nextElement()` (line 824) is the only path that can produce an area, and it throws if the world has no areas. `CMMap.areaLocation` does return an area (`Libraries/CMMap.java:1669-1680`), so the assignment was probably intended.

5. **An empty `catch` hides sale failures.** The SELL execution catches every exception and does nothing (`MOBS/StdAuctioneer.java:612-614`). A failure during the price questions leaves no message, and the item's state is not traced.

6. **The pending sale is a single field.** `lastMsgData` is one field on the NPC, shared by every seller who uses it (`MOBS/StdAuctioneer.java:52, 365-369, 579-584`). The owner check at lines 580-583 prevents the wrong seller from completing a sale, but two sellers at once would overwrite each other's pending data. This is a multiplayer assumption.

7. **Static state shared across NPCs.** `lastCheckTimes` is keyed by house name and shared by every auctioneer (`MOBS/StdAuctioneer.java:56`). Its lock is taken on a string built from the house name (`MOBS/StdAuctioneer.java:204`), and the code carries a `TODO` asking whether the synchronised block should be smaller (line 203).

8. **`setCurrency` is asymmetric.** When a currency is already set, the old set is unloaded and the new one is assigned but not loaded (`MOBS/StdAuctioneer.java:903-906`). When none is set, the new one is loaded (lines 909-911). `BeanCounter.getCurrencySet` probably loads on demand (`Libraries/BeanCounter.java:238`), so this is likely harmless but was not traced.

9. **`autoGive` is unused.** `StdAuctioneer.autoGive` is public and nothing in the codebase calls it on an auctioneer (`MOBS/StdAuctioneer.java:261-267`). The stall and librarian classes have their own copies.

10. **Negative money from a seeded roll.** `setMoney(randomizer.nextInt() % 15)` can produce a value from -14 to 14, since `%` keeps the sign of the dividend (`MOBS/Goblin.java:82`), and `setMoney` does not clamp (`MOBS/StdMOB.java:372-375`).

11. **Goblin identity changes with the base ability.** The `tick` method re-rolls the goblin whenever `basePhyStats().ability()` differs from the seed (`MOBS/Goblin.java:63-67`), and each re-roll deletes all items (line 79). Any code that changes the base ability, such as a builder command, will change the goblin's name and gear and empty its pack.

12. **Goblin hit points can jump.** The ability is also the hit-point code (section 7.1). The constructor sets hit points with `roll(level,20,level)` (`MOBS/Goblin.java:91`), but `fillOutMOB` sets them with `getLevelHitPoints`, which uses the code as a multiplier (`Libraries/CoffeeLevels.java:268-286`). With the default formula `3 + level + level × code` (`core/CMProps.java:3226`), a goblin with seed 999 at level 1 would get about 1003 hit points on a fill-out. Callers that pass an existing mob include `Commands/Create.java:846`, `Commands/GModify.java:227` and `Commands/Modify.java:2597`. Whether those paths run on ordinary goblins was not checked.

13. **A dead conditional in the giant scorpion.** `CombatAbilities` is added only if `numAllAbilities()>0` at construction (`MOBS/GiantScorpion.java:79`). The constructor adds no abilities before this line, so the behaviour is never added here.

14. **The shaman's prayers are never granted.** `LizardManShaman.addNaturalAbilities` (`MOBS/LizardManShaman.java:76`) is called from nowhere. A search finds only three other callers, all in the Drow classes (`MOBS/DrowWarrior.java:85`, `MOBS/DrowWizard.java:89`, `MOBS/DrowPriestess.java:90`). The shaman's random-prayer branch therefore depends on whatever abilities it has, and when it has none, `fetchRandomAbility` returns nothing and the cast does nothing (`MOBS/LizardManShaman.java:122-129`). Only the 30% `Prayer_CureLight` branch always works.

15. **A duplicate overload.** `GenUndead` and `GenRideableUndead` each define `recoverMaxState(MOB, CharState)` (`MOBS/GenUndead.java:78-83`, `MOBS/GenRideableUndead.java:78-83`). Nothing calls it. It does no harm, because `StdMOB.recoverMaxState()` already applies the race's `affectCharState` (`MOBS/StdMOB.java:1047-1052`).

16. **Copy-paste duplication.** `GenRideableUndead.java` is `GenUndead.java` with a different superclass and ID (`MOBS/GenRideableUndead.java`, `MOBS/GenUndead.java`). `GenCow`'s milk logic duplicates `Abilities/Misc/Milkable.java` (section 5).

17. **`GenMob.setStat` falls through.** A generic code is written with `setGenMobStat` and then also written to the extension store, because there is no `else` (`MOBS/GenMob.java:91-96`). The double write is harmless but wasteful.

18. **Doubled weight assignments.** `Jaguar` and `Panther` each set weight twice; the first value is overwritten (`MOBS/Jaguar.java:53, 56`, `MOBS/Panther.java:53, 56`).

19. **A likely typo in the squirrel's weight.** `Squirrel` sets weight to 4450 plus a random value up to 4 (`MOBS/Squirrel.java:53`), where the other small animals are in single or low double digits (`MOBS/Rabbit.java:53` gives 5 to 9). The unit of weight was not checked.

20. **Milk from a plain container may be free.** `GenCow` creates a milk item in a non-drink container and does not reduce the cow's supply (`MOBS/GenCow.java:112-132`). The drink path reduces the source in the container's handler (`Items/Basic/StdDrink.java:344-349`). `StdItem` accepts FILL as a target only for drinks and lanterns (`Items/Basic/StdItem.java:1539-1544`), so the plain-container branch may be unreachable. This was not traced through the FILL command.

21. **A message that cannot fire.** In the cleric branch of the deity worship check, the "You feel dirtied by the disappointment" message is guarded by `blackmarks[0]==0` (`MOBS/StdDeity.java:1164-1165`). The counter was just incremented with `++blackmarks[0]` (line 1142), so the guard is never true. The worshipper branch uses `==1` for its first-mark message (line 1203), which does fire.

22. **Escaped backticks in text.** About 41 lines in `MOBS/` write `\\`` inside descriptions (for example `MOBS/Hornet.java:48`, `MOBS/Puppy.java`, `MOBS/Minotaur.java`). The Java string therefore contains a backslash before the backtick. The text percolator replaces `\`` only on one path (`Libraries/MUDPercolator.java:6839`). Whether the backslash shows in play was not checked.

23. **Small wording and spelling problems.** "dangeous" in the goat doe description (`MOBS/GoatDoe.java:49`), "fascilitate" in the broker description (`MOBS/GenCraftBroker.java:52`), "An hawk" (`MOBS/Hawk.java`), and the ID "HeavenlyServent" (`MOBS/HeavenlyServent.java`). Renaming an ID would break saved data, so these are cruft to note, not to fix in place.

24. **Copyright and attribution headers.** Most files carry a licence header with a named author and year range. These are not reproduced here.

## Takeaways for a Python SUD

Single-player means there are no other buyers, sellers, worshippers or session prompts. Where a mechanic depends on other people, the notes say what to replace it with.

1. **Creatures as data, not classes.** Replace the 50 creature classes with one `Creature` dataclass and a table (TOML or CSV) with the fields in section 7.2: `id`, `name`, `description`, `level`, `damage`, `speed`, `armour`, `wimpy_hp`, `alignment`, `race`, and a set of `traits`. Use a helper `roll(level, 20, level)` for hit points, and map traits to small behaviour objects (`aggressive`, `mobile`, `follower`, `flying`, `invisible`, `sting`, `caster`). A trait set is enough to express "Mobile, Aggressive, invisible" without a class per creature.

2. **Services as components.** Keep the auction house, bank, postal office and library as one `Service` component with a `kind`, a `chain` key and a parameter dict, attached to a town NPC. Do not write one subclass per service. Derive the builder or editor field list from the dataclass fields so the codes cannot drift from the getters and setters, which is the bug in `GenAuctioneer`.

3. **Integer money, one ledger.** Store money in the smallest unit as an integer. Every path that moves money (outbid, buy-out, cancel, timeout, claim) should go through one `refund(holder, amount)` helper, so no path can forget the escrow. Reject negative amounts at the boundary, which avoids the Goblin problem.

4. **A simpler auction for one player.** The useful parts are the listing fee formula, the house cut, the nominal and reserve pair, and settlement on claim. Keep those. Replace the other bidders with NPC bidders who bid up to a random reserve when the player is outbid or when time runs out. Then the proxy-bid logic still matters, and the player is never bidding against an empty room. Reject any bid above the buy-out price, and refund the escrow on every buy-out (Problems 2 and 3).

5. **Game-time events, not real-time ticks.** Schedule settlements, rent, interest and postal holds on a priority queue keyed by in-game minute (`heapq`), and advance the clock with the player's actions or with a turn counter. Keep a mud-day constant: `hours_per_day * minutes_per_hour`. This removes the static lock maps (`MOBS/StdAuctioneer.java:56, 204`).

6. **Renewable supply as a rate.** Model a cow-like source with `supply = min(capacity, supply + capacity * elapsed_days)` and carry the fraction as a float or a remainder. This is simpler than a per-tick accumulator and works with time skips.

7. **Fees as pure functions.** Postage `min + per_lb * (weight - 1)`, hold fee `floor(months) * fee * (weight - 1)`, a purge after `max_months`, overdue charges with a minimum and a percentage, and a monthly interest accrual are each a few lines. Write them as functions of `(weight, months)` and test them directly.

8. **Seeded variants.** Keep the goblin's idea: `random.Random(seed)` chooses look, gear and stats from a banded table, and the seed is the only identity stored. Do not re-roll on tick. The table of bands (section 7.3) becomes a list of tuples, `[(0, 99, "a nasty goblin", ...), ...]`.

9. **Devotion as a counter.** A deity can be a world state rather than a second character. Keep a per-player `disfavour` counter that rises when a requirement fails, decays when it is met, and triggers a consequence at a threshold (the 30-mark rule at `MOBS/StdDeity.java:1142`). Drop the ritual language entirely, or keep a tiny version: a list of named acts (`say`, `eat`, `drink`, `sit`, `wait`) that a player can perform to earn favour.

10. **Descriptions as functions of state.** The sheep's description and herd text (`MOBS/Sheep.java:71-88`) become a method, `describe(creature, world)`, that reads state. Do not store it as text.

11. **Multiplayer assumptions to drop.** The database journal and per-chain sections, the `AUCTION` channel broadcast, the blocking `session().prompt` questions with 30-second timeouts (`MOBS/StdAuctioneer.java:329, 592, 594`), the offline-seller notices, the single shared `lastMsgData`, and the account cleanup for banks are all written for many players. A single-player game needs a menu or command state machine and an in-game message log instead.

12. **Do not copy.** The name-matched disease effects, the seed that doubles as a hit-point code, the copy-paste overloads, the empty catch, the misaligned stat table, and the escaped backticks in text. Each is a small defect that does not help a rewrite.

## Coverage

- **Read in full:** `MOBS/StdAuctioneer.java` (all 1008 lines), `MOBS/GenAuctioneer.java`, `MOBS/GenBanker.java`, `MOBS/GenDeity.java`, `MOBS/GenRideable.java`, `MOBS/GenUndead.java`, `MOBS/GenMob.java`, `MOBS/GenHorse.java`, `MOBS/Goblin.java`, and `MOBS/Rat.java`.
- **Read as constructors and the methods the chapter cites:** `MOBS/Sheep.java`, `MOBS/InvisibleStalker.java`, `MOBS/HeavenlyServent.java`, `MOBS/GoatDoe.java` (fill, supply and thirst methods), `MOBS/GiantScorpion.java` (constructor, tick and sting), `MOBS/LizardManShaman.java` (constructor, tick, castSpell and addNaturalAbilities), `MOBS/Minotaur.java`, `MOBS/Gnoll.java`, `MOBS/Puppy.java`, `MOBS/Skeleton.java`, `MOBS/Hornet.java`, `MOBS/Horse.java`, `MOBS/Pegasus.java`, `MOBS/PegasusGreater.java`, `MOBS/Naga.java`, and `MOBS/GenRideableUndead.java` (diffed against `GenUndead.java`).
- **Read in part:** `MOBS/GenCow.java` (lines 36 to 300; the rest is accessors and boilerplate), `MOBS/GenPostman.java`, `MOBS/GenLibrarian.java`, `MOBS/GenCraftBroker.java` and `MOBS/GenShopkeeper.java` (the stat code list and the get and set switches only), and the four dragon subclasses (constructor only).
- **Skimmed (constructor fields extracted by a script):** the remaining 34 creature classes (Lion, MountainLion, Panther, Jaguar, Spider, Scorpion, Rattlesnake, Python, Snake, GardenSnake, Lizard, Seal, Shark, Pig, Mouse, Rabbit, Squirrel, Monkey, Gorilla, Raven, Parakeet, Kitten, Penguin, Hawk, Owl, LargeBat, GiantBat, Goat, Rooster, Orc, Ogre, LizardMan, HillGiant, and the constructors' numeric fields for each). Their numbers are read from the constructors, and their behaviour lists come from the `addBehavior` and `addAbility` calls.
- **Outside the bucket, followed only as far as needed:** `MOBS/StdBanker.java`, `MOBS/StdPostman.java`, `MOBS/StdDeity.java`, `MOBS/StdCraftBroker.java`, `MOBS/StdLibrarian.java`, `MOBS/Dragon.java`, `MOBS/Undead.java`, `MOBS/interfaces/Deity.java`, `MOBS/interfaces/Librarian.java`, `Libraries/CoffeeShops.java` (auction and bid code), `Libraries/CoffeeLevels.java`, `Libraries/Dice.java`, `Libraries/CMMap.java`, `Libraries/BeanCounter.java`, `Common/DefaultAuctionPolicy.java`, `Common/interfaces/Triggerer.java`, `Abilities/StdAbility.java` (`spreadImmunity`), `Abilities/Misc/Milkable.java`, `Items/Basic/StdDrink.java`, `Items/Basic/StdItem.java` and `core/CMProps.java` (defaults only).
- **Skipped:** none of the 68 files were left unopened. The `StdAuctioneer` and `Gen*` helpers in this bucket were all read; the Std base classes were read only where a mechanic here depends on them.
