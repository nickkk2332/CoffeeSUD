// I20: GenTrap TRIGGERS (SIT, OPEN, LEAVE:north...) instead of its class's, a script that runs with no ability, ONCE,
// DISARM=verb:phrase.
FIXFILE = "i20-trap";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi20Keep");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi20Keep#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var HALL = room(0, "Fixi20 throne hall"), NORTH = room(1, "Fixi20 north stair"), EAST = room(2, "Fixi20 east door");
function link(a, d, b) { a.rawDoors()[d] = b; a.setRawExit(d, CMClass.getExit("Open")); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), CMClass.getExit("Open")); }
link(HALL, Dir.NORTH, NORTH); link(HALL, Dir.EAST, EAST);
var maker = CMClass.getMOB("GenMob"); maker.setName("Fixi20maker"); maker.basePhyStats().setLevel(90); maker.recoverPhyStats(); maker.bringToLife(EAST, true);
var V = CMClass.getMOB("GenMob"); V.setName("Fixi20pip"); V.basePhyStats().setLevel(1); V.recoverPhyStats(); V.bringToLife(HALL, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); V.setStartRoom(null);
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
function trap(id, affects, triggers, script, more) {
	var T = CMClass.getAbility("GenTrap");
	T.setStat("CLASS9", id);
	T.setStat("NAME", id);
	T.setStat("CANAFFECTMASK", affects);
	T.setStat("DMGF", "");
	T.setStat("ABILITY", "");
	T.setStat("TRIGMSG", "<S-NAME> spring(s) a trap!");
	T.setStat("SCRIPT", script);
	T.setStat("TRIGGERS", triggers);
	if (more) for (var k in more) T.setStat(k, more[k]);
	return T;
}
// the usurper's throne: SIT, a script and nothing else, once
var throne = CMClass.getItem("GenItem"); throne.setName("a gilded fixi20 throne"); CMLib.flags().setGettable(throne, false); HALL.addItem(throne);
var T1 = trap("Fixi20Throne", "ITEMS", "SIT", "ACT_PROG Fixi20Throne\nmpsetvar FIXTEST FIXI20S sat\n~\n", { ONCE: "true" });
var set1 = T1.setTrap(maker, throne, 0, 90, true);
clearVar("FIXI20S"); clearVar("FIXI20L");
HALL.send(V, CMClass.getMsg(V, throne, null, CMMsg.MSG_GET, null));
var afterGet = gvar("FIXI20S");
out();
HALL.send(V, CMClass.getMsg(V, throne, null, CMMsg.MSG_SIT, "<S-NAME> sit(s) on <T-NAME>."));
var said = out();
check("sit", afterGet == "" && gvar("FIXI20S") == "sat" && has(said, "spring"), "after get '" + afterGet + "' after sit '" + gvar("FIXI20S") + "' '" + said + "'");
check("once", throne.fetchEffect("Fixi20Throne") == null, "still trapped");
// a chest whose wire is cut first
var chest = CMClass.getItem("GenContainer"); chest.setName("an iron fixi20 chest"); chest.setDoorsNLocks(true, false, true, false, false, false); HALL.addItem(chest);
var T2 = trap("Fixi20Chest", "ITEMS", "OPEN", "ACT_PROG Fixi20Chest\nmpsetvar FIXTEST FIXI20C opened\n~\n", { DISARM: "verb:cut red wire" });
var set2 = T2.setTrap(maker, chest, 0, 90, true);
clearVar("FIXI20C");
var r = cmd("cut the blue wire");
var wrong = r.took;
r = cmd("cut red wire");
HALL.send(V, CMClass.getMsg(V, chest, null, CMMsg.MSG_OPEN, null));
check("disarm", (!wrong) && r.took && has(r.said, "disarm") && gvar("FIXI20C") == "", "wrong " + wrong + " took " + r.took + " '" + gvar("FIXI20C") + "'");
// a room: leaving north only
var T3 = trap("Fixi20Stair", "ROOMS", "LEAVE:north", "ACT_PROG Fixi20Stair\nmpsetvar FIXTEST FIXI20L $t\n~\n", null);
var set3 = T3.setTrap(maker, HALL, 0, 90, true);
CMLib.tracking().walk(V, Dir.EAST, false, false);
var east = gvar("FIXI20L");
HALL.bringMobHere(V, false);
CMLib.tracking().walk(V, Dir.NORTH, false, false);
check("leave", east == "" && gvar("FIXI20L") == "Fixi20pip", "east '" + east + "' north '" + gvar("FIXI20L") + "'");
check("stats", s(T3.getStat("TRIGGERS")) == "LEAVE:NORTH" && s(T2.getStat("DISARM")) == "verb:cut red wire" && has(CMLib.help().getHelpText("GENTRAP", M, true), "TRIGGERS"), T3.getStat("TRIGGERS") + " " + T2.getStat("DISARM"));

V.setPlayerStats(null); V.destroy(); maker.destroy();
CMLib.map().obliterateMapArea(X);
done();
