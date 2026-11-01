// S9, in play: a sellsword hired for two hours follows the player
FIXFILE = "s9-play";
var sword = newMob("GenMob", M.location());
sword.setName("a sellsword");
sword.setDisplayText("A sellsword leans on a spear.");
var H = CMClass.getBehavior("Hireling");
H.setParms("PRICE=10 MINUTES=120");
sword.addBehavior(H);
H.setStat("STATE", "<FOR>" + M.Name() + "</FOR><LEFT>7200000</LEFT><WARN>-1</WARN>");
sword.setFollowing(M);
check("hired", sword.amFollowing() == M && field(H, "workingFor") == M.Name(), "not hired");
done();
