// Smoke test helper: lights the player's torch with a timed spell and has
// every dog in the room follow the player.
var CMClass = Packages.com.planet_ink.coffee_mud.core.CMClass;
var M = mob();
for(var i = 0; i < M.numItems(); i++)
{
	var I = M.getItem(i);
	if(I.name().toLowerCase().indexOf("torch") >= 0)
		CMClass.getAbility("Spell_Light").startTickDown(M, I, 77);
}
var R = M.location();
for(var m = 0; m < R.numInhabitants(); m++)
{
	var F = R.fetchInhabitant(m);
	if(F.name().toLowerCase().indexOf("dog") >= 0)
		F.setFollowing(M);
}
