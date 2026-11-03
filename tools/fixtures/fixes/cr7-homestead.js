// CR7: Prop_Homestead: building without a deed; the first build claims the room as a lot (the builder's, or @PROFILE), MAXROOMS as Prop_ReqCapacity, PRICE at most 1 untaxed; a build that does not start takes the claim back; MASK and SKILLS; CLAIM=false; on an area.
FIXFILE = "cr7-homestead";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), LAW = CMLib.law();
var acct = CMClass.getCommon("DefaultPlayerAccount");
acct.setAccountName("Fixcr7profile");
var names = new java.util.ArrayList(); names.add("Fixcr7ana"); names.add("Fixcr7ben"); acct.setPlayerNames(names);
var made = [], rooms = [], sess = {};
function character(name, room, level) {
	var V = newMob("GenMob", room);
	V.setName(name);
	V.basePhyStats().setLevel(level); V.recoverPhyStats();
	var VS = CMClass.getCommon("FakeSession");
	VS.initialize(null, "MEMORY");
	V.setSession(VS);
	VS.setMob(V);
	sess[name] = VS;
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	V.playerStats().setAccount(acct);
	CMLib.players().addPlayer(V);
	["Construction", "Masonry"].forEach(function (id) { var A = CMClass.getAbility(id); A.setProficiency(100); V.addAbility(A); });
	made.push(V);
	return V;
}
function ridge(n, homestead) {
	var X = CMClass.getLocale("Plains");
	X.setRoomID(R.getArea().Name() + "#fixcr7" + n);
	X.setArea(R.getArea());
	X.setDisplayText("Fixcr7 ridge " + n);
	R.getArea().addProperRoom(X);
	if (homestead != null) { var H = CMClass.getAbility("Prop_Homestead"); H.setMiscText(homestead); X.addNonUninvokableEffect(H); }
	rooms.push(X);
	return X;
}
function build(V, skill, words) {
	V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState(); // building tires: each check starts rested
	var A = V.fetchAbility(skill), C = new java.util.ArrayList();
	words.split(" ").forEach(function (w) { C.add(w); });
	var ok = A.invoke(V, C, null, false, 0);
	var E = V.fetchEffect(skill); if (E != null) { E.unInvoke(); V.delEffect(E); }
	return ok;
}
function lotOf(X) { return X.fetchEffect("Prop_LotsForSale"); }
var seen = {};
function said(name) { var all = s(field(sess[name], "bout")), n = seen[name] || 0; seen[name] = all.length; return all.substring(n); }
try {
	var X1 = ridge(1, "SKILLS=Construction MAXROOMS=3");
	var ana = character("Fixcr7ana", X1, 10);
	// a build that does not start (no wood for the fence) leaves no claim
	var ok0 = build(ana, "Construction", "fence north");
	check("not started", !ok0 && lotOf(X1) == null, "built " + ok0 + ", lot " + lotOf(X1));
	// the first build claims it: a lot of hers, untaxed, with room for 3
	var ok1 = build(ana, "Construction", "title Fixcr seven camp");
	var lot = lotOf(X1), cap = X1.fetchEffect("Prop_ReqCapacity");
	check("claimed", ok1 && lot != null && s(lot.getOwnerName()) == "Fixcr7ana" && lot.getPrice() == 1 && cap != null && has(cap.text(), "rooms=3")
		&& LAW.doesOwnThisLand(ana, X1), "built " + ok1 + ", lot " + (lot == null ? null : lot.text()) + ", cap " + (cap == null ? null : cap.text()));
	check("told", has(s(field(sess["Fixcr7ana"], "bout")), "You claim this land as a homestead."), "no message");
	// someone of another profile cannot build on it now: it is hers
	var other = CMClass.getCommon("DefaultPlayerAccount"); other.setAccountName("Fixcr7other");
	var cy = character("Fixcr7cy", X1, 10); cy.playerStats().setAccount(other);
	check("hers", !build(cy, "Construction", "title Fixcr other camp") && s(lot.getOwnerName()) == "Fixcr7ana", "owner " + lot.getOwnerName());
	// SKILLS: Masonry is not allowed here; MASK: too low a level
	var X2 = ridge(2, "SKILLS=Construction");
	X2.bringMobHere(ana, false);
	said("Fixcr7ana");
	check("skills", !build(ana, "Masonry", "title Fixcr stone camp") && lotOf(X2) == null && has(said("Fixcr7ana"), "permission of the owner"), "lot " + lotOf(X2));
	var X3 = ridge(3, "MASK=\"-LEVEL +>20\"");
	X3.bringMobHere(ana, false);
	check("mask", !build(ana, "Construction", "title Fixcr low camp") && lotOf(X3) == null && has(said("Fixcr7ana"), "permission of the owner"), "lot " + lotOf(X3));
	// CLAIM=PROFILE, and PRICE
	var X4 = ridge(4, "CLAIM=PROFILE PRICE=400");
	X4.bringMobHere(ana, false);
	var ok4 = build(ana, "Construction", "title Fixcr party camp");
	check("profile", ok4 && lotOf(X4) != null && s(lotOf(X4).getOwnerName()) == "@PROFILE" && lotOf(X4).getPrice() == 400 && X4.fetchEffect("Prop_ReqCapacity") == null,
		"built " + ok4 + ", lot " + (lotOf(X4) == null ? null : lotOf(X4).text()));
	// CLAIM=false: building with no title
	var X5 = ridge(5, "CLAIM=false NOTAX");
	X5.bringMobHere(ana, false);
	var ok5 = build(ana, "Construction", "title Fixcr open camp");
	check("no claim", ok5 && lotOf(X5) == null, "built " + ok5 + ", lot " + lotOf(X5));
	// on the area: every room of it
	var X6 = ridge(6, null);
	var HA = CMClass.getAbility("Prop_Homestead"); HA.setMiscText("SKILLS=Construction"); R.getArea().addNonUninvokableEffect(HA);
	X6.bringMobHere(ana, false);
	var ok6 = build(ana, "Construction", "title Fixcr area camp");
	R.getArea().delEffect(HA);
	check("area", ok6 && lotOf(X6) != null && s(lotOf(X6).getOwnerName()) == "Fixcr7ana", "built " + ok6 + ", lot " + lotOf(X6));
	check("help", has(CMLib.help().getHelpText("PROP_HOMESTEAD", M, true), "MAXROOMS"), "no help");
} finally {
	made.forEach(function (V) { CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy(); });
	rooms.forEach(function (X) { R.getArea().delProperRoom(X); X.destroy(); });
}
done();
