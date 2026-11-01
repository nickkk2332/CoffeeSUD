// S2, in play: the start room is a save point, and the room north refuses
// saves but autosaves the first time each character comes in
FIXFILE = "s2-points";
var here = M.location();
var north = here.getRoomInDir(CM.core.Directions.NORTH);
check("rooms", north != null, "no room north");
var P = CMClass.getAbility("Prop_SavePoint");
P.setMiscText("MSG=\"FIXPOINT you rest at the shrine.\"");
here.addNonUninvokableEffect(P);
var N = CMClass.getAbility("Prop_NoSave");
N.setMiscText("MSG=\"FIXNOSAVE the walls whisper too loudly.\"");
north.addNonUninvokableEffect(N);
var A = CMClass.getAbility("Prop_AutoSave");
A.setMiscText("ONCE=CHAR SLOT=auto MSG=\"FIXAUTO the game saved itself.\"");
north.addNonUninvokableEffect(A);
done();
