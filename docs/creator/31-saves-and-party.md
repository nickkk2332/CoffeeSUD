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
SWITCH alike; a hardcore world can use it to forbid quitting outside an inn.

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
`PAUSE` raises `GAME_PROG PAUSE` and `RESUME`. (On other builds PAUSE
suspends ticking objects; in CoffeeSUD single-player that archon use is not
reachable by the same words.)

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
   as the action takes (the action cost divided by the character's speed, times
   a tick). `HELP TIMEMODE` puts it as "a step is a fifth of a tick, an action
   in combat a whole tick".
2. `WAIT`.
3. A long `WAIT`, which runs the game's own ticks (so one heals, shops restock,
   things respawn and `TIME_PROG`s fire), and ends early if you are hurt,
   attacked or moved.

Things that cost no time never move the world: looking, `SCORE`, `INVENTORY`,
`SAVE`, most `PARTY` and `WINDOWS` commands. While the world is catching up, a
second action waits.

The `TIMEMODE` key in `coffeemud.ini` or `world.ini` sets how a *new* game
starts (the value only needs to start with `TURN`). A restored game keeps the
mode it was saved with (`timemode=` in the header). The command can be used at
any time, which also means a world that is designed for turn-based play (for
example one whose puzzles assume enemies do nothing while you think) should say
so in its text or lock the mode with [`Prop_TimeMode`](23-properties-reference.md#prop_timemode).

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

