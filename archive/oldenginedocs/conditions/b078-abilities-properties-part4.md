# Properties, part 4 of 4: room views, triggers, boarding and the Property base

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b078` (Abilities/Properties [part 4/4: Prop_RoomRedirect .. Property]) · 42 files · 8711 lines of Java.

## Summary

The 42 classes in this bucket are the second half of CoffeeMud's `Prop_*` family, from `Prop_RoomRedirect` to the `Property` base class. A property is a `ThinAbility` (`Abilities/Properties/Property.java:38`) attached to a room, exit, area, item or mob, and it changes how that object responds: it can veto a game message, rewrite one, emote, grant spells, adjust stats or senses, or run on a tick. Each class is configured by one free-text string (`text()`) parsed in `setMiscText`, so a builder can attach a rule to any object without a new class. The notable systems are message-triggered emotes and spell casts on items (`Prop_UseEmoter`, `Prop_UseSpellCast`, `Prop_Sounder`), a spell reflector and invulnerability wrappers (`Prop_SpellReflecting`, `Prop_SafePet`, `Prop_ShortEffects`, `Prop_WizInvis`), room rewiring (`Prop_RoomRedirect`, `Prop_RoomView`, `Prop_RoomWatch`), boarding and vehicles (`Prop_TicketTaker`, `Prop_StayAboard`, `Prop_Transporter`, `Prop_WeakBridge`), and a clothing-layer rule for worn gear. For a Python SUD the main thing to borrow is the pattern of a small hook object attached to any world object and configured by data; several mechanics (weighted smells with decay, spell reflection with a level-cost budget, weight-triggered collapse, layered garments) port almost directly.

## Key files and types

File references are relative to `com/planet_ink/coffee_mud/`; bare `Prop_*.java` names are in `Abilities/Properties/`. Base classes that live in other buckets (`Prop_Adjuster`, `Prop_HaveAdjuster`, `Prop_HaveEnabler`, `Prop_HaveSpellCast`, `Prop_HaveZapper`, `Prop_HaveResister`, `Prop_RoomForSale`) are named but not described in depth.

| File | Role | Lines |
|---|---|---|
| `Property.java` | Base class: a `ThinAbility` with `ACODE_PROPERTY` and host-type masks via `canAffect` | 173 |
| `Prop_SpellAdder.java` | Casts a list of spells onto the host (item holder, mob, room or area); base of the spell family | 604 |
| `Prop_UseEmoter.java` | Emotes (visual, sound, smell, social, puff) when the owner uses the item | 548 |
| `Prop_WearOverride.java` | "Wearable Unzapper": lifts a wear restriction for a matching wearer; DRESS and CONTENTS parameters | 506 |
| `Prop_Sounder.java` | Reactive emotes on game-message triggers (18 trigger words, each with a `_ROOM` form); parallels `Behaviors/Sounder.java` | 494 |
| `Prop_RoomRedirect.java` | Sends enter, recall and look to another room chosen by zapper mask | 315 |
| `Prop_RoomView.java` | Shows another room's look, as a window or a boat porthole (`OUT`, `NOUT`) | 302 |
| `Prop_TicketTaker.java` | Fee or ticket required to board a rideable | 289 |
| `Prop_WeaponImmunity.java` | Damage immunities by damage type, weapon class, material, magic and spell class | 289 |
| `Prop_UseAdjuster.java` | Applies stat changes to the owner on eat, drink, hit or put (extends `Prop_Adjuster`) | 278 |
| `Prop_Smell.java` | Sniff text, plus periodic weighted room smells that expire with TICKS | 274 |
| `Prop_WeakBridge.java` | Room or exit that collapses under weight, then repairs after `down` ticks | 234 |
| `Prop_WizInvis.java` | Wizard invisibility and undetectability; vetoes hostile acts | 220 |
| `Prop_UseSpellCast.java` | Casts spells when a worn, wielded, drunk or put item is used (extends `Prop_SpellAdder`) | 206 |
| `Prop_Trainer.java` | Builds a training mob: classes, expertises and optionally all qualifying skills (extends `Prop_StatTrainer`) | 203 |
| `Prop_WearEnabler.java` | Grants abilities while worn or wielded; LAYERED clothing rule (extends `Prop_HaveEnabler`) | 198 |
| `Prop_WearSpellCast.java` | Casts spells while worn; lit-light check; LAYERED rule (extends `Prop_HaveSpellCast`) | 190 |
| `Prop_SpellReflecting.java` | Reflects malicious spells back at the caster, charged in spell levels | 186 |
| `Prop_Transporter.java` | Moves whoever triggers it to a fixed room | 181 |
| `Prop_Socials.java` | Defines extra socials from its text; handled on unknown commands | 177 |
| `Prop_ShortEffects.java` | Blocks remote spells and caps the duration of effects from outsiders | 176 |
| `Prop_TattooAdder.java` | Adds or removes tattoos on events (drink, eat, wear, get, death) | 175 |
| `Prop_RoomsForSale.java` | Land title for a cluster of rooms (extends `Prop_RoomForSale`) | 174 |
| `Prop_StatAdjuster.java` | Adds offsets to a mob's base stats, continuously | 165 |
| `Prop_WearAdjuster.java` | Stat adjustments while worn; LAYERED rule and item-set checks (extends `Prop_HaveAdjuster`) | 161 |
| `Prop_SafePet.java` | Unattackable, undying mob; optional liege following | 159 |
| `Prop_StayAboard.java` | Keeps a rider on a moving rideable on each tick | 147 |
| `Prop_RoomWatch.java` | Relays "others see" text from one room into watched rooms (CCTV) | 143 |
| `Prop_WearResister.java` | Resistances while worn; LAYERED rule (extends `Prop_HaveResister`) | 143 |
| `Prop_StatTrainer.java` | Sets a trainer's base stats (default 25); NOTEACH attribute handling | 137 |
| `Prop_Trashcan.java` | Destroys items put into it, immediately or after DELAY ticks | 126 |
| `Prop_Weather.java` | Forces an area's weather and climate mask | 124 |
| `Prop_Unsellable.java` | Refuses SELL of the item or of a container holding it | 120 |
| `Prop_SparringRoom.java` | Death in a sparring room: XP paid out, player reset to a room | 119 |
| `Prop_UseEmoter2.java` | Emote pool on drink, pour, eat and get (extends `Prop_UseEmoter`) | 113 |
| `Prop_WearZapper.java` | Blocks wear, wield or hold for those failing a zapper mask, with a percent chance (extends `Prop_HaveZapper`) | 111 |
| `Prop_Undroppable.java` | Refuses DROP; optional LOSTOFF | 107 |
| `Prop_UseSpellCast2.java` | Casts on drink, pour, eat and get; removes on drop (extends `Prop_UseSpellCast`) | 106 |
| `Prop_Tattoo.java` | Applies tattoos listed in its text to a mob or its account | 97 |
| `Prop_ScrapExplode.java` | Scrapping the item sets off a fire blast on the scrapper and group | 93 |
| `Prop_RoomUnmappable.java` | Hides a room or area from mapping, optionally from exploring | 79 |
| `Prop_Uncampable.java` | Sets the can-not-be-camped sense bit | 69 |

## How it works

### 1. The base class and the host

`Property` extends `ThinAbility` (`Property.java:38`). Its `classificationCode()` is `Ability.ACODE_PROPERTY` (`Property.java:105-109`), and it decides which host types it may sit on by testing the `Ability.CAN_*` bits from `canAffectCode()` against the host's class (`Property.java:146-164`). Each subclass declares its legal hosts this way: for example `Prop_SafePet` allows mobs and also Boardable items (`Prop_SafePet.java:55-64`). The default hooks that subclasses override are in `ThinAbility` (`Abilities/ThinAbility.java:604-631`).

### 2. The four hooks

- `okMessage(host, msg)` runs before anything happens. Returning false vetoes the action. `Prop_WeakBridge` refuses entry while the bridge is down with "The bridge appears to be out." (`Prop_WeakBridge.java:90-95`). `Prop_SafePet` refuses death and hostile acts (`Prop_SafePet.java:121-141`). `Prop_WizInvis` refuses malicious targeting (`Prop_WizInvis.java:183-195`).
- `executeMsg(host, msg)` runs after the message is accepted and does the work. It may queue follow-ups as trailers, which run after the parent message finishes: `addTrailerRunnable` (`Prop_UseEmoter.java:103-171`, `Prop_WearOverride.java:200-210`) and `addTrailerMsg` (`Prop_Sounder.java:481-487`). Trailers are how an item emotes after the wear or drink that triggered it.
- `affectPhyStats`, `affectCharStats` and `affectCharState` run whenever a stat set is rebuilt. They are the continuous part of a property. `Prop_WizInvis` adds disposition and sense bits here (`Prop_WizInvis.java:117-139`).
- `tick(ticking, tickID)` runs on the periodic tick. `Prop_Smell` rolls for emotes here (`Prop_Smell.java:197-272`), `Prop_WeakBridge` uses a tick-down to break and repair (`Prop_WeakBridge.java:163-233`), and `Prop_StayAboard` uses the mob tick (`Prop_StayAboard.java:75-99`).

Re-entrancy is handled with flags. `processing` stops a property reacting to its own emotes or grants (`Prop_SpellAdder.java:65`, `:466-477`; `Prop_UseEmoter.java:56`, `:243-313`). `noRepeat` does the same for `Prop_StayAboard` (`Prop_StayAboard.java:82-96`, `:126-144`), and `lastMsg` stops `Prop_Sounder` handling a message twice (`Prop_Sounder.java:408`, `:466`).

### 3. Configuration strings

`setMiscText` is called when the property is created or edited, and it turns `text()` into fields. This bucket uses five dialects:

- `key=value` parameters, read with `CMParms.getParmInt(text, "name", default)`: `max`, `chance` and `down` for the bridge (`Prop_WeakBridge.java:78-80`); `min`, `max`, `chance`, `fade` and `remain` for the reflector (`Prop_SpellReflecting.java:91-95`).
- Semicolon lists, read with `CMParms.parseSemicolons`: spell lists (`Prop_SpellAdder.java:134`), emote lists (`Prop_Sounder.java:85`), social definitions (`Prop_Socials.java:67`).
- Zapper masks after a `MASK=` token, compiled with `CMLib.masking().getPreCompiledMask` or `maskCompile` and tested with `maskCheck(mask, who, true)` (`Prop_SpellAdder.java:92-106`, `:120`, `:320`; `Prop_Sounder.java:103-107`).
- Edit prefixes. `Prop_Smell` treats a leading `+` as append and `-` as delete (`Prop_Smell.java:69-88`). `Prop_TattooAdder` reads `+`, `-`, `+-` (toggle) and a `~` silent flag (`Prop_TattooAdder.java:97-118`).
- Positional type words. `Prop_UseEmoter` reads a leading run of words such as BROADCAST, PRIVATE, SOUND, SMELL, SOCIAL and PUFF, separated from the emotes by `;` or `/` (`Prop_UseEmoter.java:327-362`, `:392-410`).

Room IDs appear as `Area#n` strings or as `#n`, which are resolved against the host's area (`Prop_RoomRedirect.java:108-113`, `Prop_RoomWatch.java:102-107`).

### 4. Triggers: from game message to effect

Item properties act only when their owner is the source of the message. `Prop_UseEmoter` checks `msg.amISource(owner)` and then switches on `msg.sourceMinor()` (`Prop_UseEmoter.java:276-306`):

| Owner's action (CMMsg minor) | Extra condition | Effect |
|---|---|---|
| FILL | item is a Drink, and is not the tool | emote |
| WEAR | item is Armor and is the target | emote |
| PUT, INSTALL | item is a Container and is the target | emote |
| WIELD, HOLD | item is none of Drink, Armor or Container | emote |
| PUFF (target) | lit Light fitted to the mouth | puff emote, to smellers and seers |
| SNIFF (target) | someone sniffs the item | smell emote |

`Prop_UseEmoter2` uses DRINK, POUR, EAT and GET instead (`Prop_UseEmoter2.java:81-104`). `Prop_UseSpellCast` uses a similar action set but casts spells: FILL, PUFF, WEAR, REMOVE (which removes the effects), PUT and INSTALL, GET from a container (which removes them), and WIELD or HOLD (`Prop_UseSpellCast.java:146-197`). `Prop_UseSpellCast2` uses DRINK, POUR, EAT, GET (add) and DROP (remove) (`Prop_UseSpellCast2.java:69-98`). `Prop_UseAdjuster` uses EAT and DRINK on the item, damage from a wielded weapon (`msg.value()>0`), PUT and INSTALL into a container, and damage to worn armour (`Prop_UseAdjuster.java:190-271`).

Room and area properties match the message's target code. `Prop_Sounder` maps each trigger word to a CMMsg type and matches on `targetMinor()` (`Prop_Sounder.java:110-325` for the parse, `:408-464` for the match). `UNDER_MASK = 1023` keeps the base type and `ROOM_MASK = 32768` marks the room-wide form (`Prop_Sounder.java:65-66`, `:476-490`). `Prop_SpellAdder` on a room or area grants on ENTER and removes on LEAVE or RECALL (`Prop_SpellAdder.java:442-454`).

`Prop_TattooAdder` derives its trigger from the host kind (`Prop_TattooAdder.java:56-89`): Drink gives DRINK, Food gives EAT, MOB gives DEATH, Weapon gives WEAPONATTACK, Armor gives WEAR, other Items give GET, and Room, Area and Exit give ENTER. On a death the killer may gain the tattoo (`Prop_TattooAdder.java:158-166`).

### 5. The spell family (`Prop_SpellAdder` and its children)

`Prop_SpellAdder` (`Prop_SpellAdder.java:35-604`) is the shared engine. Its text is a semicolon list. Each entry is an optional chance (`NN% `), then one spell ID with optional `(parm)`, then an optional `/misc` text. Global tokens are `TICKS=n` (duration for the entries after it), `MAXTICKS=n` (cap), `LEVEL=n` (caster level), `NOUNINVOKE`, `ONCLOSED` (only grant while a container is closed), and a trailing `MASK=`. See `getMySpellsV` (`Prop_SpellAdder.java:128-206`) and `convertToV2` (`:281-312`).

- Chance. `didHappen()` takes the digits before `%` (default 100 when there is no `%`) and rolls `rollPercentage() <= chance` (`Prop_SpellAdder.java:208-234`). It is rolled per spell, not once per entry.
- Application. `convertToV2` skips a spell the target already has (`:288`). `addMeIfNeccessary` invokes each spell with `A.invoke(qualMOB, V2, target, true, asLevel)` (`:341`), records the invoker, then applies duration rules. A shorter `TICKS` or `MAXTICKS` lowers `TICKDOWN`. Otherwise `makeLongLasting` is applied, and `NOUNINVOKE` also makes the effect non-removable and records it in `unrevocableSpells` (`:350-370`).
- Caster. `getInvokerMOB` uses the affected item's owner, then the source, then the target. If none is a MOB it builds a factory MOB named by `L("invoker")`, at the host's level (`:256-279`).
- Lifetime on an item or mob. `affectPhyStats` grants the spells to whichever holder the item is attached to and removes them from the previous holder (`:456-479`). `Prop_WearSpellCast` and `Prop_WearEnabler` add worn-only checks, and `Prop_WearSpellCast` also requires a Light to be lit. Both apply the clothing-layer rule when LAYERED is set (`Prop_WearSpellCast.java:152-189`).
- Used items. `Prop_UseSpellCast` overrides `affectPhyStats` with an empty body (`Prop_UseSpellCast.java:114-117`). A used item therefore casts once per event, and the effect persists until REMOVE or GET removes it.
- Accounting. `accountForYourself` returns text such as "Casts X and Y on the first one who enters.", with any restriction from `maskDesc` appended (`Prop_SpellAdder.java:376-380`, `:481-502`).

### 6. Reflection and protection

`Prop_SpellReflecting` (`Prop_SpellReflecting.java:55-186`) is the most self-contained mechanic in the bucket.

- Trigger. `okMessage` fires on a malicious spell cast (`TYP_CAST_SPELL` with `MASK_MALICIOUS`, tool a spell) aimed at the host, or at the wearer if the host is an item (`:122-139`).
- Gate. The roll is against `chance` (default 100). The caster level comes from `CMLib.ableMapper().qualifyingLevel`, then `lowestQualifyingLevel`, then 1 (`:148-152`). It must lie within `[min, max]`, default 1 to 30 (`:153-154`).
- Effect. The message "The field around <T-NAMESELF> reflects the spell!" is shown (`:156`). The spell is invoked with the wearer as caster and the attacker as target, `A.invoke(target, msg.source(), true, msg.source().phyStats().level())` (`:158`). The budget is reduced by the spell level (`:159`), and the original cast is vetoed (`:181`).
- Budget. The budget is `uses`, initially `remain` (default 100). It is set back to `remain` every time `setMiscText` runs (`:96`). When it reaches zero, a mob loses the effect and an item is destroyed, but only if `fade` is set (`:160-180`).
- Regeneration. While `fade<=0` and the budget is below `remain`, one point is restored per elapsed five-minute block of real time (`:104-120`, `5*60000`).

`Prop_SafePet` (`Prop_SafePet.java:55-158`) makes its mob unattackable (`IS_UNATTACKABLE`, `:80`). Death is refused with "You are safe from death." (`:121-127`). Hostile acts are refused with the `MSG` text, default "You may not attack <T-NAME>." (`:67`, `:129-138`). Damage is zeroed (`:144-145`). The `disabled` flag lets the pet fight back once it has attacked itself (`:140-141`), and it is cleared when the pet is out of combat (`:147-148`). `LIEGEFOLLOW=name` makes the pet follow a named liege into rooms (`:96-114`).

`Prop_ShortEffects` (`Prop_ShortEffects.java:104-174`) blocks spells and skills aimed at the host from another room. Poison, trap and disease abilities are exempt (`:127-129`), and so is anything with `REMOTEOK`. Blocked casts print "magic energy is disspated into the air" (sic) or "You can't seem to do that to X" (`:138-141`). The durations of effects put on the host by outsiders are capped at `maxTicks`, default 3 (`:63`, `:147-171`). The cap is only re-applied when the host's effect count changes (`:155-157`).

`Prop_WizInvis` (`Prop_WizInvis.java:35-220`) defaults `abilityCode` to `IS_NOT_SEEN | IS_CLOAKED` (`:78`). When NOT_SEEN is set it adds invisible, hidden, sneaking, flying, climbing and swimming disposition bits (`:126-134`), and it adds the four CAN_SEE senses (`:135-138`). Hunger and thirst are pinned to maximum (`:145-146`). Hostile acts aimed at the wizard are refused with "Ah, leave X alone." and the attacker is pacified (`:183-195`). A hostile act by the wizard sets `disabled` (`:200`), and nothing ever clears it again.

`Prop_SparringRoom` (`Prop_SparringRoom.java:55-118`) denies flee, deny recall ("No one hears your plea.") and limb damage (`:61-69`). If a player dies in combat, the death message is shown, XP is dispensed to the combat beneficiaries (`:82-85`), effects are removed, followers are released, and the player is moved to the room named in the text or to the start room (`:106-112`). The death is therefore a reset that keeps the XP.

### 7. Stats, senses and gear

- `Prop_StatAdjuster` (`Prop_StatAdjuster.java:91-121`) adds offsets to the base stats every recalculation. The result is clamped to at least 1 and to the maximum stat unless `ADJMAX` is given, in which case the offset goes to the max-stat adjustment instead (`:97-108`). It is continuous (`TRIGGER_ALWAYS`, `:76-79`). `STAT-LEVEL` reads as 0 and cannot be set (`:146-164`).
- `Prop_StatTrainer` (`Prop_StatTrainer.java:90-109`) sets each base stat to a value, rather than adding to it. Unspecified base stats default to 25 (`:56-61`, `BASEVALUE` at `:117`). Stat keys are the first three letters of the stat name (`:124`). It also clears the NOTEACH attribute unless the trainer sets it (`:93-94`).
- `Prop_Trainer` (`Prop_Trainer.java:82-202`) runs its build once, on the first tick (`:85`). Keywords in its text pick classes (`ALL <class>` adds every class with the same base class, `:105-115`), expertises, `skills` and `noexper` (`:100-131`). If `skills` is set, it sets every class level to the mob's level divided by the number of classes (`:157-158`) and grants every ability that qualifies by level and is not secret, at maximum proficiency and not savable (`:161-186`). The theme is fixed to fantasy (`:173`).
- Sense bits, which are read by the sensing library. `Prop_Undroppable` sets `SENSE_ITEMNODROP` (32, `PhyStats.java:485`; `Prop_Undroppable.java:70`). `Prop_Uncampable` sets `CAN_NOT_BE_CAMPED` (1048576, `PhyStats.java:438`; `Prop_Uncampable.java:67`; read at `Libraries/Sense.java:248`). `Prop_RoomUnmappable` sets `SENSE_ROOMUNMAPPABLE` (2048, `PhyStats.java:503`), plus `SENSE_ROOMUNEXPLORABLE` for NOEXPLORE (`Prop_RoomUnmappable.java:55-78`). `Prop_SafePet` sets `IS_UNATTACKABLE` (2097152, `PhyStats.java:595`).
- `Prop_WeaponImmunity` (`Prop_WeaponImmunity.java:79-288`) takes a list of `+` and `-` tokens. The valid words (`:89-100`) are ALL, MAGIC, NONMAGIC, LEVEL, MAGICSKILLS, MAGICSPELLS, the damage types and weapon classes (from `Items/interfaces/Weapon.java:87-180`), material names, and stat names. Only damage messages with a positive value count (`:159-162`), and the host must be the target or the owner of a worn item (`:163-189`). `ALL` sets the default. The first matching rule then decides, in this order: the stat name for stat-typed attacks (`:194-207`), the weapon damage type, the weapon class, MAGIC or NONMAGIC, the material name, the material description (`:209-251`), and finally, for spells, skills and prayers, MAGICSKILLS, MAGIC or MAGICSPELLS (`:253-283`). Immunity is applied as `msg.setValue(0)` (`:284-285`). The LEVEL rule never fires; see section 9.
- `Prop_WearOverride` (`Prop_WearOverride.java:110-215`) is the "unzapper". When a mask-matching wearer puts on the item, it clears the item's location bits from the wearer's wear-restriction bitmap (`locMaskAdj = ~rawProperLocationBitmap`, `:197`; applied at `:114`). Anyone else gets "That won't fit on the likes of you." (`:215`). The DRESS and CONTENTS parameters rewrite command-failure messages so that the item can be dressed or undressed, or items taken from and put into a worn container, by a third party (`:229-502`). This is a broad workaround for the command parser.
- `Prop_UseAdjuster` (`Prop_UseAdjuster.java:55-244`) suppresses its continuous effects (`:61-76`), so it applies only on the event. `multiplyCharStates` treats the amounts as percentages of current HP, mana or movement (`:119-141`). Otherwise they are absolute (`:145-165`). The base class supplies `canApply`, which checks only the zapper mask, not item sets (`Prop_Adjuster.java:68-85`).

### 8. Worn-gear layering (LAYERED)

`Prop_WearEnabler` (`Prop_WearEnabler.java:65-103`), `Prop_WearSpellCast` (`:71-114`), `Prop_WearResister` (`:65-100`) and `Prop_WearAdjuster` (`:72-114`) share one rule. A worn Armor is disabled when another worn Armor overlaps it in a slot (`rawWornCode() & ...`) and that other item is substantial (armour above 0, has effects, or is not paper). It is disabled if `A.getClothingLayer() <= I.getClothingLayer()`. The rule runs only if the text contains `LAYERED` (`WearEnabler.java:109`). Without it, `check()` sets `checked` and returns, so the enabler always works. The result is that outer garments of equal or higher layer switch off what is under them.

### 9. Movement, boarding and rooms

- `Prop_TicketTaker` (`Prop_TicketTaker.java:85-288`) takes either a number (a cost) or a ticket item name (`:73-83`). `okMessage` blocks MOUNT, SIT, ENTER and SLEEP onto a Rideable the host controls (`:238-241`) unless the boarder has the money (`:250`) or the ticket (`:267`). It then says "You'll need X to board." (`:255-277`). `executeMsg` charges by subtracting money (`:194`) or destroys the ticket (`:203-204`), with the message "<S-NAME> give(s) X to <T-NAME>." (`:193`, `:203`). The `paid` set is `LimitedTreeSet(1000, 25, false)` (`:70`). Its first argument is an idle expiry of 1000 ms (`LimitedTreeSet.java:66`, `:162-179`), so a name is remembered for about a second. The set therefore debounces one boarding that produces several messages; it does not record a paid trip (inferred from the constructor and expiry). Each new boarding after a pause is charged again.
- `Prop_StayAboard` (`Prop_StayAboard.java:101-146`) puts the rider back on the rideable whenever it has moved, using `moveItemTo` for items or `bringMobHere` for mobs, then calls `setRiding`. It runs on each mob tick (`TICKID_MOB`, `Tickable.java:82`) and on each stat recalculation of an item rider.
- `Prop_Transporter` (`Prop_Transporter.java:55-180`) derives its trigger from the host (`transCode`, `:90-140`): Drink gives DRINK, Food gives EAT, Rideable gives MOUNT, ENTER, SIT or SLEEP by `rideBasis`, MOB gives SPEAK, Weapon gives WEAPONATTACK, Armor gives WEAR, other Items give GET, and Room, Area or Exit give ENTER. On a match the actor is moved to the room named in the text, default "START" (`:56`, `:166-168`). With SENDENTER an enter message is sent (`:169-170`). A missing room gives "You are whisked nowhere at all, since 'X' is nowhere to be found." (`:164`).
- `Prop_WeakBridge` (`Prop_WeakBridge.java:55-233`) computes weight as the sum of the non-flying occupants plus the entrant (`:101-115`). On ENTER, if the weight exceeds `max` and a roll under `chance` succeeds, the entrant is added to `mobsToKill`, a Falling ability is started (`:137-143`), and a one-tick tick-down is scheduled (`:144`). On the next tick the bridge breaks. Every non-flying occupant of the room is treated as a victim (`:175-186`). In an air room with an open exit below, they fall with Falling at proficiency 0 (`:193-208`). Otherwise they die: "<S-NAME> fall(s) to <S-HIS-HER> death!!" (`:212-214`). The bridge then stays down for `down` ticks (`:221`) and repairs on the next tick (`:224-230`).
- `Prop_RoomRedirect` (`Prop_RoomRedirect.java:44-315`) reads `roomid=zappermask` pairs (`:66-78`). `getRedirects` builds the list in reverse, so later entries in the text come first and win (`:80-127`). `getRedirectRoom` resolves `#n` in the host's area, and `beacon` to the mob's start room, or to the home port of a riding Boardable (`:129-169`). On ENTER or RECALL the target is replaced and the redirect is used (`:189-236`). Player visits are recorded (`:205-208`). LOOK and EXAMINE are redirected, and the looker is moved there (`:215-232`). LOOK_EXITS is redirected, and LIFE messages move mobs (`:239-313`). Sysops are exempt via `MOB.Attrib.SYSOPMSGS`.
- `Prop_RoomView` (`Prop_RoomView.java:35-302`) takes `[LONGLOOK;]target`, where the target is a room ID, a direction, `OUT`, or `<dir>NOUT`. `OUT` resolves to the room where the boardable's area sits (`:104-111`). `NOUT` steps one room in the direction and keeps going through roomless air or underwater rooms until it finds occupants or a boardable (`:113-156`). LOOK and EXAMINE then show the other room's description (`:183-223`). With LONGLOOK, EXAMINE adds "In X you can see:" in a trailer (`:194-209`).
- `Prop_RoomWatch` (`Prop_RoomWatch.java:82-142`) relays the "others" text of any message in the host room to each listed room that does not already have this property (`:123`). An optional `prefix=` is added. Each viewer must be able to see and be seen (`:133-136`). It is a CCTV feed.
- `Prop_RoomsForSale` (`Prop_RoomsForSale.java:52-173`) keeps owner, price, back taxes and rental flag identical across the cluster (`:85-120`). `updateLot` runs a daily rental check when the day of month changes (`:138-173`).

### 10. Environment and objects

- `Prop_Smell` (`Prop_Smell.java:55-273`) stores smells as `text;text`, each with optional TICKS=, CHANCE=, EMOTE and BROADCAST tokens (`:96-140`). A sniff at the host, by a source that can smell, returns a smell chosen by weight (`:175-183`). `selectSmell` sums the weights and draws uniformly across the total (`:142-173`). CHANCE is therefore a relative weight, not a probability: a lone smell with CHANCE=10 is always chosen. On each tick there is a 20% roll (`:200`). An EMOTE smell is then shown in the mob's room (`:185-195`), and a BROADCAST smell is also shown in each open adjacent room as "something <direction>" (`:209-226`). The TICKS countdown advances only on those rolls (`:229-269`), so on average one step takes about five ticks. When a smell expires the text is rewritten (`:247-268`), and when none remain the property removes itself (`:265-266`).
- `Prop_Trashcan` (`Prop_Trashcan.java:55-125`) destroys an item put into it (`TYP_PUT`) or dropped in its room (`:112-124`). With DELAY=0 it destroys at once (`:96-97`). With a delay it queues the item and schedules a `TICKID_PROPERTY_SPECIAL` tick (`TICKID_PROPERTY_SPECIAL = 21`, `Tickable.java:124`) for `DELAY` ticks (`:99-106`). Items still owned by or in the trash are destroyed on that tick (`:70-77`).
- `Prop_Weather` (`Prop_Weather.java:55-122`) sets the area's current and next weather on every stat recalculation, from the names in `Climate.WEATHER_DESCS` (`Common/interfaces/Climate.java:193-196`). Climate names set bits in a mask (`:71-84`).
- `Prop_Socials` (`Prop_Socials.java:61-175`) turns each definition in its text into a social line and loads them into the social table (`:129-146`). It answers an unknown command (`TYP_HUH`) when the name matches (`:149-175`), and it can be limited with `WORNONLY` and `MASK`.
- `Prop_Tattoo` (`Prop_Tattoo.java:55-96`) applies tattoos from its text to the mob, or to the account if the entry starts with `account `. Each entry may have a leading number. It sets `savable=false` (`:95`), so the tattoos live on the mob or account, not on the property.
- `Prop_TattooAdder` (`Prop_TattooAdder.java:91-153`) adds or removes a tattoo on each trigger, with the message "<T-NAME> gives <S-NAME> the X tattoo." or "takes away" (`:140`, `:149`).
- `Prop_ScrapExplode` (`Prop_ScrapExplode.java:65-92`) reacts when the item is scrapped (tool ID `Scrapping`). Damage is `3 * weight`. The scrapper takes twice that, and each group member takes it once, all as fire-piercing damage (`:77-87`). The item is then destroyed (`:90`).
- `Prop_Unsellable` (`Prop_Unsellable.java:55-119`) refuses SELL when the tool is the item, or a container that ultimately holds it (`:86-102`). The default message is "You can't sell that." (`:58`). `SEXCEPTMASK` lets a matching shopkeeper through. `DROPOFF` removes the property when the item is dropped on its target (`:104-114`).
- `Prop_Undroppable` (`Prop_Undroppable.java:55-106`) refuses DROP with `MESSAGE`, default "You don't want to drop that." (`:57`, `:81-90`). `LOSTOFF` removes the property when the owner changes (`:93-102`).

### 11. Timing

| Mechanic | Unit | Default and source |
|---|---|---|
| Base tick | 4000 ms | `TIME_TICK = 4000` (`core/CMProps.java:681`), settable by `TICKTIME` (`:2720-2722`) |
| Bridge outage | ticks | `down`, default 300 (`Prop_WeakBridge.java:80`), so 1200 s at the default tick |
| Reflector regeneration | real time | one point per about 5 min (`Prop_SpellReflecting.java:109`, `:111`) |
| Smell TICKS countdown | 20% of ticks | `Prop_Smell.java:200`, `:229-269` |
| Trashcan DELAY | ticks | `Prop_Trashcan.java:68`, `:104` |
| Short-effect cap | ticks | `maxTicks`, default 3 (`Prop_ShortEffects.java:63`) |
| Weather period | ticks | `Climate.WEATHER_TICK_DOWN = 150` (`Climate.java:191`, comment "10 minutes") |
| Ticket debounce | real time | 1000 ms expiry (`Prop_TicketTaker.java:70`, `LimitedTreeSet.java:66`) |

### 12. Messages shown to players

| Message (short form) | Where |
|---|---|
| "The bridge appears to be out." | `Prop_WeakBridge.java:94` |
| "<S-NAME> fall(s) to <S-HIS-HER> death!!" | `Prop_WeakBridge.java:213` |
| "You are safe from death." | `Prop_SafePet.java:125` |
| "You may not attack <T-NAME>." (default) | `Prop_SafePet.java:67` |
| "No one hears your plea." | `Prop_SparringRoom.java:67` |
| "The field around <T-NAMESELF> reflects the spell!" | `Prop_SpellReflecting.java:156` |
| "Ah, leave @x1 alone." | `Prop_WizInvis.java:190` |
| "You begin to fade back into view." | `Prop_WizInvis.java:172` |
| "You'll need @x1 to board." | `Prop_TicketTaker.java:255`, `:272` |
| "You are whisked nowhere at all, since '@x1' is nowhere to be found." | `Prop_Transporter.java:164` |
| "That won't fit on the likes of you." | `Prop_WearOverride.java:215` |
| "Scrapping @x1 causes an explosion which <DAMAGE> <T-NAME>!!!" | `Prop_ScrapExplode.java:79` |
| "In @x1 you can see:" | `Prop_RoomView.java:202` |
| Emote wrapper `^E<S-NAME> ... ^?` (colour codes) | `Prop_UseEmoter.java:497` |

## Content worth mining

- **Weather and climate vocabulary.** `Climate.WEATHER_DESCS` lists 14 weather types: CLEAR, CLOUDY, WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT, COLD, FOG (`Common/interfaces/Climate.java:193-196`). `Places.CLIMATE_DESCS` lists NORMAL, WET, COLD, WINDY, HOT, DRY, VOID (`core/interfaces/Places.java:109`), and `Prop_Weather` maps each to a bit, with NORMAL as zero (`Prop_Weather.java:71-84`).
- **Damage types and weapon classes.** `Weapon.TYPE_DESCS` lists 17 damage types: NATURAL, SLASHING, PIERCING, BASHING, BURNING, BURSTING, SHOOTING, FROSTING, GASSING, MELTING, STRIKING, LASERING, SONICING, DISRUPTING, STULTIFYING, CORRUPTING, SCRAPING (`Items/interfaces/Weapon.java:87-106`). `Weapon.CLASS_DESCS` lists 12 classes: AXE, BLUNT, EDGED, FLAILED, HAMMER, KARATE, POLEARM, RANGED, SWORD, DAGGER, STAFF, THROWN (`:166-180`). Together they give a mixed fantasy and science-fiction damage taxonomy, which `Prop_WeaponImmunity` filters on.
- **Smell and emote grammar.** Smell lines take TICKS=n, CHANCE=n, EMOTE and BROADCAST (`Prop_Smell.java:107-135`). Emote lines take a leading type word (VISUAL or SIGHT, SMELL or AROMA, SOUND or NOISE, SOCIAL, PUFF) and optional BROADCAST, PRIVATE or MASK (`Prop_UseEmoter.java:327-362`). Both are compact enough for builder-written content.
- **Sounder trigger words.** Eighteen trigger words, GET, EAT, PUSH, PULL, SIT, DROP, WEAR, OPEN, CLOSE, HOLD, WIELD, DRINK, MOUNT, REMOVE, PORTAL_ENTER, PORTAL_EXIT, DAMAGE and FIGHT, each with a `_ROOM` form (36 in all), which gives a per-event emote vocabulary (`Prop_Sounder.java:110-325`). Tokens `$n`, `$e`, `$s` and `$p` substitute the actor, pronoun and host name (`:96-101`, `:355-356`).
- **Socials as data.** `Prop_Socials` accepts NAME, TARGET, SRCCODE, OTHCODE, YOUSEE, OTHSEE, TARGSEE, NOTARGSEE, MSPFILE, ZAPMASK, FLAGSTR, MASK, WORNONLY and LOAD (`Prop_Socials.java:72-103`). Default source and other codes are M and V (`:95-96`); their meaning belongs to the social parser and was not verified here. Socials are a ready-made command set attached to a mob or item.
- **Stat trainer defaults.** Base stats default to 25 (`Prop_StatTrainer.java:56-61`, `:117`), with 3-letter keys (`:124`). This is the easiest source for "trainer NPC" balance.
- **Teleport and vanish lines.** "You are whisked nowhere at all..." (`Prop_Transporter.java:164`) and the "fade back into view" line (`Prop_WizInvis.java:172`) are the kind of short flavour text that can be reused.
- **Tattoo syntax.** Tattoos use `[N ]name`, with `+`, `-` or `+-` prefixes, a `~` silent flag and an `account ` scope (`Prop_TattooAdder.java:97-127`, `Prop_Tattoo.java:76-93`).

## Notable design ideas

- **One text string, many rules.** Almost every class here is a mini-language driven by `text()`. Those strings are small enough to live in data files, and one class serves dozens of items.
- **Events, not timers, for most behaviour.** Item emotes and spell casts fire on the owner's action (wear, drink, put, sniff). Only smells and bridges use timers. The event model keeps the rule and its trigger together.
- **Re-invoking a spell from the defender.** `Prop_SpellReflecting` casts the attacking spell again with the defender as caster (`:158`). The defender gets the spell's effect, and the attacker is the target.
- **A spell budget in spell levels.** Reflection costs the spell's level (`:159`) and regenerates with time. Stronger spells drain the reflector faster, a simple and tunable rule.
- **Weighted smells that fade.** Smells use weights, EMOTE and BROADCAST flags and a countdown that is paid for in ticks (`Prop_Smell.java:96-269`). Sensory detail that decays is a useful pattern for a text game.
- **Weight-triggered collapse.** `Prop_WeakBridge` turns a room's total weight into a hazard that is rolled, delayed, then repaired (`:101-233`). It is a compact environmental puzzle.
- **Porthole views.** `Prop_RoomView` lets a boat or castle show the room it is anchored in, or a room a step away through open air (`:104-156`). That is a neat way to show a second location without moving the player.
- **Layered garments with one integer.** Clothing layers reduce to comparing two numbers and an overlap mask (`WearEnabler.java:93`).
- **Zapper masks for restrictions.** The same mask language gates rooms, items, emotes, spells and redirects, so a single parser serves every restriction.

## Problems and cruft

Checked by reading. Items marked "unverified" were not run.

1. **LEVEL immunity can never be stored.** For `LEVEL15`, the token is split so that `s` becomes `"15"`, and only that value is checked against the valid-flag set (`Prop_WeaponImmunity.java:116-131`). Numbers are never valid, so the `LEVEL` branch in `okMessage` (`:232-249`) is unreachable. The help text example "+ALL -LEVEL15" (`resources/help/arc_properties.ini:2802`) therefore does not work as documented.
2. **`ATTACK` in RoomView does nothing.** The `ATTACK` token sets `longlook` instead of `attack` (`Prop_RoomView.java:78-79`), and `attack` is never set true anywhere in the file (`:57`, `:68`). The attack path (`:226-298`) is dead code, although the help text promises it (`resources/help/arc_properties.ini:2215-2216`).
3. **UseEmoter sends smell emotes into the visual pool.** In `parseEmotes`, a SMELL emote is added to `smells` and then, because the `else` binds to the PUFF test, also to `emotes` (`Prop_UseEmoter.java:431-444`). A smell line can therefore be used as a visual emote.
4. **Undroppable LOSTOFF can throw.** When `owner` is null and the item's owner is not a MOB, the code falls through to `I.owner() != owner.get()`, which dereferences null (`Prop_Undroppable.java:94-101`). Unverified at runtime.
5. **Sounder's once-per-round guard is dead.** `oncePerRound1` is declared (`Prop_Sounder.java:68`) and tested (`:455`), but never set.
6. **SpellAdder hand-off to a new holder.** When the holder changes, `affectPhyStats` removes the spells from `lastMOB` but does not clear `lastMOB` (`Prop_SpellAdder.java:466-472`). The `lastMOB == null` test that would grant to the new holder is then false. Subclasses that call `removeMyAffectsFromLastMOB` (`:385`) avoid this. Unverified at runtime.
7. **SpellAdder duplicate fallback.** Lines 267-269 set `R` from `roomLocation(target)` twice, so the second test is a no-op (`Prop_SpellAdder.java:267-269`). The `invoker` path also dereferences `affected` without a null check (`:274-275`). Unverified.
8. **Ability enumeration is random.** `abilities()` filters through `didHappen()` (`Prop_SpellAdder.java:566-573`), which rolls dice on every call, so two listings can differ.
9. **Reflector budget resets.** `setMiscText` sets `uses` back to `remain` on every call (`Prop_SpellReflecting.java:96`), so any text rewrite refills the charge.
10. **WizInvis's hostility flag never resets.** `disabled` is set at `Prop_WizInvis.java:200` and read at `:186`. No path clears it, so one hostile act removes protection for the life of the effect.
11. **Name inverted in ShortEffects.** `getBadInvokers` returns the set of *trusted* invokers (owner and group members), and they are exempt (`Prop_ShortEffects.java:104-114`, `:135`, `:158`, `:164`). `wearer` is set but never read (`:67`, `:92`). The message text has a typo, "disspated" (`:139`).
12. **TicketTaker's paid set is a one-second debounce.** Charging depends on a 1000 ms expiry (`Prop_TicketTaker.java:70`, `:181-183`).13. **WeakBridge inconsistencies.** The field default for `ticksDown` is 100 (`Prop_WeakBridge.java:58`), but the parameter default is 300 (`:80`). The comment in `affectPhyStats` says it removes flying restrictions, yet it sets `IS_SLEEPING` (`:154-160`). Every non-flying occupant is a victim, not only the heavy entrant (`:175-186`). The exit and room-fall behaviour matches the help text (`resources/help/arc_properties.ini:2785-2794`).
14. **Duplicate-mask handling is partial.** Only empty masks are checked for duplicates (`Prop_RoomRedirect.java:93-101`). A duplicate is removed from `rawRedirects` but still added to `redirects` in the same pass (`:103-123`). The size-based cache check (`:82`) then forces a rebuild.
15. **RoomRedirect passes the wrong host.** `executeMsg` calls `super.executeMsg(this, msg)` rather than `myHost` (`Prop_RoomRedirect.java:241`).
16. **RoomView's DOWNINOUT example is doubtful.** Stripping `NOUT` from `DOWNINOUT` leaves `DOWNI` (`Prop_RoomView.java:84-88`), and the direction lookup is a prefix match (`core/Directions.java:680-692`). Unverified whether `DOWNI` resolves.
17. **Repeated and duplicated code.** The layering check is copied into four classes (`Prop_WearEnabler.java:65-103`, `Prop_WearSpellCast.java:71-114`, `Prop_WearResister.java:65-100`, `Prop_WearAdjuster.java:72-114`). The "remove from last holder" method is spelled two ways: `removeMyAffectsFromLastMOB` (`Prop_SpellAdder.java:382`, `Prop_WearEnabler.java:101`) and `removeMyAffectsFromLastMob` (`Prop_HaveEnabler.java:181`, `Prop_WearEnabler.java:152`, `:179`).
18. **WearOverride repeats recalculations.** `recoverCharStats()` is called twice in a row at `:198-199`, `:206-207` and `:224-225`.
19. **Dead and misnamed fields.** `pxceptMask` is never assigned, so its branch never runs (`Prop_Unsellable.java:53`, `:95-96`). `StatAdjuster`'s `all25` is all zeros (`Prop_StatAdjuster.java:55-60`), while `StatTrainer`'s `all25` is all 25s (`Prop_StatTrainer.java:56-61`).
20. **Unstable RoomsForSale fallback.** With no rooms the cluster ID falls back to `"ROOMS_PROPERTY_"+this` (`Prop_RoomsForSale.java:93`), which uses an object identity string that changes between runs.
21. **Fantasy-only trainer.** `Prop_Trainer` fixes the theme to `Area.THEME_FANTASY` (`Prop_Trainer.java:173`).
22. **Smell edit-prefix collision.** `tick` rewrites smell text and passes it back through `setMiscText` (`Prop_Smell.java:249-268`). A smell whose text starts with `+` or `-` is then read as an edit (`:69-88`).
23. **Brittle LOAD parsing.** `Prop_Socials` takes the file name with `substring(4)`, then `substring(1)` to drop the `=` (`Prop_Socials.java:77`).
24. **Player-only assumptions.** Many properties key on player names or groups: the ticket debounce (`Prop_TicketTaker.java:181`), the owner and group exemption (`Prop_ShortEffects.java:104-114`), liege following (`Prop_SafePet.java:96-114`), the sysop bypass (`Prop_RoomRedirect.java:200`), and XP sharing in the sparring room (`Prop_SparringRoom.java:82-85`).

## Takeaways for a Python SUD

1. **Make the hook object explicit.** The four hooks (`okMessage`, `executeMsg`, `affectPhyStats`, `tick`) are the core of the design. A Python version can be one small base class attached to any world object:

   ```python
   class Trait:
       def before(self, host, action) -> bool:   # okMessage: False vetoes
           return True
       def after(self, host, action) -> None:    # executeMsg: apply the effect
           pass
       def modify(self, host, stats) -> None:    # affectPhyStats: recomputed
           pass
       def tick(self, host) -> None:             # per turn or per second
           pass
   ```

   For a turn-based SUD, `tick` is just the turn loop. In a real-time SUD it is a clock. Durations are already counted in ticks, so they port as turns.

2. **Borrow weighted smells with decay.** Smells are a list of records with a weight and a remaining count, and sniffing draws by weight (`Prop_Smell.java:142-173`):

   ```python
   import random
   def pick_smell(smells, emote_only=False):
       pool = [s for s in smells if s.weight > 0 and (s.emote or not emote_only)]
       return random.choices(pool, weights=[s.weight for s in pool])[0].text if pool else ""
   ```

3. **Borrow the reflector budget, with a fix.** Cost in spell levels, regeneration with time, and the defender casts the spell back (`Prop_SpellReflecting.java:99-181`). Use a game clock for regeneration rather than wall time, and avoid the reset-on-edit problem (item 9 above).

4. **Keep weapon immunity as an ordered rule list, and test it.** The rule is "the first matching key decides", with `ALL` as the default (`Prop_WeaponImmunity.java:192-251`):

   ```python
   def immune(rules, hit_keys):          # hit_keys: stat, damage type, class, magic, material...
       verdict = rules.get("ALL") == "+"
       for key in hit_keys:
           if key in rules:
               verdict = rules[key] == "+"
               break
       return verdict
   ```

   Parse numeric levels as values, not as flag names, and add a test for `LEVEL`, since the original fails silently (item 1).

5. **Layered garments as an integer.** Give each garment a `layer` and a slot set. A garment is covered when another worn substantial garment shares a slot and has an equal or higher layer (`Prop_WearEnabler.java:93`). The LAYERED option becomes a per-item flag, not a special case.

6. **Room rewiring as data.** Model `RoomRedirect` as a list of `(predicate, destination)` pairs on the room, `RoomView` as a `view_of` pointer that can name the neighbour in a direction, and `RoomWatch` as a list of rooms to mirror events into. Each then needs no subclass.

7. **Drop the Java machinery.** Clone and `copyOf`, `finalize`, `synchronized` blocks and the `processing` flags are not needed in a single-threaded Python loop. An explicit action queue, drained once per turn, gives the same ordering without guards.

8. **Single-player simplifications.** Ownership, group and sysop exemptions collapse to "the player" versus "the world". Keep the sparring reset as a "safe room" effect, but drop XP sharing. The ticket debounce, liege following and remote-cast blocking mostly disappear. A single actor also makes `Prop_SafePet`'s "attacks back once" rule a simple state.

9. **Borrow the vocabulary, not the mechanism.** The weather names, damage types, weapon classes and trigger words in the content section are a ready list for a Python game. The TICKS and CHANCE syntax is worth copying only if builders edit text, otherwise use dictionaries.

10. **Things not to copy.** The LEVEL immunity parse, the ATTACK flag, the SMELL double-add and the Undroppable null dereference (items 1, 2, 3 and 4 above) are bugs to avoid.

## Coverage

- **Read in full (42 of 42):** every file listed in the manifest `b078.tsv`, including `Property.java`.
- **Read in part:** `core/collections/LimitedTreeSet.java` (constructor, `add`, `check`, `contains`); `Abilities/Properties/Prop_HaveAdjuster.java` (`canApply`, lines 722-745); `Abilities/Properties/Prop_Adjuster.java` (`canApply`, lines 60-85); `resources/help/arc_properties.ini` (entries for about ten properties); `Abilities/ThinAbility.java` (class header and method list).
- **Grepped only:** `Behaviors/Sounder.java` (call site of `doEmote`); `Abilities/Properties/Prop_HaveEnabler.java`, `Prop_HaveSpellCast.java`, `Prop_HaveZapper.java`, `Prop_HaveResister.java`, `Prop_RoomForSale.java` (declarations and the method names used); `core/CMClass.java` (`getMsg` overloads); `Common/interfaces/CMMsg.java`, `PhyStats.java`, `Climate.java`, `core/interfaces/Places.java`, `core/interfaces/Tickable.java`, `core/CMProps.java`, `core/Directions.java`, `Items/interfaces/Weapon.java`, `Libraries/Sense.java`; `guides/ArchonGuide.html` (mentions only).
- **Skipped:** the other `Prop_*` classes in the family (other buckets), `Prop_HaveAdjuster`'s set-bonus internals, and any runtime behaviour. Every bug in the problems list is from reading, not from running the code.
