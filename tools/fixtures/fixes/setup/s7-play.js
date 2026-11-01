// S7, in play: a mob that cannot be saved, for SAVES CHECK
FIXFILE = "s7-play";
var wisp = newMob("GenMob", M.location());
wisp.setName("a summoned wisp");
wisp.basePhyStats().setDisposition(wisp.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_UNSAVABLE);
wisp.recoverPhyStats();
check("unsavable", !wisp.isSavable(), "the wisp can be saved");
done();
