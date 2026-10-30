# Mob types, part 1: service-NPC interfaces, dragons, drow, giants and the animal roster

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b145` (MOBS part 1/4: Auctioneer .. FrostGiant) · 58 files · 8295 lines of Java.

## Summary

This bucket is the first quarter of the `MOBS/` directory, from `Auctioneer` to `FrostGiant`. Seven of its 58 files are interfaces: the base `MOB` contract, the `Deity` interface, and five shopkeeper-style NPC interfaces (auctioneers, craft brokers, librarians, post office clerks, bankers). The five shopkeeper variants belong to a named chain, so several branches act as one service. The other 51 files are concrete mobs. The distinctive ones are the dragon family, which stores age in the level and colour in the ability field, breathes elemental attacks from a colour table and keeps swallowed victims in a stomach room. The drow casters share a magic-resistance check and a natural prayer list. A few special-purpose mobs are worth a look: a shape-copying doppleganger, an invulnerable training dummy that turns into a cage item when picked up, and a cow that is an endless milk source. The other 33 classes are mostly animals and humanoid extras built from one `StdMOB` constructor template. Several bugs are visible on reading, including an off-by-one that means no randomly generated dragon is ever a hatchling, and a doppleganger that stops re-mimicking after its timeout.

**Citation convention.** Paths are relative to `com/planet_ink/coffee_mud/`, so `MOBS/Dragon.java:96` means `com/planet_ink/coffee_mud/MOBS/Dragon.java`, line 96. The one exception is `coffeemud.ini`, which sits at the CoffeeMud root. Line numbers were taken from the source at the time of reading.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `MOBS/interfaces/MOB.java` | Base mob contract (99 method declarations, `Attrib` enum at 1055). Read in full by b173 and not re-explained here. | 1149 |
| `MOBS/interfaces/Deity.java` | Gods, extends `MOB` (line 49). Worship, cleric requirements, rituals, blessings, curses, power-ups. Read in full by b173. | 767 |
| `MOBS/interfaces/Librarian.java` | Lending shop chain (extends `ShopKeeper`, line 51). Borrow limits, overdue charges, checkout records. | 324 |
| `MOBS/interfaces/PostOffice.java` | Post office chain (line 50). Boxes, postage, hold fees, `MailPiece` record. | 285 |
| `MOBS/interfaces/Banker.java` | Bank chain (line 50). Covered in b173. | 248 |
| `MOBS/interfaces/CraftBroker.java` | Request-listing broker chain (line 48). Commission and listing limits. | 111 |
| `MOBS/interfaces/Auctioneer.java` | Auction house chain (line 52, extends `ShopKeeper` and `AuctionPolicy`). | 69 |
| `MOBS/Dragon.java` | Dragon base: age, colour, claws, breath, stomach, colour-based saves. | 789 |
| `MOBS/BlackDragon.java`, `BlueDragon.java`, `BrassDragon.java`, `BronzeDragon.java`, `CopperDragon.java` | One-line subclasses that call `super(COLOUR)`. | 46 to 47 each |
| `MOBS/DrowElf.java` | Drow base: racial stats, darkness spell every four combat ticks. | 135 |
| `MOBS/DrowWarrior.java` | Drow fighter: magic resistance, weapon set-ups, random prayers. | 321 |
| `MOBS/DrowPriestess.java` | Drow cleric: mace, healing fallback. | 263 |
| `MOBS/DrowWizard.java` | Drow mage: quarterstaff, faster casting. | 257 |
| `MOBS/Doppleganger.java` | Shape-copying monster. | 144 |
| `MOBS/CombatDummy.java` | Invulnerable training target that can be picked up. | 124 |
| `MOBS/Cow.java` | Endless milk source, implements `Drink`. | 204 |
| `MOBS/Assassin.java` | Pre-equipped thief, extends `GenMob`, not `StdMOB`. | 110 |
| `MOBS/FrostGiant.java`, `MOBS/FireGiant.java` | Giants with a fixed kit and an `Aggressive` behaviour. | 92, 84 |
| `MOBS/Cat.java` | The cleanest example of the animal template. | 78 |
| `MOBS/Centipede.java`, `MOBS/FlyingInsect.java`, `MOBS/Bee.java` | Poison-using insects. | 85, 88, 82 |

Code read to explain these classes but outside the bucket: `Libraries/Dice.java` (`roll` 398-407, `rollPercentage` 409-412, `rollHP` 80-86), `Libraries/CoffeeLevels.java` (`getLevelHitPoints` 80-91, `getLevelMOBArmor` 119-126, `getPowerLevel` 143-159, `fillOutMOB` 268-300), `core/CMath.java` (`div` 433-448, the expression operator `?` 1474-1475), `core/CMProps.java` (`DEFAULT_MOB_HP_BASE` 309, `TIME_TICK` 681, `getMobHPBase` 1487-1492, `TICKTIME` 2720-2722), `coffeemud.ini` (2334-2337), `core/interfaces/Tickable.java` (`TICKID_MOB` 82), `MOBS/StdMOB.java` (`bringToLife` 1281-1299, `fetchRandomAbility` 5035-5040, `addNonUninvokableEffect` 5237-5247, `addBehavior` 5422-5430), `MOBS/GenMob.java` (44-82), `Libraries/CMAbleMap.java` (`lowestQualifyingLevel` 984), `core/interfaces/MOBPossessor.java`.

Other types in the family, in other buckets: `WhiteDragon`, `GreenDragon`, `RedDragon`, `SilverDragon`, `GoldDragon` (the other five colours), `StdLibrarian`, `StdPostman`, `StdAuctioneer`, `StdCraftBroker`, `StdBanker`, `StdDeity` and the `Gen*` variants, and the rideable mobs (`StdRideable`, `GenRideable`).

## Grouping

| Group | Classes | What the group adds |
|---|---|---|
| Service-chain interfaces | `Auctioneer`, `CraftBroker`, `Librarian`, `PostOffice`, `Banker` | Shopkeeper roles with a chain name, so branches share one ledger. |
| Deity | `Deity` | A god-level mob for worship, rituals and blessings (see b173). |
| Dragons | `Dragon`, `BlackDragon`, `BlueDragon`, `BrassDragon`, `BronzeDragon`, `CopperDragon` | One class parameterised by colour and age, with a stomach and breath weapons. |
| Drow | `DrowElf`, `DrowWarrior`, `DrowPriestess`, `DrowWizard` | A racial base class with three role subclasses. |
| Giants | `FrostGiant`, `FireGiant` | Large, aggressive, hand-kitted melee mobs. |
| Special purpose | `Doppleganger`, `CombatDummy`, `Cow`, `Assassin`, `Citizen`, `AngryCitizen` | Mobs whose behaviour is unusual enough to need code: copying, invulnerability, infinite supply, pre-equipped thief, town crowd. |
| Animals and insects | `Cat`, `Dog`, `Coyote`, `Fox`, `Cobra`, `Bee`, `Centipede` and about 25 others | Mostly one constructor template. Data-driven in practice. |
| Deer family | `Deer`, `Buck`, `Doe` | `Buck` and `Doe` only change the gender. |

## How it works

### 1. Service-chain interfaces

All five service interfaces extend `ShopKeeper` (`Auctioneer.java:52`, `CraftBroker.java:48`, `Librarian.java:51`, `PostOffice.java:50`, `Banker.java:50`). The shop behaviour of the `ShopKeeper` interface itself is in `core/interfaces/ShopKeeper.java` and was not read here. Each one adds a *chain* name, a string shared by every branch that uses the same ledger. Examples are `auctionHouse()` (`Auctioneer.java:60`), `brokerChain()` (`CraftBroker.java:56`), `libraryChain()` (`Librarian.java`) and `postalChain()` (`PostOffice.java:162`). A *branch* identifies one physical NPC in the chain. `postalBranch()` (`PostOffice.java:181`) returns it, and the comment says it should be unique per post office.

The javadoc explains each role. The text is paraphrased here.

- **Auctioneer** (`Auctioneer.java:35-52`). An auctioneer is a shopkeeper that asks buyers to bid higher. Other players supply the items. The winner receives the item and the money changes hands automatically, even if they are offline. The bidding logic is in `Common/interfaces/AuctionPolicy.java`, which was not read.
- **CraftBroker** (`CraftBroker.java:35-47`). The broker lists requests with prices. A customer who holds a matching item sells it to the broker at the listed price, and the broker keeps a commission. The API is `commissionPct()` (103), `setCommissionPct()` (110), `maxListings()` and `maxTimedListingDays()` (72-88).
- **Librarian** (`Librarian.java:37-50`). Players borrow items from a shared stock. Borrowed items that are not returned in time are reclaimed and the borrower is charged. The constants are:

  | Constant | Value | Line | Meaning from the javadoc |
  |---|---|---|---|
  | `DEFAULT_MIN_OVERDUE_CHARGE` | 1.0 | 57 | Minimum base-currency charge the moment an item becomes overdue |
  | `DEFAULT_PCT_OVERDUE_CHARGE` | 0.1 | 64 | Share of the item's value charged at that moment |
  | `DEFAULT_MIN_OVERDUE_DAILY` | 1.0 | 70 | Minimum charge for each further overdue day |
  | `DEFAULT_PCT_OVERDUE_DAILY` | 0.1 | 77 | Share of value charged for each further overdue day |
  | `DEFAULT_MIN_OVERDUE_DAYS` | 3 | 84 | Mud-days before an item counts as overdue |
  | `DEFAULT_MAX_OVERDUE_DAYS` | 15 | 92 | Mud-days before automatic return and charge |
  | `DEFAULT_MAX_BORROWED` | 5 | 98 | Items one person may hold at once |

  `CheckedOutRecord` (106-128) holds `playerName`, `itemName`, `mudDueDateMs`, `mudReclaimDateMs` and `charges`. `getBaseLibrary()` (323) returns the chain's full book stock, as opposed to the current shop shelf, whose contents change as books go out.
- **PostOffice** (`PostOffice.java:38-49`). A post office holds a box per player or clan, moves mail between boxes, and can serve a whole clan or one player. It handles several charges, including cash on delivery. The methods are:
  - `getSenderName(mob, func, checked)` (64). Returns the player's name or the clan name. Clan mail needs clan privilege.
  - `addToBox`, `delFromBox`, `emptyBox`, `findBoxContents` (77-105). Low-level box operations.
  - `createBoxHere(boxName, forward)` and `getOurOpenBoxes` (124-134). Each box has a forwarding address.
  - Postage: `minimumPostage()` (197), `postagePerPound()` (212), `holdFeePerPound()` (228), `feeForNewBox()` (243), `maxMudMonthsHeld()` (258). The last is the number of mud-months a parcel waits before it goes on sale.
  - `MailPiece` (275) holds `from`, `to`, `time`, `cod`, `classID` and `xml`. The item is stored as XML inside the record.
- **Banker** (`Banker.java:50`). Accounts keyed by player or clan, interest on coins, items and loans. The mechanics are in b173, section 14.
- **Deity** (`Deity.java:49`). Extends `MOB`. Gods have worship and cleric requirements, rituals, blessings and curses. Mechanics are in b173, section 14.

The implementations of all of these are in other buckets. Only the contracts are in this one.

### 2. Dragons

#### 2.1 Data model

A dragon has no fields for age or colour. Both live in fields that every mob already has.

- **Colour** is `basePhyStats().ability()` (`Dragon.java:82` read, `109` written). Colours are the constants `WHITE=0` to `GOLD=9` (68-77). `DRAGONCOLORCOUNT = 10` is at line 65.
- **Age** is `basePhyStats().level() / 10` (`Dragon.java:85-88`). The constructor sets level to `5 + age*10` (110). Ages are the constants `HATCHLING=0` to `ANCIENT=8` (55-63). An ancient dragon therefore has level 85.
- Other fields are `breatheDown=4`, `swallowDown=5`, `digestDown=4` and `dmgBonus` (44-47), the birth values `birthColor` and `birthAge` (49-50), the cached `dragonbreath` ability (52) and the stomach `myStomachR` (90).

#### 2.2 Lazy, self-guarding set-up

The constructor only records colour and age, then calls `setupDragonIfNecessary()` (`Dragon.java:116-239`). That method does the real work:

1. It returns early unless the host is running (120-121) and the current age and colour differ from `birthAge` and `birthColor` (122-123). Once set up, the object is not rebuilt until its age or colour changes.
2. It sets gender at random (131-139), a name such as "an adult Red Dragon" and a description (141-143).
3. It wields two `DragonClaw` weapons, each at the dragon's level (146-162).
4. It applies `PointMod` and an alignment, both chosen by colour (165-214).
5. It calls `CMLib.leveler().fillOutMOB(this, level+3)` (217), then resets the level to the real value (218). This fills in speed, armour, damage, money and hit points from the leveller.
6. It multiplies hit points and money by `PointMod` (219-220). It sets weight to `1500 * age` (221), sets wimp to 0 so dragons never flee (224) and sets every base stat to `13 + age*2` (227-228). It then sets the race to `Dragon` (229-230).

`recoverPhyStats()` calls the same method (576-581). The birth fields break the recursion, because `setupDragonIfNecessary` calls `recoverPhyStats()` itself at line 237.

The colour table, read from lines 168-214 (alignment and `PointMod`), 426-489 (breath), 591-616 (saves), and `getColorDescription` (310-353):

| Colour (index) | Class | Breath message (short form) | Damage type | Save boosted by +100 | Alignment | `PointMod` |
|---|---|---|---|---|---|---|
| White (0) | `WhiteDragon` (other bucket) | "breathes frost at" | cold | cold | EVIL | 1 |
| Black (1) | `BlackDragon` | "spits acid at" | acid | acid | EVIL | 2 |
| Blue (2) | `BlueDragon` | "Lightning shoots forth" | electric | electric | EVIL | 3 |
| Green (3) | `GreenDragon` (other bucket) | "a cloud of noxious vapors" | gas | gas | EVIL | 4 |
| Red (4) | `RedDragon` (other bucket) | "torches ... with fiery breath" | fire | fire | EVIL | 5 |
| Brass (5) | `BrassDragon` | "cooks ... with a blast of pure heat" | fire | fire | GOOD | 1 |
| Copper (6) | `CopperDragon` | "spits acid at" | acid | acid | GOOD | 2 |
| Bronze (7) | `BronzeDragon` | "Lightning shoots forth" | electric | electric | GOOD | 3 |
| Silver (8) | `SilverDragon` (other bucket) | "breathes frost at" | cold | cold | GOOD | 4 |
| Gold (9) | `GoldDragon` (other bucket) | fire or gas, 50% each | fire and gas | fire and gas | GOOD | 5 |
| 10 (reachable, see Problems) | none | no breath, returns false (487-488) | none | none | NEUTRAL | 3 |

`PointMod` multiplies hit points and money. Evil colours get higher multipliers as they go up the list, and good colours do the same. Gold gets two saves because its breath is random.

#### 2.3 Age

`determineAge()` (`Dragon.java:241-266`) rolls `CMLib.dice().rollPercentage()+1` (244). The thresholds then map the roll to an age. `rollPercentage()` returns 1 to 100 (`Libraries/Dice.java:409-412`), so the roll is 2 to 101. That makes the `iRoll == 1` test for hatchlings impossible, and roll 101 falls through to the final `return SUBADULT` (265). The effective distribution is:

| Age | Roll range | Share |
|---|---|---|
| HATCHLING | never (test at 247 is unreachable) | 0% |
| VERYYOUNG | 2 to 10 | 9% |
| YOUNG | 11 to 25 | 15% |
| SUBADULT | 26 to 45, plus 101 | 21% |
| YOUNGADULT | 46 to 67 | 22% |
| ADULT | 68 to 82 | 15% |
| OLD | 83 to 92 | 10% |
| VERYOLD | 93 to 97 | 5% |
| ANCIENT | 98 to 100 | 3% |

The comments on the age constants (`Dragon.java:55-63`) say 10, 10, 15, 20, 22, 15, 10, 5 and 3. Those add up to 110, so they do not describe the code.

#### 2.4 Combat cycle

`tick()` (`Dragon.java:355-401`) runs only on the mob tick (`Tickable.TICKID_MOB`, `core/interfaces/Tickable.java:82`). The default tick is 4000 ms (`core/CMProps.java:681`, set by `TICKTIME` at 2720). Dragons run four periodic checks:

- **Stomach creation** (363-379). Once `DragonAge() >= ADULT` (365), an empty `StoneRoom` is created and named "Dragon Stomach". Its description is a short, neutral text about being digested.
- **Digestion** (380-384). Every four ticks, always, `digestTastyMorsels()` runs.
- **Breath** (385-390). In combat, every four ticks, `useBreathWeapon()` runs.
- **Swallowing** (392-396). In combat, every four ticks, `trySwallowWhole()` runs. The first swallow attempt comes at tick five, because `swallowDown` starts at 5.

`useBreathWeapon()` (403-521) works like this:

- It returns without breathing if the dragon is following someone (413-417), or if `CMLib.flags().canBreatheHere` says no (419-422).
- It picks a message and damage type by colour (426-489). An example message is "The dragon breathes frost at <T-NAME>." (429).
- It walks the room's inhabitants (494-519). Each one that is not the dragon gets a `CMMsg` with the malicious-move mask and the damage type. The room must accept it through `okMessage` (508) before damage is posted.
- Damage has two formulas, depending on the message value after the room sends it. The normal case is `round(random * 7 * age / 2)` (511), which averages about 3.5 times the age. The other case is `round(random * 7) * age` (513). Damage is posted with the `Dragonbreath` ability (516).

`trySwallowWhole()` (528-574) takes its target from the current victim. The target must be alive, awake, mobile and unbound, at range 0, and able to hear, see or smell the dragon (533-535). It must weigh less than 1500 (540). The roll is `round(random*99) < 2` (543-546), about 2% per attempt. A success sends the target a `MSG_EAT` message whose text reads "<S-NAME> swallow(es) <T-NAMESELF> WHOLE!" (556). It then moves them into the stomach (563-564) and sets their hunger to its maximum (562).

`digestTastyMorsels()` (619-651) runs in the stomach room. Each morsel takes `damage = dmgBonus + round(hp/5)` (638). If that is below the morsel's level plus 6, damage becomes its current hit points plus one, which kills it (639-640). Damage is halved if the digest message value is non-zero (641-642). `dmgBonus` rises by one for each tick with morsels present (648-649) and resets to 0 when the stomach is empty (646-647). The acid message reads "The stomach acid <DAMAGE> <T-NAME>!" (643).

`killMeDead()` (653-688) moves all morsels and stomach items into the dragon's room before the body is buried. That makes death a clean way out for swallowed victims.

Because `Dragon` implements `MOBPossessor` (`Dragon.java:36`, `core/interfaces/MOBPossessor.java:11`), its inhabitant methods (690-787) all delegate to the stomach. Generic code that walks a mob's contents therefore sees swallowed victims as if they were in a room. The callers were not traced.

#### 2.5 Stats after set-up

`recoverCharStats()` (583-617) adds magic save `5 * age`, capped at age 20 (587-590). Each colour then adds a `+100` save for its element (591-616), as in the colour table.

#### 2.6 The five dragon subclasses in this bucket

Each of `BlackDragon`, `BlueDragon`, `BrassDragon`, `BronzeDragon` and `CopperDragon` has an `ID()` and a constructor that calls `super(COLOUR)`, and nothing else. For example, `BlackDragon.java:44` is `super(BLACK);`. Their colour, breath, save, alignment and `PointMod` come from the table in 2.2. Random age comes from `determineAge()`.

### 3. The drow family

#### 3.1 `DrowElf`, the base

- **Level** is `4 + |random % 7|` (`DrowElf.java:54`), so 4 to 10.
- **Speed** is 2.0 (74). **Senses** are dark and infrared (75). **Wimp** is 5 (72).
- **Stats** are 12 to 17 for strength and constitution, 14 to 19 for intelligence, 13 to 18 for wisdom and charisma, and 15 to 20 for dexterity (82-87).
- **Darkness** is cast every four combat ticks (`tick` 98-113, `darkDown` 46, 104-107). `castDarkness()` (115-133) does nothing if the room is already dark (119). Otherwise it adds `Spell_Darkness` at 100 proficiency (122-126), and invokes it (131).
- **Money** is `round(50 * level / (random % 10 + 1))` (69). See Problems for the sign bug.

#### 3.2 `DrowWarrior`

- **Level** is `CMLib.dice().roll(4,6,1)` (57), so 4d6+1, giving 5 to 25.
- **Magic resistance** is `50 + 2*level` (59), so 60 to 100. In `okMessage` (228-244), a malicious spell that targets the warrior is refused if `rollPercentage() <= magicResistance` (237). The source is told "The drow warrior resisted your spell!" (239). The check runs after `super.okMessage` (231), so the normal veto checks come first.
- **Weapons** (`equipDrow`, 93-144). It wears `DrowChainMailArmor` on the torso (95-100). A d4 roll (105) picks one of four kits:
  - 1: two `DrowSword`s, speed 2.0.
  - 2: one `DrowSword`, speed 1.0.
  - 3: a `DrowSword` and a `DrowDagger`, speed 2.0.
  - 4: two `Scimitar`s, speed 2.0.
- **Natural abilities** (`addNaturalAbilities`, 146-226). It adds `Spell_Darkness` at 100 proficiency (148-154). It also adds 14 prayers, each at proficiency `roll(5,10,50)`, which is 55 to 100 (156-224). None are savable. The prayers are listed in Content.
- **Timing** (`tick` 246-272). In combat, `useSkill()` runs every two ticks (`fightDown=2`, 253-257). `checkStatus()` runs every three (`statCheck=3`, 258-262). Darkness runs on the usual timer (263-266). `super.tick` runs last (271), and that is where the double decrement comes from (see Problems).
- **`checkStatus`** (274-281). It clears the sitting bit if it is set (276-277). It then shows "<S-NAME> stand(s) up, ready for more combat." (278). That line is outside the `if`, so the message is shown every three combat ticks even when the warrior is not sitting.
- **`useSkill`** (283-298). With probability 70%, it picks a random ability from the mob's full ability list (`fetchRandomAbility`, `StdMOB.java:5035-5040`). It re-picks up to nine times while the ability's `lowestQualifyingLevel` (`CMAbleMap.java:984`) is above the warrior's level (286-291). Otherwise it casts `Prayer_CureSerious` (294). The result is cast with `invoke(this, null, false, 0)` (296).

#### 3.3 `DrowPriestess`

- Level, magic resistance and natural abilities follow the warrior pattern. `magicResistance = 50 + 2*level` (54). Wimp is 1 (65). Armour is 40 (60). The class is `Cleric` (74), and the weapon is a `Mace` (54-59). Gender is female (64). Speed is 1.0 (78).
- **Timing** (`tick` 199-218). A spell is cast every three ticks (`spellDown=3`, 43, 205-208). Darkness uses the shared timer (210-213).
- **`castSpell`** (221-242). With probability 70%, it picks a random ability as in 3.2. Otherwise it casts `Prayer_CureSerious` at `roll(5,10,50)` proficiency (233-234).
- **Resistance message** (189-191): "The drow priestess resisted your spell!"

#### 3.4 `DrowWizard`

- The class is `Mage` (73), the weapon is a `Quarterstaff` (54-59), armour is 40 (60), gender is male (64), speed is 1.0 (77), and wimp is 1 (65).
- **Timing** (`tick` 198-217). A spell is cast every two ticks (`spellDown=2`, 42, 204-206).
- **`castSpell`** (220-236) has the same 70/30 structure. It has no cure fallback that sets proficiency.
- **Resistance message** (190) reads "The drow warrior resisted your spell!". The text says "warrior" but the class is a wizard. See Problems.

The three subclasses share a 14-prayer list and the darkness routine, but each has a different tick rhythm. The three subclasses decrement the same `darkDown` counter twice per tick (see Problems).

### 4. `Doppleganger`

- **Start state** (`revert()`, `Doppleganger.java:52-84`). Called from the constructor (49). It sets the default name and description (55-57), base stats from `DefaultPhyStats`, `DefaultCharStats` and `DefaultCharState` (58-60), evil alignment (61), money 250 (62), level 6 (72), damage 7 (69), speed 2.0 (70), armour 70 (73) and `Mobile` and `MudChat` behaviours (77-78). Hit points are `roll(level,20,level)` (75).
- **Copy trigger** (`okMessage` 111-140). Any malicious message that targets it, when it has no current copy, makes it take on the attacker's form. The sequence is:
  - a visible "<S-NAME> take(s) on a new form!" (127),
  - `mimicing = msg.source()` (128),
  - `_name = mimicing.Name()` (129), then the attacker's display text and description (130-131),
  - a `copyOf()` of the attacker's base physical stats, character stats and state (132-134),
  - a recover and a full reset to max (135-138), then `ticksSinceMimicing = 0` (139).
- **Clearing the copy** (117-124). The copy is dropped only if the attacker's victim is someone other than the doppleganger, or the attacker has left the room.
- **Timeout** (`tick` 87-102). Each mob tick with a live copy adds to `ticksSinceMimicing` (93). Past 500 it calls `revert()` (94-96). At the default 4-second tick, 500 ticks is about 33 minutes.
- **Death** (`killMeDead` 104-108) reverts the form.

The copy is a real identity swap. The doppleganger takes the attacker's name, description and stats, including the stats of a player character. The clearing logic has a bug. See Problems.

### 5. `CombatDummy`

- **Fixed stats.** Alignment neutral (51). Money 0 (52). Wimp 0 (53). Damage 0 (55). Race `WoodGolem` (57). Level 4 (61). Armour 80 as a starting value (62). Senses add `CAN_NOT_MOVE`, `CAN_NOT_SPEAK` and `CAN_NOT_THINK` (63).
- **Armour follows the level.** `executeMsg` (74-84) recomputes armour from `CMLib.leveler().getLevelMOBArmor` (`CoffeeLevels.java:119-126`) whenever the level differs from the last one seen (77-81). The formula is `102 - round((level + mid) * (level / low))`, where `mid = LASTPLAYERLEVEL * 0.44` and `low = mid / 2`. Higher levels give lower numbers, which means better armour in this code base.
- **Full heal.** `executeMsg` sets current hit points to maximum after every message (83).
- **Cannot die.** In `okMessage` (115-120), a death message from itself is refused and the dummy is reset to max, unless it has the `Dueling` ability.
- **Picking it up** (89-110). A failed command (`TYP_COMMANDFAIL`) whose text starts with G and parses as a `get` of this dummy's name (93-102) is turned into a `GenCaged` item. The dummy then sets the cage flag, posts a `postGet` for the source, kills itself with `killMeDead(false)` (110), and the veto returns false (111). The player ends up holding the dummy as an object.

### 6. `Cow`

- **Milk source.** `Cow implements Drink` (`Cow.java:35`). Drinking from the cow is accepted in `okMessage` (86-88). `executeMsg` (95-105) reports "You are no longer thirsty." (101) or "You have drunk all you can." (104), depending on the thirst state.
- **Quench and capacity.** `thirstQuenched()` is 100 (133-135). `liquidHeld()` and `liquidRemaining()` are `Integer.MAX_VALUE - 1000` (139-147). `decayTime()` is 0 (73-75). `setDecayTime` is empty.
- **Filling a container** (108-128). A `TYP_FILL` message whose tool is the cow and whose target is a container with capacity makes a fresh `GenLiquidResource` called "some milk". It is added to the container's owner, which is either a mob or a room, with the container set as its holder. Nothing is ever used up.

### 7. `Assassin`, `Citizen` and `AngryCitizen`

- **`Assassin`** (`Assassin.java:35-107`) extends `GenMob`. That makes it a data-driven base class with a hand-coded constructor. It is Human, with dexterity and wisdom both 18 (`Assassin.java:55`, `57`). It sees in the dark (58). It gets four thief skills at 100 proficiency: `Thief_Hide`, `Thief_Sneak`, `Thief_BackStab` and `Thief_Assassinate` (60-78). It wields a `Longsword`, wears `LeatherArmor` and holds a `Dagger`. It has a `CombatAbilities` behaviour (107). Its persistence path is described in Problems.
- **`Citizen`** (`Citizen.java:35-71`) is a GOOD-aligned Human, level 1. Gender is male if `rollPercentage() > 50`, otherwise female (48). Intelligence is 7 (55). Money is 10. Wimp is 0.
- **`AngryCitizen`** (`AngryCitizen.java:35-77`) is EVIL, level 1, Human. It wields a `ThrowingStone` (54). Intelligence is 6 and charisma 4 (61-62). Line 75 assigns `this.charStats` directly. No other class in this bucket does that.

### 8. `FrostGiant` and `FireGiant`

- **`FrostGiant`** (`FrostGiant.java:35-92`) is level 12, damage 19, armour 0, strength 29, dexterity 9, and weight `3500 + roll(1,1000,0)` (51). It is EVIL with race `Giant`. It has `Immunities` set to COLD and `Chant_FeelHeat`, both added as non-uninvokable effects (67-75). It is `Aggressive` (77) and wields a `BattleAxe` (79-84).
- **`FireGiant`** (`FireGiant.java:35-84`) is level 15, damage 20, armour -10, strength 20, and weight `6500 + |random % 1001|`. It is EVIL with race `Giant` and `Aggressive` (69). `recoverCharStats()` adds 100 to the fire save after every recompute (77-82).

### 9. Animals and simple monsters

All of these extend `StdMOB`. The constructor sets name, description, display text, faction, money, wimp, damage, speed, level, armour, race, behaviours and abilities, then calls `roll(level,20,level)` for hit points and the four recover calls. `Cat.java:43-76` is the template. Its gender, race, behaviour list and weight are set in the same order as the others.

Behaviours used in the bucket:

| Behaviour | Classes |
|---|---|
| `Follower` | Cat, Dog, Coyote, Duck, Bee |
| `MudChat` | Cat, Dog, Coyote, Duck, Doppleganger |
| `Mobile` | Centaur, FlyingInsect, Doppleganger |
| `CombatAbilities` | Cobra, BrownSnake, Bee, FlyingInsect, Centipede, Assassin |
| `Aggressive` | FrostGiant, FireGiant |

Poison abilities: `Poison` (Cobra, BrownSnake), `Poison_Sting` (FlyingInsect, Centipede), `Poison_BeeSting` (Bee). The poison code itself was not read.

Hit points come from three different formulas across the bucket:

- `roll(level,20,level)`, used by most classes.
- `roll(level,11,level)`, used by Alligator (`Alligator.java:63`) and Dolphin (`Dolphin.java:63`).
- `rollHP(level,20)`, used by Crocodile (`Crocodile.java:62`), Elephant (`Elephant.java:62`) and Frog (`Frog.java:62`). This goes through the NPC formula. It is random because the formula uses the `?` operator (`CMath.java:1474-1475`). The default formula is `3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))` (`coffeemud.ini:2337`), and the comment says the second variable is "usually 11" (2335).

The full roster is in Content.

### 10. The deer family

`Buck` and `Doe` extend `Deer` and change nothing except gender. `Buck` sets male (`Buck.java:49`), then calls `recoverCharStats()` (50) before `startRacing` (51). The race is already set by `Deer`, so the order makes no difference in practice.

## Content worth mining

### Dragon colour and breath

See the colour table in 2.2. It is a compact, self-contained table of five elemental breath attacks, their matching resistances and alignment-based hit-point multipliers. It is a good model for any creature family with a few variants.

### Dragon ages

| Constant | Level range | Name used in the description |
|---|---|---|
| HATCHLING (0) | 5 | "a hatchling" |
| VERYYOUNG (1) | 15 | "a very young" |
| YOUNG (2) | 25 | "a young" |
| SUBADULT (3) | 35 | "a sub-adult" |
| YOUNGADULT (4) | 45 | "a young adult" |
| ADULT (5) | 55 | "an adult" |
| OLD (6) | 65 | "an old" |
| VERYOLD (7) | 75 | "a very old" |
| ANCIENT (8) | 85 | "an ancient" |

Level ranges are `5 + 10*age` (`Dragon.java:110`). The names come from `getAgeDescription` (268-308).

### Drow prayers

The natural list in `DrowWarrior.java:146-226`, which `DrowPriestess` and `DrowWizard` share:

| Ability | Note |
|---|---|
| `Spell_Darkness` | Added at 100 proficiency, not savable |
| `Prayer_ProtGood` | |
| `Prayer_CauseLight` | |
| `Prayer_CauseSerious` | |
| `Prayer_Curse` | |
| `Prayer_Paralyze` | |
| `Prayer_DispelGood` | |
| `Prayer_Plague` | |
| `Prayer_CauseCritical` | |
| `Prayer_Blindness` | |
| `Prayer_BladeBarrier` | |
| `Prayer_Hellfire` | |
| `Prayer_UnholyWord` | |
| `Prayer_Deathfinger` | |
| `Prayer_Harm` | |
| `Prayer_CureSerious` | Fallback cast, not on the natural list |

Each prayer is given at `roll(5,10,50)` proficiency, 55 to 100, so the same prayer varies from one drow to the next.

### Animal roster

Values come from each class's constructor. Armour is the `setArmor` value. Lower is better in this code base, which follows from `getLevelMOBArmor` (`CoffeeLevels.java:119-126`), though that is an inference. Damage is `setDamage`. A dash means the value was not set in the constructor and the `StdMOB` default applies.

| Class | Level | Armour | Damage | Speed | Race | Behaviours | Abilities and notes |
|---|---|---|---|---|---|---|---|
| Cat | 1 | 90 | 4 | - | Cat | Follower, MudChat | Template class |
| Dog | 1 | 90 | 4 | - | Dog | Follower, MudChat | |
| Coyote | 1 | 90 | 4 | - | Dog | Follower, MudChat | |
| Duck | 1 | 90 | 4 | - | WaterFowl | Follower, MudChat | Weight 5 + random % 10 |
| Fox | 5 | 70 | 6 | 1.0 | Fox | - | |
| Cobra | 1 | 90 | 4 | - | Cobra | CombatAbilities | `Poison` |
| BrownSnake | 2 | 90 | 4 | - | Snake | CombatAbilities | `Poison` |
| Alligator | 1 | 90 | 8 | - | Alligator | - | Name typo "a alligator" (`Alligator.java:46`); HP `roll(L,11,L)` |
| Dolphin | 1 | 90 | 4 | - | Dolphin | - | HP `roll(L,11,L)` |
| Crocodile | 5 | 70 | 30 | - | Crocodile | - | HP `rollHP(L,20)` |
| Elephant | 5 | 70 | 20 | - | Elephant | - | HP `rollHP(L,20)` |
| Frog | 1 | 90 | 6 | - | Frog | - | HP `rollHP(L,20)` |
| Chimp | 4 | 80 | 1 | - | Chimp | - | |
| Ape | 6 | 70 | 1 | - | Ape | - | |
| Bull | 7 | 90 | 10 | 2.0 | Bull | - | Wimp 0 |
| Buffalo | 3 | 90 | 1 | 1.0 | Buffalo | - | Wimp 0 |
| Chicken | 1 | 90 | 1 | 1.0 | Chicken | - | Female |
| Deer | 2 | 90 | 1 | 2.0 | Deer | - | Female, wimp 0 |
| Buck | 2 | 90 | 1 | 2.0 | Deer | - | Male, from `Deer` |
| Doe | 2 | 90 | 1 | 2.0 | Deer | - | Female, from `Deer` |
| Beaver | 1 | 90 | 4 | - | GiantRat | - | `Chopping` |
| BlackBear | 3 | 80 | 6 | 1.0 | Bear | - | Weight 250 + random % 55 (overrides a first setting of 20 + random % 45) |
| BrownBear | 5 | 70 | 8 | 2.0 | Bear | - | Weight 450 + random % 55 (overrides a first setting) |
| Cheetah | 3 | 80 | 8 | 2.0 | GreatCat | - | Weight 150 + random % 55 (overrides a first setting) |
| Cougar | 5 | 80 | 10 | 2.0 | GreatCat | - | Weight 100 + random % 55 |
| CommonBat | 1 | 90 | 1 | 1.0 | Bat | - | Flying |
| Falcon | 2 | 90 | 1 | 3.0 | GreatBird | - | Flying; fastest speed in the bucket |
| DireWolf | 4 | 80 | 6 | 1.0 | DireWolf | - | |
| Centaur | 4 | 80 | 7 | 2.0 | Centaur | Mobile | GOOD; money 200 |
| Bugbear | 3 | 70 | default | default | none set | - | Halberd; EVIL; strength 22, intelligence 6, charisma 2 |
| Bee | 1 | 80 | 1 | - | Insect | Follower, CombatAbilities | `Poison_BeeSting`; flying |
| FlyingInsect | 1 | 90 | 10 | 1.0 | Insect | Mobile, CombatAbilities | `Poison_Sting`; flying |
| Centipede | 1 | 90 | 10 | 1.0 | Centipede | CombatAbilities | `Poison_Sting`; no `Mobile`, so it stays put |
| Cow | 2 | 90 | 1 | 1.0 | Cow | - | `Drink` source (section 6) |
| Citizen | 1 | 90 | default | default | Human | - | GOOD; money 10 |
| AngryCitizen | 1 | 90 | default | default | Human | - | EVIL; `ThrowingStone` |
| Doppleganger | 6 | 70 | 7 | 2.0 | copies target | Mobile, MudChat | EVIL; money 250 |
| CombatDummy | 4 | leveler | 0 | default | WoodGolem | - | Immobile, invulnerable |
| Assassin | from `GenMob` | from kit | from kit | default | Human | CombatAbilities | Four thief skills at 100 |
| FrostGiant | 12 | 0 | 19 | 1.0 | Giant | Aggressive | `Immunities` (COLD), `Chant_FeelHeat`; strength 29 |
| FireGiant | 15 | -10 | 20 | 1.0 | Giant | Aggressive | Fire save +100 after recompute |

### Service-chain constants

The `Librarian` defaults in section 1 can be reused directly as tuning numbers. Overdue charges start at 1.0 plus 10% of value and then accrue daily at the same rates. Items are reclaimed after 15 mud-days, and each person may hold five at once.

### Short messages

Game text quoted as a model for tone. Each is short and mechanical.

- "The dragon breathes frost at <T-NAME>." (`Dragon.java:429`)
- "<S-NAME> swallow(es) <T-NAMESELF> WHOLE!" (`Dragon.java:556`)
- "The stomach acid <DAMAGE> <T-NAME>!" (`Dragon.java:643`)
- "<S-NAME> stand(s) up, ready for more combat." (`DrowWarrior.java:278`)
- "The drow priestess resisted your spell!" (`DrowPriestess.java:191`)
- "<S-NAME> take(s) on a new form!" (`Doppleganger.java:127`)
- "You have drunk all you can." (`Cow.java:104`)
- "some milk has been left here." (`Cow.java:115`)

## Notable design ideas

1. **Age and colour piggyback on existing fields.** Age is `level / 10` and colour is `ability()`, so `Dragon` needs no new data. The cost is an overload that causes trouble (see Problems), but the idea of encoding a creature's variant in a field its family already has is useful.
2. **Lazy, self-guarding set-up.** `setupDragonIfNecessary` remembers the age and colour it last built for (`Dragon.java:120-123`). Any recompute rebuilds only when those change. A builder who edits a dragon's level or colour gets the right result the next time stats are recalculated.
3. **A stomach as a sub-location.** Swallowed victims move into a room owned by the dragon. The dragon proxies its inhabitant methods to that room (`Dragon.java:690-787`), and death spills the contents out (653-688). Eating and being inside are modelled as containment, not as a special state.
4. **A colour table drives three systems.** One switch sets breath text and damage type (`Dragon.java:426-489`), a second sets alignment and hit-point multiplier (165-214), and a third sets the matching save (591-616). Adding a colour means adding a row in each.
5. **Magic resistance as a veto.** `DrowWarrior.okMessage` refuses a malicious cast with probability equal to its resistance. The check runs in the offer phase, so the spell never lands (`DrowWarrior.java:237-241`).
6. **Natural-ability rosters with random proficiency.** Each drow gets its prayer list at a random proficiency from `roll(5,10,50)`. The variety comes from one line of code rather than per-individual data.
7. **Shape-copying by value.** The doppleganger copies the attacker's stat objects with `copyOf()` (`Doppleganger.java:132-134`). It takes on the attacker's whole stat set, not just looks.
8. **A combat dummy that becomes an item.** `CombatDummy` turns itself into a `GenCaged` object and hands it to a player who types "get" (`CombatDummy.java:89-110`). A training target the player can carry away is a neat game-design move.
9. **An endless source.** `Cow` creates a fresh milk resource whenever a container is filled from it (`Cow.java:108-128`). The mob is a supply node, not a creature with a finite drink.
10. **Chains with forwarding and overdue rules.** `PostOffice` boxes carry forwarding addresses (`PostOffice.java:124-134`). `Librarian` tracks due and reclaim dates per borrowed item (`Librarian.java:106-128`). Each keeps its state in a chain-wide ledger, not in the NPC.

## Problems and cruft

These were found by reading. None were run.

1. **Dragon age is off by one.** `determineAge()` adds one to `rollPercentage()`, which already returns 1 to 100 (`Dragon.java:244`, `Libraries/Dice.java:409-412`). The test `iRoll == 1` is therefore unreachable, so no randomly generated dragon is a hatchling (`Dragon.java:247`). Roll 101 falls through to `SUBADULT` (265), which takes 21% of rolls. The trailing comments (`Dragon.java:55-63`) do not match the code.
2. **Dragon colour can be 10.** `Math.round(Math.random()*DRAGONCOLORCOUNT)` returns 0 to 10 (`Dragon.java:96`). The colour names cover 0 to 9 (68-77). Colour 10 gets the default branches. It is named "Unknown" (347-349), gets `PointMod` 3 and neutral alignment (210-212), and has no breath (487-488). About 5% of `new Dragon()` calls produce this.
3. **Claw damage is assigned to the wrong claw.** `Dragon.java:154` sets `ClawOne`'s damage a second time. `ClawTwo` never gets its own damage value, though its level is set (153).
4. **Hatchling guard is latent.** The constructor preset `birthAge = 0` and `birthColor = 0` (111-112), which is the same as a white hatchling. A white dragon at age 0 would then skip its set-up (120-123). This cannot happen through the shipped classes, because of problem 1. It can happen if a builder lowers a white dragon's level below 10.
5. **Dragon comments are stale.** The age comments add up to 110 (55-63). The comment at `Dragon.java:232` says the swallow is added for adults, but the code only clears the stomach. The comment at 118 is garbled. `BlackDragon.java:43` says the class "creates a White" dragon.
6. **Colour and HP share a field.** The colour is stored in `ability()` (`Dragon.java:109`). `CoffeeLevels.getLevelHitPoints` reads `ability()` as the NPC hit-point code (`CoffeeLevels.java:82`, `Dice.java:80-86`). `fillOutMOB` calls it (`CoffeeLevels.java:286`) from `setupDragonIfNecessary` (`Dragon.java:217`). So a dragon's base hit points depend on its colour index, and the code then multiplies them by `PointMod` (219). The result is a hidden coupling between colour and HP. The formula's `?` operator also makes the result random.
7. **Drow money can be negative.** `DrowElf.java:69` divides by `(random % 10 + 1)`. Java's `%` keeps the sign of the dividend, so the divisor is zero or negative for about 45% of draws. A simulation of the Java rule gave 0.45 non-positive and 0.05 zero. A positive numerator over zero gives infinity, which `(int)Math.round` turns into -1. `setMoney` does not clamp (`StdMOB.java:372-375`). The other drow classes use `roll` for money, so only the base `DrowElf` is affected. The `Math.abs` pattern is used elsewhere in the same class for other values.
8. **Drow darkness fires twice as often as intended.** `DrowWarrior.tick` (`DrowWarrior.java:263-266`) decrements `darkDown`, then calls `super.tick` at 271. `DrowElf.tick` (`DrowElf.java:104-107`) decrements the same field again. The result is one cast every two ticks instead of every four. The same double decrement is in `DrowPriestess.java:210-218` and `DrowWizard.java:209-217`. The effect is probably harmless because `castDarkness` returns early in a dark room (`DrowElf.java:119`), but it is not what the constants say.
9. **Spell selection can cast the wrong thing.** The loop at `DrowWarrior.java:286-291` re-picks up to nine times for a level-appropriate ability. After that it casts whatever it has, without rechecking the level (296). The pick is also drawn from every ability the mob holds (`StdMOB.java:5035-5040`), not from prayers only.
10. **The stand-up message is unconditional.** `DrowWarrior.java:278` shows "stand(s) up" every third combat tick, even when the warrior is not sitting (276-277).
11. **The wizard's resistance message names the wrong class.** `DrowWizard.java:190` says "The drow warrior resisted your spell!".
12. **The doppleganger stops re-mimicking.** `revert()` (`Doppleganger.java:52-84`) never sets `mimicing` back to null and never resets `ticksSinceMimicing`. The counter is only reset in the copy branch (139), and `mimicing` is only cleared in `okMessage` (119-123), when the attacker's victim is someone else or the attacker has left the room. Once the counter passes 500 (94), every tick calls `revert()` (96). That resets the stats and calls `resetToMaxState()` (80-81), so the doppleganger is restored to full health every tick. It stays in its default form while the original attacker remains in the room with no other victim. A new attacker triggers a copy only once the original attacker has moved on to another victim or left the room, because until then `mimicing` is still set. The likely fix is to clear `mimicing` and the counter in `revert()`.
13. **Side effects in the veto phase.** `Doppleganger.okMessage` replaces the mob's stats (125-139), and `CombatDummy.okMessage` converts, moves and kills itself (104-110), both during `okMessage`. A later veto by another listener leaves those changes in place. The dummy's version is intentional, but it is a fragile pattern.
14. **Assassin mixes two persistence models.** `Assassin` extends `GenMob`, so its hand-coded constructor coexists with the XML-in-`miscText` model. `StdMOB.bringToLife` re-applies that XML on respawn when `resetStats` is set and `isGeneric()` is true (`StdMOB.java:1281-1299`). Whether a saved Assassin reloads with its hand-coded kit or with the XML was not traced.
15. **Hit-point formulas disagree.** Three different calls set hit points across the animal set (section 9). The `rollHP(level, 20)` calls use 20 where the comment in `coffeemud.ini:2335` says the modifier is "usually 11". Only the Alligator and Dolphin use `roll(L,11,L)`.
16. **Two weight assignments per bear.** `BlackBear.java:53` and `:56`, `BrownBear.java:53` and `:56`, and `Cheetah.java:53` and `:56` each set weight twice. The second overwrites the first, so the first (20 + random) is dead code.
17. **Name typo.** `Alligator.java:46` says "a alligator". `Dolphin.java:46` is correct.
18. **Direct field write.** `AngryCitizen.java:75` assigns `this.charStats` from a copy of the base stats. No other class in the bucket does this. It skips `recoverCharStats()`, so effects are not applied.
19. **Missing null checks.** `DrowWarrior.addNaturalAbilities` (`DrowWarrior.java:156-224`) dereferences each prayer without checking it. `DrowPriestess.castSpell` (233-234) does the same with `Prayer_CureSerious`. A missing class would throw.
20. **Possible null in the dummy's pick-up.** `CombatDummy.okMessage` calls `msg.source().location()` (`CombatDummy.java:101`) and then uses the result without checking for null.

## Takeaways for a Python SUD

**Service chains.** A single-player game has no other traders to fill an auction house, a broker or a post office. Keep the roles but make the listings simulated NPC output: a timer adds items that other NPCs "sell". Post and borrowing can stay as delayed-delivery queues and due-date records, keyed by game time. Drop branch and chain identity unless the world has several branches.

**Dragons.** Model a breath table as data, one row per variant, not three parallel switches:

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class Breath:
    damage_type: str   # e.g. "acid", "fire", "frost"
    resist: str        # the same key, used for a +100 save
    alignment: str
    hp_mult: int

BREATH = {
    "black": Breath("acid", "acid", "evil", 2),
    "blue": Breath("lightning", "lightning", "evil", 3),
    # ... one row per colour, no ten-entry gap
}
```

Use an explicit weighted age table with a real hatchling entry. Compute stats once, in the constructor, from the age and colour. The lazy guard is not needed when the object is built from data. Model the stomach as a child location that the dragon owns. Death spills its contents. Digestion is a per-turn event with a growing bonus. The breath and swallow attack timers are useful, but make them explicit `next_turn` numbers, not counters that two methods decrement.

**Drow and magic resistance.** Use one caster class with role data: a spell list, a cast chance, a resist chance and a cadence. Keep a single timer per action. Do the resistance check in the veto step of the message pipeline, as the original does, so a resisted spell never resolves.

**Doppleganger.** A disguise that copies the target's full state (`copy.deepcopy`) is a good single-player idea, especially copying the player's last spell or stance. Put the timeout and the reset in one function that clears every piece of state, so the bug in Problems item 12 cannot happen.

**Training dummy.** A flag set (invulnerable, full heal each turn, cannot move) is enough for a tutorial target. The pick-up trick is a useful puzzle or joke: a dummy the player can carry as an item. Do the conversion in the command handler, not inside a veto.

**Animals.** Do not write one class per animal. Put the roster in a single data file (YAML or JSON) with level, armour, damage, speed, race, behaviours and abilities. One `Creature` class reads it, and every hit-point value uses one formula. Keep flying and immobility as flags. Delete the Deer subclasses. Use a data row with a sex field.

**Cow and resources.** A `Source` component that produces a resource on use, with an optional limit, covers the cow and any well or herb patch.

**Drop.** The chain and branch identity, clan mail, the auction house's cross-player bidding, the doppleganger's cloning of player characters, and the three hit-point formulas.

**Keep.** The idea that a creature family has one class with variant data (dragons), the breath and save table, the stomach as contained space, and the magic-resistance veto.

**Multiplayer assumptions to rethink.** `CombatDummy` assumes a second human might pick it up. The shop chains assume shared state across players. Auction, post, clan mail and the broker's listing of other players' requests all fall away.

## Coverage

- **Read in full:** `MOBS/Dragon.java` (789), `MOBS/BlackDragon.java`, `MOBS/BlueDragon.java`, `MOBS/BrassDragon.java`, `MOBS/BronzeDragon.java`, `MOBS/CopperDragon.java`, `MOBS/DrowElf.java` (135), `MOBS/DrowWarrior.java` (321), `MOBS/DrowPriestess.java` (263), `MOBS/DrowWizard.java` (257), `MOBS/Doppleganger.java` (144), `MOBS/CombatDummy.java` (124), `MOBS/Cow.java` (204), `MOBS/Assassin.java` (110), `MOBS/Citizen.java` (71), `MOBS/AngryCitizen.java` (77), `MOBS/FlyingInsect.java` (88), `MOBS/Bee.java` (82), `MOBS/Centipede.java` (85), `MOBS/FrostGiant.java` (92), `MOBS/FireGiant.java` (84), `MOBS/Cat.java` (78), `MOBS/interfaces/Auctioneer.java` (69), `MOBS/interfaces/CraftBroker.java` (111), `MOBS/interfaces/Librarian.java` (324), `MOBS/interfaces/PostOffice.java` (285). Also `MOBS/Buck.java`, `MOBS/Doe.java`, `MOBS/Bugbear.java`, `MOBS/Crocodile.java`, `MOBS/Elephant.java`, `MOBS/Deer.java`, `MOBS/Ape.java`, `MOBS/Bull.java`, `MOBS/Centaur.java` and `MOBS/Chimp.java`, read in full at constructor level, with boilerplate skipped.
- **Read in part, or by pattern extraction:** `MOBS/Beaver.java`, `MOBS/BlackBear.java`, `MOBS/BrownBear.java`, `MOBS/Cheetah.java`, `MOBS/Chicken.java` and `MOBS/Buffalo.java`, checked for their `set*` and `Random` lines. The other 13 animal classes (`Alligator`, `Cobra`, `BrownSnake`, `Coyote`, `Dog`, `Duck`, `Fox`, `Cougar`, `DireWolf`, `CommonBat`, `Falcon`, `Dolphin` and `Frog`) were read by a scripted extraction of name, level, armour, damage, speed, race, behaviours, abilities, HP call, weight and alignment. Their values in the table come from that extraction, and the ones that a claim depends on were checked against the source.
- **Skimmed:** `MOBS/interfaces/MOB.java` (method count and the `Attrib` enum location only), `MOBS/interfaces/Deity.java` (method signatures and the two enums at 127 and 145), and `MOBS/interfaces/Banker.java` (method signatures). Full treatment of all three is in b173.
- **Consulted outside the bucket:** `Libraries/Dice.java` (80-86, 398-412), `Libraries/CoffeeLevels.java` (80-91, 119-126, 143-159, 268-300), `core/CMath.java` (433-448, 1455-1476), `core/CMProps.java` (309, 681, 1487-1492, 2720-2722), `coffeemud.ini` (2334-2337), `core/interfaces/Tickable.java` (82), `core/interfaces/MOBPossessor.java`, `MOBS/StdMOB.java` (372-375, 1281-1299, 5035-5040, 5237-5247, 5422-5430), `MOBS/GenMob.java` (44-82), `Libraries/CMAbleMap.java` (984), `Common/interfaces/AuctionPolicy.java` (26, declaration only).
- **Not read:** the implementations of the service interfaces (`StdLibrarian`, `StdPostman`, `StdAuctioneer`, `StdCraftBroker`, `StdBanker`, `StdDeity`), `Common/interfaces/AuctionPolicy.java` beyond its declaration, `core/interfaces/ShopKeeper.java`, the abilities the mobs use (`Dragonbreath`, `Poison*`, `Chopping`, `Immunities`, `Chant_FeelHeat`, `Thief_*`, `Prayer_*`, `Spell_Darkness`), the items they wield (`DragonClaw`, `DrowSword`, `Halberd`, `BattleAxe`, `GenCaged`, `GenLiquidResource`), the behaviours (`Follower`, `MudChat`, `Mobile`, `Aggressive`, `CombatAbilities`), `CMLib.flags().canBreatheHere`, and the callers of `Dragon`'s inhabitant methods.
- **Runtime verification:** none. Every behaviour above was read from source. The off-by-one, the doppleganger state, the negative money and the colour-10 claims are arithmetic or control-flow readings, and each should be checked in a running game before being relied on.
