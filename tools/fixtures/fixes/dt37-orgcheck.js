// DT37: ORGCHECK reads faction files again from their lines, and checks the world's clans and the governments.
FIXFILE = "dt37-orgcheck";
var OC = CM.core.OrgCheck;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixorgchecker");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function oc(words) { var L = new java.util.ArrayList(); L.add("ORGCHECK"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("OrgCheckCmd").execute(B, L, 0); return out(); }

var text = "NAME=Fixfaction\nMINIMUM=0\nMAXIMUM=1000\n"
	+ "RANGE1=0;399;low;FIXLOW\nRANGE2=500;700;mid;FIXMID\nRANGE3=650;899;high;FIXHIGH\nRANGE3=900;1000;top;FIXTOP\n"
	+ "CHANGE1=MURDER;UP;10%;;\nCHANGE2=NOSUCHFIXTHING;UP;10%;;\n"
	+ "REACTION1=FIXLOW;;Spell_Blindness;\nREACTION2=FIXNONE;;NoSuchFixAbility;\n"
	+ "AFFBEHAV1=Mobile;min=1;-NAMES\nAFFBEHAV2=NoSuchFixBehav;x;-NAMES\n"
	+ "RELATION1=nosuchfixfaction.ini 50%\n";
var probs = "" + OC.checkFaction("fix.ini", text);
check("duplicate", has(probs, "fix.ini line 7: RANGE3 is given again (line 6 too): only this one counts"), probs);
check("gap", has(probs, "nothing holds 400 to 499, between RANGE1 (low) and RANGE2 (mid)"), probs);
check("overlap", has(probs, "RANGE3 (high) and RANGE2 (mid) both hold 650 to 700"), probs);
check("change", has(probs, "CHANGE2: the trigger 'NOSUCHFIXTHING' matches nothing") && !has(probs, "CHANGE1"), probs);
check("reaction", has(probs, "REACTION2: unknown ability, behaviour, command or social 'NoSuchFixAbility'")
	&& has(probs, "REACTION2: the range 'FIXNONE' is no band's code") && !has(probs, "REACTION1"), probs);
check("affbehav", has(probs, "AFFBEHAV2: 'NoSuchFixBehav' is no ability or behaviour") && !has(probs, "AFFBEHAV1"), probs);
check("relation", has(probs, "RELATION1: there is no faction 'nosuchfixfaction.ini'"), probs);
// a mob in the world files naming a clan that does not exist
var F = new java.io.File(new java.io.File(CM.core.save.WorldFiles.worldDir(), "areas"), "fixorgcheck.cmare");
java.nio.file.Files.write(F.toPath(), new java.lang.String("<AREA><MOB><CLAN ROLE=0>Nofix Thieves Guild</CLAN></MOB><MOB><CLAN ROLE=0>Nofix Thieves Guild</CLAN></MOB></AREA>").getBytes("UTF-8"));
var clans = "" + OC.checkWorldClans();
check("clans", has(clans, "2 mobs in fixorgcheck.cmare name clan Nofix Thieves Guild, which does not exist"), clans);
F["delete"]();
// the command
var said = oc("alignment.ini");
check("cmd-file", has(said, "ORGCHECK alignment.ini: "), said);
check("cmd-none", has(oc("nosuchfixfile"), "There is no faction file 'nosuchfixfile'."), "an unknown file");
said = oc("ALL");
check("cmd-all", has(said, "ORGCHECK the factions, clans and governments: "), said);
var lines = ("" + said).split("\n");
for (var i = 0; i < lines.length && i < 30; i++) java.lang.System.out.println("FIXINFO dt37 " + lines[i]);
B.setSession(null);
B.destroy();
done();
