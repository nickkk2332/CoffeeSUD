// CR2: world/materials.txt: new resources after the built-ins (a fixed CODE kept, the rest sorted by name), a built-in changed by name, descriptions and effects on the raw resource; MTRALN in item XML repairs a moved number.
FIXFILE = "cr2-materials";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var RM = CM.Items.interfaces.RawMaterial, C = RM.CODES;
var MF = new java.io.File(WorldFiles.worldDir(), "materials.txt");
var things = [];
function rows(lines) { java.nio.file.Files.write(MF.toPath(), new java.lang.String(lines.join("\n") + "\n").getBytes("UTF-8")); C.reset(); }
try {
	var B = C.BUILTINS(), oak0 = C.VALUE(RM.RESOURCE_OAK), total0 = C.TOTAL();
	check("before", B == 210 && C.FIND_IgnoreCase("STARSTEEL") < 0 && total0 >= B, B + " / " + total0);
	rows(["# fixcr2 materials", "NAME\tFAMILY\tVALUE\tFREQUENCY\tHARDNESS\tBUOYANCY\tCATEGORY\tSMELL\tEFFECTS\tDESCRIPTION\tCODE",
		"starsteel\tMETAL\t300\t1\t6\t9000\t\t\tProp_HaveResister(fire 5)\tA sliver of metal that never quite cools.",
		"glowcap\tVEGETATION\t12\t3\t1\t500\tBERRY\tearthy\tSpell_Light\tA pale mushroom that glows faintly.",
		"fixcrzinc\tMETAL\t7\t5\t3\t7000\t\t\t\tA dull grey metal.\t211",
		"oak\tWOODEN\t9\t10\t3\t720\t\trich oaky\t\tA heavy hardwood.",
		"fixcrbad\tNOSUCHFAMILY\t1"]);
	var star = C.FIND_IgnoreCase("starsteel"), glow = C.FIND_IgnoreCase("GLOWCAP"), zinc = C.FIND_IgnoreCase("fixcrzinc");
	check("codes", (glow & 255) == 210 && (zinc & 255) == 211 && (star & 255) == 212 && C.FIND_IgnoreCase("fixcrbad") < 0
		&& (star & RM.MATERIAL_MASK) == RM.MATERIAL_METAL && (glow & RM.MATERIAL_MASK) == RM.MATERIAL_VEGETATION, [glow & 255, zinc & 255, star & 255].join());
	var berries = java.util.Arrays.toString(C.BERRIES());
	check("stats", C.VALUE(star) == 300 && C.HARDNESS(star) == 6 && C.FREQUENCY(star) == 1 && s(C.SMELL(glow)) == "earthy" && has(berries, "" + glow)
		&& C.VALUE(RM.RESOURCE_OAK) == 9 && oak0 != 9 && s(C.NAME(RM.RESOURCE_OAK)) == "OAK", C.VALUE(star) + " " + C.HARDNESS(star) + " " + C.SMELL(glow) + " oak " + C.VALUE(RM.RESOURCE_OAK));
	var S1 = CMLib.materials().makeItemResource(star), G1 = CMLib.materials().makeItemResource(glow), O1 = CMLib.materials().makeItemResource(RM.RESOURCE_OAK);
	things.push(S1, G1, O1);
	check("resource", s(S1.description()) == "A sliver of metal that never quite cools." && S1.fetchEffect("Prop_HaveResister") != null && G1.fetchEffect("Spell_Light") != null
		&& s(O1.description()) == "A heavy hardwood." && has(S1.name(), "starsteel") && S1.baseGoldValue() == 300, S1.name() + " / " + S1.description());
	var sword = CMClass.getItem("GenWeapon"); sword.setName("a starsteel dagger"); sword.setMaterial(star); things.push(sword);
	// the XML keeps a world material's name; a built-in's is not written
	var X1 = s(sword.text());
	var iron = CMClass.getItem("GenItem"); iron.setMaterial(RM.RESOURCE_IRON); things.push(iron);
	check("xml", has(X1, "<MTRALN>STARSTEEL</MTRALN>") && !has(s(iron.text()), "MTRALN"), X1.substring(0, 200));
	// a renumbering: the file gains a material that sorts first, so starsteel moves; the item, by name, follows
	rows(["aardsilver\tMETAL\t50\t2\t4\t8000", "starsteel\tMETAL\t300\t1\t6\t9000", "glowcap\tVEGETATION\t12\t3\t1\t500\tBERRY", "fixcrzinc\tMETAL\t7\t5\t3\t7000\t\t\t\t\t211"]);
	var star2 = C.FIND_IgnoreCase("starsteel");
	var copy = CMClass.getItem("GenWeapon"); copy.setMiscText(X1); copy.recoverPhyStats(); things.push(copy);
	var X2 = X1.replace("<MTRALN>STARSTEEL</MTRALN>", "");
	var plain = CMClass.getItem("GenWeapon"); plain.setMiscText(X2); things.push(plain);
	check("renumbered", star2 != star && copy.material() == star2 && plain.material() == star, star + " -> " + star2 + " / " + copy.material() + " / " + plain.material());
	// a gap before a CODE is filled with an unused resource
	rows(["fixcrgap\tROCK\t1\t1\t1\t1\t\t\t\t\t215"]);
	check("gap", (C.FIND_IgnoreCase("fixcrgap") & 255) == 215 && C.FIND_IgnoreCase("UNUSED 212") >= 0 && C.FREQUENCY(C.FIND_IgnoreCase("UNUSED 212")) == 0, "" + (C.FIND_IgnoreCase("fixcrgap") & 255));
	check("help", has(CMLib.help().getHelpText("WORLDMATERIALS", M, true), "MTRALN"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	MF["delete"]();
	C.reset();
	check("after", C.FIND_IgnoreCase("STARSTEEL") < 0 && C.VALUE(RM.RESOURCE_OAK) != 9 && C.TOTAL() == total0, "" + C.TOTAL());
}
done();
