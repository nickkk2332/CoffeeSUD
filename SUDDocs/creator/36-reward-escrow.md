# Reward escrow: provisional, banked and claimed rewards

[Handbook](README.md) · [Example pack](examples/reward-escrow/README.md)

The optional module `rewards.escrow` keeps what a character earns in an
activity (a trial, an encounter, an expedition) apart from what they own,
until it is settled. A reward is **provisional** when it is earned, **banked**
once it is safe, **forfeited** when it is lost, and **claimed** when it is
taken. Leaving, failing, dying and restoring each do what the activity
declares, and a reward is claimed once, whole.

## Turn it on

1. Copy [the manifest](examples/reward-escrow/modules/rewards.escrow.json) into
   `world/modules/` and your definitions into `world/rewards/`.
2. Add `rewards.escrow` to `MODULES`, run `WORLD CHECK ONLY=MODULES,REWARDS`,
   and start a new game (or `WORLD MODULES ADOPT rewards.escrow`).

The manifest is native: it allows the `character` scope, and its state has the
string fields `ledger`, `runs` and `keys` (4096 characters, empty to start).

## Definitions

`world/rewards/<id>.v<version>.json` holds two kinds of definition.

A **bundle** is what a reward gives:

```json
{"schema": 1, "id": "example.room-one", "version": 1, "title": "Spoils of room one",
 "kind": "bundle", "items": [{"name": "a trial token", "count": 2}], "money": 50,
 "resources": {"example.cinder": 1}}
```

| Field | Meaning |
| --- | --- |
| `items` | Up to 16 catalog items by name, each with a `count` (1 to 20, default 1). |
| `money` | A sum in the character's currency, 0 to 1,000,000,000. |
| `resources` | [Named resources](34-named-resources.md) gained, which need `abilities.resources`. |

A bundle gives at least one of them.

An **activity** says what happens to its provisional rewards:

```json
{"schema": 1, "id": "example.trial", "version": 1, "title": "The trial of three rooms",
 "kind": "activity", "on_leave": "forfeit", "on_fail": "forfeit", "on_death": "forfeit",
 "on_restore": "keep", "claim_rooms": ["Camp#0"]}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `on_leave` | `forfeit` | When the character leaves the activity's area (marked with `Prop_RewardActivity`). |
| `on_fail` | `forfeit` | When the content declares the run failed. |
| `on_death` | `forfeit` | When the character dies. |
| `on_restore` | `keep` | When a save made during a run is restored. |
| `claim_rooms` | anywhere | Rooms where its banked rewards may be claimed. |

Each policy is `forfeit` (provisional rewards are lost and the run ends),
`bank` (they are banked and the run ends) or `keep` (nothing changes; the run
goes on).

## Runs and keys

A character takes part in an activity in **runs**. A run begins with the first
reward offered in it (or explicitly), and ends when it is completed, failed or
left, or when the character dies, unless the policy is `keep`.

Every reward is offered with an **event key**, such as `room-one`. Within one
run, a key gives one entry: playing the same completion again, after a restore
or not, offers nothing. A new run may earn the same key again (a fresh attempt
at the trial). The ledger remembers the last 96 keys.

## Claiming

`REWARDS CLAIM ALL` (or a number) claims banked rewards. Everything a claim
gives is prepared first: each reward's pinned definition, every catalog item,
every named resource. If one is missing, the claim gives nothing and says why.
Then the rewards are marked claimed and the items, coin and resources are given
in the same step, so a claim cannot be taken twice, not even across a save and
restore. A claim made in a room the activity does not allow is refused.

## Parties

Each character has their own ledger. The content offers a reward to each member
of a party who is there (`offerParty`), so a party shares a completion, while
each character claims their own reward. Two characters on one profile do not
share rewards.

## Commands and scripts

| Command | Who | What it does |
| --- | --- | --- |
| `REWARDS` | players | Runs under way and every reward: its number, state, what it gives, its run. No game time. |
| `REWARDS CLAIM [number/ALL]` | players | Claims banked rewards. |
| `WORLD REWARDS LIST` / `CHECK` / `RELOAD` | builder, DEV=1 | Definitions; problems (a catalog item, pool or claim room that is not there); a fresh read. |
| `WORLD REWARDS SHOW [character]` | builder, DEV=1 | A character's ledger. |
| `WORLD REWARDS OFFER activity reward [key]`, `BANK activity`, `END activity outcome` | builder, DEV=1 | Test the policies on yourself. |

A trusted JavaScript extension (or a director, chapter 37) drives a run:

```javascript
var E = Packages.com.planet_ink.coffee_mud.core.RewardEscrow;
E.offer(mob(), "example.trial", "example.room-one", "room-one"); // provisional; 0 if the key was rewarded
E.bank(mob(), "example.trial");                                   // banks the run's provisional rewards
E.end(mob(), "example.trial", "complete");                        // or "fail", "leave", "death"
E.state(mob(), 3);                                                // provisional, banked, forfeited, claimed
```

## Saves

The ledger is in the campaign save. Each run notes the boot it was last seen in;
the first time a restored campaign reads a ledger, each run from before gets
its activity's `on_restore`, once. A suspended module keeps its ledgers;
nothing can be offered or claimed until it is active again.

## Limits

A ledger keeps 24 entries; the oldest claimed and forfeited ones go first. An
offer is refused while 24 are unclaimed. The fields hold 4096 characters each:
a ledger that would outgrow them refuses the change and asks the player to
claim first.

Implementation: [RewardEscrow.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/RewardEscrow.java),
[Prop_RewardActivity](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_RewardActivity.java).
Tests: [i18-escrow.js](../../tools/fixtures/fixes/i18-escrow.js).
