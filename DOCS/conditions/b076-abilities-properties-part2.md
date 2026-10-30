# Properties, part 2: grants, restrictions, slots, movement, language and traps

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b076` (Abilities/Properties [part 2/4: Prop_HaveEnabler .. Prop_NarrowLedge]) · 33 files · 8494 lines of Java.

Citation convention: paths are relative to the CoffeeMUD folder. `P/` stands for `com/planet_ink/coffee_mud/Abilities/Properties/`. Other paths are written in full. Anything I could not confirm by reading is marked "unverified". Nothing here was run.

## Summary

Part 2 of the Property family is 33 components, from `Prop_HaveEnabler` to `Prop_NarrowLedge`, that hang on items, mobs, rooms, exits and areas and change what happens to whoever owns, holds or enters them. They grant abilities or cast spells to owners and occupants, restrict who may own, get, wear, enter or use something, add socket slots, languages, transporters and traps, and modify stealth, damage and rewards. Each class is a `Property` subclass (`P/Property.java:38`) that vetoes or rewrites messages, reacts after them, and adjusts stats through the `affect*` hooks; each keeps its settings as a text string parsed in `setMiscText`. For a single-player Python game the reusable ideas are grants derived from ownership and never saved, stealth as a counter that fades after quiet turns, item effects that flow to whoever holds the item, and rules attached to objects as data. The multiplayer registries, name-based binding, world-wide scans and wall-clock timers should not be copied.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `P/Property.java` (base, outside bucket) | Common base for all properties; ACODE_PROPERTY; `canAffect` by mask | 173 |
| `P/Prop_SpellAdder.java` (base, outside bucket) | Spell/ability list grammar; grant and cast machinery; `lastMOB` tracking | 604 |
| `P/Prop_HaveEnabler.java` | Grants listed abilities to the owner of an item; revokes them when ownership ends | 323 |
| `P/Prop_HereEnabler.java` | Grants listed abilities to anyone who enters a room or area, via a per-visitor tracker effect | 152 |
| `P/Prop_HaveSpellCast.java` | Keeps the listed spells active on the owner of an item | 214 |
| `P/Prop_HereSpellCast.java` | Keeps the listed spells active on everyone in a room | 139 |
| `P/Prop_HaveResister.java` | Owner's resistances: weapon damage reduction, refusal of magic, poison and disease, saving-throw bonuses | 564 |
| `P/Prop_HaveZapper.java` | Ownership restriction on GET, EAT and DRINK by mask; optional accusation; alignment disposition | 485 |
| `P/Prop_ItemBinder.java` | Binds an item to a character, account, group, clan or NPC on pickup or first equip | 313 |
| `P/Prop_LimitedEquip.java` | Caps how many identical items may be carried (GET) or worn | 211 |
| `P/Prop_LimitedItems.java` | World-wide "only N may exist" for an item name or key | 338 |
| `P/Prop_LimitedContents.java` | Caps what a container, room or area may hold, per mask | 197 |
| `P/Prop_ItemSlot.java` | Socket host: named slots that accept filler items | 593 |
| `P/Prop_ItemSlotFiller.java` | Gem-like filler: occupies N slots and adds abilities to the host | 478 |
| `P/Prop_MoveRestrictor.java` | Refuses entry into rooms by domain, locale, keyword, mob or item; optional cast on refusal | 292 |
| `P/Prop_LocationBound.java` | Tethers an item or mob to a room or area; "whisked back home" | 367 |
| `P/Prop_NarrowLedge.java` | Ledge room or exit: entering rolls against DEX and can drop the mover | 172 |
| `P/Prop_ItemTransporter.java` | Moves loose items from its host to a random receiver with a matching code | 337 |
| `P/Prop_ItemTransReceiver.java` | Marker: a valid destination for a transporter with the same code | 60 |
| `P/Prop_LangTranslator.java` | Language understanding and translation; passive mode turns trusted speech into commands | 541 |
| `P/Prop_LanguageSpeaker.java` | Forces a mob, item holder, visitor or exit user to speak a language | 218 |
| `P/Prop_Hidden.java` | Hiddenness that fades after 30 ticks without a noisy or moving act | 158 |
| `P/Prop_Invisibility.java` | Invisibility that fades after 60 ticks without a malicious act | 95 |
| `P/Prop_MagicFreedom.java` | Anti-magic field: magic messages in the room are absorbed | 93 |
| `P/Prop_ItemNoRuin.java` | Sense flag that exempts an item from the loot-ruin policy | 84 |
| `P/Prop_IceBox.java` | Stops decay of contents; clears a "Soiled" effect after food leaves | 139 |
| `P/Prop_ModFaction.java` | Rewrites faction-change amounts with a small formula | 204 |
| `P/Prop_ModExperience.java` | Rewrites experience gained, using the leveler's XP modifiers | 108 |
| `P/Prop_ImproveGather.java` | Multiplies gathering-skill yields | 105 |
| `P/Prop_HereAdjuster.java` | Room-only stat adjuster (its base `Prop_HaveAdjuster` is in another bucket) | 78 |
| `P/Prop_InstantDeath.java` | Trap: activated by a message, then kills or damages everyone in scope on a tick | 315 |
| `P/Prop_MOBEmoter.java` | Wraps a Behavior (default Emoter) on a mob; can expire | 116 |
| `P/Prop_MultiEffects.java` | Bundles several properties in one, from a text grammar or XML | 447 |
| `P/Prop_LotsForSale.java` | Land: an owned room grows adjacent for-sale rooms; unowned ones retract | 479 |
| `P/Prop_LotForSale.java` | Variant: buy one room, adjacent rooms priced at 1 | 79 |

## How it works

### 0. Shared machinery

- **Hooks.** A property overrides some of `okMessage` (veto, may rewrite `msg.value()`), `executeMsg` (after the fact), `affectPhyStats`, `affectCharStats`, `affectCharState`, `tick` and `setMiscText`. A holder rebuilds its stats from base values plus every effect (`MOBS/StdMOB.java:826-829`). Examples of value rewriting: `P/Prop_HaveResister.java:403-414`, `P/Prop_ModExperience.java:103`, `P/Prop_ModFaction.java:200`, `P/Prop_ImproveGather.java:101`.
- **Bubble effects.** `bubbleAffect()` true means an item forwards the effect to whatever holds it (`Items/Basic/StdItem.java:665`, `:686`, `:707`). Default is false (`Abilities/StdAbility.java:190-193`). Bubble-true classes here: `P/Prop_ItemSlot.java:53-57`, `P/Prop_HaveResister.java:55-59`, `P/Prop_HereSpellCast.java:55-59`, `P/Prop_Hidden.java:98-101` (only with BUBBLES), and `P/Prop_MultiEffects.java:406-409` (true if any child is bubble, and then all children apply).
- **Trigger masks and item level.** Most enablers, zappers, resisters and casters declare `TRIGGER_GET`; the room variants declare `TRIGGER_ENTER` (`Abilities/interfaces/TriggeredAffect.java:42-48`). `Libraries/TimsLibrary.java:379-409` (`getTimsAdjResCast`) chooses which properties count toward an item's level by flag and trigger mask. `timsLevelCalculator` (`Libraries/TimsLibrary.java:152`) then adds each one's `STAT-LEVEL` (`:208-209`). Every class with a `STAT-LEVEL` branch is therefore an item-power weight.
- **Text settings.** Each class parses its own grammar from `text()`: `NAME=value` parameters through `CMParms`, `;` between sections, `%` for percentages, and a trailing `MASK=` or `; mask` for a zapper mask (`P/Prop_SpellAdder.java:92-106`).
- **Zapper masks.** One mask language is shared by most classes (`Libraries/MUDZapper.java`, about 10,200 lines; keys in `Libraries/interfaces/MaskingLibrary.java:341-420`). In the description code, `_DEITY` renders as "Requires worshipping one of the following deities" and `DEITY` as "Disallows the worshippers of" (`Libraries/MUDZapper.java:2622-2636`). So the underscored key is the requirement and the plain key the exclusion.
- **Time.** Counters advance in `tick()`. The default tick is 4000 ms (`core/CMProps.java:681`). Several classes also use wall-clock time or fixed delays, noted below.

### 1. Grants: enablers and casters

**1.1 `Prop_HaveEnabler` (owner grant).**
- *Data.* `spellV` is a list of ability copies parsed from the text (`P/Prop_SpellAdder.java:128-206`). The proficiency is the digits before the first `%` anywhere in the text, default 100 (`P/Prop_HaveEnabler.java:94-109`). `lastMOB` is the current owner and `lastMOBeffects` records effects the enabler added, so they can be removed later.
- *Trigger.* Not a message. `affectPhyStats` runs when the item's stats are recomputed. If the owner is a MOB standing in a location, it grants (`P/Prop_HaveEnabler.java:195-229`, grant at `:218-222`). If the owner changed or the item was destroyed, it revokes from `lastMOB` (`:213-216`). `executeMsg` is empty (`:190-192`).
- *Grant.* For each listed ability the owner does not already have (`:113`): set parameters from the text after `/` (`:118-122`), set proficiency (`:125`), `addAbility` (`:126`), mark it non-savable (`:127`), call `autoInvocation` (`:128`), and record the ID if the owner had no matching effect (`:134-136`).
- *Revoke.* `removeMyAffectsFrom` deletes the owner's abilities that are non-savable (`:153-157`). If the owner is `lastMOB` and something was removed, it unInvokes the effects recorded in `lastMOBeffects` (`:160-177`).
- *Why non-savable matters.* Grants are never written to the player file (`:127`). They are rebuilt from ownership, so the item is the source of truth.
- *Level.* `STAT-LEVEL` is 3 times the lowest qualifying level of each granted ability (`:236-248`).
- *Docs.* `resources/help/arc_properties.ini:840-858` says the default proficiency is 100%, which matches the code.

**1.2 `Prop_HereEnabler` (room or area grant).**
- Extends the enabler. Targets rooms and areas (`:50-53`), triggers on ENTER (`:55-59`).
- `affectPhyStats` is overridden to clear `processing` only (`:148-151`), so grants come only from movement messages.
- On ENTER or LEAVE (`:75`), if the mover has no effect with this ID (`:78`), it grants the list (`:82`) and attaches an anonymous subclass as a tracker effect (`:83-136`). The tracker revokes when the mover leaves the enabling room, leaves the enabling area, or is outside the metro map (`:90-107`), via its `unInvoke` (`:125-134`).
- The tracker shares `spellV` and `lastMOBeffects` with the enabler (`:138-141`), so all visitors get the same ability objects (see Problems).

**1.3 `Prop_HaveSpellCast` and `Prop_HereSpellCast` (casters).**
- `Prop_HaveSpellCast` extends the SpellAdder base. It triggers on GET (`:57-61`) and has flag CASTER (`:63-67`). For an item with an owning MOB in a location, `affectPhyStats` casts the spells on the owner as long-lasting effects (`addMeIfNeccessary(..., true, ...)`, `:109-112`) and removes them when ownership ends (`:104-107`).
- Spell-list grammar (`P/Prop_SpellAdder.java:128-206`): `;`-separated entries; `ID(params)`; `LEVEL=n` sets the casting level (`:150-155`); `MAXTICKS=n` caps duration (`:156-161`); `TICKS=n` sets duration for the entries that follow (`:162-166`); `NOUNINVOKE` makes the effects non-dispellable (`:140-144`, `:363-369`); `ONCLOSED` suppresses casting while a container is open (`:145-149`, `:322-325`).
- *Chance.* `didHappen()` takes the first `%` anywhere in the text as a chance (`:208-234`). It is rolled once per entry in `convertToV2` (`:289`). So a "50%" prefix applies to every entry, not only the one after it.
- `Prop_HereSpellCast` casts on everyone in the room. `process(mob, room, code)` (`:83-105`): code 1 adds on ENTER or RECALL-in (`:115`), code 2 removes on LEAVE or RECALL-out (`:120`), code 0 rechecks inside `affectPhyStats` (`:124-138`). A cached occupant count (`lastNum`, `:61`, `:85-97`) detects change cheaply.
- `Prop_HaveSpellCast` `STAT-LEVEL` is plus or minus 1 times the lowest qualifying level, negative for malicious spells (`:126-141`).

### 2. Ownership restrictions

**2.1 Resistances: `Prop_HaveResister` (564 lines).**
- *Data.* `prots` is a sorted map from resistance key to percent (`:70`). Parsing (`:85-142`): a percent applies to all unmatched words before it (`:123-128`); a number with absolute value at most 1 is a fraction (times 100), above 1 is a percent (`:110-115`); `ALWAYS` removes the weapon limits (`:117-118`); `DEBUF-DURATION` caps the remaining ticks of malicious debuffs (`:131`, `:255-280`).
- *Saving throws.* While owned (`canResist`, `:392-400`), the owner's saving throws gain the item's values (`affectCharStats`, `:151-162`).
- *Weapon hits.* `resistAffect` runs in `okMessage` on damage messages from a `Weapon` with value above 0 (`:200-246`, called at `:411`). Keys: `NON-MAGICAL-WEAPONS` (not checked when the weapon is a bonus item), `NON-SILVER-WEAPONS` (checks material), `WEAPONS` (all weapons), or `BLUNT`, `PIERCE`, `SLASH` by damage type when `WEAPONS` is absent (`:213-243`).
- *Formula* (`weaponProtection`, `:177-198`):
  - `protection = pct + (defender level - attacker level)`, recomputed at most once per tick (`:184-189`, using `CMProps.getTickMillis()`);
  - if `protection <= 0`, the damage is unchanged; if `protection >= 100`, it is 0; otherwise `round(damage * (1 - protection / 100))` (`:190-197`);
  - `remainingProtection = protection - 100` is kept for later calls in the same tick (`:192`).
  - `ALWAYS` (`:179-183`) gives `round(damage * (1 - pct / 100))` with no level term and no carry-over.
- *Magic and other messages.* `isOk` runs only when `value <= 0` (`:409`), so it sees non-damage messages (`:282-379`). For a malicious ability (MASK_MAGIC and MASK_MALICIOUS):
  - transporting abilities are refused under `TELEPORT` with "You can't seem to fixate on '<name>'." (`:290-298`);
  - prayers flagged HOLY or UNHOLY are refused under the matching key (`:304-327`), with messages such as "Holy energies from <S-NAME> are repelled from <T-NAME>.";
  - DISEASE and POISON abilities are refused always when set (`:328-346`);
  - otherwise the ability type, then the domain, then the ability ID each get a roll, and the ability is refused when `rollPercentage() <= pct` (`:347-374`).
- *Level weights* (`STAT-LEVEL`, `:424-500`): see the table in Content worth mining.
- *Docs.* `resources/help/arc_properties.ini:859-905` lists the same keys. Its phrase "weapons get reduced % damage per round, lvl based, with limits" matches `:184-197`.

**2.2 Zapper: `Prop_HaveZapper` (485 lines).**
- *Trigger.* GET, EAT and DRINK (`:303-321`). HOLD, WEAR and WIELD are accepted without a check (`:297-301`).
- *Refusal.* If the mover fails `maskCheck(mask, mob, actual)` and `rollPercentage() <= percent` (percent defaults to 100, `:61`, `:145`), the room sees the zap message (default "<O-NAME> flashes and flies out of <S-HIS-HER> hands!", `:105`) and the action is vetoed (`:309-310`, `:318-319`).
- *EVENT.* `EVENT=ACCUSE,<crime key>` makes `executeEvent` return true when the law library accuses the mover (`:265-280`). The veto is then skipped, so the mover keeps the item and is accused. The default `ZAP` cancels only.
- *ACTUAL* changes the mask check to use actual values (`:128-137`, `:304`). *CONTENT* sets `contents` (`:125`, `:130`, `:142`), but nothing reads it (see Problems).
- *Alignment and deity.* FACTION terms in the mask set the disposition bit `IS_EVIL` or `IS_GOOD` and the ambiance `#LAW` or `#CHAOS` (setup `:204-236`, applied in `affectPhyStats` `:253-263`). The deity named in the mask is returned as the worship ID (`:72-77`).
- *Level* (`:330-411`). Each mask term subtracts from the item level: -5 for PLAYER and NPC, -9 for race, class, tattoo and faction terms, and a count-based amount for alignment and gender. Each term is clamped to -9 (`:402-403`). A positive total becomes -1 (`:408-409`).

**2.3 Binding: `Prop_ItemBinder` (313 lines).**
- *Config* (`:83-109`): `TO` (CHARACTER, ACCOUNT, GROUP, CLAN, PLAYER, NPC, NOONE), `ON` (PICKUP or EQUIP), `BOUND`, `DEATHKEEP`, `MESSAGE`.
- *Binding.* On EQUIP, the first HOLD, WEAR, WIELD, EAT or DRINK binds the item (`executeMsg` `:280-291`). On PICKUP, the first GET by a non-shopkeeper that is a player or a following NPC binds it (`:293-306`). The name comes from `getBindyName` (`:224-256`): the mob's name, its account, the names of its group members at that moment, or its rivalrous clan name. The binding is appended to the item text as `BOUND="..."` (`:290`, `:302-304`), so it is saved with the item.
- *Enforcement.* GET, EAT and DRINK by a source that fails the check get the zap message and are vetoed (`:146-212`). Selling a bound item is refused: "<item> is bound to <name>, and can not be sold." (`:214-219`). WEAR, HOLD and WIELD are not blocked (`:150-154`).
- *Death.* `DEATHKEEP` sets `SENSE_ITEMDEATHKEEPER` on an item held by a MOB (`:261-270`), for EQUIP only when worn properly.
- *Flags.* `FLAG_ZAPPER | FLAG_NODISENCHANT` (`:119`), so zapper-aware code treats the binder as a zapper.

**2.4 Quantity limits.**
- `Prop_LimitedEquip` (211 lines). With `GET=true`, it caps identical items carried (`okMessage` `:144-174`). Otherwise it caps identical items worn properly (`:176-208`). "Identical" means the same class ID and the same key, which is the `ID=` parameter or the item name (`getId` `:120-137`). The count excludes the item being taken or worn. Messages: "You may not get any more of those." and "You may not wear any more of those." (`:166-171`, `:200-205`). Default max is 1 (`:58`, `:90`).
- `Prop_LimitedContents` (197 lines). Rules are `N [mask] [MSG=...] [ACTUAL=...]`, separated by `;` (`:76-106`). `itemCount` counts raw-material weight, package count, or 1 (`:108-116`). The first rule whose mask matches the new item applies (`:163-194`). It vetoes if the new item alone exceeds N (`:169-176`) or existing matched items plus the new one exceed N (`:177-191`). See Problems for the bug at `:181`.
- `Prop_LimitedItems` (338 lines). A world-wide cap: "Only N may exist" (`accountForYourself` `:67-78`). Live instances are counted in the static map `instances` (`:56`, `:182-225`), and a surplus item is destroyed (`:213-218`). Counting runs on ENTER, LOOK and GET (`:231-238`) and in `affectPhyStats` for owned items (`:327-329`). The first time a key is seen, every saved player file is scanned for matching items (`:264-326`). Offline players are loaded briefly and the code only logs (`:297-298` calls this a known problem). `SENSE_UNLOCATABLE` is set (`:261`). `copyOf` schedules a re-attach to the original item after 5000 ms (`:125-158`).

### 3. Item slots: `Prop_ItemSlot` and `Prop_ItemSlotFiller`

- *Host* (`P/Prop_ItemSlot.java`, 593 lines). Fields: `slots[]` (the item in each slot), `slotProps[]` (the filler's ability per filler), `slotType`, `removable`, `levelShift`, `levelDiff` (`:59-67`). Config (`:129-192`): `NUM` (default 1, `:142`), `TYPE`, `REMOVABLE` (also `REMOVEABLE`, default true, `:156`), `LEVEL=NONE|ADD|HIGH` (`:148-155`), and an XML list of starting items after `;` (`:158-190`).
- *Filler* (`P/Prop_ItemSlotFiller.java`, 478 lines). Fields: `slotCount`, `slotType`, `skips`, `adds` (`:61-69`). `ADDS` is a list of `ID(args)` abilities added to the host; `SKIPS` lists IDs not applied (`:200-266`). The filler is an `AbilityContainer`, so `fetchAbility` and friends read `adds` (`:380-434`).
- *Insert.* On PUT, `okMessage` (`P/Prop_ItemSlot.java:242-282`) requires a filler item, a matching TYPE, and enough empty slots, with messages such as "<O-NAME> will not fit in <T-NAME>." (`:252-277`). It then rewrites the target code to `TYP_WAND_USE` (`:281`). `executeMsg` (`:454-476`) fills the first empty slots, removes the item from its container and clears its owner (`:467-468`). The filler knows it is slotted because `owner()` is null (`P/Prop_ItemSlotFiller.java:268-279`).
- *Effects.* The host's `affectPhyStats` applies the level shift (`ADD` adds the slotted levels, `HIGH` takes the maximum; `P/Prop_ItemSlot.java:551-562`) and passes the call to each filler's abilities (`:564-568`). A filler applies a non-bubble ability to an item host and a bubble ability to a MOB host (`P/Prop_ItemSlotFiller.java:318-345`). Physical stats therefore land on the socketed item and character stats land on the wearer. The help text says the same (`resources/help/arc_properties.ini:1104-1125`).
- *Remove.* A failed command text beginning with R (for example "REMOVE x FROM y") is parsed when the item is `removable` (`P/Prop_ItemSlot.java:283-289`, REMOVE branch `:377-438`). The filler returns to the inventory (`:416`), where `isSlotted()` is false, so its effects stop.
- *Look.* Examining the socket lists "Slot N: <name or Empty>" (`:501-518`).

### 4. Movement and place

- **`Prop_MoveRestrictor`** (292 lines). On ENTER into a room by the holder or its mount (`okMessage` `:212-216`), entry is refused when any of these holds (`:219-232`): the room domain is in `NODOMAINS` or outside `ONLYDOMAINS` (`:101-127`); `SEARCH` matches the room's name or description; `MSEARCH` names an inhabitant; `ISEARCH` (see Problems); the locale is in `NOLOCALES` or outside `ONLYLOCALES` (`:131-146`). Refusal then happens with probability `rollPercentage() < CHANCE` (`:234`). The mover is told (or the room is, with PUBLIC, `:236-239`), DISMOUNT unseats riders (`:240-252`), and `CAST` invokes its abilities 500 ms later on the mover or each rider (`:253-285`, delay at `:284`). Default message: "<S-NAME> can`t go that way." (`:52`).
- **`Prop_LocationBound`** (367 lines). A tether. Tokens are resolved lazily once the host is RUNNING (`:70-132`, check `:74`):
  - `ROOM` or `AREA` set the current room or area as the anchor;
  - a room ID or area name sets a specific anchor (`:98-106`);
  - `ABSOLUTE` fixes the current location at first use (`:109-128`);
  - `PLAYEROK` exempts the item when a player rides it, a following MOB follows a player leader, a boardable's area holds a player, or a player owns the item (`:145-197`);
  - `TIMEOUT=s` allows one passage, then blocks for `s` seconds (`:92`, `:198-211`).
  - Refusals: "You are not allowed to leave this place." for a MOB, and "<item> prevents you from taking it that way." for an item (`:243-283`). For a specific room or area, a MOB is sent home with "You are whisked back home!" (`:296-298`, `:336-337`). An item is moved home while its owner's move proceeds (`:305-308`, `:345-348`).
  - If no token resolves, `type` stays null and every exit is blocked (`:243-252`).
  - Transport magic aimed at the bound thing fizzles: "Magic energy fizzles and is absorbed by <name>." (`:221-231`).
- **`Prop_NarrowLedge`** (172 lines). On ENTER into a ledge room or through a ledge exit (`executeMsg` `:141-143`), a mover who is not flying and not already falling rolls `roll(1, check, -DEX) > 0` (`:146-147`). `check` defaults to 16 (`:55`, `:71`). `Libraries/Dice.java:398-406` returns the dice total plus the modifier, so a fall needs a die roll above DEX; a tie survives. A fall invokes the Falling ability (`:154-157`) and starts a tick (`:158`). The tick (`:105-130`) moves the mover to a configured room with messages, or kills with "<S-NAME> fall(s) off <name> to <S-HIS-HER> death!!" and MSG_DEATH unless the mover is immortal (`:127-129`). In an air room with an open down exit, the tick prints a message and does not move the mover (`:96-102`). The Falling ability probably does the moving (unverified). The help says "fails to roll beneath their Dexterity" (`resources/help/arc_properties.ini:1471-1482`), which is close but not identical.
- **`Prop_ItemTransporter`** (337 lines) and **`Prop_ItemTransReceiver`** (60 lines). A transporter with code C moves loose items from its host (room, mob or container item) to a random holder of a receiver with the same code C (`:219-313`). Receivers are rooms, mobs or items with `Prop_ItemTransReceiver` whose text equals C (`:103-133`). The candidate list is cached per code for 10 calls (`:82-92`), and it is rebuilt by scanning every room, item and mob when empty (`:93-141`). The destination is `roll(1, n, -1)` (`:144`). Transfer runs on the transporter's MOB tick (`:316-321`) and after GET, GIVE, PUT, PUSH, PULL, DROP or THROW (`:324-335`). With no candidate, "The transporter has no possible ItemTransReceiver with the code '<C>'." vetoes the action (`:187-215`, message `:212`). `ultimateParent` walks container chains and breaks two-item loops (`:67-78`).

### 5. Languages

- **`Prop_LangTranslator`** (541 lines). Text grammar (`:94-140`): a bare number sets the percentage for the languages after it (default 100, `:101`); `'word` or a backtick word adds a keyword for the next language; `#name` trusts a speaker and `#*` trusts everyone; `notranslate` sets passive mode; any other token is a language ability ID.
- *Keyword matching* `wordMatch` (`:160-200`): an empty list or `*` matches every sentence. Patterns are `word` (whole word), `word*` (prefix), `*word` (suffix), `*word*` (contains) and `^word` (first word).
- *Hearing path.* `StdLanguage.getAnyTranslator` (`Abilities/Languages/StdLanguage.java:403-412`) checks the listener's effects, then the room's, then carried items, and keeps the translator with the highest proficiency for the language (`getMyTranslator`, `:385-398`). The hook is at `:587-589`. Eligibility is `translatesLanguage` (`P/Prop_LangTranslator.java:203-217`).
- *Active translation* (not passive). When a language ability is spoken near the holder, the translator adds a noise line such as "<holder> say(s) '<speaker> said "..." in <language>'" (`executeMsg` `:322-354`). A roll above the language's proficiency fails the translation (`:307-315`). Smoke signals get their own line (`:316-320`).
- *Passive mode* (NOTRANSLATE). Speech from a trusted name (or `*`) that matches the keywords is parsed with `CMLib.lang().preCommandParser` and queued as commands for the holder (`:268-303`). A pet or golem could obey trusted speakers this way.
- **`Prop_LanguageSpeaker`** (218 lines). Config: `LANGUAGE=`, `PLAYERS`, `NOMOBS`, `HOMEONLY`, plus a zapper mask after `;` (`:68-81`). It acts on a MOB, on each mob that enters or is born in an area, room or exit, and on item holders until they drop the item (`:157-217`). For area, room and exit targets, a per-mob copy of the language ability is made (`:130-145`) and attached as non-uninvokable (`:148-150`). `addNonUninvokableEffect` dedupes by ID (`MOBS/StdMOB.java:5241-5242`).

### 6. Stealth and fields

- **`Prop_Hidden`** (158 lines). `ticksSinceLoss` starts at 100 (`:59`), so a fresh property makes its holder hidden at once. It increases on each `tick()` up to 999999 (`:125-130`). A MOB's action resets it to 0 (`:70-95`) when the action is not look or examine, is not ALWAYS-masked, has a major source code above 0, and is either silent or is speech, enter, leave or recall (`:80-88`). The holder is IS_HIDDEN while the counter is above 30 (`:139-140`) and gets CAN_SEE_HIDDEN always (`:138`). Its detection save gains +100 (`:118-122`). Rooms, exits and areas are always hidden; items are hidden on a room floor unless they are an open container with a door (`:142-156`). UNLOCATABLE and BUBBLES are parsed for non-MOB hosts (`:104-115`).
- **`Prop_Invisibility`** (95 lines). Same scheme with a 60-tick threshold. The counter starts at 100 (`:55`) and resets on a malicious act by the holder (`:64-77`). IS_INVISIBLE when above 60 (`:92-93`), CAN_SEE_INVISIBLE always (`:91`). The help says "Targets: MOB" (`resources/help/arc_properties.ini:1054-1062`), but `canAffect` also allows items and exits (`:50-53`).
- **`Prop_MagicFreedom`** (93 lines). Any message with MASK_MAGIC in source, target or other major masks is vetoed, and the room is told "Magic energy fizzles and is absorbed into the air." (`:68-90`, text `:88`).
- **`Prop_ItemNoRuin`** (84 lines). Sets `SENSE_ITEMNORUIN`, or `SENSE_ALWAYSRUIN` with the NEVERMIND setting (`:70-83`). The loot policy that reads these flags is outside this bucket (unverified).
- **`Prop_IceBox`** (139 lines). On the first message, startup or room reset, it sets decay to `Long.MAX_VALUE` for everything inside (`:57-76`). Items put in or installed are frozen (`:109-114`). A GET of a decaying item from the box resets its decay so it recalculates (`:117-127`). After food leaves, a trailer removes a "Soiled" effect (`:78-98`).

### 7. Reward and stat modifiers

- **`Prop_ModFaction`** (204 lines). On TYP_FACTIONCHANGE (`:168-181`), the amount is replaced by a formula from `faction: op ; mask` (`:100-163`). The operators are `=`, `+`, `-`, `*` and `/` with a number, a bare number, or `(expr)`. A percent becomes `@x1 * (N / 100)` (`:92-97`). The faction ID travels in `msg.othersMessage()` (`:180`). `REACTION` applies to any faction with reactions (`:133-137`, `:183-188`).
- `+` and `-` with a percent work: `@x1 + @x1 * (N / 100)` and `@x1 - @x1 * (N / 100)` (`:143-147`). `*` and `/` with a percent do not (see Problems).
- **`Prop_ModExperience`** (108 lines). Parses XP modifiers with `CMLib.leveler().parseXPMods` (`:76`) and applies `handleXPMods` to experience messages for the holder, its room, its area, or a wearer (`:84-105`). The XP mechanics are in the leveler library (not read).
- **`Prop_ImproveGather`** (105 lines). For ITEMSGENERATED messages from a gathering skill (`:86-88`), the yield becomes `value + value * (AMT - 1)` (`:101`). AMT defaults to 2 (`:58`, `:70`). It applies to the holder, its room, its area, or an item's wearer, and only when the holder has that skill (`:91-99`). `SKILLS=` and `MASK` narrow it.
- **`Prop_HereAdjuster`** (78 lines). A room-only adjuster. `canApply` requires the mob to be in that room (`:68-77`). The base `Prop_HaveAdjuster` is outside this bucket.

### 8. Traps, emoters and bundles

- **`Prop_InstantDeath`** (315 lines). Activation by message (`executeMsg` `:266-314`): a malicious action by a non-monster against a MOB (`:274-276`); EAT or DRINK of food or drink (`:279-284`); WEAR, HOLD or WIELD of armour or weapons (`:287-294`); GET, PUSH or PULL of other items (`:296-300`); ENTER into a room or area (`:302-303`). Activation sets `killTrigger` and starts a 500 ms tick (`:306-311`; `Libraries/interfaces/ThreadEngine.java:65`).
  - *Tick* (`:201-263`). Each MOB in scope takes `dmgAmount = damageAmount + round(damagePercent * maxHP)` (`:227`). If that is at least its current HP, it dies (`postDeath`, `:228-232`). Otherwise it takes `postDamage` (`:236`) and the trap re-arms for 4000 ms (`:249-262`, the 4000 ms delay at `:256`).
  - *Scope* (`getDeadMOBsFrom`, `:157-199`). For a MOB host, everyone in the room except the holder (`:162-164`). For an item, its owning MOB (`:176-177`) or the room's occupants (`:179-180`). Immortals and mask failures are skipped (`:105-106`).
  - *Defaults.* `dmgpct` defaults to 100, so the default kills (`:84`). Messages: the default is "<S-NAME> <DAMAGES> <T-NAME>!" (`:216`).
- **`Prop_MOBEmoter`** (116 lines). Wraps a Behavior on a mob. The first word may name a behaviour, otherwise the Emoter is used with the whole text (`:54-68`). It forwards messages (`:80-95`) and ticks (`:98-115`). If the behaviour stops and its `expires` parameter is set, the property removes itself (`:107-110`).
- **`Prop_MultiEffects`** (447 lines). Bundles child properties. User format `Prop_A(params),Prop_B` is read by a state machine (`:102-275`). XML format `<PROP ID="...">params</PROP>` is read at `:81-100`. Flags are ORed and `bubbleAffect` is true if any child is (`:277-298`, `:370-373`, `:406-409`). Messages and stat calls go to every child (`:376-445`). Parse bugs are in Problems.

### 9. Land

- **`Prop_LotsForSale`** (479 lines), a subclass of `Prop_RoomForSale` (not read). An owned room may grow (`canGenerateAdjacentRooms` is true with an owner name, `:194-197`). `updateLot` (`:435-478`) runs the lot update, then `expandRooms` for owned rooms or `retractRooms` for unowned ones.
  - *Expansion* (`:297-432`). Loops over directions except UP, DOWN and GATE (`:306-311`). For a missing neighbour, while under the room limit (300 by default, `:301`, or the lot's `Prop_ReqCapacity.roomLimit`, `:318-331`), it creates a room, copies the title with no owner (`:368-373`), links both ways with open exits (`:376-381`), and writes the room after the update (`:387-409`). A grid layout uses `buildGridList` (`:336-359`).
  - *Retraction* (`:206-295`). An unowned for-sale room is removed when its links would keep the lot connected. The test is exactly one such link, `numKillableLinks` (1, or 2 for grid), or no doors (`:208`, `:257-259`). It is protected by a link to an owned room, a different area, or a room that does not link back (`:219-255`). Removal is deferred (`:273-291`).
  - *Lot ID* comes from the first room in the cluster (`:80-115`, `:93-95`).
- **`Prop_LotForSale`** (79 lines). Sets the title price to 1 (`:56-62`) and clusters rooms by owner (`:65-68`). Expansion is allowed only when the room's display text lacks the indoor, outdoor, sale and rent markers (`:71-78`). The marker strings come from the unread base class.

## Content worth mining

**Resistance keys** (`Prop_HaveResister`, from the code and `resources/help/arc_properties.ini:859-905`):

| Group | Keys | Behaviour |
|---|---|---|
| Weapons | `WEAPONS`, `BLUNT`, `PIERCE`, `SLASH`, `NON-MAGICAL-WEAPONS`, `NON-SILVER-WEAPONS` | Per-hit reduction, level-scaled, carried within a tick (`:177-197`) |
| Refusals | `TELEPORT`, `HOLY`, `UNHOLY`, `DISEASE`, `POISON` | Refused when set; the message names the source (`:290-346`) |
| Ability classes | spell, chant, song, prayer, thief_skill | Refused on a percent roll (`:347-374`) |
| Domains | enchantment, charm, kicking, vexing, conjuration, and others | Refused on a percent roll (`:359-365`) |
| Saving throws | gas, fire, electricity, mind, magic, cold, acid, water, undead, justice, paralysis, poison, disease, spells, prayers, songs, chants, traps, blunt, pierce, slash, general, crit chances | Added to the owner's saves (`:133-162`) |
| Misc | `DEBUF-DURATION=pct`, `ALWAYS` | Caps malicious debuff ticks; removes weapon limits (`:117-118`, `:131`, `:255-280`) |

**Item-level weights** (`STAT-LEVEL`, summed by `Libraries/TimsLibrary.java:208-209`):

| Property | Weight |
|---|---|
| HaveResister: NON-MAGICAL-WEAPONS, NON-SILVER-WEAPONS | 5 per percent point (`:424-428`) |
| HaveResister: WEAPONS | 10 per point (`:429-430`) |
| HaveResister: BLUNT, PIERCE, SLASH | 3 per point (`:431-436`) |
| HaveResister: HOLY, UNHOLY, DISEASE, POISON, TELEPORT | 2 per point (`:437-446`) |
| HaveResister: ability class | 5 per point (`:447-449`); domain 1 per point (`:450-452`) |
| HaveResister: saves, by kind | 1 for gas, fire, electricity, mind, cold, acid, undead, justice, water, paralysis, magic-crit; 2 for poison and disease; 5 for spells, prayers, songs, chants, traps, blunt, pierce, slash, magic; 30 for general (`:454-491`) |
| HaveZapper: each mask term | -5 for PLAYER or NPC; -9 for race, class, tattoo, faction (`:349-398`); clamped at -9 per term |
| HaveEnabler: granted ability | 3 times the lowest qualifying level (`:236-248`) |
| HaveSpellCast: each spell | plus or minus 1 times the lowest qualifying level, minus if malicious (`:126-141`) |

**Zapper examples** (`resources/help/arc_properties.ini:906-924`): `-class +mage -race +elf -evil`; `MESSAGE="<O-NAME> flies off <S-NAME>" -class +mage`; `-class +mage "event=accuse,trespassing"`. The event key refers to a crime defined in the law data.

**Room domains for movement** (`resources/help/arc_properties.ini:1421-1431`):
- Indoor: STONE, WOODEN, CAVE, MAGIC, METAL.
- Outdoor: CITY, WOODS, ROCKY, PLAINS, UNDERWATER, AIR, WATERSURFACE, JUNGLE, SWAMP, DESERT, HILLS, MOUNTAINS, SPACEPORT, SEAPORT.

**Slot example** (`resources/help/arc_properties.ini:1086-1103`): `NUM=4 TYPE=Gems REMOVABLE=TRUE`. Slot types are free text that must match between socket and filler, so "Gems" is a convention, not a fixed list.

**Keyword patterns for translators** (`P/Prop_LangTranslator.java:172-196`): `word`, `word*`, `*word`, `*word*`, `^word`. Example from the help: `NOTRANSLATE 75% `smile `beer Dwarven #BOB` (`resources/help/arc_properties.ini:1148-1170`).

**Thresholds and timings:**
- Hidden: 30 ticks (`P/Prop_Hidden.java:139`). Invisibility: 60 ticks (`P/Prop_Invisibility.java:92`).
- Default tick: 4000 ms (`core/CMProps.java:681`).
- InstantDeath: 500 ms before the first check, then 4000 ms between checks (`ThreadEngine.java:65`; `P/Prop_InstantDeath.java:256`, `:310`).
- MoveRestrictor cast delay: 500 ms (`P/Prop_MoveRestrictor.java:284`).
- LimitedItems re-attach: 5000 ms (`P/Prop_LimitedItems.java:158`).
- Transporter candidate cache: 10 calls (`P/Prop_ItemTransporter.java:88`).
- Ledge check default: 16 (`P/Prop_NarrowLedge.java:55`).

**Messages** (short, quoted or paraphrased):
- "Magic energy fizzles and is absorbed into the air." (`P/Prop_MagicFreedom.java:88`)
- "You are whisked back home!" (`P/Prop_LocationBound.java:296`) and "You are not allowed to leave this place." (`:248`)
- "You can't seem to fixate on '<name>'." (`P/Prop_HaveResister.java:295`)
- "Only N may exist." (`P/Prop_LimitedItems.java:77`), with "You may not get any more of those." (`P/Prop_LimitedEquip.java:168`)
- "<item> is bound to <name>, and can not be sold." (`P/Prop_ItemBinder.java:218`)
- "<S-NAME> fall(s) off <name> to <S-HIS-HER> death!!" (`P/Prop_NarrowLedge.java:127`)
- "The transporter has no possible ItemTransReceiver with the code '<C>'." (`P/Prop_ItemTransporter.java:212`)

**Help examples worth borrowing:** `magic 50% poison -10%` for a resistance set (`resources/help/arc_properties.ini:859-905`); `damage=10 dmgpct=0 msg="<S-NAME> <DAMAGES> <T-NAME>"` for a flat-damage trap (`:1040-1063`).

## Notable design ideas

- **Grants derived from ownership.** Enablers mark what they add as non-savable (`P/Prop_HaveEnabler.java:127`) and recompute from the item on every change. Nothing has to be undone from a save file, and the item is the only source of truth.
- **One stat pipeline, many carriers.** Items forward bubble effects to holders (`Items/Basic/StdItem.java:665`), rooms and mounts do the same, and a property does not need to know who its host is. The item-versus-wearer split for socket fillers (`P/Prop_ItemSlotFiller.java:318-345`) is a clean rule: physical stats stay on the item, character stats go to the wearer.
- **Stealth as a fading counter.** Hidden and Invisibility keep a counter that resets on specific acts and makes the holder hidden after a threshold (`P/Prop_Hidden.java:70-140`, `P/Prop_Invisibility.java:64-93`). Hidden resets on any silent action and on movement and speech; Invisibility resets only on malicious acts. Both start hidden.
- **Per-round protection budget.** Weapon protection is worked out once per tick, and any excess carries forward (`P/Prop_HaveResister.java:184-197`). Level differences shift the protection. This is a neat way to model damage mitigation without per-hit bookkeeping.
- **Tethers.** `LocationBound` keeps an item or mob near an anchor and sends it home on a bad move (`P/Prop_LocationBound.java:296-348`). A cursed sword that returns to its shrine is a natural game use.
- **Partial understanding.** A translator that understands some keywords per sentence, with a proficiency roll (`P/Prop_LangTranslator.java:160-200`, `:307-315`), fits a text game well. Passive mode (trusted speech becomes commands) is a companion mechanic.
- **Transporters by code.** Receivers are tagged with a code and items go to a random receiver with the same code (`P/Prop_ItemTransporter.java:103-144`). This is a simple network of drop points.
- **Power budget.** Each effect reports an item-level weight (`STAT-LEVEL`), summed by `TimsLibrary.timsLevelCalculator` (`Libraries/TimsLibrary.java:152-209`). Loot and display can share one number.
- **One restriction language.** The zapper mask is reused by enablers, binders, resisters, limits and spell casters, so the same rule text works everywhere.

## Problems and cruft

Items marked "reading" were confirmed by reading the code. Items marked "unverified" were not run and are inferred.

1. **Limited contents counts the wrong items.** `P/Prop_LimitedContents.java:181` tests `item` (the new item, already matched at `:165`) instead of `I`. So `existingCount` adds every object in the container, not just those the rule covers. (reading)
2. **Item restriction checks mobs.** `P/Prop_MoveRestrictor.java:228` calls `R.fetchInhabitant(restrictItems)`. ISEARCH is documented for items (`resources/help/arc_properties.ini:1421-1457`), but items are never checked. (reading)
3. **Locale null check tests the wrong variable.** `P/Prop_MoveRestrictor.java:144-145` checks `s` (never null) and then uses `R.ID()`. An unknown locale would throw if `getLocalePrototype` returns null for it (unverified).
4. **Area InstantDeath never kills.** `P/Prop_InstantDeath.java:189-198` fills `allMobs` and then returns an empty set at `:198`. The help says areas are in scope (`resources/help/arc_properties.ini:1040-1063`). (reading)
5. **HOMEONLY never matches.** `P/Prop_LanguageSpeaker.java:63-64` declares `homeRoom` and `homeArea` as final nulls, and `:113-114` compare the start room to them. So HOMEONLY passes only mobs with no start room or area. The help says "starting room is in the area" (`resources/help/arc_properties.ini:1171-1194`). (reading)
6. **Binding modes are parsed wrongly.** `P/Prop_ItemBinder.java:99` runs the ON prefix loop over the TO string, so abbreviated ON values such as `ON=EQ` silently become PICKUP. The TO prefix loop (`:87-91`) is last-match-wins, so `TO=N` gives NOONE rather than NPC. (reading)
7. **Wrong name in a message.** `P/Prop_HaveResister.java:371` prints the domain name where the ability name is meant. (reading)
8. **Weapon protections do not stack.** A later protection check in the same tick reuses `remainingProtection`, which is usually at most zero, so it does nothing (`:184-197`, called in order from `:213-243`). The help implies each listed type is applied. (reading of the logic; unverified in play)
9. **Flying comment, wrong flag.** `P/Prop_NarrowLedge.java:167-171` says it disables flying restrictions, but it sets `PhyStats.IS_SLEEPING` (`Common/interfaces/PhyStats.java:571`). The intent is unclear. (reading)
10. **CONTENT flag is dead.** `P/Prop_HaveZapper.java:125`, `:130` and `:142` set `contents`, and nothing reads it. The help says CONTENT applies the zapper to a container's contents too (`resources/help/arc_properties.ini:906-924`). (reading)
11. **Zapper ignores wearing.** `P/Prop_HaveZapper.java:297-301` has empty cases for HOLD, WEAR and WIELD. This may be intended, but the help does not say so. (reading)
12. **Percent multiply and divide are wrong in ModFaction.** `P/Prop_ModFaction.java:149-153` builds `"@x1 * " + translateNumber(...)`, and `translateNumber` (`:92-97`) already contains `@x1`. So `*10%` becomes `@x1 * @x1 * (10 / 100)`, which squares the amount, and `/10%` becomes `@x1 / @x1 * (10 / 100)`, which is 0.1 whatever the amount. `=10%` (`:140-141`) multiplies rather than sets. `translateAmount` (`:83-90`) is never called. (reading)
13. **Socket look text can name the wrong gem.** `P/Prop_ItemSlot.java:219-233` indexes `slotProps` by slot number, but a NUM greater than 1 filler takes one `slotProps` entry and several `slots` entries (`:168-177`, `:462-476`). Stat effects use `slotProps` directly (`:564-568`), so only the look text is affected. (reading)
14. **Dead PUT branch in ItemSlot.** `P/Prop_ItemSlot.java:292-375` cannot run. The outer test (`:288`) needs the first character to be R or r, and the inner test (`:293`) needs the first word to be a prefix of "PUT". The code comment says the block is kept for that reason. (reading)
15. **Shared dummy item.** `P/Prop_ItemSlotFiller.java:66` and `:143-147` use one static `StdItem` as the "affected" object for abilities whenever no item host is set. Mutable shared state. (reading)
16. **Unescaped ADDS text.** `P/Prop_ItemSlotFiller.java:180-198` writes ability text into `ADDS` without escaping, while the parser (`:236-240`) ends at the first `)`. Ability text containing `)` is cut on reload. SKIPS is escaped (`:189`). (reading)
17. **Shared ability objects across grantees.** Grants use the cached `spellV` objects (`P/Prop_HaveEnabler.java:112`, `:126`; `P/Prop_HereEnabler.java:138`), and `StdMOB.addAbility` calls `to.setInvoker(this)` (`MOBS/StdMOB.java:4983`). With several visitors, one ability object is re-pointed at each. (reading; unverified in play)
18. **Transporter rescans on every call with no receiver.** `P/Prop_ItemTransporter.java:93` rebuilds the candidate list whenever it is empty, and the rebuild scans every room, item and mob (`:100-135`). So each action with no receiver costs a full world scan. (reading)
19. **Transporter destinations can go stale.** Only the rebuild branch clears `roomDestination` and `mobDestination` (`:93-97`). A later pick of one kind leaves the other set, so `tryToMoveStuff` can place items in both a room and a mob (`:276-302`). This needs an okMessage with no following move. (reading; unverified in play)
20. **Container loops.** `P/Prop_ItemTransporter.java:67-78` breaks only one- and two-item loops. A longer loop recurses without end. (reading; unverified)
21. **MultiEffects parse fails on parameterless entries.** `Prop_A,Prop_B` reports "Malformed properties" (`P/Prop_MultiEffects.java:128`, `:159`) because the ability variable is not cleared after a comma or space (`:139`, `:170`). The whole property is then left empty. A trailing parameterless entry is silently dropped, since the loop has no final flush (`:277`). (reading)
22. **MultiEffects bubble leak.** If any child is bubble, the bundle applies every child to the carrier, including non-bubble ones (`P/Prop_MultiEffects.java:406-409`, `:376-403`). (reading)
23. **Unresolved LocationBound target locks the holder.** If no token resolves, `type` stays null (`P/Prop_LocationBound.java:98-106`), and every exit is then blocked (`:243-252`). A typo in a room ID freezes the holder. (reading)
24. **Possible null in PLAYEROK.** `P/Prop_LocationBound.java:161` calls `M.getGroupLeader().isPlayer()` after checking only `amFollowing()`. (unverified)
25. **Off-by-one chance.** `P/Prop_MoveRestrictor.java:234` uses `rollPercentage() < chance`, and `Libraries/Dice.java:409-411` returns 1 to 100. So CHANCE=100 refuses 99% of the time. (reading)
26. **Name-based identity.** ItemBinder binds to `Name()` (`P/Prop_ItemBinder.java:168`, `:233`), and LimitedItems and LimitedEquip key on names (`P/Prop_LimitedItems.java:169`, `P/Prop_LimitedEquip.java:126`). A new character with the same name inherits the binding. (reading)
27. **Redundant lang re-invoke.** `P/Prop_LanguageSpeaker.java:95` clears `langStr`, so the guard at `:115` (`fetchEffect(langStr)`) stops working after the first resolution. The copy is still created and invoked each time, and it is then dropped by the dedupe at `MOBS/StdMOB.java:5241-5242`. (reading)
28. **Dead code.** `P/Prop_LotsForSale.java:117-174` (`isRetractableGridLink`) only calls itself (`:165`). `P/Prop_ModFaction.java:83-90` is unused.
29. **ModExperience re-parses.** `P/Prop_ModExperience.java:82-83` re-parses the text on every message while the list is empty. (reading)
30. **Doc and code mismatches.** Invisibility is documented as MOB-only but accepts items and exits (`P/Prop_Invisibility.java:50-53`). The ItemSlotFiller help example uses `REMOVABLE`, a socket-only parameter (`resources/help/arc_properties.ini:1104-1110`). The translator help says the translation is "spoken out loud" (`:1148-1170`), but the code adds a noise line (`P/Prop_LangTranslator.java:322-354`). (reading)
31. **Typos.** `P/Prop_LimitedContents.java:187` says "You can't can only put". `P/Prop_HereAdjuster.java:58` calls `fixAccoutingsWithMask`, a misspelled base method name.
32. **Stat level scale.** HaveResister gives 5 item-level points per percent of NON-MAGICAL-WEAPONS (`P/Prop_HaveResister.java:424-426`), so 10% is worth 50 points. Whether that is large depends on the item-level scale in `TimsLibrary` (unverified).

## Takeaways for a Python SUD

- **Effects as data, not text.** Keep the idea of an effect object attached to an item, room, exit or character, but give each one a typed parameter record (a dataclass or a validated dict loaded from YAML) rather than a custom text grammar. The parsers in `MultiEffects`, `ItemSlotFiller` and `HaveResister` are where most of the bugs above sit.
- **Derived grants, never saved.** Recompute `character.skills` from equipped items whenever equipment changes, and do not save granted skills. This removes the "stale grant" problem that the enabler handles with non-savable flags.

  ```python
  def granted_skills(character) -> dict[str, int]:
      granted = {}
      for item in character.equipped_items():
          for skill, prof in item.grants.items():   # e.g. {"lockpicking": 80}
              granted[skill] = max(granted.get(skill, 0), prof)
      return granted
  ```

- **Two-phase events.** Use `before(event) -> allowed` for vetoes ("you cannot take that", "the spell fizzles") and `after(event)` for reactions. Keep the damage math (protection with carry-over) as a pure function with tests, and drive it by rounds rather than wall time.
- **Stealth as turns since a quiet act.** Track `turns_since_loud_act` for the player. Hidden is `turns_since_loud_act > 3` and resets on move, speech and attacks. This replaces `System.currentTimeMillis`-style logic if any is used.
- **Bubble as an aggregation step.** Let items contribute modifiers to their holder when stats are computed (`modifiers_for(holder)`), instead of having each item callback into the holder. Keep the item-versus-wearer split from socket fillers: physical stats stay with the item, character stats go to the wearer.
- **Restrictions as predicates.** Replace the mask language with a small set of Python predicates such as `Requires(class_="mage")` and `Forbids(race="elf")`. A single player needs only class, race, level, alignment and faction. Drop PLAYER, NPC, CLAN, ACCOUNT and GROUP modes, and drop the world-wide scans in `LimitedItems`.
- **Binding by ID.** For a single-player quest item, bind to the player's ID on pickup. Name-based binding is not needed.
- **Sockets.** `Socket(slot_type, count, removable, level_mode)` and `Gem(slot_type, count, adds, skips)` map directly onto `ItemSlot` and `ItemSlotFiller`. Keep `levelDiff` as a computed value, not a field updated in several places.
- **Power budget.** Compute `item_power = sum(weight(effect) for effect in item.effects)` from the same effect records used for play, as `TimsLibrary` does, for loot tables and display.
- **Anchors.** An `Anchor(room_id, mode)` checked on movement gives tethers and "returns home" items. Keep the refusal message and the send-home step as separate functions.
- **Translation.** A per-language set of known keywords and a proficiency roll gives a partial-understanding mechanic that suits a text game. The trusted-speech-to-command idea is a good companion mechanic.
- **Transporters as an index.** Keep a `receivers_by_tag` index updated when items or rooms move, instead of scanning the world on each action. This removes both the rescan cost and the stale-destination bug.
- **Deferred effects, not timers.** Replace InstantDeath's tick-and-retick design with a queue of effects resolved at the end of each turn. The damage formula `flat + pct * max_hp` can stay.
- **Multiplayer parts to drop.** `LimitedItems` (cross-host scan), `HereEnabler` (per-visitor trackers), `LangTranslator` passive trust (for a single player, the player's own companions), `LegalBehavior` accusation (one guard NPC reaction is enough), `LotsForSale` (unless the game has a housing system), and the ItemBinder group, clan and account modes.
- **Parts to drop outright.** The MultiEffects text grammar, the zapper mask parser, and InstantDeath as written. Keep their ideas, not their code.

## Coverage

- **Read in full:** all 33 bucket files, from the first line of code to the last. The import and licence headers (lines 1 to about 34) were skipped as they are not needed.
- **Read in full:** `P/Property.java` (173 lines, base class).
- **Read in part:** `P/Prop_SpellAdder.java` lines 88 to 605. Lines 1 to 87 were covered by grep for fields and method names only.
- **Consulted by grep or a short read:** `Abilities/Languages/StdLanguage.java:385-412` and `:587-589`; `Items/Basic/StdItem.java:625-707` (bubble branches only); `MOBS/StdMOB.java:800-830`, `:826-829`, `:4977-4985`, `:5237-5247`; `Libraries/TimsLibrary.java:51`, `:152-209`, `:379-409`; `Libraries/MUDZapper.java` (description branches around `:1585`, `:2618-2640`, `:3440-3500`, `:4702-4730`); `Libraries/interfaces/MaskingLibrary.java:341-420`; `Libraries/Dice.java:398-411`; `Libraries/Sense.java:1083-1086`; `Common/interfaces/PhyStats.java:571`; `Libraries/interfaces/ThreadEngine.java:65-74`; `core/CMProps.java:681`; `Abilities/interfaces/TriggeredAffect.java:42-48`; `Abilities/StdAbility.java:190-193`.
- **Help entries read:** `resources/help/arc_properties.ini` for HAVEENABLER (840), HAVERESISTER (859), HAVEZAPPER (906), HAVESPELLCAST (884), HEREENABLER (960), HERESPELLCAST (979), HIDDEN (1001), INSTANTDEATH (1040), INVISIBILITY (1054), ITEMBINDER (1064), ITEMNORUIN (1076), ITEMSLOT (1086), ITEMSLOTFILLER (1104), ITEMTRANSPORTER (1126), LANGTRANSLATOR (1148), LANGUAGESPEAKER (1171), LIMITEDCONTENTS (1195), LOCATIONBOUND (1238), MOVERESTRICTOR (1421), MULTIEFFECTS (1458), NARROWLEDGE (1471). Sections were read in part.
- **Skipped or unverified:** `P/Prop_RoomForSale.java` (base of LotsForSale, including its marker strings and `updateLotWithThisData`); `P/Prop_HaveAdjuster.java` (base of HereAdjuster); the XP mechanics behind `CMLib.leveler()`; the Falling ability; `CMLib.masking().getPreCompiledMask` on empty input; `getLocalePrototype` on unknown IDs; the zapper mask compiler beyond the key table; the loot policy that reads `SENSE_ITEMNORUIN`.
- **Not run:** nothing in this chapter was executed. All behaviour comes from reading the code.
