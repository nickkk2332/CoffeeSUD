// S4, after the restore: the player is still on the warhorse, and the
// wagon and the guard are as they were
FIXFILE = "s4-check";
var R = M.location();
function find(name) {
	for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == name) return R.fetchInhabitant(i);
	for (var j = 0; j < R.numItems(); j++) if (R.getItem(j).Name() == name) return R.getItem(j);
	return null;
}
var ox = find("an ox"), wagon = find("a wagon"), guard = find("a mounted guard"), steed = find("a grey horse");
check("player-riding", (M.riding() != null) && (M.riding().Name() == "a warhorse"), "the player rides " + M.riding());
check("save-hitched", (wagon != null) && (ox != null) && (wagon.riding() == ox), "the wagon is not hitched");
check("save-mounted", (guard != null) && (steed != null) && (guard.riding() == steed), "the guard is not mounted");
var count = 0;
for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == "a warhorse") count++;
check("one-warhorse", count == 1, count + " warhorses");
done();
