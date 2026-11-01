// B3: scripts and descriptions edited a line at a time: EDIT SCRIPT (inline, and a LOAD= file),
// EDIT DESC, EXTRACT SCRIPT, the check on save, + in the description prompt, and EDITOR=.
FIXFILE = "b3-editor";
var TE = CM.core.TextEditor;
var Form = CM.core.TextEditor.Form;
var WF = CM.core.save.WorldFiles;
var R = M.location();
var CMFile = CM.core.CMFile;
var Resources = CM.core.Resources;
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
// a session the editor takes for a real one: the line editor reads what is queued
var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
S.initialize(null, "MEMORY");
var B = newMob("GenMob", R);
B.setName("Fixb3builder");
B.setSavable(false);
B.setSession(S);
S.setMob(B);
var mark = 0;
function out() { var all = "" + field(S, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function feed(lines) { for (var i = 0; i < lines.length; i++) S.setFakeInput(lines[i]); }
function run(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("EditCmd").execute(B, L, 0); return out(); }
function fileText(path) { var F = new CMFile(Resources.makeFileResourceName(path), null, CMFile.FLAG_FORCEALLOW); return F.exists() ? ("" + F.text()).replace(/\n\r/g, "\n") : null; }
function dropFile(path) {
	new java.io.File(new java.io.File(WF.worldDir(), "resources"), path)["delete"]();
	new java.io.File("resources/" + path)["delete"]();
}
check("session", B.session() != null, "the adapted session is taken for a fake one");

// the lines a script is edited as, and back
var one = "GREET_PROG 100;MPECHO a\\;b;~;";
var lines = TE.toLines(one, Form.SCRIPT);
check("split", lines.size() == 3 && ("" + lines.get(1)) == "MPECHO a\\;b" && ("" + TE.fromLines(lines, one, Form.SCRIPT)) == one, "" + lines);
var js = "FUNCTION_PROG X;<SCRIPT>var a=1;var b=2;</SCRIPT>;~;";
lines = TE.toLines(js, Form.SCRIPT);
check("split-js", lines.size() == 3 && ("" + lines.get(1)) == "<SCRIPT>var a=1;var b=2;</SCRIPT>" && ("" + TE.fromLines(lines, js, Form.SCRIPT)) == js, "" + lines);
var multi = "GREET_PROG 100\nMPECHO hi\n~\n";
check("split-lines", ("" + TE.fromLines(TE.toLines(multi, Form.SCRIPT), multi, Form.SCRIPT)) == multi && TE.toLines(multi, Form.SCRIPT).size() == 3, "" + TE.toLines(multi, Form.SCRIPT));
check("as-file", "" + TE.asFile("GREET_PROG 100;MPECHO hi;~;") == "GREET_PROG 100\nMPECHO hi\n~\n", TE.asFile("GREET_PROG 100;MPECHO hi;~;"));

// EDIT SCRIPT: an inline script, line 1 rewritten
var T = newMob("GenMob", R);
T.setName("Fixb3 editor");
T.setDescription("a fixes editor mob");
var SB = CMClass.getBehavior("Scriptable");
SB.setParms("GREET_PROG 100;MPECHO hello;~;");
T.addBehavior(SB);
out();
feed(["E", "1", "MPECHO edited", "S", "Y"]);
var said = run("EDIT SCRIPT Fixb3 editor");
check("edit", ("" + SB.getParms()) == "GREET_PROG 100;MPECHO edited;~;" && has(said, "Fixb3 editor is changed; SAVE ROOM keeps it."), SB.getParms() + " / " + said);
// one with a problem: cancelled, then kept
feed(["E", "1", "MPNOSUCHFIXCMD x", "S", "Y", "C"]);
said = run("EDIT SCRIPT Fixb3 editor 1");
check("problem-cancel", has(said, "has problems:") && has(said, "MPNOSUCHFIXCMD") && ("" + SB.getParms()) == "GREET_PROG 100;MPECHO edited;~;", SB.getParms() + " / " + said);
feed(["E", "1", "MPNOSUCHFIXCMD x", "S", "Y", "K"]);
said = run("EDIT SCRIPT Fixb3 editor");
check("problem-keep", ("" + SB.getParms()) == "GREET_PROG 100;MPNOSUCHFIXCMD x;~;", SB.getParms() + " / " + said);
SB.setParms("GREET_PROG 100;MPECHO edited;~;");
check("none", has(run("EDIT SCRIPT Fixb3 editor 2"), "has 1 scripts: 1 to 1."), "a second script");
check("nobody", has(run("EDIT SCRIPT nosuchfixmob"), "There is no 'nosuchfixmob' here."), "an unknown target");

// EDIT DESC
feed(["E", "0", "A new fixes description.", "S", "Y"]);
said = run("EDIT DESC Fixb3 editor");
check("desc", ("" + T.description()) == "A new fixes description.", T.description() + " / " + said);
// + in the description prompt opens the editor
feed(["+", "E", "0", "Plus fixes description.", "S", "Y"]);
CMLib.genEd().genDescription(B, T, 1, 1);
check("desc-plus", ("" + T.description()) == "Plus fixes description.", T.description() + " / " + out());

// a script that is only LOAD=file: the file is edited
new CMFile(Resources.makeFileResourceName("fixb3/greet.script"), null, CMFile.FLAG_FORCEALLOW).saveText("GREET_PROG 100\nMPECHO from file\n~\n");
var T2 = newMob("GenMob", R);
T2.setName("Fixb3 loader");
var SB2 = CMClass.getBehavior("Scriptable");
SB2.setParms("LOAD=fixb3/greet.script");
T2.addBehavior(SB2);
out();
feed(["E", "1", "MPECHO file edited", "S", "Y"]);
said = run("EDIT SCRIPT Fixb3 loader");
check("file", fileText("fixb3/greet.script") == "GREET_PROG 100\nMPECHO file edited\n~\n" && ("" + SB2.getParms()) == "LOAD=fixb3/greet.script"
	&& has(said, "fixb3/greet.script is saved; every LOAD=fixb3/greet.script shares it."), fileText("fixb3/greet.script") + " / " + said);
// GenCommand's check reads LOAD= in
new CMFile(Resources.makeFileResourceName("fixb3/cmd.script"), null, CMFile.FLAG_FORCEALLOW).saveText("FUNCTION_PROG EXECUTE\nMPECHO hi\n~\n");
check("with-loads", has(CM.core.ScriptCheck.withLoads("LOAD=fixb3/cmd.script"), "FUNCTION_PROG EXECUTE"), CM.core.ScriptCheck.withLoads("LOAD=fixb3/cmd.script"));

// EXTRACT SCRIPT: to progs/NAME.script, LOAD= in its place
said = run("EXTRACT SCRIPT Fixb3 editor");
check("extract", ("" + SB.getParms()) == "LOAD=progs/fixb3-editor.script" && fileText("progs/fixb3-editor.script") == "GREET_PROG 100\nMPECHO edited\n~\n"
	&& has(said, "it is LOAD=progs/fixb3-editor.script now"), SB.getParms() + " / " + said);
check("extract-again", has(run("EXTRACT SCRIPT Fixb3 editor"), "That script is in progs/fixb3-editor.script already."), "extracted twice");

// EDITOR=WINDOW with no display: the line editor; EDITOR=EXTERNAL: the command named
var P = CMProps.instance();
P.setProperty("EDITOR", "WINDOW");
feed(["Q", "Y"]);
var got = TE.edit(B, "fixes text", "hello there", Form.TEXT);
check("window", got == null && has(out(), "EDITOR=WINDOW needs a display, so the line editor it is."), "" + got);
P.setProperty("EDITOR", "EXTERNAL");
P.setProperty("EDITORCOMMAND", "sed -i s/hello/goodbye/");
got = TE.edit(B, "fixes text", "hello there", Form.TEXT);
check("external", ("" + got) == "goodbye there", "" + got + " / " + out());
P.remove("EDITOR");
P.remove("EDITORCOMMAND");

dropFile("fixb3/greet.script");
dropFile("fixb3/cmd.script");
dropFile("progs/fixb3-editor.script");
T.destroy();
T2.destroy();
B.setSession(null);
B.destroy();
done();
