// S4, in play: the area file kept the hitch and the mount, and the player
// mounts a warhorse of their own
FIXFILE = "s4-play";
var R = M.location();
function find(name) {
	for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == name) return R.fetchInhabitant(i);
	for (var j = 0; j < R.numItems(); j++) if (R.getItem(j).Name() == name) return R.getItem(j);
	return null;
}
var ox = find("an ox"), wagon = find("a wagon"), guard = find("a mounted guard"), steed = find("a grey horse");
check("world-hitched", (wagon != null) && (ox != null) && (wagon.riding() == ox), "the world file lost the hitch");
check("world-mounted", (guard != null) && (steed != null) && (guard.riding() == steed), "the world file lost the mount");
var horse = newMob("GenRideable", R);
horse.setName("a warhorse");
horse.setDisplayText("A warhorse stands here.");
M.setRiding(horse);
check("riding", M.riding() == horse, "the player is not riding");
done();
