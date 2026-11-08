# Abilities/Skills, part 3: Skill_JailKey to Skill_ScrollFamiliarity

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b081` (Abilities/Skills [part 3/4: Skill_JailKey .. Skill_ScrollFamiliarity]) · 36 files · 9234 lines of Java.

Citation convention: `Skill_X.java:N` means `Abilities/Skills/Skill_X.java` line N. Other paths are relative to the package root `com/planet_ink/coffee_mud/` (for example `core/CMProps.java:681`), or are given in full where they sit elsewhere in the tree.

## Summary

These 36 skill classes form the non-combat utility layer of CoffeeMud's skill system: finding items and mapping distant regions, recalling lore, copying and identifying scrolls, lasso, rope and mount tricks, bribing officers and running jails, commanding ships and signalling by light, and a handful of torture-style or body-affecting actions. Every class extends `StdSkill`, directly or through `Skill_Stonecunning` or `Skill_DeepBreath`. Most declare trigger words, a classification (`ACODE_SKILL` plus a `DOMAIN_*`) and a usage type, and they work in one of three ways: a multi-tick action advanced by the mob's tick (research, reveal text, rope tricks, mind suck), a message hook (`okMessage`/`executeMsg`) that vetoes or follows other actions (parry, untie, prisoner hand-over, morse relay), or a passive that changes stats or attaches state to other objects (long reach, resistance, scroll familiarity). For a single-player text game the most reusable parts are the timed research model, the region-map construction from pathfinding trails, the expertise-tiered lore, the law-state hand-offs, and the pattern of effects that live on items the player does not own. Ship AI, morse relay, recall-with-followers and the body-affecting skills are either multiplayer-specific or content-heavy.

## Key files and types

Shared bases used by every class: `StdSkill` and `StdAbility` (`proficiencyCheck`, `beneficialAffect`, `maliciousAffect`, `helpProficiency`). Helpers referenced but outside this bucket (located, not read): `Skill_Stonecunning`, `Skill_DeepBreath`, `Skill_Buck`, `Skill_HandCuff`, `Thief_Bind`, `Prisoner`, `InvisibleInk`, `Disease_Leeches`, `Disease_Lockjaw`.

| File | Role (trigger words) | Lines |
|---|---|---|
| Skill_ResearchRegionMap.java | Multi-tick research that draws an ASCII map of a distant area from room trails (RESEARCHREGIONS, RESEARCHREGIONMAP) | 727 |
| Skill_PlanarLore.java | Recalls facts about a plane of existence; detail tiered by expertise (PLANARLORE, PLORE) | 501 |
| Skill_Lassoing.java | Throws a rope at a mob and binds it with Thief_Bind (LASSO) | 478 |
| Skill_NavalTactics.java | Sets a ship's stance (FOLLOW, APPROACH, RETREAT, FLEE) and issues COURSE orders each tick (NAVALTACTICS, NTACTICS) | 426 |
| Skill_ResearchItem.java | Multi-tick search of nearby rooms and inventories for a named item (RESEARCHITEM) | 423 |
| Skill_RegionalAwareness.java | Outdoor terrain minimap around the caster (REGION, REGIONALAWARENESS) | 368 |
| Skill_PrisonerTransfer.java | Pays an officer to hand the next prisoner to the caster (PRISONERTRANFER, PTRANSFER) | 355 |
| Skill_RopeDismount.java | Pulls a lassoed, mounted mob off its mount (ROPEDISMOUNT) | 333 |
| Skill_MountedLeap.java | Leap in a direction, alone or with a mount; grants flying during the jump (MLEAP) | 318 |
| Skill_PrisonAssignment.java | Pays an officer to send prisoners to a cell the caster designates (PRISONASSIGNMENT, PASSIGN) | 301 |
| Skill_RopeTricks.java | Panhandles on a city street by twirling a rope; passers-by may pay (ROPETRICKS) | 300 |
| Skill_Map.java | Writes room IDs onto a readable map as the caster enters rooms (MAP) | 297 |
| Skill_RevealText.java | Multi-tick reveal of text hidden by invisible ink (REVEALTEXT) | 280 |
| Skill_MorseCode.java | Auto-invoked signalling by light or knocking, relayed to other Morse users in range (MORSECODE, MORSE) | 276 |
| Skill_RacialLore.java | Recalls facts about a race; number of facts scales with expertise (RACIALLORE, RLORE) | 275 |
| Skill_MindSuck.java | Drains the brain of a willing or helpless follower; kills and feeds the caster (MINDSUCK) | 267 |
| Skill_ScrollFamiliarity.java | Auto: scroll spell names become visible after a familiarity delay (none) | 248 |
| Skill_JailKey.java | Locks or unlocks a jail door; unlocking releases prisoners in the cell (JAILKEY, JKEY) | 239 |
| Skill_LongReach.java | Auto: adds one to the maximum range of a melee weapon (none) | 227 |
| Skill_Meditation.java | Sitting meditation that restores mana each mob tick (MEDITATE) | 216 |
| Skill_Lobotomizing.java | Torture-type skill: strips mind-altering effects and behaviours, lowers skill levels, makes the target forget one skill (LOBOTOMIZE, LOBOTOMIZING) | 213 |
| Skill_RustingStrike.java | Strike that damages a worn metal item and leaves rust dust carrying a lockjaw disease (RUSTINGSTRIKE) | 205 |
| Skill_Nippletwist.java | Torture-type grip attack on the chest; damage scaled by tattoo names; never lethal (NIPPLETWIST) | 196 |
| Skill_Recall.java | Teleports the caster, and monster followers, to the start room (RECALL, /) | 190 |
| Skill_PlanarVeil.java | Toggle that hides the caster on a non-prime plane in woods, swamp or jungle (PLANARVEIL, PVEIL) | 182 |
| Skill_Revoke.java | Dispels spells, songs, prayers and chants the caster put on a target (REVOKE) | 173 |
| Skill_ScrollCopy.java | Memorizes a spell from a scroll by teaching it through a temporary Teacher mob (MEMORIZE) | 171 |
| Skill_ResistBuck.java | Auto: resists a mount's buck attempts, then disables that mount's auto-buck (none) | 162 |
| Skill_Parry.java | Auto: parries one melee attack per round (none) | 149 |
| Skill_Math.java | Evaluates a math formula for the caster (MATH) | 133 |
| Skill_Leeching.java | Applies leeches to a follower by invoking Disease_Leeches (LEECH, LEECHING) | 123 |
| Skill_Resistance.java | Auto: adds saving throws equal to proficiency percent of level (none) | 122 |
| Skill_MountedCombat.java | Auto: attack and damage bonus while fighting from a mount (none) | 121 |
| Skill_RelicUse.java | Marker skill that lets prayer-type wands and relics be used (implements Wand.WandUsage) | 99 |
| Skill_Keenvision.java | Subclass of Skill_Stonecunning: outdoors only; attack bonus with a ranged weapon | 83 |
| Skill_LongBreath.java | Subclass of Skill_DeepBreath: longer maximum breath | 57 |

## How it works

### Shared machinery

- **Dispatch.** `triggerStrings()` maps command words to the class (`Skill_PlanarLore.java:72`). `invoke()` does its validation (combat, sight, position, targets) before calling `super.invoke()` (which handles usage costs; not traced here), and then calls `proficiencyCheck`. Failure goes through a fizzle helper such as `beneficialWordsFizzle`, `maliciousFizzle` or `beneficialVisualFizzle`.
- **Proficiency roll.** `StdAbility.proficiencyCheck` (`Abilities/StdAbility.java:1441-1485`) works as follows. Start from `proficiency()`, add `2 * expertise`, and clamp to 5 to 95 unless the 955-rule is disabled. Add `expertise / 2`. If the adjustment is non-negative it is added to the chance. If it is negative it is not subtracted: it acts as a gate, and the check fails outright when the percentile roll exceeds `100 + adjustment`. Success requires `roll < chance`. An `auto` call always succeeds, and sets proficiency to 100 when the mob does not own this ability instance (`StdAbility.java:1443-1449`). Admins with the SUPERSKILL flag always pass (`StdAbility.java:1455-1456`).
- **Learning by use.** `helpProficiency` is called on successful or useful events. Examples are parry (`Skill_Parry.java:141`) and mounted combat, which trains about one time in ten when the mounted fighter damages something (`Skill_MountedCombat.java:115-118`).
- **Passives.** Auto-invoked skills set `isAutoInvoked()` true and `canBeUninvoked()` false, so they stay on the mob permanently. Examples are `Skill_Parry.java:82-91`, `Skill_Resistance.java:81-91`, `Skill_LongReach.java:83-92`, `Skill_ResistBuck.java:79-88`, `Skill_MorseCode.java:77-86` and `Skill_ScrollFamiliarity.java:81-91`.
- **Tick gating.** Multi-tick skills act only on `Tickable.TICKID_MOB`, for example `Skill_ResearchRegionMap.java:233` and `Skill_Meditation.java:145`. In this codebase a mob tick is the 4-second default tick (`core/CMProps.java:681`). A mud hour is `MILLIS_PER_MUDHOUR = 600000` ms (`core/CMProps.java:682`), which is 150 ticks at defaults (`core/CMProps.java:3539-3541`).

### Research, mapping and records

**Library gate.** Both research skills need books. `numBooksInLibrary` counts 10 for a room whose display text contains "library", plus 1 for each readable Book with used pages in the room, the mob's inventory, or a shop's stock (`Skill_ResearchRegionMap.java:603-652`; `Skill_ResearchItem.java:313-362`). Zero books stops the attempt with "I don't think you'll get much research done here." (`Skill_ResearchItem.java:377-381`). The count is cached per room and reset in `setMiscText` (`Skill_ResearchItem.java:129-142`).

**Start and clock.** A successful start uses `beneficialAffect(mob, mob, asLevel, 10)` and then sets `tickDown=10 // override any expertise!` (`Skill_ResearchRegionMap.java:714-717`; `Skill_ResearchItem.java:410-415`). The research effect is a timed affect whose `tickDown` is the remaining work. Research aborts if the researcher leaves the room, enters combat, cannot be seen, or is bound or unconscious (`Skill_ResearchRegionMap.java:259-268`; `Skill_ResearchItem.java:175-184`). Progress messages appear when `tickDown==4` and every four ticks after that (`Skill_ResearchRegionMap.java:269-285`).

**ResearchItem (find a thing).** Each tick it works through a list of rooms from `CMLib.tracking().getRadiantRooms` with range `25 + 2*expertise + 10*maxrange` (`Skill_ResearchItem.java:227`). It checks rooms in batches sized `checkSet.size()/(tickDown-1)+1` (`:230`), and it scans everything at once when `tickDown < 3` (`:242-243`). Inaccessible rooms and privately owned land are skipped (`:250-252`). Each room is searched for a named item, then for inhabitants carrying it or shops stocking it (`:254-300`). Each hit adds time: `ticksToRemain += 2*itemLevel - adjustedLevel(mob,0) - numBooksInRoom`, floored at 0 (`:262-265`, `:289-292`). The final `tickDown += ticksToRemain` runs when the rooms run out or `tickDown==2` (`:306-307`). Results are capped at `1 + expertise` and pruned at random (`:236`, `:301-304`). A higher-level item therefore takes longer, and more books shorten the search. An interrupted search throws away its finds (`:180`) and reports "Your research fails to find anything" (`:108-109`).

**ResearchRegionMap (draw a distant area).** The pipeline runs as follows.
1. The target area is looked up by name with `CMLib.map().findArea` (`:311`). It must be accessible and must not be the caster's own area or metro (`:689-701`).
2. A radiant search with range `45 + 2*expertise + 10*maxrange` (`:309`) finds the first accessible room with a room ID in the target area (`:326-340`). `CMLib.tracking().findAllTrails` then gives the paths (`:348`).
3. For each trail, each transition between areas is counted as a connection (`:350-374`). For each area pair, the code sums the grid displacement along the trails, counting outdoor steps double (`:414-419`), and divides by the total number of trails (`:439-440`).
4. The average displacement is quantized to eight directions. The thresholds are `atan < 0.392` (about 22.5 degrees, cardinal) and `atan > 1.18` (about 67.5 degrees, the other cardinal), with diagonals in between (`:452-463`).
5. Areas are placed on a grid, with rows and columns inserted as needed (`:473-527`).
6. Each area is rendered as a 10-column by 5-row box, with the area name wrapped over three 8-character lines (`:539-586`). The output is a `GenReadable` item named "A region map from X to Y" (`:587-593`). The research takes an extra `5 * connections` ticks (`:594`).

A start-to-target pair is remembered in a `LimitedTreeSet` of 50 entries that expires after `TimeManager.MILI_HOUR` (one hour, `Libraries/interfaces/TimeManager.java:442`), so repeating the same pair within that hour fails (`:130`, `:314-323`). The random sampling of up to 90 percent of accessible areas (`:288-308`) has no effect on the output; see the cruft list.

**RegionalAwareness (terrain minimap).** `roomChar` gives a glyph for each domain type (`Skill_RegionalAwareness.java:150-208`). `roomColor` gives a colour letter for the same domain types (`:89-148`). `getMiniMap` (`:210-304`) builds a square grid around the room. It walks outward over passable, non-air rooms that have a visible exit between them (`:223-228`, `:242-268`). Indoor rooms stop further expansion (`:280-281`). The caster's room is shown as `*` (`:232`). Outside auto mode the skill works only outdoors (`:330-334`), and the diameter is `2 + adjustedLevel/10` (`:353`). The auto branch (`:310-328`) returns the lines to a caller's command list or session. What calls that branch is unverified.

**Map (write rooms onto a map).** `Skill_Map` converts a readable item into a `BardMap` (a RoomMap item). The conversion copies name, description and material, and sets the base value to twice the original (`:242-260`, `:248`). When the caster enters a visible room that has not been mapped, it appends `;<extended room ID>` to the readable text (`:160-170`). Maps are capped at 4096 characters (`:160`). Invoking the skill while already mapping ends it (`:181-186`). The effect ends when the map is no longer owned by the caster (`:149-151`).

**ScrollCopy (memorize).** The command needs a spell word and a scroll (`:93-97`). The spell is matched by name against the scroll's spells (`:117-127`). A temporary `Teacher` mob (`:136`) gets a copy of the spell at proficiency 50 (`:140-141`). `teach()` runs against the caster (`:154`), and experience is granted if the caster then knows the spell: `round(100 * lowestQualifyingLevel / qualifyingClassLevel) + expertise` (`:155-162`). The scroll is not consumed. The mana cost is overridden to zero (`:84-87`).

**ScrollFamiliarity (scroll identification).** This is auto-invoked. When the caster picks up a scroll in its `familiar` set (`TYP_GET`), or reads one while holding a divining-flagged effect, a copy of the skill is attached to the scroll (`:96-127`). Its `readyTimeout` is `now + TickMillis * (100 - proficiency)` (`:118`). At proficiency 50 that is about 200 seconds of real time with the 4-second tick. Once ready, the scroll's name becomes "a scroll of <spell names>" (`:211-227`). The familiar set is a `LimitedTreeSet` of 100 entries with an expiry of `CMProps.getMillisPerMudHour()*1000` (`:93`). That value is a thousand mud hours, which is a suspected unit error (see cruft).

**RevealText (read invisible ink).** The target must be held, or be a fixed room item (`Skill_RevealText.java:229-238`). The skill succeeds only if the item carries the `InvisibleInk` effect (`:254-261`). The effect runs for 20 ticks (`:267`) and reports percent complete every four ticks (`:143-151`). `confirmSuccess` aborts if the item has left its owner or cannot be seen (`:109-116`). On completion, a copy of the ink effect is attached to the reader while the page is read, then removed (`:179-206`).

### Lore and recall

**PlanarLore.** The plane data is `resources/skills/planesofexistence.txt`, which holds 37 entries (read through `StdPlanarAbility.getAllPlanesMap`, `Skill_PlanarLore.java:121`). Plane lookup is exact match first, then prefix (`:151-171`). Standing on the queried plane adds +25 to the roll (`:186`). A failure starts a 10-second cooldown (`:138-142`, `lastFail`, an instance field). One fact is chosen at random (`:489`). The facts unlock by expertise, as follows.

| Expertise (XLEVEL) | Facts added (`Skill_PlanarLore.java`) |
|---|---|
| Any | Creature resistances (`MOBRESIST`), name prefixes, weapon max range, atmosphere (`:201-231`) |
| Above 0 | Hybrid race (`MIXRACE`), special flags such as no infravision or all-breathe (`:232-263`) |
| Above 1 | Creature multiplier (`MOBCOPY`), resource abundance (`:265-288`) |
| Above 2 | Weather and climate (`Prop_Weather`, gated on misc text, see cruft); height and weight scaling (`:289-345`) |
| Above 3 | Average level difference (`LEVELADJ`), description (`:346-371`) |
| Above 4 | Abilities creatures use (`ENABLE`), bonus-damage stat (`:372-395`) |
| Above 5 | Elite creatures; weapon types needed (`:396-412`) |
| Above 6 | Damage absorption (`:413-424`) |
| Above 7 | Hours per day, alignment, faction ranges (`:425-453`) |
| Above 8 | Recovery and fatigue rates (`:454-469`) |
| Above 9 | Area blurbs (`:470-479`) |

**RacialLore.** This works the same way for races. It draws from the `Race` object: genus, stat adjustments (a `-` marks a penalty, `:166-173`), sense and disposition changes, racial and cultural abilities, immunities, body-part counts, maturity age, forbidden worn locations, breathables, height and weight ranges, natural weapon, butcherable resources and rideability (`:165-249`). The caster hears `expertise + 1` facts, each removed once used (`:260-267`). The cooldown and fizzle logic is the same as PlanarLore.

**Math.** `Skill_Math.java:103-132` checks the text with `CMath.isMathExpression`, computes it with `CMath.parseMathExpression` and tells the caster the result. The room sees "work(s) through a math problem".

**Recall.** Out of combat it always succeeds (`Skill_Recall.java:91`). In combat it rolls with a `10 * expertise` bonus. The destination is `CMLib.map().getStartRoom(mob)` (`:97`), and players without one get the default start room (`:98-102`). Monster followers that are not autoguard come along (`:135-180`). The `group` switch is hard-coded to false (`:90`), which disables group recall. In combat the caster flees to "NOWHERE" first (`:115-116`).

**PlanarVeil.** This is a toggle. It needs a non-prime plane (`Skill_PlanarVeil.java:156-162`) and makes an indefinite effect (`:173-175`). The effect sets `IS_HIDDEN` (`:138`). Every tick it removes itself unless the room is woods, swamp or jungle and the area is on a plane (`:99-112`). The comment above the stat change says the mob is "put into a sleeping state", which the code does not do (see cruft).

**Revoke.** It accepts a room, area, self, exit, mob or item target (`Skill_Revoke.java:96-125`). Only effects whose `invoker()` is the caster, and whose class is spell, song, prayer or chant, are candidates (`:137-143`). The loop has no `break`, so the last match is revoked (`:133-145`). Revocation needs a `proficiencyCheck` (`:159`). Range is `adjustedMaxInvokerRange(10)` (`:84-87`).

**RelicUse.** This marker skill implements `Wand.WandUsage` with enchant type `ACODE_PRAYER` (`Skill_RelicUse.java:35-61`). `StdWand` uses any `WandUsage` ability whose enchant type matches the wand's, and passes that ability's `abilityCode()` (which here is the caster's expertise) as the power level (`Items/MiscMagic/StdWand.java:239-274`).

### Ropes, lassos and mounts

**Lassoing.** The lasso is a held or wielded item that `CMLib.flags().isARope` accepts (`Skill_Lassoing.java:275-293`). When `auto` is set, a generic hemp lasso is created instead (`:283-291`). The caster needs a free hand (`:377-385`). The throw is `proficiencyCheck(mob,0)` followed by `rollToHit` (`:390-395`). The lasso then drops in the target's room and moves into the target's inventory (`:398-407`). Animals and their group are made peaceful when no one is fighting, so the lasso does not start combat (`:439-456`). The binding is a `Thief_Bind` effect on the target, whose misc text names the rope (`:457-466`). The lasso item itself carries this skill as an effect with duration `-1` (`:457`). The meaning of `-1` in `beneficialAffect` is unverified.

Breaking the binding has two paths. Damage from a non-lasso source to the bound mob removes the binding, drops the lasso and gives it to the binder (`:245-270`). The other path is an intercepted command. `okMessage` catches "HUH" and "command fail" messages from the binder that look like `get`/`take` of the lasso or `untie <mob>`, and routes them to `untieIfPoss` (`:172-240`, `:126-170`). That function checks sight, awake state and whether the binder is the one who bound the target (`:133-137`). It then runs the untie as a message and moves the lasso to the binder if the target is no longer bound (`:143-163`).

**RopeTricks.** The skill requires standing, a city street (`Skill_RopeTricks.java:265-269`) and a wielded or held rope (`:271-281`). It is an ongoing effect with no duration (`:296`). It acts every two mob ticks (`:127-130`). Each tick it picks one visible inhabitant it has not yet hit up (`:153-160`) and rolls a proficiency check (`:160`). A 1d10 flavour message follows (`:162-204`). If a percentile roll exceeds the target's justice save, plus 10 if the performer is good, the target hands over coins (`:205-221`). The amount is the target's total native value divided by `20 - expertise`, with a minimum of 1 (`:207-212`). The coins are moved with a forced GIVE command (`:218`). Each target is hit up once, and the list forgets its oldest entry with a 5 percent chance each tick (`:225-230`). The effect ends if the performer stops standing, is no longer awake and unbound, enters combat (`:135-141`), or leaves the room (`:106-109`).

**RopeDismount.** The target must be lassoed by the caster (`getTargetRope` looks for a Rideable item carrying `Skill_Lassoing` whose invoker is the caster, `Skill_RopeDismount.java:160-176`) and must be mounted, either riding a mobile mount, a wagon or a boat (`:183-210`). The check adjustment is `-levelDiff - (35 + round((targetDEX - 9) * 3))`, where `levelDiff` is the target's level above the caster's plus twice expertise, multiplied by 5 when positive, minus `abilityCode * DEX` (`:281-287`). On success a dismount message is sent (`:294-304`). If the target was not riding it falls into the room (`:317-319`) and takes a malicious affect with arguments 2 and -1 (`:323`) and the message "You hit the ground!" (`:324`). While the effect runs the target is forced to sit (`:112-117`), and attempts to stand are refused (`:129-130`) until the effect unwinds (`:135-158`).

**MountedLeap.** The leap chooses a direction (or a portal or rideable target), checks that the exit is open, and refuses to jump up into air (`Skill_MountedLeap.java:99-194`). A jumper who is not mounted must have legs and feet and not be falling (`:197-215`). A mounted jumper's mount must be standing (`:226-238`). The number of jumps is `1 + round(expertise / 2.5)` in a direction (`:246-248`). During each jump both rider and mount get a non-savable flying effect (`:257-273`). Later jumps continue only while the jumper is still in an airy room (`:296-306`).

**MountedCombat.** While the rider is in melee range and in combat, attack adjustment increases by `(1 + expertise/3) * base attack adjustment` and damage by `(1 + expertise/3) * base damage` (`Skill_MountedCombat.java:97-106`). The bonus is a multiple of the base stat, so it is zero when the base is zero.

**ResistBuck (bronco busting).** When a `Skill_Buck` attempt targets the rider, the rider resists with a chance of `expertise + round(100 * proficiency * 0.9)` (`Skill_ResistBuck.java:134-141`, arithmetic in cruft below). A successful resist records the mount and sets `mountTicks = 30 - expertise` (`:146-152`). It then cancels the buck message (`:153-155`). While the rider stays mounted, `mountTicks` counts down. When it reaches zero, the mount gets the `NO_AUTO_BUCK` ambiance and its `Skill_Buck` behaviour is removed (`:109-120`). Nothing in this file clears that ambiance.

### Law and prisons

Three skills depend on the legal system: `CMLib.law().getLegalBehavior(R)` for the room's behaviour, `getLegalObject(R)` for the jurisdiction, and warrants that carry a `state()` (`Skill_PrisonerTransfer.java:275-283`). The relevant state values are in `Common/interfaces/Law.java`. These are `STATE_SEEKING=0` (`:696`), `STATE_MOVING=3` ("taking the criminal to trial", `:702`), `STATE_JAILING=7` ("sentencing to jail", `:710`) and `STATE_MOVING2=9` ("taking the criminal to jail", `:714`).

**PrisonerTransfer.** The caster must not be in combat, the officer must be awake and able to hear the caster, must be an officer here, and must be eligible (`Skill_PrisonerTransfer.java:262-289`). The price is `totalNativeValue(officer)/10`, with a floor of `level/2` (`:291-294`). It is paid in coins that the officer receives (`:317-329`). The effect lasts `ticksPerMudHour * hoursInDay * (1 + expertise)` (`:338`). The effect is attached to the officer. When the officer moves a prisoner it is arresting to the trial state (`STATE_MOVING`) in the same room, the warrant's jail is set to the caster's room (`:204-227`). When the prisoner enters the caster's room, the officer says "OK, <prisoner>'s is all yours." (`:158`). The arresting officer is switched to the caster (`:159-168`), any hand-cuffs are reassigned (`:177-183`), the prisoner follows the caster (`:169-176`), and the officer wanders away (`:184`).

**PrisonAssignment.** The caster designates a cell with `SETPRISON` (`Skill_PrisonAssignment.java:166-191`). The room must have a room ID, a door with a lock next to it, and must not be an instance child or a deadly or malicious room. The price is `totalNativeValue/2`, with a floor of the officer's level, multiplied by 1.25 (`:230-234`). The caster must have a route to the cell within 50 steps (`:242-255`). The effect redirects the warrant's jail when the officer moves a prisoner to jail (`STATE_MOVING2`) and the warrant is in the jailing state (`:104-139`). It then removes itself (`:133`). The help file describes the bribe as lasting for at least a day and needing renewal after each move (`resources/help/skill_help.ini:4080-4087`).

**JailKey.** The caster names a direction with a locked exit (`Skill_JailKey.java:138-201`). Either the caster's room or the room beyond must be a jail by display text (the words "jail", "prison" or "dungeon", `:118-133`) or be recognized by the legal behaviour as a jail room, or be right outside one (`:149-163`). A success toggles the lock on both sides with `roomAffectFully` (`:212-219`). Unlocking releases any `Prisoner` effects of mobs in the room beyond (`:220-233`).

### Sea and signals

**NavalTactics.** The caster must be on the deck of a water-based ship they have privileges on, outdoors, and the ship must be in combat with another ship in the same room (`Skill_NavalTactics.java:336-394`). The stance is set with `setMiscText`, which parses the tactic name (`:322-331`). Each tick the stance computes a target distance and a course (`:132-311`).

| Stance | Target distance (`Skill_NavalTactics.java`) | Course |
|---|---|---|
| APPROACH | 0 (`:163-164`) | Toward the enemy |
| FLEE | Enemy's max range + 1 (`:166-167`) | Away from the enemy, using a navigable direction if one exists (`:221-250`) |
| FOLLOW | Current range, fixed on first tick (`:169-173`) | Close or open the gap to the set distance (`:252-273`) |
| RETREAT | Highest siege-weapon range in the enemy area + 1 (`:175-198`) | Away from the enemy (`:274-286`) |

A non-empty course enqueues `COURSE` with direction names, preceded by `RAISE ANCHOR` if anchored (`:291-300`), and sets `wait=true` (`:300`). `wait` is cleared only by rider actions that match `okMessage` (`:113-130`), or by setting a new tactic (`:329`). The effect ends when the combat ends (`:304-308`).

**MorseCode.** This is auto-invoked and cannot be removed. Outdoors, sending needs a held light source (`Skill_MorseCode.java:162-171`). Indoors, the caster bangs on walls (`:186`). The radius is `3 + expertise/2 + maxrange` (`:204`). The signal propagates through the room graph in layers (`:226-269`). Each layer learns the direction back to where the signal first arrived, and that direction is phrased for the listener ("somewhere to the <direction>", `:250-260`). On a ship the deck and other ships have their own phrasing (`:213-254`). A listener who also has the skill receives the decoded text (`:109-122`).

### Body-affecting skills

These skills are torture-type or violent. They are described here in code terms only; their flavour messages are paraphrased.

**Lobotomizing.** The target must be prone, bound, or a follower, unless the call is automatic (`Skill_Lobotomizing.java:108-119`). It must be seated or lying on something (`:121-129`), and must have nothing worn on the head (`:131-140`). On success the skill removes all non-auto mind-altering effects (`:153-161`) and deletes aggressive and troublemaking behaviours (`:162-167`). Every skill above proficiency 25 is reduced to `(p - 25) * 0.9` (`:172-186`). One of those skills, chosen at random, is set to zero (`:187-197`). The skill is flagged `FLAG_TORTURING` (`:83-86`).

**MindSuck.** The target must be a helpless mob or a follower of the caster (`Skill_MindSuck.java:215-228`). The level penalty is `(caster level - target level) * 10`, clamped at zero (`:233-237`). Success adds a four-tick effect (`:254`) that sets `CAN_NOT_MOVE` on the target (`:186`). Each tick the effect ends if either side is in combat, the target is awake and not following, the two are in different rooms, or the target is dead (`:114-139`). When it ends with the target still drainable, the caster kills the target and gains hunger relief (`:155-166`). If the target is alive and present but no longer drainable, it strikes back at the caster (`:167-175`).

**Nippletwist.** A free hand is needed (`Skill_Nippletwist.java:133-144`). The target must not be wearing torso gear (`:127-131`). The proficiency check adjustment is `-levelDiff - (targetDEX - casterSTR)` (`:157`), and a hit roll is also needed (`:156`). Damage is `roll(1, adjustedLevel/10 + 2)` (`:170-177`), doubled for a tattoo prefixed "left nipple:" or "right nipple:" and multiplied by five for one prefixed "nipples:" (`:171-176`). Damage is halved if another effect has reduced the message value (`:178-179`). The damage is capped at one hit point below the target's current hit points (`:180-181`), so the skill cannot kill.

**Leeching.** A follower or willing target receives `Disease_Leeches`, invoked by the caster at the caster's level (`Skill_Leeching.java:111-113`).

**RustingStrike.** The strike deals damage with `topDamage = adjustedLevel/6 + 1` (`Skill_RustingStrike.java:114-115`). The candidate items are one per worn location, with clothing layers preferred (`:127-161`). If at least one worn metal item of hardness below 10 exists, a random candidate is chosen (`:162-166`). If that candidate is metal, it takes `10 * (10 - hardness)` item damage (`:171`, `:173`). A dust food item named "some rust" is placed in the room (`:174-194`). It carries a `Disease_Lockjaw` effect (`:189-191`).

### Passive and defensive skills

**Parry.** The caster parries a melee attack when the attacker's weapon is not flail, natural, ranged or thrown, and neither is the caster's (`Skill_Parry.java:111-134`). The check is `proficiencyCheck(null, DEX - 90 + expertise)` (`:136`). Success cancels the attack message and sends "parries" (`:135-142`). Only one parry is allowed per round, tracked by `doneThisRound`, which resets on mob ticks (`:93-99`, `:114`). Because the adjustment is negative for ordinary ability scores, the check works as a gate: see cruft.

**Resistance.** The bonus is `round(proficiency/100 * level)` (`Skill_Resistance.java:115`). It applies to one saving throw if the skill names one, or to all of them otherwise (`:116-120`). The skill is a no-op without an invoker (`:113-114`).

**LongReach.** The skill attaches a copy of itself to each wielded melee weapon (`Skill_LongReach.java:139-153`, `:188-211`). That copy sets the weapon's max range by writing into the armor field's bits (`:173-175`). The check runs every five ticks (`:188-190`).

**Keenvision.** Keenvision is Stonecunning with a different room test. It applies only in outdoor natural terrain, not indoors and not in city, water, air, space or seaport rooms (`Skill_Keenvision.java:65-81`). While the caster holds a ranged weapon it adds `10 + expertise` to the attack adjustment (`:57-60`).

**LongBreath.** It subclasses DeepBreath and changes only the maximum breath ticks, to `5 + adjustedLevel/3` (`Skill_LongBreath.java:51-55`).

**Meditation.** Meditation requires sitting and no combat (`Skill_Meditation.java:182-196`). It lasts `TICKS_FOREVER` (`:206`). Each mob tick, a proficiency check runs (`:147`). Hunger or thirst at zero breaks it (`:150-159`). Otherwise, if the mob is still sitting and not fighting, it restores mana by `round(man * 0.1 + (level + 2*expertise)/2)`, where `man = INT + 2*expertise + WIS` (`:161-169`). Any movement, hands or speech action by the caster ends it (`:117-125`). Noise from others interrupts it with a message (`:126-133`).

## Content worth mining

**The 37 planes** in `resources/skills/planesofexistence.txt` are a ready-made list of setting locations. They are Earth, Air, Fire, Water, Positive, Negative, Magma, Ooze, Minerals, Dust, Smoke, Ice, Lightning, Vacuum, Radiance, Ash, Steam, Salt, Astral, Ethereal, Nirvana, Arcadia, Seven Heavens, Twin Paradises, Elysium, Happy Hunting Grounds, Olympus, Gladsheim, Limbo, Pandemonium, Abyss, Tarterus, Hades, Gehenna, Nine Hells, Acheron and Concordant Opposition. Each entry carries attributes such as opposition, prefixes, weather and resistances, and the data can be read as a template. Example from the file: "Earth" is opposed by "Air", has prefixes "stony, earthy, rocky", and a resistance line.

**Terrain glyphs and colours** (`Skill_RegionalAwareness.java:89-208`):

| Domain | Glyph | Colour |
|---|---|---|
| City | = | w |
| Woods | T | G |
| Rocks | : | W |
| Plains | _ | Y |
| Hills | h | g |
| Mountains | M | p |
| Desert | . | y |
| Swamp | x | r |
| Jungle | J | R |
| Seaport | P | y |
| Underwater or water surface | ~ | B or b |
| Spaceport | @ | P |
| Indoor stone, wood, cave, magic, metal | # | w, y, w, r, P |

**Region-map box glyphs** (`Skill_ResearchRegionMap.java:208-226`, `:560-581`). Walls are `!` on the sides and `-` above and below. A link to a neighbouring area changes the wall: `-` on the middle side row, `!!` on the top or bottom edge, and `\` or `/` at diagonal corners. Corners with no link are `+`.

**Lore facts by expertise** (table in How it works). These are short, readable tidbits: "Everything there is very <prefix>.", "Weapons there have a maximum range of <n>.", "Creatures there are <resistance>."

**Morse code** phrases (`Skill_MorseCode.java:120`, `:250-262`). Decoded signals read "The morse code signals seem to say '<text>'." Undecoded ones read "You see someone flashing morse code in the distance" or "...somewhere to the <direction>". A ship version refers to the deck.

**Naval stances** (table in How it works) map cleanly onto NPC war-ship behaviour in a single-player setting.

**Law states** (`Common/interfaces/Law.java:696-714`). The seven-state adjudication sequence is a readable template for a crime pipeline: seeking, moving to trial, reporting, jailing, moving to jail, release.

**Rope-trick flavour** (`Skill_RopeTricks.java:162-204`). Ten short street-performance actions, paraphrased: whirling the rope overhead or around the body, dancing with it, tossing and catching it, using it to grab a small item, forming a circle in the air or on the ground, and a jig.

**Tattoo-driven multipliers** (`Skill_Nippletwist.java:171-176`). Tattoo names with particular prefixes change a combat formula (two times, two times, five times). This is a data hook: a body mark changes the outcome without any code change in the skill.

**Research time formula** (`Skill_ResearchItem.java:262`). A search time of `2 * itemLevel - adjustedLevel - books` gives a concrete, tunable rule for how long a research task takes.

## Notable design ideas

- **Region maps from trails.** The map is a projection of real pathfinding trails. Area displacements are averaged, quantized to eight compass directions, and laid out on a grid (`Skill_ResearchRegionMap.java:348-527`). The player gets a map of places they have never seen, with no hand-authored layout.
- **Timed research.** A task is an affect with a counter. Progress is reported on a schedule, interruption throws away the work in progress, and the total time depends on the item's level, the researcher's skill and the books available (`Skill_ResearchItem.java:262-307`).
- **Expertise-tiered knowledge.** Lore draws one fact at random from a pool that expands with expertise (`Skill_PlanarLore.java:201-479`, `:489`). Knowledge is uncertain, and what a player knows depends on how well they know it.
- **Two-phase interception.** `okMessage` vetoes an action and `executeMsg` follows up. Lasso untie, parry and prisoner hand-off all use this. Parry returns false from `okMessage` to cancel an attack (`Skill_Parry.java:135-143`). Untie is caught from a failed command (`Skill_Lassoing.java:172-190`).
- **State on objects the player does not own.** A lasso binds through a `Thief_Bind` on the target and a lasso effect on the rope (`Skill_Lassoing.java:457-466`). Scroll familiarity attaches itself to scrolls (`Skill_ScrollFamiliarity.java:106-124`). Long reach attaches to weapons (`Skill_LongReach.java:147-152`). Caster ownership is tracked by `invoker()`, so Revoke can undo exactly what one caster did (`Skill_Revoke.java:137-143`).
- **Non-lethal torture.** The grip attack is capped at one point below the target's hit points (`Skill_Nippletwist.java:180-181`), so harm is a state change rather than death.
- **Legal hand-offs as state.** Bribes change which warrant a jail or a trial points to, rather than teleporting anyone (`Skill_PrisonerTransfer.java:204-227`, `Skill_PrisonAssignment.java:104-139`).
- **Reuse through a stand-in.** Scroll copy teaches through a temporary Teacher mob, reusing the normal learning pipeline (`Skill_ScrollCopy.java:136-155`).

## Problems and cruft

Bugs and suspect code found by reading. Items marked unverified depend on runtime behaviour not checked here.

1. **Skill_ResistBuck.java:140.** `100.0*CMath.mul(proficiency(), 0.9)` equals `90 * proficiency`, because `CMath.mul(double,double)` is plain multiplication (`core/CMath.java:493-496`). At proficiency 2 or more the chance exceeds 100, so every buck attempt is resisted. The likely intent was `0.9 * proficiency`.
2. **Skill_MountedLeap.java:188-190.** The condition `(dir==UP) && (outdoorsAir) || (indoorsAir)` has an operator-precedence error. Any horizontal leap into an indoor-air room is refused with "You can't leap that high."
3. **Skill_MountedLeap.java:244-255.** The proficiency roll at line 244 is overwritten at line 255 before use. The leap itself is not gated by success: the movement at lines 252-306 happens whatever the roll. A failed roll gives no message and no penalty except the returned flag.
4. **Skill_Lobotomizing.java:198-204.** The alignment faction is read and adjusted (`fac += 1000`), but never written back. The block has no effect.
5. **Skill_PrisonAssignment.java:192-195.** The "no cell" message says "Try PTRANSFER SETPRISON", but `SETPRISON` is handled by this skill, and the help file says PRISONASSIGNMENT (`resources/help/skill_help.ini:4077`). The branch also does not return, so the caster gets a second message from line 253.
6. **Skill_PrisonAssignment.java:238.** The message reads "it can't be donefor less than". The text is missing a space.
7. **Skill_PrisonerTransfer.java:94.** The trigger is `PRISONERTRANFER`, while the help file documents `PRISONERTRANSFER` (`resources/help/skill_help.ini:4093`). The full word does not match, and only `PTRANSFER` works as typed.
8. **Skill_PlanarLore.java:302-324.** The weather branch is gated on `text().length()>0`, which is this ability's own misc text, not the plane's data. That is usually empty, so the weather facts rarely appear (unverified). Line 319 also prints a resource-abundance sentence for weather parameters, which looks copied from line 284.
9. **Skill_Map.java:161.** `L("@x1 is full.")` is sent with no substitution values. The `@x1` token probably reaches the player literally. `CMStrings.replaceVariables` (`core/CMStrings.java:2002-2008`) is passed an empty list (unverified for `@x` tokens).
10. **Skill_ScrollFamiliarity.java:93.** The expiry is `CMProps.getMillisPerMudHour()*1000`. `LimitedTreeSet` takes milliseconds (`core/collections/LimitedTreeSet.java:66`, default 60000 at `:118`), so this is a thousand mud hours, about 6.9 real days at the default 600000 ms per mud hour (`core/CMProps.java:682`). This looks like a unit error.
11. **Skill_LongReach.java:95, 111-113.** `reachBonus` is parsed from misc text and never read again. The range effect is always +1 (`:173-175`). The armor field is also reused to carry the max range.
12. **Skill_NavalTactics.java:119-121.** The condition requires the rider's name to equal the ship's name. This looks wrong. If so, `wait` may never clear, and the AI would issue one course and then stall (unverified).
13. **Skill_ResearchRegionMap.java:288-308, Skill_ResearchItem.java:204-224.** The random area sampling builds a set that is only used to count itself in its loop condition. It does work per tick and changes nothing.
14. **Skill_ResearchRegionMap.java:439-440.** Each area pair's displacement is divided by the total number of trails, not the number of trails that contain that pair. Pairs seen on few trails get their direction diluted toward zero.
15. **Skill_RopeTricks.java:182, 186, 190, 194, 198.** Each message says `<S-HIS-HER> his`. If `<S-HIS-HER>` expands to "her" for a female performer, the text reads "her his" (token expansion not traced).
16. **Skill_RopeTricks.java:210.** `total/(20.0-getXLEVELLevel(mob))` has no guard. If expertise can reach 20 this divides by zero (unverified whether it can).
17. **Skill_Parry.java:136.** The adjustment is `DEX - 90 + expertise`. The constant 90 suggests a scale other than the usual ability range. For ordinary DEX values the adjustment is strongly negative. Because `proficiencyCheck` treats a negative adjustment as a ceiling on the roll (`StdAbility.java:1480-1484`), a parry then succeeds only on low rolls. The actual rate depends on the DEX range (unverified).
18. **Skill_RustingStrike.java:162-169.** The metal count gates the strike, but the rust target is chosen at random from all candidates. A non-metal pick silently does nothing.
19. **Skill_MorseCode.java:128.** The loop bound is `<=` and runs one past the last direction. This is harmless because `StdRoom.getRoomInDir` rejects out-of-range codes (`Locales/StdRoom.java:1664`).
20. **Skill_PlanarVeil.java:134-138.** The comment says the mob is put into a sleeping state. The code sets `IS_HIDDEN`.
21. **Skill_RacialLore.java:206.** `s=targetR.getAbilitiesDesc();` assigns a variable that is never read.
22. **Skill_Recall.java:90, 155-157.** The `group` flag is a constant false, so the group branch and its commented-out parser are dead code.
23. **Skill_RegionalAwareness.java:128 and 189, 197.** `roomColor` reads `phyStats()`, while `roomChar` reads `basePhyStats()` for the same stone and underwater checks. The two can disagree.
24. **Skill_ScrollCopy.java:136.** A new Teacher mob is built on every call. Nothing destroys it (unverified).
25. **Skill_Lassoing.java:84-86.** `LASSO_MATERIALS` is declared and never used.
26. **Skill_PrisonerTransfer.java:63-68, 67, 344; Skill_PrisonAssignment.java:61-66, 65, 290.** Empty `finalize()` overrides and commented-out global-handler registrations are cruft.
27. **Skill_RustingStrike.java:97-100.** `executeMsg` is an override that only calls super.
28. **Skill_MountedCombat.java:102.** `getXLEVELLevel(invoker())` runs without a null check on `invoker()` (unverified).

## Takeaways for a Python SUD

**Borrow.**

- **Timed research as a task object.** Keep progress in an object advanced once per game tick, with a schedule for messages, abort rules, and a total time derived from difficulty and tools:

  ```python
  @dataclass
  class Research:
      topic: str
      ticks_left: int = 10
      found: list[str] = field(default_factory=list)

  def research_ticks(item_level: int, skill: int, books: int) -> int:
      return max(0, 2 * item_level - skill - books)
  ```

- **Region maps from paths.** Use BFS or Dijkstra from the player to the target area, count area transitions, average the direction per pair, quantize to eight compass points with `atan2`, and lay the areas out on a grid. This gives a map of unseen places from the world graph, with no hand layout.
- **The proficiency roll, with its gate.** Keep the CoffeeMud shape of the check, because it gives both a skill curve and a difficulty gate:

  ```python
  def check(prof: int, expertise: int, adj: int, roll: int) -> bool:
      chance = max(5, min(95, prof + 2 * expertise)) + expertise // 2
      if adj >= 0:
          chance += adj
      elif roll > 100 + adj:
          return False
      return roll < chance
  ```

- **Expertise-tiered lore.** Store facts in a table keyed by a category and the minimum expertise that unlocks each one. Pick one at random per request, and make the pool grow with skill.
- **Veto hooks.** Give abilities a `before_action(event) -> bool` method that can cancel an incoming action (parry, untie), and an `after_action` that follows it. Parry is a clean first example.
- **Effects on other objects.** Let a rope or scroll carry a short effect record, with the casting character's ID, so untie, revocation and identification all work on the object. Keep the caster reference, as `invoker()` does, so revoke can target exactly one caster's effects.
- **Warrants as a state machine.** Model a crime as an enum of states (seeking, moving to trial, jailing, moving to jail, released) with the officer and the jail cell as fields. A bribe changes which cell a warrant points to.
- **Non-lethal harm.** The one-point cap on the grip attack is a useful rule for a single-player game. Harm should change state without ending the NPC.

**Simplify.**

- Merge the three research skills into one `Research` task, with the target as a parameter (item, area, or lore subject).
- Merge the two prison skills into one bribe type with a cell parameter.
- Replace per-ability clamp and rounding code with one check function, as above.
- Replace the armor-bit range hack with an explicit `max_range` field on weapons.

**Drop or rethink for single player.**

- Morse relay and naval stance AI assume other humans or NPC crews as players. Keep the signalling idea as room events for NPCs, and keep ship AI only if the game has ships.
- Recall with followers is a companion system. Model it as companions following the player.
- The torture-type skills (lobotomy, mind suck, grip attack, rust strike with lockjaw) are violent and sexual-adjacent content. Decide explicitly whether they belong in the game, and if they do, keep the state-change model and drop the detailed flavour text.
- The copy-on-invoke `Teacher` mob and the `finalize()` overrides should not be copied.

## Coverage

- **Read in full (all 36):** every `Skill_*.java` file in the manifest, from `Skill_ResearchRegionMap.java` through `Skill_LongBreath.java`, read without truncation.
- **Read for verification (not in the manifest):** `Common/interfaces/Law.java:692-717`; `Items/MiscMagic/StdWand.java:225-275`; `Items/interfaces/Wand.java:41-69`; `Abilities/StdAbility.java:1441-1485` and `:2341-2349`; `core/CMath.java:493-496`; `core/CMStrings.java:2002-2009`; `Locales/StdRoom.java:1662-1670`; `core/collections/LimitedTreeSet.java:43-66` and `:107-119`; `core/CMProps.java:681-682` and `:3539-3541`; `resources/skills/planesofexistence.txt` (header and entry count); `resources/help/skill_help.ini:3562-3566` and `:4073-4099`.
- **Located by grep only, not read:** `Skill_Stonecunning`, `Skill_DeepBreath`, `Skill_Buck`, `Skill_HandCuff`, `Thief_Bind`, `Prisoner`, `InvisibleInk`, `Disease_Leeches`, `Disease_Lockjaw`, and the rest of `StdPlanarAbility`.
- **Skimmed:** none.
- **Skipped:** none within the bucket.
