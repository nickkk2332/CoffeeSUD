# Generic object editor (CMGenEditor)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b017` (Generic object editor (CMGenEditor)) · 2 files · 13810 lines of Java.

## Summary

CMGenEditor is the builder's in-game editor. Almost every editable thing in CoffeeMud (NPCs, players, items, rooms, areas, exits, races, classes, abilities, languages, clans, shopkeepers, accounts) is changed through one numbered text menu produced by this library. Each menu is a sequence of field calls, so the same code both displays the fields and edits them. Most fields are string-keyed stats (`getStat`/`setStat`), which lets one set of prompts work on any object that exposes stats, and a stat added to a class appears in the editor automatically. For a single-player Python game the most reusable parts are the menu protocol, the typed prompt primitives, and the catalogue of fields a rich world object carries (races, classes, abilities, traps, poisons, crafts, shop economics).

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/CMGenEditor.java` | Implementation: prompt primitives (`prompt`, `promptStat*`, `promptDelimitedList`, `promptMulti*`), about 280 methods, most of them field editors (`gen*`), per-type menus (`modifyGenItem`, `modifyGenMOB`, `modifyPlayer`, `modifyRoom`, `modifyGenRace`, `modifyGenClass`, ...), and `changeRoomType` | 12704 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/GenericEditor.java` | The `CMLib.genEd()` contract: about 70 public methods counting overloads, the `CMEval` validator interface, and the documented `showFlag` convention (javadoc on each method) | 1106 |
| `com/planet_ink/coffee_mud/core/CMProps.java:321,3109` | `EDITORTYPE` setting: WIZARD gives edit-all mode, MENU gives the numbered menu (default MENU) | - |
| `coffeemud.ini:1277-1279` | Config comment and default (`EDITORTYPE=MENU`) | - |
| `com/planet_ink/coffee_mud/Commands/Create.java:359,858` | Opens the editor on a newly created item or MOB with `showFlag` -1 | - |
| `com/planet_ink/coffee_mud/Commands/Stat.java:1388,1556` | Shows an object with `showFlag` -950 (display only) | - |
| `com/planet_ink/coffee_mud/Commands/Modify.java:1329-1331` | Repeats the same `EDITORTYPE` check inline for its own loop | - |

## How it works

### 1. Entry points and dispatch

`genMiscSet(mob, E, showFlag)` (CMGenEditor.java:3188-3259) is the router. It suspends ticking on the object, prints a cataloged warning if needed, then picks one editor with `instanceof` tests in this order:

| Test (in code order) | Editor (line) |
|---|---|
| `ShopKeeper` | `modifyGenShopkeeper` (12134) |
| `MOB` with no `playerStats()` | `modifyGenMOB` (11584) |
| `MOB` with `playerStats()` | `modifyPlayer` (11700) |
| `Exit` that is not an `Item` | `modifyGenExit` (11498) |
| `RoomMap` | `modifyGenMap` (11033) |
| `Armor` | `modifyGenArmor` (11343) |
| `MusicalInstrument` | `modifyGenInstrument` (11430) |
| `Food` | `modifyGenFood` (10789) |
| `Drink` that is an `Item` | `modifyGenDrink` (10902) |
| `Weapon` | `modifyGenWeapon` (11231) |
| `Container` | `modifyGenContainer` (11093) |
| `Item` with ID `GenWallpaper` / other `Item` | `modifyGenWallpaper` (10986) / `modifyGenItem` (10630) |
| `Room` / `Area` | `modifyRoom` (12295) / `modifyArea` (12528) |

Afterwards, a `Physical` object goes through `catalogCheckUpdate` unless `showFlag` is -950 (3251-3252). `genMiscText` (3262-3271) sends generic objects to `genMiscSet` and gives non-generic ones a single "Misc Text" string prompt.

Some editors are called directly by the mud rather than through the router: `modifyStdMob` (12433), `modifyStdItem` (12485, only level, ability, rejuv, uses and misc text), `modifyGenRace` (10567), `modifyGenClass` (9053), `modifyGenAbility` (9343), `modifyGenTweakAbility` (9410), `modifyGenLanguage` (9480), `modifyGenCraftSkill` (9601), `modifyGenWrightSkill` (9647), `modifyGenCommand` (9692), `modifyGenTrap` (9747), `modifyGenPoison` (9801), `modifyGenGatheringSkill` (9860), `modifyGovernment` (9271), `modifyClanPosition` (9174), `modifyClan` (12075), `modifyAccount` (12370), `modifyPlane` (10188), `modifyAllQualifyEntry` (12618), `modifyManufacturer` (12662), `modifyComponents` (10153).

### 2. The numbered-menu protocol (`showNumber` and `showFlag`)

Every field editor takes `(mob, obj, showNumber, showFlag)`. A menu numbers its fields with `++showNumber`, so a field's number is its position in that call sequence. Every `prompt` overload starts with the same gate (CMGenEditor.java:510-511 and 529-530):

```java
if((showFlag>0)&&(showFlag!=showNumber)) return oldVal;   // some other field was selected: silent
mob.tell(numStr+fieldDisp+": '"+showVal+"'.");              // always show the current value
if((showFlag!=showNumber)&&(showFlag>-999)) return oldVal;  // list mode: display only
```

The `showFlag` modes:

| showFlag | Meaning | Lines |
|---|---|---|
| -1 | List mode. Each field prints `N. Label: 'value'.` and nothing is edited. The top loop then asks `Edit which? ` | 10641-10642, 10774-10779 |
| N > 0 | Only field N prompts. After the edit the loop sets `showFlag` to -1 and redraws | 10769-10773 |
| -999 | Every field prompts once, in order, then the editor exits. `-1` becomes `-999` when `EDITORTYPE` is WIZARD | 10634-10635, 10764-10768 |
| -950 | Display only. The `> -999` gate suppresses every prompt, the loop exits after one pass, and the post-edit recalculation is skipped | 10781; Stat.java:1388 |
| 0 at `Edit which?` | Leave the editor | 10775-10779 |

Objects that are monsters are not editable through these menus: `if(mob.isMonster()) return;` (10638, 11587, 10569, 11702). A `showNumber` of 0 means the field is not part of a numbered menu, for example race PhysStats (7223, 7229). Each top-level menu ends with the same hand-copied `Edit which?` loop (37 copies in this file, see Problems).

### 3. Prompt primitives

- **Free text.** `prompt(...String...)` (506-586) and its overloads (241-275). Blank input keeps the old value (558-561). `?` shows the help text or the choice list (549-555). `null` clears the value when `emptyOK` is set (562-563). `maxChars` truncates only the display (513-514).
- **Client protocols.** If the client supports MCP simple-edit, the prompt hands the value to the client's multi-line editor (532-547). In raw-print mode it sends a GMCP `Siplet.Input` (520-521).
- **Long text.** `promptStatMsg` (186-215) opens the in-game journal message editor, `CMLib.journals().makeMessage` (203), with an optional filter.
- **Numbers.** `int` and `long` (657-704) accept only `CMath.isInteger`; anything else prints "(no change)". `double` (623-654) takes a minimum and maximum, and an out-of-range entry prints `Min value is:` or `Max value is:` and keeps the old value.
- **Booleans.** `prompt(boolean)` (589-614) reads the first letter, T/F/Y/N. `promptToggle` (301-312) asks a Y/N confirm.
- **Single choice.** `promptChoice` (874) and `promptStatChoices` (228-232) validate with `CMEVAL_INSTANCE` (77-92), which is a case-insensitive exact match. Otherwise the player sees "That was not one of your choices."
- **Delimited set.** `promptDelimitedList` (339-475). Typing an item already in the list removes it, and typing a new one adds it. When a sub-value is defined, a second prompt asks for it (462-466). `null` clears the list (391-392).
- **Bitmask.** `promptMulti` and `promptMultiOrExtra` (707-804) toggle named bits and return the OR'd integer. `NULL` gives 0, or "" if the choice list contains "" (743-750).
- **Enum sets.** `promptEnumChoices` (95-117) splits on comma, semicolon or space. `promptEnumChoice` (121-136) returns one enum constant.
- **Stat binding.** `promptStatStr`, `promptStatInt`, `promptStatBool`, `promptStatDouble`, `promptStatChoices` and `promptStatCommaChoices` (139-239) read `E.getStat(field)` and write `E.setStat(field, ...)`. Any `Modifiable` works, so one prompt can edit a room, an ability, a race or a clan by key string.

### 4. Raw stat fallback

Each top-level menu ends with a loop over the stat codes past `getSaveStatIndex()`, for example 10759-10760, 11668-11669, 12332-12333, and the same pattern at 10956, 11006, 11064 and 11202. Each code gets a prompt labelled `CMStrings.capitalizeAndLower(code)`. A new stat added to an object's code table shows up in the editor with no editor code.

### 5. Catalogs: templates and instances

- Renaming a cataloged item or MOB warns that the change detaches it from its catalog entry, and asks for confirmation (genName 969-1003, confirm at 997).
- `catalogCheckUpdate` (1005-1064) runs after an edit. It calls `CMLib.catalog().checkCatalogIntegrity(P)`, prints each differing stat as a `CATALOG:` line and a `YOURS:` line (1026-1031), then asks `U` (update the catalog), `D` (detach), or `C` (cancel). Cancel restores the original text and recalculates (1038-1045).
- Menus show `*** This object is Cataloged **` (12445-12451) or `*** This object WAS cataloged and is still tied **` when the catalog entry has gone.

### 6. Live-object safety and recalculation

- `genMiscSet` suspends ticking on the object while it is edited (`CMLib.threads().suspendTicking`, 3193-3194), and resumes in `finally` (3256-3257), except when the builder is editing himself. `modifyStdMob` does the same (12437-12438, 12479-12480).
- After an item edit, `recoverPhyStats()` runs (10783). After a MOB edit, `recoverCharStats()`, `recoverMaxState()`, `recoverPhyStats()` and `resetToMaxState()` run (11690-11693). All of these are skipped for -950.
- A MOB whose level first goes above 1 is re-scored with `CMLib.leveler().fillOutMOB(...)` and told "Combat stats rescored." (11601-11607).

### 7. Changing a room's type (`changeRoomType`, 1433-1616)

`genRoomType` (1618-1653) lists the locale classes, asks for a new one, and warns: "This will change the room type of room X. It will automatically save any mobs and items in this room permanently." (1642). Then `changeRoomType` builds the new room and moves everything across:

- Display text, description, exits and door state are copied (1465-1469, 1475-1476).
- Effects that cannot be uninvoked and all behaviors move to the new room (1443-1462, 1596-1602). Other effects are uninvoked (1448-1454).
- Savable MOBs are taken out, copied with `copyOf()`, rebuilt, and given a rejuv of 2.5 minutes of ticks, capped at 20 minutes (1490-1494, 1531-1545). Non-savable MOBs are moved as they are (1497-1500).
- Items move. Items subject to wear and tear are reset to 100 uses (1514-1524).
- Every door in the area and in `CMLib.map().rooms()` that points at the old room is repointed (1548-1575). That is a scan of the whole map.
- Players whose start room or current location is the old room are moved to the new one (1580-1594).
- If the room is savable, its room, MOB and item records are written to the database (1603-1608). Then the old room is destroyed (1609).

### 8. Mobs, players, shopkeepers, clans and accounts

- **NPC (`modifyGenMOB`, 11584-11696).** Race (`genRace`, 4197), gender (`genGender`, 4112), level and rejuv, height and weight, speed, attack, damage, armor, hit points, money and money variation, abilities, behaviors, effects, disposition, senses, rideable and drink sub-menus, deity sub-menus, factions, tattoos, expertises, scripts, image, tags, and the raw stat loop.
- **Race change** applies immediately: `startRacing(M,false)` and `setHeightWeight(...)` run on the MOB (4216-4222).
- **Class** (`genCharClass`, 4230-4324) adds and removes classes. The last class cannot be removed (4260-4261). Adding a class needs `availabilityCode()>0` or SysOp (4256). Free levels go to the new class, or are taken from the highest class (4293-4313).
- **Stats.** `genCharStats` (2848-2889) is a letter-keyed menu over base character stats, skipping gender. Values below zero are refused (2875-2877). Changing age also recomputes the birth year from the local clock (2879-2883). `genCharState` (2891-2928) does the same for the character-state codes.
- **Factions.** `genFaction` (4059-4110) adds a faction with a value clamped to the faction's minimum and maximum (4095-4098), or removes it if the MOB already has it (4082-4087).
- **Money.** `genMoney` (3574-3583) turns a zero money field into money first, converting the MOB's inventory coins (3576-3581).
- **Hit points.** For NPCs the field is "Hit Points Bonus Modifier", with the help text `Hit points = (level*level) + (random*level*THIS)` (3399-3404). For players it is labelled "Ability -- unused".
- **Player extras.** Password reset writes through `DBUpdatePassword` and never shows the current value (1667-1683). Bonus creation points (`genCommonBonus`, 2824-2846). Birthday, email and notes (1661-1698). Account expiration (12355).
- **Shopkeeper (12134-12294).** Banker coin interest, item interest, bank chain and loan interest (3535-3553). Auctioneer and craft broker timing and percentages through `genAuctionPrompt` (11934-11962) and setters such as `setTimedListingPrice`, `setTimedFinalCutPct`, `setMinTimedAuctionDays`, `setMaxListings` and `setCommissionPct`.
- **Clan (`modifyClan`, 12075-12133).** Morgue, recall and donation rooms through `genClanRoom` (12044), plus status, government, roles, classes, acceptance rules, and members (5753).
- **Account (`modifyAccount`, 12370-12432).** Email, expiration, flags and bonus counts for languages, characters, skills and stat points, plus notes (12387-12408).

### 9. Races and classes

Both are numbered menus over string keys (`modifyGenRace` 10567-10628, `modifyGenClass` 9053-9172).

- **Availability** (`AVAIL`, `genRaceAvailability` 6842-6870) is an index into `Area.THEME_PHRASE_EXT` (Area.java:735). A race is available in area themes, not globally.
- **Disabled flags** (`DISFLAGS`, `genDisableFlags` 6746-6790) are six bits named in `Race.GENFLAG_DESCS` (Race.java:576). Toggling is by bit arithmetic (6772-6776).
- **Wearable restrictions** (`WEAR`, `genRaceWearFlags` 6792-6840) are bits over `Wearable.CODES`. A bit is set for code index minus one (6805, 6819).
- **Body parts** (`genBodyParts` 7135-7177) are a per-part integer mask, indexed by `Race.BODYPARTSTR`.
- **Physical adjustments** (`ESTATS`, `genPStats` 7179-7267) are one serialized PhyStats string. If every value is zero the key is cleared (7254-7255).
- **Class names and levels.** `NUMNAME`, `NAME<i>`, and `NAMELEVEL<i>` define title sets, and each level must be higher than the one before (9072-9089). Security code sets follow the same rule (9131-9153). `ARMORMINOR` is `CMMsg.TYP_CAST_SPELL` or -1 for "Armor restricts only spells" (9104-9106).
- **Class abilities** (`genClassAbilities` 8708-8767) are records `GETCABLE<i>` with companion keys `GETCABLELVL`, `GETCABLEPROF`, `GETCABLEGAIN`, `GETCABLESECR`, `GETCABLEMAXP`, `GETCABLEPARM`, `GETCABLEPREQ` and `GETCABLEMASK`. They are grouped by unlock level and shown as a table with columns Lvl, Skill, Proff, Gain, Secret, Parm and Preq (8758-8767).
- **Dynamic lists** (racial or government abilities, effects, immunities; `genDynamicAbilities` 8213-8287) are `GETRABLE<i>` records, serialized as `ID;LVL;QUAL;PROF;PARM;ROLE` (8245). An ability is refused if it is archon-only and the builder lacks it (8275-8277), if it is autoinvoked (unless it is a language, 8282), or if it has no trigger words (8285).

### 10. Ability-family editors

Abilities, tweaked abilities, languages, craft and wright skills, commands, traps, poisons and gathering skills each use one `promptStat*` call per field, with help text and choice lists where needed. Field lists are in the catalogue below.

- **Ability (9343-9409)**: a single `CMParms.toListString(Ability.ACODE.DESCS)` and `DOMAIN.DESCS` choice list sets `CLASSIFICATION`; `RANGE_CHOICES` sets the minimum and maximum range; `USAGE_DESCS`, `CAN_DESCS` and `QUALITY_DESCS` supply the other choices.
- **Language (9480-9600)**: word lists are stored per word length, `WORDS1` through `WORDS<n>` (9497).

### 11. Behaviors, effects and mob skills

- **Behaviors** (`genBehaviors` 4601-4709). Typing an ID adds the behavior, or removes it if it is already present. A new behavior asks for parameters, and `?` shows its help (4677-4690). Only savable behaviors are listed (4614), and `@` in the list is displayed as `_` (4625).
- **Effects** (`genAffects` 4814-4922; list version `genAffectsList` 4712-4811). Same pattern. Parameters are stored as the effect's misc text (4908). New effects are added as non-uninvokable (4910). Archon-only abilities are blocked unless the builder has them (4881-4884).
- **Mob skills** (`genAbilities` 5426-5520). `Skill(params)` adds a skill with parameters in one line (5462-5467).

### 12. Planar rules (`modifyPlane`, 10188-10566)

Read by label only. It edits a key=value rule map with labels such as Alignment, Atmosphere, Like Plane, Mix Race, MOB copies, Recover Rate, Transitional, and Level Adjustments (10344-10524). Several rules use help text taken from property entries, for example `Prop_AbsorbDamage` and `Prop_StatAdjuster` (10296-10336).

### 13. Messages builders see

Short samples, quoted from code: `(no change)`, `'X' added.`, `'X' removed.`, `Unknown race! Try '?'.`, `Final class may not be removed.  To change a class, add the new one first.`, `This level may not be less than N.`, `That was not one of your choices.`, `You do not have clearance to add security code 'X' to this class.`, `253 character limit.  Please confirm:` (12309), and the cataloged-object prompt `This object is cataloged. Enter U to update the cataloged version, or D to detach this object from the catalog, or C to Cancel (u/d/C)?` (1034).

## Content worth mining

Field catalogue. Labels are the strings shown to the builder. Keys are the stat keys where the code shows them. Line numbers point at the prompt.

### NPC mob (`modifyGenMOB`, 11584-11696)

| Field (label) | Editor (line) | Notes |
|---|---|---|
| Name, Display, Description | genName 969, genDisplayText 1700, genDescription 1656 | Display is shown as `Enter something new (null = empty)` |
| Level | genLevel 3116 | Rescores combat stats the first time level exceeds 1 |
| Rejuv | genRejuv 3123 | `0=never` for MOBs |
| Race | genRace 4197 | Applies racial effects at once |
| Faction (special) | genSpecialFaction 4019 | One per faction with `showInEditor` (11612-11617) |
| Gender | genGender 4112 | Letter from ListFile.GENDERS |
| Height, Weight | genHeight 3458, genWeight 3412 | |
| Clan | genClan 1335 | Not read |
| Actions/Attacks per tick | genSpeed 3555 | |
| Attack Adjustment, Damage | genAttack 3525, genDamage 3530 | |
| Armor (lower-better) | genArmor 3566 | Lower is better for MOBs |
| Hit Points Bonus Modifier | genHitPoints 3399 | Formula text at 3402 |
| Money, Money Variation | genMoney 3574, line 11628 | |
| Abilities, Behaviors, Effects | 5426, 4601, 4814 | |
| Disposition, Senses | genDisposition 2601, genSensesMask 2930 | |
| Rideable: type, ride capacity, mount text, mount-ride text | 4924, 4957, 1728, 1762 | |
| Drink: thirst quenched, held, drink type | 6550, 6555, 2123 | Drink-type MOBs |
| Deity: cleric requirement, cleric ritual, worship requirement, worship ritual, blessings, cleric sin, worshipper sin, curses, cleric power ritual, powers, service ritual | 5916-6195 | Not read in full |
| Factions, Tattoos, Expertises, Scripts, Image, Tags | 4059, 4326, 4464, 10856, 1066, 4365 | Expertises are lessons; tattoos are name strings |

### Player (`modifyPlayer`, 11700-~11867)

Account expiration (11752-11756), password reset (1667), description, level, race, class (4230), stats (2848), character state (2891), special factions, gender, height, weight, clan, deity, speed, attack, damage, armor, TPQ (3494, not read), bonus creation points (2824), abilities, effects, behaviors, disposition, senses, rideable, factions, tattoos, expertises, titles (4402), birthday, email, security groups (4510), image, tags, scripts, notes, and flags (`PlayerStats.PlayerFlag`, 11811).

### Item (`modifyGenItem`, 10630-10787) and item sub-types

| Field (label) | Editor (line) |
|---|---|
| Name, Display, Description, Level | 969, 1700, 1656, 3116 |
| Material, Rsc Sub-Type (raw materials), Secret Identity | genMaterialCode 3847, genMaterialSubType 4182, genSecretIdentity 4177 |
| Max Pages, Chars/Page (books) | 10654-10655 |
| Clan item (clan, type) | genClanItem 3417 |
| Manufacturer | 10662 |
| Internal Name, Parent Menu (software) | 10667-10668 |
| Pow Capacity, Pow Remaining, Activated (electronics) | 10673-10675 |
| Panel type | genPanelType 1128 |
| Race ID (false limb) | 10680 |
| Installed Factor, Pwr Recharge Rate (tech components) | 10684-10685 |
| Min thrust, Max thrust, Reaction engine, Fuel Spec Impulse %, Avail. ports (ship engines) | 10690-10694 |
| Max Ports, Avail. ports (directional) | 10699-10700 |
| Message types, damage (ship war components) | genMessageTypes 3785, genDamage 3530 |
| Number of items in the package | 10709 |
| Owner, Price (private property) | 10712-10713 |
| Gettable, Readable, readable text | genGettable 2504, genIsReadable 2092, genReadableTextMisc 2199 |
| Drink type, drink held (liquid holders) | 2123, 6555 |
| Recipe (recipe books) | genRecipe 2359 (not read) |
| Burnout (lights) | genBurnout 1839 |
| Coin stuff (coins) | genCoinStuff 3315 |
| Magical Ability, Technical Level, Bundle Size (-1) | genAbility 3304-3313 |
| Moves per Tick (navigable items) | 11196 |
| Uses Remaining, Ammo Remaining | genUses 3169 |
| Maximum Uses (wands) | genMaxCharges 3177 |
| Enchant Type (wands) | 10739 |
| Base Value, Weight | genValue 3407, genWeight 3412 |
| Max Speed (% of Light) (space weapons) | genSpeed 3555 |
| Property ID (land title) | genTitleRoom 3273 |
| Owner, Price (containers) | 11159-11160 |
| Capacity | genCapacity 3520 |
| Key Code, lid door/lock | 11164, genDoorsNLocks 3005 |
| Corpse data (dead bodies) | genCorpseData 1072 |
| Smells list (perfume) | 10951 |
| Nourishment/Eat, Bite/Eat (0=all) (food) | genNourishment 4187, genBiteSize 4192 |
| Thirst quenched, drink held, container types (drink) | 6550, 6555, 3071 |
| Cloak Name (armor) | 11385. Writes stat READABLETEXT, so the label looks mismatched (unverified) |
| Layer, Size | genLayer 3509, genSize 3463 |
| Worn location (armor) | genWornLocation 6538 |
| Enchant Type (instrument editor, for wands) | 11456 |
| Door and lock (exit) | genDoorsNLocks 3005, called with `door` (11515) |

The room and exit editors and the area editor are listed under Rooms below.

### Rooms, exits and areas

| Editor | Fields |
|---|---|
| `modifyRoom` (12295-12352) | Room type (genRoomType 1618), Display (253 character limit, 12305), Description, Grid locale X and Y (6217, 6222), location coordinates (6227), AutoGen XML file path and AutoGen variables (12324-12325), tags, behaviors, effects, raw stat loop |
| `modifyGenExit` (11498-11558) | Door (`door`, 11515), door name (genDoorName 1817), closed text (genClosedText 1796), open word (genOpenWord 1849), close word (genCloseWord 2028), misc (genExitMisc 2043, not read). Display: `Enter something new (null == see-through)` (1713) |
| `modifyArea` (12528-12617) | AutoGen path and variables (12575-12576), author (genAuthor 1123), currency (genCurrency 1173), time clock (genTimeClock 1204), sub-ops (genSubOps 1864), parent and child areas (1895, 1959), theme (getTheme 3685), blurbs (genAreaBlurbs 5277), player level (genPlayerLevel 6200), economics (5205-5352: prejudice, price factors, budget, devaluation, inventory reset, ignore mask) |

### Races (`modifyGenRace`, 10577-10609)

| Field (label) | Key (line) |
|---|---|
| Name | NAME (10577) |
| Racial Category | CAT (genCat 6902-6955; `new` prefix creates a category) |
| Base Weight, Weight Variance | BWEIGHT, VWEIGHT (10579-10580) |
| Base Male Height, Base Female Height, Height Variance | MHEIGHT, FHEIGHT, VHEIGHT (10581-10583) |
| Availability (theme index) | AVAIL (6842-6870) |
| Disabled flags | DISFLAGS (6746-6790) |
| Breathes | genBreathes 3898 (not read) |
| Leaving text, Arriving text | LEAVE, ARRIVE (10587-10588) |
| Health Race, Event Race, Weapons Race | genRaceBuddy 7001, keys HEALTHRACE, EVENTRACE, WEAPONRACE |
| Body parts | genBodyParts 7135 (BODYPARTSTR mask) |
| Unwearable locations | WEAR (6792-6840) |
| Aging chart | genAgingChart 8125 (not read) |
| Never create corpse, Rideable Player, XP Adjustment % | BODYKILL, CANRIDE, XPADJ (10594-10596) |
| PhysStat adjustments | ESTATS (7179) |
| CharStat adjustments, CharStat settings | ASTATS, CSTATS (genAStats 7344) |
| CharState adjustments, New Player CharState adj. | ASTATE, STARTASTATE (genAState 7269) |
| Resources, outfit, weapons | genResources 7663, genOutfit 7803, genWeapon 8051 |
| Racial abilities, cultural abilities, racial effects, racial immunities | genDynamicAbilities 8213, genCulturalAbilities 8936, genDynamicEffects 8378, genDynamicImmunitiess 8530 |

### Classes (`modifyGenClass`, 9065-9139)

| Field (label) | Key (line) |
|---|---|
| Number of Class Names, Class Name (per level) | NUMNAME, NAME0 / NAME<i>, NAMELEVEL<i> (9065-9089) |
| Level Cap (-1 disables) | LEVELCAP (9090) |
| Base Class | BASE (9091) |
| Availability | genClassAvailability 6872 |
| HP/Level Formula, Mana/Level Formula, Move/Level Formula | HITPOINTSFORMULA, MANAFORMULA, MOVEMENTFORMULA (9093-9101) |
| Prac/Level, Attack/Level | LVLPRAC, LVLATT (9095-9096) |
| Attack Attribute | ATTATT (genAttackAttribute 6561) |
| Practices/1stLvl, Trains/1stLvl, Levels/Dmg Pt | FSTPRAC, FSTTRAN, LVLDAM (9098-9100) |
| Armor Restr. | ARMOR (genArmorCode 6582) |
| Armor restricts only spells | ARMORMINOR (9105) |
| Limitations, Bonuses | STRLMT, STRBON (9108-9109) |
| Qualifications | QUAL (genQualifications 6603) |
| Minimum stat qualifications | genMinimumStatQualifications 7969 |
| Required Races | RACQUAL (genClassRaceQuals 7075) |
| PhysStat adjustments | genPStats 7426, called with skipRejuv=true (9113) |
| CharStat adjustments, settings; CharState adjustments, new-player | ASTATS, CSTATS, ASTATE, STARTASTATE |
| Class flags | genClassFlags 8165 (not read) |
| Weapon restrictions, weapon materials | NUMWEP/GETWEP (6629), NUMWMAT/GETWMAT (6689) |
| Outfit | genOutfit 7886 |
| Starting Money | MONEY (9122) |
| Stat-modifying class, special-events class | STATCLASS, EVENTCLASS (genClassBuddy 7038) |
| Sub Class Switch Rule | SUBRUL (`CharClass.SubClassRule` values) |
| Max Non-Crafting, Max Crafting, Max All-Common, Max Languages | MAXNCS, MAXCRS, MAXCMS, MAXLGS (9126-9129) |
| Class abilities | GETCABLE<i> family (8708-8767) |
| Security code sets, per-set level | NUMSSET, SSET<i>, SSETLEVEL<i> (9131-9153) |

### Ability family

| Editor | Fields (key, line) |
|---|---|
| Ability (9343-9409) | Ability/Skill name NAME (9357); Type, Domain CLASSIFICATION (9358); Command words TRIGSTR (9359); Minimum and Maximum Range MINRANGE, MAXRANGE (9360-9361); Ticks Between Casts TICKSBETWEENCASTS (9362); Duration Override TICKSOVERRIDE (9363); Affect String DISPLAY (9364); Is Auto-invoking AUTOINVOKE (9365); Skill Flags FLAGS (9366); Override Cost OVERRIDEMANA (9367); Cost Type USAGEMASK (9368); Num. Arguments NUMARGS (9369); Can Affect CANAFFECTMASK (9370); Tick/Periodic Affects TICKAFFECTS (9371); Can Target CANTARGETMASK (9372); Quality Code QUALITY (9373); Affect Adjuster (9374); Caster Mask CASTMASK (9375); Scriptable Parm SCRIPT (9376); Target Mask TARGETMASK (9377); Attack-Type ATTACKCODE (9383); Quiet effect ID MOCKABILITY (9384); Public effects POSTCASTAFFECT (9385); Extra castings POSTCASTABILITY (9386); Damage/Healing Formula POSTCASTDAMAGE (9387); Help Text HELP (9388) |
| Tweak ability (9410-9479) | Base Ability/Skill ID ORIGINAL (9431); name, type, command words, ranges, ticks, affect string, flags, override cost, cost type, quality, help (9432-9458) |
| Language (9480-9600) | Language name NAME (9492); WORDS1..WORDS<n> by length (9497); Is Natural NATURALLANG (9576); Source Verb VERB, Translation Verb TRANVERB (9577-9578); Help HELP (9579) |
| Craft skill (9601-9646) | Skill name, verb, command words, Recipe filename FILENAME (9613-9616); Can mend, Can refit, Can bundle, Can sit, Need fire, No hardness bonus (9618-9623); MSP file SOUND (9624); Help (9625) |
| Wright skill (9647-9691) | Skill name, verb, command words, Recipe filename (9659-9662); Can mend, Can make doors (CANDOOR), Can re-title (CANTITLE), Can re-desc (CANDESC), Clan only (9664-9668); MSP file, Help (9669-9670) |
| Command (9692-9746) | Command words ACCESS (9704); Can be Ordered ORDEROK (9705); Security Mask SECMASK (9706); Action Cost ACTCOST, Combat Cost CBTCOST (9708-9709); Help HELP (9710); Script SCRIPT (9711) |
| Trap (9747-9800) | Trap name NAME; Level BASELEVEL; Is Bomb ISBOMB; Can Affect; Can Target; Reset Ticks PERMRESET; Avoid Msg AVOIDMSG; Spring Msg TRIGMSG; Damage Msg DAMMSG; Damage Formula DMGF (help text `dmgHelp`); Damage Type DMGT (`Weapon.TYPE_DESCS`); Dmg Msg Type DMGM (`CMMsg.TYPE_DESCS`); Ability Parms ABILTXT; A.Tick Override ABILTIK; Scriptable Parm SCRIPT; Help HELP (9761-9779) |
| Poison (9801-9859) | Poison name NAME; Command Words TRIGSTR; Duration Formula TICKS; Delay Formula DELAY; Start Msg STARTMSG; Target Tell TARGTELLMSG; Affect Msg AFFECTMSG; Damage Formula DAMAGE; Mood MOOD; Affect Target AFFECTTARG and Force Peace MAKEPEACE (9827-9828); formula help text covers invoker level, expertise, affected level and rank |
| Gathering skill (9860-9912) | Skill name, verb, command words; Room Mask ROOMMASK, Player Mask PLAYERMASK (9875-9876); Yield Formula YIELDFORMULA (9878); Start, Found, Not Found, Complete messages MSGSTART, MSGFOUND, MSGNOTFOUND, MSGCOMPLETE (9879-9882); Min. Ticks MINDUR, Base Ticks BASEDUR, Found @ Tick FINDTICK (9885-9887); Is Cosmetic Only ISCOSMETIC; Can sit; Can bundle; MSP file SOUND; Help (9888-9892) |

### Clans, governments and positions

- Position (`modifyClanPosition`, 9174-9216): Simple ID, Name, Name (Plural), Rank (low=better), Maximum, Position Mask, Is Shown, Powers, Title Awards (9184-9196).
- Government (`modifyGovernment`, 9271-9342): Type Name, Category, Short Desc, Long Desc, Member Mask, Is Public, Is Family, Is Rivalrous, Minimum Members (9281-9289); XP Per Level Formula, Conquest Enabled, Conquest Item Loyalty, Conquest by Worship, Vote Approved powers, Max Vote Days, Vote Quorum (Pct%), Auto-Promotion, Apply Position, Accept Position, Entry and Exit Scriptable, Misc Settings, Title Awards (9295-9318).
- Clan (`modifyClan`, 12075-12133): Morgue, recall and donation rooms; status; government; roles; classes; acceptance; members; flags (`Clan.ClanFlag`, 12098).

### Shopkeepers, bankers and auctions

Banker: coin interest per real day, item interest per real day, bank chain, loan interest per mud month (3535-3553). Auctioneer: timed listing price, timed listing percent, final-cut percent, minimum and maximum timed-auction days (prompts in 12134-12294, helper `genAuctionPrompt` 11934-11962). Craft broker: maximum timed-listing days, maximum listings, commission percent. Economics: price factors, budget, devaluation rate, inventory reset, ignore mask (5205-5352).

### Flag, choice and option lists in the code

- **Dispositions** (`genDisposition`, 2606-2620): invisible, hide, unseen, magical, glowing, light source, fly, climb, sleep, sit, sneak, swimmer, evil, good, unattackable. Fifteen flags, toggled one at a time.
- **Race disable flags** (Race.java:576): CLASSLESS, LEVELLESS, EXPLESS, CHARMLESS, CHILDLESS, INFATIGUEABLE.
- **Weapon attack types** (3661): letters N, S, P, B, F, M, R, mapped to `Weapon.TYPE_DESCS` by index.
- **Weapon classes** (4153): letters A, B, E, F, H, K, P, R, S, D, T, N, mapped to `Weapon.CLASS_DESCS`.
- **Container types** (3097-3111): `Container.CONTAIN_DESCS`, where index 0 clears the restriction.
- **Area themes** (Area.java:735): the availability list for races. The first entry is "Unavailable".
- **Genders**: `ListFile.GENDERS`, one letter each (4117-4139).
- **Sub-class switch rule**: `CharClass.SubClassRule` values (9125).
- **Wand usage**: `Wand.WandUsage.WAND_OPTIONS` (10739).

### Useful text for a game

- Formula help (`MATHFORMULA`) is shown for every HP, mana, move, yield and damage formula (9093-9101, 9387, 9878). Writing these as one shared formula reference is a good model for a Python game.
- Hit points help: `Hit points = (level*level) + (random*level*THIS)` (3402).
- Language word lists by length (9492-9497) support a simple word generator.

## Notable design ideas

- **One menu, two uses.** Each object's menu is a sequence of calls. The same sequence displays the menu, edits one field (N), edits all (-999), or only displays (-950). That avoids keeping a separate schema and editor, though it costs the hand-copied loops described below.
- **Stat keys as the data model.** Editors read and write `getStat`/`setStat` string keys, so a new field needs a key, not a new editor. Generic fallback loops pick up any remaining stat code (10759-10760).
- **Catalog as template.** A cataloged object is a template with instances. Editing an instance offers to update the template, detach from it, or cancel, after a field-by-field diff (1017-1062).
- **Availability by theme.** A race's availability is an index into the area theme list (6842-6870), so races belong to regions rather than the whole world.
- **Room-type change keeps the world.** `changeRoomType` swaps a room's class in place: it keeps its ID, moves occupants and items, repoints doors across the map, and rebuilds savable mobs (1433-1616).
- **Multi-class by levels.** A character's levels are split across classes, and a new class takes the free levels or siphons them from the highest class (4293-4313).
- **Typed prompt primitives.** Choice, multi-choice bitmask, delimited-list toggle, boolean and numeric prompts each have one helper, and all are reusable outside the object editors (see Section 3 above).

## Problems and cruft

Each item below was checked by reading the cited lines. The effect at runtime is inferred from the code, not run.

1. **ClassCastException for a negative MOB rejuv value.** `genRejuv` (3142-3145) casts `P` to `Item` when `newLevel<0`, without checking `P instanceof Item`. `modifyGenMOB` (11608) and `modifyStdMob` (12456) call it on MOBs, so a negative entry should throw.
2. **Negative availability breaks the menu.** `genRaceAvailability` (6857-6858) accepts any number below the list length, with no lower bound. The display at 6847 then indexes `Area.THEME_PHRASE_EXT[...]` with a negative value. Whether `CMath.isNumber` accepts a leading minus is unverified.
3. **Weapon ranges reset on a blank field.** In `genWeaponRanges` (3637-3648) a blank input becomes 0. Changing only the minimum, with a blank maximum, stores max 0. The defective-range check (3644) then resets both values to 0 when the minimum is positive.
4. **Wrong message and silent retry in the property ID editor.** `genTitleRoom` (3282-3290) prints "That property (room ID) doesn't exist!" only when the entry is empty. An unknown room ID is silently re-prompted.
5. **Wrong variable in a multi-choice message.** `promptMultiOrExtra` (766) prints `newVal` (the current value) where the typed choice belongs. Line 777 compares strings with `==` (`foundVal == "0"`), which works only for interned literals.
6. **Ability parameters never show in lists.** `genAbilities` (5441-5442) appends the text `()` instead of the parameter value.
7. **Dead length check.** `maxLength` is `Long.MAX_VALUE` (73). The check `text().length()>=maxLength` (for example 10784, 11694) is always false. The same check appears in about ten editors.
8. **Container type matching is last-match-wins.** `genContainerTypes` (3097-3101) keeps the last prefix match, so an ambiguous prefix silently picks the last entry.
9. **Reset delay cannot be 0.** `genDoorsNLocks` (3040-3044) only accepts values greater than 0.
10. **No lower bound on class levels.** `genCharClass` (4299-4301, 4308-4311) does not clamp negative level entries. Whether `setClassLevel` guards this is unverified.
11. **Stats cannot go negative.** `genCharStats` and `genCharState` refuse values below zero (2815-2817, 2875-2877, 2920). This is probably deliberate, but it blocks legitimate negative values such as penalties.
12. **Copy-paste.** The `Edit which?` loop is written 37 times. The character-stat menu is duplicated for `CharStats` (2798) and `MOB` (2848). Each object menu repeats the same prologue, epilogue and recalculation branches. A change to the protocol has to be made in dozens of places.
13. **Positional numbering.** Field numbers come from call order, and conditional fields (for example `if(me instanceof Book)`) renumber everything after them. Class-name loops also step `showNumber` backwards (9084, 9148) to re-prompt the same line.
14. **Label and key mismatch (unverified).** The field labelled "Cloak Name" in the armor editor (11385) writes `READABLETEXT`.
15. **Blank parameters may clear (unverified).** At 4681 the parameter prompt has no default value, so a blank entry is probably stored as empty parameters. Not checked against `Session.prompt`.
16. **Inconsistent list truncation.** Mob skill summaries are cut to 50 characters in list mode (5449-5450), while effect summaries are not cut (4733). The two lists behave differently for long entries.

## Takeaways for a Python SUD

- **Describe fields as data, not as menu code.** A field descriptor (key, label, type, bounds or choices, help text, group) can drive both the menu and the editor. Something like `Field("level", "Level", int, lo=1)` on a dataclass or a per-type list gives the same single-source benefit without the `showNumber`/`showFlag` integers.
- **Use explicit modes.** Replace the -1, N, -999, -950 integers with named modes: list, edit one, edit all, view only. Skip the post-edit recalculation as an explicit flag, not a magic number.
- **Keep the typed primitives.** Choice (`Enum`), bitmask (`enum.Flag`), delimited set (`frozenset`), bounded number, boolean, and free text. Each should validate and re-prompt on its own. Validators can be plain callables instead of the `CMEval` interface.
- **Stat-key storage vs typed fields.** Stat keys make persistence trivial (one dict to JSON), but the CoffeeMud keys are strings, which causes the bugs above. In Python, typed dataclass fields with a generated key map give the same convenience with checked types.
- **Templates and instances.** For NPCs and items, a prototype store with per-instance overrides, and an "update template / detach / cancel" choice after a diff, fits a long-lived world. It is worth keeping; the diff display (1026-1031) is a good pattern.
- **Edit data files, not the live game.** A single-player game does not need in-game editing, MCP or GMCP clients, permission clearance (`CMSecurity`), password and account fields, or per-player bonus counts. Keep the field lists as YAML or TOML, validated at load time, and drop the rest.
- **Area-gated races and content.** Keeping availability as a theme or region index (6842-6870) is a neat way to make a race or item appear only in some places. Keep the idea, express it as a set of region tags.
- **Room type change without rebuilding.** The sequence in `changeRoomType` (1433-1616) is a model of what must move when a place changes kind: occupants, items, exits, and anything that refers to the place. In Python, change a room's class and re-point references by ID rather than copying objects.
- **Simulation pause during edits.** `suspendTicking` (3193-3194) exists for multi-user safety. A single-player game that edits a running world should pause its own clock during edits, but it does not need the multi-user variant.
- **Multi-class by levels.** If the SUD has multiple classes, the level-splitting in `genCharClass` (4293-4313) is a simple rule to borrow, with the bounds checks added.
- **Drop.** Security and clearance checks (4560-4588), MCP and GMCP editing, the Player Account editor, the Clan editor, deity editing, and the shop auction setters, unless the SUD has those systems.

## Coverage

- **Read in full:** `com/planet_ink/coffee_mud/Libraries/interfaces/GenericEditor.java` (1-1106).
- **Read in large part:** `com/planet_ink/coffee_mud/Libraries/CMGenEditor.java`, about a third of its 12,704 lines. Covered ranges: 60-812 (prompt primitives), 969-1072 (name, catalog, image), 1433-1728 (room type change, room type, descriptions), 2592-2680 (dispositions), 2798-2928 (character stats and state), 3005-3315 (doors and locks, container types, levels, router, misc text), 3399-3470 (hit points, value, weight, height), 3509-3684 (armor layers, money, weapon fields), 4059-4335 (factions, gender, race, class), 4601-4924 (behaviors, effects), 5426-5475 (mob skills, part), 6461-6548 (worn location), 6746-6870 (race flags and availability), 7135-7267 (body parts, race PhysStats), 8213-8287 and 8708-8767 (dynamic and class ability lists, part), 9053-9172 (class editor), 10567-10627 (race editor), 10630-10786 (item editor), 11584-11696 (NPC editor), 12295-12352 (room editor), 12433-12524 (standard MOB and item editors).
- **Mapped by label and signature only (grep and listing, not read):** `modifyPlane` (10188-10566), `modifyComponents` and the component editor (10012-10187), `modifyPlayer` body, `modifyClan` body, `modifyGenShopkeeper` body, `modifyAccount` body, `modifyArea` body, `modifyGenExit` body, `modifyGenContainer`, `modifyGenWeapon`, `modifyGenArmor`, `modifyGenInstrument`, `modifyGenFood`, `modifyGenDrink`, `modifyGenWallpaper`, `modifyGenMap` bodies, `modifyGovernment` and ability-family bodies (only their labels were read), deity sub-editors (5888-6235), space editors (6235-6460), `spellsOrBehavs` (5521-5752), clan members and roles (5753-5887, 11963-12044), aging chart, resources, outfit, weapon, genRecipe, genBreathes, and the economics sub-editors. Every `promptStat*` call site (237) and about 1,300 label lines were scanned with grep.
- **Other files:** `core/CMProps.java:321,3109`, `coffeemud.ini:1277-1279`, `Commands/Create.java:359,858`, `Commands/Stat.java:1388,1556`, `Commands/Modify.java:1329-1331` (read for the editor-type convention only). `Races/interfaces/Race.java:576` and `Areas/interfaces/Area.java:735` (read for the constant lists only).
- **Not checked:** runtime behavior of any finding above. Items marked "unverified" depend on library behavior that was not read (`CMath.isNumber`, `Session.prompt`, `setClassLevel`).
