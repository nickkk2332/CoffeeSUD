# Senses, flags and miscellaneous utilities

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b015` (Senses, flags and miscellaneous utilities) · 4 files · 9627 lines of Java.

Path convention: Java paths below drop the `com/planet_ink/coffee_mud/` prefix, so `Libraries/Sense.java:1482` means `com/planet_ink/coffee_mud/Libraries/Sense.java`, line 1482. Other paths are relative to the CoffeeMUD folder.

## Summary

Nearly every question the engine asks about a creature or object (is it hidden, asleep, flying, good or evil, can this observer hear it) is answered from one or two integer bitmasks on that object. Those masks are recomputed each time from base values plus every active effect. `CMFlagLibrary`, implemented by `Sense`, is the single place where the state is read, and it also holds the rules for who can see, hear, smell or breathe what. `CMMiscUtils`, implemented by `CoffeeUtensils`, is a grab bag of rules with nowhere else to live: loot ruin and NPC rejuvenation, race mixing, resurrection and XP refunds, the status-prompt template engine, armour and wearability checks, traps, costs and aliases. For a single-player text game the state-as-effects model and the ordered perception rules are the parts worth carrying over; most of the multiplayer and sysop machinery can go.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Libraries/interfaces/CMFlagLibrary.java` | Interface of about 185 state and sense tests. Also the `Disposition` enum (IS_* bits, 1656-1682) and the `Senses` enum (CAN_* bits, 1856-1881), each with localized phrases. | 2045 |
| `Libraries/Sense.java` | `Sense`, the `CMFlagLibrary` implementation. Visibility (`canBeSeenBy` 1482), hiding scores (1441-1479), hearing (1196-1243), smell and breath (191-264), alignment (671-1032), liveness (1341-1414), water and flight (1730-1788), room types (2046-2262), aura text (2592-2738). | 3140 |
| `Libraries/CoffeeUtensils.java` | `CoffeeUtensils`, the `CMMiscUtils` implementation. Loot and rejuvenation (824-1265), resurrection (1551-1683), `buildPrompt` (1941-2781), armour and wearability (1061-1353), traps (340-422), costs (1686-1907, 3557-3841), race mixing (3100-3442), aliases (3844-3876). | 3877 |
| `Libraries/interfaces/CMMiscUtils.java` | Interface of 46 methods. Its class comment (39-48) calls the library one that "should not exist". | 565 |

Supporting definitions read: `Common/interfaces/PhyStats.java` (IS_* 553-599, CAN_* 397-442, SENSE_* 475-521, NO_REJUV 48, ONE_JUV 50, `Ambiance` 645); `Items/interfaces/RawMaterial.java` (densities in the data rows, accessor 1484); `Abilities/interfaces/Ability.java` (ALL_ACODES 701, FLAG_* 946-1024, QUALITY_* from 1083); `Behaviors/interfaces/Behavior.java:187-195` (FLAG_*); `MOBS/interfaces/MOB.java:1055-1100` (`Attrib`); `core/CMProps.java:546-579` (`ListFile` keys); `resources/lists.ini:1774-1792`; `coffeemud.ini` (369-374, 415-427, 716-734).

## How it works

### 1. The flag model

**Two bitmasks per object, plus a base copy.** Every `Physical` keeps `basePhyStats()` (set by builders or code) and `phyStats()` (derived). Two `int` masks live in `PhyStats`:

- `disposition()` holds the IS_* states (`Common/interfaces/PhyStats.java:553-599`), for example `IS_HIDDEN=2`, `IS_INVISIBLE=4`, `IS_SLEEPING=512`, `IS_FLYING=2048`, `IS_BOUND=131072`.
- `sensesMask()` holds the CAN_* senses (`PhyStats.java:397-442`), for example `CAN_NOT_SEE=1`, `CAN_SEE_HIDDEN=2`, `CAN_NOT_HEAR=512`, `CAN_NOT_MOVE=1024`. The same mask also carries item and room properties under the SENSE_* names (`PhyStats.java:475-521`), such as `SENSE_ITEMNOTGET=16`, `SENSE_ITEMNODROP=32`, `SENSE_ROOMNOMOVEMENT=1024`.

A third channel is a set of string tags in `PhyStats.Ambiance` (`PhyStats.java:645`). The "seems" tags such as `#GOOD`, `#LAW`, `#MODERATE` describe appearance. Suppression tags such as `-HIDDEN` silence an aura, and markers such as `@FLOATING` and `@TAMED` are plain flags. In `getDispositionBlurbs` a `#` tag is shown only when the matching `@` tag is also present, and never for LAW or CHAOS (`Libraries/Sense.java:2679-2687`).

**Recomputed from base plus effects.** `StdMOB.recoverPhyStats()` (`MOBS/StdMOB.java:798-840`) copies base into derived and then applies, in order: the room (which applies the area, `Areas/StdArea.java:1333-1350`), the mount, the deity, classes, race, carried items (line 826), every effect through `eachEffect(affectPhyStats)` (line 827), factions, and a follower light rule (830-840, whose comment reads "BUT WHY?"). An effect changes flags only in its `affectPhyStats`. The hide skill shows the pattern (`Abilities/Thief/Thief_Hide.java:189-193`):

```java
affectableStats.setDisposition(affectableStats.disposition()|PhyStats.IS_HIDDEN);
if(CMLib.flags().isSneaking(affected))
    affectableStats.setDisposition(affectableStats.disposition()-PhyStats.IS_SNEAKING);
```

A temporary state therefore disappears when its effect expires, with no cleanup. Builder-set flags work differently. The setters (`Libraries/Sense.java:397-515`, for example `setGettable` at 455) write to both base and derived masks, so they survive a recompute.

**Bits to text.** `Disposition` gives each bit `getIsDesc()`, `getState()` and `getVerb()` (`CMFlagLibrary.java:1702-1714`). `Senses` gives `getDesc()`, `getState()` and `getVerb()` (1901-1920). The phrases are localized lazily (1721-1847 and 1922-2043). `Sense.getDisposition(name)` and `getSenses(name)` (2443-2478) accept the enum name or a suffix of it, such as `HIDDEN`.

### 2. Visibility: `canBeSeenBy`

`Sense.canBeSeenBy(seen, seer)` (`Libraries/Sense.java:1482-1573`) is the central perception test. The grep count is about 850 matching lines across about 440 files. Its rules run in this order:

1. Same object, or a null target: visible (1484-1487).
2. Seer has the `SYSMSGS` attribute (`MOB.java:1062`): visible (1489-1491).
3. Seer can see at all. `canSee` (50-53) needs a non-null seer, no sleep, and no `CAN_NOT_SEE`. A null seer therefore sees nothing (1493-1494).
4. Non-Physical targets are visible (1496-1497).
5. A target with `IS_NOT_SEEN` is invisible unless asleep (1501-1502, `isSeeable` 518-521).
6. An invisible target needs `CAN_SEE_INVISIBLE` (1504-1505).
7. A hidden target that is not a room (1507-1523):
   - an item needs `CAN_SEE_HIDDEN_ITEMS` or `CAN_SEE_HIDDEN` (`canSeeHiddenItems` 79-84);
   - a mob needs `CAN_SEE_HIDDEN`, or else `getDetectScore(seer) >= getHideScore(seen)` (section 3);
   - any other Physical, such as an exit, needs `CAN_SEE_HIDDEN`.
8. Seer is in a dark room and the target is not a room (1525-1546). The target is visible if it glows, or the seer is a light source, or the seer has `CAN_SEE_DARK`, or the seer has infravision (`CAN_SEE_INFRARED`) and the target is a non-golem mob, or the target is the seer's victim and the seer has `CAN_SEE_VICTIM`, or the moon is `FULL` and visible from the room. Otherwise it is not visible.
9. Lit room: visible (1547).
10. Target is a room (1549-1571). In a dark room it is visible if the moon is `FULL`, `WAXGIBBOUS` or `WANEGIBBOUS` (1553-1561), or the seer is a light source, or the seer has `CAN_SEE_DARK`.

`isHidden` (530-540) returns false for a hidden mob that is in combat. Attacking therefore breaks hiding for every test that goes through it. Rule 8 uses the moon phase `FULL` only (1541-1542), while rule 10 accepts three phases (1555-1561). See Problems.

**Barely seen.** `canBarelyBeSeenBy` (1576-1612) is a middle tier between seen and unseen. A non-room target in a dark room is barely seen when the moon is up and not new (1580-1589). A room in the dark is barely seen in the crescent and quarter phases, and in `BLUE` (1597-1607), by a different phase table.

### 3. Hiding and detection

`getHideScore` (`Libraries/Sense.java:1441-1462`) and `getDetectScore` (1465-1479) form a contest, for mobs only:

| Score | Formula as coded | Lines |
|---|---|---|
| Hide | `level`, plus `DEX/2` for a mob | 1445-1447 |
| Hide, base disposition has `IS_HIDDEN` | + 100 | 1448-1449 |
| Hide, mob not base-hidden | + `getSave(STAT_SAVE_DETECTION)` - `round(sqrt(height))` when height >= 0 | 1450-1456 |
| Detect | `WIS/2`, plus 100 if base `sensesMask` has `CAN_SEE_HIDDEN`, else + `getSave(STAT_SAVE_OVERLOOKING)` | 1469-1473 |
| Detect | - `round(sqrt(height))` when height >= 0 | 1474-1475 |

The target is seen when `getDetectScore(seer) >= getHideScore(seen)` (1519-1521). The thief hide effect adds its bonus to save-detection (`Thief_Hide.java:182-185`) and sets only the derived `IS_HIDDEN` (189-193). A hidden thief's score therefore comes from the save-detection branch, not the +100 branch. The two scores use base masks, while `canBeSeenBy` uses derived masks (`isHidden` 534, `canSeeHidden` 75). See Problems.

### 4. Hearing, smell, breath and taste

- **Moving sounds** (`canBeHeardMovingBy`, 1196-1216). A cloaked target that is not seeable can be heard only if it is a mob no higher than the hearer's level and the hearer is a system operator (1204-1210). The hearer must pass `canHear` (`CAN_NOT_HEAR` cleared, 147-150). A sneaking target also needs the hearer to pass `canSeeSneakers` (1213-1214).
- **Speech** (`canBeHeardSpeakingBy`, 1219-1230) needs only `canHear`.
- **Who notices movement** (1233-1242). `canSenseMoving` is hearing or seeing. `canSenseEnteringLeaving` is hearing only.
- **Smell** (191-218). `canSmell(M)` needs breathable air and no `CAN_NOT_SMELL`. `canSmell(M, target)` builds a `TYP_AROMA` message and asks the target's room `okMessage` (212-213), so a room, or any behaviour or effect in it, can veto the smell. Non-mob targets get a temporary factory mob that is destroyed afterwards (207, 216).
- **Breath** (233-264). `canBreathe` checks `CAN_NOT_BREATHE`. `canBreatheThis` passes when the atmosphere code is negative, the race has no breathables list, or `Arrays.binarySearch` finds the code (257). That search assumes a sorted array, which was not verified.
- **Taste and speech** (221-230) check `CAN_NOT_TASTE` and `CAN_NOT_SPEAK`.

### 5. Liveness and action gating

- `isAliveAwakeMobile(mob, quiet)` (1361-1390) fails for dead or negative HP, for sleeping, and for `!canMove`. A non-quiet call also tells the mob why: "You are DEAD!", "You are sleeping!", "You can't move!" (1374-1387). A quiet call returns silently (1365-1373).
- `isAliveAwakeMobileUnbound` (1341-1358) adds "You are bound!" for `IS_BOUND`, and "You can't move!" for `isBoundOrHeld` (1407-1414). The latter also counts effects with `FLAG_BINDING` or `FLAG_PARALYZING` (`Ability.java:946`, `970`). The first test appears in roughly 300 matching lines.
- `canActAtAll` (1615-1627) needs a living, awake, mobile mob that is in the game. `canFreelyBehaveNormal` (1630-1643) also excludes mobs in combat, mobs following a non-player, and mobs below half HP (`curState` HP < `maxState` HP / 2, line 1638). Behaviours call it before acting, for example `Behaviors/ItemIdentifier.java:87`.

### 6. Alignment

The good and evil tests look at two sources, and law and chaos at a third.

- **Disposition bits.** `isGood` (785-795) and `isEvil` (671-682) check `IS_GOOD` and `IS_EVIL`. When both bits are set, `isNeutral` returns true (1019-1023) and the state list shows the "sacred" phrase (2981-2986).
- **Faction ranges** for `FactionMember`. `isReallyGood`, `isReallyEvil` and `isReallyNeutral` (653-669, 710-726, 984-1012) read each faction range's `alignEquiv()`. For neutrality, the first non-null range decides.
- **Ambiance tags** for law and chaos only. `isLawful` (797-816) reads `SEEMS_LAWFUL`, and `isChaotic` (819-837) reads `SEEMS_CHAOTIC`. Good and evil never read their `SEEMS_*` tags, so "seems good" affects only display (`getAlignmentName`, 933-936).

`isNeutral` (1015-1032) is true when neither good nor evil applies. `getAlignmentName` and `getInclinationName` (925-982) produce the words shown by look and score output.

### 7. Water, flight and falling

- **Floating** (`isWaterWorthy`, 1730-1776). An item floats when its weighted average density is at most 1000, the density of water (`Items/interfaces/RawMaterial.java:674`). The densities run from air 0 (`RawMaterial.java:793`) and wood 920 (659) to iron 7900 (644) and gold 19320 (648). Contents are summed in 1753-1758. A container is padded to capacity with air counted at density 1 (1759-1767). A falling item never floats (1771-1772). A swimming item floats unless it is subject to wear and has no uses left (1734-1736). A water-based mob floats (1739-1742). The loop at 1756-1757 has a defect, covered under Problems.
- **Flight** (`isInFlight`, 1779-1788) is flying, or riding something that flies. `isFloatingFreely` (1434-1438) requires the `GravityFloat` effect and the `IS_FLOATING` ambiance tag.
- **Falling** (`getFallingDirection`, 1114-1123) returns UP if the Falling effect's `REVERSED` stat is set, otherwise DOWN.
- **Water rooms** (`isWateryRoom`, `isDeepWateryRoom` and related, 2086-2164) combine domain types with a liquid atmosphere test.

### 8. Rooms, areas and consistency

- `isInWilderness` (1142-1160) and `isACityRoom` (1163-1193) are domain checks with weight tests.
- `isDrivableRoom` (2062-2083) uses the room's own weight and height as a tunnel-size proxy (2076-2080). `StdArea` adds the area's weight to every room it affects (`Areas/StdArea.java:1350`, commented "well, that's weird"), so room weight is overloaded.
- Darkness comes from the area. Outdoors at night, or in blizzard or dust-storm weather, a room with a sky gets `IS_DARK` (`StdArea.java:1337-1346`), unless the area itself is flagged as a light source (1345). That result feeds rule 8 of `canBeSeenBy`.
- `validCheck` (1245-1338) tests consistency. A room's area must not be destroyed. A mob must be in its room. An item must be contained by its owner. `StdMOB` runs the check on monsters every 60 ticks (`MOBS/StdMOB.java:4082-4083`) and kills a monster whose report starts with `*` when it is stranded (4084-4095). `StdItem` runs it every 10 + 1d100 ticks (`Items/Basic/StdItem.java:755-756`) and destroys any item it reports (757-760).

### 9. Items: handling flags and checks

- `isGettable`, `isDroppable` and `isRemovable` (295-310) read `SENSE_ITEMNOTGET`, `SENSE_ITEMNODROP` and `SENSE_ITEMNOREMOVE`.
- `isWriteable` (279-292) requires readable, plus paper, hide, hemp or silk material, or a signpost clan item.
- `isSavable` (383-394) is false for unsavable items and for items inside unsavable containers.
- `isEnspelled` (439-452) is true when an effect is a spell, prayer, chant or song that can be uninvoked, is not automatic, and is not saved.
- `isKeptOverDeath` (376-380) reads `SENSE_ITEMDEATHKEEPER`. `isHiddenInPlainSight` (367-373) marks an item on the floor that a plain look does not reveal.

### 10. Descriptions: aura tags and state lists

`getDispositionBlurbs(seen, seer)` (2592-2738) builds the text added after a name in a room listing. A tag appears only if the seer can perceive that property and the object's ambiance does not suppress it. Examples: `(glowing red)` for an evil object if the seer can see evil (2608-2609), `(hidden)` (2616-2619), `(magical aura)` (2626-2629), and `(busy)` for a mob that has been running a command for more than 30 seconds (2646-2649, `isRunningLongCommand` 1126-1131). A falling object gets "falling", "sinking" or "sunk" according to the room type (2703-2730).

`getPresentDispositionVerb` (2898-2973) picks the verb for arrival and departure lines, such as "sinks in", "sleepwalks in", "prowls in" or "swims in". Mobs use their own arrival and leave strings (2959-2966). `getDispositionStateList` (2976-3022) and `getSensesStateList` (3025-3064) produce comma lists of state words. The race-change summary uses them (`Races/StdRace.java:2079-2080`).

### 11. Other relations

- `isASlave` (3099-3124): the mob carries a `PrivateProperty` effect, optionally checked against a master's ownership through `CMLib.law().doesOwnThisProperty`.
- `isAControlledFollower` (2538-2550): a monster with no start room counts as controlled, as does a monster whose effect was cast by the invoker or has no invoker.
- `getParents` (2760-2780): lineage is kept as tattoos whose names start with `PARENT:` (2774).
- `isAlcoholic` (2783-2820): liquor material, a liquid holder of liquor, or an intoxicating effect (`Ability.FLAG_INTOXICATING`, `Ability.java:984`). The search runs recursively through containers and spell holders.
- `isMobileMounted` (3127-3139): a mount on a mobile basis. A water-based mount also needs a watery room.
- Type tests: `isMetal` (2022-2044), `isAPlant` (606-626, golems only for mobs), `isARope` (2575-2589), `isEggLayer` (556-568, hard-coded list), `isFish`, `isMarine`, `isOutsider`, `isInsect`, `isVermin`, `isVegetable`, `isUndead` (543-603, 1804-1808). The race-category tests read `resources/lists.ini` (see Content).

### 12. CoffeeUtensils: the miscellaneous rules

**Loot policy and ruin.** `ITEMLOOTPOLICY` is a semicolon-separated list of rules, such as `20% RUIN` (the default, `coffeemud.ini:427`), `10% LOSS`, or `99% RUIN MASK=-MATERIAL +WOODEN`, with optional `WORN` and `UNWORN` words (`coffeemud.ini:415-425`). `parseLootPolicyFor` (`CoffeeUtensils.java:1213-1265`) caches the parsed list and recompiles it when the setting's hash changes. A rule with no `RUIN` or `LOSS` word defaults to LOSS (1252-1253).

`isRuinedLoot` (1428-1460) exempts coins, unsavable items and existing ruined items (1430-1437), and forces ruin for `SENSE_ALWAYSRUIN` (1438-1439). Each rule then checks its mask (1444-1446), skips when `rollPercentage() > pct` (1447-1448), applies the WORN or UNWORN gate (1450-1454), returns null for LOSS (1455-1456), and otherwise returns a ruined copy (1457). It is called from monster corpse handling (`Races/StdRace.java:1035`).

`ruinItem` (1373-1425) makes a `GenRuinedItem`, inserts the word "ruined" into the name, appends a sentence chosen by material, and sets the base value to 0 (1421).

**NPC rejuvenation.** `processVariableEquipment` (824-1005) is called from `MOBS/StdMOB.java:4059` (rejuvenation after death) and `4122`. Carried items with a positive rejuv value (other than `NO_REJUV`), with `ONE_JUV`, or with the corpse-drop flag are grouped by worn slot (848-880). An item alone in its group, or one with no worn slot, is destroyed when `rollPercentage() < rejuv` (893-898). A kept item has its rejuv reset to `NO_REJUV` and, if it is a corpse drop, is hidden with `IS_NOT_SEEN` (901-907). For a rival group (several items for one slot), one item is drawn with probability proportional to its rejuv (913-936). The others are destroyed (937-944). Shop stock is culled when `rollPercentage() > rejuv` (980-986). Money drifts by `Math.random()*getMoneyVariation()` (992-997). The return value is -1, without processing, when a staff member is in the room (838-840), and 0 otherwise.

**Resurrection.** `resurrect(tellMob, corpseRoom, body, XPLevel)` (1551-1683) finds the player through `getPlayerAllHosts`, moves them to the corpse room if needed, and clears any astral-spirit ability or effect (1567-1573). It returns the corpse's contents to the player (1574-1596), destroys the body, and clears the sitting and custom bits (1598-1599). It then applies the `PLAYERDEATH` list (`coffeemud.ini:369-374`):

- `UNL` levels the player (1629-1630).
- `ASTR`, `RETA` and `PUR` do nothing here (1632-1642).
- A number, or an expression over `@x1`, `@x2` and `@x3`, gives an XP refund of `(N + 2*XPLevel)/2` (1644-1665). The same token costs XP at death, so resurrection returns part of it.
- Any other token falls back to a restore formula. The base is `round((100 + 2*XPLevel) * lvl / 2)`, where `lvl` is halved once for each level the player is above the corpse (1669-1675).

Players who died to a player killer get a temporary `+PLAYERKILL 4 false` effect (1608-1619). XP grants are skipped when `XPLevel < 0` (1646, 1667).

**Prompt templates.** `buildPrompt(mob, template)` (1941-2781) expands a status-line template. The codes are listed under Content. Three structural features matter. `%/ ... %/` shows its span only in combat (1953-1965). `%\ ... %\` shows its span only out of combat (1966-1978). `%<NAME>...%<` shows its span only while the attribute `NAME` is set, and `%<NAME!>` shows it only while the attribute is clear (1979-2008).

**Wearability and armour.** `armorCheck(mob, item, level)` (1061-1148) lets item proficiency filters decide first (1068-1076). It then checks the item's material against the allowed armour class (`CharClass.ARMOR_*`, `CharClasses/interfaces/CharClass.java:658-662`). Leather is refused at the cloth, vegan, ore-only and metal-only classes (1081-1085). Metal and mithril are refused at cloth, leather and non-metal (1089-1092). `confirmWearability` (1268-1353) removes all equipment and re-wears it in clothing-layer order, falling back to removal when a wear is refused. `outfit` (303-338) gives an NPC copies of listed items and wears them. `getSeenEquipment` (2931-3045) returns the outermost visible layer for each slot, honouring multi-wear and see-through layer attributes.

**Traps.** `makeADeprecatedTrap` (340-400) chooses a trap ability from the object type and a roll from 0 to 100 (table under Content). `setTrapped` (403-422) removes existing traps and sets reset to 50 (406). The method is deprecated in its interface (`CMMiscUtils.java:279-313`).

**Disenchanting.** `disenchantItem` (704-772) returns 0 if the item was destroyed, -999 if nothing changed, and otherwise the remaining item level. A wand loses its spell's lowest qualifying level, then gains 2 (712). Each ability point costs 3 levels (728), and each removed effect costs 1 (744). Effects flagged `FLAG_NODISENCHANT` survive (737-738). Invisible, bonus, evil, good and hidden bits are cleared, and so are the seems tags (748-765).

**Fire, exits and rooms.** `extinguish` (620-668) recurses through a room, its mobs and items. It removes heating and fire-based effects unless the call is mundane. It kills a fire elemental (653-654) and puts out lit lights (662-666). `roomAffectFully` (671-701) sends a message to both sides of an exit. Opening a locked exit first sends an unlock to the paired exit (693-697).

**Costs.** `createCostManager` (3557-3712) returns a `CostManager` that can describe, check and spend a cost. The cost types begin with TRAIN and PRACTICE (`core/interfaces/CostDef.java:33-`). `compileCost` and `compileConditionalCosts` (1742-1907) parse cost text, such as "3 trains" (see Problems). `compileConditionalRange` (1686-1739) parses conditional tables such as `<=5 1 2` into per-level arrays. Its callers were not checked.

**Recipes.** `loadRecipeList` (242-300) reads tab-delimited rows (`CMMiscUtils.java:73-79`), skips lines beginning with `#`, and pads short rows to the longest row (293-298). `addExtRecipes` (202-239) merges recipes from the mob's items and effects, skipping items flagged `FLAG_UNCRAFTABLE` (`Ability.java:998`). `addExtRecipe` (163-199) trims or pads each row to the column count of the previous one.

**Race mixing.** `getMixedRace` (3100-3442) decides what the child of two races becomes, in this order:

1. Trivial cases. A missing mother gives the father (3110-3111). If one parent's ID contains the other's as a substring, the containing race is returned (3106, 3114).
2. The `RACEMIXING` config (`coffeemud.ini:720-734`) with the words `FATHER`, `MOTHER`, `PARENT`, or explicit `A+B=C` rules (3120-3172). `getSimpleRace` (3047-3086) handles the single-word forms and `raceMixRuleCheck` (2791-2823) handles the explicit rules. A generated race is added to the class table and written to the database (3162-3166).
3. Undead. Undead with undead gives `Undead` (3179-3180). Undead with another race builds a generic race whose ID is the undead ID followed by the other ID. The name is the undead race name plus the other race's name, with a special "lich" form (3193-3196). Body sizes are scaled by 0.6 (3223-3225). Natural weapons and some stat blocks are merged (3228-3303).
4. Human or Halfling. `Half<Race>` for a human, or `<race>ling` for a halfling (3309-3369).
5. Pure elemental with a non-elemental (3371-3404). The element names are Earth, Air, Water, Electricity and Fire (3088).
6. Anything else (3405-3440). The IDs are concatenated in alphabetical order, and the name is "First-Second".

`getConstituantRaces` (2867-2928) runs the process in reverse for display, splitting a mixed ID at its capital letters and matching known race IDs.

**Other utilities.**

- `getPullWeight` (105-147) returns the weight of a pulled rider stack times 4, divided by 300 when the rideable is a wagon (136).
- `getUnsubscribeURL` (2825-2865) builds a per-account unsubscribe link (see Problems).
- `deAlias` (3844-3876) expands an alias, substituting `@X` variables when present, and honours a leading `noecho` word.
- `getFavoredMounts` (3458-3491) reads the cavalier mount skills, which store "category,race" text, and falls back to a horse (3479-3488).
- `isItemInState` (3494-3554) tests the location of an item against a set of states (carried, worn, in a room, contained or not).
- `memoryUse` (564-591) and `memoryUsage` (457-561) estimate object size by reflection, for diagnostics.
- `recursiveDropMOB` (1008-1043), `canBePlayerDestroyed` (1187-1211), `getDeadBodies` (1171-1184) and `reachableItem` (439-454) support death drops and reach checks.
- `swapRaces` and `reloadCharClasses` (1463-1548) re-point mobs and players at a reloaded race or class without a restart.

## Content worth mining

### Disposition and sense vocabulary

From `CMFlagLibrary.java:1721-1847` (disposition) and `1922-2043` (senses). Bit values are PhyStats constants.

**Disposition (IS_* bits, `Disposition` enum):**

| Enum | Bit value | State phrase | Look phrase |
|---|---|---|---|
| ISUNSEEN | 1 | unseeable | Is never seen |
| ISHIDDEN | 2 | hidden | Is hidden |
| ISINVISIBLE | 4 | invisible | Is invisible |
| ISEVIL | 8 | evil | Evil aura |
| ISGOOD | 16 | good | Good aura |
| ISSNEAKING | 32 | none (see Problems) | Is sneaking |
| ISBONUS | 64 | sacred | Is magical |
| ISDARK | 128 | darkness | Is dark |
| ISGOLEM | 256 | blank | Is golem |
| ISSLEEPING | 512 | sleepy | Is sleeping |
| ISSITTING | 1024 | crawls | Is sitting |
| ISFLYING | 2048 | flies | Is flying |
| ISSWIMMING | 4096 | swims | Is swimming |
| ISGLOWING | 8192 | glowing | Is glowing |
| ISCLIMBING | 16384 | climbing | Is climbing |
| ISFALLING | 32768 | falling | Is falling |
| ISLIGHT | 65536 | shining | Is a light source |
| ISBOUND | 131072 | bound | Is binding |
| ISCLOAKED | 262144 | cloaked | Is Cloaked |
| ISUNSAVABLE | 524288 | blank | Is never saved |
| ISCATALOGED | 1048576 | blank | Is cataloged |
| ISUNATTACKABLE | 2097152 | blank | Is unattackable |
| ISCUSTOM | 4194304 | blank | Is something |
| ISUNHELPFUL | 8388608 | blank | Is Unhelpful |

**Senses (CAN_* bits, `Senses` enum):**

| Enum | Bit value | State phrase | Desc phrase |
|---|---|---|---|
| CANNOTSEE | 1 | blind | Is Blind |
| CANSEEHIDDEN | 2 | see hidden | Can see hidden |
| CANSEEINVISIBLE | 4 | see invisible | Can see invisible |
| CANSEEEVIL | 8 | detect evil | Can see evil |
| CANSEEGOOD | 16 | detect good | Can see good |
| CANSEESNEAKERS | 32 | see sneaking | Can detect sneakers |
| CANSEEBONUS | 64 | detect magic | Can see magic |
| CANSEEDARK | 128 | darkvision | Can see in the dark |
| CANSEEINFRARED | 256 | infravision | Has infravision |
| CANNOTHEAR | 512 | deaf | Is Deaf |
| CANNOTMOVE | 1024 | can't move | Is Paralyzed |
| CANNOTSMELL | 2048 | can't smell | Can not smell |
| CANNOTTASTE | 4096 | can't eat | Can not eat |
| CANNOTSPEAK | 8192 | can't speak | Is Mute |
| CANNOTBREATHE | 16384 | can't breathe | Can not breathe |
| CANSEEVICTIM | 32768 | detects victims | Can detect victims |
| CANSEEMETAL | 65536 | metalvision | Can detect metal |
| CANNOTTHINK | 131072 | can't think straight | Can not concentrate |
| CANNOTTRACK | 262144 | can't be tracked | Is off the grid |
| CANNOTAUTOATTACK | 524288 | can't auto attack | Is not auto-attacking |
| CANNOTBECAMPED | 1048576 | can't be camped | Can not be camped on |
| CANGRUNTWHENSTUPID | 2097152 | can grunt | Can grunt |
| CANSEEITEMSHIDDEN | 4194304 | see hidden items | Can see hidden items |

### Presentation verbs

From `Libraries/Sense.java:2822-2895`. Arrival phrases are the state verb with "in" added. Examples: SINKS "sinks in", FALLS "falls in", SLEEPWALKS "sleepwalks in", FLOATS "floats in", SNEAKS "sneaks in", PROWLS "prowls in", CRAWLS "crawls in", SWIMS "swims in". A mob with a set arrival string uses that string instead (2959-2960).

### Aura tags

From `getDispositionBlurbs`, `Libraries/Sense.java:2592-2738`:

| Tag shown | Shown when the seer can perceive it and the target has it | Lines |
|---|---|---|
| (glowing red) | evil | 2608-2609 |
| (glowing blue) | good | 2610-2611 |
| (invisible) | invisible | 2612-2613 |
| (sneaking) | sneaking | 2614-2615 |
| (hidden) | hidden, for a mob or an item | 2616-2619 |
| (heat aura) | mob, not a golem, in a dark room, seer has infravision | 2620-2625 |
| (magical aura) | magical bonus | 2626-2629 |
| (metallic aura) or (mithril aura) | metal item | 2630-2639 |
| (glowing) | glowing | 2642-2645 |
| (busy) | mob running a command for over 30 seconds | 2646-2649 |
| (glowing purple) | chaotic | 2650-2653 |
| (glowing white) | lawful | 2654-2657 |
| (bound), (floating), (flying), falling / sinking / sunk | state flags | 2694-2730 |

### Status-prompt codes

From `buildPrompt`, `CoffeeUtensils.java:1941-2781`. A `%` followed by one of these letters is replaced by the value shown. Lowercase is usually the current value and uppercase the maximum or the alternative form. A trailing `%` after the h, m or v codes shows a percentage.

| Code | Shows | Line |
|---|---|---|
| `%h` / `%H` | HP / max HP | 2288 / 2302 |
| `%m` / `%M` | mana / max mana | 2470 / 2484 |
| `%v` / `%V` | movement / max movement | 2569 / 2583 |
| `%p` / `%P` | actions / speed | 2490 / 2496 |
| `%I` | visibility: Wizinvisible, Cloaked, Undetectable, Hidden/Invisible, Invisible or Hidden | 2308 |
| `%x` / `%X` | experience / experience needed | 2601 / 2611 |
| `%w` / `%W` | weight / max carry | 2589 / 2595 |
| `%c` / `%C` | items carried / max items | 2177 / 2183 |
| `%j` / `%J` | followers / max followers | 2330 / 2334 |
| `%g` / `%G` | money in lowest denomination / money as a short string | 2275 / 2282 |
| `%d` / `%D` | range to target / ammunition remaining | 2189 / 2197 |
| `%e` / `%E` | victim's name / victim's health text | 2205 / 2213 |
| `%K` / `%k` | the "tank", meaning your target's target, then `h`, `H`, `m`, `M`, `v`, `V`, `e`, `E`, `d`, `D` | 2338 |
| `%a` / `%A` | alignment faction percent / rank name | 2158 / 2164 |
| `%f` / `%F` | faction percent / rank name, by faction ID | 2221 / 2247 |
| `%y` / `%Y` | common skills' remaining percent / ticks until next step | 2626 / 2643 |
| `%u` | abilities on cooldown under compounding rules | 2530 |
| `%l` / `%L` | percent of current area / of the world visited | 2456 / 2463 |
| `%r` / `%R` | room name / room ID (system messages only) | 2502 / 2509 |
| `%t` / `%T` | time of day / hour | 2516 / 2523 |
| `%z` / `%Z` | area name / area codes (see below) | 2664 / 2671 |
| `%@` | weather description | 2762 |
| `%B` | line break | 2171 |
| `%-` | any worn item, character stat, physical stat or state, optionally with `BASE` or `MAX` | 2009 |

`%Z` subcodes: `o`/`O` space object, `l` and `L` average and median level, `c` currency, `a` and `A` average and median alignment, `n` and `N` alignment rank names, `w` weather code, `W` weather description, `t` short time, `T` time description, `r` room domain type (2680-2749).

### Trap assignment

From `makeADeprecatedTrap`, `CoffeeUtensils.java:340-400`. The roll is 0 to 100.

| Object | Door and lock | Door, no lock | No door |
|---|---|---|---|
| Exit | < 20 `Trap_Open`, < 80 `Trap_Unlock`, else `Trap_Enter` | < 50 `Trap_Open`, else `Trap_Enter` | `Trap_Enter` |
| Container | < 20 `Trap_Open`, < 80 `Trap_Unlock`, else `Trap_Get` | < 50 `Trap_Open`, else `Trap_Get` | `Trap_Get` |
| Other item | `Trap_Get` | | |

### Ruin sentences

From `ruinItem`, `CoffeeUtensils.java:1387-1416`. Each sentence follows the item's name.

- Leather, cloth, vegetation, flesh, paper: "is torn and ruined beyond repair."
- Metal, mithril, wood: "is battered and ruined beyond repair."
- Glass: "is shattered and ruined beyond repair."
- Rock, precious, synthetic: "is cracked and ruined beyond repair."
- Anything else: "is ruined beyond repair."

### Racial category sets

From `resources/lists.ini:1774-1792`, read through `CMProps.ListFile` (`core/CMProps.java:573-579`):

| Predicate | Racial categories |
|---|---|
| Undead (`isUndead`) | Undead |
| Outsider (`isOutsider`) | Demon, Outsider, Devil, Mephit, Elemental, Celestial |
| Insect (`isInsect`) | Insect, Arachnid |
| Vermin (`isVermin`) | Insect, Rodent, Worm, Arachnid |
| Vegetation (`isVegetable`) | Vegetation |
| Fish (`isFish`) | Fish |
| Marine (`isMarine`) | Fish, Water Mammal |

`isEggLayer` (`Libraries/Sense.java:556-568`) uses a fixed list instead: Amphibian, Avian, Dragon, Reptile, Ophidian, Fish.

### Loot policy and config defaults

`coffeemud.ini` gives the defaults and the grammar for these settings:

- `ITEMLOOTPOLICY=20% RUIN` (427). Rules are separated by semicolons. The grammar is a percentage, then RUIN or LOSS, then optional WORN or UNWORN, then optional `MASK=` with a zapper mask (415-425).
- `PLAYERDEATH=(@x1 * 50) < 2000` (374). Tokens are `OUT x`, `RECALL`, a number, an expression, and the keywords that resurrection reads (`UNL`, `ASTR`, `RETA`, `PUR`), as described in section 12 above (369-373).
- `RACEMIXING` is blank by default, with the example `Orc+Human=Gnome,MOTHER Elf+Human=HalfElf|Elf` (720-734).

### Price words

`createCostManager` supports types including XP, GOLD, PRACTICE, QP, HITPOINT, MANA and MOVEMENT (`CoffeeUtensils.java:3573-3604`). Each type has a short requirement phrase, such as "5 quest pts" or "3 hit points" (3617-3637). Only a prefix of each type name matches in `compileCost` (see Problems).

### Ability and behaviour flags

The engine's flag vocabulary is in `Abilities/interfaces/Ability.java` (for example FLAG_TRACKING 958, FLAG_HEATING 960, FLAG_FIREBASED 962, FLAG_INTOXICATING 984, FLAG_UNCRAFTABLE 998, FLAG_NONENCHANTMENT 1022, FLAG_NODISENCHANT 1024) and in `Behaviors/interfaces/Behavior.java:187-195` (FLAG_MOBILITY, FLAG_POTENTIALLYAGGRESSIVE, FLAG_LEGALBEHAVIOR, FLAG_POTENTIALLYAUTODEATHING). `QUALITY_MALICIOUS` is 0 and `QUALITY_INDIFFERENT` is 1 (`Ability.java:1083` and `1085`). The default `abstractQuality()` is `QUALITY_INDIFFERENT` (`Abilities/StdAbility.java:315-318`).

## Notable design ideas

- **State is what the effects leave.** Temporary conditions have no separate store. Hidden, invisible, bound and sleeping all come from effects that are recalculated every time, so when an effect ends the state ends with it (`MOBS/StdMOB.java:827`).
- **Appearance and reality are separate.** The `SEEMS_*` tags let something look good or evil without being so. The display helper `getAlignmentName` prefers the real bits and then falls back to the seems tags (`Libraries/Sense.java:925-954`), while the mechanical good and evil tests ignore the seems tags (Problem 7).
- **Hiding is a contest with a moonlight tier.** A thief's hide score and a seer's detect score are compared (1519-1521), and a middle "barely seen" state under moonlight (1576-1612) gives a graded visibility vocabulary.
- **Smell goes through the message system.** A scent is offered to the room as a `TYP_AROMA` message, so the room and its behaviours can veto it (`Libraries/Sense.java:212-213`).
- **One ordered function encodes perception.** `canBeSeenBy` is a single list of rules in a fixed order (1482-1573), which keeps the game's perception rules in one place.
- **Inconsistency checks heal the world.** `validCheck` finds stranded or orphaned mobs and items and removes them (`MOBS/StdMOB.java:4082-4095`, `Items/Basic/StdItem.java:755-760`).
- **Random gear for respawned NPCs.** Rival items compete through a weighted draw (`Libraries/CoffeeUtensils.java:913-936`), so a respawned NPC gets one of several equivalent items.
- **Race mixing is data.** Config rules, undead and human-half rules, and elemental rules produce generated races with derived names (3100-3440), which are persisted for later games.
- **The resurrection XP refund reuses the death penalty.** One `PLAYERDEATH` expression sets both the loss at death and the refund at resurrection (`coffeemud.ini:369-374`, `Libraries/CoffeeUtensils.java:1644-1665`).
- **Status lines are a small template language.** `buildPrompt` supports combat-only and attribute-gated spans (1953-2008), so one template serves many player states.

## Problems and cruft

Read-only findings. Items marked "traced" follow from reading the code and were not run.

1. **Blank sneaking state prints the word "null".** The `ISSNEAKING` branch sets `desc` and `verb` but discards its `state` (`CMFlagLibrary.java:1751-1755`, where `CMLib.lang().L("sneaks")` is called and its result dropped). `getState()` then returns null, and `getDispositionStateList` appends it as text (`Libraries/Sense.java:3016`). The list feeds the race-change summary (`Races/StdRace.java:2079`). Traced.
2. **Null mob crashes `canAccess` for areas.** `canAccess(mob, Area)` evaluates `mob.location()` whenever the area is not hidden (`Libraries/Sense.java:1987-1988`). A null mob on a visible area throws. Callers were not checked. Traced.
3. **Floating ratio uses a running total.** `totalFloatilla+=totalWeight*codes.buoyancy(...)` (`Libraries/Sense.java:1756-1757`) multiplies each density by the cumulative weight so far, not the item's own weight. Later items count more than they should. A single item is unaffected. The intended form is probably each item's weight times its density. Traced.
4. **NPC rejuvenation is inverted for non-rival gear.** `CoffeeUtensils.java:893-895` destroys an item when `rollPercentage() < rejuv`. `CMMiscUtils.java:266-272` says rejuv is the percent chance of retention. The shop branch (`CoffeeUtensils.java:980`) keeps items when the roll is at most rejuv, which matches the documentation. Traced.
5. **Resurrection writes the wrong disposition layer.** `CoffeeUtensils.java:1599` computes the derived disposition from the base value just written at 1598, which discards any derived bits set by effects. Traced.
6. **Moon rules disagree.** For non-room targets in the dark, only `FULL` counts (`Libraries/Sense.java:1541-1542`). For room targets, `FULL`, `WAXGIBBOUS` and `WANEGIBBOUS` count (1555-1561). For barely-seen, a non-room target needs any phase except `NEW` (1585-1587), and a room needs a crescent, quarter or `BLUE` phase (1597-1607). Traced.
7. **Good and evil ignore the seems tags; law and chaos do not.** `isGood` and `isEvil` (`Libraries/Sense.java:672-682`, `785-795`) never read `SEEMS_GOOD` or `SEEMS_EVIL`. `isLawful` and `isChaotic` read `SEEMS_LAWFUL` and `SEEMS_CHAOTIC` (800, 821). Traced.
8. **Base and derived masks disagree.** The hide score reads the base disposition (`Libraries/Sense.java:1448`) and the detect score reads the base senses (1470). `isHidden` (534) and `canSeeHidden` (75) read the derived masks. An effect that adds or removes a bit can therefore make the two sides of the same test disagree. Traced.
9. **Cost words must match a prefix.** `compileCost` accepts only a prefix of each `CostType` name (`CoffeeUtensils.java:1747-1752`). The example in the interface, "3 trains 5 pracs" (`CMMiscUtils.java:151`), fails to match the plural words. An unmatched word falls back silently to TRAIN (1850), so "5 pracs" would be read as 5 trains. Traced, not run.
10. **Unsubscribe tokens use the password.** `getUnsubscribeURL` (`CoffeeUtensils.java:2855`) hashes the account name with the stored password string. The interface comment says the scheme is open to a man-in-the-middle attack (`CMMiscUtils.java:494-497`). Account passwords should not be used in link tokens.
11. **Falling objects can dereference a null room.** In `getDispositionBlurbs`, `roomLocation(seen)` can return null, and `R.domainType()` is then called without a check (`Libraries/Sense.java:2706-2707`). Traced.
12. **Room weight is overloaded.** Room weight and height serve as tunnel size (`Libraries/Sense.java:2076-2080`), and areas add their weight to every room (`Areas/StdArea.java:1350`). The city-room thresholds also differ: weight above 2 for underwater rooms (1180) but at least 2 for underground stone rooms (1184). The intent was not verified.
13. **Bit values overlap.** Several masks reuse values for different kinds of object. `SENSE_ROOMNOMOVEMENT` and `SENSE_ITEMDEATHKEEPER` are both 1024 (`Common/interfaces/PhyStats.java:499-501`). `SENSE_ROOMCRUNCHEDIN` and `SENSE_ITEMNOAUTOWEAR` are both 256 (491-493). `CAN_NOT_BE_CAMPED` and `SENSE_UNCAMPABLE` are both 1048576 (438, 521). `SENSE_UNLOCATABLE` and `CAN_NOT_SEE` are both 1, and `StdArea` masks them out explicitly (`Areas/StdArea.java:1333`). This works only because each bit is read on a different kind of object.
14. **Dead and duplicated code.** Both branches of the `SITS` case in `getPresentDispositionVerb` return the same verb (`Libraries/Sense.java:2939-2942`). `parseLootPolicyFor` tests `!mob.isMonster()` after returning for non-monsters (`CoffeeUtensils.java:1215-1220`). `domainAffects` and `flaggedAffects` only forward to other methods (1880-1883, 1957-1960).
15. **Mixed-race checks use substrings.** `getMixedRace` decides by `indexOf` on race IDs (`CoffeeUtensils.java:3106`, `3114`). A short ID that appears inside a longer one can win by accident. Traced.
16. **Documentation conflicts with code.** The buoyancy range is documented as 0-20000 in one place (`RawMaterial.java:1468`) and 0-10000 in another (1634). The `PLAYERDEATH` comment says `@x2` is the killer's level (`coffeemud.ini:369-373`). Resurrection has no killer, so it puts the corpse's level in that slot (`CoffeeUtensils.java:1625`). MUDFight puts the killer's level there (`Libraries/MUDFight.java:3520-3524`).
17. **Diagnostics run inside the library.** `memoryUse` calls `System.gc()` and sleeps (`CoffeeUtensils.java:574-579`). If it finds negative memory it calls itself again with no bound (583-584). Traced.
18. **Age stored as text.** `isBaby` parses an age effect's text and compares it with `Short.MAX_VALUE` (`Libraries/Sense.java:2340-2341`). Traced.
19. **Binary search needs a sorted list.** `canBreatheThis` uses `Arrays.binarySearch` on the breathables array (`Libraries/Sense.java:257`) without checking that it is sorted. Not verified.

## Takeaways for a Python SUD

- **Separate base state from effects.** Keep a stored base set of states and senses for each creature, and compute the effective set from base plus active effects when it is needed. Effects are objects with a `modify(state) -> state` method, and expiry removes them. The CoffeeMud pair of base and derived copies exists to support caching, which a Python game does not need at this scale.
- **Use two flag sets.** One set holds states (hidden, invisible, sleeping, sitting, flying, swimming, bound, dark, glowing, falling). A second holds senses (blind, deaf, see hidden, see invisible, darkvision, cannot move). Python's `enum.Flag` gives the bitmask behaviour with names:

  ```python
  from enum import Flag, auto

  class State(Flag):
      HIDDEN = auto()
      INVISIBLE = auto()
      SLEEPING = auto()
      SITTING = auto()
      FLYING = auto()
      DARK = auto()
      BOUND = auto()

  def effective_state(entity):
      state = entity.base_state
      for effect in entity.effects:
          state = effect.modify(state)
      return state
  ```

- **Perception as one ordered function with reasons.** Port `canBeSeenBy` as `can_see(seer, thing, world) -> (Perception, reason)`, with `Perception` taking the values SEEN, BARELY and UNSEEN. The rules run in order, as in `Libraries/Sense.java:1482-1573`. The reason string can double as in-game text ("It is too dark to see that.") and as debugging output. Keep the three tiers, because moonlight-dim visibility gives a text game useful vocabulary.
- **Hiding as a single contest.** Let a hider's score be a skill plus stealth-related stats, and let a seeker's score be a skill plus perception stats. Breaking cover in combat, as `isHidden` does (`Libraries/Sense.java:535-538`), is a simple rule to keep. Drop the base-versus-derived split.
- **Appearance separate from alignment.** Store `alignment` and `appears_as` separately, so that illusions and detect spells can differ. Choose one source for each test, and use it for good, evil, law and chaos alike (Problems 7).
- **Floating from densities.** Keep a table of densities and a short function:

  ```python
  DENSITY = {"water": 1000, "wood": 920, "oak": 720, "iron": 7900, "gold": 19320}

  def floats(contents, capacity=0.0):
      weight = sum(i.weight for i in contents)
      mass = sum(i.weight * DENSITY[i.material] for i in contents)
      if capacity > weight:          # empty space counts as air
          mass += (capacity - weight) * 1
          weight = capacity
      return weight == 0 or mass / weight <= 1000
  ```

  This uses each item's own weight, which avoids Problem 3. The falling check would be a separate argument.
- **Loot as data.** Represent loot policy as an ordered list of rules, for example `{"chance": 0.2, "action": "ruin", "when": "unworn"}`, applied to each carried item with the first passing rule winning. Keep the "ruined" naming and the material sentences as data.
- **Restock with the shop semantics.** For NPC stock that refreshes, keep an item when `random() <= rejuv`, which is the shop branch's meaning (Problem 4). Skip the rival draw unless a shop has several substitute items.
- **Make the status line a template.** A format string with named fields and lazily computed values covers most of `buildPrompt`. Combat-only and attribute-gated spans can be small predicates over the state, so the template stays readable.
- **Parse costs strictly.** Model a cost as a dataclass with `check(player)` and `pay(player)`, and parse text with a table of exact words. Reject unknown words rather than defaulting to a currency (Problem 9).
- **Keep the XP refund idea.** One death-penalty expression that also sets the resurrection refund is a tidy way to keep the two in step.
- **Drop the multiplayer and sysop parts.** Leave out sysop visibility, PvP death and loot rivalry, clan-owned items, cloaking by level, session-based "busy" timing, and the database-persisted generated races. A fixed table of half-races is enough for a single-player world.
- **Keep a consistency checker as a dev command.** A `check_world()` function that reports orphaned items and stranded NPCs is useful during development. Report rather than destroy, which avoids the automatic removal in `StdItem` (`Items/Basic/StdItem.java:757-760`).

## Coverage

- **Read in full (all lines):** `Libraries/interfaces/CMFlagLibrary.java` (2045 lines), `Libraries/Sense.java` (3140), `Libraries/CoffeeUtensils.java` (3877), `Libraries/interfaces/CMMiscUtils.java` (565).
- **Read in part or cross-checked by grep and short reads:** `Common/interfaces/PhyStats.java` (constants, Ambiance), `Items/interfaces/RawMaterial.java` (density rows and accessor), `Abilities/interfaces/Ability.java` and `Behaviors/interfaces/Behavior.java` (flags and quality constants), `Abilities/StdAbility.java:315-318`, `MOBS/interfaces/MOB.java` (`Attrib`), `MOBS/StdMOB.java` (recoverPhyStats, validCheck counter), `Items/Basic/StdItem.java` (validCheck timer), `Areas/StdArea.java` (darkness and weight), `Abilities/Thief/Thief_Hide.java` (hide hooks), `Libraries/MUDFight.java:3520-3524` (death variables), `Races/StdRace.java` (call sites), `Common/interfaces/TimeClock.java` (MoonPhase), `Common/interfaces/Climate.java:111`, `CharClasses/interfaces/CharClass.java` (ARMOR_* constants), `core/CMProps.java` (ListFile), `core/interfaces/CostDef.java` (CostType), `resources/lists.ini`, `coffeemud.ini`, `Behaviors/ItemIdentifier.java` (call site).
- **Counted by grep only:** call sites of `canBeSeenBy`, `isAliveAwakeMobile`, `canSmell`, `processVariableEquipment`, and related methods.
- **Skipped:** all other buckets, and the bodies of the callers listed above.
