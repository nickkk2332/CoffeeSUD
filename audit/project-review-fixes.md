# Project review: first implementation pass

This follows the six summary areas in the [project review](project-status-review.md),
including its named developer-workflow gaps. The original report and diagnostic
observations describe `d1787779`; this document records the follow-up changes.

| Area | Implemented follow-up |
| --- | --- |
| Save safety | Exclusive process ownership before startup selection or cleanup; active working-copy protection; interrupted-rename recovery; synced files and completeness manifests; retained previous generation; format compatibility checks before restore. |
| Combat persistence | Rotation rounds, cooldowns, ONCE history, threat and timing; early boss rounds, engagement, immunity and summon bindings; saved combat targets/actions and follow links; independent copied state. |
| Script identity | Persistent runtime instance IDs for mutable stores, queued actors and journey source bindings; distinct IDs for copies; explicit identity fields for generic and non-generic saved objects. |
| World asset tracking | Creation, origins, perks, combinations, item sets, kits, encounters and authored Java class files join the asset catalog. Saves record the accepted loaded version and a per-file manifest, including reloads. The window panel already used the tracked text directory. |
| Validation | MovingRoom and validation share stop parsing. Gaps, duplicate/empty stops and missing rooms produce findings. Every detail record participates in ID collision checks. |
| Developer workflows | Disposable `PLAYTEST GAME`; compiled reload migrates declared state and preserves effect lifetime, or keeps an incompatible instance; pending choices/narration resume after restore; bounded background JavaScript jobs keep blocking work off the game thread. |

See [the usage and compatibility guide](../CoffeeMud/guides/ReviewReliability.md).

The original CSV statuses remain the review baseline. Its `followup_status` and
`followup_note` columns describe this pass. Seven defect-affected items and four
partial items now have an implementation for their reviewed gap. Broader partial
tools, creative extensions and explicitly deferred items keep their original scope.

## Deliberate boundaries

- In-place `PLAYTEST ON/OFF` keeps its changes. `PLAYTEST GAME` is the disposable
  route: a separate campaign/process preserves the builder session and files.
  Party/difficulty presets and per-party mortal/archon commands remain future work.
- Custom reload state must be declared through `STATE`/`ACTIVITY`. Arbitrary
  private fields and retained library closures cannot be inferred automatically.
- Synchronous Java/compiled calls are not forcibly preempted. `cm.background`
  provides bounded completion and cancellation, a fresh worker scope, JSON data,
  and game-thread callbacks. Operations that ignore interruption can retain one
  of the bounded workers; late results cannot mutate a restored world.
- Legacy colliding name stores cannot identify their historical owner reliably.
  Unambiguous stores migrate automatically; ambiguous data stays under its old
  explicit key for intentional assignment rather than being duplicated silently.
- Authoring assets remain live external files. The manifest records accepted
  definitions; it does not package a private copy into every campaign.
- The validators now reject the reproduced false-green cases. Full static
  argument/data-flow analysis and schemas for every property remain separate
  partial-tool work.
- Percent-expression decoding (G17), cold metacraft determinism (G1), and the
  remaining partial-tool items were outside these six summary areas.

## Regression coverage

- `tools/storage-tests.sh` / `tools/SaveStorageTest.java`: real second-process exclusion and crash-lock
  recovery, active/stale working directories, rename-window simulations,
  corrupt-generation fallback, complete/incomplete first saves, compatibility
  rejection and deletion of recovery generations.
- `review-core.js`: duplicate-name and moving/renamed stores, copied metadata,
  legacy migration, combat state, each newly tracked asset, accepted-version
  provenance, route parser parity and later detail IDs.
- `review-reload.js`: actual compiled behavior replacement preserves declared
  state and refuses incompatible replacement while retaining the live instance.
- `review-workflows.js`: restored menu callbacks, changed-source cancellation,
  unread narration and pause ownership, effect migration, independent playtest
  preparation, isolated JSON jobs and native-call timeout completion.
- `review-background.js`: background-party prompts return to the shared terminal,
  restored choices queue, and narration without a visible window releases its hold.
- `tools/fixes.sh review`: separate played processes for a mortal disposable
  campaign, combat/identity/queued-context restoration and pending interactions.

Verified across the full run and targeted replays:

- **3,851 reported regression checks:** 3,422 JavaScript checks across 326
  fixtures and 429 played checks. The new played integration driver additionally
  verifies 15 in-session assertions inside its subprocess transcripts.
- **97 smoke checks** passed, including normal-play world-file preservation.
- **17 storage/process checks** passed through `tools/storage-tests.sh`.
- The engine comparison matched **47 passes / 3 pre-existing failures** in its
  isolated copy during this pass. Final interaction/reload refinements were
  covered by the focused fixtures and virtual-display replay.

The full run exposed the synchronous FakeSession callback regression and a
missing `xvfb-run` dependency. The callback guard was corrected; all nine
related fixtures passed 127 focused checks. PR5/PR21 then passed 25 checks with
locally extracted Xvfb dependencies. A final 54-check replay covered reload
state capture before detach, attachment cleanup, effect ticking, menus and
hirelings. Other full-run groups passed without changes. The reported total
counts each fixture/check once rather than adding duplicate replay results.

Java compilation, Python/shell syntax, inventory consistency, document links
and `git diff --check` were also checked. Existing executions drafts were
preserved; executions remain a separate implementation project.
