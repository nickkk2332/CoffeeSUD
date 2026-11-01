// DT4: CHATTEST, CHATLINT and CHAT RELOAD.
FIXFILE = "dt4-chat";
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var BB = newMob("GenMob", R);
BB.setName("Fixchatter");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
BB.setSession(BS);
BS.setMob(BB);
function out() { return "" + field(BS, "bout").toString(); }
function run(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("ChatTools").execute(M, L, 0); }

var foreman = newMob("GenMob", R);
foreman.setName("a fixforeman");
var chat = CMClass.getBehavior("MudChat");
chat.setParms("_fixes/dt4.chat=foreman");
foreman.addBehavior(chat);

// CHATTEST: the groups, each entry tried, the match and its replies with $n filled in
var report = "" + chat.chatTest(foreman, M, "Hello there!");
check("test-group", has(report, "Base group: FOREMAN"), report);
check("test-match", has(report, "SAY hello | hi: MATCHES"), report);
// $n is the NPC's own name, as chat.dat says
check("test-reply", has(report, "weight 9: say Hello there, a fixforeman!"), report);
var none = "" + chat.chatTest(foreman, M, "what weather");
check("test-nomatch", has(none, "No entry matches: it would not answer."), none);

// CHATLINT names each problem with its line
var lintL = new java.util.ArrayList(); lintL.add("CHATLINT"); lintL.add("_fixes/dt4.chat");
CMClass.getCommand("ChatTools").execute(BB, lintL, 0);
var said = out();
check("lint-parse", has(said, "line 7: the pattern does not parse"), said);
check("lint-link", has(said, "line 9: @nowhere names no group defined above it"), said);
check("lint-var", has(said, "line 11: a malformed variable line"), said);
check("lint-noreply", has(said, "line 12: the pattern (bye) has no replies"), said);
check("lint-clean", !has(said, "line 2:") && !has(said, "line 4:"), said);

// CHAT RELOAD reads the file again for the NPCs that talk from it
new CM.core.CMFile("/resources/_fixes/dt4.chat", M).saveText(">foreman\n(hello|hi)\n9say Welcome back, $n.\n");
run("CHAT RELOAD _fixes/dt4.chat");
var again = "" + chat.chatTest(foreman, M, "hello");
check("reload", has(again, "weight 9: say Welcome back, a fixforeman."), again);

// in builder mode (as these tests run) an edited chat file is read again by itself
new CM.core.CMFile("/resources/_fixes/dt4.chat", M).saveText(">foreman\n(hello|hi)\n9say Third time, $n.\n");
java.lang.Thread.sleep(2200);
var auto = "" + chat.chatTest(foreman, M, "hello");
check("auto-reload", has(auto, "weight 9: say Third time, a fixforeman."), auto);

foreman.destroy();
BB.setSession(null);
BB.destroy();
done();
