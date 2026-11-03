// CR13: a way home: SLEEP in a Prop_HomeBeacon room (or HOME SET) makes it the start room of the party's characters with privileges there; HOMEBEACONS=true for owned rooms; HOME takes the whole party there in HOMERECALL turns, not while fighting.
FIXFILE = "cr13-home";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), P = CMProps.instance();
var acct = CMClass.getCommon("DefaultPlayerAccount");
acct.setAccountName("Fixcr13profile");
var names = new java.util.ArrayList(); names.add("Fixcr13ana"); names.add("Fixcr13ben"); acct.setPlayerNames(names);
var other = CMClass.getCommon("DefaultPlayerAccount"); other.setAccountName("Fixcr13other");
var made = [], rooms = [], sess = {}, seen = {};
function character(name, room, a) {
	var V = newMob("GenMob", room);
	V.setName(name);
	var VS = CMClass.getCommon("FakeSession");
	VS.initialize(null, "MEMORY");
	V.setSession(VS);
	VS.setMob(V);
	sess[name] = VS;
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	V.playerStats().setAccount(a);
	CMLib.players().addPlayer(V);
	made.push(V);
	return V;
}
function said(name) { var all = s(field(sess[name], "bout")), n = seen[name] || 0; seen[name] = all.length; return all.substring(n); }
function room(n, owner, beacon) {
	var X = scratchRoom();
	X.setRoomID(R.getArea().Name() + "#fixcr13" + n);
	X.setDisplayText("Fixcr13 room " + n);
	R.getArea().addProperRoom(X);
	if (owner != null) { var L = CMClass.getAbility("Prop_RoomForSale"); L.setMiscText(owner + "/100"); X.addNonUninvokableEffect(L); }
	if (beacon) X.addNonUninvokableEffect(CMClass.getAbility("Prop_HomeBeacon"));
	rooms.push(X);
	return X;
}
function cmd(V, words) { var C = new java.util.ArrayList(); words.split(" ").forEach(function (w) { C.add(w); }); CMClass.getCommand(words.split(" ")[0]).execute(V, C, 0); }
function wake(V) { cmd(V, "WAKE"); }
var X = room(1, "Fixcr13ana", true), Y = room(2, "Fixcr13ana", false);
var ana = character("Fixcr13ana", R, acct), ben = character("Fixcr13ben", R, acct), cy = character("Fixcr13cy", R, other);
ben.setFollowing(ana);
try {
	// sleeping at home: the party's characters with privileges there
	X.bringMobHere(ana, false); X.bringMobHere(ben, false); X.bringMobHere(cy, false);
	cmd(ana, "SLEEP");
	check("sleep", CMLib.flags().isSleeping(ana) && ana.getStartRoom() == X && ben.getStartRoom() == X && cy.getStartRoom() != X && has(said("Fixcr13ana"), "is your home now"),
		"asleep " + CMLib.flags().isSleeping(ana) + " ana " + ana.getStartRoom().roomID() + ", ben " + ben.getStartRoom().roomID() + ", cy " + cy.getStartRoom().roomID()
		+ " beacon " + CM.Abilities.Properties.Prop_HomeBeacon.isBeacon(X) + " priv " + CMLib.law().doesHavePriviledgesHere(ana, X) + " monster " + ana.isMonster() + " said " + said("Fixcr13ana"));
	wake(ana);
	// HOME SET: not by someone without privileges here
	said("Fixcr13cy");
	cmd(cy, "HOME SET");
	check("not yours", cy.getStartRoom() != X && has(said("Fixcr13cy"), "not yours to call home"), "cy " + cy.getStartRoom());
	// an owned room is no home, unless HOMEBEACONS=true
	Y.bringMobHere(ana, false);
	cmd(ana, "SLEEP"); wake(ana);
	var before = ana.getStartRoom();
	P.setProperty("HOMEBEACONS", "true");
	try { cmd(ana, "HOME SET"); } finally { P.remove("HOMEBEACONS"); }
	check("homebeacons", before == X && ana.getStartRoom() == Y, "before " + before + ", after " + ana.getStartRoom());
	// HOME: the whole party, in HOMERECALL turns
	cmd(ana, "HOME SET"); // (no beacon here now): refused
	X.bringMobHere(ana, false); cmd(ana, "HOME SET");
	R.bringMobHere(ana, false); R.bringMobHere(ben, false);
	said("Fixcr13ana");
	cmd(ana, "HOME");
	var G = ana.fetchEffect("GoingHome");
	var t0 = has(said("Fixcr13ana"), "3 turn(s)") && G != null && ana.location() == R;
	G.tick(ana, Tickable.TICKID_MOB); G.tick(ana, Tickable.TICKID_MOB);
	var t2 = ana.location() == R;
	G.tick(ana, Tickable.TICKID_MOB);
	check("home", t0 && t2 && ana.location() == X && ben.location() == X && ana.fetchEffect("GoingHome") == null, "set off " + t0 + ", still " + t2 + ", ana " + ana.location().roomID() + ", ben " + ben.location().roomID());
	check("at home", (function () { said("Fixcr13ana"); cmd(ana, "HOME"); return has(said("Fixcr13ana"), "You are home."); })(), "not told");
	// a fight stops it
	R.bringMobHere(ana, false); R.bringMobHere(ben, false);
	cmd(ana, "HOME");
	var G2 = ana.fetchEffect("GoingHome");
	var foe = newMob("GenMob", R); foe.setName("a fixcr13 boar");
	ben.setVictim(foe); foe.setVictim(ben);
	G2.tick(ana, Tickable.TICKID_MOB);
	check("fight", ana.fetchEffect("GoingHome") == null && ana.location() == R && has(said("Fixcr13ana"), "There is fighting"), "ana " + ana.location().roomID());
	ben.setVictim(null); foe.setVictim(null); foe.destroy();
	// HOMERECALL=0: no HOME
	P.setProperty("HOMERECALL", "0");
	try { cmd(ana, "HOME"); } finally { P.remove("HOMERECALL"); }
	check("off", ana.fetchEffect("GoingHome") == null && has(said("Fixcr13ana"), "Going home is off"), "no message");
	var h1 = s(CMLib.help().getHelpText("HOME", M, false)), h2 = s(CMLib.help().getHelpText("PROP_HOMEBEACON", M, true));
	check("help", has(h1, "HOME SET") && has(h2, "HOMEBEACONS"), h1.substring(0, 120) + " / " + h2.substring(0, 120));
} finally {
	made.forEach(function (V) { var G = V.fetchEffect("GoingHome"); if (G != null) V.delEffect(G); CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy(); });
	rooms.forEach(function (Z) { R.getArea().delProperRoom(Z); Z.destroy(); });
}
done();
