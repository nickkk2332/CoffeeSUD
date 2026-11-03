// smoke (item 1): after the restore, the one Sunblade is counted, and no
// second came into the third chest
var CM = Packages.com.planet_ink.coffee_mud;
var CMLib = CM.core.CMLib, U = CM.core.Uniques;
var M = mob(), R = M.location();
var D = CMLib.catalog().getCatalogItemData("the Sunblade");
var chests = [];
for (var i = 0; i < R.numItems(); i++)
	if (("" + R.getItem(i).Name()) == "an iron chest") chests.push(R.getItem(i));
var inside = chests.map(function (C) {
	var n = 0;
	for (var i = 0; i < R.numItems(); i++)
		if ((R.getItem(i).container() == C) && (("" + R.getItem(i).Name()) == "the Sunblade")) n++;
	return n;
});
M.tell("UNIQUE after: " + U.live(D, null) + " in the game, " + U.ever("the Sunblade") + " ever; in the chests " + inside.join(", "));
