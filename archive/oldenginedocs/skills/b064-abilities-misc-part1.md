# Abilities: Misc, part 1 of 3 (Acidbreath to Falling)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b064` (Abilities/Misc [part 1/3: Acidbreath .. Falling]) · 27 files · 9612 lines of Java.

## Summary

This bucket holds the first 27 of the miscellaneous ability classes (Acidbreath through Falling). They sit on top of `StdAbility`, the 3018-line base class that almost every CoffeeMud ability extends. Almost none of them are spells a player casts. They are mostly property effects: an object attached to a mob, item, room or area that changes its stats or reacts to game messages. The group covers injuries (broken and amputated limbs, bleeding, falling, burning), bodily conditions (addiction, allergy, sleep, diet), growth (`Age` turns a baby into a new character), clock-driven rules (`AutoAwards`, `AutoStack`, `Dusty`), social and legal effects (`Banishment`, `Dueler`, `Adorable`) and the dragon-breath family. Several depend on multiplayer machinery (player accounts, legal warrants, PLAYERKILL), but the patterns are portable: a timed-effect lifecycle, a body model with named limbs and healing countdowns, decay timers, and small data tables.

## Key files and types

Citations are relative to the CoffeeMud folder. In this chapter the prefix `com/planet_ink/coffee_mud/` is dropped, so `Abilities/Misc/Age.java:1239` means `com/planet_ink/coffee_mud/Abilities/Misc/Age.java:1239`.

| File or class | Role | Lines |
|---|---|---|
| `Abilities/StdAbility.java` | Family base for all of these (invoke pipeline, costs, proficiency, durations, targeting, teaching). Outside this bucket; read in full because the bucket depends on it | 3018 |
| `Abilities/Misc/Dragonbreath.java` | Breath family base: racial breath attack, colour-to-element table, dust effect | 437 |
| `Abilities/Misc/Acidbreath.java` | Breath fixed to acid | 67 |
| `Abilities/Misc/Age.java` | Growth and life stages; babies age, change race, and grow into player characters | 1563 |
| `Abilities/Misc/Amputation.java` | Removes limbs; missing-limb effects; drops a limb item | 963 |
| `Abilities/Misc/BrokenLimbs.java` | Breaks limbs; blocks actions; healing countdowns | 766 |
| `Abilities/Misc/AutoAwards.java` | Applies date- and player-masked awards (abilities or behaviours) to a mob | 568 |
| `Abilities/Misc/Burning.java` | Fire on items: burn timers, spread, weather, explosions, ash | 528 |
| `Abilities/Misc/Falling.java` | Falling (and reversed rising) for mobs and items; landing damage | 484 |
| `Abilities/Misc/Addictions.java` | Craving clock for substances and effects | 394 |
| `Abilities/Misc/Dusty.java` | Dust builds up on items over doubling intervals | 338 |
| `Abilities/Misc/Banishment.java` | Bars a mob from entering or fleeing to banished areas | 314 |
| `Abilities/Misc/Allergies.java` | Random allergens: sneezing, poisoning on contact | 309 |
| `Abilities/Misc/AutoStack.java` | Room or area effect that packs identical items into stacks | 303 |
| `Abilities/Misc/Dueler.java` | Paired duel with state snapshot and restore on exit | 283 |
| `Abilities/Misc/ExtraData.java` | Generic key-value data store (extends `ThinAbility`) | 222 |
| `Abilities/Misc/CombatSleep.java` | Unconsciousness from a malicious sleep | 217 |
| `Abilities/Misc/Carnivorous.java` | Diet: flesh doubles nourishment, other food does nothing | 209 |
| `Abilities/Misc/Bleeding.java` | Caps HP while bleeding; leaves a trail of blood | 200 |
| `Abilities/Misc/CombatFrenzy.java` | Berserk: more damage and extra HP, less armour | 187 |
| `Abilities/Misc/Copyright.java` | Stops an item being written on, copied or renamed | 187 |
| `Abilities/Misc/Antidote.java` | Consumable item that cures poison effects | 182 |
| `Abilities/Misc/DiseaseCure.java` | Consumable item that cures disease effects | 180 |
| `Abilities/Misc/Blessing.java` | Good-aura effect: armour bonus, lifts curses | 161 |
| `Abilities/Misc/Adorable.java` | Racial trait: charisma bonus; lower-level onlookers cannot attack | 155 |
| `Abilities/Misc/Cowering.java` | Fear effect: sits and cannot move | 155 |
| `Abilities/Misc/BadReputation.java` | Scales faction changes for the affected mob | 146 |
| `Abilities/Misc/AmbianceAdder.java` | Adds ambiance strings to any object | 94 |

## How it works

### 1. Shared base: `StdAbility`

Almost every class in the bucket extends `StdAbility` (`Abilities/StdAbility.java:42`). Two exceptions: `ExtraData` extends `ThinAbility` (`Abilities/Misc/ExtraData.java:37`), and `Acidbreath` extends `Dragonbreath`, which extends `StdAbility` (`Abilities/Misc/Dragonbreath.java:36`). The rules below therefore apply to nearly the whole bucket.

**State.** Each effect is one object attached to a host. Its fields are `invoker`, `affected`, `proficiency`, `miscText`, `tickDown` (default -1), `canBeUninvoked`, and `savable` (`Abilities/StdAbility.java:50-60`). A subclass stores its parameters in `miscText`, usually parsed in `setMiscText`.

**Invocation pipeline.** `invoke(mob, commands, target, auto, asLevel)` (`Abilities/StdAbility.java:1964-2086`) is the common path. Members do their own target and legality checks first, then call `super.invoke`. For a non-auto cast the base code:
1. checks that the caster is alive and awake (line 1974), enforces the cast interval (1978-1989) and blocks bound characters from movement skills (1991-1996);
2. computes the cost with `usageCost` (1998) and applies the compounding rule, where repeat casts inside a window get dearer (2001-2046);
3. checks the cost with `testUsageCost` (1925-1961, messages at 1932-1956), the minimum cast wait (2053-2066) and item components (2067, `checkComponents` at 2088-2111);
4. deducts mana, movement and hit points (2074-2076), calls `helpProficiency` (2077), and sends an MSG_PREINVOKE through `okMessage`/`executeMsg` (2078-2081).

Auto-invoked effects skip all of this and set `isAnAutoEffect` (line 2084).

**Cost.** `usageCost` (`Abilities/StdAbility.java:1696-1807`) takes a base mana value from configuration, reduces it by class-level excess as `consumed - (consumed/30)*diff` (1782-1784), and applies a floor. The floor defaults to 5 (`actualMinimum=5`, line 1756). HP, mana and movement costs can be adjusted by `HPCOST+`, `MNCOST+` and `MVCOST+` ability adjustments (1644-1676).

**Proficiency roll.** `proficiencyCheck` (1441-1486) takes the stored proficiency, adds `PROF+` adjustments and 2 points per XLEVEL (1468). The result is clamped to 5-95 unless the DIS955RULE security flag is set (1470-1476). XLEVEL/2 is added (1478), then the caller's adjustment. A negative adjustment can fail outright (1483-1484). `helpProficiency` (1810-1871) raises stored proficiency after use: it rolls `sqrt(INT)*34*random` (1837), is gated to once per five minutes per ability (1827), and has its own per-use chance (1839-1840).

**Levels and expertise.** `adjustedLevel` (899-925) starts from the lowest qualifying level in the ability mapper, adds class-level excess and `LEVEL+` adjustments, then adds XLEVEL (`getXLEVELLevel`, 444-447). The expertise types X1-X5, LEVEL, LOWCOST, MAXRANGE, TIME and XPCOST all come from `expertise()` (400-417). It reads expertise levels and then adds `charStats` circumstantial adjustments keyed `X<TYPE>+<ID|class|domain|*>`.

**Durations.** Two formulas cover most effects:
- Beneficial: `(casterLevel*4)+40` ticks, with a minimum of 5 (`StdAbility.java:2325`, `2279-2280`).
- Malicious: about `1.1*level + 25` ticks (2178), adjusted for the level gap against the target using `EXPRATE` (2140-2172), and capped.

An explicit tick count overrides both (2314-2315 and 2176-2177). `beneficialAffect` and `maliciousAffect` copy the prototype with `copyOf()`, set the invoker and call `startTickDown` (2341-2349, 2185-2208). `startTickDown` marks the effect unsavable (line 703), so timed effects do not survive a reboot. `makeLongLasting` (2825-2828) sets `tickDown` to `Integer.MAX_VALUE` for permanent effects.

**Ticking.** `tick` (`Abilities/StdAbility.java:2843-2862`) runs on each tick; it counts down `tickDown` only on `TICKID_MOB` and calls `unInvoke()` at zero. The default tick is 4000 ms (`core/CMProps.java:681`). `TICKS_FOREVER` is `Integer.MAX_VALUE-1000` (`Abilities/interfaces/Ability.java:46`).

**Messages.** Effects react through two hooks. `okMessage` can veto a message by returning false, and `executeMsg` runs after all vetoes pass. Both are empty in the base (2831-2840). Stat hooks `affectPhyStats`, `affectCharStats` and `affectCharState` are empty in the base (1547-1560). Subclasses override them to change stats, and the engine recomputes stats from base values plus all effects (`recoverPhyStats` and similar).

**Failure messages.** Failure helpers are `beneficialWordsFizzle`, `beneficialVisualFizzle`, `beneficialSoundFizzle` and `maliciousFizzle` (2210-2271), plus `failureTell` (1873-1902).

**Targeting.** `getTarget` and `getAnyTarget` resolve a named target from the room, from inventory, or from combat (1034-1234; items 1310-1376). `checkTargetRange` enforces min and max range (989-1016). `canTarget` and `canAffect` read bit masks `CAN_MOBS`, `CAN_ITEMS`, `CAN_ROOMS`, `CAN_AREAS` and `CAN_EXITS` (928-981). With no argument, a malicious ability picks the current victim, and a beneficial one picks the caster (1041-1063).

**Learning and teaching.** `canBeLearnedBy` (2468-2602) checks cost, class, level, a minimum intelligence of 2 (2504-2510) and prerequisites. `teach` and `practice` move proficiency from teacher to student (2751-2822).

**Copying and saving.** `copyOf` uses `clone()` (1424-1438), which is a shallow copy. A subclass with collections in fields must override `cloneFix`, as `ExtraData` does (`Abilities/Misc/ExtraData.java:199-204`). Save state is the `getStat`/`setStat` code list (2900-3003): `CLASS` and `TEXT`, plus internal codes `TICKDOWN`, `LEVEL`, `ISANAUTOEFFECT`, `NAME`, `NEXTCAST` and `CANUNINVOKE`. A subclass that keeps timestamps or lists outside `miscText` loses them on save. `Addictions` is an example (see section 5).

### 2. Breath family: `Dragonbreath` (and `Acidbreath`)

`Dragonbreath` is a racial attack for dragons, mephits and demons. It is malicious, targets mobs only, has range 10 scaled by expertise (`Abilities/Misc/Dragonbreath.java:113-135`), and is classed `ACODE_SKILL | DOMAIN_RACIALABILITY` (170-173).

**Colour.** `getBreathColor` (175-218) returns one character. An explicit `miscText` letter wins (210-211). A dragon with no text gets its colour by matching its name, then its display text, against a table (185-207). Anyone else with no text gets a random colour on every call (214-215).

The colour letters and their effects, from the `invoke` switch (297-388):

| Letter | Element | Weapon type and strike type | Notes |
|---|---|---|---|
| f | fire | burning, fire | |
| l | lightning | striking, electric | |
| c | cold | frosting, cold | |
| a | acid | melting, acid | |
| o | ooze | melting, acid | |
| s | slime | melting, acid | |
| g | gas | gassing, gas | 50% fizzle (355-357) |
| p | pebbles | bashing, justice | 50% fizzle (365-367) |
| u | death | bursting, undead | 50% fizzle (375-377) |
| d | dust | gassing, gas | no damage; applies a dust effect (407-411) |

The dragon colour table `DragonColors` (250-261) maps colour names to letters: WHITE c, BLACK a, BLUE l, GREEN g, RED f, BRASS f, COPPER a, BRONZE l, SILVER c, GOLD g.

**Damage.** For every colour except dust, `levelBy = (level + 2*XLEVEL + 2*X1)/4`, with a minimum of 1 (416-418). A normal breath deals `roll(levelBy, 6, levelBy)` (422). A `lesser` breath (set by a `LESSER` parameter, 156-161) deals `roll(levelBy, 4, 0)` (420). If `msg.value()` is positive, the damage is halved (423-424). For example, a level-10 caster with no expertise has `levelBy` 2, so a normal breath is 2d6+2 (4-14) and a lesser one is 2d4 (2-8).

**Dust.** Dust deals no damage. It calls `maliciousAffect` with `2 + (X1+XLEVEL)/4` ticks (409-411). The target then gets the effect, which sets `CAN_NOT_BREATHE` unless the target can breathe dust (148-153).

**Acidbreath.** It fixes `text()` to "acid" (`Abilities/Misc/Acidbreath.java:49-52`) and ignores its input in `setMiscText` (54-58). The `lesser` flag, which only `Dragonbreath.setMiscText` sets, therefore cannot be enabled on it.

### 3. Limbs: `Amputation` and `BrokenLimbs`

**Data model.** Missing and broken limbs are semicolon lists in `miscText`, such as "left arm;right leg". They are parsed by `affectedLimbNameSet` (`Abilities/Misc/Amputation.java:337-372`; `BrokenLimbs.java:405-433`). Counts per body part are kept in arrays: negative counts in `amputations[]` (line 362) and `brokenParts[]`. Body-part indexes come from `Race.BODY_*` (`Races/interfaces/Race.java:521-553`): antenna 0, eye 1, ear 2, head 3, neck 4, arm 5, hand 6, torso 7, leg 8, foot 9, nose 10, gills 11, mouth 12, waist 13, tail 14, wing 15, and `BODY_PARTS` = 16.

Which parts can be lost or broken is fixed by two tables:
- Can be amputated (`Amputation.java:130-146`): antenna, eye, ear, arm, hand, leg, foot, nose, tail, wing.
- Can be broken (`BrokenLimbs.java:128-144`): antenna, arm, hand, leg, foot, nose, tail, wing.

Losing some parts also takes sub-parts with it (`Amputation.java:148-164`). Losing an arm adds the hand. Losing a leg adds the foot. Losing a head adds ear, eye, mouth, nose, antenna and gills, and losing a neck also adds the head. These are added only when the body has the part (605-625).

**Amputation effects.**
- No leg: the mob sits, and if the arms are also gone it cannot move (`Amputation.java:197-209`). No eye: cannot see (210-211). No ear: cannot hear (212-213).
- Movement is cut to 1/8 without leg armour and 1/4 with it (222-227). Losing a foot cuts it to 1/4 or 1/2 (230-235).
- Body-part counts change through `alterBodypart` (239-250). Finger and wrist wear is blocked on the affected side (`LEFT_LOCS`/`RIGHT_LOCS`, 127-128; 349-368).
- `canBeUninvoked` returns false (99-103), so the state persists until a limb is restored. `restoreLimb` (449-475) is the path used by regrowth. `Abilities/Prayers/Prayer_Regrowth.java:160` calls it.

**Amputation invocation** (`Amputation.java:687-962`) runs in this order:
1. A warrant is needed, unless the caster has ABOVELAW or the target is a non-monster group member (734-749).
2. The wielded weapon must be piercing or slashing (760), unless the caster is a sysop.
3. In combat the caster must be in melee range (765-769).
4. The victim must be asleep (772-776). For a player group member, the victim's session gets a Y/N confirmation prompt (782-831). Otherwise the victim must be bound and asleep (833-836).
5. The limb is picked at random from the unaffected set, or named by the caster (856-911). The message is "amputate(s)" in fight colour (916-918).

**Limb damage** (`damageLimb`, 500-659) shows a falls-off or destroyed message (515-541). It creates a generic limb item of weight 5, with its material taken from the race's resources (562-587), and drops it into the room (591-597). It then cascades to other `LimbDamage` effects on the same host and removes a shared limb from them (637-648). Finally it rolls for bleeding. `Bleeding` is applied when a percentile roll is within `INJBLEEDPCTCHANCE` (default 100) and the victim is at least `INJBLEEDMINLEVEL` (default 15) (650-657; `core/CMProps.java:3167-3169`).

**BrokenLimbs effects.** These are the action gates (`BrokenLimbs.java:240-315`):
- A broken leg stops standing unless the character is asleep (248-257), and blocks advancing and retreating (258-268).
- A broken hand blocks wielding (269-278). A broken hand or arm blocks weapon attacks with the wielded item (279-289).
- A broken hand or finger blocks putting on and taking off finger items (290-309).
- A broken leg cuts movement the same way as amputation (222-235). Broken arms, legs, feet and hands change body-part counts. Fingers are not included (211-214).
- A broken leg with no leg armour, plus a broken arm, sets CAN_NOT_MOVE (197-203).

**BrokenLimbs healing.** Each broken limb has a countdown in `setBones`, which starts at `HEALING_TICKS` (line 52). That value is `(10 minutes)/tick`, which is 150 ticks at the default 4-second tick. Nothing heals limbs naturally here. A countdown starts only when the mob receives a healing effect that carries `FLAG_HEALINGMAGIC` and is not unholy (`executeMsg`, 170-184). The heal fraction is the amount divided by max HP, with a floor of 0.05 (180-181). Every countdown drops by `HEALING_TICKS*fraction` (182). After that, each MOB tick drops every countdown by 1 (tick, 478-485). A countdown that reaches zero calls `restoreLimb` and prints "is now mended" (552).

So a heal worth a fifth of max HP removes 30 of the 150 ticks. The remaining 120 ticks then take about eight real minutes at the default tick.

**Cross-effects.** Falling into the ground can break a limb through `BrokenLimbs` (`Abilities/Misc/Falling.java:166-183`, see section 4). `Amputation` cures the same limb in `BrokenLimbs` by the `LimbDamage` cascade (637-648).

### 4. Environment: `Falling` and `Burning`

**Falling** (`Abilities/Misc/Falling.java`).
- Applies to mobs and items. Falling is started only by code with `auto=true` (`invoke`, 422-425). An item needs a room set first (429-430). Callers include `Abilities/Spells/Spell_ReverseGravity.java:120-150`, `Abilities/Druid/Chant_Drifting.java:133`, and `Abilities/Properties/Prop_WeakBridge.java:139` and `Prop_NarrowLedge.java:154`.
- `proficiency() == 100` means reversed, so the rising case is a flag stored in the proficiency field (`reversed()`, 93-96; `setMiscText`, 123-138).
- Each fall tick a mob that is not in flight (222-227) either stops (`stopFalling`, 140-190) or is walked one room down (248). It takes damage `roll(1, round(maxHP*0.1*baseWeight/150), 0)`, capped at maxHP/3 (240-242). Damage accumulates while falling.
- A character who lands and took more than 10% of max HP in damage gets a random unbroken limb broken (`stopFalling`, 166-183).
- While falling, a move command fails with "You are too busy falling to do that right now." (`okMessage`, 368-374), except a flee or a hit ceiling.
- Items fall one room per tick, and may hit a mob (5% per mob, 307) or a boardable item (15%, 324) when they land.

**Burning** (`Abilities/Misc/Burning.java`).
- Applies only to items (`canAffectCode` CAN_ITEMS, line 63). `invoke` is a no-op unless `auto` is set (461-462). Mobs are not set alight, but their random carried items are, and a mob hit takes `roll(1, baseHP/10, 0)` damage (468-481).
- The flags are bits of `abilityCode`: low bits are a weather-extinguish chance, plus PERSISTFLAGS, DESTROYHOST, NODESTROYHOST, UNEXTINGUISHABLE and NOSPREAD (`Burning.java:78-114`).
- Each tick an item lying in a room (not in a container) can go out. The base chance comes from the flags, which is 0 by default. Weather adds rain +10, thunderstorm +15, sleet +5, blizzard +10 and snow +10 points (139-181).
- When the timer runs out (`tickDown<2`, 183), the effect acts on the item by owner:
  - Owned by no one: destroy it.
  - Lying in a room: if the item is a resource (raw material) or in a container, it can spread to one identical neighbour in the same room (216-235). Drinks in a container lose one unit of liquid to make a flammable resource (203-215). Liquids, metals, minerals and similar do not burn (242-249). A bonus item explodes for `roll(level,5,1)` damage to everyone present (253-270). Anything else leaves "some ash" (273-281). A raw material over 1 weight loses 1 weight and burns again for its burn duration (282-292).
  - Carried by a mob: most materials are destroyed (306-322).
- Touching a burning item (get, push or pull) can hurt the toucher through `ouch`. The check is a percentile roll above fire save minus 50 (364-389, damage `roll(1,5,5)`).
- Pouring a liquid into a burning drink container puts the fire out (`executeMsg`, 441-446).

### 5. Bodily conditions: `Addictions`, `Allergies`, `Carnivorous`, `Bleeding`, `CombatSleep`

**Addictions** (`Abilities/Misc/Addictions.java`) tracks a set of named triggers. A trigger can be an item name, a material name, or an ability (`effect:<ID>` or `effect:name:<name>`).
- Constants: `CRAVE_TIME` is 1 hour (46) and `WITHDRAW_TIME` is 1 day (47).
- Each trigger has a last-satisfied timestamp (`lastFix`, 48). Eating or drinking a matching item, sniffing a matching dust, or lighting a matching item in the mouth refreshes it (`executeMsg`, 299-329; `maybeFixFix`, 256-296).
- Each tick, once the trigger is more than an hour unsatisfied, a craving message is sent with chance `hours since last satisfied` percent (136-138). Seven flavour lines follow (174-197). After a day unsatisfied the addiction is kicked and removed (156-164).
- The effect is non-uninvokable and auto-invoked (`isAutoInvoked` 104-107, `canBeUninvoked` 110-113).
- Displayed as "(Addiction to ...)" for triggers past the craving threshold (`displayText`, 59-77).

**Allergies** (`Abilities/Misc/Allergies.java`) picks allergens at random when invoked. Each resource material and each race has a 1-in-N chance (`roll(1,N,0)==1`, 282), excluding liquids, energy, gas, cotton, iron, wood and the caster's own race (262-267). Eating or drinking an allergen invokes `Poison_Heartstopper` on the eater (195-202). Picking up an allergen invokes `Poison_Hives` (204-210). Allergic races cause `Poison_Hives` on contact (212-224). Every 11 ticks the affected mob sneezes if an allergen or allergic race is nearby (134-185). Race allergies take priority: resource sneezes run only when there are no race allergies (`else if`, 146-157).

**Carnivorous** (`Abilities/Misc/Carnivorous.java`) is a racial trait for orcs, cats, goblins and great cats (`Races/Orc.java:110`, `Races/Cat.java:131`, `Races/Goblin.java:124`, `Races/GreatCat.java:131`). Eating flesh adds the nourishment again, so it counts double (`okMessage`, 104-118). Any other food resets hunger to its old value and says "That didn't seem very nourishing." (119-131). The diet text it stores at invoke (180-205) is never read by this class.

**Bleeding** (`Abilities/Misc/Bleeding.java`) caps the mob's HP at the level recorded on its first tick (`hpToKeep`, tick, 161-176). Mana and movement are capped at the same fraction of max. Healing that lifts HP above half max ends the bleeding (`executeMsg`, 112-117). While leaving through an exit, the character leaves a hidden "trail of blood" item in the room (`executeMsg`, 124-150). The effect lasts `casterLevel*4+40` ticks by default (`invoke` 197).

**CombatSleep** (`Abilities/Misc/CombatSleep.java`) is a malicious sleep. It sets IS_SLEEPING (`affectPhyStats`, 139), blocks all the target's own actions (`okMessage` 121-127), and lasts `3 - levelDiff` ticks, where levelDiff is clamped to 0-2 (180-185, 201). It is cast by `Abilities/Fighter/Fighter_SleeperHold.java:81`.

### 6. Growth: `Age`

`Age` (`Abilities/Misc/Age.java`) is a long lifecycle attached to babies, which may be items (a caged animal) or mobs. The lifecycle is driven by a time stamp.

**Data model.** `miscText` can hold a life-stage name (matched against `Race.AGE_DESCS`, 307-315), a number of years (below `Short.MAX_VALUE`), or a birth time stamp in milliseconds (`getAgeCode`, 297-345). A value of "+N" means born N MUD hours ago (`setMiscText`, 452-456). Years are derived as elapsed ticks over `months*days*TICKSPERMUDDAY` (`divisor`, 557-559). One MUD hour is 600000 ms by default (`core/CMProps.java:682`).

**Stages.** `Race.AGE_DESCS` (`Races/interfaces/Race.java:504-514`) names nine stages. Each race has an aging chart with a start year per stage. The default chart is `{0,1,3,15,35,53,70,74,78}` (`Races/StdRace.java:59`). The code uses chart indexes 1 (toddler), 2 (child), 3 (young adult) and 5 (mature) as thresholds.

**Ticks and checks.** The age check runs from `affectPhyStats` (1472-1476), `tick` (1490-1496), and after each message (1422). Real time limits it to once a minute (`doPeriodicAgeCheck`, 1228-1242, line 1239). When the age changes, a TYP_AGE message goes out first, and a listener can veto it (`setAgeYears`, 264-291, veto at 277-281). A race change follows if the metamorphosis table says so (`setAgeRace`, 195-262). That table is `resources/skills/age.txt`.

**Metamorphosis table** (`resources/skills/age.txt`, 10 rows, tab-separated: from-race, operator, stage index, sex, to-race):

| From | Test (stage index 3, young adult) | Sex | To |
|---|---|---|---|
| Calf | at or above | male | Bull |
| Calf | at or above | any | Cow |
| Kitten | at or above | any | Cat |
| Puppy | at or above | any | Dog |
| Cub | at or above | any | Bear |
| Bull | below | any | Calf |
| Cow | below | any | Calf |
| Cat | below | any | Kitten |
| Dog | below | any | Puppy |
| Bear | below | any | Cub |

**Caged babies** (items, 570-707). A caged baby waits for an owner to follow. When it reaches toddler age and has a follower, it hatches into a live mob with reduced stats (charisma 10, constitution 7, dexterity 3, intelligence 3, strength 2, wisdom 2; 633-639), 2 HP, 10 mana and 20 movement (642-644), and it is bound to the liege (645). Its cage is destroyed (684-701). A rare disease roll is made at 1 in 1000 (649-653).

**Carried babies** (708-1126). At child age (chart index 2), the baby gets a MudChat behaviour and new stats (721-767). At young-adult age (chart index 3), it "grows up". If the room or name is not suitable, it stays a mob (827-865). Otherwise a new `StdMOB` player character is built, with legacy levels, clan membership and an account, then saved and announced (867-1109). The baby is destroyed afterwards (1107-1109). This is the multiplayer part of the system.

**NPC-carried babies** (1128-1225) follow the same stage steps, but at child, young-adult and mature (chart 2, 3 and 5). At mature age they drop the effect and are considered grown.

**Other behaviour.** An examine shows the age while the baby is young (1298-1331). The Emoter behaviour on a baby is switched among three sets of lines, depending on who holds it (1338-1374; the lines are in 146-148). Babies can get the Soiled effect at a 10% chance every 30 real minutes (1377-1421).

### 7. Clock-driven effects: `AutoAwards`, `AutoStack`, `Dusty`

**AutoAwards** (`Abilities/Misc/AutoAwards.java`, library `Libraries/AutoAwards.java`). The effect is auto-invoked and non-uninvokable (`isAutoInvoked` 88-91, `canBeUninvoked` 94-97).
- Each library entry (`AutoProperties`) has a player mask, a date mask and a list of awards. An award is an ability or behaviour ID with parameters.
- Each tick the effect compares the mob's home clock with its last reading (201-219, 229-239). When the hour, day, week, month, season or year changes, it rebuilds the list of matching entries (`gatherTimelyEntries`, 116-153; `assignMyEntries`, 380-413). The period check uses `TimePeriod` values (233-238). If the library's hash changes, it reassigns (201-209).
- An award becomes an ability with `setMiscText(parms)` and proficiency 100 (`applyAward`, 155-177). A behaviour name is used when no ability matches (179-189).
- A reverser effect flips every `+` and `-` in the parameters (159-171), so that the same awards become penalties.
- Named suppressor, reverser and holder effects are wired through `setStat` (437-463): a suppressor turns all awards off, a reverser negates them, and a holder keeps them active.
- The applied set is saved as a hash of award IDs (`getStat`, 516-528; `setStat`, 470-510).

**AutoStack** (`Abilities/Misc/AutoStack.java`). On a room, it packs identical items into one `GenPackagedStack` when the room has at least `TRIGGERCOUNT` items (default 1000, line 90). Items are grouped by name, then by `sameAs` and by container. A stack needs at least `GROUPCOUNT` members (default 10, line 91; 105-158). Packing is in `packageAll` (97-230). The interval is `CHECKTICKS` (line 92; see Problems for the default). On an area it runs over every metro room (`tick` 280-300).

**Dusty** (`Abilities/Misc/Dusty.java`). Dust builds up on items over time.
- The first step comes after `6*20` MUD hours (line 86, 120 by default). Each later step doubles the interval (117-123). The level goes up one step at a time.
- Each level changes the look of the item (`affectPhyStats`, 126-164): "a bit dusty" (0), "dusty" (1), "a dusty <name>" (2), "<name>, covered in dust" (3), "<name>, covered in a thick layer of dust" (4), "something under a dense layer of dust" (5), and "a lump of thick dust" (6 and above).
- Dust is removed by weather (rain, snow, sleet, hail, storms, dust storms; `okMessage`, 170-212), by picking the item up (`executeMsg`, 289-318; the flavour messages come from `roll(1,7,-1)`), by dropping it (320-327), or by a "dust" or "clean" command in the room (214-278). The room command lowers every gettable item's level by one, or removes it from one named item.

### 8. Social, legal and duel effects: `Banishment`, `Dueler`, `Adorable`

**Banishment** (`Abilities/Misc/Banishment.java`) keeps a list of banned areas in a `LinkedList` (78). Its text is the area names (122-133).
- `okMessage` blocks recall (143-162), fleeing (164-173) and entering (174-182) into any area that is the same as, or in the metro area of, a banned one (`badDestination`, 80-96; metro test at 91). Each block shows a "geas" message.
- `invoke` takes "banish <target> from <area> [FOREVER]" (202-313). A `FOREVER` banishment copies the effect as permanent (268-276). Otherwise the banishment is timed by `beneficialAffect`, whose duration is the normal beneficial formula (300).
- The legal system calls it directly for sentences (`Behaviors/Arrest.java:1881-1896`). A judge invokes it for a criminal, then `startTickDown` sets the length in ticks.

**Dueler** (`Abilities/Misc/Dueler.java`) is a duel between two mobs.
- `init` (223-240) records the PLAYERKILL flag, a copy of current state, the current effects, the wimpy setting, and copies of weapons and armour. It then sets PLAYERKILL on the duellists (225-226).
- `okMessage` (151-179): if the duellist would die, the death is cancelled. The loser gets a "defeated" message, the death post-processing runs, and the duel ends (156-171).
- `unInvoke` (83-142) restores HP, mana and movement from the snapshot, removes effects added during the duel, restores equipment condition, and grants a LEGAL immunity for one tick (132-134).
- A duel ends if the partner leaves, dies, or is absent for 30 seconds (`tick`, 191-213; line 208).
- With UNFAIR set, the duellist cannot be camped (`affectPhyStats`, 216-221).

**Adorable** (`Abilities/Misc/Adorable.java`) is a racial trait for kittens, calves and cubs (`Races/Kitten.java:95`, `Races/Calf.java:89`, `Races/Cub.java:89`). It adds charisma `2 + XLEVEL/2` (`affectCharStats`, 116-119). Its `okMessage` (85-113) blocks a malicious action when all of these hold: the target is not in combat, the source is in the same room, the source is not the target's victim, the target can be seen, the source is more than `EXPRATE` levels below the target (adjusted by XLEVEL), and no security or legal exception applies. The target's proficiency roll (line 100) decides whether the block happens.

## Content worth mining

**Breath colours and effects** (section 2): ten colours, ten letters, two shapes of effect (damage or dust). Easy to port as a dictionary from colour to (element, damage type, fizzle chance).

**Life stages and metamorphosis** (section 6): nine stage names with a default year table, plus a 10-row table that changes an animal's species at a stage. The table is a clean data format, and the stage names are reusable.

**Limb tables**: the amputable and breakable flags for each of 16 body parts, plus the sub-part cascade for arms, legs, head and neck (`Amputation.java:130-164`; `BrokenLimbs.java:128-144`).

**Dust stages** (section 7) are seven clean descriptive levels for an item that gets dirtier over time.

**Craving and withdrawal lines** (section 5, `Addictions.java:174-197`) are seven varied messages that escalate. They can be paraphrased directly.

**Message samples** (paraphrased): falling "hit the ground", "hit the water", "hit the ceiling" (`Falling.java:145-165`); burning "is no longer burning", "is extinguished", "is on fire" (`Burning.java:293, 444, 379-382`); amputation "amputates ... falls off" (`Amputation.java:516-521`); allergy sneezing (`Allergies.java:153`); "just too adorable to do that to" (`Adorable.java:103`); bleeding "stop(s) bleeding" (`Bleeding.java:102`); and "Your exile has been lifted" (`Banishment.java:198`).

## Notable design ideas

- **Healing as a countdown per limb.** Each broken limb holds one countdown. A heal removes a share of the total in proportion to the amount healed. Ordinary ticks then remove the rest. Nothing heals by itself (`BrokenLimbs.java:170-184`, `462-485`).
- **Cascading loss.** Losing a limb also removes the parts that hang from it, and removing a limb cures a broken copy of the same limb in other effects (`Amputation.java:605-648`).
- **Weight-based fall damage.** Damage from falling scales with the faller's weight and max HP, and reversed falling uses the same code path (`Falling.java:240-242`, `93-96`).
- **Fire as a material process.** A burning raw resource loses weight each cycle and keeps burning until it is used up. Adjacent identical items can catch (`Burning.java:216-292`).
- **Craving clock.** The chance of a craving message each tick equals the hours since the last fix, and withdrawal comes after a day (`Addictions.java:136-164`).
- **Doubling decay** (`Dusty.java:117-123`): each dust step waits twice as long as the last, so cleanliness falls off quickly at first and then slowly.
- **Snapshot and restore for duels** (`Dueler.java:223-240`, `83-134`): death inside a duel is cancelled, and the duellists get their saved state back.
- **Reverser by sign flip** (`AutoAwards.java:159-171`): one effect negates a whole set of awards by inverting the sign of each parameter.
- **Stacking by collection** (`AutoStack.java:97-230`): clutter is packed into a single entity, so a room with 1000 items stays playable.
- **Generic key-value data** (`ExtraData.java:77-156`): any object can carry arbitrary key-value data through the same save interface.

## Problems and cruft

Each item below was found by reading the code. None was run. "As written" means the code does this, whether or not that was intended.

1. **AutoStack's default interval is 0.** `INTERVAL = (2 * 60) / 4000` (`AutoStack.java:79`) is integer arithmetic and evaluates to 0. So the default check runs every MOB tick rather than, apparently, every two minutes. The units look mixed up (seconds against milliseconds). Certain from the arithmetic.
2. **AutoStack's area branch always returns false.** In `tick`, the area path falls through to `return false` (`AutoStack.java:301`). Depending on how the tick loop treats false, the area stacking may run once and stop. Unverified.
3. **BadReputation's XLEVEL term never applies.** The code uses `(invoker()!=affected)&&(canBeUninvoked())` (`BadReputation.java:136`), and this class overrides `canBeUninvoked()` to return false (89-92). So `prof` is always 0. Certain.
4. **Age's "highest base class" logic compares against a constant.** `highestBaseLevel` is `final int ... = 0` (`Age.java:895`) and is never updated. The loop at 905-908 therefore takes the last class of any parent with a level above zero, not the highest. Certain.
5. **Age's password fallback.** When no account is found, the new character's password is set to the child's own name (`Age.java:1010-1012`). The two self-assignments on the liege's player stats (1001-1002) look like a forced save. Noted for review.
6. **BrokenLimbs can throw during healing.** `progressLimbHealing` removes entries from `setBones` while iterating its key set (`BrokenLimbs.java:464-473`). `STreeMap.keySet()` returns a read-only view over the live tree key set (`core/collections/STreeMap.java:208-210`). Removing an entry and then advancing the iterator will normally throw a ConcurrentModificationException when another entry remains. Two limbs healed in the same heal would hit this. Likely, not run.
7. **Addictions loses its timers.** `setMiscText` rebuilds `lastFix` with every trigger at 0 (`Addictions.java:117-123`). Adding a second addiction (`invoke`, 370-371) or reloading the saved text does the same. Timers are not in `getStat`/`setStat`. A trigger at 0 makes the delta huge, so the next tick kicks the addiction (156-164). As written, a new addiction probably disappears on its first tick. Likely, not run.
8. **BrokenLimbs weapon test is a no-op.** `ww.weaponDamageType()==TYPE_PIERCING && ... != SLASHING && ... != BASHING` (`BrokenLimbs.java:660-662`) reduces to "is piercing". The same check in `Amputation` (760) requires piercing or slashing. The two abilities disagree about which weapons are valid.
9. **Dragonbreath's colour is random on read.** For a non-dragon with no colour text, `getBreathColor` rolls at random each call (`Dragonbreath.java:214-215`), and `flags()` and `displayText()` call it (57, 85, 223). A flag or description can change between reads.
10. **Carnivorous stores a diet nobody reads.** `invoke` builds a random list of foods (`Carnivorous.java:180-205`), but the class never uses the text. The rule is hard-coded to flesh.
11. **Allergies suppresses resource sneezes.** The `else if` at line 157 means that a mob with any race allergy never sneezes at its resource allergens (146-157). This may be intended. Noted.
12. **Dusty changes state inside stat computation.** `affectPhyStats` advances the dust level and removes the effect (`Dusty.java:112-123`). Stat recomputation therefore has side effects. `CAN_ROOMS` is accepted as a target, but only the item branch changes stats.
13. **Age runs work inside stat recomputation.** `affectPhyStats` calls `doPeriodicAgeCheck` (`Age.java:1475`), which can create or destroy mobs and send messages during a stat pass.
14. **Falling stores direction in proficiency.** `proficiency()==100` means reversed (`Falling.java:93-96`). The field is overloaded, so a skill-level change would reverse a fall.
15. **Acidbreath cannot be made lesser.** Its `setMiscText` discards input (`Acidbreath.java:54-58`), so the `LESSER` parameter has no effect on it.
16. **Banishment's list is shared between copies.** The list is a final field (`Banishment.java:78`), and `copyOf` is a shallow clone (`StdAbility.java:1424-1438`). Copies therefore share one list. This may be intended for the effect and its prototype, but it is easy to get wrong.
17. **BrokenLimbs treats each hand as having one finger.** The extra pass in `unaffectedLimbSet` reuses the hand count for the finger slot (`BrokenLimbs.java:497-506`).
18. **AutoAwards uses an exception for control flow.** A ConcurrentModificationException is caught, the saved hash is cleared, and the tick returns (`AutoAwards.java:249-253`).
19. **Age's stage naming is inconsistent.** `AGE_MATURE` is 4 (`Race.java:494`), but the description at index 4 is "Adult" and "Mature" is at index 5 (504-514).
20. **BadReputation calls `super.executeMsg` twice** (`BadReputation.java:142` and 144). Harmless, because the base method is empty.
21. **Multiplayer assumptions** are throughout: PLAYERKILL and duels, legal warrants, player accounts and databases, channel announcements, session prompts. See the takeaways.

## Takeaways for a Python SUD

**Borrow.**
- **The timed-effect object.** A base class with `duration`, `on_apply`, `on_tick`, `on_remove`, `modify(stats)`, and two message hooks: `allow(action) -> bool` (may veto, with a message) and `after(action)`. Keep the veto and after phases separate, as `okMessage` and `executeMsg` do. Compute stats on demand from base values plus active effects, rather than mutating stored values.
- **Limbs.** A dict of limb name to count (`{"arm": 2, "leg": 2, ...}`), a set of missing and broken limbs, and a per-limb healing countdown that a heal shortens by its fraction of max HP. Put the cascade table (arm to hand, leg to foot, head to eyes and ears) in data, and gate actions on limb state (cannot stand, cannot wield, cannot climb).
- **Breath-like attacks.** A table from colour to (element, damage type, fizzle chance) and a damage formula `dice = (level + 2*expertise) // 4`. It is compact and easy to test.
- **Craving.** Store `last_satisfied_at` per substance (saved, which the Java code fails to do). Chance per tick equals hours since satisfied, with withdrawal after a day.
- **Decay.** Dust-like grime as a level with a doubling interval, shown through the item's description. Burning as a per-item timer, with an extinguish chance from weather.
- **Growth.** A stage table per species (`[0, 1, 3, 15, 35, 53, 70, 74, 78]`) and a metamorphosis table as CSV or YAML (species, operator, stage, sex, into). A single-player companion can grow, change its stats and gain behaviours, without creating a new account.
- **Snapshots.** For sparring or training, deep-copy the player's state, let the bout run, then restore. The Java duel does this, with a death veto.
- **Generic attributes.** `ExtraData` shows that any object can carry key-value data. In Python, a plain `attrs` dict on each entity is enough.
- **Reverse by sign.** An effect that negates a set of modifiers by flipping their signs is a short, readable trick.

**Simplify or drop.**
- Drop the player-account creation in `Age`, the legal warrants and arrest flows, the channel and friend notifications, and the session prompts.
- Drop PLAYERKILL and the duel rules that depend on other humans. Keep a single-player spar if it is useful.
- Keep `Adorable` as an NPC rule ("does not attack much weaker targets") rather than a racial charisma trait.
- Keep `Banishment` as "forbidden regions" for a world map, but make it data, not a legal process.
- `AutoAwards` is a good idea without the zapper masks. Date-based awards for a single player (seasonal bonus, anniversary of the game start) need only a clock and a rule list.

**Rethink for single-player.** Several effects assume other people: `Dueler`, `Banishment`, `BadReputation`, `Adorable`, `Carnivorous` (race), `Allergies` (race contact), `AutoAwards` (mask by other players). In a single-player game those need a different trigger, such as an NPC's own disposition or the world state. Timed effects are not saved here (`StdAbility.java:703`). A Python port should decide on purpose which effects persist across a save.

## Coverage

- **Read in full (code bodies):** all 27 bucket files: `Acidbreath`, `Dragonbreath`, `Age`, `Amputation`, `BrokenLimbs`, `AutoAwards`, `Burning`, `Falling`, `Addictions`, `Dusty`, `Banishment`, `Allergies`, `AutoStack`, `Dueler`, `ExtraData`, `CombatSleep`, `Carnivorous`, `Bleeding`, `CombatFrenzy`, `Copyright`, `Antidote`, `DiseaseCure`, `Blessing`, `Adorable`, `Cowering`, `BadReputation`, `AmbianceAdder`. Only the licence and import blocks at the top of each file were skipped.
- **Read in full, outside the bucket:** `Abilities/StdAbility.java` (3018 lines), the family base.
- **Skimmed:** `Abilities/ThinAbility.java`, read only for its class declaration and method list (`ExtraData` extends it).
- **Checked by grep only:** `CMClass.getAbility` (`core/CMClass.java:1194-1196`, `2317-2321`); `STreeMap.keySet` (`core/collections/STreeMap.java:208-210`); `CMParms.combine` null handling; `Libraries/MUDFight.java:373, 409-424` (armour and hit roll); `Items/Armor/StdArmor.java:58, 639`; `Commands/CharGen.java:182`; `Races/StdRace.java:59`; `resources/skills/age.txt`; `Races/interfaces/Race.java` constants; `Abilities/Prayers/Prayer_Regrowth.java:160`; `Abilities/Spells/Spell_ReverseGravity.java`; `Abilities/Druid/Chant_Drifting.java`; `Abilities/Properties/Prop_WeakBridge.java`, `Prop_NarrowLedge.java`; `CharClasses/Monk.java:291`; `Behaviors/Arrest.java:1058-1066, 1874-1902`; and the race files that list these abilities.
- **Skipped:** none of the bucket's files. Members that are not in this bucket (for example `Firebreath`, `Injury`, `Pregnancy`, `Regeneration`, `QuestBound`) are covered by parts 2 and 3.
