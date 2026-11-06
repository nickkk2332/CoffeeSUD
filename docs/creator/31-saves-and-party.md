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

