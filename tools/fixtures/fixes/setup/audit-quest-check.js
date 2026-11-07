// D-SP-11 (documentation audit), after the restore: the quest is running again
// and has the gate guard, who is back at the gate (the save leaves a quest's
// mobs out of their rooms, and the restore used to clear the guard away first)
FIXFILE = "audit-quest-check";
var Q = CMLib.quests().fetchQuest("harls_hammer");
check("quest-running", Q != null && Q.running(), "the quest did not start again after the restore");
var gate = CMLib.map().getRoom("Brindlewick#2");
var there = 0;
for (var i = 0; gate != null && i < gate.numInhabitants(); i++)
	if (("" + gate.fetchInhabitant(i).Name()) == "the gate guard") there++;
check("guard-back", there == 1, there + " gate guards at the gate");
var guard = (Q != null) ? Q.getQuestMob(1) : null;
check("guard-quest-mob", guard != null && guard.location() == gate, "the quest's mob is " + guard);
done();
