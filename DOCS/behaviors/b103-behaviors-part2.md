# Behaviors, part 2: guards, conquest, services and ambience

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b103` (Behaviors [part 2/5: CombatAssister .. Hireling]) · 27 files · 7605 lines of Java.

## Summary

This bucket holds 27 attachable behaviours. Most are small scripts that give a room, item or mob one job: guards and protectors that start fights when a rule matches (Guard, CombatAssister, GoodGuardian, GoodExecutioner, EvilExecutioner, FaithHelper, DoorwayGuardian, GateGuard), combat personas (Fighterness, Druidness, Drowness), service NPCs (Concierge, Hireling, Healer), and environmental or flavour effects (Emoter, FieryRoom, Decay, DelayedTransporter, FasterRecovery, Follower, CorpseEater, the looters). The largest, Conquerable (1,707 lines), turns an area into clan territory: control points are won by kills and conversions, held by carrying clan items such as flags, and lost to revolt, point decay or a rival's conquest. Each behaviour is a Java object attached to a mob, item, room or area, configured by one parameter string, and driven by a message hook (veto or notify) and a periodic tick. Several timing guards and conditions do not do what their comments say, and many rules assume player clans, paying customers or player groups that a single-player game would not have.

## Key files and types

Paths are relative to the CoffeeMUD folder. Rows follow the manifest order (largest first).

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Behaviors/Conquerable.java` | Clan conquest of an area: control points, flags, revolt, taxes, assaults, save and load of clan items. Extends `Arrest` | 1707 |
| `com/planet_ink/coffee_mud/Behaviors/Concierge.java` | Directions-for-payment NPC: parses destination and modifiers from speech, prices by trail, sells a portal or leads the customer | 908 |
| `com/planet_ink/coffee_mud/Behaviors/Emoter.java` | Ambient emotes (visual, sound, smell, puff, social) on a room, area or carried item | 531 |
| `com/planet_ink/coffee_mud/Behaviors/Drowness.java` | Drow persona: stats, race, Fighter or Cleric class by sex, prayer list, periodic spells, 50% spell resistance | 455 |
| `com/planet_ink/coffee_mud/Behaviors/Hireling.java` | Pay-to-hire follower for a set number of real minutes; overpayment buys more time | 408 |
| `com/planet_ink/coffee_mud/Behaviors/Follower.java` | Follows a departing mob (or its items) through the exit it took; GROUP, WANDER, INVENTORY modes | 343 |
| `com/planet_ink/coffee_mud/Behaviors/FasterRecovery.java` | Rate-limited HP, mana and move recovery, or extra full ticks, for rooms, areas, riders, items, mobs | 342 |
| `com/planet_ink/coffee_mud/Behaviors/GateGuard.java` | Opens, closes, locks and unlocks a door on a day and night schedule, reacting to players and knocks | 289 |
| `com/planet_ink/coffee_mud/Behaviors/Decay.java` | Destroys its host after a countdown that starts on the first triggering interaction | 275 |
| `com/planet_ink/coffee_mud/Behaviors/FieryRoom.java` | Burning room hazard: damages occupants, roasts equipment and room items, then restores the room text | 265 |
| `com/planet_ink/coffee_mud/Behaviors/DoorwayGuardian.java` | Vetoes movement through named exits by visible non-brothers, with a message | 223 |
| `com/planet_ink/coffee_mud/Behaviors/CommonSpeaker.java` | Grants and keeps a spoken-language ability on a mob or item | 222 |
| `com/planet_ink/coffee_mud/Behaviors/FaithHelper.java` | Assigns a deity worship ID to NPCs and joins fights on behalf of co-believers | 179 |
| `com/planet_ink/coffee_mud/Behaviors/CorpseEater.java` | Eats one corpse per activation, with mask and player or NPC filters | 172 |
| `com/planet_ink/coffee_mud/Behaviors/GoodExecutioner.java` | Attacks seen evil creatures and thieves | 154 |
| `com/planet_ink/coffee_mud/Behaviors/GoodGuardian.java` | Stops fights involving evil creatures, thieves or brothers; takes the side of the innocent | 151 |
| `com/planet_ink/coffee_mud/Behaviors/DelayedTransporter.java` | Whisks occupants to a random listed room after a number of ticks | 139 |
| `com/planet_ink/coffee_mud/Behaviors/Healer.java` | Casts a benign prayer on a player in the room when mana allows | 118 |
| `com/planet_ink/coffee_mud/Behaviors/EvilExecutioner.java` | Attacks seen good creatures and paladins | 117 |
| `com/planet_ink/coffee_mud/Behaviors/GetsAllEquipped.java` | Once per arrival, takes everything in the room, drops corpses, wears all | 113 |
| `com/planet_ink/coffee_mud/Behaviors/Guard.java` | Joins a fight against anyone who maliciously targets the guarded name (or anyone, if no parameter) | 77 |
| `com/planet_ink/coffee_mud/Behaviors/FightFlee.java` | Makes a mob flee with a 33% chance per tick while its opponent is also fighting it | 76 |
| `com/planet_ink/coffee_mud/Behaviors/CombatAssister.java` | Joins a fight on the side of a mob that is maliciously targeted by another mob | 73 |
| `com/planet_ink/coffee_mud/Behaviors/Druidness.java` | Druid persona: sets Druid class and combat stats on top of `CombatAbilities` | 68 |
| `com/planet_ink/coffee_mud/Behaviors/CorpseLooter.java` | Sets the AUTOLOOT attribute on a mob at growing intervals | 67 |
| `com/planet_ink/coffee_mud/Behaviors/GoldLooter.java` | Sets the AUTOGOLD attribute on a mob at growing intervals | 67 |
| `com/planet_ink/coffee_mud/Behaviors/Fighterness.java` | Fighter persona: sets Fighter class and combat stats on top of `CombatAbilities` | 66 |

Base classes used by this bucket:

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Behaviors/StdBehavior.java` | Base of all behaviours: ID, parameter string, no-op `okMessage`/`executeMsg`/`tick`, host helpers `getBehaversMOB` and `getBehaversRoom` | 410 |
| `com/planet_ink/coffee_mud/Behaviors/ActiveTicker.java` | Random-interval activation: `minTicks`, `maxTicks`, `chance`, and `canAct()` | 166 |
| `com/planet_ink/coffee_mud/Behaviors/CombatAbilities.java` | Shared combat AI: aggro table, skill choice, weapon handling, class and stat set-up | 1066 |
| `com/planet_ink/coffee_mud/Behaviors/Aggressive.java` | `startFight()` (line 187), the common way these behaviours start a fight | 353 |

## How it works

### Shared machinery

**Parameters.** Each behaviour receives its configuration as one string through `setParms`, and `StdBehavior` keeps it (`com/planet_ink/coffee_mud/Behaviors/StdBehavior.java:220-223`). Classes read it with `CMParms` helpers (`com/planet_ink/coffee_mud/core/CMParms.java:1509` for `getParmStr`, `:2952` for `getParmInt`, `:3092` for `getParmBool`). `getParmLong` matches a key as a prefix up to the `=`, so `minticks=8` satisfies a lookup for `min` (`CMParms.java:2975-2995`). Concierge relies on this when it gives its portal decay `minticks=8 maxticks=12` (`com/planet_ink/coffee_mud/Behaviors/Concierge.java:699-700`).

**Two hooks.** A behaviour sees game messages in two phases. Before an action happens, `okMessage` can veto it by returning false. After, `executeMsg` reports it. `StdMOB` fans `okMessage` out to every behaviour (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:2492-2496`), and its `executeMsg` fans out to behaviours in the same way (`:3426-3434`). Guard-type behaviours use the second phase to start fights. DoorwayGuardian uses the first to block movement.

**Periodic ticks.** `ActiveTicker` (`com/planet_ink/coffee_mud/Behaviors/ActiveTicker.java:49-55`) keeps a countdown. `tickReset()` (`:57-71`) picks a uniform value in `[minTicks, maxTicks]`. `canAct()` (`:136-165`) decrements the countdown only for MOB, item-behaviour, room-behaviour and area ticks. At zero it resets, refuses MOBs that cannot act at all (`canActAtAll`) or any tick during shutdown, and then succeeds only if a 1-100 roll is at most `chance` (`:128-134`, roll from `com/planet_ink/coffee_mud/Libraries/Dice.java:409-411`). Several behaviours skip `canAct` and count raw tick calls instead: DelayedTransporter (`com/planet_ink/coffee_mud/Behaviors/DelayedTransporter.java:98-134`), FasterRecovery (`com/planet_ink/coffee_mud/Behaviors/FasterRecovery.java:305-341`), CommonSpeaker (`com/planet_ink/coffee_mud/Behaviors/CommonSpeaker.java:127-137`) and the two looters.

**Fights are queued as commands.** `Aggressive.startFight` (`com/planet_ink/coffee_mud/Behaviors/Aggressive.java:187-221`) checks that the target is in the room, can be seen, is not protected by security flags, is not tracking, and is not in the same group. It then queues a forced `SAY <message>` (`:215`, only when a message is given and the monster is not already fighting that target) and a forced `KILL` (`:217`). Hidden monsters first queue `Thief_BackStab` (`:205-213`). No behaviour attacks directly; the monster's command queue does it. CombatAssister, Guard, FaithHelper, GoodExecutioner and EvilExecutioner all go through this path.

### Conquest: Conquerable

Conquerable (`com/planet_ink/coffee_mud/Behaviors/Conquerable.java:41`) is an area-level behaviour that extends `Arrest`, the legal behaviour (`com/planet_ink/coffee_mud/Behaviors/Arrest.java:41`). An area is held by one clan. Control points are accumulated per clan, and the holder is kept honest by a flag item. Rules are attached as a parameter string such as `JOURNAL=`, `LAW=`, `OWNERSHIP=`, `LOYALTY=` and `LEVELDIFF=` (`Conquerable.java:224-234`).

#### Data model

- `holdingClan` and `prevHoldingClan`: clan IDs; empty string means uncontrolled (`Conquerable.java:56-57`).
- `clanControlPoints`: clan ID to `int[1]` points, cleared when a revolt or conquest happens (`:58`, `:756-759`, `:1073-1076`).
- `totalControlPoints`: the area's requirement. It is -1 until recalculated and is the sum of the levels of NPCs in the area plus the hull points of siegable clan items (`:61`, `:853-900`).
- `clanItems`: a pair list of `ClanItem` and its current owner (`:82`).
- `assaults`: a queue of (attacker NPC, target) pairs (`:59`).
- `noMultiFollows`: a short-lived set used to count each follow only once (`:60`, `:1392-1398`).
- `conquestDate`: when the current holder took control (`:68`, `:1132`).
- `loyaltyBonus`, `revoltFails`: revolt state (`:66`, `:69`).

Timing constants:

| Constant | Value at default tick | Source |
|---|---|---|
| `REVOLTFREQ` | `MILI_DAY / tick ms`, 21,600 area ticks (one real day) | `Conquerable.java:70-71`, `com/planet_ink/coffee_mud/Libraries/interfaces/TimeManager.java:440-444`, tick `TIME_TICK = 4000` at `com/planet_ink/coffee_mud/core/CMProps.java:681` |
| `CHECKFREQ` | 10 area ticks | `Conquerable.java:73` |
| `POINTFREQ` | `10*60000 / tick ms`, 150 area ticks (10 minutes) | `Conquerable.java:75` |
| `FIGHTFREQ` | 2 area ticks | `Conquerable.java:77` |
| `CONTROLTIME` | 3 real days | `com/planet_ink/coffee_mud/Behaviors/interfaces/LegalBehavior.java:54` |
| Flag scan throttle | 360000 ms (6 minutes) | `Conquerable.java:1213-1216` |

Default level-difference limit: `EXPRATE * 2`, which is 12 with the shipped `EXPRATE=6` (`Conquerable.java:229`, `coffeemud.ini:406`).

`isFullyControlled()` is true once `CONTROLTIME` has passed since `conquestDate` (`Conquerable.java:85-88`). Until then the area is "occupied" and the text reads "Full control will automatically be achieved in ..." (`:136-143`).

#### Control flow of the periodic tick

`tick` (`Conquerable.java:510-835`) does nothing unless the host is running and conquest is enabled (`:512-514`). Only area ticks matter (`:518-521`).

1. Clan items are ticked and dropped if they expired or left the area (`:527-548`).
2. On the first area tick with `totalControlPoints < 0`, saved state is loaded from area data (`:552-681`, see Persistence) and points are recalculated (`:680`).
3. Every `CHECKFREQ` ticks (`:684-731`), items that left the area or were destroyed are deregistered. If the holding clan's flag is gone, the previous holder takes the area back when it still has its own flag (`:723-727`); otherwise the rule ends with "due to a missing flag" (`:729`).
4. Every `REVOLTFREQ` ticks, a revolt roll runs (`:733-784`). It runs only when the holder is gone or uses loyalty through items (`:738-739`).
5. Every `POINTFREQ` ticks, every clan loses one point. A clan at 1 or below is removed (`:786-801`, `:794-798`).
6. Every `FIGHTFREQ` ticks, queued assaults run (`:803-832`).

#### Earning control points

All point gains and losses are in `executeMsg` (`Conquerable.java:1341-1663`) and go through `changeControlPoints` (`:1255-1323`).

- **New rooms.** `TYP_NEWROOM` adds the room's share through `fixConquereableRoom` (`:1374-1381`, `:853-887`). That function also enrols every NPC in the room with the holder's clan (`:865-869`).
- **Kills of natives.** A conquest clan member who kills a native earns the victim's level, if `killer.level - victim.level <= conqPtLvlDiff` (`:1436-1446`). It is halved (when above 1) for a deity-worship conquest where the killer and the victim share a deity (`:1439-1441`).
- **Followed kills.** When an NPC follows a victim, that follow is counted once (`:1383-1406`) and the killer is traced up the follow chain to a clan member (`:1415-1424`).
- **Rivals.** When a holder's member kills a member of a rival conquest clan in the area, the rival loses the victim's level (`:1472-1484`).
- **Conversion.** `Skill_Convert` on a native earns the native's level, under the same level-difference rule (`:1488-1506`).
- **Harm to a follower.** A malicious act against a holding-clan NPC that is following the attacker costs the attacker's clan its level in points (`:985-999`).
- **Relations gate.** A gain for a clan other than the holder is accepted only when the two clans are at war or hostile (`:1260-1266`). Otherwise `changeControlPoints` returns false.

A clan wins the area when its points reach `totalControlPoints` (`:1250-1251`, `:1300-1301`, `:1310-1311`).

#### Declaring a conquest

`declareWinner` (`Conquerable.java:1042-1134`):

1. Refuses if the clan is not conquest-enabled (`:1049`).
2. Sends an area-affect message named `CONQUEST` through every room (`:1052-1063`). If any room refuses it, the conquest is aborted and an error is logged (`:1059-1062`).
3. Ends the previous rule with "due to being defeated by ..." (`:1068-1069`).
4. Installs the new holder and clears clan points (`:1072-1076`).
5. If `OWNERSHIP` is true, switches the land titles of the area and its rooms to the new clan (`:1080-1095`).
6. Converts unaffiliated, non-animal NPCs in the area to the new clan (`:1099-1114`).
7. Bumps the `MonthlyConquests` trophy and the `CONQUEREDAREAS` achievement (`:1119`, `:1122`).
8. Posts channel and journal lines: "@x1 gains control of @x2." (`:1129`).

#### Holding the area: flags, items, revolt

Clan items have types `BANNER`, `GAVEL`, `LEGALBADGE`, `FLAG` and `PROPAGANDA` (used at `:261`, `:284`, `:971`, `:401`, `:431`). A `FLAG` proves control. A `PROPAGANDA` item carries no points (`:431`).

- **Item points.** `calcItemControlPoints` sums the levels of in-game NPCs of the holder's clan who carry clan items other than propaganda, in the same metro area (`Conquerable.java:415-437`, sum at `:432`).
- **Revolt chance.** `calcRevoltChance` (`:439-463`):

  ```
  needed = max(1, round(0.05 * totalControlPoints))        # :444-446
  chance = round(10 - 10 * itemPoints / needed)            # :447
  chance -= (rooms of boardable clan items, see :451-457)  # :453-456
  chance -= loyaltyBonus                                   # :459
  chance = max(0, chance)                                  # :460-462
  ```

  At most 10% per roll, and 0% once carried item points reach 5% of the area's total.
- **Revolt roll.** Each `REVOLTFREQ` ticks, `loyaltyBonus` is reset to 0 (`:742`) and a percentile roll below `chance` counts as a success (`:743`). A first success with no pending warning posts "There are the rumblings of revolt in ..." and arms `revoltFails = 20` (`:772-777`). A later success while `revoltFails > 0` triggers the revolt (`:745-768`, message at `:752`). Each failed roll decrements `revoltFails` (`:780-781`).
- **Flags.** `flagFound` (`:1174-1238`) first looks for a registered `FLAG` of that clan in the area. For the holder only, every 6 minutes it scans every room of the area for a flag and registers one it finds (`:1213-1235`).
- **Assaults.** When a player of a clan at war with the holder looks into a room where holding-clan NPCs are, those NPCs are queued (`:1554-1587`, war test at `:1570`). Each queued NPC yells a war cry and attacks the player if both are still valid (`:814-827`). The cries are `warCrys()` (`:1325-1339`).

#### Rules in effect while controlled

`okMessage` (`Conquerable.java:902-1040`) enforces these:

- Clan items in the area's rooms do not expire (`:908-927`).
- Holding-clan NPCs will not attack a fellow member. The would-be attacker is told that the target is "a fellow ... member, and you must respect ..." (`:941`). If the target was fighting that NPC, the target is made peaceful, and the attack is vetoed (`:932-948`).
- Only members of a conquering deity clan can use infusing prayer skills in the area (`:1005-1018`, text at `:1015`).
- Experience gained inside the area by a character whose start room is outside it is taxed at the holder's rate. The tax comes off the message value, and the clan receives its share of the adjusted experience (`:1020-1037`, `:1028-1033`).

Law integration (Conquerable overrides `Arrest`, see `:245-289`, `:1665-1706`):

- An officer is a member of the holding clan who has a `BANNER` item worn or held, not in inventory (`:245-266`).
- The judge needs a `GAVEL` (`:269-289`).
- Authority is the clan's `ORDER_CONQUERED` function (`:1679-1685`).
- `theLawIsEnabled` requires the flag to still be present, otherwise the rule ends "due to illegal conquest" (`:1688-1706`, `:1704`).
- `lawName` is forced to `custom` (`:224`).

#### Persistence

On shutdown or room reset (`:1591-1662`), if a clan holds the area or items need saving, the state is written as XML: `CLANID`, `OLDCLANID`, `CLANDATE` and a list of `ACITEM` blocks with `ROOMID`, `MOB`, `ICLAS`, `IREJV`, `IUSES`, `ILEVL`, `IABLE` and `ITEXT` (`:1605-1645`). The area data is recreated at `:1660`. On the next load (`:552-681`), each item is rebuilt, placed on a matching NPC in the room or in the area, or dropped in the room, and registered if it has a valid clan (`:662-673`).

#### Interactions

Conquerable reaches into `CMLib.clans()` (lookup, relations, trophies), `CMLib.law()` (land titles), `CMLib.database()` (journals and area data), `CMLib.channels()` and `CMLib.commands()` (announcements), `CMLib.achievements()`, `CMLib.map()` (global handler for `TYP_CLANEVENT`, `:241`), `CMLib.leveler()` (experience adjustment, `:1029`), and `Resources` (piety keys, `:341`, `:1125`). It also uses `CMLib.xml()` for the saved blob.

### Protectors and fight triggers

**CombatAssister** (`CombatAssister.java:52-72`). When a malicious message targets a mob other than the assister, the assister joins the fight. Conditions: the attacker and target are not the assister, the assister is not in combat, both are visible, and `CMLib.masking().maskCheck(getParms(), target, false)` passes (`:62-70`). It then calls `startFight` with no message (`:71`).

**Guard** (`Guard.java:55-76`). Same shape, with stricter parameters. A blank parameter guards everyone, and otherwise the target's name must equal the parameter (`:69`). Brothers are excluded (`:74`). `flags()` returns `FLAG_POTENTIALLYAGGRESSIVE` (`:43-47`), so other code treats the guard as aggressive.

**FaithHelper** (`FaithHelper.java:67-178`). The parameter is a deity name, optionally followed by a number and `MSG=...`. On start, the mob's worship character ID is set to the deity (`:104-105`) and the area's piety resource is cleared (`:109`). In `executeMsg`:

- For a room or area, an NPC with no worship ID that comes to life takes the deity (`:118-133`, `TYP_LIFE`).
- For a mob, a malicious act against a co-believer triggers the defence (`:142-152`). The number parameter limits how many defenders join a fray (`:158-168`). The reason text is "THAT`S MY FRIEND!! CHARGE!!" or "BELIEVERS OF <DEITY> UNITE! CHARGE!" (`:170-173`).
- The check `num != 1` at `:143` disables the helper when `num` is 1, even though the limit test at `:168` would allow one defender. The intent is unverified.

**GoodGuardian** (`GoodGuardian.java:51-150`). The tick (`:131-150`) looks for a fight to stop. `anyPeaceToMake` (`:51-80`) returns an in-combat monster whose behaviour grants aggression to its victim, or a brother's victim, or any evil or Thief combatant. `keepPeace` (`:82-128`) then either joins the fight of the victim's victim with "PROTECT THE INNOCENT!" (`:98`), or, with no victim, stops any fighting NPC whose level is more than `EXPRATE` below the guard's (`:108`), by saying "<S-NAME> stop(s) <T-NAME> from fighting with ..." (`:114`) and making both sides peaceful (`:121-122`).

**GoodExecutioner** (`GoodExecutioner.java:71-153`). `grantsAggressivenessTo` (`:71-96`) is true for a non-bound, non-held mob (players only with the `PLAYERS` parameter, `:82-83`) that has another aggression-granting behaviour (`:84-88`), or is evil, or has the Thief base class (`:90`). On a `TYP_LIFE` event (a creature being brought to life, `com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java:1166`) it makes the pair peaceful (`:111-113`), and it records the source name with a timestamp (`:114-121`). Names seen within the last 30 seconds are skipped (`:127-134`). Otherwise it calls `startFight` with "<NAME> IS EVIL, AND MUST BE DESTROYED!" or "... A THIEF ..." (`:142-147`).

**EvilExecutioner** (`EvilExecutioner.java:69-116`). The mirror image, for good creatures and paladins (`:83`, reason "A PALADIN" at `:105-106`).

**DoorwayGuardian** (`DoorwayGuardian.java:164-222`). The most direct use of the veto hook. When a visible non-brother tries to pass a named exit (directions from the parameter, or all exits if none), the guardian sends its message and returns false, so the movement is blocked (`:184-202`, `:204-219`). The default message is "<S-NAME> won't let <T-NAME> through there." (`:47`). Close and lock are not blocked (`:191-192`). `NOSNEAK` lets the guard notice people who would otherwise not be seen (`:182`). Exit lists are cached per room (`:110-161`).

**GateGuard** (`GateGuard.java:182-288`). Finds the first exit with a door in its room (`:86-102`) and keeps it on a schedule. It creates a key for itself if needed (`:104-124`). At night it locks the gate if it has a lock and key (`:227-238`), unless `allnight` is set, which makes it run the day logic at night too. In daytime it unlocks if the gate is locked and either there are players or `keeplocked` is off (`:241-249`), opens when players arrive (`:250-253`), closes when none are left (`:254-257`), and relocks if `keeplocked` (`:258-266`). Player count includes sessions in the room (`:126-141`), with an area-based extension for boats (`:143-179`, bug noted below). Knocks heard from outside count as a player (`:182-203`, `:225-226`).

Its scheduler is a small counter, `noticeTock` (`:223-286`). After each check it sets the next delay: 5 ticks at night, 3 when the gate is locked or closed and empty, and 0 (next tick) when players are present and the gate is closed. Otherwise it re-checks every tick.

### Combat personas

**CombatAbilities** (`CombatAbilities.java:36-1066`) is the shared engine for Druidness and Fighterness. Its flags are parsed from the parameter string in `startBehavior` (`:461-508`): `proficient`, `nostat(s)`, `nocombatstat(s)`, `-Skill` (never use), `+Skill` (always use). `useTurnBasedRule` follows the combat system (`:468`). Other notable parts:

- `makeClass` (`:107-157`) sets the character to the named classes, splitting level evenly: `setClassLevel(C, level / count)` (`:146`).
- `newCharacter` (`:207-251`) starts the class, drops abilities that do not qualify at the mob's level (`:228-233`), and raises proficiency to the class maximum (`:237-240`).
- `setCombatStats` (`:253-291`) attaches a `Prop_CombatAdjuster` whose text is built from the arguments, such as `ARMOR-25 HP+25 MANA+10 SPEED=1` (`:263-277`).
- `setCharStats` (`:293-334`) attaches `Prop_StatAdjuster`, with points per stat of `level` at or below 5, and `5 + (level - 5) / 8` above that, divided across the stats the class grants (`:313-316`).

**Aggro** (`CombatAbilities.java:336-459`). Damage taken by the mob from its victim counts double (`:414`). Damage to a group member of the victim counts once (`:419`). Healing of the victim counts triple (`:429`). Aggro-flagged abilities count twice their adjusted level (`:437`). Casting by the victim's side counts the spell's qualifying level (`:449-453`). The victim switches when an attacker's total is more than 1.5 times the current victim's and above a tenth of maximum HP (`:355-356`). Group members and unseen attackers reset that attacker instead (`:360-362`). On a switch, the others are halved (`:366-370`).

**Skill choice** (`CombatAbilities.java:526-708`). Up to 100 tries to pick a random ability (`:537-549`), skipping auto-invoked abilities, those without trigger strings, and those excluded by `+`/`-` lists (`:551-557`). Then each candidate is scored by `castingQuality` against the victim, self and leader (`:559-561`). The combat mode decides whether a malicious-quality skill is used. Table, with probabilities from `CombatAbilities.java:563-626`:

| Mode | Malicious skill | Beneficial skill (self or leader) |
|---|---|---|
| RANDOM, ONLYALWAYS | always | always |
| DEFENSIVE | 5% | always |
| OFFENSIVE | always | 5% |
| MIXEDOFFENSIVE | 75% | 25% |
| MIXEDDEFENSIVE | 25% | 75% |

**Costs** (`:629-662`). Mana short: the cast is aborted with "Not enough mana" (`:642-645`) unless the roll is 30 or less, `MANACONSUMETIME > 0`, and the mob is not following a player. In that case mana is increased by the cost (`:646`) and the cast goes ahead. Then 5 mana is added in either case (`:648`). Movement short aborts (`:652-654`). HP short aborts (`:659-661`). Action points are charged by `CMProps.getSkillCombatActionCost` (`:630`, `:690-691`).

**Proficiency** (`:664-678`). `proficient` gives 100. A skill with no qualifying level gets 75. Otherwise it is `50 + (level - qualifying level)`, with the level gap clamped to 0-50 (`:673-676`).

**Weapons** (`:783-858`). A mob with no usable weapon in hand picks a suitable weapon from its inventory, or from the room if none (`:827-849`, only weapons of level at or below its own, `:840`). It gets, then wields, one every 5 ticks (`:827`, `:856`).

**Wand fallback** (`:910-952`). If no skill was used, a wand held by the mob is used with `sayto` and its magic word.

**Druidness** (`Druidness.java:51-67`) sets `COMBAT_RANDOM`, calls `makeClass(mob, ..., "Druid")` and `newCharacter`, and applies `setCombatStats(mob,0,-25,0,25,10,0,true)` and `setCharStats` (`:58-65`). **Fighterness** (`Fighterness.java:49-65`) does the same with `"Fighter"` and `setCombatStats(mob,0,15,0,15,-10,10,true)` (`:62`).

**Drowness** (`Drowness.java:35-455`) is not a `CombatAbilities` subclass. It runs its own loop. On start it sets the stats `STR 12+1d6, INT 14+1d6, WIS 13+1d6, DEX 15+1d6, CON 12+1d6, CHA 13+1d6` (`:68-73`). Males get Fighter and the Drow race and a weapon kit (`:74-87`, `:177-235`). Females get Cleric and the same race and prayers (`:82-86`, `:94-175`). Both receive Spell_Darkness at proficiency 100 (`:100-102`, `:237-240`) and 14 prayers at proficiency `5d10+50` (`:105-173` and `:242-310`).

Cadence in combat (`:365-414`). Males use a random skill every 2 ticks (`fightDown`, `:389-395`), check their stance every 3 (`statCheck`, `:397-401`), and cast darkness every 4 (`:402-406`). Females cast a prayer every 3 ticks (`spellDown`, `:376-381`) and darkness every 4 (`:382-386`). A 70% roll picks a random qualifying ability, otherwise `Prayer_CureSerious` (`:326-335`, `:419-430`). Spell resistance is a 50% roll on hostile casts (`:351-358`, message "The drow resisted your spell!" at `:357`).

**FightFlee** (`FightFlee.java:49-75`) is an `ActiveTicker` with `min=max=1` and `chance=33` (`:52`). Each tick it flees (`CMLib.commands().postFlee`, `:72`) only when the mob is in combat with a victim who is in turn fighting it (`:69-72`).

**Healer** (`Healer.java:52-117`). Chance is set to 100 with a 10-20 tick interval (`:55`). It acts only when the mob has at least 50 mana and a random roll against its mana fraction passes (`:85-87`). It looks at a random player in the room, goes to that player's group leader, and casts one of six benign prayers on the player if the leader is a player not fighting the healer (`:89-112`). The prayer list is `Prayer_CureBlindness`, `Prayer_CureDisease`, `Prayer_CureLight`, `Prayer_RemoveCurse`, `Prayer_Bless`, `Prayer_Sanctuary` (`:59-64`).

### Service NPCs

**Concierge** (`Concierge.java:38-908`) is a directions-and-delivery vendor. Its parameter string is a list of `;`-separated items (`setParms` at `:201-338`):

- A bare number sets the base price (`:206-209`, `:313-316`).
- `KEY=value` items set options, using the `TrackWords` enum (`:52-79`): `PORTAL`, `MOBILE`, `CLAN`, `AREAONLY`, `NOCLIMB`, `NOCRAWL`, `NOWATER`, `INDOOROK`, `NOAIR`, `NOLOCKS`, `NOHOMES`, `NOINDOOR`, `MAXRANGE`, `GREETING`, `ENTERMSG`, `TALKERNAME`, `PERROOM`.
- Other names are destinations (rooms or areas) with their own price (`:253-261`, `:322-334`).

Flow when a player speaks to it (`executeMsg`, `:750-907`, speech branch `:784-881`):

1. Trailing words in the speech are read as route modifiers: `noclimb`, `nodoors`, `noswim`, `noprivate`, `nofly`, `nolocks`, `nocrawl` (`:802-833`). They restrict the search for the destination.
2. The destination is found (`findDestination`, `:414-513`): by rate table, then by area, then by room name within radius, then by a hunt within 5 rooms (`:507`).
3. Price (`getPrice`, `:340-357`): the destination's own rate if set, otherwise `basePrice + perRoomPrice * trail length` (`:348-353`), where the trail comes from the tracking library.
4. If the price is above zero, the customer's debt is recorded (`:869-879`). The concierge says "I can help you find X, but you'll need to give me Y first." (`:127`). Coins handed over reduce the debt (`executeMoneyDrop`, `:624-672`). Overpayment is returned as change: "Heres your change." (`:643-660`, text at `:649`).
5. Delivery (`giveMerchandise`, `:679-748`):
   - PORTAL: a `GenPortal` named "A portal to X" is placed in the customer's room (`:694-696`). It carries a `Decay` behaviour with `minticks=8 maxticks=12` (`:699-700`), and the customer is told "Enter this portal to X." (`:702`). The portal opens with the message "A blinding, swirling portal appears here." (`:687`).
   - MOBILE: the concierge invokes `Skill_Track` to lead the customer (`:712-718`). It then follows the customer and, once the trip is over, wanders back to its start room (`:608-620`, `:722-723`).
   - Default: a trail description, "The way to X from here is: ..." (`:737-746`). If no path avoids flying, it retries with air routes excluded (`:741-745`).

Greeting: on entry by a player, if no destination is pending, the concierge speaks its greeting (`:889-896`). The default text is "Need directions? Just name the place and I'll name the price! Append words like noswim, noclimb, nofly, nolocks, noprivate, and nocrawl to narrow the focus." (`:87-88`).

**Hireling** (`Hireling.java:37-408`) is a paid follower with a real-time contract. Its parameter string is `price;minutes;zapper-mask` (`setParms` at `:84-111`). Defaults are 100 for price and 30 minutes (`:49-50`, `:73`, `:109`).

- Hire: coins of at least `price` (in the right currency, `:252-256`) start a job. `workingFor` is set to the payer's name, `onTheJobUntil` to now plus `minutes` of wall-clock time (`:398-399`, not game time), and a warning is scheduled at half-time (`:400`, `:221-230`). The hireling follows the payer (`:401-402`) and sets its skills' proficiency where it is zero (`:386-396`).
- Overpayment: the excess is held as credit (`:384-385`).
- Underpayment: the amount is saved by name and added to later payments (`:353-371`). Nothing expires it.
- Expiry (`tick`, `:169-206`): if credit remains, extra time is `round(minutes / price * credit)` (`:191-192`), added to the job (`:204`). Otherwise the hireling stops following, walks out and returns to its start room (`:135-158`, `:197`).
- Refusals: a payer outside the zapper mask gets "I wouldn't work for the likes of you." (`:247-249`). A wrong currency gets "I'm sorry, I only deal in ..." (`:254`). It never gives or drops its own coins (`:259-268`).
- Speech: "HIRE", "WORK", "TIME" or "AVAILABLE" gives a quote ("I'm for hire. Just give me ... and I'll work for you for ... "hours"." at `:308`). "SKILLS" lists abilities (`:330-345`). "FIRED" ends the job (`:321-328`). QUIT ends it too (`:284-286`).

### Environment and ambience

**Emoter** (`Emoter.java:37-530`). An `ActiveTicker` with defaults `min=10`, `max=30`, `chance=50` (`:55`). Its parameter string has two parts, split at the first `;` (or `/`). The first part carries global options: `expires=N` (`:85`, counts down, `:408-413`), `inroom=` room IDs (`:86`, `:318-335`), `MASK=` (`:88-90`), and type words `SMELL`/`AROMA`, `SOUND`/`NOISE`, `PUFF`, `SOCIAL`, `VISUAL`/`SIGHT`, `BROADCAST`, `NOBROADCAST`, `PRIVATE`, `NOPRIVATE`, `EMOTEONLY` (`:171-212`). Each later segment is one emote, which may carry its own type words at either end (`:92-124`, `:214-230`).

Output by type (`emoteHere`, `:337-397`): visual uses `MSG_EMOTE`, sound uses `MSG_NOISE`, smell and puff use `TYP_AROMA|MASK_EYES`, and social invokes a social (`:355-392`). Targets:

- A room: emote in the room (`:448-455`).
- An area: emote in every room with a player present (`:420-435`).
- An item: "X carried by Y" when the carrier is in the game and the emote is not private (`:466-487`).
- Broadcast: also into adjacent rooms with open exits and players, as "something <direction>" (`:494-522`, text at `:518`).

Smell and puff emotes are triggered by sniff and puffing events (`:297-316`, `:232-247`).

**FieryRoom** (`FieryRoom.java:27-265`). `min=5`, `max=10`, `chance=100` (`:55`). Parameters: `Title` (default "A Charred Ruin", `:69`), `Description` (`:70`), `damage` (default 10, `:71`), `eqchance` (default 0, `:72`), `burnticks` (default 12, `:73`), and flags `NOSTOP`, `NONPC`, `NOFIRETEXT` (`:75-77`). Each activation (`:101-158`):

- Each occupant takes `damage` from fire (`dealDamage`, `:175-182`, text "The fire here <DAMAGE> <T-NAME>!" at `:180`). With `NONPC` set, monsters are spared unless they are away from their start room and have a player in their group (`:114-130`). Players are burned unless they have order or command-room rights (`:138-146`).
- Equipment is roasted unless the roll is at most `eqchance` (`:134-135`). The default 0 means it is always roasted. A metal, glass, rock or similar item takes 1d6+1 damage and may be dropped (`:191-207`). Anything else is set burning with the `Burning` ability (`:209-216`).
- Each room item catches fire with a roll above 60, which is about 40% (`roastRoom`, `:224-243`, `:234`).
- Fire text is one of six lines, chosen at random (`:81-90`, `:155-156`).

Burn-out (`:159-171`) runs on every tick call, not only activations. After `burnticks` calls the room's title and description are replaced with the `Title` and `Description` parameters, and the behaviour removes itself (`:161-168`). `NOSTOP` keeps the fire going forever (`:159`). At the default 4-second tick, 12 calls is about 48 seconds, assuming one call per tick (unverified).

**Decay** (`Decay.java:35-275`). Defaults are `min=max=50` and `chance=100` (`:49-56`). Parameters: `remain=N` overrides the countdown (`:74`), `use2` widens the triggers (`:75`), `answer=` is the departure text (default " vanishes!", `:76`, `:60`), `NOTRIGGER` starts it at once (`:78-79`), and a zapper mask gates triggers (`:80-83`). Decay does nothing until it is "activated" (`:103-104`). It becomes active when:

- A malicious act by a player is seen by its mob host (`:169-172`).
- For item hosts (non-`use2`), a player enters, sits on, mounts or sleeps on a Rideable (`:243-249`). The portal Decay in Concierge relies on this, since `GenPortal` extends `StdPortal`, which implements `Rideable` (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java:36`). The entry trigger is `TYP_ENTER` (`Decay.java:243-244`).
- A player wears, holds or wields armour or a weapon (`:250-256`), or gets, pushes or pulls any other item that is not armour, a weapon or a Rideable (`:257-268`).

Once active, each tick's countdown (`canAct`) runs out, then the host is destroyed. A mob's room is told the answer text. An item's owner is told the answer and the item is dropped or removed (`:107-156`). A null mask is assumed to pass (the mask library was not read).

**DelayedTransporter** (`DelayedTransporter.java:36-139`). Parameters: an optional ticker part before the first `;`, then `;`-separated destination room names (`:67-95`). Each tick, for each occupant, a counter per name (`transportees`, `:109-114`) increases. Once it reaches `minTicks`, a roll under `chance`, or any count above `maxTicks`, the occupant is moved to a random listed room (`:116-129`). If the room is unknown, the occupant is told "You are whisked nowhere at all, since '...' is nowhere to be found." (`:123`). The defaults are 5 ticks and 100% chance (`:62`).

**FasterRecovery** (`FasterRecovery.java:35-342`). Parameters are `HEALTH`, `HITS`, `MANA`, `MOVE` and `BURST` (`:49-56`), each with a value (`:68-122`):

- An integer applies that many recovery steps through `CMLib.combat().recoverTick` per trigger (`:209-240`).
- A percentage (`%`) scales the difference between a copy's recovery and the current state (`:124-157`).
- `N/T` triggers every `T` ticks (`:89-99`).
- `BURST` runs a whole extra `M.tick(M, TICKID_MOB)` per step (`:201-211`).
- `HITS` restores only HP, `MANA` only mana, `MOVE` only moves, by saving and restoring the other two (`:223-272`).

**Follower** (`Follower.java:35-343`). Parameters: `GROUP` (follow a leader in the room, `:246-254`), `WANDER` (may leave its area, `:262`), `NOFOLLOWERS` (`:244`), `INVENTORY` or `INV` (items too, `:81`, `:288-297`), `NAME=`, and a zapper mask (`:77-86`). With `max=-1` the follower is instant: it reacts to leave, flee, recall or enter messages as they happen, and schedules its move 100 ms later (`:185-197`, `:112`, `:95-141`). Otherwise it reads the direction from the leaving message's last word (`:161-180`) and walks that way on its next tick if it stays inside its area (`:256-273`).

**CommonSpeaker** (`CommonSpeaker.java:36-222`) grants a non-savable language ability at proficiency 100 (`:168-172`, `:190-192`) and keeps it spoken (`:197-219`). Its default is "Common" (`:78`). Language checks back off: each check waits one tick longer than the last, capped at 99 (`:133-137`).

**CorpseEater** (`CorpseEater.java:35-172`) has `min=5`, `max=20`, `chance=75` (`:58`). It eats one visible corpse per activation (`:135-168`, message "<S-NAME> eat(s) <O-NAME>." at `:164`). Parameters: `EATITEMS` (also destroy the corpse's contents, `:162-163`), `+PLAYER` (eat player corpses, `:85-88`), `-NPC` or `-MOB` (skip NPC corpses, `:91-95`), and a mask. The mask is tested against a temporary mob built from the corpse's stats (`:103-125`, `:151-160`).

**CorpseLooter** and **GoldLooter** (`CorpseLooter.java:49-66`, `GoldLooter.java:43-66`) set the mob's AUTOLOOT or AUTOGOLD attribute (`:61`). They run on mob ticks, at intervals that grow by one tick each time, capped at 99 (`:57-64`). The effect of the attribute is in mob code, which was not read.

**GetsAllEquipped** (`GetsAllEquipped.java:49-112`). Once per arrival (`DoneEquipping`, `:80-88`), it issues forced `GET ALL`, then drops any corpses it picked up (`:92-108`), then `WEAR ALL` (`:109`). The flag resets when a death happens outside the room (`:68-71`).

## Content worth mining

**Conquest reasons and lines.** The rule-ending reasons are short phrases, each a suffix for a message: " due to losing their clan" (`com/planet_ink/coffee_mud/Behaviors/Conquerable.java:158`), " due to a missing flag" (`:729`), " due to revolt" (`:768`), " due to being defeated by X" (`:1069`), " due to illegal conquest" (`:1704`), " due to a lack of legal authority" (`:1679`), " due to clan dissolution" (`:1361`), " due to an unclaimed flag" (`:1567`). Area status lines include "Area 'X' is occupied by ..." and "Full control will automatically be achieved in ..." (`:136-143`).

**War cries** (`Conquerable.java:1327-1337`). Four lines, picked at random: "INVADERS! Attack!", "We are under attack! To arms!", "Destroy the enemy!", "War!!!!!".

**Hireling dialogue.** "I'm for hire. Just give me ... and I'll work for you for ... "hours"." (`Hireling.java:308`). "Ok. You've got me for at least ... "hours". My skills include: ..." (`:403`). "Your time is up. Goodbye!" (`:196`). "Your base time is up, but you've paid for ... more minutes, so I'll hang around." (`:202`).

**Concierge dialogue.** Greeting above (`Concierge.java:87-88`). "I can help you find X, but you'll need to give me Y first." (`:127`). "Hey, you've already paid me!" (`:544`). "Sorry, I can't get there from here." (`:719`). "OK! Off we go!" (`:724`). "OK, here you are -- good luck!" (`:618`). "I'm sorry, I don't know where 'X' is." (`:863`). Modifier words are listed above (`:802-833`).

**Archetype data.** Drow (`Drowness.java:68-73`, `:86`, `:179-181`, `:186-235`). Stats 12/14/13/15/12/13 plus 1d6 each. Prayers used by both sexes: `Prayer_ProtGood`, `Prayer_CauseLight`, `Prayer_CauseSerious`, `Prayer_Curse`, `Prayer_Paralyze`, `Prayer_DispelGood`, `Prayer_Plague`, `Prayer_CauseCritical`, `Prayer_Blindness`, `Prayer_BladeBarrier`, `Prayer_Hellfire`, `Prayer_UnholyWord`, `Prayer_Deathfinger`, `Prayer_Harm`. Male weapon kit: `DrowChainMailArmor`, `DrowSword`, `DrowDagger`, `Scimitar`. Druid and Fighter stat sets are listed above (`Druidness.java:64`, `Fighterness.java:62`).

**Healer prayers** (`Healer.java:59-64`). `Prayer_CureBlindness`, `Prayer_CureDisease`, `Prayer_CureLight`, `Prayer_RemoveCurse`, `Prayer_Bless`, `Prayer_Sanctuary`.

**Fire lines** (`FieryRoom.java:83-88`). "The fire here crackles and burns." "The intense heat of the fire here is very painful." "The flames dance around you, licking at your clothes." "The fire is burning out of control..." "You hear popping and sizzling as something burns." "The smoke here is very thick and you worry about whether you will be able to breathe."

**Faction shouts.** "THAT`S MY FRIEND!! CHARGE!!" and "BELIEVERS OF X UNITE! CHARGE!" (`FaithHelper.java:170-173`). "X IS EVIL, AND MUST BE DESTROYED!" (`GoodExecutioner.java:147`). "PROTECT THE INNOCENT!" (`GoodGuardian.java:98`).

**Door and portal messages.** "<S-NAME> won't let <T-NAME> through there." (`DoorwayGuardian.java:47`). "A blinding, swirling portal appears here." (`Concierge.java:687`). "<S-NAME> lock(s) <T-NAME><O-WITHNAME>." and the unlock variant (`GateGuard.java:233`, `:245`).

**Ambient emote grammar.** `BROADCAST`, `PUFF`, `SMELL`, `SOUND`, `SOCIAL`, `EMOTEONLY`, `expires=`, `inroom=`, `MASK=`. Emotes carry no built-in text; the content is data in each mob's or room's parameter string (`Emoter.java:79-124`).

**Dispatch text.** "You are whisked nowhere at all, since '...' is nowhere to be found." (`DelayedTransporter.java:123`).

## Notable design ideas

- **Territory held by a token item.** Control is a physical object, a flag carried by an NPC. Stealing or destroying it changes the rules (`Conquerable.java:1174-1238`, `:717-729`). The same items also confer legal roles: banner for officer, gavel for judge (`:245-289`). Rule and territory are one object graph.
- **Revolt as a formula over loyalty.** Carrying clan items among the population lowers the revolt chance, down to zero at 5% of the area's total points (`:439-463`). A success does not revolt at once. It first warns, then the next success revolts (`:772-777`, `:745-768`).
- **Veto as a behaviour.** DoorwayGuardian blocks movement by returning false from `okMessage`, which requires no scripting (`DoorwayGuardian.java:184-202`). Guards are listeners with a veto, not separate AI.
- **Fights as queued commands.** `startFight` queues `SAY` and `KILL` through the normal command path, so the same visibility, tracking and security checks apply to NPC attacks as to player commands (`Aggressive.java:187-217`).
- **Trail-based prices in plain speech.** The concierge reads route modifiers from the end of the sentence and prices by path length (`Concierge.java:802-833`, `:348-353`). It is a tiny language with a price model.
- **Overpayment buys time.** Hireling converts extra money into extra minutes at the contract's own rate (`Hireling.java:191-192`). A warning sounds at half-time (`:221-230`).
- **Burn-out state changes.** A hazard that ends by rewriting the room's title and description (`FieryRoom.java:161-168`) is a cheap way to make the world change permanently.
- **Trigger-gated decay.** Decay does nothing until someone interacts with it, then counts down (`Decay.java:103-104`, `:162-183`). A dropped item or a portal can wait for the player.
- **Archetypes as attached components.** Druid, Fighter and Drow are one behaviour object each, which sets class, stats, abilities and gear on a generic mob (`Druidness.java:51-67`, `Drowness.java:61-92`).
- **Alignment-driven aggression.** GoodExecutioner and EvilExecutioner derive targets from flags such as evil, good and class, not from scripts (`GoodExecutioner.java:90`, `EvilExecutioner.java:83`).

## Problems and cruft

Bugs and inconsistencies found by reading (not run):

1. **FasterRecovery ignores the direct-recovery flag.** The `+`/`-` prefix stores `0` in the third slot (`com/planet_ink/coffee_mud/Behaviors/FasterRecovery.java:110`, `:112`), but the direct branch requires `td[2] == 1` (`:173`). That branch is unreachable, so prefixed values behave as unprefixed ones.
2. **FasterRecovery BURST can recurse without end.** A `BURST` with no `/T` suffix uses `T = 1` (`:88`), so the counter fires on every call. Each burst calls `M.tick`, which runs this behaviour again (`:201-205`, `:170-171`), and `StdMOB` ticks every behaviour with no re-entry guard (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:4345`). Likely a stack overflow. Unverified at runtime.
3. **Concierge operator precedence.** The tick condition at `Concierge.java:589-592` mixes `&&` and `||` so the `||` at `:592` applies to the whole first part. Non-mob ticks therefore always drain the speech queue. The inner `while` makes it harmless in practice, but the intent is unclear.
4. **GateGuard counts the wrong room.** The loop over the boat's rooms calls `countSessionsHere(mob, room)` with the gate's room, not the loop's `R` (`com/planet_ink/coffee_mud/Behaviors/GateGuard.java:163-168`, the bad call at `:167`). Each room in the boat's area adds the same count, so a boat inflates the player count.
5. **Conquerable tax branch has no null check.** `holdingC` may be null if the clan has been dissolved, and `holdingC.getTaxes()` is called without a test (`Conquerable.java:1024-1025`). The enclosing guard (`:1002-1003`) does not check for a clan.
6. **Conquerable order branch is dead.** The loop finds a `LEGALBADGE` and then does nothing: `if(badge==null){ }` (`:978-981`). The whole block is a stub.
7. **Conquerable cleanup loop does nothing.** The loop calls `i.next()` and never removes anything (`:1399-1403`), with the comment "this should clear out the cruft". Whether `ExpireTreeSet` prunes on iteration was not read.
8. **The "deep breath" throttle never closes.** GoodExecutioner sets `deepBreath = 0` after an attack (`GoodExecutioner.java:135`), and the gate passes whenever `deepBreath == 0` (`:123`). GoodGuardian does the same (`GoodGuardian.java:142`, `:144`). The gate only re-arms on a non-free tick (`GoodExecutioner.java:105`, `GoodGuardian.java:139`).
9. **Comments promise a random check that is not there.** "base 90% chance not to be executed" (`GoodExecutioner.java:137`, `EvilExecutioner.java:101`) has no roll in the code.
10. **EvilExecutioner re-entry guard is bypassed.** `if((deepBreath==0)||((now-deepBreath)>60000)&&(!noRecurse))` (`EvilExecutioner.java:96`) binds `&&` tighter than `||`. Because `deepBreath` is 0 after line 99, the `noRecurse` test is never reached.
11. **Mode-name parsing removes the wrong token.** `getParmsMinusCombatMode` (`CombatAbilities.java:159-175`) calls `removeElementAt(v)` once per matching mode name. A word matching two names, such as `MIXED` (both MIXED modes), removes the neighbouring token as well.
12. **FaithHelper's limit disagrees with itself.** `num != 1` (`FaithHelper.java:143`) disables the helper for 1, while the limit test at `:168` would allow one defender.
13. **Hireling credit never expires.** Underpayments accumulate by name without limit (`Hireling.java:353-371`), and overpayments are only spent when a job ends (`:191-192`).
14. **DelayedTransporter keys by name and never prunes.** `transportees` uses the mob's name (`DelayedTransporter.java:109-114`). Two mobs with the same name share a counter, and an entry for a mob that left stays forever.
15. **Two behaviours ignore the tick type.** FasterRecovery (`FasterRecovery.java:305-341`) and DelayedTransporter (`:98-100`) count every call, so their timing depends on how often a host ticks.
16. **Null location risks.** CorpseEater (`CorpseEater.java:134-135`) and GetsAllEquipped (`GetsAllEquipped.java:84-85`) call `mob.location()` and use it without a check. Healer does check (`Healer.java:81-83`).
17. **Possible unbounded loops.** In Drowness, `useSkill` and `castFemaleSpell` loop until they find an ability that qualifies at the mob's level (`Drowness.java:328-330`, `:421-423`). If none does, the loop never ends. Unverified in practice.
18. **Follower may dereference null.** `followingItem` calls `lastOwner.location()` (`Follower.java:286`), and the instant-move path can call it before `lastOwner` is set (`:136-137`, `:305-315`). Unverified.
19. **Inconsistent name matching.** Guard uses an exact, case-insensitive name (`Guard.java:69`), while CombatAssister uses a zapper mask (`CombatAssister.java:70`).
20. **Swapped constructor bounds.** GetsAllEquipped sets `maxTicks=5` and `minTicks=10` (`GetsAllEquipped.java:52`). `tickReset` still yields values from 5 to 10 (`ActiveTicker.java:57-60`), so it works by accident.

Multiplayer assumptions: Conquerable is built on player clans, wars, deities, and clan items. Concierge keeps a debt per customer. Hireling works for any named payer. Healer heals player groups. Follower's GROUP mode and CombatAbilities' leader and group checks assume players in parties. GoodExecutioner and EvilExecutioner have a PLAYERS switch to make players targets.

## Takeaways for a Python SUD

**Borrow.**

- **A single behaviour interface with two hooks.** Use a veto-capable event check and a tick. The veto is what makes a guard or a locked gate work with no special case. A minimal shape:

  ```python
  @dataclass
  class Ticker:
      """Random-interval activation, after ActiveTicker.canAct (ActiveTicker.java:136-165)."""
      min_ticks: int = 10
      max_ticks: int = 30
      chance: int = 100          # percent
      countdown: int = 0

      def due(self, rng) -> bool:
          self.countdown -= 1
          if self.countdown >= 1:
              return False
          self.countdown = rng.randint(self.min_ticks, self.max_ticks)
          return rng.randint(1, 100) <= self.chance
  ```

- **Config as data, not strings.** Replace the `key=value;key=value` strings with dicts or dataclasses. The string grammar is the reason for `getParmLong`'s prefix matching and the `;` and `/` ambiguity.
- **Revolt and control as a formula.** Port the revolt formula as is, with one caveat: Java `Math.round` rounds halves up, so use `math.floor(x + 0.5)` where Python's `round` would round to even.

  ```python
  def revolt_chance(item_points: int, total_points: int, loyalty: int = 0) -> int:
      needed = max(1, math.floor(0.05 * total_points + 0.5))   # Conquerable.java:444-446
      chance = math.floor(10 - 10 * item_points / needed + 0.5) - loyalty  # :447, :459
      return max(0, chance)                                    # :460-462
  ```

- **Territory as a carried token.** A flag or banner item that an NPC carries is cheap to implement, visible to the player, and easy to steal. It gives the same kind of "hold the flag" play as Conquerable without the clan machinery.
- **Concierge pricing.** Price by shortest path length, with modifiers that restrict the path search (no climb, no water, no flying). That gives a natural sell-directions mechanic on a graph of rooms.
- **Hireling contracts.** A timed follower with extension by overpayment is a clean mechanic for a hired guide or porter. Use game time, not wall-clock time, so that saves and tests are deterministic.
- **Triggered decay.** Items that vanish after first use, such as a one-shot portal, are a good fit for a text game.
- **Ambient emotes.** A table of flavour lines per room, with a probability and a cooldown, is cheap and makes the world feel alive. Use `ActiveTicker`'s countdown plus chance pattern.
- **Burn-out and reversion.** A hazard that rewrites a room's description after a number of turns is a simple way to make the world change.

**Simplify.**

- Merge the protector family (Guard, CombatAssister, GoodGuardian, GoodExecutioner, EvilExecutioner, FaithHelper) into one table of reaction rules: `(event kind, predicate, reaction, message)`. The predicates are the only difference between them.
- Replace the fight-by-command queue with direct state changes. A single-player game does not need forced command queues to keep NPCs from attacking.
- Make the aggression and alignment flags explicit fields on NPCs, not derived from behaviour lists.

**Drop.**

- Clans, law, deities and worship, channels, journals, achievements, trophies and the XML area blob. Conquest is the largest single item in the bucket, and most of its 1,707 lines are multiplayer bookkeeping.
- Java reflection, `copyOf` and the parameter mini-language.
- The bugs listed above, most of which become impossible if the rules are written as plain Python code.

**Rethink for one player.** Replace "customer" with "the player", "clan" with "faction", and "war" with a faction stance toward the player. The conquest engine then becomes a single-player campaign of NPC factions that the player can help or oppose.

## Coverage

- **Read in full (27 of 27 bucket files):** every file in the manifest. Conquerable was read in two parts (lines 1-1365 and 1366-1707). Base classes read in full: `StdBehavior.java`, `ActiveTicker.java`, `CombatAbilities.java`.
- **Skimmed:** `Aggressive.java:187-221` (`startFight`), `CMParms.java:1509` and `:2952-2995` (parameter lookup), `StdMOB.java:2459-2496` (`okMessage` fan-out), `:4338-4352` (behaviour tick), `:2413-2448` (`enqueCommand`), `Libraries/Dice.java:398-411` (rolls), `Behaviors/interfaces/LegalBehavior.java:50-54` (`CONTROLTIME`), `Behaviors/BrotherHelper.java:73` (signature only), `Items/Basic/StdPortal.java:36` and `Items/Basic/GenPortal.java:36` (class declarations), `Common/interfaces/CMMsg.java:1098` and `:1164-1166` (`TYP_DEATH`, `TYP_LIFE`), `core/CMProps.java:681` and `:3503-3506` (tick length), `coffeemud.ini:401-406` (`EXPRATE`), `Libraries/interfaces/TimeManager.java:440-444` (time constants).
- **Not read:** `Behaviors/Arrest.java` (4,148 lines, the superclass of Conquerable, covered by another bucket), the clan, law, achievement, channel, database and XML libraries (named only), `MaskingLibrary`, `ExpireTreeSet`, `CombatLibrary.recoverTick`, `Prop_CombatAdjuster`, `Skill_Track`, social definitions, and language ability classes.
