# Spells, part 2: clan magic, detection and mind effects

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b087` (Abilities/Spells [part 2/9: Spell_ClanDonate .. Spell_DetectWater]) · 44 files · 8924 lines of Java.

Path convention: Java paths are relative to `com/planet_ink/coffee_mud/` (the package root, as in the brief's own examples), so `Abilities/Spells/Spell.java:162` means `com/planet_ink/coffee_mud/Abilities/Spells/Spell.java`. Unless stated, spells are in `Abilities/Spells/`, and `StdAbility.java` is `Abilities/StdAbility.java`. "Default" mana means the cost comes from the ability's usage table, which was not traced.

## Summary

The 44 spells in this bucket are 13 Detect spells, 4 clan spells, 5 creation and summoning spells, 10 mind and body debuffs, 4 light and dark spells, 4 defences and 4 utility spells. Each class is a subclass of `Spell`, which adds an armour check and a backlash hook to the shared pipeline in `StdAbility` (cost, cooldown, proficiency and effect timing). A spell's result is an effect object that changes stats, vetoes or rewrites later messages, and counts down ticks. Two ideas are worth borrowing for a text game: perception as a filter (the Detect family runs on look messages and room-change ticks), and clan experience used as a currency for enchantments. The bucket also contains several real bugs, including a double tick, a double mana charge, and mana spent before a refusal (see Problems).

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Abilities/Spells/Spell.java` | Family base: class code, Mage backlash on malicious effects, armour and concentration check | 162 |
| `Abilities/StdAbility.java` | Shared pipeline for every spell (outside this bucket): invoke, cost, proficiency, effect timing | 3018 |
| `Abilities/Spells/Spell_ClanWard.java` | Clan room ward; rank-gated entry | 327 |
| `Abilities/Spells/Spell_ClanDonate.java`, `Spell_ClanExperience.java`, `Spell_ClanHome.java` | Clan item donation, XP donation, group recall | 203, 193, 186 |
| `Abilities/Spells/Spell_Clone.java` | NPC copy of the caster with cloned items | 311 |
| `Abilities/Spells/Spell_DemonGate.java` | Summons an evil demon mount that may turn on the caster | 249 |
| `Abilities/Spells/Spell_Cogniportive.java` | Item bound to a home room; spoken `HOME <name>` recalls it | 322 |
| `Abilities/Spells/Spell_Delay.java` | Stores a spell in a room, casts it after 5 ticks | 211 |
| `Abilities/Spells/Spell_Delirium.java` | Rewrites names in messages seen by the target | 324 |
| `Abilities/Spells/Spell_ColorSpray.java`, `Spell_Cloudkill.java` | Level-gap debuff; gas damage with collapse | 255, 204 |
| `Abilities/Spells/Spell_Confusion.java`, `Spell_Command.java`, `Spell_Delude.java` | Retargeting, forced order, alignment reroll | 171, 162, 252 |
| `Abilities/Spells/Spell_DeathWarning.java`, `Spell_Counterspell.java`, `Spell_Deflection.java`, `Spell_CombatPrecognition.java` | Defensive effects that veto or intercept | 173, 158, 230, 216 |
| `Abilities/Spells/Spell_DetectGold.java`, `Spell_DetectWater.java` | Room-change "senses" over six directions plus look reports | 299, 340 |
| `Abilities/Spells/Spell_DetectBombs.java`, `Spell_DetectTraps.java` | Look-time trap reports (near-duplicate code) | 258, 252 |
| `Abilities/Spells/Spell_DetectMagic.java`, `Spell_DetectPoison.java`, `Spell_DetectScrying.java`, `Spell_DetectSentience.java` | Look-time effect lists; snoop and scry check; census of minds | 199, 160, 171, 139 |
| `Abilities/Spells/Spell_DetectAmbush.java`, `Spell_DetectUndead.java` | Entry veto for hidden or aggressive mobs; undead alert | 192, 171 |
| `Abilities/Spells/Spell_ConjureNexus.java`, `Spell_ConjureAmmunition.java` | Room mana aura; ammunition by level | 160, 155 |
| `Abilities/Spells/Spell_DarknessGlobe.java`, `Spell_DarkSensitivity.java`, `Spell_Darkness.java`, `Spell_ContinualLight.java` | Light and dark disposition | 226, 255, 153, 153 |
| `Abilities/Spells/Spell_DestroyObject.java`, `Spell_ClarifyScroll.java` | Item destruction; scroll enhancement | 120, 135 |
| `core/CMProps.java:681` | `TIME_TICK = 4000` ms default tick (settable as `TICKTIME`) | n/a |

## How it works

### 1. The shared cast pipeline

`Spell.invoke` (`Abilities/Spells/Spell.java:153-161`) calls `StdAbility.invoke` and then `spellArmorCheck`. `StdAbility.invoke` (`Abilities/StdAbility.java:1964-2086`) runs these steps in order:

1. A cast needs a mobile, awake caster (`StdAbility.java:1974`).
2. The per-ability cooldown is checked, with the message "You must wait @x1 before you can do that again." (1978-1989).
3. Movement-type abilities are refused when bound (1991-1996).
4. The cost comes from `usageCost` (1998). A compounding rule raises the cost of repeated casts inside a window, and a negative penalty refuses the cast outright ("You can't do that again just yet.", 2023; rule applied at 2001-2046).
5. `testUsageCost` (2050; mana test at 1925-1931), the minimum wait between casts (2053-2066) and ability components (2067) are checked.
6. Mana, movement and hit points are debited (2074-2076). Then a `MSG_PREINVOKE` message is sent, and any listener can veto it (2078-2081).

`spellArmorCheck` (`Spell.java:132-151`) applies to player casters only. Wearing armour that is not cloth-class gives a 50% chance per cast that the armour "absorbs" the spell (line 139), and a separate check can stop concentration (144). Four clan spells opt out by returning true from `disregardsArmorCheck`: `Spell_ClanWard.java:83-86`, `Spell_ClanDonate.java:83-86`, `Spell_ClanExperience.java:83-86` and `Spell_ClanHome.java:77-80`.

Cross-cutting backlash: `Spell.maliciousAffect` (`Spell.java:94-130`). When a player Mage is hit by a malicious spell while already carrying more than five malicious spell effects, a 1% roll (`rollPercentage()==1`, line 106) adds `Disease_Magepox` (lines 119-123).

### 2. Proficiency and the outcome roll

`proficiencyCheck` (`StdAbility.java:1441-1486`):

- `pct` starts as the ability's proficiency (1452). Ability adjustments of the form `PROF+<ID>` are added (1458-1463), and then `2 x XLEVEL` (1467-1468).
- The value is clamped to 5..95 unless `DIS955RULE` is set (1470-1476). Then `XLEVEL/2` is added (1478).
- A non-negative adjustment is added to `pct` (1480-1481). A negative adjustment is a separate gate: a first d100 must not exceed `100 + adjustment` (1483-1484). After that, a second d100 must be below `pct` (1485).

Uses of the adjustment in this bucket:

- Destroy Object: `adjustment = ((casterLevel + 2 x XLEVEL) - itemLevel) x 25` (`Spell_DestroyObject.java:98`). A 3-level gap gives -75, so the first roll must be 25 or less. A 4-level gap gives -100, and the first roll can then never pass.
- Combat Precognition: the dodge uses DEX-60 for weapon blows (`Spell_CombatPrecognition.java:106`) and DEX-50 for other attacks (122). A weapon dodge therefore needs the first d100 at or below 40 + DEX, then the normal roll.
- Confusion: `adjustment = targetLevel - (casterLevel + XLEVEL)/2`, passed as `-adjustment` (`Spell_Confusion.java:145-146`). A stronger target lowers the chance.

Expertise: `getX1Level`, `getXLEVELLevel`, `getXMAXRANGELevel` and the other `X*` helpers map to `ExpertiseLibrary.XType` (`StdAbility.java:419-462`). Range is widened by `adjustedMaxInvokerRange(max) = max + ceil(max x 0.2 x XMAXRANGE)` (`StdAbility.java:482-490`). Cloudkill's base range of 10 becomes 12 at XMAXRANGE 1.

### 3. Caster level and durations

- Effective level: `adjustedLevel` (`StdAbility.java:899-915`) starts from the spell's lowest qualifying level and adds the caster's surplus over the class requirement. A non-zero `asLevel` (used for items) overrides it.
- Malicious effects, `getMaliciousTickdownTime` (`StdAbility.java:2174-2183`): a positive argument is an exact tick count. Zero gives `round(1.1 x L) + 25` ticks (2178). A negative argument scales that default by -argument percent (2182).
- Beneficial effects, `getBeneficialTickdownTime` (`StdAbility.java:2312-2331`): a positive argument is exact. Zero gives `4L + 40` ticks (2325). `L` is capped at the target's level plus EXPRATE when the caster could abuse the spell (2317-2324). EXPRATE defaults to 6 (`coffeemud.ini:406`).
- Tick countdown: `StdAbility.tick` (`StdAbility.java:2843-2862`) lowers `tickDown` only on `Tickable.TICKID_MOB` (2848-2850, 2854) and calls `unInvoke()` at zero (2856-2857).
- `TICKS_FOREVER` is `Integer.MAX_VALUE-1000` (`Abilities/interfaces/Ability.java:46`) and is used by Continual Light (`Spell_ContinualLight.java:144`).

Worked numbers at the default 4-second tick: a beneficial detect at adjusted level 10 lasts 80 ticks, about 5 min 20 s. A malicious debuff at level 10 lasts 36 ticks, about 2 min 24 s. Confusion sets 15 ticks explicitly, but its double tick (Problems 4) makes it run for about 8.

### 4. Effect objects and the message flow

A spell object is a prototype. A hostile or friendly effect is a copy: `maliciousAffect` and `beneficialAffect` call `copyOf()` (`StdAbility.java:2203`, and 2341-2347 for beneficial) and then `startTickDown` (`StdAbility.java:698`). `startTickDown` sets the invoker, marks the copy as not saved (702) and sets `tickDown` (779).

Hooks that effects override, with examples:

- `affectPhyStats` and `affectCharStats` change the stat base: Color Spray removes sight (`Spell_ColorSpray.java:91`) and sets sleeping for the unconscious tier (89-90). Darkness sets IS_DARK on the room (`Spell_Darkness.java:105`). Detect Hidden adds CAN_SEE_HIDDEN (`Spell_DetectHidden.java:113`). Detect Hidden also raises the save-overlook stat (`Spell_DetectHidden.java:106`).
- `okMessage` vetoes: Counterspell (`Spell_Counterspell.java:94-115`), Deflection (`Spell_Deflection.java:95-149`), Combat Precognition (`Spell_CombatPrecognition.java:91-168`), and Color Spray, which blocks the target's eye, hand, mouth and move actions (`Spell_ColorSpray.java:136-168`).
- `executeMsg` reacts to events: Clone ends when the caster quits (`Spell_Clone.java:96-101`) and when the clone dies (106-125). Demon Gate ends when the caster quits (`Spell_DemonGate.java:122-134`). Detect Magic appends a report to a look (`Spell_DetectMagic.java:103-145`).
- `tick` does periodic work: Confusion retargets (`Spell_Confusion.java:86-108`), Detect Undead alerts on room change (`Spell_DetectUndead.java:105-125`), and Conjure Nexus heals each mob tick (`Spell_ConjureNexus.java:85-111`).

Messages are two-phase. A spell builds a `CMMsg` with `CMClass.getMsg`, asks the room `okMessage`, and then calls `send` (Cloudkill: `Spell_Cloudkill.java:153-158`). Confusion requires both its messages to pass (`Spell_Confusion.java:152`).

### 5. Clan magic: treasury and rank gates

- Ownership and authority: ClanWard reads the room's land-title owner (`Spell_ClanWard.java:196-202`), finds the caster's clan role (209-214), and needs `CLAN_BENEFITS` authority (229).
- Cost: `exp = (1 + points) x CLANENCHCOST` (`Spell_ClanWard.java:279`). `points` is the clan role index from `getRoleFromName`. `CLANENCHCOST=1000` (`coffeemud.ini:1650`), so role 0 costs 1,000 clan XP and role 3 costs 4,000. A role above the clan level is refused (286-290).
- Gate: `passesMuster` (`Spell_ClanWard.java:103-122`) refuses a member whose role is below the ward's RANK. Tracking monsters pass, and so do sneakers unless NOSNEAK is set. A group leader counts for a follower (134-146). Entry is denied with "You may not go that way." (147). Mounting is denied with "You are not permitted in there." (175).
- Donation: Clan Donate costs 50% of max mana (`overrideMana` = COST_PCT+50, `Spell_ClanDonate.java:77-80`; decoded at `Abilities/interfaces/Ability.java:651-653`). It drops the held item in the clan donation room (`postDrop`, 171-172) and then moves it (183).
- Experience: Clan Experience costs 5 mana flat (`Spell_ClanExperience.java:79`) and needs caster level 10 (93). It refuses an amount at or above the XP still needed for the current level (150). XP is removed from the caster with `postExperience` (181) and added to the clan (182).
- Recall: Clan Home teleports the caster's group to the clan recall room (123, 147-171). It checks that the clan still owns the room (134). Combatants flee first (162-165), and followers get `postLook` (171).

### 6. Creation and summoning

- Clone (`Spell_Clone.java`): costs 200 mana flat (81). `determineMonster` (275-310) builds a GenMob, or a GenRideable if the caster is rideable (278-281), and copies the caster's base stats, state and gen fields (282-304). `cloneItems` (229-273) copies items and strips spell lists from potions, scrolls, pills and wands (242-256). It adds `Prop_HaveZapper "-NAMES"` and an immunity list naming Disenchant, Duplicate, Fabricate and PolymorphObject (258-263). Behaviour is a 50% roll (179). On success the clone becomes an aggressive or guardian NPC by alignment, or a plain mobile guard, and attacks the caster (181-207). Otherwise it follows the caster (209-216). The effect lasts the default beneficial duration (219), so `4L + 40` ticks.
- Demon Gate (`Spell_DemonGate.java`): costs 100 mana (84-88). `determineMonster` (203-248) creates an evil "great demonbeast" rider mount with STR, DEX and CON 18 (212-214), weight 850 (210), race Demon (215), no XP through `Prop_ModExperience` (227) and rider capacity 2 (230). Level is `caster + XLEVEL + 2 x X1`, then `+2 + XLEVEL` (173, 208). Behaviour (172-191): join an existing demonbeast in the room; otherwise a 10% roll (180) turns it on the caster; otherwise it follows and attacks the caster's victim. It vanishes when its victim changes (112-116).
- Cogniportive (`Spell_Cogniportive.java`): binds an item to a home room for 1000 ticks (301), about 66 min at the default tick. `establishHome` (82-162) searches for the item's name in this order: NPC owner's worn items, shop stock, NPC inventory, and room items. Rooms with a land title are skipped (100, 120, 139, 155). Speaking `HOME <name>` (224-255) fires `waveIfAble` (165-208). Followers move "in a puff of smoke". If the home is not accessible, the item says "Strange fizzled sparks fly from @x1." (175).
- Conjure Ammunition (`Spell_ConjureAmmunition.java`): picks the wielded ammunition weapon, or a random one from the target's inventory (85-106). The count depends on adjusted level: below 6 gives 5, below 11 gives 10, below 21 gives 25, below 31 gives 50, otherwise 100 (116-128). Rideable targets get a single round (113-114). The ammunition is dropped in the room (147).
- Conjure Nexus (`Spell_ConjureNexus.java`): first removes any existing nexus in accessible rooms (119-134), then creates a room aura. Each mob tick it runs `recoverTick` and then restores hit points, movement, hunger and thirst to their pre-tick values (99-107). Mana is the only stat that keeps the regeneration (`Libraries/MUDFight.java:1062` recovers mana by formula). The nexus is therefore a mana aura, not a healing aura.

### 7. Debuffs

- Color Spray (`Spell_ColorSpray.java:170-254`): needs the target's eyes and sight (195). Its `castingQuality` is indifferent outside combat, so NPCs choose it only in combat (120-124). The level gap is `(caster + XLEVEL/3) - target` (203). Above 2 x EXPRATE (12 at the default), the target is unconscious with the default duration (208-210). Above EXPRATE (6), the target is stunned for 4 ticks (213-217). Otherwise the target is dazed for 2 ticks (220). Messages: "You are unconscious.", "You are stunned and dazed.", "You are dazed." (157-162).
- Cloudkill (`Spell_Cloudkill.java:130-203`): range 1 to 10, adjusted (60-70). Damage starts from the target's current hit points (163). It is cut to 10% if the target is above the caster's half level (165-167), halved on a save (169-170), at least 1, plus `X1` (172-174). A hit on a target with legs drops them for 2 ticks (178-190). The effect also sets sitting (104).
- Confusion (`Spell_Confusion.java`): 15 ticks (158). While in combat, up to ten random inhabitants are tried as the new victim (95-106). NPCs do not choose it in rooms with fewer than three inhabitants (127-131).
- Delirium (`Spell_Delirium.java`): a pool of 300 points is drained by the target's INT each mob tick (312, 229-231). A standard malicious duration also applies (313). While active, `okMessage` replaces names in messages to and from the target with random names taken from the area (237-260; picker at 92-168). A target with INT 15 can therefore lose the effect after 20 ticks.
- Delude (`Spell_Delude.java`): the alignment is set to a random point in the opposite band (alignment block, 130-185). Lawful or chaotic inclination is used only when the good and evil alignments are not loaded (186-241). Duration is the standard beneficial one (127). unInvoke restores alignment only (81-97).
- Command (`Spell_Command.java:65-161`): NPCs only (90-94). Refuses FOL, Sleep, AutoMelee and abilities with FLAG_NOORDERING (96-125). A successful order is queued as a forced command (151). An empty order makes an NPC flee (80-81).
- Deafen, Clog Mouth, Deaden Smell (`Spell_Deafness.java:82`, `Spell_Clog.java:82`, `Spell_DeadenSmell.java:82`): set the CAN_NOT_HEAR, CAN_NOT_TASTE and CAN_NOT_SMELL sense bits with the standard malicious duration (136, 134, 120). Clog refuses NPCs in combat (104-105).
- Dark Sensitivity (`Spell_DarkSensitivity.java`): in a dark room the target loses CAN_SEE_DARK, takes -50 attack adjustment and gets +50 armour (92-96). When dark-blind, examining is always refused (125-131). Cast and delicate-hand actions aimed at others fail on a 50% roll (142-152). The comment at line 92 says the condition "should be" different, so the rule is known to be approximate.

### 8. Defences

- Counterspell (`Spell_Counterspell.java:101-112`): when a hostile spell targets the protected mob, `70 + 2 x ((mob level + 2 x XLEVEL(invoker)) - attacker level)` percent (108) dispels it. The ward then sets `tickDown = 0` (111), so it ends on the next tick.
- Deflection (`Spell_Deflection.java:95-149`): protects a mob, item or room from ranged weapon attacks aimed at it (103-104). Each deflection shows "A deflection ward sends the attack from @x1 off target." (114). The ward then ends unless the roll is at or below `5 x XLEVEL` (115). With no XLEVEL it ends on the first deflection.
- Death Warning (`Spell_DeathWarning.java:102-125`): on a lethal blow, HP is set to 1 and the stored command runs (112-115). The default command is FLEE for an NPC (142-143). If the room changed, death is cancelled, the mob is pacified and the warning stays active (116-120). Otherwise HP is restored and the warning ends (122-123). The warning reads "You receive a warning of your impending death!!" (114).
- Combat Precognition (`Spell_CombatPrecognition.java:91-168`): as in section 2. A dodge cannot repeat on the very next blow (`lastTime`, 107-117). Costs 100 mana flat (81).

### 9. The Detect family: one template, four shapes

Shape A, look reports. `executeMsg` adds a trailer when the caster looks or examines a target: Bombs (`Spell_DetectBombs.java:187-209`), Traps (`Spell_DetectTraps.java:183-206`), Gold (`Spell_DetectGold.java:229-252`), Water (`Spell_DetectWater.java:283-307`) and Magic (`Spell_DetectMagic.java:103-145`).

Shape B, room-change reports. `tick` fires when `lastRoom` changes. Gold and Water call `messageTo`, which scans the six compass directions plus "here". The loop runs from `Directions.NUM_DIRECTIONS()` down to 0, and the top index means "here" (`Spell_DetectGold.java:161-166`). Gold reports "You sense golden emanations coming from @x1." (206) or "...from @x1, and @x2." (208). Water uses "Water smells are coming from @x1, and @x2." (246-248). Detect Undead reports "gives off a cold dark vibe" for each undead inhabitant (`Spell_DetectUndead.java:115-121`).

Shape C, entry veto. Detect Ambush refuses the first entry into a room that holds a hidden mob or a mob with aggressive behaviour: "Potential danger in that direction stops you for a second." (`Spell_DetectAmbush.java:105-140`). The veto happens once per room, because `lastRoom` is set before it is checked (135).

Shape D, sense bits and one-shot reports. The sense bits are set in `affectPhyStats` for Hidden (`Spell_DetectHidden.java:113`), Invisible (`Spell_DetectInvisible.java:106`), Metal (`Spell_DetectMetal.java:105`) and Bonus (`Spell_DetectMagic.java:151`). One-shot reports are Sentience (`Spell_DetectSentience.java:83-138`) and Scrying (`Spell_DetectScrying.java:121-170`).

Sentience lists minds with INT of at least 2 (116) that are not `Prop_WizInvis` (117). It searches the passable, area-only rooms within `35 + XMAXRANGE` (103). Scrying reports snooping sessions on the target (139-145). It also reports scry-type effects found on the target, its room or its area. Those effects are `Spell_Scry`, `Spell_Claireaudience`, `Spell_GreaterClaireaudience`, `Spell_Clairevoyance` and `Spell_GreaterClairevoyance` (146-160), which are not in this bucket.

All detect spells call `beneficialAffect(..., 0)` (`Spell_DetectGold.java:291`, and the same pattern in the others), so they last `4L + 40` ticks. They use the default mana cost.

### 10. Item and time magic

- Delay (`Spell_Delay.java:93-96`): costs all mana (`COST_ALL`). It parses the spell's command and stores it (155-157). A hostile spell is refused when a non-allied being is present (166-176). Success creates a room effect for 5 ticks (200), which is 20 s. When it ends (`unInvoke`, 105-139), the stored spell is cast by a fresh StdMOB named "the thin air", at caster level + 2 x XLEVEL (121, 129). The message "You hear a clock start ticking down in your head...20...19..." (199) matches 5 ticks at 4 s. See Problems for a mana caveat.
- Continual Light (`Spell_ContinualLight.java:84-152`): lasts forever (144). It sets IS_LIGHTSOURCE on non-rooms (88) and removes IS_DARK where it is set (89-90). The guard is safe because `isInDark` tests that same bit (`Libraries/Sense.java:1047-1050`).
- Destroy Object (`Spell_DestroyObject.java:71-119`): refuses another player's corpse (81-86) and items the caster cannot destroy (89). The proficiency adjustment is 25 per level gap (98).
- Clarify Scroll (`Spell_ClarifyScroll.java:52-134`): 50 mana flat (53-55). It refuses scrolls that are already clarified (90) and non-arcane spells (102-111). It adds `5 + XLEVEL` uses (125).
- Comprehend Languages (`Spell_ComprehendLangs.java:98-155`): adds a translated trailer to other people's language speech and channel messages (119-151). Smoke signals are a special case (106-113). Channels flagged NOLANGUAGE are skipped (129).

### 11. Messages players see

Short texts, as the code emits them:

- "You may not go that way." (`Spell_ClanWard.java:147`); "Your clan does not own this room." (199); "You need @x1 to do that, but your @x2 has only @x3 experience points." (282).
- "Potential danger in that direction stops you for a second." (`Spell_DetectAmbush.java:138`).
- "You sense golden emanations coming from @x1." (`Spell_DetectGold.java:206`); "Water smells are coming from @x1." (`Spell_DetectWater.java:246`); "Your liquid senses are saturated. This is a very wet place." (134).
- "You can't seem to make out <T-NAME> in the dark." (`Spell_DarkSensitivity.java:150`).
- "A deflection ward sends the attack from @x1 off target." (`Spell_Deflection.java:114`).
- "You receive a warning of your impending death!!" (`Spell_DeathWarning.java:114`).
- "The Nexus in '@x1' dissipates." (`Spell_ConjureNexus.java:80`).
- "Your attitude returns to normal." (`Spell_Delude.java:95`).
- "You daydream @x1." (`Spell_Daydream.java:100`).

### 12. Interactions with other systems

- Clans and land: `CMLib.clans()` (`Spell_ClanWard.java:202`), `CMLib.law().getLandTitle` (196; also `Spell_ClanHome.java:134` and `Spell_Cogniportive.java:100`), `Clan.getAuthority` (229), `getDonation` and `getRecall` (`Spell_ClanDonate.java:144`, `Spell_ClanHome.java:123`).
- Movement: `CMLib.commands().postFollow`, `postFlee`, `postLook`, `postDrop` (`Spell_Clone.java:214`, `Spell_ClanHome.java:164-171`, `Spell_ClanDonate.java:172`).
- Factions: `CMLib.factions()` (`Spell_Delude.java:92-94`, `116-241`).
- Traps and shops: `CMLib.utensils().fetchMyTrap` (`Spell_DetectBombs.java:108`), `CMLib.coffeeShops().getShopKeeper` (169).
- Search: `CMLib.hunt().findInventory` (`Spell_Cogniportive.java:91`), `CMLib.tracking().getRadiantRooms` (`Spell_DetectSentience.java:103`).
- Items and XP: `CMLib.coffeeMaker().makeAmmunition` (`Spell_ConjureAmmunition.java:130`), `CMLib.leveler().postExperience` (`Spell_ClanExperience.java:181`).
- Ability components: `checkComponents` (`StdAbility.java:2088-2111`).

## Content worth mining

Catalogue of all 44 spells. Quality names are `Ability.QUALITY_*` (`Abilities/interfaces/Ability.java:1083-1093`). "Default" mana is the usage-table value, not traced. "Std" duration means the default beneficial or malicious formula from section 3.

| Spell (class) | Domain | Quality | Mana | Target, effect and key numbers |
|---|---|---|---|---|
| Clan Ward (`Spell_ClanWard`) | Abjuration | Indifferent | default | Room ward; costs (1+role) x 1000 clan XP; refuses members below RANK (279, 103-122) |
| Clan Donate (`Spell_ClanDonate`) | Conjuration | Indifferent | 50% of max | Held item to clan donation room (79, 171-183) |
| Clan Experience (`Spell_ClanExperience`) | Enchantment | Indifferent | 5 flat | Moves caster XP to clan; caster level 10 or more (79, 93, 181-182) |
| Clan Home (`Spell_ClanHome`) | Conjuration | Indifferent | default | Group recall to clan room; combatants flee first (123, 162-165) |
| Clone (`Spell_Clone`) | Transmutation | Indifferent | 200 flat | NPC copy of caster for `4L+40` ticks; dies with caster or when room differs (81, 219, 135-142) |
| Demon Gate (`Spell_DemonGate`) | Conjuration | Beneficial self | 100 flat | Evil demon rider; joins, fights or attacks caster's victim (84-88, 172-191) |
| Cogniportive (`Spell_Cogniportive`) | Conjuration | Indifferent | default | Item home for 1000 ticks; spoken `HOME <name>` recalls it (301, 224-255) |
| Delay (`Spell_Delay`) | Evocation | Indifferent | all mana | Stores a spell in the room; cast after 5 ticks (93-96, 200) |
| Delirium (`Spell_Delirium`) | Illusion | Malicious | default | Names in messages swapped for area names; 300 points drained by INT per tick (229, 312-313) |
| Delude (`Spell_Delude`) | Transmutation | OK self | default | Alignment set to a random point in the opposite band (116-164) |
| Deafen (`Spell_Deafness`) | Transmutation | Malicious | default | CAN_NOT_HEAR; standard malicious duration (82, 136) |
| Clog Mouth (`Spell_Clog`) | Transmutation | Malicious | default | CAN_NOT_TASTE; NPCs do not choose it in combat (82, 104-105) |
| Color Spray (`Spell_ColorSpray`) | Illusion | Malicious | default | Level-gap tiers: dazed 2 ticks, stunned 4, unconscious standard (203-220) |
| Cloudkill (`Spell_Cloudkill`) | Evocation | Malicious | default | Range 1-10; gas damage from current HP; leg collapse 2 ticks (60-70, 163-190) |
| Darkness (`Spell_Darkness`) | Evocation | Malicious (tag) | default | Room IS_DARK for everyone, applied as beneficial (101-105, 143) |
| Darkness Globe (`Spell_DarknessGlobe`) | Evocation | OK self | default | Dark bubble on caster and room (97-125, 217) |
| Dark Sensitivity (`Spell_DarkSensitivity`) | Transmutation | Malicious | default | In dark: -50 attack, +50 armour, examine refused, cast 50% (92-96, 125-152) |
| Deflection (`Spell_Deflection`) | Abjuration | OK others | default | Blocks ranged attacks on mob, item or room; ends unless roll <= 5 x XLEVEL (103-117) |
| Death Warning (`Spell_DeathWarning`) | Divination | Beneficial self | default | At lethal blow runs stored command (default FLEE) at 1 HP (102-125) |
| Combat Precognition (`Spell_CombatPrecognition`) | Divination | Beneficial self | 100 flat | Dodges melee (roll <= 40+DEX) and other attacks (DEX-50), not twice in a row (81, 105-165) |
| Counterspell (`Spell_Counterspell`) | Abjuration | Beneficial others | default | Dispels hostile spell at 70 + 2 x level gap %; ends next tick (101-112) |
| Confusion (`Spell_Confusion`) | Enchantment | Malicious | 15 ticks | Victim retargets to random inhabitant in combat (95-106, 158) |
| Command (`Spell_Command`) | Enchantment | Malicious | default | NPC only; queues one forced order; no FOL, sleep, automelee (80-151) |
| Daydream (`Spell_Daydream`) | Illusion | Indifferent | default | Caster's words sent to AFK players in accessible rooms (95-100) |
| Detect Water (`Spell_DetectWater`) | Divination | OK self | default | Liquid report on room change over six directions; look reports (125-307) |
| Detect Gold (`Spell_DetectGold`) | Divination | OK self | default | Gold in rooms, items and NPCs by direction (105-252) |
| Detect Bombs (`Spell_DetectBombs`) | Divination | OK self | default | Look reports bombs, including shop stock (104-209) |
| Detect Traps (`Spell_DetectTraps`) | Divination | OK self | default | Same as bombs for any trap (104-206) |
| Detect Magic (`Spell_DetectMagic`) | Divination | OK self | default | Look lists active spell, prayer, song and chant effects (103-151) |
| Detect Poison (`Spell_DetectPoison`) | Divination | OK others | default | Lists poison effects, and poison skills for mobs (88-159) |
| Detect Ambush (`Spell_DetectAmbush`) | Divination | OK self | default | Stops first entry into a room with a hidden or aggressive mob (105-140) |
| Detect Hidden (`Spell_DetectHidden`) | Divination | Beneficial self | default | CAN_SEE_HIDDEN; save-overlook +level + 2 x XLEVEL + 100 (103-114) |
| Detect Invisible (`Spell_DetectInvisible`) | Divination | Beneficial self | default | CAN_SEE_INVISIBLE (103-107) |
| Detect Metal (`Spell_DetectMetal`) | Divination | OK self | default | CAN_SEE_METAL (102-106) |
| Detect Undead (`Spell_DetectUndead`) | Divination | OK self | default | Alert on entering a room with undead (105-125) |
| Detect Scrying (`Spell_DetectScrying`) | Divination | Indifferent | default | Reports snoopers and scry effects on target (121-170) |
| Detect Sentience (`Spell_DetectSentience`) | Divination | Indifferent | default | One-shot census of minds with INT >= 2 within 35 + XMAXRANGE rooms (83-138) |
| Conjure Ammunition (`Spell_ConjureAmmunition`) | Conjuration | Indifferent | default | 5, 10, 25, 50 or 100 rounds by adjusted level (112-130) |
| Conjure Nexus (`Spell_ConjureNexus`) | Conjuration | Indifferent | default | Room aura that adds mana each mob tick (85-111) |
| Continual Light (`Spell_ContinualLight`) | Evocation | OK others | default; forever | Light source; clears IS_DARK (85-91, 144) |
| Destroy Object (`Spell_DestroyObject`) | Evocation | Indifferent | default | Destroys an item; 25 proficiency points per level gap (71-119, 98) |
| Clarify Scroll (`Spell_ClarifyScroll`) | Enchantment | Indifferent | 50 flat | Adds 5 + XLEVEL uses to an arcane scroll (53, 125) |
| Deaden Smell (`Spell_DeadenSmell`) | Transmutation | OK others | default | CAN_NOT_SMELL (82, 120) |
| Comprehend Languages (`Spell_ComprehendLangs`) | Divination | Beneficial self | default | Translates other tongues in speech and channels (98-155) |

Reusable content:

- Message strings are short, reusable one-liners for a text game. The list is in section 11.
- The Detect spells already contain reportable lines for every kind of thing a player can look at (rooms, exits, shop stock, carried items, NPCs).
- The Cogniportive home-search order (`Spell_Cogniportive.java:82-162`) is a ready-made fallback chain for "where is this thing's home".
- The Clone immunity list (`Spell_Clone.java:262`) is a ready-made list of anti-duplication spells to block on copies.

## Notable design ideas

- Veto and rewrite as the main extension point. Counterspell, Deflection and Color Spray use `okMessage` to block actions. Delirium uses the same hook to rewrite text, so a perception effect lives entirely in the presentation layer.
- A tethered item. Cogniportive stores a home key in the item's text when it is cast (`Spell_Cogniportive.java:305-308`). A spoken command then triggers recall. The item does not need to know where it is.
- Deferred spell. Delay stores the parsed input and casts it later from a placeholder caster. This is a clean way to express "cast in N turns".
- Pre-set reflex. Death Warning lets the player choose a command in advance. The engine runs it at the moment of lethal damage, and the warning survives a successful escape.
- Clan XP as mana. Enchantments cost clan experience, `(1+role) x 1000` for a ward (`Spell_ClanWard.java:279`), and players can trade their own XP for clan XP (`Spell_ClanExperience.java:181-182`).
- Level gap as tiers. Color Spray maps the level gap to unconscious, stunned or dazed (`Spell_ColorSpray.java:203-220`). Destroy Object gives 25 points of proficiency per level gap.
- Clone safeguards. Copied items are given an immunity list and stripped of consumable spells (`Spell_Clone.java:242-263`).
- The Detect template. Look trailer, room-change tick, sense bit, one-shot report. Each is small, and the set is easy to explain to a player.
- Three-way mana encoding. Flat value, percent of max (`COST_PCT`) or all mana (`COST_ALL`) (`Abilities/interfaces/Ability.java:651-653`).

## Problems and cruft

Bugs, in rough order of impact. Each is from reading the code; runtime was not tested.

1. Delay spends all mana before refusing a duplicate. `Spell_Delay.java:178` calls `super.invoke`, which debits mana (`StdAbility.java:2074-2076`). The check "A delay has already been cast here!" at 181-188 then returns false. The caster pays for nothing.
2. Conjure Nexus removes the existing nexus before checking cost. `Spell_ConjureNexus.java:119-134` unInvokes the old aura, and only then does `super.invoke` at 139 test mana. A caster who cannot pay loses the old nexus.
3. Clan Ward charges twice on success. It calls `super.invoke` at `Spell_ClanWard.java:276` and again at 300. Each call debits mana (`StdAbility.java:2074-2076`) and runs the armour check (`Spell.java:153-161`). The experience check at 280 also runs after the first debit. A cast-wait or cooldown could stop the second call, but none is set in the code read.
4. Confusion ticks twice. `Spell_Confusion.java:92-93` and 107 both call `super.tick`. `StdAbility.tick` decrements `tickDown` on each call for mob ticks (`StdAbility.java:2848-2859`). The effect therefore lasts about 8 ticks, not the 15 set at line 158.
5. Delude's inclination branch writes the wrong faction. In the chaotic case the range is read from `getInclinationID()` (`Spell_Delude.java:224`), but the change is posted to `getAlignmentID()` (237). unInvoke restores only alignment (`Spell_Delude.java:88-94`), so a lawful or chaotic shift is never reverted.
6. Darkness checks "already here" after the mana debit. `Spell_Darkness.java:122-130` (`super.invoke`, then the check).
7. Delirium sends both messages when either passes. `Spell_Delirium.java:306-309` uses `||`, and both messages are then sent. Confusion, which pairs the same two messages, uses `&&` (`Spell_Confusion.java:152`). This is suspected rather than proven wrong.
8. Delay's phantom caster probably has no mana (inferred, not run). `Spell_Delay.java:116-129` builds a fresh StdMOB and sets level and stats, but not its current state. `testUsageCost` (`StdAbility.java:1925-1931`) would then refuse any mana-costing spell. The phantom caster's mana is not set anywhere in the code read.
9. Cloudkill's messages and comments disagree with the code. The comment at `Spell_Cloudkill.java:100-103` says the mob is put to sleep. The code sets IS_SITTING (104). The unInvoke message "You feel less intoxicated." (119) is a copy-paste leftover.
10. Color Spray borrows EXPRATE as a level-gap threshold (`Spell_ColorSpray.java:208`, 213). EXPRATE is documented as an experience rate (`coffeemud.ini:401-406`). A comment at 193-194 mentions a sleep spell, which is also a leftover.
11. Conjure Nexus's name and room effect suggest healing, but the code restores hit points and movement, and only mana is gained (section 6).
12. Demon Gate counts XLEVEL twice. The level passed in already includes XLEVEL (173), and `determineMonster` adds it again (208). This may be intended.
13. Detect Scrying runs its messages together with no separator (`Spell_DetectScrying.java:148-160`), so the scry and claireaudience lines run into each other.
14. Detect Gold calls `getTotalAbsoluteNativeValue` and discards the result (`Spell_DetectGold.java:146`). The call does nothing.
15. Detect Bombs and Detect Traps duplicate their trap-finding code (`Spell_DetectBombs.java:104-184`, `Spell_DetectTraps.java:104-180`).
16. Dead code: `Spell_Counterspell.java:78` and 149 set a `ticked` field that is never read. `Spell_Confusion.java:84` declares an unused `notAgain`. `Spell_Delirium.java:282-284` has an empty branch. `Spell_Darkness.java:95-96` calls `recoverRoomStats` twice.
17. Several spells change the caller's list. `Spell_Delay.java:155` inserts "CAST" into it, `Spell_Command.java:71` removes from it, and `Spell_ClanDonate.java:129` removes from it.
18. Daydream has no null check on the session (`Spell_Daydream.java:95`, `inhab.session().isAfk()`). The `NoSuchElementException` catch does not cover a null pointer. Whether a live player can have no session is unverified.
19. Conjure Ammunition builds the ammunition before the mana check and roll (`Spell_ConjureAmmunition.java:85-132`, before `super.invoke` at 135), and discards it on failure.
20. Clan Ward's refusal message reads "Your can not enchant" (`Spell_ClanWard.java:288`).

## Takeaways for a Python SUD

- Model a spell as data plus an effect class. The data is a record of id, domain, quality, cost and duration rule. The effect object has the hooks the CoffeeMud spells use:

```python
@dataclass(frozen=True)
class SpellDef:
    id: str
    domain: str
    quality: str            # "malicious", "beneficial_self", ...
    cost: int | Percent | ALL
    duration: Duration      # Exact(n) | Standard(hostile=False) | Forever

class Effect:
    ticks_left: int | None  # None means until removed
    def on_before(self, event) -> bool: return True   # okMessage analogue: may veto
    def on_after(self, event) -> None: ...            # executeMsg analogue
    def modify_stats(self, stats) -> None: ...        # affectPhyStats analogue
    def tick(self) -> None: ...                       # counts down; calls remove() at zero
```

- Costs: borrow the three forms (flat, percent of max, all). Drop the hunger and movement parts unless the game needs them.
- Durations: keep the two defaults, `4L + 40` beneficial and `round(1.1L) + 25` hostile, in ticks. Make the tick unit a game-time choice, since real-time versus turn-based is still open. Count down once per tick, in one place.
- Proficiency: write one function `roll(prof, expertise, adjustment=0)`. Clamp to 5..95, add `expertise // 2`, and treat a negative adjustment as a separate gate, as the code does.
- Level-gap tiers: borrow the Color Spray idea (gap thresholds map to dazed, stunned, unconscious) and the Destroy Object idea (a fixed number of points per level).
- Detect template: one object with four hooks (look trailer, room-change report, sense flag set, one-shot query). Use a Python set for senses instead of bits.
- Delay and Death Warning map well onto a queue of future actions: "run this command when X happens" and "run this in N ticks". Both fit either time model.
- Drop the clan treasury, AFK daydream, snooping, and group-follower rules. These depend on other humans. Replace clan XP with a single-player currency such as standing or favours, if a currency is wanted at all.
- Rethink "other players present" (Delay, `Spell_Delay.java:166-176`) as a witness check against NPCs in the room. Confusion's random retargeting depends on a crowd, so it needs a rule for a lone player.
- When porting, fix the bugs above by construction: charge mana once per cast, tick once per round, write both halves of a paired change, and check refusals before any debit.

## Coverage

- Read in full, all 44 files: every class body from `public class` to the closing brace, excluding blank lines and `@Override` lines. The package line, imports and licence header were not read; they are boilerplate.
- Base class read in full: `Abilities/Spells/Spell.java` (162 lines).
- Shared base read in parts: `Abilities/StdAbility.java` at 355-398 (castingQuality), 419-500 (expertise and range helpers), 698-712 and 772-782 (startTickDown), 899-915 (adjustedLevel), 1441-1540 (proficiencyCheck and unInvoke), 1568-1717 (cost array and usageCost head), 1925-1931 (mana test), 1964-2111 (invoke and components), 2140-2347 (duration formulas and affect copies), 2841-2862 (tick). Not read: the rest of `StdAbility.java` (about 2,600 lines), including `getTarget`, `properTargets`, the fizzle messages and `helpProficiency`.
- Other sources checked: `Abilities/interfaces/Ability.java` constants (46, 651-653, 673, 722-736, 950-1093); `Libraries/Sense.java:1047-1050` (`isInDark`); `Libraries/MUDFight.java:1062` (`recoverTick`, signature only); `core/CMProps.java:681`; `coffeemud.ini:401-406, 1647-1650`; `Common/DefaultCharState.java` and `MOBS/StdMOB.java` (state defaults, for the Delay caveat).
- Skipped: the clan, faction, tracking, hunt and shop libraries, the ability mapper's compounding rules, `ExpertiseLibrary`, and the four Spell_Scry-family spells named by Detect Scrying.
- Catalogue fields (domain, quality, mana override, range) came from a scripted scan of each file's method returns, then checked by reading.
