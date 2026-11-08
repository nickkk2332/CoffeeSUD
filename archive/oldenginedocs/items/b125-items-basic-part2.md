# Items/Basic part 2: the Gen item subclasses (GenLantern to GenWormhole)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b125` (Items/Basic [part 2/5: GenLantern .. GenWormhole]) · 36 files · 8095 lines of Java.

## Summary

The 36 classes in this bucket are the "Gen" (generic, builder-editable) forms of everyday item types: lanterns, stoves, pipes, mirrors, packages and stacks, paper and books, recipe books, quest boards, portals, furniture, a sailing ship, space objects and prosthetic limbs. Each one is a thin layer over a `Std*` base class (`GenLightSource` extends `LightSource`, `GenRideable` extends `StdRideable`, `GenPaper` extends `StdPaper`, `GenNavigableBoardable` extends `StdNavigableBoardable`, and so on), and the layer makes the object data-driven. A shared stat-code interface lets builders create and edit these items in the running game, and one text slot (`readableText`) carries the single extra value each type needs. The mechanics worth studying are the lantern, where the oil tank and the burn timer are one quantity; the two packaging classes, a homogeneous stack versus a mixed package; the sailing ship, a boardable with its own interior area, a course queue and a hull percentage; and the quest board, which reads the live quest library instead of storing quests. Several defects sit in this bucket. The worst are the partial-unpack paths in the two packaging classes, which miscount weight or duplicate and drop contents.

## Key files and types

All files are under `com/planet_ink/coffee_mud/Items/Basic/`. Lines are the file's line count from the manifest.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Items/Basic/GenWallpaper.java` | Non-gettable readable wall art (paintings, graffiti). Implements `Item` directly with stubs for most of the interface | 955 |
| `com/planet_ink/coffee_mud/Items/Basic/GenRecipe.java` | Recipe book: a readable book whose lines are craft recipes for one skill | 550 |
| `com/planet_ink/coffee_mud/Items/Basic/GenSailingShip.java` | Sailing ship: a navigable and siegable boardable on water, with 25 hull points per room | 498 |
| `com/planet_ink/coffee_mud/Items/Basic/GenStove.java` | Container that is also a `Light` and a `FuelConsumer`; burns wood or coal items | 439 |
| `com/planet_ink/coffee_mud/Items/Basic/GenLantern.java` | Oil lantern or lamp: a `LightSource` whose liquid tank is tied to burn time | 309 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPackagedVariety.java` | Package of mixed items, stored as an XML list inside the text | 298 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPiano.java` | Musical instrument with an `InstrumentType`; a rideable that protects its player | 275 |
| `com/planet_ink/coffee_mud/Items/Basic/GenNavigableBoardable.java` | Stat-driven shell for a navigable boardable (a vehicle that owns an `Area`) | 273 |
| `com/planet_ink/coffee_mud/Items/Basic/GenSiegableBoardable.java` | Same shell for siegable boardables (castles, fortresses); near copy of the navigable one | 273 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPortal.java` | Portal: a `StdPortal` whose destination room IDs are in its text | 271 |
| `com/planet_ink/coffee_mud/Items/Basic/GenTub.java` | Bath tub: a rideable and a liquid holder | 261 |
| `com/planet_ink/coffee_mud/Items/Basic/GenRideable.java` | Stat-driven rideable (base for boats and furniture) | 260 |
| `com/planet_ink/coffee_mud/Items/Basic/GenLiquidResource.java` | Liquid raw material (water, oil, milk) that is also a drink; hooks the rot effect | 254 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPackagedItems.java` | Homogeneous stack: one template item and a count | 248 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPerfume.java` | Perfume bottle; wearing it adds a smell emote | 239 |
| `com/planet_ink/coffee_mud/Items/Basic/GenResource.java` | Solid raw-material pile; bundle size equals weight | 212 |
| `com/planet_ink/coffee_mud/Items/Basic/GenQuestBoard.java` | Quest board driven by Zapper masks over the quest library | 196 |
| `com/planet_ink/coffee_mud/Items/Basic/GenTitle.java` | Land-title deed bound to a property ID | 187 |
| `com/planet_ink/coffee_mud/Items/Basic/GenWormhole.java` | Space wormhole gateway | 187 |
| `com/planet_ink/coffee_mud/Items/Basic/GenSpaceBody.java` | Space object base (stars, planets, asteroids) with 3-D position and speed | 177 |
| `com/planet_ink/coffee_mud/Items/Basic/GenLimb.java` | Prosthetic limb; body part is derived from its name | 170 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPaper.java` | Sheet of paper with page and characters-per-page stats | 166 |
| `com/planet_ink/coffee_mud/Items/Basic/GenScale.java` | Two-pan balance; two things can rest on it and LOOK compares their weights | 155 |
| `com/planet_ink/coffee_mud/Items/Basic/GenLightSource.java` | Base light: burn duration kept as text | 143 |
| `com/planet_ink/coffee_mud/Items/Basic/GenMap.java` | Map of one or more areas; its name is built from the area list | 143 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPlayerBook.java` | Player-written book whose chapters live in the player database | 134 |
| `com/planet_ink/coffee_mud/Items/Basic/GenMobilePortal.java` | Mobile portal that binds to a stationary portal at its destination | 130 |
| `com/planet_ink/coffee_mud/Items/Basic/GenLawBook.java` | Law book bound to an area; READ shows and edits that area's laws | 125 |
| `com/planet_ink/coffee_mud/Items/Basic/GenMirror.java` | Mirror that reflects petrification spells and shows its last looker | 120 |
| `com/planet_ink/coffee_mud/Items/Basic/GenReadable.java` | Generic readable item; base for `GenRecipe` | 75 |
| `com/planet_ink/coffee_mud/Items/Basic/GenRuinedItem.java` | Ruined item produced by `CoffeeUtensils.ruinItem`; cannot be worn or wielded | 72 |
| `com/planet_ink/coffee_mud/Items/Basic/GenWater.java` | Puddle of fresh water: 10,000 units, not gettable | 62 |
| `com/planet_ink/coffee_mud/Items/Basic/GenTable.java` | Table: a rideable with four seats | 61 |
| `com/planet_ink/coffee_mud/Items/Basic/GenTent.java` | Tent: an enter-in rideable, cotton, four riders | 61 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPackagedStack.java` | Stack variant of `GenPackagedItems` that names itself "a stack of N" | 58 |
| `com/planet_ink/coffee_mud/Items/Basic/GenPipe.java` | Smoking pipe: a `GenCigar` variant with a 1200-tick duration and capacity 2 | 58 |

Base classes that carry most of the behaviour (in the same folder, mostly other buckets): `com/planet_ink/coffee_mud/Items/Basic/LightSource.java`, `com/planet_ink/coffee_mud/Items/Basic/StdRideable.java`, `com/planet_ink/coffee_mud/Items/Basic/StdContainer.java`, `com/planet_ink/coffee_mud/Items/Basic/StdNavigableBoardable.java`, `com/planet_ink/coffee_mud/Items/Basic/StdSiegableBoardable.java`, `com/planet_ink/coffee_mud/Items/Basic/StdPaper.java`, `com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java`, `com/planet_ink/coffee_mud/Items/Basic/StdPortal.java`, `com/planet_ink/coffee_mud/Items/Basic/StdLimb.java`, `com/planet_ink/coffee_mud/Items/Basic/StdTub.java`, `com/planet_ink/coffee_mud/Items/Basic/StdTitle.java`, `com/planet_ink/coffee_mud/Items/Basic/StdMap.java`, `com/planet_ink/coffee_mud/Items/Basic/StdPlayerBook.java`, `com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java`, `com/planet_ink/coffee_mud/Items/Basic/StdPerfume.java`.

## How it works

### 1. The Gen pattern shared by the bucket

Each class returns its own name from `ID()` (for example `"GenLantern"`, `com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:38-41`) and most override `isGeneric()` to return true, which marks it as builder-made rather than hand-coded.

**Stat table.** Each class exposes its fields through `getStat(code)`, `setStat(code, val)`, `getStatCodes()` and `sameAs(E)`. `getStatCodes()` concatenates the 25 generic codes of `GenericBuilder.GenItemCode` (`CLASS`, `USES`, `LEVEL`, `ABILITY`, `NAME`, `DISPLAY`, `DESCRIPTION`, `SECRET`, `PROPERWORN`, `WORNAND`, `BASEGOLD`, `ISREADABLE`, `ISGETTABLE`, `ISDROPPABLE`, `ISREMOVABLE`, `MATERIAL`, `AFFBEHAV`, `DISPOSITION`, `WEIGHT`, `ARMOR`, `DAMAGE`, `ATTACK`, `READABLETEXT`, `IMG`, `TAGS`; `com/planet_ink/coffee_mud/Libraries/interfaces/GenericBuilder.java:56-80`) with the class's own `MYCODES` array (`com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:226`, `com/planet_ink/coffee_mud/Items/Basic/GenPiano.java:199`). `getStat` first tries `CMLib.coffeeMaker().getGenItemCodeNum(code)`, then its own switch, then `CMProps.getStatCodeExtensionValue`, which reads a per-class extension list (`com/planet_ink/coffee_mud/core/CMProps.java:3618`). Because the table is just strings, the same accessors serve the in-game editors and `sameAs`.

**Serialisation.** `text()` returns `CMLib.coffeeMaker().getEnvironmentalMiscTextXML(this,false)` and `setMiscText()` unpacks it, then calls `recoverPhyStats()` (for example `com/planet_ink/coffee_mud/Items/Basic/GenLightSource.java:78-103`). The object's saved form is therefore the XML from `CoffeeMaker`, not a per-class format.

**One text slot, many meanings.** Most classes keep a `readableText` string and give it a second job. It is a duration in ticks (`com/planet_ink/coffee_mud/Items/Basic/GenLightSource.java:58-69, 93-94`; `com/planet_ink/coffee_mud/Items/Basic/GenStove.java:67-79`), a key name (`com/planet_ink/coffee_mud/Items/Basic/GenRideable.java:70-80`; `com/planet_ink/coffee_mud/Items/Basic/GenPortal.java:67-77`), a list of destination room IDs (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java:338-345`), an instrument type ordinal (`com/planet_ink/coffee_mud/Items/Basic/GenPiano.java:89-95`), a liquid type code (`com/planet_ink/coffee_mud/Items/Basic/GenPerfume.java`, `setLiquidType` writes `readableText`), an area name (`com/planet_ink/coffee_mud/Items/Basic/GenMap.java:87-96`) or a land-property ID (`com/planet_ink/coffee_mud/Items/Basic/GenTitle.java`). The clash in `GenPortal` is discussed under problems.

**Ticks and `recoverPhyStats`.** Anything that burns, rides or sails is driven by `tick(Tickable, tickID)`. The default tick is 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`, `TIME_TICK = 4000`), and light-tick IDs are fixed in `com/planet_ink/coffee_mud/core/interfaces/Tickable.java` (`TICKID_LIGHT_FLICKERS = 4` at line 90, `TICKID_AREA = 9` at line 100, `TICKID_SPECIALMANEUVER = 24` at line 130).

### 2. Light and fire

#### 2a. The burn timer: `LightSource`, `GenLightSource`, `GenLantern`

`LightSource` holds `durationTicks` (default 200) and `durationTickDown`, its live countdown (`com/planet_ink/coffee_mud/Items/Basic/LightSource.java:43-44`). Lighting an item starts a one-tick tick-down on the flicker tick. Each flicker tick decrements the countdown, and at zero it prints "@x1 flickers and burns out.", destroys the item if `destroyedWhenBurnedOut()`, recovers stats and turns the light off (`com/planet_ink/coffee_mud/Items/Basic/LightSource.java:172-200`, messages at 185 and 194).

Water and rain are handled in `executeMsg` (`com/planet_ink/coffee_mud/Items/Basic/LightSource.java:237-262`). If the holder is in water, or outdoors in rain or thunderstorm and the item `goesOutInTheRain()`, the code prints "The rain makes @x1 go out." (line 258), sets `durationTickDown=1` (line 259) and calls `tick()` directly (line 260). That call runs the burn-out branch, so a rained-on item burns out, and it is destroyed when `destroyedWhenBurnedOut` is true. `GenLightSource` sets that flag to true (`com/planet_ink/coffee_mud/Items/Basic/GenLightSource.java:52`), so a torch made from it is destroyed by rain, not just put out. Lighting in rain is refused at `com/planet_ink/coffee_mud/Items/Basic/LightSource.java` TYP_HOLD (around lines 128-146) with "It's too wet to light @x1 here."

`GenLightSource` is the base for every lightable Gen item. Its default duration is 200 ticks (`com/planet_ink/coffee_mud/Items/Basic/GenLightSource.java:54`). It keeps the duration in `readableText` and copies it into `durationTicks` on each read (lines 58-69, 93-94).

`GenLantern` is the oil lamp (`com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:35-66`):

- `DURATION_TICKS = 800` (line 43). At the default 4 s tick that is 3200 s, about 53 minutes.
- Tank of 80 units of `RESOURCE_LAMPOIL` (lines 45-47, 61-63); weight 5, base value 60, steel (lines 56-64).
- `destroyedWhenBurnedOut=false` and `goesOutInTheRain=false` (lines 58-59). A lantern survives burning out, and rain does not put it out. Water still does, because the water test in `LightSource.executeMsg` is not gated by `goesOutInTheRain`.

The oil and the time are the same quantity. `GenLantern.tick` runs on the flicker tick and recomputes the tank from the countdown: `rem = ceil(durationTickDown / getDuration() * liquidHeld())`, lowering `liquidRemaining` only when `rem` is smaller (`com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:75-90`, with the formula at 83-86). Refilling works in reverse. The code takes `min(lantern gap, drink's remaining)` units from the source drink and adds `round(duration/held * units)` ticks to `durationTickDown` (lines 142-152). A full tank sets the countdown to `getDuration()` (line 154). A full 80-unit tank therefore gives 10 ticks per unit.

Filling is gated in `okMessage` (`com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:100-120`): the source must be a `Drink` with the same liquid type name, the source must hold liquid, and the lantern must not be full. The refusal message for a wrong liquid is `L("You can only fill @x1 with @x1!",name(),liquidTypeName())` (line 106), which has a placeholder bug described under problems.

#### 2b. The stove: `GenStove`

`GenStove` extends `GenContainer` and implements `Light` and `FuelConsumer` (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:36-37`). Its fuel is the set of wood and coal items placed inside it, which the constructor sets to `RESOURCE_WOOD` and `RESOURCE_COAL` (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:64`). `isFuel` (lines 140-151) accepts an item whose material is in that set, and also any wooden item when wood is in the set.

- The stove itself does not burn out and is not put out by rain (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:62-63`).
- `light(true)` (lines 264-318) does three things. It starts the flicker tick (line 274). It applies a `Burning` effect to the stove with ability code `1024|2048|4096`, meaning not destroyed, no spread and not extinguished by rain (line 278), for `Integer.MAX_VALUE/2` ticks. It then applies a second `Burning` to the first fuel item with code 512, "item destroyed on burn end", for `getDuration()` ticks (lines 310-314).
- `Burning.unInvoke` destroys its host when code 512 is set (`com/planet_ink/coffee_mud/Abilities/Misc/Burning.java:116-123`). So each fuel item lasts one `getDuration()` burn and is then consumed.
- The flicker tick (lines 321-339) turns the stove off once `getFuel()` is empty. `consumeFuel` returns false and does nothing (lines 183-186).
- Duration is `readableText`, default `"20"` (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:44, 67-79`).

As far as this code shows, nothing applies `Burning` to the next fuel item when the first is destroyed. The stove stays lit with fuel left over until it is lit again, so one light action burns one fuel item. This is inferred from reading, not run.

Messages: "@x1 is not lit!" on extinguish (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:230`).

#### 2c. Pipes

`GenPipe` extends `GenCigar` (not in this bucket). It is an oak pipe with a 1200-tick duration (`setDuration(1200)`), `destroyedWhenBurnedOut=false` and capacity 2 (`com/planet_ink/coffee_mud/Items/Basic/GenPipe.java`, constructor). It is a smokeable container; the smoked contents are handled by the `GenCigar` and `GenContainer` bases, which belong to other buckets.

### 3. Liquids, drinks and perfume

- **`GenLiquidResource`** (`com/planet_ink/coffee_mud/Items/Basic/GenLiquidResource.java:35-254`) is a liquid raw material that is also a `Drink`. Defaults are fresh water, weight 1 (which is also the bundle size, `getBundleSize` at 146-149), capacity 0 and `autoBundle()` false (lines 133-137). Its `okMessage` and `executeMsg` defer to the shared `Prayer_Rot` ability (lines 87-117), so the resource rots through that effect unless the `FOODROT` security flag disables it. Many producers create it: `com/planet_ink/coffee_mud/Behaviors/PuddleMaker.java:131`, `com/planet_ink/coffee_mud/MOBS/Cow.java:113`, `com/planet_ink/coffee_mud/MOBS/GoatDoe.java:93`, `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Vampirism.java:226`, `com/planet_ink/coffee_mud/Libraries/RawCMaterial.java:716-722`.
- **`GenResource`** (`com/planet_ink/coffee_mud/Items/Basic/GenResource.java:37-212`) is the solid counterpart: iron by default, weight 1 as bundle size, `autoBundle()` delegates to `CMLib.materials().rebundle(this)` (line 94; `com/planet_ink/coffee_mud/Libraries/RawCMaterial.java:77`), and `domainSource` records the domain it came from.
- **`GenWater`** (`com/planet_ink/coffee_mud/Items/Basic/GenWater.java`) is a puddle: 10,000 units of fresh water, 250 quench per drink, capacity 0, `SENSE_ITEMNOTGET` so it cannot be picked up. `com/planet_ink/coffee_mud/Abilities/Spells/Spell_FakeSpring.java:139` creates it.
- **`GenTub`** extends `StdTub`, which is a `StdRideable` and a `Drink`. Its stats are the rideable stats plus `QUENCHED`, `LIQUIDHELD` and `LIQUIDTYPE` (`com/planet_ink/coffee_mud/Items/Basic/GenTub.java:85-88, 90-218`). `StdTub.containsLiquid` returns false in a drought for a non-gettable tub (`com/planet_ink/coffee_mud/Items/Basic/StdTub.java`, `containsLiquid`).
- **`GenPerfume`** extends `StdPerfume`. Its base defaults are 10 units of perfume, 1 quench per use, value 100 (`com/planet_ink/coffee_mud/Items/Basic/StdPerfume.java` constructor). Wearing it spends one unit and adds a `Prop_MOBEmoter` effect whose text is the perfume's smell list with `expires=50` if no expiry is given (`com/planet_ink/coffee_mud/Items/Basic/StdPerfume.java`, `wearIfAble`). Wearing is refused while the wearer already has a `Prop_MOBEmoter` effect ("You can't put any perfume on right now."). Drinking it is refused unless the liquid is fresh water ("You don't want to be drinking that.").

### 4. Packaging: stacks and mixed packages

Both classes implement `PackagedItems` (`com/planet_ink/coffee_mud/Items/interfaces/PackagedItems.java`). The interface contract is `packageMe(Item, n)`, `isPackagable(List)`, `unPackage(n)` (which destroys the package when the count reaches zero), `numberOfItemsInPackage()`, `peekFirstItem()`, `packageText()`/`setPackageText()` and a flag bitmap. The flag `PACKAGE_FLAG_TO_ITEMS_PROGRAMMATICALLY = 1` (`com/planet_ink/coffee_mud/Items/interfaces/PackagedItems.java:44`) stops a package from exploding into its items when touched.

**`GenPackagedItems`: a stack of identical items.**

- The count is kept in `basePhyStats().ability()` (`com/planet_ink/coffee_mud/Items/Basic/GenPackagedItems.java:87-98`), which reuses the ability field as a counter.
- `packageMe(I, n)` (lines 114-141) refuses if `disInvokeEffects(I)` fails. It sets the name from the item, the level and material from the item, weight to `item.weight * n` (line 128), value to `item.value * n` (line 131), and stores one `<PAKITEM>` entry with the class ID and the item's text.
- `name()` reads "a package of N things" (lines 73-79). `GenPackagedStack` overrides it to "a stack of N things" (`com/planet_ink/coffee_mud/Items/Basic/GenPackagedStack.java:46`).
- `isPackagable` requires every item to `sameAs` the first (lines 143-160).
- Callers: `com/planet_ink/coffee_mud/Commands/Package.java:123-146` (the player command), `com/planet_ink/coffee_mud/Commands/Destroy.java:1376-1377` (the scrap left when an item is destroyed, if its weight is above 1), `com/planet_ink/coffee_mud/Abilities/Common/Composting.java:217, 423`, and `com/planet_ink/coffee_mud/Abilities/Misc/AutoStack.java:190-191`.

**Partial unpack in `GenPackagedItems` miscounts weight.** `unPackage(n)` (lines 197-223) computes `itemWeight = weight / count` and `itemValue = value / count` before it decrements the count. After handing out `n` items it sets the remaining weight and value with `setWeight(itemWeight * number)` and `setBaseValue(itemValue * number)` (lines 219-220). `number` is the count taken out, not the count left. Removing 3 of 10 items leaves the stack weighing three items' worth. This path is reached through `RawCMaterial.unbundle(…, 1, …)` callers such as `com/planet_ink/coffee_mud/Abilities/Common/Herbology.java:153` and `com/planet_ink/coffee_mud/Abilities/Common/Mycology.java:174` when the found item is a stack. That reachability is inferred, not run.

**`GenPackagedVariety`: a mixed package.**

- The contents live as a compressed XML list of `<PAKITEM>` entries in `readableText` (`com/planet_ink/coffee_mud/Items/Basic/GenPackagedVariety.java:66-79`). Each entry has `PINUM` (count), `PICLASS` (class ID) and `PIDATA` (the item's saved text) (`packageMe`, lines 99-106).
- `packageMe` appends the entry, adds weight and value, and lengthens the description with "The contents of the package appears as follows:" and one line per item (lines 81-109). It calls `disInvokeEffects` but does not check its result (line 87), unlike the stack class.
- Touching it with GET or DROP unpacks everything into the holder or the room, then destroys the package (`executeMsg`, lines 111-129). It is the only caller of `unPackage` in this bucket.
- `com/planet_ink/coffee_mud/Abilities/Spells/Spell_Pocket.java:158-161` uses it: the target is deep-copied, each copy is packaged with count 1, and the original is destroyed. The "pocket" spell is therefore a packaging operation.

**Partial unpack in `GenPackagedVariety` duplicates and drops items.** `unPackage(n)` (lines 176-246) rebuilds a `newXml` string of the remaining entries (lines 187, 196, 211-215), but nothing ever writes it back. `setPackageText` is never called on this path, and the only state change is the cached count (line 238). A second partial unpack therefore reads the original entries from the start again. Example: a package of 2 apples and 1 pear, taken out one at a time. The first two calls each return an apple, leaving the count at 1. The third call returns another apple and destroys the package. The pear is never returned, and the third apple was not in the original package. The path is reached through `RawCMaterial.unbundle(I, n, C)` (`com/planet_ink/coffee_mud/Libraries/RawCMaterial.java:233-238`, `disBundle` at 241-270), which calls `unPackage(number)` at line 257.

### 5. Furniture and rideables

`GenRideable` extends `StdRideable` (which is a `StdContainer` implementing `Rideable`). It adds the generic rideable stats: lock, lid, capacity, contain types, reset time, ride basis, rider capacity, default closed and locked, and the phrase strings for put, mount, dismount, state and ride (`com/planet_ink/coffee_mud/Items/Basic/GenRideable.java:102-106, 151-217`). It defaults to a water-based boat of weight 2000 (`com/planet_ink/coffee_mud/Items/Basic/GenRideable.java:46-56`). The ride basis is an enum (`com/planet_ink/coffee_mud/core/interfaces/Rideable.java`: `LAND_BASED`, `WATER_BASED`, `AIR_FLYING`, `FURNITURE_SIT`, `FURNITURE_SLEEP`, `FURNITURE_TABLE`, `ENTER_IN`, `LADDER`, `WAGON`, `FURNITURE_HOOK`).

`StdRideable.recoverPhyStats` (`com/planet_ink/coffee_mud/Items/Basic/StdRideable.java`, near line 562) sets `IS_SWIMMING` for a water-based ride and `IS_FLYING` for an air-based one while the item is usable. When its wear runs out it sets `IS_FALLING` instead, so a boat that has run out of "uses" sinks.

- **`GenTable`** (`com/planet_ink/coffee_mud/Items/Basic/GenTable.java`): weight 250, oak, four riders, `FURNITURE_TABLE`.
- **`GenTent`** (`com/planet_ink/coffee_mud/Items/Basic/GenTent.java`): weight 5, cotton, four riders, `ENTER_IN`.
- **`GenTub`** (see section 3).
- **`GenPiano`** (`com/planet_ink/coffee_mud/Items/Basic/GenPiano.java:37-275`) extends `GenRideable` and implements `MusicalInstrument`. The default is `InstrumentType.PIANOS`, value 1015, weight 2000, `FURNITURE_SIT` with two riders (`com/planet_ink/coffee_mud/Items/Basic/GenPiano.java:45-60`). `InstrumentType` (`com/planet_ink/coffee_mud/Items/interfaces/MusicalInstrument.java:49`) lists `CLARINETS, CYMBALS, DRUMS, FLUTES, GUITARS, HARMONICAS, HARPS, HORNS, OBOES, ORGANS, PIANOS, TROMBONES, TRUMPETS, TUBAS, VIOLINS, WOODS, XYLOPHONES, OTHER_INSTRUMENT_TYPE`. `com/planet_ink/coffee_mud/Abilities/Songs/Play.java:467` and `626` check the instrument type against the song's required type, so a piano decides which songs it can play.
- While a seated musician is in a playing state, `GenPiano.okMessage` (lines 132-197) protects them. A malicious action from the player is refused with "Not while you are playing!" (line 152). A malicious action aimed at the player is redirected to a random member of the player's group (lines 155-183). The check uses `CMLib.flags().matchedAffects` on the player's effects. Line 139 repeats one condition twice (see problems).
- **`GenScale`** (`com/planet_ink/coffee_mud/Items/Basic/GenScale.java:35-155`) is a two-pan balance. `riderCapacity()` is dynamic: two seats minus the items already on the pans, floored at zero (lines 63-70). `okMessage` refuses a third thing ("Nothing more can fit on @x1.", line 72 onward), except that a raw material may be added to a pan that already holds the same material and subtype. LOOK and EXAMINE compare the two heaviest things and print "@x1 is heavier than @x2.", "@x1 weighs the same as @x2.", or the one-sided "@x1 weighs nothing at all." (`executeMsg`, from line 111).

### 6. Vessels: `GenSailingShip`, `GenNavigableBoardable`, `GenSiegableBoardable`

These three classes are the most elaborate in the bucket. The engine logic lives in `StdNavigableBoardable` (1983 lines) and `StdSiegableBoardable` (1531 lines), so this section covers only what the Gen layer adds and the numbers that matter.

**Data model.** `GenNavigableBoardable` (`com/planet_ink/coffee_mud/Items/Basic/GenNavigableBoardable.java:36-273`) has the boardable stats plus `AREA` (the ship's own interior, serialised with `CMLib.coffeeMaker().getAreaObjectXML` in `getStat`), `OWNERNAME`, `PRICE`, `EXITNAME` and the phrase strings. A ship is therefore a boardable whose interior is an `Area`. `StdNavigableBoardable.getAreaClassType` and `createFirstRoom` (lines 84-96) create that interior. `GenSiegableBoardable` is the same class with `OWNER` in place of `OWNERNAME` (diff against the navigable file).

`sameAs` for both compares every stat except `AREA` and then walks the two interiors room by room, comparing rooms, items and inhabitants in order (`com/planet_ink/coffee_mud/Items/Basic/GenNavigableBoardable.java:214-273`). Note that it computes `getStat("AREA")` (a full XML dump) for each comparison before skipping it (line 221).

**`GenSailingShip` (`com/planet_ink/coffee_mud/Items/Basic/GenSailingShip.java:47-498`).** Its constructor sets the name and verbs ("sail", "sailing", "ship", "anchor", "lowered"), material oak, and `basePhyStats().setAbility(2)` (lines 55-69). The ability value is the sail power, and `StdNavigableBoardable.getMaxSpeed` (`com/planet_ink/coffee_mud/Items/Basic/StdNavigableBoardable.java:159-193`) reads it:

- Base speed is `phyStats().ability()`, which is 2 by default.
- In windy weather the speed gains 1, and gains another 1 in a room whose climate is marked windy. In other rooms, a 50% roll can add 1 (lines 165-180).
- If the boat wears out, the speed is scaled by `usesRemaining()/100`. Below 10 uses the speed is 0 (lines 183-188).
- The minimum returned value is 1 unless the speed is 0 through wear (lines 190-192).

Movement happens each tick on `TICKID_AREA`, or on `TICKID_SPECIALMANEUVER` in tactical mode (`StdNavigableBoardable.tick`, lines 1258-1388). Each tick the ship increments `ticksSinceMove` and `ticksSinceLastTurn` and follows its course queue (`courseDirections`). It then takes up to `getMaxSpeed()` steps, and each step checks `ticksPerTurn` and `ticksPerMoves` (defaults 1 at `com/planet_ink/coffee_mud/Items/Basic/StdNavigableBoardable.java:58-59`, checks at 1321-1334). The effective pace is set by those counters as well as by speed. This is inferred from reading and not run. A ship starts anchored: `anchorDown` defaults to true (`com/planet_ink/coffee_mud/Items/Basic/StdNavigableBoardable.java:49`). `com/planet_ink/coffee_mud/Abilities/Druid/Druid_Krakenform.java:120-126` makes a `GenSailingShip` as a druid's sea form, with ability `ceil(level/15)`, anchor up and 100 uses.

**Command words.** `GenSailingShip.findNavCommand` maps `SAIL` to `NAVIGATE`. It also drops `RISE` and `DIVE` and so does not accept `NAVIGATE` as a word (lines 98-129). `findSiegeCommand` maps `SINK` to `IMPLODE` (lines 131-159).

**Hull and damage.** Hull is a percentage in `usesRemaining()` (100 when new). `GenSailingShip.getMaxHullPoints` is `25 * properRooms + armor` (lines 161-165). The base class uses `5 * properRooms + armor` (`com/planet_ink/coffee_mud/Items/Basic/StdSiegableBoardable.java:1281-1284`). A hit with `value` points loses `round(value / maxHull * 100)` percent (`com/planet_ink/coffee_mud/Items/Basic/StdSiegableBoardable.java:1450-1478`). A hit that removes at least the remaining percent sinks the vessel: `setUsesRemaining(0)`, `recoverPhyStats()`, `doCombatDefeat`. Otherwise the percent is reduced. The default message is "<O-NAME> fired from <S-NAME> hits and @x1 @x2.", with the hit word from `CMLib.combat().standardHitWord`. The deck and the underside each get their own message.

**Sinking (`GenSailingShip.doCombatDefeat`, lines 369-407).** It calls `CMLib.tracking().makeSink`, shows "<T-NAME> start(s) sinking!" to the room and outside viewers, sends `MSG_CAUSESINK` to everyone aboard, awards the `SHIPSSUNK` achievement and posts 500 experience to the crew (`postExperienceToAllAboard(... "SIEGE:"+ID(), 500, this)`).

**Water and sky (`GenSailingShip.executeMsg`, lines 409-475).** When the ship changes room while someone aboard is riding it, and either the old room was underwater or the new room is a sunken room, the code clears the sky over each outdoor room of the ship's area and gives it sky again (`giveASky(0)`). If the ship has sunk, each deck room with no room above gets an upward `NamedRedirectable` exit pointing at the ship's current room, where it sank (lines 455-464). `recoverPhyStats` makes the vessel `IS_SWIMMING` while it has uses, and `IS_FALLING` when it has none (lines 79-90). `amDead` is true when a sunk ship sits in an underwater room whose grid parent has no room ID (lines 485-497).

**Boarding and combat rules.** Boarding from the water is refused unless the boarder is climbing. Within two ticks of the ship's last move, any boarding attempt from the water is refused outright ("You'll need some assistance to board a ship from the water.", `okMessage` lines 315-345). Attacking a `SiegableItem` from a room that is not deep water is refused ("You are not able to engage in combat with @x1 here.", `startAttack`, lines 352-367). An "AWaterCurrent" effect is blocked while the ship is anchored (lines 307-313).

**Castles.** `GenSiegableBoardable` is the castle variant. `com/planet_ink/coffee_mud/Items/ClanItems/GenClanCastle.java:18` imports it, so clan castles build on the same siege model.

### 7. Portals

`StdPortal` (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java:36`, a `StdContainer` implementing `Rideable` and `Exit`) resolves its destination from `readableText`, which holds a semicolon-separated list of room IDs. `getDestinationRoom` picks one at random (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java:338-345`). Entering creates temporary `GATE` exits in both rooms, walks the traveller through the gate, and locks the pair with `CMClass.getSync("GATE_"+twinRoomIDs)` (`com/planet_ink/coffee_mud/Items/Basic/StdPortal.java`, the `TYP_ENTER` branch around lines 446-470).

`GenPortal` (`com/planet_ink/coffee_mud/Items/Basic/GenPortal.java:36-272`) is the builder-editable form, with lock, lid, capacity, contain types, open delay, door name, closed text and the phrase strings (`MYCODES` at lines 99-104). Its key name and destination list are the same field (see problems).

`GenMobilePortal` (`com/planet_ink/coffee_mud/Items/Basic/GenMobilePortal.java:36-130`) is a portal that moves. When a player enters it, `executeMsg` (lines 47-120) finds a stationary `StdPortal` in the destination room's floor items or its inhabitants' items. If there is one choice, that is used. If there are several, it prefers the one with the same `secretIdentity()`, then the same name, then the first (lines 80-108). It then points that stationary portal's `readableText` back at the mobile portal's current room, which gives a return path. If no destination exists, it prints "Something has gone wrong!" and logs "Destination not found" (lines 60-66).

### 8. Paper, books and information items

**`GenPaper`** (`com/planet_ink/coffee_mud/Items/Basic/GenPaper.java:36-166`) extends `StdPaper` (`com/planet_ink/coffee_mud/Items/Basic/StdPaper.java`). `StdPaper` is one page of up to 2048 characters (`maxCharsPage`, line 61). Writing is refused when the text would overflow it, with the message "There isn't enough space left on the paper to write all that. It looks like you can fit @x1 more characters." (`com/planet_ink/coffee_mud/Items/Basic/StdPaper.java:150-165`, message at 161). The message gives the page maximum, not the space left (see problems). `GenPaper` exposes `MAXPAGES` and `MAXCHARSPAGE` (`MYCODES` at line 88). `MAXPAGES` cannot change, because `StdPaper.setMaxPages` is empty and `getMaxPages` returns 1 (`com/planet_ink/coffee_mud/Items/Basic/StdPaper.java:96-97`, `com/planet_ink/coffee_mud/Items/Basic/GenPaper.java:115`).

**`GenReadable`** (`com/planet_ink/coffee_mud/Items/Basic/GenReadable.java:37-75`) is the base for readable Gen items. It sets `SENSE_ITEMREADABLE`, wood, weight 1, and forces the readable flag on in `recoverPhyStats`. `GenRecipe` extends it.

**`GenPlayerBook`** (`com/planet_ink/coffee_mud/Items/Basic/GenPlayerBook.java:35-134`) extends `StdPlayerBook`. Its chapters are not stored in the item at all. `StdPlayerBook.getChapterCount` and `readChaptersByCreateDate` read them from the player database under the category `"BOOK_"+KEY` (`com/planet_ink/coffee_mud/Items/Basic/StdPlayerBook.java`, `getChapterCount` and `readChaptersByCreateDate`). Chapters are sorted by the number after the last underscore in their key, and the book shows "By <author>" or "By Various Authors" in its table of contents. The book's own `executeMsg` has a name-based self-destruct: if the target or tool is the book and its name is "THE ARCHON BOOK", it calls `destroy()` (`com/planet_ink/coffee_mud/Items/Basic/GenPlayerBook.java:67-76`, check at line 70).

**`GenLawBook`** (`com/planet_ink/coffee_mud/Items/Basic/GenLawBook.java:35-125`) extends `StdLawBook` (`com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java`, 1603 lines, mostly outside this bucket). Its text is the area ID (`readableText`). READ (`com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java`, `executeMsg` around lines 88-150) looks up the area's legal behaviour and its law. Any READ by a reader who may modify the law (the `ABOVELAW` security flag, or a clan role with the `CREATE_LAW` authority) activates the law if it is not yet active (`changeTheLaw(... "ACTIVATED","TRUE")`). A numeric argument shows a page: 1 is a summary page, and 2 to 12 are topic pages named by their handler methods: officers and judges, victims of crime, jail policy, parole and release, basic law, trespassing, illegal influence, illegal skill, illegal emotation, tax law, and banned substances. Writing is refused with "You are not allowed to write on @x1. Try reading it." (`com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java:72-74`). The paging is the closest thing in this bucket to a rules engine, and it is worth reading in full if the law system is studied.

**`GenRecipe`** (`com/planet_ink/coffee_mud/Items/Basic/GenRecipe.java:36-550`) extends `GenReadable` and implements `RecipesBook`. Its state is:

- `commonSkillID`: the craft ability whose recipes these are (line 44).
- `recipeLines`: one tab-separated line per recipe, in the same format the crafting skill uses to read its recipe file (`com/planet_ink/coffee_mud/Items/Basic/GenRecipe.java:145, 300`; `com/planet_ink/coffee_mud/Abilities/Common/GenCraftSkill.java:86-114`).
- Its pages: `getTotalRecipePages()` is the item's `usesRemaining()`, so a book is a usage counter (lines 445-454).

`okMessage` checks the page count before a write, refuses lines whose field count does not match the skill's recipe lines, and refuses a book with no skill: "This recipe book is un-prepped." (lines 137-142). READ with a number shows that recipe, naming the item, its level and its component count. READ without a number lists the book's recipes with the skill name (`executeMsg`, lines 232-321). The item's name is made from the first recipe. A `%` in the name is replaced by the recipe's item name, or "of <item>" is appended (`recoverPhyStats`, lines 379-416).

**`GenMap`** (`com/planet_ink/coffee_mud/Items/Basic/GenMap.java:35-143`) extends `StdMap`. Its text is a semicolon-separated list of area names. `setReadableText` calls `doMapArea()`, which builds a name such as "a map of A, and B." (`com/planet_ink/coffee_mud/Items/Basic/StdMap.java`, `doMapArea`). `StdMap.finishMapMaking` renders the map as ASCII cells of 8 by 6 characters. Each cell shows the room's display text in four rows of six characters (24 characters in total) with compass walls drawn from the room's exits (`com/planet_ink/coffee_mud/Items/Basic/StdMap.java`, `finishMapMaking` and `dirChar`). `BardMap` extends `GenMap` (`com/planet_ink/coffee_mud/Items/Basic/BardMap.java:36`).

**`GenQuestBoard`** (`com/planet_ink/coffee_mud/Items/Basic/GenQuestBoard.java:37-196`) extends `StdQuestBoard`. It stores no quests. `StdQuestBoard.getQuestList` (`com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java`, around lines 122-210) re-reads `CMLib.quests().enumQuests()` when a hash of the quest list changes. It checks at most every 5 seconds (`nextCheck=now + 5000`, line 131). Quests pass if they are running and not stopping, match the optional type and category filters, have a giver who passes `GIVERMASK`, and have an area that passes `SAREAMASK` or `AAREAMASK` (see problems for an inconsistency in the second test). Reading the board with no number shows a numbered list of displayable quests the reader can accept. A number shows the quest, its giver, the giver's area and its instructions, then prints "Accept this quest (y/N)? " and calls `quest.acceptQuest(M)` on "Y" (`StdQuestBoard.executeMsg`, the TYP_READ branch, lines 221-315; prompt at line 312). The board's masks are the Zapper language (`CMLib.masking().maskCompile`).

### 9. Decor, ruin and limbs

**`GenWallpaper`** (`com/planet_ink/coffee_mud/Items/Basic/GenWallpaper.java:38-955`) is a non-gettable, non-wearable readable thing that implements `Item` directly with no-op stubs. It is painted by `com/planet_ink/coffee_mud/Abilities/Common/Painting.java:349` (keywords, description and a crafting brand as the secret identity) and written on by `com/planet_ink/coffee_mud/Abilities/Thief/Thief_Graffiti.java:116-128`, which names it "Graffiti", gives it the scribbled text as readable text, and picks one of six descriptions at random. `GenWallpaper.okMessage` (lines 606-649) lets the player look at, read or speak to it, and refuses everything else with "You can't do that to <T-NAMESELF>." Magic aimed at it is refused with "Please don't do that." The room reset in `com/planet_ink/coffee_mud/Commands/Reset.java:2290` skips it, so graffiti survives a reset.

**`GenMirror`** (`com/planet_ink/coffee_mud/Items/Basic/GenMirror.java:35-120`) extends `GenItem`. When worn properly, its `okMessage` reflects a petrification spell or prayer (`Spell_FleshStone` or `Prayer_FleshRock`, lines 104-105) back at the caster. It prints "@x1 reflects the vicious magic!" and invokes the spell on the source, with a re-entry guard (`oncePerRound`, line 43), so the reflected spell cannot reflect again. `description()` (lines 62-85) runs the look handler, through a temporary fake observer and fake session, on the last person who looked at the mirror within the last second, and returns "In @x1, you see: ...". Mirrors are placed in `resources/randareas/prison.xml:218` and `resources/randareas/town.xml:385`.

**`GenRuinedItem`** (`com/planet_ink/coffee_mud/Items/Basic/GenRuinedItem.java:35-72`) is the product of `CoffeeUtensils.ruinItem` (`com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:1373-1422`). That method copies name, display text, weight and wear location, adds "ruined" to the name, appends a material-specific sentence ("is torn and ruined beyond repair", "is battered and ruined beyond repair", "is shattered ...", "is cracked ...") and sets the value to 0. `GenRuinedItem.okMessage` refuses wear and wield with "<T-NAME> is ruined and can not be worn." and "... can not be wielded." `CoffeeUtensils.isRuinedLoot` treats a `GenRuinedItem` as already ruined (line 1436).

**`GenLimb`** (`com/planet_ink/coffee_mud/Items/Basic/GenLimb.java:35-170`) extends `StdLimb` (`com/planet_ink/coffee_mud/Items/Basic/StdLimb.java`). This is the prosthetic-limb model, and it is distinctive:

- The body part is not stored directly. `StdLimb.getBodyPartCode` scans the 16 names in `Race.BODYPARTSTR` (`com/planet_ink/coffee_mud/Races/interfaces/Race.java:555-559`: ANTENNA, EYE, EAR, HEAD, NECK, ARM, HAND, TORSO, LEG, FOOT, NOSE, GILL, MOUTH, WAIST, TAIL, WING) and uses the one the item's name ends with, or its secret identity if the name does not match (`StdLimb.getBodyPartCode`). A "glass eye" is therefore an EYE limb, and a "peg leg" is a LEG.
- The wear location comes from `Race.BODY_WEARVECTOR[partCode]` (`StdLimb.getWearLocations`).
- `StdLimb.canWear` counts the working parts the wearer has, plus other limbs of the same part, and refuses when that reaches the race's body mask ("You don't have any empty sockets to wear @x1 on.", `canWearComplete`).
- While worn, `affectCharStats` restores the missing body part with the limb's ability value. If the wearer already has enough parts, the limb is pushed off (`setRawWornCode(0)`).
- Limbs are created by amputation and beheading: `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Behead.java:200` and `com/planet_ink/coffee_mud/Abilities/Misc/Amputation.java:562`.

**`GenTitle`** (`com/planet_ink/coffee_mud/Items/Basic/GenTitle.java:35-187`) extends `StdTitle` and implements `LandTitle`. Its readable text holds the property ID of the land or ship it deeds (`landPropertyID` returns it, `setLandPropertyID` also calls `updateTitleName`). `StdTitle.updateTitleName` names the deed "the title to <area>" when the title covers one property, or "the title to rooms around <room>" when it covers several (`com/planet_ink/coffee_mud/Items/Basic/StdTitle.java`, `updateTitleName`). `PRICE` is read from and written to the property record, not stored on the deed (`StdTitle.getPrice` adds back-taxes for land). Deeds are created by `com/planet_ink/coffee_mud/Abilities/Common/GenWrightSkill.java:440`, `com/planet_ink/coffee_mud/Abilities/Common/Shipwright.java:349` and the Spell_Wish wish (`com/planet_ink/coffee_mud/Abilities/Spells/Spell_Wish.java:497`). `com/planet_ink/coffee_mud/Items/Basic/StdBoardable.java:1100` checks for the `GenTitle` ID by name.

**`GenWormhole`** (`com/planet_ink/coffee_mud/Items/Basic/GenWormhole.java:35-187`) extends `StdWormhole` (a `StdSpaceBody` implementing `SpaceGateway`). It exposes direction (`DIRECTION`, 3-D doubles), speed, radius, coordinates (reduced modulo the galaxy radius, lines 130-132), permitted number of ship directions and the permitted directions as a `ShipDir` list. `GenSpaceBody` (`com/planet_ink/coffee_mud/Items/Basic/GenSpaceBody.java:35-177`) is the same for ordinary space objects. Its subclasses (`com/planet_ink/coffee_mud/Items/Basic/Star.java`, `com/planet_ink/coffee_mud/Items/Basic/DwarfStar.java`, `com/planet_ink/coffee_mud/Items/Basic/MassiveStar.java`, `com/planet_ink/coffee_mud/Items/Basic/GasGiant.java`, `com/planet_ink/coffee_mud/Items/Basic/Moonlet.java`, `com/planet_ink/coffee_mud/Items/Basic/Asteroid.java`) are in the same folder and belong to other buckets.

**`GenWater`, `GenPipe`, `GenPerfume`** are covered under sections 2c and 3.

### 10. Stat editing and identity, across the bucket

Two cross-cutting features show up in nearly every class and are worth copying:

- Every `setStat` route begins with `CMLib.coffeeMaker().getGenItemCodeNum(code)`, so the generic fields (name, weight, material and so on) are handled in one place. The class then handles its own codes. A few classes repeat the extension-value call after this (see problems).
- `sameAs` compares stat strings, not object identity. This is what lets the game decide that two items are "the same" for stacking, packaging and comparison, and what lets a builder copy an item by its stat text.

## Content worth mining

The data that makes these classes real lives in `resources/skills/*.txt` as tab-separated craft recipes. Each row has the same layout (`com/planet_ink/coffee_mud/Abilities/Common/InstrumentMaking.java:82-83`, `com/planet_ink/coffee_mud/Abilities/Common/GenCraftSkill.java:86-114`): name (with `%` replaced by the material name), level, build time in ticks, materials needed, base value, class ID, misc field (ride capacity, wear location or flag), material, races and a subtype. The `capacity` field (column 8) becomes the item's light duration in `com/planet_ink/coffee_mud/Abilities/Common/GenCraftSkill.java:969`.

**Instruments (`resources/skills/instruments.txt`, 10 `GenPiano` rows)**

| Instrument | Level | Ticks | Materials | Value | Riders (misc) | Metal or wood | Subtype |
|---|---|---|---|---|---|---|---|
| Marimba | 11 | 80 | 40 | 560 | 1 | metal | XYLOPHONES |
| Carillon | 12 | 100 | 36 | 560 | 1 | metal | XYLOPHONES |
| Celesta | 15 | 120 | 18 | 420 | 1 | metal | XYLOPHONES |
| Dulcimer | 18 | 180 | 24 | 228 | 1 | wood | PIANOS |
| Glockenspiel | 18 | 200 | 60 | 1000 | 1 | metal | XYLOPHONES |
| Clavicord | 19 | 180 | 30 | 240 | 1 | wood | PIANOS |
| Harpsicord | 24 | 180 | 60 | 300 | 1 | wood | PIANOS |
| Hydraulis | 24 | 500 | 300 | 4000 | 4 | metal | ORGANS |
| Piano | 30 | 200 | 200 | 600 | 2 | wood | PIANOS |
| Organ | 45 | 300 | 200 | 2600 | 3 | metal | ORGANS |

Racial preferences are in the races column, for example Dulcimer for humans and halflings, and Celesta for elves.

**Lanterns and lamps (`resources/skills/blacksmith.txt:11, 21, 37, 63`; `resources/skills/glassblowing.txt:22, 47`; `resources/skills/sculpting.txt:78`)**

| Item | Level | Capacity (burn ticks) |
|---|---|---|
| lantern | 7 | 800 |
| hooded lantern | 17 | 1600 |
| bullseye lantern | 27 | 2400 |
| lamp | 37 | 3600 |
| glass lantern | 17 | 800 |
| glass lamp | 47 | 3600 |
| lamp (sculpted) | 57 | **0** |

The sculpted lamp at `resources/skills/sculpting.txt:78` has capacity 0, so it burns for zero ticks and reads as "used up" (see problems).

**Stoves (`resources/skills/blacksmith.txt:20, 36, 60, 85, 104, 118`; `resources/skills/sculpting.txt:34`).** Seven rows from level 16 to 66: large grill, range, grill, stove, furnace, small grill and a sculpted stove at level 26. Range and stove rows carry a `LID` misc flag.

**Tubs (`GenTub`, rows in `resources/skills/sculpting.txt`, `resources/skills/blacksmith.txt`, `resources/skills/glassblowing.txt`, `resources/skills/carpentry.txt`, `resources/skills/pottery.txt`).** Eight rows from level 23 to 83: tub, roman tub, claw-foot tub, jacuzzi, glass tub (level 73) and porcelain tub (level 83). The chair misc flag appears on the older tub rows.

**Mirrors (`resources/skills/glassblowing.txt`, four rows).** Dressing mirror (level 8), mirror (18), wall mirror (28) and full-length mirror (38). Values run from 54 to 1750.

**Paper, books and recipe books (`resources/skills/papermaking.txt`)**

- Paper (`GenPaper`, 15 rows): ticket and piece of paper (level 1), leaflet (2), flyer (3), posterboard (4), essay and tarot-card deck (5), parchment (6), rice paper (8), banner (9), trading card (10), vellum (10), poster (12), silkscreen and wedding banner (15). Materials vary: hide (parchment), leather (vellum), rice, silk and paper.
- Books (`GenPlayerBook`, 18 rows): log book (3), booklet (6), book (7), brochure (13), catalogue (13), journal (16), novel (24), magazine (36), manual (38), textbook (48), handbook (58), treatise (68), tome (78), omnibus (88).
- Recipe books (`GenRecipe`, 8 rows, lines 16, 28, 37, 41, 45, 48, 56, 67): recipe page (7), small (12), nice (18), large (22), huge (25), enormous (28), gigantic (45), humongous (75). The sizes track the number of pages.

**Prosthetic limbs (`GenLimb`, about 17 rows across carpentry, blacksmith, jewelmaking, pottery, glassblowing, sculpting and scrimshaw).** Examples: hook-hand (10), nose (20), eye (30), ear trumpet (40), peg leg (50), broomstick leg (1), table leg (10), glass eye (10), witch's nose (50). The naming rule in section 9 applies: a name that does not end in one of the 16 body parts gets no body part from its name alone. "Ear trumpet" ends in "trumpet", so its part depends on the secret identity. This is unverified.

**Furniture for torture (`resources/skills/torturesmith.txt:10-12`).** Two table variants (level 23) and one tent variant (level 24) built from `GenTable` and `GenTent`. Their ride-effect lists name a torture spell and injury effects (broken limbs, bleeding). The rows are neutral data; the mechanics are the ride-effect system outside this bucket.

**Graffiti and wall art.** `Thief_Graffiti` gives six random descriptions of scribbled text and a fixed name, "Graffiti" (`com/planet_ink/coffee_mud/Abilities/Thief/Thief_Graffiti.java:116-128`). Painted walls take keywords, description and a crafting brand from the painter (`com/planet_ink/coffee_mud/Abilities/Common/Painting.java:349-353`).

**Law book pages.** A summary page and eleven topic pages, listed in section 8. These are good seed content for a single-player city charter.

**Quest-board filters.** Quest boards filter on giver mask, source area mask, quest area mask, type and category. The masks are Zapper strings such as `-LEVEL +>10`, which are documented in `com/planet_ink/coffee_mud/Libraries/MUDZapper.java`.

## Notable design ideas

- **The lantern's oil is its clock.** The tank and the countdown are one quantity converted by a ratio (`com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:83-86, 150-154`). Refilling adds time, and the oil level shown to players is computed from time. No second state can drift out of step.
- **Fuel is burned by an effect, not by the stove.** The stove applies a `Burning` effect to the fuel item with the "destroy host on end" flag (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:310-316`). Consumption is therefore the generic effect system's job, and the stove only chooses the item.
- **Two packaging strategies behind one interface.** A stack holds one template and a count, which is cheap and fits any identical goods. A mixed package holds an ordered list of (class, count, data), which fits pockets and bundles. Both implement `PackagedItems`, so callers such as `disBundle` and `Package` do not care which they hold (`com/planet_ink/coffee_mud/Items/interfaces/PackagedItems.java`).
- **A ship is a room-set with a hull number.** The vessel owns an `Area` for its interior, a course queue for movement, a percentage for hull, and an ability value for sail power. Sinking changes sky access, and the deck rooms get an upward redirect exit to the ship's room (`com/planet_ink/coffee_mud/Items/Basic/GenSailingShip.java:455-464`).
- **Reflection with a guard.** The mirror catches a specific petrification spell, invokes it back on its source, and sets a flag so the reflected spell is not reflected again (`com/planet_ink/coffee_mud/Items/Basic/GenMirror.java`, `okMessage`). That is a small, clear reactive-item pattern.
- **Quests are read, not stored.** The quest board recomputes its list from the live quest library, caches it behind a hash, and filters it with masks (`com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java`, `getQuestList`). Adding a quest changes every board that matches it, with no per-board data to update.
- **Books as rule engines.** The law book reads and writes the legal state of an area, and a reader with authority activates a law just by reading it (`StdLawBook.executeMsg`). The recipe book is a crafting skill's recipe list on paper.
- **Portals return you.** A mobile portal finds its stationary partner by secret identity, then by name, then by first match, and writes its own room ID into that partner so the way back exists (`com/planet_ink/coffee_mud/Items/Basic/GenMobilePortal.java:47-120`).
- **Limbs are bodies in pieces.** A missing body part is a limb item whose name names the part. Wearing it restores the part's count, and the race's body mask decides how many parts are possible (`com/planet_ink/coffee_mud/Items/Basic/StdLimb.java`).
- **Data as the editor.** Every field is reachable as a string code, so the same codes drive the builder editors and `sameAs` (`GenericBuilder.GenItemCode`).

## Problems and cruft

**Defects that change behaviour**

1. **Partial unpack of a stack miscounts weight and value.** `GenPackagedItems.unPackage` sets the remaining weight and value to `itemWeight * number`, where `number` is the count removed, not the count left (`com/planet_ink/coffee_mud/Items/Basic/GenPackagedItems.java:219-220`). Reached through `RawCMaterial.unbundle` with a count (`com/planet_ink/coffee_mud/Libraries/RawCMaterial.java:233-238, 257`). Reachability from the callers in Herbology and Mycology is unverified.
2. **Partial unpack of a mixed package duplicates and drops items.** `GenPackagedVariety.unPackage` builds `newXml` for the remaining entries (`com/planet_ink/coffee_mud/Items/Basic/GenPackagedVariety.java:187, 196, 211-215`) and never stores it; `setPackageText` is not called (lines 176-246). A second partial unpack re-issues the original entries. This is the most serious defect in the bucket, since it can create items. Reached through `RawCMaterial.unbundle` with count 1, for example in `com/planet_ink/coffee_mud/Abilities/Common/Herbology.java:153`.
3. **Recipe book throws on read for a one-page book with no skill.** `GenRecipe.executeMsg` leaves `str` empty when the skill does not resolve and the book has one page, then calls `str.substring(0, str.length()-2)` (`com/planet_ink/coffee_mud/Items/Basic/GenRecipe.java:313`), which throws `StringIndexOutOfBoundsException`. Reading the code, `okMessage` can blank an unknown skill ID (lines 90-95). This is not run, and nothing in the method catches the exception.
4. **Wrong count in a recipe message.** `com/planet_ink/coffee_mud/Items/Basic/GenRecipe.java:85` prints "There are only 1 recipes." for every count above one, because the literal `1` is hard-coded and the count `z` is passed but never used.
5. **Wrong placeholder in the lantern fill refusal.** `com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:106` is `L("You can only fill @x1 with @x1!",name(),liquidTypeName())`. Both placeholders print the lantern's name, so the player reads "...with a hooded lantern!" instead of the oil name. Fix: use `@x2`.
6. **Dead branch in lantern fill.** `com/planet_ink/coffee_mud/Items/Basic/GenLantern.java:155` tests `!isGeneric()` inside `executeMsg`, but `isGeneric()` is always true for this class (lines 69-72), so the setDescription never runs.
7. **Wormhole never matches itself.** `GenWormhole.sameAs` tests `E instanceof GenSpaceBody` (`com/planet_ink/coffee_mud/Items/Basic/GenWormhole.java:177`). A `GenWormhole` is not a `GenSpaceBody`, so `sameAs` is always false for two wormholes. The method matches `com/planet_ink/coffee_mud/Items/Basic/GenSpaceBody.java:165-177`, so it looks copied and not renamed.
8. **Ruined items lose their original description.** `CoffeeUtensils.ruinItem` sets `I2.setDescription(I2.description())` (`com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:1381`), which copies the new item's own description into itself, so the original `I.description()` is never used. The ruin sentence is appended to the default "You see nothing special about..." text (line 1417).
9. **Crafted stoves appear to get zero capacity.** `com/planet_ink/coffee_mud/Abilities/Common/GenCraftSkill.java:967-978` applies to every `Light` that is also a `Container`. For a stove with misc `""` or `LID`, it sets capacity to 0 unless the item fits on the mouth or holds smokeables. `StdContainer.okMessage` then refuses every PUT with "You can't put anything in @x1!" when capacity is 0 (`com/planet_ink/coffee_mud/Items/Basic/StdContainer.java:150-151`). If so, a player-built stove cannot be fuelled. Unverified at runtime.
10. **A sculpted lamp burns for zero ticks.** `resources/skills/sculpting.txt:78` gives a lamp with capacity 0. `GenCraftSkill` then calls `setDuration(0)`, so the lamp is "used up" from the start (`LightSource.okMessage` TYP_HOLD, `com/planet_ink/coffee_mud/Items/Basic/LightSource.java:128-132`). Probably a data error.
11. **Rain destroys torches.** The rain path in `LightSource.executeMsg` (lines 258-260) reuses the burn-out branch. A `GenLightSource` torch rained on is destroyed, not just put out. This may be intended, but the message does not say so.
12. **Portal key and destination share one field.** `GenPortal.keyName()` returns `readableText` (`com/planet_ink/coffee_mud/Items/Basic/GenPortal.java:67-70`), and `readableText` is also the destination list used by `StdPortal.getDestinationRoom`. A locked portal's key would have to match the destination list. Intent unknown.
13. **Redundant stat code in `GenPiano`.** `com/planet_ink/coffee_mud/Items/Basic/GenPiano.java:139` tests `numFollowers()>0` twice.
14. **Equality returns early.** `GenResource.sameAs` (`com/planet_ink/coffee_mud/Items/Basic/GenResource.java:199-200`) and `GenLiquidResource.sameAs` (`com/planet_ink/coffee_mud/Items/Basic/GenLiquidResource.java:241-242`) return true when both values are null, instead of skipping that code, so later codes are never compared. Latent: I found no getter in these classes that returns null.
15. **Quest-board area masks are inconsistent.** The giver's area must match `AAREAMASK` (`com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java:163-165`), but in the quest-room loop a quest passes when some area does not match (`com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java:199-202`). `TAREAMASK` is set (`com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java:64`) and checked only as a gate (line 168), never in the filter. A board with only `TAREAMASK` set would therefore list no quests. Inferred from reading, not run.
16. **Paper overflow message gives the wrong number.** `com/planet_ink/coffee_mud/Items/Basic/StdPaper.java:161` tells the player they can fit `getMaxCharsPerPage()` more characters (2048), not the space remaining.
17. **`MAXPAGES` on paper cannot change.** `GenPaper.setStat` calls `setMaxPages`, which is empty in `StdPaper` (`com/planet_ink/coffee_mud/Items/Basic/StdPaper.java:96-97`).
18. **Stove fuel types accept unknown names as material zero.** `GenStove.setStat(CONSUMEDTYPES)` leaves `newMats[x]` at 0 when `FIND_CaseSensitive` returns -1 (`com/planet_ink/coffee_mud/Items/Basic/GenStove.java:393-396`). Zero is `RESOURCE_NOTHING` (`com/planet_ink/coffee_mud/Items/interfaces/RawMaterial.java:366`), so an unknown name becomes a fuel type of "nothing".
19. **Literal `%0D%0A` in descriptions.** `com/planet_ink/coffee_mud/Items/Basic/GenPlayerBook.java:48`, `com/planet_ink/coffee_mud/Items/Basic/GenLawBook.java:49`, `com/planet_ink/coffee_mud/Items/Basic/StdPlayerBook.java:54` and `com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java:51` pass `%0D%0A` (URL-encoded CRLF) in description text. I found no decoding on the display path, so players may see the literal sequence. Unverified.

**Latent, cosmetic, or cruft**

- Several `setStat` methods call `CMProps.setStatCodeExtensionValue` after handling a generic code, with no `else`: `com/planet_ink/coffee_mud/Items/Basic/GenLightSource.java:114-119`, `com/planet_ink/coffee_mud/Items/Basic/GenTitle.java:139-152`, `com/planet_ink/coffee_mud/Items/Basic/GenMap.java:114-119`, `com/planet_ink/coffee_mud/Items/Basic/GenPlayerBook.java:105-111`, `com/planet_ink/coffee_mud/Items/Basic/GenLawBook.java:96-101`. The extension setter only writes names in its own list, so this is harmless but confusing.
- `GenStove.getStatCodes` (lines 408-425) checks `codes!=null` twice.
- `GenPortal.MYCODES` lists `CLOSEDTEXT` twice (`com/planet_ink/coffee_mud/Items/Basic/GenPortal.java:100, 102`). Code index 13 is never reachable by name, and the `CLOSEWORD` and `OPENWORD` setters are commented out (lines 197-202).
- `GenPaper.executeMsg` only calls `super` (`com/planet_ink/coffee_mud/Items/Basic/GenPaper.java:63-65`).
- `GenMirror.oncePerRound` is not "once per round". It is reset by any other `okMessage` that passes through, so it is a re-entry guard, and the name is misleading (`com/planet_ink/coffee_mud/Items/Basic/GenMirror.java:43, 87-118`).
- `GenPlayerBook` self-destructs by name (`com/planet_ink/coffee_mud/Items/Basic/GenPlayerBook.java:67-76`). It is a single-item hack in generic code.
- `GenNavigableBoardable.sameAs` computes the full `AREA` XML before excluding it (line 221).
- Ships have 25 hull points per room, against 5 for the base siegable boardable. This looks like a tuning choice; no comment explains it.

## Takeaways for a Python SUD

1. **Put the content in data files of the same shape.** Keep the recipe rows as TSV and load them into one dataclass, for example `Recipe(name, level, ticks, materials, value, cls, misc, material, races, subtype)`. The misc column should be typed per item class: ride capacity for instruments, duration for lights, and flags for containers. This keeps the content of tables such as lanterns and instruments editable without touching Python.

2. **Make the lamp's oil and its clock one quantity.** Derive the oil level from the remaining burn time instead of storing both.

   ```python
   from dataclasses import dataclass

   @dataclass
   class Lamp:
       tank: int = 80            # oil units when full
       oil: int = 80
       burn_ticks: int = 800     # ticks of burn from a full tank
       burn_left: int = 800
       lit: bool = False

       def refill(self, units: int) -> int:
           taken = min(units, self.tank - self.oil)
           self.oil += taken
           self.burn_left = min(self.burn_ticks,
                                self.burn_left + taken * self.burn_ticks // self.tank)
           return taken

       def tick(self) -> None:
           if not self.lit:
               return
           self.burn_left -= 1
           self.oil = -(-self.burn_left * self.tank // self.burn_ticks)  # ceiling
           if self.burn_left <= 0:
               self.burn_left, self.oil, self.lit = 0, 0, False
   ```

3. **Keep a package's counts in its entries, and derive everything else.** A mixed package should hold `entries: list[tuple[Item, int]]` and compute its count and weight from them. `take(n)` should rewrite the entries in the same step, which avoids both the stale-XML and the stale-weight defects in this bucket.

   ```python
   import copy
   from dataclasses import dataclass, field

   @dataclass
   class Pack:
       entries: list = field(default_factory=list)   # [(template, count), ...]

       @property
       def count(self) -> int:
           return sum(n for _, n in self.entries)

       @property
       def weight(self) -> float:
           return sum(t.weight * n for t, n in self.entries)

       def take(self, n: int) -> list:
           out = []
           while n > 0 and self.entries:
               tmpl, k = self.entries[0]
               m = min(k, n)
               out += [copy.deepcopy(tmpl) for _ in range(m)]
               n -= m
               if m == k:
                   self.entries.pop(0)
               else:
                   self.entries[0] = (tmpl, k - m)
           return out
   ```

4. **Replace the shared `readableText` slot with named fields.** Give each item `duration`, `key_name` and `destinations` as separate fields. The dual use in `GenPortal` and the duplicated `CLOSEDTEXT` code show the cost of one slot.

5. **Use typed fields instead of string stat codes.** A dataclass with `to_dict` and `from_dict` covers saving. Keep a small registry of editable fields (name, type, validator) only for any builder tooling. The 25 generic codes are a sign of how much a stat-string interface costs without a type system.

6. **Ships: keep the hull percentage and the course queue, drop the boarding rules.** A vessel with `hull_pct`, `max_hull = 25 * rooms` (a tunable), `sail_power` and a `course` deque is enough. Movement can be one leg per turn, since the game may be turn-based. Replace the room-by-room sky logic with a single flag, and model sinking as a state change on the vessel (for example, its location becomes an underwater room).

7. **Quest boards: compute the list from world state at read time.** A board with a list of predicates (giver, area, type) is easier to reason about than the hash-and-cache scheme. Drop the cache unless profiling says otherwise.

8. **The mirror is a useful event pattern.** Implement "reflect the next spell of kind X back at the caster" as a hook with a `reflected` flag on the spell, so the reflected spell cannot reflect again.

9. **Limbs: use an explicit body-part field.** Suffix matching on names is fragile ("ear trumpet"). Store `body_part` as data and derive the name from it.

10. **Single-player drops.** Remove the boarding and siege rules, player-written books in a database (use a list of pages in the save file), clan-ruled law activation (use fixed laws authored as content), and the multi-player "Not while you are playing!" protections. Keep the quest-board accept prompt, the law book's topic pages as world documents, the packaging and recipe-book content, and the lantern.

## Coverage

- **Read in full (every line after the licence header and imports, which I skipped):** all 36 files in the manifest.
- **Skimmed:** `com/planet_ink/coffee_mud/Items/Basic/GenSiegableBoardable.java` (diffed against `com/planet_ink/coffee_mud/Items/Basic/GenNavigableBoardable.java`, which I read in full).
- **Skipped:** none.
- **Base classes read for the interactions described:** `com/planet_ink/coffee_mud/Items/Basic/LightSource.java` (most of the file), `com/planet_ink/coffee_mud/Items/Basic/StdRideable.java` (the recover and affect paths, not the whole file), `com/planet_ink/coffee_mud/Items/Basic/StdContainer.java` (the PUT check), `com/planet_ink/coffee_mud/Items/Basic/StdPaper.java` (whole), `com/planet_ink/coffee_mud/Items/Basic/StdQuestBoard.java` (whole), `com/planet_ink/coffee_mud/Items/Basic/StdLimb.java` (whole), `com/planet_ink/coffee_mud/Items/Basic/StdTub.java` (the liquid and state methods), `com/planet_ink/coffee_mud/Items/Basic/StdPortal.java` (destination and enter), `com/planet_ink/coffee_mud/Items/Basic/StdNavigableBoardable.java` (speed, tick, recover, portions), `com/planet_ink/coffee_mud/Items/Basic/StdSiegableBoardable.java` (hull, damage, look, portions), `com/planet_ink/coffee_mud/Items/Basic/StdTitle.java` (name and price), `com/planet_ink/coffee_mud/Items/Basic/StdMap.java` (map build and render), `com/planet_ink/coffee_mud/Items/Basic/StdPlayerBook.java` (chapter read), `com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java` (the read and write paths, portions), `com/planet_ink/coffee_mud/Items/Basic/StdPerfume.java` (constructor, wear, drink), `com/planet_ink/coffee_mud/Abilities/Misc/Burning.java` (flags and unInvoke), `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java` (`canBeRuined`, `ruinItem`, `isRuinedLoot`), `com/planet_ink/coffee_mud/Libraries/RawCMaterial.java` (`rebundle`, `disBundle`, `unbundle`), `com/planet_ink/coffee_mud/Abilities/Common/GenCraftSkill.java` (recipe format and Light and Container branch), `com/planet_ink/coffee_mud/Abilities/Common/InstrumentMaking.java` (recipe format and misc column), and `com/planet_ink/coffee_mud/core/CMProps.java` (tick constant and extension helpers).
- **Data read:** the rows in `resources/skills/` named in this chapter, and the mirror placements in `resources/randareas/prison.xml` and `resources/randareas/town.xml`. Other skill files were consulted by count only.
