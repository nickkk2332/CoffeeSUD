// P22: a TWEAK's CASTMASK, TARGETMASK, POSTCASTAFFECT, POSTCASTABILITY, SCRIPT (CANCAST, ONCAST, ONEXPIRE), DURATIONMULT and DAMAGEMULT, on its record and its proxy.
FIXFILE = "p22-tweak";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var CS = CM.Common.interfaces.CharStats;
var AB = CM.core.CMClass.CMObjectType.ABILITY;
var TR = CM.core.TweakRules;
var R = scratchRoom();
var made = [], recs = [];
function npc(name, race) {
	var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setLevel(20);
	m.baseCharStats().setMyRace(CMClass.getRace(race));
	m.baseCharStats().setStat(CS.STAT_INTELLIGENCE, 25); m.baseCharStats().setStat(CS.STAT_WISDOM, 25);
	m.recoverCharStats(); m.recoverPhyStats(); m.baseState().setMana(1000); m.recoverMaxState(); m.resetToMaxState();
	m.bringToLife(R, true); made.push(m); return m;
}
function ticks(A) { return Math.round(A.expirationDate() / CM.core.CMProps.getTickMillis()); }
["P22CAN", "P22CAST", "P22EXP"].forEach(clearVar);
try {
	var W = CMClass.getAbility("GenTweakAbility").copyOf(); recs.push(W);
	W.setStat("CLASS", "FixP22Glow"); W.setStat("ORIGINAL", "Spell_Light");
	W.setStat("CASTMASK", "-RACE +Elf"); W.setStat("TARGETMASK", "-RACE +Elf +Human");
	W.setStat("DURATIONMULT", "2"); W.setStat("POSTCASTAFFECT", "Spell_DetectInvisible");
	W.setStat("SCRIPT", "FUNCTION_PROG CANCAST\nif hastattoo($n FIXP22DARK)\n return false\nendif\nmpsetvar FIXTEST P22CAN yes\n~\n"
		+ "FUNCTION_PROG ONCAST\nmpsetvar FIXTEST P22CAST $t\n~\nFUNCTION_PROG ONEXPIRE\nmpsetvar FIXTEST P22EXP yes\n~\n");
	var W2 = CMClass.getAbility("GenTweakAbility").copyOf(); W2.setStat("ALLXML", W.getStat("ALLXML"));
	check("xml", s(W2.getStat("CASTMASK")) == "-RACE +Elf" && s(W2.getStat("DURATIONMULT")) == "2" && has(W2.getStat("SCRIPT"), "ONEXPIRE") && s(W2.getStat("DAMAGEMULT")) == "1", s(W2.getStat("CASTMASK")));
	var human = npc("Fixp22 human", "Human"), elf = npc("Fixp22 elf", "Elf"), dwarf = npc("Fixp22 dwarf", "Dwarf");
	var A = CMClass.getAbility("FixP22Glow"); A.setProficiency(100);
	// CASTMASK: only an elf casts it
	check("castmask", !A.invoke(human, human, false, 20) && human.fetchEffect("Spell_Light") == null, "the human cast it");
	// it casts its original, under its rules; ONCAST
	var cast1 = A.invoke(elf, elf, true, 20);
	var L = elf.fetchEffect("Spell_Light");
	check("delegated", cast1 && L != null && gvar("P22CAN") == "yes" && has(gvar("P22CAST"), "elf"), cast1 + " " + s(L) + " " + gvar("P22CAST"));
	// DURATIONMULT, against a plain cast
	var plain = npc("Fixp22 plain", "Human");
	CMClass.getAbility("Spell_Light").invoke(plain, plain, true, 20);
	var PL = plain.fetchEffect("Spell_Light");
	check("durationmult", L != null && PL != null && Math.abs(ticks(L) - 2 * ticks(PL)) <= 2, (L == null ? "-" : ticks(L)) + " vs " + (PL == null ? "-" : ticks(PL)));
	check("postcastaffect", elf.fetchEffect("Spell_DetectInvisible") != null, "no Detect Invisible");
	// TARGETMASK: not on a dwarf
	check("targetmask", !A.invoke(elf, dwarf, true, 20) && dwarf.fetchEffect("Spell_Light") == null, "the dwarf was lit");
	// CANCAST refuses
	elf.addTattoo("FIXP22DARK"); clearVar("P22CAST");
	var elf2 = npc("Fixp22 other elf", "Elf");
	check("cancast", !A.invoke(elf, elf2, true, 20) && elf2.fetchEffect("Spell_Light") == null && gvar("P22CAST") == "", gvar("P22CAST"));
	elf.delTattoo(elf.findTattoo("FIXP22DARK"));
	// ONEXPIRE, once the light is gone
	if (L != null) L.unInvoke();
	tickMob(elf);
	check("onexpire", gvar("P22EXP") == "yes" && elf.fetchEffect("Prop_TweakWatch") == null, gvar("P22EXP"));
	// POSTCASTABILITY, and the proxy over the original takes the same rules
	W.setStat("POSTCASTAFFECT", ""); W.setStat("POSTCASTABILITY", "100%;Spell_Shield");
	var mk = W.getClass().getMethod("createProxy", java.lang.String, java.lang.String);
	var PX = mk.invoke(null, "Spell_Light", "FixP22Glow"); PX.setProficiency(100);
	check("proxy castmask", !PX.invoke(human, human, false, 20), "the human cast the proxy");
	var elf3 = npc("Fixp22 third elf", "Elf");
	check("proxy cast", PX.invoke(elf3, elf3, true, 20) && elf3.fetchEffect("Spell_Light") != null && elf3.fetchEffect("Spell_Shield") != null, s(elf3.fetchEffect("Spell_Shield")));
	// DAMAGEMULT: a damage message with the casting original, or an effect a cast left, as its tool
	var MM = CMClass.getAbility("Spell_MagicMissile");
	var msg = CMClass.getMsg(elf, human, MM, CMMsg.MSG_DAMAGE, null); msg.setValue(10);
	TR.begin(MM, 2.5); TR.scaleDamage(msg); TR.end();
	var msg2 = CMClass.getMsg(elf, human, MM, CMMsg.MSG_DAMAGE, null); msg2.setValue(10); TR.scaleDamage(msg2);
	var E = CMClass.getAbility("Spell_Light"); TR.mark(E, 0.5);
	var msg3 = CMClass.getMsg(elf, human, E, CMMsg.MSG_DAMAGE, null); msg3.setValue(10); TR.scaleDamage(msg3);
	check("damagemult", msg.value() == 25 && msg2.value() == 10 && msg3.value() == 5, msg.value() + " " + msg2.value() + " " + msg3.value());
	check("help", has(CMLib.help().getHelpText("GENTWEAK", M, true), "CASTMASK"), "no help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	recs.forEach(function (a) { CMClass.delClass(AB, a); });
}
done();
