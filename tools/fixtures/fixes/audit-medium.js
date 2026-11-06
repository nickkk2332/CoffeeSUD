// The documentation audit's next defects (audit/docs-audit-2026-11.md): D-PR-1,
// D-QU-2, D-CF-2, D-BM-1 to D-BM-5, D-AB-1, D-AB-2, D-AB-6.  (D-CF-1, the
// kills log key, is read once at boot and is not tested here.)
FIXFILE = "audit-medium";
var R = M.location();
function s(x) { return "" + x; }
var made = [];
function amob(name) { var m = newMob("GenMob", R); m.setName(name); m.setSavable(false); made.push(m); return m; }

try {
	// D-PR-1: the ABLE* adjustments of the adjuster family are read
	var holder = amob("fixaudit adjusted");
	var gem = CMClass.getItem("GenItem"); gem.setName("a fixaudit gem");
	var adj = CMClass.getAbility("Prop_HaveAdjuster");
	adj.setMiscText("ABLETICKS=Spell_Fly(+5) ABLEHPCOSTS=Spell_Fly(-2) ABLEMNCOSTS=Spell_Fly(3) ABLEMVCOSTS=Spell_Fly(4) ABLEPROFS=Spell_Fly(6)");
	gem.addNonUninvokableEffect(adj);
	holder.addItem(gem);
	holder.recoverCharStats();
	var cs = holder.charStats();
	check("pr1-able", cs.getAbilityAdjustment("TICK+SPELL_FLY") == 5 && cs.getAbilityAdjustment("HPCOST+SPELL_FLY") == -2
		&& cs.getAbilityAdjustment("MNCOST+SPELL_FLY") == 3 && cs.getAbilityAdjustment("MVCOST+SPELL_FLY") == 4,
		"tick " + cs.getAbilityAdjustment("TICK+SPELL_FLY") + " hp " + cs.getAbilityAdjustment("HPCOST+SPELL_FLY")
		+ " mana " + cs.getAbilityAdjustment("MNCOST+SPELL_FLY") + " move " + cs.getAbilityAdjustment("MVCOST+SPELL_FLY"));
	check("pr1-profs", cs.getAbilityAdjustment("PROF+SPELL_FLY") == 6, "prof " + cs.getAbilityAdjustment("PROF+SPELL_FLY"));

	// D-QU-2: a RandomQuests with no PATH uses the shipped generator, which has all_quests
	var RQ = CMClass.getBehavior("RandomQuests");
	var path = s(RQ.getGeneratorXmlPath());
	var xml = CM.core.Resources.getFileResource(path, false);
	check("qu2-path", xml != null && s(xml).indexOf('ID="all_quests"') >= 0, "default PATH " + path + " has no all_quests");

	// D-CF-2: PARTYMEET and PARTYFIGHT take the same yes words as WINDOWS
	var MUD = CM.application.MUD;
	check("cf2-yes", MUD.yesWord("1") && MUD.yesWord("true") && MUD.yesWord(" Yes ") && MUD.yesWord("on"), "a yes word is no");
	check("cf2-no", !MUD.yesWord("0") && !MUD.yesWord("false") && !MUD.yesWord("") && !MUD.yesWord(null) && !MUD.yesWord("off"), "a no word is yes");

	// D-BM-1: RandomTeleporter POOFOUT sets the leave message
	var RT = CMClass.getBehavior("RandomTeleporter");
	RT.setParms("POOFIN=arrives POOFOUT=leaves");
	check("bm1-poof", s(field(RT, "poofin")) == "arrives" && s(field(RT, "poofout")) == "leaves",
		"poofin " + field(RT, "poofin") + " poofout " + field(RT, "poofout"));

	// D-BM-2: GateGuard takes KEEPLOCKED and ALLNIGHT together
	var GG = CMClass.getBehavior("GateGuard");
	GG.setParms("KEEPLOCKED ALLNIGHT");
	check("bm2-both", field(GG, "keepLocked") == true && field(GG, "allnight") == true,
		"keeplocked " + field(GG, "keepLocked") + " allnight " + field(GG, "allnight"));

	// D-BM-3: INDOOROK=true lets a concierge or taxi go indoors; NOINDOOR=true does not
	var OUT = CM.Libraries.interfaces.TrackingLibrary.TrackingFlag.OUTDOORONLY;
	var CC = CMClass.getBehavior("Concierge");
	CC.setParms("INDOOROK=true");
	check("bm3-concierge", !field(CC, "trackingFlags").contains(OUT), "INDOOROK=true keeps a concierge outdoors");
	var CN = CMClass.getBehavior("Concierge");
	CN.setParms("NOINDOOR=true");
	check("bm3-noindoor", field(CN, "trackingFlags").contains(OUT), "NOINDOOR=true lets a concierge indoors");
	var TX = CMClass.getBehavior("TaxiBehavior");
	TX.setParms("");
	var taxiDefault = field(TX, "trackingFlags").contains(OUT);
	TX.setParms("INDOOROK=true");
	check("bm3-taxi", taxiDefault && !field(TX, "trackingFlags").contains(OUT), "taxi outdoors by default " + taxiDefault + ", after INDOOROK=true "
		+ field(TX, "trackingFlags").contains(OUT));

	// D-BM-4: CombatAbilities BSIZE takes effect when the behavior is first set
	var CA = CMClass.getBehavior("CombatAbilities");
	CA.setParms("BSIZE=3");
	check("bm4-bsize", field(CA, "bucketSize") == 3, "bucket size " + field(CA, "bucketSize"));

	// D-BM-5: RandomTraps reads ROOMS= and ITEMS= without a ';'
	var RTR = CMClass.getBehavior("RandomTraps");
	RTR.setParms('min=10 max=20 chance=25 ROOMS="YES" ITEMS="NO"');
	check("bm5-rooms", field(RTR, "doRooms") == true && field(RTR, "doAnyItems") == false, "rooms " + field(RTR, "doRooms"));

	// D-AB-1: a GenPoison's EFFECTS are put on the victim
	var venom = CMClass.getAbility("GenPoison").copyOf();
	venom.setStat("CLASS", "FixAuditVenom");
	venom.setStat("EFFECTS", "Prop_Hidden");
	var victim = amob("fixaudit victim");
	venom.setAffectedOne(victim);
	var effs = call(venom, "getOtherEffects", []);
	check("ab1-effects", effs.size() == 1 && s(effs.get(0).ID()) == "Prop_Hidden", "effects " + effs);

	// D-AB-2: a GenTrap with ISBOMB is a bomb
	var bomb = CMClass.getAbility("GenTrap").copyOf();
	bomb.setStat("CLASS", "FixAuditBomb");
	bomb.setStat("ISBOMB", "true");
	var dud = CMClass.getAbility("GenTrap").copyOf();
	dud.setStat("CLASS", "FixAuditDud");
	check("ab2-bomb", bomb.isABomb() && !dud.isABomb(), "bomb " + bomb.isABomb() + " dud " + dud.isABomb());

	// D-AB-6: AFFECTS <domain> lists the effects of that domain only
	var flyer = amob("fixaudit flyer");
	var A1 = CMClass.getAbility("Spell_Fly"), A2 = null;
	var dom1 = A1.classificationCode() & CM.Abilities.interfaces.Ability.ALL_DOMAINS;
	var others = ["Spell_Shield", "Spell_Light", "Spell_DetectMagic", "Spell_Haste", "Spell_Infravision", "Prayer_Bless"];
	for (var i = 0; i < others.length && A2 == null; i++) {
		var c = CMClass.getAbility(others[i]);
		if (c != null && (c.classificationCode() & CM.Abilities.interfaces.Ability.ALL_DOMAINS) != dom1 && s(c.displayText()).length > 0) A2 = c;
	}
	A1.startTickDown(flyer, flyer, 50);
	A2.startTickDown(flyer, flyer, 50);
	var AF = CMClass.getCommand("Affect");
	var FS = CMClass.getCommon("FakeSession"); FS.initialize(null, "MEMORY");
	var listed = s(AF.getAffects(FS, flyer, true, true, null, CM.Commands.Affect.DOMAIN_FILTER | dom1));
	var t1 = s(flyer.fetchEffect(A1.ID()).displayText()), t2 = s(flyer.fetchEffect(A2.ID()).displayText());
	check("ab6-domain", listed.indexOf(t1) >= 0 && listed.indexOf(t2) < 0, "domain list [" + listed + "] for " + t1 + " / " + t2);

	// D-GE-8: with JSCRIPTS=1 a template's JavaScript waits for approval; with 2 it runs
	var GenJs = CM.core.GenJs, was = CMProps.getIntVar(CMProps.Int.JSCRIPTS), held, ran;
	try {
		CMProps.setIntVar(CMProps.Int.JSCRIPTS, 1);
		held = GenJs.eval("40+2+0*" + (new Date().getTime() % 997), null, new java.util.HashMap(), null, "fixaudit template");
		CMProps.setIntVar(CMProps.Int.JSCRIPTS, 2);
		ran = GenJs.eval("40+2", null, new java.util.HashMap(), null, "fixaudit template");
	} finally { CMProps.setIntVar(CMProps.Int.JSCRIPTS, was); }
	check("ge8-approval", held == null && s(ran) == "42", "JSCRIPTS=1 gave " + held + ", JSCRIPTS=2 gave " + ran);

	// D-CF-4: the shipped clock has a week, and month names of one word
	var C = CMLib.time().globalClock();
	var months = C.getMonthNames(), oneWord = true;
	for (var i = 0; i < months.length; i++) if (s(months[i]).indexOf(" ") >= 0) oneWord = false;
	check("cf4-week", C.getDaysInWeek() == 7 && s(CM.core.ScriptEvents.weekday(C)).length > 0, "days in week " + C.getDaysInWeek());
	check("cf4-months", months.length == 7 && oneWord && s(months[1]) == "Thawmonth", "months " + java.util.Arrays.toString(months));
} finally {
	for (var i = 0; i < made.length; i++) made[i].destroy();
}
done();
