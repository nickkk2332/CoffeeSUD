# Property defects from DOCS b075 to b078

The four property chapters of DOCS (`archive/oldenginedocs/conditions/b075` to `b078`) end
with a list of problems found by reading the code. This file says what was
done with each, in the chapters' order. I1 (step 2a) fixed the first few
(PropSetter's trigger bits, LimitedContents' count, ItemBinder's ON values,
ModFaction's percents, MoveRestrictor's ISEARCH, null check and chance,
RoomRedirect's host, ItemSlot's look text, the help key); the rest were
left for the owner, who asked for them in 2b.

Each item is one of:

- **fixed**: a behaviour that was wrong, now right, with a check in
  `tools/fixtures/fixes/p-properties.js` (named after the dash), which fails
  on the code before the fix;
- **fixed (small)**: a wrong message, a typo or a wrong help text, fixed
  without a check of its own;
- **not a defect**: the code does what it should, and the chapter misread it;
- **left**: cruft, a design question, or a change too wide for a fix, with
  the reason.

## b075 (part 1)

**Prop_HaveAdjuster**

- RONDEATH defaulting to true: fixed - `haveadjuster-rondeath`. Absent, it
  is false, as the help says; an item's adjuster no longer goes when its
  owner dies unless RONDEATH=TRUE.
- TONEDOWN-ARMOR's gate, TONEUP-WEAPON's attack test, the TONEUP-MISC and
  TONEDOWN-MISC formulas, small negative armor, the armor signs: left, but
  for the attack test (fixed (small): it tested DAM). These are the item
  balancer's (TimsLibrary) tone-up and tone-down, which every adjuster class
  writes its own way (HaveAdjuster keeps the percent, AddDamage takes it
  off, and AddDamage's TONEDOWN applies itself twice through its ARMOR and
  WEAPON parts). Fixing the formulas one by one would move the disagreement,
  not end it: the balancer wants one rule for what "90%" means, written once.
- MULTIPLYPH scaling weight and height, and SPEED a factor in multiply mode:
  fixed (small), in the help.
- An additive fractional speed counting 0: fixed - `haveadjuster-speed`.
- ALLSET cleared only for the base ID: left. Its subclasses keep their sets
  by their own messages; unverified, and nothing in the world uses ALLSET.
- The description's sign flip skipping a term at position 0: fixed (small).
- `firstTime` never true: left (dead code).
- The ABLE* tables missing from this help: left (the Prop_Adjuster help has
  them, and the parameters are the same).

**Prop_AbilityImmunity**

- OWNER and WEARER taking a message's place: fixed - `abilityimmunity-message`.
- `wearer` parsed and never used: fixed - `abilityimmunity-wearer`. The
  second clause, a copy of the first, is the WEARER test (the owner, while
  wearing the item) it was meant to be.

**Prop_AbsorbDamage**

- STAT-LEVEL counting 10 for each field not set: fixed - `absorbdamage-level`.
- `getStat("TIDBITS=ENHANCED ...")` setting a flag, the ENHANCED roll's
  message value, the upper-casing that does nothing: left (cruft; the flag
  is what the item balancer asks for).

**Prop_Artifact**

- Speech at the item destroying it: fixed - `artifact-speech`. Only the
  "reset prop_artifact" phrase, by someone allowed to edit items, does.
- The stack trace test for DESTROY, `identifyStr` destroying in its finally,
  random IDs, uses reset to 100: left. The first two are fragile, not wrong;
  the IDs are kept once made; an artifact does not wear out, by design.

**Prop_ClanEquipment**

- HEALTH, MAGIC and SPEED doing nothing here, dead guards, the dice ranges in
  the description, WATER as slashing: left. Clans are gone from play
  (phase 5a), so no clan equipment is made.
- A power level that is not a number throwing: fixed (small).

**Prop_ClosedDayNight and Prop_ClosedSeason**

- Two clocks, the duplicated door code, doors changed in a stat
  calculation, the first area fallback: left. In one world with one clock
  the home and area clocks are the same clock; the rest is design.

**Prop_AreaForSale**

- The missing `return` in `landPropertyID`: fixed - `areaforsale-id`.
- `setAllowTheft`'s missing space: left (it works, as the chapter says).

**Prop_AddDamage**

- A pure percent not toned: fixed (small): the guard tests the percent.
- TONEDOWN keeping 1 - pct: left, with the balancer above.

**Prop_Doppleganger**

- Save bonuses adding up at each rescale: fixed - `doppleganger-saves`. The
  bonus given at the last rescale is taken off before the new one is added.
- Healing at each rescale: not a defect. The rescale happens only when the
  level changes, and a mob of a new level starts with its new hit points.
- The first `setLevel` overwritten, the global sentinel: left (cruft).

**Prop_Familiar**

- The first speech returning no language: fixed (small): the language just
  started is the one returned.
- No bounds check on a numeric type: fixed (small).
- A familiar destroyed when unlinked: left (design: a familiar is a bond,
  and the commented-out penalty is the old design's).

**Prop_FightSpellCast**

- setStat dropping CLASS, TEXT and NAME: fixed - `fightspellcast-setstat`.
- STAT-LEVEL always negative: left (the item balancer's sign, with the
  balancer above).

**Prop_Fumble**

- ADOMAIN tested against the types: fixed - `fumble-domain`.
- The domain and flag lists read only when ATYPE is given: fixed -
  `fumble-domain` (with ATYPE not given).
- "50% Spell_X" dropping its chance: left (not in the help; the chance of the
  property as a whole is what the help gives).

**Prop_CloseCommand**

- MSG_OPEN for a close, and the short command: fixed in I19.

**Prop_CommonTwister**

- DISPLAY written where DISPLAYTEXT is read: fixed - `commontwister-display`.

**Prop_EnterAdjuster**

- Stat terms read only from the last `;` part: fixed -
  `enteradjuster-stats`. The help's own example (`str+2 dex-2 ...;Prayer_Bless;`)
  lost its stats. A part that is not an ability is read for stats, wherever
  it is.
- Changes made in okMessage: fixed - `enteradjuster-veto`. They are made
  when the entry happens (executeMsg), so an entry another refuses changes
  nothing. Each entry adds them again, as the help says ("permanently added
  upon entering").

**Prop_AstralSpirit**

- The stale comment: fixed (small).

**Other**

- Prop_Climbable's mask allowing items: left (the mask names what it may be
  put on; on an item it does nothing).
- FLAG_ADJUSTER on non-adjusters: left (the balancer's choice of target).
- `Tests/PropHaveadjuster.java` not checking ARM, ATT or DAM: left (the old
  test classes are not run; the checks here are in the fixes tests).

## b076 (part 2)

1. LimitedContents counting: fixed in I1.
2. MoveRestrictor ISEARCH: fixed in I1.
3. MoveRestrictor's null check: fixed in I1.
4. Area InstantDeath never killing: fixed - `instantdeath-area`.
5. LanguageSpeaker HOMEONLY: fixed - `languagespeaker-homeonly`. A mob
   whose start room is in the property's area speaks; one from elsewhere
   does not.
6. ItemBinder modes: fixed in I1.
7. HaveResister's message naming the domain: fixed (small).
8. Weapon protections not stacking: fixed - `haveresister-stack`. Each kind
   keeps its own remaining protection in a round.
9. NarrowLedge's flag: not a defect. A room's or exit's sleeping flag is how
   it says its climbing and flying rules are off (`InTheAir`, `UnderWater`
   and `MUDTracker` read it); the comment is right.
10. HaveZapper's CONTENT: fixed - `havezapper-content`. Taking a thing out of
    a container with a CONTENT zapper zaps as taking the container would.
11. HaveZapper not zapping on wear: left (a zapper zaps on pickup, eating and
    drinking; one cannot wear what one could not pick up).
12. ModFaction percents: fixed in I1.
13. ItemSlot look text: fixed in I1.
14. ItemSlot's dead PUT branch: left (dead code, kept by its own comment).
15. ItemSlotFiller's shared dummy item: left (one game thread since phase 3c).
16. ItemSlotFiller's ADDS cut at the first `)`: fixed - `itemslotfiller-adds`.
    Parentheses in an ability's text are counted.
17. Enablers sharing ability objects: left (one character at a time in each
    place in single-player; unverified in play).
18. ItemTransporter rescanning on every call: fixed (small). An empty list is
    looked for again every tenth call, as a full one is.
19. ItemTransporter's stale destinations: fixed - `itemtransporter-dest`.
20. Container loops: fixed - `itemtransporter-loop`.
21. MultiEffects failing on entries without parameters: fixed -
    `multieffects-parse`. `Prop_A,Prop_B` and a trailing `Prop_C` are read.
22. MultiEffects applying every child to the carrier: fixed -
    `multieffects-bubble`. Only the children that bubble do.
23. LocationBound with no target: left, with a log line. An unknown room or
    area leaves the thing bound to its room, as ROOM would; the log says so.
24. LocationBound's PLAYEROK null: not a defect (`getGroupLeader` returns the
    mob itself when it follows no one).
25. MoveRestrictor CHANCE: fixed in I1.
26. Binding by name: left (single-player: one character by a name).
27. LanguageSpeaker's language re-invoked: fixed, with 5.
28. Dead code in LotsForSale and ModFaction: left.
29. ModExperience re-parsing: left (only while the text gives nothing).
30. Help mismatches: fixed (small) (Invisibility's targets, ItemSlotFiller's
    example, LangTranslator's wording).
31. Typos: LimitedContents' was fixed in I1; `fixAccoutingsWithMask` left (a
    method name in several classes).
32. HaveResister's level scale: left (the balancer's).

## b077 (part 3)

1. PropSetter's trigger bits: fixed in I1.
2. PropSetter's `${}` with two references: fixed - `propsetter-vars`.
3. The doubled condition: fixed in I1 (PUT's targets).
4. Undo by finalizer: left (design; the undo also runs when the effect goes).
5. ReqCapacity's overflow loops: fixed (small): a loop counts only what it
   moves (a siege weapon, or the weight of a loose item).
6. ReqCapacity's description of siege weapons: fixed (small). SIEGEWEAPONS
   defaulting to ITEMS and the empty branch: left (a siege weapon is an
   item). ROOMS not enforced: not a defect (LotsForSale reads it).
7. ReqLevels dropping terms: fixed - `reqlevels-terms`, and the parse no
   longer reads past the end.
8. ReqLevels not checking a mount: fixed - `reqlevels-mount`.
9. NOFOLLOW in six classes: fixed - `nofollow-prefix`.
10. ReqTattoo's +ALL never denying: fixed - `reqtattoo-all`. Found with it:
    NOSNEAK let sneaking past rather than stopping it, and a first word of
    NOSNEAK or NOFOL was not read: fixed - `reqtattoo-nosneak`.
11. PrivateProperty's expiry in milliseconds: fixed (small).
12. RestrictSpells' case: fixed - `restrictspells-case`.
13. NoChannel's precedence: fixed (small), and QUIET on a mob blocks what it
    would hear. (Channels have no other players to carry in single-player.)
14. Retainable's four milliseconds: not a defect (it means "by the next tick").
15. RoomPlusForSale's lot IDs: fixed (small).
16. Unreachable code: left.
17. OutfitContainer's worn state: fixed (small): the worn code is put back
    whatever the test gives, and the worn item is the one taken off.
18. OutfitContainer's numbering: fixed (small).
19. QuestGiver's shared set: left (one game thread since phase 3c).
20. Item walks on each recompute: left (performance, not wrong).
21. RoomLit and RoomDark caching their hours: fixed - `roomlit-hours`.
    NoCraftability: not a defect (it resets on setMiscText).
22. PlayerInstance: left (gone in phase 5a with the other group features).
23. Help mismatches: PrivateProperty's PRICE: fixed (small); ROOMS: not a
    defect (see 6).
24. Copied magic blocks: left.
25. Multiplayer assumptions: left (phase 5a removed what needs other people).

## b078 (part 4)

1. WeaponImmunity LEVEL: fixed - `weaponimmunity-level`. Single-digit levels
   were dropped too, and are read.
2. RoomView ATTACK: fixed - `roomview-attack`, with its missing-weapon message
   no longer failing when nothing is wielded.
3. UseEmoter's smells in the visual pool: fixed - `useemoter-smell`.
4. Undroppable LOSTOFF throwing: fixed - `undroppable-lostoff`.
5. Sounder's once-per-round: fixed (small): a weapon attack sounds once a
   round.
6. SpellAdder's hand-off: fixed - `spelladder-handoff`.
7. SpellAdder's fallback room: fixed (small).
8. SpellAdder's random listing: left (`abilities()` is the property's
   chance of giving each, by design).
9. SpellReflecting's charge refilled by a text rewrite: left. A builder's
   edit refills it, as meant; a save keeps the item's effects by their text,
   so a restored reflector is full. Noted for the saves.
10. WizInvis' hostility: left (design: an admin who attacks is attackable).
11. ShortEffects' typo: fixed (small). The name of `getBadInvokers`: left.
12. TicketTaker's one-second set: left (design).
13. WeakBridge's defaults: fixed (small). Its flag: not a defect (as 9 of
    b076). Every occupant falling: left (design).
14. RoomRedirect's duplicate masks: fixed - `roomredirect-dupes`.
15. RoomRedirect's host: fixed in I1.
16. RoomView's DOWNINOUT: fixed - `roomview-downinout`.
17. Repeated code: left.
18. WearOverride's double recalculations: left (harmless).
19. Unsellable's `pxceptMask`: gone already. StatAdjuster's `all25` of zeros:
    not a defect (the name is wrong, the value right: no change).
20. RoomsForSale's fallback ID: fixed (small).
21. Prop_Trainer fantasy only: left (design).
22. Smell's rewrite read as an edit: fixed - `smell-rewrite`.
23. Socials' LOAD: fixed (small): the file is LOAD's own value.
24. Player-only assumptions: left (phase 5a).

## Found on the way

- A script could not use the time library (`CMLib.time()` from JavaScript
  ended the script, unreported). The core `TimeManager` interface named the
  library class's own `CoffeeTime.TimeDelta`, so reflecting on the library
  loaded that enum twice, once by each class loader, and failed with a
  `LinkageError`. `TimeDelta` is now `TimeManager.TimeDelta`.
- JRUN reported a script's exceptions but not its errors: a class that will
  not load, or a script recursing without end (as the old ItemTransporter
  container loop did), ended the command with nothing said. It reports
  them as it does exceptions.
