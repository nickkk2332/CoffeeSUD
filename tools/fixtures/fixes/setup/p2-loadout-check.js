// Phase 2 stage 5 (A19), after the restore: the loadout is back, still knows its sword, and the swap works.
FIXFILE = "p2-loadout-check";
var W = CM.Items.interfaces.Wearable;
CMProps.instance().setProperty("LOADOUTS", "2");
var store = M.fetchEffect("Prop_Loadouts");
check("restored", store != null && ("" + store.text()).indexOf("melee") >= 0, "no loadouts after the restore");
var P = CM.core.Loadouts.plan(M, "melee");
check("refs-kept", P.why == null && P.changes() == 1, "why " + P.why + " changes " + P.changes());
var said = "" + CM.core.Loadouts.use(M, "melee");
var sword = null; for (var i = 0; i < M.numItems(); i++) if (("" + M.getItem(i).Name()) == "a fixp2 packed sword") sword = M.getItem(i);
check("swap-after-restore", sword != null && sword.amWearingAt(W.WORN_WIELD), said);
done();
