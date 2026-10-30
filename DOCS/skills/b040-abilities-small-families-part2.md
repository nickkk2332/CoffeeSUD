# Abilities: poisons, Paladin and Ranger skills (part 2)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b040` (Abilities/(small families), part 2/3: Paladin_MountedCharge .. Ranger_Enemy2) · 42 files · 7856 lines of Java.

Paths are relative to `com/planet_ink/coffee_mud/`, as in the brief. Line numbers were checked against the source. Where a behaviour was only partly traced, the text says so.

## Summary

This bucket has three families. The poison family (28 files) makes a poison a persistent `Ability` effect that sits on a victim, or on food, drink or a weapon, and applies damage or stat penalties on a fixed pulse for a duration set by the poisoner's level. A generic data-driven class and a multi-poison stacker sit alongside 25 fixed poison classes, and the alcohol sub-family models drunkenness as an integer that changes speech, movement, combat and emotes. The Paladin family (10 files) is mostly alignment-gated passive skills, plus a mounted charge, a one-shot unholy strike and a summoned mount that is an NPC rideable. The Ranger family (4 files) gives a racial "favored enemy" bonus, outdoor camouflage and a companion-animal frenzy. All three use the same hooks (a veto in `okMessage`, a reaction in `executeMsg`, tick pulses, and stat modifiers), which makes the family a good model for a Python effect system, though it has several concrete bugs listed below.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Poisons/Poison.java` | Base poison: application, pulse, stat hooks, removal | 492 |
| `Abilities/Poisons/GenPoison.java` | Data-driven poison (20 editable codes, formula fields) | 755 |
| `Abilities/Poisons/UniPoison.java` | Several poisons on one target, sibling stacking | 275 |
| `Abilities/Poisons/Poison_Alcohol.java` | Drunkenness model, speech and emotes, dose stacking | 483 |
| `Abilities/Poisons/Poison_Liquor.java`, `Inebriation.java`, `Poison_Firebreather.java` | Alcohol variants (sleep on expiry, fire belches) | 97, 64, 120 |
| `Abilities/Poisons/Poison_Hallucinogen.java` | Perception rewriting: names, look redirect | 287 |
| `Abilities/Poisons/Poison_*.java` (20 others) | Fixed poisons, mostly template overrides | 107 to 209 each |
| `Abilities/Paladin/PaladinSkill.java` | Base for alignment-gated paladin skills (other bucket, read as base) | ~240 |
| `Abilities/Paladin/Paladin_SummonMount.java` | Summoned NPC steed that follows the paladin | 330 |
| `Abilities/Paladin/Paladin_UnholyStrike.java` | One-shot doubled hit against good or lawful targets | 235 |
| `Abilities/Paladin/Paladin_MountedCharge.java` | Mounted charge that closes range and buffs the rider | 222 |
| `Abilities/Paladin/Paladin_UnholyMount.java` | Alignment combat bonus copied onto the mount | 206 |
| `Abilities/Paladin/Paladin_PlanarDefiance.java` | Plane-scoped ambiance that negates absorb-damage | 184 |
| `Abilities/Paladin/Paladin_PaladinsMount.java`, `_RighteousDefense`, `_Wickedness`, `_Purity`, `_PoisonImmunity` | Protective and mount-support skills | 174, 131, 107, 90, 81 |
| `Abilities/Ranger/Ranger_Enemy1.java`, `Ranger_Enemy2.java` | Favored-enemy combat bonus | 207, 49 |
| `Abilities/Ranger/Ranger_Camouflage.java` | Concealment with a detection-save bonus | 277 |
| `Abilities/Ranger/Ranger_AnimalFrenzy.java` | Permanent buff for animal companions | 182 |

## How it works

### 1. The poison base class (`Abilities/Poisons/Poison.java`)

**Data model.** `rank` (double, default 1.0) is the potency knob used in almost every formula. It is set from `RANK=` in the misc text (`Poison.java:176-185`) and returned as `abilityCode()` (`:79-82`). `poisonTick` starts at 3 (`:173`) and counts down to the next pulse. `itemAmbiance` and `mobAmbiance` are strings added to the target's `PhyStats` (`:263-277`). `processing` is a re-entrancy guard for item messages (`:169`, `:353-354`, `:415`). Poisons are kept out of the command list (`:85-88`) and invoked by trigger word (`POISONSTING` in the base, `:90`).

**Template hooks.** Each concrete poison overrides a set of `POISON_*` methods:
- `POISON_TICKS()` (duration; 0 means the default, `:109-112`).
- `POISON_DELAY()` (ticks between pulses; default 3, `:114-117`).
- `POISON_DAMAGE()` (default `roll(1, invoker level, round(rank))`, `:159-162`).
- Messages: `POISON_START` (default `"^G<S-NAME> turn(s) green.^?"`, `:124-127`), `POISON_AFFECT`, `POISON_DONE` (default `"The poison runs its course."`, `:119-122`), `POISON_CAST`, `POISON_FAIL`.
- `POISON_AFFECTTARGET()`, `POISON_ADDICTION_CHANCE()` (default 0, `:139-142`), and `POISON_MAKE_PEACE()`.

**Application: four routes.**
- Consumption. `executeMsg` reacts to a Drink being drunk (`TYP_DRINK`, `:365-372`) or Food being eaten (`TYP_EAT`, `:375-377`). Both call `catchIt(source, source)`, so the consumer poisons themself. A drink that runs dry without disappearing loses the effect (`:369-372`).
- Weapon coating. When a poisoned weapon damages a MOB, `tickDown--` (`:391`) uses up one tick of the coating and `catchIt` is called on the victim (`:392`).
- Filling. Pouring a poisoned drink into another container copies the effect across (`:395-408`).
- Player invocation. `invoke()` (`:423-490`) runs `proficiencyCheck(mob,0,auto)` (`:430`), posts a `MSK_MALICIOUS_MOVE|TYP_POISON` message (`:434`), gives the room a veto (`:439`), and then calls `maliciousAffect` (`:452` for a MOB; `:468` for an item, fixed at 100 ticks).

`catchIt` (`:207-260`) decides whether a poison lands. The poisoner is the invoker, or else the mob (`:209-213`). Player-on-player poisoning needs `mayIFight` (`:218`). A `TYP_POISON` message is then offered to the room (`:224-227`), so any other ability can veto it. That is how `Paladin_PoisonImmunity` works (section 6). Items get an addiction roll, but only for Drink, Food and MagicDust (`:247-253`).

**Duration.** `maliciousAffect` (`Abilities/StdAbility.java:2185-2208`) passes `POISON_TICKS()` through `getMaliciousTickdownTime`. A positive value is used as-is (`StdAbility.java:2176-2177`). Zero, the default, gives `round(adjustedLevel × 1.1) + 25` ticks (`StdAbility.java:2178`), passed through `adjustMaliciousTickdownTime`. A negative value scales that default by a percentage (`:2181-2182`). With the 4000 ms tick (`core/CMProps.java:681`), a level-10 poisoner with no adjustment gets 36 ticks, about 2.4 minutes. For an item poison, `asLevel` is the item's level (`Poison.java:234-236`).

**Pulse.** `tick()` (`:280-311`) decrements `poisonTick`. At zero it resets to `POISON_DELAY()` and shows `POISON_AFFECT()` to the room (`:297-298`). A non-zero `POISON_DAMAGE()` is posted as damage from the invoker (`:302`). If the victim is not fighting and the poisoner is in the room and visible, `postAttack` turns the victim on the poisoner (`:303-306`).

**Stats.** `affectCharStats` applies −5 CON and −5 STR (floor 1) only when `ID()` is exactly `"Poison"` (`:314-325`, specifically `:317`). Every subclass that wants a penalty writes its own, for example `Poison_Venom` (`Poison_Venom.java:116-117`).

**Removal.** `unInvoke` (`:328-344`) tells the victim `POISON_DONE()` when the effect can be removed (`:337-338`).

**Item guard.** For an item poison, `executeMsg` returns at `:356-357` if the item has no owner. That return also skips `super.executeMsg` (`:419`). Poisons on unowned items therefore skip base message handling.

### 2. Data-driven poisons: `GenPoison` and `UniPoison`

**GenPoison** (`GenPoison.java`) makes each poison field editable data. There are 20 codes (`:513-533`): CLASS, TEXT, NAME, HELP, TRIGSTR, TICKS, DELAY, DONEMSG, STARTMSG, TARGTELLMSG, AFFECTTARG, ADDCHANCE, AFFECTMSG, CASTMSG, FAILMSG, ADJUSTMENTS, MAKEPEACE, DAMAGE, MOOD, EFFECTS. Numeric fields (TICKS, DELAY, ADDCHANCE, DAMAGE) are formulas compiled with `CMath.compileMathExpression` (`:173-177`) and evaluated against `formulaVars()` (`:253-267`):
- `@x1` is the invoker's level, `@x2` the invoker's XLEVEL, `@x3` the victim's level, `@x4` the rank.

Defaults (`makeEmpty`, `:149-171`) are ticks `"0"` (default duration), delay `"3"`, addiction `"0"` and damage `"@x4 + 1?@x1"` (`:167`). The `?` operator (`core/CMath.java:1848-1849`) returns a random value between the running total and the right operand, so the default damage is random between rank+1 and the invoker's level. That is my reading of the evaluation order.

Data lives in a static map keyed by the object's ID (`GenPoison.java:127`, accessors `:67-86`). Every object with the same ID shares one record, so a builder defines a poison once and clones it. ADJUSTMENTS text becomes a `Prop_Adjuster` ability that applies property changes (`:192-201`, `:307-320`). EFFECTS names are looked up as coded spells or behaviours (`makeEffectsPair`, `:179-186`). MOOD adds a `Mood` ability (`:232-251`). `setStat` renames the poison by moving its record under the new ID (`:636-644`).

**UniPoison** (`UniPoison.java`) overrides the storage to be per-instance (`:64`) and manages several poisons on one target. `addEffect` (`:80-100`) attaches a new poison as a sibling of the target's existing UniPoison (`P.siblings.add(this)`, `:98`). If an identical one already exists, the duration is extended to the larger value instead (`:92-94`). Stat effects are summed over siblings (`:117-141`). `tick` (`:143-175`) removes expired siblings (`:151-152`). When the head expires, it promotes `siblings.remove(0)` by copying that sibling's state and vars into itself (`:159-168`), so the chain keeps running. `getSpells` lists every sibling (`:255-263`).

### 3. The alcohol family

**Poison_Alcohol** (`Poison_Alcohol.java`) keeps an integer `drunkness`, which starts at 5 (`:54`, `:150`). It is both the amount of alcohol and the strength of its effects.
- Tier names (`displayText`, `:64-76`): 0 is "Holding your own", up to 3 is "Tipsy", under 10 is "Drunk", otherwise "Smashed".
- Penalties: DEX falls by `drunkness`, minimum 1 (`:197-204`). Attack falls by `drunkness + level` (`:190-195`).
- Each tick (`:229-356`): a mood ability fires with chance 4 × drunkness % (`:248-251`), and a random emote fires with the same chance (`:254`). The emote set depends on alignment: evil, good, or neutral, with nine lines each (`:259-352`).
- Mood: one of HAPPY, MEAN, SILLY, ANGRY, SAD or none, chosen once per drinker from `Name().hashCode() % 6` (`:178-179`).
- Speech: at drunkness 5 or more, speaking, telling or channel messages invoke a `Drunken` ability (`:375-393`). Other targeted messages are redirected to a random inhabitant with chance drunkness × 20 % (`:394-413`). That redirect needs more than two inhabitants (`:405-409`).
- Addiction chance is `alcoholContribution()^3 / 10`, in integer arithmetic (`:207-210`).

**Stacking** (`invoke`, `:422-482`). The new dose adds its contribution (`round(rank)`, `:140-143`, `:443`) to the drunkness already on the target. If an alcohol of the same or higher `alchoholLevel` is present, all such effects are set to the summed value and given a full timer (`:443-458`). Otherwise weaker alcohol effects are removed (`:466-467`) and the new one is applied. A second dose therefore enlarges the first effect rather than replacing it.

**Variants.**
- `Poison_Liquor` contributes `1 + round(rank)` (`Poison_Liquor.java:66-70`). On expiry out of combat, it shows "curl(s) up on the ground and fall(s) asleep." (`:78-90`). That is a display line; the code does not change the mob's stance there.
- `Inebriation` contributes `5 + round(rank)` and uses trigger `INEBRIATE` (`Inebriation.java:51-63`).
- `Poison_Firebreather` extends Liquor and contributes `2 + round(rank)` (`Poison_Firebreather.java:65-70`). Each tick, with chance drunkness %, it belches (`:88`). In 40 % of belches the belch is fire (`:90`). Each other occupant then takes `roll(min(level,10), 6, 1)` burning damage, halved when the message value is positive (`:98-110`).
- `Poison_Clarity` is the counter. It refuses any targeted message whose tool is a `Poison_Liquor` and tells the target "You are immune to …" (`Poison_Clarity.java:135-150`).

### 4. Perception-altering poison: `Poison_Hallucinogen`

This poison rewrites what the victim sees instead of damaging them. In `okMessage` (`Poison_Hallucinogen.java:240-286`), looking at a room is redirected to a random room (`:246-255`). `<S-NAME`, `<T-NAME` and `<O-NAME` tokens in messages are replaced with hallucinated names (`halluString`, `:226-237`). Other people's messages are rewritten only when the room holds fewer than two players (`:272-283`).

Names come from `getHalluName` (`:135-224`). They are cached per object in a Hashtable (`:122`, `:140-141`, `:159-194`), so a monster keeps its false name while the poison lasts. Exits are re-rolled on every call (`:197-217`), because that branch returns before the cache is written. Items draw from raw-material names (50 %), basic item prototypes (40 %) and race names (10 %) (`:155-172`). MOBs draw material (10 %), item (10 %) or race (80 %) (`:177-194`). Half the time a name gets an adjective taken from Lacquerring recipe records (`:144-152`, index 3, meaning unverified).

### 5. Stat-debuff and special-mechanic poisons

Most of the rest follow one pattern: a timed effect that subtracts from one or more stats and pulses a message, sometimes with a damage roll. The catalogue in the next section gives the numbers. Special mechanics:
- **Anesthesia** (`Poison_Anesthesia.java`) raises CON by rank (`:117-120`). It blocks flee and torture noise, and softens damage verbs in messages through `randomHurt`, which rolls 1d10 (`:123-126`).
- **Caffeine** (`Poison_Caffeine.java`) strips sitting, sleeping and sneaking from the disposition (`:128`). It blocks sit and sleep with "You're too caffeinated for that!" (`:144`).
- **Slumberall** (`Poison_Slumberall.java`) sets the sleeping disposition after the first tick (`:109-115`, `:154`). It then blocks damage messages aimed at the sleeper (`:128`).
- **Hazia** (`Poison_Hazia.java:131-171`) runs on each pulse (`:141`). It drains mana by mana/20 (minimum 1), cumulatively, through `affectCharState`. It forces a Sit when fatigue reaches exhaustion.
- **Hives** (`Poison_Hives.java`) lowers CHA by rank and DEX by 2 + rank (`:166-`). Its tick has a double-decrement bug (see Problems).
- **Ghoultouch** (`Poison_Ghoultouch.java`) sets CAN_NOT_MOVE in the sense mask (`:113-118`) and the paralysing and unholy flags (`:59-61`).
- **Glowgell** (`Poison_Glowgell.java`) only adds the glowing disposition (`:102-105`). `catchIt` returns false (`:96-98`), so it never poisons by contact.
- **Rotten and Sickening** deal damage over time with vomiting or moaning messages (`Poison_Rotten.java:132-134`, `Poison_Sickening.java:99`, `:118-120`). Rotten targets only some food and drink materials (`:71-80`). The rot system attaches it to raw food: `Libraries/RawCMaterial.java:307-308` looks up `Poison_Rotten` and `Poison_Purify` on raw materials, and `CharClasses/Gaoler.java:511` checks for it on food.

### 6. Paladin skills

**Gate and base** (`Abilities/Paladin/PaladinSkill.java`, read as the base for this bucket):
- `paladinAlignmentCheck` (`:110-122`) refuses a player's manual use when the player's factions conflict with the skill. `disregardsArmorCheck` can exempt it. The message is "You don't feel worthy enough to @x1."
- `isPaladinGoodSide` (`:124-141`) requires GOOD, and LAWFUL if that axis is loaded. `isPaladinAntiSide` (`:143-160`) is the EVIL and CHAOTIC mirror.
- The base `tick` (`:198-232`) returns false when the paladin falls out of faction (`:207-208`). Tick results of false are normally treated as expiry. It rebuilds `paladinsGroup` from same-room group members when enter, leave, recall, follow, rebuke or life events set `rebuildGroup` (`:181-195`, `:209-227`). It also has a training roll (`:228-230`).
- `appropriateToMyFactions(null)` returns true (`StdAbility.java:2865-2870`), so checks pass when there is no invoker.

**Combat skills.**
- **Paladin_MountedCharge** (`Paladin_MountedCharge.java`) is `MCHARGE [target]` (`:52`). It needs a riding mount (`:166-171`) and refuses melee range (`:156-161`). Range runs from 1 to 99 (`:89-99`). The charge steps toward the target with the same message each step (`:180-195`). On contact, `beneficialAffect(mob,mob,asLevel,2)` gives the rider a two-tick buff (`:199`; a positive tick value is literal, `StdAbility.java:2312-2315`). The buff adds attack +4L, armor +4L and damage +L, where L is `adjustedLevel(mob,0)` (`:126-133`). It ends at the rider's next weapon attack (`:106-110`) or the next mob tick after that (`:117-121`). The armor term is a penalty, as explained in the conventions note below.
- **Paladin_UnholyStrike** (`Paladin_UnholyStrike.java`) is `UNHOLYSTRIKE [target]` (`:52`). It needs a wielded melee weapon (`:158-167`) and may not be at range while in combat (`:149-154`). The target must be good or lawful (`:198-205`). The target gets a two-tick effect that adds +200 armor (`:103-106`), which makes it easier to hit. Then `postAttack` lands (`:223`), and the effect is removed (`:225`). Inside the effect's `okMessage` (`:122-145`), the next damaging hit from the paladin's weapon has its value doubled and XLEVEL added (`:141`). It shows "A dark unholy shadow comes down upon <T-NAME>!" (`:139`, or with the deity's name at `:135-137`), then the effect ends (`:142`). It is a one-shot.

**Mounts.**
- **Paladin_SummonMount** (`Paladin_SummonMount.java`) is `CALLMOUNT` (`:59`). It works only outdoors (`:162-166`) and is alignment-gated (`:167`). One live mount is allowed per ability instance (`lastHorse`, `:170-180`, `:248`). It picks a random open outdoor exit whose reverse is also open (`:184-201`). It creates a `GenRideable` MOB (`:252`) at the caster's adjusted level (`:216`, `:255`), weight 500 (`:256`), GOOD alignment (`:257`) and no rejuvenation (`:258`). The race comes from the caster's deity's favored mounts (`:259-263`). Names are "a white <race>" for a non-evil caster and "a black <race>" otherwise (`:270-283`). It has four rider slots (`:287`) and is tattooed as summoned by the caster (`:285-286`). The mount walks in from the chosen side (`:225`) and is set to follow the caster (`:230`). Each mob tick it is destroyed if it stops following, dies, or the caster leaves or changes mount (`:120-143`). The caster's quit removes it (`:146-157`). Unsummoning shows "<S-NAME> gallop(s) away!" (`:114`).
- **Paladin_UnholyMount** (`Paladin_UnholyMount.java`) is auto-invoked on the paladin (`:85-94`). While the paladin rides and fights a good or lawful victim, it copies a "fierce" effect onto the mount (`:151-177`). That effect adds attack +(prof/100 × 2L), damage +(prof/100 × L) and armor −(prof/100 × 2L), which is better defence. It also sets EVIL and GLOWING (`:115-143`). L is 2 + invoker level / 10, or 10 with no invoker (`:128-134`). The copy is removed when the rider is out of combat or the victim is no longer good or lawful (`:181-201`).
- **Paladin_PaladinsMount** (`Paladin_PaladinsMount.java`) is attached to a summoned mount that the paladin owns, checked through the property record (`:92-102`). It gives the mount a disease and poison save bonus of 50 + prof + 5 × XLEVEL, and a bonus of prof/100 × (level + 2 × XLEVEL) to every saving throw (`:117-124`). It also blocks opposed-alignment harmful spells, fear spells, and poison or disease aimed at the mount, each behind a proficiency check (`:129-173`).

**Protective skills.**
- **Paladin_RighteousDefense** (`Paladin_RighteousDefense.java`). When a malicious action targets a same-room MOB at least three levels below the paladin, the paladin steps in and retargets the message (`:91-116`). The message is "leap(s) to the righteous defense of <T-NAME>". It fires at most once per round (`:70-79`, reset in tick). It adds goodness and lawfulness faction gains of 1d(level/2) + XLEVEL (`:117-126`).
- **Paladin_Wickedness** (`Paladin_Wickedness.java`). Every second tick (`tickTock`, `:75-76`) it does `roll(1, level, 0)` burst damage to good creatures that are either in the paladin's group or fighting the paladin at range 0 (`:86-99`). See Problems.
- **Paladin_Purity** (`Paladin_Purity.java:74-87`). It blocks a negative faction change in alignment or inclination on the bearer, behind a proficiency check. The message is "<S-YOUPOSS> purity protects <S-HIM-HER> from wicked influence."
- **Paladin_PoisonImmunity** (`Paladin_PoisonImmunity.java:64-69`) vetoes poison aimed at the bearer, behind a proficiency check. It adds 50 + prof + 5 × XLEVEL to the poison save (`:79`).
- **Paladin_PlanarDefiance** (`Paladin_PlanarDefiance.java`). On a plane (`:140-143`), if the area has a `Prop_AbsorbDamage` effect, the bearer gets a negating ambiance string built from it (`:166-178`). The help text says it covers "themselves and their group" (`resources/help/skill_help.ini:3924-3927`), but the group branch cannot run (see Problems). The skill is granted at level 35, masked, and only off the Prime Material plane (`CharClasses/Paladin.java:298-299`).

### 7. Ranger skills

- **Ranger_Enemy1** (`Ranger_Enemy1.java`) is the favored enemy. On first use it picks a random racial category from fantasy-theme races (`:97-103`). It excludes categories already used by the character's other favored-enemy skills (`:104-117`), "Unique", "Unknown", and its own race category (`:118-120`). `text()` stores the choice and copies it to the character's other copies of the skill (`:124-147`). When its victim is in the enemy category, it adds attack +(prof/100 × 3L) and damage +(prof/100 × L), where L = 1 + adjusted level (`:173-188`). Training comes from 1 in 10 damaging hits on an enemy (`:190-198`).
- **Ranger_Enemy2** (`Ranger_Enemy2.java:33-49`) is the same skill with its own ID. Enemy3, Enemy4, PlanarEnemy and Skill_RacialEnemy extend the same base but are in other buckets.
- **Ranger_Camouflage** (`Ranger_Camouflage.java`) is `CAMOUFLAGE [target]` (`:82`). It works on a group member, an exit or a room-owned item, outdoors only (`:229-233`), and not in combat (`:213-218`). Success is `proficiencyCheck(mob, 10 × levelDiff, auto)`, with levelDiff = caster level + 2 × XLEVEL − the highest-level observer's level (`:238-248`). The effect lasts `TICKS_ALMOST_FOREVER` (`Abilities/interfaces/Ability.java:48`, `Integer.MAX_VALUE/2`, `:263`). It adds the IS_HIDDEN disposition and removes IS_SNEAKING (`:180-187`). It adds prof + 2 × XLEVEL to SAVE_DETECTION (`:173-178`, `:267-268`). It breaks when the hidden mob moves (`:121-130`). With abilityCode 0 it also breaks on noise, speech, spells, or opening, closing, locking, pushing or pulling an exit or an item the mob does not own (`:132-167`).
- **Ranger_AnimalFrenzy** (`Ranger_AnimalFrenzy.java`) is auto-invoked and cannot be removed (`:65-74`). The ranger copies a non-uninvokable copy of itself onto each animal companion in the same room and group (`:126-141`). While the ranger is in combat, each companion's attack is raised to at least prof/(100 − 5 × XLEVEL) × the ranger's attack adjustment (`:172-179`). Its damage rises by a factor of 4 × prof/(100 − 5 × XLEVEL) (`:176-177`). At proficiency 100 and XLEVEL 0 that is ×5 damage. Training comes at 5 % per in-combat tick (`:160-163`).

## Content worth mining

### Poison catalogue (28 classes)

Trigger and numbers are as coded. "default" means the `POISON_TICKS` value of 0 (see section 1). "Ticks / delay" is duration and pulse interval. Damage is per pulse.

| Class (file) | Display name | Trigger | Ticks / delay | Damage per pulse | Main effect |
|---|---|---|---|---|---|
| Poison (`Poison.java`) | Poison | POISONSTING | default / 3 | d(invoker level) + rank | CON −5, STR −5 (base ID only) |
| GenPoison | Poison (default data) | GPOISON | default / 3 | formula, default random rank+1 to level | data-driven |
| UniPoison | Uni-Poison | UPOISON | data | data | sibling stacking |
| Poison_Alcohol | Alcohol | POISONALCOHOL | 65 / 1 | none | DEX and attack penalties, drunk behaviour |
| Inebriation | Inebriation | INEBRIATE | 65 / 1 | none | alcohol, contribution 5 + rank |
| Poison_Liquor | Liquor | LIQUORUP | 65 / 1 | none | alcohol, contribution 1 + rank, sleep message on expiry |
| Poison_Firebreather | Firebreather | LIQUORFIRE | 35 / 1 | fire belch roll(min(L,10),6,1) | alcohol, contribution 2 + rank |
| Poison_Hallucinogen | Hallucinogen | POISONHALLUCINOGEN | default / 2 | none | rewritten perception |
| Poison_Anesthesia | Anesthesia | POISONANESTHESIA | default / 2 | none | CON +rank, softened damage text |
| Poison_BeeSting | Bee Sting | POISONBEESTING | 10 / 2 | 1 + rank | CON −rank, STR −rank |
| Poison_Bloodboil | Blood Boil | POISONBURN | 20 / 2 | roll(rank,2,0) | attack −20 × rank, CON −rank, STR −(4 + rank) |
| Poison_Caffeine | Hyper | POISONHYPER | 30 / 5 | none | no sit or sleep, mind-altering |
| Poison_Clarity | Clarity | POISONCLARITY | 30 / 5 | none | immune to liquor messages |
| Poison_Decreptifier | Decrepifier | POISONDECREP | 25 / 5 | roll(rank,3,1) | CON, STR, DEX −(9 + rank) |
| Poison_Ghoultouch | Ghoultouch | POISONGHOUL | 7 / 1 | none | cannot move, CON −rank |
| Poison_Glowgell | Glowgell | inherits POISONSTING | default / 3 | none | glowing; no contact poisoning |
| Poison_GoblinDrip | Goblin Drip | POISONDRIP | 50 / 5 | roll(rank,3,1) | CON −rank, STR −(9 + rank) |
| Poison_Hazia | Hazia | HAZIA | 250 / 5 | none | mana drain, fatigue, forced sit |
| Poison_Heartstopper | Heartstopper | POISONSTOP | 8 / 1 | roll(1,19,rank) | CON −rank, STR −rank |
| Poison_Hives | Hives | POISONHIVES | 60 / 5 | none | CHA −rank, DEX −(2 + rank) |
| Poison_Mindsap | Mindsap | POISONSAP | 50 / 1 | none | CON −rank, INT −(9 + rank), mind-altering |
| Poison_Peppersauce | Peppersauce | POISONSAUCE | 10 / 1 | none | cannot see, CON −rank |
| Poison_Rotten | Rotten | POISONROTT | 50 / 5 | roll(rank,3,1) | CON −(9 + rank), STR −(7 + rank), material-limited targeting |
| Poison_Sickening | Sickening | POISONSICKEN | 50 / 5 | roll(1,2,1) | CON −(9 + rank), STR −(7 + rank), vomiting messages |
| Poison_Slumberall | Slumberall | POISONSLEEP | 50 / 1 | none | forced sleep, damage blocked while asleep |
| Poison_Sting | Sting | POISONSTING (duplicate) | 10 / 2 | 1 + rank | CON −rank |
| Poison_Venom | Venom | POISONBITE | 25 / 2 | roll(rank,9,1) | CON −(6 + rank), STR −(2 + rank) |
| Poison_XXX | XXX | POISONXXX | 25 / 2 | roll(1,99,rank) | CON −(4 + rank), STR −rank |

Addiction chances (used only for food, drink and dust) are 5 for Hallucinogen, 2 for Anesthesia and Clarity, 1 for Caffeine and 25 for Hazia. Alcohol uses the cubic formula above. The rest use the base value of 0 (`Poison.java:139-142`).

Source positions for the table: the trigger and tick values sit at the top of each class (for example `Poison_Venom.java:51`, `:61`, `:67`), and the damage and stat overrides are in the lines cited in section 5 and in the class files.

### Other usable content

- **Poison message samples** (short, from the code): "<S-NAME> turn(s) green." (`Poison.java:126`), "<S-NAME> start(s) hallucinating." (`Poison_Hallucinogen.java:89`), "Your blood stops burning." (`Poison_Bloodboil.java`), "Your muscles relax again." (`Poison_Ghoultouch.java:79`), "You feel sober again." (`Poison_Alcohol.java:105`).
- **Drunk emotes**: staggering, hiccups, belching, staring at nothing, and a few alignment-specific variants. A crude emote exists in the evil set (`Poison_Alcohol.java:259-289`). Not reproduced here.
- **Hallucinated name pools**: random basic item prototypes, race names, and raw-material names (`Poison_Hallucinogen.java:155-194`).
- **Anesthesia verb softening**: damage verbs are swapped for gentler ones such as "affect(s)", "pressure(s)", "touch(es)" and "push(es)" (`Poison_Anesthesia.java:123-169`).
- **Mount naming**: "a white <race>" or "a black <race>", with special text for horses (`Paladin_SummonMount.java:270-283`). Races come from the deity's favored-mount list.
- **Favored-enemy categories** are generated at runtime from the fantasy race categories, so no fixed list exists in this bucket.

### Paladin and Ranger skill table

| Skill (file) | Trigger | Granted at (CharClasses) | Effect |
|---|---|---|---|
| Paladin_UnholyStrike | UNHOLYSTRIKE | level 4, EVIL alignment only (`Paladin.java:155-158`) | one-shot doubled hit on good or lawful target |
| Paladin_SummonMount | CALLMOUNT | level 5 (`Paladin.java:161`) | summoned steed that follows |
| Paladin_PaladinsMount | auto | level 7 (`Paladin.java:177`) | saves for the summoned steed |
| Paladin_Purity | auto | level 14 (`Paladin.java:232`) | blocks alignment loss |
| Paladin_MountedCharge | MCHARGE | level 16 (`Paladin.java:243`) | charge buff for the rider |
| Paladin_UnholyMount | auto | level 16, EVIL only (`Paladin.java:244-247`) | alignment combat bonus for the mount |
| Paladin_PoisonImmunity | auto | level 17 (`Paladin.java:250`) | poison veto and save bonus |
| Paladin_RighteousDefense | auto | level 21 (`Paladin.java:266`) | intercept attacks on weaker allies |
| Paladin_Wickedness | auto | level 24, EVIL only (`Paladin.java:281-284`) | aura burst on good creatures |
| Paladin_PlanarDefiance | auto | level 35, masked, off Prime Material (`Paladin.java:298-299`) | negate absorb-damage on planes |
| Ranger_Enemy1 | auto | level 8 (`CharClasses/Ranger.java:176`) | favored enemy |
| Ranger_Enemy2 | auto | level 13 (`Ranger.java:197`) | second favored enemy |
| Ranger_Camouflage | CAMOUFLAGE | level 21, needs Ranger_Hide (`Ranger.java:238`) | concealment with a detection bonus |
| Ranger_AnimalFrenzy | auto | level 30 (`Ranger.java:257`) | frenzy for animal companions |

## Notable design ideas

- **A poison is an effect that lives on an object.** Poisons ride on food, drink and weapons, and the object's own messages trigger them (`Poison.java:347-420`). Poison needs no separate system, and the item is the delivery mechanism.
- **Veto, then react.** The two-phase message flow (`okMessage` may refuse, `executeMsg` reacts) carries immunities. Clarity (`Poison_Clarity.java:135-150`), PoisonImmunity (`Paladin_PoisonImmunity.java:64-69`) and Purity (`Paladin_Purity.java:74-87`) all work this way.
- **Retargeting inside a message.** Drunks redirect speech to a random bystander (`Poison_Alcohol.java:394-413`), and Righteous Defense swaps itself in as the target (`Paladin_RighteousDefense.java:113-116`). Both suit a text game, where the same event can be rewritten before it lands.
- **Stacking by summation.** Alcohol adds its doses and resets the timer (`Poison_Alcohol.java:443-458`). UniPoison keeps a sibling chain and promotes the next one when the head expires (`UniPoison.java:159-168`).
- **Per-viewer illusion with cached names.** Hallucinogen keeps a stable false name for each object (`Poison_Hallucinogen.java:122`, `:159-194`). Each viewer sees a consistent world, which is cheap and effective for text.
- **Charge as two steps.** The charge runs a visible approach with the same message each step, then applies a buff that the next weapon attack consumes (`Paladin_MountedCharge.java:180-195`, `:106-110`).
- **One-shot strike.** The unholy strike applies its doubling in `okMessage` and removes itself straight after the attack (`Paladin_UnholyStrike.java:141-142`, `:221-225`).
- **Mounts as followers with despawn rules.** The summoned mount is an NPC that follows, and it is destroyed when its owner leaves, quits or dies (`Paladin_SummonMount.java:120-157`).
- **Mana drain as a pulse.** Hazia takes a share of max mana on each pulse, then holds it down through `affectCharState` (`Poison_Hazia.java:131-171`).
- **Data-driven poisons.** GenPoison's 20 codes and formula fields give builders a full poison editor without new classes (`GenPoison.java:513-533`).

## Problems and cruft

### Confirmed by reading

1. `GenPoison.java:218-227`: `getOtherEffects` creates and configures a copy of each EFFECTS ability, but never adds the copy to `neffects`. The list it caches is therefore always empty, and the EFFECTS field has no effect.
2. `UniPoison.java:186`: RANK is read with `map.get(newMiscText)` (the whole string) instead of `map.get(code)`. The lookup returns null. `CMath.s_double(null)` swallows the NullPointerException and returns 0.0 (`core/CMath.java:2016-2024`). Any `RANK=` in a UniPoison therefore sets rank to 0.
3. `UniPoison.java:199-203`: the health description uses `name()` (this poison) for every sibling, so sibling names are never shown.
4. `GenPoison.java:242-245`: the SUPPRESS_MOOD test runs after `mood` is assigned, so it only affects the first call. Later calls skip the test because `mood != null` (`:234`).
5. `Poison.java:317`: the base −5 CON and −5 STR apply only when `ID()` is exactly `"Poison"`. The base penalty is effectively unreachable for any subclass.
6. `Poison_Hives.java:156-158` with `Poison.java:291-293`: Hives' tick calls the base tick, which decrements and resets `poisonTick` and shows POISON_AFFECT. It then decrements `poisonTick` again. Two pulse streams run, each showing the same message, roughly every 2.5 ticks instead of every 5. This follows from reading the code and was not run.
7. `Poison_Firebreather.java:79-84`: for an affected item with an invoker, `super.tick` is called twice in one tick, so the tick-down runs twice.
8. `Poison_Liquor.java:78-90` with `Poison_Alcohol.java:212-227`: `unInvoke` rolls Disease_Migraines twice (drunkness × 10 % and 1 %) and calls `postStand` twice.
9. `Poison.java:356-357` returns from `executeMsg` before `super.executeMsg` (`:419`) for an unowned item. Base message handling is skipped for those items.
10. `Poison_Sting.java:51` and the base `Poison.java:90` both use trigger `POISONSTING`. `Poison_Glowgell` has no trigger of its own and inherits it, so three classes share one trigger word.
11. `Paladin_PlanarDefiance.java:78-114`: the group branch cannot run. This class never sets `paladinsGroup`. It has no constructor, and PaladinSkill leaves the field null (`PaladinSkill.java:88`) and only rebuilds it when non-null (`:209-226`). The `oldSet` logic is therefore dead. The `this.affected = invoker()` writes at `:81`, `:97`, `:110` and `:123` change a field during tick. The help text promises a group effect the code does not deliver.
12. `Paladin_UnholyStrike.java:178` against `:198-205`: castingQuality prefers evil targets, but invoke requires good or lawful ones. An evil target that is also lawful is accepted. An evil chaotic target is refused, so the AI's preference points partly at targets the skill refuses.
13. `Paladin_Wickedness.java:86-90`: good creatures in the paladin's own group are damaged, not spared. `Paladin_Goodness.java:88` uses the same group condition for a beneficial effect, so this looks like a copy with the sign flipped. Verify intent. The local `invoker` at `:93` also shadows the field.
14. `PaladinSkill.java:228-230`: `rollPercentage()==1` and then `rollPercentage()<10` are two independent rolls, so training happens about 0.09 % of ticks. `Paladin_UnholyMount.java:168` uses a single 1 % roll.
15. `Paladin_MountedCharge.java:201-207`: `done` is set on the cast instance, but the buff being ticked is the copy from `beneficialAffect` (`:199`). These writes appear to have no effect. The buff ends through `executeMsg` (`:106-110`) regardless.
16. `Ranger_Enemy1.java:121`: if every race category is excluded, `choices` is empty and `roll(1,0,-1)` returns −1 (`Libraries/Dice.java:400-401`). `get(-1)` then throws.
17. `Ranger_Enemy1.java:149-160`: the per-victim enemy cache is never cleared when a victim's race changes.
18. `Ranger_AnimalFrenzy.java:175-177`: at XLEVEL 20 the divisor 100 − 5 × XLEVEL is zero, and above that it is negative. `CMath.div` has no guard (`core/CMath.java:433-435`). Whether XLEVEL can reach 20 is unverified.
19. `Ranger_AnimalFrenzy.java:93-94`: the comment says the group collection is shared with the parent. The copies come from `copyOf` at `:136`, and whether `rangersGroup` is shared is unverified.
20. `Poison_Hallucinogen.java:275`: `msg.source().location()` is used without a null check. Reachability is unverified.
21. Naming: "camoflauge" (`Ranger_Camouflage.java:244`, `:246`), "caffinate" in the Caffeine fail message, the display name "Decrepifier" for class Decreptifier, and backticks in names such as "Paladin`s" (`Paladin_Purity.java:46`, `Paladin_Wickedness.java:43`). The backtick is probably an apostrophe escape, and the help file uses it too.

### Design cruft (not bugs)

- Reflection-based cloning (`GenPoison.java:94-107`) and static ID-keyed maps (`:127`) make object identity depend on strings.
- `Poison_Alcohol.java:415-418` has an empty `else` branch.
- Poisons encode an "XLEVEL" concept (extra levels from class progression) in almost every formula. A single tier number would be simpler.
- The `XXX` poison (`Poison_XXX.java:43`, `:51`) uses a placeholder-style name and trigger. No sexual content was found in this bucket.

## Conventions worth knowing

- **Armor: higher is worse.** The hit roll uses the negated adjusted armor (`Libraries/MUDFight.java:409-424`), and the adjusted value is `armor − adjustments − 100` (`core/CMProps.java:3203`). A larger `phyStats().armor()` therefore makes the bearer easier to hit. On that reading, MountedCharge's +4L armor and UnholyStrike's +200 armor are penalties to the bearer, and UnholyMount's armor reduction is a benefit. This is my reading of the code, and it matches the design of each skill.

## Takeaways for a Python SUD

- **Data first.** A poison is a record, not a class. One dataclass with the fields the table uses is enough:

```python
@dataclass
class PoisonSpec:
    id: str
    name: str
    triggers: tuple[str, ...]
    duration: int | None = None       # None = default: round(1.1 * level) + 25
    pulse_every: int = 3              # ticks between pulses
    pulse_damage: str = ""            # dice string, e.g. "1d19+rank", parsed by one helper
    stat_mods: dict[str, int] = field(default_factory=dict)   # {"con": -5}
    pulse_text: str = ""
    done_text: str = "The poison runs its course."
    addiction_chance: int = 0
```

  Use `None` for "default" rather than the source's `0`, which is easy to misread.
- **Keep the veto-then-react flow.** An effect offers `before(event) -> bool` (for immunities such as Clarity and PoisonImmunity, and for retargeting) and `after(event)` (for reactions). Two plain methods on a list of active effects replace the `CMMsg` bitmask machinery.
- **Alcohol as an integer.** Store `intoxication`, sum on re-dose, and derive the tier name, speech slur chance and emote chance from it in pure functions. Keep the four tier names.
- **Hallucinations as a per-viewer name map.** Cache a false name per entity id and rewrite the outgoing text. Exits should be cached too, to avoid the flicker the source has.
- **Replace the formula language.** Plain fields with a restricted expression evaluator, or Python callables in a registry, cover what GenPoison does. Keep the `None` default explicit.
- **Single-player edits.** Drop PvP gating (`mayIFight`), the group rebuild and rebuke events, and the faction plumbing. "Poisoner" becomes an actor id or "environment". "Group" becomes a party list. Righteous Defense becomes "protect a companion". The summoned mount becomes a companion entity with its own follow and despawn rules, and AnimalFrenzy becomes a buff for the player's animal companions.
- **Drop from the source.** Reflection cloning, static ID maps, `WeakReference`, the flag bitmasks and the `ACODE` categories. Keep the `phyStats` idea as a dict of modifiers, with one convention: higher is better, which avoids the inverted armor reading.
- **Fix before porting.** The stacking, mood-suppression and tick-count bugs above are the kind a Python rewrite should not copy.

## Coverage

Paths are relative to `com/planet_ink/coffee_mud/`.

- **Read in full** (except the licence and import header): `Abilities/Poisons/Poison.java`, `GenPoison.java`, `UniPoison.java`, `Poison_Alcohol.java`, `Inebriation.java`, `Poison_Hallucinogen.java`; all 10 files in `Abilities/Paladin/` in the bucket; all 4 files in `Abilities/Ranger/` in the bucket.
- **Read in part** (overrides and effect bodies): `Poison_Liquor.java`, `Poison_Firebreather.java`, `Poison_Hives.java`, `Poison_Hazia.java`, `Poison_Anesthesia.java`, `Poison_Slumberall.java`, `Poison_Caffeine.java`, `Poison_Clarity.java`, `Poison_Rotten.java`, `Poison_Sickening.java`.
- **Skimmed by grep** (identity, trigger, timing, damage and stat lines): `Poison_BeeSting.java`, `Poison_Bloodboil.java`, `Poison_Decreptifier.java`, `Poison_Ghoultouch.java`, `Poison_GoblinDrip.java`, `Poison_Heartstopper.java`, `Poison_Mindsap.java`, `Poison_Peppersauce.java`, `Poison_Sting.java`, `Poison_Venom.java`, `Poison_XXX.java`, `Poison_Glowgell.java`.
- **Outside the bucket, followed as needed**: `Abilities/Paladin/PaladinSkill.java` (base class, read in full); `Abilities/StdAbility.java` (`maliciousAffect`, `getMaliciousTickdownTime`, `beneficialAffect`, `appropriateToMyFactions`, `getXLEVELLevel`); `Libraries/MUDFight.java` (`rollToHit`, `adjustedArmor`); `core/CMath.java` (formula operators, `s_double`, `div`); `core/CMProps.java` (tick length, armor formula); `Libraries/Dice.java` (`roll`); `Abilities/interfaces/Ability.java` (`TICKS_ALMOST_FOREVER`); `Abilities/Ranger/Ranger_Hide.java` (helper functions); `CharClasses/Paladin.java`, `CharClasses/Ranger.java` and `CharClasses/Gaoler.java` (grant levels and references); `Libraries/RawCMaterial.java` (rot references); `resources/help/skill_help.ini` (PlanarDefiance help text).
- **Skipped**: nothing in the bucket was skipped. Abilities referenced but outside the bucket, such as `Drunken`, `Disease_Migraines`, `Addictions`, `Mood`, `Prop_Adjuster`, and `Prop_AbsorbDamage`, were not read.
