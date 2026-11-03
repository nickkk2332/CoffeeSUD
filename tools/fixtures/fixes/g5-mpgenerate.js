// G5: MPGENERATE ITEM|MOB|ROOM (a fresh roll each call, $b the first), GENSTRING(), the context variables, FROMGENFILE ... FRESH.
FIXFILE = "g5-mpgenerate";
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg5.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA>'
	+ '<ITEM id="fixg5_coin" class="GenItem" level="1" name="a fixg5 coin of $player_level" />'
	+ '<ITEM id="fixg5_token" class="GenItem" level="1" name="a fixg5 token $[1?1000000]" />'
	+ '<MOB id="fixg5_imp" class="GenMob" level="$area_level+1" race="Human" gender="M"><NAME>a fixg5 imp</NAME><DISPLAY>$mob_name is here.</DISPLAY><ALIGNMENT>0</ALIGNMENT></MOB>'
	+ '<STRING id="fixg5_rumor">They say the $season is long, and it is hour $hour.</STRING>'
	+ '<STRING id="fixg5_roll">$[1?1000000]</STRING>'
	+ '<ROOM id="fixg5_room" class="StdRoom"><TITLE>A fixg5 nook</TITLE><DESCRIPTION>A small fixg5 nook.</DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '</AREADATA>').getBytes("UTF-8"));
var X = CMClass.getAreaType("StdArea"); X.setName("Fixg5Den");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
var R = CMClass.getLocale("StdRoom"); R.setRoomID("Fixg5Den#0"); R.setDisplayText("Fixg5 den"); R.setArea(X);
var chest = CMClass.getItem("GenContainer"); chest.setName("a fixg5 chest"); R.addItem(chest);
["G5B", "G5C", "G5M", "G5S", "G5R1", "G5R2", "G5N", "G5T1", "G5T2", "G5T3", "G5T4"].forEach(clearVar);
function names(L) { var o = []; for (var i = 0; i < L.size(); i++) o.push(s(L.get(i).Name())); return o; }
try {
	var m = newMob("GenMob", R); m.setName("Fixg5wizard");
	scriptOn(m, "RAND_PROG 100\n"
		+ "mpgenerate item randareas/fixg5.xml fixg5_coin player_level=9 HERE\nmpsetvar FIXTEST G5B $b\n"
		+ "mpgenerate item randareas/fixg5.xml fixg5_coin INTO 'a fixg5 chest'\nmpsetvar FIXTEST G5C $b\n"
		+ "mpgenerate mob randareas/fixg5.xml fixg5_imp area_level=7\nmpsetvar FIXTEST G5M $b\n"
		+ "mpsetvar FIXTEST G5S $%GENSTRING(randareas/fixg5.xml fixg5_rumor)%\n"
		+ "mpsetvar FIXTEST G5R1 $%GENSTRING(randareas/fixg5.xml fixg5_roll)%\nmpsetvar FIXTEST G5R2 $%GENSTRING(randareas/fixg5.xml fixg5_roll)%\n"
		+ "mpgenerate room randareas/fixg5.xml fixg5_room north\nmpsetvar FIXTEST G5N $b\n"
		+ "mpoload FROMGENFILE randareas/fixg5.xml ANY fixg5_token FRESH\nmpsetvar FIXTEST G5T1 $b\n"
		+ "mpoload FROMGENFILE randareas/fixg5.xml ANY fixg5_token FRESH\nmpsetvar FIXTEST G5T2 $b\n"
		+ "mpoload FROMGENFILE randareas/fixg5.xml ANY fixg5_token\nmpsetvar FIXTEST G5T3 $b\n"
		+ "mpoload FROMGENFILE randareas/fixg5.xml ANY fixg5_token\nmpsetvar FIXTEST G5T4 $b\n~\n");
	tickMob(m);
	var coins = 0; for (var i = 0; i < R.numItems(); i++) if (s(R.getItem(i).Name()) == "a fixg5 coin of 9" && R.getItem(i).container() == null) coins++;
	var inChest = 0; for (var j = 0; j < R.numItems(); j++) if (has(R.getItem(j).Name(), "a fixg5 coin of") && R.getItem(j).container() == chest) inChest++;
	check("item", coins == 1 && gvar("G5B") == "a fixg5 coin of 9" && inChest == 1 && has(gvar("G5C"), "a fixg5 coin of 1"), "coins " + coins + " in chest " + inChest + " $b " + gvar("G5B") + " / " + gvar("G5C"));
	var imp = R.fetchInhabitant("a fixg5 imp");
	check("mob", imp != null && imp.basePhyStats().level() == 8 && gvar("G5M") == "a fixg5 imp", "imp " + (imp == null ? "none" : imp.basePhyStats().level()) + " $b " + gvar("G5M"));
	check("string", has(gvar("G5S"), "They say the " + s(CM.core.Calendars.season(X).name) + " is long, and it is hour " + X.getTimeObj().getHourOfDay())
		&& CMath.isInteger(gvar("G5R1")) && gvar("G5R1") != gvar("G5R2"), gvar("G5S") + " / " + gvar("G5R1") + " / " + gvar("G5R2"));
	var nook = R.getRoomInDir(CM.core.Directions.NORTH);
	check("room", nook != null && s(nook.displayText()) == "A fixg5 nook" && nook.getRoomInDir(CM.core.Directions.SOUTH) == R && gvar("G5N").length > 0, "nook " + (nook == null ? "none" : nook.displayText()) + " $b " + gvar("G5N"));
	check("fresh", has(gvar("G5T1"), "a fixg5 token") && gvar("G5T1") != gvar("G5T2") && gvar("G5T3") == gvar("G5T4"), gvar("G5T1") + " / " + gvar("G5T2") + " / " + gvar("G5T3") + " / " + gvar("G5T4"));
	m.destroy();
	check("help", has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPGENERATE") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "GENSTRING("), "no help");
} finally {
	F["delete"]();
	for (var r = X.getProperMap(); r.hasMoreElements();) { var RR = r.nextElement(); for (var k = RR.numInhabitants() - 1; k >= 0; k--) { var mm = RR.fetchInhabitant(k); if (mm != null) mm.destroy(); } }
	CMLib.map().delArea(X);
}
done();
