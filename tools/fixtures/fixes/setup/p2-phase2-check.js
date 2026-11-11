// Phase 2 in a save, after the restore: everything p2-phase2-play.js set up is as it was.
FIXFILE = "p2-phase2-check";
function s(x) { return "" + x; }
var order = CMLib.clans().getClan("Fixp2 Order"), rivals = CMLib.clans().getClan("Fixp2 Rivals");
check("orgs-back", order != null && rivals != null && M.getClanRole(order.clanID()) != null && M.getClanRole(rivals.clanID()) == null, "the organizations or the membership did not come back");
check("conflict-kept", rivals != null && s(CM.core.Affiliations.refuses(M, rivals)).indexOf("cannot be held together") >= 0, "the affiliation rule does not hold");
check("pool-kept", order != null && CM.core.OrgPools.units(order, "SUPPLIES") == 3 && s(order.getStat("POOLLEDGER")).indexOf("\"receipt\":1") >= 0,
	order == null ? "no order" : "units " + CM.core.OrgPools.units(order, "SUPPLIES") + " ledger " + order.getStat("POOLLEDGER"));
var healer = null; for (var i = 0; i < M.location().numInhabitants(); i++) { var I = M.location().fetchInhabitant(i); if (s(I.Name()) == "Fixp2 Mender") healer = I; }
check("hold-kept", healer != null && CM.core.Services.held(healer, M).size() == 1, healer == null ? "the mender is gone" : "the held payment is gone");
if (healer != null) { CM.core.Services.done(healer, M, "mend"); check("hold-settled", CM.core.Services.held(healer, M).size() == 0, "still held after DONE"); }
var C = CM.core.Cultures.current(M);
// (the first character of a new game is an archon, who knows every ability already: the culture's record is what is checked)
var hist = CM.core.StoryRecords.read(M).get("cultures");
check("culture-kept", C != null && s(C.id) == "RIVERFOLK" && hist != null && hist.get("history").length == 1 && s(hist.get("history")[0].get("how")) == "command",
	C == null ? "no culture" : s(hist));
var guard = null; for (var f = M.followers(); f.hasMoreElements();) { var F = f.nextElement().first; if (s(F.Name()) == "a fixp2 role guard") guard = F; }
var R = guard == null ? null : CM.core.FormationRoles.of(guard);
check("role-kept", R != null && s(R.id) == "GUARD" && M.fetchFollowerOrder(guard) == 2 && guard.findTattoo("ROLEBACK") != null, guard == null ? "no guard" : "role " + R + " row " + M.fetchFollowerOrder(guard));
if (guard != null) { CM.core.FormationRoles.fightEnded(guard); check("role-returns", M.fetchFollowerOrder(guard) == 0, "row " + M.fetchFollowerOrder(guard)); }
check("talents-kept", s(CM.core.Talents.decided(M, "TIDES")) == "awakened" && s(CM.core.Talents.decided(M, "DREAMS")) == "dormant" && M.fetchAbility("Skill_Swim") != null, s(CM.core.Talents.list(M)));
var book = s(CM.core.Challenges.show(M, "FIXP2"));
check("record-kept", /crossing: best 20 \(.*\); first 30/.test(book), book);
done();
