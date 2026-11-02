Fixtures for `tools/smoke.sh`. The smoke test copies them into
`CoffeeMud/resources/quests/_smoke/` for its run and deletes them after.

- `smoke.quest`: a quest that loads one rabbit from `mobs.cmare` into a random
  room and runs for 1000 ticks.
- `followfx.js`: run with `JRUN`; lights the player's torch with a timed
  spell and has the dogs in the room follow the player.
- `jsstore1.js` and `jsstore2.js`: run with `JRUN` before a save and after the
  restore; the second shows the JavaScript store (`cm.store`) the first set.
