# Example pack: reward escrow

[Chapter 36](../../36-reward-escrow.md)

The module manifest, an activity and three reward bundles: the trial of three
rooms. Each room's spoils are provisional when earned; a shrine banks them;
leaving the trial, failing it or dying forfeits what is not banked; a restore
keeps the run going.

Install into a world:

```text
modules/rewards.escrow.json -> world/modules/
rewards/*.json              -> world/rewards/
```

Add `rewards.escrow` to `MODULES`. Mark the trial's area with
`Prop_RewardActivity ACTIVITY=example.trial`, so that leaving it applies the
activity's `on_leave`. A script (or the encounter director) offers each
room's spoils, and the shrine banks them:

```javascript
var E = Packages.com.planet_ink.coffee_mud.core.RewardEscrow;
E.offer(mob(), "example.trial", "example.room-one", "room-one");  // provisional
E.bank(mob(), "example.trial");                                    // at the shrine
E.end(mob(), "example.trial", "complete");                         // the last room: banks the rest
```

Players see their ledger with `REWARDS` and claim with `REWARDS CLAIM ALL`.
