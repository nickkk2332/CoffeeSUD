// A14 named reservations: Prop_Reserve holds part of a reservable pool while it is on its host (an aura on the
// character, a minion's upkeep from its leader); what it holds cannot be spent; one that no longer fits is suspended
// (its effects stop) or, with ONSUSPEND=LEAVE, goes (a minion is dismissed); taking it off frees the pool at once;
// RESOURCES names each. Nothing is stored in the pool, so a reservation cannot outlive its holder.
FIXFILE = "a14-reserve";
var NR = CM.core.NamedResources, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-a14-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
var TM = Tickable.TICKID_MOB, STR = CM.Common.interfaces.CharStats.STAT_STRENGTH;
function s(x) { return "" + x; }
function read(file) { return s(new JString(Files.readAllBytes(file.toPath()), "UTF-8")); }
function write(file, obj) { Files.write(file.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function costs(map) { var m = new java.util.TreeMap(); for (var k in map) m.put(k, java.lang.Long.valueOf(map[k])); return m; }
var room = scratchRoom(), actors = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); actors.push(m); return m; }
function reserve(host, text) { var P = CMClass.getAbility("Prop_Reserve"); P.setMiscText(text); host.addNonUninvokableEffect(P); P.tick(host, TM); return P; }
try {
	var example = new java.io.File("../docs/creator/examples");
	var mods = new java.io.File(folder, "modules"); mods.mkdirs();
	var pools = new java.io.File(folder, "pools"); pools.mkdirs();
	write(new java.io.File(mods, "abilities.resources.json"), JSON.parse(read(new java.io.File(example, "named-resources/modules/abilities.resources.json"))));
	write(new java.io.File(pools, "fix.spirit.v1.json"), { schema: 1, id: "fix.spirit", version: 1, title: "Spirit", max: 100, start: 100, reservable: true });
	var plan = MD.load(folder, "abilities.resources", "");
	check("manifest-valid", plan.errors.isEmpty(), s(plan.problems()));
	currentField.set(null, OM.prepare(plan, null, false)); WorldFiles.configure(folder); NR.reset();

	var sage = actor("Reserve Sage"); NR.grant(sage, "fix.spirit");
	var str0 = sage.charStats().getStat(STR);
	// an aura: 30 held while it is on, and its effects work
	var aura = reserve(sage, 'POOL=fix.spirit AMOUNT=30 NAME="Aura of embers" EFFECTS="Prop_Adjuster;STR+2"');
	sage.recoverCharStats();
	check("aura-holds", aura.fits() && NR.held(sage, "fix.spirit").available() == 70 && sage.charStats().getStat(STR) == str0 + 2,
		"fits " + aura.fits() + " available " + NR.held(sage, "fix.spirit").available() + " str " + sage.charStats().getStat(STR) + "/" + str0);
	// a minion's upkeep, from the one it follows
	var wolf = actor("a spirit wolf"); wolf.setFollowing(sage);
	var upkeep = reserve(wolf, 'POOL=fix.spirit AMOUNT=50 OF=LEADER ONSUSPEND=LEAVE');
	check("minion-holds", upkeep.fits() && NR.held(sage, "fix.spirit").available() == 20, "available " + NR.held(sage, "fix.spirit").available());
	// what is held cannot be spent
	var why = ""; try { NR.spend(sage, costs({ "fix.spirit": 25 }), "test"); } catch (e) { why = s(e); }
	check("held-not-spent", why.indexOf("you have 20") >= 0 && NR.value(sage, "fix.spirit") == 100, why);
	// one that does not fit is suspended: its effects stop; RESOURCES says so
	var ward = reserve(sage, 'POOL=fix.spirit AMOUNT=40 NAME="Stone ward" EFFECTS="Prop_Adjuster;DEX+3"');
	var count = 0; for (var e = 0; e < sage.numEffects(); e++) if (s(sage.fetchEffect(e).ID()) == "Prop_Reserve") count++;
	check("stacks", count == 2, "a second reservation was not taken: " + count);
	var desc = s(NR.describe(sage));
	check("suspended", !ward.fits() && upkeep.fits() && desc.indexOf("reserved: Aura of embers 30, a spirit wolf 50") >= 0
		&& desc.indexOf("suspended (not enough): Stone ward 40") >= 0, desc);
	// the pool drained below what is held: they fit in the order they were made, so the newest go first; a minion set to leave goes
	NR.set(sage, "fix.spirit", 60);
	upkeep.tick(wolf, TM); ward.tick(sage, TM);
	check("capacity-drops", aura.fits() && !ward.fits() && wolf.amDestroyed(), "aura " + aura.fits() + " ward " + ward.fits() + " wolf gone " + wolf.amDestroyed());
	// the place in line is kept in the text, as a save keeps it
	var copy = CMClass.getAbility("Prop_Reserve"); copy.setMiscText(aura.text());
	check("since-kept", aura.reservedSince() > 0 && copy.reservedSince() == aura.reservedSince() && s(copy.reservationSpec()).indexOf("SINCE") < 0, s(aura.text()));
	// taking the aura off frees its share at once, and the ward fits again
	sage.delEffect(aura); ward.tick(sage, TM); sage.recoverCharStats();
	check("released", ward.fits() && NR.held(sage, "fix.spirit").available() == 20 && sage.charStats().getStat(STR) == str0,
		"ward " + ward.fits() + " available " + NR.held(sage, "fix.spirit").available() + " str " + sage.charStats().getStat(STR));
	// several on one character; each is a lasting effect, saved with its host (its text is all there is)
	var mine = 0; for (var e = 0; e < sage.numEffects(); e++) if (s(sage.fetchEffect(e).ID()) == "Prop_Reserve") mine++;
	check("saved", mine == 1 && ward.isSavable() && !ward.canBeUninvoked() && s(ward.text()).indexOf("Stone ward") >= 0, "reserves " + mine + " savable " + ward.isSavable());
	// a pool that cannot be reserved: never fits
	write(new java.io.File(pools, "fix.rage.v1.json"), { schema: 1, id: "fix.rage", version: 1, title: "Rage", max: 100, start: 100 });
	NR.reset(); NR.grant(sage, "fix.rage");
	var bad = reserve(sage, 'POOL=fix.rage AMOUNT=10');
	check("not-reservable", !bad.fits() && NR.held(sage, "fix.rage").available() == 100, "fits " + bad.fits());
	// the module off: nothing fits, and the effects stop
	currentField.set(null, OM.prepare(MD.load(folder, "", ""), s(OM.snapshot()), true));
	ward.tick(sage, TM);
	check("module-off", !ward.fits(), "a reservation fitted with the module off");
} finally {
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy(); room.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); NR.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
