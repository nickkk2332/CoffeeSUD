// W11: Prop_RoomDark ABSOLUTE=TRUE (torches do not beat it), LIGHTMASK (lights that still work), SEEMASK (who still sees), SNUFF.
FIXFILE = "w11-deepdark";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var Wear = CM.Items.interfaces.Wearable;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw11Crypts");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixw11Crypts#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
}
var HALL = room(0, "Fixw11 hall"), CRYPT = room(1, "Fixw11 crypt"), CELLAR = room(2, "Fixw11 cellar");
link(HALL, Dir.EAST, CRYPT); link(HALL, Dir.WEST, CELLAR);
var deep = CMClass.getAbility("Prop_RoomDark");
deep.setMiscText("ABSOLUTE=TRUE LIGHTMASK=\"-TAGS +DAWNLANTERN\" SEEMASK=\"-RACE +Dwarf\"");
CRYPT.addNonUninvokableEffect(deep);
var plain = CMClass.getAbility("Prop_RoomDark");
CELLAR.addNonUninvokableEffect(plain);
function walker(name, R) {
	var m = CMClass.getMOB("GenMob"); m.setName(name); m.bringToLife(R, true); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	return m;
}
function torch(m) { var T = CMClass.getItem("Torch"); m.addItem(T); T.wearAt(Wear.WORN_HELD); T.light(true); T.recoverPhyStats(); m.recoverPhyStats(); return T; }
// a torch beats plain darkness, not ABSOLUTE darkness
var V = walker("Fixw11pip", CELLAR); var T1 = torch(V); CELLAR.recoverPhyStats();
var plainLit = !CMLib.flags().isInDark(CELLAR);
V.location().delInhabitant(V); CRYPT.bringMobHere(V, false); V.recoverPhyStats(); CRYPT.recoverPhyStats();
check("absolute", plainLit && T1.isLit() && CMLib.flags().isInDark(CRYPT), "plain lit " + plainLit + " crypt dark " + CMLib.flags().isInDark(CRYPT));
// LIGHTMASK: the Lantern of Dawn still works
var L = CMClass.getItem("GenItem"); L.setName("the Lantern of Dawn"); L.addTag("DAWNLANTERN");
L.basePhyStats().setDisposition(L.basePhyStats().disposition() | PS.IS_LIGHTSOURCE); L.recoverPhyStats();
var V2 = walker("Fixw11bearer", CRYPT); V2.addItem(L); L.wearAt(Wear.WORN_HELD); V2.recoverPhyStats(); CRYPT.recoverPhyStats();
var lantern = !CMLib.flags().isInDark(CRYPT);
L.unWear(); V2.delItem(L); V2.recoverPhyStats(); CRYPT.recoverPhyStats();
check("lightmask", lantern && CMLib.flags().isInDark(CRYPT), "lantern lit " + lantern);
// SEEMASK: a dwarf still sees
var D = walker("Fixw11dwarf", CRYPT); D.baseCharStats().setMyRace(CMClass.getRace("Dwarf")); D.recoverCharStats(); D.recoverPhyStats();
var H = walker("Fixw11human", CRYPT); H.baseCharStats().setMyRace(CMClass.getRace("Human")); H.recoverCharStats(); H.recoverPhyStats();
check("seemask", CMLib.flags().canSeeInDark(D) && (!CMLib.flags().canSeeInDark(H)), "dwarf " + CMLib.flags().canSeeInDark(D) + " human " + CMLib.flags().canSeeInDark(H));
// SNUFF: a lit torch goes out on the way in
deep.setMiscText("SNUFF=100 SNUFFMSG=\"Your @x1 dies in the fixw11 dark.\"");
var W = walker("Fixw11walker", HALL); var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); W.setSession(VS); VS.setMob(W);
var T2 = torch(W);
CMLib.tracking().walk(W, Dir.EAST, false, false);
var said = s(field(VS, "bout").toString());
check("snuff", W.location() == CRYPT && (!T2.isLit()) && has(said, "dies in the fixw11 dark."), "lit " + T2.isLit() + " in " + W.location().displayText() + ": " + said);
check("help", has(CMLib.help().getHelpText("PROP_ROOMDARK", M, true), "ABSOLUTE"), "no help");
W.setSession(null);
[HALL, CRYPT, CELLAR].forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var m = r.fetchInhabitant(i); if (m != null) m.destroy(); } });
CMLib.map().delArea(X);
done();
