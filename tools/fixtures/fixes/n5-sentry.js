// N5: Sentry: spots an intruder (INTRUDERS=), hidden ones at SPOT less their skill; raises ALARM (RADIUS, MASK, MSG):
// those that fit wake and are Alerted for ALERTTICKS, come to the alarm and attack; ALARMITEM rung; attacked, it shouts.
FIXFILE = "n5-sentry";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var R2 = null;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) {
	var E = R.getExitInDir(d);
	if ((R.getRoomInDir(d) != null) && (E != null) && E.isOpen() && R.getRoomInDir(d) != R) { R2 = R.getRoomInDir(d); break; }
}
var V = newMob("GenMob", R);
V.setName("Fixn5thief");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
function sentry(name, parms) {
	var m = newMob("GenMob", R); m.setName(name);
	var B = CMClass.getBehavior("Sentry"); B.setParms(parms); m.addBehavior(B);
	return [m, B];
}
function npc(name, room, sleeps) {
	var m = newMob("GenMob", room); m.setName(name);
	if (sleeps) { var B = CMClass.getBehavior("Sleeper"); B.setParms("ALWAYS WAKECHANCE=0"); m.addBehavior(B); tickMob(m); }
	return m;
}
function alerted(m) { return m.fetchEffect("Alerted") != null; }
var bandit = npc("Fixn5bandit", R2, true);
var cow = npc("Fixn5cow", R2, false);
check("setup", R2 != null && CMLib.flags().isSleeping(bandit), "no room next door, or the bandit is awake");

// the lookout sees the thief: the alarm wakes the bandit next door and alerts it; the cow is not of the camp
var L1 = sentry("Fixn5lookout", "SPOT=100 INTRUDERS=\"-NAMES +Fixn5thief\" ALARM=\"RADIUS=2 MASK=-NAMES +Fixn5bandit MSG=Fixn5 to arms!\" ALERTTICKS=10");
out();
tickMob(L1[0]);
var heard = out();
check("alarm", has(heard, "'Fixn5 to arms!'"), heard.substring(Math.max(0, heard.length - 100)));
check("roused", !CMLib.flags().isSleeping(bandit) && alerted(bandit) && !alerted(cow), "bandit asleep " + CMLib.flags().isSleeping(bandit) + ", alerted " + alerted(bandit) + ", cow " + alerted(cow));
check("once", has(L1[1].getStat("DEBUG"), "alarm raised"), s(L1[1].getStat("DEBUG")));
// the bandit comes, and attacks
for (var i = 0; i < 4 && !bandit.isInCombat(); i++) tickMob(bandit);
check("comes", bandit.location() == R && bandit.isInCombat() && bandit.getVictim() == V, "at " + bandit.location().roomID() + ", fighting " + bandit.isInCombat());
// it lasts ALERTTICKS
for (var i = 0; i < 12; i++) { bandit.makePeace(true); V.makePeace(true); tickMob(bandit); }
bandit.makePeace(true); V.makePeace(true);
check("ends", !alerted(bandit), "still alerted");

// a hidden thief with all the skill is not spotted
V.basePhyStats().setDisposition(V.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_HIDDEN); V.recoverPhyStats();
var hide = CMClass.getAbility("Thief_Hide"); if (hide != null) { hide.setProficiency(100); V.addAbility(hide); }
var L2 = sentry("Fixn5watcher", "SPOT=100 INTRUDERS=\"-NAMES +Fixn5thief\" ALARM=\"RADIUS=1 MSG=Fixn5 again!\"");
for (var i = 0; i < 5; i++) tickMob(L2[0]);
check("hidden", has(L2[1].getStat("DEBUG"), "watching"), s(L2[1].getStat("DEBUG")));
V.basePhyStats().setDisposition(V.basePhyStats().disposition() & ~CM.Common.interfaces.PhyStats.IS_HIDDEN); V.recoverPhyStats();

// the gong: anyone may ring it
var L3 = sentry("Fixn5gongkeeper", "INTRUDERS=\"-NAMES +nobody\" ALARMITEM=\"a fixn5 gong\" ALARM=\"RADIUS=1 MSG=Fixn5 the gong!\"");
var gong = CMClass.getItem("GenItem"); gong.setName("a fixn5 gong"); R.addItem(gong);
out();
R.send(V, CMClass.getMsg(V, gong, null, CMMsg.MSG_PUSH, "<S-NAME> strike(s) <T-NAME>."));
check("gong", has(L3[1].getStat("DEBUG"), "alarm raised") && has(out(), "'Fixn5 the gong!'"), s(L3[1].getStat("DEBUG")));

// attacked, it shouts
var L4 = sentry("Fixn5picket", "INTRUDERS=\"-NAMES +nobody\" ALARM=\"RADIUS=1 MSG=Fixn5 help!\"");
out();
R.send(V, CMClass.getMsg(V, L4[0], null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> jab(s) <T-NAME>."));
check("attacked", has(out(), "'Fixn5 help!'"), "no shout when attacked");
V.makePeace(true); L4[0].makePeace(true);

[bandit, cow, L1[0], L2[0], L3[0], L4[0], gong].forEach(function (x) { x.destroy(); });
V.setPlayerStats(null); V.destroy();
done();
