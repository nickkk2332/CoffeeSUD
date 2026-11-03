// W9: ResetWhole DAYS (on the area's calendar), EXCEPT (a slain boss and taken loot stay gone), MSG (on the next entry), and its saved state.
FIXFILE = "w9-resetwhole";
var Dir = CM.core.Directions;
var GameTime = CM.core.GameTime;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var T = otherRoom();
var DB = CMLib.database();
// the room's saved contents: the Fixw9 King and his crown
var king = CMClass.getMOB("GenMob"); king.setName("the Fixw9 King"); king.bringToLife(T, true); king.setStartRoom(T);
var goblin = CMClass.getMOB("GenMob"); goblin.setName("a fixw9 goblin"); goblin.bringToLife(T, true); goblin.setStartRoom(T);
var crown = CMClass.getItem("GenItem"); crown.setName("the fixw9 crown"); T.addItem(crown);
DB.DBUpdateMOBs(T); DB.DBUpdateItems(T);
function named(n) { var c = 0; for (var e = T.inhabitants(); e.hasMoreElements();) { var m = e.nextElement(); if (m != null && s(m.Name()) == n) c++; } return c; }
function items(n) { var c = 0; for (var e = T.items(); e.hasMoreElements();) { var i = e.nextElement(); if (i != null && s(i.Name()) == n) c++; } return c; }
var B = CMClass.getBehavior("ResetWhole");
B.setParms("DAYS=2 EXCEPT=\"-NAME +'the Fixw9 King' +'the fixw9 crown'\" MSG=\"Fresh fixw9 tracks lead into the tunnels.\"");
T.addBehavior(B);
function cur() { return T.fetchBehavior("ResetWhole"); }
var G = scratchRoom(); G.setDisplayText("Fixw9 tunnel mouth");
var V = CMClass.getMOB("GenMob"); V.setName("Fixw9pip"); V.bringToLife(G, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o; }
function due(ago) { var b = cur(); b.setStat("STATE", "<RESETWHOLE AGO=\"" + ago + "\" />"); b.tick(T, Tickable.TICKID_ROOM_BEHAVIOR); }
try {
	// DAYS: the area calendar's days, in game time
	var C = CMLib.time().localClock(T);
	var period = call(B, "period", [T]);
	check("days", period == 2 * C.getHoursInDay() * CMProps.getMillisPerMudHour(), "period " + period + " for " + C.getHoursInDay() + "-hour days");
	// the party slays the King and takes the crown; a junk item shows when a reset has happened
	king.destroy();
	crown.removeFromOwnerContainer(); V.addItem(crown);
	var junk = CMClass.getItem("GenItem"); junk.setName("a fixw9 bone"); T.addItem(junk);
	due(Math.floor(period / 2));
	var early = items("a fixw9 bone") == 1;
	due(period + 60000);
	check("except", early && items("a fixw9 bone") == 0 && named("a fixw9 goblin") == 1 && named("the Fixw9 King") == 0 && items("the fixw9 crown") == 0,
		"early " + early + " bone " + items("a fixw9 bone") + " goblins " + named("a fixw9 goblin") + " kings " + named("the Fixw9 King") + " crowns " + items("the fixw9 crown"));
	// the state for a save: the time since the last visit, and the message waiting
	var st = s(cur().getStat("STATE"));
	check("state", has(st, "<RESETWHOLE AGO=") && has(st, "MSG=\"TRUE\""), st);
	// MSG: the first to come in reads it, once (after the room's own look, as the move's trailer)
	function enter() {
		var msg = CMClass.getMsg(V, T, null, CMMsg.MSG_ENTER, null);
		cur().executeMsg(T, msg);
		var tr = msg.trailerRunnables();
		if (tr != null) for (var i = 0; i < tr.size(); i++) tr.get(i).run();
		return out();
	}
	V.setSession(VS); VS.setMob(V); out();
	var said = enter(), again = enter();
	check("msg", has(said, "Fresh fixw9 tracks lead into the tunnels.") && !has(again, "Fresh fixw9 tracks") && !has(s(cur().getStat("STATE")), "MSG="), said + " / " + again + " / " + cur().getStat("STATE"));
	// without EXCEPT the King comes back; with it, a King still there stays (and only one)
	cur().setParms("TICKS=10");
	due(10 * CMProps.getTickMillis() + 60000);
	var back = named("the Fixw9 King") == 1 && items("the fixw9 crown") == 1;
	due(period + 60000);
	check("stays", back && named("the Fixw9 King") == 1 && items("the fixw9 crown") == 1, "back " + back + " kings " + named("the Fixw9 King") + " crowns " + items("the fixw9 crown"));
	check("quoted", s(CM.Behaviors.ResetWhole.quotedNames("-NAME +'the Goblin King' -'a rat'")) == "-NAME \"+the Goblin King\" \"-a rat\"", s(CM.Behaviors.ResetWhole.quotedNames("-NAME +'the Goblin King' -'a rat'")));
	check("help", has(CMLib.help().getHelpText("BEHAVIOR_RESETWHOLE", M, true), "EXCEPT=") && has(CMLib.help().getHelpText("BEHAVIOR_RESETWHOLE", M, true), "DAYS="), "no help");
} finally {
	while (cur() != null) T.delBehavior(cur());
	V.setSession(null); V.destroy();
	for (var i = T.numInhabitants() - 1; i >= 0; i--) { var m = T.fetchInhabitant(i); if (m != null && has(m.Name(), "ixw9")) m.destroy(); }
	for (var i = T.numItems() - 1; i >= 0; i--) { var it = T.getItem(i); if (it != null && has(it.Name(), "fixw9")) it.destroy(); }
	DB.DBUpdateMOBs(T); DB.DBUpdateItems(T);
}
done();
