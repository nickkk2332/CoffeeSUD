// S2: a fight that lasts, with a mob too tough to fall to one blow
FIXFILE = "s2-fight";
var brute = newMob("GenMob", M.location());
brute.setName("a stone brute");
brute.baseState().setHitPoints(5000);
brute.recoverMaxState();
brute.resetToMaxState();
M.setVictim(brute);
brute.setVictim(M);
check("fighting", M.isInCombat(), "the player is not fighting");
done();
