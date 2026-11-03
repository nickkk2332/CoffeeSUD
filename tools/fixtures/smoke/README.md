Fixtures for `tools/smoke.sh`. The smoke test copies them into
`CoffeeMud/resources/quests/_smoke/` for its run and deletes them after.

- `smoke.quest`: a quest that loads one rabbit from `mobs.cmare` into a random
  room and runs for 1000 ticks.
- `followfx.js`: run with `JRUN`; lights the player's torch with a timed
  spell and has the dogs in the room follow the player.
- `catalog.cmare` (item 1): the catalog the first world's sessions play
  with, as builder mode wrote it: the Sunblade, unique once ever, cap 1,
  found in an iron chest the first time one is opened.
- `unique1.js` and `unique2.js` (item 1): the first sets out three iron
  chests; the second, after a save and a restore, reports the Sunblade's
  copies in the game, those ever made, and which chests hold one.
- `jsstore1.js` and `jsstore2.js`: run with `JRUN` before a save and after the
  restore; the second shows the JavaScript store (`cm.store`) the first set.
