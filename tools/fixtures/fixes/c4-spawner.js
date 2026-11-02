// C4: RandomMonsters and RandomItems with CATALOG= weights, WHEN= on the room, DESPAWN=WHENFALSE|TICKS=n (unseen),
// NEAR=PARTY:n, LEVEL=PARTY+n and MAXAREA= on an area.
FIXFILE = "c4-spawner";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function entry(cls, name, mob) {
	var P = mob ? CMClass.getMOB(cls) : CMClass.getItem(cls);
	P.setName(name);
	P.recoverPhyStats();
	CMLib.catalog().addCatalog(P);
	P.destroy();
}
entry("GenMob", "a fixc4 grey wolf", true);
entry("GenMob", "a fixc4 dire wolf", true);
entry("GenItem", "a fixc4 pine cone", false);
// a wood of its own: clearing - path - glade - far glade
var X = CMClass.getAreaType("StdArea");
X.setName("Fixc4Wood");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixc4Wood#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, b) {
	a.rawDoors()[Dir.EAST] = b; a.setRawExit(Dir.EAST, CMClass.getExit("Open"));
	b.rawDoors()[Dir.WEST] = a; b.setRawExit(Dir.WEST, CMClass.getExit("Open"));
}
var A = room(0, "Fixc4 clearing"), B = room(1, "Fixc4 path"), C = room(2, "Fixc4 glade"), D = room(3, "Fixc4 far glade");
link(A, B); link(B, C); link(C, D);
function spawner(host, cls, parms) { var b = CMClass.getBehavior(cls); b.setParms(parms); host.addBehavior(b); return b; }
function kept(b) { return field(b, "maintained"); }
function names(b) { var l = kept(b), o = []; for (var i = 0; i < l.size(); i++) o.push(s(l.get(i).Name())); return o; }
function clear(host, b) { var l = kept(b); for (var i = l.size() - 1; i >= 0; i--) l.get(i).destroy(); host.delBehavior(b); }
var TR = Tickable.TICKID_ROOM_BEHAVIOR;

// CATALOG= weights
var W = spawner(A, "RandomMonsters", "min=1 max=1 chance=100 minmonsters=40 maxmonsters=40 CATALOG=\"a fixc4 grey wolf=4, a fixc4 dire wolf=1\"");
W.tick(A, TR);
var n = names(W), grey = n.filter(function (x) { return x == "a fixc4 grey wolf"; }).length, dire = n.filter(function (x) { return x == "a fixc4 dire wolf"; }).length;
check("weights", n.length == 40 && dire >= 1 && grey > dire * 1.5 && grey + dire == 40 && kept(W).get(0).hasTag("SPAWNED"), "greys " + grey + ", dires " + dire + " of " + n.length);
check("nofile", W.externalFiles() == null, "CATALOG= alone named a file");
clear(A, W);

// WHEN= on the room, DESPAWN=WHENFALSE
var WH = spawner(B, "RandomMonsters", "min=1 max=1 chance=100 minmonsters=2 maxmonsters=2 WHEN=\"-LOCATION +ROOMSTR(moonlit)\" DESPAWN=WHENFALSE CATALOG=\"a fixc4 grey wolf\"");
WH.tick(B, TR);
var none = kept(WH).size();
B.setDisplayText("Fixc4 moonlit path");
WH.tick(B, TR);
var two = kept(WH).size();
check("when", none == 0 && two == 2, "spawned " + none + " by day, " + two + " by moonlight");
var w0 = kept(WH).get(0);
B.setDisplayText("Fixc4 path");
WH.tick(B, TR);
check("whenfalse", kept(WH).size() == 0 && w0.amDestroyed(), "still " + kept(WH).size());
clear(B, WH);

// DESPAWN=TICKS=3, not while fighting
var V = newMob("GenMob", C);
V.setName("Fixc4pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
V.basePhyStats().setLevel(12); V.recoverPhyStats();
var TK = spawner(C, "RandomMonsters", "min=1 max=1 chance=100 minmonsters=1 maxmonsters=1 DESPAWN=TICKS=3 CATALOG=\"a fixc4 grey wolf\"");
TK.tick(C, TR);
var t1 = kept(TK).get(0);
TK.tick(C, TR); TK.tick(C, TR);
var stayed = !t1.amDestroyed();
TK.tick(C, TR);
var t2 = kept(TK).get(0);
check("ticks", stayed && t1.amDestroyed() && t2 != t1 && !t2.amDestroyed(), "first gone " + t1.amDestroyed() + " after " + (stayed ? "3" : "fewer") + " ticks");
t2.setVictim(V); V.setVictim(t2);
for (var i = 0; i < 4; i++) TK.tick(C, TR);
var fought = !t2.amDestroyed();
t2.makePeace(true); V.makePeace(true);
TK.tick(C, TR);
check("unseen", fought && t2.amDestroyed(), "left mid-fight " + !fought);
clear(C, TK);

// NEAR=PARTY:1, LEVEL=PARTY+2, MAXAREA=2 on the area
A.bringMobHere(V, false);
var AR = spawner(X, "RandomMonsters", "min=1 max=1 chance=100 minmonsters=3 maxmonsters=3 NEAR=PARTY:1 LEVEL=PARTY+2 MAXAREA=2 CATALOG=\"a fixc4 dire wolf\"");
AR.tick(X, Tickable.TICKID_AREA);
var wolves = kept(AR), nearOk = true, lvlOk = true;
for (var j = 0; j < wolves.size(); j++) {
	var wl = wolves.get(j);
	if ((wl.location() != A) && (wl.location() != B)) nearOk = false;
	if (wl.phyStats().level() != 14) lvlOk = false;
}
check("maxarea", wolves.size() == 2, "spawned " + wolves.size());
check("near", wolves.size() > 0 && nearOk, "a wolf beyond the path");
check("level", wolves.size() > 0 && lvlOk, "level " + (wolves.size() > 0 ? wolves.get(0).phyStats().level() : "-"));
clear(X, AR);

// RandomItems: CATALOG= and DESPAWN=TICKS=2 in a room
var IT = spawner(D, "RandomItems", "min=1 max=1 chance=100 minitems=1 maxitems=1 DESPAWN=TICKS=2 CATALOG=\"a fixc4 pine cone\"");
IT.tick(D, TR);
var c1 = kept(IT).size() > 0 ? kept(IT).get(0) : null;
check("items", c1 != null && s(c1.Name()) == "a fixc4 pine cone" && c1.owner() == D && c1.hasTag("SPAWNED"), "no cone");
IT.tick(D, TR); IT.tick(D, TR);
check("item-despawn", c1 != null && c1.amDestroyed() && kept(IT).size() == 1 && kept(IT).get(0) != c1, "the cone stayed");
clear(D, IT);
check("help", s(CMLib.help().getHelpText("BEHAVIOR_RANDOMMONSTERS", M, true)).indexOf("DESPAWN") >= 0
	&& s(CMLib.help().getHelpText("BEHAVIOR_RANDOMITEMS", M, true)).indexOf("CATALOG=") >= 0, "no help");

V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
["a fixc4 grey wolf", "a fixc4 dire wolf"].forEach(function (nm) { CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob(nm)); });
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixc4 pine cone"));
done();
