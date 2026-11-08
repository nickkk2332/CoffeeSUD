# The MOB model: StdMOB, the MOB interface and generic NPCs

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b173` (Mobs: the MOB model, StdMOB and generic NPC classes) · 7 files · 9476 lines of Java.

## Summary

Every creature in CoffeeMud, player or NPC, is a MOB, and `StdMOB` is the one implementation they all share. It keeps layered stats (base values recomputed into effective values from race, class, room, gear and effects), carried and worn items, abilities, temporary effects, behaviours, scripts, factions, followers, a combat target and an action-point command queue. Its tick drives NPC death and respawn, natural recovery, action accrual, fatigue and ageing before it ticks effects, behaviours and scripts. Every action is a message that is offered for veto (`okMessage`) and then applied (`executeMsg`). `GenMob` makes NPCs into data: the whole NPC is an XML string that is parsed at creation and at every respawn, and `GenShopkeeper` does the same for shops (stock, budget, restocking, price prejudice), while the price arithmetic lives in `CoffeeShops`. `Deity` and `Banker` are interfaces only in this bucket; their implementations are outside it.

## Key files and types

| File (under `com/planet_ink/coffee_mud/`) | Role | Lines |
|---|---|---|
| `MOBS/StdMOB.java` | Base implementation of every MOB: stat layers, containers, tick, message handling, combat state, followers, death, respawn, removal | 6287 |
| `MOBS/interfaces/MOB.java` | The MOB contract; `Attrib` enum of 33 flags (1055-1136); `AGE_MILLIS_THRESHOLD` (47); sheath constants (1142, 1148) | 1149 |
| `MOBS/GenMob.java` | Data-driven generic NPC: `isGeneric()` is true (59-63); state lives in `miscText` as XML | 121 |
| `MOBS/StdShopKeeper.java` | Shop behaviour for VALUE, SELL, BUY, VIEW, LIST and GIVE; budget and restock countdowns; pricing hooks | 655 |
| `MOBS/GenShopkeeper.java` | Data-driven shop: adds nine stat codes on top of the GenMob set (105-107) | 249 |
| `MOBS/interfaces/Banker.java` | Interface for bank chains: accounts keyed by player or clan, deposits, interest | 248 |
| `MOBS/interfaces/Deity.java` | Interface for gods: worship and cleric requirements, rituals, blessings, curses, power-ups | 767 |

Code read to explain the mechanics but outside this bucket: `Libraries/CoffeeMaker.java` (GenMob XML, `resetGenMOB`, shop stock), `Libraries/CoffeeShops.java` (prices and prejudice), `Libraries/CoffeeLevels.java` (NPC hit points and experience), `Libraries/CoffeeUtensils.java` (variable equipment), `Libraries/interfaces/GenericBuilder.java` (field enums), `Libraries/Dice.java` (`rollHP`), `core/CMProps.java` with `coffeemud.ini` (constants and formulas), `Common/interfaces/CharState.java` (fatigue constants).

Citation convention: `MOBS/StdMOB.java:4007` means `com/planet_ink/coffee_mud/MOBS/StdMOB.java`, line 4007.

## How it works

### 1. Three stat layers

Each MOB keeps base and effective copies of its physical stats, character stats and state. Base values describe what the creature is. Effective values are rebuilt from base by asking each contributor to adjust a copy.

| Layer | Base object | Effective object | Rebuild method |
|---|---|---|---|
| Physical: level, ability, speed, weight, armour, damage, disposition, senses, rejuv | `basePhyStats` (StdMOB.java:69) | `phyStats` (68) | `recoverPhyStats()` (798) |
| Character: race, classes, STR/DEX/..., deity, worship | `baseCharStats` (65) | `charStats` (66) | `recoverCharStats()` (926) |
| State: hit points, mana, movement, hunger, thirst, fatigue | `baseState` (112) | `maxState` (111), then `curState` (110) which damage and ticks change | `recoverMaxState()` (1047); `resetToMaxState()` (1039) copies max into current |

Rebuild order as coded:

- `recoverPhyStats` (798-840): copy base into effective; scale speed above 1.0 by `speedAdj` (801-806, default `BONUSACT` 1.0); add `STAT_WEIGHTADJ`; room (809); money weight of `money / 100` (811-812); riding (813); deity (816); each class, then race (819-825); each item, then its effects (826); effects (827); factions (828-829); a light-source exception for followers, with the source comment "BUT WHY? This is WEIRD!" (830-839).
- `recoverCharStats` (926-957): base; riding; deity; classes; race (929-941); racial ageing (942); effects (943); items (944); room (945-946); factions (947-948); a charisma cut scaled by hygiene (949-956).
- `recoverMaxState` (1047-1063): base to max; race (1050); riding; classes (1055-1057); effects (1058); items (1059); factions (1060-1061); room (1062-1063).

Rebuilds are eager. `addItem` (4497), `delItem` (4511), `setVictim` (1649-1651, 1682-1684), and the retreat and advance handlers all call the rebuild directly. There are no dirty flags. The `ApplyAffect*` helper objects (149-154) are allocated once per instance and reused as visitors (191-242).

Construction defaults (161-174): race Human, falling back to `StdRace`; level 1; ability `CMProps.getMobHPBase()`, which is 11 by default (`core/CMProps.java:309`). The attribute bitmap starts with `NOTEACH` set (99).

### 2. Identity, presence and session

- Names: `_name` (63) is the stored name. `name()` returns the physical-stat `newName` when one is set (519-525). `name(viewer)` shows only the generic race and age when `INTRODUCTIONSYSTEM` is on and the viewer has not been introduced (1845-1856).
- `displayText(viewer)` (1859-1983) builds the room line from live state: the mob's name plus "fighting YOU" or "fighting someone" when in combat, and a "behind ..." list for formation followers.
- `isPlayer()` is `playerStats != null` (4415-4417). `isMonster()` is true when there is no session or the session is fake (4421-4424). Those two predicates decide most branches in the file.
- Possession: `Commands/Possess.java:194-200` moves the player's session onto the NPC body, sets the body's `soulMate` to the player's mob, and clears the player's session. `dispossess` (1079-1096) reverses it. A possessed body therefore has `isMonster()` false.

### 3. The tick

`tick(ticking, tickID)` (4007-4412) runs once per tick for `TICKID_MOB` and records its phase in `tickStatus` using the `Tickable` phase constants (`core/interfaces/Tickable.java`). The default tick is 4000 ms (see the table in section 3a).

**Dead branch** (4015-4076):

- An NPC whose base rejuv is positive, and whose physical rejuv is not `NO_REJUV`, counts its physical rejuv down by one each tick (4021-4023). When the counter goes below zero, or the server is shutting down, the respawn path runs (4024-4062):
  - if the start room or its area is gone, the mob is destroyed (4026-4036);
  - if the mob or its start room is "can not be camped" and players are in that room, rejuv is set to 0 and the check repeats next tick (4039-4047);
  - otherwise `cloneFix(CMClass.getMOBPrototype(ID()))` (4049) copies the prototype's state in, `bringToLife(startRoom, true)` runs (4050), and the room sees "<S-NAME> appears!" (4060).
- If base rejuv is 0, or the physical counter is `NO_REJUV`, the dead NPC is destroyed (4065-4071). Its corpse is a separate item and is not removed here.
- A dead player is not destroyed. `killMeDead` has already placed the player back at the death room (1741-1745).
- Effects, behaviours and scripts do not tick while dead, because this branch returns at 4075.

**Alive branch** (4077-4316), in order:

1. Every 60 ticks an NPC is checked with `CMLib.flags().validCheck` (4081-4103). A report starting with `*` kills the mob. Any other report destroys it.
2. On the first tick after a reset, `processVariableEquipment` runs (4115-4125; see section 9).
3. Action accrual, or turn-based combat. If the combat system is `TURNBASED` and the mob is fighting, `doTurnBasedCombat` runs and the tick returns (4129-4138). Otherwise, when no commands are queued, whole action points are discarded and the fraction kept (4142-4143), and then `speed` is added, or half of it when sitting (4144).
4. Breathing (4147-4194). A mob that cannot breathe takes `Math.round(Math.random() * (level + 2))` damage per tick (4153, 4188). The messages are "can't breathe", "drowning in" (liquid atmosphere) and "choking on" (gas).
5. Natural recovery every `RECOVERRATE * (REAL_TICK_ADJUST_FACTOR + STAT_RECOVERRATE5_ADJ)` ticks (4196-4200). `REAL_TICK_ADJUST_FACTOR` is 5 (`Common/interfaces/CharState.java:65`) and `RECOVERRATE` defaults to 1 (`coffeemud.ini:691`), which gives one recovery every 5 ticks.
6. In combat: the default combat system adds 1.0 bonus action (4207-4208); `combatStarted` (4210); `tickCombat` for automatic attacks (4211-4212); player round statistics (4213-4218). Out of combat: `peaceTime` grows by one tick (4222). If AUTODRAW is set and peace is between `START_SHEATH_TIME` and `END_SHEATH_TIME`, the weapon is sheathed (4223-4227).
7. Players only: fatigue (4236-4282), described in table 3a.
8. Command drain for NPCs (4283-4286); see section 4.
9. Players only: ageing (4291-4307). Each `AGE_MILLIS_THRESHOLD` of tick time calls `tickAging(this, 120000)`, which adds two minutes to `ageMinutes` (`Libraries/CommonMsgs.java:952-960`).

**Both branches** (4318-4408):

- Effects tick, and any effect whose tick returns false is removed with `unInvoke` (4319-4330).
- Mana consumption (4332). Ability component triggers (4334-4335).
- Behaviours (4337-4348), then scripts (4349-4360).
- Factions that need updating are refreshed from their faction (4362-4380), then each faction ticks (4383-4387).
- Each class ticks, then the race (4389-4395).
- Tattoos past their expiry are removed (4398-4408).

**3a. Time and tuning constants** (defaults; "tick" is `TICKTIME` = 4000 ms):

| Quantity | Default | Source |
|---|---|---|
| Tick length | 4000 ms | `core/CMProps.java:681`; `coffeemud.ini:2151` |
| Peace before sheathing starts | `3 * tick` = 12 s | `MOBS/interfaces/MOB.java:1142` |
| Peace before sheathing stops | `6 * tick` = 24 s | `MOB.java:1148` |
| NPC validity check | every 60 ticks (4 min) | `StdMOB.java:4083` |
| Natural recovery | every 5 ticks (`RECOVERRATE=1`) | `StdMOB.java:4199-4200`; `coffeemud.ini:691` |
| Age step for players | every 120000 ms (30 ticks) adds 2 minutes | `MOB.java:47`; `StdMOB.java:4291-4297` |
| Fatigue: "fatigued" | 3000 ticks awake (about 200 min) | `CharState.java:75` |
| Fatigue: "exhausted" | 30000 ticks; then a 1% chance per tick of falling asleep | `CharState.java:77`; `StdMOB.java:4268-4270` |
| Fatigue accrual | +1 tick of milliseconds per awake tick | `StdMOB.java:4247` |
| Sleep recovery | 200 ticks of fatigue removed per asleep tick | `CharState.java:71`; `StdMOB.java:4241-4242` |
| Respawn delay | the base rejuv value, in ticks | `StdMOB.java:4021-4024` |
| Action points gained | `speed` per tick (1.0 default); half while sitting | `Common/DefaultPhyStats.java:63`; `StdMOB.java:4144` |
| Default-combat bonus | +1.0 action per tick while fighting | `StdMOB.java:4207-4208` |
| Pacing of queued commands (sessions only) | `actionCost / speed * tick ms` | `StdMOB.java:2157-2160` |
| Progress callback for timed commands | once per 1000 ms | `StdMOB.java:2196-2197` |

Fatigue stays in milliseconds (4247). Once a mob is awake for 3000 ticks it is "fatigued". Sleeping removes fatigue 200 times faster than it accrues.

### 4. Actions and the command queue

- Currency: each tick a mob gains `speed` action points (4144). Command costs come from `Command.checkedActionsCost`, `Ability.checkedCastingCost` or `Social.checkedActionsCost`, and default to 1.0 (`calculateActionCost` 2291-2311).
- `prequeCommand` (2340-2371): a zero-cost command runs at once. Otherwise it goes to the front of the queue. `enqueCommand` (2413-2447): a zero-cost command runs at once unless `METAFLAG_INORDER` is set. Otherwise it goes to the back.
- `dequeCommand` (2142-2238) runs the head command once `actions() >= cost` (2154-2156) and deducts the cost (2162). For a mob with a session it also waits `cost / speed * tick ms` after the last command (2157-2160). An NPC has no session and skips that wait.
- Timed commands (for example, a spell being cast over several seconds) are called back once per second with the seconds elapsed (2196-2197, 2216, 2225). A cancellable command at the head of the queue is cancelled when a new command arrives (`checkCommandCancel`, 2314-2337).
- Orders: a `TYP_ORDER` message to an NPC enqueues the order text as a command with `METAFLAG_ORDER` (3734-3739). Followers obey orders by queueing them.
- Drain points: NPCs drain the queue in their own tick (4283-4286). The `else` at 4283 pairs with the fatigue `if` at 4236 (confirmed by brace depth), so NPCs drain there, and so does any mob for which the fatigue block is skipped. Players drain the queue in their session loop (`Common/DefaultSession.java:3686`).

### 5. Messages: veto, then apply

Every state change is a `CMMsg` with a source, a target, a tool and separate codes and text for each party. `okMessage` (2459-3373) offers the message to every listener, and any listener can veto it. `executeMsg` (3412-3979) then applies it to every listener.

**okMessage order.** Deity (2461-2463); classes and race (2465-2475); effects (2478-2483); items (2485-2490); behaviours (2492-2497); scripts (2499-2504); factions (2506-2513). Then the mob's own rules:

- Source checks (2516-3104). Sight, sound, hands, mouth and movement checks, with messages such as "Not while you are sleeping." (2568), "You can't see that!" (2578) and "You can't move!" (2729). Combat and range checks (3035-3102): "You are too far away from @x1 to use @x2." (3084) and "You are too close to @x1 to use @x2." (3091). A weapon attack out of reach becomes an advance (3041-3049). Actions blocked during combat, such as "Not while you are fighting!" (2840, 2880, 2900, 2940). Immortal staff cannot die (2523-2531).
- Target checks for malicious messages (3106-3352). "You like yourself too much." (3123). "You may not attack @x1." (3138) when `mayIFight` fails. "That is not EVEN a fair fight." (3161) when two players' levels differ by more than `CMProps.getPKillLevelDiff()` (3145-3160), unless PKILL, a clan war, or a law exception applies. The `checkSavingThrows` call (3183) also runs here.

**executeMsg order.** Deity, classes and race (3414-3424); behaviours (3426-3436); scripts (3437-3447). Then the target's special cases: healing, sniff, damage (`CMLib.combat().handleBeingDamaged`), teach and gravity (3458-3481). Then the source side (3484-3701): a malicious message starts combat (3489-3509); channel messages (3511-3525); retreat and advance adjust range by one (3546-3569); panic leads to flight (3533-3535); death is handed to the combat library (3589-3591); follow and nofollow (3633-3645). Then the target side (3703-3805) and the bystander side (3806-3946). Finally items (3949), effects (3961) and factions (3973).

`tell()` (3376-3381) writes to the session only. An NPC has no session, so its own feedback is silent and only the room sees its actions.

### 6. Combat state

- The target is `victim` (119) and the range is `atRange` (122, where -1 means none).
- `setVictim` (1630-1687) rebuilds both parties' stats. It refuses a target who is in another room, dead, or not an inhabitant, and clears both sides in that case (1654-1666).
- `isInCombat()` (1394-1417) checks lazily each time it is asked. If the target has left, died, or changed room, both sides are cleared (1400-1410).
- `isPermissableToFight` (1436-1485): a monster follower inherits its leader's permission (1442-1458). An unattackable target is refused unless already fighting (1460-1461). If either party is a monster the fight is allowed (1462-1463). Players may fight through soulmate, clan war or PLAYERKILL (1464-1484).
- Range model: `maxRange()` is the room's value, or 10 with no room (1557-1562). `minRange()` is 0 (1565-1568). `maxRangeWith(tool)` is the lower of the room's and the tool's (1570-1578). Retreat costs `pointsPerMove() * 5` movement (2849-2850) and is refused when range is already at the room maximum (2855-2858). Retreat and advance each move range by one (3546-3569).
- Wimpy: when HP falls below `getWimpHitPoint()`, with wimpy above zero and the mob in combat, `MUDFight` calls `postPanic` (`Libraries/MUDFight.java:2609-2612`). The panic message leads to `postFlee` (StdMOB.java:3533-3535).
- `makePeace` (1588-1606) clears the target and asks followers that are in combat to stop too. Player followers are included only when requested.
- `combatStarted` (3992-4004) resets peace time and bumps player combat counters.

### 7. Death, respawn, removal and possession

- `killMeDead` (1690-1749): a corpse is made from the race's corpse container (1704) and placed in the death room for monsters, or in the default body room for players (1697-1713). Then `makePeace`; every effect is unInvoked (1721-1726); location is cleared; monsters unlink their followers without killing them (1728-1740); players are brought to life at the default death room (1741-1745).
- Respawn copies state with `cloneFix` (608-758). For hand-coded classes it keeps the instance's level, ability and rejuv (620-628). For generic (data) mobs it copies everything from the prototype (631-634). `bringToLife` then re-reads the XML of generic mobs (1286-1300).
- `bringToLife(room, reset)` (1282-1391), in order: re-parse generic data; set start room; add to room; move the session to the area's thread; start the tick; run `autoInvocation` for each ability (1325-1331); update player factions unless the tattoo `SYSTEM_SUMMONED` is present (1335-1336); run one forced tick with movement blocked (1337-1353); recover room stats; for non-generic mobs, reset to max state (1358-1361); monsters receive a catalog drop item (1366-1371); players receive automatic properties (1373-1374); the room is told "MSG_BRINGTOLIFE" (1377); players look around (1383).
- `removeFromGame(preserveFollowers, killSession)` (1172-1235). On logout, monster followers are copied into the new world with rejuv set to `NO_REJUV` (1205-1218), and the followers are saved to the database (1222). This is how pets survive a player's logout.
- `destroy()` (1099-1169) tears down every container. If a session is still attached it blocks for one second (1119-1123).

### 8. Followers and groups

- Storage: `followers` is a `PairList<MOB, Short>` (138), each entry carrying an order. `followOrder` (139) is this mob's order in its leader's list. `amFollowing` (120) is the leader. `setFollowing` (4858-4873) keeps both ends consistent. `amFollowing()` (4846-4855) clears itself when the leader no longer lists it.
- Limits: `maxFollowers()` is `1 + (CHA - 6) / 3` by default (`coffeemud.ini:2518`, evaluated in `Libraries/CharCreation.java:5057-5070`). A `TYP_FOLLOW` is refused when `totalFollowers + srcM.totalFollowers >= maxFollowers` (3327-3332). Players also face the `FOLLOWLEVELDIFF` rule (3333-3349).
- `willFollowOrdersOf` (4791-4824): a mob obeys the leader it follows; a monster obeys anyone with ORDER security; the liege; the owner of the property it stands on; and clan authority over underlings and outranking roles.
- Group traversal: `getGroupLeader` (4827-4843) guards against cycles with a seen-set. `getGroupMembers` (4888-4904) walks both directions. `getRideBuddies` (4876-4885) does the same for riders.
- `isMine(mob)` is true for followers (6105-6119).
- `totalFollowers` (875-897) recurses without a cycle guard, and silently swallows exceptions (893-895).

### 9. Inventory, worn gear and money

- `inventory` (130) holds every carried item. `addItem` sets the owner and rebuilds the item's stats (4491-4499). `delItem` (4508-4512) removes the item.
- Worn gear: `fetchWornItems(wornCode, layer, attrs)` (5982-6011) filters by worn location and clothing layer. `getWearPositions` (5897-5968) counts slots from body parts in `Race.BODY_WEARGRID`. For example, the hands slot counts one position per pair of hands. The count is capped by `MAXWEARPERLOC` (`coffeemud.ini:677`, default 6). `freeWearPositions` (5881-5894) subtracts what is already worn. `MAXITEMSWORN` (`coffeemud.ini:669`) defaults to 0, which disables that cap.
- `fetchWieldedItem` (6041-6070) and `fetchHeldItem` (6073-6102) cache their result in a `WeakReference` and recheck it on each use.
- `moveItemTo(container)` (6138-6200) removes the item from its old owner, unwears it, adds it to the new owner, and then moves everything inside it (6166-6199).
- Money: an `int money` field (96) and a `moneyVariation` (97). For NPCs the field is a shortcut. `BeanCounter.getMoney` (`Libraries/BeanCounter.java:1678-1690`) falls back to coin items when the field is zero. On each reset, `processVariableEquipment` adds `Math.random() * variation` when the variation is positive, or subtracts it when negative (`Libraries/CoffeeUtensils.java:993-997`).
- Variable equipment: an item's rejuv value marks it as optional. `processVariableEquipment` (`Libraries/CoffeeUtensils.java:824`) runs on NPC reset. An item that is alone in its slot, or not worn, is destroyed when `rollPercentage() < rejuv` (893-896). Items that share a worn slot are narrowed to one, chosen with probability proportional to rejuv (920), and the others are destroyed.

### 10. Abilities, effects, behaviours, scripts, factions and tattoos

| Container | Field | Behaviour |
|---|---|---|
| Abilities | `abilitys` (131) | Innate skills, kept sorted by ID. Racial and clan abilities are merged at lookup (5017-5032, 5058-5080). |
| Effects | `affects` (135) | Temporary effects. `addEffect` refuses duplicate IDs (5264-5272). `addPriorityEffect` inserts at the front (5250-5261). Racial and clan effects are merged in `effects()` (5412-5415). |
| Behaviours | `behaviors` (136) | Started on add. Duplicate IDs are refused (5422-5430). |
| Scripts | `scripts` (141) | Duplicates by script text are refused (5620-5633). |
| Factions | `factions` (144) | `addFaction` clamps to the faction's minimum and maximum (5776-5794). `fetchFaction` returns `Integer.MAX_VALUE` for an unknown faction (5825-5831). |
| Tattoos | `tattoos` (137) | Named flags with optional expiry (5698-5772). Used as prejudice flags in shops (`Libraries/CoffeeShops.java:621`). |
| Expertises | `expertises` (134) | "code+number" strings (5537-5547). |
| Clans | `clans` (156) | One rank per clan (5183-5234). |

Ability usage costs are cached per ability ID. The cache is cleared when level, class or class level changes (5499-5534).

### 11. Attribute flags

`attributesBitmap` holds 33 flags (`MOB.java:1055-1136`). Each flag's bit is 2 to the power of its ordinal (1097-1106). Six flags are "reversed", meaning that setting them turns the behaviour off: AUTOASSIST, AUTOMELEE, AUTOFORWARD, DAILYMESSAGE, AUTOMAP and AUTOATTACK. Most flags are player preferences such as ANSI, BRIEF, SOUND and PRIVACY. For NPCs the relevant ones are NOTEACH (default on), NOBATTLESPAM, AUTODRAW and PLAYERKILL.

### 12. GenMob: an NPC is a text document

`GenMob` (`MOBS/GenMob.java:36`) extends `StdMOB` and reports itself as generic (59-63). Its data contract has three parts.

**Fields.** `GenericBuilder.GenMOBCode` (`Libraries/interfaces/GenericBuilder.java:105-131`) defines 24 fields, listed in table 12a. `getStat` maps a code to a read (`Libraries/CoffeeMaker.java:6053-6150`). `setStat` maps it to a write (6153). Lookup first tries an exact name and then a prefix match (6038-6050).

**Serialization.** `text()` (`GenMob.java:65-73`) regenerates the whole XML description, inventory included, and stores it in `miscText` on every call. A read is therefore also a write. `setMiscText` (76-80) stores the XML and calls `CoffeeMaker.resetGenMOB` (77-92). That method parses the XML onto the object, rebuilds physical and character stats, sets hit points, mana and movement from the level tables (85-87), recomputes max state and resets to it (88-89), sets wimpy to 10% of current hit points when wimpy is set (90-91), and sets experience to the chart value for the level below plus 500 (92).

**Reading the text back.** A stored string that starts with `%DBID>` is read from the database (`CoffeeMaker.java:57-72`). Otherwise the string must be longer than 10 characters to be used. Each respawn of a generic mob re-reads the XML (`StdMOB.java:1286-1300`) and re-rolls hit points. The formula is `FORMULA_NPCHITPOINTS` (`coffeemud.ini:2337`), which is `3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))`, where x1 is level and x2 is the ability value (11 by default). The `?` operator returns a uniform random value between its operands (`core/CMath.java:1796-1797`), so each reset gives a new value. The call chain is `CoffeeLevels.getLevelHitPoints` (80-89) then `Dice.rollHP` (80-88).

**Subclasses.** `Assassin` extends `GenMob` (`MOBS/Assassin.java:35`). It sets race, stats, thief skills at proficiency 100, gear and a combat behaviour in Java. The XML is applied only when `miscText` is non-null (`StdMOB.java:1286`), so the Java defaults hold until some XML has been written. `GenUndead` also extends `GenMob`. Random-area files spawn `GenMob` instances from templates, for example `<MOB class="GenMob" like="prison_prisoner" level="$level_range" ...>` (`resources/randareas/prison.xml:204`).

**Table 12a. The GenMob field set** (`GenericBuilder.java:107-130`):

| Field | What it holds (from `CoffeeMaker.getGenMobStat`, 6053-6150) |
|---|---|
| CLASS | Class ID |
| RACE | Base race ID |
| LEVEL | Base level |
| ABILITY | Base ability value, the hit-point modifier in the HP formula |
| NAME, DISPLAY, DESCRIPTION | Name, room display line, long description |
| MONEY | Total money, via `BeanCounter` |
| ALIGNMENT | Alignment faction value |
| DISPOSITION, SENSES | Base disposition and sense bits |
| ARMOR, DAMAGE, ATTACK, SPEED | Base physical stats |
| AFFBEHAV | Effects and behaviours, as XML |
| ABLES | Abilities, as XML |
| INVENTORY | Items, as XML, with item-ID and location tags removed |
| TATTS, EXPS | Tattoos and expertises, `;`-separated |
| IMG | Image name |
| FACTIONS | Faction listing |
| VARMONEY | Money variation |
| TAGS | Tags |

### 13. Shopkeepers

**StdShopKeeper** (`MOBS/StdShopKeeper.java:42`) holds:

- `shop` (50): the stock, as a `CoffeeShop`.
- `whatIsSoldMask` (51): which deal categories it buys and sells.
- `invResetRate` (52) and `invResetTickDown` (53): restock period and countdown.
- `budgetRemaining`, `budgetMax`, `budgetTickDown` and `budget` (54-55, 63): money available for buying from players, refilled on a period.
- `devalueRate` (57), `currency` (58), `pricingAdjustments` (59): item-mask multipliers, `itemZapperMask` (60): which items it deals in, and `viewTypes` (61): how much a view reveals (BASIC, FALSE or IDENTIFY, `core/interfaces/ShopKeeper.java:145-150`).
- Defaults in the constructor: alignment GOOD (71), weight 150 (73), intelligence 16 and charisma 25 (76-77), hit points 1000 (81).

**Deal masks.** There are 37 `DEAL_*` categories, 0 to 36 (`core/interfaces/ShopKeeper.java:45-117`), with names in `DEAL_DESCS` (129-143). `addSoldType` (100-131) keeps a single legacy code in the low byte. Otherwise each category is a bit at position `mask - 1` shifted left 8. `isSold` (90-97) reads the same bits. `DEAL_CONFLICTS` (120-126) lists mutually exclusive groups: postman and clan postman, banker and clan banker, the four land and ship sellers, and trainer and caster. Adding one member removes the others.

**okMessage** (286-347). VALUE and SELL pass `ignoreIfNecessary` and then `pawnEvaluation` against the budget. BID is refused with "I'm afraid my prices are firm." (304). BUY and VIEW pass `sellEvaluation`. LIST passes the ignore mask.

**executeMsg** (350-491). GIVE of an item the shop deals in, or any item if it is set to inventory-only, adds it to stock and says "Yes, I will now sell @x1." (367). VALUE quotes "I'll give you @x1 for @x2." (384). SELL pays out and reduces `budgetRemaining` (396). BUY takes stock off the shelf and calls the purchase helpers for items, mobs or abilities (438-460). VIEW describes the item (415-416). LIST composes the stock list (475-480).

**Ticks** (244-258). Only generic shops restock (249). The restock countdown (252-253) runs `doInventoryReset` (200-226), which empties the shelves and re-populates them from the XML in `miscText` (212-221). The budget countdown (254-255) runs `doBudgetReset` (228-241). The budget period is `ticks per mud hour * hours per period`.

**Prices** (`Libraries/CoffeeShops.java`):

- Prejudice (`prejudiceFactor` 644-669; `prejudiceValueFromPart` 601-641). Prejudice text has `BUY=` and `SELL=` clauses. Each clause is a number followed by customer flags, matched case-insensitively (631-635). The customer flags (618-630) are faction range codes, tattoo names, alignment, gender, class name, race, racial category and current class. The loop breaks on the first matching flag, so a number that follows the flags is never read.
- Item masks (`itemPriceFactor` 675). Each adjustment is a multiplier plus a zapper mask, applied when the mask matches.
- Sell price (`sellingPrice` 706-775). Base price (720-725), times prejudice and item adjustment (736-739), then a charisma and wisdom skew for monster shopkeepers only (745-753), then sales tax (757-762), then a minimum of 1 gold and rounding (763-767), then a half price for a magical-aura bonus on miscellaneous magic over 2 gold (769-772), then a best-price cap (773-774).
- The charisma and wisdom skew (752-753) is `goldPrice = 2g - g * ((CHA' - WIS') / ((CHA' + WIS') * 0.8))`, where CHA' is the buyer's charisma floored at 1 and WIS' is the shopkeeper's wisdom floored at 3. The factor is exactly 2 when the two floored values are equal.
- Pawn price, what the shop pays a customer (`pawningPrice` 1000-1103; formula at 1043-1059). `buyPrice = p/2 + (p/2) * ((CHA' - WIS') / ((CHA' + WIS') * 0.8))`, with CHA' as the customer's charisma floored at 1 and WIS' as the shop's wisdom floored at 3. The shop pays half price when the two are equal.

**GenShopkeeper** (`MOBS/GenShopkeeper.java`) adds nine codes to the GenMob set: WHATISELL, PREJUDICE, BUDGET, DEVALRATE, INVRESETRATE, IGNOREMASK, PRICEMASKS, ITEMMASK and SIVIEWTYPES (105-107). It overrides the prejudice field with its own storage (46, 65-74) instead of `miscText`. Stock is XML under `<STORE>`, one `<SHITEM>` per item, holding SICLASS, SITYPE, SISTOCK, SIPRICE and SIDATA (`Libraries/CoffeeMaker.java:3105-3150`). A missing or empty SIPRICE means the computed price is used (`stockPrice = -1`, 3144).

Generic shopkeepers in the tree: `GenBanker` (on `StdBanker`), `GenLibrarian` (on `StdLibrarian`), `GenPostman` (on `StdPostman`) and `GenCraftBroker` (on `StdCraftBroker`), all on `StdShopKeeper`. `GenDeity` sits on `StdDeity`, which extends `StdMOB`.

### 14. Banker and Deity (interfaces only)

- **Banker** (`MOBS/interfaces/Banker.java:50`) extends `ShopKeeper`. Accounts are keyed by player or clan name (`getBankClientName`, 150). Interest signs are documented: positive coin and item interest is a payment, and positive loan interest is a cost (64-106). `MIN_ITEM_BALANCE_DIVISOR = 10.0` (56) requires money of at least 10% of an item's value to keep it stored, per the comment. The implementation (`StdBanker`) is outside this bucket.
- **Deity** (`MOBS/interfaces/Deity.java:49`) extends `MOB`. Worship and cleric requirements are zapper masks (163-211). Rituals are trigger strings joined with `&` or `|` (340-443); service rituals allow only `&` (370-383). Blessings and curses are `Ability` objects with a cleric-only flag (229-324, 472-567). Clerics receive power-up abilities (670-766). The `HolyEvent` enum (127-136) and `RitualType` enum (145-153) name the events and rituals. `StdMOB` consults a deity in okMessage (2461-2463), executeMsg (3414-3416) and both rebuilds (816-818, 934-936).

## Content worth mining

**Shop deal categories** (`core/interfaces/ShopKeeper.java:45-117`). Usable as an enum for trade types in a Python SUD.

| Code | Category | Code | Category | Code | Category |
|---|---|---|---|---|---|
| 0 | ANYTHING | 13 | LAND | 26 | CLAN SHIP SELLER |
| 1 | GENERAL | 14 | ANY TECHNOLOGY | 27 | SLAVES |
| 2 | ARMOR | 15 | CLAN LAND | 28 | POSTMAN |
| 3 | MAGIC | 16 | FOODS | 29 | CLAN POSTMAN |
| 4 | WEAPONS | 17 | MEATS | 30 | AUCTIONEER |
| 5 | PETS | 18 | VEGETABLES | 31 | INSTRUMENTS |
| 6 | LEATHER | 19 | HIDES | 32 | BOOKS |
| 7 | INVENTORY ONLY | 20 | LUMBER | 33 | READABLES |
| 8 | TRAINER | 21 | METALS | 34 | CLOTHS |
| 9 | CASTER | 22 | ROCKS | 35 | CHILDREN |
| 10 | JEWELRY | 23 | CLAN BANKER | 36 | STOCKBROKER |
| 11 | POTIONS | 24 | INN KEEPER | | |
| 12 | BANKER | 25 | SHIP SELLER | | |

The SLAVES and CHILDREN entries are trade categories with no further detail in this code. Nothing in this bucket is worth copying from them.

**GenMob field set**: table 12a above. It is a complete NPC record in 24 fields, and that is the schema a Python NPC file would need.

**Tuning formulas** (`coffeemud.ini`, with the variables as documented there):

| Quantity | Formula (default) | Variables | Source |
|---|---|---|---|
| Max carry weight | `x1 + ((x2 + 10) * x2 * x1 / 150) + (x2 * 5)` | x1 base weight, x2 current STR | `coffeemud.ini:2504` |
| Max items carried | `(2 * x1) + (2 * x3) + (2 * x2)` | x1 wear codes, x2 level, x3 current DEX | `coffeemud.ini:2512` |
| Max followers | `1 + ((x2 - 6) / 3)` | x2 current CHA | `coffeemud.ini:2518` |
| NPC hit points | `3 + x1 + (x1 * ((x2/2) + (1?(x2/2))))` | x1 level, x2 ability (usually 11) | `coffeemud.ini:2337` |
| NPC experience on reset | chart value for level minus 1, plus 500 | level | `Libraries/CoffeeMaker.java:92` |
| NPC wimpy on reset | 10% of current hit points, when set | | `Libraries/CoffeeMaker.java:90-91` |
| Shop selling price factor (what the buyer pays) | `2 - (CHA' - WIS') / ((CHA' + WIS') * 0.8)` | see section 13 | `Libraries/CoffeeShops.java:752-753` |
| Shop pawn price factor (what the shop pays) | `0.5 + 0.5 * (CHA' - WIS') / ((CHA' + WIS') * 0.8)` | see section 13 | `Libraries/CoffeeShops.java:1058-1059` |

The formulas are text, compiled at boot (`Libraries/CharCreation.java:5386-5388`; `Libraries/Dice.java:45-52`). They can be changed without code. That is a useful pattern for a Python SUD: data-held formula strings, evaluated by a small expression evaluator.

**Example NPCs.** `Cat` (`MOBS/Cat.java:43-76`) is a hand-coded animal. It sets race Cat, intelligence 1, armour 90, damage 4, wimpy 2, and adds the Follower and MudChat behaviours. Its hit points are `roll(level, 20, level)`. `Assassin` (see section 12) shows the other pattern: a data-driven base with Java defaults for skills and gear.

**Attribute flags** (`MOB.java:1055-1136`). Notable NPC-related names: NOTEACH, NOBATTLESPAM, AUTODRAW, PLAYERKILL, AUTOATTACK.

**Player-facing messages** (short samples, paraphrased where long): "You are too far away from @x1 to use @x2." (`StdMOB.java:3084`); "Not while you are fighting!" (2840); "<S-NAME> fall(s) asleep from exhaustion!!" (4270); "<S-NAME> appears!" (4060); "<S-NAME> vanish(es) in a puff of smoke." (1184); "I'm afraid my prices are firm." (`StdShopKeeper.java:304`); "Yes, I will now sell @x1." (367).

**Prejudice syntax** (section 13). A small, readable rule language for price discrimination by race, class, faction standing and tattoos.

## Notable design ideas

- **Two-phase messages.** Every action is one object offered to all listeners for veto, then applied to all. Abilities, behaviours, items, rooms, factions and the mob itself all take part, and the mob's own rules (sight, sound, limbs, range, fairness) sit in the same pipeline.
- **Layered stats, rebuilt from base.** Each rebuild asks every contributor (room, race, class, deity, items, effects, factions) to add its adjustment to a copy. Nothing is stored as a running total, so removing a contributor is always correct.
- **One action economy for everyone.** Speed grants action points each tick, commands cost points, and both players and NPCs use the same queue. Commands that take time get a per-second callback with the elapsed seconds, and an incoming command can cancel a cancellable one.
- **Lazy target invalidation.** The combat target is re-checked each time it is read (1394-1417), so there is no separate event for "target left the room".
- **Respawn as clone plus re-read.** A dead NPC returns as a fresh copy of its prototype and, for data mobs, its XML is re-parsed, so each respawn gets fresh hit points and loadout. A rejuv counter sets the delay, and a camping guard holds the respawn while players are present.
- **Data as text.** A GenMob is an XML document that builders can edit in game. The `%DBID>` form lets the text live in the database and be reloaded from there.
- **Variable loadouts.** Items marked with a rejuv chance are rolled on each reset (`CoffeeUtensils.java:824-920`), so the same NPC can carry different gear each time it appears.
- **Composable shop pricing.** A base price is multiplied by prejudice, item masks, and a charisma and wisdom skew, with a separate pawn formula. The skew means a wise shopkeeper is harder to bargain with.
- **Wimpy as a message.** Fleeing is triggered by a damage message, not by a poll (`MUDFight.java:2609-2612`).
- **Followers as ordered records.** Each follower has an order number, and orders reach followers as queued commands, so an order is just a message with a different target.

## Problems and cruft

Each item below was verified by reading the cited lines unless marked as unverified.

1. **Side effects during the veto phase.** `okMessage` changes state while it is supposed to be a pure check. An immortal's death sets hit points to 1 (`StdMOB.java:2526`). A retreat spends movement and rebuilds stats (2863-2864). Nothing undoes these changes if another listener later vetoes the message, so the veto phase is not side-effect free.
2. **Early return skips notifications.** For a source-side channel message that carries channel flags on neither the others code nor the target code, `executeMsg` tells the source and returns (3511-3523). That skips item, effect and faction handling for the message (3949-3978). Whether a real message reaches this path is unverified.
3. **Queue drain is tied to an unrelated `if`.** The `else` that drains NPC command queues (4283-4286) is attached to the fatigue `if` (4236). It works, but the coupling looks accidental. The player path drains the queue in the session loop instead (`DefaultSession.java:3686`).
4. **Static stat-code caches ignore subclass.** `GenMob.getStatCodes` caches its list in a static field (98-106), and `GenShopkeeper` does the same (218-234). Whichever class asks first fixes the list for all of them. Extension codes come from `EXTVAR_` settings (`CMProps.java:3251`) and are read from the end of the array (`CMProps.java:3618-3630`). A mismatch in length misreads the wrong values. This is a latent bug (unverified in a running server).
5. **Enum copy-paste bug.** `GenMOBCode.getAllCodeNames()` sizes and fills its array from `GenItemCode.values()` (`Libraries/interfaces/GenericBuilder.java:138-139`). `MUDPercolator` uses that list as the MOB ignore list (`Libraries/MUDPercolator.java:73`). Mob-only codes such as RACE, MONEY and SPEED are therefore not on the list, and item-only names such as WEIGHT are on it. The effect on the percolator was not traced.
6. **Overloaded "rejuv".** For an NPC, rejuv is a respawn countdown in ticks (`StdMOB.java:4023`). For an item it is a percent chance of destruction when it is alone in its slot (`CoffeeUtensils.java:893`), and a relative weight within a slot group (920). `NO_REJUV` (`Common/interfaces/PhyStats.java:48`) and `ONE_JUV = -1` (50) are sentinels on the same field. The meanings differ by object type.
7. **Price comment disagrees with the formula.** The comment at `Libraries/CoffeeShops.java:744` says "the price is 200% at 0 charisma, and 100% at 35". The formula at 752-753 gives 200% when buyer charisma equals shopkeeper wisdom. With the wisdom floor of 3 it gives 262.5% at charisma 1 and about 95% at charisma 35. The comment and the code disagree.
8. **Doc says ticks, value is milliseconds.** `MOB.java:1138-1148` describes the sheath constants as "ticks", but the values are `3 * getTickMillis()` and `6 * getTickMillis()`, which are milliseconds. The peace counter is also in milliseconds (`StdMOB.java:4222-4226`), so the code is consistent and the comment is wrong.
9. **Doc overstates what `flagVariableEq` does.** `MOB.java:786-789` says it causes inventory to be "reconstructed from the database". The code (`StdMOB.java:251-254`, tick at 4115-4125) only schedules `processVariableEquipment`, which deletes and selects among existing items (`CoffeeUtensils.java:824-`). It creates nothing.
10. **Blocking sleep on teardown.** `destroy()` calls `CMLib.s_sleep(1000)` when a session is still attached (`StdMOB.java:1119-1123`). Blocking in a teardown path can stall a server thread.
11. **Silent exception handling.** `dequeCommand` catches every exception and returns false (2232-2235). `totalFollowers` does the same (893-895). `totalFollowers` also recurses without a cycle guard (875-897), while `getGroupLeader` does use one (4834-4841). Whether a cycle can form was not verified.
12. **No-op call.** `setSession` calls `setAttributesBitmap(getAttributesBitmap())` (1814), which changes nothing.
13. **Overloaded text field.** A non-generic `StdShopKeeper` stores its prejudice text in `miscText` (`StdShopKeeper.java:504`, 508-511). Generic shops store XML in the same field. The subclass decides which meaning applies. This is low severity.
14. **Getter with side effects.** `GenMob.text()` (65-73) rebuilds and stores XML on every read, including the inventory. It is slow and it changes the object as it reads it.
15. **Multiplayer-only branches.** Much of the file exists for other humans: PvP fairness, clan wars, the camping guard, possession, logout copies of followers, per-session command pacing, GMCP, the introduction system and session threads. None of that applies to a single-player game.

## Takeaways for a Python SUD

- **Stats as a pure function.** Keep a base record per creature and compute effective values on demand from a fixed list of contributors, in the order `recoverPhyStats` uses (798-840): base, room, race, class, deity, items, effects, factions. Do not store running totals. The eager rebuilds in StdMOB are the expensive and easy-to-forget part.
- **Two-phase events, with a pure check.** Model each action as one event with source, target, tool and kind, and dispatch it in two phases. The check phase must not mutate anything. The apply phase does all the mutation. Problem 1 above is what happens when that rule is broken.

  ```python
  def dispatch(ev, listeners):
      # check phase: pure, may veto (cf. StdMOB.okMessage)
      if not all(l.allows(ev) for l in listeners):
          return False
      # apply phase: the only place state changes (cf. StdMOB.executeMsg)
      for l in listeners:
          l.apply(ev)
      return True
  ```

- **Choose the time model first.** The action-point economy (speed grants points, commands cost points, timed actions get a progress callback) maps well onto a turn-based or hybrid loop. Use game turns as the time unit, not wall-clock milliseconds. Drop the session pacing and the 1-second callback.
- **Respawn as a fresh instance.** Store NPC templates as data: name, description, level, a hit-point formula string, race, class, abilities, inventory with optional spawn chances, money with variation, wimpy fraction, behaviour tags and faction standing. An instance is a template plus state. Respawn creates a new instance after a delay counted in turns. Keep "respawn delay" and "item spawn chance" as separate, clearly named fields (problem 6).
- **Keep the good shop rules.** The 37 deal categories, with conflict groups, become an enum with a conflict table. The pricing chain of base price, times prejudice, times item adjustment, times an optional charisma and wisdom skew, is simple to port. Restock on a turn counter. Drop the clan banks, the pawn budget and the multi-currency handling unless the game needs them. The half-price pawn rule (factor 0.5 at equal charisma and wisdom) is worth keeping as-is.
- **Companions with an order.** Keep followers as a list of companions with an order number, with a cap computed from a formula string. Orders become queued commands. Drop the logout copy logic, since a single-player world simply keeps the companion in the world state.
- **Keep the combat state small.** A target reference with lazy validation (1394-1417), an integer range from 0 to the room maximum (1557-1578), and a wimpy threshold per NPC cover the mechanics. Drop the PvP fairness rules, clan wars and camping guard. A short list of "why won't this fight" messages is enough.
- **Keep the wear model.** Count wear slots from body parts (5897-5968) and cap them with a single number. A Python version can be a dictionary of slot name to capacity, with items checked against it.
- **Keep the fatigue meter.** The awake-accrual and sleep-recovery pair (3a) is a clear, tunable mechanic for a living world. Sleep clearing fatigue 200 times faster than it builds gives a clear sleep cycle.
- **Model NPC data as a schema, not a string.** GenMob's 24-field record (table 12a) is the right schema. Store it as YAML or a dataclass rather than XML in a string. Keep the idea of an optional override: a hand-written NPC class can supply defaults, and the data file can override any field.
- **Drop from the file.** The database id reference, the session and thread plumbing, GMCP, the PvP and clan logic, the introduction system, and possession (unless body-swapping is a design goal).

## Coverage

- **Read in full (all lines):** `MOBS/StdMOB.java` (1-6287), `MOBS/interfaces/MOB.java` (1-1149), `MOBS/interfaces/Deity.java` (1-767), `MOBS/StdShopKeeper.java` (1-655), `MOBS/GenShopkeeper.java` (1-249), `MOBS/interfaces/Banker.java` (1-248), `MOBS/GenMob.java` (1-121). Total 9476 of 9476 lines in the manifest.
- **Read in part, to explain the mechanics:** `Libraries/CoffeeMaker.java` (50-140, 214-240, 577-600, 3105-3150, 5525-5550, 6038-6200); `Libraries/CoffeeShops.java` (601-775, 991-1066); `Libraries/CoffeeLevels.java` (80-90, 1156-1170); `Libraries/CoffeeUtensils.java` (815-1000); `Libraries/Dice.java` (80-100); `Libraries/CommonMsgs.java` (952-975); `Libraries/MUDFight.java` (472, 2600-2625); `Libraries/CharCreation.java` (5015-5070, 5386-5388); `Libraries/BeanCounter.java` (1676-1690); `Libraries/MUDPercolator.java` (73, 1744-1760); `Libraries/interfaces/GenericBuilder.java` (enums 20-140); `Libraries/interfaces/CombatLibrary.java` (51-82); `core/interfaces/ShopKeeper.java` (45-166); `core/interfaces/Tickable.java` (constants); `core/CMath.java` (1790-1800); `core/CMProps.java` (681, 309, 3233-3251, 3503, 3618-3700); `Common/interfaces/CharState.java` (55-80); `Common/interfaces/PhyStats.java` (40-52); `Common/DefaultPhyStats.java` (63); `Common/DefaultSession.java` (3680-3690); `Commands/Possess.java` (186-226); `coffeemud.ini` (lines 291, 534, 669, 677, 691, 2151, 2337, 2504, 2512, 2518); `resources/randareas/prison.xml` (204); `guides/BuildersFAQ.html` (rejuv answer).
- **Sampled:** `MOBS/Cat.java` (whole file, 78 lines) and the class body of `MOBS/Assassin.java` (lines 35-110) as examples of hand-coded and data-based NPC classes.
- **Surveyed by index only (`cm.py` impl and kids queries):** the `StdMOB` subclasses in `MOBS/` (about 108 files, outside this bucket), `StdBanker`, `StdLibrarian`, `StdPostman`, `StdCraftBroker`, `StdAuctioneer`, `StdDeity`, `GenBanker`, `GenLibrarian`, `GenPostman`, `GenCraftBroker`, `GenDeity`, `GenUndead`.
- **Not read:** the implementations of `StdBanker` and `StdDeity`; `CoffeeShops` selling and buying helpers beyond the pricing functions; `CombatLibrary` and `MUDFight` internals (the tick calls `tickCombat`, `doTurnBasedCombat`, `recoverTick` and `expendEnergy` by name only); `Faction` and `Leveler` internals beyond the functions cited; the `Triggerer` and ability-component machinery; the `CMLib.flags().validCheck` routine.
