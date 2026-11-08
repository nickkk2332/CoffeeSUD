# Behaviors, part 4: NeutralAbilities to Sailor

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b105` (Behaviors [part 4/5: NeutralAbilities .. Sailor]) · 22 files · 7786 lines of Java.

## Summary

These 22 classes are CoffeeMud's ready-made NPC "behaviours": scripts attached to a mob, room, item or area that act on a timer or react to messages. They cover NPCs that use their own skills, a ship-crew AI that fights and sails vessels, a complete poker dealer (five- and seven-card stud, draw, straight poker), patrolling route-walkers, guards and protective citizens, gang and watch behaviours, random spawners for items, traps, mobs and quests, resource overrides for harvested material, weather-driven puddles, and periodic room resets. They matter because they show how the engine turns a parameter string on a behaviour into living-world activity, using one shared random-cadence clock (`ActiveTicker`) and the message stream (`okMessage` / `executeMsg`). For a single-player Python game the poker dealer, the random spawners and the route-walker are the most directly reusable; the ship, guard and gang logic are useful AI patterns once they are simplified.

## Key files and types

All paths are relative to `ref/mudlibraries/CoffeeMUD/`. Line counts are the file lengths. `ActiveTicker.java` is in another bucket; it is read here for context.

| file or class | role | lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Behaviors/PokerDealer.java` | Poker table as a state machine: antes, betting by dropping coins, draw and stud rounds, hand evaluator, showdown | 1758 |
| `com/planet_ink/coffee_mud/Behaviors/Sailor.java` | Ship-crew AI: combat course, siege weapons, grapples and boarding, captain selection, peacetime sailing | 938 |
| `com/planet_ink/coffee_mud/Behaviors/Patroller.java` | Walks a route written in the parameter string; opens doors, sneaks, returns to route when displaced | 662 |
| `com/planet_ink/coffee_mud/Behaviors/RandomItems.java` | Keeps a target number of random items in an area, room, mob, container or shop stock | 458 |
| `com/planet_ink/coffee_mud/Behaviors/RandomTraps.java` | Sets random traps on items, containers, doors and rooms | 435 |
| `com/planet_ink/coffee_mud/Behaviors/RandomMonsters.java` | Keeps a target number of random mobs in a room or area | 420 |
| `com/planet_ink/coffee_mud/Behaviors/RandomQuests.java` | Generates quests from an XML template in the background; keeps a target count | 324 |
| `com/planet_ink/coffee_mud/Behaviors/ResourceOverride.java` | Replaces the material a room or harvested corpse yields, with raw resources or crafted items | 317 |
| `com/planet_ink/coffee_mud/Behaviors/ProtectedCitizens.java` | Group version of the citizen behaviour: citizens cry for help and recruit guards | 344 |
| `com/planet_ink/coffee_mud/Behaviors/ProtectedCitizen.java` | Single-guard version of the same idea | 253 |
| `com/planet_ink/coffee_mud/Behaviors/PuddleMaker.java` | Turns weather changes into puddles and dust | 250 |
| `com/planet_ink/coffee_mud/Behaviors/ResetWhole.java` | Resets a room or area after a period with no player activity, or at a set hour | 225 |
| `com/planet_ink/coffee_mud/Behaviors/RandomTeleporter.java` | Teleports a mob to a random room, with optional arrival and departure messages | 215 |
| `com/planet_ink/coffee_mud/Behaviors/ROMGangMember.java` | Gang rivalry: starts fights with members of rival gangs | 159 |
| `com/planet_ink/coffee_mud/Behaviors/ROMPatrolman.java` | Town watchman: whistles, breaks up fights, attacks | 158 |
| `com/planet_ink/coffee_mud/Behaviors/RaceHelper.java` | Same-race bystander joins a fight against an attacker | 121 |
| `com/planet_ink/coffee_mud/Behaviors/PlayerHelper.java` | Monster joins to defend a player who is being attacked | 118 |
| `com/planet_ink/coffee_mud/Behaviors/ObjectGuardian.java` | Monster stops others from taking, dropping or throwing guarded items | 117 |
| `com/planet_ink/coffee_mud/Behaviors/QuestChat.java` | Chat keywords that record progress in a named quest (extends `MudChat`) | 97 |
| `com/planet_ink/coffee_mud/Behaviors/NoCombatAssist.java` | Turns on auto-assist for a mob at a steadily growing interval | 67 |
| `com/planet_ink/coffee_mud/Behaviors/NeutralAbilities.java` | Mob uses its own "indifferent" skills, on itself or on a masked inhabitant | 180 |
| `com/planet_ink/coffee_mud/Behaviors/NiceAbilities.java` | Mob uses its own "beneficial to others" skills on a non-hostile inhabitant | 170 |
| `com/planet_ink/coffee_mud/Behaviors/ActiveTicker.java` (context) | Random-cadence timer and chance gate used by most behaviours here | 166 |

## How it works

### The shared clock: ActiveTicker

Most behaviours in this bucket extend `ActiveTicker`, which gives them a random timer and a percentage chance.

- Defaults: `minTicks=10`, `maxTicks=30`, `chance=100`, `minGroup=1` (`ActiveTicker.java:49-52`). Parameters `min=`, `max=`, `chance=` and `mingrp=` override them (`:77-80`). Subclass constructors usually set their own, e.g. `NeutralAbilities.java:59` uses 10/20/100, `ProtectedCitizens.java:63-65` uses 1/3/99, and `Patroller.java:79` uses 5/10/100.
- `tickReset()` (`ActiveTicker.java:57-71`) draws `tickDown` uniformly from `[minTicks, maxTicks]`. With `mingrp` above 1, only every `minGroup`-th reset draws a random value and the others use `minTicks` (`:59-69`), so one random wait is spread over a run of short ones.
- `canAct()` (`:136-165`) counts `tickDown` down on area, mob, item-behaviour and room-behaviour ticks. When it falls below 1 it resets and returns false if the mob cannot act at all (`canActAtAll`, `:153`) or the server is shutting down. Otherwise it returns `canChance()`, which is `rollPercentage() <= chance` (`:128-134`).
- Timing: the default tick is 4000 ms (`core/CMProps.java:681`). A NeutralAbilities-style behaviour (10-20 ticks, 100%) therefore acts every 40-80 seconds. ProtectedCitizen (1-3 ticks, 99%) acts every 4-12 seconds.

### NeutralAbilities and NiceAbilities: NPCs that use their own skills

These two behaviours let a mob use whatever abilities it already owns, without any per-mob script. The filter is the ability's own classification.

- **NeutralAbilities.tick** (`NeutralAbilities.java:95-179`):
  - Activation: `aChance = mana / maxMana`. The tick returns early if `Math.random() > aChance` or `mana < 50` (`:105-107`). A mob at full mana acts most of the time; below 50 mana it never does.
  - It needs at least one player in the room (`:109`).
  - Target: the mob itself when no mask is set (`:112-113`); otherwise a random inhabitant matching the `;`-separated zapper mask (`:116-127`).
  - Candidate list (`:129-158`): rebuilt only when `mob.numAllAbilities()` changes. It keeps abilities with `abstractQuality()==QUALITY_INDIFFERENT` (`:150`), and skips prayers unless `appropriateToMyFactions` (`:151-152`). A `SKILLS=` parameter restricts the list instead (`:135-143`).
  - Gate: the mob must not already have the effect (`fetchEffect(ID)==null`, `:162`) and `castingQuality(mob,null)==QUALITY_INDIFFERENT` (`:163`).
  - It sets proficiency to `CMLib.ableMapper().getMaxProficiency` (`:165`) and invokes the ability. If the target is another mob and the ability can target mobs, the argument list carries `"$name$"` (`:166-170`); otherwise it invokes on the target directly (`:173`).
- **NiceAbilities.tick** (`NiceAbilities.java:87-168`) is the same skeleton with `QUALITY_BENEFICIAL_OTHERS` (`:145`, `:157`). It has no target-mask fallback; it picks a random inhabitant up to 10 tries (`:106-118`), rejecting itself and anyone whose group leader is a monster or is fighting this mob (`:113-116`). The list cache is rebuilt when `numAllAbilities` changes (`:137-152`).
- Design point: which spells an NPC casts is decided by metadata already on each ability (`abstractQuality`, `castingQuality`, `canTargetCode`). A new ability becomes NPC behaviour without new code.

### Sailor: ship-crew AI

`Sailor` is a `StdBehavior` placed on the crew of a `Boardable` vessel. It drives the ship through the same command queue a player would use (`mob.enqueCommand`, e.g. `Sailor.java:283`, `:527`, `:568`, `:610`, `:747`, `:881`).

- **Roles** are set by parameters (`Sailor.java:174-193`): `BOARDER`, `DEFENDER`, `AGGRO` (with `AGGROMOBS`, `AGGROLEVELCHECK`, `AGGROMASK`), `FIGHTMOVER` (default true), `FIGHTTECH` (default true), `PEACEMOVER` (default false), `WIMPY` (default true), `AREAONLY` (default true), plus `TICKDELAY` and `TICKBONUS`.
- **Ship identity**: on the first tick the crewman takes the ship from its room's area if that area is `Boardable` (`:426-430`). A crewman whose start room belongs to another area is cloned into the ship's room, the behaviour moved to the copy, and the original killed (`:438-453`). This is a re-homing step; the chapter's only example of an NPC replacing itself.
- **Cadence**: only on mob ticks (`:399-401`). The wait is `20 - TICKBONUS - level/4`, floored at 0 (`:407-409`), so a level 0-3 crewman with no bonus acts every 20 mob ticks, about 80 seconds at the default tick. A host that is not a mob gets 10000 instead (`:412`).
- **Combat decisions**, in order (first action taken wins; each returns after enqueueing commands):
  1. Boarders and defenders join the ship's fight: for each enemy in the room they can attack and that `grantsAggressivenessTo` allows (`:139-171`, which requires that one ship is the other's current combatant), they call `CMLib.combat().postAttack` (`:474-492`). An enemy above the `EXPRATE` level window is skipped (`:482`).
  2. Boarders with no fight leave the ship via `beMobile` (`:494-497`).
  3. Before manoeuvring, the anchor is raised (`RAISE ANCHOR` for water-based ships) or the brake released (`UNSET BRAKE` for land ships) (`:520-531`).
  4. Siege upkeep (`:540-662`), only when a target ship exists and the ship is `SiegableItem`:
     - Repair: if the ship wears and `rollPercentage() >= usesRemaining` and a second roll is under 50, call `tryMend` (`:544-548`).
     - Load: find an `AmmunitionWeapon` below capacity in the room, carry matching ammunition, or `GET` some from the room. After three misses it says "I can't seem to get any <ammo>." (`:549-589`, complaint at `:580-583`).
     - Aim: an unaimed, fully loaded weapon in range gets `AIM` with a random point `roll(1,targetSpeed,-1)` (`:593-612`).
     - Second fiddle: a crewman who finds another combat-tech crewman of the same ship earlier in the room yields (`:616-634`).
     - Move to an adjacent room that holds siege weapons (`:637-661`).
  5. Defenders indoors call `beMobile` (`:663-666`).
  6. Boarding (`:668-698`): `HOLD` and `THROW` any item whose ID ends in `Grapples` at the target ship, then `ENTER` grapple exits.
  7. Course (`:699-748`): speed is `roll(1, maxSpeed, 0)` (`:710`). If `amInTrouble()` (wimpy, and the enemy's hull is more than 33 points better, `:336-345`) and an escape heading exists (`getEscapeRoute`, `:360-384`), it flees (`:717-728`). Otherwise it steers at the target when more than 5 rooms away, or with 30% chance, else keeps its heading (`:733-739`).
- **Non-combat** (`:750-933`):
  - Hull under 100 uses: `tryMend` (`:752-756`). `tryTrawl` (`:757`) runs the trawling skill on water ships. `tryMend` picks `CaravanBuilding` (land) or `Shipwright` (water). It sets proficiency to `(level+1)*4*3`, capped at 99, when it is 0 or 100 (`:276-278`), then enqueues `CARAVANBUILD MEND` or `SHIPWRIGHT MEND` (`:283`, `:303`). It does nothing if a common-skill effect is already on the mob (`:270`).
  - After a fight: `COURSE`, lower the anchor or set the brake, then `GET` the grapples (`:758-775`).
  - Aggression on passing ships (`:777-885`): for each other boardable or water-rideable item in the room, it collects riders (players always; monsters only with `AGGROMOBS`) and crew. It picks a captain in this order: a player with weak privileges (`CMLib.law()`), else the crewman whose flags give the highest index in `Ms[0..4]` (combat-mover, peace-mover, combat-tech, boarder, defender; the last non-null wins, so a defender outranks a boarder), else a monster with weak privileges, else any player, else the first eligible. Then it enqueues `TARGET` (`:881`) if the level check and mask pass (`:878-880`).
  - Peacetime (`:886-932`): with `PEACEMOVER`, it builds a random course of up to 99 tries. The heading is kept with 70% chance and re-rolled at speed 1 with 30% chance; with `AREAONLY` it stays inside the ship's area. The course goes out as `COURSE`.
- Interactions: `NavigableItem`, `SiegableItem`, `AmmunitionWeapon`, `Ammunition`, `Boardable`, `Rideable`, `PrivateProperty` (ownership check, `:700-702`), `CMLib.combat()`, `CMLib.law()`, `CMLib.tracking()`.

### PokerDealer: a poker table as a state machine

`PokerDealer` (`PokerDealer.java:44`) can be hosted by a mob (the dealer), a room or an item. It is a `StdBehavior` that does its work in `okMessage`, `executeMsg` and `tick`.

- **States** (`:55-63`): `WAITING_FOR_ANTIS`, `DEALING`, `FIRST_BETTER`, `NEXT_BETTER`, `DONE_BETTING`, `FIRST_DRAW`, `NEXT_DRAW`, `DONE_DRAWING`. The flag `STATEMASK_NEED_ANOUNCEMENT` (256, `:68`) means "say the prompt on the next tick". `roundOfPlay` counts deals and draws (`:73`).
- **Timers** (wall clock, checked on each tick, `:80-90`): 20 s to start after the first ante, 10 s after the deal before betting, 30 s per bet, 30 s per draw. An expired bet is a default action: fold if the player cannot call, else pass or call (`:1696-1728`); an expired draw means stand pat (`:1689-1695`).
- **Game types** (`:114-123`): 0 straight poker, 1 five-card stud, 2 seven-card stud, 3 draw poker. Parameters: `GAME=`, `ANTI=` (default 1.0), `MINPLAYERS=` (default 2) (`:135-148`). A sysop can change the game by saying its name, and can run a `DETERMINEHANDTEST` that deals 10000 hands per size to the log (`:581-644`).
- **Money model**: the pot is an ordered `PairList<MOB,Double>` (`:166`). Its order is the betting order. Coins dropped in the room are the bets (`executeMsg` credits the pot at `:562-576`). The dealer rewrites drop messages with `CMMsg.modify` and adds change messages with `addTrailerMsg` (`okMessage`, `:332-479`). Dropping foreign currency or an ante below the required amount is refused with a message.
- **Antes** (`okMessage`, `:353-390`): the first drop must cover the ante. Excess becomes change. The 20-second clock starts here (`:355-356`).
- **Start** (`tick`, `:1645-1686`): fewer than `minPlayers` restarts the ante phase. Otherwise the deck is shuffled twice, each player gets a hand (`theDeck().addPlayerHand`), and the deal depends on the game: 5 down for straight and draw (`:1675`), 1 down plus 1 up then 2 up and 1 down for five-card stud (`:1678`), 2 down plus 1 up for seven-card stud (`:1681`).
- **Betting** (`executeMsg`, `:650-734`): the speaker must be the player whose turn it is (`whoseTurn`). The words are `PASS`/`PAS`, `CALL`/`CAL`, `FOLD`, `RAISE`/`RAIS`/`RASE` (`:175-184`). `nextPlayerNextState` (`:486-538`) moves the turn. A round ends when the pot is balanced and the turn returns to the first bettor (`:515-516`). Table stakes are enforced (`okMessage`, `:456-470`): a raiser may not bet another player out of the game.
- **Draw** (`executeMsg`, `:742-813`): the player names card positions ("1 3 5") to discard, or `PASS`/`STAND`/`STAND PAT`. They may not discard everything (`:795-796`). With one card left it must be an ace (`:798-799`). Discards go back to the deck and new cards are dealt (`:804-807`).
- **Stud reshuffle** (`tick`, `:1613-1638`): after each stud deal the highest face-up hand is moved to the front of the pot, so it starts the betting. Only face-up cards count (`determineAllSortedScores(true)`, `:1617`).
- **Showdown** (`announceWinners`, `:1263-1307`): the winner is the last entry of the ascending score list (`:1268`). Every other hand is announced. If the winner is in the room, all coins on the floor go to them (`:1285-1300`). With no winner, the pot list is cleared but the coins stay on the floor and go to the next winner (`:1306`).
- **Hand evaluation** (`determineHand`, `:821-1048`): one `int` score per hand. The category sits above bit 20 (`HAND_*`, `:190-200`):

  | category | constant | value |
  |---|---|---|
  | five of a kind | `HAND_5OFAKIND` | 11 |
  | royal flush | `HAND_ROYALFLUSH` | 10 |
  | straight flush | `HAND_STRAIGHTFLUSH` | 9 |
  | four of a kind | `HAND_4OFAKIND` | 8 |
  | full house | `HAND_FULLHOUSE` | 7 |
  | flush | `HAND_FLUSH` | 6 |
  | straight | `HAND_STRAIGHT` | 5 |
  | three of a kind | `HAND_3OFAKIND` | 4 |
  | two pair | `HAND_2PAIR` | 3 |
  | one pair | `HAND_1PAIR` | 2 |
  | high card | `HAND_HIGHCARD` | 1 |

  The tie-breakers are packed in 4-bit fields: value `<<15` for the main rank, value `<<11` for two-pair's second pair, suit `<<13` for flush, and kicker nibbles below. For example, a pair scores `HAND_1PAIR + (value<<15) + three kicker nibbles` (`:1037`).
- **Phrasing** (`describeHand`, `:1113-1246`): it builds lines such as "a Full House: Xs over Ys" and "Two Pair, Xs and Ys, with Z high" from the packed score.
- **Table talk**: a player who looks at the room while antes are open gets a greeting with the game name and ante (`:552-557`). A player who steps away is dropped with "Oops.. we lost <name>" (`:1429-1437`).

### Patroller: route-walking NPCs and vehicles

`Patroller` (`Patroller.java:39`) walks a route whose steps are in the behaviour's parameter string.

- **Route syntax** (`getSteps`, `:96-137`): the parameter text after the first `;` is a list of steps separated by `;`. A step is a compass direction, a room-ID suffix, `.` (pause one tick), or `RESTART`/`REPEAT` (loop to the start). If the route has no restart marker, the steps are mirrored: each direction gets its opposite appended, in reverse order (`:119-132`). A route of `N;E;S;W` therefore becomes a back-and-forth patrol, not a loop.
- **Movement** (`tick`, `:167-660`): skips if the mob is ridden or following (unless `rideok`, `:177-186`); an untrackable physical object moves on a 1% roll (`:188-194`); a ship in combat, out of usable hull, or falling does not move (`:196-206`). Doors are unlocked and opened if needed (`:596-612`); a mob then walks through with `Thief_Sneak` if it has it, and its proficiency is set to `roll(1,50,level*15)` when below 50 (`:622-637`), else `CMLib.tracking().walk` (`:644`). Items and ships use `thisRoom.showHappens`, `moveItemTo` or `ship.setCurrentCourse` (`:455-466`).
- **Correction**: if a step's room is missing, the patroller looks for a trail back to a route room with `findTrailToAnyRoom` (`:412`), within `diameter` (default 20, `:81`).
- **Administrators** present in a room stop it there (`:451-452`).

### Random spawners: RandomItems, RandomTraps, RandomMonsters

The three share one pattern. Parameters are parsed in `setParms` (text before `;` is the options, after `;` the data source). A target count is chosen once. The behaviour keeps a `maintained` list, prunes what no longer exists, and spawns only while below the target. The spawn loop is capped at 10000 attempts, and room choice at 100 tries.

- **Count**: `avg = CMLib.dice().roll(1, max-min, min)` (`RandomItems.java:170`, `RandomTraps.java:223`, `RandomMonsters.java:166`). Because `Dice.roll(n,die,mod)` is `n` dice of `1..die` plus `mod` (`Libraries/Dice.java:398-405`), the result is `min+1..max` whenever `max > min`, and `min` only when they are equal. See Problems.
- **Locale filters**: `+NAME` or `-NAME` against `Room.DOMAIN_INDOORS_DESCS` and `DOMAIN_OUTDOOR_DESCS`, and `+ALL` / `-ALL` (`RandomItems.java:110-158`, `RandomTraps.java:166-212`, `RandomMonsters.java:108-154`).
- **RandomItems** (`:319-457`): the data source is an XML file or an inline `<ITEMS>` string, cached through `Resources` (`:244-315`). Each spawned copy has `NO_REJUV` set and gets `text()` (`:361-364`). A shopkeeper gets the item in stock if it `doISellThis` (`:365-371`). A container or mob gets it directly (`:374-393`). A room or area picks a random room in its metro area (`:396-430`), gives the item to a random savable inhabitant if there is one, and, unless `MOBS` is set, also places it in the room (`:440-450`).
- **RandomTraps** (`:251-434`): picks random eligible physical objects (exits with locked or any doors, items, containers, and rooms only with `ROOMS=YES`). It then copies a random `Trap` ability that `canSetTrapOn` allows, sets proficiency 100, stores the area's `MED_ALIGNMENT` in the trap text (`:421-424`), makes it long-lasting and unsavable, and adds it (`:419-428`). The minimum is enforced regardless of the timer (`:267`).
- **RandomMonsters** (`:307-419`): copies a random XML mob, sets `NO_REJUV` and `setStartRoom(null)`, runs `text()`, and calls `bringToLife` in a random metro room (`:373-378`, `:407-410`). Air rooms require flight (`canFlyHere`, `:287-298`, `:393`). Monsters that die or lose their location are pruned (`:327-328`).

### RandomQuests: background quest generation

- `setParms` (`RandomQuests.java:89-113`) reads `PATH` (default `randareas/example.xml`), `MINQUESTS`/`MAXQUESTS`, `MAXATTEMPTS` (default 3), `EXPIRE` (default `6 hours`), `TAGID` (default `all_quests`) and `INLINE`. Every other parameter becomes a template variable, and `TEMPLATE` defaults to `normal`.
- Target count: `numQuests = min + roll(1, max-min+1, -1)` (`:280`), a uniform choice in `[min,max]`.
- `GenerateAQuest.run()` (`:164-265`) is a `Runnable` that loads the XML, builds defined IDs with `CMLib.percolator()`, checks requirements, builds the quest script, checks the quest ID is unique, adds the quest and starts it. It retries up to `maxAttempts`, and on the last failure disables the behaviour (`:243-253`).
- Concurrency gate (`:168-170`): `max = round(ceil(availableProcessors()/4))`. Generation runs off-thread through `CMLib.threads().executeRunnable` (`:299`) or inline (`:293-297`).

### ResourceOverride: changing what a room or corpse yields

- Parameters (`ResourceOverride.java:102-205`): resource names, `none`, room-type names, crafted items written as `name(material)` or `material name` (`:163-191`), ability IDs (restrict to these harvest skills), and `MOBMASK=` (who may trigger the override).
- Rooms and areas (`tick`, `:207-243`): the room's resource is set to a random configured resource if it is not already one (`:222-223`). For areas, every metro room of a matching room type is set (`:231-237`).
- Corpses: when a corpse is created (`TYP_BODYDROP`), the behaviour may be attached to the body with `canChance()` (`:248-252`). When a common skill then generates material from it, `okMessage` replaces the target: a crafted item with probability `crscs/(crscs+rscs)` (`:291`), otherwise a random configured raw resource (`:292-294`). The watcher is a single `(MOB, Ability)` pair (`:260-270`).

### ProtectedCitizens and ProtectedCitizen: "help me" NPCs

- `ProtectedCitizen` (`ProtectedCitizen.java:174-251`): in combat, the citizen yells one of its claims (default "Help! I'm being attacked!" or "Help me!!", `:52`; a claim starting with a comma becomes an emote, `:189-192`). It then recruits up to `maxassists` nearby, unengaged mobs matching the guard name or zapper (`getCityguardName`, `:84-125`), within `radius` (default 7). It attacks the victim or walks towards the guard's room. The `WANDER` flag lifts the same-area limit (`:199-201`).
- `ProtectedCitizens` (`ProtectedCitizens.java:186-315`) is the group version. It keeps an `assisters` map from citizen to recruited guards (`:58`) and refuses to recruit a guard already used by another citizen (`:286-293`).
- Parameter layout: `ProtectedCitizens` uses `citizens-mask;guard-mask;claim;claim...` (`getProtectedZapper` `:88-107`, `getCityguardZapper` `:109-135`, `getClaims` `:137-184`).

### PuddleMaker: weather as a game system

- Weather classes (`PuddleMaker.java:57-105`): cold-wet is blizzard, sleet, snow and hail; wet is rain and thunderstorm; dry is drought, dust storm and heat wave; cold adds winter cold.
- When the last weather was wet and the new weather is neither wet nor dry, each eligible outdoor room gets a puddle with `pct()` chance (default 50, `:112-118`, `:190-193`). The puddle is a `GenLiquidResource` holding 100 units of fresh water, not savable (`:131-138`). It is "some snow" if the previous weather was cold-wet, else "a puddle of water" (`:140-150`). A room that already has one is skipped (`:122-130`).
- After a dust storm ends, items lying in the open get a `Dusty` effect (`makeDusty`, `:156-177`).

### ResetWhole: periodic room reset

- `executeMsg` (`ResetWhole.java:105-121`) records the last time a non-monster acted in the room or its metro area.
- `tick` (`:135-224`) resets the room or each metro room that does not carry its own `ResetWhole` (`:181-202`), once the idle time (`time`) has passed. `time` is set to 30000 ms in `setParms` (`:67`), and `TICKS=` or a bare number multiplies it by the tick length (`:73-82`). `RLHOUR=` (real-clock hour) and `MUDHOUR=` (in-game hour) reset once a day at that hour (`:143-178`).
- Rooms that can not be camped with players present are skipped (`isRoomBeingCamped`, `:123-132`).

### RandomTeleporter

- `tick` (`RandomTeleporter.java:165-214`) acts on a mob that can track, or with a 1% roll otherwise (`:171`). It tries up to 249 random rooms (`:176-193`), rejecting air rooms for non-fliers, underwater rooms for non-swimmers, and rooms that fail `okRoomForMe` (same area if `NOWANDER`; excluded domain types). It then `wanderAway`s and brings the mob to the room with optional poof texts (`:196-210`).

### Gang, watch and guard behaviours

- **ROMGangMember** (`ROMGangMember.java:68-157`): every `roll(1,10)` mob ticks (`:154`), it looks for a player in the room and a rival-gang member. Gang name is the first `;` field, or random Red/Blue (`:60`). Chatter comes from the optional parameter lines, else one of seven built-in messages (one of them is the attack itself, `:118-135`). If any inhabitant has a `GoodGuardian` or `Patrolman` behaviour, no fight starts (`:95-96`). The attack is `CMLib.combat().postAttack` (`:140`).
- **ROMPatrolman** (`ROMPatrolman.java:50-156`): every `roll(1,3)` mob ticks (`:153`), it targets the higher-level combatant in the room (`:55-66`). Of the seven outcomes of its `roll(1,7,-1)`, two make peace (`makePeace`, when its level beats the victim's by more than `EXPRATE` and it is not evil, `:123-124`), three attack the victim, and two are flavour (`:84-111`). A whistle warns other rooms of the same area (`:71-76`).
- **RaceHelper** (`RaceHelper.java:75-118`): a same-race observer joins against a malicious act on a same-race target, with a race-specific battle cry (`:112-115`). A bare number in the parameters caps how many may be in the fray (`:61-67`, `:101-108`).
- **PlayerHelper** (`PlayerHelper.java:75-116`): a monster defends a non-monster target from a malicious act, with the same cap.
- **ObjectGuardian** (`ObjectGuardian.java:67-116`): blocks dropping or throwing (into its room) and taking, pushing or pulling items it does not own, when they pass `ITEMMASK` and `MOBMASK`. It sends "won't let <T-NAME> drop that" or "touch that" (`:93`, `:108`). In `SENTINEL` mode it only guards when it is not following and above 25% HP (`:73-80`; `round(maxHP/4.0)` at `:79`).
- **QuestChat** (`QuestChat.java:57-95`) extends `MudChat`. A keyword may end in `::code`; each speaker can trigger each code once. A hit is stored as the quest stat `CHAT:<NAME>` (`:85-88`).
- **NoCombatAssist** (`NoCombatAssist.java:53-65`): on each mob tick it sets `MOB.Attrib.AUTOASSIST` when a countdown expires. The countdown grows by one each cycle, to 99 mob ticks, then stays there.

### Interactions with other systems

- Spells and skills: `Ability` classification, quality and `invoke` (NeutralAbilities, NiceAbilities, Patroller, Sailor).
- Combat: `CMLib.combat().postAttack`, `Aggressive.startFight`, `BrotherHelper.isBrother`, `makePeace` (gangs, guards, helpers, Sailor).
- Movement: `CMLib.tracking()` (`walk`, `wanderAway`, `findTrailToAnyRoom`, `radiatesFromDir`), `Room.bringMobHere`, `moveItemTo`.
- Shops: `ShopKeeper.doISellThis` and `addStoreInventory` (RandomItems).
- Quests: `CMLib.percolator()` and `CMLib.quests()` (RandomQuests, QuestChat).
- Environment: `Area.getClimateObj().weatherType` (PuddleMaker), `Room.setResource` (ResourceOverride).

## Content worth mining

| content | where | notes for a text game |
|---|---|---|
| Poker variants and rules | `PokerDealer.java:114-123`, `:552-813` | Straight, five-card stud, seven-card stud, draw. Ante, betting by coins, table stakes, draw by naming card positions |
| Hand ranking table | `PokerDealer.java:190-200` | Standard eleven-category ranking; one scoring function for all variants |
| Hand descriptions | `PokerDealer.java:1113-1246` | Readable lines for the table ("a Full House: Xs over Ys") |
| Ship roles | `Sailor.java:174-193`, `:821-877` | Five roles (combat mover, peace mover, combat tech, boarder, defender) as one priority list |
| Ship commands | `Sailor.java:283`, `:303`, `:328`, `:527`, `:568`, `:585`, `:610`, `:690`, `:747`, `:881` | `COURSE`, `TARGET`, `AIM`, `LOAD`, `GET`, `HOLD`, `THROW`, `ENTER`, `TRAWL`, `RAISE ANCHOR` |
| Patrol route format | `Patroller.java:96-137` | `N;E;S;W;.;RESTART`-style strings; mirrored and looped routes |
| Spawner parameters | `RandomItems.java:87-171`, `RandomTraps.java:83-227`, `RandomMonsters.java:87-171` | A data-driven spawn-table pattern with count ranges and locale filters |
| Quest generator | `RandomQuests.java:89-113` | Template, expiry, tag ID, retry count; quests produced from data files |
| Protective lines | `ProtectedCitizen.java:52`, `ProtectedCitizens.java:54` | Two default cries for help; the claims are data |
| Puddle and dust items | `PuddleMaker.java:131-177` | Weather-driven terrain items with a decay effect |
| Gang and watch chatter | `ROMGangMember.java:110-136`, `ROMPatrolman.java:86-111` | Short flavour lines; some are crude and are not reproduced here |

## Notable design ideas

- **One clock for every NPC.** `ActiveTicker` (`ActiveTicker.java:57-165`) gives every behaviour a random interval in `[min,max]` and a percentage chance. Two numbers make an NPC feel varied without any scheduler.
- **Skills as NPC behaviour.** NeutralAbilities and NiceAbilities (`NeutralAbilities.java:129-173`, `NiceAbilities.java:137-162`) let every ability's quality tags decide what NPCs cast. Adding a spell adds NPC behaviour for free.
- **Route as data.** The Patroller route is a plain string, mirrored automatically unless it has a loop marker (`Patroller.java:119-132`). Routes are easy to author and to copy.
- **Message interception.** Behaviours veto or rewrite events in `okMessage` and react in `executeMsg`. The poker dealer uses this to make change, reject bad antes and rewrite the drop message (`PokerDealer.java:332-479`). The resource override replaces generated materials the same way (`ResourceOverride.java:279-310`).
- **Captain selection by priority** (`Sailor.java:821-877`). A ship's crew agree on a leader by a fixed priority list, so the AI needs no shared state.
- **Stateful poker with an announcement flag.** `STATEMASK_NEED_ANOUNCEMENT` (`PokerDealer.java:68`, `:1473-1475`) separates "state changed" from "say the prompt". Timers are wall-clock, so a stalled player is handled by the same tick.
- **Stud reshuffle by showing cards.** Moving the highest face-up hand to the front of the pot (`PokerDealer.java:1613-1634`) makes the betting order follow the table state, not a fixed seat.
- **Environment as data.** Weather transitions drive puddles and dust (`PuddleMaker.java:180-248`), so terrain reacts to a weather model that exists for other reasons.

## Problems and cruft

Items marked **(port)** were confirmed by a line-for-line Python port of `determineHand`, run outside the repository. Items marked **(reading)** come from reading the code only and were not run.

### PokerDealer

- **Hand ranking inverted within equal-count groups (port).** After sorting, the list of pairs puts the lowest-valued pair last (`PokerDealer.java:909-920`: ties pick the last index first, and the input is ascending). Two-pair and full-house code then takes `matches[size-1]` as the high pair (`:960-963`, `:1004-1019`). Port result: two pair of 3s and 9s with a king (3,3,9,9,K) ranks **below** two pair of 5s and 7s with a 2 (5,5,7,7,2).
- **The wheel (A-2-3-4-5) is never a straight (port).** The ace-low pass (`:868-882`) compares the raw ace value (14) against `prev+1`. The deck uses 14 for aces (`Items/MiscMagic/StdDeckOfCards.java:121-124` builds values 2-14). Port: A-2-3-4-5 scores as high card.
- **Spades never make a flush (port).** `PlayingCard.suits` is `{0, 16, 32, 48}` (`Items/interfaces/PlayingCard.java:30`), so spades encode as 0. The flush tests use `flushSuit > 0` (`PokerDealer.java:929`, `:939`, `:966`). Port: 2-4-6-8-10 of spades scores as high card; the same ranks in hearts score as a flush. A spade royal flush falls through to a straight.
- **Straight flushes are only checked against the highest straight (port).** The window search stops at the first straight from the top (`:849-866`), and the straight-flush test uses only that one (`:938-945`). Port: 5-9 of hearts plus 10 of clubs scores as a flush, not a straight flush.
- **Face-up cards fail the straight-flush test (port).** `StdHandOfCards.getCard(int)` matches the full ability, including the face-up bit 64 (`Items/MiscMagic/StdHandOfCards.java:507-519`; `StdPlayingCard.java:112-116` sets bit 64). In stud, dealt face-up cards keep that bit. Port: 5-9 of hearts with the 9 face up scores as a flush, not a straight flush.
- **Kicker counts drop the fifth card (port).** High card uses four cards (`:1043-1047`), and flush counts only three suited cards (`:974-975`). Port: A-K-Q-J-2 ties with A-K-Q-J-3, and A-K-Q-3-2 hearts ties with A-K-Q-4-2 hearts.
- **No best-five selection (reading).** `determineHand` scores all cards it is given (up to seven in stud) with fixed kicker counts. Together with the points above, seven-card stud rankings are approximate.
- **Overpaying a call loses value (reading).** In the call branch (`PokerDealer.java:426-445`) the dropped coins are reset to the `anti` amount (`:441-444`), not to the amount needed to call. `executeMsg` then credits the pot with that dropped value (`:567-575`). The player receives change for the full overpayment, so the difference between the amount needed and the anti is neither in the pot nor on the floor. The first-ante branch (`:379-381`) uses `anti` correctly; the call branch looks copied from it.
- Smaller points: `okMessage` builds the table-stakes check from `amountToCall + dropped` (`:458`) without the raiser's own pot entry; the timed-out bet messages say "calls" or "raises" even though no money moves (`:1715-1722`).

### Sailor

- The `TYP_ENTER` case in `executeMsg` has an empty block (`Sailor.java:253-261`), so the code that looks like it handles re-entry does nothing (reading).

### Random spawners

- **Minimum count is never reached when min < max (reading).** `roll(1, max-min, min)` yields `min+1..max` (`Libraries/Dice.java:398-405`) at `RandomItems.java:170`, `RandomTraps.java:223` and `RandomMonsters.java:166`. With `minitems=1 maxitems=3` the target is 2 or 3, never 1.
- **RandomItems places an item twice (reading).** Without `MOBS`, the item goes to a random inhabitant (`RandomItems.java:443-445`) and then also to the room (`:447-450`), and `maintained` receives it twice. `Room.addItem` (`Locales/StdRoom.java:2399-2407`) does not detach it from the mob. With `MOBS` and no inhabitants, the item is never placed.
- **Air restriction is missing on one path (reading).** `RandomMonsters` checks `canFlyHere` for unrestricted areas (`:393`) but not when a locale list is set (`:402`).
- **Cache keys (reading).** XML sources are cached under `filename.length()` plus `filename.hashCode()` (`RandomItems.java:250`, `:271`; `RandomMonsters.java:220`, `:243`), which can collide.
- **Heavy work per spawn (reading).** `RandomMonsters` clears a `HELP_<AREA>` resource on each spawn (`:380`, `:386`). `RandomTraps` enumerates every ability class on each tick that is below target (`:273-278`).
- Instrumentation: `tickStatus=Tickable.STATUS_MISC+n` markers are scattered through `RandomTraps`, `RandomMonsters` and `Patroller`. They are debugging aids that clutter the logic.

### RandomQuests

- **Generation never runs on machines with fewer than four processors (reading).** `Math.round(Math.ceil(availableProcessors()/4))` divides as integers before `ceil` (`RandomQuests.java:168`). With 1-3 processors the limit is 0, so `run()` returns at `:169-170` every time. The outer `finally` (`:261-264`) releases the counter, so nothing is left stuck, but no quest is made.
- **Inline mode can spin forever (reading).** With `INLINE=true`, `tick` loops `while (myQuests.size() < numQuests) generator.run()` (`:295-296`). On a host below the gate, `run()` returns without disabling the behaviour, so the loop never ends.

### Patroller

- `startRoom` is a `WeakReference` (`Patroller.java:68`), but `:329` compares it with `!=` against a `Room`, which is always true. `myStartRoom` can be null when the reference has cleared, and `:331` then dereferences it (reading).

### ProtectedCitizen and ProtectedCitizens

- **Static state shared across instances (reading).** `ProtectedCitizen.zapper` is `static` (`ProtectedCitizen.java:50`). Any instance with a mask-type guard spec stores its mask there (`:120-122`), and any other instance with no name set reads it back (`:88-89`). Two differently configured citizens can therefore use each other's guard mask.
- `ProtectedCitizens` has the same problem with `static citizenZapper` and `static helperZapper` (`ProtectedCitizens.java:52-53`, cached at `:90-91` and `:111-112`).

### RandomTeleporter

- **POOFOUT writes the wrong field (reading).** `setParms` sets `poofin` for `POOFOUT=` (`RandomTeleporter.java:106-107`), so the departure text is never set.
- `wanderAway` is called before the room check (`:196`), so a mob with no valid destination still takes a normal step.

### ResetWhole

- The field default is 1800000 ms (`ResetWhole.java:50`), but `setParms` always resets `time` to 30000 (`:67`). With no parameter the effective idle period is 30 seconds, not 30 minutes (reading).

### ObjectGuardian

- `DROPOK` is tested twice (`ObjectGuardian.java:55`), a copy-paste slip. `SENTINAL` is misspelled, with the correct spelling as the alternative (`:54`).

### Other

- `NoCombatAssist` is named for not helping in combat, but it switches auto-assist on (`NoCombatAssist.java:61`). The name and the behaviour disagree.
- `ROMGangMember` and `ROMPatrolman` hard-code crude lines in the source. They are flavour only and are not reproduced here.

## Takeaways for a Python SUD

- **A behaviour is a small object with a timer.** Give every NPC a `Behavior` with `tick(world, dt)` and `on_event(ev)`. Port `ActiveTicker` as `next_fire = rng.randint(min, max)` plus a `rng.random() < chance` gate. Use inclusive `randint` and avoid the `roll(1,max-min,min)` off-by-one (see Problems).
- **Poker as a state machine.** Model states as an enum, keep the pot as an ordered list (the order is the betting order), and keep a per-phase deadline. Single-player: one human against N NPC opponents. The "betting by dropping coins" mechanic can be a plain `bet <amount>` / `call` / `raise` / `fold` / `pass` command. The announcement flag becomes a `pending_prompt` boolean.
- **Build the hand evaluator from scratch, with tests.** Do not port `determineHand` as is. Use a best-five-of-seven search, a rank tuple `(category, ranks...)`, and explicit wheel handling. The port above gives the failing cases to write first: 3s-and-9s vs 5s-and-7s, the wheel, spade flushes, A-K-Q-J-2 vs A-K-Q-J-3, and 5-9 hearts plus 10 clubs.
- **Money as a ledger, not coin objects.** Track bets as integers in cents on the table, and do not make change through messages. The overpayment bug is a warning about rewriting event payloads.
- **Route NPCs as data.** A patrol route as a list of steps with a `loop` or `mirror` flag is simple to author and to test. Keep the "return to route" step with a shortest-path search.
- **Spawn tables as data.** A table of `{id, min, max, weight, where}` and a `maintain(n)` loop per area gives the random-world feel without the parameter-string parsing. Prune dead spawns each tick and cap attempts.
- **Ship crews** (`Sailor.java`) are heavy for a single player. Keep the idea of a captain chosen by a priority list and a "flee when outmatched" rule, drop boarding and siege, and let the ship be a room with a heading.
- **Protective NPCs** (`ProtectedCitizen.java`, `PlayerHelper.java`, `RaceHelper.java`) become allies that join a fight when the player is attacked. There is no multiplayer case to handle. Keep the caps on how many may join.
- **Multiplayer assumptions to drop**: gang rivalry between players, the "lost player" removal, camping checks (`ResetWhole.java:123-132`), and the admin-stops-the-patrol rule. Keep only the NPC-to-player rules.
- **Environmental responses** like puddles (`PuddleMaker.java`) suit a weather model that is already in the world. They are cheap to add once weather exists.
- **Skill-using NPCs**: if the SUD has an ability table with `quality` tags, an NPC can pick any tag-matching ability with no per-NPC script. This is the most reusable design in the bucket.

## Coverage

- **Read in full** (all lines, with blank, brace-only, comment-only and `@Override` lines filtered out of the compact reads; the license headers were skipped): `NeutralAbilities.java`, `NiceAbilities.java`, `Sailor.java`, `PokerDealer.java` (all 1758 lines, read in seven segments), `Patroller.java`, `RandomItems.java`, `RandomTraps.java`, `RandomMonsters.java`, `ProtectedCitizens.java`, `ProtectedCitizen.java`, `ResourceOverride.java`, `RandomQuests.java`, `PuddleMaker.java`, `ResetWhole.java`, `RandomTeleporter.java`, `ROMGangMember.java`, `ROMPatrolman.java`, `RaceHelper.java`, `PlayerHelper.java`, `ObjectGuardian.java`, `QuestChat.java`, `NoCombatAssist.java`.
- **Read for context**: `ActiveTicker.java` (full, lines 36-166, in another bucket), `Libraries/Dice.java:398-405` (roll), `Items/interfaces/PlayingCard.java:30` and the card classes (`StdPlayingCard.java:89-124`, `StdDeckOfCards.java:106-127`, `:274-283`), `StdHandOfCards.java:164-200`, `:218-228`, `:268-345`, `:496-520`, `Locales/StdRoom.java:2358-2407`, `core/CMProps.java:681`, `StdBehavior.java:297`.
- **Verified by a port**: the hand-ranking results in Problems (PokerDealer) were checked with a line-for-line port of `determineHand`, not with the running game.
- **Skipped**: the body of `StdBehavior`, `MudChat`, `CMLib` library internals (`tracking`, `combat`, `percolator`), and the `Ability` classes that the NPC behaviours invoke.
