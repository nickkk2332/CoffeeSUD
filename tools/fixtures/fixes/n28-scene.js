// N28: Scene: ACTOR name=mask, "name: say/emote/command" lines, PAUSE n, ECHO, DO (MOBPROG, $n the player), a line a tick;
// ONCE per character (the SCENE_<NAME> variable), REPEAT=n days, IF= a script condition; INTERRUPTIBLE (spoken to, the
// actors stop and turn; again once the speaker has left and come back); stops if an actor or the player goes; SELF on an
// NPC; FILE=; the run state; parameter problems.
FIXFILE = "n28-scene";
var TK = CM.core.interfaces.Tickable;
var CMFile = CM.core.CMFile;
var Resources = CM.core.Resources;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixn28pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function run(cmd, words) { var J = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) J.add(w[i]); out(); CMClass.getCommand(cmd).execute(V, J, 0); return out(); }
function npc(name, room) { var m = newMob("GenMob", room); m.setName(name); return m; }
function scene(host, parms) { var B = CMClass.getBehavior("Scene"); B.setParms(parms); host.addBehavior(B); return B; }
function tickRoom(B, room) { out(); B.tick(room, TK.TICKID_ROOM_BEHAVIOR); return out(); }
function debug(B) { return s(B.getStat("DEBUG")); }
function seen(name) { return s(VARS.getVar("Fixn28pip", "SCENE_" + name)); }
var mobs = [];

// the barracks at night: two guards, once
var tall = npc("Fixn28tall", R), shorty = npc("Fixn28short", R);
mobs.push(tall, shorty);
var B = scene(R, "NAME=bribes;ACTOR guard1=Fixn28tall;ACTOR guard2=-NAMES +Fixn28short;"
	+ "guard1: say Heard the bandits hit the mill again.;guard2: emote spits.;PAUSE 2;"
	+ "guard1: say The [[captain]] takes their coin, they say.;ECHO The torches gutter.;DO mpsetvar FIXTEST FIXN28DO $n");
check("parse", field(B, "steps").size() == 6 && field(B, "actors").size() == 2 && field(B, "problems").size() == 0, debug(B));
clearVar("FIXN28DO");
var heard = tickRoom(B, R);
check("line1", has(heard, "Heard the bandits hit the mill again."), heard);
check("playing", has(debug(B), "playing step 1 of 6 for Fixn28pip"), debug(B));
heard = tickRoom(B, R);
check("line2", has(heard, "spits."), heard);
heard = tickRoom(B, R) + tickRoom(B, R);
check("pause", heard.indexOf("captain") < 0 && heard.indexOf("torches") < 0, "the pause said: " + heard);
heard = tickRoom(B, R);
check("line4", has(heard, "The captain takes their coin, they say.") && has(heard, "(You can ask about captain now.)"), heard);
heard = tickRoom(B, R);
check("echo", has(heard, "The torches gutter."), heard);
tickRoom(B, R);
check("do", gvar("FIXN28DO") == "Fixn28pip", "DO set '" + gvar("FIXN28DO") + "'");
check("done", has(debug(B), "idle") && seen("BRIBES").length > 0, debug(B) + " / SCENE_BRIBES '" + seen("BRIBES") + "'");
heard = tickRoom(B, R) + tickRoom(B, R);
check("once", !has(heard, "bandits"), "played again: " + heard);

// IF=: only when it holds
var R2 = scratchRoom();
mobs.push(npc("Fixn28a", R2));
R2.bringMobHere(V, false);
var B2 = scene(R2, "NAME=friends;IF=VAR($n FIXN28OK == yes);ACTOR a=Fixn28a;a: say Fixn28 only for friends.");
VARS.setVar("Fixn28pip", "FIXN28OK", "");
check("if-no", !has(tickRoom(B2, R2), "only for friends"), "played without the condition");
VARS.setVar("Fixn28pip", "FIXN28OK", "yes");
heard = tickRoom(B2, R2);
check("if-yes", has(heard, "Fixn28 only for friends."), heard);

// REPEAT=n days: heard long ago, it plays again; ONCE does not
VARS.setVar("Fixn28pip", "SCENE_AGAIN", "0");
VARS.setVar("Fixn28pip", "SCENE_NEVER", "0");
var B3 = scene(R2, "NAME=again;REPEAT=1;ACTOR a=Fixn28a;a: say Fixn28 again and again.");
var B4 = scene(R2, "NAME=never;ACTOR a=Fixn28a;a: say Fixn28 never again.");
heard = tickRoom(B3, R2) + tickRoom(B4, R2);
check("repeat", has(heard, "again and again") && !has(heard, "never again"), heard);

// INTERRUPTIBLE: spoken to, they stop and turn; not again until the speaker has gone and come back
var R3 = scratchRoom();
var b1 = npc("Fixn28b1", R3), b2 = npc("Fixn28b2", R3);
mobs.push(b1, b2);
R3.bringMobHere(V, false);
var B5 = scene(R3, "NAME=talky;INTERRUPTIBLE;ACTOR b1=Fixn28b1;ACTOR b2=Fixn28b2;b1: say Fixn28 line one.;b2: say Fixn28 line two.;b1: say Fixn28 line three.");
check("int-1", has(tickRoom(B5, R3), "Fixn28 line one."), "line one");
var state = s(B5.getStat("STATE"));
check("state", has(state, '<SCENE AT="1"') && has(state, 'FOR="Fixn28pip"'), state);
var B5b = CMClass.getBehavior("Scene");
B5b.setParms(B5.getParms());
B5b.setStat("STATE", state);
check("restored", s(B5b.getStat("STATE")) == state, s(B5b.getStat("STATE")));
heard = run("Say", "SAYTO Fixn28b1 excuse me");
check("turned", has(heard, "stop") && has(heard, "turn"), heard);
check("int-idle", has(debug(B5), "idle") && has(debug(B5), "stopped by Fixn28pip"), debug(B5));
check("int-held", !has(tickRoom(B5, R3), "line"), "played again with the speaker still here");
check("int-unseen", seen("TALKY") == "", "an interrupted scene counted as heard");
R.bringMobHere(V, false);
tickRoom(B5, R3);
R3.bringMobHere(V, false);
check("int-again", has(tickRoom(B5, R3), "Fixn28 line one."), "not played on coming back");
// an actor goes: it stops, not heard
R.bringMobHere(b2, false);
tickRoom(B5, R3);
check("actor-gone", has(debug(B5), "idle") && seen("TALKY") == "", debug(B5));
check("no-cast", !has(tickRoom(B5, R3), "line"), "played without its cast");

// SELF on an NPC, a line a tick of the NPC
var R4 = scratchRoom();
var solo = npc("Fixn28solo", R4);
mobs.push(solo);
R4.bringMobHere(V, false);
var B6 = scene(solo, "NAME=solo;SELF: say Fixn28 talking to myself.;SELF: emote shrugs.");
out(); B6.tick(solo, TK.TICKID_MOB); heard = out();
check("self", has(heard, "Fixn28 talking to myself."), heard);
out(); B6.tick(solo, TK.TICKID_MOB); heard = out();
check("self-2", has(heard, "shrugs."), heard);

// FILE=, and what it cannot read
new CMFile(Resources.makeFileResourceName("fixn28scene.txt"), null, CMFile.FLAG_FORCEALLOW).saveText(
	"# the guards\nNAME=filed\nACTOR g=Fixn28tall\ng: say one; and on\nPAUSE 1\ng: emote nods.\n");
var B7 = CMClass.getBehavior("Scene");
B7.setParms("FILE=fixn28scene.txt");
check("file", field(B7, "steps").size() == 3 && field(B7, "actors").size() == 1, debug(B7));
B7.setParms("ACTOR x=Fixn28nobody;y: say hello;gibberish here");
check("problems", field(B7, "problems").size() == 2 && has(debug(B7), "'Y' is not an ACTOR"), debug(B7));

mobs.forEach(function (m) { m.destroy(); });
["BRIBES", "FRIENDS", "AGAIN", "NEVER", "TALKY", "SOLO"].forEach(function (n) { VARS.setVar("Fixn28pip", "SCENE_" + n, ""); });
VARS.setVar("Fixn28pip", "FIXN28OK", "");
VARS.setVar("Fixn28pip", "KNOWN_TOPICS", "");
V.setPlayerStats(null); V.destroy();
[R, R2, R3, R4].forEach(function (r) { r.destroy(); });
done();
