# Druid chants, part 7 (Uplift to Water Cover): shape-shifting, summons, weather and water

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b057` (Abilities/Druid [part 7/7: Chant_Uplift .. Druid_WaterCover]) · 46 files · 9481 lines of Java.

Citation convention: `Abilities/Druid/X.java:N` is shorthand for `com/planet_ink/coffee_mud/Abilities/Druid/X.java`, line N, under the CoffeeMUD folder. Where a line is not given, the method name is cited. The shape-shift data file is `resources/skills/shapeshift.txt`. "Condensed read" means the file was read in full with blank lines and brace-only lines removed, so exact block nesting was checked only where noted.

## Summary

This slice is the Druid's shape-shifting family (`Druid_ShapeShift` with ten "Totem" slots, plus Krakenform, GolemForm and PlantForm), the summoning and plant-ownership skills (PackCall, MyPlants, KnowPlants), and Uplift, the largest single mechanic here. Uplift morphs an animal companion over two mud hours into a new race that is generated at runtime and written to the database. The rest are about two dozen weather, sea, ward, stealth and combat chants and skills. The shape-shift design is the most reusable part: twelve forms are defined in one text file, each totem slot permanently binds one form, and the form's numbers scale with class level through a five-step tier table. Several room-scale effects (Whirlpool, WhiteMoon, WakingMoon, VolcanicChasm) show how a room can carry its own timed effect, and the Whirlpool builds a temporary grid sub-area. Several bugs are visible on reading, including a lookup that ignores its filter argument and a `return` inside a loop in WarpWood.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Abilities/Druid/Druid_ShapeShift.java` | Base shape-shift ability: form table, totem binding, tier rules, stat and race effects | 760 |
| `Abilities/Druid/Druid_ShapeShift2.java` … `Druid_ShapeShift11.java` (10 files) | Totem slots: subclasses with only an ID, a name and an empty trigger list | 63 each |
| `Abilities/Druid/Druid_Krakenform.java` | Kraken form: builds a sailing "ship" with a tentacle weapon and hit-point rooms | 409 |
| `Abilities/Druid/Druid_GolemForm.java` | Golem form: five metal and stone golem tiers | 382 |
| `Abilities/Druid/Druid_PlantForm.java` | Plant form: flower, vine, tumbleweed and shambler; NPCs refuse to attack it | 360 |
| `Abilities/Druid/Druid_MyPlants.java` | Process-wide index of the druid's summoned plants (ownership by name) | 340 |
| `Abilities/Druid/Chant_Uplift.java` | Uplift: morphs an animal companion into a runtime-generated "Neo" or "-Man" race | 420 |
| `Abilities/Druid/Chant_WindColor.java` | Wind Color: describes a room's alignment, presence and hidden items as colour words | 469 |
| `Abilities/Druid/Chant_WhisperWard.java` | Whisper Ward: one-shot trap on an item or room, triggered by a named action | 338 |
| `Abilities/Druid/Chant_Whirlpool.java` | Whirlpool: room effect that builds a grid sub-area and sweeps enemies into it | 333 |
| `Abilities/Druid/Druid_PackCall.java` | Pack Call: summons temporary pack followers within a level budget | 301 |
| `Abilities/Druid/Chant_Waterspout.java` | Waterspout: instant tornado that tosses items and people around the room | 263 |
| `Abilities/Druid/Druid_WaterCover.java` | Water Cover: hide in a watery room until a noisy or moving action breaks it | 235 |
| `Abilities/Druid/Druid_DruidicPass.java` | Druidic Pass: sneak and invisible movement through wild outdoor rooms | 222 |
| `Abilities/Druid/Chant_WhiteMoon.java` | White Moon: room effect that alters damage by alignment | 219 |
| `Abilities/Druid/Druid_AquaticPass.java` | Aquatic Pass: hidden, invisible movement from water rooms | 216 |
| `Abilities/Druid/Chant_WindGust.java` | Wind Gust: pushes back and sometimes knocks down hostile MOBs in the room | 208 |
| `Abilities/Druid/Chant_WarningWinds.java` | Warning Winds: reports fights and enemies within two rooms | 203 |
| `Abilities/Druid/Chant_VolcanicChasm.java` | Volcanic Chasm: cave-only room effect that burns MOBs and items | 199 |
| `Abilities/Druid/Druid_KnowPlants.java` | Know Plants: explains the material of an unworn vegetable or wooden item | 195 |
| `Abilities/Druid/Chant_WindSnatcher.java` | Wind Snatcher: vetoes five named wind spells aimed at the protected MOB | 192 |
| `Abilities/Druid/Chant_WaterWalking.java` | Water Walking: flying flag on water surface rooms, blocks ascending from them | 191 |
| `Abilities/Druid/Chant_WakingMoon.java` | Waking Moon: room effect that lasts only in dawn and day | 190 |
| `Abilities/Druid/Chant_WaterHammer.java` | Water Hammer: ranged water blow that needs a nearby water source | 188 |
| `Abilities/Druid/Chant_Worms.java` | Worms: disease with periodic damage, stat loss and hunger drain | 188 |
| `Abilities/Druid/Chant_WarpWood.java` | Warp Wood: wears down a wooden item until it is destroyed | 177 |
| `Abilities/Druid/Druid_Rend.java` | Rend: leg attack in animal form on a target that is not standing | 172 |
| `Abilities/Druid/Chant_VampireVine.java` | Vampire Vine: summons one life-stealing vine (extends `Chant_SummonVine`) | 161 |
| `Abilities/Druid/Chant_Yearning.java` | Yearning: lowers Wisdom until a mating social occurs in the same room | 161 |
| `Abilities/Druid/Chant_Waterguard.java` | Waterguard: shield that absorbs part of incoming water attacks | 160 |
| `Abilities/Druid/Chant_VineMass.java` | Vine Mass: summons several vines (extends `Chant_SummonVine`) | 159 |
| `Abilities/Druid/Chant_VineWeave.java` | Vine Weave: crafts a woven item from the room's plant resources | 152 |
| `Abilities/Druid/Druid_Bite.java` | Bite: mouth attack in animal form | 152 |
| `Abilities/Druid/Chant_WindShape.java` | Wind Shape: reduces damage from weapons by a quarter | 151 |
| `Abilities/Druid/Druid_RecoverVoice.java` | Recover Voice: removes effects that block speech | 142 |
| `Abilities/Druid/Chant_VenomWard.java` | Venom Ward: adds 100 to the poison save | 123 |
| `Abilities/Druid/Druid_SeaLore.java` | Sea Lore: auto-invoked combat bonus in water rooms | 120 |

Shared base classes are outside this bucket: `StdAbility` (timing and costs), `Chant` and `Chant_SummonVine` (other buckets). The class-to-ability grants are in `CharClasses/` (see Content).

## How it works

### 1. Shape-shifting: `Druid_ShapeShift` and its totem slots

**Data model.** A form is a `ShiftShapeForm` (`Druid_ShapeShift.java:98-107`): a display name (`form`), four multipliers (`attackAdj`, `dmgAdj`, `armorAdj`, `speedAdj`), and two arrays of five entries each, one name per tier (`shapes`) and one race class ID per tier (`raceIDs`). Forms are read from `resources/skills/shapeshift.txt` by `getShapeData()` (`Druid_ShapeShift.java:218-249`). The loader merges every extension copy of the file (`CMFile.getExistingExtendedFiles`, line 227), so a mod or area can add forms by adding lines to the same list. Entries are parsed line by line: a line starting with `[` opens a form, and `KEY=value` lines fill it (`fillShapeField`, lines 174-215). An unknown key is logged and skipped (line 185).

Per-instance state is three fields: `myRaceCode` (which form this slot is bound to, line 90), `myRaceLevel` (the tier chosen by name, or -1 for "best allowed", line 91) and `newRace`/`raceName` (the current race object and name, lines 92-93). The binding is saved with the ability: `invoke()` calls `setMiscText(buildUniqueShapeStrings()+myRaceCode)` (line 645), so it survives a save.

**Slot model.** The base `Druid_ShapeShift` has the trigger word `SHAPESHIFT` (line 64). The ten subclasses (`Druid_ShapeShift2` to `11`) differ only in ID and display name ("Second Totem" through "Eleventh Totem") and have an empty trigger list. Each is a separate learnable ability, so a druid can hold up to eleven slots and twelve forms are defined. A pattern for a slot is shown by `Druid_ShapeShift2.java:36-63`, which is the whole class apart from the header.

**Binding and exclusivity.** `invoke()` gathers every totem slot the mob owns (`allShapeshifts`, lines 513-522) and counts the forms they hold (`racesTaken`, lines 507-540). The first time a slot is used (`myRaceCode<0`, line 541), the player picks a form that no other slot holds:

- A player is asked "You have not yet chosen your form, would you like to now (Y/n)?" (line 557), then gets a numbered menu headed "Choose from the following:" that lists only untaken forms (lines 561-572). The choice can be a number or the start of a form name (lines 580-606). Blank input aborts (lines 575-579).
- A player who is in combat cannot choose (lines 551-552).
- A monster, or a cast on someone else, gets a random untaken form (lines 543-549).

Once set, `myRaceCode` is never changed, so each slot is bound to one form for the life of the character. This is the design's central idea: the twelve forms are a shared pool, and each totem slot is a permanent claim on one of them.

**Toggling.** Invoking the ability while any shape-shift effect is active reverts it (lines 491-503). `Druid_Krakenform` is included in that check.

**Naming a form.** The trigger may carry a tier or form name (`parm`, built from `commands`, line 619). For the bound form the code walks tiers from highest to lowest and matches the words (lines 622-643). A match sets `myRaceLevel`. If the words name a form held by another slot, that slot is invoked instead (lines 648-711). A number selects a slot by index (lines 712-719). `LIST` prints the valid forms (lines 721-722). Anything else prints "'@x1' is an illegal form!" (line 724).

**Tier rules.** A druid's tier is set by class level:

- `getMaxRaceLevel(classLevel)` returns 0 below class level 6, 1 for 6-11, 2 for 12-17, 3 for 18-23, and 4 from 24 (`Druid_ShapeShift.java:383-398`).
- `getClassLevel(mob)` is `qualifyingClassLevel + 2*XLEVEL - qualifyingLevel`, and 30 when the mob does not qualify (lines 367-374). In the ability mapper, the first term is the mob's class level and the second is the level at which the ability is granted (`Libraries/interfaces/AbilityMapper.java:447-467`). So the result is the number of levels gained since the ability was granted, plus two per XLEVEL point.
- `getRaceLevel(classLevel)` returns the chosen tier, capped at the maximum for the class level. With no tier chosen, it returns the maximum (lines 422-428).
- `getMaxCharLevel(tier)` returns 5, 11, 17, 24 or 31 for tiers 0-4 (lines 400-415). The effective level used for stats is capped at this value (lines 318-319).

In short, invoking without a tier word gives the highest tier the druid qualifies for. Each tier name also has a race behind it (`raceIDs`), which `setRaceName` loads (lines 376-381).

**Stat effects.** `affectPhyStats` (lines 308-330) applies these when the form is active:

- `adjustedLevel = min(tierCap, level) + XLEVEL` (lines 318-319).
- Attack gets `round(adjustedLevel * attackAdj / 2)`, armor is reduced by `round(adjustedLevel * armorAdj / 2)`, damage gets `round(adjustedLevel * dmgAdj / 2)` (lines 324-326).
- Speed gets `speedAdj * CMProps.getSpeedAdjustment() * (1 + XLEVEL/3)` (line 327).
- Weight is recomputed from the race's height and weight (lines 316-322).

Worked example: the Rodent form (`attackAdj` 0.7, `dmgAdj` 0.4, `armorAdj` 1.0, `speedAdj` 0.2) at effective level 20 with no XLEVEL gives attack +7, damage +4, armor -10, and speed +0.2 times the speed adjustment.

`affectCharStats` (lines 333-349) swaps the mob's race object to `newRace` and recomputes the age category. The race's own abilities and senses then apply, which is how a wolf form gets wolf senses.

**Duration and messages.** The effect is applied with `beneficialAffect(..., Ability.TICKS_FOREVER)` (line 749), so it lasts until it is removed: invoking the ability again toggles it off, and `unInvoke` runs when the effect is removed. Messages: "<S-NAME> take(s) on @x1 form." (line 748) and "<S-NAME> revert(s) to @x1 form." (line 361). If the form's faction check fails for a player, there is a 50% chance of "Extreme emotions disrupt your change." (lines 733-738).

**Monster use.** `castingQuality` (lines 464-483) rates the form as beneficial to self when the target is in combat, is not already shape-shifted, and the druid is at tier 3 or higher. That makes monsters prefer to shift in combat at high tiers.

### 2. Krakenform (Mer, class level 35)

Krakenform does not change stats the way the forms do. It builds a vehicle. `getShip()` (`Druid_Krakenform.java:111-175`) creates a `GenSailingShip` with ability `ceil(adjustedLevel/15)` (line 121) and 100 uses (line 125). It then creates an area for the ship, a deck room, and a tentacle weapon (`GenSiegeWeapon`):

- Ammunition capacity 1 (line 149). Range is 0 to `3 + XLEVEL/2` (line 152). Damage is 20 (line 153). A second copy is made (line 157).
- The number of hit-point rooms is `ceil(level/9) - 1` (line 159). The code comments that this is "dumb, but it's the only way to give hit points" (line 160). Hit points are represented by padding rooms.
- The ship's remaining uses are set to the druid's hit-point percentage times 100 (lines 169-171).

`tick()` (lines 324-353) refills every tentacle to capacity each tick while the ship is in combat (line 346). In effect, the tentacle fires once per tick.

`okMessage()` (lines 178-243) redirects the druid's speech into a "Krakenspeak" language (lines 194-212). A compass word typed as a command becomes "SAIL <dir>" on the ship (lines 213-232). The word "lower" (the anchor) is refused (lines 235-236).

Sinking is handled in `executeMsg` (lines 246-260): if the ship receives a `CAUSESINK` message, the effect is removed and the druid dies (`postDeath`). On removal (lines 263-291), the druid's hit points are set to the saved percentage of maximum if the ship was damaged, and the druid is moved off the ship.

The effect lasts `TICKS_FOREVER` (line 396).

### 3. GolemForm (Delver, class level 10)

Golem Form has five hard-coded tiers (`Druid_GolemForm.java:105-118`): Steel, Quartz, Mithril, Diamond and Adamantite Golem, with race `MetalGolem` or `StoneGolem` alternating. Tier thresholds are class levels 5, 10, 15 and 25 (`getRaceLevel`, lines 238-253).

Combat numbers grow with tier. Let `xl` be XLEVEL and `bonus = 0.1*xl` (line 133):

| Tier | Armor | Attack | Damage | Speed | Movement |
|---|---|---|---|---|---|
| 0 | -(10+xl) | +(10+xl) | +(5+xl/2) | /(1.5-bonus) | /1 |
| 1 | -(20+2xl) | +(20+2xl) | +(10+xl) | /(2.0-bonus) | /2 |
| 2 | -(40+4xl) | +(40+4xl) | +(20+2xl) | /(2.5-bonus) | /4 |
| 3 | -(60+6xl) | +(80+8xl) | +(40+4xl) | /(3.0-bonus) | /8 |
| 4 | -(80+8xl) | +(160+16xl) | +(80+8xl) | /(4.0-bonus) | /16 |

(`affectPhyStats` lines 134-166; `affectCharState` lines 190-206.) Speed is divided, so higher tiers are slower. Movement points are divided by the same power of two. The form also sets the "cannot speak" sense bit (line 126).

The form is chosen by name. With no argument, the code picks the highest tier allowed (lines 316-318). Typing `list` prints the tiers (lines 337-341). An unrecognised name silently falls back to the highest tier (the loop at lines 323-336 never matches, so `classLevel` is unchanged).

### 4. PlantForm (Gaian, class level 10)

Plant form has four tiers: Flower, Vine, Tumbleweed and Shambler, at class levels 5, 10 and 15 (`Druid_PlantForm.java:103-114`, `getRaceLevel` lines 197-209). It has no stat multipliers, only a race swap and a name change (lines 142-172). Its identity comes from the race objects.

It can only be used outdoors and outside cities (lines 283-292). Its main mechanic is in `okMessage` (lines 117-139): a hostile NPC that is not already fighting the druid, and is in the same room, has its attack vetoed and is told "Attack a plant?!" (line 129). If the plant was already fighting that NPC, the plant is made peaceful and its victim is cleared (lines 130-134). Only the Shambler tier is rated as a beneficial self-cast in combat (lines 243-244).

### 5. Uplift (Beastmaster, class level 35)

Uplift is a chant on an animal companion. It is the only mechanic in this slice that creates races at runtime.

**Preconditions** (`Chant_Uplift.java:317-376`):

- The target must be an animal following the caster (lines 331-332). Golems, plants and undead are refused (lines 337-343).
- The target's race must not already be a generated one (IDs containing `Neo` or `_Man`, lines 354-355 and 363-364). It must be able to breed with `Human` (lines 356, 365) and must not be in the elemental category (lines 357, 366). This prevents double uplift and limits the change to species that could be human-like.
- The caster must be in a room with a `DruidicMonument` item (lines 372-376; the check is at line 76).
- The chant costs all mana (`overrideMana` returns `COST_ALL`, lines 124-127).

**Timeline** (`tick` lines 97-121, `affectPhyStats` lines 84-94, `unInvoke` lines 130-314):

- The effect lasts two mud hours of ticks: `beneficialAffect(..., CMProps.getTicksPerMudHour()*2)` (line 392). Because the duration argument is positive, `StdAbility` uses it as an absolute tick count (`Abilities/StdAbility.java:2314`).
- On the first mob tick, `neoTickDown` is set to `tickDown - tickDown/4` (lines 105-106), so the first quarter of the duration is the "morphing" stage and the rest is "neomorphing" (ambiance text, lines 89-92).
- The effect ends early and fails if the target stops following the caster or leaves the grove (lines 114-116). It also ends if the target dies or leaves the game, which sets `tickDown=99999` (line 111).
- On removal, the outcome depends on the time left. If `tickDown < 2`, the race becomes "-Man" (lines 141-151). If `tickDown < neoTickDown` (at least 25% of the duration has passed), the race becomes "Neo" (lines 155-160). Otherwise the morph "ended unsuccessfully" (line 310).

**Race generation** (lines 162-297). The new race is a copy or generic version of the old one, with:

- Forced mouth and eye body parts (lines 180-183). For a full "-Man", legs and arms are raised to at least two and hands and feet are copied from them (lines 186-199).
- Worn-slot restrictions freed for the listed slots (lines 201-220).
- Stat transfer. For each non-zero base stat, `value - 10` is halved, and half of that is added to both the base stat and its maximum adjustment (lines 233-244). The old stats are cleared in the saved race. A "Neo" then gains +6 Intelligence (lines 245-249). A full "-Man" gains +2 Intelligence and +1 to maximum Strength, Dexterity and Constitution (lines 250-257).
- Racial abilities copied from the old race, with Language abilities replaced by "Common" (lines 261-293; the replacement is at lines 284-285).
- Registration with `CMClass.addRace` and `CMLib.database().DBCreateRace` (lines 295-296). An existing race with the same ID is reused, unless it has expired in the database, in which case it is deleted and rebuilt (lines 162-171).

When the morph completes, the mob's race is set (line 298). Its name, display text and description have the old race name replaced by the new one (lines 302-307).

The IDs are `"Neo" + oldRaceID` and `oldRaceID + "Man"` (lines 160 and 150-151). The display names are the old name with `-Man` appended, or `Neo` inserted after the first space (lines 150-159).

**Caster cost.** The caster sits and is put into `Chant_Hibernation` with `DEEP=true` and `FULLMANAREVOKE=true` (lines 396-405). Its sleep timer is capped at half the uplift's remaining time (lines 406-407). So the druid must stay asleep for part of the morph.

### 6. Summoned followers: Pack Call (Beastmaster, class level 30)

Pack Call (`Druid_PackCall.java`) is a combat-only summon. Its requirements are checked at lines 173-220: wilderness only, not a city room, the caster must be in combat, must be shape-shifted (it finds the last `Druid_ShapeShift` effect, lines 188-199), must have a free follower slot, and needs an open exit other than up (lines 208-220).

The summon has a level budget. `levelsRemaining = 90 + 10*XLEVEL` (line 233). Each pack member's level is the first class level at which the druid's current shape name appears, minus one (lines 239-244). Because the tier thresholds are at class levels 6, 12, 18 and 24, this gives pack levels of 0, 5, 11, 17 and 23 for tiers 0-4. Each member's level is subtracted from the budget, and the loop stops when the budget is spent or the follower cap is reached (lines 234-248). At tier 4 with no XLEVEL, that is three members (90 - 23 - 23 - 23 leaves 21, and the fourth would go negative).

Each member gets stats from the leveller (lines 263-266), a `Fighter_Rescue` ability (lines 256-258), a `Prop_ModExperience` effect that gives no experience (line 267), and the tattoos `SYSTEM_SUMMONED` and `SUMMONED_BY:<name>` (lines 268-269). It arrives from a random open exit (lines 280-283) and follows the caster (line 285).

Lifetime is handled by a `beneficialAffect` on each member (line 291). The member's `tick` (lines 97-115) dismisses it when it stops following, dies, leaves combat, or is in a different room from the caster. `unInvoke` (lines 117-132) shows "<S-NAME> wander(s) off." and destroys the member.

### 7. Plant ownership: MyPlants and KnowPlants

**Ownership.** A plant belongs to a druid when its secret identity equals the druid's name, it is in a room, and it carries a `Chant_SummonPlants` effect whose invoker is the druid or whose text is the druid's name (`isMyPlant`, `Druid_MyPlants.java:99-116`). The plant list for a druid is built on first use by scanning every room in the game (`getMyPlants`, lines 217-240) and recursing into the areas of boardable items (`tryRoom`, lines 178-215). The result is stored in a static map keyed by the druid's name (`allPlants`, line 84). Plants are added and removed through `addNewPlant` and `removeLostPlant` (lines 86-97).

**Listing.** `invoke()` (lines 290-338) prints a three-column table: number, plant name and room (column widths from the session, lines 307-312). Failure reads "Your plant senses fail you." (line 298). When there are none it reads "You don't sense that there are ANY plants which are attuned to you." (line 332).

**Area filter.** `getMyPlants(mob, rooms)` (lines 242-265) is meant to return only plants in the given rooms. It builds that list in `V` but returns `myPlants`, the unfiltered list (line 264). The only caller that passes rooms is `Abilities/Druid/Chant_SiftWrecks.java:91`, which expects an area-only result.

**KnowPlants.** `Druid_KnowPlants` (`canTargetCode` = `CAN_ITEMS`) accepts only unworn items whose material is vegetation or wood. It then writes a few sentences from the material tables: what kind of resource the material is, its sub-type, its category (`RawMaterial.CODES`), its hardness and buoyancy (line 177), and the locales where the material is a resource (scanned from `CMClass.locales()`). It also says whether the item is edible, using the `Poison_Rotten` and `Prayer_Rot` effects (lines 181 and following). If the item was summoned by a druid, it names the summoner's secret identity.

### 8. Weather and sky

**Wind Color** (`Chant_WindColor.java`, lines 93-467) is the most unusual perception mechanic here. Each time a mob enters a new outdoor, non-underwater room (lines 97-107), the code computes a colour string for the room and tells the mob "The winds are @x1." (line 107). The string encodes what is in the room, not what is visible:

- Eleven colour slots (`getColor`, lines 112-140): black (undead), red (evil items or creatures), blue (good), green (neutral creatures), grey (metal or rock items, stone or metal golems), maroon (hidden), yellow (invisible), brown (wood, wooden golems), orange (trapped items), white (lawful), purple (chaotic).
- A "source" is either a single item with at least two properties, or a creature with at least one property (lines 234-240 and 304-309). The first source is "stripes", the second is "swirl".
- A level word comes from the largest level difference between the caster and any item or creature present (lines 160-185). The levels are dull (the default, when everything present is at least 15 levels below), faded (roughly -14 to -5), plain (roughly -4 to +4), bright (+5), brilliant (+15) and dazzling (+25), and the mapping is in the switch at lines 338-407.
- Caster's group members and the caster are excluded (lines 151, 246). If nothing is present, no message is shown (lines 146-147, 325-335).

The output is a phrase such as "a swirl of brilliant red and white" or "faded stripes of ...", built at lines 415-427. The same helper is used by `Chant_BreathColor` and `Chant_DistantWindColor`, which are in other buckets.

**Warning Winds** (`Chant_WarningWinds.java`) uses the same room-change trigger. It scans rooms within two steps of the new room that are open (`getRadiantRooms`, line 109). It reports fighting (any MOB in combat) or enemies (a behaviour that `grantsAggressivenessTo` the caster, line 132). The direction is given with "far" when the room is not adjacent. The texts are "The winds tell of fighting @x1@x2." (line 152) and "The winds tell of enemies ...".

**White Moon** and **Waking Moon** are room effects. White Moon (`Chant_WhiteMoon.java`) needs a visible moon, allows one moon-domain effect per room, and alters incoming damage on each damage message to a MOB: lawful creatures take about half and chaotic creatures take about 1.5 times, as the `recovery` term at lines 129-135 shows. The moon sets with "The white moon sets." Waking Moon (`Chant_WakingMoon.java`) can only be started at dawn or day, is removed by its tick when the area's time of day is neither, and says "The Waking Moon Rises!" (line 180).

**Wind Gust** (`Chant_WindGust.java`) pushes back every hostile MOB in the room that is not already affected. The range increases by `howLong/2` (line 181), where `howLong` is 2, or 4 in windy, dust storm or thunderstorm weather (line 173). Range is capped at the room's maximum. A target may fall: the roll is `rollPercentage() > 2*DEX + level - 5*howLong`, and the target must have a leg (lines 185-187). A fall makes the target sit for `howLong` ticks (`maliciousAffect`, line 191). While the knockdown lasts, the target's attempts to stand are vetoed (lines 111-115). The chant's own success flag is overwritten by the knockdown result (line 191; see Problems).

**Wind Snatcher** (`Chant_WindSnatcher.java`) vetoes five named spells aimed at the protected MOB: `Chant_WindGust`, `Chant_SummonWind`, `Chant_CalmWind`, `Prayer_HolyWind` and `Spell_GustOfWind` (`windSpells`, line 83). Each veto shows "A form around <S-NAME> snatches @x1." (line 153). It can only be cast on a MOB whose enemy knows one of those spells (`castingQuality`).

**Wind Shape** (`Chant_WindShape.java`) reduces damage from item attacks by a quarter: `value - round(value/4)` (lines 110-112).

### 9. Water and sea

**Whirlpool** (`Chant_Whirlpool.java`) is the most structurally interesting effect here. It is cast on a watery surface room whose room below is underwater (`invoke`, lines 271-283). It then builds a `GridLocale` named "Whirlpool" as a sub-area of a temporary `StdArea` (lines 306-313). The grid copies the surface room's exits, except downward (lines 316-321), and is built with `buildGrid()` (line 322). The effect lasts the standard duration (line 303).

On each tick (lines 182-229):

- Every item in the room that `canEverEnterThePool` is announced as "swept into a massive whirlpool!" and moved to the grid's far corner, `(xGridSize-1, 0)` (lines 189-211). If the item is a boardable vehicle, the message goes to every indoor-less room in its area (lines 194-206).
- Every MOB in the room that can enter is moved to the same corner (lines 214-225). Players see "You're swept into a terrible whirlpool!".

`canEverEnterThePool(MOB)` (lines 139-166) excludes the caster, the caster's group, and any MOB the caster cannot fight (lines 141-145). Entering from an adjacent room through an exit (`okMessage`, lines 232-256) is redirected to the same corner (line 251). A flying source is not affected (line 239).

Looking at the room adds "A MASSIVE WHIRLPOOL IS HERE!!!" as a trailer (`executeMsg`, line 264). Casting costs 99% of maximum mana (`COST_ALL - 99`, line 84; the percentage is computed in `Abilities/StdAbility.java:1614-1618`).

**Waterspout** (`Chant_Waterspout.java`) is instant. It is taught to Mer at level 24 (`CharClasses/Mer.java:244`). `invoke()` collects gettable items that are not inside a container (lines 150-155) and hostile targets (`properTargets`, line 156). It adds the open-exit neighbours as possible destinations (lines 162-170). Then it sends items and MOBs to random destinations (lines 174-254):

- An item takes damage depending on its material (lines 189-224): glass 75, cloth, flesh, leather, paper, vegetation, wood or synthetic 50, metal 20, rock, precious or mithril 1, liquid, gas, energy or unknown 0.
- A MOB takes `roll(maxDie, 7, 1)` damage, where `maxDie` is half the adjusted level (lines 246-247). The damage is halved if either message value is positive (lines 248-249).

The class also declares an `IS_FLYING` override (lines 89-94) and an "already here" check (lines 128-132) that depend on a `Chant_Waterspout` effect on the room. `invoke()` never attaches one, and no other file in the repository refers to the class except its grant. Those parts are dormant as written.

**Water Walking** (`Chant_WaterWalking.java`) sets `IS_FLYING` while the mob is on a water surface room (`affectPhyStats`). `okMessage` vetoes ascending from the surface (line 105) and sets `triggerNow` so the flying flag applies on the move onto the surface (lines 115-123).

**Water Hammer** (`Chant_WaterHammer.java`) needs a water source (`getWaterSource`, lines 81-107): a watery room, a container-free drink of fresh or salt water with more than 500 units (line 97), or the owner room of a boardable area that is watery. Its range is 10 (`maxRange`, line 64). Damage is `roll(maxDie, 8, 5 + maxDie/6)` where `maxDie = (adjustedLevel + 3*X1) / 2` (lines 174-175), halved on a saved hit.

**Waterguard** (`Chant_Waterguard.java`) gives the protected MOB a pool of `adjustedLevel + 2*XLEVEL` (line 152). While the pool is positive, a water attack on the MOB is shown as "resist(s) the wet attack" (line 113). Damage is halved and half of it is taken from the pool (lines 115-117). A non-damage water effect is reduced to 1. Water effects on items the MOB owns are vetoed (lines 122-126).

**Sea Lore** (`Druid_SeaLore.java`) is auto-invoked and cannot be removed (`isAutoInvoked` true, `canBeUninvoked` false). In a watery room it adds damage +5+XLEVEL, attack +5+2*XLEVEL and armor -(10+2*XLEVEL) (`affectPhyStats`, lines around 100-104). A damage message in such a room has a one-in-ten chance (`roll(1,10,0)`, line 115) to give the druid proficiency practice (line 117).

**Aquatic Pass** and **Druidic Pass** (`Druid_AquaticPass.java`, `Druid_DruidicPass.java`) are near-identical movement skills. Aquatic Pass works from water rooms; Druidic Pass works from wild outdoor rooms that are not water and not city rooms (`canPassHere`, `Druidic Pass` line 104 for the water-domain test). Each gives sneaking and invisibility while moving. Aquatic Pass also adds hidden. For a closed exit, the code forces the door open and its reverse open for the move, then restores the saved state (lines 179-197 for Aquatic Pass). A failed roll on an open exit still moves the character (line 152).

### 10. Wards, stealth and trap-style effects

**Whisper Ward** (`Chant_WhisperWard.java`) is a one-shot trap that can sit on an item or on a room. The trigger word is the last word of the command, and one of HOLD, WIELD, WEAR, TOUCH or ENTER is recognised by its prefix (lines 277-297). The ward is placed with `beneficialAffect` at the standard duration (lines 327-328). The trigger rules are in `executeMsg` (lines 217-266):

- A message aimed at the ward's room of the trigger type does not fire the ward at once. It arms it (`waitingForLook`, lines 233-236). The next LOOK at that room fires it (lines 229-231). So an entry is armed by the first trigger, and a look fires it.
- A message aimed at the warded item fires it when the trigger type matches and the actor is not sneaking (lines 241-245).
- For TOUCH wards, a long list of other actions also fires it: opening, giving, delicate-hands actions, justice, general, noisy and quiet movement, hands, locking, pulling, pushing and unlocking (lines 247-259).

Firing calls `doMyThing()` (lines 208-214), which tells the caster "** You hear the wind whisper to you; your ward has been triggered." and removes the ward. `isLocalExempt` (lines 175-194) exempts land-title holders and monsters from the same area as a start room, but only for non-removable traps. Its callers are outside this file (unverified).

**Venom Ward** adds 100 to the poison save (`Chant_VenomWard.java`, line 89, standard duration).

**Water Cover** (`Druid_WaterCover.java`) is a hiding skill for watery rooms. It needs a watery room (line 197), no combat (line 191), and a check `proficiencyCheck(mob, levelDiff*10, auto)` where `levelDiff` is the druid's level plus twice XLEVEL minus the highest-level awake, non-group, non-sysop MOB in the room (lines 203-208, `getHighestLevelMOB` lines 150-177). The effect sets `IS_HIDDEN` and clears sneaking (lines 131-133). It adds save-detection equal to proficiency plus `2*XLEVEL` (lines 140, 225). It breaks on any sound, speech, entry, exit or recall message that is not a look or examine (`executeMsg`, lines 110-118). The effect is added with `addEffect` and does not start a timer here, so its expiry depends on the copied object's default tick-down, which was not checked.

**Druid Recover Voice** (`Druid_RecoverVoice.java`) removes effects that block speech. It scans the druid's own effects (`returnOffensiveAffects`, lines 85-100) for effects that stop the wearer speaking, where the effect's invoker is either unknown or no higher than the druid's level plus 10 plus twice XLEVEL (lines 95-99). It removes them and reports "You failed in your vocal meditation." on failure.

### 11. Combat and malice

- **Rend** (`Druid_Rend.java`): in animal form with legs, against a target that is not standing (checks in `castingQuality` and `invoke`). Its top damage is `(adjustedLevel + X2)*2` (line 155) and the roll is `1d(top) + adjustedLevel` (line 156), halved on a saved hit. It also breaks a `Fighter_Pin` on the druid.
- **Bite** (`Druid_Bite.java`): in animal form with a mouth. Damage is `1d(adjustedLevel + 2 + X2)` (line 135). Both attacks use a proficiency check with the strength difference minus 10.
- **Worms** (`Chant_Worms.java`): a disease. Each tick the hunger value is adjusted by -100 (line 112; the sign convention was not checked). Every five ticks it does `(level + 2*XLEVEL)/4 + 1` damage (line 117). It also reduces Constitution and Strength by one each (`affectCharStats`). It lasts 688 ticks (line 175). Curing it grants immunity through `spreadImmunity` (line 147).
- **Yearning** (`Chant_Yearning.java`): a malicious chant that lowers Wisdom by five, to a minimum of one (line 78), for `TICKS_ALMOST_FOREVER` (line 151, `Abilities/interfaces/Ability.java:48`). It ends when the afflicted character performs one of the two mating socials, `MATE <T-NAME>` or `SEX <T-NAME>`, on a target in the same room while neither wears leg or waist items (lines 111-115). Castable only out of combat (`castingQuality`).
- **Warp Wood** (`Chant_WarpWood.java`): reduces a wooden, wear-prone item's uses by `100 + (level + 2*XLEVEL) - item level`, halved for bonus items. Once uses reach zero, the item is destroyed (`invoke`). The target search is covered in Problems.
- **Volcanic Chasm** (`Chant_VolcanicChasm.java`): a cave-only room effect. Each tick, each MOB the caster may fight and that is not in the caster's group has a chance of `rollPercentage() > fire save` to take `roll(1, level + 2*XLEVEL, 1)` melting fire damage (lines 88-93). Items on the floor are set burning (`Burning`, line 102). If the effect cannot be removed and no tick has happened yet, a MOB entering the room starts its ticks after three ticks (lines 126-130).

### 12. Summons and vines

- **Vine Weave** (`Chant_VineWeave.java`) needs a room with resource choices (lines 85-95). It selects a material in the order vine, silk, hemp, wheat, seaweed (lines 101-112), and creates a woven item through the `Weaving` ability. The item expires as a resource. Overriding mana is 50 (line 70).
- **Vampire Vine** (`Chant_VampireVine.java`) extends `Chant_SummonVine` and overrides `determineMonster` (line 108). The vine's level is the larger of the adjusted level and the caster's level minus five plus half XLEVEL. Its damage is `30 + 9*(level/5)` and its attack is `10 + level` (lines 108-135). It is life-stealing: when it deals more than three damage, it heals itself and the caster by a quarter of that damage (lines 92-101).
- **Vine Mass** (`Chant_VineMass.java`) summons `(level + 2*XLEVEL)/4` vines (line 100). Each has damage `6 + 5*(level/5)` and attack 10. Every vine except the last gets a `Chant_VineMass` effect for the standard duration (line 145) and must follow the caster. If one does not, the whole summon stops and returns null (lines 146-150).

### 13. Timing and cost rules shared with other buckets

Several numbers in this slice come from `StdAbility`:

- A beneficial effect with tick adjustment 0 starts from `casterLevel*4 + 40` ticks (`getBeneficialTickdownTime`, `Abilities/StdAbility.java:2325`), before the class-specific adjustment in `adjustBeneficialTickdownTime`. A positive adjustment is used as an absolute tick count (line 2314). A negative one is a percentage of the standard time.
- A malicious effect with adjustment 0 starts from `round(adjustedLevel*1.1) + 25` ticks (`getMaliciousTickdownTime`, `Abilities/StdAbility.java:2178`), before `adjustMaliciousTickdownTime`.
- `maliciousAffect(mob, target, asLevel, tickAdjust, additionalCheck)` takes the tick adjustment as its fourth argument and an extra check code as its fifth (`Abilities/StdAbility.java:2185`). The code passes -1 in most places.
- A cost of `COST_ALL` means all mana. A cost between `COST_PCT` and `COST_ALL` means a percentage of maximum (`Abilities/StdAbility.java:1614-1618`). The constants are in `Abilities/interfaces/Ability.java:651-655`.
- `TICKS_FOREVER` is `Integer.MAX_VALUE - 1000` (`Abilities/interfaces/Ability.java:46`).

## Content worth mining

### Shape-shift forms (`resources/skills/shapeshift.txt`)

Twelve forms, each with five tier names (weakest to strongest). The multipliers are the file's values.

| Form | Attack | Damage | Armor | Speed | Tier names |
|---|---|---|---|---|---|
| Rodent | 0.7 | 0.4 | 1.0 | 0.2 | Rat, Beaver, Porcupine, Dire Rat, WereBat |
| Feline | 1.0 | 0.6 | 0.5 | 0.0 | Kitten, Cat, Puma, Lion, Manticore |
| Canine | 1.0 | 0.8 | 0.4 | 0.0 | Puppy, Dog, Wolf, Dire Wolf, WereWolf |
| Bird | 0.2 | 1.0 | 0.5 | 0.0 | Robin, Owl, Hawk, Eagle, Harpy |
| Snake (REPTILE) | 0.3 | 0.4 | 1.0 | 0.0 | Garden Snake, Snake, Python, Cobra, Naga |
| Ursine | 1.2 | 0.6 | 0.3 | 0.0 | Cub, Black Bear, Grizzly Bear, Polar Bear, WereBear |
| Insect | 0.7 | 0.6 | 1.0 | 0.3 | Cricket, Millipede, Tarantula, Scarab, ManScorpion |
| Primate | 1.0 | 0.8 | 0.2 | 0.0 | Spider Monkey, Chimp, Ape, Gorilla, Sasquatch |
| Bovine | 0.7 | 1.0 | 0.2 | 0.0 | Calf, Cow, Buffalo, Bull, Minotaur |
| Fish | 0.3 | 0.4 | 0.5 | 1.0 | Clown Fish, Angelfish, Swordfish, Shark, Merfolk |
| Sea Mammal | 1.0 | 0.5 | 0.3 | 0.2 | Seal, Walrus, Dolphin, Whale, Selkie |
| Mustelid | 0.8 | 0.2 | 0.4 | 0.5 | Badger, Otter, Wolverine, Dire Otter, Mustie |

The file is at `resources/skills/shapeshift.txt:1-107`. Each form's race IDs are listed on the `RACES=` line of its entry.

### Tier and budget tables

| Mechanic | Tiers and thresholds | Source |
|---|---|---|
| Shape-shift tier | class level 0-5, 6-11, 12-17, 18-23, 24+ gives tiers 0-4 | `Druid_ShapeShift.java:383-398` |
| Golem tier | class level 0-4, 5-9, 10-14, 15-24, 25+ gives tiers 0-4 | `Druid_GolemForm.java:238-253` |
| Plant tier | class level 0-4, 5-9, 10-14, 15+ gives tiers 0-3 | `Druid_PlantForm.java:197-209` |
| Pack Call budget | 90 + 10 per XLEVEL point; pack member level is 0, 5, 11, 17 or 23 by tier | `Druid_PackCall.java:233-244` |
| Golem names | Steel, Quartz, Mithril, Diamond, Adamantite Golem | `Druid_GolemForm.java:105-111` |
| Plant names | Flower, Vine, Tumbleweed, Shambler | `Druid_PlantForm.java:103-107` |

### Class grants (level in parentheses)

The grants come from `addCharAbilityMapping` calls in `CharClasses/`.

| Ability | Granted to |
|---|---|
| Druid_ShapeShift | Druid (1), Beastmaster (1), Mer (1); Beastkin race cultural list (`Races/Beastkin.java:88`) |
| Druid_ShapeShift2 | Beastmaster (6), Mer (17) |
| Druid_ShapeShift3 / 4 / 5 | Beastmaster (11 / 16 / 21) |
| Druid_ShapeShift6 to 11 | No grant found in the class files (unverified elsewhere) |
| Druid_MyPlants | Gaian (1), Mer (1), Delver (1), Druid (1) |
| Druid_DruidicPass | Gaian (1), Beastmaster (1), SkyWatcher (1), Druid (1) |
| Druid_AquaticPass, Druid_SeaLore | Mer (1) |
| Druid_KnowPlants | Gaian (7), Mer (7), Scholar (7) |
| Druid_WaterCover | Mer (7) |
| Druid_RecoverVoice | SkyWatcher (16) |
| Druid_PlantForm | Gaian (10) |
| Druid_GolemForm | Delver (10) |
| Druid_Bite | Beastmaster (7) |
| Druid_Rend | Beastmaster (25) |
| Druid_PackCall | Beastmaster (30) |
| Druid_Krakenform | Mer (35) |
| Druid_WarpWood | Druid (8) |
| Chant_Uplift | Beastmaster (35) |
| Chant_WindColor | SkyWatcher (2) |
| Chant_WindGust | SkyWatcher (9), Druid (11), Ranger (16) |
| Chant_WindShape | SkyWatcher (12) |
| Chant_WhiteMoon | SkyWatcher (13) |
| Chant_WakingMoon | SkyWatcher (14) |
| Chant_WindSnatcher | SkyWatcher (23) |
| Chant_WarningWinds | Mer (15), SkyWatcher (11) |
| Chant_WhisperWard | Mer (12), SkyWatcher (10), Druid (12) |
| Chant_WaterWalking | Mer (5), Ranger (20), Druid (9) |
| Chant_WaterHammer | Mer (8) |
| Chant_Waterguard | Mer (10) |
| Chant_VenomWard | Mer (11), Beastmaster (6), Ranger (14), Druid (8) |
| Chant_Waterspout | Mer (24) |
| Chant_Whirlpool | Mer (25) |
| Chant_Yearning | Beastmaster (12) |
| Chant_VineWeave | Gaian (11) |
| Chant_VampireVine | Gaian (18) |
| Chant_VineMass | Gaian (21) |
| Chant_Worms, Chant_VolcanicChasm | Delver (21, 24) |

### Other abilities at a glance

| ID | Kind | What it does | Lines |
|---|---|---|---|
| Chant_WhiteMoon | Room, malicious | Lawful creatures take roughly half damage and chaotic ones about 1.5 times; one moon per room | 219 |
| Chant_WakingMoon | Room | Active only at dawn or day; removed by its tick otherwise | 190 |
| Chant_WindGust | Malicious | Pushes hostile MOBs back (range +2 or +4) and may knock them down for 2 or 4 ticks | 208 |
| Chant_WindSnatcher | Beneficial | Vetoes five named wind spells aimed at the protected MOB | 192 |
| Chant_WindShape | Beneficial | Quarter reduction on weapon damage | 151 |
| Chant_WarningWinds | Beneficial | Reports fights and enemies within two steps of each new room | 203 |
| Chant_WaterWalking | Beneficial | Flying flag over water surface rooms | 191 |
| Chant_WaterHammer | Malicious, range 10 | Ranged water blow; needs a nearby water source | 188 |
| Chant_Waterguard | Beneficial | Water-attack shield, pool of level plus twice XLEVEL | 160 |
| Chant_VenomWard | Beneficial | +100 to poison save | 123 |
| Chant_VolcanicChasm | Room, malicious, cave only | Fire damage each tick, burns floor items | 199 |
| Chant_WarpWood | Malicious | Wears down a wooden item until destroyed | 177 |
| Chant_Worms | Disease | Periodic damage, hunger drain, -1 Con and -1 Str, 688 ticks | 188 |
| Chant_Yearning | Malicious | Wisdom -5 until a mating social in the same room, out of combat only | 161 |
| Chant_VineWeave | Crafting | Woven item from room plant resources, 50 mana | 152 |
| Chant_VampireVine | Summon | Life-stealing vine, heals self and caster by a quarter of damage over three | 161 |
| Chant_VineMass | Summon | Several vines; each but the last must follow | 159 |
| Druid_Rend | Skill, in form | Leg attack on a target that is not standing; breaks Fighter_Pin | 172 |
| Druid_Bite | Skill, in form | Mouth attack, 1d(level+2+X2) | 152 |
| Druid_RecoverVoice | Skill | Removes effects that block speech | 142 |
| Druid_SeaLore | Auto, water | Damage, attack and armor bonus in water rooms | 120 |
| Druid_AquaticPass | Movement | Hidden, invisible, sneaking movement from water rooms | 216 |
| Druid_DruidicPass | Movement | Sneaking, invisible movement in wild outdoor rooms | 222 |
| Druid_KnowPlants | Skill | Describes a vegetable or wooden item's material | 195 |
| Druid_WaterCover | Hiding | Hidden in a watery room until a noisy or moving action | 235 |

### Wind colour lexicon

From `Chant_WindColor.java:112-140`. The colour words are black, red, blue, green, grey, maroon, yellow, brown, orange, white and purple, in that index order. Each stands for a property: black undead, red evil, blue good, green neutral, grey metal or rock, maroon hidden, yellow invisible, brown wood, orange trapped, white lawful, purple chaotic. The level words for a "stripes" source are dull, faded, striped, brightly striped, brilliant and dazzling; for a "swirl" source they are "a swirl of dull", "a swirl of faded", "a swirl of", "a bright swirl of", "a swirl of brilliant" and "a swirl of dazzling" (`Chant_WindColor.java:338-407`).

### Messages worth keeping

- Shape-shift: "<S-NAME> take(s) on @x1 form." and "<S-NAME> revert(s) to @x1 form." (`Druid_ShapeShift.java:748, 361`).
- Pack Call: "<S-NAME> arrive(s) @x1 and attack(s) <T-NAMESELF>!" (`Druid_PackCall.java:283`).
- Uplift: "<S-NAME> morph(s) into a @x1." and "<S-YOUPOSS> uplift morphing ended unsuccessfully." (`Chant_Uplift.java:301, 310`).
- Whirlpool: "A MASSIVE WHIRLPOOL FORMS HERE!" and "You're swept into a terrible whirlpool!" (`Chant_Whirlpool.java:302, 220`).
- Whisper Ward: "** You hear the wind whisper to you; your ward has been triggered." (`Chant_WhisperWard.java:211`).
- Water Cover: "You slink into a watery corner and remain completely still." (`Druid_WaterCover.java:206`).
- Plant Form: "Attack a plant?!" (`Druid_PlantForm.java:129`).

## Notable design ideas

1. **Totem slots as exclusive, permanent claims.** The twelve forms are a shared pool. Each slot is a separate ability that claims one form on first use (`Druid_ShapeShift.java:507-614`). Choosing once gives the player something to remember.
2. **Forms as merged text data.** Forms come from a data file, and extension files merge into the same list (`Druid_ShapeShift.java:218-249`). Balance changes need no code.
3. **Derived stats from one scalar.** Every form's modifiers come from one "adjusted level", capped by tier (lines 318-327). A form scales with the druid without new code.
4. **Morph as a timed state.** Uplift's "morphing" and "neomorphing" stages are one timer with one threshold at 25% (`Chant_Uplift.java:89-92, 105-106, 141-160`). The end state depends on how far the timer got.
5. **Runtime sub-areas.** The Kraken ship (`Druid_Krakenform.java:111-175`) and the Whirlpool grid (`Chant_Whirlpool.java:306-322`) are built as areas when cast and discarded when removed. A room effect can therefore own a separate piece of map.
6. **Perception as colour words.** Wind Color turns hidden properties (hidden, invisible, evil, lawful) into colour words and strength words, so a player perceives the room through a skill (`Chant_WindColor.java`).
7. **Level budget for summons.** Pack Call stops when the sum of member levels reaches `90 + 10*XLEVEL` (`Druid_PackCall.java:233-248`), which limits strong summons without a separate cap.
8. **Trap words.** Whisper Ward reads its trigger from the last word of the command (`Chant_WhisperWard.java:277-297`), and an entry only arms the ward until someone looks (lines 229-236).
9. **Pacifism by form.** A hostile NPC that is not already fighting a plant-form druid refuses to attack it (`Druid_PlantForm.java:117-139`), so the form is a defensive stance and not just a stat change.
10. **Shield as a damage budget.** Waterguard's pool is consumed by damage it absorbs (`Chant_Waterguard.java:113-117`). The effect ends by running out rather than by time.

## Problems and cruft

Each item was checked against the cited lines unless marked unverified.

1. **Wrong return value in `getMyPlants(mob, rooms)`.** `Druid_MyPlants.java:264` returns `myPlants` (unfiltered) instead of `V` (filtered). The caller `Chant_SiftWrecks.java:91` expects only plants in the area and therefore searches every plant the druid has, not just the local ones.
2. **`return` inside the loop in `Chant_WarpWood.getPossibility`.** The `return` statements at `Chant_WarpWood.java:94-97` are inside the `for` loop that starts at line 81, so the first eligible wooden item is returned. The preference for worn over carried items never applies, and the random pick (`roll(1, size, -1)`) always picks from a list of one.
3. **Description written to display text.** `Chant_Uplift.java:306-307` reads `mob.description()` but writes the result with `setDisplayText`, so the description is never changed.
4. **Missing space in the "Neo" name.** `Chant_Uplift.java:157` builds `substring(0, x+1) + "Neo" + substring(x+1)`, which produces "Giant NeoRat" from "Giant Rat". It is cosmetic.
5. **Empty `if` in Krakenform's `castingQuality`.** `Druid_Krakenform.java:314-317` tests the combat and shape-shift conditions and does nothing, so the method has no effect.
6. **Overwritten success flag in Wind Gust.** `Chant_WindGust.java:191` assigns `success` from the knockdown application, so the returned value reflects the last knockdown, not the chant's proficiency check. Whether this changes the mana or cooldown charge was not checked (unverified).
7. **Cotton accepted but never chosen.** Vine Weave allows cotton (`Chant_VineWeave.java:91`) but never selects it (lines 101-112). The default is vine.
8. **Unbounded search in Pack Call.** `Druid_PackCall.java:242-243` loops until the current shape name matches a tier name. The name is always taken from the tier table, so this terminates in practice, but a mismatch would loop forever.
9. **Dropped member object.** `Druid_PackCall.java:237` creates a `GenMOB` before the budget check at lines 246-248, and the object is not destroyed when the budget runs out (minor).
10. **Redundant riding branch.** In `Chant_Whirlpool.canEverEnterThePool(MOB)` (lines 152-164), the riding result is always overwritten: the caster gate (lines 141-145) returns early, and line 161-164 sets the flag to true for any fightable MOB.
11. **Unchecked cast in the Whirlpool tick.** `Chant_Whirlpool.java:208` and `221` cast `theWhirlpool` to `GridLocale` without a null check. The field is set only in `invoke`. Whether any other path creates the effect is unverified.
12. **Side effect in a getter.** `Druid_GolemForm.displayText()` (lines 95-103) calls `unInvoke()` when there is no race. `Druid_PlantForm.displayText()` (lines 92-101) does the same.
13. **Speed divisor reaches zero.** Golem speed is divided by `1.5 - 0.1*XLEVEL` (`Druid_GolemForm.java:133, 140`), which is zero at XLEVEL 15. Whether XLEVEL can reach that value was not checked.
14. **Process-wide plant cache keyed by name.** `Druid_MyPlants.java:84` stores plant lists in a static map keyed by the druid's name (lines 219, 237). The data is shared across all sessions and survives a character being recreated under the same name.
15. **Possibly unbounded recursion in plant search.** `tryRoom` (lines 178-215) recurses into every room of every boardable item's area without a room visited set. Two boardables that contain each other's rooms would recurse without bound. This needs a specific layout and was not tested (unverified).
16. **Possible null dereference in Know Plants.** `Druid_KnowPlants.java:160` calls `getSubType().equalsIgnoreCase(...)` without a null check, although the code a few lines earlier checks for null. Whether `getSubType()` can be null for a raw material was not verified.
17. **Unused parameter.** `Druid_ShapeShift.getRace(classLevel, raceCode)` (lines 437-441) ignores `raceCode` and reads the field.
18. **Unguarded parse.** `Druid_ShapeShift.setMiscText` (lines 268-271) calls `substring(6, x)` where `x` is `indexOf(']')`. A missing `]` makes `x` -1 and throws.
19. **Busy retry with a time guard.** `Druid_ShapeShift.java:547-548` retries a random pick for up to ten seconds with no delay. With 12 forms and at most 11 slots, a free form always exists, so the guard should be unreachable.
20. **Dormant Waterspout effect.** The `IS_FLYING` override and the existing-effect check (`Chant_Waterspout.java:89-94, 128-132`) depend on an effect that `invoke()` never attaches. No other code in the repository refers to the class except its grant.
21. **Duplicated code.** `Druid_AquaticPass` and `Druid_DruidicPass` have nearly identical `invoke()` bodies, including the door-forcing and restore logic. The class-level formula `qualifyingClassLevel + 2*XLEVEL - qualifyingLevel` with the 30 fallback is repeated in ShapeShift, GolemForm and PlantForm.
22. **Failed pass still moves.** `Druid_AquaticPass.java:152` walks the character through an open exit even when the proficiency check fails. This may be intended but is not documented.
23. **Hunger sign unverified.** `Chant_Worms.java:112` calls `adjHunger(-100, ...)`. The sign convention for hunger was not checked.
24. **Single-player assumptions.** Several mechanics assume other players: group membership (`getGroupMembers` in WindColor, Whirlpool and Waterspout), `mayIFight` between players, the invoker's group in Whirlpool, sysop exclusion in Water Cover, and global persistence of Uplift races. See the Python takeaways.

## Takeaways for a Python SUD

- **Forms as data with a tier function.** Load forms from a YAML or JSON file with four multipliers and five tier entries (shape name and body template). Tier is `min(4, class_level // 6)`, which matches `getMaxRaceLevel` exactly. Derive stats in one function `derive_stats(base, effects)` rather than layered overrides.
- **Keep the claim, drop the slots.** The permanent per-slot form binding is a good idea, but a single-player game can store one `chosen_form` on the character, with an optional "forms unlocked" set. The ten-slot machinery is not needed.
- **Summons as a budget.** Use `budget = 90 + 10 * xlevel` and a pack member's level equal to its tier threshold minus one. Members are plain records with `summoned_by` and `expires_at_turn`, not generated mob objects.
- **Uplift as a progress bar.** Model morphing as a float from 0 to 1. Stage "neo" from 0.25, and complete at 1.0. Store the generated race as a record in the save file instead of a global registry and database table. Keep the caster sleep, since it is a cost, or drop it.
- **Room effects with hooks.** Give rooms a list of timed effects, each with `on_tick`, `on_enter` and `on_remove`. Replace the Whirlpool's runtime grid with a single "pocket" room object reached by an exit. The redirect from the surface room and the sweep both become one `move_to(corner)` call.
- **Perception strings as pure functions.** Port Wind Color as `describe_aura(room, observer) -> str` and test it with fixtures. The colour lexicon is a good flavour source for a single-player "sense" skill.
- **Triggers as words.** Port the ward triggers as an enum parsed from the last word, and keep the rule that an entry arms and a look fires.
- **Turns, not ticks.** The `casterLevel*4 + 40` duration maps to `level*4 + 40` turns. Decide between real-time and turn-based before porting the timers (the brief leaves this open).
- **Drop the multiplayer parts.** Group checks, invoker-versus-target fight rules, sysop exclusions, name-keyed caches and database races have no place in a single-player game. Plants become objects owned by the player.
- **Drop the protocol and language parts.** Sound protocol hints, Krakenspeak translation and the MSP sound calls are not needed.

## Coverage

- **Read in full (12 files):** `Druid_ShapeShift.java`, `Druid_Krakenform.java`, `Druid_GolemForm.java`, `Druid_PlantForm.java`, `Druid_MyPlants.java`, `Chant_Uplift.java`, `Chant_WindColor.java`, `Chant_Whirlpool.java`, `Chant_Waterspout.java`, `Druid_WaterCover.java`, `Chant_WhisperWard.java`, `Druid_PackCall.java`.
- **Read in full, condensed (24 files):** `Chant_WhiteMoon.java`, `Druid_AquaticPass.java`, `Chant_WindGust.java`, `Chant_WarningWinds.java`, `Chant_VolcanicChasm.java`, `Druid_KnowPlants.java`, `Chant_WindSnatcher.java`, `Chant_WaterWalking.java`, `Chant_WakingMoon.java`, `Chant_WaterHammer.java`, `Chant_Worms.java`, `Chant_WarpWood.java` (block structure checked), `Druid_Rend.java`, `Chant_Yearning.java`, `Chant_Waterguard.java`, `Chant_VineWeave.java`, `Druid_Bite.java`, `Chant_WindShape.java`, `Druid_RecoverVoice.java`, `Chant_VenomWard.java`, `Druid_SeaLore.java`, `Druid_DruidicPass.java`, `Chant_VampireVine.java`, `Chant_VineMass.java` (block structure checked). Condensed reads strip blank and brace-only lines, so exact nesting is inferred except where checked, and some line numbers are given by method name instead.
- **Totem stubs (10 files):** `Druid_ShapeShift2.java` and `Druid_ShapeShift11.java` were read in full; `Druid_ShapeShift3.java` to `Druid_ShapeShift10.java` were checked by grep for ID, name, superclass and trigger list.
- **Data read in full:** `resources/skills/shapeshift.txt` (107 lines).
- **Outside the bucket, consulted for the mechanics cited:** `Abilities/StdAbility.java` (`beneficialAffect`, `startTickDown`, tick-down and malicious-tick functions, `maliciousAffect`, cost handling, `copyOf`), `Abilities/interfaces/Ability.java` (tick and cost constants), `Libraries/interfaces/AbilityMapper.java` (qualifying-level docs), `Libraries/CMAbleMap.java` (`qualifyingClassLevel`), `Libraries/Sense.java` (`isInWilderness`, `isWateryRoom`, `isWaterySurfaceRoom`), `Abilities/Druid/Chant_SiftWrecks.java:85-100` (the `getMyPlants` caller), `CharClasses/*.java` (grants listed in Content), `Races/Beastkin.java:88`.
- **Skipped or only referenced:** `Chant.java`, `Chant_SummonVine.java`, `Chant_Hibernation`, `Chant_BreathColor.java`, `Chant_DistantWindColor.java`, `Burning`, `Weaving` and the `GenMOB` and `GenSailingShip` classes. These belong to other buckets, or were not needed beyond their call sites.
- **Not run:** no code was executed. Every behaviour above is from reading the source, and items marked "unverified" were not confirmed.
