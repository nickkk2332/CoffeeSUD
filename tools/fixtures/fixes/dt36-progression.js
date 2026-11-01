// DT36: PROGRESSION shows the level curve, and the experience the content gives by area.
FIXFILE = "dt36-progression";
var R = M.location();
var A = R.getArea();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixbalancer");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function prog(words) { var L = new java.util.ArrayList(); L.add("PROGRESSION"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("ProgressionCmd").execute(B, L, 0); return out(); }
var cmdCls = CMClass.getCommand("ProgressionCmd").getClass();
function areaXP() { return cmdCls.getMethod("areaXP", java.lang.Class.forName("com.planet_ink.coffee_mud.Areas.interfaces.Area")).invoke(null, A); }

check("usage", has(prog(""), "PROGRESSION [CLASS] ([RACE]) ([TO LEVEL])"), "no words");
check("no-class", has(prog("Flurbmancer"), "There is no class 'Flurbmancer'."), "an unknown class");
var said = prog("Apprentice Human 5");
check("curve", has(said, "Apprentice (Apprentice), Human, to level 5:") && has(said, "Lvl XP HP Mana Move Prac Trn Skills"), said);
var rows = ("" + said).split("\n"), five = false, one = false;
for (var i = 0; i < rows.length; i++) { if (/^\s*5\s+\d+\s+\d+/.test(rows[i])) five = true; if (/^\s*1\s+\d+\s+\d+/.test(rows[i])) one = true; }
check("rows", one && five, said);
// the content's experience: a script's MPEXP and a room's Prop_EnterAdjuster count
var before = areaXP();
var s = scriptedMob("a fixes rewarder", "SPEECH_PROG thanks\nmpexp $n 250\n~\n");
var withScript = areaXP();
check("mpexp", withScript - before == 250, "the area's XP went from " + before + " to " + withScript);
var adj = CMClass.getAbility("Prop_EnterAdjuster");
adj.setMiscText("expe+75");
R.addNonUninvokableEffect(adj);
var withProp = areaXP();
check("enter-adjuster", withProp - withScript == 75, "with Prop_EnterAdjuster " + withProp);
said = prog("XP " + A.Name());
check("xp", has(said, "Experience the content gives") && has(said, "achievements: ") && has(said, A.Name() + ": " + withProp + " (") && has(said, " in all, level "), said);
R.delEffect(adj);
s.destroy();
B.setSession(null);
B.destroy();
done();
