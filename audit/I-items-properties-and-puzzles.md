# Items, properties and puzzles

Item properties, general-purpose property wrappers, containers, locks, keys, traps, books, transformations and puzzle mechanisms. Part of the [builder capabilities audit](README.md).

<a id="i1"></a>

## I1. Fix property bugs that make documented options do nothing

*Kind:* property fixes · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Several documented options fail silently. In Prop_PropSetter, TRIGGER=DROP_PUTIN and TRIGGER=HITTING_WITH never fire, MOUNT also fires on hitting and PUT on DROP: the constants (64, 128) do not match TRIGGER_DESC (256, 512). Prop_LimitedContents tests the new item instead of each existing one, so it counts every item. Prop_ItemBinder checks ON= against the TO string, so abbreviations fail. AHELP PROP_UNDROPPABLE finds nothing; the key is PROP_DROPPABLE. There is no workaround.

**Add.** Bits 256 and 512 in TriggeredAffect and fixed checks in Prop_PropSetter; the mask test on I in Prop_LimitedContents; boundOn in Prop_ItemBinder; the renamed help key. Then the defects in DOCS b075-b078 (ModFaction percent squaring, MoveRestrictor ISEARCH, null check and CHANCE off-by-one, RoomRedirect host, ItemSlot look text), each with a small engine test.

**Example.** TRIGGER=HITTING_WITH on a vampiric dagger fires, and a quiver limited to 20 arrows counts only arrows.

*See also:* I15 (I15's receptacles rely on I1's Prop_LimitedContents count fix).

<!-- from: properties/pr-prop-bugfix -->

<a id="i2"></a>

## I2. MPDOOR: change a door on both sides from anywhere, with a message

*Kind:* script command, script function · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** MPOPEN, MPCLOSE, MPLOCK and MPUNLOCK change one Exit object, silently, and skip its RESETTIME relock timer. They take $xN, not a direction word. Opening a door on both sides with messages takes four lines: MPOPEN $xN, MPAT \<room> MPOPEN $xS, and an MPECHO per room. ISOPEN and ISLOCKED read state, but for another room only inside MPAT.

**Add.** MPDOOR \<OPEN|CLOSE|LOCK|UNLOCK|TOGGLE> \<room id|here|$r> \<direction|container name> [QUIET|"message"]. It goes through roomAffectFully, as Prop_OpenCommand does: both sides change, the message (default '\<T-NAME> swings open.') shows in both rooms, and the relock timer starts. It works on containers too. DOORSTATE(\<room> \<dir>) returns open, closed or locked. The help for MPOPEN and its siblings says they are one-sided.

**Example.** A cellar lever runs MPDOOR OPEN "Crypt#12" north "With a grinding of stone the crypt door swings open." The crypt sees it too, and the door is open from both sides.

*See also:* JS17, SC4, SC6 (JS17's state(), signal() and door() helpers wrap SC4's per-object state, SC6's signals and I2's MPDOOR); I9 (I9's switch actions are built on I2's MPDOOR).

<!-- from: items-puzzles/ip-mpdoor -->

<a id="i3"></a>

## I3. Books with pages kept in the item and the world files

*Kind:* presentation, data file, script trigger · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** StdBook and GenBook keep pages in the journal table CMJRNL, keyed by the book's name. That is save state: pages written in builder mode never reach world/ or a new game, and same-named books share them. GenJournal boards have the same gap. GenReadable holds one text, or one FILE= under resources/. A CNCLMSG_PROG READ script can fake pages.

**Add.** A static mode for GenBook (or a GenTome) whose text holds pages: \<PAGE TITLE="..." LANG=Elvish>...\</PAGE>, or FILE=world/texts/ost.txt with '=== title ===' separators. READ BOOK lists contents; READ 3 BOOK, READ NEXT and READ PREV turn pages. VARIES works per page, a page may carry LORE=key, READ_PROG \<page> fires, and TEAR PAGE 3 FROM BOOK makes a page item. world/journals/*.txt seeds boards and books.

**Example.** The 'Annals of Ost' keeps 12 pages in its area file. Page 7, torn out and hidden in the tower, starts a quest step when read.

*See also:* ST19 (I3's paged books fire a READ_PROG \<page>, the trigger ST19 adds); PR6 (I3's pages carry LORE= keys from PR6's codex); S10 (Both put book and board pages in world/journals (seed text files vs journal XML)).

<!-- from: items-puzzles/ip-static-books, presentation/pr-content-books -->

<a id="i4"></a>

## I4. Prop_When: props and behaviours active only while a condition holds

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Zapper masks cover most conditions: MASK= on nearly every prop, with -IF (ISMOON, ISHOUR, QUESTWINNER), -CHANCE and -TATTOOS, and decaying tattoos as cooldowns. But masks are tested at events such as entering, and Prop_Transporter, Prop_AddDamage and Prop_MultiEffects children take none. Quests test masks only at start. A behaviour that is on only while a condition holds needs TIME_PROG with MPBEHAVE and MPUNBEHAVE. Nothing gives once per profile or world.

**Add.** Prop_When PROPS="Prop_X(args);Prop_Y(args)" (MultiEffects grammar), or ID=\<ability or behaviour> PARMS="...", with WHEN=\<zapper mask>, EVERY=\<ticks>, CHECK=OWNER|HOST|ROOMPLAYERS|TRIGGERER, ONCE=CHAR|PROFILE|WORLD, COOLDOWN=\<ticks or game hours>, CHANCE=\<pct>, ONSTART=, ONEND=, ELSEMSG=. It adds what it wraps when the mask turns true and removes it when false. It works on rooms, areas, exits and items, reuses decaying tattoos, and saves its state.

**Example.** Darkwood: WHEN="-IF ISMOON(FULL)" with RandomMonsters loading catalog werewolves and an Emoter howling; it ends by itself. A fairy ring: COOLDOWN=24h PROPS="Prop_Transporter(Feywild#1)".

*See also:* N1 (Alternatives for behaviours active only while a condition holds: a WHEN= parameter on every behaviour, or a Prop_When wrapper).

<!-- from: behaviors-world/bw-conditional, properties/pr-when -->

<a id="i5"></a>

## I5. Prop_Transform: things that change form over time or by condition

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Decay only destroys, Age only turns a GenCaged item into its mob, TemporaryAffects only adds or removes props, Prop_ClosedSeason only hides. A change of form needs a script per object: TIME_PROG, DAY_PROG or DELAY_PROG, checks such as ISSEASON or INLOCALE, then MPOLOAD or MPMLOAD and MPPURGE $i. Farming and Gardening grow plants, but as skills.

**Add.** Prop_Transform with AFTER=\<ticks>, DAYS=\<game days, kept in saves> or WHEN=\<mask on its room, or NIGHT, FULLMOON, WINTER>; START=GET|DROP|WEAR|ALWAYS; INTO=\<catalog item>, MOB=\<catalog mob>, or SET="NAME=.. DISPLAY=.. DESC=.. PROPS=.." to change in place; REVERT=TRUE when WHEN ends; RESET to restart on leaving the condition; ONLYIN=room|container|carried; KEEP=CONTENTS,DAMAGE,FOLLOW; MSG=. Time left lives in miscText. A result with its own Prop_Transform makes a chain.

**Example.** A dragon egg carried three game days hatches into a wyrmling that follows its finder. A stone gargoyle wakes as a hostile mob at night and turns to stone at dawn.

*See also:* I22, I27 (All three replace an item in place with another while keeping its state; share MPTRANSFORM's KEEP code).

<!-- from: behaviors-world/bw-transformer, properties/pr-transform -->

<a id="i6"></a>

## I6. Item sets with tiers, any bonus, and progress shown

*Kind:* property, script function, script trigger · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** ALLSET=name-N exists only on Prop_HaveAdjuster (pieces carried) and Prop_WearAdjuster (pieces worn): one threshold each, adjustments only (stats, saves, ableprofs and the like; no granted spells). One of each fakes two tiers. Mixed thresholds misbehave, because setCheck writes one piece's result to the whole set. Prop_WearSpellCast, Prop_WearEnabler, Prop_WearResister and Prop_AddDamage ignore ALLSET, so a granted spell needs WEAR_PROG and REMOVE_PROG on every piece. A MASK="-WORN +piece ..." on one piece only half works: it is checked when the spell is added, not when another piece comes off. LOOK and IDENTIFY say nothing of sets.

**Add.** First the threshold fix. Then Prop_ItemSet SET=drowned_king NAME="Regalia of the Drowned King" COUNT=WORN|HELD TIER2="Prop_WearAdjuster(savewat+20)" TIER4="Prop_WearSpellCast(Spell_WaterBreathing);Prop_WearEnabler(Chant_Tsunami)" ONMSG= MASK=. Tiers sit on one piece or in resources/itemsets.txt. The highest tiers met apply as hidden effects and end when a piece comes off. LOOK and IDENTIFY show 'Set: 3/5 worn' and each tier. ALLSET works on the other Wear and Have props. Scripts get SETCOUNT($n \<set>), and SET_PROG when a tier turns on or off.

**Example.** Four dragonbone pieces lie across the world: two give fire resistance, all four the Dragonbreath ability.

*Re-checked against the code after writing: corrected.*

<!-- from: properties/pr-itemset, items-puzzles/ip-item-sets -->

<a id="i7"></a>

## I7. Prop_Cursed: hidden curses that Remove Curse ends cleanly

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** No-remove and no-drop items exist: MODIFY ITEM's misc flags, MPSET $i ISREMOVABLE false, or Prop_PropSetter(TRIGGER=WEAR_WIELD ISREMOVABLE=false) for a curse shown once worn. Prayer_RemoveCurse resets the flags but, ending only DOMAIN_CURSING effects, leaves penalties such as Prop_WearAdjuster(str-3). Prayer_CurseItem added in the builder cannot be ended. There is no false identity, and a curse broken by a deed needs a SPEECH_PROG.

**Add.** Prop_Cursed, in DOMAIN_CURSING so endLowerCurses ends it: LEVEL=15 HIDDEN=TRUE NOREMOVE NODROP PENALTY="Prop_WearAdjuster(str-3 dex-2);Prop_WearSpellCast(Prayer_CurseLuck)" REVEAL="The ring tightens on your finger!" BREAK=\<ritual steps> or BREAKMASK=\<mask> ONBREAK=CRUMBLE|KEEP|BECOME:\<catalog item>. It sets the existing flags. Hidden, it identifies as normal below LEVEL until worn. Lifted, its penalties go with it.

**Example.** A dead knight's ring seems to give +2 STR. Worn, it will not come off and drains wisdom, until the player confesses at the chapel or a strong enough priest lifts it. Then it crumbles.

<!-- from: properties/pr-cursed -->

<a id="i8"></a>

## I8. Prop_Invokable: item powers on demand, with recharging charges

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** GenWand already gives a spell on a spoken magic word, with charges (USES, MAXUSES), and its wear location can be a finger, so a ring works too. But only characters with a WandUsage class skill can use it; for others it 'glows faintly'. Recharging needs a TIME_PROG running MPSET $i USES 3. Without the skill, a SPEECH_PROG with a counter and MPCAST does it. Prop_UseSpellCast fires on wear, fill or put, never on demand.

**Add.** Prop_Invokable, with no class skill needed: WORD="Ignis" or VERB=INVOKE (INVOKE \<item>), SPELLS="Spell_Fireball;LEVEL=12", TARGET=SELF|ENEMY|ROOM, CHARGES=3, RECHARGE=DAWN|DAILY|TICKS=200, COOLDOWN=5, REQUIRE=WORN|HELD, MASK=, MSG= and EMPTYMSG="The ring is cold.". Charges live in miscText. IDENTIFY shows the charges and the recharge rule.

**Example.** The Ring of the Ember Lord casts Fireball three times a day when its wearer says 'Ignis', and recharges at dawn on the area's clock.

<!-- from: properties/pr-invokable -->

<a id="i9"></a>

## I9. Prop_Switch: levers, buttons and dials that keep their state

*Kind:* property, script trigger, script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A lever must be ungettable, but StdItem then refuses PUSH and PULL ('You can't move X'). So the builder writes CNCLMSG_PROG PULL with RETURN CANCEL, keeps state in MPSETVAR or TAGS, and calls MPOPEN twice, skipping the relock timer. Prop_OpenCommand answers only verbs that reach HUH: PULL and PUSH are commands, TURN is Turn Undead's word. ACTIVATE takes only Electronics.

**Add.** Prop_Switch for items and exits: VERBS="pull,push,turn,press,flip" (ahead of commands), STATES="down,up" or a dial 1-8, START=, DESC_\<state> for LOOK, MSG_\<state>, ACTION_\<state>="door open Gate#3 east; signal gate1 on; unhide 'a narrow stair'" (built on MPDOOR, signals and MPOLOAD), RESET=\<ticks>, REQUIRES=\<mask> with REQMSG=, ONCE=true, MASK=. State lives in the property text. Scripts get SWITCH_PROG \<state> and SWITCHSTATE(\<obj>); ACTIVATE and DEACTIVATE work too.

**Example.** PULL RUSTED LEVER raises the portcullis of Gate#3 east, both sides, for 20 ticks; LOOK LEVER says 'The lever is up.' A second lever moves only while the iron crank is carried.

*See also:* ST7, I19 (All three let an object's own verb (TURN, PULL, PUSH) win over stock commands and skills; one parser hook serves them); SC6, I12 (later), I15, I25 (The puzzle props send and receive SC6's signals (signal gate1 on, ONFULL=signal, SIGNAL inputs)); I2 (I9's switch actions are built on I2's MPDOOR); I16 (I16's combination lock shares I9's switch state).

<!-- from: items-puzzles/ip-prop-switch -->

<a id="i10"></a>

## I10. USE X ON Y and COMBINE A WITH B, with triggers and a table

*Kind:* player commands, script triggers, data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Commands/ has no USE, APPLY or COMBINE, and there is no USE trigger. CREATE COMMAND USE (a GenCommand in world/custom.cmare) with one FUNCTION_PROG EXECUTE script and a SWITCH over item pairs works, as does CMDFAIL_PROG USE on each target. Either way every pair is hand-written, with no standard answer for the rest.

**Add.** Player commands USE \<item> [ON \<target>] and COMBINE \<item> WITH \<item>, sending a new TYP_USE message to tool and target. USE_PROG and COMBINE_PROG [keywords] fire on either, with $o the tool and $t the target. world/combinations.txt has rows ITEM_A, ITEM_B (name, tag or catalog name), RESULT (catalog item or nothing), CONSUME=A,B, MASK= or SKILL=, MESSAGE. Simple combining needs no script; the rest answers 'Nothing happens.'

**Example.** USE OIL FLASK ON HINGE silences the squeaking door that wakes the guard. COMBINE TORN MAP WITH TORN MAP makes 'the old smuggler's map' from the catalog.

<!-- from: items-puzzles/ip-use-combine -->

<a id="i11"></a>

## I11. Prop_Discoverable: hidden things anyone can find

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Prop_Hidden is seen only with CAN_SEE_HIDDEN, from Thief_Search or Searching. Searching is open to every class at level 4 in allqualifylist.txt, and a data change can make it AUTOGAIN at level 1. MPHIDE sets IS_NOT_SEEN, which no search finds. Examine-to-reveal works as a LOOK_PROG (or EXECMSG_PROG EXAMINE) running MPUNAFFECT on Prop_Hidden, and the removal is saved. There is no stat roll, no hint, and no way without a script.

**Add.** Prop_Discoverable on items, exits and mobs: FOUNDBY=examine:bookshelf|search|look:fireplace, CHECK="INT 12" or DC=15 with a bonus from Searching and Thief_Search, MSG="Behind a loose book you find a small brass lever.", HINT="One book sticks out." shown when the parent is looked at, and STAYS=true. A basic SEARCH that takes a turn works without the skills.

**Example.** A secret door behind a tapestry (FOUNDBY=examine:tapestry) is found by anyone who examines the tapestry; a thief also finds it with SEARCH.

*See also:* P11 (P11's ON=FIND discovery XP fires when a hidden thing is found, as I11's discoveries are).

<!-- from: items-puzzles/ip-discoverable -->

<a id="i12"></a>

## I12. PuzzleController: puzzle state, sequences and solve actions

*Kind:* behaviour, builder tool · *Value:* high · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#i12).

<a id="i13"></a>

## I13. Prop_BlockedExit: blockages the player must clear

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Chant_CaveIn needs a caster and runs out; Prop_WeakBridge breaks and mends itself. A blockage can be faked as a closed, locked door named 'a pile of rubble' with Prop_OpenCommand ('clear rubble;dig rubble;NOOPEN'), and a script can count tool attempts in a VAR before MPUNLOCK and MPOPEN. The door state is saved, but the exit's close timer or a reset can shut it again. Nothing wears a blockage down, burns it or grows it back.

**Add.** Prop_BlockedExit on exits: NAME="a pile of rubble", HP=40, TOOL=\<mask on the held item>, SKILL=Digging|STR (each attempt lowers HP), FIRE=TRUE, MSG= and CLEARMSG=, ONCLEAR="SETVAR area tunnel_open 1", RESPAWN=0|\<ticks>. CLEAR \<dir>, DIG \<dir> and attacking the exit work on it. Its state is kept in saves (bw-save-roomdelta).

**Example.** A collapsed dwarf-mine tunnel takes about five turns with a pickaxe and opens a shortcut. A vine-choked jungle path burns clear but grows back after 3 game days.

*See also:* W7, S17 (I13's cleared blockages and W7's room states are kept through S17's room-delta save).

<!-- from: behaviors-world/bw-blockedexit -->

<a id="i14"></a>

## I14. Prop_Detector: an item that reacts to what is near

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_Sounder reacts to actions, and Prop_Smell emotes only on mobs. Same-room detection can be scripted: a RAND_PROG or DELAY_PROG on the item with NUMRACES, NUMMOBSROOM, ISHERE or ROOMMOB plus ISLIKE, then MPECHOAT the owner; NUMRACESINAREA covers an area. MPSET $i AFFBEHAV can toggle an ambiance. Detection a set number of rooms away, and a clean ambiance on and off, are out of reach.

**Add.** Prop_Detector DETECT=\<mob mask, such as "-RACECAT +Orc +Goblinoid"> or DETECTITEM=\<item mask>, RANGE=0..3 rooms, AMBIANCE="(glowing blue)", MSG="$p glows faintly blue.", STOPMSG="$p's glow fades.", WHERE=WORN|HELD|ANY, EVERY=\<ticks>. It ticks on the item and holds the ambiance while something is detected.

**Example.** An elven blade glows when orcs are within two rooms. A dowsing rod twitches near buried treasure.

<!-- from: properties/pr-detector -->

<a id="i15"></a>

## I15. Receptacles: containers that react to the right objects

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_LimitedContents already filters a container by mask, +TAGS and +NAMES included, with a count and a refusal: '2 +TAGS dragoneye;0;MSG="It does not fit."' (its count bug is in the property-bug fixes). PUT_PROG on the container fires on put, and NUMITEMS or INCONTAINER tell when it is full. Prop_ItemSlot REMOVABLE=FALSE locks items in, but only Prop_ItemSlotFiller items, and nothing fires when slots fill. Plain containers cannot lock in or consume.

**Add.** Extend Prop_LimitedContents or Prop_ItemSlot rather than add a property: LOCKIN=true (cannot be taken out once placed), CONSUME=true, and ONPUT=, ONFULL="signal eyes full" and ONEMPTY= as actions or signals.

**Example.** The dragon statue's two eye sockets accept only items tagged dragoneye. With both in place, its mouth opens and shows the key.

*See also:* SC6, I9, I12 (later), I25 (The puzzle props send and receive SC6's signals (signal gate1 on, ONFULL=signal, SIGNAL inputs)); I1 (I15's receptacles rely on I1's Prop_LimitedContents count fix).

<!-- from: items-puzzles/ip-receptacle -->

<a id="i16"></a>

## I16. Prop_ComboLock: dials, codes and keypads

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_OpenCommand 'dial 7 3 9;press 739' already opens on an exact phrase, on both sides (Prop_OpenPassword does the same for a spoken code), and GenExit's RESETTIME relocks. Its first word must reach HUH, so ENTER and PUSH, which are commands, cannot start it, nor can TURN for a player who knows Turn Undead. A random code per game can be scripted (ONCE_PROG, MPSETVAR, RANDNUM) and printed by a clue item through $\<var>. Missing: dials set one at a time and shown on LOOK, symbol sets, and a tries limit.

**Add.** Prop_ComboLock on exits and containers, sharing the state of Prop_Switch (proposed in I9; it does not exist yet): CODE=7-3-9 or a word, DIALS=3, SYMBOLS=0-9 or "sun,moon,star", VERB=turn (TURN DIAL 2 TO 3), SET DIALS 7 3 9 or ENTER 739 for keypads, SHOW=true ('The dials read 7-1-4.'), TRIES=3 with ONWRONG="cast Spell_ShockingGrasp $n", RELOCK=30, ONOPEN=. CODE=RANDOM rolls a code per new game into a variable a clue item can print.

**Example.** The alchemist's chest has three symbol dials. A torn page in the library gives the order: 'sun before star, moon last'.

*Re-checked against the code after writing: corrected.*

*See also:* I9 (I16's combination lock shares I9's switch state).

<!-- from: items-puzzles/ip-combo-lock -->

<a id="i17"></a>

## I17. Riddler behaviour: riddles with several answers, tries and consequences

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A riddle is a SPEECH_PROG with a keyword list or an exact phrase, plus MPSETVAR counters for tries and a "solved" flag. MudChat matches patterns but counts no tries. Prop_OpenPassword takes one word and can only open. Tries with consequences, a random riddle, typo tolerance and once-only memory all need a script.

**Add.** A Riddler behaviour for a mob or item: RIDDLE="..." (several; RANDOM=true picks one), ANSWERS="mountain|a mountain|mountains" (case, articles and punctuation ignored; FUZZY=1 forgives a one-letter typo), ASK=greet|look|command, TRIES=3, ONRIGHT=, ONWRONG= and ONOUT= holding script commands, and ONCE=character|save. The player answers with SAY or ANSWER \<text>.

**Example.** The sphinx at the desert gate asks one of five riddles. A right answer runs "door open here north; exp 100" and it steps aside for good. After three wrong answers it attacks.

<!-- from: items-puzzles/ip-riddler -->

<a id="i18"></a>

## I18. Fix READ \<direction> so it reads writing on an exit

*Kind:* bug fix (player command) · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** READ finds the exit for a direction, then at once overwrites it with an item lookup on the last word (Read.execute, fetchFromMOBRoomFavorsItems). That lookup matches exits by name, door name or closed text, never by direction. So READ NORTH fails, although exits can carry readable text and handleBeingRead supports them. READ DOOR works, but only if the player guesses the name.

**Add.** Keep the exit when a direction was given, and search items only when it is null. This is a one-line fix. Also, LOOK \<direction> adds "There is writing on it." when the exit is readable, so the player knows to read it.

**Example.** A door carved with "Beware the Wyrm": LOOK NORTH mentions the writing, and READ NORTH shows the carving.

<!-- from: items-puzzles/ip-read-exit-bug -->

<a id="i19"></a>

## I19. Fix Prop_CloseCommand and let command phrases start with a real verb

*Kind:* bug fix (property) · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_CloseCommand cannot work. A missing else makes the word NOCLOSE also a command phrase. The exit branch closes only if(!E.isOpen()), that is, when the exit is already closed. The container branch sends MSG_OPEN, not MSG_CLOSE. setMiscText never resets noclose to false. Also, Prop_OpenCommand and Prop_CloseCommand react only to unknown input (TYP_HUH), so a phrase that starts with a real command or skill word, such as PUSH, PULL or ENTER, never fires.

**Add.** Fix the four lines to mirror Prop_OpenCommand, and add both properties to the smoke test. Then let both listen to COMMANDFAIL too, which PUSH sends when the object is missing. If not, their help should say the phrase must not start with a command.

**Example.** PUSH STONE seals the tomb's exit behind the player. This needs the COMMANDFAIL change as well as the fix.

*See also:* ST7, I9 (All three let an object's own verb (TURN, PULL, PUSH) win over stock commands and skills; one parser hook serves them).

<!-- from: items-puzzles/ip-closecommand-bugs -->

<a id="i20"></a>

## I20. GenTrap: choose what springs it, and run its script with no ability

*Kind:* property (and a bug fix) · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** What springs a trap is fixed by its class: entering a room, unlocking or opening an exit or container, or getting, pushing or pulling an item (holding, for bombs). A cursed throne (sit), a trapped book (read), a painting (look) or a chest that bites when closed must be a Scriptable instead: EXECMSG_PROG SIT or LOOK_PROG, MPCAST, and a variable for "disarmed". GenTrap's SCRIPT also runs only inside if(able.trim().length()>0), so a trap with a script and no ABILITY does nothing.

**Add.** A TRIGGERS field on GenTrap, e.g. TRIGGERS=SIT,READ,LOOK,CLOSE,WEAR,DROP,PUT,LEAVE:north. DISARM=skill|verb:"cut red wire" for puzzle-style disarming, plus RESET= and ONCE. Move the script call out of the ability branch so a script-only trap works.

**Example.** The Usurper's Throne: sitting on it fires Spell_Curse and a script with the old king's voice, unless the seal on the throne has been broken.

*See also:* C23 (C23's ambush notes that GenTrap runs its script only inside its ability branch, which I20 fixes); P3 (Both extend GenTrap scripting (TRIGGERS and DISARM vs ONTRIGGER and ONDISARM hooks)).

<!-- from: items-puzzles/ip-gentrap-triggers -->

<a id="i21"></a>

## I21. Richer keys and locks: master keys, keys used up, unpickable locks

*Kind:* property (and a change to key matching) · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A lock matches one key code exactly (getKey().equals(keyName())). A keyring counts only one container level deep, so not inside a pack. Scripts cover the rest: CNCLMSG_PROG on UNLOCK with HAS() and MPUNLOCK for a master key, UNLOCK_PROG with MPJUNK $o for a key used up, a very high lock level or a CNCLMSG_PROG for a lock no thief can pick. Prop_LocationBound already sends a key back to its room.

**Add.** The core: key codes as a list (crypt;vault) or a prefix (jail.*), and keyrings found at any depth. Then, for convenience, Prop_KeyRules on a key (CONSUME=true, BREAK=10 as a percent chance, RETURN=room id, MASK=) and Prop_LockRules on exits and containers (PICK=never|level:30, KEYS=a;b, RELOCK=20, MSG=).

**Example.** The jailer's master key, jail.*, opens all six cells, jail.1 to jail.6. The cursed bone key unlocks the ossuary once and crumbles.

<!-- from: items-puzzles/ip-keys -->

<a id="i22"></a>

## I22. MPTRANSFORM: turn an item into another in place

*Kind:* script command and JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Turning one item into another takes MPJUNK and MPOLOAD, which loses its worn slot, container, state and tags. MPPUT and MPFORCE wear win some of that back, clumsily. MPM2I2M only converts between mobs and items. MPSET can rewrite a generic item in place (NAME, DESCRIPTION, USES, DISPOSITION, TAGS, AFFBEHAV and more), so a dull gem can start to glow. It cannot change the class, say GenItem to GenLightSource, or swap in a catalog item.

**Add.** MPTRANSFORM \<item> \<catalog name|item name|fromfile ...> [KEEP=state,tags,uses,container,worn]. The item is replaced in the same place by a copy of the named item, keeping the chosen fields. The new item is $p for the rest of the script. JavaScript gets transform(item, name).

**Example.** Dipping "an unlit torch" into the brazier turns it into "a burning torch", still held in the same hand.

*See also:* I5, I27 (All three replace an item in place with another while keeping its state; share MPTRANSFORM's KEEP code).

<!-- from: items-puzzles/ip-mptransform -->

<a id="i23"></a>

## I23. Prop_Attunement: powers that wake only once the item is attuned

*Kind:* property and command · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Prop_ItemBinder binds an item, but nothing makes its powers wait for a deed. Most of it can be built: masks on the item's props (Prop_WearEnabler(Spell_Haste; MASK=-TATTOOS +ATTUNED_CLOAK)), the tattoo given by Prop_TattooAdder on a shrine or MPTATTOO in a script, and Prop_LimitedEquip 3;id='attuned' to cap equipped items. Missing: attuning by rest, a cap on attuned rather than equipped items, a way to unattune, and "requires attunement" in identify.

**Add.** Prop_Attunement, wrapping that pattern. PROPS="Prop_WearEnabler(Spell_Haste);Prop_WearAdjuster(dex+2)" stay inert until attuned. HOW=REST:10 (rest ten ticks holding it), or RITUAL="INROOM Shrine#3 & SAY attune", or ON=EQUIP. LIMIT=3 per character, BIND=TRUE, MSG=, MASK=. UNATTUNE \<item> frees a slot.

**Example.** The party finds four strong items, but each character can attune only three, so choosing is part of building the character. A druid's staff attunes only in its grove.

<!-- from: properties/pr-attune -->

<a id="i24"></a>

## I24. Prop_ItemGrowth: items that grow with use

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** No property counts kills, hits or uses on an item. Prop_Doppleganger follows only the owner's level, and Prop_PropSetter has no counter. A script can do it: DAMAGE_PROG on the item ($g is the damage) and EXECMSG_PROG DEATH or KILL_PROG catch hits and kills, MPSETVAR and MPSAVEVAR keep the count, and at each threshold MPSET $i NAME and MPSET $i AFFBEHAV swap in stronger props. There is no way to say it as data.

**Add.** Prop_ItemGrowth GAIN=KILL|HIT|USE|WORNTICK, XP="@x1" (the victim's level), KILLMASK="-RACECAT +Undead", STAGES="100:NAME='a humming blade' PROPS=Prop_WearAdjuster(dam+2);400:NAME='Dawnbringer' PROPS=...", MSG="$p shudders as it grows stronger!", BIND=CHARACTER. Progress is kept in the property's text, so saves keep it. Identify shows the stage and progress.

**Example.** A rusty sword from the tutorial becomes the hero's legendary blade after 100, 400 and 1000 undead kills, with a new name and powers at each stage.

*See also:* JS9 (Both make items that grow with kills: JS9's bloodblade example in JavaScript, I24 as a property).

<!-- from: properties/pr-growth -->

<a id="i25"></a>

## I25. Prop_PressurePlate: triggers on weight and presence

*Kind:* property and script functions · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Room traps fire only on entering. Prop_WeakBridge checks weight only on enter and leave, GenScale compares only when looked at, and Prop_ReqCapacity only refuses entry or drops over a cap. A room script can sum STAT(x WEIGHT) over ROOMITEM and ROOMMOB in a FOR loop, called from ENTRY_PROG, EXIT_PROG, DROP_PROG and GET_PROG. It is heavy and has no release delay. There is no ROOMWEIGHT or CONTENTWEIGHT function.

**Add.** Prop_PressurePlate on a room or a floor fixture: WEIGHT=>=120 (mobs there plus items on it), COUNT=>=2 (mobs), MASK= for what counts (-RACE +Golem, or items by tag), ON="signal plate1 down; door open here north", OFF=, DELAY=0 (ticks before release), MSGON=, MSGOFF=. It re-checks on enter, leave, drop, get, put and ride. Also the functions ROOMWEIGHT($r) and CONTENTWEIGHT($i).

**Example.** Lifting the gold idol off its plate springs the trap unless a bag of sand of equal weight is dropped on it. Or one character, sent there with AS, holds the gate open while another passes.

*See also:* SC6, I9, I12 (later), I15 (The puzzle props send and receive SC6's signals (signal gate1 on, ONFULL=signal, SIGNAL inputs)).

<!-- from: items-puzzles/ip-pressure-plate -->

<a id="i26"></a>

## I26. Durability options: wear rate, broken instead of destroyed, unbreakable items

*Kind:* world option and property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Wear chances are hard-coded: per damage type and material in StdArmor, and a flat 1% per hit in StdWeapon. At 0 uses an item is destroyed (StdArmor.executeMsg, MUDFight.postItemDamage), which can delete a unique item for good. To make one nearly unbreakable, set its uses outside 0..1000 (MPSET $i USES 9999). postItemDamage then destroys it outright on any item damage, such as Freeze Metal. Or use Prop_Artifact, which brings multiplayer side effects. A CNCLMSG_PROG cannot stop StdArmor's direct wear. Mending exists: ItemMender, MEND, the mending crafts.

**Add.** Ini options ITEMWEAR=0..200 (a percent multiplier, 0 for off) and ITEMBREAK=DESTROY|BREAK. With BREAK, an item at 0 is broken: unusable and without bonuses, but it can be mended. Prop_Durability MULT=50 UNBREAKABLE NOREPAIR BROKENNAME="a shattered %s" sets it per item. The inventory shows condition, such as (worn) or (broken).

**Example.** With ITEMWEAR=50 and ITEMBREAK=BREAK, the heirloom sword breaks in a hard fight and goes to the dwarven smith (ItemMender) instead of vanishing.

*Re-checked against the code after writing: corrected.*

*See also:* CR3 (Both let worn items break instead of vanishing (a tool's wear:n fate vs ITEMBREAK=BREAK)).

<!-- from: items-puzzles/ip-durability -->

<a id="i27"></a>

## I27. ItemUpgrader behaviour: upgrade paths at a smith or workbench

*Kind:* behaviour and property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** ItemMender, ItemRefitter and ItemIdentifier mend, refit and identify. Prop_ItemSlot adds sockets, and the Enhanced crafting expertise changes an item only as it is crafted. Nothing improves an existing item along a set path, such as +1 to +2 or a sword reforged into its awakened form. A GIVE_PROG or SPEECH_PROG on the smith can check HAS() and GOLDAMT, then MPJUNK and MPOLOAD, but that loses state and tags.

**Add.** An ItemUpgrader behaviour for a mob or a fixture such as an anvil. UPGRADES=world/upgrades.txt, or inline rows of FROM="name|tag|catalog", TO="catalog:Starfall" or ADJ="dam+2 att+5", COST=500, MATERIALS="starmetal ingot x3", LEVEL=20, KEEP=state,sockets,tags and MSG=. UPGRADE SWORD shows the cost, UPGRADE SWORD CONFIRM buys. Prop_UpgradePath can carry the path on the item. KEEP= shares its code with MPTRANSFORM.

**Example.** The dwarven smith turns "a notched blade", three starmetal ingots and 2000 gold into "Starfall, the reforged blade" from the catalog.

*See also:* I5, I22 (All three replace an item in place with another while keeping its state; share MPTRANSFORM's KEEP code).

<!-- from: items-puzzles/ip-upgrader -->
