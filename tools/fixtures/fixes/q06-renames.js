// world/renames.ini: a world update renames an entry of a phase 2 rules file that characters already hold; the new name is
// put on the character, its followers and its organizations' pools (each noted in its LEDGER), and WORLD CHECK checks the file.
FIXFILE = "q06-renames";
var RF = CM.core.RulesFiles;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-q06r-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function write(name, text) { Files.write(new java.io.File(root, name).toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var G = CM.core.Guilds.government("GUILD_NPC"), clans = [], made = [];
var R = scratchRoom();
try {
	write("cultures.ini", "[RIVERKIN]\nNAME=River kin\n");
	write("formations.ini", "[WARDEN]\nNAME=Warden\nROW=0\n");
	write("talents.ini", 'TIDESENSE=TITLE="Tide-sense" LORE="X"\nDREAMS=TITLE="Dream-sight" LORE="X"\n');
	write("challenges.ini", 'TRIALS=TITLE="Trials" PREFIX="T_"\n');
	write("orgpools.ini", 'STORES=TITLE="Stores" MAX=50 MONEY=5\n');
	write("renames.ini", "# names this world used before\n[cultures.ini]\nRIVERFOLK=RIVERKIN\n[formations.ini]\nGUARD=SENTRY\nSENTRY=WARDEN\n"
		+ "[talents.ini]\nTIDES=TIDESENSE\n[challenges.ini]\nFIXOLD=TRIALS\n[orgpools.ini]\nSUPPLIES=STORES\n");
	WorldFiles.configure(root);
	check("clean", RF.problems().isEmpty(), s(RF.problems()));
	var names = RF.renames();
	check("chain", s(names.get("formations.ini").get("GUARD")) == "WARDEN", s(names));

	// a character and a follower holding the old names
	var m = newMob("GenMob", R); m.setName("Fixq06hero"); made.push(m);
	var f = newMob("GenMob", R); f.setName("Fixq06pal"); made.push(f); f["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](m);
	var data = new CM.core.MiniJSON().parseObject('{"cultures":{"current":"RIVERFOLK","history":[]},"records":{"FIXOLD":{"crossing":{"best":20,"count":1}}}}');
	CM.core.StoryRecords.write(m, data);
	m.addTattoo("CULTUREPICK_RIVERFOLK"); m.addTattoo("TALENT_TIDES"); m.addTattoo("TALENT_DREAMS_DORMANT");
	f.addTattoo("ROLE_GUARD", 50);
	var C = CMClass.getCommon("DefaultClan"); C.setName("Fixq06 Guild"); C.setGovernmentID(G.getID()); C.setMinClanMembers(0); C.create(); clans.push(C);
	C.addMember(m, G.getAcceptPos());
	C.setStat("POOLS", '{"SUPPLIES":5,"STORES":2}');
	C.setStat("POOLLEDGER", '{"serial":1,"entries":[],"givers":{"SUPPLIES":{"Fixq06hero":5}}}');

	var did = s(RF.migrateAll(m));
	check("culture", s(CM.core.Cultures.heldId(m)) == "RIVERKIN" && m.findTattoo("CULTUREPICK_RIVERKIN") != null && m.findTattoo("CULTUREPICK_RIVERFOLK") == null, did);
	check("role-follower", f.findTattoo("ROLE_WARDEN") != null && f.findTattoo("ROLE_GUARD") == null && f.findTattoo("ROLE_WARDEN").getTickDown() > 0 && s(CM.core.FormationRoles.of(f).id) == "WARDEN", did);
	check("talents", m.findTattoo("TALENT_TIDESENSE") != null && m.findTattoo("TALENT_TIDES") == null && m.findTattoo("TALENT_DREAMS_DORMANT") != null
		&& s(CM.core.Talents.decided(m, "TIDESENSE")) == "awakened", did);
	var recs = CM.core.StoryRecords.read(m).get("records");
	check("records", recs.get("TRIALS") != null && recs.get("FIXOLD") == null && s(recs.get("TRIALS").get("crossing").get("best")) == "20", s(recs));
	check("pools", CM.core.OrgPools.units(C, "STORES") == 7 && s(C.getStat("POOLS")).indexOf("SUPPLIES") < 0 && s(C.getStat("POOLLEDGER")).indexOf('"STORES"') >= 0, s(C.getStat("POOLS")) + " / " + s(C.getStat("POOLLEDGER")));
	check("said", did.indexOf("Fixq06hero: culture RIVERFOLK is RIVERKIN now (renames.ini)") >= 0 && did.indexOf("Fixq06pal: role GUARD is WARDEN now (renames.ini)") >= 0
		&& did.indexOf("Fixq06 Guild's pool SUPPLIES is STORES now (5 unit(s)) (renames.ini)") >= 0, did);
	var ev = CM.core.Provenance.recent(f), evs = "";
	for (var i = 0; i < ev.size(); i++) evs += s(ev.get(i).describe()) + "\n";
	check("ledger", evs.indexOf("role GUARD is WARDEN now") >= 0, evs);
	check("again-nothing", RF.migrateAll(m).isEmpty(), s(RF.migrateAll(m)));
	var desc = s(RF.describe());
	check("modules-rules", desc.indexOf("cultures.ini: 1 entry") >= 0 && desc.indexOf("talents.ini: 2 entries") >= 0 && desc.indexOf("formations.ini: GUARD is WARDEN now") >= 0, desc);

	// what WORLD CHECK says of a renames.ini with mistakes
	write("renames.ini", "STRAY=LINE\n[affiliations.ini]\nA=B\n[cultures.ini]\nSAME=SAME\nRIVERKIN=RIVERFOLK2\nNOPE\n[formations.ini]\nA=B\nB=A\n[talents.ini]\nDREAMS=TIDESENSE\n");
	var probs = s(RF.problems());
	check("problems", probs.indexOf("line 1 comes before any [file] section") >= 0 && probs.indexOf("[affiliations.ini] is not a rules file") >= 0
		&& probs.indexOf("SAME=SAME: a name renamed to itself") >= 0 && probs.indexOf("cultures.ini has no RIVERFOLK2") >= 0
		&& probs.indexOf("cultures.ini still has RIVERKIN") >= 0 && probs.indexOf("is not OLD=NEW: NOPE") >= 0
		&& probs.indexOf("[formations.ini] A=B: the renames go round in a circle") >= 0 && probs.indexOf("talents.ini still has DREAMS") >= 0, probs);
	var wc = s(CM.core.WorldCheck.report(CM.core.WorldCheck.run(null, new java.util.HashSet(java.util.Arrays.asList(["DATA"])))));
	check("world-check", wc.indexOf("renames.ini: [cultures.ini] SAME=SAME") >= 0, wc.substring(0, 600));
} catch (e) {
	fail("error", e);
} finally {
	made.forEach(function (x) { if (!x.amDestroyed()) x.destroy(); });
	clans.forEach(function (c) { c.destroyClan(); });
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
