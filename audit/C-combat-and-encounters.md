# Combat and encounters

Fights, boss mechanics, monster AI in combat, encounter spawning, difficulty and scaling, respawns, and death and defeat rules. Part of the [builder capabilities audit](README.md).

<a id="c1"></a>

## C1. Healing and support aimed at the ally who needs it

*Kind:* behaviour · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** CombatAbilities casts helpful skills only on itself or on the mob it follows, and only when that leader is in the room. Through CombatAbilities, a shaman heals its chief only if it follows him, and then its Aggressive needs MISBEHAVE. Healer casts one of six fixed prayers on a random inhabitant whose group leader is a player, only while a player is in the room, whether hurt or not. NiceAbilities (`min=1 max=1 ; <zapper mask>`) already casts the mob's own beneficial skills on a random inhabitant matching the mask, NPCs included, in or out of combat, while a player is in the room. A cure goes only to a target that is hurt or diseased, because its castingQuality is BENEFICIAL_OTHERS only then. So Clericness plus NiceAbilities with a mask matching the chieftain heals him without following him, and buffs him before a fight. CombatAbilities also has a PRECAST stat that casts self-buffs out of combat every n ticks, but only Java sets it. Nothing picks the most hurt ally, waits for an HP threshold or cures first.

**Add.** On CombatAbilities and its presets (Clericness and the like): `SUPPORT=SELF|LEADER|GROUP|MASK:<zapper>`, `HEALAT=50` (only allies under 50% HP, the most hurt first), `CUREFIRST=Poison,Disease`, and `BUFFOUTOFCOMBAT` (buff allies before a fight). On Healer and NiceAbilities (which already takes a zapper mask): `HEALAT=` and most hurt first. On Healer also `SPELLS=id,id` and `TARGET=GROUP|MASK:<zapper>`.

**Example.** A goblin shaman with `Clericness MIXEDDEFENSIVE SUPPORT=MASK:"-RACE +Goblin" HEALAT=60` keeps the chieftain standing, so the party learns to kill the shaman first. A temple acolyte: `Healer HEALAT=70 SPELLS=Prayer_CureSerious,Prayer_CureDisease TARGET=MASK:"-NPC"`.

*Re-checked against the code after writing: corrected.*

*See also:* C22 (Both add CombatAbilities options for healing and when to use abilities (HEALAT and SUPPORT vs BELOW, SELF and COOLDOWN)).

<!-- from: behaviors-npc/npc-support-ai -->

<a id="c2"></a>

## C2. Morale: mobs and groups that flee, rally or surrender

*Kind:* behaviour, script trigger · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Mobs fight to the death or flee on their own. FightFlee rolls a flee chance every tick whatever the HP, Wimpy flees by mask, and the engine's wimpy HP is absolute and out of reach on generic mobs. For a gang to break when its chief dies, the chief needs a DEATH_PROG that loops over the gang with MPFORCE $0 flee. Surrender exists only as the player skill Thief_Surrender.

**Add.** A behaviour `Morale`. `FLEEAT=30%` and `OUTNUMBERED=2.0` (enemies to allies in the room). `BREAK=LEADERDEAD|50%DEAD|HP<20%`: the leader is the mob it follows or `LEADER=<mask>`, and the group is its followers and BrotherHelper allies. Then `ACTION=FLEE|SURRENDER|BERSERK`, with `CHANCE=` and `MSG=`. `RALLY=<room>` with `REGROUP=n`: it flees there, rests n ticks, then comes back with any allies there. `SURRENDERAT=15%`: it drops its weapon, makes peace (reusing Thief_Surrender's peace logic), says `SURRENDERMSG=` and fires a new SURRENDER_PROG. BERSERK gives damage +30% and attack -10%. `BRAVE=<mask>`: never breaks.

**Example.** Goblins: `Morale FLEEAT=40% BREAK=LEADERDEAD ACTION=FLEE RALLY=Warren#12 REGROUP=15`. Kill the chieftain and they scatter to the warren, then return with the shaman. A bandit with `SURRENDERAT=20%` begs for mercy, and its SURRENDER_PROG offers to show where the loot is.

*See also:* C17 (Both make a mob stop fighting at low HP and fire a trigger (SURRENDER_PROG vs YIELD_PROG)).

<!-- from: behaviors-npc/npc-morale, encounters-combat/enc-morale -->

<a id="c3"></a>

## C3. BossPhases: boss phases that fire once per fight

*Kind:* behaviour, script trigger, script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Every phase action exists as a MOBPROG command (MPCAST, MPMLOAD, MPBEHAVE, MPAFFECT, MPHEAL, MPTRANSFER, MPECHO). But HITPRCNT_PROG fires every tick while HP is under its threshold. So each phase needs a guard. A per-instance tattoo (MPTATTOO) works best, because MPSETVAR variables are keyed by the mob's name and shared by its copies. Guards must be cleared by hand in DEATH_PROG, or a tattoo saved mid-fight comes back on rejuv. MPNOTRIGGER mutes every HITPRCNT prog at once.

**Add.** A behaviour `BossPhases`. Phases are `AT=75%`, `AT=ROUND n`, `AT=ADDSDEAD`, `ONENGAGE:` and `ONDEATH:`, and each fires once per fight. Actions: SAY, EMOTE, YELL, CAST, SUMMON \<catalog name> xN (gone when the fight ends), BEHAVE/UNBEHAVE, AFFECT or REMOVE \<ability> [ticks], IMMUNE ticks, HEAL n%, MOVETO \<room>, ROOM EMOTE/AFFECT, CALL \<function>. `RESET=FULLHEAL|LEAVECOMBAT|LEASH|NEVER` says when the phases rearm. The phase is per instance, saved with the mob, and set back to 1 on rejuv. Scripts get PHASE_PROG n and `PHASE($i == n)`.

**Example.** A lich: `BossPhases ONENGAGE: SAY "Fools!"; AT=60%: SUMMON skeleton-warrior x2; AT=25%: AFFECT Prop_AbilityImmunity "WEAPONS" 3, MOVETO Crypt#9, HEAL 20%; ONDEATH: ROOM EMOTE "The phylactery cracks."`

*See also:* ST15, C21 (C3's ONENGAGE and rearm and C21's reset need the combat start and end detection that ST15 adds); C21 (C3's RESET=LEASH rearm and C21's EncounterReset both reset a boss whose party walked away).

<!-- from: behaviors-npc/npc-boss-phases, encounters-combat/enc-boss-phases -->

<a id="c4"></a>

## C4. Spawners with catalog weights, conditions and despawn

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The catalog is mounted under resources/catalog, with one all.cmare per category. So `RandomMonsters ... ;catalog/mobs/wolves/all.cmare` and `MPMLOAD fromfile` already draw from it. To weight the picks, a builder has to repeat entries in a hand-made file. RandomMonsters and RandomItems filter only by room kind, and what they spawn stays forever. A quest can spawn on conditions with a room mask, LOAD MOBGROUP and DURATION, but that takes one quest per spawn.

**Add.** New parameters on RandomMonsters and RandomItems:
- `CATALOG="grey wolf=5,dire wolf=1"`: weighted catalog names;
- `WHEN="<mask>"`, tested on the chosen room;
- `DESPAWN=WHENFALSE|DAWN|TICKS=n`: the mob leaves unseen;
- `NEAR=PARTY:3`: spawn within 3 rooms of the player;
- `MAXAREA=n`;
- `LEVEL=PARTY±2`, using the catalog's level adjustment.

This complements DEVPLAN item 1, which covers catalog items.

**Example.** Pine Forest: `CATALOG="grey wolf=4,dire wolf=1" WHEN="+SEASONS WINTER +HOURS 19-5" DESPAWN=DAWN maxmonsters=6`. Wolves roam winter nights and are gone by morning.

*See also:* SC3, C9 (All three add a catalog source to RandomMonsters and MPMLOAD (catalog:name, CATALOG= weights); settle one syntax).

<!-- from: behaviors-world/bw-spawner -->

<a id="c5"></a>

## C5. Telegraphed attacks: named, cancellable, saved alarms

*Kind:* script command, script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** MPALARM \<ticks> \<command> delays an action. The function it fires can check things at that moment: a stun (`AFFECTED($i Spell_Stun)`), the caster's death, or a `braced` tattoo set by a BRACE command made with CNCLMSG_PROG HUH. DELAY_PROG can repeat a warning. But alarms have no name. MPNOTRIGGER cannot cancel them, nothing can ask about them, and they are not written to the save. GenAbility has no cast time; CHANNELING only stops auto-attacks.

**Add.** `MPALARM NAME=<name> <ticks> <command>`, `MPALARMCANCEL <name>`, and `ALARM($i <name>)` for the ticks left. Named alarms are saved. They are cancelled when the caster dies, is stunned or is knocked out. On top of them: `MPTELEGRAPH <ticks> "<warning>" <command> [INTERRUPT=STUN,DAMAGE>20%,MOVE] [AVOID=LEAVE|HIDE|COMMAND:brace]`. It shows the warning each tick, and LOOK shows '\<boss> is gathering power'. A GenAbility field `WINDUP=ticks` with `WINDUPMSG` lets mobs using CombatAbilities telegraph custom abilities.

**Example.** "The dragon rears back, flames gathering in its throat!" Two turns later its breath does 8d10 fire to everyone in the room, except those who typed BRACE behind a pillar. If the party stuns it first, the breath is cancelled.

*See also:* S7 (Both save pending MPALARMs (C5's named alarms, S7's queued alarms)).

<!-- from: encounters-combat/enc-telegraph -->

<a id="c6"></a>

## C6. Prop_PartyScaler: scale fights to the party's size

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Prop_Doppleganger scales a mob's level to its foes. With DIFFGRP=true it counts the group and scales DIFFADD and DIFFPCT per member, and PLAYERSNFOLS, LEVELCODE, MIN and MAX tune it. But it rescales only at full HP and changes only stats that follow from level. It also skips Archon characters, so a builder testing it sees nothing. Extra mobs per party member need a GREET_PROG loop over NUMPCSROOM with MPMLOAD.

**Add.** `Prop_PartyScaler` for mobs, rooms or areas, an extension or sibling of Prop_Doppleganger: `BASE=3` (the party size it is tuned for), `HP=+25%/MEMBER`, `DAMAGE=+10%/MEMBER`, `ATTACK=+5/MEMBER`, `LEVEL=AVG+2`, `MIN=`, `MAX=`, `ADDS=catalog:skeleton/MEMBER-ABOVE-2`, `WHEN=ENGAGE`. MEMBER counts the leader's party and followers in the room (core/Party). Scaling applies when combat starts and holds until the fight resets. It shows in CONSIDER and honours the PLAYTEST toggle.

**Example.** The Bone King is tuned for 3 characters. Against 1 he has 50% less HP and no skeleton guards; against 5 he has 50% more HP and two extra guards.

*See also:* C25 (C25's CONSIDER ALL notes when C6's party scaling applies).

<!-- from: encounters-combat/enc-party-scaler -->

<a id="c7"></a>

## C7. Checkpoints: per-character respawn points

*Kind:* property, script command, script function, world option · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `MPBEACON <room> all` sets the start and recall room of every character in the room. With DEATH=START, a dead character wakes there, and ISRECALL() reads the room. So a shrine is a room script: a 'pray' command made with CNCLMSG_PROG HUH runs mpbeacon, mpheal and `mprejuv area`. PLAYERDEATH=RETAIN,RECALL already avoids corpse runs. But MPBEACON also moves the recall room, every shrine is a separate script, and saving from a script is untested. DEATH_MASK entries matched on tattoos fail when a character has several tattoos, because their order is undefined.

**Add.** `Prop_Checkpoint` on rooms: `NAME="Old Shrine"`, `ON=ENTER|REST|COMMAND:pray`, `HEAL=100%`, `RESPAWNAREA=true|false`, `SAVE=AUTO` (an autosave slot). It records the room as each party member's respawn point. The point is saved in the character and kept apart from the recall room. A `DEATH=CHECKPOINT` keyword falls back to START when no checkpoint is set. Scripts get `MPSETRESPAWN $n <roomid>` and `RESPAWNROOM($n)`.

**Example.** With PLAYERDEATH=RETAIN,RECALL and DEATH=CHECKPOINT, a character killed in the crypt wakes at the last shrine they prayed at, with their gear, and pays 10% of their gold. A shrine with RESPAWNAREA=true brings the crypt's skeletons back.

*See also:* C8, C16 (Three changes to where and how the party wakes after death; C8's CHECKPOINT uses C7, and C16 makes the death rules per area); CR13 (Both set a per-character return point for the party (a respawn checkpoint vs a home start room)); S2, S23 (Three autosave designs (Prop_AutoSave, AUTOSAVE=n, a checkpoint's SAVE=AUTO), and S23's CHECKPOINT command shares C7's name).

<!-- from: encounters-combat/enc-checkpoint -->

<a id="c8"></a>

## C8. A downed state and rules for when the whole party falls

*Kind:* world option, script trigger · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Each character dies alone by the global PLAYERDEATH list. OUT x knocks a character out safely for x ticks. ASTRAL and ASTRAL_RES leave a spirit that can be resurrected, and clerics have Prayer_Resurrect. But no ally can revive a fallen character during a fight, and there is no bleed-out window. Nothing notices when the whole party is down, because core/Party has no death handling. Prop_SparringRoom moves deaths to a set room, but it drops all followers and so breaks the party.

**Add.** A new PLAYERDEATH option, `DOWNED x`. At 0 HP the character falls for x rounds and mobs ignore them. An ally's REVIVE, or a heal of at least y HP, brings them back. If the fight ends first, they get up at 1 HP. If x runs out, the rest of the PLAYERDEATH list applies. `PARTYDEFEAT=RESTORE|CHECKPOINT|DEATH` (or a list) applies when every party member is down or dead. RESTORE offers the latest save, and CHECKPOINT moves everyone there with a penalty expression. A PARTYDEFEAT_PROG fires on rooms and areas. The party stays together.

**Example.** The healer drops at 0 HP, and the fighter types REVIVE on the next turn. Later the ogre kills the whole party. The game says 'Your party has fallen' and offers the save from the dungeon entrance. Another area's PARTYDEFEAT_PROG wakes the party in the goblin pens.

*See also:* C7, C16 (Three changes to where and how the party wakes after death; C8's CHECKPOINT uses C7, and C16 makes the death rules per area).

<!-- from: encounters-combat/enc-party-defeat -->

<a id="c9"></a>

## C9. Random encounters on entering a room, from encounter tables

*Kind:* behaviour, data file, script command, quest feature · *Value:* high · *Effort:* large · *Decision:* yes

**Today.** RandomMonsters spawns on a timer, not when the party arrives, and picks evenly from a fixed list. Today an area script does encounters: ALL_GREET_PROG fires on every room entry, tests ISTIME, INLOCALE and a cooldown VAR, then runs MPMLOAD FROMGENFILE on generator XML that has weights and catalog members. That XML is heavy to write, sits under resources/, and caches results per parameter string, so the same picks repeat.

**Add.** A behaviour `RandomEncounters` for areas and rooms: `TABLE=`, `CHANCE=` per room entered, `COOLDOWN=` (in rooms or game days), `LOCALES=`, `TRIGGER=<mask>`, `MAXACTIVE=`, and `DESPAWN=` after the party leaves. Tables are files `world/encounters/<name>.enc`, edited with an ENCOUNTER builder command. An entry has a weight, catalog members with dice counts and a leader, a party mask (+HOUR, +SEASON, -LEVEL, +GROUPSIZE), a level band, ONCE and COOLDOWN, and `FROM=HIDDEN|ADJACENT|HERE`. AMBUSH gives the monsters the first round. An entry can also be ITEM, EMOTE, TRAP, MERCHANT or FUNC \<function>. `BUDGET=pct` adds entries until they reach that share of the party's power. Also add `mpmload encounter <table>`, the quest command `load encounter`, and `catalog:<name>` in RandomMonsters, MPMLOAD and MPOLOAD.

**Example.** Darkwood: `RandomEncounters TABLE=darkwood CHANCE=10% COOLDOWN=6`. By day the party meets deer or a few bandits. At night it meets 2d3 wolves led by a dire wolf, or a wraith if the party is above level 8. Once, it meets a lost traveller whose FUNCTION_PROG starts an escort.

*See also:* SC3, C4 (All three add a catalog source to RandomMonsters and MPMLOAD (catalog:name, CATALOG= weights); settle one syntax); C20 (C20's encounter: waves draw on C9's encounter tables); C23 (C23's ambush and C9's AMBUSH entries both give monsters a surprise first round, from encounter: tables); DT40 (later) (DT40 (later) simulates fights against C9's encounter: tables); C15, C25 (C9's encounter budget and C25's CONSIDER ALL both count C15's mob ranks).

<!-- from: behaviors-npc/npc-encounter, behaviors-world/bw-encounters, generators/encounter-tables, encounters-combat/enc-tables -->

<a id="c10"></a>

## C10. TargetPriority: which party member a monster attacks

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** TargetPlayer switches to the lowest-level player in the enemy group. CombatAbilities switches to whoever has dealt the most damage or healing, once that lead is clear: over 10% of its max HP and 1.5 times the current victim's. Any other choice takes a hand-written FIGHT_PROG for each monster. The script tests HITPRCNT() in a FOR loop and switches with MPKILL \<name>.

**Add.** A behaviour `TargetPriority`:
- `PREFER=`: an ordered list from HEALER, CASTER, LOWESTHP, LOWESTARMOR, LEADER, LASTHITME, STRONGEST and MASK:\<zapper>;
- `SWITCH=30`: the chance per tick to switch;
- `STICKY=3`: ticks before it may switch again;
- `TAUNTABLE`: a clear aggro lead overrides the list.

**Example.** An assassin with `TargetPriority PREFER=CASTER,LOWESTHP SWITCH=50` goes for the mage. An ogre with `PREFER=LASTHITME STICKY=4` turns on whoever hit it last. Pack wolves with `PREFER=LOWESTHP` gang up on the wounded.

*See also:* C18 (Both choose which party member a monster attacks (a behaviour vs script threat functions)).

<!-- from: behaviors-npc/npc-target-priority -->

<a id="c11"></a>

## C11. Pursue: chase a fleeing target, then go home

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Fleeing one room usually escapes. VeryAggressive has a 15% chance per tick to step into a room next door that holds a target. It has no leash and does not return home. Follower does not follow while in combat, so it gives no pursuit. MOBHunter hunts by mask through Thief_Assassinate, and that is broken. A script can chase. EXIT_PROG does not fire on a flight: fleeing is a FLEE message, not a LEAVE, and walking out of a fight is refused. EXECMSG_PROG FLEE, or ACT_PROG on 'flees', does fire, even in combat. The script then runs MPTRACKTO $n, and later MPWALKTO $%ISRECALL($i)% to walk home.

**Add.** A behaviour `Pursue`:
- `CHANCE=60`: the chance to chase each time a target flees;
- `MAXROOMS=3`, `DELAY=1` (ticks), `MASK=`;
- `LEASH=`: the furthest it goes from its start room;
- then home through WanderHomeLater, or `STAY`, with `GIVEUPMSG=`;
- `REMEMBER=n`: a target who comes back within n ticks is attacked on sight.

**Example.** A mountain cat: `Pursue CHANCE=80 MAXROOMS=4 LEASH=8`. A town guard: `Pursue CHANCE=50 MAXROOMS=10 MASK="-RACES +Goblin"` (no zapper mask can test for a warrant today).

*Re-checked against the code after writing: corrected.*

*See also:* C21 (Both send a mob home after it loses its target (Pursue's LEASH vs EncounterReset)).

<!-- from: behaviors-npc/npc-pursue -->

<a id="c12"></a>

## C12. Target masks and life steal on damage properties

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_AddDamage adds its extra damage against every target. Prop_AbsorbDamage cannot tell attackers apart. The masks on Prop_HaveResister and Prop_WearResister test the wearer, not the attacker. A bane weapon therefore needs a workaround. One is Prop_FightSpellCast with a damage spell and `MASK=-RACECAT +Undead`. Another is a DAMAGE_PROG on the weapon that tests RACECAT($t), then runs MPDAMAGE. No life steal exists outside spells.

**Add.** `TARGETMASK="<zapper>"` on Prop_AddDamage, so its extra damage applies only to matching victims. `LEECH=<pct>`, also on Prop_AddDamage, heals the wielder by that share of the damage dealt. `SOURCEMASK="<zapper>"` on Prop_AbsorbDamage, Prop_HaveResister and Prop_WearResister, so their reduction applies only against matching attackers.

**Example.** The Sunblade does 50% extra burning damage to undead only. The Wyrmguard shield absorbs 40% of fire damage when the attacker is a dragon.

<!-- from: properties/pr-bane -->

<a id="c13"></a>

## C13. A difficulty setting and a player-side flag in combat formulas

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** XPMOD in coffeemud.ini scales XP gains and losses for the whole game. AUTOAREAPROPS can put a property on every area, but no area property changes NPC combat stats: Prop_HereAdjuster works on rooms only, and Prop_Adjuster on one mob. The FORMULA_* strings cannot tell the player's side from the mobs' side; their PVP twins apply only between players. Editing a formula therefore changes fights between mobs too. There is no difficulty setting.

**Add.** `DIFFICULTY=NORMAL` in coffeemud.ini, and a DIFFICULTY command in play, saved with the game. Each preset sets multipliers: NPCDAMAGE, NPCHP, PLAYERXP and PLAYERDAMAGE. The attack, damage and crit formulas also get `@xP`, which is 1 when the attacker is a player or a player's follower and 0 otherwise. They also get `@xD`, the difficulty multiplier, so builders can write their own curves.

**Example.** In Story mode, mobs deal 60% damage and the player gets a crit bonus. In Hard mode, mobs deal 130% damage and have 120% HP. The player switches to Story before a boss they keep losing to.

*See also:* C14 (Both change the ini combat formulas for NPCs; C13's @xP and @xD variables belong in C14's new formulas).

<!-- from: encounters-combat/enc-difficulty -->

<a id="c14"></a>

## C14. Ini formulas for NPC armor, attack, damage, speed, rejuv and money

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** NPC hit points are the only NPC stat with an ini formula, FORMULA_NPCHITPOINTS. CoffeeLevels.getLevelAttack, getLevelMOBArmor, getLevelMOBDamage and getLevelMOBSpeed are fixed in Java. fillOutMOB also fixes rejuv time (2 minutes plus level/2 minutes, at most 20) and money (1d\<level>+1d10). RESET MOBCOMBATSTATS and Prop_Doppleganger use the same functions. The only way round it is per mob: Prop_Adjuster with MULTIPLYPH and MULTIPLYCH, or stats set by hand on each GenMob.

**Add.** Six formulas in coffeemud.ini: FORMULA_NPCARMOR, FORMULA_NPCATTACK, FORMULA_NPCDAMAGE, FORMULA_NPCSPEED, FORMULA_NPCREJUV and FORMULA_NPCMONEY. In them, @x1 is the level, @x2 is LASTPLAYERLEVEL, @x3 is the mob's ABILITY (its HP modifier), and @x4 is the attack attribute bonus. The defaults equal today's expressions, so nothing changes until the owner edits them. RESET MOBCOMBATSTATS and Prop_Doppleganger use the new formulas.

**Example.** The owner wants a low-magic world, where level-10 mobs hit harder but are easier to hit. They set `FORMULA_NPCDAMAGE=@x1*1.3` and `FORMULA_NPCARMOR=110-(@x1*1.8)`, then run RESET MOBCOMBATSTATS WORLD.

*See also:* C13 (Both change the ini combat formulas for NPCs; C13's @xP and @xD variables belong in C14's new formulas).

<!-- from: encounters-combat/enc-npc-formulas -->

<a id="c15"></a>

## C15. Prop_MobRank: minion, normal, elite and boss presets

*Kind:* property, script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The engine has no notion of rank. Each tough mob is tuned by hand: Prop_Adjuster with MULTIPLYPH and MULTIPLYCH for attack, armor, damage and HP, Prop_ModExperience for XP, and Prop_Resistance for resistances. A builder can tag a mob as elite and test the tag with +TAG in masks or HASTAG() in scripts. But the player never sees the tag, and retuning every elite means editing each one.

**Add.** `Prop_MobRank RANK=MINION|NORMAL|ELITE|BOSS`, built on Prop_Adjuster, since builders cannot use Prop_CombatAdjuster. It reads a table from coffeemud.ini, such as `MOBRANK_ELITE=HP+100% DAMAGE+25% XP*2.5 RESIST=mind 25% TAG=[elite]`. The tag shows in LOOK and CONSIDER. Scripts get RANK(), and masks get a +TAG-style test. Encounter budgets count each rank's power.

**Example.** The gnoll warlord is RANK=BOSS, and its bodyguards are RANK=ELITE. When bosses feel too spongy, one edit to MOBRANK_BOSS fixes all of them.

*See also:* C9, C25 (C9's encounter budget and C25's CONSIDER ALL both count C15's mob ranks).

<!-- from: encounters-combat/enc-mob-rank -->

<a id="c16"></a>

## C16. Prop_DeathRules: death and defeat rules per area or room

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** PLAYERDEATH, DEATH, MORGUE and ITEMLOOTPOLICY are global, and MUDFight reads them from the ini only. Prop_SparringRoom, on a room, area or mob, cancels a death and moves the character to a set room. Its behaviour is fixed, and it drops all followers. Custom rules take a room or area script: CNCLMSG_PROG S=DEATH ALL, then MPSTOP, MPHEAL and MPTRANSFER, gear moved with MPJUNK and MPOLOAD, then `return cancel`.

**Add.** `Prop_DeathRules` on areas or rooms. MUDFight reads it before the global values. Its parameters:
- `PLAYERDEATH="OUT 3,RETAIN"`, `MOBDEATH=` and `ITEMLOOTPOLICY=`;
- `TO=<room>`;
- `GEAR=CONTAINER:<room>/<container>`;
- `HEAL=pct`, `MSG=` and `MASK=`.

The party's followers stay with it.

**Example.** In the bandit hideout, defeat moves the character to the cage room with 25% HP. Their weapons go to the chief's strongbox, and the defeat becomes a jailbreak scene instead of a reload. A tutorial area uses OUT, so losing there only knocks the character out. A sparring hall sets MOBDEATH=OUT.

*See also:* C7, C8 (Three changes to where and how the party wakes after death; C8's CHECKPOINT uses C7, and C16 makes the death rules per area).

<!-- from: encounters-combat/enc-death-zone -->

<a id="c17"></a>

## C17. Essential and yielding mobs

*Kind:* property and script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_SafePet makes a mob wholly unattackable, with a custom MSG, so a quest NPC is safe from stray blows and from AUTOASSIST. Prop_PracticeDummy never dies, but never fights back. A mob that yields mid-fight is a script: HITPRCNT_PROG 10 guarded by a tattoo, then MPSTOP ALL, MPAFFECT Prop_SafePet for some ticks, an echo, and SPEECH_PROG branches. CNCLMSG_PROG can cancel the mob's own death.

**Add.** A property `Prop_Essential MODE=KNOCKOUT|YIELD AT=10% TICKS=20 MSG="<S-NAME> drops to one knee and yields!" NOXPPENALTY`. At the threshold the mob's fight ends, and attacks on it are refused with a message for TICKS. New triggers YIELD_PROG and DEFEAT_PROG fire so the script can branch: spare it, take its key, or finish it with a kill command.

**Example.** A rival knight yields at 10%. If the player says spare, they gain faction and he comes back later as an ally. Attacking again marks the player a murderer and costs faction.

*See also:* C2 (Both make a mob stop fighting at low HP and fire a trigger (SURRENDER_PROG vs YIELD_PROG)).

<!-- from: encounters-combat/enc-essential-yield -->

<a id="c18"></a>

## C18. Threat and target picking in scripts

*Kind:* script function and script command · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The CombatAbilities family keeps a threat table and retargets on it. Damage to the mob counts double, damage to its victim's group counts once, heals count triple, and AGGROFYING abilities add twice their level. So a taunt is a GenAbility flagged AGGROFYING. TargetPlayer switches to the lowest-level enemy. Scripts cannot read or change threat, since CombatAbilities exposes only PRECAST, PHYSDAMTAKEN, SKILLSALWAYS, SKILLSNEVER and COMBATMODE. Finding the lowest HP% needs an MQL loop over HITPRCNT.

**Add.** Functions THREAT($i $n > 100), TOPTHREAT($i), WEAKEST($i) (lowest HP%) and OPPONENT($i 'MASK=-CLASS +Cleric'), each returning a name usable in MPKILL. A command `MPTHREAT $i $n +500|=0|*2`. A light threat table, made on demand, for mobs without CombatAbilities.

**Example.** The ogre chieftain's FIGHT_PROG 20 runs MPKILL on WEAKEST($i) and echoes "The chieftain lunges at the weakest foe!". A custom TAUNT ability runs `MPTHREAT $t $n *3`.

*See also:* C10 (Both choose which party member a monster attacks (a behaviour vs script threat functions)).

<!-- from: encounters-combat/enc-threat -->

<a id="c19"></a>

## C19. Respawns that follow the story

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A dead mob comes back when its rejuv count runs out. The only exception is camping. A cleared camp refills even after its quest is won. The workaround is a ONCE_PROG on the mob, which runs again on each respawn. It checks QUESTWINNER or a global saved with MPSAVEVAR, then runs MPPURGE $i, or MPMLOAD of a replacement followed by MPPURGE $i. The mob shows for a moment, and the check runs only at respawn. Mobs loaded by a quest stop with the quest.

**Add.** `Prop_RejuvWhen WHEN="<zapper mask>" ELSE=WAIT|NEVER|REPLACE:<catalog mob>`, checked before the mob is brought back. WAIT checks again later, NEVER leaves it gone, and REPLACE spawns the catalog mob instead. Masks such as `+IF !QUESTWINNER(...)` or `-HOUR` say when. The same option works for item rejuv.

**Example.** Once the player wins "Clear the Old Mill", the bandits there never return, and settlers spawn in their place.

*See also:* W9 (Both keep a slain boss from coming back (Prop_RejuvWhen vs ResetWhole's EXCEPT=)).

<!-- from: properties/pr-rejuvwhen -->

<a id="c20"></a>

## C20. Wave and arena rooms

*Kind:* behaviour and script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Waves can be built by hand. Quest STEP sections or MPSTEPQUEST from a DEATH_PROG advance them. A room script counts survivors with NUMMOBSROOM, ISQUESTMOBALIVE or MQL, then uses MPMLOAD for the next group. MPCLOSE, MPLOCK and MPUNLOCK handle the exits, Prop_SparringRoom a defeat that is not death, and ResetWhole the reset. Wave variables are keyed by name. The invasion quest templates spawn once, with no waves.

**Add.** A room behaviour `WaveSpawner`, such as `WAVE1=catalog:goblin*3 WAVE2=catalog:goblin*2+catalog:goblin archer*2 WAVE3=encounter:goblin_boss`. It also takes NEXT=CLEAR|TICKS=n, START=ENTER|CALL, LOCKEXITS=true, ONWIN=function or unlock, ONLOSE=SPARRING|RESET, REWARD=item and RESETAFTER=ticks. It fires WAVE_PROG n, ARENAWIN_PROG and ARENALOSE_PROG. The wave number and state are saved.

**Example.** A gladiator pit locks its gate when the player enters. The arena master announces three waves. Clearing them opens the exit and gives a laurel. A defeated player wakes in the infirmary, and the pit resets.

*See also:* C9 (C20's encounter: waves draw on C9's encounter tables); C24 (later) (Both spawn waves of catalog mobs with win and lose outcomes; share the wave code).

<!-- from: encounters-combat/enc-waves -->

<a id="c21"></a>

## C21. Leash and reset for bosses

*Kind:* behaviour and script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A boss the party flees regains HP only at the slow natural rate, and keeps its phase variables and its summoned adds, so hit, retreat and return wins the fight. StdMOB has an empty "go home" branch, and Mobile's leash= only limits wandering. Parts exist. ResetWhole resets a room after n ticks with no player activity there. MPRESET $i restores HP, mana and moves. MPWALKTO and MPTRACKTO walk a mob home, $%ISRECALL($i)% gives its home room, and INROOM($i == ...) tells whether it is there. A RAND_PROG with !ISFIGHT($i) and an idle counter can leash a mob by hand.

**Add.** A behaviour `EncounterReset RADIUS=1 IDLE=5 HEAL=100% HOME=true DESPAWNADDS=true`. When no enemy has been within RADIUS for IDLE ticks, the mob returns home and heals. Its debuffs and phase state are cleared, only the adds it summoned are removed, and a new RESET_PROG trigger fires. ROOM=true applies it to every mob in the room.

**Example.** The party fights the ravine troll and runs at half HP. Five ticks later the troll is back on its bridge at full HP, growling.

*Re-checked against the code after writing: corrected.*

*See also:* ST15, C3 (C3's ONENGAGE and rearm and C21's reset need the combat start and end detection that ST15 adds); C3 (C3's RESET=LEASH rearm and C21's EncounterReset both reset a boss whose party walked away); C11 (Both send a mob home after it loses its target (Pursue's LEASH vs EncounterReset)).

<!-- from: encounters-combat/enc-leash-reset -->

<a id="c22"></a>

## C22. Ability rotations for CombatAbilities

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** CombatAbilities and its subclasses take +SKILL and -SKILL lists, COMBATMODE (RANDOM, DEFENSIVE, OFFENSIVE, MIXED, ONLYALWAYS), BSIZE, PROFICIENT and MIN/MAX/CHANCE timing. Each try picks a random ability that qualifies. A GenAbility gets a cooldown from TICKSBETWEENCASTS, and its CASTMASK may gate on HP through -IF, though that is untested. Stock abilities have no rules of their own, so "breath every 3 rounds, haste once below 30%" needs FIGHT_PROG with MPCAST and tattoo guards.

**Add.** Options on each entry of the +SKILL list, stock abilities included: EVERY, FIRST, ONCE, BELOW, COOLDOWN, WEIGHT, SELF and TARGET=CASTER. For example: `+Dragonbreath(EVERY=3 FIRST=2) +Spell_Haste(ONCE BELOW=30%) +Prayer_Heal(SELF BELOW=40% COOLDOWN=5) +Skill_Trip(WEIGHT=3 TARGET=CASTER)`. OPENER=skill sets the first round. Each mob keeps its own counters, and they reset when combat ends.

**Example.** A wyvern with no script: OPENER=Skill_Dive, `+Poison_Sting(EVERY=2) +Skill_Tail(WEIGHT=2) +Spell_Fly(ONCE BELOW=25%)`. When badly hurt, it takes to the air.

*See also:* C1 (Both add CombatAbilities options for healing and when to use abilities (HEALAT and SUPPORT vs BELOW, SELF and COOLDOWN)).

<!-- from: encounters-combat/enc-ai-rotation -->

<a id="c23"></a>

## C23. Ambushes with a perception check and surprise

*Kind:* property and script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A room ambush is GREET_PROG or ENTRY_PROG with MPMLOAD and MPFORCE kill. A tattoo or ONCE guards it, and masks such as +GROUPSIZE gate it. Hidden mobs (Prop_Hidden, MPHIDE) with Aggressive get a backstab opener, but nothing more. Room traps such as Trap_MonsterCage roll a save against level and can be found by trap-detection skills. GenTrap runs its SCRIPT only inside its ability branch, though, so it is awkward as an ambush. There is no party perception roll and no surprise round.

**Add.** `Prop_Ambush MOBS=encounter:bandits|catalog:bandit*3 CHANCE=100 DETECT=WIS+Skill_Search:15 SURPRISE=1 MSG="Figures burst from the undergrowth!" SPOTMSG="You spot glints of steel in the bushes." ONCE|RESET=ticks MASK=...`. It rolls DETECT for the party's best member. On a failure the ambushers act for SURPRISE rounds and the party's turn is skipped. On a success the party may strike first or avoid the ambush. AMBUSH_PROG fires with the outcome.

**Example.** On a forest road, bandits jump a party with no ranger. A party with a ranger spots them and can sneak past or strike first.

*See also:* C9 (C23's ambush and C9's AMBUSH entries both give monsters a surprise first round, from encounter: tables); I20 (C23's ambush notes that GenTrap runs its script only inside its ability branch, which I20 fixes).

<!-- from: encounters-combat/enc-ambush -->

<a id="c24"></a>

## C24. Invasions and raids on an area

*Kind:* behaviour and script command · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#c24).

<a id="c25"></a>

## C25. CONSIDER ALL for the whole fight

*Kind:* presentation · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** CONSIDER compares one mob with the one character who types it. GCONSIDER (GCOS, GCO) rates a mob against the group's combined damage, with return attacks only on the leader. GCONSIDER ALL repeats that for each visible mob. Nothing gives one verdict on all the room's enemies against the whole party.

**Add.** CONSIDER with no argument, or CONSIDER ALL, sums the power of the hostile mobs present and of the party (CoffeeLevels getPowerLevel). It gives one word, from trivial to deadly. It shows mob ranks, if ranks are added. It notes "scales to your party" when Prop_Doppleganger or a party-scaling property applies.

**Example.** In the barracks: "four guards and a sergeant (elite): a hard fight for your party of three."

*See also:* C15, C9 (C9's encounter budget and C25's CONSIDER ALL both count C15's mob ranks); C6 (C25's CONSIDER ALL notes when C6's party scaling applies).

<!-- from: encounters-combat/enc-consider-party -->
