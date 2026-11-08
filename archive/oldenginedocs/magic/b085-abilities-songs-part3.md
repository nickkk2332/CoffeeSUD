# Bard songs and stage skills (Abilities/Songs, part 3)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b085` (Abilities/Songs [part 3/3: Skill_Slapstick .. Song_Valor]) · 54 files · 7742 lines of Java.

Citations are relative to the CoffeeMud folder (`com/planet_ink/coffee_mud/`). The shorter form `Abilities/Songs/...` is used below. Line numbers were read from the source. Where a detail was not read, it is marked "unverified".

## Summary

CoffeeMud's bard magic is built around the `Song` class, a `StdAbility` that the performer keeps alive with a tick loop. A performance is copied onto every listener in a radius that grows each time the same song is repeated, and it ends when the performer speaks, stops, is incapacitated or moves out of range. The 43 `Song_*` classes are small effects on listeners, mostly stat, state, sense or quality modifiers scaled by the performer's Charisma. A few are custom behaviours (charm, babble, thanks, flight, rebirth, ode). The 10 `Skill_*` classes are bard skills: stage tricks, instrument and scroll magic, and Songcraft, which lets a bard learn spells cast in the room. The timed, area-scoped, performer-tied effect pattern and the Ode composition system are worth studying. Several bugs are listed under Problems and cruft.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Songs/Song.java` | Base class: invoke flow, room scope, listener copies, tick and unsing rules, speech interrupt, lyrics, stat scaling | 661 |
| `Abilities/Songs/Song_Ode.java` | Composes an "ode" about a target from observed actions, then sings its benefits | 510 |
| `Abilities/Songs/Song_Friendship.java` | Malicious charm: listeners start following the performer | 289 |
| `Abilities/Songs/Song_Babble.java` | Scrambles listeners' speech | 256 |
| `Abilities/Songs/Song_Thanks.java` | Grateful mobs say thanks and hand over coin | 239 |
| `Abilities/Songs/Song_EnchantInstrument.java` | Writes a known song into a magic instrument | 236 |
| `Abilities/Songs/Song_Saudade.java` | Timed homesick recall of listeners | 227 |
| `Abilities/Songs/Skill_Upstage.java` | Malicious stage move that switches the room's combatants to the performer as their victim | 200 |
| `Abilities/Songs/Skill_SongWrite.java` | Writes a known song onto a magic scroll | 190 |
| `Abilities/Songs/Song_Flight.java` | Walks listeners out through the first open exit | 179 |
| `Abilities/Songs/Song_RechargeInstrument.java` | Adds charges to a magic instrument | 175 |
| `Abilities/Songs/Song_RestoreMusic.java` | Restores or extends a music scroll | 169 |
| `Abilities/Songs/Skill_Slapstick.java` | Comic skill that drains mana | 165 |
| `Abilities/Songs/Song_Inebriation.java` | Drunkenness debuff with stagger messages | 165 |
| `Abilities/Songs/Skill_StrikeTheSet.java` | Hides the room's items and the room from sight | 156 |
| `Abilities/Songs/Skill_Struggle.java` | Breaking free of binds, with a strength boost | 156 |
| `Abilities/Songs/Song_ReadMusic.java` | Grants the Read Music ability (implements `Scroll.ScrollUsage`) | 147 |
| `Abilities/Songs/Skill_Warrants.java` | Lists outstanding legal warrants in the room | 144 |
| `Abilities/Songs/Skill_Songcraft.java` | Base for Chantcraft and Prayercraft; learns spells cast in the room | 140 |
| `Abilities/Songs/Song_Rebirth.java` | Resurrects player corpses in the room | 140 |
| `Abilities/Songs/Skill_Stability.java` | Blocks malicious movement-based abilities aimed at the holder | 126 |

The other 33 files are catalogued in "Content worth mining". They are the remaining `Song_*` effects and `Skill_*` skills, with the base-class hooks they override. Every file was read at least through its ID, quality, flags and hook methods.

## How it works

### 1. The performance lifecycle (Song base)

**Data model.** `Song` extends `StdAbility`. Its ID is `"Song"` and its classification is `ACODE_SONG | DOMAIN_SINGING` (`Abilities/Songs/Song.java:79-82`). Its trigger words are "SING" and "SI" (`:70`), and it targets mobs only (`:59-68`). Per-cast state is held in fields: `timeOut`, `commonRoomSet` (the rooms in scope), `originRoom`, `songDepth`, `lyricCtr`, `lyricPauseCtdn` (`:125-130`), and a cached `bonusCache` pair (`:132`, `:148-156`). The performer is `invoker`. Each listener receives its own copy of the song ability (`:608`, `:640-646`), and that copy's `invoker` still points at the performer.

**Invoke flow** (`Song.invoke`, `:538-660`):
1. Reset `timeOut` and the lyric counters (`:541-543`), then call the base `invoke` (`:544`).
2. Armour check (`:547-557`). If the performer is a player, the skill does not disregard armour, `CMLib.utensils().armorCheck(mob, CharClass.ARMOR_LEATHER)` fails, and `rollPercentage()<50`, the cast fails with "hit(s) a foul note on X due to <S-HIS-HER> armor!" (`:555`). Non-leather armour therefore costs a bard a coin-flip on every non-auto cast.
3. Grant `Song_Nothing` to any bard who knows this song but not that ability (`:559-568`). This is a side effect in the cast path.
4. Skip hook (`:570`). A silent performer gets "You can't sing!" (`:573-577`).
5. `proficiencyCheck` (`:579`), then `unSingAll` quietly ends the performer's other songs (`:580`, `:385-398`).
6. On success, set `invoker`, `originRoom`, the new depth and the scope (`:583-588`).
7. Announce in each room in scope (`:598-601`, `:496-510`). The origin room sees "<S-NAME> begin(s) to sing the Song of X." (`:589`). A recast sees "extend(s) the X's range" (`:594`) or "start(s) the X over again" (`:596`). Other rooms hear "You hear the Song of X being sung [in a direction]!" (`:505`) or "...being sung nearby!" (`:507`).
8. Copy the song onto each target from `sendMsgAndGetTargets` (`:512-536`) who can hear the performer (`:627`). A malicious song on a non-performer is flagged `MASK_MALICIOUS` (`:617-621`). If `mindAttack()` is true, the message is sent as a mind attack (`:631-632`).

**Scope and depth.** `getInvokerScopeRoomSet(depth)` (`:447-469`) runs a radiant room search from the performer's room with `OPENONLY`, `AREAONLY` and `NOAIR` (`:460-465`). Depth starts at 0. Each recast adds 1, up to `getXMAXRANGELevel(invoker)/2` (`:432-445`, maximum at `:438`). Repeating a song therefore extends its reach. The base `maxRange()` is `adjustedMaxInvokerRange(2)` (`:85-88`).

**Scaling.** `innerStatBonusPct()` is Charisma divided by `BASEMAXSTAT` (`:168-174`). `innerAvgStat()` is Charisma (`:176-181`). `adjustedLevel` adds `(CHA-10)/3` when `CHA-10 > 10`, that is, when CHA is above 20 (`:184-194`). Subclasses use `statBonusPct()`, `avgStat()` and `adjustedLevel(invoker(),0)`. The bonus pair is cached per effect and cleared when the affected target or the invoker changes (`:134-146`).

**Tick.** `Song.tick` (`:292-383`) runs on every tick. The tick length is `TICKTIME`, defaulting to 4000 ms (`core/CMProps.java:2720-2722`). Each tick:
- If the performer has moved, recompute the scope (`:301-309`).
- If the song is malicious and not flagged `maliciousButNotAggressiveFlag()`, monsters that are idle walk back toward the origin room. If the performer is visible to them, they attack it (`:311-331`).
- If the performer no longer holds this effect or the scope is null, unsing (`:333-336`). If the listener is outside the scope, recompute it. If it is still outside, unsing (`:337-344`).
- If the listener is the performer, sing the next lyric line (`:346-373`).
- If the performer is no longer an awake, living mobile, or cannot be heard by the listener, unsing (`:375-381`).

**Ending a song.** `unSingMe` (`:400-430`) ends one listener's copy. A performer's own copy ends at once. For a listener, `timeOut` is set once to now plus `TICKTIME` times the performer's XTIME level (`:419-420`), so the song lingers for a while after the performer stops. It ends when `System.currentTimeMillis() >= timeOut` (`:422-426`).

**Interruption.** `executeMsg` ends the song when the performer speaks (`TYP_SPEAK`), unless the speech is itself a Song, Play or Dance tool (`:212-218`). It also ends when the performer wears something on the mouth slot (`:225-227`). Both cases show "<S-NAME> stop(s) singing." (`:221`, `:230`). Singing and speaking are therefore mutually exclusive.

**Lyrics.** `getLyrics` (`:252-289`) reads `skills/songlyrics.txt` and splits it into lists keyed by class ID, using `[ID]` headers (`:258-285`). Each tick the performer sings the next line as "<S-NAME> sing(s) 'X'" (`:369`). A line beginning "PAUSE n" sets a pause of n ticks (`:364-367`). The lyric text itself is not reproduced here.

**Aggression.** `abstractQuality()==MALICIOUS` makes `mindAttack()` true (`:100-103`). `maliciousButNotAggressiveFlag()` (`:110-113`) stops a malicious song from starting fights. Most malicious songs override it.

**Outside this bucket.** `StdAbility` supplies `proficiencyCheck`, `properTargets`, `verbalCastCode`, `beneficialAffect` and `copyOf`. These were used but not read. `BardSkill` (`Abilities/Songs/BardSkill.java:36`) is the base of the `Skill_*` classes and is in another bucket.

### 2. How a Song_ effect is built

The 43 `Song_*` classes share one pattern, verified across all of them:
- `ID()`, `name()` (via `L(...)`), and `abstractQuality()`, which decides whether the effect is benign or malicious toward others.
- Optional overrides: `skipStandardSongInvoke()` (custom invoke, as in `Song_Friendship.java:58-60`), `skipStandardSongTick()`, `skipSimpleStandardSongTickToo()`, `maliciousButNotAggressiveFlag()`, `HAS_QUANTITATIVE_ASPECT()` (false where the quantity-based proficiency does not apply, e.g. `Song_Babble.java:67-69`), and `canTargetCode()` (items, for the instrument and scroll songs).
- Effect hooks: `affectPhyStats` (attack, armour, damage, speed, weight, senses, disposition), `affectCharStats` (base and derived stats), `affectCharState` (hit points, hunger, thirst, movement, mana), plus `okMessage` (veto), `executeMsg` and `tick`.
- Many hooks begin with `if(invoker==null) return;`, then handle the performer as a special case with `affected==invoker`.

Songs fall into two groups. Standard songs let `Song.invoke` and `Song.tick` do the work, and the subclass only adds an effect. Several songs set `skipStandardSongInvoke()` and run their own invoke, which repeats most of the base flow: Friendship, Babble, Thanks, Flight and Rebirth. Ode overrides invoke entirely and calls `Song.invoke` only when singing a composed ode.

### 3. Ode: composing a benefit from observed behaviour (Song_Ode)

Ode is the most distinctive class in this bucket.

- **Composing.** Invoking Ode with a target (`Abilities/Songs/Song_Ode.java:485-507`) copies the effect onto the composer with `whom` set to the target and `song` null, so no benefits exist yet. While `song` is null, `executeMsg` records the target's action codes into `trail` (`:295-309`). It records only codes in the 24-row table `stuff` (`:84-109`).
- **Table.** Each row holds a message type, a kind code, a parameter and a flavour line. The kinds are `s` (state), `c` (stat), `ca` (saving throw), `e` (physical stat, such as attack), and `pp` (polymorph). The rows cover eating (hunger), drinking (thirst), sleeping (hit points), sitting (mana), speaking (wisdom), leaving (movement), the saves, get (strength) and weapon attacks (attack). The full row list is in "Content worth mining".
- **Finishing.** Invoking with no argument runs `composition()` (`:111-177`). It counts each recorded code. Weapon-attack counts are divided by 25 (`:143-144`). The rows are sorted by count, and the top three are kept, with random tie-breaks (`:163-175`). The result is stored as `"index;count;"` three times. The composed ode is saved by name in `miscText` through `text()` and `getSongs()` (`:379-415`), using the record format `name|~|composition[|]` (`:387`).
- **Singing.** Naming an ode (`:451-472`) sets `whom` to an inhabitant or player with that name, or to a placeholder `StdMOB` with that name (`:463-470`).
- **Benefits.** Attack adds `ticks + XL`, with the ticks capped at `7 + avgStat()` (`:181-209`, cap `:185`). Saves and stats add `ticks + XL` with the ticks capped at 50 (`:212-239`, cap `:225`). Base stats are also capped at `1 + avgStat()/5` (`:217`, `:232-233`). States are capped at `10 + 2*avgStat()` (`:242-280`, cap `:247`). Each tick has a 10% chance (`:339`) that the target "sings" one benefit's flavour line (`:347`).

### 4. Charm, babble and other vetoes on listeners

- **Friendship** (`Abilities/Songs/Song_Friendship.java`). Flags are CHARMING and MINDALTERING (`:70-72`). Each listener who can hear and does not already have the effect is told to follow the performer (`CMLib.commands().postFollow`, `:264-266`). The group is then made peaceful (`CMLib.combat().makePeaceInGroup`, `:273`). A target whose level exceeds `3 + (level + 2*XL)/10` above the performer's effective level is refused with "<T-NAME> looks too powerful." (`:249-254`). While charmed, `okMessage` refuses attacks on the leader, refuses leaving the room while the leader is present ("You don't want to leave your friend.", `:106`), and refuses NOFOLLOW (`:110-116`). The charm breaks on a malicious attack from the leader (`:86-89`) or when the leader is no longer followed (`:146-149`). On removal (`:155-188`) the target says "<S-YOUPOSS> free-will returns." (`:167`). A monster then wanders away on a roll over 50 (`:172`) or turns on the performer (`:179-180`).
- **Babble** (`Abilities/Songs/Song_Babble.java`). `okMessage` on the listener rewrites its speech, whether say, tell or channel (`:125-134`). Letters are replaced by random letters of the same class (vowel for vowel, consonant for consonant), with the case kept (`:95-121`). The number replaced is half the letter count of the text others hear (`:104`, `:144-146`).
- **Thanks** (`Abilities/Songs/Song_Thanks.java`). Not aggressive. On each tick, with a 6% roll (`:102`), a target in the performer's room that fails both its MIND and MAGIC saves (`:103-104`), can move, is visible, and carries more than `1.0 + XL` in coin (`:107`) says one of ten thank-you lines (`:109-140`). It then gives coin equal to `(0.5 + CHA/max) * XL` (`:142-148`), via a GIVE command.
- **Serenity** (`Abilities/Songs/Song_Serenity.java`). `okMessage` refuses malicious acts aimed at listeners (`:76-80`). Such an act gets through only if a roll under `(attacker level - round(performer level * CHA/max) - XL) * 20` succeeds (`:84-88`). Otherwise the attacker is made peaceful, its victim is cleared, and the act is refused (`:97-106`).
- **Mercy** (`Abilities/Songs/Song_Mercy.java`). A counter is set to 3 on invoke (`:116`, `:119`) and reset to 3 whenever the listener changes room (`:74-77`). It is decremented each tick (`:80`). While it is above zero, or the listener has just moved, malicious acts against the listener are refused. The attacker is told "You feel like showing X mercy right now." (`:93-100`), and the listener is made peaceful (`:103-104`).

### 5. Stage and bard skills (Skill_*)

- **Upstage** (`Abilities/Songs/Skill_Upstage.java`). Malicious, flagged moving (`:95-98`), with movement and mana cost (`:89-91`). It needs at least two targets (`:157-160`). Each target that is in combat and takes the malicious mind affect (`:179-181`) has its previous range and victim saved in `previousRange` and `previousVictim` (`:184-185`). It then gets a new range of `roll(1, room maxRange) + XL/3` (`:186`) and the performer as its victim (`:187`). The effect ends when the performer leaves the room or is out of the game (`:125-130`), or when an upstaged mob damages the performer (`:107-114`). Undo restores the saved victim and range (`:137-146`).
- **Slapstick** (`Abilities/Songs/Skill_Slapstick.java`). Malicious in combat (`castingQuality`, `:89-98`), with movement cost (`:83-85`). A d10 picks one of ten comic flavour lines (`:119-143`). An auto-cast drains the target's mana (`:116-117`).
- **StrikeTheSet** (`Abilities/Songs/Skill_StrikeTheSet.java`). The performer "strikes the set" (`:130`). The room and each unattached item receive a `beneficialAffect` with `TICKS_ALMOST_FOREVER` (`:134-147`). The effect sets `IS_NOT_SEEN` and `SENSE_UNLOCATABLE` (`:102-103`) and removes itself once the performer is gone from the room (`:96-99`).
- **Stability** (`Abilities/Songs/Skill_Stability.java`). `okMessage` blocks a malicious moving ability aimed at the holder (`:97-102`). The block applies when the holder has no record of this skill, or when a proficiency check at `-40 + 2*XL` succeeds (`:101`). The message is "<S-NAME> remain(s) stable despite the <O-NAME>." (`:117-119`), and the act is refused (`:121`).
- **Struggle** (`Abilities/Songs/Skill_Struggle.java`). Usable only while bound (`:119-122`). A success copies an unsavable effect onto the mob (`:135-141`). While bound, strength rises by `DEX + level + XL` (`:98`). The effect ends when the mob is no longer bound (`:93-94`).
- **TuneInstrument** (`Abilities/Songs/Skill_TuneInstrument.java`). Adds `2 + XL` to the instrument's `ability()` (`:84-87`). An instrument can be tuned only once (`:96-99`).
- **Warrants** (`Abilities/Songs/Skill_Warrants.java`). Lists the warrants in the room's `LegalBehavior` (`:91-93`, `:105-119`) under a proficiency check at `-25 + CHA + 2*XL` (`:98`). The table header reads Name, Victim, Witness, Crime (`:116-119`).
- **SlowFall** (`Abilities/Songs/Skill_SlowFall.java`). `affectPhyStats` sets weight to 0 while activated (`:92-94`). Activation happens when the affected is falling and a proficiency check passes (`:102-110`). Whether weight 0 affects fall damage is unverified.
- **Songcraft** (`Abilities/Songs/Skill_Songcraft.java`). Base class for Chantcraft and Prayercraft. `craftType()` returns `ACODE_SONG` here (`:95-97`), and the subclasses override it (`Skill_Chantcraft.java:52-54`, `Skill_Prayercraft.java:52-54`). When another caster in the room casts a spell of that type (`:107-113`), the bard may learn it. The conditions are checked at `:114-118`. The bard's effective level is `adjustedLevel - lowestLevel + 1`, or `level/3 + XL` if that is higher (`:120-127`). The learned spell is stored in `miscText` (`:132-134`), and a trailer "<T-NAME> casts 'X'." is added (`:135`). `lastID` stops the same spell being copied twice in a row (`:114`).

### 6. Magic instruments and scrolls

- **EnchantInstrument** (`Abilities/Songs/Song_EnchantInstrument.java`). Targets an item that is both a `Wand` and a `MusicalInstrument` (`:129-130`). `GenMagicInstrument` implements both (`Items/MiscMagic/GenMagicInstrument.java:37`). The song must allow enchanting (`mayBeEnchanted`, `:176-180`). The wand must be empty of songs (`:182-185`). The XP cost is `30 + 10*lowestQualifyingLevel(song) - CHA` (`:188-189`), and mana is the whole pool (`overrideMana` `:97-99`). XP is spent before the proficiency roll (`:199-202`), so a failed cast still costs XP. On success the song is stored and copied onto the wand, the wand's level rises to the song's adjusted level, and five uses are added (`:213-224`). Failure reads "looking very frustrated" (`:231`).
- **RechargeInstrument** (`Abilities/Songs/Song_RechargeInstrument.java`). Costs 100 mana (`:59-61`). Adds `round(5 * CHA/max)` charges (`:145-146`), capped at max (`:148-155`). An instrument at full charge "glow(s) brightly then disintigrates!" and is destroyed (`:138-141`).
- **RestoreMusic** (`Abilities/Songs/Song_RestoreMusic.java`). Costs 50 mana (`:53-55`). Targets a Scroll that is also MiscMagic (`:107-108`). It refuses when `usesRemaining` exceeds the spell count (`:120-123`) or the scroll is empty (`:126-129`). On success, "The music notations on X become more definite!" (`:154`).
- **SongWrite** (`Abilities/Songs/Skill_SongWrite.java`). The bard writes a known song, matched by name prefix (`:112-121`), onto a Scroll that is also MiscMagic (`:97-101`), and needs full mana (`:103-106`). Capacity is `qualifyingClassLevel + 2*XL - qualifyingLevel` (`:128-134`). Duplicates are refused (`:137-143`). Mana is set to 0 (`:151-152`), and the XP cost is `20 * lowestQualifyingLevel(song)` (`:154-158`). The rest of the write, `:160-190`, was not read.
- **ReadMusic** (`Abilities/Songs/Song_ReadMusic.java`). Grants a copy of the Read Music effect to the target (`:128-131`). The effect implements `Scroll.ScrollUsage` (`:35`).

### 7. Messages shown to players

- Begin: "<S-NAME> begin(s) to sing the Song of X." (`Abilities/Songs/Song.java:589`). Recast: "extend(s) the X's range" (`:594`) or "start(s) the X over again" (`:596`).
- Listening from a distance: "You hear the Song of X being sung [in a direction]!" (`:505`), or "...sung nearby!" (`:507`).
- Failure: "<S-NAME> hit(s) a foul note." (`:657`). Armour fumble: "hit(s) a foul note on X due to <S-HIS-HER> armor!" (`:555`).
- Interruption: "<S-NAME> stop(s) singing." (`:221`).
- Ode: "<S-NAME> finish(es) composing the Ode." (`Song_Ode.java:430`), and "Compose or sing an ode about whom?" (`:446`).
- Death: "The painful song <DAMAGE> <T-NAME>!" (`Song_Death.java:97`).
- Serenity: "You feel too peaceful to fight." (`Song_Serenity.java:98`).
- Friendship: "<T-NAME> looks too powerful." (`Song_Friendship.java:254`).

### 8. Interactions with other systems

- `Song` ignores its own Play and Dance messages so that a performance cannot silence itself (`Song.java:215-217`).
- `Song_Nothing` is the stop command. It calls `unSingAll` with `noEcho` set, so the stop is silent for the bard (`Song_Nothing.java:76-95`).
- Other engine calls: `CMLib.tracking()` (scope and walking), `CMLib.map()`, `CMLib.combat().postAttack` and `makePeace` (`Song.java:330`, `Song_Serenity.java:99`), `CMLib.utensils().resurrect` (`Song_Rebirth.java:124`), `CMLib.beanCounter()` (`Song_Thanks.java:143`), `CMLib.leveler().postExperience` (`Song_EnchantInstrument.java:200`, `Skill_SongWrite.java:156`) and `CMLib.ableMapper()` (qualifying levels).

## Content worth mining

### Song_ catalogue (43 classes)

Quality: B = beneficial to others, M = malicious, O = OK for others, I = indifferent. "not aggr." marks `maliciousButNotAggressiveFlag` (`Song.java:110-113`). XL is the performer's extra-level term (`getXLEVELLevel`), and "pct" is `statBonusPct()`, the performer's CHA ratio.

| Class (display name) | Q | Effect (key lines) |
|---|---|---|
| `Song_Armor` (Armor) | B | Armour value reduced by avgStat + 2×level (`:64-66`) |
| `Song_Babble` (Babble) | M, not aggr. | Scrambles about half the letters of the listener's speech (`:104`, `:125-156`) |
| `Song_Blasting` (Blasting) | B | Damage += 1 + level/2 (`:64`) |
| `Song_Charm` (Suave) | O | CHA += 4 + XL (`:63-64`) |
| `Song_Clumsiness` (Clumsiness) | M | Attack −= avgStat + 2×level (`:66-68`); listeners' DEX halved, performer exempt (`:77-80`) |
| `Song_Comprehension` (Comprehension) | O | Adds "(translated from X)" trailers to heard language speech (`:64-104`) |
| `Song_Death` (Death) | M | Each tick HP loss = floor(HP × (0.06 + 0.02×(1+XL) + 0.001×CHA)), quartered if the performer is elsewhere, quartered again if not adjacent, minimum 1 (`:75-95`) |
| `Song_Detection` (Awareness) | O | Sense CAN_SEE_BONUS (`:69`) |
| `Song_Dexterity` (Dexterity) | B | Performer's DEX −= sung amount; listeners' DEX adjusted (see Problems) (`:65-69`); sung amount must be below DEX/4 + (CHA-10)/3 and below DEX (`:102-104`); monsters sing DEX/4 (`:94`) |
| `Song_Disgust` (Disgust) | M | An idle listener attacks a random room occupant (`:83-97`); skipped when roll < (listener level − pct-scaled performer level − XL) × 20 (`:92-94`) |
| `Song_Distraction` (Distraction) | M | In combat, hand or move actions fail when a roll exceeds MIND save + 50 − performer level (`:78-85`) |
| `Song_EnchantInstrument` (Enchant Instrument) | I | Writes a known song into a magic instrument (section 6) |
| `Song_Flight` (Flight) | O | After its cast, each listener is walked out the first open exit (`:146-166`) |
| `Song_Flying` (Flying) | B | Sets IS_FLYING disposition (`:76`) |
| `Song_Friendship` (Friendship) | M, not aggr. | Charm: listeners follow the performer (section 4) |
| `Song_Health` (Health) | B | Hit points += 5×level (`:75`); poison save += level (`:82`); cast refused above half HP (`:64-65`) |
| `Song_Inebriation` (Drunkenness) | M | Attack −= level (`:73`); listeners' DEX −= 3 + XL (`:85`); 25% stagger message per tick (`:106-114`) |
| `Song_Knowledge` (Knowledge) | O | WIS and INT raised to max(avgStat, current) + 2 + XL (`:64-67`) |
| `Song_Lethargy` (Lethargy) | M | Speed divided by (1.5×pct + 0.1×XL) (`:66-67`) |
| `Song_Lightness` (Lightness) | B | Weight set to 0 (`:67`); refuses GETs that exceed capacity (`:93-97`) |
| `Song_Lullibye` (Lullaby) | M | Each tick the listener sleeps if a d100 roll exceeds 50/pct − 2×XL (`:84-85`); blocks standing and sitting while asleep (`:118-120`) |
| `Song_Mana` (Mana) | O | Mana += level each tick (`:70`) |
| `Song_Mercy` (Mercy) | O | Grace counter; malicious acts refused (section 4) (`:74-106`) |
| `Song_Mute` (Muting) | M | CAN_NOT_SPEAK sense (`:72`) |
| `Song_Nothing` (Nothing) | I | Silent stop of all the bard's songs (`:85-92`) |
| `Song_Ode` (Ode) | B | Composed benefit about a target (section 3) |
| `Song_PirateShanty` (Pirate Shanty) | B | Each tick: movement += round(pct × n²) for n singers in the room; fatigue −= amount × 250 (`:97-102`) |
| `Song_Protection` (Protection) | B | Armour −= level (`:63`); DEX ÷ 3 and acid, cold, electric, fire and gas saves += 2×level (`:72-78`) |
| `Song_Quickness` (Quickness) | B | Speed += SpeedAdjustment × (pct + 0.1×XL) (`:65`) |
| `Song_Rage` (Rage) | M | Damage raised, attack lowered, armour raised (`:66-69`); FLEE blocked while affected (`:80-85`) |
| `Song_ReadMusic` (Read Music) | I | Grants Read Music to the target (`:128-131`) |
| `Song_Rebirth` (Rebirth) | O | Resurrects player corpses in the room that the caster cannot fight, or that belong to the caster's group (`:114-128`) |
| `Song_RechargeInstrument` (Recharge Instrument) | I | Adds round(5×pct) charges (`:145-146`) |
| `Song_RestoreMusic` (Restore Music) | I | Restores or extends a music scroll (`:120-154`) |
| `Song_Revelation` (Revelation) | O | castingQuality detects hidden or invisible mobs in the target's room (`:71-86`); sense effects in affectPhyStats (`:94-`, rest unverified) |
| `Song_Saudade` (Saudade) | O | Homesick timer: recall attempts at counts 3, 6, 8 and 10, forced recall at 15 or more (`:127-210`) |
| `Song_Seeing` (Seeing) | O | Senses CAN_SEE_HIDDEN, SNEAKERS and INFRARED (`:107-109`); SAVE_OVERLOOKING += level + 100 (`:67`) |
| `Song_Serenity` (Serenity) | I, not aggr. | Malicious acts against listeners blocked unless a chance roll passes (`:84-88`, `:106`) |
| `Song_Silence` (Silencing) | M | CAN_NOT_HEAR and CAN_NOT_SPEAK senses (`:78-79`); no tick behaviour (`:58-60`) |
| `Song_SingleMindedness` (Single Mindedness) | M | Captures the listener's first action, then refuses other action types (`:75-94`) |
| `Song_Strength` (Strength) | B | Performer's STR −= amount; listeners' STR += amount + XL (`:72-76`); sung amount limited (`:109-111`); monsters sing STR/2 (`:101`) |
| `Song_Thanks` (Thanks) | M, not aggr. | Thank-you lines and coin gift on a 6% tick (section 4) (`:102-148`) |
| `Song_Valor` (Valor) | B | Attack += avgStat + level (`:62-64`) |

### Skill_ catalogue (10 classes)

| Class (display name) | Q | Effect (key lines) |
|---|---|---|
| `Skill_Slapstick` (Slapstick) | I | Malicious in combat; d10 comic messages; auto-cast drains mana (`:89-143`) |
| `Skill_SlowFall` (Slow Fall) | I | Weight 0 while activated on falling (`:92-110`) |
| `Skill_SongWrite` (Song Write) | I | Writes a song onto a scroll (section 6) |
| `Skill_Songcraft` (Songcraft) | OK self | Learns spells cast in the room (section 5) (`:95-136`) |
| `Skill_Stability` (Stability) | I | Blocks malicious movement abilities (`:88-121`) |
| `Skill_StrikeTheSet` (Strike The Set) | I | Hides the room and its items (`:96-147`) |
| `Skill_Struggle` (Struggle) | I | Only while bound: STR += DEX + level + XL (`:90-141`) |
| `Skill_TuneInstrument` (Tune Instrument) | I | Instrument ability += 2 + XL, once (`:84-99`) |
| `Skill_Upstage` (Upstage) | M | Combatants in the room switched to the performer as victim, with a new range (section 5) (`:157-187`) |
| `Skill_Warrants` (Warrants) | I | Lists room warrants under a proficiency check (`:89-119`) |

### Other content

- Ode trait table (`Song_Ode.java:84-109`). Each row maps a message type to a trait and is the building block for composed odes:
  - Eat (`:85`) and drink (`:89`) feed hunger and thirst.
  - Get (`:86`) feeds strength.
  - Leave (`:107`) feeds movement, and sleep (`:102`) feeds hit points. Sit (`:103`) feeds mana, and speak (`:104`) feeds wisdom.
  - Weapon attack (`:106`) feeds attack. Its count is divided by 25 before ranking (`:143-144`).
  - Gas, fire, disease, undead, cold, magic, acid, electric, justice, mind, paralysis, poison and water (`:87-101`, `:105`) feed the corresponding save.
  - Look and examine (`:96-97`) and polymorph (`:108`) have no working effect (see Problems).
- Thanks text (`Song_Thanks.java:109-140`): ten short lines, each addressing the performer by name. They are benign and could be reused as NPC gratitude lines.
- Slapstick (`Skill_Slapstick.java:119-143`): ten comic stage-business lines, such as tripping over a foot or throwing a pie. They are benign and suit a comedy NPC.
- Warrant table (`Skill_Warrants.java:116-119`): a simple fixed-column legal listing.
- Lyrics file (`skills/songlyrics.txt`, loaded at `Song.java:258`): `[ID]` sections of sung lines with `PAUSE n` directives. It is a good format for content writers. Its contents were not read here.

## Notable design ideas

- **Radius that grows with repetition.** Recasting extends the song's depth by one room-layer at a time, up to half the performer's range (`Song.java:432-445`). The message changes to "extend(s) the range" or "start(s) over again" (`:590-597`).
- **Listener copies tied to the performer.** Each tick checks the performer's state, its location, and whether the listener can hear. That makes the song self-cleaning without a separate scheduler (`:333-381`).
- **Linger timeout for listeners.** The performer's XTIME level extends the song for listeners after the performer stops (`:419-420`), which gives the effect a tail.
- **Speech as a mutual exclusion.** Singing ends when the performer speaks (`:212-218`). This is a cheap, memorable rule that makes the bard's choice visible.
- **Ode as a behavioural signature.** The composer counts the target's observed actions, keeps the top three traits, and stores them by name (`Song_Ode.java:295-309`, `:111-177`). A target's habits thus become a reusable effect.
- **Vetoes as status effects.** Several songs implement their effect as an `okMessage` veto: Mercy, Serenity, Stability, SingleMindedness and Distraction. Each refuses an action rather than changing a stat, which is a clean way to express "cannot do X".
- **Charisma as the common knob.** Almost every song scales with `statBonusPct()` or `avgStat()`. One performer attribute drives a whole catalogue.
- **Items as capacity-limited carriers.** Instruments hold one song and charges; scrolls hold a bounded number of spells whose capacity depends on class level (`Skill_SongWrite.java:128-134`). Recharging is a rate (`round(5 × pct)`) rather than a fixed value.

## Problems and cruft

- **Song_Dexterity doubles listeners' DEX.** `Song_Dexterity.java:68-69` calls `adjStat(DEX, getStat(DEX) + amount + XL)`. `adjStat` adds its value to the existing stat (`Common/DefaultCharStats.java:1158-1162`). The result is about 2×DEX + amount + XL, not DEX + amount + XL. `Song_Strength.java:75-76` uses `setStat` for the same pattern and is correct. Intent is unverified, but this looks like a bug.
- **Three Ode trait rows are inert.** The look and examine rows have kind `es` (`Song_Ode.java:96-97`), and the polymorph row has kind `pp` (`:108`). `affectPhyStats` handles only `e` rows whose parameter starts with `a` (`:196-205`). `affectCharStats` and `affectCharState` handle only `c` and `s` kinds (`:221-222`, `:252-253`). These three rows therefore produce no effect. They can still be chosen as one of the composed top three (`:163-175`), which wastes a slot.
- **Rebirth always reports failure.** `Song_Rebirth.java:112` declares `final boolean foundOne=false;`, which is never set. "Nothing seems to happen." (`:130-131`) prints even after a corpse has been resurrected (`:124`).
- **Lyrics are re-parsed on every tick.** `Song.java:254` reads the lyric map from `Resources` but never stores it back. Unless `Resources` caches the file internally (unverified), `getLyrics()` (`:348`) re-reads and re-parses `songlyrics.txt` on each tick for a performer with lyrics.
- **Protection also cuts the performer's DEX.** `Song_Protection.java:70-73` checks only `invoker==null`, not `affected==invoker`. The performer gets the one-third DEX and the saves as well. `Song_Clumsiness.java:77-78` does exempt the performer. Intent is unverified.
- **Struggle adds DEX to STR.** `Skill_Struggle.java:98` raises strength by dexterity plus level plus XL. This cross-stat add may be intended but is unusual (unverified).
- **Saudade's schedule depends on a starting offset.** Its counter starts at `XL/2` (`Song_Saudade.java:221`, `:224`). The schedule fires on exact values 3, 6, 8 and 10 (`:143-210`), so a higher start skips the early steps. Intent is unverified.
- **Invoke bodies are copy-pasted.** Friendship, Babble, Thanks, Flight and Rebirth each repeat the proficiency, scope and message loop from `Song.invoke` (`:583-654`). Changes must be made in each copy.
- **Hidden side effect in invoke.** `Song.invoke` grants `Song_Nothing` as a side effect of casting (`:559-568`).
- **Flight returns false from tick.** `Song_Flight.java:80-81` returns false when the listener is not following the performer. Whether that ends the effect cleanly was not verified.
- **Commented-out guard.** `Song_Thanks.java:101` has a commented-out check on monsters. The gift therefore applies to any mob in range, which may be intended.
- **Armour roll is harsh.** Non-leather-wearing bards fail half their casts (`Song.java:553`). This is a deliberate design choice, but it is very punishing.
- **Friendship resets a persistence field.** On quit, `Song_Friendship.java:130-131` sets `playerStats().setLastUpdated(0)`. The purpose is unverified.

## Takeaways for a Python SUD

**Borrow.**
- The performer-tied effect pattern. An effect holds a reference to its source. Each tick checks that the source is still valid (in the same room, awake, able to speak). If not, the effect ends. Listener copies carry the source reference, and listeners get a short linger after the source stops.
- The radius-by-repetition rule. Store a depth on the song, grow it when re-sung, and derive the room set from a breadth-first search over open exits, limited to the performer's area.
- Stop-on-speak as an exclusive action. In a single-player game this is a cheap and visible rule.
- The Ode idea, which is probably the most valuable element. Tally observed events for a target (for example, the player's habits or an NPC's), keep the top three, and turn them into a named, saved benefit.
- The data-as-catalogue approach. Each song is one row of data. Something like:

```python
@dataclass(frozen=True)
class SongSpec:
    id: str                  # "Song_Armor"
    name: str                # "Armor"
    quality: str             # "good" | "bad" | "neutral" | "ok"
    stat: str                # "armor", "speed", "damage", "dexterity", ...
    op: str                  # "add" | "sub" | "mul" | "set"
    scale: str               # "avg_stat + 2*level", "1 + level//2", ...
    not_aggressive: bool = False
```

  Evaluate `scale` with a small safe expression evaluator, not `eval`. Keep the lyrics as text with `[id]` sections and `PAUSE n` lines.
- Charisma (or one performer attribute) as the shared scaling term. The formulas to port as-is are `CHA/max` for the ratio and `(CHA-10)/3` for the level bonus above CHA 20 (`Song.java:168-194`).
- Vetoes as status effects. Implement "cannot do X" effects as a `before_action` hook that can refuse. Use it for Mercy, Serenity, Stability and Single Mindedness.
- Capacity-limited items. Instruments hold one song and a number of charges. Scrolls hold a limited number of spells, with capacity derived from the writer's skill.

**Simplify.**
- Drop the two-phase `CMMsg` system. Use two Python hooks: `before(action) -> bool` for vetoes and `after(action)` for observation (the Ode trail). That covers every use in this bucket.
- Drop the string-encoded state (`"name|~|composition[|]"`, `"index;count;"`). Use JSON or plain dataclasses.
- Drop the copy-pasted invoke bodies. One generic cast routine with per-song hooks is enough.
- Drop the placeholder `StdMOB` used for absent Ode targets. A name string and a current-location lookup are enough.

**Drop or reframe for single-player.**
- Thanks, Friendship, Rebirth, Upstage, PirateShanty, Mercy and the group peace calls assume other people in the room. In a single-player SUD they only apply to NPC companions and NPC crowds. Rebirth's player-corpse rule has no equivalent unless the player can die and be restored.
- Player-only persistence (Friendship's last-updated reset) and the death-by-song rules need a single-player equivalent.
- The Dexterity and Protection scaling bugs should not be copied.

## Coverage

- **Read in full** (imports and licence header skipped, all other lines read): `Abilities/Songs/Song.java`, `Song_Ode.java`, `Song_Friendship.java`, `Song_Babble.java`, `Song_Thanks.java`, `Song_EnchantInstrument.java`.
- **Read in substantial part** (hook methods, invoke logic and the decisive lines; some invoke tails not read): `Song_Saudade`, `Song_Flight`, `Song_Death`, `Song_Rebirth`, `Song_RechargeInstrument`, `Song_RestoreMusic`, `Song_ReadMusic`, `Song_Mercy`, `Song_Disgust`, `Song_Serenity`, `Song_Lullibye`, `Song_PirateShanty`, `Song_Comprehension`, `Song_SingleMindedness`, `Song_Distraction`, `Song_Inebriation`, `Song_Strength`, `Song_Dexterity`, `Song_Lightness`, `Song_Revelation` (senses section unverified), `Song_Seeing`, `Skill_Upstage` (invoke tail unverified), `Skill_SongWrite` (`:160-190` unverified), `Skill_Songcraft`, `Skill_Slapstick` (invoke tail), `Skill_StrikeTheSet`, `Skill_Struggle`, `Skill_Stability`, `Skill_SlowFall`, `Skill_TuneInstrument`, `Skill_Warrants` (invoke tail).
- **Skimmed through ID, quality, flags and hook bodies** (short effect classes, each one to a few lines of formula): `Song_Armor`, `Song_Blasting`, `Song_Charm`, `Song_Clumsiness`, `Song_Detection`, `Song_Flying`, `Song_Health`, `Song_Knowledge`, `Song_Lethargy`, `Song_Mana`, `Song_Mute`, `Song_Nothing`, `Song_Protection`, `Song_Quickness`, `Song_Rage`, `Song_Silence`, `Song_Valor`.
- **Skipped**: none. Every file in the manifest was catalogued by ID, quality, flags and override methods.
- **Outside the bucket, not read**: `StdAbility`, `BardSkill`, `Play`, `Dance`, `CMLib` services, `DefaultCharStats` (only the `adjStat` body at `:1158-1162` was read).
