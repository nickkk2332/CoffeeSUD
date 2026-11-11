// P03 affiliations: world/affiliations.ini tags organizations and names tags that cannot be held together, either way; joining is
// refused however it comes about, rejoining too; a KEEP mask is checked again when a member's class changes, and failing it they
// leave; a LEAVE rule takes away what leaving should (tattoos, abilities).
FIXFILE = "p03-affiliations";
var Gs = CM.core.Guilds, AF = CM.core.Affiliations;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p03-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
var G = Gs.government("GUILD_NPC"), clans = [], made = [];
function makeOrg(name) { var C = CMClass.getCommon("DefaultClan"); C.setName(name); C.setGovernmentID(G.getID()); C.setMinClanMembers(0); C.create(); clans.push(C); return C; }
function member(C, m) { return m.getClanRole(C.clanID()) != null; }
try {
	var hand = makeOrg("Fixp03 Night Hand"), watch = makeOrg("Fixp03 City Watch");
	Files.write(new java.io.File(root, "affiliations.ini").toPath(), new java.lang.String(
		"# the night hand and the watch\nTAG|Fixp03 Night Hand|OUTLAW\nTAG|Fixp03 City Watch|LAWFUL\nCONFLICT|OUTLAW|LAWFUL\n"
		+ "KEEP|Fixp03 City Watch|-CLASSES +Fighter\nLEAVE|Fixp03 Night Hand|TATTOOS=NIGHTMARK ABILITIES=Thief_Hide\nnot a rule\n").getBytes("UTF-8"));
	WorldFiles.configure(root);
	check("problems", s(AF.rules().problems).indexOf("line 7: not a rule") >= 0, s(AF.rules().problems));
	var P = CMClass.getMOB("StdMOB"); P.setName("Fixp03 Pip"); P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	P.baseCharStats().setMyRace(CMClass.getRace("Human")); P.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter")); P.recoverCharStats(); P.bringToLife(M.location(), true); made.push(P);
	var role = G.getAcceptPos();

	// joining the night hand, then the watch: refused, the two cannot be held together
	hand.addMember(P, role); P.addTattoo("NIGHTMARK"); P.addAbility(CMClass.getAbility("Thief_Hide"));
	check("joined", member(hand, P), "not in the night hand");
	watch.addMember(P, role);
	check("conflict", !member(watch, P) && s(AF.refuses(P, watch)).indexOf("cannot be held together (LAWFUL and OUTLAW)") >= 0, "joined the watch while in the night hand: " + AF.refuses(P, watch));
	// a promotion within the night hand is no joining, and is not refused
	hand.addMember(P, role > 0 ? role - 1 : role + 1);
	check("promotion", member(hand, P), "a role change was refused");

	// leaving takes away what its rule says
	hand.delMember(P);
	check("leave-policy", !member(hand, P) && P.findTattoo("NIGHTMARK") == null && P.fetchAbility("Thief_Hide") == null, "leaving kept the mark or the skill");
	watch.addMember(P, role);
	check("joined-watch", member(watch, P), "could not join the watch once out of the night hand");
	hand.addMember(P, role);
	check("rejoin-refused", !member(hand, P), "rejoined the night hand while in the watch");

	// a class change: the watch asks for fighters, so a thief leaves it
	P.baseCharStats().setCurrentClass(CMClass.getCharClass("Thief")); P.recoverCharStats();
	check("class-change", !member(watch, P), "a thief stayed in the watch");
	hand.addMember(P, role);
	check("rejoin-allowed", member(hand, P), "could not rejoin the night hand once out of the watch");
	function evs(m) { var e = CM.core.Provenance.recent(m), o = ""; for (var i = 0; i < e.size(); i++) o += s(e.get(i).describe()) + "\n"; return o; }
	var ref = CM.core.Provenance.lastRefusal(P), ev = evs(P);
	check("ledger", ref != null && s(ref.describe()).indexOf("affiliations.ini CONFLICT") >= 0 && ev.indexOf("took away") >= 0 && ev.indexOf("left Fixp03 City Watch") >= 0,
		(ref == null ? "no refusal" : s(ref.describe())) + " / " + ev);
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	clans.forEach(function (c) { c.destroyClan(); });
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
