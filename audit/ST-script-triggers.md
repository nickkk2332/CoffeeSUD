# Script triggers

New, fixed and widened MOBPROG triggers: the game, world, clock, party and object events a script can react to. Part of the [builder capabilities audit](README.md).

<a id="st1"></a>

## ST1. Register LOGIN, LEVEL, LOGOFF and CHANNEL triggers when the script loads

*Kind:* script trigger · *Value:* high · *Effort:* small · *Decision:* yes (owner: "Absolutely")

**Status (docs audit, 2026-11):** Stale: scripts now register their hosts when parsed, and the world-wide triggers (QUEST_PROG, GAME_PROG, TURN_PROG, LOGIN_PROG and the rest) work in a restored game (see handbook chapter 26). The text below is the original proposal.

**Today.** `LOGIN_PROG`, `LEVEL_PROG` and `CHANNEL_PROG` register their host for world-wide events only inside `executeMsg`, so only after the host has heard some other message. A login is sent only as a global message, so a quiet room or item misses it. `RESTORE` reboots the engine and clears every registration, so each restore starts from nothing. `LOGOFF_PROG` never registers. It fires only in the rooms of the character who quits and of their group. As a result these triggers fire "sometimes". The only workaround is JavaScript calling `CMLib.map().addGlobalHandler`, which needs `JSCRIPTS` approval.

**Add.** When a script is parsed (`getScripts` or `setScript`), register its host at once: LOGIN for `LOGIN_PROG`, LEVEL for `LEVEL_PROG`, QUIT for `LOGOFF_PROG`, CHANNEL for `CHANNEL_PROG`. There is no syntax change. Unregistering mostly comes free, because `sendGlobalMessage` already drops hosts that have left the game. ST5 and ST12 build on this.

**Example.** An inn room with `LOGIN_PROG 100` greets the returning character: "You wake in the Gilded Goose; the fire has burned low." Today it stays silent after a restore unless something has happened in the inn first.

*Note.* `JSCRIPTS=2` already runs every JavaScript block without approval (see *Already possible* in the README), so the `addGlobalHandler` workaround needs no approval. The registration bug stands.

*See also:* ST12, ST2, ST5, ST14 (ST2, ST5 and ST14 fire world-wide through ST12's @GLOBAL, whose hosts are registered at parse time by ST1's change).

<!-- from: mobprog-triggers/mt-eager-global-registration -->

<a id="st2"></a>

## ST2. SKILL_PROG: skill learned, improved, mastered or failed

*Kind:* script trigger · *Value:* high · *Effort:* small · *Decision:* yes (owner: "Another must have")

**Today.** Teaching, practising and failing a skill are messages (`EXECMSG_PROG TEACH ALL`, `PRACTICE ALL`, `SKILLFAIL ALL`). Learning is not. Abilities granted on levelling (`CoffeeLevels`) only print "You have learned...", `StdMOB.addAbility` sends nothing, and a proficiency gain only prints a line. Mastery is posted to channels flagged PROFICIENT, so a hidden channel plus `CHANNEL_PROG` can catch it, but ST1's registration bug applies. The SKILLPROF and SKILLUSE achievements can award things, and `ISABLE` and `EXPERTISE` can be polled. A mentor NPC cannot react when the player learns a skill.

**Add.** `SKILL_PROG <LEARN|IMPROVE|MASTER|FAIL|ANY> <ability ID or name words|*> [mask on the learner]`. It fires for the learner's scripted items, the room and the area, and anywhere with `@GLOBAL` (ST12). Hooks: the level grants in `CoffeeLevels`, a non-temporary `addAbility`, `StdAbility.helpProficiency` (each gain, and on reaching the maximum) and SKILLFAIL. `$g` is the new proficiency.

**Example.** When the player first learns Fireball, `SKILL_PROG LEARN Spell_Fireball` on the guild area brings the wizard's familiar, who sends them to the Ember Tower. When Lockpicking is mastered, a thieves' guild invitation is loaded into their pack.

*See also:* ST1, ST12, ST5, ST14 (ST2, ST5 and ST14 fire world-wide through ST12's @GLOBAL, whose hosts are registered at parse time by ST1's change); P3 (ST2's SKILL_PROG LEARN and P3's ONLEARN hook both fire when a mob gains an ability, from the same addAbility point).

<!-- from: mobprog-triggers/mt-skill-prog -->

<a id="st3"></a>

## ST3. DISCOVER_PROG: first visit to a room or area

*Kind:* script trigger · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** The room knows about a first visit: `StdRoom`'s `addRoomVisit` returns true, and visits are kept in player stats across saves. Scripts only get `GREET_PROG`, which fires on every entry and runs after the visit is recorded. The working trick is `CNCLMSG_PROG ENTER ALL` with `IF EXPLORED($n <roomID> == 0)`. It runs before the visit is recorded, but it also fires on moves that are later cancelled. The ROOMENTER, AREAVISIT and EXPLORE achievements give XP, items or titles on discovery, but they cannot run a script or start a quest.

**Add.** `DISCOVER_PROG <ROOM|AREA> [mask]`, fired for the room's and area's scripts when `addRoomVisit` returns true. AREA fires on the first room the character visits in that area. Each party member counts separately. A PARTY option fires once for the group.

**Example.** The first time the player enters the Sunken Cathedral nave, `DISCOVER_PROG ROOM` echoes a long passage about the drowned saints. `DISCOVER_PROG AREA` on the area gives `MPEXP $n 500` and starts the Bells Beneath quest with `MPSTARTQUEST`.

*See also:* PR3, P11 (Three hooks on a character's first entry to a room (a trigger, once-only text, discovery XP) that can share one first-visit flag).

<!-- from: mobprog-triggers/mt-discover-prog -->

<a id="st4"></a>

## ST4. Weather and moon changes as messages and triggers

*Kind:* script trigger, JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A change of weather sends no message. `DefaultClimate` only tells each player under the sky (`mob.tell`). `TYP_WEATHER` is weather damage from `WeatherAffects`, not a change of weather. Dawn and dusk in `DefaultTimeClock.handleTimeChange` work the same way. So `EXECMSG_PROG`, faction rules and JavaScript cannot see these changes. Builders poll `ISWEATHER`, `ISTIME` or `ISMOON` from `RAND_PROG` or `DELAY_PROG` and keep the last value in a variable. Fixed hours already work, since `TIME_PROG` fires on any object.

**Add.** Area-wide messages, sent to the area and its rooms: WEATHERCHANGE (old, new, forecast) from `weatherTick` and `forceWeatherTick`, and TODCHANGE (DAWN, DAY, DUSK, NIGHT) and DATECHANGE (day, month, season, year) from the clock. Two triggers for mobs, items, rooms and areas: `WEATHER_PROG <new|ANY> [<old>]`, with `$g` the new weather and `$G` the old, and `MOON_PROG <phase>`. When a restore puts the saved weather back, nothing fires. Triggers for the time of day, season, month and weekday are ST11.

**Example.** A fisherman with `WEATHER_PROG THUNDERSTORM` says "Storm's coming!" and walks home. A werewolf with `MOON_PROG FULL` uses `MPAFFECT` to shape-change himself.

*See also:* ST11, JS8 (ST4's dawn, dusk and date-change messages and ST11's calendar triggers hook the same clock transitions, which JS8's hour, day and weather events also need).

<!-- from: mobprog-triggers/mt-weather-prog, mobprog-language/mpl-calendar-triggers, behaviors-world/bw-envprogs, world-systems/ws-trig-clock -->

<a id="st5"></a>

## ST5. QUEST_PROG: quest start, accept, step, win, fail and end, anywhere

*Kind:* script trigger, quest feature · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `QUEST_TIME_PROG <quest|*> -1` fires on each step and `-2` at the end. It reaches only the objects the quest registered (given scripts and behaviours, quest mobs), plus the player's tracking script on `MPENDQUEST $n` and `QUESTS DROP`. The -1 and -2 codes are not in the help. `MPQUESTWIN` and `MPENDQUEST` send `TYP_WINQUEST` and `TYP_ENDQUEST` globally, but nothing listens for them. A quest starting or timing out sends nothing globally. Only the plain countdown form, `QUEST_TIME_PROG <quest> <minutes left>`, works on any scripted object: it looks the quest up by name, so an NPC can react a few minutes before a timed quest runs out. For start, win and end, any other NPC has to poll `QUESTWINNER()` or `QVAR` from `RAND_PROG`.

**Add.** `QUEST_PROG <START|ACCEPT|STEP|WIN|FAIL|END|ANY> <quest|*> [detail]`, registered globally (ST1). STEP takes a step number or label. END takes WON, FAILED, EXPIRED, STOPPED or DROPPED. `DefaultQuest` sends the event on every path: `startQuestInternal`, `acceptQuest`, `stepQuest`, `declareWinner`, `stopQuestInternal` (FAIL when the quest stops without a winner), tick expiry and `MODIFY QUEST STOP`, plus the existing win and end messages. `$n` is the winner if there is one, `$g` the quest name, `$0` the step or outcome. Also document the -1 and -2 codes.

**Example.** The town crier has `QUEST_PROG WIN goblin_raid` with `MPECHO The crier shouts that $n drove off the goblins!`. The innkeeper has `QUEST_PROG END goblin_raid EXPIRED`: the stables burned, so the inn is closed tonight.

*Re-checked against the code after writing: corrected.*

*See also:* ST1, ST12, ST2, ST14 (ST2, ST5 and ST14 fire world-wide through ST12's @GLOBAL, whose hosts are registered at parse time by ST1's change); Q4, Q11, JS18 (Quest outcome hooks overlap: ST5's QUEST_PROG FAIL and END EXPIRED, Q4's QUESTFAIL_PROG, Q11's QUESTTIMEOUT_PROG, and JS18's onQuestFail and onQuestEnd in JavaScript); Q9, SC9 (Q9, SC9 and ST5 each ask to document MPSTEPQUEST or QUEST_TIME_PROG's -1 and -2 codes; write it once).

<!-- from: mobprog-triggers/mt-quest-prog, quests/qst-trig-lifecycle -->

<a id="st6"></a>

## ST6. GAME_PROG and TURN_PROG: new game, save, restore, switch, pause and turns

*Kind:* script trigger, script function, JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes

**Status (docs audit, 2026-11):** Stale: GAME_PROG exists with NEWGAME, RESTORE, SAVE, NEWCHAR, SWITCHTO, SWITCHFROM, PAUSE, RESUME, TIMEMODE and ANY; there are no NEWGAME_PROG, RESTORE_PROG or SAVE_PROG triggers. The text below is the original proposal.

**Today.** A new game, a restore, `SWITCH` and `PARTY ADD` all send LOGIN, so `LOGIN_PROG` cannot tell an intro from a reload. `CHARCREATIONSCRIPTS` in `coffeemud.ini` runs scripts at three stages: END (after a new character is made), CHARLOGIN, and ALLCHARLOGIN (also on `SWITCH`). They run on a throwaway engine, only `ONCE_PROG` fires, and the character is briefly in a random room. Save, restore, pause and time mode reach no script, and no function reports the game's mode. `DELAY_PROG` already counts turns in turn-based time.

**Add.** `GAME_PROG <NEWGAME|RESTORE|SAVE|NEWCHAR|SWITCHTO|SWITCHFROM|PAUSE|RESUME|TIMEMODE|ANY> [mask]`, registered globally and fired from `SaveGame`, `CharCreation`, `Switch`, `GameTime` and `TimeMode`. SAVE runs before the snapshot, so variables it sets are saved, and `RETURN CANCEL` refuses the save with the script's message. `$n` is the character, `$g` the event or save slot. `TURN_PROG [pct]` fires once per party turn in the leader's area. Functions: `ISTURNBASED()`, `ISPAUSED()`, `ISBUILDER()`, `ISDEV()`, `SAVESLOT()`, `GAMETIME()`. JavaScript gets the same events. Then either document `CHARCREATIONSCRIPTS` or retire it. Party events are ST10.

**Example.** The world's parent area has `GAME_PROG NEWGAME`, which plays the intro and loads the old sage beside the player. His `GAME_PROG RESTORE` recaps: "Last time, you had found the silver key." In the Labyrinth, `GAME_PROG SAVE` refuses: "The maze will not let you rest here."

**Also merged here, from ST23 (TURN_PROG: once per player turn).** TURN_PROG [every N] [mask]. In turn-based time, it fires at the end of each party turn (Party.afterLeaderActs and passTurn, through endTurn). In real time, it fires after each player command that takes time. $g is the turn count since the game began, kept in the save. Optionally, a function TURNS() reads the count.

**Also merged here, from ST28 (World scripts and game lifecycle triggers).** At boot, world/scripts/*.script are loaded onto a world host. It is a hidden object, with the fixed variable host WORLD, that ticks every tick wherever the player is. New triggers, usable on any host: NEWGAME_PROG, RESTORE_PROG, SAVE_PROG, and PARTY_PROG JOIN|LEAVE|SWITCH|LEAD.

**Also merged here, from JS4 (World lifecycle hooks in world/js/init.js).** If world/js/init.js exists, the engine calls whichever of these functions it defines: onBoot(), onNewGame(player) (first start only), onRestore(player, slot), onBeforeSave(slot), onPlayerEnter(player), onSwitch(fromChar, toChar), onPartyChange(leader) and onBuilderStart(builder). They are called from SaveGame.afterBoot, save and playerLoggedIn, on the game thread, with `cm` and the time limit.

*See also:* JS8 (JS8's save, restore and turn() events repeat ST6's lifecycle and turn events (with JS4 (now in ST6) and ST23 (now in ST6) merged into ST6)); ST10 (ST28 (now in ST6)'s PARTY_PROG JOIN|LEAVE|SWITCH|LEAD is ST10's party trigger); SC2 (Both add a GAMETIME() function; define it once); DT5, DT18 (ST6 adds ISDEV() and ISBUILDER(), DT5 adds ISDEV() and DT18 adds ISBUILDER(); add each once); S2 (Both make SAVE refusable (a GAME_PROG SAVE returning cancel, or save rules), which needs one check in Save.execute); W28 (ST6's ISTURNBASED() is the missing time-mode function that W28's Today names for restoring the player's mode).

<!-- from: mobprog-triggers/mt-game-prog, sp-dev-tooling/spdev-lifecycle-triggers, mobprog-triggers/mt-turn-prog, mobprog-language/mpl-world-scripts, javascript/js-lifecycle-hooks -->

<a id="st7"></a>

## ST7. VERB_PROG: verbs on objects that win over skills and socials

*Kind:* script trigger, property, builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `CMDFAIL_PROG` fires only when the word matches nothing else, and it matches any prefix, so typing `T` fires a TURN trigger. The parser tries commands first, then skills, then socials, so TURN WHEEL makes a cleric turn undead. LIGHT, SEARCH and CLIMB are skills, and PRAY and POKE are socials. `CREATE COMMAND TURN` adds a verb that wins over them, but it is global and cannot pass the line to the wheel's own script. `Prop_Socials` on rooms and exits only covers words that reach HUH.

**Add.** `VERB_PROG <verb[,alias...]> [p] [keywords]` on items, exits, mobs and rooms. It is checked before command lookup, for objects within reach. It fires when the rest of the line names the object, or when the trigger needs no object. `$t`/`$i` is the object and `$g` the rest of the line. Returning `continue` hands the line back to normal parsing. Verbs match exactly. Also add `Prop_Verbs` (`VERBS="turn,rotate" MSG=...`) for flavour without a script, and `LIST VERBS` in builder mode. A cheaper first step: make `CMDFAIL_PROG` match whole words, and let a GenCommand forward to the target's trigger.

**Example.** A ship's wheel with `VERB_PROG turn,spin p wheel` changes the heading. TURN WHEEL turns the wheel; a bare TURN still turns undead.

*See also:* I9, I19 (All three let an object's own verb (TURN, PULL, PUSH) win over stock commands and skills; one parser hook serves them); JS20 (JS20's cm.verb is a JavaScript form of ST7's VERB_PROG local verbs).

<!-- from: items-puzzles/ip-verb-prog -->

<a id="st8"></a>

## ST8. Vessel triggers: board, disembark, sail, arrive and dock

*Kind:* script trigger, JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Boarding is an ENTER into the deck room, so a `GREET_PROG` on the deck or interior area sees it, but the vessel item does not. A ship moves through a temporary mob named after it. An `ENTRY_PROG` on the ship fires for every ENTER it hears and has to test `NAME($n)==NAME($i)`. That mob is destroyed right after the move, so `$n` is unreliable in queued responses. `ARRIVE_PROG` does not fire for ships, the deck hears nothing of the arrival, and anchoring sends no event. Steering is `MSG_NOISYMOVEMENT`, which `EXECMSG_PROG` can match.

**Add.** Triggers for the vessel item, its rooms and crew, and outside rooms, with a stable `$i` (the vessel) and `$r`: `BOARD_PROG [mask]` and `DISEMBARK_PROG [mask]` (`$n` is the person); `SAIL_PROG <COURSE|ANCHOR|STEER|ALL>` (`$n` is the helmsman, `$t` the order); `VESSEL_ARRIVE_PROG <room id|area|mask>` on the vessel and crew, with `$r` the room entered; `VESSEL_GREET_PROG` on any room a vessel enters; and `DOCK_PROG` when the anchor drops or the brake is set in a seaport or drivable room. The JavaScript engine gets the same events.

**Example.** The ferry captain has `VESSEL_ARRIVE_PROG Port Azure#1`, which echoes "Port Azure! All ashore!" and runs `MPFORCE $i lower anchor`. The harbour room's `VESSEL_GREET_PROG all` sends the harbour master to meet the player's ships.

*See also:* W4 (ST8's DOCK_PROG and T1 (now in W4)'s WAIT UNTIL DOCK both need an event when a vessel stops or anchors in a port).

<!-- from: vehicles-travel-instances/vti-vessel-triggers -->

<a id="st9"></a>

## ST9. CLAN_PROG, and entry and exit scripts on every membership change

*Kind:* script trigger, JavaScript hook · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** There is no clan trigger, and `DefaultClan.addMember` and `delMember` send nothing. A government's `ENTRYSCRIPT` and `EXITSCRIPT` run only from CLANAPPLY, CLANACCEPT, CLANEXILE and CLANRESIGN, not from `MPSETCLAN` or a GuildMaster. Conquest news ("X gains control of Y") goes to channels flagged CONQUESTS. This fork's INFO channel keeps that flag, so `CHANNEL_PROG` can match the text, though that is fragile. Adding CLANINFO to INFO in `coffeemud.ini` would expose declarations and auto-promotions the same way.

**Add.** `CLAN_PROG <JOIN|LEAVE|PROMOTE|DEMOTE|EXILE|RELATION|CONQUER|LOSEAREA|LEVEL|DUESLATE|ALL> [clan name|ALL]`. It fires on scripts in the member's room, the clan's recall room and the affected area. `$n` is the member, `$t` the other clan or area, `$v` the new rank or state. JavaScript gets the same event. `addMember` and `delMember` run the entry and exit scripts, so `MPSETCLAN` and a GuildMaster act like CLANACCEPT. A government's `<EVENTSCRIPT>` receives every event of its clans.

**Example.** When the player is promoted to Shadow, the guildmaster congratulates them and unlocks the vault door. When the Orc Horde takes Millbrook, the innkeeper switches to occupation dialogue and the militia goes into hiding.

*See also:* F2, F3, F9, F10, F16, F22 (later) (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts).

<!-- from: organizations-factions/org-clan-prog -->

<a id="st10"></a>

## ST10. PARTY_PROG and party functions

*Kind:* script trigger, script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `PARTY ADD` and `REMOVE` change following directly. The only trace is the echo "\<S-NAME> join(s) \<T-YOUPOSS> party.", which an `ACT_PROG` in the same room can match. Handing over the lead, resting and bringing a member in send nothing. Masks already test the party: `+GROUPSIZE 4` works as a trigger's chance (`GREET_PROG +GROUPSIZE 4`) or in `ISLIKE`, and `+FOLLOW`, `ISFOLLOW` and `$f` test the leader. NPC followers send FOLLOW and NOFOLLOW, which `EXECMSG_PROG` catches. No function returns a count.

**Add.** `PARTY_PROG <JOIN|LEAVE|REST|LEAD|SWITCH|ANY> [mask]`, fired globally from `PartyCmd` join and remove, `Party.rest`, `handOver` and `bringIn`. `$n` is the member, `$t` the leader. New functions `PARTYSIZE($n)`, `INPARTY($n $t)` and `PARTYLEADER($n)`, and `$%PARTY($n)%` for the member names.

**Example.** A hired mercenary has `PARTY_PROG JOIN` with "Coin first, questions later." When he leaves, his rival at the tavern remarks on it. A bridge troll's `GREET_PROG` uses `IF PARTYSIZE($n) > 3` to charge a toll per head.

*See also:* ST6 (ST28 (now in ST6)'s PARTY_PROG JOIN|LEAVE|SWITCH|LEAD is ST10's party trigger); SC8 (Both add PARTYSIZE, INPARTY, PARTYLEADER and $%PARTY%; build the functions once).

<!-- from: mobprog-triggers/mt-party-prog -->

<a id="st11"></a>

## ST11. Calendar triggers: named times of day, ranges, season, month, year, weekday

*Kind:* script trigger, script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `TIME_PROG` takes hour numbers only. Each argument goes through `CMath.s_int`, so `TIME_PROG DAWN` reads as hour 0 and fires at midnight. `DAY_PROG` is the day of the month only, and neither trigger takes ranges. Nothing fires on a new season, month, year or weekday, so builders poll `ISTIME`, `ISSEASON`, `ISMONTH`, `ISYEAR` or `DATETIME` and keep a variable. A quest can already start on a game date (`SET DATE MUDDAY`). The clock has weekday names (`getWeekNames`), but no function tests the weekday.

**Add.** `TIME_PROG` accepts DAWN, DAY, DUSK and NIGHT, taken from the area clock's own hours, and ranges with steps (`TIME_PROG 6-18/3`). `DAY_PROG` takes ranges too. New triggers, each fired once on the transition from `handleTimeChange`, `bumpDays` and `bumpMonths`, for scripts in areas on that clock: `SEASON_PROG <SPRING|SUMMER|FALL|WINTER|ANY>`, `MONTH_PROG <numbers or names>`, `YEAR_PROG <year|ANY>` and `WEEKDAY_PROG <day names>`. Plus an `ISWEEKDAY` function.

**Example.** A fey glade with `SEASON_PROG SPRING` loads blossoms and unlocks a hidden exit. The town crier announces the market with `WEEKDAY_PROG Sunday`. The vampire's crypt uses `TIME_PROG DUSK` to `MPUNHIDE` him.

*See also:* ST4, JS8 (ST4's dawn, dusk and date-change messages and ST11's calendar triggers hook the same clock transitions, which JS8's hour, day and weather events also need); SC2 (Both add weekday tests on the area calendar (ISWEEKDAY, WEEKDAY())).

<!-- from: mobprog-triggers/mt-calendar-progs -->

<a id="st12"></a>

## ST12. @GLOBAL modifier for EXECMSG_PROG, DEATH_PROG and KILL_PROG

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The engine broadcasts several categories world-wide: DEATH, LEVEL (gain and loss), LOGIN, QUIT, LEGALWARRANT, LEGALSTATE, WINQUEST and ENDQUEST. Scripts hear them only through `LOGIN_PROG`, `LEVEL_PROG` and `CHANNEL_PROG`. Deaths can already be heard everywhere. `DEATH_PROG` and `KILL_PROG` on a room, area or item fire for every death the host hears, and area messages rise to parent areas, so one parent area over all areas hears every death. These triggers take no arguments, so the filtering goes in the body (`IF ISLIKE($n '<mask>')`). Warrants, legal state, quest wins and ends, and level loss reach no script.

**Add.** An `@GLOBAL` first argument: `EXECMSG_PROG @GLOBAL DEATH ALL`, `DEATH_PROG @GLOBAL -RACE +Goblin`, `KILL_PROG @GLOBAL`. The host is registered for that category when the script is parsed (ST1). `DEATH_PROG` and `KILL_PROG` also take a mask. This pairs with the proposed functions that read a message's details.

**Example.** A bounty-board NPC in the capital has `DEATH_PROG @GLOBAL -NAME +*bandit*`. It counts each kill in a variable and pays the killer on their next visit.

*See also:* ST1, ST2, ST5, ST14 (ST2, ST5 and ST14 fire world-wide through ST12's @GLOBAL, whose hosts are registered at parse time by ST1's change); JS8 (Both expose the engine's world-wide broadcasts (DEATH, LEVEL, WINQUEST, LEGALWARRANT and so on) to scripts, ST12 in MOBPROG and JS8 in JavaScript); SC5 (SC5's MSG() functions are the message-detail reads that ST12 says it pairs with).

<!-- from: mobprog-triggers/mt-global-listen -->

<a id="st13"></a>

## ST13. CRAFT_PROG and RECIPE_PROG

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Crafting sends ITEMGENERATED in the room, ruined results included, and learning a recipe sends RECIPELEARNED. Much already works. `EXECMSG_PROG ITEMGENERATED =O CLASS=Weaponsmithing` on a room, area or carried item fires for that one skill. The product is `$t`, `IF STAT($t CLASS == GenRuinedItem)` spots a ruined one, and `LEVEL($t)` works. The CRAFTING achievement counts items by skill. But the `=O` test names one skill per trigger, no variable gives the skill when matching ALL, the product itself hears nothing, and none of this is documented.

**Add.** `CRAFT_PROG <skill ID|*> <item words|ALL> [RUINED|SUCCESS]` and `RECIPE_PROG <skill ID|*> <recipe words|ALL>`. `$o` is the item made, `$g` the skill ID, `$n` the crafter. They fire on the crafter's scripted items, the room, the area and the item. A smaller option: a function for the message's tool (`MSGTOOL()`) and a documented recipe.

**Example.** The Dwarven Forge room has `CRAFT_PROG Weaponsmithing ALL SUCCESS`. The master smith appraises the blade and gives a rune if its level is above 20. A ruined attempt makes the apprentice snicker.

*See also:* P21 (ST13's CRAFT_PROG and P21's ONCRAFTED and ONFAILED skill scripts are alternative hooks on the same crafted or ruined item).

<!-- from: mobprog-triggers/mt-craft-prog -->

<a id="st14"></a>

## ST14. FACTION_PROG: faction change and range crossing

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A faction change is a FACTIONCHANGE message, sent only to the source's room. The faction id is in the message text and the amount in its value, and scripts can read neither. Faction files already react to ranges. REACTION entries give an ability, a behaviour (Scriptable included) or a command to mobs in the same room as a character in a named range. AFFBEHAV works on monsters only. Masks take `-FACTION +RANGE`, `FACTION()` reads the range, and ANNOUNCE posts to FACTIONANNOUNCEMENTS channels, which `CHANNEL_PROG` can hear. Nothing fires when a character enters a range, and nothing reports a change made elsewhere.

**Add.** `FACTION_PROG <faction ID> <range name|ANY|UP|DOWN> [mask]`, fired after the change on scripts in the room and area, and anywhere with `@GLOBAL` (ST12). It fires on any change, or only on entering the named range, which `DefaultFaction` finds by comparing the range before and after. `$g` is the new range name, `$G` the amount.

**Example.** When the player's standing with the Thieves' Guild enters Trusted, a fence in another district unhides the back-room exit (`FACTION_PROG thieves.ini Trusted` with `@GLOBAL`). When it drops to Hated, guild assassins are loaded on the road.

*Note.* A `<SCRIPT>` block in a `CNCLMSG_PROG FACTIONCHANGE` can already read the faction id and the amount from the live message, `objs()[0]` (see *Already possible* in the README). What this item adds is the plain MOBPROG trigger.

*See also:* ST1, ST12, ST2, ST5 (ST2, ST5 and ST14 fire world-wide through ST12's @GLOBAL, whose hosts are registered at parse time by ST1's change); F6, JS8 (All three need DefaultFaction to report a change and a band crossing beyond the source's room (FACTION_PROG, live band effects, a JS factionChange event)).

<!-- from: mobprog-triggers/mt-faction-prog -->

<a id="st15"></a>

## ST15. COMBAT_PROG: combat start, end and flight

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `FIGHT_PROG` fires on every combat tick. So a line said once at the start of a fight needs `FIGHT_PROG 100` (or `HITPRCNT_PROG 100`) with a tattoo or variable, which a `RAND_PROG` testing `!ISFIGHT($i)` clears later. (`MPNOTRIGGER FIGHT_PROG <ms>` can mute it instead, but only for a fixed time, and it mutes every `FIGHT_PROG` in that script.) Flight can be caught with `EXECMSG_PROG FLEE ALL`, victory with `KILL_PROG`, defeat with `DEATH_PROG`. A fight that ends with both sides alive (a flee, `MPSTOP`, peace, the foe leaving) sends no combat-ended message. Only whatever caused it can be seen: the FLEE or LEAVE message, or the peace spell. `MPSTOP` sends nothing.

**Add.** `COMBAT_PROG <START|END|FLEE|ANY> [mask on the opponent]`, triggered when the state changes: `isInCombat` is compared between ticks, and FLEE messages are watched. This covers the proposed ENGAGE_PROG, DISENGAGE_PROG and FLEE_PROG. START fires once, with `$n` the first opponent. END fires when combat stops with both sides alive. FLEE fires when an opponent flees, with `$n` the one fleeing. It works on mobs, on items whose owner fights, and on rooms for any fight inside them. WIN and LOSE would repeat `KILL_PROG` and `DEATH_PROG`, so at most they are aliases. JavaScript gets the same events.

**Example.** The vampire's `COMBAT_PROG START` echoes "The candles gutter out" and darkens the room with `Prop_RoomDark`. His `COMBAT_PROG FLEE` says "You cannot run from me forever..." and gives him `MOBHunter` with `MPBEHAVE`.

*Re-checked against the code after writing: corrected.*

*See also:* C3, C21 (C3's ONENGAGE and rearm and C21's reset need the combat start and end detection that ST15 adds).

<!-- from: mobprog-triggers/mt-combat-edges, encounters-combat/enc-engage-trigger -->

<a id="st16"></a>

## ST16. Area border triggers and an area entry banner

*Kind:* script trigger, property · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** On an area, `GREET_PROG` and `ALL_GREET_PROG` fire on every ENTER into any of its rooms, so every move inside the area counts. `ENTRY_PROG` needs the host to be the target, and `EXIT_PROG` compares against the last known room, so neither fires at the border. No variable holds the previous room. The workaround is `CNCLMSG_PROG ENTER ALL` on the area, with `IF INAREA($n == 'Haunted Marsh')` checked before the move. It also fires on cancelled moves, and catching a departure needs scripts on the neighbouring areas or a parent area. There is no entry banner.

**Add.** `AREA_ENTER_PROG` and `AREA_LEAVE_PROG [pct|mask]`, which fire only when the old and new rooms are in different areas. ENTER also fires on a login or restore into the area. An ALL option makes moves between parent and child areas count too. `$n` is the mover, `$r` the room crossed from or to, and `$g` the other area's name. A property, `Prop_AreaBanner(TITLE="..." TEXT="..." ONCE|EACH MASK=...)`, shows a styled banner and the area's description on entry and marks the area explored.

**Example.** Crossing into the Haunted Marsh shows "THE HAUNTED MARSH: mist clings to the reeds..." the first time. `AREA_ENTER_PROG 100` starts a fog with `MPAFFECT Spell_Fog`, and `AREA_LEAVE_PROG` removes it: "The marsh's chill releases you."

**Also merged here, from PR4 (Area entry and exit text: Prop_AreaEntry and AREAENTER_PROG).** `Prop_AreaEntry` on areas takes `ENTER=`, `FIRST=` (first time per character), `LEAVE=`, `SHOWNAME` (the area name as a banner), `MASK=` and `COLOR=`. It fires only when the previous room was in another area. Two script triggers go with it: `AREAENTER_PROG` and `AREAEXIT_PROG`. `$n` is the mover and the other area is the argument. Fix `Prop_Sounder` on areas at the same time.

<!-- from: mobprog-triggers/mt-area-boundary, behaviors-world/bw-areaentry, presentation/pr-area-entry -->

<a id="st17"></a>

## ST17. LEVEL_PROG for level loss, with the new level in $g

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** LEVEL_PROG fires on gains only. A level loss is sent only globally, so no script hears it, unless the builder adds a hidden LOSTLEVELS channel and uses CHANNEL_PROG. The trigger runs before the level is applied: LEVEL($n) still reads the old level and $g is empty. A host in the same room can get the message twice, once globally and once from the room, because LEVEL_PROG lacks the lastMsg check that GET_PROG has.

**Add.** LEVEL_PROG [GAIN|LOSS|ANY] [pct|mask]. GAIN stays the default, as today. The new level goes in $g and the class in $G. A message already handled is skipped. A bug that fills every local variable with the trigger array is fixed. Optionally, a CLASSLEVEL variant for multi-class levels.

**Example.** The old mentor has LEVEL_PROG GAIN with IF EVAL($g == 10), and MPOLOADs a sealed letter for $n. A cursed shrine has LEVEL_PROG LOSS and taunts the player when its drain costs a level.

*See also:* SC14 (Both fix LEVEL_PROG filling every local variable with the trigger array).

<!-- from: mobprog-triggers/mt-level-prog-fix -->

<a id="st18"></a>

## ST18. Fix RIDE_PROG; add DISMOUNT_PROG and REMOVING_PROG

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** RIDE_PROG is a copy of RIDING_PROG: both fire only when the scripted host does the mounting. So a scripted horse, raft or carriage never learns it was mounted, though Scriptable.html says it should. Dismounting has no trigger. REMOVE has no -ING form, unlike GET, DROP, PUT, WEAR and GIVE. The workaround is EXECMSG_PROG MOUNT ALL and DISMOUNT ALL on the rideable, but their keywords match the player's typed command.

**Add.** Fix RIDE_PROG, a one-line change. It then fires when anyone mounts the scripted rideable, or any rideable for a room or area host; $n is the rider and $i the rideable. RIDING_PROG stays for the host's own mounting. DISMOUNT_PROG (TYP_DISMOUNT) works the same way for getting off. REMOVING_PROG fires when the scripted mob removes something.

**Example.** The ferryman's raft has RIDE_PROG ALL. It echoes 'The raft lurches; the ferryman pushes off', then uses MPTRANSFER after 3 ticks. A warhorse throws riders who lack the HORSEFRIEND tattoo. The griffon's DISMOUNT_PROG sends it back to its roost.

<!-- from: mobprog-triggers/mt-ride-fix, vehicles-travel-instances/vti-fix-ride-prog -->

<a id="st19"></a>

## ST19. READ_PROG for books, signs and inscriptions

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Reading sends a READ message, and only EXECMSG_PROG and CNCLMSG_PROG catch it. LOOK_PROG fires on looking, not reading. CNCLMSG_PROG READ ALL on a book works now: it can MPECHOAT $n the right page, keep the page in a variable and RETURN CANCEL the default text. But it does not check that the host is the item being read, and its keywords match the typed command. So a book in a room also answers reads of other items, unless filtered with '=T NAME=a leather diary'.

**Add.** READ_PROG [item words|P phrase|ALL]. It matches the item's name as LOOK_PROG does, with $o the item and $n the reader. It fires on the item, its holder, the room and the area. RETURN CANCEL replaces the default text. Until then, the guide should document the CNCLMSG_PROG pattern.

**Example.** The diary has READ_PROG ALL. IF VAR($n diary_page) < 3, it echoes the next page and increments the variable. The last page reveals the vault code (MPSETVAR) and steps the quest (MPSTEPQUEST).

*See also:* I3 (I3's paged books fire a READ_PROG \<page>, the trigger ST19 adds).

<!-- from: mobprog-triggers/mt-read-prog -->

<a id="st20"></a>

## ST20. GENERATED_PROG after a generator runs

*Kind:* script trigger, script function and generator · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Nothing fires when a generator finishes. A builder cannot rename the deepest room, put a catalog NPC in a leaf room, or set a quest variable to the dungeon's name. The workaround is a Scriptable behaviour on the template's AREA tag, with LOAD=progs/crypt_post.script and a ONCE_PROG. The script must be in a file, because $ in template parms means a template variable. It runs only once the area starts ticking, possibly after the player arrives. The generator's variables are thrown away after the run.

**Add.** When fillInArea or postProcess completes, a GENERATED message goes to the area and to each room, mob and item built. GENERATED_PROG [pct] catches it, with $i the area and $g the generator id. GENVAR(\<area> \<name>) reads the generator's variables, such as $__boss or AREA_MAXDEPTH. An AREA attribute onGenerate="\<script or LOAD=file>" attaches a script for one run.

**Example.** After the crypt generates, its GENERATED_PROG finds the deepest room with an MQL SELECT. It MPOLOADs the catalog item 'Bone Crown' there, and sets the quest variable crypt_name for the priest's quest text.

*See also:* G14 (G14 runs ST20's GENERATED_PROG after re-rolling an area); G3, G9, G15 (G9, G15 and ST20's GENVAR use G3's depth tags (ROOMTAG_DEPTH, AREA_MAXDEPTH)).

<!-- from: generators/gen-trigger -->

<a id="st21"></a>

## ST21. First and repeat meetings: FIRSTGREET_PROG, REGREET_PROG, MEMORY()

*Kind:* script trigger and script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** GREET_PROG fires on every entry, so a first-meeting speech repeats unless the builder writes guards. The usual pattern is IF VAR($n HERMIT_MET) == '' then MPSETVAR $n HERMIT_MET 1, and MPSETVAR $n HERMIT_COUNT ++ to count meetings. 'Back after a while' takes a timed tattoo (MPTATTOO $n 7200 SEEN_HERMIT), tested with HASTATTOO. Exact days need DATETIME and MATH. Nothing records the last topic.

**Add.** For each NPC and character, saved with the game: times met, first and last meeting in game time, and the last topic. A meeting counts when the NPC sees the player arrive, as for GREET_PROG, or when they TALK. FIRSTGREET_PROG [pct] fires at the first meeting ever. REGREET_PROG \<min game days> [pct] fires when the player returns after at least that long. MEMORY($i $n MET|COUNT|LASTSEEN|DAYSSINCE|LASTTOPIC) reads the record.

**Example.** The hermit's FIRSTGREET_PROG tells the story of the cursed tower. His GREET_PROG says 'Ah, you again.' His REGREET_PROG 30 says 'I thought the tower had swallowed you.'

*See also:* N6 (Both keep a saved per-NPC, per-character record of meetings (times met, first and last game date)); N9 (ST21 counts a TALK as a meeting and keeps the last topic, which N9's TALK command supplies).

<!-- from: npc-dialogue/dlg-firstmeet -->

<a id="st22"></a>

## ST22. SOCIAL_PROG: exact names and socials aimed at the mob

*Kind:* script trigger · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** SOCIAL_PROG matches a social whose name starts with the given text. The target form can already be chosen through the name: 'SOCIAL_PROG BOW \<T-NAME>' fires only for targeted bows, 'SOCIAL_PROG BOW SELF' only for self-bows. But a bare name also catches longer socials. In the stock socials.txt, KNEE matches KNEEL, HI matches HICCUP and MOO matches MOON. To react only to a social aimed at itself, every script needs an IF comparing $t with $i.

**Add.** SOCIAL_PROG [EXACT] [ME] \<name>. EXACT matches the whole first word only. ME fires only when the social targets the scripted mob. $0 holds the full social name and $t the target. Without these options it behaves as today. The SELF, OTHER, NONE and ANY modes the audit proposed are left out, because the target forms already cover them.

**Example.** A queen has SOCIAL_PROG EXACT ME KNEEL. She answers 'Rise, loyal one.' only when the player kneels to her, not to the steward.

<!-- from: vocabulary-languages/vl-social-prog-targeted -->

<a id="st24"></a>

## ST24. EVERY_PROG: saved game-time timer that catches up

*Kind:* script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** DELAY_PROG counts the host's ticks in game time, but its countdown is not saved. It also stops when the host does not tick, as for an item carried in a room with nobody in it. DAY_PROG and TIME_PROG follow the area calendar, so 'DAY_PROG 1 8 15 22' gives a rough weekly restock. MPALARM timers are lost on restore. Cron jobs run on the real clock and ignore PAUSE and turn-based time. They are kept as journal entries, not world content. Nothing catches up on missed intervals.

**Add.** EVERY_PROG N [HOURS|DAYS|WEEKS|MONTHS] [CATCHUP], timed on the host area's game clock. The next fire time is stored in the save. With CATCHUP, it fires once for each missed interval, or once with the number missed in $g. Ideally it shares its saved state with a saved ONCE_PROG. Optionally, CMJournals cron intervals move to GameTime as well.

**Example.** The caravan room has EVERY_PROG 7 DAYS CATCHUP. It restocks the trader (MPOLOADSHOP) and echoes 'A caravan rolls in from the east.' After three game weeks in the dungeon, the trader's stock reflects them.

*See also:* S7 (ST24 wants its saved next-fire time to share S7's saved script state (ONCE, DELAY, alarms)); S21, JS19 (Three ways to run something on a game-clock interval that survives saves; ST24 also suggests moving cron to GameTime, which is S21); N8 (Both catch up on time that passed while a host or area was not ticking (catchUp(elapsedTicks) vs EVERY_PROG CATCHUP)).

<!-- from: mobprog-triggers/mt-every-prog -->

<a id="st25"></a>

## ST25. VAR_PROG: react when a variable meets a condition

*Kind:* script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** World flags such as 'bridge repaired' or 'king dead' live in MPSETVAR variables. setVar writes the variable map and notifies nothing, and no trigger watches a variable. So every NPC that cares polls IF VAR(...) from RAND_PROG or DELAY_PROG, and keeps its own 'already reacted' variable.

**Add.** VAR_PROG [host|*GLOBAL*|quest name] [var name] [== != > < >= <=] [value]. It fires once when the condition becomes true after a change by MPSETVAR, MPLOADVAR, the JavaScript setVar() or a quest's script. (MPARGSET writes no variables: it only sets the $0-$9, $n, $i, $t, $o, $p and $g slots.) It does not fire again while the condition stays true. setVar notifies a small watch list kept by variable name. $g is the new value and $0 the old. ($G already means the same message as $g in its original case, so it cannot carry the old value.)

**Example.** The player repairs the bridge (MPSETVAR *GLOBAL* bridge_fixed 1). The ferryman has VAR_PROG *GLOBAL* bridge_fixed == 1, so he grumbles about lost business. The toll keeper on the far side MPGOTOs his post.

*Re-checked against the code after writing: corrected.*

*See also:* W7 (W7's ONVAR transitions (and W19's) need the variable-change notice that ST25 adds to setVar).

<!-- from: mobprog-triggers/mt-var-prog -->

<a id="st26"></a>

## ST26. AFFECT_PROG: effect added or worn off

*Kind:* script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Adding an effect (addEffect) and ending one (StdAbility.unInvoke, delEffect) send no message. Only casting is visible, to CAST_PROG and CASTING_PROG. A disease, poison, curse or potion applied without a cast is invisible to scripts, and so is every wear-off. Builders poll AFFECTED($n Disease_Lycanthropy) from RAND_PROG with a state variable. The EFFECTSHAD achievement counts effects but cannot run a script.

**Add.** AFFECT_PROG [ADD|REMOVE|ANY] [ability ID or name words|*]. It fires after an effect is added or removed, on the scripts of the affected host and its room, and for an item also its owner's. $t is the affected object, $g the ability ID and $n the invoker. addEffect and delEffect are hot paths, so the check runs only when the host or room has scripts.

**Example.** When Lycanthropy takes hold, the village elder's AFFECT_PROG ADD Disease_Lycanthropy makes him lock his door (MPLOCK). When the curse wears off the haunted locket, its AFFECT_PROG REMOVE Curse lets the ghost speak.

<!-- from: mobprog-triggers/mt-affect-prog -->

<a id="st27"></a>

## ST27. DESTROY_PROG and DECAY_PROG for items

*Kind:* script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** StdItem.destroy() deletes an item's scripts (delAllScripts) without sending an event, and any queued EXECMSG_PROG response is lost with them. Two cases are covered: eating and drinking fire CONSUME_PROG, and expiry fires CNCLMSG_PROG EXPIRE ALL on the item, before the destroy. Breaking, burning out, junking, MPJUNK, MPPURGE and Decay fire nothing. A quest item lost for good, or a vase breaking, starts nothing.

**Add.** DESTROY_PROG [reason words|ALL]. It fires at once, not queued, in StdItem.destroy before delAllScripts, on the scripts of the item, its room and its owner. Where the caller knows why, $g holds DECAY, BROKEN, CONSUMED or JUNKED. DECAY_PROG is a shortcut for rot and expiry.

**Example.** The Heart of the Mountain gem has DESTROY_PROG ALL. It runs MPENDQUEST 'Heart of the Mountain' and echoes 'The mountain groans...' across the area. The player learns the quest failed, and it does not stall.

<!-- from: mobprog-triggers/mt-item-end-prog -->

<a id="st29"></a>

## ST29. ACHIEVE_PROG: achievement earned

*Kind:* script trigger · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** Achievements count many events (KILLS, EXPLORE, CRAFTING, SKILLPROF, QUESTOR, LEVELUP and more). They can award ITEM, MOB, TITLE, ABILITY, EXPERTISE, TATTOO, XP, QP or CURRENCY, but cannot run a script. An earned achievement adds a tattoo named after its ID, so a script can poll HASTATTOO($n WOLFBANE). A grant posts only to channels flagged ACHIEVEMENTS, and the fork removed that flag from INFO. A hidden channel with the flag, plus CHANNEL_PROG, can catch the text.

**Add.** ACHIEVE_PROG [achievement ID|*] [mask]. It fires globally when an achievement is granted, with $n the earner and $g its ID. achievements.ini then works as a declarative event counter, such as 'kill 50 wolves', and the script holds the consequence.

**Example.** An achievement Wolfbane (KILLS 50, -RACE +Wolf). The ranger lodge master has ACHIEVE_PROG Wolfbane and gives the player a wolf-pelt cloak and a title (MPTITLE).

*See also:* P5 (Alternatives for running a script when an achievement is earned: an ACHIEVE_PROG trigger or a SCRIPT reward).

<!-- from: mobprog-triggers/mt-achieve-prog -->
