# Quests

Quest scripts, quest state and outcomes, branching, rewards, offers, the quest log and templates. Part of the [builder capabilities audit](README.md).

<a id="q1"></a>

## Q1. Finished quests stay known

*Kind:* quest feature · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** A quest with no WAIT, INTERVAL or DATE, such as a story quest started by MPSTARTQUEST, is deleted from the quest list when it stops: by MPENDQUEST, MODIFY QUEST STOP or DISABLE, or its duration running out (enterDormantState calls delQuest). QUESTWINNER, +QUESTWIN and QUESTS WON then forget it, the save drops its winners, and in builder mode the next export deletes its world/quests file. The workaround is SET DURATION 0, or SET WAIT 1 with SET INTERVAL 1.

**Add.** A FINISHED state. A stopped unscheduled quest stays listed with its winners and is saved as FINISHED. Only DESTROY QUEST removes a quest or its file. New settings: `SET RESTART NEVER|ALWAYS|<game time>`, `SET ENDONWIN TRUE` (MPQUESTWIN stops the quest, with cleanup) and `SET AUTOSTART BOOT` (short for WAIT 1). `MODIFY QUEST x REOPEN` clears FINISHED for testing.

**Example.** 'The Drowned Bell' uses AUTOSTART BOOT, RESTART NEVER and ENDONWIN TRUE. When Mira wins, the ghost and the bell vanish. The quest stays FINISHED across saves, and the chapel NPCs keep using QUESTWINNER($n bell).

*See also:* Q5 (Both control whether and when a quest can be done again (SET RESTART vs SET REPEATABLE and MAXWINS)).

<!-- from: quests/qst-keep-ended -->

<a id="q2"></a>

## Q2. Quest-giver and turn-in markers in room listings

*Kind:* property, world option · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Nothing marks an NPC who has a quest for the player. Prop_QuestGiver only prompts when its trigger fires, and room listings read static ambiances. A builder can fake markers with `GIVE AFFECT AmbianceAdder (^Y!^?)` in the quest script, then swap them with MPUNAFFECT and MPAFFECT in DO_ACCEPT and turn-in scripts, by hand for every quest.

**Add.** `Prop_QuestMarker` on a mob or item. `QUEST=<name|*>`, where * means every running quest it hosts or is first quest mob of. `AVAILABLE=`, `ACTIVE=` and `READY=` give the marker strings. `READYFUNC=CAN_COMPLETE` names a FUNCTION_PROG that returns non-empty when the viewer can turn in. getDispositionBlurbs asks the marker per viewer, through canAcceptQuest, which matters with a party and per-character windows. World option `QUESTMARKERS=AUTO` adds it to every quest's first quest mob and to Prop_QuestGiver hosts.

**Example.** 'Old Ned mends a net here. (!)' until Mira accepts. Then a grey '(?)' while the quest is active, and a bright '(?)' once QOBJECTIVE says the bell is found.

<!-- from: quests/qst-markers -->

<a id="q3"></a>

## Q3. Quest journal: objectives, history and game time in QUESTS

*Kind:* script command, script function, presentation · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** QUESTS lists accepted quests by name. QUESTS \<name> adds the instructions and the free-text PROGRESS and TIME_REMAINING vars the script sets, so templates build 'Killed 3/5' by hand. Ticks show as real minutes, which mean nothing under TIMEMODE TURNS unless the script sets TIME_REMAINING_TYPE. There are no objectives, no history and no grouping by SET CATEGORY.

**Add.** A per-character log saved with the character, as a hidden savable effect. `MPQOBJECTIVE $n <quest> ADD <id> <target> <text>`, then `SET|BUMP|DONE|FAIL|HIDE <id> (n)` or `CLEAR`. `MPQJOURNAL $n <quest> <text>` adds an entry dated on the game calendar. `QOBJECTIVE($n quest id)` returns the count, DONE or FAILED. Quest-script `SET OBJECTIVE` and `SET JOURNAL` give each STEP's defaults. QUESTS groups by CATEGORY, main first, and shows `[x]/[ ] text n/m` with time left in game days or turns. `QUESTS <name>` adds history, `QUESTS LOG` recent entries. OBJECTIVEn and OBJECTIVEnDONE vars set by MPSETVAR are drawn the same way. A 'Quest updated:' notice can be turned off by a world option.

**Example.** Accepting the bell quest runs `MPQOBJECTIVE $n bell ADD drowned 5 Lay 5 drowned men to rest`. Each drowned man's DEATH_PROG runs `MPQOBJECTIVE $n bell BUMP drowned`.

*See also:* Q16 (Q16's objective locations hang on Q3's journal objectives); PR7 (Two journals: Q3's quest log with MPQJOURNAL and PR7's JOURNAL with MPJOURNAL and automatic quest entries).

<!-- from: quests/qst-journal, presentation/pr-quest-log-gametime -->

<a id="q4"></a>

## Q4. Per-character quest status and outcome

*Kind:* script function, script command · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The only per-character record is the winners map, name to last win time. Nothing records a failed or abandoned quest, or how it ended. `MPQSET * STATISTICS FAILED` only bumps a counter. Builders use tattoos (MPTATTOO, HASTATTOO) and MPSETVAR instead. A KEYPLAYER death always resets the whole quest, which suits a server event, not 'the escort died, you failed'.

**Add.** Per character: status ACTIVE, WON, FAILED or ABANDONED, an outcome word and a game-calendar time, saved with the winners. `MPQUESTWIN $n <quest> (OUTCOME <word>)`. `MPQUESTFAIL $n <quest> (<reason>)` does the MPENDQUEST $n cleanup and fires QUESTFAIL_PROG. Functions `QUESTSTATUS($n quest)` and `QUESTOUTCOME($n quest)`, with $% %-forms. Masks +QUESTFAIL/-QUESTFAIL and +QUESTOUTCOME "quest:word". `GIVE STAT KEYPLAYER FAIL` fails the quest for those on it; TRUE still resets. `QUESTS FAILED`, and outcomes in QUESTS WON.

**Example.** Merchant Oskar's escort has KEYPLAYER FAIL. If he dies, his widow's GREET_PROG tests `QUESTSTATUS($n oskar == FAILED)` and blames the player. In 'Bandits of the Fen', `MPQUESTWIN $n bandits OUTCOME spared` puts the chief in town as a guard.

*See also:* ST5, Q11, JS18 (Quest outcome hooks overlap: ST5's QUEST_PROG FAIL and END EXPIRED, Q4's QUESTFAIL_PROG, Q11's QUESTTIMEOUT_PROG, and JS18's onQuestFail and onQuestEnd in JavaScript); Q8, Q11, Q12, Q15 (These use Q4's per-character quest status (WON, FAILED, ABANDONED, EXPIRED) and MPQUESTFAIL).

<!-- from: quests/qst-status -->

<a id="q5"></a>

## Q5. Repeatable quests with win counts and game-time cooldowns

*Kind:* quest feature, script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Winners keep only the last win time, with no count, and scripts can only ask QUESTWINNER, yes or no. Templates repeat by testing $MULTIPLE in every check. The stored time is GameTime.now(), not the game calendar, so 'once per game day' cannot be said. Workarounds: an achievement with EVENT="QUESTOR" NUM=10 for a count, and a timed tattoo such as `MPTATTOO $n 1200 WOLF_CD` for a cooldown.

**Add.** A win count and a game-calendar time per winner. Quest settings `SET REPEATABLE NO|YES|<game time, e.g. 1 mudday>` and `SET MAXWINS <n>`, checked by canAcceptQuest before CAN_ACCEPT. Functions `QUESTWINS($n quest)` and `QUESTWINAGE($n quest)` (game hours since the last win), and a mask +QUESTWINS "quest:>=10". QUESTS WON shows the counts.

**Example.** 'Wolf bounty' at the ranger lodge has REPEATABLE 1 mudday and MAXWINS 10. After ten bounties, 'The Wolf King' opens with PLAYERMASK +QUESTWINS "wolf_bounty:>=10".

*See also:* Q1 (Both control whether and when a quest can be done again (SET RESTART vs SET REPEATABLE and MAXWINS)).

<!-- from: quests/qst-repeatable -->

<a id="q6"></a>

## Q6. Named steps and jumps for branching

*Kind:* quest feature · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Steps are numbered by position. STEP takes only BREAK, BACK and AUTO, and MPSTEPQUEST skips (+N) or jumps (=N) by number only; those two forms are documented nowhere. Inserting a step breaks every jump, a spare-or-kill branch must jump to a number, and the save holds only STEPNUM.

**Add.** `STEP <label>` and `STEP AUTO <label>` name the step that follows. `MPSTEPQUEST <quest> =<label>` jumps to it. `STEP GOTO <label>` ends a branch, so the next step is the label. `QVAR(quest STEPNAME == label)` and `$%QVAR(quest STEPNAME)%` read the current one. Saves store the label as well as the number, so an edited file still restores to the right step.

**Example.** In 'Bandits of the Fen', step 'confront' offers MPCHOOSE spare or kill. The script runs `MPSTEPQUEST bandits =spared` or `=slain`. Each branch loads its own mobs and ends with `STEP GOTO epilogue`.

*Re-checked against the code after writing: accurate.*

*See also:* DT2 (DT2's MODIFY QUEST STEP \<label> needs Q6's named steps).

<!-- from: quests/qst-named-steps -->

<a id="q7"></a>

## Q7. Control re-offers and accept a declined quest later

*Kind:* property, player command · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_QuestGiver on an area offers once per character, remembered only in memory, so a 'No' at the area border loses the quest. On a room or exit it asks again on every entry. A GenQuestBoard lists every running quest the reader can accept, and TRIGGER=SPEAK on the giver re-offers when the player talks, but no player command lists past offers.

**Add.** Prop_QuestGiver `REOFFER=ALWAYS|ONCE|<game time>`, with offers kept in the save. Player commands `QUESTS OFFERED`, which lists quests offered and still acceptable through canAcceptQuest, and `QUESTS ACCEPT <n|name>`, which calls acceptQuest. Reading a quest board entry adds it to OFFERED.

**Example.** Entering the swamp offers 'The Drowned Bell' and Mira says no. After talking to Ned she types QUESTS OFFERED, then QUESTS ACCEPT 1.

*See also:* S1 (Both save which quests Prop_QuestGiver has offered (REOFFER offers vs OFFERED:\<name> quest vars)).

<!-- from: quests/qst-offers -->

<a id="q8"></a>

## Q8. Prop_QuestItem: bound quest items

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A carried quest item can be dropped, sold, given or junked unless the builder stacks Prop_Undroppable, Prop_Unsellable and Prop_ItemBinder DEATHKEEP=TRUE (or bundles them in Prop_MultiEffects) and blocks give and junk with a CNCLMSG_PROG. It is cleaned up only if registered with `MPQSET * QUESTOBJ $o`, and then only when the whole quest stops.

**Add.** `Prop_QuestItem QUEST=<name> (NODROP NOSELL NOGIVE) (REMOVEON=WON,FAILED,ENDED) (KEEPONDEATH)`. It blocks those actions with a message, stays with the character on death, vanishes when the holder's quest leaves ACTIVE, and is listed under QUESTS \<name>. REMOVEON needs the per-character quest status item.

**Example.** The sunken bell has `Prop_QuestItem QUEST=bell NODROP NOSELL REMOVEON=WON`. It cannot be pawned, and it leaves the pack when the priest takes it.

*See also:* Q4, Q11, Q12, Q15 (These use Q4's per-character quest status (WON, FAILED, ABANDONED, EXPIRED) and MPQUESTFAIL).

<!-- from: quests/qst-quest-item -->

<a id="q9"></a>

## Q9. Quest QUIET fix and a quest-script help page

*Kind:* quest fix and documentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Status (docs audit, 2026-11):** QUIET and OPTIONAL are prefix words or switches in a quest script (`DefaultQuest.java` ~1447-1490); there is no `SET QUIET ON/OFF`.

**Today.** In DefaultQuest, `SET QUIET ON/OFF` sets the quiet flag from the OPTIONAL state (`q.beQuiet=optional`, line 1131). Some features are found only in the code: MPSTEPQUEST's +N/=N forms, MPQUESTWIN -name (removes a win), TIME_REMAINING_TYPE, QUEST_TIME_PROG -1/-2, and MPRUN and JCONSOLE as test tools. MPSTEPQUEST is missing from AHELP's command list and Scriptable.html. The rest is scattered over Scriptable.html, ArchonGuide.html and GameBuildersGuide.html, which still describes MUDGrinder and online-player rules.

**Add.** The QUIET fix. A CoffeeSUD quest page, AHELP QUESTSCRIPT and a guide section, that walks one single-player story quest from offer to reward, gathers these features and the new ones, and drops the MUDGrinder and online-player text.

**Example.** A builder reads AHELP QUESTSCRIPT and writes a two-branch quest without opening the Java.

*See also:* SC9, ST5 (Q9, SC9 and ST5 each ask to document MPSTEPQUEST or QUEST_TIME_PROG's -1 and -2 codes; write it once).

<!-- from: quests/qst-docs-fixes -->

<a id="q10"></a>

## Q10. Declarative quest rewards, shown when offered

*Kind:* quest feature, script command · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Rewards are scripted by hand at turn-in, with mpexp, mpfaction, mpoload and give. Offers, boards and QUESTS cannot say what a quest pays, beyond hand-written text in MESSAGE= or INSTRUCTIONS. A hidden EVENT="SCRIPTED" achievement granted by `MPACHIEVE $n <id> 1` gives a one-line reward, but only once per character, with no faction or catalog item award.

**Add.** `SET REWARDS "<awards>"` in the achievements' award syntax (CURRENCY, XP, QP, TITLE, ABILITY, EXPERTISE, STAT, TATTOO, ITEM, MOB), plus FACTION:\<id>:<+n> and ITEM:\<catalog name>. `MPQUESTWIN $n quest REWARD` grants them with one standard message; `MPREWARD $n "<awards>"` gives ad-hoc ones. Prop_QuestGiver DETAILS, quest boards and QUESTS show 'Reward:'. It reuses Achievements.parseAwards and the grant code, made public on the library.

**Example.** `SET REWARDS "100 CURRENCY 15% XP ITEM:silver_bell_charm FACTION:Brenn:+200"`. Ned's turn-in becomes `MPQUESTWIN $n bell REWARD` instead of fifteen lines.

*See also:* P5 (Both extend Achievements.parseAwards' award syntax (FACTION, catalog ITEM, SCRIPT, VAR, RECIPE)).

<!-- from: quests/qst-rewards -->

<a id="q11"></a>

## Q11. Per-character quest deadlines

*Kind:* script command, script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** The only time limit is the quest's global SET DURATION, counted from the quest's start, not from when the player accepted. A per-character deadline today is a timed tattoo, `MPTATTOO $n 360 MEDICINE_DUE`, which follows the game clock and is saved. The script polls `!HASTATTOO` from a RAND_PROG and sets TIME_REMAINING by hand for QUESTS. DELAY_PROG timers are not saved.

**Add.** `MPQTIMER $n <quest> <game time>` and `MPQTIMER $n <quest> OFF`. QUESTS shows time left in game hours or days. On expiry, QUESTTIMEOUT_PROG \<quest> fires on the player's quest scripts; with no such trigger, the quest fails with reason EXPIRED. The timer is saved with the character and runs on the game clock, so pause and turn-based time hold.

**Example.** A courier job runs `MPQTIMER $n medicine 6 mudhours`. If the medicine is late, the patient dies: QUESTTIMEOUT_PROG medicine fails the quest and junks the vial.

*See also:* ST5, Q4, JS18 (Quest outcome hooks overlap: ST5's QUEST_PROG FAIL and END EXPIRED, Q4's QUESTFAIL_PROG, Q11's QUESTTIMEOUT_PROG, and JS18's onQuestFail and onQuestEnd in JavaScript); Q4, Q8, Q12, Q15 (These use Q4's per-character quest status (WON, FAILED, ABANDONED, EXPIRED) and MPQUESTFAIL).

<!-- from: quests/qst-player-timers -->

<a id="q12"></a>

## Q12. Quest credit for the party or the whole profile

*Kind:* quest feature · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Accepting, winning, QUESTWINNER and +QUESTWIN are keyed by character name. With a party or several characters in the profile, the leader's quest does not count for the others, and a story quest won by one character is open again to another. Builders write MPACCTATTOO and HASACCTATTOO checks, or MPSCRIPT each party member by hand in DO_ACCEPT.

**Add.** `SET SHARED NONE|PARTY|PROFILE`. NONE keeps today's behaviour. PARTY gives the tracking script and log to every party member present on accept, and MPQUESTWIN and MPQUESTFAIL apply to the party. PROFILE makes QUESTWINNER, QUESTSTATUS and the masks answer for any character of the profile, and shows the log to all of them.

**Example.** 'The Drowned Bell' is SHARED PROFILE, so once Mira wins, the chapel treats her alt Tobin as the bell's saviour. 'Wolf bounty' is SHARED PARTY, so all four party members are paid.

*See also:* Q4, Q8, Q11, Q15 (These use Q4's per-character quest status (WON, FAILED, ABANDONED, EXPIRED) and MPQUESTFAIL).

<!-- from: quests/qst-party-scope -->

<a id="q13"></a>

## Q13. Single-player story templates for the Quest Maker

*Kind:* data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** The 74 templates in resources/quests/templates cover the old goal types. Some shapes exist: normal_travel1 (explore), normal_travel2 (talk to), normal_delivery1-4 (hand over), normal_killer1/2 with $MULTIPLE YES (repeat kills, no cooldown). There is no multi-stage chain, choice or use-an-item-here template. The competitive_* ones race players on QUESTCHAT, and $QUEST_ANNOUNCECHANNEL lines target channels the fork removed.

**Add.** story_talk1 (with an optional hand-over), story_chain1 (N stages, each with giver, goal and journal text), story_choice1 (two outcomes via MPCHOOSE, with their own rewards, factions and outcome), story_useitem1 (use or put an item in a room), story_explore1, and story_bounty1 (repeatable kill count with a cooldown). They use the journal, status and rewards when those exist, plain MOBPROG otherwise. A `#!QUESTMAKER_MODE` header lets the list hide competitive_* in single-player.

**Example.** CREATE QUEST, pick story_choice1, enter 'bandit chief' with outcomes 'spare' and 'kill'. The wizard writes one self-contained world/quests/bandits.quest.

<!-- from: quests/qst-story-templates -->

<a id="q14"></a>

## Q14. Quest board with generated jobs, refreshed daily

*Kind:* behaviour, generator · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** StdQuestBoard and GenQuestBoard list every running quest the reader can accept, with filters such as SAREAMASK, TYPFILTER and CATFILTER, and READ board \<n> offers to accept. RandomQuests MINQUESTS=3 MAXQUESTS=3 EXPIRE=... on a mob or area gives it a rotating set. But EXPIRE is a duration, not a calendar refresh, templates get no player context, generated quests do not persist, and the RandomQuests core-count bug blocks it on 1 to 3 core machines.

**Add.** A QuestBoard behaviour or property for an item or room: `COUNT=3 REFRESH=MUDDAY:1 TEMPLATE=normal VARS="..." FILE= TAGID=`. It keeps COUNT quests generated through the fixed RandomQuests, lists them on READ or LOOK, and the player takes one with ACCEPT \<n>; a new one comes at the next refresh. Templates get $PLAYER_LEVEL, $PARTY_SIZE, $PLAYER_FACTION_\<id> and $QUESTS_DONE. Taken quests persist through gen-persist-quests.

**Example.** The Mercenaries' Guild board in Port Halden always shows three jobs, an escort, a collection and a kill, at the party's level, with a new one each morning.

*See also:* G2, S8 (Q14's quest board needs G2's RandomQuests fix and S8's saved generated quests).

<!-- from: generators/quest-board -->

<a id="q15"></a>

## Q15. Undroppable main quests and a working drop hook

*Kind:* quest feature, script function · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** QUESTS DROP works on any accepted quest, the main story included. DO_DROP runs only for scripts whose quest no longer exists, never on the confirmed drop. The confirmed drop does run `QUEST_TIME_PROG <quest> -1` and `-2` on the tracking script, which can serve as a hook, but these also fire on MPENDQUEST $n, so telling a drop from a turn-in needs a var.

**Add.** `SET DROPPABLE FALSE`, so QUESTS DROP refuses. A FUNCTION_PROG CAN_DROP: a non-empty return refuses the drop and is shown to the player. DO_DROP is called on the confirmed drop, and the status ABANDONED is recorded.

**Example.** The main story cannot be dropped. Dropping the side delivery runs DO_DROP, which takes back the parcel.

*See also:* Q4, Q8, Q11, Q12 (These use Q4's per-character quest status (WON, FAILED, ABANDONED, EXPIRED) and MPQUESTFAIL).

<!-- from: quests/qst-drop-control -->

<a id="q16"></a>

## Q16. Objective locations and QUESTS WHERE

*Kind:* presentation, world option · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** QUESTS shows no location. The giver's room and area appear in the offer text of Prop_QuestGiver and quest boards. The giver's name and area also reach QUEST \<name>: in all normal_ and nearly all competitive_ templates (48 of 74), the giver's ONCE_PROG runs `MPQSET * INSTRUCTIONS $i in $a wants you to '...'`. A builder can write $%QVAR(quest MOB_ROOMID)% into INSTRUCTIONS or PROGRESS. Prayer_SeekersPrayer reveals quest-givers, and the tracking library backs MPTRACKTO for mobs, but nothing in the quest system points the player at an objective. Concierge already gives a route to a room through the tracking library ('The way to X from here is: ...'), and class skills such as Ranger_Track let a player track a named mob.

**Add.** An objective option `AT=<roomID|area|questobj:MOB>`, on the journal's objectives. QUESTS shows 'in \<area>', and `QUESTS WHERE <n>` gives the first direction toward it through the tracking library. A world option `QUESTTRACK=OFF|AREA|PATH` sets how much is shown. It depends on the quest journal item.

**Example.** 'Return the bell to the chapel (Brenn, Chapel of Tides)'. QUESTS WHERE 1 answers 'The chapel lies to the north-east.'

*Re-checked against the code after writing: corrected.*

*See also:* Q3 (Q16's objective locations hang on Q3's journal objectives).

<!-- from: quests/qst-waypoint -->
