// P06 formation roles: world/formations.ini names roles; FORMATION [name] ROLE [role] gives a member one (kept as a tattoo) and
// its row; each round of a fight, before its attacks, a member with a role reacts (INTERCEPT: draws an attacker off the one it
// guards when they are hurt enough; FALLBACK: steps behind everyone when it is) and turns on the foe its TARGET picks.
FIXFILE = "p06-roles";
var FR = CM.core.FormationRoles;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p06-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var R = scratchRoom(), made = [];
function body(name, hp) { var m = newMob("GenMob", R); m.setName(name); m.baseState().setHitPoints(hp); m.baseState().setMana(0); m.recoverMaxState(); m.resetToMaxState(); made.push(m); return m; }
function hurt(m, pct) { m.curState().setHitPoints(Math.max(1, Math.round(m.maxState().getHitPoints() * pct / 100))); }
try {
	Files.write(new java.io.File(root, "formations.ini").toPath(), new java.lang.String(
		"[GUARD]\nNAME=Guard\nROW=0\nTARGET=ATTACKER\nINTERCEPT=50\n"
		+ "[ARCHER]\nNAME=Archer\nROW=2\nTARGET=WEAKEST\nFALLBACK=30\n"
		+ "[SHIELD]\nNAME=Shield-bearer\nROW=1\nGUARDS=ARCHER\nINTERCEPT=100\n"
		+ "[ODD]\nTARGET=SIDEWAYS\nGUARDS=NOBODY\nROW=far\n").getBytes("UTF-8"));
	WorldFiles.configure(root);
	var probs = s(FR.problems()), refs = s(CM.core.CrossRefs.errors());
	check("problems", probs.indexOf("[ODD]: TARGET is one of") >= 0 && probs.indexOf("not SIDEWAYS") >= 0 && probs.indexOf("ROW is a number, not far") >= 0
		&& refs.indexOf("formations.ini: [ODD]/GUARDS: no role NOBODY in formations.ini") >= 0 && refs.indexOf("formations.ini: [SHIELD]") < 0, probs + " / " + refs);
	var L = body("Fixp06lead", 100);
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); L.setSession(VS); VS.setMob(L); L.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var seen = 0;
	function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
	var g = body("Fixp06gil", 100), a = body("Fixp06ash", 100), sh = body("Fixp06sol", 100);
	[g, a, sh].forEach(function (m) { m["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](L); });
	var F = CMClass.getCommand("Formation");
	function form(words) { var c = list("FORMATION"); words.forEach(function (w) { c.add(w); }); said(); F.execute(L, c, 0); return said(); }

	// giving roles: each takes its row; FORMATION shows them, FORMATION ROLES says how each works
	var out = form(["Fixp06gil", "ROLE", "guard"]);
	check("assigned", out.indexOf("Fixp06gil is your guard now (row 0): row 0; fights whoever attacks the leader; draws an attacker off the leader at or below 50% hit points.") >= 0
		&& g.findTattoo("ROLE_GUARD") != null && L.fetchFollowerOrder(g) == 0, out);
	form(["Fixp06ash", "ROLE", "archer"]); form(["Fixp06sol", "ROLE", "shield-bearer"]);
	check("rows", L.fetchFollowerOrder(a) == 2 && L.fetchFollowerOrder(sh) == 1 && sh.findTattoo("ROLE_SHIELD") != null, "archer " + L.fetchFollowerOrder(a) + " shield " + L.fetchFollowerOrder(sh));
	out = form([]);
	check("listed", out.indexOf("Fixp06gil (Guard)") >= 0 && out.indexOf("Fixp06ash (Archer)") >= 0 && out.indexOf("Fixp06sol (Shield-bearer)") >= 0, out);
	out = form(["ROLES"]);
	check("roles", out.indexOf("Archer (archer): row 2; fights the most wounded foe; falls back to the rear below 30% hit points") >= 0
		&& out.indexOf("Shield-bearer (shield): row 1; fights the leader's foe; draws an attacker off the archer at or below 100% hit points") >= 0, out);
	out = form(["Fixp06ash", "ROLE", "nope"]);
	check("unknown-role", out.indexOf("there is no role 'nope'") >= 0 && a.findTattoo("ROLE_ARCHER") != null, out);

	// a fight: two foes on the leader
	var x = body("Fixp06brute", 100), y = body("Fixp06runt", 100);
	hurt(y, 40);
	x.setVictim(L); y.setVictim(L); L.setVictim(x); g.setVictim(x); a.setVictim(x); sh.setVictim(x);
	// the archer turns on the weakest foe, and COMBATDEBUG says why; again, nothing changes
	CM.core.CombatDebug.watch(L, a); said();
	var did = s(FR.round(a));
	var dbg = said();
	CM.core.CombatDebug.unwatch(a);
	check("debug", dbg.indexOf("role Archer: turns on Fixp06runt from Fixp06brute (the most wounded foe, 40% hit points)") >= 0, dbg);
	check("weakest", a.getVictim() == y && did.indexOf("turned on Fixp06runt") >= 0 && s(FR.round(a)) == "", did);
	// the guard, its leader unhurt, fights an attacker of the leader and draws no one
	FR.round(g);
	check("no-intercept", x.getVictim() == L && y.getVictim() == L && (g.getVictim() == x || g.getVictim() == y), "x on " + x.getVictim() + " y on " + y.getVictim());
	// the leader below half: the guard draws one attacker on to itself, in the room's sight (the test's sessions keep a
	// message as written, before each viewer's names are put in)
	hurt(L, 40); said();
	did = s(FR.round(g));
	out = said();
	check("intercept", did.indexOf("intercepted") >= 0 && (x.getVictim() == g || y.getVictim() == g) && out.indexOf("<S-NAME> step(s) in front of <T-NAME>, drawing <O-NAME>'s attacks.") >= 0, did + " / " + out);
	// the shield-bearer guards the archer, always: it draws whoever attacks the archer
	y.setVictim(a);
	FR.round(sh);
	check("guards-role", y.getVictim() == sh && sh.getVictim() == y, "y on " + (y.getVictim() == null ? "no one" : y.getVictim().Name()));
	// falling back: the archer, hurt, steps behind everyone, once
	form(["Fixp06sol", "3"]);
	hurt(a, 20); said();
	did = s(FR.round(a));
	out = said();
	check("fallback", L.fetchFollowerOrder(a) == 4 && out.indexOf("<S-NAME> fall(s) back.") >= 0, did + " / row " + L.fetchFollowerOrder(a));
	FR.round(a);
	check("fallback-once", L.fetchFollowerOrder(a) == 4, "row " + L.fetchFollowerOrder(a));
	// healed again, it returns to its row; falling back again, the fight's end returns it
	hurt(a, 100); FR.round(a);
	check("returns", L.fetchFollowerOrder(a) == 2 && a.findTattoo("ROLEBACK") == null, "row " + L.fetchFollowerOrder(a));
	hurt(a, 20); FR.round(a);
	var fell = L.fetchFollowerOrder(a);
	a.setVictim(null);
	check("returns-after-fight", fell == 4 && L.fetchFollowerOrder(a) == 2 && a.findTattoo("ROLEBACK") == null, "fell to " + fell + ", now " + L.fetchFollowerOrder(a));
	a.setVictim(x);
	// the combat round itself runs the role: the archer, on the brute again, turns on the weakest before it attacks
	hurt(y, 30); hurt(a, 100); y.setVictim(L); a.setVictim(x); a.setActions(0);
	CMLib.combat().tickCombat(a);
	check("in-the-round", a.getVictim() == y || y.amDead(), "archer on " + a.getVictim());
	// no role: nothing
	out = form(["Fixp06ash", "ROLE", "none"]);
	check("none", out.indexOf("Fixp06ash has no role now.") >= 0 && a.findTattoo("ROLE_ARCHER") == null && s(FR.round(a)) == "", out);
	// a party character with a role: its role acts while no one plays it; SWITCH to it and the role waits, so it fights
	// whom the player tells it to; SWITCH back and the role takes over again
	var acct = CMClass.getCommon("DefaultPlayerAccount"); acct.setAccountName("Fixp06profile");
	L.playerStats().setAccount(acct); CMLib.players().addPlayer(L); acct.addNewPlayer(L);
	var pc = body("Fixp06pip", 100);
	pc.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); pc.playerStats().setAccount(acct);
	var PS = CMClass.getCommon("PartySession"); PS.initialize(null, "0"); PS.setMob(pc); pc.setSession(PS); CMLib.sessions().add(PS);
	CMLib.players().addPlayer(pc); acct.addNewPlayer(pc);
	pc["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](L);
	form(["Fixp06pip", "ROLE", "archer"]);
	var x2 = body("Fixp06ogre", 100), y2 = body("Fixp06imp", 100); hurt(y2, 10); x.setVictim(null); y.setVictim(null);
	x2.setVictim(L); y2.setVictim(L); pc.setVictim(x2);
	did = s(FR.round(pc));
	check("party-role", pc.findTattoo("ROLE_ARCHER") != null && L.fetchFollowerOrder(pc) == 2 && pc.getVictim() == y2 && did.indexOf("turned on Fixp06imp") >= 0, did);
	var SW = CMClass.getCommand("Switch");
	pc.setVictim(x2);
	SW.execute(L, list("SWITCH", "Fixp06pip"), 0);
	did = s(FR.round(pc));
	check("switch-played", pc.session() == VS && FR.played(pc) && !FR.played(L) && did == "" && pc.getVictim() == x2, "session " + pc.session() + " did " + did);
	SW.execute(pc, list("SWITCH", "Fixp06lead"), 0);
	did = s(FR.round(pc));
	check("switch-back", L.session() == VS && pc.getVictim() == y2 && did.indexOf("turned on Fixp06imp") >= 0, did);
	// travel: the leader goes to another room and the members come along with their roles and rows
	made.forEach(function (m) { m.setVictim(null); });
	FR.assign(a, "archer");
	var R2 = scratchRoom(); R2.setDisplayText("Fixes test room two");
	R2.bringMobHere(L, true);
	check("travel", pc.location() == R2 && g.location() == R2 && a.location() == R2 && L.fetchFollowerOrder(pc) == 2 && L.fetchFollowerOrder(a) == 2
		&& L.fetchFollowerOrder(g) == 0 && FR.of(pc) != null && s(FR.of(g).id) == "GUARD",
		"pip in " + pc.location() + " row " + L.fetchFollowerOrder(pc) + ", gil row " + L.fetchFollowerOrder(g));
	function evs(m) { var e = CM.core.Provenance.recent(m), o = ""; for (var i = 0; i < e.size(); i++) o += s(e.get(i).describe()) + "\n"; return o; }
	var ev = evs(g), led = s(CM.core.Ledgers.character(g));
	check("ledger", ev.indexOf("given the role Guard, row 0") >= 0 && led.indexOf("Formation role: Guard") >= 0 && evs(a).indexOf("fell back to row 4") >= 0, ev + " / " + led);
} finally {
	made.forEach(function (m) { if (m.session() != null && m.session() != VS) CMLib.sessions().remove(m.session()); if (m.playerStats() != null) { CMLib.players().delPlayer(m); m.setPlayerStats(null); } });
	made.forEach(function (m) { if (!m.amDestroyed()) { m.setVictim(null); m.destroy(); } });
	R.destroy(); if (typeof R2 != "undefined" && R2 != null) R2.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
