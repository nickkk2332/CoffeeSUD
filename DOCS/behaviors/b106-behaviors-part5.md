# Behaviors part 5: markets, weather, currents, taxes and mob tickers

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b106` (Behaviors [part 5/5: Scavenger .. WimpyAggressive]) · 16 files · 7861 lines of Java.

All citations are relative to the CoffeeMUD folder (`com/planet_ink/coffee_mud/...`).

## Summary

This bucket is the last slice of the `Behaviors` package. Its centrepiece is `StockMarket`, a complete stock exchange that turns game events (births, officer deaths, thefts, quests, building, shop trade) into per-stock "influence" counts and converts them, every few game days, into price moves by dice rolls against a lookup table. `WeatherAffects` is a timed weather engine (disease, rust, storms, drought fires, boat damage), `WaterCurrents` pushes idle players and items downstream, `TaxCollector` bills citizens and fines, and `TaxiBehavior` runs a fare-paying ride along a tracked path. The remaining classes are small mob behaviours (scavenging, aggression, fleeing, theft, sleeping, target switching) built on `ActiveTicker` (random-interval, percentage-gated firing) or `StdBehavior` (message veto hooks), plus two wrappers that hand scripts to the scripting engine. For a single-player game the reusable parts are the influence-to-price loop, the event-driven weather timers and the ticker helpers; the clan, archon and certificate-transfer bookkeeping is multiplayer machinery.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Behaviors/StockMarket.java` | Multi-config stock market: per-area stocks sold as certificates, influence-driven price updates, dividends, bankruptcy, splits, owner discounts | 2294 |
| `com/planet_ink/coffee_mud/Behaviors/WeatherAffects.java` | Weather engine on rooms and areas: timed storms, disease, rust, drought fires, gusts, freeze-over, boat damage, ranged and slip effects | 1393 |
| `com/planet_ink/coffee_mud/Behaviors/WaterCurrents.java` | Pushes idle players, items and optionally boats out of watery rooms along configured directions | 921 |
| `com/planet_ink/coffee_mud/Behaviors/Sounder.java` | Timed and event-triggered emotes with a small trigger grammar | 543 |
| `com/planet_ink/coffee_mud/Behaviors/TaxCollector.java` | Mob tax collector: computes fines, citizen and back taxes, demands and collects payment, accuses or attacks non-payers | 455 |
| `com/planet_ink/coffee_mud/Behaviors/StdBehavior.java` | Base class of all behaviours: no-op hooks, parms storage, helpers | 410 |
| `com/planet_ink/coffee_mud/Behaviors/ScriptableEverymob.java` | Gives a `Scriptable` copy to each monster in a room or area on first contact | 290 |
| `com/planet_ink/coffee_mud/Behaviors/Scriptable.java` | Delegates all `ScriptingEngine` calls to `DefaultScriptingEngine` | 278 |
| `com/planet_ink/coffee_mud/Behaviors/TaxiBehavior.java` | `Concierge` subclass: a paid ride along a tracked path | 255 |
| `com/planet_ink/coffee_mud/Behaviors/Scavenger.java` | Mob picks up loose items and dumps them in a trash room when loaded | 211 |
| `com/planet_ink/coffee_mud/Behaviors/VeryAggressive.java` | `Aggressive` subclass: wanders toward a suitable target, then attacks | 183 |
| `com/planet_ink/coffee_mud/Behaviors/Wimpy.java` | Mob flees when a matching enemy is fighting it; optional "very" mode keeps it moving | 145 |
| `com/planet_ink/coffee_mud/Behaviors/Thiefness.java` | Thief combat: periodically invokes Thief_Steal, Thief_Mug or Thief_Swipe on a victim | 138 |
| `com/planet_ink/coffee_mud/Behaviors/WimpyAggressive.java` | `Aggressive` subclass that attacks only sleeping targets | 128 |
| `com/planet_ink/coffee_mud/Behaviors/Vagrant.java` | Mob lies down to sleep at random; movement is blocked while asleep | 110 |
| `com/planet_ink/coffee_mud/Behaviors/TargetPlayer.java` | Moves a mob's victim to the lowest-level player in the victim's group | 107 |

Base classes read for interactions (outside this bucket): `com/planet_ink/coffee_mud/Behaviors/ActiveTicker.java` (timing gate), `.../Behaviors/Aggressive.java` (aggression and bribes), `.../Behaviors/CombatAbilities.java` (combat-class helpers, partly read), `.../Behaviors/Concierge.java` (fares), `.../Behaviors/PuddleMaker.java` (weather state).

## How it works

### Shared framework

- **Hooks.** A behaviour overrides some of `tick(Tickable,int)`, `okMessage(Environmental,CMMsg)` and `executeMsg(Environmental,CMMsg)`. The `StdBehavior` defaults are a no-op `executeMsg`, an `okMessage` that returns true, and a `tick` that only refuses destroyed objects (`com/planet_ink/coffee_mud/Behaviors/StdBehavior.java:243-253`, `:297-302`). A veto is `okMessage` returning false, for example `com/planet_ink/coffee_mud/Behaviors/Vagrant.java:70-74` and `com/planet_ink/coffee_mud/Behaviors/TaxCollector.java:290-295`. This is the two-phase flow from the brief.
- **Parameters.** Each behaviour is configured by one string (`StdBehavior.java:213-223`), parsed with `CMParms.getParmInt`, `getParmStr` and `getParmBool` using names such as `delay`, `min`, `max`, `chance`, `mingrp`. Text after a `;` is a zapper mask, for example `com/planet_ink/coffee_mud/Behaviors/Scavenger.java:76-86`.
- **Timing gate.** `ActiveTicker` defaults to `minTicks=10`, `maxTicks=30`, `chance=100` (`com/planet_ink/coffee_mud/Behaviors/ActiveTicker.java:49-52`). `canAct` decrements `tickDown`; when it drops below 1 it redraws `tickDown` uniformly in `[minTicks,maxTicks]`, refuses mobs that cannot act at all, and otherwise succeeds with probability `chance` (`ActiveTicker.java:136-165`; `canChance` at `:128-134`). Subclasses override the defaults in their constructors: Scavenger `1..10` (`Scavenger.java:61`), TargetPlayer `3..12`, chance 100 (`TargetPlayer.java:56`), WaterCurrents `3..5`, chance 75 (`WaterCurrents.java:68`).
- **Tick IDs.** Mob behaviours return early unless `tickID==Tickable.TICKID_MOB` (`WimpyAggressive.java:119`, `Wimpy.java:82`).
- **Clock.** The default tick is 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`). `WEATHER_TICK_DOWN=150` (`com/planet_ink/coffee_mud/Common/interfaces/Climate.java:191`) is therefore 10 minutes, as its comment says.
- **Dice.** `rollPercentage()` returns 1 to 100 (`com/planet_ink/coffee_mud/Libraries/Dice.java:409-412`). `roll(n,d,m)` returns `m` when `d<=0`, otherwise the sum of `n` dice of 1..d plus `m` (`Dice.java:398-406`). So `roll(1,50,x)` is `x+1..x+50`.

### Scavenger (mob)

- **Data.** `origItems` (item count at first tick), `trashRoomID` (`TRASH=`), `doAtNight` (`ATNIGHT`), `mask`, `returnR` (`Scavenger.java:51-56`).
- **Trigger.** Overloaded when weight reaches `round(0.9*maxCarry)` or the item count reaches `maxItems` (`Scavenger.java:107-108`). With `ATNIGHT` it also dumps once per game day at night (`:103-104`, `:110-111`, `:121`).
- **Dump.** At the trash room it puts everything into the largest non-gettable container there, or drops everything (`:124-141`). With `ATNIGHT` it repeats the same command (`:142-148`), under the comment "a signal to include money". It then wanders off or returns to `returnR` (`:149-152`).
- **Travel.** Away from the trash room it invokes `Skill_Track` toward it (`:157-164`).
- **No trash room.** When overloaded it destroys items beyond `origItems` (`:165-184`).
- **Pick-up.** It does nothing while PCs are present (`:186-187`). Otherwise it collects up to 1000 candidates and forces `GET $name$` on one chosen with `roll(1,n,-1)` (`:188-204`). The candidate filter is broken (see Problems).

### Aggression family

- **Aggressive (base, `com/planet_ink/coffee_mud/Behaviors/Aggressive.java`).** `delay` sets `tickWait`. `MESSAGE` is said before attacking. `BRIBE=amt,ticks,wait` demands payment. Flags: `WANDER`, `CHECKLEVEL`, `MOBKILL`/`MOBKILLER`, `NOGANG`/`NOGANGUP`, `MISBEHAVE` (`Aggressive.java:156-184`). `startFight` sneaks a `Thief_BackStab` on a hidden aggressor (`:205-213`) and queues `KILL` (`:217`). `pickAFight` scans the room for a non-group target passing `maskCheck(mask, mob)` (`:296-313`).
- **VeryAggressive.** Every `delay` ticks, behind a 15% gate (`VeryAggressive.java:102`, commented "let's not do this 100%"), it skips if it is following or grouped with someone nearby, tracking, or unable to taste (`:105-109`). It then scans every direction for an inhabitant it can see, who is not staff, and whose level is within 8 of its own unless its mask has entries (`:124-147`). `EXPRATE` applies only with `CHECKLEVEL` (`:142`). It walks there and calls `pickAFight` (`:157-162`).
- **WimpyAggressive.** `grantsAggressivenessTo` requires the target to be asleep and to match the mask (`WimpyAggressive.java:67-72`). `pickAWimpyFight` loops the room and calls `startFight` on the first sleeper it finds (`:88-100`). The mask is tested against the observer, not the sleeper (line 94).

### Fleeing, theft and sleep

- **Wimpy.** Every `delay` ticks, for each inhabitant matching the fear mask who is fighting this mob (`Wimpy.java:98-99`), the mob flees to a random open exit in its area, with UP only if flying (`:102-118`). With no exit it flees "NOWHERE" and calls `beMobile` (`:117-122`). With `very`, a mob out of combat runs its mobility behaviours up to 100 times to keep moving (`:125-138`, loop at `:130-132`).
- **Thiefness.** `startBehavior` sets `COMBAT_RANDOM`, makes the mob a Thief class, calls `newCharacter`, and unless `preCastSet` is set applies `setCombatStats(mob,0,-25,15,-15,-15,-15,true)` and `setCharStats` (`Thiefness.java:58-74`). That renders as `ARMOR-25 DAMAGE+15 HP-15 MANA-15 MOVE-15 SPEED=1` (`com/planet_ink/coffee_mud/Behaviors/CombatAbilities.java:263-277`). Each mob tick, on a 10% roll (`Thiefness.java:89`), it picks a victim: its combat victim, or the last visible non-monster inhabitant outside its group (`:93-106`). It skips staff (`:108-109`), uses `Thief_Mug` (in combat) or `Thief_Steal` when `roll>50`, else `Thief_Swipe` (`:112`), sets proficiency to `roll(1,50,level*15)` (`:131`), and invokes the ability (`:117-132`). A success sets `tickDown=2` (`:91`).
- **Vagrant.** With no active timers, a 50% roll (or combat) makes it stand and sets `wakeForTicks = roll(1,30,0)` (`Vagrant.java:87-92`). Otherwise it lies down ("curl(s) on the ground and go(es) to sleep", `:97`) and, if it is asleep, sets `sleepForTicks = roll(1,10,0)` (`:98-99`). While `sleepForTicks>0`, its move or hands actions are refused and it is kept `IS_SLEEPING` (`:59-77`). The sleep timer ends without standing it up; only `postStand` wakes it (`:91`).
- **TargetPlayer.** While fighting, it collects the victim's group, skips monsters, and takes the non-monster of lowest level (`TargetPlayer.java:72-91`). It switches its victim if that player is visible (`:94-100`). The class comment says "hero targeting"; the code targets the weakest.

### Scripting wrappers

- `Scriptable` (`Scriptable.java:35-259`) implements `ScriptingEngine` by forwarding to `CMClass.getCommon("DefaultScriptingEngine")` (`:49-55`). Its `tick` stops when the host is not `RUNNING` (`:256-257`). `getParms` returns the script (`:134-137`).
- `ScriptableEverymob` (`ScriptableEverymob.java:37-290`) keeps a `sampleB` script. On first tick, on `startBehavior`, or on a LOOK at a room (`:133-137`), it attaches a copy of the script to each monster whose start room is in the same metro area and that lacks a `Scriptable` (`:60-75`, `:97-116`). It runs once per instance (`:100-102`).

### Sounder (triggered emotes)

- **Grammar.** `setParms` splits on `;` (`Sounder.java:84`). An optional first field carries `min=` and `max=` (defaults 23 and 23, `:88-96`). Each emote may start with a keyword: `SOUND <pct> <text>` for timed emotes (`:106-118`), or `GET`, `EAT`, `PUSH`, `PULL`, `SIT`, `DROP`, `WEAR`, `OPEN`, `CLOSE`, `HOLD`, `WIELD`, `DRINK`, `MOUNT`, `REMOVE`, `PORTAL_ENTER`, `PORTAL_EXIT`, `DAMAGE`, `FIGHT` (`:120-334`). Each also has a `_ROOM` form that sets `ROOM_MASK` (`:126` and onward). Text may use `$n`/`$N` (name), `$e` (he/she), `$s` (his/her) and `$p` (host name) (`:100-105`, `:366-367`).
- **Bit layout.** `UNDER_MASK=1023`, `TICK_MASK=65536`, `ROOM_MASK=32768` (`Sounder.java:55-57`). Timed triggers store the percentage in the low bits (`:115`), and event triggers store the message type there (`:122` and the other assignments, matched at `:504-505`).
- **Timed firing.** `tickDown` counts down; at 0 it resets to a random `[min,max]` (`:418-422`). The first timed emote whose percentage roll passes is emoted, then the loop stops (`:423-429`). Timed text is wrapped in `^E` ... `^?` (`:116`).
- **Event firing.** `executeMsg` maps message types to triggers, for example OPEN/CLOSE on the host, DROP of an item into the room, ENTER/LEAVE of the behaviour's room, `WEAPONATTACK` when the host is source or tool, and `DAMAGE` when the host is target or tool (`:442-493`). Matching text is added as a trailer (`:536-537`). `PORTAL_ENTER` emotes are delayed 500 ms by a scheduled runnable and sent only if the mover is still present (`:512-535`).
- **Message shape.** The `ROOM_` form calls `CMClass.getMsg(src,null,null,NO_EFFECT,NO_EFFECT,MSG_EMOTE,text)` (`:509`), so only others see it. The normal form sets only the source code (`:511`), so the actor sees it.
- **Emoter.** `doEmote` builds a temporary neutral `StdMOB` named after the host, moves it through each room of an area (`:373-378`) or a single room, sends the emote to non-monsters who can sense it (`:340-361`), and destroys it.

### Taxi (TaxiBehavior on Concierge)

- Paying in `giveMerchandise` (`com/planet_ink/coffee_mud/Behaviors/TaxiBehavior.java:76-91`) computes a trail with `CMLib.tracking().findTrailToRoom` (`:82`), records the origin (`:84`), and attaches a `Prop_Adjuster` whose misc text is `"sen+"+PhyStats.CAN_NOT_TRACK`, that is `sen+262144` (`:85-86`; `com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:433`). The sense term is applied by `com/planet_ink/coffee_mud/Abilities/Properties/Prop_HaveAdjuster.java:370`. The traveller becomes untrackable for the ride.
- **Fare.** `Concierge.getPrice` (`com/planet_ink/coffee_mud/Behaviors/Concierge.java:340-357`) is `basePrice + perRoomPrice*trail.size()` unless a fixed rate exists for the destination. Taxi defaults are base 10 and 1 per room (`TaxiBehavior.java:225-226`).
- **Ride.** Each tick (`:166-212`) the rider or vehicle takes one step along the trail (`:194`). The ride ends on arrival (`:179-180`), on a closed or missing next exit (`:204-205`), on an outdoor-only route entering an indoor room (`:189-190`), or when a MOB can no longer act freely (`:174-175`). `endTheRide` says "We're here.  Best of luck!." or "This is as far as I can go.  Best of luck!." (`:117-119`), dismounts riders not in the default list (`:129-141`), and returns the observer home (`:142-152`).

### Tax collector

- **Parameters.** `WAIT` default 300000 ms (5 min, `TaxCollector.java:52`, `:77`). `GRACE` default 3600000 ms (1 h, `:53`, `:78`).
- **Bill (`totalMoneyOwed`, `:91-130`).** Back taxes are the sum of `backTaxes()` on land titles owned by the debtor or its clans (`:94-105`). Fines are `LegalBehavior.finesOwed` (`:111`). Citizen tax is the debtor's shopkeeper value times `CITTAX/100` when a law exists and the debtor is not an officer or judge (`:116-121`). With no law it is one tenth of that value (`:126`). The total is rounded (`:127-128`).
- **Monthly refresh (`tick`, `:323-355`).** Once per area month, if the law sets `PROPERTYTAX > 0`, the collector lists all unique land titles and marks owners with back taxes as debtors (`:332-346`). It also reads the treasury room and container (`:351-353`).
- **Demands.** Each tick, if the room holds more than one inhabitant, it picks a random one who is not an animal, not staff, not in a rival clan, and is visible (`:357-369`). A bill is spoken and recorded in `demanded` (`:397-421`). A monster debtor is forced to `GIVE <amount> <collector>` (`:424-428`). A PC gets a reminder one tick later (`:432-443`).
- **Escalation.** After `waitTime` without payment (`:373`): a monster with no land title is dropped from the list (`:376-379`); with a law the collector accuses the debtor of TAXEVASION (`:381-386`); with no law it threatens the debtor and attacks (`:387-393`).
- **Payment (`executeMsg`, `:132-266`).** Coins given to the collector are first banked in the treasury (`:147-156`). Then, in order: citizen tax if demanded (`:158-166`), fines (`:171-193`), back taxes per property (`:195-226`), and any remainder spread evenly over the debtor's properties (`:227-243`). A trailer line reports "paid in full" or "still owe" (`:244-262`).
- **Refusal (`okMessage`, `:268-298`).** Wrong currency, or less than the current bill, is refused (`:284-295`).

### Water currents

- `WaterCurrents` (`com/planet_ink/coffee_mud/Behaviors/WaterCurrents.java:39-273`) runs on rooms and areas. A room tick sweeps its own contents and, if the room below is an empty underwater grid, every cell of that grid (`:248-259`). An area tick sweeps each watery room (`:262-270`). One `done` vector is shared, so nothing moves twice in a tick (`:243`).
- **Who moves.** PCs that are not riding, flying, in combat or mobile (`:128-133`). Items that are not in a container, not flying, not mobile, and gettable or Boardable (`:147-157`). Water-based rideables with riders only when `BOATS` is set (`:149-152`).
- **Direction.** Each configured direction letter is tried in order; the first open exit into a room of the same domain or a watery room wins (`:169-185`). The default is `NE` (`:114-115`).
- **Movement.** A mob's `MSG_LEAVE` can be vetoed (`:197-199`), then it is moved with `bringMobHere` and `postLook` (`:201-205`). Items are moved by `moveItemTo` (`:224`), with a factory mob named "the water" as actor (`:191`).
- **Messages.** Default MOB text is "<S-NAME> <S-IS-ARE> swept in from @x1 by the current." and "... swept @x1 by the current." (`:53-54`). Item text is "@x2 is swept in from @x1 by the current." (`:55-56`). Custom `INMSG`/`OUTMSG` produce the item forms automatically (`:86-96`).
- **Stub ability.** `AWaterCurrent` (`:278-920`) is a 640-line `Ability` stub whose only use is as the tool on the move messages. It carries `FLAG_TRANSPORTING` (`:408-411`) and `ACODE_PROPERTY` (`:656-659`).

### Weather (WeatherAffects)

- **Structure.** It extends `PuddleMaker`, which handles puddles and dust (`com/planet_ink/coffee_mud/Behaviors/PuddleMaker.java:180-249`). Its `tick` saves `lastWeather` before the parent updates it (`WeatherAffects.java:664`) and restores it after (`:696`), so checks compare the previous tick's weather with the current one (`:700-702`).
- **Weather source.** `areaWeather` uses the forced `weather=` or `skyweather=` parms, otherwise the area's climate (`:224-242`). `roomWeather` returns clear for void-climate rooms and for areas without a sky, and honours room exceptions (`:244-275`).
- **Timers.** Each effect is a countdown that fires when it reaches 1 (`(x--)==1`) and then resets. Defaults are multiples of 150 (`:181-221`); see Content.
- **Freeze-over.** In winter, when weather changes from cold to cold, outdoor water-surface rooms (freshwater rooms when it is a single room) get `Spell_IceSheet` with probability `freezeOverChance`, default 50 (`:700-738`, default at `:142`).
- **Disease.** On the disease timer each online player in the affected area (`isInHere`, `:816-820`) gets chances from a weather table (`:786-814`), modified by room climate (`:826-847`), tents (`:848-854`) and the save bonuses (`:855`). A cold roll checks covered spots (feet, torso, legs, `:860-878`) and may apply `Disease_Cold` or `Disease_Flu` (`:881-885`). Frostbite checks hands, head and feet (`:891-916`). Heat exhaustion needs level above 6 and a 10% gate (`:917-921`).
- **Storms.** Rumble messages every 37 ticks (`:930-1017`). Lightning in a thunderstorm room with a player summons lightning on a random inhabitant at half their level, minimum 1 (`:1059-1062`), via `Chant_SummonLightning`. Tornado uses `Chant_SummonTornado` (`:1127-1134`), hail `Chant_SummonHail` at the player's level (`:1217-1222`), gusts `Chant_WindGust` (`:1301-1305`).
- **Fire and dust.** Drought fire: at `droughtFireChance` (default 1%) per tick, a random dry outdoor room item made of cloth, leather, paper, vegetation or wood is set alight by a factory mob "the heat" and given `Burning` (`:1227-1272`). Dust storms apply `Skill_Dirt` to a random awake player in a dust room (`:1160-1193`).
- **Rust.** Every 30 ticks, a player outside a tent gets base chances of 3 (snow, sleet, blizzard, hail) or 4 (rain, thunderstorm), plus 3 on a water surface if not flying, 5 underwater and 2 in a wet climate (`:1324-1353`). Each unworn metal item rusts when `roll > ability*25` (`:1366-1369`). A non-metal item worn about the body protects the whole set (`:1371-1376`). Text: "<T-NAME> rusts." or "... rusts in the water." (`:1382`).
- **Ranged weapons.** A thrown or ranged attack at range r in windy, thunder, blizzard or dust rooms has a `10r` percent chance of going wide, governed by `windsheer` (`:416`, `:425`). The second roll only sends "You fail to aim" (`:437-453`).
- **Footing and boats.** In rain, snow, sleet and thunder walkers may slip (`:540-549`), and the roll must also beat `3*DEX+25` (`:557`). A slip applies `Skill_Trip` for 3 ticks (`:559-562`). Boats on water lose progress in bad wind (`:478-498`). Boat damage is a math expression over `{rooms, windLevel, wetLevel}` (`:362-375`), configured with `shipdmgpct` and `dmgship` (`:147-161`).
- **Visibility.** Fog blocks looking at exits ("It is too foggy to see that.", `:596`) and telling exits ("It is too foggy to tell.", `:605`). Blizzard, fog and dust apply `Spell_ObscureSelf` through `okMessage` (`:576-583`).

### Stock market (StockMarket)

**Data model.**
- `StockDef` (`com/planet_ink/coffee_mud/Behaviors/StockMarket.java:299-414`): `ID`, `name`, `area`, `version`, `totalShares` (default `Integer.MAX_VALUE`, `:305`), `deed` (a `GenCertificate`), `price` (default 100.0, `:307`), `manipulation` (`:308`), `bankruptUntil` (`:309`), `outstandingShares` (`:310`), `roomID`, `influences` (per category, an int array indexed by direction, `:312`, `:324-335`), `topOwner` (`:313`). The title ID is `name#version` (`:410-412`).
- `MarketConf` (`StockMarket.java:1034-1327`): one config per stock family. Defaults: `updateDays` 24 game days (`:1036`), `waitDaysAfterBankruptcy` 10 (`:1037`), `maxStocks` 10 (`:1038`), `nameMask` "Stock in @x1 of @x2" (`:1042`), `playerInfluence` off, `maxSharesPerStock` unlimited (`:1049`). A quest-name map `questMoves` (`:1051`, defaults at `:1059-1062`) and shopkeeper and area masks round it out.
- `InfluDir` is POSITIVE, NEGATIVE, VARIABLE (`:230-235`). `InfluCat` has 19 categories (`:267-287`).

**Persistence.** Stock definitions are area data, XML `<S ID NAME M A PRICE V U T ROOMID>` with `<I CAT TYP AMT/>` children (`:548-582`, read at `:596-731`). Holdings are player records in section `STOCKMARKET_STOCKS`, keyed by title ID, holding `<AMT>n</AMT>` (`:447-467`). Ticker codes are area data `CMKTDATA` (`:929-933`).

**Stock creation and sale.** Stocks are created lazily the first time a shopkeeper is seen (`getShopStock`, `:1136-1296`, reached from `TYP_LIFE` at `:1958-1968` and buy and sell at `:2206-2227`). `GroupBy` decides the grouping: `NOTHING` (one stock per shopkeeper or room, `:1189-1213`), `RACE` (`:1214-1235`), `SHOPTYPE` (one per deal type, `:1236-1269`) or `ALL` (`:1270-1290`). New stocks stop at `maxStocks` (`:1206-1207`). The stock broker sells certificates through a `ShopProvider` (`:88-214`). The shelf price is the current price (`:202-206`) and the quantity is `totalShares - outstanding` (`:196-198`). Bankrupt stocks are withheld (`:175`).

**Trading.** Certificates are `AutoBundler` items. A broker buy or sell moves holder records and outstanding shares by the bundle size (`:2240-2247`). Gifts (`TYP_GIVE`) move shares the same way (`:1947-1955`). A broker refuses certificates whose version is stale (`:1866-1867`).

**Influence sources.**

| Category | Source | Direction and amount | Code |
|---|---|---|---|
| WEATHER | every 150 ticks, if the area or a child has blizzard, drought, dust, hail, sleet or thunder | NEG +1 | `StockMarket.java:1489-1521` |
| LEVELING | a PC levels (only with `PLAYINFLU`) | POS, or NEG when `value == level-1` | `:2012-2039` |
| BIRTHS | `Event.BIRTHS` | POS +1 | `:2070-2104` |
| QUESTING | `Event.QUESTOR` | VARIABLE +1 | `:2100-2101` |
| CONQUEST | `Event.CONQUEREDAREAS` | NEG +1 | `:2106-2107` |
| SHOPPING | any sale or purchase at a stock's shopkeeper | POS +1; certificate trades VARIABLE +1 | `:2199-2248` |
| THIEVERY | malicious thief skill on a shopkeeper (`TYP_JUSTICE`) | NEG +1 | `:1971-1991` |
| ROBBERIES | `TYP_LEGALWARRANT` against a shopkeeper | NEG +1 | `:1993-2010` |
| BUILDING | a building skill creates a room | POS +1 | `:2120-2151` |
| GATHERING, CRAFTING | gathering or crafting produces an item the shop sells | VARIABLE +1 | `:2152-2196` |
| OFFICER_DEATHS, JUDGE_DEATHS | an officer (+1) or judge (+2) dies in the area | NEG +1 or +2 | `:2258-2289` |
| HOLDINGS_ | no owner is an archon | NEG +1 | `:1663-1671` |
| HOLDINGS | at least one non-archon owner | POS +1 | `:1672-1673` |
| ASTROLOGICAL_INFLUENCES | shopkeeper has AutoAwards | VARIABLE +1 | `:1628-1635`, `:1659-1660` |
| RACIAL_BOONS | racial award matches the shopkeeper mask | VARIABLE +1 | `:1661-1662` |
| AREA_EVENTS | quest start whose name is in `questMoves` | per entry direction | `:2041-2069` |
| OWNER_DISCOUNT | monopolist buys (cap 25) | NEG +1 | `:1019-1020` |

Default `questMoves`: positive for newyear, celebration, party, leadership; negative for revolt, crimewave, swindler, brutality, harsh winter, famine, drought (`:1059-1062`).

**Periodic update.** A config's `nextUpdate` starts at now plus `updateDays` (`:1530-1533`). When it is reached, the tick starts `processStockMarket` on a worker thread, guarded by a flag (`:1540-1546`). After each config, `nextUpdate` moves forward by `updateDays` (`:1807-1808`).

**The price roll.** For each stock, each category and direction with count `n` gets `rolls = ceil(sqrt(n))` (`:289-292`, `:1684`). Each roll is:

```
d   = rollPercentage()                                  # 1..100        (:1689)
idx = clamp(round((d + manipulation + 95) / 5), 0, 59)  # (:1690-1691)
v   = THE_TABLE[idx][dir]                               # (:1692-1693)
if 0 < v < 1:  dividend_multiplier += v / 100           # (:1694-1695)
elif v < 0:    price -= roll(1, -round(v), 0)           # (:1697-1698)
else:          price += roll(1, round(v), 0)            # (:1699-1700)
```

Influence counts are cleared afterwards (`:1704`). `THE_TABLE` is at `StockMarket.java:237-258`. Because `manipulation` is always 0 in practice (see Problems), `idx` falls in 19 to 39, which are the rows labelled 0 to 100 (label = 5*idx - 95):

| idx | label | POSITIVE | NEGATIVE | VARIABLE |
|---|---|---|---|---|
| 19 | 0 | -4 | 4 | 4 |
| 20 | 5 | -3 | -3 | 3 |
| 21-22 | 10, 15 | -2 | 2 | 2 |
| 23-24 | 20, 25 | -1 | -1 | 1 |
| 25 | 30 | 0 | 0 | -0.1 |
| 26 | 35 | 0 | 0 | 0.1 |
| 27-28 | 40, 45 | 0 | 0 | -0.1 |
| 29 | 50 | 0.2 | 0.1 | -0.2 |
| 30 | 55 | 0.2 | 0.1 | 0.2 |
| 31-34 | 60, 65, 70, 75 | 1 | 0 | -1 |
| 35-36 | 80, 85 | 2 | 1 | -2 |
| 37-38 | 90, 95 | 3 | -2 | -3 |
| 39 | 100 | 5 | 3 | -5 |

The table is not monotone: a NEGATIVE influence can raise a price at some rolls (for example at 0 and 100). Fractional entries pay dividends, so 0.2 means 0.2% of the price per share per roll.

**Holder effects.** Dividends are paid per share to each holder's local bank, at the new price (`:1753-1763`). When the new price is above 200 and a 20% roll succeeds (`:1764-1765`), the stock splits: shares double, price halves, and holder records double (`:1768-1779`). The guard `totalShares < Integer.MAX_VALUE/2` (`:1766`) means default unlimited stocks never split.

**Bankruptcy.** A price below 0 sets the price to 0, deletes all holder records (`:1719`), prices the deeds at 0 (`:1720-1735`), and sets `bankruptUntil` to now plus `waitDaysAfterBankruptcy` (`:1714-1716`). After the wait the stock returns at price 100 with `version` incremented (`:1646-1654`), which makes old certificates worthless (`:1866-1867`).

**Monopoly discount.** A holder with at least 85% of `totalShares` (`:1001`) gets a shelf adjuster for that shopkeeper's gold sales that removes taxes and depreciation (`:1014-1016`). Each such purchase adds one OWNER_DISCOUNT NEGATIVE count, up to 25 (`:1019-1020`), which then pushes the price at the next update. The top-holder list is rebuilt every 10 minutes (`:990`).

**Configuration.** `configure` builds configs from the parms (`:1434-1471`). With no parms it uses the STOCK preset (`:1438-1445`). `CONFIGS=` names properties files that override the parms (`:1451-1462`). Presets are in `ShortcutMarketTypes` (`:1349-1363`).

## Content worth mining

- **Stock market presets** (`StockMarket.java:1351-1356`):

| Preset | Grouping | Notes |
|---|---|---|
| STOCK | SHOPTYPE, not area-grouped | "Stock in @x1 of @x2"; no player influence |
| REGIONAL | ALL shops | player influence on |
| BOND | ALL | AREAMASK `-EFFECTS +Arrest +EFFECTS -Conquerable` (areas with an arrest effect that are not conquerable) |
| COMMODITY | SHOPTYPE, area-grouped | "Commodity: @x1 of @x2" |
| RACIAL | RACE, area-grouped | racial award influence |
| PLAYER | NOTHING | clan stocks; SHOPMASK `-NPC`, see Problems |

- **Weather timer defaults** (`WEATHER_TICK_DOWN`=150, 4 s per tick; `WeatherAffects.java:181-221`):

| Effect | Parm | Default ticks | Approximate time |
|---|---|---|---|
| Rumble messages | rumbleticks | 37 | 2.5 min |
| Weather description to players | botherticks | 50 | 3.3 min |
| Dust | dustticks | 50 | 3.3 min |
| Rust | rustticks | 30 | 2 min |
| Hail | hailticks | 150 | 10 min |
| Disease check | diseaseticks | 300 | 20 min |
| Gusts | gustticks | 300 | 20 min |
| Lightning | lightningticks | 1200 | 80 min |
| Tornado | tornadoticks | 2250 | 150 min |
| Drought fire | droughtfirechance | 1% per tick | about 7 min on average |

- **Disease base chances** (`WeatherAffects.java:786-814`), before save and room modifiers:

| Weather | Cold | Flu | Frostbite | Heat exhaustion |
|---|---|---|---|---|
| Blizzard, sleet, snow | 99 | 25 | 15 | |
| Hail | 50 | | 10 | |
| Thunderstorm, rain | 25 | | | |
| Winter cold | 75 | 10 | 5 | |
| Heat wave | | | | 15 |
| Drought | | | | 20 |

- **Taxi fare:** base 10 plus 1 per trail room by default (`TaxiBehavior.java:225-226`, `Concierge.java:347-352`).
- **Vagrant:** awake 1d30 ticks, asleep 1d10 ticks (`Vagrant.java:92`, `:99`). Lying-down line: "curl(s) on the ground and go(es) to sleep."
- **Thief abilities** named by the behaviour: Thief_Mug, Thief_Steal, Thief_Swipe (`Thiefness.java:112`).
- **Sounder keywords:** the grammar listed under Sounder above, with `_ROOM` variants (`Sounder.java:120-334`).
- **Ticker codes** (`StockMarket.java:848-935`): take the first letters of word pairs of the name; if taken, use letter pairs of the first word; if still taken, append characters from `A-Z0-9a-z` to the first character. Stored per area so codes stay stable.
- **Weather flavour** (short): "A thunderous rumble and CRACK of lightning can be heard." (`WeatherAffects.java:959`), "The rain is soaking you!" (`:986`), "Snowflakes fall lightly on you." (`:974`), "You shiver in the cold." (`:1012`).
- **Taxman lines:** "Very good.  Your taxes are paid in full." (`TaxCollector.java:255`); "Very good, but you still owe @x1." (`:261`).

## Notable design ideas

- **Events become market pressure.** Players do not set prices. Existing game events add influence counts, so the economy reacts to play without new code paths (`StockMarket.java:1958-2287`).
- **Square-root rolls.** `ceil(sqrt(n))` dice (`:289-292`) give diminishing returns: 1 event is one roll, 4 events two, 9 events three.
- **Table-driven outcomes.** One d100 indexes a 60-row, three-column table (`:237-258`), so the economic model is data a designer can tune. `manipulation` is the built-in hook for bias.
- **Certificates as items.** Shares are `GenCertificate` items carrying a title ID and a bundle size (`:178-194`, `:1902-1905`). They are sold, given and bundled through normal item and shop code.
- **Taxes as a GIVE.** Payment is a coin transfer. The collector reacts through the message veto and trailer flow (`TaxCollector.java:132-298`), and fines go to the law system (`:111`, `:183`, `:252`).
- **One emote grammar.** Sounder covers about twenty actions, each with a self and a room variant, a percentage and substitution tokens (`Sounder.java:81-337`).
- **Weather as cause and effect.** Weather feeds disease, rust, slips, sound cues, stock influence (`StockMarket.java:1489-1521`) and summoned spells (`WeatherAffects.java:1055-1305`).
- **Lazy stock creation.** Stocks appear the first time a shopkeeper is seen (`StockMarket.java:1136-1296`), so builders do not list them.
- **Scripts on demand.** `ScriptableEverymob` copies its script to monsters the first time they are seen (`ScriptableEverymob.java:133-137`).

## Problems and cruft

1. **Scavenger filter precedence.** `Scavenger.java:192-198` reads `(... && (mask==null)) || maskCheck(mask, thisItem, false)`. `maskCheck` returns true for a null mask (`com/planet_ink/coffee_mud/Libraries/MUDZapper.java:5698-5703`), so with no mask every item is added, including containers, corpses, fixtures and null entries. The gettable, visibility, container and `DeadBody` checks are bypassed. The guard at `Scavenger.java:203` only hides the null case.
2. **Disease chances accumulate across players.** `coldChance`, `fluChance`, `frostBiteChance` and `heatExhaustionChance` are declared once (`WeatherAffects.java:782-786`) and changed inside the per-player loop (`:816-854`): `+=10` for cold rooms, `/=2` for tents. A change persists to every later player in the same tick, so one player in a tent halves the chance for everyone after them.
3. **Ranged-miss message does not veto.** The second roll at `WeatherAffects.java:437-453` sends "You fail to aim" and does not return false, so the message can appear while the shot still lands.
4. **Wimpy-aggressive mask.** `WimpyAggressive.java:94` tests the mask on the attacker (`observer`). `Aggressive.pickAFight` tests it on the target (`Aggressive.java:308`).
5. **Bribe wait value.** `Aggressive.java:169` reads `bribeParts.get(1)` (the ticks field) for the wait, not `get(2)`. The `20000` default applies only with fewer than three parts. This affects VeryAggressive and WimpyAggressive too.
6. **Tax collector null and sign.** `TaxCollector.java:167` calls `paid.containsFirst` without a null check, while `okMessage` checks at `:281`. `paid` is nulled by `setParms` (`:76`) and only recreated in `tick`. The pro-rata loop (`:230-241`) subtracts from every owned property, including those with no back taxes, so a property can go negative as credit. The setters do not clamp (`com/planet_ink/coffee_mud/Abilities/Properties/Prop_AreaForSale.java:161`).
7. **Boardables never sweep.** `WaterCurrents.java:154` requires `!isMobile(I)`, and `com/planet_ink/coffee_mud/Libraries/Sense.java:1815-1816` returns true for any `Boardable`. The Boardable branch (`WaterCurrents.java:215-223`) is therefore effectively dead, and so is the boat path for true vehicles.
8. **Stock market options that do nothing.**
   - `allowsClans` is parsed (`StockMarket.java:1078`) and only copied, compared and hashed. The documented clan option has no effect (`resources/help/arc_behaviors.ini`, lines 2193-2230 describe it).
   - `manipulation` is only set from XML (`StockMarket.java:618`, `:629`). Nothing writes it, so the table rows below label 0 are unreachable (`:1690`).
   - The split guard (`:1766`) means default stocks never split.
   - "Definitely still on top" compares against `totalShares/2` (`:482`) rather than outstanding shares, so for unlimited stocks it is always false and the top owner is recomputed.
   - `getStockOwners` deletes zero-balance records as a side effect of reading (`:538-541`).
9. **PLAYER preset contradiction.** The preset sets `SHOPMASK="-NPC"` (`:1356`). The MUDZapper help reads that as "Disallows mobs/npcs" (`MUDZapper.java:2734-2735`), so shopkeepers must be PCs. `getShopStock` rejects `isPlayer()` mobs (`StockMarket.java:1161`), and `isPlayer()` is `playerStats != null` (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:4415-4418`). I did not trace the NPC mask evaluation, so this is probably contradictory but unverified at runtime.
10. **Sounder flag and null risk.** `oncePerRound1` is never set to true (`Sounder.java:59`, `:431`, `:484`), so the weapon-attack guard is always open. `triggers` is null until `setParms` runs, yet `tick` and `executeMsg` read `triggers.length` (`:423`, `:502`) with no check.
11. **Scriptable.** `dequeResponses` passes `null` to the engine and ignores its argument (`Scriptable.java:262-265`).
12. **TaxiBehavior.** `defaultRiders` is captured on first `getTalker` and never reset (`TaxiBehavior.java:103-106`; `resetDefaults` at `:215-227` does not clear it), so it goes stale after the first ride. The arrival line has a doubled full stop ("Best of luck!.", `:117`, `:119`).
13. **Scavenger ATNIGHT.** The second PUT or DROP block (`Scavenger.java:142-148`) repeats the first under a comment about money that is not implemented.
14. **Wimpy "very" cost.** `Wimpy.java:130-132` may call each mobility behaviour's `tick` up to 100 times per Wimpy tick.
15. **Tax period text.** The "for this period" phrase uses `graceTime` (`TaxCollector.java:404`), which is the payment window, not a tax period.
16. **Stub ability.** `AWaterCurrent` implements the full `Ability` interface (`WaterCurrents.java:278-920`) only to serve as a message tool.
17. **Multiplayer assumptions.** The market assumes PC owners, archons and clan holders (`StockMarket.java:1663-1673`). Weather loops over all online sessions on each timer (`WeatherAffects.java:816`, `:933`, `:1024`, `:1096`). Taxes honour clan roles (`TaxCollector.java:85`, `:200`).

## Takeaways for a Python SUD

- **Behaviour components.** Port the pattern as small objects with `tick(entity, tick_id)`, `ok_message(host, msg) -> bool` and `execute_message(host, msg)`. Keep the no-op defaults in a base class. A single-player game needs few message types, so an enum and a dispatch table are enough.
- **Timed firing.** A helper equivalent to `ActiveTicker.canAct`:

```python
class Ticker:
    def __init__(self, lo, hi, chance=100):
        self.lo, self.hi, self.chance = lo, hi, chance
        self.left = self.redraw()

    def redraw(self):
        return random.randint(self.lo, self.hi)

    def ready(self, rng):
        self.left -= 1
        if self.left >= 1:
            return False
        self.redraw()
        return rng.randint(1, 100) <= self.chance
```

- **Market mechanic, not plumbing.** Keep `Stock` with `price`, `influences: dict[category] -> [pos, neg, var]` and a periodic `update()`. Keep `rolls = ceil(sqrt(n))` and the index mapping, with the table in YAML or JSON. Java's `Math.round` rounds halves up, so use `math.floor(x + 0.5)` in Python rather than `round`, which rounds to even. Drop certificates, bundling, bank deposits, archons and clan rules. For one player the market can be driven by the world alone (weather, wars, the player's own deeds) with no ownership records.
- **Fix known bugs in a port.** Keep per-player disease variables local (Problems 2). Test masks against the target (Problems 4). Parenthesise candidate filters (Problems 1).
- **Weather timers.** A list of `(every_ticks, effect)` pairs, with the defaults as data such as `{"disease": 300, "rust": 30, "lightning": 1200}` and the 150-tick base as a named constant. Disease chances live in a table keyed by weather.
- **Taxes.** `tax_due(player)` (fines, back taxes, citizen tax) and `pay(player, amount)` (fines first, then back taxes, then the remainder) form a clear, testable rule. Drop the monthly title scan.
- **Ferry or taxi.** A fare of base plus per-step, and a route the traveller walks one step at a time, is a good transit model. Replace the sense-bit trick with a flag on the traveller.
- **Sounder.** Emotes as data, for example `{"on": "wear", "scope": "room", "text": "$n adjusts $s hat."}` or `{"every": 23, "chance": 20, "text": "..."}`, using the same `$n`, `$s`, `$e` tokens. Cheap to port, and writers can edit it.
- **Mob behaviours.** Scavenger (pick up; dump at a bin above 90% carrying capacity), Wimpy (flee when a matching enemy engages), Vagrant (sleep 1 to 10 ticks, block moves while asleep) and TargetPlayer (hit the weakest in the group) are each a few dozen lines in Python.
- **Drop.** The `Scriptable` and `ScriptableEverymob` wrappers (scripting is a separate design question), the group and clan code, the stub ability class, and Java-style zapper masks (use plain predicates).

## Coverage

- **Read in full (all 16 bucket files):** `StockMarket.java` (2294), `WeatherAffects.java` (1393), `WaterCurrents.java` (921), `Sounder.java` (543), `TaxCollector.java` (455), `StdBehavior.java` (410), `ScriptableEverymob.java` (290), `Scriptable.java` (278), `TaxiBehavior.java` (255), `Scavenger.java` (211), `VeryAggressive.java` (183), `Wimpy.java` (145), `Thiefness.java` (138), `WimpyAggressive.java` (128), `Vagrant.java` (110), `TargetPlayer.java` (107).
- **Read for interactions (outside the bucket):** `ActiveTicker.java` (full class), `Aggressive.java` (full class), `PuddleMaker.java` (lines 178-249), `Concierge.java` (lines 338-361), `CombatAbilities.java` (lines 40-370 only), `Prop_Adjuster.java` (most of it), `resources/help/arc_behaviors.ini` (lines 2193-2230, the StockMarket help).
- **Checked by grep or small reads:** `Libraries/Dice.java`, `Libraries/Sense.java` (`isMobile`), `Libraries/MUDZapper.java` (`maskCheck`, NPC keyword), `Common/interfaces/CMMsg.java` (type codes), `core/CMClass.java` (`getMsg` overloads), `Common/interfaces/Climate.java`, `core/CMProps.java` (TIME_TICK), `Common/interfaces/PhyStats.java` (CAN_NOT_TRACK), `Abilities/Properties/Prop_HaveAdjuster.java` (sense term), `Abilities/Properties/Prop_AreaForSale.java`, `Abilities/Properties/Prop_RoomForSale.java` and `Items/Basic/StdTitle.java` (`setBackTaxes`), `MOBS/StdMOB.java` (`isPlayer`, `isMonster`), and the existence of the Thief, Chant, Skill and Spell classes the bucket names.
- **Not traced:** the bodies of the spells and abilities these behaviours invoke, `LegalBehavior` accuse and fine logic, `ShoppingLibrary` and `CoffeeShop` internals, tracking walk details, `Concierge` dialogue code, and the rest of `CombatAbilities`.
