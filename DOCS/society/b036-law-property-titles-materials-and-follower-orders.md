# Law, property titles, materials and follower orders

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b036` (Law, property titles, materials and follower orders) · 11 files · 8105 lines of Java.

**Citation convention.** Java citations are `Dir/File.java:line`, relative to `com/planet_ink/coffee_mud/`. Data files (`resources/laws.ini`, `coffeemud.ini`) are relative to the CoffeeMUD folder. Nothing was executed; every mechanic below was traced by reading the code. Where a trace is a deduction rather than something I saw happen, the text says so.

## Summary

CoffeeMud's law is a data-driven rule set. Crimes are written as five-field strings in `resources/laws.ini`, and in-world NPC officers enforce them: a detected act becomes a warrant, and the warrant walks a 15-state machine (seek, arrest, subdue, transport, judge, then parole, jail, detain, banish or execute, and finally release). Property is a "land title" attached to rooms or areas, with owner and clan privileges, theft rules, and a monthly property tax that ends in forfeiture. The material catalogue holds 210 resources in 16 material families, each with value, gather weight, hardness, density, flags and optional effect abilities. "Follower orders" are the geas and enslavement systems: a 99-entry pattern table turns plain-English commands into queues of find, fetch, give and attack steps, run a step at a time on ticks, and the slave asks bystanders for directions when it is stuck. Reading the code turned up several logic errors (see Problems), the most important being that a `punishcap` replaces the sentence instead of limiting it.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Common/interfaces/Law.java` | Law interface and constants: punishment codes, modifier bits, adjudication states, crime-field indexes, built-in default law text | 842 |
| `Common/DefaultLawSet.java` | Parses a law definition into crimes, taxes, officers and judges; runs the monthly property-tax ledger | 1081 |
| `Common/interfaces/LegalWarrant.java` | Warrant interface: criminal, victim, witness, crime, sentence, state, offence count, jail and release rooms | 384 |
| `Common/DefaultArrestWarrant.java` | Warrant implementation; a state change broadcasts a `MSG_LEGALSTATE` message | 342 |
| `Libraries/interfaces/LegalLibrary.java` | API for legal areas, land titles, privileges, ownership, theft and officials (`CMLib.law()`) | 631 |
| `Libraries/MUDLaw.java` | LegalLibrary implementation: privilege ladder, title lookup, theft check, lot marking | 1033 |
| `core/interfaces/LandTitle.java` | A title (lot): owner, price, back taxes, rental, theft and expansion flags | 188 |
| `Libraries/interfaces/MaterialLibrary.java` | API for resources: lookup, creation, bundling, destruction | 665 |
| `Libraries/RawCMaterial.java` | MaterialLibrary implementation: item creation, rebundle and split, destroy-for-crafting, animal sources | 1609 |
| `Libraries/SlaveryParser.java` | Plain-English order parser and step runner used by geas and enslavement | 1156 |
| `Libraries/interfaces/SlaveryLibrary.java` | Interface: `processRequest`, `GeasSteps`, `GeasStep` | 174 |
| `Items/interfaces/RawMaterial.java` (outside bucket, read in part) | Material enum, resource codes, `DefResource` catalogue, `CODES` registry | 1759 |
| `Behaviors/Arrest.java` (outside bucket, read in part) | The enforcing behaviour: detection, warrant processing, judgement, custody | 4148 |
| `Behaviors/interfaces/LegalBehavior.java` (outside, read in part) | Interface for legal behaviours; expiry constants | 370 |
| `Abilities/Spells/Spell_Geas.java` (outside, read in part) | Geas spell: places an order queue on a target | 229 |
| `Abilities/Skills/Skill_Enslave.java` (outside, read in part) | Enslavement: owner's spoken orders, loyalty, rebellion | 716 |
| `Abilities/Misc/Prisoner.java` (outside, read in part) | Parole and jail effect that blocks escape | 135 |
| `resources/laws.ini` (data, read in full) | Shipped laws: officers, jails, sentences, messages, crime rules, tax settings | 234 |

## How it works

### 1. Law data: crimes as strings

A `Law` is a `Properties` set parsed by `DefaultLawSet.resetLaw` (`Common/DefaultLawSet.java:770-926`). A basic crime is one line of five semicolon-separated fields, indexed by `BIT_CRIMELOCS`, `BIT_CRIMEFLAGS`, `BIT_CRIMENAME`, `BIT_SENTENCE` and `BIT_WARNMSG` (`Common/interfaces/Law.java:727-735`):

```
ASSAULT=;;assaulting <T-NAME>;jail1;Assault is a hideous offense.     (resources/laws.ini:167)
```

- **Places** (`home`, `indoors`, or a room keyword, with `!` to negate). Documented in the header of `resources/laws.ini` and parsed at `Behaviors/Arrest.java:2102-2172`.
- **Flags** (`witness`, `!witness`, `combat`, `recently`, and `ignoreNN` / `!ignoreNN`, a chance the officer overlooks the act). Documented at `resources/laws.ini:137-149` and parsed at `Arrest.java:2007-2093`.
- **Sentence**: a punishment name (`Law.java:642-654`) with optional modifiers (`Law.java:673-682`): `separate`, `skiptrial`, `detain=`, `fine=`, `norelease`, `punishcap=`, `shame=`, `banish=`.

Other keys in the same file:

- **Ability crimes.** A key matches an ability ID, an ability type such as `THIEF_SKILL` or `PRAYER`, or a domain. A leading `$` marks a rule for a *status effect* on a mob rather than for using the ability (`DefaultLawSet.java:915-923`; lookups at `:293-337`; used at `Arrest.java:2797` for effects and `:2543` for use). The `THIEF_*` defaults reach the `Thief_*` classes because `CMClass.getGlobal` compares IDs case-insensitively.
- **`CRIME<n>`**: emote or social triggers (`DefaultLawSet.java:877-894`).
- **`BANNED<n>`**: item or resource names that are contraband (`DefaultLawSet.java:896-912`).
- **Tax keys**: `PROPERTYTAX`, `SALESTAX`, `CITTAX`, `TREASURY`, `TAXEVASION` (`DefaultLawSet.java:821-836`).
- **People and places**: officers and judges by name keyword (`OFFICERS`, `JUDGE`; `laws.ini:4-9`), jails and release rooms by name or ID (`JAIL`, `RELEASEROOM`; `laws.ini:13-18`), protected victims by zapper mask (`PROTECTED`, `laws.ini:74`), trespassers by mask (`TRESPASSERS`, `laws.ini:83`).

**Where a law comes from.** `Arrest.getLaws` (`Behaviors/Arrest.java:694-780`) reads a named resource file (default `laws.ini`) at lines 753-759. The law name `custom` instead reads a per-area database record, and when none exists it seeds one from the compiled `Law.defaultLaw` (lines 733-750). The two sources are not the same text; see Problems.

### 2. Punishment and state codes

| Code | Name | What the officer or judge does (`Behaviors/Arrest.java:1691-1851`) |
|---|---|---|
| 0 | WARNING | Officer warns; no custody |
| 1 | THREAT | Officer threatens; no custody |
| 2-5 | PAROLE1-4 | Judge paroles; a `Prisoner` effect runs for `PAROLEn TIME` ticks (defaults 40, 80, 160, 320) |
| 6-9 | JAIL1-4 | Judge sentences to jail for `JAILn TIME` ticks (defaults 20, 40, 80, 160) |
| 10 | DEATH | Judge orders execution (state 8) |

Modifier bits sit above the code: SEPARATE 256, SKIPTRIAL 512, DETAIN 1024, FINE 2048, NORELEASE 4096, PUNISHCAP 8192, SHAME 16384, BANISH 32768 (`Law.java:656-671`, codes at `:684-693`).

The adjudication states (`Law.java:695-724`) are the warrant's `state()`:

| State | Code | Behaviour (`Behaviors/Arrest.java`) |
|---|---|---|
| SEEKING | 0 | Officer finds the criminal in the room, offers judgement first, else arrests (`processWarrant` at 2911-2997) |
| ARRESTING | 1 | "You are under arrest for ..."; resists move to SUBDUEING (2999-3055) |
| SUBDUEING | 2 | Sap, trip or attack a standing suspect; cuff a sitting one; then MOVING or REPORTING (3056-3182) |
| MOVING | 3 | Officer tracks the prisoner to the judge (3183-3232) |
| REPORTING | 4 | At the judge; judgement (3233-3307) |
| WAITING | 5 | Judge not yet available (3308-3369) |
| PAROLING | 6 | Judge paroles; `Prisoner` for the sentence ticks (3370-3423) |
| JAILING | 7 | Cuff and start the walk to jail (3424-3496) |
| DETAINING | 12 | Detention; `detain=` supplies room and ticks (3497-3576) |
| EXECUTING | 8 | Judge applies `Prisoner` for 100 ticks, then has the judge's execution ability invoked (`Fighter_Behead`, else `Prayer_Stoning`) and attacks; BANISHING follows if the sentence carries banish (3577-3658, execution at 3600-3612) |
| MOVING2 | 9 | Walk to jail; `Prisoner` for `jailTime+5` ticks (3659-3730) |
| MOVING3 / MOVING4 | 11 / 14 | Walk to detention or banishment (3731-3824) |
| BANISHING | 13 | Banishment (3825-3898) |
| RELEASE | 10 | Once `Prisoner` is gone, walk to a release room (3899-4107) |

An arresting officer is frozen from wandering while the warrant is not SEEKING (`Arrest.java:2900-2908`). Officers are real mobs, so the enforcement is physical: they walk, track and cuff.

### 3. Detection: how a warrant is born

Four routes produce warrants:

1. **Actions**, in `Arrest.executeMsg` (`:2473`). A DEATH message whose tool is a mob files a MURDER warrant against the killer (`:2492-2498`). The check is skipped when the victim is a non-officer NPC marked as a trouble-maker (`:2481-2486`). If the killer is an NPC and the victim is an officer, its group leader is charged too when that leader is a player (`:2500-2505`).
2. **Entry checks**, `testEntryLaw` (`:2328-2448`): nudity (no leg, waist or body garment worn, `:2330-2342`); armed (wielding a non-natural, non-hammer, non-staff weapon, not in combat, and no fight in the last 30 s, `:2356-2378`); trespass (`:2386-2397`); banned items via `testBannedItem` (`:2429-2439`).
3. **Ability use**, when a matching ability is used and it is not teaching (`:2543-2553`).
4. **Effects**, once per area tick: `tick` walks each mob's non-automatic effects and asks `findAffectCrime` (`:2789-2809`).

Other systems can also call `accuse` (`:571-619`). `MUDLaw.robberyCheck` does this for property theft with `PROPERTYROB` and `THIEF_ROBBERY` (`Libraries/MUDLaw.java:957`).

**The `fillOutWarrant` gates** (`Arrest.java:1952-2262`), in order:

1. The criminal must be alive, located, and in the legal metro area (`:1962-1979`).
2. Officers, judges, staff with ABOVELAW and "ultimate authority" are exempt (`:1981-1988`).
3. A victim cannot be the criminal (`:2001`).
4. Flags are applied (`:2007-2093`): `WITNESS`, `IGNORE`, `COMBAT`, `RECENTLY`. Any crime whose flags field is blank also needs a witness (see Witnesses below).
5. Place words are applied (`:2102-2172`).
6. A victim of a protected race or class (mask `PROTECTED`) cancels the warrant (`:2176-2183`).
7. An existing warrant for the same criminal, victim and crime is not duplicated (`:2187-2197`). An old warrant record is reused so its offence count carries over (`:2199-2201`).
8. The sentence string is parsed (`:2212-2250`). A victim who is a trouble-maker has the sentence code halved (`:2252-2255`), which is `(code & 255) / 2`.
9. A final `isStillACrime` check and witness-visibility check (`:2256-2262`) lead to `addWarrant` (`:364-404`). That broadcasts a `MSG_LEGALWARRANT` and posts "has been accused of" on the WARRANTS channel.

**Witnesses.** `fillOutWarrant` sets `requiresWitness = true` (`:1993`) and fails if there is no witness (`:2095-2099`). Only a `!witness` flag clears the requirement. The witness must be an NPC: `getAWitnessHere` (`:801-816`) requires a monster with intelligence above 3 that is not the accused, and a roll that clears a threshold of 25 for chaotic accused, 50 for neutral, and 95 for lawful. An officer always counts. `getWitness` (`:818-840`) searches the accused's room, then adjacent rooms. Players are not witnesses. The laws.ini header does not say that unflagged crimes need an NPC witness.

**When a case dies.** `isStillACrime` (`:975-1013`) returns false, and the case closes, if the witness is dead, the witness or victim is in the accused's group, or the last offence is older than `EXPIRATION_MILLIS`. That constant is seven real days (`Behaviors/interfaces/LegalBehavior.java:50-52`). An officer who is the witness or victim keeps the case alive (`Arrest.java:987-991`). A closed case becomes an old warrant and increments `offenses` (`:2837-2839`).

### 4. Judgement and sentencing

`judgeMe` (`Arrest.java:1681-1948`) applies a sentence:

- With SKIPTRIAL the officer is the judge (`:1684-1685`).
- WARN and THREAT are spoken by the judge or, if there is none, the officer, and end the case (`:1691-1744`).
- PAROLE, JAIL and EXECUTE need a judge. With no judge they do nothing and the arrest continues (`:1745-1851`).
- A fine is recorded in `finesAssessed` (`:1854-1870`).
- Banishment and shaming are timed effects: days times `CMProps.getTicksPerMudHour()` times hours per day (`:1876-1928`).
- DETAIN moves the warrant to DETAINING (`:1938-1946`).

**Escalation** is `highestCrimeAction` (`Arrest.java:1191-1237`):

1. Start from the warrant's full code, modifier bits included (`:1193`). The relevant list is every non-SEPARATE warrant the criminal has, and the current warrant is always in it (`:1196`; `getRelevantWarrants` at `:1015-1031` adds it unconditionally). So offences of different crimes escalate each other.
2. First loop (`:1199-1206`): a prior offence whose base plus offences exceeds the current base raises the code to base plus `min(offences, 3)`.
3. Second loop (`:1208-1212`): for each relevant non-SEPARATE warrant, if the code is below that warrant's base plus 4, add 1.
4. Cap (`:1215-1229`): `punishcap=` is compared against the code *with the PUNISHCAP bit still set*.
5. Good criminals get one step back (`:1233-1234`).

Steps 3 and 4 interact badly, traced as follows (see Problems for the impact):

- A plain sentence such as `ASSAULT ... jail1` with no flags. First offence, neutral criminal: code 6, step 3 adds 1, result JAIL2 (7). Good criminal: JAIL1.
- `NUDITY ... warning punishcap=parole1`. Code is `0 | 8192`. `8192 > 2`, so the cap sets the result to PAROLE1 (2) for every offence. Good criminal: THREAT.
- `INEBRIATION ... parole1 punishcap=jail4`. Code is `2 | 8192`. The cap sets the result to JAIL4 (9) for every offence, not just repeats.

Trouble-makers halve the code (`:2252-2255`). For example, MURDER (DEATH, 10) against a trouble-maker becomes PAROLE4 (5), and JAIL4 (9) becomes PAROLE3 (4).

### 5. Property: titles, privilege and theft

**The title.** `LandTitle` extends `PrivateProperty` and `Environmental` (`core/interfaces/LandTitle.java:42`). It has a landPropertyID, an owner name, a price, a `backTaxes` balance (`:145-157`), a rental flag (`:123-129`), an `allowTheft` flag (`:131-143`), and expansion and grid-layout flags (`:159-186`). A title is found on a room, an area, or a boardable item. `getLandTitle(Room)` checks the room's area first, then effects on the room (`MUDLaw.java:298-313`). `getLandTitle(Area)` checks the area itself, a boardable's item, then effects (`:233-249`).

**The privilege ladder** (`MUDLaw.java`):

| Level | Who | Refs |
|---|---|---|
| Weak | Named owner; married to the owner's liege; any clan role in the owning clan | `doesHaveWeakPrivilegesWith` `:460-476` |
| Privilege | Weak, or a clan HOME_PRIVS authority in the owning clan, or in a friendly clan | `doesHavePrivilegesWith` `:448-457`; `doesHaveClanFriendlyPrivilegesHere` `:504-523` |
| Owner | Named owner, liege, or a clan role with PROPERTY_OWNER authority | `doesOwnThisProperty` `:632-648` |

Followers inherit privileges. Each privilege check recurses into `amFollowing()`: `:486-487`, `:499-500`, `:645-646`, `:747-748`. The `LegalLibrary` documentation says the opposite (see Problems).

**Theft** (`MUDLaw.robberyCheck`, `:887-970`). The check fires on GET, PUSH or PULL of an item in a room with a title, by a source without privileges there. Then:

- A title with `allowTheft` permits it (`:913-914`), as does a crafter taking back a fresh craft (`:916-919`).
- The thief needs PK status or a clan war, otherwise the message "You either need to turn on your PK flag, or be in a clan war to rob this property." (`:921-927`).
- Witnesses are any inhabitants with privileges in the room who can see the thief (`:936-955`). If there is one, `B.accuse(... {"PROPERTYROB","THIEF_ROBBERY"})` is called (`:957`).
- The stolen item gets a `Prop_PrivateProperty` with the victim as owner and `expiresec=60` (`:960-966`). The `LegalLibrary` documentation says this makes the item unsellable (`LegalLibrary.java:510-518`). I did not read `Prop_PrivateProperty`, so the mechanism is unverified.

**Attacking property** (`canAttackThisProperty`, `:669-700`). Unowned property, property owned by a `#`-prefixed system name, and property owned by oneself can always be attacked. Clan-owned property is attackable if the attacker may fight the clan's responsible member. Personal property is attackable if the attacker may fight the owner.

**Lot marking** (`colorRoomForSale`, `:801-868`). The room description is trimmed and given sale or rent text, and a `GenWallpaper` item named `id` records the lot number and whether it is expandable or grid-laid. The text markers come from `REALESTATE_MARKERS` (`:788-797`).

**City test** (`isACity`, `:124-132`). A legal area is a city when more than 60% of its countable rooms are city rooms, shopkeepers are more than 5% of intelligent mobs, and there is at least one shopkeeper.

### 6. Property tax: a monthly ledger

`DefaultLawSet.propertyTaxTick` (`:446-680`) runs when the game month changes. The rate is `PROPERTYTAX`. A value below 1 is a fraction, and 1 or more is a percentage (`:457-458`). The shipped default is `0.0025`, or 0.25% a month (`resources/laws.ini:114`, documented at `:109-113`).

Per owner, for each title with a price above 1 (`:470`):

1. `totalValue` is the sum of prices. Positive back taxes are added to `totalValue` and to `owed` (`:504-507`). The tax is then applied to `totalValue`, so back taxes are taxed again (`:510`).
2. A bill between 0.1 and 1 is raised to 1 coin (`:511-512`).
3. Negative back taxes are credit and are spent first (`:514-533`).
4. The rest is withdrawn from the local bank (`:534-541`, `BeanCounter.modifyLocalBankGold` at `Libraries/BeanCounter.java:1579`).
5. If the bank cannot pay, each title's back taxes grow by its share of the tax (`:546-552`). A title is forfeited when `price / backTaxes < 2`, that is, when back taxes exceed half the price (`:553`). Forfeiture clears the owner (`:582-583`) and notifies the clan's CLANINFO channel or the owner (`:555-578`). Unpaid evasion files a warrant if `TAXEVASION` is defined (`:624-638`).
6. If the bill is paid, back taxes are cleared (`:646-650`), coins equal to the payment are deposited in the `TREASURY` container (`:654-664`), and evasion warrants are removed (`:665-674`).

**Timing.** The tick is triggered by `Arrest.tick` once per area tick (`Arrest.java:2813`) and by the admin command `propertytax` (`Commands/TickTock.java:139-155`). The month gate is the global resource `SYSTEM_TAXES_LASTCHECK` (`DefaultLawSet.java:448-453`).

### 7. Materials and resources

**Encoding.** A resource code is `material << 8 | index`, where the material is one of 16 `Material` families (`Items/interfaces/RawMaterial.java:127-207`, enum at `:215-246`) and the index is 0-255 (`RESOURCE_MASK`, `:577`). The default constants run from `RESOURCE_NOTHING` (`:366`) to index 209. `DefResource` (`:609-850`) gives each resource its fields: code, value per pound, frequency, hardness, buoyancy (density times 1000), smell, effect string and a flag (`:598-608`, constructor `:838-849`).

**Registry.** `CODES` (`:857-1759`) sorts the defaults by index and then loads `ADDMATERIAL_` and `REPLACEMATERIAL_` rows from `coffeemud.ini` (`:872-941`, documented at `coffeemud.ini:1387-1402`). Each row has eight comma fields: material, smell, value, weight, hardness, buoyancy, category (FISH, BERRY or WOODY), and an optional ability.

**Items.** `makeItemResource` (`RawCMaterial.java:679-771`) picks a class by family (`GenFoodResource`, `GenLiquidResource` or `GenResource`), sets weight 1, value, name and description, and attaches effect abilities parsed by `CODES.EFFECTA` (`Items/interfaces/RawMaterial.java:1517`). `adjustResourceName` (`RawCMaterial.java:913-976`) writes the name as "a pound of X" or "a N# X bundle".

**Bundling.** `rebundle` (`:77-221`) merges same-type pieces in a room or inventory: weights and values sum, and food nourishment and decay are combined. `disBundle` (`:241-486`) splits bundles and copies effects to each piece.

**Crafting consumption.** `destroyResources` (`:1055-1205`) takes material by family, subtype and secret identity. If a second material is given, it is consumed first. The primary material is then taken pound by pound, scanning the list from the end. The method returns a `DeadResourceRecord` with the value lost, amount lost and effects lost (`:1157-1204`).

**Gathering weight.** `GatheringSkill.myWeightedResources` (`Abilities/Common/GatheringSkill.java:185-203`) repeats each resource `frequency` times in a list, so frequency is literally a weight, and zero excludes a resource.

**Animal sources.** `makeMOBResource` (`RawCMaterial.java:557-677`) turns some resources into animals to be killed for them. Leather gives a cow, bull, doe or buck (d10: 1-3 cow, 4 bull, 5-7 doe, 8-10 buck). Hide, fur or meat give predators and game. Scales give reptiles. Wool and mutton give a sheep, pork a pig, and eggs or poultry a chicken.

**Burn times** (`RawCMaterial.java:1564-1608`). Coal is 40 as raw material and `20 * (1 + weight)` as an item. Lamp oil is `5 + weight`. Leather is `20 + weight`. Cloth, paper and synthetic are `5 + weight`. Wood is 20 raw or `20 * (1 + weight)`. Flesh and vegetation return -1. Metal, glass, rock, liquid and energy return 0.

### 8. Follower orders: geas and enslavement

Two entry points hand text to the same parser:

- **Geas.** `Spell_Geas.invoke` (`Abilities/Spells/Spell_Geas.java:161-215`) calls `CMLib.slavery().processRequest(caster, target, text)` at line 198. The target must have intelligence at least 5. If parsing yields no steps, the target "looks confused" and the spell fails (`:199-203`). `tick` (`:132-157`) calls `steps.step()` on each mob tick. The geas ends when steps are done and the mob is out of combat (`:138-147`), with the message "You have completed your geas." or "You have been released from your geas." (`:95-98`).
- **Enslavement.** `Skill_Enslave.executeMsg` (`:240-305`) takes speech from the liege or the obey name. "I command you to ..." or "I order you to ..." starts an order, and "stop" or "cancel" from the master cancels it (`:248-256`). An order phrased to the slave by anyone else gets "I don't take orders from you." (`:273-282`). A phrase from the master without the required lead-in gets a reminder of the wording and of "stop" or "cancel" (`:296`). A valid order gets "Yes master." (`:299-301`), and "Huh? Wuh?" when nothing parses (`:305`).

**The parser** (`SlaveryParser.java:404-472`):

1. The text is lowercased and trimmed, and each word has leading and trailing `.,;!?'` removed (`:406-408`, `cleanWord` at `:387-401`).
2. `findMatch` tries the 99 patterns in `pmap` (`:136-255`, matcher at `:257-385`). Tokens are `%m` mob, `%i` item, `%g` general text, `%c` the caster (master), `%s` social, `%k` skill word, `%r` room, `%d` direction, and `%*` the rest.
3. If nothing matches, the leading phrases in `universalStarters` (nine entries, `:56-65`) are stripped in a loop and the match is retried (`:411-433`).
4. If still nothing matches, one `wanderquery` step is queued. The slave asks bystanders "Can you help me ...?" (`:437-442`, step at `:1131-1146`).
5. Each match becomes a plan: its instruction string is split on semicolons, `%` tokens are filled with names, and the command words are parsed into a queue (`:445-469`).

Example patterns, as the table stores them:

| Pattern | Instruction |
|---|---|
| `kill %m` | `mobfind %m;kill %m` |
| `bring %i to %m` | `itemfind %i;mobfind %m;give %i %m` |
| `get %i` | `itemfind %i` |
| `go %d` | `%d;` (a bare direction) |
| `go to %r` | `find %r;sit` |
| `take me to %r` | `say follow me;find %r;` |
| `tell %m %g` | `mobfind %m;sayto %m %g` |
| `%k %m` | `mobfind %m;%k %m` (any skill at a named target) |
| `where %*` | a canned "I don't know" say |

**Running the plan.** `GeasStepsImpl.step` (`:512-579`) walks the candidate plans once per tick. A `HOLD` result moves that plan to the front and ends the tick (`:536-542`). A `DONE` result ends the order (`:526-535`). Each plan's say string carries a prefix: `0` means ask only inside the area, `1` means the slave may wander out (`:547-573`). `GeasStepImpl.botherIfAble` (`:758-785`) picks a non-animal bystander who has not been asked yet. It then waits up to about four ticks for a reply (`:987-994`).

`sayResponse` (`:794-834`) handles the reply. A reply containing a rejection word gets "Ok, thanks anyway." and is dropped (`:804-811`). Otherwise leading response phrases are stripped (`:812-821`). A reply with no recognised lead-in from an NPC is dropped with 10% probability (`:822-825`). Any other reply is queued as `find150 "<reply>"`, a directions request (`:829`).

**The item step** (`:860-997`) looks in inventory, on the floor, in open containers, then on other mobs. A holder who refuses escalates over five steps (`:925-957`). At step 4 the slave attacks the holder (`:951`). Shops are checked too: an affordable item is bought with a queued BUY (`:967-976`). Otherwise the slave asks for the money (`:977-981`). Coin orders end with "I got the money!" (`:865-873`).

**Fallback commands.** An unrecognised step is queued as an ordered command, `me.enqueCommand(cur, METAFLAG_FORCED | METAFLAG_ORDER, 0)` (`:1147-1153`). `StdMOB.enqueCommand` refuses an ability that has `FLAG_NOORDERING` (`Abilities/interfaces/Ability.java:972`, value 4096) when the order flag is set (`MOBS/StdMOB.java:2413-2428`). About 40 abilities carry that flag, mostly enchanting, imbuing and summoning rituals, plus `Spell_Wish`, `Spell_Command` and `Thief_Bribe`.

**Loyalty and rebellion** (`Skill_Enslave`):

- A social of WHIP or BEAT sets `speedDown = 2` (`:222-225`).
- Each damaging hit adds 10 to `masterAnger` (`:230-231`). Nothing ever subtracts from it; the only other reference is the read at `:413`.
- On hunger ticks (`:407-416`), if the slave is not in combat, rolls a 1, and rolls below `masterAnger / 10`, it rebels (`:416-419`). If the master is in the room, the slave is released and attacks him. Otherwise it escapes half the time (`:430-432`).
- `getObeyName` returns the current holder, which can be a temporary "pimped" holder rather than the owner (`:127-133`, `pimpedInfo` at `:683-694`).

**Parole.** The `Prisoner` effect blocks recall, fleeing and escape from parole (`Abilities/Misc/Prisoner.java:86-108`). The judge sets its duration from the sentence (`Arrest.java:3390-3391`).

### 9. Messages players see

- Arrest: "You are under arrest for X! Sit down on the ground immediately!" (`Arrest.java:2979`). "Damn, I can't arrest you." (`:2931`). "Since there is no judge, you may go." (`:3151`). "No court today." (`:3403`).
- Officer and judge lines come from `laws.ini`, for example the execution sentence and the parole dismissal (`resources/laws.ini:42` and `:36`).
- Enslavement and geas messages are listed in section 8.

### 10. Timing

- The law ticks on the area's tick (`Arrest.tick`, `Arrest.java:2759-2870`). The area dispatches its behaviours from `StdArea.java:1282-1300`. The interval is set by the tick scheduler and I did not verify it.
- The default tick is 4 seconds (`core/CMProps.java:681`, `TIME_TICK = 4000`). Parole and jail times are counted in these ticks (`resources/laws.ini:91-101`).
- An arrest resist threshold is 1.5 ticks (`Arrest.java:3040`).
- Real-time windows: "recently" is 10 minutes (`:2074`); travel timeouts are 5 minutes (`:3190`, and similar); the armed check needs 30 s since the last fight (`:2371-2372`); crimes expire after 7 real days (`LegalBehavior.java:50-52`).
- Property tax is monthly, on game-calendar month change (`DefaultLawSet.java:451`).

## Content worth mining

### Default crimes

From `resources/laws.ini`, with the sentence as written. "Witness" means the flag is set.

| Crime | Places | Sentence | Notes |
|---|---|---|---|
| Resisting arrest | any | jail1 | Filed by the arrest code |
| Trespassing | not home, not indoors, not recent | jail3 | Trespasser mask `-RACE +Undead` |
| Indecent exposure (NUDITY) | not home, not indoors; witness; not recent | warning, punishcap parole1 | See the cap bug |
| Armed citizen (ARMED) | not home, not giant; not recent; not in combat; witness; ignore 90% | warning, punishcap parole1 | Weapon check at `Arrest.java:2356-2378` |
| Assault | any | jail1 | |
| Murder | any | jail3 | Also filed on DEATH messages |
| Property robbery | any; not witnessed | jail3 | Filed by `robberyCheck` |
| Tax evasion | any; not witnessed | jail1 | Filed by property tax |
| Prison break | any; not witnessed | jail1 | |
| Thief skills (`THIEF_*`) | per crime | jail2 to death | Swipe, steal, arsonry, trap, bribe, con, embezzle, contract hit, death trap, forgery, racketeer, robbery |
| Dangerous magic and kidnapping | per crime | jail3 to jail4 | Invoking dangerous magic (three portal prayers), and kidnapping (`THIEF_KIDNAPPING`) |
| Public intoxication (INEBRIATION, POISON_ALCOHOL, POISON_FIREBREATHER, POISON_LIQUOR) | not indoors, not home, not in a pub, tavern, inn or bar; not recent | parole1, punishcap jail4 | The same rule for four keys |
| Pimping (PIMPING) and enslavement (SKILL_ENSLAVE) | not home; witness; not in combat (pimping also: not recent, 50% overlook) | jail1 (pimping, punishcap jail4); jail3 (enslavement) | Ability-type crimes |
| Emote crimes (`CRIME1`-`CRIME11`) | per entry | warning to jail2 | Spitting, public noise, swearing, a few crude or adult physical emotes, and a mock ritual. Not reproduced |
| Banned substances (`BANNED1`-`BANNED4`) | item names | parole3 | Four named drugs |

### Sentence ladder and time

Parole: 40, 80, 160, 320 ticks. Jail: 20, 40, 80, 160 ticks. At the 4-second default tick that is about 80 s to about 21 minutes of jail time.

### Officer chatter

Four lists of lines, one per stage: the ride to the judge (the prisoner is in for it), to the jail (claustrophobia, soap), to detention (mandatory detention, a nice day), and to banishment (the same, with exile). They are written to be overheard and suit a text game's ambient layer directly.

### Resource catalogue

210 resources, by family. Counts, ranges and examples come from `DefResource`.

| Family | n | Value per lb | Gather weight | Hardness | Examples |
|---|---|---|---|---|---|
| Unknown | 1 | 0 | 0 | 0 | nothing |
| Cloth | 10 | 4-200 | 0-20 | 0-4 | fur, hide, wool, feathers, cotton, hemp, silk, spidersteel, polyester, denim |
| Leather | 2 | 10 | 20 | 2-4 | scales, leather |
| Metal | 20 | 10-205 | 0-10 | 3-11 | iron, lead, bronze, silver, gold, white gold, platinum, dragonscales, aluminum, ultranium |
| Mithril | 3 | 100-200 | 1 | 6-10 | mithril, adamantite, atlantite |
| Wooden | 20 | 1-25 | 2-50 | 1-5 | wood, oak, maple, redwood, teak, cedar, bamboo, reed |
| Glass | 4 | 1-30 | 0-50 | 1-5 | glass, clay, china, crystal |
| Vegetation | 54 | 0-100 | 0-50 | 0-2 | wheat, corn, the berry family, citrus, cocoa, soap, cheese, bread |
| Flesh | 15 | 1-40 | 0-100 | 0-10 | meat, beef, pork, poultry, eggs, wax, fish and seafood, dragonmeat |
| Paper | 1 | 10 | 0 | 0 | paper |
| Rock | 22 | 0-100 | 1-100 | 0-6 | stone, flint, marble, obsidian, sand, coal, ebony, ivory, bone, uranium |
| Liquid | 18 | 0-80 | 0-100 | 0-2 | water, salt water, milk, coffee, blood, lamp oil, honey, sap, petroleum, gasoline |
| Precious | 26 | 5-5000 | 0-20 | 3-9 | jade, diamond, gem, pearl, opal, ruby, emerald, dilithium |
| Energy | 4 | 30-500 | 0 | 0-4 | energy, plasma, electricity, antimatter |
| Synthetic | 4 | 10-100 | 0 | 0-4 | plastic, rubber, deuterium, fibreglass |
| Gas | 6 | 0-60 | 40-100 | 0 | air, oxygen, hydrogen, fluorine, nitrogen, chlorine |

Family membership is not always intuitive. Dragonscales are metal, ebony and ivory are rock, soap, cheese and bread are vegetation, wax and eggs are flesh, and sap is a liquid. Items inherit their family, so this decides which class is built and which craft skill can use them.

Resources with effects or flags:

| Resource | Effect or flag |
|---|---|
| Coffee, coffee beans | Poison_Caffeine |
| Uranium | Disease_RadiationSickness |
| Lemons | Prop_UseSpellCast2(Spell_ShrinkMouth) |
| Dragon blood | Prop_UseSpellCast2(Prayer_MinorInfusion) |
| Slime | Prop_UseSpellCast2(Spell_AcidSpray) |
| WOODY (12) | apples, oranges, lemons, cocoa, nuts, peaches, plums, cherries, coconuts, bananas, limes, sap |
| BERRY (8) | berries, strawberries, blueberries, raspberries, boysenberries, blackberries, smurfberries, cranberries |
| FISH (7) | fish, salmon, carp, trout, shrimp, tuna, catfish |

Thirty resources have frequency 0 and are never gathered. They include steel, brass, aluminum, titanium, glass, china, bread, cheese, soap, paper, wax, spidersteel, the four energy resources (energy, plasma, electricity, antimatter), and the synthetics plastic, rubber, deuterium and fibreglass.

### Animal sources

See section 7. The table is a small catalogue for a single-player game: each resource names the creatures it can come from, with the d10 split.

### Order patterns

99 patterns. A sample is in section 8. Two lists of phrases are also worth reusing: nine universal leading phrases ("please", "to", "go", "i command you to", and so on) and 31 response starters (for example "look at the", "it's at the", "over at the") that strip the lead-in from a bystander's reply.

## Notable design ideas

- **Crimes as data.** A crime is one line: where it applies, what must be true, what it is called, what it earns and what the officer says. The grammar is small and covers recency, witnesses, combat and probabilistic overlook. It is the kind of thing a single-player game can ship as editable text.
- **Rules that follow the ability's family.** A rule can name an ability ID, an ability type, or a domain, and a `$` prefix moves the rule from use to effect. One line can therefore govern every thief skill (`THIEF_SKILL`) or every prayer.
- **Witnesses as the engine of enforcement.** A crime is only caught if an NPC saw it, and whether the NPC speaks up depends on the accused's alignment. This turns the world's bystanders into the police's sensors.
- **Officers are mobs.** The arrest is a physical process: the officer walks, tracks, cuffs and delivers the prisoner, and the case is driven by the same tick as the rest of the world.
- **Escalation through memory.** Old warrants are kept with an offence count, and the count feeds the next sentence. A `separate` flag isolates a crime from the rest.
- **Property tax as a ledger with forfeiture.** Back taxes accumulate, credit is spent first, and a title is forfeited when back taxes pass half its price. The bank path and the treasury path are a nice model of a local economy, even though the bank path is buggy (below).
- **Stolen goods are tagged.** A stolen item is given a temporary owner record, so it cannot simply be sold back to a shop. The mechanism is short and reusable.
- **Orders as programs.** A plain sentence becomes a list of steps, each returning DONE, HOLD or MOVE. HOLD lets a step wait for a reply, which makes "ask a bystander where the forge is" a native mechanic.
- **Packed resource codes.** A resource is `material << 8 | index`, and family, value, weight and effect all come from one table. The packing is compact, but it is also the source of two bugs below.

## Problems and cruft

### Logic errors (found by reading, not run)

1. **`punishcap` replaces the sentence instead of limiting it** (`Behaviors/Arrest.java:1215-1229`). The cap is compared with the code while the PUNISHCAP bit (8192) is still set, so `highest > cap` is always true and the result becomes the cap, for every offence. Traced results: `NUDITY ... warning punishcap=parole1` gives PAROLE1; `INEBRIATION ... parole1 punishcap=jail4` gives JAIL4. The documentation says the cap is "highest punish for repeaters" (`resources/laws.ini:159`). Fix: compare `highest & Law.PUNISHMENT_MASK`.
2. **First-offence escalation** (`Arrest.java:1196`, `:1208-1212`). The current warrant is always in the relevant list, so the second loop adds one step to any sentence with no modifier bits. Traced: `ASSAULT jail1`, first offence, neutral criminal, gives JAIL2. Good-aligned criminals get the step undone (`:1233-1234`). Worth checking in game before deciding whether it is intended.
3. **Bank-paid taxes skip the treasury and do not clear back taxes** (`Common/DefaultLawSet.java:534-541`, `:641-675`). The `else` that deposits to the treasury and resets positive back taxes is attached to the outer `if(owed>0)` (line 534), not to the bank-failure test (line 537). So when the bank pays, the money is removed from the bank (`BeanCounter.modifyLocalBankGold`, `Libraries/BeanCounter.java:1579`), no treasury deposit happens on that path, and positive back taxes stay on the title. Back taxes are then billed again next month (`:504-510`). The documentation says the money goes to the treasury (`resources/laws.ini:110`, `:117`). I checked the brace structure to confirm the attachment.
4. **One legal area per month** (`DefaultLawSet.java:448-453`). The last-checked month is stored under the global key `SYSTEM_TAXES_LASTCHECK`, not per area. `Arrest.tick` calls the tax tick for each legal area (`Arrest.java:2813`). Only the first area to tick in a new month gets its taxes processed. The effect is unverified at runtime.
5. **The order parser strips the wrong length** (`Libraries/SlaveryParser.java:424`). The code is `req = req.substring(universalStarters.length)`. That is the array length, nine, not the length of the matched phrase. Traced: "please kill the rat" becomes "ll the rat", and the slave asks bystanders "Can you help me ll the rat?" Fix: use `universalStarter.length()`.
6. **Rejection words match as substrings** (`SlaveryParser.java:804-810`). `containsString` is a substring test unless the pattern starts with `$` (`Libraries/interfaces/EnglishParsing.java:404-407`, documented as "to ensure complete hits"). The rejection list includes "no", so a reply such as "north" or "now" is answered "Ok, thanks anyway." and discarded.
7. **Random material pick is unreachable** (`Libraries/RawCMaterial.java:47-51`). The method masks the input with `MATERIAL_MASK`, leaving the mask form (`768` for metal), then checks the result against the enum count (16). Every real mask fails and returns -1. `Abilities/Properties/Prop_CommonTwister.java:159-164` then skips the material change; the `if(newMatCode>=0)` guard at line 164 has no else branch.
8. **Value buckets drop most resources** (`Items/interfaces/RawMaterial.java:1592-1606`). The bucket is a `TreeSet` ordered only by value, so resources with equal value collapse to one. Emulating the code over the 210 defaults drops **112** of them, for example 46 of the 54 vegetation entries. The editor uses the same buckets (`Libraries/editors/EditorMaterialsRequired.java:383`). Because of error 7 the random path never reaches these buckets, so the visible effect is in the editor.
9. **The built-in law text has missing newlines** (`Common/interfaces/Law.java:838-840`). Three entries end in `+` with no `\n`, so `PRAYER_BENIGNEDPORTAL`, `PRAYER_ELEMENTALPORTAL` and `THIEF_KIDNAPPING` are absorbed into the value of `PRAYER_MALIGNEDPORTAL` when parsed. This only affects custom-law areas seeded from code (`Arrest.java:745-750`), and the broken text is written to the area's database record. The shipped `resources/laws.ini` is correct (lines 198-201).
10. **Custom resource codes overflow silently** (`RawMaterial.java:1642`). A new code is `allCodes.length | material`, with no bound check. Indexes are 8 bits (`:577`), and 210 are used by default, so about 46 additions fit. Addition 47 sets bit 8, which corrupts the material family.

### Documentation disagreements

- **Followers.** `LegalLibrary.java:326-327`, `:342`, `:362`, `:378` say followers "don't matter" for privileges. `MUDLaw.java:486-487`, `:499-500`, `:645-646` and `:747-748` make a follower inherit the leader's privileges. The `LegalLibrary` text at `:404-407` ("following someone who is an owner") agrees with the code, so the file contradicts itself.
- **Crime expiry.** The comment at `Arrest.java:1005` says three real days. The constant is seven (`LegalBehavior.java:50-52`).
- **Punishment cap.** See error 1.
- **Property tax wording.** `resources/laws.ini:111-113` says a value greater than 0 is divided by 100 and that anything below 1 is used directly. The code divides at 1 or more (`DefaultLawSet.java:457-458`).
- **Material ranges.** `RawMaterial.java:831-833` gives frequency 1-10, hardness 1-10 and buoyancy 0-10000. The data uses frequency 0-100 (fish is 100), hardness up to 11 (ultranium), and buoyancy up to 21450 (platinum at line 650). The shipped `coffeemud.ini` says weight 1-100, hardness 0-6, buoyancy 0-30000 (`coffeemud.ini:1396-1398`). Its weight and buoyancy ranges match the data, but its hardness range does not, since the data reaches 11.
- **Missing property record.** `LegalWarrant.getPunishmentParm` is documented to return null when absent. `DefaultArrestWarrant.java:175-181` returns "".
- **Copy-paste comment.** `Law.java:670` describes BANISH as "public shaming".

### Data oddities

- **Two different law sets.** `Law.defaultLaw` (`Law.java:769-841`) and `resources/laws.ini` disagree on sentences: MURDER is death in code (`Law.java:815`) and jail3 in the INI (`laws.ini:168`); ASSAULT is jail4 in code (`:814`) and jail1 in the INI (`:167`); PROPERTYROB has no flags in code (`:816`) and `!witness` in the INI (`:169`).
- **Aluminum.** `ALUMINUM` has value 205 (`RawMaterial.java:651`), above gold at 150 (`:648`) and platinum at 175, and frequency 0. Probably a typo.
- **White gold.** `WHITE_GOLD` has buoyancy 1450 (`:649`), which is far below typical white-gold density. It is probably meant to be near 15000. This is from general knowledge of the alloy, not from the code.
- **Family assignments.** Dragonscales are metal (`:788`), ebony and ivory are rock (`:715-716`), soap is vegetation (`:770`), sap is liquid (`:740`), and silk and spidersteel are cloth (`:698`, `:771`). Each changes which class an item is built from.

### Smaller cruft

- **Timer reset.** `DefaultArrestWarrant.setState` sets `lastStateChangeTime` even when the state does not change (`:279-280`). The ARRESTING timeout (`Arrest.java:3040`) relies on that timestamp.
- **No-op line.** `RawCMaterial.java:325` sets a weight to itself with a "wtf?" comment.
- **Inconsistent wildcard.** `RawCMaterial.destroyResourcesValue` treats material 0 as a wildcard for a mob (`:1501-1502`) but not for a room (`:1427`).
- **Burn sentinel.** Flesh and vegetation return -1 (`RawCMaterial.java:1592-1594`). The interface says 0 means it will not burn (`Libraries/interfaces/MaterialLibrary.java:621`), and `Abilities/Common/FireBuilding.java:231` tests `== 0`.
- **Ambiguous mask.** `Material.findByMask` treats a value below 16 as an ordinal and anything else as a mask (`RawMaterial.java:300`).
- **Latent null.** `MUDLaw.isRoomSimilarlyTitled` (`:388-394`) dereferences a room that may have no title. It has no callers in the tree.
- **Possible empty parse.** `SlaveryParser.java:1066-1067` calls `firstElement()` on a parse that can be empty. I did not confirm that a reachable order produces an empty direction.
- **Loyalty never heals.** `masterAnger` only increases (see section 8).
- **Speed trick.** The WHIP and BEAT social handling (`Skill_Enslave.java:222-225`) is a special case in the ability's own code.

### Multiplayer assumptions

- Clans own property, have HOME_PRIVS, a responsible member, and CLANINFO and WARRANTS channels (`MUDLaw.java:504-523`, `Arrest.java` `addWarrant`, `DefaultLawSet.java:555-566`).
- Player-versus-player gating for theft (`MUDLaw.java:921-927`).
- Any inhabitant with privileges counts as a witness (`MUDLaw.java:936-955`), which assumes other people are present.
- Trouble-makers are mobs with a quest effect or a trouble-making flag (`Arrest.java:1401-1412`).

## Takeaways for a Python SUD

**Borrow.**

- The crime record. Represent it as a dataclass with a punishment `Enum` and explicit modifiers, and load it from TOML so authors can edit it:

```python
@dataclass(frozen=True)
class Crime:
    name: str
    places: tuple[str, ...] = ()        # e.g. ("!home", "indoors")
    needs_witness: bool = True          # the unflagged default, made explicit
    sentence: Punishment = Punishment.WARNING
    separate: bool = False              # do not escalate with other offences
    message: str = ""
```

- The warrant as a small state machine with explicit transitions: SEEKING, ARRESTING, TRANSPORT, JUDGE, PAROLE, JAIL, RELEASE. Advance it on the world tick. Collapse the transport states into one. Keep the rule that an officer frozen on a case cannot wander.
- The witness rule as a probability, with alignment as the modifier. It is cheap and makes the world feel watched.
- The property ledger, rewritten per owner: back taxes accumulate, credit is spent first, and forfeiture at half the price. Give the treasury a single explicit path. Do not reuse the bank branch's shape.
- The order planner. Compile each command into a list of steps that return `DONE`, `HOLD` or `MOVE`. Use `HOLD` to wait for a reply, and let an NPC answer "where is X" as a native mechanic, with the reply parsed by a short list of response phrases.
- Resources as a dataclass with a family `Enum`, value, a gather `weight`, hardness, density, flags and effect names. Pick with `random.choices(gatherable, weights=[r.weight for r in gatherable])`. This avoids both the packed-code and value-dedupe bugs.

**Simplify.**

- Replace the 99-pattern token matcher with named regular expressions. The pattern table is still the right idea; the wildcard matcher is the fragile part.
- Replace the ability-ID, type and domain keying with a tag set on each ability. The lookup order is the same, and it is much easier to reason about.
- Drop the `$` effect split unless the game needs effect-based crimes.
- Keep one escalation formula and write it as a single function with tests.

**Drop or rethink for single-player.**

- Clan privileges, clan forfeiture notices, CLANINFO and WARRANTS channels, PK and clan-war gating, and player witnesses. None of these exist in one-person play.
- Follower privilege inheritance. For a companion system it could be kept as "a companion may use the leader's house", but as a deliberate design choice, not an accident of recursion.
- The loyalty model should have decay and a ceiling. As written, rebellion probability only grows.
- The NOORDERING list is a good idea: each ability declares whether it can be ordered. Keep the flag.

## Coverage

- **Manifest files read in full:** `Common/interfaces/Law.java`, `Common/DefaultLawSet.java`, `Libraries/MUDLaw.java`, `Libraries/RawCMaterial.java`, `Libraries/SlaveryParser.java`, `Libraries/interfaces/SlaveryLibrary.java`, `Common/interfaces/LegalWarrant.java`, `Common/DefaultArrestWarrant.java`, `core/interfaces/LandTitle.java`.
- **Manifest files read as signatures and summaries** (the `@see`, `@param` and `@return` lines filtered out): `Libraries/interfaces/LegalLibrary.java`, `Libraries/interfaces/MaterialLibrary.java`.
- **Outside the manifest, read in part:** `Behaviors/Arrest.java` (tick, processWarrant states SEEKING through SUBDUEING, PAROLING and JAILING, RELEASE check, judgeMe, fillOutWarrant, addWarrant, accuse, isStillACrime, highestCrimeAction, getFine, getWitness, testEntryLaw, the murder and ability triggers, getLaws). Skimmed by grep only: REPORTING, WAITING, DETAINING, EXECUTING, BANISHING, MOVING2-4 bodies, `release`, `aquit`, `testBannedItem`, `getBanishmentTicks`, `getShameTicks`, `fileAllWarrants`. `Behaviors/interfaces/LegalBehavior.java` (constants only). `Items/interfaces/RawMaterial.java` (constants, Material enum, DefResource, CODES constructor, `add`, `getValueSortedResources`, `CODES.EFFECTA` and `COMPOSE_RESOURCES`; instance methods skipped). `Abilities/Spells/Spell_Geas.java` (invoke, tick, unInvoke, executeMsg). `Abilities/Skills/Skill_Enslave.java` (fields, executeMsg, tick, obey-name logic). `Abilities/Misc/Prisoner.java` (lines 80-110).
- **Short reads for citations:** `Commands/Follow.java` (processFollow), `MOBS/StdMOB.java` (enqueCommand, setFollowing), `Libraries/BeanCounter.java` (modifyLocalBankGold), `Libraries/EnglishParser.java` and `EnglishParsing.java` (containsString), `core/CMClass.java` (getGlobal), `Abilities/Common/GatheringSkill.java` (myWeightedResources), `Abilities/Properties/Prop_CommonTwister.java` (material change), `Commands/TickTock.java` (propertytax), `Areas/StdArea.java` (behaviour dispatch), `core/CMProps.java:681`, `Libraries/editors/EditorMaterialsRequired.java:383`.
- **Data read in full:** `resources/laws.ini`. Material section of `coffeemud.ini` (lines 1387-1402). `resources/lawtoc.ini` was not read beyond its header.
- **Not read:** most of `Behaviors/Arrest.java` (of its 4148 lines, only the sections listed above), `Prop_PrivateProperty` (mechanism of stolen-goods tagging), `Libraries/Dice.java` beyond `rollPercentage`, and the `Gen*` item classes.
- **Verification:** all findings are from reading and manual tracing. No code was run. The traced results (section 4 escalation, Problems 1-2, and the tax and material findings in section 6, section 7 and Problems 3-10) should be confirmed in game, or with a debug run, before any fix.
