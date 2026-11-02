// W18: a breath count before drowning: BREATHTICKS, warnings, Prop_AirSupply, air refills it, BREATH(), +BREATH.
FIXFILE = "w18-breath";
var PS = CM.Common.interfaces.PhyStats;
var CS = CM.Common.interfaces.CharStats;
var RM = CM.Items.interfaces.RawMaterial;
var Breath = CM.core.Breath;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var page = CMProps.instance();
var oldTicks = page.getProperty("BREATHTICKS");
page.setProperty("BREATHTICKS", "@x1/3");
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw18Temple");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixw18Temple#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var SUNK = room(0, "Fixw18 sunken hall"), POCKET = room(1, "Fixw18 air pocket");
SUNK.setAtmosphere(RM.RESOURCE_SALTWATER);
var V = CMClass.getMOB("GenMob"); V.setName("Fixw18diver");
V.baseCharStats().setStat(CS.STAT_CONSTITUTION, 12); V.baseState().setHitPoints(300); V.basePhyStats().setLevel(5);
V.recoverCharStats(); V.recoverPhyStats(); V.recoverMaxState(); V.resetToMaxState();
V.bringToLife(SUNK, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
function said() { return s(field(VS, "bout").toString()); }
var full = Breath.max(V);
var hp0 = V.curState().getHitPoints();
// (the game's own ticks may come between: tick until the breath is gone, and look at once)
for (var i = 0; (i < 10) && (Breath.remaining(V) > 0); i++) tickMob(V);
var held = V.curState().getHitPoints() == hp0, left = Breath.remaining(V);
check("hold", full == 4 && held && left == 0 && has(said(), "half your breath left") && has(said(), "nearly out of breath") && has(said(), "Your breath is gone"),
	"max " + full + " held " + held + " left " + left + ": " + said());
for (var i = 0; i < 4; i++) tickMob(V);
check("drown", V.curState().getHitPoints() < hp0, "hp " + V.curState().getHitPoints() + " of " + hp0);
// the function and the mask
clearVar("W18B");
var m = newMob("GenMob", SUNK); m.setName("Fixw18clerk");
scriptOn(m, "RAND_PROG 100\nmpsetvar FIXTEST W18B $%BREATH(Fixw18diver)%\n~\n"); tickMob(m); m.destroy();
check("function-mask", gvar("W18B") == "0" && CMLib.masking().maskCheck("+BREATH <2", V, true) && (!CMLib.masking().maskCheck("-BREATH <2", V, true)), "BREATH " + gvar("W18B"));
// air fills it again
V.location().delInhabitant(V); POCKET.bringMobHere(V, false);
tickMob(V);
check("refill", Breath.remaining(V) == 4 && has(said(), "You gasp in a breath of air."), "left " + Breath.remaining(V));
// an air bladder adds to it
var bladder = CMClass.getItem("GenItem"); bladder.setName("an air bladder");
var A = CMClass.getAbility("Prop_AirSupply"); A.setMiscText("TICKS=6"); bladder.addNonUninvokableEffect(A);
V.addItem(bladder);
check("airsupply", Breath.max(V) == 10 && Breath.remaining(V) == 10, "max " + Breath.max(V));
// BREATHTICKS=0: no breath held, as before
page.setProperty("BREATHTICKS", "0");
V.delItem(bladder);
var noBreath = Breath.max(V) == 0 && (!Breath.hold(V));
page.setProperty("BREATHTICKS", oldTicks == null ? "@x1/3" : oldTicks);
check("none", noBreath, "still held a breath");
check("help", has(CMLib.help().getHelpText("PROP_AIRSUPPLY", M, true), "TICKS") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "BREATH("), "no help");
V.setSession(null); V.destroy(); bladder.destroy();
CMLib.map().delArea(X);
done();
