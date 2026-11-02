// I22: MPTRANSFORM item name [KEEP=state,tags,uses,container,worn|ALL|NONE]: in the same place, $p the new item;
// JavaScript's transform(item, name[, keep]).
FIXFILE = "i22-transform";
var W = CM.Items.interfaces.Wearable;
var OV = CM.core.ObjectVars;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
function cat(cls, name) { var I = CMClass.getItem(cls); I.setName(name); I.setDisplayText(name + " lies here."); I.recoverPhyStats(); CMLib.catalog().addCatalog(I); I.destroy(); }
cat("GenItem", "a fixi22 burning torch");
cat("GenContainer", "a fixi22 open crate");
var PS = CM.Common.interfaces.PhyStats;
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi22Forge");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
var R = CMClass.getLocale("StdRoom"); R.setRoomID("Fixi22Forge#0"); R.setDisplayText("Fixi22 forge"); R.setArea(X);
var V = newMob("GenMob", R); V.setName("Fixi22pip");
// an unlit torch, held, with a tag and a state
var torch = CMClass.getItem("GenItem"); torch.setName("an unlit fixi22 torch");
torch.setRawLogicalAnd(false); torch.setRawProperLocationBitmap(W.WORN_HELD);
V.addItem(torch); torch.wearAt(W.WORN_HELD);
torch.addTag("FIXI22TAG");
var st = new java.util.TreeMap(); st.put("SOAKED", "yes"); OV.reborn(torch, st);
torch.setUsesRemaining(42);
var S = scriptOn(torch, "FUNCTION_PROG DIP\nmptransform $i a fixi22 burning torch\nmpsetvar FIXTEST FIXI22P $p\n~\n");
clearVar("FIXI22P");
S.callFunction(torch, "DIP", "", V);
var lit = V.findItem(null, "burning torch");
check("held", lit != null && torch.amDestroyed() && lit.rawWornCode() == W.WORN_HELD && V.findItem(null, "unlit") == null,
	"new " + lit + " old gone " + torch.amDestroyed() + " worn " + (lit == null ? "-" : lit.rawWornCode()));
check("p", has(gvar("FIXI22P"), "fixi22 burning torch"), "$p '" + gvar("FIXI22P") + "'");
check("default-keep", lit != null && (!lit.hasTag("FIXI22TAG")) && s(OV.vars(lit).get("SOAKED")) != "yes" && lit.usesRemaining() != 42, "kept more than the place");
// KEEP=ALL: tags, state and uses too
var stick = CMClass.getItem("GenItem"); stick.setName("a fixi22 stick"); V.addItem(stick);
stick.addTag("FIXI22TAG"); OV.reborn(stick, st); stick.setUsesRemaining(42);
var S2 = scriptOn(stick, "FUNCTION_PROG GO\nmptransform $i a fixi22 burning torch KEEP=ALL\n~\n");
S2.callFunction(stick, "GO", "", V);
var lit2 = null;
for (var i = 0; i < V.numItems(); i++) { var I = V.getItem(i); if (I != lit && s(I.Name()) == "a fixi22 burning torch") lit2 = I; }
check("keep-all", lit2 != null && lit2.hasTag("FIXI22TAG") && s(OV.vars(lit2).get("SOAKED")) == "yes" && lit2.usesRemaining() == 42,
	"new " + lit2 + " tag " + (lit2 && lit2.hasTag("FIXI22TAG")) + " state " + (lit2 && OV.vars(lit2)) + " uses " + (lit2 && lit2.usesRemaining()));
// in a room, in a container, with contents of its own: JavaScript's transform()
var chest = CMClass.getItem("GenContainer"); chest.setName("a fixi22 chest"); chest.setCapacity(500); R.addItem(chest);
var box = CMClass.getItem("GenContainer"); box.setName("a fixi22 box"); box.setCapacity(100); R.addItem(box); box.setContainer(chest);
var gem = CMClass.getItem("GenItem"); gem.setName("a fixi22 gem"); R.addItem(gem); gem.setContainer(box);
var m = scriptedMob("a fixi22 helper", "RAND_PROG 100\n<SCRIPT>\n"
	+ "var it = findItem(findItem(room('Fixi22Forge#0'), 'fixi22 chest'), 'fixi22 box');\n"
	+ "var n = transform(it, 'a fixi22 open crate');\n"
	+ "setVar('FIXTEST', 'FIXI22J', n == null ? 'null' : '' + n.Name());\n"
	+ "</SCRIPT>\nmpdisable\n~\n");
R.bringMobHere(m, false);
clearVar("FIXI22J");
tickMob(m);
var crate = null;
for (var i = 0; i < R.numItems(); i++) if (s(R.getItem(i).Name()) == "a fixi22 open crate") crate = R.getItem(i);
check("js", gvar("FIXI22J") == "a fixi22 open crate" && crate != null && box.amDestroyed() && crate.container() == chest && gem.container() == crate,
	"js '" + gvar("FIXI22J") + "' crate " + crate + " in " + (crate && crate.container()) + " gem in " + gem.container());
check("help", has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPTRANSFORM [ITEM]"), "no help");

[V, m].forEach(function (x) { x.destroy(); });
CMLib.map().obliterateMapArea(X);
["a fixi22 burning torch", "a fixi22 open crate"].forEach(function (n) { var c = CMLib.catalog().getCatalogItem(n); if (c != null) CMLib.catalog().delCatalog(c); });
done();
