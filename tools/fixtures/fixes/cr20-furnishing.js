// CR20: Prop_Furnishing: ROOMEFFECT and ROOMBEHAVIOR on the room while the item is placed there (or on the floor of an owned room), off when it leaves; COMFORT and COMFORT(); ACTS= a station, and fire.
FIXFILE = "cr20-furnishing";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), WS = CM.core.Workstations, PF = CM.Abilities.Properties.Prop_Furnishing;
var X = scratchRoom();
X.setRoomID(R.getArea().Name() + "#fixcr20");
R.getArea().addProperRoom(X);
var V = newMob("GenMob", X); V.setName("Fixcr20cook");
var things = [];
function furnishing(name, parms, placed) {
	var I = CMClass.getItem("GenItem"); I.setName(name);
	if (placed) { I.basePhyStats().setSensesMask(I.basePhyStats().sensesMask() | CM.Common.interfaces.PhyStats.SENSE_ITEMNOTGET); }
	var P = CMClass.getAbility("Prop_Furnishing"); P.setMiscText(parms); I.addNonUninvokableEffect(P);
	things.push(I);
	return I;
}
try {
	// a placed brazier lights the room, and is a fire
	var brazier = furnishing("a fixcr20 brazier", "ROOMEFFECT=Prop_RoomLit COMFORT=2 ACTS=fire,forge", true);
	X.addItem(brazier); brazier.recoverPhyStats();
	var lit = X.fetchEffect("Prop_RoomLit");
	check("room effect", lit != null && !lit.isSavable() && s(brazier.fetchEffect("Prop_Furnishing").furnishing()) == s(X), "effect " + lit);
	var cook = CMClass.getAbility("Cooking");
	check("acts", WS.has(X, "forge") && WS.has(X, "fire") && cook.getRequiredFire(V, 0) == brazier, "fire " + cook.getRequiredFire(V, 0));
	// a rack on the floor counts only once the room is owned
	var rack = furnishing("a fixcr20 trophy rack", "COMFORT=3 ROOMBEHAVIOR=Emoter(min=5 max=9 chance=50;creaks.)", false);
	X.addItem(rack); rack.recoverPhyStats();
	var c1 = PF.comfortOf(X), b1 = X.fetchBehavior("Emoter");
	var lot = CMClass.getAbility("Prop_RoomForSale"); lot.setMiscText("Fixcr20owner/100"); X.addNonUninvokableEffect(lot);
	rack.recoverPhyStats();
	var c2 = PF.comfortOf(X), b2 = X.fetchBehavior("Emoter");
	check("comfort", c1 == 2 && b1 == null && c2 == 5 && b2 != null && !b2.isSavable(), "comfort " + c1 + " -> " + c2 + ", behaviour " + b1 + " -> " + b2);
	// COMFORT() in a script
	clearVar("CR20A");
	var m = newMob("GenMob", X); m.setName("a fixcr20 butler");
	scriptOn(m, "RAND_PROG 100\nmpsetvar FIXTEST CR20A $%COMFORT(HERE)%\nmpdisable\n~\n");
	tickMob(m); m.destroy();
	check("script", gvar("CR20A") == "5", "CR20A='" + gvar("CR20A") + "'");
	// taken away: off the room
	V.moveItemTo(brazier); brazier.recoverPhyStats();
	var gone = X.fetchEffect("Prop_RoomLit") == null && !WS.has(X, "forge") && PF.comfortOf(X) == 3;
	var nb0 = X.numBehaviors(), rp = rack.fetchEffect("Prop_Furnishing"), ap = s(rp.furnishing());
	rack.destroy();
	check("leaves", gone && X.fetchBehavior("Emoter") == null, "gone " + gone + ", lit " + X.fetchEffect("Prop_RoomLit") + ", behaviours " + nb0 + " -> " + X.numBehaviors() + ", applied " + ap + " -> " + rp.furnishing() + ", affecting " + rp.affecting() + ", destroyed " + rack.amDestroyed() + ", same " + (rack.fetchEffect("Prop_Furnishing") === rp) + ", comfort " + PF.comfortOf(X));
	X.delEffect(lot);
	check("help", has(CMLib.help().getHelpText("PROP_FURNISHING", M, true), "ROOMEFFECT"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	V.destroy();
	R.getArea().delProperRoom(X);
	X.destroy();
}
done();
