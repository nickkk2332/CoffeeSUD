// B12: SANDBOX: a scratch area that is never saved; PREVIEW of a catalog item there, gone on LEAVE;
// CLEAR empties it.
FIXFILE = "b12-sandbox";
var SB = CM.core.Sandbox;
var WF = CM.core.save.WorldFiles;
var R0 = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }

var said = "" + SB.enter(M);
var A = CMLib.map().getArea("Sandbox");
check("enter", A != null && SB.isIn(M) && has(said, "You are in the sandbox") && !CMLib.flags().isSavable(A), said);
check("rooms", CMLib.map().getRoom("Sandbox#3") != null && CMLib.map().getRoom("Sandbox#0").rawDoors()[CM.core.Directions.EAST] == CMLib.map().getRoom("Sandbox#1"), "the sandbox's rooms");
WF.exportAll();
check("not-saved", !new java.io.File(new java.io.File(WF.worldDir(), "areas"), "sandbox.cmare").isFile(), "the sandbox went to the world files");

// PREVIEW: a copy of a catalog item, in the sandbox
var I = CMClass.getItem("GenItem");
I.setName("a fixb12 shovel");
I.setDescription("A fixb12 shovel for digging.");
CMLib.catalog().addCatalog(I);
said = "" + SB.preview(M, "a fixb12 shovel");
var copy = M.location().findItem(null, "fixb12");
check("preview", has(said, "a fixb12 shovel (GenItem)") && has(said, "A fixb12 shovel for digging.") && copy != null && SB.isIn(M), said);
check("preview-none", has("" + SB.preview(M, "nosuchfixthing"), "There is no catalog mob or item, or template, 'nosuchfixthing'."), "an unknown name");
said = "" + SB.leave(M);
check("leave", M.location() == R0 && copy.amDestroyed() && has(said, "1 previews are gone"), said);

// CLEAR
SB.enter(M);
var J = CMClass.getItem("StdItem");
M.location().addItem(J);
said = "" + SB.clear();
check("clear", J.amDestroyed() && has(said, "The sandbox is cleared"), said);
SB.leave(M);
check("back", M.location() == R0, "the builder is in " + CMLib.map().getExtendedRoomID(M.location()));
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixb12 shovel"));
WF.exportAll();
done();
