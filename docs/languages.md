# Languages and world vocabulary

World vocabulary files live under `world/vocab/` and ship with the world.
Learned proficiency and immersion counters are part of saved games. Files use UTF-8. `.ini` files use Java properties escaping and line
continuations unless noted below. Builders can edit languages and races through
`MODIFY LANGUAGE` and `MODIFY RACE`; their definitions save with the world's custom objects.

## Comprehension and NPC dialogue

Common is understood by default. Make a creature monolingual with:

```
Prop_Monolingual LANGUAGES=Orcish,Goblinese CONFUSED="<S-NAME> grunt(s) and shrug(s)."
```

Alternatively set `UNDERSTANDS=ONLY LANGUAGE=Orcish` on
`Prop_LanguageSpeaker`. Restrictions remove automatic Common comprehension;
learned languages and translators still work. Unknown speech does not reach
`SPEECH_PROG` or MudChat. A monolingual creature replies in its first declared
language when it has no other speaking language selected.

`CMLib.utensils().understands(mob,"Elvish")` and
`languageProficiency(mob,"Elvish")` expose the shared check. It searches known
languages and effects on the listener, carried items, and room, including
`Prop_LangTranslator`. The best available proficiency wins. GenLanguage
`INTERPRETS=Elvish(60),Sylvan(30)` multiplies the related language percentage by
the interpreter's proficiency. A language at 50% interpreting Elvish at 60%
gives 30% comprehension. Hearing, reading, and inline inscriptions use that
knowledge. Partial translations consistently reveal a subset of words. Sign Language
and Semaphore retain their visual formatting and can be understood by deaf
characters who can see the speaker.

```
NOTUNDERSTOOD_PROG ANY
mpechoat $n The guard looks baffled.
~
NOTUNDERSTOOD_PROG Common p ???
mpechoat $n The guard cannot follow your words.
~
```

This trigger runs on the listener when comprehension is zero. `$g` is the
spoken language; `$0` is the garbled text. Omitting the language matches ANY;
optional patterns match the garbled text and can be used without a language. `ISSPEAKING($n Common)` describes the
language of that event, even if the speaker changes language before a queued
response runs. `UNDERSTANDS($n Elvish)` returns true/false and
`UNDERSTANDS$($n Elvish)` returns 0–100; both can be used in script tests.

```
MPLEARNLANG $n Elvish 40
MPLEARNLANG $n Elvish +15
MPLEARNLANG $n Elvish -5
MPSAYIN Goblinese the goods are in the well
```

Learning updates both the stored ability and its live translation effect.
New languages obey the character's language limit (`MAXLANGUAGES`, class,
cultural language exemptions, and character/account bonuses); append `FORCE` to override the cap.
`MPENABLE` now also synchronizes live effects and checks the cap for a new
language. `MPSAYIN` restores the previous speaking language after one utterance,
including removing a temporary language effect it needed for that utterance.

## Invented languages

GenLanguage adds these editable, saved fields:

- `WORDFILE=desert.txt`: dictionary under `world/vocab/languages/`. Each line is
  `WORD=translation`; blank lines and `#` comments are allowed. An explicit
  `vocab/...` path is also accepted. Inline `HASHEDWORDS` takes priority.
- `SUFFIXES=S:en,ED:ka`: translated dictionary roots take these endings.
- `SYLLABLES=ka,ra,sa/lin,tor,esh`: banks separated by `/`, syllables by commas.
  Unknown words select syllables deterministically.
- `KEEPWORDS=Hakim,Qarth`: words to preserve, ignoring case.
- `KEEPNAMES=true`: preserve names and capitalized words.
- `WRITTENNAME=Glyphscript`: name used for the written language.
- `TRANSLATESCRIPT`: optional JavaScript defining `translate(word,language)`
  and optionally `garble(text,count)`. A null return falls back to normal rules.

```
function translate(word, language) {
    return String(word).split('').reverse().join('');
}
```

The rule hook runs before dictionaries and syllables, after preserved words.
It uses the existing JavaScript runtime limit and `JSCRIPTS` approval policy.
With `JSCRIPTS=1`, approve the body through `MODIFY JSCRIPT`, as for MOBPROG.
`LANGUAGE PREVIEW` shows a language's output before publishing it. Use an
ordinary character for saved learning tests: Archons receive stock skills at
full proficiency on login.

## Foreign inscriptions

Use inline language tags in descriptions, echoes, help, and other output:

```
Above the door: ^<LANG Elvish NOTE="elegant runes"^>Speak friend and enter.^</LANG^>
```

Every viewer sees the foreign text. Readers with some comprehension also see
`(you read: "..."; translated from ...)`, with unknown words still garbled.
Readers with zero comprehension see the optional note. Authored text stays
unchanged. Use language effects on readable items for a whole document.

## Immersion

Attach `Prop_LanguageImmersion` to an area, room, creature, or item:

```
LANGUAGE=Orcish HEAR=1 READ=3 CHANCE=20% START=5 MAX=60 GRANTAFTER=25
```

Use `LANGUAGE=ANY` for any language. HEAR and READ are proficiency gains per
successful exposure. CHANCE is checked per exposure; GRANTAFTER is the number
of exposures needed before an unknown language can be granted at START.
Existing knowledge grows to MAX, with live effects updated immediately. A
new language still respects the cap. Exposure counts and learned proficiency
are saved per character. Teaching items and creatures keep their exposure
history when they move between rooms.

## Glossary

`world/vocab/glossary.ini` contains whole-word replacements, one per line:

```
mana = aether
gold coin(s) = crown(s)
Hit Points = vigor; SCOPE=SCORE
spell = working; SCOPE=HELP
Archon = Warden; NOT: Archon Hall,Archon's Blade
FINAL spark = ember
```

Rules keep lower, title, and uppercase spelling and handle `(s)` plurals.
`NOT:` exempts matching names and phrases. A standalone `NOT:` line sets
exceptions for subsequent rules. `SCOPE=ALL`, `HELP`, or `SCORE` can likewise
be a standalone default or a per-rule option. Normal rules run on translated
messages and help/score output. Prefix a term with `FINAL` (or append `;FINAL`)
to apply it at final output instead. Markup attributes remain intact. Rules
run in file order; avoid circular or cascading renames.

## Local lists, races, and weapons

Attach `Prop_LocalLists` to a creature, room, or area:

```
LOAD=vocab/lists_underdark.ini
```

Or supply quoted keys inline, for example `MAGIC_WORDS="mellon,ithildin"`.
Lookup order is creature, room, area, parent areas, then stock lists. A weapon
uses its owner's context; combat supplies the wielder for unowned natural
weapons. Inline keys take priority over LOAD. Supported keys:
`TOD_CHANGE_OUTSIDE`, `TOD_CHANGE_INSIDE`, `HEALTH_CHART`, `DAMAGE_WORDS`,
`WEAPON_HIT_DESCS`, `WEAPON_MISS_DESCS`, `CORPSE_BLURBS`, `MAGIC_WORDS`, and
`GAIT_LIST`. `Prop_SkyText` keeps priority for its own time-of-day overrides.

Lists use the matching `resources/lists.ini` shape: commas separate indexed
entries, pipes separate random alternatives, and semicolons separate grid
rows. Health charts have eleven entries from near death through full health,
with `<MOB>` for the creature's name. Hit and miss lists have six slots matching
stock attack categories. Damage grids start each row with a weapon damage
type or ALL, followed by words in severity order. A matching damage type takes
priority over ALL. These words apply to ability damage as well as weapon hits. A flat damage list is also
accepted. Gait grids retain the stock gait fields.

GenRace `HEALTHCHART` overrides its health text; `HEALTHRACE` and the local/global
chart remain fallbacks. `CORPSEBLURBS` uses semicolon-separated
`cause=phrase|alternative` pairs, for example:

```
BURNING=a heap of melted springs;ALL=a pile of clockwork gears
```

Causes can be ability IDs, weapon damage/class names, or ALL. Race corpse text
takes priority over local lists, then stock corpse rules apply.

`Prop_WeaponMessages` works on weapons, including racial natural weapons:

```
HIT="<S-NAME> sear(s) <T-NAMESELF> with <TOOLNAME>: <DAMAGE>." MISS="<S-NAME> swing(s) wide." DAMAGEWORDS="singes,scorches,burns,incinerates"
```

Pipe-separated HIT/MISS alternatives are chosen randomly. Normal combat
source/target substitutions and damage tags work. Weapon text overrides local
hit/miss lists; weapon damage words override local damage words.

## Socials and help

`world/vocab/socials.txt` uses the stock tab-separated social format and loads
last. `world/vocab/socials.ini` controls which stock socials remain:

```
STOCK=BOW,NOD,SMILE,WAVE
REMOVE=CURSE
```

STOCK accepts ALL, NONE, or a comma-separated allowlist. REMOVE prunes stock
entries before world overrides load. Builder SOCIAL edits save the effective
set to the world file and select STOCK=NONE. `Prop_Socials LOAD=` accepts a
world vocab file before falling back to resources.

Stock help files load alphabetically, followed by alphabetically sorted
`world/vocab/help/*.ini`. World entries override matching keys. As with stock
help, `arc_*.ini` files contain Archon help. List hidden stock keys in
`world/vocab/help/hide.txt`, one per line, or use `HIDE=CLANS,CHANNELS` in
`vocab/help/settings.ini`. Prefix a hide key with `ARC:` to hide only Archon
help. World files can replace a hidden key.

Builder HELP edits write `world/vocab/help/world_help.ini`; AHELP edits write
`arc_world_help.ini`. Destroyed stock entries receive a hide marker. Archons
see the selected entry's source path after its text. Restart or unload the
relevant help/social cache after editing files directly.
