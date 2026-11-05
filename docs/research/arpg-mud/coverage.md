# Coverage, evidence quality and open questions

[Dossier](README.md) · [Source register](sources.csv) · [Coverage matrix](coverage.csv)

**Research cutoff: 5 November 2026 UTC.** The source register records public
references used in the guides, retrieval depth and limitations. It distinguishes
opening selected excerpts from receiving an indexed search excerpt. Neither
means an entire website or every revision of an article was read.

## What was covered

| Source/game | Coverage achieved | Limits |
| --- | --- | --- |
| Path of Exile 1 | Underlying build/combat/item/crafting/economy systems; 48 named past/current league ideas across the release sequence, with paired early leagues separated; major expansion systems; event families; 3.30 announcement | Not every balance patch, unique item, passive node, divination card, skill variant, race date or current acquisition route for legacy rewards |
| Path of Exile 2 | Early Access 0.1–0.5/0.5.5 development; present versus planned content; class/build, action, support, reservation, crafting and endgame differences; current community class roster | Not a complete patch-by-patch bug ledger or a validation of unreleased 1.0 content; roster references include indexed community material |
| We MUD Together / 3Kingdoms | Main character-development systems, multiple guild guides, linked items, professions, crafting, companions, housing, missions, reputation, skillquests, waypoints, selected dungeon mechanics and the coding primer | Large area/item/quest catalogs sampled at the system level; secrets, every walkthrough and every numerical scaler table are not replicated |
| 3Scapes | Official feature/status page; all 22 listed guild pages including the starter; relevant help and onboarding | No server login; legacy help can disagree with current positioning/branding. Upcoming features remain upcoming, and directory counts are qualified |
| Lost Souls | Game Content directories, attributes/races/cultures/traits, skills/specialties, affiliations, anatomy/death, materials/damage, bonds/chakras/talents, services and selected developer references; 19 distinctive guild pages examined more deeply | Other guilds receive directory-level coverage. The hundreds of creature/area entries, thousands of articles, all subpages and every guild power were not individually audited |
| CoffeeSUD | Existing handbook, feature inventory, authoring audit, relevant source ownership and affix/encounter/persistence contracts | No new runtime feature was implemented or tested by this research pass; the previous audit's unresolved issues remain unresolved |

“Covered” means the system is represented in the analysis and linked to an
existing capability, a proposal or an explicit decision to leave it out. It
does not mean every source game's advertised interaction was executed in play.

## Source hierarchy and interpretation

* **Publisher material:** GGG announcements/patch notes, the official filter
  reference and the publisher's Steam page establish dated changes and plans.
  Forum replies from ordinary users are not GGG announcements.
* **Official MUD site:** 3Scapes' pages establish what it currently describes
  and what it labels upcoming; they cannot prove every legacy command works.
* **Community references:** PoEDB/PoE2DB, Lost Souls' community wiki and We MUD
  Together provide detailed mechanics and observations. Version notes mirrored
  by a database are useful historical sources, with the original preferred when
  available. Page age and explicit uncertainty matter.
* **Indexed excerpts:** some inaccessible wiki pages were usable only through
  search-indexed text. Those citations are labeled in the source register and
  relevant guides. They support the limited observation made, not a claim to
  have audited the full article.
* **Original proposals:** costs, dependencies, APIs, state contracts, adaptation
  choices, examples and acceptance criteria are CoffeeSUD recommendations.
  They are not statements that the source games implement those contracts.

The guides paraphrase mechanic families rather than reproduce source prose,
item databases or walkthroughs. They use source names for attribution while
proposing original CoffeeSUD content and terminology.

## Retrieval limits and fallbacks

| Attempted source | Observed limit | Fallback / effect on coverage |
| --- | --- | --- |
| [PoE Wiki](https://www.poewiki.net/) | Attempted full-page opens returned HTTP 403 | Indexed mechanic excerpts, official patch notes and database mirrors; full-article audit not claimed |
| [PoE2 marketing site](https://pathofexile2.com/) | Browser extraction returned a JavaScript requirement | Official forum notes and publisher's Steam listing |
| [Allflame page](https://www.pathofexile.com/allflame), [Mirage page](https://www.pathofexile.com/mirage) | Extracted pages contained only a back-to-site link | Full forum/database version notes for mechanics |
| [Community class reference](https://exiledb.com/classes) | Direct open returned an unsupported Markdown content type | Indexed roster excerpt, cross-checked against other community references and the relevant update introductions |
| Some old or guessed article URLs | Missing/unresolved pages, or a category moved during wiki migration | Followed actual directory links and canonical redirects; unresolved pages do not count as deep coverage |

The registers intentionally do not include every irrelevant search hit or a
dump of retrieved copyrighted articles. They cover the references actually
cited. `excerpt-opened` describes retrieval of selected text; `indexed-excerpt`
describes search-visible text; `rendering-limited` is a successful URL with no
usable mechanics body; `access-failed` means neither useful body nor a cited
indexed excerpt was retained. Access dates are not publication dates.

## Corrections that materially affect interpretation

* PoE1 3.29 relaxes color **matching requirements**; it still has colored
  sockets and matching benefits. “Socket colors were removed” is too broad.
* The old PoE overview's no-gold statement predates later gold/economy changes.
* PoE1's official class page says 20 in its prose but lists 21 branches,
  including the new Luminary. The dossier records the named roster and additions.
* PoE2's one-copy support restriction and repeated Cruel campaign are historical.
* PoE2 0.5 changes the main Atlas progression; earlier descriptions of choosing
  a limited main-tree build cannot be assumed current.
* Database mirrors and original notes can differ in update counts; this dossier
  avoids relying on a fixed gem count where snapshots differ.
* 3Scapes explicitly marks four headline systems upcoming. Its guild listing
  has 22 entries including the starter; older help has additional guild topics.
* We MUD Together's pages sometimes preserve old rules alongside corrections;
  Ascended is labeled unmaintained, and several Elemental help files are called
  out as outdated.
* Lost Souls explicitly lists some skills with no working effect; old alignment
  requirements and defunct guild guides must not be promoted to current rules.

## Follow-up research worth doing when selecting an implementation

1. Revisit PoE1's 3.30 reveal/release and PoE2's 1.0 after their scheduled dates.
   Add a dated supplement rather than overwriting this snapshot's evidence.
2. For a faithful activity adaptation, verify the **particular chosen revision**:
   entrance conditions, current reward sources, co-op rights, failure, resets and
   item migration. The historical survey deliberately avoids certifying every
   modern reward source.
3. For MUD-inspired content, verify live behaviors where public pages conflict:
   guild admission, exact bond bonuses, old arena commands, reboot persistence
   and future feature availability. Treat new in-game observations as new evidence.
4. For CoffeeSUD, resolve the known cache/cap failure and demonstrate a complete
   prototype before estimating a broad loot economy. Design anatomy, rebirth and
   remote markets separately if they are selected.

These are refinements to a completed system-level proposal, not unperformed
implementation work presented as finished. The dossier is intended to support
choosing ideas; it is not a clone specification for any of the five games.

## Reproduce the document checks

From the repository root:

```sh
python docs/research/arpg-mud/verify.py
```

The check verifies local links, cited-source registration, proposal IDs and
dependencies, CSV/Markdown catalog agreement, coverage rows and proposal-only
YAML labels. It writes no files. The [verification record](verification.json) states
what was checked. It does not rerun remote websites or imply runtime tests for
unimplemented features.
