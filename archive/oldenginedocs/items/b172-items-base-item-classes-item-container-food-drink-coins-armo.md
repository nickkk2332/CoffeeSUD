# Base item classes: item, container, food, drink, coins, armour, weapon, wand, scroll and potion

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b172` (Items: base item classes (item, container, food, drink, coins, armour, weapon, wand, scroll, potion)) · 11 files · 6594 lines of Java.

## Summary

The item classes in this bucket all descend from `StdItem`, and each adds a thin layer of rules on top of it. Containers add containment, locks and keys; food and drink add consumption; armour and weapons add durability and combat numbers; scrolls, wands and potions add charged spells. Each item keeps a base and an effective stat set that is rebuilt from its effects, and every action on an item is a `CMMsg` that the item can veto before it happens and react to afterwards, so the wear, carry, eat, drink, read and zap rules all live in these classes. The numbers are small and easy to read: container capacity is a weight, food and drink move in fixed bites and sips, one durability counter doubles as charges, and a material-by-damage-type table decides how fast armour and weapons wear. These are the mechanics a single-player text game would most need to re-derive, and most of them port directly.

## Key files and types

Citations in this chapter are relative to `com/planet_ink/coffee_mud/` (shortened to `Items/...`, `Libraries/...` and so on), except `resources/...` paths, which are relative to the CoffeeMud folder.

| File or class | Role | Lines |
|---|---|---|
| `Items/Basic/StdItem.java` | Base class for every item: names, stats, wear, hold, carry and get rules, effects, behaviours, scripts, ticking, copy and destroy | 2386 |
| `Items/Basic/StdContainer.java` | Items that hold other items: weight capacity, type filter, lid, lock, keys, auto-reclose | 783 |
| `Items/Armor/StdArmor.java` | Worn protection: armour value, clothing layers, size deviation, wear by material and damage type, material saves | 746 |
| `Items/Weapons/StdWeapon.java` | Weapons: damage and attack adjustment, enchantment, ranges, ammunition, wear, hit-triggered disease | 527 |
| `Items/MiscMagic/StdScroll.java` | Spell scrolls: spell list in `miscText`, charges, reading cost, expertise, identification | 437 |
| `Items/MiscMagic/StdWand.java` | Single-spell wands with a secret magic word, activated by speaking it | 421 |
| `Items/Basic/StdDrink.java` | Liquid containers and drinkables: sips, fill and pour, mixing, drought | 414 |
| `Items/MiscMagic/StdPotion.java` | One-shot spell potions, cast on drinking or pouring | 307 |
| `Items/Basic/StdCoins.java` | Coin stacks: count in the ability stat, weight by count, currency and denomination | 251 |
| `Items/Basic/StdFood.java` | Food: total nourishment, bite size, hunger, obesity risk | 194 |
| `Items/Basic/GenItem.java` | Data-driven generic item whose fields are stored as an XML blob | 128 |

Inheritance: `StdContainer` extends `StdItem`; `StdArmor` and `StdDrink` extend `StdContainer` (so armour and drinks are containers with capacity 0 unless set otherwise); `StdPotion` extends `StdDrink`; `StdWeapon`, `StdScroll`, `StdWand`, `StdCoins`, `StdFood` and `GenItem` extend `StdItem` directly.

Shared constants, read in the interfaces and not in this bucket: `Items/interfaces/Wearable.java:251-293` (wear location bits), `Items/interfaces/Wearable.java:300-321` (display order), `Items/interfaces/Wearable.java:435-446` (code table), `Items/interfaces/Container.java:127-190` (container type bits and names), `Items/interfaces/Weapon.java:49-207` (damage types, classes, range and ammo bit masks), `Items/interfaces/Armor.java:61-68` (layer flags), `Items/interfaces/Wand.java:50-62` (wand usage abilities).

## How it works

### 1. The item model

Fields are declared at `Items/Basic/StdItem.java:45-76`. The ones that matter for the rules:

- `myWornCode` (line 51) is the location the item currently occupies; `Wearable.IN_INVENTORY` (0) means carried loose (`unWear()` at 564-568).
- `properWornBitmap` (58) is the set of locations the item may occupy, and `wornLogicalAnd` (57) says whether it must take all of them (two-handed style) or any one of them.
- `myUses` (50) defaults to `Integer.MAX_VALUE`. It is durability for armour and weapons, charges for scrolls and wands, and ammunition count for ranged weapons. `setUsesRemaining` is overridden to map `MAX_VALUE` to 100 for armour and weapons (`StdArmor.java:68-73`, `StdWeapon.java:339-344`).
- `miscText` (52) is a catch-all whose meaning depends on the class: a container's key name (`StdContainer.java:630-636`), a scroll's or potion's spell list (`StdScroll.java:73-83`, `StdPotion.java:153-163`), a coin stack's `currency/denomination` (`StdCoins.java:168`, `172-186`), a wand's spell ID (`StdWand.java:146-150`), a weapon's ammunition type (`StdWeapon.java:430-440`). `readableText()` returns `miscText` (`StdItem.java:601-610`), so for scrolls the readable text is the spell list. The write and rewrite paths therefore exclude `Scroll` (`StdItem.java:1573-1575`, `1594-1595`).
- `baseGoldValue` (59) plus `10 * phyStats().ability()` gives `value()` (`StdItem.java:583-586`). Enchantment is therefore worth 10 gold per point on any item before class overrides.
- `material` (60) is a `RawMaterial` code. It drives wear and tear, saves, liquid type, coin material and condition text.
- `owner` (72) is the room or mob that holds the item. `myContainer` (71) is the container it sits in. Containment is a back-pointer, not a list (section 4).
- `basePhyStats` (69) and `phyStats` (68) are the two stat sets. `recoverPhyStats()` (219-227) copies base into effective and then applies every effect's `affectPhyStats`. Subclasses override it: armour adds an "is bonus" disposition and copies a wearer's height into an unsized item (`StdArmor.java:619-628`), weapons fold enchantment into damage and attack (`StdWeapon.java:136-155`).
- `affects`, `behaviors`, `scripts` and `tags` (73-76) are allocated only when used.

Ticking is driven by the item's contents. `StdItem.tick` (748-822) runs the item's behaviours, scripts and effects. Adding the first behaviour or script starts the item's own tick (`StdItem.java:2068-2071`, `2180-2181`). Every `10 + d100` ticks the item runs `CMLib.flags().validCheck` and destroys itself if the check fails (`StdItem.java:753-762`). A tick is 4000 ms by default (`core/CMProps.java:681`), so the validity check comes every 11 to 110 ticks, about 44 to 440 seconds.

Copies and destruction are explicit. `copyOf` (303-320) clones fields, copies behaviours, scripts and effects, and drops any effect that can be uninvoked (`StdItem.java:292-296`); the effect with ID `ItemRejuv` is never copied (288). `destroy` (1773-1839) removes the item from its owner, destroys contents for containers (1799-1826), and clears effects and behaviours. `delAllEffects` also keeps `ItemRejuv` (1992-1996). These are special cases by string ID, which is a recurring pattern in this bucket.

### 2. Messages: veto first, then effect

Every action on an item arrives as a `CMMsg` with a source, a tool, a target and a `targetMinor` code. The flow is two-phase.

1. `okMessage` (`StdItem.java:1072-1627`) asks the item's behaviours, then its scripts, then its effects (1076-1097). Any one can veto by returning false. Then the item's own rules run. When the item is the tool, only throw (too heavy or undroppable, 1105-1119) and pour (must be a `Drink`, 1121-1126) are checked. When the item is not the target, the message passes (1134). Magic aimed at a non-gettable item with no description is refused with "Please don't do that." (1138-1149). Otherwise a switch on `targetMinor` (1151-1624) either allows the action or tells the actor why not. The fall-through is "You can't do that to <T-NAMESELF>." (1625-1626).
2. `executeMsg` (`StdItem.java:1630-1757`) runs behaviours, scripts and effects, then the item's own effect. Throw moves the item to the room (1669-1689). Look, examine, read, hold, wear, wield, get, remove and drop are handed to `CMLib.commands()` handlers (1699-1723). Write and rewrite edit `miscText` (1724-1737). Extinguish removes the "Burning" effect (1738-1749), and a death message destroys the item (1751-1752).

Subclasses call `super.okMessage` first and then add their own cases, so the base veto always applies first. `StdContainer` (`StdContainer.java:100-370`), `StdArmor` (215-239), `StdFood` (104-129), `StdDrink` (217-309), `StdPotion` (245-257) and `StdCoins` (190-226) all follow this pattern.

A get from the floor, as an example. The command builds a `TYP_GET` message. The room offers it to the item's `okMessage`, which checks visibility, riding, level, weight, item count and gettability (`StdItem.java:1359-1407`). If allowed, `executeMsg` hands the move to `CMLib.commands().handleBeingGetted` (1715-1716). The actual move is in that handler, which is outside this bucket and was not traced.

### 3. Wearing, holding and wielding

Locations are bits (`Items/interfaces/Wearable.java:251-291`): HEAD 1, NECK 2, TORSO 4, ARMS 8, left and right wrist 16 and 32, left and right finger 64 and 128, FEET 256, HELD 512, WIELD 1024, HANDS 2048, FLOATING_NEARBY 4096, WAIST 8192, LEGS 16384, EYES 32768, EARS 65536, ABOUT_BODY 131072, MOUTH 262144, BACK 524288. The display order is in `DEFAULT_WORN_ORDER` (300-321), and FLOATING_NEARBY holds up to six items by default (`MOBS/StdMOB.java:5897` onward).

The eligibility rules are short.

- `fitsOn(code)` (`StdItem.java:395-400`) is a bitmask test against `properWornBitmap`.
- `amBeingWornProperly()` (382-392) is true when the item is in one of its allowed locations (OR) or occupies all of them (AND, `wornLogicalAnd`).
- `wearAt` (442-454) writes `myWornCode`. For an AND item it writes the whole bitmap, so a two-handed item takes both hands.
- `whereCantWear` (491-537) returns 0 when some slot that fits has a free position. Otherwise it returns a blocked slot that fits (for an AND item, the first blocked one). `canWear` (540-549) asks `MOB.freeWearPositions`, and `StdArmor.canWear` (100-109) also passes the clothing layer.
- `MOB.freeWearPositions` (`MOBS/StdMOB.java:5881-5893`) subtracts the items already worn at that location from the slot count. The count comes from the body parts in `Race.BODY_WEARGRID` and from `MAXWEARPERLOC` (default 10, `core/CMProps.java:3199`). `MAXITEMSWORN` caps the total.

The wear command chooses the message. `Commands/Wear.java:62-75` sends `MSG_HOLD` for a HELD-only item, `MSG_WIELD` for WIELD or HELD|WIELD, and `MSG_WEAR` otherwise. A location named by the player becomes the message value (`Wear.java:109-111`, `79`). `StdItem` decodes it as:

```java
canWearComplete(mob,(msg.value()<=0)?0:((1L<<msg.value())/2),...)   // StdItem.java:1303
```

This works for the default table, where the code index is the bit position plus one (`Wearable.java:435-445`). The mapping for extra locations added through `ADDWEARLOC_` was not checked.

The hold, wear and wield checks (`StdItem.java:1244-1341`) each refuse in a fixed order: the item cannot go there, the slot is already taken (`alreadyWornMsg`, 1052-1069), the item's level is above the actor's (1258, 1298, 1312, "looks too advanced for you"), and then the slot-specific rule. Holding with full hands tries to remove the held item with `CMLib.commands().postRemove` (1267-1276) and otherwise says "Your hands are full." Missing hands and two-hand restrictions have their own messages (1279-1283).

Clothing layers decide what can be replaced. In `canWearComplete` (`StdItem.java:966-1050`), if the slot is occupied by an item of the same clothing layer, the old item is removed with `postRemove` and the new one goes on (`976-995`). If the layers differ, the actor is told "You are already wearing X on your Y." (984-985). Held and wielded slots follow the same rule with "already holding" or "already wielding" messages (`1011-1019`). Removing an item underneath a higher-layer item is refused with "You must remove X first." (`1448-1461`). Layers are a `short`, and `StdArmor` stores them in `layer` (46, 76-85), with `layerAttributes` flags `LAYERMASK_SEETHROUGH` (1) and `LAYERMASK_MULTIWEAR` (2) from `Items/interfaces/Armor.java:61-68`.

Carrying limits apply on get and throw.

- Level gate on get: refused if `mob.level < item.level - (10 + mob.level/5)`, with the message "is too powerful to endure possessing it." (`StdItem.java:1374-1381`). `StdContainer.java:213-220` applies the same formula to items taken from a container, except for shopkeepers and leveless races and classes.
- Weight gate on get: refused if the item's weight is more than `maxCarry - mob.weight` (`StdItem.java:1382-1388`). Containers use `recursiveWeight()` (`StdContainer.java:222-226`).
- Count gate: refused if the item's `numberOfItems()` exceeds `maxItems - numItems` (`StdItem.java:1389-1393`).
- Throw gate: refused if weight is above `maxCarry/5` (`StdItem.java:1108-1110`).

### 4. Containers

Contents are not stored in the container. Every item sits in its owner's flat item list, and an item is inside a container when `container()` points at it. `getContents` (`StdContainer.java:750-765`), `getDeepContents` (729-747) and `hasContent` (768-782) all scan the owner's `items()` and compare `container()`. Destroying a container walks the owner's list to find its contents (`StdItem.java:1799-1809`, `1817-1826`). Each containment query is therefore linear in the owner's inventory. This is simple and uniform, and it is why a room and a mob can both hold containers with no special case.

Capacity is a weight, not a count.

- An item is refused if its weight is above the container's `capacity` ("won't fit", `StdContainer.java:163-167`).
- An item is refused if `recursiveWeight() + weight > capacity`. The message is "is too small." when the container is empty and "is full." otherwise (169-179). A `Software` item put in a `Computer` gets "is out of memory." (174-175).
- `recursiveWeight` (698-726) sums the weights of the owner's items whose `ultimateContainer` is this container, so nested items count.
- `numberOfItems` is the deep contents plus one (692-695), and that is what the carry-count gate sees.
- The default capacity is 25 (`StdContainer.java:59`).

Contents are typed by a bitmask. `containType` of 0 accepts anything (`canContain`, 482-485). Otherwise each set bit is tested by a predicate (486-580):

- liquid by material class (493),
- coins by class (497),
- swords and daggers by `weaponClassification` (501-509),
- keys (511), drinkables (515), eatables (519), readables (566), scrolls (570), caged animals (562), bodies (553), smokeables by material (557-558), rawmaterials (538), SS components (574),
- clothes, which must fit a body location (523-529), footwear (533-534),
- other weapons (542-545) and one-handed weapons (548-550).

The container type names are in `Items/interfaces/Container.java:172-191`. One bit has no predicate: `CONTAIN_SHIELDS` (262144, `Container.java:165`, index 19 of `CONTAIN_DESCS`) appears in no `case` of `canContain`, and nothing else in the code base references it. A container whose only type is shields accepts nothing.

Putting an item in is checked on the container (`okMessage`, `StdContainer.java:117-192`). The item must be droppable and carried loose, and a closed container refuses it ("is closed.", line 128). If the item is in the actor's hands or worn, the messages are "You are holding that!" and "You are wearing that!" (`131-149`). If the actor carries the item and not the container, a `MSG_DROP` is sent first (`180-188`). The move itself is `executeMsg` (`StdContainer.java:400-411`), which calls `setContainer(this)`.

Doors and locks are fields (`StdContainer.java:43-50`): `hasALid`, `isOpen`, `hasALock`, `isLocked`, `defaultsClosed` and `defaultsLocked`. `setDoorsNLocks` (620-628) sets them together and forces `defaultsLocked` false unless the container has a lock and defaults to closed.

- Opening a container on a room floor that is not gettable starts a reopen countdown of `openDelayTicks` (default 30, line 51) at `StdContainer.java:420-424`. At 4000 ms per tick that is 120 seconds.
- `tick` with `TICKID_EXIT_REOPEN` (372-388) restores the default state. It defers while a `DoorKey` whose key matches the container's key name is inside (377-385). A key left in the box keeps the box open.
- A lock or unlock needs a `DoorKey` in the actor's inventory whose key matches `keyName()` (`StdContainer.java:344-359`). A key inside a container with the `CONTAIN_KEYS` bit also counts.
- Looking into a closed container is refused with "is not open!" (`StdItem.java:1166-1183`).

### 5. Food and drink

Food (`Items/Basic/StdFood.java`) has two numbers: `amountOfNourishment` (500, line 43) is the total, and `nourishmentPerBite` (0, line 44) is the bite size. A bite of 0 or of more than the remaining total eats everything (`StdFood.java:155-157`). Each bite lowers nourishment (158) and raises hunger through `adjHunger` (159). The hunger function multiplies by the gain percentage and clamps at the mob's maximum, returning false when full (`Common/DefaultCharState.java:185-210`). The messages are "You are no longer hungry." (160-161) and "You are full." (162-164). The food is destroyed when nourishment reaches zero (165-166).

Overeating has a small risk. When the mob is already at maximum hunger, a `d100` roll of 1 (about 1 percent per bite) invokes `Disease_Obesity` unless the mob is a golem or already has the effect (`StdFood.java:141-154`). Food handled as the tool of a get into a container gives that container a `Soiled` effect with `DIRTY=TRUE` if the food is not a raw material or is flesh (`StdFood.java:175-191`).

Drink (`Items/Basic/StdDrink.java`) is a container of liquid. It extends `StdContainer` with `containType` set to `CONTAIN_LIQUID` (`StdDrink.java:58`), and it holds the liquid as a count.

- Sip size is `amountOfThirstQuenched` = 250 (line 43). Each sip subtracts 250 from `amountOfLiquidRemaining` (332, and 404 for a drink used as a tool) and raises thirst (334). "You are no longer thirsty." and "You have drunk all you can." are the two messages (335-339).
- Capacity is `amountOfLiquidHeld` = 2000 (line 44) and the default content is 2000 (line 45). A source with capacity of 500000 or more is treated as infinite (`StdDrink.java:246-247`, `315-316`).
- The drink gate (`StdDrink.java:226-242`) lets the holder drink from anything they carry, from anything over 1000 weight, and from anything fixed in place. Empty drinks are refused ("is empty."). Saltwater and lamp oil are refused ("You don't want to be drinking").
- Filling takes `min(capacity - contents, source remaining)` (`amountTakenToFillMe`, 312-320). Filling a drink that holds a different liquid is refused with "There is still some X left in Y. You must empty it before you can fill it with Z." (`291-297`). When a puddle, which is itself a raw-material drink item, fills an empty container, a copy of the liquid is placed inside the container as an item (`StdDrink.java:350-376`). So liquids can be contents too.
- Outdoor, non-gettable drinks have no water in drought weather (`containsLiquid`, 196-201).
- A drink or puddle with zero remaining is destroyed if it disappears after drinking or is raw material (340-342, 385-391).

### 6. Coins

Coins (`Items/Basic/StdCoins.java`) are a stack. The count lives in `phyStats().ability()` (`getNumberOfCoins`, 112-115; `setNumberOfCoins` caps at `Integer.MAX_VALUE`, 118-125). The denomination and currency are kept in `miscText` as `currency/denomination` (149, 168, 172-186). The name and description come from `CMLib.beanCounter()` (81-88, 108).

- Weight is `round(count / 100)` for any material except cloth and paper (`StdCoins.java:231-234`). 250 coins weigh 3, and 49 coins weigh 0. A zero weight is skipped by several gates, for example `StdItem.java:1108` and `1384`.
- `value()` returns the coin count itself (44-47). The real total is `getTotalValue()` (152-156), which multiplies by the denomination. Whether callers use `value()` for money was not checked.
- The material comes from the name. Names containing "note", "bill" or "dollar" become paper, and otherwise a `RawMaterial` code name found in the name is used (`setDynamicMaterial`, 90-109).
- Dropping, giving or putting a currency that is not tradeable is refused unless the actor has the `CMDPLAYERS` security flag (190-226).
- `autoBundle` (245-250) merges the stack back into the owner's coins through `beanCounter().putCoinsBack`.

### 7. Armour

Armour (`Items/Armor/StdArmor.java`) is a container with capacity 0 (line 61). Its defaults are a torso slot (56), armour value 10 (58), gold 150 (60) and durability 100 (63).

Protection is an "armour" stat where lower is better. `affectPhyStats` (631-653) subtracts the item's armour from the wearer's stat when the item is worn, and not when it is carried, floating or held (unless it is a shield) (635-637). The wearer's stat is later turned into a displayed value by a formula, and the hit roll uses the adjusted armour value. `Libraries/MUDFight.java:409-423` uses the armour as `myArmor = -defence`, and `armorStr` (`MUDFight.java:1899-1904`) negates it for display. The default adjustment formula is at `core/CMProps.java:3203`. A default shirt worn therefore takes 10 off the wearer's armour value.

Enchantment adds to protection for worn items (`StdArmor.java:640-650`):

- on the torso, the ability times 5 is taken off (644),
- on the head or held, the ability times 2 (646-647),
- elsewhere, the ability once (649-650),
- for an item that takes several slots, the ability is multiplied by the number of set bits (642).

For example, a +3 shirt worn on the torso takes 10 + 15 = 25 off the wearer's armour value. Multi-wear layers do not get the enchantment bonus (640).

Material gives saving throws, applied only when worn (`affectCharStats`, 656-693). Metal lowers the electric save by 10 (663-664). Leather raises the acid save by 10 (667-668). Mithril raises the magic save by 10 (671-672). Cloth and paper lower the fire save by 10 (676-677). Glass, rock, precious, vegetation, flesh and synthetic raise the fire save by 10 (680-686). Energy raises the paralysis save by 10 (689-690).

Size is checked when the item is worn (`getSizingDeviation`, 170-212, and `okMessage` 219-237). The allowance is by location.

- Torso, legs, waist, arms, hands and feet allow 20 height units.
- Eyes and ears allow 1000, and neck allows 5000.
- Floating and mouth are not checked.
- Everything else allows 200.

Too large gives "doesn't fit you -- it's too big." and too small gives "doesn't fit you -- it's too small." Items with zero height are not checked (175). `recoverPhyStats` (624-627) copies the wearer's height into an unsized worn item, so an unsized armour becomes sized to its first wearer.

Wear and tear happens on damage taken while worn (`executeMsg`, 246-265). An item is at risk when the wearer takes damage, the damage is not poison or disease, and two rolls both succeed: `d100 > level/2 + 10*ability` (plus 20 for a bonus item) and `d100 > wearer's dexterity` (264-265). The effect then depends on material and damage type. The table in the content section lists it. Losses are in durability points, and the chance is per hit.

- Below 10 points the wearer is warned that the item is nearly destroyed (585-590).
- At zero or fewer, the item is unworn, its contents are emptied, it is destroyed, and a trailer message says so (592-608). The counter is reset to 100 before destruction (597), which has no effect on the result.
- The condition text shown on look is one of seven steps at 95, 75, 50, 25, 10, 5 and 0 percent (`armorHealth`, 111-152), chosen by material group from the lists in `resources/lists.ini:714-779`.
- A bashing hit on worn head armour made of metal, with more than 10 damage, has two 1 percent rolls. Both succeed one time in 10,000, and then the wearer gets `Disease_Tinnitus` (426-435).

The value is scaled by durability below 1000 (`value`, 696-701), and an item is subject to wear only when durability is between 0 and 1000 (704-709). The identify text shows the name with its enchantment, the power level from `CMLib.itemBuilder().timsLevelCalculator`, and "Base Protection" as the armour the item gives while worn (`secretIdentity`, 712-745).

### 8. Weapons

Weapons (`Items/Weapons/StdWeapon.java`) take `weaponDamageType` (seventeen types from `Items/interfaces/Weapon.java:49-81`, from natural to scraping) and `weaponClassification` (twelve classes, `Weapon.java:138-160`, from axe to thrown). The defaults are natural type and class, steel, weight 1, durability 100 (65), gold 15 (63), and a location of HELD or WIELD (59).

Stats work like this.

- A wielded weapon adds its attack adjustment and damage to the wielder (`affectPhyStats`, `StdWeapon.java:120-126`).
- Enchantment is folded into the weapon's own stats (`recoverPhyStats`, 136-147). Damage goes up by the ability, and attack adjustment goes up by 10 times the ability. The attack bonus is applied only when the base damage is not zero (143-146). A +2 weapon with base damage 5 has damage 7 and gets +20 attack.
- Damage scales with durability below 100 when the damage is positive (153-154).

Combat uses these values in `Libraries/MUDFight.java`. The hit roll uses the wielder's attack adjustment through `adjustedAttackBonus` (216-240), and the damage uses the weapon's `phyStats().damage()` through `adjustedDamage` (915 onward). These were read only in part. Ranged and thrown weapons use the weapon's own damage (`MUDFight.java:920-925`).

Hits can have side effects (`executeMsg`, 250-295). These apply only when the weapon is wielded and the target is a mob.

- Disease: when the hit is more than a tenth of the target's maximum hit points or more than 50, and does not kill, a 1 percent roll must succeed, followed by a second roll above four times the target's constitution, against a non-monster target (`StdWeapon.java:263-268`). For metal weapons with less than 25 durability, the disease is Lockjaw with a 50 percent chance and Tetanus otherwise (`StdWeapon.java:272-281`). The Tetanus branch never fires; see Problems.
- Wear: a `d100` of 1 at range 0, with a roll above `level/2 + 10*ability` (plus 20 for a bonus item), and not for energy or gas, costs one durability point through `CMLib.combat().postItemDamage` (286-294; `MUDFight.java:255-285`).

Weapons have no material table. They lose durability by the per-hit rule above, and weapons that need ammunition or are wands are not subject to wear (`subjectToWearAndTear`, 505-511).

Ammunition is a second use of the durability counter (`StdWeapon.java:416-421`). A weapon needs ammunition when its `readableText` holds an ammo type and it is not a wand. Its durability is then the rounds left.

- Each attack with ammunition left costs one round (`okMessage`, 304-334, the decrement at 333).
- With no rounds, the attack is refused with "@x1 is out of @x2." (318), and when the holder is a live mobile the weapon asks for a reload with `MSG_NEEDRELOAD` (319-328).
- A reload (`StdWeapon.java:181-218`) moves up to the capacity from an ammunition item. It also copies the ammunition's savable effects onto the weapon as non-savable effects (202-213), which is how a poisoned or enchanted arrow works. An empty ammunition item is destroyed (214-215).
- An unload (220-240) moves the non-savable effects back onto the ammunition and drops it in the room.

Ranges and ammunition capacity can be packed into the weapon's armour stat. `Items/interfaces/Weapon.java:183-207` defines the masks: maximum range in bits 0 to 6 with flag `0x80`, minimum range in bits 8 to 14 with flag `0x8000`, ammunition capacity in bits 16 to 22 with flag `0x800000`, and `0x80000000` to disable the flags. `minRange` and `maxRange` (`StdWeapon.java:388-401`) and `ammunitionCapacity` (476-482) read these bits and fall back to the `ranges` array and `ammoCapacity` field (47-48).

A thrown weapon adds a `MSG_DROP` trailer to its own attack, so it lands on the floor after the throw (`StdWeapon.java:245-248`).

The condition text on look has eight steps at 95, 85, 75, 50, 25, 10, 5 and 0 percent (`weaponHealth`, 346-373). Edged weapons (axe, dagger, edged, polearm, sword) use one list and the rest use another (`resources/lists.ini:686-713`).

### 9. Charged magic items

**Scrolls** (`Items/MiscMagic/StdScroll.java`) hold a spell list and charges. The list is stored in `miscText` as `ID(params);ID(params)` (`buildSpellsList`, 102-139), and parsed into ability copies on each change (73-78). Spells whose domain is Archon are dropped (131). Charges are `usesRemaining`, which defaults to 0 (59). Reading a scroll uses one charge (301). A scroll with no charges has value 0 (`value`, 159-164). Otherwise the value is `200 + 100 * sum(lowestQualifyingLevel)` over its spells (`calculateValue`, 141-150).

Reading works in this order.

1. The reader must carry the scroll, and the scroll must be identified for them (227-257). A `ScrollUsage` ability the reader has, whose read-magic type matches one of the spells, identifies the scroll with "@x1 glows softly." (247-251). The identification is stored as the reader's name in `readableScrollBy` (251). The expertise level of that ability is added to the casting level (233-250).
2. Without identification, the reader gets "The markings look magical, and are unknown to you." (321-322).
3. The reader names a spell. The lookup tries the whole string, then drops trailing words into parameters (270-291).
4. The mana check (`useTheScroll`, 167-187) costs 0 mana when the reader's qualifying level is below their class level, 5 when they qualify, and 25 when they do not. Too little mana gives "You don't have enough mana." (180-183).
5. The spell is invoked at the scroll's level plus expertise, but not below the spell's lowest qualifying level (296-300), and one charge is spent (301).

Without a spell name, a player sees the spell list with levels, "Level NN: name" (309-316). The identify text is `A scroll of X and Y, Charges: N` (`makeSecretIdentity`, 190-222), which `StdPotion` reuses.

**Wands** (`Items/MiscMagic/StdWand.java`) hold one spell. Its ID is the `miscText` (146-152), and `getSpell` returns the class prototype (162-167). Each spell has a magic word from `MAGIC_WORDS` in `resources/lists.ini:1066-1764`, chosen by hashing the spell ID (`getWandWord`, 137-143). The list has 699 uppercase words, so the same spell always gets the same word. Charges are `usesRemaining`, default 0 (58). `getMaxCharges` always returns `Integer.MAX_VALUE` and `setMaxCharges` does nothing (83-91).

Use is by speech. A holder who says something containing the magic word (case-insensitive, `checkWave`, 216-222) triggers a `WAND_USE` trailer on the speech (`StdWand.java:310-329`), and the rest of the words become parameters (232-238). The wand must be held or wielded properly (`amBeingWornProperly`, 220) and not in the inventory (226).

- The holder needs a `WandUsage` ability (`Items/interfaces/Wand.java:50-62`). `getEnchantType` is -1 for every wand (344-347), so any usage ability qualifies. The code keeps the last one it finds, unless one matches the spell's school (241-258). That is effectively "any", and the name `pickFirstOne` is misleading.
- A `proficiencyCheck` on that ability decides success. Failure gives "@x1 glows faintly for a moment, then fades." and costs nothing (261-262).
- An empty wand gives "@x1 seems spent." (268-269).
- Mana (`useTheWand`, 103-126) starts at 25 for a non-qualifying spell, 5 for a qualifying one and 0 when the qualifying level is below the class level. It then subtracts 2 times the usage ability's `abilityCode()` and floors at 5 (116-118). So every zap costs at least 5 mana.
- On success the wand "glows brightly." (280), spends one charge (281), and invokes the spell at level `wand level + min(ability, 10) + expertise`, not below the spell's lowest qualifying level (282-288).

The wand's identify text (`secretIdentity`, 170-202) names it "A wand of X", "A relic of X" for prayers or "A shard of X" for chants, shows charges as `N/MAX` or `N`, shows `+ability` between 1 and 9 (184), and gives the magic word. Its value is 0 when spent and has a spell (`value`, 129-135).

**Potions** (`Items/MiscMagic/StdPotion.java`) are one-capacity glass liquid containers (53-57). They use the same spell-list encoding and the same value formula, duplicated in the class (182-230). Drinking (`drinkIfAble`, 117-150) creates a factory caster in the room (124), sets the caster's level to `max(potion level, lowest qualifying level)` (133-138), and invokes each spell on the drinker or on the item being poured on (139). The caster is destroyed unless an effect that was created names it as invoker (140-147). The potion then vanishes ("@x1 vanishes!", 271-273). Potions cost no mana. A potion whose list contains `;DRUNK` is spent and worth 0 (`isDrunk`, 72-76; `value`, 90-95; `setDrunk`, 98-114).

### 10. Interactions with other systems

- **Combat** (`Libraries/MUDFight.java`) reads `phyStats().attackAdjustment()` for the hit roll (216-240), `phyStats().armor()` for the defence (348-373, 409-423), and `phyStats().damage()` for damage (915 onward). Weapon wear on a hit goes through `postItemDamage` (255-285), which subtracts durability and destroys the item at zero, with the same reset-to-100 pattern. Armour wear is applied inside its own `executeMsg` (`StdArmor.java:246-608`) and does not use `postItemDamage`.
- **Commands** (`Commands/Wear.java`, and the `CMLib.commands()` get, drop, remove and hold handlers) do the real moves. `StdItem` decides and `CMLib.commands()` performs.
- **Mobs** supply `freeWearPositions` (`MOBS/StdMOB.java:5881-5893`), `maxCarry`, `maxItems`, `numItems`, `curState` hunger, thirst and mana, and `maxState` limits (`Common/DefaultCharState.java:185-275`).
- **Abilities** supply spell lookup (`CMClass.getAbility`), invocation, `lowestQualifyingLevel` and `qualifyingLevel` from `AbleMapper`, `proficiencyCheck`, expertise levels, and the `DOMAIN_ARCHON` exclusion. These were not traced.
- **Effects and diseases** are named by string ID: `Disease_Tinnitus`, `Disease_Lockjaw`, `Disease_Obesity` (present as `Abilities/Diseases/*.java`), `Burning` (extinguish, `StdItem.java:1528`), `Soiled` (`StdFood.java:184`), `ItemRejuv` (`StdItem.java:288`, `1992`). The Tetanus name does not resolve (see Problems).
- **Configuration** sets `TIME_TICK` (4000, `CMProps.java:681`), `MAXWEARPERLOC` (10, `CMProps.java:3199`), `FORMULA_ARMORADJUSTMENT` (`CMProps.java:3203`), and the hunger and thirst gain percentages used by `DefaultCharState`.

## Content worth mining

**Wear locations** (`Items/interfaces/Wearable.java:300-321`). In display order: head, eyes, ears, mouth, neck, back, about body, torso, arms, left wrist, right wrist, hands, left finger, right finger, waist, legs, feet, floating nearby, wield, held. Each location has a description, a weight table (`Wearable.java:327-382`) and a dependency grid (`Wearable.java:462-480`), and the grid says which locations block which.

**Container types** (`Items/interfaces/Container.java:172-191`): anything, liquid, coins, swords, daggers, other weapons, one-handed weapons, bodies, readables, scrolls, caged animals, keys, drinkables, clothes, smokeables, SS components, footwear, raw materials, eatables, shields. The predicates are in `StdContainer.java:486-580`.

**Armour wear by material and damage type** (`Items/Armor/StdArmor.java:282-571`). Each row is a material group. "None" means the damage type does nothing to that material. Chance is per hit, and loss is durability points or the dice shown.

| Material | No loss | Chance and loss |
|---|---|---|
| Cloth, paper | bursting, frosting, gassing, stultifying, corrupting, bashing | 25%: striking (1d5), melting, burning, disrupting (1d15); 75%: lasering (1d75); 5%: piercing, shoot, slashing (2); 1%: natural, sonicing, scraping (1) |
| Glass | bursting, gassing, melting, burning, lasering, corrupting, scraping | 75%: sonicing, disrupting (1d75); 10%: bashing, natural (1d10); 5%: striking, frosting, stultifying (1d20); 5%: piercing, shoot, slashing (1d15) |
| Leather | bursting, frosting, gassing | 50%: lasering, disrupting (1d50); 25%: striking, corrupting (1d5); 25%: melting, burning (1d15); 10%: piercing, shoot (1d4); 5%: bashing, natural, sonicing, stultifying, scraping, slashing (1) |
| Metal | bursting, frosting, gassing, scraping, stultifying, corrupting | 35%: lasering, disrupting (1d35); 25%: melting (1d15); 5%: bashing, striking, natural, sonicing (2); 2%: piercing, shoot, slashing (1); 1%: burning (1) |
| Mithril | Gated by a 1% roll, then the metal row | As metal |
| Synthetic | frosting, gassing, corrupting, slashing | 20%: lasering, disrupting (1d25); 5%: bursting, melting, scraping, stultifying (1d10); 5%: bashing, striking, natural (1d4); 5%: piercing, shoot (1d8); 1%: burning, sonicing (1) |
| Rock, precious | bursting, frosting, gassing, stultifying, corrupting | 50%: lasering, scraping (1d50, not precious); 35%: sonicing, disrupting (1d35); 5%: melting (1d5); 5%: bashing, striking, natural (2); 2%: piercing, shoot, slashing (1); 1%: burning (1) |
| Wood | bursting, frosting, gassing, stultifying | 50%: lasering, disrupting, corrupting (1d50); 20%: striking (1); 20%: melting, burning, scraping (1d5); 5%: bashing, natural, sonicing (2); 2%: piercing, shoot, slashing (1) |
| Energy, gas | Everything | none |
| Any other material | none | 1%: any damage type (1) |

**Armour condition text** (`resources/lists.ini:714-779`). Metal, from light to ruined: "has some small dents", "is dented", "is dented and cracked", "is badly dented and cracked", "is a crumpled mess", "is almost destroyed". Cloth runs "is slightly worn", "has tears and rips", "has numerous tears and rips", "is shredded and ripped", "is a shredded mess", "is just a few threads". Each text takes the item name and the percentage. Glass, leather and wooden lists follow the same seven-step shape.

**Weapon condition text** (`resources/lists.ini:686-712`). Edged: "looks slightly used", "is somewhat dull", "is dull", "has some notches" (plus a second word for small cracks), "is heavily damaged", "is so damaged that it is practically harmless", "is nearly destroyed and completely harmless".

**Magic words** (`resources/lists.ini:1066-1764`). 699 uppercase, nonsense words, each assigned to a spell by hash. They are a cheap, memorable password mechanic that needs no extra data.

**Spell scroll and potion naming**. Scrolls read "A scroll of X and Y", potions "A potion of X and Y", and wands "A wand of X", "A relic of X" or "A shard of X" by school.

**Liquids** use the `RawMaterial` liquid codes, with freshwater as the default. Saltwater and lamp oil are refused for drinking.

**Coin weights and currencies** are a stack of integers with a currency name in `miscText`, and the name is generated by the bean counter.

## Notable design ideas

- **One message bus for every verb.** A wear, a drink or a zap is the same kind of object, a `CMMsg` offered to a veto chain and then applied. Adding a new rule means overriding one method. Behaviours, scripts and effects can each veto a message, so content can add rules without touching item code.
- **Containment as back-pointers.** Items are not stored in their containers. They stay in the owner's list and point at their container. A single list handles rooms, mobs and containers, and every item is equally visible to commands.
- **Capacity by weight and a type mask.** A bag is "full" when its contents' weight plus the new item exceeds its limit, and it only accepts types whose bit is set. This gives "a quiver for arrows" and "a belt for coins" without new classes.
- **Clothing layers as a number.** Same-layer items swap and different-layer items refuse. Removal of an item under a higher layer is blocked with a clear message. It is a one-line rule that gives a sensible wardrobe.
- **One counter, three meanings.** `usesRemaining` is durability for armour and weapons, charges for scrolls and wands, and rounds for ammunition. The same field and the same clamping code serve all of them.
- **Material by damage matrix.** A table of material against damage type gives each substance a character. Cloth shrugs off freezing and corrosion, glass shatters on sonic and disrupting damage, leather takes lasers hard. The numbers are data-like and easy to tune.
- **Speech-activated wands.** A holder says the magic word, and the speech itself triggers the zap as a trailing message. This turns a command into dialogue, which suits a text game.
- **Deterministic magic words.** The word for a wand is the hash of its spell ID, so the same spell always gets the same word, and nothing has to be stored.
- **Identification by expertise.** A reader's skill can identify a scroll, and that skill's expertise is added to the casting level. Learning pays off twice.
- **Liquids as stacks.** Drinks, puddles and fills use a volume model with mixing rules and drought. A puddle that fills a container becomes an item inside it.
- **Potions cast through a factory caster.** The caster is created for the moment and destroyed afterwards, unless an effect needs it as invoker. The effect keeps the caster alive only when it must.
- **Identify text shows the protection value.** The "Base Protection" line re-runs the armour's own stat calculation with the item worn, so the player sees the real number.

## Problems and cruft

Bugs found by reading the source. Each one is visible in the code cited; none was run, so each is a reading-based finding.

- **`Items/Basic/StdItem.java:1065`**: the message text is `@x1is already being worn.`, which is missing a space, so the item name runs straight into "is".
- **`Items/Basic/StdItem.java:1558-1559`**: in the `TYP_ADVANCE` case the condition is `(this instanceof Boardable) || ((this instanceof Rideable) || (((Rideable)this).rideBasis() == ...))`. For an item that is neither boardable nor rideable, the second `||` branch casts to `Rideable` and throws `ClassCastException`. The senders found address a creature or a room (`Abilities/Fighter/Fighter_Charge.java:199`, `Abilities/Fighter/Fighter_Jousting.java:249`, `Abilities/Skills/Skill_HardToStern.java:236`), not a plain item, so the path is probably latent. Whether any sender can target a plain item was not confirmed.
- **`Items/Weapons/StdWeapon.java:279`**: the tetanus branch asks for `CMClass.getAbility("Disease_Tetanus")`, but the class is `Abilities/Diseases/Disease_Tetnus.java`, with `ID()` returning `"Disease_Tetnus"` (lines 35 and 40). A class lookup by the requested ID finds nothing, and the `A!=null` guard at line 280 hides the failure. The lockjaw branch works. The index of class IDs has no entry for `Disease_Tetanus`.
- **`Items/Basic/StdContainer.java:486-580`**: `canContain` has no case for `CONTAIN_SHIELDS` (`Container.java:165`), so a container whose only type is shields accepts nothing. Nothing else references the bit.
- **`Items/Basic/StdDrink.java:332`**: a sip subtracts 250 from `amountOfLiquidRemaining` with no clamp. The check in `okMessage` (`containsLiquid`, line 202) only requires at least one unit, so the value can go negative. Its effect on contents was not traced.
- **`Items/Basic/StdDrink.java:335-336`** says "You are no longer thirsty." whenever the drinker started thirsty. `StdFood.java:160-161` also checks that hunger is still above zero after the bite. The two messages are inconsistent.
- **`Items/Basic/StdItem.java:1768`** sets `destroyed=true` in `stopTicking` with the comment "WHY?!?!?". It is a visible admission that the flag's purpose is unknown.
- **`Items/Basic/StdContainer.java:349` and `382`** compare key names with `equals` in one place and `equalsIgnoreCase` in the other.
- **`Items/Armor/StdArmor.java:45`** declares `int sheath`, which is never read in this bucket. `Items/Armor/StdThinArmor.java:42` declares it again.
- **`Items/Weapons/StdWeapon.java:143-146`**: the enchantment's attack bonus applies only when base damage is not zero, so a pure to-hit enchantment on a zero-damage item does nothing.
- **`Items/Basic/StdCoins.java:44-47`**: `value()` returns the coin count, not the count times the denomination. `getTotalValue()` does the right thing. Callers were not checked.

Hacks and cruft.

- **Packed bit fields in the armour stat** (`Items/interfaces/Weapon.java:183-207`, `StdWeapon.java:388-401`, `476-482`). Weapon range and ammunition capacity are stored as bits of `phyStats().armor()`. A weapon's displayed armour can therefore contain range data.
- **Magic numbers and string IDs.** Extinguish checks `abilityCode() & 2048` with the comment "yes, magic numbers suck" (`StdItem.java:1529`). Effects are found and special-cased by string ID (`Burning`, `Soiled`, `ItemRejuv`).
- **Duplicated code.** `StdPotion` repeats the spell-list builder, the value calculation and the `spells` field from `StdScroll` (`StdPotion.java:182-230` against `StdScroll.java:102-150`). `StdScroll` and `StdPotion` copy `recoverPhyStats` logic from the shared base.
- **Misleading name.** `pickFirstOne` in `StdWand.java:241` is true for every wand, so it means "any usage ability", not "the first one".
- **Wear-slot decoding by shift** (`StdItem.java:1303`). The code assumes the location index is the bit position plus one. That holds for the default table and was not verified for `ADDWEARLOC_` extras.
- **Height adoption** (`StdArmor.java:624-627`). An unsized armour takes the height of its first wearer and keeps it, which can make later sizing checks surprising.
- **Armour destruction resets the counter** to 100 before destroying it (`StdArmor.java:597`, `MUDFight.java:274`). The reset is harmless but pointless.

Multiplayer assumptions. These are not needed for a single-player game.

- **Identification by name.** `StdScroll` stores the identifying reader's name in `readableScrollBy` (`StdScroll.java:251`) and compares it with `mob.Name()` (257, 359-362). Identity is per character name, which only works with several characters.
- **Trade and admin flags.** Coins check `CMSecurity.SecFlag.CMDPLAYERS` before refusing non-tradeable currency (`StdCoins.java:200-201`, `214-215`).
- **Other players.** Messages split into "source", "target" and "others" text (`msg.othersMessage()` in many places, for example `StdItem.java:1270`, `1324`). Single-player games lose the "others" channel.
- **Player versus monster.** The weapon disease is gated on `!tmob.isMonster()` (`StdWeapon.java:266`), and the scroll list is shown only to non-monsters (`StdScroll.java:307`). Single-player has only one player, so this distinction matters only for NPCs.
- **PVP formulas** in `MUDFight.java:928` onward choose separate damage formulas for player versus player. Single-player does not need them.

## Takeaways for a Python SUD

**Borrow the data model, not the class hierarchy.** A single `Item` record with composition for the optional parts would cover all ten classes. Keep the fields this bucket uses: a weight, a base value, a level, a material key, a set of wear slots with an AND or OR flag, a durability or charges counter, a worn location, a clothing layer, an enchantment number, a spell list, and optional container contents with a capacity and a type set. Sketch, untested:

```python
from dataclasses import dataclass, field
from enum import Flag, auto

class Slot(Flag):
    HEAD = auto(); TORSO = auto(); ARMS = auto(); FEET = auto()
    HELD = auto(); WIELD = auto()

@dataclass
class Item:
    name: str
    material: str                        # key into a WEAR_TABLE
    weight: int = 1
    base_value: int = 0
    level: int = 1
    slots: Slot = Slot.HELD              # any one of these (OR)
    two_handed: bool = False             # takes all of slots (AND)
    uses: int | None = None              # durability or charges; None = indestructible
    worn: Slot | None = None             # None = carried loose
    layer: int = 0                       # same layer swaps, different layer refuses
    enchant: int = 0
    spells: list[str] = field(default_factory=list)   # e.g. "Fireball(3)"
    contents: list["Item"] | None = None              # containers only
    capacity: int = 0                    # total weight allowed inside
    accepts: set[str] | None = None      # container categories; None = anything
```

**Keep these rules, because they are cheap and make the game feel right.**

- Weight-based containers with a category filter, and a clear "too small" or "full" message.
- Clothing layers as integers, with same-layer swap and different-layer refusal.
- The level gate on taking an item, in simplified form, as a flavour rule.
- The material by damage table, as a dictionary of `(chance, dice)` pairs, one row per material. It is data, so it can be tuned without code.
- One counter for durability and charges, with a warning at 10 percent.
- The scroll value formula `200 + 100 * level`, and charge-based wands with a fixed mana-free rule or a simple energy pool.
- Speech-activated wands: a player types the magic word in a command, and the wand fires. This is a good fit for a text game and needs only a word-to-spell map, which can be generated from a hash of the spell ID.
- Liquids as volume with a liquid type, sips and fills in fixed units, and a rule that you cannot mix liquids.
- Food as total nourishment with bites, and the obesity risk as a small random event.

**Simplify.**

- Replace the message bus with direct method calls plus a small event list (on wear, on hit, on drink) for effects. The veto chain is useful in a multiplayer engine with many listeners; a single-player game needs only a few hooks.
- Store container contents in a list on the container. The flat owner-list with back-pointers is an engine detail, not a rule.
- Replace the factory caster with a direct spell call and drop the invoker-keeping rule.
- Replace the packed-bit range and ammunition stats with plain fields.
- Drop the string-ID special cases (`ItemRejuv`, `Soiled`) unless a feature needs them.
- Drop the trade flags, the player-versus-monster distinctions and the PVP formulas.
- Store coins as an integer in the base unit and show denominations by formatting. Keep the weight rule only if carrying limits matter.

**Single-player changes.**

- Identification by name becomes a per-item flag. A scroll is identified once, for the player.
- Message channels for "others" disappear, which removes much of the message code.
- The "too powerful to endure possessing it" gate is a good single-player rule for dangerous loot and can stay as it is.
- Mana and charges can be one resource. The mana costs in `StdScroll` and `StdWand` are a fine starting point for a per-turn energy pool if the SUD is turn-based, and a simple charge count is enough if it is not.
- The tetanus bug is a lesson. In Python, a missing key should fail loudly instead of returning nothing. The mistake here, a silent null lookup, is easy to avoid with a dictionary access that raises.

## Coverage

- **Read in full (all lines of code, after the header and imports):** `Items/Basic/StdItem.java` (1-2386), `Items/Basic/StdContainer.java` (1-783), `Items/Armor/StdArmor.java` (1-746), `Items/Weapons/StdWeapon.java` (1-527), `Items/MiscMagic/StdScroll.java` (1-437).
- **Read in full from the first code line** (package, imports and licence header skipped; the class declarations were checked by grep): `Items/MiscMagic/StdWand.java` (19-421), `Items/Basic/StdDrink.java` (36-414), `Items/MiscMagic/StdPotion.java` (19-307), `Items/Basic/StdCoins.java` (19-251), `Items/Basic/StdFood.java` (19-194), `Items/Basic/GenItem.java` (19-128).
- **Read in part:** `Items/interfaces/Wearable.java` (251-321, 435-520, 600-690), `Items/interfaces/Container.java` (127-191), `Items/interfaces/Weapon.java` (grep of constants and masks, 49-207), `Items/interfaces/Armor.java` (grep, 47-128), `Items/interfaces/Wand.java` (50-70, 116), `Items/interfaces/Scroll.java` (grep), `Libraries/MUDFight.java` (216-240, 255-285, 348-425, 915-960, 1899-1904), `Common/DefaultCharState.java` (185-210, 250-275), `MOBS/StdMOB.java` (5881-5925), `Commands/Wear.java` (56-80, 100-170), `core/CMProps.java` (681, 3199, 3203, and the list-file entries), `resources/lists.ini` (686-779, 1066-1764, the last counted by script), and the file names and IDs of the disease classes.
- **Skipped:** `GenItem` internals (`CMLib.coffeeMaker()` XML and `GenericBuilder`), `RawMaterial` codes, `CMLib.beanCounter()`, `CMLib.ableMapper()`, expertise and proficiency internals, `CMLib.flags().validCheck`, the `CMLib.commands()` handlers that move items, the disease and effect classes themselves, and the wand, scroll and armour interfaces beyond the constants listed.
