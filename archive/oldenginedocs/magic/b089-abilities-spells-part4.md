# Spells, part 4: Flaming Ensnarement to Immunity

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b089` (Abilities/Spells [part 4/9: Spell_FlamingEnsnarement .. Spell_Immunity]) · 51 files · 9127 lines of Java.

## Summary

These 51 classes are the middle slice of CoffeeMud's spell family. Each extends `Spell` (`com/planet_ink/coffee_mud/Abilities/Spells/Spell.java:36`), which extends `StdAbility`, so the cast pipeline, proficiency roll, cost model and duration model are shared. The slice covers most of what a spell can do: direct damage (Frost, Ice Storm, Ice Lance, Forked Lightning, Gravity Slam), status and mind effects on a target (Hold, Grease, Forget, Frailty, Endless Hunger, Future Death, Geas), room and exit effects (Ice Sheet, Ghost Sound, Illusory Wall), scrying on distant places (Greater Scry, Greater Clairvoyance, Greater Claireaudience), travel (Gate, Greater Levitate), and item enchantment and repair (Greater Enchant Weapon and Armor, Greater Mend, Harden, Flaming Sword). The distinctive pieces are Helping Hand, a summoned guide item that paths to a named character, and Geas, which turns a plain-English order into steps for the `SlaveryParser` engine. Most of the rest is formulaic, so the useful material is the shared mechanics plus those standouts.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/`. Line counts are from the manifest. The first block is the shared base and helpers this bucket depends on; the second block is the 51 bucket files.

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Spells/Spell.java` | Base for every spell: classification `ACODE_SPELL`, the armour and concentration gate, and the mage overload rule | 162 |
| `Abilities/StdAbility.java` | Shared cast pipeline (`invoke`), proficiency roll, cost codes, tick-down and effect cap (only the sections cited here were read) | 3018 |
| `Libraries/MUDFight.java` | `properTargets()` for area spells, including the pre-cast range filter (only lines 163-200 read) | 3817 |
| `Libraries/SlaveryParser.java` | Geas engine: order-to-step pattern table, `processRequest`, step interpreter | 1156 |
| `Libraries/interfaces/SlaveryLibrary.java` | Interface for `processRequest`, `GeasSteps` and `GeasStep` | 174 |
| `Abilities/Spells/Spell_MinorImage.java` | Parent of Greater Image (outside this bucket, cited for its duration and sight hooks) | 442 |
| `Spell_HelpingHand.java` | Beneficial. Summons a flying "helping hand" item that paths to a named character, opens doors, and gives up after 40 frustration ticks | 347 |
| `Spell_GreaterLevitate.java` | Malicious. Lifts a mob or item one room per tick for 5+XLEVEL ticks, with an optional rise limit; blocks the target's own movement | 308 |
| `Spell_FleshStone.java` | Malicious. Turns a mob into a granite statue item, strips its effects, and pins it in place | 292 |
| `Spell_ForcefulHand.java` | Beneficial, combat only. A flying hand item in the room pushes attackers to melee range | 250 |
| `Spell_GreaterScry.java` | Scrying on a room or area. Forwards other people's looks and examines to the caster; costs all mana; duration 25 percent of standard | 243 |
| `Spell_IceSheet.java` | Malicious room effect. Ice makes movement risky and freezes anyone underwater | 238 |
| `Spell_GroupStatus.java` | Divination. Places a marker effect on each group member; reports low HP and malicious effects to the caster | 234 |
| `Spell_Grease.java` | Malicious. -4 DEX; random sit, dropped weapon or both on move, attack, throw or flee | 233 |
| `Spell_Geas.java` | Enchantment. Compels a target to carry out a spoken order via `SlaveryParser`; 2000 ticks; NPCs cannot cast it | 229 |
| `Spell_HeatMetal.java` | Malicious. Heats metal items in a target's pack; each tick they burn the holder and may be dropped | 221 |
| `Spell_GreaterClairevoyance.java` | Scrying (sight) on a room or area. Forwards looks and examines; duration 50 percent of standard | 216 |
| `Spell_Gate.java` | Transportation. Moves the caster and followers to a named inhabitant; a failed roll sends them to a random safe room | 214 |
| `Spell_FlamingEnsnarement.java` | Malicious. Holds the target in flames; escape pool of 60 x speed, reduced by level on each move try; fire damage each tick | 213 |
| `Spell_GreaterClaireaudience.java` | Scrying (hearing) on a room or area. Forwards speech; duration 50 percent of standard | 213 |
| `Spell_Grow.java` | Enlarges a mob: height and weight up, DEX halved, STR up. Cancels `Spell_Shrink` if present | 212 |
| `Spell_IllusoryDisease.java` | Malicious. A fake disease that halves STR and produces cosmetic coughing; implements `DiseaseAffect`, does not spread | 206 |
| `Spell_FloatingDisc.java` | Beneficial, item. Floats an item (weight 0) for (level+2X)x30 ticks; borrows other people's items and returns them | 204 |
| `Spell_GustOfWind.java` | Malicious. Pushes the target to a longer range; may knock it down | 196 |
| `Spell_Immunity.java` | Beneficial. Grants immunity to one element (acid, fire, gas, cold, electricity) | 196 |
| `Spell_GhostSound.java` | Malicious room effect. Random ambient noise lines in the room | 185 |
| `Spell_FlamingSword.java` | Item. A metal sword burns; each hit by its owner adds fire damage; the sword is destroyed when the effect ends | 183 |
| `Spell_FutureDeath.java` | Malicious divination. Death one second after the cast unless a mind save succeeds | 180 |
| `Spell_HearThoughts.java` | Divination. Lists nearby characters with trait words, legal status and the "thoughts" their behaviours report | 180 |
| `Spell_HardenBullets.java` | Item. Sling bullets gain a fixed bonus damage per hit | 175 |
| `Spell_Forget.java` | Malicious mind effect. Target intermittently cannot use abilities it knows | 172 |
| `Spell_Frenzy.java` | Beneficial. Berserk: more damage and attack, +30+3X armour, extra HP | 172 |
| `Spell_Ignite.java` | Malicious. Sets unworn items on a target (or a target's pack) alight; burn time depends on material | 167 |
| `Spell_Harden.java` | Item. Armour bonus for siege items only; restores lost uses over time | 166 |
| `Spell_FreeMovement.java` | Beneficial. A barrier that repels paralysis and other malicious abilities, tested by a dummy mob | 161 |
| `Spell_Hungerless.java` | Beneficial. Holds hunger at maximum; ends by setting hunger to 0 | 156 |
| `Spell_FoolsGold.java` | Creates a weightless fake gold pile that vanishes if someone else takes or gives it | 154 |
| `Spell_Frailty.java` | Malicious. Non-spell damage to the target rises by a third, for 10 ticks | 150 |
| `Spell_Hold.java` | Malicious. Paralysis (`CAN_NOT_MOVE`); 1 to 7 ticks | 148 |
| `Spell_GiantStrength.java` | Beneficial. STR bonus by base class, plus (X+2)/3 | 147 |
| `Spell_GraceOfTheCat.java` | Beneficial. DEX bonus by base class, computed once per effect instance | 145 |
| `Spell_IdentifyObject.java` | Item. Reveals the secret identity; lists the spells on spell-holder items in the name | 144 |
| `Spell_GreaterEnchantArmor.java` | Permanent armour enhancement: +1 ability and +3 level on the base stats; about 250 XP cost | 143 |
| `Spell_Fly.java` | Beneficial, mob or item. Sets `IS_FLYING` | 142 |
| `Spell_GreaterEnchantWeapon.java` | Permanent weapon enhancement, same rules as the armour version | 142 |
| `Spell_GreaterInvisibility.java` | Beneficial, mob or item. Sets `IS_INVISIBLE` | 137 |
| `Spell_Friends.java` | Beneficial. CHA +6 | 132 |
| `Spell_Haste.java` | Beneficial. Speed raised by `CMProps.getSpeedAdjustment()` scaled by 1+0.1X | 132 |
| `Spell_IceStorm.java` | Malicious, area. Blast and hail on every proper target in the room | 132 |
| `Spell_Frost.java` | Malicious. Cold damage: maxDie d6 plus 5 | 130 |
| `Spell_ForkedLightning.java` | Malicious, area. Electrical damage to every proper target | 127 |
| `Spell_GravitySlam.java` | Malicious. Fall damage that depends on flight and terrain | 127 |
| `Spell_IllusoryWall.java` | Exit effect. Makes an exit invisible in one direction | 127 |
| `Spell_IceLance.java` | Malicious. Cold lance at range 1 to 5 | 125 |
| `Spell_GreaterMend.java` | Item repair. Raises `usesRemaining` (0 to 100) by weight-based points | 119 |
| `Spell_Hunger.java` | Malicious. "Endless Hunger": removes 150+5x(level+2X) hunger at once | 110 |
| `Spell_GreaterImage.java` | Extends `Spell_MinorImage`. A longer image: duration 20+level/2 | 75 |

## How it works

### 1. Cast pipeline and mana

Every spell follows the same order. The subclass resolves its target first. Area spells call `StdAbility.properTargets` (`Abilities/StdAbility.java:2113-2125`), which calls `CMLib.combat().properTargets` (`Libraries/MUDFight.java:163`). For beneficial spells that is the caster's group members in the same room. For malicious spells it is the current combatants, or everyone who might fight. A pre-cast range filter then drops targets outside `minRange()` and `maxRange()` so that no mana is spent on an empty set (`MUDFight.java:184-189`).

Then `Spell.invoke` (`Spell.java:154-161`) calls `StdAbility.invoke` (`StdAbility.java:1964`). For a non-auto cast it checks, in order:

- alive, awake and mobile (`StdAbility.java:1974`);
- the cooldown from `getTimeOfNextCast` (`:1987`; the base `getTicksBetweenCasts()` returns 0, `:201-204`);
- `usageCost` (`:1998`), then a compounding penalty for repeated casts inside a window (`:2001-2030`);
- `testUsageCost` (`:2050`), a minimum recovery time (`:2055-2062`), and reagent components (`:2067`).

Mana, movement and HP are then deducted (`:2074-2076`), `helpProficiency` runs (`:2077`), and a `MSG_PREINVOKE` message is offered to the room (`:2078-2080`).

Next, `Spell.spellArmorCheck` (`Spell.java:132-151`) runs for non-auto casts by players. Half the time (`rollPercentage()<50`, line 139) armour that is not cloth absorbs the magic and the spell fails: "watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (line 141). A failed concentration check gives "can't seem to concentrate" (lines 144-147). Because the deduction happens before this check, an absorbed or blocked spell still costs its mana.

Cost codes are in `Abilities/interfaces/Ability.java:651-655`. `COST_ALL` (`Integer.MAX_VALUE`) takes all mana. `COST_PCT` is `Integer.MAX_VALUE-100`, so `COST_PCT+n` means n percent of maximum. The conversion is at `StdAbility.java:1617-1618`: `rawCost = maxAmt * (COST_ALL-consumed) / 100`. Gate uses `COST_PCT + 50`, which is half of maximum mana (`Spell_Gate.java:65-68`). The Greater Scry, Greater Clairvoyance, Greater Claireaudience and both Greater Enchant spells use `COST_ALL` (for example `Spell_GreaterScry.java:84-89`, `Spell_GreaterEnchantWeapon.java:72-75`). Other spells take their cost from the ability mapper (`StdAbility.java:1790-1805`); that table was not read.

### 2. The proficiency roll

`proficiencyCheck(mob, adjustment, auto)` (`StdAbility.java:1441-1486`) works in this order:

1. Start from `proficiency()` (`:1452`), plus any `PROF` adjustments from the caster's `CharStats`.
2. Add `2*XLEVEL` (`:1468`). Clamp to 5 to 95 unless `DIS955RULE` is set (`:1472-1476`). Then add `XLEVEL/2` (`:1478`).
3. If `adjustment >= 0`, add it (`:1480-1481`). If it is negative, fail outright when `roll > 100+adjustment` (`:1483-1484`).
4. Succeed when `roll < pctChance` (`:1485`).

A negative adjustment is therefore a separate difficulty gate before the normal roll. Each spell chooses its own adjustment, and that choice is its difficulty model:

| Spell | Adjustment passed | Source |
|---|---|---|
| Hold | `-(levelDiff*5)`, levelDiff clamped to 0..6 | `Spell_Hold.java:114-123` |
| Forget | `-((INT*2) + levelDiff*5)` | `Spell_Forget.java:148` |
| Future Death | `-((WIS*2) + (levelDiff+6)*15)` | `Spell_FutureDeath.java:101` |
| Gate | `-adjustment`, where adjustment is the target-minus-caster level gap, times 3 for NPC targets | `Spell_Gate.java:156-162` |
| Frailty | `(caster level + 2X) - target level` | `Spell_Frailty.java:128` |
| Harden, Greater Mend | `((caster level + 2X) - item level) * 5` | `Spell_Harden.java:148`, `Spell_GreaterMend.java:85` |

Worked example, derived from the code and not run: Future Death's `levelDiff` is the target level minus (caster level + X). For WIS 12 and a target one level below the caster, the adjustment is -(24+75) = -99. The pre-roll then passes only on a roll of 1. Two levels below gives -84, so the pre-roll passes on rolls up to 16. Against a target at the caster's level the adjustment is -114, and the pre-roll can never pass. A target three or more levels above is refused before any mana is spent (`Spell_FutureDeath.java:91-93`).

### 3. Durations: one integer, three meanings

Malicious and beneficial effects both pass a single integer, `tickAdjustmentFromStandard`, into `maliciousAffect` (`StdAbility.java:2185`) or `beneficialAffect` (`:2341`). The integer is read by `getMaliciousTickdownTime` (`:2174-2183`) and `getBeneficialTickdownTime` (`:2312-2331`) with three meanings:

- **Positive: flat ticks, used as given.** Geas 2000 (`Spell_Geas.java:206`); Grease 8 (`Spell_Grease.java:222`); Frailty 10 (`Spell_Frailty.java:140`); Gust of Wind 2 (`Spell_GustOfWind.java:180`); Hold `7 - levelDiff` (`Spell_Hold.java:134`); Greater Levitate `5 + XLEVEL` (`Spell_GreaterLevitate.java:290`); Floating Disc `(level + 2X) * 30` (`Spell_FloatingDisc.java:192`); Forceful Hand `3 + round(sqrt(level))` (`Spell_ForcefulHand.java:222`); Greater Image `20 + level/2` (`Spell_GreaterImage.java:52-55`).
- **Zero: standard.** Malicious: `round(adjustedLevel * 1.1) + 25`, scaled by the target's level gap against `EXPRATE` and floored at 2 (`StdAbility.java:2140-2171`, `:2178`). Beneficial: `adjustedLevel * 4 + 40`, floored at 5 (`:2325`, `:2279-2280`).
- **Negative: a percentage of the standard value.** Greater Scry uses -25 (`Spell_GreaterScry.java:233`); both Greater Clairvoyance and Greater Claireaudience use -50 (`Spell_GreaterClairevoyance.java:206`, `Spell_GreaterClaireaudience.java:203`); Forget uses `-levelDiff` (`Spell_Forget.java:160`), so a higher-level target keeps the effect for longer.

`startTickDown` (`StdAbility.java:698-712`) then adds `XTIME` times 20 percent (`addedTickTime`, `:640-642`, applied at `:709`), applies the class duration modifier and any `TICK+` ability adjustments. The base tick is `TIME_TICK = 4000` ms (`core/CMProps.java:681`).

Worked example, derived and not run: at adjusted level 10 and an equal-level target, a malicious standard is 36 ticks (11 + 25) and a beneficial standard is 80 ticks (40 + 40). At the default tick that is 144 s and 320 s.

Effect cap: when a mob carries more effects than `EFFECTCAP` allows, `startTickDown` picks one at random among effects that have an invoker and can be uninvoked, and removes it (`StdAbility.java:721-742`).

### 4. Fizzle channels

Failed casts use one of four helpers that match the spell's sense: `beneficialWordsFizzle` (speech, `StdAbility.java:2210`), `beneficialVisualFizzle` (gesture; hands or sight, `:2228`), `beneficialSoundFizzle` (noise, `:2246`) and `maliciousFizzle` (visual with the malicious flag, `:2253`). Spell text then says what the caster did, for example "speak(s) quickly ... but nothing more happens" for Haste (`Spell_Haste.java:126`) and "wave(s) <S-HIS-HER> arms around, incanting, but nothing happens" for Grow.

### 5. Effects as message listeners

Each spell is an `Ability` attached to its target, room, item or exit, and it changes play through the message bus. It can veto a message in `okMessage`, react in `executeMsg`, and change stats in `affectPhyStats`, `affectCharStats` or `affectCharState`. Examples:

- **Grease** handles leave, enter, weapon attack, throw and flee in `okMessage` (`Spell_Grease.java:106-117`). A slip happens when `roll > DEX*4`. Flying mobs only fumble (`:120`).
- **Frailty** raises the damage value of non-spell attacks: `msg.setValue(value + round(value/3))` (`Spell_Frailty.java:96-97`). Only messages whose tool is a non-spell skill or ability qualify (`:85-92`).
- **Immunity** vetoes damage or malicious messages whose source type matches the chosen element (`Spell_Immunity.java:147-165`). The veto is unconditional when the target has the spell only as an effect (`:157`), and rolls proficiency when the target knows the spell.
- **Free Movement** tests each incoming malicious ability. It applies that ability's `affectPhyStats` to a dummy mob from `CMClass.getFactoryMOB()` and checks whether the dummy would still be able to move (`Spell_FreeMovement.java:98-125`). Paralysing abilities are refused outright (`:104-108`).
- **Ice Sheet** vetoes movement with a slip roll of `roll > DEX*3 + 25` (`Spell_IceSheet.java:122-133`). Underwater, it blocks any action not marked `MASK_ALWAYS` (`:115-120`).
- **Hold** sets `CAN_NOT_MOVE` in the sense mask (`Spell_Hold.java:85-89`). **Flesh Stone** sets no-move, no-hear, no-smell, no-speak, no-taste and "not seen" (`Spell_FleshStone.java:162-171`).

### 6. Specific systems

**Helping Hand** (`Spell_HelpingHand.java`). Target resolution first tries a player in the game by name (`:291`), then group members whose name or display text matches (`:295-304`). The spell runs only after target resolution, so a bad target costs nothing (`:311`). The summoned item is a `GenItem` named "a helping hand", set flying, not gettable, and placed in the room (`:322-331`). `beneficialAffect` then attaches the effect, and the hand is told the target's name and the target is set as the affected mob (`:331-335`).

Each tick (`:157-253`), the hand ends the effect if the target is gone, the caster is gone, or both share a room (`:163-171`). If the target has left the hand's room, the hand floats to the target's room every other tick (`:178-180`) and the frustration counter rises (`:203`). The messages escalate at frustration 3, 10, 20, 30 and 40 (`:181-195`). At 40 the hand slaps the target and disappears (`:193-199`). Otherwise the hand finds a trail with `CMLib.tracking().findTrailToRoom(..., 1000)` (`:207-208`) and a next direction (`:209`). It picks a lock or opens a door with a factory mob that issues `OPEN` (`:223-246`), moves one step (`:248`), and, if the target is an NPC in the caster's group, walks it along (`:249-250`).

**Gate** (`Spell_Gate.java`). The target is found by name across every room with `CMLib.hunt().findInhabitantsFavorExact` (`:138`) and then picked at random from up to 10 candidates (`:146`). Casting at someone in the same room is refused with "Better look around first." (`:122-126`). Level difference adjusts the roll, times 3 for NPCs (`:156-162`). A failed roll (`:163-177`) looks for a random room that is not the caster's, not restricted, and not land-titled (`isBadRoom`, `:82-89`), for up to 1000 tries (`:168-169`). Then the caster and group followers (`properTargetList`, `:183`) leave and enter with messages and an MSP "appear.wav" cue (`:190`). Anyone in combat flees first (`:194-198`), then `bringMobHere` and a look (`:200-203`).

**Geas and the step engine** (`Spell_Geas.java`, `Libraries/SlaveryParser.java`). A monster cannot cast Geas and gets a quip instead (`Spell_Geas.java:163-168`). The target is the first word group, and the target must have INT 5 or more (`:175-184`). `CMLib.slavery().processRequest` (`:198`) turns the order into `GeasSteps`. Each step is a queue of commands. The pattern table is `SlaveryParser.java:136-255`, which holds 99 patterns in the form `{"bring %i to %m","itemfind %i;mobfind %m;give %i %m"}`. Placeholders are `%m` mob, `%i` item, `%r` room, `%d` direction, `%k` skill, `%s` social, `%g` misc words, `%c` the caster and `%n` the slave (comment at `:126-135`). Before matching, a list of filler starters is stripped (`universalStarters`, `:56-65`). If nothing matches, the request becomes a `wanderquery` step (`:436-440`, handled at `:1131`). Each tick, `Spell_Geas.tick` calls `steps.step()` (`:132-158`). `GeasStepsImpl.step` (`SlaveryParser.java:512-580`) runs the sub-steps and understands the tokens DONE, HOLD and MOVE. A leading "0" or "1" marks a sub-step that can be bothered with a question. The cast itself lasts 2000 ticks (`Spell_Geas.java:206`). The slave answers speech in the same room through `sayResponse` (`:119-128`; `SlaveryParser.java:726` and `:794`). When the steps finish, or after a fight, the effect ends (`Spell_Geas.java:150-152`).

**Greater Scry, Clairvoyance and Claireaudience.** These attach to a room or area (`canAffectCode` is `CAN_AREAS`). Target resolution accepts "here", "area", "room", the room's name, or a boardable item (`Spell_GreaterScry.java:161-203`). Casting again on the same place dismisses the spell (`:204-209`). A second caster's spell on the place is refused (`:211-215`). The effect then forwards the events it sees to the caster. Scry forwards looks and examines of the place (`:105-128`) and other messages from the area's rooms when the caster is elsewhere (`:130-148`). Clairvoyance forwards looks and examines from a place the caster is not in (`Spell_GreaterClairevoyance.java:102-126`). Claireaudience forwards speech (`Spell_GreaterClaireaudience.java:102-121`). Each forwarded copy is built with the caster as source and guarded by a recursion flag (`AtomicBoolean recurse`, `Spell_GreaterScry.java:91`).

**Future Death** (`Spell_FutureDeath.java`). On success the target sees "eyes go wide" (`:105`), and a `Runnable` is scheduled one second later with `CMLib.threads().scheduleRunnable(..., 1000)` (`:116`, `:170`; the signature is `ThreadEngine.java:272`, milliseconds). When it runs, a mind save of `roll <= getSave(SAVE_MIND)` ends the attempt (`:126-129`). Otherwise one of ten death narrations is shown and `CMLib.combat().postDeath(mob, target, null)` is called (`:130-168`). Only `amDead` and `amDestroyed` are checked before the kill (`:118-121`).

**Forget** (`Spell_Forget.java`). Each time the target uses a known ability (`msg.tool()`), `okMessage` rolls `roll > SAVE_MIND + 25` to block it (`:104-106`). The result is cached: a blocked ability goes in `forgotten`, a passed one in `remember` (`:108`, `:113`), so each ability is decided once per effect.

**Greater Levitate** (`Spell_GreaterLevitate.java`). The effect blocks the target's own movement (`okMessage`, `:94-118`). A trailing number sets the maximum rise, and -1 means no limit (`:236-245`). Each tick it walks the target up one room if the room above has an open exit (`:127-199`). It stops at the ceiling, and when a water-bound rise ends it clears any "Sinking" effect (`:170-195`).

**Enchant and repair.** Greater Enchant Weapon requires an ability of at least 3 (`Spell_GreaterEnchantWeapon.java:95`), caps at 9 plus XLEVEL/3 (`:100`), and charges about 250 XP through `getXPCOSTAdjustment` (`StdAbility.java:474-480`, `Spell_GreaterEnchantWeapon.java:109-110`). The XP is taken before the proficiency roll in the same method, so a fizzle still costs it. Success adds 1 to ability and 3 to level on the base stats (`:128-129`). Greater Mend (`Spell_GreaterMend.java:95-105`) adds `round((1 - weight/20000) * 100) + 5 + X1` uses, capped at 100. Harden (`Spell_Harden.java:85-87`) gives armour only to `SiegableItem`s; its tick (`:106-110`) restores one lost use with probability `25 + 3X` percent. Flaming Sword (`Spell_FlamingSword.java:155-159`) needs a metal or mithril sword. Each hit by its owner adds `1d((2+level)/2)+1` fire damage (`:116`).

**Immunity** (`Spell_Immunity.java:104-130`, `:184`). A preset code 1 to 5 sets acid, fire, gas, cold or electricity. Otherwise one is chosen with `roll(1,5,0)`.

**Ice Sheet** (`Spell_IceSheet.java`). It is a room effect that also sets `IS_SLEEPING` on the room (`:175-181`). Its `castingQuality` declines to cast when the caster's group contains a non-flyer, or when the target flies (`:142-157`). The NPC combat AI consults `castingQuality` when choosing spells (`Behaviors/CombatAbilities.java:559-619`).

## Content worth mining

**Geas order vocabulary.** The pattern table at `SlaveryParser.java:139-254` is a ready-made command grammar for NPC requests. Groups:

- *Kill and hunt:* "kill X", "murder X", "find and destroy X", "assassinate X" (mob to `mobfind` then `kill`).
- *Fetch and deliver:* "bring me X", "fetch X for Y", "give your X to Y", "buy X" (item found, then given to the caster or to a named mob).
- *Guide and escort:* "go to ROOM", "show me the way to ROOM", "take me to ROOM" (`find` then `follow`).
- *Items:* eat, drink, wear, wield, hold, lock, unlock, read, sit on, mount.
- *Talk:* "tell X Y", "say Y to X".
- *Skill commands:* a general "%k %i" or "%k %m" form for any skill word.
- *Refusals:* "where/who/when/what/why ..." get canned replies that say the slave does not know.
- *Filler:* "please", "I want you to", "you are ordered to" and similar starters are dropped first.

**Helping Hand frustration ladder.** Five escalating messages, paraphrased here: floats in and gestures, urgently gestures, wildly gestures, insists, and then demands while grabbing the target's collar. At 40 it slaps the target and vanishes (`Spell_HelpingHand.java:181-199`).

**Hear Thoughts descriptors.** Intelligence 10, 13 and 18 map to "intelligent", "very intelligent" and "massively intelligent". Wisdom 10, 13 and 18 map to "wise", "very wise" and "incredibly wise" (`Spell_HearThoughts.java:115-131`). Legal status can read as a judge's or an officer's "thoughts" (`:133-141`). Each behaviour contributes a sentence through `accountForYourself()`, with one of four verbs chosen at random (`:145-160`).

**Ghost Sound.** Fourteen ambient lines, each shown in the room at about 10 percent per mob tick (`Spell_GhostSound.java:88-125`). They include a bird singing, a cow mooing, a distant scream, silence, a sword dropped with a clang, and "you hear your shadow" (`:91-125`).

**Fool's Gold names.** One coin is "a gold coin", two is "two gold coins", and more is "a pile of N gold coins" (`Spell_FoolsGold.java:118-127`).

**Ignite burn times** (`Spell_Ignite.java:76-107`). Leather burns for 20 plus weight, cloth, synthetic and paper for 5 plus weight, wood for 40 plus 2 times weight, lamp oil for 5 plus weight, and coal for `20*(1+3*weight)`. Other materials do not ignite. Burning is then started with `CMClass.getAbility("Burning")` (`:107`).

**Damage formulas** (all `CMLib.dice()`; "halved" means `msg.value()>0` on a resisted message):

| Spell | Dice (lines) | Notes |
|---|---|---|
| Frost | maxDie = (min(L,50)+2·X1)/4; roll maxDie d6 + 5 (`Spell_Frost.java:113-114`) | Halved if resisted (`:118-119`) |
| Ice Storm | numDice = (L+2·X1)/4; roll numDice d15 + 10 (`Spell_IceStorm.java:115-116`) | Halved once, then halved again on a resisted hit; two `postDamage` calls, a blast and hail, each with that value (`:118-121`) |
| Ice Lance | maxDie = (L+2·X1)/2; roll maxDie d6 + 15 (`Spell_IceLance.java:108-109`) | Range 1 to 5 |
| Forked Lightning | maxDie = round((L+2·X1)/2); roll maxDie d7 + 1 (`Spell_ForkedLightning.java:110-111`) | Halved if resisted (`:112-113`) |
| Gravity Slam | maxDie = round((L+2·X1)/2); halved if target not flying, /6 underwater, /4 water surface; roll maxDie d10 + (6+maxDie) (`Spell_GravitySlam.java:99-108`) | Halved if resisted |
| Flaming Ensnarement, per tick | roll 2 d (4+X+2·X1) fire (`Spell_FlamingEnsnarement.java:159`) | Victim's struggle pool is 60 x speed (`:195`) |
| Heat Metal, per tick | 1d6+1 fire to each unworn metal item (`Spell_HeatMetal.java:112-145`) | Holder drops the item on roll < STR percent |
| Flaming Sword, per owner hit | 1 d ((2+L)/2) + 1 fire (`Spell_FlamingSword.java:116`) | |

**Stat effects by class.** Giant Strength: Fighter +4, Mage +3, Thief +2, Bard +2, Cleric +1, Druid +1, default +1, plus (X+2)/3 (`Spell_GiantStrength.java:81-100`). Grace of the Cat: Fighter +3, Mage +2, Thief +2, Bard +2, Cleric +1, Druid +1, default +1, plus (X+2)/3 (`Spell_GraceOfTheCat.java:83-98`). Haste scales speed by `CMProps.getSpeedAdjustment() * (1 + 0.1X)` (`Spell_Haste.java:82`). Friends gives CHA +6 (`Spell_Friends.java:79-82`).

**Immunity elements.** Acid (1), fire (2), gas (3), cold (4), electricity (5). Gas from the caster's own tool is refused (`Spell_Immunity.java:159-160`).

**Future Death narrations.** Ten variants, paraphrased: choking, an unseen fall, unseen blows, drowning, a kneeling execution, a thousand arrows, a lightning strike, a sickly collapse, a heart stopping, and a comic collapse (`Spell_FutureDeath.java:132-160`).

## Notable design ideas

- **Struggle pool.** Flaming Ensnarement turns escape into a counter: 60 x speed, reduced by the victim's level on each move attempt, with a fire tick each round (`Spell_FlamingEnsnarement.java:117-121`, `:159`, `:195`). Escape is a sequence of tries, not a single roll.
- **Trial application.** Free Movement tests an incoming ability on a dummy mob before letting it through (`Spell_FreeMovement.java:111-116`). It is a general way to ask "would this effect disable the target?" without special-casing each spell.
- **Escort as a tick-driven item.** Helping Hand is a separate item with its own pathing, messages and patience. The caster does not have to stay involved.
- **Scrying as event forwarding.** Greater Scry and its siblings attach to a place and copy events into the caster's stream, with a recursion guard (`Spell_GreaterScry.java:91`, `:118-148`). This is an observer pattern for distant places.
- **Negating pairs.** Grow removes `Spell_Shrink` if it is present, and otherwise enlarges (`Spell_Grow.java:~110-136`, the Shrink branch is read at the call site). Opposite spells cancel.
- **Fizzle channel by sense.** Failure text matches the mode of casting: words, gestures or noise (`StdAbility.java:2210-2270`).
- **Partial damage on resist.** Damage is halved when the message is resisted (for example `Spell_Frost.java:118-119`), so a save reduces harm without a separate damage path.
- **Geas as a compiler.** The order is parsed into a queue of commands, and the target's answers to questions feed back into the queue (`SlaveryParser.java:726-736`, `:794`). It is a small planner for NPC errands.
- **Enchantment ladder.** Greater Enchant requires three steps of earlier enchantment, caps the total, charges XP, and then adds one ability and three levels (`Spell_GreaterEnchantWeapon.java:95-129`).
- **Shared "malicious" flag.** Group members are excluded from malicious targeting (`Spell_Frost.java:119`, `Spell_Geas`, and `MUDFight.properTargets` at `:166`).
- **Mage overload.** Once a mage carries more than five malicious spells, each new malicious hit has a 1 percent chance of applying `Disease_Magepox` (`Spell.java:106-124`). It is a flavourful cost for piling on curses.

## Problems and cruft

1. **Grease roll uses a value with no case.** `greaseEffect = round(Math.random()*3)` (`Spell_Grease.java:119`) can return 0, 1, 2 or 3. The switch defines SIT (0), FUMBLE_WEAPON (1) and BOTH (2) (`:84-86`). Value 3 falls to `default` (`:161-168`), which behaves like SIT, so sitting is effectively 1/3 of outcomes and not the 1/6 the enum suggests. `Math.random()` also bypasses `CMLib.dice()`, which the rest of the family uses.
2. **Hold is a flat 1 to 7 ticks at any caster level.** `maliciousAffect(..., 7-levelDiff, ...)` (`Spell_Hold.java:134`) is positive, so it is used as-is (`StdAbility.java:2176`). Other paralysis-like effects in this bucket scale with level. Whether this is intended is unverified.
3. **Frailty only scales non-spell damage.** Spells are excluded (`Spell_Frailty.java:85-92`). This may be deliberate, but it is a surprise for players.
4. **Greater Scry's mana branch is redundant.** `overrideMana` returns `COST_ALL` in both branches (`Spell_GreaterScry.java:84-89`).
5. **Gate's "AWRY" message is never shown on the no-room path.** `addOn` is set to ", but the spell goes AWRY!!" (`:175`), but the cast message is only sent when `newRoom != null` (`:179-180`). When no room is found after 1000 tries, nothing else is shown, and the caster's mana is spent. The variable `areaName` (`:120`) holds a target name, not an area.
6. **Gate-like name confusion elsewhere.** Gate, Greater Scry and Clairvoyance all use "areaName" or "targetName" for mixed purposes. Cosmetic.
7. **Illusory Wall's comment is copied from another spell.** The `affectPhyStats` comment talks about a mob being put to sleep (`Spell_IllusoryWall.java:80-83`), but the code sets `IS_INVISIBLE` on the exit (`:84`).
8. **Harden's armour bonus is restricted to siege items.** Only `SiegableItem` gets the armour change (`Spell_Harden.java:85-87`). Ordinary armour gets only the use-restoration tick. Whether that is intended is unverified.
9. **Ice Sheet uses the beneficial duration for a malicious room effect.** `beneficialAffect(..., 0)` (`Spell_IceSheet.java:221`) gives `level*4+40` ticks, not the malicious formula. Slipping also depends on the hidden sheet item being present (`:122-123`).
10. **Grace of the Cat caches its bonus.** `increase` is computed once per effect instance (`Spell_GraceOfTheCat.java:77`, `:83-98`) and not recomputed if the mob's class changes. Giant Strength recomputes each call (`Spell_GiantStrength.java:81-100`), so the two spells disagree.
11. **Group Status snapshots membership once.** `groupMembers` is built only when it is null (`Spell_GroupStatus.java:99-108`). Members who join later never get the marker, and members who leave keep it until it expires.
12. **Hungerless ends by setting hunger to zero.** `unInvoke` calls `setHunger(0)` (`Spell_Hungerless.java:109`), so the character is starving the moment the spell ends.
13. **Future Death does not recheck location.** The delayed kill (`Spell_FutureDeath.java:116-170`) runs after one second and checks only that the target is not dead or destroyed. It does not check that caster or target are still in the room.
14. **Helping Hand's unInvoke dereferences an unchecked room.** `R.isHere(affected)` runs after `roomLocation(affected)` with no null check (`Spell_HelpingHand.java:141-143`). Whether `roomLocation` can return null there is unverified.
15. **Ignite coal formula.** `20*(1+I.weight*3)` (`Spell_Ignite.java:96`) multiplies rather than adds. It may be intended; the intent is not stated.
16. **Small cruft.** Greater Scry's redundant branch (above). Ghost Sound has an empty `if(target instanceof MOB){}` in `castingQuality` (`Spell_GhostSound.java:140-150`). Harden Bullets calls `Math.round` on an int (`Spell_HardenBullets.java:109-116`). Hear Thoughts passes an unwrapped literal to `commonTelL` (`Spell_HearThoughts.java:168`). Ignite's default branch returns with no message.
17. **Multiplayer assumptions.** Gate's group travel, `isInTheGame` checks, `CMLib.players().getPlayer` lookup (`Spell_HelpingHand.java:291`), the PvP-style malicious flags, and the `includePlayers` argument to `properTargets` all assume other humans. A single-player port can drop them.
18. **Real-time delays.** Future Death's one-second `scheduleRunnable` and the 4-second tick are wall-clock timers. A turn-based port needs a different clock.

## Takeaways for a Python SUD

- **Collapse the damage family into one data-driven effect.** Frost, Ice Storm, Ice Lance, Forked Lightning and Gravity Slam differ only in dice formula, damage type, target set and modifiers. A table of rows would replace five classes:

  ```python
  @dataclass(frozen=True)
  class Damage:
      dice: Callable[[int, int], int]   # (caster_level, x_level) -> number of dice
      sides: int
      bonus: int
      kind: str                         # "cold", "fire", "electric", "fall"
      area: bool = False
  DAMAGE = {
      "frost": Damage(lambda L, x: (min(L, 50) + 2 * x) // 4, 6, 5, "cold"),
      "ice_lance": Damage(lambda L, x: (L + 2 * x) // 2, 6, 15, "cold"),
      "forked_lightning": Damage(lambda L, x: round((L + 2 * x) / 2), 7, 1, "electric", area=True),
  }
  ```

- **Make duration an explicit type.** Replace the overloaded integer (positive flat, negative percentage, zero standard) with `Duration(kind="flat" | "standard" | "percent", value=...)`. That is the single biggest readability gain in this bucket.
- **Keep one proficiency roll with a difficulty pre-roll.** Use `pct = skill + 2*expertise + expertise//2`, clamp to 5 to 95, add a non-negative adjustment, and fail on a separate roll for negative ones. The pattern is sound and compact.
- **Pay costs before the roll, as CoffeeMud does.** It makes failed casts meaningful and matches the feel of the source.
- **Port the Geas grammar as data.** The 99-pattern table is a ready list of errand verbs. A Python version would be a list of `(pattern, steps)` pairs with the same placeholders, and a step queue that yields DONE, HOLD or MOVE. This suits quests and companion orders in a single-player world.
- **Turn Helping Hand into a guide NPC or item.** Use a shortest-path step to a named character, a follow tick, and a patience counter that steps through a message list. This gives a single player a way to be led without any other humans.
- **Build observation as a first-class concept.** Scry and Clairvoyance show that "watch a place and copy its events" is a clean pattern. Implement `Observation(place, event_kinds, expires)` and deliver filtered events to the caster. This is valuable for a living world where the player can watch distant places.
- **Expose intent for "thought" spells.** Hear Thoughts relies on each behaviour describing itself (`accountForYourself`). A Python NPC can implement `describe_intent()` and power a sense spell or a "read the room" command.
- **Drop the group, PvP and speech-channel machinery.** Keep the fizzle-by-sense idea as flavour text only.

## Coverage

- **Read in full or nearly so (51 bucket files minus those listed below):** Spell_Hold, Spell_Haste, Spell_HelpingHand, Spell_Gate, Spell_Geas, Spell_FleshStone, Spell_Grease, Spell_IceSheet, Spell_GroupStatus, Spell_Immunity, Spell_IllusoryDisease, Spell_Hunger, Spell_GreaterImage, Spell_GreaterScry, Spell_HearThoughts, Spell_FutureDeath, Spell_Forget, Spell_Frenzy, Spell_FoolsGold, Spell_GhostSound, Spell_Friends, Spell_IllusoryWall, Spell_FloatingDisc, Spell_GreaterEnchantWeapon (Armor read as a diff), Spell_GiantStrength, Spell_GraceOfTheCat, Spell_Frailty, Spell_Grow, Spell_Harden, Spell_HardenBullets, Spell_Hungerless, Spell_FreeMovement, Spell_IdentifyObject, Spell_GreaterMend, Spell_FlamingSword, Spell_ForcefulHand, Spell_Ignite, Spell_FlamingEnsnarement.
- **Read in part (main logic or key sections):** Spell_HeatMetal, Spell_GustOfWind, Spell_GravitySlam, Spell_Frost, Spell_IceStorm, Spell_ForkedLightning, Spell_GreaterLevitate, Spell_GreaterClairevoyance, Spell_GreaterClaireaudience.
- **Grepped or skimmed only:** Spell_IceLance (formula and range), Spell_Fly, Spell_GreaterInvisibility, Spell_GreaterClairevoyance and Spell_GreaterClaireaudience (sections around their messages and durations).
- **Skipped:** none. All 51 files were run through a field extractor (ID, name, quality, classification, affect and target masks, flags) for the catalogue.
- **Shared code read in part:** `Spell.java` (full); `StdAbility.java` at 201-204, 260-300, 355-480, 482-500, 523, 640-760, 899-935, 1441-1490, 1600-1620, 1790-1815, 1916-2100, 2113-2125, 2140-2345 and 2629; `MUDFight.java` at 163-200; `SlaveryLibrary.java` at 30-110; `SlaveryParser.java` at 39-75, 97-135, 136-255 (pattern table), 257-300, 404-600, 726-760 and 794-930; `Behaviors/CombatAbilities.java` (grep only); `Common/DefaultCharState.java` at 185-205 (hunger semantics); `Spell_MinorImage.java` (grep only).
- **Not consulted:** `resources/help` entries and `guides/` for these spells. The chapter describes what the code does; no documentation was checked against it.
- **Not verified by running:** the arithmetic in the proficiency worked example and the duration examples is derived from reading the code, not executed.
