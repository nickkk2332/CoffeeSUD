# NPC behaviour and dialogue

Behaviours for NPC routines and AI outside combat, companions and hirelings, conversation, MudChat, barks, rumours and LLM-driven NPCs. Part of the [builder capabilities audit](README.md).

<a id="n1"></a>

## N1. WHEN= condition on every behaviour

*Kind:* behaviour · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** A builder cannot say "this wolf roams only at night". ActiveTicker reads only min, max, chance and mingrp. Of about 95 behaviours, only GateGuard (allnight), Scavenger (ATNIGHT), BribeGateGuard and ResetWhole read the clock. Zapper masks know +HOUR, +SEASON, +WEATHER and -IF, but behaviours test masks on their targets, never on their host. The workarounds: a TIME_PROG that swaps behaviours with MPBEHAVE and MPUNBEHAVE, which after a restore waits for the next listed hour; Prop_ClosedSeason, which hides the whole mob; or two mobs.

**Add.** Shared parameters, read in StdBehavior and ActiveTicker.canAct and in the StdBehavior-based mob behaviours (the Aggressive family, Wimpy, Vagrant, the helpers, the guardians). `WHEN="<zapper mask>"` is tested on the host (mob, item or room). The shorthands are `TOD=DAWN|DAY|DUSK|NIGHT` and `HOURS=a-b`. While the condition fails, the behaviour neither ticks nor reacts. For the opposite case, add a second copy with the opposite condition.

**Example.** A werewolf has `MobileAggressive TOD=NIGHT -NPC` and `Mobile TOD=DAY min=20 max=40`. A storm elemental has `Aggressive WHEN="+WEATHER -THUNDERSTORM"`.

*See also:* I4 (Alternatives for behaviours active only while a condition holds: a WHEN= parameter on every behaviour, or a Prop_When wrapper).

<!-- from: behaviors-npc/npc-when-gate -->

<a id="n2"></a>

## N2. MPMENU: numbered dialogue choices that do not block

*Kind:* script command · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `MPCHOOSE <who> <var> <choices> <default> <prompt>` takes single letters or a comma list, and the builder writes the menu into the prompt by hand. Hiding an option needs an IF around each `mpechoat` line. MPCHOOSE, MPPROMPT and MPCONFIRM wait on the player's session on the single game thread. When their real-time timeout runs out (60 s or 360 s), the rest of the script is aborted.

**Add.** `MPMENU <pc> <FUNCTION> "<prompt>" <key>:"<label>"[IF <cond>] ... [default]`. It shows the options numbered, hides those whose condition fails, takes a default or escape key, and returns at once. The chosen key goes to that FUNCTION_PROG as $0, with the original $n and $i. The menu is shown on the player's terminal and costs no time in turn-based mode. JavaScript reaches it by name, like any MP command. `MPDIALOG <pc> <file> [node]` opens a node of a dialogue tree (N14). This part waits for N14, which is in `LATER.md`. MPCHOOSE stays.

**Example.** The old sage's GREET_PROG runs `MPMENU $n SAGE "The old sage regards you." tower:"Ask about the tower" ring:"Show him the ring"[IF HAS($n ring)] bye:"Leave" bye`. FUNCTION_PROG SAGE then branches with `SWITCH $0`.

*See also:* N14 (later), N9, N29 (N2's MPDIALOG, N9's TALK fallback and N29's CHECK= on choices all hook into N14 (later)'s dialogue trees).

<!-- from: mobprog-language/mpl-dialog, npc-dialogue/dlg-mpmenu -->

<a id="n3"></a>

## N3. Schedule behaviour: daily routines over several places

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Prop_ClosedDayNight gives an NPC two states. It is open at its start room and closed at HOME=, which it reaches with Skill_Track. It has SLEEP, SIT, LOCKUP, SHOPMSG= and HOURS=a-b. It checks the hour every tick, so it catches up after a restore or a WAIT. Patroller has no idea of time. A third place takes a TIME_PROG per hour with MPTRACKTO and MPFORCE sit, which must cope with fights and failed paths. TIME_PROG fires only on the hour.

**Add.** Behaviour `Schedule`, with ';'-separated entries `WHEN: PLACE [ACTIVITY] [SAY/EMOTE text]`.
- WHEN: DAWN, DAY, DUSK, NIGHT or hours (2-3), optionally with DAYS=, SEASON=, WEATHER=, HOLIDAY= or MASK=.
- PLACE: a room id, HOME, START or ANY:\<room mask>.
- ACTIVITY: SLEEP or SIT [on item], WANDER n, SHOP (closed elsewhere, with SHOPMSG), WORK "emote|emote", LOCK or HIDE.

It travels with Skill_Track, suspends Mobile on the way (MobileBehavior.suspendMobility) and goes back to the routine after a fight. On load, restore, a time jump or an area waking up, it puts the NPC at the current entry at once, and walks only if the player is near. `STAT($i SCHEDULE)` reads the current entry.

**Example.** `Schedule DAWN: Mill#2 WORK "pours grain into the hopper|wipes flour from his hands"; DAY: Market#5 SHOP; DUSK: Inn#1 SIT "stool" SAY "Long day."; NIGHT: Mill#9 SLEEP "straw bed" LOCK`. A player who needs flour at night has to wake the miller at the mill.

*See also:* N8, N16 (N16's day and night patrol routes overlap N3's Schedule, and N8's catch-up moves both when an area wakes).

<!-- from: behaviors-npc/npc-schedule, world-systems/ws-npc-schedule -->

<a id="n4"></a>

## N4. Sleeper behaviour: sleeping NPCs woken by noise

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A mob can start asleep (the IS_SLEEPING disposition). Prop_ClosedDayNight SLEEP sleeps by the hour, and Vagrant naps at random. Only an attack, or the player's WAKE, wakes one. Noise exists as MSG_NOISE (Trap_Noise, Thief_SetAlarm, Spell_Alarm), but no sleeper hears it. The sleeper's own scripts cannot react either, because isFreeToBeTriggered needs it awake. The workaround is a room Scriptable whose ALL_GREET_PROG or EXECMSG_PROG runs `MPFORCE ogre stand` on a RAND.

**Add.** Behaviour `Sleeper`.
- When it sleeps: `ALWAYS`, `TOD=NIGHT` or `HOURS=a-b`, with `BED="item name"`.
- What wakes it: `WAKEON=ATTACK,NOISE,ENTER,LIGHT,TOUCH`, with `WAKECHANCE=n` per event. Fighting or shouting here or next door raises the chance; the intruder's Sneak or Hide proficiency lowers it.
- `DEEP`: only an attack wakes it.
- On waking: `GRUMBLE="text"`, `AWAKEFOR=n` ticks before it sleeps again, and `ALARM`, which hands over to Sentry (N5).

LOOK shows "is sleeping soundly" or "stirs".

**Example.** A cave ogre has `Aggressive` and `Sleeper ALWAYS WAKEON=NOISE,ENTER WAKECHANCE=40 GRUMBLE="Who disturbs Grok?" ALARM`. A thief sneaks past him. A fight in the next cave wakes him.

*See also:* N5 (N4's ALARM option hands over to N5's Sentry, and Sentry wakes N4's sleepers).

<!-- from: behaviors-npc/npc-sleeper -->

<a id="n5"></a>

## N5. Sentry behaviour: lookouts who spot sneakers and raise the alarm

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** No behaviour spots intruders or rouses a camp. ProtectedCitizen acts only while its own host is fighting. It yells and moves the first matching guard in each room within RADIUS (7) one step closer per call. Arrest notices crimes only in areas with law. Thief_SetAlarm and Spell_Alarm make noise, but no NPC reacts to it. A builder cannot set up a camp where the player silences the lookout first, or where the camp wakes when the player fails.

**Add.** Behaviour `Sentry`.
- `SPOT=n`: the chance per tick to notice hidden or sneaking characters here and next door, against their Hide or Sneak proficiency.
- `ALARM="RADIUS=6 MASK=-NAME +bandit MSG=Intruders!"` and `ALERTTICKS=n`. On alarm, matching mobs in the radius become alert for that time. They wake (Sleeper), pause Schedule or Patroller, head for the alarm room and attack the party.
- `ALARMITEM="a brass gong"`: a room item that anyone, the player too, can ring to set off the alarm.

The alarm sends a room message that EXECMSG_PROG can catch.

**Example.** Watchtower bandit: `Sentry SPOT=30 ALARM="RADIUS=6 MASK=-NAME +bandit MSG=Intruders! To arms!" ALERTTICKS=40`. Kill or charm the lookout quietly and the camp can be taken tent by tent.

*See also:* N4 (N4's ALARM option hands over to N5's Sentry, and Sentry wakes N4's sleepers).

<!-- from: behaviors-npc/npc-sentry-alarm -->

<a id="n6"></a>

## N6. Memory behaviour: NPCs remember what each character did

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Factions already hold grudges for a group, and they survive saves. CHANGEn= triggers (KILL or MURDER of masked targets, BRIBE, TALK, any message type) and REACTIONn= lines (Aggressive toward a range) can turn a village against a killer. +/-FACTION masks feed shop ignore masks. One NPC remembering one character is hand scripting: ACT_PROG, GIVE_PROG, DAMAGE_PROG and DEATH_PROG with `MPSETVAR $i MEM_$n` on every mob. The INDIVIDUAL ('*') variable scope is not saved. Bribes are kept by name in memory only, and a Truce lasts 30 s.

**Add.** Behaviour `Memory`. Per character it records MET (first and last game date), ATTACKED, HURT, HEALED, GAVE (total value), STOLE, KILLEDKIN (`KIN=<mask>`), TALKED, and custom `TRACK=NAME:<regex on the action text>`. Facts are script variables in a saved scope, read as `VAR($i MEM_ATTACKED_$n)`. `FORGET=30d` makes them fade. Ready-made reactions:
- `HOSTILEIF=`;
- `GREETIF= "text"`;
- `REFUSEIF= "text"`, which refuses buying, selling and teaching through okMessage.

`SHARE=AREA|MASK` passes facts on to kin. For village-wide grudges the docs point to factions.

**Example.** On a village: `Memory KIN="-NAME +villager" SHARE=AREA HOSTILEIF=KILLEDKIN>0 REFUSEIF=STOLE>0 FORGET=60d`. Steal once and the shops refuse you for two months of game time.

*See also:* ST21 (Both keep a saved per-NPC, per-character record of meetings (times met, first and last game date)); N15 (Both track one NPC's view of each character (remembered deeds vs an affinity score); N15's rules could read N6's facts).

<!-- from: behaviors-npc/npc-memory -->

<a id="n7"></a>

## N7. GenBehavior: named behaviours with parameters, defined as data

*Kind:* data file, behaviour, builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** custom.cmare holds generic races, classes, abilities and commands, but no behaviour. A routine is reused through a Scriptable with `LOAD=progs/tollkeeper.script`, one file for every user. Per-mob values go in an inline prefix, `ONCE_PROG;MPSETVAR $i price 20;~;LOAD=...`, and the script reads them as `$<$i price>`. A GenAbility's SCRIPT also runs as a permanent effect. But the file lives in resources/progs, outside the world, and nothing names it, lists it or declares its parameters.

**Add.** `GenBehavior`, made with `CREATE BEHAVIOR <ID>` in builder mode and kept in custom.cmare (or world/custom/behaviors/). Its fields:
- a name, and HELP shown by AHELP;
- CANIMPROVE: mobs, items, rooms, areas;
- PARMS with defaults (`price=20;exit=north`);
- SCRIPT, in MOBPROG or JavaScript, which reads the parameters as `PARM(price)` or `$%PARM(price)%`.

Each instance has its own engine and variables. It goes wherever a behaviour ID goes: MODIFY, MPBEHAVE, quest GIVE BEHAVIOR, faction AFFBEHAV, plane BEHAVE, holidays. A smaller form would be a registry of named Scriptable templates.

**Example.** Define `TollKeeper` once. Five bridge guards get `TollKeeper price=20 exit=north`, `TollKeeper price=5 exit=west` and so on. One fix to the script fixes them all.

*See also:* SC13, P1, JS9 (Four ways to give a shared script or effect per-instance parameters (SC13's ARG(), N7's PARM(), P1's EFFECTPARM(), JS9's parms); decide them together).

<!-- from: behaviors-npc/npc-genbehavior, data-content/dc-genbehavior -->

<a id="n8"></a>

## N8. Catch-up hook when an area wakes

*Kind:* JavaScript hook, world option · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** An area that no player has visited for PASSIVEMINS game minutes turns PASSIVE. There, only Mobile and its family, Aggressive, VeryAggressive and MovingRoom stop. Scripts keep running (runInPassiveAreas defaults to true), and so do TIME_PROG, Prop_ClosedDayNight and Patroller, so a baker on those is in place when the player returns. PASSIVEMINS is an area stat, DISABLE=PASSIVEAREAS turns passivity off, and in turn-based time WAIT moves every area. What is left: wanderers and aggressive mobs stay frozen where they were, and no behaviour is told how long its area slept.

**Add.** When an area turns ACTIVE again, each of its behaviours and properties gets `catchUp(elapsedTicks)`, which JavaScript behaviours can override:
- Schedule (N3) moves NPCs to the current entry.
- Patroller jumps to the step it would have reached.
- Sleeper and Prop_ClosedDayNight apply the current hour.
- Spawners refill.

A per-area `CATCHUP=ON|OFF`, next to PASSIVEMINS, is secondary.

**Example.** Leave the village at dusk and come back at noon. The miller is already at the market and the night watch is in bed, though the village was not simulated while you were in the dungeon.

*See also:* N3, N16 (N16's day and night patrol routes overlap N3's Schedule, and N8's catch-up moves both when an area wakes); ST24 (Both catch up on time that passed while a host or area was not ticking (catchUp(elapsedTicks) vs EVERY_PROG CATCHUP)).

<!-- from: behaviors-npc/npc-offscreen-catchup -->

<a id="n9"></a>

## N9. TALK command and TALK_PROG trigger

*Kind:* script trigger and command · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** ASK and SAYTO already address one NPC. `ask innkeeper about mine` fires the NPC's SPEECH_PROG on "mine" and MudChat's targeted path, even in a full party room. A GenCommand TALK in world/custom.cmare can run `sayto <npc> hello`. Three things are missing:
- a trigger that knows a conversation has begun, and on what topic;
- a remembered partner: a bare `'text` has no target, and MudChat ignores untargeted speech with three or more characters in the room;
- a way to learn what an NPC offers.

**Add.** A player command `TALK [npc] [ABOUT topic]` that fires `TALK_PROG [topic words|ALL]` on the NPC, with $n the player, $i the NPC and $t the topic. If no TALK_PROG runs, it falls back to the NPC's dialogue tree (N14; until that is built, this step is skipped), then to MudChat as if the player had said "hello". TALK sets the conversation partner, so a bare `'text` goes to that NPC while both are in the room. The player's own party is never picked. With no tree, TALK shows a service menu, each entry running the normal command: shop (LIST, BUY, SELL), banker, trainer or MOBTeacher, Hireling terms, and quests from Prop_QuestGiver or quest mobs.

**Example.** `talk innkeeper` fires TALK_PROG ALL; `talk innkeeper about mine` fires TALK_PROG mine. Then `'any rooms free?` reaches the innkeeper with two companions in the room.

*See also:* ST21 (ST21 counts a TALK as a meeting and keeps the last topic, which N9's TALK command supplies); N14 (later), N2, N29 (N2's MPDIALOG, N9's TALK fallback and N29's CHECK= on choices all hook into N14 (later)'s dialogue trees); N24 (N24's topic list is shown by N9's TALK, or ASK, with no topic).

<!-- from: npc-dialogue/dlg-talk-command -->

<a id="n10"></a>

## N10. Replies arrive at once in turn-based time

*Kind:* world option · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** In TIMEMODE TURNS a SAY costs 0.2 ticks. A script reply waits 1 tick and a MudChat reply 2 (RESPONSE_DELAY), plus any LLM time. So the answer comes up to 5 actions later from a script and 6 to 10 from MudChat, and WAIT does not stop when an NPC speaks. Scriptable can already reply at once: `MPSETINTERNAL NODELAY true` in a ONCE_PROG per script, or SCRIPTABLEDELAY in DISABLE= in coffeemud.ini for the whole game. DEFCMDTIME tunes command costs. An exception such as `DEFCMDTIME=Say 1.0, 0.20` does bring a script reply on the same turn (Say 2.0 for MudChat), but only by letting the whole world move a full tick on each SAY. MudChat has no setting.

**Add.** A setting `DIALOGUETIME=INSTANT|TICKS`, also available as `TIMEMODE DIALOGUE INSTANT`. It defaults to INSTANT in turn-based time. When INSTANT, replies to the player's speech run before the next prompt without moving the clock. That covers replies queued by MudChat, by Scriptable (SPEECH, GIVE, BRIBE, SOCIAL, and TALK if N9 adds it) and by the Dialogue behaviour if N14 adds it. Long LLM replies show "\<npc> is thinking..." and still land before the prompt. MudChat gets `DELAY=n` (ticks), and WAIT stops early when an NPC speaks to the player.

**Example.** In a turn-based dungeon, `sayto troll riddle?` gets the riddle on the next line. The goblins in the next room get no free turn.

*Re-checked against the code after writing: corrected.*

*See also:* N25 (later) (Both make NPC replies land before the next prompt in turn-based time, N25 (later) for LLM replies).

<!-- from: npc-dialogue/dlg-instant-turns -->

<a id="n11"></a>

## N11. MudChat: readable conditions, script effects and per-player saved state

*Kind:* behaviour, data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A chat entry's condition is a zapper mask between slashes, +IF included, evaluated with the speaker as $n. It works, but `)`, `&` and `|` inside it need a backslash, and the mask is lowercased. Replies cannot run MOBPROG commands, because `!` commands fail the security check. The workaround is a Scriptable SPEAK_PROG on the same NPC that fires on its own line, with $t the player. `$@group` switches belong to the NPC, not the speaker. They expire after six minutes and are not saved.

**Add.**
1. A guard line `?<scriptable expression>` before an entry, with $t the speaker, e.g. `?QUESTSCRIPTED($t mine) and VAR($t MET)==1`.
2. A reply operator `&` that runs the rest of the line as a MOBPROG command, with the NPC as the monster and the speaker as $n.
3. `$@group` kept per speaker. `$@!group` is permanent until changed and kept in a saved script variable. `$@-` returns to the base group.

**Example.** A mine foreman's chat file. Under `?VAR($t MINEQUEST)==done`, `(hello)` answers `9"You're the hero of the mine!`. Otherwise `(work)` answers `9"Clear the rats and I'll pay you.~&mpstartquest minerats$@!foreman_quest`.

*See also:* N19 (Both change MudChat's chat-file parser (guards and operators vs connector and tick fixes); do them together); S9 (N11's per-speaker MudChat groups are saved through S9's behaviour STATE hook).

<!-- from: npc-dialogue/dlg-mudchat-conds-effects -->

<a id="n12"></a>

## N12. Prop_NPCLines: per-NPC wording for stock service lines

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Shopkeepers, bankers, postmen, librarians, trainers, MOBTeacher and Hireling speak fixed English, such as "I'm not interested." or "I don't buy stolen goods.". CoffeeShops alone has 23 literal postSay calls, StdBanker 31, StdPostman 37. The only override is translation_en_TX.properties, which applies to the whole game, by Java class at best. Scriptable can add a line after a stock one but cannot cancel it, because CNCLMSG_PROG matches the message type, not the text. A gruff dwarf smith and a polite elven jeweller sound the same.

**Add.** `Prop_NPCLines`, which holds `key=text` entries.
- The key is a short ID (NOTBUYING, STOLEN, TOOPOOR, OUTOFSTOCK, HIRED, TIMEUP, BALANCE, ...) or the original text in quotes.
- The text may give alternatives split by `|`, use @x1 and @x2 for the engine's values and $n for the customer.
- A mask suffix gives a conditional variant, e.g. `[+AFFINITY >25]`.

The postSay calls in these classes look up the speaker's Prop_NPCLines before translation. An area-level version gives a whole town one voice.

**Example.** `Prop_NPCLines NOTBUYING="Bah! I've no use for that junk."|"Take that rubbish elsewhere." STOLEN="I know where that came from, thief."`

*Note.* A `CNCLMSG_PROG` `<SCRIPT>` block can test the text of the live message in `objs()[0]` and cancel or reword the line (see *Already possible* in the README), so this property is a convenience, not the only route.

*See also:* N21 (N21's MSG_ parameters for Hireling lines are an alternative to N12's Prop_NPCLines).

<!-- from: npc-dialogue/dlg-npclines -->

<a id="n13"></a>

## N13. Companion behaviour: NPC party members who take orders and comment

*Kind:* behaviour · *Value:* high · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#n13).

<a id="n14"></a>

## N14. Dialogue trees: a Dialogue behaviour and .dlg files

*Kind:* behaviour, data file · *Value:* high · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#n14).

<a id="n15"></a>

## N15. Prop_Affinity: one NPC's opinion of each character

*Kind:* property, script function, script command · *Value:* high · *Effort:* large · *Decision:* yes

**Today.** A faction can already serve one NPC: a file such as resources/factions/smith.ini, listed in FACTIONS= in coffeemud.ini. It has:
- RANGE bands;
- CHANGE triggers aimed at that NPC (CMMSG on MSG_GIVE, TALK(REGEX=), BRIBE, KILL, TIME for decay);
- REACTION lines;
- FACTION() and MPFACTION in scripts;
- +/-FACTION masks for MudChat, shop prejudice and ignore masks.

Its values are saved with the character. But it takes one file per NPC, under resources/ instead of world/. The faction shows in FACTIONS and SCORE unless SHOWINFACTIONSCMD=FALSE, and there is no shorthand for gifts of a given item.

**Add.** `Prop_Affinity` on an NPC, or on an area for a village. It holds a hidden faction-like value per NPC and character, and saves it.
- Bands: HOSTILE<-50, COLD<0, NEUTRAL, FRIENDLY>=25, DEVOTED>=75.
- A decay rate.
- Rules: GIFT(item mask) +n, TALK(keyword) +n, QUESTWIN(name) +n, ATTACKED -n, CAUGHT_STEALING -n, KILLED(mob mask) -n, BRIBE per gold.

`AFFINITY($i $n)` gives the number or the band, and `MPAFFINITY $i $n +5` changes it. The mask `+AFFINITY >25` tests it against the NPC in context, so MudChat, shops and Dialogue can use it. Options: `SHOPFACTOR=band:multiplier`, and `REFUSE` below a band.

**Example.** The smith starts NEUTRAL. Rare ore (+10) and his quest (+30) make him FRIENDLY: prices drop 10% and a dialogue option to forge a unique blade opens. Punch him and he turns HOSTILE and will not trade.

*See also:* N6 (Both track one NPC's view of each character (remembered deeds vs an affinity score); N15's rules could read N6's facts); F14 (F14's SHOWMASK and HIDDENNAME would hide a one-NPC faction, half of why N15 proposes Prop_Affinity instead of a faction).

<!-- from: npc-dialogue/dlg-affinity -->

<a id="n16"></a>

## N16. Patroller waypoints that wait, act and change route by time

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A '.' in a patrol pauses one step, so a long wait is a row of dots. Room-id steps re-track from wherever the patroller is, so it finds its route after a fight. Direction steps do not. Acting at a waypoint takes a Scriptable: ENTRY_PROG 100 with `IF INROOM($i == Gate#1)`, then `MPFORCE $i lock door`. A night route is a TIME_PROG that runs MPUNBEHAVE Patroller and then MPBEHAVE Patroller with another route.

**Add.** New step tokens:
- `WAIT=n` (ticks);
- `DO=<command>`, forced, e.g. `DO=lock door`;
- `EMOTE=text`;
- `IF=<zapper mask on host>`, which skips to the next RESTART unless it matches;
- named routes `ROUTE=DAY:...;ROUTE=NIGHT:...`, picked by time of day;
- `RESUME=true`, which after combat tracks back to the last waypoint. Only direction routes need it.

**Example.** `Patroller min=3 max=3;Gate#1;WAIT=5;DO=say Gate secure.;Wall#4;EMOTE=peers over the battlements.;Wall#7;WAIT=3;RESTART`. At night a ROUTE=NIGHT takes the same guard round the barracks with a lantern.

*See also:* N3, N8 (N16's day and night patrol routes overlap N3's Schedule, and N8's catch-up moves both when an area wakes).

<!-- from: behaviors-npc/npc-patrol-stops -->

<a id="n17"></a>

## N17. Warning before aggression

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes (owner: "assuming it is optional")

**Today.** Aggressive attacks on sight after an optional `delay=` and one `MESSAGE=`. Its `BRIBE=` form warns with `BRIBEPAYMSG` and waits, but only for a price, and the wait is miscomputed (N18). Its zapper mask already spares chosen players. A warning that escalates needs a script: GREET_PROG, MPALARM or DELAY_PROG, `IF INROOM($n == $i)`, MPKILL $n. Yet one wrong room can end a single-player run.

**Add.** Options for Aggressive, VeryAggressive, WimpyAggressive and MobileAggressive: `WARN="line|line"`, one line per tick for `WARNTICKS=n`, then the attack if the intruder is still there; `BACKOFF=true`, leaving, sitting down or dropping the weapon resets the warning; `NEAR=1`, the warning is heard as a sound in the rooms next door.

**Example.** A wolf den guardian: `Aggressive WARN="The wolf bares its teeth.|The wolf snarls and crouches!" WARNTICKS=2 BACKOFF=true +ITEM "-raw meat"`. The mask spares a player carrying raw meat.

*See also:* N18 (N18 fixes the Aggressive BRIBE wait window that N17's warning options build on).

<!-- from: behaviors-npc/npc-territorial -->

<a id="n18"></a>

## N18. Fix the NPC behaviour bugs a world will hit

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Seven confirmed bugs that a builder would take for mistakes in their own setup. MOBHunter looks up `Thief_Assasinate` (misspelt), so isHunting is always false, and it can pick itself as prey. MudChat `<30>` tick entries fire on about 71% of ticks (also N19). Aggressive's `BRIBE=` uses the peace ticks as the pay window, as do VeryAggressive and WimpyAggressive; MobileAggressive extends Mobile and needs a separate check. Loyalty's interval, (2*60)/4000, is 0, so it searches every tick. The GoodGuardian and GoodExecutioner pause between attacks never arms. EvilExecutioner skips its noRecurse guard and Scavenger takes items it cannot get or see, both because `&&` binds tighter than `||`.

**Add.** Fix all seven, each with a smoke or engine test.

**Example.** A bounty hunter on MOBHunter follows its hunt through instead of restarting it every cycle, and a drunk's `<10>` hiccup comes up 10% of the time, not 90%.

*See also:* N13 (later) (Both fix Loyalty's zero interval); N17 (N18 fixes the Aggressive BRIBE wait window that N17's warning options build on); N19 (Both fix the MudChat <30> tick entry firing on about 71% of ticks).

<!-- from: behaviors-npc/npc-bugfixes -->

<a id="n19"></a>

## N19. Fix the MudChat defects

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Chat files behave differently from `MUDChat.html` in eight confirmed ways. A `<30>` tick entry fires on about 71% of ticks (also N18). Tick expressions force every connector to AND, so `|` and `~` are ignored. `${PERSONALITY:3}` parses the wrong substring and crashes in nextInt(0). An empty reply line drops the replies after it. An `@` include adds the original say entry, not the re-parented copy. lastSpeakerM is never set, so expiry is never extended or reset. Tick entries take the last match, speech the first. The guide calls the LLM prefix a quote; the code uses a backtick.

**Add.** Fix each, make tick and speech matching agree, correct the guide, and add a MudChat test to the engine suite (Commands/Test.java has none).

**Example.** A tavern drunk with a `<5>` hiccup line hiccups about one tick in twenty, as meant, not nearly every tick.

*See also:* N11 (Both change MudChat's chat-file parser (guards and operators vs connector and tick fixes); do them together); N18 (Both fix the MudChat <30> tick entry firing on about 71% of ticks).

<!-- from: npc-dialogue/dlg-mudchat-fixes -->

<a id="n20"></a>

## N20. Escortee: an NPC that travels with the party to a destination

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** The escort quest templates (normal_, auto_ and competitive_escort1 and 2) MPFORCE the NPC to follow the player and track arrival with a player script; the NPC never knows the way, waits or panics. It speaks only when forced: the quest instructions on accept and $ESCORTED_RESPONSE on arrival, nothing on the way. Left without a leader, it is purged at the second check. Concierge `MOBILE=true` leads, but drops its followers on arrival. TaxiBehavior carries riders with trail options such as NOLOCKS and NOWATER. A leading escort can be scripted with MPTRACKTO, ENTRY_PROG and INROOM(), DEATH_PROG and MPSTEPQUEST, at length.

**Add.** An `Escortee` behaviour: `DEST=<room id>`, `MODE=LEAD|FOLLOW`; `WAITFOR=LEADER`, stop while the leader is away and call "Wait for me!" after n ticks; `PANIC=FLEE|HIDE|COWER` when attacked; `AVOID=<room mask>`; `TALK=room:line` remarks on the way; `ONARRIVE=` and `ONDEATH=` actions (say, set a script variable, call a script function that steps or wins the quest); `TIMEOUT=`.

**Example.** A merchant met at a crossroads: `Escortee DEST=Riverford#1 MODE=LEAD WAITFOR=LEADER PANIC=COWER TALK="Bridge#3:They say trolls live under here." ONARRIVE="SAY Thank you, friends!;SETVAR $n ESCORTED 1"`.

*Re-checked against the code after writing: corrected.*

<!-- from: behaviors-npc/npc-escortee -->

<a id="n21"></a>

## N21. A configurable Hireling

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Hireling takes only `price;minutes;zappermask`. Its keywords (HIRE, HIRELING, WORK, WORKS, TIME, AVAILABLE), lines and refusal are fixed, its contract is not saved, and the HIRE command only says "Are you for hire?". A scripted hireling works and is saved: BRIBE_PROG, `mpforce $i follow $n`, a tattoo or VAR for the contract, DELAY_PROG or QUEST_TIME_PROG to end it, MPMONEY for wages.

**Add.** Hireling parameters: `KEYWORDS=`; `MSG_OFFER=`, `MSG_HIRED=`, `MSG_REFUSE=`, `MSG_EXPIRE=` (or Prop_NPCLines, if approved); `CONTRACT=MINUTES|DAYS|QUEST:<name>|UNTILFIRED`; `UPKEEP=` coins per game day from the player; morale: it leaves if unpaid, or below a set HP% unless loyal, and AFFINITY raises loyalty; `REFUSE=<order mask>`. HIRE opens the terms as a menu. The contract is saved.

**Example.** A mercenary captain with `CONTRACT=QUEST:goblinwar UPKEEP=5 MSG_HIRED="My blade is yours till the goblin king falls."` leaves when the quest ends, or after two unpaid days.

*See also:* N12 (N21's MSG_ parameters for Hireling lines are an alternative to N12's Prop_NPCLines); N13 (later) (Two NPC party-member behaviours with loyalty, orders and saved terms; Companion and a configurable Hireling overlap); S9 (N21's saved Hireling contract is S9's Hireling STATE).

<!-- from: npc-dialogue/dlg-hireling-config -->

<a id="n22"></a>

## N22. Event barks with conditions and cooldowns, in Sounder

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Sounder already gives event-keyed one-liners on MOBs, rooms, items and areas: timed SOUND, PORTAL_ENTER (arrivals), PORTAL_EXIT, WIELD, WEAR, HOLD, GET, DROP, OPEN, DAMAGE and FIGHT, with $n, $p, $e and $s. Time, weather, low hit points, shop and bribe barks need Scriptable progs (TIME_PROG, RAND_PROG with ISWEATHER, HITPRCNT_PROG, BUY_PROG, BRIBE_PROG) written as nested IF RAND chains.

**Add.** Extend Sounder rather than add a behaviour: a compact table, inline or `FILE=barks/guard.txt`, of `EVENT: weight line`. New events GREET_FIRST, KILL, FLEE, ALLY_DIES, IDLE, DAWN, DUSK, NIGHT, RAIN, SNOW, HURT\<n%, SEE_ITEM(mask), SEE_MOB(mask), SHOP_LIST, SHOP_BUY, BRIBED. A mask condition per line; CHANCE= and COOLDOWN= per event; ONCEPERPLAYER; SAY, EMOTE or SOUND prefixes as in MudChat; lines drawn with the seeded dice.

**Example.** A city guard: GREET "Move along." | "Keep your blade sheathed."; WIELD(+WEAPONCLASS SWORD) "Put that away in the city!"; NIGHT "Curfew's at the bell."; HURT<30 "To me! Guards!".

*See also:* PR10 (Both add per-line time, weather and mask conditions, weights and once or cooldown rules to Sounder).

<!-- from: npc-dialogue/dlg-barks -->

<a id="n23"></a>

## N23. Rumours: a world list, gossiping NPCs, rumours from events

*Kind:* data file, behaviour and script command · *Value:* medium · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#n23).

<a id="n24"></a>

## N24. Topic keywords the player can learn and list

*Kind:* presentation · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** The player cannot know which words an NPC reacts to, so quest templates give the phrase outright ("say i accept"). The pieces exist: colour codes such as `^y...^?` highlight a word, `ask <npc> <topic>` or `sayto` fires SPEECH_PROG and MudChat keyword entries, and a VAR or tattoo can remember a topic.

**Add.** Markup `[[mine]]` or `[[the old mine|mine]]` in NPC lines (MudChat, Scriptable say, dialogue) highlights the word and teaches the topic to the character, saved. `ASK <npc> ABOUT` (or TALK) with no topic lists the NPC's topics the player has learned: MudChat keywords, SPEECH_PROG words, dialogue nodes. `HINTTOPICS=true` lists an NPC's topics on first meeting. MudChat indexes entries by their longest word, so the listing needs care.

**Example.** The innkeeper says "Folk don't go near the [[old mine]] since the [[collapse]]." In the next village, `ask miner about collapse` gets the miner's entry.

*See also:* N9 (N24's topic list is shown by N9's TALK, or ASK, with no topic); PR7, N23 (later), N28 (N23 (later)'s rumours, N24's [[topic]] lines and N28's overheard scenes can write PR7 journal entries).

<!-- from: npc-dialogue/dlg-topics -->

<a id="n25"></a>

## N25. LLM calls off the game thread

*Kind:* behaviour, script command and world option · *Value:* medium · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#n25).

<a id="n26"></a>

## N26. LLM NPCs with lore files and saved memory per player

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#n26).

<a id="n27"></a>

## N27. LLM NPCs that act from a declared whitelist

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#n27).

<a id="n28"></a>

## N28. Scene: NPC exchanges the player overhears

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** MudChat ignores other NPCs' speech and stays quiet 8 ticks after one speaks, so NPCs cannot converse. A scene can be one Scriptable on the room or an actor: GREET_PROG, ENTRY_PROG or TIME_PROG starts it, `if var($n SAW_SCENE) == ''` makes it once per player, `mpforce guard1 say ...` gives each line, chained with `mpalarm <ticks> mpcallfunc NEXTLINE`. It works but is tedious to keep in step.

**Add.** A `Scene` behaviour on a room or NPC: `ACTOR guard1=+NAME "tall guard"`, then lines such as `guard1: say Heard the bandits hit the mill again.`, `guard2: emote spits.`, `PAUSE 1`; conditions (player present, time of day, quest state); `REPEAT=n` days or ONCE per player; `INTERRUPTIBLE`, actors stop and turn to a player who speaks to them. Lines run on turns in turn-based time. An overheard scene can post a rumour or note.

**Example.** Entering the barracks at night plays, once, two guards arguing about the captain's bribes; it unlocks a "captain" topic and a rumour.

*See also:* SC7 (Two answers to scripted cutscenes: MPWAIT in any script, or a Scene behaviour for NPC exchanges); PR7, N23 (later), N24 (N23 (later)'s rumours, N24's [[topic]] lines and N28's overheard scenes can write PR7 journal entries).

<!-- from: npc-dialogue/dlg-scenes -->

<a id="n29"></a>

## N29. CHECK(): a skill check for dialogue

*Kind:* script function · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** Persuade, intimidate and deceive options need hand-written arithmetic. The parts exist: STAT(\<who> CHA), ISABLE(\<who> \<skill>) for proficiency (e.g. `$%ISABLE($n Skill_Haggle)%`), FACTION(), RAND() and RANDNUM() on the shared seeded dice, and MATH(). So `if rand() <= math(STAT($n CHA)*3 + ISABLE($n Skill_Haggle)/2)` works, but each builder writes it differently and nothing explains the result.

**Add.** A function `CHECK(<who> <STAT|skill ID> <difficulty> [modifier])` that rolls the shared dice, adds the stat bonus or skill proficiency and any AFFINITY or faction modifier, and returns true or false. It sets `$<who CHECKMARGIN>` and can show "[Charisma check: success]"; a config option hides or shows the rolls. Dialogue CHOICE lines take `CHECK=` directly, if the dialogue tree is approved.

**Example.** `if CHECK($n CHA 15 $%AFFINITY($i $n)%/10)` makes the toll-keeper waive the fee; on failure he raises it.

*See also:* N14 (later), N2, N9 (N2's MPDIALOG, N9's TALK fallback and N29's CHECK= on choices all hook into N14 (later)'s dialogue trees).

<!-- from: npc-dialogue/dlg-skillcheck -->
