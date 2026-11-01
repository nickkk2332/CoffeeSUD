// DT18: SCRIPTTRACE traces one host's scripts to the builder: chances rolled, triggers off, a busy host, lines run, variables set; ISBUILDER().
FIXFILE = "dt18-trace";
var R = M.location();
var ST = CM.core.ScriptTrace;
var cmd = CMClass.getCommand("ScriptTraceCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixtracer");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function trace(words) { var L = new java.util.ArrayList(); L.add("SCRIPTTRACE"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); cmd.execute(B, L, 0); return out(); }

var host = scriptedMob("a fixes traced", "RAND_PROG 100\nmpsetvar FIXTEST FIXTRACED yes\n~\nSPEECH_PROG hello\nmpsetvar FIXTEST FIXTRACESPEECH $g\n~\n");
var S = host.scripts().nextElement();
check("start", has(trace("traced"), "SCRIPTTRACE: host a fixes traced, to you alone"), "SCRIPTTRACE did not start");
tickMob(host);
var t = out();
check("roll", has(t, "[trace a fixes traced] RAND_PROG 100: rolled ") && has(t, " of 100, runs"), t);
check("line", has(t, "  1: mpsetvar FIXTEST FIXTRACED yes"), t);
check("var", has(t, "  FIXTEST FIXTRACED = 'yes'"), t);
// one trigger only
trace("traced SPEECH");
tickMob(host);
check("only", !has(out(), "RAND_PROG"), "RAND_PROG was traced under SPEECH only");
// a trigger turned off, and a busy host
CMClass.getCommand("ScriptCmd").execute(B, (function () { var L = new java.util.ArrayList(); ["SCRIPT", "DISABLE", "traced", "SPEECH"].forEach(function (w) { L.add(w); }); return L; })(), 0);
out();
var bob = newMob("GenMob", R);
bob.setName("Fixtracebob");
function say(words) { S.executeMsg(host, CMClass.getMsg(bob, null, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) '" + words + "'.")); }
say("hello");
check("off", has(out(), "SPEECH_PROG: off (MPNOTRIGGER or SCRIPT DISABLE), skipped"), "no word of the trigger being off");
CMClass.getCommand("ScriptCmd").execute(B, (function () { var L = new java.util.ArrayList(); ["SCRIPT", "ENABLE", "traced", "SPEECH"].forEach(function (w) { L.add(w); }); return L; })(), 0);
out();
host.basePhyStats().setDisposition(host.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_SLEEPING);
host.recoverPhyStats();
say("hello");
check("busy", has(out(), "busy (asleep, fighting, bound or the like), so its triggers wait"), "no word of the host being busy");
host.basePhyStats().setDisposition(host.basePhyStats().disposition() & ~CM.Common.interfaces.PhyStats.IS_SLEEPING);
host.recoverPhyStats();
// OFF
check("off-said", has(trace("OFF"), "SCRIPTTRACE is off."), "OFF said otherwise");
tickMob(host);
check("quiet", !has(out(), "[trace"), "traced after OFF");
check("is-off", !bool(ST.isOn()), "the trace is still on");
function bool(x) { return ("" + x) == "true"; }

// MPSETINTERNAL TRACE TRUE, from inside a script
var selfTracer = scriptedMob("a fixes self tracer", "RAND_PROG 100\nmpsetinternal TRACE TRUE\n~\n");
tickMob(selfTracer);
check("from-script", bool(ST.isOn()) && bool(ST.traces(selfTracer, 0)), "MPSETINTERNAL TRACE TRUE did not trace the host");
ST.stop();
// ISBUILDER()
clearVar("FIXISB"); clearVar("FIXISBV");
var asker = scriptedMob("a fixes builder asker", "RAND_PROG 100\nif isbuilder()\n  mpsetvar FIXTEST FIXISB yes\nendif\nmpsetvar FIXTEST FIXISBV $%ISBUILDER()%\n~\n");
tickMob(asker);
check("isbuilder", gvar("FIXISB") == "yes" && gvar("FIXISBV") == "true", "FIXISB '" + gvar("FIXISB") + "', FIXISBV '" + gvar("FIXISBV") + "'");

["FIXTRACED", "FIXTRACESPEECH", "FIXISB", "FIXISBV"].forEach(clearVar);
B.setSession(null);
B.destroy(); host.destroy(); bob.destroy(); selfTracer.destroy(); asker.destroy();
done();
