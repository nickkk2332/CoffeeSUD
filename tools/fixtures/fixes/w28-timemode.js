// W28: Prop_TimeMode MODE ONLYCOMBAT RESTORE MSG: a zone holds the time mode; TIMEMODE is refused there; the mode before comes back.
FIXFILE = "w28-timemode";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var GameTime = CM.core.GameTime;
var TimeLock = CM.core.TimeLock;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var orig = GameTime.isTurnBased(), origPaused = GameTime.isPaused();
var other = orig ? "REALTIME" : "TURNS";
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw28Tomb");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixw28Tomb#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
}
var GATE = room(0, "Fixw28 gate"), TOMB = room(1, "Fixw28 tomb");
link(GATE, Dir.NORTH, TOMB);
var P = CMClass.getAbility("Prop_TimeMode");
P.setMiscText("MODE=" + other + " MSG=\"Time seems to change in the fixw28 tomb.\"");
TOMB.addNonUninvokableEffect(P);
var V = CMClass.getMOB("GenMob"); V.setName("Fixw28pip"); V.bringToLife(GATE, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o; }
function words(t) { var L = new java.util.ArrayList(); t.split(" ").forEach(function (w) { L.add(w); }); return L; }
try {
	CMLib.tracking().walk(V, Dir.NORTH, false, false);
	var inside = GameTime.isTurnBased() != orig, told = has(out(), "Time seems to change in the fixw28 tomb."), before = TimeLock.previous();
	CMClass.getCommand("TimeMode").execute(V, words("TIMEMODE " + (orig ? "TURNS" : "REALTIME")), 0);
	var refused = has(out(), "Time is held") && (GameTime.isTurnBased() != orig);
	CMLib.tracking().walk(V, Dir.SOUTH, false, false);
	check("zone", inside && told && before != null && before.booleanValue() == orig && refused && GameTime.isTurnBased() == orig && TimeLock.heldBy() == null,
		"inside " + inside + " told " + told + " refused " + refused + " after " + GameTime.isTurnBased() + "/" + orig);
	// ONLYCOMBAT: held only while one fights there
	P.setMiscText("MODE=" + other + " ONLYCOMBAT=TRUE");
	CMLib.tracking().walk(V, Dir.NORTH, false, false);
	var calm = GameTime.isTurnBased() == orig;
	var foe = CMClass.getMOB("GenMob"); foe.setName("Fixw28ghoul"); foe.bringToLife(TOMB, true);
	V.setVictim(foe); TimeLock.check(V);
	var fighting = GameTime.isTurnBased() != orig;
	V.makePeace(true); foe.makePeace(true); TimeLock.check(V);
	check("onlycombat", calm && fighting && GameTime.isTurnBased() == orig, "calm " + calm + " fighting " + fighting + " after " + GameTime.isTurnBased());
	CMLib.tracking().walk(V, Dir.SOUTH, false, false);
	// RESTORE=FALSE: the zone's mode stays after
	P.setMiscText("MODE=" + other + " RESTORE=FALSE");
	CMLib.tracking().walk(V, Dir.NORTH, false, false);
	CMLib.tracking().walk(V, Dir.SOUTH, false, false);
	check("restore", GameTime.isTurnBased() != orig, "the mode came back");
	check("help", has(CMLib.help().getHelpText("PROP_TIMEMODE", M, true), "ONLYCOMBAT"), "no help");
} finally {
	GameTime.setTurnBased(orig);
	if (origPaused && !orig) GameTime.pause();
	TimeLock.restore(null);
	V.setSession(null);
	[GATE, TOMB].forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var mm = r.fetchInhabitant(i); if (mm != null) mm.destroy(); } });
	CMLib.map().delArea(X);
}
done();
