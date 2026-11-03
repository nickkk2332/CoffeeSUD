// smoke (item 1): three iron chests here, for the Sunblade of the world's
// catalog (catalog.cmare: once ever, cap 1, found in an iron chest on its
// first opening)
var CM = Packages.com.planet_ink.coffee_mud;
var CMClass = CM.core.CMClass, CMLib = CM.core.CMLib;
var M = mob(), R = M.location();
for (var i = 0; i < 3; i++) {
	var C = CMClass.getItem("GenContainer");
	C.setName("an iron chest");
	C.setDisplayText("an iron chest sits here.");
	C.setCapacity(500);
	C.setDoorsNLocks(true, false, true, false, false, false);
	R.addItem(C);
}
var D = CMLib.catalog().getCatalogItemData("the Sunblade");
M.tell("UNIQUE ready: " + ((D == null) ? "no Sunblade in the catalog" : (D.getSpawn() + " " + D.getUnique() + " cap " + D.getCap())));
