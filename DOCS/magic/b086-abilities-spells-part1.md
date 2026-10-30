# Spell base class and spells A to Clairevoyance (part 1 of 9)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b086` (Abilities/Spells [part 1/9: Spell .. Spell_Clairevoyance]) · 52 files · 9155 lines of Java.

Citation convention: every path is relative to the CoffeeMud folder, and the Java package prefix `com/planet_ink/coffee_mud/` is dropped. So `Abilities/Spells/Spell_Charm.java:273` means `com/planet_ink/coffee_mud/Abilities/Spells/Spell_Charm.java:273`. Paths outside the package (`coffeemud.ini`, `core/CMProps.java`) are given in full. Line numbers were checked against the source; formulas were read from code, not run.

## Summary

Every spell in this bucket is an `Ability` that extends `Spell`, which is a thin subclass of `StdAbility` (`Abilities/Spells/Spell.java:36`). A spell carries a class code (`ACODE_SPELL`), a domain (abjuration, conjuration and so on), a quality (malicious or beneficial), a mana rule and a set of flags. Casting runs one shared gate pipeline in `StdAbility.invoke`, then a proficiency roll, then either an immediate effect (damage, a message) or a timed effect object attached to the target. Those timed effects are live listeners: they veto or rewrite messages in `okMessage()`, react in `executeMsg()`, change stats in `affectPhyStats()`/`affectCharStats()` and act on each tick. The bucket has 52 files: the `Spell` base class, the `Spell_BaseClanEq` clan base, ten clan-enchantment subclasses, and 40 spell classes. It is a clear example of a spell modelled as data plus a stack of message hooks, and it is where most of the visible balance formulas live.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Spells/Spell.java` | Base of all spells: triggers, quality, class code, the Mage "Magepox" hook on malicious effects, armor and concentration check | 162 |
| `Abilities/StdAbility.java` (ref. 1964-2086, 1441-1486, 1696-1800, 2140-2208, 2312-2349) | Shared pipeline: `invoke`, proficiency roll, mana cost, durations, `maliciousAffect`/`beneficialAffect`, `startTickDown`, `tick` | 3018 (only parts read) |
| `Abilities/Spells/Spell_BaseClanEq.java` | Base of the ten clan-enchantment spells; drains clan experience and adds `Prop_ClanEquipment` | 244 |
| `Abilities/Spells/Spell_CEq*.java` (10 files) | Subclasses that only set `type` (Acid, Cold, Disease, Electric, Fire, Gas, Mind, Paralysis, Poison, Water) | 71-72 each |
| `Abilities/Spells/Spell_BigMouth.java` | Caster gets a "stomach" room that swallows smaller creatures and items and digests them | 689 |
| `Abilities/Spells/Spell_Charm.java` | Mind control: target follows the caster and stops fighting | 292 |
| `Abilities/Spells/Spell_AstralStep.java` | Step through closed exits on the astral plane, with a level-difficulty penalty | 276 |
| `Abilities/Spells/Spell_AlterSubstance.java` | Changes an item's material and its name for 100 ticks | 267 |
| `Abilities/Spells/Spell_ChangeSex.java` | Flips the gender stat through `affectCharStats` | 250 |
| `Abilities/Spells/Spell_ChanneledMissiles.java` | Multi-missile channelling that breaks on any other action | 248 |
| `Abilities/Spells/Spell_AchillesArmor.java` | Blocks every weapon damage type except one randomly chosen weakness | 233 |
| `Abilities/Spells/Spell_ChainLightening.java` | Chain bolt whose damage halves each hit | 176 |
| `Abilities/Spells/Spell_Clairevoyance.java` and `Spell_Claireaudience.java` | Remote look and remote hearing on a named target | 226 and 222 |
| `Abilities/Spells/Spell_CharmWard.java` and `Spell_ChantShield.java` | Wards that veto charm abilities and chant abilities | 211 and 159 |
| `Libraries/MUDFight.java` (ref. 394-424, 499) | `rollToHit` (armor polarity) and `postDeath` | 3817 (only parts read) |
| `Items/interfaces/Weapon.java` (ref. 49-100) | Weapon damage-type constants and `TYPE_DESCS` (12 names) | ref. only |
| `core/collections/PairVector.java` (ref. 102, 162, 292-311) | Pair list used by the scrying spells | ref. only |
| `MOBS/StdMOB.java` (ref. 5264-5272) | `addEffect` refuses a second effect with the same ID | ref. only |
| `coffeemud.ini` (ref. 862, 864-903, 1646-1650) | `EFFECTCAP`, `MANACOST`, `MANAMINCOST`, `CLANENCHCOST` | ref. only |
| `core/CMProps.java` (ref. 681-684) | Tick length 4000 ms, ticks per real minute and hour | ref. only |

## How it works

### 1. The base class and the cast pipeline

- `Spell` triggers on `CAST`, `CA` and `C` (`Spell.java:77`). Its quality is `QUALITY_INDIFFERENT` (`Spell.java:72-75`). `canAffectCode()` returns 0 and `canTargetCode()` returns `CAN_MOBS` (`Spell.java:60-69`). The class code is `ACODE_SPELL` (`Spell.java:86-89`).
- `Spell.invoke` calls `StdAbility.invoke`, then `spellArmorCheck` (`Spell.java:153-161`, `132-151`). For a non-auto cast by a player-owned spell whose armor is not cloth-compatible, the cast fails half the time (`rollPercentage()<50`) with "<S-NAME> watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!". Concentration is also required (`canConcentrate`, `Spell.java:144-148`). The check is skipped when `disregardsArmorCheck` is true (`StdAbility.java:877-885`, which is true for null, monster or level-qualified casters).
- `StdAbility.invoke(mob, commands, target, auto, asLevel)` (`StdAbility.java:1964-2086`) runs these gates for a non-auto cast. Auto casts (monsters, scripted effects) skip all of them (`StdAbility.java:1969`).
  1. Caster must be alive and awake (`:1974`).
  2. Per-spell cooldown: "You must wait @x1 before you can do that again." (`:1978-1989`). `setTimeOfNextCast` shortens the base by 5% per XLEVEL point and 10% per XTIME point (`:276-284`).
  3. Movement-bound check (`:1991-1996`).
  4. Compounding: `MANACOMPOUND` penalties grow the cost of repeat casts inside a window (`:2001-2046`). A negative amount gives "You can't do that again just yet." (`:2021-2025`).
  5. `testUsageCost` (`:2050`), then the minimum wait between casts: "You need a second / a few seconds / awhile to recover" (`:2053-2066`).
  6. Components for the ability, if the caster lacks the admin override (`:2067-2068`, method at `:2088-2111`).
  7. Deduct mana, moves and hit points (`:2074-2076`), `helpProficiency` (`:2077`), then a PREINVOKE message that the caster's `okMessage` may veto (`:2078-2081`).
- Each spell then does its own work: target resolution, `proficiencyCheck`, a `CMMsg` sent through the room, and an effect.

### 2. Mana cost

- The default usage type is `USAGE_MANA` (`StdAbility.java:299-302`).
- `usageCost` (`StdAbility.java:1696-1800`) builds the cost:
  - `lowest` is the lowest qualifying level for this ability over the caster's classes (`:1723-1745`).
  - `coffeemud.ini` `MANACOST=-1` (the shipped default, `coffeemud.ini:877`) means the cost is `50 + lowest` (`StdAbility.java:1781`, documented at `coffeemud.ini:864-871`).
  - Each class level above the qualifying level reduces the cost by `consumed/30` per level (`StdAbility.java:1784`). A caster well above the spell's level pays much less.
  - `MANAMINCOST=-1, 10` (`coffeemud.ini:903`) sets the floor to `lowest`, but at least the `_DEFAULT` exception, which is 10 (`StdAbility.java:1750-1757`). In effect, `max(lowest, 10)`.
  - `overrideMana()` of zero or more replaces the formula (`StdAbility.java:1787-1788`). Overrides in this bucket: `Spell_AchillesArmor.java:72-74` (100), `Spell_AnimateWeapon.java:71-73` (100), `Spell_Advancement.java:79-81` (100), `Spell_AstralStep.java:66-68` (`COST_ALL`), and `Spell_BaseClanEq.java:81-83` (`COST_ALL`).
  - `COST_ALL` is `Integer.MAX_VALUE` (`Abilities/interfaces/Ability.java:651`). It costs the caster's whole pool (`StdAbility.java:1614-1615`).
- Caster skill can lower cost further through the `LOWCOST` and `LOWFREECOST` expertise levels (`StdAbility.java:1626-1640`).

### 3. The success roll

`proficiencyCheck(mob, adjustment, auto)` (`StdAbility.java:1441-1486`):

- Start from the caster's learned proficiency (`proficiency()`, `:627`), add ability-adjustment terms, add `2 × XLEVEL` (`:1467-1468`), and clamp to 5..95 unless the `DIS955RULE` security flag is set (`:1470-1474`).
- Add `XLEVEL/2` (`:1478`).
- A non-negative `adjustment` is added to the chance (`:1480-1481`). A negative one causes an early failure when `roll > 100 + adjustment` (`:1483-1484`), then success if `roll < pctChance` (`:1485`).
- Auto casts always succeed and set proficiency to 100 for non-owners (`:1442-1447`).

Spells pass their own adjustments. Charm passes `-25 - (targetINT×2 + levelDiff×15)` (`Spell_Charm.java:263`). Astral Step passes `(casterLevel - highestLevelHere) × 10`, capped at zero (`Spell_AstralStep.java:218`, `223`).

### 4. Timed effects and durations

Time is counted in ticks. A tick is 4000 ms (`TICKTIME=4000`, `coffeemud.ini:2151`, `core/CMProps.java:681`). That gives 15 ticks per real minute and 900 per real hour (`core/CMProps.java:683-684`).

- `maliciousAffect` (`StdAbility.java:2185-2208`) and `beneficialAffect` (`:2341-2349`) copy the spell, mark it `canBeUninvoked`, and call `startTickDown`.
- Malicious duration, `getMaliciousTickdownTime` (`StdAbility.java:2174-2183`):
  - A positive `tickAdjustment` is used directly as a tick count. Most damage-over-time spells pass a small number here, such as Acid Arrow's 3 ticks (`Spell_AcidArrow.java:143`) and Choke's `5 + XLEVEL` (`Spell_Choke.java:119`).
  - Zero gives the standard duration: `round(adjustedLevel × 1.1) + 25` ticks, adjusted by level difference (`adjustMaliciousTickdownTime`, `StdAbility.java:2140-2172`), with a floor of 2 and a cap of one third of a real hour (300 ticks) for non-deity casters (`:2160-2170`).
  - A negative value is read as a percentage of the standard duration (`:2180-2182`). Charm uses this (see Problems).
- Beneficial duration, `getBeneficialTickdownTime` (`StdAbility.java:2312-2331`): a positive value is used directly. Otherwise the duration is `casterLevel × 4 + 40` ticks (`:2325`). When a player-led caster buffs another mob, the caster level is capped at the target's level plus `EXPRATE` (`:2316-2324`; `EXPRATE=6`, `coffeemud.ini:406`). The floor is 5 ticks (`:2279-2280`).
- `startTickDown` (`StdAbility.java:698-752`):
  - Adds an XTIME bonus of 20% per level (`:709`, `addedTickTime` at `:640-643`) except for crafting-type skills.
  - Refuses a second copy of the same ID on the same target (`alreadyAffected`, `:692-695`, `:721`). `StdMOB.addEffect` also refuses duplicates (`MOBS/StdMOB.java:5264-5270`), so buffs such as Advancement do not stack.
  - Enforces `EFFECTCAP`: if a mob exceeds the cap, a random eligible effect is removed (`:725-741`). The shipped default is 0, which means no cap (`coffeemud.ini:862`).
- `tick` (`StdAbility.java:2843-2858`) counts down `tickDown` on each mob tick and calls `unInvoke()` at zero.

### 5. Effects as message listeners

Each effect is a listener with these hooks: `okMessage` (can veto), `executeMsg` (reacts after the fact), `tick`, `affectPhyStats`/`affectCharStats` (stat changes), and `unInvoke` (cleanup and the "fades" message). The veto is what makes wards and mind control work. Examples:

- Charm vetoes hostile acts toward its charmer ("You like <name> too much."), leaving the room while the charmer is present ("You don't want to leave your friend."), and the nofollow command. It breaks off on quit, and `tick` re-issues the follow command (`Spell_Charm.java:104-160`, `164-180`).
- Blur's `okMessage` dodges a malicious cast that targets the blurred mob with probability `INT×2` percent and says "<T-NAME> can't seem to focus on <S-NAME>." (`Spell_Blur.java:79-104`, roll at `:96`).
- Brainwash's `okMessage` rewrites the target's outgoing speech to carry the injected message (`Spell_Brainwash.java:86-138`).
- Anchor's `okMessage` vetoes any ability with `FLAG_MOVING` or `FLAG_TRANSPORTING` aimed at the anchored thing (`Spell_Anchor.java:101-149`, veto at `:147`).
- Message results are integers stored on the message. A damage spell halves its damage when `msg.value()>0`, meaning some listener blocked or softened the hit (`Spell_BurningHands.java:106-107`, `Spell_ChainLightening.java:151-152`).

Wards test message metadata rather than the spell's identity. Charm ward checks `FLAG_CHARMING` on the tool (`Spell_CharmWard.java:107`). Chant shield checks the classification code for chants (`Spell_ChantShield.java:103`). This is a small, general tagging system.

### 6. Damage formulas

`CMLib.dice().roll(number, die, modifier)` (`Libraries/Dice.java:398`). `X` is `getXLEVELLevel` (level expertise), `X1` is `getX1Level` (`StdAbility.java:419-447`), and `adjustedLevel` is the caster's adjusted level (`StdAbility.java:899-925`).

| Spell | Formula | Lines |
|---|---|---|
| Burning Hands | 1d(adjLevel + X1) + 15, halved if blocked | `Spell_BurningHands.java:104-107` |
| Acid Arrow | initial 1d(N+10)+5 with N = (adjLevel + 2·X1)/2, halved if blocked; then 3-tick acid DoT of 2d(4+X+2·X1) per tick | `Spell_AcidArrow.java:138-143`, `:106` |
| Acid Spray | initial 2d(N)+1, N as above; 3-tick DoT of 1d(10+X+2·X1) per tick | `Spell_AcidSpray.java:139-144`, `:107` |
| Acid Fog | each tick 1d(level+X+2·X1) on everyone inside; lasts 10 × (level+X+2·X1) ticks; ends when the victim leaves the casting room | `Spell_AcidFog.java:115-117`, `:174`, `:110-111` |
| Chain Lightening | start at 1d(M)8 + M/4 with M = round((adjLevel+2·X1)/2); halved after every hit; chain stops when damage is under 5 | `Spell_ChainLightening.java:103-104`, `:150-160` |
| Channeled Missiles | floor(adjLevel/10)+1 missiles per round, each 1d11+11; channelling lasts 4 ticks | `Spell_ChanneledMissiles.java:175`, `:206`, `:183` |
| Blademouth | each time the target speaks, it takes maxHP/20 slashing damage | `Spell_Blademouth.java:93` |
| Animate Weapon | each tick an attack roll against the caster's victim, damage 1d(item damage)+5 | `Spell_AnimateWeapon.java:90-96` |
| Big Mouth | digestion every 2 ticks (after an initial 4): round(curHP × (0.15 + 0.01·X)), at least maxHP/15 and at least 2, halved if blocked | `Spell_BigMouth.java:318`, `:364-388` |

Armor polarity matters for every hit roll. Lower armor is better. `rollToHit` negates the defence value before dividing (`Libraries/MUDFight.java:409-424`). Spell Shield subtracts from armor and Blur subtracts `1 + X` (`Spell_Blur.java:111`).

### 7. Wards, shields and defensive effects

- **Achilles Armor** (`Spell_AchillesArmor.java:113-188`): a random vulnerability index is rolled from `roll(1, TYPE_DESCS.length, -1)` (`:92`, `:222`). The 12 names are NATURAL, SLASHING, PIERCING, BASHING, BURNING, BURSTING, SHOOTING, FROSTING, GASSING, MELTING, STRIKING and LASERING (`Items/interfaces/Weapon.java:87-100`). Every other damage type is blocked with "The armor around <S-NAME> blocks @x1 attack from <T-NAME>!" and `return false` (`:164-175`). A hit of the vulnerable type calls `postDeath` (`:177`). Source messages are mapped to damage types at `:127-159`.
- **Chant Shield** (`Spell_ChantShield.java:93-116`): a hostile chant is blocked when `rollPercentage < 35 + min(invokerAdjLevel, targetLevel + EXPRATE) - chantAdjLevel` (`:107-110`).
- **Charm Ward** (`Spell_CharmWard.java:96-134`): blocks `FLAG_CHARMING` abilities from SPELL, CHANT, PRAYER and SONG classes aimed at the warded mob (`:107-117`). A room ward blocks any charm ability entering the room (`:124-131`). A ward cast on owned land is stored in the room database (`:196-200`, `DBUpdateRoom`).
- **Anchor** (`Spell_Anchor.java:101-149`): see Section 5. The magical fizzle text appears only for spell, chant, prayer and song classes (`:137-145`), but the veto at `:147` applies to any moving or transporting ability.
- **Anti Plant Shell** (`Spell_AntiPlantShell.java:84-170`): against mobs that `CMLib.flags().isAPlant` identifies. Points = 3 + adjLevel/5 (`:90`). Each plant hostile act or advance costs a point and resets range to the room maximum (`:95-170`). The shell ends when points run out (`:135`, `:149`, `:161`).
- **Blur** (`Spell_Blur.java:79-111`): dodge chance of `INT × 2` percent against targeted malicious spells, plus an armor bonus of `1 + X`.
- **Arms Length** (`Spell_ArmsLength.java:109-156`): an enemy's ADVANCE is answered with "predict(s) <T-YOUPOSS> advance and retreat". Duration is `3 + round(X/3)` ticks, plus one if the caster is not in combat.

### 8. Mind and social effects

- **Charm** (`Spell_Charm.java:235-290`): resisted by a level-difference term in the proficiency roll (`:241`, `:263`). Success turns the target to peace, makes it follow the caster (`:277-279`) and sets a duration (`:273`). Unwinding gives "free-will returns" and random wandering for monsters (`:197-211`).
- **Alternate Reality** (`Spell_AlternateReality.java:127-197`): the target must already be fighting the caster (`:133-136`). On success it changes sides, makes peace, and is re-aimed at a random enemy from the caster's group opponents (`:159-187`). A `tick` ends the effect when the target is no longer in combat (`:98-101`). `okMessage` vetoes attacks on the caster's group: "But you are on <name>'s side!" (`:114-120`).
- **Brainwash** (`Spell_Brainwash.java:158-216`): the target's speech is extended with the injected message. Lead-in phrases are chosen by `roll(1,6,0)` (`:102-122`). The message is kept in misc text (`:205`).
- **Awe** (`Spell_Awe.java:79-100`): hostile acts from attackers not already fighting the target are vetoed when `rollPercentage > (attackerLevel − (targetLevel + 2X)) × 10` (`:89`). A weaker or equal attacker is always blocked. A much stronger one often gets through.
- **Awe Other** (`Spell_AweOther.java:82-103`): the target is "in awe of" a named mob (stored in misc text). Hostile acts toward the named mob are vetoed when that named mob is not in combat and the attacker is not already its fighter (`:84-100`).
- **Change Sex** (`Spell_ChangeSex.java:79-85`): `affectCharStats` sets the gender stat to F if it is not F, else M. The recompute is stable because each recompute starts from base values. Caged animals and child characters get their gender words rewritten in names and descriptions (`:88-141`).
- **Cause Stink** (`Spell_CauseStink.java:88-100`): halves charisma (`:91-93`). Each tick, with chance `pct` (default 20, `:85`, `:100`, `:110`), it cycles through ten smell messages in the room (`:120-155`). Neighbouring rooms with player characters get the smell from the opposite direction (`:167-175`).
- **Choke** (`Spell_Choke.java:79-129`): sets `CAN_NOT_BREATHE` on the target (`:82`). The message on unwinding is "You begin to breathe easier."

### 9. Items and enchantments

- **Alter Substance** (`Spell_AlterSubstance.java:147-265`): changes the target's material (for drinks, the liquid type too). It lasts 100 ticks (`:257`). The name is rewritten by swapping the old material name for the new one (`:245-256`). Unwinding restores the original (`:98-130`). Emptying a drink removes the effect (`:84-91`).
- **Animate Weapon** (`Spell_AnimateWeapon.java:77-209`): the weapon flies (`:170`) and attacks each tick while its owner (a room) and caster share the room (`:79-98`). A 25 percent idle chance produces a flavour line (`:102-120`). Touching it with GET, PUSH, PULL or REMOVE ends the effect (`:139-149`).
- **Boomerang** (`Spell_Boomerang.java:79-211`): the first GET binds the item to the taker (`:132-138`). If someone else later holds it, a bounce tick returns it to the owner's inventory (`:116-121`). Owner lookup uses `CMLib.players().getPlayer(text())` (`:102`).
- **Arcane Possession** (`Spell_ArcanePossession.java:79-149`): only the named owner can wear, hold or wield the item. Others are blocked and the item is dropped (`:91-100`).
- **Alarm** (`Spell_Alarm.java:79-127`): any non-caster who targets the item sets off "A HORRENDOUS ALARM GOES OFF, WHICH SEEMS TO BE COMING FROM ..." and ends the effect (`:93-98`).
- **Arcane Mark** (`Spell_ArcaneMark.java:77-119`): appends the caster's message to the item's description as "Some markings on it say ...".
- **Animate Item** (`Spell_AnimateItem.java:71-103`): makes an item emote the rest of the command text ("<T-NAME> @x1.").
- **Add Limb** (`Spell_AddLimb.java:115-155`): `affectCharStats` adds one arm and one hand. On unwinding `confirmWearability` drops anything no longer wearable (`:104-105`).
- **Advancement** (`Spell_Advancement.java:91-146`): +1 physical level (`:94`) and +1 class level for the current class (`:102`).
- **Big Mouth** (`Spell_BigMouth.java:219-315`, `:615-639`): the caster gets a generated `StdRoom` as a "stomach". It has no-recall and no-teleport-out effects (`:226-233`) and a watcher that lets trapped creatures attack from inside (`:236-313`). The mob delegates inhabitant and item methods to that room (`:415-611`). Small creatures are swallowed if their weight is under one third of the caster's base weight (`:94`). Ownership of the mouth is checked on invoke (`:651-653`).

### 10. Divination and information

- **Augury** (`Spell_Augury.java:77-149`): looks at one exit. "You feel going that way would be bad." is said if an aggressive behaviour or a malicious effect is in the room, the exit, or the reverse exit (`:118-142`). Otherwise "would be ok." This is a risk hint, not a full map.
- **Breadcrumbs** (`Spell_Breadcrumbs.java:76-210`): records the rooms the caster enters (`:139-188`). Re-entering a room truncates the trail back to it (`:152-165`). Any room adjacent to the new room also truncates the trail back to that room (`:166-186`). The text "(Breadcrumb Trail: north east ...)" is built from the trail in reverse (`:105-135`), with "Unknown" when two rooms are not adjacent.
- **Clairevoyance** (`Spell_Clairevoyance.java:84-224`) and **Claireaudience** (`Spell_Claireaudience.java:84-212`): scrying on a named mob. The target is looked up in the same room first (`Spell_Clairevoyance.java:157`), then in the caster's area, then in every room. One match is chosen at random (`:160-166`). When the scried mob does LOOK or EXAMINE, the caster gets the same look (`:101-117`). Claireaudience relays speech only when the two are in different rooms (`Spell_Claireaudience.java:101-111`). Both keep a static list of active scries (`:82`). The list has a bug (see Problems).
- **Analyze Dweomer** (`Spell_AnalyzeDweomer.java:76-130`): identifies an item. It describes armor wear slots (`:95-112`), containers, currency, drinks, food, light, potions and similar items.

### 11. Clan enchantment (`Spell_BaseClanEq.java`)

- Each of the ten `Spell_CEq*` classes sets `type` (Acid, Cold, Disease, Electric, Fire, Gas, Mind, Paralysis, Poison, Water) and calls the base (for example `Spell_CEqAcid.java:45-72`). The behaviour is entirely in the base class.
- Only one clan enchantment may be learned per character (`Spell_BaseClanEq.java:95-112`).
- The caster must be in a clan with ENCHANT authority (`:120-154`, `:143`). Clan choice falls back to `findPrivilegedClan` (`:154`).
- Cost: `exp = points × CLANENCHCOST` (`:190`, `coffeemud.ini:1650`, default 1000). The clan must hold that much experience (`:191-193`) and the point count may not exceed clan level (`:197-199`).
- The clan is charged (`C.adjExp`, `:217`) before the success roll (`:214`). A fizzle therefore still costs the clan (`:219-237`).
- On success, an item gets a `Prop_ClanEquipment` effect whose misc text is type, points, clan name and government type (`:226-237`).
- Flags: `FLAG_CLANMAGIC` (`:77`), class `ACODE_SPELL|DOMAIN_ENCHANTMENT` (`:71`), `COST_ALL` (`:83`), and `disregardsArmorCheck` true (`:89-91`).

### 12. Messages and text

- Message strings use tokens such as `<S-NAME>`, `<T-NAME>`, `<T-NAMESELF>`, `<S-HIS-HER>`, `<S-IS-ARE>` and `<DAMAGE>`. Verbs carry a plural marker, as in "smile(s)" and "fizzle(s)" (for example `Spell_Charm.java:266`).
- Cast messages are chosen with `verbalCastCode` and `somaticCastCode`. Malicious spells use the ATTACK variants (`StdAbility.java:2629-2660`). Auto casts add `MASK_ALWAYS`.
- Failure messages go through `beneficialWordsFizzle`, `beneficialVisualFizzle`, `beneficialSoundFizzle` and `maliciousFizzle` (`StdAbility.java:2210-2271`). Each one shows a fizzle to the room and returns false.

## Content worth mining

Concrete content for a text game. Messages are short paraphrases or quoted fragments with file and line references.

| Idea | Source | Detail |
|---|---|---|
| Random weakness per cast | `Spell_AchillesArmor.java:92`, `Weapon.java:87-100` | Twelve damage types, one picked as weakness. Everything else is blocked. Good for a "your armor has a crack" mechanic. |
| Halving chain bolt | `Spell_ChainLightening.java:103-160` | Each hit halves the damage and the chain ends below 5. |
| Missile count by level | `Spell_ChanneledMissiles.java:175` | floor(level/10)+1 missiles, each 1d11+11. |
| Route hints | `Spell_Augury.java:118-142` | "You feel going that way would be bad." Risk hints based on monsters and effects. |
| Breadcrumb trail | `Spell_Breadcrumbs.java:105-135` | Direction string built from the walked path, e.g. "(Breadcrumb Trail: north east ...)". |
| Remote look and hear | `Spell_Clairevoyance.java:101-117`, `Spell_Claireaudience.java:101-111` | The caster sees what the watched mob sees, or hears its speech. |
| Stink pulses | `Spell_CauseStink.java:120-175` | Ten smell messages in a cycle, relayed to neighbouring rooms. |
| Blink range shifts | `Spell_Blink.java:103-160` | 1d8 roll: range -2, -1, 0, +1, +2 in combat. Messages such as "vanish(es) and reappear(s) a bit closer to". |
| Brainwash lead-ins | `Spell_Brainwash.java:102-122` | Six injected phrases such as "Did you know that", "You might also be curious to know that". |
| Alarm on an item | `Spell_Alarm.java:95` | Any touch by a non-caster sets off an alarm that names the item. |
| Stomach room | `Spell_BigMouth.java:219-315` | A generated room as a belly, with an attack-from-inside verb. |
| Clan enchant types | `Spell_CEq*.java` | Acid, Cold, Disease, Electric, Fire, Gas, Mind, Paralysis, Poison, Water. Each yields `Prop_ClanEquipment` with that type. |
| Item binding by name | `Spell_ArcanePossession.java:91-100` | An item only its owner may hold or wear. |
| Item inscription | `Spell_ArcaneMark.java:108-110` | Caster writes a note into an item's description. |

Catalogue of the 42 non-CEq files (the base class, the clan base class, and 40 spell classes). Domain and quality come from each class. Lines are file sizes.

| File | Domain | Quality | Effect in brief | Lines |
|---|---|---|---|---|
| `Spell.java` | (base) | INDIFFERENT | Base class; Magepox hook; armor check | 162 |
| Spell_AchillesArmor | ABJURATION | BENEFICIAL_OTHERS | One random weak damage type; mana 100 | 233 |
| Spell_AcidArrow | CONJURATION | MALICIOUS | Acid bolt plus 3-tick DoT | 151 |
| Spell_AcidFog | EVOCATION | MALICIOUS | Area acid fog that ends when the victim leaves | 187 |
| Spell_AcidSpray | CONJURATION | MALICIOUS | Acid spray plus 3-tick DoT | 152 |
| Spell_AddLimb | TRANSMUTATION | BENEFICIAL_OTHERS | +1 arm, +1 hand | 165 |
| Spell_Advancement | ENCHANTMENT | OK_OTHERS | +1 level, +1 class level; mana 100 | 148 |
| Spell_Alarm | ENCHANTMENT | INDIFFERENT | Item alarm on non-caster touch | 129 |
| Spell_AlterSubstance | ALTERATION | INDIFFERENT | Changes material and name for 100 ticks | 267 |
| Spell_AlternateReality | ILLUSION | MALICIOUS | Turns a fighting target to the caster's side | 199 |
| Spell_AnalyzeDweomer | DIVINATION | INDIFFERENT | Identifies an item (description builder) | 188 |
| Spell_Anchor | ABJURATION | BENEFICIAL_OTHERS | Vetoes moving and transporting abilities | 179 |
| Spell_AnimateItem | ALTERATION | INDIFFERENT | Item emotes the command text | 105 |
| Spell_AnimateWeapon | ENCHANTMENT | INDIFFERENT | Weapon flies and attacks each tick; mana 100 | 211 |
| Spell_AntiPlantShell | ABJURATION | BENEFICIAL_OTHERS | Points-based shield vs plant mobs | 237 |
| Spell_ArcaneMark | ALTERATION | INDIFFERENT | Writes a note into an item description | 121 |
| Spell_ArcanePossession | ABJURATION | INDIFFERENT | Only the named owner can hold or wear it | 151 |
| Spell_ArmsLength | DIVINATION | BENEFICIAL_SELF | Foes' advances are answered with retreats | 164 |
| Spell_AstralStep | CONJURATION | INDIFFERENT | Passes closed exits, 1+X directions, level penalty; mana ALL | 276 |
| Spell_Augury | DIVINATION | INDIFFERENT | Says whether a direction is "bad" or "ok" | 151 |
| Spell_Awe | ENCHANTMENT | BENEFICIAL_SELF | Weaker hostile attackers are vetoed | 163 |
| Spell_AweOther | ENCHANTMENT | MALICIOUS | Target is "in awe of" a named mob | 179 |
| Spell_BaseClanEq | ENCHANTMENT | INDIFFERENT | Clan enchantment base; mana ALL | 244 |
| Spell_BigMouth | TRANSMUTATION | OK_SELF | Stomach room; digests creatures | 689 |
| Spell_Blademouth | EVOCATION | MALICIOUS | Target takes damage each time it speaks | 149 |
| Spell_Blindness | TRANSMUTATION | MALICIOUS | CAN_NOT_SEE; only in combat | 154 |
| Spell_Blink | CONJURATION | BENEFICIAL_OTHERS | Random range shifts in combat | 206 |
| Spell_Blur | ILLUSION | BENEFICIAL_OTHERS | INT×2% dodge vs spells; armor bonus | 157 |
| Spell_Boomerang | CONJURATION | INDIFFERENT | Item returns to owner | 213 |
| Spell_Brainwash | ENCHANTMENT | MALICIOUS | Injects a message into target's speech | 219 |
| Spell_Breadcrumbs | DIVINATION | OK_SELF | Records walked path; trail text | 228 |
| Spell_BurningHands | ALTERATION | MALICIOUS | Fire touch, 1d(adjLevel+X1)+15 | 118 |
| Spell_CauseStink | TRANSMUTATION | MALICIOUS | CHA halved; smell pulses | 228 |
| Spell_ChainLightening | EVOCATION | MALICIOUS | Halving chain bolt | 176 |
| Spell_ChangeSex | TRANSMUTATION | MALICIOUS | Flips gender stat | 250 |
| Spell_ChanneledMissiles | CONJURATION | MALICIOUS | Missiles per round; breaks on other action | 248 |
| Spell_ChantShield | ABJURATION | BENEFICIAL_OTHERS | Blocks hostile chants by chance | 159 |
| Spell_Charm | ENCHANTMENT | MALICIOUS | Target follows caster | 292 |
| Spell_CharmWard | ABJURATION | OK_OTHERS | Vetoes charm abilities on mob or room | 211 |
| Spell_Choke | ALTERATION | MALICIOUS | CAN_NOT_BREATHE for 5+X ticks | 131 |
| Spell_Claireaudience | DIVINATION | OK_OTHERS | Remote hearing of a named mob | 222 |
| Spell_Clairevoyance | DIVINATION | OK_OTHERS | Remote looking at a named mob | 226 |

The ten clan-enchantment files (`Spell_CEqAcid.java`, `Spell_CEqCold.java`, `Spell_CEqDisease.java`, `Spell_CEqElectric.java`, `Spell_CEqFire.java`, `Spell_CEqGas.java`, `Spell_CEqMind.java`, `Spell_CEqParalysis.java`, `Spell_CEqPoison.java`, `Spell_CEqWater.java`) are 71 to 72 lines each, and are listed in Section 11.

## Notable design ideas

- **Two-phase messages as the rule engine.** Every action is first offered to every listener for a veto, then delivered. Wards, shields and mind control all use this. A ward is just an effect whose `okMessage` tests tags such as `FLAG_CHARMING` or `FLAG_MOVING`, so one mechanism covers many spells (`Spell_CharmWard.java:96-134`, `Spell_Anchor.java:101-149`).
- **Flags as categories.** Abilities carry `FLAG_*` tags (`Abilities/interfaces/Ability.java:956-1010`). Wards match categories, not specific spells.
- **Difficulty by level gap.** Charm and Awe scale resistance by the difference between the caster's and target's levels (`Spell_Charm.java:241`, `Spell_Awe.java:89`). Astral Step uses the highest-level creature or item in the rooms it passes (`Spell_AstralStep.java:115-142`, `218`).
- **Concentration.** Channeled Missiles ends on any action other than casting, by listening to the caster's own messages (`Spell_ChanneledMissiles.java:88-118`).
- **Escalating cost of repeats.** `MANACOMPOUND` multiplies repeat casts inside a window (`StdAbility.java:2001-2046`), which discourages rotations of one spell.
- **Clan economy.** Enchantments are paid for in clan experience, so the group that benefits also pays (`Spell_BaseClanEq.java:190-217`).
- **Random weakness per cast.** Achilles Armor rolls its weakness on cast and carries it in the effect's misc text (`Spell_AchillesArmor.java:83-93`, `:222-225`). That is easy to read back, but it ties the rule to a string key.

## Problems and cruft

Found by reading. Nothing was executed.

1. **Chain Lightening lists each target twice.** `targets` is seeded with the target set (`Spell_ChainLightening.java:87`), then the same set is added again (`:88-89`):

   ```java
   final List<MOB> targets=new ArrayList<MOB>(h);
   for (final MOB element : h)
       targets.add(element);
   ```

   Every proper target appears twice, so the damage halves faster than intended and the chain wastes steps on repeats.

2. **Clairevoyance's scry list leaks.** `scries.add(target, mob)` stores (target, caster) (`Spell_Clairevoyance.java:216`, `PairVector.java:102`). `unInvoke` removes with `scries.removeSecond(mob)` (`Spell_Clairevoyance.java:93`), where `mob` is the scried target. That removes a pair whose *caster* is the target, which never matches. The pair stays, and `containsFirst(target)` keeps blocking every later scry on that target (`:196`). Revoking does not help either, because the revoke path calls the same `unInvoke`. The `scries` list is static (`:82`), so the block lasts until restart. Claireaudience uses the correct `removeElementFirst` (`Spell_Claireaudience.java:93`), so this looks like a copy error.

3. **Charm's duration is scaled twice.** Charm passes a negative tick adjustment equal to the level gap (`Spell_Charm.java:241-243`, `:273`). `getMaliciousTickdownTime` reads a negative value as a percentage of standard duration (`StdAbility.java:2180-2182`). But the standard duration has already been reduced by a level-gap fraction in `adjustMaliciousTickdownTime` (`:2148-2159`), which is called first (`:2178`). With the shipped `EXPRATE=6` (`coffeemud.ini:406`), a target one level above the caster is charmed for about 0.86 percent of the standard duration: the level-gap fudge leaves 0.86 of the standard, and the Charm term keeps 1 percent of that. A target at equal level gets the full standard. The intended scale is unclear, but as written the result is a cliff, not a gradual fall. Intent unverified; the code path is clear.

4. **Achilles Armor's weakness is death.** A hit of the rolled weakness type calls `postDeath` (`Spell_AchillesArmor.java:177`). `postDeath` posts a death, or a knock-out when `isKnockedOutUponDeath` applies (`Libraries/MUDFight.java:499`). The damage still goes through. A single hit can therefore kill a protected target outright. It may be intended, but the spell is sold as armor. The mapping of speech or water to FROSTING (`:136-137`) also looks questionable (unverified intent).

5. **Dead field in Big Mouth.** `dmgBonus` is written (`Spell_BigMouth.java:392`, `:394`) and declared (`:202`), and never read.

6. **Unreachable branch in Astral Step.** Line 225 already fails the cast when the caster has the effect (`Spell_AstralStep.java:225-226`). The later `if(mob.fetchEffect(ID())==null)` (`:251`) is therefore always true, and its `else` (`:266-268`) can only run if state changed between the two checks. It is effectively dead code.

7. **No-op calls in Change Sex.** `M.text();` and `target.text();` (`Spell_ChangeSex.java:232`, `:234`) discard their results.

8. **Boomerang's owner lookup is player-only.** `getOwner` falls back to `CMLib.players().getPlayer(text())` (`Spell_Boomerang.java:102`). If a monster took the item, the name will not resolve and the bounce will never fire. Reading-based, not tested.

9. **Clan enchant overflow guard is fragile.** `points*CLANENCHCOST` multiplies two ints before widening to long (`Spell_BaseClanEq.java:190`). The `exp<0` check at `:191` catches the overflow. Works, but only by accident.

10. **Ini key mismatch.** The comment says `CLANENCCOST` (`coffeemud.ini:1647`), but the key read is `CLANENCHCOST` (`coffeemud.ini:1650`, `core/CMProps.java:3089`). Anyone following the comment will set a key that is ignored.

11. **Clan experience is spent on failure.** The experience charge (`Spell_BaseClanEq.java:217`) comes before the success roll (`:214` sets the roll, `:219` checks it). Commenting "lose all the mana!" suggests this is intended, but it is harsh.

12. **Shared static state.** Both scrying spells keep global static lists (`Spell_Clairevoyance.java:82`, `Spell_Claireaudience.java:82`). In a multi-player server this links players to each other. It also explains the leak in item 2.

13. **Style.** `Spell_AcidArrow.java:105` declares a local named `invoker` that shadows the inherited field. Harmless as written.

14. **Awe Other's veto is not restricted to the affected mob.** `okMessage` checks the message target's name (`Spell_AweOther.java:84-87`) but not that the source is the affected mob. As written, the veto may apply to any attacker that the effect hears of. Unverified against the message dispatch.

## Takeaways for a Python SUD

- **Model a spell as data plus effect classes.** Use a `SpellDef` with: `id`, `school`, `quality` (malicious or beneficial), `target_kinds`, `flags` (charming, moving, transporting, divining, fire and so on), `base_level`, `mana_rule`, and `mana_override`. Make each effect a small object with `on_veto(event)`, `on_event(event)`, `tick()` and `modify_stats(stats)`. This mirrors the CoffeeMud pattern without its Java overhead.
- **Keep one mana formula, but make it a function.** `mana = 50 + lowest_level`, minus `mana // 30` per class level above the qualifying level, with a floor of `max(lowest, 10)`. Allow overrides for a few spells (flat 100, or "all mana"). This is a neat, tunable curve for a single-player game.
- **Keep the proficiency roll, drop the global hooks.** The roll `pct = skill + 2·X + X/2 + adjustment`, clamped to 5..95, with a hard-fail when `roll > 100 + adjustment`, is compact and easy to test. The `DIS955RULE` and `SUPERSKILL` bypasses are not needed.
- **Use one duration rule, applied once.** Keep the two shapes (direct count for a fixed effect, base-by-level for a standard one) and apply any level-gap penalty in exactly one place. The Charm bug is a warning about stacking adjustments.
- **Turn the veto pipeline into an event bus.** Each action creates an event with a source, target, tool, a set of tags and a numeric result. Effects get a veto pass, then an after pass. Wards become a list of predicates over tags (for example, "block any `CHARMING` ability from a `SPELL`"). This will drive Charm Ward, Anchor, Chant Shield and Blur without special cases.
- **Single player: drop or reinterpret the group logic.** `getGroupMembers`, clan authority, Alternate Reality's group-side rule, and the "player caster" duration cap all assume other humans. In a single-player game, the caster's companions are NPCs. Keep the idea (allies do not hit allies) but tie it to a companion list, not to group membership.
- **Replace the static scry list.** Store scry links on the caster object. This avoids the leak in Problems item 2 and keeps the mechanic compatible with save and load.
- **Keep the text-friendly features.** The Augury risk hint, the Breadcrumb trail, the remote look, the stink pulses and the Blink range shifts are all good text-game mechanics. Each is a few lines of Python.
- **Consider the Big Mouth stomach as a pocket location.** A temporary sub-room with its own description and its own digestion ticks is a distinctive idea. It could become a single-player "pocket dimension" with simpler rules.
- **Drop the clan enchant system, or make it a crafting feature.** Clan experience has no single-player analogue. The idea of spending a resource pool to make an item carry an enchantment type is worth keeping as an item-crafting rule.
- **Use a template for messages.** CoffeeMud's `<S-NAME>`, `<T-NAME>` and `(s)`-style plurals can become a small template function, for example `"{actor} smile{s}"` with the actor's grammar supplied. Avoid building sentences by string concatenation.
- **Write the Achilles-style weakness as state on the actor.** A random weakness chosen at cast and stored on the effect (not in a text field) is clearer in Python. Keep the rule that the weakness blocks everything else. Decide whether a weakness hit should kill (CoffeeMud's choice) or just deal extra damage.

## Coverage

- **Read in full (logic and messages):** `Spell.java`; `Spell_BaseClanEq.java`; `Spell_CEqAcid.java`; `Spell_CEqCold.java` (by `diff` against Acid); `Spell_CEqDisease.java` (read); the `type` line of each other CEq file (by grep); `Spell_BigMouth.java`; `Spell_Charm.java`; `Spell_ChangeSex.java`; `Spell_ChanneledMissiles.java`; `Spell_AstralStep.java`; `Spell_AlterSubstance.java`; `Spell_Brainwash.java`; `Spell_Choke.java`; `Spell_Blindness.java`; `Spell_BurningHands.java`; `Spell_AcidArrow.java`; `Spell_AchillesArmor.java`; `Spell_Blur.java`; `Spell_Blink.java`; `Spell_ChainLightening.java`; `Spell_Clairevoyance.java`; `Spell_AcidFog.java`; `Spell_AlternateReality.java`; `Spell_CharmWard.java`; `Spell_ChantShield.java`; `Spell_ArmsLength.java`; `Spell_Awe.java`; `Spell_AnimateWeapon.java`; `Spell_Advancement.java`; `Spell_AddLimb.java`; `Spell_Boomerang.java`; `Spell_Breadcrumbs.java`; `Spell_Alarm.java`; `Spell_Augury.java`; `Spell_ArcanePossession.java`; `Spell_AnimateItem.java`; `Spell_ArcaneMark.java`.
- **Read the key sections (invoke, okMessage, tick, stat hooks, unInvoke):** `Spell_Anchor.java`; `Spell_AntiPlantShell.java`; `Spell_AweOther.java`; `Spell_AcidSpray.java`; `Spell_Blademouth.java`; `Spell_CauseStink.java`; `Spell_Claireaudience.java` (by diff against Clairevoyance and its executeMsg); `Spell_Clairevoyance` unInvoke and invoke.
- **Skimmed (grep for ID, quality, targets, flags, numbers and messages):** `Spell_AnalyzeDweomer.java` (description builder only); the other nine `Spell_CEq*.java` files (`type` strings and shared base).
- **Skipped:** none of the 52 files was left out. The `Spell_CEq*` family is covered through the base class.
- **Outside the bucket, read by line range:** `Abilities/StdAbility.java` (355-398, 400-447, 627, 698-752, 877-925, 1441-1486, 1696-1800, 1964-2111, 2140-2208, 2210-2271, 2312-2349, 2629-2660, 2843-2858, 3018 total, parts only); `Libraries/MUDFight.java` (394-424, 499-540); `Libraries/Dice.java` (398); `Items/interfaces/Weapon.java` (49-100); `Abilities/interfaces/Ability.java` (622-655, 956-1093); `core/collections/PairVector.java` (102, 162-170, 292-318); `MOBS/StdMOB.java` (5264-5272); `core/CMProps.java` (681-684, 3089, 3097); `coffeemud.ini` (406, 862, 864-903, 1644-1650, 2151).
- **Not verified by running:** all formulas and the two bugs above. Each was confirmed by reading the code path in full.
