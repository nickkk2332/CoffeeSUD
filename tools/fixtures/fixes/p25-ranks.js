// P25: rank ladders in world/ranks.ini: REQUIRES (rising by itself), MPRANK, RANK(), REWARDS once, EFFECTS and TITLE held at the rank, RANKS with NEXT, and the RANKUP achievement event.
FIXFILE = "p25-ranks";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function clean(t) { return s(t).replace(/\^./g, "").replace(/\s+/g, " "); }
var RK = CM.core.Ranks;
var Agent = CM.Common.interfaces.AccountStats.Agent;
var R = M.location();
var F = new java.io.File(CM.core.save.WorldFiles.worldDir(), "ranks.ini");
var had = F.exists() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8")) : null;
var made = [];
function write(f, text) { java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); f.setLastModified(java.lang.System.currentTimeMillis() + 2000); }
clearVar("P25RANK");
try {
	write(F, "# test ladder\n[LANTERN]\nNAME=Order of the Lantern\nRANKS=\"Initiate, Acolyte, Lantern-bearer, Warden, Keeper\"\n"
		+ "RANK1.REQUIRES=\"-TATTOOS +FIXP25SWORN\"\nRANK1.NEXT=an oath at the shrine\nRANK2.REWARDS=\"2 QP\"\n"
		+ "RANK3.EFFECTS=Prop_Smell;lamp oil\nRANK3.TITLE=*, Lantern-bearer\nRANK4.NEXT=the Vigil of Ash\n");
	var L = RK.find("LANTERN");
	check("ladder", L != null && s(L.name()) == "Order of the Lantern" && L.ranks().size() == 5 && s(L.rankName(3)) == "Lantern-bearer", s(L));
	CMLib.achievements().evaluateAchievement(Agent.PLAYER, 'ACH_FIXP25WARDEN=EVENT="RANKUP" DISPLAY="Become a Warden" TITLE="" REWARDS="" PLAYERMASK="" NUM=4 LADDER=LANTERN', true);
	var P = CMClass.getMOB("StdMOB"); P.setName("Fixp25hero"); P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	P.baseCharStats().setMyRace(CMClass.getRace("Human")); P.basePhyStats().setLevel(5); P.recoverCharStats(); P.recoverPhyStats();
	P.bringToLife(R, true); made.push(P);
	// RANKS before joining: the first rank's NEXT
	check("not yet", RK.rank(P, "LANTERN") == 0 && has(clean(RK.list(P)), "not a member: next rank needs an oath at the shrine"), clean(RK.list(P)));
	// REQUIRES met: rises by itself
	P.addTattoo("FIXP25SWORN");
	RK.check(P);
	check("requires", RK.rank(P, "LANTERN") == 1 && P.findTattoo("LANTERN_1") != null, RK.rank(P, "LANTERN"));
	// MPRANK +1, and its REWARDS once
	var qp0 = P.getQuestPoint();
	var sm = scriptedMob("Fixp25 keeper", "ONCE_PROG 100\nmprank Fixp25hero LANTERN +1\nmpsetvar FIXTEST P25RANK $%RANK(Fixp25hero LANTERN)%\n~\n"); made.push(sm);
	tickMob(sm);
	check("mprank", RK.rank(P, "LANTERN") == 2 && P.findTattoo("LANTERN_1") == null && P.getQuestPoint() == qp0 + 2, RK.rank(P, "LANTERN") + " qp " + P.getQuestPoint() + "/" + qp0);
	check("rank()", gvar("P25RANK") == "2", gvar("P25RANK"));
	// =3: its effect and title, held while there
	RK.change(P, "LANTERN", "=3");
	check("effects and title", P.fetchEffect("Prop_Smell") != null && P.playerStats().getTitles().contains("*, Lantern-bearer"), s(P.fetchEffect("Prop_Smell")) + " " + P.playerStats().getTitles());
	check("ranks list", has(clean(RK.list(P)), "Order of the Lantern: Lantern-bearer (3/5): next rank needs the Vigil of Ash"), clean(RK.list(P)));
	// down again: the effect and title go; up again: the rewards do not come twice
	RK.change(P, "LANTERN", "-2");
	check("demoted", RK.rank(P, "LANTERN") == 1 && P.fetchEffect("Prop_Smell") == null && !P.playerStats().getTitles().contains("*, Lantern-bearer"), RK.rank(P, "LANTERN"));
	var qp1 = P.getQuestPoint();
	RK.change(P, "LANTERN", "+1");
	check("rewards once", RK.rank(P, "LANTERN") == 2 && P.getQuestPoint() == qp1, P.getQuestPoint() + "/" + qp1);
	// an effect of the same kind from another source is neither taken for the rank's nor removed with it
	var other = CMClass.getAbility("Prop_Smell"); other.setMiscText("sea salt"); P.addNonUninvokableEffect(other);
	RK.change(P, "LANTERN", "=3");
	function smells() { var out = []; for (var e = 0; e < P.numEffects(); e++) { var E = P.fetchEffect(e); if (E != null && s(E.ID()) == "Prop_Smell") out.push(s(E.text())); } return out.sort().join("|"); }
	check("one of a kind", smells() == "sea salt", smells()); // the engine keeps one effect of a kind
	RK.change(P, "LANTERN", "-1");
	check("other effect kept", smells() == "sea salt", smells());
	P.delEffect(other);
	RK.change(P, "LANTERN", "=2");
	// RANKUP: reaching rank 4
	RK.change(P, "LANTERN", "=4");
	check("rankup", P.findTattoo("ACH_FIXP25WARDEN") != null, "no achievement");
	check("command and help", CMClass.getCommand("RanksCmd") != null && has(CMLib.help().getHelpText("RANKS", M, false), "RANKS") && has(CMLib.help().getHelpText("SCRIPTABLE", M, true), "MPRANK") && has(CMLib.help().getHelpText("RANKLADDERS", M, true), "REQUIRES"), "no help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	CMLib.achievements().deleteAchievement("ACH_FIXP25WARDEN");
	if (had == null) F["delete"](); else write(F, had);
}
done();
