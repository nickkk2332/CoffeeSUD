# World systems (time, weather, environment, economy, law)

Calendar and clock, weather and climate, environmental hazards, darkness, tides, resets, shops, money and currency, and the legal system. Part of the [builder capabilities audit](README.md).

<a id="w1"></a>

## W1. Warrants and fines in scripts and masks

*Kind:* script function, script command · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Scripts can accuse with MPACCUSE, and Prop_ReqEntry or Prop_HaveZapper can accuse as their zap event. The only way to pardon is MPCAST Prayer_Forgive $n from a mob in the legal area. It clears every warrant at once and shows prayer messages. A quest can suppress a law with SET AREA and GIVE STAT SUPPRESSLAW while it runs. Nothing can ask whether the player is wanted, for what, or what they owe. Masks have +OFFICER and +JUDGE but no key for wanted.

**Add.** Functions ISWANTED(who [crime]), WARRANTS(who) (a count) and FINESOWED(who), read in the legal area of the scripted object. Commands MPPARDON who [crime|ALL], quiet and per crime; MPFINE who +/-amount; MPRELEASE who; MPSUPPRESSLAW crime|ALL \<duration>. Zapper keys +WANTED and -WANTED [crime], so a shop's IGNOREMASK, a gate guard or \<VARIES> text can react to criminals.

**Example.** A corrupt magistrate: BRIBE_PROG 500 runs MPPARDON $n ALL if ISWANTED($n). An honest shop turns away anyone wanted.

*See also:* JS6, W4, W6, W12, W14, W15, W27 (later) (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them); W26 (later), PR1 (W1's +WANTED mask is what W26 (later)'s bounty hunters need and what PR1's \<WANTED> tag tests); DT21 (Both add pardoning and accusing per crime (MPPARDON and MPFINE vs LAW PARDON and LAW ACCUSE)).

<!-- from: world-systems/ws-law-script -->

<a id="w2"></a>

## W2. Currency file and a world default currency

*Kind:* data file, world option · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** An area's CURRENCY can be a full definition or just a code. Child areas inherit it, so a parent area can serve as a world default. A code resolves from what areas define at load, or from the built-in GOLD, COPPER, CREDIT, DOLLAR and VICTORY. An area with no currency anywhere in its parent chain gets the hard-coded gold coins and Archon notes. When a code is defined twice, the first definition is kept without a warning. VALUE() knows only the sets areas made, so VALUE($n GOLD > 5) fails for the built-in ones.

**Add.** A currency file loaded at boot: vocab/currencies.ini, or CURRENCY_\<CODE>= lines in the world ini. Lines such as DEFAULT=CROWN, CROWN=1 copper penny(s) (cp);12 silver shilling(s) (ss);240 gold crown(s) (gc) and TRADEBAR=notrade;1 trade bar(s) (tb). DEFAULT replaces the hard-coded set. A code defined twice with different denominations is logged. VALUE() accepts every registered code.

**Example.** The kingdom uses CROWN and the elven wood uses LEAF. An area with no currency of its own falls back to CROWN, not Archon notes.

*See also:* W16 (W16's coin braces extend the denomination lines of W2's currency file).

<!-- from: vocabulary-languages/vl-currency-registry -->

<a id="w3"></a>

## W3. Typed hazard zones: Prop_Hazard

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** FieryRoom does fire only. Prop_InstantDeath with DAMAGE= and a MASK (which can test -WEATHER, -WORN, -EFFECTS and +IF) hurts occupants on entry and each tick, but its damage is untyped, so saves and resistances do not count. It has no formula. It stops once nobody matches and does not start again until the next entry. On an area it does nothing. Anything more takes a RAND_PROG with MPDAMAGE in every room.

**Add.** Prop_Hazard, on a room, or on an area with a room filter such as -ALL +MOUNTAINS. TYPE=COLD|HEAT|ACID|GAS|POISON|ELECTRIC|HOLY|UNHOLY|DISEASE|THINAIR. DAMAGE= takes a formula (@x1 = level) and is dealt as that type, so saves and Prop_Resistance apply. Also SAVE=, DRAIN=MOVE|MANA, AFFECT="Disease_FrostBite 20%", PROTECT= (a mask on the victim), WHEN= (a mask checked every tick), EVERY=, ONSET=, ESCALATE=, DARKONLY, LITONLY, NONPC, WARN= (shown once) and MSG= lines. It can start from Prop_InstantDeath, with its area bug fixed.

**Example.** Frostfang Pass: TYPE=COLD DAMAGE=1d6 SAVE=COLD WHEN="-WEATHER +BLIZZARD +SNOW". Without a fur cloak or Resist Cold, the crossing in winter hurts more each tick.

*See also:* W10 (W10's +ROOMDARK mask is meant as a WHEN condition in W3's Prop_Hazard, next to its DARKONLY); W25 (later) (Both hurt characters by cold and heat from where they are (a typed hazard zone vs a body-temperature model)).

<!-- from: behaviors-world/bw-hazard, properties/pr-hazard -->

<a id="w4"></a>

## W4. Letting time pass: long WAIT, WAIT UNTIL, MPPASSTIME

*Kind:* script command, builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** WAIT runs real simulated ticks and stops when the player is disturbed. It is capped at 100 ticks, which is less than one mud hour, and it works only while the clock is stopped. TICKTOCK moves the calendar without running the ticks in between, so nothing heals, restocks or respawns, and TIME_PROGs are skipped. No script can make time pass. GameTime.requestAdvance, which WAIT uses, already takes a stop condition.

**Add.** WAIT n TICKS|HOURS|DAYS|WEEKS and WAIT UNTIL DAWN|DAY|DUSK|NIGHT|HOUR h|DAY d, with a progress line each game hour. In real time the clock pauses for the wait. Builder mode adds WAIT UNTIL VAR host name op value and WAIT UNTIL QUEST name STEP n|DONE. The owner chooses whether long waits are for players too, or only for builders. Script command MPPASSTIME \<n HOURS|n DAYS|UNTIL DAWN|UNTIL HOUR n> [INTERRUPTIBLE] [QUIET]. Optional Prop_InnRoom: a wait in this room gives full rest.

**Example.** A ferryman's BUY_PROG moves the player across with MPTRANSFER, then runs MPPASSTIME 4 HOURS. A builder types WAIT UNTIL HOUR 20 to watch the night market open.

**Also merged here, from T1 (WAIT UNTIL arrival, and a script command to end a wait).** `WAIT UNTIL ARRIVAL|STOP|DOCK`, which ends when the vessel or rideable you are on stops or reaches a port, and `WAIT UNTIL DAWN|DUSK|MORNING|HOUR <n>`. These forms get a larger cap, say 3 game days, and keep the usual hurt and attack checks. A script command `MPINTERRUPT <who> [message]` ends any multi-tick wait or journey for that character and the party. JavaScript gets the same call. `GameTime.requestAdvance` already takes an "until" test, so this is cheap.

*See also:* JS6, W1, W6, W12, W14, W15, W27 (later) (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them); ST8 (ST8's DOCK_PROG and T1 (now in W4)'s WAIT UNTIL DOCK both need an event when a vessel stops or anchors in a port).

<!-- from: world-systems/ws-cmd-passtime, sp-dev-tooling/spdev-wait-until, vehicles-travel-instances/vti-wait-until -->

<a id="w5"></a>

## W5. Area calendars and the start date as content

*Kind:* world option, data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The global calendar is already content: coffeemud.ini sets HOURSINDAY, DAYSINMONTH, MONTHSINYEAR, DAYSINWEEK, DAWNHR to NIGHTHR, and YEARDESC. A StdTimeZone area's own calendar is saved only in the TIMECLOCK record in CMPDAT. That is a save table: WorldFiles never writes it and a new game does not copy it. So in every real game that area reverts to the global calendar. Every clock starts at hour -1, day 1, month 1, year 1000. A date set in builder mode with MODIFY YEAR or TICK SET is lost the same way.

**Add.** An area stat CALENDAR, kept in the area's ADATA in the .cmare and edited on the MODIFY AREA calendar screen. It holds HOURS, DAYS, MONTHS, WEEK, YEARS, DAWNHR, DAYHR, DUSKHR, NIGHTHR and START=Y/M/D/H. Left empty, it inherits from the parent area or the global clock. STARTDATE=Y/M/D/H in coffeemud.ini sets the global start. TIMECLOCK then holds only the current date, as save state.

**Example.** An elven realm has 10-hour days and months named Leafwake and Bloomtide. Every new game opens there at dawn on 3 Bloomtide 812.

*See also:* W22 (W22's seasons, day length and moons are calendar fields for W5's per-area CALENDAR stat).

<!-- from: world-systems/ws-calendar-content -->

<a id="w6"></a>

## W6. Holidays: more recurrences and tests by name

*Kind:* quest feature, script function · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** CREATE, MODIFY, LIST and DESTROY HOLIDAY edit resources/quests/holidays/holidays.quest, which is content. A holiday is a quest without a script. It has an area or room mask (which can test -SEASON and -WEATHER), MUDDAY m-d, a real-life DATE or WAIT/INTERVAL, and a DURATION. It sets behaviours, properties and stats such as PRICEMASKS for that time. CALENDAR lists the dated holidays. A holiday cannot recur by weekday, interval, season or moon. Nothing tests a holiday by name: QVAR(\<quest> REMAINING > 0) is the nearest.

**Add.** New recurrences in the holiday system: WEEKDAY=name, EVERY=n DAYS FROM y/m/d, SEASONSTART=WINTER and MOON=FULL. DATE follows the game calendar, and DURATION takes days or hours. Tests by name: ISHOLIDAY(name), HOLIDAY() (the current names), zapper +HOLIDAY, VARIES \<HOLIDAY name>, and quest SET HOLIDAY name, which makes a quest run exactly while the holiday is on. CALENDAR shows game dates ("Moonfeast in 4 days").

**Example.** Moonfeast falls on every full moon in Riverton. A quest with SET HOLIDAY Moonfeast suppresses the drunkenness law and sets PRICEMASKS to 0.9. Room text mentions the lanterns.

*See also:* JS6, W1, W4, W12, W14, W15, W27 (later) (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them); W23 (Both tie a quest to a holiday (SET HOLIDAY vs SET SCHEDULE HOLIDAY)); PR1 (Both add a VARIES \<HOLIDAY name> tag); F21 (later) (F21 (later)'s deity holy days are named holidays, tested with W6's tests by name).

<!-- from: world-systems/ws-holidays -->

<a id="w7"></a>

## W7. RoomStates: named versions of a room

*Kind:* behaviour, script command · *Value:* high · *Effort:* large · *Decision:* yes

**Today.** A script can switch a room between states. Each state is a FUNCTION_PROG called with MPCALLFUNC. It runs MPSET DISPLAY/DESCRIPTION/ATMOSPHERE/CLIMATE, MPAFFECT Prop_RoomDark, MPBEHAVE WaterCurrents, MPUNLINK, MPCLOSE and MPSETVAR, and VARIES text follows the state through '+IF VAR(...)'. Prop_ClosedSeason and Prop_ClosedDayNight darken rooms and close exits by time or season. But MPSET CLASS does nothing, rooms cannot switch together, and the states are not declared in one place.

**Add.** Behaviour RoomStates. A state looks like STATE=flooded TITLE= DESC= ATMOSPHERE= CLIMATE= CLASS=IndoorShallowWater ADD="Prop_RoomDark();WaterCurrents(go=east)" BLOCK=north,down OPEN=up ENTERMSG=. Transitions: TO=flooded WHEN="+WEATHER +RAIN" AFTER=20; TO=dry TICKS=300; TO=collapsed ONVAR="mine_collapse==1". On an area it switches several rooms together. Script command MPROOMSTATE \<room|area> \<state>, and function ROOMSTATE(room), which VARIES masks can also test. The save keeps the state, through the room-delta save item (bw-save-roomdelta).

**Example.** Deepdelve mine shaft: a trap makes it 'collapsed', with the way down blocked, darkness and dust. MPROOMSTATE from the digging script makes it 'cleared', with shored-up beams.

*See also:* ST25 (W7's ONVAR transitions (and W19's) need the variable-change notice that ST25 adds to setVar); I13, S17 (I13's cleared blockages and W7's room states are kept through S17's room-delta save); W8, W19 (W19's Floodable and W8's TidalRoom are presets of W7's RoomStates).

<!-- from: behaviors-world/bw-roomstates -->

<a id="w8"></a>

## W8. Tides for builders

*Kind:* script function, property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Each area's clock works out the tide, but only Mer, Pirate and the druid tide chants read getTidePhase. Scripts, masks and VARIES cannot. The tide model is thin. It is LOW only in the hour the day starts and HIGH only in the dusk hour, with its strength set by the moon, and NO_TIDE the rest of the time. A builder can match it today with a TIME_PROG at those hours plus ISMOON, and close a causeway door with Prop_ClosedDayNight HOURS=.

**Add.** Function ISTIDE(room, HIGH|LOW), mask +TIDE HIGH|LOW, and VARIES tags \<HIGHTIDE> and \<LOWTIDE>. Prop_TidalExit(OPEN=LOW MSG="The causeway is under water.") makes an exit passable only at low tide. TidalRoom, or a RoomStates preset, floods a shore room at high tide: swimming, a water atmosphere at the deepest, light items washed away. RISING and EBBING, or a longer flood window, would first need a new tide model.

**Example.** The causeway to St. Brine's Isle is open only at low tide. At high tide the description changes, and the player can be cut off on the island until the next ebb.

*See also:* W7, W19 (W19's Floodable and W8's TidalRoom are presets of W7's RoomStates).

<!-- from: behaviors-world/bw-tides -->

<a id="w9"></a>

## W9. ResetWhole: DAYS, EXCEPT and MSG

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** ResetWhole's TICKS= already runs on the game clock and counts from the last player activity in the area, so it already means n ticks after the party left. MUDHOUR= uses the area's calendar. RLHOUR reads the real clock, which means nothing with pause and turn-based time. A reset reloads whole rooms, so a slain boss comes back unless his ONCE_PROG purges him when a variable is set. A message after a reset needs an area GREET_PROG.

**Add.** New ResetWhole parameters. DAYS=n, in game days on the area's calendar, as a shorthand for TICKS. EXCEPT=mask, for mobs or items that never respawn, such as bosses and unique loot. MSG="...", shown on the next entry after a reset. RLHOUR is deprecated in single-player.

**Example.** The Goblin Warrens refill 7 game days after the party leaves, except the Goblin King (EXCEPT="-NAME +'Goblin King'"). On the way back in, the player reads "Fresh tracks lead into the tunnels."

*Note.* Leave unique items to `DEVPLAN.md` item 1, whose copy counting and once-ever rule stop a unique item from coming back; keep `EXCEPT=` for bosses and the like.

*Re-checked against the code after writing: accurate.*

*See also:* C19 (Both keep a slain boss from coming back (Prop_RejuvWhen vs ResetWhole's EXCEPT=)); T2 (Both add ResetWhole parameters (DAYS vs MINAWAY, MSG vs RESETMSG) for how an area resets after the party leaves).

<!-- from: behaviors-world/bw-resetwhole-sp -->

<a id="w10"></a>

## W10. Darkness tests, and dangers in the dark

*Kind:* script function, property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A room's darkness is its IS_DARK disposition. Night, blizzards, dust storms, Prop_RoomDark and Prop_ClosedSeason set it, and any light in the room clears it. A room script can test it with ISLIKE($d '-DISPOSITION +ISDARK'), and so can a room Emoter or a quest room mask. A mask checked against the player cannot see it, because StdRoom filters IS_DARK out of what reaches occupants. Losing your way in the dark exists only as the Chant_Labyrinth spell.

**Add.** Functions ISDARK(room) and ISLIT(room). Mask keys +ROOMDARK and -ROOMDARK, seen from the player's side, so they also work as WHEN conditions in Prop_Hazard and similar properties. Property Prop_DarkDanger(LOST=10% STUMBLE=5% LURKER=catalog:"ghoul" 3% MSG=...), which acts only while the room is dark to that player.

**Example.** The catacombs: without a lit torch, each move has a 10% chance of going a random way ("You lose your way in the dark"). There is also a 3% chance that a ghoul comes out of the dark.

*See also:* W3 (W10's +ROOMDARK mask is meant as a WHEN condition in W3's Prop_Hazard, next to its DARKONLY).

<!-- from: behaviors-world/bw-darkness -->

<a id="w11"></a>

## W11. Darkness that torches cannot beat

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Prop_RoomDark sets IS_DARK on the room (with HOURS=), but any carried torch clears it, because inhabitants are applied after room effects. Spell_DarknessGlobe does beat lights. It strips IS_LIGHTSOURCE from every inhabitant and forces the room dark on each tick and each entry. But it is a spell on a mob. The workaround is a hidden room mob with a permanent globe, and lights flicker back between its ticks.

**Add.** New options on Prop_RoomDark, for rooms and areas. ABSOLUTE=TRUE applies the darkness again after inhabitants and items, and turns off their IS_LIGHTSOURCE while inside, reusing the DarknessGlobe code. LIGHTMASK="+NAMES -sunrod -'everburning lantern'" names the lights that still work. SEEMASK= says who still sees, such as darkvision races. SNUFF=30 is the chance to put out a lit torch on entry, with a message.

**Example.** In the Shadowfell crypt, torches go out on entry. Only the Lantern of Dawn from the temple quest lights the way.

<!-- from: properties/pr-deepdark -->

<a id="w12"></a>

## W12. MPWEATHER, and WEATHER SET, FORECAST and NEXT

*Kind:* script command, builder tool · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** 'MPAFFECT Prop_Weather $a 300 TICKS THUNDERSTORM' pins an area's current and next weather for a time, but silently. Weather chants such as Chant_SummonRain or Prayer_Weather, cast through MPAFFECT, change the next weather with the usual messages. They cannot pick an exact state or a duration. WEATHER WORLD lists each area's current weather. Nothing shows the odds of the next.

**Add.** MPWEATHER \<area|$a> \<state> [NEXT] [FOR n HOURS|DAYS] [QUIET]. It sets the current weather, or with NEXT only the next one. It sends the normal end and start messages to everyone under the sky, holds the weather for the time given, then returns to the normal chain. Builder commands: WEATHER SET \<state> [FOR ...]; WEATHER FORECAST, which shows the current and next weather and the weighted chances of the step after; WEATHER NEXT, which forces one weather step.

**Example.** A step of the necromancer quest runs MPWEATHER $a THUNDERSTORM FOR 2 HOURS. A builder runs WEATHER FORECAST after changing an area's climate.

*See also:* JS6, W1, W4, W6, W14, W15, W27 (later) (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them); W20, DT20 (W12's MPWEATHER and WEATHER SET and FORECAST are repeated in W20 (MPWEATHER, FORECAST) and DT20 (WEATHER SET); build one set of weather commands).

<!-- from: world-systems/ws-cmd-weather -->

<a id="w13"></a>

## W13. Sky and weather lines per area: Prop_SkyText

*Kind:* property, presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The dawn, day, dusk and night lines and the weather texts come from global lists in resources/lists.ini (TOD_CHANGE_OUTSIDE, TOD_CHANGE_INSIDE, WEATHER_*). A cave city and a desert share them. Lines 669 and 671 have the sun rising in the west and setting in the east. A room's description can follow time and weather with \<VARIES>. For lines per area, DISABLE=TODNOTIFIES or WEATHERNOTIFIES turns off the stock ones, and then every area needs a TIME_PROG or RAND_PROG with MPECHO.

**Add.** Prop_SkyText on an area or room. DAWN=, DAY=, DUSK= and NIGHT= are for outside, and INDAWN=, INNIGHT= and so on for indoors. WEATHER_\<STATE>= gives that weather's start, stop and description lines, and MOON= the moon's. Alternatives separated by | are picked at random. NONE silences a line. Anything unset falls back to lists.ini. Separately, a data-only fix to lines 669 and 671.

**Example.** Ashen Wastes: DAWN="The red sun drags itself over the ash dunes.", and snow starts with "Grey ash begins to fall." The Underdark: DAWN=NONE, NIGHT="The glowcaps dim as night falls on the surface far above."

*See also:* W20, L3 (Three ways to give an area its own time-of-day and weather lines (Prop_SkyText, climate files, Prop_LocalLists); pick one).

<!-- from: world-systems/ws-prop-skytext, presentation/pr-area-clock-text -->

<a id="w14"></a>

## W14. MPSHOP: remove, restock and reprice shop stock

*Kind:* script command, script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Scripts can read a shop with SHOPITEM, NUMITEMSSHOP and SHOPHAS (which sets $9 to the price). They can add stock with MPOLOADSHOP (with PRICE=n) and MPMLOADSHOP. MPSET changes a GenShopkeeper's rules: PRICEMASKS, BUDGET, DEVALRATE, INVRESETRATE, IGNOREMASK and WHATISELL. No script or quest can remove stock, set a quantity, reprice a stocked item, reset the inventory or set the budget that is left. Only the RESET command clears a store.

**Add.** MPSHOP \<shopkeeper> with one of REMOVE \<item> [n|ALL], STOCK \<item> \<n>, PRICE \<item> \<amount|-1>, CLEAR, RESET (reload the built stock) or BUDGET \<amount> (the budget left). Functions SHOPPRICE(\<shopkeeper> \<item> [\<customer>]), the price that customer would pay, and SHOPBUDGET(\<shopkeeper>).

**Example.** While the mine is collapsed, the blacksmith's script runs MPSHOP $i REMOVE "iron ingot" ALL. A fence offers a stolen ring back at its SHOPPRICE plus half.

*See also:* JS6, W1, W4, W6, W12, W15, W27 (later) (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them); W17 (W14's SHOPPRICE and W17's PRICE both return an item's price at a shop for a customer).

<!-- from: world-systems/ws-shop-script -->

<a id="w15"></a>

## W15. Bank accounts and debts in scripts

*Kind:* script function, script command · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** The money library keeps bank balances per bank chain, and debts with interest and due dates, in CMPDAT, which a save holds. Scripts see only the coins a character carries (GOLDAMT, MPMONEY). The workarounds are to MPFORCE the player to DEPOSIT in front of a banker, or LIST BANKS, which only shows them. A reward cannot be paid into an account, and a quest cannot create or check a debt.

**Add.** Functions BANKBALANCE(who chain [currency]) and DEBT(who owedTo). Commands MPBANK who chain +/-amount [reason], which writes a ledger line. MPDEBT who owedTo +/-amount [INTEREST pct] [DUE n DAYS], and MPDEBT who owedTo CLEAR. The data already lives in the save, so changes made by scripts persist.

**Example.** The guild pays bounties straight into Riverton Trust. A moneylender's quest sends thugs once DEBT($n Vargo) is past due.

*See also:* JS6, W1, W4, W6, W12, W14, W27 (later) (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them).

<!-- from: world-systems/ws-bank-script -->

<a id="w16"></a>

## W16. Coin descriptions, materials and weights

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A coin's material follows from its denomination's name. Note, bill and dollar give PAPER. Otherwise it is the first material word in the name, so a silver shilling is SILVER and a glass moon bead is GLASS. Its description is always "Equal to N \<lowest coin>." There is no weight per denomination, so invented coins all read alike, and a material other than the one in the name cannot be set.

**Add.** Optional braces after a denomination in a currency definition, for example 12 silver shilling(s) (ss) {MATERIAL=SILVER WEIGHT=0.01 DESC="A thin disc stamped with the crowned stag."}. StdCoins uses them for the material, weight and description. The "Equal to" line comes after DESC.

**Example.** Dwarven iron hacksilver is heavy, a paper guild scrip has a watermark in its description, and moon beads are made of GLASS though their name does not say so.

*See also:* W2 (W16's coin braces extend the denomination lines of W2's currency file).

<!-- from: vocabulary-languages/vl-coin-flavour -->

<a id="w17"></a>

## W17. Money formatting for scripts

*Kind:* script function · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Scripts see money only as numbers. `GOLDAMT` and `VALUE` return amounts, `CURRENCY` returns a code, and only `SHOPHAS` fills `$9` with a formatted price. To quote a price in coin names, a script must embed JavaScript that calls `Packages.com.planet_ink.coffee_mud.core.CMLib.beanCounter().nameCurrencyLong`. That needs `JSCRIPTS` approval and some Java.

**Add.** Three MOBPROG functions, also reachable from JavaScript as `name$`. `MONEYSTR(amount [CODE] [LONG|SHORT])` calls `nameCurrencyLong` or `nameCurrencyShort`. `PRICE($i [shopkeeper])` gives the item's price at that shop, formatted. `DENOMNAME(CODE value [count])` gives the name of a denomination.

**Example.** A ferryman's `SPEECH_PROG` runs `say The crossing is $%MONEYSTR(36 CROWN LONG)%, friend.` It prints "3 silver shillings".

*Note.* `JSCRIPTS=2` already runs every JavaScript block without approval (see *Already possible* in the README), so the JavaScript route mentioned above needs no approval; what this item adds is the convenience of doing it without JavaScript.

*See also:* W14 (W14's SHOPPRICE and W17's PRICE both return an item's price at a shop for a customer).

<!-- from: vocabulary-languages/vl-money-functions -->

<a id="w18"></a>

## W18. A breath count before drowning

*Kind:* world option, property, script function · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Drowning and choking damage start on the first tick a mob cannot breathe the room's atmosphere. Holding breath exists only as skills: `Skill_DeepBreath` and `Skill_LongBreath` (sailors, LizardMan, Otter). A builder can lend them with `Prop_WearEnabler` or `Prop_HaveEnabler`, and `Prop_WearSpellCast` can give `Spell_WaterBreathing`. Anyone else has no breath to hold and gets no warning.

**Add.** A breath count for everyone, set by an ini formula such as `BREATHTICKS=@x1/3` (`@x1` is constitution). It warns at half and at a quarter, and damage starts only when it runs out. `Prop_AirSupply(TICKS=n)` on items adds ticks. As with the skills, any room with breathable air refills the count, so an air pocket needs nothing new. Add a script function `BREATH($n)` and a mask `+BREATH <n`.

**Example.** In the Sunken Temple the player has about 6 ticks of air to reach the air pocket in room 4. A merfolk NPC sells air bladders.

*See also:* W19 (W19's full flood drowns through W18's breath count).

<!-- from: behaviors-world/bw-breath -->

<a id="w19"></a>

## W19. Floodable rooms driven by weather, tide or a variable

*Kind:* behaviour · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Flooding exists in two forms only. `Trap_FloodRoom` is sprung once: it blocks exits, makes occupants swim, then drains. `Chant_Flood` is a spell with a duration. Scripts can already build a flood by hand. `MPSET $i ATMOSPHERE WATER` makes the room's air water, so occupants drown (the resource is named WATER; FRESHWATER is ignored). `MPAFFECT property:Chant_Flood $i` makes it a swimming room until `MPUNAFFECT`. `MPBEHAVE WaterCurrents $i <dir>` sweeps loose items, and players, out through an exit. A variable can hold the level, and `ISWEATHER` tests for rain. What is missing is one behaviour that ties these into levels that rise and drain on their own. WaterCurrents also takes every loose item, not just small ones.

**Add.** A `Floodable` room behaviour, built as a preset of RoomStates, with levels 0 to 3: dry; ankle-deep (slower, slips); waist-deep (swimming, small items float off to `FLOWTO=dir`); full (water atmosphere, so drowning; see the breath item). The water rises one level per `RATE` ticks while its condition holds, and drains (`DRAIN`) when it does not. The condition is `RISE="+WEATHER +RAIN +THUNDERSTORM"`, `ONVAR="sluice==open"` or `TIDE=HIGH`. Rising water puts out lights and fires, and `VARIES` text can test `FLOODLEVEL()`.

**Example.** The old city storm drains rise a level every 15 ticks of rain. The player must turn the pump-room wheel, which sets `sluice=open`, before the lower tunnel fills.

*Re-checked against the code after writing: corrected.*

*See also:* W7, W8 (W19's Floodable and W8's TidalRoom are presets of W7's RoomStates); W18 (W19's full flood drowns through W18's breath count).

<!-- from: behaviors-world/bw-flooding -->

<a id="w20"></a>

## W20. Climates and weather texts as data, chosen per area

*Kind:* data file, world option, script command · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Weather odds are static arrays in `DefaultClimate`, which every area uses. An area saves only five climate bits (wet, cold, windy, hot, dry). `Prop_Weather` pins one weather. `LinkedWeather` copies another area's weather or rolls it in, so a hidden template area can act as a climate. `CLIMATE_VOID` is always CLEAR. The weather texts in `resources/lists.ini` apply to the whole world.

**Add.** Named climates in `world/climates/<name>.ini`. Each gives a weight per season for each of the 14 weather states, `TRANSITION` overrides (`RAIN->FOG=30`), a minimum `HOLD` in hours, an optional allowed list, and texts per weather that override `lists.ini`. An area picks one with `WEATHERPROFILE=<name>` in `MODIFY AREA`. Child areas inherit it, and the climate bits still apply as modifiers. `MPWEATHER <area> <weather> [NEXT|NOW]` makes one change without pinning it. A `WEATHER FORECAST` could show the odds.

**Example.** Saltmarsh Coast uses "monsoon": heavy summer rain that reads "Warm monsoon rain drums on the reeds". The Underdark allows only FOG and CLEAR.

*See also:* W12, DT20 (W12's MPWEATHER and WEATHER SET and FORECAST are repeated in W20 (MPWEATHER, FORECAST) and DT20 (WEATHER SET); build one set of weather commands); W13, L3 (Three ways to give an area its own time-of-day and weather lines (Prop_SkyText, climate files, Prop_LocalLists); pick one).

<!-- from: behaviors-world/bw-climates, world-systems/ws-weather-profiles -->

<a id="w21"></a>

## W21. Law book edits in builder mode saved to the world files

*Kind:* builder tool, data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Per-area laws already work as content. `Arrest(laws_riverton.ini;SALESTAX=5 OFFICERS="cityguard")` names a law file in `resources/` and merges the overrides over it. The behaviour is kept in the area's `BEHAVES` in the `.cmare`. The gap is the law named `custom` and the `StdLawBook`/`GenLawBook` editor. They write the law text to CMPDAT under `AREA/ARREST`, which is save state, so laws edited in builder mode do not reach a new game.

**Add.** In builder mode, law book edits (`updateLaw`) are written back as content. They go either as `KEY=VALUE` overrides in the `Arrest` parameters or as a law file (an area stat `LAWTEXT`, or `world/laws/<Area>.ini` from WorldFiles) that `Arrest` loads for `custom`. In a normal game, law changes, such as those after a conquest, stay in the save as now. Builders' documentation should also describe the `file;KEY=VALUE` route.

**Example.** In builder mode the builder bans nightshade and sets `SALESTAX=5` in the Riverton courthouse law book. The change lands in `world/areas/riverton.cmare`, so every new game has it.

*See also:* DT21 (DT21's LAW SET writes the custom law that W21 saves as world content).

<!-- from: world-systems/ws-law-content -->

<a id="w22"></a>

## W22. Custom seasons, seasonal day length and named moons

*Kind:* world option · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Seasons are the fixed `TimeClock.Season` enum. Each season is floor(months/4) months long, WINTER comes first, and leftover months go to FALL. Day length is one `dawnToDusk` array for the whole year. There is one moon, whose phase comes from the day of the month; `DOMAIN_MOONALTERING` effects only shift it. `ISSEASON`, `+SEASON`, `VARIES` and `Prop_ClosedSeason` know only the four names.

**Add.** Calendar fields:
- `SEASONS=Frostfall:WINTER:0-2,Thaw:SPRING:3,...` names each season, the season it counts as for weather, and its months.
- `SEASONOFFSET=n` shifts the seasons in one area, for a southern land.
- `DAYLIGHT=WINTER:2-4,SUMMER:0-5` sets dawn and night hours per season.
- `MOONS=Selune:24,Ember:9:3` gives each moon a name, a cycle in days and an offset.

`ISSEASON`, `VARIES`, `+SEASON` and `TIME` accept the custom names, and `ISMOON` takes an optional moon name.

**Example.** A desert world has two seasons, "the Burn" and "the Rains", and a red second moon, Ember. A `TIME_PROG` that checks `ismoon(Ember full)` starts a cult's ritual.

*See also:* W5 (W22's seasons, day length and moons are calendar fields for W5's per-area CALENDAR stat).

<!-- from: world-systems/ws-seasons-moons -->

<a id="w23"></a>

## W23. Game-time schedules for quests

*Kind:* quest feature · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Quest `SET WAIT`, `INTERVAL` and `DURATION` already take game time (`MUDHOURS`, `MUDDAYS`, `MUDWEEKS`, ...), and `MUDDAY m-d` gives a yearly date. Room masks with `-SEASON` or `-WEATHER` limit where an event happens. A scheduler mob can run `MPSTARTQUEST` from a `TIME_PROG`, or from a `RAND_PROG` that tests `ISSEASON(WINTER)`. CRON runs by the real clock and is stored as state, not content. What is missing is a declared window (weekday, hours, season, moon, weather, holiday) and an end at dusk.

**Add.** A quest keyword `SET SCHEDULE "<rule>"`. Rules: `HOUR a-b`, `WEEKDAY name`, `EVERY n DAYS [AT DAWN|HOUR h]`, `SEASON name`, `MONTH name`, `MOON phase`, `HOLIDAY name`, or `WEATHER state` in the quest area. `SET DURATION UNTIL DUSK|HOUR h|END` ends the quest when its window closes. The quest engine checks the rule each game hour.

**Example.** A weekly fair uses `SET SCHEDULE "WEEKDAY Marketday"` and `SET DURATION UNTIL DUSK`, and spawns stalls and traders in the square. A winter wolf-pack quest uses `SET SCHEDULE "SEASON WINTER"`.

*See also:* W6 (Both tie a quest to a holiday (SET HOLIDAY vs SET SCHEDULE HOLIDAY)).

<!-- from: world-systems/ws-quest-schedule -->

<a id="w24"></a>

## W24. Gradual and conditional restocking

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#w24).

<a id="w25"></a>

## W25. Exposure: body temperature from climate, shelter and clothing

*Kind:* behaviour, property, script function · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#w25).

<a id="w26"></a>

## W26. Bounty hunters and wanted posters

*Kind:* behaviour, property · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#w26).

<a id="w27"></a>

## W27. Market: supply and demand that remembers and recovers

*Kind:* behaviour, script command, script function · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#w27).

<a id="w28"></a>

## W28. Prop_TimeMode: zones locked to turn-based or real time

*Kind:* property · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** `TIMEMODE` is the player's own switch between turn-based and real-time play. A zone can switch it by script. A `GREET_PROG` or `ENTRY_PROG` runs `mpforce $n timemode turns`, and an `EXIT_PROG` runs `mpforce $n timemode realtime`. But no script function reads the current mode, so the player's own choice cannot be restored on leaving. The player can also type `TIMEMODE` inside the zone.

**Add.** `Prop_TimeMode` for rooms and areas, with `MODE=TURNS|REALTIME`, `ONLYCOMBAT=TRUE`, `RESTORE=TRUE` and `MSG="Time seems to slow..."`. On entry it switches GameTime as `TIMEMODE` does and remembers the previous mode. On leaving it restores that mode. Inside the zone, `TIMEMODE` says the mode is locked.

**Example.** The flooding tomb runs in real time, with the water rising every few seconds. The chess-puzzle hall is always turn-based.

*See also:* ST6 (ST6's ISTURNBASED() is the missing time-mode function that W28's Today names for restoring the player's mode).

<!-- from: properties/pr-timemode-zone -->

<a id="w29"></a>

## W29. Exchange rates that can change

*Kind:* behaviour, script command · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** Coins exchange at the fixed value ratio of their denominations. `MoneyChanger` takes a fee per currency (`CROWN=30`, with `CUT=5` as the default) and refuses unlisted currencies once any rate is set. "Crowns lost value" can therefore be done for one changer as a higher fee. A quest can swap the fee with `MPUNBEHAVE` and `MPBEHAVE`, and the save keeps the change. But the parser skips "-", so there is no premium, and a world-wide change means re-scripting every changer.

**Add.** Negative rates in `MoneyChanger`: `RATE.<CODE>=pct`, a premium or discount on top of `CUT`. A script command `MPEXCHANGE <CODE> <pct>` sets a world-wide exchange modifier, kept in the save, which every money changer applies.

**Example.** When the quest "Fall of the Capital" completes, `MPEXCHANGE CROWN 70` makes every money changer give 30% less for crowns.

*Re-checked against the code after writing: accurate.*

<!-- from: vocabulary-languages/vl-exchange-rates -->
