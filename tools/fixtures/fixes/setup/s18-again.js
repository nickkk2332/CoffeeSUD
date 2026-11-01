// S18, having walked out of the mines and back in: the same dungeon
FIXFILE = "s18-again";
var A = M.location().getArea();
check("same-mines", String(A.Name()) == "1_Mines", "now in " + A.Name());
check("no-second", CMLib.map().getArea("2_Mines") == null, "a second dungeon was made");
done();
