// Phase 2 stage 5 (A19): a loadout kept in the save. The player keeps "melee" (a sword wielded), then lowers it.
FIXFILE = "p2-loadout-play";
var W = CM.Items.interfaces.Wearable;
CMProps.instance().setProperty("LOADOUTS", "2");
var sword = CMClass.getWeapon("GenWeapon"); sword.setName("a fixp2 packed sword"); sword.setRawProperLocationBitmap(W.WORN_WIELD); M.addItem(sword);
sword.wearAt(W.WORN_WIELD);
var said = "" + CM.core.Loadouts.save(M, "melee");
sword.unWear();
var P = CM.core.Loadouts.plan(M, "melee");
check("kept", said.indexOf("Loadout 'melee' kept:") >= 0 && P.why == null && P.changes() == 1, said + " / " + P.why + " / " + P.changes());
done();
