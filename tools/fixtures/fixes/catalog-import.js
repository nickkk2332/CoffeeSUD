// A catalog entry imported from the world files keeps one database row, holding its catalog data;
// and a cache made before that, holding two rows for the entry (one with empty data), loads the one with data.
FIXFILE = "catalog-import";
function s(x) { return "" + x; }
var WF = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles");
var RL = java.lang.Class.forName("com.planet_ink.coffee_mud.core.database.RoomLoader");
var CS = CM.Libraries.interfaces.CatalogLibrary.CataSpawn;
var NAME = "a fixcat lantern";
function rows() {
	var D = CMLib.database().getConnector().DBFetch(), out = [];
	try {
		var R = D.query("SELECT * FROM CMROIT WHERE CMROID='CATALOG_ITEMS'");
		while (R.next()) { var tx = s(R.getString("CMITTX")); if (tx.indexOf(NAME) >= 0) out.push(tx); }
	} finally { CMLib.database().getConnector().DBDone(D); }
	return out;
}
var I = CMClass.getItem("GenItem");
I.setName(NAME); I.setDisplayText(NAME + " hangs here."); I.recoverPhyStats();
CMLib.catalog().addCatalog(I);
I.destroy();
var D0 = CMLib.catalog().getCatalogItemData(NAME);
D0.setSpawn(CS.ROOM); D0.setRate(0.05); D0.setCap(2); D0.setMaskStr("-AREAS +nowhere");
CMLib.catalog().updateCatalogCategory(CMLib.catalog().getCatalogItem(NAME), "");
var text = s(callTyped(null, WF, "catalogText", [], []));
// keep only our entry, then take it out of the catalog and import the file as a fresh world would
var start = text.indexOf("<CATITEM>", text.lastIndexOf("<CATITEM>", text.indexOf(NAME))), end = text.indexOf("</CATITEM>", start) + "</CATITEM>".length;
var file = java.io.File.createTempFile("fixcat", ".cmare");
java.nio.file.Files.write(file.toPath(), new java.lang.String("<CATALOG>" + text.substring(start, end) + "</CATALOG>").getBytes("UTF-8"));
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem(NAME));
var before = rows().length;
var problem = callTyped(null, WF, "importCatalog", ["File"], [file]);
var after = rows(), D1 = CMLib.catalog().getCatalogItemData(NAME);
check("one row", before == 0 && problem == null && after.length == 1 && after[0].indexOf("<SPAWN>ROOM</SPAWN>") > 0 && D1 != null && D1.getSpawn() == CS.ROOM && D1.getCap() == 2,
	"rows " + before + " -> " + after.length + " problem " + problem + " data " + (D1 && D1.data(null)));

// the loader reads a name's row with data first, whichever order the cache gives them in
var empty = s(CMLib.catalog().sampleCataData("").data(null)), full = s(D1.data(null));
function order(firstKey, firstData, secondKey, secondData) {
	var nums = new java.util.HashMap(), data = new java.util.HashMap();
	var a = CMClass.getItem("GenItem"), b = CMClass.getItem("GenItem");
	a.setName(NAME); b.setName(NAME);
	nums.put(firstKey, a); nums.put(secondKey, b); data.put(firstKey, firstData); data.put(secondKey, secondData);
	var o = callTyped(null, RL, "catalogOrder", ["Map", "Map"], [nums, data]);
	a.destroy(); b.destroy();
	return s(o.get(0));
}
check("loader prefers data", order("GenItem1@a", empty, "GenItem2@b", full) == "GenItem2@b" && order("GenItem1@a", full, "GenItem2@b", empty) == "GenItem1@a",
	order("GenItem1@a", empty, "GenItem2@b", full) + " / " + order("GenItem1@a", full, "GenItem2@b", empty));

CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem(NAME));
file.delete();
done();
