# Abilities/Properties, part 3: entry gates, lots, magic blocks and the property setter

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b077` (Abilities/Properties [part 3/4: Prop_NewDeathMsg .. Prop_RoomPlusForSale]) · 47 files · 8621 lines of Java.

## Summary

A CoffeeMud "property" is an ability that never casts. It sits in the effect list of a room, exit, area, item or mob and changes behaviour by vetoing or rewriting messages, or by changing stats. The 47 classes here fall into a few families: eleven entry gates, counting the capacity limit (level, class, race, alignment, tattoo, stat, height, player-kill status, no-monsters and so on), most of which share one mask-style grammar and check the whole traveling party; a family of magic blocks (teleport, summon and charm) keyed on ability flags rather than on individual spells, plus spell and skill restrictions by ID; real-estate, rent and item-decay logic that charges a monthly lot fee from the owner's bank; and `Prop_PropSetter`, a data-driven way to change any editable field when a trigger fires and to undo the change when the opposite trigger fires. For a single-player text game, the gate pattern, the flag-keyed magic blocks and the reversible setter are the most borrowable parts. Several rules are inconsistent or broken as written, and these are listed under Problems and cruft.

## Key files and types

Paths are under `com/planet_ink/coffee_mud/Abilities/Properties/` unless shown in full. Every class here extends `Property` (`Property.java:38`) unless noted. The mask-library and interface files cited below are full paths.

| File or class | Role | Lines |
|---|---|---|
| `Property.java` (base) | `Property extends ThinAbility`; `canAffectCode()` and `canAffect(Physical)` restrict targets; classification `ACODE_PROPERTY` | 173 |
| `Prop_RoomForSale.java` | Lot for sale or rent: owner, price, tax and rental flags in one text string; title, lot upkeep, rent billing, cluster walk | 882 |
| `Prop_PropSetter.java` | Writes stat fields on a trigger, undoes them on the opposite trigger, supports `${STAT}` and `@x1` math | 633 |
| `Prop_QuestGiver.java` | Offers a fixed or generated quest on a trigger message, with a Y/N prompt | 523 |
| `Prop_ReqCapacity.java` | Room or area caps on people, players, mobs, items, siege weapons and weight; spills excess items to a neighbour | 514 |
| `Prop_ReqEntry.java` | Zapper-mask entry gate with outcome BLOCK (default) or ACCUSE | 301 |
| `Prop_Retainable.java` | Hireling: wages paid from the owner's bank each period; the hireling quits when unpaid | 285 |
| `Prop_OutfitContainer.java` | Container remembers what was put in; wearing it dresses the wearer in those items | 279 |
| `Prop_ReqTattoo.java` | Access gate on tattoo masks (`+NAME`, `-NAME`, `+ALL`, `-ALL`, account tattoos) | 246 |
| `Prop_ReqStat.java` | Gate on stat comparisons for entry, sitting, doors, wear, wield, fill and put | 234 |
| `Prop_ReqLevels.java` | Level gate with `<`, `>`, `=` terms | 229 |
| `Prop_PrivateProperty.java` | Item owner name, price and `EXPIRESEC`; ownership released when unclaimed | 219 |
| `Prop_OpenCommand.java` | Object-specific verb phrases (for example unlock then open), attached through the unknown-command message | 190 |
| `Prop_ReqNoMOB.java` | Entry gate that excludes monsters; party and rider pass-through | 173 |
| `Prop_RestrictSkills.java` | Blocks named skills unless the room domain is allowed | 162 |
| `Prop_NoTeleport.java` | Blocks teleport into and summon into; `EXCEPTIONS`, `REDIRECT`, `MESSAGE` | 154 |
| `Prop_OpenPassword.java` | Speaking a password opens an exit or container | 153 |
| `Prop_RestrictSpells.java` | Blocks named spells, optionally only for listed targets or planes | 151 |
| `Prop_ReqClasses.java` | Class gate with `+NAME`, `-NAME` and `ALL` | 143 |
| `Prop_RideEnabler.java` | Grants spells to whoever rides the host (extends `Prop_HaveEnabler`) | 143 |
| `Prop_NoPurge.java` | Pins expiration dates of room or item contents so they are not purged | 142 |
| `Prop_RoomPlusForSale.java` | Expandable-lot variant of `Prop_RoomForSale` | 142 |
| `Prop_ReqRaces.java` | Race gate, same grammar as `Prop_ReqClasses` | 141 |
| `Prop_ReqAlignments.java` | Alignment gate, same grammar | 139 |
| `Prop_NoTeleportOut.java` | Blocks teleport out of the property and summon from outside it | 136 |
| `Prop_ReRollStats.java` | Re-rolls base stats when the owning player looks at self | 134 |
| `Prop_RideSpellCast.java` | Casts spells on riders when mounted, or when a rideable closes (extends `Prop_HaveSpellCast`) | 134 |
| `Prop_PracticeDummy.java` | Invulnerable training mob unless its text contains `KILL` | 127 |
| `Prop_PlayerInstance.java` | Enter redirected to a per-group-leader copy of the room's area | 125 |
| `Prop_RoomLit.java` | Removes the dark bit, optionally only at listed hours | 122 |
| `Prop_RoomDark.java` | Adds the dark bit, optionally only at listed hours | 116 |
| `Prop_NoTelling.java` | Blocks tell messages in a room, area or mob | 114 |
| `Prop_NoChannel.java` | Blocks channel messages in a room or area; `SENDOK` and `QUIET` flags | 112 |
| `Prop_PeaceMaker.java` | Vetoes hostile messages; an NPC answers with a peace line | 109 |
| `Prop_NoSummon.java` | Blocks summoning abilities; `ALLOWNONAGGR` and `MAGICONLY` exemptions | 105 |
| `Prop_RideZapper.java` | On mounting, zaps a rider who fails a mask, with a percentage roll (extends `Prop_HaveZapper`) | 101 |
| `Prop_NoCraftability.java` | Marks items uncraftable unless a mask passes | 98 |
| `Prop_ReqPKill.java` | Player-kill zone: entry needs the `PLAYERKILL` attribute | 95 |
| `Prop_ReqHeight.java` | Height limit on entry, default 100 | 92 |
| `Prop_NoRecall.java` | Blocks recall from inside; a mob host fizzles only while in combat | 86 |
| `Prop_Resistance.java` | Resistances for the affected mob (extends `Prop_HaveResister`) | 85 |
| `Prop_NoPKill.java` | Forbids player-vs-player malice, or allows it only within a level gap | 84 |
| `Prop_NoCharm.java` | Blocks charming abilities | 83 |
| `Prop_NewDeathMsg.java` | Replaces the first line of the death message | 81 |
| `Prop_RideResister.java` | Resistances for riders (extends `Prop_HaveResister`) | 77 |
| `Prop_NoDamage.java` | Sets damage to zero for the affected mob or item | 76 |
| `Prop_RideAdjuster.java` | Stat adjustments for riders (extends `Prop_HaveAdjuster`) | 76 |
| `Prop_NoOrdering.java` | Blocks ORDER commands aimed at mobs inside the property | 75 |

Base-class helpers outside this bucket that the chapter relies on: `com/planet_ink/coffee_mud/Abilities/interfaces/TriggeredAffect.java:37-80` (trigger constants and names), `Prop_HaveZapper.java:265-300` (zapper `executeEvent`), `Prop_SpellAdder.java:314` and `Prop_HaveEnabler.java:87` (`addMeIfNeccessary` overloads).

## How it works

### 1. The property contract

- A property is an `Ability` whose `classificationCode()` is `ACODE_PROPERTY` (`Property.java:105-109`). It is attached to a target, and `canAffect(Physical)` (`Property.java:147-164`) checks the target type against `canAffectCode()`. Subclasses declare the mask, for example `Prop_ReqLevels.java:50-53` returns `CAN_ROOMS|CAN_AREAS|CAN_EXITS`.
- Each class keeps its data in its misc text and parses it in `setMiscText`. Examples are `Prop_ReqLevels.java:75-154`, `Prop_ReqCapacity.java:95-123` and `Prop_PropSetter.java:105-166`.
- Behaviour comes from the message pipeline. `okMessage` can veto by returning false (`Prop_ReqLevels.java:199-228`). `executeMsg` performs side effects (`Prop_RoomForSale.java:301-336`). Stat changes go through `affectPhyStats` (`Prop_RoomLit.java:116-121`). `tick` runs periodically (`Prop_Retainable.java:132-210`).
- Deferred effects are queued on the message with `msg.addTrailerRunnable(...)`, so they run only after the message is accepted. Examples: `Prop_PropSetter.java:568-622`, `Prop_QuestGiver.java:379-394`.
- Which message types matter is set by `triggerMask()`, and the names come from `TriggeredAffect.TRIGGER_DESC` (`TriggeredAffect.java:40-71`). `ALWAYS`, `ENTER`, `BEING_HIT`, `WEAR_WIELD`, `GET`, `USE`, `PUT`, `MOUNT`, `DROP_PUTIN` and `HITTING_WITH` are the ten names.

### 2. Entry gates: the zapper family

Nine classes share one shape and differ only in the test: `Prop_ReqLevels`, `Prop_ReqClasses`, `Prop_ReqRaces`, `Prop_ReqAlignments`, `Prop_ReqNoMOB`, `Prop_ReqEntry`, `Prop_ReqPKill`, `Prop_ReqHeight` and `Prop_ReqTattoo`. Each has `flags()` set to `FLAG_ZAPPER` (for example `Prop_ReqLevels.java:63-66`).

Common control flow:

- The gate runs on `TYP_ENTER` into a room and, for most gates, on `TYP_SIT` onto a rideable (`Prop_ReqLevels.java:201-205`; `Prop_ReqEntry.java:264-297` also covers sleep, mount and enter). Characters who are falling are exempt (`isFalling`, same lines).
- Unless `NOFOLLOW` is given, the test covers the actor, the actor's group members and their riders (`Prop_ReqLevels.java:207-216`, using `getGroupMembers` and `getRideBuddies`). One passing member lets the whole move through (`:217-223`). Otherwise the actor sees a message and the move is vetoed (`:224-225`).
- Tracking monsters always pass. Sneaking passes unless `NOSNEAK` is given (`Prop_ReqLevels.java:170-174`).
- The default refusal is "You are not allowed to go that way." (`Prop_ReqLevels.java:59`), replaceable with `MESSAGE=` (`:98`).

Specific rules:

- **Level** (`Prop_ReqLevels.java:99-151`, `:156-196`). Terms are `<`, `>` or `=` followed by a number. `>=10` expands to `(>,10)` and `(=,10)` (`:137-141`), which I confirmed with a transcription of the parse loop. Terms are ORed (`:177-194`). The token `ALL` lets everyone in (`:92-93`, `:161-165`). The token `SYSOP` fails everyone without the GOTO security flag, and it runs before the tracker and sneak bypasses (`:167-171`). Note that a rideable target bypasses the check (see Problems).
- **Class and race** (`Prop_ReqClasses.java:89-110`, `Prop_ReqRaces.java:89-108`). The rule text is searched as a string. `-ALL` blocks unless the entity is listed with a leading `+`. A listed `-NAME` blocks outright. Classes match on `displayClassName()` and fall back to the base class. Races match on `raceName()`.
- **Alignment** (`Prop_ReqAlignments.java:89-106`). The same grammar, matched on `CMLib.flags().getAlignmentName(mob)`.
- **No monsters** (`Prop_ReqNoMOB.java:89-98`). The result is `!mob.isMonster()`, so monsters are excluded. A monster who has a player in its group or riders gets through (`:113-166`).
- **Mask** (`Prop_ReqEntry.java:77-145`). Tokens beginning with `+` or `-` are collected into a compiled zapper mask (`getPreCompiledMask`, `:143`). `MESSAGE=` and `EVENT=` are parsed. The first word of `EVENT` selects the outcome. `BLOCK` is the default (`:153-156`). `ACCUSE` reports the subject to the local legal behaviour and then lets the move through (`:157-166`, and `return true` at `:162-165` makes `okMessage` allow it). If there is no legal object or behaviour, the move is blocked. The `DEITY` clause of the mask is read back by `getWorshipCharID` (`:171-195`), so a room can act as a deity's gate.
- **Tattoo** (`Prop_ReqTattoo.java:101-205`). The mask grammar has `+NAME`, `-NAME`, `+ALL`, `-ALL`, `+NONE` and the prefix `ACCOUNT ` for account-wide tattoos. The default mode is a conjunction (each `+` must be held and each `-` must be absent, `:166-199`). `-ALL` means "holds any of the `+` tattoos" (`:188-197`). The `+ALL` mode cannot deny (see Problems).
- **Stat** (`Prop_ReqStat.java:100-130`). The gate loops over `CharStats.CODES.ALLCODES()` and calls `CMParms.getParmCompare(text(), name, value, comparator)`, so rules look like `STR>10`. The refusal names the first failing stat, for example "You are too @x1 to @x2." (`:120`). The same test guards entry (`:171-179`), sitting on rideables, doors and containers (`:137-169`), wear and wield (`:198-228`), fill (`:188-197`) and put (`:207-216`).
- **Height** (`Prop_ReqHeight.java:73-91`). The limit is an integer, default 100, compared with `phyStats().height()`. Refusal: "You are too tall to fit in there." (`:86`).
- **Player-kill zone** (`Prop_ReqPKill.java:67-94`). Entry needs `MOB.Attrib.PLAYERKILL` (`:76-81`). Any non-monster inside the zone who lacks the attribute has it switched on, with the message "Your PLAYERKILL flag is now ON!" (`:83-92`).

### 3. Capacity and overflow: `Prop_ReqCapacity`

- Parameters are `PEOPLE`, `PLAYERS`, `MOBS`, `ITEMS`, `WEIGHT`, `SIEGEWEAPONS`, `ROOMS`, `INDOOR`, `CONTAINERSOK` (`Prop_ReqCapacity.java:95-123`). With no parameters at all, `PEOPLE=2` (`:106-107`).
- Entry checks compare head counts before entry (`:392-425`). Refusals include "No more people can fit in there." (`:400`) and "No more MOBs can fit in there." (`:417`).
- Drops, pushes, pulls and generated items are checked against item, siege and weight caps (`:433-508`). For example, "There is no more room in here to drop @x1." (`:476`). When raw resources are present and `rawResources/itemCap > 0.5`, the player is also told to bundle them (`:477-478`).
- Overflow is evaluated at most every 30 minutes, triggered from any `okMessage` (`:377-388`). The excess goes to the neighbouring room that has an open exit, is not airy, and has the fewest items, siege weapons or weight (`:212-229`, `:282-304`, `:327-343`). Items are moved in a fixed order: loose non-container items, then loose containers, then contents pulled out of containers (`:232-273`). Moves go through `overflowFix`, which clears rejuvenation and uses `Expire.Inheret` (`:167-179`).
- The help text says `ROOMS` limits the number of new rooms a LotsForSale expansion can create (`resources/help/arc_properties.ini:1755-1780`). In code, `roomLimit` is parsed and displayed but never enforced (`Prop_ReqCapacity.java:63`, `:90`, `:118`).

### 4. Ownership, lots, rent and decay

- **Storage.** `Prop_RoomForSale` keeps owner, flags and price in one string, `owner/[RENTAL ][GRID ][ALLOWTHEFT ][TAX<n>X ]price` (`saveData`, `Prop_RoomForSale.java:133-141`). Parsers follow (`:149-243`). A missing or zero price defaults to 100000 (`:87-104`).
- **Identity.** The land title ID is `LAND_TITLE_FOR#` plus the extended room ID (`:259-271`). The lot ID is `ROOM_PROPERTY_` plus the room ID (`:273-277`).
- **Upkeep** (`updateLotWithThisData`, `Prop_RoomForSale.java:521-742`). If the lot is unowned, items are destroyed on a scheduled reset, or they get an expiry of `EXPIRE_PLAYER_DROP` minutes (`:549-559`). If it is owned, item expiry is cleared and rejuvenation is turned off (`:678-692`). The room's sale and rent markers are stripped from its description (`:652-663`). If the named owner no longer exists as a player or clan, the lot is released with a warning (`:627-649`).
- **Decay.** If the room's items have not been saved for more than `t` days, unclaimed loose items gain a `Dusty` effect with the text `LEVEL=0 INTERVAL=`+`t0` (`:699-730`). The value of `t` is derived from the calendar length and `TIME_SAVETHREAD_SLEEP` (`:700-703`).
- **Rent** (`doRentalProperty`, `:745-843`). `updateLot` runs once per in-game day (`:860-862`). Each due month is withdrawn from the owner's local bank with `CMLib.beanCounter().modifyLocalBankGold(...)` (`:801-806`). Due dates are kept per owner in player data under `RENTAL INFO` as `ID|~>|day month year|~;|` (`:763-765`). If a payment fails, `setOwnerName("")` releases the lot (`:864-871`).
- **Expansion.** `Prop_RoomPlusForSale` returns `allowsExpansionConstruction()` true (`Prop_RoomPlusForSale.java:65-67`). Its cluster code is broken (see Problems).
- **Cluster walk.** `fillCluster` (`Prop_RoomForSale.java:357-436`) does a breadth-first walk over same-area rooms that carry the same lot effect and an identical owner. It puts the room with the outside door first.
- **Save hook.** On shutdown, room expiry or room reset, the lot is updated and the room's savable mobs are written (`:305-333`).
- **Item ownership** (`Prop_PrivateProperty.java:64-190`). `OWNER`, `PRICE` and `EXPIRESEC` are parsed from the text (`:64-73`). When carried by a mob, the item loses the property if the carrier lacks weak privileges (`:131-143`). When lying in a room, the owner's absence starts a countdown (`:145-187`).

### 5. Hirelings: `Prop_Retainable`

- Text is `amount [PERSIST=true];periodDays;lastPayTimestamp` (`Prop_Retainable.java:91-125`).
- The payroll period is `payPeriodLengthInMudDays * TICKSPERMUDDAY * tickMillis` (`:159-163`). When it elapses, the owner's account is debited with `modifyLocalBankGold` (`:191-194`).
- The hireling leaves and is destroyed if the property it stands on has an owner and the lord has no privileges there (`:168-180`), if there is no owner (`:182-190`), or if the account cannot pay (`:195-204`). The lines are "Hey, I'm not a crook!", "Argh! I quit!" and "I don't work for free!  I quit!".
- When the hireling enters a room the lord has privilege in, that room becomes its start room and its rejuvenation is turned off, so it persists (`:254-266`). With `PERSIST=true`, following is cleared on shutdown, expiry or quit so the hireling can be saved (`:274-280`).
- On hire (`TYP_BUY`), `tellSkills` lists the hireling's abilities and sets any zero proficiency to `50 + level - lowestQualifyingLevel` (`:212-231`).

### 6. Magic and transport blocks

These classes test the tool of a message. The relevant checks are `FLAG_TRANSPORTING`, `FLAG_SUMMONING` and `FLAG_CHARMING` on the ability (for example `Prop_NoTeleport.java:92-93`), together with the ability's class code: spell, prayer, chant or song. For those classes the caster is told "Magic energy fizzles and is absorbed into the air." (`Prop_NoTeleport.java:146`, `Prop_NoTeleportOut.java:129`, `Prop_NoSummon.java:94`, `Prop_NoCharm.java:78`, `Prop_RestrictSpells.java:140-146`). Other abilities are blocked silently, or with a custom message.

- **Teleport in** (`Prop_NoTeleport.java:85-149`). The block condition is `((!shere)&&(!summon)&&(teleport))||((shere)&&(summon))` (`:123-124`). Here `shere` means the caster is in the property. So it blocks teleport from outside and summon from inside. `EXCEPTIONS` may list a room ID, an area name, an ability ID or `planarability` (`:115-120`). `REDIRECT=` changes the target room with `msg.setTarget` and lets the spell through (`:132-141`).
- **Teleport out** (`Prop_NoTeleportOut.java:102-131`). The block condition is `((shere)&&(!summon)&&(teleport))||((!shere)&&(summon))` (`:109-110`). This is the mirror image: it blocks teleport from inside and summon from outside. Summon pulls the target to the caster, so the second case stops a target being pulled out.
- **Summon** (`Prop_NoSummon.java:80-101`). Every summoning ability inside the property is blocked. `ALLOWNONAGGR` lets through summons aimed at non-aggressive targets (`:86`). `MAGICONLY` lets non-magic summons through (`:97-101`). Non-magic summons are refused with "<S-YOUPOSS> @x1 attempt fails." (`:99`).
- **Charm** (`Prop_NoCharm.java:65-80`). Charming abilities are refused when the caster is in the room or area, or when the target is the property itself.
- **Spells** (`Prop_RestrictSpells.java:65-150`). Keys are ability IDs in upper case. A value in brackets lists targets or planes, for example `SPELL_X(NAME)`. A spell with no list is blocked everywhere (`:104`, `:139-147`). A spell with a list is blocked only when the target name or plane is listed (`:106-125`).
- **Skills** (`Prop_RestrictSkills.java:113-161`). A skill is allowed in rooms whose domain is in `ONLYROOMS`, or, when `NEVERROOMS` is set, in rooms outside it (`:151-154`). Otherwise the default refusal is "You can't do that here." (`:58`, `:155-158`). `WEARONLY` restricts the check to properly worn items (`:125-127`).
- **Recall** (`Prop_NoRecall.java:67-85`). Recall is refused from inside. A mob host fizzles the recall only when the caster is in combat (`:74`). Rideable, item-owner and room hosts block only when the caster is riding, owns or is in them, and exit and area hosts always block (`:75-79`).

### 7. Combat, social and command overrides

- **PeaceMaker** (`Prop_PeaceMaker.java:61-108`). A message with a malicious flag on its source, target or others, from someone other than the property's occupant, is vetoed and turned into a no-effect message (`:103`). An NPC host, if alive, awake, mobile, unbound and not in combat, says one of its semicolon-separated lines, picked by `dice().roll(1,n,-1)` (`:72-88`). Both parties are made peaceful (`:99-102`). The default text is "No fighting!" (`:79`).
- **NoPKill** (`Prop_NoPKill.java:55-83`). Player-on-player malice is refused with "Player killing is forbidden here." when the parameter is 0 (`:66-71`). Otherwise it is refused when the absolute level difference exceeds the parameter (`:72-80`).
- **PracticeDummy** (`Prop_PracticeDummy.java:65-126`). The dummy is unkillable unless the text contains `KILL` (`:69`). It gets max HP 99999 (`:73-78`) and armour 100 (`:81-86`). Death is refused with "You are not allowed to die." (`:96-99`). Malice heals it and pacifies the room (`:102-115`). Item grabs are refused with "Dummys cant get anything." (`:118-123`).
- **NoDamage** (`Prop_NoDamage.java:68-75`). Any damage message where the affected thing is source or tool gets `setValue(0)`.
- **Silencing.** `Prop_NoTelling` (`:71-113`), `Prop_NoChannel` (`:80-111`) and `Prop_NoOrdering` (`:53-74`) veto messages. The channel rule has a precedence problem (see Problems).
- **OpenCommand** (`Prop_OpenCommand.java:49-189`). Each phrase is stored as a word array whose first word is upper-cased (`:54-81`). A trailing `*` matches a word prefix, and `**` accepts any remainder (`:127-135`). The property intercepts messages whose source minor is `TYP_HUH`, the unknown-command fallback (`:109-112`). It then performs an unlock and an open on the exit or container (`:150-183`) and vetoes the fallback (`:184`). The `noopen` flag blocks direct open (`:63-64`, `:98-107`). `message=` overrides the text (`:66-68`).
- **OpenPassword** (`Prop_OpenPassword.java:54-152`). The password is taken from the text between the first and last single quote of a spoken message (`:105-109`). The format is `LANGUAGE;PASSWORD;MESSAGE`. The language must be a `Language` ability for the rule to restrict speech (`:63-64`). Matching is case-insensitive. The password is stored in plain text in the misc text, and speech is public.
- **QuestGiver** (`Prop_QuestGiver.java:36-523`). The default trigger depends on the target: area, room or exit gives `ENTER`, mob gives `DEATH`, item gives `GET` (`:87-99`). `TRIGGER=` overrides it (`:101-133`). `QUEST=` names a quest. `QUEST=GENERATE:...` ticks the `RandomQuests` behaviour up to ten times until it yields a name (`:151-165`). The offer (`maybeGiveTheQuest`, `:181-272`) skips monsters and players already listed in `leaveThemAlone`. It announces rows labelled Quest, Giver, In and Descrip. (`:190-210`). With `OPTIONAL=false` the quest is accepted silently (`:260-266`). Otherwise the player is asked "Accept this quest (y/N)? " (`:225-257`), and then told "You are now on the quest '@x1'." (`:251`).
- **NewDeathMsg** (`Prop_NewDeathMsg.java:61-80`). When the "others" death message contains `<S-NAME> IS DEAD` at a position after 0 (`:69`), its first line is replaced by the property text (`:71-76`).

### 8. Mounts: riders as targets

- **RideEnabler** (`Prop_RideEnabler.java:77-116`). On each stat recompute, every rider of the host gets the granted spells through `addMeIfNeccessary(M,M,maxTicks)` (`:95`). Riders who have dismounted lose them (`:100-109`). A `processing` flag guards against re-entry (`:79-83`). `STAT-LEVEL` sums the lowest qualifying levels of the granted abilities (`:118-136`).
- **RideSpellCast** (`Prop_RideSpellCast.java:76-133`). Spells are cast on riders when the host is mounted (`processRideSpellCast`, `:87-127`). Closing the rideable also triggers the cast (`:80-84`). The effect is re-run from `affectPhyStats` (`:130-133`).
- **RideZapper** (`Prop_RideZapper.java:73-100`). On sit, sleep, mount or enter, if the mount mask refuses the rider and a `rollPercentage()` roll is within `percent`, the rider is zapped. The default text is "<O-NAME> zaps <S-NAME>, making <S-HIM-HER> jump up!" (`:58`, `:92-95`). The zap itself is `executeEvent` in `Prop_HaveZapper.java:265-290`.
- **RideResister and RideAdjuster** (`Prop_RideResister.java:61-76`, `Prop_RideAdjuster.java:61-75`). Resistances and stat adjustments apply to a rider through `canResist` and `canApply`.

### 9. The setter: `Prop_PropSetter`

- **Parsing** (`Prop_PropSetter.java:105-166`). The misc text is split with `CMParms.parseEQParms`. Every key that is not `TRIGGER`, `IDENTITY` or `BUBBLE` is a field to set (`:114-146`). An optional `<PREVIOUS>` XML block after the parameters records original values, so they can be undone after a reboot (`:150-164`).
- **Trigger bits.** Each `TRIGGER_DESC` index `i` becomes bit `2^i` (`:126-138`). `TRIGGER_ALWAYS` applies from `affectPhyStats` (`:493-502`). Other triggers become trailer runnables on the message (`:559-631`). `properTarget` decides whether the change lands on the object or on its holder when `BUBBLE=true` (`:541-557`).
- **Field lookup** (`findModifiable`, `:195-358`). The setter tries the affected object, its base physical stats, its base character stats and state, and finally the generic "gen" stat accessor.
- **Apply** (`addEffect`, `:412-479`). Previous values are recorded for undo unless the change is permanent (`:436-438`). If the value contains `@` or `${`, the old value is substituted for `@x1`, other stats are substituted for `${STAT}`, and the result is evaluated as a math expression (`:440-466`). The value is then written and stats are recovered (`:467-472`).
- **Undo** (`undoEffect`, `:385-410`). Entries are matched by identity. The identity is the player name for players, the database ID for other saved objects, or the class name (`:176-193`).
- **Permanent triggers.** `BEING_HIT`, `HITTING_WITH`, `USE` and the fill, drink, eat and pour messages pass `perm=true` (`:568-622`), so they are never undone. The help entry describes this as a savable change (`resources/help/arc_properties.ini:2429-2446`).
- **Shipped use.** `resources/skills/embellishing.txt:13-35` gives pocket garments an always-on capacity bonus, for example `*Prop_PropSetter;trigger=always bubble=false capacity=(${weight}+5)` for "tiny pockets", with +10, +10, +25 and +50 for the larger pockets.

### 10. Light, time and persistence

- `Prop_RoomDark` sets the `IS_DARK` disposition bit (`Prop_RoomDark.java:110-115`). `Prop_RoomLit` clears it when the room is dark and the lit hours apply (`Prop_RoomLit.java:116-121`). An optional `HOURS=` list is matched against `CMLib.time().localClock(affected).getHourOfDay()` (`Prop_RoomLit.java:85-112`, `Prop_RoomDark.java:70-108`).
- `Prop_NoPurge` writes an expiration date onto the room's items, the container's deep contents, or the item itself on every stat recompute (`Prop_NoPurge.java:71-102`). It removes itself after the date has passed (`:137-139`). The date is a long or a `CMLib.time().string2Millis` string (`:58-68`).
- `Prop_PlayerInstance` (`Prop_PlayerInstance.java:62-124`) redirects entry to an instance named `owner_room`, where `#` in the room ID becomes `_` (`:73-75`). If the instance does not exist, it copies the template room from the database and stores it under `PLAYERINSTANCE` in the owner's player data (`:99-115`). Entry is then redirected to the instance room `#0` (`:118-121`). The area class is `com/planet_ink/coffee_mud/Areas/PlayerInstanceArea.java`.
- `Prop_ReRollStats` (`Prop_ReRollStats.java:76-133`) reacts to the owner's own LOOK message, and runs `CharCreation.promptBaseCharStats` with a 300 timeout (`:101`; the parameter is `timeoutSecs` in `Libraries/CharCreation.java:5107`). It can re-pick the class (`:103-105`), then regrants abilities (`:107`). A `reRollFlag` prevents re-entry (`:87-92`).

## Content worth mining

- **Real-estate markers** (`resources/lists.ini:551-554`). Four strings identify lot state in room descriptions: "An empty room", "An empty plot", "This lot is for sale (look id).", and "This lot (look id) is for rent on a monthly basis." `Prop_RoomForSale.initializeClass` reads them as indoor, outdoor, sale and rent markers (`Prop_RoomForSale.java:121-131`).
- **Refusal messages as data.** Each gate has a short default line that can be overridden with `MESSAGE=`. Examples: "You are not allowed to go that way." (ReqLevels, ReqClasses), "You can not go that way." (ReqRaces), "You may not go that way." (ReqAlignments), "You are not allowed in there." (ReqNoMOB), "You have not been granted authorization to go that way." (ReqTattoo), "You are too tall to fit in there." (ReqHeight), "You must have your playerkill flag set to enter here." (ReqPKill).
- **The shared fizzle line.** "Magic energy fizzles and is absorbed into the air." is used by six magic blocks. It is a ready-made flavour line for any game that wants a quiet spell refusal.
- **Stat-gate verbs.** `Prop_ReqStat` builds sentences from stat names and attribute descriptions: "You aren't the right @x1 to @x2.", "You are too @x1 to @x2.", "You are not @x1 enough to @x2.", where `@x2` is a verb phrase such as "open that" or "wear that" (`Prop_ReqStat.java:110-124`).
- **Quest flow template.** The announcement rows (Quest, Giver, In, Descrip.) and the Y/N prompt (`Prop_QuestGiver.java:190-257`) are a complete offer template.
- **Hireling lines.** "Payday!", "Argh! I quit!", "I don't work for free!  I quit!", "Is this my new permanent post?" (`Prop_Retainable.java:149`, `:185-199`).
- **Pocket garments.** The embellishing table (`resources/skills/embellishing.txt:13-35`) is a small, well-formed catalogue: each row gives a name, two numbers, a format string, the consumed materials, the worn locations and the property with a capacity formula. The meaning of the two numbers was not verified.
- **Help text as a second catalogue.** `resources/help/arc_properties.ini` has a block for each property, with parameter syntax and examples (for example `-all +manlymen` for tattoos, and `<=10 >=5` for levels, at `:1880-1898` and `:1822-1834`).

## Notable design ideas

- **Gates test the party, not just the actor.** Level, class, race, alignment, tattoo and no-monster checks walk the group and riders (`Prop_ReqLevels.java:207-216`, `Prop_ReqNoMOB.java:116-166`), so one member's standing can admit the whole party.
- **Consequences, not just refusals.** `EVENT=ACCUSE` sends the subject to the legal system and lets them through (`Prop_ReqEntry.java:157-166`). A gate can therefore be a crime trigger rather than a wall.
- **Magic blocks keyed on flags, not spell names.** `FLAG_TRANSPORTING`, `FLAG_SUMMONING` and `FLAG_CHARMING` plus the spell, prayer, chant and song class codes cover every ability with one rule (`Prop_NoTeleport.java:92-130`, `Prop_NoSummon.java:80-92`). New spells need no changes to the blocks.
- **Two directions per boundary.** `NoTeleport` and `NoTeleportOut` make the boundary symmetric: one blocks entry by teleport and summon, the other blocks exit by teleport and summon (`Prop_NoTeleport.java:123-124`, `Prop_NoTeleportOut.java:109-110`).
- **Effects after acceptance.** `PropSetter`, `QuestGiver` and `OutfitContainer` use trailer runnables, so their effects run only if the triggering message succeeds (`Prop_PropSetter.java:568`, `Prop_QuestGiver.java:292-312`, `Prop_OutfitContainer.java:260-274`).
- **Reversible, self-referential stat changes.** `PropSetter` can express "capacity = old value + 5" or "capacity = this stat + 5 * weight" as text, apply it, and undo it on the opposite event (`Prop_PropSetter.java:412-479`).
- **Abandoned items gather dust.** A long-unsaved room gets a `Dusty` effect on its loose items (`Prop_RoomForSale.java:699-730`), a cheap signal of neglect.
- **Retainers leave when unpaid.** Missing wages make the hireling quit with an in-character line, which is a cheap, legible payroll (`Prop_Retainable.java:171-204`).
- **OutfitContainer dresses the wearer.** Wearing a container puts on the items that were stored in it, first removing conflicting clothing (`Prop_OutfitContainer.java:169-240`).
- **NPC peacekeeping.** A peaceful NPC answers violence with one of several lines, chosen by dice (`Prop_PeaceMaker.java:79-84`).
- **Hour-based lights.** `HOURS=` lists give day and night lighting without a separate clock system (`Prop_RoomLit.java:85-112`).

## Problems and cruft

Each item below was confirmed by reading the cited lines. Items marked as traced were also transcribed into Python in `scratch/b077`. The Java was not executed.

1. **Trigger bits do not match the constants** (`TriggeredAffect.java:56` and `:58` against `TRIGGER_DESC` at `:60-71`). `DROP_PUTIN` is `64` and `HITTING_WITH` is `128` in the constants, but `TRIGGER_DESC` puts them at indices 8 and 9. The setter's parser therefore stores them as `256` and `512` (`Prop_PropSetter.java:126-138`). The checks at `:580` and `:615` test `64` and `128`, so a setter with `TRIGGER=DROP_PUTIN` or `TRIGGER=HITTING_WITH` never fires. Meanwhile `PUT` (`64`) also fires on drop and `MOUNT` (`128`) also fires on hitting with the tool.
2. **Variable substitution corrupts values with two references** (`Prop_PropSetter.java:442-452`; traced). The loop substitutes from `finalVal` but takes the tail from the original `value` at `xe+1`. Transcription gives `${A}+${B}` becoming `5+10{B}`, and `${WEIGHT}*${B}` becoming `5*10HT}*10B}`. Single-reference values work.
3. **Duplicated condition in the setter's DAMAGE and PUT handling** (`Prop_PropSetter.java:591-593`). The condition tests `msg.target()==affected` twice. The intended second operand is unknown.
4. **Undo relies on finalizers** (`Prop_PropSetter.java:168-174`). `finalize()` calls `undoEffect` when the object is collected. Finalizers are unreliable and deprecated.
5. **Capacity loops miscount** (`Prop_ReqCapacity.java:307`, `:346-353`). The siege overflow loop decrements `totOver` on every visited item, not on every moved one, so it can move too few. The weight loop subtracts the weight of each moved item and also decrements `totOver` once per iteration in the loop header, so it can over- or under-shoot.
6. **Capacity accounting and dead code** (`Prop_ReqCapacity.java:89`, `:117`, `:198-201`). `accountForYourself` prints `itemCap` on the siege-weapon line. `SIEGEWEAPONS` defaults to `ITEMS` (`:117`), so setting `ITEMS` silently caps siege weapons too. The empty branch at `:198-201` does nothing. `ROOMS` is parsed but never enforced (`:63`, `:118`).
7. **Level terms are dropped** (`Prop_ReqLevels.java:105-151`; traced). The loop searches for `>` first, then `<`, then `=`, each time from the last position. A `<` term that appears before a `>` term is never read. Transcription gives `<30 >10` only `(>,10)`, while `>10 <30` gives both terms. Unverified at runtime: `text.charAt(x)` at `:117` and `:125` can run past the end if the text ends with an operator.
8. **Level gate ignores rideable targets** (`Prop_ReqLevels.java:161-165`, with `:199-205`). `passesMuster` returns true whenever the target is not a `Room`, so sitting on or mounting a rideable with a level rule is never checked.
9. **Inverted `NOFOLLOW` test in six files.** The test is `"NOFOLLOW".startsWith(s)` (`Prop_ReqLevels.java:86`, `Prop_ReqEntry.java:93`, `Prop_ReqClasses.java:80`, `Prop_ReqRaces.java:80`, `Prop_ReqNoMOB.java:80`, `Prop_ReqAlignments.java:80`). The intended form is `s.startsWith("NOFOLLOW")`. Tokens that are prefixes of `NOFOLLOW` (`N`, `NO`, `NOF`) set the flag by mistake. The real token still works, so the impact is small.
10. **`+ALL` tattoo masks never deny** (`Prop_ReqTattoo.java:178-186`, `:201-204`). In that mode every path returns true, including the fall-through at the end. The help text says `+all` means "allow unless has any of the following -tattoos" (`resources/help/arc_properties.ini:1880-1898`). The code cannot deny.
11. **Private property expiry units** (`Prop_PrivateProperty.java:161-170`, `:184`). With the room empty, `EXPIRESEC` is added as milliseconds. When someone is present it is multiplied by 1000. The help says seconds (`arc_properties.ini:1713-1724`), so an empty room expires 1000 times too early.
12. **Spell target and plane lists are case-mismatched** (`Prop_RestrictSpells.java:69`, `:107`, `:117`). The parameter list is upper-cased, but it is compared with `msg.target().Name()` and with the plane name without upper-casing. Name-based and plane-based exemptions therefore match only all-caps names and planes.
13. **Channel precedence bug** (`Prop_NoChannel.java:89-90`). The condition is `(source==affected) || (!(affected instanceof MOB) && channelMatch)`, because `&&` binds tighter than `||`. For mob targets the channel list is ignored when they speak. The `QUIET` check runs only inside the same block, so for mob targets `QUIET` never blocks incoming messages.
14. **Retainer leave timer uses milliseconds** (`Prop_Retainable.java:142`). The test is `(System.currentTimeMillis()-lastMoveIn)>4`, which is four milliseconds. The intent (seconds or ticks) is not clear.
15. **Expansion lot IDs are never set** (`Prop_RoomPlusForSale.java:132-133`). The loop only updates neighbours that are `Prop_LotsForSale` instances. `Prop_RoomPlusForSale` extends `Prop_RoomForSale` (`:36`), not `Prop_LotsForSale`, and the fetched effect is its own class, so the neighbour IDs are never written. The branch at `:109-112` is empty.
16. **Unreachable code**: `Prop_ReqStat.java:72-73` repeats the room, area and exit test already handled at `:66-67`; `Prop_RestrictSkills.java:134-146` computes `roomS` and `roomD`, which never affect the result.
17. **Loose item and worn state in `OutfitContainer`** (`Prop_OutfitContainer.java:210-218`). The worn code of the candidate item is set to `IN_INVENTORY` to test `canWear`. It is restored only when the test succeeds. On failure the candidate stays unworn, and the code adds `I`, the outfit item that is not yet worn, to `thingsToRemove` instead of `chkI`.
18. **Duplicate-name numbering never runs** (`Prop_OutfitContainer.java:95-105`). `lastName` is the constant `""`, so the `$name$.N` suffix is never produced. Two identically named items resolve to the first one. `thingsToReDo` is filled but never used (`:183-184`, `:221`, with a TODO at `:223-226`).
19. **Quest-giver state is shared unsafely** (`Prop_QuestGiver.java:51`, `:133-135`, `:171-173`, `:307`, `:340`). `leaveThemAlone` is a plain `TreeSet` written from scheduled threads without synchronisation. It also doubles as an error-log filter, using the sentinel strings "TheLog" and "TheLog2". Names are added and never removed for the life of the instance.
20. **Per-recompute item walks** (`Prop_NoPurge.java:72-101`, `Prop_RoomForSale.java`). Each stat recompute walks the room's items and writes their expiry, so the cost grows with room size.
21. **Caches that are never invalidated.** `Prop_RoomLit` and `Prop_RoomDark` parse `HOURS` once per instance under a `setup` flag and have no `setMiscText` override (`Prop_RoomLit.java:73-112`, `Prop_RoomDark.java:67-108`). Changing the text on a live instance has no effect until it is recreated. `Prop_NoCraftability` caches its result on the first stat recompute and resets only on `setMiscText` (`Prop_NoCraftability.java:75-94`).
22. **Possible null room in player instancing** (`Prop_PlayerInstance.java:107-112`). `DBReadRoomObject` is used without a null check before `delEffect` and `setRoomID`. Whether it can return null at runtime is unverified.
23. **Documentation disagrees with code.** The `PRICE` parameter of `PrivateProperty` is described as doing nothing (`arc_properties.ini:1713-1724`), but `getPrice()` returns it (`Prop_PrivateProperty.java:76-83`). Whether callers use it is unverified. The `ROOMS` parameter of `ReqCapacity` is documented but not enforced (see item 6).
24. **Copy-pasted magic blocks.** The same ability-class test and fizzle message appear in six files. `NoTeleport` and `NoTeleportOut` are near duplicates. Any change to the rule has to be made several times.
25. **Multiplayer assumptions throughout.** `PLAYERKILL` (`Prop_ReqPKill`, `Prop_NoPKill`), tell and channel blocking, group-leader instancing (`Prop_PlayerInstance`), clan ownership (`Prop_RoomForSale`, `Prop_PrivateProperty`) and `playerExistsAllHosts` checks all assume other human players.

## Takeaways for a Python SUD

- **Borrow the gate as data.** A gate is a small, immutable rule with a message, checked against the actor. Keep the party walk only if companions exist in the game. Sketch:

  ```python
  @dataclass(frozen=True)
  class Gate:
      min_level: int = 0
      max_level: int = 999
      deny_classes: frozenset = frozenset()
      message: str = "You are not allowed to go that way."

      def refusal(self, player) -> str | None:
          if not (self.min_level <= player.level <= self.max_level):
              return self.message
          if player.cls in self.deny_classes:
              return self.message
          return None
  ```

  Write rules in the data files as tuples or dicts, not as a grammar. The CoffeeMud level and tattoo grammars are fragile (items 7 to 10).

- **Borrow the flag-keyed block.** Tag each action with what it does, for example `{"transport"}`, `{"summon", "magic"}` or `{"charm"}`. A blocker then vetoes by tag, and a single fizzle line serves every magic system. This is the cleanest idea in the bucket.

- **Borrow the veto and effect phases.** Let every thing in the room or inventory veto an action with `ok(event)` first, and run the effects with `run(event)` only after all have agreed. Deferred effects are a list of callbacks that run on success.

- **Borrow the reversible modifier, with explicit undo.** Store the old value when the modifier is applied, and restore it on the opposite event. Do not rely on finalizers (item 4). Compute the value with a restricted expression evaluator, and do not hand-roll substitution (item 2). Sketch:

  ```python
  @dataclass
  class Modifier:
      attr: str
      expr: Callable[[object], float]      # e.g. lambda item: item.weight + 5
      _saved: dict = field(default_factory=dict)

      def apply(self, item):
          self._saved[id(item)] = getattr(item, self.attr)
          setattr(item, self.attr, self.expr(item))

      def undo(self, item):
          setattr(item, self.attr, self._saved.pop(id(item)))
  ```

- **Borrow the daily rent ledger.** A lot or room with a `paid_through_day` value, checked once per in-game day, that charges the owner's purse and releases the property when the charge fails (`Prop_RoomForSale.java:745-871`). It fits an SUD economy without any other player.

- **Borrow object-local verbs.** `Prop_OpenCommand` shows that an object can contribute verb phrases that are tried only after the parser fails. This suits a text parser: each object declares patterns such as `("unlock", "*")`, and the fallback path checks them.

- **Simplify.** Drop the Java plumbing for trigger bitmasks, `Modifiable` wrappers, the `<PREVIOUS>` XML and the gen-stat accessor. Use a set of trigger names. Drop NOFOLLOW, the tattoo grammar, account and clan ownership, `PLAYERKILL`, tell and channel blocks, ordering, and group-leader instancing unless the SUD needs per-character copies of locations.

- **Rethink multiplayer assumptions.** A single-player "owner exists" check is trivial, so the player-name lookups can go. Payroll and rent are fine as in-game economy. The PK, tell and channel controls have no single-player analogue.

## Coverage

- **Read in full:** all 47 files in the bucket, and the base class `Property.java` (173 lines).
- **Read in part:** `TriggeredAffect.java:36-80`; `Prop_HaveZapper.java:265-300` (zapper `executeEvent`); `Libraries/MUDZapper.java:6078-6110` (what the `actual` flag does for class checks); `Libraries/CharCreation.java:5107` (signature only); the `addMeIfNeccessary` signatures in `Prop_SpellAdder.java` and `Prop_HaveEnabler.java` (found by grep); `resources/lists.ini:551-554`; `resources/skills/embellishing.txt` (first 12 lines and the pocket rows); and six help entries in `resources/help/arc_properties.ini` (`OutfitContainer`, `PrivateProperty`, `ReqCapacity`, `ReqLevels`, `ReqTattoo`, `PropSetter`).
- **Skipped:** the part-2 base classes `Prop_HaveSpellCast`, `Prop_HaveResister`, `Prop_HaveAdjuster`, `Prop_LotsForSale`, `Prop_LotForSale` and `Prop_AreaForSale` (only their class declarations were read, by grep), plus the internals of `MaskingLibrary`, the law and bank libraries and `DatabaseEngine`.
- **Verified by transcription (Python, `scratch/b077`):** the level-term parse loop (item 7) and the `${}` substitution loop (item 2). The Java was not executed.
- **Not verified at runtime:** all other behaviour is from reading the code. Items 14, 21 and 22 are flagged as unverified where intent or runtime behaviour is unclear.
