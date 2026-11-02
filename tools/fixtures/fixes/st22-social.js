// ST22: SOCIAL_PROG EXACT matches the whole name, ME only a social aimed at
// the scripted mob; without them, as before.
FIXFILE = "st22-social";
var SEv = CM.core.ScriptEvents;
check("exact", SEv.socialMatches("EXACT KNEEL", "KNEEL <T-NAME>", false) && !SEv.socialMatches("EXACT KNEE", "KNEEL <T-NAME>", false), "EXACT");
check("as-before", SEv.socialMatches("KNEE", "KNEEL <T-NAME>", false), "a start still matches without EXACT");
check("me", SEv.socialMatches("EXACT ME KNEEL", "KNEEL <T-NAME>", true) && !SEv.socialMatches("ME KNEEL", "KNEEL <T-NAME>", false), "ME");
check("form", SEv.socialMatches("EXACT BOW SELF", "BOW SELF", false) && !SEv.socialMatches("EXACT BOW SELF", "BOW <T-NAME>", false), "a social's target form");
var R = M.location();
var Q = newMob("GenMob", R);
Q.setName("a fixst22 queen");
var S = scriptOn(Q, "SOCIAL_PROG EXACT ME KNEEL\nmpsetvar FIXTEST FIXST22K $n\n~\n");
var soc = CMLib.socials().fetchSocial("KNEEL <T-NAME>", true);
check("social", soc != null, "no KNEEL <T-NAME> social");
if (soc != null) {
	clearVar("FIXST22K");
	R.send(M, CMClass.getMsg(M, Q, soc, CMMsg.MSG_NOISYMOVEMENT, null));
	runQueued(S);
	check("queen", gvar("FIXST22K") == M.Name(), "FIXST22K='" + gvar("FIXST22K") + "'");
	clearVar("FIXST22K");
	var Other = newMob("GenMob", R);
	R.send(M, CMClass.getMsg(M, Other, soc, CMMsg.MSG_NOISYMOVEMENT, null));
	runQueued(S);
	check("not-me", gvar("FIXST22K") == "", "FIXST22K='" + gvar("FIXST22K") + "'");
	Other.destroy();
}
Q.destroy();
done();
