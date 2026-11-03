// G7: ENSURE on an AREA: a key put back outside the door it opens (reachable-without:keyname=, IN=mob|container), a counter
// made up with its FALLBACK, MAX removing extras, and ROOM= making sure of a room (REPLACE= the template it may take over).
FIXFILE = "g7-ensure";
var D = CM.core.Directions;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg7.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><AREA id="fixg7_area" layout="TREE" order="reverse-depth" requires="areaname=$,areasize=int">'
	+ '<NAME>$areaname</NAME><SIZE>$areasize</SIZE><CLASS>StdArea</CLASS><ROOM select="first">'
	+ '<ROOM class="StdRoom" condition="$roomtag_isdeepest=\'true\' and $__fixcell&lt;1" define="__fixcell+=1"><TITLE>A fixg7 cell</TITLE><DESCRIPTION>Bars.</DESCRIPTION>'
	+ '<ITEM class="GenKey" name="a fixg7 cell key" readabletext="fixg7 cellkey" display="A key lies here." />'
	+ '<EXIT class="GenDoor" hasalock="true" deflocked="true" keyname="fixg7 cellkey" /></ROOM>'
	+ '<ROOM id="fixg7_hall" class="StdRoom"><TITLE>A fixg7 hall</TITLE><DESCRIPTION>Stone.</DESCRIPTION>'
	+ '<ITEM class="GenItem" name="a fixg7 pebble" display="A pebble." />'
	+ '<ITEM class="GenContainer" name="a fixg7 chest" display="A chest." condition="$roomtag_depth=1" />'
	+ '<MOB class="GenMob" name="a fixg7 guard" level="1" display="A guard." condition="$roomtag_depth=1" /><EXIT class="Open" /></ROOM>'
	+ '</ROOM>'
	+ '<ENSURE name="fixg7 cell key" min="1" max="1" fallback="fixg7_key" where="reachable-without:keyname=fixg7 cellkey" in="mob|container" />'
	+ '<ENSURE var="__fixtorch" min="2" fallback="fixg7_torch" where="reachable" in="room" />'
	+ '<ENSURE name="fixg7 pebble" max="1" />'
	+ '<ENSURE room="fixg7_shrine" count="1" replace="fixg7_hall" />'
	+ '</AREA>'
	+ '<ITEM id="fixg7_key" class="GenKey" name="a fixg7 cell key" readabletext="fixg7 cellkey" display="A key lies here." />'
	+ '<ITEM id="fixg7_torch" class="GenItem" name="a fixg7 torch" display="A torch." define="__fixtorch+=1" />'
	+ '<ROOM id="fixg7_shrine" class="StdRoom"><TITLE>A fixg7 shrine</TITLE><DESCRIPTION>Candles.</DESCRIPTION></ROOM>'
	+ '</AREADATA>').getBytes("UTF-8"));
function cmd(words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand("Generate").execute(M, L, 0); }
function rooms(A) { var o = []; for (var r = A.getProperMap(); r.hasMoreElements();) o.push(r.nextElement()); return o; }
function reach(A, key) {
	var e = A.getRoom(A.Name() + "#0"), seen = [e], todo = [e];
	while (todo.length) {
		var R = todo.shift();
		for (var d = 0; d < D.NUM_DIRECTIONS(); d++) {
			var N = R.rawDoors()[d], E = R.getRawExit(d);
			if (N == null || N.getArea() != A || seen.indexOf(N) >= 0) continue;
			if (E != null && E.hasALock() && s(E.keyName()) == key) continue;
			seen.push(N); todo.push(N);
		}
	}
	return seen;
}
function named(A, n) {
	var o = [];
	rooms(A).forEach(function (R) {
		for (var i = 0; i < R.numItems(); i++) if (has(R.getItem(i).Name(), n)) o.push(R.getItem(i));
		for (var j = 0; j < R.numInhabitants(); j++) { var M2 = R.fetchInhabitant(j); for (var k = 0; k < M2.numItems(); k++) if (has(M2.getItem(k).Name(), n)) o.push(M2.getItem(k)); }
	});
	return o;
}
var made = [];
try {
	var bad = [];
	[1, 2, 3].forEach(function (seed) {
		cmd(["GENERATE", "NOSAVE", "AREA", "fixg7_area", "FROM", "randareas/fixg7.xml", "areaname=Fixg7Jail" + seed, "areasize=7", "SEED=" + seed, "UNLINKED"]);
		var A = CMLib.map().getArea("Fixg7Jail" + seed);
		made.push(A);
		if (A == null) { bad.push(seed + ": no area"); return; }
		var cell = null, locked = false;
		rooms(A).forEach(function (R) { if (s(R.displayText()) == "A fixg7 cell") cell = R; });
		if (cell != null) for (var d = 0; d < D.NUM_DIRECTIONS(); d++) { var E = cell.getRawExit(d); if (E != null && E.hasALock() && s(E.keyName()) == "fixg7 cellkey") locked = true; }
		var ok = reach(A, "fixg7 cellkey");
		var keys = named(A, "fixg7 cell key");
		var K = keys.length ? keys[0] : null;
		var KR = (K == null) ? null : CMLib.map().roomLocation(K);
		var keyOk = keys.length == 1 && ok.indexOf(KR) >= 0 && KR !== cell && ((K.owner() instanceof CM.MOBS.interfaces.MOB) || (K.container() != null && has(K.container().Name(), "fixg7 chest")));
		var torches = named(A, "fixg7 torch");
		var pebbles = named(A, "fixg7 pebble");
		var shrines = rooms(A).filter(function (R) { return s(R.displayText()) == "A fixg7 shrine"; });
		if (!(cell != null && locked)) bad.push(seed + ": cell " + (cell != null) + " locked " + locked);
		if (!keyOk) bad.push(seed + ": keys " + keys.length + " in " + (KR == null ? "none" : KR.displayText()) + " owner " + (K == null ? "" : K.owner()) + " container " + (K == null ? "" : K.container()));
		if (torches.length != 2) bad.push(seed + ": torches " + torches.length);
		if (pebbles.length != 1) bad.push(seed + ": pebbles " + pebbles.length);
		if (!(shrines.length == 1 && shrines[0].roomID() != A.Name() + "#0")) bad.push(seed + ": shrines " + shrines.length);
	});
	check("ensure", bad.length == 0, bad.join("\n"));
	check("help", has(CMLib.help().getHelpText("GENERATE", M, true), "ENSURE"), "no help");
} finally {
	made.forEach(function (A) { if (A != null) CMLib.map().obliterateMapArea(A); });
	if (F.exists()) F["delete"]();
}
done();
