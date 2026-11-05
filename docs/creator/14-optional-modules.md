# Optional modules: definitions, state and compatibility

[Handbook](README.md) · [Runnable example](examples/optional-modules/README.md)

Optional modules provide a world-owned framework for selecting features,
validating their contracts and preserving their campaign state. Nothing is
selected by default. The included practice-journal counter demonstrates the
lifecycle; ARPG supports, crafting, guild specialties and companion bonds are
still separate proposals, not implementations supplied by this framework.

## Creator workflow

1. Place flat UTF-8 JSON manifests in `world/modules/`, named exactly
   `<module-id>.json`. IDs use lower-case letters, digits, dots, underscores and
   dashes, beginning with a letter, up to 96 characters.
2. Set `MODULES` in `world/world.ini` to a comma-separated list of root IDs.
   Dependencies are selected transitively and inspection shows the roots that
   selected them. A blank/missing value selects nothing.
3. Set `MODULESETTINGS` to a JSON object of per-module setting overrides. Engine
   INI, world INI and launch overrides retain their normal precedence. Restart
   the process after changing these settings; ordinary asset reloads do not
   silently change the selected rules.
4. In builder mode or DEV=1, run `WORLD CHECK ONLY=MODULES`, then inspect
   `WORLD MODULES`. Resolve errors before ordinary play.
5. Test a normal new game, save, exit and restore. Inspect existing-campaign
   transitions with `WORLD MODULES PLAN` before applying definition reloads.
6. Ship the manifests with the world. They are tracked source assets; builder
   SAVE never generates, rewrites or deletes them.

For example, after installing the example's two manifests:

```ini
MODULES=example.counter
MODULESETTINGS={"example.counter":{"step":2}}
```

`MODULES` lists this campaign's modules for an ordinary player.
`MODULES DEMO example.counter` explicitly advances the demonstration counter for
that character. It does not affect combat, currency or progression.

## Manifest schema 1

The following is supported JSON, not proposal syntax:

```json
{
  "schema": 1,
  "id": "myworld.practice",
  "title": "Practice journal",
  "version": 1,
  "handler": "data",
  "clock_modes": ["TURNS", "REALTIME"],
  "scopes": ["character", "profile"],
  "on_disable": "suspend",
  "settings": {
    "step": {"type": "integer", "default": 1, "min": 1, "max": 10}
  },
  "state": {
    "count": {"type": "integer", "default": 0, "min": 0, "max": 1000000}
  }
}
```

| Field | Meaning |
| --- | --- |
| `schema` | Manifest format; currently integer 1 |
| `id` | Stable module identity, matching the filename |
| `title` | Human-readable name; changing it alone does not change the rule contract |
| `version` | Positive integer rule/schema version; saved campaigns record it |
| `handler` | `data` by default; `example.counter` is the demonstration; `abilities.composition` selects the registered [native skill adapter](15-ability-composition.md) |
| `requires` | Object mapping dependency IDs to exact positive integer versions |
| `conflicts` | IDs that cannot be selected together; one declaration is sufficient |
| `clock_modes` | Nonempty array drawn from `TURNS`, `REALTIME` |
| `scopes` | Nonempty array of allowed state-owner scopes |
| `settings` | Typed configuration fields and defaults |
| `state` | Typed fields/defaults for every owner record |
| `on_disable` | `suspend` by default, or `refuse`; stored in the save |
| `provides` | Array of named exports for cross-definition reference validation |
| `references` | Array of `{ "module": "id", "name": "export" }` references |
| `migrations` | Explicit version-transition steps, described below |

Unknown manifest/field properties, duplicate JSON keys, trailing data, unknown
schema versions, invalid IDs, dependency cycles and malformed definitions are
errors. Uninstalled conflict targets are allowed; they are not dependencies.
Every installed manifest is checked, including disabled definitions. A malformed
draft therefore belongs outside `world/modules/` until it is valid. Builder and
CHECK=1 runs remain available to diagnose errors; ordinary startup refuses an
invalid module library or selection.
When deleting a definition, also remove configuration roots and setting overrides
that refer to it; dangling configuration IDs remain validation errors. A valid
world selection can still restore the removed module's saved capsule as suspended.

Dependencies require exact versions rather than assuming that a newer version
is compatible. Root and dependency selection is independent of installation:
adding a manifest alone never enables it. Conflicting roots or a conflicting
transitive dependency are errors. Setting overrides for unknown modules/fields
are errors rather than ignored typos.

A reference must resolve to an export in the same module or a declared dependency.
These are module-level named contracts. An export does not create an ability,
recipe or dungeon by itself; those future feature implementations must supply
and validate their own actual content. The current loader does not claim to
compile unimplemented ARPG content formats.

## Field types and budgets

Settings and state support these deliberately small, predictable schemas:

- `integer`: signed 64-bit whole numbers; optional inclusive `min` and `max`.
- `boolean`: JSON `true` or `false`.
- `string`: at most 4096 characters; optional `max_length` and exact `choices`.

Every field requires a valid `default`. Bounds/choices must match its type.
Missing fields receive their declared defaults; unknown supplied fields are
rejected. A record write replaces one complete validated record atomically.
Reads return copies and do not create persistent records.

Limits are 256 manifests, 256 KiB per manifest, 128 settings and 128 state fields
per module, JSON nesting depth 32, 10,000 total campaign state-owner records,
and 8 MiB of serialized record data. Module snapshots have an additional total
size limit. Limits produce errors; they do not truncate saved records. Inspection
may truncate a long display while preserving the complete saved data.

Nested arbitrary state objects, executable migration scripts and arbitrary
handler class names are not part of schema 1. Trusted Java/JavaScript extensions
can use the state API and explicitly guard their behavior with module activation.

Native consumers can require an engine capability in their saved capsule.
`abilities.composition` writes `requires_handler`, retained even when suspended
or its definition is removed. Unsupported handlers refuse restoration before
objects/effects load. Older readers reject the capability field rather than
silently dropping native abilities. Data-only capsules retain their original
shape and compatibility.

## State ownership and extension APIs

Use existing persistent instance references for characters/items. Do not use
names such as “the sword” as identities. The supported scopes are:

| Scope | Owner factory | Sharing |
| --- | --- | --- |
| `world` | `OptionalModules.world()` | One record per module/campaign |
| `character` | `OptionalModules.character(mob)` | Independent character/NPC instance |
| `profile` | `OptionalModules.profile(mob)` | Characters on the same profile |
| `item` | `OptionalModules.item(item)` | One item instance |
| `relationship` | `OptionalModules.relationship(first, second)` | Ordered pair of participants; reversing roles is a different record |
| `organization` | `OptionalModules.organization(clan)` | Existing internal organization ID |

`owner(scope, persistentKey)` is for adapters that already have a stable key.
It validates the scope/key form. It does not resolve a missing actor or recreate
a deleted item. Relationship records remain stored when a participant disappears;
a future relationship feature must define separation/death/recovery behavior.

For a trusted Rhino extension running in the normal engine context:

```javascript
var Modules = Packages.com.planet_ink.coffee_mud.core.OptionalModules;
if (Modules.enabled("myworld.practice")) {
    var owner = Modules.character(mob());
    var progress = JSON.parse(String(Modules.getJSON("myworld.practice", owner)));
    progress.count += 1;
    Modules.putJSON("myworld.practice", owner, JSON.stringify(progress));
}
```

`setting(id, key)` reads the campaign's effective typed setting. `get`/`put`
accept `MiniJSON.JSONObject` values for Java extensions; use Long values for
integer fields. `getJSON`/`putJSON` avoid accidental JavaScript floating-number
wrappers. Individual record replacement is atomic, while a custom multi-call
read/modify/write operation still belongs on the game thread or in an extension's
own coordinated action. This is not the later multi-item crafting transaction
system.

A disabled, incompatible or unsupported-clock module cannot read/write its
active records through these APIs. No background ticker is installed by the
module framework or the example counter. In an unsupported clock mode a module
is dormant, with a reason shown by inspection; it becomes available again when
the clock is supported. GameTime and PAUSE retain their existing behavior.

All state is kept in the campaign save. No module campaign state is written to
source manifests or installation-wide resource files. Template/item copy rules
remain those of persistent instance identity; intentionally shared state must
use an explicit shared scope.

## Existing campaigns and settings

A new campaign adopts the world's selected modules. A restored campaign adopts
only the modules already recorded in that save. This includes legacy saves with
no module metadata: enabling modules in a world's configuration does not force
them onto an old campaign.

A creator can explicitly add a world-selected module to a campaign:

```text
WORLD MODULES ADOPT example.counter
```

The command is restricted to builder/developer tooling, respects dependencies,
and does not reset suspended data to evade a failed migration. Ordinary players
cannot activate arbitrary installed modules or override the world selection.

Effective settings are saved and pinned to the campaign. Changing world defaults
supplies values for new campaigns/adoptions; it does not silently rewrite saved
builds. A creator can explicitly change a compatible active campaign setting:

```text
WORLD MODULES CONFIGURE example.counter step 3
SAVE mycampaign
```

This validates the new value, changes the current campaign only, and leaves
world defaults alone. String settings take the command's remaining text;
integers/booleans use JSON literals. Use file-based `MODULESETTINGS` for world
defaults. `WORLD MODULES` shows source settings provenance and saved values.

## Rule versions and migrations

Saves record each selected module's rule version, canonical contract fingerprint,
effective settings, removal policy and owner records. A title change is harmless.
Changing another contract field without increasing `version` suspends or refuses
that saved module. Whitespace and JSON object-key order do not change the
fingerprint; array order is part of the recorded contract.

A version increase needs an explicit migration path even when the state shape
stays the same. Use an empty step to acknowledge a compatible transition:

```json
"migrations": [{"from": 1, "to": 2}]
```

Rename fields without losing their values:

```json
"migrations": [{
  "from": 1,
  "to": 2,
  "state_renames": {"count": "total"},
  "setting_renames": {"step": "increment"}
}]
```

These are fragments inside a manifest. Update its version and field schemas at
the same time. Update world-setting override names to the new schema before
restarting; saved setting names follow the declared migration. Steps must move
forward, have one route per starting version and
end at the installed version. Every owner and setting is staged and validated
before that module commits. Conflicting rename destinations, invalid values,
unsupported owner scopes and unknown leftover fields reject the migration.
New fields receive their valid defaults. Silent field deletion is not supported;
keep the field or explicitly rename it into the new schema.

A failed migration preserves the original version, settings and records.
Repeated restores of an already migrated save do not apply its migration again.
A dependent module is suspended/refused if its dependency is not active. New
world-selected dependencies are not silently added to old saves; explicit
adoption revalidates the complete selected dependency closure.

## Disabling, removing and reloading

The saved policy governs the transition, even if its source manifest disappears:

- **`suspend`:** preserve the module capsule and its records in subsequent saves,
  disable access to active state, and report the reason. Re-enable a compatible
  definition to resume those records.
- **`refuse`:** refuse the restore/reload before replacing the running module
  campaign or starting a working game. The original save remains available.

Schema 1 does not offer a destructive “drop all data” switch or generic refunds
for future gameplay entities. A future feature that creates abilities, items or
long-running jobs must implement its lifecycle against this activation contract;
its ongoing effects cannot be made safe merely by storing a manifest.

Use these commands in builder mode or DEV=1:

```text
WORLD MODULES
WORLD MODULES example.counter
WORLD MODULES CHECK
WORLD MODULES PLAN
WORLD MODULES RELOAD
WORLD MODULES STATE example.counter
```

CHECK reads/validates definitions and the startup selection without changing
anything. WORLD CHECK ONLY=MODULES also checks compatibility with the current
clock. PLAN stages definition changes against current saved module state and
reports results without committing. RELOAD applies the same validated plan;
it preserves existing campaign settings and acknowledges only module source
files actually covered by the reload. It does not reload unrelated world assets
or change startup selection/settings. STATE inspects a saved capsule, including
suspended data, and remains restricted creator tooling.

Normal startup and in-game RESTORE preflight compatibility before replacing
campaign state. New module saves participate in the existing atomic save-slot
write, with a format marker so a missing module file is an error rather than
mistakenly treated as a legacy save.
Campaigns containing module capsules use outer save format 2 (module payload
schema 1). Earlier format-1-only engines reject these saves before restoration.
Campaigns without modules retain save format 1, and this engine reads both.

## Verification and remaining scope

```sh
tools/fixes.sh optional-modules
tools/fixes.sh modules
```

The focused suite tests schemas, dependencies, conflicts, references, state
isolation, Unicode, pinned settings, migrations, suspension/refusal and clocks.
The played suite exercises normal-player use, old-save adoption, actual
fresh-process restore, definition migrations, preserved suspended payloads,
refusal without source-save mutation and separate-world isolation.

This foundation supports later feature-specific validators and lifecycle
adapters. It does not yet provide skill supports, loot affixes, progression
trees, arbitrary asset migration, executable migration code, or item/reward
transactions. Those remain separately selectable work from the research catalog.

Implementation: [ModuleDefinitions.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/ModuleDefinitions.java),
[OptionalModules.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/OptionalModules.java),
and [SaveGame.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/save/SaveGame.java).
Regression sources: [focused fixtures](../../tools/fixtures/fixes/optional-modules.js)
and [played integration harness](../../tools/modules-integration.py).
