# Bard songs and performance skills, part 2 (Play_Rhythm to Skill_Shuffle)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b084` (Abilities/Songs [part 2/3: Play_Rhythm .. Skill_Shuffle]) · 39 files · 7729 lines of Java.

Citation convention: `Songs/X.java:N` means `com/planet_ink/coffee_mud/Abilities/Songs/X.java:N`. Every other path is written in full, relative to the CoffeeMUD folder.

## Summary

The 39 files in this bucket are the middle third of CoffeeMud's bard content: 12 `Play_` songs and 27 `Skill_` performance skills. A song is a room-scoped aura built on the `Play` base class (`Songs/Play.java:37`). The bard must hold an instrument, the song's strength comes from Charisma, Intelligence and the instrument's ability, and each listener receives a copied effect that lasts while the bard keeps playing. Replaying a song widens its radius, so a performance spreads through the area like a crescendo. The skills are short, theatre-themed actions (monologue, curtain call, exit stage left, cast blocking, mock, false arrest, disguise, juggling, puppets) that change who is in the room, what they can do, or how they look. The most borrowable parts are the table-driven Symphony effects and the stage-as-crowd-control model. Several defects, such as the faction check in `Skill_Befriend` and the heal clamp in `Skill_LightPlacebo`, show where the logic is fragile.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Songs/Play.java` (`Play`) | Base for every song: instrument selection, room scope, listener effects, tick rules | 763 |
| `Songs/Play_Instrument.java` | Base for one-shot instrument songs that fire a mimic spell at each listener | 154 |
| `Songs/BardSkill.java` | Base for every bard skill: leather-armour fumble roll | 95 |
| `Songs/Play_Symphony.java` | Symphony: 16 instrument types, each with level-banded effects | 833 |
| `Songs/Skill_Juggle.java` | Juggling: floats items with Spell_Fly, throws them in combat | 534 |
| `Songs/Skill_Disguise.java` | Disguise: fake attributes, gated by skill level, stored as misc text | 468 |
| `Songs/Skill_Befriend.java` | Recruits a same-category monster as a lasting follower | 388 |
| `Songs/Skill_LightPlacebo.java` | Healing paid back through a fake-HP pool | 310 |
| `Songs/Skill_Monologue.java` | Freezes the audience while the bard keeps speaking | 224 |
| `Songs/Skill_CurtainCall.java` | Audience bows and heals each tick | 210 |
| `Songs/Skill_QuickChange.java` | Stashes and swaps a whole outfit as serialized text | 204 |
| `Songs/Skill_Imitation.java` | Learns the visible text of others' spells and replays it | 200 |
| `Songs/Skill_Satire.java`, `Songs/Skill_FalseArrest.java` | Legal actions through the warrant system | 179, 169 |
| `Songs/Play_Solo.java` | Exclusive solo with an upstage contest | 208 |
| `Songs/Play_Rhythm.java` | Malicious song that lowers magic saves | 68 |

Other files used for context are listed under Coverage. The complete 39-file catalogue is under Content worth mining.

## How it works

### 1. The Play base class (`Songs/Play.java`)

**Data model.** Fields are `instrument`, `timeOut`, `commonRoomSet`, `originRoom`, `playDepth` and `bonusCache` (`Songs/Play.java:79-85`). The classification is `ACODE_SONG | DOMAIN_PLAYING` (`:103`). Usage costs MOVEMENT and MANA (`:107-110`). Trigger words are PLAY, PL and PLA (`:71`). `maxRange()` is `adjustedMaxInvokerRange(2)` (`:175-179`).

**Strength formulas** (`Songs/Play.java:131-250`, integer division where the code uses ints):
- `innerAvgStat = round( ((CHA+3) + (INT+3)) / 2.0 + instrument.ability )` (`:163-173`).
- `innerStatBonusPct = ( (CHA+3)/max + (INT+3)/max + 2*instrument.ability/max ) / 2`, where `max` is `CMProps.Int.BASEMAXSTAT` (`:151-161`).
- `adjustedLevel = base + (CHA-10)/4 + (INT-10)/5 + instrument.ability` (`:223-235`).
- `invokerLevel = level + instrument.ability + 2*XLEVEL` (`:237-250`).

**Invoke flow** (`Songs/Play.java:587-762`):
1. If not an automatic cast, find the instrument: a ridden instrument first, then the first held instrument (`:593`, `:596-624`). Missing gives "You need an instrument!" (`:622`). A wrong instrument type gives "This song can only be played on @x1." (`:628`).
2. A player without leather armour has a 50% chance to fumble (`:633-643`, `BardSkill.java:77-94`).
3. The bard is given a non-savable `Play_Break` ability if missing (`:648-657`). That ability belongs to the neighbouring bucket.
4. A proficiency check runs (`:665`), the new depth is computed (`:667`), and the bard's other songs are cancelled (`:668`).
5. On success, each scope room gets a message (`:690-692`). Each listener gets a vetoable message (`:726-736`). A persistent song adds a copy as an effect (`:738-745`). A non-persistent song calls `inpersistentAffect` (`:747`).
6. On failure: "<S-NAME> hit(s) a foul note." (`:759`).

**Scope and depth** (`Songs/Play.java:476-510`). Depth 0 is the bard's room. Depth n uses `CMLib.tracking().getRadiantRooms` out to n rooms, with the flags OPENONLY, AREAONLY and NOAIR (`:95-98`, `:506`). Replaying while still playing increments the depth, capped at `getXMAXRANGELevel/2` (`:476-489`). Remote rooms receive "You hear @x1 being played @x2!" with a direction, or "...nearby!" (`:537-554`). `sendMsgAndGetTargets` temporarily moves the bard into each scope room so that the target finder sees that room's occupants (`:556-580`).

**Tick and expiry** (`Songs/Play.java:269-331`). A listener's song ends when:
- the bard's copy is gone (`:309-312`);
- the listener leaves the scope (`:313-320`);
- the bard stops holding the instrument or cannot be heard (`:325-329`).

Speaking while the instrument is in the mouth, or removing or wielding the held instrument, stops the song (`:356-384`).

Grace period (`:401-430`): when the bard stops, a listener's song lasts `XTIME(bard) * tick length` more. At XTIME 0 it ends at once. This was read from the code and not run.

**Malicious songs** (`Songs/Play.java:286-307`). When `abstractQuality()` is MALICIOUS, idle monster listeners (not following, not fighting, awake, not tracking) walk back toward the origin room or attack the bard (`postAttack` at `:306`).

**Persistence.** Each listener gets `copyOf()` (`:694`, `:742-744`). `StdMOB.addEffect` ignores a duplicate ID (`MOBS/StdMOB.java:5264-5272`). The bard therefore calls `unPlayAll` first, so replays replace the old effect.

### 2. Instrument songs (`Songs/Play_Instrument.java`)

- These songs are non-persistent (`:132-135`). Each play fires `getSpell()`, the mimic spell, once per listener in scope, at level `adjustedLevel(bard,0)` (`inpersistentAffect`, `:84-108`).
- `calculateNewSongDepth` adds one to `playDepth` if the bard last played less than 30 seconds ago (`:70`), capped at `XMAXRANGE/2`. Otherwise depth resets to 0 (`:67-81`). Rapid replay therefore builds a crescendo of range.
- The bard is never hit by a malicious mimic (`:87-88`, comment at `Songs/Play.java:709`).
- An idle monster listener that is not fighting and not tracking, when a malicious mimic lands, invokes `Thief_Assassinate` against the bard (`:94-106`, the signature is at `Abilities/Thief/Thief_Assassinate.java:301`). A malicious instrument therefore makes hostile listeners retaliate.
- `abstractQuality()` equals the mimic spell's quality (`:124-129`).

### 3. Symphony (`Songs/Play_Symphony.java`)

**Dispatch table.** `getToDoCode()` (`:94-615`) picks one of 16 effect codes (`:73-88`) from the instrument type and its level band. The result is cached in `toDoCode`, `toDoString` and `toDoVal`. The full table is under Content worth mining.

**Effects:**
- `UPSAVE` adds `adjustedLevel` to a save stat for all band members, the bard included (`affectCharStats`, `:658-660`).
- `UPSTAT` adds `max(1, adjustedLevel/10)` to a stat (`:661-666`).
- `DOWNSAVE` subtracts `adjustedLevel` from the save of everyone except the bard (`:667-670`). Only this code makes the song MALICIOUS (`abstractQuality`, `:61-71`).
- `UPENVPER2` and `UPENVPER5` adjust attack or armour by `adjustedLevel/2` or `/5` (`affectPhyStats`, `:674-697`).
- `DOWNDAMAGEPER5` reduces incoming damage of the matching type by `adjustedLevel/5` (`executeMsg`, `:623-629`).
- `UPDAMAGEPER3` and `UPDAMAGEPER5` add `adjustedLevel/3` or `/5` to the bard's outgoing damage of the matching type, but only against targets that do not carry this bard's effect (`:631-646`). The bonus therefore falls on outsiders, not on the band.

**Periodic actions** (`tick`, `:710-798`). The per-tick chance is `(addedLevel + adjustedLevel) / per` percent (`:773-774`). The values are per = 2, 5, 10 or 20, with addedLevel 0, 0, 10 or 20 (`:724-740`). Malicious spells are cast at the victim only while in combat (`:721-723`). Friendly spells are cast on the bearer. `REMOVESPELLTYPE` strips charm-flagged effects every tick (`:744-756`). `SPEEDCOMMONSKILLS` gives the bearer's own common skills an extra tick (`:757-769`). `UPMOVEMENT` adds 1 movement per tick (`:741-743`).

**Stacking.** During `invoke`, the ID becomes `Play_Symphony_<INSTRUMENT>` (`:819-830`). Different instrument families therefore stack, and the same family replaces itself.

### 4. The other songs (`Songs/`)

- **Rhythm** (`Play_Rhythm.java:57-66`). Malicious. Every listener in scope except the bard gets `STAT_SAVE_MAGIC -= avgStat + 2*adjustedLevel`.
- **Solo** (`Play_Solo.java:58-207`). Non-persistent, and skips the standard invoke and tick (`:64-79`). Each other bard's song on the room's personal effects is cancelled at 10 mana each (`:186`). An upstage attempt succeeds when `otherBard.level + d30 + XLEVEL > soloist.level + d20 + XLEVEL` (`:97`), which ends the solo (`:104`). A resisted attempt drains 10 mana from the soloist and vetoes the message (`:109-112`). If the soloist's mana bottoms out, the solo ends.
- **Tempo** (`Play_Tempo.java:75-91`). Every tick, each common-skill effect on the listener gets an extra tick. There is no owner check. The song cannot be cast in combat (`:68-69`).
- **Tribal** (`Play_Tribal.java:82-113`). Each tick, every non-malicious chant on every inhabitant of the room that has 1 or 2 ticks left is bumped to 2 or 3 (`:100-108`).
- **Spiritual** (`Play_Spiritual.java:82-104`). A prayer with exactly 1 tick left is set to 2 (`:99-100`).

Caveat on Tribal and Spiritual: `StdAbility.tick` decrements and then unInvokes at 0 or below (`Abilities/StdAbility.java:2843-2858`). Whether a bump lands before expiry depends on which tick runs first. This was not verified.

### 5. Audience and stage skills

- **Monologue** (`Skill_Monologue.java:94-224`). The bard needs an audience (`:196-200`). Each member who can hear the bard gets the effect, cannot move or speak (`:104-105`), and has its ticking suspended (`CMLib.threads().suspendTicking`, `:156`) until unInvoke resumes it (`:135`). The effect lasts while the bard speaks within `4000 ms + 1000 * XLEVEL` (`:173`). Newcomers who enter are added (`:114-118`). The message is "You must now SAY something at least every 4 seconds to maintain the monologue." (`:216`).
- **Curtain Call** (`Skill_CurtainCall.java:107-210`). Requires other actors and no combat (`:159-170`). The audience gets the effect for `10 + XLEVEL` ticks (`:197`). Each tick, a standing member below max HP or mana does a Bow, gains 10% of max HP (`:132`, `:138`) and 10% of max mana (`:133`, `:139`), and pays `round(maxMove * (0.06 - 0.005*XLOWCOST))` movement (`:134`). If movement is short, the effect ends (`:141-145`).
- **Exit Stage Left** (`Skill_ExitStageLeft.java:117-147`). Needs an open west exit (`:163-174`). Audience members get `5 + XLEVEL` ticks (`:195`) and are walked west each tick (`:144`). On unInvoke, anyone still in the bard's room attacks the bard (`:109-113`).
- **Center of Attention** (`Skill_CenterOfAttention.java`). Malicious. The victim gets CAN_NOT_MOVE (`:101-109`) for 3 ticks (`maliciousAffect(...,3,-1)`, `:259`). Each tick, a random pick from 10 × 10 phrase lists produces a flavour line (`:127-192`). The cooldown is `MILLIS_PER_MUDHOUR / TIME_TICK / 2` (`:97`). At defaults that is 600000 / 4000 / 2 = 75 ticks (`core/CMProps.java:681-682`). The effect ends if the victim cannot see the bard or is in a different room (`:119-123`).
- **Cast Blocking** (`Skill_CastBlocking.java:94-183`). Requires combat and at least two occupants (`:102-112`). Combatants are split into two opposing groups by following their victims. Each member then takes a random victim from the other side and a random range from `1..maxRange+1` (`:158-159`, `:171-172`).
- **Shuffle** (`Skill_Shuffle.java:88-146`). Not in combat, and needs at least two occupants. The room's occupant list is emptied and refilled in random order (`:121-139`).
- **Ad Lib** (`Skill_AdLib.java:96-191`). A 3-tick wait (`:182`). When the target uses an ability the bard lacks and `lowestQualifyingLevel <= level/3 + XLEVEL` (`:139-142`), the bard copies the ability, casts it at the target, then unwinds (`:144-155`).
- **Conduct Symphony** (`Skill_Conduct.java:91-169`). Replays the bard's `Play_Symphony` on each group member at `asLevel + 3*XLEVEL` (`:156`). See the defect at `:136` under Problems.

### 6. Disguise family

- **Disguise** (`Skill_Disguise.java:45-62`, `:118-176`). Nine attributes are gated by skill level (`:47-55`): WEIGHT 2, LEVEL 10, SEX 4, RACE 14, HEIGHT 6, NAME 8, CLASS 0, ALIGNMENT 18, AGE 12. Values are stored as `KEY="value"` pairs in misc text (`:129-146`). Name and alignment are applied in `affectPhyStats` (`:149-161`). Race, sex, class and level, and age are applied in `affectCharStats` (`:164-176`). Weight and height appear only in look text (`:212-235`, see Problems).
- **Difficulty.** For weight, height and age, the proficiency adjustment is `-round(|old-new|/old * 100)` (`:297-300`, `:353-356`, `:430-433`).
- **Name checks** (`:359-397`). Spaces and illegal characters are refused. So are existing player names (`:385`) and banned names (`:391`).
- **Mark Disguise** (`Skill_MarkDisguise.java:77-151`). Requires a `Thief_Mark` effect (`:63-75`) and at least 15 ticks of observation (`:105-111`). Cannot copy CMDPLAYERS-class admins (`:98`). The mark's weight, level, gender, race, height, name, class and good/evil flag are copied into the same values array (`:127-140`).

### 7. Object tricks

- **Juggle** (`Skill_Juggle.java:88-534`). Items are floated with a Spell_Fly effect (`:181-199`). The cap is `5 + class level + 2*XLEVEL` (`:99-104`). Every 500 ms, two items are kept in hand and the rest are set down, rotating through the list (`:201-308`). In combat, up to `round(speed) + (level + 2*XLEVEL)/5` items are thrown as thrown weapons with damage `roll(1, adjustedLevel)` (`:106-110`, `:336-389`, damage at `:375`).
- **Puppeteer** (`Skill_Puppeteer.java:82-240`). Targets items whose names end in " puppet" or " marionette" (`:208-209`). The animated puppet gets IS_FLYING (`:194`). In combat it attacks with a hit bonus of `+5*XLEVEL` and damage `roll(1, level + 2*XLEVEL, 1)` (`:142-150`). Out of combat there is a 25% chance per tick of a flavour line (`:153-170`). The veto logic is discussed under Problems.
- **Buffoonery** (`Skill_Buffoonery.java:145-259`). The bard must carry a junk item: no armour above 1 and no weapon above 1 damage (`:121-122`, `:189-194`). The target's best slot item (armour above 1 or weapon above 1 damage, `:135-136`) goes into its pack, and the junk item is worn in its place (`:229-251`).
- **QuickChange** (`Skill_QuickChange.java:99-204`). The first use stows the whole outfit as XML in misc text (`:156-173`), destroys the items (`:171-172`), and leaves the bard bare, with "That outfit is now tucked away for a quick change later on." (`:193`). Later uses swap the outfit (`:142-154`).
- **Instrument Bash** (`Skill_InstrumentBash.java:89-151`). Needs a held instrument. The weapon damage base is `instrument.level + 5 + 2*XLEVEL` (`:140`).

### 8. Social and legal

- **Befriend** (`Skill_Befriend.java:36-388`). The target must be a monster, mobile, not a shopkeeper, and not already following (`:274-296`). Its level must be at most `bard.level + XLEVEL/10` (`:269`). It must share the bard's racial category, or match a RACE or RACECAT parameter (`:298-322`). The proficiency adjustment is `-(d*d)/(1+XLEVEL)`, where `d = bard.level + XLEVEL/10 - target.level` (`:353-359`). Both branches give the same sign. On success the target follows and gets a forever effect (`:372-377`). When the master trades with the friend, the master gets CHA +10 for that message (`:117-130`). If the master is gone, the friend wanders home, or is destroyed when it has no start room (`:233-247`).
- **Satire** (`Skill_Satire.java:82-177`). The bard must be in a room with a legal behaviour and must address an officer or judge (`:111-117`). Group members with warrants are forgiven (`:118-127`, `:163-170`). A failed mock adds a warrant with probability `100 - CHA` (`:155-156`). A successful one carries a penalty of `-5*(excess level)` (`:144-150`) and a `25 - CHA` warrant risk (`:171-173`). The crime is "disrespect for the law" (`:84`).
- **False Arrest** (`Skill_FalseArrest.java:88-167`). Finds a target that has a warrant, in the local area or any accessible area (`:105-132`), then arrests it with `B.arrest` (`:160`). The mana override is 50 (`:77-79`). The same level penalty as Satire applies (`:143-149`). Despite the name, the code requires an existing warrant.
- **Joke** (`Skill_Joke.java:82-182`). Seven joke templates are built from two word lists, `resources/skills/insulto.txt` (60 lines) and `resources/skills/insultd.txt` (63 lines). The lists are read from disk on each use (`:109-110`). The chance is 25% for Spell_Laughter and 75% for Disease_Giggles (`:165-174`). The crude content is not reproduced here.

### 9. Health and damage

- **Light Placebo** (`Skill_LightPlacebo.java:128-308`). Heals `roll(2, level, 4)` (`:255`) for 500 ticks (`:165`, `:288`). Healing goes into a fake-HP pool (`fakeHp`). `doAdjustments` claws back any HP above `baseHp` into the pool (`:131-157`). The next incoming damage is raised by `fakeHp` and the effect ends (`:198-206`). The pending heal is carried in misc text as `"+N"`, applied by `setMiscText` (`:159-191`, `:295`).
- **Placebo** (`Skill_Placebo.java:65-69`). Same mechanism, healing `roll(5, level, 10)` (`:68`).
- **Break A Leg** (`Skill_BreakALeg.java:94-175`). Listeners with a leg get a curse for `20 + XTIME` ticks (`:164`). Each tick there is a `5 + XLEVEL` percent chance that the listener gets BrokenLimbs on the leg (`:109-114`).
- **Fire Breathing** (`Skill_FireBreathing.java:120-167`). Needs a held burning item (`:132-137`), which is consumed (`:159-160`). Dice count is `round((level + 2*X1)/1.5) + 1` (`:151`). Damage is `roll(3, numDice)`, halved when the second message is resisted (`:152-154`). Range is 0 to 5 (`:78-87`).

### 10. Learning and copying

- **Imitation** (`Skill_Imitation.java:82-199`). Auto-invoked and not removable (`:83-92`). It watches the four craft skills. When the bard crafts a spell it has just seen used by someone else, it records that spell's others-message (`:117-146`). Learned messages are replayed as cosmetic output (`:163-193`).
- **Mimicry** (`Skill_Mimicry.java:88-250`). Records the last emote or social in the room (`:92-105`) and replays it as the bard on the next tick (`:150-221`). A `disabled` flag stops recursion (`:97`, `:206`, `:219`).

### 11. Craft subclasses

`Skill_Chantcraft` and `Skill_Prayercraft` (56 lines each) only set `craftType()` to ACODE_CHANT and ACODE_PRAYER over `Skill_Songcraft` (`:51-55`). `Skill_Songcraft` (`Songs/Skill_Songcraft.java:95`, `:113`) is in the neighbouring bucket.

## Content worth mining

### Songs (12 files)

| Song | Class | Quality | Effect |
|---|---|---|---|
| Rhythm | `Play_Rhythm` | MALICIOUS | Magic save lowered for listeners in scope, except the bard (`:64-65`) |
| Solo | `Play_Solo` | BENEFICIAL_OTHERS | Exclusive; cancels other bards' songs in the room at 10 mana each; upstage contest |
| Tempo | `Play_Tempo` | BENEFICIAL_OTHERS | Extra tick for listeners' common skills |
| Tribal | `Play_Tribal` | BENEFICIAL_OTHERS | Keeps beneficial chants in the room from expiring |
| Spiritual | `Play_Spiritual` | BENEFICIAL_OTHERS | Keeps prayers at 1 tick from expiring |
| Symphony | `Play_Symphony` | per instrument | Persistent band effects (table below) |
| Trumpets | `Play_Trumpets` | MALICIOUS (Spell_Shatter, `:61`) | One-shot Shatter on each listener |
| Violins | `Play_Violins` | MALICIOUS (Spell_Frost, `:69`) | One-shot Frost |
| Trombones | `Play_Trombones` | beneficial (Spell_Frenzy, `:63`) | One-shot Frenzy |
| Tubas | `Play_Tubas` | MALICIOUS (Spell_GustOfWind, `:69`) | One-shot Gust of Wind |
| Woods ("Wood Clappers") | `Play_Woods` | beneficial (Spell_Shield, `:63`) | One-shot Shield |
| Xylophones | `Play_Xylophones` | MALICIOUS (Spell_Slow, `:63`) | One-shot Slow |

The quality of an instrument song is taken from its mimic spell (`Songs/Play_Instrument.java:124-129`). The spell files are under `Abilities/Spells/`.

### Symphony effect table (from `Songs/Play_Symphony.java:94-612`)

Bands are instrument level thresholds, read top to bottom. "per N" is the tick chance divisor.

| Instrument | Bands and effects |
|---|---|
| Clarinets | <=2 cast Lightning (per 10); <=11 -Electric save; <=15 +electric damage (per 3); <=18 -Electric save; <=21 -electric damage (per 5); higher +Electric save |
| Cymbals | <=2 +Dexterity; <=3 speed common skills; <=4 cast Haste (per 10); <=9 strip charm effects; <=13 +Dexterity; higher +Poison save |
| Drums | <=3 cast Disarm (per 10); <=5 -Justice save; <=6 cast Deafness (per 10); <=7 +Justice save; <=8 cast Whomp (per 10); <=16 +Justice save; higher -Justice save |
| Flutes | Cast by band, each per 10: Fear, Confusion, Hunger, Shrink, Charm, Insatiable Thirst |
| Guitars | <=4 +Fire save; <=5 fire damage (per 5); <=6 +Fire save; <=9 -Fire save; <=12 +Fire save; <=13 -Fire save; higher fire damage (per 3) |
| Harmonicas | Armour adjustment at level/5 for all levels |
| Harps | +Charisma (level/10) |
| Horns | <=4 -Acid save; higher +Acid save |
| Oboes | <=4 +Acid; <=7 +Acid; <=9 acid damage (per 3); <=12 cast Nightmare (per 10); <=17 -Acid; <=19 +Acid; higher -Acid |
| Organs | <=22 cast Cure Light (per 2); <=24 Cure Critical (per 10, +10 level); <=25 Cure Serious (per 5); higher Heal (per 20, +20 level) |
| Pianos | <=19 -Mind save; higher +Mind save |
| Trombones | +Constitution |
| Trumpets | Attack adjustment at level/2 (see Problems) |
| Tubas | +Strength |
| Violins | <=7 -Cold save; <=16 +Cold save; <=17 -Cold save; higher cold damage (per 3) |
| Woods | Armour adjustment at level/2 |
| Xylophones | <=11 cast Restore Voice (per 10); <=12 Remove Paralysis (per 10); <=15 +Disease save; <=18 cast Exhaustion (per 10); higher +1 movement per tick |

### Skills (27 files)

| Skill | Class (lines) | Quality | What it does |
|---|---|---|---|
| Juggle | `Skill_Juggle` (534) | INDIFFERENT | Floats up to 5+ held items; throws them in combat |
| Disguise | `Skill_Disguise` (468) | OK_SELF | Fake name, race, sex, class, alignment, age, level; skill-gated |
| Befriend | `Skill_Befriend` (388) | INDIFFERENT | Permanent follower from a same-category monster |
| Light Placebo | `Skill_LightPlacebo` (310) | BENEFICIAL_OTHERS | Heal as a loan, paid back on the next hit |
| Center of Attention | `Skill_CenterOfAttention` (272) | MALICIOUS | Victim cannot move; 75-tick cooldown |
| Buffoonery | `Skill_Buffoonery` (261) | MALICIOUS | Swaps a junk item onto the target's body |
| Mimicry | `Skill_Mimicry` (251) | OK_OTHERS | Replays the last emote or social |
| Puppeteer | `Skill_Puppeteer` (241) | INDIFFERENT | Animates a " puppet" item that flies and attacks |
| Monologue | `Skill_Monologue` (224) | INDIFFERENT | Audience cannot move or speak while the bard talks |
| Curtain Call | `Skill_CurtainCall` (210) | BENEFICIAL_OTHERS | Audience heals 10% HP and mana per tick while bowing |
| Exit Stage Left | `Skill_ExitStageLeft` (208) | MALICIOUS | Audience is walked out the west exit |
| QuickChange | `Skill_QuickChange` (204) | INDIFFERENT | Stows and swaps the whole outfit |
| Imitate | `Skill_Imitation` (200) | OK_SELF | Learns and replays other casters' messages |
| Ad Lib | `Skill_AdLib` (193) | INDIFFERENT | Copies the next ability the target uses (3 ticks) |
| Cast Blocking | `Skill_CastBlocking` (185) | INDIFFERENT | Re-splits a melee into two opposing sides |
| Joke | `Skill_Joke` (184) | INDIFFERENT | Insult joke; 25% Spell_Laughter, else Disease_Giggles |
| Satire | `Skill_Satire` (179) | INDIFFERENT | Mocks an officer to forgive group warrants; risk of a warrant |
| Break A Leg | `Skill_BreakALeg` (175) | MALICIOUS | Curse that rolls to break a leg each tick |
| Conduct Symphony | `Skill_Conduct` (170) | not set in file | Replays the bard's Symphony on the group |
| False Arrest | `Skill_FalseArrest` (169) | INDIFFERENT | Arrests a target that has a warrant; 50-mana override |
| Fire Breathing | `Skill_FireBreathing` (168) | MALICIOUS | Burning breath; consumes a held fire source |
| Mark Disguise | `Skill_MarkDisguise` (153) | inherits Disguise | Copies a marked target after 15 ticks of study |
| Instrument Bash | `Skill_InstrumentBash` (151) | MALICIOUS | Weapon hit with the held instrument |
| Shuffle | `Skill_Shuffle` (148) | INDIFFERENT | Randomizes the room's occupant order |
| Placebo | `Skill_Placebo` (71) | inherits Light Placebo | Stronger heal: `roll(5, level, 10)` |
| Chantcraft | `Skill_Chantcraft` (56) | not set in file | Songcraft with craftType ACODE_CHANT |
| Prayercraft | `Skill_Prayercraft` (56) | not set in file | Songcraft with craftType ACODE_PRAYER |

## Notable design ideas

- **Replay widens the audience.** Playing the same song again within 30 seconds grows its radius (`Songs/Play_Instrument.java:67-81`). Other songs grow through the depth counter (`Songs/Play.java:476-489`). Sustained performance reaches further.
- **A performance is an aura owned by its source.** Each listener holds a copy, and the copies lapse when the source stops, after a grace period set by the source's skill (`Songs/Play.java:417-421`).
- **Stage verbs as crowd control.** Exit Stage Left walks targets out, Monologue freezes them, Cast Blocking re-splits melee, and Shuffle reorders them. Each is a concrete area-control action with its own cost.
- **Table-driven instrument effects.** Symphony maps each instrument to level bands (`Songs/Play_Symphony.java:104-612`). The data is easy to read and to tune.
- **Instruments as carriers.** An instrument carries another spell (`Songs/Play_Instrument.java:59-62`, `:118-129`). Puppets and fire sources do the same job for other skills.
- **Learning by watching.** Imitation records others' spell messages and replays them (`Skill_Imitation.java:117-146`).
- **Wardrobe as text.** QuickChange stores an outfit as XML in misc text (`Skill_QuickChange.java:156-173`).
- **Disguise as attribute bundle.** Disguise keeps its settings as key-value text (`Skill_Disguise.java:129-146`), and each attribute is gated by skill level (`:47-55`).
- **Applause economy.** Curtain Call turns an audience into a healing source at a movement cost (`Skill_CurtainCall.java:132-139`).
- **Delayed injury.** Break A Leg is a curse with a per-tick roll for a lasting injury (`Skill_BreakALeg.java:109-114`).
- **Warrants as a crime loop.** Satire and False Arrest use a warrant and arrest system (`Skill_FalseArrest.java:160`, `Skill_Satire.java:163-173`).

## Problems and cruft

Defects listed here were found by reading the code. None of them was run.

**Likely bugs:**

1. **Conductor always skipped.** `Skill_Conduct.java:136-137` calls `Play.getInstrument(mob, null, false)`. With a null type, `Play.java:466-468` treats every real instrument as a type mismatch, so the call returns null. The self check is therefore always true, and the conductor never receives the symphony. The fix is to pass `InstrumentType.OTHER_INSTRUMENT_TYPE`.
2. **Faction check cannot fail.** `Skill_Befriend.java:327-328` reads `target.fetchFaction` for both `his` and `mine`, so the comparison at `:329-333` is always equal. `mine` should come from `mob`.
3. **Wrong max HP in heal clamp.** `Skill_LightPlacebo.java:291` checks against `target.maxState()`, but `:292` clamps to `mob.maxState()`.
4. **Attack adjustment overwritten.** `Songs/Play_Symphony.java:690` sets `setAttackAdjustment(stats.armor()+lvl)`. It uses the armour value, and it overwrites rather than adding to the existing attack adjustment.
5. **Dead message in Imitate.** `Skill_Imitation.java:172-177` tests `found!=null` inside the `found==null` branch, so "'@x1' is not something you know how to imitate." can never print. Line `:133` tests a tool ID against a map keyed by names (`:137`), so that duplicate check never matches. The effect is harmless.
6. **Prefix ambiguity in Disguise.** `Skill_Disguise.java:263-266` has no `break`, so "a" selects AGE (the last match) and not ALIGNMENT.
7. **Disguise description block looks unreachable.** `Skill_Disguise.java:212` tests `msg.amITarget(this)`, which compares the target with the ability object (`Common/DefaultMessage.java:571-574`). Examine messages target the examined mob, exit or room (`Commands/Examine.java:150`, `:163`). The weight and height text at `:223-230` therefore never appears. The okMessage path at `:186` tests `amITarget(mob)` and does work. This is unverified at runtime.
8. **Dead level penalties.** `Skill_CenterOfAttention.java:248-252` computes `levelDiff` and never uses it. In `Skill_Buffoonery.java:214-220`, `levelDiff` is computed after `proficiencyCheck(mob,0,auto)` at `:216` and is never used, so the intended penalty is dropped.
9. **Puppet veto probably never matches.** `Skill_Puppeteer.java:93-98` needs `targetMinor()==TYP_ORDER` together with a hands or move source. The Order command builds its message with source MSG_SPEAK (`Commands/Order.java:182`), and `CMMsg.java:1573` defines MSG_ORDER as sound and mouth. The comment at `Skill_Puppeteer.java:90-92` describes a much broader lock. The condition looks inverted or too narrow. This is unverified at runtime.
10. **Satire returns false on success.** `Skill_Satire.java:163-169` returns false from inside the forgiveness loop, even though the mock succeeded.
11. **Fire source destroyed on veto.** `Skill_FireBreathing.java:159-160` destroys the source even when the attack messages were vetoed. It sits outside the okMessage check.
12. **Placebo sentinel.** `Skill_LightPlacebo.java:129` starts `fakeHp` at -1. The first heal therefore gives `healing - 1`, so a 1-point heal ends at once (`:170-171`). If the effect is saved with its misc text, reloading could call `setMiscText("+N")` again and reapply the heal. This is unverified.
13. **Unguarded index in Juggle.** `Skill_Juggle.java:424` forces `whatToJuggle` to "all" for a monster with a MOB target, even when `commands` is empty. `:453` then calls `commands.get(0)` with no guard. Callers were not checked.
14. **Imitate's learned map is not saved.** `Skill_Imitation.java:113` keeps `immitations` as a field. It is not part of `text()` or `setMiscText`, and `StdAbility.java:621-624` returns only misc text. The learned spells are probably lost on save. Unverified against the save code.
15. **Mark Disguise "!" shortcut.** `Skill_MarkDisguise.java:90-91` reads the instance field `mark`. That field is set only by an earlier invoke on the same object (`:114`).
16. **QuickChange on NPCs.** `Skill_QuickChange.java:190` passes `mob.session()`, which is null for NPCs. Whether `addItemsFromXML` tolerates null is unverified.
17. **Orphaned friend destroyed.** `Skill_Befriend.java:238-239` destroys a befriended monster whose master is gone, if it has no start room.

**Cruft:**

- `Skill_Mimicry.java:107-148` defines `fixSNameTo`, which this class never calls. `Behaviors/Mime.java:88` has its own copy.
- `Skill_AdLib.java:134-138` has an unreachable `hasAble` branch, because the guard at `:142` requires `!hasAble`.
- `Skill_Conduct.java:111` creates a throwaway ability with `new Play()` just to call a protected method.
- `Skill_Joke.java:109-110` reads two resource files on every use.
- `Skill_Monologue.java:173` hard-codes 4000 ms rather than deriving it from the tick length.
- `Songs/Play_Symphony.java:61-71` makes only DOWNSAVE malicious. The malicious-spell codes are therefore classed beneficial, which affects the message flags (`Songs/Play.java:717-718`) and the mindAttack flag (`:191-194`).
- `Skill_InstrumentBash.java:134-141` creates a weapon per bash and never destroys it. `Skill_Juggle.java:379` does destroy its equivalent. Whether this matters is unverified.
- `Skill_Juggle.java:311-315` calls `juggle()` on every message, which is wasteful.
- `Skill_Befriend.java:282` has the typo "cann't".

## Takeaways for a Python SUD

1. **Performance as an owned object.** Model a song as `Performance(source, instrument, depth, effects)`. The tick removes listeners when the source stops or when they leave scope, and the grace period belongs to the source. In a single-player game the audience is NPCs in rooms, so there are no rival players to guard against.
2. **Band tables as data.** Symphony's effect table fits a dict of `[(max_level, effect), ...]` lists, read top to bottom. The effects can live in YAML or JSON so designers can tune them without touching code.
3. **Port the integer maths carefully.** Java truncates integer division toward zero, and Python's `//` floors. For example `(-7)//4` is -2 in Python and -1 in Java. Use a helper such as `int(a / b)` when porting formulas like `(CHA-10)/4` so negative stats match.
4. **Replace the veto pipeline.** Many of the defects above come from the okMessage and executeMsg message flow: Puppeteer, Solo and Buffoonery all have this trouble. A simple event hook with an explicit `cancelled` flag would be clearer.
5. **Rethink the multiplayer parts.** Solo's upstage contest assumes a second player. Replace it with a rival NPC bard. Monologue's suspension of the audience's ticking is useful: in a turn-based design, the NPCs simply skip their updates while the player performs.
6. **Keep the stage verbs.** Monologue, Curtain Call, Exit Stage Left, Cast Blocking, Shuffle and Center of Attention each give a distinct area-control action with a cost. The Satire and False Arrest warrant loop gives NPC officers a simple justice model.
7. **Simplify the wardrobe.** QuickChange's XML round trip is heavy. A stash of item IDs is enough for a Python version.
8. **Name the constants.** The 500 ms juggling rotation, the 30 s replay window, the 4000 ms monologue threshold and the 75-tick cooldown are all hard-coded here. Make them named settings.

## Coverage

- **Read in full:** all 39 manifest files (12 `Play_` and 27 `Skill_`).
- **Read in full, outside the manifest (base classes):** `Songs/Play.java`, `Songs/Play_Instrument.java`, `Songs/BardSkill.java`.
- **Cross-checked with targeted reads or grep:** `Abilities/StdAbility.java:621-624`, `:1916`, `:2843-2858`; `MOBS/StdMOB.java:5264-5272`; `Common/DefaultMessage.java:571-574`; `Common/DefaultCharState.java:310-323`; `core/CMProps.java:681-682`, `:2727-2729`; `Commands/Examine.java:150`, `:163`; `Commands/Order.java:182`; `Common/interfaces/CMMsg.java:1573`; the quality returns in `Abilities/Spells/` for Shatter, Frost, Frenzy, GustOfWind, Shield and Slow; `Behaviors/Mime.java:88`; `Abilities/Thief/Thief_Assassinate.java:301` (signature only); `Songs/Skill_Songcraft.java:95`, `:113` (grep only); `resources/skills/insulto.txt` and `insultd.txt` (line counts only; contents not reproduced).
- **Skipped:** none within the bucket.
- **Not done:** nothing was executed. Items marked unverified or "appears" were inferred from reading the code alone.
