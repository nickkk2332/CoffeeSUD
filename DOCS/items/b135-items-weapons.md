# Weapons and special items

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b135` (Items/Weapons) · 63 files · 8069 lines of Java.

Java paths are shortened to `Items/...`; the full prefix is `com/planet_ink/coffee_mud/`. The base-class machinery (`StdItem`, the wear and enchantment fields, and the durability and ammunition rules in `StdWeapon`) is covered in [b172](b172-items-base-item-classes-item-container-food-drink-coins-armo.md). This chapter says what each weapon type adds or changes, and goes deep only where a type has its own mechanics.

## Summary

Most of the 63 files are stat blocks, where a constructor sets damage, attack bonus, weight, value, damage type, weapon class and hands, and the family base supplies the rest. The behaviour sits in a few subtypes. Thrown entanglers (`StdLasso`, `StdNet`) bind their victim with the `Thief_Bind` effect, and `StdSiegeWeapon` runs its own tactical combat on a grid, one round every four pulses. Staves and wands are used by speaking a magic word hashed from the spell ID, and four unique items add effects by reacting to combat or speech messages. The `Gen*` classes store every field as builder-editable text, so new weapons need no Java. The bucket also has faults: some stat settings are overwritten or never take effect, and one field serves as both the ammunition type and a lock key.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Items/Weapons/StdWeapon.java` | Base weapon: enchantment, durability scaling, ammunition, thrown drop, wear messages | 527 |
| `Items/Weapons/StdSiegeWeapon.java` | Rideable siege engine: tactical grid, rounds, aim, durability damage | 1348 |
| `Items/Weapons/GenSiegeWeapon.java` | Builder siege engine, with container-style codes (locks, riders, strings) | 262 |
| `Items/Weapons/StdLasso.java` | Thrown lasso: hit places it on the victim and binds with `Thief_Bind` | 284 |
| `Items/Weapons/StdNet.java` | Thrown net: as the lasso, but binds the target's allies at the same range | 142 |
| `Items/Weapons/GenLasso.java`, `GenNet.java` | Builder versions of the two above (name and `sameAs` only) | 140, 139 |
| `Items/Weapons/Staff.java` | Wand-type staff: charges, magic word, spoken use | 216 |
| `Items/Weapons/GenStaff.java` | Builder staff: spell and charges as codes | 356 |
| `Items/Weapons/ArchonStaff.java` | Administrator staff: level, gain, heal and damage words | 528 |
| `Items/Weapons/GenWeapon.java` | Builder weapon: range, damage type, class, ammunition as codes (`GENWEAPONCODES`, line 96) | 208 |
| `Items/Weapons/StdBoffWeapon.java`, `GenBoffWeapon.java` | Blunt weapons with comic hit verbs | 100, 150 |
| `Items/Weapons/StdBow.java`, `GenBow.java`, `GenHandBow.java` | Bows and crossbows with ammunition | 71, 143, 143 |
| `Items/Weapons/StdSling.java`, `GenSling.java`, `StdSpear.java`, `GenSpear.java`, `Spear.java`, `Arquebus.java`, `Javelin.java`, `ThrowingStone.java` | Other ranged, thrown and polearm types | 72, 140, 69, 140, 69, 73, 62, 61 |
| `Items/Weapons/HolyAvenger.java` | Alignment-gated sword that dispels evil on each hit | 123 |
| `Items/Weapons/FlamingSword.java` | Extra fire damage on each hit by the wielder | 89 |
| `Items/Weapons/DaggerOfVenom.java` | Poisons on about 5 percent of damaging hits | 82 |
| `Items/Weapons/EternityQuarterstaff.java` | Heals on a spoken word, at a durability cost | 103 |
| `Items/Weapons/ShieldWeapon.java` | Proxy weapon built from a shield, for a shield bash | 81 |
| `Items/Weapons/Natural.java`, `DragonClaw.java` | Unarmed "fingernails and teeth", and a claw item | 64, 59 |
| `Items/Weapons/Claymore.java`, `Halberd.java` | Two-handed items with a random quality tier | 100, 98 |
| `Items/Weapons/DrowDagger.java`, `DrowMace.java`, `DrowQuarterstaff.java`, `DrowSword.java` | Dark-elf items with a random enchantment of 1 to 6 | 61 each |
| 26 other files | Plain stat blocks (Sword family, Dagger family, Mace, Quarterstaff, Club, Sickle, Glaive, Trident and others) | see catalogue |

## How it works

### 1. What the subtypes change

| Group | Types | What differs from `StdWeapon` |
|---|---|---|
| Plain stat blocks | 26 files | Only constructor values. The family base sets the class and damage type (for example `Sword.java:57-59`). |
| Random variants | `Claymore`, `Halberd`, four `Drow*` | A random tier (name only) or a random enchantment set in the constructor. |
| Builder classes | 10 `Gen*` files | Stats stored as text codes that builders edit in game (`GenWeapon.java:96-154`). |
| Ammunition families | `StdBow`, `StdSling`, `Arquebus`, `GenHandBow`, `StdSiegeWeapon` | Ammunition type in `readableText`, rounds in the durability counter. |
| Thrown entanglers | `StdLasso`, `StdNet` | A hit binds the victim instead of doing damage. |
| Siege | `StdSiegeWeapon`, `GenSiegeWeapon` | Its own combat loop, aiming and crew. |
| Wands | `Staff`, `GenStaff`, `ArchonStaff` | Charges, and use by speech. |
| Unique effects | `HolyAvenger`, `FlamingSword`, `DaggerOfVenom`, `EternityQuarterstaff` | Extra effects triggered from combat or speech messages. |
| Proxies | `ShieldWeapon`, `Natural` | Stand in for a shield bash or an unarmed attack. |
| Blunt joke weapons | `StdBoffWeapon`, `GenBoffWeapon` | Only the hit text changes. |

### 2. Rules the subtypes depend on

- **Enchantment.** `StdWeapon.recoverPhyStats` adds `ability` to damage and `10 x ability` to attack, but only when base damage is not zero, and it marks any enchanted weapon as a bonus item (`Items/Weapons/StdWeapon.java:140-149`). The Drow items use this. `DrowDagger.java:49` sets `basePhyStats().setAbility(CMLib.dice().roll(1,6,0))`, so each one gets +1 to +6 damage and +10 to +60 attack.
- **Durability scales damage and value.** `subjectToWearAndTear()` is true for uses from 0 to 1000 when the item needs no ammunition and is not a wand (`StdWeapon.java:504-511`). Below 100 uses, damage becomes `round(damage x uses / 100)` (`StdWeapon.java:153-154`), and value is scaled the same way (`StdWeapon.java:499-500`). At zero uses the item is destroyed (`Libraries/MUDFight.java:268-284`).
- **Two-handed items.** These set `wornLogicalAnd=true` with `WORN_HELD|WORN_WIELD`, for example `Claymore.java:64-65` and `Staff.java:61-63`. With the flag on, the item counts as worn only when both bits are set (`Items/Basic/StdItem.java:388-389`). With it off, either bit is enough (`:391`).
- **Ammunition.** The ammunition type is stored in `readableText` (`StdWeapon.java:430-440`), and the rounds left are the durability counter (`:415-421`, `:443-446`). A shot with no rounds prints "@x1 is out of @x2." and posts a reload request (`StdWeapon.java:304-334`).
- **Reloading.** A reload moves rounds from an ammunition item, copies the ammunition's savable effects onto the weapon as non-savable effects (`StdWeapon.java:202-213`), and destroys an empty ammunition item (`:214-215`). Whether such an effect fires on a hit depends on the effect class, which this file does not check.
- **Thrown weapons** get a drop trailer on their own attack, so they land on the floor after the throw (`StdWeapon.java:245-248`).

### 3. Ranged families and reach

| Type | Ammunition (capacity) | Reach (min to max) | Damage | Hands | Source |
|---|---|---|---|---|---|
| `StdBow` | arrows (20) | 1 to 3 | 5 piercing | 2 | `StdBow.java:52-61` |
| `GenHandBow` | bolts (1) | 1 to 2 | 5 piercing | 1 | `GenHandBow.java:50-54` |
| `StdSling` | bullets (50) | 1 to 2 | 2 bashing | 1 | `StdSling.java:52-61` |
| `Arquebus` | bullets (1), loaded at creation | 0 to 5 | 10 piercing, attack -1 | 2 | `Arquebus.java:52-63` |
| `StdSiegeWeapon` | spears (1) | 1 to 10 | 20 piercing | not wearable | `StdSiegeWeapon.java:71-85` |

A minimum reach of 1 rules out melee range (`rangeToTarget()` of 0). `Spear` and `StdSpear` have minimum 0, so they can be used in melee.

### 4. Thrown entanglers: `StdLasso` and `StdNet`

- **Shape.** Both are thrown, natural-type, reach 1 to 1, weight 1, value 10 and two-handed (`StdLasso.java:51-60`, `StdNet.java:51-60`). Their thrown attack returns before the base class runs, so the base drop trailer is skipped (`StdLasso.java:223-226`, `StdNet.java:91-94`).
- **Hit.** `okMessage` sets the damage to zero (`StdLasso.java:114-121`, `StdNet.java:76-84`). On the damage message, `executeMsg` unwears the item and queues three trailers: a drop in the room, a get by the victim, and a `TYP_GENERAL` message from the thrower to the victim (`StdLasso.java:229-239`, `StdNet.java:97-107`). The item therefore ends up in the victim's inventory.
- **Binding.** A `TYP_GENERAL` message on an item in the victim's inventory invokes `Thief_Bind` on the victim and records the thrower as `lastBinder` (`StdLasso.java:241-261`). `Thief_Bind` sets `IS_BOUND` (`Abilities/Thief/Thief_Bind.java:131`) and turns hand and movement actions into struggles, with "<S-NAME> struggle(s) against @x1 binding <S-HIM-HER>." (`:158`). If the bind allows breaking, each struggle subtracts the struggler's strength plus level from the remaining strength, and the bind ends below zero (`:160-164`). A bind that cannot be broken prints "You are constricted by @x1 and can't move!" (`:169`).
- **Release.** While the victim is bound, the thrower's own GET, TAKE or UNTIE commands are intercepted (`StdLasso.java:152-216`). `untieIfPoss` frees the victim only when the thrower is the binder. Otherwise it prints "You can't figure out the knots." (`:73-107`, message at `:84`). Freeing the victim drops the lasso and returns it to the thrower (`:98-100`).
- **Animals.** A lasso on an animal-intelligence target first checks whether anyone in the thrower's group is fighting (`:123-132`). If nobody is, the target is made peaceful when the thrower has law privileges in the room (`:134-135`). After the hit, every group member who was fighting the target is made peaceful (`:136-147`).
- **Net.** `StdNet` does not stop at the victim. Once the net lands, every other inhabitant of the room who is outside the thrower's group, has the same combat victim as the target, and is at the same range also receives `Thief_Bind` (`StdNet.java:109-137`). A net thrown into a melee snares the target's allies at that range.
- **Skill link.** `Skill_Lassoing` uses any rope item in hand or wielded, found by `CMLib.flags().isARope` (`Abilities/Skills/Skill_Lassoing.java:275-292`, `Libraries/Sense.java:2575`). With `auto` set, it creates a generic rideable named "a lasso" with ladder basis instead.

### 5. Siege weapons: `StdSiegeWeapon`

**Stats and wearing.** It extends `StdRideable` (`StdSiegeWeapon.java:37`), so crew can ride it. Its defaults are damage 20, piercing, ranged, reach 1 to 10, one "spears" round, weight 500, value 15000 and oak, and it is not wearable (`:61-88`). `getMaxHullPoints()` is `12 + armor` (`:173-177`). The weapon's own damage code does not use it (`:575-604`).

**Engagement.** The `TARGET` and `AIM` commands are matched on any prefix (`:973`, `:1021`). `startAttack` engages a target: it sets `siegeTarget`, places both items on a shared coordinate list for the room, and starts a repeating tick (`:641-682`, `:401-422`, `:419-420`). The round interval is `CombatLibrary.TICKS_PER_SHIP_COMBAT = 4` (`Libraries/interfaces/CombatLibrary.java:87`). `startTickDown` uses the global pulse (`core/threads/ServiceEngine.java:349-351`). A tick client decrements its counter every pulse and calls `tick()` when it reaches zero, then resets it (`core/threads/StdTickClient.java:140-156`). A round is therefore four pulses, or 16 seconds at the default `TIME_TICK = 4000` (`core/CMProps.java:681`).

**Tactical grid.** Each engaged item gets a coordinate pair chosen at random from `0` to `room.maxRange()-1` (`getMagicCoords`, `:232-242`). It retries up to ten times to avoid an occupied square (`:325-330`). `Room.maxRange()` returns the room's height stat (`Locales/StdRoom.java:2895-2898`). Distance is the ceiling of the Euclidean distance between the two pairs (`:244-265`, formula at `:257`).

**Each round (`tick`, `:751-941`).**
1. If the weapon has gone more than ten rounds without an action, it disengages with "<S-NAME> disengage(s)." (`:759-783`). Any action resets `ticksFromHappen` (`:412`, `:578`, `:621`, `:643`).
2. A queued PUSH, PULL or LEAVE order moves the weapon one step (`:785-839`). The order is queued in `okMessage` (`:1125-1160`).
3. Firing. A weapon that needs ammunition and has none does not fire. Otherwise, if it is aimed, the hit test runs and `CMLib.combat().postSiegeAttack` posts the shot (`:841-887`, `Libraries/MUDFight.java:690-708`). A status line that repeats is shown at most three times in a row (`:892-929`).

**Commands.** `TARGET <weapon> <target>` engages (`:963-1019`). `AIM <weapon> <lead>` sets the aim (`:1021-1123`), and needs tactical mode (`:1023-1026`). The lead moves the aim point along the target's facing if the target is navigable, and otherwise in a random direction (`:1062-1082`). The target must be within reach (`:1083-1090`, "Your target is presently at distance of @x1, but this weapons range is @x2 to @x3."). The weapon must be loaded (`:1091-1099`). On success it says "@x1 is now aimed and will be fired in @x2 seconds.", where the seconds are the time until the next round (`:1100-1120`). Monsters cannot re-aim an aimed weapon (`:1102-1106`).

**Hit test (`:866-878`).** A shot aimed at the target's square hits, unless a d100 roll is below `A = target armor - weapon attack` when A is positive. A shot aimed elsewhere hits only if A is negative and a d100 roll is below `-A`. So a correct aim hits with about `1 - A/100` probability and a wrong aim with about `-A/100`.

**Damage taken (`:575-604`).** Damage to the weapon is converted to durability loss at 10 points per damage point. The code is `pctLoss = value/level x 10` and `pointsLost = round(pctLoss x level)`, which is `value x 10` because `level` cancels (`:579-583`). When the loss reaches the remaining durability, the weapon is destroyed with "<T-NAME> <T-IS-ARE> destroyed!" (`:586-597`). Each hit that does damage also calls `postItemDamage` for 1 point (`:613-623`). Bonus items take that point only when a d100 roll exceeds their level.

**Crew, ships and players.** The weapon's shooter is a factory MOB named after the weapon, riding it, and given the clan of recent player attackers (`:626-639`). The first MOB rider is the operator (`:217-230`). In a boardable area (a ship), the weapon's own tick stops and ship combat takes over (`:756-757`, `:937-939`). Ships apply a hit as a percent of max hull (`Items/Basic/StdSiegableBoardable.java:1448-1462`). Players who gave it orders in the last five minutes, and any riders in the room, receive its round messages (`:684-749`).

### 6. Staves and wands

**`Staff`.** It implements `Wand` (`Staff.java:36`). Charges are the durability counter, with no maximum (`:66`, `:70-85`), and value is zero when empty (`:122-128`). The magic word comes from the spell ID: `StdWand.getWandWord` hashes the ID into the `MAGIC_WORDS` list (`Items/MiscMagic/StdWand.java:137-143`; list at `resources/lists.ini:1066`, about 700 words). Speech by the holder that contains the word, while the staff is worn properly, adds a `TYP_WAND_USE` trailer (`Staff.java:184-209`, `StdWand.checkWave` at `StdWand.java:216-222`). The match is a plain substring test.

**Using it (`StdWand.waveIfAble`, `StdWand.java:224-296`).**
- The user needs a `Wand.WandUsage` ability: `Skill_WandUse`, `Skill_ShardUse`, `Skill_RelicUse` or `PlayInstrument`. The item's enchant type limits which family may use it: ANY, SPELL, PRAYER, CHANT or SKILL (`Items/interfaces/Wand.java:56-62`). `Staff` uses ANY (`Staff.java:151`, `getEnchantType` returns -1).
- A failed proficiency check prints "@x1 glows faintly for a moment, then fades." With no charges it prints "@x1 seems spent."
- The spell is cast at caster level `item level + min(enchant, 10) + expertise level`, never below the spell's lowest qualifying level. Then the item loses one charge and prints "@x1 glows brightly."
- Mana (`StdWand.useTheWand`, `:103-121`): 5 for a spell that qualifies for the user, and 25 for one that does not. The cost falls by 2 per item level, with a minimum of 5.

**`GenStaff`.** The builder staff stores its spell as an ability ID and exposes `ENCHTYPE`, `SPELL` and `MAXUSES` as codes (`GenStaff.java:255`, `getStat`/`setStat` at `:258-330`). It is never worn down by charge use (`:76-78`). `setReadableText` treats the text as a spell name when an ability with that ID exists and keeps it as readable text otherwise (`:160-172`), so one field does two jobs.

**`ArchonStaff`.** An administrator item (`ArchonStaff.java:39`, level 30, damage 12, attack 10, value 10000). Its speech commands are checked in `waveIfAble` (`:136`):
- `LEVEL ALL UP`, `LEVEL n UP` and `LEVEL n DOWN` change the target's level through the leveler and experience (`:144-208`, `:275-310`).
- `GAIN n UP` and `GAIN ALL UP` set proficiency to 100 for one skill, or for every class ability (`:211-274`).
- `RESTORE` removes disease and poison (through the prayers `Prayer_CureDisease` and `Prayer_RemovePoison`) and clears bleeding, injury, amputation, broken-limb and scarring effects (`:315-370`).
- `REWIND` sets the `NEXTCAST` cooldown of every ability on the target to zero (`:371-393`). `REFRESH` clears bleeding and recovers the target's maximum state (`:394-410`).
- `BLAST` and `BURN` damage the target (`:411-430`). `BURN` deals `3k+1` fire damage, where `k` is `round(random x 6)`, so from 1 to 19.
- Every damaging hit with the staff adds fire damage of `round(random x 6)` times the item's level, which is 30 by default (`:514-517`).
- While it is worn properly, hunger and thirst are set to very large values (`:439-450`).
- Only system operators can hold it. Others see "@x1 flashes and falls out of <S-HIS-HER> hands!" (`:470-472`).
- `safetyCheck` (`:101-118`) refuses to act unless the spoken words appear in the user's last typed command, aliases excepted, and then prints "The wand fizzles in an irritating way."

### 7. Unique items

**`HolyAvenger`** (`TwoHandedSword` subclass; level 20, attack +25, damage 18, weight 25, value 15500, mithril, good and bonus dispositions, `HolyAvenger.java:48-59`). Holding, wearing, wielding or taking it requires the class ID "Paladin" and, when the GOOD and LAWFUL factions are loaded, a good and lawful alignment (`:72-81`). Otherwise the item is taken off and dropped: "@x1 flashes and flies out of <S-HIS-HER> hands!" (`:83-87`, message at `:84`). Against a living evil target, a trailer adds bursting damage of `roll(1,15)` halved (`:97-121`, `:112-114`), and a message reads as "dispels evil within <T-NAME>".

**`FlamingSword`** (`Longsword` subclass, level 1, damage 8, value 2500, light source and bonus dispositions, `FlamingSword.java:54`). When the wielder damages a living target, extra fire damage is posted: `round(random x 6) x item level`, which is 0 to 6 at level 1 (`:70-82`). The two ends (0 and 6) come up half as often as 1 to 5, because `round` splits its range that way.

**`DaggerOfVenom`** (`Dagger` subclass, level 1, damage 4, value 1500). On each damaging hit, `round(random x 20) == 10` is tested (`:72-73`). That is one value in twenty, so 5 percent. The hit then invokes `Poison` (`Abilities/Poisons/Poison.java`) on the target at the item's level (`:75-77`). Its secret identity reads "Periodically injects poison on a successful hit."

**`EternityQuarterstaff`** (`Quarterstaff` subclass, level 20, damage 12, value 5001, 50 uses, two-handed). While it is worn properly, the holder can speak "heal" at a person in the room. Each cast costs 5 durability (`:82-84`) and heals `1 + round(level/10)`, which is 3 at level 20 (`:88`). The messages are "<S-NAME> point(s) <S-HIS-HER> quarterstaff at <T-NAMESELF>, and delivers a healing beam of light." and "You feel a little better!". At 50 uses its damage is halved to 6 by the durability rule (`StdWeapon.java:153-154`).

### 8. Random and proxy items

**`Claymore` and `Halberd`.** A constructor draws `Math.abs(new Random(System.currentTimeMillis()).nextInt() % 3)` (`Claymore.java:51-52`, `Halberd.java:51-52`). The result picks one of three names and descriptions ("simple", "very nice", "exceptional"; `Claymore.java:70-98`). The draw is also stored with `phyStats.setAbility(...)` (`Claymore.java:54`). The constructor then calls `basePhyStats().setAbility(0)` (`:57`) and `recoverPhyStats()` (`:63`), and that copies the base over the current stats (`Items/Basic/StdItem.java:219-221`). The tier survives only as the name.

**`ShieldWeapon`** is a proxy. `Skill_Bash` creates one, copies the shield's name and description, sets damage to the shield's level plus twice the skill level, and calls `CMLib.combat().postAttack` (`Abilities/Skills/Skill_Bash.java:152-158`). A successful bash knocks the target sitting if it has legs and weighs less than twice the basher (`:159-165`). `Fighter_Pistolwhip.java:153` and `Songs/Skill_InstrumentBash.java:134` do the same.

**`Natural`** is the unarmed weapon. A mob's natural weapon comes from its race through `getNaturalWeapon` (`MOBS/StdMOB.java:1818-1828`), and the race's default list holds a `Natural` item (`Races/StdRace.java:808-816`). A mob with no race gets `CMClass.getWeapon("Natural")` directly (`StdMOB.java:1824`). Its hit text is "<S-NAME> <DAMAGE> <T-NAMESELF>." (`Natural.java:60-63`).

### 9. Boff weapons

`StdBoffWeapon` is blunt bashing with no rule in its own code except the hit text (`StdBoffWeapon.java:54-55`, `:61-99`). A roll of `1d7 - 1` picks one of seven verbs: puff, boff, poof, bambam, whack, smoosh or kabloom (`:64-88`). No other Java file refers to the class. Harmlessness comes from a property. The "dizzy boff stick" in `resources/skills/weaponsmith.txt:3` carries `Prop_NoDamage`, which zeroes damage messages in which the item is the tool (`Abilities/Properties/Prop_NoDamage.java:68-77`). The Archon guide describes the boff class as providing "silly and random hit messages" (`guides/ArchonGuide.html:2871`), which matches the code.

## Content worth mining

### Catalogue of the 63 classes

Stats are from the constructors, with inheritance resolved. Dmg is base damage and Atk is the attack adjustment. Value is the base gold value. Hands is 1 or 2. Where a constructor adds to a value (`+=`), the result is given. Names are the default `setName` values, and the weapons that choose their name at random are marked.

| Class (parent) | Name | Type / class | Dmg / Atk | Wt | Lvl | Value | Hands | Notes |
|---|---|---|---|---|---|---|---|---|
| StdWeapon (StdItem) | weapon | natural / natural | 0 / 0 | - | 0 | 15 | 1 | base; see b172 |
| Arquebus (StdWeapon) | an arquebus | piercing / ranged | 10 / -1 | 15 | 0 | 500 | 2 | bullets 1; reach 0-5 |
| ArchonStaff (Staff) | a wooden staff | bashing / staff | 12 / 10 | 4 | 30 | 10000 | 2 | admin only |
| BattleAxe (Sword) | a battle axe | slashing / axe | 8 / 0 | 15 | 0 | 35 | 2 | |
| Broom (Quarterstaff) | a broom | bashing / staff | 3 / 0 | 4 | 0 | 1 | 2 | same stats as Quarterstaff |
| Cestus (StdWeapon) | a mean looking cestus | piercing / edged | 4 / 0 | 2 | 0 | 5 | 1 | spiked glove |
| Claymore (Sword) | random: simple, very nice or exceptional claymore | slashing / sword | 8 / 0 | 10 | 0 | 25 | 2 | random name |
| Club (StdWeapon) | a wooden club | bashing / blunt | 7 / 0 | 10 | 0 | 8 | 1 | |
| Dagger (StdWeapon) | a small dagger | piercing / dagger | 3 / +5 | 1 | 0 | 2 | 1 | |
| DaggerOfVenom (Dagger) | a small dagger | piercing / dagger | 4 / 0 | 1 | 1 | 1500 | 1 | 5% poison per hit |
| Dirk (Dagger) | a dirk | piercing / dagger | 4 / 0 | 1 | 0 | 2 | 1 | |
| DragonClaw (Natural) | a vicious dragons claw | slashing / natural | 8 / +2 | 7 | 0 | 15 | 1 | |
| DrowDagger (Dagger) | a dagger | bashing / dagger | 4 / 0, +1d6 enchant | 4 | 1 | 2500 | 1 | drow |
| DrowMace (Mace) | a mace | bashing / blunt | 6 / 0, +1d6 enchant | 4 | 1 | 2500 | 1 | drow |
| DrowQuarterstaff (Mace) | a quarterstaff | bashing / blunt | 6 / 0, +1d6 enchant | 4 | 1 | 2500 | 1 | class is blunt, not staff |
| DrowSword (Longsword) | a longsword | slashing / sword | 8 / 0, +1d6 enchant | 4 | 1 | 2500 | 1 | drow |
| EternityQuarterstaff (Quarterstaff) | a quarterstaff | bashing / staff | 12 / 0 | 4 | 20 | 5001 | 2 | 50 uses; heal word |
| FlamingSword (Longsword) | a fancy longsword | slashing / sword | 8 / 0 | 4 | 1 | 2500 | 1 | fire on hit |
| GenBoffWeapon (StdBoffWeapon) | a generic boff weapon | bashing / blunt | 5 / 0 | 2 | 5 | 5 | 1 | builder |
| GenBow (StdBow) | a generic short bow | piercing / ranged | 5 / 0 | 8 | 0 | 150 | 2 | arrows 20; reach 1-3 |
| GenHandBow (StdBow) | a generic hand crossbow | piercing / ranged | 5 / 0 | 8 | 0 | 150 | 1 | bolts 1; reach 1-2 |
| GenLasso (StdLasso) | a generic lasso | natural / thrown | 0 / 0 | 1 | 0 | 10 | 2 | reach 1-1 |
| GenNet (StdNet) | a generic net | natural / thrown | 0 / 0 | 1 | 0 | 10 | 2 | reach 1-1 |
| GenSiegeWeapon (StdSiegeWeapon) | a generic siege weapon | piercing / ranged | 20 / 0 | 500 | 0 | 15000 | - | not wearable |
| GenSling (StdSling) | a generic sling | bashing / ranged | 2 / 0 | 8 | 0 | 150 | 1 | bullets 50 |
| GenSpear (StdSpear) | a generic spear | piercing / thrown | 5 / 0 | 8 | 0 | 10 | 1 | reach 0-3 |
| GenStaff (GenWeapon) | a wooden staff | bashing / staff | 4 / 0 | 4 | 0 | 1 | 2 | spell charges |
| GenWeapon (StdWeapon) | a generic weapon | bashing / natural by default | 5 / 0 | 2 | 5 | 5 | 1 | builder base |
| Glaive (StdWeapon) | a heavy glaive | slashing / polearm | 6 / 0 | 8 | 0 | 6 | 2 | |
| Halberd (StdWeapon) | random: simple, very nice or exceptional halberd | slashing / polearm | 10 / 0 | 10 | 0 | 10 | 2 | random name |
| HolyAvenger (TwoHandedSword) | the holy avenger | slashing / sword | 18 / 25 | 25 | 20 | 15500 | 2 | paladin and good only |
| Javelin (StdWeapon) | a steel javelin | piercing / thrown | 6 / 0 | 2 | 0 | 1 | 1 | uses 1: damage rounds to 0 |
| Katana (Sword) | a katana | slashing / sword | 8 / 0 | 4 | 0 | 15 | 1 | |
| Longsword (Sword) | a fancy longsword | slashing / sword | 8 / 0 | 4 | 0 | 15 | 1 | |
| Mace (StdWeapon) | a rather large mace | bashing / blunt | 7 / 0 | 10 | 0 | 8 | 1 | |
| Natural (StdWeapon) | fingernails and teeth | natural / natural | 0 / 0 | 0 | 0 | 15 | - | unarmed |
| Quarterstaff (StdWeapon) | a wooden quarterstaff | bashing / staff | 3 / 0 | 4 | 0 | 1 | 2 | |
| Rapier (Sword) | an sleek rapier | piercing / sword | 7 / 0 | 4 | 0 | 15 | 1 | name typo kept |
| Ruler (Quarterstaff) | a ruler | bashing / staff | 3 / 0 | 4 | 0 | 1 | 2 | |
| Sabre (StdWeapon) | a sabre | slashing / sword | 6 / 0 | 5 | 2 | 15 | 1 | |
| Scimitar (Sword) | an ornate scimitar | slashing / sword | 8 / 0 | 4 | 0 | 15 | 1 | |
| ShieldWeapon (StdWeapon) | a bashing shield | bashing / blunt | 1 (set to shield level) / 0 | 0 | 0 | 15 | - | proxy for shield bash |
| Shortsword (Sword) | a short sword | piercing / sword | 5 / 0 | 3 | 0 | 10 | 1 | |
| Sickle (StdWeapon) | a sickle | piercing / edged | 5 / 0 | 3 | 0 | 1 | 1 | |
| SmallDagger (Dagger) | a small dagger | piercing / dagger | 3 / +5 | 1 | 0 | 2 | 1 | |
| SmallHammer (StdWeapon) | a small hammer | bashing / hammer | 4 / +1 | 3 | 0 | 10 | 1 | |
| SmallMace (StdWeapon) | a small mace | bashing / blunt | 4 / 0 | 10 | 0 | 8 | 1 | |
| Spear (StdWeapon) | a spear | piercing / polearm | 5 / 0 | 8 | 0 | 10 | 2 | reach 0-1 |
| Staff (StdWeapon) | a wooden staff | bashing / staff | 4 / 0 | 4 | 0 | 1 | 2 | charges 0 by default |
| StdBoffWeapon (StdWeapon) | boff weapon | bashing / blunt | 0 / 0 | default | 0 | 15 | 1 | comic hit verbs |
| StdBow (StdWeapon) | a short bow | piercing / ranged | 5 / 0 | 8 | 0 | 150 | 2 | arrows 20; reach 1-3 |
| StdLasso (StdWeapon) | a lasso | natural / thrown | 0 / 0 | 1 | 0 | 10 | 2 | reach 1-1; binds |
| StdNet (StdWeapon) | a net | natural / thrown | 0 / 0 | 1 | 0 | 10 | 2 | reach 1-1; binds allies |
| StdSiegeWeapon (StdRideable) | a siege weapon bow | piercing / ranged | 20 / 0 | 500 | 0 | 15000 | - | spears 1; reach 1-10 |
| StdSling (StdWeapon) | a sling | bashing / ranged | 2 / 0 | 8 | 0 | 150 | 1 | bullets 50; reach 1-2 |
| StdSpear (StdWeapon) | a spear | piercing / thrown | 5 / 0 | 8 | 0 | 10 | 1 | reach 0-3 |
| Stiletto (Dagger) | a cool stiletto | piercing / dagger | 3 / 0 | 1 | 0 | 1 | 1 | |
| Sword (StdWeapon) | a sword | slashing / sword | 8 / 0 | 10 | 0 | 15 | 1 | base sword |
| ThrowingStone (StdWeapon) | a throwing stone | bashing / thrown | 1 / 0 | 1 | 0 | 10 | 1 | reach 0-3 |
| Trident (StdWeapon) | a trident | piercing / polearm | 10 / 0 | 10 | 1 | 10 | 2 | |
| TwoHandedSword (Sword) | a two-handed sword | slashing / sword | 10 / 0 | 15 | 0 | 50 | 2 | |
| Whip (StdWeapon) | a long leather whip | slashing / flailed | 2 / 0 | 2 | 0 | 1 | 1 | slashing type doubted in a comment |
| WarHammer (StdWeapon) | a warhammer | bashing / hammer | 6 / 0 | 10 | 0 | 25 | 2 | |

### Damage types, weapon classes and their messages

- **Damage types** (`Items/interfaces/Weapon.java:87-106`): natural, slashing, piercing, bashing, burning, bursting, shooting, frosting, gassing, melting, striking, lasering, sonicing, disrupting, stultifying, corrupting, scraping.
- **Elemental mapping** (`Weapon.java:111-130`): burning becomes fire, frosting becomes cold, gassing becomes gas, melting becomes acid, striking becomes electric, lasering becomes laser, sonicing becomes sonic, disrupting becomes poison, stultifying becomes paralysis, corrupting becomes undead and scraping becomes water. Natural, slashing, piercing, bashing, bursting and shooting map to none.
- **Weapon classes** (`Weapon.java:166-180`): axe, blunt, edged, flailed, hammer, karate (the natural class), polearm, ranged, sword, dagger, staff and thrown.

### Message and word lists

- **Weapon condition text**, edged and other (`resources/lists.ini:686-707`). Edged: "looks slightly used", "is somewhat dull", "is dull", "has some notches" (plus a second word for small cracks), "is heavily damaged", "is so damaged that it is practically harmless", "is nearly destroyed and completely harmless". Other: "looks slightly used", "is somewhat worn", "is worn", "is damaged", and then the same last three. The thresholds are 95, 85, 75, 50, 25, 10, 5 and 0 percent (`StdWeapon.java:348-372`).
- **Boff verbs** (`StdBoffWeapon.java:64-88`): puff, boff, poof, bambam, whack, smoosh, kabloom.
- **Wand words**: about 700 nonsense words in `resources/lists.ini:1066`, chosen by spell hash. The words are not meaningful.
- **Archon words** (`ArchonStaff.java:48`): LEVEL, RESTORE, REFRESH, BLAST, BURN, GAIN, REWIND.
- **Item lore** (paraphrased): the Holy Avenger is a paladin's sword that dispels evil; the flaming sword adds fire on each strike; the Dagger of Venom poisons now and then; the Eternity quarterstaff comes from a sacred tree and heals on command.
- **Lasso and net messages**: "You can't figure out the knots." (`StdLasso.java:84`); "<S-NAME> untie(s) <T-NAME>." (`:88`); "<S-NAME> struggle(s) against @x1 binding <S-HIM-HER>." (`Thief_Bind.java:158`).

## Notable design ideas

- **A stable spoken trigger without storage.** A wand's magic word is `hash(spell ID)` mapped into a word list (`StdWand.java:137-143`). Each spell gets a fixed word that never has to be saved, and saying it is the command.
- **Capture by area.** A net binds every same-range enemy who is fighting the same target (`StdNet.java:109-137`). That makes a thrown weapon a group tactic, not just a single-target effect.
- **Effects carried by ammunition.** Savable effects on a round are copied onto the launcher as non-savable effects at reload (`StdWeapon.java:202-213`), so enchanted ammunition carries its effect.
- **Siege combat as rounds on a grid.** Coordinates, facing, aim with lead, a move order and a repeating pulse give a small wargame (`StdSiegeWeapon.java:232-265`, `:785-839`, `:1062-1082`). The grid size comes from the room's height stat (`StdRoom.java:2895-2898`).
- **Uniques that hook messages.** Each special effect is a trailer or extra message, and alignment is an `okMessage` veto (`HolyAvenger.java:72-121`). Items therefore change the game without touching the base classes.
- **Builder codes.** Every `Gen*` class exposes its fields through `getStat`/`setStat` text codes (`GenWeapon.java:96-154`). Builders create and edit weapons in game.
- **Speech guard.** `ArchonStaff.safetyCheck` (`:101-118`) makes sure the spoken command was typed, so an accidental word in conversation does not fire a powerful effect.

## Problems and cruft

1. **`Javelin` does no damage and is worth nothing.** It sets 1 use (`Items/Weapons/Javelin.java:55-56`). Damage 6 is then scaled to `round(6 x 1/100)` = 0 (`StdWeapon.java:153-154`, `core/CMath.java:445-448`, `:493`). Value scales the same way (`StdWeapon.java:499-500`). If one-use javelins were intended, the scaling defeats them.
2. **Eternity heal can make durability negative.** The check is `usesRemaining()>0`, then it subtracts 5 (`EternityQuarterstaff.java:82-84`). From 1 to 4 uses the count goes negative. `subjectToWearAndTear()` then returns false (`StdWeapon.java:505-511`), so the staff regains full damage (`:153`), full value (`:499`), and takes no wear from hits (`Libraries/MUDFight.java:268`).
3. **Claymore and Halberd tiers are discarded.** The tier is written into `phyStats` and then overwritten by the base copy in `recoverPhyStats` (`Claymore.java:54`, `:57`, `:63`; `StdItem.java:219-221`). The intended ability is never kept. `Halberd.java:75` also uses the `Claymore.PLAIN` constants.
4. **Siege rider string cannot be set.** `GenSiegeWeapon.setStat` handles `case 24` (`GenSiegeWeapon.java:212`), but `RIDERSTR` is index 14 and `getStat` reads case 14 (`:142-143`). Setting it falls through to the extension-value default.
5. **One field, two meanings.** In `GenSiegeWeapon`, `readableText` is also the lock key (`keyName`, `GenSiegeWeapon.java:68-76`; keys are compared in `Items/Basic/StdContainer.java:349`, `:377`, `:382`). It is also the ammunition type through `StdSiegeWeapon.ammunitionType()` (`StdSiegeWeapon.java:1261-1263`). Setting one changes the other.
6. **Two condition-text systems.** `StdSiegeWeapon.weaponHealth` hard-codes its messages and thresholds (`StdSiegeWeapon.java:1185-1206`). `StdWeapon` reads editable lists (`lists.ini:686-707`).
7. **Typo.** "will be engage in" (`StdSiegeWeapon.java:1118`).
8. **Natural class reads as "KARATE".** `CLASS_NATURAL` is index 5 and its description is "KARATE" (`Weapon.java:148`, `:173`). A builder-made weapon keeps the natural class by default (`StdWeapon.java:45`), so it shows as karate until set.
9. **Quarterstaff class error.** `DrowQuarterstaff` extends `Mace`, so it is blunt, not staff (`Mace.java:57`).
10. **Whip damage type.** `Whip.java:56` is `TYPE_SLASHING;//?????????`, which records doubt about the type.
11. **Unfinished backfire.** `Arquebus.java:68` onward is a commented-out `isBackfire` stub.
12. **Unused shield constructors.** `ShieldWeapon(Item)` and `setShield` (`ShieldWeapon.java:59-79`) have no callers. `Skill_Bash` builds its own copy (`Skill_Bash.java:152-158`).
13. **Dead guard.** `HolyAvenger` tests `msg.value()>0` inside a block that already requires it (`HolyAvenger.java:102`, `:113-114`). The halving always applies, so the test does nothing.
14. **Level term that cancels.** The siege damage code divides by a level floored at 10 and then multiplies by the same level (`StdSiegeWeapon.java:579-583`). The result is always `value x 10`, so the real rule is 10 durability points per damage point, and the floor only guards the division.
15. **Missed lassos and nets.** The drop trailer for a missed throw is commented out (`StdLasso.java:227`), and the thrown branch returns early (`StdNet.java:91-94`). Where a missed lasso ends up is not settled in these files (unverified).
16. **Net with no victim.** The check `M2.getVictim()==M.getVictim()` (`StdNet.java:126`) is true when both are null, so bystanders with no fight at the same range would be netted. Whether that state can happen is unverified.
17. **Re-aim rules.** Monsters cannot re-aim an aimed weapon (`StdSiegeWeapon.java:1102-1106`), but a player's new `AIM` replaces the old one. That looks like an oversight.
18. **Multiplayer assumptions.** Player-attacker lists with five-minute expiry (`:684-749`), group membership checks in the lasso and net (`StdLasso.java:125`, `StdNet.java:116`), clan copying onto factory attackers (`:632-636`), and "announce to users" messages (`:745-749`) all assume other people.
19. **Substring trigger.** The magic word test is `indexOf` on the upper-cased speech (`StdWand.java:216-222`), so the word can match inside another word.
20. **Drow dagger type.** `DrowDagger` is a dagger with bashing damage (`DrowDagger.java:58`). This may be intentional.

## Takeaways for a Python SUD

**Borrow**
- **One data record for weapons, with a catalogue file.** The 63 classes reduce to one `Weapon` record and about 60 rows of data. Subclass only where behaviour differs: staff charges, thrown binding, siege, and unique hooks.
- **Two simple rules.** Enchantment adds to damage and attack, and durability scales damage and value. Both are one-line formulas. Clamp durability at zero, which fixes problem 2.
- **Ammunition as a separate count.** Store ammunition type and capacity on the launcher and track rounds as their own field. Reload as an action.
- **Spoken magic words.** Map each spell to a word with a stable hash (`zlib.crc32` or `hashlib`; Python's built-in `hash()` changes between runs), and match the word in the player's input.
- **Binding as a status effect.** A `bound` status blocks movement and hand actions. The victim breaks free by struggling, and the binder can release the victim.
- **Two-phase hooks.** Let an item veto an action first, then apply its effect, as CoffeeMud's `okMessage` and `executeMsg` do.

**Simplify or drop**
- The siege engine is the largest body of code (1348 lines) and depends on a real-time pulse. For a text game, keep the commands (target, aim, fire) and resolve them one per turn with a fixed hit chance. Drop the factory MOBs, clans and ship handling.
- Drop the `Gen*` string codes in favour of plain data files, and drop the weapon-condition lists in favour of a single function.
- Replace the random tiers (`Math.abs(new Random(currentTimeMillis).nextInt() % 3)`) with a seeded choice so that tests are repeatable.
- `ArchonStaff` is an admin tool. A single-player game may want only a developer command.

**Single-player rethink**
- Group checks, clans, player-attacker lists and announcements to other users have no purpose here. Their equivalents are simply the player's own messages.
- The "crew" of a siege weapon becomes the player, or an NPC companion if one exists.
- The bound-victim logic can stay, with the binder being the player.

```python
@dataclass
class Weapon:
    id: str                    # stable key, like CoffeeMud's ID()
    name: str
    damage: int
    attack: int = 0
    enchant: int = 0           # CoffeeMud's "ability"
    durability: int = 100      # 0..100
    damage_type: str = "bashing"
    weapon_class: str = "blunt"
    hands: int = 1
    reach: tuple[int, int] = (1, 1)
    ammo: str | None = None
    ammo_capacity: int = 0
    thrown: bool = False

    def effective_damage(self) -> int:
        scaled = round((self.damage + self.enchant) * self.durability / 100)
        return max(0, scaled)
```

## Coverage

- **Read in full:** `Items/Weapons/StdWeapon.java`, `StdSiegeWeapon.java`, `GenWeapon.java`, `StdBoffWeapon.java`, `GenBoffWeapon.java`, `StdLasso.java`, `StdNet.java`, `Claymore.java`, `Halberd.java`, `HolyAvenger.java`, `Staff.java`, `FlamingSword.java`, `DaggerOfVenom.java`, `EternityQuarterstaff.java`, `ShieldWeapon.java`, `Natural.java`, `Javelin.java`, `Arquebus.java`, `Whip.java`, `StdBow.java`. Also `ArchonStaff.java` except lines 260 to 417 (checked by keyword), and `GenStaff.java` except lines 51 to 67 (checked by grep).
- **Read as diffs or partly:** `GenBow.java`, `GenHandBow.java`, `GenSling.java`, `GenSpear.java`, `GenLasso.java`, `GenNet.java` (diffed against their base classes); `GenSiegeWeapon.java` (lines 1 to 232 read; lines 233 to 262 checked by grep only).
- **Skimmed by script (constructor fields only):** the 26 plain stat blocks, plus `DragonClaw`, the four `Drow*` files, `StdSling`, `StdSpear`, `Spear` and `ThrowingStone`. A script resolved inheritance, and the catalogue reflects those values.
- **Outside the bucket, read for the interactions named above:** `Items/Basic/StdItem.java`, `Libraries/MUDFight.java`, `Items/MiscMagic/StdWand.java`, `Items/interfaces/Wand.java`, `Items/interfaces/Weapon.java`, `Abilities/Thief/Thief_Bind.java`, `Abilities/Properties/Prop_NoDamage.java`, `Abilities/Skills/Skill_Bash.java`, `Abilities/Skills/Skill_Lassoing.java`, `Items/Basic/StdSiegableBoardable.java` (ship damage only), `core/threads/ServiceEngine.java`, `core/threads/StdTickClient.java`, `Libraries/interfaces/CombatLibrary.java`, `Locales/StdRoom.java`, `resources/lists.ini`, `resources/skills/weaponsmith.txt` and `guides/ArchonGuide.html`.
- **Not verified:** the outcome of a missed lasso or net throw, and whether a net with no victim on either side can occur in play.
