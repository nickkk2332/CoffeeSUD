// I3: GenTome pages (PAGE blocks, FILE= with === titles ===), READ contents / n / NEXT / PREV / title, LANG, LORE,
// READ_PROG PAGE n, TEAR PAGE n FROM; world/journals/NAME.txt fills an empty GenBook.
FIXFILE = "i3-tome";
var WF = CM.core.save.WorldFiles;
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi3pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function read(what) { out(); var l = new java.util.ArrayList(); ("READ " + what).split(" ").forEach(function (w) { l.add(w); }); CMClass.getCommand("Read").execute(V, l, 0); return out(); }
var tome = CMClass.getItem("GenTome"); tome.setName("the fixi3 annals"); V.addItem(tome);
tome.setReadableText("<PAGE TITLE=\"The Founding\">Ost was founded by the river.</PAGE>"
	+ "<PAGE TITLE=\"Elder Words\" LANG=Elvish>Mae govannen, travellers.</PAGE>"
	+ "<PAGE TITLE=\"The Fall\" LORE=ost.fall>The tower fell in the seventh year.</PAGE>");
scriptOn(tome, "READ_PROG PAGE 3\nmpsetvar FIXTEST FIXI3P $n\n~\n");
clearVar("FIXI3P");
var said = read("annals");
check("contents", has(said, "1. The Founding") && has(said, "2. Elder Words") && has(said, "3. The Fall"), "'" + said + "'");
said = read("1 annals");
check("page", has(said, "Page 1: The Founding") && has(said, "founded by the river"), "'" + said + "'");
said = read("next annals");
check("lang", has(said, "written in Elvish, which you cannot read"), "'" + said + "'");
said = read("next annals");
check("lore", has(said, "The tower fell") && V.findTattoo("LORE:OST.FALL") != null && gvar("FIXI3P") == "Fixi3pip", "'" + said + "' prog '" + gvar("FIXI3P") + "'");
said = read("prev annals");
var prev = has(said, "Page 2");
said = read("founding annals");
check("turn", prev && has(said, "Page 1: The Founding"), "prev " + prev + " '" + said + "'");
// a page torn out
out();
var l = new java.util.ArrayList(); ["tear", "page", "1", "from", "annals"].forEach(function (w) { l.add(w); });
var took = SE.verb(V, l);
var torn = V.findItem(null, "page torn");
said = read("1 annals");
check("tear", took && torn != null && has(s(torn.readableText()), "founded by the river") && has(said, "torn out") && tome.hasTag("TORN:1"), "took " + took + " page " + torn + " '" + said + "'");
// pages from a file
var W = WF.worldDir();
var texts = new java.io.File(W, "texts"); texts.mkdirs();
var tf = new java.io.File(texts, "fixi3.txt");
java.nio.file.Files.write(tf.toPath(), new java.lang.String("=== Of Wolves ===\nWolves run in packs.\n=== Of Bears | LANG=Elvish ===\nBears sleep.\n").getBytes("UTF-8"));
var book2 = CMClass.getItem("GenTome"); book2.setName("a fixi3 bestiary"); book2.setReadableText("FILE=world/texts/fixi3.txt"); V.addItem(book2);
said = read("bestiary");
var c2 = has(said, "1. Of Wolves") && has(said, "2. Of Bears");
said = read("1 bestiary");
check("file", c2 && has(said, "Wolves run in packs."), "'" + said + "'");
// an empty GenBook filled from world/journals
var jd = new java.io.File(W, "journals"); jd.mkdirs();
var jf = new java.io.File(jd, "fixi3_ledger.txt");
java.nio.file.Files.write(jf.toPath(), new java.lang.String("AUTHOR: the Archivist\n=== First Entry ===\nThe ledger begins.\n=== Second Entry ===\nIt goes on.\n").getBytes("UTF-8"));
var ledger = CMClass.getItem("GenBook"); ledger.setName("Fixi3 Ledger"); V.addItem(ledger);
read("ledger"); var firstPage = s(ledger.getContent(1));
var n = CMLib.database().DBCountJournal("Fixi3 Ledger", null, null);
check("seed", n == 2 && has(firstPage, "The ledger begins."), "entries " + n + " page " + firstPage);
check("help", has(CMLib.help().getHelpText("GENTOME", M, true), "TEAR PAGE") && has(CMLib.help().getHelpText("READ", M, false), "READ NEXT BOOK"), "no help");

CMLib.database().DBDeleteJournal("Fixi3 Ledger", null);
tf["delete()"](); jf["delete()"]();
V.setPlayerStats(null); V.destroy();
done();
