# Abilities: Archon and Paladin skill families (part 1 of 3)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b039` (Abilities/(small families) [part 1/3: ArchonSkill .. Paladin_InciteFight]) · 40 files · 7809 lines of Java.

## Summary

The Archon family (19 files: `ArchonSkill` and 18 subclasses) is a toolbox for game administrators: watching and recording players, punishing or containing them, inspecting and crafting items, and driving a monster with an LLM. The Paladin family (21 files: `PaladinSkill`, 11 subclasses of it, and others on `StdAbility` or a crafting base) is a class kit for a good-aligned fighter: passive auras that act on the paladin's own party, a few stances and strikes, two crafting skills that forge a holy or unholy weapon, and a mana-costed heal. Both families are built from the same pieces: a base class that carries shared rules (targeting, alignment gating, party tracking) and small listener hooks (`okMessage`, `executeMsg`, `tick`) that react to messages. The Paladin side maps well onto a single-player combat design, because its auras, charges and stances are ordinary combat effects. The Archon side is mostly multiplayer administration, so it is worth reading for its patterns rather than its features.

## Key files and types

Paths are relative to the CoffeeMUD folder. Line counts come from the manifest.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Archon/ArchonSkill.java` | Base for all Archon abilities: admin-only domain, world-wide target resolution | 159 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/PaladinSkill.java` | Base for Paladin auras and passives: alignment gate, party tracking, teaching refusal | 239 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_CRecord.java` | Combat record: logs a player's messages and score to a file; level-up triggered listener | 611 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Multiwatch.java` | Multiplay watcher: groups online characters by address, counts speech, socials, commands | 409 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Metacraft.java` | Admin crafting: makes any known recipe for free, to self, to room, or to an XML file | 403 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java` | Puts an NPC on a fake socket whose input and output go through an LLM session | 377 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Banish.java` | Sends a mob to a sealed prison room, optionally for a time | 342 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Record.java` | Snoop-records one player's whole session to a file | 242 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Accuse.java` | Files a legal warrant against a target through the area's legal behaviour | 214 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Freeze.java` | Stops a mob from moving | 179 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Injure.java` | Amputates a limb and applies an Injury effect | 179 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Shame.java` | Applies a timed "Shaming" effect | 176 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Hush.java` | Stops a mob's tells and channel messages | 171 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MarkOOC.java` | Blocks role-play XP changes | 164 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Wrath.java` | Halves HP, mana and movement and knocks off the feet item | 156 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_InstanceDisplay.java` | Reports where a player would be sent for an instance type | 154 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Peacefully.java` | Runs a command as the caster, then makes peace if the caster was not fighting | 144 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Infect.java` | Applies any named `DiseaseAffect` to a target | 136 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Stinkify.java` | Sets a player's hygiene to the delimiter plus one | 128 |
| `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MatrixPossess.java` | Keeps an admin possessing NPCs in whatever room a player is in | 187 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java` | Crafting skill (EnhancedCraftingSkill): forges a holy weapon from the deity's weapon | 322 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyStrike.java` | Charged strike: doubles the next weapon hit on an evil or chaotic target | 235 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Defend.java` | "All Defence" stance: no attacks, armor bonus, disengage after a quiet round | 223 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ChaosRage.java` | Berserk-style rage: damage and attack up, armor worse, HP up by a fifth | 206 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyMount.java` | Gives a mount combat bonuses while its rider fights evil or chaotic foes | 206 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HealingHands.java` | Mana-costed heal; for evil-side casters, a burning touch instead | 192 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_InciteFight.java` | Makes a target aggressive through an `Aggressive` behaviour | 181 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CommandHorse.java` | Orders the paladin's own holy mount | 158 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java` | Aura: burns evil party members; shields party from opposed magic, poison and disease | 147 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Breakup.java` | Ends a fight between two other characters | 147 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Fear.java` | Periodic fear effect on nearby enemies (1d4 outcomes) | 147 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ExploitingAura.java` | Moves part of a party member's incoming damage onto a weaker member, doubled | 144 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_AbidingAura.java` | Stops a party member's death for 10 seconds unless HP recovers above 10% | 113 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Goodness.java` | Every other tick, burns evil party members and evil melee attackers | 106 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Courage.java` | Blocks fear-type attacks aimed at party members | 101 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Corruption.java` | Doubles negative alignment changes for the affected character | 91 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CrushingAura.java` | Executes a fighting victim at or below 10% HP unless it saves | 88 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftUnholyReaver.java` | Subclass of the Holy Avenger craft for the evil counterpart | 82 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_DiseaseImmunity.java` | Blocks disease and raises the disease save | 81 |
| `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ImprovedResists.java` | Adds a level-scaled bonus to every saving throw | 69 |

## How it works

### 1. Shared base: ArchonSkill

`ArchonSkill` extends `StdAbility` and implements `ArchonOnly` (`com/planet_ink/coffee_mud/Abilities/Archon/ArchonSkill.java:36`). Its classification is `ACODE_SKILL | DOMAIN_ARCHON` (line 81). It sets `canAffectCode` and `canTargetCode` to 0 (lines 67-76) and `putInCommandlist()` to false (line 61), so the ability is not offered as a normal command. Subclasses set their own `CAN_MOBS`, `abstractQuality()` and `usageType()`. Most use `USAGE_MOVEMENT` (for example `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Banish.java:96-99`). `Archon_Peacefully` uses `USAGE_MANA` (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Peacefully.java:108-111`).

The base class supplies a world-wide target resolver, `getTargetAnywhere` (lines 84-158), which tries these in order:

1. The standard `StdAbility` target (line 91).
2. A given target that is a MOB (lines 96-97).
3. An empty name while in combat with a malicious ability: the current victim (lines 99-100).
4. An empty name for a beneficial self ability, or for a non-malicious ability: self (lines 102-106).
5. `self` or `me` (lines 108-109).
6. A name, searched across all rooms with `CMLib.hunt().findInhabitants(CMLib.map().rooms(), mob, targetName, 50)`, then one picked at random (lines 111-117).
7. Fallback: a player with that name, loaded if necessary (lines 124-128).

It refuses targets that cannot be seen, unless they were heard moving while in combat (lines 133-144), and refuses targets that already carry the ability unless `alreadyAffOk` is set (lines 146-156). Most Archon commands call it with `playerOnly` set to false or true; for example `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Injure.java:118` uses true and `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Banish.java:299` uses false.

Two patterns repeat across the family:

- **Toggle.** Invoking the ability on a target that already has its effect removes the effect. This is in `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Banish.java:303-309`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Freeze.java:150-156`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Shame.java:141-147`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MarkOOC.java:135-141` and `com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java:301-313`.
- **Open-ended effects.** Most status effects are applied with `beneficialAffect` or `startTickDown` using `Ability.TICKS_ALMOST_FOREVER` (`com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java:48`, equal to `Integer.MAX_VALUE/2`). `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Freeze.java:171`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Hush.java:163` and `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MarkOOC.java:156` use it; `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MatrixPossess.java:182` uses `Integer.MAX_VALUE/2` directly.

### 2. Shared base: PaladinSkill

`PaladinSkill` extends `StdAbility` (`com/planet_ink/coffee_mud/Abilities/Paladin/PaladinSkill.java:36`). It is an auto-invoked, non-removable effect that targets MOBs (`isAutoInvoked` line 65, `canBeUninvoked` line 71, `canAffectCode` line 77). Its `abstractQuality` is `QUALITY_OK_OTHERS` (line 59). The classification is `ACODE_SKILL` (line 95).

**Alignment gate.** `paladinAlignmentCheck` (lines 110-122) refuses a manual use with "You don't feel worthy enough to @x1." unless the caster's faction allows it. `isPaladinGoodSide` (lines 124-141) and `isPaladinAntiSide` (lines 143-160) test good and lawful alignment. Each axis is used only if it is loaded in `CMLib.factions()`, so the gate degrades gracefully: if neither axis is loaded, the good-side test returns false.

**Teaching.** `canBeTaughtBy` (lines 162-178) refuses a student whose inappropriate faction is found by `getInappropriateFaction`, with messages to teacher and student.

**Party tracking.** `paladinsGroup` is a set of MOBs (line 88). It is rebuilt on the next tick after any `executeMsg` whose source minor is `TYP_ENTER`, `TYP_LEAVE`, `TYP_RECALL`, `TYP_FOLLOW`, `TYP_REBUKE` or `TYP_LIFE` (lines 180-195). In `tick` (lines 197-232), the rebuild uses `getGroupMembers` and keeps only members in the same room (lines 217-222). The set is non-null only for subclasses whose constructor creates it (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java:58-62`, and the same pattern in AbidingAura, ExploitingAura, CrushingAura, Goodness, Courage and Fear). In every other subclass it stays null, and `copyOf` (lines 99-108) handles that case.

**Per-tick gate.** `tick` returns false when the paladin is no longer appropriate to its factions (lines 207-208), so the aura stops being applied.

**Proficiency gain.** `PaladinSkill.tick` gives proficiency with `if((CMLib.dice().rollPercentage()==1)&&(CMLib.dice().rollPercentage()<10)) helpProficiency(...)` (lines 228-230). That is a 1% roll and then a 9% roll, about 0.09% per tick. See Problems.

### 3. The Paladin aura family

Most auras are PaladinSkill effects that hold an invoker (the paladin) and act on the room each tick. The aura's power depends on the paladin's proficiency: `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java:73-75` makes a proficiency roll every tick (`pass`), and only passing ticks apply the aura. The aura's logic is then split between `tick` (periodic effects) and `okMessage` (vetoes).

**Paladin_Aura** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java`). Each passing tick, for each member of `paladinsGroup` in the room who is evil (when the invoker is good) or good (when the invoker is evil), it deals `round((level + 2*xlvl) / 3)` burst damage (line 89, applied by `postDamage` at lines 91-92). The group-protection part is in `okMessage` (lines 104-145). Messages from a source outside the group that target a group member are vetoed when:

- the spell is malicious, is a cast spell, and its ability flags are holy against an evil aura or unholy against a good aura (lines 118-139), with the message "The holy field around <S-NAME> protect(s) <S-HIM-HER> from the evil magic attack of @x1."
- the message is poison or disease, while the aura is active (lines 141-143).

**Paladin_AbidingAura** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_AbidingAura.java`). Intercepts `TYP_DEATH` for a party member (lines 66-112). If the paladin is appropriate to its factions and the proficiency roll passes (lines 69-76), it sets HP to 1 (line 81) and shows "death is prevented by ... abiding aura!" (line 82). The first time, it stores a runnable and schedules it 10,000 ms later (lines 83-105). When the runnable fires, the member is dropped from the aura's set and killed again only if it is alive and below 10% HP (lines 92-99). Characters with the Dueling skill are excluded (lines 78-79).

**Paladin_ExploitingAura** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ExploitingAura.java`). Flags are `FLAG_UNHOLY|FLAG_CHAOS` (line 66). Once per tick (`oncePerRound`, lines 69, 76, 95, 101), when a party member is about to take damage from someone else (lines 90-99), it looks for another party member at least 3 levels below the target (`targetLevel = target.level - 3`, lines 102, 106-109) with the highest HP fraction. A fraction `0.2 + 0.01*xlvl` of the damage (line 126) is moved to that member (lines 127-134). The member takes twice that amount as burning damage, `postDamage(bestM, bestM, this, dmgTaken, TYP_UNDEAD, BURNING)` (lines 133-136). The amount is capped so that the member is not killed (lines 128-131).

**Paladin_CrushingAura** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CrushingAura.java`). While the paladin is in combat (lines 72-74), if the victim is at or below 10% HP (line 78) and a percentile roll exceeds the victim's `STAT_SAVE_JUSTICE` (line 79), the victim is killed with the message "An aura around <S-NAME> crushes <T-NAME>." (lines 81-82).

**Paladin_Goodness** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Goodness.java`). `tickTock` flips each tick, and the effect runs on every second tick (lines 75-76). It targets evil characters in the room who are in `paladinsGroup` or are fighting the invoker at melee range (lines 86-89). Each hit is `1d(adjusted level)` burst damage (line 94), with the message "The aura of goodness around <S-NAME> <DAMAGES> <T-NAME>!" (line 98).

**Paladin_Fear** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Fear.java`). `fearDown` starts at 5 and resets to 10 (lines 69, 76-78), so the effect fires after five ticks and then every ten. It needs at least two people in the room (line 84), and passes a chance check of `10 + adjustedLevel/5 + 2*xlvl` (line 86). Then it picks a random non-group enemy it may fight (lines 90-101) and rolls 1d4 (line 102): 1 enqueues CRINGE, 2 enqueues COWER (lines 104-109), 3 casts `Spell_Spook` (lines 110-129), and 4 casts `Spell_Nightmare` for two ticks (lines 130-143). In cases 3 and 4 the combat victims of both groups are saved before the spell and restored after it (lines 112-116, 126-127, 132-136, 140-141).

**Paladin_Courage** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Courage.java`). When an attack with `FLAG_FEARING` targets a party member at the same location as the paladin (lines 78-84), and the paladin's proficiency passes (line 87), the attack is vetoed with "Your courage protects you from the @x1 attack." (line 94).

**Paladin_Corruption** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Corruption.java`). For a `TYP_FACTIONCHANGE` message against the affected character that changes its alignment or inclination by a negative value, and only while the paladin is appropriate to its factions and the proficiency roll passes (lines 74-83), the change is doubled (line 86) and the message "<S-YOUPOSS> corruption enhance(s) <S-HIS-HER> wicked influence." is shown (line 85).

**Paladin_DiseaseImmunity** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_DiseaseImmunity.java`). Vetoes `TYP_DISEASE` for the affected character (lines 64-69). `affectCharStats` adds `50 + proficiency + 5*xlvl` to the disease save (line 79).

**Paladin_ImprovedResists** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ImprovedResists.java`). Adds `round(proficiency/100 * (adjustedLevel/2))` to every saving throw in `CharStats.CODES.SAVING_THROWS()` (lines 64-66).

### 4. Stances, strikes and mounts

**Paladin_HolyStrike** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyStrike.java`). This is a charge. `invoke` requires melee range (lines 149-153), a melee weapon (lines 158-167), and an evil or chaotic target (lines 198-205). It then calls `maliciousAffect(mob, target, asLevel, 2, -1)` (line 221). The charge is an effect whose `okMessage` doubles the value of the next weapon damage message from the invoker: `value*2 + xlevel` (line 141). It removes itself after that hit (line 142). `invoke` also removes the charge after `postAttack` (line 225), so it only lives for that one attack call. While the target carries it, `affectPhyStats` adds 200 to armor (line 105). Armor is "lower is better" in this codebase (see the note under Notable design ideas), so this is a penalty to the target.

**Paladin_Defend** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Defend.java`). This is an "all-out defence" stance that lasts until it is turned off. `invoke` toggles it (lines 186-192) and needs combat (lines 193-197). While active, `affectPhyStats` subtracts `20 + 4*xlvl` from armor (line 148), which is a defensive bonus. `okMessage` vetoes the paladin's own weapon attacks, showing "defend(s) <S-HIM-HERSELF> against <T-NAME>" (lines 109-114). `executeMsg` resets `fullRound` when the paladin is hit by a weapon (lines 138-141). In `tick` (lines 152-177), after one full mob tick without a weapon hit, the paladin and its victim make peace (lines 156-171, with the message at line 165). The paladin therefore disengages by surviving a round without being hit by a weapon.

**Paladin_ChaosRage** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ChaosRage.java`). Flags are `FLAG_UNHOLY|FLAG_CHAOS` (line 88). It is refused if the target already has `Paladin_ChaosRage` or `Fighter_Berzerk` (line 174). The effect adds `damage / (6 - 0.2*xlvl)` to damage (line 108), the same fraction to attack adjustment (line 109), and `20 + 2*xlvl` to armor (line 110), which makes the rager easier to hit. HP rises by `round(maxHP / 5)` (line 196), and on removal the amount is taken back, but never below 1 (lines 133-136).

**Paladin_HolyMount** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyMount.java`). An auto-effect on the paladin. When the paladin rides a MOB and is fighting an evil or chaotic victim (lines 155-166), the mount receives a long-lasting copy of the same ability (lines 170-177). The copy gives the mount: `attBonus = (prof/100) * 2 * level` to attack, `damBonus = (prof/100) * level` to damage, and `-attBonus` to armor (lines 135-139), where `level = 2 + adjustedLevel/10` (line 129). It also gets IS_GOOD and IS_GLOWING (lines 140-141). The copy is removed when the mount has no riders, the rider is not in combat, or the victim is no longer evil or chaotic (lines 188-201). Proficiency rises by 1% per mount tick (line 168).

**Paladin_InciteFight** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_InciteFight.java`). Applies a malicious `TYP_MIND` effect (`maliciousAffect(..., 0, CMMsg.TYP_MIND)`, line 156). It attaches an `Aggressive` behaviour with parameter `MOBKILL` (lines 159-161). The effect is removed when that behaviour's `tick` returns true (lines 104-110). If the target was not fighting, the code makes peace first (lines 165-172). The meaning of `MOBKILL` is not verified here.

**Paladin_Breakup** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Breakup.java`). Refuses if the paladin is itself in combat (lines 105-109). For a target that is in combat (lines 120-124), success calls `makePeace` on the target and, if the victim is fighting the target, on the victim too (lines 134-138).

**Paladin_CommandHorse** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CommandHorse.java`). Requires the target to have a property record that is a `Paladin_SummonMount` owned by the invoker (lines 111-118). The order is sent as a verbal message, then a `TYP_MIND` message, then `MSG_ORDER` (lines 127-135). If all three pass, the target makes peace and enqueues the order as `METAFLAG_FORCED|METAFLAG_ORDER` (lines 146-147). A monster with no order gets `FLEE` (lines 99-103).

**Paladin_HealingHands** (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HealingHands.java`). Heal amount and mana cost are both `1 + round(adjustedLevel/4)` (lines 121-126; mana is spent at lines 165-168). Only a good-side caster heals others; an evil-side caster hits the target for burning damage instead (lines 140-145, 177-178). Undead casters cannot heal themselves (lines 147-149). `minCastWaitTime` returns one tick (`CMProps.getTickMillis()`, default 4000 ms; lines 90-93), which the base class presumably treats as the minimum gap between casts (not verified).

### 5. Crafting: Holy Avenger and Unholy Reaver

`Paladin_CraftHolyAvenger` extends `EnhancedCraftingSkill` (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java:37`). It uses the deity's weapons as the model (`getBaseModel`, lines 150-188): it looks for a weapon with `rawLogicalAnd`, and falls back to a generic sword (`GenWeapon`, lines 180-185). Raw materials are chosen by the model's material class (lines 242-277). Required wood is twice the model's weight (line 239). Completion is `50 - qualifyingClassLevel`, with a minimum of 6 (lines 307-309). Failure in `proficiencyCheck` marks the work as messed up (line 306).

The finished item is restricted with two effects (lines 207-222). The first is a `Prop_HaveZapper` with the mask `ACTUAL -CLASS +Paladin -ALIGNMENT +Good -ALIGNMENT +Lawful` (lines 209-216). The second is a `Prop_Doppleganger` at `120%` (lines 217-218). The item is marked IS_GOOD (lines 220-221).

`Paladin_CraftUnholyReaver` extends the Holy Avenger class (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftUnholyReaver.java:37`). It changes the name to "the Unholy Reaver" (lines 61-64) and the mask to `ACTUAL -CLASS +Paladin -ALIGNMENT +Evil -ALIGNMENT +Chaotic` (lines 69-74). The second effect is wrong; see Problems.

### 6. Archon tools: recording, monitoring and crafting

**Archon_CRecord** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_CRecord.java`). The effect has two roles, chosen by `ISRECORDER` in its misc text (`setMiscText`, lines 160-198). As a listener (on the admin), it registers a global handler for `TYP_LEVEL` (line 178) and starts a recording when a player levels up at or above `MINLEVEL` (default 10, line 106). The trigger is in `executeMsg` (lines 259-276): the time since the player's `leveledDateTime(curLevel)` must be between 1 second and `TRIGGERTIME`. Whether `leveledDateTime` is set before the `TYP_LEVEL` message is sent is not verified. As a recorder (on the target), it writes to a file. It logs a session banner with the port and `getScore` (lines 243-247). It logs XP events with `TYP_EXPCHANGE` (lines 328-336). It logs malicious or in-combat messages with colours stripped and filtered through `CMLib.coffeeFilter().fullOutFilter` (lines 339-353). A recording ends at the recorded player's next level event outside the window (lines 278-304).

Buffer rules: flush when the buffer exceeds 65,536 characters (`FLUSH_THRESHOLD`, line 116) or after 10 minutes without a write (lines 361-362). A "spot check" score is appended on flush (lines 369-373). Recording stops when the file reaches 64 MB (`CANCEL_THRESHOLD = 65536*1024`, lines 117 and 425-432). The admin commands are LIST, START <minutes>, STOP, MINLEVEL <n>, ADD <player>, and DEL <player> (lines 450-607).

**Archon_Record** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Record.java`). Snoop-records a player's session. `set filename`, `set stripsnoop` and `set stripcrlf` store options on the admin's effect (lines 147-169). Invoking RECORD on a target attaches a `FakeSession` that writes to a file (lines 221-233), and `tick` makes the target's session snoop the fake session (lines 133-139). The default file name is `/<name><millis>.log` (lines 207-208). Recording the same target again stops it (lines 176-193). The server log line is suppressed when a sysop records a non-sysop (lines 213, 218).

**Archon_Multiwatch** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Multiwatch.java`). Groups online characters by their connection address and watches any address with two or more characters (`AUTO`, lines 235-281). Each watched character gets a non-savable copy of the effect (lines 266-271). Counters are kept in `DATA` (a per-MOB `int[10]`, lines 95, 98-108). `executeMsg` counts speech and socials (lines 131-181). `nonIPnonMonsterWithMe` (lines 112-128) is true when another character from the same address is in the room, so the "GOOD" counters count speech and socials made while an alt is present; the name is not explained. `tick` compares each character's last command with its predecessor and with the first same-address peer that has the effect (lines 184-230). The report lists per address a total of "Syncs" and a table of name, speech, socials, commands and orders (lines 356-400).

**Archon_Metacraft** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Metacraft.java`). Builds a list of every `ItemCraftor` ability, sorted by `lowestQualifyingLevel` (lines 87-116). Arguments are `to self`, `to here`, or `to file <name>` (lines 123-151), an optional material (lines 154-159), and then a recipe, `everything`, `every <recipe>`, or `all <skill>` (lines 190-263). Items are created with `craftItem` and `craftAllItemSets` (lines 285-326). Items go to the admin's hands or to the room (lines 330-357). Items that belong to a clan are stamped with the admin's clan if it has the ENCHANT privilege (lines 334-345). With `to file`, the items are written as XML, with referenced files included (lines 359-393).

### 7. Archon tools: NPC takeover and LLM

**Archon_LLMIFY** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java`). Puts an NPC on an LLM. `LLMSocket` (lines 102-280) is a fake `Socket`. Output from the MUD is buffered (lines 196-217) and is sent to `llmSession.chat(...)` after one second of quiet (`expire = now + 1000`, lines 199, 208, 217; the check is at lines 113-114). The check also ensures that only one request is in flight (`thinking`, lines 113-116, 137). The reply plus CRLF is written back as input (lines 127-130). Players cannot be infected (lines 296-300). The NPC gets a `DefaultSession` (lines 340-342) with its session started on a thread (lines 343-353), ANSI telnet mode (line 358), and AUTOEXITS and BRIEF attributes (lines 362-363), and then receives a LOOK (line 364). The attributes are not reset on close (lines 263-279). The feature is gated by `CMLib.protocol().isLLMInstalled("LLMIFY")` (line 323). The session is created with `createLLMSession(categories, prompt, 128)` (line 331); what the 128 means is not verified.

**Archon_MatrixPossess** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_MatrixPossess.java`). Attaches to a player. Each tick it finds the first NPC in the player's room with no soul-mate and forces the admin to possess it with `forceInternalCommand(invoker, "Possess", M, TRUE)` (lines 111-126). The effect ends when the admin or the player is no longer in the game (lines 104-110). The effect is permanent (`Integer.MAX_VALUE/2`, line 182). `CMSecurity.isAllowed(mob, room, POSSESS)` gates it (line 161).

**Archon_Infect** (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Infect.java`). Finds the disease by name with `CMClass.findAbility` and requires it to be a `DiseaseAffect` (lines 110-115). It then invokes the disease on the target (line 129), with the message "<S-NAME> infect(s) <T-NAMESELF>." (line 124).

### 8. Archon tools: punishment and status effects

| Ability | Effect | Notes |
|---|---|---|
| Accuse (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Accuse.java`) | Warrant through `CMLib.law().getLegalBehavior(room)` and `addWarrant` (lines 145-194) | Sentence from `Law.PUNISHMENT_DESCS`, default `JAIL1`, max 10 (lines 121-132; `com/planet_ink/coffee_mud/Common/interfaces/Law.java:637-653`). A trailing `!` announces it (lines 101-104). |
| Freeze (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Freeze.java`) | Vetoes ENTER, ADVANCE, LEAVE, RECALL and FLEE for the target (lines 114-126) | Open-ended. Message: "You are frozen, and cant go anywhere." |
| Hush (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Hush.java`) | Vetoes TELL and channel messages from the target, and from monsters that follow its orders (lines 109-117) | Reply: "Your message drifts into oblivion." |
| Shame (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Shame.java`) | Applies `Shaming` with misc text `GLOBAL` for a timed duration (lines 105-135, 164-169) | Duration is converted by `time/CMProps.getTickMillis()`. Invoking again releases the target (lines 141-147). |
| Injure (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Injure.java`) | Amputates the named or next unaffected limb (lines 121-148); applies `Injury` with `+limb=20` (line 171); takes maxHP/5 from HP (lines 165-167) | Limb choice from `Amputation` effect (line 121). |
| Wrath (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Wrath.java`) | Halves HP, mana and movement if above 2 (lines 125-130); removes and drops the feet item (lines 131-137) | Message: "**<S-NAME> BLAST(S) <T-NAMESELF>**, knocking <T-HIM-HER> out of <T-HIS-HER> shoes!!" |
| MarkOOC (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_MarkOOC.java`) | Vetoes TYP_RPXPCHANGE from the target (lines 109-111) | Open-ended. Invoking again releases the target. |
| Stinkify (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Stinkify.java`) | Sets a player's hygiene to `HYGIENE_DELIMIT+1` (line 117) | Refuses mobs (line 121). |
| Peacefully (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Peacefully.java`) | Runs the command as the caster, then makes peace in the room and group if the caster was not fighting (lines 123-138) | Usage mana, range 10 (lines 102-111). |
| InstanceDisplay (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_InstanceDisplay.java`) | Reads `SELECT-DISPLAYFOR` from the `InstanceArea` ability (lines 118-127) | Reports "You believe they would be sent to: ..." (line 147). |

## Content worth mining

Paladin abilities, with the numbers a game designer could reuse:

| Ability | Kind | Key numbers (code) |
|---|---|---|
| Holy Strike | Charge on evil target | Next weapon hit doubled plus XLEVEL; +200 armor on target while charged |
| All Defence | Stance | Armor -(20 + 4*XLEVEL); disengage after one mob tick without weapon hit |
| Chaos Rage | Self buff | Damage and attack +1/(6 - 0.2*XLEVEL) of base; armor +20 + 2*XLEVEL; HP +max/5 |
| Holy Mount | Mount buff | Attack +2*L*prof, damage +L*prof, armor -2*L*prof, L = 2 + level/10 |
| Healing Hands | Heal or burn | 1 + level/4 HP and mana |
| Incite Fight | Aggression | `Aggressive` behaviour with parameter `MOBKILL` |
| Breakup Fight | Peace | Ends one fight, both sides |
| Command Horse | Order | Forced order to own mount |
| Paladin's Aura | Aura | Burst damage (level + 2*XLEVEL)/3 to evil members; blocks opposed spells, poison and disease |
| Abiding Aura | Last stand | Death prevented 10 s; then killed unless HP above 10% |
| Exploiting Aura | Damage transfer | 0.2 + 0.01*XLEVEL of damage moved to a weaker party member, doubled |
| Crushing Aura | Execution | Kills a victim at or below 10% HP unless it saves against justice |
| Goodness | Alternating burn | Every second tick, 1d(level) to evil party members and evil melee attackers |
| Fear | Periodic fear | Every 10 ticks (after 5); 1d4 outcome: cringe, cower, Spook, Nightmare |
| Courage | Fear block | Vetoes FLAG_FEARING attacks on party |
| Corruption | Fall from grace | Doubles negative alignment change |
| Disease Immunity | Protection | Vetoes disease; +50 + prof + 5*XLEVEL to disease save |
| Improved Resists | Protection | +prof/100 * level/2 to all saves |

Crafting catalogue: Holy Avenger (`Paladin_CraftHolyAvenger`, triggers CRAFTHOLY, CRAFTHOLYAVENGER, CRAFTAVENGER) and Unholy Reaver (`Paladin_CraftUnholyReaver`, triggers CRAFTUNHOLY, CRAFTUNHOLYREAVER, CRAFTREAVER). Both use the deity's weapon as a model and the zapper masks from section 5.

Archon tools, reusable as design patterns: CRecord (level-window logging), Multiwatch (same-address activity counters), Metacraft (craft-everything dev tool sorted by level), Banish (sealed cell with magic filters), LLMIFY (debounced world-to-LLM pipe), InstanceDisplay (instance preview).

## Notable design ideas

- **Charge consumed by the next hit.** Holy Strike is an effect that doubles the next weapon damage and removes itself, and it is applied and removed within the same attack call (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyStrike.java:141-142, 221-225`). This is a simple way to build "next attack" mechanics.
- **Disengage by surviving a round.** Defend ends a fight after one full tick without a weapon hit (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Defend.java:138-171`). This gives a defensive way out of combat without a flee command.
- **Last stand.** Abiding Aura prevents death for ten seconds and then requires HP above 10% (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_AbidingAura.java:95, 105`). This suits a single-player companion.
- **Aura reliability tied to skill.** Each aura makes a per-tick proficiency roll (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java:73-75`), so the aura's strength is learned rather than binary.
- **Redistributing damage inside a party.** Exploiting Aura moves damage to a weaker member and doubles it as burning (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ExploitingAura.java:126-136`). The design makes the party itself a resource.
- **Fear keeps the fight intact.** Spells cast by Fear can change combat victims, so the code saves and restores them (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Fear.java:112-116, 126-127, 133-136, 140-141`).
- **Alignment that degrades.** Alignment gates only check axes that are loaded (`com/planet_ink/coffee_mud/Abilities/Paladin/PaladinSkill.java:124-160`), so the same code works with or without the factions system.
- **Data-driven item restrictions.** The holy and unholy weapons are restricted by a zapper mask string (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java:209-216`). This avoids code for each restriction.
- **Crafting skills sorted by progression.** Metacraft orders skills by lowest qualifying level (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Metacraft.java:94-115`).
- **Sealed prison room.** Banish builds a room whose exits all loop back to itself, and filters magic by flags: a summon or a teleport that crosses the boundary "fizzles" (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_Banish.java:142-146, 183-222`).
- **Debounced LLM pipe.** LLMIFY sends the MUD's output to the model only after a second of quiet and only one request at a time (`com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java:113-141`). This is a sound pattern for any LLM-driven NPC.

Armor note: `com/planet_ink/coffee_mud/Libraries/CMGenEditor.java:3569` labels armor "lower-better", which confirms the sign convention used above (Defend lowers armor as a bonus; Chaos Rage and Holy Strike raise it as a penalty).

## Problems and cruft

Verified by reading the code unless stated otherwise.

- **Missing argument.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_InstanceDisplay.java:130` calls `L("'@x1' is not a valid instance id type.")` with no argument, so the literal "@x1" is shown to the admin.
- **Null dereference.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Multiwatch.java:332-333` uses `getPlayerAllHosts(name)` and then `M.session()` with no null check. `com/planet_ink/coffee_mud/Libraries/CMPlayers.java:758-770` returns null when nothing matches, so an unknown name throws.
- **Likely inverted counter.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Multiwatch.java:217-223` increments SYNCHROFOUND when the peer's last command is different (`!A.lastCommand.equals(hisLastCmd)`). The name suggests it should count matches. The loop also breaks after the first same-address peer with the effect (line 222), so only one peer is compared. Intent is unverified.
- **Teaching refusal does not refuse.** Several subclasses print "lacks the moral disposition" and then `return true`, so the teach goes ahead: `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java:67-76`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyStrike.java:109-118`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Defend.java:120-129`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ChaosRage.java:144-153`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_InciteFight.java:116-125`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Breakup.java:90-99`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyMount.java:103-113`. For HolyMount, the superclass check at `com/planet_ink/coffee_mud/Abilities/Paladin/PaladinSkill.java:162-178` runs first and does refuse inappropriate factions, so the local override matters only for the cases the superclass allows. The Unholy Reaver inherits the Avenger's version.
- **Unholy Reaver item restrictions are wrong.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftUnholyReaver.java:69-78` creates one `Prop_HaveZapper`, sets its text to the mask, adds it, then sets its text to `120%` and adds the same object again. The Doppleganger effect from the Holy Avenger (`com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java:217-219`) is never added, and the mask is overwritten. This holds if `addNonUninvokableEffect` keeps a reference, which I did not verify.
- **Invoker dereferenced before it is set.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CommandHorse.java:113` uses `invoker.Name()` before `super.invoke` at line 120. `StdAbility.invoke` sets the invoker only when the caster has an XMAXRANGE level (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:1966-1967`), so `invoker` may be null here. Unverified whether callers set it first.
- **Odd proficiency gain.** `com/planet_ink/coffee_mud/Abilities/Paladin/PaladinSkill.java:228-230` requires two rolls (1% and then 9%), about 0.09% per tick, and calls `helpProficiency` only then. The intent looks like a single roll; unverified.
- **Metal default resource.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java:254` sets `defResource = MATERIAL_MITHRIL` for metal; the other branches use `RESOURCE_*` codes (lines 245, 249, 258, 269). Line 262 also compares `material()` without masking.
- **Inconsistent material index test.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Metacraft.java:171` uses `material>0`, but `:285`, `:301` and `:315` use `>=0`. Index 0 is therefore filtered inconsistently. Lines 186-189 are an empty block.
- **Stat side effect.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ChaosRage.java:103-104` assigns `invoker` inside `affectPhyStats`, which runs on every stat recompute.
- **Divisor reaches zero.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ChaosRage.java:107-109` divides by `6 - 0.2*xlvl`, which is zero at XLEVEL 30. Whether XLEVEL can reach 30 was not checked.
- **Aura hurts own party.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java:85-89` and `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Goodness.java:86-94` damage evil members of the paladin's own group. That is a design choice, but it is surprising for a "party" aura.
- **Attributes not restored.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java:362-363` sets AUTOEXITS and BRIEF on the NPC, and `close()` (lines 263-279) does not reset them.
- **Message after send.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java:332-336` reports "The LLM service is not available." after the infect message has already been sent (lines 327-329).
- **Sensitive logging.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java:126-128` writes every MUD output and LLM reply to the system log.
- **Ambiguous text.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Accuse.java:189` reads "No one should never be caught @x1!". `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Freeze.java:51` and `:140` use "Freezed".
- **Redundant condition.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Injure.java:166` tests `cur > cur - percentOff`, which is true whenever `percentOff > 0`.
- **Falls through after unInvoke.** `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Defend.java:161-163` continues into the message and peace code after `unInvoke()` at line 162.
- **Global state.** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Multiwatch.java:95-96` keeps `DATA` and `IPS` as static maps holding MOB references, cleared only by STOP or AUTO.

## Takeaways for a Python SUD

- **Base class for rules, data for content.** Keep a small `Ability` base that owns the shared rules: target resolution, alignment gate, teach refusal, and a party set refreshed by movement events. Describe each ability as data (name, domain, quality, flags, cost, hooks), not one class per ability.
- **One event pipeline.** Copy the two-phase idea: an `ok_message(event)` veto pass, then an `execute(event)` pass, with auras, vetoes and effects all as listeners. In Python this is a list of callables on the world's dispatch.
- **Auras as periodic listeners.** An aura is an effect that holds its party set and a proficiency roll per tick. Borrow the roll: aura strength rises with skill.
- **Charges and stances.** Port the one-shot charge (`dmg*2 + bonus`, removed after the first hit) and the disengage-after-quiet-round rule. Both are a few lines.

```python
class Charge:
    def __init__(self, bonus):
        self.bonus, self.live = bonus, True
    def on_damage(self, hit):
        if self.live and hit.via_weapon:
            hit.amount = hit.amount * 2 + self.bonus
            self.live = False  # consumed by the first hit
```

- **Single player.** Replace "party" with companions (NPC allies). The aura, Abiding Aura, Exploiting Aura and Courage all apply cleanly to companions. The fight-between-two-players tools (Breakup, Incite, Peacefully) become "ceasefire" and "provoke" actions against NPCs.
- **Drop the admin tooling.** Multiwatch, CRecord, Record, InstanceDisplay, MatrixPossess and Infect's cross-player logic assume many players and admins. Keep Metacraft's idea as a developer tool that dumps every recipe to a file.
- **Keep the LLM idea, not the socket.** The LLMIFY pattern (debounced output, one request at a time, reply as input) fits a single-player NPC brain. The fake-socket plumbing does not.
- **Use one sign convention.** The codebase uses "lower armor is better". Pick "higher is better" for the Python game to avoid the penalty and bonus confusion seen here.
- **Restrictions as data.** Replace zapper mask strings with a small predicate, for example `lambda a: a.cls == "paladin" and a.good`.
- **Sealed rooms.** The Banish prison (exits that loop back, magic filtered by flags) is a clean way to build a holding cell or a timed exile in a text world.

## Coverage

- **Read in full:** `com/planet_ink/coffee_mud/Abilities/Archon/ArchonSkill.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/PaladinSkill.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_CRecord.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Record.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Multiwatch.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Metacraft.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_LLMIFY.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Banish.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Accuse.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Freeze.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MatrixPossess.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_MarkOOC.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_InstanceDisplay.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Infect.java`, `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Stinkify.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftHolyAvenger.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CraftUnholyReaver.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyStrike.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Defend.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ChaosRage.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HolyMount.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_HealingHands.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_InciteFight.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CommandHorse.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Aura.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_AbidingAura.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ExploitingAura.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_CrushingAura.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Fear.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Breakup.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Goodness.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Courage.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_Corruption.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_DiseaseImmunity.java`, `com/planet_ink/coffee_mud/Abilities/Paladin/Paladin_ImprovedResists.java`.
- **Read in part (the method bodies that matter):** `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Injure.java` (invoke, lines 96-178), `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Shame.java` (invoke, lines 103-174), `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Wrath.java` (invoke, lines 96-155), `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Hush.java` (okMessage and unInvoke, lines 103-133; invoke lines 136-168 by grep), `com/planet_ink/coffee_mud/Abilities/Archon/Archon_Peacefully.java` (invoke, lines 96-145; header by grep). Headers and invoke bodies were checked with grep where a full read was not needed.
- **Outside the bucket, checked for this chapter:** `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (invoke, maliciousAffect, setInvoker), `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java` (TICKS constants), `com/planet_ink/coffee_mud/Libraries/CMPlayers.java` (getPlayerAllHosts), `com/planet_ink/coffee_mud/Common/interfaces/Law.java` (punishment table), `com/planet_ink/coffee_mud/Common/DefaultPlayerStats.java` (leveledDateTime), `com/planet_ink/coffee_mud/Libraries/CMGenEditor.java` (armor label), `com/planet_ink/coffee_mud/core/CMProps.java` (tick time).
- **Not read (other buckets):** the ten Paladin files that sort after `Paladin_InciteFight`, such as `Paladin_PlanarDefiance` and `Paladin_Wickedness`, which belong to parts 2 and 3.
