// SC7: MPWAIT ticks pauses the body and resumes at the next line, $0-$9
// kept; inside IF and SWITCH; MPWAIT UNTIL (condition) [max]; refused in
// FOR; a pending wait is saved with the engine's state; CANCELWAITS.
FIXFILE = "sc7-wait";
var R = M.location();
["FIXSC7A", "FIXSC7B", "FIXSC7C", "FIXSC7D", "FIXSC7E", "FIXSC7F", "FIXSC7GO"].forEach(clearVar);
function fn(S, host, f) { return S.callFunction(host, f, "", M); }
var m = newMob("GenMob", R); m.setName("a fixsc7 shrine");
var S = scriptOn(m, "FUNCTION_PROG start\n"
	+ "mpargset $0 kept\n"
	+ "mpsetvar FIXTEST FIXSC7A one\n"
	+ "mpwait 2\n"
	+ "mpsetvar FIXTEST FIXSC7A $<FIXTEST FIXSC7A>:two:$0\n"
	+ "~\n"
	+ "FUNCTION_PROG blocks\n"
	+ "if var(FIXTEST FIXSC7GO == yes)\n"
	+ " mpsetvar FIXTEST FIXSC7B before\n"
	+ " switch yes\n"
	+ "  case yes\n"
	+ "   mpwait 1\n"
	+ "   mpsetvar FIXTEST FIXSC7B $<FIXTEST FIXSC7B>:case\n"
	+ " endswitch\n"
	+ " mpsetvar FIXTEST FIXSC7B $<FIXTEST FIXSC7B>:inside\n"
	+ "endif\n"
	+ "mpsetvar FIXTEST FIXSC7B $<FIXTEST FIXSC7B>:after\n"
	+ "~\n"
	+ "FUNCTION_PROG until\n"
	+ "mpwait until (var(FIXTEST FIXSC7GO == go)) 5\n"
	+ "mpsetvar FIXTEST FIXSC7C done\n"
	+ "~\n"
	+ "FUNCTION_PROG loop\n"
	+ "for $1 = 1 to 2\n"
	+ " mpwait 1\n"
	+ " mpsetvar FIXTEST FIXSC7D $<FIXTEST FIXSC7D>x\n"
	+ "next\n"
	+ "~\n"
	+ "FUNCTION_PROG cancel\n"
	+ "mpsetinternal CANCELWAITS\n"
	+ "~\n");
fn(S, m, "start");
var a1 = gvar("FIXSC7A");
S.dequeResponses(null);
var a2 = gvar("FIXSC7A");
var state = "" + S.getStateXML();
S.dequeResponses(null);
check("wait", a1 == "one" && a2 == "one" && gvar("FIXSC7A") == "one:two:kept", "at once '" + a1 + "', a tick later '" + a2 + "', after '" + gvar("FIXSC7A") + "'");
check("saved", state.indexOf('W="1"') >= 0 && state.indexOf("<T0>kept</T0>") >= 0, "the engine's state: " + state);

VARS.setVar("FIXTEST", "FIXSC7GO", "yes");
fn(S, m, "blocks");
var b1 = gvar("FIXSC7B");
runQueued(S);
check("blocks", b1 == "before" && gvar("FIXSC7B") == "before:case:inside:after", "at once '" + b1 + "', after '" + gvar("FIXSC7B") + "'");

VARS.setVar("FIXTEST", "FIXSC7GO", "no");
fn(S, m, "until");
S.dequeResponses(null); S.dequeResponses(null);
var c1 = gvar("FIXSC7C");
VARS.setVar("FIXTEST", "FIXSC7GO", "go");
S.dequeResponses(null);
check("until", c1 == "" && gvar("FIXSC7C") == "done", "before '" + c1 + "', after '" + gvar("FIXSC7C") + "'");

fn(S, m, "loop");
check("loop", gvar("FIXSC7D") == "xx", "FIXSC7D='" + gvar("FIXSC7D") + "' (a FOR does not wait)");

// a wait put back from the saved state runs in a new engine
var m2 = newMob("GenMob", R); m2.setName("a fixsc7 shrine");
var S2 = CMClass.getCommon("DefaultScriptingEngine");
S2.setScript(S.getScript());
m2.addScript(S2);
clearVar("FIXSC7A");
S2.setStateXML(state);
fn(S2, m2, "nosuchfunction");
runQueued(S2);
check("restored", gvar("FIXSC7A") == ":two:kept", "FIXSC7A='" + gvar("FIXSC7A") + "'");

// CANCELWAITS drops them
clearVar("FIXSC7A");
fn(S, m, "start");
fn(S, m, "cancel");
runQueued(S);
check("cancel", gvar("FIXSC7A") == "one", "FIXSC7A='" + gvar("FIXSC7A") + "'");
m.destroy(); m2.destroy();
done();
