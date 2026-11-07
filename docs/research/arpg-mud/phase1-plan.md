# Phase 1 and the Q proposals: build plan

Status: in progress. This is the working plan for finishing every Q proposal
(Q01 to Q12) and the dossier's phase 1 (A01, A02, A03, A04, A09, A15, I01, I02,
I03, I04, I18, G01, G03, P01, with Q03, Q05, Q06, Q07, Q08 and Q12 alongside).
Each item is built to the MVP and acceptance lines of
[proposals.csv](proposals.csv), on the existing optional-module framework
([chapter 14](../../creator/14-optional-modules.md)). Everything stays off by
default: a world that selects no module behaves exactly as before.

## Where each item starts

| ID | Before this plan | To build |
| --- | --- | --- |
| Q01, Q06, Q11 | Done | Q06: a track rename tested against a pre-change save (with P01) |
| A01, A02, A04, A09, I01, I03 | Done for their MVP | Used by the new families; no new work unless a family needs it |
| Q02 | Per-family CHECKs | Cross-family references in one command, with file and JSON-path locations; scripts marked unknown |
| Q03 | Skill sheet, craft preview | Build comparison; expedition preflight |
| Q05 | Module state, combat results, receipts | Ledgers for a character, item, relationship and expedition |
| Q08 | Event, chain, support, modifier limits | Per-encounter actor, trigger and loot budgets |
| Q10 | Ember Trial and four small packs | Guild laboratory, bonded familiar, branching expedition |
| I02 | Loot tables make tracked items | Rarity policy, guarantee counter, preview and sampling that agree |
| I04 | Add, remove, reroll, protect, unprotect | Replace, base upgrade, one staged production recipe |
| A15, A03, I18, G03, G01, P01, Q04, Q07, Q09, Q12 | Not started | New |

## Shared pieces

- **Definition families.** One loader (`core/ModuleContent.java`) for the new
  families: `world/<folder>/<id>.v<N>.json`, each `{"schema":1,"id","version","title",...}`,
  validated by the family, hashed canonically, and pinned (id, version, hash)
  in saved state. A pinned definition that changed is refused with its file
  named, as the existing families do. Several versions can be installed side by
  side, so a save keeps the version it pinned.
- **Native handlers.** Each family is a native module whose ID is its handler
  (as `items.modifiers` is), registered in `ModuleDefinitions`, with its required
  settings and state fields checked there, and `requires_handler` saved.
- **Random streams (Q09).** `core/RandomStreams.java`: named streams (`loot`,
  `offers`, `encounters`, `expeditions`, `projects`, `crafting`) drawn from the
  save's own seed, each with its own saved counter, so a loot roll never moves an
  encounter's dice. A draw can be recorded as a scenario (rules versions,
  stream, seed, counter) and reproduced. Inspection never draws.
- **Observability (Q12).** `core/ModuleStats.java`: bounded counters and timings
  per family and source (actors spawned, trigger roots and actions, budget
  refusals, transactions prepared/committed/cancelled/refused, duplicate events
  suppressed, catch-up steps). `WORLD MODULES REPORT` prints them by source,
  without character or campaign data.

## The families

### A15 Named resources: `abilities.resources`

- Definitions in `world/pools/`: a pool (`max`, `start`) or charges (`max`
  whole charges); optional `decay` (`amount`, `every`, `clock`: `turns`, `ticks`
  or `hours` of the game calendar); `reservable`; `label`.
- Character state: value, the clock reading it was last settled at, and the
  pin. Decay is settled lazily from the chosen clock, so a save and restore give
  the same value as play without one.
- Order: costs are checked and paid before a skill resolves (refused costs spend
  nothing); gains apply after it resolves, skill first, then supports, then
  triggers. A lower maximum clamps the value when it is next settled; a higher one
  adds nothing.
- Hooks: composition definitions gain optional `resources: {"cost":{...},"gain":{...}}`;
  trigger actions can gain or spend; JS API; `RESOURCES` shows each pool as text
  ("Heat 40 of 100", "Cinder charges: 2 of 3").
- MVP: heat, focus and a three-charge resource in an example pack.

### A03 Causal triggers: `abilities.triggers`

- Definitions in `world/triggers/`: `on` (`block`, `kill`), `priority`,
  `cooldown` (amount and clock), `cost` (A15 pools), `chance`, `include_summons`,
  and one `action`: a composed skill at the attacker, victim or self; gain or
  spend a pool; heal; or summon a catalog mob owned by the trigger's owner.
- Grants: `Prop_Triggers TRIGGERS=id,id` on worn/held items or on the character.
- A causal event carries a root ID, depth, owner, source and the consumers that
  already ran. Actions spend one shared per-root budget (`action_budget`) under a
  depth limit (`max_depth`), so two effects that trigger each other stop.
- Ownership: a summoned creature's kill fires its owner's `include_summons`
  triggers; a trigger's summon follows and belongs to the trigger's owner.
- Saved: cooldowns (by clock) and the recent committed roots, so a restored
  campaign cannot run a committed trigger again.

### I18 Reward escrow: `rewards.escrow`

- Definitions in `world/rewards/`: a bundle of catalog items, money and track
  points (P01).
- Character ledger: entries `provisional`, `banked`, `forfeited`, `claimed`,
  each with its activity, the event key that made it, and the reward's pin. One
  event key gives one entry, so replaying a completion adds nothing.
- An activity declares what leaving, failing, dying and restoring do
  (`forfeit`, `bank` or `keep`). Claiming is an I03-style transaction: all the
  outputs and the claimed mark settle together, with a receipt.
- `REWARDS`, `REWARDS CLAIM n|ALL`; JS API and MOBPROG `MPREWARD`.
- MVP: a three-room trial whose rewards bank at a shrine and resume after a
  restore.

### G03 Encounter director: `encounters.director`

- Definitions in `world/encounters/`: phases, each with an entry rule (start, a
  boss's hit points below a percentage, after a wave), a warning (Q07), spawns
  from the catalog, and objectives (defeat, survive N ticks, protect); budgets
  for actors, triggers and loot (Q08); a scaling rule chosen once at the start;
  what a wipe and a retreat do (`reset` or `fail`); rewards through I18.
- Runtime: behavior `EncounterDirector` on a room (`ENCOUNTER=id`), or started
  by an expedition or a script. One saved state machine: pin, stage, phase,
  wave, spawned actors by instance ID, participants, scale, granted keys.
- Target death, a removed actor, a retreat, a wipe and a restore cannot repeat a
  wave or a reward. `ENCOUNTER` repeats the current warning and objectives.
- MVP: a three-phase boss and a defense wave from the same schema.

### G01 Expeditions: `expeditions.contracts`

- Definitions in `world/expeditions/`: a destination (a `StdAutoGenInstance`
  area and its entrance), seed policy, difficulty, modifiers to choose from (how
  many), costs, attempts, participant limits, an optional encounter and reward,
  and the recovery room.
- A chart is an item with `Prop_ExpeditionChart EXPEDITION=id SEED=n TIER=n`,
  its seed drawn from the `expeditions` stream when it is made.
- `EXPEDITION PREVIEW chart` (Q03 preflight: costs, attempts, modifiers,
  party, refusals; spends nothing), `EXPEDITION CHOOSE chart mod mod`,
  `EXPEDITION OPEN chart` (one transaction: the chart and costs are consumed once,
  and a run record is made), `EXPEDITION STATUS`, `EXPEDITION RECOVER`.
- The run's seed and modifiers go to the generated instance when the party
  enters. A death inside uses an attempt; with none left the run fails and the
  party is returned. The run record keeps the instance's identity across a
  restore; a missing instance or member takes the recovery path.
- MVP: one chart opens a seeded dungeon with two modifier choices.

### P01 Progression tracks: `progression.tracks`

- Definitions in `world/tracks/`: owner scope (`character`, `organization`,
  `item`), points, earning rules (kills, composed casts, crafts, item use,
  script grants) with amounts, a level curve, a cap, milestones (combat stats for
  the owner or the item, an unlock flag), a reset policy, and `replaces` (old
  track IDs, for migration).
- State per owner: points, level, milestones granted, and recent source keys,
  so a repeated event cannot grant twice. An old track ID moves to its
  replacement once, deterministically.
- `TRACKS` (plain-text milestone tree, Q07), `WORLD TRACKS`; JS API and
  MOBPROG `MPTRACK`; `TRACKLEVEL()` in scripts.
- MVP: a character discipline, an organization research project and an item
  mastery track.

### I02 Loot policies: `items.loot`; I04 operations: `items.modifiers`

- Loot policies are their own family, `items.loot` (requires
  `items.modifiers` 1), in `world/loot/`: rarities with weights and modifier
  counts, a base's pool filtered by tags, tier limits by level, a power budget,
  and a guarantee (after N drops without a rarity, the next is that rarity).
  `MODBASE` takes `POLICY=id`. A separate module keeps the pinned
  `items.modifiers` manifest (version 1, required exactly by the Ember Trial)
  unchanged. The guarantee counter is campaign-wide (world state): drops made
  with no one present (spawn loot) count, and a party shares one luck.
- `WORLD LOOT PREVIEW policy level` gives eligibility and chances;
  `WORLD LOOT SAMPLE policy n level` samples on a cursor at the loot stream's
  next draw (it moves nothing, and predicts the next real drops) and checks
  itself against the preview.
- New operations: `replace` (remove and add, settled together), `upgrade`
  (a base to its declared `successor`, keeping modifiers) and a staged recipe
  (`kind: "recipe"`: a work piece carrying `Prop_WorkOrder`, stages settled one
  at a time through the craft ledger, the product made by a base or a policy
  at the last stage). Refusals explain protected modifiers and the group in
  the way.
- Q07 loot view: `LOOT [container]` summarises tracked loot by rarity, with a
  per-character `LOOT FILTER` (character state).

## Q tools

- **Q02:** `WORLD CHECK ONLY=REFS` (also part of a full WORLD CHECK) follows
  references between families: support to skill, trigger to composition and pool,
  expedition to encounter, reward and instance area, reward to catalog, track to
  milestone grants. Each error names the file and the JSON path. A script name is
  reported as unknown, not checked.
- **Q03:** `COMPOSE COMPARE skill-a skill-b` (two sheets side by side, with the
  combat profile's exact chances); craft preview exists; expedition preflight.
  None of these spends, draws or marks anything.
- **Q04:** `tools/simulate.py`: a scenario file names the world, two builds, a
  boss, seeds, a turn limit and metrics. Each batch runs in its own engine
  process. A run that passes the limit is reported as stalled; the report keeps
  sampled results apart from the exact chances of the combat profile.
- **Q05:** `LEDGER [character|item|relationship|expedition]`: controller,
  active grants, recent causal events, pending transactions, with the source of
  each.
- **Q07:** plain-text views for the track tree, boss warnings and the loot
  filter; inspection commands take no game time.
- **Q08:** encounter budgets, enforced and explained.
- **Q12:** `WORLD MODULES REPORT` and a stress fixture with declared limits.
- **Q10:** worked packs with create, play, save and restore steps.

## Stages

Each stage is committed with its fixtures and docs, and the full check runs
before anything is merged.

| Stage | Contents | Status |
| --- | --- | --- |
| 1 | ModuleContent, RandomStreams (Q09), ModuleStats (Q12 counters) | Done |
| 2 | A15 resources | Done |
| 3 | A03 triggers | Done |
| 4 | I18 escrow | Done |
| 5 | G03 director, Q08 budgets, Q07 warnings | Done |
| 6 | G01 expeditions, Q03 preflight | Done |
| 7 | P01 tracks, Q06 rename test, Q07 tree | Done |
| 8 | I02 loot policies, I04 operations, Q07 loot view | Done |
| 9 | Q02 references, Q03 compare, Q05 ledgers, Q12 report | |
| 10 | Q04 simulation | |
| 11 | Q10 packs, handbook chapters, catalog status | |
