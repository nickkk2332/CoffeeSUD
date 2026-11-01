// DT27: world/dev areas, loaded only for building and testing, written back there.
FIXFILE = "dt27-dev";
var WF = CM.core.save.WorldFiles;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixdevbuilder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function world(words) { var L = new java.util.ArrayList(); L.add("WORLD"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("WorldCmd").execute(B, L, 0); return out(); }
function readFile(F) { return "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"); }

var worldDir = WF.worldDir();
var devDir = new java.io.File(worldDir, "dev");
var areasDir = new java.io.File(worldDir, "areas");
devDir.mkdirs();
// a dev area file, made from the shipped new area's, with another name
var shipped = new java.io.File(areasDir, "new-area.cmare");
var devFile = new java.io.File(devDir, "fixes-dev-hub.cmare");
java.nio.file.Files.write(devFile.toPath(), new java.lang.String(readFile(shipped).replace(/New Area/g, "Fixes Dev Hub")).getBytes("UTF-8"));
var sumBefore = "" + WF.devChecksum();
WF.loadDevAreas();
var A = CMLib.map().getArea("Fixes Dev Hub");
check("loaded", A != null && WF.isDevArea(A), "the dev area " + (A == null ? "did not load" : "is not tagged DEV"));
check("own-sum", ("" + WF.checksum()).indexOf("dev") < 0 && has(sumBefore, "dev-1-"), "the checksums " + WF.checksum() + " / " + sumBefore);
var listing = world("DEV");
check("listed", has(listing, "Dev areas (world/dev") && has(listing, "Fixes Dev Hub ("), listing);
// loading again keeps the cache's dev areas when they match the files
WF.loadDevAreas();
check("kept", CMLib.map().getArea("Fixes Dev Hub") == A, "the dev area was loaded again");

// an exit from a shipped area into it: WORLD CHECK warns
var devRoom = A.getProperMap().nextElement();
var d = -1;
for (var i = 0; i < 4; i++) if (R.rawDoors()[i] == null && R.getRawExit(i) == null) { d = i; break; }
if (d >= 0) {
	R.rawDoors()[d] = devRoom;
	R.setRawExit(d, CMClass.getExit("Open"));
	var parts = new java.util.HashSet(); parts.add("EXITS");
	var found = "" + CM.core.WorldCheck.report(CM.core.WorldCheck.run(R.getArea(), parts));
	check("check-warns", has(found, "in the dev area Fixes Dev Hub, which a normal game does not load"), found);
	R.rawDoors()[d] = null;
	R.setRawExit(d, null);
}

// SAVE writes it to dev/, and WORLD DEV OFF moves it to areas/
devFile["delete"]();
WF.exportAll();
check("export-dev", devFile.isFile() && !new java.io.File(areasDir, "fixes-dev-hub.cmare").isFile(), "after SAVE: dev " + devFile.isFile());
var said = world("DEV Fixes Dev Hub OFF");
check("off", has(said, "Fixes Dev Hub is a shipped area again") && !WF.isDevArea(A), said);
WF.exportAll();
check("export-shipped", !devFile.isFile() && new java.io.File(areasDir, "fixes-dev-hub.cmare").isFile(), "after OFF and SAVE: dev " + devFile.isFile());
said = world("DEV Fixes Dev Hub ON");
check("on", has(said, "Fixes Dev Hub is a dev area") && WF.isDevArea(A), said);
WF.exportAll();
check("export-back", devFile.isFile() && !new java.io.File(areasDir, "fixes-dev-hub.cmare").isFile(), "after ON and SAVE: dev " + devFile.isFile());

// put the world back: the area gone, its file too
CMLib.map().obliterateMapArea(A);
devFile["delete"]();
WF.exportAll();
B.setSession(null);
B.destroy();
done();
