# Items and their interfaces: materials, worn gear, containers, weapons, tech

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b136` (Items/interfaces) · 52 files · 8664 lines of Java.

Citations are `path:line`, with paths relative to `com/planet_ink/coffee_mud/` (the package folder under the CoffeeMud root). "Unverified" marks anything I did not confirm by reading code.

## Summary

The 52 files in `Items/interfaces` are the contracts that every physical object in CoffeeMud implements. They cover the root `Item` (location, condition, value, material), worn gear (`Wearable`, 20 body slots as bit codes), raw materials (`RawMaterial`, 210 built-in resources), containers with bitmask content filters, weapons and armor, and the powered "Tech" family (electronics, software, ship components). Most tunable data sits in nested `CODES` registries that merge built-in tables with rows from `coffeemud.ini`, so builders can add materials and wear locations without code changes. The resource table feeds gathering, room spawns, crafting difficulty and item balancing, and the corpse-expiry model decides how long dead bodies persist. For a single-player Python SUD, the reusable core is the material table, the body-slot and dependency model, the container type filter, and the corpse-as-container idea. The clan, PK-looting, ship-command and reader-list machinery assumes other players or a space setting.

## Key files and types

| File or class | Role | File lines |
|---|---|---|
| `Items/interfaces/RawMaterial.java` | Resource code layout, `Material` enum, 210 built-in resources (`DefResource`), `CODES` registry, value-sorted lookup | 1759 |
| `Items/interfaces/Wearable.java` | Worn-location bit codes, parallel tables (names, weights, dependency grid), `CODES` registry, wear predicates | 1163 |
| `Items/interfaces/Item.java` | Root item contract: container, owner, condition (`usesRemaining`), `baseGoldValue`/`value`, material, weight | 206 |
| `Items/interfaces/Container.java` | Container contract, capacity, `CONTAIN_*` content-type bits and names | 193 |
| `Items/interfaces/Weapon.java` | 17 damage types, 12 weapon classes, damage-to-message map, packed range and ammo masks | 281 |
| `Items/interfaces/Armor.java` | Clothing layer, layer attributes (`SEETHROUGH`, `MULTIWEAR`), `SizeDeviation` | 135 |
| `Items/interfaces/Technical.java` | `TechType` (28 kinds) and `TechCommand` (17 inter-component commands) | 352 |
| `Items/interfaces/Electronics.java` | Power model: capacity, remaining, target, activation | 133 |
| `Items/interfaces/DeadBody.java` | Corpse data: killer, victim name and hash, time of death, PK flag, saved MOB | 243 |
| `Items/interfaces/PlayingCard.java`, `HandOfCards.java`, `DeckOfCards.java` | Card bit encoding; hands and decks as containers | 120, 234, 91 |
| `Items/interfaces/ClanItem.java` | Clan-owned item types (17) and rightful owner | 194 |
| `Items/interfaces/Software.java` | Ship and computer software: menus, services, screen text | 195 |
| `Items/interfaces/ShipEngine.java`, `ShipDirectional.java`, `SpaceShip.java`, `ShipWarComponent.java`, `ElecPanel.java`, `TechComponent.java`, `FuelConsumer.java`, `PowerSource.java`, `PowerGenerator.java`, `NavigableItem.java`, `SiegableItem.java`, `Boardable.java`, `Computer.java` | Space and sailing ship components, panels, fuel and power | 230, 143, 136, 88, 86, 84, 109, 43, 62, 125, 89, 130, 67 |
| `Items/interfaces/Scroll.java`, `Wand.java`, `Potion.java`, `Pill.java`, `MagicDust.java`, `MiscMagic.java`, `SpellHolder.java`, `Perfume.java` | Magic consumables: spell holders and "usage" abilities | 98, 125, 64, 45, 49, 40, 75, 69 |
| `Items/interfaces/Food.java`, `Light.java`, `Coins.java`, `Book.java`, `RecipesBook.java`, `RoomMap.java`, `MusicalInstrument.java` | Everyday items | 75, 95, 97, 134, 61, 61, 134 |
| `Items/interfaces/DoorKey.java`, `InnKey.java`, `CagedAnimal.java`, `FalseLimb.java`, `BodyToken.java`, `Shield.java`, `Ammunition.java`, `AmmunitionWeapon.java`, `PackagedItems.java`, `ArchonOnly.java` | Specialised items and marker interfaces | 57, 46, 96, 101, 42, 40, 71, 123, 133, 42 |

Implementations cited below live outside this bucket. They are `Items/Basic/StdItem.java`, `Items/Basic/StdLimb.java`, `Items/Basic/StdContainer.java`, `Items/Basic/Corpse.java`, `Items/Basic/LightSource.java`, `Items/Basic/StdCoins.java`, `Items/Armor/StdArmor.java`, `Items/Weapons/StdWeapon.java`, `Items/BasicTech/StdElecItem.java`, `Items/MiscMagic/StdPlayingCard.java`, `MOBS/StdMOB.java`, `Races/interfaces/Race.java`, `Races/StdRace.java`, `Libraries/TimsLibrary.java`, `Libraries/RawCMaterial.java`, `Libraries/MUDFight.java`, `Locales/StdRoom.java`, `core/interfaces/ItemPossessor.java`, and `Abilities/Common/GatheringSkill.java`.

## How it works

### 1. The item contract (`Item.java`)

- **Location.** `container()` returns the enclosing container (`Item.java:49`) and `owner()` returns the mob or room (`Item.java:195`). `ultimateContainer()` walks the chain (`Item.java:67`). `numberOfItems()` is 1 for ordinary items (`Item.java:83`).
- **Condition is overloaded by type.** `usesRemaining()` means charges for wands, condition for weapons and armor, and quantity for ammunition (`Item.java:119-121`). `subjectToWearAndTear()` says whether it matters (`Item.java:117`).
- **Value.** `baseGoldValue()` is the builder-entered figure. `value()` is the adjusted figure after enchantment and condition (`Item.java:146-152`).
- **Material.** `material()` returns one integer resource code (`Item.java:168`). See section 3.
- **Weight.** `recursiveWeight()` returns own weight plus contents for containers (`Item.java:186`).

### 2. Worn gear (`Wearable.java`)

**Data model.** Each location is one bit. `IN_INVENTORY` is 0, `WORN_HEAD` is 1, `WORN_NECK` is 2, `WORN_TORSO` is 4, and so on up to `WORN_BACK` = 524288 (`Wearable.java:251-291`). The highest allowed code is 2^60 (`:293`). Several parallel arrays are indexed by position:

| Code position | Location | Usual name | Armor strength | Cloth / leather / metal points |
|---|---|---|---|---|
| 1 | head | hat | 1.0 | 2 / 6 / 10 |
| 2 | neck | necklace | 0.5 | 1 / 3 / 5 |
| 3 | torso | shirt | 3.0 | 6 / 18 / 30 |
| 4 | arms | sleeves | 1.0 | 3 / 9 / 15 |
| 5 | left wrist | bracelet | 0.5 | 1 / 2 / 3 |
| 6 | right wrist | bracelet | 0.5 | 1 / 2 / 3 |
| 7 | left finger | ring | 0.25 | 1 / 1 / 2 |
| 8 | right finger | ring | 0.25 | 1 / 1 / 1 |
| 9 | feet | shoe | 0.5 | 2 / 6 / 10 |
| 10 | held | gizmo | 2.0 | 3 / 9 / 15 |
| 11 | wield | weapon | 1.0 | 3 / 9 / 15 |
| 12 | hands | gloves | 0.5 | 1 / 3 / 5 |
| 13 | floating nearby | floaty | 0.01 | 0 / 0 / 0 |
| 14 | waist | belt | 0.5 | 1 / 3 / 5 |
| 15 | legs | pants | 2.0 | 3 / 9 / 15 |
| 16 | eyes | eyeglasses | 0.25 | 1 / 1 / 2 |
| 17 | ears | earmuffs | 0.25 | 1 / 1 / 2 |
| 18 | body | cloak | 1.5 | 1 / 2 / 3 |
| 19 | mouth | mouthpiece | 0.01 | 1 / 1 / 2 |
| 20 | back | cape | 1.0 | 1 / 2 / 3 |

Sources: names `Wearable.java:383-405` and usual names `:410-432`; strengths `:327-349`; points `:356-378`. Other tables are `DEFAULT_WORN_CODES` (`:435`), the dependency grid `DEFAULT_WORN_DEPENDENCYGRID` (`:462-484`), and the display order `DEFAULT_WORN_ORDER` (`:300-321`).

**Registry.** The nested `CODES` class (`Wearable.java:491`) builds the tables and then accepts `ADDWEARLOC_` and `REPLACEWEARLOC_` rows from the ini (`:503-504`). Each row has six comma-separated fields: dependency mask, armor strength, display order, and the three weights. Adding is refused past 62 codes (`:1089`).

**Where wear capacity comes from.** The body, not the item, decides how many slots exist.

- `StdMOB.getWearPositions` (`MOBS/StdMOB.java:5897`) walks `Race.BODY_WEARGRID` (`Races/interfaces/Race.java:630`). Each row maps a body part to a location with a divisor. Eyes give one slot per two eyes (`{WORN_EYES,2}`), head one per head, legs and feet one per two. A negative divisor means any such part removes the slot: an antenna removes the head slot and wings remove the back slot (`Race.java:631-649`).
- Hands get a special case. For held, left finger and left wrist the count is one less than the hand count (`StdMOB.java:5942-5945`).
- `freeWearPositions` (`StdMOB.java:5881`) subtracts items already worn there. `MAXWEARPERLOC` caps a location (`coffeemud.ini:677`, default 6; the code fallback is 10 at `core/CMProps.java:3199`). `MAXITEMSWORN` caps total worn items, and 0 disables it (`coffeemud.ini:669`).

**Predicates** (`Items/Basic/StdItem.java`):

- `amWearingAt(code)` (`:374`) treats code 0 as "in inventory". Otherwise it is a bit-containment test.
- `fitsOn(code)` (`:395`) asks whether the item's allowed-location bitmap contains the code.
- `canWear(mob, where)` (`:540`) requires `rawProperLocationBitmap & where == where`. A logical-AND item (one that needs all of its locations, such as a two-handed weapon) must pass `whereCantWear == 0`. Any other item must find `freeWearPositions > 0`.
- `whereCantWear(mob)` (`:491-538`) returns 0 if some fitting location has room. Otherwise it returns one code (the last fitting location examined), `WORN_HELD` for a held-only item, or -1 if the item has no allowed locations.
- `wearIfPossible(mob)` (`:431`) tries every code in display order (`ALL_ORDERED`), so the first free location wins. `wearAt` (`:442`) sets the whole bitmap for logical-AND items and a single bit otherwise.

**Layering.** `fetchWornItems` (`MOBS/StdMOB.java:5982-6003`) decides which worn items compete with a new armor piece. Armor at clothing layer L conflicts with worn armor at layers L-1, L and L+1, and with everything above L. A MULTIWEAR piece ignores other MULTIWEAR pieces in that window. Non-armor worn items always count. Layer 0 is the baseline, positive layers are outer garments such as coats, and negative layers are underwear (`Armor.java:70-76` Javadoc).

**Dependency rule** (`Items/Basic/StdLimb.java:82-111`). A limb item (a body part held by the mob) cannot be removed while anything is worn on a location in its dependency mask. The refusal reads "You'll need to remove <T-NAMESELF> first." The HEAD entry is `WORN_EYES|WORN_EARS|WORN_MOUTH` (`Wearable.java:464`), so the head cannot come off while eyewear, earmuffs or a mouthpiece is on. The grid is indexed by display position, and the check tests `dependency_masks()[w] > 0` (`StdLimb.java:97`), which is why the -1 "no dependency" sentinel is harmless.

**Sizing** (`Items/Armor/StdArmor.java:170-212`). Armor compares its `phyStats().height()` with the wearer's and allows a deviation. The default allowance is 200. Torso, legs, waist, arms, hands and feet (`SMALL_DEVIATION_WEAR_LOCS`, `:161-167`) get 20. Eyes and ears get 1000 and neck gets 5000. Mouth and floating items are not checked. A failed fit prints "@x1 doesn't fit you -- it's too big." (`:227`) or the "too small" variant, from `okMessage` on `TYP_WEAR`.

### 3. Raw materials (`RawMaterial.java`)

**Code layout.** A resource code is `material << 8 | index`. The material bits are `MATERIAL_* = n << 8` for n from 0 to 15 (`RawMaterial.java:127-207`), so `MATERIAL_METAL` is `3 << 8`. The index is the low byte (`RESOURCE_MASK = 255`, `:577`). For example, `RESOURCE_GOLD = MATERIAL_METAL | 37` (`:403`). The `Material` enum (`:215-364`) gives the 16 classes with `mask()` and `noun()`.

**Resource table.** `DefResource` (`:609-850`) holds 210 rows. Each row carries a code, a value, a frequency, a hardness, a buoyancy, a smell, an effect string and a flag. The Javadoc says value is base gold per pound and buoyancy is 1000 times density (`:598-608`). Examples:

- `GOLD(RESOURCE_GOLD, 150, 1, 4, 19320, ...)` at `:648`.
- `DIAMOND(..., 500, 1, 9, 3510, ...)` at `:681`.
- `DILITHIUM(..., 5000, 0, 7, 5010, ...)` at `:812`.

The flags are BERRY (8 resources), FISH (7) and WOODY (12) (`ResourceFlag`, `:585-596`). `add()` also marks every wooden-material resource as woody (`:1650`).

**Registry.** The `CODES` constructor (`:857-954`) sorts the defaults by index. It then reads `ADDMATERIAL_` and `REPLACEMATERIAL_` rows from the ini (`:872-873`). Each row has eight fields: material, smell, value, frequency, hardness, buoyancy, flag and ability (`:898-929`). A row with the wrong field count is logged and skipped (`:899`).

**Lookups.** `VALUE` (`:1368`), `FREQUENCY` (`:1390`), `HARDNESS` (`:1451`), `BOUYANCY` (`:1473`), `SMELL` (`:1314`) and `EFFECTA` (`:1517-1571`). `EFFECTA` parses effect strings such as `Prop_UseSpellCast2(Spell_AcidSpray)` into cached Ability objects, with the parenthesised text as misc-text parameters. `MOST_FREQUENT` (`:1412`) returns the highest-frequency resource of a material. Callers include `Commands/GModify.java:270`, `Abilities/Common/CraftingSkill.java:1146`, `Abilities/Common/Baiting.java:226` and `Libraries/CoffeeMaker.java:4090`.

**Random draw.** `RawCMaterial.getRandomResourceOfMaterial` (`Libraries/RawCMaterial.java:47-58`) calls `getValueSortedResources` (`RawMaterial.java:1580-1622`) and then picks a uniform index with `dice().roll(1, size, -1)`. The pool is built as a `TreeSet` ordered by value only (`:1592-1599`), so two resources of equal value cannot both be in it. See Problems, item 1.

**Frequency as a weight.** Two other systems treat `FREQUENCY` as a weight.

- `GatheringSkill` (`Abilities/Common/GatheringSkill.java:187-199`) copies each resource into a list `frequency` times. A frequency of 0 means it is never gathered.
- `StdRoom` (`Locales/StdRoom.java:716-731`) builds an `IntegerRangeMap` with inclusive ranges `[min, min+freq]` and then rolls `dice().roll(1, max, 0)`. `Dice.roll` returns 1 to `max` (`Libraries/Dice.java:398-405`) and the ranges are inclusive (`core/collections/IntegerRangeMap.java:112-120`). Each resource therefore gets `freq+1` slots, except the first, which gets `freq` because slot 0 is never rolled.

**Hardness in crafting.** Crafting subtracts a constant from hardness to adjust difficulty. Cobbling uses hardness minus 6 (`Abilities/Common/Cobbling.java:475`), Fletching minus 3 (`Fletching.java:474`) and LeatherWorking minus 2 (`LeatherWorking.java:622`). Rusting strike only affects hardness below 10, with amount `10 * (10 - hardness)` (`Abilities/Skills/Skill_RustingStrike.java:168-171`). A death-trap attempt count is `(hardness + level) * 2` (`Abilities/Thief/Thief_DeathTrap.java:349`).

**Buoyancy and smell** are display and sensing data. Buoyancy feeds a "floatilla" total in `Libraries/Sense.java:1757` and is shown by `Abilities/Druid/Druid_KnowPlants.java:178`. Smell appears in sniff text at `Libraries/CommonMsgs.java:1707`. I found no general float-or-sink physics that uses buoyancy (unverified beyond these call sites).

### 4. Containers (`Container.java`, `StdContainer.java`)

- **Capacity** is a weight used as a volume proxy (`Container.java:58-64`).
- **Content filter.** `containTypes()` is a bitmask. Bit i corresponds to the flag `2^i`. The flags run from `CONTAIN_LIQUID = 1` up to `CONTAIN_SHIELDS = 262144` (`Container.java:127-165`).
- **Names.** `CONTAIN_DESCS` (`:172-192`) has 20 entries, with index 0 "ANYTHING" and index i+1 naming bit i. `CraftingSkill.java:339-341` relies on this (`pow(2, bit-1)`), as does `CMath.s_parseBitLongExpression` (`core/CMath.java:2249-2270`).
- **Check.** `StdContainer.canContain` (`Items/Basic/StdContainer.java:482`) returns true when `containType == 0`. Otherwise it tests each set bit. Liquids are matched by material class, coins by `instanceof Coins`, swords and daggers by weapon class, keys by `DoorKey`, drinkables by `Drink` and eatables by `Food`.

### 5. Corpses (`DeadBody.java`, `Items/Basic/Corpse.java`)

- **Data.** `DeadBody` holds the character stats, the victim's name and hash, description, killer name, `isKillerPlayer`, `lastMessage` (usually the killing blow), `killerTool`, `destroyAfterLooting`, `isPlayerCorpse`, `mobPKFlag`, `timeOfDeath` in milliseconds, and a saved MOB (`DeadBody.java:49-241`).
- **Placement.** `StdRace.java:994-999` picks `Expire.Monster_Body` for NPCs and `Expire.Player_Body` otherwise, then calls `room.addItem(body, code)`. `StdRoom.addItem` (`Locales/StdRoom.java:2358-2382`) puts bodies at the top of the room and sets the expiry to now plus `Expire.getExpirationMilliseconds()`. That method returns the configured value times `MILI_MINUTE` (`core/interfaces/ItemPossessor.java:102-107`).
- **Defaults.** The `EXPIRATIONS` list is ordered monster loot, player drops, resources, monster corpses, player corpses, and its defaults are `30,1200,60,30,1330` minutes (`coffeemud.ini:1168-1173`, code defaults at `core/CMProps.java:3053-3080`). So an NPC corpse lasts 30 minutes and a player corpse about 22 hours.
- **Looting.** `Corpse.okMessage` (`Items/Basic/Corpse.java:303-361`) blocks `TYP_GET` and ability-driven removal of player corpses, unless the policy allows it. `CORPSEGUARD` values are ALL, ANY, SELFONLY and PKONLY (`coffeemud.ini:408-413`, default ALL). SELFONLY allows only one's own corpse and PKONLY requires the PK flag (`Corpse.java:326-359`).
- **Decay message.** Sniffing a corpse more than half an hour after death gives "has definitely started to decay" (`Corpse.java:296-297`). Rot is expiry, not a tick. `Tickable.TICKID_DEADBODY_DECAY` exists (`core/interfaces/Tickable.java:88`) but only `Prayer_PreserveBody` deletes it (`Abilities/Prayers/Prayer_PreserveBody.java:106`).
- **Auto-loot on kill** (`Libraries/MUDFight.java:1643-1667`). A killer with AUTOLOOT takes the items from the body. If `isDestroyedAfterLooting()` is true, raw materials are left behind (`:1662`). The body is then destroyed (`:1706-1715`). Gold is divided evenly among the looters (`MUDFight.java:1564-1567`), and each share becomes a Coins item for its looter (`:1676-1678`).

### 6. Weapons (`Weapon.java`, `Items/Weapons/StdWeapon.java`)

- **Damage types** (`Weapon.java:49-81`), named for messages in `TYPE_DESCS` (`:87-106`): NATURAL, SLASHING, PIERCING, BASHING, BURNING, BURSTING, SHOOTING, FROSTING, GASSING, MELTING, STRIKING, LASERING, SONICING, DISRUPTING, STULTIFYING, CORRUPTING and SCRAPING.
- **Damage to message type.** `TYPE_MSG_MAP` (`:111-130`) maps BURNING to `CMMsg.TYP_FIRE`, FROSTING to `TYP_COLD`, GASSING to `TYP_GAS`, MELTING to `TYP_ACID`, STRIKING to `TYP_ELECTRIC`, LASERING to `TYP_LASER`, SONICING to `TYP_SONIC`, DISRUPTING to `TYP_POISON`, STULTIFYING to `TYP_PARALYZE`, CORRUPTING to `TYP_UNDEAD` and SCRAPING to `TYP_WATER`. The rest map to -1.
- **Classes** (`:137-160`): AXE, BLUNT, EDGED, FLAILED, HAMMER, NATURAL, POLEARM, RANGED, SWORD, DAGGER, STAFF, THROWN.
- **Range.** `minRange()` and `maxRange()` read a stored pair (`StdWeapon.java:388-402`). Skills can override either value by setting a flag in the `phyStats().armor()` bits: max range is 7 bits at `0x7f` with flag `0x80`, min range 7 bits at `0x7f00` with flag `0x8000`, and ammo capacity 7 bits at `0x7f0000` with flag `0x800000` (`Weapon.java:182-207`). The grep shows `Skill_LongReach`, `Fighter_FarShot` and `Fighter_PointBlank` use these flags.
- **Ammunition.** `requiresAmmunition()` is true when the readable-text field is non-empty and the item is not a Wand (`StdWeapon.java:416-420`). The ammunition type is therefore stored in the readable-text field. `ammunitionCapacity()` (`:477`) uses the override bits or the stored capacity.
- **Messages.** `hitString` and `missString` delegate to `CMLib.combat()` standard strings keyed by damage type and class (`StdWeapon.java:376-385`).

### 7. Armor and item balance (`Armor.java`, `Libraries/TimsLibrary.java`)

- **Layers.** `getClothingLayer()` and `getLayerAttributes()` (`Armor.java:77, 98`). The attribute bits are SEETHROUGH = 1 (see-through on the top layer, so items beneath show) and MULTIWEAR = 2 (many items at one layer), at `:52-68`.
- **Derived numbers.** The interface does not fix armor points, weight or value. `TimsLibrary` (an `ItemBalanceLibrary`) derives them from material and level when items are balanced or valued (`Libraries/TimsLibrary.java:703-878`, and `timsItemAdjustments` at `:1168`). Material point arrays are in `getArmorMaterialPointsArray` (`Libraries/TimsLibrary.java:734-761`):
  - leather, wood, rock, glass and synthetic: `{0,0,1,5,10,16,23,31,40,49,58,67,76,85,94}`
  - cloth: `{0,3,7,12,18,25,33,42,52,62,72,82,92,102}`
  - metal, mithril, precious and energy: `{0,0,0,0,1,3,5,8,12,17,23,30,38,46,54,62,70,78,86,94}`
- **Base value** (`TimsLibrary.java:763-778`). A weapon costs `round(2 * (weight*matValue + (2*damage + attack + 10*reach) * damage) / ((hands+1) * (thrown+1)))`. Armor costs `round((pts^2 + matValue) * weight / 2)`.
- **Weight and points per location** come from the weight tables. `getLocationArmorWeight` (`:813`) selects the cloth, leather or metal column by material family, and `getLocationArmorPoints` (`:867`) multiplies the strength by the material points.

### 8. Electronics, tech and ships

- **Power.** An `Electronics` item has `powerCapacity`, `powerRemaining`, `powerTarget` (the level it charges to), an `activated` flag and `powerNeeds()` (`Electronics.java:54-132`). `StdElecItem.powerNeeds()` returns `(int)(powerCapacity - power)` (`Items/BasicTech/StdElecItem.java:113-116`). `PowerSource` produces power (generators and batteries). `FuelConsumer` burns fuel that must be in a container of a typed kind (`FuelConsumer.java:53`). `PowerGenerator` combines the two (`PowerGenerator.java:41-51`).
- **Tech level** is `techLevel()` (`Technical.java:121`), stored in `phyStats().ability()` by `StdTechItem`. Section 9 notes that this field is reused for other things.
- **TechType** (`Technical.java:51-108`) has 28 kinds, each with a long and short display name, for example `SHIP_ENGINE("Ship Engine", "Engine")`. The kinds are ANY, GIZMO, CONTROL_PANEL, eight PERSONAL_* kinds, and the SHIP_* and COMP_* kinds.
- **TechCommand** (`Technical.java:184-351`) has 17 typed commands: THRUST, ACCELERATED, ACCELERATION, COMPONENTFAILURE, SENSE, AIRREFRESH, POWERSET, DIRSET, AIMSET, TARGETSET, FIRE, SHIELDSET, GRAVITYCHANGE, SWSVCALLOW, SWSVCNEED, SWSVCREQ and SWSVCRES. `makeCommand` (`:227`) encodes the name and typed arguments as a string, and `confirmAndTranslate` (`:280`) decodes and type-checks it. In `GenSpaceShip`, commands travel as the target message of a control CMMsg (`Items/BasicTech/GenSpaceShip.java:233-250`, `:421-430`), and listeners registered per command receive them (`SpaceShip.registerListener`, `SpaceShip.java:123`).
- **Thrust.** `ShipEngine.ShipAccelerator` (`ShipEngine.java:160-229`) is a strategy with `executeThrust(..., simulation)`, `getInjectedThrust`, `getFuelToConsume` and `getFuelThrustCap`.
- **Directions.** `ShipDir` (`ShipDirectional.java:50-72`) has six values, AFT, PORT, VENTRAL, DORSEL, STARBOARD and FORWARD, with `opposite()` pairs.
- **Ship flags** are IN_THE_AIR, NO_GRAVITY and ARTI_GRAV (`SpaceShip.java:46-51`).
- **Software** (`Software.java`). Software has a parent menu (`:68`), activation and command matching (`:116`, `:135`), and screen messages (`:151`). Services are TARGETING, IDENTIFICATION and COORDQUERY (`:56-60`).
- **Siege.** `SiegeCommand` is TARGET, AIM and IMPLODE (`SiegableItem.java:39-45`). `getSiegeWeaponAimings` returns weapon-to-coordinate pairs (`:52`).

### 9. Cards (`PlayingCard.java`, `Items/MiscMagic/StdPlayingCard.java`)

- **Bit code.** `suits = {0,16,32,48}` for spades, clubs, hearts and diamonds (`PlayingCard.java:29-30`). `cards = {2..14}`, with the ace at 14 (`:33`). Face-up is bit 64 (`:50-80`). `StdPlayingCard` stores the value in the low four bits (`getBitEncodedValue` returns `ability & 15`, `StdPlayingCard.java:98-100`), and tests face-up with `ability & 64` (`:105-107`).
- **String code.** A card is written as a suit letter followed by a face letter or a number (`HandOfCards.java:111-117`).
- **Containers.** `HandOfCards extends Container` (`HandOfCards.java:26`). It has shuffle, sort by value (ace high or low) and per-value and per-suit queries (`:32-96`). `DeckOfCards` manages a deck and per-player hands (`DeckOfCards.java:40-81`).

### 10. Magic and consumables (`SpellHolder`, `Scroll`, `Wand`, `Potion`, `Pill`, `Food`, `MagicDust`, `Light`)

- **Spell holders.** `SpellHolder.getSpells()` returns a list of Ability objects (`SpellHolder.java:48`). `Scroll`, `Potion`, `Pill` and `MagicDust` implement it.
- **Scroll.** `useTheScroll` (`Scroll.java:67`), `readIfAble` (`:97`), and a nested `ScrollUsage` ability that carries a read-magic type (`:49-57`).
- **Wand.** A nested `WandUsage` ability has a `WAND_OPTIONS` table of spell, prayer, chant and skill families (`Wand.java:50-62`). `magicWord()` (`:108`) and `checkWave` and `waveIfAble` (`:93, :102`) implement the "say the word at a target" mechanic.
- **Food and drink.** `nourishment()` and `bite()` (`Food.java:47-65`), `eatIfAble` (`Pill.java:44`), `drinkIfAble` and `isDrunk` (`Potion.java:47-63`).
- **Dust.** `MagicDust.spreadIfAble(mob, target)` (`MagicDust.java:48`) is the targeted form, described in its Javadoc as "malicious magic".
- **Light.** `LightSource` (`Items/Basic/LightSource.java`) starts with `durationTicks = 200` (`:43`). It schedules `TICKID_LIGHT_FLICKERS` (`:114`, id at `Tickable.java:90`). Each tick decrements the counter, and at zero the light says "flickers and burns out." and is destroyed if `destroyedWhenBurnedOut` (`:172-210`). Rain outdoors, or being under water, puts it out (`:214-221`, message "The rain makes @x1 go out." at `:258`). At the default 4-second tick (`core/CMProps.java:681`) 200 ticks is about 13 minutes (derived, not timed).

### 11. Other items

- **Coins.** `Coins` has a count, a denomination and a currency (`Coins.java:46-96`). `StdCoins` stores the count in `phyStats().ability()`, capped at `Integer.MAX_VALUE` (`Items/Basic/StdCoins.java:118-124`). `getTotalValue` is denomination times count (`:153-156`).
- **Clan items.** `ClanItemType` has 17 kinds, from FLAG (`ClanItem.java:50`) to SIGNPOST (`:66`). `rightfulOwner()` (`:182`) names the room or mob the item must stay with, so clan items return to members (multiplayer).
- **Books.** `canRead`, `canWrite` and `isJournal` (`Book.java:116-133`).
- **Instruments.** `InstrumentType` has 18 kinds, from CLARINETS to XYLOPHONES plus OTHER (`MusicalInstrument.java:49-82`).
- **Keys.** `DoorKey` names a lock (`DoorKey.java:47-56`). `InnKey` can `hangOnRack` with a shopkeeper (`InnKey.java:45`). Its Javadoc says it flies back to the shopkeeper "when its time is up".
- **Prosthetics.** `FalseLimb` re-provides a wear location and a race ID for an amputee (`FalseLimb.java:50-89`).
- **Caged animals.** `CagedAnimal` converts a MOB to an item and back (`CagedAnimal.java:57-65`).
- **Packaged stacks.** `PackagedItems` holds many items as one object with less overhead than a container (`PackagedItems.java:44-132`). The Javadoc says it is "similar to a container, but without some of the deep engine features".
- **Marker interfaces.** `ArchonOnly` (`ArchonOnly.java:39-42`) marks admin-only items, and the comment says builder-made items should never be archon-only. `BodyToken` is thin under-armor with no protection (`BodyToken.java:39-42`). `MiscMagic` groups wands, potions, scrolls and similar, and calls itself "mildly deprecated" (`MiscMagic.java:38-40`).

## Content worth mining

### Resource catalogue

The 210 default resources group by material as follows. "Distinct values" counts the base values present, which is the number of resources the random draw can reach in that material (see Problems, item 1).

| Material | Resources | Distinct base values |
|---|---|---|
| Vegetation | 54 | 8 |
| Precious (gems and jewels) | 26 | 16 |
| Rock | 22 | 8 |
| Metal | 20 | 12 |
| Wooden | 20 | 12 |
| Liquid | 18 | 8 |
| Flesh | 15 | 8 |
| Cloth | 10 | 7 |
| Gas | 6 | 4 |
| Energy | 4 | 3 |
| Synthetic | 4 | 3 |
| Glass | 4 | 3 |
| Mithril | 3 | 3 |
| Leather | 2 | 1 |
| Paper | 1 | 1 |
| Unknown | 1 | 1 |
| **Total** | **210** | **98** |

Counts were made by parsing the `DefResource` rows of `Items/interfaces/RawMaterial.java`. Sample rows, with fields value, frequency, hardness, buoyancy:

| Resource | Material | Value | Freq | Hard | Buoy | Smell or effect | Source |
|---|---|---|---|---|---|---|---|
| GOLD | metal | 150 | 1 | 4 | 19320 | none | `:648` |
| DIAMOND | precious | 500 | 1 | 9 | 3510 | none | `:681` |
| DILITHIUM | precious | 5000 | 0 | 7 | 5010 | none | `:812` |
| COFFEE | liquid | 0 | 10 | 0 | 430 | "rich coffee"; effect `Poison_Caffeine` | `:689` |
| COFFEEBEANS | vegetation | 2 | 10 | 1 | 560 | "mild coffee"; effect `Poison_Caffeine` | `:688` |
| LEMONS | vegetation | 2 | 10 | 1 | 480 | "strong citrusy"; effect `Prop_UseSpellCast2(Spell_ShrinkMouth)` | `:629` |
| SLIME | liquid | 5 | 10 | 1 | 750 | "horridly acidic"; effect `Prop_UseSpellCast2(Spell_AcidSpray)` | `:792` |
| DRAGONBLOOD | liquid | 10 | 100 | 0 | 1025 | "mild salty"; effect `Prop_UseSpellCast2(Prayer_MinorInfusion)` | `:778` |
| URANIUM | rock | 100 | 1 | 0 | 2000 | effect `Disease_RadiationSickness` | `:803` |

Six resources carry an effect string, and the rest carry none. Seven resources carry the fish flag, eight berry and twelve woody.

### Worn-location names

The usual names in the table above (`Wearable.java:410-432`) are a ready-made vocabulary for describing what a character wears: hat, necklace, shirt, sleeves, bracelet, ring, shoe, gizmo, weapon, gloves, floaty, belt, pants, eyeglasses, earmuffs, cloak, mouthpiece and cape.

### Enumerations a text game can reuse

- **Weapon damage types** (17): natural, slashing, piercing, bashing, burning, bursting, shooting, frosting, gassing, melting, striking, lasering, sonicing, disrupting, stultifying, corrupting, scraping (`Weapon.java:87-106`).
- **Weapon classes** (12): axe, blunt, edged, flailed, hammer, natural, polearm, ranged, sword, dagger, staff, thrown (`Weapon.java:166-180`).
- **Container contents** (19 bits): liquid, coins, swords, daggers, other weapons, one-handed weapons, bodies, readables, scrolls, caged animals, keys, drinkables, clothes, smokeables, space-ship components, footwear, raw materials, eatables, shields (`Container.java:172-192`).
- **Armor layers**: underwear and hose below zero, baseline at zero, coats and shawls above (`Armor.java:70-76`).
- **Clan item kinds** (17): flag, banner, gavel, propaganda, gathering tool, crafting tool, justice tool, scavenging tool, clan item, taxing tool, journal, anti-propaganda, merchant tool, officer emblem, tabard, sailors cap, signpost (`ClanItem.java:50-66`).
- **Instrument kinds** (18): clarinets through xylophones, plus other (`MusicalInstrument.java:51-68`).
- **Expiry timers**: 30 minutes for monster loot, 1200 for player drops, 60 for gathered resources, 30 for monster corpses, 1330 for player corpses (`coffeemud.ini:1173`).

### Short messages (paraphrased)

- Armor does not fit, too big or too small (`Items/Armor/StdArmor.java:227`).
- You'll need to remove the blocking item first (`Items/Basic/StdLimb.java:108`).
- The light flickers and burns out (`Items/Basic/LightSource.java:185`).
- The rain makes the light go out (`LightSource.java:258`).
- The corpse has started to decay (`Items/Basic/Corpse.java:297`).

## Notable design ideas

- **Dependency-based unequipping.** A body part cannot be removed while something depends on it (`StdLimb.java:82-111`, grid at `Wearable.java:462-484`). This is a simple, data-only rule that produces sensible messages with no special-case code.
- **Slots that come from anatomy.** Eyewear and rings are limited by how many eyes and hands a character has, and an antenna removes a head slot (`Race.java:630-649`). A race change therefore changes equipment rules with no per-item edits.
- **Layered clothing with a conflict window.** Armor at one layer blocks neighbours one layer up and down, and only multiwear garments stack (`StdMOB.java:5982-6003`). Coats, shirts and underwear come out of one integer.
- **Anatomically sized armor.** Each location has its own height tolerance (`StdArmor.java:154-212`), so a child's helmet and a giant's helmet are distinct without a size table per item.
- **Resource codes packed into one integer.** The material class is the high byte and the resource is the low byte (`RawMaterial.java:127-577`). A material-level test is a mask, so "is this any kind of metal" is a single comparison.
- **One table, several uses.** The same `DefResource` row supplies value, weight-as-frequency for spawns, hardness for crafting, buoyancy for sensing, smell for sniffing, and effect abilities for consumption (`RawMaterial.java:609-1571`).
- **Expiry by category.** Each class of world junk expires on its own timer from the ini (`ItemPossessor.java:83-107`). That makes the cleanup policy a configuration choice.
- **Corpses that remember.** A corpse stores the killer, the killer's tool and the last message seen (`DeadBody.java:111-157`). That gives a narrative hook for "killed by" text and for looting rules.
- **Typed tech commands.** The `TechCommand` enum attaches parameter types to each message and validates them (`Technical.java:184-351`). The idea is sound even if the string encoding is more than a single process needs.
- **Cards in one integer.** Suit, value and face-up state fit in one int with a small bit layout (`PlayingCard.java:29-33`, `:50-80`). That is compact enough to save and compare.

## Problems and cruft

1. **The random resource pool drops duplicates.** `getValueSortedResources` builds a `TreeSet` ordered only by value (`RawMaterial.java:1592-1599`). `TreeSet.add` discards any element that compares equal, so only the first resource of each value in each material survives. The random picker in `Libraries/RawCMaterial.java:47-58` draws only from that pool. Parsing the table gives 112 of 210 default resources that can never be drawn at random. For example, in Metal only LEAD survives of LEAD, BRONZE, ZINC, COPPER, TIN and NICKEL, which are all worth 10. The count comes from parsing the source, not from running the game (unverified at runtime).
2. **Frequency has two different meanings.** `GatheringSkill` gives a resource `freq` slots (`GatheringSkill.java:195-196`), so frequency 0 is excluded. `StdRoom` gives `freq+1` slots (`StdRoom.java:721-724`, inclusive ranges in `IntegerRangeMap.java:112-120`), so frequency 0 still spawns, and the first resource gets one slot fewer because roll never returns 0 (`Dice.java:398-405`). Both systems read the same field.
3. **Documented ranges disagree with the data.** The frequency parameter is documented as "1-10" (`RawMaterial.java:831`) and "0-10" (`:1632`), but 49 rows exceed 10 and the maximum is 100 (`:617` FISH, `:701` BONE, `:778` DRAGONBLOOD). Hardness is documented as 1-10 (`:832`) and 0-10 (`:1633`), but ULTRANIUM is 11 (`:801`). Buoyancy is documented as 0-10000 (`:833`) and as 0-20000 (`:1468`), but PLATINUM is 21450 (`:650`).
4. **Off-by-one in material name lookup.** `MAT_NAME` and `MAT_DESC` test `mat_code <= Material.values().length` (`RawMaterial.java:1268, 1282`). A code whose material index equals 16 would index past the 16-element enum. This affects only invalid codes.
5. **No cap on resource count.** `CODES.add` computes `allCodes.length | material` (`RawMaterial.java:1642`) with no check. The index occupies the low eight bits (`:577`), so the 257th resource would set the material bits. The defaults use 210 of the 256 slots, leaving 46 for ini additions.
6. **Usual names for ini wear locations can never be set.** Ini rows must have exactly six fields (`Wearable.java:520`), but the code reads an optional seventh field (`:557`). Every ini-added location therefore gets "unusual".
7. **Work thrown away in the wear registry.** The constructor inserts each default location in display order (`Wearable.java:505-509`), then overwrites the order with `allCodesInOrder = Arrays.copyOf(DEFAULT_WORN_ORDER…)` (`:511`). The source comment reads "stupid as it is, I have to fix the worn orders" (`:510`).
8. **Overloads with different meanings.** `Wearable.CODES.NAME(int)` indexes by position (`:898-901`), while `NAME(long)` takes the bit code and searches (`:942-945`, `:963-969`). An int literal binds to the int version, so a caller passing a code can get the wrong name silently. `name(int)` also translates (`:922`) while `name(long)` does not.
9. **Doc and code disagree on `whereCantWear`.** The interface says it returns "a bitmap made up of unwearable location bitmaps" (`Wearable.java:109-119`). The implementation returns a single code, 0, or -1 (`Items/Basic/StdItem.java:491-538`).
10. **Natural weapons are shown as "karate".** `CLASS_DESCS[5]` is "KARATE", but `CLASS_NATURAL` is 5 (`Weapon.java:148`, `:173`). Player-facing text uses the table (`Behaviors/ItemIdentifier.java:145`, `Commands/ListCmd.java:577`), so a natural weapon is described as a karate weapon. The table also lacks a "NATURAL" name, so `CharGen.java:1153` accepts "KARATE" for the class.
11. **Reverse damage map loses information.** `MSG_TYPE_MAP` reverses `TYPE_MSG_MAP` with `SHashtable(..., true)` (`Weapon.java:135`, `core/collections/SHashtable.java:83-99`). Six damage types map to -1, so the reversed map keeps only one -1 key. By HashMap iteration order that is most likely `TYPE_SHOOT` (6), which is inferred and unverified. `StdArmor.java:273` uses this map for non-weapon damage messages.
12. **`getValueOf` contradicts its Javadoc.** The Javadoc says it returns "the clanitemtype found, or null" (`ClanItem.java:126`). In fact an unknown name makes `Enum.valueOf` throw `IllegalArgumentException` (`:137`), and an out-of-range number also falls through to that throw (`:132-135`).
13. **Units disagree for the monster-equipment timer.** The ini says expiry values are minutes (`coffeemud.ini:1169-1173`), and `Expire.getExpirationMilliseconds` multiplies by `MILI_MINUTE` (`ItemPossessor.java:106`). But `StdRace.java:1049` multiplies the same `EXPIRE_MONSTER_EQ` setting by `MILI_HOUR`. With the default of 30, that path gives 30 hours where the enum path gives 30 minutes. Which path applies to which item is unverified.
14. **Duplicate branch in auto-loot.** `MUDFight.java:1650` and `:1653` test the same condition, `killerM.riding() instanceof MOB`. The second branch appears meant to test for an Item (the "disembark" message at `:1654`), so it is unreachable.
15. **Vestigial tick id.** `TICKID_DEADBODY_DECAY` (`Tickable.java:88`) is only referenced by a `deleteTick` call in `Prayer_PreserveBody.java:106`. My search found nothing that starts it. Decay is by expiry date instead.
16. **Overloaded `phyStats().ability()`.** The same integer holds the tech level (`StdTechItem`), the card value and face-up bit (`StdPlayingCard.java:98-107`) and the coin count (`StdCoins.java:44-47`, `:118-124`). Anything else that writes to `ability()` silently changes these. Ammunition type is kept in the readable-text field (`StdWeapon.java:416-420`), another overloaded slot.
17. **Coin value is ambiguous.** `StdCoins.value()` returns the raw count (`StdCoins.java:44-47`), while `getTotalValue()` returns count times denomination (`:153-156`). Whether callers depend on `value()` for coins is unverified.
18. **Range overrides are capped at 127.** The packed range and ammo fields are 7 bits wide (`Weapon.java:182-207`), so overrides above 127 cannot be stored.
19. **Some grid entries depend on the sentinel.** The WAIST row in the dependency grid points at WORN_WAIST itself (`Wearable.java:477`), and LEGS points at FEET (`:478`). The WAIST row is harmless because `StdLimb` skips the slot being checked (`StdLimb.java:103-104`). The -1 "no dependency" values work only because the outer test is `> 0` (`StdLimb.java:97`).
20. **Name collision in materials.** METAL and MITHRIL both have the noun "Metal" (`RawMaterial.java:220-221`).

## Takeaways for a Python SUD

The game is single-player, so drop the multiplayer parts and keep the data models.

1. **Materials and resources as data.** Port `DefResource` to a table (TOML, CSV or a dataclass list). Keep a small `Material` enum and a `Resource` record:

   ```python
   @dataclass(frozen=True)
   class Resource:
       key: str
       material: Material
       value: int        # base currency per unit
       weight: int       # spawn and gather weight; 0 = never
       hardness: int     # crafting difficulty modifier
       density: int      # 1000 x g/cm^3, as in the original
       smell: str = ""
       effects: tuple[str, ...] = ()
   ```

   Draw with `random.choices(pool, weights=[r.weight for r in pool])` over the full pool. Do not sort-and-dedupe by value (Problems, item 1). Keep one meaning for the weight field and use it everywhere.

2. **Body slots as flags with a dependency map.** Use `enum.IntFlag` for slots and a dict of dependencies:

   ```python
   class Slot(IntFlag):
       HEAD = auto(); EYES = auto(); EARS = auto(); MOUTH = auto(); TORSO = auto(); ...

   DEPENDS = {Slot.HEAD: Slot.EYES | Slot.EARS | Slot.MOUTH, ...}
   ```

   A single-player game can fix capacity per slot (for example two wrists and two rings) and skip the body-part derivation. Keep the "cannot remove the head while eyewear is worn" rule, since it produces good messages.

3. **Layering.** Use `layer: int` and `multiwear: bool` on armor. Use a simplified conflict rule: same or adjacent layer conflicts unless both are multiwear. Skip the MOB-side window logic unless the game needs it.

4. **Containers with typed filters.** Give each container an `accepts` IntFlag or a set of item kinds. Use weight as capacity, as the original does. Leave out the bit-name indirection.

5. **Corpses as containers.** Make a `Corpse(Container)` with `victim`, `killer`, `last_message`, `died_at` and `expires_at` in game time, not wall-clock milliseconds. Drop the PK and CORPSEGUARD logic, or reduce it to one "loot allowed" flag.

6. **Light and timers in game time.** Replace tick counts with game-minutes. Keep the rule that rain or water puts a light out, as a hook into weather.

7. **Typed events instead of encoded strings.** Replace `TechCommand` string codecs with dataclasses and direct calls or a small event bus. Keep `TechType` as an enum with display names and `ShipDir.opposite()`. Ship and space systems are optional and can be dropped if SUD has no spaceflight.

8. **Cards.** Use `Card(rank: int, suit: Suit, face_up: bool = False)` with ranks 2 to 14, and `Hand` and `Deck` as containers. Keep the terse string code (suit letter plus rank) for typed commands.

9. **Drop for single-player.** Clan items and `rightfulOwner`, PK flags, reader lists on computers, multi-looter gold splits, siege and caravan navigation, the ammunition-in-readable-text trick, and the Gen-object registry layer.

10. **Fix before borrowing.** The dedupe bug, the `<=` bound, the ini field count, the expiry units, the duplicate auto-loot branch, and the `getValueOf` contract.

## Coverage

**Read in full (13 of 52 files).** `Items/interfaces/RawMaterial.java`, `Wearable.java`, `Item.java`, `Technical.java`, `Weapon.java`, `Container.java`, `Armor.java`, `Electronics.java`, `DeadBody.java`, `HandOfCards.java`, `Software.java`, `ClanItem.java` and `PlayingCard.java`.

**Skimmed (39 of 52 files).** Declarations, constants, enums and class-level Javadoc were read. Method-level Javadoc was not read for most of them. These are `ShipEngine.java`, `ShipDirectional.java`, `SpaceShip.java`, `Book.java`, `MusicalInstrument.java`, `Boardable.java`, `NavigableItem.java`, `Wand.java`, `AmmunitionWeapon.java`, `FuelConsumer.java`, `FalseLimb.java`, `Scroll.java`, `Coins.java`, `CagedAnimal.java`, `Light.java`, `DeckOfCards.java`, `SiegableItem.java`, `ShipWarComponent.java`, `ElecPanel.java`, `TechComponent.java`, `Food.java`, `SpellHolder.java`, `Ammunition.java`, `Perfume.java`, `Computer.java`, `Potion.java`, `PowerGenerator.java`, `RecipesBook.java`, `RoomMap.java`, `DoorKey.java`, `MagicDust.java`, `InnKey.java`, `Pill.java`, `PowerSource.java`, `ArchonOnly.java`, `BodyToken.java`, `MiscMagic.java`, `Shield.java`, `PackagedItems.java`.

**Implementations read (outside the bucket, only as far as the interaction).** `Items/Basic/StdItem.java` (wear methods, `:374-564`), `Items/Basic/StdLimb.java`, `Items/Basic/StdContainer.java` (`canContain`), `Items/Basic/Corpse.java`, `Items/Basic/LightSource.java`, `Items/Basic/StdCoins.java`, `Items/Armor/StdArmor.java` (sizing), `Items/Weapons/StdWeapon.java`, `Items/BasicTech/StdElecItem.java`, `Items/MiscMagic/StdPlayingCard.java`, `MOBS/StdMOB.java` (wear slots and layering), `Races/interfaces/Race.java` (`BODY_WEARGRID`), `Races/StdRace.java`, `Libraries/TimsLibrary.java`, `Libraries/RawCMaterial.java`, `Libraries/MUDFight.java` (looting excerpt), `Locales/StdRoom.java` (`addItem`, resource choice), `Abilities/Common/GatheringSkill.java` (weights), `core/interfaces/ItemPossessor.java` (`Expire`), `core/collections/IntegerRangeMap.java`, `core/collections/SHashtable.java`, `Libraries/Dice.java`, `core/CMProps.java` (defaults) and `coffeemud.ini` (config lines 408-413, 669, 677, 1168-1173).

**Not read.** Gen-object implementers (`Gen*`), the ship simulation in `Areas/StdSpaceShip.java`, the combat text in `CMLib.combat()`, and most ability classes that consume these interfaces. Mentions of them above are from grep hits only.
