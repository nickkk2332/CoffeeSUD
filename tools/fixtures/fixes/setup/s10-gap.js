// The cache gap: a builder changes a room with JRUN, without the database,
// as builder commands would not; quitting writes the world files from the
// live world, and must leave the cache with the change too
FIXFILE = "s10-gap";
var R = otherRoom();
R.setDescription("FIXGAP the walls are new.");
check("changed", ("" + R.description()).indexOf("FIXGAP") >= 0, "not changed");
done();
