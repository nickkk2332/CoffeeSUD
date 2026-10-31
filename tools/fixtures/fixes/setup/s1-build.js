// S1, in builder mode: the area offers s1quest to whoever looks around in it
var A = M.location().getArea();
var P = CMClass.getAbility("Prop_QuestGiver");
P.setMiscText("QUEST=s1quest TRIGGER=LOOK MESSAGE=FIXOFFER DETAILS=false");
A.addNonUninvokableEffect(P);
CMLib.database().DBUpdateArea(A.Name(), A);
M.tell("FIXSETUP s1 area offers the quest");
