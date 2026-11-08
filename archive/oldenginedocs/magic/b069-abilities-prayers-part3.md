# Prayers, part 3: Deafness to Empower Shield

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b069` (Abilities/Prayers [part 3/8: Prayer_Deafness .. Prayer_EmpowerShield]) · 52 files · 8598 lines of Java.

Paths are relative to the CoffeeMud folder. A bare `Prayer_X.java:N` means `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer_X.java` line N. Line numbers were read from the current files; anything not checked is marked "unverified".

## Summary

These 52 prayers are CoffeeMud's deity-powered magic from Deafness through Empower Shield. They cover curses, healing, protection, room and item effects, and two economies: upgrading gear by alignment, and releasing the stored prayers in relics and scrolls. Every class extends `Prayer` (`Prayer.java`), which adds worship-aware messages and an alignment gate that can make a player's prayer fail, plus helpers for relics and alignment "infusions". Most members are short (81 to 350 lines) and follow one invoke pattern: pick a target, pay mana, roll proficiency, send a pair of combat messages through the room, then attach a timed effect whose stat, tick and message hooks do the work. For a single-player Python game the useful parts are the message-veto hooks, the empower-and-release item loop, the room-level "desecrated" state, and a catalogue of about fifty spells that could be rewritten as data rows.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Prayers/Prayer.java` | Base class: identity, worship message helpers, relic and infusion helpers, alignment gate in `invoke` | 432 |
| `Abilities/Prayers/Prayer_DeflectPrayer.java` | Shield-held buff: deflects malicious prayers and wears the shield | 304 |
| `Abilities/Prayers/Prayer_DivineQuest.java` | Deity commissions a generated quest for a player (uses the quest generator) | 350 |
| `Abilities/Prayers/Prayer_Doomspout.java` | Disease-type prayer that spreads by proximity when the sufferer speaks | 285 |
| `Abilities/Prayers/Prayer_Disown.java`, `Prayer_Divorce.java`, `Prayer_Designation.java` | Relationship effects on NPC children, marriages and undead companions | 226, 174, 205 |
| `Abilities/Prayers/Prayer_DepleteScroll.java`, `Prayer_DepleteRelic.java` | Release stored prayers from a scroll or relic, then empty it | 198, 170 |
| `Abilities/Prayers/Prayer_Empower*.java` (10 files: Weapon x5, Armor x2, Shield, Relic, Scroll) | Item upgrade family: tier, level, alignment tags, deity lock | 152 to 187 each |
| `Abilities/Prayers/Prayer_DesecrateLand.java` | Room effect that refuses holy-only prayers | 145 |
| `Abilities/Prayers/Prayer_EarthMud.java`, `Prayer_DivinePerspective.java` | Room effect (mud); remote perception through a deity | 167, 166 |
| `Abilities/StdAbility.java` | Tick-down formulas (2174-2183, 2312-2331), `maliciousAffect` (2185-2208), `beneficialAffect` (2341-2349), `spreadImmunity` (2351-2365), `beneficialWordsFizzle` (2210-2220) | 3018 (read in part) |
| `Libraries/Dice.java` | `roll(number, die, modifier)` (398-406) | 414 |
| `Libraries/Sense.java` | `isGood` (785), `isEvil` (672), `isLawful` (798), `isChaotic` (819) | 3140 (read in part) |
| `Libraries/MUDFight.java` | `adjustedArmor` (377), `rollToHit` (409-424): sign convention for armour | 3817 (read in part) |
| `core/CMStrings.java` | `replaceVariables` (1955-1980): `@xN` takes the Nth argument | 6112 (read in part) |
| `Libraries/CommonMsgs.java` | `postSay` (640-672) | 3051 (read in part) |
| `Libraries/BeanCounter.java` | Bank deposit rows: `addBankDepositInventory` (1221), `delBankDepositInventory` (1402) | 1958 (read in part) |
| `MOBS/interfaces/Deity.java` | `Deity extends MOB` (line 49) | 767 |
| `Abilities/Properties/Prop_WearZapper.java` | Wear restriction carried by empowered gear (mask text) | 111 (existence only) |
| `Abilities/Poisons/Inebriation.java` | Wrapped by `Prayer_DrunkenStupor` | 64 (existence only) |
| `core/CMProps.java` | `TIME_TICK` from `TICKTIME`, default 4000 (2720-2722) | 3965 (read in part) |
| `resources/randareas/example.xml` | Template used by the divine quest generator | (data) |

Base classes that belong to other buckets are only referenced: `Prayer_BladeBarrier.java` (base of `Prayer_DoomAura`), `Prayer_ImbueShield.java` (base of `Prayer_DefileShield`), `Prayer_MalignedPortal.java` (base of `Prayer_ElementalPortal`), `Prayer_Sacrifice.java` (`getBody`, used by `Prayer_Desecrate`), and the healing prayers `Prayer_Heal`, `Prayer_CureCritical`, `Prayer_CureSerious`, `Prayer_CureLight` (used by `Prayer_DeathGuard`).

## How it works

### 1. The base class and the alignment gate (`Prayer.java`)

- **Identity.** `Prayer.ID()` is `"Prayer"` (line 42). The trigger words are `PRAY` and `PR` (77). Every prayer's `classificationCode()` ORs in `Ability.ACODE_PRAYER` (88) with a `DOMAIN_*` constant. Default `abstractQuality()` is `QUALITY_INDIFFERENT` (74) and default `canTargetCode()` is `CAN_MOBS` (68).
- **Worship-aware wording.** `prayWord`, `prayForWord`, `prayingWord`, `hisHerDiety` and `ofDiety` (99-149) return text such as "pray(s) to *<deity>*" when the caster has a deity name and a generic "pray(s)" when not. Almost every message in the bucket goes through these.
- **Relics** (151-183, 307-370, 367-370). `getRelicPrayers` returns the prayers stored in a `Wand` or in a non-weapon, non-armour, non-scroll item whose ability container is `Dischargeable`. `isARelic` is true when that list is not empty. Charges are read and written through the same `Dischargeable` object (`getRelicCharges` 356, `setRelicCharges` 330).
- **Infusions** (208-305). `infusePhysicalByAlignment` adds a non-uninvokable infusion ability to an item, chosen by the caster's alignment on two axes. Good caster: `Prayer_InfuseHoliness`; evil: `Prayer_InfuseUnholiness`; neutral: `Prayer_InfuseBalance` (lines 211-223). Law axis: `Prayer_InfuseDiscipline`, `Prayer_InfuseImpunity`, `Prayer_InfuseModeration` (237-243). This only happens when the item has no deity infusion yet and the caster worships a deity (246-250). `infuseMobAlignment` (257-292) adds a `Prop_WearZapper` with text such as `+FACTION -EVIL -NEUTRAL` for good casters (263-266). `clearInfusions` (294-305) removes all of these.
- **Alignment gate** (`prayerAlignmentCheck`, 372-420; called from `invoke`, 422-430). The gate runs only when all of these hold (374-379): the cast is not automatic, the caster is a player (`!mob.isMonster()`), the ability is owned by the caster, the prayer is not "appropriate to my factions", and the prayer has at least one flag set. Then:
  - Holy and not unholy: threshold 1000, tested against the caster's **good** purity (382-396).
  - Unholy: threshold 0, tested against **evil** purity (388-389, 392-393).
  - Anything else: threshold 500, tested against **neutral** purity, minus 10 (398-400).
  - `CMLib.dice().rollPercentage() > basis` passes (403-404). Otherwise the caster sees one of three messages (406-416): "The goodness of X disrupts your prayer.", "The evil nature of X disrupts your prayer.", or "The non-good (or non-evil) nature of X disrupts your thought." The gate is a probability tied to how pure the caster's faction standing is. It runs after `StdAbility.invoke` and before the prayer's own proficiency roll.

### 2. The common cast pattern

Nearly every class does the same steps:

1. Resolve the target with `getTarget` or `getAnyTarget` (for example `Prayer_Deafness.java:132`). Some prayers take the room (`Prayer_DesecrateLand.java:110`), the caster (`Prayer_DeflectPrayer.java:251`), or a corpse (`Prayer_Desecrate.java:78-82`).
2. Refuse if the effect is already present (`Prayer_DivineLuck.java:140`, `Prayer_Earthshield.java:129`).
3. Call `super.invoke`. That is `Prayer.invoke`, which runs `StdAbility.invoke` and then the alignment gate.
4. Roll `proficiencyCheck(mob, adjustment, auto)`. Most use 0. Exceptions:
   - Deafness: `-(WIS*2)` (`Prayer_Deafness.java:139`).
   - Demonic Consumption: `-(levelDiff*15)`, where `levelDiff` is the target's level minus the caster's (plus `XLEVEL/2`), plus 6 for a mob target and floored at 0 (`Prayer_DemonicConsumption.java:116-121`).
   - Disenchant: `5 + 5*(caster level + 2*XLEVEL - item level)` (`Prayer_Disenchant.java:85-88`).
5. Build two `CMMsg`s: a verbal cast message (with `MASK_MALICIOUS` for harmful prayers) and a typed message such as `TYP_DISEASE`, `TYP_MIND`, `TYP_JUSTICE`, `TYP_UNDEAD`, `TYP_ELECTRIC` or `TYP_FIRE`. Both are offered to `room.okMessage` and then sent with `room.send` (for example `Prayer_Doomspout.java:257-266`).
6. Apply the effect only when `msg.value() <= 0`, which means no listener altered or cancelled the message (`Prayer_Doomspout.java:267`, `Prayer_Drain.java:113`).
7. On failure, call a `StdAbility` fizzle that speaks to the room: `beneficialWordsFizzle` (`StdAbility.java:2210-2220`), `maliciousFizzle`, or `beneficialVisualFizzle` (2222 onward).

Short example from Deafness:

```java
final boolean success=proficiencyCheck(mob,-((target.charStats().getStat(CharStats.STAT_WISDOM)*2)),auto);
```

### 3. Durations (`StdAbility.java`)

- **Beneficial effects** (`beneficialAffect`, 2341-2349). With an adjustment of 0 the duration is `casterLevel*4 + 40` ticks (`getBeneficialTickdownTime`, 2325). The caster level is capped at the target's level plus `EXPRATE` when the caster is a suspected abuser (2316-2324). A positive adjustment is an absolute tick count (2314-2315): `Prayer_DeflectPrayer.java:289` uses 15, `Prayer_DivinePerspective.java:156` uses 10, and `Prayer_DivineQuest.java:328` uses two hours of ticks (`CMProps.getTicksPerHour()*2`). An adjustment of 0 gives the standard duration, not an unlimited one.
- **Malicious effects** (`maliciousAffect`, 2185-2208). With an adjustment of 0 the duration is `round(adjustedLevel*1.1) + 25` ticks (`getMaliciousTickdownTime`, 2178). The fifth argument is an extra room check. Every call in this bucket passes `-1`, which skips the check (2190). A value of 0 or more sends a room message first and aborts the effect if it is vetoed or has `value > 0` (2190-2201). Users: Deafness (`Prayer_Deafness.java:149`), Doomspout (183, 272), DrunkenStupor (200), DevourerCurse (137).
- **Spread immunity** (`spreadImmunity`, 2351-2365). Adds a `TemporaryImmunity` effect named `+<prayer ID>` to the target so the same prayer is not reapplied straight away. Doomspout uses it (`Prayer_Doomspout.java:187`, 227, 276). Expiry of `TemporaryImmunity` is unverified.
- **Time units.** `TICKTIME` defaults to 4000 ms (`core/CMProps.java:2720-2722`), so a tick is about four seconds.

### 4. Effect hooks

Effects are copies of the prayer's ability object, so per-effect state lives in instance fields and in the misc text. `Prayer_Doomspout` keeps `plagueDown`, `godName` and `ispoke` (`Prayer_Doomspout.java:100-102`). `Prayer_DeflectPrayer` keeps `shield` and `timeToNextCast` (71, 109). `Prayer_DivineConstitution` keeps `conPts`, `xtraHPs` and `maxPoints` (89-91). `Prayer_DivineResistance` keeps `prots` and `permProts`, the set of tools already counted (89-90). `Prayer_DivineFavor` keeps `struckDownToday` (89). `Prayer_DreamFeast` keeps `ticksSleeping` (89). `Prayer_DivineQuest` keeps `quest1` and a parsed JSON `data` map (122-123). `Prayer_Designation` keeps `nameCheckCtr` (112) and stores the designation in the misc text (193-195). `Prayer_DesecrateLand` stores the caster's name in the misc text (129). The misc text is the string passed to `setMiscText`; the save and reload path is unverified, except that `Prayer_DivineQuest.java:162-178` and `Prayer_DreamFeast.java:92-96` both override it. The hooks used in this bucket are:

- **Stat changes** (`affectPhyStats`, `affectCharStats`, `affectCharState`):
  - Deafness sets `PhyStats.CAN_NOT_HEAR` in the sense mask (`Prayer_Deafness.java:98`).
  - Divine Constitution adds `conPts` to CON and to the max-CON adjustment, and `xtraHPs` to max HP (94-110).
  - Divine Resistance adds +100 to every saving throw (`Prayer_DivineResistance.java:110-111`).
  - Divine Luck adds `1 + ((level + 2*XL)/5)` to each saving throw (`Prayer_DivineLuck.java:95-96`), adds `((level + 2*XL)/5) + 1` to attack and to damage (109-110), and lowers armour by `5 + XL` while the victim is evil and the caster is in combat (112-117).
  - Earthshield lowers armour by `5 + adjustedLevel/4` (`Prayer_Earthshield.java:97`) and halves movement (104).
  - Demonshield lowers armour by `1 + 2*XL` (`Prayer_Demonshield.java:157`).
  - DrunkenStupor lowers attack by `level + 2*XL` (`Prayer_DrunkenStupor.java:108-110`) and DEX by 3 (117).
  - Devourer Curse renames the target to "*<name>, a horrid devourer*" (`Prayer_DevourerCurse.java:95`) and sets CHA to 3 (104).
  - Doomspout caps INT at 3 (`Prayer_Doomspout.java:199-200`).
  - Divine Guidance adds `10 + 2*XL` to attack and `5 + XL` to damage, and sets IS_GOOD (`Prayer_DivineGuidance.java:95-97`).
  - Armour is a "lower is better" value. `MUDFight.rollToHit` uses `myArmor = -defence` (`Libraries/MUDFight.java:411-416`), so a more negative armour value makes an attack roll less likely to hit. That is why the buffs above lower the number.
- **Veto or rewrite** (`okMessage`, which runs before the action):
  - Death's Door cancels the death message, restores the mob to full state, moves it to its start room, and removes its effects (`Prayer_DeathsDoor.java:96-115`). A caster with the "Dueling" ability is passed through (100-101).
  - Divine Favor scales experience changes by 0.9 for losses and 1.1 for gains, for worshippers only (`Prayer_DivineFavor.java:111-121`).
  - Electric Healing takes electric damage, heals half of it (`recovery = round(value/2)`, line 117), and vetoes the damage (`Prayer_ElectricHealing.java:113-120`).
  - Desecrate Land refuses a cast whose tool has `FLAG_HOLY` and not `FLAG_UNHOLY` (`Prayer_DesecrateLand.java:95-102`), with the message "This place is blocking holy magic!".
  - DrunkenStupor rewrites a targeted action so it lands on a random room occupant (`Prayer_DrunkenStupor.java:156-162`).
  - Divine Resistance counts blocked messages but does not veto them (see Problems, item 5).
- **Reacting to events** (`executeMsg`):
  - Divine Guidance ends at the first weapon hit that does damage (`Prayer_DivineGuidance.java:119-124`).
  - Designation ends when the undead is told to stop following (`Prayer_Designation.java:103-109`).
  - Demonshield burns a non-evil attacker who fails a `DEX*3` roll when acting at melee range, at most once per mob tick (`Prayer_Demonshield.java:119-143`).
  - Divine Perspective forwards "look" and "examine" to the deity's room, and forwards other messages from the caster's room to the caster, guarded by `noRecurse` (`Prayer_DivinePerspective.java:98-127`).

### 5. Tick-driven and reactive behaviour

- **Death Guard** (`Prayer_DeathGuard.java:90-128). Each tick, if the guarded mob is below 130% of its wimp threshold or at 30% of max HP or less (102-103), and the cleric is in the same room, the cleric casts the first available healing prayer: Heal, then Cure Critical, Serious or Light (107-113). The cleric's usage count is saved and restored around the cast (116-123), so the automatic heal does not use up the cleric's limits.
- **Death Knell** (`Prayer_DeathKnell.java:90-107). At 10% of max HP or less (99) the mob "succumbs to the death knell" and `CMLib.combat().postDeath` is called with the cleric as the killer (103).
- **Dream Feast** (`Prayer_DreamFeast.java:99-126). The effect counts ticks while the mob sleeps. After more than 8 ticks, it sends a dream message every ninth tick (106-110). When the mob wakes after that, hunger and thirst are set to their maximum (120-121) and the effect ends.
- **Doomspout** (`Prayer_Doomspout.java:117-191). Every 4 ticks (`plagueDown`, 124-127) the sufferer makes one of twelve preaching lines (133-167). `executeMsg` sets `ispoke` when the sufferer speaks (204-211). `ispoke` is cleared at 132 and then set again if the sermon is delivered as a speech (`CommonMsgs.postSay`, 640-672, sends the message to the room; `MSG_SPEAK` includes `TYP_SPEAK` as its minor type, `Common/interfaces/CMMsg.java:1463`). If the sufferer spoke this tick, a random occupant may be infected with `maliciousAffect`, after a save roll (`rollPercentage() > save vs disease`, 176-183). Otherwise that occupant is immunised (187).
- **Divine Favor** (`Prayer_DivineFavor.java:126-160). Each tick in combat, with a 1-in-1000 chance (`roll(1,1000,0)==1`, 133), the deity smites a victim who worships another deity. The deity is made the killer through `postDeath` (150). This happens once per effect (`struckDownToday`, 132, 144).
- **Earth to Mud** (`Prayer_EarthMud.java:97-111). Each tick, every occupant in combat loses one movement point (106).
- **Deflect Prayer** (`Prayer_DeflectPrayer.java:125-156). The effect ends if the shield is gone, not worn properly, or no longer held.
- **Designation** (`Prayer_Designation.java:115-133). Every 11 ticks (`nameCheckCtr > 10`, 119), the effect removes itself if the mob is not following anyone and is in the game.
- **Divine Quest** (`Prayer_DivineQuest.java:125-153). Each tick, the generated quest is checked. When the player wins, the effect ends with "You have successfully completed the divine quest." (146). If the quest is no longer running, the effect ends (138-139).

### 6. Deflect Prayer and shields

`Prayer_DeflectPrayer.java:159-220`. While held, a shield is checked on each message. When a malicious prayer is cast at the holder (180-187), the shield deflects it, and the effect ends (216-217) and cancels the cast (`return false`). If the shield wears out, its damage is the prayer's level minus `2*XL`, at least 1 (192-195). A shield under 10 uses prints "*X is nearly destroyed!*" (197-199). At 0 or less it is destroyed and its uses reset to 100 (201-214). The prayer can be recast every 15 ticks (76). It needs a non-enchanted shield in hand (`hasAppropriateItem`, 222-232) and a deity (272-276).

### 7. Deity quests

`Prayer_DivineQuest.java:265-349` (generator at 180-263).

- **Requirements.** The target must be a player (272-276). A caster who is not a deity must worship the same deity as the target (281-294). A deity caster uses its own name (278-279).
- **Generation.** Loads `resources/randareas/example.xml` (190). Sets `QUEST_CRITERIA` to target name and NPC (197-198), `DURATION` and `EXPIRATION` to one hour of ticks (199-200), and `MULTIAREA` to YES (201). Builds the script with `CMLib.percolator()` (203-221) and patches two script lines (229-230). Starts the quest (239-247). Retries up to five times (183-184). Each failure logs an error (193-261).
- **Effect.** A two-hour effect (328) that stores a JSON string with the quest name (326-327). `setMiscText` parses it (162-178) and looks the quest up again on reload. Failing the quest gives "You have failed the divine quest." (114, 118).
- **Mana.** `overrideMana` is 1 (72-74), so the cost is trivial. Mana is taken in `super.invoke` (299) before the quest is built. A failed build returns false after that (323-324). Whether mana is refunded is unverified.

### 8. Empowerment and the item economy

The ten `Prayer_Empower*` classes share one skeleton. `Prayer_EmpowerHolyWeapon.java` is a clean example (invoke at 89-177):

- **Gate.** Target must be a weapon (96-100). Refuse if `ability() > 4` (102-106) so an item can be empowered at most five times. Caster must worship a deity (108-113). `checkInfusionMismatch` must pass (115). Alignment refusal: Holy refuses evil items (120-124).
- **Cost.** Experience. `getXPCOSTAdjustment(mob, 50)` is charged through `postExperience` (129-130), and the prayer fails if nothing could be charged (133-137). Mana is `COST_ALL` (`overrideMana`, 78-81).
- **Result.** Ability tier +1 and level +3 (148-149). Disposition loses IS_EVIL and gains IS_GOOD and IS_BONUS (150-151). A `Prop_WearZapper` is added with a mask such as `+FACTION -EVIL -NEUTRAL -DEITY "+<DEITY>"` (156), which limits wearers to that faction and deity. Existing zappers are extended instead (161-166).
- **Variants.**
  - Weapons: Foul (chaos, refuses lawful), Just (law, refuses chaotic), Modest (moderate, refuses lawful or chaotic), Holy (good, refuses evil), Sacred (neutral, refuses good or evil).
  - Armour: Holy (good, refuses evil, must be a non-shield armour piece worn on an allowed body location, lines 96-110), Sacred (neutral, refuses good or evil). Both use the same location bitmask.
  - Shield (`Prayer_EmpowerShield.java`): tier +1, level +3, then `infuseMobAlignment` and `infusePhysicalByAlignment` (145-149). Refuses the four "repulsive" alignment pairs (108-115).
  - Relic (`Prayer_EmpowerRelic.java`): no experience cost. Requires a relic with charges left (105-115). Level +1 and one charge used (148-151). Refused if the relic's level is already at or above the caster's adjusted level (133-136). Applies the same infusions (152-153).
  - Scroll (`Prayer_EmpowerScroll.java`): must be a divine scroll with uses left (99-121). Scroll level +1 and one use spent (141-143). Same level-cap refusal (127-129).

### 9. Relics and scrolls released by depletion

- **Deplete Scroll** (`Prayer_DepleteScroll.java:88-196`). Works only on divine scrolls (117-130). For each scroll use it creates a temporary factory mob in the room (`CMClass.getFactoryMOB`, 161), gives it the prayers that the caster's factions accept (152-154), and casts each one from that mob (163-171) at the cost of one scroll use per prayer (165-166). The mob is removed in a `finally` block (173-182). The scroll's spell list is then cleared (185) and the caster gets 25 + 5*XL experience (186). The prayer is not restricted by alignment here, only by faction fit.
- **Deplete Relic** (`Prayer_DepleteRelic.java:94-169`). Costs 100 mana (70-72). Every prayer stored in the relic is cast once from a temporary mob (130-145). Then charges go to 0, the magic is removed, and infusions are cleared (158-160). Experience is 25 + 5*XL (161).

### 10. Room and world state

- **Desecrate Land** (`Prayer_DesecrateLand.java:107-143`). Stores the caster's name as the effect text (129). If the caster owns the land, the effect is attached to the room as a permanent non-uninvokable effect and the room is saved to the database (133-134). Otherwise it is a normal beneficial effect on the room (137). Blocks holy-only prayers in the room (95-103).
- **Earth to Mud** (`Prayer_EarthMud.java:133-166`). Refused indoors, in the air domain, in city rooms or in water: "That magic won't work here." (134-145; also `castingQuality` 114-131). Success: "The ground here turns to MUD!" (157) and a beneficial effect on the room (158). While active, the room's weight becomes `weight*2 + 1` (93) and combatants lose movement each tick (106). Its end message is "The mud in '<room>' dries up." (85).
- **Desecrate** (`Prayer_Desecrate.java:76-132`). Destroys a dead body (123). A corpse that is someone else's player corpse with content is protected (92-98). An evil caster gains a small amount of XP (base 5, 115), set to 0 when the level gap exceeds `EXPRATE` (117-119).

### 11. Relationship and social effects

- **Designation** (`Prayer_Designation.java:136-204`). Gives a non-player undead in the caster's group a one-word name with no spaces (143-154). The name is stored as the effect text and applied in `affectPhyStats` (88-93). The displayed name is built as "*A/AN/SOME <name> named <X>*" or "*<X>, <name>*" (167-174). Refused if the name would duplicate an existing one (176-180).
- **Disown** (`Prayer_Disown.java:108-225`). Targets one of three kinds of object (120-163):
  - A caged animal with an `Age` effect.
  - A child-stage group member with `Age`, `Prop_SafePET` and a `PARENT:` tattoo.
  - A group member with a `Loyalty` effect.

  On success it removes `Prop_SafePET`, `Loyalty`, `Age` and all `PARENT:` tattoos (186-191, 197-201). It renames the mob (fixName path, 178-185) or uncages and renames the animal (203-215). Experience is 5 (218). The `PARENT:` tattoos are written by `Prayer_Adoption.java:305, 309` and `Pregnancy.java:478, 485`.
- **Divorce** (`Prayer_Divorce.java:70-172`). Needs a married target (75-78) who is not wearing a "wedding band" (80-84). On success the Divorces channel is told (105-107), both liege IDs are cleared (108-111), land titles move from one spouse's name to the other's (117-122), and bank deposits are re-keyed to the other spouse (125-159, via `BeanCounter.java:1221` and `1402`). Coins are summed (146-147). A statistic is bumped for non-monster, non-soulmate targets (95-96).

### 12. Messages shown to players

Most messages are short and set in the caster's own voice. Representative ones:

- "Your hearing returns." (Deafness, `Prayer_Deafness.java:112`), "You feel sober now." (DrunkenStupor, 175), "Your divine luck is over." (DivineLuck, 131), "The ground here turns to MUD!" (EarthMud, 157), "This place is blocking holy magic!" (DesecrateLand, 101), "That magic won't work here." (EarthMud, 143).
- Fizzles often name the deity: "*<S-NAME> pray(s) at <T-NAMESELF>, but nothing happens.*" (Designation, 200).
- Failed prayers reach the room (the fizzles speak to the room, `StdAbility.java:2213-2218`).

## Content worth mining

### Catalogue of the 52 prayers

Columns: domain (`DOMAIN_*` minus prefix), quality (`QUALITY_*` minus prefix), alignment flags, target, effect, and line count. Notation: `L` is the caster's adjusted level (`adjustedLevel`), `X` is the caster's XLEVEL bonus (`getXLEVELLevel`), and `X1` is `getX1Level`. Their definitions are in `StdAbility` and were not read.

| Prayer | Domain | Quality | Flags | Target | Effect | Lines |
|---|---|---|---|---|---|---|
| Deafness | CORRUPTION | MALICIOUS | UNHOLY | mob | Cannot hear; proficiency penalty `-2*WIS` | 159 |
| DeathGuard | HEALING | BEN_OTHERS | HOLY | mob | Cleric heals the guarded mob when low (Heal, CureCritical, CureSerious, CureLight) | 194 |
| DeathKnell | CURSING | MALICIOUS | UNHOLY | mob | Kills at 10% HP or below; uses beneficial duration | 150 |
| DeathsDoor | HOLYPROTECTION | BEN_SELF | HOLY | self | Next death restores and strips effects (Dueling excepted) | 168 |
| Deathfinger | VEXING | MALICIOUS | UNHOLY | mob | Mana 150; damage `1d(L)+64`, plus `HP/10` per level above the target | 132 |
| DefileShield | (from ImbueShield) | (inherited) | UNHOLY, NOORDERING | shield | Imbues a shield with a malicious prayer; XP `500 + 50*spell level` | 96 |
| DeflectPrayer | SHIELDUSE | BEN_SELF | none | self | Held shield deflects malicious prayers; 15-tick effect; recast 15 ticks | 304 |
| DivineQuest | BLESSING | INDIFFERENT | NEUTRAL | player | Deity commissions a generated quest; 2-hour effect | 350 |
| Doomspout | EVANGELISM | MALICIOUS | UNHOLY | mob | Disease: preaching, spreads when the sufferer speaks; INT capped at 3 | 285 |
| Disown | CORRUPTION | OK_OTHERS | UNHOLY | child NPC or caged animal | Removes child status and renames | 226 |
| DrunkenStupor | CURSING | MALICIOUS | NEUTRAL, INTOXICATING, MINDALTERING | mob | Wraps Inebriation; attack and DEX penalties; targeted actions can misfire | 211 |
| Designation | DEATHLORE | OK_OTHERS | UNHOLY | undead NPC in group | Single-word name; ends if the mob stops following | 205 |
| DepleteScroll | NEUTRALIZATION | MALICIOUS | UNHOLY | scroll | Casts a scroll's faction-appropriate prayers once per use, then clears it | 198 |
| DivineFavor | COMMUNING | BEN_SELF | NEUTRAL | self | XP gain x1.1, loss x0.9; 1/1000 per tick smite; ends on death | 194 |
| DivineConstitution | HEALING | BEN_OTHERS | HOLY | mob | Overheal adds permanent CON and max HP, up to a cap | 193 |
| DivineResistance | HOLYPROTECTION | BEN_OTHERS | HOLY | mob | +100 to saves; 4 counted blocks, one per distinct tool | 193 |
| Demonshield | HOLYPROTECTION | BEN_OTHERS | UNHOLY, HEATING, FIREBASED | mob | Armour `-(1+2X)`; non-evil melee actors burned when they fail a DEX*3 roll | 190 |
| EmpowerHolyArmor | BLESSING | INDIFFERENT | HOLY | armour | Tier +1, level +3, good, deity zapper | 187 |
| EmpowerSacredArmor | BLESSING | INDIFFERENT | NEUTRAL | armour | As above for neutral; refuses good and evil | 187 |
| DemonicConsumption | VEXING | MALICIOUS | UNHOLY | mob or item | Destroys the target (mob takes HP x100 damage) | 184 |
| EmpowerFoulWeapon | BLESSING | INDIFFERENT | CHAOS | weapon | Chaotic; refuses lawful | 179 |
| EmpowerJustWeapon | BLESSING | INDIFFERENT | LAW | weapon | Lawful; refuses chaotic | 179 |
| EmpowerModestWeapon | BLESSING | INDIFFERENT | MODERATE | weapon | Moderate; refuses lawful and chaotic | 179 |
| EmpowerHolyWeapon | BLESSING | INDIFFERENT | HOLY | weapon | Good; refuses evil | 177 |
| EmpowerSacredWeapon | BLESSING | INDIFFERENT | NEUTRAL | weapon | Neutral; refuses good and evil | 177 |
| Divorce | CORRUPTION | OK_OTHERS | UNHOLY | married mob | Ends marriage; moves land titles and bank deposits | 174 |
| DepleteRelic | NEUTRALIZATION | MALICIOUS | UNHOLY | relic | Mana 100; casts all stored prayers once; charges to 0 | 170 |
| EarthMud | CREATION | MALICIOUS | NEUTRAL | room | Room becomes mud; refused indoors, city, water, air | 167 |
| DivineLuck | HOLYPROTECTION | BEN_SELF | NEUTRAL | self | Saves, attack and damage bonus; armour `-(5+X)` vs evil foe | 166 |
| DivinePerspective | COMMUNING | OK_SELF | HOLY | self | Caster sees the deity's room; 10 ticks | 166 |
| EmpowerRelic | BLESSING | INDIFFERENT | NEUTRAL | relic | Level +1, one charge used; level cap | 162 |
| ElectricHealing | HEALING | BEN_SELF | NEUTRAL, HEALINGMAGIC | self | Electric damage healed at half value | 161 |
| EmpowerShield | BLESSING | INDIFFERENT | NEUTRAL | shield | Tier +1, level +3, infusions | 160 |
| DivineGuidance | COMMUNING | BEN_OTHERS | HOLY | mob | Attack `+10+2X`, damage `+5+X`, good; ends at first weapon hit | 159 |
| Earthshield | HOLYPROTECTION | BEN_SELF | NEUTRAL | self | Armour `-(5+L/4)`; movement halved | 158 |
| DreamFeast | RESTORATION | OK_OTHERS | HOLY | mob | After 8+ ticks asleep, restores food and water on waking | 156 |
| EmpowerScroll | BLESSING | INDIFFERENT | NEUTRAL | scroll | Scroll level +1, one use spent | 152 |
| DevourerCurse | CURSING | MALICIOUS | UNHOLY | mob | Renamed "*a horrid devourer*"; CHA 3 | 148 |
| DesecrateLand | WARDING | MALICIOUS | UNHOLY | room | Refuses holy-only prayers; owned land saved to the database | 145 |
| Desecrate | DEATHLORE | INDIFFERENT | UNHOLY | corpse | Destroys a body; small XP for evil casters | 133 |
| Drain | VEXING | MALICIOUS | UNHOLY | mob | Damage `1d(L)`; caster heals the same amount | 131 |
| DispelChaos | NEUTRALIZATION | MALICIOUS | LAW | mob | Gates inverted; see Problems | 122 |
| DispelGood | NEUTRALIZATION | MALICIOUS | UNHOLY | mob | Damage `2d(L+4)+10` to good targets; halved if blocked | 122 |
| DispelLaw | NEUTRALIZATION | MALICIOUS | CHAOS | mob | Gates inverted; see Problems | 122 |
| DispelEvil | NEUTRALIZATION | MALICIOUS | HOLY | mob | Damage `2d(L+4)+10` to evil targets | 119 |
| DispelUndead | NEUTRALIZATION | MALICIOUS | HOLY | mob | Damage `1d(L+15)+L/2` to undead | 118 |
| Discipline | EVANGELISM | OK_OTHERS | LAW | mob | Shifts the target's law-inclination faction by `10d(L)+10X` | 111 |
| Disenchant | NEUTRALIZATION | INDIFFERENT | NEUTRAL | item | Calls `disenchantItem`; proficiency bonus from level gap | 107 |
| ElectricStrike | CREATION | MALICIOUS | NEUTRAL | mob | Electric damage `1d(L)+5` | 106 |
| Ember | CREATION | MALICIOUS | UNHOLY, FIREBASED | mob | Fire damage `1d(L)+3+X1` | 103 |
| DoomAura | CORRUPTION | (inherited) | UNHOLY | self | Aura; extends `Prayer_BladeBarrier`, burning undead-type damage | 82 |
| ElementalPortal | COSMOLOGY | (inherited) | NEUTRAL | room | Planar "inner" portal; extends `Prayer_MalignedPortal` | 81 |

### Groups worth lifting

- **The Dispel family** (`Prayer_DispelEvil.java`, `_DispelGood.java`, `_DispelUndead.java`) is one template: a gate on the target's alignment, a verbal message plus a justice-typed message, and a damage roll that halves when either message is blocked (`Prayer_DispelEvil.java:95-108`). Only the gate and the formula change.
- **The alignment table of the Empower family** (weapons, armour, shield, relic). Each variant has a single alignment it produces and a list of alignments it refuses, so the table is data.
- **Curses and afflictions:** Deafness, Death Knell, Devourer Curse, Drunken Stupor, Drain and Doomspout. Each is a short class that applies a malicious effect with one changed stat or hook.
- **Room states:** Earth to Mud, Desecrate Land. Both are room-level effects that change movement, weight, or which magic is allowed.
- **Messages:** the bucket has consistent voice and structure. Doomspout's twelve sermon lines are in the same tone as the rest; they are not reproduced here.

## Notable design ideas

1. **Veto and rewrite as the core mechanic.** Prayers attach effects that change other people's actions before they happen: a death that cancels itself (Death's Door), lightning that heals (Electric Healing), a room that refuses holy magic (Desecrate Land). The two-phase message flow in the base game makes this possible. These are good examples of "world rules" that a player can discover.
2. **Faith as a probability.** The alignment gate (`Prayer.java:372-420`) turns the caster's standing into a chance of failure, with a message that explains why. Players learn that a pure caster's holy prayers are reliable.
3. **Empowerment as progression.** Each empowerment adds ability tier, level, alignment tags and a deity-bound wear restriction, and it is capped at five tiers. The mismatch refusals make alignment a real choice.
4. **Consumable spell batteries.** Scrolls and relics hold prayers. Depleting one releases them through a temporary caster in the room (`Prayer_DepleteScroll.java:161-182`). This is a neat way to model item-stored spells.
5. **Remote perception through a deity.** Divine Perspective attaches its effect to the deity object, so the caster sees the deity's room (`Prayer_DivinePerspective.java:138-156`). It is an unusual use of the message system.
6. **Speech-carried disease.** Doomspout spreads only when the sufferer speaks (`Prayer_Doomspout.java:204-211`). This makes a contagion depend on in-world behaviour.
7. **Automatic healing that does not cost the healer.** Death Guard saves and restores the cleric's usage counts around each heal (`Prayer_DeathGuard.java:116-123`).
8. **Retaliation with a dice roll.** Demonshield burns non-evil melee attackers when their hit roll exceeds `DEX*3` (`Prayer_Demonshield.java:119-143`), once per mob tick.

## Problems and cruft

1. **Repeated argument in Deflect Prayer.** The success and fizzle messages use `@x1` twice with arguments `(prayWord, shield.name())` (`Prayer_DeflectPrayer.java:285` and 299). `CMStrings.replaceVariables` (1955-1980) substitutes the Nth argument for `@xN`, so both occurrences become the prayer word and the shield name never appears. The second placeholder should be `@x2`.
2. **Inverted gates in Dispel Chaos and Dispel Law.**
   - `Prayer_DispelChaos.java`: `castingQuality` favours chaotic targets (76-77), but `invoke` only proceeds for lawful targets (95). The damage branch then requires a chaotic target (107). Both checks read the same alignment data (`Libraries/Sense.java:798-838`), so the damage branch is effectively unreachable. An automatic cast on a chaotic target always fizzles.
   - `Prayer_DispelLaw.java` mirrors this: quality favours lawful targets (76-77), `invoke` requires chaotic (95) and damages lawful targets (107). Its message says "The chaotic spell" (110) for lawful targets.
3. **Disown egg check uses the wrong race.** `Prayer_Disown.java:207` tests `mob.charStats().getMyRace()`, which is the caster. The animal's race is `R` (203) and is used for the name on the next line. Likely a bug (unverified at runtime).
4. **Death Knell uses the wrong duration path.** It is a malicious-quality curse applied with `beneficialAffect` (`Prayer_DeathKnell.java:141`). The message "for a a death knell curse" (145) has a doubled "a".
5. **Divine Resistance counts but does not veto.** `Prayer_DivineResistance.java:135-145` decrements `prots` and shows "divine protection glows!" but returns `super.okMessage` unchanged. The real protection is the +100 to saves (110-111), which the combat code must read (not verified). The "four blocks" are a counter, not a veto.
6. **Dispel Undead returns success after a blocked cast.** It has no `else` for a blocked message (`Prayer_DispelUndead.java:102-111`). Dispel Good returns false in that case (113-114).
7. **Copy-paste and dead code in the Empower family.**
   - Every alignment refusal says "You can not empower that repulsive weapon." (or armour, or shield).
   - Each class calls `target.text();` and discards the result (for example `Prayer_EmpowerFoulWeapon.java:169`).
   - The ability cap reads `phyStats().ability()` (102) but increments `basePhyStats()` (148).
8. **Deity quest copy-paste and mana.** "You must worship a deity to begin the imbuing." (`Prayer_DivineQuest.java:286`) reuses imbuing text. A commented-out block for scripted instructions is left in (332-341). Mana is spent before the quest build can fail (299, 323-324).
9. **Divine Perspective is fragile.** The effect lives on the deity object (138, 156). Cross-room behaviour relies on `noRecurse` and on message forwarding in both directions (98-127). Duration is only 10 ticks.
10. **Desecrate Land's test.** It refuses prayers with HOLY and without UNHOLY (`Prayer_DesecrateLand.java:98-99`). A prayer with neither flag passes.
11. **Missing null check.** `Prayer_Deathfinger.java:108` uses `target.location()` without a null check before `okMessage`.
12. **Drunken Stupor's random target.** The redirect picks any occupant index (`Prayer_DrunkenStupor.java:161`), which can be the drunk. Unverified whether this is intended.
13. **Blunt cleanup on death.** Death's Door removes every effect on the mob (`Prayer_DeathsDoor.java:107-112`), not only its own. Curses and buffs are lost along with the death.

Multiplayer assumptions: Divorce posts to a player channel and moves bank records between two named players. Disown, Designation, Discipline and Divine Quest all depend on group membership or on another player being the target. Death's Door checks for the Dueling ability. Divine Favor smites other worshippers, which is player-versus-player logic.

## Takeaways for a Python SUD

- **One data row per prayer.** A dataclass with `id`, `domain`, `quality`, `alignment` (a set), `target` (`mob`, `item`, `room`, `self`), `mana`, `proficiency_mod` and `effect` (a key into a registry of effect classes). About twenty of the fifty-two are parameter variants of a few templates (the Dispel family, the Empower family, the curses). Those can be data rows with no class at all.
- **Keep the two-phase message flow in simple form.** Each action produces an event. Effects get `on_before(event) -> bool` (return False to cancel, like `okMessage`) and `on_after(event)` (like `executeMsg`). Death's Door, Electric Healing and Desecrate Land then become ordinary effects.
- **Durations and dice.** Port these exactly, but beware of Java's `Math.round`, which rounds half up while Python's `round` rounds half to even:

```python
import random

def roll(n: int, sides: int, mod: int = 0) -> int:   # mirrors Dice.roll (Dice.java:398-406)
    if sides <= 0:
        return mod
    return sum(random.randint(1, sides) for _ in range(n)) + mod

def beneficial_ticks(caster_level: int) -> int:      # StdAbility.java:2325
    return caster_level * 4 + 40

def malicious_ticks(caster_level: int) -> int:       # StdAbility.java:2178
    return int(caster_level * 1.1 + 0.5) + 25        # Java Math.round semantics
```

- **Replace the alignment purity system with one number.** Use a single alignment value and a faith value. Keep the probabilistic "disrupts your prayer" check (`roll > purity`) because it is cheap and gives feedback. Use the same thresholds (1000, 500, 0).
- **Keep the empowerment loop, drop the mask language.** An item with a tier (cap 5), level +3 per tier, a set of alignment tags, and an optional "bound to god" flag gives progression and a reason to choose a deity. Drop `Prop_WearZapper`'s mask grammar and the faction lookups; use a simple allowed-set on the item.
- **Relics and scrolls as consumables.** Store prayers on the item. Depleting it casts each stored effect once in the room. No temporary mob is needed in Python; call the effect directly.
- **Room states are a good single-player mechanic.** A timed room flag such as "desecrated" or "muddy", with a movement modifier and a rule that refuses one class of spell, gives the world memorable rules. Model the rooms as data in the same way.
- **Doomspout's speech contagion** fits a single-player game as an NPC-only mechanic: an afflicted NPC spreads a condition only when it speaks.
- **Relationships.** Disown and Designation map onto a single-player companion or pet system (a named undead follower, a child stage that ends at adulthood). Divorce, the channel announcement and the bank transfer belong to multiplayer; drop them or reduce them to a flag.
- **Drop or rethink:** group checks, Dueling, the channel broadcast, the deity smite between worshippers, and the cross-room look (Divine Perspective) unless a single-player scry effect is wanted.
- **Quest generation** can be a template file (like `randareas/example.xml`) with variables filled in at runtime. The retry-five-times loop and error logging are worth copying; the rest is multiplayer-specific.

## Coverage

- **Read in full (all 52):** every file in the bucket, from first to last line, except the licence and import header blocks, which were skimmed.
- **Read in full, outside the bucket:** `Prayer.java` (432 lines), the base class.
- **Read in part (targeted ranges):** `StdAbility.java` (2174-2231, 2312-2365), `Libraries/Sense.java` (665-690, 785-838), `Libraries/MUDFight.java` (345-432), `Libraries/Dice.java` (398-406), `core/CMStrings.java` (1940-1980, 2002-2010), `Libraries/CommonMsgs.java` (500-510, 640-672), `Libraries/BeanCounter.java` (1221-1249, 1402-1430), `core/CMProps.java` (2720-2722), `MOBS/interfaces/Deity.java` (line 49), and the `PARENT:` tattoo writers (`Prayer_Adoption.java:305, 309`, `Pregnancy.java:478, 485`).
- **Existence only:** `Prop_WearZapper.java`, `Inebriation.java`, `resources/randareas/example.xml`, `Prayer_Heal.java`, `Prayer_Sacrifice.java`, `Prayer_BladeBarrier.java`, `Prayer_ImbueShield.java`, `Prayer_MalignedPortal.java`.
- **Skipped:** none within the bucket.
- **Unverified:** the experience cost scaling `getXPCOSTAdjustment`; the `XLEVEL`, `X1` and `adjustedLevel` definitions; `TemporaryImmunity` expiry; whether `CombatLibrary.postDamage` carries `TYP_ELECTRIC` into the message's source minor (which Electric Healing needs); whether the combat code reads the +100 saving-throw bonus; mana refund on a failed quest build; the Drunken Stupor redirect including the drunk.
