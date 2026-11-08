# Guides for administrators and scripters: ArchonGuide and Scriptable

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c12` (content: Guides for administrators and scripters: ArchonGuide and Scriptable) · 2 files · 663006 bytes of data.

## Summary

These two HTML manuals are the working reference for everyone who builds or runs a CoffeeMud world. `ArchonGuide.html` (the builder and administrator's guide, written for CoffeeMud 5.11) covers the admin command set, the security-group model, room locales, exits, areas, and the "Gen" property sheets for items, mobs, shopkeepers and races. Its appendices give the standard item and mob lists and a weapon and armour balance system whose formulas I checked against the sample table. `Scriptable.html` documents the event-trigger scripting language that descends from MOBPROGs: 60 triggers, about 100 commands, 119 functions and a `$`-code substitution scheme. The code behind that language is `DefaultScriptingEngine.java`, which is a 16,435-line interpreter with separate switch blocks for the veto phase, the reactive triggers, the timed triggers and the commands. For a single-player Python game, the valuable parts are the generic object model, the two-phase "veto, then react" event flow, the timed-trigger queue, and the balance formulas. The social and multiplayer parts (shared boards, auction houses, clans, channels, player-versus-player flags) should be dropped.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `guides/ArchonGuide.html` | Builder and administrator manual: admin commands, security, locales, exits, Gen property sheets, shop economy, appendices A to D | 11,263 |
| `guides/Scriptable.html` | Scripting reference: triggers (lines 640 to 2854), `$` codes (2854 to 3340), commands (4465 to 6857), functions (6857 to end) | 8,838 |
| `com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java` | Interpreter: parsing, trigger dispatch, command execution, variables, delayed response queue | 16,435 |
| `com/planet_ink/coffee_mud/Common/interfaces/ScriptingEngine.java` | Name tables `progs` (line 430), `funcs` (496), `methods` (620); `MPContext` (732); `SPECIAL_*` temp slots (421 to 427) | 808 |
| `com/planet_ink/coffee_mud/Behaviors/Scriptable.java` | Behaviour wrapper; delegates to the engine and can attach to mobs, items, rooms, exits and areas (lines 44 to 47) | 278 |
| `com/planet_ink/coffee_mud/Libraries/CommonMsgs.java` | `<VARIES>` description selection (`parseVariesCodes` at 2935, `parseVaries` at 3045) | 3,051 |
| `com/planet_ink/coffee_mud/Libraries/CoffeeShops.java` | Shop prejudice factors (601 to 676), devaluation (932 to 950), budget and devalue parsing (855 to 930) | 2,909 |
| `com/planet_ink/coffee_mud/Libraries/BeanCounter.java` | Currency definition parser, including `notrade` and short names (150 to 200) | 1,958 |
| `com/planet_ink/coffee_mud/Libraries/Dice.java` | `roll(n, die, mod)` (398 to 406) and `rollPercentage()` returning 1 to 100 (409 to 412) | 414 |
| `com/planet_ink/coffee_mud/Libraries/MUDZapper.java` | Zapper mask compiler and `maskCheck` (5678, 5698) | 10,244 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/GenericBuilder.java` | Stat-code enums `GenItemCode` (25 codes, line 56) and `GenMOBCode` (24 codes, line 105) behind every Gen property | 1,323 |
| `com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java` | Get and set of Gen stats (`getAnyGenStat` at 3352); XML packing of Gen misc text | 6,719 |
| `com/planet_ink/coffee_mud/core/CMSecurity.java` | Security groups (`GROUP_` prefix, 276), `AREA` privilege paths (306, 321), staff-only checks (608 to 640), SysOp mask | 3,068 |
| `com/planet_ink/coffee_mud/Items/Basic/GenItem.java` | Generic item: state stored as text, read and written through the stat-code API | 128 |
| `com/planet_ink/coffee_mud/MOBS/GenMob.java` | Generic mob, same pattern as GenItem | 121 |
| `com/planet_ink/coffee_mud/Commands/Create.java`, `Modify.java`, `Destroy.java`, `Link.java`, `UnLink.java`, `Catalog.java`, `Export.java`, `Import.java`, `Save.java`, `SysMsgs.java`, `TickTock.java` | Admin commands the guide documents (`SysMsgs` message at 65; `TickTock` at 72 to 73) | Create 2,682; Modify 3,427; Export 873; Import 7,618; Save 428; Catalog 936; TickTock 212 |
| `com/planet_ink/coffee_mud/Locales/*.java` (128 files), `Exits/*.java` (31), `Areas/*.java` (15) | Room locale, exit and area classes that the guide's lists name | varies |
| `coffeemud.ini` (CoffeeMUD folder root) | Defaults the guide cites: `SYSOPMASK` (212), `BUDGET` (1825), `DEVALUERATE` (1839), `TICKTIME` (2151) | n/a |

## How it works

### 1. What the manuals describe

`ArchonGuide.html` is organised as a tour: administration (lines 30 to 330, a list of 52 commands from AHELP to WIZINV), security (333 to 390), planning (390), then rooms (409), exits (1056), areas (1657), items (2447), mobs (4639), races (6129), the catalog (6072), saving (6388) and appendices A to D (6414 onward). Where a guide claim can be checked against the code, section by section below says whether it holds. Claims I could not check are marked as such.

`Scriptable.html` is an alphabetical reference. Each trigger has a "Usage" line, a description and examples. The command and function sections follow the same pattern, and the `$` codes and `$%function()%` substitutions are described in the codes section (2854 to 3340).

### 2. Administration and security

- The admin commands are a flat list (`ArchonGuide.html:36` to `:330`). Many map to `Commands/*.java` classes of the same name; the guide's `SYSMSGS` description ("extended messages") matches `Commands/SysMsgs.java:65`, though the message text in code is "Extended messages are now : ON" with a space before the colon, so the guide's quoted string is slightly off.
- Archon (SysOp) status is a zapper mask in config: `SYSOPMASK=-ANYCLASS +Archon` (`coffeemud.ini:212`), read in `MUD.java:1653` and `CMSecurity.setSysOp`. The guide says the same (`ArchonGuide.html:346`).
- Security groups are declared in config under keys beginning `GROUP_` (`CMSecurity.java:276` to `:283`). A group is a name plus a set of flags. A player's security entry can be a group name or a single code.
- Area-only privileges are written with a leading `AREA ` (guide `ArchonGuide.html:382`). The code applies the restriction through the path check `(!p.isAreaOnly)||subop` (`CMSecurity.java:634`, and the same pattern at 710, 745, 789). `subop` means the player is listed as staff of the room's area (`room.getArea().amISubOp(name)`). So a staff member gets area-scoped powers only inside areas that list them.
- Messages a player sees: `TickTock` replies "..tick..tock.." and then advances the area's time object (`Commands/TickTock.java:72` to `:73`).

### 3. Rooms, locales, exits and areas

**Locale.** Every room has a locale class that decides weather, light, swim or drown behaviour, resources, and movement cost. The code has 128 `Locales/*.java` files. The guide names all but four of them (`IndoorUnderWaterThinGrid`, `MetalRoom`, `MetalRoomMaze`, `UnderwaterCityStreet` are not named).

Movement cost is per room and is set in each locale's constructor. I checked the guide's table against the code:

| Cost | Guide says (`ArchonGuide.html:1038` to `:1043`) | Code check |
|---|---|---|
| 1 | street, road, sky, most indoor | `CityStreet` 1, `Road` 1, `InTheAir` 1, `StoneRoom` 1 |
| 2 | cave, desert, plains, water surface | `CaveRoom` 2, `Desert` 2, `Plains` 2 (`Locales/Plains.java:47`), `WaterSurface` 2; base `StdRoom` 2 (`Locales/StdRoom.java:91`) |
| 3 | hills, forest, jungle, underwater, swamp | `Hills` 3, `Woods` 3, `Jungle` 3, `UnderWater` 3, `Swamp` 3 |
| 4 | climbing surface | `ClimbableSurface` 4 |
| 5 | mountainous | `Mountains` 5 |
| 6 | mountain climbing surface | `MountainSurface` 6 |
| 3 to 4 | LargeXRooms | `LargeStoneRoom` 3 |

All checked values match. The guide also gives weapon range sizes (outdoor 10, indoor 1, caves 5, `ArchonGuide.html:1037` to `:1038`). I did not verify those.

**Area types.** The guide lists 11 area types. The code has 15 `Areas/*.java` classes; `Moon`, `PlayerInstanceArea` and `StdThinPlanet` are not in the guide. Three types matter for design: `StdThinArea` (rooms loaded on demand and unloaded after idle time, `ArchonGuide.html:1749` to `:1771`), `StdThinInstance` (each group gets its own copy of the area, `:1771` onwards) and `StdAutoGenInstance` (a random area generated from an XML definition, with formulas using `@x1` to `@x7`, the player level, lowest, median, average, highest, total levels and group size). The `@x` token is implemented in `Behaviors/ItemIdentifier.java:66` as a formula parameter; I did not trace the random-area generator itself.

**Exits.** The guide's exit list has about 26 names, and the code has 31 `Exits/*.java` classes. Not named in the guide: `AjarDoor`, `GenAirLock`, `NamedRedirectable`, `OpenPrepositional`, `Skyway`. `GenExit` and `GenDoor` are the fully editable exits. Their properties (name, description, display, level for pick or knock, door or gate noun, open and close verbs, reset delay in ticks, key code) are documented at `ArchonGuide.html:2255` to `:2447`.

**Description variation.** Room and item text can vary with time, season and weather by wrapping it in `<VARIES>` with tags such as `<DAY>`, `<NIGHT>`, `<RAIN>`, `<WINTER>`, or a zapper mask `<MASKn -RACE +DWARF>`, with `<ELSE>` as fallback (`ArchonGuide.html:1372` to `:1391`). The parser is `CommonMsgs.parseVariesCodes` (`Libraries/CommonMsgs.java:2935`), entered from `parseVaries` (3045). Grid and maze rooms can carry several title and description alternatives separated by `<P>` and pick one at random (`ArchonGuide.html:1423` to `:1425`).

### 4. Generic objects: the data model behind "Gen" items and mobs

Every Gen class exposes its state as a flat list of stat codes:

- `GenericBuilder.GenItemCode` has 25 codes (`CLASS`, `USES`, `LEVEL`, `ABILITY`, `NAME`, `DISPLAY`, `DESCRIPTION`, `SECRET`, `PROPERWORN`, `WORNAND`, `BASEGOLD`, `ISREADABLE`, `ISGETTABLE`, `ISDROPPABLE`, `ISREMOVABLE`, `MATERIAL`, `AFFBEHAV`, `DISPOSITION`, `WEIGHT`, `ARMOR`, `DAMAGE`, `ATTACK`, `READABLETEXT`, `IMG`, `TAGS`). `GenMOBCode` has 24 (`CLASS`, `RACE`, `LEVEL`, `ABILITY`, `NAME`, `DISPLAY`, `DESCRIPTION`, `MONEY`, `ALIGNMENT`, `DISPOSITION`, `SENSES`, `ARMOR`, `DAMAGE`, `ATTACK`, `SPEED`, `AFFBEHAV`, `ABLES`, `INVENTORY`, `TATTS`, `EXPS`, `IMG`, `FACTIONS`, `VARMONEY`, `TAGS`).
- `GenItem.getStat` and `setStat` route through `CoffeeMaker.getGenItemStat` and `setGenItemStat` (`Items/Basic/GenItem.java`, `Libraries/CoffeeMaker.java:3352`). Unknown codes fall through to a generic extension map (`CMProps.getStatCodeExtensionValue`).
- The whole object is stored as XML inside the misc text field: `GenItem.text()` returns `getEnvironmentalMiscTextXML`, and `setMiscText` unpacks it. `GenMob` follows the same pattern (`MOBS/GenMob.java:83` to `:87`), so each object is stored as one string.
- `GenMOBCode.getAllCodeNames()` is a copy-paste bug. It builds its array from `GenItemCode.values()` (`Libraries/interfaces/GenericBuilder.java:134` to `:143`), so the mob list used by `MUDPercolator.java:73` (`MOB_IGNORE_STATS`) is the item list. See "Problems and cruft".

The guide's property tables map onto these codes plus per-class extras. For example, the GenWeapon sheet has attack adjustment, damage, attack type (bashing, slashing, piercing, natural), weapon classification (blunt, dagger, edged, flailed, hammer, karate, polearm, ranged, sword), two-handed, magical ability, condition 0 to 100, minimum and maximum range, ammunition type and capacity (`ArchonGuide.html:2857` to `:2987`). GenArmor adds worn locations with a MAY or MUST flag, a layer number (a layer must be two above the item it covers), protection, magical ability, capacity and size (`:3140` to `:3234`).

The guide's "Gen" family is large. The `Gen*` classes in code number 202 (across `Items`, `MOBS`, `Exits`, `Areas`, `Abilities/Common`, `Behaviors`). 105 of those names never appear in the guide. Many of the missing ones are space, tech, clan and crafting items (for example `GenShipThruster`, `GenElecPanel`, `GenGun`, `GenPlayingCard`, `GenHat`, `GenTent`, `GenStove`). They are presumably covered in other documents; I did not search the help files.

**Properties and behaviours on objects.** Rooms, exits, items, mobs and areas accept two kinds of attachments, both listed in the guide with `ahelp` pointers:

- Behaviours (a list attached to each object): for example `Emoter`, `Sounder`, `Patroller`, `Follower`, `Mobile`, `Scriptable`, `DelayedTransporter`, `ItemGenerator`, `RandomMonsters`, `ResetWhole`. About 98 behaviour classes exist in `Behaviors/`.
- Effects and properties (`Prop_*`, `Spell_*`, `Chant_*`, `Disease_*`, `Trap_*`, `Bomb_*`, `Poison_*`). The guide names 100 distinct `Prop_` names. Examples: `Prop_ReqLevels`, `Prop_ReqRaces`, `Prop_ReqCapacity` (with `weight=715` style parameters), `Prop_WearAdjuster`, `Prop_HaveSpellCast`, `Prop_LimitedItems`, `Prop_LocationBound`. Entry rules use zapper masks.

The sum of these is a building kit: almost every object has a type, optional behaviours, and a list of effects.

### 5. Mobs, races, shopkeepers and deities

**Mobs.** A GenMob has these editable fields, with the defaults and ranges the guide gives: level (experience and alignment drive XP), attacks per round (1 is normal), attack adjustment and damage per hit (kept at defaults), armour (lower is better, 50 is weak, 100 is helpless), hit points per level (default 11), alignment, money and money variation, weight, height, rejuvenation ticks, abilities, behaviours, effects, factions, and senses and disposition flags (`ArchonGuide.html:4672` to `:4884`). Rejuvenation ticks of 0 or 2147483647 mean the mob never returns once killed (`ArchonGuide.html:5999`; for items, `:4547`).

**Shopkeepers.** `GenShopKeeper` (`ArchonGuide.html:4884` onward) is a Gen mob with a sell type (anything, armour, weapons, pets, potions, land, trainers, casters, and combinations), a view flag (BASIC, IDENTIFY, or FALSE, which fudges numbers), an item buy mask, stock, and an optional price factor. Price encodings, from `ArchonGuide.html:4986` to `:5006`:

- `0` or higher: a fixed price in base value.
- `-1`: the engine prices the item.
- `-101` to `-999`: quest points, `|price| - 100`.
- `-1001` or lower: experience, `|price| - 1000`.
- `-2` to `-100` and `-1000`: invalid.

Inn-keeping uses keys `INN1`, `INN2` and so on, matched to locked doors (`:4908`, `:6497`).

**Economy settings.** These can be set per shopkeeper, per area, or globally in `coffeemud.ini`, in that order of precedence (`ArchonGuide.html:2100` to `:2250`):

- Budget: maximum money a shop will spend buying from players in a period. The guide's default is "100 ticks" (`ArchonGuide.html:2100`); the shipped default is `BUDGET=500000 MONTH` (`coffeemud.ini:1825`), and the guide's own unit list (HOUR, DAY, WEEK, MONTH, YEAR, `:2103`) has no tick unit. Unit parsing is in `CoffeeShops.parseBudget` (`Libraries/CoffeeShops.java:855`).
- Devaluation: stock-size devaluation. Verified in code: the buy price is multiplied by `1 - rate * n`, where `n` is the number already in stock plus half the quantity being sold (rounded down), capped so the factor cannot go below zero (`Libraries/CoffeeShops.java:932` to `:950` and `:1064`). Default `DEVALUERATE=20 0.2` gives 20 percent per unit for finished goods and 0.2 percent for raw materials (`coffeemud.ini:1839`). The guide describes the same rule (`ArchonGuide.html:2149`).
- Prejudice factors: `SELL=` and `BUY=` adjustments, each with optional race, alignment, sex, class, tattoo, faction or category tags, separated by semicolons. The first matching part wins (`CoffeeShops.java:601` to `:676`). A factor of 0.0 is treated as "not set", so it cannot be used to block trade; the guide suggests 0.000000001 instead (`ArchonGuide.html:2207`).
- Currency: defined as `NAME=value unit(s) (short);value unit(s) (short)`. The parser (`BeanCounter.java:150` to `:200`) sorts denominations by value and treats `notrade` as a flag. Example from the guide: `US=1.0 penny(s) (p);10.0 dime(s) (d);25.0 quarter(s) (q);100.0 dollar(s) ($)` (`ArchonGuide.html:2059`).

**Deities and ritual grammar.** A deity's clerical rituals are written in a small grammar (`ArchonGuide.html:5560` to `:5760`). Each task is a keyword, and tasks combine with a pipe for "either" and an ampersand for "then in order". The 26 task words include SAY, TIME, PUTTHING, BURNTHING, EAT, READ, DRINK, INROOM, RIDING, CAST, EMOTE, SOCIAL, PUTVALUE, PUTMATERIAL, BURNMATERIAL, RANDOM, CHECK, INCLUDE, TIMEOUT, SITTING, STANDING, SLEEPING, WAIT, YOUSAY, ALLSAY and OTHERSAY. The guide's rule that the first WAIT must not be the first step is a design note, not a parser rule (I did not trace the ritual parser).

**Shops and other services.** The guide documents auctioneers (flat fee, percent of base price per day, cut of final price), bankers (interest on coin and items, loans), postmen (boxes, postage per pound, holding fees), librarians (checkout, overdue charges, shared library chain), and craft brokers (request and fulfil listings). All of these assume other players. They are discussed under "Takeaways".

### 6. Weapon and armour balance (Appendix C)

These are the most reusable formulas in the pair of guides. I checked them against the sample table starting at `ArchonGuide.html:6982` (the rows were read from the HTML, so the numbers are exact).

**Level from stats** (`ArchonGuide.html:6908` to `:6909`):

    Level = ((2*D)/(H+1) + (A-W)/5 + R) * ((R/W) + 2) + 1

where D is damage before material modifiers, H is hands, A is attack bonus, R is reach, W is weight. Round down, minimum 1.

I reproduced all seven sample rows (Hand Ax, Dagger, Short Sword, Voulge, Gladius, Grand Sceptre, Executioner's Axe). The computed values are 1.80, 0.60, 1.00, 9.36, 7.00, 12.60 and 13.67, which floor to the table levels 1, 1, 1, 9, 7, 12 and 13.

**Cost from stats** (`ArchonGuide.html:7445` to `:7446`):

    Cost = 2*(W*V + (5*D + A + R*10)*D) / ((H+1) * (1+T))

V is material value (iron 20, wood 2 in the guide's material table), T is 1 for thrown. The formula reproduces all seven sample costs. Five match exactly, for example Gladius 300 (D=4, A=5, W=10, iron) and Grand Sceptre 680 (D=8, A=5, W=16, iron). Voulge gives 803.33 and the Executioner's Axe 1233.33, which the table rounds down to 803 and 1233.

**Stats from level** (`:7422` to `:7425`) is the inverse. The guide's version of the damage inverse has a factor error. It writes `((H*2)+1)/2`, but inverting the level formula gives `(H+1)/2`. For the Gladius (level 7, D=4, A=5, W=10, one hand) the guide's inverse returns 6.0, and the algebraic inverse returns 4.0, which is the true damage. The error grows with the number of hands: for H=2 the guide multiplies by 2.5 where the algebra gives 1.5. A designer who uses the inverse to set damage from level gets damage that is too high.

**Armour.** Points by armour type (`:7728` onward): cloth, leather and metal each have a level for each base point value, from simple clothes (0 points) to dwarven plate (12 points), with a note that points above 13 add 10 levels per point. Armour value is points times the per-location factor (head 1, torso 3, legs 2, arms 1, hands 0.5 and so on). Location weights depend on material (for example torso 6, 18 and 30 pounds for cloth, leather and metal). Cost is:

    COST = (A*A + V) * (W/2)

The guide's example (a seven-point iron banded shirt) gives 2173.5, which I checked (`ArchonGuide.html:8235`).

**Materials.** Each material has a value, an attack, damage and armour modifier, and a condition note. A few rows from Appendix C (`:8555` onward):

| Material | Type | Value | Attack | Damage | Armour |
|---|---|---|---|---|---|
| Iron | Metal | 20 | (none) | (none) | (none) |
| Steel | Metal | 150 | +20 | +4 | +2 |
| Mithril | Mithril | 200 | 30 | 6 | 3 |
| Adamantite | Mithril | 500 | 40 | 8 | 4 |
| Ironwood | Wood | 25 | +10 | +2 | +1 |
| Balsa | Wood | 1 | -10 | -2 | -1 |
| Obsidian | Rock | 10 | +10 | +2 | +1 |
| Bone | Glass | 1 | +20 | +4 | +2 |

Standard modifiers for a weapon of a given base material also apply (for example the "Other" material on metal is -30 attack, `:8555` onward). The guide says material modifiers must not be applied to attack, damage or armour when the material is non-standard, and it says they do not change the level.

**Magic** (`:8256` onward). Each magic adjustment point adds 1 armour (wearables) or +2 damage and +10 attack (weapons), and adds 5 levels. Spell and property effects add levels by a typical-level rule: `PropHaveSpellCast` adds the spell's level, `PropWearSpellCast` adds half, `PropHaveAdjustor` is at least twice `PropWearAdjuster`. Resistances cost 2 to 30 levels per point. Final cost is base cost plus total level increase times a cost index between 10 (magic-rich world) and 100 (magic-poor world). These are guide rules; I did not check the item code that applies them.

### 7. The scripting engine: triggers, dispatch and the veto flow

**Script structure.** A script is a list of blocks. Each block is an event trigger line, then commands, then a line containing `~` (`Scriptable.html:647` to `:651`). An optional XML block at the end can define mobs and items for `MPMLOAD` and `MPOLOAD`. Scripts are attached through the `Scriptable` behaviour and cached as resources; `LOAD=` scripts stay in memory until the resource is unloaded (`Scriptable.html:593` to `:625`).

**Trigger table.** The 60 triggers are in the `progs` array (`ScriptingEngine.java:430` to `:495`). The `case` numbers in `DefaultScriptingEngine.java` match the array index. I grouped them by what they react to and noted where each one is dispatched.

| Family | Triggers (array index) | Dispatched in |
|---|---|---|
| Presence and movement (10) | greet 1, all_greet 2, group_greet 52, entry 8, exit 9, arrive 45, login 29, logoff 30, level 32, age 61 | `executeMsg` |
| Speech and messages (11) | speech 3, speak 47, act 18, mask 12, imask 43, regmask 31, channel 33, social 38, execmsg 41, look 39, llook 40 | `executeMsg` |
| Object handling (21) | give 4, giving 48, get 20, getting 54, put 21, putting 46, drop 22, dropping 53, wear 23, wearing 57, remove 24, consume 25, buy 27, sell 28, bribe 19, open 34, close 35, lock 36, unlock 37, ride 55, riding 56 | `executeMsg` |
| Combat and death (5) | damage 26, kill 44, death 10, fight 7, hitprcnt 11 | `executeMsg` for damage, kill, death; `tick` for fight and hitprcnt |
| Magic (2) | cast 49, casting 50 | `executeMsg` |
| Following (2) | follow 59, unfollow 60 | `executeMsg` |
| Timed (6) | rand 5, once 6, quest_time 13, time 14, day 15, delay 16 | `tick` |
| Veto phase (2) | cmdfail 51, cnclmsg 42 | `okMessage` |
| Named function (1) | function 17 | looked up by name by `findFunc` (`DefaultScriptingEngine.java:9146`) when a script calls it |

Index 58 in the array is a pseudo-entry used by the XML section and is not a trigger.

Every trigger takes a qualifier: a number (percent chance), `ALL`, `P` (exact name or ID), `Z` (zapper mask), or a keyword list. `standardTriggerCheck` implements the keyword and mask matching (`DefaultScriptingEngine.java:14156` to `:14230`), and `getTriggerPercent` turns the number into a chance (`:14233` to `:14247`). `ALL` becomes 101 and a zapper mask that matches becomes 101 (always), otherwise 0.

**Two-phase message flow.** This is the most reusable mechanism in the engine.

1. `okMessage(host, msg)` (`DefaultScriptingEngine.java:13878`) runs first. It handles only the veto triggers, `cmdfail` (51, line 13907) and `cnclmsg` (42, line 13969). If the script's `RETURN CANCEL` gives the string `CANCEL`, `okMessage` returns false and the action does not happen (`:14144` to `:14145`). The guide describes this as "to cause the triggered event to be cancelled, add a RETURN CANCEL command" (`Scriptable.html:1164`).
2. `executeMsg(host, msg)` (`:14265`) runs after the action. Its `switch` on the trigger code holds the reactive triggers (greet at `:14309`, arrive at `:14331`, give at `:14538`, and so on).

Both phases refuse to recurse more than two levels deep (`recurseCounter.get()>2`, lines 13880 and 14267).

**Queueing.** A reactive trigger that fires does not run its script at once unless `NODELAY` is set. It calls `enqueResponse` (`:16081`), which builds a `ScriptableResponse` with a tick delay (`:208` to `:246`, decrement on each `checkTimeToExecute`). Greet uses a delay of 1 (`:14322` to `:14327`). `dupCheckClear` (`:16023` to `:16080`) removes an identical queued response once the queue holds more than five entries, and caps the queue at 25 entries (150 for areas); extra entries are dropped from the end and logged as an overflow. `dequeResponses` (`:16123`) runs all responses whose delay has reached zero, and at the end of each tick it is called with spawn allowed (`:16007`). A script with the `SPAWN` flag runs on its own thread through `CMLib.threads().executeRunnable`.

`multiTriggers` (default false, `:91`) decides whether one event can fire several triggers on the same object. With it false, the reactive loop returns after the first match (`:14325` and similar).

**Timed triggers.** These run in `tick(ticking, tickID)` (`:15729`):

- `rand` (5, `:15795` to `:15809`): fires when `rollPercentage() < N`, after a guard described under "Problems and cruft".
- `delay` (16, `:15810` to `:15838`): picks `targetTick = roll(1, high-low+1, low-1)` (`:15829`), so the wait is between `low` and `high` ticks, and fires once.
- `fight` (7, `:15844`): on each tick while the object is in combat, makes a percent roll (`:15852`) and fires on success.
- `hitprcnt` (11, `:15876` to `:15910`): floor is `round(percent/100 * maxHP)` (`:15883` to `:15884`); fires when current HP is at or below the floor.
- `once` (6, `:15910`): fires once per script, tracked in the in-memory list `oncesDone` (field at line 97; re-created at 1030).
- `time` (14, `:15920` to `:15949`): fires once per in-game hour when the hour matches, tracked with `lastTimeProgsDone` so it does not repeat within the same hour.
- `day` (15, `:15950`): same logic per day of the month, with `lastDayProgsDone`.
- `quest_time` (13, `:15979`): tied to a running quest.

**Pausing a trigger.** `canTrigger(code)` (`:15717` to `:15727`) checks a map of "not before" times, which `mpnotrigger` sets (`DefaultScriptingEngine.java:12125`, command case 55). It also clears queued responses for that trigger, so a script can pause a trigger for a set time in milliseconds.

### 8. Scripting language: variables, codes, conditions and commands

**Dollar codes** (`Scriptable.html:2880` to `:3340`). About 50 codes substitute names and pronouns. They are expanded in `DefaultScriptingEngine.varify` (`:2192`). The important ones:

| Code | Meaning |
|---|---|
| `$i` / `$I` | the scripted monster (name / display) |
| `$n` / `$N` | source of the trigger (name) |
| `$t` / `$T` | target of the trigger |
| `$o` / `$p` | item 1 and item 2 |
| `$a` | name of the area |
| `$c` / `$r` | random PC or NPC in the room (name) |
| `$j`, `$e`, `$k`, `$h`, `$m`, `$s` | pronouns (he or she, his or her, him or her) for monster, source, target |
| `$d` / `$D` | title and description of the room |
| `$x` / `$X` | random exit by direction code or by name |
| `$0` to `$9` | temporary variables (used by FOR, MPARGSET, MPCALLFUNC) |
| `$<code var>` | a named variable, read with a code (`Scriptable.html:3289`) |
| `$%name(args)%` | inline function call |
| `$$` | literal dollar sign |

The temporary variable table is `ctx.tmp`, sized `SPECIAL_NUM_OBJECTS = 12` (`ScriptingEngine.java:421`). Index 9 is reserved for the shop-price result of `shophas` (`SPECIAL_9SHOPHASPRICE = 9`, line 427). That collides with `$9` as a user temporary. See "Problems and cruft".

**Variables.** Named variables are stored in the resources map under `SCRIPTVAR-<scope>` (`DefaultScriptingEngine.setVar`, `:2943`). Scope is the object or a quest name, so a quest's variables can be shared between its mobs.

**Conditions.** `if`, `else`, `endif` (`:9319`), `switch`, `case`, `default`, `endswitch` (`:9510`), `for`, `next` (`:9695`), and `mpwhile` (`:13396`). Comparisons come from the `Signs` enum (`:131` to `:168`): `==`, `>=`, `>`, `<`, `<=`, `!=`, and `.in.` for word containment. Logic joins with `AND`, `OR`, `ANDNOT`, `ORNOT`, `NOT`, and a 5 by 5 table resolves adjacent connectors (`Connector.Map`, `:170` to `:200`). Parentheses group sub-expressions (`Scriptable.html:4576` to `:4596`).

**Functions.** 119 names in `funcs` (`ScriptingEngine.java:496`). The families are: `is*` (38, for example `isnpc`, `ispc`, `isgood`, `isday`, `ismoon`, `isweather`), `num*` (11, for example `nummobsroom`, `numpcsarea`), `has*` (7), `quest*` (7), `clan*` (4), and about 52 others (`rand`, `hitprcnt`, `inroom`, `inlocale`, `level`, `exp`, `goldamt`, `shopitem`, `value`, `stat`, `gstat`, `var`, `callfunc`, `cansee`, `canhear`, `datetime`, `eval`, `math`, and more). `rand(N)` is true when `rollPercentage() < N` (`:3729` to `:3735`).

**Commands.** 101 entries in `methods` (`:620`). 87 are `MP*` commands (the rest are control words: `IF`, `BREAK`, `RETURN`, `FOR`, `SWITCH`, `ENDIF`, `ENDSWITCH`, `NEXT`, `CASE`, `DEFAULT`, `<SCRIPT>`, and aliases `MEA` for `mpechoat` (`:11518`) and `MER` for `mpechoaround` (`:11598`)). The families are:

- messages: `mpecho`, `mpechoat`, `mpechoaround`, `mpasound`, `mpchannel`, `mpspeak`, `mplog`
- movement: `mpgoto`, `mpat`, `mptransfer`, `mpwalkto`, `mptrackto`, `mplink`, `mpunlink`, `mpbeacon`
- combat and character: `mpdamage`, `mpheal`, `mpcast`, `mpcastext`, `mpaffect`, `mpunaffect`, `mpkill`, `mphit`, `mpslay`, `mpforce`, `mpexp`, `mpprompt`
- objects and mobs: `mpoload`, `mpmload`, `mpoloadroom`, `mpput`, `mpjunk`, `mppurge`, `mpset`, `mpgset`, `mpsetvar`, `mpmoney`, `mpopen`, `mpclose`, `mplock`, `mpunlock`
- quests and factions: `mpstartquest`, `mpendquest`, `mpqset`, `mpquestwin`, `mpquestpoints`, `mpfaction`, `mptrains`, `mppracs`
- flow: `mpalarm`, `mpchoose`, `mpconfirm`, `mpnotrigger`, `mpsetinternal`, `mpscript`, `mpunloadscript`, `mpcallfunc`, `return`

`mpsetinternal` controls engine flags: `SCOPE`, `NODELAY`, `ACTIVETRIGGERS`, `MULTITRIGGERS` (default false), `DEFAULTQUEST`, `SAVABLE`, `PASSIVE` (`Scriptable.html:6425` to `:6470`).

**Embedded JavaScript.** A `<SCRIPT>` block switches to ECMA JavaScript (Rhino, `DefaultScriptingEngine.java:36` import; `case 57`, `:9241`). The guide says the MOB program commands remain callable from JavaScript with parenthesised, comma-separated arguments (`Scriptable.html:4704` to `:4776`). The JavaScript event wrapper is `JScriptEvent` (`:16181` onward).

### 9. Timing and messages players see

- Tick length defaults to 4000 ms: `TIME_TICK = 4000` (`core/CMProps.java:681`) and `TICKTIME=4000` in `coffeemud.ini:2151`. The Scriptable guide calls each tick a 4 second interval throughout (for example `Scriptable.html:4879`). The Archon guide says "roughly 3 seconds" once (`ArchonGuide.html:4545`).
- Trigger delays are counted in ticks, so the real time of a delayed message depends on `TICKTIME`.
- Player-visible messages from scripts are whatever the `mpecho` family writes to the room. `TickTock` replies "..tick..tock..", `SysMsgs` replies "Extended messages are now : ON/OFF".

### 10. Percent semantics (a trap for script authors)

`Dice.rollPercentage()` returns 1 to 100 inclusive (`Libraries/Dice.java:409` to `:412`). Triggers test `rollPercentage() < N` (`DefaultScriptingEngine.java:14322`, `:15805` and others). So a written value N fires with probability (N-1)/100 for N between 1 and 100. `all_greet_prog 100` therefore fires 99 percent of the time, while the guide says it fires "100% of the time" and "every time" (`Scriptable.html:705`, `:710`, `:839`). The guide's wording "equal to or below the percentage" (`:685`) is also off by one. `ALL` (101) is the only way to be certain.

## Content worth mining

**Trigger catalogue.** The 60 triggers in the table above. Each one maps to a concrete game moment (a player enters, a player gives an item, a mob is hit, the hour changes). Together they are a checklist of world events a text game can react to. The guide's usage lines (`Scriptable.html` trigger sections) give argument forms; the most common are a percent or a keyword list plus an optional `p`, `z` or `all` qualifier.

**Dollar codes.** The substitution table above. It is a compact way to write messages that refer to whoever triggered them.

**Balance formulas.** The weapon level, cost and damage inverse, the armour point and location tables, the cost formula, and the material table (Appendix C). These are concrete and checked (see section 6). A Python SUD can use them as a generator: pick a level, derive damage and cost, then apply a material. The sample table is also a good test set.

**Locale movement costs.** A short dictionary of terrain types to integer costs, checked against the code. Ready to port.

**Shop and currency rules.** The devaluation formula, price codes (`0`, `-1`, quest points, XP), and the currency string format. A single-player economy can keep the stock-depletion rule and the currency line and drop the rest.

**Ritual grammar.** The 26 deity ritual task words and the `|` and `&` combinators (`ArchonGuide.html:5560` to `:5760`). A text game could use the same grammar for puzzles, rituals, and multi-step quests.

**Description variation.** `<VARIES>` with time, season, weather and mask tags, and `<P>` for random alternatives (`ArchonGuide.html:1372` to `:1425`). This is a good template for room text in a text game.

**Item-property vocabulary.** The 100 `Prop_` names are a catalogue of item and room effects (traps, adjusters, zappers, transporters, spell casters). Only a subset makes sense for a single player.

**Disease effects.** 36 disease effects are named (`ArchonGuide.html:5953` to `:5960`), several of them sexually transmitted infections, plus poisons and traps. They are a list of hazards and need only be described, not copied.

**Quest-board filters.** The `GenQuestBoard` filter keys (`SAREAMASK`, `TAREAMASK`, `AAREAMASK`, `GIVERMASK`, `TYPFILTER`, `CATFILTER`) are implemented in `Items/Basic/StdQuestBoard.java:70`. They show how a board can select quests by mask, which is useful for a single-player quest board.

## Notable design ideas

- **Veto before reaction.** An event passes through a "may I?" phase where any listener can cancel it, and only then does the "what happened" phase run (`okMessage` then `executeMsg`, `DefaultScriptingEngine.java:13878` and `:14265`). Scripts express cancellation as a return value (`RETURN CANCEL`). This is a clean way to implement doors, traps, and "you cannot leave while held".
- **Uniform attachment.** The same script engine runs on mobs, items, rooms, exits and areas (`Behaviors/Scriptable.java:44` to `:47`). A room can react to a player entering, and an item can react to being picked up, with one language.
- **Zapper masks as a tiny language.** `-RACE +Elf`, `-LEVEL +>10`, `-MATERIAL +WOODEN` (`MUDZapper.java:5678`) describe who or what qualifies, and the same syntax serves shops, room entry, and quest filters.
- **Gen objects as data.** Every generic object is a flat list of named stat codes stored as a string (`GenericBuilder.java:56`, `:105`). The editor, the saver, and the script `mpgset` command all use the same codes, so one schema drives all three.
- **Reset by chance.** Mob equipment and inventory can be marked as "appear with percent chance" on each reset, and "-1" means at boot only (`ArchonGuide.html:6022` to `:6070`). This gives variety without extra content.
- **Thin and instance areas.** Rooms load when needed and unload after idle time, and instances give each party its own copy (`ArchonGuide.html:1749` to `:1800`). These are memory-saving ideas, though a single-player game may not need them.
- **Balance by formula.** A weapon or armour design is a stat budget with a level, a cost and a material multiplier (section 6). A designer can pick one number and get the rest.

## Problems and cruft

1. **Off-by-one percent.** `rollPercentage() < N` with a 1 to 100 roll (`Dice.java:409` to `:412`, `DefaultScriptingEngine.java:14322`) fires N-1 percent of the time. The guide says N percent (`Scriptable.html:685`, `:705`).
2. **`rand_prog` guard looks wrong.** `if((t[1].length()>1) || (area is ACTIVE))` (`DefaultScriptingEngine.java:15800` to `:15803`) means a one-digit chance (1 to 9) fires only in active areas, while two or three digits fire everywhere. It reads as a mistake; I did not confirm intent.
3. **Shared temporary slot.** `$9` (a user temporary) and the shop price set by `shophas` (`SPECIAL_9SHOPHASPRICE = 9`, `ScriptingEngine.java:427`, written at `DefaultScriptingEngine.java:7059` to `:7064`) use the same index in `ctx.tmp`. A script that uses `$9` may be overwritten.
4. **Hit-point trigger does not latch.** `hitprcnt` (`:15876` to `:15910`) has no once-guard, so it fires on every tick while HP is at or below the floor, unless the script stops it (for example with `mpnotrigger`). The guide describes it as a check every round (`Scriptable.html:1885`).
5. **Abbreviation lookup.** When an `MP` command name is not found exactly, `execute` scans the names with `startsWith` and keeps the last match, with no `break` (`DefaultScriptingEngine.java:9225` to `:9231`). Abbreviations therefore resolve to whichever matching command appears last in the array.
6. **Copy-paste bug in stat codes.** `GenMOBCode.getAllCodeNames()` builds from `GenItemCode.values()` (`GenericBuilder.java:134` to `:143`). `MUDPercolator.java:73` uses it for the mob ignore list.
7. **Budget default documented wrong.** The guide says the default budget period is "100 ticks" (`ArchonGuide.html:2100`); the shipped default is `500000 MONTH` (`coffeemud.ini:1825`).
8. **Tick length documented inconsistently.** Mostly 4 seconds (`ArchonGuide.html:2325`, `Scriptable.html:4879`), but "roughly 3 seconds" at `ArchonGuide.html:4545`. The code default is 4000 ms (`CMProps.java:681`).
9. **Weapon inverse formula error.** `ArchonGuide.html:7425` uses `((H*2)+1)/2` where the algebra gives `(H+1)/2`. Verified with the Gladius row (guide inverse gives 6, true damage 4).
10. **Undocumented script items.** The `cmdfail_prog` trigger (index 51) has no guide section. The commands `mpstepquest`, `mprload`, and the functions `objtype` and `hastag` are in code but not in the guide. The guide's aliases are not documented either: `MEA` and `MER` (`DefaultScriptingEngine.java:11518`, `:11598`).
11. **Duplicate anchors in the guide.** Several section anchors are reused: `canhear` for `cansee`, `roompc` for `areapc`, `mpoloadroom` for `mpoloadshop`, `mpunhide` for `mpunloadscript`. A link to one of these names lands on the first section that uses it.
12. **Giant switch statements.** `DefaultScriptingEngine.java` is 16,435 lines. Triggers and commands are dispatched through integer `case` labels whose numbers must match the name arrays in `ScriptingEngine.java`. Adding a trigger means editing both files and keeping the numbers aligned by hand.
13. **Two languages.** The embedded JavaScript path (`<SCRIPT>`, Rhino) duplicates the command set with a different calling style (`Scriptable.html:4704`).
14. **Scripts cached in memory.** `LOAD=` scripts stay in the resource cache until `UNLOAD` is issued (`Scriptable.html:620` to `:623`), so editing a file has no effect until then.
15. **Multiplayer assumptions everywhere.** Login and logoff triggers, channel and social triggers, `Prop_NoPKill`, `Prop_ReqPKill`, shared journals and boards, auction houses, craft brokers, postmen, libraries and clan items all assume other human players. They are described at length and have little use for one player.

## Takeaways for a Python SUD

- **Event bus with a veto phase.** Implement actions as events. Before-hooks can cancel (return `CANCEL` or a `False` flag) and after-hooks react. Model it as two lists per event type:

  ```python
  def move_player(world, player, exit_):
      for hook in world.before["move"]:
          if hook(world, player, exit_) == "CANCEL":
              return False
      player.room = exit_.target
      for hook in world.after["move"]:
          hook(world, player, exit_)
      return True
  ```

- **Triggers as data.** Keep the 60-trigger idea but reduce it to what one player needs (about 20 to 25: enter, leave, give, get, drop, wear, use, kill, damage, time of day, day, delay, once, random). Store scripts as lists of `(command, args)` tuples, not as text. Avoid `eval`; provide a whitelisted function table for conditions, as the engine does with `funcs`.
- **Timed queue.** Copy the tick queue with a per-entry countdown and a `once` flag. This fits a turn-based design directly. For real time, count seconds instead of ticks. Compute delay with `random.randint(low, high)` rather than the engine's dice helper.
- **Percent done right.** Use `random.random() < p/100` (or `randint(1,100) <= p`) and document that `100` means always. Do not reproduce the off-by-one.
- **Zapper-style masks, simplified.** Replace the mask language with include and exclude lists or Python predicates. Keep the concept (one filter for shops, room entry, and quests), not the syntax.
- **Generic objects as dataclasses.** Use one `Item` dataclass with optional fields and a `kind` string, rather than 200 classes. Keep the Gen idea: a schema (dict of field names to types) that the editor, saver and scripts all read.
- **Items from formulas.** Implement the level, cost and damage formulas from section 6 as functions, with the inverse corrected to `(H+1)/2`. Test them against the seven sample rows. Use the material table as data (a dict).
- **Terrain costs as data.** A dict from terrain name to movement cost, from section 3.
- **Descriptions that vary.** Port the `<VARIES>` idea as a small template function: a description with tagged alternatives chosen by time, season and weather, plus a random `<P>`-style pick. Keep the tag names; drop the mask part.
- **Economy, lightly.** Keep per-unit price drop on sale (`price * (1 - 0.2*n)`, floor at zero), a per-shop stock limit, and the price code scheme `-1` (engine prices), `0+` (fixed). Drop auction, bank, postal and library chains.
- **Rituals and puzzles.** The deity ritual grammar (`SAY`, `WAIT`, `EAT`, with `|` and `&`) is a neat way to script a multi-step puzzle. Implement it as a list of step objects, each with a `matches(event)` method.
- **Respawn with chance.** Use `rejuv_ticks` for each spawn, and a per-item `appear_chance` on respawn. Keep `-1` meaning "boot only" and `0` meaning "never returns".
- **Drop these.** Shared journals and boards (unless as single-player notes), auction houses, craft brokers, postal boxes, clan and conquest systems, channels and social triggers, PvP flags, the Archon security model, and the embedded JavaScript path. One language is enough.
- **Single-player reframing.** Triggers that name "a player" can name "the player" and drop the source-target distinction for most events. Staff and area permissions are unnecessary, but a debug mode that lets the developer inspect triggers is worth keeping.

## Coverage

- **`guides/ArchonGuide.html`**: read in full as converted text (about 9,600 lines): the building chapters, the admin command list, the Gen property sheets, shop economics, races, catalogue, saving, and appendices A, B and D. Appendix C was read for the weapon, armour and magic formulas and for the sample tables; the long material table from `:8555` onward was skimmed for its shape and read for the sample rows only.
- **`guides/Scriptable.html`**: the events section read in full for `all_greet_prog` and `act_prog`, with the `cnclmsg_prog` note read in place; the usage and description lines of all 60 triggers extracted and read; the `$` code list read in full; the `%` function codes introduction read; the commands `mpgset`, `mpexp`, `mpsetinternal`, `rand`, `<SCRIPT>`, and the `if` and `for` sections read; the functions section read at the level of its introduction and a few examples. The remaining 90 or so command and function entries were covered by their headings and by the code arrays, not read individually.
- **Code verified**: `DefaultScriptingEngine.java` (fields, enums, queue, `okMessage`, `executeMsg` greet, `tick` timed triggers, `execute` dispatch, `varify`, `setVar`, `rand`, `if`, `for`, `return`, `mea`, `mer`); `ScriptingEngine.java` (name arrays, constants, `MPContext`); `Behaviors/Scriptable.java`; `Libraries/Dice.java`; `Libraries/CommonMsgs.java` (VARIES); `Libraries/CoffeeShops.java` (prejudice, devaluation, budget parse); `Libraries/BeanCounter.java` (currency parse); `core/CMSecurity.java` (group, area and staff checks); `Libraries/interfaces/GenericBuilder.java` (stat enums); `Items/Basic/GenItem.java`; `Locales` movement costs (for 16 classes); `coffeemud.ini` defaults; `CMProps.java` tick.
- **Numeric checks performed**: weapon level formula (7 rows), weapon cost formula (7 rows), armour cost example, locale movement costs (16 classes), the trigger count (60) and function and command counts (119 and 101), the percent off-by-one.
- **Skipped or unverified**: the admin command implementations beyond `SysMsgs` and `TickTock`; the random-area generator; the ritual parser; the exact weapon range sizes; the Gen item and mob attribute mapping in `CoffeeMaker`; the thin-area unloading timer (about 40 minutes per the guide); the help files under `resources/help/` that the guides cite.
- **Content rules**: no author names, emails or addresses are reproduced. The only URLs in the source are external references and are not copied. Sexual or minor-related content: none found in either guide. Disease and poison names are described in aggregate.
