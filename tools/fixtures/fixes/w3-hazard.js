// W3: Prop_Hazard TYPE DAMAGE SAVE WARN ONSET EVERY DRAIN AFFECT PROTECT WHEN ROOMS NONPC; Prop_InstantDeath on an area wakes.
FIXFILE = "w3-hazard";
var PS = CM.Common.interfaces.PhyStats;
var CS = CM.Common.interfaces.CharStats;
var Climate = CM.Common.interfaces.Climate;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw3Pass");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(cls, i, name) { var r = CMClass.getLocale(cls); r.setRoomID("Fixw3Pass#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var PASS = room("Mountains", 0, "Fixw3 pass"), VALLEY = room("Plains", 1, "Fixw3 valley");
function walker(name, R, player) {
	var m = CMClass.getMOB("GenMob"); m.setName(name);
	m.baseCharStats().setStat(CS.STAT_SAVE_COLD, 0); m.baseCharStats().setStat(CS.STAT_SAVE_GAS, 0); m.baseCharStats().setStat(CS.STAT_SAVE_FIRE, 0); m.baseState().setHitPoints(500); m.baseState().setMovement(500);
	m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState(); m.bringToLife(R, true);
	if (player) m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	return m;
}
var V = walker("Fixw3climber", PASS, true);
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
function said() { return s(field(VS, "bout").toString()); }
var H = CMClass.getAbility("Prop_Hazard");
H.setMiscText("TYPE=COLD DAMAGE=5 SAVE=COLD ONSET=1 WARN=\"The fixw3 wind bites.\" NONPC");
PASS.addNonUninvokableEffect(H);
var npc = walker("Fixw3goat", PASS, false);
function tick(P, n) { for (var i = 0; i < n; i++) P.tick(P, TPS); }
var hp0 = V.curState().getHitPoints();
tick(H, 1);
var hp1 = V.curState().getHitPoints();
tick(H, 2);
var hp2 = V.curState().getHitPoints();
check("damage", hp1 == hp0 && hp2 < hp1 && npc.curState().getHitPoints() == npc.maxState().getHitPoints() && H.exposureOf(V)[1] == 2,
	"hp " + hp0 + " -> " + hp1 + " -> " + hp2 + ", goat " + npc.curState().getHitPoints() + ", harms " + H.exposureOf(V)[1]);
var warns = said().split("The fixw3 wind bites.").length - 1;
check("warn", warns == 1, "warned " + warns + " times");
// PROTECT: those who pass it are spared; WHEN: only in a blizzard
H.setMiscText("TYPE=COLD DAMAGE=5 PROTECT=\"-NAMES +Fixw3climber\"");
var hp3 = V.curState().getHitPoints(); tick(H, 2);
var spared = V.curState().getHitPoints() == hp3;
H.setMiscText("TYPE=COLD DAMAGE=5 WHEN=\"-WEATHER +BLIZZARD\"");
var C = X.getClimateObj();
C.setCurrentWeatherType(Climate.WEATHER_CLEAR); tick(H, 2);
var clear = V.curState().getHitPoints() == hp3;
C.setCurrentWeatherType(Climate.WEATHER_BLIZZARD); tick(H, 1);
check("masks", spared && clear && V.curState().getHitPoints() < hp3, "spared " + spared + " clear " + clear + " blizzard " + V.curState().getHitPoints());
C.setCurrentWeatherType(Climate.WEATHER_CLEAR);
// DRAIN=MOVE, EVERY: thin air saps moves every other tick
H.setMiscText("TYPE=THINAIR DAMAGE=10 EVERY=2");
var mv0 = V.curState().getMovement(), hp4 = V.curState().getHitPoints();
tick(H, 1); var mv1 = V.curState().getMovement();
tick(H, 1); var mv2 = V.curState().getMovement();
tick(H, 1); var mv3 = V.curState().getMovement();
check("drain", mv1 == mv0 - 10 && mv2 == mv1 && mv3 == mv2 - 10 && V.curState().getHitPoints() == hp4, "moves " + mv0 + " " + mv1 + " " + mv2 + " " + mv3);
// an area hazard with a room filter: only on the mountain
PASS.delEffect(H);
var AH = CMClass.getAbility("Prop_Hazard");
AH.setMiscText("TYPE=HEAT DAMAGE=4 ROOMS=\"-DOMAIN +MOUNTAINS\"");
X.addNonUninvokableEffect(AH);
var W2 = walker("Fixw3walker", VALLEY, true);
var hpV = W2.curState().getHitPoints(), hpP = V.curState().getHitPoints();
tick(AH, 1);
check("area", W2.curState().getHitPoints() == hpV && V.curState().getHitPoints() < hpP, "valley " + W2.curState().getHitPoints() + " pass " + V.curState().getHitPoints());
X.delEffect(AH);
// Prop_InstantDeath on an area wakes when a room of it is entered
var ID = CMClass.getAbility("Prop_InstantDeath");
ID.setMiscText("DAMAGE=1 DMGPCT=0");
X.addNonUninvokableEffect(ID);
var enter = CMClass.getMsg(W2, PASS, null, CMMsg.MSG_ENTER, null);
ID.executeMsg(X, enter);
var woke = field(ID, "killTrigger")[0];
X.delEffect(ID);
check("instantdeath", woke == true, "killTrigger " + woke);
check("help", has(CMLib.help().getHelpText("PROP_HAZARD", M, true), "ESCALATE"), "no help");
V.setSession(null);
[PASS, VALLEY].forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var m = r.fetchInhabitant(i); if (m != null) m.destroy(); } });
CMLib.map().delArea(X);
done();
