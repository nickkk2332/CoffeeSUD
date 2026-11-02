// SC3: MPMLOAD, MPOLOAD and the room and shop forms load from the catalog
// (CATALOG [FORCE] name [count]), counted and capped; CATALOG() and
// CATALOGCOUNT(); quest LOAD MOB|ITEM CATALOG; RandomMonsters catalog:.
FIXFILE = "sc3-catalog";
var R = M.location();
["FIXSC3A", "FIXSC3B", "FIXSC3C", "FIXSC3D", "FIXSC3E"].forEach(clearVar);
function entry(cls, name, cap, mob) {
	var P = mob ? CMClass.getMOB(cls) : CMClass.getItem(cls);
	P.setName(name);
	P.recoverPhyStats();
	CMLib.catalog().addCatalog(P);
	P.destroy(); // the original stays a counted copy otherwise
	var data = mob ? CMLib.catalog().getCatalogMobData(name) : CMLib.catalog().getCatalogItemData(name);
	if (cap > 0) data.setCap(cap);
	return data;
}
var GD = entry("GenMob", "a fixsc3 goblin", 0, true);
var OD = entry("GenMob", "a fixsc3 ogre", 1, true);
var SD = entry("GenItem", "a fixsc3 sunblade", 1, false);
var HD = entry("GenItem", "a fixsc3 shield", 0, false);
function inRoom(name) { for (var i = 0; i < R.numItems(); i++) if (("" + R.getItem(i).Name()) == name) return R.getItem(i); return null; }
function here(name) { var n = 0; for (var i = 0; i < R.numInhabitants(); i++) if (("" + R.fetchInhabitant(i).Name()) == name) n++; return n; }

var m = scriptedMob("a fixsc3 chieftain", "RAND_PROG 100\n"
	+ "mpmload catalog a fixsc3 goblin 2\n"
	+ "mpsetvar FIXTEST FIXSC3A $b\n"
	+ "mpoload catalog a fixsc3 sunblade\n"
	+ "mpoload catalog a fixsc3 sunblade\n"
	+ "if catalog(a fixsc3 sunblade)\n mpsetvar FIXTEST FIXSC3C yes\nelse\n mpsetvar FIXTEST FIXSC3C no\nendif\n"
	+ "mpsetvar FIXTEST FIXSC3B $%CATALOGCOUNT(a fixsc3 sunblade)%:$%CATALOGCOUNT(a fixsc3 goblin)%\n"
	+ "mpoload catalog force a fixsc3 sunblade\n"
	+ "mpsetvar FIXTEST FIXSC3D $%CATALOGCOUNT(a fixsc3 sunblade)%\n"
	+ "mpoloadroom catalog a fixsc3 shield\n"
	+ "~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("mload", here("a fixsc3 goblin") == 2 && gvar("FIXSC3A") == "a fixsc3 goblin" && GD.numReferences() == 2, "goblins here " + here("a fixsc3 goblin") + ", counted " + GD.numReferences() + ", last loaded '" + gvar("FIXSC3A") + "'");
var blades = 0; for (var i = 0; i < m.numItems(); i++) if (("" + m.getItem(i).Name()) == "a fixsc3 sunblade") blades++;
check("cap", gvar("FIXSC3B") == "1:2" && gvar("FIXSC3C") == "no", "FIXSC3B='" + gvar("FIXSC3B") + "', CATALOG() '" + gvar("FIXSC3C") + "'");
check("force", gvar("FIXSC3D") == "2" && blades == 2, "FIXSC3D='" + gvar("FIXSC3D") + "', blades carried " + blades);
check("room", inRoom("a fixsc3 shield") != null && HD.numReferences() == 1, "the shield is not in the room, counted " + HD.numReferences());
check("none", here("a fixsc3 ogre") == 0 && CMLib.catalog().getCatalogMob("a fixsc3 ogre").location() == null, "the catalog's own ogre moved");

// quests: LOAD MOB [n] CATALOG name, LOAD ITEM CATALOG name; the cap holds
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript("set name fixsc3q\nset duration 100\nset area '" + R.getArea().Name() + "'\nload mob 3 catalog a fixsc3 ogre\nload mob 2 catalog a fixsc3 goblin\nload item catalog a fixsc3 shield\n", true);
CMLib.quests().addQuest(Q);
Q.startQuest();
check("quest", Q.running() && OD.numReferences() == 1 && GD.numReferences() == 4 && HD.numReferences() == 2, "running " + Q.running() + ", ogres " + OD.numReferences() + ", goblins " + GD.numReferences() + ", shields " + HD.numReferences());
Q.stopQuest();
check("quest-end", OD.numReferences() == 0 && GD.numReferences() == 2, "after the quest: ogres " + OD.numReferences() + ", goblins " + GD.numReferences());
CMLib.quests().delQuest(Q);

// RandomMonsters: catalog:name,name keeps three, the ogre at most once
var R2 = otherRoom();
var B = CMClass.getBehavior("RandomMonsters");
B.setParms("minmonsters=3 maxmonsters=3 min=1 max=1 chance=100;catalog:a fixsc3 ogre,a fixsc3 goblin");
R2.addBehavior(B);
for (var t = 0; t < 4; t++) B.tick(R2, Tickable.TICKID_ROOM_BEHAVIOR);
var kept = field(B, "maintained");
check("randommonsters", kept.size() == 3 && OD.numReferences() <= 1 && GD.numReferences() >= 4, "kept " + kept.size() + ", ogres " + OD.numReferences() + ", goblins " + GD.numReferences());
R2.delBehavior(B);
for (var i = kept.size() - 1; i >= 0; i--) kept.get(i).destroy();

// FROMFILE file ALL loads every mob of the file (it loaded none)
var xa = newMob("GenMob", R); xa.setName("a fixsc3 rat");
var xb = newMob("GenMob", R); xb.setName("a fixsc3 bat");
new CM.core.CMFile("/resources/_fixes/sc3mobs.cmare", M).saveText("<MOBS>" + CMLib.coffeeMaker().getMobXML(xa) + CMLib.coffeeMaker().getMobXML(xb) + "</MOBS>");
xa.destroy(); xb.destroy();
var f = scriptedMob("a fixsc3 piper", "RAND_PROG 100\nmpmload fromfile _fixes/sc3mobs.cmare all\n~\n");
tickMob(f);
runQueued(f.scripts().nextElement());
check("fromfile-all", here("a fixsc3 rat") == 1 && here("a fixsc3 bat") == 1, "rats " + here("a fixsc3 rat") + ", bats " + here("a fixsc3 bat"));
f.destroy();

// clean up
for (var i = R.numInhabitants() - 1; i >= 0; i--) { var x = R.fetchInhabitant(i); if (("" + x.Name()).indexOf("fixsc3") >= 0 && x != m) x.destroy(); }
var sh = inRoom("a fixsc3 shield"); if (sh != null) sh.destroy();
m.destroy();
["a fixsc3 goblin", "a fixsc3 ogre"].forEach(function(n) { CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob(n)); });
["a fixsc3 sunblade", "a fixsc3 shield"].forEach(function(n) { CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem(n)); });
done();
