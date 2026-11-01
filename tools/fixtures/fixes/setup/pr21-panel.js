// PR21: the panel beside a character window's text, in a game with a display
// (tools/fixes.sh runs this under a virtual display).
FIXFILE = "pr21-panel";
var R = M.location();
var WP = CM.core.WindowPanel;
var CW = CM.core.CharacterWindow;
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
// without the game's colour codes (%h, for one, colours low hit points)
// and its MXP tags (%h is ^<Hp^>20^</Hp^>), which a window's filter drops
function plain(text) { return ("" + text).replace(/\^<[^\^]*\^>/g, "").replace(/\^./g, ""); }

// %{VAR name}: the character's variable, else one of the name on any host;
// %{VAR host name}: that host's
VARS.setVar(M.Name(), "FIXCHAPTER", "3");
VARS.setVar("FIXWORLD", "FIXWHEN", "early dawn");
VARS.setVar("FIXWORLD", "FIXCHAPTER", "9");
var built = "" + CMLib.utensils().buildPrompt(M, "Chapter %{VAR FIXCHAPTER} at %{VAR FIXWHEN}%{VAR FIXNONE}, %{VAR FIXWORLD FIXCHAPTER}.");
check("prompt-var", has(built, "Chapter 3 at early dawn, 9."), "the prompt gave '" + built.trim() + "'");

// STATUS: world/text/panel.txt with the prompt's codes; MAP; PARTY
var status = plain(WP.text(M, "STATUS"));
check("status-file", has(status, "FIXPANEL") && has(status, "" + R.displayText()) && has(status, "Chapter 3")
	&& has(status, "HP " + M.curState().getHitPoints() + "/" + M.maxState().getHitPoints()), "STATUS gave '" + status + "'");
var map = "" + WP.text(M, "MAP");
check("map", map.length > 0 && has(map, "*"), "MAP gave '" + map + "'");
var party = plain(WP.text(M, "PARTY"));
check("party", has(party, M.Name()) && has(party, "[") && has(party, M.curState().getHitPoints() + "/" + M.maxState().getHitPoints()), "PARTY gave '" + party + "'");
var all = "" + WP.text(M, "STATUS,MAP,PARTY");
check("order", all.indexOf("FIXPANEL") < all.indexOf("*") && all.indexOf("*") < all.lastIndexOf(M.Name()), "the sections are out of order");

// WINDOWS PANEL sets the sections, ON is all, a wrong one changes nothing
var cmd = CMClass.getCommand("WindowsCmd");
function windows(words) { var L = new java.util.ArrayList(); L.add("WINDOWS"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); cmd.execute(M, L, 0); }
windows("PANEL MAP,STAT");
check("cmd-sections", ("" + WP.sections()) == "MAP,STATUS", "WINDOWS PANEL MAP,STAT gave '" + WP.sections() + "'");
windows("PANEL BOGUS");
check("cmd-wrong", ("" + WP.sections()) == "MAP,STATUS", "a wrong section changed it to '" + WP.sections() + "'");
windows("PANEL ON");
check("cmd-on", ("" + WP.sections()) == "STATUS,MAP,PARTY", "ON gave '" + WP.sections() + "'");

// a window shows the panel, in colour, and hides it
check("display", CW.canOpen(), "no display: the window part cannot run");
if (CW.canOpen()) {
	var W = CW.open("fixes panel", "UTF-8", "UTF-8", null);
	W.setPanel("\u001b[1;32mFIXGREEN\u001b[0m and plain\nline two");
	var shown = "" + W.panelShown();
	check("window-shown", has(shown, "FIXGREEN and plain") && has(shown, "line two") && !has(shown, "["), "the panel shows '" + shown + "'");
	W.setPanel(null);
	check("window-hidden", W.panelShown() == null, "the panel is still shown");
	W.close();

	// a window session draws its character's panel at the prompt
	var S = CMClass.getCommon("WindowSession");
	S.initialize(null, "0");
	var who = newMob("GenMob", R);
	who.setName("Fixpanel");
	S.setMob(who);
	S.showPrompt();
	var drawn = "" + S.window().panelShown();
	check("session-panel", has(drawn, "Fixpanel") && has(drawn, "FIXPANEL"), "the session's panel shows '" + drawn + "'");
	windows("PANEL OFF");
	check("session-off", S.window().panelShown() == null, "WINDOWS PANEL OFF left the panel");
	S.window().close();
	S.setMob(null);
	who.destroy();
}
WP.setSections("OFF");
done();
