// S9, after the restore: the sellsword is still on the job, for the player
FIXFILE = "s9-check";
var R = M.location();
var sword = null;
for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == "a sellsword") sword = R.fetchInhabitant(i);
check("sellsword", sword != null, "no sellsword");
var H = (sword == null) ? null : sword.fetchBehavior("Hireling");
var left = (H == null) ? 0 : (field(H, "onTheJobUntil") - CM.core.GameTime.now());
check("still-hired", (H != null) && field(H, "workingFor") == M.Name() && left > 7000000 && left <= 7200000, "for '" + ((H == null) ? "" : field(H, "workingFor")) + "', " + left + " ms left");
check("follows", (sword != null) && sword.amFollowing() == M, "the sellsword does not follow");
done();
