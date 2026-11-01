// DT25: RECORD INPUT writes what is typed to a playtest file, after a header of where the game started.
FIXFILE = "dt25-record";
var IR = CM.core.InputRecord;
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixrecorder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function rec(words) { var L = new java.util.ArrayList(); L.add("RECORD"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("RecordCmd").execute(B, L, 0); return out(); }

check("idle", has(rec("INPUT"), "Nothing is being recorded."), "not recording yet");
check("mark-idle", has(rec("MARK nothing here"), "Nothing is being recorded."), "a mark with no recording");
var said = rec("INPUT fixrecording");
var F = IR.file();
check("start", IR.isRecording() && F != null && has(F.getPath(), "recordings") && has(F.getPath(), "fixrecording.play") && has(said, "Recording what is typed to "), said);
// the session hands each typed line here; two characters, a mark, a line that would read as the runner's own
IR.record(B, "look");
IR.record(M, "say hello there");
IR.record(B, "   ");
rec("MARK the loop starts here");
IR.record(B, "#expect not a command");
check("count", IR.lines() == 3, "lines " + IR.lines());
said = rec("INPUT OFF");
check("stop", !IR.isRecording() && has(said, "Stopped recording: 3 lines in "), said);
var text = "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
check("header", has(text, "# Recorded input (RECORD INPUT), ") && has(text, "#builder\n") && has(text, "# world ") && has(text, "# game time "), text);
check("lines", has(text, " Fixrecorder\nlook\n") && has(text, " " + M.Name() + "\nsay hello there\n") && !has(text, "\n   \n"), text);
check("mark", has(text, "# MARK ") && has(text, "Fixrecorder: the loop starts here"), text);
check("escaped", has(text, "# (typed) #expect not a command") && !has(text, "\n#expect not a command"), text);
check("end", has(text, "# end of the recording, 3 lines"), text);
// a file the runner reads: no line but comments and the header's own
var lines = text.split("\n"), odd = [];
for (var i = 0; i < lines.length; i++) {
	var l = lines[i];
	if (l.length == 0 || l.indexOf("# ") == 0 || l == "#builder" || l == "#realtime" || l.indexOf("#seed ") == 0) continue;
	if (l == "look" || l == "say hello there") continue;
	odd.push(l);
}
check("runner-lines", odd.length == 0, "lines the runner would not expect: " + odd.join(" | "));
F["delete"]();
// RECORD with other words is still the Archon's skill
check("no-skill", has(rec("SET FILENAME fixes"), "RECORD INPUT [FILE]/OFF, RECORD MARK [TEXT]"), "RECORD with no skill");
B.addAbility(CMClass.getAbility("Archon_Record"));
said = rec("SET FILENAME fixes");
check("skill", has(said, "Ok."), "RECORD SET FILENAME said " + said);

B.setSession(null);
B.destroy();
done();
