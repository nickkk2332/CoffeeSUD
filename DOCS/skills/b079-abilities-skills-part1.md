# Abilities/Skills, part 1: Abandon Ship to Crow's Nest

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b079` (Abilities/Skills [part 1/4: Skill_AbandonShip .. Skill_CrowsNest]) · 36 files · 9290 lines of Java.

Paths are relative to the CoffeeMUD folder, so `com/planet_ink/coffee_mud/...` is written in full.

## Summary

A CoffeeMud "skill" is an ability invoked as a command rather than a spell. Its base class `StdSkill` (`com/planet_ink/coffee_mud/Abilities/Skills/StdSkill.java:36`) is 69 lines of defaults on top of `StdAbility` (3018 lines), which holds the real shared rules: proficiency rolls, mana/move/hit-point costs, cooldowns, tick-down effects and message hooks. The 36 classes in this bucket fall into four groups. Many are one-shot verbs that roll a check and then change the world (Burrow digs a room, Crow's Nest builds one, Abandon Ship moves a crew, Convert changes a mob's deity). Some are passives that listen to messages (Combat Log, Combat Recall, Above The Law, the Second/Third/Half Attack family). A large sailing-ship group sits on the boardable-area and siege code. Legal skills (warrants, bounty, arrest, sap) and animal and racial skills make up the rest. Together they show how a text engine turns a plain verb into an effect with timing, skill growth, costs and messages, and they supply many small, reusable systems.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Skills/StdSkill.java` | Family base: classification `ACODE_SKILL`, targets mobs only | 69 |
| `Abilities/StdAbility.java` | Shared rules: `proficiencyCheck`, `usageCost`, `invoke`, `startTickDown`, `tick` (skill-relevant ranges read, see Coverage) | 3018 |
| `Skill_CombatLog.java` | Per-target combat statistics log, kept as a long-lasting copy of the skill | 543 |
| `Skill_BearForaging.java` | Racial food tracking; a state machine over `nextDirection`. Extends `StdAbility`, not `StdSkill` | 455 |
| `Skill_Convert.java` | Converts a mob or player to another deity, with a consent prompt for players | 455 |
| `Skill_CrowsNest.java` | Builds a look-out room above a ship's deck and reports distant sightings | 437 |
| `Skill_AbandonShip.java` | Evacuates crew and cargo to boats or overboard during ship combat | 433 |
| `Skill_ControlUndead.java` | Charms undead; the charmed mob will not attack its master | 349 |
| `Skill_Chirgury.java` | Removes body parts from a restrained target or a corpse (torture-flagged, see Content rules below) | 326 |
| `Skill_CombatRecall.java` | Auto-invoked passive that transcribes combat text into recallable segments | 321 |
| `Skill_BoulderThrowing.java` | Ship-to-ship siege throws, plus automatic NPC crew behaviour | 317 |
| `Skill_ArrestingSap.java` | Knock-out effect (sleeping status, "drowsy" blocks) | 315 |
| `Skill_BreakMount.java` | Turns a wild animal NPC into a rideable mount, with rollback on failure | 312 |
| `Skill_Buck.java` | Mount bucks riders off (also a `Behavior`) | 311 |
| `Skill_Burrow.java` | Digs a hidden room over 4 ticks; the room fills in when the effect ends | 303 |
| `Skill_CaravanTravel.java` | Route finding from numbered trade-chart points or a named destination | 279 |
| `Skill_BefoulShrine.java` | Defiles a room that holds a deity's infusion | 266 |
| `Skill_CollectBounty.java` | Hands a wanted person to a judge for payment | 263 |
| `Skill_BurrowHide.java` | Auto-invoked hiding in a burrow; rodent alarm | 259 |
| `Skill_ChildLabor.java` | Auto-invoked craft-timing passive that uses NPC children (see Content rules) | 243 |
| `Skill_AwaitClanShip.java` / `Skill_AwaitShip.java` | Sends an owned ship's course toward the shore | 234 / 209 |
| `Skill_CombatRepairs.java` | Temporary siege-combat hull repair | 226 |
| `Skill_Climb.java` | Climbs an exit, a direction or a ladder | 212 |
| `Skill_Autoclimb.java` / `Skill_Autocrawl.java` / `Skill_Autoswim.java` | Toggles that wrap Climb, Crawl and Swim during movement | 208 / 205 / 188 |
| `Skill_Cage.java` | Cages an animal NPC into a `GenCaged` item | 207 |
| `Skill_Arrest.java` | Uses warrants: applies the sap, then cuffs a sleeping target | 196 |
| `Skill_Bash.java` | Shield bash; a `ShieldWeapon` attack that can knock the target down | 175 |
| `Skill_ConsumeCorpse.java` | Heals and restores from a corpse | 173 |
| `Skill_AvoidCurrents.java` | Passive ship immunity to the `AWaterCurrent` tool | 171 |
| `Skill_Attack2.java` (+ `Skill_Attack3.java`, `Skill_AttackHalf.java`) | Extra-attack passives: speed bonus and a periodic attack-adjustment nerf | 168 (75, 75) |
| `Skill_AboveTheLaw.java` | Auto-invoked; pardons the holder's warrants on every tick | 136 |
| `Skill_BellyRolling.java` | Auto-invoked; a sitting mob may roll away from a weapon attack | 133 |
| `Skill_AllBreathing.java` | Auto-invoked; removes the "cannot breathe" sense flag | 112 |

## How it works

### 1. The base class and the constants

`StdSkill` overrides only the classification and targeting defaults. `classificationCode()` returns `ACODE_SKILL` (`com/planet_ink/coffee_mud/Abilities/Skills/StdSkill.java:53-56`), `canAffectCode()` returns 0 (`:59-62`), and `canTargetCode()` returns `CAN_MOBS` (`:65-68`). A skill therefore targets a mob by default and cannot be applied to a room or item. Subclasses change this: Burrow uses `CAN_MOBS|CAN_ROOMS` (`Skill_Burrow.java:65-67`), and Crow's Nest uses `CAN_ROOMS` (`Skill_CrowsNest.java:66-69`).

Constants used across the bucket, from `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java`:

- Usage bits: `USAGE_MANA=1`, `USAGE_MOVEMENT=2`, `USAGE_HITPOINTS=4` (`:622-626`). The default `usageType()` is `USAGE_MANA` (`Abilities/StdAbility.java:299-302`).
- Cost sentinels: `COST_ALL=Integer.MAX_VALUE`, `COST_PCT=Integer.MAX_VALUE-100`, `COST_NORMAL=-1` (`Ability.java:651-655`).
- Quality (the sign of the effect for an AI or a target): `MALICIOUS=0`, `INDIFFERENT=1`, `OK_SELF=2`, `OK_OTHERS=3`, `BENEFICIAL_SELF=4`, `BENEFICIAL_OTHERS=5` (`Ability.java:1083-1093`).
- Domains are `n<<5` values. Examples: `DOMAIN_SEATRAVEL=76<<5` (`:872`), `DOMAIN_LEGAL=70<<5` (`:860`), `DOMAIN_RACIALABILITY=64<<5` (`:848`).
- Flags: `FLAG_TRACKING=64` (`:958`), `FLAG_UNHOLY=1024` (`:966`), `FLAG_TORTURING=4294967296L` (`:1012`).

### 2. Proficiency: the roll every skill makes

`StdAbility.proficiencyCheck` (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:1441-1486`) is the single roll behind almost every skill in this bucket:

1. Auto-invoked use always succeeds and marks the effect as automatic (`:1443-1449`).
2. A mob with the SUPERSKILL security flag always succeeds (`:1455-1456`).
3. The base is `proficiency()`, plus any `PROF` ability adjustments from the character (`:1452`, `:1457-1465`).
4. Add `2*XLEVEL`, where XLEVEL is the expertise level for the `LEVEL` type (`:1467-1468`).
5. Clamp to 5..95 unless the `DIS955RULE` disable is set (`:1470-1476`).
6. Add `XLEVEL/2` (`:1478`).
7. A non-negative `adjustment` is added to the chance (`:1480-1481`). A negative `adjustment` is a separate gate: the check fails if `roll > 100+adjustment` (`:1482-1484`). For example, an adjustment of -15 fails outright 15% of the time before the normal roll.
8. Success if `roll < chance` (`:1485`).

The skill-specific adjustments are passed in. Examples: Control Undead uses `(casterLevel - targetLevel)*30` (`Skill_ControlUndead.java:305`), and Buck uses `2*STR - 3*avgRiderDEX + 2*XLEVEL` (`Skill_Buck.java:269-270`). Arrest and Arresting Sap use `-levelDiff` style penalties (see section 11).

Learning happens in `helpProficiency` (`StdAbility.java:1810-1871`), called from `invoke` (`:2077`). A use can raise proficiency by one point when a random value `round(sqrt(INT)*34*rand)` reaches the current proficiency (`:1837`), and then a gain roll against `ProfGainChance` passes (`:1839-1840`). The check is throttled to once per 300 seconds per ability (`:1827`). Players also get a "You become better at X." message when `AUTOIMPROVE` is set (`:1855-1856`).

### 3. The invoke pipeline and costs

Most skills follow the same order: validate and tell the player why not, call `super.invoke` (which deducts costs), roll `proficiencyCheck`, build a `CMMsg` and check `okMessage` before `send`, and on failure call a `*Fizzle` helper (for example `beneficialVisualFizzle` or `maliciousFizzle`).

`StdAbility.invoke` (`StdAbility.java:1964-2086`) does this for a non-automatic use:

1. Refuses if the mob is not alive, awake and mobile (`:1974`).
2. Cooldown: if `getTicksBetweenCasts()` is set and the next-cast time has not arrived, it says "You must wait @x1 before you can do that again." (`:1978-1989`).
3. Movement-costing skills refuse when bound: "You are bound!" (`:1991-1996`).
4. Computes `usageCost(mob,false)` (`:1998`). A compounding rule (`:2001-2046`) adds `amtPenalty` and then a `pctPenalty` share of the cost for each recent repeat in the window, and refuses with "You can't do that again just yet." when `amtPenalty<0` (`:2023`).
5. `testUsageCost` refuses short of mana, moves or hit points with messages such as "You don't have enough mana to do that." and "You must be at full mana to do that." (`:1925-1961`).
6. Minimum recovery time between uses (`:2053-2066`): "You need a second/few seconds/awhile to recover before doing that again."
7. `checkComponents` (`:2067`), then the costs are deducted (`:2074-2076`), `helpProficiency(mob,0)` runs (`:2077`), and a `MSG_PREINVOKE` message goes through `okMessage`/`executeMsg` (`:2078-2081`).

Cost formula (`StdAbility.java:1696-1807`, `buildCostArray` at `:1574-1681`):

- The base is the mana cost object for the ability (an integer or a compiled formula). A negative value means `50 + lowest*|value|` (`:1763-1781`). A floor is applied from `MANAMINCOST`, or `lowest*|N|` with a minimum of 5 when the setting is negative (`:1750-1762`).
- Each class level above the qualifying level reduces the cost: `consumed - (consumed/30)*diff` (`:1782-1784`).
- `overrideMana()` replaces the cost when it is 0 or above (`:1787-1788`). Convert returns 50 (`Skill_Convert.java:92-95`), Abandon Ship returns 100 (`Skill_AbandonShip.java:88-91`), and Befoul Shrine returns 100 (`Skill_BefoulShrine.java:98-101`).
- The split between mana, moves and hit points is divided across the usage types set in `usageType()` (`:1580`). `XLOWCOST` cuts 5% of the raw cost per level, and `XLOWFREECOST` adds a further reduction (`:1621-1636`).
- Per-character `HPCOST`, `MNCOST` and `MVCOST` ability adjustments add to each cost (`:1644-1676`).

### 4. Effects, durations and the tick loop

Skills that persist use `beneficialAffect` or `maliciousAffect`. Both copy the skill and mark the copy as un-invocable (`StdAbility.java:2185-2208`, `:2341-2349`). `startTickDown` (`:698-780`) sets `tickDown` and adds: a class duration modifier, and `addedTickTime` for non-crafting skills (`:705-714`). It also enforces `EFFECTCAP`, which removes a random earlier effect once the mob has too many (`:725-743`), and applies `TICK` ability adjustments (`:745-752`).

Duration when the caller passes no explicit tick count (`StdAbility.java:2312-2331`):

- The base is `casterLevel*4 + 40` ticks (`:2325`).
- When a player or a player-led group casts on someone else, the caster level is capped at the target's level plus `EXPRATE` (`:2317-2324`, `aPossibleAbuserOfCasterLevel` at `:2284-2310`).
- The result is clamped to at least 5 and at most `TICKSPERMUDDAY` (`:2273-2282`).
- A positive explicit tick count is used as given. Examples: Burrow's dig is 4 ticks (`Skill_Burrow.java:293`), Combat Log's copy is `Integer.MAX_VALUE/2` ticks (`Skill_CombatLog.java:526`), and Burrow's room lasts `TICKSPERMUDMONTH` (`Skill_Burrow.java:157`).

Each mob tick decrements `tickDown`, and at zero the effect is removed (`StdAbility.java:2843-2862`). `makeLongLasting()` sets `tickDown` to `Integer.MAX_VALUE` (`:2825-2828`), which makes an effect permanent until removed.

Tick length: `TIME_TICK` defaults to 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`), overridable with `TICKTIME` (`:2720`).

### 5. Auto-invocation

`autoInvocation` (`StdAbility.java:2368-2390`) attaches an auto-invoked skill to a mob at login, unless the mob's auto-invoke list excludes it (`:2375-2376`). The copy is un-invocable and not savable (`:2382-2385`). Eleven skills in this bucket are auto-invoked and un-invocable (Combat Recall, Boulder Throwing, Break Mount, Second Attack, Third Attack and Half Attack, which inherit from Second Attack, Burrow Hide, Child Labor, Above The Law, Belly Rolling and All Breathing), and three more are toggles (Autoclimb, Autocrawl, Autoswim).

### 6. Message hooks and two-phase messages

Skills use the two-phase `CMMsg` flow from the brief. The skill builds a message and asks the room's `okMessage` (for example `Skill_CombatLog.java:522-525`, `Skill_Convert.java:358-363`). Listeners can veto it, and then `send` runs `executeMsg` on all listeners. Passives hook `okMessage` or `executeMsg`. Burrow Hide reacts to movement, sound and speech by the hidden mob (`Skill_BurrowHide.java:162-230`). Crow's Nest uses `msg.addTrailerRunnable` to run its look-out after the look message resolves (`Skill_CrowsNest.java:140-198`, `:206-293`).

### 7. Passives that listen and report

**Combat Log** (`Skill_CombatLog.java`). The cast is `COMBATLOG <target>` (trigger at `:81`). It keeps a `CombatStat` enum of 15 counters (`:117-134`): combats, rounds, enemies fought and killed, XP, gold, seconds (total and combat), mana and moves used (total and in combat), damage done and taken, and healing done. It is updated from `executeMsg` (`:293-363`: death, damage, healing, XP, loot and faction changes) and from `tick` (`:368-429`). The report is gated by expertise level. For example, "Enemies per Combat" needs level 5 (`:165`), "Damage per second" needs 6 (`:209`), "Mana used/second" needs 7 (`:250`), and "Gold per game-hour" needs 10 (`:180`). Subcommands are `STOP`, `REPORT` (broadcast with `postSay`, `:485`) and `WRITE <target>` (hands the text to Skill_Write, `:489-507`).

**Combat Recall** (`Skill_CombatRecall.java`). Auto-invoked and un-invocable (`:207-216`). In `executeMsg` (`:124-167`), any message that is malicious, a death, a damage message, or involves a combatant is turned into text with `CMLib.coffeeFilter().fullOutFilter` (`:152`) and appended to `log` (capped at 500 lines, `:155-156`). A segment ends after `LOG_EXPIRE = CMProps.getTickMillis()*2` ms of quiet, so about 8 seconds at the default tick (`:57`, `:170-198`). The number of stored segments is `2 + XLEVEL/2` (`:193`). `COMBATRECALL LIST` shows segments with start and end times, and `COMBATRECALL <n>` replays one (`:221-284`; trigger at `:90`).

### 8. Combat-round skills

**Second, Third and Half Attack** (`Skill_Attack2.java`, `Skill_Attack3.java`, `Skill_AttackHalf.java`). These share one class with four overridable numbers: `attackToNerf()`, `roundToNerf()`, `nerfAmount()` and `numberOfFullAttacks()` (`Skill_Attack2.java:93-111`).

- Speed bonus while in combat: `speed += SpeedAdjustment * numberOfFullAttacks * proficiency/100` (`Skill_Attack2.java:121`).
- Counting: each `TYP_WEAPONATTACK` against a mob in combat increments `attacksSinceNerfing` (`:142`). When the count equals `attackToNerf` in the matching round, `freeToNerf` is set (`:143`). A 3% chance (`rollPercentage()>97`) raises proficiency when that happens (`:146-147`).
- Nerf: while `freeToNerf` holds, the mob's attack adjustment is multiplied by `nerfAmount` (`:122-123`).
- Reset: `tick` advances `roundOfNerfing` and resets the counters when it passes `roundToNerf` (`:159-165`).
- Values: Second Attack is 2 / 1 / 0.8 / 1.0 (`Skill_Attack2.java:93-111`). Third Attack is 3 / 1 / 0.6 / 1.0 (`Skill_Attack3.java:52-72`). Half Attack is 2 / 2 / 0.8 / 0.5 (`Skill_AttackHalf.java:52-72`).

**Boulder Throwing** (`Skill_BoulderThrowing.java`). Auto-invoked. A per-skill counter `tickDownThisShipCombatRound` starts at `CombatLibrary.TICKS_PER_SHIP_COMBAT` (`:93`), which is 4 ticks (`com/planet_ink/coffee_mud/Libraries/interfaces/CombatLibrary.java:87`), and it limits the mob to one throw per ship round (`:100-101`, `:289`). `tick` also automates NPC crew on siege ships: pick up ammunition, hold it, and throw when the ship is in combat and the round is ready (`:108-158`). `okMessage` intercepts a `TH*` command (`:173-182`) and checks the throw in order:

- "You've already thrown this round." (`:220-221`)
- Strength of at least 10: "You aren't strong enough." (`:223-224`)
- The ammunition must be remaining: "You can't throw that." (`:226-227`)
- Range: `weapon.minRange() .. weapon.maxRange()`, with max range `1+(STR-10)/2` (`:246`, `:249-253`).

Damage depends on the ammunition type: catapult boulders `10+X`, ballista bolts `5+X`, trebuchet shots `15+X`, and anything else `1+X/2`, where X is the expertise level (`:256-265`). The hit roll is made once per unit of distance, using `proficiencyCheck(-50 + DEX + 2X)` (`:290-291`).

**Combat Repairs** (`Skill_CombatRepairs.java`). Works on a `SiegableItem` that is subject to wear and tear, only while it is in siege combat (`:186-197`). Each tick outside combat costs the ship 5 uses (`:124-128`, message "The temporary combat repairs are slowly unraveling." at `:139`). The repair sets the ship's uses to `usesRemaining + 20 + DEX + 7*X`, capped at 100 (`:209-212`, `:219`). The pre-repair value is stored as the effect's ability code, so the effect knows when to end (`:213-218`).

### 9. Sea and ship skills

Ships are `Boardable` areas whose `getBoardableItem()` is a `NavigableItem` or `SiegableItem`. Checks use `R.getArea() instanceof Boardable` and `getBoardableItem()` (for example `Skill_CrowsNest.java:352-356`).

- **Abandon Ship** (`Skill_AbandonShip.java:118-431`). Requires a ship in combat (`:150-154`), a ship that has not sunk (`:156-160`) and privileges on the deck (`:143-148`). A mob with an open exit to another area can jump there (`:99-115`). Otherwise the crew is moved overboard, up to `adjustedLevel/2` people (`:193`, `:264-278`). If boats are present, they are loaded and lowered, and the rowers move the ship with up to `adjustedLevel/3` steps (`:194`, `:359-421`). The mob ends with its movement set to zero (`:422`).
- **Crow's Nest** (`Skill_CrowsNest.java:336-436`). Must be on the deck of a water-based ship, awake and standing (`:338-364`). The skill builds a `WoodenDeck` room above the deck and one `InTheAir` room per entry of `Directions.CODES()`, all linked down to the deck (`:401-412`). The nest holds one person through `Prop_ReqCapacity people=1` (`:392-393`). The nest's effect lasts through `beneficialAffect(mob, crowsR, asLevel, 0)`, which is the default duration of `casterLevel*4+40` ticks (`:426`). On expiry, `unInvoke` empties and destroys the nest rooms (`:300-333`). The look-out (`:206-293`) gives a fog message (`:218-221`), then reports what is visible in each room within `maxRadius = 1 + adjustedLevel/20 + XLEVEL/2 + XMAXRANGE` (`:226`). The message depends on how far the trail goes: "Directly <dir>, you see <list>" (length below 3), "Farther <dir>..." (length 3), or "Way off <dir>, you can barely see <list>" (longer) (`:264-281`). A landward room gives "Directly <dir>, you see Land!" (`:264`).
- **Await Ship** and **Await Clan Ship** (`Skill_AwaitShip.java:92-208`, `Skill_AwaitClanShip.java:92-233`). The skill finds a water-based ship the caller has privileges on, using a trail search of up to `30 + 5*XLEVEL` rooms (`Skill_AwaitShip.java:163`). The ship must not be in combat (`:174-177`). The course is set with `setCurrentCourse` and the anchor is raised (`:199-200`). The clan variant also requires a clan-owned ship (`Skill_AwaitClanShip.java:153-155`). A ship with a player aboard who passes the security check is skipped (`:162-168`). The captain's message applies when such a ship exists (`:223-224`).
- **Avoid Currents** (`Skill_AvoidCurrents.java:84-111`). A passive that intercepts an `AWaterCurrent` tool message aimed at the ship (`:88-92`). If the invoker on that ship passes `proficiencyCheck(0)` (`:99`), the current is cancelled with "<S-YOUPOSS> superior sailing skills keeps <T-NAME> from being swept away by currents." (`:105`).
- **Caravan Travel** (`Skill_CaravanTravel.java`). Works on land-based or wagon mounts in drivable rooms, or on a ship (`:98-114`). Destinations come from Trade Charting points, split on `;` (`:137`), or from a named area or room in full-navigation mode (`:178-204`). The trail is limited to 100 rooms (`:175`, `:201`). On a ship, the result is sent as a `MSG_HUH` with a `COURSE` string (`:257-263`). On land it is shown as "Your charts say the way there is: ..." (`:256`).

### 10. Movement helpers

- **Climb** (`Skill_Climb.java:103-210`). Resolves a direction, an exit or a ladder rideable (a `LADDER` basis becomes `UP`, `:117-134`). While the move happens, a temporary copy of the skill sets `IS_CLIMBING` (`:96-100`, `:179-196`). Proficiency is rolled twice (`:171`, `:176`). The first roll only decides the return value if the move is refused, which is an oddity (see Problems).
- **Autoclimb, Autocrawl, Autoswim** (`Skill_Autoclimb.java`, `Skill_Autocrawl.java`, `Skill_Autoswim.java`). Each is a toggle. Invoking it again removes it (`Skill_Autoclimb.java:187-189`). Otherwise it adds a long-lasting effect with `adjustedLevel` ticks (`Skill_Autoclimb.java:199-202`). While active, the skill catches a failed direction command (`TYP_COMMANDFAIL`) or a leave or flee message through `okMessage`. It then runs the underlying skill with a proficiency roll (`Skill_Autoclimb.java:108-145`). The roll fails with "You forgot to automatically climb." (`:141`). Each wrapper refunds part of the cost it just paid. Autoclimb's refund is `pts/2 + XLOWCOST + XLEVEL/2`, capped at `pts` (`Skill_Autoclimb.java:99-105`, `:127-131`). Autoswim uses `pts/2 + (XLOWCOST+XLEVEL)/2` (`Skill_Autoswim.java:99-103`). Autoswim only acts when either the room or the next room is watery (`:126`). Autocrawl refunds `usedMovement/2 + XLOWCOST + XLEVEL/2`, capped at the amount used (`Skill_Autocrawl.java:118-120`), then stands the mob up (`:127`).
- **Burrow** (`Skill_Burrow.java`). Digs a random free direction for an NPC (`:255-256`) or the direction the player names (`:259-276`). The dig takes 4 ticks (`:293`). It is aborted by combat, movement, not being awake, quitting or shutdown (`:112-119`, `:170-175`). A message says "almost done" at `tickDown==2` (`:120-122`). The room is built as a `CaveRoom` titled "A grimey burrow" with the description "You are in a dark dirty burrow!" (`:140-142`). It connects through a `HiddenWalkway` exit (`:147-148`), and "Your burrow has been dug to the <dir>!" is shown (`:158-159`). The room lasts `TICKSPERMUDMONTH` ticks (`:157`). When it expires it fills in: "The burrow fills itself in..." (`:198`). A digger loses hygiene each tick (`:129-133`). The room must be diggable (`isDiggableRoom`, `:88-102`): not an indoor room (except caves), not a city room, and not underwater or in the air.

### 11. Legal and warrant skills

Warrants come from the legal library. `Skill_Arrest.getWarrantsOf` asks the area's `LegalBehavior` for a target's warrants and drops any with crime "pardoned" (`Skill_Arrest.java:94-111`). The same logic is copied into `Skill_CollectBounty.java:93-110`.

- **Arrest** (`Skill_Arrest.java:131-195`). Requires a warrant in the area ("@x1 has no warrants out here.", `:142-146`). Cannot be used on oneself (`:136-140`). Weight gate: the target must not be more than 450 heavier (`:153-158`). The level-difference formula is shared with the sap (see below). On success the sap is applied with ability code 10 and auto mode (`:170-176`). If the target is asleep, the sap is removed, Handcuffs are added, and the caller is told to pull the target to the judge (`:177-187`).
- **Arresting Sap** (`Skill_ArrestingSap.java`). Auto-invoked form has a default of 3 ticks, and the words `SAFELY` and an integer can be passed (`:236-256`). Not usable on targets more than 450 heavier (`:267-271`), and needs a warrant (`:272-276`). The level penalty is `levelDiff = 3*(targetLevel - (casterLevel + 2*XLEVEL))` when positive, minus `abilityCode()*STR` (`:278-283`). Proficiency is rolled with `-levelDiff - (targetSTR - casterSTR)` (`:286`). The effect sets `IS_SLEEPING` (`:173`) and blocks eyes, hands, mouth and movement actions with "You are way too drowsy." (`:129-139`). With `SAFELY`, the target is made peaceful and wanders away on waking (`:141-161`, `:209-210`). The health condition reads "Unconscious" (`:113-115`).
- **Collect Bounty** (`Skill_CollectBounty.java:162-260`). The judge must be present (`:144-160`, `:175-181`), the target must have a warrant, and the target must not be dead or absent (`:183-193`). Pardoned targets are refused (`:194-202`). An eligible officer is looked up in the area and then the legal area (`:112-142`). The bounty is the sum over warrants of `punishmentCode * (5 + XLEVEL)` (`:243-250`), paid by the judge (`:253`). The target is handed to the officer and its warrant is set to `STATE_REPORTING` (`:237-245`).
- **Above The Law** (`Skill_AboveTheLaw.java:98-130`). Auto-invoked. On each tick the skill re-checks the area when the mob changes areas (`:108-117`). It sets every warrant the mob holds to crime "pardoned" with zero offences (`:124-129`). A non-savable copy needs a proficiency check first (`:114`).

### 12. Religion, mounts, animals and the body

**Convert** (`Skill_Convert.java`). The `deity` argument is looked up with `CMLib.map().getDeity`, falling back to the caller's own deity and then to a named target (`:153-175`). Animal-intelligence targets are refused (`:176-182`). The check is `proficiencyCheck(-(levelDiff*15))`, where `levelDiff = targetLevel - (casterLevel + 2*XLEVEL)` (`:209-217`). Converting a player goes through a confirm prompt with a 30-second timeout (`:292`). Accepting re-invokes with the target as the explicit target (`:314`). Each target can be converted once per 5 mud hours (`:196-198`, `:366`). The converter gains 1 XP for an NPC and 200 XP for a player (`:370-372`). An NPC's conversion lasts 3 real hours (`:376`). For NPCs, a roll of `d100 <= STAT_SAVE_DOUBT` converts the target (`:220`, `:333-334`). The name "targetMadeSave" means the roll resisted: a roll above the DOUBT save is a "doubt". The typed-command path passes `null` as the given target (`Libraries/EnglishParser.java:1343`), so the explicit-target path is not reached that way (see Problems).

**Control Undead** (`Skill_ControlUndead.java`). Only undead targets (`:271`) and only by a caster who is not "good" (`:277`). The caster level is `mob.level + area blurb level + 2*XLEVEL` (`:290-304`). The success gate (`:315`) requires the level difference to be greater than 1, or the target to be level 1. A controlled non-NPC gets a `maliciousAffect` (`:319`). A failed control makes the target submissive for 5 ticks (`:336-338`). While controlled, the target cannot attack its master ("You're too submissive towards <name>.", `:150-160`). When control ends, the message is "free-will returns" (`:226`). For an NPC, the mob either wanders away or takes the caster as its victim (`:232-242`).

**Befoul Shrine** (`Skill_BefoulShrine.java`). Targets a room that holds a deity's infusion (`:220-226`). The room effect lasts `hoursInDay*TicksPerMudHour + adjustedLevel/10 + XLEVEL` ticks (`:244-247`). While it lasts, the room refuses the deity's service start (`:148-157`) and prayer infusions (`:158-165`). Cleaning with `PURIFY` or `CLEAN` counts up, and from the third clean on the room may be restored at `cleans*5%` (`:176-181`). The caster loses `25 + XLEVEL` faction with the deity's `DEITY_<NAME>` faction (`:255-258`). The caster may not befoul their own deity's shrine (`:227-231`).

**Break Mount** (`Skill_BreakMount.java`). Intercepts a `TYP_MOUNT` message onto an animal-intelligence NPC (`:219-227`). The race or racial category must be a favoured mount for the rider (`:237-239`). Proficiency is checked (`:242`). The NPC is cloned into a `GenRideable`, copying its generated stats (`:245-247`), set to land-based (`:248`), and given a `Skill_Buck` (`:250-258`). The original is suspended and removed from the room (`:263-265`), and the mount is forced through the `Mount` command (`:267-272`). If the mount fails the clone is destroyed and the animal is restored (`:278-285`). The tick checks the rider each round. A mount with the `NO_AUTO_BUCK` ambience is made permanent (`:190-191`, `:108-141`). A rider who is off the mount for more than 10 ticks ends the mount (`:204-206`, `:143-164`).

**Buck** (`Skill_Buck.java`). The mount bucks off all of its riders. The check uses `adj = 2*STR - 3*avgRiderDEX + 2*XLEVEL` (`:260-270`). A rider who is thrown and not mounted is tripped for 2 ticks through `Skill_Trip` ("You hit the ground!", `:291-298`). As a `Behavior`, Buck automatically bucks NPC mounts with riders, unless the riders are in the mount's group, share its start room, or have property privileges (`:197-233`). A tamed animal gets `NO_AUTO_BUCK` (`:173-178`). Buying the mount removes the behaviour and adds `NO_AUTO_BUCK` (`:157-163`).

**Cage** (`Skill_Cage.java`). Finds a container with the caged flag in the room, or one the caller names (`:105-131`). The caller may cage an animal-intelligence NPC that is sleeping, cannot move, is following the caller, or is bound or held (`:140-145`). Otherwise the target must follow the caller's orders or the caller must have the ORDER security flag (`:136`), and the refusal is "@x1 won't seem to let you." (`:150`). The target is put into a `GenCaged` item (`:173-174`) and then killed (`:194`).

**Bash** (`Skill_Bash.java`). Needs a worn shield (`:102-115`, `:123-128`), and the target must be standing (`:130-134`). Damage is the shield's level plus `2*XLEVEL` (`:158`). On a hit to a leg, if the target is less than twice the caster's weight, it is knocked to sitting (`:159-165`).

**Chirgury** (`Skill_Chirgury.java`). Torture-flagged (`:83-86`). The target must be bound or held and asleep, and the caster must hold a piercing or slashing weapon (`:172-176`, `:198-204`). Parts are listed at `:88-100`. Some parts need a body part the race has (`:146-152`), and some races are excluded by category (`:102-104`, `:154-164`). Removing blood from a living target produces a liquid resource and deals damage (`:257`, `:264`). Organs come only from a corpse (`:178-182`). Removing the fetus (only where a pregnancy effect exists) makes a corpse item (`:285-315`). The already-removed check is broken (see Problems).

**Consume Corpse** (`Skill_ConsumeCorpse.java`). Eats a non-player corpse in the room (`:89-99`), or the corpse named by the caller. It refuses other players' corpses that still hold items (`:137-141`). The restoration fraction is `targetLevel / adjustedCasterLevel` (`:156`). HP is restored through `postHealing` (`:160`), and mana and moves are restored directly (`:161-163`). The corpse is destroyed (`:163`).

**Bear Foraging** (`Skill_BearForaging.java`). Extends `StdAbility`. Sets `FLAG_TRACKING` (`:101`), and invoking it removes any other tracking effect (`:392-400`). It searches for food in a radius of `3 + adjustedLevel/15 + XMAXRANGE` (`:421`) and tracks a trail to the food (`:423-435`). The trail's `nextDirection` sentinel values are: `>=0` a step to take ("The way home seems to continue <dir>.", `:264-266`); `999` and `-1` the trail has ended, so check for food and stop (`:236-262`); `-999` finished; and `-2` idle. Food can be taken from another mob's inventory (`:139`, "<S-NAME> suddenly attack(s) <T-NAME> ripping @x1 away from <T-HIM-HER>."), which marks it as theft. Food in cloth, glass, liquid or paper containers is torn open and the container destroyed (`:162-168`). Foraging hides the bear's trail from tracking through `CAN_NOT_TRACK` (`:334`).

**Racial and body utilities**: All Breathing clears `CAN_NOT_BREATHE` and sets an empty breathables array (`Skill_AllBreathing.java:101`, `:110`).

### 13. Content flagged by the brief

- **Chirgury** is a torture-flagged skill (`Skill_Chirgury.java:85`). It is documented above in neutral terms only.
- **Child Labor** (`Skill_ChildLabor.java:100-241`) makes NPC children in a room "toil" while a privileged character crafts. It is a timing mechanic that calls the craft's own `tick` every few ticks (`:203-237`). It contains no sexual content.
- **Pregnancy** appears only in Chirgury (`Skill_Chirgury.java:166`, `:285`) as a removable effect.

## Content worth mining

Catalogue of the 36 skills. Usage is the `usageType()` override, or "default" for `StdAbility`'s mana default. Notes come from the code.

| Class | Name | Domain | Quality | Usage | Notes |
|---|---|---|---|---|---|
| Skill_AbandonShip | Abandon Ship | SEATRAVEL | INDIFFERENT | MOVEMENT (cost override 100) | Evacuate crew and boats in ship combat |
| Skill_AllBreathing | All Breathing | (none) | BENEFICIAL_SELF | default | Auto; breathes anywhere |
| Skill_Arrest | Arrest | LEGAL | OK_OTHERS | MOVEMENT | Warrant-based sap and cuffs |
| Skill_ArrestingSap | Arresting Sap | LEGAL | MALICIOUS | MOVEMENT | Knock-out, 3 ticks by default |
| Skill_AboveTheLaw | Above The Law | LEGAL | BENEFICIAL_SELF | default | Auto; pardons all warrants each tick |
| Skill_Attack2 | Second Attack | MARTIALLORE | BENEFICIAL_SELF | default | Auto; speed bonus and nerf |
| Skill_Attack3 | Third Attack | MARTIALLORE | (inherited) | default | Nerf on the third attack, x0.6 |
| Skill_AttackHalf | Half Attack | MARTIALLORE | (inherited) | default | Nerf every other round, x0.8, speed x0.5 |
| Skill_Autoclimb | AutoClimb | FITNESS | OK_SELF | MOVEMENT | Toggle; wraps Climb, refunds cost |
| Skill_Autocrawl | AutoCrawl | FITNESS | OK_SELF | MOVEMENT | Toggle; wraps Crawl, refunds movement |
| Skill_Autoswim | AutoSwim | FITNESS | OK_SELF | MOVEMENT | Toggle; wraps Swim in water |
| Skill_AvoidCurrents | Avoid Currents | SEATRAVEL | INDIFFERENT | MOVEMENT+MANA | Ship immunity to current |
| Skill_AwaitShip | Await Ship | SEATRAVEL | INDIFFERENT | MANA | Summon an owned ship to the shore |
| Skill_AwaitClanShip | Await Clan Ship | SEATRAVEL | INDIFFERENT | MANA | Clan-ship variant |
| Skill_Bash | Shield Bash | SHIELDUSE | MALICIOUS | MOVEMENT | Shield attack, leg knockdown |
| Skill_BearForaging | Bear Foraging | RACIALABILITY | OK_SELF | default | Food tracking; extends StdAbility |
| Skill_BefoulShrine | Befoul Shrine | CRIMINAL | INDIFFERENT | MOVEMENT+MANA (cost override 100) | Defiles a deity's room |
| Skill_BellyRolling | Belly Rolling | FOOLISHNESS | BENEFICIAL_SELF | default | Auto; roll away while sitting |
| Skill_BoulderThrowing | Boulder Throwing | MARTIALLORE | BENEFICIAL_SELF | default | Auto; ship siege throws |
| Skill_BreakMount | Break Mount | ANIMALAFFINITY | BENEFICIAL_SELF | default | Auto; tame animal into a mount |
| Skill_Buck | Buck | RACIALABILITY | INDIFFERENT | MOVEMENT | Mount throws riders |
| Skill_Burrow | Burrow | RACIALABILITY | BENEFICIAL_SELF | default | Dig a hidden room in 4 ticks |
| Skill_BurrowHide | Burrow Hide | STEALTHY | BENEFICIAL_SELF | default | Auto; hidden while in a burrow |
| Skill_CaravanTravel | Caravan Travel | TRAVEL | INDIFFERENT | MOVEMENT+MANA | Route from trade-chart points |
| Skill_Cage | Cage | ANIMALAFFINITY | MALICIOUS | MOVEMENT+MANA | Cage an animal NPC |
| Skill_Chirgury | Chirgury | ANATOMY | INDIFFERENT | default | Torture-flagged; part removal |
| Skill_ChildLabor | Child Labor | LEGAL | BENEFICIAL_SELF | default | Auto; craft timing with NPC children |
| Skill_Climb | Climb | FITNESS | INDIFFERENT | MOVEMENT | Climb exit, direction or ladder |
| Skill_CollectBounty | Collect Bounty | LEGAL | OK_OTHERS | MANA | Judge pays for a wanted person |
| Skill_CombatLog | Combat Log | COMBATLORE | INDIFFERENT | MANA | Statistics logger with reports |
| Skill_CombatRecall | Combat Recall | COMBATLORE | INDIFFERENT | MANA | Auto; recall combat transcripts |
| Skill_CombatRepairs | Combat Repairs | SEATRAVEL | INDIFFERENT | MOVEMENT+MANA | Temporary siege hull repair |
| Skill_ConsumeCorpse | Consume Corpse | CORRUPTION | INDIFFERENT | MANA | Heal from a corpse; `FLAG_UNHOLY` |
| Skill_ControlUndead | Control Undead | DEATHLORE | MALICIOUS | default | Charm undead |
| Skill_Convert | Convert | EVANGELISM | INDIFFERENT | default (cost override 50) | Convert to a deity |
| Skill_CrowsNest | Crow's Nest | SEATRAVEL | INDIFFERENT | MOVEMENT+MANA | Look-out room above the deck |

Useful messages to paraphrase (short, from the code):

- "Directly <dir>, you see <list>." / "Farther <dir>, ..." / "Way off <dir>, you can barely see <list>." (`Skill_CrowsNest.java:264-281`)
- "It is too foggy to see anything else." (`Skill_CrowsNest.java:220`)
- "Your charts say the way there is: <dirs>" (`Skill_CaravanTravel.java:256`)
- "The burrow fills itself in..." (`Skill_Burrow.java:198`); "Your friends alert you to incoming danger! You quickly burrow out of sight." (`Skill_BurrowHide.java`, okMessage block)
- "You are way too drowsy." (`Skill_ArrestingSap.java:137`)
- "The way home seems to continue <dir>." (`Skill_BearForaging.java:266`); "You found a meal!" and "The trail dries up here." (`Skill_BearForaging.java:241-245`)
- "The temporary combat repairs are slowly unraveling." (`Skill_CombatRepairs.java:139`)
- "It's a little late to abandon ship .. it's already sunk." (`Skill_AbandonShip.java:158`)
- "You've already thrown this round." / "You aren't strong enough." (`Skill_BoulderThrowing.java:221, 224`)

## Notable design ideas

- **Verbs become effects with one shared check.** A skill is an ability with a quality, a cost mask and a proficiency. The same roll (`proficiencyCheck`) and cost code serve a deed (`Skill_Burrow.java:281`), a spell-like hit (`Skill_Bash.java:139`) and a passive (`Skill_Attack2.java:146`).
- **Toggles that wrap another verb.** Autoclimb, Autocrawl and Autoswim run Climb, Crawl or Swim during movement, then refund part of the cost (`Skill_Autoclimb.java:99-105`, `:127-131`). The refund makes the automatic version cheaper but not free.
- **A room that exists only while needed.** Burrow digs a `CaveRoom` linked by a hidden exit, lasts a mud month (`Skill_Burrow.java:157`), and fills in on expiry (`:198`). Crow's Nest does the same with a deck room and a ring of air rooms (`Skill_CrowsNest.java:401-412`, `:300-333`).
- **Distance phrasing from the route.** The look-out chooses "Directly", "Farther" or "Way off" from the length of the trail (`Skill_CrowsNest.java:264-281`).
- **Segmented combat memory.** Combat Recall closes a transcript segment after two quiet ticks (`Skill_CombatRecall.java:57`, `:170-198`), so each fight is one replayable entry.
- **Combat log as a moved effect.** The log object is copied and given `Integer.MAX_VALUE/2` ticks, and the counters are handed over by reference (`Skill_CombatLog.java:526-534`).
- **Mounts as cloned NPCs.** Break Mount clones the animal into a rideable with the same generated stats, and it rolls back if the mount fails (`Skill_BreakMount.java:245-285`).
- **Warrants as data with a pardon effect.** Above The Law rewrites each warrant's crime to "pardoned" every tick (`Skill_AboveTheLaw.java:124-129`), and every consumer filters pardoned warrants out (`Skill_Arrest.java:105-107`).
- **Consent prompts.** Convert asks a player to confirm, with a 30-second timeout (`Skill_Convert.java:292`), then re-invokes the skill with the target fixed (`:314`).
- **Negative proficiency adjustments as a gate.** A negative `adjustment` is a separate pre-roll that can fail regardless of skill (`StdAbility.java:1482-1484`). Boulder Throwing uses `-50 + DEX + 2X` per unit of range (`Skill_BoulderThrowing.java:291`).

## Problems and cruft

Verified by reading the code. Each entry names the defect and its effect.

1. **Mana copied as moves.** `Skill_CombatLog.java:426` sets `numMovesLastTick=mob.curState().getMana()`. It should be `getMovement()`. The "Moves used" statistics are therefore computed from the mana value.
2. **Crew list fills with the caster.** `Skill_AbandonShip.java:225` does `crew.add(mob)` inside the loop over awake mobs, where it should add `M`. The crew list then holds copies of the caster (`:198`, `:237` removes only one copy). The caster is "thrown overboard" several times (`:264-278`) and the real crew never moves.
3. **Only one boat is considered for crew.** `Skill_AbandonShip.java:328` tests `b==0`, so only the first boat is ever filled.
4. **Removed-part check never matches.** `Skill_Chirgury.java:185` and `:244` build the marker with `";"+parts[partCode]`, which concatenates an `Object[]`. The stored text is therefore `[Ljava.lang.Object;@...`, and the duplicate check at `:185` can never succeed.
5. **Wrong variable in ammunition copy.** `Skill_BoulderThrowing.java:281` checks the outer `Area A.isSavable()`, where it should check the ammunition effect `A1.isSavable()`. Non-savable ammunition effects are copied onto the weapon.
6. **Wrong variable in blurb parse.** `Skill_ControlUndead.java:295-299` uses `value` where the loop variable `s` is meant, so only the whole string is read.
7. **Guard that cannot fire.** `Skill_CollectBounty.java:169` refuses a second bounty if `mob.fetchEffect(ID())` exists, but the skill never creates that effect (`grep` finds no `Affect(` call in the file).
8. **Argument mismatch.** `Skill_Burrow.java:271` passes one argument to a message with two `@x1` placeholders, so the message shows the list twice and never the input.
9. **Wrong number in a message.** `Skill_ChildLabor.java:216` prints `kidTickMax` (the tick interval) where the count of children belongs.
10. **Doubtful duplicate check.** `Skill_CrowsNest.java:376` only checks for an existing nest when the deck room has no items (`R.numItems()==0`). Intent is unverified.
11. **Castings always indifferent.** `Skill_ArrestingSap.java:186-188` returns `QUALITY_INDIFFERENT` on both branches, so the warrant check changes nothing. Intent is unverified.
12. **Naming inversion in Convert.** `Skill_Convert.java:220` names a roll above the DOUBT save "targetMadeSave", which then means "doubted". The comment at `:218-219` says so too. Also, `givenTarget == target` forces the flag true (`:220`, `:282-283`, `:333-334`), so a caller that passes an NPC as the given target can never convert it. The typed-command path passes `null` (`Libraries/EnglishParser.java:1343`), so this path is only reached by programmatic callers. No such caller was identified.
13. **Double proficiency roll in Climb.** `Skill_Climb.java:171` and `:176` each roll. The first decides the return value only when the move message is refused.
14. **Copy-paste duplication.** `getWarrantsOf` is written twice (`Skill_Arrest.java:94-111`, `Skill_CollectBounty.java:93-110`). The sap and arrest level formula is also duplicated (`Skill_ArrestingSap.java:278-286`, `Skill_Arrest.java:159-167`).
15. **Unused or redundant code.** `Skill_CombatRecall.java:116-121` `getReport` returns only the header and is never used. `logLast` is set twice (`:142`, `:164`). `Skill_BoulderThrowing.java:267` sets ammunition remaining twice.
16. **Unclamped restoration.** `Skill_ConsumeCorpse.java:156` computes `targetLevel/casterLevel`, which exceeds 1 for a stronger corpse. Whether the mana and move adjustments cap at the maximum is unverified.
17. **Unlabelled mount values.** `Skill_BreakMount.java:204` and `:210` count ticks in an `int[]` with no names. This is readable only with the surrounding code.
18. **Dead-looking code.** `Skill_CrowsNest.java:116-118` has an empty `if` block in `tick`.

## Takeaways for a Python SUD

**What to borrow**

- **A skill as data plus one behaviour function.** About 30 of these 36 classes are "validate, roll, change one thing, say one line". In Python this is a table row and a small function. For example, in YAML or a dict:

  ```python
  @dataclass(frozen=True)
  class SkillDef:
      id: str                  # "burrow"
      domain: str              # "racial", "seatravel", "legal" ...
      quality: Quality         # MALICIOUS / INDIFFERENT / BENEFICIAL_SELF ...
      cost: Cost               # Cost(mana=0, moves=4, hp=0)
      auto: bool = False       # passive, applied on load
      run: Callable[..., Result] | None = None   # only for the odd ones
  ```

  Keep a `run` function only for the skills with real logic (Combat Log, Convert, Crow's Nest, Abandon Ship). Everything else can be data.
- **The proficiency roll, in Python form.** Port `proficiencyCheck` directly. It is small and it is the heart of the design:

  ```python
  def check(prof: int, xlevel: int, adj: int, rng) -> bool:
      pct = prof + 2 * xlevel
      pct = max(5, min(95, pct))            # clamp (StdAbility.java:1470-1476)
      pct += xlevel // 2
      if adj >= 0:
          pct += adj
      elif rng.randint(1, 100) > 100 + adj:  # negative adjustment is a pre-gate
          return False
      return rng.randint(1, 100) < pct
  ```

  Learning on use (`StdAbility.java:1810-1871`) is a useful pattern too: proficiency rises slowly with use, gated by a time throttle and a stat-based roll.
- **Costs as a small record.** Mana, moves and hit points as separate integers, with an optional per-skill override. Most skills use one or two of them.
- **Lasting effects with ticks.** The effect object with `tick_down`, un-invocation on expiry, and an effect cap is a clean model for a single-player world (`StdAbility.java:698-780`, `:2843-2862`).
- **Temporary rooms.** Burrow and Crow's Nest show how to add rooms that exist only while needed (`Skill_Burrow.java:138-161`, `Skill_CrowsNest.java:394-431`). In Python, a graph node with `expires_at_tick` and an `on_expire` hook covers both.
- **Route phrasing.** The look-out's "Directly / Farther / Way off" depends only on trail length (`Skill_CrowsNest.java:264-281`). It is easy to reuse.
- **Combat transcripts.** A ring buffer of recent combat lines, cut into segments after a quiet period, gives recall for free (`Skill_CombatRecall.java`).
- **Mounts and warrants as simple records.** A mount is a creature with a rider list. A warrant is `(crime, punishment, state)` plus a pardon flag (`Skill_CollectBounty.java:243-250`).

**What to simplify**

- Replace the attack family (`Skill_Attack2.java`, `Skill_Attack3.java`, `Skill_AttackHalf.java`) with one function. Something like `extra_attacks(count, round, nerf_at=2, nerf=0.8)` covers all three.
- Replace the combat-statistics enum and its report thresholds with a dict of counters and a table of unlock levels (`Skill_CombatLog.java:117-134`, `:145-290`).
- Replace the two-phase message veto with a simple event list. The veto is needed only for passives such as Avoid Currents and Burrow Hide.
- Keep the rolls and the simple table costs, and drop the compounding-cost machinery (`StdAbility.java:2001-2046`) unless the single-player design needs repeated-action penalties.

**What to drop or rethink**

- **Multiplayer assumptions.** Convert's consent prompt (`Skill_Convert.java:292-321`) has no meaning without other people. Combat Recall's use of other players' fights, Await Clan Ship's security check on players aboard (`Skill_AwaitClanShip.java:162-168`), Control Undead's quit handling (`Skill_ControlUndead.java:112-125`) and Caravan group travel all assume other humans. In single player, Combat Recall becomes a log of NPC fights the player witnessed, and Convert becomes a plain NPC conversion.
- **Deity and faction layers.** Befoul Shrine and Convert depend on deities and faction tables (`Skill_BefoulShrine.java:248-258`). Keep them only if the SUD needs religion as a system.
- **Torture-flagged and child-labour mechanics.** Chirgury (`Skill_Chirgury.java`) and Child Labor (`Skill_ChildLabor.java`) add nothing a text game needs. Drop them.
- **Ships.** The naval set (Abandon Ship, Crow's Nest, Await Ship, Combat Repairs, Boulder Throwing, Avoid Currents, Caravan Travel) is about 2,000 lines of Java for one feature. Keep one or two ideas (the look-out, the caravan route) if the SUD has travel.
- **Fix before copying.** Do not port the bugs listed above. Chirgury's duplicate check (`Skill_Chirgury.java:185`) and the crew-list bug (`Skill_AbandonShip.java:225`) would be the first to show up.

## Coverage

**Read in full** (all lines; a few blank lines and `@Override` markers omitted in the listings):

- `StdSkill.java` (69 lines)
- `Skill_CombatLog.java`, `Skill_BearForaging.java`, `Skill_Convert.java`, `Skill_CrowsNest.java`, `Skill_AbandonShip.java`, `Skill_ControlUndead.java`, `Skill_Chirgury.java`, `Skill_CombatRecall.java`, `Skill_BoulderThrowing.java`, `Skill_ArrestingSap.java`, `Skill_BreakMount.java`, `Skill_Buck.java`, `Skill_Burrow.java`, `Skill_CaravanTravel.java`, `Skill_BefoulShrine.java`, `Skill_CollectBounty.java`, `Skill_ChildLabor.java`, `Skill_AwaitShip.java`, `Skill_AwaitClanShip.java`, `Skill_CombatRepairs.java`, `Skill_Climb.java`, `Skill_Arrest.java`, `Skill_Bash.java`, `Skill_AvoidCurrents.java`, `Skill_Attack2.java`
- `Skill_BurrowHide.java`, `Skill_Autoclimb.java`, `Skill_Autocrawl.java`, `Skill_Autoswim.java`, `Skill_Cage.java`, `Skill_ConsumeCorpse.java`, `Skill_AboveTheLaw.java`, `Skill_BellyRolling.java`, `Skill_AllBreathing.java`, `Skill_Attack3.java`, `Skill_AttackHalf.java` (bodies read; blank lines and `@Override` lines filtered in the reading, so line numbers for these were taken with `grep -n`)

**Read in part:** `StdAbility.java` (3018 lines). Read in full: the sections on proficiency (`:1441-1486`), usage and cost (`:1574-1807`), `helpProficiency` (`:1810-1871`), `invoke` (`:1905-2088`), tick-down and effects (`:698-782`, `:2185-2210`, `:2273-2350`, `:2368-2390`), `tick` and `executeMsg` (`:2825-2865`), and the cost-related overrides (`:286-530`). Method list read for the rest (`grep`).

**Skimmed by grep only:** constants in `Abilities/interfaces/Ability.java`, `Libraries/interfaces/CombatLibrary.java:87`, `Libraries/interfaces/TimeManager.java:442`, `Common/interfaces/PhyStats.java`, `Common/interfaces/CharStats.java`, `Races/interfaces/Race.java:527-535`, `Libraries/EnglishParser.java:1343`, `Commands/*` (for how skills are invoked). Cross-referenced but not read: `Skill_Write`, `Skill_Trip`, `Skill_Swim`, `Skill_TradeCharting`, `Skill_HandCuff`, `Prop_ReqCapacity`, `Prayer_ReligiousDoubt`, and the legal, tracking and boardable libraries.

**Skipped:** none of the 36 manifest files. Unverified items are marked in the text.
