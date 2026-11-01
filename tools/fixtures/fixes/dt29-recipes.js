// DT29: VALIDATE RECIPES checks every recipe row, extension file and table, and reports each problem with its file and row.
FIXFILE = "dt29-recipes";
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixcrafter");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function validate(words) { var L = new java.util.ArrayList(); L.add("VALIDATE"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("ValidateCmd").execute(B, L, 0); return out(); }

// a blacksmith's file of the test's own, with an extension file: every problem, not only the first
var fmt = "" + CMClass.getAbility("Blacksmithing").getRecipeFormat();
var probs = "" + CMLib.ableParms().validateRecipes("/resources/_fixes/dt29bad.txt", fmt);
check("good-row", !has(probs, "dt29bad.txt row 1:"), probs);
check("bad-class", has(probs, "dt29bad.txt row 2: ") && has(probs, "'GenNoSuchFixClass' for column ITEM_CLASS_ID") && !has(probs, " at row "), probs);
check("extension", has(probs, "dt29bad.txt.1 row 1: 11 columns, and the format has 9"), probs);
check("missing", has(CMLib.ableParms().validateRecipes("/resources/_fixes/nosuchfixrecipes.txt", fmt), "not found"), "a file that is not there");
// the command
check("usage", has(validate(""), "VALIDATE RECIPES ([SKILL]/ALL)"), "no words");
check("unknown", has(validate("RECIPES Flurbling"), "There is no skill 'Flurbling'."), "an unknown skill");
var said = validate("RECIPES Blacksmithing");
check("one", has(said, "VALIDATE RECIPES: ") && has(said, " problems in 1 skills."), said);
said = validate("RECIPES Hunting");
check("hunting", has(said, "VALIDATE RECIPES: "), said);
said = validate("RECIPES ALL");
check("all", has(said, "VALIDATE RECIPES: ") && has(said, " skills."), said);
// what the shipped files have, for the plan's notes
java.lang.System.out.println("FIXINFO dt29 " + ("" + said).split("\n")[0]);
var lines = ("" + said).split("\n");
for (var i = 1; i < lines.length && i < 40; i++) java.lang.System.out.println("FIXINFO dt29 " + lines[i]);

B.setSession(null);
B.destroy();
done();
