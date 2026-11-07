# Combat and encounters

This chapter explains how fighting works in CoffeeSUD and how to build fights
that play well. The first half is the native combat engine: how an attack is
started, how the hit roll and the damage are worked out, how armor, resistances
and saving throws change them, how a fight ends in death, defeat or flight, and
which settings in `coffeemud.ini` and `world.ini` control all of it. The second
half is the CoffeeSUD encounter toolkit (features C1 to C25 of the project
audit): support and healing AI, morale, boss phases, telegraphed attacks,
spawners, wave rooms, ambushes, checkpoints, the downed state, essential NPCs,
target priority, pursuit, difficulty presets, mob ranks, death rules, threat and
more. It ends with design recipes (a guard post, an ambush, a three-phase boss,
a defense wave) and the debugging tools.

It is written for world creators (who attach behaviors and properties to mobs,
rooms and areas), scripters (who read and drive the same systems from MOBPROG or
JavaScript) and engine developers (who need to know where the code lives).

The optional combat module is not covered here. For the default-off
`combat.profiles` module (typed resistance and penetration for composed
abilities) read [16-combat-profiles.md](16-combat-profiles.md), and for
composed skills read [15-ability-composition.md](15-ability-composition.md).
Everything below describes the native engine, which those modules leave alone
unless you install them.

## In this chapter

- [Concepts and vocabulary](#concepts-and-vocabulary)
- [How native combat works](#how-native-combat-works)
  - [Starting a fight](#starting-a-fight)
  - [Rounds, ticks and actions](#rounds-ticks-and-actions)
  - [Combat systems: COMBATSYSTEM](#combat-systems-combatsystem)
  - [Range, formation and weapons](#range-formation-and-weapons)
  - [The hit roll](#the-hit-roll)
  - [Damage](#damage)
  - [Damage types and weapon classes](#damage-types-and-weapon-classes)
  - [Armor, absorption and resistances](#armor-absorption-and-resistances)
  - [Saving throws](#saving-throws)
  - [Criticals and spell damage](#criticals-and-spell-damage)
  - [Injury and bleeding](#injury-and-bleeding)
  - [Fleeing, wimpy and panic](#fleeing-wimpy-and-panic)
  - [Death, corpses and loot](#death-corpses-and-loot)
  - [Experience](#experience)
- [Player commands used in fights](#player-commands-used-in-fights)
- [Combat settings reference](#combat-settings-reference)
- [Everyday NPC combat behaviors](#everyday-npc-combat-behaviors)
- [The encounter toolkit](#the-encounter-toolkit)
  - [How to attach a behavior or property](#how-to-attach-a-behavior-or-property)
  - [C1 Support and healing AI](#c1-support-and-healing-ai)
  - [C2 Morale](#c2-morale)
  - [C3 Boss phases](#c3-boss-phases)
  - [C5 Telegraphed attacks](#c5-telegraphed-attacks)
  - [C4 Spawners](#c4-spawners)
  - [C9 Random encounters and encounter tables](#c9-random-encounters-and-encounter-tables)
  - [C20 Wave and arena rooms](#c20-wave-and-arena-rooms)
  - [C23 Ambushes](#c23-ambushes)
  - [C6 Party scaling](#c6-party-scaling)
  - [C7 Checkpoints](#c7-checkpoints)
  - [C8 Downed state and party defeat](#c8-downed-state-and-party-defeat)
  - [C17 Essential and yielding NPCs](#c17-essential-and-yielding-npcs)
  - [C16 Death rules](#c16-death-rules)
  - [C10 Target priority](#c10-target-priority)
  - [C18 Threat](#c18-threat)
  - [C11 Pursuit](#c11-pursuit)
  - [C12 Masks and life steal on damage properties](#c12-masks-and-life-steal-on-damage-properties)
  - [C13 Difficulty presets](#c13-difficulty-presets)
  - [C14 NPC formulas](#c14-npc-formulas)
  - [C15 Mob ranks](#c15-mob-ranks)
  - [C19 Story-aware respawns](#c19-story-aware-respawns)
  - [C21 Leash and encounter reset](#c21-leash-and-encounter-reset)
  - [C22 Ability rotations](#c22-ability-rotations)
  - [C25 CONSIDER for the whole fight](#c25-consider-for-the-whole-fight)
  - [C24 Invasions and raids (deferred)](#c24-invasions-and-raids-deferred)
- [Script hooks for encounters](#script-hooks-for-encounters)
- [Designing encounters](#designing-encounters)
  - [A guard post](#a-guard-post)
  - [An ambush on the road](#an-ambush-on-the-road)
  - [A three-phase boss](#a-three-phase-boss)
  - [A defense wave](#a-defense-wave)
  - [Tuning and balance](#tuning-and-balance)
- [Debugging combat](#debugging-combat)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Concepts and vocabulary

| Term | Meaning |
| --- | --- |
| Round | One tick of a mob in combat. The game tick is `TICKTIME` in `coffeemud.ini` (4000 ms by default), so a round is about four seconds of real time. |
| Action | The currency of a round. A mob gains `speed` actions per round (its `phyStats().speed()`); a melee swing costs one. |
| Victim | The one mob a combatant is currently swinging at (`getVictim()`). A fight is the web of victim links in a room. |
| Player's side | A character, or any mob in a group led by a character (followers, hired companions, recruited allies). Many CoffeeSUD systems (difficulty, downed state, death rules) treat "the player's side" differently from other NPCs. |
| Party | The group a character leads, followers included. Encounter tools count the party in the room. |
| Attack, armor | Numbers on a mob. The hit roll compares the attacker's adjusted attack against the defender's adjusted armor. For armor, lower is better (a mob with no protection has 100). |
| Damage type | The kind of harm: slashing, piercing, bashing, burning and so on. Armor and absorption properties react to it. |
| Save | A resistance chance, kept as a "save" stat on a mob (save vs fire, vs spells, vs blunt...). |
| Corpse (DeadBody) | The item a dead mob leaves behind, holding its gear. |
| Rank | CoffeeSUD's minion, normal, elite or boss preset on a mob (Prop_MobRank). |
| Threat | A per-NPC table of how much each enemy has done to deserve attention (damage, healing, taunts). |
| Encounter | A planned fight: the mobs, the room, the rules for starting, scaling, ending and resetting it. |

## How native combat works

All of this is in the combat library
[MUDFight](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/MUDFight.java)
(the `CMLib.combat()` library), driven from the mob tick in `StdMOB`.

### Starting a fight

A fight starts when one mob sends a malicious message at another. The usual
routes:

- A player types `KILL <name>` (aliases `K`, `ATTACK`, `MURDER`, `FIGHT`). With no
  name and already in a fight, `KILL` re-attacks the current victim only in
  non-default combat systems (see below); in the `DEFAULT` system it does
  nothing, because the engine swings for you.
- A spell or skill with a malicious target (a damage spell, a poison, a bash).
- An aggressive behavior (`Aggressive`, `MobileAggressive`, `VeryAggressive`,
  `WimpyAggressive`; see [everyday NPC combat behaviors](#everyday-npc-combat-behaviors))
  attacks a character it can see.
- Scripts: `MPKILL` and friends, or `MPCAST` with a harmful spell.
- Allies join in. `AUTOASSIST` makes a character
  help a group member who is attacked. Behaviors such as `MOBHelper`,
  `PlayerHelper`, `RaceHelper`, `BrotherHelper` and `CombatAssister` make NPCs join
  fights. `NoCombatAssist` stops a follower from joining.

When the attack lands in
`handleBeingAssaulted`, an unengaged target is given the attacker as its victim and
the range between them is set. If the attacker leads a group and the target had no
victim, the target may turn instead on the front-most group member (the formation
rule, see below), unless that member has `AUTOASSIST` off or is helpless.

`KILL` also has an archon form: `KILL <name> DEAD` and `KILL ALL DEAD` kill
outright for those holding the `KILLDEAD` security flag
(see [19-configuration-and-administration.md](19-configuration-and-administration.md)).

PvP is off in CoffeeSUD (`PLAYERKILL=NEVER`) because every character is the same
person. `PARTYFIGHT=1` in `coffeemud.ini`, or `PARTY FIGHT ON`, lets party
members fight each other (see [31-saves-and-party.md](31-saves-and-party.md)).
When it is on, the separate "PvP" formula variants (`FORMULA_PVP*`) apply.

### Rounds, ticks and actions

Each combat mob is ticked by `StdMOB.tick`. In a round it:

1. regains actions: `speed` actions (half that when sitting);
2. in the `DEFAULT` system, an extra "bonus action" while in combat (kept for
   commands such as casting);
3. if it can auto-attack (alive, awake, mobile), calls `tickCombat`, which spends
   whole actions on `postAttack` swings at its victim;
4. loses a point of movement now and then (a chance based on constitution).

A mob's attacks per round come from its `speed`. NPC speed comes from
`FORMULA_NPCSPEED` (one attack, plus one more every 30 levels by default), and
a rank or `Prop_CombatAdjuster SPEED` can raise it. Weapons and skills can also
change speed through their effects.

A swing is `postAttack(attacker, target, weapon)`: it sends a
`WEAPONATTACK` message to the room. `handleBeingAssaulted` on the target then
rolls to hit and, on a hit, posts the weapon damage.

### Combat systems: COMBATSYSTEM

`COMBATSYSTEM` in `coffeemud.ini` or `world.ini` decides how a character's actions
are spent. The value is case-insensitive; anything unrecognised means `DEFAULT`.

| Value | Behavior |
| --- | --- |
| `DEFAULT` | Standard actions are spent on standard attacks or movement automatically. One bonus action per round is granted for commands. A mob cannot bank more than `speed + 0.9` swings. |
| `QUEUE` | Queued player commands are run first; any unused actions go to automatic attacks or movement. |
| `MANUAL` | Queued commands are run; actions left over are lost. Characters must type their attacks (monsters still swing automatically). |
| `TURNBASED` | Each room's combatants take turns. Only the mob whose turn it is acts (`doTurnBasedCombat`); it spends all its actions in its turn. Monsters still swing automatically; characters act through commands. |

The `CombatAbilities` behavior notes that in a turn-based system it only works if
the NPC has more than one attack and can still make its normal weapon attack.
Test every encounter under the system your deployment uses.

### Range, formation and weapons

CoffeeMud tracks distance between combatants (`rangeToTarget`). When a fight
starts, `calculateRangeToTarget` picks it:

- if your victim is already fighting you at some range, you inherit it;
- a mount and its riders share a range;
- a follower fights at its formation position added to its leader's range;
- otherwise it starts at the maximum range of the attacking tool (weapon,
  spell or natural attack), but never more than the room's `maxRange()` (a
  small room caps it).

A weapon has a minimum and maximum range (`MODIFY ITEM` shows "Minimum/Maximum
Ranges"). An attack can only be made when the distance is between them. With
`AUTOMELEE` on (the default) a combatant out of range advances toward its
target; with `AUTOMELEE OFF` it never advances and instead spends actions
retreating when it is too close (needs at least 25 movement). So a mage or an
archer turns `AUTOMELEE` off; a swordsman leaves it on. Formation (`FORMATION`
command, see [Formation](31-saves-and-party.md#formation)) sets the starting
distance of group members. Ranged weapons (class `RANGED`, `THROWN`) use the
ranged damage formulas below. The help topic `RANGE` explains the same rules.

There is no command file named `ADVANCE` or `RETREAT` in `Commands/`: advancing
and retreating are done by `AUTOMELEE` and by abilities (charge, spring, forceback
and the like).

### The hit roll

`rollToHit(attacker, defender)`:

1. **Attack** = `FORMULA_ATTACKADJUSTMENT` applied to the attacker (base attack
   +50, a strength term, minus penalties for hunger, thirst and fatigue).
2. **Armor** = `FORMULA_ARMORADJUSTMENT` applied to the defender (base armor,
   dexterity term, penalties; sitting and sleeping remove the dexterity term).
   The formula ends by subtracting 100, so an unarmored mob has about 0 and
   armor makes it negative.
3. **Fudge** = `FORMULA_ATTACKFUDGEBONUS` (or the PvP variant, 0 by default):
   by default `±(attackerLevel − defenderLevel)²`, positive when the attacker is
   the higher level and negative when lower.
4. The hit score is `round(50 × attack / protection) + fudge`, where
   protection is the negated adjusted armor (0 counts as 1; a negative
   protection, meaning armor worse than none, is inverted).
5. The score is held to the range **5 to 95** and compared with a d100 roll
   (a hit when the roll is below the score). So there is always at least a 5%
   chance to hit and at least a 5% chance to miss.

The attack and armor formulas can treat the player's side apart (`@xP`) and
follow the difficulty (`@xD`); see [difficulty](#c13-difficulty-presets).

### Damage

On a hit, `postWeaponAttackResult` calls `adjustedDamage` and posts a `DAMAGE`
message. `adjustedDamage` runs in stages:

1. **Targeted damage formula**: `FORMULA_DAMAGEMELEETARGETED` (or
   `...RANGEDTARGETED` for ranged and thrown weapons). The inputs are the damage
   stat of the weapon (ranged and thrown weapons) or of the attacker (all
   other attacks; a wielded weapon's damage reaches the attacker's own damage
   stat through the item's effect on its wielder), strength, both levels, hunger/thirst/
   fatigue flags, whether the attacker cannot see the target, and whether the
   target is sleeping or sitting. By default it is `1?damage` (a random roll
   from 1 to damage) plus `(strength - 10 + attackerLevel - targetLevel) / 5`,
   minus 50% when the attacker cannot see the target, plus 50% when the
   target sleeps and 20% when it sits, minus 20% for each of the attacker's
   hunger, thirst and fatigue, never below 1. When the attack has no target,
   the `...STATIC` formulas are used.
2. **Weapon critical**: a percent chance from `FORMULA_CHANCEWEAPONCRIT` (plus
   the attacker's `CRIT_CHANCE_PCT_WEAPON` stat), which adds
   `FORMULA_DAMAGEWEAPONCRIT` (plus a `CRIT_DAMAGE_PCT_WEAPON` share).
3. **Fudge**: `FORMULA_DAMAGEMELEEFUDGE` or `...RANGEDFUDGE`; by default it
   shaves damage (up to the full amount at 11 or more levels) when the target
   is a higher level than the attacker.
4. The result is the message's value. Then the target's side runs the message
   through its defenses (below). Finally the difficulty preset scales it
   ([C13](#c13-difficulty-presets)) and `handleBeingDamaged` subtracts hit
   points.

The same formula names exist with a `PVP` prefix (`FORMULA_PVPDAMAGEMELEETARGETED`
and so on) and are used only when both sides are characters.

`SHOWDAMAGE` in the ini decides whether numbers are shown: `NO` (words only,
the default), `YES`, `SOURCE` (the attacker sees numbers), or `TARGET`.

### Damage types and weapon classes

A weapon has a damage type, set in `MODIFY ITEM` as "Weapon Attack Type":

`NATURAL`, `SLASHING`, `PIERCING`, `BASHING`, `BURNING`, `BURSTING`,
`SHOOTING`, `FROSTING`, `GASSING`, `MELTING`, `STRIKING`, `LASERING`,
`SONICING`, `DISRUPTING`, `STULTIFYING`, `CORRUPTING`, `SCRAPING`.

and a classification ("Weapon Classification"): `AXE`, `BLUNT`, `EDGED`,
`FLAILED`, `HAMMER`, `KARATE`, `POLEARM`, `RANGED`, `SWORD`, `DAGGER`, `STAFF`,
`THROWN`. Properties such as `Prop_AbsorbDamage` and `Prop_AddDamage` use these
words. The type also chooses the verbs in the hit and miss messages.

Spell and effect damage is categorised by the ability's own attack type,
for example fire, cold, acid, electricity, poison, gas, mind, paralysis,
water, undead (evil), disease, justice and traps. Those are the same names
used by the saves and by `Prop_AbsorbDamage` and `Prop_HaveResister`.

For items, see [22-items.md](22-items.md).

### Armor, absorption and resistances

Defense reaches a hit in four places:

1. **Armor value**: worn armor, spells and the dexterity term lower the
   defender's armor number, which changes the hit roll only. Armor does not
   reduce damage directly.
2. **Damage saves** (`checkDamageSaves`): for damage from a weapon, the
   defender's save vs the weapon's damage type (`BLUNT` for bashing, `PIERCE`
   for piercing, `SLASH` for slashing; other types skip this step). For damage
   from a spell, chant, prayer or song, the matching "saves vs spells/chants/
   prayers/songs" plus the general magic save, plus the save for the damage's
   element. The total, held to the range -100 to +100, is the chance that the
   damage is cut by that same percentage: with a 40 save there is a 40% chance
   that the hit is reduced by 40%. A negative total behaves as a weakness and
   raises damage. This step has no 5% floor.
3. **Prop_AbsorbDamage** on a mob, an item (worn), a room or an area: absorbs a
   percentage or a fixed number of points of damage matching the listed
   damage types, weapon classes, ability codes, domains, flags, specific
   ability IDs, `MAGIC`, `LEVELn` or materials. `SOURCEMASK="..."` limits it to
   attackers a zapper mask fits. Parameter reference:
   [Prop_AbsorbDamage](23-properties-reference.md#prop_absorbdamage).
4. **Prop_HaveResister** on a carried item gives the owner saving-throw style
   resistances and immunities (fire 40%, magic, weapons, teleport...).
   Reference: [Prop_HaveResister](23-properties-reference.md#prop_haveresister).

Other relevant properties: `Prop_AddDamage` (extra damage by type on a mob or a
worn weapon; `TARGETMASK=` for a bane weapon; `LEECH=n%` heals the wielder)
[Prop_AddDamage](23-properties-reference.md#prop_adddamage);
`Prop_CombatAdjuster` (changes a mob's attack, armor, damage, hit points, mana,
moves or speed by a percentage or sets them; mostly used by the engine).

COMBATDEBUG ([below](#debugging-combat)) prints each of these steps for an
attack, including what an absorb or resistance did.

### Saving throws

Spells, prayers, songs and chants that harm or afflict a target go through
`checkSavingThrows` on the target. It applies to any non-weapon message whose
value is still 0 (not yet resolved):

1. The chance to resist starts at the target's save stat for the ability class
   (save vs spells, prayers, songs or chants) plus the save for the effect
   (paralysis, fire, cold, water, gas, mind, justice, acid, electricity, poison,
   undead, disease, traps, general...).
2. A level term is added: `sign × (targetLevel − casterLevel)²`. A target 3
   levels above the caster gains +9, 3 levels below loses 9.
3. The chance is **held to 5% to 95%**. This is the 5% floor: even a helpless
   target can resist one time in twenty, and even a hardened one fails one time
   in twenty.
4. A d100 roll under the chance means the target resists; the engine posts a
   "resists" line and the effect does not land.

A save stat of `Integer.MIN_VALUE/2` or lower is treated as "no save at all"
and skips the roll. Saves live on `CharStats` (the `STAT_SAVE_*` stats). Items
and properties add to them (`Prop_HaveResister`, `Prop_Adjuster` with
`SAVE...` terms; see [23-properties-reference.md](23-properties-reference.md)),
and rank presets add their `RESIST=` list to the mob ([C15](#c15-mob-ranks)).

### Criticals and spell damage

Weapon criticals are in the damage section above. Spell damage (an `Ability`
that posts damage, whose caster has level above 1 or owns the spell) is shaped
by `modifySpellDamage`: `FORMULA_DAMAGESPELLFUDGE` first (level-gap fudge), then
a critical chance from `FORMULA_CHANCESPELLCRIT` (plus the caster's
`CRIT_CHANCE_PCT_MAGIC`) adding `FORMULA_DAMAGESPELLCRIT` (plus the
`CRIT_DAMAGE_PCT_MAGIC` share). Intelligence feeds these formulas.

### Injury and bleeding

`INJURYSYSTEM` is a comma list of nine numbers (shipped value
`100,60,25,100,2,10,15,20,100`): chance per hit that a limb is affected, the
percentage of hit points below which limbs start to be affected, the percent of
hit points in one blow that removes a limb, the chance a limb is removed when it
is fully damaged, a damage multiplier, the minimum level that can lose limbs,
the minimum level that can bleed, the percent of hit points lost in one blow
that starts bleeding, and the chance of bleeding on losing a limb. The `Injury`
effect is placed on a mob when its hit points fall to the second percentage, and a
big blow from a bleedable weapon adds `Bleeding`. Golems are exempt from injury.
(The ini comment lists a different default than the shipped line; this chapter
follows the shipped line.)

### Fleeing, wimpy and panic

- `FLEE ([direction])` moves a character out of combat. With no direction it
  tries the room it came from, then a random open exit (up as a last resort).
  It works only while in combat (for characters and followers). It makes peace and
  costs experience as set by the `FLEE` ini key (default `EXPERIENCE`: 10 plus
  5 per level above the foe, minimum 10, no loss when every foe is asleep, held
  or bound). A blank `FLEE=` means fleeing is free. The foe may gain a share
  of the lost experience.
- `WIMPY <hit points or pct>` sets a threshold. Dropping below it in combat posts
  a panic, which makes the character flee automatically. NPCs also have a wimp
  point (`Wimpy`, `FightFlee`, `Morale` behaviors drive it; see below).
- `Morale` ([C2](#c2-morale)) is the authored version for NPC groups.

### Death, corpses and loot

When hit points reach zero, `postDeath` runs. In order:

1. A script or JavaScript `beforeDeath` hook may refuse the death
   (`cm.on('beforeDeath', fn)` returning false leaves the victim at 1 hit point;
   see [05-javascript.md](05-javascript.md)).
2. If the victim is on the player's side and the `PLAYERDEATH` list contains
   `DOWNED`, it falls down instead ([C8](#c8-downed-state-and-party-defeat)).
3. A death message is built (a `DEFEATED` line instead of `DEAD` when the death
   list knocks out) and a `DEATH` message is sent to the room and globally.
4. `handleDeath` then calls `justDie`.

`justDie` (the real work):

- finds the killer's side and the experience beneficiaries and dividers,
  re-targets anyone who was fighting the dead mob, and **awards experience**
  ([below](#experience));
- splits money among beneficiaries with `AUTOGOLD` on;
- chooses the death list: `MOBDEATH` for mobs (and possessed mobs), `PLAYERDEATH`
  for characters, each possibly overridden by a `Prop_DeathRules` in the room or
  area ([C16](#c16-death-rules));
- makes the **corpse** (`killMeDead`), unless the list contains `RECALL` or knocks
  out (`OUT`). The corpse holds the gear (subject to `ITEMLOOTPOLICY`, default
  `20% RUIN`); the killer's name, tool and the cause are recorded and added to
  the corpse's description;
- applies the death consequences (below);
- `AUTOLOOT` takes the corpse's items for the killer; `AUTOGOLD` takes the coins.

The **`PLAYERDEATH`** list (comma separated; each entry applies in order):

| Entry | Effect |
| --- | --- |
| `EXPERIENCE` | Lose 100 experience per level of the character (minimum 100). |
| a number, or a math expression | Lose that experience. In an expression `@x1` is the character's level, `@x2` the killer's level, `@x3` its rejuv ticks. In CoffeeMud math `<` means "at most": the shipped `(@x1 * 50) < 2000` loses 50 experience per level, up to 2000. |
| `UNLEVEL X` | Lose X levels. |
| `LOSESKILL` | Forget a random skill. |
| `RETAIN` | Keep equipment. |
| `ASTRAL` / `ASTRAL_RES` | Become a spirit (`Prop_AstralSpirit`); `ASTRAL_RES` can self-resurrect. |
| `OUT X` | Go safely unconscious for X ticks (an expression is allowed); no corpse is made. |
| `RECALL` | Recall to the death room, no body. |
| `PURGE X` | Inherited: the character is erased after X deaths. Destructive; avoid in CoffeeSUD. |
| `DOWNED X Y` | CoffeeSUD: fall down for X rounds; an ally's `REVIVE` or a heal of Y points brings you back. See [C8](#c8-downed-state-and-party-defeat). |

`MOBDEATH` takes `DEATH` (the default: the mob dies and returns on its rejuv) or
`OUT <expression>` (unconscious for that many ticks; `@x3` is the mob's rejuv
ticks), which is how an arena or a training area makes mobs not die.

Where a dead character wakes is `DEATH` (`START`, a room ID, `MORGUE`, `HERE`,
or the CoffeeSUD value `CHECKPOINT`), plus `DEATH_*` per race, class or mask
and `MORGUE_*` for where bodies go. See [C7](#c7-checkpoints).

After a mob dies, the mob **rejuvenates** (is recreated at its start room) after
`FORMULA_NPCREJUV` game minutes (`(2+(@x1/2))<20` by default, with `@x1` the
level), or its own rejuv setting. [C19](#c19-story-aware-respawns) can hold it
back.

### Experience

Experience for a kill is worked out from two formulas.

- `FORMULA_TOTALCOMBATXP` is the pool for a victim: `@x1` dividers, `@x2` sum of
  their levels squared, `@x3` sum of their levels, `@x4` killers, `@x5` highest
  killer level, `@x6` the victim's level. The default is
  `100 + ((25*@x1) - (@x1*((25*@x1)^.5)))`.
- `FORMULA_INDCOMBATXP` is each killer's share of that pool: `@x1` pool, `@x2`
  this killer's level squared, `@x3` all dividers' levels squared, `@x4` this
  killer's level and so on. The default is `(@x1 * (@x2 / @x3)) < 100`.

The amount is then adjusted in `postExperience`: `EXPRATE` (level difference
scaling; 6 by default, 0 for none), `XPMOD`, the difficulty's `PLAYERXP`
percentage ([C13](#c13-difficulty-presets)), and the victim's rank (`XP*x` in
[C15](#c15-mob-ranks)). Killers are those who contributed and are in the room;
group members share. Experience loss on death is in the `PLAYERDEATH` list above.

For the level and leveling side see [03-characters.md](03-characters.md).

## Player commands used in fights

Commands are looked up by their exact access words.

| Command | Use |
| --- | --- |
| `KILL` / `K` / `ATTACK` / `MURDER` / `FIGHT` `<name>` | Start a fight. |
| `FLEE ([direction])` | Leave combat; see above. |
| `WIMPY <hp or pct>` | Auto-flee threshold. |
| `AUTOASSIST` `(ON/OFF)` | Help group members who are attacked. |
| `AUTOATTACK` `(ON/OFF)` | Attack aggressors automatically. |
| `AUTOMELEE` `(ON/OFF)` | Advance on a target out of range (on) or hold range (off). |
| `AUTODRAW` `(ON/OFF)` | Draw a weapon automatically. |
| `AUTOGUARD` / `GUARD` | Guard a spot (stand watch). |
| `AUTOLOOT`, `AUTOGOLD` | Loot corpses and coins after a kill. |
| `ASSIST <name>` | Join a fight on someone's side. |
| `CONSIDER ([name]/[item]/ALL)` | Size up a foe, or the whole fight ([C25](#c25-consider-for-the-whole-fight)). `GCONSIDER` does it for the group. |
| `REVIVE ([name])` | Bring up a downed ally with a tenth of their hit points. |
| `DIFFICULTY ([name])` | Show or change the difficulty (not mid-fight). |
| `DRAW`, `FIRE`, and others | Weapon handling. |

The auto-flags are per-character options (see the player option help topics).
Every player-facing option mentioned here is documented in the shipped help; use
`HELP <command>` in game.

## Combat settings reference

All of these are keys in `CoffeeMud/coffeemud.ini` (the comments there are the
long form). Keys marked "world.ini" may also be set in `world/world.ini` for one
world (see [world-settings.md](world-settings.md) and
[19-configuration-and-administration.md](19-configuration-and-administration.md));
the others are engine-wide. A world.ini value overrides `coffeemud.ini`.

| Key | Where | Default | Meaning |
| --- | --- | --- | --- |
| `COMBATSYSTEM` | ini, world.ini | `DEFAULT` | `DEFAULT`, `QUEUE`, `MANUAL`, `TURNBASED`. See [above](#combat-systems-combatsystem). |
| `TICKTIME` | ini | `4000` | Milliseconds per game tick, hence per combat round. |
| `SHOWDAMAGE` | ini | `NO` | `YES`, `NO`, `SOURCE`, `TARGET`: whether damage numbers show. |
| `PROWESSOPTIONS` | ini | blank | What the `SCORE` prowess lines show (`ARMOR-NUMBER`, `COMBAT-ADJ`, `NONE`...). Blank means absolute and numeric armor and combat. |
| `INJURYSYSTEM` | ini, world.ini | `100,60,25,100,2,10,15,20,100` | Nine comma-separated numbers; see [Injury and bleeding](#injury-and-bleeding). |
| `PLAYERKILL` | ini | `NEVER` | Characters never kill each other, unless `PARTYFIGHT`. |
| `PARTYFIGHT` | ini, world.ini | blank | `1` (or `ON`, or a word starting `Y` or `T`) lets party members fight each other. |
| `PLAYERDEATH` | ini, world.ini | `(@x1 * 50) < 2000` | What happens to a character who dies; entries in [Death, corpses and loot](#death-corpses-and-loot). |
| `MOBDEATH` | ini, world.ini | `DEATH` | `DEATH`, or `OUT <expression>`. |
| `PARTYDEFEAT` | ini | `DEATH` | CoffeeSUD: what happens when every party member in a room is down or dead: `RESTORE`, `CHECKPOINT`, `WAIT`, `DEATH`, comma separated, first that applies ([C8](#c8-downed-state-and-party-defeat)). |
| `PARTYDEFEATGOLD` | ini | `10` | Percent of gold lost when `PARTYDEFEAT` uses `CHECKPOINT`. |
| `FLEE` | ini | `EXPERIENCE` | Cost of fleeing; the same entry grammar as `PLAYERDEATH`. Blank means free. |
| `EXPRATE` | ini, world.ini | `6` | Level-difference scaling of kill experience. 0 means none. |
| `XPMOD` | ini, world.ini | blank | Global experience modifier. |
| `LASTPLAYERLEVEL` | ini, world.ini | `91` | Top character level; it is also `@x2` in the NPC formulas. |
| `CORPSEGUARD` | ini | `ALL` | Who may loot player corpses (`ALL`, `SELFONLY`, `PKONLY`). |
| `ITEMLOOTPOLICY` | ini | `20% RUIN` | Rules for items lost when a mob dies; may be overridden per room or area by `Prop_DeathRules`. |
| `DEATH` | ini, world.ini | `START` | Room a dead character wakes in: `START`, a room ID, `MORGUE`, `HERE`, or `CHECKPOINT`. |
| `DEATH_*`, `MORGUE_*` | ini, world.ini | none | Per race, class, alignment, level (`DEATH_13`) or `DEATH_MASK` overrides of `DEATH` and `MORGUE`. |
| `DIFFICULTY` | ini, world.ini | `NORMAL` | The starting difficulty preset. |
| `DIFFICULTY_<NAME>` | ini, world.ini | the four built-ins | Define or change a preset ([C13](#c13-difficulty-presets)). |
| `MOBRANK_<NAME>` | ini, world.ini | the four built-ins | Define or change a mob rank ([C15](#c15-mob-ranks)). |
| `FORMULA_NPCHITPOINTS` | ini, world.ini | `3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))` | NPC hit points from level (`@x1`) and the ability modifier (`@x2`). |
| `FORMULA_NPCARMOR`, `_NPCATTACK`, `_NPCDAMAGE`, `_NPCSPEED`, `_NPCREJUV`, `_NPCMONEY` | ini, world.ini | see [C14](#c14-npc-formulas) | Derive NPC combat numbers from level. |
| `FORMULA_ATTACKADJUSTMENT`, `FORMULA_ARMORADJUSTMENT`, `FORMULA_ATTACKFUDGEBONUS`, `FORMULA_PVPATTACKFUDGEBONUS` | ini, world.ini | see ini | The hit roll. |
| `FORMULA_DAMAGE{MELEE,RANGED}{TARGETED,STATIC,FUDGE}`, `FORMULA_PVPDAMAGE{MELEE,RANGED}{TARGETED,FUDGE}` | ini, world.ini | see ini | Weapon damage. |
| `FORMULA_CHANCEWEAPONCRIT`, `FORMULA_DAMAGEWEAPONCRIT` (and `PVP` forms) | ini, world.ini | see ini | Weapon criticals. |
| `FORMULA_DAMAGESPELLFUDGE`, `FORMULA_CHANCESPELLCRIT`, `FORMULA_DAMAGESPELLCRIT` (and `PVP` forms) | ini, world.ini | see ini | Spell damage. |
| `FORMULA_HITPOINTRECOVER`, `_MANARECOVER`, `_MOVESRECOVER` | ini, world.ini | see ini | Resting recovery per recovery tick. |
| `FORMULA_TOTALCOMBATXP`, `FORMULA_INDCOMBATXP` | ini, world.ini | see [Experience](#experience) | Kill experience. |
| `FORMULA_*` generally | ini, world.ini | | `world.ini` accepts any `FORMULA_*` key. In the attack, armor, damage and crit formulas `@xP` is 1 for the player's side and 0 for other NPCs, and `@xD` is the difficulty's `D`. |

Variable lists (what `@x1`, `@x2`... mean) are in the comments of
`coffeemud.ini` above each formula. The ini comment for the NPC formulas
(`FORMULA_NPC*`) says they are applied when a mob is made, by `RESET MOBCOMBATSTATS`,
`Prop_Doppleganger`, summons and hirelings.

Change a value in the running game by editing `world/world.ini` (`WORLD SET
KEY=value` in builder mode, effective on the next start; see
[02-world-workflow.md](02-world-workflow.md)). `DIFFICULTY <name>` is the one
combat setting that changes in play.

## Everyday NPC combat behaviors

These inherited behaviors are what most mobs use. Each has a full entry in
[24-behaviors-and-masks.md](24-behaviors-and-masks.md); this is the quick map.

| Behavior | Job |
| --- | --- |
| `Aggressive`, `MobileAggressive`, `VeryAggressive`, `WimpyAggressive`, `ClanEnemyAggressive` | Attack characters it can see: in its room; also wandering; also in adjacent rooms; only sleepers; clan enemies. Flags: `message=`, `delay=`, `mobkill`, `nogang`, `misbehave`, `checklevel`, `BRIBE=`. CoffeeSUD adds `WARN="line|line"` with `WARNTICKS=`, `BACKOFF=true`, `NEAR=1`: a grace period instead of instant attack. |
| `CombatAbilities` and the class presets `Fighterness`, `Thiefness`, `Mageness`, `Clericness`, `Druidness`, `Bardness` | Use skills, spells, prayers and songs in combat. A preset also makes the mob that class and gives it level-appropriate skills. Use one of them alone on a mob. |
| `NiceAbilities`, `NastyAbilities`, `NeutralAbilities`, `Healer` | Use benevolent / malicious / indifferent abilities on characters in the room, in or out of combat (see [C1](#c1-support-and-healing-ai)). |
| `MOBHelper`, `PlayerHelper`, `RaceHelper`, `BrotherHelper`, `CombatAssister` | Join fights on the side of other NPCs, characters, the same race, identical or same-start-room mobs, or any mob a mask fits. `NoCombatAssist` stops a follower joining. |
| `FightFlee`, `Wimpy` | Flee during combat at random; flee characters it sees. |
| `TargetPlayer` | In combat, target the weakest member of the enemy group (older and simpler than [TargetPriority](#c10-target-priority)). |
| `MOBHunter` | Seek and attack a victim within a radius. |
| `Sentry`, `Guard`, `GoodGuardian` and relatives | Station-keeping and law behaviors; see [06-npcs-dialogue.md](06-npcs-dialogue.md) and [09-society.md](09-society.md). |

Every behavior also accepts the CoffeeSUD universal parameters `WHEN="[zapper
mask]"`, `TOD=[DAWN,DAY,DUSK,NIGHT]` and `HOURS=[from]-[to]` to switch it on only at
certain times; for the opposite case put a second copy with the opposite
condition. See [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

### CombatAbilities parameters

Source:
[CombatAbilities](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/CombatAbilities.java).

| Parameter | Meaning |
| --- | --- |
| `MINTICKS=`, `MAXTICKS=`, `CHANCE=` | Timing of its ability use. |
| `BSIZE=` | Limits the size of the rotation bucket. |
| `RANDOM`, `DEFENSIVE`, `OFFENSIVE`, `MIXEDOFFENSIVE`, `MIXEDDEFENSIVE`, `ONLYALWAYS` | The combat mode (any unambiguous prefix of the name matches). |
| `+SKILLID`, `-SKILLID` | Allow a skill the mob would not otherwise use, or forbid one it has. |
| `PROFICIENT` | (presets) all skills at 100%. |
| `NOSTATS`, `NOCOMBATSTAT` | (presets) do not rewrite the mob's stats / combat stats. |
| `SUPPORT=`, `HEALAT=`, `CUREFIRST=`, `BUFFOUTOFCOMBAT` | Support targeting, [C1](#c1-support-and-healing-ai). |
| `OPENER=`, `+SKILL(rule ...)` | Rotations, [C22](#c22-ability-rotations). |

Presets change the mob's stats to suit the class, and the help warns that adding
or removing them should be followed by a room reset and a check of the combat
stats. The mob's combat stats are what the formulas in this chapter start from.

## The encounter toolkit

The tools below were added by CoffeeSUD. Each is a behavior (on a mob, room or
area) or a property (an "effect" without a visible spell). They cooperate: the
same mob can have `Prop_MobRank`, `BossPhases`, `EncounterReset`,
`TargetPriority` and `Morale` at once.

### How to attach a behavior or property

In game (builder mode or a DEV game), edit the host with `MODIFY MOB <name>` (or
`MODIFY ROOM`, `MODIFY AREA`, `MODIFY ITEM`):

- The **Behaviors** field prompts "Enter a behavior to add/remove". Type the
  behavior's ID, then, at the next prompt, its parameter string. Type its number
  to remove it. `?` lists the behaviors.
- The **Effects** field prompts "Enter an effect to add, -ID to remove, =ID or
  its number to edit, ?P for the properties that fit". Type the property ID
  (`Prop_Essential`), then its parameter string.

See [21-mobs.md](21-mobs.md) and [20-areas-rooms-exits.md](20-areas-rooms-exits.md)
for those editors and how mobs, rooms and areas are saved. The same hosts
accept behaviors and properties from scripts (`MPBEHAVE`/`MPUNBEHAVE`, see
[27-mobprog-commands.md](27-mobprog-commands.md)) and from Java and JavaScript
(`B.setParms(...)`, `A.setMiscText(...)`).

Parameter strings use `KEY=value` words. Quote a value that contains spaces
(`MSG="Hello there"`). Flags are bare words (`TAUNTABLE`, `STAY`, `NOXP`).

### C1 Support and healing AI

Problem: stock `CombatAbilities` casts helpful skills only on itself or the mob
it follows; `Healer` casts a fixed prayer set at a random character; nothing
picked "the most hurt ally".

Source:
[CombatAbilities](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/CombatAbilities.java),
[Healer](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Healer.java),
[NiceAbilities](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/NiceAbilities.java),
[SupportPicker](../../CoffeeMud/com/planet_ink/coffee_mud/core/SupportPicker.java).
Fixture: `tools/fixtures/fixes/c1-support.js`.

**CombatAbilities and the presets** (Clericness...):

| Parameter | Meaning |
| --- | --- |
| `SUPPORT=LEADER` | The default: itself and the mob it follows. |
| `SUPPORT=SELF` | Itself only. |
| `SUPPORT=GROUP` | Its group in the room. |
| `SUPPORT=MASK:"[zapper mask]"` | Anyone in the room the mask fits, NPCs too, whom it need not follow. |
| `HEALAT=N` | Heal only those under N% of their hit points, the most hurt first, before anything else. |
| `CUREFIRST=Poison,Disease` | Cure these first, using its abilities whose names say so. |
| `BUFFOUTOFCOMBAT` | Out of a fight, buff the allies it supports that lack the effect. |

`HEALAT`, `CUREFIRST` and `BUFFOUTOFCOMBAT` also make it look every 3 ticks outside
a fight.

```text
Clericness MIXEDDEFENSIVE SUPPORT=MASK:"-RACE +Goblin" HEALAT=60
```

**Healer**: `min= max= chance=` as before, plus `SPELLS=Id,Id` (its own spells
instead of the stock set), `HEALAT=N`, `TARGET=GROUP` or `TARGET=MASK:"..."`
(whom it heals, NPCs too). With any of the new parameters it chooses whom it
heals instead of picking at random.

```text
min=1 max=2 chance=100 HEALAT=70 SPELLS=Prayer_CureSerious,Prayer_CureDisease TARGET=MASK:"-NPC"
```

**NiceAbilities**: `HEALAT=N` before the `;` makes it heal the most hurt of those
it helps who are under N%, and nothing else. The mask after `;` selects who it
helps.

```text
min=1 max=1 HEALAT=60 ; -RACE +Goblin
```

Notes: the stock `Healer` and `NiceAbilities` act when a character is in the room;
a cure goes only to a target that is hurt or diseased. Give the healer the
spells it will use (a `Clericness` mob gets them from its level). Mask syntax is
in [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

### C2 Morale

A morale check makes a mob or group lose its nerve: flee, surrender or go berserk.

Source: [Morale](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Morale.java).
Fixture: `c2-morale.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `FLEEAT=N%` | off | Breaks when its hit points are under N%. |
| `OUTNUMBERED=X` | off | Breaks when the foes here outnumber its side X to one. |
| `BREAK=` | none | Comma list of: `LEADERDEAD`, `N%DEAD` (that share of its side has fallen), `HP<N%` (its side's hit points together). |
| `LEADER="[mask]"` | the mob it follows | Which mob counts as the leader for `LEADERDEAD`. |
| `ACTION=` | `FLEE` | `FLEE`, `SURRENDER` or `BERSERK`. |
| `CHANCE=N` | `100` | Percent chance per tick, once broken, that it acts. |
| `MSG="text"` | none | What it says (start with `:` for an emote). |
| `RALLY=` | none | A room ID or `HOME`: where a fleeing mob goes. |
| `REGROUP=N` | `10` | Ticks it waits at the rally point before returning with the NPCs it finds there, and attacking those it fled from. |
| `SURRENDERAT=N%` | off | Surrenders at this hit-point share whatever else is set. |
| `SURRENDERMSG="text"` | `I yield! Mercy!` | What it says on surrender. |
| `BRAVE="[mask]"` | none | Mobs the mask fits never break. |

"Its side" means its group, its leader's, and NPCs of its race in the room that
fight. `BERSERK` gives a third more damage and a tenth less attack until the
fight ends. A surrender drops its weapon, makes peace with its foes, says the
message and runs the mob's `SURRENDER_PROG` (`$n` is the one it fought).

```text
FLEEAT=40% BREAK=LEADERDEAD ACTION=FLEE RALLY=Warren#12 REGROUP=15
SURRENDERAT=20% SURRENDERMSG="Mercy! I'll show you the loot!"
```

Combine with [C17](#c17-essential-and-yielding-npcs) when the surrendering mob
must survive: a low-hit-points morale check does not by itself stop a killing blow.

### C3 Boss phases

`BossPhases` scripts a fight as a list of phases that each fire once.

Source: [BossPhases](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/BossPhases.java).
Fixture: `c3-phases.js`.

Syntax: `TRIGGER: ACTION, ACTION; TRIGGER: ACTION; ...; RESET=mode`. Triggers
separate with semicolons and actions within a trigger with commas.

| Trigger | Fires |
| --- | --- |
| `ONENGAGE:` | When the fight starts. |
| `AT=N%:` | Once its hit points drop under N%. |
| `AT=ROUND N:` | In the fight's Nth round. |
| `AT=ADDSDEAD:` | When everything it summoned is dead. |
| `ONDEATH:` | When it dies. |

| Action | Meaning |
| --- | --- |
| `SAY text`, `EMOTE text`, `YELL text` | Speech and emotes. |
| `CAST Ability` | Cast on its foe (or itself if the ability helps). |
| `SUMMON name xN` | Bring N copies of a catalog mob (or a mob class). They follow the boss, attack its foe, are tagged `SUMMONED`, and go when the phases rearm. |
| `BEHAVE Behavior (parms)`, `UNBEHAVE Behavior` | Add or remove a behavior. |
| `AFFECT Ability "parms" (ticks)`, `REMOVE Ability` | Put an effect on itself, or take one off. |
| `IMMUNE ticks` | No damage for that many ticks. |
| `HEAL N%` | Heal that share of its hit points. |
| `MOVETO Room#ID` | Move to a room. |
| `ROOM EMOTE text` | A message to the room. |
| `ROOM AFFECT Ability (ticks)` | Put an effect on the room. |
| `CALL FUNCTION` | Run `FUNCTION_PROG FUNCTION` of its scripts. |

The boss starts in phase 1; each `AT=` that fires starts the next phase, runs
the scripts' `PHASE_PROG N` and `PHASE($i)` reads the number. `RESET=` says when
the phases rearm: `FULLHEAL` (the default: when it is whole and out of a fight),
`LEAVECOMBAT` (when the fight ends), `LEASH` (when
[EncounterReset](#c21-leash-and-encounter-reset) resets it) or `NEVER`. It is
back to phase 1 when it dies. Phases, the fight round count, summons and
immunity survive a save ("a saved game keeps the phase"); an earlier defect
that lost early-round progress was fixed (audit finding R03).

```text
ONENGAGE: SAY "Fools, you dare?"; AT=60%: SUMMON skeleton warrior x2, EMOTE raises its bony arms; AT=25%: AFFECT Prop_AbilityImmunity "WEAPONS" 3, HEAL 20%; ONDEATH: ROOM EMOTE "The phylactery cracks."; RESET=LEAVECOMBAT
```

Use real catalog names in `SUMMON` (see [21-mobs.md](21-mobs.md) for the catalog).
Combine with `Prop_MobRank RANK=BOSS`, `EncounterReset`, `TargetPriority`
and `CombatAbilities`.

### C5 Telegraphed attacks

A telegraph is a visible, cancellable wind-up: the mob announces the blow, the
party has a few ticks to react, then the blow lands. The wind-up is a named alarm
that survives save and restore.

Source:
[Prop_Telegraph](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Telegraph.java),
[Telegraph](../../CoffeeMud/com/planet_ink/coffee_mud/core/Telegraph.java).
Fixtures: `c5-telegraph.js`, `a10-responses.js`.

Three ways to use it:

1. In a script, `MPTELEGRAPH (NAME=name) TICKS "warning" COMMAND (INTERRUPT=...)
   (AVOID=...) (INTERRUPTMSG="...") (RESPOND="...") (THEN=function)`. After TICKS
   the command runs once for each foe in the room who did not avoid it (`$n` is
   each one). Reference: [MPTELEGRAPH](27-mobprog-commands.md#mptelegraph).
2. `Prop_Telegraph` on the mob, parameters
   `NAME=` (default `TELEGRAPH`), `TICKS=` (2), `WARNING="..."`,
   `INTERRUPT=STUN,DAMAGE>N%,MOVE`, `AVOID=LEAVE,HIDE,COMMAND:word`,
   `INTERRUPTMSG="..."`, `RESPOND="..."`, `THEN=function`,
   `ABILITY=Id TARGET=name`. Reference:
   [Prop_Telegraph](23-properties-reference.md#prop_telegraph).
3. A generic ability with `WINDUP` (ticks) and `WINDUPMSG` (the warning; `<T-NAME>`
   is the target): when a monster uses it, it winds up first and uses it on its
   target when the time is up. Set them in `MODIFY ABILITY` or `WINDUP=` in its
   XML (see [25-abilities-and-effects.md](25-abilities-and-effects.md)).

Rules: the warning shows at the start and each tick, and `LOOK` at the mob says it
is gathering power. A stun, sleep, hold or binding always interrupts; `INTERRUPT`
adds `DAMAGE>N%` (that share of its hit points lost meanwhile) and `MOVE` (it
leaves the room). `AVOID` lists what spares a foe: leaving the room, hiding, or
typing a command word such as `brace`. `MPALARMCANCEL name` and
`ALARM($i NAME)` work on a telegraph, and a named `MPALARM NAME=` is replaced by
a new one of the same name, is saved, and ends when the mob dies, sleeps or is
held.

```text
MPTELEGRAPH 2 "<S-NAME> rears back, flames gathering in <S-HIS-HER> throat!" MPDAMAGE $n 30 60 INTERRUPT=STUN,DAMAGE>20% AVOID=COMMAND:brace
```

#### Responses and chains

`RESPOND="word=outcome|word=outcome|..."` gives the party words to type while
the mob winds up, each with its own outcome:

| Outcome | What happens to the one who typed the word |
| --- | --- |
| `SPARE` | Not hit at all (as `AVOID=COMMAND:word`). |
| `INTERRUPT` | The wind-up stops at once: no one is hit, and no `THEN` follows. |
| any other text | A script command run on them instead of the blow (`$n` is them). |

Only a foe's first word counts (`You are already ready (dodge).`), and the room
sees it (`Pip dodges, ready for it.`). A word that is not listed is an ordinary
command. `THEN=function` names a `FUNCTION_PROG` of the same mob that runs once
the blow has landed, so a boss can chain one wind-up into the next: the
function starts the next `MPTELEGRAPH`.

```text
FUNCTION_PROG SWEEP
mptelegraph 2 "<S-NAME> sweeps its tail back!" mpdamage $n 30 50 RESPOND="dodge=SPARE|brace=mpdamage $n 5 10|parry=INTERRUPT" THEN=STOMP
~
FUNCTION_PROG STOMP
mptelegraph NAME=STOMP 1 "<S-NAME> rears up to stomp!" mpdamage $n 40 60 AVOID=LEAVE
~
```

Here a dodge takes nothing, a brace takes 5 to 10 instead of 30 to 50, and a
parry stops the sweep and the stomp after it. In turn-based time a wind-up's
ticks are turns, so the party has that many turns to answer; commands that
take no time, such as `LOOK`, do not use one.

A save in the middle of a wind-up keeps it: the ticks left, the damage taken,
who avoided it and who answered with which word are kept in the property's
text (`LEFT=`, `TAKEN=`, `AVOIDED=`, `RESPONDED=`), as are a script's named
alarms.

Test stun, displacement, death, escape and re-entry during the delay.

### C4 Spawners

`RandomMonsters` and `RandomItems` gained CoffeeSUD spawn rules, written before the
semicolon (or with no semicolon and no file).

Source:
[RandomMonsters](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/RandomMonsters.java),
[RandomItems](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/RandomItems.java),
[SpawnRules](../../CoffeeMud/com/planet_ink/coffee_mud/core/SpawnRules.java).
Fixture: `c4-spawner.js`.

| Parameter | Meaning |
| --- | --- |
| `min= max= chance= minmonsters= maxmonsters=` | The inherited rate and amount (`minitems`/`maxitems` for items). |
| `CATALOG="name=weight,name=weight"` | Pick catalog entries by weight; counted and capped as the catalog's own limits are. |
| `WHEN="[zapper mask]"` | Tested on the room a spawn would go to (`-SEASON +WINTER`, `-HOUR +20`, `-LOCATION +ROOMSTR(moonlit)`). Nothing spawns where it fails. |
| `DESPAWN=WHENFALSE` | A spawn leaves, unseen, when the mask no longer passes where it is. |
| `DESPAWN=DAWN` | Leaves at dawn or by day. |
| `DESPAWN=TICKS=N` | Leaves N of the behavior's ticks after it came. |
| `NEAR=PARTY:N` | (on an area) spawn only within N rooms of the player's side. |
| `MAXAREA=N` | (on an area) spawn nothing while the area holds N spawns of any spawner. |
| `LEVEL=PARTY`, `PARTY+N`, `PARTY-N`, `PARTY+-N` | Make each spawn the level of the party's highest, give or take, with its stats from the level formulas. |

A spawn never despawns while a character is in its room or while it fights. For
items the room rules (`WHEN`, `NEAR`, `MAXAREA`, `DESPAWN`) are for items left in
rooms.

```text
maxmonsters=6 CATALOG="grey wolf=4,dire wolf=1" WHEN="-SEASON +WINTER" DESPAWN=DAWN NEAR=PARTY:3 LEVEL=PARTY+-2
```

References: [RandomMonsters](24-behaviors-and-masks.md#randommonsters),
[RandomItems](24-behaviors-and-masks.md#randomitems).

Decide whether population is fixed, rejuvenating, generated on entry or spawned by
a quest. Mixing them duplicates monsters.

### C9 Random encounters and encounter tables

Entering a room may bring an encounter rolled from a table.

Source:
[RandomEncounters](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/RandomEncounters.java),
[Encounters](../../CoffeeMud/com/planet_ink/coffee_mud/core/Encounters.java),
[EncounterCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/EncounterCmd.java).
Fixture: `c9-encounters.js`.

**Tables** are text files, `world/encounters/NAME.enc`, one entry per line
(`#` for a comment). The shipped world has none; create them with the
`ENCOUNTER` command or by hand. Each line is `KIND "what" options`:

| Kind | Meaning |
| --- | --- |
| `MOBS "name*dice,name*dice"` | Bring mobs from the catalog (or a class name). The dice are `2d3`, `1d4+1`, `3`, `2-5`. `LEADER="name"` adds one more that the others follow. |
| `MERCHANT "name"` | The same, for a pedlar. |
| `ITEM "name*dice"` | Leave items in the room. |
| `EMOTE "text"` | Shown to the room. |
| `TRAP Trap_Id` | Set off on the one who came in. |
| `FUNC FUNCTION` | Run `FUNCTION_PROG FUNCTION` of what rolled the table (the area's or room's scripts); `$n` is the one who came in. |

| Option | Meaning |
| --- | --- |
| `WEIGHT=N` | Relative chance (default 1). |
| `MASK="[zapper]"` | On the one who came in (`-HOUR +20 +21`, `-LEVEL`, `+GROUPSIZE`...). |
| `LEVEL=LO-HI` | The party's highest level (`8-` for 8 and up, `-5` for up to 5). |
| `ONCE` | Once a game. |
| `COOLDOWN=N` | N encounters of the table before it comes again. |
| `FROM=HERE`, `HIDDEN`, `ADJACENT` | Where they start: in the room, hidden in it, or walking in from next door. |
| `AMBUSH` | The monsters strike first. |

```text
# world/encounters/darkwood.enc
MOBS "grey wolf*2d3" LEADER="dire wolf" WEIGHT=4 MASK="-HOUR +20 +21 +22" AMBUSH
MOBS "a lost traveller" ONCE
EMOTE "An owl hoots somewhere above."
```

**The `ENCOUNTER` command** (builder mode, a DEV game, or `CMDMOBS`):

```text
ENCOUNTER LIST
ENCOUNTER SHOW darkwood
ENCOUNTER ADD darkwood MOBS "grey wolf*2d3" LEADER="dire wolf" WEIGHT=4 MASK="-HOUR +20 +21 +22" AMBUSH
ENCOUNTER DEL darkwood 3
ENCOUNTER ROLL darkwood
ENCOUNTER CHECK ALL
ENCOUNTER RESET darkwood
```

`SHOW` lists a table with what `ONCE` and `COOLDOWN` hold back, `ADD` checks an
entry before it goes in, `ROLL` rolls the table where you stand, and `RESET`
lets `ONCE` and `COOLDOWN` entries come again.

**The behavior `RandomEncounters`** (on an area or a room):

| Parameter | Default | Meaning |
| --- | --- | --- |
| `TABLE=name` | none | The table file to roll. |
| `CHANCE=N%` | `10` | Chance per room entered. |
| `COOLDOWN=N` or `COOLDOWN=NDAYS` | `0` | Rooms (or, with `DAYS`/`D`, game days: `COOLDOWN=2DAYS`) that pass after one. |
| `LOCALES="PLAINS,HILLS"` | any | The kinds of room it applies to. |
| `TRIGGER="[zapper]"` | any | A mask on the one who came in. |
| `MAXACTIVE=N` | `6` | Monsters of its encounters about at once. |
| `DESPAWN=N` | `10` | Ticks they stay once the party has left them, unless fighting. |
| `BUDGET=N%` | `0` (off) | Add `MOBS` entries until the monsters' power is that share of the party's; mob ranks count ([C15](#c15-mob-ranks)). |

An encounter is rolled on the next tick for the one who leads the party in.
Scripts can roll a table too: `MPMLOAD ENCOUNTER` and the quests' `LOAD ENCOUNTER`
(see [08-quests.md](08-quests.md)). Reference:
[RandomEncounters](24-behaviors-and-masks.md#randomencounters).

### C20 Wave and arena rooms

`WaveSpawner` on a room runs consecutive waves and decides win or loss.

Source: [WaveSpawner](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/WaveSpawner.java).
Fixture: `c20-waves.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `WAVE1="..." WAVE2="..." ...` | none | The waves, in order. A wave is `name*dice` terms joined with `+` (catalog names, `catalog:` optional, or a mob class), or `encounter:TABLE` to roll an encounter table. |
| `NEXT=CLEAR` or `NEXT=TICKS=N` | `CLEAR` | The next wave comes when the last is dead, or after N ticks. |
| `START=ENTER` or `START=CALL` | `ENTER` | Start when the party walks in, or when a script runs `MPWAVE START`. |
| `LOCKEXITS=true` | `false` | Keep the party in until it is done. |
| `ONWIN=UNLOCK` or `ONWIN=FUNCTION` | `UNLOCK` | On the last wave's death, unlock, or run `FUNCTION_PROG FUNCTION` of the room's scripts (`$n` is the winner). |
| `ONLOSE=RESET` or `SPARRING(:Room#ID)` | `RESET` | On a loss. `SPARRING` puts `Prop_SparringRoom` on the room while it runs, so a defeat is not death; the room ID is where the loser wakes. |
| `REWARD="item"` | none | An item given to the winner (a catalog name). |
| `RESETAFTER=N` | `0` | Ticks after which it may start again. |

If the party leaves or falls, what is left of the wave leaves. The scripts of
the room and its NPCs (an arena master's) receive `WAVE_PROG N`, `ARENAWIN_PROG`
and `ARENALOSE_PROG`. The current wave is kept in a saved game. In a script,
`MPWAVE START`, `MPWAVE NEXT`, `MPWAVE STOP` (ends it as a loss) and
`MPWAVE RESET` (clears it to start again). Reference:
[WaveSpawner](24-behaviors-and-masks.md#wavespawner).

```text
WAVE1="catalog:goblin*3" WAVE2="goblin*2+goblin archer*2" WAVE3="encounter:goblin_boss" LOCKEXITS=true REWARD="a laurel" ONLOSE=SPARRING:Arena#2
```

### C23 Ambushes

`Prop_Ambush` on a room springs an ambush when the party walks in; `Prop_Surprised`
is the state it puts on the unlucky.

Source:
[Prop_Ambush](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Ambush.java),
[Prop_Surprised](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Surprised.java).
Fixture: `c23-ambush.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `MOBS="..."` | required | `name*dice` terms joined with `+` (`catalog:` optional) or `encounter:TABLE`. |
| `CHANCE=N` | `100` | Percent chance when the party's leader walks in and `MASK` fits. |
| `DETECT=STAT+SKILL:N` | `15` | Detection check: the party's best member rolls a d20, plus (stat - 10) / 2 for a stat, plus the skill's proficiency / 10, against N. |
| `SURPRISE=N` | `1` | Rounds the unspotting party is surprised. |
| `MSG="text"` | `Figures burst from hiding!` | Shown when the ambush springs unspotted. |
| `SPOTMSG="text"` | `You spot someone lying in wait.` | Shown when spotted. |
| `WAIT=N` | `2` | Ticks the party has after spotting before the ambushers attack. |
| `RESET=N` | `0` | Ticks before it may spring again; 0 means once. |
| `MASK="[zapper]"` | none | Whom it springs on. |

Not spotted: the ambushers strike and the party is surprised (`Prop_Surprised`
with `TICKS=`: unable to move, flee, strike or cast, but can look) for `SURPRISE`
rounds. Spotted: the party may strike first or walk away (the ambushers go) before
`WAIT` ticks are up. Scripts of the room and its NPCs get `AMBUSH_PROG SURPRISED`,
`AMBUSH_PROG SPOTTED` or `AMBUSH_PROG AVOIDED`. References:
[Prop_Ambush](23-properties-reference.md#prop_ambush),
[Prop_Surprised](23-properties-reference.md#prop_surprised).

```text
MOBS="encounter:bandits" DETECT=WIS+Thief_DetectTraps:15 MSG="Figures burst from the undergrowth!" SPOTMSG="You spot glints of steel in the bushes."
MOBS="catalog:a bandit*3" CHANCE=50 RESET=300 MASK="-LEVEL +>4"
```

### C6 Party scaling

`Prop_PartyScaler` sizes a fight to the party at the moment it starts.

Source:
[Prop_PartyScaler](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_PartyScaler.java),
[PartyScale](../../CoffeeMud/com/planet_ink/coffee_mud/core/PartyScale.java).
Fixture: `c6-scaler.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `BASE=N` | `3` | The party size it is balanced for. |
| `HP=+N%/MEMBER` | `0` | Hit points up (or down, with a negative number) per member over (or under) BASE. |
| `DAMAGE=+N%/MEMBER` | `0` | The same for damage. |
| `ATTACK=+N/MEMBER` | `0` | The same for attack. |
| `LEVEL=AVG+N` or `MAX+N` | none | Set the mob's level to the party's average or highest plus N, within `MIN` and `MAX`. |
| `MIN=N`, `MAX=N` | `0`, none | Level limits for `LEVEL=`. |
| `ADDS="name/MEMBER-ABOVE-N"` | none | One add (catalog name or class) for each member over N, following the mob. |

When the mob's fight starts it counts the party: its foes' groups in the room,
followers included, builders included (so PLAYTEST shows it as a player would see
it). The adjustments hold until the fight ends, when the adds go. On a room or an
area, each NPC there that fights the party gets it for that fight. `CONSIDER`
says that the mob scales to your party. The older `Prop_Doppleganger` also counts
as scaling. Reference: [Prop_PartyScaler](23-properties-reference.md#prop_partyscaler).

```text
BASE=3 HP=+25%/MEMBER DAMAGE=+10%/MEMBER ADDS="catalog:a skeleton guard/MEMBER-ABOVE-3"
LEVEL=AVG+2 MIN=5 MAX=20
```

### C7 Checkpoints

A checkpoint records a respawn room for the characters who touch it.

Source:
[Prop_Checkpoint](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Checkpoint.java),
[Checkpoints](../../CoffeeMud/com/planet_ink/coffee_mud/core/Checkpoints.java).
Fixture: `c7-checkpoint.js`.

Put `Prop_Checkpoint` on a room:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `NAME="text"` | none | What it is called. |
| `ON=ENTER`, `ON=REST`, `ON=COMMAND:word` | `ENTER` | Set when the party walks in, rests there (sits or sleeps), or types the word (`pray`). |
| `HEAL=N%` | `0` | Heals the party that share. |
| `RESPAWNAREA=true` | `false` | Brings the area's dead mobs back. |
| `SAVE=AUTO` or `SAVE=slot` | none | Saves the game (the autosave slot, or a named slot). |

The checkpoint is stored on each character of the party who is there, through
death, apart from the recall room. With `DEATH=CHECKPOINT` in the ini, a fallen
character wakes at their last checkpoint, or in their recall room when they have
none. Scripts use `MPSETRESPAWN [CODE] [ROOM ID]/HERE/CLEAR` and read it with
`RESPAWNROOM([CODE])`. Reference:
[Prop_Checkpoint](23-properties-reference.md#prop_checkpoint).

```text
NAME="the Old Shrine" ON=COMMAND:pray HEAL=100% RESPAWNAREA=true
ON=ENTER SAVE=AUTO
```

Recall and checkpoint are different concepts. Test the path back from a defeat so
a relocked puzzle cannot strand the player.

### C8 Downed state and party defeat

Instead of dying outright, a character can fall and lie "down" for a few rounds
while allies revive them; if the whole party falls, the party is defeated.

Source:
[Downed](../../CoffeeMud/com/planet_ink/coffee_mud/core/Downed.java),
[Prop_Downed](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Downed.java),
[Revive](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Revive.java).
Fixture: `c8-downed.js`.

**Enable it** by adding `DOWNED X Y` to `PLAYERDEATH` (globally in the ini,
or per area or room with [Prop_DeathRules](#c16-death-rules)):

```ini
PLAYERDEATH=DOWNED 3 5, (@x1 * 50) < 2000
```

`X` is the rounds the character lies down (default 3), `Y` the healing, in hit
points, that brings them back (default 1). Only the player's side (characters and
their followers) can be downed; other NPCs die as `MOBDEATH` says.

**What happens.** At zero hit points the character falls to 1 hit point, makes
peace, and gets `Prop_Downed` (`TICKS=X HEAL=Y`, not saved with the character).
While down they sit, cannot move, strike, cast, stand or flee, and cannot be
attacked. Then:

- An ally types `REVIVE ([name])`, which costs one action and brings them up with a
  tenth of their maximum hit points; or any heal of `Y` hit points or more does.
- When the foes that downed them are gone or dead and no one of the party in the
  room is still fighting, they get up at 1 hit point.
- When the rounds run out, the character dies by the rest of the `PLAYERDEATH`
  list.

**Party defeat.** When every party member in the room is down or dead, and at
least one is down, "Your party has fallen." Then, in order:

1. a `PARTYDEFEAT_PROG` in the room's or the area's scripts takes it (no further
   rules apply);
2. else `PARTYDEFEAT` from `coffeemud.ini`, the first entry that applies:
   `RESTORE` offers the last save (when the game has been saved), `CHECKPOINT`
   wakes the party together at the leader's checkpoint at 1 hit point with
   `PARTYDEFEATGOLD` percent of their gold gone, `WAIT` lets each lie there until
   their time runs out, `DEATH` kills each (the default, and the last word).

For a "defeated, not dead" jailbreak instead of reload, use `Prop_DeathRules` with
`TO=`. Test the last active character falling, companions in other rooms,
recruited characters and followers.

### C17 Essential and yielding NPCs

`Prop_Essential` protects a mob the story needs, or lets one give up.

Source:
[Prop_Essential](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Essential.java).
Fixture: `c17-essential.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `MODE=YIELD` or `KNOCKOUT` | `YIELD` | `YIELD`: under `AT` percent of its hit points it stops fighting, as do its foes. `KNOCKOUT`: a blow that would kill it leaves it at 1 hit point, out cold. |
| `AT=N%` | `10` | The yield threshold. |
| `TICKS=N` | `20` | Ticks it is spared; attacks on it are refused. A knocked-out mob wakes at the end. |
| `MSG="text"` | yield: `<S-NAME> drop(s) to one knee and yield(s)!`; knockout: `<S-NAME> collapse(s), out cold.` | Shown when it is beaten. |
| `REFUSEMSG="text"` | `<T-NAME> has yielded.` / `<T-NAME> is out cold.` | Shown to one who tries to attack it while spared. |
| `NOXP` | experience is given | The one who beat it does not gain kill experience. |

On yield, the scripts' `YIELD_PROG` runs (`$n` is the one who beat it); on
knockout, `DEFEAT_PROG`. A script can then spare it, take its key, or finish it.
The experience for the kill goes to the victor unless `NOXP`. This is an authored
alternative to mandatory death, not a universal invulnerability flag. Do not give
essential protection without an eventual path to finish the quest.
Reference: [Prop_Essential](23-properties-reference.md#prop_essential).

```text
MODE=YIELD AT=10% TICKS=20 MSG="<S-NAME> drop(s) to one knee and yield(s)!"
MODE=KNOCKOUT TICKS=10
```

### C16 Death rules

`Prop_DeathRules` changes what death means in a room or an area (the room's rule
is read before its area's).

Source:
[Prop_DeathRules](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_DeathRules.java),
[DeathRules](../../CoffeeMud/com/planet_ink/coffee_mud/core/DeathRules.java).
Fixture: `c16-deathrules.js`.

| Parameter | Meaning |
| --- | --- |
| `PLAYERDEATH="list"`, `MOBDEATH="list"`, `ITEMLOOTPOLICY="list"` | Replace the ini's value there (for example `PLAYERDEATH="OUT 3" MOBDEATH="OUT 3"` for a tutorial where losing only knocks one out). |
| `TO=Room#ID` | A defeat of the player's side is not death: `MSG` shows, the fight stops, the character's gear goes into `GEAR`, and they wake in the `TO` room, their followers with them, at `HEAL` percent of their hit points (1 if none). A jailbreak instead of a reload. |
| `GEAR="CONTAINER:Room#ID/container name"` | Where the gear goes (a container in that room). |
| `HEAL=N%` | Hit points they wake with. |
| `MSG="text"` | Default `<S-NAME> <S-IS-ARE> beaten!`. |
| `MASK="[zapper]"` | Whom the rules are for. |

```text
TO=Hideout#4 GEAR="CONTAINER:Hideout#9/the chief's strongbox" HEAL=25% MSG="<S-NAME> <S-IS-ARE> beaten and dragged off!"
PLAYERDEATH="OUT 3" MOBDEATH="OUT 3"
```

Reference: [Prop_DeathRules](23-properties-reference.md#prop_deathrules). The
sibling `Prop_SparringRoom` makes a room or area where a character who dies keeps
gear and experience and wakes at the start room ([reference](23-properties-reference.md#prop_sparringroom)).

### C10 Target priority

`TargetPriority` chooses which enemy the monster attacks.

Source: [TargetPriority](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/TargetPriority.java).
Fixture: `c10-target.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `PREFER=list` | none | In order: `HEALER` (most healing abilities), `CASTER` (most spells, prayers, chants or songs), `LOWESTHP`, `LOWESTARMOR` (worst protected), `LEADER` (the party's leader), `LASTHITME`, `STRONGEST`, `MASK:[zapper mask]` (the first it fits; last in the list; quote the list when the mask has spaces). |
| `SWITCH=N` | `30` | Percent chance each tick that it looks again. |
| `STICKY=N` | `3` | Ticks after a switch before it may switch again. |
| `TAUNTABLE` | off | One with a clear lead in its threat (half as much again as the next) comes before the list. |

Its foes are those it or they fight, whom it can see. The first preference that
names someone here wins.

```text
PREFER=CASTER,LOWESTHP SWITCH=50
PREFER=LASTHITME STICKY=4
PREFER="LOWESTHP,MASK:-CLASS +Cleric" TAUNTABLE
```

Reference: [TargetPriority](24-behaviors-and-masks.md#targetpriority).

### C18 Threat

Every NPC in a fight has a threat table: who has done the most to deserve its
attention.

Source: [Threat](../../CoffeeMud/com/planet_ink/coffee_mud/core/Threat.java),
and `CombatAbilities`. Fixture: `c18-threat.js`.

- A mob with `CombatAbilities` (or a preset) keeps its own table (`aggro`). When
  an enemy's threat exceeds one and a half times that of its current victim, and
  exceeds a tenth of its own maximum hit points, and the enemy is in combat, it
  turns on that enemy and halves the others' threat. Group members and unseen
  characters never take it.
- Any other NPC gets a light table, made when it is first hurt: twice the damage
  dealt it and three times the healing of its victim.

Scripts read and change it:

| Script element | Meaning |
| --- | --- |
| `THREAT([NPC] [CODE] > N)` (condition) and `$%THREAT(npc who)%` | A character's threat to an NPC. |
| `$%TOPTHREAT(npc)%` | The enemy here with the most threat to it (a name for `MPKILL`). |
| `$%WEAKEST(npc)%` | The enemy here with the least hit points left, in percent. |
| `$%OPPONENT(npc 'MASK=[zapper]')%` | The first enemy here the mask fits. |
| `MPTHREAT [NPC] [PC] +N / -N / =N / *X` | Raise, lower, set or multiply a character's threat: `MPTHREAT $t $n *3` is a taunt. |

References: [MPTHREAT](27-mobprog-commands.md#mpthreat) and
[28-mobprog-functions.md](28-mobprog-functions.md).

### C11 Pursuit

`Pursue` makes a mob chase one who flees it.

Source: [Pursue](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Pursue.java).
Fixture: `c11-pursue.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `CHANCE=N` | `60` | Percent chance it gives chase. |
| `MAXROOMS=N` | `3` | Rooms in all before it gives up. |
| `DELAY=N` | `1` | Ticks per room. |
| `MASK="[zapper]"` | anyone | Whom it chases. |
| `LEASH=N` | `0` (none) | Rooms from home it will go. |
| `STAY` | off | On giving up, stay where it is instead of walking home. |
| `GIVEUPMSG="text"` | `<S-NAME> give(s) up the chase.` | Shown when it gives up. |
| `REMEMBER=N` | `0` | One who fled it and comes back within N ticks is attacked on sight. |

```text
CHANCE=80 MAXROOMS=4 LEASH=8
CHANCE=50 MAXROOMS=10 MASK="-RACES +Goblin" REMEMBER=30
```

Reference: [Pursue](24-behaviors-and-masks.md#pursue).

### C12 Masks and life steal on damage properties

CoffeeSUD gave the damage properties target masks:

- `Prop_AddDamage` (`TARGETMASK="[zapper]"` gives the extra damage only to victims it
  fits, a bane weapon; `LEECH=N%` heals the wielder that share of the damage dealt,
  the hit and the extra): `50% BURNING FIRE TARGETMASK="-RACECAT +Undead"`,
  `5 LEECH=25%`.
- `Prop_AbsorbDamage` (`SOURCEMASK="[zapper]"` works only against attackers it
  fits): `40% +BURNING SOURCEMASK="-RACECAT +Dragon"`.
- `Prop_HaveResister` and `Prop_WearResister` (`SOURCEMASK=`; `MASK=` is still whom
  it protects): `fire 40% SOURCEMASK="-RACECAT +Dragon"`.

Fixture: `c12-masks.js`. References in
[23-properties-reference.md](23-properties-reference.md#prop_adddamage).

### C13 Difficulty presets

A single setting scales how hard the whole game fights, and gives formulas a
player-side flag.

Source:
[GameDifficulty](../../CoffeeMud/com/planet_ink/coffee_mud/core/GameDifficulty.java),
[Difficulty](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Difficulty.java).
Fixture: `c13-difficulty.js`.

A preset has five numbers: `NPCDAMAGE` (percent of their damage NPCs deal to the
player's side), `NPCHP` (percent of their hit points), `PLAYERDAMAGE` (percent of
its damage the player's side deals to NPCs), `PLAYERXP` (percent of the experience
characters gain) and `D` (the number the formulas see as `@xD`). The built-ins:

| Preset | NPCDAMAGE | NPCHP | PLAYERDAMAGE | PLAYERXP | D |
| --- | --- | --- | --- | --- | --- |
| `STORY` | 60 | 80 | 125 | 100 | 0.5 |
| `EASY` | 80 | 90 | 110 | 100 | 0.75 |
| `NORMAL` | 100 | 100 | 100 | 100 | 1 |
| `HARD` | 130 | 120 | 100 | 110 | 1.25 |

`DIFFICULTY=NORMAL` in `coffeemud.ini` or `world.ini` is a new game's difficulty.
`DIFFICULTY_<NAME>=NPCDAMAGE=.. NPCHP=.. PLAYERDAMAGE=.. PLAYERXP=.. D=..` defines a
preset or changes a built-in one (in `world.ini` for one world):

```ini
DIFFICULTY_NIGHTMARE=NPCDAMAGE=160 NPCHP=150 PLAYERDAMAGE=90 PLAYERXP=130 D=1.5
```

In the game, `DIFFICULTY` shows the current preset and the choices;
`DIFFICULTY <name>` changes it (a prefix of the name is enough; not in the middle of a
fight). The save keeps the choice. "Player's side" means characters and mobs in a
group led by a character. Damage scaling is applied to each damage message
between the two sides (damage inside a side is not scaled, and at least 1 point
is dealt when the percentage is above 0). NPC hit points are scaled whenever
their maximum is worked out, and changing the preset recalculates every NPC,
keeping its hit-point share.

In the attack, armor, damage and crit formulas `@xP` is 1 for the player's side
and 0 for other NPCs and `@xD` is the preset's `D`, so a formula can follow the
difficulty: `(...)*(1+(0.2*@xP*(1-@xD)))` gives the player more as `D` falls.

`PLAYTEST ON DIFFICULTY=preset` runs a test under a preset ([debugging](#debugging-combat)).

### C14 NPC formulas

When an NPC is made from its level (a new mob, `RESET MOBCOMBATSTATS`,
`Prop_Doppleganger`, summons and hirelings), its combat numbers come from these
formulas in `coffeemud.ini` (or `world.ini`). `@x1` is the level, `@x2` is
`LASTPLAYERLEVEL`, `@x3` the mob's ability (hit point modifier); in
`FORMULA_NPCATTACK` `@x4` is the attack bonus a level from the attack attribute.

| Key | Default | Gives |
| --- | --- | --- |
| `FORMULA_NPCHITPOINTS` | `3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))` | Hit points (here `@x2` is the "ability" modifier, usually 11). |
| `FORMULA_NPCARMOR` | `102-((@x1+(@x2*0.44))*(@x1/((@x2*0.44)/2.0)))` | Armor (lower is better); a half rounds down. |
| `FORMULA_NPCATTACK` | `((@x1-1)*@x4)+@x1` | Attack adjustment. |
| `FORMULA_NPCDAMAGE` | `@x1` | Damage. |
| `FORMULA_NPCSPEED` | `1+((@x1/30)-((@x1/30)%1))` | Attacks per round: one more each 30 levels. |
| `FORMULA_NPCREJUV` | `(2+(@x1/2))<20` | Game minutes before a dead mob comes back. |
| `FORMULA_NPCMONEY` | `@x5+@x6` | Money carried; `@x5` is 1d(level), `@x6` is 1d10. |

Fixture: `c14-npcformulas.js`. Difficulty, ranks and party scaling multiply on top
of these ("tune one layer at a time"), as do any `Prop_CombatAdjuster` effects.

### C15 Mob ranks

`Prop_MobRank` stamps a shared preset on a mob.

Source:
[Prop_MobRank](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_MobRank.java),
[MobRanks](../../CoffeeMud/com/planet_ink/coffee_mud/core/MobRanks.java),
[FightOdds](../../CoffeeMud/com/planet_ink/coffee_mud/core/FightOdds.java).
Fixture: `c15-mobrank.js`.

Parameter: `RANK=MINION`, `NORMAL`, `ELITE`, `BOSS` or a rank of your own.

Rank numbers are in `MOBRANK_<NAME>` keys of `coffeemud.ini` or `world.ini`, read
each time the mob's stats are worked out, so one edit retunes every mob of the rank.
Terms: `HP+n%`, `DAMAGE+n%`, `ATTACK+n`, `ARMOR-n` (lower is better), `SPEED+n`,
`XP*x` (the experience for killing it), `RESIST="fire 25% mind 25%"` (saving
throws), `POWER=x` (what an encounter budget counts it as) and `TAG=text` (shown by
`LOOK` and `CONSIDER`). The defaults:

| Rank | Numbers |
| --- | --- |
| `MINION` | `HP-50% DAMAGE-25% ATTACK-10 XP*0.5 POWER=0.5 TAG=[minion]` |
| `NORMAL` | `POWER=1` |
| `ELITE` | `HP+100% DAMAGE+25% ATTACK+10 ARMOR-10 XP*2.5 POWER=2 TAG=[elite]` |
| `BOSS` | `HP+300% DAMAGE+50% ATTACK+20 ARMOR-20 XP*5 POWER=5 TAG=[boss]` |

A world adds ranks of its own:

```ini
MOBRANK_CHAMPION=HP+150% DAMAGE+35% ATTACK+12 ARMOR-12 XP*3.5 POWER=3 TAG=[champion] RESIST="fire 20%"
```

Scripts read `RANK($i)` (NORMAL for a mob with none) or test `RANK($i == BOSS)`;
masks use `-RANK +ELITE +BOSS` (only those) and `+RANK -MINION` (not those). `MPRANK`
and `RANK(who LADDER)` are a different, character rank-ladder feature, not mob ranks.
Reference: [Prop_MobRank](23-properties-reference.md#prop_mobrank).

### C19 Story-aware respawns

`Prop_RejuvWhen` is checked before a mob or item rejuvenates.

Source:
[Prop_RejuvWhen](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_RejuvWhen.java),
[RejuvRules](../../CoffeeMud/com/planet_ink/coffee_mud/core/RejuvRules.java).
Fixture: `c19-rejuv.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `WHEN="[zapper mask]"` | none | Checked on the mob as if it were back in its start room (its hour, season, room text, `-IF`/`+IF` script conditions), or for an item, on someone in its room. |
| `ELSE=WAIT` | `WAIT` | Try again at the next rejuv. |
| `ELSE=NEVER` | | It stays gone. |
| `ELSE=REPLACE:name` | | A catalog mob or item (or a class) takes its place, for good. |

```text
WHEN="-IF !VAR(* OLDMILL == CLEARED)" ELSE="REPLACE:a settler"
WHEN="-HOUR +20 +21 +22 +23 +0 +1 +2 +3 +4" ELSE=WAIT
```

Reference: [Prop_RejuvWhen](23-properties-reference.md#prop_rejuvwhen).

### C21 Leash and encounter reset

`EncounterReset` returns a boss, and optionally its room, to a clean state once the
party has gone.

Source:
[EncounterReset](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/EncounterReset.java).
Fixture: `c21-reset.js`.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `RADIUS=N` | `1` | Rooms around it that count as "near" (0 for its own room). |
| `IDLE=N` | `5` | Ticks no one it fought has been near before it resets. |
| `HEAL=N` | `100` | Raise its hit points to this percent of maximum if they are lower (100 restores it fully); mana and moves are restored. |
| `HOME=true` | `true` | Return to its start room. |
| `DESPAWNADDS=true` | `true` | Remove adds: NPCs following it with no home, or tagged `SUMMONED`. |
| `ROOM=true` | `false` | Reset every NPC of its start room with it. On a room, every NPC there is reset together. |

A reset also removes what was cast on it to harm it, forgets its threat, rearms
`BossPhases` when that behavior's `RESET` is `LEASH` or `FULLHEAL`, and runs the
scripts' `RESET_PROG`.

```text
RADIUS=1 IDLE=5 HEAL=100
IDLE=10 ROOM=true
```

### C22 Ability rotations

`CombatAbilities` can run a script of skills instead of choosing at random.

Fixture: `c22-rotation.js`. A `+SKILL` entry may carry rules in parentheses:

| Rule | Meaning |
| --- | --- |
| `EVERY=N` | Every N rounds of the fight. |
| `FIRST=N` | Not before round N; with `EVERY`, its first round. |
| `ONCE` | Tried once a fight, whether it works or not. |
| `BELOW=N%` | Only while its own hit points are under N%. |
| `COOLDOWN=N` | N rounds between tries (a fizzle counts). |
| `WEIGHT=N` | Picked N times as often as the others (random choice). |
| `SELF` | Cast on itself. |
| `TARGET=CASTER` | On the foe here with the most spells, prayers, chants or songs. |

A skill with `EVERY`, `FIRST`, `ONCE` or `BELOW` is used only when its turn comes,
before anything else, the first due in the list's order; the rest are picked at
random as before, by weight. `OPENER=Skill` is its first round's skill. Counts
restart with each fight. Stock skills take rules too. Round counters, cooldowns,
ONCE history and threat are saved (audit finding R03 fixed).

```text
OPENER=Skill_Trip +Poison_Sting(EVERY=2) +Fighter_Kick(WEIGHT=3) +Spell_Fly(ONCE BELOW=25%)
proficient +Spell_MagicMissile(EVERY=1 COOLDOWN=2 TARGET=CASTER)
```

`STAT BEHAVIORS <mob>` shows what it tried (`ID@round` log).

### C25 CONSIDER for the whole fight

`CONSIDER` with no argument, or `CONSIDER ALL`, sizes up the whole fight: the foes
here (those fighting your party, or aggressive; otherwise everyone here not on your
side), with their ranks shown, against your party here, by power level, in one word:
trivial, an easy fight, a fair fight, a hard fight, a very hard fight, or deadly. The
foes' power is multiplied by their rank's `POWER`, and the line says when a foe
scales to your party. `CONSIDER ALL.<name>` still considers each one so named. For a
single foe, `CONSIDER <name>` gives the classic level comparison ("is a serious
threat"...) and lists skills of yours that might help.

Source:
[Consider](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Consider.java),
[FightOdds](../../CoffeeMud/com/planet_ink/coffee_mud/core/FightOdds.java).
Fixture: `c25-consider.js`. It is the quickest way to check an encounter's
difficulty: run it as a mortal under `PLAYTEST`, not as an invulnerable archon.

### C24 Invasions and raids (deferred)

Area-wide invasions and raids are deferred (listed in `LATER.md`; status
`deferred` in the feature audit). Use the existing primitives: `RandomMonsters` with
`NEAR=PARTY`, wave rooms, scripts and quests.

## Script hooks for encounters

Encounter tools talk to MOBPROG and JavaScript. The full entries are in the
reference chapters: [26-mobprog-triggers.md](26-mobprog-triggers.md),
[27-mobprog-commands.md](27-mobprog-commands.md),
[28-mobprog-functions.md](28-mobprog-functions.md) and
[05-javascript.md](05-javascript.md).

| Script element | Kind | Fired or used by | Reference |
| --- | --- | --- | --- |
| `COMBAT_PROG [START/END/FLEE/ANY]` | trigger | A monster's fight starting, ending or fleeing. | [COMBAT_PROG](26-mobprog-triggers.md#combat_prog) |
| `PHASE_PROG N` | trigger | BossPhases entering phase N ([C3](#c3-boss-phases)). | [PHASE_PROG](26-mobprog-triggers.md#phase_prog) |
| `SURRENDER_PROG` | trigger | Morale surrender; `$n` is the one it fought ([C2](#c2-morale)). | [SURRENDER_PROG](26-mobprog-triggers.md#surrender_prog) |
| `YIELD_PROG`, `DEFEAT_PROG` | triggers | Prop_Essential yield / knockout; `$n` is the victor ([C17](#c17-essential-and-yielding-npcs)). | [YIELD_PROG](26-mobprog-triggers.md#yield_prog), [DEFEAT_PROG](26-mobprog-triggers.md#defeat_prog) |
| `RESET_PROG` | trigger | EncounterReset ([C21](#c21-leash-and-encounter-reset)). | [RESET_PROG](26-mobprog-triggers.md#reset_prog) |
| `WAVE_PROG N`, `ARENAWIN_PROG`, `ARENALOSE_PROG` | triggers | WaveSpawner ([C20](#c20-wave-and-arena-rooms)), on the room's scripts and its NPCs'. | [WAVE_PROG](26-mobprog-triggers.md#wave_prog) |
| `AMBUSH_PROG SURPRISED/SPOTTED/AVOIDED` | trigger | Prop_Ambush ([C23](#c23-ambushes)). | [AMBUSH_PROG](26-mobprog-triggers.md#ambush_prog) |
| `PARTYDEFEAT_PROG` | trigger | Whole party down or dead ([C8](#c8-downed-state-and-party-defeat)); takes the defeat from `PARTYDEFEAT`. | [PARTYDEFEAT_PROG](26-mobprog-triggers.md#partydefeat_prog) |
| `FUNCTION_PROG name` | trigger | Called by BossPhases `CALL`, encounter-table `FUNC`, WaveSpawner `ONWIN=`. | [FUNCTION_PROG](26-mobprog-triggers.md#function_prog) |
| `MPTELEGRAPH`, `MPALARM NAME=`, `MPALARMCANCEL` | commands | Wind-ups ([C5](#c5-telegraphed-attacks)). | [MPTELEGRAPH](27-mobprog-commands.md#mptelegraph) |
| `MPTHREAT` | command | Raise, lower, set or multiply threat ([C18](#c18-threat)). | [MPTHREAT](27-mobprog-commands.md#mpthreat) |
| `MPWAVE START/NEXT/STOP/RESET` | command | Drive a wave room. | [MPWAVE](27-mobprog-commands.md#mpwave) |
| `MPSETRESPAWN` | command | Set or clear a respawn point ([C7](#c7-checkpoints)). | [MPSETRESPAWN](27-mobprog-commands.md#mpsetrespawn) |
| `MPMLOAD ENCOUNTER` | command | Roll an encounter table ([C9](#c9-random-encounters-and-encounter-tables)). | [MPMLOAD](27-mobprog-commands.md#mpmload) |
| `PHASE()`, `RANK()`, `THREAT()`, `TOPTHREAT()`, `WEAKEST()`, `OPPONENT()`, `RESPAWNROOM()`, `ALARM()`, `PARTYSIZE()` | functions and conditions | Read encounter state. | [28-mobprog-functions.md](28-mobprog-functions.md) |

JavaScript has `cm.on('beforeDeath', fn)` (return false to keep the victim alive at
1 hit point) and a `death` hook; see [05-javascript.md](05-javascript.md).

A small script that reacts to a boss phase and a yield:

```text
PHASE_PROG 2
  mpecho The crypt walls begin to bleed.
  mpmload skeleton warrior
~
YIELD_PROG
  say Enough! The key is yours.
  mpoload the crypt key
~
```

Command names and arguments are in [27-mobprog-commands.md](27-mobprog-commands.md).

## Designing encounters

Four patterns, each built from the tools above. Substitute your own room IDs and
catalog names; build the mobs first (see [21-mobs.md](21-mobs.md)) and add
them to the catalog if a tool refers to catalog names.

Before any of these: give the mob real fighting numbers. Set a level and let the
level formulas ([C14](#c14-npc-formulas)) fill attack, armor, damage and hit points,
give it a weapon or natural attacks, and only then add behaviors.

### A guard post

Goal: guards who warn, call for help, hold a line, chase a little and break when
their captain dies.

Mob `a gate sergeant` (level 8):

```text
Behaviors:
  Aggressive WARN="Halt! State your business.|Turn back, or I'll strike!" WARNTICKS=2 BACKOFF=true
  Fighterness OFFENSIVE
  TargetPriority PREFER=CASTER,LOWESTHP SWITCH=40
  Pursue CHANCE=70 MAXROOMS=3 LEASH=4 GIVEUPMSG="<S-NAME> return(s) to the post."
  Morale FLEEAT=25% ACTION=SURRENDER SURRENDERAT=15% SURRENDERMSG="Peace! I yield!"
Effects:
  Prop_MobRank RANK=ELITE
```

Mob `a gate guard` (level 5, three copies):

```text
Behaviors:
  MOBHelper
  Fighterness
  Pursue CHANCE=60 MAXROOMS=2 LEASH=3
  Morale BREAK=50%DEAD ACTION=FLEE RALLY=HOME REGROUP=10
Effects:
  Prop_MobRank RANK=NORMAL
```

How it plays: a visitor gets two warning lines, one per tick, before the attack.
`BACKOFF=true` spares a visitor who sits down or holds no weapon. When a guard is hit,
`MOBHelper` brings the others. A visitor who flees is chased up to three rooms (a
leash of four rooms from the post), then they walk home. When half of the guard's
side has fallen the survivors break and flee to their start room, wait ten ticks,
and come back together; the sergeant yields at 15%.

Checks: `CONSIDER ALL` before fighting; fight at the party's level; flee past the
leash; kill the sergeant first and again last; save and restore mid-fight.

### An ambush on the road

Goal: bandits that burst from the undergrowth on a forest road, unless the party
spots them.

Table `world/encounters/roadside.enc`:

```text
MOBS "a bandit*2d2" LEADER="a bandit captain" AMBUSH
```

Room property on the road room (or on several rooms with a shared
`RandomEncounters`):

```text
Prop_Ambush  MOBS="encounter:roadside" CHANCE=60 DETECT=WIS+Thief_DetectTraps:14 SURPRISE=1 WAIT=2 RESET=400 MSG="Bandits burst from the undergrowth!" SPOTMSG="Sunlight glints on steel in the bushes."
```

A reaction script on the room, to give the spotted case a payoff:

```text
AMBUSH_PROG SPOTTED
  mpecho A voice hisses: "Too late!"
~
AMBUSH_PROG AVOIDED
  mpecho The bandits melt back into the trees.
~
```

How it plays: when the party's leader enters, there is a 60% chance of the check.
The party's best member rolls d20 plus a wisdom term plus the skill's
proficiency/10 against 14. Failure: the bandits attack, the party is surprised for
one round (cannot move, flee, strike or cast). Success: the party gets
`SPOTMSG` and two ticks to strike first, or to walk away, which makes the bandits
go. `RESET=400` lets it spring again after 400 ticks.

Check: the party leader's level vs bandit numbers with `CONSIDER ALL` after the
fight starts; a stunned or sleeping party; a hidden or invisible leader; re-entry.

### A three-phase boss

Goal: a lich with an opening, a summon phase, an immune desperation phase, and a
clean reset if the party flees.

Mob `Vorsh the Lich` (level 15, catalog name used by the room's spawn):

```text
Effects:
  Prop_MobRank RANK=BOSS
  Prop_PartyScaler BASE=3 HP=+20%/MEMBER DAMAGE=+8%/MEMBER ADDS="catalog:a bone minion/MEMBER-ABOVE-3"
Behaviors:
  Mageness OFFENSIVE OPENER=Spell_Sleep +Spell_MagicMissile(EVERY=2) +Spell_Fireball(WEIGHT=2)
  TargetPriority PREFER=HEALER,CASTER,LOWESTHP SWITCH=40 STICKY=4 TAUNTABLE
  BossPhases ONENGAGE: SAY "You should have left."; AT=70%: SAY "Rise!", SUMMON a bone minion x2, ROOM EMOTE "Bones claw out of the floor."; AT=35%: SAY "Not yet!", HEAL 15%, IMMUNE 2, AFFECT Prop_AbilityImmunity "WEAPONS" 4; AT=ADDSDEAD: SAY "My servants!"; ONDEATH: ROOM EMOTE "The phylactery cracks."; RESET=LEASH
  EncounterReset RADIUS=1 IDLE=5 HEAL=100 DESPAWNADDS=true
```

The lich's scripts:

```text
PHASE_PROG 3
  mpecho Violet light flares around the lich.
~
```

How it plays: phase 1 is a caster fight that opens with `Spell_Sleep`, then casts
`Spell_MagicMissile` every second round and `Spell_Fireball` at random in between
(the preset also gives the mob level-appropriate mage spells). At 70% it says its
line and raises two minions, which follow it and attack the party's foe; they are
tagged `SUMMONED`. `PHASE_PROG 2` runs. At 35% it heals 15%, is immune to all damage for two ticks, and takes a
weapon immunity for four, so the party must use spells, then phase 3 starts and
`PHASE_PROG 3` runs. If all the summoned minions die, `AT=ADDSDEAD` fires once. If the
party flees far enough (past `RADIUS=1` for `IDLE=5` ticks), `EncounterReset` heals
the lich fully, brings it home, removes minions, and (since `RESET=LEASH`) rearms
the phases; it is back at phase 1 on death too.

Readability rules: every phase should have a visible line (SAY/EMOTE/ROOM EMOTE), one
response the player can learn (here: kill the minions or burst through the
immunity with spells), and a predictable reset policy. Add a telegraph (below) for the
big hit. Remember `BossPhases` thresholds fire once per fight; do not use an unguarded
`HITPRCNT` trigger to award a one-time reward each tick.

Add a telegraphed nova: change the 35% phase to end with `CALL NOVA`, and give the
lich a script (see [C5](#c5-telegraphed-attacks)):

```text
FUNCTION_PROG NOVA
  mptelegraph NAME=NOVA 2 "<S-NAME> gathers a sphere of black fire!" mpdamage $n 60 90 INTERRUPT=STUN,DAMAGE>15% AVOID=LEAVE,COMMAND:duck
~
```

Whoever has not left the room or typed `duck` after two ticks takes 60 to 90 damage,
unless the lich is stunned or loses 15% of its hit points meanwhile.

### A defense wave

Goal: a gate room where the party must hold off three waves, win a trophy, and
be knocked out rather than killed if beaten.

Room `Gate Yard`:

```text
Behaviors:
  WaveSpawner WAVE1="catalog:a raider*3" WAVE2="a raider*2+a raider archer*2" WAVE3="encounter:raid_boss" NEXT=CLEAR START=ENTER LOCKEXITS=true REWARD="a laurel" ONWIN=YARDWON ONLOSE=SPARRING:Barracks#1 RESETAFTER=300
Effects:
  Prop_PartyScaler BASE=3 HP=+15%/MEMBER
```

The yard's scripts:

```text
WAVE_PROG 2
  mpecho A horn sounds. More raiders pour over the wall!
~
FUNCTION_PROG YARDWON
  mpecho The raiders flee. The garrison cheers $n!
~
ARENALOSE_PROG
  mpecho The captain drags you inside to recover.
~
```

`world/encounters/raid_boss.enc` holds the final wave as a table roll:

```text
MOBS "a raider chief,a raider*2" WEIGHT=1
```

How it plays: walking in starts wave 1 and locks the exits; the next wave comes
when the last is dead; the third rolls the encounter table. A win unlocks the exits,
gives the laurel to the winner, runs `YARDWON`, and re-arms after 300 ticks. If the
party leaves or falls, what is left of the wave leaves, and `ONLOSE=SPARRING`
makes the defeat non-lethal, waking the loser in `Barracks#1`.

Checks: all `WAVEn` names exist in the catalog; an exit-locked room has a way out
if the game is saved mid-wave and restored (the current wave is saved); a single
party member vs a full party (`Prop_PartyScaler` on the room scales each NPC that
fights the party); the room after the reset.

### Tuning and balance

Several layers multiply: the NPC formulas ([C14](#c14-npc-formulas)), the mob's
rank ([C15](#c15-mob-ranks)), party scaling ([C6](#c6-party-scaling)), the
difficulty preset ([C13](#c13-difficulty-presets)) and any `Prop_CombatAdjuster`
or armor/absorb properties. Tune one layer at a time and record what you changed.

1. Fix the mob's level and let the formulas set the baseline.
2. Compare with `CONSIDER ALL` (it uses level and rank power, not the fine numbers).
3. Fight it with `COMBATDEBUG ON <mob>` to see hit chances and damage per swing.
4. Only then add a rank, scaler or difficulty change.

An invulnerable archon gives misleading balance results. Use `PLAYTEST ON`, which
suspends builder powers so aggressive mobs, followers, dopplegangers and spawns
behave as for a player, and build a representative character with `TESTCHAR` /
`KIT` ([13-testing-release.md](13-testing-release.md)); `PLAYTEST` does not
rebuild a well-balanced level-1 character from your builder.

## Debugging combat

### COMBATDEBUG

```text
COMBATDEBUG ON ([target])
COMBATDEBUG OFF ([target])
COMBATDEBUG LOG [file]
COMBATDEBUG LOG OFF
```

Available in builder mode, in a `DEV=1` game, or with the `CMDMOBS` security flag.
Each attack by or on a watched character is explained on one line, prefixed
`[dbg]`. With no target, every attack in the game is watched. `COMBATDEBUG` alone
tells whether it is on and whom it watches. `LOG` also writes every line to a
file you name.

A line looks like this (illustrative; the pieces are those asserted by the DT35
fixture `dt35-combat.js`, joined by `; `):

```text
[dbg] a fixes goblin -> a fixes dummy: atk 58 vs ac -32, fudge -4, hit 50% roll 31 HIT; dmg 7 (TARGETED, from 5); FUDGE(-2 lvls) 6; damage 6 by a rusty sword; Prop_AbsorbDamage(50% +ALL) on a fixes dummy -> 3; deals 3
```

Read it left to right:

| Piece | Meaning |
| --- | --- |
| `atk A vs ac B` | The adjusted attack and adjusted armor used in the hit roll. |
| `fudge N` | The level-gap fudge added to the hit score (shown only when not 0). |
| `hit P% roll R HIT/MISS` | The hit score after the 5 to 95 clamp, the d100 roll, and the outcome. |
| `dmg N (TARGETED, from D)` | Damage after the targeted formula, starting from the damage stat D. |
| `CRIT (P%) N` | A weapon critical happened, with its chance. |
| `FUDGE(L lvls) N` | The level-difference fudge applied (L is attacker level minus target level). |
| `damage N by X` | The damage message posted, and its source. |
| `Prop_...(parms) on X -> N` | An effect or item that changed the damage and the new value (an absorb, a resistance, a ward). |
| `X on NAME stopped it`, `ITEM of NAME stopped it` | An effect or an item refused the damage. |
| `stopped` | The damage message was refused. |
| `deals N` | What was finally dealt. |

Damage outside an attack (a spell's) gets a line of its own. A fight with nobody
watched says nothing. The state is not saved.

### Other tools

| Tool | Use |
| --- | --- |
| `CONSIDER ALL` | The fight in one word, with ranks and party scaling ([C25](#c25-consider-for-the-whole-fight)). |
| `STAT BEHAVIORS <mob>` | Each behavior of a mob with its parameters and its run state (phase, summons, rotation log, waves, timers), and tells keys it does not recognise. The first check for a typo. |
| `TRACEB <mob> ([ticks])`, `TRACEB OFF` | Tells each decision of the host's timed behaviors for a while: when its time comes, whether it acts, could not act, or lost its chance roll. |
| `PLAYTEST ON ([PARTY=name,name] [DIFFICULTY=preset] [KIT=name])`, `PLAYTEST OFF`, `PLAYTEST GAME`, `PLAYTEST RUN [file]` | Meet the world as a mortal; run `.play`, `.test` and JavaScript tests in disposable campaigns. A death takes you back to the room you began in. `BUILD [command]` runs one command with powers back. |
| `TESTCHAR`, `KIT` | Make a representative character or give a kit. |
| `ENCOUNTER ROLL <table>`, `ENCOUNTER CHECK ALL` | Roll a table where you stand; check every entry. |
| `DIFFICULTY <name>` | Try the preset the player will use. |
| `GOD` | In a DEV game or builder mode, single powers and cheats on and off, instead of `PLAYTEST`. |
| `SCRIPTCHECK`, `SCRIPTPROF` | Syntax-check a script a hook relies on, profile slow triggers. |
| `WORLD CHECK`, `WORLD CHECK ONLY=COMBAT` | World linting; `ONLY=COMBAT` checks profile stat properties of the optional [combat profiles](16-combat-profiles.md). |
| `COMBATRESULT`, `WORLD COMBAT ...` | Only for the optional `combat.profiles` module; see [16-combat-profiles.md](16-combat-profiles.md). |

The fixtures `tools/fixtures/fixes/c*-*.js` and `dt35-combat.js` are executable
examples of each feature: they construct a mob or room, set the parameters shown
in this chapter, tick the behavior and assert the result. Read the one for the
feature you are debugging. See [13-testing-release.md](13-testing-release.md) for
running them.

Engine internals: the engine log has a `combat` writer used by `handleBeingDamaged`
(`DAMG` records of attacker and target stats) when the `CBTMSGS` key of
`coffeemud.ini` (shipped `OFF`) is set to `FILE`, `BOTH` or `OWNFILE` (see
[18-installing-and-running.md](18-installing-and-running.md)).

## Persistence notes

- **World files** (areas, rooms, mobs, items) store behaviors and properties with
  their parameter text, so `BossPhases`, `Morale`, `TargetPriority`, `Pursue`,
  `Prop_MobRank`, `Prop_Essential`, `Prop_PartyScaler`, `Prop_DeathRules`,
  `Prop_Checkpoint`, `Prop_Ambush`, `WaveSpawner` and `RandomEncounters` are saved
  with the thing they sit on. Encounter tables are `world/encounters/*.enc`.
  The ini keys (`DIFFICULTY_*`, `MOBRANK_*`, `FORMULA_*`, death keys) are in
  `world.ini` for a world; see [world-settings.md](world-settings.md).
- **Campaign saves** keep the run state of the fight machinery: boss phase and
  rounds, engagement, immunity, summon bindings and combat targets; rotation round,
  cooldowns and ONCE history, threat and scheduling; wave state; named alarms and
  telegraphs; each character's checkpoint; and the chosen difficulty.
  `Prop_Downed` is not saved with the character. See [state-and-persistence.md](state-and-persistence.md).
- A **world reload** re-reads parameters but not necessarily fight state; a mob in a
  fight at reload is a new object. Treat `RELOAD` as a fight reset.
- **Rejuvenation** re-creates a dead mob from its world definition at its start
  room, after the `FORMULA_NPCREJUV` time or its own rejuv setting.
  `Prop_RejuvWhen` can hold it back. Spawners track their spawns; a despawn removes
  them quietly.
- **Difficulty** is part of the save; the ini value is only the starting choice.

## Troubleshooting and gotchas

- **A behavior seems to do nothing.** Run `STAT BEHAVIORS <mob>`: unknown parameter
  keys are reported. Check the help `AHELP BEHAVIOR_<NAME>`. Quote values with
  spaces. Remember `WHEN`, `TOD` and `HOURS` can switch a behavior off.
- **Two presets on one mob.** Use exactly one of `CombatAbilities`, `Fighterness`,
  `Thiefness`, `Mageness`, `Clericness`, `Druidness`, `Bardness`.
- **A preset changed my mob's stats.** Presets rewrite stats to suit the class.
  Add `NOSTATS` / `NOCOMBATSTAT` (as the help allows), and reset the room after
  changing a preset.
- **Support healer heals nobody.** `HEALAT` needs a target under the percentage; it
  heals the most hurt first. The mask in `SUPPORT=MASK:` must fit the ally. NPC
  healers act only when a character is in the room.
- **A phase fires again after a reset.** `RESET=FULLHEAL` rearms when the boss is
  whole and out of a fight. Use `LEAVECOMBAT`, `LEASH` or `NEVER` to choose.
- **Adds never disappear.** `SUMMON` adds go when the phases rearm and with
  `EncounterReset DESPAWNADDS=true` (NPCs with no home or tagged `SUMMONED`).
  Adds made by other means need their own cleanup.
- **A yielding mob keeps getting attacked.** `Prop_Essential` refuses attacks
  only for `TICKS`; then it can be attacked again. A killing blow in
  `MODE=YIELD` is not prevented; use `KNOCKOUT` when the mob must survive.
- **DOWNED does nothing.** It is in `PLAYERDEATH` but the victim is not on the
  player's side (no `playerStats` and no player group leader), or a
  `Prop_DeathRules` in the room or area replaces `PLAYERDEATH`.
- **The party wakes in an unexpected place.** `DEATH=CHECKPOINT` falls back to the
  recall room. `Prop_DeathRules TO=` overrides death in its room or area.
- **A save/damage seems 100% or 0%.** Spell saves are clamped to 5 to 95; weapon
  and spell *damage* saves (the damage-reduction ones) are clamped to plus or
  minus 100 and have no floor.
- **Hit chance never reaches 0 or 100.** It is clamped to 5 to 95.
- **Balance looks wrong as an archon.** Archon powers hide the fight. Use
  `PLAYTEST`.
- **Tough numbers stack.** Rank, scaler, difficulty and formulas multiply.
  Change one at a time.
- **Turn-based combat and CombatAbilities.** In `TURNBASED`, `CombatAbilities`
  needs more than one attack and a normal weapon attack; test it.
- **A telegraph never lands.** Stun, sleep, hold, bind, damage over the threshold
  or leaving the room cancel it; so can `AVOID` for each foe. Check
  `ALARM($i NAME)`.
- **A wave room traps the party.** `LOCKEXITS=true` holds them until the end;
  test a loss, a death and a save/restore, and keep an `ONLOSE` path.
- **Catalog names.** Spawners, summons, waves, ambushes and encounter tables
  refer to catalog names or mob classes. A misspelled name makes nothing appear
  (use `ENCOUNTER CHECK ALL`).

## See also

- [06-npcs-dialogue.md](06-npcs-dialogue.md): NPC life, schedules, sentries, memory and dialogue.
- [21-mobs.md](21-mobs.md): creating mobs, equipment, catalog, spawning and rejuvenation.
- [22-items.md](22-items.md): weapons, armor, damage types and ranges.
- [23-properties-reference.md](23-properties-reference.md) and [24-behaviors-and-masks.md](24-behaviors-and-masks.md): every property and behavior, and the zapper mask language.
- [25-abilities-and-effects.md](25-abilities-and-effects.md): spells, skills, effects, `WINDUP` on generic abilities.
- [04-mobprog.md](04-mobprog.md), [26-mobprog-triggers.md](26-mobprog-triggers.md), [27-mobprog-commands.md](27-mobprog-commands.md), [28-mobprog-functions.md](28-mobprog-functions.md): scripting encounters.
- [05-javascript.md](05-javascript.md): JavaScript hooks such as `beforeDeath`.
- [03-characters.md](03-characters.md): levels, experience, classes.
- [31-saves-and-party.md](31-saves-and-party.md): party, formation, turns, save rules.
- [13-testing-release.md](13-testing-release.md): PLAYTEST, TESTCHAR, fixtures.
- [15-ability-composition.md](15-ability-composition.md) and [16-combat-profiles.md](16-combat-profiles.md): the optional composed-ability and combat-profile modules.
- [19-configuration-and-administration.md](19-configuration-and-administration.md), [world-settings.md](world-settings.md): the ini and world.ini keys.
- [32-engine-programming.md](32-engine-programming.md): CMMsg flow, ticks and how combat hooks into the engine.
- [cookbook.md](cookbook.md): short recipes, including a boss phase, a surrender and an encounter table.
