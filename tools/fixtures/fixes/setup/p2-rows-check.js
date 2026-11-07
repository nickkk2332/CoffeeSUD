// Phase 2 stage 1, after the restore: the guard is back in row 2 and the player in row 1.
FIXFILE = "p2-rows-check";
var guard = null;
for (var f = M.followers(); f.hasMoreElements();) { var F = f.nextElement().first; if (("" + F.Name()) == "a fixp2 row guard") guard = F; }
check("guard-back", guard != null && guard.amFollowing() == M, "the guard did not come back following");
check("rows-kept", guard != null && M.fetchFollowerOrder(guard) == 2 && M.fetchFollowerOrder(M) == 1,
	"guard " + (guard == null ? "-" : M.fetchFollowerOrder(guard)) + " self " + M.fetchFollowerOrder(M));
done();
