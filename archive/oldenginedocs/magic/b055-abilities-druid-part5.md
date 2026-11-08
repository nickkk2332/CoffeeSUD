# Druid chants, part 5: Rockthought to Summon Fog

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b055` (Abilities/Druid [part 5/7: Chant_Rockthought .. Chant_SummonFog]) · 49 files · 9520 lines of Java.

Citations are relative to `com/planet_ink/coffee_mud/`, as in the brief. Quoted message text is short flavour text from the code. The Druid chants are small `Chant` subclasses. This chapter covers the 49 files in the bucket plus the shared base `Abilities/Druid/Chant.java`, which is not in this bucket and was read as a dependency.

## Summary

The chants in this bucket are the druid's utility and manipulation spells. They fall into four families: sensing (reporting nearby water, metal, life, sentience, poison, age, pregnancy or resources), summoning (animals, elementals, a dust devil, a tree-golem worker, a flytrap, coral, fish food and a campfire, all tethered to the caster), environment (fog and cold on an area, darkness on a room, sacred ground, stone walking, shapelessness) and status effects on people and items (rust, aging, charm, polymorph guard, wards, root, stubbornness). Almost every chant is one class built on the same `invoke`, `tick`, `okMessage` and `executeMsg` hooks, and most timed effects last as long as whatever they created. For a single-player Python game the most reusable pieces are the sensing reports, the summon-and-tether lifecycle, the weather transition table, and the content tables (animal list, elementals, sapling crafts, coral and fish names).

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Druid/Chant.java` (base, not in bucket) | Base for every chant: verbal cast code, default quality, alignment and armour disruption, weather queue | 201 |
| `Abilities/Druid/Chant_SensePlants.java` | Base for the plant sense; detects matching resources in the current room and its exits | 214 |
| `Abilities/Druid/Chant_SenseGems.java`, `Chant_SenseOres.java`, `Chant_SenseFluids.java` | Subclasses of SensePlants that change `word()` (line 80) and, presumably, the sensed set (not read in full) | 97, 97, 95 |
| `Abilities/Druid/Chant_SenseWater.java` | Water sense: terrain and weather text, drinks and containers, look-trailer on examine | 340 |
| `Abilities/Druid/Chant_SenseMetal.java` | Sets `CAN_SEE_METAL`; adds a room look message on metal ground | 144 |
| `Abilities/Druid/Chant_SenseLife.java` | Reports breeding, non-golem life in adjacent rooms | 190 |
| `Abilities/Druid/Chant_SenseSentience.java` | Lists intelligent NPCs in a radius (one-shot) | 132 |
| `Abilities/Druid/Chant_SenseAge.java` | Reports a target's age, tiered by chant level (one-shot) | 214 |
| `Abilities/Druid/Chant_SensePregnancy.java` | Reports a pregnancy, tiered by chant level (one-shot) | 244 |
| `Abilities/Druid/Chant_SensePoison.java` | Lists poison effects on an object or unworn inventory (one-shot) | 139 |
| `Abilities/Druid/Chant_SpeakWithAnimals.java` | Language effect and animal chat (`implements Language`) | 506 |
| `Abilities/Druid/Chant_SummonAnimal.java` | One animal summon. Owns `determineMonster` and the 64-entry animal ID table | 302 |
| `Abilities/Druid/Chant_SummonAnimals.java` | Several animals per cast, reusing SummonAnimal's helpers | 196 |
| `Abilities/Druid/Chant_SummonElemental.java` | One of four elementals, with breath ability and alignment | 268 |
| `Abilities/Druid/Chant_SummonDustdevil.java` | Air elemental that carries loose items | 319 |
| `Abilities/Druid/Chant_SaplingWorkers.java` | Tree-golem "sapling" with a random crafting skill | 316 |
| `Abilities/Druid/Chant_SummonFlyTrap.java` | Flytrap that eats insect and arachnid corpses | 256 |
| `Abilities/Druid/Chant_SummonChum.java` | Spawns fish-bit food in watery rooms | 121 |
| `Abilities/Druid/Chant_SummonCoral.java` | Coral item, a subclass of `Chant_SummonPlants` | 176 |
| `Abilities/Druid/Chant_SummonFlower.java` | Flower item, a subclass of `Chant_SummonPlants` | 168 |
| `Abilities/Druid/Chant_SummonFire.java` | Magical campfire item that burns without fuel | 158 |
| `Abilities/Druid/Chant_SummonFear.java` | Makes nearby targets flee | 139 |
| `Abilities/Druid/Chant_SummonFog.java` | Sets area weather to fog, restores on expiry | 149 |
| `Abilities/Druid/Chant_SummonCold.java` | Shifts area weather toward cold (transition table) | 174 |
| `Abilities/Druid/Chant_SnatchLight.java` | Darkens the caster's room and strips light sources from its contents | 245 |
| `Abilities/Druid/Chant_StarGazing.java` | Ongoing outdoor meditation that drifts faction values | 254 |
| `Abilities/Druid/Chant_SpeedTime.java` | Fast-forwards the room's tickers while freezing ages | 141 |
| `Abilities/Druid/Chant_SpeedAging.java` | Ages items (rot) and creatures | 256 |
| `Abilities/Druid/Chant_SpeedBirth.java` | Shortens a pregnancy's remaining time | 136 |
| `Abilities/Druid/Chant_StrengthenSeed.java`, `Chant_StrikeBarren.java` | Edits pregnancy-effect stats, or blocks it | 147, 150 |
| `Abilities/Druid/Chant_StealFortune.java` | Moves the AutoAwards holder from target to caster | 195 |
| `Abilities/Druid/Chant_StoneFriend.java` | Malicious charm on golems and earth elementals, implemented as follow | 266 |
| `Abilities/Druid/Chant_Rockthought.java` | Makes a target repeat one visible action | 175 |
| `Abilities/Druid/Chant_RustCurse.java` | Rusts metal items carried by the target each tick | 170 |
| `Abilities/Druid/Chant_SacredEarth.java` | Room effect: blocks gathering, halves damage to plant and earth creatures | 213 |
| `Abilities/Druid/Chant_Shapelessness.java` | Weightless, with a 75% chance to block physical actions | 211 |
| `Abilities/Druid/Chant_Stonewalking.java` | Invisible and weightless inside rock | 204 |
| `Abilities/Druid/Chant_Shamblermorph.java` | Race swap into a shambler | 187 |
| `Abilities/Druid/Chant_Root.java` | Blocks movement | 180 |
| `Abilities/Druid/Chant_SongWard.java`, `Chant_SpellWard.java` | 34% block of hostile songs or spells (`rollPercentage()<35`) | 158, 158 |
| `Abilities/Druid/Chant_StabilizeForm.java` | Absorbs polymorph attempts, extends other polymorph timers | 162 |
| `Abilities/Druid/Chant_SiftWrecks.java` | Searches underwater rooms, using the caster's own summoned coral | 162 |
| `Abilities/Druid/Chant_Shillelagh.java` | Enchants a wood or vegetation weapon | 175 |
| `Abilities/Druid/Chant_SparkRunes.java` | Re-activates a runic scroll's chant spells | 180 |
| `Abilities/Druid/Chant_SoaringEagle.java`, `Chant_SnuffFlame.java` | Flying disposition; extinguish | 132, 109 |
| Out of bucket: `Abilities/Druid/Chant_SummonPlants.java` | Parent of SummonCoral and SummonFlower; builds plant items and ownership | 296 |
| Out of bucket: `Abilities/Druid/Druid_MyPlants.java` | Registry of the caster's summoned plants (referenced, not read) | n/a |

## How it works

### 1. Shared base and cast pipeline

`Chant` (`Abilities/Druid/Chant.java:36`) extends `StdAbility`. Its defaults are `abstractQuality()` = `QUALITY_OK_SELF` (line 89), `canAffectCode()` and `canTargetCode()` = `CAN_MOBS` (lines 101, 107), and `classificationCode()` = `ACODE_CHANT` (line 122). A "render mundane" misc text switches the classification to `ACODE_SKILL` (lines 113-122).

`Chant.invoke()` (lines 191-200) calls `super.invoke` and then `chantAlignmentCheck` (lines 167-189). For a non-auto cast by a player's own ability, that check runs on `rollPercentage()<50` (line 174), which is a 49% roll. A failed roll can produce "Extreme emotions disrupt your chant." (line 178) if the chant is not appropriate to the caster's factions, or "watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (line 184) if metal armour is worn.

Most chants in the bucket then follow the same pattern:

1. Choose a target (`getAnyTarget`, `getTarget`, or the caster), with an optional auto-cast override from `givenTarget`.
2. Check preconditions and fail with a short message, such as "This magic will not work here." or "You must be outdoors for this chant to work."
3. Call `super.invoke`, then `proficiencyCheck(mob, adj, auto)` (`Abilities/StdAbility.java:1441`). The `adj` value is a penalty or bonus.
4. Build a message with `CMClass.getMsg(...)` and ask `okMessage` on the room or mob. If it passes, call `send` and then apply the effect with `beneficialAffect(mob,target,asLevel,tickAdj)` (`StdAbility.java:2341`) or `maliciousAffect(mob,target,asLevel,tickAdj,extraCode)` (`StdAbility.java:2185`).
5. On failure, call one of the fizzle helpers ("nothing happens", "the magic fades").

The fourth argument to `beneficialAffect` / `maliciousAffect` is a tick adjustment added to the standard duration. Values seen here are 0 (no adjustment), 3 (Shapelessness, `Chant_Shapelessness.java:202`), 10 (SummonFog on the area, `Chant_SummonFog.java:139`), 20 (Rockthought, `Chant_Rockthought.java:150`), `Ability.TICKS_FOREVER` (StarGazing, `Chant_StarGazing.java:242`) and `Ability.TICKS_ALMOST_FOREVER` (StrikeBarren, `Chant_StrikeBarren.java:142`). The `-1` passed as the fifth argument of `maliciousAffect` in StoneFriend, RustCurse and Shamblermorph (`Chant_StoneFriend.java:247`, `Chant_RustCurse.java:159`, `Chant_Shamblermorph.java:171`) is an extra check code; its exact meaning was not read.

Mana costs come from `overrideMana()`. Values in this bucket are 5 (SenseAge, SensePregnancy), 100 (SpeedTime), `COST_ALL` (SpeedAging, SpeedBirth) and `COST_ALL - 99` (SummonFog). `COST_ALL` is `Integer.MAX_VALUE` (`Abilities/interfaces/Ability.java:651`). The `COST_ALL - 99` sentinel was not decoded further.

Proficiency is also adjusted by the situation. SummonFog subtracts the area's room count divided by the caster's level plus twice the chant level (`Chant_SummonFog.java:124-128`). SummonCold does the same (`Chant_SummonCold.java:107-111`). Shamblermorph subtracts 10 for each level by which the target outranks the caster (`Chant_Shamblermorph.java:153-156`).

### 2. Timing

Per-mob periodic work uses `tickID==Tickable.TICKID_MOB` (`core/interfaces/Tickable.java:82`, value 0). The default tick is 4000 ms (`core/CMProps.java:681`, settable as `TICKTIME`), so one tick is about 4 seconds by default. StarGazing is the exception: it tests wall-clock time with `System.currentTimeMillis()` and a 60000 ms gap (`Chant_StarGazing.java:141-145`). Most other durations are in ticks.

Dice come from `Libraries/Dice.java`. `roll(n,d,m)` gives `n` rolls of `1..d`, plus `m` (line 398). `rollPercentage()` gives `1..100` (line 409). Rolls in this bucket therefore have the forms `rollPercentage()<35` (34%), `>25` (75%), `<50` (49%) and `<10` (9%).

### 3. Sensing family

The sense chants share one pattern. The effect's `tick` runs `messageTo(mob)` whenever the caster enters a new room (`lastRoom` gating). `Chant_SensePlants.java:168-182` and `Chant_SenseWater.java:253-267` are the examples. Each sense then reports the matching resources in the room and in the exits, in every direction the game defines (`Directions.NUM_DIRECTIONS()`, which is 7 or 11 depending on configuration, `core/Directions.java:256-260`).

- SensePlants matches a room's `myResource()` against materials `VEGETATION` and `WOODEN`, or resources `COTTON` and `HEMP` (`Chant_SensePlants.java:90, 97`). It reports "You sense @x1 to @x2, and @x3." (line 164). The gems, ores and fluids variants change the word (line 80) and, presumably, the sensed set (not read in full).
- SenseWater (`Chant_SenseWater.java:126-196`) gives flavour text from terrain and weather before it scans items. "Your water senses are saturated. This is a very wet place." (line 135) for watery rooms. "It is raining here! Your water senses are twinkling!" (line 142) for rain. "It is snowing here! Your water senses have a dull glow!" (line 148) for snow. Items are checked with `waterCheck` (lines 104-124), which matches drinks whose liquid is `RESOURCE_FRESHWATER` or `RESOURCE_SALTWATER` (lines 110-113). The loop in `messageTo` (lines 202-215) runs one extra slot at index `NUM_DIRECTIONS` that means "here", using a dummy `StdExit`.
- SenseWater also adds a look trailer to examine and look commands (`Chant_SenseWater.java:283-308`). Looking at a container with water shows "contains some sort of water." (line 115).
- SenseMetal sets `CAN_SEE_METAL` (line 111) and adds "You sense metals strongly in the earth here." to look messages on metal or mithril rooms (lines 97-103).
- SenseLife (`Chant_SenseLife.java:95-108`) counts an inhabitant as "life" if it is not a golem and its race `canBreedWith` itself. Messages are "You feel a life force coming from @x1." (line 137).
- SenseSentience (`Chant_SenseSentience.java:82-130`) is a one-shot. It scans a radius of `50 + xlevel + 2*xmaxrange` in passable, same-area rooms (lines 99-103). It lists NPCs with intelligence of at least 2, in outdoor or non-city rooms and in the caster's own room (lines 109-119).
- SenseAge (`Chant_SenseAge.java`) reports age and gains detail by chant level. Level 0 gives "is aged N years" (line 123). Level 1 gives the life stage (line 129). Level 2 gives stat changes from the aging chart (lines 134-153). Level 3 gives adult age (line 156). Level 4 gives toddler age (line 162). Level 5 gives lifespan (line 168). Level 6 gives the next life stage, or a death notice for the oldest stage (lines 178-196).
- SensePregnancy (`Chant_SensePregnancy.java`) works the same way, with tiers at levels 0, 1, 3, 4, 6 and 7 (lines 110, 148, 164, 179, 210, 220). It reports the pregnancy's state and, at higher levels, multiples, expected duration, birth type, the baby's likely race, and the expected arrival date. The name of the other parent is reported at level 6 (lines 210-216) and is not reproduced here.
- SensePoison (`Chant_SensePoison.java:81-101`) collects effects with `ACODE_POISON`, plus, for a MOB, every poison ability it knows (lines 88, 97). The reply is "@x1 contains: ..." (line 124), or "weak impurities" if the list is empty (line 126).

### 4. Summoned creatures and objects

Each summon has the same lifecycle, though the details differ.

1. Precondition. Outdoors, with an open exit to an outdoor room (`Chant_SummonAnimal.java:165-175`). Other summons add their own limits, such as no water (SummonDustdevil, `Chant_SummonDustdevil.java:242-246`).
2. Pick the arrival exit. `outdoorChoices` lists open, two-way exits to non-indoor, non-air rooms (`Chant_SummonAnimal.java:124-142`). The random pick is `roll(1,n,-1)` (line 177), so index 0 to n-1.
3. Create the creature. `determineMonster` builds a `GenMOB` (`Chant_SummonAnimal.java:243-301`). The creature is stamped with `SYSTEM_SUMMONED` and `SUMMONED_BY:<caster>` tattoos (lines 289-290), and given `Prop_ModExperience` with value 0 so it grants no experience (line 288).
4. Bring to life. `bringToLife` (line 195), then `CMLib.tracking().walk(target, opDir, ...)` (line 203). The creature walks back toward the caster's room. If it arrives, it is made to follow the caster with `postFollow` (line 208) and receives the lifetime effect with `beneficialAffect` (line 209). If it does not arrive, it is destroyed (lines 213-218).
5. Tether. Each summon's effect `tick` checks that the creature still follows the caster, is alive and is in the caster's room. If not, the creature is destroyed. See SummonElemental (`Chant_SummonElemental.java:103-111`), SummonDustdevil (`Chant_SummonDustdevil.java:105-108`) and SaplingWorkers (`Chant_SaplingWorkers.java:105-108`).
6. Teardown. `unInvoke` destroys the creature. Elementals announce "<S-NAME> vanish(es)!" (`Chant_SummonElemental.java:128`). The `TYP_QUIT` path in `executeMsg` also ends each summon (`Chant_SummonAnimal.java:113-121`).

Summon-specific details:

- Animal level: `determineMonster` uses `adjustedLevel` minus 3 when the level is above 5, and otherwise sets 1 (`Chant_SummonAnimal.java:246-249`). The animal is then filled out with `CMLib.leveler().fillOutMOB`, and its state is copied into the `GenMOB` (lines 281-287).
- Several animals: `num = roll(2, 2 + xlevel/3, 0)` (`Chant_SummonAnimals.java:132`). The caster can override the count with text of the form "N/Type", parsed with `CMath.parseIntExpression` (lines 133-140). Each animal is a `copyOf` of one template (lines 148, 151).
- Elementals: one at a time (`hasAControlledFollower`, `Chant_SummonElemental.java:165-169`). The level is the larger of `adjustedLevel` and `(caster level - 5) + xlevel/2` (lines 201-204). Hit points are twice the base (line 244), and armour, attack, speed and damage come from `CMLib.leveler()` (lines 245-248).
- Dust devil: level 3, race `AirElemental`, neutral alignment, weight `level*(level + 2*xlevel)`, strength `level + 2*xlevel`, armour 100 and damage 1 (`Chant_SummonDustdevil.java:284-302`). Its tick takes every loose item it can carry (lines 111-126). When it cannot carry something, it gives its items to the caster with "whirl(s) <O-NAME> to <T-NAMESELF>" (line 139). Attacks are blocked with "You can't fight!" (line 166).
- Sapling worker: level is `adjustedLevel - 6`, minimum 1 (`Chant_SaplingWorkers.java:237-239`). It is a `TreeGolem` named "<wood> sapling". It gets one of seven crafting skills (lines 249-280), and Foraging is invoked once on the creature at creation (lines 312-313).
- Flytrap: its tick destroys one dead insect or arachnid `DeadBody` in the room per tick (`Chant_SummonFlyTrap.java:106-119`). Its level uses the same formula as the elemental (lines 213-214).
- Fish food: `Chant_SummonChum.java:96-111` picks a random fish from `RawMaterial.CODES.FISHES()`, creates `adjustedLevel/4 + 1` bits, sets nourishment to `150 + 10*x1`, and sets value to 1.
- Coral and flower: built by `Chant_SummonPlants` (`Chant_SummonPlants.java:117-161`) and `Chant_SummonCoral.buildCoral` (`Chant_SummonCoral.java:102-132`). They are registered with `Druid_MyPlants.addNewPlant`. If the caster owns the room's land, the coral is permanent. Otherwise the effect gets a tick adjustment of `adjustedLevel*240 + 450` on top of its standard duration (`Chant_SummonCoral.java:123-130`). The flower gets a Decay behaviour with min and max set to one mud-month of ticks and chance 100 (`Chant_SummonFlower.java:94-98`); the decay semantics were not read.
- Campfire: a `GenItem` named "a magical campfire", weight 50, with no material (`Chant_SummonFire.java:132-138`). It carries a Burning effect with code `100|512`, which the comment says means it cannot be put out by weather and is destroyed when the burn ends (lines 140-142).

### 5. Weather and area effects

Sky-changing chants change the area's `Climate` object. They do not change one room.

- SummonFog (`Chant_SummonFog.java:105-147`) refuses if the area is already fogged (line 116). It stores the current weather code (line 136), sets next weather to `WEATHER_FOG`, forces a weather tick (lines 137-138), and attaches itself to the `Area` with a 10-tick adjustment (line 139). `unInvoke` restores the old code and forces a tick (lines 90-101).
- SummonCold (`Chant_SummonCold.java:118-163`) moves the weather one step toward cold using this table. Clear goes to winter cold. Cloudy and fog go to snow. Drought goes to winter cold. Dust storm goes to windy. Hail stays hail. Heat wave goes to windy. Rain goes to sleet. Sleet goes to snow. Snow stays snow. Thunderstorm goes to blizzard. Windy goes to winter cold. Winter cold and blizzard stay the same. Other states do nothing.
- The base class `Chant.weatherQue(Room)` (`Chant.java:126-165`) maps weather to bit flags: wind, rain, hot, cold and calm. Blizzard, thunderstorm and heat wave return none. Clear returns four active flags (wind, rain, hot and cold). SummonCold uses the cold flag to refuse casting when cold is already present (`Chant_SummonCold.java:89`).

### 6. Room and item effects

Several chants attach an effect to every object in a room by copying it. `SnatchLight` is the clearest case. Its `tick` makes a non-savable copy of itself on the room, on each glowing or light-source inhabitant, on their carried items and on loose room items (`Chant_SnatchLight.java:113-148`). Each copy's `affectPhyStats` sets `IS_DARK` on its host and clears `IS_LIGHTSOURCE` and `IS_GLOWING` (lines 87-96). A copy removes itself if its host is no longer in the caster's room (lines 98-100 and 157-160).

- SacredEarth is a room effect (`CAN_ROOMS`, `Chant_SacredEarth.java:72-75`). It blocks gathering-skill use with "The sacred earth will not allow you to violate it." (lines 103-108). It also halves damage to targets whose race category is "Vegetation" or "Earth Elemental": `recovery = round(value/2)` and `value - recovery` (lines 110-116). Casting is refused in indoor, watery or air rooms (lines 129-131), and in combat it is indifferent if the victim is a plant or earth creature (lines 137-143) or if the caster is not one (lines 144-146). It then spreads to adjacent rooms (line 187 onward, not read in full).
- Summoned objects carry their own lifetime. The campfire and the coral are examples (see section 4).
- SpeedAging's item rot is a room action. Food rots when the target has no Age effect (`Chant_SpeedAging.java:139-147`). Items made of cloth, flesh, leather, paper, vegetation or wood also rot (lines 152-159). Other items are reported as aging, with no change (line 168).

### 7. Status effects on people

- Rockthought (`Chant_Rockthought.java:92-127`). `executeMsg` stores the first message the target sources, while nothing is stored, if that message has an "others" text (lines 94-101). The `okMessage` branch that would block other messages is empty (lines 108-115, with a comment that the checks were judged too costly). A successful cast clears the stored message, applies a malicious effect with a +20 tick adjustment, and sends "look(s) stubborn." (lines 149-155). It makes the target peaceful and stops the caster and its followers from attacking it (lines 153-163). The `tick` (lines 120-127) re-sends the stored message every tick, so the target keeps repeating that action for the duration. The re-send does not replace the stored message, because `executeMsg` only stores when nothing is stored (line 95).
- RustCurse (`Chant_RustCurse.java:95-137`). Each tick the target gets one rust attempt. It chooses among its own metal and mithril items that are subject to wear, preferring items not worn (lines 101-126). Mithril succeeds only at 9% (lines 130-131). The damage is `postItemDamage(..., 1, TYP_ACID, "<T-NAME> rusts!")` (line 132). The cast says "You feel damp!" on the target (line 160).
- StoneFriend (`Chant_StoneFriend.java:219-264`). Only earth elementals, stone golems and metal golems are valid targets (lines 225-227). The effect's `flags()` returns 0 and the comment says golems resist charm (lines 84-87). Instead the target is made to follow the caster with `postFollow` (line 164 in tick, line 252 in invoke). `okMessage` stops the charmed creature from attacking its charmer, leaving the charmer's room, or dropping the follow (lines 119-146).
- StealFortune (`Chant_StealFortune.java:117-191`) transfers the AutoAwards property. It places a `Chant_StealFortune` effect on the target and a mirrored one on the caster (lines 155-158), then moves the target's AutoAwards holder and suppressor settings to the caster (lines 164-174). The message value check is `msg.value()<=0` (line 152). Its meaning was not verified.
- Shapelessness (`Chant_Shapelessness.java:99-157`) sets weight to 0 and height to -1 (lines 102-103). In `okMessage` it blocks 75% of the time (`rollPercentage()>25`, line 112). Blocked actions are: passing a closed door while carrying items (lines 118-124), handling items (lines 127-144), and attacking, opening, pushing or pulling (lines 145-153). The chant needs a visible moon (`canSeeTheMoon`, line 184).
- Stonewalking (`Chant_Stonewalking.java:93-112`) makes the target invisible, hidden, weightless and height -1 while it stands in a cave, stone, mountain or rock room, or a room whose atmosphere is rock (lines 103-112). It can only be cast in those rooms (lines 174-180).
- Root (`Chant_Root.java:94-130`) blocks `TYP_LEAVE`, `TYP_ADVANCE` and `TYP_RETREAT` with "You can't really go anywhere -- you are rooted!" (lines 100-107), and blocks tools with the moving or transporting flags (lines 115-125). The escape path depends on a field `uprooted` that is never set to true (see Problems).
- Shamblermorph (`Chant_Shamblermorph.java:80-174`) swaps the target's race for `Shambler` (lines 84-92), makes it neutral in gender (line 98), renames it (lines 111-114), and keeps the age category (lines 95-96). The cast is malicious unless the target is in the caster's group (line 157). It needs the caster's and the target's polymorph messages to pass, and both to report a value of 0 or less (lines 163-167).
- StabilizeForm (`Chant_StabilizeForm.java:86-151`) absorbs incoming polymorph attempts. It absorbs if the caster has no learned ability, or if a proficiency roll passes (line 96). It also extends the remaining timers of other polymorph effects by `getTickdownTime` or 50 (lines 141-151).
- SongWard and SpellWard (`Chant_SongWard.java:107-125`, `Chant_SpellWard.java:107-125`) block malicious song or spell casts on the target with `rollPercentage()<35` (line 120). Their `castingQuality` drops to indifferent when the target's victim has no song or spell abilities (lines 98-100).
- StrikeBarren (`Chant_StrikeBarren.java:98-148`) works only on targets whose `reproductiveCode()` is `'F'` (line 135). It then adds a permanent effect (`TICKS_ALMOST_FOREVER`, line 142) that blocks the `Pregnancy` ability's application (lines 104-109).
- StrengthenSeed (`Chant_StrengthenSeed.java:88-145`). When the target gains a `Pregnancy` effect, the chant sets that effect's `BABYRACE` to the caster's race ID (line 100) and then removes itself (line 103). Casting requires that no pregnancy is present unless the cast is automatic (line 130).
- SpeedBirth (`Chant_SpeedBirth.java:76-131`) shortens the remaining time of the `Pregnancy` effect by dividing it by `2.0 - 0.05*x1` (line 123), and moves both start and end times back by that amount (lines 125-126). Under one game day left, the end time is set to about 20 seconds in the past (lines 117-121). Under 20 seconds left, the chant only prints "Birth is imminent!" (line 113).

### 8. Chants that change the caster's state

- StarGazing (`Chant_StarGazing.java:125-206`) is an ongoing effect. Every 60 seconds of real time, with a proficiency roll, it moves the caster's alignment faction by 1% of the faction total, with the sign chosen by whether the caster's purity is nearer the EVIL or GOOD pole (lines 141-155; sign convention not checked). It does the same for the inclination faction (lines 156-164). It picks one of ten contemplation lines, such as "watch(es) a single point of light" (lines 166-197). It ends when the caster is in combat (lines 201-204), when the sky is not visible (lines 136-139), or when the caster moves, uses hands, casts or looks (`executeMsg`, lines 105-121). The `lastTime` array is shared between the invoke instance and the effect (lines 83, 242-244).
- SnatchLight: see section 6.
- SpeedTime (`Chant_SpeedTime.java:90-139`) is the most unusual chant. It records every inhabitant's positive age, sets it negative so it stops aging (lines 114-118), and then calls `CMLib.threads().tickAllTickers(room)` `adjustedLevel/2` times (lines 123-124). Ages are restored in a `finally` block (lines 126-130), and any mana gained during the skip is capped back to the starting value (lines 131-132). It costs 100 mana.
- SpeakWithAnimals: see section 9.
- Shillelagh (`Chant_Shillelagh.java:84-173`) enchants a weapon. Only wood or vegetation weapons qualify (lines 142-146). It adds the `IS_BONUS` disposition to the item and adds `4 + xlevel/2` to its ability value (lines 89-91). A monster cast without a target enchants its wielded weapon, unless that weapon is wood or vegetation (lines 128-134).
- SoaringEagle sets `IS_FLYING` and says "<S-YOUPOSS> eagle wings vanish." when it ends (`Chant_SoaringEagle.java:81, 95`).
- SnuffFlame calls `CMLib.utensils().extinguish(mob,target,false)` (`Chant_SnuffFlame.java:99`).

### 9. Language and animal chat (SpeakWithAnimals)

`Chant_SpeakWithAnimals` implements `Language` and is the only chant here that acts as a translator. The rules are:

- An animal is any MOB for which `CMLib.flags().isAnAnimal` is true (line 116). Other MOBs qualify if their race has an intelligence modifier that leaves a base 10 intelligence at exactly 1 (lines 118-143). The factory test is cached per race ID in a static map (`raceIDs`, lines 44, 121-143).
- Animal languages are the target race's racial abilities whose class is a non-natural `ACODE_LANGUAGE` (lines 182-184). `getAnimalSpeak` auto-invokes one that the animal does not already have (lines 186-190).
- Speech to an animal goes through the animal language (`executeMsg` lines 323-336 and `okMessage` lines 374-387). Speech from an animal is given a trailer with "(translated from ...)" (lines 282-322).
- A MudChat behaviour, created with parameter `=` (`Chant_SpeakWithAnimals.java:215`, `Behaviors/MudChat.java:327-350`), gives each nearby animal its default chat file. It is ticked on the caster's tick (lines 239-247).

The first-call bug in `getAnimalSpeak` is described under Problems.

### 10. Pregnancy and aging edits (neutral description)

Three chants edit the stats of an existing effect: SpeedBirth (section 7), StrengthenSeed and StrikeBarren. SpeedAging is the aging chant. It handles items and creatures in these ways:

- Creatures whose age stat is zero or less gain 10% of their age in minutes (`Chant_SpeedAging.java:182-183`).
- Other creatures with a player birthday record age by 10% of their age in years. The result is rounded. If both the years and the months come out as zero, one month is applied. The birthday is moved back (lines 186-200), and the age stat is increased (line 207).
- Where the target has an `Age` effect (line 139), its text is either a counter (if it is small, below `Short.MAX_VALUE`) or a timestamp. The timestamp is moved earlier by `age/(10 - xlevel/3)`, with at least a month or a year added (lines 213-233).
- The malicious flag is cleared for group members, the target's liege, targets in the child age category and mature targets that have a liege (lines 102-121). It is also cleared for items and for animals in a room where the caster has privileges (lines 123-129).

## Content worth mining

### A. Chant catalogue (49 files)

| Chant (name) | Domain | abstractQuality | One-line mechanic |
|---|---|---|---|
| Rockthought | ENDURING | MALICIOUS | Target repeats one visible action each tick for the effect's duration |
| Shillelagh | PLANTCONTROL | INDIFFERENT | Enchants a wood or vegetation weapon (+4 + xlevel/2 ability on items) |
| Summon Cold | WEATHER_MASTERY | INDIFFERENT | Steps area weather toward cold (table in section 5) |
| Rust Curse | DEEPMAGIC | MALICIOUS | Rusts one metal item per tick, mithril 9% |
| Summon Flower | PLANTGROWTH | INDIFFERENT | Plant item from SummonPlants, with a decay behaviour |
| Sift Wrecks | WATERLORE | INDIFFERENT | Lists underwater items in range, needs own coral |
| Stabilize Form | PRESERVING | BENEFICIAL_SELF | Absorbs polymorph attempts |
| Song Ward | PRESERVING | BENEFICIAL_SELF | 34% chance to block hostile songs |
| Spell Ward | PRESERVING | BENEFICIAL_SELF | 34% chance to block hostile spells |
| Summon Fire | DEEPMAGIC | INDIFFERENT | Creates a fuel-free magical campfire item |
| Strike Barren | BREEDING | MALICIOUS | Permanent block of pregnancy on female targets |
| Summon Fog | WEATHER_MASTERY | INDIFFERENT | Fog on the area (+10 tick adjustment), restored on expiry |
| Strengthen Seed | BREEDING | OK_OTHERS | Sets pregnancy's baby race to the mother's race |
| Sense Metal | ROCKCONTROL | OK_SELF | Sees metal, look messages on metal ground |
| Speed Time | MOONSUMMONING | INDIFFERENT | Runs room tickers, freezes ages, 100 mana |
| Sense Poison | PRESERVING | OK_OTHERS | Lists poison effects on an object |
| Summon Fear | ENDURING | MALICIOUS | Makes nearby targets flee |
| Speed Birth | BREEDING | OK_OTHERS | Shortens remaining pregnancy time |
| Sense Sentience | BREEDING | OK_SELF | Lists intelligent NPCs in a radius |
| Soaring Eagle | SHAPE_SHIFTING | BENEFICIAL_SELF | Flying disposition |
| Summon Chum | WATERLORE | INDIFFERENT | Fish-bit food in watery rooms |
| Snuff Flame | ENDURING | OK_OTHERS | Extinguishes a target |
| Sense Gems | ROCKCONTROL | OK_SELF | Plant sense with "gems" |
| Sense Ores | ROCKCONTROL | OK_SELF | Plant sense with "ores" |
| Sense Fluids | DEEPMAGIC | OK_SELF | Plant sense with "fluids" |
| Speak With Animals | ANIMALAFFINITY | BENEFICIAL_SELF | Animal language and chat |
| Sense Water | WATERLORE | OK_SELF | Water sense with terrain and weather text |
| Summon Dust Devil | WEATHER_MASTERY | BENEFICIAL_SELF | Air elemental that carries loose items |
| Sapling Workers | PLANTCONTROL | BENEFICIAL_SELF | Tree-golem with a random crafting skill |
| Summon Animal | ANIMALAFFINITY | BENEFICIAL_SELF | One animal from a 64-ID table |
| Summon Elemental | DEEPMAGIC | BENEFICIAL_SELF | One of four elementals |
| Stone Friend | ROCKCONTROL | MALICIOUS | Charms golems and earth elementals via follow |
| Speed Aging | BREEDING | MALICIOUS | Rots items, ages creatures |
| Summon FlyTrap | PLANTGROWTH | BENEFICIAL_SELF | Flytrap eats insect and arachnid corpses |
| Star Gazing | ENDURING | OK_SELF | Ongoing faction drift and flavour, outdoors |
| Snatch Light | DEEPMAGIC | OK_SELF | Darkens room, strips light sources |
| Sense Pregnancy | BREEDING | OK_OTHERS | Reports pregnancy by level |
| Sense Age | BREEDING | OK_OTHERS | Reports age by level |
| Sense Plants | PLANTCONTROL | OK_SELF | Plant resources in adjacent rooms |
| Sacred Earth | ENDURING | MALICIOUS | Room: no gathering, halves damage to plant and earth creatures |
| Shapelessness | SHAPE_SHIFTING | BENEFICIAL_SELF | Weightless, 75% action block, needs moon |
| Stonewalking | ROCKCONTROL | BENEFICIAL_SELF | Invisible and weightless in rock |
| Summon Animals | ANIMALAFFINITY | BENEFICIAL_SELF | 2dN animals, or "N/Type" override |
| Steal Fortune | COSMOLOGY | OK_OTHERS | Moves AutoAwards holder to the caster |
| Life Echoes (SenseLife) | BREEDING | OK_SELF | Breeding life in adjacent rooms |
| Shamblermorph | SHAPE_SHIFTING | OK_OTHERS | Race swap to shambler |
| Root | PRESERVING | BENEFICIAL_SELF | Blocks movement |
| Spark Runes | NATURELORE | MALICIOUS | Re-casts chant spells from a runic scroll |
| Summon Coral | PLANTGROWTH | INDIFFERENT | Coral item, 18 possible names |

The abstractQuality column is the value each class returns from `abstractQuality()`, with the `Ability.QUALITY_` prefix removed. The chapter does not trace how the game uses these codes.

### B. Animal list (SummonAnimal, `Chant_SummonAnimal.java:231-241`)

64 IDs in one table. Examples: Wolf, Bear, Eagle, Horse, Tiger, Lion, Python, Cobra, Rattlesnake, Owl, Hawk, Raven, Rabbit, Squirrel, Walrus, Tarantula, Penguin, Gorilla, Goat, Sheep, Pig, Cow, Duck, Parakeet and Worm. Each is passed to `CMClass.getMOB` (the existence of each class was not checked). A race name is also accepted as the summon text (lines 252-264).

### C. Elementals (SummonElemental, `Chant_SummonElemental.java:206-240`)

| Roll | Name | Alignment | Race | Breath ability |
|---|---|---|---|---|
| 1 | fire elemental | Evil | FireElemental | Firebreath |
| 2 | ice elemental | Good | WaterElemental | Frostbreath |
| 3 | earth elemental | Neutral | EarthElemental | Acidbreath |
| 4 | air elemental | Good | AirElemental | Lighteningbreath (the class is spelled this way in `Abilities/Misc/`) |

### D. Sapling worker crafts (SaplingWorkers, `Chant_SaplingWorkers.java:249-280`)

Foraging (invoked once on creation), Farming, Tailoring, LeatherWorking, Butchering, ScrimShaw and Sculpting. Each has a display line such as "is a humble farmer" or "has an eye for foraging". The sapling is named "<material> sapling", for example "oak sapling".

### E. Coral and fish names

- 18 coral names (SummonCoral, `Chant_SummonCoral.java:80-100`): staghorn coral, brain coral, sea fan, antipathes, leptopsammia, cup coral, wire coral, lace coral, blade coral, box fire coral, plate coral, sheet coral and others.
- Fish food is named "some <fish> bits" and described as delicious (`Chant_SummonChum.java:100-102`).

### F. Weather transitions (SummonCold)

See section 5. This table is a reusable state machine for an SUD's weather.

### G. Star gazing lines

Ten short contemplation lines (`Chant_StarGazing.java:166-197`), such as "learn(s) the patterns of the heavens" and "find(s) clarity in the stars". They are a good source of flavour text for a meditation action.

## Notable design ideas

- Chants attach to the objects they affect by making copies of themselves. SnatchLight copies itself onto the room, inhabitants and items, with `setSavable(false)` so the copies are not persisted (`Chant_SnatchLight.java:115-117`). The room's darkness is therefore a fact about the effects present, not stored state.
- Every summon is tethered by a tick check on follow, death and room (section 4). The creature's life is the effect's life, and the same test ends it when the caster goes away.
- Summoned creatures are built as data (`GenMOB`) and stamped with tattoos for identity, and `Prop_ModExperience` at 0 stops them granting experience. This keeps summons out of the progression system.
- SpeedTime treats time as something that can be run forward. It freezes ages by negating them, runs the room's ticks, then restores everything. This is a clear, if blunt, time-skip.
- Rockthought's stubborn effect repeats the victim's first visible action after the cast. It needs no new text, because it replays the victim's own message.
- The sense family shares one report routine, with room-change gating and a fixed direction loop that includes "here". It is a simple and useful pattern for any game that wants a player to perceive their surroundings.
- StealFortune moves a property holder from one MOB to another, using a holder and suppressor pair in the AutoAwards settings (`Chant_StealFortune.java:164-174`). The purpose of the pair is inferred from the code, not stated in it.
- Several chants scale their penalty to the scale of their target: the area size for fog and cold, the level gap for Shamblermorph, and the caster's level for the summon formulas.

## Problems and cruft

1. Wrong first-call result in `Chant_SpeakWithAnimals.getAnimalSpeak` (`Chant_SpeakWithAnimals.java:186-193`). `effectA` is read before `autoInvocation` runs, and the test on line 192 uses that null value. The language is therefore not returned on the first call after the animal gains the effect. The second call finds it. Reassigning `A` on line 190 does not help, because the returned value is `effectA`.
2. Dead escape in `Chant_Root`. The field `uprooted` (`Chant_Root.java:71`) is tested and cleared in `okMessage` (lines 104-109 and 120-125). Nothing in the codebase sets it to true. A grep of the whole `com/` tree finds only this file, plus the words "can not be uprooted" in `Chant_FreeVine.java:105`. The root can therefore never be escaped by that path.
3. Empty branch in `Chant_Rockthought.okMessage` (lines 108-115). The code that would block messages has been removed, and only a comment remains.
4. Copy-pasted comments that do not match the code. `Chant_Stonewalking.java:96-99` and `Chant_Shamblermorph.java:105-108` both say the effect "should consistantly put the mob into a sleeping state". Neither does.
5. `Chant_SenseWater.messageTo` uses a variable named `metalFound` (`Chant_SenseWater.java:218-232`), copied from the metal sense.
6. Unused code. `Chant_SummonDustdevil.java:248` declares `material` and never uses it, and `determineMonster` takes an unused `material` parameter (line 281). `Chant_SpeakWithAnimals.java:43` declares a static `charStats` that is never referenced by name. `canSpeakWithThis(MOB, Ability)` ignores its ability argument (lines 101-106).
7. Success is reported after a veto. In `Chant_SummonElemental.invoke` (lines 175-195) and `Chant_SpeakWithAnimals.invoke` (lines 440-452), the return value stays true even if the room's `okMessage` refuses the cast, so the caller sees a success. Other chants in the bucket follow the same pattern.
8. SpeedAging returns false on a successful cast that did not destroy an item (`Chant_SpeedAging.java:174`). Whether mana or cooldowns are refunded was not verified.
9. Unreleased template mobs. `Chant_SummonAnimal.determineMonster` gives its `newMOB` the caster's location (line 269) but never brings it to life or destroys it (lines 269-301). `Chant_SummonAnimals` does the same with its `templateMob` (`Chant_SummonAnimals.java:148`). The effect on memory was not verified.
10. SpeedTime calls `tickAllTickers` on the whole room, so other creatures' effects and combat run during the time skip. No check excludes other players or interrupts the loop (`Chant_SpeedTime.java:123-124`).
11. StarGazing uses wall-clock time (`System.currentTimeMillis`) for its 60-second gate, while every other chant uses ticks (`Chant_StarGazing.java:141-145`).
12. Magic numbers with no named constant: `Ability.COST_ALL - 99` (`Chant_SummonFog.java:86`) and the `100|512` burn code (`Chant_SummonFire.java:141`).
13. Multiplayer assumptions: summons end when their owner quits (`Chant_SummonAnimal.java:113-121`, `Chant_SummonElemental.java:137-145`), and StealFortune moves player awards (`Chant_StealFortune.java:160-174`). Group and liege checks appear in SpeedAging (lines 85-121) and StrengthenSeed (line 122).
14. Spelling and style: "forune" in `Chant_StealFortune.java:130`, "inhabitated" as the name of a method in `Chant_SenseLife.java:95`, and "Lighteningbreath" as the ID of an existing class (`Abilities/Misc/Lighteningbreath.java`). These are harmless but should be fixed if copied.

## Takeaways for a Python SUD

- Model a chant as data plus one effect. A `Spell` record with name, domain, quality, target kind (self, other, room, item), mana cost, base proficiency, and an effect object with `on_tick`, `before(event)` and `after(event)`. Keep the two-phase veto that CoffeeMud uses (`okMessage` before `executeMsg`). Shapelessness, Root, SongWard and StabilizeForm need it.
- Use game turns, not wall-clock time. Give every effect a duration in turns. Skip StarGazing's real-time gate and count turns instead.
- For summons, use a creature with `owner`, a `lifetime` tied to the effect, and a tether check each turn: owner alive, owner in the same location, owner not gone. Destroy the creature when any check fails. This is the cleanest part of the bucket to borrow.
- Keep the content tables as Python data: the 64 animal IDs (or a smaller set), the four elementals with alignment and breath, the seven sapling crafts, the 18 coral names, the weather transition table from SummonCold, and the star-gazing lines. They can drive random choice directly with `random.choice`.
- Weather as a transition table: `NEXT_WEATHER = {"clear": "winter_cold", "rain": "sleet", ...}`, with an area-level effect that remembers the old state and restores it on expiry (the SummonFog pattern, `Chant_SummonFog.java:90-101`).
- Sensing as a function, not an effect: `sense_water(player, world) -> list[str]` that scans the room and its exits. Drop the lastRoom gating, since a single-player game can call it on movement.
- Room and item effects can be simple attributes: `room.dark = True` with a list of effects that clear it on expiry. The copy-onto-every-object pattern is a MUD artefact. Python gives the same behaviour with a per-room effect list.
- Drop for a single-player SUD: the group and liege checks, faction and alignment nudging (or replace it with one simple attribute), AutoAwards transfer, the MudChat behaviour, the tattoo system (use a `source` attribute instead), and the experience suppression (summons simply do not grant experience).
- Rethink for single-player: the StealFortune mechanic has no second player to steal from, but it could become "take a creature's bonus for a while". Speed Time is a clean power for a turn-based game (advance the world N turns while the caster's region is frozen). The room-wide time skip is worth keeping in a turn-based design.

## Coverage

- Read in full, with only boilerplate accessors and blank lines elided: `Chant.java` (base, 201 lines), `Chant_SensePlants.java`, `Chant_SenseWater.java`, `Chant_SpeakWithAnimals.java`, `Chant_SummonAnimal.java`, `Chant_SummonElemental.java`, `Chant_SummonDustdevil.java`, `Chant_SaplingWorkers.java`, `Chant_StarGazing.java`, `Chant_SnatchLight.java`, `Chant_SpeedAging.java`, `Chant_StealFortune.java`, `Chant_Shapelessness.java`, `Chant_Shamblermorph.java`, `Chant_Root.java`, `Chant_Stonewalking.java`, `Chant_SenseAge.java`, `Chant_SummonFog.java`, `Chant_SummonCold.java`, `Chant_SpeedTime.java`, `Chant_SenseMetal.java`, `Chant_SenseSentience.java`, and the main logic of `Chant_SenseLife.java`.
- Read in part (the logic paths the chapter relies on): `Chant_SacredEarth.java` (to line 189), `Chant_StoneFriend.java` (tick, okMessage, unInvoke, invoke), `Chant_Rockthought.java`, `Chant_Shillelagh.java`, `Chant_RustCurse.java`, `Chant_SiftWrecks.java`, `Chant_StabilizeForm.java`, `Chant_SongWard.java`, `Chant_SpellWard.java`, `Chant_StrikeBarren.java`, `Chant_StrengthenSeed.java`, `Chant_SensePoison.java`, `Chant_SpeedBirth.java`, `Chant_SoaringEagle.java`, `Chant_SummonChum.java`, `Chant_SnuffFlame.java`, `Chant_SummonFear.java`, `Chant_SummonFire.java` (to line 156), `Chant_SummonCoral.java` (to line 143), `Chant_SummonFlyTrap.java` (tick, unInvoke, castingQuality, determineMonster header), `Chant_SummonAnimals.java` (invoke), `Chant_SensePregnancy.java` (messages and tier conditions), and `Chant_SparkRunes.java` (invoke path).
- Skimmed by grep only: `Chant_SummonFlower.java`, `Chant_SenseGems.java`, `Chant_SenseOres.java` and `Chant_SenseFluids.java` (declarations and `word()`).
- Out of bucket and not read in full: `Chant_SummonPlants.java` (key lines only), `Druid_MyPlants.java` (referenced only), `Abilities/StdAbility.java` (signatures only), `Behaviors/MudChat.java` (`setParms` only), and `core/Directions.java` (count only).
- Line counts for the bucket total 9520, matching the assignment.
