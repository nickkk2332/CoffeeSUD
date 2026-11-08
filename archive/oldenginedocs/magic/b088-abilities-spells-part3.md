# Spells, part 3: Detect Weaknesses to Flaming Arrows

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b088` (Abilities/Spells [part 3/9: Spell_DetectWeaknesses .. Spell_FlamingArrows]) · 53 files · 9138 lines of Java.

## Summary

This bucket is the middle slice of CoffeeMud's spell list: divination (Detect Weaknesses, Farsight, Find Directions), illusion and disguise (Disguise Self, Feign Death, Fake Armor), familiars, traps (Detonate Bombs), item creation and enchanting, and a set of area and elemental attacks. Every class extends `Spell`, which extends `StdAbility`, so the shared cast rules (mana, the proficiency roll, the tick-based duration, the fizzle messages) live in the base classes and most spell files only define targeting, a success test and one effect. Three mechanics are worth studying for a text game: the two-phase message veto that lets an effect block or rewrite a player's action (Endless Road, Ensnare, Divination Ward, Fake Armor); item creation paid for in experience points and then allowed to expire (Fabricate, Fake Weapon, Fake Food); and a level-difference model that feeds both the success roll and the duration (Enthrall, Dispel Magic, Disintegrate, Dismissal). Several spells contain defects that a player would notice in play, listed under Problems and cruft.

## Key files and types

Spell files are in `com/planet_ink/coffee_mud/Abilities/Spells/` and are cited below by base name. Other files are cited relative to the CoffeeMud folder.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell.java` | Base for all spells: target code `CAN_MOBS`, `ACODE_SPELL`, triggers CAST/CA/C, 1% Magepox roll after more than five malicious spells, armor and concentration check | 1-162 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` | `proficiencyCheck` (1441-1485), XP-cost helper (474-480), range helper (482-489), `adjustedLevel` (899-925), `maliciousAffect` (2185-2207), tick-duration formulas (2140-2183, 2312-2331), `beneficialAffect` (2341-2349), empty `affectPhyStats` (1548-1550), `copyOf` (1424-1431) | read in ranges |
| `com/planet_ink/coffee_mud/Libraries/Dice.java` | `roll(number, die, modifier)` is the sum of `number` dice each 1..`die`, plus `modifier` | 398-406 |
| `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java` | `disenchantItem`: strips wand and scroll spells, ability bonus (3 levels per point), effects (1 level each), good/evil/invisible bits | 704-771 |
| `com/planet_ink/coffee_mud/Libraries/Sense.java` | `isInvisible` reads only the `IS_INVISIBLE` disposition bit | 635-637 |
| `com/planet_ink/coffee_mud/MOBS/StdMOB.java` | `isInCombat` (clears stale victims), `addAbility` (ignores duplicate IDs) | 1394-1412, 4977-4986 |
| `com/planet_ink/coffee_mud/Common/DefaultPhyStats.java` | `setSensesMask` replaces the whole mask | 222-224 |
| `com/planet_ink/coffee_mud/Common/interfaces/CharState.java` | `FATIGUED_MILLIS = tickMillis * 3000` | 75 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_Imprisonment.java` | `getPlanarDiff`, used by Find Planar Familiar to pick a plane | 195-225 |
| `coffeemud.ini` (config) | `EXPRATE=6` (line 406), `TICKTIME=4000` (line 2151) | |
| `Spell_Fireball.java` | Template for a damage spell | 82-115 |
| `Spell_FindFamiliar.java` | Creature summoning and XP payment | 122-272 |
| `Spell_FindPlanarFamiliar.java` | Planar race mixing | 78-177 |
| `Spell_Fabricate.java` | Item creation from crafting recipes | 79-243 |
| `Spell_DetonateBombs.java` | Trap detonation across room, containers, exits, mobs and shops | 77-251 |
| `Spell_FlamingArrows.java`, `Spell_EnchantArrows.java`, `Spell_EnchantWand.java`, `Spell_EnchantArmor.java`, `Spell_EnchantWeapon.java` | Enchanting economy, each with its own cap | 88-201 |
| `Spell_DispelMagic.java`, `Spell_DispelDivination.java` | Dispel selection rules | 70-216 |

## How it works

### 1. The cast pipeline

`Spell.invoke` (`Spell.java:153-161`) calls `StdAbility.invoke` and then `spellArmorCheck` (`Spell.java:132-151`). For a player caster the check fails the cast in two ways: a 50% chance that worn non-cloth armor "absorb[s] magical energy" (message at line 141), and a failure when `CMLib.flags().canConcentrate` is false (line 146, "can't seem to concentrate").

Most spells in the bucket follow one sequence. Resolve a target (`getTarget`, `properTargets`, `getAnyTarget`), run the cheap prechecks, call `super.invoke` (mana), pay any XP, roll `proficiencyCheck`, build a `CMMsg`, pass it through `okMessage` on the room, send it, apply the effect or damage, and on failure show a fizzle message. `Spell_Fireball.java:82-115` is the plainest example:

- Target `getTarget`, room `CMLib.map().roomLocation(target)` (lines 84-89).
- Roll `proficiencyCheck(mob,0,auto)` (line 94).
- Dice count `maxDice = round((adjustedLevel + 2*X1) / 2)`, then damage `roll(maxDice, 9, maxDice/4)` (lines 105-106). Damage is halved if either message has `value()>0` (lines 107-108).

Failure goes to `maliciousFizzle` or `beneficialVisualFizzle`, which display the spell's fizzle text (`Spell_Fear.java:167`, `Spell_Fireball.java:113`).

### 2. The success roll

`StdAbility.proficiencyCheck` (`StdAbility.java:1441-1485`) computes:

1. `pct = proficiency()`, plus any `PROF` adjustments on the caster (lines 1452-1463).
2. `pct += 2 * XLEVEL` (line 1468).
3. Clamp `pct` to 5..95 unless the `DIS955RULE` security flag is set (lines 1470-1476).
4. `pct += XLEVEL / 2` (line 1478).
5. If `adjustment >= 0`, add it to `pct`. If `adjustment < 0`, it is a gate: fail when a d100 roll exceeds `100 + adjustment`, and only then roll against `pct` (lines 1480-1485).

The bucket supplies the adjustments:

- Enthrall: `-10 - INT - 5*levelDiff` (`Spell_Enthrall.java:259`). A target with INT 15 and no level gap gives a gate of -25, so 25% of attempts fail before the normal roll.
- Dispel Magic and Dispel Divination: `-20 * (invoker level - caster level)` when the effect's invoker is higher than the caster, otherwise 0 (`Spell_DispelMagic.java:175-181`, `Spell_DispelDivination.java:130-133`).
- Disintegrate: `-(levelDiff * 16)`, where 16 is `100 / EXPRATE` (`Spell_Disintegrate.java:110-116`). A target three levels above the caster gives a gate of -48, so 48% of attempts fail at the gate.
- Dismissal: `-(levelDiff * 5)` only when the target has no start room, meaning a summoned creature (`Spell_Dismissal.java:91-99`).
- All other spells pass 0.

`levelDiff` in Disintegrate adds 6 when the target is a mob (`Spell_Disintegrate.java:111-112`).

### 3. Costs: mana and experience

Mana is the default unless `overrideMana()` says otherwise. `Ability.COST_ALL` is `Integer.MAX_VALUE` (`Abilities/interfaces/Ability.java:651`) and means the whole pool. It is used by Find Familiar (`Spell_FindFamiliar.java:91-95`), Duplicate (`Spell_Duplicate.java:66-69`), and the four enchanting spells. Enchant Wand says so in a comment: "lose all the mana!" (`Spell_EnchantWand.java:162`). Disintegrate has a fixed cost of 100 (`Spell_Disintegrate.java:71-73`).

Experience is a second currency, and in this bucket it is taken before the success roll:

| Spell | XP formula | Where |
|---|---|---|
| Find Familiar | base 100, reduced by the XPCOST expertise | `Spell_FindFamiliar.java:134-144` |
| Fabricate | `10 + 2 * item level` | `Spell_Fabricate.java:210-223` |
| Duplicate | `item level * multiPlier` (see section 7) | `Spell_Duplicate.java:127-146` |
| Enchant Wand | `10 * lowestQualifyingLevel(spell)`, checked against current XP first | `Spell_EnchantWand.java:156-173` |
| Enchant Armor, Enchant Weapon | 50 | `Spell_EnchantArmor.java:104-114`, `Spell_EnchantWeapon.java:104-111` |
| Enchant Arrows | 5 | `Spell_EnchantArrows.java:119-126` |

`StdAbility.getXPCOSTAdjustment` (`StdAbility.java:474-480`) reduces the XP loss by 5% per point of the XPCOST expertise. The consequence is that a fizzle still costs XP. Only Duplicate and Enchant Wand check that the caster can pay before casting. The rest rely on the result of `postExperience`, so a caster who cannot pay sees "unable to complete this magic" only after the post.

### 4. Duration: how the tick count is chosen

`StdAbility.beneficialAffect` and `maliciousAffect` both call a tick-duration function that treats the sign of `tickAdjustmentFromStandard` as a mode switch (`StdAbility.java:2176-2182`, `2314-2330`):

- **Positive**: the value is the absolute tick count.
- **Zero**: the standard formula applies.
- **Negative**: the standard formula is multiplied by `-value / 100`.

The standard formulas are:

- Beneficial: `casterLevel * 4 + 40` ticks, capped at `TICKSPERMUDDAY` and at least 5 (`StdAbility.java:2316-2326`, `2273-2281`). Here `casterLevel = adjustedLevel(mob, asLevel)`.
- Malicious: `round(1.1 * adjustedLevel) + 25` ticks (`StdAbility.java:2178`). `adjustMaliciousTickdownTime` then reduces it by `levelDiff / (EXPRATE + 1)`, where `levelDiff` is the target level minus the caster's adjusted level, clamped to +/- EXPRATE (`StdAbility.java:2140-2159`). The result is at least 2 ticks (`StdAbility.java:2169-2170`).

With the default 4-second tick (`coffeemud.ini:2151`), the bucket's overrides work out as:

| Spell | Call | Effective duration |
|---|---|---|
| Feign Death | `beneficialAffect(...,10)` (`Spell_FeignDeath.java:185`) | 10 ticks, about 40 s |
| Disguise Self | `adjustedLevel(mob,asLevel)` (`Spell_DisguiseSelf.java:204`) | adjustedLevel ticks, not the standard formula |
| Enlarge Object | `beneficialAffect(...,100)` (`Spell_Enlarge.java:135`) | 100 ticks, about 6.7 min |
| Feign Invisibility | `caster level * 3` (`Spell_FeignInvisibility.java:110`) | three ticks per caster level |
| Earthquake knock-down | `maliciousAffect(...,2,-1)` (`Spell_Earthquake.java:190`) | 2 ticks, about 8 s |
| Enthrall | `maliciousAffect(...,-levelDiff,...)` (`Spell_Enthrall.java:269`) | a percentage of the already level-reduced duration (see Problems) |

The standard beneficial duration is what most creations get. A level-10 caster's Fabricate item or Fake Weapon lasts `10*4+40 = 80` ticks, about 5 minutes (`Spell_Fabricate.java:235`, `Spell_FakeWeapon.java:226`, `Spell_FakeArmor.java:206`).

### 5. Effects that rewrite or veto messages

Many effects are objects that stay attached to the target (through `addEffect` or `beneficialAffect`) and override `okMessage` (may veto a message) and `executeMsg` (reacts after the message is sent). Each effect owns a small piece of the world's rules:

- **Endless Road** (`Spell_EndlessRoad.java:93-117`). When the victim ENTERs a room other than the one it is in, `msg.modify` replaces the target with the victim's current room, so it never leaves. The message "You feel like you are finally getting somewhere." appears on removal (line 88).
- **Ensnare** (`Spell_Ensnare.java:93-141`). Each attempt to ENTER, ADVANCE, LEAVE or FLEE is vetoed and subtracts the victim's level from `amountRemaining`. The counter starts at `round(60 * speed)` (`Spell_Ensnare.java:172-173`). The victim is freed when the counter drops below zero. At speed 1.0 and level 20 that takes four struggles. The mob's speed default was not verified.
- **Divination Ward** (`Spell_DivinationWard.java:96-131`). A mob ward vetoes any ability with `FLAG_DIVINING` that targets the warded mob (line 107 and the return at line 117). A room ward vetoes any divining ability used in the room (lines 124-131). On land the caster owns, the ward is copied as a non-uninvokable effect and saved to the room database (lines 196-200), so it is permanent.
- **Fake Armor** (`Spell_FakeArmor.java:87-102`). The first damage message to the wearer destroys the armor item and adds a trailer "absorbs some of the damage done to <S-NAME>" (lines 96-100). The code never changes `msg.value()`, so the damage still lands. The trailer describes absorption that the code does not do.
- **Fake Weapon** (`Spell_FakeWeapon.java:87-135`). Damage messages from the item are passed through `replaceDamageTag` and their value is set to 0 (lines 92-104), so a hit reads as a hit and does nothing. Spells aimed at the item or its container are vetoed with "Magic energy fizzles around @x1 and is absorbed into the air." (lines 114-125). Ability-tool attacks get "That doesn't appear to work on @x1" (lines 128-131).
- **Feign Death** (`Spell_FeignDeath.java:112-146`). Attacks are vetoed with "You are unable to attack in this semi-incorporeal form." (line 122). Hand and mouth actions are vetoed (lines 127-135). Targeting the caster is vetoed with "<name> doesn't seem to be here." (lines 139-143). `affectPhyStats` sets `IS_INVISIBLE` and `IS_NOT_SEEN` (lines 156-157).
- **Flameshield** (`Spell_Flameshield.java:103-146`). A melee touch with HANDS or MOVE triggers a fire burst with a chance of `100 - 3*DEX` percent, using the source's DEX. The burst does `roll(1, round((invokerLevel + 2*X1)/4), 1)` damage, with invoker level capped at the target level plus EXPRATE (lines 121-139). It fires at most once per target tick (lines 123, 142).
- **Flaming Arrows** (`Spell_FlamingArrows.java:88-131`). When the owner's arrow is the tool of a damaging hit, it posts an extra fire hit of `msg.value() + XLEVEL` (line 111). A `norecurse` flag stops the extra hit from triggering itself (lines 113-125).

### 6. Dispel selection

Both dispels collect candidate effects, pick one, and then call `unInvoke()` on it. They differ in how candidates are chosen.

**Dispel Magic** (`Spell_DispelMagic.java:70-81`, `139-157`). An effect is eligible if it `canBeUninvoked`, is not in the CURSING domain, and is a SPELL, SONG, PRAYER or CHANT. The loop has no `break`, so the last eligible effect in the list is selected. The caster can dispel an effect whose invoker is the caster, or whose invoker's level is at most the caster's level plus EXPRATE, or any effect when the caster is a sysop.

**Dispel Divination** (`Spell_DispelDivination.java:70-89`, `107-113`). It collects DIVINATION-domain effects from the target, its room and its area (lines 109-111). An effect is eligible if the caster invoked it or if `adjustedLevel(caster) <= invoker's adjustedLevel + 5 + EXPRATE` (line 84). That allows 11 levels of slack at the default EXPRATE. The pick is random with `roll(1, n, -1)`, which is 0..n-1 (line 113).

Both return "The magic on <T-NAME> appears too powerful to dispel." when divinations or magic exist but none are eligible (`Spell_DispelMagic.java:162-163`, `Spell_DispelDivination.java:117-118`).

### 7. Item creation

**Fabricate** (`Spell_Fabricate.java:79-243`). A static list of every `ItemCraftor` ability is built on first use and sorted by lowest qualifying level (lines 79-113). The typed name is matched first as an exact recipe name, then as a loose match (lines 143-171). If nothing matches, the caster's inventory, the local area and then the world are searched, with a time budget of 5% of a second between rooms in the world search (lines 173-178). The chosen item is then:

- refused if it is ArchonOnly or cannot be player-destroyed (lines 186-190);
- copied, disenchanted (`CoffeeUtensils.disenchantItem`), stripped of its behaviors, and given a value of 0 (lines 193-206);
- given a standard beneficial duration as a temporary item (line 235);
- destroyed on expiry with "<T-NAME> vanishes!" (`unInvoke`, lines 115-128).

The source comment (lines 208-209) says the XP cost is meant to make Fabricate cheaper than Duplicate or Polymorph Object and dearer than Wish. The code does not check that.

**Fake Weapon** (`Spell_FakeWeapon.java:138-233`). The argument must be one of seven names. Each name maps to a weapon class and damage type:

| Argument | Name | Weapon class | Damage type |
|---|---|---|---|
| sword | a fancy sword | CLASS_SWORD | SLASHING |
| dagger | a sharp dagger | CLASS_DAGGER | PIERCING |
| mace | a large mace | CLASS_BLUNT | BASHING |
| staff | a quarterstaff | CLASS_STAFF | BASHING |
| axe | a deadly axe | CLASS_AXE | SLASHING |
| hammer | a large hammer | CLASS_HAMMER | BASHING |
| flail | a large flail | CLASS_FLAILED | BASHING |

The `GenWeapon` is cotton, weight 0, value 0, flagged `IS_BONUS`, with attack adjustment `100 + 10*XLEVEL` and damage `75 + 3*XLEVEL` (lines 164-222). `Spell_FakeWeapon.java:226` then sets its temporary duration.

**Fake Armor** (`Spell_FakeArmor.java:120-213`). Two words are needed: a material (plate, chain, leather, studded) and a slot (helmet, shirt, leggings, sleeves, boots). The item has armor value 0 (line 156), so the only effect is the one-hit absorb described in section 5.

**Fake Food** (`Spell_FakeFood.java:74-206`). The recipe name comes from one of four cooking skills (`FoodPrep`, `Cooking`, `MasterCooking`, `Baking`, line 70). Recipes must be food with a level at or below the caster's adjusted level (line 107). A recipe is rejected if its name is also an ingredient name, which keeps the material hidden (lines 124-127). The food has nourishment 0 (line 191). Five copies are placed on the floor with `Expire.Resource` (lines 193-198). If no recipe qualifies within 1000 tries, one of five fixed foods is used (lines 158-189).

**Fake Spring** (`Spell_FakeSpring.java:116-165`). A non-gettable `GenWater` drink with quench 0 is placed in the room (lines 139-149). Drinking from it gives "You have drunk all you can." and vetoes the fill of any other drink container (lines 94-109). On land the caster owns, the effect is permanent (lines 150-154).

**Duplicate** (`Spell_Duplicate.java:78-183`). Refused on clan items, ArchonOnly items, boards, packaged items, and coins in a currency that cannot be traded (lines 89-117). The XP cost is `level * multiPlier`, where `multiPlier = 5 + (weight + value)/2 + 10*numEffects + 10 (potion) + 10 (pill) + 5 (wand)` (lines 119-130). The copy is disenchanted (line 159) and placed with its owner, or on the floor with `Expire.Player_Drop` (lines 165-171).

### 8. Familiars

**Find Familiar** (`Spell_FindFamiliar.java:122-272`). A caster with followers, or a monster caster, is refused (lines 125-128). The familiar is a `GenMOB` at the caster's level. Its `basePhyStats` ability is set to `CMProps.getMobHPBase() - 4` (line 177). A random animal is chosen with `roll(1, 9, -1)`, giving 0..8 (line 181):

| Index | Animal | Gender | Race |
|---|---|---|---|
| 0 | dog | F | Dog |
| 1 | turtle | F | Turtle |
| 2 | cat | F | Cat |
| 3 | bat | M | Bat |
| 4 | rat | M | Rodent |
| 5 | snake | M | Snake |
| 6 | owl | M | Owl |
| 7 | rabbit | F | Rabbit |
| 8 | raven | M | Raven |

The familiar gets a `Prop_ModExperience` of 0 (line 255), the summoned and summoned-by-caster tattoos (lines 256-257), alignment GOOD (line 258), and a `Prop_Familiar` effect whose misc text is the animal index (lines 264-268). The effect is made non-uninvokable and long-lasting (lines 267-268). Persistence across reboots was not verified.

**Find Planar Familiar** (`Spell_FindPlanarFamiliar.java:78-177`). If the caster is on a plane that defines a `MIXRACE`, that race is used (lines 81-94). Otherwise the plane with the smallest `getPlanarDiff` is chosen, where the diff sums the distance on alignment and on the inclination faction (`Spell_Imprisonment.java:195-225`; lines 96-138 here). The familiar's race is then mixed with the plane race through `CMLib.utensils().getMixedRace` (line 149). The name becomes "a/an <race>" and the display text is rewritten to match (lines 155-170).

### 9. Traps: Detonate Bombs

A bomb is a `Trap` whose `isABomb()` is true and whose invoker is the caster (`Spell_DetonateBombs.java:77-87`). The spell scans:

- the room, or a container's deep contents, an item, a mob's inventory, and the stock of a shopkeeper (`bombsHere`, lines 90-174);
- an exit, in both directions and into the far room (lines 123-148).

Success needs only one bomb or target, whatever the proficiency roll says (lines 224-228). For each bomb the spell picks one candidate target with `roll(1, n, 0)` (line 241), then calls `T.spring(mob2)`. Section Problems explains why line 241 is wrong.

### 10. Area and elemental attacks

- **Earthquake** (`Spell_Earthquake.java:157-207`). It affects every proper target in the room. A target in flight is unaffected (line 181). A target with no legs (`BODY_LEG` of 0) is unaffected (line 198). Otherwise the target is knocked down for 2 ticks with `IS_SITTING` and a veto on standing, announced once per tick (`oncePerRd`, lines 111-126). A fall does 10 damage if the target is still in the room (line 194).
- **Elemental Storm** (`Spell_ElementalStorm.java:88-132`). Six missiles fire in order: fire, cold, acid, water, electric and gas (table in section 11). Each does `roll(1, 3 + (adjustedLevel + 2*X1)/10, 0)` damage (line 121). It stops when the target leaves the room (line 110) and re-targets to a new mob when the target dies (lines 126-132).
- **Dragonfire** (`Spell_Dragonfire.java:84-139`). Each target takes `roll(maxDie, 4, maxDie/4)` damage, where `maxDie = adjustedLevel + X1` (lines 107, 124). `maxDie` halves for every later target, with a floor of 4 (lines 125-127). The breath requires the caster to be able to breathe in the room (line 93).
- **Disintegrate** (`Spell_Disintegrate.java:77-177`). A mob target takes `HP * 100` damage (line 143), so the kill is certain unless the damage is prevented. Items are destroyed (lines 152-153). Corpses that appear as a result are removed (lines 154-166). Player corpses owned by others are protected (lines 82-92).
- **Fear** (`Spell_Fear.java:127-170`). Fear does not apply a lasting effect. For each target it sends a verbal message and then a malicious mental message. If neither is vetoed, it sets `invoker = mob` and calls `postFlee(target, "")` (lines 150-160). A "WEAK" misc-text variant needs the caster's level / 2 to exceed the target's level (line 150). The lasting code in `affectPhyStats`, `tick` and `unInvoke` is only reached if other code applies this object as an effect (see Problems).

### 11. Enchanting

Each enchanting spell has its own rule and cap. They are compared here:

| Spell | Target | Effect | Cap | Permanence | Where |
|---|---|---|---|---|---|
| Enchant Armor | Armor | base ability +1, base level +3, `IS_BONUS` | ability above 2 refused | permanent (base stats) | `Spell_EnchantArmor.java:95-125` |
| Enchant Weapon | Weapon | same as Armor | same | permanent | `Spell_EnchantWeapon.java:95-125` |
| Enchant Arrows | arrows, bolts, ballista-bolts | counter in misc text; PhyStats ability += counter | counter above 2 refused (3 max) | permanent non-uninvokable effect | `Spell_EnchantArrows.java:92-148` |
| Flaming Arrows | arrows | fire hit of `XLEVEL` added to each arrow hit | one per arrow | permanent non-uninvokable effect | `Spell_FlamingArrows.java:111-168` |
| Enchant Wand | empty wand | spell stored (copy), 5 charges, level raised to the spell's | wand must hold no spell | permanent | `Spell_EnchantWand.java:150-191` |

The **Enchant Wand** spell picks its spell by exact name first, then by substring, among the caster's known spells of the SPELL class that are either not savable or meet the level test (`Spell_EnchantWand.java:115-136`). The spell must be `mayBeEnchanted()` (line 144).

**Disenchant Item** calls `CoffeeUtensils.disenchantItem` (`Libraries/CoffeeUtensils.java:704-771`). The wand loses its spell and charges (lines 711-714). The item loses 3 levels per point of ability (lines 724-730) and 1 level for each effect that is not `FLAG_NODISENCHANT` (lines 733-746). The good, evil, invisible, bonus and hidden disposition bits are cleared (lines 748-756). The result is the new level, or -999 if nothing changed (lines 769-771). `Spell_Disenchant.java:88-99` sets the new level with a floor of 1.

**Disenchant Wand** (`Spell_DisenchantWand.java:82-101`) clears the spell and charges. **Erase** (`Spell_Erase.java:83-103`) clears a scroll's spell list or any readable text.

### 12. Divination and information

**Detect Weaknesses** (`Spell_DetectWeaknesses.java:97-155`, `170-217`). The caster must be in combat (lines 176-179). The target gets a bonus while it is fighting the enemy that was "spotted" (lines 135-151). Its damage and attack adjustment each gain `round(value / (4.0 - 0.2*XLEVEL))` (lines 106-108). That is +25% at XLEVEL 0, +33% at XLEVEL 5, and +50% at XLEVEL 10. The effect ends when the caster leaves the room or is no longer in combat (lines 120-132). It is cast on the target at the standard beneficial duration (line 200).

**Farsight** (`Spell_Farsight.java:77-163`). With no arguments it shows the exit descriptions three rooms deep in each open direction (lines 97-114). With direction arguments it walks the sight outward, up to `caster level / 5` steps (lines 88-90). It sends a MSG_LOOK to each room it reaches (lines 155-156). A blocked exit ends the walk with "Something has obstructed your vision." (line 145).

**Divining Eye** (`Spell_DiviningEye.java:82-143`). It needs an active Prying Eye effect on the caster (lines 90-94). The rest of the command is read as a divination spell from the caster's list (lines 97-109). That spell is invoked on the eye mob, from the eye's location (lines 123-131).

**Distant Vision** (`Spell_DistantVision.java:77-123`) and **Find Directions** (`Spell_FindDirections.java:83-141`) look at a named place or area. Distant Vision shows one room, chosen at random among the matches. Find Directions prints a trail from the caster's room to an accessible area (lines 133-134).

**Dream** (`Spell_Dream.java:65-114`) delivers text to every sleeping mob in every room the caster can access (lines 86-98).

### 13. Illusion and disguise

- **Disguise Self** (`Spell_DisguiseSelf.java:84-215`). The misc text stores race, class, name and gender, copied from a mob the caster can see (lines 93-96, 170-206). The caster cannot copy a player or a mob above their level (lines 180-189). The disguise lasts `adjustedLevel` ticks (line 204).
- **Disguise Undead** (`Spell_DisguiseUndead.java:84-237`) makes an undead mob look human. It picks a noun by a d5 roll and a descriptor by a d3 roll (lines 179 and 217).
- **Disguise Other** (`Spell_DisguiseOther.java:60-79`) is Disguise Self with the first argument naming the target.
- **Faerie Fog** (`Spell_FaerieFog.java:87-197`) is a room effect that removes `IS_INVISIBLE` from mobs and items in the room and makes them glow, and adds +10 to armor (lines 103-120). **Faerie Fire** (`Spell_FaerieFire.java:91-147`) does the same to one mob.
- **Divine Beauty** (`Spell_DivineBeauty.java:79-146`) sets charisma to at least 30, or adds 1 if it is already 30 or more (lines 82-85).
- **Enlightenment** (`Spell_Enlightenment.java:79-142`) adds to intelligence by base class (table in section 14).
- **Feeblemind** (`Spell_Feeblemind.java:85-140`) sets intelligence to 1 and mental saves minus 25 (lines 88-89).
- **Feel The Void** (`Spell_FeelTheVoid.java:97-149`) replaces the sense mask (see Problems).
- **Enlarge Object** (`Spell_Enlarge.java:73-144`) adds 9999 to weight and height and appends " of ENORMOUS SIZE!!!" to the name (lines 76-78). It cancels a Shrink effect instead if one is present (lines 129-131).

### 14. Other spells

- **Endless Road** and **Ensnare** are described in section 5.
- **Enthrall** (`Spell_Enthrall.java:84-288`). The charmer is the invoker, or a mob named in the misc text (lines 85-101). If the enthralled mob or its leader quits, the effect ends (lines 107-111). The enthralled mob follows its charmer, and is blocked from attacking, leaving the room, or stopping its following (lines 117-160). Each tick re-issues the follow (lines 163-179). Releasing the mob makes it wander or attack its invoker (lines 199-210).
- **Dismissal** (`Spell_Dismissal.java:81-128`) destroys a summoned target and sends a target with a start room back to that room (lines 107-116).
- **Dispel** and **Divination Ward** are described in sections 5 and 6.
- **Exhaustion** (`Spell_Exhaustion.java:92-127`) sets movement to 0 and adds fatigue. The test is inverted (see Problems).
- **Fatigue** (`Spell_Fatigue.java:94-126`) adds `FATIGUED_MILLIS + level * 5 * tickMillis` of fatigue (line 117).
- **Flagportation** (`Spell_Flagportation.java:86-236`) moves the caster and their group to a random room with a clan flag of the caster's clan (section 15).
- **Flameshield** and **Flaming Arrows** are described in section 5.
- **Fear** is described in section 10.

Enlightenment's class table:

| Base class | INT bonus | Source |
|---|---|---|
| Mage | 8 | `Spell_Enlightenment.java:86-87` |
| Druid | 7 | `:98-99` |
| Cleric | 6 | `:95-96` |
| Thief, Bard | 5 | `:89-93` |
| Fighter and others | 4 | `:82-84` |

### 15. Flagportation and clans

Flagportation requires a clan membership (`Spell_Flagportation.java:90`). The clan is named by the argument or chosen as the first clan where the caster holds the `CLAN_BENEFITS` function (lines 95-125). The spell scans every room in the world (line 132) for accessible rooms that hold a clan flag of that clan (lines 137-146). It picks a random candidate with `roll(1, n, -1)` (line 171) and tries up to 20 times to pass the enter check with the session temporarily nulled (lines 169-180).

On a failed roll the spell does not return. It finds a random room that is not in the same area as the flag room, not the current room, accessible to the caster and without a land title (`isBadRoom`, lines 76-84), and moves the group there (lines 193-201). The fizzle text is shown only when no such room is found within 1000 tries (lines 197-200).

## Content worth mining

### Spell catalogue

All 53 spells are in `Abilities/Spells/`. "Quality" is the spell's `abstractQuality()`, which tells the engine whether a spell counts as helpful or harmful to the target. "Level" is the minimum level from the class's `addCharAbilityMapping` call in `CharClasses/` (see the notes on the two unmapped spells).

| Class (file `Spell_*.java`) | Shown name | Quality | Target | Domain | Level (class) | One-line effect |
|---|---|---|---|---|---|---|
| DetectWeaknesses | Detect Weaknesses | BENEFICIAL_SELF | fighting target | Divination | 5 (Diviner) | +25% to +50% damage and attack while fighting the spotted enemy |
| DetonateBombs | Detonate Bombs | INDIFFERENT | room, item, exit, mob | Evocation | 24 (Trapper) | springs the caster's own bombs in range |
| Disenchant | Disenchant Item | INDIFFERENT | item | Evocation | 22 (Mage), 24 (Arcanist) | strips enchantments and lowers level |
| DisenchantWand | Disenchant Wand | INDIFFERENT | wand | Evocation | 13 (Arcanist) | empties a wand's spell and charges |
| DisguiseOther | Disguise Other | OK_OTHERS | mob | Illusion | 23 (Illusionist) | Disguise Self on another mob |
| DisguiseSelf | Disguise Self | OK_OTHERS | mob (self default) | Illusion | 15 (Illusionist) | copies the race, class, name and gender of a non-player mob the caster can name |
| DisguiseUndead | Disguise Undead | OK_OTHERS | undead mob | Illusion | 1 (Illusionist) | makes an undead mob appear as a normal human |
| Disintegrate | Disintegrate | MALICIOUS | mob or item | Evocation | 25 (Mage) | destroys the target; mob takes HP x 100 |
| Dismissal | Dismissal | MALICIOUS | mob | Conjuration | 16 (Mage) | destroys a summoned mob or returns it to its start room |
| DispelDivination | Dispel Divination | MALICIOUS | mob, item, room, exit | Evocation | 5 (Illusionist) | removes one divination effect |
| DispelMagic | Dispel Magic | MALICIOUS | items, mobs, exits, rooms | Evocation | 5 (Mage) | removes one spell, song, prayer or chant effect |
| DistantVision | Distant Vision | INDIFFERENT | named place | Divination | 13 (Mage) | one-shot look at a random matching room |
| DivinationWard | Divination Ward | OK_OTHERS | mob or room | Abjuration | 21 (Abjurer) | vetoes divining abilities on a mob or in a room |
| DivineBeauty | Divine Beauty | BENEFICIAL_OTHERS | mob | Illusion | 20 (Illusionist) | charisma floor of 30 |
| DiviningEye | Divining Eye | INDIFFERENT | via Prying Eye | Divination | 17 (Diviner) | casts a divination spell from the eye's location |
| Dragonfire | Dragonfire | MALICIOUS | room mobs | Evocation | 23 (Mage) | breath of fire to everyone in range; dice halve per target |
| Dream | Dream | INDIFFERENT | every sleeper | Illusion | 3 (Mage) | sends text to all sleeping mobs in accessible rooms |
| Duplicate | Duplicate | INDIFFERENT | own item | Alteration | 25 (Alterer) | copies an item, disenchanted, at an XP cost |
| Earthquake | Earthquake | MALICIOUS | room mobs | Evocation | 13 (Mage) | knocks down legged mobs for 2 ticks, 10 damage |
| ElementalStorm | Elemental Storm | MALICIOUS | mob | Evocation | 8 (Mage) | six elemental missiles |
| EnchantArmor | Enchant Armor | INDIFFERENT | armor | Enchantment | 12 (Arcanist), 20 (Mage) | +1 ability, +3 level, cap ability 3 |
| EnchantArrows | Enchant Arrows | INDIFFERENT | arrows, bolts | Enchantment | 2 (Mage), 3 (Arcanist) | +1 ability per cast, cap 3 |
| EnchantWand | Enchant Wand | INDIFFERENT | empty wand | Enchantment | 15 (Arcanist) | stores a known spell, 5 charges |
| EnchantWeapon | Enchant Weapon | INDIFFERENT | weapon | Enchantment | 14 (Arcanist), 22 (Mage) | as Enchant Armor |
| EndlessRoad | Endless Road | MALICIOUS | mob | Illusion | 25 (Illusionist) | every move returns the mob to its room |
| Enlarge | Enlarge Object | INDIFFERENT | unworn item | Alteration | 2 (Mage) | weight and height +9999 for 100 ticks |
| Enlightenment | Enlightenment | BENEFICIAL_OTHERS | mob | Transmutation | 12 (Mage) | intelligence bonus by base class |
| Enthrall | Enthrall | MALICIOUS | mob | Enchantment | 19 (Enchanter) | charms a mob into following the caster |
| Erase | Erase Scroll | INDIFFERENT | scroll or readable | Alteration | 1 (Mage), 2 (Arcanist) | clears a scroll's spell or text |
| Ensnare | Ensnare | MALICIOUS | mob | Alteration | 15 (Mage) | blocks movement until a struggle counter runs out |
| Fabricate | Fabricate | INDIFFERENT | named crafted item | Alteration | 23 (Alterer) | temporary copy of any craftable item |
| FaerieFire | Faerie Fire | MALICIOUS | mob | Illusion | 5 (Mage) | reveals a mob, +10 armor value |
| FaerieFog | Faerie Fog | INDIFFERENT | room | Illusion | 8 (Mage) | reveals invisible things in the room |
| FakeArmor | Fake Armor | INDIFFERENT | creates armor | Illusion | 16 (Mage) | one-hit absorb |
| FakeFood | Fake Food | INDIFFERENT | creates food | Illusion | 4 (Mage) | five food items with zero nourishment |
| FakeSpring | Fake Spring | INDIFFERENT | creates spring | Illusion | 4 (Mage) | infinite water source that quenches nothing |
| FakeWeapon | Fake Weapon | INDIFFERENT | creates weapon | Illusion | 15 (Mage) | temporary weapon that does no damage |
| Farsight | Farsight | INDIFFERENT | exits | Divination | 9 (Mage) | see down exits, or walk the sight outward |
| FeatherFall | Feather Fall | OK_OTHERS | mob | Alteration | 3 (Mage) | sets weight to 0, veto on heavy GET |
| FeelTheVoid | Feel The Void | MALICIOUS | mob | Illusion | 30 (Illusionist) | removes most senses |
| Feeblemind | Feeblemind | MALICIOUS | mob | Enchantment | 11 (Mage) | intelligence 1, mental saves -25 |
| FeignDeath | Feign Death | OK_OTHERS | mob | Illusion | 13 (Mage) | fake corpse; target hidden and blocked from attacking, speaking and gesturing for 10 ticks |
| FeignInvisibility | Feign Invisibility | OK_OTHERS | mob | Illusion | 13 (Illusionist) | sets no invisibility bit (see Problems) |
| Fireball | Fireball | MALICIOUS | mob | Evocation | 7 (Mage) | d9 damage per die, dice = half of level |
| FindDirections | Find Directions | INDIFFERENT | area | Divination | 20 (Diviner) | path trail to a named area |
| FindFamiliar | Find Familiar | BENEFICIAL_SELF | none | Conjuration | 6 (Mage) | summons one of nine animals, costs XP |
| FindPlanarFamiliar | Find Planar Familiar | BENEFICIAL_SELF | none | Conjuration | 18 (Mage) | familiar mixed with the plane's race |
| Fatigue | Fatigue | MALICIOUS | mob | Enchantment | 5 (Enchanter) | adds fatigue by level |
| Exhaustion | Exhaustion | MALICIOUS | mob | Alteration | not mapped in CharClasses | sets movement to 0 (see Problems) |
| Fear | Fear | MALICIOUS | mobs in room | Enchantment | 8 (Mage) | immediate flee |
| Flagportation | Flagportation | INDIFFERENT | clan flags | Conjuration | not mapped in CharClasses | group teleport to a clan flag |
| Flameshield | Flameshield | BENEFICIAL_OTHERS | mob | Evocation | 9 (Mage) | fire burst on touch, armor -1 minus XLEVEL |
| FlamingArrows | Flaming Arrows | INDIFFERENT | arrows | Alteration | 4 (Alterer) | extra fire damage on hits |

The class-level mapping for Exhaustion and Flagportation was not found in `CharClasses/`. Exhaustion is named in `Abilities/Songs/Play_Symphony.java:602`. Flagportation is named nowhere outside its own file, so it is reachable only through data or content that this search did not find.

### Tables worth reusing

**Fire, cold, acid, water, electric and gas missiles** (`Spell_ElementalStorm.java:88-105`):

| Missile | Message | Damage type |
|---|---|---|
| fire | "A flame" | BURNING |
| cold | "Some frost" | FROSTING |
| acid | "Drops of acid" | MELTING |
| water | "Stream of water" | BURSTING |
| electric | "A spark" | STRIKING |
| gas | "A puff of gas" | GASSING |

**Disguise nouns** (`Spell_DisguiseUndead.java:179-227`). A d5 roll picks the noun by reproductive code. Male gives male, man, guy, joe, person. Female gives female, woman, gal, jane, person. A d3 roll adds "a normal", "a regular" or "an average" as the descriptor.

**Fake Food** names (`Spell_FakeFood.java:158-189`) when no recipe is found: "a shiny apple", "a nice peach", "a big pot pie", "a juicy steak", "a bit of food".

**Detect Weaknesses bonus** (`Spell_DetectWeaknesses.java:106-108`):

| XLEVEL | Damage and attack bonus |
|---|---|
| 0 | +25% |
| 5 | +33% |
| 10 | +50% |

**Dispel windows**. Dispel Magic accepts invokers up to EXPRATE (6) levels above the caster. Dispel Divination accepts up to 11 (5 + EXPRATE). Both penalize casters below the invoker by 20 points per level.

**Familiar animals** (section 8).

**Endless Road** and **Ensnare** give concrete, text-only stunts: a victim who cannot leave, and a victim whose struggles count down.

## Notable design ideas

- **Messages as the rule engine.** Effects change the world by answering `okMessage` and rewriting messages, not by editing state directly. Endless Road rewrites the destination of a move (`Spell_EndlessRoad.java:105-113`). Fake Weapon keeps a hit's text and zeroes its value (`Spell_FakeWeapon.java:92-104`). A single pipeline then covers wards, shields, charms and cages.
- **One duration formula with three modes.** The sign of one integer chooses absolute, standard or percentage duration (`StdAbility.java:2176-2182`). It is compact and gives callers an easy override, though it is easy to misuse (see Enthrall).
- **Pre-roll gates from level difference.** A negative proficiency adjustment fails a fraction of casts before the skill roll (`StdAbility.java:1480-1485`). A strong target is resisted without any special-case code, and the cost scales with a single number.
- **Experience as currency for creation.** Fabricate, Duplicate and the enchanting spells cost XP, not only mana, and the cost is tied to the item's level, value, weight and effects. A creation can be made dearer or cheaper by adding expertise (`StdAbility.java:474-480`).
- **Temporary items as illusions.** Fake Weapon, Armor, Food and Spring are real items with a timed effect. Expiry is just `unInvoke` destroying the item (`Spell_FakeWeapon.java:76-85`).
- **Level-windowed dispels.** The dispel rules compare the caster and the invoker of each effect with EXPRATE, so high-level magic is hard to remove and low-level magic is easy to remove by a strong caster.
- **Familiars as data.** The nine-animal table is a list of name, gender, race and description. The planar version mixes races by reading plane variables (`Spell_FindPlanarFamiliar.java:145-149`).
- **Bombs tied to ownership.** A bomb counts only if its invoker is the caster (`Spell_DetonateBombs.java:84`). Trap ownership is a simple check.

## Problems and cruft

Listed in rough order of how much they would matter in play. Items marked "unverified" were read from the code but not run.

1. **Detonate Bombs throws a ClassCastException.** In `getTargets`, the branch for a trap on a mob casts the mob to `Item`: `else if(T.affecting() instanceof MOB) targetMs.add((MOB)((Item)T.affecting()).owner());` (`Spell_DetonateBombs.java:183-184`). A `MOB` is not an `Item`, so this throws. The branch is reached when a bomb's affected object is a mob. Unverified at runtime.
2. **Detonate Bombs indexes past the end of its list.** `targetMs.get(CMLib.dice().roll(1, targetMs.size(), 0))` (`Spell_DetonateBombs.java:241`). `Dice.roll` returns 1..size (`Libraries/Dice.java:398-406`), so the index is one too high. With a single candidate it always fails. With n candidates it fails with probability 1/n, and the first candidate is never chosen (index 0 is never produced).
3. **Exhaustion applies its effect when the message value is greater than zero.** `Spell_Exhaustion.java:111` tests `if(msg.value()>0)`, while Fatigue, Earthquake, Ensnare and most others apply when `value() <= 0`. In this codebase `value > 0` means "saved or halved": Dragonfire halves damage on it (`Spell_Dragonfire.java:128-129`), and `Prop_AbsorbDamage.java:433` sets it to 1 with the comment "saved?". Exhaustion therefore appears to take effect when the target resists. Unverified at runtime.
4. **Find Planar Familiar has an unreachable fallback and a possible null dereference.** The second loop (`Spell_FindPlanarFamiliar.java:121-138`) can only find something the first loop missed, and `getPlanarDiff` returns a finite sum so the first loop always finds a plane when one defines `MIXRACE`. If no plane defines `MIXRACE`, `mixRace` stays null and `determineMonster` dereferences it (lines 147-149). Unverified whether any shipped plane setup lacks `MIXRACE`.
5. **Fake Armor's shirt uses the head slot.** `Spell_FakeArmor.java:182-183` sets `WORN_HEAD` for "a @x1 shirt". `Items/interfaces/Wearable.java:257` defines `WORN_TORSO`, which is the obvious slot. This looks like a copy-paste error.
6. **Fake Armor does not absorb damage.** The trailer says it absorbs part of the damage, but `okMessage` never changes `msg.value()` (`Spell_FakeArmor.java:96-101`). The static flag `notAgainThisRound` (line 65) is shared by all instances and is cleared on any Fake Armor tick (lines 79-85), so it limits absorption across the whole world rather than per item.
7. **Feign Invisibility sets no invisibility.** The class has no `affectPhyStats`. `StdAbility.affectPhyStats` is empty (`StdAbility.java:1548-1550`). `CMLib.flags().isInvisible` reads only the `IS_INVISIBLE` disposition bit (`Libraries/Sense.java:635-637`). The code sends "You fade from view" (line 109) and its display text is "(Improved Invisibility)" (line 52), but nothing hides the target. Compare `Spell_Invisibility.java:94-102`.
8. **Flagportation's failure still moves the group.** After the fizzle message at line 200, the code sets `newRoom = room` (line 201) with no `return`, so the group moves to the random room (lines 204-232). The fizzle text is also shown only when no room is found after 1000 tries, which is not what a reader would expect.
9. **Fear's lasting effect is dormant in this file.** `Spell_Fear.invoke` never adds itself to a target (lines 150-160). The `affectPhyStats`, `tick` and `unInvoke` methods (lines 79-124) only matter if another class applies it. `Prayer_AuraFear.java:174,225`, `Play_Symphony.java:230`, `Races/Lich.java:99` and `Races/Beholder.java:95` reference it. Whether any of them adds it as an effect was not verified.
10. **Enthrall applies the level penalty twice.** `maliciousAffect(...,-levelDiff,...)` (`Spell_Enthrall.java:269`) turns a positive `levelDiff` into a percentage of the standard duration (`StdAbility.java:2178-2182`). That duration was already reduced by `adjustMaliciousTickdownTime`, which subtracts `levelDiff / (EXPRATE+1)` of the ticks (`StdAbility.java:2148-2159`). Read from the code; not measured.
11. **Several spells let a per-target result overwrite the spell result.** Earthquake (`Spell_Earthquake.java:190`) and Ensnare (`Spell_Ensnare.java:176`) assign `success` once per target, so the return value reflects only the last target.
12. **XP is spent before the roll in most creation spells.** A fizzle still costs XP in Find Familiar (lines 134-144), Fabricate (210-223), Duplicate (139-149), Enchant Wand (166-176), Enchant Armor and Weapon (104-114) and Enchant Arrows (119-129). Duplicate and Enchant Wand check the caster's XP first, but the others do not.
13. **Fabricate's "a copy of Wish" comment is a statement of intent, not code.** The comment at `Spell_Fabricate.java:208-209` compares XP costs to other spells. The code uses `10 + 2*level`, which is not checked against those spells.
14. **Dispel Divination does not check that the effect was removed.** `Spell_DispelMagic.java:203-207` checks whether the target still has the effect and fizzles if so. `Spell_DispelDivination.java:151` does not.
15. **Divining Eye leaks the ability onto the eye.** The `finally` block is empty (`Spell_DiviningEye.java:134-136`). The divination spell stays on the eye's list. `addAbility` ignores duplicate IDs (`StdMOB.java:4977-4986`), so the leak is one entry per spell, not per cast.
16. **Feather Fall does not reduce falling damage.** It sets phyStats weight to 0 (`Spell_FeatherFall.java:82`) and vetoes heavy GETs by base inventory weight (`:98-117`). The name suggests a different effect.
17. **Feel The Void's second mask is a no-op.** `mask2` excludes the `CAN_SEE_*` bits from `Integer.MAX_VALUE` (`Spell_FeelTheVoid.java:83-94`), and `mask` contains none of those bits, so `mask & mask2 == mask`. `setSensesMask` replaces the mask (`Common/DefaultPhyStats.java:222-224`), so every other sense bit is cleared as well.
18. **Flagportation changes the session during a veto check.** It sets the session to null around `okMessage` (`Spell_Flagportation.java:174, 179`) without a `finally`, so an exception would leave the caster without a session.
19. **Disenchant Wand reports a successful roll on an empty wand as a fizzle.** The else branch at `Spell_DisenchantWand.java:100-101` covers both a failed roll and an empty or spell-less wand.
20. **Typos and cruft.** `Spell_EnchantArrows.java:112` reads "ith". `Spell_DisguiseSelf.java:182` and `:188` end in question marks and have a double space at 182. `Spell_FakeArmor.java:142` has no space in "leather.You". `Spell_DispelMagic.java:76-78` lists the SONG code twice. `Spell_FindFamiliar.java:148` and `:162` both set `invoker = mob`. `Spell_Erase.java:76` says "Erase what?.".
21. **Dream scans the whole world per cast.** `Spell_Dream.java:86` walks every room. Flagportation does the same at line 132. Fine for a single player's world, but it is a scan on every cast.

## Takeaways for a Python SUD

- **Spells as data.** Model each spell as a record with an `id`, `school`, `quality` (harmful, helpful-self, helpful-other, neutral), a target kind (`mob`, `item`, `room`, `exit`, `area`, `none`), a mana cost, an optional XP cost function and a duration rule. Keep the behaviour in a small `cast(ctx)` function. Most of the 53 files are under 250 lines, so this shape should shrink the bucket to a table plus about a dozen functions.
- **Duration as an explicit type, not a sign trick.** The three-mode integer in `StdAbility.java:2176-2182` is the main source of confusion. Use a tagged value:

  ```python
  from dataclasses import dataclass

  @dataclass(frozen=True)
  class Ticks:
      n: int               # absolute ticks, e.g. Feign Death 10

  def beneficial_ticks(caster_level: int) -> int:
      return caster_level * 4 + 40           # StdAbility.java:2325

  def malicious_ticks(caster_level: int, target_level: int, expr: int = 6) -> int:
      base = round(1.1 * caster_level) + 25  # StdAbility.java:2178
      diff = max(-expr, min(expr, target_level - caster_level))
      return max(2, base - round(base * diff / (expr + 1)))   # 2148-2170, caps omitted
  ```

  Keep the level-difference penalty in one place. Do not also pass it as a duration percentage (Enthrall).
- **A proficiency roll with explicit gates.** The roll in `StdAbility.java:1441-1485` maps to a small function. Use a random source with a known range and state the assumption (the Java range of `rollPercentage` was not verified):

  ```python
  def skill_roll(prof: int, xlevel: int, gate: int, rng) -> bool:
      chance = min(95, max(5, prof + 2 * xlevel)) + xlevel // 2
      if gate >= 0:
          chance += gate
      elif rng.randint(1, 100) > 100 + gate:
          return False
      return rng.randint(1, 100) < chance
  ```

- **One event pipeline for vetoes and rewrites.** Give every action an `Event` object with `source`, `target`, `kind`, `value` and `redirect(to)`. Effects implement `can(event)` and `after(event)`. Endless Road is then: if the event is a move by the victim to a different room, redirect it to the victim's current room. Ensnare is: veto moves and decrement a counter. Keep `value` as the "resisted or halved" flag and write the convention in one place. Exhaustion's inverted test is what happens without that.
- **Creation as temporary items.** A `fake` item is an ordinary item with an `expires_at` tick and an on-expiry hook. Fake Weapon, Armor, Food and Spring become one `make_temporary_item(template, ticks)` function with four templates. Fabricate adds a crafting lookup. Duplicate and Fabricate share a `disenchant(item)` helper.
- **Enchanting with one economy.** Enchant Armor, Weapon and Arrows follow one rule: a capped counter on the item, paid in XP. Put the cap and the XP cost in a table, and do the XP payment before the roll only if the design should punish fizzles. A single-player game can refund on fizzle.
- **Simplify.** Merge the seven fizzle helpers into one with message templates. Merge the three enchant spells into one function with a table of target types. Drop `Dream`'s world scan in favor of the caster's current area. Replace the nine familiar animals with a list of data records that a `Companion` class reads.
- **Single-player changes.** There are no other humans, so these need redesign rather than porting:
  - Flagportation uses clan flags. Replace it with waystone or gate items the player places.
  - Feign Death is designed around other players not seeing the caster. A single player meets only NPCs, so it becomes a stealth or evasion effect.
  - Dream's broadcast to every sleeper is a world event, not a player message.
  - The dispel windows compare caster and invoker levels. Keep them for NPC casters and drop them for player-only rules.
- **Borrow the content.** The bomb scan over room, container, exit and shop stock makes a good "traps" system. Fake Food's name comes from the recipe table, so a Python SUD can name generated food from its own recipes. The disguise nouns and the familiar table are small and reusable as flavor data.

## Coverage

**Read in full** (method bodies, with license and import headers and one-line `ID()`, `name()` and `displayText()` accessors filtered from the view): all 53 spell files in `Abilities/Spells/` listed in the manifest, and `Spell.java` (the base class).

**Read in part**:
- `StdAbility.java` at 474-500, 899-925, 1424-1560, 2140-2220, 2273-2349, plus `invoke` and `executeMsg` helpers as cited.
- `Spell_Imprisonment.java` at 195-225 (`getPlanarDiff`).
- `Dice.java` at 398-406.
- `CoffeeUtensils.java` at 704-771 (`disenchantItem`).
- `Sense.java` at 635-645, `StdMOB.java` at 1394-1412 and 4977-4990, `DefaultPhyStats.java` at 222-224.
- `CraftingSkill.java` at 1279-1286 (`matchingRecipeNames`), `ItemCraftor.java` at 120-140, `WorldHuntLibrary.java` at 180-192 (signatures only).
- `CharState.java:75`, `Ability.java:651`, `coffeemud.ini` lines 406 and 2151.
- `CharClasses/*.java`, searched by grep for each spell ID (the level table).

**Skipped**: the cited helpers in other libraries were read only where the spell depended on them. Prayer, Song and Race files that reference spells (`Prayer_AuraFear.java`, `Play_Symphony.java`, `Races/Lich.java`, `Races/Beholder.java`) were only grepped to find references. `Spell_SpottersOrders` (a Diviner level-18 spell outside this bucket) was noticed in grep output and not read.

**Citation convention**: spell files in this bucket are cited by base name and line (`Spell_Fabricate.java:210`), which is unique within the spell directory. All other files are cited relative to the CoffeeMud folder (`com/planet_ink/coffee_mud/...`), except for the config file `coffeemud.ini`, which is cited by its line numbers.
