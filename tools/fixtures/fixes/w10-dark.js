// W10: ISDARK and ISLIT; +ROOMDARK and -ROOMDARK (dark to them); Prop_DarkDanger LOST, STUMBLE, LURKER.
FIXFILE = "w10-dark";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw10Crypts");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixw10Crypts#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
}
var HALL = room(0, "Fixw10 hall"), CRYPT = room(1, "Fixw10 crypt"), NORTH = room(2, "Fixw10 north"), SOUTH = room(3, "Fixw10 south");
link(HALL, Dir.EAST, CRYPT); link(CRYPT, Dir.NORTH, NORTH); link(CRYPT, Dir.SOUTH, SOUTH);
var dark = CMClass.getAbility("Prop_RoomDark"); CRYPT.addNonUninvokableEffect(dark); CRYPT.recoverPhyStats();
var V = CMClass.getMOB("GenMob"); V.setName("Fixw10pip"); V.bringToLife(CRYPT, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
CRYPT.recoverPhyStats();
// the functions, from a scripted mob in each room
function probe(R) {
	clearVar("W10D"); clearVar("W10L");
	var m = newMob("GenMob", R); m.setName("Fixw10clerk");
	scriptOn(m, "RAND_PROG 100\nif isdark(HERE)\n mpsetvar FIXTEST W10D yes\nendif\nif islit(HERE)\n mpsetvar FIXTEST W10L yes\nendif\n~\n");
	tickMob(m); m.destroy();
	return gvar("W10D") + "/" + gvar("W10L");
}
var inDark = probe(CRYPT), inLight = probe(HALL);
check("functions", CMLib.flags().isInDark(CRYPT) && inDark == "yes/" && inLight == "/yes", "crypt " + inDark + " hall " + inLight);
// the masks, from the character's side
var MK = CMLib.masking();
var darkMask = MK.maskCheck("+ROOMDARK", V, true), litMask = MK.maskCheck("-ROOMDARK", V, true);
V.basePhyStats().setSensesMask(V.basePhyStats().sensesMask() | PS.CAN_SEE_DARK); V.recoverPhyStats();
var seesDark = MK.maskCheck("+ROOMDARK", V, true);
V.basePhyStats().setSensesMask(V.basePhyStats().sensesMask() & ~PS.CAN_SEE_DARK); V.recoverPhyStats();
check("masks", darkMask && (!litMask) && (!seesDark) && has(MK.maskDesc("+ROOMDARK"), "dark to them"), "dark " + darkMask + " lit " + litMask + " darkvision " + seesDark);
// STUMBLE: the move fails
var P = CMClass.getAbility("Prop_DarkDanger");
P.setMiscText("STUMBLE=100% STUMBLEMSG=\"You trip over a fixw10 bone.\"");
CRYPT.addNonUninvokableEffect(P);
out();
CMLib.tracking().walk(V, Dir.NORTH, false, false);
check("stumble", V.location() == CRYPT && has(out(), "You trip over a fixw10 bone."), "in " + V.location().displayText());
// LOST: the move goes a random way
P.setMiscText("LOST=100% MSG=\"You lose your way, fixw10.\"");
CMLib.tracking().walk(V, Dir.NORTH, false, false);
var said = out();
check("lost", V.location() != CRYPT && has(said, "You lose your way, fixw10."), "in " + V.location().displayText() + ": " + said);
// LURKER: something comes out of the dark as they enter
P.setMiscText("LURKER=GenMob LURK=100% LURKMSG=\"A fixw10 ghoul lurches out of the dark!\"");
V.location().delInhabitant(V); HALL.bringMobHere(V, false);
var before = CRYPT.numInhabitants();
CMLib.tracking().walk(V, Dir.EAST, false, false);
said = out();
check("lurker", V.location() == CRYPT && CRYPT.numInhabitants() == before + 2 && has(said, "A fixw10 ghoul lurches out of the dark!"), "inhabitants " + before + " -> " + CRYPT.numInhabitants() + ": " + said);
// a light beats it: no danger for one who can see
var torch = CMClass.getItem("GenItem"); torch.setName("a fixw10 torch");
torch.basePhyStats().setDisposition(torch.basePhyStats().disposition() | PS.IS_LIGHTSOURCE); torch.recoverPhyStats();
P.setMiscText("STUMBLE=100%");
V.addItem(torch); torch.wearAt(CM.Items.interfaces.Wearable.WORN_HELD); V.recoverPhyStats(); CRYPT.recoverPhyStats();
var lit = !CMLib.flags().isInDark(CRYPT);
CMLib.tracking().walk(V, Dir.NORTH, false, false);
check("light", lit && V.location() == NORTH, "dark " + CMLib.flags().isInDark(CRYPT) + " in " + V.location().displayText());
check("help", has(CMLib.help().getHelpText("PROP_DARKDANGER", M, true), "LOST=") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "ISDARK("), "no help");
V.setSession(null);
var all = [HALL, CRYPT, NORTH, SOUTH];
all.forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var m = r.fetchInhabitant(i); if (m != null) m.destroy(); } });
CMLib.map().delArea(X);
done();
