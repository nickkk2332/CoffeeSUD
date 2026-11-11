// P15 latent talents: learning lore checks a character's talents again; one whose lore is all known is decided then, once, by one
// draw from the talents stream when its chance is below 100, and kept as a tattoo; reading again, checking again or a refreshed
// list never decides it again; an awakened talent teaches its ability; TALENTS says what each waits for.
FIXFILE = "p15-talents";
var LB = CM.core.LoreBook, T = CM.core.Talents, RS = CM.core.RandomStreams;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p15-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function write(path, text) { var f = new java.io.File(root, path); f.getParentFile().mkdirs(); Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var R = scratchRoom(), ann = null;
try {
	write("lore/herbs.ini", "HERBS_1=Of roots|Herbs|Roots hold.\nHERBS_2=Of leaves|Herbs|Leaves turn.\nHERBS_3=Of flowers|Herbs|Flowers open.\n");
	write("talents.ini", 'HERBALIST=TITLE="Herb-sense" LORE="HERBS_1,HERBS_2,HERBS_3" ABILITY=Herbalism PROFICIENCY=25 MESSAGE="The plants\' names come to you unbidden."\n'
		+ 'DREAMER=TITLE="Dream-sight" LORE="HERBS_1" CHANCE=0\n'
		+ 'SEER=TITLE="Second sight" LORE="HERBS_2" CHANCE=50\n'
		+ 'BROKEN=TITLE="Nothing" LORE="NOSUCH" ABILITY=NoSuchSkillAtAll\n'
			+ 'SEASONED=TITLE="Seasoned eye" LORE="HERBS_1" MASK="-LEVEL +>9"\n');
	WorldFiles.configure(root);
	var refs = s(CM.core.CrossRefs.errors());
	check("problems", refs.indexOf("talents.ini: BROKEN/ABILITY: no ability NoSuchSkillAtAll") >= 0 && refs.indexOf("talents.ini: BROKEN/LORE: no lore NOSUCH in lore/") >= 0
		&& refs.indexOf("talents.ini: HERBALIST") < 0, refs);
	ann = newMob("GenMob", R); ann.setName("Fixp15 Ann"); ann.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));

	// the first lore: the talent it completes, of chance 0, is decided dormant; the one that needs more waits
	LB.unlock(ann, "HERBS_1", false, null);
	check("dormant", s(T.decided(ann, "DREAMER")) == "dormant" && s(T.decided(ann, "HERBALIST")) == "", s(T.list(ann)));
	var listed = s(T.list(ann));
	check("waits", listed.indexOf("Herb-sense: latent; it waits for the lore of Of leaves, the lore of Of flowers") >= 0 && listed.indexOf("Dream-sight: it did not awaken in you, and will not") >= 0, listed);

	// the second: a chance talent, decided by one draw from the talents stream, once
	var at = RS.counter("talents");
	LB.unlock(ann, "HERBS_2", false, null);
	var seer = s(T.decided(ann, "SEER"));
	check("drawn-once", (seer == "awakened" || seer == "dormant") && RS.counter("talents") == at + 1, "seer " + seer + " draws " + (RS.counter("talents") - at));
	at = RS.counter("talents");
	T.recheck(ann); T.list(ann); T.recheck(ann);
	check("not-again", s(T.decided(ann, "SEER")) == seer && RS.counter("talents") == at, "checking again drew or changed it");

	// the third completes Herb-sense: awakened, the ability learned at its proficiency; reading again gives nothing more
	LB.unlock(ann, "HERBS_3", false, null);
	var herb = ann.fetchAbility("Herbalism");
	check("awakened", s(T.decided(ann, "HERBALIST")) == "awakened" && herb != null && herb.proficiency() == 25, s(T.list(ann)));
	herb.setProficiency(60);
	var again = LB.unlock(ann, "HERBS_3", false, null);
	check("read-again", !again && ann.fetchAbility("Herbalism").proficiency() == 60 && RS.counter("talents") == at, "reading again granted or drew");
	check("broken-waits", s(T.decided(ann, "BROKEN")) == "", "a talent with unknown lore was decided");
	// a talent whose mask waits on a level: decided when the level comes, without reading again
	check("level-waits", s(T.decided(ann, "SEASONED")) == "", "decided before the level");
	ann.basePhyStats().setLevel(12); ann.recoverPhyStats(); ann.recoverCharStats();
	check("level-decides", s(T.decided(ann, "SEASONED")) == "awakened", "not decided at level 12: " + s(T.list(ann)));
	function evs(m) { var e = CM.core.Provenance.recent(m), o = ""; for (var i = 0; i < e.size(); i++) o += s(e.get(i).describe()) + "\n"; return o; }
	var ev = evs(ann), led = s(CM.core.Ledgers.character(ann));
	check("ledger", ev.indexOf("awakened: Herb-sense") >= 0 && ev.indexOf("did not awaken, and will not: Dream-sight") >= 0 && led.indexOf("Herb-sense (awakened)") >= 0, ev + " / " + led);
} finally {
	if (ann != null) ann.destroy();
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
