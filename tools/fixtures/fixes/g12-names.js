// G12: name styles as data (world/namegen, resources/namegen): syllables, prefixes and suffixes, given names by gender, family names and titles; NAMESTYLE on GenRace and GenLanguage, RACES= and LANGUAGES=; SYSTEM_RANDOM_NAME:min-max:style[:FULL]; RANDNAME().
FIXFILE = "g12-names";
var NS = CM.core.NameStyles;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var D = new java.io.File(WorldFiles.worldDir(), "namegen");
D.mkdirs();
var A = new java.io.File(D, "fixg12.ini"), B = new java.io.File(D, "fixg12b.ini");
var T = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg12.xml");
T.getParentFile().mkdirs();
function write(F, t) { java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
try {
	// the stock styles
	var names = s(NS.names());
	check("stock", has(names, "default") && has(names, "elvish") && has(names, "dwarvish") && has(names, "goblin")
		&& s(NS.styleFor(CMClass.getRace("Dwarf"))) == "dwarvish" && s(NS.styleFor(CMClass.getRace("Elf"))) == "elvish"
		&& s(NS.styleFor(CMClass.getRace("Orc"))) == "goblin" && s(NS.resolve("Dwarven")) == "dwarvish"
		&& s(NS.resolve("Human")) == "default" && NS.resolve("fixg12nothing") == null, names);
	var d = s(CMLib.login().generateRandomName(3, 6)), e = s(NS.make("elvish", 0, 0, 'M', true));
	check("made", /^[A-Z]/.test(d) && d.indexOf(" ") < 0 && e.indexOf(" ") > 0, d + " / " + e);
	// a world's own: syllables, suffix, family, title, races and languages
	write(A, "# test\nSTART=CONSONANT\nSYLLABLES=4-4\nVOWELS=a7\nCONSONANTS=z7\nSUFFIXES=qx\nSUFFIXPCT=100\nFAMILY=Stone\nTITLES=the Bold\nTITLEPCT=100\nRACES=Gnome\nLANGUAGES=Gnomish\n");
	write(B, "START=CONSONANT\nSYLLABLES=2-2\nVOWELS=o7\nCONSONANTS=b7\nMALE=Bert\nGIVENPCT=100\n");
	check("style", s(NS.make("fixg12", 0, 0, '?', false)) == "Zazaqx" && s(NS.make("fixg12", 2, 2, '?', true)) == "Zaqx Stone the Bold"
		&& s(NS.make("fixg12b", 0, 0, 'M', false)) == "Bert" && s(NS.make("fixg12b", 0, 0, 'F', false)) == "Bo"
		&& s(NS.make("fixg12b", 0, 0, '?', false)) == "Bert",
		NS.make("fixg12", 0, 0, '?', false) + " / " + NS.make("fixg12", 2, 2, '?', true) + " / " + NS.make("fixg12b", 0, 0, 'F', false));
	check("races", s(NS.styleFor(CMClass.getRace("Gnome"))) == "fixg12" && s(NS.styleFor(CMClass.getAbility("Gnomish"))) == "fixg12"
		&& s(NS.resolve("gnome")) == "fixg12", NS.styleFor(CMClass.getRace("Gnome")) + " " + NS.styleFor(CMClass.getAbility("Gnomish")));
	// a changed file is read again
	write(A, "START=CONSONANT\nSYLLABLES=2-2\nVOWELS=i7\nCONSONANTS=k7\n");
	A.setLastModified(A.lastModified() + 5000);
	check("changed", s(NS.make("fixg12", 0, 0, '?', false)) == "Ki" && NS.styleFor(CMClass.getRace("Gnome")) == null, NS.make("fixg12", 0, 0, '?', false));
	// NAMESTYLE on a GenRace and a GenLanguage, saved with them
	var R = CMClass.getRace("GenRace").copyOf();
	R.setStat("NAMESTYLE", "fixg12b");
	var R2 = CMClass.getRace("GenRace").copyOf();
	R2.setRacialParms(R.racialParms());
	var L = CMClass.getAbility("GenLanguage").copyOf();
	L.setStat("CLASS9", "FixG12Lang");
	L.setStat("NAMESTYLE", "fixg12b");
	check("namestyle", s(NS.styleFor(R)) == "fixg12b" && has(R.racialParms(), "<NAMESTYLE>fixg12b</NAMESTYLE>") && s(R2.getStat("NAMESTYLE")) == "fixg12b"
		&& s(NS.styleFor(L)) == "fixg12b" && has(L.getStat("ALLXML"), "<NAMESTYLE>fixg12b</NAMESTYLE>"),
		NS.styleFor(R) + " " + R2.getStat("NAMESTYLE") + " " + NS.styleFor(L));
	// templates: a style, FULL, a race, a bad style, NAMESTYLE defined
	write(A, "START=CONSONANT\nSYLLABLES=4-4\nVOWELS=a7\nCONSONANTS=z7\nFAMILY=Stone\nTITLEPCT=0\n");
	A.setLastModified(A.lastModified() + 10000);
	write(T, '<AREADATA><STRING id="fixg12_a">$SYSTEM_RANDOM_NAME:3-3:fixg12</STRING><STRING id="fixg12_b">${SYSTEM_RANDOM_NAME:4-4:fixg12:FULL}</STRING>'
		+ '<STRING id="fixg12_c">${SYSTEM_RANDOM_NAME:2-2:fixg12b}</STRING><STRING id="fixg12_d">${SYSTEM_RANDOM_NAME:2-2:fixg12nothing}</STRING>'
		+ '<STRING id="fixg12_e" define="NAMESTYLE">fixg12</STRING><STRING id="fixg12_f">${SYSTEM_RANDOM_NAME:2-2} $fixg12_e</STRING><STRING id="fixg12_g">${SYSTEM_RANDOM_NAME:2-2}</STRING></AREADATA>');
	// (a string's variables are filled from the last, so the define comes after the name)
	var GC = CM.core.GenCalls, F = "randareas/fixg12.xml", bad = "", VM = new java.util.HashMap();
	VM.put("NAMESTYLE", "fixg12");
	try { GC.string(F, "fixg12_d", null, null, M.location()); } catch (x) { bad = s(x); }
	check("template", s(GC.string(F, "fixg12_a", null, null, M.location())) == "Zaz" && s(GC.string(F, "fixg12_b", null, null, M.location())) == "Zaza Stone"
		&& s(GC.string(F, "fixg12_c", null, null, M.location())) == "Bert" && has(bad.toLowerCase(), "unknown name style 'fixg12nothing'") && s(GC.string(F, "fixg12_f", null, null, M.location())) == "Za fixg12"
		&& s(GC.string(F, "fixg12_g", VM, null, M.location())) == "Za" && s(GC.string(F, "fixg12_g", null, null, M.location())) != "Za",
		GC.string(F, "fixg12_a", null, null, M.location()) + " / " + GC.string(F, "fixg12_b", null, null, M.location()) + " / " + GC.string(F, "fixg12_c", null, null, M.location()) + " / " + bad + " / " + GC.string(F, "fixg12_f", null, null, M.location()) + " / " + GC.string(F, "fixg12_g", VM, null, M.location()));
	// RANDNAME() in a script
	["G12A", "G12B", "G12C", "G12D", "G12E"].forEach(clearVar);
	var m = newMob("GenMob", M.location()); m.setName("Fixg12namer");
	scriptOn(m, "RAND_PROG 100\n"
		+ "mpsetvar FIXTEST G12A $%RANDNAME(fixg12 3 3)%\nmpsetvar FIXTEST G12B $%RANDNAME(fixg12b M)%\n"
		+ "mpsetvar FIXTEST G12C $%RANDNAME(fixg12 4 4 FULL)%\nmpsetvar FIXTEST G12D $%RANDNAME(Dwarf)%\nmpsetvar FIXTEST G12E $%RANDNAME()%\n~\n");
	tickMob(m);
	check("randname", gvar("G12A") == "Zaz" && gvar("G12B") == "Bert" && gvar("G12C") == "Zaza Stone" && /^[A-Z][a-z']+$/.test(gvar("G12D")) && /^[A-Z][a-z']+$/.test(gvar("G12E")),
		gvar("G12A") + " / " + gvar("G12B") + " / " + gvar("G12C") + " / " + gvar("G12D") + " / " + gvar("G12E"));
	m.destroy();
	check("help", has(CMLib.help().getHelpText("NAMESTYLES", M, true), "FAMILYSTART") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "RANDNAME("), "no help");
} finally {
	[A, B, T].forEach(function (F) { if (F.exists()) F["delete"](); });
	NS.reload();
}
done();
