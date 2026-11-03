// CR16: crop rules from world/crops.txt and Prop_CropRules (area, room, seed): GROWHOURS, SEASONS, NEEDSUN, DOMAINS, WATER, YIELD, FAILMSG; Farming plants in a dark cave and grows for its hours.
FIXFILE = "cr16-crops";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var RM = CM.Items.interfaces.RawMaterial, CR = CM.core.CropRules;
var CF = new java.io.File(WorldFiles.worldDir(), "crops.txt");
var made = [], links = [];
function room(cls) { var R = CMClass.getLocale(cls); R.setRoomID(""); R.setArea(M.location().getArea()); R.setDisplayText("Fixcr16 " + cls); made.push(R); return R; }
function prop(P, text) { var A = CMClass.getAbility("Prop_CropRules"); A.setMiscText(text); P.addNonUninvokableEffect(A); return A; }
function link(A, B) { A.rawDoors()[0] = B; A.setRawExit(0, CMClass.getExit("Open")); links.push(A); }
var V = null, oldC = null, A = M.location().getArea();
try {
	java.nio.file.Files.write(CF.toPath(), new java.lang.String("# fixcr16\nWHEAT GROWHOURS=2 YIELD=2d4+1 FAILMSG=\"Fixcr16 wheat will not take here.\"\nFIXCR16GLOW SEASONS=autumn NEEDSUN=true\n").getBytes("UTF-8"));
	var cave = room("CaveRoom");
	var rp = prop(cave, "NEEDSUN=false DOMAINS=CAVE");
	// the merge: the file's row, then the room's, then the seed's
	var ru = CR.of(cave, "Wheat", "", "YIELD=10");
	check("merge", CR.anyRows() && ru.growHours == 2 && ru.needSun != null && !ru.needSun.booleanValue() && ru.domains.contains("CAVE") && s(ru.yield) == "10" && s(ru.failMsg) == "Fixcr16 wheat will not take here.",
		ru.growHours + " " + ru.needSun + " " + ru.domains + " " + ru.yield);
	var ru2 = CR.of(cave, "WHEAT", "FIXCR16GLOW", "");
	check("subtype", ru2.seasons.size() == 1 && s(ru2.seasons.iterator().next().name()) == "FALL" && ru2.needSun != null && !ru2.needSun.booleanValue(), s(ru2.seasons) + " " + ru2.needSun);
	// time and yield
	var tph = CM.core.CMProps.getTicksPerMudHour();
	var y = []; for (var i = 0; i < 20; i++) y.push(CR.yieldRoll(CR.of(cave, "WHEAT", "", ""), 1));
	var lo = Math.min.apply(null, y), hi = Math.max.apply(null, y);
	check("time and yield", CR.growTicks(ru, 50) == 2 * tph && CR.growTicks(CR.of(null, "", "", ""), 50) == 50 && lo >= 3 && hi <= 9 && CR.yieldRoll(ru, 1) == 10 && CR.yieldRoll(CR.of(null, "", "", ""), 4) == 4, lo + "-" + hi);
	// the checks: DOMAINS, the usual land, WATER
	var M1 = "sun", M2 = "land", M3 = "drought";
	var r1 = CR.refusal(cave, CR.of(cave, "", "", ""), M1, M2, M3);
	var cave2 = room("CaveRoom");
	var r2 = CR.refusal(cave2, CR.of(cave2, "", "", "NEEDSUN=false"), M1, M2, M3);
	var plain = room("Plains"), wet = room("ShallowWater");
	var r3 = CR.refusal(plain, CR.of(plain, "", "", "NEEDSUN=false WATER=1"), M1, M2, M3);
	link(plain, wet);
	var r4 = CR.refusal(plain, CR.of(plain, "", "", "NEEDSUN=false WATER=1"), M1, M2, M3);
	check("checks", r1 == null && s(r2) == "land" && has(r3, "not enough water") && r4 == null, r1 + " / " + r2 + " / " + r3 + " / " + r4);
	// the season, by the area's calendar
	oldC = A.getTimeObj(); var C = CMClass.getCommon("DefaultTimeClock"); A.setTimeObj(C);
	function monthOf(season) { for (var m = 0; m <= C.getMonthsInYear(); m++) { C.setMonth(m); if (s(C.getSeasonCode().name()) == season) return m; } return -1; }
	C.setMonth(monthOf("WINTER")); var w = CR.seasonRefusal(cave, CR.of(cave, "", "", "SEASONS=autumn FAILMSG=\"Not yet.\""));
	C.setMonth(monthOf("FALL")); var f = CR.seasonRefusal(cave, CR.of(cave, "", "", "SEASONS=autumn"));
	check("season", s(w) == "Not yet." && f == null, w + " / " + f);
	A.setTimeObj(oldC); oldC = null;
	// Farming plants wheat in the dark cave, and it grows for its hours
	V = newMob("GenMob", cave);
	V.setName("Fixcr16farmer");
	V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
	V.basePhyStats().setSensesMask(V.basePhyStats().sensesMask() | CM.Common.interfaces.PhyStats.CAN_SEE_DARK); V.recoverPhyStats(); // a cellar is dark
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
	var Fa = CMClass.getAbility("Farming"); Fa.setProficiency(100); V.addAbility(Fa); Fa = V.fetchAbility("Farming");
	var seed = CMLib.materials().makeItemResource(RM.RESOURCE_WHEAT); cave.addItem(seed);
	var C1 = new java.util.ArrayList(); C1.add("wheat");
	var ok1 = Fa.invoke(V, C1, null, false, 0);
	var E = V.fetchEffect("Farming");
	if (E != null) E.unInvoke();
	var G = cave.fetchEffect("Farming");
	check("planted", ok1 && E != null && G != null && field(G, "tickDown") == 2 * tph && s(field(G, "cropRsc")).toUpperCase() == "WHEAT", ok1 + " / " + (E != null) + " / " + (G == null ? "no crop" : field(G, "tickDown")) + " / " + s(field(VS, "bout")).slice(-200));
	if (G != null) { G.setAffectedOne(null); cave.delEffect(G); }
	check("help", has(CMLib.help().getHelpText("PROP_CROPRULES", M, true), "GROWHOURS"), "no help");
} finally {
	if (oldC != null) A.setTimeObj(oldC);
	CF["delete"]();
	links.forEach(function (R) { R.rawDoors()[0] = null; R.setRawExit(0, null); });
	if (V != null) { V.setPlayerStats(null); V.destroy(); }
	made.forEach(function (R) { for (var i = R.numItems() - 1; i >= 0; i--) R.getItem(i).destroy(); });
}
done();
