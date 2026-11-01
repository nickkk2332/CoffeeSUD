// S8, in play: a quest made in play, as RandomQuests makes one: not in the
// world's files, a copy, running, with a herald and a variable
FIXFILE = "s8-play";
var script = "set name s8gen\nset duration 1000\nimport mobs _fixes/s1mobs.cmare\nset room any\nload mob any\nstep break\n";
var Q = CMClass.getCommon("DefaultQuest");
check("script", Q.setScript(script, true), "the script did not take");
CMLib.quests().addQuest(Q);
check("started", Q.startQuest() || Q.running(), "the quest did not start");
Q.setCopy(true);
Q.setQuestVar("PROGRESS", "4");
var herald = Q.getQuestMob(1);
check("herald", herald != null, "no quest mob");
if (herald != null) M.location().bringMobHere(herald, false);
done();
