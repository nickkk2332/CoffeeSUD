// S18: back in the vault, long after leaving it: the same dungeon, kept for
// the group (PERSIST=true), not a new one
FIXFILE = "s18-kept";
var A = M.location().getArea();
check("same-vault", String(A.Name()) == "1_Vault", "now in " + A.Name());
check("no-second", CMLib.map().getArea("2_Vault") == null, "a second vault was made");
done();
