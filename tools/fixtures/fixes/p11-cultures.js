// P11 cultures: world/cultures.ini names cultures, a source of their own. One is held at a time; adopting gives only what the
// character lacks and records it, so leaving takes away exactly that (but KEEP); what they had before is theirs. An origin's
// CULTURE=, the CULTURE command (ADOPT=COMMAND only) and a script (MPCULTURE) adopt by the same rules; LEAVE=NEVER holds but
// against a script's FORCE; WORLD CHECK names unknown abilities and an origin's unknown culture.
FIXFILE = "p11-cultures";
var CU = CM.core.Cultures, OR = CM.core.Origins, SR = CM.core.StoryRecords;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p11-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
function write(path, text) { Files.write(new java.io.File(root, path).toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var made = [];
try {
	write("cultures.ini", "[RIVERFOLK]\nNAME=River folk\nDESCRIPTION=Boat people of the delta.\nABILITIES=Fishing 40, Elvish 75, Skill_Swim 60\nTATTOOS=RIVERMARK\nKEEP=Elvish\nADOPT=COMMAND\n"
		+ "[HILLCLAN]\nNAME=Hill clan\nMASK=-RACES +Dwarf\nABILITIES=Dwarven 50\nADOPT=COMMAND\n"
		+ "[CLOISTER]\nNAME=The cloister\nABILITIES=Skill_Climb 30\nLEAVE=NEVER\n"
		+ "[BROKEN]\nABILITIES=NoSuchAbilityAtAll\n");
	write("origins.ini", "[DELTA]\nNAME=Delta-born\nCULTURE=RIVERFOLK\n[LOST]\nNAME=Lost\nCULTURE=NOSUCH\n");
	WorldFiles.configure(root);
	var probs = s(CM.core.CrossRefs.errors());
	check("problems", probs.indexOf("cultures.ini: [BROKEN]/ABILITIES: no ability NoSuchAbilityAtAll") >= 0 && probs.indexOf("origins.ini: [LOST]/CULTURE: no culture NOSUCH in cultures.ini") >= 0, probs);
	var P = CMClass.getMOB("StdMOB"); P.setName("Fixp11Pip"); P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	P.baseCharStats().setMyRace(CMClass.getRace("Human")); P.recoverCharStats(); P.bringToLife(M.location(), true); made.push(P);
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); P.setSession(VS); VS.setMob(P);
	var seen = 0;
	function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
	function cmd(words) { var L = list("CULTURE"); words.split(" ").forEach(function (w) { if (w.length) L.add(w); }); said(); CMClass.getCommand("CultureCmd").execute(P, L, 0); return said(); }
	var swim = CMClass.getAbility("Skill_Swim"); swim.setProficiency(70); P.addAbility(swim);

	// at creation: the origin's culture, by the same rules; what they had already is not the culture's
	OR.choose(P, OR.find(OR.all(), "DELTA"));
	OR.apply(P);
	check("origin", s(CU.current(P).id) == "RIVERFOLK" && P.fetchAbility("Fishing") != null && P.fetchAbility("Fishing").proficiency() == 40 && P.fetchAbility("Elvish") != null
		&& P.findTattoo("RIVERMARK") != null && P.findTattoo("CULTURE_RIVERFOLK") != null, "culture " + CU.current(P));
	check("theirs-already", P.fetchAbility("Skill_Swim").proficiency() == 70 && s(CU.gave(P)).indexOf("Swim") < 0, s(CU.gave(P)));
	var mine = cmd("");
	check("shown", mine.indexOf("You are of River folk. Boat people of the delta.") >= 0 && /From it: .*Fishing/.test(mine) && mine.indexOf("but Elvish") >= 0, mine);
	var all = cmd("LIST");
	check("listed", all.indexOf("River folk: Boat people of the delta. - yours") >= 0 && all.indexOf("Hill clan is only for") >= 0
		&& all.indexOf("The cloister is not taken up by a word: it is learned in the world") >= 0, all);
	var out = cmd("ADOPT hill clan");
	check("mask", out.indexOf("Culture: Hill clan is only for") >= 0 && s(CU.current(P).id) == "RIVERFOLK", out);

	// leaving takes what it gave: not KEEP, not what was theirs
	out = cmd("LEAVE");
	check("left", out.indexOf("You leave River folk. It takes away: Fishing. You keep: Elvish.") >= 0 && P.fetchAbility("Fishing") == null && P.fetchAbility("Elvish") != null
		&& P.fetchAbility("Skill_Swim") != null && P.findTattoo("RIVERMARK") == null && CU.current(P) == null, out);
	// adopting again: the same rules; Elvish is theirs now, not the culture's
	out = cmd("ADOPT riverfolk");
	check("again", out.indexOf("You are now of River folk. It gives you: Fishing.") >= 0 && s(CU.gave(P)).indexOf("Elvish") < 0, out);

	// a script: MPCULTURE leaves the one held first, under its rules
	var clerk = scriptedMob("Fixp11clerk", "RAND_PROG 100\nMPCULTURE Fixp11Pip ADOPT CLOISTER\n~\n"); made.push(clerk);
	said(); tickMob(clerk); clerk.destroy();
	out = said();
	check("script-adopt", s(CU.current(P).id) == "CLOISTER" && P.fetchAbility("Fishing") == null && P.fetchAbility("Skill_Climb") != null && out.indexOf("You leave River folk.") >= 0, out);
	// LEAVE=NEVER: no leaving, no other culture, by command; a script's FORCE leaves it
	out = cmd("LEAVE");
	var other = cmd("ADOPT riverfolk");
	check("never", out.indexOf("The cloister cannot be left") >= 0 && other.indexOf("you are of The cloister, which cannot be left") >= 0 && s(CU.current(P).id) == "CLOISTER", out + " / " + other);
	var forcer = scriptedMob("Fixp11abbot", "RAND_PROG 100\nMPCULTURE Fixp11Pip LEAVE FORCE\n~\n"); made.push(forcer);
	tickMob(forcer); forcer.destroy();
	check("force", CU.current(P) == null && P.fetchAbility("Skill_Climb") == null, "still " + CU.current(P));

	// the history, in the story records
	var hist = SR.read(P).get("cultures").get("history");
	var kinds = []; for (var i = 0; i < hist.length; i++) kinds.push(s(hist[i].get("culture")) + " " + s(hist[i].get("what")) + " " + s(hist[i].get("how")));
	check("history", kinds.join(", ") == "RIVERFOLK adopted origin, RIVERFOLK left command, RIVERFOLK adopted command, RIVERFOLK left script, CLOISTER adopted script, CLOISTER left force", kinds.join(", "));
	function evs(m) { var e = CM.core.Provenance.recent(m), o = ""; for (var i = 0; i < e.size(); i++) o += s(e.get(i).describe()) + "\n"; return o; }
	// LEDGER: each taking up and leaving, the culture held, the last refusal
	var led = s(CM.core.Ledgers.character(P)), ev = evs(P);
	check("ledger", ev.indexOf("took up River folk (origin); it gave Fishing, Elvish") >= 0 && ev.indexOf("left The cloister (force)") >= 0
		&& s(CM.core.Provenance.lastRefusal(P).describe()).indexOf("you are of The cloister, which cannot be left") >= 0, ev + " / " + led);
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
