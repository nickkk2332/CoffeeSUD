# Abilities: Ranger skills, weapon and armour skill tiers, super powers, tech jobs (part 3 of 3)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b041` (Abilities/(small families) [part 3/3: Ranger_Enemy3 .. TechSkill]) · 70 files · 7848 lines of Java.

Paths are relative to `com/planet_ink/coffee_mud/`, so `Abilities/Ranger/Ranger_Track.java:263` means `com/planet_ink/coffee_mud/Abilities/Ranger/Ranger_Track.java`, line 263. Files outside this bucket are marked "(not in bucket)".

## Summary

This bucket is the tail of CoffeeMud's small ability families and has four parts. The Ranger skills give a wilderness character trail-following over room graphs, three stealth skills (Hide, Creep, Sneak), snares, and passive bonuses in the wild or against chosen enemies. A three-tier family for most weapon classes, plus armour and shield variants (Proficiency, Specialization, Familiarity), splits weapon permission, combat bonuses for the wielded class, and bonuses for gear the character crafted, each over one shared base class. The SuperPowers are status effects that bind, grow limbs, climb or sense traps. TechSkill and AstroEngineering model a timed, interruptible install/repair/enhance job on space-ship electronics whose result is a number that the target item applies. The three-tier weapon model, the room-trail tracker and the timed job pattern are the most reusable for a single-player Python game.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| Abilities/Specializations/Proficiency_Weapon.java | Weapon-class permission via zapper mask feeding CharStats | 171 |
| Abilities/Specializations/Specialization_Weapon.java | Attack and damage bonus base for the wielded weapon class | 193 |
| Abilities/Specializations/Familiarity_Weapon.java | Attack bonus for a weapon the character crafted | 131 |
| Abilities/Specializations/Specialization_Armor.java | Armour bonus scaled by number of worn armour pieces | 170 |
| Abilities/Specializations/Familiarity_Armor.java | Armour bonus for self-crafted armour | 153 |
| Abilities/Specializations/Familiarity_Shield.java | Armour bonus for a held, self-crafted shield | 142 |
| Abilities/Specializations/Specialization_SiegeWeapon.java | Damage bonus for siege ammunition weapons | 119 |
| Abilities/Specializations/Specialization_Natural.java | Unarmed (hand-to-hand) attack bonus | 89 |
| Abilities/Specializations/Specialization_Shield.java and _Whip.java | Narrowed weapon-match rules | 80, 67 |
| Abilities/Specializations/Proficiency_Bow.java, Proficiency_Sling.java, Familiarity_Bow.java, Specialization_Bow.java | Ammunition-filtered variants | 75, 72, 68, 68 |
| Abilities/Specializations/ one-class stubs (31 files, listed in section 6) | Set only a weapon class; no logic of their own | 56-57 each |
| Abilities/Ranger/Ranger_Track.java | Track a named mob: trail engine and tick state machine | 308 |
| Abilities/Ranger/Ranger_TrackAnimal.java | Track the nearest animal; same engine | 297 |
| Abilities/Ranger/Ranger_FindWater.java | Trail to the nearest wet room or liquid; water report | 376 |
| Abilities/Ranger/Ranger_Hide.java | Woodland Hide: IS_HIDDEN, broken by noise | 245 |
| Abilities/Ranger/Ranger_WoodlandCreep.java | Hidden and sneaking in jungle, swamp or woods | 193 |
| Abilities/Ranger/Ranger_Sneak.java | One-step sneak with bystander-level difficulty | 191 |
| Abilities/Ranger/Ranger_SetSnare.java | Places a Trap_Snare in a wilderness room | 217 |
| Abilities/Ranger/Ranger_SenseTraps.java | Automatic trap perception on room entry | 186 |
| Abilities/Ranger/Ranger_Enemy1.java (not in bucket) | Favored-enemy base class used by Enemy3, Enemy4, Skill_RacialEnemy, PlanarEnemy | 207 |
| Abilities/Ranger/Ranger_PlanarEnemy.java | Favored enemy is a plane of existence | 165 |
| Abilities/Ranger/Ranger_FierceCompanions.java | Combat bonus while an animal ally fights alongside | 150 |
| Abilities/Ranger/Ranger_HuntersEndurance.java | Hunger, thirst and fatigue relief while tracking | 147 |
| Abilities/Ranger/Ranger_WoodlandLore.java | Wilderness damage, attack and armour shifts | 120 |
| Abilities/Ranger/Ranger_Enemy3.java, Ranger_Enemy4.java, Skill_RacialEnemy.java | Named slots and racial variant; no logic of their own | 48, 48, 55 |
| Abilities/SuperPowers/SuperPower.java | Base class; ACODE_SUPERPOWER, no logic | 88 |
| Abilities/SuperPowers/Power_OctoGrapple.java | Grapple: binds target and invoker | 239 |
| Abilities/SuperPowers/Power_WebSpinning.java | Web on mob, item or exit with a struggle budget | 223 |
| Abilities/SuperPowers/Power_OctoArms.java | Extra arm attacks in combat | 148 |
| Abilities/SuperPowers/Power_SuperClimb.java | Climb a direction, temporary IS_CLIMBING | 142 |
| Abilities/SuperPowers/Power_TrapSense.java | Automatic trap report on room entry | 214 |
| Abilities/Tech/TechSkill.java | Base class; ACODE_TECH | 88 |
| Abilities/Tech/AstroEngineering.java | Timed install, repair or enhance job on space-ship electronics | 474 |
| Abilities/StdAbility.java (not in bucket) | proficiencyCheck, helpProficiency, adjustedLevel, X-level accessors | (helper) |
| Libraries/MUDTracker.java (not in bucket) | Trail building and next-step logic used by all trackers | (helper) |

## How it works

### 1. Shared skill machinery

Every skill here uses the same helpers in `Abilities/StdAbility.java`.

- **Proficiency roll.** `proficiencyCheck(mob, adjustment, auto)` at `Abilities/StdAbility.java:1441-1485`. The chance starts at `proficiency()` (0-100, line 627). Ability-adjustment modifiers are added if present. Then `2 * XLEVEL` is added and the total is clamped to 5..95 unless the DIS955RULE flag is set (lines 1469-1476). Then `XLEVEL/2` is added (line 1478). A non-negative `adjustment` is added to the chance. A negative `adjustment` is a separate gate: the check fails outright when `rollPercentage() > 100 + adjustment` (lines 1480-1484). The final test is `rollPercentage() < chance`. So a negative adjustment scales the success probability by about `(100 + adjustment)/100` and never lowers the chance directly.
- **Roll range.** `rollPercentage()` returns 1..100 (`Libraries/Dice.java:409-412`). A test written `rollPercentage() < N` therefore succeeds N-1 times in 100. The chapter's percentages use the code's literal test, so `<10` is 9%, `<5` is 4%, `>95` is 5%, and `==1` is 1%.
- **Learn by use.** `helpProficiency(mob, adjustment)` at `Abilities/StdAbility.java:1810`. It is throttled per ability copy to one gain per 300000 ms (5 minutes). A random check passes when `round(sqrt(INT) * 34 * random)` is at least the current proficiency plus adjustment, then a gain-chance roll is made (`getProfGainChance`). Gains stop at `CMLib.ableMapper().getMaxProficiency`. Callers add this as a side effect at fixed rates: 10% per weapon attack (`Specialization_Weapon.java:145`), 5% per attack (`Proficiency_Weapon.java:165`), 25% per throw (`Specialization_Ranged.java:62`), 1 in 10 per damaging hit (`Ranger_Enemy1.java:197`).
- **Generic expertise knobs.** `getX1Level` to `getX4Level`, `getXLEVELLevel`, `getXMAXRANGELevel` and `getXTIMELevel` (`StdAbility.java:419-464`) read per-character expertise. Each skill gives them a meaning. In `Specialization_Weapon` X1, X2 and X3 are damage bonuses by damage type and X4 relaxes item levels (`Specialization_Weapon.java:98-113`, `188`). In trackers MAXRANGE widens the search. LEVEL is added to many formulas.
- **Auto-invoked passives.** Most passives here set `isAutoInvoked()` true and `canBeUninvoked()` false, so they are present from creation and never removed (for example `Ranger_Enemy1.java:77-86`, `Specialization_Weapon.java:76-86`).

### 2. Trackers and the trail engine (Ranger_Track, Ranger_TrackAnimal, Ranger_FindWater)

**Data model.** Each tracker keeps `theTrail` (a `List<Room>`) and `nextDirection` (int). Index 0 of the trail is the destination. Ranger_Track appends the tracker's own room at the end (`Ranger_Track.java:287`). `nextDirection` holds a direction index or one of four sentinels:

| value | meaning | where |
|---|---|---|
| -2 | idle, nothing computed | field default, `Ranger_Track.java:108` |
| 999 | standing on the destination | `Libraries/MUDTracker.java:470-471` returns 999 when `location == theTrail.get(0)` |
| -1 | no neighbour on the trail, so the trail dries up | `MUDTracker.java:468-469` and the loop at `:479-492` leaves `winningDirection` at -1; `Ranger_Track.java:145-150` |
| -999 | finished; tick returns early | `Ranger_Track.java:128-129`, `148` |

`trackNextDirectionFromHere` (`MUDTracker.java:464-494`) picks the neighbour whose index in the trail is smallest, that is the neighbour nearest the destination, and only follows open exits when `openOnly` is set.

**Building the trail.** The destination is found with `CMLib.tracking().getRadiantRoomsToTarget` (`MUDTracker.java:771`) and then `findTrailToAnyRoom` (`MUDTracker.java:405`).

- Track uses flags OPENONLY, NOEMPTYGRIDS, NOAIR and NOWATER (`Ranger_Track.java:266-270`), range `50 + 2*XLEVEL + 10*XMAXRANGE` (line 272). The destination filter `FilterMOBName` (lines 198-221) needs the named mob to be in the room, accessible to the viewer, and seeable. A failed roll reports "<S-NAME> attempt(s) to track X, but can't find the trail." (line 304).
- TrackAnimal uses the same engine with flags OPENONLY, NOEMPTYGRIDS, NOAIR and NOWATER (`Ranger_TrackAnimal.java:246-250`), range `50 + ...` (line 253). Any animal (`animalHere`, lines 188-201, which checks `isAnAnimal` and `isSeeable`) is a valid destination. A check that finds an animal already in the room says "Try 'look'." (lines 237-240).
- FindWater uses flags PASSABLE, NOEMPTYGRIDS and NOAIR (`Ranger_FindWater.java:338-341`), range `60 + 2*XLEVEL + 10*XMAXRANGE` (line 342). The destination is the nearest room where `waterHere` is non-empty (lines 344-354).

**Control flow.**

1. `invoke` removes any active tracking effect (`flaggedAffects` with FLAG_TRACKING), then builds the trail and rolls `proficiencyCheck(mob, 0, auto)` (`Ranger_Track.java:263`).
2. On success a copy of the ability is added as an effect (`copyOf`, `addEffect`), then `recoverPhyStats()` runs (`Ranger_Track.java:296-299`). The copy carries the trail and the first direction.
3. `tick` at TICKID_MOB moves monsters with `CMLib.tracking().walk(mob, dir, false, false)` (`Ranger_Track.java:162`) and tells players "The trail seems to continue @x1." (line 154). Players must act: a player's `nextDirection` is recomputed only when they look at the room (`executeMsg`, `Ranger_Track.java:191-195`; the same test in `Ranger_TrackAnimal.java:181-185` and `Ranger_FindWater.java:184-188`). The player's look is the clock.
4. `affectPhyStats` sets CAN_NOT_TRACK on the tracker (`Ranger_Track.java:176-180`). `Libraries/Sense.java:153-155` (`canTrack`) then reports the tracker as untrackable by others.

**Water detection.** `Ranger_FindWater.waterHere` (lines 240-310) classifies a room in order: watery room (`CMLib.flags().isWateryRoom`), wet climate bits (`CMath.bset(room.getClimateType(), Places.CLIMASK_WET)`), current weather (rain or thunderstorm, hail, snow), and only then the items and inhabitants. Messages are "Your water-finding senses are saturated.  This is a very wet place." (line 249), "It is raining here! Your water-finding senses are saturated!" (line 256), and for containers "@x1 contains some sort of liquid." (line 222). Carried liquids are reported as "@x1 is carrying some liquids." (line 293). Shop stock is scanned (lines 295-307). FindWater's `invoke` checks the tracker's own room first and reports without a roll if water is there (lines 327-332).

### 3. Stealth: Woodland Hide, Woodland Creep, Woodland Sneak

- **Hide** (`Ranger_Hide.java:179-244`). Refused in combat ("Not while in combat!", line 193), outside wilderness, and in city rooms unless `auto`. Difficulty uses the highest-level bystander: `levelDiff = (mob.level + 2*XLEVEL) - highestLevel` (line 210), passed as `levelDiff*10` to the proficiency check (line 218). `getHighestLevelMOB` skips group members, sleepers and system operators (lines 150-177). The effect sets `IS_HIDDEN` (line 131) and adds save-detection `proficiency + bonus` with `bonus = 2*XLEVEL` (lines 140, 235). Noise breaks it: `executeMsg` unInvokes on sound, speech, enter, leave or recall unless the message is a look or examine (lines 107-122). Start message "You creep into some foliage and remain completely still.", or "...behind some rocks..." in rock, mountain or desert rooms (lines 212-216).
- **Woodland Creep** (`Ranger_WoodlandCreep.java:143-192`). Only in jungle, swamp or woods rooms (lines 161-168). The effect sets `IS_HIDDEN` and `IS_SNEAKING` (lines 104-105), `setSpeed(0.5)` (line 106), and save-detection `proficiency + 25 + bonus` (line 113). Bonus is `2*XLEVEL` (line 184), and the effect is made long-lasting (line 185). It ends when the room is no longer jungle, swamp or woods (lines 124-131), or when the holder makes a malicious action while in combat at range 0 (lines 132-139).
- **Woodland Sneak** (`Ranger_Sneak.java:124-189`). A single step in a given direction. `levelDiff = mob.level + 2*XLEVEL - highest` (line 155). It is multiplied by 10 if negative and by 5 otherwise (lines 165-168), then used as the proficiency adjustment (line 169). `IS_SNEAKING` is set in base stats only for the duration of the step (lines 173-185). Movement is `CMLib.tracking().walk` (line 176).

### 4. Snares and trap perception

- **Set Snare** (`Ranger_SetSnare.java:100-216`). The trap is a `Trap_Snare` object (`Abilities/Traps/Trap_Snare.java:35`). Only in wilderness rooms (lines 153-157) and not in combat (lines 115-118). The proficiency adjustment is `3 * (mob.level + 2*XLEVEL - room.level)` (lines 162-163). A set trap takes trap level `XLEVEL` and `adjustedLevel(mob, asLevel)` (line 197). Owners of the land get a permanent trap and a room database update (lines 192-199). A failed attempt springs the snare on the setter half the time (`rollPercentage() > 50`, lines 203-208); otherwise "You fail in your snare setting attempt." (line 211).
- **Sense Snares and Pits** (`Ranger_SenseTraps.java:36-186`). Auto-invoked. On the first tick in each new room (lines 175-183) a 0-adjustment proficiency check (line 181) decides whether to report `trapHere`. `trapHere` covers the current room, its exits and neighbours, and floor items. The wilderness test (`isInWilderness`) applies to the room's own trap and items, and to exits and neighbours when either end is wilderness (lines 126-149). Reports are "To the @x1, @x2 is trapped." and "@x1 is trapped." (lines 110-112), named for the viewer through `P.name(viewer)`. Each report calls `helpProficiency(mob, 0)` (line 166).

### 5. Favored enemies and other Ranger passives

- **Favored enemy base** (`Ranger_Enemy1.java`, not in bucket). `pickAnEnemy` (lines 94-122) chooses a random racial category from fantasy-theme races. It removes the mob's own category, "Unique", "Unknown", and any category already held by another favored-enemy ability or effect (lines 104-120). The result is stored in `miscText`. Against a victim of that category (cached per victim, lines 149-160, membership at 162-171), `affectPhyStats` adds attack `round(prof/100 * 3*level)` and damage `round(prof/100 * level)`, where `level = 1 + adjustedLevel(mob, 0)` (lines 182-186). Each damaging hit against an enemy has a 1 in 10 chance of learn (lines 190-200).
- **Enemy3, Enemy4, Skill_RacialEnemy.** These add only a name and ID. `Skill_RacialEnemy` overrides the class code to `DOMAIN_RACIALABILITY` (`Skill_RacialEnemy.java:49-53`), so it is the racially granted version of the same effect. `Ranger_Enemy2` (not in bucket) is the other slot.
- **Planar Enemy** (`Ranger_PlanarEnemy.java:36-165`). The enemy is a plane of existence rather than a race. `pickAnEnemy` reads every plane key from `StdPlanarAbility` (line 57) and categorises planes as lower or higher. It keeps planes whose alignment exceeds the mob's by more than 15000 (preferred, line 81) or by more than 10000 (fallback, line 84). Then it narrows to the mob's inclination faction band (lines 98-134), and picks at random (line 143). `isTheEnemy` is true when the start room's area is on the chosen plane (lines 150-163).
- **Fierce Companions** (`Ranger_FierceCompanions.java:36-150`). Each tick in combat it looks for a group member that is an animal, in the same room, alive and fighting (lines 131-139). While one exists, `activated` is set. Then level = `2 + adjustedLevel/10` (line 111), damage `+round(prof/100 * level)` (line 112), attack `+round(prof/100 * 2*level)` (line 113). A 1% per-tick learn chance is rolled in combat (lines 140-141).
- **Hunter's Endurance** (`Ranger_HuntersEndurance.java:36-147`). While the character is tracking (`Libraries/Sense.java:693`), is not trackable (`canTrack` false, `Sense.java:153`) and is not in combat (lines 113-115), hunger and thirst are held at max (lines 119-120). At proficiency 99 or above, fatigue is set to 0 and movement to max (lines 121-125). Otherwise, on a `proficiencyCheck(mob, 0, false)` (line 127), fatigue falls by the proficiency and movement rises by it (lines 129-130). When the condition ends, the snapshot taken at the start is written back (lines 136-143).
- **Woodland Lore** (`Ranger_WoodlandLore.java:36-120`). In wilderness, `affectPhyStats` adds damage `5 + X`, attack `5 + 2X`, and subtracts `10 + 2X` from the armour stat (lines 100-105). A 1 in 10 learn chance on damaging hits in wilderness is at line 115. The same method sets `invoker` as a side effect (lines 98-99).

### 6. Weapon, armour and shield skill tiers

Each weapon family has three tiers, all derived from bases in `Abilities/Specializations/`.

| Tier | Base class | What it does | Where it is consumed |
|---|---|---|---|
| Proficiency_ | Proficiency_Weapon.java:37-171 | Permission to use a weapon class the character would otherwise be refused | `CharClasses/StdCharClass.java:906` (weaponCheck), `Libraries/CoffeeUtensils.java:1069` (armorCheck) |
| Specialization_ | Specialization_Weapon.java:36-193 | Attack and damage bonus while the matching weapon is wielded | Attack adjustment in `affectPhyStats` (157-174); damage in `executeMsg` (134-155) |
| Familiarity_ | Familiarity_Weapon.java:36-131 | Attack bonus when the wielded weapon was crafted by this character | `affectPhyStats` (116-130) |

**Proficiency.** `getWeaponMask()` builds a zapper mask (`Proficiency_Weapon.java:107-120`): `-CLASSTYPE +Weapon -WEAPONCLASS +<class description>`, plus a secondary class where set. `affectCharStats` compiles it once with `CMLib.masking().maskCompile` (lines 128-139) and adds a filter to the character's `itemProficiencies` array (lines 140-152). The filter returns ALLOWED or NOTAPPLICABLE for each item (line 136). It is only added when `proficiency() > 95 || proficiencyCheck(affected, 0, false)` (line 140). The class code treats the result as a tri-state: ALLOWED lifts the class restriction, DENIED refuses, and NOTAPPLICABLE falls through to the normal class rules (`StdCharClass.java:906-909`, `CoffeeUtensils.java:1069-1075`). The normal rules in `weaponCheck` include a fumble on restricted weapons: "<S-NAME> fumble(s) horribly with @x1." when `rollPercentage() > stat*2` (`StdCharClass.java:906-924`).

**Specialization.** `isWeaponMatch(W)` (`Specialization_Weapon.java:115-122`) matches `weaponClassification()` against `weaponClass` or `secondWeaponClass`, and matches any weapon when `weaponClass < 0`. While a matching weapon is wielded, `activated` is set and the attack adjustment is `round((15 + 3*XLEVEL) * prof/100)` (lines 169-170). A damaging hit by a matching weapon adds `getDamageBonus` to the damage value (line 151). The bonus is X1 for slashing, X2 for piercing, X3 for bashing and X2 for shoot (lines 98-113). It is flat, not scaled by proficiency. The `okMessage` path (lines 176-191) lowers the item's current level by `(1 + X4)/2` when a character wields or holds an item above their level (line 188). This is how expertise lets a character use gear above their level. It changes `phyStats().level()` on the item, not its base.

**Familiarity.** `isWeaponMatch(mob, W)` (`Familiarity_Weapon.java:97-104`) needs the same class match plus `CMLib.ableParms().getCraftingBrand(W)` equal to the character's name (line 102). The attack adjustment is `round(adjustedLevel(mob, 0) * prof/100)` (lines 126-127).

**Family catalogue.** The class constants come from `Items/interfaces/Weapon.java:138-160`: AXE=0, BLUNT=1, EDGED=2, FLAILED=3, HAMMER=4, NATURAL=5, POLEARM=6, RANGED=7, SWORD=8, DAGGER=9, STAFF=10, THROWN=11.

| Group | Specialization_ | Proficiency_ | Familiarity_ | Weapon class match |
|---|---|---|---|---|
| Axe | Axe | Axe | Axe | AXE |
| Blunt | BluntWeapon | BluntWeapon | BluntWeapon | BLUNT, or STAFF |
| Dagger | Dagger | Dagger | Dagger | DAGGER |
| Edged | EdgedWeapon | EdgedWeapon | EdgedWeapon | EDGED, or DAGGER |
| Flailed | FlailedWeapon | FlailedWeapon | FlailedWeapon | FLAILED |
| Hammer | Hammer | Hammer | Hammer | HAMMER |
| Polearm | Polearm | Polearm | Polearm | POLEARM |
| Staff | Staff | Staff | Staff | STAFF |
| Sword | Sword | Sword | Sword | SWORD |
| Natural | Natural (unarmed, see below) | Natural | Natural ("Racial Weapon Familiarity") | NATURAL |
| Ranged | Ranged | Ranged | Ranged | RANGED, or THROWN |
| Bow | Bow | Bow | Bow | RANGED, and AmmunitionWeapon with ammunition arrow or arrows |
| Sling | none | Sling | none | AmmunitionWeapon whose name matches the sling pattern |
| Thrown | ThrownWeapon | none | none | THROWN |
| Shield | Shield (matches Shield instances only) | none | Shield (armour tier) | Shield |
| Whip | Whip | none | none | FLAILED, leather, name contains "whip" |
| Siege | SiegeWeapon | none | none | AmmunitionWeapon with isASiegeWeapon |
| Armour | Armor | none | Armor | worn armour pieces |

Each one-class stub sets only `weaponClass` (and `secondWeaponClass` for Blunt, Edged and Ranged, values in the table above). These are the 31 stub files: the Specialization_ stubs for Axe, BluntWeapon, Dagger, EdgedWeapon, FlailedWeapon, Hammer, Polearm, Staff and Sword; the Familiarity_ stubs for Axe, BluntWeapon, Dagger, EdgedWeapon, FlailedWeapon, Hammer, Natural, Polearm, Ranged, Staff and Sword; and the Proficiency_ stubs for Axe, BluntWeapon, Dagger, EdgedWeapon, FlailedWeapon, Hammer, Natural, Polearm, Ranged, Staff and Sword.

**Specific variants.**

- **Armour** (`Specialization_Armor.java:88-170`). Counts worn armour pieces in the nine WORN_ARMOR locations (lines 88-90), +1.0 per piece with base armour above 0 (line 115), with a 5% learn check on recalculation (lines 118-119). The cached `bonus` is recalculated only when it is -1 (line 143). The armour stat falls by `round(bonus * prof/100) + XLEVEL/2` (lines 147-149). The cache is cleared on the holder's own wear, remove or drop of armour, and on TYP_LIFE (lines 124-135). Wearing armour above the holder's level lowers its current level by `(1 + XLEVEL)/2` (lines 155-166).
- **Familiarity_Armor** (`Familiarity_Armor.java:100-152`). Same cache. +0.3 per worn armour piece crafted by the holder (lines 113-116). Armour stat falls by `round(adjustedLevel * bonus * prof/100)` (line 149).
- **Familiarity_Shield** (`Familiarity_Shield.java:96-141`). One held Shield crafted by the holder adds +0.33333 (lines 101-108). The reduction is the same shape as Familiarity_Armor (line 138).
- **Specialization_Shield** (`Specialization_Shield.java:35-79`). Extends the weapon base with a fixed BLUNT class, but overrides matching to `instanceof Shield` (lines 64-67). Damage bonus is flat `XLEVEL` (lines 58-61).
- **Specialization_Natural** (`Specialization_Natural.java:35-88`). Unarmed: if nothing is wielded, attack `+round(15 * prof/100) + 10*XLEVEL` (lines 75-81). 10% learn per weapon attack made as a natural attack (lines 60-66).
- **Specialization_Whip** (`Specialization_Whip.java:58-65`). Matches only FLAILED-class weapons made of leather whose name contains "whip".
- **Specialization_SiegeWeapon** (`Specialization_SiegeWeapon.java:101-118`). Not a weapon-base subclass. A siege ammunition weapon's damage in the same room gets `+round((5 + 2*X3) * prof/100)` (lines 106-113), with a 10% learn check (lines 114-115).
- **Bow family.** `Specialization_Bow.java:59-67` and `Familiarity_Bow.java:59-67` accept only AmmunitionWeapon items whose `ammunitionType()` is "arrow" or "arrows". `Proficiency_Bow.java:64-73` appends `-WEAPONAMMO +arrow +arrows` to its mask.
- **Sling.** `Proficiency_Sling.java:62-71` appends `-SUBNAME +*sling* +WEAPONAMMO - ` to the base mask (line 68).
- **Ranged and Thrown.** `Specialization_Ranged.java:59-70` and `Specialization_ThrownWeapon.java:59-70` give a 25% learn chance on a throw of an item at a character (line 62).

### 7. SuperPowers

`SuperPower.java` is an ID-only base with ACODE_SUPERPOWER (line 86) and CAN_MOBS as its target. The five powers add behaviour as follows.

- **Octo-Grapple** (`Power_OctoGrapple.java:197-238`, trigger "GRAPPLE", FLAG_BINDING, USAGE_MOVEMENT). Refused if the target is more than 200 base weight heavier than the caster (lines 203-207). Difficulty is `levelDiff * 10` when the target is above the caster (lines 212-216), plus `-(target STR - caster STR) * 5` (line 219). Success also requires a hit (line 218) and the target to have more than 2 arms (line 220). On success two effects are applied, one to the target and one to the caster (lines 229-230). While held, the target's okMessage vetoes its eye, hand, mouth and movement actions (lines 113-139). Each struggle breaks free with chance `rollPercentage() < STR` (line 126). The caster's `affectCharStats` removes 2 arms (lines 159-160).
- **Web Spinning** (`Power_WebSpinning.java:186-222`, trigger "WEBSPIN"). Range 1 to `adjustedMaxInvokerRange(5)` (lines 67-75). Targets: mob, unworn item or exit (lines 87-93). Budget `amountRemaining = 160 * target speed` (lines 207-208). Each struggle subtracts `STR + level` (line 129), and the web ends when the budget is below 0 (line 130). Items cannot be dropped by the owner (lines 145-149). Exits cannot be opened (lines 158-160). Duration is `adjustedLevel * 10` (line 211). The start message sends the MUD sound cue `web.wav` (line 201).
- **Octo-Arms** (`Power_OctoArms.java:89-147`, auto). In combat each tick, if at least 3 arms, it posts `arms - 2` extra attacks with a steel "huge snaking arm" weapon (`arms = getBodyPart(BODY_ARM) - 2`, line 104; attacks at 116-117; weapon at 108-113). The weapon has 1000 uses and damage copied from the holder's base damage. The invoker gets +4 arms (line 146). Hits with the arm add the arm's base damage (line 137). A learn check fires on about 5% of combat ticks (line 102). Body-part defaults were not located, so the count of extra attacks assumes the race has the usual two arms (unverified).
- **Super Climb** (`Power_SuperClimb.java:90-141`, triggers "CLIMB" and "SUPERCLIMB"). Refused when sitting or sleeping (lines 104-108). A temporary copy with IS_CLIMBING is added during the move (lines 119-133) and removed in a `finally`. The climb happens whether or not the roll passes. The success value only decides whether an extra room message is sent (lines 137-138).
- **Trap Sense** (`Power_TrapSense.java:96-213`, auto). On each new room, `messageTo` reports traps in the room ("<name> is trapped.") or, if none, in adjacent rooms: "You sense a trap to @x1, and @x2." (lines 180-196). A learn check of 5% fires when the current room has a trap (line 173).

### 8. Tech: AstroEngineering

`TechSkill.java:36-88` is a base with ACODE_TECH, target CAN_MOBS, and no logic (lines 58-68, 84-87). `AstroEngineering` (`Abilities/Tech/AstroEngineering.java:37-474`) is a timed job.

**Operations.** The enum `Operation` has INSTALL, REPAIR and ENHANCE (lines 100-110). The first word is matched by prefix: "INSTALL".startsWith(input) (line 317), REPAIR (line 353), ENHANCE (line 359). Target checks: the item must be `Electronics` and `TechComponent` (lines 401-410), and must not be in combat (line 411), seen (line 416) or sitting or sleeping (line 421). An INSTALL target panel must be an `ElecPanel` (lines 344-348). REPAIR needs `subjectToWearAndTear()` (line 428). ENHANCE refuses an item below 100 uses (lines 437-441).

| Operation | Completion value (CMMsg value) | Applied by |
|---|---|---|
| INSTALL | `50 + getBonus(mob, item, 50)` (line 201), so 50 to 100 | `Items/BasicTech/StdElecPanel.java:155-160` refuses value 0 or less; `:178-184` sets the component's installed factor to value/100 |
| REPAIR | `roll(1, prof/2, getBonus(...,50))` (line 212) | `Items/CompTech/StdElecCompItem.java:284-301`: with probability value% the item is restored to at least 100 uses; otherwise it gains round(missing * value/100) |
| ENHANCE | `prof/2 + getBonus(...,50)` (line 223) | `Items/CompTech/StdElecCompItem.java:303-320`: succeeds on roll < value and roll < 50; installed factor grows by 0.01 at 1.0 or above, otherwise by max(0.1, 10/value) |

Each completed job calls `giveBonus` (lines 205, 216, 227). `getBonus` (lines 112-136) scores 0.5 times the manufacturer expertise percentage plus 0.5 times the tech-type expertise percentage. Missing manufacturer or type adds 0.5 each. The score is multiplied by `multiplyBy` (50 in all three uses) and rounded. Full expertise in both gives 50. `giveBonus` (lines 138-178) picks manufacturer or tech type with a 50% roll (line 147), throttles to one gain per 300000 ms (line 143), and adds +1 expertise when `round(sqrt(INT) * 34 * random)` is at least the current expertise (line 169). The result is written with `mob.addExpertise(name + "%" + value)` (line 171) and the message "You gain some new insights about @x1." (line 176).

**Timing.** `baseTickSpan = weight / 4` (line 464), halved on failure (lines 465-466), with a minimum of 8 ticks (lines 316, 467-468). The job runs as `beneficialAffect` with that tick count (line 470). Tick length is the engine default and was not re-verified here. In `tick` (lines 240-283) the job aborts if the character enters combat, leaves the target room, or is not awake and unbound (lines 245-251). At `tickDown == 4` the message is "almost done" (lines 253-259), and every 4 ticks it reports progress "continue(s) @x1 <T-NAME> (@x2% completed)." (lines 261-271). Each tick also adds `HYGIENE_COMMONDIRTY` (lines 273-277). Quitting or shutdown aborts (lines 292-297). Starting a job cancels other common-skill effects and any other AstroEngineering on the character (lines 448-458).

**Enhance wording.** ENHANCE picks a random phrase from the TECH_BABBLE verb, adjective and noun lists (`core/CMProps.java:583-586`; `AstroEngineering.java:362-374`). The phrase is flavour only.

## Content worth mining

**Ranger skills.**

| ID | Name shown | Domain | Core effect | Usage |
|---|---|---|---|---|
| Ranger_Track | Track | COMBATLORE | Trail to a named mob, range 50 + 2X + 10X(max) | MOVEMENT |
| Ranger_TrackAnimal | Track Animal | COMBATLORE | Trail to nearest animal, same range | MOVEMENT |
| Ranger_FindWater | Find Water | NATURELORE | Trail to nearest wet room or liquid, range 60 + ... | no override |
| Ranger_Hide | Woodland Hide | STEALTHY | IS_HIDDEN; save detection +prof +2X; broken by noise | MOVEMENT and MANA |
| Ranger_WoodlandCreep | Woodland Creep | STEALTHY | Hidden and sneaking; speed 0.5; jungle, swamp or woods only | MOVEMENT and MANA |
| Ranger_Sneak | Woodland Sneak | STEALTHY | One-step sneak; difficulty x10 or x5 by bystander level | MOVEMENT |
| Ranger_SetSnare | Set Snare | TRAPPING | Places Trap_Snare in a wilderness room | MOVEMENT and MANA |
| Ranger_SenseTraps | Sense Snares and Pits | NATURELORE | Automatic report on room entry | none |
| Ranger_FierceCompanions | Fierce Companions | ANIMALAFFINITY | Automatic attack and damage bonus beside an animal ally | none |
| Ranger_HuntersEndurance | Hunters Endurance | FITNESS | Automatic while tracking untrackable | none |
| Ranger_WoodlandLore | Woodland Lore | NATURELORE | Automatic wilderness shifts to damage, attack and armour | none |
| Ranger_PlanarEnemy | Planar Enemy | MARTIALLORE (inherited) | Favored enemy is a plane | none |
| Ranger_Enemy3, Ranger_Enemy4 | Favored Enemy 3, Favored Enemy 4 | MARTIALLORE (inherited) | Empty slots | none |
| Skill_RacialEnemy | Racial Enemy | DOMAIN_RACIALABILITY | Racially granted favored enemy | none |

**Super powers.**

| ID | Name shown | Target | Effect | Trigger |
|---|---|---|---|---|
| Power_OctoGrapple | Octo-Grapple | Mob | Binds target and caster; STR-based struggle | GRAPPLE |
| Power_WebSpinning | Web Spinning | Mob, item or exit | Binds; budget 160 x speed | WEBSPIN |
| Power_OctoArms | Octo-Arms | Self (auto) | +4 arms; extra attacks each combat tick | auto |
| Power_SuperClimb | Super Climb | Direction | Climb with temporary IS_CLIMBING | CLIMB, SUPERCLIMB |
| Power_TrapSense | Trap Sense | Self (auto) | Reports traps on room entry | auto |

**Sample player messages** (quoted briefly): "You creep into some foliage and remain completely still."; "The water trail seems to continue @x1."; "The trail dries up here."; "You have set the snare."; "<S-NAME> trigger(s) the snare on accident!"; "You sense a trap to @x1, and @x2."; "<S-NAME> continue(s) @x1 <T-NAME> (@x2% completed)."; "You gain some new insights about @x1."

**Weapon-skill catalogue.** See the family table in section 6, which lists 17 groups over three tiers.

## Notable design ideas

- **Three tiers, three questions.** Permission (can this character use the class?), combat (how well does the character fight with it?) and provenance (was it made by this character?) are separate abilities over one base. The provenance tier uses the crafter's name as a brand (`Familiarity_Weapon.java:102`).
- **Declarative weapon classes.** The zapper mask `-CLASSTYPE +Weapon -WEAPONCLASS +Axe` (`Proficiency_Weapon.java:111-116`) is compiled once and used as a filter. A tri-state result (ALLOWED, DENIED, NOTAPPLICABLE) lets an ability override the class rules without replacing them.
- **Trails as a single integer state.** A trail is an ordered room list, and the tracker's state is one integer (direction, or 999 for arrived, or -1 for dried up). The player's look is the clock for players, while monsters walk themselves in the same tick. One code path serves both.
- **Stealth broken by the message stream.** Hide ends on any sound, speech, enter, leave or recall message (`Ranger_Hide.java:107-122`), with no polling.
- **Struggle budget.** Web Spinning's escape budget is `160 * speed` and each struggle spends `STR + level` (`Power_WebSpinning.java:207-208`, `129`). Faster creatures get a longer budget, so they are harder to web-escape.
- **Two-sided binding.** Octo-Grapple applies its effect to both the target and the caster, and the caster loses two arms while the grapple holds (`Power_OctoGrapple.java:229-230`, `159-160`).
- **Tech jobs as numbers.** A job produces a number (value), not logic. The item decides what the number means: install sets a factor, repair sets uses, enhance sets a factor. Expertise is learned per manufacturer and per tech type (`AstroEngineering.java:138-178`).

## Problems and cruft

Bugs and oddities found by reading. None was run.

1. **Wrong base for OctoGrapple's disposition.** `Power_OctoGrapple.java:152`: `affectableStats.setDisposition(affectableStats.sensesMask()|PhyStats.IS_BOUND)`. Every other disposition edit in this bucket ORs into `disposition()`, for example `Ranger_Hide.java:131` and `Power_SuperClimb.java:86`. Grep finds no other use of `sensesMask()` inside `setDisposition`. Reading suggests the bound creature's other disposition bits (posture, hidden, sneaking) are replaced by the sense mask. Unverified at runtime.
2. **Dead branch in OctoGrapple.** `Power_OctoGrapple.java:129-134` tests `fetchEffect(ID()) == null`, then fetches the same effect and unInvokes it if non-null. The inner branch cannot run.
3. **Discarded return value.** `Power_OctoGrapple.java:229-230`: the result of the first `maliciousAffect` is overwritten, so success reflects only the caster's effect.
4. **Unreachable loop in TrapSense.** `Power_TrapSense.java:105-112` compares `R.getExitInDir(d) == P` where `P` is the room. The comparison is never true, so the recursive call never runs.
5. **Double roll in SuperClimb.** `Power_SuperClimb.java:112` and `117` roll proficiency twice; the first result is discarded. The climb happens regardless of the roll (lines 119-135).
6. **Minimum-ticks term that almost never adds.** `AstroEngineering.java:444-445`: `minTicks += round((installedFactor - 1.0) * 0.01)`. For any factor below 1.5 this adds 0. Install value is 50 plus a bonus of at most 50 (line 201, `getBonus`), so the factor set by install is at most 1.0 while expertise stays at or below 100%. Probably a scale error; intent unverified.
7. **Babble list reuse.** `AstroEngineering.java:369-372`: the second word comes from ADJ1 plus ADJ2 combined, and the third word from ADJ2 alone. ADJ2 is weighted more heavily, and ADJ1 appears only in the second slot.
8. **Empty candidate pool in favored-enemy selection.** `Ranger_Enemy1.java:118-121`: if all categories are removed, `roll(1, 0, -1)` returns -1 (`Libraries/Dice.java:400-401`), and `choices.get(-1)` throws. Reachable only when the pool is exhausted, which the code does not check for.
9. **Hunter's Endurance reverts its own gains.** `Ranger_HuntersEndurance.java:136-143` restores the snapshot taken when tracking began. Fatigue and movement gained while tracking are therefore reset when tracking ends. Possibly intended; unverified.
10. **Proficiency rolled on every recalculation.** `Proficiency_Weapon.java:140` calls `proficiencyCheck` inside `affectCharStats`, which runs on every stat recalculation. The granted permission can change between recalculations. The cache at lines 142-152 prevents the filter array from growing, not the flicker.
11. **Armour bonus cache can go stale.** `Specialization_Armor.java:124-135`, `Familiarity_Armor.java:125-136` and `Familiarity_Shield.java:114-125` clear the cached bonus only on the holder's own wear, remove or drop, and on TYP_LIFE. Armour removed by other means (theft, disarm, destruction) is not noticed until the next such event. Unverified in practice.
12. **Duplicated trail engine.** The tick logic in `Ranger_Track.java:121-173`, `Ranger_TrackAnimal.java:118-170` and `Ranger_FindWater.java:121-173` is almost identical, as are the executeMsg bodies. `getHighestLevelMOB` is duplicated in `Ranger_Hide.java:150-177` and `Ranger_Sneak.java:96-121` with different filters: Hide skips sleepers and system operators, Sneak does not.
13. **Stealth-level helpers use two different rules.** The same concept has different filters in Hide and Sneak (item 12). Pick one.
14. **Side effect in a stat hook.** `Ranger_WoodlandLore.java:98-99` sets `invoker` inside `affectPhyStats`.
15. **Brittle de-duplication.** `Ranger_FindWater.java:227-228` decides whether a liquid note was already printed by searching the accumulated output string with `indexOf`.
16. **Repair guard tests the wrong bound (outside bucket).** `Items/CompTech/StdElecCompItem.java` sets `repairApplied` to at least 1 only when the value is below 0 (`if(repairApplied < 0)`). The value is never negative here, so the guard never fires and a zero repair stays zero.
17. **Sling mask is hard to read.** `Proficiency_Sling.java:68` appends `-SUBNAME +*sling* +WEAPONAMMO - ` with a trailing dash. This differs from `Proficiency_Bow.java:70` (`-WEAPONAMMO +arrow +arrows`). Zapper parsing was not verified (`Libraries/MUDZapper.java:4793` and the WEAPONAMMO cases near 1456, 1462 and 6470).
18. **Multiplayer and admin assumptions.** Hide skips system operators (`Ranger_Hide.java:170`). Groups are skipped in several places (`getGroupMembers`, `Ranger_Hide.java:159`, `Ranger_Sneak.java:105`, `Ranger_FierceCompanions.java:131`). Tracking and stealth read `isPlayer` through `CMLib.coffeeTables` and `CMLib.achievements` (`StdAbility.java:1820-1826`). Power_OctoGrapple assumes another mob will be held, and FindWater and Track assume named targets.

## Takeaways for a Python SUD

- **Skill check as a pure function.** Port `proficiencyCheck` as one function so every skill uses the same roll. Keep the clamp, the level bonus and the gate for negative adjustments, and note that a negative adjustment is a gate, not a discount. Sketch (derived from `StdAbility.java:1441-1485`):

  ```python
  import random

  def skill_check(proficiency: int, level: int = 0, adjustment: int = 0,
                  rng: random.Random = random) -> bool:
      chance = min(95, max(5, proficiency + 2 * level)) + level // 2
      if adjustment < 0:
          if rng.randint(1, 100) > 100 + adjustment:
              return False
      else:
          chance += adjustment
      return rng.randint(1, 100) < chance
  ```

- **Three tiers as data.** Model each weapon skill as one record with a set of classes, a level and a proficiency. Permission becomes a predicate, combat becomes a formula, and provenance becomes a crafter-name check. Round the way Java does (`math.floor(x + 0.5)`) if you want the same values; Python's `round` is banker's rounding.
- **Trackers as BFS.** Compute the route to the destination with a breadth-first search over the room graph, with predicates in place of the zapper flags. Store the route as a list. Expose `next_step(route, here)` returning a direction, `ARRIVED`, or `DRIED_UP`. Drop the integer sentinels in favour of an enum. Keep the rule that players act by looking and that monsters move on their own.
- **Stealth as status.** Model Hidden and Sneaking as booleans on the player. A noise event (speech, move, combat) clears them. This is a simple event handler, not a polling loop.
- **Traps as room objects.** A `Snare` attached to a room, sprung by entities that enter it. Perception is a check on room entry that returns a list of messages. Keep the wilderness-only rule as a terrain tag.
- **Jobs as timed actions.** Model the AstroEngineering pattern as a `Job` with a tick count, interrupt conditions (combat, leaving the room, incapacitated) and a result enum. Keep expertise per (manufacturer, category) as a dictionary learned by use. Compute the job result from skill and expertise, and let the target object apply it.
- **Binding effects with a struggle budget.** Use `budget = 160 * speed`, spend `STR + level` per struggle, and let the effect end when the budget runs out. Keep the veto (an effect that blocks actions) as a hook such as `can_act(action)`. The CMMsg two-phase system (veto, then execute) is worth borrowing only in this simplified form.
- **Drop for single-player.** Group and companion logic, sysop exemptions, `isPlayer` bookkeeping, MSP sound cues, hygiene from work, shop-stock scanning for liquids (unless the world has shops), and the babble lists as anything more than optional flavour. The zapper mask language can be replaced by Python predicates.

## Coverage

- **Read in full (39 bucket files):**
  - Ranger: Ranger_FindWater, Ranger_Track, Ranger_TrackAnimal, Ranger_Hide, Ranger_SetSnare, Ranger_WoodlandCreep, Ranger_Sneak, Ranger_SenseTraps, Ranger_PlanarEnemy, Ranger_FierceCompanions, Ranger_HuntersEndurance, Ranger_WoodlandLore, Skill_RacialEnemy, Ranger_Enemy3, Ranger_Enemy4.
  - SuperPowers: SuperPower, Power_OctoGrapple, Power_WebSpinning, Power_TrapSense, Power_OctoArms, Power_SuperClimb.
  - Tech: TechSkill, AstroEngineering.
  - Specializations: Specialization_Weapon, Proficiency_Weapon, Familiarity_Weapon, Specialization_Armor, Familiarity_Armor, Familiarity_Shield, Specialization_Shield, Specialization_Natural, Specialization_Whip, Specialization_SiegeWeapon, Specialization_Ranged, Specialization_ThrownWeapon, Specialization_Bow, Familiarity_Bow, Proficiency_Bow, Proficiency_Sling.
- **Skimmed (31 bucket files):** the one-class stubs in Specializations (Specialization_ for Axe, BluntWeapon, Dagger, EdgedWeapon, FlailedWeapon, Hammer, Polearm, Staff, Sword; Familiarity_ for Axe, BluntWeapon, Dagger, EdgedWeapon, FlailedWeapon, Hammer, Natural, Polearm, Ranged, Staff, Sword; Proficiency_ for Axe, BluntWeapon, Dagger, EdgedWeapon, FlailedWeapon, Hammer, Natural, Polearm, Ranged, Staff, Sword). Checked by grep for declarations and weapon-class assignments; each has only the standard identity overrides (ID, name, displayText, canAffect and canTarget, abstractQuality, classificationCode), a constructor, and a weapon-class assignment.
- **Outside the bucket, read for interaction:** Ranger_Enemy1.java (full); StdAbility.java excerpts (lines 419-464, 627, 899-925, 1441-1485, 1810-1850, 2185-2253); Libraries/MUDTracker.java (lines 405-494, 771, 1642); Libraries/Sense.java (lines 153-155, 693-697); Libraries/Dice.java (lines 398-412); Items/interfaces/Weapon.java (lines 138-160); Common/interfaces/CMMsg.java (lines 1166, 1198-1200, 1611-1615); Items/BasicTech/StdElecPanel.java (lines 155-190); Items/CompTech/StdElecCompItem.java (lines 284-320); Items/Basic/StdItem.java (lines 1150-1156); CharClasses/StdCharClass.java (lines 895-925); Libraries/CoffeeUtensils.java (lines 1060-1080); Races/interfaces/Race.java:531; core/CMProps.java:583-586; Abilities/Traps/Trap_Snare.java:35 (class declaration only).
- **Not checked:** Ranger_Enemy2, Ranger_AnimalFrenzy and Ranger_Camouflage (other buckets); internals of Trap_Snare, StdPlanarAbility, ExpertiseLibrary, MaskingLibrary, ableMapper proficiency maxima and gain chance; race body-part defaults; tick length; the `guides/` documentation and `resources/help` entries for these skills (not consulted for this chapter). No build or test was run.
