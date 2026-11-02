// ST13: CRAFT_PROG and RECIPE_PROG hear what a crafter makes and learns.
FIXFILE = "st13-craft";
var R = M.location();
["FIXST13C", "FIXST13R", "FIXST13X", "FIXST13U"].forEach(clearVar);
var S = scriptOn(R, "CRAFT_PROG Weaponsmithing sword SUCCESS\nmpsetvar FIXTEST FIXST13C $n:$G:$o\n~\nCRAFT_PROG * ALL RUINED\nmpsetvar FIXTEST FIXST13U $o\n~\n"
	+ "RECIPE_PROG * dagger\nmpsetvar FIXTEST FIXST13R $G:$o\n~\nCRAFT_PROG Cooking ALL\nmpsetvar FIXTEST FIXST13X $o\n~\n");
var A = CMClass.getAbility("Weaponsmithing");
var I = CMClass.getItem("GenWeapon");
I.setName("a fine fixst13 sword");
M.addItem(I);
R.send(M, CMClass.getMsg(M, I, A, CMMsg.TYP_ITEMGENERATED | CMMsg.MASK_ALWAYS, null));
runQueued(S);
check("craft", gvar("FIXST13C") == M.Name() + ":Weaponsmithing:a fine fixst13 sword" && gvar("FIXST13X") == "", "FIXST13C='" + gvar("FIXST13C") + "', FIXST13X='" + gvar("FIXST13X") + "'");
var J = CMClass.getItem("GenRuinedItem");
if (J == null) J = CMClass.getItem("GenItem");
J.setName("a ruined fixst13 lump");
M.addItem(J);
R.send(M, CMClass.getMsg(M, J, A, CMMsg.TYP_ITEMGENERATED | CMMsg.MASK_ALWAYS, null));
runQueued(S);
check("ruined", ("" + J.ID()).indexOf("Ruined") < 0 || gvar("FIXST13U") == "a ruined fixst13 lump", "FIXST13U='" + gvar("FIXST13U") + "', " + J.ID());
var K = CMClass.getItem("GenWeapon");
K.setName("a fixst13 dagger");
R.send(M, CMClass.getMsg(M, K, A, CMMsg.TYP_RECIPELEARNED | CMMsg.MASK_ALWAYS, null));
runQueued(S);
check("recipe", gvar("FIXST13R") == "Weaponsmithing:a fixst13 dagger", "FIXST13R='" + gvar("FIXST13R") + "'");
R.delScript(S);
I.destroy(); J.destroy(); K.destroy();
done();
