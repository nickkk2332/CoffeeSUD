# Druid chants, part 6: summons, weather, plants and tides

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b056` (Abilities/Druid [part 6/7: Chant_SummonFood .. Chant_UnicornsHealth]) · 47 files · 9566 lines of Java.

Citation convention: a bare `File.java:NNN` in the text is in `com/planet_ink/coffee_mud/Abilities/Druid/`. Files outside that folder are given with their full path from the CoffeeMUD folder. Line numbers were checked against the source.

## Summary

This bucket holds 47 of the Druid chant abilities, all subclasses of `Chant` (the base is read for context). They fall into five groups: summons that create temporary minions or hazards (vine, sapling, golem, mount, fish school, jellyfish, insect swarm, tornado); planted objects that persist in rooms (fungi, houseplants, seaweed, ivy, trees, berries, herbs, seeds, springs); weather and sky control, which nudge each area's climate state machine (rain, heat, wind, squall, hail, lightning, sun, moon); water and tide effects (tidal wave, tsunami, tide moon); and status effects on a person or item (tree transformation, rooting, thorns, tethers, unbreakable items, tangles, sunbeams). Nearly every chant follows one template: a proficiency roll (often with a level or size penalty), a `beneficialAffect` or `maliciousAffect` whose duration is derived from caster level, and a side effect on a room, area, item or mob. For a single-player SUD the most reusable parts are the weather transition tables, the summon lifecycle rules, and the plant-as-world-object model.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant.java` (outside bucket, read in full) | Base class: casting gate, weather bitmask, "render mundane" switch | 201 |
| `Chant_TidalWave.java` | Water wave from the nearest water that washes boats and rooms | 526 |
| `Chant_Tsunami.java` | Shore-only flood over rooms within two steps | 342 |
| `Chant_Treemorph.java` | Turns a target into a rooted oak tree with a tethered item | 304 |
| `Chant_SummonPlants.java` | Base for stationary plants: creation, ownership, decay, city bonus | 296 |
| `Chant_SummonVine.java` | Combat vine minion with out-of-combat timeout | 296 |
| `Chant_SummonSapling.java` | Combat sapling warrior in forest, jungle or wooden rooms | 291 |
| `Chant_SummonSchool.java` | Mirror-image school while shape-shifted underwater | 275 |
| `Chant_SummonTornado.java` | Storm one-shot that whirls items and mobs between rooms | 275 |
| `Chant_Treeform.java` | Self-rooting status; leaving the area ends it | 275 |
| `Chant_SummonRockGolem.java` | Cave or rock golem minion, one per caster | 263 |
| `Chant_SummonMount.java` | Wild horse placed in an adjacent outdoor room, then walked to the caster | 249 |
| `Chant_TapGrapevine.java` | Relays speech heard by another druid's plants | 222 |
| `Chant_Treehouse.java` | Creates a temporary room above the caster | 215 |
| `Chant_Tangle.java` | Binds a target to a plant; struggle budget | 202 |
| `Chant_SummonHail.java`, `Chant_SummonRain.java`, `Chant_SummonHeat.java`, `Chant_SummonWind.java`, `Chant_SummonSquall.java` | Weather nudges through `Climate` | 178, 174, 174, 159, 165 |
| `Chant_TideMoon.java` | Area tide offset through an effect code | 153 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Druid_MyPlants.java` (outside bucket, partly read) | Plant registry and ownership test | 340 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (outside bucket, excerpts) | Tick durations, `proficiencyCheck`, `adjustedLevel` | 3018 |
| `com/planet_ink/coffee_mud/Common/DefaultTimeClock.java` (outside bucket, excerpt) | Applies tide-altering effect codes to the tide phase | 1378 |

## How it works

### 1. The chant base and the shared template

- **Casting gate.** `Chant.invoke` (`Chant.java:191-200`) calls `chantAlignmentCheck` (`Chant.java:167-189`). For a non-automatic cast by a player, not rendered mundane, there is a 50% roll (`rollPercentage()<50`, line 174). A faction clash prints "Extreme emotions disrupt your chant." (line 178). Wearing armour that is not non-metal prints "watch(es) ... armor absorb ... magical energy!" and fails the cast (lines 182-185).
- **Mundane rendering.** `setMiscText("render mundane")` (`Chant.java:110-117`) changes the classification to `ACODE_SKILL` (line 122) and removes verbal casting (lines 68-82). `Chant_SummonTornado`, `Chant_SummonHail` and `Chant_SummonLightning` use this to name themselves "tornado", "hail" and "lightning" (`Chant_SummonTornado.java:46`, `Chant_SummonHail.java:46`, `Chant_SummonLightning.java:46`). The same ability can therefore be a natural event rather than a spell.
- **Weather bitmask.** `Chant.weatherQue` (`Chant.java:126-165`) maps the area's weather to WIND=1, RAIN=2, HOT=4, COLD=8, CALM=16. Indoors, blizzard, thunderstorm and heat wave return nothing. Clear returns all four active bits. Fog returns RAIN|CALM, cloudy returns WIND|RAIN, drought returns RAIN|COLD, dust storm returns RAIN|CALM|COLD, hail returns HOT|CALM, rain returns WIND|RAIN, sleet returns HOT, snow returns WIND, windy returns RAIN, winter cold returns RAIN.
- **Template.** Most `invoke` methods run the same steps:
  1. Check the location (indoors, city, water, air or cave), with a message such as "You must be outdoors for this chant to work."
  2. Call `super.invoke`, which pays the cost.
  3. Call `proficiencyCheck(mob, adjustment, auto)`.
  4. Build a `CMMsg`, ask `location().okMessage(...)`, then `send`.
  5. Apply the effect with `beneficialAffect(mob, target, asLevel, tickAdjust)` or `maliciousAffect`.
  6. On failure, call `beneficialWordsFizzle` or `maliciousFizzle`.

### 2. Numbers shared by all chants (verified)

- **Tick length.** `TIME_TICK = 4000` ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`). `Climate.WEATHER_TICK_DOWN=150` is commented "10 minutes * 60 seconds / 4" (`com/planet_ink/coffee_mud/Common/interfaces/Climate.java:191`).
- **Default beneficial duration** is `casterLevel*4+40` ticks (`StdAbility.java:2312-2331`, `getBeneficialTickdownTime`). A positive explicit tick count is used as-is. A negative value is a percentage of the default. At level 20 the default is 120 ticks, about 8 minutes.
- **Default malicious duration** is `round(1.1*level)+25` ticks (`StdAbility.java:2174-2185`).
- **`proficiencyCheck`** (`StdAbility.java:1441-1487`). The chance is `proficiency + 2*XLEVEL`, clamped to 5-95 unless the `DIS955RULE` flag is disabled, plus `XLEVEL/2`. A non-negative adjustment is added to the chance. A negative adjustment first rolls percent and fails when the roll exceeds `100+adjustment`, so a penalty of N is an N% extra auto-fail gate, and -100 always fails. Then the normal roll must be below the chance.
- **`adjustedLevel`** (`StdAbility.java:899-912`). Monsters, and players who qualify, get the lowest qualifying level plus the amount their class level exceeds the qualifying level. A player who does not qualify gets `level - lowestQualifyingLevel - 25`.
- **Dice.** `roll(n, d, mod)` is the sum of n rolls of 1..d plus mod, and returns mod when d<=0 (`com/planet_ink/coffee_mud/Libraries/Dice.java:398-407`). `rollPercentage()` is 1..100 (`Dice.java:409-412`).
- **Expertise.** `getXLEVELLevel`, `getX1Level`, `getXTIMELevel` and `getXMAXRANGELevel` read the ExpertiseLibrary types LEVEL, X1, TIME and MAXRANGE (`StdAbility.java:419-467`).

### 3. Summons: minions and hazards

**Minion template.** Each `determineMonster` builds a `GenMOB` or `GenRideable`. It sets the level, HP ability, armour, attack, damage and race. It adds `Prop_ModExperience` with parameter "0", which appears to stop experience from the minion (not traced). It adds the tattoos `SYSTEM_SUMMONED` and `SUMMONED_BY:<caster>`, calls `bringToLife`, then `postFollow(minion, caster)`. If the follow fails the caster sees "... seems unwilling to follow you." (for example `Chant_SummonVine.java:285-287`).

**Level and stats.** Vine, sapling and golem use `max(adjusted level, casterLevel - 5 + XLEVEL/2)`:

| Minion | Level | Stats | Source |
|---|---|---|---|
| Vine ("a vine", race Vine) | same rule, min 1 | HP base+2, then x2; damage 6+5*(L/5); armour 100-(30+L/2); attack +10 | `Chant_SummonVine.java:247-271` |
| Sapling ("<wood> sapling", race TreeGolem) | same rule | HP base x2; armour, attack, damage, speed from `CMLib.leveler()` | `Chant_SummonSapling.java:242-267` |
| Rock golem ("an golem of stone", race StoneGolem) | same rule, no min-1 clamp | HP base x2; armour +100; attack +100; speed/2.0; damage +20 | `Chant_SummonRockGolem.java:208-224` |

Vine and sapling get `Fighter_Rescue` at proficiency 100 (`Chant_SummonVine.java:261-263`, `Chant_SummonSapling.java:257-259`). The rock golem gets `Fighter_Rescue` and `Fighter_Kick` at default proficiency, plus the `CombatAbilities` behaviour (`Chant_SummonRockGolem.java:235-237`). It also gets a `Prop_StatTrainer` configured with "NOTEACH STR=20 INT=1 WIS=10 CON=15 DEX=3 CHA=10" (lines 229-234; the intent is not traced).

**Lifecycle.** The minion is a timed effect, and its `tick` removes it when a rule fails:

| Summon | Ends when | Source |
|---|---|---|
| Vine | Not following, dead, or in another room. Also after more than 5 ticks out of combat (`peaceTicks`) | `Chant_SummonVine.java:98-119` |
| Sapling | Not following, dead, out of combat (immediately), or in another room | `Chant_SummonSapling.java:98-110` |
| Rock golem | Not following, dead, or in another room. Only one per caster (`hasAControlledFollower`, line 158) | `Chant_SummonRockGolem.java:98-112` |
| Mount | Not following, dead, the caster has left the room or the game, or the caster rides something other than the mount | `Chant_SummonMount.java:128-141` |
| Vine, sapling, golem, mount | Caster quits (TYP_QUIT in `executeMsg`) | `Chant_SummonVine.java:156-167`, `Chant_SummonSapling.java:147-158`, `Chant_SummonRockGolem.java:134-145`, `Chant_SummonMount.java:107-118` |
| Vine, sapling | Minion dies (TYP_DEATH in `okMessage`) | `Chant_SummonVine.java:125-138`, `Chant_SummonSapling.java:116-127` |

**Mount.** `Chant_SummonMount.invoke` collects open exits to outdoor, non-air rooms whose reverse exit is also open (`Chant_SummonMount.java:157-168`). It picks one at random (line 174), creates the horse in that room (line 190), and walks it back toward the caster (line 198). The horse is a `GenRideable` "a wild horse" of race Horse, weight 500, with rider capacity 1 (lines 221-241).

**School.** `Chant_SummonSchool` needs a shape-shift (`Druid_ShapeShift.isShapeShifted`, line 214), an underwater room (line 223), and a race category of "Fish" or "Sea Mammal" (lines 229-239). The number of images is `1d(round(L/3)) + 2`, or `1d L + 100` for ClownFish (lines 249-252). Each melee attack on the caster by someone else is diverted by a roll that is meant to remove one image (lines 99-110; see Problems). The caster's own visible actions are echoed as trailer messages once per image (lines 137-156). Looking at the caster lists each image (lines 159-182).

**Insect swarm.** Each tick the swarm stings the affected target for `1d(3+XLEVEL)` as a cast-spell hit (`Chant_SummonInsects.java:103-111`). The malicious effect lasts `(L+2*XLEVEL)*10` ticks (line 184). The effect ends if the target leaves the casting room (line 104).

**Jellyfish.** `Chant_SummonJellyfish` is a room effect. Its factory MOB "a swarm of jellyfish" carries `Prop_SafePet` (lines 193-202). Each tick it finds the first hostile occupant who is not in a stung list of length 3, and stings for `M.level/10 + XLEVEL(invoker)` with TYP_PARALYZE (lines 107-121). The effect ends when the invoker is out of combat or the swarm is gone (lines 99-104).

**Tornado.** `Chant_SummonTornado` is a one-shot. It collects gettable, unworn items and mobs in the room (lines 160-172). It drops each into a random room from the current room and open adjacent outdoor rooms (lines 175-182, 188-190). Items take wear damage by material (lines 201-236): 1 for precious, rock or mithril; 75 for glass; 50 for cloth, flesh, leather, paper, vegetation, wood or synthetic; 20 for metal; 0 for liquid, unknown, energy or gas. Mobs take `roll((L/2), 7, 1)` (`Chant_SummonTornado.java:258-259`), halved if saved (line 260), as TYP_GAS BASHING (line 262).

### 4. Plants: stationary objects with an owner

**Creation.** `Chant_SummonPlants.buildPlant` (`Chant_SummonPlants.java:117-172`) makes a `GenItem`, or a `GenResource` for fungus. It sets the secret identity to the caster's name (line 154) and registers the plant with `Druid_MyPlants.addNewPlant` (line 157). Its expiration is 0 (line 158), and the room is told "Suddenly, X sprout(s) up here." (line 159). The plant's level is `10 + 10*X1` (line 152).

**Ownership and lifetime.** If the caster owns the land (`CMLib.law().doesOwnThisLand`, line 162), the summon effect is attached as a non-uninvokable effect, so the plant lasts while the caster keeps the land. Otherwise it is a timed `beneficialAffect` of `240*L + 450` ticks (line 169). At level 10 that is 2850 ticks, about 3.2 hours at 4 seconds per tick.

**Identity test.** `Druid_MyPlants.isMyPlant` (`com/planet_ink/coffee_mud/Abilities/Druid/Druid_MyPlants.java:99-116`) is true when the item's secret identity equals the caster's name, the item is in a room, and it has an effect that is a `Chant_SummonPlants` whose invoker is the caster or whose text is the caster's name. The summon effect itself is the ownership token. Other chants (tangle, sweet scent, tap grapevine) use this test.

**Decay on touch.** Getting, pushing or pulling a fungus or houseplant swaps its summon effect for a `Decay` behaviour with min and max equal to `TICKSPERMUDMONTH` and chance 100 (`Chant_SummonFungus.java:78-100`, `Chant_SummonHouseplant.java:78-100`). Once touched, the plant becomes a wild item that decays after one mud month.

**Withering.** When a plant's effect ends, the room sees "X wither(s) away." and the item is destroyed (`Chant_SummonPlants.java:80-97`).

**City bonus and druidic connection.** `Chant_SummonPlants.buildMyThing` (lines 174-226) behaves differently by area:
- Outside a city, once the caster's plants cover at least 50% of the area's visitable rooms, `Chant_DruidicConnection` is applied to the area (lines 183-201).
- In a city, a druid who plants in a room without a plant of their own and without a chant on the grid parent can earn experience and the message "You have made this city greener." (lines 204-221).
- The city award counter resets when `daysInMonth * hoursInDay * millisPerMudHour` of real time has passed, which is one mud month, not one day (line 207). The message and XP are given at most 18 times per window (lines 214-220).

**Plant families** (all in this bucket unless noted):

| Summon | Habitat check | Variants (roll) | Notes |
|---|---|---|---|
| Summon Plants (base) | Outdoors, not city, not underwater, air or surface (`Chant_SummonPlants.java:245-252`) | 5 wildflowers and greens (lines 121-149) | Level 10+10*X1 |
| Summon Fungus | Cave or rock atmosphere (`Chant_SummonFungus.java:110-120`) | Freshwater room: 6 aquatic fungi (lines 131-163). Otherwise 6 mushrooms, including "a wood ear" (lines 170-203) | Decays after one mud month once touched |
| Summon Houseplant | Indoor stone or wood domain only (`Chant_SummonHouseplant.java:110-120`) | 7 potted plants (lines 126-161) | Ownership uses `doesOwnThisProperty` (line 175) |
| Summon Seaweed | Watery room, not indoors (`Chant_SummonSeaweed.java:142-156`) | algae, seaweed, kelp, coral, sponge (lines 84-110) | Base class `Chant_SummonPlants` supplies the lifecycle |
| Summon Ivy | Base class rules | poison ivy (rolls 1, 4), poison sumac (2), poison oak (3, 5) (lines 79-98) | Attaches `Disease_PoisonIvy` (line 104) |
| Summon Tree | Wooden room resource, else a random wooden resource other than WOOD (lines 149-165) | "<wood> tree" (line 83) | Weight 10000, not gettable (lines 87-88) |
| Summon Food | Outdoors, not city, air or water (lines 79-90) | Random berry (line 104) | `floor(L/4)+1` items; nourishment `150+10*X1` (lines 105, 112); `Expire.Resource` (line 116) |
| Summon Herbs | Same as food (lines 79-90) | "some herbs" (line 106) | Same count; nourishment 1 (line 110) |
| Summon Seeds | None (no room check) | Vegetation, cotton, hemp and wooden materials, minus ash, soap, cheese, bread and cracker (lines 75-93) | `floor(L/4)` seeds, weight 0, value 0 (lines 141-155). `list` is free (lines 114-118) |
| Summon Water | Outdoors, not city, water or air (lines 101-112) | A `Spring` item (`Items/Basic/Spring.java`) | Standard beneficial duration (line 139) |
| Summon Pool | Cave only (lines 101-106) | Same `Spring` item named "a magical pool" (lines 127-129) | Standard beneficial duration (line 135) |

**Summon Tree and the room.** Each tick, `Chant_SummonTree` rewrites the room's natural resource to its own wood material (`Chant_SummonTree.java:120-124`). On removal it restores the old resource (lines 137-143). Any inhabitant with a "Chopping" effect removes the tree (lines 125-133). The tree changes the land's resource, so a planted grove changes what can later be harvested.

### 5. Weather and sky

Rain, heat, wind and hail are one-shot changes. Each copies the area's `Climate`, sets `setNextWeatherType`, calls `forceWeatherTick`, and calls `Chant_CalmWeather.xpWorthyChange` (for example `Chant_SummonRain.java:114-166`). Squall, sun and moon leave an effect on the area or a room.

**Difficulty.** Rain, heat, wind and squall all compute `size = numberOfProperIDedRooms / (L + 2*XLEVEL)` and call `proficiencyCheck(mob, -size, auto)`. A bigger area makes the chant harder. Examples: `Chant_SummonHeat.java:107-111`, `Chant_SummonRain.java:107-111`, `Chant_SummonWind.java:92-96`, `Chant_SummonSquall.java:136-140`.

**Transition tables.** Heat (`Chant_SummonHeat.java:120-164`), rain (`Chant_SummonRain.java:120-164`) and wind (`Chant_SummonWind.java:105-149`) use a switch on the current weather. Unlisted weathers are unchanged. The tables are in the Content section below.

**Other weather chants:**
- **Squall** (`Chant_SummonSquall.java:113-164`). It is refused if a squall effect is already on the area or the area is already a thunderstorm (line 127). It applies an effect to the area for `4 + XTIME` ticks (line 149), stores the previous next-weather value in `oldClimate` (line 152), and sets THUNDERSTORM. `unInvoke` restores the stored value (lines 84-96). The storm's own duration depends on `Climate`, which is not traced.
- **Lightning** (`Chant_SummonLightning.java:111-115`). It needs a current thunderstorm. Damage is `roll(M, 8, M/4)` where `M = round((L + 2*X1)/2)` (lines 136-137), halved if saved (lines 138-139).
- **Hail** (`Chant_SummonHail.java:112-118`). It needs winter cold or hail. Damage is `roll(M, 4, 0)` with `M = (L + 2*X1)/2` (lines 139-140). A worn metal, mithril or rock helmet blocks the hit (lines 143-158). Then the area is forced to hail if it is not already (lines 162-169).
- **Sun** (`Chant_SummonSun.java:149-195`). Day or dawn only, and the sun must not already be visible (lines 155-165). The room effect sets the light-source bit and clears the dark bit (lines 105-109). It ends outside dawn and day (lines 118-123).
- **Moon** (`Chant_SummonMoon.java:149-159`). Night or dusk only, and indoors only (line 155). The effect ends outside dusk and night (lines 115-117).

### 6. Water and tide

- **Tidal wave** (`Chant_TidalWave.java:247-525`). The chant needs a watery surface room (line 258) or a boardable target such as a boat or ship. Targets are chosen by `gatherHighestLevels` (lines 89-188), which prefers players over monsters and fails with "You are not permitted to target ..." if `mayIFight` is false (lines 99, 414-416).
  - Direction: `getWaterRoomDir` finds water within one or two steps (lines 190-214). If a boardable area sits in a water room, the direction is random over the four compass directions (`roll(1,4,-1)`, line 375).
  - Harm: each enemy takes `roll(1, L/n, n)` where `n` is the number of enemies (lines 468-469, 489).
  - Washing: an enemy may be washed toward the wash room. The chance to stay is `10 + (DEX - 2*XLEVEL)` (line 498). A roll of 1 never washes (line 500).
  - Save: the chance to fail is the target's `STAT_SAVE_JUSTICE` (which `DefaultCharStats.getSave` returns as the stat plus CHA) plus `sign(diff) * diff^2`, clamped to 5-95 (lines 429-445).
- **Tsunami** (`Chant_Tsunami.java:185-341`). The caster must be on a shore, not indoors or in a boardable area (lines 190-202). The targets are the rooms within two steps, filtered by tracking flags NOAIR, OPENONLY and NOWATER (lines 206-210). Each room gets "A massive Tsunami rushes in, flooding the whole area!" (line 293) and damage as for the tidal wave (line 311). Each room also gets a `Chant_Flood` effect with its tick count halved (line 327). Its atmosphere is set to salt water or the nearby liquid (lines 246-269, 329).
- **Tide moon** (`Chant_TideMoon.java:108-152`). Night only, with the moon visible (lines 110-120). The chant creates or reuses a `Chant_TideMoon` effect on the area and increments its `abilityCode` (lines 135-141). `DefaultTimeClock` then adds the `abilityCode` of every `FLAG_TIDEALTERING` effect on the room and area to the tide index, modulo 7 (`DefaultTimeClock.java:455-485`). The index walks `TidePhase` in the order SPRING_HIGH, SPRING_LOW, NORMAL_HIGH, NORMAL_LOW, NEAP_HIGH, NEAP_LOW, NO_TIDE (`Common/interfaces/TimeClock.java:898-906`). The shift applies even when the base phase is NO_TIDE, so the moon can create tides at other hours (read from the code, not run). A second cast on the same area adds to the offset but does not extend the duration (line 136).
- **Underwater action** (`Chant_UnderwaterAction.java:131-147`). In a water room the target gets the swimming bit, and outside combat also a speed bonus of `CMProps.getSpeedAdjustment()` (lines 137-144). Entering water rooms adds the swimming bit (lines 88-109).

### 7. Status effects on a person or item

- **Treemorph** (`Chant_Treemorph.java`). The target is turned into an oak tree. In `invoke`, a level-difference penalty of `-(levelDiff*10)` is applied to the proficiency check (lines 250-253). The race is set to TreeGolem (line 254). The target's other effects are removed (lines 267-276), and it is made peaceful (line 277). A GenItem "a oak tree" of weight 5000 is placed in the room (lines 279-284, 290). The duration is `5 + L + levelDiff + 10*XLEVEL` ticks (line 286).
  - Actions: `okMessage` refuses any major action with "Trees can't do that." (lines 148-155). Malicious targeting is refused with "You can't do that to X." (lines 166-169).
  - Tether: each tick, if the tree item is in another room than the transformed target, the target is brought to the tree's room (lines 89-98).
  - Name and senses: the name becomes "a TreeGolem called X" or "X the TreeGolem" (lines 192-195). Senses are removed (lines 202-206).
  - `unInvoke` restores HP to 1, mana and movement to 0 and hunger and thirst to 0 (lines 225-231).
- **Treeform** (`Chant_Treeform.java`). The target is rooted in place. Entering, leaving or travelling ends the status (lines 126-129). Hostile attacks are refused with "Attack a tree?!" (lines 140-146). The duration is `(L + 2*XLEVEL) * 50` ticks (line 260). The state saved in `oldState` is restored on end (lines 202-209). The message "To return to your flesh body, try to leave this area." is at line 264.
- **Thorns** (`Chant_Thorns.java`). When an adjacent source's hand or movement action targets the caster, the thorns fire with a chance of `100 - 2*DEX` percent, at most once per tick (lines 113-131). The damage is `roll(1, round(L/3), 1)` pierce (lines 127-128). The target also gets armour -10-2*XLEVEL (line 146).
- **Tether** (`Chant_Tether.java`). The effect records the room the target came from (lines 92-95, 132-135). A death message with no target or tool, while HP is still above 0, is refused and the target is pulled back (lines 97-107). The "Falling" effect is removed each tick (lines 136-140).
- **Unbreakable** (`Chant_Unbreakable.java`). The item's `usesRemaining` is reset to its stored condition on every stat recomputation and tick (lines 86-105). For items that do not wear, the condition is -1 and not reset (lines 161-164). Malicious messages aimed at the item are refused with "... is unbreakable!" (lines 112-119).
- **Treemind** (`Chant_Treemind.java:87-115`). A mind shield. Mind-targeted messages, and messages whose tool is an illusion or enchantment ability, are refused when the proficiency check succeeds (lines 106-109). If the check fails, the message goes through and the source is told "Your tree mind focus fails." (lines 111-112). The shield refuses unconditionally when the target has no entry for the chant in its own ability list (`fetchAbility` returns null, line 108), which is the case for a target that only holds the effect.
- **Sunray** (`Chant_Sunray.java`). Removes sight (`CAN_NOT_SEE`, line 80) for a standard malicious duration (lines 141-142). Refused for targets without eyes (lines 125-129).
- **Tangle** (`Chant_Tangle.java`). Binds the target to the caster's first plant in the room (lines 157-172). The struggle budget starts at `200 * speed` (lines 185-186). Each struggle costs `4 * STR` (line 117). The bind ends when the budget is exhausted or the plant is gone (lines 99-103, 118-120). The duration is `L*10` ticks (line 189).
- **Sunbeam** (`Chant_Sunbeam.java`). Personal light for a non-room target (lines 77-84). It clears the dark bit when the target is in darkness (lines 82-83).
- **Unicorn's health** (`Chant_UnicornsHealth.java:77-82`). Adds Constitution `(target level + 2*XLEVEL(invoker)) / 6`.
- **Suppress fortune** (`Chant_SuppressFortune.java`). Sets the `SUPPRESSOR` stat on the target's `AutoAwards` effect to this effect's ID and forces a tick (lines 137-141). Non-players are given auto-properties first (lines 135-136). Restored on end (lines 93-98). The meaning of "fortune" is the automatic-awards system, which is not traced here.

### 8. Place-bound and sensing chants

- **Tremor sense** (`Chant_TremorSense.java`). The caster must be sitting and not mounted (line 183). A room effect is placed on every room in a radius of `5 + XMAXRANGE/2` except air, underwater, watery and indoors-air rooms (lines 204-221). Each room effect reports footsteps and "ferocious rumble" messages with direction from `radiatesFromDir` (lines 137-166).
- **Sweet scent** (`Chant_SweetScent.java`). Affects one of the caster's plants (line 155). Every third tick (lines 83-85), animals in the same room are told they smell something intoxicating (lines 87-97). Animals in other rooms within range `10 + XLEVEL + 2*XMAXRANGE` (line 181) walk toward the plant if a roll exceeds their MIND save (lines 101-118).
- **Tap grapevine** (`Chant_TapGrapevine.java`). The caster must be in a room with another druid's plant (lines 153-175). The effect is copied onto each of that druid's plants (lines 183-213). Speech heard by a plant is relayed to the listener (lines 87-94).
- **Treehouse** (`Chant_Treehouse.java`). Creates a `WoodRoom` above the caster, joined by a climbable exit. The room has peace, no-recall, no-summon and no-teleport properties (lines 150-185). Adjacent outdoor rooms get impassable exits with room-view properties (lines 187-204). The room is removed on end (lines 83-109). A refusal is given when the room has no room ID (line 132) or when there is already a room above (lines 137-142).

## Content worth mining

### Summon catalogue

| Summon | Minion or effect | Key stats | Lifetime or end |
|---|---|---|---|
| Vine | "a vine", race Vine, combat only | Level rule above; damage 6+5*(L/5) | Timeout after 6 ticks out of combat |
| Sapling | "<wood> sapling", TreeGolem, combat only, forest or jungle or wood | Leveler stats; HP base x2 | Ends at once out of combat |
| Rock golem | "an golem of stone", StoneGolem, cave or rock | Armour +100, attack +100, damage +20, speed /2 | Follows; one per caster |
| Mount | "a wild horse", Horse, 500 weight, one rider | Leveler stats at adjusted level | Follows; ends if caster moves |
| School | Mirror images, shape-shifted fish or sea mammal, underwater | `1d(round(L/3))+2` images | Exhausted, or leaves water |
| Insect swarm | Malicious sting effect on each target | `1d(3+X)` per tick | Target leaves the room |
| Jellyfish | Room effect with a factory swarm MOB | Sting `M.level/10 + X` | Invoker leaves combat |
| Tornado | One-shot storm | Mob damage `(L/2)d7+1` | None |

### Plant catalogue

See the plant families table in section 4. The variant names are short enough to reuse as flavour text, for example "a patch of bluebonnets", "a potted gladiola" and "some kelp".

### Weather transition tables

| From | Heat (`SummonHeat`) | Rain (`SummonRain`) | Wind (`SummonWind`) |
|---|---|---|---|
| Clear | heat wave | rain | windy |
| Cloudy | clear | rain | windy |
| Fog | clear | rain | windy |
| Drought | dust storm | clear | dust storm |
| Dust storm | drought | cloudy | dust storm |
| Hail | rain | sleet | hail |
| Heat wave | drought | rain | dust storm |
| Rain | clear | thunderstorm | thunderstorm |
| Sleet | rain | sleet | sleet |
| Snow | rain | sleet | blizzard |
| Thunderstorm | rain | thunderstorm | thunderstorm |
| Windy | dust storm | thunderstorm | windy |
| Winter cold | clear | snow | windy |
| Blizzard | thunderstorm | blizzard | blizzard |

Hail is forced in `Chant_SummonHail.java:164-166`. Squall forces thunderstorm (`Chant_SummonSquall.java:153`). Lightning needs a thunderstorm and sun and moon need their light conditions.

### Full inventory of the 47 files

Quality: M = malicious, BS = beneficial self, OS = okay self, OO = okay others, I = indifferent. Domain names omit the `DOMAIN_` prefix.

| # | Class | Name | Quality | Domain | Lines | Mechanic |
|---|---|---|---|---|---|---|
| 1 | Chant_TidalWave | Tidal Wave | M | Water control | 526 | Wave from nearest water; washes boats and rooms; justice save |
| 2 | Chant_Tsunami | Tsunami | M | Water control | 342 | Shore only; floods rooms within two steps |
| 3 | Chant_Treemorph | Treemorph | M | Shape shifting | 304 | Target becomes a TreeGolem and oak tree item |
| 4 | Chant_SummonPlants | Summon Plants | I | Plant growth | 296 | Base for stationary plants; ownership and city bonus |
| 5 | Chant_SummonVine | Summon Vine | BS | Plant control | 296 | Combat vine minion; out-of-combat timeout |
| 6 | Chant_SummonSapling | Summon Sapling | BS | Plant control | 291 | Combat sapling warrior |
| 7 | Chant_SummonSchool | Summon School | BS | Animal affinity | 275 | Mirror images while shape-shifted underwater |
| 8 | Chant_SummonTornado | Summon Tornado | M | Weather mastery | 275 | One-shot whirlwind moving items and mobs |
| 9 | Chant_Treeform | Treeform | I | Shape shifting | 275 | Self-rooting status; leaving ends it |
| 10 | Chant_SummonRockGolem | Summon Rock Golem | BS | Rock control | 263 | Cave golem minion; one per caster |
| 11 | Chant_SummonMount | Summon Mount | I | Animal affinity | 249 | Wild horse walked in from adjacent room |
| 12 | Chant_SummonFungus | Summon Fungus | I | Plant growth | 234 | Cave or rock fungus or mushroom; decays on touch |
| 13 | Chant_TremorSense | Tremor Sense | OS | Deep magic | 231 | Sitting; room effects report nearby movement |
| 14 | Chant_TapGrapevine | Tap Grapevine | I | Plant control | 222 | Relays speech heard by another druid's plants |
| 15 | Chant_Treehouse | Treehouse | I | Plant control | 215 | Temporary room above with restricted props |
| 16 | Chant_SummonJellyfish | Summon Jellyfish | M | Animal affinity | 214 | Underwater room sting effect |
| 17 | Chant_Tangle | Tangle | M | Plant control | 202 | Binds target; struggle budget |
| 18 | Chant_SummonInsects | Summon Insects | M | Animal affinity | 197 | Sting each tick on each affected target |
| 19 | Chant_SummonSun | Summon Sun | I | Weather mastery | 196 | Light source; day only |
| 20 | Chant_SweetScent | Sweet Scent | I | Plant control | 194 | Lures animals toward own plant |
| 21 | Chant_SummonHouseplant | Summon Houseplant | I | Plant growth | 192 | Indoor potted plant; decays on touch |
| 22 | Chant_SummonMoon | Summon Moon | I | Moon summoning | 190 | Indoor light source; night only |
| 23 | Chant_Thorns | Thorns | BS | Shape shifting | 184 | Armour penalty; counter-damage on melee |
| 24 | Chant_SummonHail | Summon Hail | M | Weather mastery | 178 | Hail damage; helmet protection; forces hail |
| 25 | Chant_Tether | Tether | BS | Preserving | 178 | Pulls back from non-lethal death; cancels Falling |
| 26 | Chant_Unbreakable | Unbreakable | I | Preserving | 177 | Holds item condition; blocks malicious targeting |
| 27 | Chant_UnderwaterAction | Underwater Action | OO | Water control | 175 | Swimming; speed bonus out of combat |
| 28 | Chant_SummonHeat | Summon Heat | I | Weather mastery | 174 | Weather transition (hotter) |
| 29 | Chant_SummonRain | Summon Rain | I | Weather mastery | 174 | Weather transition (wetter) |
| 30 | Chant_SummonSeed | Summon Seeds | I | Plant growth | 170 | Seeds of a chosen material; `list` is free |
| 31 | Chant_SummonTree | Summon Tree | I | Plant growth | 169 | Tree that rewrites room resource |
| 32 | Chant_SummonSquall | Summon Squall | I | Weather mastery | 165 | Area storm; restores previous next-weather |
| 33 | Chant_SummonWind | Summon Wind | I | Weather mastery | 159 | Weather transition (windier) |
| 34 | Chant_SummonSeaweed | Summon Seaweed | I | Plant growth | 158 | Aquatic plant in water rooms |
| 35 | Chant_SuppressFortune | Suppress Fortune | OO | Cosmology | 158 | Suppresses target's AutoAwards |
| 36 | Chant_TideMoon | Tide Moon | I | Moon altering | 153 | Area tide offset by cast count |
| 37 | Chant_Sunray | Sunray | M | Weather mastery | 151 | Blinds target |
| 38 | Chant_SummonLightning | Summon Lightning | M | Weather mastery | 150 | Bolt during thunderstorm |
| 39 | Chant_Treemind | Treemind | BS | Preserving | 150 | Mind and illusion or enchantment shield |
| 40 | Chant_SummonWater | Summon Water | I | Weather mastery | 149 | `Spring` item for the duration |
| 41 | Chant_SummonPool | Summon Pool | I | Weather mastery | 145 | Cave-only magical pool |
| 42 | Chant_Sunbeam | Sunbeam | OS | Weather mastery | 143 | Personal light that follows the caster |
| 43 | Chant_UnicornsHealth | Unicorns Health | BS | Preserving | 131 | Constitution bonus |
| 44 | Chant_SummonFood | Summon Food | I | Plant growth | 129 | Berries; nourishment by X1 |
| 45 | Chant_SummonIvy | Summon Ivy | I | Plant growth | 128 | Poison ivy, sumac or oak |
| 46 | Chant_SummonHerb | Summon Herbs | I | Plant growth | 125 | Herbs; nourishment 1 |
| 47 | Chant_SummonPeace | Summon Peace | I | Preserving | 114 | Calms all combatants in the room |

### Phrases worth reusing

These short lines are game text and need no paraphrase: "Trees can't do that." (`Chant_Treemorph.java:153`), "Attack a tree?!" (`Chant_Treeform.java:142`), "The tether keeps you from falling!" (`Chant_Tether.java:138`), "Your tree mind focus fails." (`Chant_Treemind.java:111`), "The moon begins pushing and pulling on the tides in new ways!" (`Chant_TideMoon.java:140`), "The Sun pierces into the room!" (`Chant_SummonSun.java:186`), and "The tornado dissipates harmlessly." (`Chant_SummonTornado.java:184`).

## Notable design ideas

- **Lifecycle rules, not timers.** Minions end because of a predicate that is checked every tick: following, alive, same room, in combat. The predicate changes by summon. Vines tolerate six quiet ticks, saplings do not, and golems end only when unfollowed (`Chant_SummonVine.java:105-119`, `Chant_SummonSapling.java:105-109`).
- **Plants as objects with an owner token.** A plant is a room item. Its owner is a secret identity plus an effect that carries the caster as invoker, so ownership can be tested from the item alone (`com/planet_ink/coffee_mud/Abilities/Druid/Druid_MyPlants.java:99-116`). Owning the land changes the plant from a timed object to a lasting one (`Chant_SummonPlants.java:162-169`).
- **Weather as a shared state machine.** Each chant only nudges `nextWeatherType` and forces a weather tick. No chant needs to know the weather rules, and the squall restores the previous next-weather when it ends (`Chant_SummonSquall.java:84-96`).
- **Tide as a stack of offsets.** Each tide-altering effect adds its code to the index, modulo the phase count (`DefaultTimeClock.java:471-485`). Several effects stack without any special case.
- **Echoed actions.** The school copies the caster's visible actions as trailer messages, once per image (`Chant_SummonSchool.java:137-156`). The effect is a mirror, not a counter.
- **Plant eavesdropping.** Tap grapevine copies its own effect onto another druid's plants, and each plant relays speech (`Chant_TapGrapevine.java:183-213`, `87-94`). Objects in the world become a network.
- **Touch makes decay.** Touching a fungus or houseplant starts a decay behaviour (`Chant_SummonFungus.java:78-100`). The object changes role on interaction.
- **Dynamic geometry.** The treehouse adds a real room with exits and properties, and removes it on end (`Chant_Treehouse.java:150-209`, `83-109`).
- **Struggle budget.** The tangle escape is a budget of `200 * speed`, spent at `4 * STR` per attempt. It is simple to tune and easy to explain (`Chant_Tangle.java:117`, `185-186`).
- **Armour as a magic restriction.** A player in metal armour has roughly a one-in-two chance per cast of having the chant absorbed (`Chant.java:167-189`).

## Problems and cruft

Verified by reading the code:

1. **Biased mirror roll.** `Chant_SummonSchool.java:105` tests `randomizer.nextInt() % numberOfTargets >= intAdjustment`. Java's `%` keeps the sign of the dividend, so about half of all rolls (negative `nextInt()` values) can never reach the threshold. The diversion rate is about half the intended rate. The `Random` is also a per-instance field seeded from the clock (line 77).
2. **Ineffective INT floor.** `Chant_SummonSchool.java:256-257` sets INT to at least 5 on the working stats. Both branches then call `recoverCharStats()` (lines 260 and 265), which copies the base stats over the working set (`MOBS/StdMOB.java:926-929`). The floor is discarded.
3. **Swapped arguments in the rock golem.** `Chant_SummonRockGolem.java:195` calls `beneficialAffect(mob, target, 0, asLevel)`. The third parameter is `asLevel` and the fourth is the tick adjustment (`StdAbility.java:2341`). Every other summon passes `(asLevel, 0)`. With a non-zero `asLevel`, the golem lasts `asLevel` ticks, or a percentage of the default if negative (`StdAbility.java:2312-2331`).
4. **Tree check mostly passes.** `Chant_Treehouse.java:120-121` is `(domain==WOODS)||(domain!=JUNGLE)`. That is true for every room except jungle. The "not enough trees" refusal at line 127 therefore only fires in a jungle room with no wood plant and no wooden resource. The check was probably meant to admit woods and jungle.
5. **Dead tornado guard.** `Chant_SummonTornado.java:138-144` refuses if the room already has an effect with this ID. `invoke` never applies one, so the check cannot fire, and the tornado has no duration.
6. **Transformed targets reset every message.** `Chant_Treemorph.java:138-141` and `Chant_Treeform.java:108-111` reset HP, hunger and thirst to maximum, and recompute stats, on every `okMessage` for the affected mob. Damage is applied after `okMessage`, so the reset runs again on the next message and may restore HP during or after a fight. The net effect on death and combat was not traced, so it is unverified.
7. **Instance state for the tree form.** `tree` and `treeForm` are instance fields (`Chant_Treemorph.java:78-79`) set only in `invoke` (line 254). `affectCharStats` and `affectPhyStats` read them (lines 108, 190). An effect object recreated without `invoke` would lose the race change. Whether this effect is ever recreated that way is unverified.
8. **Duplicated code.** `getWaterRoomDir` (`Chant_TidalWave.java:190-214` and `Chant_Tsunami.java:147-171`), the highest-level search (`Chant_TidalWave.java:89-188` and `Chant_Tsunami.java:89-145`) and the justice-save block (`Chant_TidalWave.java:429-445` and `Chant_Tsunami.java:222-238`) are copied. The spring code in `Chant_SummonWater.java` and `Chant_SummonPool.java` is nearly identical.
9. **Dead code.** `Chant_TidalWave.getWashRoom` (lines 227-245) has no callers in the codebase. `Chant_SummonSeaweed.seaOk()` (line 75) has no callers. `Chant_Treemind.amountAbsorbed` (lines 70, 140) is set and never read.
10. **Always-true guard.** `Chant_TidalWave.java:430` and `Chant_Tsunami.java:223` test `chanceToFail > Integer.MIN_VALUE`, which is always true.
11. **Unused parameter.** `Chant_SummonVine.determineMonster` takes `material` (line 243), which is never used. The caller passes HEMP (line 213).
12. **Unbounded static map.** `Chant_SummonPlants.plantBonuses` (line 77) is a static `Hashtable` keyed by "caster/area" strings (lines 206-211). Each key is replaced in place, but keys for other caster and area pairs are never pruned.
13. **Copy-paste and grammar.** "an golem of stone" and "an stone golem lumbers around here." (`Chant_SummonRockGolem.java:213-214`). "a potted sunflowers is here." (`Chant_SummonHouseplant.java:145`). The school says "You must be in your animal form to call a mate." (`Chant_SummonSchool.java:216`), which looks copied from a mating chant. A double space appears in "a yinmingella  fungus" (`Chant_SummonFungus.java:154`).
14. **Null hazard in Sunbeam.** `Chant_Sunbeam.unInvoke` takes the room from the affected mob (line 93) and calls `room.recoverRoomStats()` without a null check (line 97).
15. **Odd damage type.** Tornado damage to mobs is TYP_GAS (`Chant_SummonTornado.java:262`).

Multiplayer-only assumptions (relevant to a single-player SUD):

- PvP gates: `mayIFight` and `canAttackThisProperty` decide who can be hit (`Chant_TidalWave.java:277-281`, `318-322`; `Chant_Tsunami.java:96`, `285`).
- Group membership: group members are skipped as targets, using `getGroupMembers` (`Chant_TidalWave.java:171`, `Chant_Tsunami.java:137`, `Chant_Treemorph.java:258`).
- Cross-player spying: tap grapevine reads another player's plants (`Chant_TapGrapevine.java:153-163`).
- Name-based ownership: plants belong to a name (`com/planet_ink/coffee_mud/Abilities/Druid/Druid_MyPlants.java:99-103`). Unverified, but a name change or duplicate name would break ownership.
- Player-first targeting: the tidal wave prefers players over monsters (`Chant_TidalWave.java:106`, `114-116`).

## Takeaways for a Python SUD

- **Weather as data.** Port the transition tables from `Chant_SummonRain.java:120-164`, `Chant_SummonHeat.java:120-164` and `Chant_SummonWind.java:105-149` as dictionaries. Give each area a `next_weather` field and a `nudge(table)` method. Keep the squall-style restore so a temporary storm can end cleanly.
- **Summons as rows.** Define a `Summon` record with a name, a race or template, a level formula, stat multipliers and a lifetime predicate. For example, `alive = follows(owner) and not dead and same_room(owner) and (not needs_combat or owner.in_combat)`. Evaluate it each turn. Vines tolerate six quiet turns; saplings do not.
- **Plants as world objects.** Use `Plant(name, owner_id, room_id, kind, decay_turn)`. Ownership is `plant.owner_id == pc.id`, which is simpler than CoffeeMud's effect token. Keep the decay-on-touch rule as an event handler on the object.
- **Durations in game time.** The 4-second tick and the `L*4+40` formula become turn counts or in-game minutes. Keep the caster-level scaling and the explicit override.
- **Proficiency roll.** Port `proficiencyCheck` as a single chance, clamped to 5-95, with an optional penalty that adds a pre-roll auto-fail. Keep the XLEVEL-style bonus as a separate expertise value.
- **Dice.** Port `roll(n, d, mod)` with the `d <= 0` guard. Use it as `roll(1, round(L/3), 1)` for the thorns damage and similar formulas.
- **Tide as an offset stack.** Store an integer offset on each area. The current tide is `TIDE[(base + sum(offsets)) % 7]`, using the `TidePhase` order.
- **Status effects with a message gate.** The treemorph's "cannot act except sleep" pattern is a pre-action hook. The SUD can keep a `status` object that vetoes actions with a message, and an `after` hook that adds trailing text, as the school does. This is cheap to write and clear to read.
- **Tether and rooting as statuses.** The treeform's leave-to-end behaviour and the tether's pull-back are simple location checks, and they fit a single-player text game well.
- **Drop for single-player.** Tap grapevine (cross-player), the PvP and group gates, the player-first targeting, and the name-based plant identity. Consider merging the 47 chants into about eight effect kinds: summon, plant, weather nudge, room effect, status, item effect, shape change and tide.
- **Fix before copying.** The mirror roll, the INT floor, the rock golem's argument order, and the tree check. Use shared helpers instead of the duplicated water and justice code.

## Coverage

- **Read in full (body, from the class declaration to the end):** all 47 files in the bucket. The licence headers and import blocks were skipped as boilerplate.
- **Read in full, outside the bucket:** `com/planet_ink/coffee_mud/Abilities/Druid/Chant.java` (201 lines).
- **Read in part, outside the bucket:**
  - `com/planet_ink/coffee_mud/Abilities/StdAbility.java`: lines 419-467, 899-912, 1441-1487, 2174-2185 and 2312-2346.
  - `com/planet_ink/coffee_mud/Abilities/Druid/Druid_MyPlants.java`: lines 60-170 and 99-116.
  - `com/planet_ink/coffee_mud/Common/DefaultTimeClock.java`: lines 455-490.
  - `com/planet_ink/coffee_mud/Common/interfaces/TimeClock.java`: lines 898-906 (`TidePhase`).
  - `com/planet_ink/coffee_mud/Common/DefaultCharStats.java`: lines 780-830 (`getSave`).
  - `com/planet_ink/coffee_mud/MOBS/StdMOB.java`: lines 926-940 (`recoverCharStats`).
  - `com/planet_ink/coffee_mud/Libraries/Dice.java`: lines 398-412.
  - Grep only: `core/CMProps.java:681`, `Climate.java:161-191`, `Ability.java:363-368, 651-653`, `Directions.java` constants, `Items/Basic/Spring.java` existence, `Behaviors/Decay.java` existence.
- **Not read:** `Chant_Flood.java` (called by `Chant_Tsunami.java:318-332`), `Chant_CalmWeather.xpWorthyChange`, `Druid_ShapeShift.java` (except `isShapeShifted`), the `Decay` behaviour, `Spring`, `Climate.java` body, the Druidic connection chant, and the other plant summons outside this bucket (`GrowOak`, `SummonCoral`, `SummonFlower`).
- **Unverified:** combat outcomes of the transformed targets (issue 6), whether the tree effect persists across reboots (issue 7), and the actual storm duration after a squall, which depends on `Climate`.
