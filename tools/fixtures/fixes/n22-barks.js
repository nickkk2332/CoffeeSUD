// N22: Sounder's bark table: GREET_FIRST, GREET with COOLDOWN and weights, WIELD(item), SHOP_LIST, BRIBED once a player,
// SEE_MOB(mask), ALLY_DIES, KILL, NIGHT, HURT<30, FILE=; old entries still work; the state.
FIXFILE = "n22-barks";
var CMFile = CM.core.CMFile;
var Resources = CM.core.Resources;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixn22pip");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var guard = newMob("GenMob", R);
guard.setName("Fixn22guard");
var S = CMClass.getBehavior("Sounder");
S.setParms("min=50 max=50;SOUND 1 Fixn22 a bell tolls far off.;"
	+ "GREET_FIRST: SAY Fixn22 a new face.;"
	+ "GREET: COOLDOWN=5 2 SAY Fixn22 move along. | 1 SAY Fixn22 keep your blade sheathed.;"
	+ "WIELD(sword): SAY Fixn22 put that away in the city!;"
	+ "SHOP_LIST: SAY Fixn22 have a look.;"
	+ "BRIBED: ONCEPERPLAYER SAY Fixn22 I saw nothing.;"
	+ "SEE_MOB(-NAMES +Fixn22thief): EMOTE eyes $n warily.;"
	+ "KILL: SAY Fixn22 justice.;"
	+ "ALLY_DIES: SAY Fixn22 you will hang for that, $n!;"
	+ "NIGHT: SAY Fixn22 curfew's at the bell.;"
	+ "HURT<30: SAY Fixn22 to me! Guards!;"
	+ "IDLE: CHANCE=0 SAY Fixn22 never said.");
guard.addBehavior(S);
// (Sounder sizes its old entries before it drops min= max=: count those set)
function oldCount(B) { var t = field(B, "triggers"), n = 0; for (var i = 0; i < t.length; i++) if (t[i] != 0) n++; return n; }
check("parse", field(S, "barks").size() == 11 && oldCount(S) == 1, "barks " + field(S, "barks").size() + " old " + oldCount(S));
function enter(m) { R.send(m, CMClass.getMsg(m, R, null, CMMsg.MSG_ENTER, "<S-NAME> arrive(s).")); }
out();
enter(V);
check("greet-first", has(out(), "Fixn22 a new face."), "no first greeting");
for (var i = 0; i < 5; i++) S.tick(guard, Tickable.TICKID_MOB);
enter(V);
var g = out();
check("greet", (has(g, "Fixn22 move along.") || has(g, "Fixn22 keep your blade sheathed.")) && !has(g, "a new face"), g);
enter(V);
check("cooldown", !has(out(), "Fixn22"), "greeted again within the cooldown");
var sword = CMClass.getWeapon("GenWeapon"); sword.setName("a fixn22 sword"); V.addItem(sword);
R.send(V, CMClass.getMsg(V, sword, null, CMMsg.MSG_WIELD, "<S-NAME> wield(s) <T-NAME>."));
check("wield", has(out(), "Fixn22 put that away in the city!"), "no word on the sword");
R.send(V, CMClass.getMsg(V, guard, null, CMMsg.MSG_LIST, null));
check("shop", has(out(), "Fixn22 have a look."), "no word on LIST");
var cur = CMLib.beanCounter().getCurrency(guard);
for (var j = 0; j < 2; j++) {
	var c = CMLib.beanCounter().makeBestCurrency(cur, 5.0); V.addItem(c);
	R.send(V, CMClass.getMsg(V, guard, c, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
}
var b = out();
check("bribed-once", b.split("Fixn22 I saw nothing.").length == 2, b);
var thief = newMob("GenMob", R); thief.setName("Fixn22thief");
out();
enter(thief);
check("see-mob", has(out(), "Fixn22thief warily"), "no eye on the thief");
var ally = newMob("GenMob", R); ally.setName("Fixn22watchman");
ally.baseCharStats().setMyRace(guard.charStats().getMyRace()); ally.recoverCharStats();
R.send(ally, CMClass.getMsg(ally, null, V, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_DEATH, "<S-NAME> is DEAD!!!"));
check("ally-dies", has(out(), "Fixn22 you will hang for that, Fixn22pip!"), "no word on the watchman");
R.send(thief, CMClass.getMsg(thief, null, guard, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_DEATH, "<S-NAME> is DEAD!!!"));
check("kill", has(out(), "Fixn22 justice."), "no word on its kill");
// the hour turns
var C = CMLib.time().localClock(guard);
var wasHour = C.getHourOfDay();
var night = -1, day = -1;
for (var h = 0; h < C.getHoursInDay(); h++) { C.setHourOfDay(h); var t = s(C.getTODCode()); if (t == "NIGHT" && night < 0) night = h; if (t == "DAY" && day < 0) day = h; }
C.setHourOfDay(day);
S.tick(guard, Tickable.TICKID_MOB);
C.setHourOfDay(night);
S.tick(guard, Tickable.TICKID_MOB);
check("night", has(out(), "Fixn22 curfew's at the bell."), "no word at nightfall");
C.setHourOfDay(wasHour);
// hurt
guard.curState().setHitPoints(1);
S.tick(guard, Tickable.TICKID_MOB);
S.tick(guard, Tickable.TICKID_MOB);
var hu = out();
check("hurt", hu.split("Fixn22 to me! Guards!").length == 2, hu);
check("idle-chance", !has(hu, "never said"), "CHANCE=0 barked");
guard.curState().setHitPoints(guard.maxState().getHitPoints());
// the state
var st = s(S.getStat("STATE"));
check("state", has(st, "FIXN22PIP") && has(st, "BRIBED/Fixn22pip"), st);
// FILE=
new CMFile(Resources.makeFileResourceName("fixn22barks.txt"), null, CMFile.FLAG_FORCEALLOW).saveText("# a guard's barks\nGREET: SAY Fixn22 from the file.\n");
Resources.removeResource("fixn22barks.txt");
var guard2 = newMob("GenMob", R); guard2.setName("Fixn22porter");
var S2 = CMClass.getBehavior("Sounder");
S2.setParms("FILE=fixn22barks.txt");
guard2.addBehavior(S2);
out();
enter(V);
check("file", has(out(), "Fixn22 from the file."), "the file's line");

new CMFile(Resources.makeFileResourceName("fixn22barks.txt"), null, CMFile.FLAG_FORCEALLOW)["delete"]();
[guard, guard2, thief, ally].forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
