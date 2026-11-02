// C20: WaveSpawner WAVE1= WAVE2=... NEXT=CLEAR|TICKS=n START=ENTER|CALL LOCKEXITS ONWIN= ONLOSE= REWARD= RESETAFTER=;
// WAVE_PROG n, ARENAWIN_PROG, ARENALOSE_PROG; MPWAVE; encounter: waves; the state in a save.
FIXFILE = "c20-waves";
var Dir = CM.core.Directions;
function s(x) { return "" + x; }
function entry(cls, name, mob) {
	var P = mob ? CMClass.getMOB(cls) : CMClass.getItem(cls);
	P.setName(name);
	P.recoverPhyStats();
	CMLib.catalog().addCatalog(P);
	P.destroy();
}
entry("GenMob", "a fixc20 goblin", true);
entry("GenMob", "a fixc20 archer", true);
entry("GenItem", "a fixc20 laurel", false);
var GATE = scratchRoom(), PIT = scratchRoom();
GATE.setDisplayText("Fixc20 gate"); PIT.setDisplayText("Fixc20 pit");
GATE.rawDoors()[Dir.EAST] = PIT; GATE.setRawExit(Dir.EAST, CMClass.getExit("Open"));
PIT.rawDoors()[Dir.WEST] = GATE; PIT.setRawExit(Dir.WEST, CMClass.getExit("Open"));
var V = newMob("GenMob", GATE);
V.setName("Fixc20pip");
V.baseState().setHitPoints(5000); V.recoverMaxState(); V.resetToMaxState();
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var WS = CMClass.getBehavior("WaveSpawner");
WS.setParms("WAVE1=\"catalog:a fixc20 goblin*2\" WAVE2=\"a fixc20 goblin+a fixc20 archer*2\" START=ENTER LOCKEXITS=true ONWIN=FIXC20WIN REWARD=\"a fixc20 laurel\" RESETAFTER=2");
PIT.addBehavior(WS);
scriptOn(PIT, "WAVE_PROG 2\nmpsetvar FIXTEST FIXC20W two\n~\nARENAWIN_PROG\nmpsetvar FIXTEST FIXC20R won\n~\n"
	+ "ARENALOSE_PROG\nmpsetvar FIXTEST FIXC20L lost\n~\nFUNCTION_PROG FIXC20WIN\nmpsetvar FIXTEST FIXC20F $n\n~\n");
["FIXC20W", "FIXC20R", "FIXC20L", "FIXC20F"].forEach(clearVar);
function tick(n) { for (var i = 0; i < (n || 1); i++) WS.tick(PIT, Tickable.TICKID_ROOM_BEHAVIOR); }
function inPit(name) { var n = 0; for (var i = 0; i < PIT.numInhabitants(); i++) if (s(PIT.fetchInhabitant(i).Name()) == name) n++; return n; }
function kill() { for (var i = PIT.numInhabitants() - 1; i >= 0; i--) { var m = PIT.fetchInhabitant(i); if (s(m.Name()).indexOf("fixc20") >= 0) m.destroy(); } V.makePeace(true); }
// the party walks in: the first wave
CMLib.tracking().walk(V, Dir.EAST, false, false);
tick();
var g = null; for (var i = 0; i < PIT.numInhabitants(); i++) if (s(PIT.fetchInhabitant(i).Name()) == "a fixc20 goblin") g = PIT.fetchInhabitant(i);
check("wave1", inPit("a fixc20 goblin") == 2 && s(WS.getStat("WAVE")) == "1" && g != null && g.hasTag("WAVE") && g.getVictim() == V,
	"goblins " + inPit("a fixc20 goblin") + " wave " + WS.getStat("WAVE"));
CMLib.tracking().walk(V, Dir.WEST, false, false);
check("locked", V.location() == PIT, "left the arena mid-fight");
check("state", s(WS.getStat("STATE")).indexOf("WAVE=\"1\"") >= 0 && s(WS.getStat("STATE")).indexOf("RUNNING") >= 0, s(WS.getStat("STATE")));
// wave one down: the second, and its WAVE_PROG
kill();
tick();
check("wave2", inPit("a fixc20 goblin") == 1 && inPit("a fixc20 archer") == 2 && gvar("FIXC20W") == "two", "goblins " + inPit("a fixc20 goblin") + " archers " + inPit("a fixc20 archer") + " prog '" + gvar("FIXC20W") + "'");
kill();
tick();
var laurel = V.findItem("a fixc20 laurel");
check("win", gvar("FIXC20R") == "won" && gvar("FIXC20F") == "Fixc20pip" && laurel != null && s(WS.getStat("ARENASTATE")) == "WON",
	"win '" + gvar("FIXC20R") + "' func '" + gvar("FIXC20F") + "' laurel " + laurel + " state " + WS.getStat("ARENASTATE"));
CMLib.tracking().walk(V, Dir.WEST, false, false);
check("open", V.location() == GATE, "still shut in");
tick(2);
check("resetafter", s(WS.getStat("ARENASTATE")) == "IDLE", "state " + WS.getStat("ARENASTATE"));
// NEXT=TICKS=2, and a loss: the party walks out
WS.setParms("WAVE1=\"a fixc20 goblin*3\" WAVE2=\"a fixc20 archer\" NEXT=TICKS=2");
CMLib.tracking().walk(V, Dir.EAST, false, false);
tick();
var three = inPit("a fixc20 goblin");
tick(2);
check("next-ticks", three == 3 && inPit("a fixc20 archer") == 1 && inPit("a fixc20 goblin") == 3, "goblins " + three + " archers " + inPit("a fixc20 archer"));
V.makePeace(true);
CMLib.tracking().walk(V, Dir.WEST, false, false);
tick();
check("lose", V.location() == GATE && gvar("FIXC20L") == "lost" && inPit("a fixc20 goblin") == 0 && inPit("a fixc20 archer") == 0 && s(WS.getStat("ARENASTATE")) == "IDLE",
	"lost '" + gvar("FIXC20L") + "' goblins " + inPit("a fixc20 goblin") + " state " + WS.getStat("ARENASTATE"));
// START=CALL with MPWAVE, a wave from an encounter table
var EF = CM.core.Encounters.fileOf("fixc20boss");
EF.getParentFile().mkdirs();
java.nio.file.Files.write(EF.toPath(), new java.lang.String("MOBS \"a fixc20 archer*2\"\n").getBytes());
CM.core.Encounters.forget();
WS.setParms("WAVE1=\"encounter:fixc20boss\" START=CALL");
CMLib.tracking().walk(V, Dir.EAST, false, false);
tick();
var waited = inPit("a fixc20 archer") == 0;
var SS = scriptOn(PIT, "FUNCTION_PROG GO\nmpwave start\n~\n");
SS.callFunction(PIT, "GO", "", V);
check("mpwave", waited && inPit("a fixc20 archer") == 2 && s(WS.getStat("WAVE")) == "1", "waited " + waited + " archers " + inPit("a fixc20 archer"));
// a saved game's state brings it back to the wave, its monsters those tagged in the room
var WS2 = CMClass.getBehavior("WaveSpawner");
WS2.setParms("WAVE1=\"encounter:fixc20boss\" START=CALL");
WS2.setStat("STATE", WS.getStat("STATE"));
WS2.tick(PIT, Tickable.TICKID_ROOM_BEHAVIOR);
check("restore", s(WS2.getStat("WAVE")) == "1" && s(WS2.getStat("ARENASTATE")) == "RUNNING" && field(WS2, "current").size() == 2, "wave " + WS2.getStat("WAVE") + " current " + field(WS2, "current").size());
WS.setStat("WAVECOMMAND", "RESET");
check("reset", inPit("a fixc20 archer") == 0 && s(WS.getStat("ARENASTATE")) == "IDLE", "archers " + inPit("a fixc20 archer"));
check("help", s(CMLib.help().getHelpText("BEHAVIOR_WAVESPAWNER", M, true)).indexOf("LOCKEXITS") >= 0, "no help");

kill();
EF["delete"]();
PIT.delBehavior(WS);
if (laurel != null) laurel.destroy();
V.setPlayerStats(null); V.destroy();
[GATE, PIT].forEach(function (r) { r.destroy(); });
["a fixc20 goblin", "a fixc20 archer"].forEach(function (n) { CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob(n)); });
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixc20 laurel"));
done();
