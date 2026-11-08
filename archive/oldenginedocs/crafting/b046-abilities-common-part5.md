# Common skills, part 5: labels, lectures, mining, leatherwork, painting and shops (Labeling to Painting)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b046` (Abilities/Common [part 5/7: Labeling .. Painting]) · 42 files · 8947 lines of Java.

Citation base: Java paths are relative to `com/planet_ink/coffee_mud/` (for example `Abilities/Common/Painting.java:182`). Paths under `resources/` are relative to the CoffeeMUD folder. Base classes that belong to other buckets (CommonSkill, GatheringSkill, PaintingSkill, CraftingSkill, EnhancedCraftingSkill, BuildingSkill, AnimalTaming) are cited only where this code depends on them. Everything below was read from the source; nothing was executed.

## Summary

This bucket holds the artisan and "soft" common skills: labelling and teaching (`Labeling`, `Lecturing`, `Organizing`), the painting family (`Painting` for canvases, `Lacquerring` for a single lacquer colour, and `MasterDyeing`/`MasterLacquerring` for two- or three-colour designs driven by a mask grammar), leatherwork with a stage-and-tier model, mining and mushroom identification, locksmithing and taming, and `Merchant` ("Marketeering"), a shop that can be attached to any room, exit, area, item or mob. Twenty-seven `Master*` subclasses (plus one Legendary tier) add a higher tier to a base skill: longer base durations, a yield multiplier, and a level band that decides which items the tier may make. Nearly every action is a timed, tick-driven effect that aborts on combat or movement, rolls proficiency at the start or end, and only then creates or changes something in the world. The most reusable ideas for a single-player SUD are the recipe table with generated stage variants, the per-room depletion rule for gathering, the teaching-transfer rule, and the shop-as-attachment model; the mask grammar and the session-prompt flows are heavier than a one-player game needs.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Common/Merchant.java` | "Marketeering": an always-on ShopKeeper ability that can sit on rooms, exits, areas, items and mobs | 974 |
| `Abilities/Common/LeatherWorking.java` | Leatherwork crafting; Normal, Hard and Studded stages; mend, refit, learn, enhancements | 731 |
| `Abilities/Common/MasterLeatherWorking.java` | Master leatherwork; seven stages (Designer to Legendary); level-band gate | 760 |
| `Abilities/Common/Painting.java` | Item and wall painting from `skills/painting.txt`; prompt-and-replay flow | 656 |
| `Abilities/Common/Lacquerring.java` | Applies one lacquer colour from `skills/lacquering.txt` to a crafted item | 240 |
| `Abilities/Common/MasterPaintingSkill.java` | Multi-colour engine: `applyColorMask`, `addPaintJob`, sysop test | 334 |
| `Abilities/Common/MasterDyeing.java` | Master dyeing: design plus two or three colours (`skills/masterdyeing.txt`) | 339 |
| `Abilities/Common/MasterLacquerring.java` | Master lacquering; same engine, other materials and recipe file | 355 |
| `Abilities/Common/LockSmith.java` | Locks and keys on exits and containers (a `CraftingSkill`) | 424 |
| `Abilities/Common/Mining.java` | Mines ore, stone and metal from the room (a `GatheringSkill`); `bundle` mode | 223 |
| `Abilities/Common/Mycology.java` | Identifies raw mushrooms from `skills/mycology.txt` | 309 |
| `Abilities/Common/MasterMycology.java` | Same skill without the one-unit limit (bulk identification) | 76 |
| `Abilities/Common/Labeling.java` | Writes sequential "Tag #" labels on generic items | 306 |
| `Abilities/Common/Organizing.java` | Sorts room or container contents by a chosen key | 333 |
| `Abilities/Common/Lecturing.java` | Teaches a known skill to three or more students in the room | 347 |
| `Abilities/Common/MeatCuring.java` | Cures meat by giving it the `Prayer_Purify` effect | 174 |
| `Abilities/Common/MountTaming.java` | Animal taming restricted to favoured mount races | 74 |
| `Abilities/Common/Masonry.java` | Stone construction; configures `BuildingSkill` | 116 |
| `Abilities/Common/Landscaping.java` | Outdoor terrain and road construction; configures `BuildingSkill` | 111 |
| `Abilities/Common/MasterFoodPrep.java` | Cooking-family master tier, plus a "chop" subcommand | 195 |
| `Abilities/Common/MasterBaking.java`, `MasterCooking.java`, `MasterDistilling.java` | Same cooking-family master pattern (duration 60/min 8, yield 2) | 119, 118, 118 |
| `Abilities/Common/MasterArmorsmithing.java`, `MasterWeaponsmithing.java`, `LegendaryWeaponsmithing.java`, `MasterTailoring.java`, `MasterCostuming.java` | Master and Legendary tiers of crafts defined in other buckets; each adds `masterCraftCheck` and a recipe file | 112, 108, 108, 96, 94 |
| `Abilities/Common/Master{Chopping,Digging,Drilling,Farming,Fishing,Foraging,Gardening,Mining,Trawling,Shearing,Butchering}.java` | Gathering master tier: longer durations, yield 3 (table below) | 70 to 74 each |
| `Abilities/Common/Master{Herbology,Floristry,Mycology}.java` | Gathering-like masters that are not limited to one unit | 76 each |
| `Abilities/Common/MasterSmokeRings.java` | Data-only override (`skills/mastersmokerings.txt`) | 65 |
| `Abilities/Common/CommonSkill.java` (other bucket) | Base of every timed skill: invoke gates, costs, tick aborts, `getDuration`, `dropAWinner` | 1016 |
| `Abilities/Common/GatheringSkill.java` (other bucket) | Room yield depletion, resource lists, `bundle()` | 513 |
| `Abilities/Common/PaintingSkill.java` (other bucket) | Paint-job machinery: colour codes in names, saved originals | 398 |
| `Abilities/Common/AnimalTaming.java` (other bucket) | Taming base: strips behaviours from an animal | 259 |
| `Abilities/Common/CraftingSkill.java` (other bucket) | Crafting base; enhancement enum (121), `replacePercent` (279), `addSpellsOrBehaviors` (539), `isANativeItem` (2011) | 2459 |
| `Abilities/Common/EnhancedCraftingSkill.java` (other bucket) | Expertise enhancements: `enhancedTypes` (465), `enhanceItem` (685) | 913 |
| `Abilities/Common/BuildingSkill.java` (other bucket) | Construction engine used by Masonry and Landscaping | 2035 |
| `Items/Basic/GenCaged.java` | Caged mob storage; `unCageMe` rebuilds a mob from stored text (135) | n/a |
| `Abilities/Prayers/Prayer_Purify.java` | Stops decay on food by zeroing `decayTime` (88-93) | n/a |

## How it works

### 1. What every skill here inherits

- **Timed effect, not an instant action.** `CommonSkill` is a `StdAbility` that runs as an effect on the mob. `invoke()` refuses while the mob is in combat (`CommonSkill.java:866`, "in combat!"), riding (871), unable to see (878), sitting or sleeping (884-887), or unable to move (904). It first cancels any other common skill the mob is running (890-902, with an exception list from `getUninvokeException()`), then checks and deducts mana, movement and hit points (909-938). The subclass sets the result and calls `beneficialAffect(..., duration)` at the end.
- **Per-tick aborts.** `tick()` aborts the activity if the mob is in combat, has changed room, is no longer awake and mobile, or has become unable to act (`CommonSkill.java:236-251`). It sends an "almost done" line when `tickDown==4` (253-261) and a progress line every four ticks (263-271). `stop` (828-845) and `setMiscText("abort")` (850-856) also abort, and every subclass checks `!aborted` before it delivers a result.
- **Duration.** `getDuration(baseTicks, mob, itemLevel, minDuration)` (345-361) first removes `(mob level - item level) / LASTPLAYERLEVEL × 0.5` of the ticks (348-350), then removes 5% per XTIME level (356-357), and never goes below the minimum (353-359). The default tick is 4000 ms (`core/CMProps.java:681`).
- **Costs.** `usageCost()` starts at 25 (`CommonSkill.java:560`), drops by `diff/3` when the mob's qualifying level is more than two levels above the skill's lowest level (579-584), drops to 5 when the difference exceeds 60 (580), and has a floor of 5 (624).
- **Proficiency.** Each subclass calls `proficiencyCheck(mob, modifier, auto)` and stores the failure as `messedUp`. `unInvoke()` then takes the spoiled branch, which usually emits a "mess(es) up" line and drops a loser item (see `CraftingSkill.java:471`).
- **Output placement.** `dropAWinner()` (`CommonSkill.java:386-446`) adds the item to the room with `Expire.Resource`. If the room refuses the item, the mob gets a "lottery" message and 50 experience (436-441).
- **Teaching limits.** `canBeLearnedBy()` and `teach()` (750-812) cap how many common and crafting skills a student may hold, through `CMLib.ableComponents().getSpecialSkillRemainder`.

### 2. Painting (`Painting.java`): items and wall art

- **Commands.** `paint list`, `paint <type> <canvas>` and `paint wall` (`Painting.java:182-291`). The skill does not extend `PaintingSkill`; it is a `CommonSkill` that reads `painting.txt` (145).
- **Gates.** It needs a session to answer prompts, and an NPC painter needs a follower with a session (224-230). The canvas must be cotton or silk, or have a name ending in CANVAS or SILKSCREEN (307-314). The recipe must pass both the expertise column (`exp <= XLEVEL`, 325) and the level test `level <= xlevel(mob)` (326), where `xlevel = level + 2 × XLEVEL` (`CommonSkill.java:645-648`).
- **Prompt-and-replay.** The command asks for a subject (493-502). If the answer is "here" or the room's name (520-524), it captures the room's full look text as the description, with the canvas temporarily hidden (526-535). Otherwise it looks up a named object in the room (538-545) or takes the typed text (549). It then asks for a description (552-577 or 581-612) and calls itself again with two hidden tokens, `PAINTINGKEYWORDS=` and `PAINTINGDESC=` (453-456, 571-574, 603-609). The tokens are stripped from the command tail at 194-216.
- **Result.** The item class comes from the recipe (472). Its name is "a/an <recipe> of <keywords>" (473-475), and its weight, material and level come from the canvas (479-482). Value is the canvas value plus `1d(recipe value + XLEVEL)` (480). Spells come from the recipe through `addSpellsOrBehaviors` (485). The canvas is destroyed (486).
- **Timing.** `messedUp = !proficiencyCheck(...)` (644). The duration is `getDuration(25, mob, 1, 2)` (645). The finished painting waits in `building` until the effect ends (`unInvoke`, 160-179). A spoiled painting produces "mess(es) up painting" and nothing else. A good one is dropped on the floor with `Player_Drop` (173).
- **Wall art.** Needs owner permission for the room (236-240). A GenWallpaper is created (349-352). The keyword prompt checks for unlabelled room items that share a keyword and asks whether to destroy them, with the default answer N (370-412). Then it asks for a description and a confirmation (416-461).
- **List.** `paint list` filters by expertise, level and zapper mask (`zmask`, 247-287), and prints item and level columns.

### 3. Lacquering and the paint-job machinery (`Lacquerring.java`, `PaintingSkill.java`)

- **Commands.** `lacquer <item> <colour>`, `lacquer <item> remove` and `lacquer list` (`Lacquerring.java:115-239`). The target must be crafted, meaning it has a crafting brand (158-162). It must also be generic and made of glass, metal, mithril, synthetic, precious, rock or wooden material (171-182). The colour name is matched in `lacquering.txt` (186-198) with level `<= adjustedLevel` and expertise `<= XLEVEL`.
- **Applied when the timer ends.** `unInvoke()` (90-113) removes the old paint job, then applies the new one unless the command was `remove` (106-108). A failed proficiency check sets `writing=""` (226-227), which leads to "mess(es) up the lacquering" (103).
- **Duration.** The recipe's tick column (10 to 87 in the data) is used. The default is 30 and the minimum is 12 (228-231).
- **Colour in the text.** The paint job is stored in the name and display text as in-band colour codes. The item description is recoloured and closed with `^?` (`PaintingSkill.java:389-397`, 307-348). `fixColor` inserts the colour word after "a"/"an", "of", "some" or "the", or at the front (102-129).
- **Reversal.** Before a paint job is applied, the original name and display are saved on an `ExtraData` effect (368-387), unless the display already has a colour close code (370). `removePaintJob` (228-260) restores the saved values if present (350-366). Otherwise it strips known colour words, longest first (131-194, 179-190), and removes colour codes from the description (256).
- **Colour-code characters.** `#` introduces a 256-colour code and `~` a background code (`PaintingSkill.java:268-305` uses `ColorLibrary.COLORCODE_FANSI256`, `COLORCODE_BACKGROUND` and `COLORCODE_BANSI256`; the values are `Libraries/interfaces/ColorLibrary.java:322-332`).

### 4. Dyeing and the master colour masks (`MasterDyeing.java`, `MasterLacquerring.java`, `MasterPaintingSkill.java`)

- **Commands.** `masterdye <item> <design> <colour> <colour> [<colour>]`, `remove`, `list` (designs, `MasterDyeing.java:137-154`) and `colors` (palette, 156-181). A sysop typing `test` runs `doPaintingTest` (129-133).
- **Colour parsing.** Colours are matched greedily against all 256-colour names, longest first (`MasterDyeing.java:204-237`, `PaintingSkill.java:179-190`). There is no word boundary, so "redblue" parses as two colours. Black is excluded (165-166, 219-220). Each colour must be within XLEVEL (222). Two colours are required and three is the maximum (238-248). The count must equal the design's slot count (264-267), and `requiredColorsInRecipe` returns 3 if the mask contains a `3`, else 2 (`MasterPaintingSkill.java:291-296`).
- **Materials.** Dye takes cloth, paper, liquid, vegetation or leather (`MasterDyeing.java:249-253`). Lacquer takes glass, metal, mithril, synthetic, precious, rock or wooden material (`MasterLacquerring.java:265-271`).
- **Applying the mask.** The chosen colours and design are copied onto the effect instance (`MasterDyeing.java:329-335`). At the end, `MasterPaintingSkill.addPaintJob` (249-289) calls `applyColorMask` (62-246) on the name and display text, then recolours the description (285-286).
- **Mask language.** Characters in a mask (documented in the header of `masterdyeing.txt`): `0` reset; `1`, `2`, `3` a colour slot; `w` and `W` word end (`W` is fixed length); `e`, `E`, `F`, `c`, `C`, `m` positional anchors (last letter, last word, next-to-last word, middle letters, centre word, middle); `-` back one character; `*` finish the phrase; `|` separates alternatives, one of which is picked at random (`MasterPaintingSkill.java:71-84`, 109, 238-242).
- **Duration.** Recipe ticks, with leather doubled (`MasterDyeing.java:322-323`) and a minimum of 6 (324). `MasterLacquerring` has the same rule, but its leather branch is dead code (see Problems).
- **Sysop test.** `doPaintingTest` (`MasterPaintingSkill.java:298-333`) builds a test item and applies each design with palette entries 9, 10 and 11 (313), printing the results. It is the only way to see every design at once.

### 5. Leatherwork and its master tier (`LeatherWorking.java`, `MasterLeatherWorking.java`)

- **Stage variants are generated at load time.** `LeatherWorking.loadRecipes()` (161-213) copies every base recipe into Hard and Studded variants. Each copy has a prefix word and a level offset (185-198). The offsets are 9 (Hard) and 18 (Studded) (`Stage` enum, 104-106). Normal recipes stay unprefixed. `Stage.find()` (138-157) recovers the stage from a name.
- **Stage numbers** (`LeatherWorking.java:104-106`): Normal (offset 0, multiplier 1, damage ×1.0, attack −7, armour ×1.0); Hard (9, 2, ×2.0, −13, ×1.75); Studded (18, 3, ×3.0, −19, ×2.5). Studded requires metal (554, 571-576).
- **Master stages** (`MasterLeatherWorking.java:107-113`): Designer (offset 30, multiplier 4, damage ×1.0, attack −16, armour ×1.0); Cuirboulli (37, 5, ×1.28, −24, ×1.15); Thick (45, 6, ×1.56, −30, ×1.3); Masterwork (54, 7, ×1.84, −36, ×1.45); Laminar (63, 8, ×2.12, −42, ×1.60); Battlemoulded (72, 9, ×2.40, −48, ×1.75); Legendary (80, 10, ×2.68, −54, ×1.85). Every master recipe gets all seven stage prefixes, with the level set to base + 2 + offset (`MasterLeatherWorking.java:312-375`, 336-341). Only Battlemoulded needs metal (601).
- **Effects of a stage on the item.** In the base tier, duration is `multiplier × recipe ticks`, minimum 4 (`LeatherWorking.java:602`). Value is base × `(0.5 + multiplier/2)`, so Normal 1.0×, Hard 1.5×, Studded 2.0× (620). In the master tier, value is base × `1 + 0.3 × (multiplier − 4)` (650-651), so Designer 1.0× and Legendary 2.8×. Its duration is always computed against level 30 (633), not the recipe level.
- **Tier gate.** The base tier refuses items above level 30 unless they are native items (`LeatherWorking.java:289-290`). The master tier refuses items below level 31 unless they are native (`MasterLeatherWorking.java:246-247`). The two tiers therefore divide the same catalogue at level 30/31.
- **Pipeline** (`LeatherWorking.java:507-728`). An optional trailing number sets the leather amount (515-519). The leather requirement is checked (520). The recipe is matched exactly, then loosely (525-527). The stage comes from the name (537). Components are gathered (560), leather units are fetched (563-582), and the skill is charged (583). Materials are destroyed (585-593), then the item is made from its class (595). The duration is set (602). Name, description, weight and value follow (604-620). Hardness is a material property: level gets `hardness/2` (623), weapons get attack `baseYield + abilityCode + hardness×5 + bonusAttack` (661) and damage `round(armordmg × damagePct) + hardness` (664), armour gets `round(armordmg × armorPct) + (baseYield + abilityCode − 1) + hardness` (678). Drinks get liquid capacity `(capacity + (multiplier − 1) × 2) × 50` (686). A spoiled result (697) takes the `messedUp` branch in `unInvoke`.
- **Other modes.** `mend` (459-474) sets `usesRemaining` to 100 on success (`LeatherWorking.java:247-251`). `refit` (476-506) resets height to 0 (259-262). `learn` (451-454) deconstructs an existing item into a recipe (`deconstructRecipeInto`, 255). `scan` (456-457). Mend and refit share the same `CraftingActivity` enum.
- **Enhancements.** The command's expertise words are parsed by `enhancedTypes()` (`EnhancedCraftingSkill.java:465-563`), and `enhanceItem()` applies them (685 onward, called at `LeatherWorking.java:720`). The types are in `CraftingSkill.java:121-133`: ADVNCRAFT, LITECRAFT, DURACRAFT, QUALCRAFT, LTHLCRAFT, CNTRCRAFT, FORTCRAFT, IMBUCRAFT, VIGOCRAFT and RUSHCRAFT. RUSHCRAFT lowers value by 10/20/30%, cuts ticks by the same share, and lowers the level by 2/3/4 (`EnhancedCraftingSkill.java:735-759`). LITECRAFT raises value by 10/20/30% and adds 0/25/50% to ticks (713-734). DURACRAFT only applies to protective armour (771-775).

### 6. Mining and the gathering family (`Mining.java`, `GatheringSkill.java`, Master* gatherers)

- **Start.** `Mining.invoke` refuses while mounted (`Mining.java:66-69`, via `allowedWhileMounted`). It needs a room with precious, glass, sand, rock, metal or mithril material (183-188). `bundle` as the first word switches to bundling mode (171-179).
- **Yield is decided at the end.** The skill reveals the vein at `tickUp==6` ("You have found a vein of ...", 104-111; or "You can't seem to find anything worth mining here", 114-115). On completion (`unInvoke`, 131-158):
  - A unit roll is 1d4 if the material value is 50 or more, otherwise 1d6 (134-137).
  - ROCK other than coal rolls 1d25 (138-140).
  - The result is multiplied by `baseYield + abilityCode` (141). Base yield is 1 (`Mining.java:93-96`).
  - It is passed through the room depletion rule (142).
  - Each unit is a copy of the found raw item, dropped separately (152-157).
- **Gating by room.** Precious, glass and sand rooms are accepted at invoke (183-188) but never yield anything: the yield test at 196-203 accepts only rock, metal and mithril. Mining in those rooms therefore spends time and cost for nothing.
- **Room depletion.** `GatheringSkill` keeps a static map from room to a record of resource code, harvest count and expiry (`GatheringSkill.java:69-70`). The first harvest in a room gets full yield (`adjustYieldBasedOnRoomSpam`, 111-141). Each later harvest drops the yield by 20 percentage points of the original (79, 144-146). `checkIfAnyYield` (82-109) refuses the skill once the count reaches five. The record expires 30 minutes after it was created (69, 130-132), and it resets when the room's resource changes (93-94, 123-124). Traced by hand, a room gives 100%, 80%, 60%, 40% and 20% of the base yield, then nothing until the window expires. The rule is shared by every gathering skill, because the map is static and keyed only by room.
- **Fixed material tweaks.** `fixResourceRequirement` halves mithril and balsa, divides adamantite by three and doubles ironwood (`GatheringSkill.java:161-183`).
- **Bundling.** `bundle <amount|ALL> <name>` (`GatheringSkill.java:365-512`) merges loose raw piles of one kind into a single bundle whose weight is the amount. It picks the Nth match with a `name.N` suffix (396-403), requires matching subtype and secret identity (412-420), and consumes the source piles (481-497). Non-uninvokable effects are copied to the bundle (503-509). A bundle of decaying food takes the shortest decay time (501-502).
- **Master gathering tier.** The `Master*` gatherers override `getDuration` with a longer base and a higher floor, and most set `baseYield` to 3 (table in section 17; Herbology, Floristry and Mycology override `duration()` instead). Master Herbology, Floristry and Mycology drop the one-unit limit (`isLimitedToOne` returns false, for example `MasterMycology.java:66-69`).

### 7. Labelling and tags (`Labeling.java`)

- **Tag format.** A tag is `prefix + 4-digit counter`, for example `GI0001` (`Labeling.java:167-169`). The prefix comes from the capital letters of the item's class ID (147-151) and is cached in a static map (`tagPrefixes`, 69, 130-163). The counter is per skill instance and per item type (`nextTagNumbers`, 71). It is saved in the skill's text (`text()` 105-108 and `setMiscText` 111-120).
- **Effect.** The tag is appended to the description as " Tag #" plus the tag (`unInvoke`, 196-200), and to the display text as "(Tag #...)" (200). Removing a tag truncates both at the label (184-194, 205-206). The `Organizing` skill can sort by tag through `getCurrentTag()` (`Labeling.java:91-102`, `Organizing.java:160`).
- **Requirements.** The mob needs the `Skill_Write` ability (`Labeling.java:267-272`, implemented in `Abilities/Skills/Skill_Write.java`). The target must be generic (274-278). Both the labelling and the writing proficiency checks must pass, or the tag is not written (295-296). Duration is 20 base, with a minimum of `11 - XLEVEL/2` (297).
- **Unfinished.** Two `TOOD` comments say the item should become "unsellable" on label and "not unsellable" on removal (285, 291). Nothing implements them here.

### 8. Organizing (`Organizing.java`)

- **Scope.** Targets the room, a container, a container in the mob's inventory, or a group member's inventory (`Organizing.java:269-306`). Another player's inventory needs group membership (287-294), and a room with an owner needs privilege (272-277).
- **Keys.** `OrganizeBy` is TAG, VALUE, CRAFTER, LEVEL, TYPE, NAME or WEIGHT (`Organizing.java:66-75`). Ties fall back to the name, and DESCENDING inverts the order (132-192).
- **Mechanism.** Items are detached from their owner (108-131), sorted (193), then re-appended in the new order (194-212). A container's contents keep their container pointer, so only the owner's list order changes.
- **Timing.** Duration is 15 base, minimum 2 (323). A spoiled result (`messedUp`) leaves items alone (101-106).

### 9. Lecturing (`Lecturing.java`)

- **Requirements.** The lecturer must know the topic skill (306-320). At least three students must be present, must have the topic skill, and must be able to hear the lecturer (`Lecturing.java:145-168`, 323-327). A monthly limit compares the area's calendar month with the month of the last lecture (279-298), and a lecture that is a year back gets a message about the year (295).
- **Duration.** `getDuration(15 × 60, mob, lowestQualifyingLevel(topic), 1)` (333). That is 900 ticks, about an hour at the default 4-second tick, before the level and XTIME reductions in `CommonSkill.getDuration`.
- **Flow.** The check runs at each tick. Fewer than three students cancels the lecture (86-92). Each tick has a 20% chance of a flavour line, one of ten (94-131). At tick 6, if the lecturer's own proficiency check failed, the lecture is cancelled with "No one is paying attention" (133-140).
- **Transfer.** On completion (`unInvoke`, 180-245), each student who has the topic skill is checked:
  - Students who are not appropriate to the lecturer's factions learn nothing (194-198).
  - Students who already know more than the lecturer, are at 100%, or are at their maximum learn nothing (200-210).
  - Otherwise the gain is `amt = lecturer proficiency − student proficiency`, clamped to between 1 and 5, and capped at 100 (213-220).
  - The lecturer gets `10 × amt` experience (221), and the student's proficiency rises by `amt` (224).
  - A student who reaches the maximum triggers a proficiency channel message and an achievement (226-233).
- **Persistence.** `Lecturing` does not override `text()` or `setMiscText()`. The `lastLecture` month therefore appears to live only in memory and to reset on reboot (unverified against `StdAbility`).

### 10. Mycology (`Mycology.java`, `MasterMycology.java`)

- **Input.** An unidentified raw mushroom stack: a raw material of `RESOURCE_MUSHROOMS`, with empty subtype, generic, and a name ending in " MUSHROOMS", "mushrooms" or "BUNDLE" (`Mycology.java:277-287`). A bundle is unbundled to one unit first (288-289).
- **Table.** `mycology.txt` has 43 species. Each has a frequency weight (sum 616), a value multiplier, and an optional spell list. `unInvoke` builds an `IntegerRangeMap` from the weights (148-169). Each species takes `freq + 1` slots, because `cur = cur + v + 1` (165). The roll is `dice.roll(1, max, 0)`, which returns 1 to max (`Dice.java:398-406`). Slot 0 is therefore never reached, a small bias against the first species (unverified in play).
- **Identification.** The species is picked (176-181). The item is renamed. Names ending in "shroom" get " mushrooms" (197-200). The value is multiplied (210), the description cleared (211), the subtype set to the species (212-213), and the spell list applied (214-215).
- **Secret identity.** If the raw item already has a secret identity (set elsewhere), that name is used with multiplier 1 (186-191).
- **Bulk (MasterMycology).** After one unit, the skill looks for more units with the same original name in inventory, using a `$name$` match (225), and continues while the proficiency check passes for each (217-236).
- **Timing.** Duration 15 base, minimum 2 (255). The master version uses 45 with minimum 6 (`MasterMycology.java:72-74`). A spoiled result gives "You lose your concentration" and keeps the unknown mushrooms (144-145).
- **Naming hack.** The name is cut with `name.substring(0, name.length() - 10)` (194). That works for the usual "a pound of mushrooms" (20 characters), but any other shape is cut at a fixed offset.

### 11. Masonry and Landscaping (configured `BuildingSkill` subclasses)

- **What they set.** Each class sets its recipe file, resource mask, main resource label, and sound. Masonry uses `ROCK|STONE`, `masonry.txt`, the label "Stone", and "stone.wav" (`Masonry.java:65-83`). Landscaping uses the same mask with `landscaping.txt` and the label "Material" (`Landscaping.java:65-83`).
- **Materials.** Masonry's basic materials are stone, limestone, flint, granite, obsidian and marble (`Masonry.java:92-111`). Landscaping defaults to "rock" (`Landscaping.java:100-108`) and can describe or title a room only if it is outdoors and not underwater (92-98).
- **Engine.** `BuildingSkill.invoke` (`BuildingSkill.java:1279`) handles LIST and INFO (1299-1378). Costs tagged VALUE are shown as currency at the land's value when the room has a land title, and as a bare percentage when it does not (1296-1297, 1332-1348). Costs tagged MONEY are shown as currency (1350-1361). Construction needs land ownership or the CMDROOMS security flag (1436-1437). Boardable areas refuse construction (1489-1494). Roofs and ceilings are demolished with a special path (1498-1536).
- **Data.** `masonry.txt` has 49 rows, and `landscaping.txt` has 74 (section "Content worth mining").

### 12. LockSmith (`LockSmith.java`)

- **Materials.** A new exit lock uses five units of metal (`LockSmith.java:303-308`). A new container lock uses three (333-338). Making a key for an existing lock uses one unit and halves the duration (391-393). Delocking uses no metal and yields "a broken lock", which is not returned (356-362, 86-87).
- **Key identity.** A new key code is `"" + Math.random()` (235), a random double rendered as a string. If the lock already has a key name, the new key copies it (`getBuilding`, 101-108). A key is set on the exit or container with `setKeyName` (172, 213), and the paired exit gets the same key unless the lock is one-way ("boltlock", 176-188).
- **Level.** The lock's level is set to `xlevel(mob)` (165, 182). Proficiency is made harder by five per level that the target is above `xlevel` (410-412).
- **Label.** `LABEL <text>` puts a label of at most seven characters with no spaces on the key (258-266, 379-381). The key is named "<label or metal> key".
- **Permission.** The door must be generic and have a door (293-302). Player instance areas can only be changed by the same kind of area (286-292). Owner privilege is required for exits and for containers in an owned room (312-318, 340-346).

### 13. Taming: `AnimalTaming.java` and `MountTaming.java`

- **Taming removes wildness.** A success removes `baseYield + abilityCode` random behaviours from the animal (`AnimalTaming.java:124-146`) and adds the TAMED ambiance (132). An animal with no behaviours left reports "already tame" (120-121).
- **Difficulty.** The proficiency check is modified by `-animal level + 2 × XLEVEL` (248). Duration is `getDuration(35, mob, animal level, 10)` (249).
- **Caged animals.** For a caged animal, `unCageMe` (`GenCaged.java:135-172`) builds a fresh mob from the cage's stored text. The cage text is not changed. After success the animal is re-caged with its new text (`AnimalTaming.java:148-151`). A failed attempt therefore leaves the cage unchanged.
- **Mounts.** `MountTaming.mayITame` (`MountTaming.java:59-75`) allows only animals whose race is in `CMLib.utensils().getFavoredMounts(mob)` or whose racial category matches. Other mount skills read `resources/skills/racialmounts.txt` (`Abilities/Fighter/Fighter_RacialMount.java:146-149`). That `getFavoredMounts` reads the same file is unverified.
- **Tick hazard.** If the animal is a mob and the taming mob has no location, `tick()` calls `unInvoke()` and then dereferences `mob.location()` without returning (`AnimalTaming.java:81-86`).

### 14. Meat curing (`MeatCuring.java`)

- **Target.** Flesh-material food only (`MeatCuring.java:138-143`). Rotten food cannot be cured (145-149), and food that is already cured cannot be cured again (151-155).
- **Success.** `Prayer_Purify` is added as a permanent effect (`unInvoke`, 105-107). `Prayer_Purify` sets `decayTime` to zero each time stats are recalculated (`Abilities/Prayers/Prayer_Purify.java:88-93`), so the food stops rotting.
- **Failure.** `Prayer_Purify` is started with a 20-tick timer (109). Duration is `getDuration(1 + weight × 2, mob, 1, 3)` (165).

### 15. Merchant ("Marketeering", `Merchant.java`)

- **Attachment.** `Merchant` is a `CommonSkill` that implements `ShopKeeper`. It is always on (`isAutoInvoked`, 70-73; `canBeUninvoked` false, 76-79). It can attach to mobs, rooms, exits, areas and items (`canAffectCode`, 88-91). `autoInvocation` skips ShopKeeper mobs (955-960).
- **State.** The stock is serialised as the shop's XML (`text()` 138-141, `setMiscText` 185-191). Other settings are the budget, the default of 100000 per day (115), currency, devalue rate, prejudice, ignore mask, pricing adjustments and an item zapper mask (105-115).
- **Who the shopkeeper is.** `deriveMerchant()` (400-438) returns the mob if the ability sits on a mob. For an owned item it returns the owner. For a gettable item with no owner it returns nothing. For a room, exit or area it creates a static "the shopkeeper" or "the shop" mob and places it in the room. The budget is topped up on that mob (432-436).
- **Trade.** `okMessage` (441-530) vets buy, view, sell and value through `CMLib.coffeeShops()` evaluation calls (481, 497). The sell budget is `budgetRemaining × 100` (478-479). Pawn offers are spoken with a price (607-611). When a merchant mob dies, its stock goes to its inventory or is brought back to life (513-526).
- **Player command: `market`** (`Merchant.java:780-952`):
  - `market list` shows stock (791-797).
  - `market remove <item>` takes an item back out of stock (798-829).
  - A trailing integer or a gold amount is the price (833-857).
  - `all` or a `.ALL` suffix takes every identical stack (861-899).
  - Containers with contents cannot be sold, and stolen items are refused (885-890, 716-728).
  - Without a stored price and without a price in the command, the sale is refused (907-911).
  - The sale needs proficiency (916-920). Items go into stock with the price or the default (929-932). Mobs are killed or destroyed (939-945).
- **One active store.** Only the first ShopKeeper effect on an object is active (`isActive`, 730-748). `makeActive` moves the current one to the front and reports "is now your active store" (750-777).
- **Weight.** Stock weight is added to the mob's weight (`affectPhyStats`, 194-198).

### 16. Master craft tiers and the cooking pattern

- **Level-band rules.** Each master tier adds a `masterCraftCheck()` that restricts which items it may make, on top of the base skill's recipes:
  - `MasterArmorsmithing.masterCraftCheck` (`MasterArmorsmithing.java:73-88`) refuses items below level 30 that carry a wear or wield adjuster effect.
  - `MasterWeaponsmithing` (`MasterWeaponsmithing.java:79-87`) accepts items at level 31 to 60, or any item whose ID starts with MASTER.
  - `LegendaryWeaponsmithing` (79-87) accepts items at level 61 and up, or any item whose ID starts with LEGENDARY.
  - `MasterTailoring` (`MasterTailoring.java:67-75`) accepts items whose name starts with or contains DESIGNER, or any item at level 31 and up.
  - `MasterCostuming` (`MasterCostuming.java:67-73`) accepts items at level 31 and up.
- **Cooking-family concurrency.** `MasterBaking`, `MasterCooking`, `MasterDistilling` and `MasterFoodPrep` each use `getDuration(60, mob, 1, 8)` and `baseYield() = 2` (`MasterBaking.java:72-78`). Their `invoke()` gives the effect a numbered ID (`cookingID`, 44, 87-103), so up to five such effects can run together. The list of existing ones is kept in `noUninvokes` (61, 66, 103-117), and `CommonSkill.invoke` lets them coexist through `getUninvokeException()` (`CommonSkill.java:890-902`).
- **Chop (MasterFoodPrep).** `chop <food>` splits a food item in two repeatedly, halving weight and nourishment at each step (`MasterFoodPrep.java:87-157`). The split is not reliable. Problems section, item 2, traces two cases.

### 17. Gathering master tier (table)

Base values are from the base classes in other buckets, cited where the master file calls them.

| Master class | Duration (base ticks, minimum) | Base class value | Other |
|---|---|---|---|
| MasterChopping | 100, min 37 (`MasterChopping.java:62`) | Chopping 40, min 15 (`Chopping.java:89`) | yield 3 |
| MasterDigging | 150, min 37 (`MasterDigging.java:62`) | Digging 60, min 15 (`Digging.java:89`) | yield 3 |
| MasterDrilling | 90, min 25 | Drilling 35, min 10 | yield 3 |
| MasterFarming | 113, min 37 | Farming 45, min 15 | yield 3 |
| MasterFishing | 113, min 37 | Fishing 45, min 15 | yield 3 |
| MasterForaging | 80, min 18 | Foraging 45, min 10 | yield 3 |
| MasterGardening | 113, min 37 | Gardening 45, min 15 | yield 3 |
| MasterMining | 125, min 37 (`MasterMining.java:62`) | Mining 50, min 15 (`Mining.java:89`) | yield 3 |
| MasterTrawling | 265, min 90 | Trawling 135, min 45 | yield 3 |
| MasterShearing | `(weight / (10 + XLEVEL)) × 2`, min 25, cap 100 (`MasterShearing.java:60-68`) | Shearing `weight/(10+XLEVEL)`, min 10, cap 40 (`Shearing.java:75-81`) | yield 3 |
| MasterButchering | `round((weight / (10 + XLEVEL)) × 2.5)`, min 7, cap 100 | Butchering (other bucket) | yield 3 |
| MasterHerbology, MasterFloristry, MasterMycology | 45, min 6 (`duration(mob)` override) | Herbology and Floristry 15, min 2 | not limited to one unit |

## Content worth mining

- **Painting styles.** `skills/painting.txt` has 910 rows. They are 26 subjects (finger-painting, sketch, drawing, painting, colour-painting, scribble, image, portrait, watercolour-painting, doodle, contour, panorama, pastel-painting, outline, cartoon, fresco, shaded-painting, caricature, illustration, mural, tinted-painting, silhouette, pen and ink, ink wash, splatter-painting, depiction) times 35 style words. Style words are gated by expertise tier 0 to 10. Tier 1 holds 23 cultural or racial styles, and tier 5 holds three styles including gender-coded ones. Tier 10 holds the "surreal" family. Painting levels run from 1 to 87, ticks from 12 to 449, and base values from 6 to 8792 (columns 2 to 4). 650 rows carry a zapper mask that restricts them to a race, racial category or gender, for example `-RACECAT +ELF`. The reusable part is the 26-subject list with its style tiers.
- **Lacquer colours.** `skills/lacquering.txt` has 451 colour names over 21 distinct levels, from 1 to 91. Examples: red, green, blue and white at level 1; yellow, purple, cyan and grey at level 5; silvery and fuchsia at level 10; maroon and dark green at level 15; black at 35. Many colour names are coined or culturally referenced, which is useful for flavour text.
- **Dye and lacquer designs.** `masterdyeing.txt` and `masterlacquering.txt` have 41 designs each, all with levels 1 to 40. The file has one solid design (one colour), 20 two-colour designs at levels 1 to 20, and 20 three-colour designs at levels 21 to 40. Examples: solid, striped, banded, pinstriped (`1Cc21*`), widestriped, tie-dyed (four alternative masks), paisley and camouflaged (nine alternative masks). The mask strings are compact and show a usable pattern language.
- **Mushrooms.** `skills/mycology.txt` has 43 species with weights summing to 616 and value multipliers from 1 to 100. Button is the rarest and most valuable (weight 1, multiplier 100). Russula, enoki, brittlegill, fieldshroom, shiitake and oystershroom are common. Death cap and destroying angel carry `Poison_Heartstopper`, and lepidella, foolshroom, webcap, falsemorel and falseparasol carry `Poison_Sickening`.
- **Construction data.** `skills/masonry.txt` has 49 rows and `skills/landscaping.txt` has 74. Examples: `wall` (level 1, 60 ticks, cost 250, directional indoor wall), `roof` (125 ticks, 500, turns an outdoor place indoors, room type StoneRoom), `archway` (level 5, 25 ticks, 200, exit type GenExit), `plains` (5 ticks, 1000, VEGETATION), `road` (38 ticks, 500, ROCK, OUTDOOR ROOM).
- **Leatherwork.** `skills/leatherworking.txt` has 55 rows: 46 armour (cestus, collar, belt, cuff, armband, slipper, headband, chaps, choker, bracer, sandal, skirt, mask and more), six flail weapons (whips, scourge), two waterskins and a pouch. `masterleatherworking.txt` has the same 55 rows with higher armour and capacity numbers (for example cestus armour damage 1 to 3, chaps 2 to 11).
- **Other data.** `skills/masterdyeing.txt`, `masterlacquering.txt`, `mastersmokerings.txt` and `mastertailor.txt` are used by other master tiers. `skills/racialmounts.txt` is shared with other mount skills.

## Notable design ideas

- **Stage variants by prefix and level offset.** `LeatherWorking.loadRecipes` (`LeatherWorking.java:185-198`) multiplies each base recipe into prefixed copies, adding a level offset and a stage record that sets multiplier, damage, attack and armour. One table yields a whole progression of items, and the stage is read back from the name.
- **Tiers keyed to level bands.** The leather pair divides the catalogue at level 30 and 31 (`LeatherWorking.java:289`, `MasterLeatherWorking.java:246`). Weapon and armour tiers do the same at 30, 31, 60 and 61. Gating is by level, not by a separate unlock.
- **Mask grammar.** `MasterPaintingSkill.applyColorMask` (62-246) implements a small language of positional anchors and alternatives, so one row describes a pattern across any name length. Random alternatives (109) give natural variety.
- **Room depletion.** Yield falls 20% per harvest per room and recovers after 30 minutes (`GatheringSkill.java:69-70`, 79, 144-146). It is a cheap, effective way to keep a world resource from being stripped.
- **Teaching as transfer.** `Lecturing` moves `clamp(teacher − student, 1, 5)` percentage points per lecture (`Lecturing.java:213-220`), so a lecturer can only pass on what they know, and a student who is already ahead gains nothing. The monthly rule (279-298) limits abuse without any extra data.
- **Taming by subtraction.** Taming removes random behaviours from the animal (`AnimalTaming.java:136-146`) and marks it TAMED, which is a clear and easy-to-port model.
- **Caged animals as text.** The cage stores the animal as serialized text (`GenCaged.unCageMe`, 135-172), so a failed taming leaves the cage intact and a success writes the modified animal back.
- **Paint jobs as reversible edits.** Paint saves the original name on an `ExtraData` effect (`PaintingSkill.java:368-387`), so removing paint restores it exactly. The paint itself is in-band colour codes.
- **The shop as an attachment.** `Merchant` (canAffectCode 88-91) can be placed on a room, exit, area, item or mob, and it creates a shopkeeper only when it needs one (`deriveMerchant`, 400-438). The shop is data, not a fixed NPC.
- **Re-entrant prompts.** Painting asks questions by calling itself again with hidden tokens (`Painting.java:453-456`, 194-216). It is a small, clever way to keep a multi-step command in one code path.

## Problems and cruft

1. **Painting consumes the canvas before validating the action.** In the keyword-and-description path, `canvasI.destroy()` runs at `Painting.java:486` and the building is created at 472-485. The action is only validated later, in `super.invoke` (626). If that fails (in combat, no mana, cannot see, cannot move), `building.destroy()` runs at 628 and the canvas is already gone. Confirmed by reading; not executed.
2. **MasterFoodPrep chop loses or duplicates food** (`MasterFoodPrep.java:116-157`). Traced by hand:
   - A 2-pound food: the original is halved to 1 pound and renamed "a bit of ...", and the copy is never added to the room, so half the food disappears. The loop's last `F` equals `existsF`, so the re-add at 155 is skipped entirely.
   - A 3-pound food: two pieces are lost, and one piece remains.
   - A 4-pound food: the original is added to the room a second time (`existsF.owner().addItem`), so it appears twice.
   The test `F != existsF` at 155 does not track which pieces are new.
3. **Merchant's refusal message names the wrong party.** `shopperM.tell(shopperM, null, null, "You'll have to talk to <S-NAME> about that.")` (`Merchant.java:473`). `tell()`'s first argument is the source (`MOBS/interfaces/MOB.java:291`), so `<S-NAME>` resolves to the shopper, not the merchant. Likely a bug; the message is unverified in play.
4. **Lecturing does not persist its monthly limit** (`Lecturing.java:246-254`, no `text()` override). See section 9. Unverified against `StdAbility`.
5. **Paint colour list is not lowercased as its name claims.** `PaintingSkill.java:173-174` checks the lowercased name but adds the original `getName1()`, so any colour name with capitals is added unlowered and is never matched by the lowercased `startsWith` in dye parsing. Whether any name has capitals is unverified. The comparator at 179-190 also returns 0 for equal-length names, which is not a consistent total order.
6. **Empty-list crash, theoretical.** `colors.substring(0, colors.length()-2)` throws when nothing qualifies (`Lacquerring.java:135`, `MasterDyeing.java:152` and `180`, `MasterLacquerring.java:153` and `181`). In practice the level-1 lacquer colours (expertise 0) always qualify.
7. **Mycology fragility.** The fixed 10-character name cut (`Mycology.java:194`) assumes a specific name shape. The reroll loop (`176-181`) never ends if no table row has a name but the list has more than two rows. Both are unlikely with the shipped data.
8. **Labelling.** The `replace` keyword is read and discarded (`Labeling.java:226-232`). `getNextTag` uses `charAt(length-1)` on a possibly empty uppercase set (153) and an unguarded `substring` in the suffix loop (155-159), which can throw for IDs with no capitals. Two TODO comments are unfinished (285, 291).
9. **Taming tick.** `AnimalTaming.java:81-86` calls `unInvoke()` and then keeps going, so `mob.location()` may be dereferenced as null (line 86) if the location is gone.
10. **Leatherwork destroys materials before checking the class.** In `LeatherWorking.java`, materials are destroyed at 590-593, then `CMClass.getItem(...)` is checked at 595-601. A misconfigured recipe consumes materials and produces nothing.
11. **Dead code and typos.** `MasterLacquerring.java:330` says "dyeing", and the leather doubling at 338-339 can never apply, because lacquer does not accept leather. `MasterDyeing.java:313-314` is unreachable for the same reason. `MasterLeatherWorking.java:405` says "mleatherworkd". `Painting.java:74` declares `supportedResourceString()` as "LEATHER", but canvases must be cotton or silk (307-310). The painting code does not use that string, so the declaration looks like a copy-paste.
12. **Mask code has an unused parameter and dead writes.** `applyColorMask` takes `colorNames` but uses the field `colorsNames` (`MasterPaintingSkill.java:62-66`). `addPaintJob` builds a coloured `writing` string (261-271) that is only used in the fallback at 273-277.
13. **Static state shared across skills.** `CommonSkill` caches `getStatCodes()` in a static field (`CommonSkill.java:999-1015`), so the first subclass's result may be returned to every other subclass (impact unverified). `GatheringSkill` keeps one static depletion map for all gatherers (69-70). `Labeling` keeps one static prefix map (69).
14. **Mining accepts rooms it cannot mine.** Precious, glass and sand rooms are accepted at invoke (`Mining.java:183-188`) but excluded from the yield test (198-200). The result is wasted time and cost.
15. **Bundle returns success on failure.** `GatheringSkill.bundle` returns true at 511 even when `R.show` refuses the bundle (479-511).
16. **Session and follower assumptions.** Painting needs a session or a follower (`Painting.java:224-230`). Labelling's counters are per skill instance, so two players can produce the same tag. Organizing needs group membership to touch another mob's items (`Organizing.java:287-294`). The shop's pricing lives in the shared library (`CMLib.coffeeShops()`).
17. **Crude wording.** A failure message in `Lecturing.java:242` uses mild profanity. Nothing else here is adult or violent beyond the poison spells on some mushrooms.

## Takeaways for a Python SUD

- **Recipes as rows, stages as generated variants.** Load a TSV of recipes with name, level, ticks, cost, output class, expertise tier, requirement expression and spell list. Generate stage copies at load time, as `LeatherWorking.loadRecipes` does, but store the stage as a field, not a name prefix:

  ```python
  @dataclass(frozen=True)
  class Stage:
      name: str; level_offset: int; mult: int; dmg: float; attack: int; armour: float

  def stage_variants(recipes, stages):
      return [replace(r, name=f"{s.name} {r.name}", level=r.level + s.level_offset, stage=s)
              for r in recipes for s in stages]
  ```

- **Validate every resource before touching it.** Port the timed action as one object (`start`, `tick`, `finish`): check the cost and the place first, consume materials last, and never destroy the canvas or ore until the final step. That fixes the Painting and LeatherWorking problems at once.
- **Tick-driven activity with aborts.** Keep the pattern from `CommonSkill`: per-tick checks for combat, movement and incapacity, a "almost done" flag at a fixed number of ticks, and a single `aborted` flag that discards the result. In a turn-based game the tick is a turn.
- **Room depletion.** Port `GatheringSkill` as a plain dict: `depletion[room_id] = {"resource": r, "harvests": n, "expires": t}`, with yield `base × max(0, 1 − 0.2 × n)` and a 30-minute (or N-turn) window. Keep it per resource type if two gatherers share a room.
- **Teaching transfer.** `teach(teacher, students, topic)`: each student gains `clamp(teacher_prof − student_prof, 1, 5)` points, skipped if the student is already ahead or at the cap, with the teacher earning a small XP bonus. Use the game calendar for the monthly rule.
- **Taming by removal.** `animal.behaviours.discard(rng.choice(list(animal.behaviours)))` for each success, and an `is_tame` check when the set is empty. Mount restrictions become a set of species the player may ride.
- **Shops as attachments.** Give any room, exit, item or mob an optional `Shop` component with a stock dict, a budget, and buy and sell methods. Create a shopkeeper NPC only when a room has a shop. Keep the price adjustments as simple multipliers and drop the zapper and prejudice machinery unless you need it.
- **Locks and keys.** Store a key string on every lock and key. A new key copies the lock's string. Use `secrets.token_hex(4)` for new key strings, not a float.
- **Gating by level bands.** Keep the 30/31 and 60/61 bands as a single function per tier, and show the band in the tier's help text. The master tier should change duration and yield, not add new rules.
- **Drop for a single player.** Drop the session prompts and the follower checks; turn prompts into function arguments or a short dialogue. Drop shared static caches and replace them with per-game objects. Drop the full mask grammar: keep a few named patterns (solid, striped, banded, checked) plus two colour slots, and port the 41 designs as those patterns.

## Coverage

- **Read in full (bucket files):** `Painting.java`, `Lacquerring.java`, `MasterPaintingSkill.java`, `MasterDyeing.java`, `MasterLacquerring.java` (lines 180-355 and a diff against MasterDyeing), `LeatherWorking.java`, `MasterLeatherWorking.java` (diff against LeatherWorking, plus lines 236-305), `Merchant.java`, `Labeling.java`, `Lecturing.java`, `Organizing.java`, `Mycology.java`, `Mining.java`, `MeatCuring.java`, `LockSmith.java`, `MountTaming.java`, `Masonry.java`, `Landscaping.java`, `MasterFoodPrep.java` (class body, including the chop path), `MasterBaking.java` (class body), `MasterArmorsmithing.java`, `MasterTailoring.java` (body), `MasterSmokeRings.java`, `MasterDigging.java`, and the overrides in the other Master gatherers, which were read by grep.
- **Skimmed (bucket files):** `MasterCooking.java` and `MasterDistilling.java` (diffed against `MasterBaking`), `MasterCostuming.java` (`masterCraftCheck`), `MasterWeaponsmithing.java` and `LegendaryWeaponsmithing.java` (diffed), and the Master gatherers and Master Herbology, Floristry and Mycology overrides (grep).
- **Read in full (other buckets, for interactions):** `CommonSkill.java`, `GatheringSkill.java`, `PaintingSkill.java`, `AnimalTaming.java`.
- **Read in part (other buckets):** `CraftingSkill.java` (121-133, 279-283, 302, 361, 471, 539, 709, 2011), `EnhancedCraftingSkill.java` (102, 465-563, 685-795), `BuildingSkill.java` (197, 233, 1279-1400, 1436-1537), `Items/Basic/GenCaged.java` (135-185), `Prayers/Prayer_Purify.java` (88-93), `Libraries/RawCMaterial.java` (913-968), `core/collections/IntegerRangeMap.java` (211-260), `Libraries/Dice.java` (398-406).
- **Data read:** `skills/painting.txt`, `lacquering.txt`, `masterdyeing.txt`, `masterlacquering.txt`, `mycology.txt`, `masonry.txt`, `landscaping.txt`, `leatherworking.txt`, `masterleatherworking.txt` (header and sampled rows, counted with awk).
- **Not read:** `Abilities/Skills/Skill_Write.java` (only confirmed to exist), `StdAbility` text and persistence, `CoffeeShop` implementation and `CMLib.coffeeShops()` evaluation code, `CMLib.utensils().getFavoredMounts`.
