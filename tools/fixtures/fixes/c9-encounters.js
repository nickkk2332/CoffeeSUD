// C9: encounter tables (world/encounters/NAME.enc) and the ENCOUNTER command; RandomEncounters on a room (CHANCE,
// COOLDOWN, DESPAWN after the party leaves); MOBS with dice, LEADER, AMBUSH, FROM=ADJACENT|HIDDEN, LEVEL bands, ONCE,
// ITEM, EMOTE, FUNC; BUDGET; MPMLOAD ENCOUNTER and catalog:name; quest LOAD ENCOUNTER.
FIXFILE = "c9-encounters";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var Enc = CM.core.Encounters;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
function entry(cls, name, mob) {
	var P = mob ? CMClass.getMOB(cls) : CMClass.getItem(cls);
	P.setName(name);
	P.recoverPhyStats();
	CMLib.catalog().addCatalog(P);
	P.destroy();
}
["a fixc9 wolf", "a fixc9 dire wolf", "a fixc9 deer", "a fixc9 traveller"].forEach(function (n) { entry("GenMob", n, true); });
entry("GenItem", "a fixc9 branch", false);
check("world", CM.core.save.WorldFiles.worldDir() != null, "no world folder");
// the ENCOUNTER command builds the table
var EC = CMClass.getCommand("EncounterCmd");
var X = CMClass.getAreaType("StdArea");
X.setName("Fixc9Wood");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixc9Wood#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, b) {
	a.rawDoors()[Dir.EAST] = b; a.setRawExit(Dir.EAST, CMClass.getExit("Open"));
	b.rawDoors()[Dir.WEST] = a; b.setRawExit(Dir.WEST, CMClass.getExit("Open"));
}
var B = room(0, "Fixc9 path"), A = room(1, "Fixc9 clearing"), C = room(2, "Fixc9 thicket");
link(B, A); link(A, C);
var V = CMClass.getMOB("GenMob");
V.setName("Fixc9pip");
V.basePhyStats().setLevel(5); V.recoverPhyStats();
V.baseState().setHitPoints(5000); V.recoverMaxState(); V.resetToMaxState();
V.bringToLife(B, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); EC.execute(V, CMParms.parse(line), 0); return out(); }
var CMParms = CM.core.CMParms;
Enc.fileOf("fixc9wood")["delete"](); Enc.fileOf("fixc9quest")["delete"]();
check("add-bad", has(cmd("ENCOUNTER ADD fixc9wood MOBS \"a fixc9 nothing\""), "Not added"), "a bad entry went in");
[
	"MOBS \"a fixc9 deer*1d2\" LEVEL=-9",
	"MOBS \"a fixc9 wolf*2\" LEADER=\"a fixc9 dire wolf\" LEVEL=10-19 AMBUSH",
	"MOBS \"a fixc9 traveller\" LEVEL=20-29 ONCE",
	"ITEM \"a fixc9 branch*3\" LEVEL=30-39",
	"EMOTE \"Fixc9 an owl hoots.\" LEVEL=40-49",
	"FUNC FIXC9F LEVEL=50-59",
	"MOBS \"a fixc9 deer\" LEVEL=60-69 FROM=ADJACENT",
	"MOBS \"a fixc9 wolf\" LEVEL=70-79 FROM=HIDDEN",
	"MOBS \"a fixc9 deer\" LEVEL=90- MASK=\"-NAMES +Fixc9pip\""
].forEach(function (l) { cmd("ENCOUNTER ADD fixc9wood " + l); });
var shown = cmd("ENCOUNTER SHOW fixc9wood");
check("show", has(shown, "9. MOBS") && has(shown, "LEADER=\"a fixc9 dire wolf\"") && has(shown, "MASK=\"-NAMES +Fixc9pip\""), shown);
check("list", has(cmd("ENCOUNTER LIST"), "fixc9wood: 9 entries"), "not listed");
var RE = CMClass.getBehavior("RandomEncounters");
RE.setParms("TABLE=fixc9wood CHANCE=100 DESPAWN=2");
A.addBehavior(RE);
scriptOn(A, "FUNCTION_PROG FIXC9F\nmpsetvar FIXTEST FIXC9F $n\n~\n");
var TR = Tickable.TICKID_ROOM_BEHAVIOR;
function enter(lvl) {
	V.makePeace(true);
	if (V.location() != B) B.bringMobHere(V, false);
	V.basePhyStats().setLevel(lvl); V.recoverPhyStats();
	out();
	CMLib.tracking().walk(V, Dir.EAST, false, false);
	RE.tick(A, TR);
	return out();
}
function inA(name) { var n = 0; for (var i = 0; i < A.numInhabitants(); i++) if (s(A.fetchInhabitant(i).Name()) == name) n++; return n; }
function clearA() {
	for (var i = A.numInhabitants() - 1; i >= 0; i--) { var m = A.fetchInhabitant(i); if (m != V && has(m.Name(), "fixc9")) m.destroy(); }
	for (var j = A.numItems() - 1; j >= 0; j--) { var it = A.getItem(j); if (has(it.Name(), "fixc9")) it.destroy(); }
}
// level 5: deer, 1d2, tagged
enter(5);
var deer = inA("a fixc9 deer");
check("deer", V.location() == A && deer >= 1 && deer <= 2 && has(RE.getStat("LAST"), "fixc9 deer*1d2"), "deer " + deer + " last " + RE.getStat("LAST"));
// the party goes: DESPAWN=2 ticks later they are gone
B.bringMobHere(V, false);
RE.tick(A, TR); RE.tick(A, TR);
check("despawn", inA("a fixc9 deer") == 0 && RE.getStat("ACTIVE") == "0", "deer left " + inA("a fixc9 deer"));
// level 15: wolves with their leader, and the first round is theirs
enter(15);
var wolves = inA("a fixc9 wolf"), dire = inA("a fixc9 dire wolf"), led = true, att = 0;
for (var i = 0; i < A.numInhabitants(); i++) {
	var m = A.fetchInhabitant(i);
	if (s(m.Name()) == "a fixc9 wolf" && (m.amFollowing() == null || s(m.amFollowing().Name()) != "a fixc9 dire wolf")) led = false;
	if (has(m.Name(), "fixc9") && m.getVictim() == V) att++;
}
check("pack", wolves == 2 && dire == 1 && led, "wolves " + wolves + " dire " + dire + " led " + led);
check("ambush", att == 3, "attacked by " + att);
V.makePeace(true); clearA();
// level 25: once
enter(25);
var t1 = inA("a fixc9 traveller");
clearA();
enter(25);
check("once", t1 == 1 && inA("a fixc9 traveller") == 0, "first " + t1 + ", again " + inA("a fixc9 traveller"));
cmd("ENCOUNTER RESET fixc9wood");
clearA();
enter(25);
check("reset", inA("a fixc9 traveller") == 1, "not again after RESET");
clearA();
// level 35: items
enter(35);
var br = 0; for (var k = 0; k < A.numItems(); k++) if (s(A.getItem(k).Name()) == "a fixc9 branch") br++;
check("items", br == 3, "branches " + br);
clearA();
// level 45: an emote
check("emote", has(enter(45), "Fixc9 an owl hoots."), "no owl");
// level 55: a function of the room's scripts, $n the one who came in
clearVar("FIXC9F");
enter(55);
check("func", gvar("FIXC9F") == "Fixc9pip", "FUNC gave '" + gvar("FIXC9F") + "'");
// level 65: from next door; level 75: hidden
var seen65 = enter(65);
check("adjacent", inA("a fixc9 deer") == 1, "no deer came in");
clearA();
enter(75);
var hid = null; for (var h = 0; h < A.numInhabitants(); h++) if (s(A.fetchInhabitant(h).Name()) == "a fixc9 wolf") hid = A.fetchInhabitant(h);
check("hidden", hid != null && CMLib.flags().isHidden(hid), "not hidden");
clearA();
// COOLDOWN=2: two rooms after one go by
RE.setParms("TABLE=fixc9wood CHANCE=100 DESPAWN=2 COOLDOWN=2");
var got = [];
for (var c = 0; c < 4; c++) { enter(5); got.push(inA("a fixc9 deer") > 0 ? 1 : 0); clearA(); }
check("cooldown", got.join("") == "1001", "encounters " + got.join(""));
// BUDGET: more MOBS until their power is that share of the party's
V.basePhyStats().setLevel(95); V.recoverPhyStats();
var res = Enc.roll(Enc.table("fixc9wood"), A, V, A, 300);
check("budget", res.spawned.size() > 1, "only " + res.spawned.size());
clearA();
// MPMLOAD ENCOUNTER, and catalog:name
B.bringMobHere(V, false);
V.basePhyStats().setLevel(5); V.recoverPhyStats();
var npc = newMob("GenMob", A); npc.setName("Fixc9warden");
var S = scriptOn(npc, "FUNCTION_PROG GO\nmpmload encounter fixc9wood\nmpsetvar FIXTEST FIXC9B $b\nmpmload catalog:a fixc9 traveller\n~\n");
clearVar("FIXC9B");
S.callFunction(npc, "GO", "", V);
check("mpmload", has(gvar("FIXC9B"), "fixc9 deer") && inA("a fixc9 traveller") == 1, "$b '" + gvar("FIXC9B") + "' travellers " + inA("a fixc9 traveller"));
npc.destroy(); clearA();
// quest LOAD ENCOUNTER
cmd("ENCOUNTER ADD fixc9quest MOBS \"a fixc9 deer*2\"");
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript("set name fixc9q\nset duration 100\nset area 'Fixc9Wood'\nload encounter fixc9quest\n", true);
CMLib.quests().addQuest(Q);
Q.startQuest();
function inX(name) { var n = 0; [A, B, C].forEach(function (r) { for (var i = 0; i < r.numInhabitants(); i++) if (s(r.fetchInhabitant(i).Name()) == name) n++; }); return n; }
var qd = inX("a fixc9 deer"), qr = Q.running();
Q.stopQuest();
check("quest", qr && qd == 2 && inX("a fixc9 deer") == 0, "running " + qr + ", deer " + qd + " during, " + inX("a fixc9 deer") + " after");
CMLib.quests().delQuest(Q);
check("help", has(CMLib.help().getHelpText("BEHAVIOR_RANDOMENCOUNTERS", M, true), "TABLE") && has(CMLib.help().getHelpText("ENCOUNTER", M, true), "ADJACENT"), "no help");

clearA();
A.delBehavior(RE);
Enc.reset(Enc.table("fixc9wood"));
Enc.fileOf("fixc9wood")["delete"](); Enc.fileOf("fixc9quest")["delete"]();
V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
["a fixc9 wolf", "a fixc9 dire wolf", "a fixc9 deer", "a fixc9 traveller"].forEach(function (n) { CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob(n)); });
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixc9 branch"));
done();
