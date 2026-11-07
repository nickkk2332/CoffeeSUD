// A10 telegraph responses and chains: MPTELEGRAPH RESPOND="word=SPARE|word=command|word=INTERRUPT" (each foe's first
// word counts: SPARE is not hit, a command is run on them instead of the blow, INTERRUPT stops the wind-up at once) and
// THEN=fn (the host's function that starts the next step, once the blow lands; not after an interrupt). The responses
// are in the wind-up's saved text.
FIXFILE = "a10-responses";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom(), made = [];
function npc(name) { var m = newMob("GenMob", R); m.setName(name); m.baseState().setHitPoints(1000); m.recoverMaxState(); m.resetToMaxState(); made.push(m); return m; }
var V = npc("Fixa10pip"), tam = npc("Fixa10tam"), bo = npc("Fixa10bo");
[V, tam, bo].forEach(function (m) { m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); });
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function type(m, word) { CMLib.commands().handleUnknownCommand(m, new java.util.ArrayList(java.util.Arrays.asList([word]))); }
var dragon = npc("Fixa10dragon");
function vars() { return "pip '" + gvar("FIXA10_Fixa10pip") + "' tam '" + gvar("FIXA10_Fixa10tam") + "' bo '" + gvar("FIXA10_Fixa10bo") + "' then '" + gvar("FIXA10_THEN") + "'"; }
function clear() { ["FIXA10_Fixa10pip", "FIXA10_Fixa10tam", "FIXA10_Fixa10bo", "FIXA10_THEN"].forEach(clearVar); }
try {
	var T = scriptOn(dragon, "FUNCTION_PROG SWEEP\nmptelegraph 2 \"<S-NAME> sweeps its tail back!\" mpsetvar FIXTEST FIXA10_$n hit"
		+ " RESPOND=\"dodge=SPARE|brace=mpsetvar FIXTEST FIXA10_$n braced|parry=INTERRUPT\" THEN=STOMP\n~\n"
		+ "FUNCTION_PROG STOMP\nmpsetvar FIXTEST FIXA10_THEN stomped\n~\n");
	clear();
	T.callFunction(dragon, "SWEEP", "", V);
	var P = dragon.fetchEffect("Prop_Telegraph");
	check("started", P != null && P.responseOutcome("dodge") == "SPARE" && s(P.responseOutcome("brace")) == "mpsetvar FIXTEST FIXA10_$n braced", "no wind-up, or its responses: " + (P == null ? null : P.text()));
	// pip dodges (and a second word does not change it), tam braces, bo does nothing
	out(); type(V, "dodge");
	check("responded", has(out(), "You dodge, ready for it."), "no answer to dodge");
	type(V, "brace");
	check("first-counts", has(out(), "You are already ready (dodge)."), "a second response was taken");
	type(tam, "brace");
	check("in-text", s(P.text()).indexOf("RESPONDED=") >= 0 && s(P.text()).indexOf("=dodge") >= 0 && s(P.text()).indexOf("=brace") >= 0, s(P.text()));
	// the responses survive the text, as a save keeps them
	var P2 = CMClass.getAbility("Prop_Telegraph"); P2.setMiscText(P.text());
	check("restored", field(P2, "responded").size() == 2 && s(P2.telegraphThen()) == "STOMP", "responded " + field(P2, "responded") + " then " + P2.telegraphThen());
	tickMob(dragon); tickMob(dragon);
	check("outcomes", gvar("FIXA10_Fixa10pip") == "" && gvar("FIXA10_Fixa10tam") == "braced" && gvar("FIXA10_Fixa10bo") == "hit", vars());
	check("then", gvar("FIXA10_THEN") == "stomped" && dragon.fetchEffect("Prop_Telegraph") == null, vars());

	// INTERRUPT: the wind-up stops at once; no one is hit and the chain does not go on
	clear(); out();
	T.callFunction(dragon, "SWEEP", "", V);
	type(bo, "parry");
	check("interrupted", dragon.fetchEffect("Prop_Telegraph") == null, "the wind-up went on after a parry");
	tickMob(dragon); tickMob(dragon); tickMob(dragon);
	check("interrupt-no-hit", gvar("FIXA10_Fixa10pip") == "" && gvar("FIXA10_Fixa10bo") == "" && gvar("FIXA10_THEN") == "", vars());
	// a word that is not a response is left alone
	T.callFunction(dragon, "SWEEP", "", V);
	P = dragon.fetchEffect("Prop_Telegraph");
	type(V, "look");
	check("other-words", field(P, "responded").size() == 0, "look counted as a response");
	P.telegraphDone();
} finally {
	VS.setMob(null); V.setSession(null);
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	R.destroy();
}
done();
