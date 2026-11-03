// P16: teachers gated by STUDENTMASK (and REFUSEMSG), SKILLMASK.<ID> and PRICE, HIDDEN, TEACHLIST, and TRAIN skipping one who refuses.
FIXFILE = "p16-teach";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var TR = CM.core.TeacherRules;
var MA = CM.MOBS.interfaces.MOB.Attrib;
var CS = CM.Common.interfaces.CharStats;
var R = scratchRoom();
var made = [];
var name = function (id) { return s(CMClass.getAbility(id).name()); };
var PARMS = 'Prayer_Bless Prayer_Sanctuary Prayer_CureLight Skill_Recall STUDENTMASK="-RACE +Human" REFUSEMSG="I teach only the sons and daughters of men." '
	+ 'SKILLMASK.Prayer_Sanctuary="-CLASS +Fighter" PRICE="5 per level" HIDDEN=Skill_Recall';
try {
	check("parsed", s(TR.withoutRules(PARMS)).replace(/\s+/g, " ") == "Prayer_Bless Prayer_Sanctuary Prayer_CureLight Skill_Recall"
		&& s(TR.rules(PARMS).get("REFUSEMSG")) == "I teach only the sons and daughters of men." && s(TR.rules(PARMS).get("SKILLMASK.PRAYER_SANCTUARY")) == "-CLASS +Fighter",
		s(TR.withoutRules(PARMS)) + " " + TR.rules(PARMS));
	// the teacher, first in the room
	var T = CMClass.getMOB("GenMob"); T.setName("Fixaldous"); T.basePhyStats().setLevel(30); T.recoverPhyStats(); T.bringToLife(R, true); made.push(T);
	var B = CMClass.getBehavior("MOBTeacher"); B.setParms(PARMS); T.addBehavior(B);
	T.setAttribute(MA.NOTEACH, false);
	check("teacher knows", T.fetchAbility("Prayer_Bless") != null && T.fetchAbility("Prayer_Sanctuary") != null && T.fetchAbility("Prayer_CureLight") != null, "missing skills");
	// a trainer who teaches anyone, second
	var G = CMClass.getMOB("GenMob"); G.setName("Fixgorm"); G.basePhyStats().setLevel(30); G.recoverPhyStats(); G.bringToLife(R, true); made.push(G);
	var PT = CMClass.getAbility("Prop_Trainer"); PT.setMiscText("STR=25 INT=25 DEX=25 CON=25 CHA=25 WIS=25"); G.addNonUninvokableEffect(PT);
	G.setAttribute(MA.NOTEACH, false);
	// the student: an elf cleric, with a session of their own
	var St = CMClass.getMOB("GenMob"); St.setName("Fixpupil"); St.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	St.baseCharStats().setMyRace(CMClass.getRace("Elf"));
	St.baseCharStats().setCurrentClass(CMClass.getCharClass("Cleric")); St.baseCharStats().setClassLevel(CMClass.getCharClass("Cleric"), 30);
	St.basePhyStats().setLevel(30);
	St.baseCharStats().setStat(CS.STAT_INTELLIGENCE, 18); St.baseCharStats().setStat(CS.STAT_WISDOM, 18);
	St.recoverCharStats(); St.recoverPhyStats();
	St.bringToLife(R, true); made.push(St);
	St.setAttribute(MA.NOTEACH, false);
	St.setPractices(20); St.setTrains(20);
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); St.setSession(VS); VS.setMob(St);
	var seen = 0;
	function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
	function say(text) { CMClass.getCommand("Say").execute(St, list("SAY", text), 0); }
	// STUDENTMASK
	said();
	check("mask", s(TR.refusal(T, St, null)) == "I teach only the sons and daughters of men.", s(TR.refusal(T, St, null)));
	say("teach me " + name("Prayer_Bless"));
	var h = said();
	check("refused", has(h, "I teach only the sons and daughters of men.") && St.fetchAbility("Prayer_Bless") == null, h);
	// TRAIN skips him, and goes to the trainer who will
	var hp0 = St.baseState().getHitPoints(), tr0 = St.getTrains();
	CMClass.getCommand("Train").execute(St, list("TRAIN", "HIT", "NOPROMPT"), 0);
	h = said();
	check("train skips", St.baseState().getHitPoints() > hp0 && St.getTrains() < tr0 && !has(h, "sons and daughters"), hp0 + " -> " + St.baseState().getHitPoints() + " " + h);
	// SKILLMASK
	St.baseCharStats().setMyRace(CMClass.getRace("Human")); St.recoverCharStats();
	check("skillmask", TR.refusal(T, St, "Prayer_Bless") == null && has(TR.refusal(T, St, "Prayer_Sanctuary"), name("Prayer_Sanctuary")), s(TR.refusal(T, St, "Prayer_Sanctuary")));
	say("teach me " + name("Prayer_Sanctuary"));
	h = said();
	check("skill refused", St.fetchAbility("Prayer_Sanctuary") == null && has(h, "I will not teach you"), h);
	// PRICE: 5 per level, so 150 at level 30
	check("price", Math.abs(TR.price(T, St) - 150) < 0.01, TR.price(T, St));
	CMLib.beanCounter().giveSomeoneMoney(St, 100);
	say("teach me " + name("Prayer_Bless"));
	h = said();
	check("cannot pay", St.fetchAbility("Prayer_Bless") == null && has(h, "My lessons cost"), h);
	CMLib.beanCounter().giveSomeoneMoney(St, 200);
	var m0 = CMLib.beanCounter().getTotalAbsoluteNativeValue(St);
	say("teach me " + name("Prayer_Bless"));
	h = said();
	var m1 = CMLib.beanCounter().getTotalAbsoluteNativeValue(St);
	check("taught and paid", St.fetchAbility("Prayer_Bless") != null && Math.abs((m0 - m1) - 150) < 0.01, m0 + " -> " + m1 + " " + h);
	// TEACHLIST: what is left for them, the price, what is not for them, and nothing HIDDEN
	CMClass.getCommand("TeachListCmd").execute(St, list("TEACHLIST", "Fixaldous"), 0);
	h = said();
	check("teachlist", has(h, name("Prayer_CureLight")) && has(h, s(CMLib.beanCounter().nameCurrencyShort(St, 150))) && has(h, "Not for you") && has(h, name("Prayer_Sanctuary"))
		&& !has(h, name("Skill_Recall")) && !has(h, "can teach you: " + name("Prayer_Bless")), h);
	// PRACTICE: refused as an elf, then paid as a man
	var prof0 = St.fetchAbility("Prayer_Bless").proficiency();
	St.baseCharStats().setMyRace(CMClass.getRace("Elf")); St.recoverCharStats();
	CMClass.getCommand("Practice").execute(St, list("PRACTICE", name("Prayer_Bless"), "Fixaldous"), 0);
	h = said();
	check("practice refused", St.fetchAbility("Prayer_Bless").proficiency() == prof0 && has(h, "sons and daughters"), h);
	St.baseCharStats().setMyRace(CMClass.getRace("Human")); St.recoverCharStats();
	CMLib.beanCounter().giveSomeoneMoney(St, 200);
	m0 = CMLib.beanCounter().getTotalAbsoluteNativeValue(St);
	CMClass.getCommand("Practice").execute(St, list("PRACTICE", name("Prayer_Bless"), "Fixaldous"), 0);
	h = said();
	m1 = CMLib.beanCounter().getTotalAbsoluteNativeValue(St);
	check("practiced", St.fetchAbility("Prayer_Bless").proficiency() > prof0 && Math.abs((m0 - m1) - 150) < 0.01, prof0 + " -> " + St.fetchAbility("Prayer_Bless").proficiency() + " " + m0 + "/" + m1 + " " + h);
	check("help", has(CMLib.help().getHelpText("TEACHLIST", M, false), "TEACHLIST") && has(CMLib.help().getHelpText("BEHAVIOR_MOBTEACHER", M, true), "STUDENTMASK")
		&& has(CMLib.help().getHelpText("PROP_TRAINER", M, true), "STUDENTMASK"), "no help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
}
done();
