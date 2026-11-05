# Current authoring limits and recommended follow-up

Reviewed against commit `99615dbe`. This covers the recent faction/organisation,
travel/instance, presentation and language systems, their shared runtime paths,
and relevant remaining limits in scripting, creature authoring, generators and
builder diagnostics. It is a code review with targeted reproductions, not an
exhaustive playthrough or performance benchmark. The findings below record the
original review; the first consolidation pass is now implemented.

The most useful next investment is a consolidation pass: consistent rule
handling, stable identities, complete validation and clearer diagnostics. There
are also substantial creative capabilities worth adding after that foundation.

## First consolidation pass

Items 1–7 are addressed by the reliability changes described in
[authoring identities, checks and recovery](../docs/authoring-reliability.md):
shared NPC comprehension, persistent source IDs and migration aliases, quoted
option parsing and typed schemas, authoritative help edits/deletions, broader
world asset tracking, dedicated validation domains and configurable journey
recovery/refunds. NPC rank seats now distinguish identically named creatures.

External assets remain live definitions; campaign saves do not pin a private
asset bundle. Validation checks the supported formats and references without
executing arbitrary script paths or proving script data flow. The broader
capabilities in items 8–14 remain future work.

Verification: 3,362 JavaScript checks across 322 fixtures, 411 played checks,
97 smoke checks, and the engine's unchanged 47-pass / 3-known-failure baseline.
The final JavaScript replay includes saved source identities, migration aliases,
service comprehension, help overrides, asset reloads, cross-currency journey
refunds and recovery after a passenger dies or loses its marker.

## Correctness and integration to address first

### 1. Language restrictions do not cover every NPC conversation handler

**Reproduced.** A guildmaster with `Prop_Monolingual LANGUAGES=Orcish` reports
that it does not understand Common, but saying `join` in Common still joins its
guild. `GuildMaster` reads the speaker's original clear message rather than the
shared comprehension result. The stable conversation added to `Nanny`, and
existing Hireling speech handling, use the same raw-message pattern.

Route NPC conversation consumers through one hearing/comprehension interface,
including partial understanding, deafness and visual languages. Fixing only
SPEECH_PROG and MudChat leaves other interactions inconsistent.

Evidence: [GuildMaster.java](../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/GuildMaster.java#L95),
[Nanny.java](../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Nanny.java#L669),
[Languages.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Languages.java#L44).

### 2. Some saved identities change when content is renamed, moved or edited

**Reproduced.** A discovered `Prop_ShowWhen HIDEMODE=HIDDEN` object becomes
undiscovered after renaming it, even with the same explicit `ID`. Its discovery
key still includes its name and room before the explicit ID.

**Reproduced.** Moving the same sensory clue item between rooms produces two
separate once-only markers. Detail/sense clue keys include the current room,
object name and complete parameter text. Language immersion avoids the moving
room problem for carried items, but still keys its counters to the provider's
name and full property text; editing a gain or chance can reset progress.

Use persistent author IDs separately from display names, locations and prose.
Support state-key aliases/migrations when a shipped world changes. Waypoints
and first-entry properties already offer better examples of an explicit ID
replacing the unstable default.

A related case to test: NPC rank seat accounting excludes members by name.
Identically named NPCs may therefore be confused with the candidate itself;
this additional case has code evidence but was not reproduced in this review.

Evidence: [ViewRules.java](../CoffeeMud/com/planet_ink/coffee_mud/core/ViewRules.java#L12),
[Scenery.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Scenery.java#L26),
[Languages.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Languages.java#L102),
[Organizations.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java#L178).

### 3. Shared property parsing needs proper quoting and typed validation

**Reproduced.** In `SOUND="A label says CHANCE=0" CHANCE=100`, the option parser
reads the embedded CHANCE token instead of the actual outer setting. Escaped
quotes in message text are also truncated. Several new properties reuse this
parser, so this affects more than one feature.

Use a shared parser that understands escaping, quoted values and separators.
Define valid keys, value types, ranges and references in one place for parsing,
editing, AHELP and validation. Arbitrary prose must not turn into settings.

Evidence: [Scenery.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Scenery.java#L15).

### 4. Builder help edits can be shadowed, and deletion can leave a topic alive

**Reproduced.** Builder edits go to `world_help.ini`, but an alphabetically later
file such as `zzz_content.ini` overrides the edited value. The editor can save
successfully while HELP continues showing the other file's value.

**Reproduced.** Deleting a topic originally supplied by another world help file
redirects the deletion to the builder file. A stock hide marker is written, but
the original world definition loads again and the topic survives.

Make editor provenance and write precedence agree. Deletion needs to remove the
selected definition or create a final deletion override with documented rules.

Evidence: [WorldHelp.java](../CoffeeMud/com/planet_ink/coffee_mud/core/WorldHelp.java#L17),
[MUDHelp.java](../CoffeeMud/com/planet_ink/coffee_mud/Libraries/MUDHelp.java#L1888).

### 5. World version tracking misses newer content folders

**Reproduced.** Adding/changing vocabulary and lore files did not change
`WorldFiles.checksum()`. Its content list includes scripts and routes but omits
`vocab`, `lore` and presentation text such as `score.txt`.

A restored campaign can therefore receive changed language/lore/presentation
assets without the corresponding world-change notice. Saves preserve learned
state, but these external definitions are still read from the current world.

Create one asset registry for checksums, packaging, dependencies and reloads.
Define which assets remain live and which should be pinned to a campaign.

Evidence: [WorldFiles.java](../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldFiles.java#L2676),
[SaveGame.java](../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java#L674).

### 6. Validation has fallen behind the new formats

**Reproduced.** CHECKTEXT reports valid LANG markup inside a VARIES description
as unknown tags. It does not validate language IDs, matching LANG tags or
language files. WORLD CHECK has no dedicated vocabulary/lore/template domains;
PROPCHECK mainly checks keys, basic references, applicability and parser logs.

Expand validation to language dictionaries/scripts, glossary rules, health and
damage tables, lore, route/stop references, waypoint IDs and duplicate author
IDs. Include file/line diagnostics and make missing files visibly distinct from
an intentional fallback. Existing script checks also lack full argument and
variable-flow checking.

Evidence: [TextCheck.java](../CoffeeMud/com/planet_ink/coffee_mud/core/TextCheck.java#L66),
[WorldCheck.java](../CoffeeMud/com/planet_ink/coffee_mud/core/WorldCheck.java#L63),
[PropCheck.java](../CoffeeMud/com/planet_ink/coffee_mud/core/PropCheck.java#L152).

### 7. Journey recovery needs explicit missing-passenger policies

**Code-confirmed condition; not reproduced in play here.** Arrival requires the
original passenger count. If a passenger dies, loses its marker or cannot return
to the transit room, RESUME keeps retrying an arrival that cannot satisfy that
condition. CANCEL ends the trip at its current location. Cancellation has no
built-in refund or return-to-origin policy.

Add configurable handling for dead, disconnected, separated and abandoned
passengers: continue without them, wait, rescue, return or cancel. Define fare
refunds and cleanup for each outcome. This is especially relevant because
journeys explicitly support combat encounters.

Evidence: [Prop_JourneyState.java](../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_JourneyState.java#L174).

## Broader authoring capabilities worth extending

### 8. Scenes, dialogue and cinematic control remain separate primitives

Scene runs a linear ACT/ECHO/DO/PAUSE/NOTE list and stops when actors become
unavailable or fight. Branches can be scripted externally, but there is no
first-class scene graph, participant control/reservations, interruption policy
or multi-scene lifecycle. Paged narration holds the whole real-time clock;
it is not participant-only control while the world continues.

The proposed executions runtime is still a design draft. Dedicated dialogue
trees and the full Companion behaviour remain deferred too. A reusable sequence
runtime with saved node state, bindings, branches, action results and interruption
rules would make these considerably easier to author and test.

Evidence: [Scene.java](../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Scene.java#L449),
[Narration.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Narration.java#L21),
[execution plan](../plans/executions/implementation-plan.md),
[LATER.md](../LATER.md#n14).

### 9. Route planning is simple and object selection can be ambiguous

Routing finds the fewest open, terrain-compatible room steps, with a 10,000-node
search bound. It does not optimize travel time, fare, danger, faction access or
weather. Vessels and some restored journey hosts still resolve through names;
ambiguous names can select the first match. Waypoint destinations also select
the first matching discovered name in the network.

Add stable selectors, ambiguity errors, route policies and useful route-failure
explanations. Keep bounded searches, but make the bounds and failure reasons
visible to the author. Shared time advancement also currently permits only one
active fast-forward operation at a time.

Evidence: [Travel.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java#L254),
[Travel.java routing](../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java#L323),
[Waypoints.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Waypoints.java#L126),
[Prop_JourneyState.java](../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_JourneyState.java#L47).

### 10. Organisations provide rules and scaffolds, not an autonomous society

Guild templates create an editable starting structure; quests, lessons, stock,
real rank requirements and personality still need authoring. Merit checks run
for in-game members; dues can load offline player records, while NPC members
are not billed. NPC organisations do not yet have strategic agendas, automatic
raids/conquest campaigns or a full companion loyalty/order model.

Add these as explicit simulation modules if the world needs them. A universal
party budget covering profile characters, pets, summons and hirelings would
also be useful; the new profile/party settings are not a single policy for all
those categories, which still involve legacy follower rules.

Evidence: [organisation guide](../docs/organizations.md),
[Organizations.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java#L146),
[dues](../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java#L268),
[WorldRules.java](../CoffeeMud/com/planet_ink/coffee_mud/core/WorldRules.java#L237),
[LATER.md](../LATER.md#f19).

### 11. Language authoring has useful rules, but limited grammar and pacing

Translation is primarily word-based. Suffix rules and syllable banks do not
provide a declarative grammar, word order, noun classes or conversational
registers. JavaScript can go further, but its hooks receive word/language or
text/count rather than a complete speaker/listener/context object.

KEEPNAMES preserves all capitalized words, including ordinary sentence starts.
Immersion rewards repeated exposures without a novelty rule, cooldown, daily
budget or diminishing returns. Its provider selection and parent-area
inheritance also differ from local vocabulary inheritance.

Useful additions: context-aware hooks, separate proper-name policies, and
configurable learning schedules and repeated-source limits.

Evidence: [LanguageLexicon.java](../CoffeeMud/com/planet_ink/coffee_mud/core/LanguageLexicon.java#L29),
[Languages.java immersion](../CoffeeMud/com/planet_ink/coffee_mud/core/Languages.java#L96),
[Vocabulary.java inheritance](../CoffeeMud/com/planet_ink/coffee_mud/core/Vocabulary.java#L38).

### 12. Some creature/combat concepts still require Java extensions

Weapon damage channels are a fixed Java list and anatomy has 16 built-in body
part categories. Authors can vary counts, descriptions, races, abilities,
effects and scripted outcomes, but a genuinely new engine-recognized damage
channel or body part is not a normal data-only addition. Equipment locations
already have ADDWEARLOC/REPLACEWEARLOC support; that should not be confused with
adding a new anatomical category or an independent limb damage system.

Data registries for these concepts, and typed hit-location context, would raise
the ceiling for exotic creatures and the planned executions system.

Evidence: [Weapon.java](../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Weapon.java#L87),
[Race.java](../CoffeeMud/com/planet_ink/coffee_mud/Races/interfaces/Race.java#L553),
[wear-location configuration](../CoffeeMud/coffeemud.ini#L1770).

### 13. Presentation has fixed layout and retention policies

`world/score.txt` appends to the stock score rather than replacing its layout.
Maps use a direction-projected grid: non-geometric room graphs can overlap,
only the current plane is drawn as a grid, and radius/search limits are fixed.
The journal silently removes its oldest entries beyond 300, without pinning,
export or an author-controlled retention policy.

Add full layout templates, map layers/coordinate overrides and configurable
journal retention with protected clues or pinned notes. Keep sensible default
budgets for computed text (currently 32 calls per render context) and expose
why content was omitted when a budget is reached.

Evidence: [Score.java](../CoffeeMud/com/planet_ink/coffee_mud/Commands/Score.java#L373),
[PlayerMaps.java](../CoffeeMud/com/planet_ink/coffee_mud/core/PlayerMaps.java#L54),
[Chronicle.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Chronicle.java#L20),
[StoryText.java](../CoffeeMud/com/planet_ink/coffee_mud/core/StoryText.java#L101).

### 14. Large-world performance and exact replay need a dedicated pass

There are whole-world scans for ports, waypoints and preserved names. The new
systems also parse and serialize shared character JSON for many small updates.
These are scaling concerns, not measured performance failures in this review.
They merit indexes, incremental state updates and benchmarks with a substantial
world and long-running save.

Fixed random seeds are useful, but strict deterministic replay remains deferred.
The development plan also records a remaining metacraft/generation reproducibility
case. Scenario tests need party configuration, clocks, encounter state and
controlled random streams to distinguish content changes from execution drift.

Evidence: [Waypoints.java](../CoffeeMud/com/planet_ink/coffee_mud/core/Waypoints.java#L57),
[LanguageLexicon.java](../CoffeeMud/com/planet_ink/coffee_mud/core/LanguageLexicon.java#L12),
[StoryRecords.java](../CoffeeMud/com/planet_ink/coffee_mud/core/StoryRecords.java#L11),
[DEVPLAN.md](../DEVPLAN.md#L2594), [LATER.md](../LATER.md#dt39).

## Recommended sequence

1. Fix the reproduced integration/identity/parser/help issues, complete asset
   version tracking, and cover journey recovery.
2. Add schemas, dependency validation, one reload command and an explanation
   facility for visibility, language comprehension, rank gates and travel vetoes.
3. Standardize stable IDs and migrations across saved story state, NPCs,
   instances and authored definitions.
4. Build the execution/sequence runtime, then dedicated dialogue and companion
   authoring on the same lifecycle facilities where appropriate.
5. Expand simulation, grammar, layouts and core combat registries according to
   the needs of the first substantial world; benchmark that world as it grows.

## Validation performed

Pure Java probes reproduced property parsing, help overlay/deletion behavior,
LANG validation and omitted vocabulary/lore checksum changes. A scratch-world
played probe reproduced the guildmaster comprehension bypass, discovery loss
with a stable ID after rename, and repeated clue markers after movement.
The closed-container TOUCH probe was blocked as expected; it is not filed as a
confirmed issue. Other items above are marked as code-confirmed conditions,
feature limits, deferred work or unmeasured scaling concerns.
