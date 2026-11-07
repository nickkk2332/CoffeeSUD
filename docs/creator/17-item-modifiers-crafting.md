# Optional tracked modifiers and selective crafting

Stage 5 extends existing affixes, generation, loot tables and workstations with
per-item modifier records and controlled item transactions. It is off by default.
The [Ember Trial](examples/ember-trial/README.md) includes a supply coffer and
workbench that demonstrate it with ordinary characters.

The existing tools remain useful: ordinary `AFFIX`/`AFFECT` definitions, weighted
loot, recipes, quality, salvage and upgrades retain their native behavior. This
slice adds individually editable numeric modifiers; it does not replace those
tools or promise every PoE crafting operation.

## Play the workbench example

Create a fresh deployment using the sample's factory and launch it normally:

```sh
python3 docs/creator/examples/ember-trial/make_world.py /tmp/sud-forge
python3 docs/creator/examples/ember-trial/play.py /tmp/sud-forge
```

At camp:

```text
OPEN supply coffer
GET "trial mantle" coffer
GET "cinder iron" coffer
ITEMMODS trial mantle
MODCRAFT LIST
MODCRAFT POOL "trial mantle"
MODCRAFT PREVIEW embertrial.reroll "trial mantle" 1
MODCRAFT PREPARE embertrial.reroll "trial mantle" 1
MODCRAFT STATUS
MODCRAFT COMMIT 1
ITEMMODS trial mantle
SAVE forge
```

ITEMMODS numbers the current modifiers and shows their rolls, tier, group,
protection and definition source. PREVIEW checks the operation and lists costs
and roll ranges. LIST shows the latest operations with costs and stations; POOL
lists this item's affix IDs, ranges, groups and reasons for blocked additions.
PREPARE creates one pending request for this character. Neither
draws random values or spends materials. COMMIT revalidates it, rolls the selected
values, consumes the cost and records its receipt. A reroll can draw the same
value again. Quote multiword item names in PREVIEW/PREPARE.

Use the number printed by PREPARE; it increases across committed and cancelled
requests. `MODCRAFT CANCEL 1` cancels without payment or random draws, also
while the campaign has crafting switched off (`allow_crafting false`); while
the module itself is suspended, a pending request keeps its saved state until
the module is active again. COMMIT or RECEIPT of the last cancelled request
says it was cancelled. `MODCRAFT RECEIPT 1` reads the committed receipt.
Repeating COMMIT 1, including after a fresh-process restore, cannot charge or
apply it twice.

Protect a modifier with `embertrial.protect`. Its reroll/removal then refuses
without payment. `embertrial.unprotect` explicitly releases it; `embertrial.remove`
removes one modifier. `embertrial.add` takes an **affix ID**, rather than a modifier
number, for example:

```text
MODCRAFT PREPARE embertrial.add "trial mantle" embertrial.fireguard1
```

It succeeds only when the base/operation pools, level, groups and capacities
permit it. The two fireguard tiers share a group, so they cannot coexist even
if a prefix slot is spare. This is why a free slot alone does not guarantee a
legal craft. Each sample operation costs ordinary IRON weight units; “cinder
iron” is the sample's descriptive name, not a new currency type.

Wear the mantle to use its recorded stats in composed combat. REMOVE it before
crafting. `COMBATRESULT STATS` and resolved combat results identify the rolled
stat sources. Its bonuses use the [profile pipeline](16-combat-profiles.md);
ordinary native attacks/spells retain their existing rules.

## Install in another world

Use a separate deployment and install the parent ability-composition and
combat-profile packs. Merge the [item-modifier pack](examples/item-modifiers/README.md)
into its world: `modules/`, `itemmods/`, `js/` and `resources/`. Merge the tracked
entries into an existing `affixes.xml` rather than discarding its legacy entries.

```ini
MODULES=items.modifiers
```

Its native handler depends on combat.profiles, which depends on
abilities.composition. Installing files alone activates nothing. New campaigns
adopt the selected closure. Existing campaigns require explicit
`WORLD MODULES ADOPT items.modifiers` after world selection. Installing this
module grants no skills or items; author a generator, reward and workstation.
MODBASE generation requires the active module and definitions that pass CHECK,
and runs on the game thread, or while the world loads at boot, before that
thread starts (so a mob's ON=SPAWN loot is made as it first comes to life).
Called from anywhere else, such as a script's own Java thread, it is refused. A
loot-table roll that gives nothing because its tracked items cannot be made for
one of these reasons is not spent: a container stays unrolled (no ROLLED=TRUE)
and rolls at its next opening, a mob's ON=SPAWN roll is made as it dies, into
its corpse, and an ON=DEATH roll stays open. The log names the reason. The
pack's [coffer script](examples/item-modifiers/js/props/itemmods-loot-guard.js)
also tells the player why a coffer waits while the module is disabled: attach
it with Prop_JScript SCRIPT=world:js/props/itemmods-loot-guard.js, as the sample
does. A script's own GenCalls call gets the refusal as its error and needs its
own fallback.

```text
WORLD MODULES CHECK
WORLD ITEMMODS LIST
WORLD ITEMMODS CHECK
WORLD CHECK ONLY=ITEMMODS
GENERATE CHECK randareas/embertrial-loot.xml
```

WORLD ITEMMODS requires builder/DEV or the existing room-creation permission.
Players inspect their own carried equipment with ITEMMODS. WORLD CHECK also
compares loaded records' base/affix contracts and rolled ranges with their pins.
The native module
manifest validates its state/settings contract, and saves retain
`requires_handler: items.modifiers` even if disabled or missing its definition.
Engines without that handler refuse the save before restoring native objects.

## Extend the existing affix file

Tracked entries use the same `world/affixes.xml` as ordinary affixes:

```xml
<AFFIXES>
  <AFFIX ID="myworld.fireguard" NAME="Wardwoven" POS="prefix" KINDS="armor"
         LEVEL="1-99" PICKWEIGHT="10" POWER="0" TRACKED="true" VERSION="1"
         TIER="1" GROUP="fire-resistance" TAGS="defense,elemental">
    <ROLL STAT="resistance.fire" MIN="10" MAX="20" />
  </AFFIX>
</AFFIXES>
```

ID/version/hash identify a released definition. TIER is authored metadata,
not an automatic level calculation. GROUP is exclusive across all modifiers on
that item. POS chooses prefix/suffix capacity; KINDS and LEVEL retain the old
eligibility meanings. PICKWEIGHT controls generation selection. TAGS supplies
craft filters. Integer ROLL endpoints are inclusive. Constant ranges draw no
random value. At most eight numeric rolls belong to an affix.

Rollable paths are accuracy, evasion, armor, block, spell_block, crit_chance,
crit_multiplier, and resistance.TYPE / penetration.TYPE for the five profile
types. They use the existing stat-delta limits. Multiple modifiers sum as deltas
before the profile's aggregate clamps; no absolute default critical multiplier
is added per affix. `crit_multiplier: 50` adds 50 points to the profile default.

This first tracked family requires POWER=0, no curse and no AFFECT children.
Arbitrary attached effects cannot yet be selectively owned/removed safely.
Ordinary untracked affixes continue to support their existing powers, curses
and effects. Tracked entries are selected by MODBASE; legacy affix/enchantment
consumers do not silently treat them as ordinary name-only affixes.

## Declare an item modifier contract

Flat `world/itemmods/<id>.v<version>.json` files declare bases and operations.
A base wraps an existing generated item rather than replacing item definitions:

```json
{
  "schema": 1, "kind": "base", "id": "myworld.mantle", "version": 1,
  "name": "Mantle modifier contract", "item_kind": "armor",
  "prefixes": 2, "suffixes": 1, "tags": ["cloth"],
  "pool": ["myworld.fireguard"]
}
```

Item kinds are armor, weapon, ammo and item. Each capacity is 0–3, with at most
six modifiers total. The pool references tracked affix IDs. New generation uses
the latest installed versions and stores their exact pins. The group rule can
make the legal count smaller than the raw capacity.

Use the existing XML item generator and loot tables:

```xml
<ITEM ID="myworld_mantle" CLASS="GenArmor" LEVEL="1" NAME="a trial mantle"
      DISPLAY="A trial mantle lies here." MATERIAL="COTTON" WEIGHT="1"
      PROPERWORN="body" MODBASE="myworld.mantle" AFFIXES="2" />
```

MODBASE opts the template into tracked generation; like AFFIXES, its value may
use the generator's variables (`MODBASE="$mantle_base"`). AFFIXES asks for up to that
many eligible modifiers. The underlying item, loot-table trigger, container
once-roll policy and seeded generation remain the existing systems. Without
an enclosing seed, generation consumes the campaign dice stream. An ITEM SEED
covers tracked selection and value rolls while restoring that stream afterward.
Instance/modifier UUIDs remain fresh even when numeric rolls repeat. Without
MODBASE, AFFIXES retains the legacy path. Standard GenArmor, GenWeapon, GenItem
and GenAmmunition are supported. Item kind must agree with its base contract.

The saved property contains the item instance ID, base pin, revision, origin,
display baseline/overrides, and each modifier's UID, pin, tier, group, tags,
position, values and protection. Removing/rebuilding one modifier preserves
the others' identities and values. An external item rename remains separate
from modifier identity. There is no requirement to recover modifiers from
the displayed name.

For trusted native extensions, `ItemModifiers.initialize` initializes a fresh
unowned generated item. `ItemModifiers.copyOf` copies a managed item with fresh
item/modifier identities while keeping its rolls. Both run on the game thread
(or at boot, as MODBASE does). Direct generic cloning (a rejuvenated or
RandomItems copy) is not the managed-copy API: such a copy shares the record
but is not the item it is bound to, so it grants no bonuses, AFFIXES adds
nothing to it, and WORLD CHECK ONLY=itemmods names it. Generation addition is
refused for owned or already crafted items. Ordinary gameplay uses
transactions for edits.
For catalog generation, keep the prototype untracked and apply MODBASE to each
fresh generated copy. Publishing an already managed instance as a prototype
does not make generic catalog cloning the managed-copy API.

## Declare an operation

```json
{
  "schema": 1, "kind": "operation", "id": "myworld.reroll", "version": 1,
  "name": "Temper one modifier", "action": "reroll", "station": "ember-forge",
  "material": "IRON", "units": 1,
  "item_tags": ["cloth"], "modifier_tags": ["defense"], "affixes": []
}
```

Actions: add, remove, reroll, protect, unprotect, replace and upgrade, and the
two that draw their modifiers, imbue and reforge
([targeted crafting](#targeted-crafting-imbue-reforge-and-catalysts)).
Item/modifier tag lists require all listed tags. An add or replace operation's
nonempty affixes list further restricts the base pool; an empty list uses the
whole base pool. Remove, reroll, protect and unprotect target one modifier UID
resolved from its current displayed number when preparing.

- **replace** takes a modifier number and the affix to put in its place:
  `MODCRAFT PREPARE workbench.replace "workbench cloak" 2 embertrial.piercing`.
  The old modifier is removed and the new one added, rolled, in one settlement
  and one revision. The new affix must fit once the old one is gone: its group,
  its position's capacity, its level and the pools. A protected modifier
  cannot be replaced. `modifier_tags` apply to both the old and the new.
- **upgrade** takes no selector: `MODCRAFT PREPARE workbench.upgrade "workbench cloak"`.
  It moves the item to its base's declared `successor`, keeping every modifier,
  its rolls, identity and protection. The successor must be for the same item
  kind, hold every modifier's affix in its pool, and have room for them.

A base declares its successor with an optional field:

```json
{"schema": 1, "kind": "base", "id": "workbench.cloak", "version": 1, "name": "...",
 "item_kind": "armor", "prefixes": 1, "suffixes": 1, "tags": ["cloth", "workbench"],
 "pool": ["embertrial.fireguard1", "embertrial.fireguard2", "embertrial.piercing"],
 "successor": "workbench.greatcloak"}
```

**A refusal says why.** PREVIEW and PREPARE explain an invalid target: "modifier
is protected: Wardwoven, group fire-resistance; unprotect it first", "exclusive
group fire-resistance already present: Wardwoven (protected)", "no such modifier
number", "workbench.greatcloak.v1 declares no successor to upgrade to", "need 2
weight unit(s) of plain IRON in your inventory". Each ends "Nothing was spent or
drawn": a refused validation consumes nothing and draws no random value.

## Staged recipes

A staged recipe makes a new item in stages, each settled on its own, its progress
kept on a work piece:

```json
{
  "schema": 1, "kind": "recipe", "id": "workbench.cloak-work", "version": 1,
  "name": "Make a woven cloak", "station": "ember-forge", "piece": "an unfinished cloak",
  "stages": [
    {"name": "Spin the cloth", "material": "COTTON", "units": 2},
    {"name": "Stitch the hem", "material": "COTTON", "units": 1},
    {"name": "Rivet the clasp", "material": "IRON", "units": 1}
  ],
  "result": {"catalog": "a woven cloak", "base": "workbench.cloak", "modifiers": 1}
}
```

| Field | Meaning |
| --- | --- |
| `station` | The workstation tag every stage needs. |
| `piece` | The work piece's name. |
| `stages` | 1 to 6, each a `name`, a registered `material` and its `units`, paid as an operation's cost is. |
| `result` | `catalog`: the catalog item made (a GenArmor, GenWeapon, GenItem or GenAmmunition), and either `base` and `modifiers` (tracked generation, as MODBASE and AFFIXES) or `policy` (a [loot policy](40-loot-policies.md)). |

```text
MODCRAFT BEGIN workbench.cloak-work
MODCRAFT PREVIEW workbench.cloak-work "unfinished cloak"
MODCRAFT PREPARE workbench.cloak-work "unfinished cloak"
MODCRAFT COMMIT
MODCRAFT STATUS
```

BEGIN, at the station, sets out the work piece (`Prop_WorkOrder`, which holds
the recipe's pin and the stage reached); it spends and draws nothing. Each
PREPARE and COMMIT settles the next stage, through the same request ledger as
the operations: the stage's materials are paid once, the piece moves on, and a
repeated COMMIT cannot pay or advance it again. The piece keeps its progress in
the save. The last stage makes the product, gives it to the crafter and takes
the piece away; it is the only stage that draws random values (the product's
modifiers). STATUS lists the work pieces you carry and each one's next stage.
The recipe is pinned when the piece is set out, so a newer version of it does
not change a piece already begun.

Each settled stage fires `itemCrafted` with its receipt, whose `stage` field
says which. As for operations, a listener sees the settled state; returning
false, throwing or calling COMMIT again does not repeat the stage, and it cannot
start another craft.

Station uses the existing `Workstations.has` rule: a room/item Prop_Workstation
with the tag, or an appropriately tagged top-level room item. This slice uses
the station's presence; time/proficiency/quality bonuses from native recipe
crafting do not alter these instantaneous operations.

Cost is 1–1,000 weight units of a registered raw material, using its exact uppercase
name, including spaces where present (for example WHITE GOLD). CHECK diagnoses
unknown material names; an enum spelling with underscores is not a space alias.
The first transaction
consumer accepts directly carried, unworn **plain GenResource piles**, with no
scripts, behaviors or effects beyond identity metadata. It supports partial
piles and complete consumption, preserving proportional base value. A partly
spent pile whose name shows its weight (“an 8# iron ore bundle”) is renamed for
what is left, as native crafting does; an authored name (“some cinder iron”)
is kept. At most
eight piles pay one operation. Decorated/enchanted resources, other currencies,
multi-ingredient recipes, timed work and arbitrary scripts are outside this
controlled commit family. Existing crafting skills still handle their normal
recipes and materials.

## Targeted crafting: imbue, reforge and catalysts

Two actions draw their modifiers instead of being told which:

- **imbue** adds one modifier, drawn by weight among the base's pool;
- **reforge** draws again every unprotected modifier (as many as there were),
  keeping the protected ones as they are.

Each pick is drawn among the affixes the item can hold now (kind, level,
group and slot), within the operation's `affixes` allow-list and with its
`modifier_tags`. Three fields shape the draw:

| Field | Meaning |
| --- | --- |
| `exclude_tags` | Affixes with any of these tags cannot be drawn. |
| `weights` | A multiplier (0.01 to 100) for affixes with a tag. An affix with several weighted tags multiplies them all, and so do the operation and each catalyst. |
| `guarantee` | A tag: the first modifier drawn must have it. If no affix with it can be drawn, the craft is refused. |

An operation can carry them itself, and it names the **catalysts** it takes.
A catalyst is a definition of its own: an ingredient with a cost and the same
three fields.

```json
{"schema": 1, "kind": "operation", "id": "workbench.imbue", "version": 1,
 "name": "Weave in a drawn modifier", "action": "imbue", "station": "ember-forge",
 "material": "IRON", "units": 2, "item_tags": ["workbench"], "modifier_tags": [], "affixes": [],
 "catalysts": ["workbench.coal", "workbench.ash", "workbench.amber"]}
```

```json
{"schema": 1, "kind": "catalyst", "id": "workbench.coal", "version": 1,
 "name": "Forge coal", "material": "COAL", "units": 1, "weights": {"offense": 4}}
```

The player names catalysts after WITH, at most three, each once:

```text
MODCRAFT PREVIEW workbench.imbue "plain workbench cloak" WITH workbench.coal
MODCRAFT PREPARE workbench.imbue "plain workbench cloak" WITH workbench.ash, workbench.amber
MODCRAFT COMMIT
```

A catalyst is paid with the craft, in the same settlement: its material and
units are added to the operation's (both IRON and COAL here), and the request
and its receipt keep the catalysts' pins. Two guarantees of different families
cannot both hold, and a guaranteed family cannot also be excluded: such a craft
is refused.

**The preview shows the draw.** PREVIEW lists the distribution and every affix
of the pool, with its chance or why it cannot be drawn:

```text
Distribution: offense x4 (catalyst workbench.coal)
The modifier drawn:
  embertrial.fireguard1 (Wardwoven, prefix tier 1): 19.2%
  embertrial.fireguard2 (Flameward, prefix tier 2): 3.8%
  embertrial.piercing (Embers, suffix tier 1): 76.9% (weight 10, offense catalyst workbench.coal x4)
```

A reason reads, for example, "tag offense is excluded by catalyst
workbench.ash", "not of the guaranteed defense family (catalyst
workbench.amber)" or "exclusive group fire-resistance already present:
Wardwoven". For a reforge, the first pick is shown; later picks leave out the
groups and slots the earlier ones took.

**An empty pool is refused before paying.** When nothing can be drawn (every
slot or group is taken, or every affix that fits is excluded), or when the
guarantee cannot hold, PREVIEW and PREPARE refuse with that list, and nothing is
spent or drawn. A reforge of an item whose modifiers are all protected is
refused too.

**A recipe takes catalysts at its last stage**, the one that draws the product.
Its `catalysts` field lists the ones it takes; naming one at an earlier stage is
refused. The last stage's PREVIEW shows the product's first modifier, with or
without catalysts. A recipe whose result draws no modifiers (`"modifiers": 0`)
cannot list catalysts. For a policy result, the rarity still decides how many
modifiers the product gets: a catalyst shapes them but adds none, so a rarity
that gives none gives none.

The workbench pack has the example: `workbench.imbue`, `workbench.reforge`
(which leans to defense itself, `"weights": {"defense": 2}`), the catalysts
`workbench.coal` (offense x4), `workbench.ash` (excludes offense) and
`workbench.amber` (guarantees defense), and the recipe `workbench.ward-cloak`.
Test: [i06-targeted.js](../../tools/fixtures/fixes/i06-targeted.js).

## Requests, atomicity and persistence

An item must be directly owned, unworn and outside a container. The crafter must
be alive, outside combat, at the station. PREPARE pins the operation and captures
item/material fingerprints, room, actor and selected modifier identity. It does
not reserve objects. Trading, wearing, editing or spending inputs can make a
request stale; COMMIT rechecks and refuses before rolling/spending when relevant
state differs. CANCEL and prepare again to accept changed inputs. A new add-affix
version installed while a request is pending requires fresh preparation.

One request may be pending per character. Pending requests, serial/settlement
counters and bounded receipt details save together with items/materials. Old
receipt details may be pruned to fit eight entries / 4,096 characters, but the
settlement watermark continues to refuse old commits. Save rollback restores
the whole earlier campaign snapshot, including its costs, receipts and dice.
This is once-only settlement relative to saved state, not external crash-durable
banking or a guarantee across manually combined save files.

Commit runs on the game thread with controlled property/resource mutations.
SaveGame and WorldState refuse snapshots inside its critical mutation section.
Item-lost notifications wait until the receipt and item/material changes settle;
consumed objects are destroyed before advisory listeners run. The post-commit
event is:

```javascript
cm.on('itemCrafted', function (who, item, receiptJSON) {
    var receipt = JSON.parse(String(receiptJSON));
    cm.tell(who, 'The forge records craft #' + receipt.request + '.');
}, 'myworld.forge-notice');
```

Register listeners at boot. The event sees settled state. Returning false or
throwing does not roll back or repeat the craft. A listener can inspect/save
the completed state; it cannot start another crafting mutation reentrantly.
Retrying the same settled request does not emit the event again. Arbitrary
creator callbacks and external I/O are not a general rollback transaction.

## Updates and controls

```text
WORLD ITEMMODS RELOAD
WORLD MODULES CONFIGURE items.modifiers allow_crafting false
WORLD MODULES CONFIGURE items.modifiers max_modifiers 3
```

RELOAD accepts valid definitions and their file provenance together. Failed
reloads preserve the old accepted library. CHECK reads disk without accepting
it. The general WORLD RELOAD ASSETS path does not silently acknowledge this
family. Validation limits: 128 base/operation files, 64 KiB each, a 256 KiB world
affix file, six modifiers/item, and 32,768 characters per saved item record. Unknown fields,
unsupported stat paths and symlinked definition files/directories are refused.
These are errors once the world uses the family (it selects items.modifiers,
the campaign has it, or the world has `itemmods/`). A world that never opts in
keeps reading its legacy `affixes.xml` as before: WORLD CHECK does not hold it
to the size/link rule and reports tracked-entry problems as warnings only.

Keep released JSON versions and XML affix versions installed. XML affixes may
share an ID with different VERSION values; ID/version/hash identify each pin.
Even a name/weight edit changes that version's canonical definition hash.
Missing/changed pins suspend crafting; existing saved numeric rolls continue to
apply while the module is active. Merely adding newer versions never rerolls
existing gear. Creator migrations for changing a released item's contract remain
explicit work; no automatic reinterpretation is performed.

Disabling the module suspends its bonuses/crafting and retains records and
settlement. Re-enabling restores frozen rolls. Lowering the modifier limit
withholds an over-limit item's bonuses: the log and WORLD CHECK ONLY=itemmods name
it, COMBATRESULT shows the source as withheld, and its wearer's composed combat
goes on without it. A corrupt record is treated the same way. Unequip and remove
enough modifiers, or restore the limit. Removal/unprotect paths remain available
on unworn over-limit items. Changing campaign settings does not edit world
defaults; use MODULESETTINGS for future-campaign defaults.

The sample adventure manifest is version 2 with an identity migration for its
existing progress fields and a new declared dependency. A new deployment is the
simple way to try the forge. Existing Stage 4 campaigns need explicit dependency
adoption; restoring an old room snapshot does not inject new coffers or reset
defeated enemies. Updating campaign content is separate from module adoption.

## Verification and remaining scope

```sh
tools/item-modifier-tests.sh
tools/fixes.sh item-modifiers i04-workbench g6-loot g10-affixes
tools/fixes.sh itemcraft
tools/fixes.sh trial
python3 tools/creator_docs.py --check
```

Pure tests cover selective transforms, groups, protection, bounds and pins. Live
tests inspect real inventory/costs, source removal, native combat collection,
deferred observers, stale requests, cloning and legacy isolation. Played tests
use ordinary player commands across fresh-process requests, settlement, locks,
loot reuse, module suspension/reactivation and new campaigns.

Rarity policies are [chapter 40](40-loot-policies.md); escrowed rewards are
[chapter 36](36-reward-escrow.md). Further work includes independently owned
arbitrary effect layers, crafting currency families, staged choice reveals,
corruption and recombination. These are separate contracts, not behavior implied
by this numeric modifier family.
