# Druid chants, part 2 (Control Weather to Find Driftwood)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b052` (Abilities/Druid [part 2/7: Chant_ControlWeather .. Chant_FindDriftwood]) · 49 files · 9578 lines of Java.

Citation convention: `path:line` is relative to the Java source root `com/planet_ink/coffee_mud/`. Where the text says `Chant_X.java:N`, the file is `Abilities/Druid/Chant_X.java`, line N. The base class is `Abilities/Druid/Chant.java`.

## Summary

The 49 chants in this slice are the Druid's verbal spells. Each is a subclass of the `Chant` base class (`Abilities/Druid/Chant.java:36`), which in turn extends `StdAbility`. They cover weather and nature (ControlWeather, FavorableWinds, Earthquake, EelShock, and the Feel series of cold, heat and electricity attacks), room and area effects (DruidicConnection, DeathMoon, Den, Earthpocket, DeepDarkness), and a large crafting family in which the chant itself endows, enhances or crafts an item (EndowGemstones, EndowIounStone, EndowJewelry, EnchantShards, CrystalGrowth). The rest are buffs, curses, and tracking or remote-building utilities (CurseSeed, CurseFortune, FindDriftwood, Farsight, the Distant* family). The most borrowable mechanics are items that hold other chants as data, an area "territory" gated by how much of it the druid has planted, and reversible effects that remember an item's original state. The slice also contains real defects, notably the CurseSeed inheritance count, the Den teardown and an unguarded cast in EndowJewelry.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Druid/Chant.java` | Base class: IDs, trigger words, weather bitmask, alignment check (not in this bucket; read in full for context) | 201 |
| `Abilities/Druid/Chant_EndowGemstones.java` | Recipe-driven endowment of a cut gem with a zapper-mask wear adjuster | 391 |
| `Abilities/Druid/Chant_EndowIounStone.java` | Ioun-stone crafting by delegating to a `GenCraftSkill`; room-sweep trick | 333 |
| `Abilities/Druid/Chant_CurseSeed.java` | Hereditary curse; overwrites a pregnancy effect's gender field | 264 |
| `Abilities/Druid/Chant_DruidicConnection.java` | Area "territory" effect gated by planted-plant coverage, with daily XP | 253 |
| `Abilities/Druid/Chant_FindDriftwood.java` | Spawns a driftwood item within a radius and tracks it; extends `Chant_FindPlant` | 252 |
| `Abilities/Druid/Chant_DeepThoughts.java` | Cave meditation that drifts alignment and inclination | 244 |
| `Abilities/Druid/Chant_DeathMoon.java` | Room effect that damages inhabitants each tick while the moon is visible | 235 |
| `Abilities/Druid/Chant_Earthpocket.java` | Weightless, unlimited container that lives in cave walls and follows the caster | 232 |
| `Abilities/Druid/Chant_FavorableWinds.java` | Ship speed buff | 229 |
| `Abilities/Druid/Chant_EndowJewelry.java` | Endows another known chant onto a piece of jewelry | 228 |
| `Abilities/Druid/Chant_FilterWater.java` | Reversible purification of liquids and containers | 228 |
| `Abilities/Druid/Chant_Earthquake.java` | Group knockdown with a sit-lock effect | 226 |
| `Abilities/Druid/Chant_FindPlant.java` (other bucket) | Tracking base class for FindDriftwood; only its tick and executeMsg (`:146-200`) were read | n/a |

## How it works

### 1. Shared rules (Chant.java)

- **Identity and triggers.** `ID()` is `"Chant"` and the trigger words are `CHANT` and `CH` (`Chant.java:41`, `:84`). Concrete chants override `ID()`, `classificationCode()`, `abstractQuality()`, `canAffectCode()` and `canTargetCode()`. The base defaults are `CAN_MOBS` for both affect and target (`Chant.java:101`, `:107`).
- **Classification.** The default is `ACODE_CHANT`. If the ability is given the misc text `render mundane` it becomes `ACODE_SKILL` (`Chant.java:113-115`, `:122`). A rendered-mundane chant is treated as spoken noise rather than a verbal cast (`Chant.java:71-80`).
- **Weather bitmask.** `WEATHERQUE_WIND=1`, `RAIN=2`, `HOT=4`, `COLD=8`, `CALM=16` (`Chant.java:61-66`). `weatherQue(Room)` returns the set for the room's climate: nothing indoors (`:130-131`), nothing in blizzard, thunderstorm or heat wave (`:136-139`), all four active flags in clear weather (`:141`), and so on through `default` returning CALM (`:162-163`). The consumers of this method are outside this slice (unverified).
- **Alignment check.** Before a non-auto cast by a player (not a monster, not disregarding armour), there is a 50% roll (`Chant.java:169-174`). On a hit, the cast fails if the caster is not appropriate to their factions: "Extreme emotions disrupt your chant." (`:178`). Otherwise it fails if the armour absorbs the magic: `CharClass.ARMOR_NONMETAL` (`:182`), shown as "<S-NAME> watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (`:184`). The check runs after `StdAbility.invoke` succeeds (`Chant.java:191-200`).

### 2. The Endow family: chants that make items magical

Common pattern. The cost is paid in mana (`overrideMana()` returns `COST_ALL` in most of these, e.g. `Chant_EndowGemstones.java:189`) and in experience. Experience is posted with `CMLib.leveler().postExperience`, which returns the amount actually posted, or 0 when XP is disabled or the race or class is expless (`Libraries/CoffeeLevels.java:1085-1105`). In these chants XP is charged after the mana and proficiency roll, so a failed attempt still costs XP (for example `Chant_EndowGemstones.java:382-386`).

**Gemstones** (`Chant_EndowGemstones.java`). Recipe-driven. Each recipe row has the format `ITEM_NAME, ITEM_LEVEL, ADJUSTER_ARGS, XLEVEL, EXPERTISE` (`:117`), read from `endowgemstones.txt` (`:129`).
- The target must be an `Item` of `MATERIAL_PRECIOUS` (`:264-266`) that carries a `Prop_ItemSlotFiller` whose text contains "gemsetting" or "jewelrycharm" (`:270-273`). It must not already be endowed (`:278`).
- The gem's level must reach the recipe's level (`:326`). An expertise may be required (`:335-342`).
- XP cost is `490 + 10 * XLEVEL` (`:198-200`), and the caster must have it (`:344-349`).
- On success the gem gets the `IS_BONUS` disposition (`:372`) and a `Prop_WearAdjuster` whose misc text is the recipe's mask (`:374-376`). The mask uses the zapper language (`Libraries/MUDZapper.java`), so the gem's effect applies only to qualifying wearers.
- On failure: "chant(s) to <T-NAMESELF>, but nothing happens." (`:386`).

**Ioun stones** (`Chant_EndowIounStone.java`). The class implements `ItemCraftor` but delegates almost everything to a `GenCraftSkill` built in code (`:73-96`). The settings are `CLASS9` set to its own ID, `TRIGSTR` set to `CHANT "Endow Ioun Stones"`, `FILENAME` set to `endowiounstone.txt`, `MATLIST` set to `PRECIOUS`, `CANMEND` true, `CANREFIT` false, `CANBUNDLE` false and `NOHARDBONUS` true (`:78-91`). The subcommands list, info, mend, refit and stop are routed to the crafting skill (`:227-248`).
- Cost: `1100 + 50 * lvl`, where `lvl` is the level of the item the recipe would make (`:285-287`). The caster must hold that much XP (`:288-291`).
- The room-sweep trick (`:293-303`). Every other precious-material raw item in the room is removed from the room, the target stone is put in its place (`:310`), and the craft runs (`:320`). In `finally` (`:322-331`) the stone goes back to the caster and the removed items are re-added with their expiration dates restored. The generic craft code can therefore only see the chosen stone.

**Jewelry** (`Chant_EndowJewelry.java`) and **club** (`Chant_EndowClub.java`). These endow a chant the caster already knows, not a recipe.
- The chant to endow is matched from the caster's own ACODE_CHANT abilities (`Chant_EndowJewelry.java` invoke, `:123-135`). It may not be summoning (`FLAG_SUMMONING`, `:143`) or a room-targeting chant (`canAffect(CAN_ROOMS)`, `:144`). It must be `mayBeEnchanted()` (`:150`).
- Jewelry must be worn only on the ears, fingers, neck or wrists. A "good" bitmask must match (`:103-109`), and a "bad" bitmask of torso, arms, feet, about-body, hands and head must not (`:111-115`).
- XP: `1000 + 100 * lowestQualifyingLevel(chant)` (`:162-163`). The item's level rises by half that value (`:193`).
- Effect: `Prop_WearSpellCast` with misc text `LAYERED;<chantID>;` (`:197-211`), so the item casts the stored chant while worn.
- The club version requires a `Weapon` of `CLASS_BLUNT` (`Chant_EndowClub.java:99`) made of wood or vegetation (`:100-101`). It adds `Prop_FightSpellCast` with misc text `25%;MAXTICKS=12;<chantID>;` (`:183-184`). The meaning of the `25%` and `MAXTICKS` fields is in `Prop_FightSpellCast` (unverified here).

**Shards and enhancers.**
- EnchantShards (`Chant_EnchantShards.java`) needs a `Wand` whose enchant type is -1 or ACODE_CHANT (`:98-104`). It costs `10 * lowestQualifyingLevel` (`:156`), copies the chosen spell into the wand and raises the wand's level to the spell's adjusted level (`:190-192`).
- EnhanceJewelry (`Chant_EnhanceJewelry.java`) requires an `Armor` item (`:90`), raises its `ability()` by one, and stops at `5 + XLEVEL/2` (`:110`). It costs 50 XP (`:119`).
- EnhanceShard (`Chant_EnhanceShard.java`) raises a wand's level and ability, capped at the smaller of the caster's level and the adjusted level plus XLEVEL (`:107-108`). It costs 50 XP (`:117`) and mana 100 (`overrideMana`).
- EnhancePotion (`Chant_EnhancePotion.java`) raises a potion's level by a chosen `levels` value (`:147-158`). The potion must hold magical spells (`:131`). Mana is `50 + 5 * (levels-30)` above 30 levels and 50 otherwise (`:85-89`).

**Crystal growth** (`Chant_CrystalGrowth.java`). Cave only. Mana cost 50 (`:70-72`). It rolls a d10 (`:108`): 1-4 Blacksmithing, 5-7 Armorsmithing, 8-10 Weaponsmithing. It tries up to 100 times (`:104`) and rejects any item above the caster's level (`:132`). The output is a random crystal item.

### 3. Area and room effects

**DruidicConnection** (`Chant_DruidicConnection.java`). An area-wide territory effect.
- Requirements: the area must have more than 10 visitable rooms before the percentage applies (`:197-198`). At least 50% of them must contain the druid's own planted items (`:199`, counted by `Druid_MyPlants.myAreaPlantRooms`, `:195`). Only one druid connection per area (`:189`), druids only unless auto (`:205`), and not in a city unless auto (`:212`).
- The effect is attached to the area (`:230-239`) and also added to the caster's effect list (`:238`), so the caster's tick drives it. The caster-side link is removed in `unInvoke` (`:172-175`).
- Each in-game day (`:100-102`), `tick` rechecks coverage. Below 50% the connection ends (`:109-113`). Otherwise XP is `round(5 * coverage * averageAreaLevel)` (`:115-117`).
- Stats: for the druid, for animals, and for Vegetation-race mobs in the area (`:130-133`), attack adjustment is multiplied by `1.1 + 0.05*XLEVEL` (`:134`) and armour is reduced by `2*level + 2*XLEVEL` (`:135`).
- Teardown destroys the druid's plants in the area, keeping those in the first listed room (`:147-161`), and recalculates the rooms (`:164-169`).

**DeathMoon** (`Chant_DeathMoon.java`). A room effect that turns the moon against the room.
- Preconditions: the moon must be visible, `Climate.canSeeTheMoon` (`:181`). The room must not already have this effect (`:186`) or any other moon-domain effect (`:191-200`).
- Duration is 10 ticks (`:226`). The brief gives the default tick as 4 seconds, so this is about 40 seconds.
- Each tick, every inhabitant the invoker may fight and who is neither in the caster's group nor following the caster (`:127-142`) takes `roll(1, level + 2*XLEVEL, 0) / divider` damage (`:143`). The divider is `numInhabitants - 1`, minimum 1 (`:123`). The damage type is undead and bursting (`:144`), and a revenge attack follows (`:145`).
- `affectPhyStats` sets `CAN_NOT_SPEAK` and `CAN_NOT_HEAR` on the affected object (`:103-104`). Whether this reaches room occupants is unverified.
- Casting quality is INDIFFERENT if the moon is hidden or another moon effect exists (`:161-168`).

**Earthpocket** (`Chant_Earthpocket.java`). A pocket dimension in the walls.
- The pocket is a `GenContainer` with unlimited capacity, zero weight, and the senses `SENSE_ITEMNOTGET` and `SENSE_ALWAYSCOMPRESSED` (`:212-216`). It is placed in the caster's room (`:221`) and lasts one mud month (`:222`, `CMProps.Int.TICKSPERMUDMONTH`).
- `movePocket()` (`:132-178`) moves the pocket with the caster through cave or rock rooms. Elsewhere it is set IS_NOT_SEEN and stays put (`:147-168`).
- `okMessage` blocks anyone other than the caster from using hands, moving, delicate or mouth actions on the pocket: "The dark pocket draws away from you, preventing your action." (`:103-111`).
- `unInvoke` empties the pocket into the caster's inventory (`:86-96`). The displayed name shows days remaining (`:54`).

**Den** (`Chant_Den.java`). A temporary cave room built next to the caster.
- The direction is chosen at random from the directions that have no room yet (`:135-146`).
- A non-savable `CaveRoom` named "A musty den" is created (`:161-166`). It is linked through a `HiddenWalkway` exit (`:168-171`).
- The new room gets `Prop_PeaceMaker`, `Prop_NoRecall`, `Prop_NoSummon`, `Prop_NoTeleport` and `Prop_NoTeleportOut` (`:176-190`). The exit gets `Prop_RoomView`, which points back to the origin room (`:172-174`).
- Duration is one mud month, attached to the origin room (`:194`). The teardown has a defect (see Problems).

**DeepDarkness** (`Chant_DeepDarkness.java`). A room effect at the standard duration (`:153`). It refuses if one is already present (`:137-138`).

**Fertilization** (`Chant_Fertilization.java`). A room fertility effect.
- Not allowed in indoor rooms, air, cities, underwater or watery surface (`:160-169` range).
- Duration is `qualClassLevel * ticksPerMudDay` (`:188`, `:198`), where `ticksPerMudDay = millisPerMudHour * hoursInDay / tickMillis`.
- Each tick, mobs in the room with Farming, Foraging, Gardening, Shrooming or Master variants of these get `setAbilityCode(3)` (`:120`), a skill bonus. The room's vegetation resource is remembered (`:98`) and restored on unInvoke (`:85`).

**FertileCavern** (`Chant_FertileCavern.java`). A cave-only room effect (`:156`). If the caster owns the property (`CMLib.law().doesOwnThisProperty`, `:176`), the effect is stored as a non-uninvokable copy in the room and saved to the database (`:178-179`). Otherwise it is a normal standard-duration effect (`:182`).

**ControlWeather** (`Chant_ControlWeather.java`). An area-level weather effect.
- Outdoors only unless auto (`:123`).
- An existing enchantment of code 1 ("climactic balance") blocks control for its remaining time (`:131-136`).
- The size of the area is `numberOfProperIDedRooms` (`:138`), divided by `level + XLEVEL` (`:139`). If another druid holds control, the penalty adds `(their level - (your level + XLEVEL)) * 10` (`:143`). This is applied as a proficiency modifier, `proficiencyCheck(mob, -size, auto)`.
- On success the area gets `beneficialAffect(..., 0)` (`:155`). Messages: "The sky changes color as the weather comes under control!" (auto) and "chant(s) into the sky for control of the weather!" (`:147`), and "You successfully wrest control of the weather from @x1." when overriding another druid (`:152`).

### 4. Weather, stun, knockdown and elemental attacks

**Earthquake** (`Chant_Earthquake.java`). Group knockdown.
- Range 3 (`:74`). Targets are chosen by `properTargets` (`:173`). With none: "There doesn't appear to be anyone here worth shaking up." (`:176`).
- A thunderous message plus an MSP sound cue (`:188-189`).
- Flying targets are unaffected (`:196-197`). Each other target must pass the okMessage (`:199`) and must not already carry the effect (`:199`).
- If the message's value is at most zero (`:202`) and the target has legs (`:204`), `maliciousAffect(..., 3, -1)` is applied with an intended duration of 3 ticks (`:206`; the malicious duration rule is unverified, see section 7). If the target is in the caster's room, the caster deals 20 damage, "The ground underneath <T-NAME> shakes as <T-NAME> fall(s) to the ground!!" (`:209-210`). Legless targets are "unaffected by the quake" (`:214`).
- The effect sets IS_SITTING (`:106`). Its okMessage blocks TYP_STAND, with the message "attempt(s) to stand up, and falls back down!" shown once per throttle window (`:116-125`). `tick` resets the throttle (`:92-96`). `unInvoke` posts a stand (`:143-147`).

**EelShock** (`Chant_EelShock.java`). Stun.
- Needs a wet room: watery, swamp, rain or thunderstorm (`roomWet`, `castingQuality`). Otherwise "It's too dry to invoke this chant." (`:156-161`).
- Range 0 to 3 (`:66-72`). Each target gets `maliciousAffect(..., 3 + XLEVEL + 2*X1, -1)` (`:203`). The effect sets IS_SITTING and blocks all source actions except those with the ALWAYS mask, with "You are stunned." (`affectPhyStats`, `okMessage`).

**Drown** (`Chant_Drown.java`). Water-breathing removal.
- `affectCharStats` removes saltwater and freshwater from the breathable list. If none remain it adds air (`:84-100`).
- `okMessage` blocks movement with the Skill_Swim ability (`Chant_Drown.java` okMessage).
- Duration 6 ticks (`:197`). `tick` ends the effect when the room is no longer watery.
- Casting quality is INDIFFERENT in combat (`:160`).

**Drifting** (`Chant_Drifting.java`). Flight.
- `affectPhyStats` sets IS_FLYING unless the caster is already falling (`Chant_Drifting.java` affectPhyStats).
- `tick` (once per mob tick, outdoors) reverses the "Falling" ability (`:133-135`), keeping the caster aloft.
- `okMessage` blocks advancing or retreating ("You can't seem to drift accurately enough to advance or retreat!") and blocks up or down moves into air rooms ("You can not seem to direct your flying that way.").

**Feel series** (`Chant_FeelCold.java`, `Chant_FeelHeat.java`, `Chant_FeelElectricity.java`). Three elemental curses.
- Each applies a save penalty of 100 to its element: cold (`Chant_FeelCold.java:120`), fire (`Chant_FeelHeat.java:162`), electric (`Chant_FeelElectricity.java:87`).
- Each applies `maliciousAffect(..., 0, -1)` at the standard duration (`Chant_FeelCold.java:195`, `Chant_FeelHeat.java:186`, `Chant_FeelElectricity.java:168`).
- The okMessage increases incoming damage of the same element by `0.5 + 0.05*XLEVEL` (`Chant_FeelCold.java:110`). The variable is named `recovery` but it adds to the damage.
- Each tick, weather decides the damage. In `Chant_FeelCold.java:140-164` the values are 1 point (cold wind, winter cold), `1d8` (blistering snow, biting hail) and `1d16` (blizzard). Each roll only applies if `rollPercentage() > save`. Heat uses `1d8`, `1d16` and `1d8` (`Chant_FeelHeat.java:119-134`), and electricity uses `1d5` (`Chant_FeelElectricity.java:106`). The exact weather conditions for heat and electricity were skimmed, not verified.

**FavorableWinds** (`Chant_FavorableWinds.java`). Ship speed.
- Only on a `NavigableItem` ship (`:154`). The ship is found in the caster's room, or in the ship's room if the target is elsewhere (`:167-188`).
- Strength is stored in the ability code: `amt = 1 + XLEVEL/3` (`:215-217`). The ability code is also the misc text (`:92-104`).
- `affectPhyStats` adds the code to the ship's `ability()` stat (`:146`). Whether ship movement reads that stat is unverified.
- Duration is standard (`:212`). Messages: "Favorable winds begin to blow!" (`:218`), and "The favorable winds die down." on unInvoke (`:121`, `:132`).

### 5. Buffs and curses (self and ally)

- **CurseSeed** (`Chant_CurseSeed.java`). A hereditary curse on a target (see section 6 for the detail of the inheritance). Success requires no existing Pregnancy effect on the target unless auto (`:217`). Its effect overwrites the baby-gender field of a Pregnancy effect when a social between two characters matches one of two named social commands (`:184-202`, `pollute` at `:105-120`). Conflicting genders set the value to N (`:233-236`). Inherited copies have their tick count halved if the invoker differs (`:237-238`). Duration is hours * days * ticksPerMudHour, plus a level term (`:243-244`).
- **CurseFortune** (`Chant_CurseFortune.java`). Saving throws are reduced by `100 + 10*XLEVEL` (`:102`). The effect intercepts up to four hostile actions aimed at the target (`prots=4`, `:72`, `:114`, `:129`). It also blocks repeated wand use. Each source can trip it only once, tracked in `permProts`.
- **CurseMood** (`Chant_CurseMood.java`). Applies a `Mood` effect (`CMClass.getAbility("Mood")`, `:112`, `:163`). The mood name comes from the command, defaulting to random (`:112-150`). Duration is `hoursPerDay * ticksPerMudHour + ticksPerMudHour * adjustedLevel` (`:169-171`).
- **DelayPoison** (`Chant_DelayPoison.java`). Strips the target's offensive effects (`returnOffensiveAffects`) and holds them in `poisonAffects` (`:157-164`). On unInvoke they are reattached (`:108-119`). The poison is effectively paused, not cured.
- **Dragonsight** (`Chant_Dragonsight.java`). Sets the overlooking saving throw to the caster's level plus `2*XLEVEL + 100`, added to the existing stat value (`:81`; the line is cut off in the source extract). Standard duration (`:162`).
- **Darkvision** (`Chant_Darkvision.java`). Standard duration (`:138`). Refuses if already present.
- **EnhanceBody** (`Chant_EnhanceBody.java`). When unarmed, attack adjustment rises by `5 * ((level + 2*XLEVEL)/10)` and damage by `1 + ((level + 2*XLEVEL)/10)` (`:78-80`). Standard duration on self (`:121`).
- **Feralness** (`Chant_Feralness.java`). Active only while the mob's race differs from its base race (`:76`, `:96`), that is, while shape-shifted (see `Druid_ShapeShift`). When unarmed, damage is multiplied by `2 + XLEVEL` and attack gets `2*adjLevel` (`:83-84`). Damage and attack also get a bonus of `x/(4.0 - 0.1*XLEVEL)` (`:86`). Armour rises by `adjLevel` (`:88`). Hit points rise by `hpAdjustment + 2*XLEVEL` (`:97`).
- **Dehydrate** (`Chant_Dehydrate.java`). Against a mob, thirst falls by `150 + (level + 2*XLEVEL) * 100` (`:93`). Against a container or drink, the liquid is zeroed when below 10000 and raw-material contents are destroyed.
- **ExtendFortune** (`Chant_ExtendFortune.java`). Valid targets are group members, the caster, or any mob the caster may fight (`:110-113`). On a fizzle against a non-group enemy it uses `maliciousFizzle` (`:149-150`), but success always applies a beneficial effect (`:133`). Duration is the standard tick count times 10 (`:132-133`). Non-player targets get auto-award properties (`CMLib.awards().giveAutoProperties`, `:136-137`).
- **EndureRust** (`Chant_EndureRust.java`). Standard duration on a mob or item (`:145`). The message is "causing a rust proof film to envelope <T-HIM-HER>!".
- **Crossbreed** (`Chant_Crossbreed.java`). A marker with TICKS_ALMOST_FOREVER (`:156`). The effect class has no overrides. `Chant_CallMate.java:228` looks for it and casts it into a `Chant_Crossbreed`. The Beastmaster class maps this chant at level 25 (`CharClasses/Beastmaster.java:250`).
- **Fertility** (`Chant_Fertility.java`). Creates a Pregnancy effect (`:120`). Each check rolls `roll(1, 160 + 50*(numKids-1) - 10*XLEVEL, 0) == 1`, which adds one to the expected child count (`:144-147`). The effect lasts almost forever (`:187`). This is adult-themed mechanics; no explicit content is quoted.
- **ExplosiveDecompression** (`Chant_ExplosiveDecompression.java`). Indoor only unless auto (`:106`). A fire blast hits every inhabitant but the caster. Damage is `roll(numDice, 5, 25)` with `numDice = adjustedLevel + 2*X1` (`:136-137`). It is halved if the target's message has value greater than 0, meaning the target avoided it (`:139`). The flaming-blast text is shown with postDamage (`:140`).

### 6. Meditation and tracking

**DeepThoughts** (`Chant_DeepThoughts.java`). Cave meditation.
- Refused in combat ("You can't think deeply while in combat!", `:211-215`). Requires a cave or rock atmosphere ("You must be in a cave for this chant to work.", `:216-221`). Standard proficiency roll, then `beneficialAffect` with `Ability.TICKS_FOREVER` (`:224-232`).
- `executeMsg` breaks the trance on any move, hand, mouth-with-sound or eye action by the caster (`:111-118`).
- `tick` runs only on the mob tick (`:130`) and only every 60 seconds of wall-clock time (`:140`, using `System.currentTimeMillis`). It requires a proficiency check with no caster (`:142`).
- Alignment: `ratePct` is 1% of the alignment faction total (`:148`). If the mob is not already at the extreme of its alignment range (`MODERATE` purity below 99, `:149`), the push goes toward the side it already leans to. More good than evil gives `+ratePct`; otherwise `-ratePct` (`:151-154`). Inclination is pushed the same way (`:155-164`). The purity measure is `Factions.getAlignPurity` (semantics partly unverified).
- Ten flavour messages each tick (`:165-197`), for example "contemplate(s) the great depths" and "listen(s) to the wisdom of the stones".

**FindDriftwood** (`Chant_FindDriftwood.java`). A tracking chant that makes its own quarry.
- Requires standing on the watery surface (`:245-248`). The default target word is "driftwood" (`:240-241`).
- `findWhatImLookingFor` (`:136-183`) searches radiantly from the caster with `getRadiantRooms` (`:140-141`). The limit is `50 - (XLEVEL + XMAXRANGE)`. Too few rooms gives "You don't sense any driftwood around here. Perhaps no wrecks have occurred?" (`:142-146`).
- It picks one of 20 wood resources (`:147-169`). Drift room selection uses a second limit of `150 - 2*XLEVEL` on the room list (`:170-174`).
- It creates the wood item with `makeItemResource` (`:175`), weight `roll(1, 4 + adjustedLevel/15, 3 + XLEVEL)` (`:176-178`), and places it in the drift room with `Expire.Player_Drop` (`:180`).
- Tracking uses `makeTheTrail`, which finds a path to any drift room within `50 - XLEVEL` (`:186-205`). The inherited `Chant_FindPlant` tick reports "You sense @x1 @x2" and ends with "The trail fizzles out here." (`Chant_FindPlant.java:146-200`).
- Picking it up ends the chant (`:118-133`). Leaving the chant destroys the driftwood unless it is in the caster's room (`:208-223`).

**Farsight** (`Chant_Farsight.java`). Outdoors only (`:78`). The caster's range is `(level + 2*XLEVEL)/3` rooms (`:97`). With no argument, it lists visible open exits in every direction and the next room's exits, showing what lies beyond.

**Distant\* family** (`Chant_DistantIngrowth.java`, `DistantOvergrowth`, `DistantGrowth`, `DistantFungalGrowth`, `DistantWindColor`). A named far place is looked up with `CMLib.hunt().findRooms(CMLib.map().rooms(), mob, areaName, true, 10)` (`:90` in each file). The first room that passes the test is used. The test differs per chant.
- Ingrowth: an indoor stone or wood room (`:131-137` build seaweed, houseplant or plants).
- Overgrowth: an outdoor room (fungus in caves, seaweed in water, houseplant or plants elsewhere, `:127-136`).
- Growth: an outdoor room, plants only (`:133`).
- FungalGrowth: a cave room (`:94`), fungus built at `:132`.
- WindColor: an outdoor room that is not watery (`:94-95`). It reports the wind colour.
- Each failure gives a targeted message ("There IS such a place, but its in a cave where fungus rule, so your magic would fail.", and similar).

### 7. Timing and durations

- Tick length: `TIME_TICK = 4000` ms by default (brief, `core/CMProps.java:681`).
- Beneficial effects (`beneficialAffect`) use `getBeneficialTickdownTime`. Malicious effects (`maliciousAffect`, `StdAbility.java:2185`) use `getMaliciousTickdownTime`, which was not read, so the duration rule for them is unverified. If the passed adjustment is greater than 0, it is an absolute tick count. If it is 0, the standard is `casterLevel * 4 + 40` ticks (`StdAbility.java:2312-2334`). A negative adjustment is a percentage of that standard. Passing 0 therefore means "standard", and a positive number such as 10 means exactly 10 ticks (DeathMoon, `:226`).
- `Ability.TICKS_FOREVER` is `Integer.MAX_VALUE - 1000` and `TICKS_ALMOST_FOREVER` is `Integer.MAX_VALUE / 2` (`Abilities/interfaces/Ability.java:46-48`).
- Mud-time durations: the Earthpocket and Den last `CMProps.Int.TICKSPERMUDMONTH` (`Chant_Earthpocket.java:222`, `Chant_Den.java:194`). Fertilization uses ticks per mud day (`:188`). The curse and mood chants use hours per day times ticks per mud hour (`Chant_CurseSeed.java:243-244`, `Chant_CurseMood.java:169-171`).
- Real time: DeepThoughts uses wall-clock milliseconds for its 60-second cadence (`Chant_DeepThoughts.java:140`). DruidicConnection uses milliseconds per mud hour times hours per day (`Chant_DruidicConnection.java:100-102`).

### 8. Messages shown to players (samples)

- "Extreme emotions disrupt your chant." (`Chant.java:178`)
- "Your prolonged connection to this place fills you with harmony!" (`Chant_DruidicConnection.java:114`)
- "The Death Moon Rises!" and "The death moon sets." (`Chant_DeathMoon.java:225`, `:92`)
- "The gaze of the death moon <DAMAGE> <T-NAME>!" (`Chant_DeathMoon.java:144`)
- "The ground underneath <T-NAME> shakes as <T-NAME> fall(s) to the ground!!" (`Chant_Earthquake.java:210`)
- "A dark pocket of energy appears in a nearby wall." (`Chant_Earthpocket.java:223`)
- "Favorable winds begin to blow!" (`Chant_FavorableWinds.java:218`)
- "You can't think deeply while in combat!" (`Chant_DeepThoughts.java:213`)
- "There seems to be @x1 around here." (`Chant_FindDriftwood.java:113`)
- "The earthpocket can only be summoned or seen in a cave." (`Chant_Earthpocket.java:190`)
- "Your den, carefully covered, appears to the @x1!" (`Chant_Den.java`, invoke)
- "The cold biting wind <DAMAGE> <T-NAME>!", "The blizzard <DAMAGE> <T-NAME>!" (`Chant_FeelCold.java:140`, `:156`)

### 9. Interactions with other systems

- **Zapper masks.** EndowGemstones writes a recipe mask into `Prop_WearAdjuster` (`Chant_EndowGemstones.java:374-376`). The mask language is in `Libraries/MUDZapper.java` (per the brief).
- **Prop effects.** Endowments are stored as `Prop_WearSpellCast` (`Chant_EndowJewelry.java:197-211`), `Prop_FightSpellCast` (`Chant_EndowClub.java:183-184`) and `Prop_ItemSlotFiller` with an `AbilityContainer` (`Chant_EndowGemstones.java:270-278`). The `Prop_*` classes are in another bucket.
- **Crafting engine.** Ioun stones use a `GenCraftSkill` (`Chant_EndowIounStone.java:78`). Crystal growth uses the Blacksmithing, Armorsmithing and Weaponsmithing skills (`Chant_CrystalGrowth.java:108-`). The Distant* chants use the Chant_Summon* builders (for example `Chant_SummonSeaweed.buildSeaweed`, `Chant_DistantIngrowth.java:131`).
- **Tracking.** FindDriftwood calls `CMLib.tracking().getRadiantRooms` (`:141`) and `findTrailToAnyRoom` (`:203`). Trail-following comes from `Chant_FindPlant`.
- **Factions.** DeepThoughts reads and writes `CMLib.factions()` alignment and inclination (`:146-164`).
- **Experience.** Every Endow chant calls `CMLib.leveler().postExperience` (`Libraries/CoffeeLevels.java:1085-1105`).
- **Climate.** DeathMoon uses `Climate.canSeeTheMoon` (`:119`, `:161`, `:181`). `Chant.java:134` uses `Climate.weatherType`. Druid_MyPlants gives plant-room counts for DruidicConnection (`:105`).
- **Other chants.** Crossbreed is consumed by Chant_CallMate (`Chant_CallMate.java:228`). DelayPoison calls `returnOffensiveAffects` (`Chant_DelayPoison.java`, invoke), whose definition is outside this slice (location unverified).

## Content worth mining

### Full catalogue (49 chants, manifest order)

| # | Chant (file) | Lines | Domain / quality | Target | What it does (key numbers and lines) |
|---|---|---|---|---|---|
| 1 | Chant_EndowGemstones | 391 | Deep magic / indifferent | precious gem | Recipe mask into a wear adjuster; XP 490+10·XLEVEL (`:200`) |
| 2 | Chant_EndowIounStone | 333 | Deep magic / indifferent | precious stone | Ioun stones via GenCraftSkill; XP 1100+50·level (`:287`) |
| 3 | Chant_CurseSeed | 264 | Curse / malicious | mob | Hereditary curse; socials spread it; gender field overwritten (`:186-236`) |
| 4 | Chant_DruidicConnection | 253 | Enduring / indifferent | area | Territory with ≥50% plant cover; daily XP (`:115-117`) |
| 5 | Chant_FindDriftwood | 252 | Nature lore / OK self | item | Spawns and tracks a wood piece (`:169-180`) |
| 6 | Chant_DeepThoughts | 244 | Deep magic / OK self | self | Cave meditation; alignment drift every 60 s (`:140-164`) |
| 7 | Chant_DeathMoon | 235 | Moon altering / malicious | room | 10-tick room killer while moon visible (`:143`, `:226`) |
| 8 | Chant_Earthpocket | 232 | Rock control / OK self | self | Weightless container in cave walls; one mud month (`:212-222`) |
| 9 | Chant_FavorableWinds | 229 | Weather mastery / indifferent | ship | Ability +1+XLEVEL/3 (`:215-216`) |
| 10 | Chant_EndowJewelry | 228 | Deep magic / indifferent | jewelry | Stores a known chant on the item (`:197-211`) |
| 11 | Chant_FilterWater | 228 | Water control / indifferent | liquid | Purify and restore original state (`:134-170`) |
| 12 | Chant_Earthquake | 226 | Rock control / malicious | room mobs | Knockdown, 3 ticks, 20 damage (`:206-210`) |
| 13 | Chant_CurseFortune | 217 | Curse / malicious | mob | Save penalty 100+10·X (`:102`); four trips (`:72`) |
| 14 | Chant_EelShock | 213 | Weather mastery / malicious | room mobs | Stun 3+X+2·X1 ticks; wet room needed (`:203`) |
| 15 | Chant_Fertilization | 211 | Plant growth / indifferent | room | Ticks = level × mud-day ticks (`:188`); gardening skill bonus (`:120`) |
| 16 | Chant_Drifting | 207 | Enduring / OK self | mob | Flight; blocks advance and retreat (`:133-135`) |
| 17 | Chant_Drown | 207 | Water control / malicious | mob | Removes water breathing; 6 ticks (`:84-100`, `:197`) |
| 18 | Chant_EnchantShards | 205 | Deep magic / indifferent | wand | Copies a known chant into a wand; XP 10·level (`:156`) |
| 19 | Chant_FeelCold | 204 | Enduring / malicious | mob | Cold save -100; weather damage 1d8, 1d16 (`:120`, `:140-164`) |
| 20 | Chant_Den | 203 | Deep magic / indifferent | room | Temporary cave room, five protective props (`:135-190`) |
| 21 | Chant_EndowClub | 201 | Deep magic / indifferent | blunt wood weapon | Fight spell cast 25%, max 12 ticks (`:183-184`) |
| 22 | Chant_FeedingFrenzy | 198 | Animal affinity / indifferent | creatures in radius | Underwater only; radius 10+XMAX+X/2 (`:156-157`, `:173`) |
| 23 | Chant_FertileCavern | 198 | Plant control / indifferent | cave room | Owner gets a permanent copy saved to DB (`:176-179`) |
| 24 | Chant_Fertility | 196 | Breeding / OK others | mob | Pregnancy with 1-in-(160+50·(n-1)-10·X) extra child chance (`:144-147`) |
| 25 | Chant_FeelHeat | 195 | Enduring / malicious | mob | Fire save -100 (`:162`); 1d8, 1d16 per tick (`:119-134`) |
| 26 | Chant_Feralness | 193 | Animal affinity / beneficial self | self | Active while shape-shifted; unarmed damage ×(2+X) (`:78-97`) |
| 27 | Chant_Farsight | 188 | Enduring / indifferent | self | Outdoors; sees exits up to (level+2X)/3 rooms (`:78`, `:97`) |
| 28 | Chant_CurseMood | 180 | Curse / malicious | mob | Applies a Mood effect; duration by hours/day and level (`:169-171`) |
| 29 | Chant_FeelElectricity | 177 | Enduring / malicious | mob | Electric save -100 (`:87`); 1d5 per tick (`:106`) |
| 30 | Chant_DelayPoison | 176 | Preserving / beneficial others | mob | Holds offensive effects; reattaches on end (`:157-164`, `:108-119`) |
| 31 | Chant_Dragonsight | 171 | Shape shifting / beneficial self | mob | Overlooking save level+2X+100 (`:81`) |
| 32 | Chant_CrystalGrowth | 168 | Rock control / indifferent | cave | Random smithing craft; mana 50 (`:70-72`, `:104-132`) |
| 33 | Chant_EnhancePotion | 167 | Nature lore / indifferent | potion | Raises potion level; mana 50 (+5 per level above 30) (`:85-89`) |
| 34 | Chant_Crossbreed | 165 | Breeding / OK others | mob | Marker effect; read by Chant_CallMate (`:156`) |
| 35 | Chant_ControlWeather | 163 | Weather mastery / indifferent | area | Outdoors; room count divided by level+X; balance lock (`:131-141`) |
| 36 | Chant_DeepDarkness | 163 | Deep magic / malicious | room | Room darkness, standard duration (`:153`) |
| 37 | Chant_ExplosiveDecompression | 157 | Deep magic / malicious | indoor room | Fire blast roll(level+2·X1, 5, 25); halved on save (`:136-139`) |
| 38 | Chant_ExtendFortune | 156 | Cosmology / OK others | ally | Duration ×10; NPCs get auto-awards (`:132-136`) |
| 39 | Chant_EnhanceJewelry | 155 | Deep magic / indifferent | worn jewelry | Ability +1, cap 5+X/2 (`:90`, `:110`); 50 XP (`:119`) |
| 40 | Chant_EnhanceShard | 152 | Deep magic / indifferent | wand | Level and ability cap (`:107-108`); 50 XP (`:117`) |
| 41 | Chant_EndureRust | 149 | Preserving / beneficial others | mob or item | Rust-proof film, standard duration (`:145`) |
| 42 | Chant_Darkvision | 147 | Deep magic / OK self | mob | Darkvision, standard duration (`:138`) |
| 43 | Chant_DistantIngrowth | 147 | Plant growth / indifferent | indoor stone or wood room | Remote seaweed, houseplant or plant (`:90`, `:131-137`) |
| 44 | Chant_DistantOvergrowth | 146 | Plant growth / indifferent | outdoor room | Remote fungus, seaweed, houseplant or plant (`:127-136`) |
| 45 | Chant_DistantGrowth | 143 | Plant growth / indifferent | outdoor room | Remote plant (`:133`) |
| 46 | Chant_DistantFungalGrowth | 142 | Plant growth / indifferent | cave room | Remote fungus (`:94`, `:132`) |
| 47 | Chant_DistantWindColor | 142 | Weather mastery / indifferent | outdoor, not watery | Reports wind colour of a far place (`:94-95`) |
| 48 | Chant_EnhanceBody | 131 | Shape shifting / beneficial self | self | Unarmed attack and damage scale with (level+2X)/10 (`:78-80`) |
| 49 | Chant_Dehydrate | 130 | Enduring / malicious | mob, container, drink | Thirst -(150+(level+2X)·100) (`:93`) |

### Concrete content for a text game

- **Wood types** (`Chant_FindDriftwood.java:147-168`): oak, wood, pine, balsa, maple, redwood, hickory, ironwood, yew, teak, cedar, elm, cherrywood, beechwood, willow, sycamore, spruce, mesquite, bamboo and reed. A random one is picked for each driftwood spawn.
- **Contemplation lines** (`Chant_DeepThoughts.java:165-197`): ten short cave-meditation messages, such as "watch(es) the unchanging stones and feel(s) their history" and "expunge(s) <S-HIS-HER> unnatural thoughts".
- **Weather strike lines** (`Chant_FeelCold.java:140-164`, `Chant_FeelHeat.java:119-134`): "The blistering snow", "The blizzard", "The biting hail", "The scorching heat", "The burning hot dust". These are a ready-made list of weather hazards with dice.
- **Den.** A musty den that appears in a free direction, with its own protective props (`Chant_Den.java:159-190`).
- **Recipe rows.** The endowment tables use `ITEM_NAME, ITEM_LEVEL, ADJUSTER_ARGS, XLEVEL, EXPERTISE` (`Chant_EndowGemstones.java:117`). This is a good table shape for data-driven items.

## Notable design ideas

- **Chant as an item property.** EndowJewelry, EndowClub and EndowGemstones store a spell reference on an object (`LAYERED;<id>;`, `25%;MAXTICKS=12;<id>;`, `Prop_WearAdjuster` masks). The caster's own repertoire is the input, so a skilled druid can make gear from known spells (`Chant_EndowJewelry.java:116-130`).
- **Room-sweep as a crafting filter.** EndowIounStone temporarily removes every other precious item in the room so a generic craft routine can only see the chosen stone, then restores them in `finally` with their expiry dates (`Chant_EndowIounStone.java:293-331`). This reuses a general engine without changing it.
- **A pocket dimension in the wall.** Earthpocket is a container that exists in the walls, follows the caster only through cave and rock rooms, and is hidden elsewhere (`Chant_Earthpocket.java:132-168`).
- **Temporary places.** Den builds a new room in a free direction, links it, and gives it protective props. Its teardown is meant to remove it (`Chant_Den.java:135-190`).
- **Remote building by name.** Distant* casts at a named far place and builds a living item there, using the hunt library to find the place (`Chant_DistantIngrowth.java:90`).
- **Territory.** DruidicConnection is an area claim that needs continuing upkeep (planting ≥50% of the rooms) and pays XP daily (`Chant_DruidicConnection.java:105-117`).
- **Weighted craft from the environment.** CrystalGrowth rolls a d10 to choose which smithing skill makes a crystal item, then retries up to 100 times (`Chant_CrystalGrowth.java:104-108`).
- **State that depends on form.** Feralness works only while the race differs from the base race, that is, while shape-shifted (`Chant_Feralness.java:76`, `:96`).
- **Veto by message.** Earthquake blocks standing (`Chant_Earthquake.java:116-125`), Earthpocket blocks hand and move actions on the pocket (`Chant_Earthpocket.java:103-111`), and Drifting blocks advancing and retreating. Each vetoes a message in `okMessage` rather than changing game state directly.

## Problems and cruft

1. **CurseSeed inheritance count never decrements (likely bug).** `Chant_CurseSeed.java:134-140` takes `text().substring(x)` where `x` is the index of `'/'`. That string starts with a slash, and `CMath.s_int` returns 0 when parsing fails (`core/CMath.java:2104-2114`). So `count` is -1, the `count>=0` block (`:141`) never runs, and the copy keeps its unreduced generation count. This follows from reading the code and has not been run. Also `if(x>0)` is repeated at `:135` and `:138`, and the gender logic is duplicated at `:113-117`, `:145-149` and `:226-229`.
2. **CurseSeed quality mismatch.** `abstractQuality()` is MALICIOUS (`:68`), but `isMalicious()` returns false (`:78-81`).
3. **Den teardown looks unreachable and clears the wrong exit.** `Chant_Den.java:99` requires `R.roomID().equalsIgnoreCase("")`, but invoke assigns a room ID at `:166`, so the teardown branch appears never to run. Even when it does, `:102-103` clear `Directions.UP` instead of `denDirection`, which can remove a real upward exit. Unverified at runtime.
4. **EndowJewelry unguarded cast.** `Chant_EndowJewelry.java:98` checks only `instanceof Item`, but `:103` and `:111` cast the target to `Armor` with no check. A non-Armor item would throw ClassCastException. EnhanceJewelry guards the same cast (`Chant_EnhanceJewelry.java:90`). EndowJewelry's `:195-211` also has three branches that add the identical `Prop_WearSpellCast`, so the held-or-wield test does nothing.
5. **Mana spent before a silent XP failure.** In EndowGemstones, `super.invoke` spends mana (`:351`) before XP is posted (`:359`). If `postExperience` returns 0 (XP disabled or expless, `CoffeeLevels.java:1085-1097`), the chant prints "You are unable to complete this chant." (`:362-366`) and returns false after the mana is gone.
6. **EnhanceBody auto message is wrong.** `Chant_EnhanceBody.java:117` uses "<T-NAME> go(es) feral!" for auto casts, copied from `Chant_Feralness.java:165`.
7. **FindDriftwood display typo.** `Chant_FindDriftwood.java:62` returns "(Finding Diftwood)" from `displayText()`. The constructor's default at `:95` is correct but is overridden.
8. **Radiant search uses depth as a count.** `getRadiantRooms` takes a max depth (`TrackingLibrary.java:500`, parameter `maxDepth`), but `Chant_FindDriftwood.java:140-142` compares the number of returned rooms with the same value. The far-room branch (`:170-174`) indexes the list by a second number, `150 - 2*XLEVEL`.
9. **Feralness divisor.** `Chant_Feralness.java:86` divides by `4.0 - 0.1*XLEVEL`, which is zero at XLEVEL 40. Behaviour of `CMath.div` on zero is unverified.
10. **Misnamed variable.** `Chant_FeelCold.java:110` calls the damage increase `recovery`. It increases incoming cold damage.
11. **Throttle naming.** The `oncePerRd` flag in `Chant_Earthquake.java:89-96` resets every tick, not every round.
12. **Unbounded message work.** `Chant_Earthpocket.java:117-121` runs `movePocket` on every executeMsg, including all of the caster's movement and speech.
13. **Help text mismatch.** `Chant_EndowIounStone.java:255` advertises "scan", but the routing at `:237-247` does not handle it. Whether GenCraftSkill does is unverified.
14. **Null-safety gaps.** DeepThoughts dereferences `mob.location()` without a check (`Chant_DeepThoughts.java:216`). DeathMoon calls `getXLEVELLevel(invoker)` at `:143` after tick may have set `invoker` to null (`:117`). Whether `getXLEVELLevel(null)` is safe is unverified.
15. **Wall-clock timing.** DeepThoughts uses `System.currentTimeMillis` (`:140`), unlike the other chants, which count ticks. This makes behaviour depend on server load.
16. **Mismatched locations.** Earthpocket checks cave status for the caster's room (`:187-189`) but adds the pocket to the target's room (`:221`).
17. **Alignment amplifier.** DeepThoughts pushes alignment toward whichever side the mob already leans to (`:149-154`) until purity reaches 99. The design makes the druid more extreme over time. This may be intended.
18. **ExtendFortune target logic.** The validity test at `Chant_ExtendFortune.java:110-113` lets the chant target enemies the caster may fight. The fizzle path (`:149-150`) treats that case as malicious, but the success path always applies a beneficial effect (`:133`). The intent is unclear; it may be an inverted condition or a deliberate dual-use chant.

### Multiplayer assumptions to rethink in a single-player game

- **Groups and followers.** DeathMoon spares the caster's group and followers (`Chant_DeathMoon.java:127-142`).
- **Who may be fought.** Several chants call `mayIFight` (`:130`, `:221`), which becomes an NPC-faction rule.
- **Protected by other players.** CurseFortune's protection is aimed at hostile actions by other characters (`Chant_CurseFortune.java:114`).
- **Player-only effects.** ExtendFortune branches on `isPlayer()` (`Chant_ExtendFortune.java:136`). NPCs get auto-awards and players do not.
- **One per area.** DruidicConnection allows only one druid per area (`Chant_DruidicConnection.java:189`). In a single-player world this becomes a single owner per territory.

## Takeaways for a Python SUD

1. **A spell record instead of a class per spell.** Each chant is mostly metadata plus one effect. In Python this is a dataclass with `id`, `domain`, `quality` (beneficial, malicious, indifferent), `target_kind` (self, mob, item, room, area), `mana_cost`, `xp_cost`, and `apply(caster, target, level)`. The rule for duration can be a small function: `standard_ticks = level*4 + 40`, with explicit absolute ticks overriding it (`StdAbility.java:2312-2334`).
2. **Endowments as typed data, not strings.** Replace the misc-text codes (`LAYERED;id;`, `25%;MAXTICKS=12;id;`, `;` and `~` in FilterWater) with fields: `Enchantment(kind="chant", chant_id=..., chance=0.25, max_ticks=12)`. Items then hold references to spells, and the recipe table (`ITEM_NAME, ITEM_LEVEL, MASK, XLEVEL, EXPERTISE`) becomes a CSV loaded into dataclasses.
3. **Reversible effects store their own original state.** FilterWater serialises the old material and name into a string (`Chant_FilterWater.java:95-130`). In Python, keep `original_material`, `original_name` and `original_liquid` on the effect object and restore them in `remove()`.
4. **Territory with upkeep.** Adopt the DruidicConnection pattern: a per-area count of player-owned features, a threshold (50%), a daily check, and a daily reward (`Chant_DruidicConnection.java:105-117`). It gives a living-world reason to keep tending land.
5. **Use game ticks, not wall time.** DeepThoughts's 60-second wall clock (`:140`) makes results depend on server speed. Use the simulation tick everywhere so the game is deterministic and testable.
6. **Two-phase events.** The okMessage veto and executeMsg pattern (Earthquake, Earthpocket, Drifting) maps to `event = Event(...)`, then `for h in hooks: h.before(event)`, then the change, then `h.after(event)`. Replace `msg.value() <= 0` with an explicit `event.cancelled` flag.
7. **Pay costs in one place.** Compute mana and XP in one function before any state change, and refuse early. The Endow chants instead charge XP after proficiency and sometimes after mana (EndowGemstones:351-366). A single `can_afford` check avoids the silent-failure path.
8. **Keep the climate queries.** `canSeeTheMoon`, `weatherType` and the weather bitmask (`Chant.java:126-165`) are good hooks. Model weather and a moon phase as pure functions of the world clock, so DeathMoon's gate is a cheap check.
9. **Drop.** The MSP sound hooks (`Chant_Earthquake.java:189`), the NPC-only AutoAwards branch (`Chant_ExtendFortune.java:136-137`), the group and follower checks (`Chant_DeathMoon.java:127-142`) as they stand, the duplicated gender code, and the three-way Prop branches (`Chant_EndowJewelry.java:195-211`).
10. **Rethink the crafting wrapper.** The room-sweep trick (`Chant_EndowIounStone.java:293-331`) is worth keeping as a pattern, but in Python it should be a `with` block that temporarily hides competing items from a craft function and restores them on exit.

## Coverage

- **Read in full or essentially in full:** `Abilities/Druid/Chant.java` (base class, not in this bucket, read for context), `Chant_EndowGemstones.java`, `Chant_EndowIounStone.java`, `Chant_CurseSeed.java`, `Chant_DruidicConnection.java`, `Chant_FindDriftwood.java`, `Chant_DeepThoughts.java`, `Chant_DeathMoon.java`, `Chant_Earthpocket.java`, `Chant_FavorableWinds.java`, `Chant_Earthquake.java`, `Chant_FilterWater.java`, and `Chant_EndowJewelry.java` (body, non-blank lines, and a diff against EndowGemstones).
- **Read in part:** `Chant_FindPlant.java` (other bucket; tick and executeMsg only, `:146-200`).
- **Skimmed (compacted method bodies or grep of key lines):** `Chant_EelShock`, `Chant_Fertilization`, `Chant_Drifting`, `Chant_Drown`, `Chant_CurseFortune`, `Chant_EnchantShards`, `Chant_EndowClub`, `Chant_FeedingFrenzy`, `Chant_FertileCavern`, `Chant_Fertility`, `Chant_FeelCold` (tick read), `Chant_FeelHeat` (partly), `Chant_FeelElectricity` (partly), `Chant_Feralness`, `Chant_Farsight`, `Chant_CurseMood`, `Chant_DelayPoison`, `Chant_Dragonsight`, `Chant_CrystalGrowth`, `Chant_EnhancePotion`, `Chant_Crossbreed`, `Chant_ControlWeather`, `Chant_DeepDarkness`, `Chant_ExplosiveDecompression`, `Chant_ExtendFortune`, `Chant_EnhanceJewelry`, `Chant_EnhanceShard`, `Chant_EndureRust`, `Chant_Darkvision`, `Chant_DistantIngrowth`, `Chant_DistantOvergrowth`, `Chant_DistantGrowth`, `Chant_DistantFungalGrowth`, `Chant_DistantWindColor`, `Chant_EnhanceBody`, `Chant_Dehydrate`, `Chant_Den` (invoke and unInvoke read in full).
- **Verified helpers:** `core/CMath.java:2104-2114` (s_int), `Abilities/StdAbility.java:2312-2349` (tick-duration helpers), `Libraries/CoffeeLevels.java:1085-1105` (postExperience), `Libraries/Factions.java` (getAlignPurity, semantics partly unverified), `Libraries/interfaces/TrackingLibrary.java:500` (getRadiantRooms signature), `Abilities/interfaces/Ability.java:46-48` (tick constants).
- **Not read:** the recipe data files (`endowgemstones.txt`, `endowiounstone.txt`), `GenCraftSkill`, `Druid_MyPlants`, `Druid_ShapeShift`, the `Chant_Summon*` builders, `Chant_CallMate` (beyond line 228), and the `Prop_*` effect classes.
