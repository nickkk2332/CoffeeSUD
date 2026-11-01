// DT30: VOCAB tests the translation and parser rules, shows lists.ini values, reloads, and adds a rule to the world's file.
FIXFILE = "dt30-vocab";
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixwordsmith");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function vocab(words) { var L = new java.util.ArrayList(); L.add("VOCAB"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("VocabCmd").execute(B, L, 0); return out(); }

check("usage", has(vocab(""), "VOCAB TEST [TEXT], VOCAB INPUT [COMMAND LINE]"), "no words");
// INPUT: the stock parser's abbreviation rule
var said = vocab("INPUT ge sword");
check("input", has(said, "Command: get sword") && has(said, "[COMMAND-PRE-PROCESSOR] REPLACE "), said);
check("input-none", has(vocab("INPUT look"), "No rule matched."), "a line no rule touches");
// ADD a translation rule to the world's file; TEST shows it working, and which rule it was
said = vocab("ADD FINAL-TRANSLATION replaceall \"fixcolour\" with \"fixcolor\"");
check("add", has(said, "Added to [FINAL-TRANSLATION] in ") && has(said, "replaceall \"fixcolour\" with \"fixcolor\""), said);
said = vocab("TEST the fixcolour of it");
check("test", has(said, "Final translation: the fixcolor of it") && has(said, "[FINAL-TRANSLATION] REPLACEALL \"fixcolour\" with \"fixcolor\""), said);
check("test-none", has(vocab("TEST nothing to change here"), "Final translation: none matched, as written"), "a text no rule touches");
// LIST: a lists.ini value and its file
said = vocab("LIST TECH_LEVEL_NAMES");
check("list", has(said, "TECH_LEVEL_NAMES=") && has(said, "from ") && has(said, "lists.ini"), said);
check("list-none", has(vocab("LIST NOSUCHFIXKEY"), "has NOSUCHFIXKEY."), "a key no file has");
// RELOAD forgets the caches
said = vocab("RELOAD");
check("reload", has(said, "cached rule and list files, and the help"), said);
check("after-reload", has(vocab("TEST the fixcolour of it"), "Final translation: the fixcolor of it"), "the rule after RELOAD");

// take the rule out again
var worldRes = new java.io.File(CM.core.save.WorldFiles.worldDir(), "resources");
var files = worldRes.listFiles();
for (var i = 0; files != null && i < files.length; i++) {
	var F = files[i];
	if (("" + F.getName()).toLowerCase().indexOf("translation_") == 0) {
		var text = "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
		text = text.replace("replaceall \"fixcolour\" with \"fixcolor\"\n", "");
		java.nio.file.Files.write(F.toPath(), new java.lang.String(text).getBytes("UTF-8"));
	}
}
CMLib.lang().clear();
B.setSession(null);
B.destroy();
done();
