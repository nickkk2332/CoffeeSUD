// Phase 2 stage 1: formation rows in a save. The player stands in row 1 and a hired guard follows in row 2.
FIXFILE = "p2-rows-play";
var guard = CMClass.getMOB("GenMob"); guard.setName("a fixp2 row guard"); guard.basePhyStats().setLevel(3); guard.recoverPhyStats();
guard.bringToLife(M.location(), true);
guard.setFollowing(M);
M.addFollower(guard, 2);
M.addFollower(M, 1);
check("rows-set", M.fetchFollowerOrder(guard) == 2 && M.fetchFollowerOrder(M) == 1, "guard " + M.fetchFollowerOrder(guard) + " self " + M.fetchFollowerOrder(M));
done();
