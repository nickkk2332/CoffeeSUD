# Generators and random content

Random area and room generation, generated content in scripts, loot tables, affixes, theme packs and name generators. Part of the [builder capabilities audit](README.md).

<a id="g1"></a>

## G1. Seeds for repeatable generation

*Kind:* world option · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** `GENERATE` takes only `NOSAVE`, `FROM` and `VAR=VALUE`, and its result depends on every roll made before it. The only seed is the global `RANDOMSEED`. A builder cannot keep a dungeon they liked or have the engine rebuild it exactly. A `<SCRIPT>` block can reach `CMath.seedRand`, but that is clumsy. Even with one seed, loot varies: the metacraft search stops after one second of real time, and crafted recipe sets are cached (`SYSTEM_ITEMGEN_*`).

**Add.** `SEED=<n>` on `GENERATE`, a `SEED` variable for `StdAutoGenInstance` and `AutoGrid`, and a `seed="..."` attribute on `AREA`, `ROOM`, `MOB` and `ITEM` that takes math, so `seed="$[$GAME_DAY]"` gives a dungeon of the day. A seeded run saves the shared generator's state, seeds it, builds, then restores it. The seed is stored on the area, exposed as `$GEN_SEED`, and printed when none was given. Metacraft gets a fixed number of tries, and seeded runs skip the item cache (the `DISABLE=ITEMGENCACHE` switch already exists).

**Example.** The owner generates 'The Drowned Crypt' with seeds 1 to 20, keeps 13 for its layout, and writes `SEED=13` into the AutoGen area so every new game has that crypt.

**Also merged here, from T13 (Generated instances with a fixed or saved seed).** An autogen variable `SEED=<number>|GAME|ENTRY`, with a generator reseeded around the build of each copy. `GAME` uses one seed for the whole save, stored in world.xml, so the dungeon is the same each visit. `ENTRY` rolls a seed per entry and saves it with the instance, so a restore rebuilds it; this needs instances to be saved, a separate item. `PERSIST=true` would do what `GENERATE AREA` does, once per save in play.

*See also:* G4 (G4's SEED option needs G1's seeds); G14 (G14's SEED=daily|weekly uses G1's seeds); S18 (S18 regenerates generated areas from their seed on restore, and S18 and T13 (now in G1) both propose PERSIST=true for one instance per save); S19 (T13 (now in G1)'s ENTRY seed needs S19's saved instances, as T13 (now in G1) says).

<!-- from: generators/gen-seed, vehicles-travel-instances/vti-autogen-seed -->

<a id="g2"></a>

## G2. Fix RandomQuests hanging or never generating

*Kind:* behaviour · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** `RandomQuests` caps its workers at `availableProcessors()/4` using integer division. That is 0 on one to three cores, so every attempt returns at once. In INLINE mode, which `Prop_QuestGiver QUEST="GENERATE:..."` always uses, `tick()` loops `while(myQuests.size()<numQuests) generator.run()` and never checks the disable flag. The game thread hangs on such machines. It also hangs on any machine when generation fails for good: file not found, undefined tag, unmet `REQUIRES`, attempts used up. The gate does nothing in the fork, because `ServiceEngine.executeRunnable` already queues work on the one game thread.

**Add.** Drop the processor gate. Generate on the game thread with a set number of tries per tick (`MAXATTEMPTS`), end the inline loop when the disable flag is set, and after repeated failures disable the behaviour with a log line. Add a smoke test that generates one quest through `Prop_QuestGiver GENERATE:`.

**Example.** A notice board with `Prop_QuestGiver QUEST="GENERATE:template=normal"` gives out a quest on the owner's two-core laptop instead of freezing the game when picked up.

*See also:* Q14, S8 (Q14's quest board needs G2's RandomQuests fix and S8's saved generated quests).

<!-- from: generators/randquests-cpu-fix -->

<a id="g3"></a>

## G3. Depth and position variables for generated rooms

*Kind:* generator · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** Room templates see a room's node type, run, exits and flags (`ROOMTAG_NODETYPE`, `NODERUN`, `NODEEXITS`, `NODEFLAGS`). They do not see its distance from the entrance or its coordinates, though `LayoutNode.coord()` exists. The area has only `AREA_NUMLEAFS` and `AREA_SIZE`. Leaf groups are built first, in random order, so a boss room gated by a `__boss<1` counter lands in whichever leaf comes first, often next to the entrance.

**Add.** A breadth-first pass from the entry node over the node list that `layOutRooms` already holds. Each room gets `ROOMTAG_DEPTH`, `ROOMTAG_DEPTHPCT` (0 to 100), `ROOMTAG_ISDEEPEST`, `ROOMTAG_INDEX`, `ROOMTAG_X` and `ROOMTAG_Y`. The area gets `AREA_MAXDEPTH` and `AREA_NUMROOMS`. An `AREA` attribute `order="depth|reverse-depth|random"` sets the order in which room groups are built.

**Example.** `<ROOM condition="$roomtag_isdeepest='true' and $__boss<1" define="__boss+=1">` puts the ogre chieftain's hall at the far end, and mobs with `level="$level_low+$roomtag_depthpct/10"` get harder deeper in.

*See also:* G9, G15, ST20 (G9, G15 and ST20's GENVAR use G3's depth tags (ROOMTAG_DEPTH, AREA_MAXDEPTH)).

<!-- from: generators/layout-depth-tags -->

<a id="g4"></a>

## G4. GENERATE PREVIEW and GENLAYOUT: see before you build

*Kind:* builder tool · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** The only way to see what a template or layout makes is to generate it for real and `DESTROY` the area afterwards. In builder mode `GENERATE NOSAVE` does not protect the world files. It skips only the database writes, and `WorldFiles.exportAll` writes every live area to `world/areas` on `SAVE` and on quit. The ASCII layout drawer, `LayoutTester.draw`, is a developer `main()` that prints to the console.

**Add.** `GENLAYOUT <layout> <size> [dir] [SEED=n] [LEVELS=n]` prints the ASCII map with node types and a count per type, reusing `LayoutTester.draw`. `GENERATE PREVIEW <type> <id> [FROM file] [vars] [SEED=n]` builds in a scratch area, prints the room list (title, node type, mobs, items, exits) and the seed, then destroys the scratch area. `KEEP` then runs the real `GENERATE` with the same seed. `SEED` needs G1, and `LEVELS` needs G16.

**Example.** The owner runs `GENLAYOUT SPOTTEDMESH 40 SEED=9` a few times to pick a shape. Then `GENERATE PREVIEW AREA swamp_dungeon theme=swamp level_range=8 SEED=9`, checks the mob levels, and types `KEEP`.

*See also:* G1 (G4's SEED option needs G1's seeds); G16 (G4's LEVELS option needs G16's multi-level layouts); B12 (Both preview generated content in a throwaway scratch area).

<!-- from: generators/gen-preview -->

<a id="g5"></a>

## G5. MPGENERATE command and GENSTRING function for MOBPROG

*Kind:* script command and script function · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Scripts generate only through `FROMGENFILE` on `MPOLOAD`, `MPOLOADROOM`, `MPMLOAD` and `MPMLOADSHOP`. It takes `INTO <container>` and sets `$b`. Results are cached per file, tag and parameters, so the same call always gives the same roll. A throwaway `nonce=$%RANDNUM(1000000)%` forces a new roll, but each value leaves a cache entry behind. Scripts cannot generate rooms, areas or text, and they pass in no context variables. Quests come only from `MPBEHAVE RandomQuests` or `Prop_QuestGiver` (G2).

**Add.** `MPGENERATE ITEM <file> <id> [vars] [INTO <container>|TO <mob>|HERE]`, `MPGENERATE MOB <file> <id> [vars] [HERE|AT <room>]`, `MPGENERATE ROOM <file> <id> <dir>`, `MPGENERATE AREA <file> <id> <dir> [SEED=n]` and `MPGENERATE QUEST <file> <id> [vars]`. Each call is a fresh roll and sets `$b`. A function `GENSTRING(<file> <id> [vars])` returns generated text. Every call defines `$PLAYER_LEVEL`, `$PARTY_SIZE`, `$AREA_LEVEL`, `$AREA_THEME`, `$HOUR`, `$SEASON` and `$WEATHER`. `FROMGENFILE` stays and accepts a `FRESH` word to skip the cache.

**Example.** An iron chest's `OPEN_PROG` runs `MPGENERATE ITEM randareas/loot.xml chest_loot level_range=$<$n.level> INTO $i` once per reset. A tavern drunk's `SPEECH_PROG` says `GENSTRING(randareas/rumors.xml rumor area=$a)`.

*See also:* G11 (The same fresh-roll generation calls, for MOBPROG (MPGENERATE, GENSTRING) and for JavaScript (gen.item, gen.string and so on)).

<!-- from: generators/mp-generate -->

<a id="g6"></a>

## G6. Loot tables: Prop_LootTable on mobs and containers

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `RandomItems` picks evenly from a static list. `ItemGenerator` weights all craftables by value, and its loop starts at index 2, so it never picks the first two. That is a bug worth fixing anyway. The catalog's `DROP` and `LIVE` rules place one item each. A weighted `loot.xml` can be written today with `select="pick-n"`, `PICKWEIGHT`, `condition`, `class="catalog"` and metacraft. Rolling it on an event is the weak part: a `DEATH_PROG` running `MPOLOAD FROMGENFILE` gets the cached roll every time (G5).

**Add.** `Prop_LootTable` with `FILE=<xml> ID=<tag> ROLLS=<math, e.g. 1?3> ON=DEATH|OPEN|SPAWN|RESET CHANCE=<pct> GOLD=<math>`. Each roll is fresh, with `$mob_level`, `$area_level`, `$theme` and `$player_level` set. Entries with `class="catalog"` keep DEVPLAN item 1's caps and once-ever rules. A container rolls once until its contents reset. It uses the same state as DEVPLAN item 1's planned `CONTAINER` on-opening rule, not a second copy. Ship a sample `loot.xml` with common, uncommon and rare tiers.

**Example.** Goblins in the Warrens carry `Prop_LootTable FILE=randareas/loot.xml ID=goblin ROLLS=0?2 ON=DEATH`. They drop mostly copper and crude daggers, a war-horn 3% of the time, and 0.5% of the time the catalog item 'Fang of Grishnak', capped by the catalog.

*Note.* Its roll on opening a container is the `CONTAINER` rule already planned in `DEVPLAN.md` item 1. Build it on that item, sharing its caps and once-ever rules, not as a second container rule.

*See also:* G10 (G10's affixes are rolled by G6's loot tables).

<!-- from: generators/loot-tables -->

<a id="g7"></a>

## G7. Guaranteed placements and reachable keys (ENSURE)

*Kind:* generator · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Templates place keys by chance, as `lair.xml` does with `validate="1?10=1 or (1?2=1 and $__storekey<2)"`. So a lair can have a locked storeroom with no key, or a key behind the door it opens. `postProcess` only re-runs values that could not be resolved earlier. Nothing checks the built area. Two parts exist to build on: the area-wide `__` counters and the rooms held on `LAYOUT_NODES`.

**Add.** An `<ENSURE>` child of `AREA` that runs after layout and fill, for example `<ENSURE var="__storekey" min="1" fallback="store_key" where="reachable-without:keyname=$areaname storekey" in="mob|room|container"/>`. If the counter is short, it builds the fallback tag. It then puts it in a room reachable from the entrance without passing a door locked to that key, found by walking the built rooms. `max="n"` removes extras, and `<ENSURE room="boss_room" count="1"/>` requires a room.

**Example.** The prison theme always has one jail key outside the cell block, on a guard or in the warden's desk, never inside a locked cell.

<!-- from: generators/gen-ensure -->

<a id="g8"></a>

## G8. Hand-drawn MAP layout: drawn shape, generated content

*Kind:* generator · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** The layouts are `APARTMENT`, `BOXCITY`, `BOXCITYSQUARE`, `BRANCH`, `CROSS`, `GRIDCITY`, `MAZE`, `MESH`, `SPOTTEDMESH` and `TREE`. All are procedural, and none reads a drawn map. A builder who wants a set shape, such as a keep with a courtyard or a cross-shaped temple, must dig every room by hand or take a random shape. A JavaScript layout class could parse a file, but that is programming, not content.

**Add.** `layout="MAP"` with an inline `<MAP>` child or a `MAPFILE="world/maps/keep.txt"` attribute. The map is a character grid: `.` is street, `o` interior, `L` leaf, `@` the entrance, `-` and `|` links, `+` a door, `^` and `v` stairs. Other glyphs map to room templates with `<GLYPH char="B" room="boss_room"/>`. Nodes get the usual `ROOMTAG_*` tags, so the existing theme templates fill them.

**Example.** The owner draws the 25-room Abbey of St. Corwin in a text file, maps `C` to the chapel template, and lets the forest-ruins theme dress the rest. Re-rolling changes text and mobs but keeps the floor plan.

<!-- from: generators/layout-map -->

<a id="g9"></a>

## G9. GENERATE FILL: theme templates on hand-built rooms

*Kind:* builder tool · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `GENERATE ROOM` always makes a new room (`buildRoom`) in an empty direction, and node tags come only from a layout. A builder who digs the map by hand with `DIG`, `LINK` or `CREATE ROOM` cannot have a theme write text, mobs and items into those rooms. The workaround goes room by room: `GENERATE MOB` and `GENERATE ITEM` in each room, and `GENERATE STRING` output pasted in with `MODIFY ROOM`.

**Add.** `GENERATE FILL <roomtemplate_id> [FROM file] [vars] ROOM|AREA [KEEPTEXT] [SEED=n]`. For each target room it works out `ROOMTAG_NODETYPE`, `NODERUN`, `NODEEXITS`, `NODEFLAGS` and the neighbour tags from the room's real exits, and the depth from the area entrance (G3). It then runs the `ROOM` template on the existing room. It sets the title and description, unless `KEEPTEXT` is given or the room is marked `GENLOCK` (G13), and adds mobs, items, affects and behaviours. Exits are left alone.

**Example.** The owner digs a 30-room forest by hand to match their world map and runs `GENERATE FILL forest_rooms FROM randareas/forest.xml AREA level_range=5`. Then they edit the few rooms the story needs.

*See also:* G3, G15, ST20 (G9, G15 and ST20's GENVAR use G3's depth tags (ROOMTAG_DEPTH, AREA_MAXDEPTH)); G13, G14 (G14 needs G13's GENSOURCE, and G9 and G14 keep G13's GENLOCK rooms).

<!-- from: generators/gen-fill -->

<a id="g10"></a>

## G10. Data-driven affixes for random enchantment

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `TimsLibrary.enchant` is called by `ItemGenerator ENCHANTED=%` and `Thief_Digsite`. It only bumps the plus or minus, adds a curse, or adds `Prop_FightSpellCast` or `Prop_WearSpellCast` with random spells (ammunition gets a `Prop_WearAdjuster` attack and damage bonus instead). A template can build 'of the Bear' by hand, using `AFFECT` children picked by `PICKWEIGHT` and `condition` plus a prefix `STRING` used in the name. But each template has to repeat this, and nothing ties an affix to the item's level or value: `enchant()` raises the level only for added spells and ammunition, and a template can only set the whole item's `LEVEL`, `VALUE` or `ITEM_POWERBONUS`.

**Add.** An affix file, `world/affixes.xml` or `resources/randareas/affixes.xml`. Each entry looks like `<AFFIX id name="Flaming" pos="prefix|suffix" kinds="weapon,armor,ammo" level="5-30" pickweight="10" cursed="false">` and holds `AFFECT` tags in the percolator language. `enchant()` uses the file when it exists. So do `ItemGenerator ENCHANTED=%`, `RandomItems`, `Prop_LootTable` (item G6) and a new `ITEM` template attribute, `AFFIXES="0?2"`. The item's name gets the prefix and suffix, and its level and value rise by the affix's level.

**Example.** Goblin loot can roll 'a Rusty Short Sword of Bleeding', a rare result for levels 4 to 12, from the owner's 30 hand-written affixes.

*Re-checked against the code after writing: corrected.*

*See also:* G6 (G10's affixes are rolled by G6's loot tables).

<!-- from: generators/affix-tables -->

<a id="g11"></a>

## G11. JavaScript generator helpers

*Kind:* JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** A `<SCRIPT>` block sees `host`, `source`, `target`, `monster`, `item`, `message`, `getVar`, `setVar` and a few helpers. It can reach the percolator through `Packages.com.planet_ink.coffee_mud.core.CMLib`, but must repeat the whole sequence: parse the XML, `buildDefinedIDSet`, `checkRequirements`, `preDefineReward`, `defineReward`, a `find*` call, `postProcess`. `getLayoutManager('TREE').generate(30,dir)` already returns the node list. With `JSCRIPTS=1`, each script needs `MODIFY JSCRIPT` approval unless the owner sets `JSCRIPTS=2`.

**Add.** A `gen` object in `JScriptEvent` and `JavaScriptingEngine` with `gen.item(file,id,vars)`, `gen.mob(file,id,vars)`, `gen.string(file,id,vars)`, `gen.room(file,id,dir)`, `gen.area(file,id,dir,seed)` and `gen.layout(name,size,seed)`. `gen.layout` returns the nodes with x, y, type and exits. `vars` is a JavaScript object. The results are live objects that the script places itself.

**Example.** A cartographer's `JS_PROG` calls `gen.layout('TREE',30,seed)` to sell a treasure map whose drawing matches the dungeon later generated with that seed.

*See also:* G5 (The same fresh-roll generation calls, for MOBPROG (MPGENERATE, GENSTRING) and for JavaScript (gen.item, gen.string and so on)); G17 (Both put generator access in JavaScript: G11 from play-time scripts, G17 inside templates).

<!-- from: generators/js-generate -->

<a id="g12"></a>

## G12. Name styles by culture and race

*Kind:* data file and script function · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `CharCreation.generateRandomName` has one hard-coded vowel and consonant table. Everything uses it: `SYSTEM_RANDOM_NAME:min-max` in templates, `NEW *`, the account name, character creation and `Spell_TrueName`. So elves and orcs sound alike. Templates can keep pools per culture in data, with `STRING` banks as in `inc_fantasynames.xml` or with syllable picks joined together. JavaScript can call `CMLib.login().generateRandomName(a,b)`. MOBPROG has no random-name function, and nothing picks a style by race.

**Add.** Name styles as data, one file per style in `world/namegen/` or `resources/namegen/`, in the format `makeRandomNameSets` reads: syllables with start, middle and end flags. A style can also hold prefixes, suffixes, titles, and given and family names by gender. Today's table becomes `default`. Templates write `SYSTEM_RANDOM_NAME:2-4:elvish`. Scripts get `RANDNAME(style min max)` or `RANDNAME(race gender)`. A `NAMESTYLE` on `GenRace`, a theme or a `GenLanguage` picks the style for `NEW *`, for `NAME=RANDOM` templates and for the name offered at character creation.

**Example.** The dwarf hold names its NPCs with `SYSTEM_RANDOM_NAME:2-3:dwarvish` (Thrandin, Borgrum), and `NEW *` for a dwarf offers 'Thrain Ironhand'. Goblin chieftains get harsh 'gr', 'zk' and 'ug' syllables.

*See also:* L5 (Both add syllable banks for made-up words (names vs unknown words), and G12 attaches a name style to a GenLanguage).

<!-- from: generators/name-styles, character-progression/cp-race-names, vocabulary-languages/vl-language-names -->

<a id="g13"></a>

## G13. Cleaner baking: unlinked areas, apostrophes, GENSOURCE, REGENERATE

*Kind:* builder tool · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** Baking already works: in builder mode, `GENERATE AREA` output goes to `world/areas/<area>.cmare` on `SAVE` or quit. But `getAreaXML` keeps no record of how an area was made, so it cannot be re-rolled without losing hand edits. A detached area needs a scratch room with a free exit, then `UNLINK`, then `LINK` later. Generated apostrophes are stored as backticks. That is the engine-wide convention and players see apostrophes, but it makes the files harder to read.

**Add.** (1) `GENERATE AREA ... UNLINKED`, which builds with no gate, for `LINK` later. (2) A world option `GEN_BACKTICKS=false`, or builder mode itself, keeps real apostrophes in generated text. (3) A `GENSOURCE` stat records the generator file, id, vars and seed, and is written to the `.cmare`. (4) `REGENERATE AREA [SEED=n|SAME]` rebuilds from `GENSOURCE` and keeps rooms marked `GENLOCK` (a room flag or property) with their exits. Parts 3 and 4 carry the value. Parts 1 and 2 are conveniences.

**Example.** The owner bakes 'Old Tunnels' unlinked, writes the story rooms by hand and marks them `GENLOCK`. After improving the template's descriptions, they run `REGENERATE AREA SEED=SAME`, and the hand-written rooms stay.

*See also:* G14, G9 (G14 needs G13's GENSOURCE, and G9 and G14 keep G13's GENLOCK rooms).

<!-- from: generators/gen-bake -->

<a id="g14"></a>

## G14. Regenerate behaviour and MPREGENERATE: areas that re-roll

*Kind:* behaviour and script command · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** `ResetWhole` and `MPRESET` restore an area's stored state. Only instances and grids re-roll. `StdAutoGenInstance` builds a fresh child per party and drops old ones after `PASSIVEMINS`. `RESET INSTANCE` re-rolls unless `NORESET=true`, and `AutoGrid` rebuilds on expiry. A `<SCRIPT>` block can call `resetInstance(R)` on an instance child. Nothing re-rolls a persistent area on a schedule or on a story event.

**Add.** A `Regenerate` behaviour for areas: `FILE= ID= VARS="..." SEED=random|daily|weekly|<n> WHEN=MUDDAY:<n>|MOON:FULL|ONEMPTY|SCRIPT KEEP=<room ids or GENLOCK>`. It fires only when no party member is inside. It destroys the generated rooms and rebuilds them, keeping gate links, kept rooms and anything a quest is using. `MPREGENERATE <area> [SEED=n]` triggers it from a script, and a trigger such as `GENERATED_PROG` runs afterwards. It needs `GENSOURCE` (G13) and seeds (G1).

**Example.** 'The Shifting Labyrinth' rebuilds itself at every full moon with a new seed. Its entrance hall and the minotaur's throne room, marked `GENLOCK`, stay as they are.

*See also:* ST20 (G14 runs ST20's GENERATED_PROG after re-rolling an area); G1 (G14's SEED=daily|weekly uses G1's seeds); G13, G9 (G14 needs G13's GENSOURCE, and G9 and G14 keep G13's GENLOCK rooms); T2 (Alternatives for a dungeon that is fresh when the party returns: reset it in place, or re-roll it).

<!-- from: generators/regen-on-reset -->

<a id="g15"></a>

## G15. New theme packs, set-piece library and theme skeleton

*Kind:* data file · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** Twelve themes ship, plus `planet.xml`. The driver's TODO lists ruins, crypt, castle, volcano and sky as missing. Making a new theme means copying and editing, since each theme lists its theme names in every `requires`. The layout variants are already one line each with `like="maze_dungeon"`. No set-piece rooms ship, though their parts exist: `Prop_OpenPassword`, `RandomTraps`, `Prop_Hidden`, `class="catalog"`. `guides/RandomAreas.html` leaves out `BRANCH`, `MESH` and `SPOTTEDMESH`. It also says `repeat-X` selects every choice, but the code draws X with replacement.

**Add.** Data only. Seven themes built from the existing banks: `crypt.xml`, `ruins.xml`, `castle.xml`, `volcano.xml`, `sky.xml`, `mine.xml` and `sewer.xml`. An `inc_setpieces.xml` with a riddle door (`Prop_OpenPassword` fed by a riddle `STRING` bank), a lever room (a MOBPROG on a `GenItem`), a healing shrine, a trapped corridor (`RandomTraps`) and a collapsed passage. Each set piece is gated by a `__setpiece` counter and the depth tags (G3). A `theme_skeleton.xml` with one `AREA`, the layout from a variable or list, `requires` built from one `THEME_NAMES` string, and a comment on each slot. The guide documents the skeleton, and its errors are fixed.

**Example.** The owner copies the skeleton to `fungal_caverns.xml`, fills in the room text and five mob tags, and adds a riddle door. `random_dungeon` can now roll it.

*See also:* G3, G9, ST20 (G9, G15 and ST20's GENVAR use G3's depth tags (ROOMTAG_DEPTH, AREA_MAXDEPTH)).

<!-- from: generators/theme-packs -->

<a id="g16"></a>

## G16. Multi-level areas and new layouts (CAVE, CLUMP, LINEAR, RING, ISLANDS)

*Kind:* generator · *Value:* medium · *Effort:* large · *Decision:* yes / no

**Today.** Every layout is a flat grid. `AbstractLayout.getDirection` gives only north, south, east and west, and no layout uses `LayoutRuns.ud`. Some of this can be done in data, though it is untested. An `EXIT` with `DIRECTION="DOWN"` builds a nested `ROOM`. A `ROOM` with `layout=` and `size=` becomes a sub-layout, so a `__counter` can give one stair per level. Picking a layout at random already works, with `select="any"` over `like=` variants or with a `$variable`. A JavaScript layout in a `LIBRARY` path's `layouts/` folder may also load.

**Add.** A `levels="n"` attribute on `AREA` and on nested room layouts. It splits the size over n stacked floors joined by `UP` and `DOWN` at non-leaf nodes, and exports `ROOMTAG_LEVEL` and `ROOMTAG_STAIRS`. Five new layouts: `CAVE` (cellular-automata blobs, winding passages), `CLUMP` (a square with random dead ends), `LINEAR` (a road with side spurs), `RING` (a loop with spokes, for wall walks) and `ISLANDS` (clusters joined by single bridges). `layout` also takes a comma list and picks one at random.

**Example.** `<AREA layout="TREE" levels="4" size="60">` makes a goblin mine four floors deep with ladders down. A 'Sky Citadel' uses `RING` with `levels="2"`.

*See also:* G4 (G4's LEVELS option needs G16's multi-level layouts).

<!-- from: generators/layouts-multilevel -->

<a id="g17"></a>

## G17. JavaScript in random-area templates

*Kind:* generator, JavaScript hook · *Value:* low · *Effort:* medium · *Decision:* yes / no

**Today.** Random-area templates (`resources/randareas`, run by `GENERATE` through `MUDPercolator`) compute values with `${...}` variables, math expressions and `STAT:` lookups, and filter with their own `CONDITION`/`VALIDATE` grammar. The `j` variable flag only escapes a value for use inside JavaScript. No JavaScript runs while generating. A `<SCRIPT>` on a generated mob's Scriptable runs at play time, which is too late. So logic like "a name from my own tables" or "skip this room if a flag is set" has to be written in the template language.

**Add.** `${js:expr}` in template strings, and `CONDITION="js:expr"` and `VALIDATE="js:expr"` on template tags. The expression sees `defined` (the generator's variables) and `obj` (the object being built). It also gets the bindings that the other JavaScript items in this plan give scripts (`cm`, and `require()` from `world/js/lib`). One name or loot library then serves both play and generation.

**Example.** `<STRING ID="innkeeper_name">${js:require('names').dwarven(2)}</STRING>` names each innkeeper from the owner's dwarven tables. `<ROOM CONDITION="js:!cm.store('world').bridgeBurnt" ...>` leaves out the bridge room once the story has burnt it.

*See also:* JS3 (G17's template JavaScript loads libraries through JS3's require() from world/js/lib); G11 (Both put generator access in JavaScript: G11 from play-time scripts, G17 inside templates).

<!-- from: javascript/js-generator-hook -->
