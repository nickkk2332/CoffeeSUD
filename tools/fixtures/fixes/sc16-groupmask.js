// SC16: +ANYINGROUP and +ALLINGROUP apply the rest of a mask to the
// subject's group in the room: anyone, everyone; - turns them around.
FIXFILE = "sc16-groupmask";
var R = M.location();
var L = newMob("GenMob", R); L.setName("Fixsc16lead");
var F = newMob("GenMob", R); F.setName("Fixsc16pass");
F.setFollowing(L);
var X = newMob("GenMob", R); X.setName("Fixsc16alone");
function mk(m, mask) { return ("" + CMLib.masking().maskCheck(mask, m, true)) == "true"; }
var any = "+ANYINGROUP -NAMES +Fixsc16pass", all = "+ALLINGROUP -NAMES +Fixsc16pass";
check("any", mk(L, any) && mk(F, any) && !mk(X, any), "lead " + mk(L, any) + ", follower " + mk(F, any) + ", alone " + mk(X, any));
check("all", !mk(L, all) && !mk(F, all), "lead " + mk(L, all) + ", follower " + mk(F, all));
check("all-yes", mk(L, "+ALLINGROUP -NAMES +Fixsc16pass +Fixsc16lead"), "everyone is one of the two");
check("minus", !mk(L, "-ANYINGROUP -NAMES +Fixsc16pass") && mk(X, "-ANYINGROUP -NAMES +Fixsc16pass") && mk(L, "-ALLINGROUP -NAMES +Fixsc16pass"), "the - forms");
// a member out of the room does not count
var R2 = otherRoom();
R2.bringMobHere(F, false);
check("room", !mk(L, any), "the follower is not here, yet counted");
check("help", ("" + CMLib.masking().maskDesc(any)).indexOf("Requires anyone of the group here") >= 0, "the mask's description: " + CMLib.masking().maskDesc(any));
[L, F, X].forEach(function(x) { x.destroy(); });
done();
