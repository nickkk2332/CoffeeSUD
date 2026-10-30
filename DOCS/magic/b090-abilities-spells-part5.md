# Spells, part 5: Imprisonment to Majesty

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b090` (Abilities/Spells [part 5/9: Spell_Imprisonment .. Spell_Majesty]) · 51 files · 9175 lines of Java.

Citations are relative to `com/planet_ink/coffee_mud/`, as in the brief. The shared base `Abilities/Spells/Spell.java` (162 lines) is not in this bucket and was read in full as a dependency. Six classes in the bucket are thin overrides of bases in other buckets (`Spell_ImprovedPlanarmorph` extends `Spell_Planarmorph`, `Spell_ImprovedPlanarDistortion` extends `Spell_PlanarDistortion`, `Spell_LimitedWish` extends `Spell_Wish`, `Spell_MajManaShield` extends `Spell_ManaShield`, `Spell_LesserImage` extends `Spell_MinorImage`, `Spell_ImprovedClanWard` extends `Spell_ClanWard`); those bases were not read.

## Summary

This bucket is the second half of the spell list's alphabetical run from Imprisonment to Majesty, and it covers the widest range of effect types in the Spells folder. The spells include planar transport (Imprisonment), room and area effects (Lighthouse, Insect Plague, Increase Gravity, Invisibility Sphere), item enchantment (Magic Item, Lesser Permanency, Magic Bullets, Keen Edge, Magic Mouth), divination (Know Fate, Locate Object and the Know family), direct damage (Magic Missile, Lightning, Kinetic Pulse, Know Pain, Limb Rack) and status effects on people (Laughter, Know Bliss, Light Sensitivity, Irritation, Lower Resistance). Nearly every spell follows one skeleton: resolve the target, run a proficiency check, offer a `CMMsg` to the room, then either do the work at once or attach a timed effect. The most reusable parts for a Python game are the Lighthouse beam sweep, the planar-destination rule in Imprisonment, the simulated duel in Know Fate, the spell-on-item trigger model in Magic Item, and the duration formulas in the base class.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Spells/Spell.java` | Shared base for spells: classification, malicious-spell side effects, armour and concentration checks (dependency, not in bucket) | 162 |
| `Abilities/Spells/Spell_Majesty.java` | Royal aura: retitles the caster, makes race-mates kneel and greet, forwards ORDER commands | 421 |
| `Abilities/Spells/Spell_Lighthouse.java` | Light that sweeps the exits around a ship or outdoor caster, one direction per tick | 376 |
| `Abilities/Spells/Spell_Imprisonment.java` | Moves a player to the planar plane most opposed to its alignment and inclination; freezes monsters as statues | 351 |
| `Abilities/Spells/Spell_ImprovedPolymorph.java` | Race change priced by the stat, movement and breathing differences between old and new race | 349 |
| `Abilities/Spells/Spell_InvisibilitySphere.java` | Sphere of invisibility centred on the caster; attaches itself to every object in the room | 285 |
| `Abilities/Spells/Spell_IncreaseGravity.java` | Room effect that gives occupants a timed copy: double weight, and weak characters cannot move | 243 |
| `Abilities/Spells/Spell_LightSensitivity.java` | Light-sensitivity curse: lit-room penalties and a chance to block examine and targeted actions | 243 |
| `Abilities/Spells/Spell_ImprovedRepairingAura.java` | Repairs worn items on a timer; the aura hops to another item when the current one is full | 240 |
| `Abilities/Spells/Spell_MagicItem.java` | Enchants a generic item with one of the caster's known spells, paid for in experience | 231 |
| `Abilities/Spells/Spell_LocateObject.java` | Divination for named items and carried items within a radius of the caster | 230 |
| `Abilities/Spells/Spell_KnowFate.java` | Predicts a duel by running the two combatants in a simulated arena 20 times | 225 |
| `Abilities/Spells/Spell_MagicMouth.java` | One-shot spoken message triggered on an item by a chosen action or by someone entering | 211 |
| `Abilities/Spells/Spell_Levitate.java` | Lifts a target or item; a levitated mob cannot advance, retreat, leave or enter | 206 |
| `Abilities/Spells/Spell_InsectPlague.java` | Room effect: stings occupants and destroys crops, vegetation and foraging | 203 |
| `Abilities/Spells/Spell_LimbRack.java` | Damage over time that tears off the remaining arms and legs when the victim dies | 196 |
| `Abilities/Spells/Spell_Knock.java` | Opens an exit or container, overriding a Wizard Lock of lower level | 197 |
| `Abilities/Spells/Spell_KnowBliss.java` | Pacifies a target until it takes damage; monsters wander away | 190 |
| `Abilities/Spells/Spell_Laughter.java` | Paralyses a target with laughter for 3 to 8 ticks | 176 |
| `Abilities/Spells/Spell_Light.java` | Light above the caster's head; also the template the lighthouse copies into rooms | 143 |
| `Abilities/Spells/Spell_MagicMissile.java` | Volley of missiles; the count grows with the adjusted level | 127 |
| `Abilities/Spells/Spell_LesserPermanency.java` | Makes one of the caster's own spells on a target permanent | 160 |

The other 29 classes are listed in the catalogue under "Content worth mining".

## How it works

### Shared rules in the base class

- `Spell.java:86-89` sets the classification to `ACODE_SPELL`. `canTargetCode` defaults to `CAN_MOBS` (`Spell.java:65-69`), and `abstractQuality` defaults to `QUALITY_INDIFFERENT` (`Spell.java:71-75`).
- `Spell.invoke` (`Spell.java:153-161`) runs `StdAbility.invoke` and then `spellArmorCheck` (`Spell.java:132-151`). For a non-auto cast by a player, a roll of `rollPercentage()<50` (`Spell.java:139`) with cloth-incompatible armour fails the cast with "<S-NAME> watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (`Spell.java:141`). `CMLib.flags().canConcentrate` failing gives "can't seem to concentrate." (`Spell.java:144-147`).
- `Spell.maliciousAffect` (`Spell.java:93-130`) runs a side effect when a malicious spell lands on a player Mage. On a 1-in-100 roll (`Spell.java:106`, `rollPercentage()==1`), if the target already carries more than five malicious spells (`Spell.java:119`, `num>5`), it applies `Disease_Magepox` (`Spell.java:121-123`).

### The cast skeleton

Spell_Majesty.invoke (`Spell_Majesty.java:392-420`) is typical. It resolves the target with `getTarget` (line 394), calls `super.invoke` (line 398), rolls `proficiencyCheck(mob,0,auto)` (line 402), builds a `CMMsg` (lines 405-406), asks `mob.location().okMessage` (line 407), sends it (line 409) and applies the effect with `beneficialAffect` (line 412). Failure paths use `beneficialWordsFizzle`, `beneficialVisualFizzle` or `maliciousFizzle`.

Malicious spells usually send a second message carrying `CMMsg.MSK_CAST_MALICIOUS_VERBAL|CMMsg.TYP_...` and require both messages to have `value()<=0` before they apply. Examples are `Spell_ImprovedPolymorph.java:325` and `Spell_Laughter.java:153-158`. The value is set by listeners (resistance checks). Those listeners are outside this bucket and were not traced.

### Durations

- Beneficial default: `StdAbility.java:2312-2330`. Ticks are `adjustedLevel*4 + 40`, capped at the target's level plus `EXPRATE` when a caster who is a possible abuser targets another player. A positive explicit value is used as given. A negative value is a percentage of the default.
- Malicious default: `StdAbility.java:2174-2183`. Ticks are `round(adjustedLevel*1.1) + 25`, passed through the `adjustMaliciousTickdownTime` hook.
- Tick length: `core/CMProps.java:681`, `TIME_TICK = 4000` ms. A level-10 beneficial effect is therefore 80 ticks, about 320 seconds.
- Explicit durations in this bucket: Lighthouse light copies live 8000 ms (`Spell_Lighthouse.java:115`), two default ticks. Levitate lasts `5 + XLEVEL` ticks (`Spell_Levitate.java:193`). Limb Rack lasts 12 ticks (`Spell_LimbRack.java:180`). Laughter lasts `8 - clamp(levelDiff,0,5)` ticks, minimum 1 (`Spell_Laughter.java:127-131`, `160-162`). Improved Invisibility lasts `(level + 2*XLEVEL)*3` ticks (`Spell_ImprovedInvisibility.java:122`). Majesty lasts the ticks in a mud hour times the hours in a day (`Spell_Majesty.java:411`).

### Majesty: retinue, titles and orders (`Spell_Majesty.java`)

- Data: `fname` and `ftitle` hold the display name and title. `mask` is a compiled zapper mask `-RACE +<raceID>` (`Spell_Majesty.java:142-143`), so only the caster's race-mates count as subjects. `noboth` (`Spell_Majesty.java:84`) records who has already been greeted.
- Title: `setAffectedOne` (`Spell_Majesty.java:121-147`) picks "King" for reproductive code 'M', "Queen" for 'F' and "Royal" otherwise (lines 133-139). The full title is "<Name>, <title> of <plural race>" (line 144). `addTitle` and `removeTitle` (lines 93-119) write it into `PlayerStats`.
- Events: `executeMsg` removes the title on quit (`Spell_Majesty.java:190-191`) and re-adds it on life (`193-194`). When a mask-matching mob enters the room, `doGreet` (`Spell_Majesty.java:149-178`) schedules a 1000 ms delay (line 177). The subject kneels, then one of four greetings is chosen by `roll(1,5,0)` (line 160): "Hail @x1!", "All Hail @x1!", "Hail @x1!" with the full title, or "Your Majesty.". The subject is then added to `noboth` (line 175).
- Speech: `okMessage` rewrites first-person words in speech, tells and channels to "we", "our", "ours" and "us" (`Spell_Majesty.java:231-254`).
- Orders: a failed "ORDER <name>" command (`TYP_COMMANDFAIL`, `Spell_Majesty.java:256-264`) is intercepted. The named subjects are found, and "ALL" expands over numbered inhabitants `.2`, `.3` and so on (lines 284-297). If any subject fails the mask, the message passes through unchanged (lines 298-302). Otherwise each queued command is run as the subject, with a `norc` guard (lines 309-316) and a `;ORDER` security flag (line 324). The original message is then refused (line 336).
- Protection: a malicious action on the caster by someone who is not in combat with them and is not their victim is refused with "You are too much in awe of @x1" (`Spell_Majesty.java:348-364`). The victim is made peaceful if it was attacking the caster (lines 357-360). `TYP_LEGALWARRANT` is refused with "@x1 is above the law." (lines 342-346).

### Lighthouse: sweeping light (`Spell_Lighthouse.java`)

- Data: `roomSet` holds the rooms currently lit. `lastRoom`, `lastShipRoom` and `lastDir` hold the sweep state (lines 100-103). The spell refuses a second lighthouse on the same caster (`Spell_Lighthouse.java:349-353`).
- Each tick (`tick`, lines 241-341) finds the caster's room. On a ship (outdoors and in a `Boardable` area, lines 252-253) it uses the ship's room and removes the spell if the ship is gone (lines 255-261). Otherwise it uses the caster's room (lines 305-310).
- The lit set is the current room, plus rooms within `range = 10 + XLEVEL + 2*XMAXRANGE` (line 269). Those come from radiant tracking restricted to the same area, open exits and the outdoors (lines 264-270). On a ship the set also includes the ship room (line 273). It adds the neighbour in the sweep direction when that exit is open (lines 274-278).
- The sweep: `nextDir` (lines 219-239) moves one direction index per tick, starting at NORTH (lines 221-222). It skips directions with no room, no exit or a closed exit. The room in that direction is also lit.
- Light: each lit room gets a `Spell_Light` copy (`addToRoom`, lines 105-142) with an 8000 ms expiry, marked not savable (lines 114-115). `delFromRoom` (lines 144-191) removes a copy at once only when it is the last light and the room goes dark. Otherwise it is left to expire.
- Messages: "A bright light shines in from @x1." (lines 125, 134, 138), "A bright light from @x1 disappears." (lines 165, 174, 178), and "The lighthouse above <S-NAME> dims." on unInvoke (line 201).
- Duplicate-guard quirk: `addToRoom` tests `R.fetchEffect(ID())` (line 110), where `ID()` is `Spell_Lighthouse`, but it adds `Spell_Light` (line 109). The guard never matches. In the land branch `currentRoom` is added to `newRooms` twice (lines 307 and 310), so on the first tick a room may get two copies. This is read from the code and not run.

### Imprisonment: the planar prison (`Spell_Imprisonment.java`)

- Data: `prevRoom` remembers where the target came from (line 89). The spell is malicious, has `FLAG_TRANSPORTING` (lines 65-69) and costs 200 mana (lines 78-81).
- Monsters are statued. `okMessage` (lines 102-155) resets the monster to its maximum state, sets hunger and thirst to 1000 (lines 118-121), drops any combat, and refuses non-always major actions with "Statues can't do that." (lines 128-136). `affectPhyStats` (lines 157-172) adds `CAN_NOT_MOVE`, `CAN_NOT_HEAR`, `CAN_NOT_SMELL`, `CAN_NOT_SPEAK`, `CAN_NOT_TASTE` and `IS_NOT_SEEN`. The name change to a statue is commented out (line 164).
- Players go to a plane. `getPlanarDiff` (lines 195-231) returns `|alignment - planeAlignment| + |inclination - planeInclination|`, using the plane's faction list. The chosen plane is the one with the largest difference among planes in the "Outer" category (lines 272-284). If none qualifies, any plane is used (lines 287-297). The player is moved by invoking `StdPlanarAbility` (line 313). Any shorter timers on the plane's own area effects are raised to the imprisonment's duration (lines 320-334), so the plane does not close before the imprisonment ends.
- Freeing: `unInvoke` (lines 174-193) prints "Your imprisonment expires.", and if the mob is still on a plane it is returned to `prevRoom` (line 186). Players then get a `postLook` (line 188).
- Gate: the move happens only when the cast message value is at most zero (line 260). The failure text is "<S-NAME> swipe(s) <S-HIS-HER> hands at <T-NAMESELF>, but the spell fails." (lines 346-347), also used when the target is already on a plane.

### Improved Polymorph: a priced race change (`Spell_ImprovedPolymorph.java`)

- Data: the effect stores `RACE=<id> NOXP=<bool>` as its misc text (`setMiscText`, lines 87-97; written at line 332). `newRace` and `noxp` are read back from it.
- Effects: `affectPhyStats` rewrites the name to "a <race> called <name>" or "<name> the <race>" (lines 117-120) and keeps extra weight (lines 121-124). `affectCharStats` swaps the race and its restrictions (lines 134-141) and maps age through the new race's aging chart (lines 142-144). `okMessage` zeroes experience gain when `noxp` is set (lines 102-107).
- Cost: `levelDiff = (caster level + 2*XLEVEL) - target level` (line 298). `statDiff` is the difference of base-stat totals (lines 256-274), plus penalties: 75 if movement changes (lines 276-278), 40 if breathing changes (lines 281-283), 25 for sight (lines 286-287), 10 for hearing (lines 288-289), 25 for speech (lines 290-291) and 5 for smell (lines 292-293). The proficiency check is `proficiencyCheck(mob, levelDiff - statDiff, auto)` (line 299).
- Gates: a target already polymorphed is refused (lines 241-245). Only availability code `THEME_FANTASY` races are allowed (lines 247-251). A manual cast fails when `levelDiff - statDiff <= -100` (line 312). A target that will not consent gets "will not hold still for you", "already answers to another" or "will not be changed" (lines 300-310).
- Random races: "any" or an unknown race picks a random race (lines 206-239). Monsters pick a theme race other than their own.
- Message on success: "<S-NAME> become(s) a @x1!" (line 328). Unwinding: "<S-NAME> morph(s) back to <S-HIS-HER> normal form." (line 159).

### Invisibility Sphere: an area effect attached to a room (`Spell_InvisibilitySphere.java`)

- The caster is the centre. The centre is `invoker()`, or `affecting()` when there is no invoker (lines 115, 143, 187, 224).
- One `Ability` object is attached to every mob and loose item in the room with `addEffect(this)` (line 163), and its owner pointer is reset to the centre with `setAffectedOne(invoker)` (line 164). `affectPhyStats` (lines 107-135) sets `IS_INVISIBLE` (line 126) on objects in the same room, and calls `removeFromSphere` for anything outside (lines 128, 131, 134).
- Movement: when the centre enters, all inhabitants and loose items are added (lines 192-203). When the centre leaves, items in the destination room are removed (lines 205-214). `tick` (lines 219-253) re-scans the room but returns early when the XOR of the object hash codes is unchanged (lines 231-240).
- Cast: refused if the caster is already inside, with "You are already inside an invisibility sphere!" (line 260). The fizzle text is "cast(s) a spell, but nothing happens." (line 280). Duration is the default beneficial value (`beneficialAffect(mob,mob,asLevel,0)`, line 276). `castingQuality` is `INDIFFERENT` when the caster is in combat or is a monster (lines 170-181).
- Messages: "<S-NAME> <S-IS-ARE> now inside the invisibility sphere." (line 162), "...outside the invisibility sphere." (lines 96, 147).

### Increase Gravity: a room effect with per-occupant copies (`Spell_IncreaseGravity.java`)

- The room is the affected object (`gravityRoom`, lines 78-87). Each tick (lines 95-118), every occupant without the effect gets a copy with `startTickDown` (lines 107-111). An occupant in combat loses one movement per tick through `adjMovement(-1, maxState())` (lines 113-114).
- Occupants: `affectPhyStats` doubles weight (line 204) and cancels flying (lines 202-203). An occupant who has left the room loses the effect (lines 198-199 and 151-155).
- Strength gate (lines 158-188): a character with strength under 18 cannot advance, retreat, leave or flee. The messages are "You feel too heavy to advance.", "...retreat.", and "...leave." (lines 164, 173, 183).
- Duration: `adjustedLevel(mob,asLevel)` (line 234). Messages: "Gravity begins to increase!" (line 228) and "Gravity returns to normal..." (line 131).

### Light Sensitivity and Light Blindness (`Spell_LightSensitivity.java`, `Spell_LightBlindness.java`)

- Sensitivity depends on the room's light. In a lit room the target takes -50 attack adjustment and +50 armour (`Spell_LightSensitivity.java:96-97`). In a dark room it gets `CAN_SEE_DARK` (line 93). Blindness is harsher: dark rooms give the same `CAN_SEE_DARK` (`Spell_LightBlindness.java:88`), but lit rooms give `CAN_NOT_SEE` (line 90).
- Blocks: while sensitive in a lit room, EXAMINE on a non-room target is refused with "You can't seem to make it out that well in this bright light." (`Spell_LightSensitivity.java:118-124`). Targeted casts, delicate-hands actions and some justice actions are refused on a 50% roll (lines 127-149, roll at 142). A source carrying `Spell_DarkSensitivity` is exempt (`cancelID`, line 63).
- Cast: a manual cast on a target with no eyes is refused (lines 195-199). An existing cancelling effect is removed first (lines 220-226). `castingQuality` is `INDIFFERENT` in combat if the room is dark, if the target has no eyes, or if the target cannot be seen (lines 167-186).
- Messages: "<S-NAME> become(s) extremely sensitive to light." in dark rooms and "...sensitive to the light." in lit rooms (lines 217, 219). Blindness uses "<S-NAME> become(s) blinded by the light." (`Spell_LightBlindness.java:158`).

### Repairing Aura (`Spell_ImprovedRepairingAura.java`)

- Costs 50 mana (lines 76-80). `REPAIR_MAX = 30` (line 82). Each tick `repairDown -= adjustedLevel` (line 98), and when it reaches zero it resets to 30 (line 101). A repair therefore happens every `ceil(30 / adjustedLevel)` ticks.
- Repair: if the item is subject to wear and tear and has fewer than 100 uses (line 103), its uses rise by `1 + XLEVEL/3` (line 110). The message is "<S-NAME> is magically repairing itself." in a room (line 106) or "@x1 is magically repairing itself." on a mob (line 109).
- Hop: otherwise the aura moves to another item the owner carries (lines 114-133). `getChoices` (lines 146-173) excludes Glowing Mage Armor (line 156), items already under a repair aura, and the current item. Worn items are preferred when there are three or more of them. Otherwise carried items are added to the pool. The effect is moved with `delEffect` and `addEffect` (lines 131-132).
- Casting on a mob picks a random candidate item (line 210) and fails silently if there are none (line 208). `adjustedLevel` is stored on the effect after casting (lines 225-228).

### Magic Item: enchanting with experience (`Spell_MagicItem.java`)

- Overrides mana cost with `COST_ALL` (lines 72-76), so it uses all mana. It is flagged `FLAG_NOORDERING` (lines 66-70).
- Input: the last word names an unworn item in the room (line 92). The rest is matched against the caster's known spells by `fetchEnvironmental`, exact then partial (lines 107-121).
- Exclusions: Stoneskin, Mirror Image, summoning spells and room-targeted spells (lines 128-135). The spell must be `mayBeEnchanted()` (lines 137-141). The target must be a generic item with no effects (lines 143-147).
- Cost: `experience = 1000 + 100 * lowestQualifyingLevel(spell)` (lines 149-150). The caster must have that much experience (lines 151-155). It is lost on failure too (lines 222-225).
- Result: the item gains `IS_BONUS` (line 179) and level `+ lowestQualifyingLevel/2` (line 180). It also gets an attached trigger effect chosen by item type: armour gets `Prop_WearSpellCast` with `LAYERED;<spell>;` (lines 182-187), weapons get `Prop_FightSpellCast` with `25%;MAXTICKS=12;<spell>;` (lines 189-194), food and drink get `Prop_UseSpellCast2` (lines 196-202), and anything else that is held or wielded gets `Prop_WearSpellCast` (lines 204-215).
- Messages: "You lose @x1 experience points for the effort." (line 167), "You are unable to complete this enchantment." (line 170), "<T-NAME> glow(s) brightly!" (line 178).

### Lesser Permanency (`Spell_LesserPermanency.java`)

- Costs 100 mana in `overrideMana` (lines 72-74), and a further 20 from base and max mana (lines 146-147).
- The spell searches the target's effects from last to first (lines 113-126). It takes the first one the caster invoked, which is not auto-invoked, can be uninvoked, is a spell, and has a lowest qualifying level of 10 or less.
- The chosen effect becomes non-uninvokable and savable (lines 144-145). For rooms and exits, a malicious spell is refused unless the caster owns the land (lines 134-136).

### Magic Bullets, Keen Edge, Lighten Item, Magic Mouth

- Magic Bullets (`Spell_MagicBullets.java`) enchants sling bullets only. On each hit the bullet deals the hit's damage plus `text()` again (`Spell_MagicBullets.java:109`), via a `postDamage` call (lines 118-119). Recursion is blocked by a `norecurse` flag (lines 113-124).
- Lighten Item (`Spell_LightenItem.java:138`) uses `beneficialAffect(...,100)`, a fixed 100-tick weight reduction. Its message on expiry is "@x1 grows heavy again." (line 95).
- Keen Edge (`Spell_KeenEdge.java:133-135`) stores the caster's XLEVEL as misc text on the edge effect. Its message on expiry is "<S-NAME> loses its keen edge." (line 95).
- Magic Mouth (`Spell_MagicMouth.java`) is one-shot. The trigger is one of TOUCH, HOLD, WIELD, WEAR or ENTER (lines 166-181). The speech is "<item> says '<message>'." (line 84), followed by `unInvoke()` (line 85). TOUCH also fires on OPEN, GIVE, LOCK, UNLOCK, PUSH, PULL, GENERAL, JUSTICE and DELICATE_HANDS actions (lines 124-137). ENTER waits for a LOOK in the room (lines 103-111). Sneaking characters do not trigger it (lines 101, 117).

### Divination family

- Know Fate (`Spell_KnowFate.java`) takes the caster's saved player aliases as a combat script (lines 95-114). It copies the caster and the target into a fresh room and runs 20 bouts (lines 124-136), ticking both combatants until one dies or ten consecutive ticks pass with no change in hit points (lines 138-173). Its report is "@x1% of the time, you defeat @x2 with @x3 hit points left@x4." (lines 199-200). The "you die" branch (lines 205-206) and the "half of the time" branch (lines 211-212) are the other outcomes. Each draw counts for 5% (line 215).
- Locate Object (`Spell_LocateObject.java`) parses level bounds from words like `<40` and `>10` (lines 91-122). It returns up to `1 + XLEVEL` results (line 128), searches passable rooms within `50 + 2*XLEVEL + 10*XMAXRANGE` (lines 165-166), and reports "@x1 is in a place called '@x2'." for room items (line 177). It reports carried items as "...is being carried by @x3..." and shows the level in brackets if the query had level bounds (line 202). Results beyond the cap are trimmed at random (lines 216-217).
- Know Alignment (`Spell_KnowAlignment.java`) reports "chaotic <alignment>", "lawful <alignment>" or the plain alignment (lines 90-96) on success. On failure it reports a random alignment from the enum, skipping index 0 (line 102). A failed cast therefore gives a false answer, not an error.
- Know Pain (`Spell_KnowPain.java:102-109`) deals `roll(maxDie, 6, INT)` damage, where `maxDie = (target level + adjusted level)/2`. It is a `TYP_GAS` damage message: "The horrible memory <DAMAGE> <T-NAME>!"
- Know Value, Know Origin, Know Province and Infravision are divinations with little mechanics. Know Origin's detail depends on `XLEVEL` (switch at `Spell_KnowOrigin.java:136`). Know Province prints entry messages as the target moves (`run` at `Spell_KnowProvince.java:121`).

### Damage spells

- Magic Missile (`Spell_MagicMissile.java`): missile count `min(floor(adjustedLevel/5) + 1, 5)` (line 92). Each missile does `roll(1, 5+XLEVEL, (5+XLEVEL)/count)` (line 105). If the target dies, the next target is chosen (lines 111-118).
- Lightning (`Spell_Lightning.java:106-107`): `maxDie = round((adjustedLevel + 2*X1Level)/2)`, rolled as `roll(maxDie, 8, maxDie/4)`. The message is "The bolt <DAMAGE> <T-NAME>!".
- Kinetic Pulse (`Spell_KineticPulse.java:111-118`): `maxDie = (adjustedLevel + 2*X1Level)/2`, and damage is `roll(maxDie, 6, 15)`, halved if either message has a value above zero (lines 114-115). With a 1-in-10 roll (lines 119-120) the target is shoved back, to range `2 + XLEVEL + current range` (line 136).
- Limb Rack (`Spell_LimbRack.java`): the spell records the arms and legs still attached (lines 142-155). Each tick the victim takes `maxHP / (10 - XLEVEL/2)` burst damage (line 93). On death, `unInvoke` tears off the recorded limbs through an `Amputation` effect (lines 100-133).
- Insatiable Thirst (`Spell_InsatiableThirst.java:92`): an instant effect that cuts thirst by `150 + (level + 2*XLEVEL)*100`.
- Kinetic Bubble (`Spell_KineticBubble.java:128`): when struck, the bubble reflects `roll(1, (XLEVEL + msg.value)/3, 0)` damage.

### Status effects on people

- Laughter (`Spell_Laughter.java`): the level difference is clamped to 0 through 5 (lines 127-131). The save is `proficiencyCheck(mob, -(levelDiff*5), auto)` (line 146). The target must be able to hear the caster (lines 135-137). The effect blocks movement (line 88) and prints "<S-NAME> laugh(s) uncontrollably, unable to move!" every tick (line 99).
- Know Bliss (`Spell_KnowBliss.java`): the target cannot take malicious actions and gets "Nah, you feel too happy to do that." (lines 139-145). Taking damage ends the effect (lines 112-115). Each tick the target wanders away (lines 121-127). Monsters that leave also wander on expiry (lines 97-101). The caster makes nearby monsters that were attacking the target peaceful (lines 172-180).
- Irritation (`Spell_Irritation.java:89-90`): armour `+10+2*XLEVEL` and attack `-10-2*XLEVEL`. Each tick there is a 50% roll (line 101) for "<T-NAME> <T-IS-ARE> wincing and scratching!".
- Lower Resistance (`Spell_LowerResists.java`): first strips the target's own abjuration effects (loop from line 119, abjuration test at line 124), then sets `amount = ((level + 2*XLEVEL) - target level)*5`, minimum 5 (lines 132-134). The saving throws are reduced by `amount` (`affectCharStats`, line 99).
- Improved Harden (`Spell_ImprovedHarden.java`): on a siege item only, armour `+20 + adjustedLevel/2 + XLEVEL` (line 86). Each tick, a lost use is restored with a `40 + 5*XLEVEL` percent roll (line 109).

## Content worth mining

The catalogue below lists all 51 classes, from the key fields and the code read. Quality and domain are from `abstractQuality()` and `classificationCode()`. The six inherited wrappers take their values from their bases.

| Class (under `Abilities/Spells/`) | Quality | Domain | Targets | What it does |
|---|---|---|---|---|
| Spell_Majesty | OK_OTHERS | Illusion | Mobs | Royal title and retinue, greetings, ORDER forwarding; cooldown equals one mud day |
| Spell_Lighthouse | OK_SELF | Evocation | Caster | Sweeping light from a ship or outdoor position |
| Spell_Imprisonment | MALICIOUS | Cosmology | Mobs | Planar prison; freezes monsters as statues; 200 mana |
| Spell_ImprovedPolymorph | OK_OTHERS | Transmutation | Mobs | Race change priced by stat and sense differences |
| Spell_InvisibilitySphere | INDIFFERENT | Illusion | Mobs, items | Invisibility sphere centred on caster |
| Spell_IncreaseGravity | MALICIOUS | Alteration | Rooms, mobs | Room doubles occupants' weight; strength below 18 blocks movement |
| Spell_LightSensitivity | MALICIOUS | Transmutation | Mobs | Lit-room penalties and blocked actions |
| Spell_ImprovedRepairingAura | INDIFFERENT | Abjuration | Items, mobs | Timed repair; hops between items; 50 mana |
| Spell_MagicItem | INDIFFERENT | Enchantment | Items | Attaches a known spell to an item; 1000 + 100 x level experience |
| Spell_LocateObject | INDIFFERENT | Divination | None | Finds named items within a radius |
| Spell_KnowFate | INDIFFERENT | Divination | Mobs | Simulates 20 duels with the caster's aliases |
| Spell_MagicMouth | INDIFFERENT | Alteration | Items | One-shot spoken message on TOUCH, WEAR, WIELD, HOLD or ENTER |
| Spell_KnowOrigin | INDIFFERENT | Divination | Mobs, items | Reports origin; detail depends on XLEVEL |
| Spell_Levitate | MALICIOUS | Evocation | Mobs, items | Lifts target; 5 + XLEVEL ticks; range 5 |
| Spell_InsectPlague | MALICIOUS | Conjuration | Rooms | Stings occupants; destroys crops and vegetation |
| Spell_MageClaws | BENEFICIAL_SELF | Transmutation | Mobs | Claws in place of weapons; needs free hands |
| Spell_Knock | INDIFFERENT | Evocation | Exits, items | Opens exits and containers; beats Wizard Lock of lower level |
| Spell_LimbRack | MALICIOUS | Evocation | Mobs | Burst damage each tick; limbs torn off on death; 12 ticks |
| Spell_IronGrip | BENEFICIAL_OTHERS | Transmutation | Mobs | Weapon hand turns to iron; grip resists disarm |
| Spell_KnowBliss | MALICIOUS | Enchantment | Mobs | Pacifies until damaged; monsters wander off |
| Spell_MageArmor | BENEFICIAL_SELF | Abjuration | Mobs | Armour plate on the torso; armour + XLEVEL (`Spell_MageArmor.java:87`) |
| Spell_Laughter | MALICIOUS | Enchantment | Mobs | Paralysis for 3 to 8 ticks |
| Spell_LightBlindness | MALICIOUS | Transmutation | Mobs | Sees in the dark; blind in lit rooms |
| Spell_MagicBullets | INDIFFERENT | Enchantment | Items | Sling bullets that re-strike on hit |
| Spell_KnowProvince | OK_OTHERS | Divination | Mobs | Announces province entries as the target moves |
| Spell_KineticBubble | BENEFICIAL_SELF | Abjuration | Mobs | Reflects struck damage |
| Spell_LedFoot | MALICIOUS | Transmutation | Mobs | Heavy feet and cannot move; the message at `Spell_LedFoot.java:134` says footless targets are not affected |
| Spell_ImprovedHarden | BENEFICIAL_OTHERS | Alteration | Items | Armour on siege items; restores uses |
| Spell_Invisibility | BENEFICIAL_OTHERS | Illusion | Mobs | Invisibility on a mob, default duration |
| Spell_LesserPermanency | INDIFFERENT | Enchantment | Items | Makes caster's own spell permanent; 100 mana + 20 |
| Spell_KineticPulse | MALICIOUS | Evocation | Mobs | 1d6 pool + 15 damage; 10% shove |
| Spell_MagicalAura | OK_SELF | Illusion | Mobs, items, rooms, exits | Aura on the target (`Spell_MagicalAura.java:140`) |
| Spell_LightenItem | INDIFFERENT | Enchantment | Items | Weight reduction for 100 ticks |
| Spell_KeenEdge | INDIFFERENT | Alteration | Items | Keen edge on a weapon; the caster's XLEVEL stored as text |
| Spell_Infravision | OK_SELF | Divination | Mobs | Infravision; refused if already active |
| Spell_Light | OK_SELF | Evocation | Mobs | Light above the head; template for lighthouse copies |
| Spell_LowerResists | MALICIOUS | Enchantment | Mobs | Strips abjuration; saving throws down by 5 per level difference, minimum 5 |
| Spell_Irritation | MALICIOUS | Enchantment | Mobs | Armour and attack penalties; 50% per tick wince |
| Spell_ImprovedInvisibility | BENEFICIAL_OTHERS | Illusion | Mobs | Duration `(level + 2*XLEVEL)*3` ticks |
| Spell_MagicMissile | MALICIOUS | Conjuration | Mobs | Missile volley, up to 5 |
| Spell_ImprovedPlanarmorph | OK_SELF | Transmutation | Mobs | Extends Spell_Planarmorph (other bucket) |
| Spell_Lightning | MALICIOUS | Evocation | Mobs | Bolt, `roll(maxDie, 8, maxDie/4)` |
| Spell_KnowPain | MALICIOUS | Divination | Mobs | Burst of damage tied to INT; gas damage type |
| Spell_KnowValue | INDIFFERENT | Divination | Items | Reports an item's worth |
| Spell_KnowAlignment | INDIFFERENT | Divination | Mobs | Reports alignment; failure gives a random answer |
| Spell_InsatiableThirst | MALICIOUS | Enchantment | Mobs | Instant thirst drain |
| Spell_ImprovedPlanarDistortion | (inherited) | (inherited) | (inherited) | Extends Spell_PlanarDistortion (other bucket); chooses a plane name |
| Spell_LimitedWish | (inherited) | (inherited) | (inherited) | Extends Spell_Wish; doubles XP cost; refuses changing another player's target (`Spell_LimitedWish.java:57-62`, `64-79`) |
| Spell_MajManaShield | (inherited) | (inherited) | (inherited) | Extends Spell_ManaShield; overrides protection and adjective only |
| Spell_LesserImage | (inherited) | (inherited) | (inherited) | Extends Spell_MinorImage; duration `10 + adjustedLevel/5` (`Spell_LesserImage.java:55`) |
| Spell_ImprovedClanWard | (inherited) | (inherited) | (inherited) | Extends Spell_ClanWard; overrides `setMiscText` (`Spell_ImprovedClanWard.java:54-56`) |

Sample messages worth reusing as text for a Python game:

- Damage: "The bolt <DAMAGE> <T-NAME>!" (`Spell_Lightning.java:112`), "The horrible memory <DAMAGE> <T-NAME>!" (`Spell_KnowPain.java:109`), "The kinetic force wave <DAMAGES> <T-NAME>!" (`Spell_KineticPulse.java:118`).
- Room effects: "A plague of insects buzzes and swarms through the air." (`Spell_InsectPlague.java:121`), "A plague of insects consumed all the @x1 here." (`Spell_InsectPlague.java:135`), "A bright light shines in from @x1." (`Spell_Lighthouse.java:125`).
- Social: "Nah, you feel too happy to do that." (`Spell_KnowBliss.java:142`), "You can't seem to go anywhere!" (`Spell_Levitate.java:109`), "@x1 can't hear your words." (`Spell_Laughter.java:137`).
- Royal court: "Hail @x1!" (`Spell_Majesty.java:163`), "Your Majesty." (line 172), "You are too much in awe of @x1" (line 356).

Content note: the violent content here is limited to Limb Rack (tearing off limbs) and Know Pain (damage), and both are described above as plain mechanics. No explicit adult or slur text was seen in the code read.

## Notable design ideas

- Spell on item as a trigger. Magic Item gives an item a `Prop_*` effect that casts a spell (`Spell_MagicItem.java:182-215`). The trigger depends on the item type: armour on wear, weapons with a 25% chance per hit for up to 12 ticks, food and drink on use. The enchantment is a data-driven trigger, not a new item class.
- Roaming effect. The Repairing Aura moves from item to item (`Spell_ImprovedRepairingAura.java:129-133`), so the same spell keeps working on whatever needs repair.
- Simulated duel. Know Fate runs the caster's own alias script inside a copied arena (`Spell_KnowFate.java:95-136`). A prediction comes from the game's own combat code and not from a separate model.
- Sweeping light. Lighthouse lights one exit per tick (`Spell_Lighthouse.java:219-239`), and each light is a short-lived copy of a light spell. The light fades on its own unless it is the last light in the room (lines 144-191).
- Planar destination by alignment. Imprisonment picks the plane most opposed to the target's alignment and inclination (`Spell_Imprisonment.java:195-297`), so the punishment is a function of the target's character.
- Pacifism that breaks on damage. Know Bliss ends when the target is hurt (`Spell_KnowBliss.java:112-115`) and sends monsters wandering (lines 121-127).
- Statue. Imprisonment fixes a monster's state every message (`Spell_Imprisonment.java:118-136`) and takes away its senses (lines 165-170) rather than removing the monster.
- Magic Mouth waits for a look. An ENTER trigger does not speak until someone LOOKs in the room (`Spell_MagicMouth.java:103-111`), so the message is tied to a player noticing the room, not to the arrival itself.

## Problems and cruft

- Locate Object builds an area sample it never uses. The loop at `Spell_LocateObject.java:139-158` fills `areas` with up to 90% of the world's areas (line 142) and makes up to `numAreas^2` tries (line 145). Nothing reads `areas` afterwards. The search is a radius from the caster's room (lines 165-166). The loop costs time and does nothing. Read in full, not run.
- Lighthouse duplicate guard. The guard in `addToRoom` tests the wrong ID (`Spell_Lighthouse.java:110`), and the land branch adds the current room twice (lines 307, 310). The likely result is two `Spell_Light` copies on the first tick. Not verified at runtime.
- Invisibility Sphere shares one `Ability` between many objects. `addEffect(this)` (`Spell_InvisibilitySphere.java:163`) and `setAffectedOne(invoker)` (line 164) mean the effect's owner field always points at the centre, and each object's state comes from the `affectPhyStats` parameter. This works, but it is fragile. `affectPhyStats` dereferences `centerR` without a null check (lines 121-122); the tick code guards against that, but the stat path does not.
- Limb Rack has two possible faults. The damage divisor `10 - XLEVEL/2` (`Spell_LimbRack.java:93`) is zero when XLEVEL is 20 or 21, which would throw an arithmetic error. Whether XLEVEL can reach that is unverified. The race checks also disagree: `>=0` at lines 88-89 and `>0` at lines 108-109.
- Increase Gravity changes state inside a stat recompute. `affectPhyStats` calls `unInvoke()` when the mob has left the room (`Spell_IncreaseGravity.java:198-199`). That is a side effect in a method that should only read.
- Know Alignment returns a random alignment on failure (`Spell_KnowAlignment.java:102`). That is a silent false answer. It may be intended as misdirection, but nothing in the code marks it as such.
- Know Fate's loop is bounded only by death or ten motionless ticks (`Spell_KnowFate.java:138`, `168-169`). A fight whose hit points keep changing could run long, and each bout runs real ticks on a server thread. The cost is not measured.
- Majesty has redundant title code. `addTitle` removes the title when it is not already present, then adds it (`Spell_Majesty.java:102-104`).
- Majesty's ORDER handling works by catching a failed command message and re-running the command as the subject (`Spell_Majesty.java:256-336`). That is a MUD-specific hack. It depends on the message layer and the security flag.

## Takeaways for a Python SUD

- Spell data as records. A `Spell` record with `id`, `name`, `domain`, `quality` (benefit to self, benefit to other, malice, neutral), `targets` (mob, item, room, exit), `mana`, `base_ticks` and `level_scale` covers all 51 classes. Effects are objects with hooks: `on_tick`, `allow(event)` for the veto step, `modify_stats(stats)` for stat changes, and `on_expire`. This matches the two-phase message flow, but for one player it is a plain list of filters.
- Durations to copy. Use the base formulas as a starting point: beneficial `4*level + 40` ticks and malicious `round(1.1*level) + 25` ticks. Keep the tick as the unit and state its length in seconds in one constant.
- Proficiency with a difficulty term. Pass a difficulty into a single `cast_check(caster, difficulty)` function. Use the level-difference penalties in this bucket as concrete tuning: 5 per level for Laughter and Lower Resistance, 25 per level for Knock, and 75 or 40 for a changed movement or breathing mode in polymorph.
- Borrow the Lighthouse sweep. Store the lit set and the direction index. Each tick, remove the rooms that left the set and add the new ones. Keep one short-lived light object per room.
- Borrow the planar choice. Store each plane's alignment and inclination as numbers and pick `max(planes, key=lambda p: abs(a - p.alignment) + abs(i - p.inclination))`. Drop the "Outer" category filter unless the Python game has such a category.
- Borrow Know Fate as a prediction. Deep-copy the two combatants, run the real combat step for up to N rounds, and report the win percentages. No separate model is needed. Drop the alias-script input unless the Python game has macros.
- Borrow the trigger model from Magic Item. Give an item a list of triggers such as `{"on": "hit", "chance": 0.25, "max_rounds": 12, "spell": "..."}`. Keep the experience cost as a deliberate sink.
- Multiplayer assumptions to rethink. Majesty's retinue, ORDER forwarding and "too much in awe" protection assume other players and NPC followers. In a single-player game, followers can take ordinary commands. Limited Wish's refusal to change another player's target has no meaning for one player. Imprisonment's player branch has no use unless the game has planar travel.
- Drop. The MSP sound tags in Knock and Magic Missile, the Magepox group penalty as written, the statue name hack, and the message-interception approach to orders.
- Simplify. Many spells differ only in their effect and their message. Merge the family of Know spells into one `Divination` class with a table of what it reveals, and merge the room effects (Insect Plague, Increase Gravity, Lighthouse) under one room-effect base.

## Coverage

- Read in full: `Abilities/Spells/Spell_Majesty.java`, `Spell_Lighthouse.java`, `Spell_Imprisonment.java`, `Spell_ImprovedPolymorph.java` (from line 20), `Spell_InvisibilitySphere.java` (from line 20), `Spell_IncreaseGravity.java` (from line 20), `Spell_LightSensitivity.java`, `Spell_ImprovedRepairingAura.java`, `Spell_MagicItem.java`, `Spell_LocateObject.java`, `Spell_KnowFate.java`, `Spell_MagicMouth.java`, `Spell_Levitate.java`, `Spell_InsectPlague.java`, `Spell_LimbRack.java`.
- Read in part: `Spell_Light.java` (`castingQuality`, `affectPhyStats`, `unInvoke` message and the invoke section, plus the digest of the rest).
- Read in large part (method bodies and cited regions): `Spell_KnowBliss.java` (lines 84-190), `Spell_Laughter.java` (84-175), `Spell_Knock.java` (76-197), `Spell_KineticPulse.java` (80-151), `Spell_MagicMissile.java` (88-121), `Spell_InsatiableThirst.java` (73-101), `Spell_Irritation.java` (84-109), `Spell_ImprovedHarden.java` (80-120), `Spell_MagicBullets.java` (84-133), `Spell_LesserPermanency.java` (100-158), `Spell_LowerResists.java` (95-136), `Spell_LightBlindness.java` (79-93, 150-170), `Spell_KnowAlignment.java` (78-106), `Spell_LimitedWish.java` (55-79).
- Skimmed (method signatures, messages and key lines from a digest of each file, plus the cited lines): `Spell_KnowOrigin`, `Spell_MageClaws`, `Spell_IronGrip`, `Spell_MageArmor`, `Spell_KnowProvince`, `Spell_KineticBubble`, `Spell_LedFoot`, `Spell_Invisibility`, `Spell_LightenItem`, `Spell_KeenEdge`, `Spell_MagicalAura`, `Spell_Infravision`, `Spell_ImprovedInvisibility`, `Spell_KnowValue`, `Spell_KnowPain`, `Spell_Lightning`, `Spell_ImprovedPlanarmorph`, `Spell_ImprovedPlanarDistortion`, `Spell_MajManaShield`, `Spell_LesserImage`, `Spell_ImprovedClanWard`.
- Dependencies read only as far as needed: `Abilities/Spells/Spell.java` (full), `Abilities/StdAbility.java` (`adjustedLevel` at 899, malicious duration at 2174-2183, beneficial duration at 2312-2330, `beneficialAffect` at 2341), `core/CMProps.java:681`.
- Not read: the six base classes outside the bucket (`Spell_Planarmorph`, `Spell_PlanarDistortion`, `Spell_Wish`, `Spell_ManaShield`, `Spell_MinorImage`, `Spell_ClanWard`), the Prop_* behaviours used by Magic Item, and the `proficiencyCheck` body in `StdAbility`.
- Skipped: none of the 51 files. Each was at least digested.
