// S4, in builder mode: a wagon hitched to an ox, and a guard on a horse,
// placed in the start room and written to the world files
FIXFILE = "s4-build";
var R = M.location();
var ox = newMob("GenRideable", R);
ox.setName("an ox");
ox.setDisplayText("An ox stands here, in harness.");
var wagon = CMClass.getItem("GenRideable");
wagon.setName("a wagon");
wagon.setDisplayText("A wagon stands here.");
R.addItem(wagon);
wagon.setRiding(ox);
var steed = newMob("GenRideable", R);
steed.setName("a grey horse");
steed.setDisplayText("A grey horse stands here.");
var guard = newMob("GenMob", R);
guard.setName("a mounted guard");
guard.setDisplayText("A mounted guard sits here.");
guard.setRiding(steed);
check("hitched", wagon.riding() == ox, "the wagon is not hitched");
check("mounted", guard.riding() == steed, "the guard is not mounted");
// as builder commands do: the database cache, then the world files at quit
CMLib.database().DBUpdateMOBs(R);
CMLib.database().DBUpdateItems(R);
done();
