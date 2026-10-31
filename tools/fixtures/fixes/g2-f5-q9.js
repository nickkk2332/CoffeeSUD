// G2, F5: RandomQuests and factions.
FIXFILE = "g2-f5-q9";
var R = M.location();

// G2: an inline RandomQuests that cannot generate gives up instead of hanging
var giver = newMob("GenMob", R);
giver.setName("a notice board keeper");
var RQ = CMClass.getBehavior("RandomQuests");
RQ.setParms("PATH=fixes/nonexistent.xml INLINE=true MINQUESTS=1 MAXQUESTS=1");
giver.addBehavior(RQ);
var started = java.lang.System.currentTimeMillis();
RQ.tick(giver, Tickable.TICKID_MOB);
var took = java.lang.System.currentTimeMillis() - started;
check("randomquests-no-hang", took < 20000, "took " + took + " ms");
check("randomquests-disabled", field(RQ, "disable").get() == true, "not disabled after failing");
giver.destroy();

// F5: MPFACTION sets a band of the faction it names, by band code
var F = CMLib.factions().getFaction("inclination.ini");
if (F == null) fail("faction-loaded", "inclination.ini is not loaded");
else {
	var band = null;
	for (var e = F.ranges(); e.hasMoreElements();) { var b = e.nextElement(); if (band == null || b.low() > band.low()) band = b; }
	var target = newMob("GenMob", R);
	target.setName("fixfactiontarget");
	var S = scriptedMob("a faction setter", "RAND_PROG 100\nmpfaction fixfactiontarget inclination.ini " + band.codeName() + "\nmpsetvar FIXTEST FIXFVAL $%FACTIONVALUE(fixfactiontarget inclination.ini)%\nmpsetvar FIXTEST FIXFBAND $%FACTIONBAND(fixfactiontarget inclination.ini)%\n~\n");
	clearVar("FIXFVAL"); clearVar("FIXFBAND");
	tickMob(S);
	var v = target.fetchFaction(F.factionID());
	check("mpfaction-band-code", v >= band.low() && v <= band.high(), "value " + v + " not in " + band.codeName() + " " + band.low() + ".." + band.high());
	check("factionvalue", gvar("FIXFVAL") == "" + v, "FACTIONVALUE='" + gvar("FIXFVAL") + "'");
	check("factionband", gvar("FIXFBAND").toUpperCase() == band.codeName().toUpperCase(), "FACTIONBAND='" + gvar("FIXFBAND") + "'");
	S.destroy(); target.destroy();
}
// F5: CHANGE rules keyed to an ability ID are found for that ability
var IF = CMLib.factions().getFaction("inclination.ini");
var bury = CMClass.getAbility("Prayer_Bury");
var events = (IF == null || bury == null) ? null : IF.findAbilityChangeEvents(bury);
check("ability-change-rules", events != null && events.length > 0, "no inclination rules for Prayer_Bury");
var AF = CMLib.factions().getFaction(CMLib.factions().getAlignmentID());
var rd = CMClass.getAbility("Paladin_RighteousDefense");
var events2 = (AF == null || rd == null) ? null : AF.findAbilityChangeEvents(rd);
check("ability-change-rules-alignment", events2 != null && events2.length > 0, "no alignment rules for Paladin_RighteousDefense");
done();
