// P13 challenge books: world/challenges.ini names a book of achievements; CHALLENGES lists each book with what is done and how many
// entries are still hidden; a book shows the entries one may see, and a count of the rest; a personal record keeps its first value
// and its best (higher, or LOW: lower), each with when, in the character's story records.
FIXFILE = "p13-challenges";
var C = CM.core.Challenges, Agent = CM.Common.interfaces.AccountStats.Agent;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p13-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
var made = [], achs = [];
function ach(line) { var id = line.substring(0, line.indexOf("=")); CMLib.achievements().evaluateAchievement(Agent.PLAYER, line, true); achs.push(id); }
try {
	ach('ACH_FIXP13_1=EVENT="KILLS" DISPLAY="Trials: defeat the fixp13 warden" TITLE="" REWARDS="" NUM=1 ZAPPERMASK="-NAMES \\"+the fixp13 warden\\""');
	ach('ACH_FIXP13_2=EVENT="KILLS" DISPLAY="Trials: defeat the fixp13 sentinel" TITLE="" REWARDS="" NUM=1 ZAPPERMASK="-NAMES \\"+the fixp13 sentinel\\""');
	ach('ACH_FIXP13_3=EVENT="KILLS" DISPLAY="Trials: the warden unarmored" TITLE="" REWARDS="" NUM=1 ZAPPERMASK="-NAMES \\"+the fixp13 warden\\"" VISIBLEMASK="-TATTOOS +ACH_FIXP13_1"');
	Files.write(new java.io.File(root, "challenges.ini").toPath(), new java.lang.String('FIXP13=TITLE="Trials of the fixp13 forge" PREFIX="ACH_FIXP13_"\nBADBOOK=TITLE="Nothing" ENTRIES="ACH_NOSUCH"\n').getBytes("UTF-8"));
	WorldFiles.configure(root);
	check("problems", s(C.problems()).indexOf("BADBOOK: no achievement ACH_NOSUCH") >= 0, s(C.problems()));
	var P = CMClass.getMOB("StdMOB"); P.setName("Fixp13 Pip"); P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); P.baseCharStats().setMyRace(CMClass.getRace("Human"));
	P.recoverCharStats(); P.bringToLife(M.location(), true); made.push(P);

	// the book: none done, one entry hidden until the first is earned
	var list = s(C.list(P));
	check("listed", list.indexOf("Trials of the fixp13 forge: 0 of 3 done; 1 hidden entry") >= 0, list);
	var book = s(C.show(P, "fixp13"));
	check("hidden-count", book.indexOf("[ ] Trials: defeat the fixp13 warden") >= 0 && book.indexOf("unarmored") < 0 && book.indexOf("...and 1 hidden entry") >= 0, book);
	P.addTattoo("ACH_FIXP13_1");
	book = s(C.show(P, "Trials of the fixp13 forge"));
	check("revealed", book.indexOf("[x] Trials: defeat the fixp13 warden") >= 0 && book.indexOf("[ ] Trials: the warden unarmored") >= 0 && book.indexOf("hidden") < 0
		&& s(C.list(P)).indexOf("1 of 3 done") >= 0, book);

	// personal records: the first value kept, the best replaced only by a better one
	check("first", s(C.record(P, "fixp13", "warden time", 80, true)) == "first", "not a first record");
	check("not-better", s(C.record(P, "fixp13", "warden time", 95, true)) == "", "a slower time was a best");
	check("better", s(C.record(P, "fixp13", "warden time", 45, true)) == "best", "a faster time was not a best");
	C.record(P, "fixp13", "damage", 120, false); C.record(P, "fixp13", "damage", 300, false);
	book = s(C.show(P, "fixp13"));
	check("records-shown", book.indexOf("Your records:") >= 0 && /warden time: best 45 \(.*\); first 80 \(.*\); 3 time\(s\)/.test(book) && /damage: best 300/.test(book), book);
	check("kept", s(P.fetchEffect("Prop_StoryRecords").text()).indexOf("warden time") >= 0, "the records are not in the story records");
	var bad = ""; try { C.record(P, "nosuchbook", "x", 1, false); } catch (e) { bad = s(e); }
	check("unknown-book", bad.indexOf("no challenge book 'nosuchbook'") >= 0, bad);
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	achs.forEach(function (a) { CMLib.achievements().deleteAchievement(a); });
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
