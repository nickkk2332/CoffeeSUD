# CoffeeSUD feature status and implementation review

The [first follow-up implementation](project-review-fixes.md) addresses the six
summary areas. The findings and counts below preserve the original review baseline;
the CSV includes separate follow-up status columns. The
[remaining implementation follow-up](remaining-implementation-fixes.md) closes
18 more gaps; current totals are 388 implemented, 1 partial and 20 deferred.

Reviewed on 2026-11-05 at `d1787779`. This covers the single-player conversion in PLAN.md,
the 409 numbered items in the builder audit, the recent authoring consolidation,
and the executions draft. It is a source review with focused diagnostic probes,
not a certification of every combination of the engine's features.

The fork has substantial working systems: standalone play, a game clock,
turn-based party control, real campaign saves, file-based authoring, extensive
scripting and developer tools. The next useful work is persistence and authoring
integration. Several features marked done implement their main path while
leaving important state, validation or workflow cases unfinished.

## Inventory and meaning of the statuses

[The complete 409-item inventory](project-feature-status.csv) records each
feature's status, remaining limit, related finding, source files, regression
fixtures, original audit location and implementation-plan references.

- **Implemented:** a runtime/tooling implementation was identified. This means
  the capability exists; it does not mean every branch was independently tested
  in this review or that no further defects exist.
- **Partial:** the core capability exists, with a known missing part or
  integration contract. The reason is explicit in the inventory.
- **Implemented with defect:** the feature exists but is affected by a finding
  reproduced here. These counts are affected audit items, not a count of bugs.
- **Deferred:** the owner deliberately placed it in LATER.md. This is separate
  from an implementation failure.

There are **359 implemented items with no specific incompleteness established
in this review, 22 partial items, 8 implemented items affected by reproduced
findings, and 20 deferred items**. All 389 non-deferred audit items have mapped
source evidence. The core conversion and executions are assessed separately
below, since they are not additional numbered items in the 409-item audit.

| Theme | Implemented | Partial | With defect | Deferred |
| --- | ---: | ---: | ---: | ---: |
| Builder tools | 13 | 0 | 0 | 1 |
| Combat and encounters | 22 | 0 | 2 | 1 |
| Crafting, gathering and housing | 21 | 0 | 0 | 1 |
| Testing and debugging | 25 | 10 | 2 | 2 |
| Factions and organizations | 19 | 0 | 0 | 3 |
| Generators | 15 | 1 | 1 | 0 |
| Items and puzzles | 23 | 3 | 0 | 1 |
| JavaScript | 17 | 3 | 1 | 0 |
| Languages and vocabulary | 14 | 0 | 0 | 0 |
| NPC behavior/dialogue | 22 | 1 | 0 | 6 |
| Characters/progression/abilities | 29 | 0 | 0 | 1 |
| Presentation | 19 | 1 | 0 | 0 |
| Quests | 16 | 0 | 0 | 0 |
| Saves and world files | 20 | 2 | 2 | 0 |
| Script language/functions | 16 | 1 | 0 | 0 |
| Script triggers | 27 | 0 | 0 | 0 |
| Travel/vehicles/instances | 16 | 0 | 0 | 0 |
| World simulation | 25 | 0 | 0 | 4 |

The counts describe feature scope. A shared defect can affect another otherwise
implemented feature: for example, origins and perks work, while their data files
are missed by the world-version tracker. Those relationships are also in the CSV.

## The single-player conversion

| Feature | Current status | Remaining limit or improvement |
| --- | --- | --- |
| Standalone terminal launcher | Implemented | The startup save selection, profile menu and title/prologue are separate flows; a unified game menu is unfinished. |
| Removal of network/web/intermud/mail services | Implemented | Internal host/thread-group registries remain architectural debt. They do not make the console game a network server. |
| One game thread, pause and game clock | Implemented | Blocking Java or compiled script work can still monopolize that thread; see R08. |
| Turn-based actions and party turns | Implemented | Leadership after death still requires manual PARTY LEAD; richer initiative/action scheduling is an extension. |
| Seeded dice and reproducibility | Partial | Full session replay is not exact; tick ordering/boot timing remain relevant. Cold metacraft generation also has a narrower known determinism gap, R07. |
| Whole-world campaign save/restore | Implemented with defects | Working-directory ownership and crash recovery need protection, R01–R02; combat and script state have gaps, R03–R04/R11. |
| File-based worlds and database cache | Implemented with defects | Authored-asset coverage and effective version tracking are incomplete, R05. External definitions remain live rather than embedded in each campaign. |
| Profile, switching, party commands and restrictions | Implemented | The feign-death switch fix and world party controls are present. Pets, summons and hirelings still have separate limits, R12. |
| Party PvP and character windows | Implemented | Windows share one game process/world. Separate launcher processes need the ownership guard described in R01. |
| Unique/rare catalog items | Implemented | Container rules, shared caps, once-ever/returning uniqueness and save tracking exist. Stable catalog identity and migrations deserve explicit policy when shipped definitions are renamed. |
| New builder/debug/authoring systems | Mostly implemented, with partial workflows | See the theme table, CSV and R06/R10. The archived MUDGrinder is reference material; a replacement visual editor is not implemented. |
| Shipped game content | Intentionally minimal | The shipped world has two rooms. Building a campaign is the owner's content work, not a missing engine implementation. |
| Executions | Draft only | Tick/node runtime, participant control, branches, interruptions and mid-sequence restore are not implemented. The workspace plans are design documents. |

## Findings to address first

### R01. A second launcher can remove another game's working database

**Priority: high. Reproduced with the filesystem API in an isolated directory.**

`prepareWorkingDatabase` unconditionally calls `deleteWorkingDatabases`, which
removes every `.play-*` directory in the save folder. There is no save-folder
ownership lock checked before this cleanup. A second launcher using the same
SAVEDIR can unlink the first game's working state on this filesystem.

The probe created one working database and an active marker, prepared a second,
and observed `second_start_deletes_first_working_state=true`. This tests the
cleanup operation directly; it does not attempt to corrupt a real campaign.

Add a process ownership lock before cleanup, identify abandoned directories
without touching live owners, and refuse or isolate an accidental second launch.
Test two processes using one save folder, as well as stale-lock recovery.

Evidence: [SaveSlots.prepareWorkingDatabase](../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveSlots.java#L270),
[cleanup](../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveSlots.java#L310).

### R02. Interrupted save replacement has no startup recovery

**Priority: high. Reproduced by simulating the intermediate filesystem state.**

Saving writes `.new-slot`, renames the old slot to `.old-slot`, and then renames
`.new-slot` to the public slot name. Caught rename failures have rollback.
An abrupt termination between the two renames leaves the public slot absent.
Listing/restoring ignores the hidden backup, and startup has no recovery pass.

The probe retained a complete `.old-hero/header.properties` and world snapshot,
but listing returned zero saves and lookup of `hero` failed. It simulates the
rename window; it is not a power-loss/fsync durability test.

Recover interrupted transactions before listing, cleanup or another write;
validate candidate snapshots; retain a last-known-good generation and define
flush/durability guarantees. An unsupported save-format version should also be
rejected or migrated explicitly: versions are written today but there is no
central version migration/compatibility gate.

Evidence: [replacement sequence](../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveSlots.java#L335),
[slot listing](../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveSlots.java#L121).

### R03. Some new combat behaviors do not preserve their fight state

**Priority: high. Reproduced through the actual behavior serialization API.**

CombatAbilities holds the rotation round and last-used-round map in memory.
Its STATE is empty. A serialization round trip changed round **7 to 0** and
removed the cooldown map. This can replay openers and ONCE attacks and reset
cooldowns when the same fight is restored.

BossPhases saves no state before the first phase fires, even when a ROUND
trigger is counting toward a later phase: round **7 became 0**. Its state after
a phase did preserve round 7, providing a positive control. Other fight fields,
such as the engaged flag, are omitted too. Cleanup/ownership of phase summons
and temporary immunity should be included in the follow-up census.

These are serialization probes, not a complete played fight/restore matrix.
WorldState uses the same STATE API and omits empty behavior state.

Implement complete per-behavior state contracts and add played restores before
the first phase, between rotation uses, during immunity and after summons.
Audit the other stateful AI behaviors with the same persistence checklist.

Evidence: [rotation fields](../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/CombatAbilities.java#L62),
[default empty state](../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/StdBehavior.java#L328),
[boss state](../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/BossPhases.java#L496),
[world state writer](../CoffeeMud/com/planet_ink/coffee_mud/core/save/WorldState.java#L534).

### R04. Saved identity is still inconsistent across subsystems

**Priority: high. JavaScript store failures reproduced in a running scratch game.**

The recent presentation/discovery ID fixes work in their intended systems.
`cm.store(host)` still keys NPCs by start room plus name, and items by owner or
room plus name. Two distinct same-name NPCs shared a store in the probe. Moving
an item selected an empty store; moving it back recovered the previous data.
The per-object ObjectVars mechanism distinguished the two NPCs as a control.

Queued MOBPROG contexts and restored journey source-host lookup also retain
name-based references in places. Those additional cases have source evidence
and need their own duplicate-name/rename restore tests.

Separate **authored definition IDs** from **runtime instance IDs**. Sharing a
clue's definition ID between copies can be intentional; sharing an NPC's mutable
state is a different contract. Extend instance identity and migration to stores,
queued contexts and host bindings. Explicit string stores should remain shared.

Evidence: [JSStore.keyOf](../CoffeeMud/com/planet_ink/coffee_mud/core/JSStore.java#L60),
[ObjectVars](../CoffeeMud/com/planet_ink/coffee_mud/core/ObjectVars.java#L56),
[queued script contexts](../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java#L15114),
[journey host fallback](../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_JourneyState.java#L180).

### R05. World asset tracking still misses authored files and live changes

**Priority: high for shipped-world compatibility. Reproduced.**

Adding each of these files left `WorldFiles.checksum()` unchanged:

```
creation.ini       origins.ini       perks.ini
combinations.txt   itemsets.txt       kits/*.kit      encounters/*.enc
```

A vocabulary file changed the checksum as a positive control. The recent
registry expansion therefore fixed its selected folders, but does not cover
all older/newer authoring systems. Character creation, perks, kits, encounter
balance and puzzle combinations can change without the expected
world-version notice.

Correction from the implementation pass: the original probe also added root
`panel.txt`, but WindowPanel reads `text/panel.txt`, which was already tracked.
The root-file observation is not evidence of a missing panel asset format.

A second problem is provenance after live reload. Reloading a tracked asset
changed the current checksum but left `bootChecksum()` unchanged. SAVE records
that boot-only checksum. A campaign that has already used reloaded definitions
can therefore record an older world version. Inspecting that save against the
old/new files can give misleading change notices.

Declare asset paths, dependencies, validation and reload behavior alongside
each authoring subsystem. Save an effective loaded-asset manifest, including
live definitions, rather than assuming the entire campaign uses its boot version.
Offer explicit packaging/version policy when a campaign must pin definitions.

Evidence: [asset registry](../CoffeeMud/com/planet_ink/coffee_mud/core/WorldAssets.java#L14),
[save header](../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java#L1010),
[origins reader](../CoffeeMud/com/planet_ink/coffee_mud/core/Origins.java#L88),
[kits reader](../CoffeeMud/com/planet_ink/coffee_mud/core/StartingKits.java#L68).

### R06. Validation still has false-green paths

**Priority: medium. Two concrete cases reproduced.**

A route containing valid STOP1 and STOP3 room references passed ROUTES
validation with no findings. MovingRoom stopped parsing at the missing STOP2
and retained only one stop. Validation checks matching references, but not the
same structural invariants as the runtime parser.

A Prop_Details property with a unique first record and a duplicated second
record ID passed IDS validation. The checker reads only the first ID from the
whole property. The later record collided with an ID on another object.

Beyond these cases, SCRIPTCHECK lacks full argument/data-flow checking;
GENERATE CHECK lacks complete variable/REQUIRES/unknown-attribute analysis;
property schemas cover a subset of the much larger property catalog. Text validation still has
uneven coverage across base object text and other authored formats.

Have parsers expose their parsed models and validation invariants. Add negative
contract fixtures for incomplete structures, later records, aliases and missing
references. Integrate the useful standalone checks into the combined build check.

Evidence: [route/ID checks](../CoffeeMud/com/planet_ink/coffee_mud/core/AuthoringCheck.java#L158),
[schemas](../CoffeeMud/com/planet_ink/coffee_mud/core/AuthoringSchema.java),
[script checker](../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptCheck.java),
[generator checker](../CoffeeMud/com/planet_ink/coffee_mud/core/GenerateCheck.java),
[quest checker](../CoffeeMud/com/planet_ink/coffee_mud/core/QuestCheck.java).

### R07. Procedural generation has two known correctness limits

**Priority: medium. Percent decoding reproduced; cold-seed gap documented in code/tests.**

XML percent-hex decoding changes formula text: `level %20 + 1` became
`level   + 1` in the probe. Generator expressions and JavaScript in XML
attributes can be altered before evaluation. Resolve legacy percent escapes
separately from ordinary XML/authoring expressions.

G1 also explicitly retains a cold metacraft determinism gap. The regression
fixture warms seed 370170 before comparing subsequent builds, so it does not
establish first-build determinism. Remove that initialization dependency and
compare first builds in fresh processes. Full session replay is a wider,
explicitly deferred DT39 feature and should remain separately described.

Evidence: [percent decoding](../CoffeeMud/com/planet_ink/coffee_mud/Libraries/XMLManager.java#L566),
[known G1 limit](../DEVPLAN.md#L2614),
[seed warm-up](../tools/fixtures/fixes/g1-seeds.js).

### R08. JavaScript runtime limits have important coverage boundaries

**Priority: high for keeping the game responsive. Native-call bypass reproduced.**

With a one-second JSRuntime budget, a short interpreted script calling
`java.lang.Thread.sleep(1500)` returned 42 after **1,657 ms**, without a timeout.
Instruction observation cannot forcibly bound native/Java blocking work.
Compiled `//extends` classes are also outside that instruction budget, as the
runtime already documents. Either can monopolize the single game thread.

Provide a cancellable job/IO path for work that can block and state the precise
budget contract. This is a runtime-control boundary, not a claim that scripts
are sandboxed against an untrusted world author.

The ES6 upgrade is real, but JCONSOLE still replaces backticks with apostrophes;
interactive template literals therefore do not have the same behavior as JRUN
files. Finish the quote handling independently of the runtime-budget work.

Evidence: [instruction observer](../CoffeeMud/com/planet_ink/coffee_mud/core/JSRuntime.java#L106),
[JCONSOLE rewrite](../CoffeeMud/com/planet_ink/coffee_mud/core/JSConsole.java#L157).

### R09. Hot reload needs a runtime-state migration contract

**Priority: medium. Source-confirmed; no compiled-class reload probe in this review.**

Replacing compiled JavaScript behaviors copies their parameters. Replacing
effects copies misc text and the invoker, but does not generally migrate
STATE, proficiency, tick duration or other instance state. A successful
compilation/replacement is therefore not enough to promise uninterrupted
behavior. Removed files and dependency invalidation also need clearer semantics.

Define compatible reload, explicit reset and refused-incompatible-reload modes;
use existing state APIs; preserve effect lifetime; and report which existing
objects were migrated, reset or left on their old class.

Evidence: [behavior replacement](../CoffeeMud/com/planet_ink/coffee_mud/core/JSReload.java#L300),
[effect replacement](../CoffeeMud/com/planet_ink/coffee_mud/core/JSReload.java#L317).

## Partial developer workflows

### R10. Useful tools exist, but several promised workflows are unfinished

| ID | What works / what is unfinished |
| --- | --- |
| DT4 | CHATTEST/CHATLINT work; hidden-pattern reachability and tick-odds analysis remain limited. |
| DT8 | Mortal security mode works; no full campaign snapshot/rollback, PLAYTEST GAME or per-party mortal/archon workflow. |
| DT9 | Live log filtering/history exists; DEVLOG WINDOW is absent. |
| DT11 | Static checker exists and runs headless; argument signatures and variable data flow are incomplete. |
| DT14 | Generator lint covers core references/classes/selectors; variable flow, unsatisfiable requirements and unknown attributes are incomplete. |
| DT15 | External .play runner/assertions work; in-game PLAYTEST RUN/WORLD TEST and the wider planned test-file workflow are missing. |
| DT17 | Preview works; CHECKTEXT coverage remains uneven outside room text and selected property schemas. |
| DT19 | STAT BEHAVIORS works, but useful live internal state is only exposed by some behaviors. |
| DT21 | LAW inspection, warrants, pardon and accuse work; LAW TEST and LAW SET are missing. |
| DT33 | Only PUZZLE CHECK is implemented; listing/set/signal/reset console awaits deferred controller I12. |
| G1 | Seed controls exist; cold metacraft generation can differ and the regression fixture explicitly warms a seed. |
| I6 | Tiered sets work; legacy ALLSET support on all bonus properties and identify integration remain incomplete. |
| I7 | Curse mechanics work; promised identify-as-normal-below-level wording is absent. |
| I13 | Blockages respond to interaction verbs; ATTACK uses the blockage check rather than normal weapon/combat damage. |
| JS2 | Interpreted instruction budget works; native Java calls and compiled //extends classes are not forcibly bounded. |
| JS10 | Hot reload exists; replacing compiled behaviors/effects does not migrate all runtime state, proficiency or effect lifetime. |
| JS21 | Rhino ES6 upgrade works; JCONSOLE still converts backticks into apostrophes. |
| N2 | Nonblocking choices work; pending menu state has no general save/restore contract. |
| PR12 | Paged asynchronous narration works; pending session/page cursor is not saved. |
| S7 | Script progress is saved; saved queued contexts still use name-based references for some actors. |
| S16 | Most activity fields persist; Ability-valued links such as helpingAbility are explicitly skipped. |
| SC14 | Original scriptableTest.script automation is still absent; the confirmed command fixes have dedicated replacements. |

PLAYTEST deserves particular attention: it suppresses builder exemptions and
returns a killed tester to the starting room with restored health. It does not
roll back inventory, XP, quests or the whole builder world. An isolated
campaign test with character/party/difficulty presets would remove a recurring
source of misleading author tests and accidental authoring-state changes.

Evidence: [PLAYTEST](../CoffeeMud/com/planet_ink/coffee_mud/Commands/PlaytestCmd.java),
[restore point](../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Playtest.java#L79),
[PUZZLE CHECK-only command](../CoffeeMud/com/planet_ink/coffee_mud/Commands/PuzzleCmd.java#L33),
[LAW command surface](../CoffeeMud/com/planet_ink/coffee_mud/Commands/LawCmd.java#L29).

### R11. Saved interactions and timers need explicit lifecycle contracts

**Source-confirmed limits; cross-window modal restore is not reproduced here.**

Pending MPMENU choices and narration page queues are transient maps associated
with characters/sessions, without a general serialized interaction record.
Define whether saving during an interaction is refused, resumes that interaction,
or explicitly cancels/reissues it. Include other party windows and script-driven
saves in the tests. Narration's temporary real-time pause makes this a clock
lifecycle concern as well as a display concern.

Recurring JavaScript timers are intentionally registered on boot rather than
saved; their elapsed phase restarts. Saved `after` callbacks are rebuilt from
function text and data, so lexical closures are not retained. These are documented
constraints, not missing implementations of the original timer registration API.
Persistent callback names, host bindings and next-fire times would make authored
long-running simulations more dependable.

Common-skill activity snapshots preserve most scalar/object/list fields, but
Ability-valued links such as `helpingAbility` remain explicitly skipped. The
save warning is useful; completing these links is needed for exact assisted
crafting/gathering continuation.

A reusable saved sequence/interaction runtime would support menus, scenes,
narration and the proposed executions system without separate lifecycle rules.
Existing Scene remains a useful saved linear scene tool; a branching executions
runtime is not present.

Evidence: [menus](../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptMenus.java#L74),
[narration](../CoffeeMud/com/planet_ink/coffee_mud/core/Narration.java#L13),
[timer contract](../CoffeeMud/com/planet_ink/coffee_mud/core/JSTimers.java#L32),
[activity serialization](../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Common/CommonSkill.java#L1074).

### R12. Authoring ceilings and worthwhile extensions

These are capability limits/extensions, not blanket evidence that the current
features are broken.

- **Creatures and abilities:** generic races/classes/abilities, forms, summons,
  phases, immunities, modifiers and script hooks provide substantial control.
  Body-part/wear categories and weapon damage categories still have engine
  definitions. A data-authored creature can use the existing vocabulary;
  introducing a genuinely new anatomy/equipment/damage taxonomy is a deeper
  engine change. Scripts can add mechanics without making those registries
  fully data-driven.
- **Travel:** routing is a bounded search for the fewest compatible steps.
  Weighted time/danger/fare policies, stable vessel/host selectors and ambiguity
  errors would help larger worlds. Recovery/refund controls from the last pass
  are implemented; the missing feature is not basic journey cancellation.
- **Party:** profile and active-character limits and summon/meeting rules work.
  A combined budget across profile characters, pets, summons and hirelings is
  not provided by those controls. Leader-death handoff remains manual.
- **Organizations:** diplomacy, ranks, dues, standing, guild services and guild
  scaffolds exist. Their actual quests, lessons, stock and progression are world
  content. Autonomous faction strategy/warfare is deferred, not implemented by
  the guild templates.
- **Languages:** dictionaries, related-language comprehension, immersion, text
  marking and rule hooks exist. Declarative sentence grammar/registers and
  paced learning/exposure cooldowns would extend them.
- **Scale and portability:** add a sizeable authored-world integration fixture,
  long-duration simulation tests and automated Windows/macOS coverage. This
  review makes no new performance or platform-compatibility certification.

Evidence: [race body categories](../CoffeeMud/com/planet_ink/coffee_mud/Races/interfaces/Race.java#L553),
[weapon damage categories](../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Weapon.java#L87),
[route search](../CoffeeMud/com/planet_ink/coffee_mud/core/Travel.java#L324),
[manual leadership](../CoffeeMud/com/planet_ink/coffee_mud/core/Party.java#L645).

### R13. Update the status documentation alongside the next fixes

Some plan entries are historical and have already been superseded: DT11 now
runs inside headless WORLD CHECK; starting kits share the developer kit format;
named quest steps and their QUESTCHECK cross-references exist; TaxiBehavior
exists; and workstation quality hooks were subsequently implemented.
Old phase-4 save limitations were also addressed
by later save work. They should not all be reported as current missing features.

The inverse matters too: an item called done can still have a following
"not done" paragraph or a regression fixture that works around an acknowledged
gap. Maintain the feature inventory with explicit accepted scope, remaining
scope, persistence contract and positive/negative validation coverage.

## Explicitly deferred and draft work

- **B14:** CHATDRAFT: LLM drafts of fixed NPC dialogue.
- **C24:** Invasions and raids on an area.
- **CR22:** Linked storage chests for the profile: Prop_SharedStash.
- **DT39:** DETERMINISTIC=1: exact replay for playtests.
- **DT40:** SIMFIGHT: simulate the real party against an encounter.
- **F19:** ClanWarfare behaviour: NPC clans raid and conquer.
- **F21:** Deity holy days, favor gates and portents.
- **F22:** Prop_ClanDisguise: pass as a member in uniform.
- **I12:** PuzzleController: puzzle state, sequences and solve actions.
- **N13:** Companion behaviour: NPC party members who take orders and comment.
- **N14:** Dialogue trees: a Dialogue behaviour and .dlg files.
- **N23:** Rumours: a world list, gossiping NPCs, rumours from events.
- **N25:** LLM calls off the game thread.
- **N26:** LLM NPCs with lore files and saved memory per player.
- **N27:** LLM NPCs that act from a declared whitelist.
- **P30:** Training that takes game time.
- **W24:** Gradual and conditional restocking.
- **W25:** Exposure: body temperature from climate, shelter and clothing.
- **W26:** Bounty hunters and wanted posters.
- **W27:** Market: supply and demand that remembers and recovers.

The executions plan remains draft-only outside those 409 IDs. A replacement
visual authoring editor is also future work; the archived MUDGrinder is not an
active editor for this fork.

## Verification and evidence

The reference regression baseline for `d1787779`, recorded by the preceding
implementation session, is **3,773 regression checks, 97 smoke checks and
47 engine passes / 3 pre-existing engine failures**. This review did not rerun
that whole suite. It used source tracing, inventory mapping and these additional
isolated probes:

- Running-game probes for JavaScript store identity, checksum coverage, live
  reload stamps, route/detail validation and XML percent decoding.
- Behavior STATE round trips for rotation and boss-phase state, with a positive
  control after a phase had fired.
- Filesystem simulations for working-database cleanup and the interrupted-save
  rename window.
- A separate JVM probe for a native Java call exceeding an interpreted budget.

See [probe sources/instructions](probes/README.md) and [observations](probes/project-status-results.txt).
The diagnostic runner's PASS means the probe completed, not that the observed
behaviors are correct. No engine fixes were made by this review. The existing
executions drafts and DEVPLAN edits were preserved.

## Recommended implementation order

1. Protect save-folder ownership and recover interrupted slot writes; add
   version/compatibility gates before broader save-format changes.
2. Complete combat behavior state and instance identity across JavaScript
   stores, queued contexts and restored host bindings.
3. Complete the authored-asset catalog and effective loaded-world manifest.
4. Make validation consume runtime parser models and add the negative cases
   reproduced here; fix percent decoding and cold generator determinism.
5. Define native/compiled-script runtime limits and hot-reload state contracts;
   provide a cancellable path for work that can block the game thread.
6. Complete isolated PLAYTEST and persistent interaction lifecycles, then use
   that foundation for executions/dialogue expansion.

The deferred creative features can remain separate choices. The first four
steps address correctness in systems that already exist.
