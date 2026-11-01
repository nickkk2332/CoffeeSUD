// DT14: GENERATE CHECK loads a generator file as GENERATE does and reports its mistakes, generating nothing.
FIXFILE = "dt14-generatecheck";
var GC = CM.core.GenerateCheck;
function has(list, part) { return ("" + list).indexOf(part) >= 0; }
var F = "_fixes/dt14gen.xml:";
var got = "" + GC.check("_fixes/dt14gen.xml");
check("layout", has(got, F + "4 AREA fixes_area: unknown layout NOSUCHLAYOUT"), got);
check("area-class", has(got, F + "4 AREA fixes_area: CLASS NoSuchAreaClass: no such AREA class"), got);
check("insert", has(got, F + "4 ROOM fixes_area: INSERT fixes_missing_rooms: no such id") && !has(got, "INSERT fixes_rooms:"), got);
check("room-class", has(got, F + "9 ROOM fixes_rooms: CLASS NoSuchRoomClass: no such ROOM class"), got);
check("select", has(got, F + "9 ROOM fixes_rooms: SELECT SOME-2: not a selection") && !has(got, "SELECT pick-1"), got);
check("like", has(got, F + "9 ITEM fixes_rooms: LIKE fixes_mob: a MOB, not a ITEM"), got);
check("behaviour", has(got, "CLASS NoSuchBehaviour: no such BEHAVIOR class") && !has(got, "CLASS Mobile:"), got);
check("missing", has("" + GC.check("_fixes/nosuchgen.xml"), "_fixes/nosuchgen.xml: no such file"), "a missing file");
// the shipped generator files have none
var shipped = new java.io.File("resources/randareas").list();
var bad = [];
for (var i = 0; i < shipped.length; i++) {
	var name = "" + shipped[i];
	if (!name.endsWith(".xml")) continue;
	var p = GC.check("randareas/" + name);
	// known, and left as shipped (DEVPLAN DT14): planet.xml's own placeholders for ids
	// its loaded files define with MERGE, and rats.xml's insert of prison.xml's junk
	var left = [];
	for (var j = 0; j < p.size(); j++) {
		var s = "" + p.get(j);
		if (name == "planet.xml" && s.indexOf("Duplicate ID: ") >= 0) continue;
		if (name == "rats.xml" && s.indexOf("INSERT prison_junk: no such id") >= 0) continue;
		left.push(s);
	}
	if (left.length > 0) bad.push(name + ": " + left.join("; "));
}
check("shipped", bad.length == 0, bad.join(" | "));
// the command
var B = newMob("GenMob", M.location());
B.setName("Fixgenreader");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var L = new java.util.ArrayList(); L.add("GENERATE"); L.add("CHECK"); L.add("_fixes/dt14gen.xml");
CMClass.getCommand("Generate").execute(B, L, 0);
var said = "" + field(BS, "bout").toString();
check("cmd", has(said, "GENERATE CHECK _fixes/dt14gen.xml: 7 problems:") && has(said, "unknown layout NOSUCHLAYOUT"), said);
B.setSession(null);
B.destroy();
done();
