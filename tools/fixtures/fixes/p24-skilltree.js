// P24: SKILLTREE: a class's skills by REQUIRES chain, each learned, learnable, locked (level, prerequisite, mask) or hidden, with hints (skills/skillhints.ini, SHOW=HINT).
FIXFILE = "p24-skilltree";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
function clean(t) { return s(t).replace(/\^./g, "").replace(/\s+/g, " "); }
var ST = CM.core.SkillTree;
var AM = CMLib.ableMapper();
var SF = CM.Libraries.interfaces.AbilityMapper.SecretFlag;
var R = scratchRoom();
var HF = new CM.core.CMFile(CM.core.Resources.buildResourcePath("skills") + "skillhints.ini", null, CM.core.CMFile.FLAG_FORCEALLOW);
var hadHints = HF.exists() ? s(HF.text()) : null;
var made = [];
try {
	HF.saveText("# hints\nSpell_DetectInvisible=HINT=\"seek the Druid circle in the Fernwood\"\nFixP24Ranger.Spell_Infravision=HINT=\"for elves alone\" SHOW=HINT\n", false);
	var GK = CMClass.getCharClass("GenCharClass").copyOf(); GK.setStat("ID", "FixP24Ranger"); GK.setStat("NAME", "Fixranger");
	AM.addCharAbilityMapping("FixP24Ranger", 1, "Spell_Light", false);
	AM.addCharAbilityMapping("FixP24Ranger", 2, "Spell_Shield", false, list("Spell_Light(50)"));
	AM.addCharAbilityMapping("FixP24Ranger", 30, "Spell_Blur", false);
	AM.addCharAbilityMapping("FixP24Ranger", 1, "Spell_Infravision", false, "-RACE +Elf");
	var sec = AM.addCharAbilityMapping("FixP24Ranger", 1, "Spell_DetectInvisible", false); sec.secretFlag(SF.SECRET);
	var St = CMClass.getMOB("GenMob"); St.setName("Fixp24 ranger"); St.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	St.baseCharStats().setMyRace(CMClass.getRace("Human")); St.baseCharStats().setCurrentClass(GK); St.baseCharStats().setClassLevel(GK, 10);
	St.basePhyStats().setLevel(10); St.setTrains(5); St.setPractices(5); St.recoverCharStats(); St.recoverPhyStats(); St.bringToLife(R, true); made.push(St);
	var L = CMClass.getAbility("Spell_Light"); L.setProficiency(40); St.addAbility(L);
	check("hint", s(ST.hint("FixP24Ranger", "Spell_DetectInvisible")) == "seek the Druid circle in the Fernwood" && ST.showHint("FixP24Ranger", "Spell_Infravision") && !ST.showHint("Other", "Spell_Infravision"), s(ST.hint("FixP24Ranger", "Spell_DetectInvisible")));
	var t = clean(ST.tree(St, GK));
	var name = function (id) { return s(CMClass.getAbility(id).name()); };
	check("learned", has(t, name("Spell_Light") + " (learned, 40%)"), t);
	check("prerequisite", has(t, "> " + name("Spell_Shield") + " (locked: needs"), t);
	check("level", has(t, name("Spell_Blur") + " (locked: level 30)"), t);
	check("mask", has(t, name("Spell_Infravision") + " (locked: for elves alone)"), t);
	check("hidden", has(t, "??? - seek the Druid circle") && !has(t, name("Spell_DetectInvisible")), t);
	St.fetchAbility("Spell_Light").setProficiency(60);
	t = clean(ST.tree(St, GK));
	check("learnable", has(t, name("Spell_Shield") + " (learnable"), t);
	var c = clean(ST.chain(St, CMClass.getAbility("Spell_Shield")));
	check("chain", has(c, name("Spell_Light") + " (learned, 60%)") && has(c, "> " + name("Spell_Shield")), c);
	check("command", CMClass.getCommand("SkillTreeCmd") != null && has(CMLib.help().getHelpText("SKILLTREE", M, false), "SKILLTREE"), "no command or help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	AM.delCharMappings("FixP24Ranger");
	if (hadHints == null) HF.delete(); else HF.saveText(hadHints, false);
}
done();
