# Clan items (Items/ClanItems)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b130` (Items/ClanItems) · 31 files · 6112 lines of Java.

Java paths in this chapter are relative to `com/planet_ink/coffee_mud/` (for example `Items/ClanItems/StdClanItem.java:526`). Data and documentation paths are relative to the CoffeeMud folder. The base item classes (StdItem, StdArmor, StdContainer and the rest) belong to bucket b172 and are only referred to here.

## Summary

Clan items are the objects a clan owns and hands out: banners, gavels, conquest flags, propaganda pamphlets, membership cards, tabbards, sailors' caps, signposts, donation journals, worker tools and containers, and special items that attach behaviours (executioner scales, a tax clipboard, a scavenger's sticker). Each item carries a clan ID and is meant to be usable only by members. Rivals may touch one only when their clan is at war with the owner or holds the area, and the code is written to destroy an item taken by an outsider and pay its clan experience, although in practice only flags pay (see Problems). NPC clan members run the worker tools on their own tick, pamphlets spread between NPCs once per MUD day, and a flag keeps a conquered area in place. Most concrete items are data-driven "Gen" classes whose stats are filled in from `resources/skills/clancraft.txt` by the clan crafting skill. The parts worth borrowing for a single-player game are the ownership and capture rules, the area need-chart that lets NPC workers share material shortages, and the table-driven recipe format.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Items/ClanItems/StdClanItem.java` | Base for most clan items: clan ID, item type, rightful owner, expiry check, shared static helpers `stdOkMessage`, `stdExecuteMsg`, `standardTick`, `loadRecipes` | 544 |
| `Items/ClanItems/StdClanCommonItem.java` | NPC worker tool (gathering and crafting). Holds the static area need-chart and the mining glow | 468 |
| `Items/ClanItems/StdClanFlag.java` | Conquest flag. Extends StdItem directly and reuses the static helpers | 359 |
| `Items/ClanItems/GenClanCastle.java` | Siege castle on GenCastle. Hull damage opens temporary breaches, and sit and enter rules apply | 314 |
| `Items/ClanItems/StdClanSpecialItem.java` | Scales, tax clipboard and scavenger sticker. Attach a behaviour and run it on the carrier | 153 |
| `Items/ClanItems/StdClanPamphlet.java` | Propaganda pamphlet. Traded to ruling-clan NPCs once per MUD day | 128 |
| `Items/ClanItems/StdClanCard.java` | Membership card (anti-propaganda). Rejects rival pamphlets and enrols NPCs in the ruling clan | 129 |
| `Items/ClanItems/StdClanSignpost.java` | Signpost. Strength-based attempts to pull it free, and a write permission test | 151 |
| `Items/ClanItems/StdClanSailorsCap.java` | Rank-gated cap. Implements ClanItem on StdArmor and keeps its own clan logic | 201 |
| `Items/ClanItems/StdClanDonationList.java` | Donation journal. Logs player actions to the database and prints them on READ | 193 |
| `Items/ClanItems/StdClanContainer.java` | Clan container (StdContainer). Has the capture rule | 151 |
| `Items/ClanItems/StdClanCommonContainer.java` | Worker container. Its NPC holder performs a common skill | 106 |
| `Items/ClanItems/StdClanArmor.java` | Base for worn clan armour (tabbard, concierge) | 149 |
| `Items/ClanItems/StdClanTabbard.java` | Tabbard. Worn about the body. Clan takes a share of the wearer's experience | 68 |
| `Items/ClanItems/StdClanApron.java` | Merchant apron. Refuses trade to hostile clans and adds the Merchant ability | 114 |
| `Items/ClanItems/StdClanConcierge.java` | Concierge ribbon. Wraps a Concierge behaviour with clan parameters | 103 |
| `Items/ClanItems/GenClanItem.java` | Generic Gen item base. Adds `readableText`, the CLANID and CITYPE stats, and the stat-code cache | 171 |
| `Items/ClanItems/GenClanSpecialItem.java` | Gen wrapper for special items. Overrides `setReadableText` (see Problems) | 171 |
| `Items/ClanItems/GenClanCommonItem.java` | Gen wrapper for worker tools. Sets the glow for "Mining" | 172 |
| `Items/ClanItems/GenClanCommonContainer.java` | Gen wrapper for worker containers | 219 |
| `Items/ClanItems/GenClanContainer.java` | Gen wrapper for clan containers | 219 |
| `Items/ClanItems/GenClanArmor.java` | Gen wrapper for clan armour, with layer stats | 214 |
| `Items/ClanItems/GenClanTabbard.java` | Gen wrapper for tabbards | 213 |
| `Items/ClanItems/GenClanConcierge.java` | Gen wrapper for concierge ribbons | 212 |
| `Items/ClanItems/GenClanSailorsCap.java` | Gen wrapper for sailors' caps | 171 |
| `Items/ClanItems/GenClanFlag.java` | Gen wrapper for flags | 171 |
| `Items/ClanItems/GenClanPamphlet.java` | Gen wrapper for pamphlets | 171 |
| `Items/ClanItems/GenClanSignpost.java` | Gen wrapper for signposts | 171 |
| `Items/ClanItems/GenClanCard.java` | Gen wrapper for membership cards | 170 |
| `Items/ClanItems/GenClanApron.java` | Gen wrapper for aprons | 169 |
| `Items/ClanItems/GenClanDonationList.java` | Gen wrapper for donation journals | 167 |

Shared code outside this bucket that the chapter depends on (read only as far as needed):

| File | Why it matters here |
|---|---|
| `Items/interfaces/ClanItem.java:48-67` | `ClanItemType` enum. The comment says its order is significant (line 48) |
| `Abilities/Common/ClanCrafting.java:79-96, 559-569, 623` | Recipe column layout and how a recipe row becomes a built item |
| `resources/skills/clancraft.txt` | 46 recipe rows that generate the Gen items |
| `Common/interfaces/Clan.java:1054-1062, 1190-1195` | `REL_*` relation constants and the `Authority` enum (`CAN_NOT_DO`, `CAN_DO`, `MUST_VOTE_ON`) |
| `Common/DefaultClan.java:2611-2630` | `applyExpMods`, the clan's tax share of member experience |
| `Libraries/Clans.java:176-247` | `findRivalrousClan(s)`: clans flagged rivalrous |
| `MOBS/StdMOB.java:5183-5204` | `setClan` with a negative role removes membership |
| `Items/Basic/GenCastle.java:77-79` | `getMaxHullPoints` = 100 × proper rooms + armour |
| `Items/Basic/StdSiegableBoardable.java:1448-1478` | Siege damage: converted to whole percent of hull |
| `Behaviors/Conquerable.java:252-287, 955-980` | Area conquest. Banners, gavels and the officer badge are checked here |
| `core/database/PDataLoader.java:76-110` | Player-data read used by the donation journal (no ORDER BY) |
| `core/CMClass.java:2333-2345`, `core/CMath.java:2104-2114` | Case-insensitive ID fallback; `s_int` returns 0 for non-numbers |
| `guides/ArchonGuide.html:4160-4220` | Builder description of the Gen clan item classes and their item-type mapping |

## How it works

### 1. Data model

Every clan item keeps the same few fields:

- `myClan` (clan ID; empty means not a clan item), `ciType` (a `ClanItemType`), and `riteOwner` (the "rightful owner", an NPC MOB or a Room). `StdClanItem.java:46-49`.
- `lastClanCheck`, the time of the last membership check. It starts at construction time in StdClanItem (line 48) and at 0 in StdClanFlag and StdClanContainer.

`ClanItemType` (`Items/interfaces/ClanItem.java:48-67`) is stored as a number in data. Its ordinal is part of the file format, since `getValueOf` accepts a number or a name (lines 128-138):

| Ordinal | Type | Display name | Used by |
|---|---|---|---|
| 0 | FLAG | Flag | Conquest flags; also the default ordinal for several recipes |
| 1 | BANNER | Banner | Guard banner. Only roaming NPCs may hold one (`StdClanItem.java:349-357`) |
| 2 | GAVEL | Gavel | Judge's gavel. Read by `Conquerable.isTheJudge` (`Behaviors/Conquerable.java:284`) |
| 3 | PROPAGANDA | Propaganda | Pamphlet |
| 4 | GATHERITEM | Gathering tool | Worker tools and worker containers |
| 5 | CRAFTITEM | Crafting tool | Crafting worker tools |
| 6 | SPECIALSCALES | Justice tool | Executioner's scales and the "Assassins Eye" recipe |
| 7 | SPECIALSCAVENGER | Scavenging tool | Scavenger's sticker |
| 8 | SPECIALOTHER | Clan item | Default type. Concierge ribbons and uniforms |
| 9 | SPECIALTAXER | Taxing tool | Tax collector's clipboard |
| 10 | DONATIONJOURNAL | Journal | Donation list |
| 11 | ANTI_PROPAGANDA | Anti-propaganda | Membership card |
| 12 | SPECIALAPRON | Merchant tool | Shop apron |
| 13 | LEGALBADGE | Officer emblem | No recipe makes it. Searched for in `Behaviors/Conquerable.java:962-979` (see Problems) |
| 14 | TABBARD | Tabbard | Tabbard |
| 15 | SAILORSCAP | Sailors cap | Sailors' cap |
| 16 | SIGNPOST | Signpost | Signpost |

The Gen classes store two extra stats, CLANID (index 0) and CITYPE (index 1, the ordinal). `GenClanItem.java:90-128`. Their stat-code arrays are cached in a static field (`GenClanItem.java:140-156`).

### 2. Membership: eviction and expiry

Each clan item re-checks its carrier every so often. In `StdClanItem.executeMsg` the check runs every half hour (`StdClanItem.java:150`, `TimeManager.MILI_HOUR / 2`). If the clan no longer exists, or the carrying MOB no longer has a role in it (propaganda is exempt), the item is removed from the carrier, moved to the room and announced:

```
if ((CMLib.clans().getClanAnyHost(clanID()) == null)
|| ((getClanItemType() != ClanItem.ClanItemType.PROPAGANDA)
    && (((MOB) owner()).getClanRole(clanID()) == null)))
...
R.showHappens(CMMsg.MSG_OK_VISUAL, L("@x1 is dropped!", name()));
```

StdClanFlag, StdClanSailorsCap and StdClanContainer check hourly (`StdClanFlag.java:146`, `StdClanSailorsCap.java:121`, `StdClanContainer.java:104`). StdClanFlag and StdClanSailorsCap destroy themselves once their clan is gone (`StdClanFlag.java:149-152`). StdClanArmor checks every half hour like StdClanItem (`StdClanArmor.java:109`). StdClanFlag and StdClanSailorsCap override `expirationDate()` to return 0 (`StdClanFlag.java:137-141`, `StdClanSailorsCap.java:112-116`). Whether 0 means "never expires" is not checked in StdItem.

### 3. The two-phase gate: `okMessage` and `executeMsg`

The rules sit in the static helper `StdClanItem.stdOkMessage` (`StdClanItem.java:380-496`), which most classes call from `okMessage`. The capture step is `stdExecuteMsg` (`StdClanItem.java:498-543`), which only some classes call (see Problems).

Gives (`StdClanItem.java:382-399`): a give to someone with no role in the clan is refused with "You cannot give this item to @x1." (line 393). Recipients are checked by `stdOkMessageMOBS` (lines 325-378):

- An NPC may hold only one clan item, except that propaganda is allowed alongside another item (`stdOkMessageMOBS`, lines 329-348). Otherwise "@x1 already has @x2, and cannot have another Clan Item."
- Banners go only to roaming NPCs: "This item should only be given to those who roam the area." (line 353).
- A conquered mob may receive a clan item only inside the conquered area (lines 358-375).

Takes (`StdClanItem.java:401-468`): a GET, PUSH, PULL or CAST_SPELL on the item by a non-member needs a rivalrous clan (line 410), or the refusal "You must belong to an eligible clan to do that to a clan item." If the item's clan has disappeared, the item is destroyed with "This ancient relic from a lost clan fades out of existence." (lines 417-421). The area's law is then checked. Holding the area itself is refused with "You'll need to conquer this area to do that." (line 445). A member of the ruling clan who does not yet control the whole area is refused with "Your clan does not yet fully control the area." (line 452). Anyone else must be at war with the item's clan, with staff exempt through the CMDITEMS flag: "You must be at war with this clan to take one of their items." (lines 458-461). The war test loops over all of the taker's rivalrous clans and stops at the first at war (lines 431-435).

Tabbard experience (`StdClanItem.java:470-494`): when the wearer gains experience (an EXPCHANGE message, or a Spell_ClanExperience cast with a positive value) and the item is a worn tabbard, the message is refused if the wearer is no longer in the clan, and the item is destroyed. Otherwise the clan takes its share. A cast gives the clan `adjExp`, and an EXPCHANGE calls `applyExpMods` (lines 489-492). `applyExpMods` removes the clan's tax share (`Common/DefaultClan.java:2611-2625`).

Capture after the fact (`StdClanItem.java:498-543`, called only by the classes listed in Problems item 5): if a clan member GETs the item, the rightful owner becomes the NPC taker (or nobody, for a player). If a non-member GET, PUSH or PULL succeeds, the item is destroyed with "<T-NAME> is destroyed by <S-YOUPOSS> touch!" (line 518). Every rival clan the taker belongs to is then paid half the recipe's experience value (lines 519-535) with the message "@x1 gains @x2 experience points for this capture." (line 532). The return value is `false`, so the touch does nothing else.

### 4. Ticks: `standardTick`

`Tickable.TICKID_CLANITEM` is 15 (`core/interfaces/Tickable.java:112`). The default tick is 4000 ms (`core/CMProps.java:681`). `StdClanItem.standardTick` (`StdClanItem.java:205-254`) does three things:

- An NPC holding an item for a clan it has left drops it: "<S-NAME> drop(s) <T-NAME>." (lines 217-229), except for propaganda.
- A carried item that is not worn is put on the NPC at its proper location, provided the NPC wears no other clan item (`wearingAClanItem`, lines 190-203; lines 232-239).
- An item on the floor whose rightful owner is an NPC is moved back to that NPC (lines 242-251). A corpse releases it (line 245).

### 5. Conquest flags (StdClanFlag)

A flag is COTTON, weight 1, type FLAG (`StdClanFlag.java:47, 54-61`). It has no tick of its own. The rules are in `okMessage` and `executeMsg`:

- **Planting (DROP by a member).** The room becomes the rightful owner and a LOOK trailer is attached (`StdClanFlag.java:162-169`). The area's ruling clan is announced: "Area '@x1' is presently neutral." or "Area '@x1' is presently controlled by @x2." (lines 323-327). A member of a different clan may plant only while at war with the ruling clan (lines 328-343).
- **Non-member drop.** This is allowed only where the room's land title belongs to the clan, the taker's liege or the taker, or where the area has control points above zero. Otherwise "You can not place a flag here, this place is controlled by the Archons." (lines 257-287).
- **Taking.** A non-member needs a rivalrous clan ("You must belong to an eligible clan to take a clan item.", line 220). Clan guards in the room block the attempt: "<S-NAME> guard(s) @x1 closely." (lines 226-237). The taker must belong to the ruling clan (lines 240-247) and the area must be fully controlled (lines 249-252). An NPC clan member may take a flag only when a player member of the clan is in the room, otherwise "You are guarding @x1 too closely." (lines 291-311).
- **Look.** Examining the flag returns the area's `conquestInfo` text, or "This area is under the control of the Archons." (lines 171-182).
- **Declaration.** A clan member who speaks the phrase "I HEREBY DECLARE THIS AREA" adds one control point to the area's legal behaviour (lines 184-199; the constant is at line 191). The check at line 185 uses `CMSecurity.isAllowed(..., CMDROOMS)`, which is a staff flag. Whether an ordinary player can ever reach this path is unverified.

The Archon guide says the flag "holds a conquered area in place, and provides Conquerable behavior information" (`guides/ArchonGuide.html:4160-4180`, approximate). Flags are handled by `Behaviors/Conquerable.java`, which is in another bucket.

### 6. Worker tools and the need-chart (StdClanCommonItem)

A worker tool is GATHERITEM (`StdClanCommonItem.java:58`). Its `readableText` names a common skill (for example "Mining"). The tick (`StdClanCommonItem.java:225-467`) applies only to an NPC holding the item who is in the game, has a clan role, is not an animal and is not already tracking someone (lines 229-237).

Timing: the worker waits `workDown` ticks, which is reset to `1d7` after each action (lines 234, 239). That is 1 to 7 ticks, or 4 to 28 seconds at the default tick. Only common skills qualify (line 244). The worker does not repeat a skill that already has an effect on it (line 241).

Non-craft skills go through the need-chart logic:

- **Fire building** (lines 259-296). When metal is needed, a worker goes to a peer whose room has wood and no fire.
- **Holding materials** (lines 299-329). A worker that carries materials a peer in the same room needs drops them.
- **Going elsewhere** (lines 330-354). A worker tracks a peer elsewhere who needs what it carries, using the common skill Skill_Track (`trackTo`, lines 196-222).
- **Fetching** (lines 355-383). A worker picks up materials lying in its room that a far peer needs, then tracks that peer.

Crafting and epicurean skills (lines 417-439) are invoked with the name of a random carried item as the argument, or with no argument. The darkness lines are at lines 391-413. The worker speaks one of seven complaints when it cannot see, for example "It's too dark to work." (line 397).

Failure bookkeeping (lines 440-462): a failed craft records the NPC in the area's `needChart` (a static map keyed by Area, line 43) together with the materials parsed from the skill's requirement text. The parser `enCode` (lines 138-173) finds material names and codes in upper-cased text, ignoring partial words. If metal is needed and the room has no wood, wood is added (lines 168-171). A successful craft removes the worker from the chart (lines 460-461).

Glow: the readable text "Mining" sets `glows` (line 193). While the item is equipped, the holder in a home-area cave gains `CAN_SEE_DARK` (`affectPhyStats`, lines 176-187).

Worker containers (`StdClanCommonContainer.java:63-105`) are simpler. The holder repeats its common skill every 1 to 5 ticks (`roll(1,5,0)` at line 75), with a random carried item as the argument. A container does not seek resources.

### 7. Propaganda and membership cards

**StdClanPamphlet** (PROPAGANDA, paper, weight 1; `StdClanPamphlet.java:45-58`):

- It acts only while held by an NPC in its home area who is not an animal (lines 65-72).
- Gate (lines 75-76): the carrier is a member of the pamphlet's clan, or `--tradeTime <= 0`. `tradeTime` starts at -1 (line 43), so a non-member carrier acts at once.
- If the ruling clan differs from the pamphlet's clan, and the carrier is a member of the ruling clan, the carrier is removed from that clan with `setClan(rulingClan, -1)` (lines 81-85). `StdMOB.setClan` with a negative role removes the membership (`MOBS/StdMOB.java:5194-5203`).
- On trade time (lines 86-121), a random inhabitant of the ruling clan who can see and hear the carrier is chosen. The carrier says "Hey, take a look at this." (line 107), copies the pamphlet (`copyOf`, line 108), and gives the copy with a GIVE message (lines 110-112). The copy is destroyed if the recipient does not hold it (lines 113-117).
- The timer is reset to `TICKSPERMUDDAY` (line 123), a MUD day in ticks (`core/CMProps.java:342`). The configured value is unverified.

**StdClanCard** (ANTI_PROPAGANDA, paper; `StdClanCard.java:43-56`):

- Rejection (lines 61-91). An NPC holding a card is handed a propaganda item from a different clan. The NPC says "How dare you!  Give me those!" (line 73) and then destroys the giver's other clan items of that clan (lines 77-84). The give is refused (line 86). See Problems for what happens to the pamphlet itself.
- Enrolment (lines 98-124). An NPC in its home area, where the ruling clan is the card's clan, and who is not yet a member, is added to the clan at the government's auto role (`setClan(clanID(), roleID)`, line 121).

### 8. Signpost (StdClanSignpost)

A signpost is WOOD, weight 25, with `SENSE_ITEMNOTGET` set, and it is readable (`StdClanSignpost.java:55-65`).

- Push and pull are refused: "You can't move @x1." (lines 71-77).
- Writing requires a clan role. Lines 94-97 test CLAN_BENEFITS and ENCHANT against `CAN_NOT_DO` and refuse with "Only authorized members of @x1 may write on @x2." As written, that refuses members who do hold either authority. See Problems.
- An NPC can never pull it free (lines 107-113).
- A player's attempt subtracts their strength from `counter`, which starts at 30 (`STR_TO_LOOSEN`, lines 45, 48, 117). The counter resets after one minute with no attempt (`GET_RESET_MILLIS`, lines 46, 115-116). So a character with strength 15 needs two attempts, and one with strength 30 or more needs one.
- Once the counter is at or below zero, the item is taken for a moment by clearing `SENSE_ITEMNOTGET` (lines 119-132). The mask is restored in a `finally` block. Otherwise the message is "<S-NAME> strain(s) against <T-NAME>, and it loosens slightly but holds fast." (line 136).

### 9. Special items (StdClanSpecialItem)

The class carries a `Behavior` in a private field `B` and a flag `flag` (lines 44-45). Its behaviour depends on its type (tick, lines 110-149):

- **SPECIALSCAVENGER** (lines 112-123). Picks a random room item that is not in a container and orders the holder to get it.
- **SPECIALSCALES** (lines 124-138). Attaches `GoodExecutioner`, or `EvilExecutioner` when `flag` is "EVIL".
- **SPECIALTAXER** (lines 139-146). Attaches `TaxCollector` and ticks it on the owner.

Messages reach the attached behaviour through `executeMsg` (lines 75-84), but only for a member who is awake, mobile and not an animal. `okMessage` lets `TaxCollector` veto a message (lines 92-93). `setReadableText` stores "GOOD" and "EVIL" in `flag` and not in the readable text (lines 63-68).

`GenClanSpecialItem` overrides `setReadableText` without calling the parent, so `flag` is never set on a generated item (`GenClanSpecialItem.java:77-80`). See Problems.

The behaviours `GoodExecutioner`, `EvilExecutioner` and `TaxCollector` are in `Behaviors/` and were not read here.

### 10. Sailors' cap (StdClanSailorsCap)

The cap is worn on the head (`StdClanSailorsCap.java:62`). It extends StdArmor and implements ClanItem, so it repeats the clan logic rather than using StdClanItem's helpers.

- A non-approved member who takes it needs PROPERTY_OWNER authority at `CAN_DO` (line 156), or "You have not earned @x1." (line 158).
- A member with PROPERTY_OWNER authority who gives it to another member sets `approvedGetterM` (lines 166-184). That approval is a plain field, and this class does not save it.

### 11. Apron, tabbard and concierge (worn effects)

- **StdClanApron** (`StdClanApron.java:43-113`). Worn at the waist and about the body (line 55). While the owner is a merchant, customers from clans that are hostile to the apron's clan are refused with "<S-NAME> seem(s) to be ignoring you." (line 89). Only NEUTRAL, ALLY and FRIENDLY relations pass (lines 85-87; constants at `Common/interfaces/Clan.java:1054-1062`). The tick adds the Merchant ability (lines 104-111; `Abilities/Common/Merchant.java`).
- **StdClanTabbard** (`StdClanTabbard.java:43-58`). Worn about the body (line 55). Its experience effect is in `StdClanItem.stdOkMessage` (see section 3).
- **StdClanConcierge** (`StdClanConcierge.java:60-102`). `getConcierge()` creates a Concierge behaviour lazily. For head-only ribbons it sets `CLAN="<id>";AREAONLY=TRUE`, and for others `CLAN="<id>"` (lines 70-74). It starts an item tick (lines 75-76), and `okMessage`, `executeMsg` and `tick` are forwarded to it (lines 83-100). The Concierge behaviour is in `Behaviors/Concierge.java` and was not read.

### 12. Donation journal (StdClanDonationList)

The journal writes player data for the clan. Its hooks are Spell_ClanDonate on an item (lines 165-174), and GET (line 179), PUSH or PULL (line 184) and DROP (line 189) of an item. Each record goes to the `DONATIONS` section, keyed by a timestamp, the player and a random number (`getDonationKey`, lines 96-103), with a text such as "<player> <verb> <item> at <time>." (lines 83-94).

READ (lines 110-164) loads every row for the clan (line 117), sorts the rows by their timestamp (lines 118-152), and prints them one per line (lines 153-157). An empty clan gets "There is nothing written on @x1." (line 159). The query behind `DBReadPlayerData` has no `ORDER BY` (`core/database/PDataLoader.java:86`).

`okMessage` makes the journal immovable once it sits in the clan's donation room (lines 62-81).

### 13. Gen classes and the recipe table

Each Gen class is a copy of its Std parent with a `readableText` field, `isGeneric()` returning true, `text()` writing XML through CoffeeMaker, and `setMiscText` unpacking that XML (for example `GenClanItem.java:45-88`). Each adds its own MYCODES array and stat cache. Armour and containers add HASLOCK, HASLID, CAPACITY, CONTAINTYPES, RESETTIME, DEFCLOSED and DEFLOCKED, and wearables add LAYER and LAYERATTRIB (`GenClanCommonContainer.java:103`, `GenClanArmor.java:90`).

The clan crafting skill turns a recipe row into a Gen item (`Abilities/Common/ClanCrafting.java`):

- The class column names the Gen class to create (RCP_CLASSTYPE, column 8).
- The type column sets CITYPE (RCP_CITYPE, column 5; line 623).
- The misc column becomes the readable text (lines 559-569, `buildingI.setReadableText(misctype)` at line 569). For tools this is the skill ID. For special items it is GOOD or EVIL.
- The name has a `%` placeholder that is replaced by the crafter's chosen name, and the item is named "... of <clan type> <clan name>" (lines 559-566; the substitution helper is in `Abilities/Common/CraftingSkill.java:280-299`).

Column layout (`ClanCrafting.java:79-88`):

```
ITEM_NAME  ITEM_LEVEL  BUILD_TIME_TICKS  RESOURCE_1  RESOURCE_2  CLAN_ITEM_CODENUMBER
CLAN_EXPERIENCE_COST  ITEM_BASE_VALUE  ITEM_CLASS_ID  CLAN_AREA_FLAG||WEAR_LOCATION||READABLE_TEXT
CONTAINER_CAPACITY  BASE_ARMOR||BOARDABLE_POP  CONTAINER_TYPE  CODED_SPELL_LIST  REQUIRED_SKILL
```

A sample row (`resources/skills/clancraft.txt`, the first line):

```
Flag %	1	200	COTTON/1	OAK/1	0	2500	0	GenClanFlag
```

The experience cost (column 6) is used when crafting (`ClanCrafting.java:378`, `459`). A pamphlet built this way gets a default readable text that praises the clan and asks the reader to join (`ClanCrafting.java:632-636`).

Two more things happen when a recipe becomes an item (`ClanCrafting.java:606-623`). The item's level is the recipe level plus 3 × (material hardness − 6), with a minimum of 1 (lines 607-610). The item is marked unlocatable (`SENSE_UNLOCATABLE`, line 617). Its clan is the crafter's clan, or the first clan on the server when the crafter has none (lines 617-622).

## Content worth mining

### Clan item types (see section 1)

Sixteen types make up the mechanical vocabulary. A single-player game can reuse the idea of a typed token, although the ordinal-based storage is best avoided (see Takeaways).

### Recipe catalogue

All 46 rows of `resources/skills/clancraft.txt`. Level (lvl), build ticks, resource pairs, type (ordinal and name), Java class, clan experience cost (XP) and the misc column (readable text or wear location). Rows marked (not clan) use classes outside this bucket.

| Item (name with `%` placeholder) | Lvl | Ticks | Resources | Type | Class | XP | Misc or skill |
|---|---|---|---|---|---|---|---|
| Flag | 1 | 200 | COTTON/1, OAK/1 | 0 FLAG | GenClanFlag | 2500 | |
| Tabbard | 1 | 50 | COTTON/10, GOLD/2 | 14 TABBARD | GenClanTabbard | 1000 | BODY |
| Journal | 1 | 20 | PINE/10 | 0 | GenJournal (not clan) | 50 | |
| Home Compass | 1 | 20 | OAK/10, GOLD/1 | 0 | GenClanItem | 50 | Spells: Prop_UseSpellCast, Skill_FindClanHome |
| Sailors Cap | 6 | 20 | SILK/5, GOLD/1 | 15 SAILORSCAP | GenClanSailorsCap | 1000 | HEAD |
| GuardBanner | 1 | 40 | COTTON/1, OAK/1 | 1 BANNER | GenClanItem | 500 | HELD |
| Judges Gavel | 1 | 100 | MITHRIL/2, OAK/5 | 2 GAVEL | GenClanItem | 1000 | HELD |
| Book of the Laws | 10 | 100 | OAK/100 | 0 | GenLawBook (not clan) | 2000 | AREA |
| Pamphlet | 5 | 20 | PINE/5 | 3 PROPAGANDA | GenClanPamphlet | 500 | |
| Membership Card | 5 | 20 | PINE/1 | 11 ANTI_PROPAGANDA | GenClanCard | 5 | |
| Donation List | 10 | 350 | MAPLE/1, SILK/20 | 10 DONATIONJOURNAL | GenClanDonationList | 1000 | |
| Miners Pick | 1 | 10 | OAK/5, IRON/10 | 4 GATHERITEM | GenClanCommonItem | 100 | Mining |
| Foragers Basket | 1 | 10 | VINE/20 | 4 | GenClanCommonContainer | 100 | Foraging, capacity 30 |
| Lumberjacks Saw | 1 | 10 | OAK/10, IRON/5 | 4 | GenClanCommonItem | 100 | Chopping |
| Diggers Shovel | 1 | 10 | PINE/10, IRON/5 | 4 | GenClanCommonItem | 100 | Digging |
| Tanners Knife | 1 | 10 | OAK/1, IRON/3 | 4 | GenClanCommonItem | 100 | Butchering |
| Fishers Pole | 1 | 10 | HICKORY/10 | 4 | GenClanCommonItem | 100 | Fishing |
| Hunters Snare | 1 | 10 | REDWOOD/10 | 4 | GenClanCommonItem | 100 | Hunting |
| Farmers Tiller | 10 | 10 | BALSA/10 | 4 | GenClanCommonItem | 100 | Farming |
| Shroomers Bag | 10 | 10 | HEMP/10 | 4 | GenClanCommonItem | 100 | Shrooming |
| Tailors Needle | 2 | 20 | COPPER/5 | 5 CRAFTITEM | GenClanCommonItem | 100 | Tailoring |
| Weaponsmiths Hammer | 2 | 20 | GRANITE/10 | 5 | GenClanCommonItem | 100 | Weaponsmithing |
| Armorsmiths Anvil | 2 | 20 | IRON/50 | 5 | GenClanCommonItem | 100 | Armorsmithing |
| Leatherworkers Tools | 2 | 20 | IRON/6 | 5 | GenClanCommonItem | 100 | Leatherworking (ID is LeatherWorking; lookup ignores case) |
| Blacksmiths Bellows | 2 | 20 | OBSIDIAN/8 | 5 | GenClanCommonItem | 100 | Blacksmithing |
| Wainwrights Tools | 2 | 20 | IRON/8 | 5 | GenClanCommonItem | 100 | Wainwrighting |
| Potters Wheel | 2 | 20 | MAPLE/20 | 5 | GenClanCommonItem | 100 | Pottery |
| Jewellers Tools | 2 | 20 | SILVER/10 | 5 | GenClanCommonItem | 100 | JewelMaking |
| Scrimshawers Blade | 2 | 20 | IRON/5 | 5 | GenClanCommonItem | 100 | ScrimShaw |
| Papermakers Tools | 5 | 20 | IRON/10 | 5 | GenClanCommonItem | 100 | PaperMaking |
| Carpenters Hammer | 2 | 20 | STONE/10, OAK/5 | 5 | GenClanCommonItem | 100 | Carpentry |
| Firebuilders Flint | 2 | 20 | FLINT/1 | 5 | GenClanCommonItem | 100 | FireBuilding |
| Fletchers Tools | 2 | 20 | BRONZE/20 | 5 | GenClanCommonItem | 100 | Fletching |
| Glassblowers Tools | 2 | 20 | TIN/5 | 5 | GenClanCommonItem | 100 | GlassBlowing |
| Sculptors Chisel | 2 | 20 | BRASS/20 | 5 | GenClanCommonItem | 100 | Sculpting |
| Boatwrights Tools | 5 | 20 | LEAD/10 | 5 | GenClanCommonItem | 100 | "BoatWrighting" (no such skill ID; see Problems) |
| Weavers Loom | 2 | 20 | OAK/5 | 5 | GenClanCommonItem | 100 | Weaving |
| Executioners Scales | 5 | 50 | BRONZE/20 | 6 SPECIALSCALES | GenClanSpecialItem | 500 | GOOD |
| Shop Apron | 2 | 10 | COTTON/30 | 12 SPECIALAPRON | GenClanApron | 250 | |
| Assassins Eye | 5 | 50 | BRONZE/20 | 6 SPECIALSCALES | GenClanSpecialItem | 500 | EVIL |
| Scavengers Sticker | 1 | 20 | MAPLE/20, IRON/20 | 7 SPECIALSCAVENGER | GenClanSpecialItem | 100 | |
| Tax Collectors Clipboard | 5 | 30 | IRONWOOD/20, GOLD/10 | 9 SPECIALTAXER | GenClanSpecialItem | 1000 | |
| Concierges Uniform | 15 | 20 | WOOL/10, SILK/2 | 8 SPECIALOTHER | GenClanConcierge | 500 | TORSO&&ARMS&&LEGS, armour 1 |
| Concierges Cap | 3 | 10 | WOOL/5 | 8 | GenClanConcierge | 100 | HEAD |
| Ship Compass | 3 | 20 | IRONWOOD/10, MITHRIL/1 | 0 | GenClanItem | 50 | Spells: Prop_UseSpellCast, Skill_FindClanShip |
| Signpost | 4 | 20 | WOOD/24, NICKEL/1 | 16 SIGNPOST | GenClanSignpost | 150 | base value 20 |

Points to note: the first and third columns are the only ones that vary a lot. Every tool recipe uses a common skill as its misc value, so the tools form a clear ladder from gathering (level 1, 10 ticks) to crafting (level 2 to 5, 20 ticks). No recipe produces a LEGALBADGE, and the Journal and Book of the Laws rows use classes that belong to other buckets.

### Names and messages worth reusing

- "I HEREBY DECLARE THIS AREA" (`StdClanFlag.java:191`), the declaration phrase.
- "Hey, take a look at this." (`StdClanPamphlet.java:107`), a propaganda pitch.
- "How dare you!  Give me those!" (`StdClanCard.java:73`), a rejection line.
- "<S-NAME> strain(s) against <T-NAME>, and it loosens slightly but holds fast." (`StdClanSignpost.java:136`), a strength-check line.
- "<S-NAME> seem(s) to be ignoring you." (`StdClanApron.java:89`), a refusal to serve hostile clans.
- The seven darkness complaints (`StdClanCommonItem.java:391-413`), for example "Too dadgum dark." Useful NPC barks.

## Notable design ideas

- **Capture as destruction plus compensation.** A non-member who takes a clan item destroys it, and each rival clan the taker belongs to is paid experience from the recipe (`StdClanItem.java:516-539`). The item cannot become a trophy, but it can still pay out.
- **A shared need-chart for NPC workers.** Workers in an area record unmet material needs in one static table keyed by area (`StdClanCommonItem.java:43, 440-461`). Other workers carry, drop or fetch what a needy peer lacks, and they track peers across the area (lines 299-383). This is an emergent supply network with no planner.
- **Propaganda as a meme.** A pamphlet copies itself into the ruling clan's NPCs once per MUD day, and it strips the carrier's membership of the ruling clan on every tick (`StdClanPamphlet.java:81-121`).
- **Conquest pipeline.** A flag needs guards cleared, a ruling-clan member carrying it, a fully controlled area and war (`StdClanFlag.java:226-252`). Control points come from declarations, and a castle's defeat resets them (`GenClanCastle.java:175-186`).
- **Effort counter with a decay.** The signpost counts down by strength and restarts after a minute of rest (`StdClanSignpost.java:45-46, 114-118`). It is a small effort mechanic that costs one number.
- **Auto-enrolment by card.** A membership card in a conquered area enrols local NPCs at the clan's auto role (`StdClanCard.java:113-121`).
- **Data-driven factory.** A recipe row names the Java class to build, the item type and the readable text, so builders can add items without code (`ClanCrafting.java:79-96, 559-569`).
- **Taxed devotion.** A tabbard routes part of its wearer's experience to the clan through the clan's tax rate (`StdClanItem.java:470-494`, `DefaultClan.java:2611-2625`).
- **Sit-to-enter siege.** A sitting intruder can enter a castle through a breach if the group leader is not friendly to the castle's clan (`GenClanCastle.java:119-145`).
- **Strength-gated pull.** A signpost can be pulled free only by an attempt total that exceeds its counter (`StdClanSignpost.java:117-133`).

## Problems and cruft

The items below are read from the code. None was run, so each is a reading to confirm in game or in a test.

1. **Capture reward reads the wrong recipe column.** `StdClanItem.java:526` compares `CMath.s_int(V.get(3))` with the item type, but column 3 is a resource string (for example "COTTON/1"), and the type is column 5 (`ClanCrafting.java:92`). `CMath.s_int` returns 0 for text (`core/CMath.java:2104-2114`). So the test matches only ordinal 0, FLAG, and only on the first row, "Flag". Captured flags pay 2500/2 = 1250 experience. Every other captured item pays nothing (line 528 uses the right column 6, so the value is right, but the type test is not).
2. **Inverted write test on signposts.** `StdClanSignpost.java:94-97` refuses members who hold CLAN_BENEFITS or ENCHANT authority (`!= CAN_NOT_DO`), with the message "Only authorized members ... may write". The sense looks reversed.
3. **Generated scales and eyes are always "good".** `GenClanSpecialItem.java:77-80` overrides `setReadableText` without calling the parent, so `StdClanSpecialItem.setReadableText` (lines 63-68) never sets `flag`. Every generated scales or Assassins Eye therefore attaches `GoodExecutioner` (`StdClanSpecialItem.java:133-135`), including the EVIL recipe.
4. **Rejected pamphlet is not destroyed.** `StdClanCard.java:77-84` skips `msg.tool()` (`I!=msg.tool()`), so the giver's other items of that clan are destroyed, but the pamphlet itself is not. The message "takes ... away ... and destroys it!" (line 74) says otherwise, and the give is refused (line 86). Confirm in game.
5. **Capture rule applies to only some classes.** `StdClanItem.executeMsg` (lines 146-172) never calls `stdExecuteMsg`. Only StdClanFlag, StdClanSailorsCap, StdClanContainer (and its subclasses) and GenClanCastle do (`StdClanFlag.java:155`, `StdClanSailorsCap.java:130`, `StdClanContainer.java:132`, `GenClanCastle.java:109`). Banners, gavels, tools, signposts, pamphlets and specials can be taken by a rival at war without destruction or payment (only the `okMessage` gate applies). StdClanArmor calls neither helper in `executeMsg` (`StdClanArmor.java:105-131`). Whether this is intended is not clear.
6. **Recipe cache never hits.** `StdClanItem.java:308` reads `"PARSED: clancraft.txt"`, and line 320 stores `"PARSED: clancrtaft.txt"`. So the file is read and parsed on every capture (small impact).
7. **Donation list READ can loop forever.** The insertion sort at `StdClanDonationList.java:132-142` has no `break` after `insertElementAt`. Once a row is smaller than an element already sorted, the same element is re-inserted at each following index, the vector grows, and the loop never ends. This needs rows out of timestamp order, and `PDataLoader.DBRead` has no `ORDER BY` (`core/database/PDataLoader.java:86`), so the risk depends on the database's row order (unverified).
8. **Castle breaches are approximate.** Siege damage is converted to whole percent (`StdSiegableBoardable.java:1454`, and the percent is subtracted at line 1476). `GenClanCastle` then rebuilds hull points from the last tick's percent (`GenClanCastle.java:208-211`) and updates `lastPctHealth` every tick (line 240). Damage below 0.5% of the hull does nothing at all, and damage is not accumulated across ticks. A breach needs at least 50 points in one tick (`newHoles = hullPtsLost/50`, line 214).
9. **Castle door state changed during a check.** `GenClanCastle.java:133-135` sets `isOpen` to true, calls the parent's `okMessage`, then sets it back to false. A check should not change state.
10. **Destination override lasts two seconds.** `GenClanCastle.java:160` sets `targetExpire` only when a breach is used, and `getDestinationRoom` (lines 189-197) returns `targetRoom` for those two seconds. `targetRoom` is never cleared. It is simply ignored once `targetExpire` has passed.
11. **Tabbard experience return value discarded.** `StdClanItem.java:492` ignores what `applyExpMods` returns (the exp left for the member, `DefaultClan.java:2611-2630`). `CoffeeLevels.java:611` uses that return value. Whether the wearer keeps the full gain is unverified.
12. **Non-flag recipes are typed FLAG.** Ordinal 0 is FLAG, and the Home Compass, Ship Compass and Journal rows put 0 in the type column (`resources/skills/clancraft.txt`). `ClanCrafting.java:623-631` applies that value. The compasses are GenClanItem, so they use `StdClanItem.genericName`. For a FLAG item that returns "a flat" (`StdClanItem.java:80-81`) whenever the name lacks a leading article (lines 67-69). A Home Compass is named "Home Compass of ...", so players are likely to see "a flat". The FLAG capture-reward test (line 526) also matches compasses.
13. **Typos and grammar that players see.** "an flag" (`StdClanFlag.java:56`); "an list is setting here." (`StdClanDonationList.java:52`); "an captains sailors cap" (`StdClanSailorsCap.java:57`); "an workers item" and "an workers container" (`StdClanCommonItem.java:54`, `StdClanCommonContainer.java:52`).
14. **Null-check ordering.** `StdClanItem.java:164-167`, `StdClanArmor.java:123-126` and `StdClanContainer.java:118-121` call `R.moveItemTo` before testing `R != null`. The null test two lines later is inconsistent. Eviction runs on every half hour, so a missing room could throw a NullPointerException (not triggered in testing; unverified).
15. **Duplicated expiry code.** The same eviction block appears in five classes with intervals of half an hour (`StdClanItem`, `StdClanArmor`) and an hour (`StdClanFlag`, `StdClanSailorsCap`, `StdClanContainer`). It should be one helper.
16. **StdClanArmor drops the parent tick.** `StdClanArmor.java:142-147` returns `!amDestroyed()` and never calls `super.tick`. What the skipped `StdArmor` tick would have done was not checked.
17. **Dead store and copies.** `StdClanFlag.java:330` assigns `relation` and then reassigns it at line 338. `StdClanCommonItem.java:266, 304, 336, 361` copy the loop index into `rand` for no reason. The donation list's `text();` (`StdClanDonationList.java:76`) is a no-op call.
18. **Shared field overload.** On worker containers, `readableText` is both the skill to perform (`StdClanCommonContainer.java:70, 79`) and the key name (`GenClanCommonContainer.java:72-81`, `keyName` returns `readableText`). Changing the key name changes the skill.
19. **Broken badge check.** `Behaviors/Conquerable.java:962-979` finds an officer emblem (LEGALBADGE) on an ordering player, then has an empty `if (badge==null) { }` (lines 978-980) and never uses the badge. No recipe makes a badge. This belongs to bucket Behaviors, but the clan items depend on it.
20. **Unclear declaration gate.** The declaration phrase needs CMDROOMS (`StdClanFlag.java:185`), which is a staff security flag. The documentation does not describe the phrase (`guides/ArchonGuide.html` has no entry for it).
21. **Static mutable state.** `needChart` (`StdClanCommonItem.java:43`) is a shared static map of PairLists, keyed by Area and never cleared. It is not synchronised, and the tick code reads and changes it without any lock.
22. **Multiplayer assumptions throughout.** Rivalrous clans, war, guard NPCs, clan-friendly group leaders, the "destroyed by your touch" compensation and the propaganda spread all assume several competing human clans.
23. **Mislabelled data.** "Boatwrights Tools" stores the readable text "BoatWrighting" (`resources/skills/clancraft.txt`). `CMClass.getAbility` searches the alphabetised ability list with a case-insensitive comparison (`core/CMClass.java:1194-1196`, `2054-2080`), and no skill is named "BoatWrighting" (the skill is "Boatwright", `Abilities/Common/Boatwright.java:40`). So the tool's tick finds no skill and does nothing (`StdClanCommonItem.java:243-244`). "Leatherworking" resolves correctly through the ignore-case fallback.
24. **Ordinal coupling.** The ClanItemType order is baked into data (`ClanItem.java:48`, `ClanCrafting` column 5, and the recipe's ordinal in the table above). Reordering the enum silently changes every recipe.

## Takeaways for a Python SUD

- **Ownership by faction, not by clan object.** Give each item an optional `faction_id` and a `rightful_owner` (an NPC id). Drop the membership-rank bookkeeping, which a single player does not need. Keep the rule "an outsider who takes a faction item loses it and the faction gains standing", but make the gain a reputation change rather than experience.

  ```python
  @dataclass
  class FactionItem:
      name: str
      kind: ItemKind              # enum, stored by name, never by ordinal
      faction: str | None         # None means an ordinary item
      rightful_owner: str | None  # npc id to return to when found on the floor
  ```
- **Recipes as data with validation.** Use a TOML or CSV table with named columns (`name, level, ticks, materials, kind, cls, standing_cost, misc`). Parse materials as a dict, so `"COTTON/1 OAK/1"` becomes `{"cotton": 1, "oak": 1}`, and check at load time that every column name and skill ID exists. Several bugs above (the wrong column, the typo'd cache key, the mismatched skill ID) would have failed such a check.
- **Need-chart for NPC workers.** Replace the static Area-keyed map with `need_chart: dict[str, dict[str, set[str]]]` (area id, then NPC id, then missing materials). Run it from the same scheduler that drives the living world. A single player can see the result, so the chart can be simple. Skip the "track to peer" pathing unless the world needs it.
- **Propaganda and membership as daily events.** A pamphlet that copies itself into a neighbour once per in-game day is a cheap, legible way to make a world feel alive. Model the day counter explicitly and use `random.choice` for targets, not a room scan.
- **Strength-decay effort.** The signpost counter (`counter -= strength`, reset after a quiet period) is a good single-player puzzle with no multiplayer state. Keep it as a per-object counter with a timestamp.
- **Capture and touch rules.** Keep one place for "may this actor take this item", returning a reason string the UI can show. Apply it to every item class; the inconsistency in Problems item 5 is easy to avoid.
- **Siege as floats.** If a castle or fort appears, track hull as a float and accumulate damage across turns. Do not round to whole percent, and do not reset the baseline each tick.
- **Donation log.** Store the journal as a list of dicts with a float timestamp, and sort with `sorted(entries, key=...)`. That removes the insertion-sort hazard, and the player-keyed database sections are not needed in single-player.
- **Drop for single-player.** Clan-vs-clan war checks, rivalrous-clan flags, guard NPCs that block a rival, the group-leader friendliness test on castle entry, the Archon-style staff bypasses (CMDITEMS, CMDROOMS), the database sections, and the Gen XML persistence layer. Keep the idea of a typed, editable item and drop the infrastructure.
- **Turn-based or real-time.** The 4-second tick and the 1-to-7-tick worker timer translate directly to a turn counter. A worker that acts every 1d7 turns is a simple, readable rule.

## Coverage

Read in full (imports and licence headers skipped): `Items/ClanItems/StdClanItem.java`, `StdClanCommonItem.java`, `StdClanFlag.java`, `GenClanCastle.java`, `GenClanItem.java`, `GenClanSpecialItem.java`, `StdClanSpecialItem.java`, `StdClanSailorsCap.java`, `StdClanSignpost.java`, `StdClanConcierge.java`, `StdClanDonationList.java`, `StdClanPamphlet.java`, `StdClanCard.java`, `StdClanApron.java`, `StdClanArmor.java`, `StdClanTabbard.java`, `StdClanContainer.java`, `StdClanCommonContainer.java` (18 files).

Skimmed (diffed against the parent class or against `GenClanItem.java`, method signatures listed, and the stat-code arrays checked): `GenClanApron.java`, `GenClanArmor.java`, `GenClanCard.java`, `GenClanCommonContainer.java`, `GenClanCommonItem.java`, `GenClanConcierge.java`, `GenClanContainer.java`, `GenClanDonationList.java`, `GenClanFlag.java`, `GenClanPamphlet.java`, `GenClanSailorsCap.java`, `GenClanSignpost.java`, `GenClanTabbard.java` (13 files). Each is a copy of its parent with a different name, display text, default material and stat-code list. The only behavioural differences found are in `setReadableText` (GenClanCommonItem sets the glow, which matches the parent) and in `GenClanSpecialItem` (see Problems item 3).

Skipped: none. Outside the bucket, the chapter read only the lines listed in the Key files table, plus `resources/skills/clancraft.txt` in full. The NPC behaviours (`GoodExecutioner`, `EvilExecutioner`, `TaxCollector`, `Concierge`), the Merchant and Skill_Track abilities, and `Behaviors/Conquerable.java` as a whole were not read and are left to their own buckets.
