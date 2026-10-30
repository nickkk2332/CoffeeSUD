# Languages, part 2: Drowish to WormSpeak

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b063` (Abilities/Languages [part 2/2: Drowish .. WormSpeak]) · 42 files · 13113 lines of Java.

## Summary

The tongues in this bucket are `Ability` subclasses built on `StdLanguage` (`com/planet_ink/coffee_mud/Abilities/Languages/StdLanguage.java:38`); the one exception here, Krakenspeak, extends `Aquan`, which is outside this manifest. A language is a small mechanism plus data: a whole-word dictionary, syllable pools used to invent words of a given length, a verb used in speech output ("quack(s)"), and a flag for whether it is a natural language. The base class does the real work: it decides who understands whom from a 0-100 proficiency on each side, garbles or rewrites messages according to the speaker's and listener's skill, and records written text in a language so that only readers who know it can read it. This bucket holds the concrete languages built on that base: fantasy-race tongues (Drow, Elvish, Orcish, Goblin, Dwarven, Gnomish, Ignan, Giant, a Fey conlang), a criminal cant, a pirate dialect, sign and semaphore, a drunken-slur filter, Pig Latin, a rotation cipher, an invisible ink, and 19 animal-speech families. `GenLanguage` lets builders define new languages in the running game as data. The most reusable parts are the two-sided comprehension rule, the proficiency-driven garbling, the deterministic invented-word method, and the idea that a medium (voice, hands, flags, ink) changes what gets through.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Languages/StdLanguage.java` | Base class: speaking toggle, translate, scramble, garble, comprehension roll, writing, learning cap | 858 |
| `Abilities/Languages/GenLanguage.java` | Data-defined language (a `Gen` object), all fields in static tables keyed by ID | 413 |
| `Abilities/Languages/Drowish.java` | Drow tongue; largest dictionary (3,685 unique keys) and the shared dictionary for Undercommon | 4373 |
| `Abilities/Languages/Fey.java` | Fey conlang (934 unique keys) | 1053 |
| `Abilities/Languages/ThievesCant.java` | Underworld cant plus a slang glossary (844 unique keys); mapped to the Bard class | 1041 |
| `Abilities/Languages/Elvish.java` | Elvish dictionary (524 keys) and syllable pools | 618 |
| `Abilities/Languages/Goblinese.java` | Goblin tongue (426 keys) | 520 |
| `Abilities/Languages/Orcish.java` | Orc tongue (414 put calls, 344 unique keys) | 509 |
| `Abilities/Languages/Semaphore.java` | Ship-to-shore flag signalling; every word becomes "flag" | 365 |
| `Abilities/Languages/Ignan.java` | Fire-folk tongue (229 keys, Sanskrit-like pools) | 331 |
| `Abilities/Languages/PirateTalk.java` | Pirate dialect: about 208 phrase substitutions plus an "-ing" rule | 305 |
| `Abilities/Languages/SignLanguage.java` | Sign language; swaps say/ask verbs for sign verbs, no word translation | 270 |
| `Abilities/Languages/Gigantic.java` | Giant tongue, speech forced to capitals (88 keys) | 209 |
| `Abilities/Languages/Drunken.java` | Slurring filter that rewrites words, not a real tongue | 141 |
| `Abilities/Languages/Encrypto.java` | Per-instance letter and word rotation cipher | 124 |
| `Abilities/Languages/PigLatin.java` | Rule-based Pig Latin | 114 |
| `Abilities/Languages/Undercommon.java` | Pidgin: Drowish dictionary plus mixed syllable pools; partial fluency in four other tongues | 112 |
| `Abilities/Languages/FoxSpeak.java` | Replaces the whole utterance with a random fox phrase (29 entries) | 95 |
| `Abilities/Languages/Krakenspeak.java` | Extends `Aquan` (outside this manifest); forces capitals | 88 |
| `Abilities/Languages/Druidic.java` | Animal-noise pools for druid speech | 86 |
| `Abilities/Languages/Dwarven.java`, `Gnomish.java` | Syllable-pool-only tongues | 84 each |
| `Abilities/Languages/InvisibleInk.java` | Ink whose words translate to nothing; not a natural language | 62 |
| 19 animal files (`DuckSpeak` ... `WolfSpeak`, `WormSpeak`) | One sound list each on top of `AnimalSpeak` | 67 each (WormSpeak 52); 1258 total |
| `Abilities/Languages/AnimalSpeak.java` | Base for the animal family; not in this manifest, read for the interaction | (outside bucket) |
| `Libraries/Dice.java:398-411` | `roll(n,d,mod)` and `rollPercentage()`; read for ranges only | (outside bucket) |

## How it works

### 1. Data model: a language is an ability with dictionaries

- Class defaults in `StdLanguage`: `triggerStrings` is "SPEAK" (`:60`); `abstractQuality` is `QUALITY_OK_SELF` (`:69-71`); `canAffectCode` is `CAN_MOBS|CAN_ITEMS` (`:75-78`), so a language can sit on a mob (a speaker) or on an item (a book, scroll or ink); `isAutoInvoked` is true (`:87-90`); `canBeUninvoked` is false (`:93-96`); `classificationCode` is `ACODE_LANGUAGE` (`:105-108`).
- Per-instance flags: `spoken` (the "current language" flag) and `alwaysSpoken`, set from misc-text keys `ALWAYS=` and `SPOKEN=` (`:113-114`, `:151-159`). `beingSpoken` returns `alwaysSpoken || spoken` (`:121-124`).
- Data hooks that subclasses override: `translationHash(lang)` (word to word, default empty, `:133-136`); `translationLists(lang)` (syllable pools indexed by word length, default empty, `:139-142`); `languagesSupported()` (the IDs this tongue can act as a translator for, `:162-167`); `isANaturalLanguage()` (`:145-148`); `getVerb()` and `getTranslationVerb()` (both empty by default, `:281-289`).
- Training cost comes from `CMProps.getLangSkillGainCost(ID())` (`:99-102`).
- Learning cap: `canBeLearnedBy` asks `CMLib.ableComponents().getSpecialSkillRemainder(student, this).languageSkills()` and refuses at zero, with messages "can not learn any more languages" (`:697`) and "You have learned the maximum ... languages" (`:698`). `teach` reports the remaining count (`:705-717`). The per-race or per-class limit itself is in another bucket (unverified here).

### 2. Choosing the spoken language

`invoke` (`:719-757`), when not auto-invoked, walks the mob's effects. The chosen language gets `setBeingSpoken(true)` and every other language gets `false` (`:737-740`), so exactly one is active. Monsters get proficiency 100 on every language effect they carry (`:731-732`). Messages: "You are now speaking X." (`:749`), "You were already speaking X." (`:747`), or "You are now speaking Common." when the mob has no such language (`:752`).

### 3. Word translation

`translate(language, word)` (`:212-237`):

1. Dictionary lookup on the upper-cased word, with the case of the original restored by `fixCase` (`:214-215`).
2. If the word is the name of a player (`CMLib.players().getPlayerAllHosts(word)`), it is returned unchanged (`:216-218`).
3. Otherwise the syllable pool for that length is used: `translationVector.get(word.length() - 1)` (`:225`). Words longer than the table fall back to the last pool (`:227-231`). The result is `choices[abs(word.toLowerCase().hashCode()) % choices.length]` (`:234`).

The third step is deterministic: the same English word always produces the same invented word in a given tongue, with no stored translation. An empty pool returns the word as-is (`:232-233`).

### 4. Garbling and scrambling

Two different operations:

- `messChars(language, words, numToMess)` (`:252-278`) garbles letters. It halves the count first (`:254`), then picks random positions (`:262`). A vowel is replaced by a random vowel and a consonant by a random consonant (`:268-271`). A position is never changed twice (`:273`). An attempt cap of `length*100` prevents loops (`:259`).
- `scrambleAll(language, str, numToMess)` (`:292-383`) translates the text word by word. It is a small state machine: markup is skipped (`:310`), tokens with digits pass through (state 1, `:342-348`), and each run of letters goes to `translate` (`:332`, `:351`). Its `numToMess` parameter is never used (the only mention is the signature at `:292`).

**Speaker garbling number.** In `okMessage` (`:515`): `numToMess = round(numChars(str) * (100 - proficiency) / 100)`. Worked example: proficiency 0 with 20 letters gives 20, which `messChars` halves to 10 letters changed. Proficiency 100 gives 0 and nothing changes. `numChars` counts letters outside markup (`:239-250`).

### 5. The speaking path (`okMessage`, `:497-684`)

**Speaker side** (`:500-523`). When the affected mob is the source of a say, tell or channel message with no tool, the quoted words are garbled (`processSourceMessage`, `:441-460`), and the verb is swapped for the tongue's translation verb (`fixSayVerb`, `:424-439`). Others and target texts are scrambled (`processNonSourceMessages`, `:462-477`). Each spoken line gives a chance to practise (`helpProficiency`, `:520-521`, only if the speaker is alive and awake).

**Listener side** (`:536-646`). This branch only runs when the affected mob is the target of an order, buy, bid, sell, list, view, withdraw or deposit (`:542-549`). Staff are exempt through `CMSecurity` checks (`:554-556`). The spoken tongue is `msg.tool()` if it is a `Language`; for a monster it is `CMLib.utensils().getLanguageSpoken(source)` (`:559-564`); otherwise it falls back to Common, creating it at proficiency 100 if needed (`:567-575`).

- The heard tongue is this one if the IDs match, or else `getAnyTranslator` (`:403-422`). That looks at the listener's effects, then the room's effects, then carried items until one has a translator, and finally returns this effect if `languagesSupported()` contains the ID.
- **Comprehension roll** (`:591`): the listener fails if `rollPercentage()*2 > speakerProficiency + heardProficiency`. `rollPercentage()` returns 1-100 (`Libraries/Dice.java:409-411`), so the success chance is about `(pA + pB) / 200`: 100+100 always succeeds, 50+50 succeeds about half the time (this follows from the formula; not run).
- **Failure messages**: "<S-NAME> do(es) not speak X and would not understand <T-HIM-HER>." when no translator exists (`:631`), or "<T-NAME> <T-IS-ARE> having trouble understanding <T-YOUPOSS> pronunciation." otherwise (`:634`). The message is recoded as speech and a trailer is added (`:635-638`).
- **Animal orders** (`:596-630`): an animal that does not understand the tongue can still act on a word from an order list (`CMProps.ListFile.ANIMAL_ORDER_LIST`, contents unverified), and the matched command is written into the target message.

**Hearer-side translation** (`executeMsg`, `:807-857`). When the message's tool is a language with the same ID, the hearers get a trailer in their own language, "@x1 (translated from @x2)" (`:759-805`). There are three forms: `translateOthersMessage` (`:759-773`), `translateTargetMessage` (`:775-788`) and `translateChannelMessage` (`:790-805`, which skips channels flagged `NOLANGUAGE`, `:795`).

### 6. Writing and reading

- Writing (`tryLinguisticWriting`, `:479-495`): an item that already carries another language refuses new writing, with the message "@x1 is already written in @x2 and can not have @x3 writing added." (`:489`).
- After a write, `executeMsg` removes the previous language effect from the item (`:846-852`) and attaches a copy of this language (`:855`).
- Reading (`okMessage`, `:648-682`): when a non-author reads a readable item, the text is run through `messChars` and `scrambleAll`, using the reader's own proficiency (`:670-672`). The result is "It says '...'." (`:675`). If the reader knows the tongue, the original is added as a "(translated from X)" note (`:676-679`) and the target message is set to the original (`:679`).

### 7. Ticks

`StdLanguage` has no tick logic. `Semaphore` overrides `tick` (`Semaphore.java:304-325`): unless the mob is in a `Boardable` area or riding a water-based mount, it calls `invoke(mob, ..., auto=false)` on itself every tick. Read literally, the condition selects the forced re-invoke off the ship. That looks inverted, and the in-game effect is unverified (the non-auto invoke would repeat "You were already speaking" on each tick, `StdLanguage.java:747`). The base tick interval is 4 seconds (`TIME_TICK = 4000`, per the brief; not re-read here).

### 8. Medium: what the channel does to the message

Speech, signs, flags and ink differ only in which message bits they remove or set:

- **Semaphore** (`Semaphore.java:64-120`): removes `MASK_SOUND` and `MASK_MOUTH` from the source code (`:79`) and adds `MASK_MOVE`-style effects. When the speaker is in an outdoor `Boardable` area, the message is also sent to the room where the boardable item (the ship) is located, through a trailer runnable (`:95-102`). Yelling is refused: "You can't yell in semaphore." (`:140`).
- **Sign language** (`SignLanguage.java:70-150`): replaces the verbs "say(s)", "ask(s)" and "exclaim(s)" with "sign(s)" variants (`SignLanguage.java:125-127`) and sets `MASK_HANDS` in place of sound (`:141`, `:210`). It has no word dictionary, and its `wordLists` is never assigned (`:57`), so words pass through unchanged. Its written form is Braille (`:52-54`).
- **Invisible ink** (`InvisibleInk.java:58-61`): `translate` always returns the empty string, so every word of text written in it disappears for anyone who does not have it as their speaking tongue.

### 9. Animal speech base (`AnimalSpeak.java`, outside manifest)

`AnimalSpeak.translate` (`:123-`) picks a random entry from `getSounds()` (`:103`) and a random case variant (lower, capitalised, upper, or upper with "!"). The class sets `CAN_GRUNT_WHEN_STUPID` on the sense mask whenever the animal tongue is being spoken (`:115-119`). The 19 subclasses override `getSounds()` with their own list (table in section 9d below).

### 10. GenLanguage: data-defined languages

- All fields live in a static table keyed by ID (`GenLanguage.java:59-103`), so instances with the same ID share data.
- Fields: `CLASS` (ID), `TEXT` (misc), `NAME`, `WORDS` (pools split by "/", with comma lists, and `WORDS<n>` for one pool, `:313-334`), `HASHEDWORDS` (dictionary pairs, `:335-337`), `HELP` (default "This language is not yet documented.", `:76`), `INTERPRETS` (IDs this tongue can translate, `:341-348`), `NATURALLANG`, `VERB`, `TRANSVERB` (`:184-194`).
- Renaming `CLASS` moves the table and re-registers the class with `CMClass.delClass`/`addClass` (`:289-302`).
- `newInstance` copies the ID (`:150-164`). `isGeneric` is true (`:171-175`). `getAllXML`/`parseAllXML` serialise every field (`:384-412`). The source notes "lots of work to be done here" (`:177`).

### 11. Mechanisms by family

**9a. Dictionary and syllable-pool languages.** The dictionary gives words with a pool fallback for unlisted words. Data is in `translationHash` and `translationLists`.

| Tongue | Class | Verb | Unique keys | Notes |
|---|---|---|---|---|
| Drowish | `Drowish.java` | "miser(es)" (`:60`) | 3,685 (4,274 puts) | Six pools (`:68-80`); dictionary `:88-4372`; words the Drow lack stay English (`:4358-4370`) |
| Fey | `Fey.java` | "harmonize(s)" (`:67`) | 934 (951 puts) | Pools `:75-86`; "Based on" a Gevey lexicon (`:37`) |
| Elvish | `Elvish.java` | "trill(s)" (`:54`) | 524 | Pools `:68-80`; Tolkien-style vocabulary |
| Goblinese | `Goblinese.java` | "squeek(s)" (`:54`) | 426 | Pools `:68-80` |
| Orcish | `Orcish.java` | "grunt(s)" (`:54`) | 344 (414 puts) | Pools `:68-80` |
| Ignan | `Ignan.java` | "crackle(s)" (`:67`) | 229 | Pools `:72-90` (Sanskrit-like) |
| Gigantic | `Gigantic.java` | "BOOM(S)" (`:52-55`) | 88 | Pools `:64-83`; `tup()` forces capitals (`:85-91`); `okMessage` (`:93-110`) |
| Undercommon | `Undercommon.java` | "utter(s)" (`:55`) | uses Drowish's | Own pools mixing Goblin, Gnomish, Dwarven and Drow syllables (`:66-106`); `translatesLanguage` for Dwarven, Goblinese, Drowish, Gnomish (`:88-93`); proficiency divided by 5 in those four (`:99-104`); dictionary is `drowish.translationHash` (`:108-110`) |
| Druidic | `Druidic.java` | "murmur(s)" (`:54`) | none | Animal noise pools, 7 tiers (`:64-`); pool one is `{""}` (`:68`) |
| Dwarven | `Dwarven.java` | "bellow(s)" (`:54`) | none | Six pools, Khuzdul-flavoured (`:64-`) |
| Gnomish | `Gnomish.java` | "articulate(s)" (`:60`) | none | Six pools, a subset of Undercommon's (`:64-`) |

**9b. Dictionary languages with special logic.**

- **ThievesCant** (`ThievesCant.java`): verb "insinuate(s)" (`:61`). Dictionary `:101-1039`. Lines 106-237 hold verb-like cant, 238-848 general words, 849-1038 mostly phrase-level slang, including multi-word keys. The constructor maps the tongue onto the Bard class at level 10 (`:66-74`; the meaning of the last argument is unverified). Pools `:81-93`.
- **PirateTalk** (`PirateTalk.java`): no verb. `translate` runs the base translate then rewrites "-ing" to "in'" (`:68-76`). The dictionary is built from a `subExactWords` map of about 208 `put` calls (`:84-296`) and copied into `exactWords` with uppercase keys and lowercase values (`:297-302`). `languagesSupported` contains "PirateTalk", "" and "Common" (`:57`, `:59-62`).
- **Krakenspeak** (`Krakenspeak.java`): extends `Aquan`. `okMessage` forces the speech and text to capitals (`:70-`) before calling the base.
- **Semaphore** (`Semaphore.java`): `translate` returns `fixCase(word, "flag")` (`:58-60`); there is no dictionary.
- **Drunken** (`Drunken.java`): a rewrite filter (see section 9c).
- **PigLatin** (`PigLatin.java`): see section 9c.

**9c. Filters and gags (no real vocabulary).**

- **Drunken** (`Drunken.java:75-141`): every "s" not followed by "h" is a candidate (`getSChoices`, `:75-86`). One is turned into "sh" (`:109`). Half the time (`rollPercentage() < 50`, `:110`) only that happens. Otherwise one of three vowel effects is applied (`roll(1,3,0)`, `:117-140`): repeat the vowel 1-5 times, stutter with hyphens, or insert "sh" after it. Case is preserved by `fixCase`.
- **PigLatin** (`PigLatin.java:59-114`): a word starting with a vowel gets "way" (`:93-`). Otherwise the longest matching consonant cluster from `baseComboSounds` (`:59-64`, with upper and title-case forms, sorted longest first at `:66-88`) moves to the end with "ay".
- **Encrypto** (`Encrypto.java:44-122`): `letterRot = roll(1,13,0)` and `wordRot = roll(1,3,-1)` are set per instance (`:44-45`). `translate` rotates the word's characters by `wordRot` (`:77-78`) and then rotates each letter by `letterRot` (`:85-96`, mod 26). Both values are saved in misc text as `LROT=` and `WROT=` (`:110-122`). `isANaturalLanguage` is false (`:104`).
- **FoxSpeak** (`FoxSpeak.java:57-92`): `scrambleAll` ignores its input and returns one of 29 phrases at random (`:90-92`). The speaker's text is replaced entirely for listeners.
- **InvisibleInk** (`InvisibleInk.java:58-61`): see section 8.

**9d. Animal speech.** Each class is a list of sounds over `AnimalSpeak`. Verbs and lists below come from each file (`animalSounds` lines).

| Class | Verb | Sound pool (distinct entries as written) |
|---|---|---|
| DuckSpeak | "quack(s)" | quack |
| ElephantSpeak | "trumpet(s)" | trumpet, roar, moan, rumble |
| FowlSpeak | "warble(s)" | cackle, gobble, hiss, honk, quack |
| FrogSpeak | "croak(s)" | croak, ribbit, gribbit (with repeats) |
| GoatSpeak | "bleat(s)" | bleat, baa, baaa, baaaaa, baaaaaa |
| GooseSpeak | "honk(s)" | cackle, gobble, hiss, honk, quack |
| GreatBirdSpeak | "screech(es)" | scream, squawk, mew, squeal, chirrup, chirp, twitter, tweet, sing, whistle, warble, trill, cheep, click (18 entries with repeats) |
| HorseSpeak | "whinie(s)" | neigh, snort, whinny, nicker, sputter |
| MonkeySpeak | "howler(s)" | chatter, gecker, gibber, whoop, screech |
| MooseSpeak | "moo(s)" | bellow, belloow, behllouuuhw |
| OwlSpeak | "hoot(s)" | hoot (repeated), scream, screech, shriek |
| PigSpeak | "oink(s)" | snort, grunt, squeal, oink (repeated) |
| RabbitSpeak | "eep(s)" | stomp, STOMP, crrryyyy, SCREAM!!, cry, sqeeeek, chitter, purr |
| RavenSpeak | "caw(s)" | croak |
| RodentSpeak | "squeal(s)" | squeak (repeated), squeal, eek, brux |
| SheepSpeak | "bleat(s)" | bleat, baa, baaa, baaaa, baaaaa, baaaaaa |
| SnakeSpeak | "hiss(es)" | hiss, hissss, hisssss, sssss, ss, s, ssssss |
| WolfSpeak | "howl(s)" | bark, howl, cry, yell, yelp |
| WormSpeak | "squirm(s)" (`WormSpeak.java:37-40`) | mrrr, mrrmrmmmrmrm, mmmmm, rrrrm, rr, m, mrmrmrmrm (`:42-46`) |

Every one of the 19 classes overrides `getTranslationVerb` (line 52 in 17 files, line 57 in ElephantSpeak, line 37 in WormSpeak). The base verb is "growl(s)" (`AnimalSpeak.java:76-78`), used only by a subclass that does not override it. The base pool (`AnimalSpeak.java:81-101`) is a mixed list of several hundred noises; it is used when a subclass does not override `getSounds()`.

**9e. Mechanism-only channels.** Semaphore, SignLanguage, InvisibleInk, FoxSpeak and the animal families differ from the dictionary languages in that they change the output shape rather than the vocabulary. See sections 5, 8 and 9c.

## Content worth mining

These are catalogues a text game could reuse. Samples are short; full lists are in the source files.

**Drowish** (`Drowish.java`, dictionary `:88-4372`, pools `:68-80`):

| English | Drowish | Line |
|---|---|---|
| ABYSS | rendan | 112 |
| ANGEL | mirshann | 221 |
| BLOOD | vlos | 473 |
| DARK | olath | 838 |
| DEATH | aphyon | 854 |
| WATER | niar | 4254 |
| HOPE | hope (kept English) | 4364 |

The closing block (`:4358-4370`) keeps JOY, COMPASSION, HOPE, JUSTICE, MORAL, ETHIC(S) and VIRTUE(OUS) in English, with a comment that the Drow lack these concepts.

**Fey** (`Fey.java`, dictionary `:100-1050`): ALL to "laes" (`:120`), AFTERNOON to "viejluu" (`:112`), BOOK to "shubuu" (`:203`), FRIEND to "ohsle" (`:435`), SUN to "wage" (`:312`).

**Thieves' cant** (`ThievesCant.java`): cant words such as ARREST to "sanilin" (`:106`), KILL to "intan" (`:166`), GOLD to "hymur" (`:472`), MONEY to "hyman" (`:589`). The slang glossary (`:849-1038`) gives English-to-slang phrases: ABANDONED PERSON to "lurch" (`:849`), GUARD to "pig" (`:912`), MAN to "cove" (`:961`), BOOTY to "swag" (`:863`), GOLD to "yellow tin" (`:1010`), SILVER to "tin" (`:1012`), EMERALD to "green beer" (`:1015`), GALLOWS to "three legged stool" (`:906`), TIE UP to "dress up" (`:991`), NIGHT GUARD to "moon pig" (`:964`). Coins and jewels are coded as other goods, which is the most reusable idea in this file.

**Pirate** (`PirateTalk.java:85-296`): "hello" to "ahoy", "yes" to "aye", "no" to "nay", "coin" to "doubloon", "friend" to "hearty", "girl" to "lass", "boy" to "lad", "sleep" to "caulk", "food" to "grub", "understand" to "savvy", "drunk" to "three sheets to the wind", "hell" to "Davy Jones' locker", "telescope" to "spyglass", "surrender" to "strike colors", "lie" to "spin yarn".

**Animal pools**: section 9d above.

**Semaphore directions** (`Semaphore.java:193-221`): up, up-right, sideways right, down-right, up-left, sideways left, down-left, down, with "more" as the default. A random direction is picked per word, so a message reads as a string of flag positions.

**Giant** (`Gigantic.java:119-206`): numerals in Serbo-Croatian-like forms ("0" to "nola", "1" to "jedan", "2" to "dva") and short words, all in capitals.

**Fox phrases** (`FoxSpeak.java:57-87`): 29 onomatopoeic nonsense lines in the style of a fox's yip chorus. Use the idea, not the text.

**Druidic pools** (`Druidic.java:64-`): animal-noise pools grouped by length, from "hissssss" up to "gobble-gobble" and "cock-a-doodle-doo".

## Notable design ideas

- **Two-sided comprehension.** Understanding is a single roll against the sum of speaker and listener proficiency (`StdLanguage.java:591`). Two fluent speakers always understand each other; a mixed pair gets a partial chance, and nobody needs a lookup table for that.
- **Garbling scaled by skill** (`:515`, `:254`). Skill does not switch a message on or off; it removes a share of the letters, so a learner hears the shape of a sentence before the content.
- **Deterministic invented words.** A hash of the English word picks the syllable (`:234`). A tongue has consistent vocabulary without storing every mapping, and it reads the same each time.
- **Concept gaps.** Drowish keeps words the culture lacks in English (`Drowish.java:4358-4370`). Narratively strong and cheap.
- **Pidgin.** Undercommon borrows Drow vocabulary and four other tongues, and gives a fraction (one fifth) of proficiency in each (`Undercommon.java:99-104`).
- **Writing carries its language.** The language is an effect on the item (`StdLanguage.java:846-855`), so a book in Drowish is unreadable to a non-speaker, with no separate "locked" flag.
- **Medium as message bits.** Semaphore removes sound and mouth bits and relays to the room the ship is in (`Semaphore.java:79`, `:95-102`). Sign language sets hands in place of sound (`SignLanguage.java:141`). The medium decides what others notice.
- **Player names pass through** (`StdLanguage.java:216-218`), so a name is never turned into nonsense.
- **Animal orders** let a creature act on a recognised command without understanding the tongue (`:596-630`).

## Problems and cruft

- **Duplicate dictionary keys, last one wins.** Drowish has 4,274 `put` calls for 3,685 keys. ASSASSIN is set at `Drowish.java:308` (to "velg`larn") and again at `:3770` (to "velg'larn"). Orcish has 414 puts for 344 keys; ACID is set twice (`Orcish.java`, the two "tharb"/"tharm" lines). Fey has 951 puts for 934 keys (APARTMENT at `Fey.java:141-142`, BAKER at `:163-164`). ThievesCant has 933 puts for 844 keys. None of this is detected at build time.
- **Unreachable keys (multi-word).** `scrambleAll` hands `translate` one letter-run at a time (`StdLanguage.java:332`, `:351`), so a key with a space can never match. Affected: "BECAUSE OF" (`ThievesCant.java:290`), "EVERY TIME" (`:429`), "KNOCK OUT" (`:540`), "LOCK PICK" (`:563`), "IN FRONT OF" (`:522`), and most of the slang block at `:849-1038`. Only `translate` called with a whole phrase would match them, and nothing in this bucket does that.
- **Unreachable keys (apostrophe or backtick).** Input apostrophes split words (`StdLanguage.java:310-327`), so keys such as "CAN`T" (`Drowish.java:575`) and "DON`T" (`:958`) only match input that itself contains a backtick.
- **Single letters vanish in Druidic.** The one-letter pool is `{""}` (`Druidic.java:68`). `translate` returns the empty string from that pool (`StdLanguage.java:225-234`), so every one-letter word is deleted in Druidic speech.
- **AnimalSpeak dead branch and literal token.** `roll(1,5,-1)` yields 0-4 (`Dice.java:398-405`), so `case 5` (`AnimalSpeak.java:136`) never runs, and its "!" variant is lost. The sound pool contains the literal "pipe[10]" (`AnimalSpeak.java:86`), which will be spoken as written.
- **Semaphore tick reads inverted** (`Semaphore.java:304-325`). See section 7. Unverified at runtime.
- **Data errors.** Elvish maps OIL to "LIB" (`Elvish.java:395`), the only capitalised value in that dictionary. Fey has typos in keys: "BEFREIDN" (`Fey.java:183`), "FIGTH" (`:405`), "HOLIDY" (`:513`). Fey maps LOOKS to a mixed-case value "Vleemen" (`:594`). Fey also has a key "CEGMUU" (`:248`) that is not an English word.
- **Crude and slur-type entries are present** in the dictionaries and word lists (for example `Drowish.java:642` and `ThievesCant.java:85`). They are not reproduced here. Adult-themed vocabulary also appears in several dictionaries.
- **Copied methods.** `Semaphore`, `SignLanguage`, `Krakenspeak` and `Gigantic` each repeat near-identical `tup` or `translateOthersMessage` / `translateTargetMessage` / `translateChannelMessage` code from `StdLanguage` (`:759-805`). `Semaphore.java:252-296` and `SignLanguage.java:218-270` differ only in small details.
- **Static shared state.** Dictionaries are `private static` and filled lazily behind a `size()>0` check (for example `Drowish.java:85-91`). Two instances share the same map; the check is not thread-safe. Fine for one process, not for a threaded server.
- **Static coupling.** `Undercommon` holds a static `Drowish` instance (`Undercommon.java:59`) and reads its dictionary through that instance (`:108-110`). `ThievesCant` maps itself onto Bard once per JVM via a static flag (`ThievesCant.java:65-72`).
- **Multiplayer-only assumptions**: player-name check (`StdLanguage.java:216`), channel flags (`:794-795`), staff exemptions (`:554-556`), and the market codes that let a mob's language change the outcome of a sale (`:542-549`).
- **GenLanguage** is a `Gen` table with string codes (`GenLanguage.java:184-194`), a dead branch in `setStat` for ID "GenLanguage" (`:308-311`), and an admitted gap ("lots of work to be done here", `:177`).

## Takeaways for a Python SUD

1. **Language as data.** Model a tongue as a dataclass loaded from TOML or JSON: `id`, `name`, `verb`, `natural: bool`, `medium` (voice, sign, flag, ink, writing), `dictionary: dict[str, str]`, `pools: list[list[str]]` indexed by word length, `supports: set[str]`, and an optional `transform: Callable[[str], str]` for effect-style dialects. Keep the data outside the code, as CoffeeMud's `resources/` does; drop the `GenLanguage` string-code layer.
2. **Tokenise, do not scan character by character.** `re.finditer(r"[A-Za-z]+|[^A-Za-z]+", text)` gives the same word boundaries as `scrambleAll`. Keep the case-matching step (`fixCase`).
3. **Deterministic invented words, but not with `hash()`.** Python's `hash()` is randomised per process. Use `zlib.crc32(word.lower().encode())` (or `hashlib`) to pick the pool member, so the same word gives the same invented word across saves.
4. **Keep the proper-noun pass-through**, but as an explicit list of NPC and place names, not a lookup of player accounts.
5. **Two-sided comprehension**: `random.randint(1, 100) * 2 <= prof_speaker + prof_listener`. Store proficiency per character per tongue (0-100). Learning can be a simple increment on use.
6. **Garbling**: `n = round(letters * (100 - prof) / 100) // 2`, then replace `n` distinct letter positions with random letters of the same class, as `messChars` does (`StdLanguage.java:252-278`).
7. **Writing carries its language.** Store `Text(content, language_id)` on the item. A reader without the tongue gets the scrambled text, and the game can print the "(translated from X)" note.
8. **Media as flags, not bitmasks.** Give each medium a rule: voice needs the speaker to be heard, sign needs free hands, flags need line of sight (or a ship in range), ink needs a reader who has the tongue. A single-player game can check these in one function instead of rewriting message bits.
9. **Dialects are a pipeline.** Model PirateTalk, Drunken, PigLatin and the cipher as `str -> str` functions in a list, applied in order. The Fox phrase set is a language whose output ignores its input: a useful gag for an NPC who is out of their depth.
10. **Animal speech for pets and wildlife.** Use a sound pool plus case variants and a short command table (the animal-order idea). This covers creatures the player talks to without writing a full tongue.
11. **Simplify.** Drop the two-phase `CMMsg` bus and mask juggling, the duplicated dictionaries, the static singletons, and the Gen string-code property system. Keep: the two-sided roll, proficiency garbling, deterministic invented words, the concept-gap list, writing-carries-language, and the "translated from" annotation.
12. **Budget the vocabulary.** The big dictionaries (Drowish about 3,700 keys, Fey and ThievesCant about 850-930) are the expensive part. A Python SUD needs a few hundred words per tongue, or a syllable grammar that generates them. The slang-glossary idea (cant as coded names for goods) costs little and makes a criminal faction feel distinct.
13. **Single-player changes**: NPCs need their own proficiency per tongue (CoffeeMud's "monsters get 100" is a shortcut). Channels, player-name checks and the staff exemptions can go. The market-language coupling is a good idea for shopkeepers.

## Coverage

- **Read in full**: `StdLanguage.java` (858 lines), `GenLanguage.java` (413), `Fey.java` (1053), `ThievesCant.java` (1041), `Elvish.java` (618), `Semaphore.java` (365; `tick` and the processing methods read directly, the rest by grep), `SignLanguage.java` (270; head, tail and the message methods read, `translateOthers`/`Target`/`Channel` read in part), `Gigantic.java` (209; structure read, dictionary counted), `Drunken.java` (141), `Encrypto.java` (124), `PigLatin.java` (114), `Undercommon.java` (112), `Druidic.java` (86), `Dwarven.java` (84), `Gnomish.java` (84), `InvisibleInk.java` (62), `FoxSpeak.java` (95), `Krakenspeak.java` (88; structure read to `okMessage`), `DuckSpeak.java` (67), `WormSpeak.java` (52).
- **Read in part**: `Drowish.java` (4373 lines; lines 1-1065 and 4200-4374 read, the middle 3,100 lines checked by grep for non-entry lines, which found none); `Goblinese.java` (520; structure read, dictionary sampled); `Orcish.java` (509; structure read, dictionary sampled); `Ignan.java` (331; structure and word pools read, dictionary sampled); `PirateTalk.java` (305; structure read, `subExactWords` mostly read, commented-out lines at `:203-284` skipped); the 17 other animal files (sound pool and verb checked by grep, no full read).
- **Outside the manifest, read for interaction only**: `AnimalSpeak.java` (base of the animal family), `Libraries/Dice.java:398-411` (ranges of `roll` and `rollPercentage`). `Aquan` (base of `Krakenspeak`) was not read.
- **Not verified at runtime**: the comprehension odds, the Semaphore tick behaviour, the Bard mapping's last argument, and the animal-order list contents. Each is marked in the text.
