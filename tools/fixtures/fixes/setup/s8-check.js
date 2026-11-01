// S8, after the restore: the quest made in play is back, running, with its
// variable and its herald where it was
FIXFILE = "s8-check";
var Q = CMLib.quests().fetchQuest("s8gen");
check("back", Q != null, "the quest made in play is gone");
check("running", (Q != null) && Q.running() && Q.isCopy(), "not running as a copy");
check("variable", (Q != null) && Q.getStat("PROGRESS") == "4", "PROGRESS is " + ((Q == null) ? "" : Q.getStat("PROGRESS")));
var herald = (Q == null) ? null : Q.getQuestMob(1);
check("herald-here", (herald != null) && (herald.location() == M.location()), "the herald is " + ((herald == null) ? "gone" : "in " + herald.location()));
var count = 0;
var R = M.location();
for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == "a herald") count++;
check("one-herald", count == 1, count + " heralds");
done();
