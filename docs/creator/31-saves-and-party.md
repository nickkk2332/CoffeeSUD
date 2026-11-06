# Saves, the profile and the party

This chapter describes CoffeeSUD's single-player model from both sides of the
screen. Players learn how to keep several characters in a profile, play them as
a party, choose between real-time and turn-based play, and save and restore
their campaign. World creators learn which of those behaviours their world can
configure (party limits, summoning rules, save rules, save points), what a save
file actually contains, and what happens when a saved campaign meets a world
that has been edited since. Engine developers will find the on-disk save
format and the recovery rules. CoffeeMud's network features (logins to a
server, other people's characters, channels) do not exist here: one person
plays one profile, and the "world" is rebuilt from files at every start.

## In this chapter

- [The model in one page](#the-model-in-one-page)
- [The profile and its characters](#the-profile-and-its-characters)
- [The party](#the-party)
- [Windows and the console](#windows-and-the-console)
- [Time: real time, pause and turns](#time-real-time-pause-and-turns)
- [Saving](#saving)
- [Save rules for world creators](#save-rules-for-world-creators)
- [The save folder](#the-save-folder)
- [What a save contains, and what comes from the world files](#what-a-save-contains-and-what-comes-from-the-world-files)
- [Restoring after the world changed](#restoring-after-the-world-changed)
- [Save tools](#save-tools)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [Reference](#reference)
- [See also](#see-also)

## The model in one page

| Word | Meaning in CoffeeSUD |
| --- | --- |
| World | The files in `world/` (areas, custom classes, quests, scripts, `world.ini`). Every new game starts from them. A normal game never writes to them. |
| Campaign | One playthrough: a working database made from the world, plus everything that happens in it. It exists as a running game and, when you save, as a **save** (also called a *slot*). |
| Profile | The single local account that holds every character of a campaign. It replaces CoffeeMud's accounts and logins; nobody types a password. Each campaign has its own profile. |
| Character | One member of the profile. Characters are ordinary CoffeeMud player characters (class, race, level, items). |
| Played character | The one you type to in the terminal. Its session is the console. |
| Party | The characters of your profile that are in the world and follow one leader. |
| Rest | A character that is in the profile but not in the world (it was dismissed, or was never brought in). |
| Window | An optional extra window for a character in the world that you are not playing in the terminal. |
| Save / slot | A folder under `saves/` named by you (`hero`, `before-the-dragon`). |

How the pieces fit:

1. At start the launcher lists the saves and asks which to restore, or starts a
   new game (see [Installing and running](18-installing-and-running.md)).
2. The engine builds the world from `world/` (or from its cache of it) into a
   fresh *working database*, then, for a restore, lays the save's character and
   state tables over it and puts the saved contents of every room back.
3. You play. Anything not saved is lost when you quit.
4. `SAVE` writes your characters' tables and the live state of the world into a
   slot folder. `RESTORE` restarts the engine in the same process with that slot.

Builder mode (`BUILDER=1`) has no saves at all: changes go to the world files
([World files and builder mode](02-world-workflow.md)). Every command in this
chapter that touches saves says "There are no saved games in builder mode" there.

## The profile and its characters

### What the profile is

The profile is one `PlayerAccount` named `Profile`, created the first time the
game needs it. All characters you make are members of it. The launcher turns
the account system on for you (`COMMONACCOUNTSYSTEM=50`, a ceiling of 50
characters; `MAXCONNSPERACCOUNT` in `coffeemud.ini` ships as 50 too), so the
usual CoffeeMud account menu is what you see after you log off.

Because the profile lives in the campaign's database tables (`CMACCT`,
`CMCHAR` and friends, see [What a save contains](#what-a-save-contains-and-what-comes-from-the-world-files)),
a new game starts with an empty profile and a restore brings back exactly the
characters that existed when the save was made.

### Making and choosing characters

| To do this | Type |
| --- | --- |
| See your characters | `PROFILE` (alias `ACCOUNT`; takes optional sort/field words such as `profile level`) |
| Make another character | `LOGOFF` and answer `y`; at the profile menu choose `N` (new character) or type `NEW name`; confirm with `y` and go through character creation ([Characters](03-characters.md)) |
| Play a character that is resting | `SWITCH name`; the character you were playing leaves the world and the other comes back where they were |
| Play a character already in the world | `SWITCH name`; the two swap places in the terminal, and neither moves |
| Delete or retire a character | `LOGOFF`, then `D` at the profile menu |
| Leave the campaign | `QUIT` (offers to save) |

The profile menu (`LOGOFF` brings it up) is the inherited CoffeeMud account
menu: `L`ist characters, `N`ew character (with a count of how many more you
may make), `D`elete/Retire, `H`elp, `Q`uit, or type a character's name to play
it. The very first character of a new game is created straight away, without
the menu.

`LOGOFF` is not `QUIT`: it takes the played character out of the world and
returns to the menu. It does not save. The mask in `LOGOUTMASK` (an optional
`coffeemud.ini` zapper mask, empty by default) must pass for LOGOFF, QUIT and
SWITCH alike; a hardcore setup can use it to forbid quitting outside an inn. It
is an engine setting (`coffeemud.ini` or a launch argument), not one of the keys
a `world.ini` may set.

### World rules for the profile

These keys go in `world/world.ini` or `coffeemud.ini` (a world's value wins
over the ini; the command line wins over both). All are in the world-settings
allowlist ([World settings](world-settings.md)).

| Key | Values | Default | Effect |
| --- | --- | --- | --- |
| `PROFILEMAX` | number, `0` for no limit | `0` | The most characters in the profile, resting ones included. The first character is always allowed. |
| `NEWCHARS` | `ALWAYS`, `FIRSTONLY`, `CHEAT`, `MASK` | `ALWAYS` | When a second or later character may be made. `FIRSTONLY` allows only the first. `CHEAT` allows more only after `CHEAT ON`. `MASK` runs `NEWCHARMASK` against the leader (the character played last). Anything starting `FIRST`, `CHEAT` or `MASK` is accepted. |
| `NEWCHARMASK` | zapper mask | empty | Used with `NEWCHARS=MASK`, for example `-LOCATION +ROOMSTR(Guild)`. An empty mask allows creation. |
| `NEWCHARMESSAGE` | text | empty | Said instead of the default refusal. |
| `NEWCHARLEVEL`, `NEWCHARXP` | see [Characters](03-characters.md) | `1`, `KEEP` | The level a later character starts at (by real level-ups). |
| `ACTIVECHARMAX` | number, `0` for no limit | `0` | Also refuses creation while that many of the profile's characters are already in the world ("Rest a character before bringing in another"). |
| `PARTYROLES` | `role:mask; role:mask` | empty | See [Roles](#roles-partyroles). Creation (`NEW` at the menu) warns about roles no character fills. |

The refusal strings come from `WorldRules.newCharRefusal`: "You may only have N
character(s) in this world." (PROFILEMAX), "In this world you play the one
character you made first." (FIRSTONLY), "New characters are made only with
CHEAT ON." and "New characters can be made only when ..." (MASK).

Lowering `PROFILEMAX` (or any limit below) does not delete characters from an
existing campaign; it only blocks further additions.

`CHEAT ON` and `CHEAT OFF` toggle the cheat flag that `NEWCHARS=CHEAT` tests.
It is stored in the save (header key `cheat`) and the save is listed as
`[cheat]` in `SAVES`.

Source: [WorldRules.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/WorldRules.java),
[CharCreation.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CharCreation.java)
(`singlePlayerStart`, `getProfile`).

## The party

### The four states of a character

Every character of the profile is in exactly one state:

| State | In the world? | Has a session? | How you act as it |
| --- | --- | --- | --- |
| **Played** | yes | the console | Type commands. |
| **In the party** | yes, following the party's leader | a session of its own with nothing behind it (a `PartySession`), or a window | `AS name command`, `SWITCH name`, or its window. |
| **Elsewhere in the world** | yes, not following the leader | the same | The same. Left where it stands. |
| **Resting** | no | none | `PARTY ADD name` brings it in; `SWITCH name` plays it. |

A *party* is the group that follows one leader: ordinary CoffeeMud following,
restricted here to your own characters. `PARTY` with no arguments shows all four
states:

```text
Your party:
  Hero, level 3 Fighter (you), leading
  Bran, level 3 Mage, acting on their own
Elsewhere in the world:
  Mira, level 2 Thief, in The Old Mill
Resting:
  Tolliver
```

("You are on your own." replaces "Your party:" when you lead no one. `, with
orders` is added to a member's line in turn-based time when it has an order
queued.) If `PARTYROLES` is set, a line such as `Your party has no healer or
scout.` follows.

There is no command called `CONTROL`. "Taking control" of a character is
`SWITCH` (to play it) or `AS` (to give it one command); the handbook map and
older notes sometimes say CONTROL for these.

### PARTY command reference

`PARTY` is available only in single-player with the profile on (it is not
orderable). Names may be abbreviated: the whole name is matched first, then the
start of a name.

| Syntax | Effect |
| --- | --- |
| `PARTY` | Lists the party, the others in the world, and the resting. |
| `PARTY ADD name` (alias `JOIN`) | Brings a character into your party. A resting character comes into the world; a character already in the world is moved to you (or told to wait, see PARTYSUMMON/MEET). The character follows the party's leader and the party order gets them appended. |
| `PARTY REMOVE name` (alias `LEAVE`) | Takes a member out of the party; they stay in the world where they are. You cannot remove the leader (remove the others). |
| `PARTY REST name` (alias `DISMISS`) | Saves a character that is in the world but not played, and takes them out of the world to rest. If they were leading, the lead passes to the next member. You cannot rest the character you are playing: `SWITCH` to another or `LOGOFF`. |
| `PARTY MEET ON` / `OFF` | Chooses where an added character appears; see [Meeting](#where-companions-appear-partysummon-and-partymeet). With no argument it shows the setting. |
| `PARTY FIGHT ON` / `OFF` | Lets your own characters attack each other, or not. With no argument it shows the setting. |
| `PARTY ORDER name name ...` | Sets the order in which members act in turn-based time. With no names it shows the order. |
| `PARTY LEAD name` | Hands the lead to another member: they follow no one and everyone else, the old leader included, follows them. |
| `PARTY DISABLE name` | The member acts "on their own" in turn-based time (the turn does not wait for an order for them). |
| `PARTY ENABLE name` | Undoes `DISABLE`. |
| `PARTY RULES` | Shows this world's party size, profile size, active-character limit and summoning rule. |
| `PARTY CLANS` | Lists the party's clan memberships and ranks. |
| `PARTY MORTAL name` / `PARTY ARCHON name` | Builder mode only: a party member tests as a mortal (with `Prop_Playtest`) or as an Archon. `ME` names yourself. |

Joining and leaving are visible in the room ("Bran joins your party.", "Bran
leaves your party.") and raise the [`PARTY_PROG`](26-mobprog-triggers.md#party_prog)
events `JOIN`, `LEAVE`, `LEAD` and `REST`.

Source: [PartyCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/PartyCmd.java),
[Party.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Party.java).

### AS: telling a member to do something

```text
AS bran say hello
AS bran north
AS mira cast magic missile goblin
```

`AS name command` has one of your characters (in the world, not the one you
play) do the command. The effect is shown on your terminal as if you were
looking over their shoulder: the engine briefly swaps the terminal's session
with the member's for the duration of the command (`Party.actWithTerminal`).
The member does the command with their own skills, items and location.

- In **real time** it is done at once.
- In **turn-based time** it goes into the member's command queue like any
  command. A command that takes time waits for the leader's turn (see
  [Party turns](#party-turns-in-turn-based-time)); one that costs no time (a
  look, an inventory) happens at once.
- `AS` also exists as the inherited CoffeeMud archon command (do something as
  any player or mob, `AS port`, `AS PLAYERS`). It needs the `AS` security flag.
  CoffeeSUD checks the party first: if the name is one of your own characters
  in the world, the party form is used and no flag is needed. `securityCheck`
  allows everyone in single-player.
- `AS` gives no way to command a character you are already playing; just type.

Errors you may see: "As whom do what?" (fewer than two words), "You don't know
of anyone by that name." (not a party member and not found by the archon form).

### SWITCH: playing another character

`SWITCH name` plays another character of your profile.

- If the other character is **in the world**, the two simply exchange sessions:
  you now type as them, the one you were playing stays where it is and becomes a
  party session. Both are looked at again. This works even if the target is
  feigning death.
- If the other character is **resting**, the one you were playing leaves the
  world (as `LOGOFF` does, without saving) and the other enters where they last
  were.
- You cannot `SWITCH` to someone else's character; the target must be in your
  profile (sysops can use `SWITCH override name`).
- A recent player-versus-player fight blocks `SWITCH` for five minutes, as it
  does `QUIT` (only relevant when `PARTY FIGHT` is on or `PLAYERKILL` allows
  it).

It raises `GAME_PROG SWITCHFROM` and `SWITCHTO`, the JavaScript `onSwitch(from,
to)` hook, and, when both are in the same party, `PARTY_PROG SWITCH`.

### Bringing characters in, and resting them

To bring a character in: `PARTY ADD name`. To take a character out of the
world: `PARTY REST name`, or close that character's window, or `SWITCH` away from
the played character when the target is resting. Resting saves the character
(`savePlayer`) and removes their session. The character keeps their location
for next time.

At a restore, the characters that were in the world when the save was made are
brought back in automatically, each where they were, followers and effects
included. The limit `ACTIVECHARMAX` is not applied to those (a limit lowered
since the save does not leave anyone behind).

### Where companions appear: PARTYSUMMON and PARTYMEET

When `PARTY ADD` names a character who is not where you are, the engine either
pulls them to you or makes you go to them.

| Setting | Values | Default | Meaning |
| --- | --- | --- | --- |
| `PARTYMEET` | `1` or blank/`0` | blank | Start-of-game value of `PARTY MEET`: `1` means companions stay where they are (a resting one returns where they last were) and you go to meet them. Only the exact value `1` counts. The `PARTY MEET ON/OFF` command changes it during play, and a save keeps it (`partymeet`). |
| `PARTYSUMMON` | `ALLOW`, `MEET`, `MASK` | `ALLOW` | The world's rule. `ALLOW`: the player's `PARTY MEET` choice decides. `MEET`: always meet; `PARTY MEET OFF` is refused. `MASK`: companions can be called to you only where the caller passes `PARTYSUMMONMASK`. Any other value counts as `MEET`. |
| `PARTYSUMMONMASK` | zapper mask | empty | Used by `PARTYSUMMON=MASK`. The mask is run on the character who gives the `PARTY ADD`, including where they stand, so `-AREA "+Sanctuary"` or `-LOCATION +ROOMSTR(Inn)` limit summoning to a place. An empty mask in `MASK` mode refuses summoning. |
| `PARTYSUMMONMESSAGE` | text | empty | What the player is told when the mask refuses; otherwise "You cannot summon companions here." |

Characters already standing in the same room can always be added. When
meeting is required and the companion is elsewhere, `PARTY ADD` says "Bran is in
The Old Mill.  Go and meet them there, then PARTY ADD them." The world's `MEET`
rule shows in `PARTY RULES` as "meet companions where they are".

Source: `WorldRules.partySummon`, `WorldRules.summonRefusal`,
`Party.isMeetWhereLeft`.

### Size limits

| Key | Counts | Refusal |
| --- | --- | --- |
| `PARTYMAX` | Characters of the profile in one party, the leader included. `0` for no limit. | "Your party may have at most N character(s) in it." |
| `ACTIVECHARMAX` | All of the profile's characters in the world, whether in the party or not. `0` for no limit. | "This world allows at most N of your characters in the world at once." |
| `PROFILEMAX` | All characters in the profile. | "You may only have N character(s) in this world." |
| `MAXCONNSPERACCOUNT` (engine ini) | Characters of the profile in the world at once, an engine limit. Ships as `50`. | "You may only have N of your characters on at one time." |

Ordinary `FOLLOW` observes `PARTYMAX` too, for characters of the same profile
(`WorldRules.followRefusal`): you cannot get around the limit by having characters
follow each other instead of using `PARTY ADD`. NPC followers (pets, hirelings, charmed creatures) do not count; only
profile characters do.

Example (also in [`docs/party-rules.md`](../party-rules.md)):

```ini
# world/world.ini
PARTYMAX=4
ACTIVECHARMAX=4
PROFILEMAX=8
PARTYSUMMON=MEET
```

### Roles (PARTYROLES)

```ini
PARTYROLES="healer:-BASECLASS +Cleric +Druid; scout:-BASECLASS +Thief"
```

Entries are `role:mask`, separated by semicolons. `PARTY` and the `NEW` step of
the profile menu warn about roles that none of the characters fills: "Your party
has no healer or scout." It is advice only; nothing is refused. A role with an
empty mask is filled by anyone.

### Fighting each other: PARTYFIGHT

`PARTYFIGHT=1` (or `PARTY FIGHT ON`) lets your characters attack each other.
It works by setting the `PLAYERKILL` attribute on everyone and the server-wide
`PLAYERKILL` setting to `ALWAYS-100000` (no level limit), so `PARTYFIGHT`
overrides `PLAYERKILL` in the ini. `PARTY FIGHT OFF` (the default) stops any
fight between them. A save keeps the setting (`partyfight`). Without it, an
attack on a party member answers "You may not attack Bran."

### Followers and companions

Two different things are called companions:

- **Your other characters** (this chapter).
- **NPC followers**: pets, hirelings, charmed or summoned creatures that follow
  a character. A *player's* followers are saved with that player
  (`<FOLLOWERS>` in `world.xml`), including how hurt they are and their timed
  effects, and are restored beside them. A follower that cannot be saved (for
  example some summoned creatures that nothing keeps alive) is not kept, and
  `SAVES CHECK` says so. See [NPC life and dialogue](06-npcs-dialogue.md).

A follower of a party member who is not the played character is saved with that
member.

Characters who follow the leader also help fight ("Who follows the leader also
helps them fight, unless AUTOASSIST is off", `HELP PARTY`).

### Script hooks

| Hook | Fires |
| --- | --- |
| [`PARTY_PROG`](26-mobprog-triggers.md#party_prog) `JOIN/LEAVE/REST/LEAD/SWITCH/ANY` | Party membership changes. `$n` is the member, `$t` the leader, `$g` the event. |
| [`GAME_PROG`](26-mobprog-triggers.md#game_prog) `SWITCHFROM/SWITCHTO` | The player switches characters. |
| [`TURN_PROG`](26-mobprog-triggers.md#turn_prog) | A party's turn ends in turn-based time. |
| Script functions `PARTYSIZE()`, `INPARTY()`, `PARTYLEADER()`, `PARTY()` | See [MOBPROG functions](28-mobprog-functions.md). The fixture `st10-party.js` exercises them. |
| `onSwitch(from, to)` (JavaScript) | Player switch; see [JavaScript](05-javascript.md). |

## Windows and the console

Your terminal (standard input and output) is the game's only connection. The
character you play there is the *played* character. Every other character of
your profile that is in the world has a session of its own with nothing behind
it: it receives text (so scripts, spells and messages work) but reads nothing.
You act for it with `AS` or `SWITCH`, or give it a window.

### WINDOWS

With windows on, each character in the world other than the played one gets its
own window. It shows what that character sees and takes that character's
commands, so you can drive several characters at once without switching.

| Command | Effect |
| --- | --- |
| `WINDOWS` | Shows whether windows are on. |
| `WINDOWS ON` | Opens a window for each character in the world now, and for each that comes in later. Refused with "There is no display here, so the windows cannot open." when Java has no display. |
| `WINDOWS OFF` | Closes them all. The characters stay in the world, back on plain party sessions. |
| `WINDOWS PANEL` | Shows the panel setting. |
| `WINDOWS PANEL ON` | Gives each window a panel beside its text with all sections. |
| `WINDOWS PANEL OFF` | Takes the panel away. |
| `WINDOWS PANEL STATUS,MAP,PARTY` | Shows only the sections named, in the order given (separators: comma, semicolon or space). Anything else is refused with the list of sections. |

Closing a window with the mouse sends that character to rest (`PARTY REST`
would do the same). If they led the party, the lead passes on.

Start-up settings:

| Key | Where | Meaning |
| --- | --- | --- |
| `WINDOWS=1` | `coffeemud.ini` or command line | Start with windows on. With no display the launcher prints "There is no display here, so the characters' windows (WINDOWS=1) are off." and carries on. |
| `WINDOWPANEL=STATUS,MAP,PARTY` | `coffeemud.ini` or command line (not `world.ini`) | The panel sections at start. Blank or `OFF` for none. |

Neither setting is saved with a campaign. `WINDOWS ON` made in play is lost on
the next start.

Panel sections:

| Section | Shows |
| --- | --- |
| `STATUS` | `world/text/panel.txt` if the world has one, with the prompt codes of the `PROMPT` command (`%h`, `%H`, `%r`, `%z`, `%{VAR NAME}`, colour codes), else the character's points, room and area. |
| `MAP` | The rooms about the character, as the regional awareness map draws them (seven rooms across). |
| `PARTY` | Each party member in the party's order, with a bar of hit points. |

The panel is drawn again at every prompt. World creators who want the status
panel to show campaign variables use the `%{VAR ...}` prompt codes
([Presentation and language](11-presentation-language.md)).

In turn-based time a window's character is bound by the same rules as `AS`: a
command that takes time waits for the leader's turn.

### Headless runs

Without a display, windows cannot open and every non-played character uses a
plain `PartySession`. The game itself needs no display: `CHECK=1` loads the world
and exits, and `tools/sudplay.py` and `tools/playtest.sh` drive the console
from scripts (see [Installing and running](18-installing-and-running.md) and
[Testing and release](13-testing-release.md)). To force headless for tests,
launch Java with `-Djava.awt.headless=true`.

## Time: real time, pause and turns

CoffeeSUD owns the game clock (`GameTime`). The calendar, effects, cooldowns,
ticks, decay and weather all read it, so changing how it moves changes
everything at once. Logs, idle timers and "last login" dates stay on the system
clock.

### Real time

In real time the clock follows the system clock, and the world ticks on its own
(a tick is four seconds, `HELP TIMEMODE`). A game starts in real time unless
`TIMEMODE=TURNS` is set. `MILLISPERMUDHOUR` and the other calendar keys decide how
long a game hour is ([World simulation](10-world-simulation.md)).

`TIMESCALE` speeds up or slows down real time: `TIMESCALE=8` in `coffeemud.ini`
(or the `TIMESCALE` command) makes eight game seconds pass in each real second.
Allowed range 0.1 to 20; the prompt shows `(x8)` when the rate is not 1. The
command is limited to builder mode and `DEV=1` games, the setting is not saved,
and pause and turn-based time work as before (the rate waits).

### PAUSE and RESUME

In single-player `PAUSE` stops the clock and `PAUSE` again (or `RESUME`,
`UNPAUSE`, `PAUSE RESUME`) starts it. While paused:

- Nothing ticks: no monster moves, no effect expires, no regeneration.
- Commands that take time wait in the character's queue until you resume.
- Commands that cost no time (`LOOK`, `INVENTORY`, `SCORE`, `SAVE`) work.
- `WAIT` lets one tick (or the number you name) pass.
- The prompt shows `(paused)`.
- A save made while paused restores paused (`paused=true` in the header).

In turn-based time `PAUSE` only reminds you that the world already waits.
`PAUSE` raises `GAME_PROG PAUSE` and `RESUME`. The inherited archon forms
`PAUSE AREA`, `PAUSE ROOM` and `PAUSE name` (suspend or resume the ticks of one
object) still exist and need the `PAUSE` security flag, which a normal player
does not have.

### Turn-based time

Turn-based time stops the clock and moves it only when you ask:

| Command | Effect |
| --- | --- |
| `TIMEMODE` | Says which mode you are in. |
| `TIMEMODE TURNS` (also `TURN`, `TURNBASED`, `TURN-BASED`) | Switches to turn-based time and unpauses. |
| `TIMEMODE REALTIME` (also `REAL`, `REAL-TIME`) | Switches back to real time. |
| `TIMEMODE DIALOGUE INSTANT` / `TICKS` / `DEFAULT` | Chooses whether NPCs answer at once (before your next prompt, taking no time) or on their own ticks. `DEFAULT` is instant in turn-based time and ticks in real time. Same as `DIALOGUETIME` in the ini or world. |
| `WAIT`, `WAIT n` | Lets one (or n, up to 100) ticks pass. |
| `WAIT n HOURS/DAYS/WEEKS`, `WAIT UNTIL ...` | Lets game time pass in long steps (up to three game days); governed by `LONGWAIT`. `HELP WAIT` lists the forms. |

What moves the clock in turn-based time:

1. Doing something that **takes time**: the world then moves forward by as long
   as the action takes. A command's cost is its action cost divided by the
   character's speed, times a tick. Commands that take time (movement, skills,
   attacks, `SWITCH` and the like) cost `DEFCMDTIME` actions out of combat
   (0.20 by default, so a step is a fifth of a tick) and `DEFCOMCMDTIME` in
   combat (1.0, a whole tick); the ini can list exceptions per command
   (`DEFCMDTIME=Score 1.0, East 0.5, 0.15`). Commands without a cost of their
   own, such as `SAVE`, `RESTORE`, `PARTY`, `AS`, `WINDOWS` and `WAIT`, take no
   time.
2. `WAIT`.
3. A long `WAIT`, which runs the game's own ticks (so one heals, shops restock,
   things respawn and `TIME_PROG`s fire), and ends early if you are hurt,
   attacked or moved.

Things that cost no time never move the world: looking, `SCORE`, `INVENTORY`,
`SAVE`, most `PARTY` and `WINDOWS` commands. While the world is catching up, a
second action waits.

The `TIMEMODE` key in `coffeemud.ini` or `world.ini` sets how a *new* game
starts (the value only needs to start with `TURN`). A restored game keeps the
mode it was saved with (`timemode=` in the header). The player can change the mode at
any time, so a world designed for one mode (for example puzzles that assume
enemies do nothing while you think) should either say so in its text or lock
the mode with [`Prop_TimeMode`](23-properties-reference.md#prop_timemode).

`Prop_TimeMode MODE=TURNS|REALTIME [ONLYCOMBAT=TRUE] [RESTORE=TRUE|FALSE]
[MSG="..."]` on a room or area holds the time mode while a player is there:
coming in switches to MODE and says MSG, `TIMEMODE` is refused ("Time is held
turn by turn here, and cannot be changed until you leave."), leaving puts back
the mode that was in force (unless `RESTORE=FALSE`), and `ONLYCOMBAT` holds it
only during a fight. The mode "before" is saved (`timemodebefore`) so a save made
inside the zone restores correctly. The fixture `w28-timemode.js` exercises it.

The script events: `GAME_PROG TIMEMODE` with detail `TURNS` or `REALTIME`.

### Party turns in turn-based time

When one profile has several characters in the world following a leader, only
the **leader** moves the world. A member who is given something that takes time
queues it as an **order**; orders run on the leader's turn. `PARTYTURNS` chooses
how.

| Setting | `LEADER` (default) | `ALL` |
| --- | --- | --- |
| Who is waited for | Nobody. | Every living member that is not `DISABLE`d must have an order or have used `WAIT`. |
| What happens when the leader acts | Each member's queued order runs first (in the party order), then the leader's action, then the world moves on by as long as the leader's action took. | The leader's action is held until everyone is ready: "The turn waits for Bran to be given something to do (AS NAME COMMAND), or to WAIT." Then members placed before the leader in the party order act, the leader acts, members after act, and the world moves on by at least one tick. |
| Leader uses `WAIT` | Members carry out their orders, then time passes. | The same. |
| Member uses `WAIT` | "You wait for Hero's turn." | The same, and the turn no longer waits for them. |
| Member types a time-taking command | "Bran will do that on Hero's turn." (once per turn); it is queued. | The same. |

A member who is still busy with a long action, or has no actions left, keeps the
order for a later turn. A character who is not in a party (or the only member
playing) simply moves the world as in ordinary turn-based play. Orders are given
with `AS name command`, by `SWITCH`ing to a member and typing, or in the
member's window.

`PARTYTURNS` alone shows the setting. `PARTY ORDER`, `PARTY LEAD`, `PARTY
DISABLE` and `PARTY ENABLE` control who acts when. The start-up value is
`PARTYTURNS=LEADER` or `ALL` in `coffeemud.ini`/`world.ini`; a save keeps the
setting, the order (`partyorder`) and the "on their own" list (`partyalone`).

`TURN_PROG` fires once per party turn in turn-based time, and per time-taking
player command in real time.

Source: [GameTime.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/GameTime.java),
[Party.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Party.java)
(`turnOf`, `beforeLeaderActs`, `afterLeaderActs`, `passTurn`),
`StdMOB.dequeCommand`, [Wait.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Wait.java),
[TimeMode.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/TimeMode.java).

## Saving

### The commands

| Command | Effect |
| --- | --- |
| `SAVE` | Saves the game under the default name. |
| `SAVE name` | Saves under `name`. |
| `SAVES` (or `RESTORE` with no name) | Lists the saved games, newest first, numbered. |
| `RESTORE name` or `RESTORE number` | Restores a save after a yes/no question. |
| `RESTORE name FRESH=CHANGED` / `ALL` / `Area,Area` | Restores, taking some areas from the world as it is now ([below](#restoring-after-the-world-changed)). |
| `SAVES CHECK` | Says what a save made now would not keep. |
| `SAVES INFO name` | Describes one save. |
| `SAVES COPY from to` | Copies a save to a new name. |
| `SAVES DIFF a b` | Lists what differs between two saves. |
| `SAVES DELETE name` | Deletes a save (and its recovery generations) after asking. |
| `CHECKPOINT`, `ROLLBACK` | Quick saves in a ring of three; see below. |
| `QUIT` | Offers to save before leaving. |

All of these are single-player commands. In builder mode `SAVE` writes the
world files instead (see [World files](02-world-workflow.md)) and `SAVES` and
`RESTORE` say "There are no saved games in builder mode."

### Slot names

A slot name is 1 to 32 letters, digits, dashes and underscores, and must start
with a letter or digit. Names are case-insensitive for lookup. You can also use
a save's number from the `SAVES` list. The folder on disk has the same name.

`SAVE` with no name writes to the save this game came from or was last saved
to. In a brand-new game that has no save yet, the name is made from the
character's name (lower case, only letters, digits, `-` and `_`; `game` if
nothing is left).

One trap: in a normal game, `SAVE` followed by any of the builder words `USERS
PLAYERS CHARACTERS USER PLAYER CHARACTER CHAR GRACES ITEMS AREA REWORLD ROOM
MOBS QUESTS FACTION` (as the first or the last word) is the inherited builder
SAVE, not a game save. Without builder powers you get "You are not allowed to
do that."; with them it saves objects to the (copy of the) database and warns
that the game plays on a copy of the world. So you cannot name a slot `room`.

### What SAVE does

1. Checks the [save rules](#save-rules-for-world-creators) (`SAVEMODE`,
   `Prop_NoSave`, `Prop_SavePoint`).
2. Runs the save hooks. `onBeforeSave(slot)` in `world/js/init.js`, JavaScript
   `cm.on('save')` listeners and `GAME_PROG SAVE` scripts all run before the
   snapshot is taken, and any of them may refuse the save ("The game was not
   saved."). What they set is saved. See [JavaScript](05-javascript.md) and
   [`GAME_PROG`](26-mobprog-triggers.md#game_prog).
3. Saves the played character and every other character in the world to the
   database tables, saves the clocks, flushes database writes.
4. Writes the snapshot folder (see [The save folder](#the-save-folder)).
5. Prints `Game saved as 'name'.`, preceded by the save point's `MSG=` if the room
   or area has a [`Prop_SavePoint`](23-properties-reference.md#prop_savepoint),
   followed by a line `Kept in part: <skill> under way.  A restore starts those
   parts afresh.` for each crafting or gathering activity the save could only keep
   in part.

While an item transaction is being committed a save is refused ("An item
transaction is committing; save at a settled boundary."); try again.

### RESTORE

`RESTORE name` asks `Restore 'name'?  Anything since you last saved will be lost
(y/N)?`. On yes it checks the save is whole and its format supported
([The save folder](#the-save-folder)), checks the optional modules it needs
([Optional modules](14-optional-modules.md)), prints `Restoring 'name'...`, and
restarts the engine in the same process: the world is rebuilt from its files and
the save is laid over it. The terminal stays; the game logs your saved character
in again.

- An IRONMAN game refuses: "This is an ironman game: there is no going back to a
  save.  QUIT keeps the game."
- A save that is incomplete or in an unsupported format is refused with the
  reason, and the running game continues.
- `RESTORE 2` uses the number from `SAVES`.

When CoffeeSUD starts it lists the saves and asks which to restore (`C`ontinue the
newest, `N`ew game, `Q`uit, or a name or number). `RESTORE=name` or `RESTORE=NEW`
on the command line skips the question, and `FRESH=` on the command line goes
with `RESTORE=name` (it is not read when you choose from the menu). See
[Installing and running](18-installing-and-running.md).

### SAVES, SAVES INFO and SAVES DIFF

`SAVES` prints one line per save:

```text
Saved games:
   1) team         Hero, level 4 Fighter, with Bran, Mira, in The Rocky Room, saved Nov 6 14:03 (this game)
   2) before-boss  Hero, level 3 Fighter, in The Old Mill, saved Nov 5 21:40 (older world)
```

`(this game)` marks the save this campaign came from or was last saved to;
`(older world)` marks a save made with a different version of the world files;
`[dev]` is a save from a `DEV=1` game and `[cheat]` a save with `CHEAT ON`.

`SAVES INFO name` prints the character, place, save time, game date, time mode
(and `, paused`), save mode, the party members and where they are, the running
quests with their steps, how many script variables and JavaScript stores the
save holds, whether it was made in a developer's game or with cheats on, and the
world sum with `(this world)` or `(older world)`.

`SAVES DIFF a b` compares two saves and prints lines such as `place: The Old
Mill -> The Rocky Room`, `player hp: 40 -> 31`, `carried: +potion`, `variable
Throne DOORS: 0 -> 1`, `quest Siege: running, step 1 -> running, step 2`, and,
for each room, `Room#12 mobs: -2 goblin`. It compares the header keys
`character level class place area timemode savemode party gamedate`, the played
character's room, hit points, mana, movement and inventory, script variables,
quests, and the mobs and items placed in each room. It is the quickest way to
see what a fight or a decision changed.

`SAVES CHECK` lists what a save made right now would not keep:

```text
A save now would not keep:
  a sprite in The Glade (Glade#3) cannot be saved, so a restore will not have it, nor its scripts' state.
  4 things in space (ships, stations, planets) are not saved where they are.
```

It reports monsters that cannot be saved (flagged unsavable, or summoned with no
one to follow) and the things in space. "A save now would keep everything in the
world." means nothing is lost.

### CHECKPOINT and ROLLBACK

`CHECKPOINT` saves into `checkpoint1`, `checkpoint2` or `checkpoint3`, in turn:
the slot never written, else the oldest. `ROLLBACK` restores the newest after
asking `Go back to checkpoint 'checkpoint2'?  Anything since then will be lost
(y/N)?`. Both obey the save rules. (`Prop_Checkpoint` on a room is a different
thing, a respawn point; see [Combat and encounters](29-combat-and-encounters.md).)

### Autosave

Three ways, all writing ordinary saves:

| Way | Setting | Slots |
| --- | --- | --- |
| Timed | `AUTOSAVE=30` in `coffeemud.ini` or `world.ini`: every 30 *game* minutes (the game clock, so turn-based play counts game time passing) | `autosave1` ... `autosaveN`, N = `AUTOSAVEKEEP` (default 3, at least 1), taking turns |
| On changing area | `AUTOSAVE=ON-AREA-CHANGE` (anything starting `ON-AREA`) | the same |
| By place | [`Prop_AutoSave`](23-properties-reference.md#prop_autosave) on a room or area | `SLOT=` (default `auto`), with `ROTATE=n` for `slot1`..`slotn` |

Blank or `0` for `AUTOSAVE` means none. The player is told `[Autosaved as
'autosave2']` (timed) or `[Autosaved]` (or the property's `MSG=`). Timed
autosaving starts when the played character enters the game and watches the
played character's area.

A timed or area autosave skips a fight unless the save mode is `ANYWHERE`; a
refused autosave is silent (a timed one is tried again at the next tick, an
area one at the next area change). `Prop_AutoSave`
saves even where `Prop_NoSave` or the lack of a save point would refuse a `SAVE`;
the timed autosave also ignores `Prop_NoSave` (it only checks the fighting rule).
In an ironman game an autosave writes the game's single save.

### QUIT

`QUIT` in a normal game asks `Quit -- save the game as 'hero' first (Y/n), or
cancel (c)?`. `Y` saves (obeying the save rules; if they refuse, you are told why
and are not quit), `n` quits without saving, `c` cancels. If the rules forbid a
save here the question becomes the reason plus `Quit without saving (n), or
cancel (C)?`. Ending the terminal's input (end of file) quits without writing
anything.

## Save rules for world creators

A world decides **when** a campaign may be saved. This is the main tool for
hardcore, roguelike or checkpoint-based designs. Three things work together: the
`SAVEMODE` setting, and the room/area properties `Prop_SavePoint`, `Prop_NoSave`
and `Prop_AutoSave`.

### SAVEMODE

`SAVEMODE` goes in `world/world.ini` or `coffeemud.ini` (the world wins). It is
read when a **new** game starts, and the mode is then kept in the campaign's
saves (`savemode=` in the header). A restored game uses the mode in its save,
whatever the ini says now, so editing `world.ini` never changes a running
campaign's rules.

| Value | Meaning |
| --- | --- |
| `ANYWHERE` (default) | Save at any time, even in a fight. |
| `NOTINCOMBAT` | Refuses while the played character, any of your characters in the world, or anyone in the played character's group is in combat: "You cannot save the game during a fight." |
| `SAVEPOINTS` | As `NOTINCOMBAT`, and only in a room whose own effects, its area's, or a parent area's include `Prop_SavePoint`: "You can only save the game at a save point." |
| `IRONMAN` | One save only. `SAVE` with any other name is refused ("This is an ironman game: it has one save, 'hero', which it keeps for you."), `RESTORE` and `ROLLBACK` in play are refused, and `QUIT` always writes the save wherever you stand (`Quit -- the game is saved as 'hero' (Y), or cancel (c)?`). The name is the default slot (the campaign's name, or made from the character's name). The launcher still lists the save at the next start, so you can continue. |

An unrecognised value is logged and counts as `ANYWHERE`.

### How a save is judged (order of checks)

For `SAVE`, `CHECKPOINT` and the offer in `QUIT` (`SaveGame.whyNotSave`):

1. Ironman: a different slot name is refused; saving at `QUIT` is always allowed.
2. `Prop_NoSave` in the room, its area or a parent area: refused with its `MSG=` or
   "You cannot save the game here." (so a no-save zone beats a save point).
3. Any mode but `ANYWHERE` and the party is fighting: refused.
4. `SAVEPOINTS` and no `Prop_SavePoint` here: refused.
5. Then the [save hooks](#what-save-does) may still refuse.

Autosaves (the timed ones and `Prop_AutoSave`) only perform step 3, so a no-save
area does not stop them. That is deliberate: a `Prop_AutoSave` is how a world
saves *for* the player in a place where they may not save themselves.

### Prop_SavePoint

```text
Prop_SavePoint MSG="You rest by the fire and set down your journey."
```

On a room or an area (every room of it, and of sub-areas). `MSG=` is printed after
`Game saved as 'x'.` Only meaningful with `SAVEMODE=SAVEPOINTS`; harmless
otherwise. See [Prop_SavePoint](23-properties-reference.md#prop_savepoint).

### Prop_NoSave

```text
Prop_NoSave MSG="The water's whispering drowns your thoughts."
```

On a room or an area. The game cannot be saved here with `SAVE`, `CHECKPOINT` or
`QUIT`'s offer (an ironman `QUIT` still keeps the game). See
[Prop_NoSave](23-properties-reference.md#prop_nosave).

### Prop_AutoSave

```text
Prop_AutoSave ONCE=CHAR SLOT=gate ROTATE=3 MSG="[Progress kept]"
```

Saves when the played character comes into the room (or into the area from
outside it). `ONCE=CHAR` only the first time for each character (a tattoo
`AUTOSAVE:ROOMID` records it); `SLOT=` the slot name (default `auto`); `ROTATE=n`
uses `SLOT1`..`SLOTn` in turn; `MSG=` replaces `[Autosaved]`. It does nothing for
a party member who is not the character played at the terminal. See
[Prop_AutoSave](23-properties-reference.md#prop_autosave).

### Putting properties on rooms

In builder mode: `MODIFY ROOM AFFECTS` (or `MODIFY AREA AFFECTS`) opens the effects
editor; add `Prop_SavePoint` and enter its text. In the world file the effect is
the usual effect entry, in a room's `RTEXT` (XML-escaped there):

```xml
<AFFECS><AFF><ACLASS>Prop_SavePoint</ACLASS><ATEXT>MSG="You rest by the fire."</ATEXT></AFF></AFFECS>
```

and in an area's `ADATA` block the same `AFFECS` element, unescaped. See
[Areas, rooms and exits](20-areas-rooms-exits.md) and
[World files](02-world-workflow.md).

### Other save-related settings

| Key | Where | Default | Effect |
| --- | --- | --- | --- |
| `SAVEMODE` | ini/world | `ANYWHERE` | See above. |
| `AUTOSAVE`, `AUTOSAVEKEEP` | ini/world | blank, `3` | Timed autosave. |
| `SAVEDIR` | ini or command line (not world.ini) | `saves` | Folder for saves, relative to `CoffeeMud/` unless absolute. |
| `RESTORE` | ini or command line | blank | `NAME` restores that save at start without asking; `NEW` starts a new game. |
| `FRESH` | command line | blank | With `RESTORE=name`, same as `RESTORE name FRESH=`. |
| `PARTYDEFEAT` | ini | `DEATH` | `CHECKPOINT` has a defeated party wake together at the leader's checkpoint ([Combat and encounters](29-combat-and-encounters.md)). |
| `LONGWAIT` | ini/world | `ALL` | `BUILDERS` restricts long `WAIT`s. |
| `BUILDER` | command line | blank | Builder mode: no saves. |
| `DEV` | command line | blank | A developer's game: saves are marked `[dev]`. |

## The save folder

A save is a folder named after the slot, under `SAVEDIR`:

```text
saves/
  .owner.lock                   held by the running game (never delete while a game runs)
  hero/                         one save
    header.properties           who, where, when, clock, time mode, party, sums
    db/                         the state tables, as fake-database files
      fakedb.data.CMCHAR        characters
      fakedb.data.CMCHAB        their abilities
      fakedb.data.CMCHIT        their items
      fakedb.data.CMCHFO        their followers
      fakedb.data.CMCHCL        their classes' data
      fakedb.data.CMACCT        the profile
      fakedb.data.CMPDAT        per-player and shared data
      fakedb.data.CMJRNL        journals
      fakedb.data.CMCLAN        clans
      fakedb.data.CMCLIT        clan items
    world.xml                   the live world (rooms' contents, quests, variables ...)
    world-manifest.properties   checksums of the world files the game had loaded
    modules.json                optional-module state (only when modules are in use)
    .complete                   SHA-256 of every file above: proves the save is whole
  autosave1/ autosave2/ ...     rotating autosaves
  checkpoint1/ ...              CHECKPOINT ring
  .play-1730900000000/          the running game's working database (removed at the next start)
  .old-hero/                    the previous generation of hero
  .new-hero/                    hero being written
  .damaged-hero-1730900100000/  a hero found damaged and set aside
  recordings/                   RECORDINPUT playtest files
```

Dot-folders are never listed as saves. Only folders with a valid name and a
`header.properties` are.

### Writing a save safely

`SaveSlots.write` never edits a save in place:

1. Write everything into `.new-NAME` (tables, `world.xml`, manifest, `modules.json`,
   header), flush each file to disk, then write `.complete`, a Java properties file
   with one `path=sha256` line per file, and flush the folders.
2. Delete the previous `.old-NAME`, rename the current `NAME` to `.old-NAME`.
3. Rename `.new-NAME` to `NAME`. If that fails, `.old-NAME` is put back.

The last complete generation therefore stays as `.old-NAME` until the next
successful write. The save folder is owned by one process at a time; a second
game on the same `SAVEDIR` is refused with "Another game is using the save
folder ... Close it or choose a different SAVEDIR." The operating system releases
the lock if the game crashes, and the stale lock file is harmless.

### Recovery

At every start, listing, save and delete, the folder is repaired
(`SaveSlots.recover`). For each name that has a `.old-` or `.new-` folder, and
whose public folder is not whole:

| Situation | Result |
| --- | --- |
| Public folder whole | Nothing to do. |
| Public folder missing (crash between the two renames) and `.new-NAME` whole | `.new-NAME` becomes `NAME`: the save you asked for. |
| Public folder missing or damaged and `.old-NAME` whole | `.old-NAME` becomes `NAME` (the previous generation). |
| Only `.new-NAME` whole | It becomes `NAME`. |
| None whole | Nothing is done; `NAME` stays as it is and will be refused. |

A damaged public folder is not deleted: it is renamed to
`.damaged-NAME-<milliseconds>` so that you can inspect it. "Whole" means: a
non-empty readable `header.properties`, a `db/` folder, `world.xml`, and, if `.complete`
exists, every file it lists present with the listed SHA-256. Files the manifest
does not list (a desktop's `.DS_Store`, a sync tool's marker, `world.xml.bak`) are
ignored, so they do not make a good save look damaged. A save with no `.complete`
at all is a legacy snapshot and is accepted if it has the other parts.

### Format versions

| Item | Value | Meaning |
| --- | --- | --- |
| `version` in `header.properties` | `1` (also when absent) | Plain save. |
| | `2` | Module-aware save: also has `moduleformat=1` and a `modules.json`. Written only when optional modules are in use, so a world without modules keeps writing version 1. |
| `VERSION` on `<WORLD>` in `world.xml` | `1` | The only snapshot version this engine reads. |

Before replacing anything, `RESTORE` and the launcher validate the save and refuse
with: `The save 'x' is incomplete or damaged.`, `Unsupported save format N in
'x'. This engine supports versions 1 and 2.`, `The module-aware save 'x' is
missing its module metadata.`, `The world snapshot in 'x' is incomplete or
damaged.` or `Unsupported world snapshot version N in 'x'.` A save that needs
optional modules the world no longer provides is refused with "The save cannot
load its optional modules: ...". The module capsule (selection, versions,
settings, scoped owner records) is described in
[Optional modules](14-optional-modules.md); the world-manifest records which
module files were loaded.

## What a save contains, and what comes from the world files

### Two layers

A running campaign is the world files plus the save. The engine never mixes
them in the files: every start rebuilds the *content* from `world/` into a
working database (a cache, `resources/fakedb`, is used when the files have not
changed), and a restore lays the *campaign state* over it.

| Comes from the world files at every start | Comes from the save |
| --- | --- |
| Areas, rooms (names, descriptions, exits, locales), room and area effects and behaviours | The profile and every character: stats, skills, items, followers, accounts, journals, clans (the state tables) |
| Mobs and items as placed, the catalog | What is in each room now: mobs (and how hurt they are), items on the floor, with their saved state |
| Custom races, classes, abilities, commands, quests (definitions), scripts, JavaScript, text, help, `world.ini` | Dead mobs waiting to respawn and items taken from where they belong |
| Optional-module definitions | Open/locked doors, weather, room states, timed effects with time left |
| Whatever `resources/` holds | Script variables, JavaScript `cm.store` data, quest state and variables, per-object script and behaviour state, rides, fights under way, unique items found, crafting yields |
| | The game clock, time mode, pause, dice, difficulty, party order and settings |
| | Rooms and areas built or changed in play (a normal game), vessels |

### header.properties

| Key | Meaning |
| --- | --- |
| `version`, `moduleformat` | Format (see above). |
| `character`, `level`, `class`, `race` | The played character, for the menu. The character is logged in again at restore. |
| `party` | Names of the other characters in the world, comma separated. |
| `place`, `area` | The played character's room and area. |
| `saved` | Real time of the save, milliseconds. Orders the list. |
| `gametime`, `gamedate` | The game clock in milliseconds, and the calendar date as text. |
| `timemode`, `timemodebefore` | `TURNS` or `REALTIME`; the mode a `Prop_TimeMode` zone took over from. |
| `paused` | `true` restores the game paused. |
| `turns` | The count `TURN_PROG` and `TURNS()` use. |
| `savemode` | The save rules (see above). |
| `partymeet`, `partyfight`, `partyturns`, `partyorder`, `partyalone` | The `PARTY MEET`, `PARTY FIGHT`, `PARTYTURNS`, `PARTY ORDER` and `PARTY DISABLE` states. |
| `difficulty`, `exchange`, `genseed` | The difficulty setting, currency exchange rates and `SEED=GAME` generator seed. |
| `dice` | The random generator's state, set last so play carries on exactly where it stopped. |
| `cheat`, `dev` | `true` when the save was made with `CHEAT ON` or in a `DEV=1` game. |
| `worldsum`, `areasums` | Checksums of the world files at the time ([below](#world-sums)). |

Not saved: `TIMESCALE`, `WINDOWS` and `WINDOWPANEL`, `AUTOSAVE`, any `coffeemud.ini` or
`world.ini` value other than the ones above.

### world.xml

`world.xml` is CoffeeSUD's own XML (text escaped only for `<` and `>`). Its main
sections, in the order written:

| Tag | Holds |
| --- | --- |
| `MENUS`, `NARRATION` | Pending script menus and paged narration. |
| `PLAYER`, `PARTY` | The played character, and each other character in the world: room, leader, hit points, mana, movement, timed effects, entry limits, inventory with item state, followers. |
| `ROOMSHAPES` | Rooms made (`NEWROOM`), changed (`CHANGEDROOM`) or destroyed (`GONEROOM`) in play, areas made or changed in play. |
| `ROOMS` | For every room: effects, states, doors (`DOOR DIR OPEN LOCKED`), the mobs (`MOB`) and items placed in it now. |
| `SPACE`, `VESSELS` | Things in space; vessels and their travel state. |
| `DEAD`, `TAKEN` | Mobs waiting to respawn, with time left; items taken away and due back. |
| `WEATHER`, `AREAS` | Each area's weather and held weather; area states. |
| `SCRIPTVARS` | `MPSETVAR` variables of every scope that is saved. |
| `JSSTATE` | JavaScript `cm.store` data. |
| `QUESTS`, `QUESTRECORDS` | Quest controllers, steps, winners, variables, time left; each character's quest records. |
| `YIELDS`, `UNIQUES` | Gathered-out rooms; unique items ever made or lost. |
| `RIDES`, `COMBAT` | Who rides what; who follows whom and who fights whom. |

Behaviours, properties and scripts add their own runtime state inside the object
they belong to (`BSTATE`, `ASTATE`, `SSTATE`): which `ONCE_PROG`s have run,
`DELAY_PROG` countdowns, queued `MPALARM`s, a hireling's contract, and so on.
Behaviours that declare no state simply start fresh.

Mobs that cannot be saved (flagged unsavable, or summoned and left out of saves)
are not kept unless they follow a player. Mobs and items belonging to a running
quest are left to the quest.

What a save does **not** keep whole: the call stack of a running script
interpreter (use explicit stages), anything held only in Java statics of custom
code, background jobs (cancelled on restore), native I/O. The per-system answers
are in [State and persistence](state-and-persistence.md).

### Restoring: the sequence

1. Validate the save and its modules; remember the restore request.
2. The engine restarts. `beforeBoot` makes a fresh working database from the
   content and puts the save's tables over it, then reads the header: clock, time
   mode (and pause), save mode, party settings, cheat flag.
3. The world boots from the files; the clocks read their dates; the world's
   JavaScript `init.js` and `onBoot()` run (the world is flagged *pending* so
   scripts wait rather than run what they already ran).
4. `WorldState.apply` empties every room of the mobs and items the boot placed
   and puts back what the save holds, then doors, weather, area states, variables,
   quests, rides and so on.
5. The played character logs in. `finishRestore` brings the other characters back
   into the world where they were, resolves who follows whom, puts hit points and
   timed effects back, and restores fights and rides.
6. The dice state is restored last. `GAME_PROG RESTORE` and the JavaScript
   `onRestore(player, slot)` hook fire; any change report is shown ([below](#the-restore-report)).

## Restoring after the world changed

You edit `world/` after a campaign has been saved. What does a restore do?

### World sums

Every save records `worldsum` (a checksum of all the world files the game had
loaded, written `3-<count>-<crc>`) and `areasums` (a checksum of each area file).
At a restore the game compares them with the files it just booted from.

- The first part of a sum is its *kind*. Sums of different kinds (a save from an
  older version of CoffeeSUD) cannot be compared; only the area files are
  compared, and the world is said to have changed only if one has.
- Optional-module files and the `dev/` areas are tracked separately and do not
  count in the world sum; modules are versioned by their own manifests.
- `SAVES` marks a save `(older world)` when its sum differs from the world as it
  is now.

### Default: the save wins, for contents

By default every room's **contents** come from the save. The restore empties
every room of the mobs and items the boot placed and puts back the saved ones.
That means:

- Changes to a room's name, description, exits, flags, effects, and to areas,
  custom classes, quest definitions, scripts, help and text show up after a
  restore, because those come from the files.
- Changes to the mobs and items **placed** in a room by an area file that already
  existed when the save was made do **not** show up: the save's version of the
  room wins.
- A room that is in the files but did not exist when the save was made has nothing in
  the save, so, by the code (`clearWorld` and `apply`), it is emptied of its placed mobs
  and items unless its area is taken fresh (below). Test this with your own world
  before shipping a patch; take the area `FRESH` if it holds new rooms.
- Rooms, exits and areas the player built or changed in play are kept as built
  (`ROOMSHAPES`), unless their area is taken fresh.
- A saved room that the files no longer have is reported, and its saved mobs and
  items are dropped.

### FRESH

`FRESH=` tells the restore to take some areas' room contents from the world as it
is now instead of from the save. Characters and everything else still come from
the save.

| Value | Areas taken fresh |
| --- | --- |
| `CHANGED` | The area files that are new or whose checksum differs from the save's `areasums`. |
| `ALL` | Every area. |
| `Name,Name` | The named areas (area names or area file names, comma separated, case-insensitive). |

```text
RESTORE hero FRESH=CHANGED
RESTORE 2 FRESH=Millbrook,The Old Mill
```
```sh
CoffeeMud/sud.sh RESTORE=hero FRESH=CHANGED
```

In a fresh area the dead and taken lists are not applied and room shapes from the
save are skipped: the area is as the files have it. Characters keep their saved
locations, whatever the area.

`CHANGED` only sees area files (`areas/*.cmare`); a change that is not in an area
file (the catalog, quests, scripts) shows up in `worldsum` but names no area.

### The restore report

If anything changed, the first thing the restored character sees is:

```text
This save was made with another version of the world:
  The world has changed since this game was saved.
  Changed area file: millbrook.cmare
  New area file: sewers.cmare
  Area file gone: old-keep.cmare
RESTORE name FRESH=CHANGED takes the changed areas' rooms as they are now.
```

Other lines can appear in the list, each the result of a specific loss:
`Room X is gone, with N mobs and M items saved there.`, `The vessel room X is gone,
with what was saved there.`, `No mob class X: a saved mob is lost.`, `No item class
X: a saved item is lost.`, `A room made in play could not be made again: ...`,
`The area X, made in play, is of a kind this game does not have.`, `Room X became a
kind of room this game does not have (Y).` and `Quest X is gone from the world.` At
most 60 lines are kept. A separate block, `The save's optional modules:`, reports
what the restore did with the save's module state; `FRESH=` does not affect it.

### A checklist for shipping a patch to a world with existing saves

1. Keep a save made before the change (`SAVES COPY hero hero-before-patch`); a
   test made after the edit cannot prove that migration works.
2. Prefer changes the files carry: descriptions, exits, scripts, quests, items'
   definitions. Moves of placed mobs or items need `FRESH` or a script that places
   them (for example an `ENTRY_PROG` that makes the mob if the quest says so).
3. Keep IDs: area names, room IDs, quest names and instance/source IDs ([State and
   persistence](state-and-persistence.md)).
4. Restore the old save twice: once as is (see the report) and once with
   `FRESH=CHANGED`; compare with `SAVES DIFF` after saving each as a new slot.
5. Start a new game too, and check it has the new initial state.

## Save tools

### In the game

| Tool | Use |
| --- | --- |
| `SAVES` | List saves; `(older world)` flags saves made with other world files. |
| `SAVES CHECK` | What a save made now would lose (unsavable mobs, things in space). Run it before saving a hand-built scenario. |
| `SAVES INFO name` | Header, party, running quests, variable counts and world sum of a save. |
| `SAVES DIFF a b` | What changed between two saves: character, hit points, inventory, variables, quests, room contents. |
| `SAVES COPY a b` | Keep a known-good copy before an experiment or a world patch. |
| `SAVES DELETE name` | Delete a save and its `.old-` and `.new-` generations. |
| `CHEAT ON/OFF`, `DEV=1` | Marks saves `[cheat]` / `[dev]`. |
| Playtest files | `#save SLOT` and `#restore SLOT` in `.play` files ([Testing and release](13-testing-release.md)). |
| `RECORDINPUT=1` | Writes every typed line to `recordings/input-DATE-TIME.play` in the saves folder, with a header naming the seed, mode, save and world sum. |

### tools/saveinspect.py

```sh
tools/saveinspect.py CoffeeMud/saves/hero
tools/saveinspect.py CoffeeMud/saves/hero --var Throne DOORS 1
tools/saveinspect.py CoffeeMud/saves/hero --hp 40
tools/saveinspect.py CoffeeMud/saves/hero --room "Millbrook#12"
```

With only the folder it prints the header keys, the character with room and
points, party members, quests with their steps, and the script variables
(`host name = value`). `--var HOST NAME VALUE` sets (or adds) a script variable,
`--hp N` sets the played character's hit points, `--room ID` moves the played
character. Each change rewrites `world.xml` and keeps the old one as
`world.xml.bak`. The game must not be running on that save.

Caveat: this tool predates the `.complete` manifest. A change alters `world.xml`
but not the digest in `.complete`, so (reading `SaveSlots.complete`) the save then
counts as damaged and is refused or rolled back to `.old-NAME`. After editing, delete
`NAME/.complete` (the folder is then treated as a legacy snapshot and is accepted)
or regenerate it from the files, for example:

```sh
cd CoffeeMud/saves/hero
{ echo '#Complete snapshot: SHA-256 per file'
  find . -type f ! -name .complete ! -name '*.bak' | sed 's|^\./||' | sort | while read -r f; do
    printf '%s=%s\n' "$f" "$(sha256sum "$f" | cut -d' ' -f1)"
  done; } > .complete
```

The next `SAVE` into that slot writes a fresh manifest. Prefer scripts and
`jrun` for scenario setup where you can; hand edits are for rescue.

### tools/worlddiff.py

```sh
tools/worlddiff.py OLD_WORLD NEW_WORLD
tools/worlddiff.py --git v1.2 CoffeeMud/world
```

Compares two world folders object by object (rooms by ID, mobs and items by name,
other files as added/removed/changed). Run it before you ship a patch to see what
`FRESH=CHANGED` will take, and which placed mobs and items a normal restore will
ignore. Exit status 1 means there are differences.

### Tests and fixtures

| Item | Covers |
| --- | --- |
| `tools/storage-tests.sh` (`tools/SaveStorageTest.java`) | The save folder: second process refused, working copies preserved, interrupted-rename recovery, damaged generation falls back and is kept as `.damaged-`, strays do not roll a whole save back, unsupported formats refused, deleting a save removes its generations, lock release. Builds the engine first. |
| `tools/fixtures/fixes/party-controls.js` | `SWITCH` to a feigning character, `PARTYMAX`/`ACTIVECHARMAX` against `FOLLOW` and creation, `PARTYSUMMON` MEET and MASK, the merged `HELP PARTY`. |
| `tools/fixtures/fixes/p10-rules.js` | `PROFILEMAX`, `NEWCHARS`, `PARTYROLES`, the `[cheat]` mark in `SAVES`. |
| `tools/fixtures/fixes/st10-party.js` | `PARTY_PROG` and the party functions. |
| `tools/fixtures/fixes/w28-timemode.js` | `Prop_TimeMode` zones, `TIMEMODE` refusal, `ONLYCOMBAT`, `RESTORE=FALSE`. |
| `tools/fixtures/fixes/c7-checkpoint.js` | `Prop_Checkpoint` respawn points. |
| `tools/smoke.sh`, sessions 8b to 8e | A real second character, the party, a restore that brings the party back, party turns in turn-based time, plain `SAVE` and `RESTORE`. The transcripts are the best examples of exact output. |
| `tools/fixtures/fixes/b4-unsaved.js` | Builder-mode unsaved rooms (SAVE CHANGED). |

## Worked examples

### Example 1: a three-character party adventure

Goal: a profile with Hero, Bran and Mira, played as a party in real time, saved
and restored. The world sets sensible limits.

`world/world.ini`:

```ini
PARTYMAX=3
ACTIVECHARMAX=3
PROFILEMAX=6
PARTYROLES="healer:-BASECLASS +Cleric; scout:-BASECLASS +Thief"
```

1. Start a new game and make Hero (the first character is made without the
   profile menu).
2. Make the others. `LOGOFF`, answer `y`; at the profile menu type `NEW Bran`,
   confirm `y`, and go through creation. When Bran is in the world, `LOGOFF`
   again and `NEW Mira`. The `NEW` step warns `Your party has no healer or
   scout.` if no character yet fills those roles (a warning, not a refusal).
   Each `LOGOFF` takes the played character out of the world; it rests at the
   spot where you left it.
3. Play Mira and bring the others in:

   ```text
   PARTY ADD hero
   PARTY ADD bran
   PARTY
   ```

   Bran and Hero arrive beside Mira ("Bran joins your party.") because `PARTYSUMMON`
   is `ALLOW` and `PARTY MEET` is off. `PARTY` now shows `Your party:` with Mira
   `(you)` and `leading`, then Hero and Bran. With a fourth character in the
   profile, a fourth `PARTY ADD` would be refused: "Your party may have at most 3
   characters in it."
4. Hand the lead to the fighter and give orders:

   ```text
   PARTY LEAD hero
   AS bran say hello
   AS hero north
   SWITCH bran
   ```

   `AS bran say hello` shows `You say to Hero 'hello'` on your terminal (it is
   Bran's `SAY`). `AS hero north` moves Hero alone; Mira and Bran still follow
   him ("Bran arrives from the south." is the sort of line you see when a
   follower comes after). `SWITCH bran` swaps the terminal to Bran; Mira stays
   where she stands and keeps her own session.
5. Rest someone who is not needed:

   ```text
   PARTY REST mira
   ```

   "Mira rests in your profile." She is saved, her session ends, and she no
   longer counts for `ACTIVECHARMAX`. `PARTY` lists her under `Resting:`.
6. Save and leave:

   ```text
   SAVE camp
   QUIT
   ```

   (`QUIT` offers to save again under the same name.)
7. Next day, continue (`C` at the launcher menu, or `sud.sh RESTORE=camp`).
   The save restores Bran (the character you were playing) and Hero (who was in
   the world with him), each in his room and in the party, with hit points and
   effects; Mira is back in the profile, resting; the
   party order, `PARTY MEET` and `PARTY FIGHT` are as you left them.

What persisted: each character's tables, the room contents, who follows
whom, the order. What did not: windows opened with `WINDOWS ON`, and
anything started that the save could not keep (`SAVES CHECK` would have said).

### Example 2: choosing turn-based play

Goal: a tactical world where the party plans each move. Real time suits action
games and ambient worlds; turn-based suits puzzles, careful exploration and
combat the player wants to think through, and it is the mode in which a seeded
game replays exactly (`RANDOMSEED`, see [Testing and release](13-testing-release.md)).

In `world/world.ini`:

```ini
TIMEMODE=TURNS
PARTYTURNS=ALL
PARTYMAX=3
```

(or `PARTYTURNS LEADER`, the default, for a lighter touch). The player can still
use `TIMEMODE REALTIME`; to forbid it in a dangerous place, put
[`Prop_TimeMode`](23-properties-reference.md#prop_timemode) `MODE=TURNS
ONLYCOMBAT=TRUE` on the area.

In play, with Hero leading and Bran in the party:

```text
TIMEMODE
AS bran south
```

`TIMEMODE` replies `Time is turn-based: the world moves only when you act, or
WAIT.` `AS bran south` queues Bran's step: "Bran will do that on Hero's turn."
and nothing moves yet. With `PARTYTURNS LEADER`, Hero's next time-taking
command, say `SOUTH`, first runs Bran's order (he is south first), then Hero's step,
then the world advances by the time Hero's step took. Now switch to `ALL`:

```text
PARTYTURNS ALL
NORTH
```

Hero's `NORTH` is held: "The turn waits for Bran to be given something to do (AS
NAME COMMAND), or to WAIT." Give Bran an order (`AS bran north`) or have him pass
(`AS bran wait`, "You wait for Hero's turn."). The moment every member is ready,
Hero's action goes, the others' orders run in the party order (`PARTY ORDER hero
bran` changes it), and the world moves on by a whole tick at least. A member you
do not want to micromanage can be set to act alone with `PARTY DISABLE bran`; the
turn then never waits for him, and he does what followers do.

Because the game clock only moves in turn-based time when something asks, a
turn-based campaign saves the clock where it was (`gametime`, `timemode=TURNS`
in the header) and restores exactly there. `PAUSE` means nothing in this mode.

Checklist for a turn-based world: tell the player in the intro text (`world/text/`),
expect `WAIT` and its long forms to be the way days pass (`LONGWAIT` limits
who may use them), and test `AS` orders across a save. A queued order is a command
in the member's queue, and the save does not record command queues (nothing in
`WorldState` writes them), so save when no member has orders waiting (`PARTY`
shows `, with orders`).

### Example 3: a hardcore save-point world

Goal: the player can only save in inns, never in the Sunken Temple, and gets an
automatic save on first reaching the village. A second, stricter version is
ironman.

`world/world.ini`:

```ini
SAVEMODE=SAVEPOINTS
PARTYMAX=3
PARTYSUMMON=MEET
```

Properties (add each with `MODIFY ROOM AFFECTS` or `MODIFY AREA AFFECTS`):

| Where | Property | Text |
| --- | --- | --- |
| The inn's common room and its upstairs | `Prop_SavePoint` | `MSG="You settle in for the night and set down your journey."` |
| The whole Sunken Temple area | `Prop_NoSave` | `MSG="The water's whispering drowns your thoughts."` |
| The village gate | `Prop_AutoSave` | `ONCE=CHAR SLOT=gate MSG="[The village remembers you.]"` |

In play:

```text
SAVE            (in the field)    -> You can only save the game at a save point.
SAVE            (in the inn)      -> You settle in for the night and set down your journey.
                                     Game saved as 'hero'.
SAVE            (in the temple)   -> The water's whispering drowns your thoughts.
```

The first player to walk through the village gate gets `[The village remembers
you.]` and a save called `gate`, even though the gate is not a save point
(autosaves ignore the save-point rule, but still skip a fight). `QUIT` in the
field offers only `Quit without saving (n), or cancel (C)?` with the reason,
so the player cannot quit with a save, which is the point; a world that wants the
opposite ("you may always leave, but only keep progress at inns") sets
`SAVEMODE=ANYWHERE` and relies on `Prop_AutoSave` instead.

Notes:

- The mode is chosen at new game and kept in the save. If you change `SAVEMODE`
  in `world.ini` later, existing campaigns keep the mode they started with.
- The hardcore edge of save points is the **restore**: players can restore any
  save, including an old one. To remove that, use ironman:

  ```ini
  SAVEMODE=IRONMAN
  ```

  The game has one save, written by `QUIT` and by autosaves; `SAVE other`,
  `RESTORE` and `ROLLBACK` are refused. Death does not delete the campaign by
  itself; pair this with [death rules](29-combat-and-encounters.md) that fit the
  design.
- Optionally pair with `LOGOUTMASK=-LOCATION +ROOMSTR(Inn)` in `coffeemud.ini`
  (or a launch argument) so that `LOGOFF`, `QUIT` and `SWITCH` work only in
  inns. It is an engine setting; a world's `world.ini` cannot set it.
- `SAVES CHECK` before announcing a save point: it lists monsters nothing will
  keep, such as summoned creatures.

For the rest of the hardcore recipe, see
[Configuration and administration](19-configuration-and-administration.md) and
[Combat and encounters](29-combat-and-encounters.md) (`PARTYDEFEAT`, downed and
essential characters).

### Example 4: recovering a damaged save

Start with what the engine does without help. The save folder repairs itself at
every start, listing, save and delete, so most crashes need no action:

| What happened | What you see |
| --- | --- |
| The game crashed while writing `hero` | Nothing. The old `hero` is whole (the new one was in `.new-hero`), and it is used. |
| Power went off between the two renames | `hero` is missing, `.new-hero` is whole: it is promoted. You have the newest save. |
| A disk fault damaged `hero/world.xml` | `SAVES` lists `hero` with the previous save's time: the damaged folder was renamed `.damaged-hero-<ms>` and `.old-hero` promoted. You lost the last save, not the campaign. |
| The same, but `hero` was the campaign's first save (no `.old-hero`) | The launcher says `The save 'hero' is incomplete or damaged.` |

For the last case, with the game **stopped**:

1. Look at the folder:

   ```sh
   ls -a CoffeeMud/saves
   ```

   Candidates: `hero` (damaged), `.damaged-hero-*`, `.old-hero`, `.new-hero`,
   other slots (`autosave1`, `checkpoint2`, copies from `SAVES COPY`).
2. Test each against its manifest:

   ```sh
   check() { ( cd "$1" && grep -v '^#' .complete | while IFS='=' read -r f sum; do
       [ "$(sha256sum "$f" 2>/dev/null | cut -d' ' -f1)" = "$sum" ] || echo "BAD $f"
     done ); }
   check CoffeeMud/saves/.damaged-hero-1730900100000
   ```

   No output means every listed file matches. (A save without `.complete` is a
   legacy snapshot with no integrity check.)
3. Copy the best candidate to a new, valid name and play from it:

   ```sh
   cp -a CoffeeMud/saves/<best candidate> CoffeeMud/saves/hero-rescued
   CoffeeMud/sud.sh RESTORE=hero-rescued
   ```

   `SAVES INFO hero-rescued` in the game confirms who and where; `SAVES DIFF
   hero-rescued autosave1` shows what you lost compared with an autosave.
4. If only `world.xml` is damaged and you have a `world.xml.bak` from
   `tools/saveinspect.py`, put it back and regenerate `.complete` (see
   [Save tools](#save-tools)); if `world.xml` is gone, the room contents are gone
   too, though `db/` still holds the characters. There is no tool to rebuild
   `world.xml`.
5. Afterwards, `SAVE` normally. Delete the `.damaged-*` folder when you are done
   with it; nothing removes it for you.

Prevention: turn on `AUTOSAVE` (three rotating saves cost little), `SAVES COPY` before
risky changes, and keep `saves/` out of cloud-sync folders that rewrite files while
the game runs.

