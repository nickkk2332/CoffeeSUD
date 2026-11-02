// N11: MudChat: ?guard lines (a script condition, $t the speaker), & replies (a MOBPROG line, the speaker $n),
// $@group per speaker, $@!group kept (in a saved variable) until changed, $@- back to the base group.
FIXFILE = "n11-mudchat";
var DT = CM.core.DialogueTime;
var CMFile = CM.core.CMFile;
var Resources = CM.core.Resources;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var wasDT = s(DT.setting());
DT.set("INSTANT");
new CMFile(Resources.makeFileResourceName("fixn11.dat"), null, CMFile.FLAG_FORCEALLOW).saveText(
	">foreman\n"
	+ "?VAR($t FIXN11HERO == yes)\n(hello)\n9\"Fixn11 you are the hero of the mine!\n"
	+ "(hello)\n9\"Fixn11 good day.\n"
	+ "(work)\n9\"Fixn11 clear the rats and I'll pay you.~&mpsetvar $n FIXN11JOB given$@!foreman_quest\n"
	+ "(gossip)\n9\"Fixn11 the rats came last spring.$@foreman_talk\n\n"
	+ ">foreman_quest\n(hello)\n9\"Fixn11 back so soon? The rats!\n(done)\n9\"Fixn11 then we are square.$@-\n\n"
	+ ">foreman_talk\n(hello)\n9\"Fixn11 where was I? The rats.\n");
Resources.removeResource("MUDCHAT:fixn11.dat");
var R = scratchRoom();
function speaker(name) {
	var m = newMob("GenMob", R);
	m.setName(name);
	var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	S.initialize(null, "MEMORY"); m.setSession(S); S.setMob(m);
	m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	return { mob: m, s: S, mark: 0 };
}
var pip = speaker("Fixn11pip"), bob = speaker("Fixn11bob");
function out(p) { var all = s(field(p.s, "bout").toString()); var o = all.substring(p.mark); p.mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function foreman() {
	var f = newMob("GenMob", R);
	f.setName("Fixn11foreman");
	var MC = CMClass.getBehavior("MudChat");
	MC.setParms("fixn11.dat=foreman");
	f.addBehavior(MC);
	return f;
}
var F = foreman();
function say(p, words) {
	out(p);
	var L = new java.util.ArrayList(); L.add("SAYTO"); L.add("Fixn11foreman"); L.add(words);
	CMClass.getCommand("Say").execute(p.mob, L, 0);
	DT.flush(p.mob);
	return out(p);
}
VARS.setVar("Fixn11pip", "FIXN11HERO", "");
VARS.setVar("Fixn11pip", "FIXN11JOB", "");
VARS.setVar("Fixn11foreman", "MUDCHAT_GROUP_FIXN11PIP", "");
check("lint", has(CM.Behaviors.MudChat.lintChat("?\n(hi)\n9\"x\n"), "a guard (?) with no condition")
	&& CM.Behaviors.MudChat.lintChat("?VAR($t X == 1)\n(hi)\n9\"x\n").size() == 0, s(CM.Behaviors.MudChat.lintChat("?\n(hi)\n9\"x\n")));

// a guard: the first (hello) only for the hero
check("unguarded", has(say(pip, "hello"), "Fixn11 good day."), "no plain greeting");
VARS.setVar("Fixn11pip", "FIXN11HERO", "yes");
check("guarded", has(say(pip, "hello"), "Fixn11 you are the hero of the mine!"), "no hero's greeting");
VARS.setVar("Fixn11pip", "FIXN11HERO", "");
// & and $@!: the job, kept
var said = say(pip, "work");
check("work", has(said, "Fixn11 clear the rats"), said);
check("and", s(VARS.getVar("Fixn11pip", "FIXN11JOB")) == "given", "FIXN11JOB '" + VARS.getVar("Fixn11pip", "FIXN11JOB") + "'");
check("kept-var", s(VARS.getVar("Fixn11foreman", "MUDCHAT_GROUP_FIXN11PIP")) == "foreman_quest", "'" + VARS.getVar("Fixn11foreman", "MUDCHAT_GROUP_FIXN11PIP") + "'");
check("switched", has(say(pip, "hello"), "Fixn11 back so soon?"), "not switched");
// another speaker has the base group; then pip's switch is back
check("other", has(say(bob, "hello"), "Fixn11 good day."), "bob got pip's group");
check("own-again", has(say(pip, "hello"), "Fixn11 back so soon?"), "pip lost the switch");
// a foreman made again (as after a restore) reads the kept switch
F.destroy();
F = foreman();
check("restored", has(say(pip, "hello"), "Fixn11 back so soon?"), "the kept switch was not read back");
// $@-: back to the base group, forgotten
check("done", has(say(pip, "done"), "Fixn11 then we are square."), "no answer to done");
check("base", has(say(pip, "hello"), "Fixn11 good day.") && s(VARS.getVar("Fixn11foreman", "MUDCHAT_GROUP_FIXN11PIP")) == "", "not back to the base group");
// a passing switch ($@) for bob alone
check("gossip", has(say(bob, "gossip"), "Fixn11 the rats came last spring."), "no gossip");
check("bob-talk", has(say(bob, "hello"), "Fixn11 where was I?"), "bob not switched");
check("pip-base", has(say(pip, "hello"), "Fixn11 good day."), "pip got bob's switch");
check("bob-again", has(say(bob, "hello"), "Fixn11 where was I?"), "bob lost his switch");

DT.set(wasDT);
VARS.setVar("Fixn11pip", "FIXN11JOB", "");
new CMFile(Resources.makeFileResourceName("fixn11.dat"), null, CMFile.FLAG_FORCEALLOW)["delete"]();
F.destroy();
[pip, bob].forEach(function (p) { p.mob.setPlayerStats(null); p.mob.destroy(); });
R.destroy();
done();
