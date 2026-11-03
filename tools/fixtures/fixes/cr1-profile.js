// CR1: property owned by the profile: every character of the profile owns what another owns and what @PROFILE owns; @PROFILE always exists, so a lot keeps it; PROFILEPROPERTY=true buys for the profile; MPGRANT gives deeds and vessels.
FIXFILE = "cr1-profile";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var LAW = CMLib.law(), PO = CM.core.ProfileOwner;
var R = M.location();
function account(name, players) {
	var a = CMClass.getCommon("DefaultPlayerAccount");
	a.setAccountName(name);
	var n = new java.util.ArrayList();
	players.forEach(function (p) { n.add(p); });
	a.setPlayerNames(n);
	return a;
}
var acct = account("Fixcr1profile", ["Fixcr1ann", "Fixcr1bo"]), other = account("Fixcr1other", ["Fixcr1cy"]);
var made = [];
function character(name, room, a) {
	var V = newMob("GenMob", room);
	V.setName(name);
	var VS = CMClass.getCommon("FakeSession");
	VS.initialize(null, "MEMORY");
	V.setSession(VS);
	VS.setMob(V);
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	V.playerStats().setAccount(a);
	CMLib.players().addPlayer(V);
	made.push(V);
	return V;
}
var A = character("Fixcr1ann", R, acct), B = character("Fixcr1bo", R, acct), C = character("Fixcr1cy", R, other);
var stranger = newMob("GenMob", R);
stranger.setName("a fixcr1 stranger");
// a room of the area for the lot
var X = scratchRoom();
X.setRoomID(R.getArea().Name() + "#fixcr1");
R.getArea().addProperRoom(X);
var lot = CMClass.getAbility("Prop_RoomForSale");
lot.setMiscText("@PROFILE/0");
X.addNonUninvokableEffect(lot);
var cup = CMClass.getItem("GenItem"); cup.setName("a fixcr1 cup"); X.addItem(cup);
try {
	check("token", PO.isToken("@profile") && PO.exists("@PROFILE") && !PO.exists("Fixcr1nobody") && s(PO.shown("@PROFILE")) == "the party", "token");
	check("profile owns", LAW.doesOwnThisProperty(A, X) && LAW.doesOwnThisLand(B, X) && LAW.doesHavePriviledgesHere(B, X)
		&& !LAW.doesOwnThisProperty(stranger, X) && !LAW.doesHavePriviledgesHere(stranger, X), "the profile's characters own an @PROFILE room");
	lot.updateLot(null);
	check("kept", lot.isProperlyOwned() && s(lot.getOwnerName()) == "@PROFILE", "owner '" + lot.getOwnerName() + "' after updateLot");
	// owned by one character: the other characters of the profile own it too, another profile does not
	lot.setOwnerName("Fixcr1ann");
	check("one character", LAW.doesOwnThisProperty(B, X) && LAW.doesOwnThisLand(B, X) && !LAW.doesOwnThisProperty(C, X) && !LAW.doesHavePriviledgesHere(C, X),
		"B " + LAW.doesOwnThisProperty(B, X) + ", C " + LAW.doesOwnThisProperty(C, X));
	// taking from it: no theft between the profile's characters
	X.bringMobHere(B, false); X.bringMobHere(C, false);
	var getB = CMClass.getMsg(B, cup, null, CMMsg.MSG_GET, null);
	// (C, of another profile, goes on to the PK and law checks; B never gets to them)
	check("no theft", LAW.robberyCheck(lot, getB, true) && LAW.doesHavePriviledgesHere(B, X) && !LAW.doesHavePriviledgesHere(C, X), "B " + LAW.robberyCheck(lot, getB, true));
	R.bringMobHere(B, false); R.bringMobHere(C, false);
	// PROFILEPROPERTY=true: a bought title is the profile's
	var P = CMProps.instance();
	check("buyer", s(PO.buyerName(A)) == "Fixcr1ann", s(PO.buyerName(A)));
	P.setProperty("PROFILEPROPERTY", "true");
	var buyer = "";
	try {
		lot.setOwnerName("");
		var T = CMClass.getItem("GenTitle");
		T.setLandPropertyID(X.roomID());
		var shop = newMob("GenShopkeeper", R);
		T.executeMsg(A, CMClass.getMsg(A, T, shop, CMMsg.MSG_GET, null));
		buyer = s(lot.getOwnerName());
		shop.destroy(); T.destroy();
		check("profileproperty", s(PO.buyerName(A)) == "@PROFILE" && buyer == "@PROFILE" && s(PO.buyerName(stranger)) == "a fixcr1 stranger", buyer);
	} finally { P.remove("PROFILEPROPERTY"); }
	// MPGRANT: a deed to a character, to the profile, and back
	lot.setOwnerName("");
	var deed = CMClass.getItem("GenTitle");
	deed.setLandPropertyID(X.roomID());
	R.addItem(deed);
	function run(text) { var m = scriptedMob("a fixcr1 reeve", "RAND_PROG 100\n" + text + "\nmpdisable\n~\n"); tickMob(m); m.destroy(); }
	run("mpgrant title Fixcr1ann");
	var g1 = s(lot.getOwnerName()), carried = (deed.owner() == A);
	R.addItem(deed);
	run("mpgrant title Fixcr1ann PROFILE");
	var g2 = s(lot.getOwnerName());
	R.addItem(deed);
	run("mpgrant title NOBODY");
	var g3 = s(lot.getOwnerName());
	check("mpgrant deed", g1 == "Fixcr1ann" && carried && g2 == "@PROFILE" && g3 == "", deed.Name() + ": " + g1 + " / " + carried + " / " + g2 + " / '" + g3 + "'");
	deed.destroy();
	// MPGRANT: a vessel, renamed, kept with the character, and back
	var ship = CMClass.getItem("GenBoardable");
	ship.setName("a fixcr1 skiff called [NAME]"); // as a shipyard's stock is named: rename fills in [NAME]
	ship.setDisplayText("A fixcr1 skiff called [NAME] is here.");
	R.addItem(ship);
	ship.dockHere(R);
	CMLib.map().registerWorldObjectLoaded(R.getArea(), R, ship);
	run("mpgrant skiff Fixcr1ann NAME=the Fixcrone Seawolf");
	var v1 = s(ship.getOwnerName()), vn = s(ship.Name()), kept = A.playerStats().getExtItems().isContent(ship), sav = ship.isSavable();
	run("mpgrant seawolf NOBODY");
	var v2 = s(ship.getOwnerName()), gone = !A.playerStats().getExtItems().isContent(ship);
	check("mpgrant vessel", v1 == "Fixcr1ann" && has(vn, "Fixcrone Seawolf") && kept && !sav && v2 == "" && gone,
		v1 + " / " + vn + " / " + kept + " / " + sav + " / '" + v2 + "' / " + gone);
	ship.destroy();
	// a vessel not in a room is docked near the giver (CR3's fix: dockNearest by name)
	var raft = CMClass.getItem("GenBoardable"); raft.setName("a fixcr1 raft called [NAME]");
	A.addItem(raft);
	PO.grant(raft, A, false, null, R);
	check("docked", raft.owner() == R && s(raft.getOwnerName()) == "Fixcr1ann", "owner " + raft.owner());
	raft.destroy();
	// a bad one is a script error
	var errs = s(CM.core.ScriptErrors.recent(200, null));
	run("mpgrant fixcr1nothing Fixcr1ann");
	var errs2 = s(CM.core.ScriptErrors.recent(200, null));
	check("syntax", errs2.length > errs.length && has(errs2, "MPGRANT"), errs2.substring(errs.length));
} finally {
	X.delEffect(lot);
	R.getArea().delProperRoom(X);
	cup.destroy();
	X.destroy();
	stranger.destroy();
	made.forEach(function (V) { CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy(); });
}
done();
