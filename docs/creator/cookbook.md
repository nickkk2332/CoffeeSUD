# Creator cookbook

[Handbook](README.md) · [Runnable museum](examples/README.md)

For the optional ARPG skill/support recipe, see
[ability composition](15-ability-composition.md#install-the-playable-example):
install the module separately, grant its adapter, then link chaining, burning
and power supports to Ember bolt.
For optional armor, resistance, penetration, block, criticals and alternative
conversion rules, follow the [combat-profile recipe](16-combat-profiles.md#install-and-play)
and [stat-property examples](16-combat-profiles.md#add-defense-and-offense-stats).
For a complete Stage 4 adventure using all three foundations, build and play
[the Ember Trial](examples/ember-trial/README.md): training, gear choices,
scripted enemies, a boss and persistent completion in an isolated deployment.

These recipes combine systems rather than listing hypothetical features.
The museum pack supplies a tested end-to-end world for recipes 1–7, 17 and 21's
language foundation. Other recipes are authoring patterns with explicit setup
requirements; replace catalog names, room IDs, abilities and organization names
with definitions in your world. Run them in a disposable deployment first.

**Attaching a property** means add its ID and parameter text through the
target's effects editor (MODIFY ROOM/ITEM/MOB). AFFECT/AFFECTS displays effects;
it does not attach the property.
**Attaching a behavior** means add its ID and parameters through the behavior
editor. Script text below is MOBPROG unless the fence says JavaScript, XML or
INI. A setting block for a property is not a terminal command.

## 1. Build a complete miniature adventure

Create a fresh [museum deployment](examples/README.md), run its builder script
and SAVE. Play NEW as a normal character. Ask the guide about the gate, pull the
lever, examine the gallery, read the tablet and visit the shrine. The pack's
[build.js](examples/world/resources/creator/build.js) shows the complete object
setup, not just fragments of property text.

Check: `python3 docs/creator/examples/check.py` builds, publishes, plays,
checks API behavior and restores a campaign in a fresh process. It preserves
failure transcripts. This is also a template for developing your own example
world without changing the repository's starter world or personal save slots.

## 2. A numbered NPC conversation

Host: an NPC with Scriptable. File: `world/resources/progs/museum-guide.script`.
Behavior parameters: `LOAD=progs/museum-guide.script`.

```text
SPEECH_PROG p gate
mpmenu $n MUSEUM_GUIDE "What would you like to know?" gate:"How do I open the gate?" history:"What is this place?" bye:"Goodbye" bye
~
FUNCTION_PROG MUSEUM_GUIDE
switch $0
case gate
  mpechoat $n Pull the brass lever to open the east gate.
case history
  mpechoat $n This museum keeps the memory of the drowned coast.
case bye
  mpechoat $n The guide returns to the ledger.
endswitch
~
```

SAY gate opens choices; answering `1` calls the function with `gate` as `$0`.
MPMENU returns immediately, so put dependent work in the answer function.
Optional choices can use `[IF condition]`, for example a carried quest item.
Check: choose each branch, an invalid answer, Enter (the default), and save while
the choice is pending. Restore reissues the displayed menu unless its source
changed. See the pack's [played test](examples/world/tests/museum.play).

## 3. A lever that opens a gate

Setup: a two-way door from CreatorMuseum#0 east to CreatorMuseum#1 west.
Host: an ungettable lever item. Property: Prop_Switch.

```text
STATES="down,up" START=down MSG_up="The east gate grinds open." MSG_down="The east gate grinds shut." ACTION_up="door open CreatorMuseum#0 east QUIET; setvar WORLD MUSEUM_GATE 1" ACTION_down="door close CreatorMuseum#0 east QUIET; setvar WORLD MUSEUM_GATE 0"
```

Use MPDOOR/action `door` so both sides change. On the entrance description:

```text
<VARIES><IFVAR MUSEUM_GATE=1>The east gate stands open.</IFVAR><ELSE>The east gate is closed.</ELSE></VARIES>
```

Check: PULL lever, walk east and west, then operate it again. Inspect both exit
objects and the rendered description. Save open and restore; a new game should
still start closed. PROPCHECK currently warns about valid MSG_up/MSG_down keys;
see the [known validator gap](../../audit/developer-tools-audit.md#open-property-key-validation-gap). Add RESET only if an automatic closure is intended; ensure
it cannot strand the party. The complete setup and checks are in the pack.

## 4. A clue that remains found after rewriting it

Host: a room. Property: Prop_Details.

```text
inscription,runes=Nine carved waves surround a seal. ID=museum-waves SETVAR=MUSEUM_RUNES=1
```

Add Prop_Senses for an audible hint:

```text
ID=museum-gallery SOUND="The distant surf echoes through the stone." CHANCE=100
```

LOOK inscription and LISTEN expose the clues. Check a second character before
deciding whether progress should be personal or shared. Keep `ID=museum-waves`
when revising prose; use ALIASES/LEGACYKEYS when migrating an older source key.
Give a separate puzzle a new ID. CHECKTEXT and WORLD CHECK ONLY=ids catch spelling
and duplicate-ID mistakes, not whether the clue is understandable.

## 5. An opening paragraph and an optional paged introduction

Host: the arrival room. Prop_FirstEntry:

```text
ID=museum-shrine TEXT="A single candle burns before the sea-worn altar." ONCE=CHARACTER
```

For a longer introduction, put sectioned paragraphs in `world/text/museum.txt`:

```text
[arrival]
The guide sets a ledger on the desk.

'Some things here still remember the sea,' she says.
```

From an appropriate NPC/room event, call:

```text
MPNARRATE $n museum.txt#arrival PAUSE
```

Use NOPAUSE for uninterrupted narration. Check Enter paging, leaving, a second
party window and restoring halfway through. Narration owns only its temporary
pause; a manual pause remains after it finishes. File edits affect later/restored
reading because assets are external. The pack supplies this file for adaptation.

## 6. Unlock a lore entry by reading an object

Setup: `world/lore/museum.ini`:

```ini
COAST=The Drowned Coast|History|The museum was built after the sea took the old harbour.
```

Host: a readable tablet. Prop_Lore:

```text
ENTRY=COAST ON=READ SCOPE=CHAR MSG="You learn the history of the coast."
```

Check READ tablet, LORE COAST, reading again and another character's codex.
Choose PROFILE scope for shared knowledge. The definition stays in world files;
learned state stays in the campaign. WORLD CHECK ONLY=lore catches missing entries.

## 7. A new command with saved state

File: `world/js/init.js`, in its onBoot function:

```javascript
function onBoot() {
    cm.command('museumstatus', {help: 'MUSEUMSTATUS: inspect the bell.', cost: 0}, function (who, args) {
        cm.tell(who, 'Museum bell count: ' + (cm.store('world').museumBells || 0));
    });
}
```

The pack adds a MUSEUMBELL command that increments this store. Code registrations
are recreated at boot; the JSON counter saves with the campaign. Do not store a
mob, callback or Java object in the store. Check after a **fresh process** restore
and after a separate NEW game. Use a namespaced store key for reusable packages.

## 8. A delayed story event that survives a save

Scope: JavaScript in JRUN, an event handler or a `<SCRIPT>` block. Give a valid
room ID and keep the callback free of captured objects:

```javascript
cm.after(3, function (data) {
    var room = cm.room(data.roomId);
    if (room != null) cm.echo(room, data.text);
}, {roomId: 'CreatorMuseum#0', text: 'A distant bell answers.'});
```

The timer is measured in ticks, pauses with game time and is saved. Its function
is rebuilt from text after restore. Check by saving before the third tick,
restoring in a new process and advancing the remainder. Use a saved stage/guard
if the initiating trigger might schedule it twice. For a combat windup use a
named alarm/telegraph so stun, death and movement can cancel it deliberately.

## 9. Count an event anywhere in the world

File: init.js; merge this registration into your existing onBoot:

```javascript
cm.on('death', function (victim, killer) {
    if (killer == null || !sud.party.isMember(killer)) return;
    if (String(victim.Name()).indexOf('marsh goblin') < 0) return;
    var data = cm.store('world');
    data.marshGoblins = (data.marshGoblins || 0) + 1;
    if (data.marshGoblins === 5)
        cm.echo(killer.location(), 'The marsh falls strangely quiet.');
}, 'story.marsh-goblin-count');
```

For production content prefer a tag/class/story identity over a display-name
substring. Check two matching deaths, a nonparty killer and reload: the explicit
listener key prevents duplicate registration. A death counter is not a quest
acceptance/reward policy; use the quest system when that distinction matters.

## 10. Make a reusable passive property without Java

Use CREATE ABILITY Prop_Haunted. Set its SCRIPT to:

```text
RAND_PROG 10
if effectparm(LOUD == yes)
  mpecho Something moans behind the walls!
else
  mpecho A cold draft slips through the room.
endif
~
```

Attach Prop_Haunted with `LOUD=yes` or `LOUD=no` to different rooms. Configure
HERESTATS/MOCKABILITY when the property also adjusts stats or delegates an
existing effect. Check both parameterizations, plain passive attachment and
save/restart. The generic definition is world data; per-instance text belongs
to the host. RAND_PROG probability is not an exact ten-tick schedule.

## 11. Turn a generic attack into an area ability

Setup: a tested single-target GenAbility with appropriate target/affect types,
classification, damage formula, costs and saving throw. Through MODIFY ABILITY,
set TARGETMODE=ALLENEMIES, a MAXTARGETS formula if needed, and AOEMSG. Add
ONCAST for per-target consequences and CANCAST for a pre-cost condition.

Check with several enemies, an ally, an invalid target and insufficient mana.
Cost/CANCAST occur once, while target masks/saves/effects occur per selected
target. Use ALLALLIES for a group buff, ALLINROOM for an intentional room-wide
effect or GROUPANYWHERE only when remote effects are desired. Compare
proficiency and action costs under turn-based combat.

## 12. A boss with one readable phase transition

Host: a mob with actual fighting abilities and enough HP to reach a phase.
Behavior: BossPhases.

```text
ONENGAGE: SAY "You should have left."; AT=50%: HEAL 10%, SAY "Still standing!"; RESET=FULLHEAL
```

Add `Prop_MobRank RANK=BOSS` only if the rank's bonuses fit your balance.
Check that the 50% event fires once, that retreat/full healing rearms it, and
that save/restore preserves the active phase. Add SUMMON using actual catalog
names after the basic fight works. EncounterReset and RESET=LEASH coordinate
cleanup for a boss who should return home after pursuit.

## 13. Enemies that surrender or choose a vulnerable target

Host: a bandit mob. Behavior: Morale.

```text
SURRENDERAT=20% SURRENDERMSG="Mercy! I'll show you the hidden cache!"
```

Add a SURRENDER_PROG for the offer. If the bandit must survive a killing blow,
use an appropriate essential/knockout rule too; a low-HP morale check alone does
not promise lethal damage will wait for the next tick. For target selection:

```text
PREFER=LOWESTHP STICKY=4 SWITCH=50
```

Those are TargetPriority parameters, a separate behavior. Check a mixed party,
a taunt if enabled, a surrender while the whole group fights, and save/restore.
Do not give essential NPC protection without an eventual path to finish the quest.

## 14. A small weighted encounter table

File: `world/encounters/woodland.enc`:

```text
MOBS "GenMob*1d2" WEIGHT=1 COOLDOWN=3
EMOTE "An owl calls from the dark branches." WEIGHT=4
ITEM "GenItem" WEIGHT=1 ONCE
```

These generic classes are valid placeholders; replace them with finished catalog
creatures/items. Configure the encounter property/command through `AHELP
ENCOUNTER` to use woodland, entry chance and budget. `FUNC name` can call a
FUNCTION_PROG on the rolling host. Check repeated entry, party entry and the
once-per-game item. Add masks for time/season/level rather than a second
unrelated random-spawn script.

## 15. Loot that rolls once when a chest opens

Setup: a real container and the shipped `randareas/loot.xml` chest template, or
a tested world overlay. Host property: Prop_LootTable.

```text
FILE=randareas/loot.xml ID=chest ROLLS=1 ON=OPEN CHANCE=100
```

Check the first open, close/open, emptying and reopening, and a saved open chest.
An opened container stays rolled until contents reset; the player should not
reroll by closing it. Catalog caps/once-ever rules apply to catalog entries in
the table. Verify them across process/cache reuse: the audit records an unresolved
[cached-policy failure](../../audit/developer-tools-audit.md#open-cached-catalog-smoke-failure). Use ON=DEATH on a mob for corpse loot instead of an OPEN trigger.

## 16. A two-piece equipment set

Setup: two wearable items in compatible distinct wear slots. First item's
Prop_ItemSet:

```text
SET=coastguard NAME="Coastguard's Oath" PIECES=2 TIER2="Prop_WearAdjuster(con+2)"
```

Second item's Prop_ItemSet: `SET=coastguard`. Check one piece, both worn,
removing either, identifying the item and restoring equipped items. Use
`world/itemsets.txt` for shared definitions when many pieces use the same tiers.
Carried duplicates do not count as correctly worn pieces.

## 17. Give backgrounds and starting equipment

File: `world/origins.ini`:

```ini
[COAST]
NAME=Child of the Coast
DESCRIPTION=You grew up among the tidal ruins.
START=CreatorMuseum#0
TATTOOS=COAST_BORN
MONEY=10
```

File: `world/kits/coast.kit`:

```text
starting -TATTOO +ORIGIN_COAST
title A traveller's belongings
item GenWeapon
gold 5
```

Replace generic equipment with catalog items. Check the NEW flow and its
ORIGIN_COAST tattoo, then review/back and a later NEW character. The pack's
museum provides the start room but does not automatically install these records.
Use creation.ini for custom question order or locked/template-only openings.

## 18. A workshop with a required forge

Host: a fixed forge item or room. Prop_Workstation:

```text
TAG=forge SKILLS=Blacksmithing,Weaponsmithing SPEED=25 PROF=+10 QUALITY=+1
```

Add `STATION=forge TOOL="rune chisel" FAILMSG="You need the forge and a rune chisel."`
to the supported recipe requirements column, preserving all original tab
columns. For GenCraftSkill use KEY_VALUE_PARMS. Check with/without station and
tool, and VALIDATE RECIPES. QUALITY changes tier rolls only when the world has
enabled tiers in `resources/skills/craftquality.txt`; shipped tiers are comments.
Use NOQUALITY=1 for a recipe whose output should be fixed.

## 19. Crops that grow in a cave

Setup: a farming-compatible room/resource and viable seeds. Host room's
Prop_CropRules:

```text
NEEDSUN=false DOMAINS=CAVE
```

File: `world/crops.txt`:

```text
WHEAT GROWHOURS=2 YIELD=2d4+1 FAILMSG="The wheat will not take root here."
```

Darkness and sunlight are not identical to suitable soil; a stone floor can
still fail planting. Check planting, waiting one hour, saving, restoring and
finishing growth. Add SEASONS/WATER when desired. Depletion/regrowth and crops
use game time rather than the computer's calendar.

## 20. Scaffold an NPC-run organization

Setup: a real hall, a free adjoining exit and valid catalog kit items.

```text
CREATE GUILD Keepers of the Coast TEMPLATE=ORDER HALL=CreatorMuseum#2
ORGCHECK
SAVE
```

Edit the scaffold's masks, ranks, dues, guildmaster/trainer and shop stock.
Test an eligible and ineligible mortal, merit advancement, a filled seat and
rejoining. Confirm definitions survive a clean boot. The scaffold does not
write the guild's story for you or turn standing into membership automatically.
See [organizations](../organizations.md) for exact GuildMaster parameters.

## 21. An invented language and an inscription

CREATE LANGUAGE MuseumSpeech, then set WORDFILE=museum.txt,
SYLLABLES=`sa,ta,ma/la,ri,ven`, SUFFIXES=`S:en`, and WRITTENNAME=`Coastal Runes`.
The pack exports this definition and supplies `world/vocab/languages/museum.txt`:

```text
HELLO=sela
SEA=mar
GATE=esh
FRIEND=tavi
```

Use `LANGUAGE PREVIEW MuseumSpeech hello` as a builder, and teach a mortal
through `MPLEARNLANG $n MuseumSpeech 40`. Add inline writing:

```text
^<LANG MuseumSpeech NOTE="coastal runes"^>Hello friend.^</LANG^>
```

Check 0%, partial and full comprehension, a translator, and fresh-process restore.
Do not use an Archon's automatic full skills to judge immersion progression.
Prop_LanguageImmersion adds exposure-based learning with caps and stable IDs.

## 22. A paid abstract journey with recovery

Setup: a destination and a safe transit room. Host: a coach or departure exit.
Prop_Journey:

```text
DEST=Harbour#2 VIA=Road#0 DURATION=2 FARE=5 PARTY=LEADER MISSING=WAIT DEAD=CONTINUE DISCONNECTED=TRAVEL CANCEL=RETURN REFUND=UNUSED
```

Replace all IDs. Check insufficient fare before anyone moves, two party
members, combat interruption, JOURNEY RESUME/CANCEL and a save halfway through.
Return can itself be refused by movement rules and leave a paused journey;
provide a recovery route. Refunds use saved receipts and should happen once.
Use TransportLine for a physical scheduled vehicle instead of an abstract trip.

## 23. Discovered fast-travel stones

Setup: two rooms in one network. Prop_Waypoint on each, with different NAME/ID:

```text
NETWORK=COAST NAME="Museum Stone" ID=museum-stone DISCOVER=TOUCH SCOPE=PROFILE COST="gold 1"
```

Use `NAME="Harbour Stone" ID=harbour-stone` at the other endpoint. Touch/USE
to discover, then TRAVEL. Check unknown destinations stay hidden, the cost is
charged once after all passengers pass entry checks, and a second profile
character sees shared discovery. Rename a stone without changing its ID.
WORLD CHECK ONLY=routes,ids catches ambiguous names and duplicate network IDs.

## 24. A crime test that leaves no warrant

Setup: an area with a legal behavior/law and two ordinary characters.

```text
LAW TEST ASSAULT AS Alice AGAINST Bob
LAW SET SALESTAX=7.25
```

TEST is diagnostic; inspect its reason and verify no warrant was issued. It
does not consume campaign dice and uses a fixed ignore-chance roll. SET edits
the area's custom law and is world content in builder mode. Save/restart to
check tax persistence and inspect a neighboring area to confirm its shared law
was not changed. A builder may be above the law; choose mortal actors.

## 25. A declarative quest reward without double payment

Setup: a valid quest named coast_task with normal acceptance/objective/win
handling. Add to its controller:

```text
set rewards "50 XP 2 QP"
```

At the one authoritative success point in its giver/tracker script:

```text
MPQUESTWIN $n coast_task REWARD
```

The snippet is a reward addition, not a complete quest. Keep the win guarded by
the quest's active/objective state; do not also call MPREWARD for the same
bundle. Test repeated talk, repeat completion/cooldown and party/profile sharing.
QUESTCHECK catches structure; normal play proves the success route. See
[quest design](08-quests.md) and the [Q10 regression](../../tools/fixtures/fixes/q10-rewards.js).

## 26. Rename a shipped clue without losing progress

Keep its existing ID, or set a new ID plus ALIASES for previous IDs. When a
location/name/key format changed, use LEGACYKEYS with the complete old token:

```text
ID=crypt-inscription ALIASES=old-crypt-runes LEGACYKEYS=your-complete-old-token
```

This parameter pattern applies to properties supporting authored identities;
it is not a universal option for every effect. Restore a pre-change save and
verify it migrates once, does not repay rewards, and keeps state after another
save. Keep aliases while supported players can still bring older saves. External
text remains live; preserve world versions if old wording must remain exact.

## 27. Add off-thread integration without freezing the world

Scope: an ordinary JavaScript game scope. This minimal example needs no network:

```javascript
cm.background(function (data) {
    return {answer: data.number + 1};
}, {number: 41}, 2000, function (error, result) {
    if (error) debug(error);
    else cm.store('world').answer = result.answer;
});
```

Only JSON crosses into the fresh worker. Put Java I/O in the worker with its
own timeouts and make world changes in the callback. Check cancellation, timeout,
queue saturation and restore while pending before attaching a real service.
Jobs are not saved; late results are discarded. This is not forced preemption
of arbitrary synchronous Java and does not install a dedicated LLM framework.

## 28. Prepare a world for release

Run LIST UNSAVED, WORLD STATUS, WORLD PROBLEMS, full WORLD CHECK and your world
tests. Resolve conflicts and inspect the Git diff after SAVE. Start NEW without
DEV/BUILDER and finish the opening. Restore at least one supported older save
in another process. Package world assets with the compatible engine revision,
instructions and licenses. Keep dev-only rooms out of mandatory routes.

Check that no .builder conflict, scratch database or test save was accidentally
published. A successful static check is evidence about structure; completing
the game as an ordinary character is evidence about the experience. Keep both.

## 29. A selective equipment workbench

Use the [item-modifier pack](examples/item-modifiers/README.md) with its parent
composition/profile modules, or generate the complete
[Ember Trial](examples/ember-trial/README.md). Select items.modifiers explicitly.
Merge the tracked affixes into affixes.xml, declare a base's capacities/pool,
and add MODBASE/AFFIXES to the existing loot item. Place an ember-forge
Prop_Workstation and supply plain IRON resources.

As an ordinary character, open the coffer, collect the mantle/iron, inspect
ITEMMODS and MODCRAFT POOL, then PREVIEW/PREPARE/COMMIT embertrial.reroll for one
modifier. Protect that modifier and confirm reroll/removal refuse without cost.
Unprotect, remove and add an eligible affix by ID. The other modifier retains
its rolls and identity. Wear it and inspect COMBATRESULT STATS.

Save a prepared request and quit; commit after restoring in another process.
Repeat the same request number and verify neither cost nor result repeats.
Reopen the saved coffer to verify it cannot replenish its loot. Disable/re-enable
the module to check suspension preserves rolls/receipts. The
[complete guide](17-item-modifiers-crafting.md) supplies schemas, limits and
the post-commit itemCrafted event. This family uses numeric profile deltas and
plain resource costs; arbitrary effect-layer ownership remains future work.

## 30. A guarded shrine that blesses whoever claims it

Setup: a shrine room. A guardian mob with the behavior `ObjectGuardian` and the
parameter `SENTINAL`: nobody takes anything from the room while it stands. A
gettable item, `a shrine ember`, with two properties.

`Prop_Invokable`, the blessing: held and used, it casts a timed spell once, and
the shrine renews it each game day.

```text
VERB=CLAIM SPELLS=Prayer_Bless TARGET=SELF CHARGES=1 RECHARGE=DAILY REQUIRE=HELD MSG="<S-NAME> raise(s) <T-NAME>, and warmth spills over <S-HIM-HER>." EMPTYMSG="The ember is cold until tomorrow."
```

`Prop_LocationBound`, so the ember cannot leave the shrine's area:

```text
AREA
```

Check: as an ordinary character, `GET ember` is refused while the guardian
stands. Defeat it, `GET ember`, `CLAIM ember`: the blessing is a normal timed
effect and runs out. `CLAIM ember` again is refused until the next game day.
Save and restore: the ember keeps its charge state (`LEFT=`) and its recharge.
The ember cannot be carried out of the area. For a shrine with no item, the
[once-a-day shrine](28-mobprog-functions.md#a-shrine-that-blesses-once-per-game-day)
script does the same from a room's `GREET_PROG`.

## 31. Weapon mastery and an egg that hatches

A **weapon-family mastery**: an achievement counting kills made with a sword
(`PLAYERMASK` is tested on the killer, so `-WEAPONCLASS` is what they wield),
in `achievements.ini` under `[PLAYER]`:

```ini
ACH_BLADEADEPT=EVENT="KILLS" DISPLAY="Win 25 fights with a sword" TITLE="*, Blade Adept" REWARDS="" NUM=25 ZAPPERMASK="-LEVEL +>=0" PLAYERMASK="-WEAPONCLASS +SWORD"
```

A `KILLS` achievement must have a `ZAPPERMASK` (on the slain); `-LEVEL +>=0`
counts every kill.

`ACHIEVEMENTS` shows the count so far (`ACHIEVEMENTS NOW`), and the title comes
once. Under `[ACCOUNT]` instead, it counts for the whole profile.

An **incubator**: an egg that grows while held in fights. Put
`Prop_ItemGrowth` on a GenItem `a warm egg` (it counts the kills of whoever
holds or wears it, not of one who only carries it):

```text
GAIN=KILL STAGES="5:NAME='a cracking egg' DISPLAY='A cracking egg twitches here.';12:NAME='a hatchling drake' DISPLAY='A hatchling drake curls here.' PROPS=Prop_HaveAdjuster(STR+1)" MSG="$p changes!" BIND=CHARACTER
```

Check: `HOLD egg`, kill five things, and its name changes; `IDENTIFY`
shows its stage and how far the next one is. Growth and stage are kept with the
item, so they survive a save. The [item-mastery track](39-progression-tracks.md)
is the module version, with milestones and a tree.

## 32. A collection with hints

Six entries and a completion, as achievements. Each entry counts the first time
a matching item is picked up; the display text is the hint.

```ini
ACH_SHELL_1=EVENT="GOTITEM" DISPLAY="Tide shell: found where the river meets the sea" TITLE="" REWARDS="" NUM=1 ITEMMASK="-NAMES \"+a tide shell\""
ACH_SHELL_2=EVENT="GOTITEM" DISPLAY="Ember shell: kept by the furnace warden" TITLE="" REWARDS="" NUM=1 ITEMMASK="-NAMES \"+an ember shell\""
...
ACH_SHELLS=EVENT="ACHIEVER" DISPLAY="The full shell collection" TITLE="*, Shell Keeper" REWARDS="" ACHIEVEMENTLIST="ACH_SHELL_1,ACH_SHELL_2,ACH_SHELL_3,ACH_SHELL_4,ACH_SHELL_5,ACH_SHELL_6"
```

Check: `ACHIEVEMENTS ALL` lists the entries still to find with their hints;
picking up a shell marks its entry once; once all six are marked, the
completion comes at the engine's next achievement check (shortly after, or at
once when the player types `ACHIEVEMENTS`). Tell
the collectable copies from ordinary ones by name or by a tag in the mask. The
completion reward is optional, so missing it costs nothing else.

## 33. Sockets for gems

The host has `Prop_ItemSlot`; each gem has `Prop_ItemSlotFiller` with the same
`TYPE`. The host gains the gems' effects while they are in it.

```text
NUM=2 TYPE=Gems REMOVABLE=TRUE
```

```text
TYPE=Gems ADDS="Prop_HaveAdjuster(STR+1)"
```

Check: `PUT ruby INTO blade` fills a slot (a gem of another `TYPE` is refused,
and so is a third gem). `LOOK blade` lists the slots. `REMOVE ruby FROM blade`
takes it out again, only while the blade is not worn and only if `REMOVABLE`.
The gems are saved inside the host's text. Destroying the host destroys its
gems with it.

Give each slot its own type with a list (`TYPE="Gems,Gems,Runes"`): a gem goes
only into an empty gem slot, a rune into the rune slot. `ONLOSS=KEEP` returns
the gems to the owner when the host is destroyed; without it they are lost
with it. A gem can carry a composed-skill support: give its filler
`ADDS="Prop_ComposeSupport(SUPPORTS=example.burning)"`, and while the host is
worn the wearer's composed skills get that support (`COMPOSE SHOW`: `From your
gear`). Fixture: `i05-sockets.js`.

## 34. One guild, two associations, and a forbidden pair

Give the organization types a category in the government file
(`CATEGORY="GUILD"`, `CATEGORY="ASSOC"`), and cap memberships per category in
`coffeemud.ini` or `world.ini`:

```ini
MAXCLANCATS=GUILD 1, ASSOC 2, NPCGUILD 10, 1
```

A character can then be in one guild and two associations at once. To forbid a
pair, give one type's `REQUIREDMASK` (or one clan's `JOINMASK`) a clan mask:

```text
+CLAN "-The Night Hand"
```

Check: joining a second guild is refused; a third association is refused; a
member of the Night Hand cannot join the type with that mask. Grants follow
membership: a government's `ABILITIES` and `EFFECTS` by clan level are
recomputed from the clans a character is in now, so leaving one takes away its
automatic grants. Skills a character learned through a clan stay learned.

## 35. A challenge book with hidden entries

Ten achievements, some visible and some only once a condition is met, and one
completion. `VISIBLEMASK` hides an entry from those who do not pass it:

```ini
ACH_TRIAL_1=EVENT="KILLS" DISPLAY="Trials: defeat the furnace warden" TITLE="" REWARDS="" NUM=1 ZAPPERMASK="-NAMES \"+the furnace warden\""
ACH_TRIAL_7=EVENT="KILLS" DISPLAY="Trials: defeat the warden without armor" TITLE="" REWARDS="" NUM=1 ZAPPERMASK="-NAMES \"+the furnace warden\"" VISIBLEMASK="-TATTOOS +ACH_TRIAL_1"
ACH_TRIALS=EVENT="ACHIEVER" DISPLAY="Trials: the whole book" TITLE="*, Tried" REWARDS="" ACHIEVEMENTLIST="ACH_TRIAL_1,ACH_TRIAL_2,ACH_TRIAL_3,ACH_TRIAL_4,ACH_TRIAL_5,ACH_TRIAL_6,ACH_TRIAL_7,ACH_TRIAL_8,ACH_TRIAL_9,ACH_TRIAL_10"
```

Check: `ACHIEVEMENTS ALL` lists the visible entries (give them a common
prefix, such as "Trials:"); a hidden entry appears once its `VISIBLEMASK` passes; each is
earned once and kept as a tattoo, so a save keeps the book. A "book" view with a
count of hidden entries, and personal records, are P13's extension.
