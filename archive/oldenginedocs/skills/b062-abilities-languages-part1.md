# Abilities/Languages part 1: the language base class, animal speech and Draconic

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b062` (Abilities/Languages [part 1/2: AlligatorSpeak .. Draconic]) · 17 files · 11531 lines of Java.

## Summary

In CoffeeMud a spoken language is an ability: a `StdLanguage` effect attached to a mob (for speaking), an item (for writing) or a room (as a translator), with a 0-100 proficiency. While a language is being spoken, the message pipeline garbles the speaker's own words in proportion to the speaker's proficiency, and replaces what everyone else hears with translated text; a listener who holds the same language also gets a "(translated from ...)" line. A creature that does not understand the speaker refuses orders and transactions, except that animal followers accept a short list of commands. This bucket holds the shared base class, a family of animal "languages" where every word becomes a random animal sound, and Draconic, a 10,190-key English-to-Draconic lexicon written directly into Java source. The base class and the animal family are small and reusable; Draconic is mostly content.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/`. Line counts come from the manifest (`wc -l`), except StdLanguage.java, which is not in this bucket's manifest and was read because the focus requires the base class.

| file or class | role | lines |
|---|---|---|
| `Abilities/Languages/StdLanguage.java` | Shared base for all languages: speaking state, translation primitives, the speech pipeline (`okMessage`/`executeMsg`), NPC comprehension, writing and reading. Not in this manifest. | 858 |
| `Abilities/Languages/Draconic.java` | Draconic: fallback syllable lists, a static `exactWords` TreeMap (10,190 keys) filled by `addHashPt1..4` and `translationHash`. | 10325 |
| `Abilities/Languages/AnimalSpeak.java` | Animal base: one random sound per word, 280-entry shared sound table, `CAN_GRUNT_WHEN_STUPID`. | 142 |
| `Abilities/Languages/Aquan.java` | Water-creature language: 30 base syllables plus 60 generated combinations, built at first use. | 87 |
| `Abilities/Languages/Common.java` | Default language: fixed proficiency 100, the only language that can be uninvoked. | 105 |
| `Abilities/Languages/Blah.java` | Joke language: every word becomes "blah". Used by the Archon class and by `Disease_Blahs`. | 80 |
| `Abilities/Languages/ApeSpeak.java` | Ape sounds (6), verb "bark(s)". | 67 |
| `Abilities/Languages/BatSpeak.java`, `BigCatSpeak.java`, `BirdSpeak.java`, `CatSpeak.java`, `ChickenSpeak.java`, `ChimpSpeak.java`, `CowSpeak.java`, `DeerSpeak.java`, `DogSpeak.java` | Template subclasses of AnimalSpeak that override only `ID`, `name`, `getSounds` and usually `getTranslationVerb`. | 67 each |
| `Abilities/Languages/AlligatorSpeak.java`, `BearSpeak.java` | Same template, shorter (no verb override). | 61 each |
| `Libraries/Dice.java:398`, `:409` | `roll(n, die, mod)` and `rollPercentage()`; the base class's random numbers come from here. | (not in bucket) |
| `Abilities/interfaces/Language.java` | The interface the base class implements (`translationHash`, `translationLists`, `translate`, `beingSpoken`, `getVerb`, `getTranslationVerb`). | (not in bucket) |

## How it works

### 1. Language as an ability: identity and speaking state (StdLanguage)

- **Trigger and placement.** The trigger word is `SPEAK` (`StdLanguage.java:60`). The classification is `ACODE_LANGUAGE` (`:104-108`, value 7 at `Abilities/interfaces/Ability.java:685`). Languages can be cast on mobs and items (`canAffectCode` `CAN_MOBS|CAN_ITEMS`, `:74-78`) and have no target (`:80-84`).
- **Auto-invocation.** `isAutoInvoked()` is true (`:86-90`) and `canBeUninvoked()` is false (`:92-96`). `Common` overrides both (`Common.java:51-61`).
- **Speaking state.** Each effect holds `spoken` and `alwaysSpoken` (`:113-114`). `beingSpoken` returns `alwaysSpoken || spoken` (`:120-124`). `setMiscText` reads the `ALWAYS` and `SPOKEN` parameters (`:150-159`).
- **Choosing a language.** `invoke` (`:719-757`) loops over the mob's effects. It sets the chosen language's speaking flag and clears every other language's flag (`:726-741`), so only one language is spoken at a time. Replies: "You are now speaking X." or "You were already speaking X." (`:746-749`). If no matching effect exists, the reply is "You are now speaking Common." (`:752`). Monsters always get proficiency 100 when they invoke a language (`:728-732`).
- **Proficiency.** `getProficiency(lang)` returns `proficiency()` for its own ID and 0 otherwise (`:176-181`).
- **Learning limits.** `canBeLearnedBy` (`:686-702`) asks `CMLib.ableComponents().getSpecialSkillRemainder(student, this).languageSkills()`. If no slot remains, it says "@x1 can not learn any more languages." (`:697`) and "You have learned the maximum @x1 languages, and may not learn any more." (`:698`). `teach` (`:704-717`) reports remaining slots. Training cost comes from `CMProps.getLangSkillGainCost(ID())` (`:98-102`).
- **Display.** `displayText` shows "(Speaking X)" while the language is spoken (`:184-189`).

### 2. Translation primitives

- **Defaults.** `translationHash` and `translationLists` return empty collections (`:132-142`). A language with no tables leaves words unchanged (`translate` returns `word`, `:236`).
- **`translate(language, word)`** (`:211-237`) works in four steps:
  1. An exact lookup of `word.toUpperCase()` in `translationHash`. The case of the input is copied onto the result by `fixCase` (`:214-215`).
  2. If `CMLib.players().getPlayerAllHosts(word)` finds an online character with that name, the word passes through unchanged (`:216-218`).
  3. Otherwise the word is looked up in `translationLists` by length: index `word.length() - 1`. If that index is out of range, the exception is caught and the last list is used (`:223-231`).
  4. The syllable is `choices[ CMath.abs(word.toLowerCase().hashCode()) % choices.length ]` (`:234`). The same word always gets the same fallback within a run.
- **`messChars(lang, words, numToMess)`** (`:252-278`) garbles letters. The count is `numToMess/2` (`:254`). Each chosen letter that is not markup (`CMStrings.markMarkups`, `core/CMStrings.java:2402`) becomes a random vowel (`aeiouy`) or consonant of the same case (`:262-272`). A letter is changed at most once (`:273`).
- **`scrambleAll(lang, str, numToMess)`** (`:292-383`) splits text into maximal runs of letters. Each run goes through `translate`. Spaces, punctuation and the separators between runs are kept. A run containing a digit is left raw (`:344-348`). The `numToMess` parameter is never read in the body (see Problems).
- **Speaker's words.** The text inside the speech is found by `CMStrings.getSayFromMessage`: the text between the first and last apostrophe (`core/CMStrings.java:1728-1737`). `replaceSayInMessage` and `substituteSayInMessage` put text back in the same place (`:1746-1773`).
- **Translator search.** `getMyTranslator` (`:385-401`) returns the language effect with the highest proficiency among a container's effects. `getAnyTranslator` (`:403-422`) checks the listener's effects, then the room's, then the listener's carried items. An item or room that holds a language effect therefore translates for everyone in it.

### 3. The speaker's side (`okMessage`, `StdLanguage.java:503-523`)

The branch runs when this effect is being spoken, the source is its owner, the message is SPEAK, TELL or a channel message, and no tool is set yet (`:503-508`). The garble count is:

`numToMess = round(letters × (100 − proficiency) / 100)` (`:515`), then halved again inside `messChars` (`:254`).

So a 20-letter utterance at proficiency 0 has 10 letters garbled. At proficiency 100 it has none. The steps:

- `processSourceMessage` (`:441-460`) garbles the speaker's own line and sets its verb to `getTranslationVerb()` (for example "hiss(es)" for Draconic, "growl(s)" for animals). The speaker therefore sees their own garbled words.
- `processNonSourceMessages` (`:462-477`) replaces what everyone else sees with `scrambleAll(...)` output and sets the verb to `getVerb()` (for animals, "go(es)"; Draconic has an empty verb, so `fixSayVerb` leaves the text as is, `:424-439`).
- Both calls use `msg.modify(..., this, ...)` (`:449-457`, `:467-475`), which sets the tool of the message to this language. Listeners recognise the language from the tool.
- Each use calls `helpProficiency(speaker, 0)` (`:520-521`). See section 7.

### 4. The listener's side (`executeMsg`, `StdLanguage.java:812-833`)

When a listener holds the same language effect and the message's tool is this language, the listener's effect:

1. Garbles the words again using the listener's own proficiency: `numChars × (100 − listenerProficiency)/100` (`:824-826`).
2. Adds a trailer message (`msg.addTrailerMsg`) built by one of three helpers. Channel messages come first (`translateChannelMessage`, `:790-805`), unless the channel has the `NOLANGUAGE` flag (`:795`). Otherwise the target's line (`translateTargetMessage`, `:775-788`) or the others' line (`translateOthersMessage`, `:759-773`) is used. The trailer reads `@x1 (translated from @x2)` (`:768-769`, `:783`, `:798-799`), where `@x2` is the language's name and the text is the speaker's (garbled) words.

No roll decides whether a knower sees the trailer: it appears whenever the listener holds the effect. Only the garble depends on the listener's proficiency. `translateOthersMessage` also needs an apostrophe in the others' line (`:761-762`).

### 5. Comprehension when a creature is addressed (`okMessage`, `StdLanguage.java:536-646`)

This branch runs when the message targets this mob and is one of ORDER, BUY, BID, SELL, LIST, VIEW, WITHDRAW or DEPOSIT (`:540-549`). It is skipped for speakers with the relevant staff security flags (`:554-556`). The steps:

1. **Speaker's language.** The message tool if it is a language (`:559-560`). Otherwise, for a monster listener, the language the speaker is currently speaking (`CMLib.utensils().getLanguageSpoken`, `:562-566`). Otherwise Common, taken from the speaker or created at proficiency 100 (`:569-575`).
2. **Listener's translator.** `getAnyTranslator` for that language on the listener (`:582-589`). It may return null.
3. **Test.** The creature understands if it has a translator and `2 × d100 ≤ speakerProficiency + listenerProficiency` (`:590-591`). `rollPercentage` returns 1..100 (`Libraries/Dice.java:409-411`). So two 100s always understand, a total of 100 understands half the time, and a total below 2 never does.
4. **Failure.** The message is changed to SPEAK in both codes (`:635-637`) and a trailer is sent back to the speaker (`:638`). The reply is "<S-NAME> do(es) not speak X and would not understand <T-HIM-HER>." when there is no translator (`:631`), or "<T-NAME> <T-IS-ARE> having trouble understanding <T-YOUPOSS> pronunciation." when the roll fails (`:634`). Because the message is now speech, the order or transaction is not carried out by the normal path (by reading; not run).
5. **Animal orders** (ORDER only, `:596-630`). If the creature has no translator, the first word of the order is matched as a prefix against the entries of `ANIMAL_ORDER_LIST` (`core/CMProps.java:602`; `resources/lists.ini:662-664`). Entries are `NAME` or `NAME(replacement)`. Single-word orders match a plain entry. Entries ending in `*` take an argument. A match with a parenthesised replacement rewrites the order text (`:624-626`). Then `break` (`:627`) leaves the switch, so the failure trailer is never sent. For example, "stay" becomes "AUTOGUARD ON" and "come" becomes "AUTOGUARD OFF". "attack goblin" matches `ATTACK *` and passes unchanged.

### 6. Writing and reading (`StdLanguage.java:479-495`, `:525-534`, `:648-682`, `:834-856`)

- **Refusing a second language.** Writing while speaking a language is refused if the item already holds a different language effect: "@x1 is already written in @x2 and can not have @x3 writing added." (`tryLinguisticWriting`, `:489`).
- **Tagging the item.** After a write (`executeMsg`, `:834-856`), the item's last language effect is removed (reverse search, `:846-854`) and a copy of this language is attached to the item as a non-uninvokable effect (`:855`). The item therefore carries the language of its last writer.
- **Reading.** For an item with a language effect and text, a reader who can see the item (`CMLib.flags().canBeSeenBy`, `:655`) sees "It says '<scrambled text>'" (`:675`). The scramble count uses the reader's proficiency if they know the language (`:667-673`). A reader who knows the language and whose text changed also sees "It says '<original>' (translated from X)" (`:678`). Text prefixed with `::` skips the prefix (`:662-664`). A literal `CANCEL` in the others' message disables scrambling (`CANCEL_WORD`, `:110`, `:653`).

### 7. Proficiency gain and learning

- **Use-based gain.** Each speech calls `helpProficiency(mob, 0)` (`StdLanguage.java:520-521`). `StdAbility.helpProficiency` (`Abilities/StdAbility.java:1810-1840`) exits if the ability is not savable (`:1824`) or if the last help was within 300,000 ms, i.e. five minutes (`:1827-1828`). It also exits at the mob's maximum proficiency (`:1833-1834`). Otherwise it passes a gate of `round(sqrt(INT) × 34 × random) ≥ current proficiency` (`:1837`), then a percentage roll against `getProfGainChance` (`:1839-1840`).
- **Monsters.** `StdRace.giveMobAbility` sets a monster's language to proficiency 100 and invokes it (`Races/StdRace.java:659-665`).
- **Race and class grants.** See section 11.

### 8. The animal family (`AnimalSpeak.java` and its template subclasses)

- **Identity.** `AnimalSpeak` is not a natural language (`:63-67`). The speaker's verb is "growl(s)" (`:75-79`) and listeners' verb is "go(es)" (`:69-73`). `translationLists` returns the static `wordLists`, which nothing fills (`:51`, `:108-112`). The constructor's `mapped` flag does nothing (`:52`, `:54-61`).
- **Per-word translation.** `translate` ignores the word and returns one sound from `getSounds()` (`:122-125`). The sentence keeps its word count, and the same word gets a different sound on every call.
- **Random case.** The sound is then cased by `switch(CMLib.dice().roll(1,5,-1))` (`:126-140`). `roll(1,5,-1)` returns 0..4 (`Libraries/Dice.java:398-405`). Cases 0 and 1 give lowercase (40%), case 2 gives capitalised (20%), case 3 gives UPPERCASE (20%) and the default gives the sound as stored (20%). Case 5 (uppercase with "!", `:136-137`) can never run.
- **Grunting.** `affectPhyStats` sets `CAN_GRUNT_WHEN_STUPID` (`:114-120`, flag at `Common/interfaces/PhyStats.java:440`). Without it, an animal-intelligence mob is refused: "You aren't smart enough to speak." (`MOBS/StdMOB.java:2613-2615`).
- **Sound tables.** Each subclass overrides `getSounds()` with its own array. The base array has 280 entries and 107 distinct sounds (`:81-101`). Duplicates act as weights: "chirp" appears 15 times. The base table also contains the literal string `pipe[10]` (`:86`).

### 9. Aquan (`Aquan.java:35-87`)

Aquan inherits the animal translation (`translate` from AnimalSpeak), but builds its own sound list. The base is 30 syllables (`:51-58`). On first use (`:60`, `:71-84`), it adds 60 random two-syllable combinations, each checked for duplicates. The random source is `new Random(System.currentTimeMillis())` (`:74`), not the game's dice. The list therefore has 90 sounds and differs between boots. Its verb is "gurgle(s)" (`:62-66`).

### 10. Draconic (`Draconic.java:35-10325`)

- **Fallback syllables** (`:63-83`, lists built on first call): length 1 gives `{y}`; length 2 gives six syllables (`ve, ov, iv, si, es, se`); length 3 gives six (`see, sev, ave, ces, ven, sod`); length 4 gives eleven; length 5 gives ten; length 6 and longer use the last list of twelve. The choice is deterministic per word (`StdLanguage.java:234`).
- **Dictionary storage.** `exactWords` is a static `TreeMap` (`:85`). The first call to `translationHash` (`:6459-6467`) runs `addHashPt1` (`:87`), `addHashPt2` (`:1716`), `addHashPt3` (`:3287`) and `addHashPt4` (`:4887`). The method body then adds 3,854 more entries inline (lines 6468-10321). The map is returned on later calls (`:6462-6463`).
- **Size.** 10,210 `put` calls give 10,190 distinct keys. Twenty keys are defined twice, always with the same value (for example `ALLRIGHT` at lines 390 and 398). Key lengths run from 1 to 22 characters (median 7). Values run from 2 to 19 (median 8). 315 values contain an apostrophe (for example "ava'yorn" for HELLO).
- **Alphabetical blocks.** `addHashPt1` covers AARDVARK to CHILD, `addHashPt2` CHILDFREE to ENTICING, `addHashPt3` ENTICINGLY to HOLLOWS, `addHashPt4` HOLY to NORTHEAST, and the method body NORTHERN to ZOMBIISM.
- **Morphology.** Derived words are often the root's Draconic plus a short suffix. Measured by a script over the table (only where the root is in the table; exact-match rate): -ED to +a 87% (472/542); -ING to +ir 92% (572/624); -LY to +vi 92% (326/356); -NESS to +ra 95% (283/297); -ABLE to +ul 95% (120/126); -ER to +ar 61% (141/231); -ERS to +ari 77% (131/170); -ES to +ic 46% (63/136); -S to +i 51% (1082/2108). Examples: ABIDE `feqad`, ABIDED `feqada`, ABIDING `feqadir`; ABLE `yscik`, ABLEIST `yscikox`. Plurals and -ES forms are much less regular than the others.

### 11. Races, classes and other systems

- **Cultural languages.** `StdRace.mapCulturalAbilities` (`Races/StdRace.java:739-760`) maps each cultural ability at the race's proficiency. Draconic is cultural for these races (proficiency from the race file):

| race (file) | Draconic proficiency | other cultural abilities on the race |
|---|---|---|
| Dragon (`Races/Dragon.java:137-138`) | 100 | none |
| Draconian (`Races/Draconian.java:102`) | 100 | Butchering (skill), 50 |
| Naga (`Races/Naga.java:131`) | 100 | SnakeSpeak, 100 |
| FaerieDragon (`Races/FaerieDragon.java:131`) | 100 | none |
| Kobold (`Races/Kobold.java:102-103`) | 75 | none |
| Lizard (`Races/Lizard.java:102`) | 75 | none |
| LizardMan (`Races/LizardMan.java:104`) | 25 | Hunting (50), Skill_TailSwipe (25) |
| Wyvern (`Races/Wyvern.java:101`) | 50 | none |
| Troll (`Races/Troll.java:95`) | 50 | none |

- **Animal races.** `Races/Animal.java:95-97` gives AnimalSpeak at 100 (`racialAbilityProficiencies`). GiantAnimal, WereAnimal and Mustelid do the same (Mustelid also adds burrowing skills). Ape races get ApeSpeak at 100 (`Races/Ape.java:102-104`); Gorilla and Sasquatch also get it (`Races/Gorilla.java:89`, `Races/Sasquatch.java:80`).
- **Aquan races.** `Races/GreatAmphibian.java:97`, `Races/Crab.java:94` and `Races/SeaHorse.java:96` grant Aquan with Skill_Swim.
- **Classes.** `CharClasses/Archon.java:78-79` maps AnimalSpeak and Blah at level 1 with the flag passed as false.
- **Familiars.** `Abilities/Properties/Prop_Familiar.java:244-266` gives the master a copy of the familiar's animal language at proficiency 100, set to speaking. Lines 326-334 pass the familiar's messages through that copy's `okMessage`, so the master understands the familiar.
- **Disease.** `Abilities/Diseases/Disease_Blahs.java:151-158` routes an afflicted mob's speech through a fresh Blah effect at proficiency 100, then through that effect's `okMessage`.
- **Other translators.** `Abilities/Druid/Chant_SpeakWithAnimals.java:490-496` and `Abilities/Properties/Prop_LangTranslator.java:247-253` override the same translation hooks.
- **Natural-language filters.** `Prayer_ReadLanguage.java:132` and `Prayer_Fluency.java:105` check `isANaturalLanguage()`, so animal languages are excluded from those prayers.
- **Speaking state of NPCs.** `Behaviors/CommonSpeaker.java:98-120` switches an NPC's spoken language.
- **Utilities.** `Libraries/CoffeeUtensils.java:594-612` returns the first language effect with `beingSpoken`.
- **Editors.** `WebMacros/grinder/GrinderAbilities.java:342-368` and `WebMacros/AbilityData.java:425-490` read and write the word tables (see Problems).

### 12. Messages shown to players

Short, real strings from the files:

- "You are now speaking X." / "You were already speaking X." (`StdLanguage.java:746-749`)
- "You are now speaking Common." (`:752`); Common's own reply is "You are already speaking Common." / "You are now speaking Common." (`Common.java:94-99`)
- "It says '...'" and "(translated from X)" for written text (`StdLanguage.java:675`, `:678`)
- "@x1 is already written in @x2 and can not have @x3 writing added." (`:489`)
- "<S-NAME> do(es) not speak X and would not understand <T-HIM-HER>." (`:631`)
- "<T-NAME> <T-IS-ARE> having trouble understanding <T-YOUPOSS> pronunciation." (`:634`)
- "I am now speaking X." (or "I am already speaking X."), said aloud by a monster that is following someone (`Common.java:100-101`)

## Content worth mining

### Draconic lexicon

A dictionary of 10,190 keys is too big to paste. Sample pairs (uppercase English key to Draconic), all from `Draconic.java`:

| English | Draconic | English | Draconic |
|---|---|---|---|
| A | vi | DRAGON | draushum |
| I | si | GOLD | aurix |
| ONE | ir | FIRE | docar |
| NOT | ti | WATER | okraz |
| OF | di | SWORD | caex |
| OR | usv | DEATH | nelithral |
| YOU | wux | KILL | amid |
| YES | axun | HELLO | ava'yorn |
| NO | thric | GOODBYE | veyet'toon |
| OK | kruth | PLEASE | petranas |
| NORTH | myvillion | THANK | tiichi |

Two things are worth copying: the suffix behaviour in section 10 (it lets a small curated list generate many forms), and the length-indexed fallback (it gives unknown words a stable, pronounceable form). Draconic is also the cultural language of about nine races (section 11), so one lexicon serves several species.

### Animal sound banks (sound arrays and speaker verbs)

| language (file) | sounds (entries / distinct) | speaker verb | sample sounds |
|---|---|---|---|
| AnimalSpeak (base) | 280 / 107 | growl(s) | hiss, grunt, roar, quak, snort, growl |
| Ape (`ApeSpeak.java`) | 6 / 6 | bark(s) | hoot, bark, grunt, whine, pock, pant |
| Alligator (`AlligatorSpeak.java`) | 4 / 4 | growl(s) (inherited) | hiss, grunt, roar, quak |
| Bat (`BatSpeak.java`) | 3 / 3 | squeek(s) | screech, screeech, screeeeech |
| Bear (`BearSpeak.java`) | 4 / 4 | growl(s) (inherited) | growl, groan, moan, roar |
| Big cat (`BigCatSpeak.java`) | 3 / 3 | roar(s) | growl, roar, snarl |
| Bird (`BirdSpeak.java`) | 10 / 10 | squawk(s) | chirrup, chirp, twitter, tweet, sing, whistle, warble, trill, cheep, click |
| Cat (`CatSpeak.java`) | 8 / 8 | mewl(s) | mew, purr, meow, miaow, hiss, yowl, screech, caterwaul |
| Chicken (`ChickenSpeak.java`) | 12 / 7 | bawk(s) | cluck, cackle, bock (six times), chirp, crow, screech, peep |
| Chimp (`ChimpSpeak.java`) | 7 / 7 | chatter(s) | pant, hoot, grunt, scream, chatter, screech, bark |
| Cow (`CowSpeak.java`) | 5 / 5 | moo(s) | moo, moooo, moooooo, mooooooo, mooooo |
| Deer (`DeerSpeak.java`) | 4 / 2 | bleat(s) | bell (three times), grunt |
| Dog (`DogSpeak.java`) | 7 / 7 | bark(s) | bark, woof, arf, bay, bow-wow, howl, yap |
| Aquan (`Aquan.java`) | 90 (30 base + 60 generated) | gurgle(s) | gurgle, blub, chitter, oioioi, glub, honk |

### Animal follower commands (`resources/lists.ini:662-664`)

EAST, WEST, UP, DOWN, NORTH, SOUTH, FOREWARD, AFT, RIGHT, LEFT, FLAP, CRAWL, ATTACK \*, KILL \*, MURDER \*, FIGHT \*, STAY(AUTOGUARD ON), COME(AUTOGUARD OFF), AUTOGUARD \*, EAT \*, DRINK \*, FLEE \*, SIT, STAND. The table is a ready-made list of pet commands that do not need the pet to share the speaker's language.

### Message strings

The strings in section 12 are short, neutral and reusable as templates for an NPC-language system.

### Content caution

The Draconic table includes a small number of sexual, anatomical and profane entries (about a dozen and about seventeen by pattern count, in `Draconic.java`). They are noted here and not reproduced. No minors are involved in this bucket.

## Notable design ideas

- **Proficiency blurs rather than switches.** Comprehension is a graded garble: the speaker's proficiency garbles the speaker's own words, and the listener's proficiency garbles the trailer a knower sees. The speaker sees their own mistakes.
- **Translation as an extra line.** Knowers do not lose the message. They get the speaker's (garbled) words plus "(translated from X)", while everyone else gets gibberish (`StdLanguage.java:759-773`, `:812-833`).
- **Translators are objects and places.** A language effect on an item or a room can translate for anyone who carries or stands in it (`:385-422`).
- **The comprehension test is two-sided and simple.** `2 × d100 ≤ speaker + listener` (`:590-591`) needs no tables.
- **Sentence shape is preserved.** Animal speech and scrambled speech keep the word count and the punctuation (`:292-383`), so a listener can still follow the rhythm.
- **Items remember their language.** Writing in a language tags the item, and readers without the language see scrambled text (`:834-856`, `:648-682`).
- **Animal commands are rewritten.** "stay" becomes "AUTOGUARD ON" before the order is executed (`:596-630`).
- **Stable randomness.** Fallback translations use a hash of the word, so the same word is always translated the same way.

## Problems and cruft

Items marked "by reading" were established from the code and not run.

1. **Proficiency does not scramble non-knowers.** `scrambleAll` takes `numToMess` (`StdLanguage.java:292`) and never uses it. The call at `:464` and `:674` therefore always scrambles the whole line (by reading). Only knowers get a proficiency-dependent garble.
2. **Unreachable keys.** 39 dictionary keys contain a hyphen, an apostrophe or a pipe, such as `AREN'T` (`Draconic.java:589`), `HALF-BLOOD` (`:4601`), `WILLO'WISP` (`:10122`), `IS|HAS` (`:5405`) and `WOULD|HAD` (`:10240`). `scrambleAll` splits at any non-letter (`StdLanguage.java:329-355`), so `translate` never receives such keys. Speech never matches them (by reading; not run).
3. **Placeholder output.** Twelve entries translate to the literal string `UNKNOWN`: BARE (`Draconic.java:817`), DARKELF and DARKELVES (`:2433-2434`), EVERLASTING (`:3351`), MARKET (`:5851`), NECROMANCER, NECROMANCERS, NECROMANCY, NECROMANTIC, NECROMANTICAL and NECROMANTICALLY (`:6288-6293`), and PRAY (`:7067`). Listeners would hear "unknown" in place of these words.
4. **English leak.** `ONDAY` maps to `entertainer` (`Draconic.java:6560`). There is no `MONDAY` key (checked by script).
5. **Odd Draconic forms.** Mixed-case values (`Vs'shtak` at `:2926-2928`, `MakiaVi` at `:2934-2935`) are overwritten by `fixCase` anyway. Values with a pipe (`ui|ulph`, `ornla|dah`) cannot be reached (point 2). The fallback list `four` has a stray backtick in the syllable ``sav`e`` (`:71`).
6. **Dead code in AnimalSpeak.** The case list has a duplicated pair (0 and 1, `:128-131`) and an unreachable case 5 (`:136-137`). The `mapped` flag and `wordLists` do nothing (`:51-61`).
7. **Stray token.** `pipe[10]` sits in the base sound table (`AnimalSpeak.java:86`).
8. **Non-reproducible sounds.** Aquan uses `java.util.Random` seeded from the clock (`Aquan.java:74`), not the game's dice, and its list differs between boots.
9. **Latent index error.** `CMath.abs` returns a negative value for `Integer.MIN_VALUE` (`core/CMath.java:2034-2039`). The modulo at `StdLanguage.java:234` can then give a negative index. This needs a word whose hash is exactly that value, so it is rare but unguarded.
10. **Lazy static initialisation.** `translationHash` fills a static TreeMap on first use (`Draconic.java:6462-6467`). This is not thread-safe, which does not matter for a single-player port.
11. **Probable null dereference.** `AnimalSpeak.translationLists` (`AnimalSpeak.java:108-112`) and `Blah.translationLists` (`Blah.java:69-73`) return null. `GrinderAbilities.java:354` calls `.size()` on the result without a null check. Saving an animal or Blah ability in the web editor would throw (by reading; not run).
12. **Typo in the order list.** `FOREWARD` (`resources/lists.ini:662`) is probably meant to be FORWARD.
13. **Documentation and code differ.** The help says Draconic is "the language spoken by dragons and their kind" (`resources/help/misc_help.ini:1757`). The code also grants it to Kobolds, Lizards, Trolls and Wyverns (section 11). The help for Common says it is "the default language spoken by all races" (`misc_help.ini:1755`), but Common is not auto-invoked (`Common.java:51-55`).
14. **Multiplayer assumptions.** Tells and channels are handled (`StdLanguage.java:507`, `:790-805`), shopkeeper and order handling with staff bypass (`:540-556`), and player names are passed through from the online-player cache (`:216-218`). None of these is needed for a single-player game.

## Takeaways for a Python SUD

- **Model a language as data.** A `Language` record holds a lexicon (`dict` of upper-case word to translation), syllable banks by length, an optional sound list for animal-style languages, and a verb pair. Keep the 10,000-entry Draconic table as a data file, or start with a curated few hundred words and the suffix rules.
- **Keep the proficiency model.** Store a `dict[str, int]` of language to proficiency per character. Use the two-sided garble (speaker's proficiency for the speaker's line, listener's for the listener's trailer) and the `2 × d100 ≤ a + b` comprehension test.
- **Use stable randomness.** Use `random.Random(seed)` for animal sounds and `zlib.crc32` for fallback syllables. Do not use Python's `hash()`, which changes between processes and would change translations between sessions.
- **Borrow the NPC order rule.** A creature that does not understand refuses an order unless it is a follower and the order is in a short command table. For a single-player game this is the most useful mechanic here: shopkeepers, guards and pets interpret speech by language.
- **Borrow the pet commands.** Use the order table (`stay` → `AUTOGUARD ON` style) as a `dict` in the pet's behaviour, independent of language.
- **Simplify.** Drop channels, tells, player-name passthrough, the `CANCEL` sentinel, the web editors and the `Gen` plumbing. A single-player game has one listener and NPCs, so the "translated from" line can be shown only when the player holds the language.
- **Avoid the bugs above.** Make the scramble use proficiency if that is what is intended, check keys against the tokeniser, and remove the placeholder entries.

A sketch of the core functions:

```python
import random, zlib
from dataclasses import dataclass, field

@dataclass
class Language:
    name: str
    speak_verb: str = "say(s)"
    lexicon: dict = field(default_factory=dict)     # UPPER word -> translation
    syllables: list = field(default_factory=list)   # index = word length - 1
    sounds: list = field(default_factory=list)      # animal style: one sound per word

def translate_word(lang: Language, word: str, rng: random.Random) -> str:
    if lang.sounds:
        return rng.choice(lang.sounds)
    hit = lang.lexicon.get(word.upper())
    if hit is None and lang.syllables:
        bank = lang.syllables[min(len(word), len(lang.syllables)) - 1]
        hit = bank[zlib.crc32(word.lower().encode()) % len(bank)]
    return hit or word

def understands(speaker_prof: int, listener_prof: int, rng: random.Random) -> bool:
    return rng.randint(1, 100) * 2 <= speaker_prof + listener_prof
```

## Coverage

- **Read in full:** `Abilities/Languages/StdLanguage.java` (858 lines; not in this manifest, read because the focus requires the base class), `AnimalSpeak.java`, `Common.java`, `Aquan.java`, `Blah.java`, `ApeSpeak.java`, `AlligatorSpeak.java`, `BearSpeak.java`.
- **Read in part:** `Draconic.java`: lines 1-140, the translation hook region 6440-10325, and the method layout. The dictionary lines 87-6456 were checked by script (every `put`, duplicate keys, and that no non-dictionary code sits between the method headers), not read one by one.
- **Skimmed by script:** `BatSpeak.java`, `BigCatSpeak.java`, `BirdSpeak.java`, `CatSpeak.java`, `ChickenSpeak.java`, `ChimpSpeak.java`, `CowSpeak.java`, `DeerSpeak.java`, `DogSpeak.java` (sound arrays and speaker verb only).
- **Consulted outside the bucket:** `Abilities/StdAbility.java`, `Libraries/Dice.java`, `core/CMStrings.java`, `core/CMath.java`, `Races/StdRace.java` and the race files cited, `CharClasses/Archon.java`, `Abilities/Diseases/Disease_Blahs.java`, `Abilities/Properties/Prop_Familiar.java`, `Libraries/CoffeeUtensils.java`, `resources/lists.ini`, `resources/help/misc_help.ini`.
- **Statistics** (dictionary counts, suffix rates, sound counts) were computed by script over the source files and are reproducible from them. Behaviour was established by reading, not by running the game.
