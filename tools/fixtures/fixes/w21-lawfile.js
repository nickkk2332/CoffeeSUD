// W21: an area's own law (Arrest custom) as world content: the law book's edits in builder mode go to world/laws/[area].ini, which a new game starts from; a game's saved law wins in a game.
FIXFILE = "w21-lawfile";
var PS = CM.Common.interfaces.PhyStats;
var Resources = CM.core.Resources;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var DB = CMLib.database();
var SG = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.SaveGame");
var BM = SG.getDeclaredField("builderMode"); BM.setAccessible(true);
var wasBuilder = BM.getBoolean(null);
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw21 Riverton");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
DB.DBDeletePlayerData(X.Name(), "ARREST");
var F = WorldFiles.lawFile(X.Name());
if (F.exists()) F["delete"]();
var B = CMClass.getBehavior("Arrest"); B.setParms("custom"); X.addBehavior(B);
function fresh() { Resources.removeResource("LEGAL-" + X.Name() + java.lang.Long.toString(field(B, "extraLawParms").hashCode())); field(B, "lastLawChange"); return B.legalInfo(X); }
function tax() { return s(fresh().getInternalStr("SALESTAX")); }
try {
	// builder mode: the law book bans nightshade and sets a 5% sales tax, and the law lands in the world's files
	BM.setBoolean(null, true);
	var L = fresh();
	L.setInternalStr("SALESTAX", "5");
	L.setInternalStr("BANNED", "nightshade");
	B.updateLaw(X);
	var text = F.exists() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8")) : "";
	check("written", s(F.getName()) == "fixw21-riverton.ini" && has(text, "SALESTAX=5") && has(text, "BANNED=nightshade") && has(text, "# The law of Fixw21 Riverton"), F + ": " + text);
	// a new game: no saved law, so the world's file
	BM.setBoolean(null, false);
	DB.DBDeletePlayerData(X.Name(), "ARREST");
	var newGame = tax();
	// in a game the law changes (a conquest, a law book) stay in the save, not the world's files
	L = fresh(); L.setInternalStr("SALESTAX", "7"); B.updateLaw(X);
	var inGame = tax();
	var text2 = s(new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"));
	// back in builder mode the world's file is the law
	BM.setBoolean(null, true);
	var builder = tax();
	check("read", newGame == "5" && inGame == "7" && has(text2, "SALESTAX=5") && builder == "5", "new game " + newGame + " in game " + inGame + " builder " + builder);
	check("help", has(CMLib.help().getHelpText("BEHAVIOR_ARREST", M, true), "world/laws/"), "no help");
} finally {
	BM.setBoolean(null, wasBuilder);
	X.delBehavior(B);
	DB.DBDeletePlayerData(X.Name(), "ARREST");
	if (F.exists()) F["delete"]();
	CMLib.map().delArea(X);
}
done();
