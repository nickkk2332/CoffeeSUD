# Character classes and races: models and base classes

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b169` (Character classes and races: models and base classes) · 9 files · 9720 lines of Java.

Citations are relative to the CoffeeMud folder. Below, the `com/planet_ink/coffee_mud/` prefix is dropped, so `CharClasses/StdCharClass.java:1326` means `com/planet_ink/coffee_mud/CharClasses/StdCharClass.java` line 1326. Every claim was checked by reading the source. Nothing was run, so runtime behaviour is inferred, and the text says so where it matters.

## Summary

A character class and a race are both stat-affecting objects that every mob carries. Together they set stat caps and adjustments, per-level bonuses, which abilities are free and which must be bought, which armour and weapons are allowed, and which body parts (and so which wear slots) a character has. `StdCharClass` and `StdRace` hold the hard-coded defaults and the contract; `Fighter`, `Mage` and `Human` are the worked examples. `GenCharClass` and `GenRace` implement the same contract with every field stored as data (an XML string in the database, edited through a numbered stat-code protocol), which is how builders create classes and races in a running game and how `StdRace.mixRace` builds hybrids. Level-up is driven centrally by `Libraries/CoffeeLevels.java`, which evaluates the class's formula strings and records each level's gains so that delevelling reverses them exactly.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `CharClasses/interfaces/CharClass.java` | Contract for a class: names, stat caps, restriction sets, grant and level hooks, formula getters, `GENFLAG_*` bits | 779 |
| `Races/interfaces/Race.java` | Contract for a race: size, aging chart, body-part counts, wear grid, natural weapons, racial and cultural abilities, `BODY_*` constants | 652 |
| `CharClasses/StdCharClass.java` | Default class: formulas, `grantAbilities`, qualification rules, armour and weapon checks, `makeGenCharClass` | 1590 |
| `CharClasses/GenCharClass.java` | Data-driven class: fields in XML, numbered stat-code get and set, delegate "buddies" | 1612 |
| `CharClasses/Fighter.java` | Worked example: 76 ability mappings (levels 1 to 35), STR cap +7, duel and conquest XP | 378 |
| `CharClasses/Mage.java` | Worked example: about 234 mapping calls, cloth armour, magic-like weapons, random free spells per level | 554 |
| `Races/StdRace.java` | Default race: size, aging effects, racial and cultural grants, corpse, `mixRace`, `makeGenRace` | 2204 |
| `Races/GenRace.java` | Data-driven race: fields in XML, stat-code get and set, buddies | 1689 |
| `Races/Human.java` | Worked example: size, body counts, justice save +10, natural weapons, health text | 262 |

Supporting code read, outside the bucket: `Libraries/CoffeeLevels.java` (formulas, level-up, unlevel), `core/CMath.java` (formula evaluator), `Libraries/Dice.java`, `MOBS/StdMOB.java` (stat recompute, wear slots), `Common/DefaultCharStats.java` (body parts, age category), `Libraries/CMAbleMap.java` and `Libraries/interfaces/AbilityMapper.java` (ability mappings), `Commands/Create.java`, `Commands/Train.java`, `core/CMClass.java` (Gen loading), `Abilities/Common/DiligentStudying.java`.

## How it works

### 1. Class and race as stat carriers

Each mob has one race (`charStats().getMyRace()`) and one or more classes (`numClasses()`, `getMyClass(i)`, `MOBS/StdMOB.java:938-941`). Both are `Tickable`, `StatsAffecting`, `MsgListener` and `Modifiable` (`CharClass.java:41`, `Race.java:38`). Their `ID()` is the key the rest of the engine uses: ability mappings, `CMClass.getCharClass(id)`, and Gen storage. `StdCharClass.getTickStatus()` returns `STATUS_NOT` (`CharClasses/StdCharClass.java:348-351`), so a class or race does not tick unless a Gen buddy is attached.

Stats are recomputed, not accumulated. `StdMOB.recoverCharStats()` (`MOBS/StdMOB.java:926-948`) copies the base stats, then applies in order: riding, deity, each class's `affectCharStats`, the race's `affectCharStats`, the race's `agingAffects` (run against the base race), effects, items, room and factions. A class's effect only applies while it is the current class (`CharClasses/StdCharClass.java:1361`).

### 2. Data model

Class fields. Defaults come from `StdCharClass`; the Gen fields are at `CharClasses/GenCharClass.java:46-91`.

| Field (getter) | StdCharClass default | Meaning | Where |
|---|---|---|---|
| `getAttackAttribute()` | STRENGTH | Stat that drives attack gain and the fumble odds | `CharClasses/StdCharClass.java:86-89` |
| `getPracsFirstLevel()` / `getTrainsFirstLevel()` | 5 / 3 | Practices and trains at creation | `StdCharClass.java:91-101` |
| `getBonusPracLevel()` / `getBonusAttackLevel()` | 0 / 0 | Added to each level's gain | `StdCharClass.java:73-83` |
| `getLevelsPerBonusDamage()` | 1 | +1 damage every N class levels | `StdCharClass.java:103-107` |
| `getLevelCap()` | -1 (none) | Class level ceiling | `StdCharClass.java:67-71` |
| `maxStatAdjustments()` | all 0 | Added to the maximum each stat can be trained to | `StdCharClass.java:135`, `952-956` |
| `getMinimumStatRequirements()` | none | Stat prerequisites to qualify | `StdCharClass.java:245-252` |
| `getRequiredRaceList()` | empty | Races allowed: IDs, names, categories, "All", or "-" exclusions | `StdCharClass.java:237-243`, `1060-1103` |
| `allowedArmorLevel()` / `allowedWeaponLevel()` | ANY / ANY | Restriction sets (section 7) | `StdCharClass.java:143-153` |
| `getSubClassRule()` | BASEONLY | Who may train into this class: NONE, ANY or BASEONLY | `StdCharClass.java:193-197`, `CharClass.java:650-655` |
| `outfit()` | none | Starting items | `StdCharClass.java:1346-1350` |
| `availabilityCode()` | 0 | Theme bits that control creation visibility | `StdCharClass.java:309-313` |
| `getHitPointsFormula()` and the other formula getters | see section 6 | Per-level HP, mana and movement | `StdCharClass.java:109-129` |

Race fields. Defaults come from `StdRace`; the Gen fields are at `Races/GenRace.java:42-86`.

| Field | StdRace default | Meaning | Where |
|---|---|---|---|
| `shortestMale()` / `shortestFemale()` | 24 / 24 inches | Height floor | `Races/StdRace.java:87-97` |
| `heightVariance()` | 5 | Extra inches available | `StdRace.java:99-103` |
| `lightestWeight()` / `weightVariance()` | 60 / 10 lb | Weight floor and spread | `StdRace.java:105-115` |
| `bodyMask()` | all -1 | Count of each of 16 body parts; -1 means not applicable | `StdRace.java:55`, `Race.java:521-559` |
| `getAgingChart()` | {0,1,3,15,35,53,70,74,78} | First year of each of 9 age categories | `StdRace.java:59`, `Race.java:486-502` |
| `practicesAtFirstLevel()` / `trainsAtFirstLevel()` | 0 / 0 | Bonus at creation | `StdRace.java:185-193` |
| `getNaturalWeapons()` | one "Natural" weapon | Unarmed attacks | `StdRace.java:807-816` |
| `forbiddenWornBits()` | 0 | Wear slots the race cannot use | `StdRace.java:117-121` |
| `racialCategory()` | "Unknown" | Category name used in class race lists | `StdRace.java:123-129` |
| `culturalAbilities()` / `racialAbilities(mob)` | none | Abilities granted to members of this race | `StdRace.java:1872-1957` |

### 3. Stat grants

- **Class caps.** `StdCharClass.affectCharStats` raises each stat's ceiling by the class adjustment for that stat and for its MAX code, but only while the class is current (`StdCharClass.java:1358-1366`). `Fighter` sets STR +7 (`Fighter.java:124-126`) and `Mage` sets INT +7 (`Mage.java:152-154`). `CMProps` sets `BASEMAXSTAT` to 18 by default (`core/CMProps.java:3094`), so a Fighter can train STR to 25 and a Mage INT to 25.
- **Race effects.** `Human` adds +10 to the justice saving throw in `affectCharStats` and removes it in `unaffectCharStats` (`Races/Human.java:142-153`). The default race changes nothing (`StdRace.java:350-358`).
- **Aging.** `StdRace.agingAffects` (`StdRace.java:386-463`) applies stat and save changes per age category. The Human values are in section 8.
- **Gen effects.** `GenCharClass` and `GenRace` each carry five optional stat objects, stored as strings:
  - ASTATS: additive per stat code (`GenCharClass.java:498-504`; `GenRace.java:398-404`).
  - CSTATS: a set value, applied when non-zero (`GenCharClass.java:505-512`). `GenRace` applies it with `setRacialStat` (`GenRace.java:405-412`), which keeps the stat within the racial maximum (`Common/DefaultCharStats.java:1108-1125`).
  - ASTATE: additive to max fatigue, hit points, hunger, mana, movement and thirst (`GenCharClass.java:517-531`).
  - ESTATS: physical stats (`GenCharClass.java:449-466`), including level, height, weight and speed.
  - STARTASTATE: applied once, at creation (`GenCharClass.java:1539-1549`; `GenRace.java:1125-1133`).

### 4. Ability grants

Every grant reads an ability mapping from `CMLib.ableMapper()`. A mapping ties one class or race ID to one ability. It holds: qualifying level, default and maximum proficiency, default parameter, `autoGain`, secrecy (`SECRET`, `PUBLIC` or `MASKED`, `Libraries/interfaces/AbilityMapper.java:1434-1438`), prerequisite skills, and an extra mask (a zapper expression). `autoGain=true` means the ability is granted free at that level. `autoGain=false` means it must be bought with trains or practices (`AbilityMapper.java:140-144`; the training cost is in `Commands/Train.java:266-288`).

**Class grants.** `StdCharClass.grantAbilities` (`CharClasses/StdCharClass.java:969-1042`) walks every ability in the library. It keeps those whose class mapping has `autoGain`, a qualifying level at or below the class level, and an extra mask that passes. It then calls `giveMobAbility` (`StdCharClass.java:926-950`), which copies the ability and sets its proficiency and default parameter. A mob with the ALLSKILLS security flag gets every ability instead (`StdCharClass.java:973-1013`). `startCharacter` is the creation-time entry point and calls `grantAbilities` (`StdCharClass.java:1326-1333`). `CoffeeLevels.java:1280` calls it again at each level-up.

`Fighter` adds a path for mobs with no player stats: they also receive every non-free mapping up to their level that is not already qualified (`CharClasses/Fighter.java:263-286`). `GenCharClass` does the equivalent in `startCharacter` (`GenCharClass.java:1550-1569`), so the two classes place this grant at different points.

**Mage's random spell grant.** `Mage.grantAbilities` (`CharClasses/Mage.java:484-538`) runs a lottery on top of the base grant. It collects spells whose qualifying level equals the mob's current class level, that are at most 25, not secret, not free, and of the spell classification (`Mage.java:501-513`). It then grants a quota of them chosen at random (`Mage.java:527`). The quota is 3 below class level 8, 2 below 19, and 1 from 19 on (`Mage.java:492-500`). Spells already known count against the quota. A Mage therefore receives spells that would otherwise be bought, but only some of them, and at random.

**Race grants.** `StdRace.grantAbilities` (`Races/StdRace.java:686-737`) walks the race's mappings. A mapping whose ID is in the race's racial list gets its proficiency set and is auto-invoked. Any other mapping is a cultural ability and is given with its mapping proficiency and parameter, if its extra mask passes (`StdRace.java:713-726`). Racial abilities are registered from the racial arrays with `autoGain = !racialAbilityQuals()[i]` (`StdRace.java:1903-1914`). The stored RAGAIN flag is therefore the inverse of auto gain (see section 12).

`startRacing` gives first-level practices and trains only when the mob is at level 1 or below (`StdRace.java:786-789`). At each level-up it is called with `verifyOnly=true` (`CoffeeLevels.java:1282`). That skips the first-level grants and skips cultural abilities (`StdRace.java:794-796`). Cultural abilities still arrive at level-up through the separate `grantAbilities(mob,false)` call (`CoffeeLevels.java:1281`).

**Racial effects.** Racial effects are auto-invoked effects rather than abilities. `Human` has one, `DiligentStudying`, at level 1 with an empty parameter (`Races/Human.java:155-157`). On a level-up message it sets `amt` to the new level minus the old level, multiplies by its parameter when that is non-zero, and adds the result to practices (`Abilities/Common/DiligentStudying.java:95-122`). With an empty parameter the multiplier is 0 and is skipped, so a Human gains one extra practice per level.

**Immunities and charm.** `StdRace.okMessage` vetoes any ability message whose tool is in `naturalAbilImmunities` (`StdRace.java:471-492`). A race flagged uncharmable also rejects charm abilities with a message (`StdRace.java:479-484`).

### 5. Level progression

The driver is `CoffeeLevels.level(mob)` (`Libraries/CoffeeLevels.java:1183`).

1. It returns early when levels are disabled, when the class or race is leveless, or when the class is at its level cap (`CoffeeLevels.java:1185-1188`; `Common/DefaultCharStats.java:574-579`).
2. It sends a global `MSG_LEVEL` message before any gain applies (`CoffeeLevels.java:1190-1193`). Listeners such as `DiligentStudying` see the message at that point.
3. `doBaseLevelAdjustment(gain=true)` (`CoffeeLevels.java:301-398`) raises the physical level and the class level, then adds the HP, movement, mana, attack and damage gains. Each gain is written into `costGains`.
4. Practices are `floor(min(WIS, max WIS)/6) + bonusPrac`, at least 1. Trains are always +1 (`CoffeeLevels.java:1226-1237`).
5. The hooks run: `curClass.grantAbilities(mob,false)`, `race.grantAbilities(mob,false)`, `race.startRacing(mob,true)`, achievements (`CoffeeLevels.java:1280-1283`), then auto-invocation of qualifying abilities. Last, `CharClass.level(...)` and `Race.level(...)` run (`CoffeeLevels.java:1347-1348`).
6. The gains for this level are stored with `playerStats().recordLevelData(...)` (`CoffeeLevels.java:1243`). `unLevel` reads them back with `leveledCostGains` and subtracts the same amounts (`CoffeeLevels.java:405-437`, `434`).

**Per-level formulas.** Each gain is a formula evaluated on current stats (section 6). The HP and movement formulas use these nine variables (`CoffeeLevels.java:162-177`, `188-211`): x1 level, x2 STR, x3 max STR, x4 DEX, x5 max DEX, x6 CON, x7 max CON, x8 WIS, x9 INT. The mana formula uses (`CoffeeLevels.java:55-70`): x1 level, x2 WIS, x3 max WIS, x4 INT, x5 max INT, x6 attack stat, x7 max attack stat, x8 CHA, x9 CON. A "max" is `BASEMAXSTAT` plus the MAX-code adjustment.

| Gain | Rule | Source |
|---|---|---|
| HP per level | class HP formula. If the result is 0 or less: 1 when CON is at least 1, else 0 | `CoffeeLevels.java:188-212` |
| Mana per level | class mana formula | `CoffeeLevels.java:55-70` |
| Movement per level | class movement formula | `CoffeeLevels.java:162-177` |
| Attack per level | `floor(min(stat, max)/18) + bonusAttack`; +1 if the stat is at least 22; +2 if at least 25 | `CoffeeLevels.java:93-108` |
| Damage | +1 when the new class level is a multiple of `levelsPerBonusDamage` | `CoffeeLevels.java:392-398` |
| Practices | `floor(min(WIS, max)/6) + bonusPrac`, at least 1 | `CoffeeLevels.java:1226-1229` |
| Trains | +1 flat | `CoffeeLevels.java:1234-1237` |

The totals queries `getLevelMana` and `getLevelMove` (`CoffeeLevels.java:73-76`, `180-186`) and `getPlayerHitPoints` (`CoffeeLevels.java:215-218`) return start value plus (level - 1) times the current bonus. A totals query therefore uses today's stats, not the history recorded at each level.

**Worked numbers.** These are expectations computed from the formulas, not measured values. They assume stats of 18 and no class adjustment (rounded at the end, as the engine does).

| Quantity | Fighter | Mage | StdCharClass default |
|---|---|---|---|
| HP per level | CON/2 + 2·U[1,7.5) at CON 18: 11 to 24, mean about 17.5 | CON/6 + U[1,5.5) at CON 18: about 4 to 8 | CON/3 + U[1,6.5) at CON 18: 7 to 12 |
| Mana per level | INT/8 + U[1,2.5) at INT 18: 3 to 5 | INT/3 + U[1,4.5) at INT 18: 7 to 10 | INT/3 + U[1,6.5) at INT 18: 7 to 12 |
| Movement per level | 7·STR/18 at STR 18: 7 | 3·STR/18 at STR 18: 3 | 5·STR/18 at STR 18: 5 |
| Attack per level | STR 18: 1; STR 22: 2; STR 25: 3 | INT 18: 1 | STR 18: 1 |
| Practices per level | WIS 18: 3 - 1 = 2 | WIS 18: 3 + 4 = 7 | WIS 18: 3 |
| Damage step | every 30 class levels | every 30 class levels | every class level |

Here U[a,b) is the continuous random value described in section 6.

**First-level grants.** Standard: 5 practices and 3 trains (`StdCharClass.java:91-101`). Fighter: 3 and 4 (`Fighter.java:83-92`). Mage: 6 and 3 (`Mage.java:84-93`). Human: 0 practices and +2 trains, applied only at level 1 (`Human.java:88-91`; `StdRace.java:786-789`). `StdCharClass.startCharacter` applies the class figures (`StdCharClass.java:1330-1331`).

### 6. Formula language

The class formulas are evaluated by `CMath.parseMathExpression` (`core/CMath.java:1372-1490`; the compiled variant is at `1743-1800`). Four points matter to anyone copying them:

- **Left to right, no precedence.** Each operator applies to the running value and the next term. Parenthesised groups are evaluated first.
- **`<` is a minimum, not a comparison.** The code is `finalValue = finalValue < curValue ? finalValue : curValue` (`CMath.java:1444`). So `(@x6<@x7)` means CON capped at its maximum, and `((@x6<@x7)/3)` is CON divided by three. The description code depends on this: `hitPointDescReplacePairs` prints `(@x6<@x7)` as "Con" (`CharClasses/StdCharClass.java:271-281`).
- **`?` is a continuous random number.** The code is `finalValue = ((curValue - finalValue + 0.5) * rand.nextDouble()) + finalValue` (`CMath.java:1475`). So `(1?7)` is uniform on [1, 7.5), and the total is rounded at the end. It is not a die roll, although the description code renders `*(1?7)` as "d7" (`StdCharClass.java:739-755`).
- **Parsed on every call.** The formula strings are compiled only in the web macros (`WebMacros/StatRejuvCharts.java:154-156`). The game parses the string again on each call (`CoffeeLevels.java:55-70` and similar).

Class formulas (the source of the numbers in section 5):

| Class | HP | Mana | Movement |
|---|---|---|---|
| StdCharClass (`StdCharClass.java:112`, `120`, `128`) | `((@x6<@x7)/3)+(1*(1?6))` | `((@x4<@x5)/3)+(1*(1?6))` | `5*((@x2<@x3)/18)` |
| GenCharClass defaults (`GenCharClass.java:49-51`) | `((@x6<@x7)/3)+(1*(1?6))` | `((@x4<@x5)/6)+(1*(1?3))` | `5*((@x2<@x3)/18)` |
| Fighter (`Fighter.java:97`, `103`, `115`) | `((@x6<@x7)/2)+(2*(1?7))` | `((@x4<@x5)/8)+(1*(1?2))` | `7*((@x2<@x3)/18)` |
| Mage (`Mage.java:98`, `104`, `110`) | `((@x6<@x7)/6)+(1*(1?5))` | `((@x4<@x5)/3)+(1*(1?4))` | `3*((@x2<@x3)/18)` |

`Dice.roll(n, die, mod)` is an integer sum of uniform dice (`Libraries/Dice.java:398-406`). `rollPercentage()` returns 1 to 100 (`Dice.java:409-411`).

### 7. Armour, weapons and equipment

**Restriction sets.** `CharClass` defines armour levels `ARMOR_ANY` to `ARMOR_OREONLY` (0 to 6), with descriptions such as "Must wear cloth, vegetation, or paper based armor." (`CharClasses/interfaces/CharClass.java:657-686`). It defines weapon levels `WEAPONS_ANY` to `WEAPONS_MONGER` (0 to 16), each mapping to a set of `Weapon.CLASS_*` values or materials (`CharClass.java:688-747`). `Mage` uses `ARMOR_CLOTH` (`Mage.java:120-123`) and `WEAPONS_MAGELIKE`, which permits only natural, dagger and staff weapons (`Mage.java:126-129`; `CharClass.java:701`, `733`).

**Armour check.** `StdCharClass.armorCheck` (`StdCharClass.java:883-898`) runs only when the class has an armour restriction and the action's source matches `requiredArmorSourceMinor`. For a Mage that source is spell casting (`Mage.java:140-143`). Recall, common skills and languages are exempt. The action is blocked when the worn armour is not allowed (`Libraries/CoffeeUtensils.java:1151-1166`) and `rollPercentage() > 2 × attack stat`. The Mage's blocked-cast message is "<S-NAME> watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (`Mage.java:116`). The block chance is therefore max(0, 100 − 2·stat) percent: 64 percent at stat 18, and never at stat 50 or more.

**Weapon check.** `weaponCheck` (`StdCharClass.java:900-924`) applies the same roll to a disallowed weapon class or material, but only when the mob has something wielded. The message is "<S-NAME> fumble(s) horribly with X." (`StdCharClass.java:919`). Item proficiency filters are checked first (`StdCharClass.java:906-913`). Wielding a disallowed weapon without a fumble prints "<T-NAME> feel(s) a bit strange in your hands." (`StdCharClass.java:1411`), and the wield is not blocked.

**Outfits.** Starting items are set once per class, and their base value is set to 0 (`cleanOutfit`, `StdCharClass.java:815-821`). `Fighter` gets a Shortsword (`Fighter.java:365-377`), `Mage` a Quarterstaff (`Mage.java:541-553`), and `Human` a shirt, shoes, pants and belt (`Human.java:177-197`).

**Body counts and wear slots.** A race lists 16 body parts (`Race.java:521-559`). A count of -1 means not applicable, 0 means absent, and 1 or more means that many (`Race.java:148-158`). A character's count is the race count plus a per-character alteration, which is how limb and tattoo effects change it (`Common/DefaultCharStats.java:698-707`; `alterBodypart` at `743-748`).

Wear slots are derived from counts. `Race.BODY_WEARGRID` gives, for each part, the slot bits and how many parts make one slot (`Race.java:630-651`). `StdMOB.getWearPositions` (`MOBS/StdMOB.java:5897-5955`) divides the count by that number, with a minimum of one slot when the part is present. A divisor of -1 removes the slot when the part is present (a tail or antenna, for example). A race with no arms gets no arm slots, and a race with four arms gets two. Hands have their own special case for wield, held and ring slots (`StdMOB.java:5922-5945`). `Race.forbiddenWornBits` removes slots outright (`StdRace.java:117-121`; 0 for Human).

## 8. Physical rules for races

**Size and weight.** `StdRace.setHeightWeight` (`StdRace.java:907-928`) rolls a weight offset `w` from 1 to `weightVariance`. Height is then the sex-specific minimum plus `round(heightVariance × w / weightVariance)`, so height depends on the same roll. Only when weight variance is zero does it roll height separately. For Human: weight is 150 + w (151 to 200 lb), and height is 68 inches (male) or 64 (female) plus round(12 × w / 50) (`Races/Human.java:52-79`). Heavier characters are therefore taller.

**Aging.** `ageCategory()` maps age to one of nine categories using the chart (`Common/DefaultCharStats.java:751-761`). `StdRace.agingAffects` changes stats for the category, unless the viewer is an immortal or ageing is disabled (`StdRace.java:386-392`). The Human values:

| Years | Category | Effect | Source |
|---|---|---|---|
| 0 | Infant | SAVE_MIND -10; max STR -2, INT -4, WIS -4 | `StdRace.java:396-402` |
| 1-2 | Toddler | As Infant | `StdRace.java:396-402` |
| 3-14 | Child | SAVE_MIND -5; max STR -1, INT -2, WIS -2 | `StdRace.java:403-408` |
| 15-34 | Young adult | None | `StdRace.java:409-411` |
| 35-52 | Adult | None | `StdRace.java:409-411` |
| 53-69 | Mature | SAVE_MIND +5; max STR, CON, DEX -1; max INT, WIS, CHA +1 | `StdRace.java:412-420` |
| 70-73 | Old | SAVE_MIND +10, SAVE_UNDEAD -5; max STR, CON, DEX -2; max INT, WIS, CHA +2 | `StdRace.java:421-430` |
| 74-77 | Venerable | SAVE_MIND +15, SAVE_UNDEAD -25; max STR, CON, DEX -3; max INT, WIS, CHA +3 | `StdRace.java:431-440` |
| 78+ | Ancient | Scaled by n = floor((age − 78)/4) − 1: SAVE_MIND +20+5n; SAVE_UNDEAD −35+5n; max STR −(3+n); max CON and DEX −(3+n); max INT, WIS, CHA +(3+n) | `StdRace.java:441-459` |

The Ancient scale is clamped to at most 16, and only a result of exactly 0 is raised to 1. So ages 78 to 81 get n = −1 (see section 12).

**Health text.** `Human.healthText` returns one of eleven messages by hit-point fraction, from "is mortally wounded and will soon die." below 10 percent to "is in perfect health." at 99 percent and above (`Races/Human.java:207-243`). The default race uses the shared `standardMobCondition` (`StdRace.java:835-839`).

**Natural weapons.** `StdRace.getHumanoidWeapons` builds ten unarmed attacks (`StdRace.java:841-898`). `Human` uses this list (`Human.java:199-205`). `getNaturalWeapon` picks one at random each time it is called (`StdRace.java:799-805`).

**Corpse and resources.** `StdRace.getCorpseContainer` (`StdRace.java:953-1129`) makes a corpse named "the body of X" and stores a snapshot of the character's stats. It moves items that are not kept over death into the body or the room, copies contagious disease effects into the body, and drops the race's resources if `destroyBodyAfterUse` is set. Resources come from `myResources()`. For Human these are a liver, blood and bones (`Human.java:246-258`).

## 9. Qualifying for a class

`StdCharClass.qualifiesForThisClass` (`StdCharClass.java:359-596`) checks, in order:

1. The class is not disabled (`StdCharClass.java:362`).
2. The multiclass rule from configuration, a string of the form first-second such as "MULTI-3", "BASE-5" or "SUB" (`StdCharClass.java:364-426`). A class-count limit prints "You have already trained for your max @x1 classes." (`:422`).
3. A minimum level in the base class for BASE rules (`:447-459`).
4. Minimum stats, counting race adjustments, with tattoo overrides (`:461-479`). The message is "You need at least a @x1 @x2 to become a @x3." (`:475`).
5. Race: `isAllowedRace` (`:480-495`). A list entry can be an ID, a name, a racial category, "All", or a "-" exclusion. A security flag `CHARCLASS_<ID>` can override the list (`:1060-1103`). The message is "You need to be a @x1 to be a @x2." (`:492`).
6. Training rules from the current class's `SubClassRule` (`:496-594`).

`Fighter` needs STR 9 (`Fighter.java:252-254`), and `Mage` needs INT 9 (`Mage.java:471-475`). Fighter accepts every race (`Fighter.java:243`). Mage's list has 14 entries, which are races and racial categories, including Human, Elf, Dragon and Orc (`Mage.java:459-463`).

## 10. Gen versions: classes and races as data

**Creation and storage.** The builder command `CREATE CLASS` makes a `GenCharClass` with the given ID, or converts a standard class with `makeGenCharClass` after a confirmation (`Commands/Create.java:1744-1779`). Races follow the same path (`Commands/Create.java:918-937`). The skeleton records are `<CCLASS><ID>…</ID><NAME>…</NAME></CCLASS>` (`Create.java:1775`) and `<RACE><ID>…</ID><NAME>…</NAME></RACE>` (`Create.java:931`). The saved record is written with `DBCreateClass(id, classParms())` or `DBCreateRace(id, racialParms())` (`Create.java:1779`, `935`). At boot, `CMClass` reads every stored record and passes it to `setClassParms` or `setRacialParms` (`core/CMClass.java:3573-3606`).

**Two serialisations.** Each Gen object stores its data twice:

- an XML string for storage: `classParms()` (`GenCharClass.java:534-675`) and `racialParms()` (`GenRace.java:467-624`), parsed by `setClassParms` (`GenCharClass.java:692-964`) and `setRacialParms` (`GenRace.java:627-931`);
- a numbered key protocol for editing: `getStat(code)` and `setStat(code, value)`, with codes such as `NAME0`, `NAMELEVEL0`, `GETCABLE3` and `GETOFTID2` (`GenCharClass.java:988-1001` for the code list, `1004-1533` for get and set; `GenRace.java:933-948`, `951-1648`). The builder editor sets each field through these codes (`Libraries/CMGenEditor.java:9053`).

Ability lists are staged through the key protocol. `GETCABLE<n>` is the commit key and must come last. Its parts arrive first as `GETCABLELVL<n>`, `GETCABLEPROF<n>`, `GETCABLEGAIN<n>`, `GETCABLESECR<n>`, `GETCABLEPARM<n>`, `GETCABLEPREQ<n>`, `GETCABLEMASK<n>` and `GETCABLEMAXP<n>`. They are held in `tempables` (`GenCharClass.java:1155`) and written by `setStat` case 25 (`GenCharClass.java:1273-1290`).

**Converting a standard class to data.** `makeGenCharClass` (`StdCharClass.java:1105-1318`) snapshots a class by running its effects on a factory mob. It runs `affectCharStats` three times with inputs 0, 10 and 11 (`StdCharClass.java:1156-1171`). A stat whose result does not depend on the input becomes an absolute override (CSTATS). The rest become adjustments (ASTATS) (`StdCharClass.java:1172-1192`). `makeGenRace` does the same with 100, 10 and 14, and with the genders M, N and F (`StdRace.java:1323-1372`).

**Buddies.** Gen objects delegate to other objects by class name. `GenCharClass` has `statBuddy` and `eventBuddy`, which contribute stats and messages (`GenCharClass.java:79-80`, `464-465`, `513-514`, `471-495`). `GenRace` has `healthBuddy`, `eventBuddy` and `weaponBuddy` (`GenRace.java:63-65`, `346-351`, `372-377`). A builder can give a Gen race a Java behaviour without writing a subclass.

**Copies.** `GenCharClass.copyOf` and `GenRace.copyOf` create a new object and re-parse the XML (`GenCharClass.java:391-397`; `GenRace.java:337-343`). Every copy is a full round trip.

**Hybrid races.** `StdRace.mixRace` builds a new GenRace from two races (`StdRace.java:1474-1869`). It averages size, weight, XP percentage and stat adjustments. It takes the smaller aging value at each age, takes the natural weapons of the longer list (`StdRace.java:1550-1553`), and merges up to four racial and cultural abilities. Two of those merges are wrong (section 12).

## 11. Interactions

- **Abilities.** Every grant goes through `AbilityMapper` (`Libraries/CMAbleMap.java`). Reloading a race or class deletes its mappings and adds them again (`StdRace.java:1900`; `GenCharClass.java:852`).
- **Leveling.** `CoffeeLevels` (section 5) calls the class and race hooks and reads and writes the per-level records.
- **Training.** Class training costs trains. The amounts come from `CLASSTRAINCOST` and `CLASSSWITCHCOST` (`Commands/Train.java:156-160`, `266-288`). Non-free abilities are bought through the normal training path.
- **Stats.** `StdMOB.recoverCharStats` applies class and race effects (section 1).
- **Masks.** An ability mapping's extra mask is a zapper expression checked with `CMLib.masking().maskCheck` (`StdCharClass.java:1028-1032`; `StdRace.java:719-723`). For example, `Fighter_PlanarVeteran` stays masked until the mob meets the `PLANE "-Prime Material"` condition (`Fighter.java:233-234`).
- **Experience.** `Fighter` gives duel XP of 300 (`Fighter.java:336`) and conquest XP of 50 × area average level ÷ mob level, capped at 500 (`Fighter.java:352-359`). `StdCharClass` decides which group members share a kill (`StdCharClass.java:1466-1510`). `StdRace` multiplies experience gains by the race's percentage (`StdRace.java:502-507`).
- **Messages.** Classes and races both use the two-phase `CMMsg` flow. `okMessage` can veto an action (armour, weapons, immunities, charm). `executeMsg` adds trailer messages and side effects (`StdCharClass.java:1374-1428`; `StdRace.java:472-595`).

## Content worth mining

**Fighter ability ladder** (`CharClasses/Fighter.java:132-229`). "Free" means `autoGain` is true, and "bought" means it costs trains or practices. The ladder has 76 mappings, 28 of them free. Prerequisites are shown in brackets.

| Level | Free | Bought |
|---|---|---|
| 1 | Skill_Write (50%), Skill_Recall (50%), Specialization for Axe, BluntWeapon, EdgedWeapon, FlailedWeapon, Hammer, Natural, Polearm, Ranged, Sword, Armor, Shield | Skill_Swim |
| 2 | — | Fighter_Kick, Fighter_ArmorTweaking |
| 3 | Skill_Parry | Skill_TwoWeaponFighting |
| 4 | — | Skill_Bash, Fighter_WeaponSharpening |
| 5 | Fighter_Rescue | Fighter_Cleave, Skill_Whipsmack (0%), Fighter_SetPolearm |
| 6 | Skill_Disarm, Skill_Subdue | Fighter_DismountingBlow, Fighter_Pistolwhip |
| 7 | — | Skill_Dodge, Fighter_RapidShot |
| 8 | Skill_Attack2 | Fighter_TrueShot |
| 9 | — | Fighter_CritStrike, Fighter_ShieldBlock, Fighter_LegHold |
| 10 | Fighter_BlindFighting | Fighter_Toughness, Fighter_Headlock |
| 11 | — | Skill_Dirt, Skill_MountedCombat |
| 12 | Fighter_WeaponBreak | Fighter_Knee |
| 13 | — | Fighter_Stance, Fighter_DualParry, Fighter_ClinchHold |
| 14 | Skill_Trip | Fighter_Toughness2 [Fighter_Toughness] |
| 15 | — | Skill_Climb, Fighter_Sweep, Fighter_ElbowJab |
| 16 | Fighter_Roll | Fighter_CriticalShot, Fighter_ChokeHold |
| 17 | — | Fighter_Whomp, Fighter_DesperateMoves, Fighter_Forceback |
| 18 | Skill_Attack3 [Skill_Attack2] | Fighter_Toughness3 [Fighter_Toughness2] |
| 19 | — | Fighter_Endurance, Fighter_PointBlank, Fighter_HoldTheLine |
| 20 | Fighter_Tumble | Fighter_AutoBash [Skill_Bash] |
| 21 | — | Fighter_SizeOpponent, Fighter_UrbanTactics, Fighter_RapidReload |
| 22 | Fighter_ImprovedShieldDefence | Fighter_Berzerk |
| 23 | — | Fighter_CoverDefence, Fighter_WeaponCatch |
| 24 | Fighter_CalledStrike, Fighter_CounterAttack | — |
| 25 | — | Fighter_Heroism; Fighter_Behead (secret) |
| 30 | Fighter_CoupDeGrace | — |
| 35 | — | Fighter_PlanarVeteran (masked) |

**Mage pattern** (`CharClasses/Mage.java:157-451`). Mage has about 234 mapping calls. Level 1 has 19 of them: ten free (Write, WandUse, Recall, Revoke, ScrollScribing, MagicMissile, ResistMagicMissiles, ReadMagic, Shield, IronGrip) and nine bought. The random lottery draws from the bought spells, and at level 1 that pool has seven: Erase, MagicalAura, Ventriloquate, Prestidigitation, SpiderClimb, WizardsChest and RepairingAura (`Mage.java:175-181`). Levels 2 to 25 each carry 5 to 13 mappings. The lottery quota is 3 for levels 1 to 7, 2 for levels 8 to 18, and 1 from 19 (`Mage.java:492-500`). Several mappings are conditional on the subclass ID (`Mage.java:365`, `396`, `422`, `429`, `435`, `442`, `449`).

**Human** (`Races/Human.java`):

- Size: 68 inches (male) and 64 (female), with variance 12 and weight 150 plus 1 to 50 lb (`Human.java:52-79`).
- Body counts: eyes 2, ears 2, head 1, neck 1, arms 2, hands 2, torso 1, legs 2, feet 2, nose 1, mouth 1, waist 1; no antennae, gills, tail or wings (`Human.java:117`).
- Justice save +10 (`Human.java:145`).
- Cultural skill: Chopping at 50% (`Human.java:101-102`; `Abilities/Common/Chopping.java`).
- Racial effect: `DiligentStudying`, one extra practice per level (section 4).
- Aging: section 8.
- Natural attacks, ten entries: "a quick punch" (three times), "fingernails and teeth", "an elbow", "a backhand", "a strong jab", "a stinging punch", then "a knee", "a fin" or "a limb" depending on legs and gills, and "a bite" if undead or "a head butt" otherwise (`StdRace.java:841-898`).
- Health tiers: eleven messages (`Human.java:213`, `242`).
- Resources: a liver, blood and bones (`Human.java:252-257`).

**Vocabulary.** The armour and weapon descriptions (`CharClass.java:678-686`, `749-767`) work as help text for item restrictions.

**Messages.** "Victory!!" marks duel and conquest wins (`Fighter.java:335`, `358`). Level-up prints "You have L E V E L E D" (`CoffeeLevels.java:1217`).

## Notable design ideas

1. **One table shapes progression.** Each class's ability ladder is a list of entries: level, ability, free or bought, proficiency, prerequisites, secrecy and mask. Changing a class's progression means editing data. Fighter's chains (Toughness, Toughness2, Toughness3) show prerequisites carrying the design.
2. **Caps make classes.** A class adds to a stat cap (Fighter STR +7, Mage INT +7). The attack thresholds at 22 and 25 (`CoffeeLevels.java:103-107`) reward growing the prime stat past 18.
3. **Formula strings as data.** HP, mana and movement per level are short strings over nine named stats. The constants in those strings are the class's identity.
4. **Free but random.** The Mage lottery grants spells that would otherwise cost trains, but only a random subset. Casters gain variety without choice.
5. **Recorded gains.** Each level's gains are stored, so delevelling subtracts exactly what was given, even after stats have changed (`CoffeeLevels.java:1243`, `434`).
6. **Height from the weight roll.** One roll drives both weight and height, which gives a coherent body (`StdRace.java:907-928`).
7. **Probing a standard class.** `makeGenCharClass` turns hard-coded behaviour into data by running it on a factory mob and sorting the results into overrides and adjustments (`StdCharClass.java:1156-1192`). It is a practical way to migrate code to data.
8. **Buddy delegation.** Gen objects delegate to a named Java object for behaviour they do not store (`GenCharClass.java:79-80`; `GenRace.java:63-65`).
9. **Two-phase messages.** The veto-then-execute flow (`StdCharClass.java:1374`; `StdRace.java:472`) is generic. It handles armour, weapons, immunities and charm in one place.
10. **Body shape shows in combat text.** Natural attack names depend on body parts (knee, fin or limb; bite or head butt), so a race's anatomy appears in combat messages.

## Problems and cruft

These are bugs found by reading. None was run. Each is checked against the source lines given.

1. **Duel cooldown never expires.** `Fighter.java:317` computes `now - (now - MILLIS_BETWEEN_DUEL_WINS)`, which is the constant 1,800,000. The prune test at `:318` and the cooldown test at `:327-328` compare epoch-millisecond values with that constant. A name that has won once is refused on every later win for the life of the process (`duelWinners` is static, `Fighter.java:120`).
2. **Armour averaged with itself.** `StdRace.java:1594`: `RS.setArmor((RS2.armor()+RS2.armor())/2)`. In `mixRace` the first race's armour is ignored.
3. **Second race's start state lost.** `StdRace.java:1697` writes `race2`'s start state into `STARTCS1`, so `STARTCS2` stays empty and the first race's start state is overwritten.
4. **The "keep four" loops keep the wrong count.** `StdRace.java:1773-1774`, `1800-1801`, `1837-1838` and `1859-1860` remove by index while the list shrinks. Ten entries leave seven, and six entries leave five. The intended cap of four is not enforced. `TriadVector` and `PairVector` extend `Vector`, so `remove(int)` is index-based.
5. **Body-part writes use the count as the index.** `GenRace.java:1236-1240`: for a `NAME=count` value the code writes `bodyMask()[numPart]=numPart`. So `EYE=2` writes the EAR slot and leaves EYE unchanged. A count of 16 or more would index past the 16-entry array.
6. **Partial race XML silently truncates.** `GenRace.java:659-665`: when `CAT` is missing the code sets a local variable and returns, so every later field keeps its default. The builder skeleton at `Commands/Create.java:931` has no `CAT`, so it works only because the editor fills in the fields before the first save.
7. **Partial class XML likewise.** `GenCharClass.java:745-747` returns when `BASE` is missing, skipping formulas, levels, restrictions and stats. The same skeleton issue applies (`Commands/Create.java:1775`).
8. **Gen races cannot set first-level trains or practices.** `GenRace.java:101-110` returns 0 for both, and no data field stores them. Human's +2 trains (`Human.java:88-91`) cannot be expressed as data.
9. **Ages 78 to 81 get the wrong scale.** `StdRace.java:446-447` computes n = floor((age − 78)/4) − 1, which is −1 for ages 78 to 81. The clamp checks only `n == 0`, so those ages get smaller stat changes than ages 82 to 89, where n = 1.
10. **Corpse cleanup reads the wrong inventory.** `StdRace.java:1116-1118` loops over `room.numItems()` but reads `mob.getItem(i)`. The duplicate-corpse removal therefore checks the dead mob's inventory, not the room. `MOBS/StdMOB.java:4566-4574` returns null for an out-of-range index, so the loop does little.
11. **Racial effect cache may give a stale invoker (likely, not run).** `StdRace.java:1215-1216` caches racial effects by level alone. The cached list includes copies whose invoker is set to the first mob (`StdRace.java:1257-1261`, cached at `:1267-1271`).
12. **RAGAIN is inverted in name.** The XML tag `RAGAIN` holds the qualifier flag (`GenRace.java:563`, `857`). The code then uses `autoGain = !qualifier` (`StdRace.java:1911`). Anyone writing race data by hand must remember that "true" means "must buy".
13. **Describing a race changes its usage count.** `makeStatChgDesc` (`StdRace.java:2041-2131`) calls `startRacing(mob,false)` on a temporary mob (`:2055`). `startRacing` increments `usageCount` once population has started (`:766-767`).
14. **Possible null dereference in duel XP (unverified).** `Fighter.java:309` calls `msg.source().playerStats().getAccount()`. That would fail for a non-player source, if such a source can send a duel-loss message.
15. **Formula traps.** `<` as minimum, `?` as continuous random, and left-to-right evaluation (section 6). The description tables also depend on text replacement (`StdCharClass.java:271-281`).

Cruft and cost, not bugs:

- Two serialisations of every Gen object, which must stay in sync (`GenCharClass.java:534-675` and `1159-1533`).
- Formulas are re-parsed on every call (`CoffeeLevels.java:55-70` and similar).
- `GenCharClass.getStat` rebuilds the whole ability list for each numbered query (`GenCharClass.java:1068-1070`, `1128-1130`), which is quadratic when an editor walks the list.
- Every Gen copy re-parses XML (section 10).
- Fighter's NPC grants run in `grantAbilities`, while the Gen class's run in `startCharacter`, so NPC behaviour differs by class type (`Fighter.java:263-286`; `GenCharClass.java:1550-1569`).
- Mage's subclasses are picked by string comparison on the ID inside `initializeClass` (`Mage.java:365`, `396`, `422`, `429`, `435`, `442`, `449`).
- The aging chart is duplicated in `Races/Human.java:125` and `StdRace.java:59`.
- Mutable static state in `Fighter` (`Fighter.java:118-120`).

## Takeaways for a Python SUD

**Borrow:**

- Keep class and race as plain data (YAML or TOML), validated on load. Do not give each class its own Python subclass. Use one small hook table for the real exceptions.
- Copy the ability ladder as a table: `(level, ability, free, prerequisites, secret)`. The free-or-bought split is a useful single-player lever, as in "free at level 5" versus "study for two sessions".
- Give each class a stat-cap bonus and a rule for attack thresholds (CoffeeMud uses 22 and 25).
- Keep the body-part counts and the divisor idea for wear slots. It is a small pure function: `slots = count // per_slot`, with a minimum of one when the part is present.
- Keep the age table and per-category stat shifts for long-lived characters, but store them as explicit data.
- Keep `mix_race(a, b)` for hybrid species. Average the numbers, take the smaller age value, merge abilities once, and test it.
- Keep the health text tiers for descriptions.

A sketch of the data shape:

```python
@dataclass(frozen=True)
class Grant:
    level: int
    ability: str
    free: bool                   # CoffeeMud autoGain
    prereqs: tuple[str, ...] = ()
    secret: bool = False

@dataclass
class ClassDef:
    id: str
    attack_stat: str                       # e.g. "str"
    stat_cap_bonus: dict[str, int]         # e.g. {"str": 7}
    hp_gain: Callable[[Stats, random.Random], int]
    grants: list[Grant]
    armor: frozenset[str] | None = None    # None means any
    weapons: frozenset[str] | None = None
```

**Simplify:**

- Replace the formula strings with Python functions or a fixed integer dice notation, for example `hp_gain = con // 2 + roll(2, 7)`. Avoid the evaluator's `<`-as-minimum and continuous `?`.
- Store each level's gains in a list and derive totals from it, so delevelling is a `pop()`.
- Make restrictions visible rather than hidden. A restricted armour or weapon blocks the action with a message the player can read. If random failure is kept, use a seeded RNG so tests are repeatable.
- Keep the two message phases as one `before(event) -> bool` and an `after(event)` pair per class and race.

**Drop (multiplayer or out of scope):**

- Multiclass rule strings, the SUB and BASE subclass rules, and the group-share functions (`StdCharClass.java:359-596`, `1466-1510`).
- Duel and conquest XP, group sharing, and the group message sent at level-up (`Fighter.java:302-362`; `CoffeeLevels.java:1201-1206`).
- The security-flag and tattoo overrides for qualification.
- The mating and pregnancy hooks (`StdRace.java:509-592`). A text game does not need them. If the SUD wants such content, design it separately and neutrally.
- The statBuddy and eventBuddy machinery. In Python, composition is a single attribute.

**Rethink for one player:** The duel system is the only part that depends directly on other people. Conquest XP depends on clans. Both would need replacement quests or events, not a code port.

## Coverage

- **Read in full:** all nine bucket files: `CharClasses/interfaces/CharClass.java`, `Races/interfaces/Race.java`, `CharClasses/StdCharClass.java`, `CharClasses/GenCharClass.java`, `CharClasses/Fighter.java`, `CharClasses/Mage.java`, `Races/StdRace.java`, `Races/GenRace.java`, `Races/Human.java`.
- **Read in part, supporting code:** `Libraries/CoffeeLevels.java` (55–218, 301–470, 1183–1350), `core/CMath.java` (1372–1490), `Libraries/Dice.java` (398–411), `MOBS/StdMOB.java` (926–948, 4566–4574, 5897–5955), `Common/DefaultCharStats.java` (574–579, 698–761, 1108–1135), `Libraries/CMAbleMap.java` and `Libraries/interfaces/AbilityMapper.java` (signatures only), `Commands/Create.java` (918–937, 1744–1782), `Commands/Train.java` (150–160, 262–292), `core/CMClass.java` (3570–3606), `core/CMProps.java` (3094, 3193–3195, 3265–3273), `Common/DefaultPlayerStats.java` (1826–1850), `Abilities/Common/DiligentStudying.java` (95–125), `Libraries/CoffeeUtensils.java` (1061–1075, 1151–1166), `WebMacros/StatRejuvCharts.java` (154–156).
- **Located but not opened:** `Abilities/Common/Chopping.java` (Human's cultural skill); the Pregnancy effect and `Spell_Blindness` (both named in the `StdRace` social hook; their files were not found or not read); `Libraries/CMGenEditor.java` (only the entry point at 9053); the Mage subclass files; the Specialization abilities.
- **Skipped:** the body of the Gen editor, the spell implementations, and the training command beyond its cost lines.
