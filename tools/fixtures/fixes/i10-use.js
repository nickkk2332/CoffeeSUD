// I10: USE x (ON y) and COMBINE a WITH b: USE_PROG and COMBINE_PROG on either thing, then world/combinations.txt
// (A | B | RESULT | VERB= CONSUME= MASK= SKILL= FAILMSG= MSG= ACTIONS=), else "Nothing happens."
FIXFILE = "i10-use";
var WF = CM.core.save.WorldFiles;
var CO = CM.core.Combinations;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = CO.file();
var before = F.isFile() ? new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8") : null;
var table = "# fixes test\n"
	+ "a torn fixi10 map | a torn fixi10 map | the old fixi10 smuggler's map | VERB=COMBINE CONSUME=BOTH MSG=\"<S-NAME> fit(s) the torn halves together.\"\n"
	+ "oil flask | squeaky hinge | | VERB=USE CONSUME=A ACTIONS=\"setvar FIXTEST FIXI10H oiled\" MSG=\"<S-NAME> oil(s) <T-NAME>.\"\n"
	+ "tag:FIXI10WAND | | | VERB=USE MASK=\"-NAMES +Fixi10bo\" FAILMSG=\"You cannot make it work.\"\n";
java.nio.file.Files.write(F.toPath(), new java.lang.String(table).getBytes("UTF-8"));
F.setLastModified(java.lang.System.currentTimeMillis() + 5000);
var C = CMClass.getItem("GenItem"); C.setName("the old fixi10 smuggler's map"); C.recoverPhyStats(); CMLib.catalog().addCatalog(C); C.destroy();
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi10pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(c, line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); CMClass.getCommand(c).execute(V, l, 0); return out(); }
function item(name, where) { var I = CMClass.getItem("GenItem"); I.setName(name); if (where == R) R.addItem(I); else V.addItem(I); return I; }
["FIXI10H", "FIXI10S", "FIXI10C"].forEach(clearVar);
// combine two halves
var h1 = item("a torn fixi10 map", V), h2 = item("a torn fixi10 map", V);
var said = cmd("Combine", "combine torn with torn");
var whole = V.findItem(null, "smuggler's map");
check("combine", whole != null && h1.amDestroyed() && h2.amDestroyed() && has(said, "halves together") && has(said, "You now have"), "map " + whole + " '" + said + "'");
// use one thing on another in the room
var flask = item("an oil flask", V), hinge = item("a squeaky hinge", R);
CMLib.flags().setGettable(hinge, false);
said = cmd("Use", "use flask on hinge");
check("use", flask.amDestroyed() && (!hinge.amDestroyed()) && gvar("FIXI10H") == "oiled" && has(said, " oil"), "flask " + flask.amDestroyed() + " '" + gvar("FIXI10H") + "' '" + said + "'");
// a mask that fails, alone
var wand = item("a bent fixi10 wand", V); wand.addTag("FIXI10WAND");
said = cmd("Use", "use wand");
check("mask", has(said, "You cannot make it work.") && (!wand.amDestroyed()), "'" + said + "'");
// the scripts come first: USE_PROG on the target, COMBINE_PROG on either
var lock = item("a rusty fixi10 lock", R);
scriptOn(lock, "USE_PROG crowbar\nmpsetvar FIXTEST FIXI10S $n:$o:$t\n~\n");
var bar = item("a crowbar", V);
said = cmd("Use", "use crowbar on lock");
check("use_prog", gvar("FIXI10S") == "Fixi10pip:a crowbar:a rusty fixi10 lock" && (!has(said, "Nothing happens.")), "'" + gvar("FIXI10S") + "' '" + said + "'");
var gem = item("a fixi10 gem", V), ring = item("a fixi10 ring", V);
scriptOn(ring, "COMBINE_PROG gem\nmpsetvar FIXTEST FIXI10C $o+$t\n~\n");
cmd("Combine", "combine gem with ring");
check("combine_prog", gvar("FIXI10C") == "a fixi10 gem+a fixi10 ring", "'" + gvar("FIXI10C") + "'");
said = cmd("Use", "use gem on ring");
check("nothing", has(said, "Nothing happens."), "'" + said + "'");
check("help", has(CMLib.help().getHelpText("USE", M, false), "combinations.txt") && has(CMLib.help().getHelpText("COMBINE", M, false), "COMBINE")
	&& has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "USE_PROG"), "no help");

if (before == null) F["delete()"](); else java.nio.file.Files.write(F.toPath(), before.getBytes("UTF-8"));
F.setLastModified(java.lang.System.currentTimeMillis() + 10000);
V.setPlayerStats(null); V.destroy();
[hinge, lock].forEach(function (x) { x.destroy(); });
var c2 = CMLib.catalog().getCatalogItem("the old fixi10 smuggler's map"); if (c2 != null) CMLib.catalog().delCatalog(c2);
done();
