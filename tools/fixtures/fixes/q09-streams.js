// Q09 random streams, the shared module definition loader (pins, JSON-path problems) and the Q12 work counts.
FIXFILE = "q09-streams";
var RS = CM.core.RandomStreams, MC = CM.core.ModuleContent, MS = CM.core.ModuleStats;
function s(x) { return "" + x; }
var was = s(RS.saved());
try {
	RS.restore("");
	// a stream's values depend only on the seed, the stream and the counter
	var seed = RS.seed();
	check("deterministic", RS.valueAt(seed, "loot", 5) == RS.valueAt(seed, "loot", 5) && RS.valueAt(seed, "loot", 5) != RS.valueAt(seed, "encounters", 5),
		"the same place gave different values, or two streams the same");
	// drawing from one stream does not move another
	var nextEncounter = RS.peek("encounters").draw();
	for (var i = 0; i < 7; i++) RS.draw("loot");
	check("partitioned", RS.counter("loot") == 7 && RS.counter("encounters") == 0 && RS.peek("encounters").draw() == nextEncounter,
		"loot " + RS.counter("loot") + " encounters " + RS.counter("encounters"));
	// peeking and describing never draw
	var before = RS.counter("loot");
	RS.peek("loot").draw(); RS.describe();
	check("peek", RS.counter("loot") == before, "a peek moved the stream");
	// a recorded scenario draws the same values again
	var rec = RS.record("expeditions", null);
	var a = RS.draw("expeditions"), b = RS.draw("expeditions");
	var cur = RS.replay(rec);
	check("replay", cur.draw() == a && cur.draw() == b && RS.counter("expeditions") == 2, "a replay drew other values");
	// the save keeps each stream's place
	var saved = s(RS.saved());
	var next = RS.peek("loot").draw();
	RS.restore(""); RS.restore(saved);
	check("saved", RS.counter("loot") == 7 && RS.draw("loot") == next, "after restore: " + RS.saved());
	check("roll", RS.roll(RS.valueAt(1, "loot", 1), 6) >= 1 && RS.roll(RS.valueAt(1, "loot", 1), 6) <= 6, "a roll out of range");
	var bad = false; try { RS.draw("Bad Name"); } catch (e) { bad = true; }
	check("names", bad, "a bad stream name was taken");

	// the definition loader: names, pins, and problems with their JSON path
	var dir = java.nio.file.Files.createTempDirectory("q09world").toFile();
	var folder = new java.io.File(dir, "things"); folder.mkdirs();
	function put(name, text) { var w = new java.io.FileWriter(new java.io.File(folder, name)); w.write(text); w.close(); }
	put("fix.ok.v1.json", '{"schema":1,"id":"fix.ok","version":1,"title":"Fine","size":3}');
	put("fix.ok.v2.json", '{"schema":1,"id":"fix.ok","version":2,"size":4}');
	put("fix.bad.v1.json", '{"schema":1,"id":"fix.bad","version":1,"decay":{"clock":"weeks"}}');
	put("fix.misnamed.v1.json", '{"schema":1,"id":"fix.other","version":1}');
	var reader = new CM.core.ModuleContent.Reader({ read: function (n) {
		n.allow("schema", "id", "version", "title", "size", "decay");
		var d = n.object("decay"); if (d != null) d.choice("clock", "turns", ["turns", "ticks", "hours"]);
		return java.lang.Long.valueOf(n.integer("size", 1, 1, 10));
	} });
	var lib = MC.load(dir, "things", "thing", reader);
	var errs = s(java.lang.String.join("|", lib.errors));
	check("load", lib.entries.size() == 2 && lib.latest("fix.ok").version == 2 && s(lib.latest("fix.ok").value) == "4", "entries " + lib.entries.keySet() + " / " + errs);
	check("json-path", errs.indexOf("things/fix.bad.v1.json: /decay/clock: must be one of turns, ticks, hours") >= 0, errs);
	check("filename", errs.indexOf("fix.misnamed.v1.json: the file must be named fix.other.v1.json") >= 0, errs);
	var pin = lib.latest("fix.ok").pin();
	check("pin", lib.pinned(pin).version == 2, "the pin did not resolve");
	// a title is not part of the rules; any other change without a new version is refused
	put("fix.ok.v2.json", '{"schema":1,"id":"fix.ok","version":2,"title":"Renamed","size":4}');
	var lib2 = MC.load(dir, "things", "thing", reader);
	check("title-free", lib2.pinned(pin).title == "Renamed", "renaming changed the rules");
	put("fix.ok.v2.json", '{"schema":1,"id":"fix.ok","version":2,"size":5}');
	var lib3 = MC.load(dir, "things", "thing", reader), refused = "";
	try { lib3.pinned(pin); } catch (e) { refused = s(e); }
	check("changed-refused", refused.indexOf("changed without a new version") >= 0 && refused.indexOf("things/fix.ok.v2.json") >= 0, refused);

	// work counts: a limit, and a reading over it
	MS.limit("fixq09", "actors", 3);
	check("limit", MS.reading("fixq09", "fix.src", "actors", 3) && !MS.reading("fixq09", "fix.src", "actors", 4)
		&& MS.value("fixq09", "fix.src", "actors over limit") == 1 && s(MS.report()).indexOf("fixq09 | actors | fix.src: 4 of 3") >= 0, s(MS.report()));
} finally {
	RS.restore(was);
}
done();
