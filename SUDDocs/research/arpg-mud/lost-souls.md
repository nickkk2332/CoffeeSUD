# Lost Souls: compositional characters and a simulated world

[Dossier](README.md) · [Proposal catalog](proposals.md) · [Architecture](architecture.md)

The [Lost Souls community wiki](https://wiki.lostsouls.org/) has migrated to
new article/category URLs. This review followed its public
[Game Content directory](https://wiki.lostsouls.org/articles/game-content),
then examined the major character systems and a selection of unusually rich
guilds. Dates on pages range from older help to October 2026 observations.
Player notes sometimes disagree with one another or label details unconfirmed.
Those qualifications are part of the evidence.

Lost Souls is the strongest reference here for characters whose **body,
knowledge, affiliations and relationships** all have mechanical consequences.
It complements the ARPG references especially well.

## Character construction is more than race and class

| Public system | Observed design | CoffeeSUD proposal |
| --- | --- | --- |
| [Atman](https://wiki.lostsouls.org/articles/atman) | A master account contains multiple characters; some settings are shared and others character-specific | Reuse CoffeeSUD profiles; P01/P11 define the scope of each additional unlock |
| [Attributes](https://wiki.lostsouls.org/articles/attributes) | Multiple physical, mental and perceptual attributes; size is meaningful; development has diminishing returns | P01/A23: authored curves and descriptive bands; a displayed stat must have actual consumers |
| [Races](https://wiki.lostsouls.org/articles/races) | Playable choices vary greatly and can have access requirements; the wider creature catalog is larger | A21/P11: separate anatomy/capability from origin and unlock policy; do not require paid race unlocks |
| [Cultures](https://wiki.lostsouls.org/articles/cultures) | Culture determines learned skills and languages independently of species | P11: build on origins, creation steps and language definitions; allow adoption and change over time |
| [Traits](https://wiki.lostsouls.org/categories/traits) | Traits may be numeric, categorical or boolean and can change through events | A01/A21: typed traits with a visible source and declared rules, rather than a bag of unvalidated strings |
| [Skills](https://wiki.lostsouls.org/articles/skills) | Skills can be hidden, incompatible or antagonistic; the page explicitly says some have no implemented effect | P02/Q01: distinguish known vocabulary, trainable skill and working mechanic in creator tools |
| [Specialties](https://wiki.lostsouls.org/articles/specialties) | Specialization determines skill ceilings and learning; attribute budgets and affiliation access both matter | P02: separate permission, point budget, invested rank and effective skill; preview the consequences of leaving a source |

Specialty access from several affiliations can add together, while minimum
requirements are not simply summed. That is a subtle but useful distinction:
“may invest,” “must invest,” “currently invested,” and “temporarily effective”
are four different values. CoffeeSUD's existing class/ability mappings and masks
are foundations; a general access evaluator would make combinations reliable.

## Affiliations and obligations

[Affiliations](https://wiki.lostsouls.org/categories/affiliations) include guilds
and associations with compatibility types. The
[guild directory](https://wiki.lostsouls.org/categories/guilds) describes one
primary guild, while [associations](https://wiki.lostsouls.org/categories/associations)
can be added alongside it. Compatibility, discovery and requirements still
constrain the combination.

P03 should extend existing organizations with explicit character membership
rules: exclusive slots, incompatible tags, required/forbidden disciplines,
conduct obligations and leave/rejoin effects. These are separate from diplomacy
between organizations. A hostile faction relationship does not necessarily make
dual membership mechanically impossible, and a character's false appearance is
not the same as genuine membership.

Interesting association themes include devotional orders, discipline societies,
linguistic study, scholarly office and fellowship. Most can be authored now
with organizations, teachers, masks, ranks and quests. What benefits from a new
primitive is the reliable combination and removal of their overlapping grants.

## The body is part of the rules

[Limbs](https://wiki.lostsouls.org/articles/limbs) have individual health and
functions. Disabled limbs can stop actions, severed limbs can become recoverable
objects, and injuries affect movement or senses. Overall health can conceal a
dangerously injured vital part. The page also discusses multi-headed bodies.

A21/A22 propose an **optional anatomy graph**: body parts have parent
relationships, functions, exposure, coverage, vitality and injury state. Races
can supply defaults; forms can replace or overlay them. The same system could
represent a hydra, insect, ooze, machine, amorphous spirit or armored vehicle.
Natural attacks and equipment requirements should refer to capabilities such as
grasp, locomotion or vision rather than assuming two human hands.

This is substantial engine work. CoffeeSUD already has race body parts, wear
locations, natural weapons and injury-related abilities, but that is not proof
of a general persistent per-part damage and restoration model. Prototype the
contract on one creature and one item family before touching all native combat.
A world can keep ordinary hit points and use anatomy only for descriptive or
conditional effects.

[Equipment resizing](https://wiki.lostsouls.org/articles/equipment-resizing)
shows how anatomy and material can affect NPC services. I09/P04 should permit
an armorer to handle leather, metal or enchanted matter differently, while
explaining the refusal. Ordinary equipment-level checks are not enough for a
winged quadruped or a creature that changes size during combat.

## Damage, materials and perception

The [damage directory](https://wiki.lostsouls.org/articles/damage-types) contains
both physical/esoteric types and combinations, with different descriptive
intensities. The [exotic-material directory](https://wiki.lostsouls.org/articles/exotic-materials)
illustrates materials with setting-specific identities. These are useful
references for typed vocabularies; a long list of names alone does not establish
every possible interaction.

A09/I09 should let an author declare mixtures, affinities and reaction rules.
For example, an original “stormglass” material could conduct lightning, soften
under sonic damage and be safe against acid. Define those properties once so
crafting, combat and environmental hazards consult the same data. Bound the
number of composed components and detect reaction cycles.

A23 extends CoffeeSUD's senses, lore and language presentation. An observer's
knowledge can determine whether they see “an unfamiliar alloy” or its known
name, and expertise can improve appraisal. Provide an optional mechanical
inspection mode so creators and players can understand important consequences.
Rendering must never roll a new outcome or silently advance discovery each time
the room is redrawn.

## Companion bonds as reciprocal progression

[Empathic bonds](https://wiki.lostsouls.org/articles/empathic-bonds) develop over
time and activity, granting benefits to both familiar and consort. Bond growth
and benefits can depend on different skills and conditions. Multiple bonds can
reduce growth; the page notes a cost to the consort when a familiar dies, and
warns that bonuses are not documented consistently.

P05 should represent the relationship itself as a persistent object, separate
from either creature's physical instance. It needs participants, identity,
strength, history, eligibility, granted capabilities and an interruption/death
policy. A mount, guardian spirit, symbiont and ordinary dog can then have
different bond types without separate hardcoded systems.

The interesting benefit need not be extra damage. A seabird could share coastal
navigation, a mole could reveal unstable walls, and an insect colony could
expand a settlement's gathering options. A23/P15 make those discoveries useful
without turning every familiar into another required combat actor. The current
generic-companion feature remains deferred; existing followers and recruits
do not by themselves implement this whole contract.

## Spiritual and psychic development

| Source | Distinctive behavior | Proposal |
| --- | --- | --- |
| [Chakras](https://wiki.lostsouls.org/articles/chakra) | Charged body-associated glyphs grant benefits and penalties; maintenance/acquisition differ by character access | A08/A15: optional body sockets and charge-dependent effects, with decay tied to the chosen game clock |
| [Psychic wild talents](https://wiki.lostsouls.org/articles/psychic-wild-talents) | Extraordinary powers can awaken outside an ordinary guild progression; individual abilities scale from relevant capabilities | P15/A23: latent discoveries, deterministic eligibility and an optional chance policy; avoid rolling repeatedly while a menu is open |
| [Gods](https://wiki.lostsouls.org/articles/gods) | Portfolios, symbols, temples and affiliations connect religion to the world | P10: reusable patron definitions linked to behavior and services, extending existing deity support |
| [Fates](https://wiki.lostsouls.org/articles/fates) | Community observations associate predictions with possible later consequences; details include uncertainties | G10: an original prophecy contract with explicit triggers and outcomes; do not claim these anecdotal mappings are a complete live specification |
| [Deck of Changes](https://wiki.lostsouls.org/articles/deck-of-changes) | Voluntary draws can cause significant character changes | G04/I17: a bounded high-stakes choice table; make permanent effects and recovery policy visible before commitment |

These are particularly promising for authored worlds where power is discovered
through experience and relationships. They should also be able to remain
entirely absent from a low-magic or historical world.

## Guilds worth studying in depth

| Examined guild page | Distinctive evidence | Reusable CoffeeSUD mechanism |
| --- | --- | --- |
| [Travelers](https://wiki.lostsouls.org/articles/travelers) | Progress through obstacles and assistance; bestowals, karma and travel-related capabilities | G23/P10: challenge contracts with objective, restriction, taboo, forfeit and reward; compatible noncombat challenges |
| [Aligned](https://wiki.lostsouls.org/articles/aligned) | A bond with a place supports forms grounded in art, science and disciplined practice | A02/P11: thematic ability construction and place-linked progression |
| [Ringwielders](https://wiki.lostsouls.org/articles/ringwielders) | Runes and a central power source; the source's absence can disable powers | I05/P04/P08: modular artifact abilities and explicit shared infrastructure dependencies |
| [Lightbringers](https://wiki.lostsouls.org/articles/lightbringers) | A lens stores/focuses power for varied actions and constructions | A15/I05: a personal focus with authored resource conversion and delivery modes |
| [Zetesai](https://wiki.lostsouls.org/articles/zetesai) | Symbiosis and a moving guild location | P05/P04: bonds and a guild home hosted in a traveling entity |
| [Kazarak](https://wiki.lostsouls.org/articles/kazarak) | Permanent demon/host bond with altered life and powers | A21/P05/P14: host identity and body state separated from the controlling relationship |
| [Nglethk N'keh Fl'hlgath](https://wiki.lostsouls.org/articles/nglethk-n-keh-fl-hlgath) | Shoggoth self-expression and individual growth unlock learning | A21/P01/P15: morphology and authored personal-development milestones |
| [Warbreakers](https://wiki.lostsouls.org/articles/warbreakers) | Shadow copies of a central sword support a broad agent identity | I11/P04: shared artifact lineage plus individual progression |
| [Riddlemasters](https://wiki.lostsouls.org/articles/riddlemasters) | A teacher's location controls which services are available; joining details remain partly uncertain | P04/G23: scheduled services and knowledge challenges; distinguish a documented service from unverified entry conditions |
| [Aisenshi](https://wiki.lostsouls.org/articles/aisenshi) | Devotion to a single weapon form; notes explicitly retract an old alignment requirement | I12/P02: style mastery and versioned prerequisites |
| [Coven](https://wiki.lostsouls.org/articles/coven) | Avian totemic bonds and traversal limitations | P05/A21: companion movement eligibility and meaningful recovery when it cannot follow |
| [Deep Whisperers](https://wiki.lostsouls.org/articles/deep-whisperers) | Psychic experimentation, unusual bonds and reporting experiences | P01/P05: research/data contributions as advancement |
| [Equites Serpentina](https://wiki.lostsouls.org/articles/equites-serpentina) | Horse/rider bonding | P05/P06: mounted partnership and shared action windows |
| [Tzakazhar](https://wiki.lostsouls.org/articles/tzakazhar) | Conduct toward draconic creatures affects standing and access | P03/P10: species/faction conduct events with declared witnesses and scope |
| [Aedarene](https://wiki.lostsouls.org/articles/aedarene) | Invested divine energy spent on benisons | A15/P10: a patron-specific resource and service repertoire |
| [Ordo Ignis Aeternis](https://wiki.lostsouls.org/articles/ordo-ignis-aeternis) | Fire and related energies support both combat and utility | A02/A09: shared elements with different operations and costs |
| [Ordo Purus Magicae](https://wiki.lostsouls.org/articles/ordo-purus-magicae) | Thaumaturgic research; the article is a stub | P02/P15: learned construction rules; details need further verification before a faithful adaptation |
| [Shapeshifters](https://wiki.lostsouls.org/articles/shapeshifters) | Skill-gated forms with maintenance limits and transitions through a base form | A21: a form state machine with duration, recovery and equipment policies |
| [League of Whispers](https://wiki.lostsouls.org/articles/league-of-whispers) | Intentions unlock through multiple disciplines; a crafted psychic resource powers them | A02/A15/P02: multi-skill construction and alternative resource production |

The [Traveler challenge notes](https://wiki.lostsouls.org/articles/traveler-challenges)
are a particularly useful complement to PoE's encounter escalation. A challenge
can ask a character to **change how they play**, not simply defeat tougher
enemies. CoffeeSUD can generate “deliver a message while forbidden to teleport”
or “resolve a dispute without attacking,” provided the generator proves a valid
solution and rewards only once. Full procedural constraint checking is new
work; existing quests can prototype a small authored set.

## Broader guild directory coverage

The [public directory](https://wiki.lostsouls.org/categories/guilds) also indexes
Aliavelyrae, Battleragers, Brute Squad, Erisian Liberation Front, Faradrim
il'Avathar, Hantaka, Justicars of Axa, Khatamkar, Knights of the Round Table,
Ordo Sapientiae Abyssi, Ordo Verbus Glacialis, Ordo Zephyrius Mutatoris, POEE,
Reapers, Shemsu Sutekh, The Syndicate, Ulfhednar and Verynvelyrae, alongside the
default Adventurer's Guild. These received **directory-level coverage**, not
an audit of every power. They extend the same martial, elemental, devotional,
covert and bonded-character families.

The directory separately labels Bogatyr, Doppelsoldner, Ravenspeakers and Nomos
Mentis Peransoma as rumored, and puts Ordo Maleficus, Ordo Undine Proeliatori,
Outsiders, Rainbow Magi, Red Magi, Sentinels, White Magi and Devonshire Clerics
under defunct guilds. They should not be counted as verified current playable
systems merely because old guides remain available.

## Death, services and exploration

[Dying](https://wiki.lostsouls.org/articles/dying) describes leaving a corpse
and becoming a ghost, resurrection influenced by circumstances, trauma, and a
limited-life system with beginner exceptions. P14 should allow these as
independent policies. A world might choose recoverable injuries without life
loss, or a ghost chapter without corpse equipment loss. Reaching zero lives
must be an explicit irreversible outcome, never a surprise caused by restoring
a save during resurrection.

[Mental disorders](https://wiki.lostsouls.org/articles/mental-disorders) are
documented with causes, magnitude, duration, diagnosis and treatment. The
transferable mechanism is a condition that can affect behavior/perception and
require a specific service. CoffeeSUD can use original fantastical conditions,
such as dream contamination or magical disorientation, with readable controls
and a declared cure path rather than arbitrary input disruption.

[Banking](https://wiki.lostsouls.org/articles/banking) links access, fees and
limits to organizations and capabilities. Existing banks, currencies and
organizations should provide the base; P04 adds declarative service eligibility
and explanations. [Areas](https://wiki.lostsouls.org/categories/areas) and
[creature races](https://wiki.lostsouls.org/categories/races) illustrate a very
large world with special planes, locations and nonhuman inhabitants. The review
surveyed those directories; it did not individually inspect all listed areas
or all category pages of creatures.

The [Heroes and Legends directory](https://wiki.lostsouls.org/articles/heroes-and-legends)
is a community/player-history resource, not a special combat system. It suggests
optional memorials and persistent campaign chronicles (P13/P16), without copying
personal profiles into an engine pack.

## Highest-value ideas

The public developer documentation provides additional engineering ideas.
The [Developer Primer](https://wiki.lostsouls.org/articles/developer-primer)
describes practical onboarding through a room, an NPC/item combination and an
area. The [Dev Manual](https://wiki.lostsouls.org/articles/dev-manual) links
technical contracts, while the
[directory guide](https://wiki.lostsouls.org/articles/lost-souls-directory-structure)
distinguishes content from reusable library support. That distinction should
remain explicit in CoffeeSUD packs and guides.

The [messaging manual](https://wiki.lostsouls.org/articles/man-message) describes
point-of-view events, recipient/sense filtering and alternate messages. CoffeeSUD
already has source/target/observer message infrastructure, senses and vocabulary.
A23/Q07 should extend that shared path rather than print independent combat
strings that bypass perception or language. The
[follower guide](https://wiki.lostsouls.org/articles/a-guide-to-followers) also
shows why inventory, traversal and persistence need consistent companion tools;
its older player instructions are not proof of current engine guarantees.

My first picks are P02/P03/P05: specialty access, affiliation compatibility and
reciprocal bonds. Next are G23's constrained challenges and A23's knowledge-aware
inspection. Anatomy and fully compositional body changes are distinctive but
much larger projects. Keep them optional and defer a general body simulation
until the smaller prototype demonstrates useful play and acceptable tooling.
