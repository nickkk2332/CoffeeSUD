# Fighter abilities (part 3): racial mounts, caravan escort, stances and combat maneuvers

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b061` (Abilities/Fighter [part 3/3: Fighter_RacialMount .. MonkSkill]) · 52 files · 9681 lines of Java.

Paths are relative to `com/planet_ink/coffee_mud/`, so `Abilities/Fighter/Fighter_Stance.java:35` means `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Stance.java`, line 35. Files outside this bucket are marked "(not in bucket)". Line numbers were read from the files; behaviour marked "by reading" was not executed.

## Summary

This bucket is the last third of the Fighter skill family. It covers a data-driven racial mount table, an escort system that lets mounted fighters guard a caravan or wagon, a three-stance combat system with a rock-paper-scissors counter rule, and about forty-five single-purpose maneuvers (trips, sweeps, knockdowns, shield and staff blocks, war cries, ranged tricks, mount care and grapples). Almost every maneuver uses the same three ingredients: a proficiency roll with a level-and-stat adjustment, a tick-counted effect on the target, and a veto or interception hook in the message pipeline. The stances, the caravan guards and the escort logic are the most distinctive pieces. The maneuver catalogue is useful as a source of ready-made combat verbs for a text game. The code also has several concrete bugs, listed under "Problems and cruft".

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| Abilities/Fighter/FighterSkill.java (not in bucket, read for base rules) | Base for all fighter skills: movement usage, CAN_MOBS, grapple helpers | 100 |
| Abilities/Fighter/Fighter_Stance.java | Three combat stances (Defensive, Offensive, Wild) with counter-stance rule | 346 |
| Abilities/Fighter/Fighter_RacialMount.java | Racial mount affinity: maps a race to the mount it rides; mount-handling message vetoes | 330 |
| resources/skills/racialmounts.txt | Data table race → mount category and default mount race (21 rows) | 21 rows |
| Abilities/Fighter/Fighter_Vanguard.java | Mounted escort effect on a caravan or wagon; rides to the vehicle when it is attacked | 416 |
| Abilities/Fighter/Fighter_RearGuard.java | Several mounted guards per vehicle; positioned by distance, rush in during attacks | 378 |
| Abilities/Fighter/Fighter_WeaponSharpening.java | Timed sharpening of a weapon; adds a damage bonus stored in the effect's misc text | 312 |
| Abilities/Fighter/Fighter_RopeTrip.java | Lasso trip: knocks a target to the floor for 2 ticks, veto on standing | 301 |
| Abilities/Fighter/Fighter_Whomp.java | Knockout: forces sleep for 2 ticks, cannot be woken by the victim's actions | 256 |
| Abilities/Fighter/Fighter_Sweep.java | Multi-target swing with a temporary attack and damage penalty on the swinger | 216 |
| Abilities/Fighter/Fighter_Runover.java | Caravan runs over a target; damage scales with weight ratio of vehicle to target | 262 |
| Abilities/Fighter/Fighter_SetPolearm.java | Braces a polearm against mounted charges; ×1.5 damage to mounted targets | 281 |
| Abilities/Fighter/Fighter_UnwaveringMark.java | Marks an enemy; when the mark attacks someone, the marker may counter and redirect it | 269 |
| Abilities/Fighter/Fighter_TendMount.java | Tending a mount: heals a percentage each tick and sometimes removes a harmful effect | 251 |
| Abilities/Fighter/Fighter_RidingFight.java | Pursuit: a mounted fighter follows a mounted opponent who flees through an exit | 192 |
| Abilities/Fighter/Fighter_ShieldBlock.java | Blocks weapon attacks; shield armour also added to the armour stat | 176 |
| Abilities/Fighter/Fighter_Tumble.java | Rolls out of hits; reduces own damage and attack, ends after 2 weapon hits | 199 |
| Abilities/Fighter/Fighter_Warcry.java | Group buff: +damage through an affect that ends 5 ticks after combat | 163 |
| Abilities/Fighter/Fighter_Rallycry.java | Group buff: +max HP per recipient; caster's HP is adjusted (see Problems) | 172 |
| Abilities/Fighter/Fighter_Toughness.java (+ Toughness2, Toughness3) | Passive: +HP from level and proficiency; II and III differ only in ID and name | 118 (+50, +50) |
| Abilities/Fighter/Fighter_Stonebody.java | Passive damage reduction from a proficiency-scaled amount (has a shadowing bug) | 138 |
| Abilities/Fighter/Fighter_Roll.java | Roll with blows: random share of incoming weapon damage removed | 151 |
| Abilities/Fighter/Fighter_RapidShot.java | Extra ranged attacks per tick in combat | 120 |
| Abilities/Fighter/Fighter_RapidReload.java | Chance to refund the action cost of loading ammunition | 114 |
| Abilities/Fighter/Fighter_TrueShot.java | Attack bonus for ranged and thrown weapons; occasional proficiency gain | 133 |
| Abilities/Fighter/Fighter_WristLock.java | Grapple that breaks a limb after four ticks if a strength roll succeeds | 239 |
| Abilities/Fighter/Fighter_SleeperHold.java | Grapple; chance per tick rises with hold duration | 132 |
| Abilities/Fighter/Fighter_SideKick.java, Fighter_SnapKick.java | Kicks made as generated natural weapons (GenWeapon) | 168, 137 |
| Abilities/Fighter/Fighter_SmokeSignals.java | Long-range message broadcast by smoke from a burning fire, blocked by weather | 230 |
| Abilities/Fighter/Fighter_ScoutAhead.java | Mounted scouting: moves ahead up to 1 + XLEVEL steps | 297 |
| Abilities/Fighter/Fighter_Rescue.java, Fighter_RideToTheRescue.java | Take an ally's blows; scoop an ally up onto one's mount | 156, 189 |
| Abilities/Fighter/Fighter_Shrug.java | One-shot brace: absorbs the next weapon hit | 161 |
| Abilities/Fighter/Fighter_SizeOpponent.java | Sizes up a target; reports its current hit points | 112 |
| Abilities/Fighter/Fighter_ViciousBlow.java | Passive critical damage percentage bonus | 114 |
| Abilities/Fighter/Fighter_StaffBlock.java, Fighter_StaffSpin.java, Fighter_StaffThrust.java, Fighter_StaffSweep.java | Staff family: blocks, spin debuff, thrust, sweep | 135, 196, 205, 193 |
| Abilities/Fighter/Fighter_Sweep.java family peers: Fighter_SweepingTrip.java, Fighter_RopeDisarm.java, Fighter_WeaponBreak.java, Fighter_WeaponCatch.java, Fighter_ReturnProjectile.java | Disarm, break, catch and projectile-return maneuvers | 198, 208, 184, 122, 175 |
| Abilities/Fighter/Fighter_Spring.java, Fighter_Ridethrough.java, Fighter_SootheMount.java | Spring attack, ride-through charge, mount calming | 162, 152, 180 |
| Abilities/Fighter/Fighter_UrbanTactics.java, Fighter_WaterTactics.java, Fighter_SwampTactics.java | Terrain tactics, each only a list of room-domain classes (base Fighter_FieldTactics is not in bucket) | 64, 64, 57 |
| Abilities/Fighter/MonkSkill.java | Empty base for monk-style kicks; adds `anyWeapons(mob)` | 57 |
| Abilities/Fighter/FighterGrappleSkill.java (not in bucket) | Base for the two grapples above | 502 |

## How it works

### Shared rules: skill check, expertise level and durations

Every skill here extends `FighterSkill` (`FighterSkill.java:36`). It is an action (`ACODE_SKILL`), uses movement points (`usageType()` returns `USAGE_MOVEMENT`, `FighterSkill.java:58-62`) and targets mobs (`FighterSkill.java:70-74`).

The success roll is `StdAbility.proficiencyCheck(mob, adjustment, auto)` (`Abilities/StdAbility.java:1441-1488`). Its steps:

1. Start from `proficiency()` (0-100), add any ability adjustments for the mob.
2. Add `2*XLEVEL`, clamp to 5..95 (unless the DIS955RULE security flag is set), then add `XLEVEL/2` (lines 1468-1476).
3. If `adjustment >= 0`, add it to the chance. If `adjustment < 0`, first roll `d100 > 100 + adjustment` and fail if so (line 1482-1483). This is a separate gate, so a negative adjustment of -35 means a flat 35% chance of failure before the proficiency roll.
4. Success is `d100 < chance`.

`XLEVEL` is read by `getXLEVELLevel(mob)` (`StdAbility.java:444-446`), which calls `expertise(...)` with `ExpertiseLibrary.XType.LEVEL` (`StdAbility.java:400-416`). That method returns 0 unless the ability is an auto effect, can be uninvoked, or is auto-invoked. The level is a per-mob, per-skill number from the expertise system, plus circumstantial ability adjustments from `CharStats`. Maneuvers that are one-shot and not cancellable therefore read XLEVEL as 0 (by reading).

Durations use two helpers. `getBeneficialTickdownTime` gives `casterLevel*4 + 40` ticks by default (`StdAbility.java:2312-2339`). `getMaliciousTickdownTime` gives `round(adjustedLevel*1.1) + 25` (`StdAbility.java:2174-2183`). Any positive second argument is used as an explicit tick count. Several maneuvers pass explicit counts: Whomp and RopeTrip pass `2` to `maliciousAffect` (`Fighter_Whomp.java:242`, `Fighter_RopeTrip.java:292`), SetPolearm and the sharpening effect pass explicit values (`Fighter_WeaponSharpening.java:297` passes 8).

Tick granularity is the mob tick, 4 seconds by default (`CMProps.java:681`, per the brief). Several effects here, however, use wall-clock milliseconds instead. See Problems.

### Racial mounts (Fighter_RacialMount)

Mount choice is data, not code. `getRacialMountChoices()` reads `resources/skills/racialmounts.txt` once, parses tab-separated rows of `race`, `mount category`, `mount race`, and caches the map as a shared resource (`Fighter_RacialMount.java:143-189`, cache key at lines 146 and 186). The first column may be a race ID, a racial category, or `*` for the default row. Rows with an unknown race or category are logged and skipped (lines 170-180).

`getMountInfo(mob)` picks the entry in this order (`Fighter_RacialMount.java:191-209`):

1. An entry keyed by the mob's race ID.
2. An entry keyed by the race's `racialCategory()`.
3. The `*` row.
4. The hard-coded default `("Equine","Horse")` (line 141).

A rider is considered mounted on their own kind when `isMount` holds: the rider is `riding()` the other mob and that mob's racial category equals the mount category from the table (`Fighter_RacialMount.java:211-215`). This is an affinity: a rider of the right kind gets the mount's details, and the skill itself is always on (`isAutoInvoked()` true, `canBeUninvoked()` false, lines 100-110).

The effects are narrow and message-driven, all in `okMessage` (`Fighter_RacialMount.java:243-313`):

- A mounted rider may give an item to an unmounted mob, and the message is forced through with `CMMsg.MASK_ALWAYS` (lines 272-284).
- A mounted rider may pick up a floor item it could not otherwise reach (lines 286-295).
- A mounted rider receiving an item from a mount-matched mob has the transfer forced through (lines 298-310).
- When the mount leaves the room with a "Go" command, the mount's speed is multiplied by 4 for 250 ms of wall-clock time (lines 217-225, 316-329). The window is set by `lastLeave` in `okMessage` (lines 258 and 262-266).

`text()` returns `"<category>,<race>"` for the mob's current mount (lines 119-128), and the display text reads "(Affinity for @x1 mounts)" (lines 58-73).

### Caravans and wagons: Vanguard and Rearguard

Both are effects placed on a vehicle item (an `Item` that is `Rideable` with `LAND_BASED` or `WAGON` basis, or a `NavigableItem` with the same bases). The effect's `tick()` moves the vehicle or its guards. A mounted fighter invokes the skill on the vehicle to volunteer (`Fighter_Vanguard.java:295-415`, `Fighter_RearGuard.java:290-377`). The vehicle must have a non-animal driver aboard, or, for a navigable vehicle, a non-animal mob in the area who can steer it (`Fighter_Vanguard.java:317-371`).

Eligibility checks in `invoke`:

- A vehicle cannot have the same fighter as both vanguard and rearguard (`Fighter_Vanguard.java:373-381`, `Fighter_RearGuard.java:339-347`).
- The volunteer must be mounted (`Fighter_Vanguard.java:383-387`, `Fighter_RearGuard.java:334-338`).
- The `stop` argument releases the post (`Fighter_Vanguard.java:298-316`, `Fighter_RearGuard.java:292-317`).

**Vanguard** (single guard per vehicle, `Fighter_Vanguard.java` fields lines 90-98): `vanGuard` is a `Pair<MOB,Integer>`. The guard's trail distance is `2 + floor(XLEVEL/4)` (line 405). The `tick()` has two modes:

- If the vehicle was attacked within the last 4000 ms (`lastAttack`, line 123), and the guard is mounted or in the vehicle's room, the guard walks toward the vehicle along a path found by `CMLib.tracking().findTrailToAnyRoom` (lines 139-174). It tries up to 10 steps per tick (line 149), and uses "FLEE" for each step if in combat (lines 164-171). The message "is under attack!" is shown (line 148).
- Otherwise, if the vehicle is not in the guard's room, the driver is ordered to move the vehicle toward the guard, with the path turned into direction commands `COURSE` (navigable) or `GO` (other), lines 178-231.

Interception is in `okMessage` (`Fighter_Vanguard.java:236-274`). An `AmmunitionWeapon` attack or noisy movement aimed at the vehicle, when the guard is in the same room and mounted, is blocked with probability `10 + XLEVEL` percent (line 257). The message is "<S-NAME> harass(es) <T-NAME>, preventing <T-HIS-HER> attack on @x1." (line 261).

**Rearguard** (several guards per vehicle, `Fighter_RearGuard.java:90` `rearGuards` is a `SHashtable<MOB,Integer>` mapping each guard to a distance): the distance is `floor(XLEVEL/4)` (line 367), with no base offset. In steady state each guard stands within its own distance of the vehicle, computed as `getRadiantRooms` (lines 141-144 and 169-172). In a combat run, every guard heads into the vehicle's room (lines 169-170). A guard who has dismounted and is away from the vehicle resigns, and the effect ends if it was the last guard (lines 147-161, 177-187, 202-212). Interception in `okMessage` (lines 233-269) uses the same `10 + XLEVEL` percent check, over every guard in the vehicle's room.

### Stances (Fighter_Stance)

Stance is a single effect on a mob, with the stance name stored in misc text (`Fighter_Stance.java:188-197`). The enum has `None`, `Defensive`, `Offensive` and `Wild` (lines 51-57). Changing stance is only possible in combat (line 277-281). The change is a quiet movement message and a proficiency check with no adjustment (lines 316-343). When a monster's command is not a stance name, it picks a random stance that is neither None nor its current one (lines 283-294), and an NPC with no stance text is assigned one at random on auto-invocation (lines 255-270). Stance effects are auto-invoked while in combat (`isAutoInvoked`, lines 88-99) and cannot be cancelled (line 102-105).

The modifiers are computed in `affectPhyStats` (lines 200-249). Let `X = XLEVEL` and `exp = X/100`, and let `baseArmor = armor - 100` (line 219; the code treats 100 as the unarmored value):

| Stance | Attack | Damage | Armour |
|---|---|---|---|
| Offensive | × (1.1 + exp) (line 227) | unchanged | armor - round(baseArmor × (0.05 + exp/2)) (line 228) |
| Defensive | × (0.95 - exp/2) (line 233) | unchanged | armor + round(baseArmor × (0.1 + exp)) (line 234) |
| Wild | × (0.95 - exp/2) (line 242) | × (1.1 + exp/2) (line 241) | armor - round(baseArmor × (0.05 + exp/2)) (line 243) |
| None | unchanged | unchanged | unchanged |

Armour is treated as a value where lower is better: Defensive moves the number down (more protection), Offensive moves it up toward or past 100 (less protection). The code comments at lines 228 and 234 say "actually adds" and "actually subs" for this reason. This reading is an inference from the arithmetic, not a documented rule.

Counter rule. Each stance loses its bonus when the opponent holds a specific counter stance. These are the `return` branches in `affectPhyStats`:

- Offensive loses its bonus against Defensive (lines 224-226).
- Defensive loses its bonus against Wild (lines 230-232).
- Wild loses its bonus against Offensive (lines 238-240).

The counter stance is read from the opponent's stance effect when both mobs target each other (lines 210-218). `lastVictimM` and `lastVictimStancerA` are refreshed in `tick()` whenever the victim changes, and `recoverPhyStats()` is called (lines 166-185). `castingQuality` makes NPCs prefer the stance that beats the opponent: Defensive against Wild, Offensive against Defensive, Wild against Offensive (lines 121-159).

### Combat maneuvers

Most maneuvers share one pattern in `invoke`: check range and state (`rangeToTarget()`, `isInCombat()`, sitting or sleeping, weapon class), compute a contest adjustment from levels and stats, call `proficiencyCheck` with that adjustment, post a `CMMsg` with `MSK_MALICIOUS_MOVE` or `TYP_WEAPONATTACK` to the room, and then apply a `maliciousAffect` or `beneficialAffect` on the target. A failure calls `maliciousFizzle` or `beneficialWordsFizzle`. The numbers below are the ones the code uses.

**Trips and knockdowns**

- *Rope Trip* (`Fighter_RopeTrip.java`). Needs a rope held or wielded, or an automatic hemp lasso for mobs (`getRope`, lines 195-213). `levelDiff` is 5 times how far the target's level exceeds `mob.level + 2*XLEVEL`, or 0 (line 273-277). `levelDiff -= abilityCode() * mobDEX` (line 278). The adjustment is `-levelDiff - (35 + round((targetDEX - 9) * 3))` (line 279). Success also needs the target to have a leg (line 281). The target is put down with `maliciousAffect(...,2,-1)` (line 292), which keeps the target sitting (`affectPhyStats`, lines 118-124) and vetoes its stand attempts (lines 136-137) until the effect ends. The effect's `unInvoke` posts "regain(s) <S-HIS-HER> feet" (line 155). With equal levels and target DEX 9, the gate is a roll ≤ 65 and then a roll below proficiency, so success is roughly 0.65 times proficiency.
- *Whomp* (`Fighter_Whomp.java`). A knockout. `levelDiff` is 10 times the target's excess level (lines 223-227). The adjustment is `-levelDiff - (targetSTR - mobSTR)` and the attack also needs `rollToHit` (lines 229-230). The effect forces `IS_SLEEPING` on every recalculation (`affectPhyStats`, lines 129-150) and lasts 2 ticks (line 242). The victim is told "You are way too drowsy." (line 121) when trying to act.
- *Sweeping Trip, Staff Sweep, Sweep*. Each needs at least two targets in range (`Fighter_Sweep.java`, lines 103-109 for the penalty). The Sweep ability copies itself onto the mob as a non-savable effect (`copyOf`, `setSavable(false)`, `addEffect`, lines 188-204), so the attack penalty applies only during the multi-target swing. The penalty is attack / (2 - 0.1*X) and damage / (3 - 0.1*X) (lines 106-108). Each target then gets `CMLib.combat().postAttack` (line 199). The effect is removed in a `finally` block (line 205).
- *Staff Spin*. Adds `round(weapon damage × (1 + X/10))` to the armour value of everyone who can see the spinner (`Fighter_StaffSpin.java:113-117`), so viewers become easier to hit. The effect's label is "(dazzled by a staff)".
- *Spring Attack, Staff Thrust, Ride-Through, Sweeping Trip*. Range gating and state checks only, as far as read (grep-level). Staff Thrust sets `maxAttacks = XLEVEL` when applied (`Fighter_StaffThrust.java:114-125`). Ride-Through overrides `maxRange()` to 99 (`Fighter_Ridethrough.java:96-98`), i.e. it works at any range along the line of the charge.

**Disarm, break and catch**

- *Rope Disarm* uses the lasso to disarm an opponent from range 1 (`maxRange`, `Fighter_RopeDisarm.java:132-134`). The lasso is a generated item, "a lasso" (lines 124-125).
- *Weapon Break* targets a weapon at range 1 (`Fighter_WeaponBreak.java:77-79`). Messages include "@x1 is not wielding a weapon!" (line 139).
- *Weapon Catch* is a passive, triggered on a disarm attempt at range 0. The disarmed weapon is caught by the mob with a proficiency check (`Fighter_WeaponCatch.java:105-112`, messages on lines 110-112).
- *Return Projectile* is a passive that catches and returns ranged or thrown weapon attacks aimed at the mob from range greater than 0. It needs two free hands (`BODY_HAND > 1`) and a free held-item slot, and passes a proficiency check of `-85 + DEX + 2*X` (`Fighter_ReturnProjectile.java:108-116`). The caught item is placed on the floor (line 139).

**Defence and damage reduction**

- *Shield Block* (`Fighter_ShieldBlock.java`). Passive. When the mob's victim makes a weapon attack, a proficiency check with adjustment `DEX - 90 + 2*X` (line 110) can block it. The block replaces the attack ("<T-NAME> block(s) <S-YOUPOSS> attack with <O-NAME>!", line 113). The armour bonus is the sum of worn shield armour values (computed in `tick` when reset, lines 136-156), subtracted at `X*0.5` of that sum from the armour stat (line 166).
- *Staff Block* (`Fighter_StaffBlock.java`). Requires a wielded staff and an attacker within the staff's reach (lines 111-117). Each round, up to `speed + X/3` tries (line 124) with a proficiency check of `DEX - 93 + X` (line 125).
- *Shrug Off* (`Fighter_Shrug.java`). Braces: absorbs the next weapon hit, with message "shrug(s) off the attack from <T-NAME>." (line 108). It ends on that hit (`unInvoke` then veto, lines 105-112).
- *Tumble* (`Fighter_Tumble.java`). While in combat, damage and attack are reduced by `1/(2 + 0.2*X)` (lines 106-107). Each weapon hit that would land is turned into no effect with a trailer message "tumble(s) around the attack" (lines 113-146). The effect ends after two such hits (line 135-136) or when the mob leaves combat.
- *Stone Body* (`Fighter_Stonebody.java`). Passive. At range 0, a proficiency check of `-85 + CON` (lines 111-112) lets the mob reduce incoming weapon damage by `round(proficiency/100 × (2 + 0.15*X))` (lines 114-116). See Problems for the bug that prevents its proficiency gain.
- *Roll With Blows* (`Fighter_Roll.java`). Similar, but random. The amount removed is `round(damage × proficiency/100 × Math.random())` (lines 118-120), on a check of `-85 + DEX + 2*X` (line 115).
- *Toughness*, *Toughness II*, *Toughness III*. Passive HP. Current max HP is raised by `30 + round(2 × adjustedLevel × proficiency/100)` (`Fighter_Toughness.java:97`). The skill improves with a 5% chance per mob tick in combat (line 111). II and III are subclasses with only ID and name changed.

**Shouts and group buffs**

- *War Cry* (`Fighter_Warcry.java`). For each target in range, an affect adds `1 + round(damage/(4 - 0.1*X))` to the weapon damage stat (line 99). The affect ends after more than five ticks out of combat (`timesTicking`, tick, lines 103-112). Message: "<S-NAME> scream(s) a mighty WAR CRY!!" and "get(s) enraged!".
- *Rally Cry* (`Fighter_Rallycry.java`). Recipients get `hpUp = caster level + 2*X` added to max HP through `affectCharState` (lines 94-100, 158). Each recipient's effect is removed after five ticks out of combat. Messages: "scream(s) a mighty RALLYING CRY!!", "seem(s) rallied!", "You feel less rallied." See Problems for where the healing lands.

**Ranged**

- *Rapid Shot* (`Fighter_RapidShot.java`). In `tick`, while the mob is in combat with a wielded ranged ammunition weapon in range, the mob posts `1 + floor(adjustedLevel/16)` extra attacks, bounded by remaining uses (lines 98-118).
- *Rapid Reload* (`Fighter_RapidReload.java`). When a `TYP_RELOAD` loads ammunition into a weapon, with a proficiency check and a `d100 < 20 + 5*X4` roll, the action cost of the LOAD command is refunded (lines 96-112; `getX4Level`).
- *True Shot* (`Fighter_TrueShot.java`). For a wielded ranged or thrown weapon, adds `attack × proficiency/(200 - 10*X)` to attack (lines 96-108). On each weapon attack, a 5% roll (`> 95`) gives a proficiency gain (lines 127-131).
- *Vicious Blow* (`Fighter_ViciousBlow.java`). Passive: adds `50 + 5*X` to `STAT_CRIT_DAMAGE_PCT_WEAPON` (lines 108-112).

**Mounted tactics and mount care**

- *Runover* (`Fighter_Runover.java`). The driver of a caravan picks a target in the same room. Damage is `d(1, 6 + X) × weights` (line 146), where `weights = (vehicle weight + puller weights) / target weight` in integer arithmetic (lines 141-145). A target heavier than the caravan therefore takes no damage. Message: "<O-NAME> run(s) over <T-NAME>!" (line 139). A caravan not in motion is refused with "You must be driving a caravan to run someone over." (line 186).
- *Set Polearm* (`Fighter_SetPolearm.java`). While set, a polearm multiplies damage taken from a mounted attacker by 1.5 (`Fighter_SetPolearm.java:125`, ×1.5 on `TYP_DAMAGE` when the target is riding). The mob cannot advance or retreat while set ("stand(s) firm.", line 113). When an opposing mounted attacker advances into the polearm's maximum range, the setter invokes Fighter_DismountingBlow on that attacker (lines 152-160). The set ends on room change (line 127-129). Setting is gated by one proficiency check (line 264), with message "set(s) <T-NAME>!" at line 268.
- *Riding Fight* (`Fighter_RidingFight.java`). When the mob leaves a room through an exit while in combat with a mounted opponent, it may pursue. The check is `d100 + level + 2*X > save vs TRAPS` and `> save vs MIND` (lines 143-145). The opponent is walked into the reverse exit (line 151-155). A pursuer that is a monster and is away from its start area is marked to wander home later, after `CMProps.getTicksPerHour()/4` ticks (`Fighter_RidingFight.java:173-181`, by reading).
- *Ride To The Rescue* (`Fighter_RideToTheRescue.java`). Scoops an ally onto the rider's mount. The ally must be in the group (line 131) and not be too large (line 136). Messages on lines 103-136.
- *Tend Mount* (`Fighter_TendMount.java`). A timed care effect on a mount. Each tick it heals `(0.02 + 0.005*X) × maxHP`, capped at the amount missing (lines 165-175). Each tick it also has a `5 + X` percent chance to end one random offensive effect on the mount (lines 177-181). It stops when the caretaker enters combat, leaves the room, or the cast quality falls to indifferent (lines 150-157). Progress shows as "continue(s) tending to <T-NAME> (@x1)." (lines 161-163).
- *Soothe Mount* (`Fighter_SootheMount.java`). Calms an animal mount (`CAN_MOBS` target). Messages include "is a bit too powerful to soothe" (line 144) and "is not the sort that would heed you" (line 153). Details were not read in full.
- *Scout Ahead* (`Fighter_ScoutAhead.java`). Mounted only, and not in combat (lines 167-174). Scouting limit is `1 + X` moves (lines 189-229). Direction is either named or a random one (line 185).
- *Smoke Signals* (`Fighter_SmokeSignals.java`). Needs a burning fire item in the room (`getRequiredFire`, lines 124-141, "A fire will need to be built first." at 138). The climate's weather must not be blizzard, duststorm, hail, rain, sleet, snow or thunderstorm (lines 156-173, 203-210). The message reaches rooms within a radius of `20 + X + 2*XMAX` rooms (line 201) by `getRadiantRooms` (line 202). Recipients see "You see some smoke signals in the distance." (line 199) and the text is the caster's message.
- *Rescue* (`Fighter_Rescue.java`). Takes over the monster's attacker: `monster.setVictim(mob)` after a successful proficiency check (lines 131-140). Messages: "rescue(s) <T-NAMESELF>!" and "attempt(s) to rescue <T-NAMESELF>, but fail(s)."
- *Unwavering Mark* (`Fighter_UnwaveringMark.java`). Marks an enemy. When the mark makes a malicious attack on someone other than the marker, a trailer runnable (lines 139-165) makes the marker attack the mark with `postAttack`, and then with chance `proficiency/2 + 5*X` (default 50 if the marker has no skill record) the mark's victim is switched to the marker (lines 152-161). The mark is cleared on its death (lines 126-131). Invoking the skill sets the mark, with level and ability-code difficulty `levelDiff = targetLevel - (mobLevel + abilityCode + 2*X)` (line 239) and a proficiency adjustment of `-levelDiff` (line 243). Message: "switch(es) <S-HIS-HER> attention to <T-NAME>." (line 157).

**Grapples and natural weapons**

- *Wrist Lock* (`Fighter_WristLock.java`). The chance is `10 × (invoker STR - target STR)`, at least 10, or 50 with no invoker (lines 74-84). After four ticks held (`tickUp >= 4`, line 96), a roll below that chance breaks a limb: a `BrokenLimbs` effect is added to the target (lines 96-100).
- *Sleeper Hold* (`Fighter_SleeperHold.java`). Each tick the chance is `10 × tickUp + X` percent (line 77) to put the target out (line 79).
- *Side Kick* (`Fighter_SideKick.java`) and *Snap Kick* (`Fighter_SnapKick.java`). A kick is a generated `GenWeapon` named "a side kick" or "a snap kick", bone material, bash damage 5, and 1000 uses that are reset to 100 per kick (`Fighter_SideKick.java:126-138`). Each kick has a 5% chance (roll > 95) to improve the skill (line 126). The kick is only made at range 0 against the current victim.

**Terrain tactics and monk base**

- *Urban, Water and Swamp Tactics* each define `landClasses` as a list of room domains (for example `DOMAIN_OUTDOORS_SWAMP` only for Swamp; four underwater and water-surface domains for Water). The shared effect is in `Fighter_FieldTactics` (not in bucket).
- *MonkSkill* is an empty base; its only addition is `anyWeapons(mob)`, true if a weapon or held item is present (`MonkSkill.java:45-50`).

### Messages shown to players (selected)

| Situation | Message (paraphrased in the table, exact in code) | Where |
|---|---|---|
| Stance changed | "<S-NAME> get(s) into a @x1 fighting stance." | Fighter_Stance.java:327 |
| Stance not in combat | "You must be in combat to change your stance." | Fighter_Stance.java:279 |
| Vanguard volunteers | "<S-NAME> volunteer(s) to be the vanguard for <T-NAME>" | Fighter_Vanguard.java:395 |
| Vanguard harass | "<S-NAME> harass(es) <T-NAME>, preventing <T-HIS-HER> attack on @x1." | Fighter_Vanguard.java:261 |
| Rearguard under attack | "@x1 is under attack!" | Fighter_RearGuard.java:190 |
| Rope trip lands | "<S-NAME> trip(s) <T-NAME> with @x1!" then "You hit the floor!" | Fighter_RopeTrip.java:285, 293 |
| Whomp lands | "<S-NAME> knock(s) <T-NAMESELF> to the floor!" | Fighter_Whomp.java:234-235 |
| Runover | "<O-NAME> run(s) over <T-NAME>!" | Fighter_Runover.java:139 |
| Polearm set | "<S-NAME> set(s) <T-NAME>!" | Fighter_SetPolearm.java:268 |
| Sharpening complete | "You have finished sharpening <T-NAME>." | Fighter_WeaponSharpening.java:174 |
| Weapon no longer sharp | "<T-NAME> no longer seem(s) quite as sharp." | Fighter_WeaponSharpening.java:184 |
| Smoke signal heard | "You see some smoke signals in the distance." | Fighter_SmokeSignals.java:199 |
| Tumble | "<S-NAME> tumble(s) around the attack from <T-NAME>." | Fighter_Tumble.java (trailer message) |
| Unwavering mark challenge | "<S-NAME> challenge(s) <T-NAMESELF> with <S-HIS-HER> unwavering mark." | Fighter_UnwaveringMark.java:247 |

Several messages are contextual and show exact wording only in the source. The table gives the substance.

## Content worth mining

**Racial mount table** (`resources/skills/racialmounts.txt`). Each row maps a race to a mount category and a default mount:

| Race | Mount category | Default mount |
|---|---|---|
| (default) | Equine | Horse |
| Human | Equine | Horse |
| Beastkin | Canine | DireWolf |
| Drow | Arachnid | GiantSpider |
| Duergar | Porcine | DireBoar |
| Dwarf | Ursine | DireBear |
| Elf | Feline | DireCat |
| Githyanki | Equine | Horse |
| Gnoll | Canine | DireWolf |
| Gnome | Mustelid | DireOtter |
| Goblin | Rodent | DireRat |
| HalfElf | Equine | Horse |
| Halfling | Equine | Horse |
| LizardMan | Reptile | GiantTurtle |
| Merfolk | Fish | GiantSeahorse |
| Ogre | Pachyderm | Elephant |
| Orc | Canine | DireWolf |
| Pixie | Feline | GreatCat |
| Svirfneblin | Elemental | EarthElemental |
| Tiefling | Equine | Horse |

The idea is a single mount species per race with a fixed default, useful for a Python game's fantasy races.

**Maneuver catalogue.** Columns: class, trigger words, quality, domain, usage, key mechanic. Usage "none" means a passive or an effect that costs no movement.

| Class | Trigger words | Quality | Domain | Usage | Key mechanic |
|---|---|---|---|---|---|
| Fighter_Vanguard | VANGUARD | ok others | TRAVEL | none | Mounted escort of a vehicle, 10 + X % intercept |
| Fighter_RearGuard | REARGUARD | ok others | TRAVEL | none | Several guards per vehicle, distance floor(X/4) |
| Fighter_Stance | STANCE | beneficial self | MARTIALLORE | none | Three stances, rock-paper-scissors counter |
| Fighter_RacialMount | (auto) | indifferent | ANIMALAFFINITY | none | Race-to-mount table |
| Fighter_WeaponSharpening | WEAPONSHARPENING, SHARPEN | indifferent | WEAPON_USE | MANA | 8 ticks, bonus `round((0.1+0.1X) × base damage)` |
| Fighter_RopeTrip | ROPETRIP, RTRIP | malicious | BINDING | MOVEMENT | Lasso trip, 2 ticks down |
| Fighter_Whomp | WHOMP | malicious | DIRTYFIGHTING | MOVEMENT | Knockout, 2 ticks asleep |
| Fighter_Sweep | SWEEP | malicious | MARTIALLORE | MOVEMENT | Multi-target swing with self penalty |
| Fighter_SweepingTrip | SWTRIP, SWEEPINGTRIP | malicious | WEAPON_USE | MOVEMENT | Sweep-style trip (grep-level) |
| Fighter_StaffSweep | SSWEEP, STAFFSWEEP | malicious | WEAPON_USE | MOVEMENT | Staff sweep (grep-level) |
| Fighter_StaffThrust | STAFFTHRUST, STHRUST | malicious | MARTIALLORE | MOVEMENT | maxAttacks = X |
| Fighter_StaffSpin | STAFFSPIN | malicious | MARTIALLORE | MOVEMENT | Viewers' armour raised by weapon damage × (1+X/10) |
| Fighter_StaffBlock | (none) | beneficial self | EVASIVE | none | Staff parry, tries = speed + X/3 |
| Fighter_Spring | SPRINGATTACK, SPRING, SATTACK | malicious | ACROBATIC | MOVEMENT | Spring attack, range-gated |
| Fighter_Runover | RUNOVER | malicious | TRAVEL | MOVEMENT | d(1,6+X) × weight ratio |
| Fighter_SetPolearm | SETPOLEARM | indifferent | WEAPON_USE | MOVEMENT | ×1.5 vs mounted, dismount counter |
| Fighter_RidingFight | (none) | ok self | EVASIVE (thief skill) | none | Pursue a fleeing mounted opponent |
| Fighter_RideToTheRescue | RRESCUE | indifferent | ACROBATIC | MOVEMENT | Scoop group ally onto mount |
| Fighter_Ridethrough | RIDETHROUGH | indifferent | MARTIALLORE | MOVEMENT | maxRange 99 charge |
| Fighter_TendMount | TENDMOUNT | beneficial others | ANATOMY | none | Heal 2% + 0.5%X of max HP per tick, cure chance 5+X % |
| Fighter_SootheMount | SOOTHE, SOOTHEMOUNT | ok others | ANIMALAFFINITY | none | Calm an animal mount |
| Fighter_ScoutAhead | SCOUT | indifferent | EVASIVE | MOVEMENT | Mounted scouting, 1+X steps |
| Fighter_SmokeSignals | SMOKESIGNALS, SMOKESIGNAL | indifferent | NATURELORE | MOVEMENT | Fire needed, range 20+X+2·XMAX, weather blocks |
| Fighter_Rescue | RESCUE, RES | ok others | MARTIALLORE | MOVEMENT | Take the monster's attacker |
| Fighter_UnwaveringMark | UMARK | malicious | COMBATLORE | none | Mark; counter-attack and redirect |
| Fighter_RopeDisarm | RDISARM, ROPEDISARM | malicious | MARTIALLORE | MOVEMENT | Lasso disarm at range 1 |
| Fighter_WeaponBreak | BREAK | malicious | MARTIALLORE | MOVEMENT | Break a weapon at range 1 |
| Fighter_WeaponCatch | (none) | beneficial self | MARTIALLORE | none | Catch a disarmed weapon |
| Fighter_ReturnProjectile | (none) | ok self | EVASIVE | none | Catch and return shots |
| Fighter_ShieldBlock | (none) | beneficial self | SHIELDUSE | none | Block with DEX-90+2X, shield armour |
| Fighter_Shrug | BRACE | beneficial self | FITNESS | MOVEMENT | Absorb the next weapon hit |
| Fighter_Tumble | TUMBLE | beneficial self | ACROBATIC | MOVEMENT | Negate hits, 2 hits |
| Fighter_Roll | (none) | ok self | EVASIVE | none | Random share of damage removed |
| Fighter_Stonebody | (none) | ok self | FITNESS | none | Damage reduction (bug, see Problems) |
| Fighter_Toughness, II, III | (none) | beneficial self | FITNESS | none | +30 + 2·level·prof/100 HP |
| Fighter_Warcry | WARCRY | beneficial others | SINGING | none | +1 + damage/(4-0.1X) damage |
| Fighter_Rallycry | RALLYCRY | beneficial others | SINGING | none | +(level + 2X) max HP |
| Fighter_RapidShot | (none) | beneficial self | MARTIALLORE | none | 1 + floor(level/16) extra shots |
| Fighter_RapidReload | (none) | beneficial self | WEAPON_USE | none | Refund LOAD cost, 20+5·X4 % |
| Fighter_TrueShot | (none) | beneficial self | MARTIALLORE | none | Ranged attack +prof/(200-10X) |
| Fighter_ViciousBlow | (none) | beneficial self | ANATOMY | none | Crit damage +50 + 5X percent |
| Fighter_SizeOpponent | SIZEUP, OPPONENT | indifferent | COMBATLORE | none | Reports target's HP |
| Fighter_WristLock | WRISTLOCK | grapple | (grapple base) | (grapple) | Limb break after 4 ticks |
| Fighter_SleeperHold | SLEEPERHOLD | grapple | (grapple base) | (grapple) | Chance 10·tickUp + X per tick |
| Fighter_SideKick | (none) | malicious | KICKING | none | Generated bone kick, 5 damage |
| Fighter_SnapKick | (none) | malicious | KICKING | none | Generated bone kick |
| Fighter_UrbanTactics, Fighter_WaterTactics, Fighter_SwampTactics | (none) | (base) | (base) | (base) | Terrain lists for Fighter_FieldTactics |
| MonkSkill | (none) | (base) | (base) | (base) | Base class with `anyWeapons` |

**Dice and percentage catalogue.** The recurring numbers, with their sources, are useful as default values in a text game:

- Proficiency gate on negative adjustment: `d100 > 100 + adjustment` fails (StdAbility.java:1482-1483).
- Interception: `d100 <= 10 + XLEVEL` (Fighter_Vanguard.java:257, Fighter_RearGuard.java:252).
- Common contest adjustments: `-35 - 3*(targetDEX - 9)` for Rope Trip (Fighter_RopeTrip.java:279). `-(10 × excess level)` and `-(targetSTR - mobSTR)` for Whomp (Fighter_Whomp.java:223-230). `-85 + stat + 2X` for the passive defences (Fighter_Roll.java:115, Fighter_Stonebody.java:112, Fighter_ReturnProjectile.java:115).
- Shield block: `DEX - 90 + 2X` (Fighter_ShieldBlock.java:110). Staff block: `DEX - 93 + X` with `speed + X/3` tries (Fighter_StaffBlock.java:124-125).

## Notable design ideas

- Guards are effects on the vehicle, not on the guard. The vehicle carries its escort list (`vanGuard` or `rearGuards`) and a per-guard distance. The effect's tick moves the guards and the driver, so escort duty survives the vehicle being moved by someone else (Fighter_Vanguard.java:102-233, Fighter_RearGuard.java:110-230).
- The vehicle and guards interact through the message pipeline only. The guard does not need to know about the attacker; it sees `AmmunitionWeapon` attacks aimed at the vehicle's room in `okMessage` and can veto them (Fighter_Vanguard.java:236-274).
- Stances are a three-way cycle with no transitive advantage. Each stance is weak against exactly one other, and the counter is read from the opponent's own stance effect (Fighter_Stance.java:210-240). The result is a readable, deterministic choice with one number per stance (attack, damage, armour).
- The Sweep maneuver copies its own ability object onto the attacker as a temporary, non-savable penalty for the swing duration, then removes it in a `finally` (Fighter_Sweep.java:188-205). This is a clean way to scope a temporary modifier to one action.
- Unwavering Mark redirects enemies through a trailer runnable, so the counter-attack happens after the original message has resolved (Fighter_UnwaveringMark.java:139-165).
- Runover uses the physics of the vehicle, including the riders who pull it, to scale damage (Fighter_Runover.java:141-146). Weight ratios are a cheap, data-driven damage model.
- Smoke Signals is a communication skill that depends on physical preconditions (a burning fire, clear weather) and on distance measured in rooms. It makes communication a resource rather than a free command (Fighter_SmokeSignals.java:124-210).
- Limb breaks come from a grapple's duration (Fighter_WristLock.java:96-100). Injury is a consequence of holding a position, not a separate roll.

## Problems and cruft

1. **Shadowed field, lost proficiency gain (bug, by reading).** `Fighter_Stonebody.java:37` declares field `int regain=-1`. In `okMessage`, line 115 declares a local `final int regain` that shadows it, and the field is never set. `executeMsg` at line 132 checks the field (`regain>0`), which is always false, so the skill never improves on taking hits. `Fighter_Roll.java:119` assigns to the field and works correctly.
2. **Concurrent modification in Rearguard (bug, by reading, unverified at runtime).** `Fighter_RearGuard.java:145` iterates `rearGuards.keySet()`. `SHashtable` wraps a plain `HashMap` and returns the live key set (`core/collections/SHashtable.java:214-220`). At line 158 the loop removes a dismounted guard and then `continue`s (line 159). The next iterator step after more guards remain should throw `ConcurrentModificationException`. The `break` branches at lines 186 and 210 avoid this.
3. **Concurrent modification in sweep family (bug, by reading, unverified at runtime).** `Fighter_Sweep.java:119-122` and `:155-158` remove out-of-range targets from `h` while iterating over `h`. The same pattern appears in `Fighter_SweepingTrip.java:123-126, 154-157` and `Fighter_StaffSweep.java:122-125, 153-156`. `h` is the set from `properTargets` (`StdAbility.java:2113-2126`; `Libraries/MUDFight.java:163` builds it as an `SHashSet`, which wraps a `HashSet`), so the same exception is possible whenever a removed target is not the last one in iteration order.
4. **Caster healed by Rally Cry (apparent bug).** `Fighter_Rallycry.java:162` calls `mob.curState().adjHitPoints(hpUp, ...)`, where `mob` is the caster. The caster is healed once per recipient, while the recipients get only a max-HP increase. This may have been intended as a caster reward but is not what the message "seem(s) rallied!" describes.
5. **Stance null risk (fragile, unverified).** `Fighter_Stance.java:176-179` reads `mob.getVictim()` twice. If the victim changes to null between the two reads (possible only with a concurrent change), `lastVictimM.fetchEffect(...)` throws. `isInCombat()` is `victim != null` (`MOBS/StdMOB.java:1394-1397`).
6. **Typos and dead code.**
   - "longre" at `Fighter_Stance.java:325`.
   - "navagable" at `Fighter_Vanguard.java:363` and `Fighter_RearGuard.java:330`.
   - In `Fighter_Vanguard.java:125-141`, `max` is the constant 0, so the `if(x != max)` branch builds the same one-room list as the else. This is dead logic left from a radius design.
   - Empty `executeMsg` overrides in `Fighter_Vanguard.java:276-280` and `Fighter_RearGuard.java:271-275`.
   - `Fighter_Stance.java:320-328` checks the room for null twice (return at 321-322, then `R!=null` again at 328).
7. **Wall-clock timers.** Vanguard and Rearguard use a hard-coded 4000 ms combat window (`Fighter_Vanguard.java:123`, `Fighter_RearGuard.java:123`). Racial mount speed uses 250 ms (`Fighter_RacialMount.java:223, 321`). These do not follow the configurable tick (`TICKTIME`, `CMProps.java:681`). The 4000 ms value only matches the default tick.
8. **Cache key naming.** The racial mount table is cached under `PARSED_RECIPE: racialmounts.txt` (`Fighter_RacialMount.java:146, 186`), a recipe prefix on a non-recipe resource. Cosmetic, but confusing.
9. **Integer division in Runover.** `weights / M.phyStats().weight()` (`Fighter_Runover.java:145`) is integer division, so the result is 0 whenever the caravan plus pullers weigh less than the target. A zero target weight would divide by zero (by reading).
10. **Multiplayer assumptions.** Ride To The Rescue refers to "your group" (`Fighter_RideToTheRescue.java:131`). Smoke Signals and the caravans assume other players will see the message or be passengers. Rescue, War Cry and Rally Cry assume a group of allies. Vanguard and Rearguard assume a driver is a player or NPC crewmate. A single-player design needs to replace these with party or companion logic.
11. **Passive HP stacking.** Toughness adds HP through `affectCharState` each recalculation (`Fighter_Toughness.java:94-98`). It is not capped or tied to a base value, so it compounds with other max-HP effects.

## Takeaways for a Python SUD

- **Stances as data.** Model a stance as an enum or string with three modifier functions of the expertise level, and a dictionary of counters: `NULLIFIED_BY = {"offensive": "defensive", "defensive": "wild", "wild": "offensive"}`. Compute modifiers from the base attack, damage and armour values on demand; do not store them. The armour convention (lower is better, 100 is unarmoured) is worth keeping, or change it explicitly.
- **Racial mounts as a table.** Use a TSV or YAML table of race → mount category and default mount, with the same lookup order as the code: race, then category, then wildcard, then default. Keep it data-only so content authors can edit it.
- **Escort as a state object.** Represent a caravan or wagon as an object with a driver and a dict of guards to distances. Each tick, move guards toward the vehicle when it is under attack, and move the vehicle toward guards otherwise. Interception is a single roll against `10 + skill_level` when an attack targets the vehicle's location. Use ticks, not wall-clock time.
- **Maneuvers as rows.** Define each maneuver as a small record: trigger words, minimum range, required weapon class or mount state, a contest adjustment function, a proficiency call with the two-stage negative gate, an effect with a tick count, and message templates. The `proficiency_check(prof, adjustment)` function can follow `StdAbility.java:1441-1488` with its gate.
- **Scoped temporary modifiers.** Apply a sweep-style penalty as a context manager (`with temporary_effect(actor, penalty): ...`) so it is removed even on error, as the `finally` in Fighter_Sweep does.
- **Injuries from holds.** Let a hold accumulate ticks, and after a threshold roll for a broken limb. A `broken_limbs` set on the target can feed movement and attack rules.
- **Keep it simple.** Drop the group-based rescue and buff targets and the multi-guard rearguard distance logic if the single-player game has one companion at a time. Keep the pattern of a counter-stance, the lasso trip, the knockdown with a 2-tick sleep, and the mount-care heal per tick. These translate directly.
- **Avoid the bugs.** Use separate lists or copies when removing during iteration. Avoid shadowing fields with locals of the same name. Use one configurable tick constant for every duration.

## Coverage

- **Read in full:** `FighterSkill.java` (not in bucket, base rules), `Fighter_Stance.java`, `Fighter_RacialMount.java`, `Fighter_Vanguard.java`, `Fighter_RearGuard.java`, `Fighter_WeaponSharpening.java`, `Fighter_RopeTrip.java`, `MonkSkill.java`, `resources/skills/racialmounts.txt`.
- **Read in large part (targeted ranges and grep):** `Fighter_Whomp.java`, `Fighter_Runover.java`, `Fighter_SetPolearm.java`, `Fighter_TendMount.java`, `Fighter_Sweep.java`, `Fighter_StaffBlock.java`, `Fighter_StaffSpin.java`, `Fighter_Stonebody.java`, `Fighter_Roll.java`, `Fighter_Toughness.java`, `Fighter_Warcry.java`, `Fighter_Rallycry.java`, `Fighter_ShieldBlock.java`, `Fighter_Tumble.java`, `Fighter_RapidShot.java`, `Fighter_RapidReload.java`, `Fighter_TrueShot.java`, `Fighter_ViciousBlow.java`, `Fighter_RidingFight.java`, `Fighter_WristLock.java`, `Fighter_SideKick.java`, `Fighter_UnwaveringMark.java`, `Fighter_Shrug.java`, `Fighter_Rescue.java`, `Fighter_SmokeSignals.java`, `Fighter_SleeperHold.java`, `Fighter_ScoutAhead.java`.
- **Skimmed (grep for ID, trigger, quality, domain and key numbers only):** `Fighter_SweepingTrip.java`, `Fighter_StaffSweep.java`, `Fighter_StaffThrust.java`, `Fighter_StaffSpin.java` (partly read), `Fighter_Spring.java`, `Fighter_Ridethrough.java`, `Fighter_RideToTheRescue.java`, `Fighter_RopeDisarm.java`, `Fighter_WeaponBreak.java`, `Fighter_WeaponCatch.java`, `Fighter_ReturnProjectile.java`, `Fighter_SootheMount.java`, `Fighter_SnapKick.java`, `Fighter_SizeOpponent.java`, `Fighter_Toughness2.java`, `Fighter_Toughness3.java` (ID and name only).
- **Skipped (not in bucket):** `Fighter_FieldTactics.java` (base of the terrain tactics), `FighterGrappleSkill.java` (base of the grapples, 502 lines). Read only where cited for the base rules in `StdAbility.java`, `core/collections/SHashtable.java`, and `MOBS/StdMOB.java`.
