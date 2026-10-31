# Languages and vocabulary

Invented languages and how they are heard, read and learned, rule files, lists, socials, help files and the words the world uses. Part of the [builder capabilities audit](README.md).

<a id="l1"></a>

## L1. NPCs who understand only their own language

*Kind:* property · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** Speech in a language a mob does not know never reaches its speech_prog. Common has no language tool, though, so Common speech always does, even on an orc meant to know only Orcish. MudChat answers speech in any language, because it reads the speaker's clear text (`msg.sourceMessage()`). Only orders and trade check comprehension. `ISSPEAKING($n Common)` is true even when $n speaks Orcish. `Prop_LanguageSpeaker HOMEONLY` compares with `homeRoom` and `homeArea`, which are final nulls, so it fails for any mob with a start room.

**Add.** `UNDERSTANDS=ONLY` on `Prop_LanguageSpeaker`, or a separate `Prop_Monolingual LANGUAGES=Orcish,Goblinese CONFUSED="<S-NAME> grunt(s) and shrug(s)."`. Speech the mob cannot follow through its languages, translators or spells, Common included, does not reach speech_prog or MudChat. The CONFUSED line shows instead, if set. MudChat gets the same check and answers in its spoken language. Fix `HOMEONLY` to use the property's area and room, and fix `ISSPEAKING` for Common.

**Example.** In an orc camp only a captured interpreter (`Prop_LangTranslator`) or a player who has learned Orcish can bargain with the chieftain. Anyone else gets shrugs.

*See also:* L4, L11 (All three route hearing through one comprehension check (L4's understands(), L11's INTERPRETS search, L1's monolingual filter)).

<!-- from: vocabulary-languages/vl-monolingual-npcs -->

<a id="l2"></a>

## L2. A glossary for renaming terms without regex

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** The translation file can rename terms. `[final-translation]` runs on every printed line, and `[session-translation]` runs inside every `L()` call. But `REPLACE` swaps the whole line for one regex template, so each rule handles one occurrence. `REPLACEALL` is a plain substring match, so 'mana' also hits 'manage'. Neither keeps capitals: 'Mana' becomes 'aether'. Plurals and scopes need hand-written regex.

**Add.** `vocab/glossary.ini`, one term per line: `hit points = vigor`, `mana = aether`, `gold coin = crown`, `Archon = Warden`. Each line compiles to a whole-word rule that replaces every occurrence and keeps capitals (Hit Points to Vigor, HIT POINTS to VIGOR). It handles plurals from `(s)` forms, takes an exception list (`NOT:` proper names, item names) and `SCOPE=ALL|HELP|SCORE`. It applies in session translation through `L()` and in help output, and to final output only for lines marked `FINAL`. It can be built as a new `DirtyLanguage` rule command, `REPLACEWORD`.

**Example.** A low-magic world renames mana to 'focus' and spell to 'working' in score, help, prompts and messages with two lines. 'Mana' at the start of a sentence reads 'Focus'.

<!-- from: vocabulary-languages/vl-glossary -->

<a id="l3"></a>

## L3. List overrides per area, room and mob (Prop_LocalLists)

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Time-of-day lines, the health chart, damage and miss words, corpse blurbs, gaits and magic words are global. An underground area still hears the stock line, which has the sun rising in the west. Workarounds cover a few of these: `DISABLE=TODNOTIFIES` plus an area Scriptable with `TIME_PROG` and `MPECHO`, GenRace `HEALTHRACE`, corpse blurbs keyed by the killing ability or weapon, and `Prop_NewDeathMsg` on one mob. Nothing gives an area its own health chart, damage words, hit and miss text, gaits or magic words. Weather lines per area are a separate item.

**Add.** `Prop_LocalLists` on an area, room or mob: `TOD_CHANGE_OUTSIDE=`, `TOD_CHANGE_INSIDE=`, `HEALTH_CHART=`, `DAMAGE_WORDS=`, `WEAPON_HIT_DESCS=`, `CORPSE_BLURBS=`, `MAGIC_WORDS=`, `GAIT_LIST=`, or `LOAD=vocab/lists_underdark.ini`. Where the code knows a room or mob, a lookup tries the room's effects, then the area, then parent areas, then the global list. It goes through a new `CMProps.getListFileChoiceFromIndexedList(ListFile, Physical context)`.

**Example.** The Underdark sets `TOD_CHANGE_INSIDE` to '^JThe glowmoss brightens; it is now day-cycle.^?' and gives `HEALTH_CHART` entries like 'is bleeding black ichor'. The Feywild's `CORPSE_BLURBS` read 'dissolving into petals'.

*See also:* W13, W20 (Three ways to give an area its own time-of-day and weather lines (Prop_SkyText, climate files, Prop_LocalLists); pick one); L7, L8 (Health chart, corpse blurbs and hit and miss text overridden by area (L3), race (L7) and weapon (L8); one lookup chain).

<!-- from: vocabulary-languages/vl-area-lists -->

<a id="l4"></a>

## L4. Script trigger and functions for understanding a language

*Kind:* script trigger and script function · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** `ISSPEAKING($n Elvish)` tests the speaker's language, so `speech_prog p mellon` with `if isspeaking($n Elvish)` can already require a language. `ISABLE($n Elvish)` tests whether someone knows a language, and its string form gives the proficiency. `AFFECTED` tests the live effect. But speech the mob cannot follow produces no message a script sees. No test counts translators (`Prop_LangTranslator`, spells such as `Prayer_Tongues`, items, the room) or `INTERPRETS`. That logic is protected inside `StdLanguage.getAnyTranslator`.

**Add.** A public `CMLib.utensils().understands(MOB, String)`, with three script parts built on it. `NOTUNDERSTOOD_PROG [language|ANY] [p pattern]` fires when the mob hears speech it cannot follow. The garbled text is in `$0`, and `ISSPEAKING($n)` names the language. `UNDERSTANDS($n <lang>)` returns true or false. `UNDERSTANDS$($n <lang>)` returns the effective proficiency, 0 to 100, counting effects, items and the room. The proposed `LANG=` option for speech_prog is dropped, because `isspeaking` already covers it.

**Example.** A gate guard's `NOTUNDERSTOOD_PROG ANY` says 'Speak Common, stranger!' and marks the player suspicious with `MPSETVAR`.

*See also:* L1, L11 (All three route hearing through one comprehension check (L4's understands(), L11's INTERPRETS search, L1's monolingual filter)).

<!-- from: vocabulary-languages/vl-lang-triggers -->

<a id="l5"></a>

## L5. GenLanguage: dictionary files, suffixes, kept names, written name

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** A GenLanguage dictionary is one `HASHEDWORDS` string of english/word pairs, plus `WORDS` lists by length. Both are saved in `world/custom.cmare`, a text file. A large dictionary can be generated outside the game and pasted in, but thousands of entries are unwieldy. Draconic's 10,190 words are in Java. Unknown words are whole words picked by length and hash. Only player names stay as they are, so NPC, area and place names are scrambled. `writtenName()` always returns the spoken name.

**Add.** New GenLanguage fields:

- `WORDFILE=vocab/languages/dunetongue.txt`: one `english=word` per line, merged with `HASHEDWORDS`.
- `SUFFIXES=ED:a,ING:ir,S:i`: an inflected form is the root's translation plus a suffix, the regularity DOCS b062 found in Draconic.
- `SYLLABLES=`: banks of syllables that build unknown words, always the same way for a given word.
- `KEEPWORDS=` and `KEEPNAMES=true`: words left as they are; KEEPNAMES also keeps capitalised words and the names of mobs, areas and catalog entries.
- `WRITTENNAME=`: the name of the script in reading messages.

**Example.** Dunetongue gets a 600-word file, `SUFFIXES=S:en,ED:ka` and `KEEPNAMES=true`, so 'the sultan Hakim rode to Qarth' keeps Hakim and Qarth. Its writing is called Glyphscript.

*See also:* G12 (Both add syllable banks for made-up words (names vs unknown words), and G12 attaches a name style to a GenLanguage); L12 (Both extend GenLanguage translation (dictionary files and suffixes vs a translate() script run before the tables)); DT31 (DT31's preview shows which of L5's sources (dictionary, suffixes, syllables) each word came from).

<!-- from: vocabulary-languages/vl-genlanguage-lexicon -->

<a id="l6"></a>

## L6. Foreign text inside descriptions, exits, echoes and help

*Kind:* presentation · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** A readable item can carry a language as an effect, added in the item editor or by writing on it while speaking the language. READ then shows scrambled text, plus the translation for readers who know it. A non-gettable item can stand in for runes over a door. `LOOK_PROG` with `if isable($n Dwarven)` and `MPECHOAT` can show each viewer different room text. But room and exit descriptions, `MPECHO` and help cannot hold text in a language. Exits cannot be read in a language, because `StdLanguage` reading needs an Item. `CoffeeFilter` has no language tag.

**Add.** A markup tag that works in room, exit and item descriptions, readable text, `MPECHO`, script output and help: `^<LANG Elvish^>Mellon, friend^</LANG^>`. The filter garbles the text for each viewer by how well they understand it, using the reading code. Those who know the language also see '(you read: "Mellon, friend")'. An optional `NOTE="elegant runes"` is shown to those who cannot read it.

**Example.** A tomb: 'Above the door is carved ^\<LANG Dwarven NOTE="angular runes"^>Here lies Durin; disturb not his rest^\</LANG^>.' Players who know Dwarven read it. Others see angular runes and gibberish.

<!-- from: vocabulary-languages/vl-inline-language-text -->

<a id="l7"></a>

## L7. GenRace HEALTHCHART and CORPSEBLURBS fields

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** A custom race can only borrow another race's health text, through the GenRace field `HEALTHRACE`. It points at one of the 123 Java races that override `healthText`, golems, undead and plants among them. A race cannot write its own. Corpse blurbs are chosen by the killing ability or weapon, not by race. `Prop_NewDeathMsg` changes the death message of a single mob only.

**Add.** Two GenRace fields. `HEALTHCHART` is a list of eleven entries with `<MOB>`, like `HEALTH_CHART`, and allows pipe-separated variants. `CORPSEBLURBS` holds cause=phrase pairs. When they are empty, `HEALTHRACE` applies, then the global chart. Both are edited in `MODIFY RACE` and saved in `custom.cmare`.

**Example.** A Clockwork race: 'is shedding gears and sparks', 'has a cracked casing', 'ticks along perfectly'. Its corpse is 'a heap of springs'.

*See also:* L3, L8 (Health chart, corpse blurbs and hit and miss text overridden by area (L3), race (L7) and weapon (L8); one lookup chain).

<!-- from: vocabulary-languages/vl-race-health-chart -->

<a id="l8"></a>

## L8. Hit and miss messages per weapon

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Hit and miss text comes only from the six fixed slots of `WEAPON_HIT_DESCS` and `WEAPON_MISS_DESCS`, chosen by weapon class and damage type. `StdWeapon` always returns `standardHitString` or `standardMissString`, so a named sword or a whip cannot have its own lines. A Scriptable `DAMAGE_PROG` (amount in `$g`) or `Prop_UseEmoter` can only add a line next to the stock one. `CNCLMSG_PROG` cancels the damage along with the message. `Prop_NewDeathMsg` shows that a property can override a message.

**Add.** `Prop_WeaponMessages`, or GenWeapon fields `HITMSG` and `MISSMSG`. It takes `HIT="<S-NAME> sear(s) <T-NAMESELF> with <TOOLNAME>, <DAMAGE> <T-HIM-HER>.|..."`, `MISS="..."` and an optional `DAMAGEWORDS=` list for this weapon. `StdWeapon.hitString` and `missString` check it first. It also works on the natural weapons of a GenRace.

**Example.** Frostbrand: 'Rime spreads as \<S-NAME> \<DAMAGE> \<T-NAMESELF> with \<TOOLNAME>.' A spider race's bite: '\<S-NAME> sink(s) dripping fangs into \<T-NAMESELF>.'

*Note.* A `CNCLMSG_PROG` `<SCRIPT>` block can change the live message in `objs()[0]` instead of cancelling it (see *Already possible* in the README), so a weapon's hit line can be reworded today, if clumsily.

*See also:* L3, L7 (Health chart, corpse blurbs and hit and miss text overridden by area (L3), race (L7) and weapon (L8); one lookup chain).

<!-- from: vocabulary-languages/vl-weapon-messages -->

<a id="l9"></a>

## L9. Socials as world content, with pruning of stock ones

*Kind:* data file and builder tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `Prop_Socials` already adds emotes as content, on areas, rooms, exits, mobs and items. It is written inline with `NAME=`, `TARGET=`, `YOUSEE=`, `OTHSEE=`, `TARGSEE=`, `NOTARGSEE=`, `SRCCODE=`, `OTHCODE=`, `ZAPMASK=`, `MASK=` and `WORNONLY=`, and on a top-level area it is saved in that area's .cmare. `SOCIAL_PROG` lets NPCs react. But stock socials cannot be dropped, including the crude ones DOCS c03 notes: DISABLE knows only `COMMAND_`, `ABILITY_`, `EXPERTISE_`, `FACTION_`, `RACE_` and `CHARCLASS_`, and none of them reaches socials. `CREATE`, `MODIFY` and `DESTROY SOCIAL` write `resources/socials.txt`. `Prop_Socials LOAD=` looks only under `resources/`.

**Add.** Read `vocab/socials.txt` after the stock file, so a world line overrides a stock social of the same name. `vocab/socials.ini` takes `STOCK=ALL|NONE|<list to keep>` and `REMOVE=<list>`. In builder mode the three SOCIAL commands write the world file. `Prop_Socials LOAD=` also looks in `vocab/`.

**Example.** A monastery world keeps only BOW, NOD, SMILE and WAVE from the stock socials and adds MEDITATE, PROSTRATE and CHANT. Its monks react to these through `SOCIAL_PROG`.

*Re-checked against the code after writing: corrected.*

*See also:* S6, S20 (Three designs for world socials and dropping stock ones (vocab/socials files, the world resources overlay, DISABLE=STDSOCIALS)).

<!-- from: vocabulary-languages/vl-world-socials -->

<a id="l10"></a>

## L10. MPLEARNLANG and MPSAYIN, and an MPENABLE fix

*Kind:* script command · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** `MPENABLE <who> <lang> <N|++N|--N>` adds or raises a language, but it changes only the stored ability. The live effect, which translation reads, keeps its old proficiency unless the ability is new. MPENABLE also ignores the language cap that `StdLanguage.canBeLearnedBy` enforces. JavaScript can set the effect directly (`source().fetchEffect('Elvish').setProficiency(60)`). Saying one line in a language takes three: `MPSPEAK` to it, the line, then `MPSPEAK $i Common` back.

**Add.** `MPLEARNLANG <who> <lang> <N|+N|-N> [FORCE]` adds the language if it is missing, within `MAXLANGUAGES` unless `FORCE` is given. It sets both the ability and the live effect, as `helpProficiency` does, and tells the player 'Your grasp of Elvish improves.' `MPSAYIN <lang> <text>` says one line in that language, then goes back to the previous one. Also fix `MPENABLE` so it updates the live effect.

**Example.** Each lesson with the elven tutor runs `MPLEARNLANG $n Elvish +15`, and the next lines the player hears are visibly less garbled. A smuggler mutters `MPSAYIN ThievesCant the goods are in the well`.

*See also:* L13 (Both fix MPENABLE not updating the live language effect).

<!-- from: vocabulary-languages/vl-lang-commands -->

<a id="l11"></a>

## L11. INTERPRETS for hearing and reading, with partial understanding

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** GenLanguage `INTERPRETS` is read only by `getAnyTranslator`, which only orders and trade call. To hear speech, the listener needs an effect with the speaker's exact language ID; to read, the reader needs the same. `Prop_LangTranslator` takes percentages ('80% Elvish 40% Sylvan'). By default its bearer says the translation aloud as an interpreter, with the percentage as the chance per sentence. In `NOTRANSLATE` mode it covers only orders and trade. Neither lets a player half-understand a related language, so dialects cannot be modelled.

**Add.** Hearing and reading use the same search as orders and trade: own effects, then `INTERPRETS`, then translators. `INTERPRETS` takes percentages, as in `INTERPRETS=Elvish(60),Sylvan(30)`, and understanding counts at proficiency times the percentage. '(translated from Elvish)' shows as it does now.

**Example.** High Elvish scholars follow ordinary Elvish at 80%. Woodland Elvish speakers follow High Elvish at only 40%, so ancient scrolls are half-garbled for them.

*See also:* L1, L4 (All three route hearing through one comprehension check (L4's understands(), L11's INTERPRETS search, L1's monolingual filter)).

<!-- from: vocabulary-languages/vl-interprets-fix -->

<a id="l12"></a>

## L12. JavaScript hook for languages built by rule

*Kind:* JavaScript hook · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** GenLanguage can only look words up. Languages that transform words by a rule, such as PigLatin, PirateTalk and Drunken, override `translate()` in Java. `StdLanguage` has no script hook, so a builder cannot make a language that spells words backwards or a cipher.

**Add.** A GenLanguage field `TRANSLATESCRIPT` holding a JavaScript body. It defines `translate(word, language)` and optionally `garble(text, count)`. It is called before the lookup tables, and a null return falls back to them. It reuses the Rhino setup in `DefaultScriptingEngine`, like JavaScript in MOBPROG. That setup is not restricted (it has full `Packages` access), so with `JSCRIPTS=1` the script needs the same `MODIFY JSCRIPT` approval (`CMSecurity.isApprovedJScript`).

**Example.** Mirrorspeech for a looking-glass realm: `TRANSLATESCRIPT="function translate(w){return w.split('').reverse().join('');}"`. A Runic cipher shifts each letter.

*See also:* L5 (Both extend GenLanguage translation (dictionary files and suffixes vs a translate() script run before the tables)).

<!-- from: vocabulary-languages/vl-js-language -->

<a id="l13"></a>

## L13. Learning a language by immersion

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Proficiency in a language grows only while the character speaks it (`helpProficiency`), and only if they already know it. Hearing only translates, and reading only scrambles or translates, so living among orcs teaches no Orcish. The ways to learn a language are explicit: `TEACH` and `MOBTeacher`, the Studying skill, `MPENABLE`, or a race or class. A rough version can be scripted: a room Scriptable whose speech_prog runs `MPENABLE $n Orcish ++1`. But it hears only speech its host understands, and MPENABLE does not update the live effect.

**Add.** `Prop_LanguageImmersion` on an area, room, mob or item, or given by a quest: `LANGUAGE=Orcish|ANY HEAR=1 READ=3 CHANCE=20% START=5 MAX=60 GRANTAFTER=25`. Each time the character hears or reads the language, they may gain proficiency, up to `MAX`. A character who does not know it gains it at `START` after `GRANTAFTER` exposures, within `MAXLANGUAGES`. Gains are reported ('You are starting to follow Orcish.') and saved.

**Example.** A prisoner in the goblin warrens picks up Goblinese to 50% over a few game days. A dusty library gives `READ` gains in Ancient Imperial up to 40%.

*See also:* L10 (Both fix MPENABLE not updating the live language effect).

<!-- from: vocabulary-languages/vl-immersion-learning -->

<a id="l14"></a>

## L14. World help with fixed priority and hidden stock entries

*Kind:* data file and builder tool · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** GenAbility, GenLanguage and GenCommand carry their own `HELP` field, saved in `custom.cmare`. `CREATE COMMAND` on a stock command copies its help for rewriting. But `help/*.ini` files merge in `File.listFiles()` order, which is unspecified, so a world file cannot reliably override a stock key. Stock entries for removed features cannot be hidden, nor the 70 or so places that name CoffeeMud or the MUD. `CREATE HELP` and `CREATE AHELP` write `resources/help/newhelp.ini` and `arc_newhelp.ini`. World files have no place for lore pages.

**Add.** Load the stock files sorted by name, then `vocab/help/*.ini` last, so world entries win. `vocab/help/hide.txt` lists stock keys to drop (or `HIDE=CLANS,CHANNELS,...`). In builder mode `CREATE` and `MODIFY HELP` write `vocab/help/world_help.ini`. For an Archon, `HELP` shows where each entry came from.

**Example.** The world replaces HELP MAGIC with its own account of 'workings', hides the CLAN entries, and adds HELP DUNETONGUE and HELP SANDSEA as lore pages.

*See also:* S6 (Both load world help after the stock files and point the help editors at the world (vocab/help vs world/resources/help)).

<!-- from: vocabulary-languages/vl-world-help -->
