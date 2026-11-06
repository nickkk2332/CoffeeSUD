# Properties reference

A property is a permanent rule you hang on a room, area, exit, mob or item: "only elves may enter", "this lever keeps its state", "whoever wears this gains +2 strength", "no teleporting into here". This chapter explains how properties work and how to attach them, lists the shared parameter conventions, groups every property by purpose, and then gives one reference entry for each of the 238 `Prop_*` classes (plus the other property-type abilities that live in `Abilities/Misc`). It serves world creators (the effects editor and world files), scripters (adding properties from scripts and JavaScript) and engine developers (what each class reads and where). Entries are headed with the exact class ID so other chapters can link to them, for example [`Prop_ReqEntry`](#prop_reqentry).

Source: [Abilities/Properties](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/) and [Abilities/Misc](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Misc/); shipped help: [arc_properties.ini](../../CoffeeMud/resources/help/arc_properties.ini) (also reachable in-game as `AHELP Prop_ReqEntry`), with the newer CoffeeSUD travel, language and presentation properties in `arc_travel.ini` and `arc_language_authoring.ini`. [tool-reference.md](tool-reference.md) is generated from the same help.

## In this chapter

- [How properties work](#how-properties-work)
- [Attaching properties](#attaching-properties)
- [Parameter conventions](#parameter-conventions)
- [Choosing a host](#choosing-a-host)
- [Properties by purpose](#properties-by-purpose)
- [Reference: Prop_* properties](#reference-prop_-properties)
- [Reference: property-type abilities in Abilities/Misc](#reference-property-type-abilities-in-abilitiesmisc)
- [Worked examples](#worked-examples)
- [Persistence](#persistence)
- [Troubleshooting](#troubleshooting)
- [Audit notes and known mismatches](#audit-notes-and-known-mismatches)
- [See also](#see-also)

## How properties work

**What they are.** A property is an Ability whose classification is `ACODE_PROPERTY`. Almost all of them extend the `Property` base class (a thin ability: it has no command word, no cost and no proficiency, and nobody "learns" it). A few property-type classes outside the `Prop_*` family (the entries in the Abilities/Misc section) extend `StdAbility` instead and behave like permanent status effects.

**How they run.** A property is put on a host with `addNonUninvokableEffect`, so it is an effect that never expires and cannot be cured or dispelled in play. Once on a host, the property takes part in three engine mechanisms:

- *Stat recalculation.* The host's `affectPhyStats`, `affectCharStats` and `affectCharState` run through all its effects, so a property can change armour, senses, strength, hit points and so on. Adjusters, resisters and the Wear/Have/Ride/Use families work this way.
- *Message flow.* Every game action is a message that goes through each effect's `okMessage` (which can veto it) and then `executeMsg` (which can react). Entry restrictions, locks, no-teleport rules, emote triggers and quest hooks are message filters.
- *Ticking.* Most properties do not tick. A few that need time (hazards, hidden/invisible timers, transformations, broken items) start their own tick-down when they are attached.

**Timed versus permanent.** Spells and skills put *timed* effects on things: they have a duration and `unInvoke` when it runs out. Properties are the *uninvokable* kind: they stay until a builder removes them (or, for a few, until the property removes itself, as Prop_Broken does once an item is mended, or Prop_Downed when its time is up). Anything described in a property's help as "WHEN" or "HOURS" is the property reading the clock itself, not the effect timing out.

**What they are not.** Behaviors (chapter 24) are separate objects with their own tick and are added in a different editor field. Scripts (chapter 04) are behaviours. Properties are best for static rules and message filters; behaviours for actions an NPC takes on its own.

**Which hosts.** Each class declares the kinds of host it can affect (`canAffectCode`): mobs, items, rooms, areas, exits. Every entry below shows the hosts from the code. The effects editor's `?P` option lists only the properties that declare a host type fitting the thing you are editing; a few old classes (noted in their entries) declare none, so they do not appear in `?P`, but can still be added by typing the class ID.

## Attaching properties

### With the effects editor

Every generic editor for mobs, items, exits, rooms and areas has an **Effects** field (races and player records have one too). Open the editor with the usual command (`MODIFY ROOM`, `MODIFY MOB name`, `MODIFY ITEM name`, `MODIFY EXIT direction`, `MODIFY AREA`; see chapters 20-22 for the commands) and choose the Effects field by its number. It shows the current list and prompts:

```text
Enter an effect to add, -ID to remove, =ID or its number to edit, ?P for the properties that fit (?)
:
```

| You type | Effect |
|---|---|
| a class ID such as `Prop_ReqEntry` | Adds that effect; you are then asked for its parameters. |
| `-Prop_ReqEntry` | Removes that effect. |
| `=Prop_ReqEntry` or the effect's number in the list | Edits the parameters of an effect already there. |
| `?P` | Lists the properties and Abilities/Misc effects that fit this host, with the start of each one's help. |
| `?` | Lists every ability, with those that can affect this host highlighted. |
| (nothing) | Leaves the field. |

The parameter prompt reads `Enter any effect parameters (?)` and shows the current text after the colon. Type the new parameter text and press Enter; press Enter on an empty line to keep the current text; type `null` to clear it; type `?` to see the property's help. Adding a class that is already on the host asks whether to wrap both copies in a [Prop_MultiEffects](#prop_multieffects) (a second copy would otherwise be ignored). Archon-domain abilities can be added only by someone who has the ability themselves.

Only effects flagged savable are listed and saved, so internal, engine-made effects (for example [Prop_Playtest](#prop_playtest)) never appear in the editor.

### In world files

World files store effects as XML. Each host with effects has an `AFFECS` block of `AFF` entries, each holding the class (`ACLASS`) and the parameter text (`ATEXT`, with `<` and `>` escaped):

```xml
<AFFECS><AFF><ACLASS>Prop_Smell</ACLASS><ATEXT>stinky</ATEXT></AFF></AFFECS>
```

Rooms keep this inside their `RTEXT` field in the `.cmare` area file (see `world/areas/new-area.cmare`), mobs and items inside the matching `MOB`/`ITEM` records, so a hand-edited world file can add any property. The loader skips an unknown class with an error in the log ("Unknown affect ...") and keeps the rest. Chapter 02 describes the file formats.

### From scripts and JavaScript

A property is an ordinary Ability object, so any script that can reach the host can attach one. In JavaScript (the same API the test fixtures use):

```js
var P = CMClass.getAbility("Prop_ReqEntry");
P.setMiscText("\"message=Elves only.\" -race +elf");
room.addNonUninvokableEffect(P);
```

`host.fetchEffect("Prop_ReqEntry")` finds it again and `host.delEffect(P)` removes it. Set the parameter text with `setMiscText` (it can be called again later to re-parse).

In a MOBPROG, `MPAFFECT [ID] [target] [parms]` adds a property-type ability to the target as a permanent effect (`MPAFFECT Prop_Adjuster $n str+1`). Starting the parameters with a number immediately followed by `TICKS` (`MPAFFECT Prop_Adjuster $n 50TICKS str+1`) makes it temporary through the `TemporaryAffects` effect, and prefixing the ID with `property:` forces any ability to be added as a permanent effect. `MPUNAFFECT [target] [ID or ALL]` removes it. `MPBEHAVE` is for behaviours, not properties.

### Other places effects are accepted

Several definition formats take a list of effects in the form `ID=Prop_X PARMS="..."`: generic races and classes (their EFFECTS fields), achievement rewards, factions (`PLAYEREFFECT` keys, see [Prop_FactionEffects](#prop_factioneffects)) and abilities. Those chapters give the exact field syntax; the effects behave as described here.

### Checking your work

`PROPCHECK [ROOM|AREA [name]|ALL|CATALOG|target]` re-reads every effect's text and lists what would do nothing or not what you meant: unknown keys, unfinished quotes, duplicate settings, invalid values and unresolved references. `AHELP <ID>` shows the shipped help (with a type and range schema for properties that have one). `LIST PROPERTIES` lists all property classes. `WORLD CHECK` includes the property domains.

## Parameter conventions

Properties have no common parameter format: each class parses its text its own way. In practice there are four styles. The entry for each property says which it uses.

1. **KEY=value pairs** (the commonest). Read with the engine's forgiving parser:
   - Keys are case-insensitive. A key matches if it is the start of a word, so `CHANCE` finds `CHANCE=40` but a key preceded by letters is skipped.
   - A value is either a bare word (ending at a space, `;` or `:`) or a double-quoted string (`MSG="You cannot pass."`). Quote any value that has spaces, semicolons or colons. Inside quotes, `\"` is a literal quote.
   - If the key is followed by `+` or `-` before any `=` the key counts as absent.
   - Numbers are plain; "percent" parameters are usually `40` or `40%` as the entry says.
   - Booleans are `TRUE`/`FALSE` (sometimes bare flag words, as `SHOWNAME`).
2. **Stat lists**: `str+2 dex-2 move+100 class=thief`. The adjuster, resister and enabler families share this grammar (see [Prop_Adjuster](#prop_adjuster), [Prop_Resistance](#prop_resistance), [Prop_HaveEnabler](#prop_haveenabler), [Prop_HaveSpellCast](#prop_havespellcast)).
3. **Mask-led text**: many access properties are "options then a zapper mask": `nosneak nofollow -race +elf`. The mask is everything that is not an option word (or is introduced by `MASK=`). The mask language is chapter 24; `AHELP ZAPPERMASK` has the short form.
4. **Strict authoring options** (newer CoffeeSUD properties; see [Authoring option syntax](#authoring-option-syntax)).

Other conventions you will meet:

- `[ ]` in a parameter line is a placeholder; `( )` is optional; `/` or `|` is a choice.
- A list of spells or abilities is usually `;`-separated and each ability may take its own text in parentheses: `Spell_Bless;Prayer_Cure(Light Wounds)`.
- In the spell-casting families (Have/Wear/Ride/Here/Use/FightSpellCast, SpellAdder) a leading `NN%` entry sets the chance (`90%;Spell_Portal(Somewhere else)`), and the flags `NOUNINVOKE`, `LEVEL=[N]`, `MAXTICKS=[N]` and `TICKS=[N]` can stand in the list in place of a spell; each entry says which it supports.
- Item-set and item-balancer helpers (`TONEUP`, `TONEDOWN`, `STAT-LEVEL`) are read by the item balancer for some property classes; they are not meant to be typed by hand.
- Text parameters shown to players accept the usual substitutions of the message they appear in (`<S-NAME>`, `<T-NAME>`, `$n` and so on) only where the entry says so.

### Authoring option syntax

The newer CoffeeSUD authoring properties (presentation, language and travel) share a strict parser (`AuthoringOptions`):

- Tokens are separated by spaces and semicolons; a token is `KEY=value` or a bare flag word.
- A double-quoted value may contain spaces, semicolons and text that looks like settings: `SOUND="A label says CHANCE=0; someone whispers \"wait\"." CHANCE=100` has only one setting `CHANCE=100` outside the quotes. Inside a value `\"` is a literal quote, `\\` a literal backslash, `\;` a semicolon.
- Statement lists (such as the records of [Prop_Details](#prop_details)) are split on `;` only outside quotes.
- Keys are upper-cased; `CHANCE=40%` is read as 40.
- PROPCHECK and the relevant `WORLD CHECK` domains report unfinished quotes, duplicate settings, unknown keys, invalid values and unresolved references. AHELP appends the schema (types and ranges) to properties that have one.

Properties files (`.properties`) keep their own Java escaping as a separate file-format layer.

### Saved identities

[Prop_ShowWhen](#prop_showwhen), [Prop_Senses](#prop_senses), each record of [Prop_Details](#prop_details), [Prop_LanguageImmersion](#prop_languageimmersion), [Prop_FirstEntry](#prop_firstentry) and [Prop_Waypoint](#prop_waypoint) keep saved state (once-only markers, discoveries, exposure counters). They accept:

```text
ID=crypt-relic ALIASES=old-crypt-relic LEGACYKEYS=FOUND_...
```

An explicit `ID` replaces names, locations and prose in the saved key, so renaming, moving or rewording the host keeps the state. Keep it when editing text, chances, gains, masks or display names; give independent sources different IDs (waypoint IDs are scoped to their network; sense clues stay separate for SOUND, TOUCH and TASTE). Put a detail ID after its description:

```text
inscription,runes=Ancient letters. ID=crypt-inscription SETVAR=READ_RUNES=1
```

`ALIASES` lists previous IDs separated by commas; reading an old marker migrates it to the current ID. `LEGACYKEYS` accepts complete old saved tokens for migrations that also changed a name, room, network or old key shape. Keep aliases in shipped content while players may still restore older saves.

Without an explicit ID the first stateful use adds a saved [Prop_AuthorIdentity](#prop_authoridentity) to the host so later renames, moves and prose edits keep the identity. Copies carrying the same identity share state on purpose; give a copy a new explicit `ID` to make it independent.

## Choosing a host

| Host | Properties that suit it |
|---|---|
| **Room** | Entry rules (`Prop_Req*`, `Prop_BlockedExit`), light and hazard (`Prop_RoomDark`, `Prop_RoomLit`, `Prop_Hazard`), teleport/summon rules (`Prop_NoTeleport`, `Prop_NoSummon`), presentation (`Prop_Details`, `Prop_Senses`, `Prop_Smell`, `Prop_RoomView`), property sale, quests and ambush. |
| **Area** | The same rules applied to every room: `Prop_ReqEntry`, `Prop_NoPKill`, `Prop_Weather`, `Prop_RoomDark`, `Prop_AreaEntry`, `Prop_DeathRules`, `Prop_LocalLists`. Room properties override area ones where they overlap (see each entry). |
| **Exit** | `Prop_ComboLock`, `Prop_LockRules`, `Prop_OpenCommand`, `Prop_ReqEntry`, `Prop_Climbable`, `Prop_Crawlspace`, `Prop_NarrowLedge`, `Prop_TidalExit`, `Prop_Switch`, `Prop_BlockedExit`, `Prop_TravelTime`. |
| **Mob** | Stats and resistances (`Prop_Adjuster`, `Prop_Resistance`, `Prop_StatTrainer`), role (`Prop_Trainer`, `Prop_Familiar`, `Prop_Recruitable`), combat (`Prop_Essential`, `Prop_PartyScaler`, `Prop_Doppleganger`), `Prop_SafePet`, `Prop_PracticeDummy`, `Prop_NPCLines`. |
| **Item** | Everything conferred by wearing, wielding, holding, owning, riding or using (`Prop_Wear*`, `Prop_Have*`, `Prop_Ride*`, `Prop_Use*`), containers (`Prop_Trashcan`, `Prop_IceBox`, `Prop_LimitedContents`, `Prop_ItemSlot`), puzzle items (`Prop_Switch`, `Prop_PressurePlate`, `Prop_ComboLock`), and item rules (`Prop_Artifact`, `Prop_Cursed`, `Prop_Attunement`, `Prop_Durability`). |

Doors are exits; a "portal" or "container" is an item. A property that fits both exits and containers (locks) lists both.

## Properties by purpose

Every property appears once in the groups below (a few are cross-listed in the text of their entries). Click through for the entry.

### Access and entry restrictions

Who may enter a room, area or exit, pass a checkpoint, or stand where.

[Prop_ReqEntry](#prop_reqentry), [Prop_ReqAlignments](#prop_reqalignments), [Prop_ReqClasses](#prop_reqclasses), [Prop_ReqRaces](#prop_reqraces), [Prop_ReqLevels](#prop_reqlevels), [Prop_ReqNoMOB](#prop_reqnomob), [Prop_ReqPKill](#prop_reqpkill), [Prop_ReqStat](#prop_reqstat), [Prop_ReqTattoo](#prop_reqtattoo), [Prop_ReqCapacity](#prop_reqcapacity), [Prop_ReqHeight](#prop_reqheight), [Prop_BlockedExit](#prop_blockedexit), [Prop_MoveRestrictor](#prop_moverestrictor), [Prop_ClosedDayNight](#prop_closeddaynight), [Prop_ClosedSeason](#prop_closedseason), [Prop_Checkpoint](#prop_checkpoint), [Prop_TicketTaker](#prop_tickettaker), [Prop_Discoverable](#prop_discoverable), [Prop_StockZapper](#prop_stockzapper), [Prop_Uncampable](#prop_uncampable)

### Locks, doors, containers and puzzle mechanisms

Combination locks, lock and key rules, remote open/close, pressure plates, switches, containers with rules.

[Prop_ComboLock](#prop_combolock), [Prop_LockRules](#prop_lockrules), [Prop_KeyRules](#prop_keyrules), [Prop_GenLock](#prop_genlock), [Prop_OpenCommand](#prop_opencommand), [Prop_CloseCommand](#prop_closecommand), [Prop_OpenPassword](#prop_openpassword), [Prop_PressurePlate](#prop_pressureplate), [Prop_Switch](#prop_switch), [Prop_Verbs](#prop_verbs), [Prop_Trashcan](#prop_trashcan), [Prop_IceBox](#prop_icebox), [Prop_OutfitContainer](#prop_outfitcontainer), [Prop_LimitedContents](#prop_limitedcontents), [Prop_ItemSlot](#prop_itemslot), [Prop_ItemSlotFiller](#prop_itemslotfiller)

### Money, shops and owned property

Rooms and areas players can buy, inns, homes, what shopkeepers refuse, purchase records.

[Prop_AreaForSale](#prop_areaforsale), [Prop_LotForSale](#prop_lotforsale), [Prop_LotsForSale](#prop_lotsforsale), [Prop_RoomForSale](#prop_roomforsale), [Prop_RoomPlusForSale](#prop_roomplusforsale), [Prop_RoomsForSale](#prop_roomsforsale), [Prop_PrivateProperty](#prop_privateproperty), [Prop_Homestead](#prop_homestead), [Prop_HomeBeacon](#prop_homebeacon), [Prop_InnRoom](#prop_innroom), [Prop_Unsellable](#prop_unsellable), [Prop_Undroppable](#prop_undroppable), [Prop_Purchases](#prop_purchases), [Prop_ItemNoRuin](#prop_itemnoruin)

### Item behaviour, binding and growth

What an item does or is: artifacts, curses, binding, growth, durability, transformation, sets, uses.

[Prop_Artifact](#prop_artifact), [Prop_Attunement](#prop_attunement), [Prop_Cursed](#prop_cursed), [Prop_Detector](#prop_detector), [Prop_Invokable](#prop_invokable), [Prop_ItemBinder](#prop_itembinder), [Prop_ItemGrowth](#prop_itemgrowth), [Prop_ItemSet](#prop_itemset), [Prop_ItemModifiers](#prop_itemmodifiers), [Prop_Durability](#prop_durability), [Prop_Broken](#prop_broken), [Prop_Transform](#prop_transform), [Prop_LimitedEquip](#prop_limitedequip), [Prop_LimitedItems](#prop_limiteditems), [Prop_LocationBound](#prop_locationbound), [Prop_Furnishing](#prop_furnishing), [Prop_QuestItem](#prop_questitem), [Prop_UpgradePath](#prop_upgradepath), [Prop_ScrapExplode](#prop_scrapexplode), [Prop_SalvageYield](#prop_salvageyield), [Prop_NoCraftability](#prop_nocraftability), [Prop_StayAboard](#prop_stayaboard), [Prop_Retainable](#prop_retainable), [Prop_Workstation](#prop_workstation), [Prop_Summoned](#prop_summoned), [Prop_ItemTransporter](#prop_itemtransporter), [Prop_ItemTransReceiver](#prop_itemtransreceiver)

### Wearing, holding and riding (item-conferred powers)

Powers given while an item is worn, held, owned, used, or ridden.

[Prop_WearAdjuster](#prop_wearadjuster), [Prop_WearEnabler](#prop_wearenabler), [Prop_WearResister](#prop_wearresister), [Prop_WearSpellCast](#prop_wearspellcast), [Prop_WearZapper](#prop_wearzapper), [Prop_WearOverride](#prop_wearoverride), [Prop_HelmRestrict](#prop_helmrestrict), [Prop_HaveAdjuster](#prop_haveadjuster), [Prop_HaveEnabler](#prop_haveenabler), [Prop_HaveResister](#prop_haveresister), [Prop_HaveSpellCast](#prop_havespellcast), [Prop_HaveZapper](#prop_havezapper), [Prop_RideAdjuster](#prop_rideadjuster), [Prop_RideEnabler](#prop_rideenabler), [Prop_RideResister](#prop_rideresister), [Prop_RideSpellCast](#prop_ridespellcast), [Prop_RideZapper](#prop_ridezapper), [Prop_UseAdjuster](#prop_useadjuster), [Prop_UseSpellCast](#prop_usespellcast), [Prop_UseSpellCast2](#prop_usespellcast2), [Prop_UseEmoter](#prop_useemoter), [Prop_UseEmoter2](#prop_useemoter2), [Prop_FightSpellCast](#prop_fightspellcast), [Prop_AddDamage](#prop_adddamage), [Prop_AbsorbDamage](#prop_absorbdamage), [Prop_WeaponImmunity](#prop_weaponimmunity), [Prop_WeaponMessages](#prop_weaponmessages), [Prop_AirSupply](#prop_airsupply), [Prop_NoDamage](#prop_nodamage)

### Stats, resistances and effect control

Adjusting stats, resistances, immunities, and how long effects last.

[Prop_Adjuster](#prop_adjuster), [Prop_StatAdjuster](#prop_statadjuster), [Prop_StatTrainer](#prop_stattrainer), [Prop_EnterAdjuster](#prop_enteradjuster), [Prop_HereAdjuster](#prop_hereadjuster), [Prop_CombatAdjuster](#prop_combatadjuster), [Prop_CombatStats](#prop_combatstats), [Prop_Resistance](#prop_resistance), [Prop_AbilityImmunity](#prop_abilityimmunity), [Prop_SpellReflecting](#prop_spellreflecting), [Prop_MagicFreedom](#prop_magicfreedom), [Prop_AffectReTick](#prop_affectretick), [Prop_ShortEffects](#prop_shorteffects), [Prop_Fumble](#prop_fumble), [Prop_RestrictSkills](#prop_restrictskills), [Prop_RestrictSpells](#prop_restrictspells), [Prop_MultiEffects](#prop_multieffects), [Prop_SpellAdder](#prop_spelladder), [Prop_HereEnabler](#prop_hereenabler), [Prop_HereSpellCast](#prop_herespellcast), [Prop_Senses](#prop_senses), [Prop_Hidden](#prop_hidden), [Prop_Invisibility](#prop_invisibility), [Prop_WizInvis](#prop_wizinvis), [Prop_TattooAdder](#prop_tattooadder), [Prop_Tattoo](#prop_tattoo), [Prop_FactionEffects](#prop_factioneffects)

### Combat, encounters and death

Ambushes, surprise, telegraphs, party scaling, death rules, safe and dummy targets.

[Prop_Ambush](#prop_ambush), [Prop_Surprised](#prop_surprised), [Prop_Telegraph](#prop_telegraph), [Prop_PartyScaler](#prop_partyscaler), [Prop_Doppleganger](#prop_doppleganger), [Prop_Downed](#prop_downed), [Prop_Essential](#prop_essential), [Prop_DeathRules](#prop_deathrules), [Prop_InstantDeath](#prop_instantdeath), [Prop_NewDeathMsg](#prop_newdeathmsg), [Prop_PracticeDummy](#prop_practicedummy), [Prop_SafePet](#prop_safepet), [Prop_PeaceMaker](#prop_peacemaker), [Prop_NoPKill](#prop_nopkill), [Prop_SparringRoom](#prop_sparringroom), [Prop_Trainer](#prop_trainer), [Prop_Familiar](#prop_familiar), [Prop_AstralSpirit](#prop_astralspirit), [Prop_ModExperience](#prop_modexperience), [Prop_ModFaction](#prop_modfaction), [Prop_MobRank](#prop_mobrank), [Prop_Affinity](#prop_affinity), [Prop_LootTable](#prop_loottable), [Prop_RejuvWhen](#prop_rejuvwhen)

### Environment and hazards

Light and dark, hazards, air, weather, time zones, terrain.

[Prop_RoomDark](#prop_roomdark), [Prop_RoomLit](#prop_roomlit), [Prop_DarkDanger](#prop_darkdanger), [Prop_Hazard](#prop_hazard), [Prop_Weather](#prop_weather), [Prop_SkyText](#prop_skytext), [Prop_TimeMode](#prop_timemode), [Prop_TidalExit](#prop_tidalexit), [Prop_NarrowLedge](#prop_narrowledge), [Prop_WeakBridge](#prop_weakbridge), [Prop_Climbable](#prop_climbable), [Prop_Crawlspace](#prop_crawlspace), [Prop_EnlargeRoom](#prop_enlargeroom), [Prop_EnlargeRoom](#prop_enlargeroom), [Prop_RoomUnmappable](#prop_roomunmappable), [Prop_Road](#prop_road), [Prop_Yield](#prop_yield), [Prop_CropRules](#prop_croprules), [Prop_ImproveGather](#prop_improvegather), [Prop_CraftXP](#prop_craftxp), [Prop_CommonTwister](#prop_commontwister)

### Presentation, text and sound

Descriptions, smells, sounds, views, map glyphs, lore, emotes.

[Prop_Lore](#prop_lore), [Prop_Details](#prop_details), [Prop_DarkDescription](#prop_darkdescription), [Prop_FirstEntry](#prop_firstentry), [Prop_AreaEntry](#prop_areaentry), [Prop_AreaBanner](#prop_areabanner), [Prop_Smell](#prop_smell), [Prop_Sounder](#prop_sounder), [Prop_MOBEmoter](#prop_mobemoter), [Prop_ShowWhen](#prop_showwhen), [Prop_RoomView](#prop_roomview), [Prop_RoomWatch](#prop_roomwatch), [Prop_TransitView](#prop_transitview), [Prop_MapGlyph](#prop_mapglyph), [Prop_MapReveal](#prop_mapreveal), [Prop_NPCLines](#prop_npclines), [Prop_Socials](#prop_socials), [Prop_LocalLists](#prop_locallists)

### Travel, journeys and teleport rules

Waypoints, ports, journeys, teleport and recall rules, redirects.

[Prop_Waypoint](#prop_waypoint), [Prop_Port](#prop_port), [Prop_TravelTime](#prop_traveltime), [Prop_Journey](#prop_journey), [Prop_JourneyState](#prop_journeystate), [Prop_JourneyPassenger](#prop_journeypassenger), [Prop_Transporter](#prop_transporter), [Prop_RoomRedirect](#prop_roomredirect), [Prop_NoTeleport](#prop_noteleport), [Prop_NoTeleportOut](#prop_noteleportout), [Prop_NoSummon](#prop_nosummon), [Prop_NoRecall](#prop_norecall), [Prop_NoSave](#prop_nosave), [Prop_NoChannel](#prop_nochannel), [Prop_NoTelling](#prop_notelling), [Prop_NoCharm](#prop_nocharm), [Prop_NoOrdering](#prop_noordering), [Prop_NoPurge](#prop_nopurge)

### Languages

Languages spoken, translated and immersed in.

[Prop_LangTranslator](#prop_langtranslator), [Prop_LanguageSpeaker](#prop_languagespeaker), [Prop_LanguageImmersion](#prop_languageimmersion), [Prop_Monolingual](#prop_monolingual)

### Quests, story and progress

Quest hooks, boards, logs, discovery, markers and story records.

[Prop_QuestGiver](#prop_questgiver), [Prop_QuestBoard](#prop_questboard), [Prop_QuestLog](#prop_questlog), [Prop_QuestMarker](#prop_questmarker), [Prop_StoryRecords](#prop_storyrecords), [Prop_Discovery](#prop_discovery), [Prop_Respec](#prop_respec), [Prop_ReRollStats](#prop_rerollstats), [Prop_Recruitable](#prop_recruitable), [Prop_KnownRecipes](#prop_knownrecipes), [Prop_PlayerTemplate](#prop_playertemplate)

### Saving, testing and authoring support

Save points, auto-save, scripts, property setters, playtest, dev cheats, author identity.

[Prop_AutoSave](#prop_autosave), [Prop_SavePoint](#prop_savepoint), [Prop_JScript](#prop_jscript), [Prop_PropSetter](#prop_propsetter), [Prop_When](#prop_when), [Prop_Playtest](#prop_playtest), [Prop_DevCheats](#prop_devcheats), [Prop_AuthorIdentity](#prop_authoridentity), [Prop_TweakWatch](#prop_tweakwatch)

### Legacy and clan

Inherited properties kept for old content.

[Prop_ClanEquipment](#prop_clanequipment)

Property-type abilities outside the `Prop_` family (Addictions, Age, Mood and others) are in [the Misc reference](#reference-property-type-abilities-in-abilitiesmisc).

## Reference: Prop_* properties

One entry per class, in alphabetical order. **Hosts** come from the class (`canAffectCode`); where the shipped help names the targets differently the help wording is added in brackets. **Parameters** and **Example** are the shipped help (checked against the parsing code; see the audit notes); the text that follows describes behaviour. "CoffeeSUD:" in a description marks a CoffeeSUD addition.

### Prop_AbilityImmunity

**Hosts:** mobs, items, rooms, exits, areas (shipped help: Anything)

**Parameters:**

```text
(OWNER;) (WEARER;)list of abilities with optional error string, ';' delimited
```

**Examples:**

```text
OWNER;Disease_Cold;Disease_Flu;Spell_Fireball
Disease_Cold=<T-NAME> is immune to <O-NAME>;Spell_Fireball=<T-NAME> can't be burnt!
```

Makes the mob, item, or whatever immune to the listed disease, spell or similar affects. Items will pass their immunity on to their owners if the OWNER flag is present, or the wearer (an item worn rather than just carried) if the WEARER flag is present.

Notes:

- `OWNER`/`WEARER` forms (the owner, or the owner while wearing the item) now work as documented.

### Prop_AbsorbDamage

**Hosts:** mobs, items (shipped help: Items, Mobs, Areas, Rooms)

**Parameters:**

```text
Damage Absorption percentages or amounts, the list of absorbed damage types
```

**Examples:**

```text
50% +ALL -SILVER -CLOTH -LEATHER
5 +ALL -MAGIC -SLASHING -FROSTING
25% +ALL -LEVEL15
15% +BASHING 1 +IRON 3 +STEEL 15% +MAGIC
40% +BURNING SOURCEMASK="-RACECAT +Dragon"
```

CoffeeSUD: SOURCEMASK="\[ZAPPER MASK\]" makes it work only against the attackers it fits.

Whenever the item with this property is worn, or for the mob with this property, or anyone in the area or room with this property, they will attain damage absorption from the types and/or materials of weapons specified. Damage absorption may be represented as a percentage or as a straight amount. Damage amounts must be before the type of damage absorbed in the parameters. Damage amounts/percentages in the negative will add to the damage. If the term "ENHANCED" is included in the parameters, and percentage absorption amounts are used, then this will also attempt to auto-save against skills according to the percentage change, or, if the percentage is negative, make the effect last longer. Here are the parameters:

+ALL - Specifies that, by default, the mob has ALL damage absorbed.

-\[DAMAGETYPE\]- Creates an exception to the +ALL rule by permitting normal damage of the given type. Examples for DAMAGETYPE would be -PIERCING, -GASSING, -SLASHING, -BURNING, -MELTING, -NATURAL, -BASHING, etc.

+\[DAMAGETYPE\]- Can be used instead of +ALL to absorb damage from the given damage type. Examples for DAMAGETYPE would be +PIERCING, +GASSING, +SLASHING, +BURNING, +MELTING, +NATURAL, +BASHING, etc.

-\[WEAPONCLASS\]- Creates an exception to the +ALL rule by permitting normal damage of the given class. Examples for WEAPONCLASS would be -AXE, -BLUNT, -EDGED, -FLAILED, -HAMMER, -KARATE, -POLEARM, -RANGED, -SWORD, -DAGGER, -STAFF, -THROWN, etc.

-\[WEAPONCLASS\]- Can be used instead of +ALL to absorb damage from the given weapon class. Examples for WEAPONCLASS would be +AXE, +BLUNT, +EDGED, +FLAILED, +HAMMER, +KARATE, +POLEARM, +RANGED, +SWORD, +DAGGER, +STAFF, +THROWN, etc.

-\[ABILITYCODE\]- Creates an exception to the +ALL rule by permitting normal damage of the given type of ability. Examples for ABILITYCODE would be -THIEF_SKILL, -PRAYER, etc. Do list abilitycodes.

+\[ABILITYCODE\]- Can be used instead of +ALL to absorb damage from the given type of ability. Examples for ABILITYCODE be +SPELL, +SONG, etc.. Do list abilitycodes.

-\[ABILITYDOMAIN\]- Creates an exception to the +ALL rule by permitting normal damage of the given domain of ability. Examples for ABILITYDOMAIN would be -ILLUSION, -DEATH_LORE, etc. Do list abilitydomains.

+\[ABILITYDOMAIN\]- Can be used instead of +ALL to absorb damage from the given domain of ability. Examples for ABILITYDOMAIN be +SPELL, +SONG, etc.. Do list abilitydomains.

-\[ABILITYFLAG\]- Creates an exception to the +ALL rule by permitting normal damage of the given flagged abilities. Examples for ABILITYFLAG would be -UNHOLY, -CHARMING, etc. Do list abilityflags.

+\[ABILITYFLAG\]- Can be used instead of +ALL to absorb damage from the given flagged abilities. Examples for ABILITYDOMAIN be +HOLY, +CHARMING, etc.. Do list abilityflags.

-\[ABILITYID\]- Creates an exception to the +ALL rule by permitting normal damage of the given specific ability. Examples for ABILITYID would be -SPELL_FIREBALL, etc.

+\[ABILITYID\]- Can be used instead of +ALL to absorb damage from the given specific ability. Examples for ABILITYID be +SPELL_MAGICMISSILE, etc..

-MAGIC - Creates an exception to the +ALL rule by permitting normal damage from magical spells and magical weapons.

+MAGIC - If +ALL is not used, this will absorb damage from magical spells and weapons.

-LEVELX - Creates an exception to the +ALL rule by permitting normal damage from weapons whose level is X or better. Example: -LEVEL1, -LEVEL5, -LEVEL10, etc.

+LEVELX - Can be used instead of +ALL to absorb damage from weapons whose level is X or lower. Example: +LEVEL1, +LEVEL5, +LEVEL10, etc.

-MATERIALTYPE - Creates an exception to the +ALL rule above by permitting normal damage from a weapon made of the given MATERIALTYPE. Where MATERIALTYPE may be any valid material, such as SILVER, WOOD, COTTON, etc. As for the damage types above, +MATERIALTYPE may be used instead of +ALL to absorb damage from materials a given material.

-PARALYSIS - Creates an exception to the +All rule by permitting normal damage from paralysis spells.

+PARALYSIS - If +All is not used, this absorbs damage from damage from fire spells.

-FIRE - Creates an exception to the +All rule by permitting normal damage from fire spells.

+FIRE - If +All is not used, this absorbs damage from damage from fire spells.

-COLD - Creates an exception to the +All rule by permitting normal damage from cold spells.

+COLD - If +All is not used, this absorbs damage from damage from cold spells.

-WATER - Creates an exception to the +All rule by permitting normal damage from water spells.

+WATER - If +All is not used, this absorbs damage from damage from water spells.

-GAS - Creates an exception to the +All rule by permitting normal damage from gas spells.

+GAS - If +All is not used, this absorbs damage from damage from gas spells.

-MIND - Creates an exception to the +All rule by permitting normal damage from mind spells.

+MIND - If +All is not used, this absorbs damage from damage from mind spells.

-JUSTICE - Creates an exception to the +All rule by permitting normal damage from justice spells.

+JUSTICE - If +All is not used, this absorbs damage from damage from justice spells.

-ACID - Creates an exception to the +All rule by permitting normal damage from acid spells.

+ACID - If +All is not used, this absorbs damage from damage from acid spells.

-ELECTRICITY - Creates an exception to the +All rule by permitting normal damage from electric spells.

+ELECTRICITY - If +All is not used, this absorbs damage from damage from electric spells.

-POISON - Creates an exception to the +All rule by permitting normal damage from poison spells.

+POISON - If +All is not used, this absorbs damage from damage from poison spells.

-UNDEAD - Creates an exception to the +All rule by permitting normal damage from undead spells.

+UNDEAD - If +All is not used, this absorbs damage from damage from undead/evil spells.

-DISEASE - Creates an exception to the +All rule by permitting normal damage from disease spells.

+DISEASE - If +All is not used, this absorbs damage from damage from disease spells.

-TRAPS - Creates an exception to the +All rule by permitting normal damage from traps spells.

+TRAPS - If +All is not used, this absorbs damage from damage from traps spells.

Notes:

- `STAT-LEVEL` no longer counts 10 for each unset field.

### Prop_AddDamage

**Hosts:** mobs, items

**Parameters:**

```text
Extra Damage percentages or amounts, the list of extra damage types
```

**Examples:**

```text
50% PIERCING
5 SLASHING FROST
50% BURNING FIRE TARGETMASK="-RACECAT +Undead"
5 LEECH=25%
```

Whenever the item with this property is worn, or for the mob with this property, they will do extra damage of the types specified. Extra Damage may be represented as a percentage or as a straight amount. Damage amounts must be before the type of extra damage in the parameters. \* Here are the classes of damage:

NATURAL, SLASHING, PIERCING, BASHING, BURNING, BURSTING, SHOOTING, FROSTING, GASSING, MELTING, STRIKING, LASERING, SONICING, DISRUPTING, STULTIFYING, CORRUPTING, SCRAPING

\* Here are the types of damage attacks:

PARALYSIS, FIRE, COLD, WATER, GAS, MIND, JUSTICE, ACID, ELECTRICITY, POISON, UNDEAD, DISEASE, TRAPS, WEAPONATTACK.

\* CoffeeSUD: TARGETMASK="\[ZAPPER MASK\]" gives the extra damage only to the victims it fits (a bane weapon), and LEECH=\[N\]% heals the wielder that share of the damage dealt, the hit and the extra.

ALLSET="set-name-N" activates this bonus when at least N worn pieces share the set name, including pieces using the other ALLSET bonus properties. Each property keeps its own threshold; the default is two pieces.

### Prop_Adjuster

**Hosts:** mobs, items, exits, rooms, areas (shipped help: Mobs)

**Parameters:**

```text
statistic names followed by a +, -, or = and a value
The above parameters may be followed by an optional mask
```

**Examples:**

```text
str+2 dex-2 move+100 class=thief race=elf gender=F
str+2 dex-2 move+100 MASK=-Race +Dwarf
```

The listed effects will be given to the mob. All parameters use the + or - adjuster, except the class, race, and gender, ableprofs, ableticks, ablehpcosts, ablemncosts, ablemvcosts, and ablelvls parameters. Valid parameters are as follows:

strength dexterity constitution charisma wisdom intelligence

maxstr maxdex maxcon maxcha maxwis maxint (maximum trainable)

savepar savefir savecol savewat savegas savemin savegen savejus

saveaci saveele savepoi saveund savemag savedis savetra

ability (magical level), armor, attacks (prowess), damage (max), disposition (see AHELP ADJDISPOSITION), level, rejuv(!NO!), senses (see AHELP ADJSENSES), speed (#attacks), RONDEATH (t/f),height, weight, ambiance+``, gender, class, clstart, race, hitpoints, hunger (stomach size), mana, movement, thirst (stomach size) doubt faith deity weightadj saveblunt savepierce saveslash savespells saveprayers savesongs savechants rejuvrate critpctweapons critpctmagic critdmgweapons critdmgmagic faith rejuvrate xpadjpct. Ableprofs, ableticks, ablehpcosts, ablemncosts, ablemvcosts, and ablelvls is a commma-delimited list of ablityids followed by a number in parenthesis to adjust proficiency, duration, costs, or casting level by.

By default, the values + or - are added to the existing values. However, if MULTIPLYPH=true is included in the parameters, then attack, armor, damage, weight and height will instead have their values multiplied by the given value / 100, and speed by the given value itself (SPEED+1.5 is one and a half times). Likewise, if MULTIPLYCH=true is included, then hitpoints, mana, and movement are similarly multiplied instead of added. All other values act the same.

The RONDEATH parameter, when set to true, causes this property to disappear on the death of the item owner, the mob, or a mob in the room.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. This may seem a strange parameter for a mobs-only property, but perhaps you can find a use for it. :) See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_AffectReTick

**Hosts:** items, mobs, rooms, exits, areas

**Parameters:**

```text
SCAN=[SCAN TARGETS] SCANMASK=[ZAPPERMASK]
.. [ABILITYID]=[FORMULA] ([ABILITYID]=[FORMULA]...)
```

**Example:**

```text
scan="items,mobs" scanmask="-levels +>5" Prayer_Bless="@x1+1"
```

This property alters the tick-down time of any ability effect that it finds on a scannable target that matches the given scanmask.

The SCAN code is a comma list of MOBS, ITEMS, AREAS, ROOMS, EXITS or OWNER(S) (default MOBS when SCAN is absent; the help's WEARERS is not recognised by the code and is logged as an unknown scan code), and will attempt to scan to every available object in the room as the property, except OWNER, which will limit the scan only to the affected mob, or the owner of an affected item.

The SCANMASK narrows which items will be scanned for effects with a ZAPPERMASK. See ahelp ZAPPERMASK for more info on that.

The remaining arguments are ability Ids of effects to retick, which are set equal to a math formula on how to alter its tickdown time. The formula MUST contain the variable @x1, which represents the existing tick down time.

Once the tick-down time on an effect has been altered by this property, it will not be altered again, so while getting an affected item may cause an alteration, dropping it will not 'revert' the alterations UNLESS the OWNERS scan value is used instead of the others.

### Prop_Affinity

**Hosts:** mobs, areas

**Parameters:**

```text
(START=[N]) (DECAY=[N]) (GIFT([ITEM])=[+N]) (TALK([WORD])=[+N]) (QUESTWIN([QUEST])=[+N]) (ATTACKED=[-N]) (STOLE=[-N]) (KILLED([MOB])=[-N]) (BRIBE=[N]) (SHOPFACTOR=[BAND]:[N],...) (REFUSE=[BAND]) (REFUSEMSG="[TEXT]")
```

**Example:**

```text
GIFT(rare ore)=+10 QUESTWIN(The Smith's Blade)=+30 ATTACKED=-40 SHOPFACTOR=FRIENDLY:0.9,DEVOTED:0.8 REFUSE=HOSTILE
```

CoffeeSUD: the NPC's opinion of each character, a number from START (0): HOSTILE below -50, COLD below 0, NEUTRAL, FRIENDLY from 25, DEVOTED from 75. It moves by the rules: an item given that GIFT's item names (words of its name, or a +/- mask), money given (BRIBE points a gold), a word said to it (TALK, at most once a game day each), a quest won (QUESTWIN, counted when next read), an attack on it (ATTACKED, once a game hour), a theft from it (STOLE), or a killing it sees of a mob KILLED names. DECAY brings it that much toward 0 a game day. SHOPFACTOR multiplies its prices by band (a friend sells for less and pays more); REFUSE: at or below that band it will not trade, saying REFUSEMSG. On an area, it is the village's opinion, shared by its NPCs that have none of their own. It is kept in the game's script variables AFFINITY_\[CHARACTER\] of the NPC (or the area), so a save keeps it; it does not show in FACTIONS. Scripts read AFFINITY($i $n) (or AFFINITY($i $n BAND)) and change it with MPAFFINITY; Prop_NPCLines takes \[AFFINITY \>=25\] or \[AFFINITY FRIENDLY\] after an entry; MudChat a ?AFFINITY($i $t \> 25) guard.

### Prop_AirSupply

**Hosts:** items

**Parameters:**

```text
TICKS=[N]
```

**Example:**

```text
TICKS=6
```

CoffeeSUD: whoever carries it can go that many more ticks without air before they choke or drown, on top of their own breath (BREATHTICKS in coffeemud.ini or world.ini, @x1/3 of their constitution by default). Breathable air fills one's breath again.

### Prop_Ambush

**Hosts:** rooms

**Parameters:**

```text
MOBS="[MOBS]" (CHANCE=[N]) (DETECT=[STAT]+[SKILL]:[N]) (SURPRISE=[N]) (MSG="[TEXT]") (SPOTMSG="[TEXT]") (WAIT=[N]) (RESET=[N]) (MASK="[ZAPPER MASK]")
```

**Examples:**

```text
MOBS="encounter:bandits" DETECT=WIS+Thief_DetectTraps:15 MSG="Figures burst from the undergrowth!" SPOTMSG="You spot glints of steel in the bushes."
MOBS="catalog:a bandit*3" CHANCE=50 RESET=300 MASK="-LEVEL +>4"
```

CoffeeSUD: an ambush. When the party walks in (its leader, at CHANCE percent, 100, if MASK fits), MOBS come out: name\*dice joined with + (catalog: optional) or encounter:\[TABLE\]. The party's best rolls DETECT: d20, a stat's (N-10)/2 and a skill's proficiency/10, against the number (15). Not spotted: MSG shows, the ambushers strike, and the party is caught by surprise for SURPRISE rounds (1; Prop_Surprised), unable to move or strike. Spotted: the party sees SPOTMSG and may strike first or walk on (the ambushers go) before WAIT ticks (2) are up, when they attack. It springs once, or again RESET ticks later. AMBUSH_PROG SURPRISED, SPOTTED or AVOIDED runs on the room's scripts and its NPCs'.

### Prop_AreaBanner

**Hosts:** areas.

**Parameters:** the same as [Prop_AreaEntry](#prop_areaentry).

```text
TITLE="[text]" TEXT="[text]" ENTER="[text]" FIRST="[text]" LEAVE="[text]" ONCE MASK="[zapper mask]" COLOR="[colour code]"
```

**Example:**

```text
TITLE="THE DEEP MARKET" TEXT="Lamps swing over crowded stalls." ONCE
```

CoffeeSUD addition. A Prop_AreaEntry with the banner defaults switched on: on coming into the area the title defaults to the area's name in capitals, and the area's description is always shown after it. With no parameters at all it simply prints the area name and description. See Prop_AreaEntry for every option.

Notes:

- If an area carries both properties, Prop_AreaEntry is the one used (the engine looks for Prop_AreaEntry first).
- Fires only when the previous room was in a different area (`ScriptEvents.areaText`).

### Prop_AreaEntry

**Hosts:** areas.

**Parameters:**

```text
ENTER="[text]" FIRST="[text]" LEAVE="[text]" TITLE="[banner title]" SHOWNAME TEXT="[text]" DESC ONCE MASK="[zapper mask]" COLOR="[colour code]"
```

| Option | Meaning |
|---|---|
| `ENTER` | Text told to the character each time they come into the area. |
| `FIRST` | Text told only the first time that character enters (in addition to ENTER). |
| `LEAVE` | Text told on leaving the area (to a room in another area). |
| `TITLE` | A banner line shown in the colour code `COLOR` (default `^H`) before the text. |
| `SHOWNAME` | A flag: use the area's name in capitals as the title when no `TITLE` is given. |
| `TEXT` | A paragraph shown after the title. |
| `DESC` | A flag: also show the area's description. |
| `ONCE` | A flag: the title, text and description are shown only on the character's first visit; `ENTER`/`FIRST` are unaffected. |
| `MASK` | A zapper mask the character must fit for anything to show (for `LEAVE` as well). |
| `COLOR` | Colour code for the title line; default `^H`. |

**Example:**

```text
SHOWNAME ONCE TEXT="You step under the dripping eaves of the old forest." LEAVE="The trees close behind you."
```

CoffeeSUD addition (ST16/PR4). Shows text on an area's border. It runs only when the move crosses from one area to a different one.

Notes:

- The first-visit test is made by the engine when the character comes into the area (`ScriptEvents.areaText`); it is not a stable saved marker.
- Use [Prop_FirstEntry](#prop_firstentry) when you need a saved once-only marker that survives renaming.

### Prop_AreaForSale

**Hosts:** areas

**Parameters:**

```text
(RENTAL )Price of the area
```

**Examples:**

```text
1000000
Bob/100000
```

Makes this area available for purchase by a player. To have a shopkeeper sell the area, you must create a GenTitle or StdTitle item with the area name as the property id and give it to the shopkeeper to sell. The system will automatically maintain the integrity of any items left in the rooms, including restoring them on reboot. The title received for purchasing a room may be GIVEn or SELLed to change possession of the property. The word rental followed by a space may preceed the price to make the price of the property be charged every month automatically out of a Bankers account. Failure to make rent returns the property to sellable status.

When property is sold, it will be listed as the owner with a slash and the price. Entering this sort of parameter directly is the quick way to give players or clans property.

See also Prop_ReqCapacity for the means to set limits on players building abilities on their property.

Notes:

- `landPropertyID` fixed (the area now gets its id). In CoffeeSUD ownership belongs to the player profile; see Prop_RoomForSale.

### Prop_Artifact

**Hosts:** items

**Parameters:**

```text
autodrop=[T/F] nomobs=[T/F] nolocate=[T/F] nocast=[T/F]
```

**Example:**

```text
nomobs=false
```

The items with this property will persist in their last known location past reboots, and optionally always drop from players inventories when they quit, be invulnerable to damage, not be possessable by mobs, and not capable of being acted upon with skills or spells. By default, all of the above is true, though that can change by setting one of the following parameters to false:

autodrop - the item drops from player inventories when they quit or shutdown

nomobs - the item may not be picked up by a non-player

nolocate - the item may not be located via Locate Object spell

nocast - the item may not have ANY spells cast upon it

Notes:

- Only the phrase "reset prop_artifact", said by someone allowed to edit items, destroys or resets the artifact; ordinary speech at the item no longer does.

### Prop_AstralSpirit

**Hosts:** mobs

**Parameters:** none.

This property makes the mob into an incorporeal spirit. It is used to support the "Astral Projection" chant.

### Prop_Attunement

**Hosts:** items

**Parameters:**

```text
PROPS="[ID]([ARGS]);[ID]([ARGS])" (HOW=REST:[TICKS]) (RITUAL="INROOM [ROOM ID] & SAY [WORDS]") (ON=EQUIP) (LIMIT=[N]) (BIND=true) (MSG="[TEXT]") (MASK="[ZAPPER MASK]")
```

**Examples:**

```text
PROPS="Prop_WearEnabler(Spell_Haste);Prop_WearAdjuster(dex+2)" HOW=REST:10 LIMIT=3 BIND=true
PROPS="Prop_WearEnabler(Chant_Barkskin)" RITUAL="INROOM Grove#1 & SAY attune"
```

CoffeeSUD: powers that wake only once the item is attuned. PROPS stay inert until then. One attunes by resting with it (HOW=REST:\[TICKS\], sitting or sleeping while carrying it), by a RITUAL (in the room, saying the words; either part may be left out), or by wearing it (ON=EQUIP). LIMIT is how many items a character may be attuned to (3); UNATTUNE \[ITEM\] frees one. BIND keeps it inert in anyone else's hands. MSG is what the one who attunes is told; MASK is who may. Identifying it says it requires attunement, or to whom it is attuned.

### Prop_AuthorIdentity

**Hosts:** anything (rooms, areas, items, mobs, exits).

**Parameters:** none authored. The text is assigned by the engine.

CoffeeSUD internal. When a stateful authoring property (Prop_ShowWhen, Prop_Senses, Prop_Details, Prop_LanguageImmersion, Prop_FirstEntry, Prop_Waypoint, ...) is used on something that has no explicit `ID=`, the engine adds a saved Prop_AuthorIdentity to the host (`AuthorIds` class) and stores a generated identity in it. The property then keeps the saved markers (discoveries, once-only flags, immersion counters) tied to the host through renames, moves and description edits.

Notes:

- Do not attach it by hand. To give a source a stable identity yourself, put `ID=your-id` in the property that needs it; see [Saved identities](#saved-identities).
- A copy of the host carries the same identity and therefore shares state with the original. Give the copy a new explicit `ID=` to make it independent.
- It is saved with the host like any other property.

### Prop_AutoSave

**Hosts:** rooms, areas

**Parameters:**

```text
(ONCE=CHAR) (SLOT=[NAME]) (ROTATE=[NUMBER]) (MSG="[MESSAGE]")
```

**Examples:**

```text
ONCE=CHAR MSG="[Autosaved]"
SLOT=auto ROTATE=3
```

Saves the game when the player comes into the room, or into the area from outside it. With ONCE=CHAR, only the first time each character comes in. The save is called SLOT (auto if not given); with ROTATE, the saves take turns, SLOT1 to SLOT3 for ROTATE=3, so the last three are kept. The player is told MSG, or \[Autosaved\]. It is skipped while the party fights (unless the save mode is ANYWHERE), but saves even where Prop_NoSave or the lack of a save point would refuse a SAVE. In an ironman game it writes the game's one save.

### Prop_BlockedExit

**Hosts:** exits

**Parameters:**

```text
(NAME="[TEXT]") (HP=[N]) (TOOL="[ZAPPER MASK]") (SKILL=[STAT]+[SKILL]) (FIRE=true) (MSG="[TEXT]") (CLEARMSG="[TEXT]") (ONCLEAR="[ACTIONS]") (RESPAWN=[TICKS])
```

**Examples:**

```text
NAME="a pile of rubble" HP=40 TOOL="-TAGS +PICKAXE" SKILL=Digging+STR CLEARMSG="The rubble gives way to a dark tunnel."
NAME="a wall of vines" HP=60 FIRE=true RESPAWN=3000
```

CoffeeSUD: a blockage the player must clear. While it stands nobody goes that way, and LOOK at the exit says so. CLEAR, DIG, SMASH, HIT or ATTACK the direction (or the blockage by its NAME) wears it down: 1d6 and SKILL (a stat's (N-10)/2, a skill's proficiency/10) off its HP (40) each try, with TOOL held or wielded if given. FIRE=true lets BURN clear it at once with a lit light in hand. MSG shows each try; CLEARMSG on both sides when it is gone, and ONCLEAR's actions run; RESPAWN ticks later it is back (0: never). What is left of it is kept with it (LEFT=), and one on the other side of the exit is kept the same.

### Prop_Broken

**Hosts:** items.

**Parameters:** `ORIGINAL="[the item's name before it broke]"`. Set by the engine.

CoffeeSUD addition (item durability). Added by `Durability.breakItem` when a worn-out item is kept rather than destroyed (coffeemud.ini `ITEMBREAK=BREAK`; the default `DESTROY` removes the item). A broken item has armour and damage set to 0, shows the ambiance `(broken)`, and refuses to be worn, wielded or held ("... is broken."). The property ticks once per tick; as soon as the item's uses remaining are above 0 again (a repair skill or a script restores them) the property removes itself and the item's original name returns.

Notes:

- If the item's durability rules define `BROKENNAME`, the engine renames the item when it breaks (`%s` stands for the old name without its article); `ORIGINAL` is how the old name comes back.
- Builders normally never attach it; to test, set an item's uses to 0 with `ITEMBREAK=BREAK` in effect.
- See [Prop_Durability](#prop_durability) for the per-item wear rules.

### Prop_Checkpoint

**Hosts:** rooms

**Parameters:**

```text
(NAME="[TEXT]") (ON=ENTER/REST/COMMAND:[WORD]) (HEAL=[N]%) (RESPAWNAREA=true) (SAVE=AUTO/[SLOT])
```

**Examples:**

```text
NAME="the Old Shrine" ON=COMMAND:pray HEAL=100% RESPAWNAREA=true
ON=ENTER SAVE=AUTO
```

CoffeeSUD: a checkpoint. When the party walks in (ON=ENTER, the default), rests there (REST: sits or sleeps) or types the word (COMMAND:pray), the room becomes the respawn point of each of the party there: kept on the character, through death, and apart from the recall room. With DEATH=CHECKPOINT in coffeemud.ini, a fallen character wakes at their last checkpoint (or their recall room, with none). HEAL heals them that share; RESPAWNAREA brings the area's dead mobs back; SAVE saves the game (AUTO, or a slot's name). Scripts set it with MPSETRESPAWN and read it with RESPAWNROOM().

### Prop_ClanEquipment

**Hosts:** items

**Parameters:**

```text
[TYPE] [POWER#] [CLAN NAME] [CLAN TYPE]
```

**Example:**

```text
Acid 5 "The Skooters" "Guild"
```

Base property for clan equipment. Do not use independent of the clan spells.

Notes:

- Legacy. It depends on the inherited clan spells; clans as a player-run feature are not part of play in CoffeeSUD, so no clan equipment is made. A non-numeric power level is now tolerated.

### Prop_Climbable

**Hosts:** exits, rooms, items (shipped help: Exits, Rooms, Portals)

**Parameters:** none.

Forces players to climb, fly, or use a ladder through the exit or room with this property.

### Prop_CloseCommand

**Hosts:** items, exits (shipped help: Containers, Exits)

**Parameters:**

```text
[COMMAND WORD] ([PARMS...]/NOCLOSE/MESSAGE=[STR];...
```

**Examples:**

```text
twist knob;tw knob;twi knob;noclose
smack botto*
```

When applied to an open container or exit, this property will force the affected openable to close and maybe lock when the command phrase is entered by the user in its presence. The command word must match exactly, and the command entered must otherwise be completely invalid. PARMS arguments must also match exactly, although any PARM arg may end with \* to use a starting string wildcard. A PARM argument may also end with \*\* to match the rest of the users entered command string. In addition to a series of command words, you can also use the NOCLOSE keyword to also disable the standard close command for the exit/item, or use MESSAGE= to change the message sent to the room, which can use \<S-NAME\> for the actor, and \<T-NAME\> for the item.

Notes:

- Uses MSG_CLOSE (fixed: it sent MSG_OPEN).

### Prop_ClosedDayNight

**Hosts:** items, mobs, exits, rooms

**Parameters:**

```text
(DAY/NIGHT)(SLEEP)(SIT)(LOCKUP)(HOURS=0-13)(HOME=Room#)
(WARNMSGx=[msg]) (SHOPMSG=[msg]) (MASK=)
```

**Examples:**

```text
DAY
SLEEP HOURS=5-10
"Home=My Area#123" SLEEP LOCKUP MASK=-RACE +ELF
"Home=Bob`s House" SLEEP LOCKUP "SHOPMSG=Go Away! I'm Closed!"
```

Affects the thing with this property depending on the time of day. By default, this property activates at night. If the DAY paramater is included, it will activate by day instead. The effect of the property is that rooms will go dark, exits will close, and items will become unseeable. Mobs will become unseeable by default, though if the sit or sleep flags are set, they will do that instead. LOCKUP will cause the mob to lock up his starting room, if any doors with locks are found. If a Home Room is specified with the HOME= parameter, the mob will track to that room at closing time, then back to their start room the next day. For shopkeepers, you can specify a SHOPMSG for them to say whenever someone tries to interact with them as a shopkeeper during closed hours. You can also specify a particular greeting message at a particular hour of the day with WARNMSG, such as WARNMSG0 for the 0th hour of the day, etc..

Notes:

- `MASK=` must come last: everything after it is read as the mask. The other words are separated by spaces, so quote a whole `"HOME=..."` or `"SHOPMSG=..."` token that has spaces. `WARNMSG<hour>` accepts hours 0 to 99.

- With one world and one clock, the "home" and "area" clocks are the same clock.

### Prop_ClosedSeason

**Hosts:** items, mobs, exits, rooms

**Parameters:**

```text
(SUMMER/SPRING/WINTER/FALL/DAY/NIGHT
DUSK/DAWN/RAIN/SLEET/SNOW/CLEAR/HEATWAVE
THUNDERSTORM/BLIZZARD/WINDY
DROUGHT/DUSTSTORM/COLD/HAIL/CLOUDY)
(MASK=[ZAPPERMASK])
```

**Examples:**

```text
DAY
WINTER BLIZZARD MASK=-RACE +ELF
```

Affects the thing with this property depending on the weather, or time of the year. By default, this property does not activate. You must specify one or more times, seasons, or conditions under which the object will no longer be visible.

Notes:

- The code also accepts `FOG`, `DAYTIME` and `NIGHTTIME` (not in the help). Words are matched against the area's weather, time of day and season, so the weather only counts in the area the object is in. `MASK=` must come last: everything after it is read as the mask.

- With one world and one clock, the "home" and "area" clocks are the same clock.

### Prop_CombatAdjuster

**Hosts:** MOB (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
(ATTACK+/-[PCT NUMER]) (ARMOR+/-[PCT NUMER]) (DAMAGE+/-[PCT NUMER])
(HP+/-[PCT NUMER]) (MANA+/-[PCT NUMER]) (MOVE+/-[PCT NUMER])
(ATTACK=[PCT NUMER]) (ARMOR=[PCT NUMER]) (DAMAGE=[PCT NUMER])
(HP=[PCT NUMER]) (MANA=[PCT NUMER]) (MOVE=[PCT NUMER])
```

**Example:**

```text
ATTACK+25 DAMAGE-20 MANA=1000
```

This property either adjusts a mobs current stats by a percentage value as specified, or sets the value to a specific number. This property is mostly useless for builders, but has internal uses. Supported stats include: ATTACK, DAMAGE, ARMOR, HP, MANA, MOVE, SPEED

Notes:

- Declares no host type, so `?P` does not list it; add it to a mob by name.

### Prop_CombatStats

**Hosts:** mobs, items (a worn top-level item).

**Parameters:** strict JSON (at most 8192 characters).

```text
{"schema":1,"armor":200,"resistance":{"fire":50},"penetration":{"fire":20}}
```

**Example:**

```text
{"schema":1,"armor":150,"crit_chance":5,"resistance":{"cold":25}}
```

CoffeeSUD addition, for the optional combat-profile system only. Attach it to a MOB, or to an item that is worn at the top level of an owner (not inside a container). It adds accuracy, evasion and armour, block, spell_block, crit_chance and crit_multiplier points, typed resistance and penetration, and immune type lists. Types are `physical`, `fire`, `cold`, `electric` and `acid`; immunity also accepts `burning`. The critical multiplier is additive points: `+50` changes the default 150% to 200%.

Notes:

- Taking the item off, or removing the source, removes the bonus. Ordinary engine stats (Prop_Adjuster and friends) are not changed by this property.
- At most 64 sources per actor. Invalid primary stats reject casts before costs.
- `WORLD CHECK ONLY=COMBAT` diagnoses these properties; see `AHELP WORLD_COMBAT` and chapter 16 (combat profiles) for ranges and the order of application.

### Prop_ComboLock

**Hosts:** items, exits (shipped help: Exits, Containers)

**Parameters:**

```text
CODE=[CODE]/RANDOM (SYMBOLS="[SYMBOL],[SYMBOL]"/[N]-[N]) (DIALS=[N]) (VERB=[VERB]) (SHOW=true) (TRIES=[N]) (ONWRONG="[ACTIONS]") (ONOPEN="[ACTIONS]") (RELOCK=[TICKS]) (MSG="[TEXT]") (VAR=[NAME])
```

**Examples:**

```text
CODE=sun-star-moon SYMBOLS="sun,moon,star" SHOW=true TRIES=3 ONWRONG="cast Spell_ShockingGrasp $n"
CODE=739 RELOCK=30
CODE=RANDOM DIALS=4 VAR=VAULTCODE
```

CoffeeSUD: a combination lock on a door or a container. TURN DIAL 2 TO 3 sets one dial (VERB changes the word), SET DIALS 7 3 9 all of them, ENTER 739 a keypad's code; ON \[THING\] picks one when there are more. SYMBOLS are each dial's (0-9 by default); DIALS how many (as many as the code has). When the dials read the code, it unlocks and opens, on both sides of a door, with MSG, and runs ONOPEN; RELOCK shuts and locks it again after that many ticks. SHOW adds what the dials read to LOOK. After TRIES wrong codes, ONWRONG runs. CODE=RANDOM rolls one for each new game (DIALS of the SYMBOLS), kept with the lock, and VAR puts it in the script variable $\<COMBO \[NAME\]\> for a clue to print. Actions are script commands, as in Prop_Switch.

### Prop_CommonTwister

**Hosts:** exits, rooms, areas, items, mobs (shipped help: Anything)

**Parameters:**

```text
SKILL=[*/SKILL] MASK=[*/NAME] (NAME=[*/NEW]) (DISPLAY=[*/NEW]) (MATERIAL=[*/NEW]);([REPEAT])
```

**Examples:**

```text
skill=Foraging mask=berries name="some corn" display="some corn is here" material=corn
skill=* mask=* name="some stuff" display="some stuff";skill=* mask=* name="some other" display="some other"
```

This property changes the results from the use of common skills. This property requires the SKILL and MASK parameters, and at least one of the other parameters (NAME, DISPLAY, MATERIAL, or SUBTYPE). For each parameter, \* may be used to match anything or a random thing.

Notes:

- The parameter is spelled `DISPLAY` (the code reads `DISPLAY`; `DISPLAYTEXT` is only the item field it sets). `SUBTYPE=` is also read (a raw material's sub-type) though the help omits it.

### Prop_CraftXP

**Hosts:** mobs, items, rooms, areas

**Parameters:**

```text
(PCT=[PCT]) (GATHER=[N]) (SKILLS=[SKILL ID],...) (MASK="[ZAPPER MASK]")
```

**Example:**

```text
PCT=50 GATHER=2 SKILLS=Blacksmithing,Mining MASK="-LEVEL +<20"
```

CoffeeSUD: crafting and gathering XP for a player character of any class. When their crafting, cooking or building skill makes something, it has an XP value (what an Artisan earns for it): CRAFTXP=\[PCT\] in the ini gives every player character that percent of it, and PCT here adds its own. When a gathering skill finds something, GATHERXP=\[N\] in the ini, and GATHER here, are XP for every unit found. It counts on the character, on an item they carry, on the room or an item lying in it, and on the area or one of its parents, as PCT=50 on a guild hall that teaches. SKILLS are the skills it is for (all when not given) and MASK who it is for. The XP goes through the usual way, so Prop_ModExperience and achievements see it, and an Artisan earns it on top of their own.

### Prop_Crawlspace

**Hosts:** exits, rooms, areas

**Parameters:**

```text
(HEIGHT=[NUM])
```

**Example:**

```text
height=24
```

Forces players to crawl through the exit or room with this property. The height of the crawlspace is optionally defineable (`HEIGHT`, default 24).

### Prop_CropRules

**Hosts:** rooms, areas, items

**Parameters:**

```text
(GROWHOURS=[N]) (SEASONS=[SEASON],...) (NEEDSUN=true/false) (DOMAINS=[ROOM TYPE],...) (WATER=[N]) (YIELD=[DICE]) (FAILMSG="[TEXT]")
```

**Example:**

```text
NEEDSUN=false DOMAINS=CAVE,STONE FAILMSG="Only the cellar beds will take that."
```

CoffeeSUD: crop rules for Farming and Gardening, on a room or an area (all crops there) or on the seed itself. GROWHOURS is how many game hours the crop takes to grow (50 ticks when not given); a crop given hours is not set back by the nights while it grows. SEASONS are when it may be planted (SPRING, SUMMER, AUTUMN, WINTER). NEEDSUN=false lets it grow without sunlight. DOMAINS are the kinds of room it grows in (PLAINS, HILLS, WOODS, JUNGLE, SWAMP, CAVE, STONE..., or ANY), in place of the usual farmland. WATER is how many water rooms, this one or those next to it (a pond Irrigation digs counts), it needs. YIELD is dice, as 2d6+1, in place of 1d7, still times the skill's yield. FAILMSG is told when one of them refuses. A world can give a crop its rules in world/crops.txt, a line each: the resource or subtype, then these rules, as WHEAT GROWHOURS=240 SEASONS=AUTUMN. The area's, the room's and the seed's come over the file's, in that order.

### Prop_Cursed

**Hosts:** items

**Parameters:**

```text
(LEVEL=[N]) (HIDDEN) (NOREMOVE) (NODROP) (PENALTY="[ID]([ARGS]);[ID]([ARGS])") (REVEAL="[TEXT]") (BREAK="SAY:[WORDS];ENTER:[ROOM ID]") (BREAKMASK="[ZAPPER MASK]") (ONBREAK=CRUMBLE/KEEP/BECOME:[ITEM]) (BREAKMSG="[TEXT]")
```

**Example:**

```text
LEVEL=15 HIDDEN NOREMOVE PENALTY="Prop_WearAdjuster(wis-3);Prop_WearSpellCast(Prayer_CurseLuck)" REVEAL="The ring tightens on your finger!" BREAK="SAY:i confess;ENTER:Chapel#3" ONBREAK=CRUMBLE
```

CoffeeSUD: a curse on an item that Remove Curse ends cleanly. It is of the cursing domain, so Remove Curse finds it, but only a priest of LEVEL or more lifts it. HIDDEN shows nothing until it is first worn, when REVEAL is told. NOREMOVE and NODROP keep it on and in hand. PENALTY is what it does (properties and their arguments, a semicolon between). BREAK is a list of deeds, done in order by the one who has it (SAY:\[WORDS\], ENTER:\[ROOM ID\]); BREAKMASK a mask they come to fit. Lifted or broken, its penalties go with it, and the item stays (KEEP), crumbles (CRUMBLE), or becomes another (BECOME:\[ITEM\]).

### Prop_DarkDanger

**Hosts:** rooms, areas

**Parameters:**

```text
(LOST=[PCT]%) (STUMBLE=[PCT]%) (LURKER=[CATALOG MOB] LURK=[PCT]%) (MSG="[TEXT]") (STUMBLEMSG="[TEXT]") (LURKMSG="[TEXT]")
```

**Example:**

```text
LOST=10% STUMBLE=5% LURKER=ghoul LURK=3%
```

CoffeeSUD: dangers for a player the room is dark to (dark, and they cannot see in the dark; a lit torch ends it). LOST: a move goes a random open way (MSG says so). STUMBLE: a move fails and costs movement (STUMBLEMSG). LURKER: as they come in, the catalog's mob of that name (or a mob class) comes out of the dark, LURK percent of the time (LURKMSG).

### Prop_DarkDescription

**Hosts:** rooms.

**Parameters:**

```text
TITLE="[room title when dark]" DESC="[description when dark]"
```

**Example:**

```text
TITLE="Somewhere dark" DESC="Cold water laps at your knees."
```

CoffeeSUD addition. Describes a room that is dark to the looker without revealing its contents. `VARIES` text is allowed. As an alternative, put DARK and LIT blocks in the room's own description (see the presentation chapter, 11).

### Prop_DeathRules

**Hosts:** rooms, areas

**Parameters:**

```text
(PLAYERDEATH="[LIST]") (MOBDEATH="[LIST]") (ITEMLOOTPOLICY="[LIST]") (TO=[ROOM ID]) (GEAR=CONTAINER:[ROOM ID]/[CONTAINER]) (HEAL=[N]%) (MSG="[TEXT]") (MASK="[ZAPPER MASK]")
```

**Examples:**

```text
TO=Hideout#4 GEAR="CONTAINER:Hideout#9/the chief's strongbox" HEAL=25% MSG="<S-NAME> <S-IS-ARE> beaten and dragged off!"
PLAYERDEATH="OUT 3" MOBDEATH="OUT 3"
```

CoffeeSUD: the rules of death in an area or a room (the room's before its area's). PLAYERDEATH, MOBDEATH and ITEMLOOTPOLICY take the place of coffeemud.ini's there (OUT 3 for a tutorial, where losing only knocks one out). TO makes a defeat of the player's side not death: MSG shows, the fight stops, the character's gear goes into the GEAR container, and they wake in the TO room with HEAL percent of their hit points (1 if none), their followers with them, so the party stays together: a jailbreak instead of a reload. MASK is whom these rules are for.

### Prop_Details

**Hosts:** rooms, items, mobs.

**Parameters:**

```text
keyword,alias=description;other keyword=description
```

Per-detail options go after a description, inside its record: `EXAMINEONLY`, `READ="text"`, `MASK="zapper"`, `HIGHLIGHT`, `SETVAR=key=value`, `TATTOO=name`, `ID=stable-id`, `ALIASES=old-id`, `LEGACYKEYS=old-token`.

**Example:**

```text
inscription,runes=Ancient letters wind around the arch. READ="It says: LEAVE." ID=crypt-inscription SETVAR=READ_RUNES=1;lever=A rusted lever.
```

CoffeeSUD addition. Lets a host answer LOOK, EXAMINE and READ for extra "things" that are not objects. Each record is `keywords=description`; several keywords are separated by commas, records by `;`. Details are tried after real objects fail to match. `LOOK keyword ON object` qualifies the host. The description is rendered through the story-text engine, so `VARIES` markup works. Quote option values containing spaces or semicolons (see [Authoring option syntax](#authoring-option-syntax)).

| Option | Meaning |
|---|---|
| `EXAMINEONLY` | The detail answers EXAMINE, not plain LOOK. |
| `READ="text"` | The text shown for READ (READ shows only this; LOOK and EXAMINE show the description without its options). |
| `MASK="zapper"` | Only characters who fit the mask see the detail. |
| `HIGHLIGHT` | On a room: the keywords are coloured (`^H`) wherever they appear in the room description. |
| `SETVAR=key=value` | Clue option: sets a variable on the character the first time the detail is used. |
| `TATTOO=name` | Clue option: gives the character a tattoo the first time. |
| `ID`, `ALIASES`, `LEGACYKEYS` | Stable identity for the once-only markers. See [Saved identities](#saved-identities). |

Notes:

- Clue options run only on the first use. `LOOK_PROG` scripts on the host receive the keyword in `$g`.
- A detail without an explicit `ID` is additionally keyed by its keyword list.

### Prop_Detector

**Hosts:** items

**Parameters:**

```text
(DETECT="[ZAPPER MASK]") (DETECTITEM="[ZAPPER MASK]") (RANGE=[0-3]) (WHERE=WORN/HELD/ANY) (EVERY=[TICKS]) (AMBIANCE="[TEXT]") (MSG="[TEXT]") (STOPMSG="[TEXT]")
```

**Examples:**

```text
DETECT="-RACECAT +Orc +Goblinoid" RANGE=2 WHERE=WORN AMBIANCE="(glowing blue)" MSG="$p glows faintly blue." STOPMSG="$p's glow fades."
DETECTITEM="-TAGS +TREASURE" MSG="$p twitches in your hands." WHERE=HELD
```

CoffeeSUD: an item that reacts to what is near: mobs that DETECT fits, or items that DETECTITEM fits, in its room or up to RANGE rooms away. WHERE: WORN (anywhere on the body), HELD (held or wielded) or ANY (carried, or lying anywhere). Every EVERY ticks (1) it looks; while it finds something it has AMBIANCE, and the one carrying it (or the room it lies in) is told MSG when it starts and STOPMSG when it stops; $p is the item.

### Prop_DevCheats

**Hosts:** mobs

**Parameters:**

```text
([CHEAT]) (-[CHEAT]) (-[SECURITY FLAG])...
```

**Example:**

```text
NOCLIP NOAGGRO -IMMORT
```

A developer's cheats, which the GOD command sets: see AHELP GOD for what each does. A cheat's name turns it on; a minus and a cheat's name turns it off, even where a sysop power would give it; a minus and a security flag (as in AHELP SECURITY) suspends that power for the character. -NODEATH suspends IMMORT. The cheats are NOCLIP, NOCOST, NODAMAGE, NODEATH, SEEALL and NOAGGRO. It is saved with the character like any effect.

### Prop_Discoverable

**Hosts:** items, exits, mobs

**Parameters:**

```text
(FOUNDBY=examine:[THING]|look:[THING]|search) (CHECK="[STAT] [NUMBER]") (DC=[NUMBER]) (STAT=[STAT]) (MSG="[TEXT]") (HINT="[TEXT]") (STAYS=false) (RESET=[TICKS])
```

**Examples:**

```text
FOUNDBY=examine:tapestry MSG="Behind the tapestry is a narrow door." HINT="The tapestry stirs in a draught."
FOUNDBY=search|examine:fireplace CHECK="INT 12"
```

CoffeeSUD: something hidden that anyone can find. Until it is found nobody sees it, and nobody goes through a hidden exit. FOUNDBY (search, by default): EXAMINE of that thing here, LOOK at it (look:), or SEARCH, which needs no skill (one SEARCH looks for all there is in the room). CHECK (or STAT and DC): a d20, the stat's (N-10)/2 and a tenth of Searching's or Thief_Search's proficiency against the number; with none, it is found at once. MSG is what the finder is told; HINT is added when one of its FOUNDBY things is looked at. Once found it stays found (kept with it), unless STAYS=false, which hides it again RESET ticks (30) later.

### Prop_Discovery

**Hosts:** rooms, exits, items, mobs

**Parameters:**

```text
XP=[N or N%] ON=[ENTER/LOOK/READ/OPEN/GET/FIND/TALK] ONCE=[CHARACTER/PARTY/PROFILE] MESSAGE="..." TATTOO=[NAME] MASK="..." JOURNAL="..."
```

**Example:**

```text
XP=150 ONCE=PARTY MESSAGE="Behind the falls lies a hidden grotto!" TATTOO=FOUND_GROTTO
```

CoffeeSUD: a discovery. The first time its event happens to a character (ON: ENTER the room, LOOK at it, READ, OPEN, GET, TALK to the mob, or FIND it when it is hidden with Prop_Discoverable; by default ENTER for a room, OPEN an exit, GET an item, TALK a mob) and MASK lets them in, they get XP (a number, or a percent of their level's experience; tagged DISCOVERY:, so XPMOD can scale it), are told MESSAGE, get a JOURNAL line in their quest journal, and the tattoo TATTOO (or one of its own) marks it done: for them, their whole party (ONCE=PARTY) or every character of the profile (ONCE=PROFILE). DISCOVERYXP=\[formula\] in the ini or world.ini pays each room a character enters for the first time (@x1 their level, @x2 the area's median level).

### Prop_Doppleganger

**Hosts:** mobs, items (shipped help: Mobs, Items, Rooms, Areas)

**Parameters:**

```text
nothing, or a level change + or -, a level %, or args:
: : MIN=[NUM] MAX=[NUM] LEVELADD=[NUM] LEVELPCT=[PCT%]
: : PLAYERSONLY=true/false PLAYERSNFOLS=true/false
: : ASMATERIAL=[RESOURCE] DIFFADD=[NUM] DIFFPCT=[PCT%]
: : LEVELCODE=0/1/-1 DIFFGRP=[true/false] SAVEPCT=[PCT%]
```

**Examples:**

```text
10
150%
MAX=20 MIN=5 LEVELADD=0 LEVELPCT=100% PLAYERSONLY=false
MAX=20 MIN=5 ASMATERIAL=IRON
```

When this property is on a mob, it makes the mob the same level as those in the room with it, or as those who are fighting it. The mob will only change, however, if the mob is at full hit points, to prevent mobs from being "softened up". By default the level is the average of qualifying room mobs, but can be the lowest level if LEVELCODE=-1, or the highest level if LEVELCODE=1. When the property is on armor or a weapon, it will adjust the strength and level of the item appropriate to its owner.

A parameter may be given to always adjust the level of the item up or down from normal, be multiplied by the level as a percentage, or a number of parameter variables like: MIN to set a minimum level, MAX to set a maximum level, LEVELADD to add a fixed number to the level, LEVELPCT to multiply a percentage by the level (or leave the same when set to 0), PLAYERSONLY to limit the level calculating mobs to players only, PLAYERSNFOLS to limit the level calculating mob to players or their followers only, and ASMATERIAL to change the material of the item only for stat calculation purposes. The difficulty of the mob relative to the level change can also be affected using DIFFADD and DIFFPCT. These will change the difficulty of the mob relative to their level. Saving throw bonus per level can also be applied by setting SAVEPCT=0-100. These difficulties can be multiplied by the number of threats by setting DIFFGRP to true, which also will include group members not present in the math.

Notes:

- Save bonuses no longer add up at each rescale: the bonus given at the last rescale is removed first.

### Prop_Downed

**Hosts:** mobs

**Parameters:**

```text
(TICKS=[N]) (HEAL=[N])
```

**Example:**

```text
TICKS=3 HEAL=5
```

CoffeeSUD: down (PLAYERDEATH's DOWNED puts it on one of the player's side at 0 hit points). For TICKS rounds (3) the character lies there: it cannot move, strike, cast or stand, and is not attacked. An ally's REVIVE, or a heal of HEAL hit points (1) or more, brings them back. When the foes that downed them are gone or dead, and no one of the party there still fights, they get up at 1 hit point. When the time runs out, the rest of the PLAYERDEATH list applies.

### Prop_Durability

**Hosts:** items

**Parameters:**

```text
(MULT=[PERCENT]) (UNBREAKABLE) (NOREPAIR) (BREAK=true) (BROKENNAME="[NAME with %s]")
```

**Examples:**

```text
MULT=50 BROKENNAME="a shattered %s"
UNBREAKABLE
```

CoffeeSUD: an item's own wear and tear. MULT is the percent of the usual wear it takes (with coffeemud.ini's ITEMWEAR); UNBREAKABLE takes none. BREAK or BROKENNAME: worn out, it breaks rather than goes (as ITEMBREAK=BREAK does for all), and BROKENNAME names it so (%s its old name). A broken item cannot be worn, held or wielded, gives no armour or damage, and shows as (broken), until it is mended (MEND, a smith); then it has its name back. NOREPAIR: nobody mends it. Below half its condition it shows as (worn).

### Prop_EnlargeRoom

**Hosts:** rooms, areas

**Parameters:**

```text
number or adjustment of movement points
```

**Examples:**

```text
2
+2
-2
/2
*2
/2.5
*1.5
```

Change the number of movement points required to cross the room, and range. The +-/\* signs may be used to adjust the amounts for a given room, or an absolute value may be given.

### Prop_EnterAdjuster

**Hosts:** exits, rooms

**Parameters:**

```text
statistic names followed by a +, -, or = and a value
```

**Examples:**

```text
and/or spells separated by semicolons, and an optional mask
str+2 dex-2 move+100 class=thief race=elf gender=F;Prayer_Bless;
str+2 dex-2 move+100 MASK=-Race +Dwarf
str+2 gender=F;Prayer_Bless;MASK=-Race +Dwarf
```

Upon entering this room or exit, the listed effects or spells will be PERMANENTLY ADDED to the mob. All parameters use the + or - adjuster, except the class, race, and gender, ableprofs, and ablelvls parameters. Valid parameters are as follows:

strength dexterity constitution charisma wisdom intelligence

maxstr maxdex maxcon maxcha maxwis maxint (maximum trainable)

savepar savefir savecol savewat savegas savemin savegen savejus

saveaci saveele savepoi saveund savemag savedis savetra

ability (magical level), armor, attacks (prowess), damage (max), disposition (sitting/standing/flying/etc), level, rejuv(!NO!), senses (blind/infravision/darkvision/etc), speed (#attacks), height, weight, ambiance+``,gender, class, race, hitpoints, hunger (stomach size), mana, movement, thirst (stomach size) conversion weightadj saveblunt savepierce saveslash savespells saveprayers savesongs savechants critpctweapons critpctmagic critdmgweapons critdmgmagic faith deity rejuvrate xpadjpct, experience, practices trains, questpoints, coins. Ableprofs and ablelvls is a commma-delimited list of ablityids followed by a number in parenthesis to adjust proficiency or casting level by.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

Notes:

- The changes are made when the entry happens (not when it is only attempted), so an entry another effect refuses changes nothing. Each entry adds them again, as the help says. Stat terms are read from every `;` part that is not an ability.

### Prop_Essential

**Hosts:** mobs

**Parameters:**

```text
(MODE=YIELD/KNOCKOUT) (AT=[N]%) (TICKS=[N]) (MSG="[TEXT]") (REFUSEMSG="[TEXT]") (NOXP)
```

**Examples:**

```text
MODE=YIELD AT=10% TICKS=20 MSG="<S-NAME> drop(s) to one knee and yield(s)!"
MODE=KNOCKOUT TICKS=10
```

CoffeeSUD: a mob the story needs alive, or one that gives up. MODE=YIELD: under AT percent of its hit points (10) it stops fighting, as do its foes, MSG shows, and its scripts' YIELD_PROG runs ($n who beat it), so a script can spare it, take its key, or finish it. MODE=KNOCKOUT: a blow that would kill it leaves it at 1 hit point, out cold, and DEFEAT_PROG runs. Either way, for TICKS ticks (20) attacks on it are refused with REFUSEMSG, and the one who beat it gains the experience of a kill, unless NOXP.

### Prop_FactionEffects

**Hosts:** mobs (added by the engine to player characters).

**Parameters:** the faction ID (for example `elves.ini`). Set by the engine.

CoffeeSUD internal (DefaultFaction). A faction file can define player effects with `PLAYEREFFECT1`, `PLAYEREFFECT2`, ... keys of the form `AbilityID;parameters;band codes or a zapper mask` (see `AHELP FACTIONPLAYEREFFECTS` and [docs/organizations.md](../organizations.md)). For a player character in such a faction, the engine attaches one Prop_FactionEffects. It reads the faction rules each time, instantiates the listed abilities for the bands the character currently falls in (or whose mask they fit), forwards stat, tick and message handling to them, and drops them when the character leaves the band.

Notes:

- The effects are transient: they are recomputed from faction data and are not saved as character effects. Do not attach this property yourself; edit the faction file.
- Non-player mobs never get it.

### Prop_Familiar

**Hosts:** mobs

**Parameters:**

```text
The familiar code number or name
```

**Example:**

```text
rat
```

This property is a special MOB property created for the find familiar spell. It handles all the special benefits of the familiar type, along with the benefits to the owner. It will also destroy the familiar when no longer following the owner.

Notes:

- A familiar is a bond: it is destroyed when unlinked from its master.

### Prop_FightSpellCast

**Hosts:** items (shipped help: Weapon (Item))

**Parameters:**

```text
A percentage, and proper spell names separated by semicolons
Spell names may optionally have parameters in parenthesis
Siege weapons: ALLMOBS, ALLITEMS, ONLYMOBS, ONLYITEMS flags
The above parameters may be followed by an optional mask
```

**Examples:**

```text
50%;Spell_Invisible;Prayer_Sanctuary
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_Portal(Somewhere else);MASK=-RACE +Dwarf
```

Causes a spell to be cast upon the target being struck during melee combat. This property also supports a % chance of triggering. Siege weapons will also affect a random mob/item on the deck, or all if ALLMOBS/ALLITEMS added, orselectively if ONLYMOBS/ONLYITEMS added.

The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The flag NOOWN can be used to skip item ownership checks.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

Notes:

- `setStat` no longer drops CLASS, TEXT and NAME.

### Prop_FirstEntry

**Hosts:** rooms, areas.

**Parameters:**

```text
TEXT="..." ONCE=CHARACTER|PROFILE SETVAR=key=value TATTOO=name ID=stable-id ALIASES=old-id LEGACYKEYS=old-token
```

**Example:**

```text
TEXT="A hush falls as you pass beneath the old arch." ONCE=PROFILE SETVAR=SAW_ARCH=1 ID=old-arch
```

CoffeeSUD addition. A once-only message for a character's first entry into the room or area. `TEXT` accepts `VARIES` markup. `ONCE=CHARACTER` (the default) keeps the marker on the character, `ONCE=PROFILE` shares it across the player profile. `SETVAR` is set on the entering character; `TATTOO` gives them a tattoo. A stable `ID` keeps the same once marker after the host is renamed or moved. See [Saved identities](#saved-identities).

### Prop_Fumble

**Hosts:** items, areas, rooms

**Parameters:**

```text
CHANCE=[PCT] (ITEM=WORN/HERE/HAVE/PRESENT) (MASK=[ZAPPERMASK])
(MSG="<S-NAME> fail(s) to do @x1 to <T-NAME> because @x2.")
(PRIVATE=TRUE/FALSE) (AID=[ABILITY ID],..) (WEAPON=TRUE/FALSE)
(ATYPE=[ABILITY TYPE],...) (ADOMAIN=[DOMAIN],...)
(CAST=[ABILITY ID(ARGS)],...)
```

**Examples:**

```text
chance=50% mask="-race +elf" aid=Spell_Fireball,Spell_Light
chance=50% atype=spell,skill domain=stealthy
chance=50% atype=spell cast=Spell_Fireball
```

Causes a spell or skill to fumble and show a message according to the given chance%. The skills to fumble are identified as belonging to the given ATYPE (if specified) AND either belonging to the ADOMAIN, having an AFLAG, or having its Ability ID in the list of AID. The skill will fumble if the caster matches the given MASK zappermask. The message MSG= may be specified, with special codes @x1 for the skill name, and @x2 for the affected item name. The message can be shown PRIVATE= ly, or to everyone in the room. If the affected is an item, the ITEM= parameter can be used to specify the state that the item must be in to cause the fumble. Lastly, a CAST= argument can be given to cause fumbles to be accompanied by a spell being cast by the item against the skill user.

Prop_Fumble also has another mode of operation. If WEAPON=TRUE, then instead of fumbling skills, it will fumble attacks and cause the attacker to suffer the consequences.

Notes:

- `ADOMAIN` is now matched against the domains and the domain/flag lists are read even without `ATYPE`.

### Prop_Furnishing

**Hosts:** items

**Parameters:**

```text
(ROOMEFFECT=[ABILITY](PARMS)) (ROOMBEHAVIOR=[BEHAVIOR](PARMS)) (COMFORT=[N]) (ACTS=[NAME],...)
```

**Examples:**

```text
ROOMEFFECT=Prop_RoomLit COMFORT=2 ACTS=fire
COMFORT=3 ROOMBEHAVIOR=Emoter(min=5 max=9 chance=50;creaks.)
```

CoffeeSUD: furniture that changes its room. While the item is placed in a room (it cannot be picked up, as a decorated or mounted thing) or lies on the floor of an owned room, the ROOMEFFECT and ROOMBEHAVIOR are on the room; when it leaves, they go, and they are never saved with the room. COMFORT adds to the room's comfort, which the script function COMFORT() reads. ACTS makes it a workstation of those names with no bonus (AHELP PROP_WORKSTATION): ACTS=fire serves a craft that needs a fire.

### Prop_GenLock

**Hosts:** rooms

**Parameters:** none.

CoffeeSUD: GENLOCK. REGENERATE AREA keeps this room as it is, with its exits, when it builds the rest of the area again from its GENSOURCE: for rooms written or changed by hand in a generated area. REGENERATE LOCK and UNLOCK put it on the room you are in and take it off.

### Prop_HaveAdjuster

**Hosts:** items

**Parameters:**

```text
statistic names followed by a +, -, or = and a value
The above parameters may be followed by an optional mask
```

**Examples:**

```text
str+2 dex-2 class=thief race=elf allset=aname-3
str+2 ableprofs="spell_fireball(10)" MASK=-Race +Dwarf
```

So long as the item with this property is owned, the listed effects will be given to the owner. All parameters use the + or - adjuster, except the class, race, gender, ableprofs, and ablelvls parameters. Valid parameters are as follows:

strength dexterity constitution charisma wisdom intelligence

maxstr maxdex maxcon maxcha maxwis maxint (maximum trainable)

savepar savefir savecol savewat savegas savemin savegen savejus

saveaci saveele savepoi saveund savemag savedis savetra

ability (magical level), armor, attacks (prowess), damage (max), disposition (see AHELP ADJDISPOSITION), level, rejuv(!NO!), senses (see AHELP ADJSENSES), speed (#attacks), height, weight, ambiance+``,gender, class, race, hitpoints, hunger (stomach size), mana, movement, thirst (stomach size) conversion weightadj saveblunt savepierce saveslash savespells saveprayers savesongs savechants critpctweapons critpctmagic critdmgweapons critdmgmagic faith deity rejuvrate xpadjpct. Ableprofs and ablelvls is a commma-delimited list of ablityids followed by a number in parenthesis to adjust proficiency or casting level by.

By default, the values + or - are added to the existing values. However, if MULTIPLYPH=true is included in the parameters, then attack, armor, damage, weight and height will instead have their values multiplied by the given value / 100, and speed by the given value itself (SPEED+1.5 is one and a half times). Likewise, if MULTIPLYCH=true is included, then hitpoints, mana, and movement are similarly multiplied instead of added. All other values act the same.

Parameters may also include ALLSET=\[SET_NAME\]-\[NUMBER\] where SET_NAME is a arbitrary un-spaced name of the set to which the item belongs. Only when \[NUMBER\] items in that set are possessed (or worn for Prop_WearAdjuster) will all of the stats be adjusted according to their parameters.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

Notes:

- `RONDEATH` defaults to false: an item adjuster no longer goes when its owner dies unless `RONDEATH=TRUE`.
- An additive fractional `SPEED` now counts. `MULTIPLYPH`/`MULTIPLYCH` change the arithmetic as the description says.
- The same adjuster grammar is shared by Prop_Adjuster, Prop_WearAdjuster, Prop_RideAdjuster, Prop_HereAdjuster, Prop_UseAdjuster, Prop_EnterAdjuster and Prop_StatAdjuster (differing in when they apply).

### Prop_HaveEnabler

**Hosts:** items

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the item with this property is owned, the owner will be granted the spells, skills listed. The percentage given is optional, and will be the proficiency of the skill. If a percentage is not given, the default is 100%.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_HaveResister

**Hosts:** items

**Parameters:**

```text
resistance names followed by percentages
Parameters may be followed by an optional mask
```

**Examples:**

```text
magic 50% poison -10%
magic poison 50% pierce -10% MASK=-RACE +Dwarf
fire 40% SOURCEMASK="-RACECAT +Dragon"
```

CoffeeSUD: SOURCEMASK="\[ZAPPER MASK\]" makes it work only against the attackers it fits; MASK= is still whom it protects.

So long as the item with this property is owned, the owner will gain certain resistances and immunities in the parameters. The effects which may be included are:

Resistances: gas fire electricity mind magic cold acid water evil (undead)

Resistances: justice (as per many thief abilities)

Resistances: weapons blunt pierce slash non-magical-weapons non-silver-weapons

Resistances: -- weapons get reduced % damage per round, lvl based, with limits.

: : -- always modifier makes weapon resists always work, no limits.

Resistances: spell chant song prayer thief_skill (ability types)

Resistances: enchantment/charm kicking vexing conjuration (ability domains)

Immunities : teleport (includes gate, summon) holy disease poison

Misc : debuf-duration (pct is the maximum debuf ticks remain)

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

Notes:

- The kind-by-kind weapon protections now stack: each keeps its own remaining protection in a round.

### Prop_HaveSpellCast

**Hosts:** items

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the item with this property is owned, the owner will be under the effect of the listed spells. The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_HaveZapper

**Hosts:** items

**Parameters:**

```text
masking parameters described below, optional zap message
```

**Examples:**

```text
-class +mage -race +elf -evil
MESSAGE="<O-NAME> flies off <S-NAME>" -class +mage
-class +mage "event=accuse,trespassing" "message=^N"
```

Whenever this item is picked up by a mob, the item will be zapped out of the mobs hands if they meet the parameters. An optional MESSAGE parameter can define what is seen when the zap occurs. Also optionally, the parameters can start with the word ACTUAL to change zapping behavior to look at actual values instead of perceived values (this about a class check applied to a Charlatan.). The parameters can also start with the word CONTENT to have the zapper apply equally to a container and its contents (at least until the thing with the zapper is separated from the container). If the parameters include any alignment checks, the item will end up with an appropriate ambiance as well. The optional EVENT parameter is a comma-delimited set of strings, where the first is one of ZAP (default) or ACCUSE followed by a comma and the crime key from a laws.ini type definition. See AHELP ZAPPERMASK, where allowed can possess, and disallowed is zapped.

Notes:

- `CONTENT` zaps when taking a thing out of a container as taking the container would. It does not zap on wear (one cannot wear what one could not pick up).

### Prop_Hazard

**Hosts:** rooms, areas

**Parameters:**

```text
TYPE=[TYPE] DAMAGE=[DICE/FORMULA] (SAVE=[TYPE]) (DRAIN=MOVE/MANA) (AFFECT="[ID] [PCT]%") (ROOMS="[MASK]") (WHEN="[MASK]") (PROTECT="[MASK]") (EVERY=[TICKS]) (ONSET=[TICKS]) (ESCALATE=[PCT]) (WARN="[TEXT]") (DARKONLY) (LITONLY) (NONPC) (NAME="[TEXT]") (MSG="[TEXT]")
```

**Examples:**

```text
TYPE=COLD DAMAGE=1d6 SAVE=COLD WHEN="-WEATHER +BLIZZARD +SNOW" PROTECT="-EFFECTS +Spell_ResistCold" ESCALATE=20
TYPE=HEAT DAMAGE=@x1/2 ROOMS="-DOMAIN +DESERT" WARN="The sand burns through your boots."
```

CoffeeSUD: a hazard zone. Each tick (EVERY) those in the room, or in the area (ROOMS a mask on each of them, as -DOMAIN +MOUNTAINS, for some of its rooms), take DAMAGE as TYPE's damage (COLD, HEAT, ACID, GAS, POISON, ELECTRIC, HOLY, UNHOLY, DISEASE, THINAIR), so resistances count: dice as 1d6+2 or a formula, @x1 their level. SAVE names the save that halves it (the type's own by default). DRAIN=MOVE or MANA takes it from moves or mana instead (THINAIR's default). AFFECT may give an effect, that percent of the time. WHEN is a mask checked on each every tick (the weather, the time), PROTECT the mask of those it spares; ONSET ticks pass before it starts, ESCALATE adds that percent each time in a row; WARN is said once as it starts. DARKONLY and LITONLY: only in the dark, or the light; NONPC: players only. NAME is what hurts (the biting cold), MSG the damage line.

### Prop_HelmRestrict

**Hosts:** items (a vessel or vehicle).

**Parameters:**

```text
NAVMASK="[zapper mask]" MESSAGE="[refusal text]" ONFAIL=[script function]
```

**Example:**

```text
NAVMASK="-PLAYERS" MESSAGE="Only the crew may touch the helm."
```

CoffeeSUD addition. Restricts who may give anchor, brake, course, steering, sailing and tender commands to the vessel. Sailor NPC crew aboard and builders with item permissions always pass. `ONFAIL` names a function called on the vessel when a command is refused. See the travel chapter (30).

### Prop_HereAdjuster

**Hosts:** rooms

**Parameters:**

```text
statistic names followed by a +, -, or = and a value
The above parameters may be followed by an optional mask
```

**Examples:**

```text
str+2 dex-2 move+100 class=thief race=elf gender=F
str+2 ableprofs="spell_fireball(10)" MASK=-RACE +Dwarf
```

So long as the room with this property is occupied, the listed effects will be given to the occupants. All parameters use the + or - adjuster, except the class, race, and gender, ableprofs, and ablelvls parameters. Valid parameters are as follows:

strength dexterity constitution charisma wisdom intelligence

maxstr maxdex maxcon maxcha maxwis maxint (maximum trainable)

savepar savefir savecol savewat savegas savemin savegen savejus

saveaci saveele savepoi saveund savemag savedis savetra

ability (magical level), armor, attacks (prowess), damage (max), disposition (see AHELP ADJDISPOSITION), level, rejuv(!NO!), senses (as AHELP ADJSENSES), speed (#attacks), height, weight, ambiance+``,gender, class, race, hitpoints, hunger (stomach size), mana, movement, thirst (stomach size) conversion weightadj saveblunt savepierce saveslash savespells saveprayers savesongs savechants critpctweapons critpctmagic critdmgweapons critdmgmagic faith deity rejuvrate xpadjpct. Ableprofs and ablelvls is a commma-delimited list of ablityids followed by a number in parenthesis to adjust proficiency or casting level by.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_HereEnabler

**Hosts:** rooms, areas

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

Whenever someone enters the room or area, they will be granted the spells, skills listed. The percentage given is optional, and will be the proficiency of the skill. If a percentage is not given, the default is 100%. When they leave, they lose the spells/skills.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_HereSpellCast

**Hosts:** rooms

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the room with this property is occupied, the occupants will be under the effect of the listed spells. The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_Hidden

**Hosts:** mobs, items, exits, rooms, areas (shipped help: MOB, ROOM, ITEM, EXIT, ETC)

**Parameters:**

```text
(UNLOCATABLE) (BUBBLES)
```

**Example:**

```text
unlocatable
```

An alternative to setting something permanently hidden, this will keep a hidden until the mob performs some act which would normally un-hide the mob. After that, a period of time must elapse before the property reengages and makes a mob hidden again. All other object types just stay hidden

Optional flags include:

Unlocatable - makes the item unlocatable by spells, or an area by the WHERE command.

Bubbles - makes the hidden property not only apply to the object, but to an owner.

### Prop_HomeBeacon

**Hosts:** rooms

**Parameters:** none.

CoffeeSUD: a home. Sleeping here (or HOME SET) makes it the start room of every character of the party who has privileges here, so RECALL and HOME bring them back to it. HOMEBEACONS=true in the ini makes every owned room a home without it; HOMERECALL is how many turns HOME takes (3 by default, 0 for no HOME).

### Prop_Homestead

**Hosts:** rooms, areas

**Parameters:**

```text
(MASK="[ZAPPER MASK]") (SKILLS=[SKILL ID],...) (CLAIM=TRUE/FALSE/PROFILE) (MAXROOMS=[N]) (PRICE=[N]) (NOTAX)
```

**Example:**

```text
SKILLS=Construction,Masonry MAXROOMS=3
```

CoffeeSUD: land a character may build on without buying a deed, on a room or every room of an area. MASK is a zapper mask the builder must pass, and SKILLS the building skills allowed (all when not given). With CLAIM=TRUE (the default) the first build in a room with no title claims it: the room gets a Prop_LotsForSale owned by the builder (by @PROFILE, the whole profile, with CLAIM=PROFILE or PROFILEPROPERTY=true), priced PRICE (1 by default, and at 1 no property tax is due; NOTAX keeps it at 1), and with MAXROOMS a Prop_ReqCapacity, so the lot grows by the usual building rules to MAXROOMS rooms at most. If that build does not start (no materials, say), the claim is taken back. With CLAIM=FALSE they may build there with no title at all.

### Prop_IceBox

**Hosts:** items, rooms

**Parameters:** none.

Any items placed in the room or container with this property will not spoil. When removed, however,spoilage will resume.

### Prop_ImproveGather

**Hosts:** mobs, items, areas, rooms

**Parameters:**

```text
AMT=[MULTIPLIER] MASK=[ZAPPERMASK] SKILLS=ALL/[SKILLID LIST]
```

**Example:**

```text
amt=3 mask="-SEASON +SPRING" skills=Farming,Foraging
```

Alls the mob, item, or location to bestow the ability to multiply the yield from certain gathering skills. SKILLS can be either a list of Ability IDs, or the word "ALL" to apply to all gathering skills. A MASK would always apply to the person doing the skill, and the AMT is the integer number to multiply the yield by (or 0 to make it go away altogether!).

See AHELP ZAPPERMASK, where allowed is improved, and disallowed is not.

### Prop_InnRoom

**Hosts:** rooms

**Parameters:**

```text
(HOURS=[N]) (MSG="[TEXT]")
```

**Example:**

```text
HOURS=6 MSG="You wake in a soft bed, well rested."
```

CoffeeSUD: a room to rest in: a WAIT (or MPPASSTIME) of HOURS game hours or more (1 by default) ends with the one who waited fully rested: hit points, mana and moves.

### Prop_InstantDeath

**Hosts:** rooms, areas, items, mobs

**Parameters:**

```text
(MASK="[zapper]") (DAMAGE=[NUM]) (DMGPCT=[0-100]) (MSG="[STR]")
```

**Examples:**

```text
mask="-race +dwarf"
damage=10 dmgpct=0 msg="<S-NAME> <DAMAGES> <T-NAME>"
```

Will (eventually) cause the death of anyone using the item, attacking the mob, or entering the room or area. If a mask if given, only those matching the mask are killed. If a DMGPCT \< 100 is used, damage is done and the MSG is displayed. If a DAMAGE \> 0 is used, but DMGPCT=0, then a flat amount of damage is done.

See AHELP ZAPPERMASK, where allowed is killed, and disallowed is spared.

Notes:

- On an area it now kills, as documented (it did nothing before).

### Prop_Invisibility

**Hosts:** mobs, items, exits

**Parameters:** none.

An alternative to setting a mob permanently invisible, this will keep the MOB invisible until the mob performs some act which would normally dispel the invisibility. After that, a period of time must elapse before the property reengages and makes the mob invisible again.

### Prop_Invokable

**Hosts:** items

**Parameters:**

```text
(WORD="[WORD]") (VERB=[VERB]) SPELLS="[ID];LEVEL=[N],[ID]" (TARGET=SELF/ENEMY/ROOM) (CHARGES=[N]) (RECHARGE=DAWN/DAILY/TICKS=[N]) (COOLDOWN=[TICKS]) (REQUIRE=WORN/HELD) (MASK="[ZAPPER MASK]") (MSG="[TEXT]") (EMPTYMSG="[TEXT]")
```

**Example:**

```text
WORD=Ignis SPELLS="Spell_Fireball;LEVEL=12" TARGET=ENEMY CHARGES=3 RECHARGE=DAWN REQUIRE=WORN EMPTYMSG="The ring is cold."
```

CoffeeSUD: an item's powers on demand, for anyone, with no class skill. Its holder says WORD, or types VERB and its name (INVOKE \[ITEM\], the default without a WORD). It casts SPELLS, each at LEVEL (or the item's level), on its user (SELF), their foe (ENEMY) or about them (ROOM). CHARGES is how many uses it holds (what is left is kept with it); RECHARGE fills them again at dawn, each game day, or every N ticks. COOLDOWN ticks pass between uses. REQUIRE: it must be worn, or held. MASK is who may use it. MSG is what the room sees (\<S-NAME\> the user, \<T-NAME\> the item); EMPTYMSG what they are told when it is spent. Identifying it tells its charges and recharge.

### Prop_ItemBinder

**Hosts:** items

**Parameters:**

```text
TO=CHARACTER/ACCOUNT/CLAN/PLAYER/NPC/NOONE ON=PICKUP/EQUIP
: (MESSAGE=[ZAPMSG]) (DEATHKEEP=TRUE/FALSE)
```

**Example:**

```text
TO=CHARACTER ON=PICKUP MESSAGE="Bound -- you can't do that."
```

Allows an item to be bound when an event occurs to the person, account, or clan that either picks it up or first equips it. Once bound, an item can only be picked up by that same player, account, or clan. The zap message is also configureable, as is whether the item persists with the player over death.

Notes:

- `ON=` values match abbreviations correctly now (it compared against the TO string). In single-player the binding is to a character name.

### Prop_ItemGrowth

**Hosts:** items

**Parameters:**

```text
(GAIN=KILL/HIT/USE/WORNTICK) (XP="[FORMULA]") (KILLMASK="[ZAPPER MASK]") STAGES="[N]:NAME='[NAME]' PROPS=[ID]([ARGS]);[N]:..." (MSG="[TEXT]") (BIND=CHARACTER)
```

**Example:**

```text
GAIN=KILL XP="@x1" KILLMASK="-RACECAT +Undead" STAGES="100:NAME='a humming blade' PROPS=Prop_WearAdjuster(dam+2);400:NAME='Dawnbringer' PROPS=Prop_WearAdjuster(dam+5)" MSG="$p shudders as it grows stronger!" BIND=CHARACTER
```

CoffeeSUD: an item that grows with use. GAIN: the kills of the one who wields or wears it (KILL; KILLMASK is which), its hits (HIT), anything done with it (USE), or each tick worn (WORNTICK). Each gives XP (a formula, @x1 the slain's level; 1 by default). STAGES: at each amount it takes the NAME (DISPLAY too) and PROPS of that stage (a semicolon before each next stage), and its holder is told MSG ($p the item). BIND=CHARACTER: it grows for its first owner only. Its growth, stage and owner are kept with it; identifying it shows the stage and the way to the next.

### Prop_ItemModifiers

**Hosts:** items.

**Parameters:** a strict saved JSON record. Do not write it by hand.

CoffeeSUD addition (item modifiers, optional). Applied by `MODBASE` generation to standard generic items and equipment. It carries a saved record: the item's identity, base pin and revision, display baselines, and per-modifier identity, source pin, tier, group, tags, numeric rolls and protection. `ITEMMODS [carried item]` inspects it; `MODCRAFT` makes controlled selective edits. Do not attach an empty property by hand: use `ItemModifiers.initialize` on freshly generated, unowned items, and `ItemModifiers.copyOf` to clone one with fresh identities and preserved values.

Notes:

- Numeric deltas feed the optional combat-profile collector when the item is correctly worn. A generic copy bound to another item, a corrupt record, or one over the campaign's `max_modifiers` grants nothing and does not stop its wearer's combat; the log, COMBATRESULT and `WORLD CHECK ITEMMODS` name it.
- Disabling `items.modifiers` suspends bonuses and crafting but keeps the records and receipts.
- This first family does not own arbitrary AFFECT layers, new crafting currencies or staged choices. See chapter 17 (item modifiers).

### Prop_ItemNoRuin

**Hosts:** items

**Parameters:**

```text
(NEVERMIND)
```

When placed on an item, that item will be exempt from your systems loot policy. The item will not be deleted, corrupted, ruined, or otherwise affected by the policy, regardless of your ini file settings. If the NEVERMIND argument is given, however, it will always be ruined no matter what.

### Prop_ItemSet

**Hosts:** items

**Parameters:**

```text
SET=[ID] (NAME="[TEXT]") (COUNT=WORN/HELD) (TIER[N]="[ID]([ARGS]);[ID]([ARGS])") (PIECES=[N]) (ONMSG="[TEXT]") (OFFMSG="[TEXT]") (MASK="[ZAPPER MASK]")
```

**Examples:**

```text
SET=drowned_king NAME="Regalia of the Drowned King" PIECES=5 TIER2="Prop_WearAdjuster(savewat+20)" TIER4="Prop_WearSpellCast(Spell_WaterBreathing);Prop_WearEnabler(Chant_Tsunami)" ONMSG="The regalia stirs."
SET=drowned_king
```

CoffeeSUD: a piece of an item set. Every piece has SET, the set's id; one piece (or a line of world/itemsets.txt, SET=\[ID\] NAME=... TIER2=..., for a set none of whose pieces says) has the tiers: TIER\[N\] what wearing N pieces gives (COUNT=HELD: carrying them), properties and their arguments in brackets, a semicolon between. Every tier met applies: its properties go on one of the counted pieces, unsaved, and come off when a piece does. ONMSG and OFFMSG tell the wearer as a tier comes and goes ($t the tier); MASK is who may have them; PIECES how many there are. LOOK at a piece shows "Set: 3/5 worn" and each tier. Scripts: SETCOUNT(\[CODE\] \[SET\]) and SET_PROG. The ALLSET of Prop_HaveAdjuster and Prop_WearAdjuster now gives each piece its own number of pieces needed.

### Prop_ItemSlot

**Hosts:** items

**Parameters:**

```text
(NUM=[SLOTS COUNT]) (TYPE=[STRING]) (LEVEL=NONE/HIGH/ADD)
(REMOVABLE=[TRUE/FALSE]) ; [XML]
```

**Example:**

```text
NUM=4 TYPE=Gems REMOVABLE=TRUE
```

When placed on an item, that item will have available slots into which other items, those with the Prop_ItemSlotFiller property, can be PUT using the PUT command. When this is done, the item with this property will gain the effect benefits of the other item in addition to its own.

The NUM parameter determines how many slots are available. The TYPE parameter must match the TYPE parameter from the Prop_ItemSlotFiller of the item being PUT. The REMOVABLE property sets whether the items in the slots may be removed. The LEVEL argument affects how slot fillers affect the item level: none, equal to the highest, or added together. See also Prop_ItemSlotFiller.

Notes:

- The look text for slots is fixed.

### Prop_ItemSlotFiller

**Hosts:** items (shipped help: ITEMS (NOT CONTAINERS))

**Parameters:**

```text
(NUM=[SLOTS COUNT]) (TYPE=[STRING]) (SKIPS=[ID,...])
(ADDS="[ID(args),...]")
```

**Example:**

```text
TYPE=Gems ADDS="Prop_HaveAdjuster(STR+1)"
```

When placed on an item, that item will put able to be PUT, using the PUT command, into the slots provided on an item with the Prop_ItemSlot property. When this is done, the item into which this item is PUT will gain all the effect benefits of this item.

The NUM parameter determines how many slots this item takes up. The TYPE parameter must match the TYPE parameter from the Prop_ItemSlot of the item being PUT into.

The SKIPS property is a comma delimited list of Ability IDs which should be ignored when determining which effects on this item should be applied to the slotted item.

The ADDS property is a comma delimited list of Ability IDS/Properties for adjusting the item or the wearer of the item whose slot is being filled. Adjustments to physical stats like attack, armor, damage, speed, or disposition/senses will only affect the slotted item itseld, while all other character state and stat adjustments will impact the wearer/wielder of item.

Notes:

- `ADDS` is cut at the matching parenthesis, so abilities whose text contains parentheses work.

### Prop_ItemTransReceiver

**Hosts:** mobs, items, rooms (shipped help: Rooms, MOBs, Items (Containers))

**Parameters:**

```text
The transporter KEY
```

**Example:**

```text
KEYX123
```

See Prop_ItemTransporter above.

### Prop_ItemTransporter

**Hosts:** mobs, items, rooms (shipped help: Rooms, MOBs, Items (Containers))

**Parameters:**

```text
The transporter KEY
```

**Example:**

```text
KEYX123
```

Any room, items, or mob with this property becomes a medium for transportation to another mob, room, or item with the Prop_ItemTransReceiver property with the same key. Any items dropped in the room, picked up or give to the mob, or placed inside the containers with this property will be instantly transported to another room/item/mob with the Prop_ItemTransReceiver property which has the exact same KEY parameter. If there is more than one Prop_ItemTransReceiver found with the proper key, the destination will go randomly to one of the choices.

Notes:

- Container loops are detected; stale destinations are refreshed.

### Prop_JScript

**Hosts:** mobs, items, exits, rooms, areas

**Parameters:**

```text
SCRIPT=[FILE] ([NAME]=[VALUE])...
```

**Example:**

```text
SCRIPT=world:js/props/bloodblade.js KILLS=0
```

Runs a JavaScript file (under resources/, or world:path) that may define affectPhyStats(host, affected, stats), affectCharStats(host, mob, stats), affectCharState(host, mob, state), okMessage(host, msg) (false refuses the message), executeMsg(host, msg), tick(host, tickID), onAdded(host), onRemoved(host) and catchUp(host, ticks) (AHELP CATCHUP). The other parameters are the file's parms, numbers as numbers; what a hook changes in parms is written back to the property's text, and so saved with its host. guides/JavaScriptSUD.html has an example.

### Prop_Journey

**Hosts:** items, mobs, exits, rooms.

**Parameters:**

```text
DEST=[room or port] DURATION=[game hours] VIA=[transit room] FARE=[price]
PARTY=LEADER|EACH PERHEAD=true
MISSING=WAIT|CONTINUE|RESCUE|CANCEL|RETURN
DEAD=CONTINUE|WAIT|CANCEL|RETURN
DISCONNECTED=TRAVEL|WAIT|CONTINUE|CANCEL|RETURN
CANCEL=HERE|RETURN REFUND=NONE|FULL|UNUSED
LEAVEMSG="[text]" ARRIVEMSG="[text]" EVENTS="[chance%:action;...]"
```

**Example:**

```text
DEST=Harbor#1 DURATION=6 VIA=Ferry#1 FARE=5 LEAVEMSG="The ferry shoves off." ARRIVEMSG="The ferry bumps the quay."
```

CoffeeSUD addition. Entering the host (or typing `JOURNEY <host>`) starts a passage for the nearby party: the party is moved to the `VIA` room (default: where it stands), `DURATION` game hours pass, and then the party arrives at `DEST`. Each game hour rolls the `EVENTS` entries: an action is a script function, `LOAD MOB <spec>`, or `CATALOG <encounter table>`; a function that returns PAUSE waits for `JOURNEY RESUME`. Combat pauses travel. Saves retain elapsed hours, remaining leg time, scenes and individual passengers (see [Prop_JourneyState](#prop_journeystate)).

Policies decide what happens when a passenger is missing, dead or disconnected, checked before the journey advances or arrives. `WAIT` pauses without spending time; `CONTINUE` releases the seat; `RESCUE` obeys movement checks; `RETURN` brings the present party back to the origin (a refused return stays paused). Saved fare receipts support a full or unused-time refund once per payer.

Defaults: `MISSING=WAIT DEAD=CONTINUE DISCONNECTED=TRAVEL CANCEL=HERE REFUND=NONE PARTY=LEADER PERHEAD=true`.

Notes:

- A positive `DURATION` and a valid `DEST` are required; otherwise the journey does not start and the player is told why.
- Script trigger: `JOURNEY_PROG START|HOUR|ARRIVE|CANCEL|ALL` with `$g` the stage, `$0` the completed game hours and `$1` the destination room ID.
- Options use the strict [authoring option syntax](#authoring-option-syntax).

### Prop_JourneyPassenger

**Hosts:** mobs.

**Parameters:** the passenger's seat token, set by the engine.

CoffeeSUD internal. A marker the journey system puts on each party member while a journey is under way ([Prop_JourneyState](#prop_journeystate) is on the leader). It shows nothing to the player and holds the seat identifier so that a missing or dead passenger can be recognised and the MISSING/DEAD/DISCONNECTED policies of the journey applied. It is removed on arrival or cancellation. Builders do not attach it; define journeys with [Prop_Journey](#prop_journey).

Note: the class extends StdAbility, not Property, so its classification code is the ordinary ability one; it is listed here because it lives with the properties.

### Prop_JourneyState

**Hosts:** mobs (the party leader).

**Parameters:** a saved XML record (definition, origin, destination, transit room, remaining time, seats, receipts). Set by the engine.

CoffeeSUD internal. One passage in progress, kept on the party leader and saved with the character so that a journey survives a save and reload. The `JOURNEY` command (status, `JOURNEY RESUME`, `JOURNEY CANCEL`) reads it; `Travel.beginJourney` creates it from a [Prop_Journey](#prop_journey) definition. It shows `(Travelling)` as its effect text, spends one game hour at a time (events from the definition's `EVENTS` roll each hour), pauses while anyone fights, and rebuilds its timer from the remaining time after a reload.

Notes:

- It refuses to start if the party leader already has one, if time is advancing, if the destination or transit room is missing, or if a member is fighting.
- Fare receipts are stored in the record so that `REFUND=FULL|UNUSED` on cancellation can pay back once per payer.
- Never attach it by hand; use Prop_Journey on a room, exit, item or mob.

### Prop_KeyRules

**Hosts:** items (shipped help: Items (keys))

**Parameters:**

```text
(CONSUME=true) (BREAK=[PERCENT]) (RETURN=[ROOM ID]) (MASK="[ZAPPER MASK]") (MSG="[TEXT]")
```

**Examples:**

```text
CONSUME=true
RETURN=Castle#12 MASK="-CLASSES +Thief" MSG="The key will not turn for you."
```

CoffeeSUD: rules for a key. CONSUME: it crumbles once it has unlocked something; BREAK: that percent of the time it snaps when it is used; RETURN: once used it goes back to that room; MASK: who may use it (MSG is told to the others). A key's code may also be a list (crypt;vault: it opens both) or end in \* (jail.\*: a master key for jail.1, jail.2...), and a key on a keyring is found however deep the keyring is in a pack.

### Prop_KnownRecipes

**Hosts:** mobs

**Parameters:**

```text
[SKILL ID]|[RECIPE NAME OR WHOLE RECIPE LINE], one a line
```

**Example:**

```text
Weaponsmithing|moonblade
```

CoffeeSUD: the recipes a character knows, kept with them. A whole recipe line (tabs and all) is a recipe of that skill as a recipe book's would be, without the book; a name makes one of the skill's own recipes known, as one marked HIDDEN=1 (AHELP RECIPEKEYS), which no one else sees. LEARN FROM a recipe book copies its recipes in, and scripts use MPLEARNRECIPE and KNOWSRECIPE(). With PROFILERECIPES=true in the ini, every character of the profile knows what any of them knows.

### Prop_LangTranslator

**Hosts:** mobs, items, rooms

**Parameters:**

```text
(NOTRANSLATE) ([PERCENT]) (`WORDS) ([LANGUAGE ID]) ...
(#[PLAYERNAME])
```

**Examples:**

```text
100% Dwarven 50% Elvish
Dwarven Elvish
NOTRANSLATE 75% `smile `beer Dwarven #BOB
```

This is property has two distinct but related purposes. The original is to allow the mob or item to translate any words spoken in the room in a language other than Common, into Common. The mob or item is heard saying the translation, a line everyone in the room hears (not a SAY of its own, so it is not translated again). The percentage chance given, which defaults to 100%, will be the chance that the mob or item will translate any given sentence.

The other function of this property is activated when NOTRANSLATE is one of the parameters. In this case, this property on a mob will allow that mob to understand languages they otherwise would not. In fact, understanding can be limited to specific words by prefixing a language id with one or more `words. This mode can also support passing commands through to mobs by including the names of trusted players using the # syntax, or a wildcard name of #\*.

### Prop_LanguageImmersion

**Hosts:** mobs, items, rooms, areas.

**Parameters:**

```text
LANGUAGE=Orcish HEAR=1 READ=3 CHANCE=20% START=5 MAX=60 GRANTAFTER=25 ID=stable-id ALIASES=old-id LEGACYKEYS=old-token
```

**Example:**

```text
LANGUAGE=Orcish HEAR=1 READ=2 CHANCE=25% START=5 MAX=60 GRANTAFTER=20 ID=orc-camp
```

CoffeeSUD addition. Lets a character pick up a language by exposure. `LANGUAGE=ANY` covers every language. `HEAR` and `READ` are the proficiency points gained per successful exposure by hearing or reading it; `CHANCE` is rolled per exposure. A language the character does not know is granted at proficiency `START` after `GRANTAFTER` exposures (within the world's `MAXLANGUAGES`). Knowledge grows up to `MAX`, immediately updating live effects. Exposure counters and learned abilities survive saves. The property can be carried, given to a creature, or placed on a room or area where immersion should happen. See the languages notes in chapter 11.

### Prop_LanguageSpeaker

**Hosts:** rooms, areas, mobs, items (shipped help: Mobs, Items, Rooms, Exits, Areas)

**Parameters:**

```text
LANGUAGE=[LANGUAGE] (UNDERSTANDS=ONLY) (HOMEONLY=TRUE/FALSE) (NOMOBS=TRUE/FALSE)
Parms(Cont): (PLAYERS=TRUE/FALSE) (; [ZAPPER MASK])
```

**Examples:**

```text
LANGUAGE=Dwarven
LANGUAGE=Dwarven players=true ; -NAMES +BOB
LANGUAGE=SignLanguage homeonly=true ; -NAMES +BOB
```

This is property forces the mobs who become affected by this property to speak the designated language until something else stops them. On a mob, the affected mob will speak the language. On a room, those who enter or are born in a room may be speak the language. On an area, those who enter or are born in a room may speak the language. On an exits, those who enter the exit may speak the language. On an item, those who pick up the item may start speaking the language, while putting it down will stop them.

On a mob, UNDERSTANDS=ONLY removes automatic Common comprehension while allowing learned languages and translators. See PROP_MONOLINGUAL. The parameters affect who will start speaking the language. By default, any non-player mob will become affected. HOMEONLY will force only those whose starting room is in the area to speak. NOMOBS will eliminate all non-players from consideration. PLAYERS will allow players to be affected. An optional zapper mask may follow a semicolon to add additional masks. \<ZAP=disallow\>

Notes:

- `HOMEONLY`: a mob whose start room is in the property's area speaks; one from elsewhere does not.

### Prop_LimitedContents

**Hosts:** items, areas, rooms

**Parameters:**

```text
[MAX NUM] ([MASK]) (; [...] / MSG="[MESSAGE]" ACTUAL=[T/F] LOCKIN=true LOCKMSG="[MESSAGE]" CONSUME=true ONPUT="[ACTIONS]" ONFULL="[ACTIONS]" ONEMPTY="[ACTIONS]")
```

**Examples:**

```text
3 -javaclass +genweapon
3;msg="Can't do it!"
2 -TAGS +DRAGONEYE;0;MSG="It does not fit.";LOCKIN=true;ONFULL="door open here north; echo The dragon's mouth grinds open."
```

This property will ensure that no more than the given number of items that match the given mask may be either put into the container, or dropped in a room. Multiple masks may be specified. The first mask that the item matches will be applied. Matching NONE of the masks means no limit of those items. CoffeeSUD: a container is also a receptacle. LOCKIN keeps what is put in it in it (LOCKMSG is told); CONSUME makes what is put in vanish, while still counting it (kept as TALLY=). ONPUT runs when something goes in, ONFULL when each limit above 0 is met, ONEMPTY when the last thing is taken out: script commands, a semicolon between them, MP left off if you like, $n who did it (as Prop_Switch's). A semicolon inside quotes does not split the parts.

Notes:

- Counts only items matching the mask, as intended (it counted every item).

### Prop_LimitedEquip

**Hosts:** items

**Parameters:**

```text
[MAX NUM](;ID="[IDENTIFIER]" MESSAGE="[MESSAGE]")
(GET=[T/F] MASK="[ZAPPERMASK]")
```

**Examples:**

```text
3
3;id="my unique id" message="Can't do it!"
```

This property will ensure that no more than the given number of items with the same name (or ID) as the ones with this property can be equipped by someone at the same time, or gotten at the same time if GET=TRUE. An optional zappermask can filter who this restriction applies to.

### Prop_LimitedItems

**Hosts:** items

**Parameters:**

```text
[MAX ITEMS](;ID="[IDENTIFIER]")
```

**Example:**

```text
3
```

This property will ensure that no more than the given number of items with the same name (or ID) as the ones with this property will exist, either in the inventory of players, or in rooms. It works by removing the excess items from the map when someone first enters a room with one of the excess items. For this reason, limited items are automatically marked as non-locatable to block them from spells like Locate Object.

This property will cause all player records to be loaded into memory at the end of the normal boot cycle, which will increase boot time and may cause a delay for the first person logging in.

### Prop_LocalLists

**Hosts:** rooms, areas, mobs.

**Parameters:**

```text
LOAD=vocab/lists_underdark.ini     or     KEY="value" ...
Keys: TOD_CHANGE_OUTSIDE TOD_CHANGE_INSIDE HEALTH_CHART DAMAGE_WORDS WEAPON_HIT_DESCS WEAPON_MISS_DESCS CORPSE_BLURBS MAGIC_WORDS GAIT_LIST
```

**Example:**

```text
LOAD=vocab/lists_underdark.ini DAMAGE_WORDS="nicks,gashes,mangles"
```

CoffeeSUD addition. Overrides the stock descriptive lists (those in `lists.ini`) for one creature, room or area. Inline values override `LOAD`. The lookup tries the creature, its room, its area, the parent areas, then the global lists. `LOAD` resolves under `world/vocab/`. Lists use the stock `lists.ini` format: comma-indexed entries, `|` alternatives, `;` grid rows. `HEALTH_CHART` has eleven entries using `<MOB>`; hit and miss text has the six stock attack categories; `DAMAGE_WORDS` accepts flat severity words or a stock grid. Weapons use the wielder's context, including unowned racial natural weapons. [Prop_SkyText](#prop_skytext) overrides its own time-of-day text first; [Prop_WeaponMessages](#prop_weaponmessages) overrides weapon messages.

### Prop_LocationBound

**Hosts:** items, mobs

**Parameters:**

```text
AREA/ROOM/[AREA NAME]/[ROOM ID]
(;ABSOLUTE) (;PLAYEROK) (;TIMEOUT=[SEC])
```

**Examples:**

```text
AREA
ROOM ;PLAYEROK ;TIMEOUT=44
My Happy Area ;PLAYEROK
My Area#12321
```

This property will ensure that the affected mob or item is unable to leave or be taken away from its home location, which is determined by the parameters. Use ROOM or AREA to prevent it from leaving whatever its current room or area is. Using a specific area name or room id is more helpful, however, as it will not only prevent movement away from the given area or room, but will whisk the mob or item BACK to its home if it is ever able to be removed for some reason ANYWAY. If the PLAYEROK argument is given, then the item can be moved anywhere so long as a player is holding, riding, or being followed by the bound thing. If the TIMEOUT argument is given, then the item can be moved outside its bound only until the timer expires. If the ABSOLUTE argument is given, then the AREA and ROOM argument are no longer relative, but absolute to the starting room of the bound item, as if the room number or area name had been specified.

Notes:

- With an unknown ROOM or AREA the thing stays bound to its own room and the log says so.

### Prop_LockRules

**Hosts:** items, exits (shipped help: Exits, Containers)

**Parameters:**

```text
(PICK=NEVER/LEVEL:[N]) (KEYS=[CODE];[CODE]) (RELOCK=[TICKS]) (MSG="[TEXT]")
```

**Example:**

```text
PICK=NEVER KEYS=spare;guard.* RELOCK=20
```

CoffeeSUD: rules for a lock. PICK=NEVER: no thief picks it; PICK=LEVEL:30: only one of that level or more (MSG tells the others). KEYS: key codes it takes besides its own (a code ending in \* takes all that begin so). RELOCK: that many ticks after it is unlocked it shuts and locks again.

### Prop_LootTable

**Hosts:** mobs, items

**Parameters:**

```text
FILE=[TEMPLATE FILE] ID=[ITEM TAG ID] (ROLLS=[MATH]) (ON=DEATH/OPEN/SPAWN/RESET) (CHANCE=[PCT]) (GOLD=[MATH])
```

**Examples:**

```text
FILE=randareas/loot.xml ID=goblin ROLLS=0?2 ON=DEATH GOLD=1?10
FILE=randareas/loot.xml ID=chest ROLLS=1?3 ON=OPEN
```

CoffeeSUD: a loot table: the ITEM tag ID of a template file (resources/randareas/, or the world's own), rolled ROLLS times, each a fresh roll with $mob_level, $area_level, $theme and $player_level set, if CHANCE (a percent, 100 by default) allows, and GOLD as money. ON=DEATH (a mob's default) puts it in the mob's corpse as it dies; ON=OPEN (a container's default) the first time the container is opened, and it stays rolled until its contents reset (a save keeps that); ON=SPAWN or RESET as the mob is brought to life, or as a player first comes into the container's room. A catalog entry (class="catalog") keeps the catalog's cap. A roll that gives nothing because its tracked items (MODBASE) cannot be made yet, as the items.modifiers module is off or its definitions fail CHECK, is not spent: a container rolls at its next opening, a mob's ON=SPAWN roll is made as it dies. resources/randareas/loot.xml has sample tables (goblin, chest).

### Prop_Lore

**Hosts:** items, rooms, mobs.

**Parameters:**

```text
ENTRY=key ON=READ|GET|EXAMINE|ENTER|KILL|DEATH MSG="..." SCOPE=CHAR|PROFILE
```

**Example:**

```text
ENTRY=old_kings ON=READ MSG="The names of the old kings come back to you." SCOPE=PROFILE
```

CoffeeSUD addition. Unlocks an entry of `world/lore/*.ini` (the codex) when its event happens to a character. `KILL` awards the killer of this host; `DEATH` awards the dying character. `SCOPE` chooses a per-character or per-profile unlock. From a script, `MPLORE $n ADD key [PROFILE]` does the same. See `HELP LORE` and the presentation chapter (11).

### Prop_LotForSale

**Hosts:** rooms

**Parameters:**

```text
(RENTAL/GRID/ALLOWTHEFT) [PRICE]
```

**Examples:**

```text
1000000
Bob/100000
GRID 1000000
```

Makes this room and future adjacent rooms available for purchase as a single lot. This property is an extension of the Prop_RoomForSale property, and includes all of its capabilities, PLUS, when this room is sold, adjacent lots will automatically be created, allowing the player to expand his or her property. When the property is sold, or falls out of a players possession, the system will automatically retract the created rooms. The word rental followed by a space may preceed the price to make the price of the property be charged every month automatically out of a Bankers account. Failure to make rent returns the property to sellable status. The word GRID followed by a space may preceed the price to make new rooms automatically link to other rooms via demolishable walls. The word ALLOWTHEFT disables robbery law enforcement on the property.

When property is sold, it will be listed as the owner with a slash and the price. Entering this sort of parameter directly is the quick way to give players or clans property.

This property is similar to prop_lotsforsale, except that the players don't have to purchase each room.

See also Prop_ReqCapacity for the means to set limits on players building abilities on their property.

### Prop_LotsForSale

**Hosts:** rooms

**Parameters:**

```text
([OWNER NAME]/)(RENTAL) (GRID) (ALLOWTHEFT) [PRICE]
```

**Examples:**

```text
1000000
GRID 1000000
Bob/100000
Bob/ RENTAL 100000
```

Makes this room and future adjacent rooms available for purchase. This property is an extension of the Prop_RoomForSale property, and includes all of its capabilities, PLUS, when this room is sold, adjacent lots will automatically be created and put up for sale, allowing the player to expand his or her property. When the properties are sold, or fall back out of any players possession, the system will automatically retract the created rooms. The word RENTAL followed by a space may precede the price to make the price of the property be charged every month automatically out of a Bankers account. Failure to make rent returns the property to sellable status. The word GRID followed by a space may precede the price to make new rooms automatically link to other rooms via demolishable walls.The word ALLOWTHEFT disables robbery law enforcement on the property.

When property is sold, it will be listed as the owner with a slash and the price. Entering this sort of parameter directly is the quick way to give players or clans property.

This property is similar to prop_lotforsale, except that the players must buy each room.

See also Prop_ReqCapacity for the means to set limits on players building abilities on their property.

### Prop_MOBEmoter

**Hosts:** Room, Area, Item, Mob, Exit (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
Same as Emoter Behavior
([BEHAVIOR ID] [BEHAVIOR ARGUMENTS])
```

This property, by default, is simply an ability/effect encapsulation of the Emoter behavior. Please do AHELP Emoter for information on the proper use and parameters of this property. However, if the first word of the arguments is a valid Behavior ID, then this property can encapsulate ANY behavior with all arguments after the first being the arguments of that behavior.

Notes:

- Declares no host type, so `?P` does not list it; add it to a mob by name.

### Prop_MagicFreedom

**Hosts:** rooms, areas (shipped help: Room)

**Parameters:** none.

Cancels all magical effects in the room. If placed on a mob, will cancel magical effects in the same room as the mob.

### Prop_MapGlyph

**Hosts:** rooms.

**Parameters:**

```text
CHAR=I COLOR=y LABEL="Inn" LEGEND      or      HIDE
```

**Example:**

```text
CHAR=I COLOR=y LABEL="Inn" LEGEND
```

CoffeeSUD addition. Sets a custom glyph and legend entry for the room on `MAP`, `AUTOMAP` and the window panel. `HIDE` hides the room's glyph. Rooms and areas made unmappable ([Prop_RoomUnmappable](#prop_roomunmappable)) stay excluded.

### Prop_MapReveal

**Hosts:** items (readable).

**Parameters:**

```text
ROOMS=Area#1-40,Area#45   or   AREA="Area name"      MARK=Area#12:X:"Buried chest" COLOR=r
```

**Example:**

```text
AREA="Old Harbor" MARK=Harbor#12:X:"Buried chest" COLOR=r
```

CoffeeSUD addition. Reading the item teaches the reader saved map knowledge of the listed rooms or the whole area, without counting as a visit. Revealed rooms are drawn dim on `MAP` under `MAPFOG=KNOWN`. `MARK` places a labelled glyph on a room. See chapter 11.

### Prop_MobRank

**Hosts:** mobs

**Parameters:**

```text
RANK=[MINION/NORMAL/ELITE/BOSS/ANOTHER]
```

**Example:**

```text
RANK=BOSS
```

CoffeeSUD: the mob's rank. It gets the rank's numbers, from MOBRANK_\[NAME\] in coffeemud.ini (or a world's world.ini): HP+n% DAMAGE+n% ATTACK+n ARMOR-n (lower is better) SPEED+n, XP\*x (the experience for killing it), RESIST="fire 25% mind 25%" (saving throws), POWER=x (what an encounter budget counts it as) and TAG=text, which LOOK and CONSIDER show. The numbers are read each time the mob's stats are worked out, so one edit retunes every mob of the rank. The defaults: MINION HP-50% DAMAGE-25% ATTACK-10 XP\*0.5 TAG=\[minion\]; NORMAL nothing; ELITE HP+100% DAMAGE+25% ATTACK+10 ARMOR-10 XP\*2.5 TAG=\[elite\]; BOSS HP+300% DAMAGE+50% ATTACK+20 ARMOR-20 XP\*5 TAG=\[boss\]. A world may add ranks of its own (MOBRANK_CHAMPION=...). Scripts read RANK($i) (NORMAL for a mob without one) or test RANK($i == BOSS); masks take -RANK +ELITE +BOSS (only those) and +RANK -MINION (not those).

### Prop_ModExperience

**Hosts:** mobs, items, areas, rooms

**Parameters:**

```text
(=,+,-,*,/)[NUM](%)(SELF)(RIDEOK)(TARGET)(;[MASK])(;;[TYPE])
([Comma-delimited, bracket[]-surrounded groups of above])
```

**Examples:**

```text
500
500;-race +orc
=10%
+20% SELF RIDEOK
[+20% SELF RIDEOK;-race +horse],[=10%;-race +elf]
+20%;-class +Fighter +Mage -Race +Elf
*2
```

Allows the archon to modify the amount of experience gained by each player for slaying the mob. If placed on a mob, the experience will be granted according to the parm, so long as the player meets the mask, and xp meets the type parameters (both optional). The property will apply to the mob with this property him or herself only if the word SELF is included in the first parameters. The property will apply to riders of mobs with this property, unless RIDEOK is included in the first parameters. This property will only apply to targeted xp (like combat) with the TARGET parameter. Other arguments include: NEGATIVE to apply only to lost experience, or POSINEGA to apply to positive and negative experience. POSITIVE experience only is the default. If placed on an item, the xp mod will be applied to the wearer (or wielder or holder) . If placed on a Room or Area, the experience mods will be apply to those inside. If no mask or xp type is given, the experience is always granted. For valid masks, see AHELP ZAPPERMASK, where allowed is modified, and disallowed is not modified. Valid xp types include things like: "ABILITY:\[ability id\]", "COMBAT:\*", "SCRIPT:\[quest name\]", "CLASS:\[class name\]", "ACHIEVE:\*", "COMMAND:\[command id\]", "LIEGE:\[name\]", "MISC:\*", etc. The type can be matched using \* or ? characters. Multiple independent XP changes may be specified by grouping all arguments in \[\], and separating multiple groupings with commas.

Notes:

- The text is only re-parsed while it gives nothing.

### Prop_ModFaction

**Hosts:** mobs, items, areas, rooms

**Parameters:**

```text
(+/-[FACTION ID]:)(=,+,-,*,/)[NUMBER](%)(;[MASK])
```

**Examples:**

```text
alignment.ini: 500
alignment.ini: 500;-race +orc
=10%
examples/reputation.ini: +20%
+20%;-class +Fighter +Mage -Race +Elf
+alignment.ini:*2
```

Allows the archon to modify the amount of faction gained or lost by each player for slaying the mob. If placed on a mob, the faction will be granted according to the parm, so long as the player meets the mask parameters (optional) and so long as the faction being changed is the one specified in the parameters (also optional). The faction id is left empty to specify affecting all factions. The faction id can be preceeded by a + to only affect faction gains, - to only affect faction losses, or neither to affect both. You can also use REACTION as a faction ID to only apply to factions that have reactions defined. If this property placed on an item, the faction will be altered for the wearer (or wielder or holder), so long as the mob killed meets the mask (optional). If placed on a Room or Area, the faction will be changed for one who meets the (optional) mask requirements. If no faction id is given, all factions are changed. If no mask is given, the faction is always granted. For valid masks, see AHELP ZAPPERMASK, where allowed is modified, and disallowed is not.

Notes:

- Percent values are applied once (they were squared).

### Prop_Monolingual

**Hosts:** mobs.

**Parameters:**

```text
LANGUAGES=Orcish,Goblinese CONFUSED="<S-NAME> grunt(s) and shrug(s)."
```

**Example:**

```text
LANGUAGES=Orcish CONFUSED="<S-NAME> stare(s) blankly."
```

CoffeeSUD addition. Removes the automatic comprehension of Common. The listed languages are understood fully; learned languages and translators still apply. Speech in a language the mob does not understand reaches `NOTUNDERSTOOD_PROG` instead of `SPEECH_PROG` or MudChat, and the optional `CONFUSED` line is shown. If no foreign language is selected, replies use the first declared language. [Prop_LanguageSpeaker](#prop_languagespeaker) also accepts `UNDERSTANDS=ONLY` for its `LANGUAGE`.

### Prop_MoveRestrictor

**Hosts:** mobs, items (shipped help: MOBs, Rideables)

**Parameters:**

```text
(CHANCE=[NUMBER 0-100])
(NOLOCALES="[LOCALE],...") (ONLYLOCALES="[LOCALE],...")
(NODOMAINS="[DOMAIN],...") (ONLYDOMAINS="[DOMAIN],...")
(SEARCH="[STR]") (ISEARCH="[STR]") (MSEARCH="[STR]")
(MESSAGE="<S-NAME> [PREDICATE]") (PUBLIC=TRUE/FALSE)
(DISMOUNT=[TRUE/FALSE]) (CAST="[ABILITY ID];...")
```

**Example:**

```text
nolocales="WOODS,SWAMP" message="<S-NAME> will not enter!" public=true
```

According to the given percentage CHANCE (default 100%), this property restricts the ability of a mob or rideable to enter rooms of a particular spec. NODOMAINS can list the types of rooms that cannot be entered, whereas ONLYDOMAINS lists the types of rooms that can be entered, restricting all others. Both are comma-delmited lists. Indoor DOMAINS include: STONE, WOODEN, CAVE, MAGIC, and METAL. Outdoor DOMAINS include: CITY, WOODS, ROCKY, PLAINS, UNDERWATER, AIR, WATERSURFACE, JUNGLE, SWAMP, DESERT, HILLS, MOUNTAINS, SPACEPORT, SEAPORT.

NOLOCALES can list the types of rooms that cannot be entered, whereas ONLYLOCALES lists the types of rooms that can be entered, restricting all others. Both are comma-delmited lists. See LIST LOCALES for a list of valid room locale ids.

The SEARCH string allows room restricting based on a key phrase in the potential room title or description. MSEARCH restricts based on mobs in the potential room, and ISEARCH based on items in the potential room.

The MESSAGE parameter allows you to set the string that the person trying to move into the room sees when it is restricted. Use \<S-NAME\> as a substitute for the persons name. The PUBLIC parameter decides whether everyone sees the message (true), or only the person trying to move (false).

The DISMOUNT will automatically dismount any riders on the affected.

The CAST parameter allows you to cause a spell to be cast upon all the properties mover, or upon all the riders, when they try to enter a room that is restricted.

Notes:

- `ISEARCH`, the null check, and `CHANCE` (off-by-one) were fixed.

### Prop_MultiEffects

**Hosts:** mobs, rooms, exits, items

**Parameters:**

```text
[AbilityID] [AbilityID([args]) ...
```

**Example:**

```text
Prop_WearAdjuster(INT+2),Prop_WearAdjuster(STR-2 MASK=-RACE +ELF)
```

Allows an object to have the same effect multiple times by embedding each version of the effect into this property. The list of effects is space (or comma) delimited, and can optionally have arguments in parenthesis. Make sure parenthesis in parameters match!

Note: the parameters are write-only. When looked at later, the parameters are turned into an XML doc to protect the integrity of other properties that also transform their data.

Notes:

- `Prop_A,Prop_B` lists and a trailing entry without parameters are read; only the children that bubble apply to the carrier.

### Prop_NPCLines

**Hosts:** mobs, areas

**Parameters:**

```text
[KEY]="[TEXT]"(|"[TEXT]")([MASK]) ... or "[STOCK LINE]"="[TEXT]" ...
```

**Example:**

```text
NOTBUYING="Bah! I've no use for that junk."|"Take that rubbish elsewhere." STOLEN="I know where that came from, $n."
```

CoffeeSUD: the NPC's own words for the stock lines a shopkeeper, banker, postman, hireling or the like says. Each entry names a line by a short key or by the line itself in quotes; its text is what is said instead, one of several split by \| at random, with @x1, @x2... the line's values (a price, an item) and $n the one spoken to. A \[MASK\] after the text makes it that customer's alone, and such entries come first; \[AFFINITY \>=25\] or \[AFFINITY FRIENDLY\] tests the NPC's opinion of them (AHELP PROP_AFFINITY). An empty text says nothing. On an area it gives the whole town's words; an NPC's own come first. The keys: NOTBUYING (I'm not interested.), NOTBUYINGTHOSE, STOLEN, OUTOFSTOCK, TOOPOOR (@x1 the item), SHOPPOOR, TOODEAR, OFFER (@x1 the price, @x2 the item), FIRM, TOOADVANCED, TOOLOW, CLANSONLY, BALANCE, NOLOANS, ACCOUNTCLOSED, ONLYCURRENCY, SAFE, DELIVER, FORHIRE, HIRED, PRICE, ONTHEJOB, TIMEUP, GOODBYE, WONTWORK, SKILLS. Any other line the NPC says may be named in quotes.

### Prop_NarrowLedge

**Hosts:** rooms, exits

**Parameters:**

```text
check=[%] name=[NAME OF THE LEDGE] (room=[ROOM ID])
```

**Examples:**

```text
check=16 name="the narrow ledge"
check=25 name="a tightrope" room="myarea#2"
```

Whenever a player enters or exits the room or exit with this property, that player is in great danger. If the player fails to roll beneath their Dexterity on a roll from 1-the "check" parameter, the player will fall to their death or into the specified room ID. The check parameter can be made smaller to give those with less dexterity a better survival chance, or made higher to make it more difficult to survive.

Notes:

- A room or exit flagged this way has its own climbing/flying rules switched off; this is intended.

### Prop_NewDeathMsg

**Hosts:** mobs

**Parameters:**

```text
the new death string
```

Makes the mob with this property emote a different death string than the normal one.

### Prop_NoChannel

**Hosts:** rooms, areas (shipped help: Room, Area, MOB)

**Parameters:**

```text
Semicolon delimited list of channel names and or flags QUIET; SENDOK
```

**Examples:**

```text
SENDOK;GOSSIP;CHAT
GOSSIP;QUIET
```

Without any parameters, this property will cancel all channel message sending from the room, or by the mob/player with the property. You can limit the channels that are blocked by naming them in the parameters, separating them with semicolons. You can also have the property block reception of channel messages using the QUIET flag. Lastly, you can override the no-channel-sending using the SENDOK flag, thereby allowing channel messages to be sent.

Notes:

- Inherited. Channel messaging between players is removed in CoffeeSUD, so it matters mainly for other channel uses; `QUIET` on a mob blocks what it would hear.

### Prop_NoCharm

**Hosts:** rooms, areas, mobs

**Parameters:** none.

Disallows the casting of charm like affects in the area, or against the target mob.

### Prop_NoCraftability

**Hosts:** items

**Parameters:**

```text
([ZAPPERMASK])
```

**Example:**

```text
-RACE +Elf
```

Disallows the item with this property from being learned as a recipe for crafting, even if otherwise qualified. If placed on a GenRecipe, it prevents the recipe from being listed in crafting. An optional ZapperMask can narrow the effect only to those who do not pass the mask.

### Prop_NoDamage

**Hosts:** mobs, items (shipped help: Mobs, Weapons)

**Parameters:** none.

Makes a weapon or mob incapable of doing any real damage.

### Prop_NoOrdering

**Hosts:** rooms, areas, mobs

**Parameters:** none.

Disallows ordering by non-admins in the area, or against the target mob.

### Prop_NoPKill

**Hosts:** rooms, areas

**Parameters:** none.

Cancels all player killing in the room or area.

### Prop_NoPurge

**Hosts:** rooms, items (shipped help: Rooms, Containers, Items)

**Parameters:**

```text
([TIMESTAMP]/[DATE-TIME]_
```

**Examples:**

```text
16885795721221
10/31/2023 08:51am
```

Prevents items in the room from being destroyed as refuse over time. When placed on an item, this property makes the item immune to "garbage collection" regardless of where in the world it is. If no parameter is given, the protection will last until the next reboot, or otherwise until the time given.

### Prop_NoRecall

**Hosts:** rooms, areas, items

**Parameters:** none.

Disallows recalling in the room. If placed on items, the item must be possessed by the mob or player trying to recall for prevention to work.

### Prop_NoSave

**Hosts:** rooms, areas

**Parameters:**

```text
(MSG="[REFUSAL]")
```

**Example:**

```text
MSG="The water's whispering drowns your thoughts."
```

The game cannot be saved here, whatever its save mode (SAVEMODE in coffeemud.ini); the player is told MSG, or that they cannot save here. On an area, it covers every room in it. In an ironman game, QUIT still keeps the game, and a Prop_AutoSave here still saves.

### Prop_NoSummon

**Hosts:** rooms, areas, mobs (shipped help: Room, Area)

**Parameters:**

```text
(ALLOWNONAGGR)
```

Disallows summoning of creatures or people INTO the affected place. The optional ALLOWNONAGGR parm may be added to disallow summoning only of aggressive mobs.

### Prop_NoTeleport

**Hosts:** rooms, areas

**Parameters:**

```text
(EXCEPTIONS=[ROOMID/AREANAME,etc..]) (NOSUMMON=TRUE)
... (INTERAREAOK=true/false) (MESSAGE=[message])
... (REDIRECT=[ROOM ID])
```

**Example:**

```text
exceptions="area name#12,other area name"
```

Disallows the casting of gate, teleport, portal, plantpass, and other spells INTO this area. If this is placed on an area, specific room or area exceptions may be given. By default, mob summoning skills, such as monster summoning, ARE allowed, but can also be disabled with nosummon=true. The optional exceptions argument can list room ids, area names, planarability, or spell ids. The NOSUMMON argument prevents summoning also, and INTERAREAOK allows/prevents teleportation inside an area with this property. MESSAGE can be used to change the fizzle message seen by the casters. REDIRECT is an optional room ID that will allow the teleport to procede, but only to the specific room.

### Prop_NoTeleportOut

**Hosts:** rooms, areas

**Parameters:**

```text
(EXCEPTIONS=[ROOMID/AREANAME,etc..])
```

**Example:**

```text
exceptions="area name#12,other area name"
```

Disallows the casting of gate, teleport, portal, plantpass, and other spells OUT OF this area. The optional exceptions argument can list room ids, area names, planarability, or spell ids. The INTERAREAOK argument allows/prevents teleportation inside an area with this property.

### Prop_NoTelling

**Hosts:** rooms, areas, mobs

**Parameters:** none.

Cancels all tell message sending in or to the room, area, or mob. You can also add this to player mobs as punishment or to shut them up. Admins are exempt from the restrictions imposed.

### Prop_OpenCommand

**Hosts:** items, exits (shipped help: Containers, Exits)

**Parameters:**

```text
[COMMAND WORD] ([PARMS...]/NOOPEN/MESSAGE=[STR];...
```

**Examples:**

```text
twist knob;NOOPEN;MESSAGE="Doorknob!!";tw knob;twi knob
smack botto*
```

When applied to a locked container or exit, this property will force the affected openable to unlock and open when the command phrase is entered by the user in its presence. The command word must match exactly, and the command entered must otherwise be completely invalid. PARMS arguments must also match exactly, although any PARM arg may end with \* to use a starting string wildcard. A PARM argument may also end with \*\* to match the rest of the users entered command string. In addition to a series of command words, you can also use the NOOPEN arg to also disable the standard open command for the exit/item, or use MESSAGE= to change the message sent to the room, which can use \<S-NAME\> for the actor, and \<T-NAME\> for the item.

### Prop_OpenPassword

**Hosts:** items, exits (shipped help: Containers, Exits)

**Parameters:**

```text
[PASSWORD]/[LANGUAGEID];[PASSWORD]{;[MESSAGE])
```

**Examples:**

```text
open sesame
Orcish;open sesame
;open sesame;<T-NAME> opens!
```

When applied to a locked container or exit, this property will force the affected openable to unlock and open when the magic word specified in the parameter is spoken in its presence. A language and a semicolon may be optionally given to require that a particular language is being spoken. If a language (or blank) is specified, then an open message may also be specified.

### Prop_OutfitContainer

**Hosts:** items (shipped help: Containers)

**Parameters:**

```text
;[XML TAGS...]
```

When applied to a container, this property will allow the container to remember which things were put into it by a player. Then, whenever the player attempts to wear/hold/wield the container, if the container has items/armor/weapons inside, the player will actually wear the containing items instead. The automatic wearing of the contained items will automatically remove conflicting items as normal. Later, when the remembered items are removed, they will automatically be put back into the container that remembers them.

### Prop_PartyScaler

**Hosts:** mobs, rooms, areas

**Parameters:**

```text
(BASE=[N]) (HP=+[N]%/MEMBER) (DAMAGE=+[N]%/MEMBER) (ATTACK=+[N]/MEMBER) (LEVEL=AVG+[N]/MAX+[N]) (MIN=[N]) (MAX=[N]) (ADDS="[NAME]/MEMBER-ABOVE-[N]")
```

**Examples:**

```text
BASE=3 HP=+25%/MEMBER DAMAGE=+10%/MEMBER ADDS="catalog:a skeleton guard/MEMBER-ABOVE-3"
LEVEL=AVG+2 MIN=5 MAX=20
```

CoffeeSUD: a fight scaled to the party. When the mob's fight starts it counts the party: its foes' groups in the room, followers too. For each member over BASE (3), or under it, its hit points go up or down HP percent, its damage DAMAGE percent and its attack ATTACK; LEVEL=AVG+N or MAX+N makes its level the party's average or highest plus N, within MIN and MAX; ADDS brings one add (the catalog's, or a class) for each member over N, following it. It holds until the fight ends, when the adds go. On a room or an area, each NPC there that fights the party gets it for that fight. CONSIDER says that the mob scales to your party. It counts builders too, so PLAYTEST shows it as a player would see it.

### Prop_PeaceMaker

**Hosts:** rooms, areas, mobs

**Parameters:**

```text
optional list of mob statements, room emotes, semicolon delimited
```

**Example:**

```text
No Fighting Here!;Make Love, not War!;You should not do that.
```

Cancels any fighting in the room.

### Prop_PlayerTemplate

**Hosts:** mobs

**Parameters:**

```text
PITCH="[TEXT]" LOCK=[NAME,GENDER] NAME=RANDOM
```

**Example:**

```text
PITCH="A tired inspector of the Yard." LOCK=NAME,GENDER
```

CoffeeSUD: on a catalog mob in the category PLAYERTEMPLATE, makes it a ready-made character NEW offers (AHELP PLAYERTEMPLATES). PITCH is its line in the menu; LOCK keeps the template's own name or gender; NAME=RANDOM draws a name. It does nothing in play.

### Prop_Playtest

**Hosts:** mobs (a player character in a playtest).

**Parameters:** the room ID where the playtest began. Set by the engine.

CoffeeSUD addition. Added by the `PLAYTEST ON` and `PARTY` commands, by `PLAYTEST` flows and by an isolated playtest game (`PLAYTEST GAME`). It marks the character as playtesting (`CMSecurity.setPlaytest`), is not saved, and intercepts death: instead of dying, the character is made whole (full hit points, mana and movement, peace) and returned to the restore room with the message "You die. The playtest takes you back to where it began, whole."

Notes:

- `PLAYTEST OFF` removes it. It is deliberately not savable, so a saved game never keeps playtest status.
- See the testing chapter (13) for PLAYTEST ON/OFF/GAME/RUN.

### Prop_Port

**Hosts:** rooms.

**Parameters:**

```text
NAME="[port name]" KIND=SEA|ROAD|AIR|SPACE HIDDEN=false KNOWNMASK="[mask]"
```

**Example:**

```text
NAME="Greyharbour" KIND=SEA
```

CoffeeSUD addition. Marks a room as a port for vessels. `PORTS` lists the known ports; a `HIDDEN` port requires a character who fits `KNOWNMASK`. `COURSE TO`, `SAIL TO` and `DRIVE TO` accept a known port, a room ID, an area, or a numbered Sea Charting point. Routes follow open exits and the vessel terrain rules. See chapter 30.

### Prop_PracticeDummy

**Hosts:** mobs

**Parameters:**

```text
(KILLABLE)
```

**Example:**

```text
KILLABLE
```

Prevents a mob from fighting back. If the KILLABLE parameter is NOT included, the mob will also never run out of hit points or be missed in combat.

### Prop_PressurePlate

**Hosts:** rooms, items

**Parameters:**

```text
(WEIGHT=[TEST]) (COUNT=[TEST]) (MASK="[ZAPPER MASK]") (ON="[ACTIONS]") (OFF="[ACTIONS]") (MSGON="[TEXT]") (MSGOFF="[TEXT]") (DELAY=[TICKS])
```

**Examples:**

```text
WEIGHT=">=50" OFF="cast Spell_Lightning $n" MSGOFF="The pedestal rises with a click!"
COUNT=">=2" ON="door open here north" OFF="door close here north" DELAY=2
```

CoffeeSUD: a pressure plate. On a room it weighs the mobs there (with all they carry) and the items lying there; on a fixture, what is on or in it and who sits on it. WEIGHT and COUNT (of the mobs) are tests: \>=120, \>50, \<=10, \<3, =2, or a number (at least). MASK is what counts. When it goes down ON runs and MSGON shows; when it comes up again OFF runs and MSGOFF shows, DELAY ticks later if given (it stays down if pressed again meanwhile). It looks again when someone enters or leaves, things are got, put or dropped, or someone sits or stands on it. Actions are script commands, as in Prop_Switch. Scripts have ROOMWEIGHT() and CONTENTWEIGHT().

### Prop_PrivateProperty

**Hosts:** items

**Parameters:**

```text
OWNER="[NAME]" (PRICE=[price]) (EXPIRESEC=[SECONDS])
```

**Example:**

```text
owner="bob" expiresec=60
```

Marks the item as belonging to a particular person or clan. Items so marked can only be sold by that person, an associate, or clan member. PRICE is the asking price, as a ship or other boardable item marked so gives it. EXPIRESEC can be set to make this property temporary, so that an item dropped becomes "unowned" once the owner is gone for the number of seconds given. CoffeeSUD: OWNER="@PROFILE" is the whole profile, and every character of the profile counts as the owner of what another of them owns.

### Prop_PropSetter

**Hosts:** items, areas, rooms, mobs, exits (shipped help: All)

**Parameters:**

```text
TRIGGER=[TRIGTYPE] (BUBBLE=TRUE/FALSE) [STAT="VALUE"]
```

**Examples:**

```text
trigger=get bubble=false ammocapacity=10
trigger=always bubble=false capacity="@x1+${WEIGHT}+5"
```

This property changes the settable fields on the affected object, or an items owner if bubble=true. The change occurs on the trigger, and is undone on an opposing event, if the trigger has one. The supported triggers are: ALWAYS, ENTER, BEING_HIT, WEAR_WIELD, GET, USE, PUT ,MOUNT, DROP_PUTIN, HITTING_WITH. The stats you can discover by using LIST STATS \[CLASSNAME\].

"VALUE"s can be anything, including math formulas based on the previous numeric value by including the @x1 variable, or by referencing some other stat using ${OTHERSTAT} syntax inside the value.

Now the warnings: Most fields can be temporarily and conditionally adjusted safely using the various Prop_\*Adjuster properties. This property makes permanent savable changes, similar to MPGSET in a script. It \*might\* also be able to safely undo a change, making it seem temporary, but its best to use this as a last resort, and to test it first.

Notes:

- `TRIGGER=DROP_PUTIN` and `HITTING_WITH` work now (their bit values were corrected), `PUT` no longer fires on DROP and `MOUNT` no longer fires on hitting. A `${...}` variable with two references is read correctly.
- The undo runs when the property is removed as well as at its own undo trigger.

### Prop_Purchases

**Hosts:** mobs (the player character).

**Parameters:** one line per purchase `KIND|ID|TRAINS|PRACTICES|LEVEL|EXTRA`, kept by the engine.

CoffeeSUD internal (`Purchases`). A hidden effect that records what a character bought with trains and practices (skills, expertises, stat gains, class changes) so that a respec can refund exactly what each cost. `KIND` is SKILL, EXPERTISE, STAT or CLASS. It is saved with the character and displays nothing.

Notes:

- Do not edit or attach by hand. See [Prop_Respec](#prop_respec) for the builder-facing respec property.

### Prop_QuestBoard

**Hosts:** items, rooms

**Parameters:**

```text
(COUNT=[N]) (REFRESH="[GAME TIME]") (TEMPLATE=[TEMPLATE]) (VARS="[NAME]=[VALUE] ...") (FILE=[PATH]) (TAGID=[ID]) (LEVELRANGE=[N])
```

**Example:**

```text
COUNT=3 REFRESH="1 mudday" TEMPLATE=normal LEVELRANGE=3
```

CoffeeSUD: a board of jobs, quests generated as the RandomQuests behavior makes them (TEMPLATE, FILE as its PATH, TAGID, and its other parameters, such as SOURCENAME= for the giver, are passed on; VARS sets more of the generator's variables). LOOK at the board (or READ it, if the item is readable; LOOK in the room, on a room) lists the jobs the reader may take, COUNT of them (3), and ACCEPT \[NUMBER\] takes one. At each REFRESH (1 MUDDAY, or MUDDAY:1), on the game clock, the jobs done or given up go and new ones come up to COUNT, or with none taken the oldest is replaced; a job taken stays until it is done. The generator is told the reader's PLAYER_LEVEL, PARTY_SIZE (the players' characters of their group), PLAYER_FACTION_\[ID\] and QUESTS_DONE, and with LEVELRANGE=\[N\] TARGET_MIN_AREALEVEL and TARGET_MAX_AREALEVEL that far around the level. A job waits on the board with no expiry unless EXPIRE= says one. The jobs are kept in a saved game with the board.

### Prop_QuestGiver

**Hosts:** mobs, items, exits, rooms, areas

**Parameters:**

```text
QUEST="[NAME]/GENERATE:([ARGS])" (MESSAGE="[ANNOUNCEMENT]")
(DETAILS="TRUE/FALSE") (OPTIONAL="TRUE/FALSE") (REOFFER="ALWAYS/ONCE/[GAME TIME]")
(TRIGGER="GET/ENTER/LEAVE/PUTIN/DEATH/SPEAK/WEAR/WIELD/READ")
```

**Example:**

```text
quest="save_the_day" message="You found a quest item" trigger=GET
```

Makes the item, room, whatever capable of offering a quest to the player who triggers it. The trigger has a default for each object type, but you can set it explicitly with the TRIGGER argument. The quest can be made non-optional with the OPTIONAL argument. The MESSAGE is shown before the quest is offered. The quest details will be as well, unless the DETAILS argument is set to false. The quest itself can be specified by name, or you can have one generated by the RandomQuests behavior by putting GENERATE: followed by optional quest parameters. The quest must have an accept script (FUNCTION_PROG CAN_ACCEPT and DO_ACCEPT on something it loads), and must be running. On an area, with the ENTER, LEAVE, LOOK, SPEAK, READ, DEATH, GET, WEAR, WIELD or PUTIN triggers, each player is offered the quest once in each run of the quest; this is kept in the quest variable OFFERED:PLAYERNAME (QVAR), which a saved game keeps. CoffeeSUD: REOFFER says how often to offer it, on any target: ALWAYS whenever the trigger comes, ONCE in a run of the quest, or a game time (such as 1 MUDDAY) after the last offer; without it, an area offers once and the rest whenever their trigger comes. The details show the quest's SET REWARDS as Reward:. A quest offered, or read on a quest board, is listed by QUESTS OFFERED while the player may still take it, and QUESTS ACCEPT takes it on.

### Prop_QuestItem

**Hosts:** items

**Parameters:**

```text
QUEST=[NAME] (NODROP) (NOSELL) (NOGIVE) (KEEPONDEATH) (REMOVEON=[WON,FAILED,ABANDONED,EXPIRED,ENDED/ALL/NONE])
```

**Example:**

```text
QUEST=bell NODROP NOSELL REMOVEON=WON
```

CoffeeSUD: binds the item to a quest. NODROP, NOSELL and NOGIVE refuse those, with a message; KEEPONDEATH keeps it with the character when they die. It goes when the quest leaves ACTIVE for its holder as REMOVEON says: WON, FAILED, ABANDONED (dropped), EXPIRED, or ENDED (the quest stopped); ALL, the default, is any of them, NONE none. QUESTS \[QUEST\] lists what the character carries for it.

### Prop_QuestLog

**Hosts:** mobs (player characters).

**Parameters:** none by hand. The text holds the log.

CoffeeSUD addition. A character's quest journal: the objectives and entries given by `MPQOBJECTIVE`, `MPQJOURNAL` and a quest script's `SET OBJECTIVE` and `SET JOURNAL`, kept in this hidden effect so that they are saved with the character. `QUESTS` shows it. Not for builders to give or change; see the quests chapter (08).

### Prop_QuestMarker

**Hosts:** mobs, items

**Parameters:**

```text
(QUEST=[NAME/*]) (AVAILABLE="[MARK]") (ACTIVE="[MARK]") (READY="[MARK]") (READYFUNC=[FUNCTION])
```

**Examples:**

```text
QUEST=bell READYFUNC=CAN_COMPLETE
QUEST=* AVAILABLE="^Y(!)^?" ACTIVE="^w(?)^?" READY="^Y(?)^?"
```

CoffeeSUD: a mark after the mob or item in room listings, by how the quest stands for the one looking: AVAILABLE (^Y(!)^?) while they may accept it (its CAN_ACCEPT says so, as for Prop_QuestGiver), ACTIVE (^w(?)^?) once they have, READY (^Y(?)^?) when READYFUNC, a FUNCTION_PROG of this mob's or item's run with them as $n, returns something (not CANCEL). QUEST=\* (the default) is every running quest it has a script from, is first quest mob or item of, or gives by Prop_QuestGiver. Only the player's characters see marks, each their own. QUESTMARKERS=AUTO in coffeemud.ini or world.ini marks every running quest's first quest mob, and every Prop_QuestGiver, without this property.

### Prop_ReRollStats

**Hosts:** mobs

**Parameters:**

```text
(BONUSPOINTS=[NUM]) (PICKCLASS=TRUE/FALSE)
```

**Example:**

```text
BONUSPOINTS=5 PICKCLASS=TRUE
```

Allows a player a one-time opportunity to redesignate their stats, completely from scratch, and potentially re-pick their class.

### Prop_Recruitable

**Hosts:** mobs

**Parameters:**

```text
MASK="[ZAPPER MASK]" COST=[GOLD] MESSAGE="[TEXT]" NAME=[NAME]
```

**Example:**

```text
MASK="-FACTION +GOOD" COST=50 MESSAGE="<S-NAME> shoulder(s) her bow and fall(s) in with <T-NAMESELF>."
```

CoffeeSUD: a player may RECRUIT this NPC: if MASK lets them in and they pay COST, it becomes a character of their profile in their party (as MPRECRUIT does), named NAME or by the first word of its name. MESSAGE is shown as it joins (S the NPC, T the player).

### Prop_RejuvWhen

**Hosts:** mobs, items

**Parameters:**

```text
WHEN="[ZAPPER MASK]" (ELSE=WAIT/NEVER/REPLACE:[NAME])
```

**Examples:**

```text
WHEN="-IF !VAR(* OLDMILL == CLEARED)" ELSE="REPLACE:a settler"
WHEN="-HOUR +20 +21 +22 +23 +0 +1 +2 +3 +4" ELSE=WAIT
```

CoffeeSUD: respawns that follow the story. When the mob's or item's rejuv would bring it back, WHEN is checked first: on the mob, as if back in its start room (its hour, season, room text, and -IF/+IF script conditions), or, for an item, on someone in its room. If it fails, ELSE says what happens: WAIT (the default) tries again at the next rejuv, NEVER leaves it gone, and REPLACE:name puts the catalog's mob or item (or a class) there instead, for good.

### Prop_ReqAlignments

**Hosts:** rooms, areas, exits

**Parameters:**

```text
alignment masks listed below
```

**Example:**

```text
-all +good
```

(DEPRECATED -- See PropReqEntry)

Creates alignment restrictions for entering a room. Followers will be allowed unless explicitly disallowed. Valid parameters include:

nosneak (disallow sneaking past the requirement)

-all (disallow all alignments)

nofol (disallow followers of appropriate alignment)

+evil +good +neutral (create exceptions to -all)

-evil -good -neutral (disallow only listed alignments)

Notes:

- Deprecated in the shipped help; use [Prop_ReqEntry](#prop_reqentry) with an `-ALIGNMENT` mask instead.

### Prop_ReqCapacity

**Hosts:** rooms, areas, exits (shipped help: Areas, Room)

**Parameters:**

```text
PEOPLE=[MAX] PLAYERS=[MAX] MOBS=[MAX] ITEMS=[MAX] WEIGHT=[MAX] INDOOR=[T/F]
CONTAINERSOK=[T/F] ROOMS=[MAX] SIEGEWEAPONS=[MAX]
```

**Examples:**

```text
people=2
players=3 mobs=2 indoor=true
weight=2000 items=10
```

Creates a capacity limit of one or more types for the room or area with this affect. The ITEMS, or WEIGHT parameters may be used to limit the number of items, or maximum weight respectively. The CONTAINERSOK parameter will create an exception for putting items in containers (but not for dropping the containers themselves). The PEOPLE parameter lets you limit the total number of players and/or mobs. Otherwise, the PLAYERS or MOBS can be used to limit the number of players or non-player mobs respectively. The optional INDOOR flag only matters if this property is added to an area; it makes the property only enforce itself on indoor rooms types.

If no parameters at all are given, a PEOPLE capacity of 2 is assumed.

When this property is on an entry room with Prop_LotsForSale or Prop_LotForSale, then this property will be automatically propogated to each new room created. Also, in this case, and the optional parameter ROOMS can be specified to limit the maximum number of contiguous new rooms that can be generated.

### Prop_ReqClasses

**Hosts:** rooms, areas, exits

**Parameters:**

```text
class masks listed below
```

**Example:**

```text
-all +thief
```

(DEPRECATED -- See PropReqEntry)

Creates class restrictions for entering a room. Followers will be allowed unless explicitly disallowed. Valid parameters include:

nosneak (disallow sneaking past the requirement)

-all (disallow all classes)

nofol (disallow followers of appropriate class)

+thief +mage +bard (create exceptions to -all)

-thief -mage -bard (disallow only listed classes)

Notes:

- Deprecated in the shipped help; use [Prop_ReqEntry](#prop_reqentry) with `-CLASS`/`+CLASS` masks instead.

### Prop_ReqEntry

**Hosts:** rooms, areas, exits (shipped help: Rooms, Areas, Exits, Portals)

**Parameters:**

```text
(nosneak) (nofollow) (actual) ("message=my message")
("event=[block/accuse],(crime)") mask
```

**Examples:**

```text
-race +elf
nosneak -race +elf
"event=accuse,trespassing" "message=^N" -race +elf
"message=Elves Only in there!" nofollow -race +elf
```

Creates restrictions for entering a room. Followers will be allowed unless explicitly disallowed. Valid parameters include:

nosneak (disallow sneaking past the requirement)

nofol (disallow followers)

actual (discount disguises)

"message= ... " (override the default no-entry message.

"event= ..." (comma-delim event: default='block', or 'accuse,\[crime\],..')

See AHELP ZAPPERMASK, where allowed can enter, and disallowed is not.

Notes:

- CoffeeSUD additions: the `event=` and `message=` options (blocking versus accusing a crime), and `actual`. The help calls the follower option `nofol`, but the code tests for words starting with `NOFOLLOW`, so write `nofollow` (a bare `nofol` is ignored). Option words are matched case-insensitively; the mask is whatever follows the first term that begins with `+` or `-`.
- Followers of an allowed character are let in unless `nofollow` is given.
- It carries `FLAG_ZAPPER`, so tools that list zapper-using properties include it. Mask syntax: chapter 24 (zapper masks).

### Prop_ReqHeight

**Hosts:** rooms, areas, exits

**Parameters:**

```text
a maximum height to enter
```

**Example:**

```text
12
```

Creates a maximum height in order to enter a room. Similar to Prop_Crawlspace.

### Prop_ReqLevels

**Hosts:** rooms, areas, exits

**Parameters:**

```text
==, <=, >=, >, or < followed by a level number ([PARMS])
```

**Example:**

```text
<=10 >=5
```

Creates level restrictions for entering a room. Followers will be allowed unless the NOFOL parameter appears. The parameter should describe only the allowed levels. Multiple criteria are allowed. The term "NOSNEAK" may be added to the parameter to disallow sneaking past your criteria. The parm MESSAGE="message" may be added to change the error message seen. The term "SYSOP" will require admin privileges. The "ALL" flag, for some reason, just lets everyone in.

Notes:

- Terms are no longer dropped from the list and a mount is checked as well (property audit fixes).

### Prop_ReqNoMOB

**Hosts:** rooms, areas, exits

**Parameters:**

```text
optional nosneak parameter
```

**Example:**

```text
nosneak
```

Prevents non-player mobs without followers from entering the room. The term "NOSNEAK" may be added to the parameters to disallow sneaking past your criteria.

### Prop_ReqPKill

**Hosts:** rooms, areas, exits

**Parameters:** none.

Allows access only to those players who have their playerkill flag set.

### Prop_ReqRaces

**Hosts:** rooms, areas, exits

**Parameters:**

```text
room masks listed below
```

**Example:**

```text
-all +elf
```

(DEPRECATED -- See PropReqEntry)

Creates race restrictions for entering a room. Followers will be allowed unless explicitly disallowed. Valid parameters include:

nosneak (disallow sneaking past the requirement)

-all (disallow all races)

nofol (disallow followers of appropriate alignment)

+elf +half +human (create exceptions to -all)

-elf -half -human (disallow only listed races)

Notes:

- Deprecated in the shipped help; use [Prop_ReqEntry](#prop_reqentry) with `-RACE`/`+RACE` masks instead.

### Prop_ReqStat

**Hosts:** rooms, areas, exits, items (shipped help: Rooms, Areas, Exits, Portals)

**Parameters:**

```text
mob character stat expressions as shown in example
```

**Example:**

```text
strength<10 intelligence==5 wisdom>=3
```

Creates restrictions for entering a room. Players must pass every expression test in order to enter. Valid parameters include:

nosneak (disallow sneaking past the requirement)

nofol (disallow followers)

STRENGTH, INTELLIGENCE, WISDOM, CONSTITUTION, CHARISMA, DEXTERITY, etc..

### Prop_ReqTattoo

**Hosts:** rooms, areas, exits, items

**Parameters:**

```text
optional msg; tattoo masks listed below
```

**Examples:**

```text
-all +manlymen
You can't go here!;-all +manlymen
```

Creates tattoo restrictions for entering a roomor possessing an item. Followers will be allowed unless explicitly disallowed. See the help on Tattoos. Understood masks include:

nosneak (disallow sneaking past the requirement)

nofol (disallow followers of appropriate alignment)

-all (disallow unless has one of the following +tattoos)

+all (allow unless has any of the followng -tattoos)

+none (must have all +tatoos, and lack all -tattoos)

+TATTOONAME (must have tattoo)

-TATTOONAME (must not have tattoo)

Prefix TATOONAME name with ACCOUNT(space) to use account-based tattoos.

Notes:

- `+ALL` now denies when a listed tattoo is missing, and `NOSNEAK`/`NOFOL` are read as the first word (fixed in the property audit).

### Prop_Resistance

**Hosts:** mobs

**Parameters:**

```text
resistance names followed by percentages
Parameters may be followed by an optional mask
```

**Examples:**

```text
magic 50% poison -10%
magic poison 50% pierce -10% MASK=-RACE +Dwarf
```

Gives a mob natural resistances and immunities. The effects which may be included are:

Resistances: gas fire electricity mind magic cold acid water evil (undead)

Resistances: justice (as per many thief abilities)

Resistances: weapons blunt pierce slash non-magical-weapons non-silver-weapons

Resistances: -- weapons get reduced % damage per round, lvl based, with limits.

: : -- always modifier makes weapon resists always work, no limits.

Resistances: spell chant song prayer thief_skill (ability types)

Resistances: enchantment/charm kicking vexing conjuration (ability domains)

Immunities : teleport (includes gate, summon) holy disease poison

Misc : debuf-duration (pct is the maximum debuf ticks remain)

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_Respec

**Hosts:** mobs, items, rooms

**Parameters:**

```text
WHAT=[STATS,SKILLS,EXPERTISES,CLASS,ALL] COST="[N]( per skill)" ONCE=YES MASK="[ZAPPER MASK]"
```

**Example:**

```text
WHAT=SKILLS COST="500 per skill" MASK="+TATTOOS +TUTORIAL_DONE"
```

CoffeeSUD: where this is (the room, someone or something in it, or something carried), a character may RESPEC: undo what they bought with trains and practices, of the kinds WHAT names (ALL by default), and have back what each cost. Each character's purchases are kept as they are made: skills taught, gained and practised, expertises, stats, hit points, mana and moves trained, and a class trained into (undone while it has no levels). Skills that come by themselves with a class stay. COST is money for the whole respec, or with "per skill" for each thing undone; ONCE=YES lets each character do it here once; MASK names who may. MPRESPEC does it from a script.

### Prop_RestrictSkills

**Hosts:** rooms, areas, mobs, items

**Parameters:**

```text
SKILLS="[ABILITYID,...]" (MESSAGE="[STRING]") (WEARONLY=[T/F])
(ONLYROOMS="[DOMAIN,...]") (NEVERROOMS="[DOMAIN,...]")
```

**Example:**

```text
SKILLS="Spell_Sleep,Skill_Recall" MESSAGE="No!" ONLYROOMS="UNDERWATER,AIR"
```

Cancels the effects of the listed spells/skills, and can be restricted by location. The only required parameter is SKILLS, which is a comma-delimited list of Ability IDs. Optional parameters includes MESSAGE, to change the skill users error message, ONLYROOMS to restrict the skill everywhere except the comma delimited list of DOMAIN strings, and NEVERROOMS to restrict the skill only in the comma delimited list of DOMAIN strings. DOMAINS include: CITY, WOODS, ROCKY, PLAINS, UNDERWATER, AIR, WATERSURFACE, JUNGLE, SWAMP, DESERT, HILLS, MOUNTAINS, SPACEPORT, SEAPORT, STONE, WOODEN, CAVE, MAGIC, UNDERWATER, AIR, WATERSURFACE, METAL. When this property is on a mob, it only affects the mob. When on an item, it only affects the owner of the item. If you want to limit spells/skills used AGAINST a mob, look at immunities.

### Prop_RestrictSpells

**Hosts:** rooms, areas, mobs (shipped help: Room, Area)

**Parameters:**

```text
[SPELLID];[SPELLID];...
```

**Example:**

```text
Spell_Sleep;Chant_Uplift;
```

Cancels the effects of the listed spells when cast on or in the room.

Spell IDs can optionally include an argument in parenthesis () to restrict the spell only when cast against a target of the given name. Spell ids with parenthesis arguments may be listed multiple times. When a spell targets a room or area, the name of a Plane of Existence may also be a valid argument.

Notes:

- Spell ids are matched case-insensitively.

### Prop_Retainable

**Hosts:** mobs

**Parameters:**

```text
The value of the mob to shopkeepers
Price of the mob per period;number of days per period
(PERSIST=true)
```

**Examples:**

```text
100000
100 persist=true;10
```

This property serves many a purpose: to allow you to set a value to mobs when being sold as pets by shopkeepers. It also allows you to create mobs which will be saved in the database when the system is shut down, so long as the mob has this property, and the mob is presently on someone's personal home, such as a Prop_RoomForSale room. The second parameter following the semicolon, if present, will also make the mob automatically draw money from the property owners bank account periodically. The second parameter refers to the number of mud-days between each pay period. If a pay period passes and the mob can not find a bank account of the owners from which to get paid, or has not been placed on someone's personal property as described above, the mob will quit and disappear.

If persist is set to true, then the mob will stop following the player, but remain in the game when the player logs out.

### Prop_RideAdjuster

**Hosts:** items, mobs

**Parameters:**

```text
see Prop_HaveAdjuster
```

**Example:**

```text
see Prop_HaveAdjuster
```

Whenever the item or mob having this property is mounted or ridden, the statistics of the rider will be adjusted as described in the parameters. Dismounting will remove the effects. See Prop_HaveAdjuster for more information on the valid parameters for this property.

### Prop_RideEnabler

**Hosts:** items, mobs

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the item with this property is being mounted or ridden, the owner will be granted the spells, skills listed. The percentage given is optional, and will be the proficiency of the skill. If a percentage is not given, the default is 100%.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_RideResister

**Hosts:** items, mobs

**Parameters:**

```text
resistance names followed by percentages
Parameters may be followed by an optional mask
```

**Examples:**

```text
magic 50% poison -10%
magic poison 50% pierce -10% MASK=-RACE +Dwarf
```

Whenever the item having this property is mounted or ridden, the rider gains certain resistances and immunities described in the parameters. When exited, the resistances and immunities are also cancelled. The effects which may be included are:

Resistances: gas fire electricity mind magic cold acid water evil (undead)

Resistances: justice (as per many thief abilities)

Resistances: weapons blunt pierce slash non-magical-weapons non-silver-weapons

Resistances: -- weapons get reduced % damage per round, lvl based, with limits.

: : -- always modifier makes weapon resists always work, no limits.

Resistances: spell chant song prayer thief_skill (ability types)

Resistances: enchantment/charm kicking vexing conjuration (ability domains)

Immunities : teleport (includes gate, summon) holy disease poison

Misc : debuf-duration (pct is the maximum debuf ticks remain)

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_RideSpellCast

**Hosts:** items, mobs

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the item with this property is being mounted or ridden, the owner will be under the effect of the listed spells. The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The flag ONCLOSED can be used to invoke effects only when the container is closed.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_RideZapper

**Hosts:** items, mobs

**Parameters:**

```text
masking parameters described below, optional zap message
```

**Examples:**

```text
-class +mage -race +elf -evil
MESSAGE="<S-NAME> falls off <O-NAME>!" -class +mage
-class +mage "event=accuse,trespassing" "message=^N"
```

Whenever the rider of the item or mob with this property mounts the item, they will be zapped unless it is allowed by the listed parameters.

An optional MESSAGE parameter can define what is seen when the zap occurs. Also optionally, the parameters can start with the word ACTUAL to change zapping behavior to look at actual values instead of perceived values (this about a class check applied to a Charlatan.) The optional EVENT parameter is a comma-delimited set of strings, where the first is one of ZAP (default) or ACCUSE followed by a comma and the crime key from a laws.ini type definition. If the parameters include any alignment checks, the item will end up with an appropriate ambiance as well. For valid masking parameters, see AHELP ZAPPERMASK, where allowed can ride, and disallowed is zapped.

### Prop_Road

**Hosts:** rooms, areas.

**Parameters:**

```text
TIME=[multiplier] NOTFOR="[vehicle names or IDs, comma separated]"
```

**Example:**

```text
TIME=0.5 NOTFOR="rowboat"
```

CoffeeSUD addition. Lets caravans and road vehicles use existing terrain without changing the locale, resources or description. `TIME` multiplies the [Prop_TravelTime](#prop_traveltime) step time; `NOTFOR` excludes matching vehicles. See chapter 30.

### Prop_RoomDark

**Hosts:** rooms, areas

**Parameters:**

```text
(HOURS=[NUM],[NUM],...) (ABSOLUTE=TRUE) (LIGHTMASK="[MASK]") (SEEMASK="[MASK]") (SNUFF=[PCT]) (SNUFFMSG="[TEXT]")
```

**Examples:**

```text
HOURS=1,4,0
ABSOLUTE=TRUE LIGHTMASK="-TAGS +DAWNLANTERN" SEEMASK="-RACE +Dwarf" SNUFF=30
```

Makes a room dark, so that you need a light to see it. You can specify the hours of the day in which the room is made dark using the optional HOURS argument. CoffeeSUD: ABSOLUTE=TRUE makes darkness lights cannot beat: the lights of those in the room, and of what lies there, go dark while they are in it, but those that pass LIGHTMASK (a mask on the light, as -TAGS +DAWNLANTERN). SEEMASK is who still sees in it. SNUFF is the chance a lit torch goes out as one comes in (SNUFFMSG, @x1 the light).

Notes:

- The hours are re-read when the text changes.

### Prop_RoomForSale

**Hosts:** rooms

**Parameters:**

```text
(RENTAL/GRID/ALLOWTHEFT) [PRICE]
```

**Examples:**

```text
1000000
Bob/1000000
@PROFILE/0
```

CoffeeSUD: every character of the player's profile owns what another of them owns, and an owner of @PROFILE (as @PROFILE/0) is the whole profile, so a world can ship a home that is already the player's; it is never lost as an owner who does not exist. PROFILEPROPERTY=true in the ini records what the player buys as @PROFILE. MPGRANT gives a deed from a script.

Makes this room available for purchase by a player. The room will be automatically listed in the stock of any ShopKeeper in the SAME AREA who is designated a LandSeller, or any shopkeeper who has the proper StdTitle item in its selling inventory. The system will automatically maintain the integrity of any items left in the room, including restoring them on reboot. After a room is sold, the player need only enter "LOOK ID" to see the rooms ID for the purposes of identifying his or her lot in the future. The title received for purchasing a room may be GIVEn or SELLed to change possession of the property. The word rental followed by a space may precede the price to make the price of the property be charged every month automatically out of a Bankers account. Failure to make rent returns the property to sellable status.The word ALLOWTHEFT disables robbery law enforcement on the property.

When property is sold, it will be listed as the owner with a slash and the price. Entering this sort of parameter directly is the quick way to give players or clans property.

See also Prop_ReqCapacity for the means to set limits on players building abilities on their property.

### Prop_RoomLit

**Hosts:** rooms, areas

**Parameters:**

```text
(HOURS=[NUM],[NUM],...)
```

**Example:**

```text
HOURS=1,4,0
```

Makes a room lit, even at night. You can specify the hours of the day in which the room is made lit using the optional HOURS argument.

Notes:

- The hours are re-read when the text changes.

### Prop_RoomPlusForSale

**Hosts:** rooms

**Parameters:**

```text
([OWNER NAME]/)(RENTAL) (GRID) (ALLOWTHEFT) [PRICE]
```

**Examples:**

```text
1000000
GRID 1000000
The Bob Clan/1000000
The Bob Clan/RENTAL 1000000
```

Makes this room available for purchase by a player. If you have more than one room for sale, they MUST be separated by at least one room which is not for sale, otherwise both groups will be considered one. This property is identical to Prop_RoomForSale or Prop_RoomsForSale, except that the building skills are permitted to add new rooms to it. See Prop_RoomForSale for more information on sold rooms and the flags.

When property is sold, it will be listed as the owner with a slash and the price. Entering this sort of parameter directly is the quick way to give players or clans property.

See also Prop_ReqCapacity for the means to set limits on players building abilities on their property.

### Prop_RoomRedirect

**Hosts:** rooms, areas

**Parameters:**

```text
[ROOM ID]=[ZAPPERMASK](;[ROOM ID]=[ZAPPERMAKS] ... )
```

**Example:**

```text
Midgaard#3001=-name +bob;Midgaard#3005=-name +joe
```

Allows a room or area to redirect players into another room based on a zappermask check of them. Multiple masks and entries may be included, and each entry is separated by semicolons. An entry is a room id followed by an equal sign and a zapper mask. An empty room ID will let the player enter the room or area as normally. A room ID number will check only the current area of the host object. This property allows players to be redirected based on a mask, but won't fool more programmatic mapping systems, such as concierge behavior or tracking skills. Once active, only SYSMSGS can be used to re-enter the based room regardless of settings.

Notes:

- The host used for the check was fixed, and duplicate masks are handled.

### Prop_RoomUnmappable

**Hosts:** rooms, areas

**Parameters:**

```text
(MAPOK) (NOEXPLORE)
```

**Example:**

```text
NOEXPLORE
```

Makes a room unable to be mapped with skills, explored, or seen with regional awareness. Turning off exploration requires the NOEXPLORE parameter. MAPOK parameter disables the unmappable aspect.

### Prop_RoomView

**Hosts:** rooms, items, exits

**Parameters:**

```text
((LONGLOOK) (ATTACK);)[Room ID to show, or direction]
```

**Examples:**

```text
Midgaard#3001
LONGLOOK;south
```

Allows players looking at the room or exit to actually SEE the room description specified in the parameter. Useful for high-up views of the countryside. The LONGLOOK parameter makes it so the room is seen only on Examine/Longlook. ATTACK makes it so a single ranges attack can target someone in the room (but, not visa-versa).

The Room ID "OUT" can be used to allow boardable items, such as castles, ships, etc, to view the room in which their item object is in. "DOWNINOUT" can be used by the same boardables to view the rooms below.

Notes:

- `ATTACK` works; its missing-weapon message no longer fails when nothing is wielded; `DOWNINOUT` fixed.

### Prop_RoomWatch

**Hosts:** rooms, items (shipped help: Rooms)

**Parameters:**

```text
Room IDs of the rooms that may watch here or var defs ; delimited
```

**Examples:**

```text
Midgaard#3001;Midgaard#3002;Midgaard#3012
#3001;prefix=Throw the window you see ;#3012
```

Allows players in one of the listed rooms to see everything going on in the host room as if they were there. They will be unable to interact with the things they see (LOOKing at, or similar), but can at least witness them. The parameters are the list of rooms, though you can also use prefix=message to define a string to show before the remote messages, and can use the shorter #\<num\> syntax if both rooms/objects are in the same area.

### Prop_RoomsForSale

**Hosts:** rooms

**Parameters:**

```text
(RENTAL/GRID/ALLOWTHEFT) [PRICE]
```

**Examples:**

```text
1000000
The Bob Clan/1000000
```

Makes this room part of a group of rooms available for purchase by a player. The rooms will be sold as a group by the shopkeeper. If you have more than one group of rooms for sale, they MUST be separated by at least one room which is not for sale, otherwise both groups will be considered one. See Prop_RoomForSale for more information on sold rooms and the flags.

When property is sold, it will be listed as the owner with a slash and the price. Entering this sort of parameter directly is the quick way to give players or clans property.

See also Prop_ReqCapacity for the means to set limits on players building abilities on their property.

### Prop_SafePet

**Hosts:** mobs

**Parameters:**

```text
MSG=[NEW DISPLAY MESSAGE]
```

**Example:**

```text
MSG="<S-NAME> is forbidden to harm <T-NAMESELF>."
```

Makes the mob with this property completely unattackable. The default message can be changed with parameters.

### Prop_SalvageYield

**Hosts:** items

**Parameters:**

```text
(SKILL=[SKILL ID],...) YIELD="[THING]:[N](@[PCT]%), ..." (XP=[N]) (DESTROY=false)
```

**Example:**

```text
YIELD="brass:4, iron(clockwork):1@25%, catalog:soul gear@5%" XP=20
```

CoffeeSUD: what taking the item apart gives, in place of the usual. SKILL is Scrapping when not given, or a crafting skill, for its DISMANTLE (\* for any). YIELD is a comma list (in quotes when it has spaces): \[RESOURCE\]:\[N\] is N units of a resource, \[RESOURCE\](\[VARIANT\]):\[N\] the resource as a named variant, and catalog:\[ITEM NAME\]:\[N\] a catalog item; @\[PCT\]% after one is the chance of it. XP is given on success, and DESTROY=false leaves the item where it is, with nothing more in it. With it, Scrapping takes just that item, whatever its material, enchanted or not. It works on a catalog entry too. Without one, \[SKILL\] DISMANTLE \[ITEM\] takes apart an item the skill could make, carried or in the room, for DISMANTLEPCT (in the ini: 50 when blank, 0 for none) percent of the materials its recipe uses, times the crafter's proficiency.

### Prop_SavePoint

**Hosts:** rooms, areas

**Parameters:**

```text
(MSG="[MESSAGE]")
```

**Example:**

```text
MSG="You rest by the fire and set down your journey."
```

A place where the game can be saved when its save mode is SAVEPOINTS (SAVEMODE in coffeemud.ini), as an inn or a campfire. MSG is shown when the game is saved here. On an area, it covers every room in it.

### Prop_ScrapExplode

**Hosts:** items

**Parameters:** none.

Makes an item explode when scrapped.

### Prop_Senses

**Hosts:** rooms, items, mobs, exits.

**Parameters:**

```text
SOUND="a|b" TOUCH="..." TASTE="..." CHANCE=100 SETVAR=key=value TATTOO=name ID=stable-id ALIASES=old-id LEGACYKEYS=old-token
```

**Example:**

```text
SOUND="water drips|a stone shifts";TOUCH="nine studs; one loose";TASTE="salt" CHANCE=100
```

CoffeeSUD addition. Gives the host text for the senses other than sight. `LISTEN [target]`, `TOUCH`/`FEEL target` and `TASTE target` work even in darkness (`LISTEN` needs the listener to be able to hear). Quote texts; `|` separates random alternatives, one of which is chosen each time. `CHANCE` (default 100) is the percent chance the text is delivered; on a failed roll the sense returns silently. First-use clues (`SETVAR`, `TATTOO`) are tracked separately for each sense. `SNIFF` is still [Prop_Smell](#prop_smell).

### Prop_ShortEffects

**Hosts:** mobs, items, rooms, areas, exits (shipped help: MOB, Item, Area, Room, etc)

**Parameters:**

```text
([MAX TICKS] (REMOTEOK) (LONGTIMEOK);)([ABILITY MASK])
(... (OWNER) (WEARER) ;)([ABILITY MASK])
```

**Example:**

```text
3 REMOTEOK;+SKILLFLAG -DANCING -SINGING -PLAYING
```

Makes the mob with this property more resiliant against effects by limiting their tick-down time, and preventing effects cast by someone not in the same room. Useful for bosses and elite mobs. The effects can be re-enabled with REMOTEOK or LONGTIMEOK. An ability mask can be given to limit what skills are affected by this property, although traps, poisons, and diseases always get a pass from remote effects. If the protected thing is an Item, OWNER and WEARER can limit the circumstances of protection.

### Prop_ShowWhen

**Hosts:** items, mobs, exits, rooms.

**Parameters:**

```text
MASK="-TATTOO +SPIRITSIGHT" CHECK=VIEWER|ROOMPLAYERS|ANYPLAYER|HOST WHEN="IFVAR GHOSTS=1" HIDEMODE=UNSEEN|HIDDEN ID=stable-id ALIASES=old-id LEGACYKEYS=old-token
```

**Example:**

```text
MASK="-TATTOO +SPIRITSIGHT" HIDEMODE=UNSEEN
```

CoffeeSUD addition. Hides the host until a condition holds. `MASK` is checked against the party selected by `CHECK` (the viewer, players in the room, any player, or the host itself); `WHEN` accepts a `VARIES` tag and argument, or a zapper mask. Until allowed, the host is invisible and cannot be handled or crossed. A successful SEARCH reveals `HIDDEN` hosts to that character; `UNSEEN` hosts require the condition. `SYSOPMSGS` bypasses the hiding for builders. See [Saved identities](#saved-identities) for `ID`.

### Prop_SkyText

**Hosts:** rooms, areas

**Parameters:**

```text
(DAWN="[TEXT]") (DAY=) (DUSK=) (NIGHT=) (INDAWN=) (INDAY=) (INDUSK=) (INNIGHT=) (WEATHER_[STATE]=) (STOP_[STATE]=) (MOON=)
```

**Examples:**

```text
DAWN="The red sun drags itself over the ash dunes." WEATHER_SNOW="Grey ash begins to fall."
DAWN=NONE NIGHT="The glowcaps dim as night falls on the surface far above."
```

CoffeeSUD: an area's or a room's own lines for the sky. DAWN, DAY, DUSK and NIGHT are told under the sky as the time of day changes, INDAWN to INNIGHT indoors; WEATHER_\[STATE\] (CLEAR, CLOUDY, WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT, COLD, FOG) is the weather's description, as it starts and as LOOK and WEATHER tell it, STOP_\[STATE\] its ending line, MOON TIME's moon line. Alternatives go between \|, one picked each time; NONE says nothing. A room's own come before its area's, an area's before its parents'; what none sets comes from the area's climate file (AHELP WEATHERPROFILE), then resources/lists.ini.

### Prop_Smell

**Hosts:** rooms, areas, exits, items (shipped help: MOBS, Items, Rooms)

**Parameters:**

```text
(TICKS=[NUM]) (CHANCE=[NUM]) (EMOTE) (BROADCAST) text(;...)
```

**Examples:**

```text
<T-NAME> smells flowery!
<T-NAME> smells awful!;<T-NAME> smells BAD!
CHANCE=50 <T-NAME> smells awful!;CHANCE=10 <T-NAME> smells BAD!
EMOTE BROADCAST <T-NAME> smells awful!
```

Makes the object with this property respond to a SNIFF command. More than one smell may be given, separated by semicolons. Each smell may have a chance of being selected during a sniff using the CHANCE parameter. ONLY MOBS with this property have other options, including an absolute duration before going away using the TICKS parameter, the opportunity to emote periodically using the EMOTE flag, and the opportunity to emote to adjacent rooms using the BROADCAST flag.

Notes:

- A rewrite of the text counts as a rewrite, not as an edit of the existing smells.

### Prop_Socials

**Hosts:** mobs, items, rooms, exits, areas

**Parameters:**

```text
NAME=[SOCIAL NAME] SRCCODE=[CODE] TGTCODE=[CODE] YOUSEE="[MSG]"
: : (TARGET="[TARGET CODE]") (OTHSEE="[MSG]") (TGTSEE="[MSG]")
: : (NOTARGSEE="[MSG]") (MSPFILE=[FILENAME]) (;[MORE SOCIALS]...)
MASK="[ZAPPER MASK]" ; ...
WORNONLY=TRUE/FALSE ; ...
LOAD="[SOCIALS INI FILE PATH]" ; ...
```

**Examples:**

```text
name=USE srccode=V yousee="You use it." ; name=ROP srccode=H ...
load="specialSocials.txt"
```

Creates and defines socials that apply to players in the affected area, room, or near the affected exit, or possessing the affected item.

The parameters may be a LOAD command for a resource file containing a standard socials definition list (see your resources/socials.txt file). These files have a single social definition per line, where each part is separated by a tab character.

The parameters may also contain a MASK= definition for a zapper mask that will determine whether the given invoker player or mob can use the social. See help on ZAPPERMASK for more information on how to use masks. A WORNONLY variable can be set to TRUE or FALSE to determine whether an item with this property must be worn in order for the social to be usable.

The parameters may also be a semicolon (;) separated list of social definitions using the VARIABLE=VALUE format. The required variables are NAME, SRCCODE, TGTCODE, and YOUSEE. Each variable definition set is separated by semicolons to define as many socials as are needed.

The allowed variables and valid values are as follows:

NAME = the keyword/name of the social -- the command

SRCCODE = source letter: W)ords, M) hands, V)isual, S)ound, O) movement

TGTCODE = target letter: T)hands, S)ounds, W)ords, V)noisy moves, O) visual

TARGET = target: \<T-NAME\> SELF ALL \<I-NAME\> \<V-NAME\> \<E-NAME\> \[ITEM\]

YOUSEE = What the command invoker sees (\<S-NAME\>) sees.

OTHSEE = What others see the invoker do

TGTSEE = What the target (if any) sees

NOTARGSEE = What the invoker sees when the target is not present

MSPFILE = name of msp sound file in your sounds directory

Notes:

- `LOAD` takes the file named as its own value.

### Prop_Sounder

**Hosts:** areas, rooms, items, mobs, exits.

**Parameters:**

```text
[EMOTE TRIGGER] [EMOTE STRING] (MASK=[ZAPPERMASK]);...
```

**Example:**

```text
enter wiggles his bottom.;sleep smiles evilly. MASK=-NAME +Bob;
wear $p looks good on you.;remove Now you don't look so good.;remove_room $n doesn't look so good.
```

A lightweight alternative to the Emoter behaviours: simple emotes driven by events (getting an item, leaving a room) instead of ticks. Each trigger is separated by `;`. A `MASK=` near the end filters the cause of the trigger. Triggers (each `_ROOM` form emotes to the others in the room instead of the actor):

| Trigger | Fires when |
|---|---|
| `GET`, `GET_ROOM` | the item is gotten |
| `DROP`, `DROP_ROOM` | the item is dropped |
| `PUSH`, `PUSH_ROOM` | the item is pushed |
| `PULL`, `PULL_ROOM` | the item is pulled |
| `EAT`, `EAT_ROOM` | the item is eaten |
| `DRINK`, `DRINK_ROOM` | the item is drunk |
| `MOUNT`, `MOUNT_ROOM` | the item is mounted |
| `SIT`, `SIT_ROOM`, `SLEEP`, `SLEEP_ROOM` | the item is sat on or slept on |
| `WEAR`, `WEAR_ROOM`, `HOLD`, `HOLD_ROOM`, `WIELD`, `WIELD_ROOM`, `REMOVE`, `REMOVE_ROOM` | worn, held, wielded, removed |
| `OPEN`, `OPEN_ROOM`, `CLOSE`, `CLOSE_ROOM` | the item or exit is opened or closed |
| `PORTAL_ENTER`, `PORTAL_ENTER_ROOM`, `PORTAL_EXIT`, `PORTAL_EXIT_ROOM` | the room is entered or left |
| `DAMAGE`, `DAMAGE_ROOM` | the object is hurt, or used to hurt |
| `FIGHT`, `FIGHT_ROOM` | the object is attacking, or used to attack |

The emote string is any text; `$p` is the name of the host, `$n` the source of the trigger, `$e` its he/she and `$s` its his/her.

Notes:

- A weapon attack sounds once per round.

Notes:

- See also [Prop_Verbs](#prop_verbs) for word-driven flavour.

### Prop_SparringRoom

**Hosts:** rooms, areas, mobs (shipped help: Areas, Room)

**Parameters:**

```text
(ROOMID)
```

**Example:**

```text
SparringArea#123
```

Creates a room or area where death is of little consequence. Players (not mobs) who die in this area will lose neither equipment, nor experience, but will be brought back to their Start room (not their death room), or to any room designated in the parameters. Players also cannot flee or recall.

### Prop_SpellAdder

**Hosts:** items, rooms, areas, mobs (shipped help: Rooms, Items, MOBs)

**Parameters:**

```text
See Prop_HereSpellCast
See Prop_HereSpellCast
```

**Example:**

```text
50%;Spell_Invisible;Prayer_Sanctuary
```

Casts the spells in the parameters on the next one to enter the same room. as the item, mob, or room with this property.

Notes:

- The spell list is parsed by the same code as [Prop_HereSpellCast](#prop_herespellcast): `;`-separated entries that can be a spell with optional text in parentheses (`Prayer_SummonElemental(/FIRE)`), an entry with a leading `NN% ` chance, or the flags `NOUNINVOKE`, `LEVEL=[N]`, `MAXTICKS=[N]` and `TICKS=[N]`. The code also reads an `ONCLOSED` flag that the help does not describe (see the audit notes).

### Prop_SpellReflecting

**Hosts:** mobs, items

**Parameters:**

```text
(MIN=[LEVEL]) (MAX=[LEVEL]) (CHANCE=[%]) (FADE=0/1) (REMAIN=[NUM])
```

**Examples:**

```text
min=1 max=30 chance=100 fade=0 remain=100
max=10 chance=75 fade=1 remain=50
```

This complex property allows the archon to set up mobs or items which, when worn, are capable of reflecting spells cast at the mob back to the source. The min and max parms describe the levels of spells which may be reflected. The chance parm describes the % chance of a spell being reflected. Remain is the number of spell levels which may be reflected. Every time a spell is reflected, the level of the spell is subtracted from the remain value. When remain reaches below 0, the item or mob will no longer reflect spells. Fade describes whether the property is persistent or destructive. If fade is equal to 1 or more, then the property is destructive, meaning that the mob will never again gain the fading benefit from this property after remain reaches below 0. If the property was on an item, it will be destroyed. If fade is equal to 0, then the property is persistent. The property will still stop working when the remain value reaches below 0, but the item will not be destroyed, and will begin to regain its strength at a rate of 1 point every 5 minutes or so.

Notes:

- A builder's edit of the text refills the charge; a saved and restored reflector is full.

### Prop_StatAdjuster

**Hosts:** mobs

**Parameters:**

```text
(STR=#) (INT=#) (DEX=#) (CHA=#) (CON=#) (WIS=#)
(ADJMAX=TRUE/FALSE)
```

**Examples:**

```text
CON=1
STR=3 INT=5 DEX=2 CON=-3 CHA=-2 WIS=-1
```

This property adjusts a mobs basic stats up or down. Normally, these stats won't go above the mobs max stats. However, if ADJMAX is set to true, the max stat will also be adjusted.

### Prop_StatTrainer

**Hosts:** mobs

**Parameters:**

```text
(STR=#) (INT=#) (DEX=#) (CHA=#) (CON=#) (WIS=#)
(BASEVALUE=#) (NOTEACH)
```

**Examples:**

```text
BASEVALUE=10 CON=10
STR=30 INT=15 DEX=20 CON=0 CHA=0 WIS=0 NOTEACH
```

This property changes a mobs basic 6 stats. With no parameter, it will set all stats to 25 (or the base value), making the mob a suitable trainer. Other parameters may be entered in order to set the stats of the mob to the specified values. Any values not given will default to 25 (or the base value). If you do not want the mob to also be a trainer, you may include the NOTEACH flag. Use BASEVALUE to change the base value from 25 to something else. A BASEVALUE of -1 will prevent any alteration of that stat.

### Prop_StayAboard

**Hosts:** items, mobs

**Parameters:** none.

Put this property on mobs or items that you want to be perpetually mounted on whatever it is initially mounted on/to. This is especially useful for Patrollers in chaotic environments where a gust of wind or a nasty current may throw things out of whack.

### Prop_StockZapper

**Hosts:** items (stock items of a shop).

**Parameters:**

```text
MASK="[zapper mask]" HIDE=TRUE|FALSE MSG="[refusal]"
```

**Example:**

```text
MASK="+CLAN Thieves" HIDE=TRUE MSG="That is for guild members only."
```

CoffeeSUD addition. A shop stock item's customer access rule. A customer who fails the mask is refused `BUY` with `MSG`; with `HIDE=TRUE` the stock is also hidden from them, with `HIDE=FALSE` it is listed marked as restricted. Membership and rank masks are evaluated for the buyer. See `AHELP ORGANIZATIONS` and docs/organizations.md.

### Prop_StoryRecords

**Hosts:** mobs (the player character).

**Parameters:** a JSON object kept by the engine.

CoffeeSUD internal (`StoryRecords`). A hidden effect that keeps a character's story-state records (counters, once-only tokens and the like that quests, scenes and tutorials need) as saved JSON text. It is created on first write and saved with the character. It has no behaviour of its own.

Notes:

- Not for builders to attach or edit; use quest or script variables instead.

### Prop_Summoned

**Hosts:** mobs

**Parameters:**

```text
ID=[ABILITY ID] TICKS=[N] [FLAGS]
```

**Example:**

```text
ID=CallThePack TICKS=20 FOLLOW ASSIST DISMISSONEXPIRE
```

CoffeeSUD: put on a mob a generic ability's SUMMON brought (AHELP GENABILITY); it is not given by hand. With BIND the mob departs when its summoner dies or leaves the game; TICKS counts down to its departure and is kept in a save; NOSAVE keeps it out of saves. Its summoner is the caster, or after a restore the mob it follows.

### Prop_Surprised

**Hosts:** mobs

**Parameters:**

```text
(TICKS=[N])
```

**Example:**

```text
TICKS=1
```

CoffeeSUD: caught by surprise (Prop_Ambush puts it on): for TICKS rounds the mob can neither move, flee, strike, nor cast; it can look about.

### Prop_Switch

**Hosts:** items, exits

**Parameters:**

```text
(VERBS="[VERB],[VERB]") (STATES="[STATE],[STATE]"/[N]-[N]) (START=[STATE]) (DESC_[STATE]="[TEXT]") (MSG_[STATE]="[TEXT]") (ACTION_[STATE]="[ACTIONS]") (RESET=[TICKS]) (REQUIRES="[ZAPPER MASK]") (REQMSG="[TEXT]") (ONCE=true)
```

**Examples:**

```text
STATES="down,up" DESC_up="The lever is up." ACTION_up="door open Gate#3 east" ACTION_down="door close Gate#3 east" RESET=20
VERBS=turn STATES=1-8 ACTION_5="signal WORLD vault dial5"
```

CoffeeSUD: a lever, button, crank or dial that keeps its state. One of VERBS (pull, push, turn, press, flip), with the thing's name, moves it to its next state, before the game's own command of that name; "TURN DIAL TO 3" to that state; ACTIVATE and DEACTIVATE to the second state and the first. STATES are words or a range of numbers (a dial); START the first. DESC_\[STATE\] is added to LOOK; MSG_\[STATE\] is what the room sees (\<S-NAME\> who, \<T-NAME\> it); ACTION_\[STATE\] runs script commands, a semicolon between them and MP left off if you like (door open Gate#3 east; signal WORLD gate1 up; unhide 'a narrow stair'), for the thing, $n who moved it. RESET goes back to START after that many ticks, running its action. REQUIRES (or MASK) is who may move it, REQMSG what the others are told; ONCE moves it once. SWITCH_PROG \[STATE\] runs on its scripts and its room's, and SWITCHSTATE() reads it. The state is kept in the property's text (STATE=), so saves and world files keep it.

### Prop_Tattoo

**Hosts:** mobs

**Parameters:**

```text
tattoo names delimited by semicolons
```

**Example:**

```text
;UNDEADKILLER;MANLYMEN;
```

This property WAS used to give MOBs tattoos. It is deprecated now. They are now stored on MOB objects. See Help TATTOO.

Prefix the tattoo name with ACCOUNT(space) to use account-based tattoos.

Notes:

- Deprecated in the shipped help: tattoos live on the MOB (`TATTOO` in the mob editor, `MPTATTOO`). Use [Prop_TattooAdder](#prop_tattooadder) to give them on an action.

### Prop_TattooAdder

**Hosts:** items, rooms, areas, mobs, exits (shipped help: Areas, Weapons, Food, Drink, Armor)

**Parameters:**

```text
tattoo name to grant or remove
```

**Examples:**

```text
MANLYMEN
-MANLYMEN
+-MANLYMEN
+~MANLYMEN
+-~MANLYMEN
+34 MANLYMEN
```

This property is used to give MOBs tattoos based on an action taken. The tattoo is given by the object with this property, based on an action which depends on the type of object with this property. Tattoos are arbitrary strings, without spaces, that can be checked for as flags by scripts, masks, and other properties. There are only two types of 'special' tattoos: those starting with RACE_ and those starting with CHARCLASS_, which are used to grant access to otherwise inaccessible races or classes for char creation (e.g. RACE_FIREMEPHIT), assuming all other rules allow.

The tattoo parameter is prefixed with - to remove it when the trigger occurs.

The tattoo parameter is prefixed with + (or nothing) to add it when the trigger occurs.

The tattoo parameter is prefixed with +- to add or remove it when the trigger occurs.

The tattoo parameter (after any +- marks) is prefixed with ~ to disable emoting.

The tattoo parameter is prefixed (after +- or ~) with a number to make it decay.

The tattoo name is prefixed with ACCOUNT(space) to use account-based tattoos.

The actions which trigger the tattoo include:

MOB - death

Item - get

Exit - enter

Room - enter

Area - enter

Weapon - use in battle

Food - eat

Drink - drink

Armor - wear

### Prop_Telegraph

**Hosts:** mobs

**Parameters:**

```text
(NAME=[NAME]) (TICKS=[N]) (WARNING="[TEXT]") (INTERRUPT=STUN,DAMAGE>[N]%,MOVE) (AVOID=LEAVE,HIDE,COMMAND:[WORD]) (INTERRUPTMSG="[TEXT]") (ABILITY=[ID] TARGET=[NAME])
```

**Example:**

```text
TICKS=2 WARNING="<S-NAME> rears back!" INTERRUPT=STUN,DAMAGE>20% AVOID=COMMAND:brace
```

CoffeeSUD: a mob winding up an attack. Scripts put it on with MPTELEGRAPH (AHELP BEHAVIOR_SCRIPTABLE), and a generic ability with a WINDUP does when a monster uses it. WARNING shows at the start and each tick, and LOOK at the mob tells that it is gathering power. A stun, sleep, hold or binding always interrupts it; INTERRUPT adds DAMAGE\>N% (that share of its hit points taken meanwhile) and MOVE (it leaves the room); INTERRUPTMSG is what the room sees. AVOID: those who leave the room are never hit; HIDE spares those hidden, and COMMAND:word those who type the word (brace, duck...). A generic ability's WINDUP field (ticks; MODIFY ABILITY, or WINDUP= in its XML) and WINDUPMSG (the warning, \<T-NAME\> its target) make a monster wind it up first and use it on its target when the time is up.

### Prop_TicketTaker

**Hosts:** mobs, items

**Parameters:**

```text
[Cost of boarding]/[Name of ticket item]
```

**Examples:**

```text
100
a red ticket
```

This property makes a mob or item the master of a carriage, horse, ship, or other rideable item. They will require the payment specified in the parameters before allowing anyone else to board. Non-player mobs will always be allowed to board. The ticket taker must be riding either the carriage, or riding a horse pulling it, or actually BE the carriage, or either be just outside or just inside the ship or caravan type boardable.

CoffeeSUD: also accepts FARE=\[price\] PARTY=LEADER\|EACH PETS=\[price per pet\] CARGO=\[price per 100 lbs\]. LEADER charges once for the nearby party and admits its companions on the same payment. Disembarking clears the receipt. EACH preserves individual payment. The original numeric and named-ticket formats still work.

### Prop_TidalExit

**Hosts:** exits

**Parameters:**

```text
OPEN=LOW/HIGH (SPAN=[HOURS]) (MSG="[TEXT]")
```

**Example:**

```text
OPEN=LOW SPAN=1 MSG="The causeway is under water."
```

CoffeeSUD: an exit passable only at one tide: low tide in the hour the day starts, high tide in the dusk hour (by the area's clock), SPAN hours either side; MSG the rest of the time.

### Prop_TimeMode

**Hosts:** rooms, areas

**Parameters:**

```text
MODE=TURNS/REALTIME (ONLYCOMBAT=TRUE) (RESTORE=TRUE/FALSE) (MSG="[TEXT]")
```

**Examples:**

```text
MODE=REALTIME MSG="Time seems to quicken; the water is rising."
MODE=TURNS ONLYCOMBAT=TRUE
```

CoffeeSUD: a zone of its own time. Coming in, time turns to MODE (turn-based or real time), as TIMEMODE does, and MSG is said; TIMEMODE cannot change it there; leaving, the mode before comes back, unless RESTORE=FALSE. ONLYCOMBAT holds it only while one fights there.

### Prop_Trainer

**Hosts:** mobs

**Parameters:**

```text
(STR=#) (INT=#) (DEX=#) (CHA=#) (CON=#) (WIS=#) (NOTEACH)
((ALL) [CLASSNAME]) (EXPERTISENAMES) (SKILLS)
```

**Examples:**

```text
CON=10
STR=30 INT=15 DEX=20 CON=0 CHA=0 WIS=0 NOTEACH
all Fighter all Cleric
```

This property makes a mob a suitable target for the TRAIN command for players. With no parameter, it will set all stats to 25, give them 0 levels in all classes, and give them all expertises. Other parameters may be entered in order to set the stats of the mob to the specified values. Any values not given will default to 25. If you do not want the mob to also be a trainer, you may include the NOTEACH flag. Listing one or more class names will make that trainer have 0 levels in those classes (able to train to it). Not listing any will give the mob ALL classes at level 0. Listing expertise IDs works similarly.

CoffeeSUD: the teacher's rules, in any order among the rest: STUDENTMASK="\[ZAPPER MASK\]" (whom it teaches at all; REFUSEMSG="\[TEXT\]" is what it says to others), SKILLMASK.\[ABILITY ID\]="\[ZAPPER MASK\]" (whom it teaches that skill), PRICE="\[N\] per level" or PRICE=\[N\] (money it takes for each lesson, on top of the usual cost) and HIDDEN=\[ID\],\[ID\] (skills it teaches but leaves off TEACHLIST). They gate TEACH ME, GAIN, PRACTICE and TRAIN with it, and TRAIN with no trainer named passes over one who refuses. TEACHLIST shows a student what this teacher offers them.

### Prop_Transform

**Hosts:** items, mobs

**Parameters:**

```text
(AFTER=[TICKS]/DAYS=[GAME DAYS]/WHEN=NIGHT/DAY/DAWN/DUSK/FULLMOON/[SEASON]/"[ZAPPER MASK]") (START=GET/DROP/WEAR/ALWAYS) (RESET=true) (ONLYIN=ROOM/CONTAINER/CARRIED) INTO="[ITEM]"/MOB="[MOB]"/SET="NAME=.. DISPLAY=.. DESC=.. PROPS=.." (REVERT=true) (KEEP=FOLLOW,STATE,TAGS,USES,CONTAINER,WORN) (MSG="[TEXT]")
```

**Examples:**

```text
DAYS=3 START=GET ONLYIN=CARRIED MOB="a wyrmling" KEEP=FOLLOW MSG="$p cracks open!"
WHEN=NIGHT REVERT=true MOB="a stone gargoyle" MSG="$p stirs and spreads its wings!"
```

CoffeeSUD: a thing that changes form. The time: AFTER ticks or DAYS game days (what is left is kept with it), counted once START holds (GET: carried, DROP: lying in a room, WEAR: worn; ALWAYS by default) and while ONLYIN does; RESET starts again when START no longer holds. Or WHEN: a time of day, FULLMOON, a season, or a zapper mask tested on its room. It becomes: INTO, an item (from the catalog, or a class) in its place, MOB, a mob there (KEEP=FOLLOW: following the one who carried it), or SET, changed in place (PROPS adds properties). KEEP is also MPTRANSFORM's (STATE, TAGS, USES, CONTAINER, WORN). With WHEN, REVERT=true changes it back when WHEN ends. MSG is shown ($p is what it became). What it becomes may have a Prop_Transform of its own.

### Prop_TransitView

**Hosts:** rooms (created on the fly).

**Parameters:** the text to show on LOOK.

CoffeeSUD internal. The MovingRoom behaviour (vehicle and route rooms that move past scenery) adds a non-saved Prop_TransitView to the rooms it passes through; when someone looks at the room, the text is told after the normal room description. It is not savable. For a permanent extra view line, use [Prop_RoomView](#prop_roomview) or [Prop_Details](#prop_details).

### Prop_Transporter

**Hosts:** mobs, items, exits, rooms, areas (shipped help: MOBs, Items, Exits, Rooms, Areas, Weapons, Food, Drink, Armor)

**Parameters:**

```text
Room ID (; (SENDENTER=[TRUE/FALSE]))
```

**Examples:**

```text
Midgaard#3001
Midgaard#3001;sendenter=true
```

This property is used to transport mobs to a particular place based on an action taken. The actions which trigger the transport include:

Rideable - mount

MOB - speak

Item - get

Exit - enter

Room - enter

Area - enter

Weapon - use in battle

Food - eat

Drink - drink

Armor - wear

Normally, this skill bypasses any normal notifications, but behaves as if the transported mob is "just there". Add a semicolon and set "sendenter" to true for scripts to see the movement.

### Prop_Trashcan

**Hosts:** items, rooms (shipped help: Room, Container Item)

**Parameters:**

```text
(DELAY=[#TICKS])
```

**Example:**

```text
DELAY=4
```

Causes all items dropped, or placed in the host container to be destroyed and to cease to exist. If a delay parameter is used, the destruction of the items in the can will not occur until that time has passed.

### Prop_TravelTime

**Hosts:** rooms, exits, areas.

**Parameters:**

```text
TIME=[game minutes] MOUNTED=0.5 VEHICLE=0.7 NIGHT=1.5
```

**Example:**

```text
TIME=30 MOUNTED=0.5 NIGHT=1.5
```

CoffeeSUD addition. `TIME` is the game time spent per step through the host. The multipliers apply when mounted, in a vehicle, and at night. Priority: the exit, then the destination room/area, then the source room/area. Followers do not add another time charge. The scheduler runs weather, hunger, NPCs and scripts during travel. See chapter 30.

### Prop_TweakWatch

**Hosts:** mobs.

**Parameters:** `[tweak ability ID] [watched effect ID]`. Set by the engine.

CoffeeSUD internal (GenTweakAbility). When a generic tweak ability has a SCRIPT with an `ONEXPIRE` section, the engine puts a Prop_TweakWatch on the target. On each tick it checks that the watched effect is still present; when it has gone, it calls the tweak's `ONEXPIRE` script once and removes itself. It is not savable and shows nothing. See the abilities chapter (25) for GenTweakAbility.

### Prop_Uncampable

**Hosts:** mobs, items, rooms

**Parameters:** none.

If this mob, room, or item is otherwise rejuvenating, this property will prevent the rejuvenation from actually occurring if PCs are in the same room as the rejuvenation, but an admin is not present.

### Prop_Undroppable

**Hosts:** Items (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
(MESSAGE="[message]") (AMBIANCE="[ambiance]") (LOSTOFF=[T/F])
```

**Example:**

```text
message="You can't drop that."
```

Makes the mob or item unable to be dropped. The message shown and whether the item has an ambiance can also be set, and whether this property is automatically removed when the item is lost to another owner.

Notes:

- `LOSTOFF` no longer throws. Declares no host type, so `?P` does not list it; add it to an item by name.

### Prop_Unsellable

**Hosts:** Items, Mobs (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
(MESSAGE="[message]") (AMBIANCE="[ambiance]") (DROPOFF=[T/F])
```

**Example:**

```text
message="You can't sell that."
```

Makes the mob or item unable to be sold to a shopkeeper. The message shown and whether the item has an ambiance can also be set, and whether this property is automatically removed when the item is dropped.

Notes:

- Declares no host type, so `?P` does not list it; it can still be added to an item or mob by name.

### Prop_UpgradePath

**Hosts:** items

**Parameters:**

```text
TO=catalog:[ITEM]/ADJ="dam+[N] att+[N] arm+[N]" (COST=[GOLD]) (MATERIALS="[NAME] x[N]") (LEVEL=[N]) (KEEP=[KEEPS]) (MSG="[TEXT]")
```

**Example:**

```text
TO="catalog:Starfall, the reforged blade" COST=2000 MATERIALS="starmetal ingot x3"
```

CoffeeSUD: an item's own way up, for any ItemUpgrader (a smith, a workbench): its row, without FROM. See BEHAVIOR_ITEMUPGRADER.

### Prop_UseAdjuster

**Hosts:** items

**Parameters:**

```text
statistic names followed by a +, -, or = and a value
(reversed)
The above parameters may be followed by an optional mask
```

**Examples:**

```text
move+100 class=thief race=elf gender=F
dex-1 reversed MASK=-Race +Dwarf
```

When the item with this property is used in a particular way, the listed effects will be permanently given to the owner. For items, trigger use includes:

Food, Drink: EAT/DRINK

Weapon: Doing damage with (can be reversed)

Container: Putting something into

Armor: Taking damage while wearing properly

The REVERSED parameter is special, and means that instead of the effect going to the owner of the item, it goes to the other party in a trigger, such as to the target instead of the owner when a weapon does damage, or to the attacker instead of the owner when taking damage with an armor.

All parameters use the + or - adjuster, except the class, race, gender, ableprofs, and ablelvls parameters. Valid parameters are as follows:

strength dexterity constitution charisma wisdom intelligence

maxstr maxdex maxcon maxcha maxwis maxint (maximum trainable)

savepar savefir savecol savewat savegas savemin savegen savejus

saveaci saveele savepoi saveund savemag savedis savetra

ability (magical level), armor, attacks (prowess), damage (max), disposition (see AHELP ADJDISPOSITION), level, rejuv(!NO!), senses (see AHELP ADJSENSES), speed (#attacks), height, weight, ambiance+``,gender, class, race, hitpoints, hunger (stomach size), mana, movement, thirst (stomach size) conversion weightadj saveblunt savepierce saveslash savespells saveprayers savesongs savechants critpctweapons critpctmagic critdmgweapons critdmgmagic faith deity rejuvrate xpadjpct. Ableprofs and ablelvls is a commma-delimited list of ablityids followed by a number in parenthesis to adjust proficiency or casting level by.

By default, the values + or - are added to the existing values. However, if MULTIPLYPH=true is included in the parameters, then attack, armor, damage, weight and height will instead have their values multiplied by the given value / 100, and speed by the given value itself (SPEED+1.5 is one and a half times). Likewise, if MULTIPLYCH=true is included, then hitpoints, mana, and movement are similarly multiplied instead of added. All other values act the same.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_UseEmoter

**Hosts:** items

**Parameters:**

```text
Flags, pct chance, emote strings, ';' separated.
```

**Examples:**

```text
chance=25;wiggles his bottom.;smiles evilly.;burps!
smell chance=25 ;smells horrible!;is really stinky!
chance=100 inroom="Here#1,Here#2";looks horrible!;is bad!
broadcast sound chance=25;grumbles!;eeeks!
chance=25;sound grumbles!;visual leers broadly.;social smile
```

This property defines a serious of emotes, one of which is triggered when an item is used, such as armor worn, weapon wielded, container filled, drinkable filled. All emote strings are separated by the semicolons shown above. The first parameter can be empty for defaults,the percent chance of occurrence after the event occurs, a list of rooms where the emoter MUST be for the emoter to work (inroom=...), a mask="..." zapper mask to filter who can trigger the emoter, a tag to make the emote sound based (sound), a tag to make the emote smell based (smell), smoking (puff), a tag to make the emote broadcast to adjacent rooms (broadcast), or a tag to make the message private to the actor (private).

You can also mix and match the types of emotes by putting the keywords SIGHT, SMELL, NOISE, PUFF, and SOCIAL as the first word in each emote string. This denotes the type of emote for the rest of the string. The last example shows how to enter a mixed emote.

If an emote begins with MASK= then the remaining is a zappermask for the mob who is causing the emote.

Notes:

- Smells go to the smell pool, not the visual pool.

### Prop_UseEmoter2

**Hosts:** items

**Parameters:**

```text
Flags, pct chance, emote strings, ';' separated.
```

**Example:**

```text
See Prop_UseEmoter
```

This property defines a serious of emotes, one of which is triggered when an item is used, such as item gotten, food ate or drank, or liquid poured. All emote strings are separated by the semicolons shown above. The first parameter can be empty for defaults,the percent chance of occurrence after the event occurs, a list of rooms where the emoter MUST be for the emoter to work (inroom=...), a mask="..." zapper mask to filter who can trigger the emoter, a tag to make the emote sound based (sound), a tag to make the emote smell based (smell), a tag to make the emote broadcast to adjacent rooms (broadcast), or a tag to make the message private to the actor (private).

You can also mix and match the types of emotes by putting the keywords SIGHT, SMELL, NOISE, and SOCIAL as the first word in each emote string. This denotes the type of emote for the rest of the string. The last example shows how to enter a mixed emote.

### Prop_UseSpellCast

**Hosts:** items

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis.
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

When the item with this property is worn or wielded, filled or has something placed in it, the owner will be affected by the listed spells.

The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_UseSpellCast2

**Hosts:** items

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis.
The above parameters may be followed by an optional mask
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

When the item with this property is touched, or in the case of food and drink, eaten or drank, the owner will be affected by the listed spells.

The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.

### Prop_Verbs

**Hosts:** mobs, items, rooms, exits, areas.

**Parameters:**

```text
VERBS="pull,yank" MSG="[what the doer sees]" OTHERS="[what the others see]"
```

**Example:**

```text
VERBS="pull,yank" MSG="You pull the rusty chain. Nothing happens." OTHERS="<S-NAME> pulls the rusty chain."
```

CoffeeSUD addition (ST7). Gives the host extra command words with no script. When a character types one of the VERBS words followed by the name of the host (or, if the host is a room, the verb alone), the engine sends a visual message: MSG to the doer, OTHERS to the rest of the room. Use a `VERB_PROG` script on the host for anything that has to do more than flavour text.

Notes:

- `LIST VERBS` shows the verbs of the current room (`ScriptEvents`), marking those from Prop_Verbs.
- [Prop_When](#prop_when) can turn a Prop_Verbs on and off with a condition (see the i4-when fixture).

### Prop_Waypoint

**Hosts:** items, rooms.

**Parameters:**

```text
NETWORK=[name] NAME="[destination]" ID=[stable author ID] ALIASES=[old IDs] LEGACYKEYS=[old tokens]
DISCOVER=ENTER|TOUCH|RITUAL|QUEST SCOPE=CHARACTER|PROFILE
COST="mana|moves|gold [number]" TIME=[game hours] MASK="[mask]" MSG="[text]" RITUAL=[function] QUEST="[quest name]"
```

**Example:**

```text
NETWORK=waystones NAME="Stonegate Waystone" ID=stonegate DISCOVER=TOUCH SCOPE=PROFILE COST="gold 10" TIME=2
```

CoffeeSUD addition. A fast-travel point. `TRAVEL` lists the discovered destinations in the current network and `TRAVEL <name>` moves the nearby party. Discovery is saved per character, or shared by the profile (`SCOPE`). `ID` keeps the key stable when a waypoint is moved or renamed ([Saved identities](#saved-identities)). `TRAVEL ATTUNE` checks masks and ritual/quest requirements. `TOUCH` and `USE` activate touch waypoints. The cost is charged once, after every passenger passes the entry checks. See chapter 30.

### Prop_WeakBridge

**Hosts:** rooms, exits

**Parameters:**

```text
max=[WEIGHT] chance=[%] down=[# TICKS]
```

**Example:**

```text
max=400 chance=75 down=300
```

Whenever a player enters or exits the room or exit with this property, that player is in great danger. If their total weight exceeds the weight specified by the max parameter, there is a % chance equals to the chance parameter that the bridge beneath them will break. If the property is on an exit, that means instant death for the player. If the property is on an InTheAir type room with a place to fall beneath it, they will fall. If the property is on any other kind of room, instant death will result. The down property describes how many ticks the bridge will remain "down" once it has been broken by a heavy player. Flying can always overcome this property.

Notes:

- Every occupant falls when the bridge gives way.

### Prop_WeaponImmunity

**Hosts:** mobs, items

**Parameters:**

```text
List of immunities
```

**Examples:**

```text
+ALL -SILVER -CLOTH -LEATHER
+ALL -MAGIC -SLASHING -FROSTING
+ALL -LEVEL15
+BASHING +IRON +STEEL +MAGIC
+ALL +MAGICSKILLS +AXE +THROWN +DAGGER
```

Whenever the item with this property is worn, or for the mob with this property, they will attain damage immunities to the types and/or materials of weapons specified. Here are the parameters:

+ALL - Specifies that, by default, the mob is immune to ALL damage.

-DAMAGETYPE- Creates an exception to the +ALL rule by permitting damage of the given type. Examples for DAMAGETYPE would be -PIERCING, -GASSING, -SLASHING, -BURNING, -MELTING, -NATURAL, -BASHING, etc.

+DAMAGETYPE- Can be used instead of +ALL to create an immunity to the given damage type. Examples for DAMAGETYPE would be +PIERCING, +GASSING, -WEAPONCLASS- Creates an exception to the +ALL rule by permitting damage of the given type. Examples for WEAPONCLASS would be -AXE, -BLUNT, -EDGED, -FLAILED, -HAMMER, -KARATE, -POLEARM, -RANGED, -SWORD, -DAGGER, -STAFF, -THROWN, etc.

+WEAPONCLASS- Can be used instead of +ALL to create an immunity to the given of the given type. Examples for WEAPONCLASS would be +AXE, +BLUNT, +EDGED, +FLAILED, +HAMMER, +KARATE, +POLEARM, +RANGED, +SWORD, +DAGGER, +STAFF, +THROWN, etc.

-MAGIC - Creates an exception to the +ALL rule by permitting damage from magical spells and magical weapons.

+MAGIC - If +ALL is not used, this will create an immunity to magical spells and weapons.

-MAGICSKILLS - Creates an exception to the +ALL rule by permitting damage from magical spells, chants, songs, or prayers.

+MAGICSKILLS - If +ALL is not used, this will create an immunity to magical spells, chants, songs, and prayers.

-LEVELX - Creates an exception to the +ALL rule by permitting damage from weapons whose level is X or better. Example: -LEVEL1, -LEVEL5, -LEVEL10, etc.

+LEVELX - Can be used instead of +ALL to create an immunity to weapons whose level is X or lower. Example: +LEVEL1, +LEVEL5, +LEVEL10, etc.

-MATERIALTYPE - Creates an exception to the +ALL rule above by permitting damage from a weapon made of the given MATERIALTYPE. Where MATERIALTYPE may be any valid material, such as SILVER, WOOD, COTTON, etc. As for the damage types above, +MATERIALTYPE may be used instead of +ALL to create an immunity to a given material.

-PARALYSIS - Creates an exception to the +All rule by permitting damage from paralysis spells.

+PARALYSIS - If +All is not used, this creates an immunity to damage from paralysis spells.

-FIRE - Creates an exception to the +All rule by permitting damage from fire spells.

+FIRE - If +All is not used, this creates an immunity to damage from fire spells.

-COLD - Creates an exception to the +All rule by permitting damage from cold spells.

+COLD - If +All is not used, this creates an immunity to damage from cold spells.

-WATER - Creates an exception to the +All rule by permitting damage from water spells.

+WATER - If +All is not used, this creates an immunity to damage from water spells.

-GAS - Creates an exception to the +All rule by permitting damage from gas spells.

+GAS - If +All is not used, this creates an immunity to damage from gas spells.

-MIND - Creates an exception to the +All rule by permitting damage from mind spells.

+MIND - If +All is not used, this creates an immunity to damage from mind spells.

-JUSTICE - Creates an exception to the +All rule by permitting damage from justice spells.

+JUSTICE - If +All is not used, this creates an immunity to damage from justice spells.

-ACID - Creates an exception to the +All rule by permitting damage from acid spells.

+ACID - If +All is not used, this creates an immunity to damage from acid spells.

-ELECTRICITY - Creates an exception to the +All rule by permitting damage from electric spells.

+ELECTRICITY - If +All is not used, this creates an immunity to damage from electric spells.

-POISON - Creates an exception to the +All rule by permitting damage from poison spells.

+POISON - If +All is not used, this creates an immunity to damage from poison spells.

-UNDEAD - Creates an exception to the +All rule by permitting damage from undead spells.

+UNDEAD - If +All is not used, this creates an immunity to damage from undead/evil spells.

-DISEASE - Creates an exception to the +All rule by permitting damage from disease spells.

+DISEASE - If +All is not used, this creates an immunity to damage from disease spells.

-TRAPS - Creates an exception to the +All rule by permitting damage from traps spells.

+TRAPS - If +All is not used, this creates an immunity to damage from traps spells.

Notes:

- `LEVEL` is read for single-digit levels too.

### Prop_WeaponMessages

**Hosts:** items (weapons, including racial natural weapons).

**Parameters:**

```text
HIT="<S-NAME> sear(s) <T-NAMESELF> with <TOOLNAME>: <DAMAGE>." MISS="<S-NAME> swing(s) wide." DAMAGEWORDS="singes,scorches,burns,incinerates"
```

**Example:**

```text
HIT="<S-NAME> sear(s) <T-NAMESELF> with <TOOLNAME>: <DAMAGE>." DAMAGEWORDS="singes,scorches,burns,incinerates"
```

CoffeeSUD addition. Per-weapon combat text. `HIT` and `MISS` may contain `|`-separated random alternatives; the normal combat source, target and damage substitutions apply. This property's text wins over local hit/miss lists ([Prop_LocalLists](#prop_locallists)); its damage words win over local damage words.

For races, `HEALTHCHART` (eleven comma-separated health lines with `<MOB>` and `|` alternatives) and `CORPSEBLURBS` (`cause=phrase|alternative` pairs separated by `;`, for example `BURNING=molten gears;ALL=a heap of springs`) are fields of `MODIFY RACE`; empty fields keep the `HEALTHRACE` and local/global fallbacks.

### Prop_WearAdjuster

**Hosts:** items (shipped help: Items (armor and weapons principally))

**Parameters:**

```text
see Prop_HaveAdjuster, and may include optional LAYERED flag
```

**Examples:**

```text
str+2 dex-2 class=thief race=elf allset=aname-3
str+2 dex-2 move+100 LAYERED MASK=-Race +Dwarf
```

Whenever the item having this property is wielded or worn, the statistics of the owner will be adjusted as described in the parameters. Removing the item will remove the effects. The Layered flag may be used to prevent bonus or penalty stacking due to multiple items worn on the same location. See Prop_HaveAdjuster for more information on the valid parameters for this property.

### Prop_WearEnabler

**Hosts:** items

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis.
The above parameters may be followed by an optional mask
The above parameters may include optional LAYERED flag
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
Spell_MageArmor;LAYERED;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the item with this property is worn, wielded, or held, the owner will be granted the spells, skills listed. The percentage given is optional, and will be the proficiency of the skill. If a percentage is not given, the default is 100%. The layered flag means that, among layers of armor, only the top layer will grant its effect.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax. ALLSET="set-name-N" activates this bonus when at least N worn pieces share the set name, including pieces using the other ALLSET bonus properties. Each property keeps its own threshold; the default is two pieces.

### Prop_WearOverride

**Hosts:** items

**Parameters:**

```text
(DRESS) (CONTENTS) ([masking params])
```

**Example:**

```text
-class +mage -race +elf -evil
```

Whenever the owner of the item with this property tries to wear or wield the item, they will be be permitted only if allowed by the mask argument. Additionally, if they are permitted, then any wear restrictions that would normally prevent them from wearing the item will be suspended until the item is removed. This is so that you can make special armor for creatures that normally can't wear standard gear, such as barding for horse backs, and so forth. The DRESS argument will allow the item to be dressed or undressed from a creature without regard to normal permission rules. The CONTENTS parm allows the contents to be seen or manipulated.

For valid masking parameters see AHELP ZAPPERMASK, where allowed can wear, and disallowed is not.

### Prop_WearResister

**Hosts:** items (shipped help: Items (armor and weapons principally))

**Parameters:**

```text
resistance names followed by percentages
Parameters may be followed by an optional mask
May include optional LAYERED flag
```

**Examples:**

```text
magic 50% poison -10%
magic poison 50% pierce -10% MASK=-RACE +Dwarf
fire 40% SOURCEMASK="-RACECAT +Dragon"
magic 50% poison -10%
magic 50% poison -10% LAYERED MASK=-RACE +Dwarf
```

CoffeeSUD: SOURCEMASK="\[ZAPPER MASK\]" makes it work only against the attackers it fits; MASK= is still whom it protects.

Whenever the item having this property is wielded or worn, the owner gains certain resistances and immunities described in the parameters. When removed, the resistances and immunities are also cancelled. The Layered flag may be used to prevent resistance stacking due to multiple items worn on the same location. The effects which may be included are:

Resistances: gas fire electricity mind magic cold acid water evil (undead)

Resistances: justice (as per many thief abilities)

Resistances: weapons blunt pierce slash non-magical-weapons non-silver-weapons

Resistances: -- weapons get reduced % damage per round, lvl based, with limits.

: : -- always modifier makes weapon resists always work, no limits.

Resistances: spell chant song prayer thief_skill (ability types)

Resistances: enchantment/charm kicking vexing conjuration (ability domains)

Immunities : teleport (includes gate, summon) holy disease poison

Misc : debuf-duration (pct is the maximum debuf ticks remain)

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.ALLSET="set-name-N" activates this bonus when at least N worn pieces share the set name, including pieces using the other ALLSET bonus properties. Each property keeps its own threshold; the default is two pieces.

### Prop_WearSpellCast

**Hosts:** items

**Parameters:**

```text
a list of properly named spells separated by semicolons
Spell names may optionally have parameters in parenthesis.
The above parameters may be followed by an optional mask
The above parameters may include optional LAYERED flag
```

**Examples:**

```text
Spell_MageArmor;Spell_Shield
Spell_MageArmor;LAYERED;Spell_Shield
90%;Spell_Portal(Somewhere else);Prayer_SummonElemental(/FIRE)
Spell_MageArmor;Spell_Shield; MASK=-RACE +Dwarf
```

So long as the item with this property is being worn, wielded, or smoked, the owner will be under the effect of the listed spells. The layered flag means that, among layers of armor, only the top layer will grant its effect. The flag NOUNINVOKE can be substituted for a spell to make all the effects non-dispellable until the property no longer applies; use this flag with care! The flag LEVEL=\[NUMBER\] can also be substituted for a spell name to force all the listed effects be cast at a different level than default.The flag MAXTICKS=\[NUMBER\] can be used to shorten the length of all spell casts.

The flag TICKS=\[NUMBER\] can be used to shorten the length of all subsequent listed spells.

The parameters may also END with the key word MASK= followed by a mask. This mask will select which kinds of mobs and players will be affected by this property. See ZAPPERMASKS for more information on zapper mask syntax.ALLSET="set-name-N" activates this bonus when at least N worn pieces share the set name, including pieces using the other ALLSET bonus properties. Each property keeps its own threshold; the default is two pieces.

### Prop_WearZapper

**Hosts:** items

**Parameters:**

```text
masking parameters described below, optional zap message
```

**Examples:**

```text
-class +mage -race +elf -evil
MESSAGE="<O-NAME> falls off <S-NAME>!" -class +mage
-class +mage "event=accuse,trespassing" "message=^N"
```

Whenever the owner of the item with this property tries to wear or wield the item, they will be zapped unless it is allowed by the listed parameters.

An optional MESSAGE parameter can define what is seen when the zap occurs. Also optionally, the parameters can start with the word ACTUAL to change zapping behavior to look at actual values instead of perceived values (this about a class check applied to a Charlatan.) The optional EVENT parameter is a comma-delimited set of strings, where the first is one of ZAP (default) or ACCUSE followed by a comma and the crime key from a laws.ini type definition. If the parameters include any alignment checks, the item will end up with an appropriate ambiance as well. For valid masking parameters, See AHELP ZAPPERMASK, where allowed can wear, and disallowed is zapped.

### Prop_Weather

**Hosts:** areas

**Parameters:**

```text
the weather type and/or one or more climate masks
```

**Examples:**

```text
RAIN
RAIN CLIMATE_WET
```

Makes this area always have the same weather. Valid weather values include: "CLEAR", "CLOUDY", "WINDY", "RAIN", "THUNDERSTORM", "SNOW", "HAIL", "HEAT", "SLEET","BLIZZARD","DUST","DROUGHT","COLD"

Can also change the climate settings. Valid climate mask values include: "CLIMATE_NORMAL", "CLIMATE_WET", "CLIMATE_COLD", "CLIMATE_WINDY", "CLIMATE_HOT", "CLIMATE_DRY", "CLIMATE_VOID"

### Prop_When

**Hosts:** rooms, areas, exits, items, mobs

**Parameters:**

```text
PROPS="[ID]([ARGS]);[ID]([ARGS])"/ID=[ID] PARMS="[ARGS]" (WHEN="[ZAPPER MASK]") (CHECK=HOST/OWNER/ROOMPLAYERS/TRIGGERER) (EVERY=[TICKS]) (CHANCE=[PERCENT]) (COOLDOWN=[TICKS]/[N]H) (ONCE=CHAR/PROFILE/WORLD) (ONSTART="[ACTIONS]") (ONEND="[ACTIONS]") (ELSEMSG="[TEXT]")
```

**Examples:**

```text
WHEN="-IF ISMOON(FULL)" PROPS="RandomMonsters(catalog:werewolf;3;5);Emoter(min=5 max=10 howls)"
CHECK=ROOMPLAYERS COOLDOWN=24H ONCE=CHAR PROPS="Prop_Transporter(Feywild#1)"
```

CoffeeSUD: properties and behaviours that are there only while a condition holds. PROPS lists them (abilities or behaviours, a semicolon between; their arguments in brackets), or ID and PARMS name one. WHEN is a zapper mask on what CHECK says: HOST (the thing it is on, the default), OWNER (an item's owner), ROOMPLAYERS (any of the player's side in its room) or TRIGGERER (the last of them to come in). Every EVERY ticks (1) it looks again: when the condition comes true what it wraps is added (at CHANCE percent; not before COOLDOWN, ticks or game hours with H, has passed since it last went; ONCE a character, a profile or a game), and ONSTART runs; when it no longer holds they are taken off and ONEND runs. ELSEMSG is told to one who comes in while it is off. What it wraps is not saved; its cooldown and ONCE=WORLD are.

### Prop_WizInvis

**Hosts:** mobs

**Parameters:** none.

Grants the MOB unlimited sensory knowledge as well as complete undetectibility.

### Prop_Workstation

**Hosts:** items, rooms

**Parameters:**

```text
TAG=[NAME],... (SKILLS=[SKILL ID],...) (SPEED=[PCT]) (PROF=[N]) (MAXLEVEL=[N]) (QUALITY=[N])
```

**Example:**

```text
TAG=forge,anvil SKILLS=Blacksmithing,Weaponsmithing SPEED=25 PROF=+10 MAXLEVEL=40 QUALITY=+1
```

CoffeeSUD: a workstation, for a crafter in the room with it (on the room, or on an item lying there). TAG is what it is, as a recipe's or skill's STATION asks for it. SKILLS are the skills it helps (all when not given). SPEED takes that percent off the time a craft takes, PROF is added to the crafter's proficiency, MAXLEVEL lets recipes up to that level be made here (above the crafter's own), and QUALITY is for quality tiers (AHELP CRAFTQUALITY). An item with a tag (TAGS) and no Prop_Workstation counts as a station of that name too, with no bonus. A recipe asks for one with STATION=\[NAME\] (AHELP RECIPEKEYS), and a data skill (GenCraftSkill) for all its recipes with its STATION field.

### Prop_Yield

**Hosts:** rooms, areas

**Parameters:**

```text
(HARVESTS=[N]) (DROP=[PCT]%) (REGROW=[N] [HOURS/DAYS/MONTHS/YEARS]) (EMPTYMSG="[TEXT]")
```

**Example:**

```text
HARVESTS=5 DROP=0% REGROW=7 DAYS EMPTYMSG="The glade is picked clean."
```

CoffeeSUD: how a room, or every room of an area, is gathered out and grows back. Each harvest of a room takes a share off the next (DROP; the skill's own, 20% for most, when not given), and HARVESTS is how many it gives before it is bare: trying again then shows EMPTYMSG and finds nothing. REGROW is how long after its last harvest it grows back, in game time (a bare number is game hours); without this property a room grows back 30 game minutes after its first harvest. All of it runs on game time, so it waits while the game is paused or between turns, and the save keeps it. With Prop_ImproveGather (AMT=0 outside a season) and ResourceOverride's seasons, a glade can give mushrooms five times in autumn and then rest a week.

## Reference: property-type abilities in Abilities/Misc

These are permanent status effects, not `Prop_*` classes. Most extend `StdAbility` and are classified as properties, so they can be listed with `LIST PROPERTIES`, added in the effects editor by typing the ID, and appear in `?P`. Many are put on characters by the engine (hunger, age, moods) rather than by builders. The headings use the class ID.

Index: [Addictions](#addictions), [Adorable](#adorable), [Age](#age), [Alerted](#alerted), [Allergies](#allergies), [AmbianceAdder](#ambianceadder), [AutoAwards](#autoawards), [AutoStack](#autostack), [BadReputation](#badreputation), [Banishment](#banishment), [Carnivorous](#carnivorous), [Copyright](#copyright), [Dusty](#dusty), [ExtraData](#extradata), [Gait](#gait), [GoingHome](#goinghome), [Injury](#injury), [Loyalty](#loyalty), [Milkable](#milkable), [Mood](#mood), [Phobias](#phobias), [Pregnancy](#pregnancy), [Prisoner](#prisoner), [QuestBound](#questbound), [Regeneration](#regeneration), [ScriptLater](#scriptlater), [Shaming](#shaming), [SlowLearner](#slowlearner), [SmellsLikeCherries](#smellslikecherries), [SoundEcho](#soundecho), [TemporaryAffects](#temporaryaffects), [TemporaryImmunity](#temporaryimmunity), [TriggeredAffects](#triggeredaffects), [Soiled](#soiled), [InstanceArea](#instancearea), [Merchant](#merchant), [BookLoaning](#bookloaning), [Spell_Teleport](#spell_teleport)

### Addictions

**Hosts:** mobs

**Parameters:**

```text
[ITEM NAME];...
```

**Example:**

```text
pot pie;sugar
```

This ability governs addictions to food, drink, smoking, or sniffing. The parameter is the substring to match in the name of the item. The player with an addiction will be annoyed unless he gets his "fix" every rl hour. Players can only become addiction through the engine by smoking, or by a drink infected with inebriation, alcohol, or liquor. The player can either "ride out" the addiction for 24 hours, suffering increasingly annoying messages as he goes, or he can keep making his fix. Addictions are not diseases, and can not be cured with magic or restoration. Multiple substances can be listed by separating with semicolons, and the matches are made by parts of item names.

### Adorable

**Hosts:** mobs

**Parameters:** none.

The character is just so adorable that higher level players and creatures simply won't attack them, and they get a natural charisma boost.

### Age

**Hosts:** mobs, items

**Parameters:**

```text
[MILLISECONDS]/[AGE YEARS]/+[# MUD HOURS]/[AGE RANGE]
```

**Examples:**

```text
106222323232
10
child
+9999
```

The ability to age from infancy (an item) to adulthood (a mob). The normal argument is the birth time in real-life epoc milliseconds. A number smaller than 32768 will be interpreted as a number of years old. An argument beginning with a + will adjust the milliseconds birth time backwards by the given number of mud-hours. Only a timestamp argument will allow aging to occur over time.

This process can also alter the race of a mob using the /resources/skills/age.txt instructions. The format of that rules in that doc is as follows:

\[RACEID\] \</\> \[AGE CATEGORY\] \[GENDER CHAR\]/\* \[NEWRACEID\]

Each field is tab-separated. The first is the existing race, then a \< or \> character, then the age category that the race needs to be greater or less than, then either the Gender to match that line, or an \* to match any unlisted Genders, and lastly the race to convert them into.

### Alerted

**Hosts:** mobs.

**Parameters:** `ROOM=[room ID] INTRUDER=[name]`.

**Example:**

```text
ROOM=Keep#12 INTRUDER=Bob
```

CoffeeSUD alarm state, put on guards by the alarm system (`ALERTTICKS` sets how long). While it lasts, the mob stops wandering, stands up if needed, attacks the intruder (or their party, or if none is named any player character) when it can see them, and otherwise walks to `ROOM` (or to where the intruder is, if they are in the same area). It is not in the shipped property help.

### Allergies

**Hosts:** mobs

**Parameters:**

```text
Resource or Race names
```

**Example:**

```text
STRAWBERRIES DWARF
```

This ability governs allergies.

### AmbianceAdder

**Hosts:** mobs, items, rooms, areas, exits

**Parameters:**

```text
[AMBIANCE TEXT](;[AMBIANCE TEXT]...)
```

**Example:**

```text
^^YGLOWING^^N;^^hshining^^n
```

Allows the builder to add permanent ambiance "flags" to an object.

Ambiances, by default, are designed for adding random text to the room display text of items and mobs, such as "glowing" or other decorations. Ambiances also have a 'flagging' capability, when specific string values are used. These include:

-ALL : hides all natural flag displays

-MOST : hides most natural flag displays

-GOOD, -EVIL, -INVISIBLE, -SNEAKING : hides these flag displays

-HIDDEN, -MAGIC, -METAL, -GLOWING : hides these flag displays

-BUSY, -LAW, -CHAOS : hides these flag displays

-BOUND, -FLYING, -FALLING : hides these flag displays

(?)X:Y : shows X if target heard, Y otherwise

@X : X is an special flag, not to be shown by default

#X : X is shown if @x is also an ambiance

### AutoAwards

**Hosts:** mobs

**Parameters:** none.

This ability governs properties and affects based on player attributes and date/time changes. See awards.txt for more information.

### AutoStack

**Hosts:** rooms, areas

**Parameters:**

```text
(TRIGGERCOUNT=[NUM]) (GROUPCOUNT=[NUM]) (CHECKTICKS=[NUM])
```

**Example:**

```text
triggercount=1000 groupcount=10 checkticks=600
```

This ability governs the automatic packaging (as stacks) of identical items, whenever the total number of items in a room is larger than TRIGGERCOUNT, and whenever there are at least GROUPCOUNT identical items to package. The checks are made every CHECKTICKS ticks.

As this is a property, it will not operate only sporadically on rooms, but like clockwork on areas.

### BadReputation

**Hosts:** mobs

**Parameters:**

```text
Percentage of faction gain
```

**Example:**

```text
45%
```

The character is of such a poorly received race that others are much less forgiving of historical events and unlikely to accept them into society. Faction changes happen much more slowly.

### Banishment

**Hosts:** mobs

**Parameters:**

```text
[AREA NAME](;[AREA NAME] .. )
```

**Example:**

```text
Midgaard
```

This mob property makes them unable to enter the area. If the area has child areas they will not be able to enter those either. Even recalling into the area won't work.

### Carnivorous

**Hosts:** mobs

**Parameters:** none.

Mobs with this effect take no nourishment from foods that aren't meat.

### Copyright

**Hosts:** items

**Parameters:**

```text
([NAME OF COPYRIGHT])
```

**Example:**

```text
Bob
```

When placed on an item, it prevents it from being transcribed, copied by the duplicate spell, renamed or titled by the BookNaming or Titling skills, or edited by the BookEdit skill.

### Dusty

**Hosts:** rooms, items (it is only active on items lying in a room).

**Parameters:** `TIME=[ms timestamp] LEVEL=[n] INTERVAL=[game hours]`, maintained by the engine.

**Example:**

```text
LEVEL=0 INTERVAL=120
```

Items left lying in a room slowly gather dust. Each `INTERVAL` game hours (default 120, doubling each step) the dust level rises: "a bit dusty", "dusty", "a dusty ...", "..., covered in dust", "..., covered in a thick layer of dust", "Something under a dense layer of dust", then "A lump of thick dust". An item picked up by a mob loses the effect. Not listed in the property help; read from the class. Shown in `?P` for rooms and items.

### ExtraData

**Hosts:** any generic object

**Parameters:**

```text
[XML Document]
```

**Example:**

```text
<VARNAME>my value</VARNAME>
```

An internal use property for attaching random keypair data to any Modifiable and Generic object.

### Gait

**Hosts:** mobs

**Parameters:**

```text
ARRIVE=[STR] LEAVE=[STR] STATE=[STR]
```

**Example:**

```text
ARRIVE="arrives" leave="leaves" state="walking"
```

This ability changes how mobs appear to be moving from room to room.

### GoingHome

**Hosts:** mobs.

**Parameters:** none (the destination is set in code).

CoffeeSUD effect applied by the HOME command and [Prop_HomeBeacon](#prop_homebeacon) while a character is on the way home: after the delay the character (and party) arrives at their home room. Any fighting in the party cancels it ("There is fighting: you do not go home."). Not for builders to attach.

### Injury

**Hosts:** mobs

**Parameters:**

```text
[BODY PART CODE]:[PART NAME]:[DAMAGE AMT];(...)
```

**Example:**

```text
1:left finger:23
```

This mob property denotes damage to a particular body part, which is a prelude to amputation.

### Loyalty

**Hosts:** mobs

**Parameters:**

```text
NAME=[PLAYER] TELEPORT=[TRUE/FALSE]
```

**Example:**

```text
NAME=Bob TELEPORT=false
```

This mob property makes a mob keep an eye out for a particular player and Follow them when they encounter them. If TELEPORT=true, they will also attempt to teleport to the player before following them.

### Milkable

**Hosts:** mobs

**Parameters:**

```text
(DRINK=[TRUE/FALSE]) (ON=[TRUE/FALSE])
(PERDAY=[AMT]) (TYPE=[RESOURCE])
```

**Example:**

```text
drink=true on=true perday=10000 type=milk
```

This ability governs the ability for an ordinary non-GenCow mob, to be milked. The MILK command becomes available as well, which you can learn about with HELP MILK.

The DRINK argument determines whether the DRINK and FILL commands work with this mob, or whether ONLY the MILK command will work with it.

The ON argument determines whether the mob, by default, is cooperative. By default, they are not, unless they are at home on private property that is managed by the milker, or are bound.

The PERDAY argument is the amount of milk, per day, that the creature generates.

The TYPE argument can be given to change the default liquid type produced, which is, of course, MILK.

### Mood

**Hosts:** mobs

**Parameters:**

```text
[name of the mood]
```

**Examples:**

```text
PROUD
HAPPY
```

This ability governs player and mob moods. It can be added like a property to mobs.

### Phobias

**Hosts:** mobs.

**Parameters:** a comma-separated list of race names, racial categories, or object words.

**Example:**

```text
spider,snake,fire
```

A fear. Each entry is matched as a race (or racial category) or, failing that, as a word in the name of items and creatures present. A mob with this effect that meets the thing it fears panics: its mood is set to SCARED, it cowers or flees, and the mood is restored afterwards. Not in the shipped property help.

### Pregnancy

**Hosts:** mobs

**Parameters:**

```text
Start Date/Delivery Date/Fathers Name/Fathers Race
```

**Example:**

```text
106222323232/12312232322/Bob/Human
```

The ability to be pregnant, and to eventually give birth. A consequence of sex under certain circumstances.

### Prisoner

**Hosts:** mobs

**Parameters:** none.

This mob property makes them unable to recall or magically leave their present location. It also prevents fleeing from combat.

### QuestBound

**Hosts:** Mobs, Items (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
Class reference code of a Quest object or *
```

**Examples:**

```text
Don't worry about it
*
```

Either a temporary affect automatically added to all quest mobs and items both created and existing, or , if the parameter is \*, a permanent effect to restrict an item or mob from being selected for a quest. Its purpose is to detect when a reset, shutdown, or other disaster is about to happen to the mob or item so that the quest can be stopped. This prevents unwanted properties/behaviors/changes from being saved to the database on accident.

### Regeneration

**Hosts:** mobs

**Parameters:**

```text
(+[Classes of weapons which do permanent damage]) (+LEVEL[NUM])
(HEALTH=[NUM BURST TICKS]) (BURST=[NUM]) (HITS=[NUM])
(MOVE=[NUM]) (MANA=[NUM]) (HEALTH=[NUM]/[SKIP TICKS])
```

**Examples:**

```text
+MAGIC +SILVER +LEVEL5
+BASHING +PIERCING
```

Allows the mob to regenerate a few hit points, mana, and movement, even during combat. Hit point damage will remain permanent (not regenerate) if the damage is done by a weapon type specified in the parameters. Valid weapon types include the standard weapon types (bashing, piercing, slashing, gassing, bursting, etc), magic to refer to spells and magical weapons, weapon materials (silver, cotton, etc), or a minimum weapon level specifier as shown above. \r

\nThe regen parameters are all optional. If the "health" parameter is given, then the mob will get a number of regen ticks equal to the number given. For instance, if the value is 2, then the mob will get 1 extra regen per normal tick, a value of 3 means 2 extra times per tick, etc. Values of 1 or less mean no extra regen ticks. The "burst" parameter refers to bursts of extra speed to the benefiting mobs equal to the number given. If the number given starts with + or -, then the adjustment will be exactly that number, as opposed to another full recovery tick. If no special regen parameters are given, defaults are used.

### ScriptLater

**Hosts:** mobs

**Parameters:**

```text
xml containing invoker, times, and scripts
```

**Examples:**

```text
<scripts><script invoker= clock=>script</script>..
<scripts><script invoker= time=>script</script>..
<scripts><script invoker= ticks=>script</script>..
```

Adds a new script to a player at a later time. Invocation arguments are mob, timeclock, future time in millis, or a number of ticks, and script to add.

Parameters to the Script tag in the Parameters incude:

INVOKER = the name of the mob who caused the later script.

-- ONE OF THE BELOW --

CLOCK = Formatted Mud Date, format=yr/month/day/hr timezone name

TICKS = number of ticks to wait before the script starts.

TIME = timestamp (milliseconds since epoc) that script start.

### Shaming

**Hosts:** mobs

**Parameters:**

```text
GLOBAL/[AREA NAME]
```

This mob property makes them unable to effectively interact with anyone in the specified area and their home area, if they are different. Attempts to interact are changed into emotes of shaming towards the shamed one.

### SlowLearner

**Hosts:** mobs

**Parameters:**

```text
Percentage of experience lost
```

**Example:**

```text
20%
```

The character does not learn from experience the way most do, gaining fewer experience points than most.

### SmellsLikeCherries

**Hosts:** mobs

**Parameters:** none.

This creature emits an aroma that is unpleasant to predators, enabling them to usually move past hostile animals unmolested.

### SoundEcho

**Hosts:** rooms, areas

**Parameters:**

```text
[RANGE]/(RANGE=[RANGE] ACCURACY=[ACCURACY]
```

**Examples:**

```text
4
range=10 accuracy=85
```

Causes sound to echo around the area or room when it occurs. In other rooms, there is a default 50% chance that the echo will seem to come from the direction it actually came from. The range can be changed by including the range argument, and the accuracy by its argument (0-100).

### TemporaryAffects

**Hosts:** mobs, items, exits, rooms

**Parameters:**

```text
[ABILITYID/BEHAVIORID] [NUM TICKS] ([PARAMETERS])
+[ABILITYID/BEHAVIORID] [NUM TICKS] ([PARAMETERS])
+AMBIANCE [NUM TICKS] ([AMBIANCE TEXT])
+BINDTO ([NAME])
-[ABILITYID/BEHAVIORID]
```

**Examples:**

```text
+Mood 10 HAPPY
+Clericness 20 Healer
+Prop_Hidden 10
```

Allows the builder to temporarily affect a mob, item, exit, or room with a property or behavior that would normally be permanent if applied in a normal way. The first parameter is the ability id or the behavior id, the second is the number of ticks that it will last, while the last parameter is optional parameters for THAT ability or behavior. The parameters may be prefixed with a plus(+) sign to allow it to stack with other temporary affects. A minus(-) sign followed by an ability or behavior id will prematurely remove the temporary affect. The ability id may be AMBIANCE to add an ambiance string. The ability id may also be BINDTO in order to bind the effects to the continued presence of a given named mob or object in the same room. If the parms (following any optional +) is prefixed with a #, then parms will be allowed on behaviors on non-mobs here.

### TemporaryImmunity

**Hosts:** mobs

**Parameters:**

```text
Ability ID/Start time in millis;etc..
```

**Example:**

```text
Disease_Cold/12312312323;Spell_Fireball/123123132
```

This is used by diseases primarily to provide a mob or player with temporary (30 mud hour) immunity to a set of diseases, but you can also include other ability ids or behavior ids on the list as well.

### TriggeredAffects

**Hosts:** mobs, items, exits, rooms

**Parameters:**

```text
[PARM LIST - see below]
```

**Example:**

```text
Disease_Cold/12312312323;Spell_Fireball/123123132
```

This allows other properties or effects to be automatically add or removed, for a limited time, based on a triggering event performed by someone in the same room.

One parameter list is required for each Property or effect. Multiple property lists must be grouped in (parenthesis).

Each parameter list has some required and some optional keywords. These are listed in key="value" format.

The required keywords for each parameter list are:

ID=\[ABILITY ID\]/\[BEHAVIOR ID\]/AMBIANCE

TICKS=\[NUMBER OF TICKS\] - how long the effect will remain or be gone after triggering.

ONTRIGGER=ADD/REMOVE - whether to add the effect on trigger, or remove it.

TRIGGER=\[RITUAL\] - the ritual that triggers the change, see AHELP RITUAL.

Optional Keywords:

PARMS=\[PARMS\] - Parameters for the property or behavior, or the ambiance.

MESSAGE=\[MSG\] - The message to echo to the room when a trigger occurs.

IMEANIT=TRUE/FALSE - PARMS apply to Behaviors even when applied to a non-agent.

INVOKE=TRUE/FALSE - the ability is invoked instead of merely made an effect.

---

### Effects documented with the properties but classified as skills

The shipped property help also documents these four. They are not property-classified, but builders attach them in the same effects editor field.

#### Soiled

**Hosts:** mobs (shipped help: Items, Mobs)

**Parameters:** none.

A property of something dirty and smelling. Bathing or moving around in water will remove it.

#### InstanceArea

**Hosts:** Mobs, Items, Area (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
TYPEID or instance modification arguments (see areainstancetypes.txt)
```

**Examples:**

```text
Triumph
ID=ANYTHING
LIKE=Triumph AREAMATCH=Midgaard
```

This misc property, when applied to an area, will put anyone who enters the area into a group instance of the area, as per StdThinInstance. Modifications to the mobs, items, and levels of the area will be applied according to a TYPEID given in the arguments, which matches a type from resources/skills/areainstancetypes.txt. If no modifications are desired, then at least an ID= parameter is required.

When the property is applied to a player or item directly, then it will also require an AREAMATCH argument to specify which area(s) will be instanced when entering them.

#### Merchant

**Hosts:** Items, Mobs, Areas, Rooms, Exits (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
<XML> or (ISELL=[whattobuy] IPREJ=[prejudice] IBUDJ=[money]
IDVAL=[devalue] IGNOR=[mask]
```

**Example:**

```text
ISELL=WEAPONS IBUDJ=1000 IGNOR=-Race +Elf
```

Merchant is primarily known as a common skill for allowing a player to sell items to other players. However, when used as a property/effect on ungettable items, areas, rooms, or exits, it allows items to be bought and sold just like a shopkeeper. Archons may add to the items to sell by either dropping the item in the same room as the Merchant, or (in the case of an area), throwing the item up into the air. The parameters have meanings identical to those listed in the Archon's Guide, except for budget, which is the maximum amount to spend on any particular item purchased from a player.

#### BookLoaning

**Hosts:** Items, Mobs, Areas, Rooms, Exits (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
<XML> or (DUEDAYS=[NUM] MAXDAYS=[NUM] MAX=[NUM])
```

**Example:**

```text
DUEDAYS=12 MAXDAYS=24 MAX=2
```

BookLoaning is primarily known as a common skill for allowing a player to loan books to other players. However, when used as a property/effect on ungettable items, areas, rooms, or exits, it allows items to be borrowed and lent just like a librarian. Archons may add to the items to sell by either dropping the item in the same room as the BookLoaning, or (in the case of an area), throwing the item up into the air. The parameters have meanings identical to those listed in the Archon's Guide

#### Spell_Teleport

**Hosts:** Exits (the class declares no host type, so `?P` does not list it)

**Parameters:**

```text
(AREANAME)/(THEME=[AUTOGENTHEME] AREASIZE=[NUM] etc)
(CASTMSG="[STR]") (APPEARMSG="[STR]") (LEAVEMSG="[STR]")
```

**Examples:**

```text
Midgaard
theme=rats areasize=100
```

When used as a property on exits, teleport can be used to generate random areas as per the Generate command or StdAutoGenInstance areas. The caster will be evicted after the spell expires. You can also, in any case, change the default teleport area, or the casting and arrive/leave messages.

### Sense and disposition names

[Prop_Adjuster](#prop_adjuster) and the other adjusters accept `senses` and `disposition` terms made of these names (`AHELP ADJSENSES`, `AHELP ADJDISPOSITION`).

Senses: `cannotsee, canseehidden, canseeinvisible, canseeevil, canseegood, canseesneakers, canseebonus, canseedark, canseeinfrared, cannothear, cannotmove, cannotsmell, cannottaste, cannotspeak, cannotbreathe, canseevictim, canseemetal, cannotthink, cannotwork, cannotautoattack, cannotbecamped, canseeitemshidden`.

Dispositions: `isunseen, ishidden, isinvisible, isevil, isgood, issneaking, isbonus, isdark, isgolem, issleeping, issitting, isflying, isswimming, isglowing, isclimbing, isfalling, islight, isbound, iscloaked, isunsavable, iscataloged, isunattackable, iscustom, isunhelpful`.

## Worked examples

### 1. An elves-only guild hall

Goal: a hall that humans cannot enter (even following an elf), smelling faintly of cedar.

```text
MODIFY ROOM
```

Choose the **Effects** field by its number, then:

```text
Enter an effect to add, -ID to remove, =ID or its number to edit, ?P for the properties that fit (?)
:Prop_ReqEntry
Enter any effect parameters (?)
:"message=The wardens bar your way: elves only." nofollow -race +elf
Prop_ReqEntry added.
:Prop_Smell
Enter any effect parameters (?)
:CHANCE=50 <T-NAME> smells of cedar and beeswax.
Prop_Smell added.
:
```

[Prop_ReqEntry](#prop_reqentry) is "options then a mask": `nofollow` stops followers of an elf, and `-race +elf` is the zapper mask meaning "disallow everyone, allow elves". The quoted `message=` replaces the default refusal. Put `nosneak` in front to stop sneaking past. To apply the same rule to a whole area, put the same property on the area in `MODIFY AREA`. Test with a non-elf character; `PROPCHECK ROOM` lists anything it could not parse.

### 2. A combination-locked vault door that relocks itself

Put on the *exit* (the vault door must be a real door with a lock; see chapter 20 for doors and keys):

```text
Prop_ComboLock   CODE=739 RELOCK=30
Prop_LockRules   PICK=NEVER
```

[Prop_ComboLock](#prop_combolock): `TURN DIAL 2 TO 3` sets one dial, `SET DIALS 7 3 9` sets them all, `ENTER 739` works on a keypad; when the dials read the code the door unlocks and opens on both sides, and after 30 ticks it shuts and locks again. [Prop_LockRules](#prop_lockrules) with `PICK=NEVER` stops thieves picking it. For a code the player must discover, use `CODE=RANDOM DIALS=4 VAR=VAULT` and print the clue with the script variable `$<COMBO VAULT>`.

### 3. A ring that helps against cold, for dwarves only

On a ring item:

```text
Prop_WearAdjuster   con+1 savecol+20 MASK=-Race +Dwarf
```

While worn by a dwarf the ring adds one constitution and 20 to the cold save. A non-dwarf wearing it gets nothing, because the trailing `MASK=` selects who is affected. To make the ring unwearable by others instead, use [Prop_WearZapper](#prop_wearzapper) with `-race +dwarf`.

### 4. Adding properties from a script

JavaScript (as in the passing fixtures `tools/fixtures/fixes/*.js`):

```js
var P = CMClass.getAbility("Prop_ComboLock");
P.setMiscText("CODE=739 RELOCK=30");
door.addNonUninvokableEffect(P);
```

MOBPROG, on whatever the script can name:

```text
MPAFFECT Prop_Adjuster $n str+1
MPAFFECT Prop_Adjuster $n 50TICKS str+1
MPUNAFFECT $n Prop_Adjuster
```

The first line is permanent; the second runs for 50 ticks through `TemporaryAffects`. To turn a property on and off with a condition (night only, once per day) wrap it in [Prop_When](#prop_when).

## Persistence

- **World files.** Every savable property on a room, area, exit, mob or item is written with its host as an `AFF` entry: the class ID and the parameter text. Building with the effects editor and `SAVE` therefore stores the properties with the world. A hand-edited world file works the same way. A class ID that no longer exists is skipped on load with a log error.
- **Campaign saves.** Items and characters carry their properties into a campaign save. A property that keeps running state in its own text (a countdown, a charge, an owner, a switch position, saved exposure markers) saves the current text, so a restored game continues where it was. A property whose class declares itself non-savable (engine-made ones such as [Prop_Playtest](#prop_playtest), [Prop_TweakWatch](#prop_tweakwatch), [Prop_TransitView](#prop_transitview), the item-set tier effects of [Prop_ItemSet](#prop_itemset) and the faction handlers of [Prop_FactionEffects](#prop_factioneffects)) is never written and is rebuilt from its source when needed.
- **Authoring markers.** Once-only flags, discoveries and exposure counters are keyed by the property's `ID` (see [Saved identities](#saved-identities)); keep IDs stable when you edit shipped content.
- **Reload.** Reloading a world resets host objects to the world-file text; campaign state then applies on top as in `state-and-persistence.md`.
- **Edits in a running game.** `-ID` in the effects editor removes a property immediately; changing the text with `=ID` re-parses it (`setMiscText`), which for some properties (those that count or keep a charge) also resets that state.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| The property does not appear in `?P` | The class declares no host type (see its entry) or the host is the wrong kind. Type the ID anyway if the entry says it works on that host. |
| "'X' is not recognized. Try '?'." | A misspelled class ID. IDs are matched case-insensitively but must be the full name, such as `Prop_ReqEntry`. |
| A KEY=value setting is ignored | A key preceded by letters, a `+` or `-` before the `=`, or an unquoted value containing spaces or `:`/`;`. Quote the value. Run `PROPCHECK`. |
| The mask does nothing | The mask belongs after `MASK=` for adjuster-style properties, but is the bare tail of the text for `Prop_ReqEntry` style ones; copy the pattern from the entry's example. |
| Two copies of one property on a host | Only one is used; the editor offers to wrap both in `Prop_MultiEffects`. |
| An item property has no effect | It applies when worn/held/owned/ridden/used as the class name says: `Wear*` when worn or wielded, `Have*` when owned, `Ride*` when ridden, `Use*` when touched or consumed. |
| Entry restriction lets NPCs or followers through | Followers pass unless `nofollow` is given; sneaking passes unless `nosneak`. NPCs are subject to the mask like anyone else. |
| A once-only message repeats after a rename | The property had no explicit `ID=`; add one (and `ALIASES=` for the old identity). |
| Clan or multiplayer wording in help | Inherited text; clans, accounts and player-to-player channels are not part of CoffeeSUD play. See chapter 31 for the single-player model. |

## Audit notes and known mismatches

Recorded while writing this chapter (the full list is in the audit notes for chapter 23):

- 13 properties have no shipped help entry: Prop_AreaBanner, Prop_AreaEntry (mentioned only inside the Scriptable help), Prop_AuthorIdentity, Prop_Broken, Prop_FactionEffects, Prop_JourneyPassenger, Prop_JourneyState, Prop_Playtest, Prop_Purchases, Prop_StoryRecords, Prop_TransitView, Prop_TweakWatch and Prop_Verbs (mentioned inside the Scriptable help). Their entries here come from the code.
- Four Abilities/Misc properties (Dusty, Alerted, GoingHome, Phobias) have no help either; their entries come from the code.
- Where the shipped help names different hosts from the code, the entry shows both. Examples: Prop_AbsorbDamage lists areas and rooms in help but declares only items and mobs; Prop_Adjuster help says "Mobs" while the class accepts all five host kinds.
- Prop_CombatAdjuster, Prop_MOBEmoter, Prop_Undroppable and Prop_Unsellable declare no host type, so the effects editor's `?P` never lists them.
- The Prop_ReqEntry help writes `nofol` for the follower option, but `Prop_ReqEntry.setMiscText` only recognises words that start with `NOFOLLOW`; `nofol` does nothing (Prop_ReqEntry.java line 91).

## See also

- [24-behaviors-and-masks.md](24-behaviors-and-masks.md): behaviours, and the zapper mask language that most `Req*`, `*Zapper` and `MASK=` parameters use.
- [25-abilities-and-effects.md](25-abilities-and-effects.md): timed effects, `GenAbility`, and the difference between a property and a spell.
- [20-areas-rooms-exits.md](20-areas-rooms-exits.md), [21-mobs.md](21-mobs.md), [22-items.md](22-items.md): the editors that hold the Effects field.
- [07-items-crafting.md](07-items-crafting.md): puzzle items built from `Prop_Switch`, `Prop_PressurePlate`, `Prop_ComboLock`.
- [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md): journeys, ports, waypoints and travel time.
- [11-presentation-language.md](11-presentation-language.md): details, senses, lore and language properties in context.
- [state-and-persistence.md](state-and-persistence.md), [tool-reference.md](tool-reference.md), [feature-index.md](feature-index.md).
