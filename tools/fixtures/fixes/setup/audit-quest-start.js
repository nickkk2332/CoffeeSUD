// D-SP-11 (documentation audit), before the save: the tutorial's quest takes
// over the gate guard, a mob of the world (SET MOB RESELECT), and is running
FIXFILE = "audit-quest-start";
var Q = CMLib.quests().fetchQuest("harls_hammer");
check("quest-loaded", Q != null, "the quest is not loaded");
if (Q != null && !Q.running()) Q.startQuest();
check("quest-running", Q != null && Q.running(), "the quest did not start");
var guard = (Q != null) ? Q.getQuestMob(1) : null;
check("guard-taken", guard != null && ("" + guard.Name()) == "the gate guard"
	&& CMLib.map().getExtendedRoomID(guard.location()) == "Brindlewick#2", "the quest has no gate guard: " + guard);
done();
