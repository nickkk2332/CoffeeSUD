// S6: the world's resources overlay.  This runs in builder mode, on a world
// folder of its own (WORLDDIR), so it puts files there and takes them away.
FIXFILE = "s6-overlay";
var CMFile = CM.core.CMFile;
var File = java.io.File;
var WD = CM.core.save.WorldFiles.worldDir();
function put(rel, text) {
	var F = new File(WD, rel);
	F.getParentFile().mkdirs();
	var w = new java.io.FileWriter(F);
	w.write(text);
	w.close();
	return F;
}
function gone(F) { if (F.exists()) F["delete"](); }
var stock = "" + new CMFile("resources/text/blocked.txt", null, 0).text();

// a world copy of a stock file is read instead of it
var copy = put("resources/text/blocked.txt", "FIXOVERLAY the world's own words\n");
var read = "" + new CMFile("resources/text/blocked.txt", null, 0).text();
check("copy-read", read.indexOf("FIXOVERLAY") >= 0 && read.indexOf("BLOCKED") < 0, "read '" + read + "'");
gone(copy);
check("stock-again", ("" + new CMFile("resources/text/blocked.txt", null, 0).text()) == stock, "the stock file did not come back");

// name.add is read after the stock file
var add = put("resources/text/blocked.txt.add", "FIXADDED one more line\n");
read = "" + new CMFile("resources/text/blocked.txt", null, 0).text();
check("add-text", read.indexOf("BLOCKED") >= 0 && read.indexOf("FIXADDED") > read.indexOf("BLOCKED"), "read '" + read + "'");
var raw = new java.lang.String(new CMFile("resources/text/blocked.txt", null, 0).raw());
check("add-raw", raw.indexOf("FIXADDED") > 0, "raw lacks the addition");
gone(add);

// a world file with a new name is listed with the stock ones
var extra = put("resources/text/fixextra.txt", "FIXEXTRA\n");
var names = [];
var list = new CMFile("resources/text", null, 0).listFiles();
for (var i = 0; i < list.length; i++) names.push("" + list[i].getName());
check("listed", names.indexOf("fixextra.txt") >= 0 && names.indexOf("blocked.txt") >= 0, "listed " + names.length + " files");
check("read-new", ("" + new CMFile("resources/text/fixextra.txt", null, 0).text()).indexOf("FIXEXTRA") >= 0, "the world's new file cannot be read");
gone(extra);

// world/ is the world folder, wherever WORLDDIR is
var areas = new CMFile("world/areas", null, 0);
check("world-root", areas.isDirectory() && areas.listFiles().length > 0, "world/areas is not the world's areas");

// builder mode writes resources/ files to the world's copy, not the stock one
var target = new File(WD, "resources/text/fixwritten.txt");
new CMFile("resources/text/fixwritten.txt", null, 0).saveText("FIXWRITTEN\n");
check("write-world", target.exists() && !(new File("resources/text/fixwritten.txt").exists()), "written to " + (target.exists() ? "both" : "the stock folder"));
gone(target);
gone(new File("resources/text/fixwritten.txt"));

// CHATDEFAULT names the chat file mobs use by default, or NONE for none
var MudChat = CMClass.getBehavior("MudChat").getClass();
var props = CM.core.CMProps.instance();
check("chat-default", "" + callTyped(null, MudChat, "defaultChatFile", [], []) == "chat.dat", "the default is not chat.dat");
props.put("CHATDEFAULT", "NONE");
check("chat-none", "" + callTyped(null, MudChat, "defaultChatFile", [], []) == "", "NONE still has a file");
props.put("CHATDEFAULT", "fixchat.dat");
check("chat-file", "" + callTyped(null, MudChat, "defaultChatFile", [], []) == "fixchat.dat", "the file was not taken");
props.remove("CHATDEFAULT");
done();
