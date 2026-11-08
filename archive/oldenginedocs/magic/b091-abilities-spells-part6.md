# Spells, part 6: illusions, planar magic, mass effects and globes

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b091` (Abilities/Spells [part 6/9: Spell_MajorGlobe .. Spell_PlanarDistortion]) · 44 files · 9066 lines of Java.

## Summary

The 44 classes in this bucket are all spells: each extends `Spell` (`com/planet_ink/coffee_mud/Abilities/Spells/Spell.java:36`), which extends `StdAbility`. They fall into a handful of mechanical families. Message interceptors veto or rewrite game messages (the globes, Mirror Image, Obscure Self, Nightmare, Mind Block, Resist Divination). Summons create GenMob "illusions" that follow the caster or fight for them (Minor Image, Phantasm, Phantom Hound). Room and plane rewriters change what a room looks like or what can enter it (Planar Bubble, Planar Distortion, Mirage, Planar Block, Planar Burst, Planar Banish, Marker Portal, Marker Summoning). Many "Mass" spells are thin loops that apply a single-target spell to each group member or room occupant. The most borrowable ideas are the two-phase message hook, the budget-style absorbers and drains, and planes defined as data in a text file.

## Key files and types

File paths are relative to the CoffeeMud folder. The 44 bucket files are all under `com/planet_ink/coffee_mud/Abilities/Spells/`; the catalogue below gives them by class name.

| file or class | role | lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell.java` | Family base. ID `Spell`, `ACODE_SPELL`, triggers `CAST`/`CA`/`C`; `invoke()` adds the armour and concentration check; overrides `maliciousAffect` for the Magepox rule | 162 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` | Engine base. `proficiencyCheck` (1441), `adjustedLevel` (899), `properTargets` (2113), tick-duration rules (2174-2330), `maliciousAffect` (2185), `beneficialAffect` (2341), `tick` (2843) | read in part |
| `com/planet_ink/coffee_mud/Libraries/MUDFight.java` | `allPossibleCombatants` (141), `properTargets` (163), `postDamage` (819) | read in part |
| `com/planet_ink/coffee_mud/Libraries/Dice.java` | `roll(number, die, modifier)` (398), `rollPercentage` (409) | read in part |
| `com/planet_ink/coffee_mud/core/threads/StdTickClient.java` | Tick dispatch; a `false` from `tick()` ends that client (153-158) | read in part |
| `com/planet_ink/coffee_mud/core/CMProps.java` | `TIME_TICK = 4000` (681); `getTicksPerMudHour()` (3539) | grep |
| `com/planet_ink/coffee_mud/Abilities/StdPlanarAbility.java` | Plane engine (planes, room colouring, planar mobs). Outside this bucket | 2235 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/PlanarAbility.java` | Interface the plane spells use | 341 |
| `resources/skills/planesofexistence.txt` | Plane definitions, read by the plane engine | data |
| `resources/randareas/example.xml` | Quest template that `Spell_MysticTask` fills in | data |
| `resources/help/spell_help.ini` | In-game help for these spells (entries around lines 2009-2331) | data |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_MirrorImage.java` | Interceptor with a random-target bug | 239 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_Meld.java` | Largest file: fuses two items | 467 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_MinorImage.java` | Simulacrum with copied appearance | 442 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_PlanarBubble.java` | Room rewrite with look interception and content cache | 428 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_PlanarDistortion.java` | Per-victim room rewrite; duplicates Bubble | 423 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_MysticLoom.java` | Crafting automaton using `Textiling` | 362 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_MysticTask.java` | Generates a quest for a player | 309 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_PhantomHound.java` | Combat-only illusory beast with a disbelief timer | 275 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_Nightmare.java` | Action-blocking effect with a per-action budget | 272 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_ObscureSelf.java` | Message rewriter (hides identity) | 224 |
| `com/planet_ink/coffee_mud/Abilities/Spells/Spell_MarkerPortal.java` | Two-way gate exit to a summoned marker | 212 |

Every other file in the bucket is listed with its key numbers in "Content worth mining".

## How it works

### 1. Cast lifecycle

Every spell follows the same path, and the family code only varies the middle.

1. `Spell.invoke()` (`Spell.java:154-161`) calls `StdAbility.invoke()` for mana and checks, then `spellArmorCheck` (`Spell.java:132-151`). For a non-auto cast by a player, a 50% roll (`rollPercentage()<50`) makes non-cloth armour "absorb" the magic, and `CMLib.flags().canConcentrate` must hold.
2. Each spell does its own target check and proficiency check (`proficiencyCheck`, `StdAbility.java:1441`).
3. The spell builds a `CMMsg` (`CMClass.getMsg(mob,target,this,somaticCastCode(...),text)`) and asks `mob.location().okMessage` for permission. On success it calls `send`.
4. The effect is created from the template by `beneficialAffect` (`StdAbility.java:2341-2348`) or `maliciousAffect` (`2185-2208`). Both call `setInvoker(mob)`, copy the template with `copyOf()`, set `canBeUninvoked=true`, and call `startTickDown(invoker, target, ticks)`.
5. On failure the spell calls a fizzle helper (`beneficialWordsFizzle`, `beneficialVisualFizzle`, `maliciousFizzle`, `StdAbility.java:2210-2255`) that sends a message and returns false.

Two details matter for porting. First, the template object is never the live effect. Second, many "Mass" and summon spells build a fresh instance of another spell class (`new Spell_Hold()`, `new Spell_Haste()`, and so on) and call `beneficialAffect` or `maliciousAffect` on it, so one spell reuses another's effect class.

### 2. Durations and tick units

Effects count down on `TICKID_MOB` ticks only (`StdAbility.java:2843-2860`). Each tick is 4000 ms by default (`CMProps.java:681`). A `tickDown` below zero means the effect never expires on its own.

The duration argument has three meanings, decided in `getMaliciousTickdownTime` (`StdAbility.java:2174-2183`) and `getBeneficialTickdownTime` (`2313-2330`):

- **Positive:** an absolute tick count. `if(tickAdjustmentFromStandard>0) return tickAdjustmentFromStandard;` (2176 and 2314).
- **Zero:** the default. Beneficial default is `casterLevel*4+40` (`2325`). Malicious default is `round(adjustedLevel*1.1)+25`, adjusted by `adjustMaliciousTickdownTime` (`2178`).
- **Negative:** a percentage of the default, computed as `-adj/100 * defaultTicks` (`2182`).

Beneficial spells cast on someone else cap the caster level at the target's level plus `EXPRATE` (`2316-2322`), so a high-level caster cannot buff a low-level ally past a fixed gap.

Durations used in this bucket, as written:

| Spell | Ticks | Source |
|---|---|---|
| Minor Image | `10 + adjustedLevel/6` | `Spell_MinorImage.java:70-73, 217` |
| Mirror Image | default beneficial | `Spell_MirrorImage.java:228` |
| Phantasm | default beneficial | `Spell_Phantasm.java:227` |
| Phantom Hound | default beneficial, plus its own disbelief timer | `Spell_PhantomHound.java:266` |
| Nightmare | 10 (absolute, `maliciousAffect(...,10,-1)`) | `Spell_Nightmare.java:261` |
| Pass Door (auto) | 5 | `Spell_PassDoor.java:215` |
| Marker Portal | 5 | `Spell_MarkerPortal.java:203` |
| Planar Bubble (non-owner) | 3 | `Spell_PlanarBubble.java:413` |
| Planar Block | `(12 + adjustedLevel) * TicksPerMudHour` | `Spell_PlanarBlock.java:186` |
| Mass Hold | `7 - levelDiff`, so 1 to 7 | `Spell_MassHold.java:134-138` |
| Mass Sleep, Mass Slow | 2 | `Spell_MassSleep.java:134`, `Spell_MassSlow.java:132` |
| Mystic Loom | `75 + adjustedLevel + 30*XTIME` | `Spell_MysticLoom.java:353` |
| Mystic Task | 2 hours (`getTicksPerHour()*2`) | `Spell_MysticTask.java:299` |
| Mana Burn | `levelDiff` percent of the malicious default (see Problems) | `Spell_ManaBurn.java:172` |

### 3. Resolution: proficiency, saves and targeting

`proficiencyCheck` (`StdAbility.java:1441-1485`) starts from `proficiency()`, adds `2*XLEVEL` and `XLEVEL/2`, and clamps to 5-95 unless `DIS955RULE` is set. A positive `adjustment` is added to the chance. A negative `adjustment` first rolls: the spell fails outright if `rollPercentage() > 100+adjustment` (1480-1484), so an adjustment of -20 is a 20% automatic failure before the normal roll.

Each spell sets its own adjustment from the context. Examples: Mana Burn uses `-(target INT + levelDiff*5)` (`Spell_ManaBurn.java:158`); Mend uses `((casterLevel + 2*XLEVEL) - itemLevel) * 5` (`Spell_Mend.java:90`); Marker Portal sums the level gap of everyone in the destination room, times 3 for monsters, plus 20 per item, and subtracts that (`Spell_MarkerPortal.java:151-168`); Mass Sleep and Mass Hold use a flat -20 (`Spell_MassSleep.java:111`, `Spell_MassHold.java:109`).

Targeting goes through `StdAbility.properTargets` (`2113-2123`) and then `MUDFight.properTargets` (`163-190`):

- Beneficial (non-malicious) abilities take the caster's group members in the same room (`MUDFight.java:166-177`).
- Malicious abilities take the room's current combatants if the caster is fighting (`180-181`). Otherwise they take `allPossibleCombatants(caster, includePlayers)` (`183`). That function (`141-160`) lets through only monsters unless `includePlayers` is set or the caster is a monster (`156`).
- The `auto` argument is passed as `includePlayers`. A player who casts a malicious area spell by hand therefore hits monsters only, not other players, unless they are already in combat with the caster.

Saves: `maliciousAffect` takes an `additionAffectCheckCode`. If it is `-1`, no save is run and the effect always attaches (`StdAbility.java:2190`). Otherwise a `CMMsg` with that code goes through `okMessage`/`send`, and a `msg.value()>0` means the target resisted and the method returns null (`2190-2199`). Mass Sleep passes a mind-type code (`Spell_MassSleep.java:134`) so it gets a save; Mass Hold passes `-1` (`Spell_MassHold.java:138`) so it does not.

### 4. Message interceptors

Most of the cleverness in this bucket is in `okMessage` (veto) and `executeMsg` (after-the-fact) overrides on the effect object. An effect attached to a MOB or room sees every message in that place and can change or cancel it. The two-phase flow is described in the brief; these spells use it as follows.

**Mirror Image** (`Spell_MirrorImage.java:83-113, 116-176`). Each image absorbs one melee attack (`TYP_WEAPONATTACK`) directed at the caster. The attacker's intelligence matters:

- `intAdjustment = (attacker maxINT - defender INT)/2`, at least 1 (`99-102`).
- `numberOfTargets = numberOfImages + intAdjustment` (`104`).
- The attack hits an image when `randomizer.nextInt() % numberOfTargets >= intAdjustment` (`105`). See Problems: the sign of `nextInt() % n` makes this about half as likely as intended.

When the caster acts visibly (eyes, move, mouth or hands, `130-133`), `executeMsg` appends one trailer copy of the action per image (`142-143`), so the room sees mirrored actions. Looking at the caster adds one descriptive line per image (`153-172`). A `notAgain` flag stops the recursive trailer from repeating itself (`123-124`, `175`). Images also give -1 armour per image plus `XLEVEL` (`179-184`). The image count is `roll(1, round(adjustedLevel/3), 2)` (`221`), so 3 up to about `adjustedLevel/3 + 2` images.

**Obscure Self** (`Spell_ObscureSelf.java:91-173`). A table of eight placeholder pairs maps the placeholders in other people's messages (`<S-NAME>`, `<S-HIS-HER>` and so on) to neutral words such as "someone" and "he or she" (`78-88`). The effect rewrites the bearer's name and pronouns in messages from others (`114-139`) and in the caster's own messages where the bearer is not the target (`141-169`). It calls `msg.modify(...)` to store the change (`171`). Looking at the bearer, or reading them, gets the message "He or she is too vague to make out any details." and the look is vetoed (`105-112`).

**Nightmare** (`Spell_Nightmare.java:93-211`). This effect vetoes every hands or movement action, and recall (`105-110`). Each veto shows a random one-of-ten flavour line to the victim and another to the room, all of them grim (paraphrased, not quoted here; `119-168`). Then the nightmare budget is charged: `amountRemaining -= round(INT*2.5)` (`203`). The budget starts at `round(100 * target speed)` (`259-260`). When it goes negative the effect ends (`204-205`). With INT 10 that is about five blocked actions at normal speed. A throttle, `notAgainThisRound`, allows one set of flavour lines per MOB tick (`112`, `169`, `231-232`). Flags are `MINDALTERING|FEARING` (`85-88`).

**Mind Block** (`Spell_MindBlock.java:98-125`). The target is protected from any message whose `targetMinor` is `TYP_MIND`, or whose tool is an ability in the illusion or enchantment domain (`108-117`). If the target has learned Mind Block as a skill, a proficiency check decides each time (`106`). Otherwise it always blocks.

**Resist Divination** (`Spell_Nondetection.java:93-108`). Blocks any divination-domain tool from another source (`99-102`). The same learned-skill rule applies (`105`), and at proficiency above 99 it always blocks.

**Globes** (`Spell_MajorGlobe.java:98-121`, `Spell_MinorGlobe.java:98-120`). Hostile spells and prayers aimed at the bearer are absorbed if their lowest qualifying level is 15 or less (greater globe) or 8 or less (globe). Each absorbed spell adds its level to `amountAbsorbed` (`114`). The globe breaks when the total exceeds a budget, checked on each later message (`118-119`):

- Greater: `(invoker level + 2*XLEVEL) * 4` (`118`).
- Minor: `(invoker level + XLEVEL) * 2` (`118`).

The message: "The globe around <S-NAME> absorbs the ... from <T-NAME>!" (`115`).

**Planar Block** (`Spell_PlanarBlock.java:81-108`). Only teleport or summon arrivals are checked. The message's tool must be a planar ability with the summoning or transporting flag, and its destination room must lie in a forbidden plane (`88-105`). Forbidden planes are a comma list kept in lower case (`137-151`). The message is "A powerful blocking geas prevents the teleporting magic from working." (`102`).

**Phantasm** (`Spell_Phantasm.java:117-155`). An attack aimed at the phantasm's own body is turned into a quit message, so the phantasm vanishes as the attack passes through it (`152-153`). A spell aimed at it is refused with "... seems strangely unaffected by your magic." (`127-130`).

**Minor Image** (`Spell_MinorImage.java:142-187`). The image cannot act unless the action is forced (`150-153`). A malicious action against it ends the image (`158-159`). Several hostile message types, including damage, weapon attacks, spells and elemental damage, also end it (`165-183`).

**Mind Light** and **Mirage** are covered in section 6.

### 5. Illusions and summons

Three spells create a GenMob with `CMClass.getMOB("GenMob")`, copy or set its stats, add tattoos, and bring it to life.

**Minor Image** (`Spell_MinorImage.java:372-440`). The image copies the target's name, race, gender and description, sets race to `Spirit`, and uses a neutral gender (`375-388`). Its tattoos are `SYSTEM_SUMMONED` and `SUMMONED_BY:<caster>` (`391-392`). It can copy visible equipment and tattoos only when `canSeeAppearance()` is true, which it is not in this spell (`75-78`, `405-435`). The image's `affectPhyStats` and `affectCharStats` override the target's displayed name, weight and height, race, and sets WIS and INT to 1 (`117-140`). Its unInvoke removes the GenMob (`98-115`).

**Phantasm** (`Spell_Phantasm.java:170-279`). The caster names a race, or the spell picks one at random from the theme races (`177-194`). The summon is a "ferocious" monster: level `caster level + 2*XLEVEL`, weight 850, STR/DEX/CON 25, Fighter class, evil alignment (`221`, `245-251`). It becomes a follower (`CMLib.commands().postFollow`, `223`). Its tick checks whether its victim has changed. If it has no victim or a different one, it wanders off and is destroyed (`91-113`, message "... look(s) around for someone to fight...", `109`).

**Phantom Hound** (`Spell_PhantomHound.java:85-274`). The hound can only be summoned in combat (`200-204`). It has 9 times base HP, +100 attack, -20 armour, damage 75, saving throws of 200, and the Dog race (`228-245`). It takes the caster's enemy as its own target (`259-264`). Its timer starts at `100 + 5*adjustedLevel` points (`265`). Each tick it loses the victim's INT, the victim's level, and a quarter of any damage it has taken (`127-132`). It vanishes when the points go below zero, with the message "... disbelieve(s) <T-NAME>, who vanishes!" (`133-141`). It also vanishes when it is no longer in combat or its victim changes (`117-125`).

The hound's own damage is zeroed (`186-191`). The damage text is first rewritten using the un-zeroed value, so the messages describe large damage while the real value is zero. The help text says so: "all damage is illusionary" (`resources/help/spell_help.ini`, Phantom Hound entry).

Every tick the hound also strips all other effects from itself (`98-116`), so a spell cast on the hound does not last.

### 6. Room and plane rewriting

**Planes.** Plane definitions come from `resources/skills/planesofexistence.txt`, read through `getAllPlanesMap()`. `StdPlanarAbility.getAllPlaneKeys()` returns the keys (`StdPlanarAbility.java:1528-1535`). Each plane has variables such as an atmosphere, opposed planes, categories and faction and behaviour lists, and room colouring is applied by `doPlanarRoomColoring`. These spells use only the plane name and a few of its variables.

**Planar Bubble** (`Spell_PlanarBubble.java:110-342`). The room is rewritten into a copy built from the chosen plane. `room()` (`139-188`) makes a new room of the same ID and copies its description, doors and exits. It then applies the plane's colouring and atmosphere (the atmosphere is set on the real room, `161-166`, and restored on unInvoke, `124-125`). It also takes the plane's area effects and behaviours, and adds `Prop_AbsorbDamage` if the plane sets an absorb value (`178-184`).

The look interception is the interesting part (`212-342`). On a LOOK or EXAMINE at the room, the spell computes a content hash over the room's items and creatures (`233-256`), and rebuilds its planar copy only if the hash changed (`257`). Wrappers (`StdItemWrapper`, `StdContainerWrapper`, `StdPortalWrapper`, `StdRideableWrapper`, `StdMobWrapper`) stand in for the real objects in the copy (`265-294`). The look is then re-sent into the copy (`326-333`), and the real look is vetoed. The hash includes the viewer's `hashCode()` (`235`), so each new viewer forces a rebuild.

The bubble lasts 3 ticks for a caster who does not own the land (`413`). A land owner gets a malicious, default-length effect (`410-411`).

**Planar Distortion** (`Spell_PlanarDistortion.java:111-353`). The same idea, but only the target's own view is changed. The effect runs when the victim looks at the room (`214-228`). Planar mobs are copied (`287-292`) and other mobs are wrapped (`296-303`). Its atmosphere and area-effect branches are empty (see Problems).

**Mirage** (`Spell_Mirage.java:84-195`). The room's LOOK or EXAMINE is answered from another room in the same area, `newRoom` (`100-113`, `116-135`). The redirect is guarded by `room().fetchEffect(ID())==null`, so two mirages cannot chain. The destination is either a random proper room (`178`) or the stored room ID. For a land owner the effect is persistent (`174-185`, with `CMLib.database().DBUpdateRoom`), and otherwise it lasts the default beneficial duration (`186-187`). The area must have at least two proper rooms (`154`).

**Planar Block** is in section 4.

**Planar Burst** (`Spell_PlanarBurst.java:35-225`). Requires the caster to be on a plane, in a `SubArea` with a `PlanarAbility` effect (`101-116`, `150-164`). It has a range of 0 to 10 (`52-62`). The damage is `round(1.2 * max HP)` (`200`). It is cut to 30% if the target is more than half the caster's level (`202-204`), divided by four on a save (`206-207`), and at least 1 (`209-210`). The hit goes through `postDamage` with `TYP_CAST_SPELL` and `TYPE_BURSTING` (`214`).

**Planar Banish** (`Spell_PlanarBanish.java:102-173`). Requires the caster on a plane and the target not on one (`108-121`). For each group member in the room who is not already on a plane, the spell flees them from combat, halves their movement (`145-148`), applies `Chant_EelShock` for 3 ticks (`149-151`), and moves them to random prime-material rooms of the super area (`153-163`). The relocation loop has no `break`, so the target is moved up to 20 times and gets a look command queued each time (see Problems).

**Marker Portal** (`Spell_MarkerPortal.java:101-211`) and **Marker Summoning** (`Spell_MarkerSummoning.java:77-183`) work as a pair. Both look for a room where the caster has a `Spell_SummonMarker` effect (`117`, `93`), a spell in another bucket that the Conjurer class grants (`CharClasses/Conjurer.java:73`).

- Marker Portal makes a two-way `GATE` exit between the current room and the marker room. Each side gets a `GenExit` with a `Prop_RoomView` that shows the other room (`180-202`). It lasts 5 ticks (`203`). The cost is the level gap of the marker room's occupants, multiplied by 3 for monsters, plus 20 per item (`151-163`).
- Marker Summoning pulls every creature and item from the marker room into the caster's room (`145-174`). Each creature is calmed (`152`) and sees "appear(s) in a burst of light" (`148`). The cost is the level gap, halved (`123-137`).

**Mind Light** (`Spell_MindLight.java:101-162`) is a room effect that should add mana to everyone in the room each tick, scaled by the caster's INT and WIS. It is covered in Problems.

### 7. Mass spells

Most "Mass" spells follow one template:

1. `properTargets(mob, givenTarget, auto)` or `properTargets(mob, givenTarget, false)`, which selects group members for beneficial spells and combatants for malicious ones (see section 3).
2. Super-invoke and roll proficiency, usually with a flat -20 for the group version.
3. Show one room message, then loop over the targets: `okMessage` on the per-target `CMMsg`, then `send`, then a new single-target effect (`new Spell_Haste()`, `Spell_Fly`, `Spell_Invisibility`, `Spell_WaterBreathing`, `Spell_FeatherFall`, `Spell_Hold`, `Spell_Sleep`, `Spell_Slow`) with `setProficiency(proficiency())`.

The catalogue shows which ones use the group and which the room. Mass Haste, Mass Fly, Mass Invisibility, Mass Waterbreath and Mass Featherfall apply the buff to group members. Mass Sleep, Mass Slow and Mass Hold apply the debuff to audible targets. Mass Disintegrate, Meteor Storm and Planar Burst hit the room's hostile targets.

For the debuffs, `CMLib.flags().canBeHeardSpeakingBy(mob, target)` must hold (`Spell_MassHold.java:121`, `Spell_MassSleep.java:123`, `Spell_MassSlow.java:121`). Mass Hold clears the caster's victim after each target so the group does not start a fight (`123-145`). Mass Feather Fall, Mass Haste and Mass Fly check group size and state in `castingQuality` (`Spell_MassFeatherfall.java:83-99`, `Spell_MassHaste.java:83-96`, `Spell_MassFly.java:83-96`).

Each target gets its own effect, but the return value is the last target's result. This is a small bug, described in Problems.

### 8. Items and crafting

**Meld** (`Spell_Meld.java:107-466`) fuses two items into one new item. The spell rejects other pairs (`199-203`).

- Two armours must share a clothing layer and layer attributes (`136-145`). Their worn slots must be one of the 14 adjacent pairs in `shinBone` (`147-160`), for example head and neck, or torso and legs. Otherwise the spell fails with "... aren't worn in compatible places".
- Two weapons must both fit in the held slot, and neither can be two-handed (`171-193`).
- Two containers merge (`195-197`).

The new item's properties are computed from the pair (`264-332`, `343-395`, `397-425`):

- Material: the one with the higher index in the `heiarchy` list (flesh, paper, cloth, leather, vegetation, wood, synthetic, metal, rock, precious, energy, gas, mithril), so metal beats cloth (`83-105`, `256-258`).
- Armour: weight is summed, armour is averaged, level is the maximum (`313`, `326-329`).
- Weapon: damage and attack adjustment are averaged, ranges are averaged (`344-388`).
- Container: capacity is summed, lock is OR-ed, and the key comes from the locked one (`414-417`).

The spell then copies every savable effect and behaviour from both parents onto the new item (`428-451`). It is in a worn state, so it gets full uses if it wears (`454-455`), and both originals are destroyed (`456-457`).

Failure is a fizzle, and both items survive (`461-462`).

**Mend** (`Spell_Mend.java:71-117`) restores one item's uses to 100. It refuses items that cannot wear or that weigh over 1000 (`76-85`), and does nothing if the item is already at 100 (`100-101`). The message is "Nothing happens to <item>." (`101`).

**Mystic Shine** (`Spell_MysticShine.java:85-141`) changes an item's disposition: it becomes a light source (`88`), and if it is in darkness the dark bit is cleared (`89-90`). Only metal and mithril items qualify (`113-119`).

**Permanency** (`Spell_Permanency.java:84-173`) makes one of the caster's own spells on a target permanent. Only spells whose lowest qualifying level is below 25 qualify (`108-121`). The caster must have at least 100 mana, and not be a monster (`90`). The price is paid in maximum mana: both current and maximum drop by 100 (`140-141`). Rooms and exits are saved to the database when owned (`143-162`).

**Mass Repairing Aura** (`Spell_MassRepairingAura.java:91-160`). Every `ceil(30 / adjustedLevel)` ticks (the counter drops by `adjustedLevel` each tick and resets to 30, `102-109`), every item in the affected set with wear-and-tear, fewer than 100 uses and weight 500 or less gains `1 + XLEVEL/3` uses (`102-129`). The set is recomputed each cycle by `getChoices` (`134-160`).

**Mystic Loom** (`Spell_MysticLoom.java:102-361`) is the largest mechanic in the bucket. The caster summons a stationary loom (a GenItem plus a factory MOB, `336-353`). Each mob tick, the loom does the following (`161-304`):

1. Collects every raw-material item in the rooms within `1 + XMAXRANGE` steps (`172-217`), including those carried by creatures. It records each item with its owner and container.
2. Tries up to ten random Textiling recipes (`225-254`). A recipe that succeeds leaves a `TICKDOWN` on the craft effect, shortened by `XTIME * 5%` with a floor of 5 ticks (`238-247`).
3. Returns each surviving original to its owner and container. Each destroyed original is replaced by one of the leftover "spare" items (`256-303`).

As I read it, the owners' materials are not consumed, only the spares. Unverified.

### 9. Damage and drain

**Meteor Storm** (`Spell_MeteorStorm.java:77-124`). Room-wide. Damage is `roll(maxDie, 6, maxDie/4)` with `maxDie = round(adjustedLevel/3)` (`109-110`), fire-typed and bashing (`114`). The message is "The meteors <DAMAGE> <T-NAME>!" (`114`). See Problems for the halving rule.

**Mass Disintegrate** (`Spell_MassDisintegrate.java:83-199`). Costs 200 mana (`77-80`). Range 2 (`53-56`). Each target is checked against the caster's level and a random roll. A target within the level window gets one of two results (`129-151`):

- A chance to be killed outright: damage is `HP * 100` if `rollPercentage() > levelDiff*successThreshold + failChance` (`140-143`).
- Otherwise half its current HP (`146`), and `failChance` rises by 5.

Targets outside the window take 1 damage (`152-153`). The room is then swept: unattended items and corpses are destroyed, except other players' corpses (`157-192`).

**Planar Burst** is in section 6.

**Mana Burn** (`Spell_ManaBurn.java:81-183`). Removes 50 mana from the target at once (`170`). Then it doubles mana losses while active. `adjustMana` (`101-114`) runs on every message and every tick. It subtracts any drop in mana from the target a second time. The help text says "continues to use up mana at roughly twice the normal rate" (`spell_help.ini`, Mana Burn). See Problems for the duration mapping.

**Mana Shield** (`Spell_ManaShield.java:104-126`). Halves incoming damage (protection 0.5). The mana cost equals the damage prevented, capped at current mana (`116-122`). Minor Mana Shield uses 0.25 (`Spell_MinManaShield.java:61-63`). Only one shield of either kind can be active (`135-145`).

**Mind Fog** (`Spell_MindFog.java:85-90`) sets WIS to 1 and reduces the mind save by 25. It is a malicious spell with a default duration.

**Misstep** (`Spell_Misstep.java:79-83`) sets DEX to 1.

**Mute** (`Spell_Mute.java:79-83`) sets `PhyStats.CAN_NOT_SPEAK`.

### 10. Quest generation

**Mystic Task** (`Spell_MysticTask.java:174-257`) is the most unusual spell here. It asks the quest engine to build a quest from `resources/randareas/example.xml` for a player target (`266-270`).

- It builds a `definedIDs` table: area name, target level, aggression, and template `auto` (`284-290`). The quest criteria default to a zapper-style mask naming the target (`191-192`).
- Duration and expiry are one hour of ticks (`193-194`). The quest is multi-area (`195`).
- Up to five attempts are made (`177-178`). Each attempt runs the percolator, checks requirements, rewrites two script lines (`223-224`), and starts the quest (`225-241`).
- The spell records the quest name in its misc text as JSON (`297-301`). On each tick it checks whether the target won (`129-136`).
- On expiry or failure it prints "You have failed your task." (`108`, `112`). On success it prints "You have successfully completed your task." (`140`).

### 11. Other status and movement effects

**Pass Door** (`Spell_PassDoor.java:98-246`). The caster walks through a closed exit (`Spell_PassDoor.java:186-190` refuses open ones). A level-based penalty applies (`192-197`): the highest level of monsters, items or the land owner in the caster's room and the target room counts, and the adjustment is (caster level minus that) * 5, capped at zero. While walking, a non-savable copy of the spell gives invisibility and translucency (`91-96`, `225-242`). Overall it costs all mana (`66-70`).

**Natural Communion** (`Spell_NaturalCommunion.java:82-159`). Lists the terrain, the resource (via `CMLib.materials().makeResource`), and any plant or animal-intelligence creatures in the room and in a radius of `3 + XLEVEL + XMAXRANGE` (`144`). Indoors is a -25 penalty (`134-136`). The result is announced as "Your surroundings show the following natural signs: ..." (`150-151`).

## Content worth mining

All 44 spells, with key numbers. Files are `com/planet_ink/coffee_mud/Abilities/Spells/<Class>.java`. Quality is the spell's `abstractQuality()`; "others" means the target is a different person.

| Class | Display name | Quality | Domain | Lines | What it does |
|---|---|---|---|---|---|
| Spell_MajorGlobe | Greater Globe | Beneficial, others | Abjuration | 150 | Absorbs hostile spells up to level 15 within a budget of `(lvl+2X)*4` |
| Spell_MinorGlobe | Globe | Beneficial, others | Abjuration | 150 | Absorbs up to level 8 within a budget of `(lvl+X)*2` |
| Spell_MeteorStorm | Meteor Storm | Malicious | Conjuration | 125 | Room-wide fire damage, `maxDie d6 + maxDie/4` |
| Spell_Mend | Lesser Mend | Indifferent | Alteration | 118 | Refills one item's uses to 100 |
| Spell_Meld | Meld | Indifferent | Alteration | 467 | Fuses two items into one |
| Spell_MinorImage | Minor Image | Indifferent | Illusion | 442 | Self-simulacrum that copies name, race and gender |
| Spell_PlanarBubble | Planar Bubble | Malicious | Alteration | 428 | Rewrites the room into a chosen plane; look is intercepted |
| Spell_PlanarDistortion | Planar Distortion | Malicious | Illusion | 423 | As Bubble, but only the victim's view |
| Spell_MysticLoom | Mystic Loom | Ok, self | Evocation | 362 | Crafting automaton using Textiling |
| Spell_MysticTask | Mystic Task | Indifferent | Enchantment | 309 | Generates a 1-hour quest for a player |
| Spell_Phantasm | Phantasm | Beneficial, self | Illusion | 280 | Summons a "ferocious" race copy as follower |
| Spell_PhantomHound | Phantom Hound | Malicious | Illusion | 275 | Combat-only illusory beast with a disbelief timer |
| Spell_Nightmare | Nightmare | Malicious | Illusion | 272 | Blocks hands and movement; each block drains a budget |
| Spell_PassDoor | Pass Door | Indifferent | Conjuration | 247 | Walks through closed exits while invisible; costs all mana |
| Spell_MirrorImage | Mirror Image | Beneficial, self | Illusion | 239 | Absorbs melee attacks; copies visible actions |
| Spell_MassRepairingAura | Mass Repairing Aura | Indifferent | Abjuration | 229 | Repairs wear-and-tear items every ceil(30/level) ticks; 100 mana |
| Spell_PlanarBurst | Planar Burst | Malicious | Evocation | 226 | Only on a plane; `1.2 x max HP` burst |
| Spell_ObscureSelf | Obscure Self | Ok, self | Illusion | 224 | Hides the bearer's name and pronouns from others |
| Spell_MarkerPortal | Marker Portal | Indifferent | Conjuration | 212 | Two-way gate to the summoned marker; 5 ticks |
| Spell_PlanarBlock | Planar Block | Malicious | Cosmology | 206 | Bars teleport or summon into the caster's plane |
| Spell_MassDisintegrate | Mass Disintegrate | Malicious | Evocation | 200 | Room-wide level-gated kill chance; 200 mana |
| Spell_Mirage | Mirage | Malicious | Illusion | 196 | Room looks like another room in the area |
| Spell_ManaBurn | Mana Burn | Malicious | Enchantment | 184 | Removes 50 mana; double drain while active |
| Spell_MarkerSummoning | Marker Summoning | Indifferent | Conjuration | 184 | Pulls the marker room's creatures and items to the caster |
| Spell_Permanency | Permanency | Indifferent | Enchantment | 174 | Makes a low-level spell permanent; costs 100 max mana |
| Spell_PlanarBanish | Planar Banish | Malicious | Cosmology | 174 | Sends the group to random prime-material rooms |
| Spell_ManaShield | Mana Shield | Beneficial, self | Abjuration | 168 | Halves damage; mana cost equals damage prevented |
| Spell_MinManaShield | Minor Mana Shield | Beneficial, self | Abjuration | 72 | Mana shield at 0.25 |
| Spell_MindLight | Mind Light | Indifferent | Enchantment | 163 | Room effect meant to add mana; see Problems |
| Spell_NaturalCommunion | Natural Communion | Indifferent | Divination | 160 | Lists terrain, resources and wildlife nearby |
| Spell_MassHold | Mass Hold | Malicious | Enchantment | 158 | Applies Hold to the room; 1 to 7 ticks |
| Spell_MindBlock | Mind Block | Beneficial, others | Abjuration | 154 | Blocks mind-type and illusion or enchantment actions |
| Spell_MassSleep | Mass Sleep | Malicious | Enchantment | 153 | Applies Sleep to audible targets; 2 ticks; save check |
| Spell_MassSlow | Mass Slow | Malicious | Alteration | 151 | Applies Slow; 2 ticks; save check |
| Spell_Mute | Mute | Malicious | Transmutation | 144 | Sets CAN_NOT_SPEAK |
| Spell_MassFeatherfall | Mass FeatherFall | Ok, others | Alteration | 141 | Applies Feather Fall to group members in the room (cast only if one is falling) |
| Spell_MassHaste | Mass Haste | Beneficial, others | Alteration | 141 | Applies Haste to group members |
| Spell_MysticShine | Mystic Shine | Indifferent | Alteration | 141 | Makes metal items into light sources |
| Spell_MassFly | Mass Fly | Beneficial, others | Transmutation | 140 | Applies Fly to group members |
| Spell_MassWaterbreath | Mass Waterbreath | Ok, others | Transmutation | 140 | Applies Waterbreathing to group members |
| Spell_MassInvisibility | Mass Invisibility | Beneficial, others | Illusion | 139 | Applies Invisibility to group members |
| Spell_MindFog | Mind Fog | Malicious | Enchantment | 139 | WIS to 1; mind saves -25 |
| Spell_Nondetection | Resist Divination | Beneficial, others | Abjuration | 136 | Blocks divination spells from others |
| Spell_Misstep | Misstep | Malicious | Transmutation | 130 | DEX to 1 |

Useful flavour lines, paraphrased or short:

- Mirror Image attack: "<S-NAME> attack(s) a mirrored image!" (`Spell_MirrorImage.java:107`). Fade: "Your mirror images fade away." (`198`).
- Obscure Self: look is refused with "He or she is too vague to make out any details." (`Spell_ObscureSelf.java:110`).
- Planar Block: "A powerful blocking geas prevents the teleporting magic from working." (`Spell_PlanarBlock.java:102`).
- Planar Distortion: "The fundamental nature of this place seems to change..." (`Spell_PlanarDistortion.java:407`), and "... seems to return to normal." (`120`).
- Mind Fog: "... begin(s) to feel a bit fogged in the head." (`Spell_MindFog.java:128`).
- Pass Door: "... shimmer(s) and pass(es) <direction>." (`Spell_PassDoor.java:221`).
- Permanency: "The quality of <spell> inside <T-NAME> glows!" (`Spell_Permanency.java:163`).
- Natural Communion: "Your surroundings show the following natural signs: ..." (`Spell_NaturalCommunion.java:150`).
- Nightmare: victims are struck by a set of graphic and grim lines, which the spell pulls at random. Not quoted here.

Obscure Self's eight placeholder pairs (`Spell_ObscureSelf.java:78-88`) are a reusable table for any game that wants to hide identity in messages: name, reflexive, object and possessive forms, each with a neutral substitute.

Plane definitions are data in `resources/skills/planesofexistence.txt` (34 KB). Each plane has variables such as atmosphere, category and opposed planes, and the spells pick one at random. This is a ready source of setting content.

## Notable design ideas

- **Two-phase message hooks as the mechanism for every effect.** Veto (`okMessage`) and after-the-fact (`executeMsg`) handlers on the effect itself, not on the caster. Nightmare, Obscure Self, Mirror Image and the globes all work this way (`Spell_Nightmare.java:93`, `Spell_ObscureSelf.java:91`, `Spell_MajorGlobe.java:98`).
- **Budgets rather than counters.** A globe absorbs spells up to a level budget, and a nightmare runs on a budget of `100 * speed`, drained by `INT*2.5` per blocked action. The effect ends when the budget runs out.
- **Damage as illusion.** Phantom Hound's damage text is computed from the real value, and then the value is zeroed (`Spell_PhantomHound.java:185-191`). The victim sees a big hit and takes none.
- **Permanency as a cost in max mana.** A spell made permanent costs 100 maximum mana for good (`Spell_Permanency.java:140-141`). It is an upkeep that a player can never recover.
- **Mana Shield as one-to-one absorption.** Each point of damage prevented costs one point of mana (`Spell_ManaShield.java:116-122`). It fits a text game because the trade is easy to read.
- **Look redirection with content cache.** Mirage and Planar Bubble intercept look messages and answer them from another room or a rebuilt copy (`Spell_PlanarBubble.java:233-333`). The cache is keyed on content and viewer.
- **Mystic Loom reuses the crafting system.** It runs the ordinary Textiling recipes on nearby materials, with a per-cycle random retry and a spare-material swap to keep the owners' items safe (`Spell_MysticLoom.java:161-304`).
- **Mystic Task generates a quest from a template.** It fills an XML template with defined IDs, checks requirements, rewrites two script lines, and starts a quest (`Spell_MysticTask.java:174-257`).
- **Planar Block's fake invoker.** The spell builds a `StdMOB` at the caster's level plus 50 and sets it as the invoker when that level is higher (`Spell_PlanarBlock.java:187-195`). This keeps the curse's invoker strong. The exact effect on dispel or resist checks is unverified.
- **Marker Portal's one-way view.** Each end of the gate is a `GenExit` with a `Prop_RoomView` that shows the other room, so the portal is a window as well as a passage (`Spell_MarkerPortal.java:180-202`).

## Problems and cruft

1. **Mirror Image hits about half as often as intended.** `randomizer.nextInt() % numberOfTargets >= intAdjustment` (`Spell_MirrorImage.java:105`) takes the sign of the dividend. Half the random values are negative, and a negative remainder is never `>= intAdjustment` (which is at least 1). The roll that Dice itself uses to avoid this is `Math.abs(...)` (`Libraries/Dice.java:411`). The bug is confirmed by reading; its in-game size is unverified.
2. **Mind Light's mana loop never runs for a normal cast.** When `invoker()` is set, `tick` returns false before the mana loop (`Spell_MindLight.java:108-113`). The mana loop is at lines 114-124. `beneficialAffect` sets the invoker (`StdAbility.java:2343`). The help text says Mind Light gives mana "at an accelerated rate, so long as the caster remains" (`resources/help/spell_help.ini`, Mind Light). So the code and the documentation disagree.
3. **Planar Banish relocates the target up to 20 times.** The loop at `Spell_PlanarBanish.java:154-163` has no `break`. Each accessible room in the loop moves the target again and queues a look.
4. **Mass Disintegrate counts the caster's level twice.** `levelDiff = avgLevel - (mob.level + ppowerLevel)` (`Spell_MassDisintegrate.java:107-108`), where `ppowerLevel` already includes the caster's level. It also uses `EXPRATE`, a rate setting for experience, as the level window (`130`) and as a divisor (`119`). `tlevelDiff` is computed and never used (`126-128`).
5. **Meteor Storm's halving condition reads inverted.** `if(msg.value()<=0) damage = damage/2` (`Spell_MeteorStorm.java:111-112`). Planar Burst uses `msg.value()>0` for its save (`Spell_PlanarBurst.java:206-207`). As written, Meteor Storm halves damage on a normal hit. Verify before porting.
6. **Mana Burn's duration mapping is odd.** `tickAdjustmentFromStandard` is `-levelDiff` (`Spell_ManaBurn.java:172`). A negative value gives `|adj|/100 * default` (`StdAbility.java:2182`). A target one level above the caster gets 1% of the default duration, a few ticks at most. The duration rises again as the level gap widens. The double-drain itself matches the help text.
7. **Minor Image's worn-tattoo scan looks broken.** `getSeenTattoos` (`Spell_MinorImage.java:245-370`) clears and refills `wornHere` inside the loop that indexes it (`327-343`), and reuses `sets` across iterations. I could not establish what it returns. Unverified.
8. **Mass spells return the last target's result.** `success` is reassigned for each target (`Spell_MassHold.java:138`, `Spell_MassSleep.java:134`, `Spell_MassSlow.java:132`). The return value and the fizzle logic reflect only the last target.
9. **Planar Distortion has dead branches.** The atmosphere branch is empty (`Spell_PlanarDistortion.java:171-173`). `aeffects` is never filled (`176-178`). The cached `planarA` is never stored, so `getPlanarAbility()` builds a new one on each call (`133-135`). Most of its room-building code is copied from Planar Bubble.
10. **Empty catch and finally blocks.** `Spell_PlanarBubble.java:336-338` has an empty `finally`. `Spell_MarkerPortal.java:129-131` and `Spell_MarkerSummoning.java:105-107` swallow `NoSuchElementException` with nothing.
11. **Dead field.** `Spell_MindBlock.amountAbsorbed` is set (`144`) and never read. Its globe cousins read theirs.
12. **Mirage may dereference null.** `room()` can return null (`Spell_Mirage.java:110-112`), and `okMessage` calls `room().fetchEffect` without a check (`121`). Unverified: whether the area can lack a proper room at that point.
13. **Mystic Task prints the failure message in the wrong case.** `unInvoke` prints "You have failed your task." even when no quest was ever started (`Spell_MysticTask.java:111-112`).
14. **Mirror Image and the globes duplicate code.** The two globes are the same class with different constants. `Spell_MassWaterbreath.castingQuality` creates a `Spell_WaterBreathing` only to ask it a question (`Spell_MassWaterbreath.java:93`).
15. **Marker Portal may copy effects into the far exit.** `e2 = e.copyOf()` happens after `e` gets its own `Prop_RoomView` (`Spell_MarkerPortal.java:186-192`), so `e2` may carry both views. Unverified.
16. **Phantom Hound clears its own effects every tick.** Lines `98-116` remove all other effects from the hound on each MOB tick. This is probably intended to stop buffs on the hound, but it is blunt.
17. **Duplicated plane code.** Planar Bubble and Planar Distortion each contain about 100 lines of the same room-copy and wrapper logic.
18. **Multiplayer assumptions.** Group membership, `includePlayers`, the quit and logout hooks (`Spell_Phantasm.java:144-149`, `Spell_PhantomHound.java:154-160`), per-player quest criteria, and database saves of owned rooms all assume many connected people.

## Takeaways for a Python SUD

**Effects as objects with two hooks.** Port the pattern directly: an effect has a tick counter, an optional `ok_message` that can veto, and an `on_message` for after-the-fact changes. Everything in this bucket can be expressed that way.

```python
class Effect:
    ticks: int | None = None          # None means it lasts until removed
    def tick(self, world) -> bool:    # return False to remove
        if self.ticks is None: return True
        self.ticks -= 1
        return self.ticks > 0
    def ok_message(self, msg) -> bool:   # False vetoes the message
        return True
    def on_message(self, msg) -> None:   # after the message is sent
        pass
```

Use one duration convention. The three-way positive/zero/negative rule in `StdAbility.java:2174-2330` is hard to read. A Python port can take an explicit tick count, `None` for permanent, and a separate percentage helper where needed.

**Absorbers as budgets.** A globe is a counter, `absorbed_levels`, with a cap of `(caster_level + 2*x) * 4`. Blocking a spell adds to the count and removes the spell. Once the cap is passed, the globe is removed. This is one of the cleanest ideas here.

**Nightmare as a per-action drain.** Give the effect a budget (`100 * speed`) and charge a fixed cost per blocked action (`round(INT * 2.5)`). It is simple and readable.

**Use `random.randrange`, not signed modulo.** The Mirror Image bug (Problems 1) should not be copied. Python's `random.randrange(n)` is always in range.

**Single-player simplifications.**

- Drop `includePlayers` and the group logic. Area spells hit the hostile creatures in the room; allies come from the party list, which is the player's own companions.
- Keep the summon rules that do not depend on other people: the phantasm dissolves when its target changes (`Spell_Phantasm.java:91-113`), and the phantom hound dissolves on a disbelief timer (`Spell_PhantomHound.java:127-141`). Both are good single-player design.
- Replace the quit hooks with "the summoner is removed from play" or a level change.
- Replace database saves of owned rooms with ordinary world state.

**Planes as data.** Store planes in a TOML or YAML file, one table per plane, with an atmosphere, opposed planes, and a set of rules. Planar Block then becomes a set of forbidden plane names on the target. Planar Burst and Planar Banish check only whether the caster or target is on a plane.

**Look hooks for perception.** A `describe_room(room, viewer)` method that effects can override covers Mirage, Planar Bubble and Distortion, Mirror Image's look line, and Obscure Self's look refusal. Cache the result by a content hash, keyed on the viewer, as Planar Bubble does (`Spell_PlanarBubble.java:233-257`).

**Permanency as max-mana upkeep.** Charge a permanent loss of maximum mana to make a spell permanent. It is a strong trade-off that fits a single-player survival loop.

**Crafting automaton.** Mystic Loom is worth borrowing as a "familiar" that uses the game's own recipes on nearby materials. The essential rule is that the owners' originals come back intact and only the outputs are used up.

**Drop.** The duplicated Bubble and Distortion code, the dead fields, the wrappers that build one spell to query another, the EXPRATE coupling, and the `StdMOB` fake invoker are all things not to copy.

## Coverage

- **Read in full (44 of 44 bucket files).** Every file was read from its first class line to the end. The import and licence header was skipped (roughly lines 1-34). `Spell_Meld.java` was read from line 1.
- **Read in full:** `com/planet_ink/coffee_mud/Abilities/Spells/Spell.java` (162 lines).
- **Read in part (framework call sites):** `StdAbility.java` at 899-915, 1441-1490, 2113-2135, 2140-2210, 2273-2400, 2843-2875. `MUDFight.java` at 141-190 and 819-860. `Dice.java` at 398-412. `StdTickClient.java` at 140-170. `CMProps.java` by grep (681, 3539). `StdPlanarAbility.java` and `PlanarAbility.java` by grep only. `resources/help/spell_help.ini` by grep and short reads.
- **Not read (other buckets, referenced by name only):** `Spell_Hold`, `Spell_Sleep`, `Spell_Slow`, `Spell_Haste`, `Spell_Fly`, `Spell_FeatherFall`, `Spell_WaterBreathing`, `Spell_Invisibility`, `Spell_SummonMarker`, `Chant_EelShock`, `Textiling`, `Prop_RoomView`, `Prop_AbsorbDamage`, `Disease_Magepox`, `CharClasses/Conjurer.java` (line 73 only), and the plane engine in `StdPlanarAbility.java`.
- **Not verified in play.** Every "unverified" note above is a reading of the code, not a test run.
