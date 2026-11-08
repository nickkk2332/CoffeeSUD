# Technology and science-fiction audit and proposals

Audit date: **5 November 2026 UTC**. Engine baseline:
`8df259443699f517d7380696460644d395b9c4a8` (research commit; engine code matches
`df43a4c7`). This dossier proposes work; it does not implement gameplay changes.

CoffeeSUD has a sizable inherited technology engine. It can already represent
guns, energy weapons, personal shields, tricorders, powered components,
manufacturers, spacecraft with interiors, planets, sensors, navigation software,
ship weapons and engineering work. The problem is the reliability and authoring
integration of this collection, followed by the absence of several reusable
science-fiction gameplay systems. Rebuilding everything would discard useful
foundations; treating the existing collection as finished would hide real gaps.

Read in this order:

1. [Current capabilities and limits](inventory.md): what exists, what is partial,
   what is only an enum category, and what creators can approximate today.
2. [Audit findings](audit.md): 28 findings, with evidence strength, affected code,
   impact and concrete closure criteria.
3. [Proposal catalog](proposals.md): 72 individually selectable repairs,
   extensions and new systems, with author controls, dependencies and tests.
4. [Architecture and rollout](architecture.md): ownership, time, persistence,
   optional modules, compatibility and a recommended first playable slice.
5. [Design cookbook](cookbook.md): eight original scenario designs showing how
   systems fit together, including smaller versions possible today.

The filterable [proposal catalog](proposals.csv), [finding register](findings.csv)
and [capability inventory](inventory.csv) support prioritization. A
[source manifest](sources.csv) records the paths and hashes supporting the audit.

Start by choosing R01–R12, Q01–Q03 and T01–T03. That stabilizes the existing
systems, gives worlds ownership of their technology definitions, and establishes
saved state before adding elaborate simulation. A good first expansion is a
damaged research station: terminals, access cards, a small power network, a drone,
airlocks and one repairable shuttle. Continuous interstellar physics is a later
option; a cyberpunk city or post-apocalyptic world should not need it.

## Evidence and boundaries

This is a current-code audit, rather than another survey of commercial games.
The primary sources are the repository. The older `DOCS/` analyses were used as
leads, then checked against current code; they are not a current implementation
contract. Absence claims mean no dedicated reusable system was located in the
scoped code, not that a creator cannot script a similar scene. Stock classes and
interfaces demonstrate capability, not a complete shipped science-fiction world.

Evidence labels distinguish **runtime observation**, **static defect**,
**integration gap**, **design limitation** and **test gap**. Runtime observations
cover the narrow probes actually run. For example, an item text round trip
dropping navigation state does not prove that every spacecraft save is broken.
Full campaign restore of a powered, crewed ship remains a required follow-up.
The entire engine suite was not rerun. The focused `Spaceangles` run reproduced
its known baseline failure; this work changes no engine code.

Reproduce the observations in an isolated game copy:

```sh
python docs/research/technology-scifi/run-probes.py --output /tmp/technology-audit
python docs/research/technology-scifi/verify.py
```

[Recorded observations](observations.txt) and [verification report](verification.json)
document the checks. The runner deliberately reports existing defects; successful
completion of the runner means the observations completed, not that the engine
passed their proposed acceptance criteria. It uses no external network calls.
`verify.py` checks the source snapshots against the audited commit while that commit
can be reached, and otherwise against the working tree, counting (not failing) files
changed since the audit. It requires each runtime finding's recorded probe line, not
that the defect is still present, so a repair and a re-recorded run still verify.

All example formats, new command names and new property names in the proposal
and cookbook chapters are **design sketches**, not supported APIs. Existing
commands are identified as current. The separate executions design remains
unimplemented; no proposal here silently depends on it.

See the [creator handbook](../../creator/README.md) for supported tools and the
[ARPG/MUD dossier](../arpg-mud/README.md) for adjacent optional designs. These
proposal sets should share primitives where useful, particularly item identity,
damage tags, activities, progression and diagnostics.
