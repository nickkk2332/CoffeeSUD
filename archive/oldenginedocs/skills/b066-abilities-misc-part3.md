# Abilities/Misc (part 3): Pregnancy to WingFlying

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b066` (Abilities/Misc [part 3/3: Pregnancy .. WingFlying]) · 31 files · 9715 lines of Java.

## Summary

The last third of `Abilities/Misc` holds the mechanics behind many conditions and special effects. The largest is a full pregnancy cycle that ends in an NPC child with inherited traits. Two generic wrapper effects hold timed sets of other effects, and one of them can wait for a trigger before it acts. The rest covers limb scarring that lowers Charisma, regeneration that can cause permanent damage, level and energy drains, social shunning and territory marks, a falling hazard, faction-driven stat effects, delayed scripts, and racial specials such as webs, quills, flight and corpse scavenging. Almost all of them are small `StdAbility` subclasses that change stats through `affect*` hooks, veto or react to messages, and count down on a tick. The most reusable parts for a text game are the timed-condition pattern, the trigger-driven wrapper, and scarring treated as data. Several files have real defects, listed under "Problems and cruft", and a few mechanics assume that other players are present.

## Key files and types

Paths are under `com/planet_ink/coffee_mud/Abilities/Misc/`.

| File | Role | Lines |
|---|---|---|
| Pregnancy.java | Conception (trigger `IMPREGNATE`), gestation timer, labour and birth of an NPC child | 813 |
| TriggeredAffects.java | Wrapper effect: a list of effects that activate or deactivate when a trigger fires | 738 |
| QuestBound.java | Marker effect (implements `Ability` directly, no base class) that resets a quest when bound objects go away or a key player dies | 733 |
| Scarring.java | Per-limb scars kept as text, shown as tattoos, lowering Charisma | 642 |
| TemporaryAffects.java | Wrapper effect: timed list of effects, attributes and behaviours, optionally bound to a room occupant | 637 |
| Regeneration.java | Periodic HP/mana/move regeneration with per-resource rates, plus permanent damage from listed attack types | 436 |
| Sinking.java | Mobs and items sliding down (or up, if reversed) through water rooms | 396 |
| PresenceReaction.java | Effect that runs behaviours, abilities, mood changes and commands while a named target is in the room | 380 |
| Shaming.java | Public shaming: refuses trade and speech with a shamed mob in its area, and answers with snubs | 357 |
| QuickSwipe.java | Thief skill: take a worn item or some coins from a target | 329 |
| Soiled.java | Dirty or soiled condition: halves Charisma, emits smells, ended by cleaning | 317 |
| ScriptLater.java | Delayed-script scheduler on a mob, by clock period, absolute time or tick count | 271 |
| Stigma.java | Faction-driven stat effects for snake-like races | 245 |
| Undead_WeakEnergyDrain.java | Level drain that stacks onto a drain already on the victim; 10-tick adjustment on first use | 240 |
| Undead_EnergyDrain.java | Stronger variant of the level drain; undead targets gain levels instead | 239 |
| TemporaryImmunity.java | Blocks re-application of recently applied abilities for 10 hours | 225 |
| WildTagTurf.java | Room "turf" claim that refuses animal and racial abilities from non-owners | 223 |
| Quills.java | Racial passive: armour penalty and piercing retaliation | 217 |
| ShootWeb.java | Racial ranged binding effect (a web the target must struggle out of) | 211 |
| Truce.java | Peace agreement with a named mob, or with anyone who recently killed the holder | 210 |
| SoundEcho.java | Automatic room and area effect that echoes sound into nearby rooms | 200 |
| Scavenge.java | Racial skill: butcher a corpse into flesh resources | 198 |
| WingFlying.java | Toggle "flap" that sets the flying state for winged races | 190 |
| ThrowFeces.java | Racial attack using a temporary thrown object that may infect and soil | 189 |
| WanderHomeLater.java | Waits, then wanders an NPC and returns it home, or destroys it | 188 |
| SmellsLikeCherries.java | Racial passive: low-intelligence attackers are often repelled | 170 |
| WeakParalysis.java | Paralysis (cannot move), 5-tick adjustment | 160 |
| Undead_ColdTouch.java | Chill touch: sets Strength and Constitution to 3 | 157 |
| Undead_LifeDrain.java | Life drain: caster spends mana equal to level and deals damage | 145 |
| Prisoner.java | Geas that blocks recall, fleeing, ability-based leaving and leaving the area | 135 |
| SlowLearner.java | Cuts experience gained (20 percent by default) | 124 |

## How it works

Line citations are `Abilities/Misc/<File>.java:<line>` under `com/planet_ink/coffee_mud/`. Other folders are given in full from that root.

### Shared pattern

- Classes extend `StdAbility` (`Abilities/StdAbility.java`). `canAffectCode()` and `canTargetCode()` say what the effect can attach to, for example MOBs only in `Pregnancy.java:296-306`.
- Timed copies are made by `beneficialAffect` (`StdAbility.java:2341-2349`) or `maliciousAffect` (`StdAbility.java:2185-2208`). Both copy the ability and start a countdown. `maliciousAffect` takes a tick adjustment from the standard duration and an optional check code, and the check can veto it.
- Stats are never written straight onto a mob. `affectPhyStats`, `affectCharStats` and `affectCharState` modify a working copy, and the engine recomputes stats from base values plus every effect (`Sinking.java:315-323`, `Scarring.java:154-163`).
- Messages go through two phases: `okMessage` may veto by returning false, and `executeMsg` reacts afterwards (`Shaming.java:200-299`, `Truce.java:153-172`).
- MOB ticks (`Tickable.TICKID_MOB`) drive most `tick` methods. The wrapper effects also use `TICKID_PROPERTY_SPECIAL` (`TriggeredAffects.java:676-712`).

### Pregnancy (conception, gestation, birth)

Data model. The state lives in one `/`-separated string (`Pregnancy.java:84-137`): field 0 is the start time in milliseconds, field 1 the due time, field 2 the father's name, field 3 the father's race with an optional `<n>X` prefix giving the number of babies (1 to 9, `:179-217`), field 4 a baby-race override, field 5 the baby's gender (M, F or N). `setStat` exposes these to builders (`:221-248`).

Conception. Trigger `IMPREGNATE` (`:320`). `invoke` (`:743-811`) works like this:

- Gestation is `round(0.75 × (agingChart[1] − agingChart[0]) × monthsInYear)` months, with a minimum of 5 (`:757-759`). The period in ticks is `TICKSPERMUDDAY × daysInMonth × months` (`:754-760`).
- A natural conception (`auto`) sets due time to start plus one period (`:781`). A manual cast sets due time to now and moves start back one period (`:785-788`), so labour begins on the next tick.
- If the target is already pregnant, the existing due time is pulled to now (`:763-779`). This is an "induce labour" path.
- Extra babies: each d100 roll of 1 adds one, up to 9 (`:789-792`). Twins are therefore 1 in 100 and triplets 1 in 10,000.
- Conceptions are posted to the `CONCEPTIONS` channel (`:797-799`).

Per-tick behaviour (`tick`, `:390-741`, MOB ticks only, `:395-397`):

- Days and months left are recomputed from due time (`:406-409`). A 1 in 200 roll triggers a mood (`:410-411`).
- While pregnant, eating food may attach an `Addictions` effect. It is named after the food's material 75% of the time and after the item otherwise (`:364-386`).
- Labour starts when fewer than 7 days remain (`:418`). During labour, a noise message is shown on half the ticks (`:422-428`). After 45 labour ticks (`:430`) the birth happens, about three minutes at the default 4-second tick.
- Before labour: fatigue rises each tick by months-remaining × 100 (`:708-712`). A contraction message fires on a 1% roll when one month or less remains (`:714-716`). A kick needs two 1% rolls when three months or less remain (`:722-725`). Morning sickness, which is only possible in the first two hours of the day, fires on a 1% roll when more than eight months remain (`:727-736`).

Birth (`:430-700`):

- The mother's movement goes to 0 and her hit points are halved (`:495-496`).
- Baby race is the override if it exists, otherwise `CMLib.utensils().getMixedRace(motherRace, fatherRace, false)`, with the mother's race as the fallback (`:517-528`). A newly created race is counted for an achievement (`:526-527`, `:647-652`).
- Gender comes from the stored value or a 50/50 roll (`:458-463`). Name comes from `makeMobName(gender, 2)` (`:529`).
- Baby stats are fixed: Charisma 10, Constitution 6, Dexterity 2, Intelligence 2, Strength 1, Wisdom 1, hit points 1, mana and movement 0 (`:560-572`). Height and weight are divided by 10 (`:568-569`).
- Inheritance: `Prop_Retainable` (`:578-591`), the racism disease (`:592-594`), diseases marked as inherited (`:630-639`), a 50% chance of allergies (`:597-611`), `Prop_SafePet` and an `Age` effect (`:619-629`). Animal-intelligence parents give animal-intelligence babies (`:574-576`).
- Clan: the baby joins rival clans where a parent holds a home-privilege role (`:532-552`).
- Delivery: the baby is placed in the room inside a caged item (`GenCaged`, value 1000, `:653-670`). If the mother's race lays eggs, the item is named as an egg (`:663-677`). Otherwise the item gets a happy-baby `Emoter` behaviour (`:671-677`).
- The mother loses the Pregnancy effect on 95% of births (`:498-514`). A player mother without a soulmate has a 20% chance of depression (`:679-687`).
- Birth is announced on the `BIRTHS` channel (`:691-693`), counted in coffee tables (`:690`) and written as a player-data record under `HEAVEN` (`:698`). What reads that record is unverified.
- If the father is a player, his saved character is loaded at birth (`:446`).

### TemporaryAffects (timed bundle)

`TemporaryAffects.java` holds a list of `(object, [ticks, ...])` pairs. Each object can be an Ability, a Behavior, an `AmbianceAdder` or an `Attrib` flag.

- The misc-text grammar (`:244-392`) reads `+id ticks parms` to add an entry and `-name` to remove one by ID or name. `++` or `+#` means "I mean it" and lets behaviours take parameters. A bare entry replaces everything. `+BINDTO name` binds the bundle to a room occupant.
- `invoke` (`:535-636`) takes a target, a property, a positive integer tick count and optional parameters, with several entries separated by `;` (`:556-567`, `:612-616`). It creates the container with a 10-tick start (`:625`).
- Each entry counts down every tick and is removed at zero (`:504-506`). A binding ends the bundle when the bound occupant has left the room, checked on leave or recall and on each tick (`:453-473`, `:508-524`). Empty bundles unInvoke themselves (`:525-530`).
- Attributes store `[ticks, set flag, previous flag]` (`:376`) and are restored on removal (`:217-220`).
- Callers include Bomb_Sonic (`Abilities/Traps/Bomb_Sonic.java:133-136`), a rejuvenation routine (`Libraries/CoffeeUtensils.java:1610-1613`), scripting (`Common/DefaultScriptingEngine.java:11712-11715`) and potions (`Abilities/Thief/Thief_UsePotion.java:130-183`).

### TriggeredAffects (trigger-driven bundle)

`TriggeredAffects.java` is the same container with an event condition on each entry.

- Each entry has a trigger (a `DefaultTriggerer` pattern), an ONTRIGGER mode of ADD or REMOVE, a tick count, an optional message, and optional IMEANIT and INVOKE flags (`:134-153`). The parser (`:406-522`) accepts the keys ID, PARMS, TICKS, TRIGGER, ONTRIGGER, IMEANIT, MESSAGE and INVOKE. Names resolve through `CMClass.getAbility`, then `getBehavior`, then the `Attrib` enum, then the `find` variants (`:477-485`).
- ADD entries wait dormant in `naffects`. When the trigger completes they go active for their ticks and then return to dormant (`:663-670`, `:686-709`).
- REMOVE entries are active until the trigger completes, then go dormant for their ticks and come back (`:647-653`, `:686-694`).
- Activating an ability uses a hidden stand-in mob (`getFixMob`, `:201-214`), which is only used when the effect is not already present (`:305-325`). Matching a trigger temporarily sets the message source to the stand-in and then restores it (`:613-630`). If a trigger has a message, it is shown in the source's room (`:633-637`).
- A non-special tick is passed to `Tickable` children (`:715-723`). Attribute entries are re-applied each tick (`:725-735`).

### Scarring (limb damage as data)

- Scars are a `;`-separated list of lower-case limb names in the effect's text (`Scarring.java:292`, `:474`). Each scar is a `Tattoo` whose text is built from the body-part name and the wear location (`:310-320`). The map from limb to tattoo is kept in `scarredLimbs` (`:53`).
- Charisma: each distinct scarred limb lowers Charisma by 1. If the count is at least the current Charisma, Charisma becomes 1 (`:154-163`).
- Tattoos last `300 000 / 4000 = 75` ticks (`:52`) and are re-asserted about every 74 ticks while the effect lives (`:330-342`, `confirmScarring` at `:113-141`). The tattoos are therefore a visible timer.
- `invoke` (`:496-641`) for manual use needs a legal warrant or the ABOVELAW security flag (`:521-533`), a wielded weapon unless the caster is a sysop (`:534-541`), and a target who is bound or held (`:547-551`). Amputated limbs are excluded (`:559-578`). The limb is random or chosen by a fuzzy match on the text (`:586-605`). The attack message uses the malicious mask for players and the move mask for sysops (`:610`). Success is a `proficiencyCheck` (`:556`).
- `damageLimb` (`:455-478`) adds the scar. `restoreLimb` (`:410-452`) removes one and deletes the effect when none remain. `unInvoke` removes and heals every tattoo when uninvoking is allowed (dead NPCs are forced to allow it, `:185-186`), and otherwise shortens the tattoo timers to one tick (`:178-216`).
- Other users: Thief_Whiplash (`Abilities/Thief/Thief_Whiplash.java:124-127`), the Restoration prayer (`Abilities/Prayers/Prayer_Restoration.java:177`), and the Archon staff (`Items/Weapons/ArchonStaff.java:358`).

### Regeneration and permanent damage

- The effect acts on every third call to `tick` (`Regeneration.java:38`, `:241-243`). With no custom rates it restores hit points of round(level / 2) when below max, mana of level × 2 and movement of level × 3 (`:253-264`). Each action shows "regenerate(s)".
- The misc text sets per-resource rates (`:268-336`). Resource keys are BURST, HEALTH, HITS, MANA and MOVE (`:115-122`). A value of `amount/ticks` sets how much is restored and how often. BURST runs whole extra MOB ticks (`:177`).
- Vulnerabilities are listed in the same text: `+LEVEL n` sets a minimum weapon level, `+MAGIC` and weapon damage-type or material names go into an exception set (`:277-293`).
- Matching hits add to `permanentDamage` and recompute max state (`:346-386`). The loss is applied through `affectCharState` (`:395-399`) and cleared on death (`:346-350`).
- Matching rules (`:358-382`): weapon damage type, magic-bonus items, weapon level (see Problems) and material. Spells, prayers, chants and songs match "MAGIC".

### Level and energy drains (Undead_*)

- State is `levelsDown` and `direction` (`Undead_WeakEnergyDrain.java:98-99`). An undead target gets direction −1 (`Undead_EnergyDrain.java:226-227`), so the drain adds levels instead.
- `affectPhyStats` lowers level by `levelsDown × direction` and adjusts attack proportionally (`Undead_WeakEnergyDrain.java:102-117`). At level zero the victim is killed by `CMLib.combat().postDeath(invoker(), victim, null)` (`:111-115`).
- `affectCharState` removes hit points, mana and movement each equal to current ÷ level × levelsDown × direction (`:120-131`). `affectCharStats` removes class levels, in the order the classes are listed (`:139-159`).
- Targeting: when the attacker is in melee with a victim who already has a drain, the new drain goes to that victim (`:180-193`).
- Stacking: if a drain is already present, `levelsDown` rises by one (weak only, `:218-219`) and the timer extends by 5 ticks (weak, `:220`) or by the attacker's level (strong, `:218`). New applications use `maliciousAffect` with a +10 tick adjustment for weak (`:230`) and 0 for strong (`Undead_EnergyDrain.java:228`).
- The strong and weak classes are almost identical. The class-level loop condition differs (`Undead_EnergyDrain.java:147` uses `>` where the weak one uses `>=`).

### Social rules: Shaming, Truce, SmellsLikeCherries, Prisoner, TemporaryImmunity

- **Shaming** (`Shaming.java`). The area is found lazily through the legal jurisdiction of the mob's start area and stored by name (`:81-117`). If there is no area it is global (`:105-109`). A mob is shamed in its metro area or the area of its start room (`:111-115`). Refused acts are trade and bank verbs (LIST, VIEW, BUY, SELL, GIVE, BID, DEPOSIT, WITHDRAW, BORROW), channel talk about the mob, social targeting of the mob, and room speech containing its name (`:200-299`). Speech to the mob is answered with a snub but allowed (`:237-239`). A single PC in the room counts as the target (`:232-233`). Each refusal triggers one of twelve quiet snubs (`:140-198`). Users: Archon_Shame, Arrest (`Behaviors/Arrest.java:1915-1930`), Prayer_Forgive (`Abilities/Prayers/Prayer_Forgive.java:132`).
- **Truce** (`Truce.java`). Text with a name means a truce with that one mob (`isTruceWith`, `:124-130`). Empty text means a truce with anyone who recently killed the holder, remembered for 30 seconds (`:96-97`, `:106-122`, `:133-150`). A malicious targeted act between the two is refused and both sides are pacified (`:153-172`). Casting gives 0 ticks, or 3 when automatic (`:194`), and calls `makePeace` (`:199-201`).
- **SmellsLikeCherries** (`SmellsLikeCherries.java`). Automatic. A source with Intelligence below 3 who can smell the holder is repelled when the roll exceeds 10 × (source level − target level − 2 × XLEVEL), so an equal or weaker attacker is always repelled and one ten or more levels stronger never is (`:92-99`). The check is skipped if the source has the killdead security flag or a legal officer or judge is present (`:100-102`). A repelled attack clears the target's victim and makes the target peaceful (`:109-113`).
- **Prisoner** (`Prisoner.java`). A geas that refuses recall (`:83-88`), fleeing (`:90-94`), leaving by ability (`:96-101`) and entering a room in another area (`:103-110`). Used heavily by `Behaviors/Arrest.java` (for example lines 3390 and 3602) and `Abilities/Skills/Skill_JailKey.java:228`.
- **TemporaryImmunity** (`TemporaryImmunity.java`). Holds `(ability ID, timestamp)` pairs. `StdAbility.spreadImmunity` (`StdAbility.java:2351-2365`) adds the caster's ID, and only `Abilities/Skills/Skill_SpreadHate.java:228` calls it. Entries expire after `IMMUNITY_TIME = 36 000 000` ms, which is 10 hours (`:94`), checked every 10 MOB ticks (`:120-128`). While an ID is listed, matching behaviours and effects are removed (`:137-164`), and a matching attack is refused with "seem(s) immune" (`:208-224`). Sheep (`MOBS/Sheep.java:73`) and Thief_SetAlarm (`Abilities/Thief/Thief_SetAlarm.java:363`) also check or add it.

### Quest and presence markers

- **QuestBound** (`QuestBound.java`). Implements `Ability` directly and stubs about 60 methods (`:40-730`). It keeps a set of quest IDs as a `;` list (`:557-575`) and is cloaked when the set contains `*` (`:616-617`). It resets a quest when a shutdown, room reset, or expiry touches the room, the holder or an owned item, or when a key player dies (`:631-665`, `:692-702`). `resetQuest` finds the first matching quest, calls `Q.resetQuest(5)` and clears the IDs (`:667-689`). Users: `Common/DefaultQuest.java` (lines 4062, 4632, 5588), `Behaviors/Arrest.java:1411` (questing mobs are treated as trouble-makers), `Behaviors/GoodGuardian.java:96`, and `Races/StdRace.java:1031` (quest items are kept on drop).
- **PresenceReaction** (`PresenceReaction.java`). The misc text is a `~~`-separated list of `NAME=PARMS` entries (`:84-132`). A name can be a Behavior, an Ability (made non-uninvokable and non-savable, `:103-107`) or a Command, which is queued and run once with `<TARGET>` replaced (`:112-116`, `:278-301`). `=name` sets the target's name (`:90-93`). While the target is in the room the managed objects receive messages and stat effects (`:134-187`, `:304-335`). When the target leaves or dies, or the holder starts following a player, a Mood is reverted and the other objects are destroyed (`:189-224`). `Common/DefaultFaction.java:3010-3039` attaches these when a player enters a room with faction reaction sets.

### Sinking (falling through water)

- A mob in a water-surface room slides one room per tick, down or up if reversed (`Sinking.java:186-228`). It stops if the room is not watery, the mob is water-worthy, flying, weightless, or has no open exit (`:195-200`). A swimmer may tread water with a Skill_Swim check at 25 (`:202-214`).
- Falling is a disposition flag, `IS_FALLING`, set unless the target is water-worthy, flying, treading or weightless (`:315-323`).
- Items sink too (`:229-310`). In a room with more than 100 items the delay is `roll(1, numItems/50)` ticks (`:256-261`). A rideable, boardable item carries a factory rider (`:266-281`).
- Mobs cannot enter indoor or outdoor air rooms unless flying, and get "You can't seem to get there from here." (`:131-139`).
- Callers: `Libraries/MUDTracker.java:2576-2578`, `Locales/UnderWater.java:124,147,164`, the Sailor and Pirate classes, and `Abilities/Spells/Spell_GreaterLevitate.java:174`.
- The misc text REVERSED sets proficiency to 100 and NORMAL sets it to 0 (`:108-122`). Proficiency is used as a flag here.

### Stigma (faction-driven effects)

Stigma is automatic and cannot be uninvoked (`Stigma.java:97-106`). Every 1 to 100 ticks for NPCs, or 1 to 10 for players (`:161-163`), it checks each faction the mob belongs to. The faction value is mapped to the maximum, minimum or midpoint (`:117-126`). If the resulting range differs from the true range, a faction-data entry is stored (`:172-180`). Its stat effects then apply to the mob (`:129-150`). Snake-type races use it (`Races/Snake.java:95`, `CornSnake.java:62`, `Cobra.java:89`, `Naga.java:95`, `GardenSnake.java:62`, `Python.java:89`).

### ScriptLater (delayed scripts)

- The effect holds `(time, script)` pairs. The time is a clock period, a tick count, or an absolute millisecond time. A number greater than now minus 30 seconds is treated as absolute, and smaller numbers as ticks (`ScriptLater.java:210-230`). A script already queued is refused (`:254-255`).
- Due items run as a `DefaultScriptingEngine` script added to the mob (`:183-193`). The schedule serialises to XML so that it persists (`:95-159`). Callers: Prayer_Prophecy (`:189`, `:275`), Thief_Runecasting (`:392`) and Thief_FindRunaway (`:262`).

### Thief, combat and racial specials

- **QuickSwipe** (`QuickSwipe.java`). Refused if the target cannot be fought or its levelDiff is over 15 (`:165-170`), or if the target is the thief (`:171-175`). The levelDiff used for the roll is target level − (thief level + code + 2 × XLEVEL) (`:165`). An asleep or unaware target sets levelDiff and discovery chance to 100 (`:181-185`). Half the time a target item is picked: visible worn items on fingers, wrists, head, eyes, mouth or in hands, weight 5 or less, and level at or below the thief's (`:188-207`). Otherwise a pack item under weight 5 outside a container (`:209-219`). Coins are 10% of total value, 5% if levelDiff is above 5 and 3% above 10, times a random fraction (`:244-251`), floored at the thief's class level (`:252-253`). A stolen item gets `Prop_PrivateProperty` naming the thief for 60 seconds (`:313-319`).
- **ShootWeb** (`ShootWeb.java`). Range 0 to 5 (`:76-85`). The target gets `IS_BOUND` (`:123`). Each struggle subtracts strength plus level from the web's remaining amount, and the web breaks below zero (`:139-147`). The starting amount is level × 10 × target speed (`:194-195`). The duration adjustment is level × 10 (`:198`).
- **Quills** (`Quills.java`). Automatic and permanent. Armour changes by −(1 + XLEVEL) (`:181`). When an adjacent creature acts against the holder with hands or movement, it retaliates at most once per tick (`:150`, `:166`). The retaliation chance is about (100 − 3 × Dexterity)% (`:152`). Damage is roll(1, (level + XLEVEL + 2 × X1 level) / 4) piercing (`:162-163`).
- **ThrowFeces** (`ThrowFeces.java`). Creates a temporary thrown object (`:144-151`) with a 5% + XLEVEL% chance of attaching one of four diseases (`:153-158`). If it hits, the target gets Soiled (`:166-172`). While the object is in play, the thrower's weapon damage is rolled as 1 to 2 (`:116-119`) and the thrower's damage stat is set to 2 (`:110`).
- **WildTagTurf** (`WildTagTurf.java`). Rooms only. A claim records the owner's name or clan (`:212-216`) for (1 + level) real-time hours plus XLEVEL days (`:217-218`, `CMProps.getTicksPerHour` and `getTicksPerDay`). A non-owner who is not in the clan and uses an animal-affinity or racial ability in the room is refused, and told "You definitely aren't allowed to do that on X's turf", when that intruder can smell the room (`:109-119`). Sniffing shows the claim (`:128-143`). The owner can untag with UNTAG (`:162-168`), and a claimant at least as high in level can override it (`:176-186`). Cities and indoor wood or stone rooms are refused (`:195-201`).
- **WingFlying** (`WingFlying.java`). Needs a wing body part (`:147-151`). Flapping removes the old effect and adds a 9999-tick effect whose text records `FLYING=true` or `FLYING=false` (`:154-180`). The flying disposition applies unless the mob is asleep or sitting (`:113-118`). Losing the wings removes the effect (`:132-139`).
- **Scavenge** (`Scavenge.java`). Corpses only, and a "FAKE" secret identity is refused (`:116-121`). A player corpse can only be scavenged by its owner, when it has contents (`:123-129`). Results are the race's flesh resources (`:144-151`), with extra copies equal to level ÷ 8 of the first flesh resource (`:152-164`). Consumption or contact diseases on the corpse are copied onto food and drink results (`:165-184`). The corpse is destroyed (`:188`).
- **SoundEcho** (`SoundEcho.java`). Automatic on rooms and areas. For a sound message that carries the sound mask on both sides and does not already contain "You hear an echo: " (`:132-136`), an echo goes to rooms within range ÷ 2 with a direction, correct at accuracy% and random otherwise (`:158-172`). It goes to occupied rooms within full range as a faint echo (`:177-193`). Defaults are range 10 and accuracy 50 (`:104-111`). Text is filtered for a shared blind mob named "Someone" (`:96`, `:115-127`, `:142`).
- **WanderHomeLater** (`WanderHomeLater.java`). Options are read from the misc text: areaok, destroy, ignorepcs, ignorefollow, respectfollow, once, minticks and maxticks (`:83-95`). A random wait is used between wanders (`:94`, `:126-129`). When away from home, `CMLib.tracking().wanderAway(mob, mindPCs, true)` runs (`:161-162`). That method moves the mob up to ten times while it stays in the room, stopping early when PCs are present and `mindPCs` is set (`Libraries/MUDTracker.java:1184-1202`), and then heads toward the start room (`:1203-1206`). The effect ends when it is once-only and at home, when dead, or when a respected follow starts (`:131-172`). Users: Spell_SummonMonster (`:132-134`), Thief_RunningFight (`:180`), Thief_SetAlarm (`:351`), Prayer_MalignedPortal (`:288`), and `Libraries/MUDTracker.java:391-394`.
- **Soiled** (`Soiled.java`). Halves Charisma (`:104-110`). For player characters, hygiene is raised to 10 000 whenever it is lower (`:262-264`). `MOBS/StdMOB.java:949-955` then lowers Charisma by hygiene ÷ 5000 (`HYGIENE_DELIMIT`, `Common/interfaces/PlayerStats.java:1303`), which is about 2. Each tick there is a 1% chance of a smell emote to occupants who can smell it (`:243`, `:267-278`). It ends on a hygienic action, on entering water, or on putting the item into a liquid container (`:188-207`). The duration is TICKS_ALMOST_FOREVER (`:298`).
- **WeakParalysis** and **Undead_ColdTouch**: WeakParalysis adds `CAN_NOT_MOVE` (`:113`) for 5 ticks (`:149`). ColdTouch sets Strength and Constitution to 3 (`:104-105`) with no tick adjustment (`:148`).
- **Undead_LifeDrain** (`Undead_LifeDrain.java`). The caster spends mana equal to level (`:124-130`). The target takes damage equal to level, halved on a successful save (`:131-133`). Undead targets are not flagged as malicious (`:125`) and get a beneficial fizzle (`:137-138`).
- **SlowLearner** (`SlowLearner.java`). An experience-change message from the holder is reduced by `changePct` (default 0.20, `:94`, `:117-121`).

## Content worth mining

**Racial abilities.** The table shows which races grant each effect, taken from `racialAbilityNames` in `Races/*.java`.

| Ability | Effect in short | Races (examples, file:line under `Races/`) |
|---|---|---|
| WingFlying | flap-toggle flight | Owl:95, Pegasus:95, WereBat:95, Gargoyle:51, Tiefling:108, Birdman:121, Manticore:95, Robin:59, Deva:65, Dragon:101, Bat:109, Raven:95, Devil:168, Pixie:117, Wyvern:116 and about 15 more |
| Quills | armour penalty, retaliation | Porcupine:90 |
| ShootWeb | ranged web binding | Spider:95, Tarantula:102 |
| ThrowFeces | temporary thrown object | Orangutan:89 |
| Scavenge | corpse butchery | Rat:90, WereRat:95, Scarab:101, Bear:101, GrizzlyBear:101, PolarBear:101, DireBear:81, Cub:89, WereBear:101 |
| WildTagTurf | room claim | Wolverine:51, Dog:117 |
| SmellsLikeCherries | repel weak attackers | Millipede:100 |
| Stigma | faction-driven effects | Snake:95, CornSnake:62, Cobra:89, Naga:95, GardenSnake:62, Python:89 |

**Command trigger words** (the verbs a text parser can map to each ability): IMPREGNATE, SCAR, REGENERATE, DRAINWEAKENERGY, DRAINENERGY, DRAINLIFE, COLDTOUCH, SOIL, DECLARETRUCE, WPARALYZE, WILDTAG (with WTAGTURF, WTURFTAG, WILDTAGTURF, WILDTURFTAG), QUICKSWIPE (and QSWIPE), FLAP, SCAVENGE, SHOOTWEB, QUILLS, THROWFECES, TEMPORARYAFFECTS, TRIGGEREDAFFECTS. Sources are each file's `triggerStrings()`.

**Shaming snubs** (`Shaming.java:150-197`, paraphrased). There are twelve quiet non-verbal responses: looking away, avoiding, ignoring (two outcomes), frowning, disapproving looks, a silent shame, scowling, glowering, turning the back, turning away, and avoiding eye contact. Each is shown with the no-message "quiet movement" flag.

**Smell and dirt lines** (`Soiled.java:152-179`). Five crude but non-explicit remarks about a soiled character, and one plain "smells dirty" or "stinks" line for items. Paraphrased here.

**Four infections** for the thrown object (`ThrowFeces.java:154-156`): Tetanus (the class is spelled `Disease_Tetnus`), Lyme, fever and a general infection. These can be reused as a table of random illnesses.

**Pregnancy numbers** (`Pregnancy.java`): gestation rule, labour of 45 ticks, babies at CHA 10 and INT 2, the 1-in-100 twin roll, and the phase text for morning sickness, kicks and contractions, all reusable as a pregnancy event table.

**Scavenge yield** (`Scavenge.java:144-184`): flesh from the race's resource list, bonus copies at level ÷ 8, and disease transfer into food. This is a good simple model for butchering.

**Ability parameters.** Quick reference: Weak drain 10-tick adjustment, strong drain 0 (`Undead_*`); web duration level × 10 (`ShootWeb.java:198`); WildTagTurf (1 + level) hours plus XLEVEL days; wing flight 9999 ticks (`WingFlying.java:174`); Truce 30 seconds of memory (`Truce.java:114`); TemporaryImmunity 10 hours (`TemporaryImmunity.java:94`); scar tattoo 75 ticks (`Scarring.java:52`).

## Notable design ideas

- **Two wrapper effects with different triggers.** TemporaryAffects is a plain timed bundle. TriggeredAffects adds an ADD or REMOVE mode, so an effect can wait for an event, and an effect can be taken away by an event (`TriggeredAffects.java:647-694`). A text game can express "the next time the player does X, do Y for three turns" with the same structure.
- **Birth as a deferred event.** Conception stores the due date, and birth spawns an NPC with inherited traits (`Pregnancy.java:517-639`). The NPC inherits allergies, diseases, safe-pet status and clan roles from the parents.
- **Scars as a counter, not a state machine.** Charisma falls with the number of scars, and tattoos refresh on a timer so they show while the effect lasts (`Scarring.java:154-163`, `:330-342`).
- **Opposite-direction drains.** The same drain adds levels to undead (`Undead_EnergyDrain.java:226-227`). That is a neat use of one sign.
- **Deterrence by level gap.** SmellsLikeCherries makes the chance of repelling an attacker a linear function of the level gap (`SmellsLikeCherries.java:99`). It is easy to tune and easy to explain.
- **Territory marks that can be sniffed.** WildTagTurf lets anyone smell the claim, and blocks specific ability domains for non-owners (`WildTagTurf.java:109-143`).
- **Sound propagation in two rings.** SoundEcho sends a directional echo to a near ring and a faint echo to a far ring that only reaches occupied rooms (`SoundEcho.java:158-193`).
- **Presence-bound reactions.** PresenceReaction runs a set of behaviours and mood changes for as long as one target stays in the room, then reverts them (`PresenceReaction.java:189-224`).
- **Quest-bound objects.** A quest object carries its quest ID and resets the quest when the bound object expires, its room resets or its holder dies (`QuestBound.java:631-689`).

## Problems and cruft

Ordered roughly by how much they change behaviour.

1. **Regeneration weapon-level test is unguarded** (`Regeneration.java:364`). `hurts = hurts || (W.phyStats().level() >= weapMinLevel)` runs even when `weapMinLevel` is −1, its default (`:127`, `:272`). Once any exception is set (for example `+MAGIC`), every weapon hit counts as permanent damage. The guard at `:355` only checks that some exception exists.
2. **Scarring's scarred-part counter is never positive** (`Scarring.java:309`). `scarredParts[c]--` decrements, so `unaffectedLimbSet` never takes the scarred branch (`:375-389`). It falls through to the plain loop (`:394-396`), which lists every part and ignores the scars. Consequences: the same limb can be scarred again; left and right entries are never produced; and the "nothing unscarred" refusal (`:579-584`) is effectively unreachable. `invoke` also strips the LEFT or RIGHT prefix before matching (`:513-517`), so left or right choice has no effect.
3. **Truce likely never matches the other party** (`Truce.java:195-197`). After the effect is applied to the target, its name is set to `target.Name()`, not the caster's. The check at `:160-161` then compares against the caster's name, so a single-target truce probably never matches. Unverified at runtime. Also, `setMiscText` stores `capitalizeAndLower(name)` (`:102`), but `isTruceWith` compares with the raw name (`:129`). Names with internal capitals will not match.
4. **QuickSwipe may have an inverted sign** (`QuickSwipe.java:165`, `:225`). `proficiencyCheck` adds a non-negative adjustment to the chance (`Abilities/StdAbility.java:1480-1481`). A higher-level target therefore makes the theft easier, which looks opposite to intent. A sleeping target gives a levelDiff of 100 and an automatic success. Verify the intent before changing it.
5. **Drain sentinel reuse** (`Undead_WeakEnergyDrain.java:107`, `:113-116`). `levelsDown` is set to −1 as a death flag and then used in the attack adjustment on the same pass, so that pass adds back the attack instead of removing it. Later recalculations return at `:107` and apply nothing. Use a separate flag.
6. **WanderHomeLater default is wrong** (`WanderHomeLater.java:90`). `respectfollow` defaults to the value of `ignoreFollow`, not to its own value. Setting `ignorefollow` therefore also turns on `respectfollow`.
7. **ScriptLater uses the wrong object** (`ScriptLater.java:237`). After initialising, it re-fetches the effect from `givenTarget` instead of `target`. That is a null-dereference risk when `givenTarget` is null, and it can read the wrong mob.
8. **Shaming loses its area on one path** (`Shaming.java:102-103`). `setMiscText` clears `shameArea` (`:122`) and restores it only if the name lookup succeeds. `invoke` restores it explicitly (`:346-347`), but this path does not. If the lookup fails, `:111` dereferences null.
9. **Regeneration drops the last option** (`Regeneration.java:280`). When no space follows a `+` token, `substring(x+1)` with x = −1 returns the whole string, so a trailing `+MAGIC` is ignored.
10. **Unreachable branch** (`Regeneration.java:144`). The branch tests `td[2] == 1`, but the parser only writes 0 (`:324`) or leaves the array at length 2 (`:326`). The "absolute" branch therefore never runs.
11. **Enum ordinals used as loop counts** (`Regeneration.java:194`, `:207`, `:220`). The loops run `RecType.HITS.ordinal()` (2), `MANA.ordinal()` (3) and `MOVE.ordinal()` (4) times. These are arbitrary counts. `changed = ...` at `:195` also overwrites rather than accumulates.
12. **QuestBound changes state in okMessage** (`QuestBound.java:631-646`). A quest can be reset by a message that another listener later vetoes. The class also stubs about 60 `Ability` methods rather than extending `StdAbility`.
13. **Sinking null check** (`Sinking.java:288`). `getOwnerName().length()` is called without the null check used at `:272`.
14. **Disease ID mismatch** (`ThrowFeces.java:155`). The string `Disease_Tetnus` matches its class file (`Abilities/Diseases/Disease_Tetnus.java:35`). `Items/Weapons/StdWeapon.java:279` requests `Disease_Tetanus`, which has no class (grep found none). This is outside this bucket but is the same family of data.
15. **ShootWeb comment disagrees with code** (`ShootWeb.java:134-141`). The comment says the web prevents "ANYTHING except sleep". The code blocks only hands and movement. `flags()` (`:112-115`) also drops the base flags.
16. **Pregnancy wording and dead code.** The attack message reads "imgregnate(s)" (`Pregnancy.java:794`). The induce-labour message reads "birthifies" (`:768`). There is an empty `if` (`:801-804`) and a no-op `I.text();` (`:678`). The father's race field also carries the baby count (`:179-217`).
17. **Other typos.** "unscarredn" (`Scarring.java:582`). The fizzle in Truce says "fall asleep" (`Truce.java:205`). `FasterRecovery` appears in a log message in Regeneration (`:330`).
18. **Stale imports and shadowing.** `org.w3c.dom.Text` is imported and unused in TriggeredAffects (`:22`) and TemporaryAffects (`:22`). The inner class `TriggeredAffect` shadows the interface name, which is why `TriggeredAffects.java:86` is fully qualified.
19. **Unused or odd code in QuickSwipe.** `timesPicked` is never called (`:110-128`). The chance uses `Math.random()` (`:251`) instead of `CMLib.dice()`. The branch at `:245-246` is redundant. `ExpertiseLibrary` is imported and unused (`:14`).
20. **SoundEcho phrasing** (`SoundEcho.java:189` against `:192`). One branch uses `getFromCompassDirectionName` and the other uses `getDirectionName`.
21. **TemporaryImmunity change detection** (`TemporaryImmunity.java:151`). It compares counts (`fct != numEffects()`), so a replaced effect of the same count is missed.
22. **Soiled hygiene forced to a constant** (`Soiled.java:262-264`). Setting hygiene to 10 000 each tick ties a PC's condition to one number in `PlayerStats`. Its meaning is defined outside this bucket (unverified).
23. **Empty catch blocks and duplicates.** `PresenceReaction.java:76-77` sets `reactToM` twice. Empty catch blocks at `:210-212` and `:294-296`. Undead_EnergyDrain and Undead_WeakEnergyDrain are near-copies of each other.
24. **damageLimb ignores its flag** (`Scarring.java:455`). The `intentional` parameter is never used.

**Assumptions about other players.** These mechanics assume more than one player or are written around the player-versus-NPC split. Shaming counts PCs in the room (`Shaming.java:233`). PresenceReaction watches a named target (`PresenceReaction.java:313`). WanderHomeLater and `wanderAway` respect PCs (`MUDTracker.java:1194`). Truce is about agreements between two named mobs. Stigma checks players far more often than NPCs (`Stigma.java:163`). QuestBound's key-player logic is written for a multi-player quest economy.

## Takeaways for a Python SUD

- **One condition base with stat hooks.** Keep the affect pattern: a `Condition` has `ticks_left`, an `on_tick(dt)` method, and `modify(stats)`. Derived stats are recomputed from base values plus all active conditions. Use game-time units (hours or days) rather than tick counts, and convert once at load time, as `CMProps.getTicksPerHour` does for flight and turf durations.
- **Injuries as a set, not a timer.** Store scars as `injuries: set[str]` with Charisma = base − len(injuries) (floor 1). This removes the timer and the duplicate-limb bug (Problem 2). Left and right can be enum values, not string prefixes.
- **Trigger-driven bundles.** Use a `TimedBundle(effects, trigger, mode, duration)` where mode is `ADD` or `REMOVE`. Triggers can be predicates over game events in the single-player loop. This covers "wait for the next X, then Y" without the message-matching machinery.
- **Gestation as an event.** `Pregnancy(conceived_at, due_at, father_id, babies, race_override)` with a daily check and a `birth()` function that makes an NPC from a pure `mix_races(a, b)` rule. Keep the inheritance list short: traits, allergies and clan are enough.
- **Deterrence formula.** SmellsLikeCherries' rule (repel chance = clamp(1 − Δ/10, 0, 1) with Δ the level gap) is a cheap, explainable way to make a creature avoid fights with much weaker prey.
- **Scheduler.** Replace ScriptLater's XML with a `heapq` of `(due_time, callback)`, deduplicated by key.
- **Sound.** Copy SoundEcho's two-ring idea for a text world: a near ring with direction and a far ring with a faint echo only where someone is.

What to drop for a single-player game: Truce (no peace with other humans), QuestBound's key-player logic, the shared-player checks in Shaming and WanderHomeLater, the database and channel writes in Pregnancy, the hidden stand-in mob in TriggeredAffects, the MSP sound and the factory riders. Keep Shaming as a rule about NPC reactions to the player, with area reputation as a dictionary keyed by region.

## Coverage

- **Read in full (31 of 31):** Pregnancy, TriggeredAffects, QuestBound, Scarring, TemporaryAffects, Regeneration, Sinking, PresenceReaction, Shaming, QuickSwipe, Soiled, ScriptLater, Stigma, Undead_WeakEnergyDrain, Undead_EnergyDrain, TemporaryImmunity, WildTagTurf, Quills, ShootWeb, Truce, SoundEcho, Scavenge, WingFlying, ThrowFeces, WanderHomeLater, SmellsLikeCherries, WeakParalysis, Undead_ColdTouch, Undead_LifeDrain, Prisoner, SlowLearner. For 18 of them the package, import and licence header (lines 1 to 33) were not read; they contain no mechanics.
- **Skimmed outside the bucket for context:** `Abilities/StdAbility.java` (proficiencyCheck, maliciousAffect, beneficialAffect, spreadImmunity), `core/collections/SVector.java` (copy-on-write list, so removal during iteration is safe), `core/CMStrings.java` (capitalizeAndLower), `Libraries/MUDTracker.java` (wanderAway), `MOBS/StdMOB.java` (hygiene effect on Charisma), `Common/interfaces/PlayerStats.java`, `core/CMProps.java` (default tick 4000 ms), `Common/DefaultFaction.java` (presence reactions), and grep-level call sites in Races, Abilities, Behaviors, Common and Locales.
- **Not verified at runtime.** Behaviour is described from code reading only. Items marked unverified above need a run to confirm.
