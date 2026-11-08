# Songs and dances, part 1: the Play and Dance families

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b083` (Abilities/Songs [part 1/3: BardSkill .. Play_Reveille]) · 63 files · 7777 lines of Java.

Citations are relative to `com/planet_ink/coffee_mud/`, as in the brief. Catalogue rows give the class and file name, which the brief allows in place of a line number.

## Summary

A bard's song or dance is a timed, room-scoped performance. `Play` (`Abilities/Songs/Play.java:37`) and `Dance` (`Abilities/Songs/Dance.java:36`) are the two bases. Each applies a copy of the performance to every creature in a ring of rooms around the performer, and each listener's copy then ticks, changes stats, heals, hurts or ends by itself. Most of the 59 subclasses in this bucket are small: they override a few hooks (stat changes, a tick effect, a message veto), so the bucket is mainly a catalogue of data. Every effect scales off the performer's Charisma, Intelligence or Strength through one shared formula. Replaying a song that is already playing widens its reach one room ring at a time, and listeners keep the effect for a grace period after the performer stops. For a single-player Python game the reusable parts are the persistent-or-one-shot split, the replay-to-extend scope rule, the stat formula and the effect catalogue. The sexual-social and drink-language content is optional flavour that can be dropped.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Abilities/Songs/BardSkill.java` | Base for 33 bard skill classes; leather-armour fumble check | 95 |
| `Abilities/Songs/Play.java` | Base for all songs: instrument choice, room scope and depth, listener effects, tick, grace period | 763 |
| `Abilities/Songs/Dance.java` | Base for all dances: the same pattern with visibility instead of hearing, plus movement upkeep | 601 |
| `Abilities/Songs/Play_Instrument.java` | Base for the 11 instrument songs: non-persistent, casts a mimic spell on each listener | 154 |
| `Abilities/Songs/Dance_Swords.java` | Dance that animates floor swords, which then attack the performer's victim | 296 |
| `Abilities/Songs/Dance_Square.java` | Malicious dance; the performer's speech becomes orders for dancers | 215 |
| `Abilities/Songs/Play_Dirge.java` | Plays over a corpse: destroys it and awards XP to listeners | 184 |
| `Abilities/Songs/Play_Reveille.java` | One-shot wake-up for the room and adjacent rooms; no lasting effect | 182 |
| `Abilities/Songs/Dance_Polka.java` | Malicious dance: staggering, drunkenness, misdirected actions | 172 |
| `Abilities/Songs/Play_Blues.java` | Malicious, not aggressive: refuses sex socials, reduces hunger, disease roll | 141 |
| `Abilities/Songs/Play_Lullabies.java` | Malicious, not aggressive: per-tick chance to fall asleep | 129 |
| `Abilities/Songs/Play_Ballad.java` | Social hook into the Pregnancy ability; mind-save and attack bonuses | 132 |
| `Abilities/Songs/Dance_Cotillon.java` | Hands the performer's opponent to the next dancer in turn | 129 |
| `Abilities/Songs/Dance_Manipuri.java` | Room-entry grace period against non-combat malice | 132 |
| `Abilities/Songs/Dance_Basse.java` | Chance-based refusal of malicious actions from non-combatants | 104 |
| `Abilities/Songs/Dance_Swing.java` | Parries one melee attack per mob round | 118 |

The rest of the 63 files are in the catalogues under "Content Worth Mining".

## How it works

### Shared base rules

**BardSkill.** `BardSkill.java:36` extends StdAbility. It sets `abstractQuality()` to INDIFFERENT (`BardSkill.java:53-56`), `classificationCode()` to `ACODE_SKILL` (`:59-62`) and `canTargetCode()` to `CAN_MOBS` (`:71-74`). Its `invoke` makes a 50% armour fumble roll for a player whose armour fails `CMLib.utensils().armorCheck(mob, CharClass.ARMOR_LEATHER)` (`:82-92`), with the message "fumble(s) @x1 due to <S-HIS-HER> clumsy armor!". Play and Dance do not extend BardSkill. They repeat the same check (`Play.java:633-643`, `Dance.java:475-485`).

**Classification and usage.** Play is `ACODE_SONG | DOMAIN_PLAYING` with usage MOVEMENT and MANA (`Play.java:101-110`). Dance is `ACODE_SONG | DOMAIN_DANCING` with usage MOVEMENT only (`Dance.java:77-87`). The trigger words are PLAY, PL, PLA (`Play.java:71`) and DANCE, DA (`Dance.java:70`). No subclass overrides `triggerStrings`.

**Quality.** `Abilities/interfaces/Ability.java:1083-1093` gives MALICIOUS=0, INDIFFERENT=1, OK_SELF=2, OK_OTHERS=3, BENEFICIAL_SELF=4 and BENEFICIAL_OTHERS=5. Quality decides whether a performance is hostile. A MALICIOUS song uses mind-attack messages on other creatures (`Play.java:191-194`, `Dance.java:169-172`) and marks messages to others as MASK_MALICIOUS (`Play.java:717-718`, `Dance.java:550-553`).

**Stat formulas.** The bonus and level formulas are the same in both families, with one stat swapped. `MAX` is `CMProps.Int.BASEMAXSTAT`, whose default is 18 (`core/CMProps.java:3094`).

| Quantity | Play (`Play.java`) | Dance (`Dance.java`) |
|---|---|---|
| `statBonusPct()` | ((CHA+3)/MAX + (INT+3)/MAX + 2×instrument ability/MAX) / 2, lines 151-161 | ((CHA+3)/MAX + (STR+3)/MAX) / 2, lines 144-152 |
| `avgStat()` | round(((CHA+3) + (INT+3)) / 2 + instrument ability), lines 163-173 | round(((CHA+3) + (STR+3)) / 2), lines 154-162 |
| `adjustedLevel()` | base + (CHA-10)/4 + (INT-10)/5 + instrument ability, lines 224-235 | base + (CHA-10)/4 + (STR-10)/5, lines 203-211 |

Java integer division truncates toward zero. With CHA and INT both 10 and no instrument, `statBonusPct()` is about 0.72 and `avgStat()` is 13.

Expertise terms come from StdAbility: XLEVEL (`StdAbility.java:444-446`), XTIME (`:464-466`) and XMAXRANGE (`:459-461`). `maxRange()` is `adjustedMaxInvokerRange(2)` (`Play.java:176-179`), which adds ceil(2 × 0.2 × XMAXRANGE) (`StdAbility.java:482-489`).

**Proficiency.** Every invoke calls `proficiencyCheck(mob, 0, auto)` (`Play.java:665`, `Dance.java:505`). The routine at `StdAbility.java:1441-1484` takes the ability's proficiency, adds 2 × XLEVEL, clamps the result to 5..95 unless the DIS955RULE switch is disabled, then adds XLEVEL/2. A non-negative adjustment is added to the chance. A negative one is a separate gate that fails when d100 > 100 + adjustment. The check passes when d100 is below the chance. d100 comes from `Libraries/Dice.java:409-412`, which returns 1..100. `roll(n, die, mod)` sums n dice of 1..die and adds mod (`Libraries/Dice.java:398-406`).

### Data model

Play fields (`Play.java:79-85`): `instrument` (the instrument in use), `timeOut` (the listener's grace deadline in milliseconds), `commonRoomSet` (rooms in scope), `originRoom` (where the performance started), `playDepth` (ring depth) and `bonusCache` (a cached stat pair, cleared by `setInvoker` and `setAffectedOne` at `Play.java:118-129`). `invoker` comes from StdAbility.

Dance fields (`Dance.java:95-101`) are the same, with `danceDepth` in place of `playDepth`. `invokerManaCost` also lives here, but it actually holds a movement cost (see Problems).

Each listener receives a `copyOf()` of the performance through `addEffect` (`Play.java:694`, `:738-744`; `Dance.java:538`, `:581-585`). Per-listener state sits on that copy, for example `asleep` in `Play_Lullabies.java:63` and `directionCode` in `Play_Retreat.java:80`.

### The performance pipeline (Play.invoke)

`Play.invoke` (`Play.java:587-762`) is the template. `Dance.invoke` (`Dance.java:469-600`) follows the same steps.

1. **Instrument choice** (skipped for auto-casts). A riding instrument is used first, then the first carried instrument that is held or worn properly (`Play.java:593-619`, `usingInstrument` at `:256-267`). With none: "You need an instrument!" (`:622`). With the wrong type: "This song can only be played on <type>." (`:628`).
2. **Armour fumble** (`:633-643`), 50% chance when armour fails the leather check.
3. **`super.invoke`**, then the performer is granted `Play_Break` once, at proficiency 100 (`:648-657`).
4. **Early exit** when `skipStandardSongInvoke()` is true (`:659-660`). Several subclasses use this and do their own work.
5. **Awake and unbound** check for non-auto casts (`:662-663`).
6. **Proficiency and depth.** `proficiencyCheck` (`:665`). `replay` is true if the performer already has this song (`:666`). `newDepth` is computed (`:667`). `unPlayAll` stops every song the performer has running, including this one (`:668`).
7. **Announcement** (`:670-686`). The origin room and scope are set and the depth stored. The text is "begin(s) to play", "extend(s) the @x1`s range" if the ring grew, or "start(s) playing ... again".
8. **Per room in scope** (`:687-756`). The room-level message is vetoable (`R.okMessage`, `:692`). `sendMsgAndGetTargets` (`:556-580`) sends it to the room, or to others when the room is not the origin. It then finds the targets by briefly moving the performer into that room (`:696`). For each target:
   - If the target already has the song, only its depth is updated (`:720-722`).
   - Otherwise, if `canBeHeardSpeakingBy(invoker, target)` holds (`:724`), a somatic message and, for mind-attack songs, a mind message are both vetoable (`:726-730`). The effect is then applied: a persistent song adds a copy (`:738-744`), and a non-persistent song calls `inpersistentAffect` (`:746-747`).
9. **Failure**: "<S-NAME> hit(s) a foul note." (`:759`).

Dance differs in two places. Listeners must be able to see the dancer, not hear them (`Dance.java:565-566`), and the messages are different (`:518-524`).

### Scope and depth

- **Depth** (`Play.java:476-489`). If the performer already has this song running, depth is the previous depth plus one, capped at `getXMAXRANGELevel()/2`. The comment "decreased because fireball" marks the cap (`:482`). Otherwise depth is 0. So the first performance covers only the performer's room, and each replay while it plays adds one ring, up to half the XMAXRANGE level. Dance works the same way (`Dance.java:331-344`).
- **Scope** (`Play.java:491-510`). Depth 0 is the room alone. Otherwise `CMLib.tracking().getRadiantRooms(...)` is called with the flags OPENONLY, AREAONLY and NOAIR (`Play.java:95-98`). A song therefore stays in its area and does not cross closed exits or air rooms. Dance builds the same flags inline (`Dance.java:358-362`).
- **Recompute.** When the performer moves, the listener's scope is rebuilt on the next tick (`Play.java:276-284`, `Dance.java:221-227`).

### Listener effects: tick, grace period and ending

- **Tick** (`Play.java:269-331`). Each listener's copy runs `tick` every `TIME_TICK` milliseconds, which is 4000 by default (`core/CMProps.java:681`). Three things happen:
  - Malicious, aggressive songs make idle monster listeners walk back to the origin, or attack the performer when the performer is in the same room and visible (`Play.java:286-306`). Songs with `maliciousButNotAggressiveFlag()` skip this.
  - The effect ends when the performer no longer has it, when the listener is outside scope (`:309-320`), or when the performer can no longer be heard or is not awake (`:325-329`). `skipStandardSongTick()` returns early (`:322-323`).
- **Grace period** (`Play.java:401-430`, `unPlayMe`). On the first tick after the performer stops, a listener's `timeOut` is set to now plus `TIME_TICK × XTIME(performer)` (`:417-420`). The effect is removed when that deadline passes. The performer's own copy has no grace, because the multiplier is 0 when the invoker is the mob itself (`:420`).
- **Instrument stop** (`Play.java:356-384`). The performer stops if they speak while an instrument is worn at the mouth (`:365-373`), or if they remove, wear or wield the held instrument (`:374-382`).
- **Dance tick and upkeep** are covered below.

### Persistent and one-shot performances

- `persistentSong()` defaults to true (`Play.java:201-204`). A song that returns false calls `inpersistentAffect(mob)` once per listener each time it is played (`Play.java:746-747`). The non-persistent songs are the instrument family (`Play_Instrument.java:131-135`), Play_Charge (`:63-67`), Play_Retreat (`:63-67`), Play_Harmony (`:64-67`) and Play_Dirge (`:63-67`).
- Play_Reveille has no lasting effect at all. Its `invoke` loops over the rooms itself and never calls `addEffect` (`Play_Reveille.java:113-175`).
- Dances are always persistent. Dance has no `inpersistentAffect` path.

### Malice, aggression and resistance

- **Hostile marking.** Malicious performances mark messages to other creatures as MASK_MALICIOUS (`Play.java:717-718`, `Dance.java:550-553`). Where `mindAttack()` applies, a non-self target gets a TYP_MIND message (`Play.java:728-729`, `Dance.java:570-571`). Dance_Square does the same for non-group members (`Dance_Square.java:187-188`).
- **No saving throws.** A grep of the folder for `savingThrow`, `saveThrow`, `resist` and `rollSave` found nothing. Resistance comes from veto: a target's own `okMessage` override refuses a hostile message. Examples are Dance_Basse (`Dance_Basse.java:64-94`), Dance_Manipuri (`Dance_Manipuri.java:85-110`), Dance_Morris (`Dance_Morris.java:76-96`, which rolls d100 against the target's SAVE_MIND on weapon attacks), Dance_Swing (`Dance_Swing.java:74-112`) and Dance_Polka (`Dance_Polka.java:128-171`, which turns the target's speech into drunkenness).
- **Malicious but not aggressive.** Play_Blues (`Play_Blues.java:57-61`) and Play_Lullabies (`Play_Lullabies.java:57-61`) return true from `maliciousButNotAggressiveFlag()`. They never make monsters attack, so they stay on the listener.

### Instrument songs (Play_Instrument)

- **Instrument type.** `requiredInstrumentType()` returns WOODS by default (`Play_Instrument.java:53-57`). Each subclass overrides it, for example HARPS in `Play_Harps.java:53-57`. The type comes from the enum at `Items/interfaces/MusicalInstrument.java:49`, and is checked in `Play.getInstrument` (`Play.java:466-471`) and in `invoke` (`:625-630`).
- **One-shot cast.** `persistentSong()` is false (`Play_Instrument.java:131-135`), so `inpersistentAffect` runs for each listener (`Play_Instrument.java:84-108`). It casts the mimic spell on the listener as an auto-cast by the performer, at level `adjustedLevel(invoker, 0)` (`:93`). A malicious mimic spell is skipped when the listener is the performer (`:88`).
- **NPC retaliation.** If the mimic spell is malicious and the listener is a monster that is idle, not in combat, not following, not tracking, not dead and not carrying riders, the listener invokes its own `Thief_Assassinate` against the performer (`Play_Instrument.java:94-106`).
- **Quality** is the mimic spell's quality, or BENEFICIAL_OTHERS when there is none (`Play_Instrument.java:123-129`).
- **Depth** is overridden. A replay within 30 seconds adds one ring, capped at XMAXRANGE/2. Otherwise depth resets to 0 (`Play_Instrument.java:67-81`, window at `:70`).
- **Caching.** Each subclass keeps its mimic spell in a `private static` field, such as `theSpell` in `Play_Harps.java:65-76`, so the lookup happens once per class.

### Dance upkeep and end conditions

- **End conditions** (`Dance.java:268-274`). For each holder, the dance ends if the performer is sitting, if the holder is not awake, if the holder is riding a mob, or if `CMLib.flags().canBeSeenBy(invoker, mob)` fails.
- **Movement upkeep** (`Dance.java:276-283`). Each tick every holder loses `invokerManaCost / 15` movement. The cost is `usageCost(invoker, false)[1]`, and index 1 is `USAGEINDEX_MOVEMENT` (`Abilities/interfaces/Ability.java:660`). When `adjMovement` returns false, which happens when movement would drop below 0 (`Common/DefaultCharState.java:339-351`), the holder sees "The dancing exhausts you." and every dance on them stops (`:280-281`).
- **Armour** (`Dance.java:439-449`). The performer putting on armour stops the dance.
- **Dance_Stop** (`Dance_Stop.java:76-95`) stops all of the dancer's dances silently (`unDanceAll` with noEcho). It says "<S-NAME> stop(s) dancing." or, when auto, "Rest.".

### Special mechanics

- **Play_Dirge** (`Play_Dirge.java:104-183`). The target must be a `DeadBody` whose secret identity does not contain "FAKE" (`:112`). A player corpse with contents is refused (`:117-121`). Each listener gets 10 XP (`:162`), reduced to 0 if their level is more than the EXPRATE setting above the body's level (`:163-166`), through `CMLib.leveler().postExperience` (`:171`). The body is then destroyed (`:175`).
- **Play_Reveille** (`Play_Reveille.java:86-181`). Every creature in the room, and in each adjacent room outside scope, is targeted. A sleeping listener gets a forced WAKE (`:160-162`). If the listener wakes, the Searching ability is invoked on them (`:163-168`).
- **Play_Charge** (`Play_Charge.java:71-79`, `:94-107`). Each listener gets Fighter_Charge with its ability code set to 4 × XLEVEL (`:76`) and invoked as an auto-cast (`:77`). With no argument and no combat, the performer is told "Play charge at whom?" (`:96-99`). With no argument in combat, the current victim is used (`:101-102`).
- **Play_Retreat** (`Play_Retreat.java:82-139`). Listeners make peace and walk one step in the chosen direction. With no direction, a random open exit is used, and UP is the last resort (`:116-130`). "NOWHERE" skips the direction.
- **Play_Harmony** (`Play_Harmony.java:76-82`). Makes peace for each listener and for the listener's victim.
- **Dance_Swords** (`Dance_Swords.java:128-219`). The dance is applied to floor swords that are CLASS_SWORD and neither flying nor already dancing (`:137-163`), up to 3 + XLEVEL/2 (`:157`). A dancing sword gets IS_FLYING (`:120-125`). While the performer is in combat, the sword attacks the victim with `rollToHit` and damage of 5 × pct plus bonuses (`:176-186`). GET, PUSH, PULL or REMOVE on the sword stops it (`:88-100`).
- **Dance_Square** (`Dance_Square.java:87-122`). While it runs, the performer's speech becomes orders for dancers in scope. A dancer obeys when d100 > chance, where chance = dancer level − 10 × (round(performer level × pct) + XLEVEL) (`:99-102`). The order is resolved with `CMLib.english().findCommand` and `enqueCommand` (`:112-114`). Group members are exempt from the malicious flag (`:154`, `:174-175`).
- **Dance_Cotillon** (`Dance_Cotillon.java:84`). While the performer is in combat, the performer's opponent is re-targeted to the next in-combat dancer in the room, cycling through the room. The message is "<S-NAME> dance(s) into <O-YOUPOSS> way." The cast needs combat and a group of at least 2 (`:72-78`).
- **Dance_Swing** (`Dance_Swing.java:66-112`). Once per mob round, reset at `:69`, a melee attack at range 0 is parried. The weapon must not be flail, natural, ranged or thrown. The parry is `proficiencyCheck(null, round(DEX × pct) + 2×XLEVEL − 70, false)` (`:102`). A parry the room permits sets `doneThisRound` and vetoes the attack (`:107-109`).
- **Dance_Basse** (`Dance_Basse.java:64-94`). A malicious action aimed at a holder who is not in combat, from a source in the same room who is not already the holder's victim, is refused when d100 > (holder level / pct) − 10 × (source level + XLEVEL) (`:78-79`). The source hears "You are too much in awe of <target>" (`:81`), and if the holder was fighting the source, the fight ends (`:82-86`).
- **Dance_Manipuri** (`Dance_Manipuri.java:67-110`). A counter is set to 3 + XLEVEL when a holder enters a new room, then decremented each tick. While it is positive, a malicious action from a source who is not in combat and is not already fighting the holder is refused with "You feel like letting <target> be for awhile." The source's fight is ended.
- **Dance_Polka** (`Dance_Polka.java:58-171`). Listeners lose attack (− adjusted level, `:73`) and DEX (− round(2 + pct) + XLEVEL, `:85-86`). Each tick there is a 25% chance of a stagger message keyed to alignment (`:107-121`). A listener's speech makes the speaker drunk through the Drunken language ability (`:152-157`). A listener's action aimed at a creature is retargeted to a random creature in the room (`:166-168`).
- **Dance_Flamenco** (`Dance_Flamenco.java:58-90`). Each tick the performer deals bursting damage to each listener, roll(adjusted level, 8) + roll(group − 1, 8) (`:69-70`). The result is divided by 4 when the listener is outside the performer's room, and divided by 4 again when they are not adjacent. It floors at 1 (`:74-88`). The damage is `postDamage` (`:90`).
- **Dance_Tarantella** (`Dance_Tarantella.java:73-89`). Every tick from `15 − XLEVEL` onward, all effects flagged ACODE_POISON are removed from the dancer (`:82-88`).
- **Dance_Waltz** (`Dance_Waltz.java:60-79`). The count of random base-stat boosts is `(qualifying class level + 3 × XLEVEL + 1) / 9 + 1` (`:66-68`). Each boost is 2 + round(2 × pct) (`:70-72`) and goes to a random base stat. The random table is cached per effect and reset at invoke (`:57`, `:80`).
- **Dance_Macabre** (`Dance_Macabre.java:66-86`). While the dancer is hidden, damage rises by adjusted level / 2 and attack by 3 × adjusted level. Otherwise, while in combat with a victim who is also in combat, damage rises by adjusted level / 4 and attack by adjusted level. The tick (`:86`) recalculates stats when the dancer hides or appears.
- **Dance_Foxtrot** (`Dance_Foxtrot.java:61-93`). Each tick it restores movement by `invokerManaCost/15 + increment` and mana by `increment` (`:85-86`). The increment grows by one every third tick while it is at most 1 + round(adjusted level / 2), so it can reach that value plus one.
- **Dance_CanCan** (`Dance_CanCan.java:60-80`). In combat, each tick it casts Fighter_Kick at the victim through a `static` ability shared by all instances (`:57`, `:71-77`), and restores mana afterward.

### Messages shown to players (samples)

- Performance start: "<S-NAME> begin(s) to play @x1 on @x2." (`Play.java:679`) and "<S-NAME> begin(s) to dance the @x1." (`Dance.java:518`). Replays use "extend(s) the @x1`s range." (`Play.java:683`, `Dance.java:522`) or "start(s) the @x1 over again." (`Dance.java:524`).
- Audience: "You hear @x1 being played @x2!" (`Play.java:549`), or "...being played nearby!" (`:551`). Dance audiences get "You see the @x1 being performed @x2!" (`Dance.java:403`).
- Failures: "You need an instrument!" (`Play.java:622`), "<S-NAME> hit(s) a foul note." (`Play.java:759`), "<S-NAME> make(s) a false step." (`Dance.java:597`), "The dancing exhausts you." (`Dance.java:280`).
- Stops: "<S-NAME> stop(s) playing." (`Play_Break.java:91`), "<S-NAME> stop(s) dancing." (`Dance_Stop.java:92`).
- Effects: Lullabies "fall(s) asleep." and "wake(s) up." (`Play_Lullabies.java:100`, `:104`). Swing "parr(ys) @x1 attack from <T-NAME>!" (`Dance_Swing.java:104`). Dirge "@x1 fades away." (`Play_Dirge.java:174`). Morris "become(s) distracted." (`Dance_Morris.java:91`). Flamenco "The flamenco dance <DAMAGE> <T-NAME>!" (`Dance_Flamenco.java:90`).
- Polka stagger lines are keyed to alignment (`Dance_Polka.java:110-121`). Swords flavour lines include "is looking for trouble" and "dances around" (`Dance_Swords.java:194-207`).

### Interactions with other systems

- **Expertise** (StdAbility.java:444-466): XLEVEL, XTIME and XMAXRANGE scale most songs.
- **Tracking** (`Play.java:506`, `Play_Retreat.java:91`): `CMLib.tracking().getRadiantRooms` and `walk`.
- **Flags and state**: `CMLib.flags()` checks sleeping, hidden, visibility, hearing, mobility and tracking monsters. `mob.curState()` is used for hunger, mana and movement.
- **Combat**: `CMLib.combat().postDamage`, `postHealing`, `postAttack`, `rollToHit`, `adjustedDamage` and `adjustedAttackBonus`.
- **Leveling**: `CMLib.leveler().postExperience` (`Play_Dirge.java:171`).
- **Other abilities** (looked up with `CMClass.getAbility`): Fighter_Charge, Fighter_Kick, Pregnancy, Disease_Depression, Drunken, Searching, Thief_Assassinate, and the mimic spells. Each one exists in the tree (`Abilities/Misc/Pregnancy.java`, `Abilities/Diseases/Disease_Depression.java`, `Abilities/Languages/Drunken.java`, `Abilities/Common/Searching.java`, `Abilities/Thief/Thief_Assassinate.java`, `Abilities/Fighter/Fighter_Charge.java`, `Abilities/Fighter/Fighter_Kick.java`).
- **Commands**: `mob.doCommand(CMParms.parse("MATE ..."), METAFLAG_FORCED)` (`Play_LoveSong.java:99`, `Dance_Salsa.java:91`), `CMLib.commands().postRemove` for waist and leg garments (`Play_LoveSong.java:95-98`), and `CMLib.english().findCommand` for orders (`Dance_Square.java:112`).
- **Socials**: matched by tool name, "MATE <T-NAME>" and "SEX <T-NAME>" (`Play_Blues.java:87-90`, `Play_Ballad.java:75-78`).
- **Messages**: each step is a `CMMsg` that passes `okMessage` and then `executeMsg`. See `Common/interfaces/CMMsg.java` (the MASK_MALICIOUS bit is at `:960`).

## Content worth mining

### Songs (Play_*, 18 non-instrument members)

Files are in `Abilities/Songs/`. Quality is `abstractQuality()`.

| Class (file) | Name | Quality | What it does |
|---|---|---|---|
| Play_Accompaniment | Accompaniment | BENEFICIAL_OTHERS | Other bards in the group gain class and physical level of max(1, adj/10); cast needs 2+ group members |
| Play_Background | Background | BENEFICIAL_OTHERS | All saving throws + adj/3 + avgStat/2 |
| Play_Ballad | Ballad | BENEFICIAL_OTHERS | SAVE_MIND + adj; attack + avgStat + adj; a qualifying social can trigger the Pregnancy ability (adult content, see Problems) |
| Play_Battlehymn | Battlehymn | BENEFICIAL_OTHERS | Damage + 1 + damage × adj/100; ends after 5 + XTIME non-combat ticks, counted over the whole effect |
| Play_Blues | Blues | MALICIOUS, not aggressive | Refuses sex socials aimed at others; hunger −2 per tick; disease roll (Disease_Depression); attack and SAVE_JUSTICE penalties; FLAG_MINDALTERING |
| Play_Break | Break | INDIFFERENT | Ends the performer's songs at once (own copy removed directly; listeners' copies fall away on their next tick); granted once, when a known song is first played |
| Play_Carol | Carol | BENEFICIAL_OTHERS | SAVE_MIND + 2×adj + avgStat |
| Play_Charge | Charge! | BENEFICIAL_OTHERS | Non-persistent; each listener gets Fighter_Charge |
| Play_Dirge | Dirge | BENEFICIAL_OTHERS | Plays over a corpse; 10 XP per listener; body destroyed |
| Play_Ditty | Ditty | BENEFICIAL_OTHERS | Heals each holder 4 + round(adj/4) per tick, credited to the performer; FLAG_HEALINGMAGIC |
| Play_Harmony | Harmony | BENEFICIAL_OTHERS | Non-persistent; makes peace for listener and victim; castingQuality indifferent in combat |
| Play_LoveSong | Love Song | BENEFICIAL_OTHERS | CHA + round(4 × pct) + XLEVEL; periodic flavour lines; 1% per tick forces a MATE social |
| Play_Lullabies | Lullabies | MALICIOUS, not aggressive | Per-tick sleep if d100 > round(50/pct) − 2×XLEVEL; blocks stand and sit while asleep |
| Play_March | March | BENEFICIAL_OTHERS | Movement + adj/4 per tick; no cast on targets with half or more movement |
| Play_Melody | Melody | MALICIOUS | SAVE_MIND − (avgStat + 2×adj) for listeners |
| Play_Mystical | Mystical | BENEFICIAL_OTHERS | Keeps non-malicious spells in the room from expiring (tick-down 1 becomes 2) |
| Play_Reveille | Reveille | BENEFICIAL_OTHERS | One-shot; wakes sleepers in the room and adjacent rooms, then Searching |
| Play_Retreat | Retreat | BENEFICIAL_OTHERS | Non-persistent; listeners make peace and flee to a chosen or random exit |

### Instrument songs (Play_Instrument base and 11 subclasses)

The mimic spell is the first spell cast on each listener when the instrument is played. The quality of each song is the mimic spell's quality, which was not verified for each spell.

| Class (file) | Instrument type | Mimic spell |
|---|---|---|
| Play_Instrument (base) | WOODS by default | none |
| Play_Clarinets | CLARINETS | Spell_ShockingGrasp |
| Play_Cymbals | CYMBALS | Spell_Knock |
| Play_Drums | DRUMS | Spell_Deafness |
| Play_Flutes | FLUTES | Chant_CharmAnimal |
| Play_Guitars | GUITARS | Spell_Fireball |
| Play_Harmonicas | HARMONICAS | Spell_Awe |
| Play_Harps | HARPS | Spell_Friends |
| Play_Horns | HORNS | Spell_FaerieFire |
| Play_Oboes | OBOES | Spell_AcidArrow |
| Play_Organs | ORGANS | Prayer_ProtectHealth |
| Play_Pianos | PIANOS | Spell_Feeblemind |

### Dances (Dance_*, 30 members)

| Class (file) | Name | Quality | What it does |
|---|---|---|---|
| Dance_Ballet | Ballet | BENEFICIAL_SELF | DEX + adj/3 for every holder |
| Dance_Basse | Basse | BENEFICIAL_SELF | Refuses malicious actions from non-combatants aimed at a holder who is not in combat, by chance |
| Dance_Butoh | Butoh | BENEFICIAL_SELF | Mana + adj per tick; castingQuality indifferent on targets with half mana |
| Dance_Butterfly | Butterfly | BENEFICIAL_OTHERS | Every holder gains IS_FLYING |
| Dance_CanCan | Can-Can | BENEFICIAL_OTHERS | In combat, casts Fighter_Kick each tick (shared static ability) |
| Dance_Capoeira | Capoeira | BENEFICIAL_OTHERS | Unarmed only: attack + avgStat + 2×adj, damage + adj/3 |
| Dance_Clog | Clog Dance | MALICIOUS | Listeners: attack − (avgStat + 2×adj), DEX / (1 + pct) |
| Dance_Cotillon | Cotillon | BENEFICIAL_OTHERS | Opponent handed to the next in-combat dancer |
| Dance_Courante | Courante | BENEFICIAL_OTHERS | Speed + the configured speed adjustment × (pct + 0.05×XLEVEL) (`core/CMProps.java:1156`) |
| Dance_Flamenco | Flamenco | MALICIOUS | Per-tick bursting damage, reduced by distance |
| Dance_Foxtrot | Foxtrot | BENEFICIAL_OTHERS | Restores movement and mana each tick; increment grows every third tick |
| Dance_Grass | Grass | BENEFICIAL_SELF | Every holder: sets IS_BONUS; armour − 2×adj (lower is better, see below) |
| Dance_Jingledress | Jingledress | BENEFICIAL_SELF | Heals each holder roll(2, adj, 4) per tick, credited to the performer; FLAG_HEALINGMAGIC |
| Dance_Jitterbug | Jitterbug | BENEFICIAL_SELF | Applies to every holder, dancer included: attack − adj; DEX / 3; five elemental saves + 2×adj |
| Dance_Macabre | Macabre | BENEFICIAL_OTHERS | Hidden dancer: damage + adj/2, attack + 3×adj; in combat: damage + adj/4, attack + adj |
| Dance_Manipuri | Manipuri | BENEFICIAL_SELF | Holder refuses malicious non-combat actions for 3 + XLEVEL ticks after entering a room |
| Dance_Morris | Morris | MALICIOUS | Non-dancers: armour + 2×adj, attack − 2×adj; dancer's weapon attacks are sometimes distracted |
| Dance_Musette | Musette | MALICIOUS | Non-dancers: speed / (1 + pct + 0.30×XLEVEL) |
| Dance_Polka | Polka | MALICIOUS | Listeners: attack − adj, DEX loss; stagger; their speech makes them drunk; their actions are misdirected |
| Dance_RagsSharqi | Rags Sharqi | BENEFICIAL_SELF | Hit points + (adj + 10) × 5 per recovery; SAVE_DISEASE + 2×adj |
| Dance_Salsa | Salsa | BENEFICIAL_SELF | CHA + round(4 × pct) + XLEVEL; periodic flavour; 1% per tick forces a MATE social |
| Dance_Square | Square | MALICIOUS | Dancers in scope; performer's speech becomes orders |
| Dance_Stop | Stop | INDIFFERENT | Ends all the performer's dances |
| Dance_Swing | Swing | BENEFICIAL_OTHERS | Parries one melee attack per mob round |
| Dance_Swords | Swords | OK_OTHERS | Floor swords dance and attack the performer's victim; cap 3 + XLEVEL/2 (order-dependent, see Problems) |
| Dance_Tango | Tango | BENEFICIAL_SELF | CHA + round(10 × pct) + XLEVEL; castingQuality indifferent in combat |
| Dance_Tap | Tap | MALICIOUS | Listeners lose adj/3 movement and adj mana per tick |
| Dance_Tarantella | Tarantella | BENEFICIAL_SELF | SAVE_POISON + 2×adj; cures poison effects from tick 15 − XLEVEL onward |
| Dance_Waltz | Waltz | BENEFICIAL_SELF | Random base-stat boosts, count from class level |
| Dance_War | War | BENEFICIAL_OTHERS | Mana forced to 0 each tick; attack + 2×adj, armour − 2×adj, damage + adj/3; +2 to every base stat |

Armour is an AC-style value where a lower number is better: `MUDFight.rollToHit` takes `myArmor = -defence` (`Libraries/MUDFight.java:409-424`). That explains why Dance_Morris raises armour on its victims and Dance_War lowers it on the dancer.

### Message and flavour samples

- Dance_Swords flavour: "twitches a bit", "is looking for trouble", "practices its moves", "makes a few fake attacks", "dances around" (`Dance_Swords.java:194-207`).
- Dance_Polka stagger: one line per alignment, from "making ugly faces" to "trying to hug everyone" (`Dance_Polka.java:110-121`).
- Dance_Basse: "You are too much in awe of <target>" (`Dance_Basse.java:81`). Dance_Manipuri: "You feel like letting <target> be for awhile." (`Dance_Manipuri.java:85-110`).

## Notable design ideas

1. **Replay to extend.** Playing the same song again while it runs widens its reach one ring at a time, up to a cap set by expertise (`Play.java:476-489`, message at `:683`). The player gets a reason to replay and a visible result.
2. **Persistent or one-shot.** One flag decides whether a performance is a lasting per-listener effect or a single action (`Play.java:738-747`). The same engine covers a ticking buff (Background), a one-shot wake (Reveille) and a spell cast (the instrument songs).
3. **Instrument as a spell delivery system.** An instrument plays a mimic spell on each listener (`Play_Instrument.java:84-108`). Eleven instruments map to eleven existing spells, so a catalogue of spells becomes a catalogue of bard songs.
4. **Listener grace.** When the performer stops, the effect lingers for a number of ticks set by the performer's expertise (`Play.java:401-430`).
5. **Veto-based defence.** Dances like Basse and Manipuri defend by refusing hostile messages, so there is no central saving-throw system to tune (`Dance_Basse.java:64-94`, `Dance_Manipuri.java:85-110`).
6. **Dancing weapons.** The dance effect is applied to floor items, not only creatures (`Dance_Swords.java:137-163`), and IS_FLYING marks the item as animated (`:120-125`).
7. **Speech as commands.** Dance_Square turns the performer's speech into orders, with obedience falling as the listener's level rises above the performer's (`Dance_Square.java:99-114`).
8. **Redirect rather than block.** Cotillon moves the opponent onto another dancer (`Dance_Cotillon.java:84`), and Polka sends a listener's action to a random target (`Dance_Polka.java:166-168`). Both change the target without stopping the action.
9. **Upkeep as movement.** A dance costs movement each tick, and running out of movement stops it with a message (`Dance.java:276-283`). This makes long dances a tradeoff without a separate resource.
10. **One stat formula for everything.** `statBonusPct` and `adjustedLevel` read the performer's stats in one place (`Play.java:151-235`), so tuning one number changes the whole family.

## Problems and cruft

Verified by reading the code unless marked as unverified.

1. **Misnamed field.** `Dance.invokerManaCost` holds the movement cost, taken from index 1 of `usageCost` (`Dance.java:276-277`; `Abilities/interfaces/Ability.java:660`). The name says mana. Dance_Foxtrot uses it the same way (`Dance_Foxtrot.java:85`).
2. **Proficiency is always 100 for granted abilities.** `Play_Break` and `Dance_Stop` override `setProficiency` to force 100 (`Play_Break.java:69-73`, `Dance_Stop.java:69-73`). The later `setProficiency(proficiency)` calls in the grant code (`Play.java:654`, `Dance.java:494`) therefore do nothing.
3. **Order-dependent sword cap.** `Dance_Swords.java:137-159` counts already-dancing swords only until it reaches the first free sword, then breaks. If a free sword comes first in the room's item list, the cap of 3 + XLEVEL/2 is never checked.
4. **Redundant check.** `Dance_Swords.java:177` tests `!(affected instanceof Weapon)` after the same test at line 166.
5. **Counter never resets.** `Dance_Tarantella.java:57` and `:82` increment `ticks` without resetting it. After the threshold, poison is removed every tick, which is probably not the intent.
6. **No caster exemption in Jitterbug.** `Dance_Jitterbug.java:58-80` applies the penalties to every holder, including the dancer, even though its quality is BENEFICIAL_SELF. Whether this is intended is unverified.
7. **Shadowed variable.** In `Dance_Flamenco.java:68`, the local `invoker` hides the field. The check `if(invoker != null)` at line 71 is therefore always true. Line 70 calls `invoker()` on the field without a null check. Whether that can be null on this path is unverified.
8. **Unused and shared static state.** `Dance_Grass.java:57` declares `public static Ability kick` and never uses it. `Dance_CanCan.java:57` and `:71-77` share one static `kick` across every instance.
9. **Corpse destroyed once per room.** `Play_Dirge.java:174-175` shows the message and destroys the body inside the per-room loop, so it repeats for each room in scope.
10. **Direct stat mutation.** `Play_Lullabies.java:99` and `:105` change `mob.phyStats()` disposition directly, outside the affect layer. How that interacts with recalculation after the effect ends is unverified.
11. **Misleading name.** `Play_Background.java:63` stores avgStat/2 in a variable called `cha`.
12. **Empty branch.** `Play_March.java:67-70` tests a mob and does nothing.
13. **Swallowed exceptions.** `Play_LoveSong.java:122-124` catches and ignores all exceptions around the forced MATE.
14. **Class detection by string.** `Play_Accompaniment.java:61` and `:74` compare `baseClass()` to "Bard".
15. **Dead code.** `Play.invokerLevel()` (`Play.java:237-250`) has no callers in the tree (checked by grep).
16. **Copied comments.** "decreased because fireball" appears at `Play.java:482`, `Dance.java:337` and `Play_Instrument.java:72`. The comments "malicious ... must not affect the invoker" at `Play.java:709` and `Dance.java:547` do not match the code around them.
17. **Inconsistent veto.** `Dance.java:572` vetoes both messages on `R`. `Play.java:730` vetoes the second on `R2`.
18. **Channel speech.** `Dance_Square.java:89-93` does not exclude channel messages, while `Play.java:366` does for the instrument stop. This may be intended.
19. **Null source location.** `Dance_Polka.java:166` calls `msg.source().location().fetchRandomInhabitant()` with no null check.
20. **Hard-coded numbers.** Lullabies uses 50 (`Play_Lullabies.java:89`), LoveSong and Salsa require a partner whose CHA save is above 14 (`Play_LoveSong.java:83`, `Dance_Salsa.java:77`), Swing uses 70 (`Dance_Swing.java:102`), and the instrument window is 30000 ms (`Play_Instrument.java:70`). None are configurable.
21. **Multiplayer assumptions.** Sex socials between eligible partners (Play_Ballad, Play_Blues, Play_LoveSong, Dance_Salsa), the Drunken language for speech (Dance_Polka), group membership as a protection (Dance_Square) and as a casting rule (Dance_Cotillon, Play_Accompaniment), and XP for listeners (Play_Dirge) all assume other players or NPCs with social lives. A single-player game has to decide which of these to keep.

## Takeaways for a Python SUD

- **Model a performance as data.** A `Performance` record with an id, a quality (MALICIOUS, INDIFFERENT, BENEFICIAL_SELF, BENEFICIAL_OTHERS), a `persistent` flag, an optional instrument type, a scope depth, and a list of effects is enough to cover most of this bucket. Store the numbers in JSON or YAML, not in classes.
- **Keep the stat formula, with Python rounding.** Use `stat_bonus = ((cha + 3) / MAX + (other + 3) / MAX) / 2` and `adjusted_level = base + (cha - 10) // 4 + (other - 10) // 5`. Java truncates toward zero, so use `int(a / b)` or `math.trunc` where the Java code truncates; `//` floors and gives different results for negative values.
- **Keep the proficiency rule.** `pct = clamp(prof + 2*xl, 5, 95) + xl // 2`, then succeed when `d100 < pct`. This is a short function (`StdAbility.java:1441-1484`).
- **Keep persistent and one-shot as one flag.** A persistent effect is a timed object on each creature in scope. A one-shot is a function called once. Reveille, Charge, Retreat and Harmony show that a single flag covers all of them.
- **Keep replay-to-extend.** Repeat the same song to grow its radius by one room, capped by a skill value. In Python this is a breadth-first search over the room graph limited by depth, restricted to open exits and the same area.
- **Use two-phase messages in plain Python.** Each action runs a veto step (any listener may refuse it) and then an apply step. A small list of callables per creature is enough. CMMsg's general structure is not needed.
- **Turn ticks into turns.** The 4-second tick (`core/CMProps.java:681`) becomes one game turn or a fixed number of seconds. Durations, the grace period (XTIME) and the depth ramp all become turn counts.
- **Borrow these effects first.** Lullabies (sleep chance), Retreat (flee), Ditty (heal), Jingledress (heal), Battlehymn (damage bonus with a non-combat timeout), Background (saves), Reveille (wake), Dirge (corpse XP), Cotillon (target rotation), Square (orders to companions), Basse and Manipuri (refusal), Swing (parry), Flamenco (distance-scaled damage) and Waltz (random stat boosts). Each one is a short class.
- **Drop or defer.** Sex socials and pregnancy (Ballad, Blues, LoveSong, Salsa), the Drunken language (Polka), and the sword-animation system (Swords, which needs a floor-item effect model) are costly for little gain in a single-player text game.
- **Fix the problems before porting.** Name the movement cost correctly, reset the Tarantella counter per cure, make the sword cap count every sword, and drop the static shared kick ability. Test each of these with a small Python unit test.

A possible shape:

```python
@dataclass
class Performance:
    id: str
    quality: Quality            # MALICIOUS, INDIFFERENT, BENEFICIAL_SELF, BENEFICIAL_OTHERS, ...
    persistent: bool = True
    instrument: InstrumentType | None = None
    stat_mods: dict[str, str] = field(default_factory=dict)  # e.g. {"save_mind": "-(avg + 2*level)"}
```

## Coverage

- **Read in full (28 files):** BardSkill, Play, Dance, Play_Instrument, Play_Dirge, Play_Reveille, Play_Break, Play_Accompaniment, Play_Background, Play_Blues, Play_Lullabies, Play_Ballad, Play_Charge, Play_Retreat, Play_LoveSong, Play_Melody, Play_Mystical, Play_March, Play_Harps, Play_Clarinets, Play_Ditty, Play_Battlehymn, Dance_Swords, Dance_Square, Dance_Polka, Dance_Salsa, Dance_Stop, Dance_Morris.
- **Read as method bodies, with header and boilerplate lines filtered out (26 files):** Play_Harmony, Play_Carol, Dance_Waltz, Dance_Macabre, Dance_Clog, Dance_Butterfly, Dance_RagsSharqi, Dance_Jitterbug, Dance_Tap, Dance_CanCan, Dance_Ballet, Dance_Manipuri, Dance_Capoeira, Dance_Jingledress, Dance_Musette, Dance_Grass, Dance_Tango, Dance_Courante, Dance_Cotillon, Dance_Swing, Dance_Basse, Dance_Foxtrot, Dance_War, Dance_Flamenco, Dance_Tarantella, Dance_Butoh. The filter removed `return Ability.QUALITY_*` lines, so the return values of castingQuality were checked with grep.
- **Skimmed for key fields (9 files):** Play_Cymbals, Play_Guitars, Play_Organs, Play_Oboes, Play_Harmonicas, Play_Pianos, Play_Drums, Play_Flutes, Play_Horns. Their ID, name, quality, instrument type and mimic spell were taken from grep output.
- **Skipped:** none.
- **Unverified:** the spell qualities behind the instrument songs; the effect on `mob.phyStats()` after Lullabies ends; the intent of Jitterbug's self-penalty; whether Flamenco's `invoker()` call can see a null; the tick order that Mystical depends on; the meaning of `PhyStats.IS_BONUS` (`Common/interfaces/PhyStats.java:565`); and the actual movement and mana costs from `usageCost`.
