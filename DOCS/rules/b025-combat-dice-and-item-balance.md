# Combat, dice and item balance

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b025` (Combat, dice and item balance) · 6 files · 7269 lines of Java.

## Summary

CoffeeMud fights with one engine, `MUDFight` (the `CombatLibrary`, reached as `CMLib.combat()`). Each mob's action points become melee swings on its own 4-second tick, and each swing runs as a chain of combat messages: attack roll, damage, saves, damage-word text, then death if HP runs out. Experience for a kill is shared out at the moment of death. Nearly every number lives in `coffeemud.ini` as a `FORMULA_*` string, evaluated by a small left-to-right expression language in `CMath`, so balance can be retuned without recompiling. `TimsLibrary` (the `ItemBalanceLibrary`, `CMLib.itemBuilder()`) gives each weapon and armour a single integer "power level" and derives or repairs its damage, attack, armour, value and weight from that level. `Dice` supplies the random primitives: a 1–100 percentile roll, which hit, crit and save tests all use, and for hits a 5–95 clamp on the score. For a single-player text game, the reusable parts are the message-chain structure, the data-driven formulas, the hit and crit model and the item-level abstraction. Formations, group XP splits, PK and clan rivalry, and corpse looting by others mostly do not apply.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/MUDFight.java` | `CombatLibrary` implementation (ID `MUDFight`, line 48). Attack, armour and damage formulas; hit, crit and save rolls; damage text; the death pipeline; XP division; ranges and formations; the per-mob melee tick. | 3817 |
| `com/planet_ink/coffee_mud/Libraries/TimsLibrary.java` | `ItemBalanceLibrary` implementation (ID `TimsLibrary`, line 47). Item power level; weapon and armour stat generation; tone-up and tone-down; enchantment; base value. | 1626 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/CombatLibrary.java` | Interface with javadoc for each combat entry point. Defines the `CombatSystem` enum (`DEFAULT`, `QUEUE`, `MANUAL`, `TURNBASED`, lines 51-82) and `TICKS_PER_SHIP_COMBAT = 4` (line 87). | 854 |
| `com/planet_ink/coffee_mud/Libraries/Dice.java` | Random primitives (ID `Dice`, line 28): `roll`, `rollPercentage`, `normalizeAndRollLess`, NPC HP encoding, pick and scramble. | 414 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/DiceLibrary.java` | Interface and javadoc for `Dice`. | 391 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/ItemBalanceLibrary.java` | Interface for `TimsLibrary`. | 167 |

Outside the bucket, read only where the combat code depends on them:

- `coffeemud.ini`: 291 (`COMBATSYSTEM`), 299 (`SHOWDAMAGE`), 340 (`INJURYSYSTEM`), 374 (`PLAYERDEATH`), 381 (`MOBDEATH`), 691 (`RECOVERRATE`), 2151 (`TICKTIME`), 2334–2498 (every combat `FORMULA_*` key).
- `core/CMath.java`: the expression compiler and evaluator (1496–1520, 1621–1700, 1758–1880).
- `resources/lists.ini`: lines 18–540, covering damage words, hit and miss text, the health chart, corpse blurbs and the adjective and noun lists.
- `MOBS/StdMOB.java`: the mob tick and message dispatch (okMessage gate 2459–3190; executeMsg 3412–3480, 3590, 3742; tick 4129–4212).
- `Items/Armor/StdArmor.java:639-650` (worn armour subtracts from the wearer's armour stat), `Commands/CharGen.java:182` (new characters start at armour 100), `Items/interfaces/Weapon.java:49-160` (damage-type and weapon-class codes), `core/CMLib.java:105,127,134,400-402,460` (accessors).

## How it works

### 0. The formula language

Every combat number that matters is a string from `coffeemud.ini`, loaded in `MUDFight.activate()` (MUDFight.java:90-124) and compiled by `CMath.compileMathExpression`. The evaluator (CMath.java:1758-1800) has three properties that you need in order to read any of them:

- **No operator precedence.** Operators apply strictly left to right, so `2+3*4` is 20. Parentheses start sub-groups.
- **`@xN` is input N** (1-based) from the `vars` array the Java passes in. **`@xx` is the running total of the group one level up, as it stood when the current group was entered.** So in `(…)-(0.5*@xx*@x8)`, `@xx` is the total so far, and the term means "half of that total, if flag x8 is set". Penalties compound, because each one is computed on what remains.
- **Non-standard operators.** `a?b` is a uniform roll between `a` and `b` (CMath.java:1796-1797, `((cur - final + 0.5) * rand.nextDouble()) + final`). `a<b` is the minimum and `a>b` the maximum (CMath.java:1799-1803). `a^b` is a power.

Worked values in this chapter were produced by re-implementing these rules and evaluating the shipped strings. CoffeeMud itself was not run. "Low" means a random operand resolved to its minimum.

### 1. Clock and round structure

- A tick is `TICKTIME=4000` ms by default (coffeemud.ini:2151). Mob melee runs on each mob's own tick. It does not run on a combat thread.
- `MUDFight.activate()` starts a service tick (MUDFight.java:126-130) at `TICKS_PER_SHIP_COMBAT`. Its `tick()` (3751-3768) calls only `tickAllShips()` (3699-3720). The name suggests a fight thread for all combat, but mob-to-mob melee is driven from `StdMOB.tick`.
- Each mob tick (StdMOB.java:4141-4144): if no commands are queued, the fractional part of `actions` is dropped, and then `actions += speed`, or `speed/2` when sitting. `speed` defaults to 1.0 (Common/DefaultPhyStats.java:63).
- If the mob is in combat (StdMOB.java:4205-4212), the DEFAULT combat system gives one bonus action (4207-4208), and then `CMLib.combat().tickCombat(this)` runs (4212) when `canAutoAttack` holds.
- `tickCombat` (MUDFight.java:3350-3404) decides how many swings to make:
  - `saveAction = (system != DEFAULT) ? 0 : 1` (L3376). Under DEFAULT one action point is kept back for a skill, and "all other action points are automatically used on basic attacks" (CombatLibrary.java:53-57).
  - Under DEFAULT the swing count is capped at `speed + 0.9` (L3378-3383).
  - Each swing spends 1.0 action (L3393). A swing needs `actions >= 1.0`. Only slot `s==0` may fire without standing (L3391).
  - Each tick a fighter loses one movement point when `rollPercentage() > CON*4` (L3399-3400).
  - With speed 1.0 under DEFAULT, that works out to one swing per 4-second tick. This is derived from the rules above and has not been timed in-game.
- The combat system comes from `COMBATSYSTEM` (coffeemud.ini:291, default `DEFAULT`). `TURNBASED` skips the normal tick and calls `doTurnBasedCombat` (MUDFight.java:314-345 from StdMOB.java:4129-4132). That gives the turn to the next in-combat inhabitant in room order.

### 2. Targets, range and formation

- `properTargets` (MUDFight.java:163-213): non-malicious skills target the caster's group in the room. Malicious skills target `allCombatants` when in combat, otherwise `allPossibleCombatants` (141-160). If every candidate is out of range, the caster gets "You aren't in range!" (L207).
- `allCombatants` (427-457) returns the current victim plus any seeable inhabitant who is not in the mob's group and does not share its victim.
- Range is an integer per attacker (`rangeToTarget`), set by `establishRange` (3189-3200) through `calculateRangeToTarget` (3059-3186). For a follower it is the follower's position in the leader's formation plus the range of whoever in that formation is already engaged (3141-3176). Riders inherit range from their mount (3078-3122).
- AUTOMELEE (`subtickAttack`, 3202-3248) makes a mob advance ("advance(s) at", L3228) when out of reach and retreat ("retreat(s) before", L3237, needs movement at least 25) when too close. Otherwise a swing needs `minRange <= range <= maxRange` for the weapon, or range 0 when unarmed.
- Formations: `getFormation` (MUDFight.java:1346) builds rows from the leader's follower order (1317-1342). In `handleBeingAssaulted` a front-line follower takes an attack that was aimed at a leader who is not in combat (2897-2931).
- `makeFollowersFight` (2981-3017): when a fight starts, followers of the attacker or victim join it. Observers are skipped if their AUTOASSIST attribute is set (the comment at 2908 says a set attribute means "do not auto-assist") or if they are IS_UNHELPFUL.

### 3. The attack roll

Attack side:

- `adjustedAttackBonus(mob, target)` (MUDFight.java:216-238) evaluates `FORMULA_ATTACKADJUSTMENT` (coffeemud.ini:2344):
  `(50+@x1+(((@x2-9)/5)*((@x3-9)/5)*((@x3-9)/5))+@x4)-(0.15*@xx*@x5)-(0.15*@xx*@x6)-(0.3*@xx*@x7)`
  - `@x1` is the base attack adjustment. `@x2` is current STR capped at max, `@x3` is base STR capped at max, and `@x4` is STR above max. `@x5` hungry, `@x6` thirsty, `@x7` fatigued.
  - The baseline is 50. Hungry multiplies the running total by 0.85, thirsty by 0.85 again, and fatigued by 0.7.
- `fightingProwessStr` (1946-1951) subtracts the 50 back out for the displayed text only.

Defence side:

- `adjustedArmor(mob)` (MUDFight.java:348-374) evaluates `FORMULA_ARMORADJUSTMENT` (coffeemud.ini:2352):
  `(@x1-( (((@x2-9)/5)*((@x3-9)/5)*((@x3-9)/5*@x8)) +(@x4*@x8)-(0.15*@xx>0*@x5)-(0.15*@xx>0*@x6)-(0.3*@xx>0*@x7)*@x9))-100`
  - `@x1` is `phyStats().armor()`. New characters start at 100 (Commands/CharGen.java:182). Each worn armour piece subtracts its points from that number (Items/Armor/StdArmor.java:639), and some worn items subtract more for ability (644-650). The trailing `-100` puts a naked average character near 0.
  - Dex bonuses apply only when not sitting (`@x8`). Hunger and thirst take 15% of the running Dex bonus each, and fatigue takes 30%.
  - The trailing `*@x9` reads as if it applies only to the fatigue term. Under left-to-right evaluation it multiplies the whole inner running total, so while asleep (`@x9` is 0) the Dex bonus and every penalty go to zero (see Problems).
- **Lower adjusted armour is better.** `armorStr` shows `-adjustedArmor` (MUDFight.java:1904), so the displayed protection is the negation.

The hit test is `rollToHit(int attack, int defence, int adjustment)` (MUDFight.java:409-424):

```
myArmor = -defence;  if 0 -> 1.0;  if negative -> myArmor = -1/myArmor      (so defence > 0 becomes 1/defence)
hisAttack = attack;  if 0 -> 1.0;  if negative -> hisAttack = -1/myArmor    (L422, see Problems)
score = round(50 * hisAttack / myArmor) + adjustment
hit when normalizeAndRollLess(score)
```

- `normalizeAndRollLess(s)` is `rollPercentage() < clamp(s, 5, 95)` (Dice.java:62-77). `rollPercentage()` is `abs(nextInt() % 100) + 1`, so 1 to 100 (Dice.java:408-412). **The effective hit chance is therefore (clamp(s,5,95) − 1)%, which runs from 4% to 94%.**
- `adjustment` comes from `rollToHit(mob, defender)` (MUDFight.java:394-406). It is `FORMULA_ATTACKFUDGEBONUS=@x3 * (@x1 - @x2) * (@x1 - @x2)` with `@x3` set to 1 when the attacker is higher level and −1 otherwise (coffeemud.ini:2359). That is sign(diff) × diff², added to the score points. PvP uses `FORMULA_PVPATTACKFUDGEBONUS=0` (coffeemud.ini:2360).

Worked hit chances (attack 50, no fudge):

| Target adjusted armour | Score before clamp | Chance |
|---|---|---|
| 0 (naked, average) | 2500 | 94% |
| −10 (base-90 animal, `Rat.java:60`, assuming average DEX) | 250 | 94% |
| −25 | 100 | 94% |
| −50 (50 points of protection) | 50 | 49% |
| −75 | 33 | 32% |
| −100 (base-0 NPC, e.g. `StdBanker.java:67`) | 25 | 24% |
| +10 (armour pushed above 100) | 25000 | 94% |

Other attack-side values: base attack 0 with STR 10 gives 50.01, with STR 18 gives 55.8, and with hunger gives 42.5. A +5 level gap on the attacker adds +25 to the score, and a +10 gap adds +100.

### 4. Damage

Entry point: `adjustedDamage(mob, weapon, target, bonusDamage, allowCrits, biasHigh)` (MUDFight.java:915-1036). `postWeaponAttackResult` calls it with `allowCrits=true` (L1201). Other callers are the damage-prowess displays and the static helper at 1039-1059.

**Stage 1, base damage** (L918-980):

- Ranged and thrown weapons use the weapon's `phyStats().damage()`. Everything else uses the mob's damage plus the bonus.
- Targeted melee, `FORMULA_DAMAGEMELEETARGETED` (coffeemud.ini:2424):
  `((1?@x1)+((@x2-10+@x3-@x4)/5)-(0.5*@xx*@x8)+(0.5*@xx*@x9)+(0.2*@xx*@x10)-(0.2*@xx*@x5)-(0.2*@xx*@x6)-(0.2*@xx*@x7))>1`
  - `(1?@x1)` is a uniform roll from 1 to the weapon's damage. `@x2` is STR (capped), `@x3` attacker level, `@x4` defender level. `@x8` is 1 if the attacker cannot see the target, `@x9` is 1 if the target is asleep, and `@x10` is 1 if it is sitting. `>1` floors the result at 1.
  - Each modifier is a fraction of the running total: cannot see −50%, asleep +50%, sitting +20%, and hungry, thirsty or fatigued −20% each.
- Ranged targeted (coffeemud.ini:2397) uses `(@x3-@x4)/2.5` in place of the STR and level term. Static forms with no target (2406, 2433) use defender level 0.
- PvP forms drop the level term. Ranged PvP (coffeemud.ini:2398) has no STR or level term at all. Melee PvP (2425) keeps the STR term but drops the level term. The PvP fudge formulas are plain `@x1` (ranged 2415, melee 2442). PvP crit and spell crit also drop the level term (2452, 2461).

**Stage 2, critical hit** (L1005-1021). This uses a second vars array, built at L994-1004: `{damage so far, curDex, baseDex, dexBonus, hungry, thirsty, fatigued, attacker level, defender level}`.

- Chance: `FORMULA_CHANCEWEAPONCRIT` (coffeemud.ini:2451) is `((((@x2-10+((@x8-@x9)<10))/2.5)>0 * ((@x3-10+((@x8-@x9)<10))/2.5)>0 * ((@x3-10+((@x8-@x9)<10))/2.5)))`, plus `STAT_CRIT_CHANCE_PCT_WEAPON` (L1007-1008). It is a cube of `(DEX − 10 + min(levelgap, 10)) / 2.5`. The test is `rollPercentage() < chance` (L1009), and it always passes when `biasHigh` is set.
- Worked values, equal level: DEX 10 gives 0%, 12 gives 0.5, 14 gives 4.1, 16 gives 13.8, 18 gives 32.8, 20 gives 64. **The chance is not clamped.** DEX 16 with a +10 level advantage evaluates to 262 (see Problems).
- Damage: `FORMULA_DAMAGEWEAPONCRIT` (coffeemud.ini:2460) is `(@x1 * (cube)/50.0)+(@x4/2)`. `@x1` is damage so far and `@x4` is DEX above max. The result is added to damage (L1017), plus `STAT_CRIT_DAMAGE_PCT_WEAPON` percent of it (L1018-1019). For base 6 at DEX 16 the bonus is 1.66.

**Stage 3, level fudge** (L1022-1034). `FORMULA_DAMAGEMELEEFUDGE` (coffeemud.ini:2441) is `(@x1 - ( ((@x9-@x8)>0<1.0) * @x1 * (((@x9-@x8)<12.0)*((@x9-@x8)<12.0))/120.0))>0`. It runs only when there is a target, and with d = defender level − attacker level:

- d ≤ 0: no change.
- 1 ≤ d ≤ 10: damage × (1 − d²/120). For base 10 this gives 9.92 at d=1, 7.92 at d=5, and 1.67 at d=10.
- d ≥ 11: damage is zero.
- Ranged uses the same shape (coffeemud.ini:2414). PvP uses `@x1` alone (melee 2442).

The result is rounded (L1035).

**Spells.** `modifySpellDamage` (MUDFight.java:875-912) applies to ability sources when the caster owns the ability's item or is above level 1 (L835-836). It uses `FORMULA_DAMAGESPELLFUDGE` (coffeemud.ini:2387) and the spell crit pair (2369, 2378), with INT in place of DEX and `/5` in place of `/2.5`, plus the `*_PCT_MAGIC` stats. Other damage sources call `postDamage` (MUDFight.java:819-873). Some 216 files in the tree reference `postDamage(`, for example `Abilities/Spells/Spell_Fireball.java:109`.

**Stage 4, text.** The message carries a `<DAMAGE>` or `<DAMAGES>` token, and `replaceDamageTag` (MUDFight.java:737-782) replaces it with a damage word for the final value (`standardHitWord`, 1848-1863). `SHOWDAMAGE` (coffeemud.ini:299; default `NO`) can add the number for `YES`, `SOURCE` or `TARGET`.

**Stage 5, saves.** See section 5 for where these run.

- `checkDamageSaves` (MUDFight.java:2348-2408). For a weapon hit the save depends on damage type: BASHING → `STAT_SAVE_BLUNT`, PIERCING → `STAT_SAVE_PIERCE`, SLASHING → `STAT_SAVE_SLASH`. Every other type returns without a save (L2365-2366). For an ability the save is the class save (chants, prayers, spells or songs) plus `STAT_SAVE_MAGIC` (L2377-2391). The chance is clamped to ±100. If `rollPercentage() < |chance|`, the damage becomes `value × (100 − chance)/100` (L2404-2405). A negative save makes the hit stronger.
- `checkSavingThrows` (2443-2488) applies to non-weapon malicious messages that are not yet resisted. Chance = class save + save for the message type. Then `+ sign(diff) × diff²`, where diff = target level − source level (L2474-2476). The result is clamped to 5–95 (L2477-2481). On a successful roll, `resistanceMsgs` (2121-2345) adds a flavour line such as "<S-NAME> shake(s) off the mental attack from <T-NAME>." and raises `msg.value()` by 1 (L2344).
- Worked: a target 5 levels above the source adds +25 to its resist chance, and a 10-level gap adds +100, clamped to 95.

Worked melee damage, base 5, STR 16, attacker and defender both level 10: 2.2 at the low roll and 6.7 at the high roll, so 2 to 7 after rounding. Against a sleeping target the range is 3.3 to 10.05. Against a sitting target it is 2.6 to 8.0. If the attacker cannot see the target it is 1.1 to 3.35.

### 5. The message pipeline

Each swing is a `CMMsg` (`Common/interfaces/CMMsg.java`). The relevant codes are:

- `MSG_WEAPONATTACK = MASK_HANDS|MASK_MOVE|MASK_SOUND|MASK_MALICIOUS|TYP_WEAPONATTACK` (CMMsg.java:1455)
- `MSG_DAMAGE = MASK_ALWAYS|TYP_DAMAGE` (1555), `MSG_DEATH` (1521), `MSG_PANIC` (1537), `MSG_ATTACKMISS` (1625)

The sequence for one melee swing:

1. `postAttack` (MUDFight.java:580-601) builds `MSG_WEAPONATTACK` and sends it to the room with `okMessage` and then `send`. It returns `msg.value() > 0`.
2. The target's `StdMOB.okMessage` (MOBS/StdMOB.java:2459). For malicious messages aimed at the mob (gate at 3106-3110) it runs `checkSavingThrows` (3183). Other listeners may veto here.
3. The target's `StdMOB.executeMsg` (3412). For malicious messages it runs `handleBeingAssaulted` (3742; MUDFight.java:2877-2979). **The attacker's roll is made on the defender's side here.** If the defender is in combat (the first hit puts it there), `rollToHit(attacker, target)` decides (2959). A hit calls `postWeaponAttackResult` (1190-1237), which calls `postWeaponDamage` (1128-1187), and sets `msg.setValue(1)` on the attack message (2962).
4. The `MSG_DAMAGE` message goes through the target's `okMessage`, which runs `checkDamageSaves` (StdMOB.java:3240).
5. The target's `executeMsg` runs `handleBeingDamaged` (StdMOB.java:3469; MUDFight.java:2566-2630). It applies the HP loss and the statistics, and then:
   - If HP falls below 1, `postDeath` (2598).
   - Otherwise: bleeding is applied if a single hit took at least `INJBLEEDPCTHP` percent of max HP, the weapon can bleed, and the target's level is at least `INJBLEEDMINLEVEL` (2601-2608). Panic is started if HP is below the wimp level while in combat (2609-2612). An Injury effect is added if the HP percentage is at or below `INJPCTHP` (2614-2626).
6. `postDeath` (MUDFight.java:499-577) sends a `TYP_DEATH` message. `executeMsg` routes it to `handleDeath` (StdMOB.java:3590; MUDFight.java:2633-2688), which calls `justDie` (1514).

Injury settings: `INJURYSYSTEM=100,60,25,100,2,10,15,20,100` (coffeemud.ini:340). `CMProps` reads the second field as `INJPCTHP` (value 60; falls back to 40 when absent, CMProps.java:3162), and the seventh and eighth as the bleed level (15) and bleed percent (20; CMProps.java:3167-3168).

### 6. Damage words, hit text and health

- Thresholds: `DAMAGE_WORDS_THRESHOLDS=0,3,6,10,15,25,35,50,70,100,130,165,215,295,495,795,2147483647` (resources/lists.ini:441). The word lists are in `DAMAGE_WORDS` (lists.ini:448-495), one row per damage type with 16 words each.
- `standardHitWord(type, damage)` (MUDFight.java:1848-1863) picks the first band where `damage <= threshold[i]` and uses word `i` (0-based), clamping to the last word. For SLASHING the bands are:

| Damage up to | SLASHING word | Damage up to | SLASHING word |
|---|---|---|---|
| 0 | annoy(s) | 70 | massacre(s) |
| 3 | scratch(es) | 100 | MASSACRE(S) |
| 6 | graze(s) | 130 | destroy(s) |
| 10 | wound(s) | 165 | DESTROY(S) |
| 15 | cut(s) | 215 | obliterate(s) |
| 25 | slice(s) | 295 | OBLITERATE(S) |
| 35 | gut(s) | 495 | **OBLITERATE(S)** |
| 50 | murder(s) | 795 | --==::OBLITERATE(S)::==-- |
| | | above 795 | (last word again) |

- Damage types (`Items/interfaces/Weapon.java:49-81`): NATURAL 0, SLASHING 1, PIERCING 2, BASHING 3, BURNING 4, BURSTING 5, SHOOT 6, FROSTING 7, GASSING 8, MELTING 9, STRIKING 10, LASERING 11, SONICING 12, DISRUPTING 13, STULTIFYING 14, CORRUPTING 15, SCRAPING 16.
- Weapon classes (Weapon.java:138-160): AXE 0, BLUNT 1, EDGED 2, FLAILED 3, HAMMER 4, NATURAL 5, POLEARM 6, RANGED 7, SWORD 8, DAGGER 9, STAFF 10, THROWN 11.
- Hit and miss templates are chosen by attack family (`getWeaponAttackIndex`, MUDFight.java:2053-2077). RANGED maps to row 0 (LASERING to 5), THROWN to 1, SLASHING and BASHING to 2, PIERCING to 4, SHOOT to 0, LASERING to 5, and everything else to 3. The rows are in `WEAPON_HIT_DESCS` (lists.ini:429-439), `WEAPON_MISS_DESCS` (403-413) and `MISS_DESCS` (415-427).
- Health text: `standardMobCondition` (MUDFight.java:2109-2118) indexes `HEALTH_CHART` (lists.ini:497-512) by `floor(hp/maxhp × 10)`, so 0 is "hovering on deaths door!" and 10 is "is in perfect health." Only exactly full HP gives 10.

### 6a. Death

`justDie` (MUDFight.java:1514-1722) runs the post-death pipeline:

1. Achievements (DEATHS, for players, 1521-1527).
2. Who gets XP: `getCombatDominantClass` (1419-1442) and `getCombatBeneficiaries` (1444-1477). Dividers come from `isValidClassDivider` (1479-1512).
3. Anyone whose victim was the dead mob is given a new victim (`pickNextVictim`, 1536-1543 and 2779-2828).
4. XP is dispensed (section 7).
5. Gold: if killers have AUTOGOLD, the dead mob's currency is split between them (1551-1568, 1670-1698).
6. Base XP loss: `expLost = max(100, 100 × level)` (1570-1572).
7. Death rules come from config. `MOBDEATH` (default `DEATH`, coffeemud.ini:381) applies to monsters and soul-mates. `PLAYERDEATH` (default `(@x1 * 50) < 2000`, coffeemud.ini:374) applies to players, so a player loses the smaller of 50 × level and 2000 experience.
8. Corpse: `killMeDead(true)` unless `RECALL` is listed or the mob is knocked out (1580-1597). The killer's name and weapon are recorded (1611-1618).
9. Disease_Amnesia: for a player death, a 1 in 100 chance (`rollPercentage() == 1`, 1620-1630).
10. Soul-mate: the spirit returns to its body (1632-1641).
11. Autoloot: the killer takes items from the corpse when set (1643-1668).
12. Victims are cleared. A body marked destroy-after-looting is removed (1700-1719).

Death consequences (`handleCombatLossConsequences`, MUDFight.java:3507-3674) read the config list one entry at a time:

- `UNLEVEL [n]`: lose n levels (3528-3536).
- `RECALL`: `killMeDead(false)`, so no corpse (3538-3539).
- `ASTRAL` or `ASTRAL_R`: become a spirit through `Prop_AstralSpirit`. The `_R` form allows self-resurrection (3541-3557).
- `OUT <expr>`: knocked out for `expr` ticks through `Skill_ArrestingSap`, then reset to max state (3559-3568). `isKnockedOutUponDeath` (3407-3423) reads the same keyword.
- `PURGE [n]`: permadeath after n lives. The player is obliterated in a background runnable (3570-3615). Remaining lives are reported (3617-3624).
- `LOSESKILL`: forget a random ability (3628-3645).
- `EXPERIENCE`: lose the base XP from step 6 (3660-3671).
- A bare expression: lose that many experience points (3647-3658). Its variables are `@x1` the level, `@x2` the killer's level, and `@x3` the rejuvenation value.

### 7. Experience

`dispenseExperience` (MUDFight.java:3302-3347):

- Pool: `FORMULA_TOTALCOMBATXP=100 + ((25*@x1) - (@x1*((25*@x1)^.5)))` (coffeemud.ini:2489), where `@x1` is the number of dividers. That is 100 + 25n − 5n^1.5.
- Share: `FORMULA_INDCOMBATXP=(@x1 * (@x2 / @x3)) < 100` (coffeemud.ini:2498) is pool × (killer level² ÷ sum of divider level²), capped at 100 by the `<`.
- Each beneficiary receives `postExperience(mob, "COMBAT:"+killedLevel+flag, ...)` (MUDFight.java:3345).

Worked values with equal-level dividers, each at level 10:

| Dividers | Pool | Share each, capped at 100 |
|---|---|---|
| 1 | 120.0 | 100 |
| 2 | 135.9 | 67.9 |
| 3 | 149.0 | 49.7 |
| 4 | 160.0 | 40.0 |
| 6 | 176.5 | 29.4 |
| 8 | 186.9 | 23.4 |

The pool peaks at 192.6 for 11 dividers and falls back to 100 at 25.

### 8. Recovery and survival

- `recoverTick` (MUDFight.java:1062-1126) is called from StdMOB (4198) on the recovery interval. The interval is `RECOVERRATE` (coffeemud.ini:691, default 1), scaled by a `CharState` constant that was not traced. It does nothing while the mob is in combat.
- HP: `FORMULA_HITPOINTRECOVER=5+((@x2/2.0)+(((@x1/2.0)<13) - (@xx*@x3/2.0) - (@xx*@x4/2.0)) + (@xx*@x6*.7) + (@xx/4.0*@x7) - (@xx/2.0*@x9))` (coffeemud.ini:2468). Inputs are CON, level, hungry, thirsty, fatigued, asleep, sitting or riding, flying and swimming. Golems get no HP (1101).
- Mana, `FORMULA_MANARECOVER` (2475), uses INT plus WIS. Movement, `FORMULA_MOVESRECOVER` (2482), uses STR.
- Fatigue is added on recovery unless the mob is asleep, sitting or infatigueable (1116-1121).
- Worked HP per tick at CON 10 and level 1: standing 10.5, sitting 11.9, asleep 14.4, hungry 8.0, hungry and thirsty 6.75. At CON 18 and level 10: awake 19.0, asleep 28.8.
- `expendEnergy` (MUDFight.java:3426-3504) spends movement per tick (`pointsPerMove`, with an extra cost when overweight). Hunger falls by `baseWeight/500` per round. Thirst falls by the room's `thirstPerRound()` times that factor. The factor is floored at 0.001. Dying of thirst or hunger starts when the `CharState.DEATH_*_TICKS` threshold is passed, with "YOU ARE DYING OF THIRST!" (3468-3471).

### 9. Dice

- `roll(n, die, mod)` is the sum of n uniform 1..die plus mod (Dice.java:397-406). `rollPercentage` is 1..100 (408-412). `rollInRange(min, max)` is at 162-184.
- `rollNormalDistribution` (255-270) takes three uniform draws per die and returns `1 + floor(sum/2.9999)`, a roughly triangular spread. `rollLowBiased` (272-287) takes `|Gaussian × die| × 0.5` with rejection.
- NPC HP: `FORMULA_NPCHITPOINTS=3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))` (coffeemud.ini:2337). `@x1` is level and `@x2` is `MOB_HP_BASE`, default 11 (CMProps.java:309). Worked: level 1 gives 13.0 (mid roll) to 15.5 (high); level 5 gives 53 to 65.5; level 10 gives 103 to 128.
- A packed HP code (`getHPCode` 305-365, `rollHP` 79-96) stores roll count in bits 23 and up, die size in bits 15–22 (both at most 255), and a plus in bits 0–14 (at most 32767). Codes below 32768 use the formula above instead.
- `normalizeBy5` (68-77) and `normalizeAndRollLess` (62-65) provide the clamp and percentile test used by every hit.
- `biasHigh` passes `CMath.NotRandomHigh` (CMath.java:1496-1520) into the formula evaluator. The prowess displays use it (MUDFight.java:2012, 2016), and `WebMacros/PlayerData.java:177` does too. Its `nextInt()` returns the maximum and `nextInt(bound)` returns `bound-1`, but `nextDouble()` is not overridden and comes out at about −1.5e-8. `Tests/Notrandom.java:61-64` prints these values. So a `?` roll resolves to its low end, which is the opposite of the name (see Problems).

### 10. Item balance (TimsLibrary)

Each weapon and armour has an integer **power level**. Its stats are a deterministic function of level, material, class, worn location and any adjuster effects. The library works in both directions.

**Forward.** `timsItemAdjustments(I, level, material, hands, wclass, reach, worn)` (TimsLibrary.java:1168-1236):

- Weapon. Base attack is the class attack modifier (`getWeaponAttackModifierFromClass`, 81-97): FLAILED −5, EDGED and DAGGER +10. Minimum reach is 1 for POLEARM, RANGED and THROWN, and maximum reach is 5 for RANGED and THROWN (`getWeaponReachFromClass`, 900-944).
  - `damage = round( ((level−1)/((reach/weight)+2) + (weight−baseAttack)/5 − reach) × ((2·hands+1)/2) × dmgMod )` (L1199). `dmgMod` is 1.1 FLAILED, 0.8 EDGED, 0.5 DAGGER, 0.9 BLUNT and STAFF, 1.5 THROWN (57-79).
  - Attack adds `round(level × attMod)` (L1200), where attMod is −0.3 FLAILED, 0.6 EDGED, 1.3 DAGGER, 0.3 BLUNT and STAFF (99-118).
  - Material adjustments are then added to attack and damage (L1201-1202; 946-1164).
  - Value is `round(2·((weight·matValue) + (2·damage + attack + 10·reach)·damage) / ((hands+1)·(thrown+1)))` (L1206).
- Armour. `matPoints = getMaterialArmorPoints(material, level)` looks up a level table selected by material family (734-760 and 848-865). `ARMOR = round(Σ over worn locations of matPoints × location strength points) − material adjustment` (L1224-1229). Value is `round((matPoints² + matValue) × weightpts/2)` (L1227).

Worked weapon scaling, EDGED, one-handed, reach 1, weight 8, metal (from the forward formula):

| Level | DAMAGE | ATTACK |
|---|---|---|
| 5 | 1 | 13 |
| 10 | 3 | 16 |
| 20 | 9 | 22 |
| 30 | 15 | 28 |
| 40 | 20 | 34 |
| 60 | 32 | 46 |

Armour material tables (index = points, value = level needed; TimsLibrary.java:736-738):

- Leather, synthetic, glass, rock and wood: `0,0,1,5,10,16,23,31,40,49,58,67,76,85,94`
- Cloth (the default): `0,3,7,12,18,25,33,42,52,62,72,82,92,102`
- Metal, mithril, precious and energy: `0,0,0,0,1,3,5,8,12,17,23,30,38,46,54,62,70,78,86,94`

**Inverse.** `timsLevelCalculator(item, props)` (TimsLibrary.java:152-362) searches for the level whose forward stats match the item's stats. It runs about 60 steps of doubling, halving and ±1, comparing ARMOR for armour and attack plus damage for weapons (229-356). Results are cached in static tree maps (38-40, 357). `timsBaseLevel` (1390-1448) is the closed-form starting estimate.

**Adjusters and power.** Effects with an adjuster, resister, zapper or enabler flag, or with a caster flag, add to an item's total power (`getTimsAdjResCast` 379-412; `getItemLevels` 1326-1334). Ability points count five levels each (`levelsFromAbility` 1450). Adjuster effects also change base stats through `STAT-ARMOR`, `STAT-ATTACK` and `STAT-DAMAGE` (`timsBaseAdjustments` 1369-1388).

**Repair and balance.**

- `itemFix(I, lvl, preferMagic, changes)` (433-675). Spell holders take the average `lowestQualifyingLevel` of their spells. Weapons and armour are toned down when total power exceeds 1.1 × level, by repeatedly lowering the largest contributor. Toning down a weapon multiplies damage and attack by 0.9 when they are 10 or more, and subtracts 1 otherwise (`toneDownWeapon` 1239-1262). Armour works the same way (`toneDownArmor` 1265-1281). Items under 0.9 × level are toned up instead. Both loops stop after a 5-second cap (L492 and L580).
- `toneDownValue` only lowers price (678-700).
- `balanceItemByLevel` regenerates base stats from level (703-732). Callers are `Commands/CharGen.java:125,159` and `Commands/GModify.java:224`.
- `enchant(I, pct)` (1497-1624). With `pct`% chance, a random walk `bump` decides the enchantment: +1 with 80% chance, −1 with 20%, and it continues with 10%. A negative bump makes the item non-removable (1504-1505) and adds a curse (`Prayer_CurseItem`). Otherwise a weapon gets `setAbility(bump)` or a fight-spell property, and armour gets a wear-spell property. Those properties carry `bump` random spells from `getCombatSpell` (1478-1494).
- Callers: `Behaviors/ItemGenerator.java` (enchant), `Commands/Reset.java:2094,2162,2179` (itemFix), `Tests/Timsvalue.java` (calculateBaseValue).

### 11. Interactions with other systems

- Abilities: spells and other abilities call `postDamage` (MUDFight.java:819; `Spell_Fireball.java:109` and `Spell_AcidArrow.java:106` are examples). Malicious abilities go through the same `checkSavingThrows` gate. Effects named in combat code include `Prop_AstralSpirit`, `Skill_ArrestingSap`, `Fighter_Whomp`, `Bleeding`, `Injury`, `Disease_Amnesia`, `Prayer_CurseItem`, `Prop_FightSpellCast` and `Prop_WearSpellCast`.
- Leveling and XP: `_leveler().postExperience` (MUDFight.java:3345, 3653, 3666) and `unLevel` (3535).
- Achievements, clans, coffee tables and channels are notified from death handling (1521-1527, 2642-2662, 2732-2771).

## Content worth mining

- **Damage words.** Sixteen-word bands for each of 17 damage types, plus an ALL fallback row (`resources/lists.ini:448-495`). Sample BASHING: annoy, scratch, graze, hit, smash, bash, crush, crunch, then the shared top bands. Sample SLASHING: see the table in section 6. A text game can use these directly for hit flavour.
- **Hit and miss templates** by attack family (lists.ini:403-439). Two examples: "<S-NAME> swing(s) at <T-NAMESELF> with <TOOLNAME> and miss(es)." and "<S-NAME> fire(s) <TOOLNAME> at <T-NAMESELF> and <DAMAGE> <T-HIM-HER>."
- **Health chart** (lists.ini:497-512), eleven lines from "hovering on deaths door!" to "is in perfect health."
- **Corpse blurbs** by damage type (lists.ini:514 onward). Examples: SLASHING "slashed to hell", "sliced and diced", "slashed into pieces"; PIERCING "full of holes", "pierced to death"; BASHING "crushed and battered", "pummeled to goo"; BURNING "fried to a crisp", "burned almost beyond recognition".
- **Armour ladder** (`ARMOR_DESCS`, lists.ini:21-40), a 19-step scale from "vulnerable" through "covered" and "protected" to "heavily armored", with the ceiling set at 300 (line 18). The companion `ARMOR_ADJS` (74-89) runs "vulnerable, brittle, fragile, susceptible, exposed, unprotected, uncovered, covered, protected, defended, guarded, armored, unhittable, impenetrable, invulnerable."
- **Prowess words.** `COMBAT_ADJS` (222-235) runs "Powerless, Terrible, Inept, Weak, Clumsy, Marginal, Average, Able, Skilled, Masterful, Powerful, Dangerous, Deadly". `COMBAT_NOUNS` (238 onward) runs "Neophyte, Beginner, Novice, Initiate, Trainee, Rookie, Journeyman, Traveler, Voyager, Adventurer, Wanderer, Pioneer...". `EXTREME_ADVS` gives the intensifiers "Very, Extremely, Incredibly, Astoundingly, Stupendously, Drastically, Amazingly". `DAMAGE_ADJS` (386 onward) starts "infinitesimal, feeble, Impotent, Underpowered, Inferior, Normal, Superior, Over-powered, Potent, Mighty, Forceful".
- **Class modifiers** (TimsLibrary.java:57-118). A table of damage multipliers, attack bonuses and attack multipliers for each weapon class.
- **Material modifiers** (TimsLibrary.java:946-1164). For metal-based weapon classes (polearm, edged, dagger, sword), the material sets damage and attack as follows: wood or synthetic −4 and 0; leather −6 and −10; rock −2 and −10; glass −4 and −20; precious −4 and −10; gas and others −8 and −30. Resource overrides: bone +4 and +20; sand or coal −8 and −40.
- **The formula set itself** (coffeemud.ini:2334-2498). About forty `FORMULA_*` keys, including the PvP variants, that together define a combat model. This is the most reusable artefact in the bucket.
- **Death-rule keywords** (MUDFight.java:3507-3674): `UNLEVEL`, `RECALL`, `ASTRAL`, `OUT`, `PURGE`, `LOSESKILL`, `EXPERIENCE`, plus a bare XP expression.

## Notable design ideas

1. **Formulas as data.** The balance lives in an ini file, each formula documents its variables in comments, and `@xx` gives a running total with multiplicative penalties (coffeemud.ini:2339-2498).
2. **A chain of messages.** Attack, hit roll, damage, saves and death are separate messages, each able to veto or modify (okMessage) before its effect runs (executeMsg). That is why one call can add flavour trailers, saves and bleeding.
3. **Hit chance as a percentile against 50 × attack ÷ armour.** It is monotonic and bounded to 4–94%, and armour above 100 makes the defender always hittable.
4. **Crit as a cube of DEX.** High DEX is steeply rewarded. Crit damage scales with the damage already rolled.
5. **Percentage modifiers on the running total.** Asleep +50%, cannot see −50%, and so on. Penalties scale with the raw damage.
6. **Level-gap fudge.** To-hit gets sign(diff) × diff². Damage gets (1 − d²/120), which is zero from an 11-level gap. Both are simple and bounded.
7. **Diminishing XP pool.** 100 + 25n − 5n^1.5 rises and then falls, so large parties earn less each, and each share is capped at 100.
8. **Power level as the single item currency.** One integer drives every stat and the price. An inverse solver recovers the level from stats, so generators and repair tools share one table.
9. **Death as a string DSL.** Keywords such as `UNLEVEL 2`, `OUT (expr)` and `PURGE 3` are comma-chained in config.
10. **Band-based text.** Damage numbers map to verbs per damage type, which gives expressive feedback without showing numbers.
11. **Action-point modes.** DEFAULT reserves one action for skills. QUEUE, MANUAL and TURNBASED are alternatives, all selectable in config.

## Problems and cruft

All of these come from reading the code. None was tested in-game.

1. **"Biased high" rolls resolve low.** `CMath.NotRandomHigh` (CMath.java:1496-1520) makes `nextDouble()` about −1.49e-8. I replicated that in a standalone Java run. A `?` roll under `biasHigh` therefore resolves to its minimum, so the prowess display (MUDFight.java:2012-2016) shows low base damage. The forced crit at line 1009 still applies. Impact is probably confined to the display and to `WebMacros/PlayerData.java:177`.
2. **Negative-attack branch assigns the wrong variable.** `rollToHit` (MUDFight.java:421-422) sets `hisAttack = -CMath.div(1.0, myArmor)`. The intent looks like `hisAttack`. Both forms give a negative score that falls to the 5% floor unless the fudge is large. Attack values are normally near 50, so the branch is rarely reached. Unverified.
3. **Crit chance is not clamped** (MUDFight.java:1007-1009). The cube reaches 262 at DEX 16 with a +10 level gap, and `rollPercentage() < pct` then always passes. Spell crit (902-904) has the same issue.
4. **Precedence bug in `itemFix`** (TimsLibrary.java:500-502). The expression is `(a) || ((b && !preferMagic) || highest instanceof Ability)`. Once an `Ability` is the current candidate, every later key replaces it. The lowest-level loop at 589-590 groups correctly, so the two loops disagree. Probably unintended.
5. **Precedence in death and panic guards** (MUDFight.java:484-486 and 514-516). The test is `(source==x && minor==PANIC) || minor==DEATH`, so any death trailer from any source suppresses a new one. Probably meant `source==x && (...)`.
6. **Duplicate condition in corpse looting** (MUDFight.java:1650-1654). Both the `if` and the `else if` test `riding() instanceof MOB`, so the "disembark" message for item mounts can never print.
7. **Dead branch in gravity handling** (MUDFight.java:2514-2526). The `else if` repeats the outer condition `value >= ACCELERATION_UNCONSCIOUSNESS`, so the "pass(es) out from high acceleration" message is unreachable.
8. **Weight is hard-coded in the inverse.** `timsBaseLevel` sets `double weight=8;` (TimsLibrary.java:1398). The forward formula uses the item's own weight (1183, 1198). Items not weighing 8 therefore invert to a level that does not match their stats. Effect in-game unverified.
9. **Copy-paste in tone functions.** TimsLibrary.java:547 and 550 both set `TONEDOWN-MISC "90%"`, and 636 and 639 both set `TONEUP-MISC "110%"`. `toneUpWeapon` writes the key `TONEDOWN-WEAPON` (1288), which is probably wrong.
10. **Unguarded division.** `CMath.div(curArmor, weightpts)` (TimsLibrary.java:1433) divides by zero for an item with no worn location. `CMath.div` is a plain `a/b` (CMath.java:433-436).
11. **Exponential damage text.** With no weapon, `postSiegeWeaponAttackResult` prints `Math.round(Math.pow(2, damageInt))` as the damage number (MUDFight.java:1296). That is probably a slip.
12. **Dead or unfinished code.** `getAllInProximity` (MUDFight.java:3019-3039) builds an N×N matrix that is never used, then returns only the starting mob. `getCombatDominentKiller` (1391-1416) has no callers.
13. **Misleading tick name.** `MUDFight.tick` (3751-3768) only runs ship combat. The mob melee loop lives in `StdMOB`.
14. **Documentation mismatches.**
    - The `normalizeAndRollLess` javadoc says "between 0 and 100" (DiceLibrary.java:28-37). The code rolls 1–100 (Dice.java:411), so the effective hit chance is (score − 1)%.
    - The `INJURYSYSTEM` comment gives the default as `100,60,10,100,4,10,15,20,100` (coffeemud.ini:330-340). The shipped value is `100,60,25,100,2,10,15,20,100`.
    - The `armorStr` javadoc points to `PhyStats#armor()`, but the sign convention (displayed = −adjusted) is visible only in the code (MUDFight.java:1904).
15. **The evaluation order is a trap.** Without precedence, `(0.3*@xx>0*@x7)*@x9` in the armour formula multiplies the whole running total while asleep (coffeemud.ini:2352). Modders will misread these strings.
16. **Cliffs in the level fudge.** Damage goes to zero at an 11-level gap (coffeemud.ini:2441). The cut-off is abrupt.
17. **Attack fudge grows quickly.** With sign(diff) × diff², a 5-level gap adds 25 points and a 10-level gap adds 100 (coffeemud.ini:2359). A 10-level advantage therefore pushes the hit score past the 95 clamp, so the attacker hits at the 94% ceiling.

## Takeaways for a Python SUD

**Borrow:**

- **One resolution chain per action.** Write it as plain functions: `attack → hit_roll → damage → saves → apply → death`, with a list of hooks for effects. Use a small `Event` dataclass in place of CMMsg and its source, target and tool fields.
- **The hit model.** `p_hit = clamp(50 × attack / armour_points, 5, 95) / 100`, plus the sign-squared level fudge. Use the same clamp, and roll `randint(1, 100) < clamp(...)` so the 4–94% behaviour is preserved.
- **Balance as data, with a real parser.** Keep the formulas in TOML or JSON, but evaluate them with a whitelisted expression parser that follows normal precedence and provides `min`, `max`, `clamp` and `roll(n, d)`. Do not copy the left-to-right language. Its traps are the most useful lesson in this code.
- **Damage text by band.** A table from damage thresholds to verbs, per damage type. It is cheap and expressive.
- **Power level for items.** Store one `level` on each weapon and armour, and derive damage, attack, armour and value from tables. Skip the 60-step inverse search unless you need to import items.
- **Death as data.** A list of outcome keys, such as `knockout:<expr>`, `recall`, `lose_xp:min(50*level,2000)`, `lose_level:n` and `permadeath`, each mapped to a function.
- **Multipliers as a list.** Asleep +50%, cannot see −50% and similar effects read well as a list applied to the running damage.

**Simplify or drop (single player, no other humans):**

- Formations, followers taking hits, per-pair range, AUTOMELEE advance and retreat, clans and rivalries, PK and pride stats, corpse looting by others, group XP division, soul-mates, catalog bumps, channel announcements, and all PvP formula variants.
- Keep ranged and melee positioning only if the design needs it. A small enum (adjacent, near, far) is simpler than an integer per attacker.
- For a lone player, XP can be a fixed amount per kill. Keep the pool formula only if companions exist.

**Multiplayer assumptions to rethink:**

- The action-point model (DEFAULT, QUEUE, MANUAL, TURNBASED) maps cleanly onto either real-time ticks with a command queue or turns with one action per turn. The 4-second tick with speed-based action gain is a sensible real-time default.
- Injury and bleeding as timed conditions add texture cheaply. Keep them.

**Numbers worth keeping:** 4-second tick; percentile roll of 1–100; clamp of 5–95; attack base 50; armour baseline 100; XP pool 100 + 25n − 5n^1.5, share capped at 100; player death XP loss min(50 × level, 2000); level-gap damage factor 1 − d²/120.

## Coverage

- **Read in full:** `Libraries/MUDFight.java` (1–3817), `Libraries/TimsLibrary.java` (1–1626), `Libraries/interfaces/CombatLibrary.java` (1–854), `Libraries/Dice.java` (1–414), `Libraries/interfaces/DiceLibrary.java` (1–391), `Libraries/interfaces/ItemBalanceLibrary.java` (1–167).
- **Read in part, outside the bucket, only as far as the combat code needs:** `core/CMath.java` (1496–1520, 1621–1700, 1758–1880); `coffeemud.ini` (291, 299, 340, 374, 381, 691, 2151, 2334–2498); `resources/lists.ini` (18–40, 74–100, 222–250, 271–285, 386–540); `MOBS/StdMOB.java` (2459–2500, 3106–3190, 3240, 3412–3480, 3590, 3742, 4129–4212); `Items/Armor/StdArmor.java` (625–660); `Commands/CharGen.java` (176–184); `Items/interfaces/Weapon.java` (49–170).
- **Searched only:** the call sites of the `CMLib` accessors, `Tests/Notrandom.java`, the `postDamage` callers in `Abilities/Spells`, `Behaviors/ItemGenerator.java`, `Commands/Reset.java`, and the defaults in `core/CMProps.java`.
- **Not examined:** `Common/DefaultPhyStats.java` beyond the speed and armour defaults; the `CharState` death and annoyance constants; `CharStats` save and crit stat codes; the experience leveler; and the bodies of `Injury`, `Bleeding`, `Prop_*` and `Skill_*` effects.
- **Worked numbers** were computed by a script that re-implements the `CMath` evaluation rules (left-to-right, `?`, `<`, `>`, `@xx`) and applies them to the shipped strings. They were not produced by running CoffeeMud. The `NotRandomHigh` finding comes from a standalone Java replica of that class, not from the engine.
