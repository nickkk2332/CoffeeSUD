# Properties (part 1): the Property base, stat adjusters, gates and creature rules

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b075` (Abilities/Properties [part 1/4: Prop_AbilityImmunity .. Prop_HaveAdjuster]) · 23 files · 8805 lines of Java.

## Summary

A CoffeeMud "property" is an attachable rule. `Property` is an `Ability` whose classification is `ACODE_PROPERTY`, and it is attached to a mob, item, room, exit or area. Once attached it reacts through the same hooks spells use: it changes computed stats, vetoes or edits messages, and ticks. Each class reads its own configuration from the text string in `miscText`, so a builder can write rules directly on an object, for example `str+2 dex-2 class=thief MASK=-Race +Dwarf`. This bucket holds the base class and the stat adjusters (`Prop_HaveAdjuster`, `Prop_Adjuster`), damage and immunity rules, shop-hour and season gates, movement limits (crawlspace, climbing), a companion link (familiar), a level scaler (doppleganger), unique artifacts, and a set of one-off rules. The pattern of "one small rule object per thing, configured by a string, with a veto hook" is worth borrowing. Several classes carry defects (a tone-scaling gate that never fires for negative armor, a death-removal default that contradicts the help, copy-paste guards), so the code should not be copied literally.

## Key files and types

Paths are relative to the CoffeeMud folder (`ref/mudlibraries/CoffeeMUD`). Line counts are from the manifest.

| File | Role | Lines |
|---|---|---|
| com/planet_ink/coffee_mud/Abilities/Properties/Property.java | Base class of every `Prop_` class: ID `Property`, classification `ACODE_PROPERTY`, type mask check | 173 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_HaveAdjuster.java | Central stat adjuster for items (applies to the holder), with ALLSET set bonuses, MASK, MULTIPLY modes and TONEUP/TONEDOWN tuning | 1535 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Adjuster.java | Subclass of the adjuster for mobs, rooms, exits and areas; no item-only or set logic | 98 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_AbilityImmunity.java | Vetoes abilities and diseases by name, with optional custom messages | 207 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_AbsorbDamage.java | Percent or flat damage absorption by weapon type, ability class, domain, ID, flag or level | 620 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_AddDamage.java | Extra damage (percent and flat) on the owner's attacks; counter-damage for armor and shields | 393 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Fumble.java | Chance to make a named ability or weapon attack fail; can cast spells on the user | 322 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_FightSpellCast.java | Items that cast spells during combat (extends Prop_SpellAdder, covered in b076) | 328 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_CloseCommand.java | Adds close-command phrases through the unrecognised-command message; `noclose` | 192 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_ClosedDayNight.java | Shop and room hours: closed at night, lock-up, sleep or sit, go home, warnings | 586 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_ClosedSeason.java | Visibility and doors closed by weather, time-of-day or season codes | 193 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Crawlspace.java | Height limit for passing through a room or exit; forces sitting or crawling | 140 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Climbable.java | Requires a ladder or climbing to move through; thrown or dropped items fall | 178 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_EnlargeRoom.java | Adds or scales weight and height of a room or area | 134 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_AreaForSale.java | Land title on an area: owner, price, rent, theft flag | 421 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Familiar.java | Animal companion linked to a master, with per-animal stat bonuses | 525 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_AstralSpirit.java | Incorporeal form: race swap and restrictions on acting | 205 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Doppleganger.java | Scales mob and item levels to the players present | 395 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_ClanEquipment.java | Clan-owned items: elemental damage, shield thorns, secret-word wands, save bonuses | 462 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_CombatAdjuster.java | Multiplicative combat stats written by the CombatAbilities behaviour | 206 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_EnterAdjuster.java | Permanent stat, money and spell changes when a mob enters a room or exit or sits on a rideable | 347 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_CommonTwister.java | Rewrites what gathering skills and Speculate produce | 245 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_AffectReTick.java | Rescales remaining TICKDOWN of named effects in a scope (room, owner, area) | 457 |
| com/planet_ink/coffee_mud/Abilities/Properties/Prop_Artifact.java | Unique items saved to the database across shutdown and room reset | 616 |

Other files consulted: `Abilities/ThinAbility.java` (665 lines, the default no-op `Ability` implementation that `Property` inherits), `Abilities/interfaces/Ability.java` (flag and code constants), `Libraries/TimsLibrary.java` (the item balancer that tunes adjusters).

## How it works

### 1. The base class: Property on ThinAbility

**Data model.** The configuration string lives in `miscText` (`Abilities/ThinAbility.java:41`) and is set through `setMiscText`. Each subclass parses it there and caches the result in fields. `affected` is the host object the property is attached to (`Abilities/ThinAbility.java:42`).

**Type mask.** `canAffectCode()` is a bit mask of host types: `CAN_MOBS`=1, `CAN_ITEMS`=2, `CAN_AREAS`=4, `CAN_ROOMS`=8, `CAN_EXITS`=16 (`Abilities/interfaces/Ability.java:1100-1108`). `Property.canAffect(Physical)` checks the host's class against that mask (`Abilities/Properties/Property.java:146-164`). The base returns 0 (`Property.java:66-69`), so subclasses must declare what they attach to. `Prop_CombatAdjuster` declares 0 and is attached by code, not by the engine (see §5).

**Classification and flags.** `classificationCode()` returns `ACODE_PROPERTY` (`Property.java:105-109`, `Ability.java:681`). `flags()` carries loose tags. `FLAG_ADJUSTER` (`Ability.java:986`, value 524288) is set on many non-adjuster properties too (`Prop_ClosedDayNight.java:95`, `Prop_Climbable.java:65`, `Prop_Familiar.java:108`, `Prop_EnterAdjuster.java:85`), so it is closer to "changes stats or time" than to a precise type. `TimsLibrary.getBaseAdjusterAbility` uses it to find the adjuster to tune (`Libraries/TimsLibrary.java:1344-1362`). `FLAG_RESISTER` (`Ability.java:988`, value 1048576) marks immunities (`Prop_AbilityImmunity.java:87-90`).

**Hook surface.** The hooks are the same as for spells, and `ThinAbility` provides no-op defaults (`Abilities/ThinAbility.java:604-634`):

- `affectPhyStats(host, PhyStats)`, `affectCharStats`, `affectCharState`: called while the host's stats are recomputed.
- `okMessage(host, msg)`: return false to veto the message. The default is true (`ThinAbility.java:625-628`).
- `executeMsg(host, msg)`: runs after a message has been accepted.
- `tick(ticking, tickID)`: periodic. The default returns true (`ThinAbility.java:631-634`).
- `triggerMask()` (on the `TriggeredAffect` interface) tells the engine which trigger events a property wants.
- `getStat`/`setStat`: the tuning contract (see below).
- `accountForYourself()`: the text shown when the object is identified.

**Attachment and propagation.** `startTickDown` adds the effect only if no effect with the same ID is already present (`ThinAbility.java:338-343`). `bubbleAffect()` defaults to false (`ThinAbility.java:136-139`). When it is true, the holder receives the effect. `StdItem.affectPhyStats`, `affectCharStats` and `affectCharState` forward to every attached ability whose `bubbleAffect()` is true, and pass the holder as `affected` (`Items/Basic/StdItem.java:665-669`, with the same pattern in the other two methods). A mob's `recoverPhyStats` calls `affectPhyStats` on each carried item (`MOBS/StdMOB.java:826`, `core/interfaces/EachApplicable.java:36-47`). So a carried item's adjuster is evaluated against whoever is carrying it. Bubbling is enabled in `Prop_HaveAdjuster.java:58-61`, `Prop_ClanEquipment.java:57-60`, and in `Prop_Adjuster.java:56-58` only when the host is an Area.

**Config language.** Most classes use the shared parsers in `core/CMParms.java`. `getParmPlus` returns a signed integer, so `ARM-5` yields -5, and a `=` right after the key yields 0 (`core/CMParms.java:2761-2800`). Every adjuster-family class depends on this sign rule. Zapper masks (`MASK=...`) are compiled with `CMLib.masking()`, for example `Prop_ClosedDayNight.java:102-112` and `Prop_HaveAdjuster.java:300-304`. Mask semantics are in MUDZapper, which is outside this bucket.

**Power estimate and tuning contract.** `getStat("STAT-LEVEL")` returns an estimate of how powerful the property is. `TimsLibrary.timsLevelCalculator` sums it (`Libraries/TimsLibrary.java:209`). The balancer then lowers or raises items until the estimate reaches a target (`Libraries/TimsLibrary.java:516-555`, `:636-642`). It uses `setStat("TONEDOWN"...)` and `setStat("TONEUP"...)` with a percentage such as "90%" or "110%". Several properties implement these, and others ignore them (for example `Prop_FightSpellCast.java:276-305`, whose bodies are commented out).

### 2. Stat adjusters: Prop_HaveAdjuster (and Prop_Adjuster)

**Configuration grammar.** `setMiscText` (`Prop_HaveAdjuster.java:290-460`) reads a set of loose parameters. The parser `CMParms.parseLooseParms` is given the list of allowed keys (`:309-314`, `:325`).

- Terms of the form `<key><+|-><number or math expression>` (`addIfPlussed`, `:99-140`, which accepts plain numbers and math expressions). Phys keys: `abi arm att dam dis lev rej sen spe wei hei` (`:362-378`). Base and max stats: `str dex con cha wis int` and `max` prefixed forms (`:416-421`). Saves: `save` plus three letters (`:423-426`). State keys: `hit`/`hp`, `hun`, `man`, `mov`, `thi` (`:428-434`). `chr` (`:455`).
- Identity keys (not signed): `gen=M|F|N` (`:381-387`), `cla=` (class, must be available, `:394-403`), `cls=` (class level, `:404-409`), `rac=` (`:410-415`), `DEITY=` (`:388-393`).
- `dis` and `sen` take verbs. `getComplexPhyTerm` maps a verb to a bit position by substring match on the code names (`:254-288`, match at `:276`). A signed value means set (+) or clear (-) those bits, as the phyStuff code shows (`:534-543`, `:554-563`).
- Ability tuning lists: `ABLEPROFS`, `ABLELVLS`, `ABLETICKS`, `ABLEHPCOSTS`, `ABLEMNCOSTS`, `ABLEMVCOSTS` take `ability(number)` items separated by commas (`:192-222`, `:449-454`).
- Flags: `MULTIPLYPH` and `MULTIPLYCH` (`:355-356`), `RONDEATH` (`:339`), `ALLSET=name-N` (`:340-354`), `AMBIANCE+x` or `AMBIANCE-x` (`:168-190`), `ALLSAVES` (`:436-448`).
- Output: three typed lists, `phyStatsChanges`, `charStatsChanges` and `charStateChanges`, built at `:362-458`. Identity changes use `Character` tags: `G` gender, `C` class, `S` class level, `R` race, `W` deity (`adjCharStats`, `:807-827`). Ability adjustments are `Pair` objects that are added twice, with the comment "have to add twice, because thats how charadjs work" (`:212-214`).

**Applying the changes.**

- `phyStuff` (`:498-636`) applies phys changes. In additive mode it adds the value (`:582-632`). Speed uses `speed + value` (`:630-632`).
- In multiply mode (`MULTIPLYPH`, `:508-575`), `att`, `dam`, `wei` and `hei` are scaled by value/100 (`:529`, `:532`, `:565`, `:568`). Armor is computed on a 100-based scale: `baseAmt = 100 - armor` and `armor = 100 - round(baseAmt * pct/100)` (`:521-523`). In effect, a higher percentage means better protection. `spe` in multiply mode is a plain factor, so 1.5 means times 1.5 (`:571`), which is a different unit from the other stats.
- Character state (`adjCharState`, `:840-897`): under `MULTIPLYCH`, hit points, mana and movement are scaled by value/100 (`:850-863`). Hunger and thirst always add (`:853-858`).
- Owned, not only worn: because of `bubbleAffect` (§1), the adjuster works while the item is carried. The help text says "So long as the item with this property is owned" (`resources/help/arc_properties.ini:806`). The worn-only variant is `Prop_WearAdjuster`, which is outside this bucket.

**Set bonuses (ALLSET).** The parameter `ALLSET=name-N` sets a group name and a required count (`:340-354`). The default count is 2 (`ItemSetDef.allSetReqNum`, `:67`). `setCheck` (`:643-687`) counts the items in the holder's inventory that share the group name. It stores the answer in `setChecked` and `setActivated` on each member's definition, and reuses the answer until the cache is cleared. `clearSet` (`:689-722`) clears it when an item in the set is gotten, dropped or given (`executeMsg` branch at `:751-761`). `canApply` requires `setCheck` to pass (`:724-732`), so a single piece does nothing.

**Masks and the holder.** `canApply(MOB)` (`:724-732`) requires that the adjuster's host is an Item, that the item is not destroyed, that the zapper mask passes for the holder, and that the set check passes. `Prop_Adjuster` replaces this with a simpler check that only needs a mask pass and the host being set (`Prop_Adjuster.java:67-85`).

**Removal on death.** When `removeOnDeath` is set, a death message whose source is the host, the owner, or the host room or area removes the property and recovers the stats (`Prop_HaveAdjuster.java:763-778`). The default is discussed in Problems.

**Descriptions.** `accountForYourself` (`:970-974`) calls `fixAccoutingsWithMask` (`:917-968`). That function removes `ALLSET` from the text, rewrites `dis`/`sen` values as verb lists through `CMLib.flags().getDispositionVerbList` and `getSensesVerbList` (`:941-962`), flips the sign of the first +/- after `ARM` for display (`:921-940`), and appends the set requirement and the mask description (`:963-966`). The text is "Affects the owner: ...", and `Prop_Adjuster` uses "Effects: ..." (`Prop_Adjuster.java:93-97`).

**Power estimate (STAT-LEVEL).** `getStat("STAT-LEVEL")` (`:1007-1195`) sums weights:

- Hit points, mana and movement: value/5 (`:1049-1059`).
- Base stats: value x10. Max stats: value x90. Saves: value/20 (`:1073-1079`).
- Additive phys: `abi` x5, `arm` x-1, `att` x1, `dam` x3, `dis` +10, `lev` -value/3, `sen` +10, `spe` x100 (`:1160-1189`).
- Multiply phys uses other weights, and `arm` is `amt / -5` (`:1121-1151`, armor at `:1125`).

The additive and multiply branches disagree about the sign of armor (see Problems).

**Tuning (TONEDOWN and TONEUP).** `setStat` rewrites the numbers in `miscText` as text (`:1216-1534`). `TONEDOWN-ARMOR` (`:1233-1255`) and `TONEDOWN-WEAPON` (`:1257-1300`) scale `ARM`, `ATT` and `DAM` by `round(value * pct)`. If rounding leaves a value above 1 unchanged, it is reduced by one (`:1246-1248`, `:1271-1273`). `TONEDOWN-MISC` (`:1302-1370`) skips the `DIS` and `SEN` terms, skips the `ARM` and `ATT`/`DAM` terms that do not apply to the item type (`:1335-1342`), and scales only terms whose sign suits them: a negative `ARM`, and a positive value for any other term (`:1344-1348`). The TONEUP variants (`:1372-1531`) mirror these. The balancer calls them with 90%, 110% and 150% (`Libraries/TimsLibrary.java:516-553`, `:636-642`). It also calls the weapon routine with `TONEDOWN-WEAPON` for a tone-up (`Libraries/TimsLibrary.java:1288`). See Problems for defects.

**Prop_Adjuster.** The subclass (`Prop_Adjuster.java:35-98`) allows mobs, items, exits, rooms and areas (`:49-53`). It bubbles only for areas (`:56-58`), and it declares `TRIGGER_ALWAYS` (`:88-91`). It overrides `canApply` with a mask-only check and states "sets can't really work with this" (`:73-74`). The help lists its target as Mobs (`resources/help/arc_properties.ini:405-411`).

### 3. Vetoes and damage rules

**Prop_AbilityImmunity** (`Prop_AbilityImmunity.java:35-207`).

- Data: a list of names, a list of messages, and two flags, OWNER and WEARER (`:74-78`). Entries are separated by semicolons (`:97`). An entry may carry `=message` (`:110-117`).
- Matching (`okMessage`, `:123-155`): a message whose tool is an Ability, aimed at the host (or at the owner of an item with the OWNER flag, `:129-132`), is compared against each name. The comparison is `CMLib.english().containsString` on the ability's ID and name (`:137-138`). That is a case-insensitive substring search (`Libraries/EnglishParser.java:1591-1603`).
- Effect: the target is told "You are immune to @x1." (`:140-141`). If the source is not the target, the source sees the custom message or the default "<T-NAME> seem(s) immune to <O-NAME>." (`:142-148`). The message is then vetoed (`:150`).
- The WEARER flag is parsed (`:106-107`) but never read.
- Power estimate: each named ability adds `±(lowest qualifying level / 2)`, positive when the ability is malicious (`:162-179`).

**Prop_AbsorbDamage** (`Prop_AbsorbDamage.java:35-620`).

- Config: amounts and category words. A number applies to the words after it (`:103-116`, `:125-129`), and the default amount is 50% (`:115-116`). `+ALL` absorbs everything, and `-word` entries become exclusions (`:99`, `:136-137`). Without `+ALL`, a bare word means `+word` (`:132-135`). Words are matched against weapon damage types and classes, ability classes, domains, flags, IDs, materials and levels, with the matching tables built from `Weapon.TYPE_DESCS`, `Weapon.CLASS_DESCS`, `Ability.ACODE.DESCS_`, `Ability.DOMAIN.DESCS`, `Ability.FLAG_DESCS` and `RawMaterial.CODES` (`:141-219`). `MAGIC` covers spells, prayers, chants and songs (`:208-212`). `LEVELn` covers weapons at or below level n (`:213-219`).
- Precedence without `+ALL` (`:323-384`): the later assignment wins. The order is message type, then weapon matches, then the highest matching level threshold, then ability class, domain, ID, flags, and last the MAGIC setting for spell-type abilities. With `+ALL`, a matching exclusion returns early without absorbing (`:265-322`).
- Application (`:385-436`): damage or healing above zero is reduced by `round(value * pct)` when the absorb is a percent (`:389-390`), or by a flat integer (`:391-392`). The result is clamped at zero (`:393-394`). A negative value increases damage.
- ENHANCED (`:242-244`, `:397-434`): for spell casts and similar messages, a negative percent reduces the effect's TICKDOWN after 500 ms (`:410-427`). A positive percent rolls against `rollPercentage` (`:431`), and on success sets the message value to 1 (`:433`, comment "saved?"). What value 1 means for a cast message is unverified.
- Exemption: if the source or target has the ambiance tag `-<this>`, the absorb is skipped (`:261-263`).
- Descriptions: `getStat("TIDBITS...")` builds sentences with `makeStatMsg` (`:441-465`, `:467-550`).

**Prop_AddDamage** (`Prop_AddDamage.java:36-393`).

- Config: a percent and/or a flat integer, damage-type or attack-type words, and optional `;SMASK="..." TMASK="..."` source and target masks (`:101-196`). Words match by equality first, then by prefix (`:139-181`).
- `getDamage` = `round(value * pct) + bonus`, with a minimum of 0 (`:198-204`).
- Three paths in `executeMsg` (`:225-289`):
  - Armor or shield: when the holder is hit by hand or move attacks from range 0, with a chance of `d100 > 32 + attacker DEX` (`:237-239`), a verbal counter-spell is sent (`:241-250`). If it is not blocked, the attacker takes the damage (`:248-249`).
  - Weapon owned by the wielder who hits (`:254-277`). This path is guarded by `norecurse` (`:263-274`).
  - Mob with the property making weapon attacks (`:278-289`).
- STAT-LEVEL = `round(pct / 0.20) + 3 * bonus` (`:300-301`).

**Prop_Fumble** (`Prop_Fumble.java:37-322`).

- Config: `CHANCE` (default 100%, `:88`), `ITEM` (WORN by default, then HAVE/HAS/INV, HERE/ROOM, PRESENT, `:90-107`), `MASK`, `MSG` (default "<S-NAME> fail(s) <S-HIS-HER> @x1 on <T-NAME> due to @x2", `:69`, `:113`), `PRIVATE` (default true, `:114`), `WEAPON` (default false, `:115`), `AID` (ability IDs or names), `ATYPE` (default ALL, `:121`), `ADOMAIN`, `AFLAG`, and `CAST` (spells, with `TICKS=` and `NOUNINVOKE`, `:182-224`).
- Behaviour (`okMessage`, `:289-321`): when the source uses a matching ability and a percentile roll is within the chance, the use is vetoed, the failure message is shown, and any CAST spells are cast on the user (`addMeIfNeccessary`, `:227-253`). In WEAPON mode the same happens for the wielder's weapon attacks (`executeMsg`, `:267-286`).
- Cast on failure: a factory mob named after the item casts each spell at asLevel 0 (`:232-239`). `TICKS` shortens the spell's TICKDOWN (`:243-246`).

**Prop_FightSpellCast** (`Prop_FightSpellCast.java:35-328`). This class extends `Prop_SpellAdder` (b076), which holds the spell list. Its own logic is the trigger. On a damage message it finds the owner, and a wielded weapon casts at its target while the owner is in combat (`:149-162`). Armor casts on its wearer when that wearer is hit (`:164-167`). A siege weapon picks random targets in a boardable area (`:171-226`). The options are `ALLMOB`, `ALLITEM`, `ONLYMOB`, `ONLYITEM` and `NOOWN` (`:90-119`).

**Prop_CloseCommand** (`Prop_CloseCommand.java:35-192`).

- Config: semicolon-separated phrases. The first word is the command, the rest are arguments. A trailing `*` means a prefix match on that argument, and `**` means a prefix match that ends the phrase (`:57-73`, `:119-141`). `noclose` blocks closing (`:61-62`, `:95-104`). `message=...` replaces the default text (`:63-65`).
- Behaviour: the property intercepts `TYP_HUH`, the unrecognised-command message (`:106-112`). If a phrase matches, it closes the exit in that direction and locks it if lockable (`:150-172`, `MSG_CLOSE` at `:164`, `MSG_LOCK` at `:168`). For a container it sends the open and lock messages (`:175-184`), and then vetoes the HUH message (`:186`).

### 4. Time, place and movement gates

**Prop_ClosedDayNight** (`Prop_ClosedDayNight.java:37-586`).

- Config (`setMiscText`, `:99-180`): `DAY` inverts the night test; `SLEEP`, `SIT`, `LOCKUP`; `HOURS=open-close` (`:143-152`); `HOME=` (a room or name, `:154-155`); `SHOPMSG=` (`:157-158`); `WARNMSG<hour>=text`, with hours 0 to 99 (`:160-178`); `MASK=` (`:102-112`).
- Closed test (`checkClosed`, `:205-241`). Without HOURS the place is closed at area TimeOfDay NIGHT (`:217-222`). With HOURS and a normal range, it is closed outside [open, close] (`:225-229`). A range that wraps midnight is closed between close and open (`:230-234`). A mask limits whom the closure affects (`:236-239`).
- Entity behaviour (`tick`, `:327-526`). For a mob, when the test says closed: it stands up (`:339`), and it does nothing if it is fighting or not awake (`:340-341`). With LOCKUP at its start room, it closes and locks each door in every direction (`:343-375`). With HOME, it walks to the home room using Skill_Track (`:377-400`). With SLEEP or SIT it sleeps or sits on a matching furniture item in the room (`:402-454`). Then it sets `amClosed` to true (`:455`). Opening reverses these steps (`:477-511`).
- Shopkeepers (`okMessage`, `:243-272`): buy, bid, sell, list, value, deposit, withdraw, borrow and view are refused while closed (`shopCmds`, `:74-84`). The reply is `SHOPMSG`, or "Sorry, I'm off right now.  Try me tomorrow." (`:262`). A sleeping shopkeeper is described as asleep (`:256-257`).
- Visibility (`affectPhyStats`, `:528-585`). A closed mob or item is IS_NOT_SEEN and cannot see, move, speak or hear, unless it is fighting or has sleep, sit or home set (`:544-556`). A closed room gets IS_DARK (`:559-561`). A closed exit has its doors closed and locked once per closure, and restored once on reopening (`:563-583`).
- Warnings: on ENTER, the hour of the home clock picks a `WARNMSG` to say on the next tick (`:190-202`, `:514-524`).
- Timing: `tick` runs every 4 seconds (`core/CMProps.java:681`, `TIME_TICK = 4000`). The state of non-mob hosts is cached for one tick (`:538-541`).

**Prop_ClosedSeason** (`Prop_ClosedSeason.java:36-193`). The same idea with codes instead of hours. `closed` (`:105-145`) checks the area's weather (`W`), time-of-day (`C`) or season (`S`) against the codes in the config. The codes come from `Room.VariationCode`, which was not read. Effects match the day/night class: visibility for mobs and items (`:147-165`), IS_DARK for rooms (`:167-168`), and door state for exits (`:170-190`). The area used for an exit is the first one seen in a message, falling back to `CMLib.map().getFirstArea()` (`:94-103`, `:172`).

**Prop_Crawlspace** (`Prop_Crawlspace.java:35-140`).

- `HEIGHT` sets the limit (default 24, `:67-72`). A taller entity cannot enter, leave or flee through the room or exit unless it is sitting (`:89-96`). Entities over 120 get "You cannot fit in there." (`:107-111`). Others get "You must crawl that way." (`:112`). A flee attempt makes the mob sit first (`:97-105`).
- Standing in such a room is refused for tall entities with "You cannot fully stand up here." (`:132-133`). A sleeping mob wakes up crawling instead (`:120-130`).
- Adds the SENSE_ROOMCRUNCHEDIN sense (`:79`).

**Prop_Climbable** (`Prop_Climbable.java:36-178`).

- Rooms and exits. A mob that is not climbing, flying or falling, and that tries to move through, is stopped with "You need to climb that way, if you know how." (`:97-102`). Before stopping it looks for a ladder in the room and mounts one if found (`:92-96`). Recalls and transporting abilities are exempt (`:85-90`). The host gets IS_CLIMBING (`:72`).
- Thrown or dropped items fall through the room (`makeFall`, `:127-140`). Moving down an open drop tries a ladder first, and otherwise the mob falls (`:142-175`).
- Items are allowed by the mask (`:53`) but the logic only handles rooms and exits.

**Prop_EnlargeRoom** (`Prop_EnlargeRoom.java:35-134`). One operator and one number. `+n` or `-n` adds or subtracts an integer from weight and height, and `*f` or `/f` multiplies or divides by a decimal (`:71-133`). It attaches to rooms and areas (`:49-53`). Whether movement reads room height is not checked here.

**Prop_AreaForSale** (`Prop_AreaForSale.java:35-421`). A land title for an area.

- Text: `owner/[RENTAL ][ALLOWTHEFT ][TAX<n>X ]price` (`:103-107`, `:141-146`, `:163-168`).
- Price: 100000 by default, or when the value is not positive (`:77-78`, `:89-90`).
- Ownership: the owner must be a player or a clan (`:127-136`).
- Theft: `CMLib.law().robberyCheck` decides (`:265`).
- Rent: charged once per game day when the day number changes (`:407-418`), through `Prop_RoomForSale.doRentalProperty`, which was not read. If that call returns true, the owner is cleared (`:412-415`).
- Saving: when the area saves, start-room mobs that do not rejuvenate are written to the database (`:271-303`).

### 5. Creature and item behaviour

**Prop_Familiar** (`Prop_Familiar.java:35-525`).

- Config: an animal index or name from the enum at `:66-88` (`setMiscText`, `:404-431`).
- Linking (`tick`, `:162-181`): an unlinked mob that is following another mob that is in the game becomes linked. A copy of the property is placed on the master (`:172-178`), so both mobs carry the effect and the master's copy points back to the familiar.
- Both mobs get their bonus only while they share a room (`:197-199`, `:436-438`).
- Breaking the link (`removeMeFromFamiliarTo`, `:111-143`) removes both effects, and then destroys the familiar mob (`:137-140`). It runs when either mob dies (`:343-345`), when the familiar stops following the master (`:182-187`), or when the host is not a mob (`:150-151`).
- Speech: a familiar's racial language is copied for the master (`getMyAnimalSpeak`, `:244-264`). Speech is translated in both directions (`okMessage` `:315-336`, `executeMsg` `:346-400`).
- RABBIT pacifies attackers: a malicious act on either mob by an unengaged attacker is refused with "You are too much in awe of <target>" (`:294-313`).

The animal table:

| Animal | Stat or sense effect | Special | Lines |
|---|---|---|---|
| DOG | STR +1 | sees hidden (CAN_SEE_HIDDEN) | `:203-204`, `:441-443` |
| TURTLE | STR +1 | breathes salt and fresh water (breathables set) | `:444-459` |
| CAT | DEX +1 | paralysis save +100 if below 500 | `:461-465` |
| BAT | DEX +1 | removes CAN_NOT_SEE from mobs (cures blindness) | `:210-213`, `:466-468` |
| RAT | CON +1 | disease save +100 if below 500 | `:469-473` |
| SNAKE | CON +1 | poison save +100 if below 500 | `:474-478` |
| OWL | WIS +1 | infrared sight (CAN_SEE_INFRARED) | `:218-220`, `:479-481` |
| RABBIT | CHA +1 | pacifies attackers | `:482-484`, `:294-313` |
| RAVEN | INT +1 | undead save +100 if below 500 | `:485-489` |
| PARROT | CHA +1 | on-shoulder ambiance, not flying | `:229-235`, `:498-500` |
| SEATURTLE | CON +1 | breathes water | `:501-515` |
| BOA_CONSTRICTOR | STR +1 | none | `:490-492` |
| IGUANA | INT +1 | undead save +100 if below 500 | `:493-497` |
| SPIDERMONKEY | DEX +1 | paralysis save +100 if below 500 | `:516-520` |

**Prop_AstralSpirit** (`Prop_AstralSpirit.java:35-205`).

- Applies to mobs only (`:57-61`). `autoInvocation` adds it as a non-uninvokable effect (`:78-87`).
- Race is replaced by "Spirit" (`:71-76`, `:173-178`). Weight becomes 0 and height -1. The name becomes "The spirit of <name>" (`:191-193`). The mob flies, is invisible and not seen, can see in the dark, and cannot speak. The cannot-move bit is cleared (`:191-203`).
- Blocks in `okMessage` (`:108-168`): attacks ("You are unable to attack in this incorporeal form.", `:143-148`); hand and mouth actions (`:150-158`); being targeted by others ("<name> doesn't seem to be here.", `:161-166`); sitting on a corpse unless the text contains SELF-RES ("You lack that power", `:129-137`). A dispossess ends Chant_AstralProjection (`:117-127`).
- Used by `Abilities/Druid/Chant_AstralProjection.java:202`, `Libraries/MUDFight.java:3543` and `Libraries/CoffeeUtensils.java:1567-1570`.

**Prop_Doppleganger** (`Prop_Doppleganger.java:35-395`). Level scaling to the people present.

- Config (`:98-118`): a bare integer is a level add, and a bare percent is a level scale. Otherwise: `MIN`, `MAX`, `LEVELCODE` (-1 lowest, 0 average, 1 highest of the group, `:217-241`), `LEVELADD`, `LEVELPCT` (default 100, `:109`), `DIFFADD`, `DIFFPCT` (default 100, `:113`), `SAVEPCT` (default 0, `:111`), `DIFFGRP`, `PLAYERSONLY`, `PLAYERSNFOLS` and `ASMATERIAL`.
- Mobs (`doppleGangMob`, `:189-296`). The candidate set is the victim, the entrant, and the room's other inhabitants that are fighting the scaled mob or have no victim. Each candidate must pass `canMimic` (`:195-216`, `:298-327`). The aggregate level depends on `LEVELCODE` (`:217-241`). The final level is `aggregate * LEVELPCT + LEVELADD`, clamped to MIN..MAX (`:262-266`). `DIFFGRP` scales the difficulty terms by group size (`:244-258`). If the level or difficulty changed, the mob's base armor, attack, damage and speed are rebuilt from CMLib.leveler (`:272-277`, speed is `1.0 + level/100 * 4.0`). HP, mana and movement are set (`:278-280`), saves are added (`:281-286`), and the mob is healed to full (`:292`).
- Items (`doppleGangItem`, `:122-166`). The item level is the owner's level times the percent plus the add. The item is then balanced to that difficulty with `CMLib.itemBuilder().balanceItemByLevel` (`:143`), and the displayed level is restored (`:144-153`).
- Triggers (`okMessage`, `:329-394`): an ENTER or LIFE message in the room, a GET by a non-owner (`:332-336`), and an owner level change (`executeMsg`, `:171-174`).
- `canMimic` excludes players who have CMDMOBS or CMDROOMS security (`:322-325`), and excludes monsters whose start room is in the same area as the property's own room or area (`:303-312`).

**Prop_ClanEquipment** (`Prop_ClanEquipment.java:42-462`).

- Config: four tokens (`:126-134`): element type, power level (an integer), clan name and clan type. Fewer than four tokens leave the defaults in place.
- Activation: the item works only while the holder has a role in the clan (`:362-367`). Each `executeMsg` checks the clan once an hour, and removes the property if the clan no longer exists (`:344-351`).
- Weapon: a hit deals an extra elemental roll `roll(1, 6P, P)`, capped at the owner's level (`:385-387`, roll semantics in `Libraries/Dice.java:398-406`). The message is "The magic of <clan> <DAMAGE> <T-NAME>!" (`:388`).
- Shield thorns: a melee attacker whose roll is above `32 + DEX` is hit by `roll(1, 3P, P)`, capped at half the owner's level (`:398-411`).
- Wand: speaking the secret word (chosen from a list by hash of the config text, `:246-254`, `:135`) fires the wand if the holder has Skill_WandUse. The mana cost is `maxMana/10 - 5*level`, with a minimum of 5 (`:228-244`). The damage is `roll(1, 6P, P)`, capped at the holder's level (`:300-302`).
- Armor: the element's save bonus `P * 5` is added to the wearer's saves for non-shield armor while worn (`:321-338`, `:144-208`).

Elemental table (from `setMiscText`, `:140-225`):

| Type | Attack effect | Weapon type | Save bonus |
|---|---|---|---|
| PARALYSIS | TYP_PARALYZE | STRIKING | SAVE_PARALYSIS |
| FIRE | TYP_FIRE | BURNING | SAVE_FIRE |
| COLD | TYP_COLD | FROSTING | SAVE_COLD |
| WATER | TYP_WATER | SLASHING | SAVE_WATER |
| GAS | TYP_GAS | GASSING | SAVE_GAS |
| MIND | TYP_MIND | STRIKING | SAVE_MIND |
| ACID | TYP_ACID | MELTING | SAVE_ACID |
| ELECTRIC | TYP_ELECTRIC | BURNING | SAVE_ELECTRIC |
| POISON | TYP_POISON | STRIKING | SAVE_POISON |
| DISEASE | TYP_DISEASE | STRIKING | SAVE_DISEASE |
| HEALTH | 1001 (no weapon effect) | BURSTING | none |
| MAGIC | 1002 (no weapon effect) | BURSTING | none |
| SPEED | 1003 (no effect in this class) | none | none |

**Prop_CombatAdjuster** (`Prop_CombatAdjuster.java:35-206`). It multiplies attack, damage, armor, HP, mana, move and speed by `1 + pct/100` (`alladj`, `:56`, parsed at `:131-137`). It also supports absolute overrides (`allset`, `:58`, `:138-144`). The only known caller is the CombatAbilities behaviour (`Behaviors/CombatAbilities.java:257-260`), which builds text such as "ATTACK+5 ARMOR+3". Armor is `100 - round((100 - armor) * mult)` (`:83`), so a positive ARMOR is better protection. That is the opposite of the additive `arm` in Prop_HaveAdjuster (see Problems). Its STAT-LEVEL weights are attack /5, damage /20, armor /-5, and HP, mana, move and speed /20 (`:168-188`).

**Prop_EnterAdjuster** (`Prop_EnterAdjuster.java:39-347`). A room, an exit, or a rideable on SIT applies changes to the entering mob (`okMessage`, `:258-346`).

- Config (`setMiscText`, `:113-199`): semicolon-separated spell names, then a final segment of stat terms. The spells are cast on every entry (`:267-274`). The stat terms use the same keys as Prop_HaveAdjuster, plus `clev`, `prac`, `trai`, `ques`, `coin` and `expe` (`:153-198`).
- Effects: base stats, current state, class level, race (with aging, `:296-307`), practices, trains, quest points, money and experience (`:275-339`).
- Each entry repeats the change, and the mask gates it (`:264`). Recovery and wearability checks run at the end (`:340-343`).

**Prop_CommonTwister** (`Prop_CommonTwister.java:35-245`). Entries have the form `SKILL=<id> MASK=<name fragment> NAME=... DISPLAY=... MATERIAL=... SUBTYPE=...` (`:64-86`). When a gathering skill generates an item, or makes gathering noise, or when Speculate names a material, one matching entry is chosen at random (`:119`). Its name, text, material and subtype are then rewritten (`:88-178`, applied at `:214-240`). An ambiance tag "-Prop_CommonTwister" on the item prevents a second application (`:215`, `:239`). With no text, a random raw material is generated (`:92-100`).

**Prop_AffectReTick** (`Prop_AffectReTick.java:37-457`). It rescales the remaining TICKDOWN of named effects.

- Config (`setMiscText`, `:397-456`): `SCAN=` a list of OWNER, MOB, ITEM, ROOM, EXIT or AREA; `SCANMASK=`; and `AbilityName=formula`, where the formula must contain `@x1` for the current tick count (`:441`).
- Trigger: when an ability named in the rules is used, a runnable 800 ms later re-ticks the matching effect on the source and on the target (`executeMsg`, `:267-300`).
- `retick` (`:60-91`): it computes the new TICKDOWN. If the value is lower, it runs the effect's tick that many times now (`:86-87`), and then stores the value (`:89`).
- `untick` restores the saved values, per effect (`:93-104`) and per holder (`:106-134`), on DROP and REMOVE (`:327-331`, `:338-341`). The saved values expire after 30 minutes (`:54`), and scanned entities are forgotten after 5 minutes (`:55`).
- Scans on ENTER into a room (`:302-310`), and on initialisation (`:348-393`).

**Prop_Artifact** (`Prop_Artifact.java:39-616`). Unique items that survive shutdown and room reset.

- Storage: `miscText` is `<flags>;<itemID>` (`:106-131`). The ID is built if missing from the class ID, a hash and `Math.random()` (`:70-90`, `:122-131`). Flags: AUTODROP (true), NOCAST (true), NOLOCATE (true), NOMOBS (true), AUTORESET (false), NODB (false) (`:133-138`).
- Save: on SHUTDOWN or ROOMRESET (`executeMsg`, `:308-383`), the owner's start room, class, rejuvenation, uses, level, ability and text are written as an XML block (`:352-382`). The item is then destroyed. The save goes to the ARTIFACTS area data through `DBReCreateAreaData` (`:379-382`).
- Reload (`tick`, `:434-598`): after a wait (60 seconds, or 5 seconds for a BOOT entry, `:113`, `:292`, `:339`), the item is recreated in its room or in the start-room mob that holds it. Matching items in the target are removed (`:534-575`). The item is registered in the static `registeredArtifacts` map (`:59`, `:487-488`).
- Restrictions: a monster cannot take it (`:249-254`); magic is refused (`:255-274`, fizzle message at `:272`); it cannot expire in a room (`:239-242`); quitting drops it if AUTODROP (`:283-298`). `affectPhyStats` makes it unlocatable and undestroyable, and it never expires (`:601-615`).
- Admin path: a say containing "reset prop_artifact" by a CMDITEMS user clears its registry entries (`:213-234`).

## Content worth mining

- **The animal companion table** (above, `Prop_Familiar.java:66-88` and `:203-520`). Fourteen animals, each with one stat and one save or sense. This is a ready-made set of pet archetypes.
- **Elemental gear table** (above, `Prop_ClanEquipment.java:140-225`). Thirteen element types, each mapped to an attack effect, a weapon damage class and a save. Adaptable as "enchanted gear by element".
- **Adjuster vocabulary** (`Prop_HaveAdjuster.java:309-314`, `:416-434`). The list of stat keys is a usable starting vocabulary for a SUD character sheet: strength, dexterity, constitution, charisma, wisdom, intelligence; max-trainable forms; saves for paralysis, fire, cold, water, gas, mind, acid, electric, poison, disease; plus armor, attack, damage, speed, level, weight, height, hit points, hunger, thirst, mana, movement, and proficiency and cost tables for abilities.
- **Short messages** (paraphrased, all short): "You need to climb that way, if you know how." (`Prop_Climbable.java:100`); "You must crawl that way." and "You cannot fit in there." (`Prop_Crawlspace.java:112`, `:109`); "You cannot fully stand up here." (`:132`); "You are immune to @x1." (`Prop_AbilityImmunity.java:141`); "Sorry, I'm off right now. Try me tomorrow." (`Prop_ClosedDayNight.java:262`); "You are too much in awe of @x1" (`Prop_Familiar.java:307`); "You are unable to attack in this incorporeal form." (`Prop_AstralSpirit.java:145`); "Magic energy fizzles around @x1 and is absorbed into the air." (`Prop_Artifact.java:272`).
- **Opening-hours knobs** (`Prop_ClosedDayNight.java:131-178`). DAY, SLEEP, SIT, LOCKUP, HOURS=open-close, HOME, SHOPMSG and hour-keyed WARNMSG lines. This is a small, declarative schedule language for NPCs and shops.
- **Absorb categories** (`Prop_AbsorbDamage.java:141-219`). Weapon damage types, weapon classes, materials, ability classes, domains, flags and level thresholds, all resolved from the game's own tables, which makes it easy to add new categories without changing the rule code.
- **Ability and item phrases for failure** (`Prop_Fumble.java:69`): the default "<S-NAME> fail(s) <S-HIS-HER> @x1 on <T-NAME> due to @x2" is a template a SUD could reuse for item-caused failures.

## Notable design ideas

1. **One rule object, many hosts.** The same class can attach to a mob, item, room, exit or area, and the type mask decides where it is valid (`Property.java:146-164`). A rule like "closed at night" works on a shopkeeper, a shop room or its door alike.
2. **Carried items as stat sources.** `bubbleAffect` forwards an item's effects to whoever carries it (`Items/Basic/StdItem.java:665-669`, `MOBS/StdMOB.java:826`). The mask is then checked against the holder (`Prop_HaveAdjuster.java:724-732`), so an item can say "+2 STR for thieves" without being equipped.
3. **Set bonuses from inventory.** Group size is counted from what the holder carries, cached, and cleared on get, drop and give (`Prop_HaveAdjuster.java:643-761`). A pure function of inventory with explicit invalidation.
4. **Gates that move the world.** The closure rules make an NPC go home, lock doors, sleep, or sit at furniture (`Prop_ClosedDayNight.java:343-454`). The gate is a schedule that acts, not just a flag.
5. **Scaling to the present.** `Prop_Doppleganger` recomputes NPC and item levels from whoever enters the room, with MIN, MAX, average, highest or lowest modes (`Prop_Doppleganger.java:217-292`). It is adaptive difficulty without a global level.
6. **Artifacts that persist through reset.** Unique items are saved on shutdown and room reset, and respawn at their last holder or room, with dedupe (`Prop_Artifact.java:308-598`).
7. **Time compression for effects.** `Prop_AffectReTick` runs an effect's tick several times at once when a rule applies, and restores the original duration later (`Prop_AffectReTick.java:60-104`). This changes durations for everything in a scope without rewriting the effects.
8. **Mirrored effects on a companion.** The familiar link places a copy of the property on the master, so both sides get the same bonus and both break on separation (`Prop_Familiar.java:172-187`).
9. **Random but configured twists.** `Prop_CommonTwister` picks at random among matching config entries, so a gathering skill can yield different materials in different places (`Prop_CommonTwister.java:119`).
10. **Clan items that depend on the holder.** Item power is active only when the holder has a clan role, and the property removes itself when the clan disappears (`Prop_ClanEquipment.java:344-367`).

## Problems and cruft

Each item cites the code path. "Unverified" means the statement follows from reading, but I did not run it.

**Prop_HaveAdjuster**

- `Prop_HaveAdjuster.java:339`: `"TRUE".startsWith(getParmStr(..., "RONDEATH", "").toUpperCase())`. When RONDEATH is absent the default string is "", and `"TRUE".startsWith("")` is true, so removeOnDeath defaults to **true**. The help says the property disappears on death "when set to true" (`resources/help/arc_properties.ini:437`), which implies a default of false. As written, every item adjuster is stripped when its owner dies unless RONDEATH is set explicitly to false. Likely unintended; unverified in play.
- `Prop_HaveAdjuster.java:1235-1239`: the TONEDOWN-ARMOR gate tests `getParmPlus(text,"ARM") > 0` (a positive armor term), but the search that follows looks for a `-`. Since bonus armor is written as negative, the gate is false for the usual case and nothing is toned down. A positive armor term also searches for a `-` that may belong to another term.
- `Prop_HaveAdjuster.java:1409`: in TONEUP-WEAPON, `doesAtt` tests `"DAM"` instead of `"ATT"`. Attack is never scaled unless damage is also present. Otherwise a random "ATTACK+5" or "DAMAGE+1" is prepended (`:1410-1416`).
- `Prop_HaveAdjuster.java:1502` and `:1514`: TONEUP-MISC treats positive values as `num + round(num * pct)`, about 2.1 times for 110%, and negative values as `num + |round(num * (pct - 1))|`. TONEDOWN-MISC (`:1353`) uses `round(num * pct)`. The positive branch looks wrong; the three formulas disagree.
- `Prop_HaveAdjuster.java:1392-1393`: for negative armor, `Math.round(-5.5)` gives -5, and the guard `newNum > 1` only applies to positive values. Small negative armor cannot be toned up.
- Armor sign conventions disagree. The additive branch treats armor as an armor value (`:1164`, `level += value * -1`). The multiply branch uses `amt / -5` (`:1125`). CombatAdjuster treats a positive ARMOR as better protection (`Prop_CombatAdjuster.java:83`, `:175`). A builder cannot write one sign for "better armor" across these.
- `Prop_HaveAdjuster.java:565` and `:568`: MULTIPLYPH also scales weight and height. The help (`resources/help/arc_properties.ini:826`) lists only attack, armor and damage.
- `Prop_HaveAdjuster.java:571` against `:1149`: in multiply mode `spe` is a factor, while other keys are percentages. The help does not say this.
- `Prop_HaveAdjuster.java:1188`: in additive mode, `spe` uses `intValue() * 100` on a double, so a fractional speed of 0.5 contributes 0.
- `Prop_HaveAdjuster.java:755`: the ALLSET clear only runs for the base ID. Subclasses have other IDs and skip it. `Prop_WearAdjuster` overrides `executeMsg` (`Prop_WearAdjuster.java:124`), so whether its set state is cleared correctly is unverified here.
- `Prop_HaveAdjuster.java:922`: the sign-flip loop uses `x > 0`, so an `ARM` term at position 0 is never flipped in the description. The same guard appears in `Prop_EnterAdjuster.java:207`.
- `Prop_HaveAdjuster.java:78` and `:892`: `firstTime` is never set to true anywhere in the Properties folder, so the copy into `mob.curState()` is dead code.
- Parameter `ABLE*` tables are documented for Prop_Adjuster but not for this class's help block (`resources/help/arc_properties.ini:405-422` vs `:798-840`). Minor.

**Prop_AbilityImmunity**

- `Prop_AbilityImmunity.java:112-116` and `:144`: the `messages` list gets one entry per non-flag name, but `diseases` keeps OWNER and WEARER as entries too. So with `OWNER;Disease_Cold=msg`, the index for Disease_Cold is 1, and the message list has one item, so the custom message is lost and the default is used.
- `Prop_AbilityImmunity.java:131-132`: the second clause is a subset of the first. The `wearer` field is parsed and never used (`:77`, `:107`).

**Prop_AbsorbDamage**

- `Prop_AbsorbDamage.java:573-576`: each null field adds 10 to STAT-LEVEL, because `null instanceof Map` is false. An unconfigured property reports 110 (11 fields, all null).
- `Prop_AbsorbDamage.java:502`: `getStat("TIDBITS=ENHANCED ...")` sets `enhFlag` permanently. A getter has a side effect.
- `Prop_AbsorbDamage.java:433`: a successful ENHANCED roll sets the message value to 1 ("saved?"), and what that means is unverified.
- `Prop_AbsorbDamage.java:230`: `newMiscText = newMiscText.toUpperCase()` does nothing.

**Prop_Artifact**

- `Prop_Artifact.java:213-237`: any speech targeted at the item calls `destroyArtifact` (`:235`) and returns true. The CMDITEMS check guards only the registry cleanup (`:219-234`). A player can destroy an artifact by speaking at it.
- `Prop_Artifact.java:157`: the Destroy command is detected by searching a stack-trace string for `Commands.Destroy.execute`. Fragile.
- `Prop_Artifact.java:197`: `identifyStr` destroys the artifact in its `finally` block. A description helper with a side effect.
- `Prop_Artifact.java:83` and `:128-130`: IDs include `Math.random()`. They are stable once saved in `miscText`, but a new ID cannot be derived from the item.
- `Prop_Artifact.java:611-612`: `affectPhyStats` resets the item's uses to 100 on every stat recomputation.

**Prop_ClanEquipment**

- `Prop_ClanEquipment.java:210-225`: HEALTH, MAGIC and SPEED set `typeOfEffect` to 1001 to 1003. Every weapon, shield and wand path is gated by `typeOfEffect < 1000` (`:381`, `:396`, `:102-115`), so these types do nothing in this class. Unverified whether another class reads them.
- `Prop_ClanEquipment.java:80`, `:377` and `:83`, `:400`: `notAgain` and `lastMessage` are never assigned, so their guards are dead.
- `Prop_ClanEquipment.java:132`: `Integer.valueOf(V.elementAt(1))` is unguarded, so a non-numeric power level throws.
- `Prop_ClanEquipment.java:104`, `:113`, `:117`: the descriptions give ranges of `1*P` to `6*P` (and `1*P` to `3*P` for shields). The roll `roll(1, 6P, P)` returns P+1 to 7P (`Libraries/Dice.java:398-406`), so the text is off by the modifier.
- `Prop_ClanEquipment.java:164`: WATER maps to a SLASHING weapon type. Possibly intentional; unverified.

**Prop_ClosedDayNight and Prop_ClosedSeason**

- `Prop_ClosedDayNight.java:194` against `:219-234`: warnings use `homeClock(affected)`, while closure uses the area clock. Two clocks can disagree.
- The two classes duplicate the visibility and exit-door logic (`Prop_ClosedDayNight.java:528-583`, `Prop_ClosedSeason.java:147-190`), each with its own `doneToday` flag. Both change exit doors inside a stat calculation (`:567-581`, `:174-188`), which is a side effect in a getter.
- `Prop_ClosedSeason.java:172`: before any message has been seen, the exit's area falls back to `CMLib.map().getFirstArea()`, which may be the wrong area.

**Prop_AreaForSale**

- `Prop_AreaForSale.java:228-229`: `((Area)affected).Name();` has no `return`. For an area title, `landPropertyID` returns "", and `getUniqueLotID` (`:97`) becomes "AREA_PROPERTY_" for every area.
- `Prop_AreaForSale.java:203`: `setAllowTheft` omits the trailing space that the other setters use. It works only because `getPrice` scans digits from the end.

**Prop_AddDamage**

- `Prop_AddDamage.java:341-342` and `:372-373`: the percent is applied only if `bonusDamage != 0` (checked after the update), so a pure percentage adder cannot be toned down or up. The guard should test `pctDamage`.
- `Prop_AddDamage.java:340` against `Prop_HaveAdjuster.java:1246`: TONEDOWN here computes `bonus -= round(bonus * pct)`, which keeps `1 - pct` of the value. HaveAdjuster keeps `pct`. The balancer's "90%" therefore means opposite things to the two classes.

**Prop_Doppleganger**

- `Prop_Doppleganger.java:281-286`: save bonuses are added to the base char stats on each rescale, without resetting first, so they accumulate. Unverified in play.
- `Prop_Doppleganger.java:292`: `resetToMaxState()` runs on every rescale, which heals the mob each time a player enters.
- `Prop_Doppleganger.java:144-153`: the first `setLevel` on the item is overwritten by the second block. Redundant.
- `Prop_Doppleganger.java:217`: the minimum-level sentinel is the global `LASTPLAYERLEVEL`, so results depend on global state.

**Prop_Familiar**

- `Prop_Familiar.java:137-140`: unlinking destroys the familiar mob. Stopping a follow, a master's death or the familiar's death all destroy it. The commented-out XP penalty (`:119-123`) suggests the design was once different.
- `Prop_Familiar.java:277-284`: `effectA` is declared `final` and is never refreshed after `autoInvocation`, so the first speech call returns null and translation begins on the second call.
- `Prop_Familiar.java:409`: `Familiar.values()[CMath.s_int(newText)]` has no bounds check.

**Prop_FightSpellCast**

- `Prop_FightSpellCast.java:324-326`: the `else super.setStat(code, val)` is attached to `if (code != null)`. For every non-null code the base `setStat` is never called, so CLASS, TEXT and NAME are dropped. The TONEDOWN and TONEUP bodies (`:285-322`) are commented out.
- `Prop_FightSpellCast.java:252`: STAT-LEVEL is always negative for cast spells (`mul = -1`), and the malicious branch that would make it positive is commented out (`:253-254`). Compare Prop_AbilityImmunity, which does use the sign.

**Prop_Fumble**

- `Prop_Fumble.java:305`: the domain test checks `ableTypes` instead of `domains`, so ADOMAIN never matches.
- `Prop_Fumble.java:148` and `:168`: the domain and flag guards test `typStr` (ATYPE), not their own strings. Since ATYPE defaults to "ALL", they are almost always true.
- `Prop_Fumble.java:199-201`: a per-spell chance written as "50% Spell_X" is parsed and then discarded.

**Prop_CloseCommand**

- `Prop_CloseCommand.java:178`: the container branch sends `MSG_OPEN` for a close command. It opens the container and then says "closes".
- `Prop_CloseCommand.java:122`: `cmds.get(i)` has no size check. A short command such as "close" for a longer phrase may throw. Unverified whether the caller traps the exception.

**Prop_CommonTwister**

- `Prop_CommonTwister.java:140` and `:143`: a DISPLAY value is written to the key `DISPLAY`, but the caller reads `DISPLAYTEXT` (`:232`). Display-text changes are dropped.

**Prop_EnterAdjuster**

- `Prop_EnterAdjuster.java:122-150` and `:137`: the parser consumes `parameters[0]`, so the stat terms must be the last `;` segment. Stat terms placed before spells are lost.
- `Prop_EnterAdjuster.java:258-346`: permanent base-stat changes happen inside `okMessage`. They are applied even if a later listener vetoes the move, and every entry repeats them.

**Prop_AstralSpirit**

- `Prop_AstralSpirit.java:187-190`: the comment says the form keeps the mob asleep, but `:199` removes IS_SLEEPING. The comment is stale.

**Other**

- `Prop_Climbable.java:53` against `:78`: items are allowed by the mask, but the logic only handles rooms and exits.
- Many non-adjusters set `FLAG_ADJUSTER` (see §1), and `TimsLibrary` uses the flag to pick a tuning target (`Libraries/TimsLibrary.java:1344-1362`). The flag is not precise.
- `Tests/PropHaveadjuster.java` (117 lines) exists but contains no ARM, ATT or DAM checks (grep only), so the defects above would not be caught by it.

## Takeaways for a Python SUD

- **Adopt the effect protocol, not the strings.** Give each effect a small class with four hooks: `stat_mods(entity, stats)`, `on_event(entity, event) -> bool` for vetoes, `tick(entity)`, and `describe()`. Attach effects to any world object. Keep stat computation pure: compute effective stats from base values plus effects, and never change the world inside it. Several classes here break that rule (`Prop_ClosedDayNight.java:553-583`, `Prop_Artifact.java:611-612`).
- **Use typed data for rules.** Replace the miscText grammar with dataclasses or TOML, for example `Adjuster(stats={"str": 2, "armor": -5}, percent={"damage": 10}, mask=..., set=("ring-of-kings", 2), on_death="remove")`. Keep the mask as a predicate over race, class, level and deity. Parsing `+`/`-` from strings was the source of most of the sign confusion.
- **Pick one unit convention.** Store armor as protection (higher is better), write it once, and never flip signs in display code. Keep an explicit multiplier field instead of a MULTIPLY flag that changes unit meaning per stat.
- **Carry effects on items.** An item's effects apply to its holder while carried, and the holder is checked against the item's mask. For a single-player game this is a simple loop over `player.inventory`, with no bubbling machinery needed.
- **Set bonuses without caches.** Count the items in the player's inventory each time the stats are computed. With one player, this is cheap and avoids the invalidation bugs of `clearSet`.
- **Schedules that act.** Model a shop as a schedule with states (open, closing, closed) and a clear per-state behaviour: the keeper goes home, locks the door, sleeps. Apply door changes in `tick`, not in stat computation. The hour-keyed warning idea (`WARNMSG`) is a cheap way to add flavour.
- **Absorb and resist as ordered rules.** Use a list of rules sorted from most specific (ability ID) to least (all damage), with first match wins and a single documented precedence. The current "last assignment wins" pattern hides which rule applied.
- **Immunities by exact ID.** Match on exact ability or effect IDs, with an explicit owner or wielder flag. Substring matching (`EnglishParser.java:1591`) is convenient but can catch unintended abilities.
- **Persist unique items deliberately.** Keep a registry of unique items keyed by stable IDs. Save holder, location and uses on shutdown, respawn them at the last place, and never destroy them on speech. Drop the stack-trace detection; deletion should be an explicit action.
- **Adaptive difficulty as a function.** For a single player, define `scaled_level = clamp(player.level * pct + add, lo, hi)`, computed when the player enters the area or when the NPC is created. Apply it to fresh stats each time rather than adding to base values, which avoids the accumulation in `Prop_Doppleganger.java:281-286`, and do not heal on every rescale.
- **Companions without destruction.** Model a companion with a stat-bonus table (the animal table is ready to use). When the companion stops following, let it wander off rather than destroy it.
- **Multiplayer assumptions to remove.** Doppleganger's group modes and PLAYERSONLY assume several players. Familiar and AstralSpirit depend on another player (`soulMate`, the follow relation). ClanEquipment assumes a clan, and AreaForSale assumes player-owned land with daily rent. For SUD, reduce these to: the player, NPC allies, and faction standing.
- **Drop or redesign.** Drop the ambiance tag mechanism, the bit-mask verb tables, the stack-trace check and the `Math.random()` IDs. Keep the declarative command phrase matcher (`Prop_CloseCommand.java:115-143`) as a model for mapping typed commands to actions, and keep the Fumble-style chance-and-cast failure rule as a template for item-caused failures.

## Coverage

- **Read in full (code lines; license headers and import blocks skipped):** all 23 bucket files. `Prop_HaveAdjuster.java` was read in four chunks (1-1535). `Property.java` (173 lines) was read in full as the family base class.
- **Read selectively:** `Abilities/ThinAbility.java`. Lines 38-160, 209-216, 338-345, 379-384, 421-442, 516-560 and 580-665 were read, and method signatures were listed by grep.
- **Consulted outside the bucket (short reads or grep only):** `Abilities/interfaces/Ability.java` (constants); `core/CMParms.java:2755-2800` (`getParmPlus`); `core/CMath.java:1083-1090` (`s_pct`); `Libraries/Dice.java:398-406` (`roll`); `Items/Basic/StdItem.java:640-730` (bubble forwarding); `MOBS/StdMOB.java:800-830` and `:826-827` (stat recovery); `core/interfaces/EachApplicable.java:36-47`; `Items/Armor/StdArmor.java:630-655` (armor subtraction); `Libraries/TimsLibrary.java` (lines 209, 495-555, 625-650, 1239-1362, and 1288 by grep); `Behaviors/CombatAbilities.java:250-275`; `Libraries/EnglishParser.java:1591-1603`; `Abilities/Druid/Chant_AstralProjection.java:202`, `Libraries/MUDFight.java:3543`, `Libraries/CoffeeUtensils.java:1567-1570` (uses of AstralSpirit); `Abilities/Thief/Thief_PetSpy.java:155`, `Thief_PetSteal.java:224`, `Thief_PirateFamiliar.java:327` (uses of Familiar); `resources/help/arc_properties.ini` lines 405-440, 798-840 and the help strings (`:437`, `:826`); `guides/refs/affbehav.html` line 2940 (grep); `Tests/PropHaveadjuster.java` (grep; no coverage of ARM, ATT or DAM).
- **Not read (other buckets or outside scope):** `Prop_SpellAdder` (b076), `Prop_RoomForSale` (rent and robbery callers), `Prop_WearAdjuster` (except its `executeMsg` line), `Room.VariationCode` (the codes for Prop_ClosedSeason), MUDZapper (mask semantics), `CMLib.leveler()`, `ItemBuilder.balanceItemByLevel`, `CMLib.law().robberyCheck`.
- **Unverified at runtime:** all defect statements are from reading the code. None was executed.
