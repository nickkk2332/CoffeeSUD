// Phase 2 in a save, before: an organization with a shared pool and an affiliation rule, a held service payment, a culture,
// a formation role and a fall back, two talents decided, a personal record. p2-phase2-check.js looks at them after a restore.
FIXFILE = "p2-phase2-play";
function s(x) { return "" + x; }
var Files = java.nio.file.Files, W = WorldFiles.worldDir();
function write(path, text) { var f = new java.io.File(W, path); f.getParentFile().mkdirs(); Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
write("orgpools.ini", 'SUPPLIES=TITLE="Supplies" MAX=50 MONEY=5\n');
write("affiliations.ini", "TAG|Fixp2 Order|LAWFUL\nTAG|Fixp2 Rivals|OUTLAW\nCONFLICT|LAWFUL|OUTLAW\nLEAVE|Fixp2 Order|TATTOOS=FIXP2BADGE\n");
write("cultures.ini", "[RIVERFOLK]\nNAME=River folk\nABILITIES=Fishing 40\nADOPT=COMMAND\n");
write("formations.ini", "[GUARD]\nNAME=Guard\nROW=0\nTARGET=ATTACKER\nFALLBACK=30\n");
write("lore/fixp2.ini", "FIXP2_1=Of tides|Lore|Tides turn.\n");
write("talents.ini", 'TIDES=TITLE="Tide-sense" LORE="FIXP2_1" ABILITY=Skill_Swim PROFICIENCY=30\nDREAMS=TITLE="Dream-sight" LORE="FIXP2_1" CHANCE=0\n');
write("challenges.ini", 'FIXP2=TITLE="Fix trials" PREFIX="FIXP2_"\n');
var G = CM.core.Guilds.government("GUILD_NPC");
function makeOrg(name) { var C = CMClass.getCommon("DefaultClan"); C.setName(name); C.setGovernmentID(G.getID()); C.setMinClanMembers(0); C.create(); return C; }
var order = makeOrg("Fixp2 Order"), rivals = makeOrg("Fixp2 Rivals");
order.addMember(M, G.getAcceptPos()); M.addTattoo("FIXP2BADGE");
rivals.addMember(M, G.getAcceptPos());
check("org", M.getClanRole(order.clanID()) != null && M.getClanRole(rivals.clanID()) == null, "memberships wrong");
// the pool
CMLib.beanCounter().addMoney(M, 100);
CM.core.OrgPools.give(M, order, "SUPPLIES", 3);
check("pool", CM.core.OrgPools.units(order, "SUPPLIES") == 3, "units " + CM.core.OrgPools.units(order, "SUPPLIES"));
// a service paid when done, its payment held across the save
var healer = CMClass.getMOB("GenMob"); healer.setName("Fixp2 Mender"); healer.bringToLife(M.location(), true);
var P = CMClass.getAbility("Prop_Service");
P.setMiscText(JSON.stringify({ services: [{ id: "mend", title: "Mending", words: ["mend gear"], cost: { money: 5 }, pay: "done", hold: 100000 }] }));
healer.addNonUninvokableEffect(P);
CMLib.commands().postSay(M, healer, "mend gear", false, false);
check("held", CM.core.Services.held(healer, M).size() == 1, "nothing held");
// a culture, taken up by command
CM.core.Cultures.adopt(M, "RIVERFOLK", "command");
check("culture", s(CM.core.Cultures.current(M).id) == "RIVERFOLK" && M.fetchAbility("Fishing") != null, "no culture");
// a follower with a role, fallen back
var guard = CMClass.getMOB("GenMob"); guard.setName("a fixp2 role guard"); guard.basePhyStats().setLevel(3); guard.recoverPhyStats();
guard.bringToLife(M.location(), true); guard.setFollowing(M); M.addFollower(guard, 0);
CM.core.FormationRoles.assign(guard, "GUARD");
M.addFollower(guard, 2); guard.addTattoo("ROLEBACK");
check("role", s(CM.core.FormationRoles.of(guard).id) == "GUARD" && M.fetchFollowerOrder(guard) == 2, "no role");
// talents, decided by lore
CM.core.LoreBook.unlock(M, "FIXP2_1", false, null);
check("talents", s(CM.core.Talents.decided(M, "TIDES")) == "awakened" && s(CM.core.Talents.decided(M, "DREAMS")) == "dormant" && M.fetchAbility("Skill_Swim") != null, s(CM.core.Talents.list(M)));
// a personal record
CM.core.Challenges.record(M, "FIXP2", "crossing", 30, true);
CM.core.Challenges.record(M, "FIXP2", "crossing", 20, true);
check("record", s(CM.core.Challenges.show(M, "FIXP2")).indexOf("crossing: best 20") >= 0, s(CM.core.Challenges.show(M, "FIXP2")));
done();
