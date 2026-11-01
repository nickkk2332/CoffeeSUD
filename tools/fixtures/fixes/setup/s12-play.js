// S12, in play: a warrant for theft and an unpaid fine against the player
FIXFILE = "s12-play";
var A = M.location().getArea();
var B = A.fetchBehavior("Arrest");
check("laws", B != null, "the area has no laws");
var laws = B.legalInfo(A);
var W = CMClass.getCommon("DefaultArrestWarrant");
W.setCriminal(M);
W.setCrime("theft");
W.setLastOffense(CM.core.GameTime.now());
laws.warrants().add(W);
B.modifyAssessedFines(25.0, M);
check("wanted", laws.warrants().size() == 1 && B.finesOwed(M) == 25.0, "not wanted");
done();
