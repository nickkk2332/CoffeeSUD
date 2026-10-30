# Abilities/Skills part 2: crew, slavery, restraint, sea, tracking and dirty-fighting skills

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b080` (Abilities/Skills [part 2/4: Skill_CulturalAdaptation .. Skill_InterceptShip]) · 41 files · 9123 lines of Java.

Conventions: a bare `Skill_X.java:n` is a file in `com/planet_ink/coffee_mud/Abilities/Skills/`. Other citations are relative to `com/planet_ink/coffee_mud/`. Anything I did not check is marked "unverified".

## Summary

The sea and crew skills (Hire Crewmember, the hard-turn skills, Intercept Ship, Dead Reckoning) treat a ship as a game object that carries its own effects, course and crew. Enslave and Hire Crewmember turn an NPC into a companion with a master or a role, and Enslave takes orders from ordinary speech and tracks a rebellion meter. Hand Cuff, Diary and the six honorary degrees use a skill as a small state machine: a restraint with a struggle budget, a passive listener that writes into a book, and a title that grants a temporary class context. The four "Find ..." skills walk a trail computed by the tracking library. The rest are melee and dirty-fighting moves, utility skills (decipher, encrypted writing, poison identification) and passives. For a single-player SUD the useful parts are the skills that persist across turns, the ones that veto or amend messages, and the single proficiency roll with explicit modifiers.

## Key files and types

| File | Role | Lines |
|---|---|---|
| Skill_Enslave.java | NPC slave: master, orders by speech, anger and rebellion, price | 716 |
| Skill_HireCrewmember.java | Hires a sailor at a pub near water; assigns a crew role, gear and a Sailor behaviour | 703 |
| Skill_Diary.java | Passive event log written into a Book item | 500 |
| Skill_HandCuff.java | Binds a prisoner with a struggle budget; prisoner follows captor | 346 |
| Skill_HonoraryDegreeCommoner.java | Base for honorary degree titles; counts a character's skills per class family | 345 |
| Skill_InterceptShip.java | Plots a course to a named enemy ship; applies a speed effect | 333 |
| Skill_FindClanShip.java | Trail to a clan-owned ship | 328 |
| Skill_FindShip.java | Trail to the character's own ship | 327 |
| Skill_FindClanHome.java | Trail to a clan-owned room | 301 |
| Skill_DecipherScript.java | Timed decryption of an "Encrypto" item | 298 |
| Skill_FindHome.java | Trail to a room owned by the character | 297 |
| Skill_HardToPort.java | Hard turn to port on a ship in combat; ship dodge lock | 265 |
| Skill_HardToStern.java | Two starboard steps (180 degrees) on a ship in combat | 265 |
| Skill_HardToStarboard.java | Hard turn to starboard on a ship in combat | 263 |
| Skill_Dirt.java | Kick dirt to blind a target; room must allow dirt | 257 |
| Skill_HighJump.java | Jump up or in a direction; extra jumps in air | 251 |
| Skill_Groin.java | Groin kick: damage, dazed effect, repeat block | 248 |
| Skill_ImprovedRevoke.java | Removes the character's own spells, songs, prayers and chants from a target | 208 |
| Skill_DevourCorpse.java | Auto skill: allows eating corpses, fills hunger | 199 |
| Skill_Feint.java | Combat feint: target DEX set to 0 and armour shifted for 2 ticks | 191 |
| Skill_FoulWeatherSailing.java | Ship effect that vetoes weather messages | 178 |
| Skill_DeadReckoning.java | Reports the first direction to the nearest landing | 174 |
| Skill_Disarm.java | Knocks a wielded weapon out of an opponent's hands | 173 |
| Skill_Guildmaster.java | Passive: stops like-class attackers hitting the character | 160 |
| Skill_Diving.java | Passive: reduces fall damage when falling into water | 157 |
| Skill_Dodge.java | Passive: dodges one melee hit per MOB tick | 148 |
| Skill_Haggle.java | Temporary CHA boost while a forced BUY or SELL runs | 145 |
| Skill_CulturalAdaptation.java | Passive: multiplies positive non-alignment faction gains | 139 |
| Skill_DeepBreath.java | Passive: tolerates gas or water damage for a budget of events | 138 |
| Skill_IdentifyPoison.java | Lists poison effects on an unworn item | 137 |
| Skill_IndoorRiding.java | Passive: heavy mounts pass through doors; movement cost | 133 |
| Skill_EncryptedWriting.java | Writes in the "Encrypto" ink language (extends Skill_Write) | 130 |
| Skill_Explosive.java | "Explosive Touch": heavy burst damage on a touched target | 121 |
| Skill_EagleEyes.java | Passive: raises the save-overlooking stat | 105 |
| Skill_Flee.java | Flee command with a combat check | 104 |
| Skill_HonoraryDegreeBard.java | Honorary degree subclass, base class "Bard" | 57 |
| Skill_HonoraryDegreeFighter.java | Honorary degree subclass, base class "Fighter" | 57 |
| Skill_HonoraryDegreeMage.java | Honorary degree subclass, base class "Mage" | 57 |
| Skill_HonoraryDegreeThief.java | Honorary degree subclass, base class "Thief" | 57 |
| Skill_HonoraryDegreeCleric.java | Honorary degree subclass, base class "Cleric" | 56 |
| Skill_HonoraryDegreeDruid.java | Honorary degree subclass, base class "Druid" | 56 |

Base classes: 30 files extend `StdSkill`, four Find skills extend `StdAbility` directly (`Skill_FindShip.java:36`), `Skill_EncryptedWriting.java:35` extends `Skill_Write`, and the six degree subclasses extend `Skill_HonoraryDegreeCommoner` (`Skill_HonoraryDegreeBard.java:35`).

## How it works

### 1. Shared mechanics

**Shape of a skill.** Each class sets `ID()`, `triggerStrings()` (the command words, for example `Skill_HardToPort.java:73` maps `HARDTOPORT`), `classificationCode()` (`ACODE_SKILL` plus a domain, for example `Skill_Enslave.java:91` uses `DOMAIN_CRIMINAL`), `abstractQuality()`, and the `invoke(mob, commands, givenTarget, auto, asLevel)` entry point. `abstractQuality()` also decides the duration path: malicious effects use the malicious tick-down and the rest use the beneficial one (`Abilities/StdAbility.java:2312-2338`).

**The proficiency roll.** Almost every `invoke` ends in `proficiencyCheck(mob, adjustment, auto)` (`Abilities/StdAbility.java:1441-1485`):

- `pctChance` starts at the skill's proficiency plus ability-adjustment stats, then gains `2*XLEVEL`. It is clamped to 5..95 unless the DIS955RULE security flag is set, and then gains `XLEVEL/2`.
- A non-negative `adjustment` is added to `pctChance`.
- A negative `adjustment` is not subtracted from the chance. It is a separate gate: the attempt fails outright when `roll > 100 + adjustment`, so an adjustment of -30 is a flat 30% failure. The normal `roll < pctChance` test then applies.
- `auto` skips the roll and succeeds.
- `rollPercentage()` returns 1..100 (`Libraries/Dice.java:409-411`).

Adjustments used in this bucket:

| Skill | Adjustment (file:line) | What it means |
|---|---|---|
| Dirt | -(target DEX x 3) + 2 x XL (`Skill_Dirt.java:237`) | target DEX 10 is a flat 30% gate |
| Groin | -levelDiff - (target DEX - own STR) (`Skill_Groin.java:209`) | levelDiff = 10 x (target level - (own level + 2 x XL)) when positive (`:201-205`) |
| Disarm | -levelDiff, levelDiff = 5 x (victim level - (own level + 2 x XL)) when positive (`Skill_Disarm.java:147-149`) | |
| Feint | -target DEX (`Skill_Feint.java:174`) | |
| Dodge | DEX - 93 + XL (`Skill_Dodge.java:127`) | DEX 18 gives -75, a 75% flat gate |
| Decipher Script | (adjusted level - cipher level) x 10 when below (`Skill_DecipherScript.java:268-277`) | |
| Improved Revoke | 10 - 10 x (effects revoked) (`Skill_ImprovedRevoke.java:187`) | |
| Deep Breath | 2 x CON (`Skill_DeepBreath.java:116`) | positive, so it adds to the chance |
| Flee | 10 x XL, only in combat (`Skill_Flee.java:89`) | positive |

**Durations.** The fourth argument of `beneficialAffect` and `maliciousAffect` is a tick adjustment (`Abilities/StdAbility.java:2341-2349`, `:2312-2338`). A positive value is an exact tick count. Zero selects the standard length, `casterLevel*4 + 40` ticks, adjusted by the library's hook. A negative value is a percentage of the standard length. Examples: Feint uses 2 ticks (`Skill_Feint.java:184`), Decipher Script 20 (`Skill_DecipherScript.java:285`), the hard-turn ship lock 6 (`Skill_HardToPort.java:245`), Diary `Integer.MAX_VALUE/3` with `makeLongLasting()` (`Skill_Diary.java:485-488`).

**Messages: veto and amend.** Every action is a `CMMsg`. A skill sits on the target as an effect and gets `okMessage` first, where it may veto (return false) or change the message (`msg.setValue(0)`), and then `executeMsg` on every listener. Examples: Hand Cuff vetoes movement and hand actions (`Skill_HandCuff.java:115-199`), Groin vetoes sight, hand, mouth and movement actions while dazed (`Skill_Groin.java:110-134`), the hard-turn skills zero weapon damage to the ship (`Skill_HardToPort.java:100-116`), Foul Weather Sailing vetoes a weather message to the ship (`Skill_FoulWeatherSailing.java:92-118`). Skills also add trailer work: Honorary degrees reset the class override after the message (`Skill_HonoraryDegreeCommoner.java:297-308`), and the Find skills add a trailing look (`Skill_FindShip.java:207-208`).

**Passives.** Nine classes set `isAutoInvoked()` true and `canBeUninvoked()` false, so they are never on the command list and cannot be removed: Honorary degrees (`Skill_HonoraryDegreeCommoner.java:87-96`), Guildmaster (`Skill_Guildmaster.java:81-91`), Dodge (`Skill_Dodge.java:87-97`), Devour Corpse (`Skill_DevourCorpse.java:81-91`), Diving (`Skill_Diving.java:82-91`), Cultural Adaptation (`Skill_CulturalAdaptation.java:82-91`), Deep Breath (`Skill_DeepBreath.java:82-91`), Eagle Eyes (`Skill_EagleEyes.java:51-61`), Indoor Riding (`Skill_IndoorRiding.java:76-85`). The honorary degrees, Guildmaster and Dodge depend on the character, not on a command.

### 2. Crew: hiring and roles (Skill_HireCrewmember)

**Data.** `shipName` and `type` (a `CrewType` enum: REPAIRER, TACTICIAN, CAPTAIN, DEFENDER, TRAWLER; `Skill_HireCrewmember.java:123-130`) are stored in the misc text as `ship;TYPE` (`:137-146`). Crew membership is an effect on the NPC with the same ship name (`isCrew`, `:275-279`). The skill's ability code is set to the hiring skill's XL on promotion to crew (`:459`).

**Hiring (invoke, `:571-702`).**

- The hirer must be in a pub: `isPub` (`:543-568`) looks for a shop with an alcoholic item in stock. The refusal text is "You must in a pub to hire a sailor." (`:589`, the odd word order is in the code).
- Water must be near: `findTrailToAnyRoom` on water-surface rooms within `baseWaterRange` (8) + XL + XMAXRANGE (`:592-600`, `baseWaterRange` at `:49`).
- Level band: `minLevel = max(1, level-10)`, `range = 5`, or 1 if `minLevel + 5 > level` (`:578-582`). Recruit level = `roll(1, range, minLevel)` (`:584`), which is minLevel plus 1..range.
- Price: `medLevel = minLevel + ceil(range/2)` and cost `medLevel x 10` in the area's currency (`:602-606`). The cost is paid only when the hiring is not automatic (`:626-627`).
- Race: `CMLib.login().raceQualifies(mob, Area.THEME_FANTASY)` picks among qualifying races (`:629-630`). Name: one of five patterns (`:633-650`): a random name, "sailor <name>", "a sailor", "an <race>", "a sailor <race>".
- Stats: the NPC is built from `GenMob` and given level-based armour, attack, damage and speed from `CMLib.leveler()` (`:628-666`). Tattoos `SYSTEM_SUMMONED` and `SUMMONED_BY:<name>` mark it (`:668-669`).
- Success: the move is sent through `R.okMessage` (`:685-693`), the crew effect is added with `type` left null (`:690-691`), and the NPC is set to follow the employer (`:692`).

**Role assignment (tick, `:328-525`).** While the NPC is on a water-based ship area, it draws a role at random: `CrewType.values()[roll(1,5,-1)]` (`:411`), re-rolling up to 10,000 times while a role is at its cap (`:408-438`). Gear and "I am ready for duty." happen later, once it is no longer following anyone (`:452-515`). Caps (`:395-399`):

- captain 1, tactician 1;
- defenders = decks, trawlers = decks;
- repairers = ceil((rooms - decks) / 2).

Rooms and decks are counted over the ship's area (`:353-384`) and the hiring level adds `level/10` rooms and half as many decks (`:386-392`). A full ship gets "This ship already has the maximum crew." (`:403`) or "This ship already has enough crew." (`:442`). Once the role is set, the NPC gets gear (`:464-513`) and says "I am ready for duty." (`:514`). If it stops being on the ship and is not following, it says "I guess I'm fired." (`:518-523`).

**Behaviour.** `getSailor()` (`:175-237`) attaches a `Sailor` behaviour (not read) with role parameters. All roles also get `TICKBONUS` set to the ability code. Repairers and trawlers also learn a craft skill at a proficiency of `min(99, (level + 5 x XL) x 2)` (`:188-204`, `:214-228`).

**Dismissal.** `unInvoke` removes the Sailor behaviour, shows "<S-NAME> quit(s).", wanders the NPC away and destroys the skill's `affected` object (`:240-259`).

### 3. Slavery and obedience (Skill_Enslave)

Adult and violent content: this section describes a slavery mechanic in plain terms. No sexual content is involved.

**Data.** `masterName`, `price`, `masterAnger` (starts at 0), `speedDown`, `hungerTickDown` (reset to `HUNGERTICKMAX` = 4, `:100`, `:107`), a `SlaveryLibrary.GeasSteps` object for orders, and `pimpedInfo` for a temporary second owner with an expiry (`:94-108`). On enslavement the master's previous liege and clans are saved (`:142-147`) and restored on release (`:151-162`). The misc text is `MASTER="name" PRICE=n` (`:111-125`). The skill implements `PrivateProperty`: the owner is the master and `getPrice()` is `100 x level` when no price is set (`:165-173`). `isProperlyOwned()` needs a clan or a real player for the owner name (`:198-207`).

**Enslaving (invoke, `:542-610`).**

- An NPC caster is refused with a joke line (`:544-549`).
- Target intelligence must be at least 5 (`:559`). Undead cannot be enslaved (`:564`).
- The target must be bound or held, already enslaved, or the caster must have staff override (`:570-576`).
- On a proficiency pass (`:581`), the caster's message goes through `okMessage` (`:588-589`), both sides are made peaceful (`:592-595`), and a copy of the skill is added to the target as a non-uninvokable effect with the caster as owner (`:596-602`).

**Orders (executeMsg, `:216-340`).**

- The master (the liege ID or owner name) speaks an order beginning "I command you to " or "I order you to ". The rest goes to `CMLib.slavery().processRequest(...)` (`:279-299`), which returns the steps. The slave answers "Yes master." when steps exist and "Huh? Wuh?" when none do (`:299-306`).
- "stop" or "cancel" from the master ends the steps (`:252-258`).
- Someone else giving the prefix gets "I don't take orders from you." (`:273-281`). An order without the prefix gets a reply explaining the prefix (`:296`).
- While steps are active, other speech to the slave goes to `steps.sayResponse` (`:240-260`).
- Any damage to the slave adds 10 to `masterAnger` and calls `postPanic` (`:231-232`). Whipping or beating it (socials "WHIP <T-NAME>" and "BEAT <T-NAME>") sets `speedDown = 2` (`:223-225`).

**Upkeep and rebellion (tick, `:378-527`).**

- While `speedDown` is positive, the slave's common-skill effects get extra ticks (`:385-397`). The code does not otherwise slow the slave.
- Every 4 mob ticks (`:406-411`) the slave expends energy. Then, if not in combat, it runs a rebellion check: `rollPercentage() == 1` (a 1% gate) and `rollPercentage() < masterAnger/10` (`:411-414`). So nothing can happen below 20 anger; each further 10 anger adds one point to the second roll.
- If the master is in the same room, the slave rebels: "<S-NAME> rebel(s) against <T-NAMESELF>!", the master link is removed, the slave is reset to full and attacks its former master (`:415-428`). Otherwise there is a 50% chance it escapes (`:430-433`).
- Hunger and thirst: a slave with no hunger eats a Food item in its inventory through the Eat command, or says "I am hungry." when it has none (`:435-460`). Thirst is handled the same way with Drink (`:461-486`).
- The liege is synced to the master, and rival clans adopt the master's government position (`:489-498`).
- When steps finish, the slave says "You have completed your masters task." and is unInvoked (`:504-520`).

**Master.** `getMaster()` loads the master's player record even if the master is offline (`:135-149`). If the master can give the slave privileges in a room, that room becomes the slave's start room (`:318-329`). Stat codes MASTER, PRICE, PIMP and PIMPEXPIRE are editable for builders (`:613`, `:615-707`).

### 4. Restraint (Skill_HandCuff)

**Requirements (invoke, `:241-345`).** The caster cannot be fighting (`:243`). The target must have a warrant out, or the caster must be an eligible officer (`:271-280`). A standing target is refused unless the call is automatic (`:281-285`). Success gives the target a malicious effect for `TICKS_ALMOST_FOREVER` (`:304`). The struggle budget is `adjustedLevel(mob, asLevel) x 300` (`:300`).

**Binding (affect and okMessage).** The effect sets `IS_BOUND` (`:111`). While bound, the prisoner can only sit, stand, leave or enter (`:144-162`) and cannot recall (`:126-131`). A follow or nofollow attempt drains the budget by strength plus level (`:137`), and so does an entry into a room the captor is not in (`:154`). Hand and movement actions drain it by strength (`:169`). When the budget runs out the effect ends (`:138-139`, `:170-173`). Others may not attack the prisoner while it follows a captor who is not in combat (`:183-197`, "You may not assault this prisoner.").

**Prisoner follows captor.** On success the target is made to follow the caster with the NOFOLLOW attribute handled (`:320-329`), and the caster is made the target's follow leader with `postFollow` (`:324`).

**Release.** `UNTIE` (`:248-266`) removes the effect after a visible HANDS action. There is no skill roll on this path. `unInvoke` shows "released from the handcuffs" (`:214`) and stands the prisoner (`:219`).

### 5. The diary (Skill_Diary)

**Setup (invoke, `:392-499`).** Keeping a diary toggles off when one is already running (`:394-399`). Features unlock with the skill's XL: XP at 0, KILLS at 1, DEATHS at 2, AREAS at 3, FACTION at 4, LOOT at 5, DAMAGE at 7, HEALING at 8, ALL at 10 (`:134-151`, `:401-405`). The command is `DIARY <book> <period> <features...>`. Periods come from `AVAIL_PERIODS` (`:81-88`): HOUR, DAY, WEEK, MONTH, SEASON, YEAR. The caster must know Skill_Write (`:417-421`) and must carry the book (`:463-468`). Writer's block is a fizzle (`:497`).

**Recording (record, `:206-280`).**

- The diary keeps a page per period, headed by the home clock's date (`getPeriodDate`, `:162-204`, for example "the 3rd day of <month>, year N").
- A new period starts a new page with a write message (`:225-250`, `:255-268`).
- Each entry is appended by rewriting the whole page with `EDIT <page> <text>` (`:269-279`).
- If the Skill_Write check fails, the entry becomes "(unintelligible)" (`:217-218`).
- If the book is no longer owned by the diarist, the diary is aborted (`:211-214`).

**Events (executeMsg, `:283-368`).** Damage and healing to or from the diarist (`:286-308`). Deaths (`:311-322`). Entering a new area, recorded as "Entering <area>." (`:325-333`). Experience gain or loss (`:334-343`). Looting a corpse (`:344-349`). Faction changes using the faction name (`:350-366`). Sample entries: "@x1 does @x2 points of damage to @x3." (`:294`) and "@x1 gains @x2 points of experience." (`:339`).

### 6. Honorary degrees (Skill_HonoraryDegreeCommoner and six subclasses)

**Family list.** `getAllDegrees()` (`:102-124`) builds `[class ID, class name, title]` for every playable class whose base class equals `getBaseClassID()` (`:51-54`, returning "Commoner" in the base). Titles read "*, Honorary <Class>" (`:119`). The list is cached under `SKILL_<ID>` (`:104`, `:121`). The subclasses differ only in base class: Bard, Cleric, Druid, Fighter, Mage, Thief (each `getBaseClassID()` at line 54).

**Counting (tick, `:134-256`).** When the mob's ability count changes (`:142`), the skill counts the mob's abilities that appear in each class's ability map. Abilities mapped to "EACH" are ignored (`:167-168`). If the mob has the Studying container, that is counted instead (`:148-156`). A family counts when it has more than 3 matches, so at least four (`:183`, `:188`). Its title is added to the player's titles (`:222`), and titles that no longer qualify are removed (`:213-218`).

**Activation (`:278-343`).** While the active title is one of these honorary titles, `eligibleC` is set and the skill gains proficiency (`:228-251`). On a WEAR, WIELD, GET, HOLD, TRAVEL, ENTER or LEAVE message, a proficiency pass (`:280`) sets `activatedC`. `affectCharStats` (`:259-276`) then swaps the mob's current class to that family's class for the duration of that message, and a trailer resets it (`:297-308`). Any other action clears the override (`:331-339`). Other systems that read the current class (for example equipment restrictions) are not in this bucket (unverified).

### 7. Ships: hard turns, intercept, dead reckoning, foul weather

**Ship model.** The ship is a `NavigableItem` with `navBasis() == WATER_BASED`, and the area is a `Boardable`. The skills read `getCombatant()` (ship in combat), `getDirectionFacing()` and `setDirectionFacing()`, and `isAnchorDown()` (`Skill_HardToPort.java:156-160`, `:187-243`; `Skill_InterceptShip.java:186-196`). Effects are attached to the ship (`beneficialAffect(mob, myShipItem, ...)`, `Skill_HardToPort.java:245`), so the ship carries its own locks.

**Hard turns.** Shared checks (`Skill_HardToPort.java:121-170`): the caster must be on the deck (not an indoor room, `:146-150`) and have privileges on the ship (`:152-157`) and the ship must be in combat (`:158-162`). A 3-minute throttle applies to the skill (`:164`), and another hard-turn lock on the ship blocks the attempt (`:165-166`). Each turn yells the command ("HARD TO PORT!", "HARD TO STERN!", "HARD TO STARBOARD!", `:182-184`). A factory NPC rides the ship and sends a MSG_ADVANCE "<S-NAME> change(s) course, turning hard <dir>." (`:221-239`). The turn sets the facing (`:243`) and applies a 6-tick lock (`:245`, `:244-245` for Stern, first turn only).

The turn tables (direction facing before, after):

```python
PORT      = {"N": "W", "S": "E", "E": "N", "W": "S", "NE": "NW", "NW": "SW", "SE": "NE", "SW": "SE"}   # Skill_HardToPort.java:189-216
STARBOARD = {"N": "E", "S": "W", "E": "S", "W": "N", "NE": "SE", "NW": "NE", "SE": "SW", "SW": "NW"}   # Skill_HardToStarboard.java:187-214
# Skill_HardToStern.java:183-253 applies STARBOARD twice.
```

While locked, a weapon attack on the ship has its damage set to 0 with a roll: Port `<= 25 + 5 x XL` (`Skill_HardToPort.java:110`), Starboard `<= 25 + XL` (`Skill_HardToStarboard.java:108`), Stern `<= 12 + XL` (`Skill_HardToStern.java:108`).

Failure pushes the throttle forward by one tick (`Skill_HardToPort.java:258`, `Skill_HardToStarboard.java:256`, `Skill_HardToStern.java:258`). Mana costs (overrideMana): Port 100 (`:88-90`), Stern 200 (`Skill_HardToStern.java:86-89`), Starboard 100 (`Skill_HardToStarboard.java:86-89`).

**Intercept Ship.** On a water-based ship, or while riding a water-based craft, the skill searches up to 100 rooms (`Skill_InterceptShip.java:258`) through water-surface and shore rooms for a Rideable ship whose name matches the argument (`:222-257`, `:242`). It refuses if the caster's ship is in combat (`:186-190`) or anchored (`:192-196`), and if the target is not an enemy by `mayIFight` (`:268-272`). On success it prints the compass course "Your charts say the way there is: <dirs>" and sends a COURSE command to the ship (`:307-313`). It applies an effect of `trail.size()/2` ticks (`:314`). That effect's tick ends when the caster enters combat, reaches the target room, or leaves the water craft (`:94-139`). The effect gives the ship +1 + XL/3 ability and the mob +1.0 + XL/3 speed (`:151-159`). The mana cost is `COST_PCT + 50` (`:141-145`).

**Dead Reckoning.** From a ship's room, finds the nearest non-water room within 100 rooms (`Skill_DeadReckoning.java:126-148`) and reports the first step: "You believe the closest way to land is <dir>" (`:159-165`). "You seem to already be there." applies when the trail has one room (`:149-153`).

**Foul Weather Sailing.** Applies an effect to the ship (`beneficialAffect(mob, target, asLevel, 0)`, `Skill_FoulWeatherSailing.java:171`, so the standard length). The effect's `okMessage` vetoes each TYP_WEATHER message to the ship when the caster is aboard and passes a proficiency check, showing "<S-YOUPOSS> superior sailing skills keeps <T-NAME> sailing." (`:96-113`). Each weather message costs one roll.

### 8. The Find family (Skill_FindShip, Skill_FindClanShip, Skill_FindHome, Skill_FindClanHome)

**Common machinery.** All four extend `StdAbility` with `FLAG_TRACKING` (`Skill_FindShip.java:93-96`), and each starts by cancelling other tracking effects (`:265-272`). Each stores `theTrail` (a room list) and `nextDirection`, an int state:

- `-2` idle;
- `>= 0` the next direction to walk;
- `-1` the trail has ended;
- `999` arrived;
- `-999` finished.

The tick (`Skill_FindShip.java:121-173`) makes an NPC walk with `CMLib.tracking().walk` (`:162`) or tells a player "The way to your ship seems to continue <dir>." (`:154`). Each look at the room recomputes `nextDirection` from the trail (`:184-188`). The search range is `60 + 2 x XL + 10 x XMAXRANGE` (`:292`, `:298`, `:262`, `:266`). The searches use `getRadiantRoomsToTarget` with a filter (`:294-304`, `:264-274`) or `getRadiantRooms` (FindClanShip, `:299-305`), then `findTrailToAnyRoom` (`:307`). All four set `PhyStats.CAN_NOT_TRACK` on the bearer (`Skill_FindShip.java:233-235`, `PhyStats.java:433`). Skill_Track sets the same flag (`Skill_Track.java:140`). The exact effect is in the tracking library (unverified).

**Ownership tests.**

- FindShip (`:213-230`): a WATER_BASED item whose property record gives the caster privileges, with "appears to be your ship!" (`:226`). The owner test accepts the caster's name or any existing player name (`:224-225`).
- FindClanShip (`:223-236`): the owner must be a clan the caster belongs to (`:230-232`).
- FindHome (`:220-231`): the room is owned by the caster or any existing player (`:222-228`).
- FindClanHome (`:226-235`): the room is owned by a clan with weak privileges for the caster (`:228-233`).

**Messages.** "You feel like this is home." (`Skill_FindHome.java:140`), "This place feels very homey." (`:202`), "You already feel at home" (`:250`), "The trail home dries up here." (`:150`), "This place feels very clan homey." (`Skill_FindClanHome.java:208`), "begin(s) heading back to <S-HIS-HER> ship." (`Skill_FindShip.java:311`).

### 9. Combat and dirty fighting

**Dirt (Skill_Dirt.java).** Kicks dirt into a target's eyes. The room must allow dirt (`hereOK`, `:162-182`): not a city room, not wet climate, not outdoor air, watery, indoor air, magic, stone, metal, cave or wood, and not sand or dust atmosphere. The caster must stand and have feet (`:199-232`). Targets without eyes are immune (`:150`, `:205`). Success applies `CAN_NOT_SEE` (`:106-111`) for `3 + XL/3` ticks (`:249`). The clear-on-action path in `okMessage` is never reached: `doneTicking` is only declared and read in this class and never set (`:103`, `:109`, `:121`). Blindness therefore lasts the full duration. The sibling skills Slip (`Skill_Slip.java:149`) and Tail Swipe (`Skill_TailSwipe.java:146`) set the flag in `executeMsg`.

**Groin (Skill_Groin.java).** Implements `HealthCondition` ("Dazed and in pain", `:99-102`) and has `FLAG_TORTURING` (`:105-108`). Damage is `roll(1, adjustedLevel/3 + 2, 0)` (`:229-230`), halved when the defender blocks (`:231-232`), and capped at HP-1 so it never kills (`:233-234`). A target whose reproductive code is male also gets a dazed effect for `2 + XL` ticks (`:237-238`). While dazed, sight, hand, mouth and movement actions fail with "You are way too dazed." (`:110-134`), and the disposition is forced to sitting (`:137-145`). The skill refuses to hit the same target twice in a row (`lastHit` as a `WeakReference`, `:183`, `:211-217`, `:239`). A level gap above the caster costs 10 points per level (`:201-205`).

**Feint (Skill_Feint.java).** Needs combat and melee range (`:157-166`). Success applies a 2-tick effect to the victim (`:184`) that sets DEX to 0 (`:108`) and adds the invoker's adjusted level to `armour()` (`:100-101`). The effect ends on the next tick once the feinter's own weapon attack message targets the victim (`:132-135` sets `done`, `:116-119` ends the effect). Per the hit formula (`Libraries/MUDFight.java:409-412` negates the defence value), a higher `armour()` gives the attacker better odds, so the added level weakens the victim. The armour sign convention in the leveler is unverified.

**Disarm (Skill_Disarm.java).** In combat only (`:114`). The victim must wield or hold a non-natural Weapon (`:128-142`). The roll is `proficiencyCheck(mob, -levelDiff, auto) && rollToHit` (`:147-153`). On success the victim drops the weapon (`MSG_DROP`, `:160-164`).

**Dodge (Skill_Dodge.java).** Passive melee dodge. The attacker must be adjacent (`rangeToTarget()==0`, `:120`), have the dodger as its victim (`:128`), and use a non-ranged, non-thrown weapon or no weapon (`:122-125`). It works once per MOB tick (`doneThisRound`, `:99`, `:105`, `:129`, `:139`). Its roll is proficiency against DEX - 93 + XL (`:127`).

**Explosive Touch (Skill_Explosive.java).** Damage is `roll(1, 90 + level, 30)` (`:109`), so 31 to 120+level, halved when blocked (`:110-111`), with damage type BURSTING (`:112`). The skill has no cooldown in the code, and its usage is movement only (`:83-86`).

**Improved Revoke (Skill_ImprovedRevoke.java).** Removes the caster's own spells, songs, prayers and chants from a target (`:148-160`). The target can be the room, the area, the caster, an exit direction, a mob or an item (`:107-141`). `quietly` suppresses the message (`:100-105`). The gate is `10 - 10 x n` for `n` effects (`:187`).

**Flee (Skill_Flee.java).** Out of combat it always succeeds. In combat it needs a roll with `10 x XL` (`:89`), then forces the FLEE command (`:92-94`).

**Guildmaster (Skill_Guildmaster.java).** Passive pacifism. A malicious message aimed at the guildmaster from an attacker is vetoed when the guildmaster is not in combat and `isInAweOfMe` holds (`:129-151`). That holds when the attacker's current class has the same base class as the guildmaster's (`:112-114`) or the guildmaster's active title names a class from that base (`:115-124`). The attacker must not be a plain StdCharClass (`:108-109`). The veto needs a proficiency check (`:145`), prints "There is no way you would attack <name>." (`:149`) and makes the guildmaster peaceful (`:150`). The `disabled` flag is set when the guildmaster itself attacks (`:137-138`) and cleared when it leaves combat (`:155-156`).

**Diving (Skill_Diving.java).** When falling, it looks for a watery room straight down, up to 1000 rooms (`:120-124`). If it finds one, the fall counts as diving, with the ambiance "Diving" and no fall ambiance (`:99-103`). The fall damage is reduced by the proficiency percentage (`:153`).

**High Jump (Skill_HighJump.java).** Jumps up or in a direction. Extra jumps are `round(XL / 2.5)`, only in airy rooms (`:195-197`, `:234-244`). The character gets the flying disposition while jumping (`:96-100`, effect copy `:206-224`). It cannot jump in combat (`:112-116`), sitting or asleep (`:169-174`), or while riding (`:176-180`).

### 10. Knowledge, writing and other utility skills

**Decipher Script (Skill_DecipherScript.java).** Targets an item with an "Encrypto" effect (`:247-263`). INT must be at least 5 (`:209`). The timer is 20 ticks (`:285`) with a progress line every 4 ticks (`:143-150`). `confirmSuccess` aborts if the item has moved or been destroyed (`:103-124`). On success a temporary copy of the Encrypto effect lets the reader understand it (`:174-185`), a MSG_READ carries the page number text (`:188`), and the copy is removed (`:194-199`). The page argument is passed through as message text (`:219-221`, `:291`). Interpreting the text is outside this bucket.

**Encrypted Writing (Skill_EncryptedWriting.java).** Extends Skill_Write. For one write it adds the Encrypto language, makes it the spoken language and turns the current language off, then restores both (`:92-128`).

**Identify Poison (Skill_IdentifyPoison.java).** On an unworn item it lists poison effects (ACODE_POISON, `:82-93`) with roman-numeral ranks (`:124-125`). A poison drink with no listed effects says "weak impurities" (`:116-118`). Output is built at `:116-128`.

**Haggle (Skill_Haggle.java).** Adds `10 + XL` to CHA for the duration of a forced BUY or SELL (`:83-87`, `:124-135`). The mob cannot run it without the words BUY or SELL and a shopkeeper (`:92-102`).

**Cultural Adaptation (Skill_CulturalAdaptation.java).** Positive faction gains other than alignment are multiplied by `1 + 0.20 + 0.05 x XL` (`:99-100`, `:125-133`). The `setMiscText` override that should read a percentage is dead code (see Problems).

**Deep Breath (Skill_DeepBreath.java).** When gas or water damage hits and the room is not breathable, each event passes on a roll with `2 x CON` (`:101-121`). The budget is `3 + adjustedLevel/5` events (`:93-96`, `:115`). The budget resets when a breathable room is reached (`:126-137`).

**Indoor Riding (Skill_IndoorRiding.java).** A land mount over weight 199 is treated as weight 100 when it enters an indoor room (`:111-126`, `:123`), and the entry costs the mount `11 - XL` movement (`:94-108`, `:105`).

**Devour Corpse (Skill_DevourCorpse.java).** Lets the character eat a corpse weighing under 3 x its own weight (`:94-108`, `:107`), otherwise "is just too large" (`:137`). Eating fills hunger (`:161-187`). When already at maximum hunger, a 1% roll applies a Disease_Obesity effect (`:166-175`). The corpse is then destroyed (`:185`).

**Eagle Eyes (Skill_EagleEyes.java).** Raises the save-overlooking stat by `level + 2 x XL + proficiency` (`:94-98`, `:97`). The same stat is raised by Searching (`Abilities/Common/Searching.java:71`), Thief_Detection (`Abilities/Thief/Thief_Detection.java:103`), Spell_DetectHidden (`Abilities/Spells/Spell_DetectHidden.java:106`) and Prayer_SenseHidden (`Abilities/Prayers/Prayer_SenseHidden.java:108`). The consuming check is outside this bucket (unverified).

**Honorary degree subclasses.** See section 6.

## Content worth mining

### Crew roles (Skill_HireCrewmember.java:123-130, `:464-513`, `:188-237`)

| Role | Sailor parameters | Gear | Extra skill | Cap |
|---|---|---|---|---|
| REPAIRER | FIGHTMOVER=false FIGHTTECH=false | craftsman's hammer (Weaponsmithing, iron); white cotton pants, shirt, sailor's cap | Shipwright | ceil((rooms-decks)/2) |
| TACTICIAN | FIGHTMOVER=true FIGHTTECH=false | drum (InstrumentMaking, oak) | none | 1 |
| CAPTAIN | PEACEMOVER=true WANDEROK=true FIGHTMOVER=false FIGHTTECH=false | blue-dyed pants, shirt, captain's hat | none | 1 |
| DEFENDER | DEFENDER=true FIGHTMOVER=false FIGHTTECH=false | leather set (vambrace, gauntlets, vest, skullcap, leggings, bullwhip, small shield) | none | decks |
| TRAWLER | FIGHTMOVER=false FIGHTTECH=false | net (Weaving, hemp) | Trawling | decks |

Every role except DEFENDER and CAPTAIN also gets the white cotton outfit (`:464-478`), so the REPAIRER, TACTICIAN and TRAWLER rows above include it. The comment at `Skill_HireCrewmember.java:463` mentions blue-and-white striped shirts. The code only colours the items white (`:475`).

### Diary periods and features (Skill_Diary.java:81-151)

- Periods: HOUR, DAY, WEEK, MONTH, SEASON, YEAR (ALLTIME exists but is not offered).
- Features (XL needed): XP 0, KILLS 1, DEATHS 2, AREAS 3 (unused, see Problems), FACTION 4, LOOT 5, DAMAGE 7, HEALING 8, ALL 10.

### Honorary degrees (Skill_HonoraryDegree*.java)

Bard, Cleric, Commoner (base), Druid, Fighter, Mage, Thief. The title for each is "*, Honorary <class name>" (`Skill_HonoraryDegreeCommoner.java:119`). A character qualifies with four or more abilities from one family.

### Commands and phrases

- Enslave: "I command you to <task>" or "I order you to <task>" starts orders; "stop" or "cancel" ends them. Replies: "Yes master.", "Huh? Wuh?", "I am hungry.", "You have completed your masters task." (`Skill_Enslave.java:254`, `:279-306`, `:448`, `:509`).
- Ship yells: "HARD TO PORT!", "HARD TO STERN!", "HARD TO STARBOARD!" (`Skill_HardToPort.java:184`, `Skill_HardToStern.java:182`, `Skill_HardToStarboard.java:182`).
- Dirt refuses rooms that are city, wet, watery, indoors or dusty (`Skill_Dirt.java:162-182`).

### Numbers at a glance

| Effect | Value | Where |
|---|---|---|
| Hand Cuff struggle budget | adjusted level x 300 | `Skill_HandCuff.java:300` |
| Hire level band | level-10 .. level, 5-wide (1 near the top) | `Skill_HireCrewmember.java:578-584` |
| Hire price | (min level + 3) x 10 with a 5-wide band; (min level + 1) x 10 with a 1-wide band | `Skill_HireCrewmember.java:602-603` |
| Slave price default | 100 x level | `Skill_Enslave.java:170` |
| Groin damage | d(adjusted level/3 + 2), halved on block | `Skill_Groin.java:229-232` |
| Explosive damage | 30 + d(90 + level), halved on block | `Skill_Explosive.java:109-111` |
| Dirt blindness | 3 + XL/3 ticks | `Skill_Dirt.java:249` |
| Ship lock after a hard turn | 6 ticks | `Skill_HardToPort.java:245` |
| Decipher duration | 20 ticks | `Skill_DecipherScript.java:285` |
| Diary features unlock | XP 0 .. ALL 10 | `Skill_Diary.java:134-151` |
| Deep Breath budget | 3 + adjusted level/5 events | `Skill_DeepBreath.java:95` |

## Notable design ideas

- A skill can be a long-lived state machine on its target. Enslave keeps a grudge meter (`masterAnger`), a hunger clock and a list of steps. Hand Cuff keeps a struggle budget. Deep Breath keeps a breath budget. Each is a single integer driven by the message stream.
- A ship is a game object that carries effects. Hard turns put a lock on the ship (`Skill_HardToPort.java:245`), so combat rules on the ship are data, not special cases.
- Commands can be speech. Enslave listens for the owner's ordinary speech with a fixed prefix, and the NPC answers in character.
- Titles drive class context. Honorary degrees turn a set of skills into a title, and the title temporarily swaps the class for a single action (`Skill_HonoraryDegreeCommoner.java:259-308`).
- Negative proficiency adjustments are a flat failure gate (`Abilities/StdAbility.java:1480-1484`). Players can understand "30% flat failure" more easily than a subtracted chance.
- The Diary is a passive observer that writes into an in-world book. The book is an item the player can lose or have stolen, and the diary checks ownership at every write (`Skill_Diary.java:211-214`).
- Foul Weather Sailing shows the veto pattern cleanly: one effect on one object cancels one class of environment event.

## Problems and cruft

1. `Skill_HandCuff.java:317`: `oldGuard` is read from `AUTOASSIST`, not `AUTOGUARD`. Because `AUTOASSIST` has just been set (`:314-316`), `oldGuard` is always true, and `AUTOGUARD` is cleared on cuffing (`:318-319`) and again on release (`:217-218`) whatever its previous value was.
2. `Skill_CulturalAdaptation.java:106`: the percentage and integer parsing sits inside `if(newMiscText.length()==0)`, so the parse never sees a real value. The defaults (0.05 per level, 0.20 base) always apply (`:99-100`, `:103-120`). CMath helpers were not checked (unverified).
3. `Skill_Dirt.java:103-121`: `doneTicking` is never set, so the victim-clears-blindness path is dead and blindness lasts its full duration (see section 9).
4. `Skill_HardToPort.java:162-166`: the lock check tests Starboard and the Port lock, but not the Stern lock, so a Port turn is allowed during a Stern lock. The Stern throttle uses the literal `3 * 60 * 1000` (`Skill_HardToStern.java:162`) rather than `REUSE_MILLIS` (`Skill_HardToPort.java:45`). `lastUse` is an instance field (`Skill_HardToPort.java:118`), so the throttle appears to be per character's copy of the skill, not per ship (unverified how ability instances are shared).
5. `Skill_HireCrewmember.java:285-292`: `mats[m++] = m` writes into an array that is never read. Material names (COTTON, LEATHER, OAK, IRON, HEMP) are dropped, and `craftGear` calls `craftItem` with no material. Gear is made from the default material.
6. `Skill_HireCrewmember.java:252`: `R.show(...)` on the room from `CMLib.map().roomLocation(agent)` has no null check (unverified whether it can be null here).
7. `Skill_Enslave.java:317-339`: the `else if` chain takes the room-and-master branch whenever `getMaster()` is non-null, which is the normal case. The follow and quit cleanup on SHUTDOWN, EXPIRE and QUIT (`:332-339`) is therefore almost never reached.
8. `Skill_Enslave.java:223-225`: "speedDown" is set by a whip or beating, but the effect is to tick common-skill effects, not to slow the slave. `masterAnger` only grows (`:231`) and never decays.
9. `Skill_Diary.java:138-139` and `:325-333`: the AREAS feature is defined but never checked, and every ENTER into a new area is recorded regardless of features.
10. `Skill_Diary.java:314-322`: the KILLS branch fires only when the diarist is the dying source, so it records the diarist's own death. Kills the diarist makes are recorded only under DEATHS.
11. `Skill_Diary.java:167-169`: the ALLTIME branch dereferences `nextPeriodBegin`, which is null on the first record (`:224-226`). It is unreachable from the command because ALLTIME is not in `AVAIL_PERIODS` (`:81-88`).
12. `Skill_Diary.java:272`: each entry rewrites the whole page with EDIT, so the cost grows with page length.
13. `Skill_ImprovedRevoke.java:107-141`: with one argument (`REVOKE <what>`) the target branch never runs, so `target` stays null and the reply is "Revoke what from whom? You don't see '' here." unless the command layer supplies `givenTarget` (unverified).
14. `Skill_Disarm.java:154-169`: if the disarm succeeds but the weapon does not fit the wielded or held slot, the else branch prints the failure message.
15. `Skill_HighJump.java:193` and `:204`: proficiency is rolled once before the loop and again inside it, and the first roll is overwritten.
16. `Skill_DeepBreath.java:118`: the "breath ticks" budget counts damage events, not ticks.
17. `Skill_FindShip.java:224-225`: the ownership test accepts any existing player name as the owner, so another player's ship can read as "your ship" if the caster has privileges on it (unverified how `doesHavePrivilegesWith` behaves).
18. `Skill_DeadReckoning.java:100-148`: `currentR` may be null, and it is passed to `findTrailToAnyRoom` without a check (unverified effect).
19. Cosmetic: stray spaces before full stops (`Skill_InterceptShip.java:264`, `:270`), "You must in a pub" (`Skill_HireCrewmember.java:589`), and "fail(s) to get away" with no full stop (`Skill_Flee.java:97`).

## Takeaways for a Python SUD

- **Skill as an object with hooks.** Model a skill as a class with `before(event)` (may veto or amend) and `after(event)` (observe), plus `tick()`. This matches the two-phase message flow without the Java plumbing. Use it for passives, effects on a ship or NPC, and the Diary-style observer.
- **One proficiency helper with explicit modifiers.** Negative modifiers as a flat gate are easy to explain:

```python
def skill_check(proficiency, xlevel=0, adjustment=0, rng=random):
    chance = proficiency + 2 * xlevel           # StdAbility.java:1452-1468
    chance = max(5, min(95, chance)) + xlevel // 2
    if adjustment >= 0:
        chance += adjustment
    elif rng.randint(1, 100) > 100 + adjustment:  # flat gate, e.g. -30 = 30% fail
        return False
    return rng.randint(1, 100) < chance
```

- **Borrow the ship model in a small form.** A vessel with a `facing`, a `combat` flag, an `anchored` flag and a list of effects. The turn table can be a dict (see section 7). The hard-turn lock is a timed effect on the vessel, which is clean.
- **Borrow the trail machinery as one function.** Collapse the four Find skills into `seek(predicate, max_steps)` that returns a path and steps through it. Replace the magic `nextDirection` values with an enum (idle, walking, ended, arrived, done).
- **Crew as data.** Roles as a dataclass with a behaviour dict and a cap rule, for example `Role("tactician", cap=1, params={"fight_move": True})`. The cap formulas (decks, rooms) suit a ship with rooms.
- **Restraint with a budget.** A bound prisoner with a struggle counter reduced by strength plus level is a simple, readable mechanic.
- **Enslavement as a companion with loyalty.** Keep anger, a rebellion chance that rises with anger, and a single order channel. Drop the offline-master loading, the clan and liege sync, and the pimp mechanism; they are multiplayer machinery.
- **Honorary-style progression.** Four or more abilities from one family earns a title, and the title grants a context for one action. That is a neat single-player reward. Drop the clan and privilege checks.
- **Multiplayer assumptions to rethink.** Guildmaster pacifism is aimed at attackers by class and title, which assumes other players. Clan-owned ships and homes, the owner test that needs a real player, and the Diary's recording of other players' deaths are the same. In a single-player game, ownership can be a single property record, and the diary can log only the player's own events.
- **Simplify.** Merge the three hard-turn skills into one `turn(vessel, "port"|"starboard"|"stern")`. Merge Dodge, Feint and Disarm into a combat-modifier table. Keep Groin and Explosive out, or make them crude options with explicit caps, because their unbounded or uncapped damage is hard to balance.

## Coverage

- **Read in full (class body, every method):** all 41 files. The package, import and licence block (about lines 1-35) was skimmed in every file. Only the bodies are code.
- **Honorary degrees:** `Skill_HonoraryDegreeBard.java` read in full. Cleric, Druid, Fighter, Mage and Thief were compared with `diff` against Bard and differ only in class name, ID, display name and base-class string.
- **Consulted outside the bucket, only for the mechanics quoted:** `Abilities/StdAbility.java:1441-1485` (proficiencyCheck), `:2312-2349` (durations and beneficialAffect); `Libraries/Dice.java:398-411` (roll, rollPercentage); `Libraries/MUDFight.java:394-412` (rollToHit); `Common/interfaces/PhyStats.java:433` (CAN_NOT_TRACK); the STAT_SAVE_OVERLOOKING users (`Abilities/Common/Searching.java:71`, `Abilities/Thief/Thief_Detection.java:103`, `Abilities/Spells/Spell_DetectHidden.java:106`, `Abilities/Prayers/Prayer_SenseHidden.java:108`); `Skill_Track.java:140`; the `doneTicking` pattern in `Skill_Slip.java:149` and `Skill_TailSwipe.java:146`.
- **Named but not read:** StdSkill, Skill_Write, Skill_Arrest (`getWarrantsOf`), SlaveryLibrary and GeasSteps, the tracking library, the Sailor behaviour, the Boardable and NavigableItem interfaces, and `CMLib.leveler()` and `CMLib.login()`.
- **Skipped:** none of the 41 files.
