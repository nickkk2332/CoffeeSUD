# Prayers, part 1: base class, undead summoning and auras

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b067` (Abilities/Prayers [part 1/8: Prayer .. Prayer_BladeBarrier]) · 38 files · 8516 lines of Java.

Path convention: Java paths are relative to `com/planet_ink/coffee_mud/` (the package root, as in the brief's examples), so `Abilities/Prayers/Prayer.java:432` means `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer.java`. Unless stated, the prayers are in `Abilities/Prayers/`.

## Summary

Prayers are the divine half of CoffeeMud's ability system. `Prayer.java` (432 lines) is a thin base class over `StdAbility` that adds a trigger word pair, an alignment gate for holy and unholy prayers, and helpers for relics and deity infusions. The 37 concrete prayers in this bucket fall into a few families: an undead-summoning ladder (ten "Animate" classes plus the Animate Dead spell) that turns corpses into undead scaled by corpse level and death-lore expertise; room and mob auras that pulse every fifth tick; combat and stat self-buffs; item prayers that store and re-cast spells through relics and scrolls; and social prayers (adoption, annulment, atonement) that need several logged-in players. For a single-player game the reusable parts are the undead ladder as data, the aura pulse pattern, and the relic and scroll attunement. The consent, marriage and two-player mechanics do not carry over.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Abilities/Prayers/Prayer.java` | Base class: trigger words, classification, alignment gate `prayerAlignmentCheck`, relic and infusion helpers, `invoke()` wrapper | 432 |
| `Abilities/Prayers/Prayer_AnimateZombie.java` | Reference copy of the undead template: `getUndeadLevel`, `makeUndeadFrom`, tier thresholds | 287 |
| `Abilities/Prayers/Prayer_AnimateDead.java` | Spell variant: the corpse becomes a `GenUndead` and its items are worn; no level gate | 254 |
| `Abilities/Prayers/Prayer_AnimateLimb.java` | Turns a severed flesh limb into a zombie limb; generates and persists a new Race per body part | 350 |
| `Abilities/Prayers/Prayer_AnimateVampire.java` | Vampire tier; re-applies `Disease_Vampirism` on death | 305 |
| `Abilities/Prayers/Prayer_AuraHeal.java`, `Prayer_AuraHarm.java` | Room auras that pulse every fifth tick; mutually exclusive | 216, 206 |
| `Abilities/Prayers/Prayer_AuraFear.java` | Fear aura: forces non-group mobs to sit or flee | 274 |
| `Abilities/Prayers/Prayer_AuraStrife.java` | Charisma debuff and forced infighting from an evil caster | 238 |
| `Abilities/Prayers/Prayer_AuraIntolerance.java` | Extra damage to other-faith mobs, applied every tick | 207 |
| `Abilities/Prayers/Prayer_AuraDivineEdict.java` | Vetoes violence; turns uppercase speech into orders | 244 |
| `Abilities/Prayers/Prayer_Absorption.java` | Copies a random ability from a target for 15 ticks | 191 |
| `Abilities/Prayers/Prayer_BladeBarrier.java` | Counter-slash on melee attacks; lowers armour | 187 |
| `Abilities/Prayers/Prayer_Avatar.java` | Avatar: HP, armour, attack and speed boost; auto-hunts | 251 |
| `Abilities/Prayers/Prayer_AttuneRelic.java`, `Prayer_AttuneScroll.java` | Re-cast the prayers stored in relics or scrolls through a temporary factory mob | 168, 186 |
| `Abilities/Prayers/Prayer_Awaken.java` | Wakes sleepers; finds sleep effects by probing `affectPhyStats` on a scratch mob | 164 |
| `Abilities/Prayers/Prayer_Adoption.java` | Two-player consent adoption; writes `PARENT:` tattoos | 322 |
| `Abilities/StdAbility.java` (outside bucket) | Mana cost, proficiency roll, `adjustedLevel`, tick durations, `invoke` pipeline (cited by range) | 2,000+ |
| `Abilities/interfaces/Ability.java` (outside bucket) | Constants: ACODE, DOMAIN, FLAG, QUALITY, COST_ALL, TICKS_* | |

## How it works

### 1. Base class and the invoke pipeline

Every prayer reports `ACODE_PRAYER` from `classificationCode()` (`Prayer.java:86-89`). The trigger words are `PRAY` and `PR` (`Prayer.java:77`). The default target is `CAN_MOBS` (`Prayer.java:66-69`), `canAffectCode()` is 0 (`Prayer.java:60-63`) and the default quality is `QUALITY_INDIFFERENT` (`Prayer.java:72-75`). Subclasses set their own domain, flags and quality.

A typical subclass `invoke()` runs in this order:

1. Resolve the target with `getAnyTarget`, `getTarget` or `mob` (for example `Prayer_AnimateDead.java:127`, `Prayer_AuraStrife.java:211`).
2. Reject bad targets with `mob.tell(...)` and `return false`, before any cost is charged.
3. Call `super.invoke(...)`. That reaches `Prayer.invoke` (`Prayer.java:422-430`), which calls `StdAbility.invoke` and then `prayerAlignmentCheck`.
4. `StdAbility.invoke` (`StdAbility.java:1964-2086`) checks that the caster is awake and mobile (`:1974`), the bound state (`:1991`), the cast timer and compounding (`:1978-2046`) and components (`:2067`). It then deducts mana, movement and hit points (`:2074-2076`) and sends `MSG_PREINVOKE` (`:2078`).
5. `proficiencyCheck(mob, 0, auto)` decides success (`StdAbility.java:1441-1486`).
6. On success it builds a `CMMsg` with `verbalCastCode` and the prayer's words, checks `okMessage`, calls `send`, and applies the effect with `beneficialAffect` or `maliciousAffect`. On failure it calls `beneficialWordsFizzle` or `maliciousFizzle`.

Cost is paid at step 4, before the alignment roll (`Prayer.java:427`) and before the proficiency roll. A prayer that the alignment gate rejects still costs mana, as the call order shows.

### 2. The alignment gate (`Prayer.prayerAlignmentCheck`, `Prayer.java:372-420`)

- The check runs only for manual casts (`!auto`), by player characters, for prayers that have some holy or unholy flag (`A.flags()!=0`), and only when `appropriateToMyFactions` is false (`Prayer.java:374-380`).
- Threshold: holy prayers use `hq=1000`, unholy `hq=0`, and neutral `hq=500` (`Prayer.java:381-389`). Neutral is `FLAG_HOLY|FLAG_UNHOLY` (`Abilities/interfaces/Ability.java:968`).
- `basis` is the caster's faction purity toward the matching alignment (`Prayer.java:391-400`). Neutral subtracts 10 (`:400`).
- The prayer passes when `rollPercentage() > basis` (`Prayer.java:403-404`), so a purer caster passes more often.
- Failure messages (`Prayer.java:406-416`): "The evil nature of @x1 disrupts your prayer.", "The goodness of @x1 disrupts your prayer.", "The non-good nature of @x1 disrupts your thought.", "The non-evil nature of @x1 disrupts your thought." A neutral caster of a neutral prayer gets no message, only the refusal.

### 3. Relic and infusion helpers (`Prayer.java:151-365`)

- `getRelicPrayers` finds the prayers in a `Wand` or in an item's `AbilityContainer` that is also `Dischargeable` (`Prayer.java:151-183`). Charges are read and written through `getRelicCharges` and `setRelicCharges` (`:330-365`).
- `infusePhysicalByAlignment` gives an item a deity infusion keyed to the caster's alignment. Good casters get `Prayer_InfuseHoliness`, evil casters `Prayer_InfuseUnholiness`, neutral casters `Prayer_InfuseBalance` (`Prayer.java:215-221`). On the law axis the names are `Prayer_InfuseDiscipline`, `Prayer_InfuseImpunity` and `Prayer_InfuseModeration` (`:237-243`). These classes are in other buckets.
- `infuseMobAlignment` adds a `Prop_WearZapper` mask such as `+FACTION -EVIL -NEUTRAL` to an item, which limits who can wear it (`Prayer.java:259-290`). This is the zapper-mask language from the brief.

### 4. Shared numbers: level, expertise, cost, success and duration

**Effective level.** `adjustedLevel(caster, asLevel)` (`StdAbility.java:899-920`) starts from the ability's lowest qualifying level (`Libraries/CMAbleMap.java:984`), adds a class-level margin, adds personal level adjustments, and adds the XLEVEL expertise (`StdAbility.java:920`). Expertise is read through `getXLEVELLevel` (`StdAbility.java:444-446`) and `getX1Level` (`:419-421`), both from `ExpertiseLibrary`.

**Cost.** `overrideMana()` replaces the normal cost. `Ability.COST_ALL` (`Ability.java:651`) means the whole mana pool (`StdAbility.java:1614-1615`). Values above `COST_PCT` (`Ability.java:653`) are percentages. Smaller values are flat amounts divided by a usage divider (`StdAbility.java:1617-1622`). Users: `Prayer_AuraDivineEdict.java:84-87` and `Prayer_AmplifyUnholyWeapon.java:77-80` use all mana; `Prayer_AttuneRelic.java:64-67` uses a flat 100.

**Success.** `proficiencyCheck` (`StdAbility.java:1441-1486`) starts from the ability's proficiency, adds PROF adjustments, adds 2×XLEVEL, clamps to 5..95 unless the DIS955RULE flag is set, adds XLEVEL/2 and any non-negative adjustment. The cast succeeds when `rollPercentage() < pctChance` (`:1485`). Automatic casts always succeed (`:1443-1449`).

**Duration.** These are the defaults; a caller may pass a positive tick adjustment that overrides them.

- Beneficial: `casterLevel*4 + 40` ticks (`StdAbility.java:2325`). When the caster may abuse level on another mob, `casterLevel` is capped at the target's level plus `EXPRATE` (`:2317-2324`).
- Malicious: `round(adjustedLevel × 1.1) + 25` ticks (`StdAbility.java:2178`).
- Ticks are 4000 ms by default (`core/CMProps.java:681`). A beneficial effect from a level-20 caster therefore lasts 120 ticks, which is 8 minutes at defaults (computed).
- Overrides: `Prayer_Absorption.java:182` passes 15; `Prayer_Avatar.java:242` passes the Avatar class level.
- `TICKS_FOREVER` and `TICKS_ALMOST_FOREVER` are `Ability.java:46` and `:48`.

### 5. Undead summoning (Animate family)

**Shared shape.** The target must be a `DeadBody`. Player corpses, nameless corpses and undead races are refused with "You can't animate that." (`Prayer_AnimateZombie.java:240-254`). Tier-gated variants also refuse weak corpses with "This creature is too weak to create a zombie from." (`:255-259`). Then the normal `super.invoke`, `proficiencyCheck`, a message, and `makeUndeadFrom`. The result gets `beneficialAffect(mob,newMOB,0,0)` (`:276`), the corpse is destroyed, and the room sees "<S-NAME> begin(s) to rise!" (`:277`).

**`makeUndeadFrom` (`Prayer_AnimateZombie.java:148-226`).**

- Description: the corpse's description plus one undead line (paraphrased in the catalogue).
- Mob class: `GenRideableUndead` if the corpse race can ride, else `GenUndead` (`:157`).
- Race: `CMLib.utensils().getMixedRace(bodyR.ID(), "Zombie", false)` (`:163`). The undead race is the corpse race mixed with an undead type.
- Stats: level from the computed undead level (`:160`); attack and damage from `CMLib.leveler()` (`:170-171`); armour from `getLevelMOBArmor` (`:175`); alignment EVIL (`:172`). Hit points, movement and mana vary by tier (table below).
- Behaviours: `CombatAbilities` (`:186-188`) and `Aggressive` with `CHECKLEVEL +NAMES "-<caster>"` (`:189-194`), so the caster's name is excluded from the aggression.
- Tattoos: `SYSTEM_SUMMONED` and `SUMMONED_BY:<caster>` (`:178-180`). Also `Prop_ModExperience` at 0 (`:177`) and `NO_REJUV` (`:181`), so these undead never respawn.
- Items: every item inside the corpse is taken and worn (`:199-223`).

**The undead level formula (`getUndeadLevel`, `Prayer_AnimateZombie.java:125-146`).** Let `base` be the tier's base, `corpse` the corpse level, `lore` the current death-lore (LEVEL) expertise and `appr` the appropriate-lore (X1) expertise. Let `maxLore` and `maxAppr` be the highest stages the caster can reach in this prayer.

- If both `maxAppr` and `maxLore` are above zero: `lvl = charLevel × (10+appr) / (10+maxAppr) − (base + 4 + 2·maxLore)`.
- Clamp `lvl` to at least 0, then add `base + 2·lore`.
- Clamp to `corpse`, then round.
- Derived, not stated in the code: with full expertise (`appr=maxAppr`, `lore=maxLore`) the result is `charLevel − 4`, clamped to the corpse level. Without expertise it is `base + 2·lore`.

**Tier table** (values from each file; "scaled" means `CMLib.leveler().getLevelMove`):

| Prayer | Min corpse level | Base | HP per level | Mana | Movement | Notes |
|---|---|---|---|---|---|---|
| Animate Skeleton | none | 1 | 15 | 100 | scaled | bones; `Prayer_AnimateSkeleton.java:173-176, 266` |
| Animate Zombie | 3 | 2 | 25 | 0 | 30 | the template, `Prayer_AnimateZombie.java:173-176, 255, 273` |
| Animate Ghoul | 5 | 4 | 25 | 100 | scaled | `Prayer_AnimateGhoul.java:173-176, 255, 273` |
| Animate Ghast | 7 | 6 | 25 | 100 | scaled | `Prayer_AnimateGhast.java:173-176, 255, 273` |
| Animate Shadow | 10 | 9 | 15 | 100 | scaled | `GenUndead` only; `Prayer_AnimateShadow.java:171-174, 254, 272` |
| Animate Spectre | 10 | 9 | 15 | 100 | scaled | `GenUndead` only; `Prayer_AnimateSpectre.java:171-174, 254, 272` |
| Animate Ghost | 15 | 14 | 10 | 100 | scaled | `GenUndead` only; attack adj 10, damage 4 fixed; `Prayer_AnimateGhost.java:169-175, 254, 271` |
| Animate Mummy | 20 | 19 | 30 | 100 | scaled | `Prayer_AnimateMummy.java:173-176, 256, 274` |
| Animate Vampire | 25 | 19 | 30 | 100 | scaled | disease on death, `Prayer_AnimateVampire.java:125-140, 273, 291` |
| Animate Limb | flesh limb only | 2 | 25 | 0 | 30 | per-limb race, `Prayer_AnimateLimb.java:273-276, 336` |
| Animate Dead | none | corpse + (X1+XLEVEL)/2 | 15 | 0 | 30 | `Prayer_AnimateDead.java:179, 195-198` |

Notes on the variants:

- Animate Dead (`Prayer_AnimateDead.java`) has no level gate. Its level is the corpse level plus half the sum of its two expertise values (`:179`). It names the undead "<realname> zombie" (`:176`), adds only `Aggressive` (`:199-203`), and gives a `Prop_StatTrainer` with the text "NOTEACH STR=20 INT=10 WIS=10 CON=10 DEX=3 CHA=2" (`:184-188`). The semantics of that property are in `Abilities/Properties/Prop_StatTrainer.java`, not read.
- Animate Limb (`Prayer_AnimateLimb.java`) needs a `FalseLimb` made of flesh (`:315-320`). On the first use for each body part it builds a race `Zombie<Part>` from a Zombie race template (`:157-257`), adds it with `CMClass.addRace` (`:255`) and persists it with `CMLib.database().DBCreateRace` (`:256`). Arrival and departure verbs depend on the part (`:170-206`). The natural weapon is a whip, slam, claw, kick or bite depending on the part (`:208-241`).
- Animate Vampire (`Prayer_AnimateVampire.java:125-140`) runs `Disease_Vampirism` on the undead when it dies, unless it already has the disease.

**Expiry.** `unInvoke` (`Prayer_AnimateZombie.java:90-108`) makes the summon "wander(s) off" and destroys it when the effect ends. This happens unless the undead is following someone, is dead, or the invoker has privileges in the room (`:96-100`). `tick` (`:110-123`) freezes the countdown while the undead follows a leader (`:118-120`).

### 6. Auras

**Pulse pattern.** Aura prayers keep a counter that starts at 4 and decrements on each `tick()` call. They act when it goes negative, which is every fifth call (`Prayer_AuraHeal.java:118-120`, `Prayer_AuraHarm.java:118-120`, `Prayer_AuraFear.java:117-119`). If each call is one 4-second tick, the pulse is about 20 seconds. That mapping is unverified for room effects.

**Group exclusion.** The caster's group is collected with `getGroupMembers` (`Prayer_AuraFear.java:124-141`, `Prayer_AuraHeal.java:122-127`). Group members are skipped. Heal also skips anyone whose current victim is a group member (`Prayer_AuraHeal.java:132-137`), so a heal aura does not heal enemies of the caster's group.

**Heal (`Prayer_AuraHeal.java`).** Each pulse heals each injured inhabitant for `roll(2, adjustedLevel, 4)`, which is 2d(level)+4 (`:141`; dice from `Libraries/Dice.java:398-406`). The message is "You feel a little better!" (`:150`). Casting it while a Harm aura is present removes the Harm aura and returns false before `super.invoke`, so no cost is paid (`:188-193`).

**Harm (`Prayer_AuraHarm.java`).** Each pulse hits each inhabitant outside the group for `roll(1, adjustedLevel/3, 1)`, which is 1d(level/3)+1 (`:136`). The damage is `TYP_UNDEAD` bursting, with the text "The unholy aura <DAMAGE> <T-NAME>!" (`:137`). Each target then gets a revenge attack on the invoker (`:144`). Casting Harm removes a Heal aura (`:178`).

**Fear (`Prayer_AuraFear.java`).** For each non-group mob, a percentage roll below the MIND save (`:150`) produces only a "shudder" message. Otherwise:

- Monsters that are immobile or out of combat are forced to Sit (`:158-163`). If they end up sitting, they gain `Spell_Fear` with `TICKS_ALMOST_FOREVER` (`:170-177`).
- Monsters in combat are forced to Flee (`:180-194`).
- Players in combat are forced to Flee (`:201-216`). Players out of combat are made mobile and then feared (`:217-230`).

**Strife (`Prayer_AuraStrife.java`).** This aura is a `CAN_MOBS` effect on one mob.

- `affectCharStats` lowers Charisma by `adjustedLevel(invoker)/5`, minimum 1 (`:93-99`), when the invoker is evil and the bearer is not the invoker.
- The bearer leaves the aura when it changes room from the invoker, or joins the invoker's group (`:147-153`).
- If the bearer is in combat, each tick there is a 10% chance it re-targets a random inhabitant outside the group (`:155-164`).
- If it is not in combat and the invoker is evil, there is a 15% chance per tick to pick a random target. The first pick produces "yell(s) and curse(s) at <target>" (`:167-182`). Repeating the same target triggers a real attack (`:176-177`).
- On the invoker's own tick, if the invoker is evil, it applies the strife effect to every non-group mob it may fight, forever (`:188-202`).

**Intolerance (`Prayer_AuraIntolerance.java`).** The aura needs a real deity (`:180-185`). `okMessage` multiplies the bearer's damage to a mob that has a different worship ID: ×2 if the victim worships a deity, and ×1.5 (`value + value/2`) if it does not (`:112-121`). `tick` (`:126-154`) has no pulse counter. Each call damages every inhabitant with a different or no deity: 3 for other-faith worshippers and 1 for non-worshippers (`:141-149`), then a revenge attack (`:150`). The real tick rate is unverified.

**Divine Edict (`Prayer_AuraDivineEdict.java`).** Three effects on the bearer, all in `okMessage` (`:112-171`):

- Any message with a malicious source or target major flag is vetoed. The source hears "@x1 DEMANDS NO FIGHTING!" and is made peaceful (`:121-127`).
- Uppercase speech aimed at a nearby mob below the level window (`:133`: target level < invoker level + 2×XLEVEL) and past a proficiency roll (`:134`) is parsed as an `ORDER` and run as a command, with the target's liege temporarily set to the speaker (`:128-166`).
- Players refuse DROP, SELL and GIVE orders with "The divine care not about such orders." (`:146-153`).

The room-wide peace loop in `tick` (`:173-195`) is unreachable for this prayer; see Problems.

### 7. Combat and self-buff prayers

- **Blade Barrier** (`Prayer_BladeBarrier.java`). `affectPhyStats` lowers armour by `1 + adjustedLevel/10` (`:147-151`). When a melee attacker targets the bearer's hands or move messages, a percentile roll must exceed 60 plus the attacker's DEX (`:124-128`). The counter-hit deals `1d(round(adj/5))+1` slashing damage (`:131`, `:96-99`). It fires at most once per target tick (`:126`, `:140`).
- **Anti-Undead Field** (`Prayer_AntiUndeadField.java`). It gets `3 + adjustedLevel/5` points (`:91-97`). Each undead approach or attack is refused and costs one point (`:99-171`). Undead that run out of range are pushed back with `MSG_RETREAT` (`:175-193`). When points go below zero the field fades (`:138-141`, `:194-197`).
- **Avatar** (`Prayer_Avatar.java`). `affectCharState` adds +200 hit points (`:103-108`). `affectPhyStats` lowers armour by `xlvl`, raises speed by `SpeedAdjustment × (1 + 0.10×XLEVEL)`, raises attack by `2×xlvl`, and renames the bearer "<name>, the Avatar of <deity>" (`:110-127`), with `xlvl = 2 + round(adj/1.5)` (`:117`). `tick` (`:129-203`) fights a random inhabitant if already in combat (`:139-145`). Otherwise it attacks a mob in the room, or walks toward an open exit with a mob in it (`:146-196`). It then attacks (`:198-199`). If the caster has an Avatar class level but is not currently that class, the cast is refused (`:218-226`). The duration is the Avatar class level (`:242`).
- **Benediction** (`Prayer_Benediction.java`). It grants `pts = adjustedLevel/5` (`:103`) of stat points. When a player casts on another mob, the points cannot exceed the target's `level/5 + EXPRATE` (`:104-108`). The points are split evenly across the base stats that the target's classes grant (`:109-125`, `:127-128`).
- **Behemoth** (`Prayer_Behemoth.java`). STR +5 and DEX −5 (`:107-108`). Carried weight rises by four times the base weight, capped at `Short.MAX_VALUE` (`:109-112`). Height is tripled (`:119`). Worn gear is rechecked with `confirmWearability` (`:151`).
- **Airy Form** (`Prayer_AiryForm.java`). Damage from `Item` tools is multiplied by 0.75, and at least 1 point is always removed (`:116-120`).
- **Acid Healing** (`Prayer_AcidHealing.java`). Acid damage is vetoed and heals half its value (`:113-120`).
- **Absorption** (`Prayer_Absorption.java`). It picks up to 99 random abilities from the target (`:143-163`). An ability must pass these filters: the caster must not know it (`:149`), it must not be auto-invoked (`:152`), the caster must have no qualifying level for it (`:155`, `CMAbleMap.java:1670`), its lowest qualifying level must be under 25 (`:158`), and an archon-domain test (`:161-162`, see Problems). The copy is unsavable (`:177-179`) and lasts 15 ticks (`:182`). It is removed on unInvoke (`:103-104`) or on quit (`:110-118`).

### 8. Item prayers

- **Attune Relic** (`Prayer_AttuneRelic.java:94-167`). The relic must hold prayers (`:105`) and charges (`:111-115`). Its stored prayers are copied to a temporary factory mob named after the item, and each one is cast through a "release" message (`:137-156`). Then one charge is spent (`:158-159`). Casting quality is always indifferent (`:88-91`).
- **Attune Scroll** (`Prayer_AttuneScroll.java:87-185`). The item must be a `Scroll` with divine spells (`:99-121`) and uses left (`:123-127`). Spells that match the caster's factions are copied (`:142-149`), cast through a temporary mob (`:153-164`), and one use is spent (`:176-177`).
- **Amplify Unholy Weapon** (`Prayer_AmplifyUnholyWeapon.java:109-156`). The weapon must carry a `Prayer_UnholyArmament` effect whose text equals the caster's name (`:121-131`). That binds the weapon to its maker. It gets a permanent copy with `LEVEL = adjustedLevel/4 + XLEVEL` (`:145-146`). The default level is 23 (`:88`, `:95`). `affectPhyStats` adds that level to the item's damage (`:98-107`).
- **Addicting Curse** (`Prayer_AddictingCurse.java:142-170`). Only food or drink can be cursed (`:147-151`). The item gains an IS_EVIL disposition (`:90-95`). When an owned curse item is eaten or drunk, the consumer gains the `Addictions` ability (`:97-106`, `:109-128`). The file is `Abilities/Misc/Addictions.java`. The owner check is at `:114-115`.
- **Birds Eye** (`Prayer_BirdsEye.java:70-109`). It builds a `BardMap` item (`Items/Basic/BardMap.java`) whose readable text lists the extended room IDs within radius 2 (`:82-95`). It makes the item glow (`:97`), gives it to the caster, sends a `MSG_READ` (`:98-100`) and destroys it (`:101`). The result is a one-shot radius-2 map.

### 9. Social, faction and sleep prayers

- **Adoption** (`Prayer_Adoption.java:71-322`). It needs two logged-in players (`:118-131`) and neither may be waiting for input (`:133-138`). The child must not already have a `PARENT:` tattoo (`:140-145`). Both sides confirm through `InputCallback` prompts (`:156-295`). Each confirmation re-enters `invoke` with `CHILD_CONFIRM_`/`PARENT_CONFIRM_` tokens (`:210`, `:267`). The result adds `PARENT:<name>` (`:305`), and also the liege as a second parent if the parent is married (`:307-309`). The description gets an adoption line (`:310-314`).
- **Annul** (`Prayer_Annul.java:70-111`). It ends a marriage, but only if the target is married and does not wear a wedding band (`:75-84`). It bumps the DIVORCES stat (`:95-96`), posts to a channel (`:98-100`) and clears both liege IDs (`:101-104`).
- **Atonement** (`Prayer_Atonement.java:70-114`). If the faction system exists (`:87`), it applies a faction gain of `roll(10, adjustedLevel, 10×XLEVEL)` (`:98-99`). Casting on someone outside the group also sends a hostile companion message (`:81-85`).
- **Anger** (`Prayer_Anger.java:96-152`). It works only in a room with more than three inhabitants and no combat (`:103-106`). Each calm inhabitant is set against a random other inhabitant whose level is within `EXPRATE` (`:111-136`, `:130-132`). It sends a malicious verbal message (`:137`) and sets the victim (`:141`).
- **Awaken** (`Prayer_Awaken.java:125-163`). For each sleeping mob, `returnOffensiveAffects` (`:82-109`) runs each effect's `affectPhyStats` on a scratch factory mob and checks `isSleeping` (`:97-100`). Effects whose invoker level is at most caster level + 1 + 2×XLEVEL are removed (`:92-93`, `:149-150`). The mob then stands (`:154`).

### 10. Messages shown to players (selected)

| Message | Where |
|---|---|
| "A healing aura descends over the area!" | `Prayer_AuraHeal.java:206` |
| "The unholy aura <DAMAGE> <T-NAME>!" | `Prayer_AuraHarm.java:137` |
| "@x1 DEMANDS NO FIGHTING!" | `Prayer_AuraDivineEdict.java:124` |
| "The divine care not about such orders." | `Prayer_AuraDivineEdict.java:151` |
| "The intolerant aura around <S-NAME> <DAMAGES> <T-NAMESELF>!" | `Prayer_AuraIntolerance.java:146` |
| "<S-NAME> <S-IS-ARE> repelled by <T-YOUPOSS> anti-undead field." | `Prayer_AntiUndeadField.java:143` |
| "<S-NAME> begin(s) to rise!" | `Prayer_AnimateZombie.java:277` |
| "Good, pure thoughts fill your head." | `Prayer_Atonement.java:97` |
| "Your anti-undead field fades." | `Prayer_AntiUndeadField.java:210` |
| "Your benediction fades." | `Prayer_Benediction.java:142` |

## Content worth mining

### Undead ladder (a data table in disguise)

The table in section 5 is the main content. Ten classes differ only in these values: threshold, base, HP per level, mana, movement, the undead race label, the flavour text, and whether they get `CombatAbilities` or ride-class selection. The corpse-to-undead mapping is easy to express as data.

### Prayer catalogue (the 37 concrete prayers in this bucket)

Columns: domain (from `classificationCode`), quality, flags, what the target is, and a one-line effect. "Default" means the class does not override it.

| Prayer | Name | Domain | Quality | Flags | Target | Effect |
|---|---|---|---|---|---|---|
| AnimateLimb | Animate Limb | DEATHLORE | INDIFFERENT | UNHOLY | item | flesh limb becomes a zombie limb (per-part race) |
| Adoption | Adoption | BLESSING | OK_OTHERS | NEUTRAL | two players | consent-based adoption, PARENT tattoo |
| AnimateVampire | Animate Vampire | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 25+; disease on death |
| AnimateMummy | Animate Mummy | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 20+ |
| AnimateGhast | Animate Ghast | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 7+ |
| AnimateGhoul | Animate Ghoul | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 5+ |
| AnimateZombie | Animate Zombie | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 3+; the template |
| AnimateGhost | Animate Ghost | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 15+; incorporeal, fixed attack |
| AnimateShadow | Animate Shadow | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 10+; incorporeal |
| AnimateSpectre | Animate Spectre | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse level 10+; incorporeal |
| AnimateSkeleton | Animate Skeleton | DEATHLORE | INDIFFERENT | UNHOLY | item | any corpse; bones only |
| AuraFear | Aura of Fear | COMMUNING | MALICIOUS | UNHOLY, FEARING | mob, room, item | pulsed fear: sit or flee |
| AnimateDead | Animate Dead | DEATHLORE | INDIFFERENT | UNHOLY | item | corpse becomes a zombie with its gear |
| Avatar | Avatar | COMMUNING | BENEFICIAL_SELF | UNHOLY | self | HP, armour, attack, speed; auto-hunts |
| AntiUndeadField | Anti Undead Field | HOLYPROTECTION | BENEFICIAL_SELF | HOLY | mob | points-based shield vs undead |
| AuraDivineEdict | Aura of the Divine Edict | EVANGELISM | BENEFICIAL_SELF | HOLY | mob | vetoes violence; uppercase orders |
| AuraStrife | Aura of Strife | COMMUNING | INDIFFERENT | UNHOLY | mob | CHA debuff; infighting |
| AnimalSpirit | Animal Spirit | COMMUNING | OK_SELF | default | self (auto) | label only: a random animal |
| AuraHeal | Aura of Healing | HEALING | INDIFFERENT | HOLY, HEALINGMAGIC | room | pulsed healing |
| AuraIntolerance | Aura of Intolerance | COMMUNING | BENEFICIAL_SELF | HOLY | mob | damage to other-faith mobs |
| AuraHarm | Aura of Harm | VEXING | MALICIOUS | UNHOLY | room | pulsed damage; cancels Heal |
| Absorption | Absorption | VEXING | BENEFICIAL_SELF | UNHOLY | mob | copies a random ability for 15 ticks |
| BladeBarrier | Blade Barrier | CREATION | BENEFICIAL_SELF | HOLY | mob | counter-slash; armour −1−lvl/10 |
| AttuneScroll | Attune Scroll | COMMUNING | MALICIOUS | HOLY | scroll | re-casts scroll prayers; uses |
| Benediction | Benediction | COMMUNING | BENEFICIAL_OTHERS | HOLY | mob | stat points from level/5 |
| AddictingCurse | Addicting Curse | CURSING | MALICIOUS | UNHOLY | food or drink | curse gives drinker an addiction |
| AttuneRelic | Attune Relic | BLESSING | MALICIOUS | HOLY | item | re-casts relic prayers; spends a charge |
| Awaken | Awaken | RESTORATION | OK_OTHERS | NEUTRAL | room | wakes sleepers; strips sleep effects |
| AcidHealing | Acid Healing | HEALING | BENEFICIAL_SELF | NEUTRAL, HEALINGMAGIC | mob | acid damage heals half |
| AiryForm | Airyform | HOLYPROTECTION | BENEFICIAL_SELF | NEUTRAL | mob | item damage × 0.75 |
| Behemoth | Behemoth | CORRUPTION | BENEFICIAL_SELF | UNHOLY | mob | size, STR+5, DEX−5, weight |
| AmplifyUnholyWeapon | Amplify Unholy Weapon | HOLYPROTECTION | INDIFFERENT | NOORDERING, UNHOLY | weapon | damage bonus = level |
| Anger | Anger | EVANGELISM | MALICIOUS | UNHOLY | room | calm mobs pick fights |
| Atonement | Atonement | EVANGELISM | OK_OTHERS | HOLY | mob | faction gain |
| Annul | Annul | NEUTRALIZATION | OK_OTHERS | NEUTRAL | mob | ends a marriage |
| BirdsEye | Birds Eye | COMMUNING | INDIFFERENT | NEUTRAL | none | one-shot radius-2 room map |
| BenignedPortal | Benigned Portal | COSMOLOGY | default | HOLY | (planar) | extends `Prayer_MalignedPortal` (other bucket) |

Quality constants are `Ability.java:1083-1093` (MALICIOUS 0 through BENEFICIAL_OTHERS 5). Domain numbers are in `Ability.java:893-900`.

### Animal spirits (`Prayer_AnimalSpirit.java:104-131`)

The enum holds 24 animals plus a `None` sentinel: Mouse, Rat, Cat, Lion, Dog, Wolf, Robin, Owl, Hawk, Eagle, Garden Snake, Snake, Python, Cobra, Grasshopper, Centipede, Tarantula, Monkey, Chimp, Ape, Gorilla, Cow, Bull, Buffalo. Each spirit has a display text "(Spirit of the <animal>)" (`:172-175`).

### Phrases and flavour

- Undead descriptions are one line each, paraphrased here: skeletons keep only bones and clinging scraps; zombies are decayed and clumsy; ghasts are taller, thin and greyish; ghouls are emaciated and hairless; mummies are wrapped in shrouds; vampires look almost alive with paler skin and soulless eyes; ghosts, shadows and spectres are incorporeal.
- Divine Edict's sample flavour: an ordered NPC who will obey a shouted order from the caster.

## Notable design ideas

- **Alignment as a dice gate.** A prayer's holy or unholy flag, plus the caster's faction purity, is a single percentile check (`Prayer.java:372-420`). That is a simple way to make a religion cost something without a rules engine.
- **Corpse scaling by expertise.** The undead level uses expertise on both the prayer and its lore. A caster with full expertise gets about `level − 4` (derived in section 5). Low lore gives a weak undead. This rewards investment in a skill.
- **Auras as tick-driven effects with group exclusion.** The same pulse pattern serves heal, harm, fear and edict. Group membership is checked each pulse (`Prayer_AuraHeal.java:122-137`).
- **Opposed auras cancel.** Casting Heal removes Harm and the reverse (`Prayer_AuraHeal.java:188-193`, `Prayer_AuraHarm.java:178`). The cancel costs nothing.
- **Orders by shouting.** The Divine Edict turns uppercase speech into commands that the target runs under a temporary liege (`Prayer_AuraDivineEdict.java:128-166`). This is a neat trick for NPC allies.
- **Sleep detection by probing.** `Prayer_Awaken.java:97-100` runs each effect's `affectPhyStats` on a scratch mob and asks whether it sleeps, instead of tagging effects by type.
- **Stored spells in items.** Relics and scrolls hold spell lists. Attuning copies them to a temporary mob that casts them (`Prayer_AttuneRelic.java:137-156`), and each use spends a charge.
- **Map as a read event.** Birds Eye creates a `BardMap` whose text is a list of room IDs, sends a read message, then destroys the item (`Prayer_BirdsEye.java:95-101`).

## Problems and cruft

- **Limb body mask is garbage.** `Prayer_AnimateLimb.java:249-252` appends `bodyParts + ";"` (the array object's `toString`), not `bodyParts[i]`. Every generated limb race gets a broken `BODY` stat. Read by hand; runtime effect unverified.
- **Self-adoption is not blocked.** `Prayer_Adoption.java:114-117` tells the caster "cannot be adopted by" but does not return. The code continues to the effect at `:297-316`, so a player appears able to adopt themselves. Read by hand.
- **Mana is charged twice on consent.** The first pass pays for the cast through `super.invoke` (`Prayer_Adoption.java:147`, then `Prayer.java:425` and `StdAbility.java:2074`), then returns false to prompt. Consent re-enters `invoke` (`:210`, `:267`), which pays again. Runtime effect unverified.
- **Unreachable code in Fear.** The `else` at `Prayer_AuraFear.java:195-199` for monsters is unreachable. The branch at `:160` and the `isInCombat` test at `:180` cover every case.
- **Unreachable room branch in Divine Edict.** `Prayer_AuraDivineEdict.java:176-177` returns for any non-room affect. The prayer is `CAN_MOBS` (`:60-63`) and `invoke` targets mobs (`:211-213`), so the peace loop at `:184-193` never runs. The prayer is granted only through `CharClasses/Purist.java:182`.
- **Archon-domain filter matches more domains.** `Prayer_Absorption.java:161-162` uses `CMath.bset(classificationCode, DOMAIN_ARCHON)`. `bset(num, mask)` is `(num & mask) == mask` (`core/CMath.java:734-736`), a subset test, not equality. `DOMAIN_ARCHON` is `9<<5` (`Ability.java:738`), so any domain number with bits 0 and 3 set also passes: 11 (DANCING), 13 (DECEPTIVE), 15 (BINDING), 25 (EVANGELISM), 27 (MOON_ALTERING), 29 (CRAFTING), 79 (COSMOLOGY), and others (domain names from `Ability.java:893-900`). Absorption can therefore take evangelism and cosmology prayers if the other filters pass.
- **Hit-word code is dead.** `Prayer_BladeBarrier.java:132-138` builds `hitWord` and never uses it. The `charAt(length-2)` and `charAt(length-3)` calls could throw on a very short string (unverified).
- **Avatar's direction loop overwrites its target.** `Prayer_Avatar.java:172-190` does not stop at the first mob it finds, and `attack` is overwritten for each open exit. `dir` is chosen separately (`:177-180`). `postAttack` at `:198-199` can then target a mob in an adjacent room, while the Avatar walks elsewhere. Whether `mayPhysicallyAttack` blocks cross-room targets is unverified.
- **Animal Spirit is mostly inert.** Only `displayText` and `text()` use the spirit (`:52-56`, `:206-209`). The `None` sentinel is also a valid roll (`:180-182`, index 0 of 25), so it re-rolls on each call until a non-None spirit is chosen. `valueOf` at `:198` throws on unknown text, so the null check at `:199-200` never runs. `getRaceID` (`:152-157`) is never called; the only file that mentions `AnimalSpirit` is this one.
- **Duplicate summoning code.** `getUndeadLevel` is copied verbatim in ten files in this bucket (the Animate classes plus Limb). `makeUndeadFrom` is copied with small changes in ten files (`Prayer_BoneMoon.java` and `Prayer_Haunted.java` use the same pattern in other buckets). The corpse-item loop at `Prayer_AnimateZombie.java:200-223` is repeated in each copy.
- **Mana before alignment.** The alignment gate runs after the mana is spent (`Prayer.java:425-427`, `StdAbility.java:2074`), so a disrupted prayer is still paid for.
- **Auras act on every tick.** `Prayer_AuraIntolerance.java:126-154` has no pulse counter. Its damage rate depends on how often the effect is ticked (unverified).
- **Minor.** "it become" grammar in `Prayer_AnimateGhoul.java:151`, `Prayer_AnimateGhost.java:151`, `Prayer_AnimateSpectre.java:151`. `Prayer_AuraHeal.java:167-168` can never be true, because the identical undead test at `:163` has already returned. `Prayer_AttuneScroll.java:162` sends the cast message from `mob` while `Prayer_AttuneRelic.java:144` sends it from the temporary mob.

## Takeaways for a Python SUD

- **Undead as data.** Replace the ten copied classes with one function and a tier table. The Java expression can be ported almost directly (round half up as Java does):

```python
import math
from dataclasses import dataclass

@dataclass(frozen=True)
class UndeadTier:
    key: str
    min_corpse_level: int   # 0 means no gate
    base: int               # starting point for undead_level()
    hp_per_level: int
    mana: int = 0
    scaled_move: bool = True
    behaviours: tuple = ("CombatAbilities", "Aggressive")

def undead_level(caster_level, base, corpse_level, appr, max_appr, lore, max_lore):
    lvl = 0.0
    if max_appr > 0 and max_lore > 0:
        lvl = caster_level * (10 + appr) / (10 + max_appr) - (base + 4 + 2 * max_lore)
    lvl = max(lvl, 0.0) + base + 2 * lore
    return int(math.floor(min(lvl, corpse_level) + 0.5))
```

- **Keep the pulse-counter aura.** One `Effect` class with an `on_tick`, a `pulse_every=5` field and an optional group exclusion covers heal, harm, fear and edict. Store the caster's group set once per pulse.
- **Keep the opposed-aura rule.** A mutual cancel that costs nothing is a clear, testable mechanic.
- **Keep item re-casting, drop the temp mob.** A relic or scroll holds a list of spells and a charge count. `attune(item, caster)` runs the stored spells directly, without a factory mob or a room.
- **Simplify the alignment gate.** A single-player game has no other players to judge. Use one roll against a "standing" value, or drop it. Keep the mana-before-check ordering only if it is intended.
- **Drop the multiplayer mechanics.** Adoption (two consents, tattoo), Annul (marriage and channels) and the two-player parts of Atonement are not needed. Keep Awaken's probe idea for any effect system.
- **Fix the archon-style filter.** Use equality on the domain value, not a bit subset, if a Python version ever needs "domain is archon".
- **Time.** The pulse and duration numbers (5-tick pulses, `4·level + 40` ticks) are in ticks. Whether the game is real-time or turn-based, express them as a count of world ticks, not seconds.

## Coverage

- **Read in full:** `Prayer.java`; `Prayer_AnimateZombie.java`; `Prayer_AnimateSkeleton.java`; `Prayer_AnimateDead.java`; `Prayer_AnimateVampire.java`; `Prayer_AnimateLimb.java`; `Prayer_Adoption.java`; `Prayer_AuraFear.java`; `Prayer_AuraHeal.java`; `Prayer_AuraStrife.java`; `Prayer_AuraIntolerance.java`; `Prayer_AuraDivineEdict.java`; `Prayer_AntiUndeadField.java`; `Prayer_Absorption.java`; `Prayer_Avatar.java`; `Prayer_Benediction.java`; `Prayer_BladeBarrier.java`; `Prayer_AttuneRelic.java`; `Prayer_AttuneScroll.java`; `Prayer_AddictingCurse.java`; `Prayer_Awaken.java`; `Prayer_Behemoth.java`; `Prayer_AmplifyUnholyWeapon.java`; `Prayer_Anger.java`; `Prayer_Atonement.java`; `Prayer_Annul.java`; `Prayer_BirdsEye.java`; `Prayer_BenignedPortal.java`; `Prayer_AcidHealing.java`; `Prayer_AiryForm.java`; `Prayer_AnimalSpirit.java`.
- **Skimmed (diff against the template, then key lines):** `Prayer_AnimateGhast.java`, `Prayer_AnimateGhoul.java`, `Prayer_AnimateGhost.java`, `Prayer_AnimateShadow.java`, `Prayer_AnimateSpectre.java`, `Prayer_AnimateMummy.java` (diffs against `Prayer_AnimateZombie.java`); `Prayer_AuraHarm.java` (diff against `Prayer_AuraHeal.java`, plus its tick and invoke lines).
- **Skipped:** none of the 38 files.
- **Outside the bucket, read only at the cited lines:** `Abilities/StdAbility.java`; `Abilities/interfaces/Ability.java`; `Libraries/CMAbleMap.java`; `Libraries/Dice.java`; `Libraries/MUDFight.java` (`rollToHit`, `adjustedArmor`); `core/CMath.java`; `core/CMProps.java`; `Common/interfaces/CMMsg.java`; `CharClasses/Purist.java`.
- **Named but not read:** `Prayer_MalignedPortal` (base of Benigned Portal), `Prayer_Infuse*`, `Prayer_UnholyArmament`, `Prayer_BoneMoon`, `Prayer_Haunted`, `Prop_WearZapper`, `Prop_StatTrainer`, `Prop_ModExperience`, `Disease_Vampirism`, `Addictions`, `Spell_Fear`, `GenUndead`, `GenRideableUndead`, `CombatAbilities`, `Aggressive`, `BardMap`.
- **Unverified at runtime:** aura tick rate; the double charge on Adoption consent; whether Avatar's cross-room attack is blocked; whether `owner()` in Addicting Curse excludes room items; whether `armour` is lower-is-better (inferred from `MUDFight.rollToHit` negating the defence value, `MUDFight.java:409-416`); the Blade Barrier string exception risk.
