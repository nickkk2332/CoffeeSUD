// N7: GenBehavior: CREATE BEHAVIOR and its editor, PARM() and parm() for each copy's own values,
// MODIFY BEHAVIOR reaching every copy, AHELP, MPBEHAVE, WHEN=, the world files, DESTROY and loading back.
FIXFILE = "n7-genbehavior";
var GB = CM.core.GenBehaviors;
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
// a builder whose session the editor takes for a real one
var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
S.initialize(null, "MEMORY");
var B = newMob("GenMob", R);
B.setName("Fixn7builder");
B.setSavable(false);
B.setSession(S);
S.setMob(B);
var mark = 0;
function out() { var all = s(field(S, "bout").toString()); var o = all.substring(mark); mark = all.length; return o; }
function feed(lines) { for (var i = 0; i < lines.length; i++) S.setFakeInput(lines[i]); }
function cmd(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); M.doCommand(L, 0); }
function record(id) {
	var recs = CMLib.database().DBReadCommands();
	for (var i = 0; i < recs.size(); i++) if (s(recs.get(i).ID()) == id) return recs.get(i);
	return null;
}
var ID = "Fixn7Toll";
if (GB.find(ID) != null) GB.destroy(B, ID);

// CREATE: the editor, fields 1, 2 and 4 changed
out();
feed(["1", "Fixn7 toll keeper", "2", "Fixn7 help: asks a toll at a bridge.", "4", "price=20 exit=north", ""]);
var P = GB.create(B, ID);
var said = out();
check("create", P != null && GB.find(ID) == P && s(P.name()) == "Fixn7 toll keeper" && s(P.getStat("PARMS")) == "price=20 exit=north"
	&& has(said, "The behaviour Fixn7Toll is made."), said);
var rec = record(ID);
check("stored", rec != null && s(rec.typeClass()) == "GenBehavior" && has(rec.data(), "Fixn7 help"), rec == null ? "no record" : rec.data());
check("not-builtin", GB.create(B, "Mobile") == null && has(out(), "Mobile is a behaviour the game has"), "made over Mobile");
check("not-twice", GB.create(B, ID) == null && has(out(), "already exists: MODIFY BEHAVIOR Fixn7Toll"), "made twice");
check("bad-id", GB.create(B, "fix n7") == null && has(out(), "is not a good ID"), "a bad ID");

// MODIFY one field, by the command: a script that reads its parameters, in MOBPROG and JavaScript
cmd("MODIFY BEHAVIOR " + ID + " SCRIPT RAND_PROG 100\\nMPSETVAR FIXTEST FIXN7_$i $%PARM(price)%:$%PARM(exit)%\\n<SCRIPT>\\nsetVar('FIXTEST', 'FIXN7JS_' + host().Name(), parm('price'));\\n</SCRIPT>\\nIF PARM(free)\\nMPSETVAR FIXTEST FIXN7FREE_$i yes\\nENDIF\\n~");
check("modify", has(P.getStat("SCRIPT"), "MPSETVAR FIXTEST FIXN7_$i") && has(record(ID).data(), "FIXN7JS_"), s(P.getStat("SCRIPT")));

// two guards, each with its own values
function guard(name, parms) {
	var g = newMob("GenMob", R);
	g.setName(name);
	var b = CMClass.getBehavior(ID);
	b.setParms(parms);
	g.addBehavior(b);
	return g;
}
["FIXN7_Fixn7guardA", "FIXN7_Fixn7guardB", "FIXN7JS_Fixn7guardA", "FIXN7FREE_Fixn7guardA", "FIXN7FREE_Fixn7guardB"].forEach(clearVar);
var gA = guard("Fixn7guardA", "price=5");
var gB = guard("Fixn7guardB", "price=50 exit=west free=1");
var bA = gA.fetchBehavior(ID);
check("instance", bA != null && s(bA.ID()) == ID && s(bA.getParms()) == "price=5" && s(bA.name()) == "Fixn7 toll keeper", bA == null ? "none" : bA.getParms());
tickMob(gA); tickMob(gB);
check("parm", gvar("FIXN7_Fixn7guardA") == "5:north" && gvar("FIXN7_Fixn7guardB") == "50:west", gvar("FIXN7_Fixn7guardA") + " / " + gvar("FIXN7_Fixn7guardB"));
check("parm-js", gvar("FIXN7JS_Fixn7guardA") == "5", "'" + gvar("FIXN7JS_Fixn7guardA") + "'");
check("parm-if", gvar("FIXN7FREE_Fixn7guardB") == "yes" && gvar("FIXN7FREE_Fixn7guardA") == "", gvar("FIXN7FREE_Fixn7guardA") + " / " + gvar("FIXN7FREE_Fixn7guardB"));

// one change to the definition reaches both
cmd("MODIFY BEHAVIOR " + ID + " PARMS price=20 exit=south");
tickMob(gA); tickMob(gB);
check("one-fix", gvar("FIXN7_Fixn7guardA") == "5:south" && gvar("FIXN7_Fixn7guardB") == "50:west", gvar("FIXN7_Fixn7guardA") + " / " + gvar("FIXN7_Fixn7guardB"));
// a copy, as a mob copied keeps it
var gC = gA.copyOf();
var bC = gC.fetchBehavior(ID);
check("copy", bC != null && bC != bA && s(bC.getParms()) == "price=5", bC == null ? "none" : bC.getParms());
gC.destroy();

// AHELP
var help = s(CMLib.help().getHelpText(ID, M, true));
check("ahelp", has(help, "Fixn7Toll") && has(help, "Fixn7 help: asks a toll") && has(help, "price=20 exit=south"), help);

// MPBEHAVE gives it as any behaviour
var giver = scriptedMob("a fixn7 clerk", "RAND_PROG 100\nmpbehave " + ID + " $i price=7\nmpdisable\n~\n");
tickMob(giver);
var given = giver.fetchBehavior(ID);
check("mpbehave", given != null && has(given.getParms(), "price=7"), given == null ? "none" : given.getParms());
giver.destroy();

// WHEN= works on its parameters, as on any behaviour's
clearVar("FIXN7_Fixn7guardW");
var gW = guard("Fixn7guardW", "price=9 WHEN=\"-NAMES +nobodyfixn7\"");
tickMob(gW);
check("when", gvar("FIXN7_Fixn7guardW") == "", "it ran with its WHEN unmet: " + gvar("FIXN7_Fixn7guardW"));
gW.destroy();

// the world files: custom/behaviors/, the script beside it
var WFC = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles");
var kinds = callTyped(null, WFC, "customFiles", [], []);
var bfiles = kinds.get("behaviors");
var element = "", script = "";
for (var it = bfiles.entrySet().iterator(); it.hasNext();) {
	var e = it.next();
	if (has(e.getValue(), "<BEHAVIOR ID=\"Fixn7Toll\">")) element = s(e.getValue());
	if (has(e.getValue(), "MPSETVAR FIXTEST FIXN7_$i")) script = s(e.getKey());
}
check("files", element.length > 0 && has(element, "SCRIPT>FILE:" + script) && script.indexOf(".script") > 0, element);

// DESTROY; and made again from its record, as at boot
var kept = record(ID);
var keptData = s(kept.data());
cmd("DESTROY BEHAVIOR " + ID);
check("destroy", GB.find(ID) == null && CMClass.getBehavior(ID) == null && record(ID) == null, "still there");
CMLib.database().DBCreateCommand(ID, "GenBehavior", keptData);
var L = new java.util.ArrayList();
L.add(record(ID));
check("load", GB.loadAll(L) == 1 && GB.find(ID) != null && s(GB.find(ID).getStat("PARMS")) == "price=20 exit=south", "not loaded");
var again = CMClass.getBehavior(ID);
again.setParms("price=3");
var gD = newMob("GenMob", R);
gD.setName("Fixn7guardD");
gD.addBehavior(again);
clearVar("FIXN7_Fixn7guardD");
tickMob(gD);
check("loaded-runs", gvar("FIXN7_Fixn7guardD") == "3:south", "'" + gvar("FIXN7_Fixn7guardD") + "'");

GB.destroy(B, ID);
[gA, gB, gD].forEach(function (m) { m.destroy(); });
B.setSession(null); B.destroy();
done();
