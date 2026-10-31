# Crafting, gathering and housing

Crafting and gathering skills, recipes, resources, farming, workstations, player-owned property and home storage. Part of the [builder capabilities audit](README.md).

<a id="cr1"></a>

## CR1. Homes owned by the profile, and pre-owned homes

*Kind:* world option · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Land belongs to a character's name, a spouse, a clan or the follow chain. A party member who does not follow the owner owns nothing (after PARTY REMOVE, after SWITCH, or while the owner rests), and the theft check refuses them, since PLAN 5a set PLAYERKILL to NEVER. A world file cannot ship a home that is already the player's. `isProperlyOwned` and `updateLotWithThisData` wipe any owner who is not an existing player or clan, and clans are not world content.

**Add.** In single-player, every character of the profile (its one PlayerAccount) counts as owner in `doesOwnThisLand`, `doesOwnThisProperty`, `doesHaveWeakPrivilegesWith` and `robberyCheck`. A reserved owner token, `@PROFILE`, in `Prop_*ForSale` and `Prop_PrivateProperty`, which `isProperlyOwned` and `updateLot` treat as always existing. As an option, land the player buys is recorded as `@PROFILE`.

**Example.** "Your cottage" in the tutorial village carries `Prop_RoomForSale('@PROFILE/0')`. Any character the player makes later can enter, build there and use the chest, and nothing counts as theft between them.

**Also merged here, from T15 (Vessels owned by the whole profile, and MPGRANT for quest rewards).** In `doesOwnThisProperty`, any character of the owner's profile (`Party.profileOf`) counts as owner, for vessels, caravans, castles and land titles. A script command `MPGRANT <property item> <who> [NAME=<new name>]` sets the owner, registers the item with the character so it saves, docks it at the nearest port, and renames it through `Boardable.rename` with no prompt, without the duplicate noted in the item on saving vessels. `MPGRANT <item> NOBODY` undoes it.

*See also:* CR6, CR7 (CR6's ISOWNER and MPDEED and CR7's claims use CR1's profile owner and @PROFILE, and MPDEED and T15 (now in CR1)'s MPGRANT both set an owner from a script).

<!-- from: crafting-gathering-housing/cgh-profile-owner, vehicles-travel-instances/vti-profile-ownership -->

<a id="cr2"></a>

## CR2. A world materials file with stable codes

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** New resources come only from `ADDMATERIAL_` (and `REPLACEMATERIAL_`) lines in coffeemud.ini, which is engine config, not world/ content. Codes follow the ini's hash order, and items save MTRAL as a bare number. Adding or removing a material can renumber others and silently change what world items, catalog entries and saves are made of. A resource carries one effect, used when eaten raw, and no description.

**Add.** `world/materials.txt`, one row per resource: NAME, FAMILY, VALUE, FREQUENCY, HARDNESS, BUOYANCY, CATEGORY (FISH, BERRY or WOODY), SMELL, EFFECTS, DESCRIPTION and an optional fixed CODE. Other codes are stable: sorted by name, after the built-ins. World XML writes the resource name next to MTRAL, and load cross-checks the two, so a renumbering repairs itself. The ini route stays as a fallback.

**Example.** Starsteel (METAL, value 300, hardness 6) is mined only in the Fallen Star crater through `ResourceOverride(starsteel)`, and Blacksmithing takes it as METAL. Glowcap (VEGETATION, BERRY) casts Spell_Light when eaten.

*See also:* CR15 (Two world data files for resources (materials vs named variants) that should share one format).

<!-- from: crafting-gathering-housing/cgh-world-materials -->

<a id="cr3"></a>

## CR3. Workstations and tool wear

*Kind:* property and data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The only workstation is a fire. `NEEDFIRE` accepts any burning item, so a hearth with a permanent Burning effect works. components.txt can require a tool (NEARBY, HELD, fate KEPT), but matches only by resource, material or name substring, and a kept tool never wears. Only gathering has a tool bonus (`Prop_ImproveGather`). Only the crafter makes a craft faster, better or higher-level. Their level above the item's and the X-TIME and RUSH expertises shorten it, the QUAL, DURA, LITE and other EnhancedCraftingSkill expertises improve the item, and the ADVN expertise opens higher recipes. No tool, item or room does any of this.

**Add.** (a) Component id `tag:anvil`, matching item tags (CompType TAG). (b) Recipe key `STATION=forge` and a GenCraftSkill field STATION. (c) `Prop_Workstation(TAG=anvil SKILLS=Blacksmithing,Weaponsmithing SPEED=25 PROF=+10 MAXLEVEL=40 QUALITY=+1)` on an item or room, applied when the crafter is in the room with it. (d) Component fate `wear:n`: the tool is kept but loses n uses or condition, and breaks at zero.

**Example.** The home smithy's "dwarven forge" (tags forge and anvil) makes Blacksmithing 25% faster and allows level 30+ recipes. A "travel anvil" satisfies STATION=anvil with no bonus. A smith's hammer loses 1 condition per craft.

*Re-checked against the code after writing: corrected.*

*See also:* I26 (Both let worn items break instead of vanishing (a tool's wear:n fate vs ITEMBREAK=BREAK)); CR17, CR20 (CR17's station quality and CR20's ACTS= stations build on CR3's workstations); CR5 (Both add recipe requirement keys (STATION vs MASK, ROOMMASK and TOOL) read by the same recipe parser).

<!-- from: crafting-gathering-housing/cgh-workstations -->

<a id="cr4"></a>

## CR4. Recipes a character knows

*Kind:* property, script command and script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Extra recipes count only while their GenRecipe book is carried, even one made sticky with `Prop_ItemBinder TO=ACCOUNT`. Sold, they are lost, and the rest of the party cannot use them. Base-file rows are open to anyone of the right level, so "learned from the elven smith" cannot be said. `CoffeeUtensils.addExtRecipes` already reads recipes from effects that implement Recipes, but no Ability does.

**Add.** `Prop_KnownRecipes`, a permanent Ability implementing Recipes, with SKILL= and recipe lines, saved with the character; the existing loop picks it up. A `STUDY <book>` (or `LEARN FROM <book>`) verb copies or consumes a GenRecipe's lines into it. Script command `MPLEARNRECIPE $n <skill> <recipe name|line>`, function `KNOWSRECIPE($n skill name)`, and recipe key `HIDDEN=1` for base rows usable only when known. The profile rule of CR1 can make knowledge party-wide.

**Example.** After the quest "Silver for the Lady" the elven smith runs `mplearnrecipe $n Weaponsmithing moonblade`. The moonblade is known for good and shows in `weaponsmith list`.

*See also:* CR18, CR21 (Ways a character learns recipes: CR18 needs CR4's Prop_KnownRecipes and HIDDEN key, and CR21 turns on the LEARN that CR18 notes data skills lack); P5 (P5's RECIPE reward needs a place to keep known recipes, which CR4's Prop_KnownRecipes is).

<!-- from: crafting-gathering-housing/cgh-known-recipes -->

<a id="cr5"></a>

## CR5. Recipe requirement keys

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** GenCraftSkill's KEY_VALUE_PARMS column knows one requirement, RACEREQUIREMENT; any other key becomes an item stat. Stock skills have no such column. A recipe can be held back in a GenRecipe book, and `NEEDFIRE` asks for a fire. Anything else needs a room script: `CNCLMSG_PROG` on the craft words with `RETURN CANCEL` to refuse, and `EXECMSG_PROG ITEMGENERATED` to give XP or a tattoo.

**Add.** Reserved keys, removed before item stats are set: `MASK=` (zapper on the crafter), `ROOMMASK=` (zapper on the room: locale, room ID, area, a room effect), `TOOL=` (item held or nearby), `FAILMSG=`, `XP=` (on success) and `TATTOO=` (on first success). The recipe list hides recipes whose MASK the crafter fails. Stock skills read the same keys from an optional extra column when present.

**Example.** The Runeblade row has `ROOMMASK="-ROOMID +Deepforge#3" MASK="-TATTOO +RUNE_TRAINED" TOOL="rune chisel" XP=200 FAILMSG="Only the Deepforge burns hot enough."`

*See also:* CR3 (Both add recipe requirement keys (STATION vs MASK, ROOMMASK and TOOL) read by the same recipe parser); CR12 (Both award crafting XP (a recipe XP= key vs CRAFTXP)).

<!-- from: data-content/dc-recipe-requirements -->

<a id="cr6"></a>

## CR6. Script functions and commands for property

*Kind:* script function and script command · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Scripts cannot simply ask who owns a room or give a deed as a reward. `INROOM` has ANYPROPERTY and MYPROPERTY, but MYPROPERTY tests the scripted mob, not $n. `INROOM(BEACON)` never matches: the start room it fetches is thrown away. A deed changes hands by GIVE only from its owner or a shop. A JavaScript `<SCRIPT>` block can call `CMLib.law().getLandTitle(room)` and set the owner, as `resources/progs/changeRoomOwned.js` does, but it needs JSCRIPTS approval.

**Add.** Functions `OWNER(room|$n)` (owner name or empty), `ISOWNER($n [room])`, honouring the profile rule of CR1, and `LANDTITLE(room)` (lot ID and price). Commands `MPDEED <room|area|lotID> <$n|@PROFILE> [price]`, which sets the owner, updates the title and colours the room, and `MPUNDEED <room>`. Fix the BEACON branch.

**Example.** When the ghost of the haunted farmhouse is laid to rest, the reeve runs `mpdeed "Farmhouse#3" $n` and says "The deed is yours." A gardener's GREET_PROG checks `ISOWNER($n)` before offering to tend the plot.

*Note.* `JSCRIPTS=2` already runs every JavaScript block without approval (see *Already possible* in the README), so the JavaScript route mentioned above needs no approval; what this item adds is the convenience of doing it without JavaScript.

*See also:* SC14 (Both fix INROOM(BEACON) throwing away the start room); CR1, CR7 (CR6's ISOWNER and MPDEED and CR7's claims use CR1's profile owner and @PROFILE, and MPDEED and T15 (now in CR1)'s MPGRANT both set an owner from a script).

<!-- from: crafting-gathering-housing/cgh-script-property -->

<a id="cr7"></a>

## CR7. Building without a deed where the world allows

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Building skills need land the builder owns (`doesOwnThisLand`, also the grid parent or the room in the build direction) or the CMDROOMS security flag. Nothing grants build rights without a title: Thief_Squatting only claims existing titles, and the recipe's DAT_BUILDERMASK limits who builds, not where. A camp or a cave home needs a `Prop_LotsForSale` or `Prop_RoomForSale` at price 0 placed first, and a real-estate shopkeeper to sell it.

**Add.** `Prop_Homestead` on rooms or areas: `MASK=` (zapper on the builder), `SKILLS=Construction,Masonry,Excavation`, `CLAIM=true` (the first build creates a Prop_LotsForSale-style title owned by the builder or `@PROFILE`), `MAXROOMS=n`, `PRICE=0` and `NOTAX`. BuildingSkill's `canBuild` also accepts a matching homestead. Expanding the lot then follows the existing rules.

**Example.** On the north ridge of The Wilds a ranger may build a lean-to with roof, fence and gate, at most 3 rooms, and it becomes the party's camp.

*See also:* CR1, CR6 (CR6's ISOWNER and MPDEED and CR7's claims use CR1's profile owner and @PROFILE, and MPDEED and T15 (now in CR1)'s MPGRANT both set an owner from a script).

<!-- from: crafting-gathering-housing/cgh-homestead -->

<a id="cr8"></a>

## CR8. Building skills as data

*Kind:* builder tool and data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Crafting, gathering and wright skills can be data (GenCraftSkill, GenGatheringSkill, GenWrightSkill), but building skills cannot; CREATE knows only CRAFTSKILL, WRIGHTSKILL and GATHERSKILL. Construction differs from a new trade only in name, trigger, recipe file, main material and sound. A JavaScript class extending Construction could point at another file, but it loads only through LOAD ABILITY or the ini, is not kept in world/custom.cmare, and is untested in the fork.

**Add.** `GenBuildingSkill`, extending BuildingSkill, with fields NAME, HELP, TRIGSTR, FILENAME, MATLIST, MAINRESOURCE (word and material), VERB, SOUND and CANSIT, stored like GenCraftSkill. Builder commands `CREATE`, `MODIFY` and `DESTROY BUILDSKILL`, with a CMGenEditor page. The alternative is to have world/ load .js classes, so the script route becomes content.

**Example.** "Icecarving" for the Frost Clans, from icecarving.txt: an igloo roof that makes the room an ice room, ice walls, a snow-block door and an ice-shelf platform, all from ICE and SNOW resources.

*See also:* JS5 (CR8 names world-loaded JS classes (JS5) as the alternative to GenBuildingSkill).

<!-- from: crafting-gathering-housing/cgh-genbuildskill -->

<a id="cr9"></a>

## CR9. Weighted and masked finds for data gathering skills

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A data gathering skill picks evenly among its ITEMXML items (repeating an entry weights it), and ROOMMASK and PLAYMASK gate only the whole skill. Time or season variants can be had through ResourceOverride named entries with MOBMASK=, at a fixed chance. `Prop_ImproveGather` (AMT=, SKILLS=, MASK=) multiplies yield and can test level. YIELDFORMULA is parsed without variables, so yield cannot grow with level or proficiency.

**Add.** Each ITEMXML entry takes an optional FREQ (weight), MASK (zapper on room and gatherer: +SEASONS, +HOUR, +WEATHER, +AREA, +TAGS) and YIELD. YIELDFORMULA gets variables through the `CMath.parseIntExpression` overload that takes them: @x1 gatherer level, @x2 proficiency, @x3 expertise bonus, @x4 the room's remaining yield. NOTHINGFREQ weights a find of nothing.

**Example.** "Beachcombing": driftwood (weight 60), shells (30), amber (5, only when +WEATHER is stormy) and a "message in a bottle" catalog item (1). Yield is `1+@x2/40`.

*See also:* CR10 (Both add a zapper mask to gathering finds (data skill ITEMXML vs the hunting and fishing tables)).

<!-- from: crafting-gathering-housing/cgh-gather-table -->

<a id="cr10"></a>

## CR10. A mask column for hunting and fishing tables

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** hunting.txt rows match only the room's resource and a domain, `*`, or a locale class ID; fishing.txt rows match only the resource. Every row applies to the whole world, so area, seasonal or night-only game cannot be set. For fish, ResourceOverride with a named entry and MOBMASK can swap the catch for a variant, but it keeps the base fish's value.

**Add.** An optional last column, MASK: a zapper mask tested against the room and the hunter or fisher (+AREA, +SEASONS, +HOUR, +WEATHER, +TAGS on the room). Rows without it behave as now. A masked fishing row carries its own value, so a fish can be worth more in one area. The same column applies to the Herbology, Floristry and Mycology lists where they pick names.

**Example.** In Frostmere, in winter only, hunting fur finds snow hares (a catalog mob). In the Bog of Lights at night, fishing finds lantern eels worth 12 gold.

*See also:* CR9 (Both add a zapper mask to gathering finds (data skill ITEMXML vs the hunting and fishing tables)).

<!-- from: crafting-gathering-housing/cgh-masked-tables -->

<a id="cr11"></a>

## CR11. Seasonal room resources

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A room's resource is fixed by its locale class or by ResourceOverride, all year. Nothing in gathering reads the calendar. A room script can do it: a DAY_PROG testing `ISSEASON(autumn)` runs `MPUNBEHAVE $d ResourceOverride`, then `MPBEHAVE $d ResourceOverride apples` (or $a for the area). It must unbehave first, since MPBEHAVE does not re-parm, and re-apply after a restore, since behaviours added in play are not saved in world.xml.

**Add.** ResourceOverride parameters `SEASON=SPRING,SUMMER`, `MONTH=` and `HOUR=`, or a list form `SPRING=flowers,herbs SUMMER=berries AUTUMN=apples WINTER=none` that swaps the room's or area's resource as the season turns, with an optional message on the change. `none`, already accepted, yields nothing.

**Example.** The Vale orchard gives blossoms for Floristry in spring, apples in autumn and nothing in winter, so the party stores fruit in the home pantry (`Prop_IceBox`).

<!-- from: crafting-gathering-housing/cgh-seasonal-resources -->

<a id="cr12"></a>

## CR12. Crafting and gathering XP for every class

*Kind:* world option and property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Every craft computes an XP value (`setMsgXPValue`), but only the Artisan class awards it. Two data routes exist. In achievements.ini, EVENT="CRAFTING" with ABILITYID, ITEMMASK, NUM and REWARDS="n XP" (ACH_FIRSTCRAFTOR gives 50) can be chained as milestones, and SKILLUSE covers gathering. A script, `EXECMSG_PROG ITEMGENERATED all` with `MPEXP $n 10`, can sit on a room, a carried item, or the character through `MPSCRIPT $n SAVABLE`.

**Add.** World options `CRAFTXP=` (percent of the computed value, for all classes; default 0, so nothing changes) and `GATHERXP=n` per gathered unit. `Prop_CraftXP(PCT=50 SKILLS=... MASK=...)` on a character, item or area, such as a guild hall that teaches. XP goes through `postExperience`, so `Prop_ModExperience` and achievements apply.

**Example.** In a cosy crafting world, the fighter who forges her own armour and the cook who feeds the party earn some XP for it.

*See also:* CR5 (Both award crafting XP (a recipe XP= key vs CRAFTXP)).

<!-- from: crafting-gathering-housing/cgh-craft-xp -->

<a id="cr13"></a>

## CR13. A way home for the party

*Kind:* property, command and world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Recall goes to each character's start room, and only a script changes it: `MPBEACON <room> all` in a bed or room script (`EXECMSG_PROG SLEEP all`), which a crafted bed can carry through the recipe's spell column. A home bought or built has no tie to recall by itself. Skill_Recall takes along only monster followers, so party members are left behind. The existing `+ISHOME` mask means the home area, but `-LOCATION +OWNED` and `-LOCATION +PRIV` already test for property the character owns or has privileges in.

**Add.** `Prop_HomeBeacon` on rooms, which an option can add to owned property: SLEEP or `HOME SET` there sets every party member's start room, if they have privileges there. A `HOME` command recalls the whole party there, out of combat, over N turns (world option `HOMERECALL=turns`, 0 to disable). No new zapper mask is needed: `-LOCATION +OWNED` and `-LOCATION +PRIV` already cover owned property.

**Example.** After buying the Millhouse the party sleeps there once. From then on Recall or HOME brings everyone back to it after a dungeon.

*Re-checked against the code after writing: corrected.*

*See also:* C7 (Both set a per-character return point for the party (a respawn checkpoint vs a home start room)).

<!-- from: crafting-gathering-housing/cgh-home -->

<a id="cr14"></a>

## CR14. Resource depletion and regrowth on game time, saved

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Each harvest in a room cuts its yield by 20%, and it recovers after a fixed 30 minutes. The record's expiry uses game time, but the map holding it (`ExpireHashMap`) clears by the computer's clock, so in a paused or turn-based game it recovers in real minutes. Depletion is not saved, and the count and regrowth cannot be set. Conditional yield already exists: `Prop_ImproveGather` with `AMT=` (0 removes yield) and a `MASK=` on season, weather or hour.

**Add.** `Prop_Yield` on rooms and areas: `HARVESTS=5 DROP=20% REGROW=2 DAYS` (game time) and `EMPTYMSG="The bushes are bare."`. Regrowth runs on game time everywhere, and depletion records are saved in world.xml. Seasons need nothing new here: Prop_ImproveGather covers yield, and CR11 the resource itself.

**Example.** A mushroom glade has `Prop_ImproveGather` with AMT=0 outside autumn, and `Prop_Yield(HARVESTS=5 REGROW=7 DAYS)`. After five autumn harvests it stays bare for a week, even across a save.

<!-- from: world-systems/ws-resource-ecology -->

<a id="cr15"></a>

## CR15. Named resource variants as data

*Kind:* data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A variant such as "moonpetal" herbs is only a name. `RawCMaterial.makeItemResource` sets value and effects from the resource code and leaves the description empty; the subtype changes only the name (`adjustResourceName`). ResourceOverride's `name(RESOURCE subtype)` entries and `Prop_CommonTwister` only rename, and fishing.txt sets a value for fish only. A valuable or magical variant needs a new material code, or a custom GenGatheringSkill whose ITEMXML holds the exact item.

**Add.** `world/resources.txt` rows: SUBTYPE, BASE_RESOURCE, DISPLAY_NAME, VALUE, WEIGHT_PER_UNIT, DESCRIPTION, SMELL, EFFECTS (properties or spells on the raw item) and NOURISHMENT. `makeItemResource` and `adjustResourceName` apply the row whenever that subtype is made: gathering, ResourceOverride, Butchering, the fishing and hunting tables, bundling. Components and recipes already accept `herbs(moonpetal)`.

**Example.** Moonpetal (HERBS, value 40, glows, Spell_Light when eaten) grows only in the Silver Glade through `ResourceOverride(moonpetal(HERBS moonpetal))`. An Herbalism recipe asks for `herbs(moonpetal)`.

*See also:* CR2 (Two world data files for resources (materials vs named variants) that should share one format).

<!-- from: crafting-gathering-housing/cgh-subtype-registry -->

<a id="cr16"></a>

## CR16. Crop rules as data

*Kind:* property and data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A crop grows in a fixed 50 ticks after planting (about 3 real minutes, or WAIT 50 in turn-based time). It needs `canSeeTheSun` and an outdoor plains, hills, woods, jungle or swamp room (or a DIRT resource), and yields 1d7 times a base amount, plus compost. Gardening uses the same rooms, and MasterFarming only shortens planting. No season, per-crop time, cellar farm or watering exists; the only workaround is a room `CNCLMSG_PROG` that refuses planting when `ISSEASON` fails.

**Add.** `Prop_CropRules` on a room, area or seed item, plus optional rows in a world `crops.txt` keyed by resource or subtype: `GROWHOURS=` (game hours), `SEASONS=`, `NEEDSUN=true|false`, `DOMAINS=`, `WATER=n` (a water source or an Irrigation-built room adjacent), `YIELD=` (dice) and `FAILMSG=`. Farming and Gardening read it for duration, season, sun and yield.

**Example.** Winter wheat planted in autumn takes 10 game days. Glowcap mushrooms grow in the dark cellar farm under the player's house (`NEEDSUN=false DOMAINS=CAVE`).

<!-- from: crafting-gathering-housing/cgh-crop-rules -->

<a id="cr17"></a>

## CR17. Rolled quality tiers for crafted items

*Kind:* world option · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** A craft either works or makes a ruined item: one proficiency check, then `dropAWinner` or `dropALoser`. Quality words exist only as stages the crafter learns and picks, such as the EnhancedCraftingSkill expertise stages and MasterLeatherWorking's Designer to Legendary. Nothing rolls a tier. Prop_CommonTwister can rename output on ITEMGENERATED, but it renames every item the same way and leaves stats alone.

**Add.** A world list `CRAFTQUALITY`, in lists.ini or a world file. Each tier has a NAME prefix and a CHANCE formula built from proficiency, level margin and station or tool quality. It also has multipliers for value, armour or damage and uses, a level change, and optional effects. GenCraftSkill gets `QUALITY=on/off`. A recipe key `NOQUALITY=1` opts a recipe out. The tier name goes into the item name and the maker's brand.

**Example.** Smithing yields "a crude iron sword" (-10% damage), "an iron sword", "a fine iron sword" (+1 level, +10% value) or, rarely, "a masterwork iron sword" (+3 levels).

*See also:* CR3, CR20 (CR17's station quality and CR20's ACTS= stations build on CR3's workstations).

<!-- from: crafting-gathering-housing/cgh-quality-tiers -->

<a id="cr18"></a>

## CR18. EXPERIMENT: discover recipes by trying materials

*Kind:* skill subcommand, recipe keys and world option · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** The only way to learn a recipe in play is LEARN (`doLearnRecipe`). It takes apart an unbranded item no higher than your level into a recipe book with blank pages. It needs `supportsDeconstruction`, which is false for Cooking and for GenCraftSkill data skills. Nothing finds a recipe by trying materials together.

**Add.** A subcommand `<skill> EXPERIMENT <material> [<material>...]`. It compares the materials and amounts offered with recipes marked HIDDEN that the character could make at their level. A match teaches the recipe and gives XP. The recipe goes into a new Prop_KnownRecipes on the character; `addExtRecipes` already reads recipes from a character's effects. A near miss shows the recipe's optional HINT. A miss uses up part of the materials. A world option limits experiments per game day.

**Example.** A cook tries honey, apples and mead and discovers "spiced cider". A near miss says "It needs something warming...".

*See also:* CR4, CR21 (Ways a character learns recipes: CR18 needs CR4's Prop_KnownRecipes and HIDDEN key, and CR21 turns on the LEARN that CR18 notes data skills lack).

<!-- from: crafting-gathering-housing/cgh-experiment -->

<a id="cr19"></a>

## CR19. Salvage and dismantle yields as data

*Kind:* property, skill subcommand and world option · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Scrapping gives a fifth of an item's weight in its one material. It refuses flesh, liquid, paper, energy, gas, vegetation and enchanted items. Mobs are covered: a GenRace's NUMRSC, GETRSCID and GETRSCPARM, or ResourceOverride on the mob, set full Butchering yields. ResourceOverride on a room or area, with Scrapping in its parameters, can swap the scrap for a named variant at a chance. None of these gives several outputs, catalog items or rules per item.

**Add.** Prop_SalvageYield on items or catalog entries: `SKILL=Scrapping` (or any skill ID), `YIELD="brass:4, gear(clockwork):1@25%, catalog:soul gear@5%"`, `XP=n`, `DESTROY=true`. Scrapping uses it when it is present. Crafting skills also get a DISMANTLE subcommand. It finds the item's recipe and returns a share of its materials, set by a world option `DISMANTLEPCT=50` and adjusted by proficiency.

**Example.** Scrapping the "ruined automaton" in the Old Foundry yields brass, a mainspring, and a 25% chance of the soul gear a quest needs.

<!-- from: crafting-gathering-housing/cgh-salvage-yield -->

<a id="cr20"></a>

## CR20. Furniture that changes its room: Prop_Furnishing

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Some furniture already works. FasterRecovery on a placed, non-gettable item helps everyone in the room, and on a bed or chair (a Rideable) it helps whoever is on it. Prop_RideAdjuster, Prop_RideSpellCast and Prop_RideEnabler affect whoever sits or sleeps on it. A hearth with a permanent Burning counts as fire for Cooking. A recipe's spell column can put any property or behaviour on what it makes. Three things are missing. An item cannot give room-wide effects, because Prop_HereAdjuster, Prop_HereSpellCast and Prop_RoomLit go only on rooms or areas. There is no comfort total. An item cannot act as a station.

**Add.** Prop_Furnishing on items. `ROOMEFFECT=<ability(parms)>` and `ROOMBEHAVIOR=<behavior(parms)>` apply to the room while the item is decorated or placed in the room, or lies on the floor of an owned room. They stop when it leaves. `COMFORT=n` adds to a room total that masks and scripts can read. `ACTS=fire|forge|anvil` makes it count as a station (see the workstations item).

**Example.** A crafted brazier with `ROOMEFFECT=Prop_RoomLit` lights the cellar. A trophy rack adds comfort. At comfort 5 the house's resting bonus rises.

*See also:* CR3, CR17 (CR17's station quality and CR20's ACTS= stations build on CR3's workstations).

<!-- from: crafting-gathering-housing/cgh-furnishings -->

<a id="cr21"></a>

## CR21. LEARN for data crafting skills (GenCraftSkill)

*Kind:* data file · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** Data crafting skills (GenCraftSkill) cannot use LEARN. Recognising items already works: `GenCraftSkill.mayICraft` checks the supported resource and `mayBeCrafted`. It then matches the item's name against each recipe's final name, with % as a wildcard. Two things block LEARN. `supportsDeconstruction()` is hard-coded false. `GenCraftSkill.unInvoke` has no LEARNING branch that calls `deconstructRecipeInto`, as Blacksmithing's does.

**Add.** A GenCraftSkill field `CANLEARN` that turns `supportsDeconstruction` on. The missing LEARNING branch in `unInvoke`. An optional `LEARNMASK`, an item zapper mask (class, material, +TAGS), that narrows which items the skill will learn from.

**Example.** A custom Runecarving skill learns rune designs by taking apart rune stones found in barrows into a blank recipe book, with `LEARNMASK "+TAGS rune"`.

*See also:* CR4, CR18 (Ways a character learns recipes: CR18 needs CR4's Prop_KnownRecipes and HIDDEN key, and CR21 turns on the LEARN that CR18 notes data skills lack).

<!-- from: crafting-gathering-housing/cgh-gencraft-learn -->

<a id="cr22"></a>

## CR22. Linked storage chests for the profile: Prop_SharedStash

*Kind:* property · *Value:* low · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#cr22).
