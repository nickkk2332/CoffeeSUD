// N10: DIALOGUETIME=INSTANT: replies (SPEECH_PROG, GIVE_PROG, MudChat) run before the next prompt, after a player's
// command; TICKS leaves them to the NPC's tick; TIMEMODE DIALOGUE; MudChat DELAY=; WAIT stops when spoken to.
FIXFILE = "n10-dialogue";
var DT = CM.core.DialogueTime;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var was = s(DT.setting());
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixn10pip");
// a session taken for a real one: MudChat answers players only
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function words(w) { var L = new java.util.ArrayList(); w.split(" ").forEach(function (x) { if (x.length > 0) L.add(x); }); return L; }
function say(text) { CMClass.getCommand("Say").execute(V, words("SAY " + text), 0); }
var troll = newMob("GenMob", R);
troll.setName("Fixn10troll");
scriptOn(troll, "SPEECH_PROG riddle\nsayto $n Fixn10 what walks on four legs?\n~\nGIVE_PROG all\nsayto $n Fixn10 thank you for $o.\n~\n");

// TICKS: the reply waits for the troll's tick
DT.set("TICKS");
out();
say("riddle");
var n = DT.flush(V);
check("ticks-wait", n == 0 && !has(out(), "Fixn10 what walks"), "it replied at once with TICKS");
tickMob(troll); tickMob(troll);
check("ticks-tick", has(out(), "Fixn10 what walks"), "no reply on its tick");

// INSTANT: before the next prompt
DT.set("INSTANT");
check("instant", DT.instant(), "not instant");
say("riddle");
n = DT.flush(V);
check("instant-reply", n == 1 && has(out(), "Fixn10 what walks"), "flushed " + n);
// a gift
var gem = CMClass.getItem("GenItem"); gem.setName("a fixn10 gem"); V.addItem(gem);
R.send(V, CMClass.getMsg(V, troll, gem, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
n = DT.flush(V);
check("give", n == 1 && has(out(), "Fixn10 thank you for"), "flushed " + n);
// by the command queue, as the game runs a typed command (in real time for a moment: the test runs in turns)
var GT = CM.core.GameTime;
var turns = GT.isTurnBased();
GT.setTurnBased(false);
V.setActions(5);
V.enqueCommand(words("SAY riddle"), 0, 0.0); // run at once if free, else queued
V.dequeCommand();
GT.setTurnBased(turns);
var q = out();
check("queue", has(q, "riddle") && has(q, "Fixn10 what walks"), q);

// MudChat, DELAY=0 kept in its parameters
var chat = newMob("GenMob", R);
chat.setName("Fixn10keeper");
var MC = CMClass.getBehavior("MudChat");
MC.setParms("DELAY=0 +(hello);9\"Fixn10 well met, traveller.");
chat.addBehavior(MC);
check("delay", has(MC.getParms(), "DELAY=0") && field(MC, "responseDelay") == 0, s(MC.getParms()));
CMClass.getCommand("Say").execute(V, words("SAYTO Fixn10keeper hello"), 0);
n = DT.flush(V);
var mc = out();
check("mudchat", n >= 1 && has(mc, "Fixn10 well met"), "flushed " + n + ": " + mc);

// TIMEMODE DIALOGUE
CMClass.getCommand("TimeMode").execute(V, words("TIMEMODE DIALOGUE TICKS"), 0);
check("timemode", s(DT.setting()) == "TICKS" && has(out(), "Replies come on the NPCs' own ticks"), s(DT.setting()));
CMClass.getCommand("TimeMode").execute(V, words("TIMEMODE DIALOGUE DEFAULT"), 0);
var dflt = out();
check("default", s(DT.setting()) == "" && (CM.core.GameTime.isTurnBased() ? has(dflt, "Replies come at once, before your next prompt, and take no time (the default in turn-based time).")
	: has(dflt, "(the default in real time)")), "'" + DT.setting() + "' " + dflt);

// spoken to: counted for WAIT
var before = DT.spokenTo(V);
R.send(troll, CMClass.getMsg(troll, V, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) to <T-NAMESELF> 'Fixn10 boo'."));
check("spoken", DT.spokenTo(V) == before + 1, "not counted");
R.send(troll, CMClass.getMsg(troll, null, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) 'Fixn10 to all'."));
check("not-to-me", DT.spokenTo(V) == before + 1, "a word to the room counted");
var stop = call(CMClass.getCommand("Wait"), "disturbed", [V]);
check("wait-calm", !stop.getAsBoolean(), "stopped with nothing said");
R.send(troll, CMClass.getMsg(troll, V, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) to <T-NAMESELF> 'Fixn10 wake up'."));
check("wait-stops", stop.getAsBoolean() && has(out(), "You stop waiting: you are spoken to."), "WAIT went on");

DT.set(was);
[troll, chat].forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
