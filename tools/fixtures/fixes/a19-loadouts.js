// A19 loadouts: off unless LOADOUTS= is above 0; LOADOUT SAVE keeps what is worn (each item and where) and the
// character's own reservations; LOADOUT [name] swaps all of it or none (a cursed item, a reservation that does not
// fit: nothing changes); LOADOUT SHOW says what would change; a swap costs LOADOUTCOST actions per change; the
// loadouts are in a property's text, so the save keeps them.
FIXFILE = "a19-loadouts";
var W = CM.Items.interfaces.Wearable, P = CMProps.instance();
var keys = ["LOADOUTS", "LOADOUTCOST"], before = keys.map(function (k) { return P.getStr(k, ""); });
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var R = scratchRoom(), made = [];
var H = newMob("GenMob", R); H.setName("Fxa19hero"); made.push(H);
H.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); H.setSession(VS); VS.setMob(H);
var seen = 0;
function said() { var all = s(field(VS, "bout").toString()); var o = all.substring(seen); seen = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var CMD = CMClass.getCommand("LoadoutCmd");
function lo() { said(); CMD.execute(H, list.apply(null, ["LOADOUT"].concat(Array.prototype.slice.call(arguments))), 0); return said(); }
function item(name, cls, where) { var I = CMClass.getItem(cls || "GenItem"); I.setName(name); I.setRawProperLocationBitmap(where); H.addItem(I); return I; }
try {
	P.setProperty("LOADOUTS", "0");
	check("off", has(lo(), "There are no loadouts in this world."), "LOADOUT worked with LOADOUTS=0");
	P.setProperty("LOADOUTS", "2"); P.setProperty("LOADOUTCOST", "0.5");
	var sword = item("a fxa19 sword", "GenWeapon", W.WORN_WIELD), bow = item("a fxa19 bow", "GenWeapon", W.WORN_WIELD);
	var helm = item("a fxa19 helm", "GenArmor", W.WORN_HEAD), hood = item("a fxa19 hood", "GenArmor", W.WORN_HEAD);
	sword.wearAt(W.WORN_WIELD); helm.wearAt(W.WORN_HEAD);
	check("saved", has(lo("SAVE", "melee"), "Loadout 'melee' kept: 2 items worn, 0 reservations"), "melee not kept");
	sword.unWear(); helm.unWear(); bow.wearAt(W.WORN_WIELD); hood.wearAt(W.WORN_HEAD);
	lo("SAVE", "ranged");
	check("limit", has(lo("SAVE", "third"), "You can keep 2 loadouts"), "a third loadout was kept");
	// SHOW changes nothing; the cost is per change
	var show = lo("SHOW", "melee");
	check("show", has(show, "take off a fxa19 bow") && has(show, "put on a fxa19 sword (wield)") && has(show, "4 changes, 2.0 actions") && bow.amWearingAt(W.WORN_WIELD), show);
	check("cost", CMD.actionsCost(H, list("LOADOUT", "melee")) == 2.0 && CMD.actionsCost(H, list("LOADOUT", "SHOW", "melee")) == 0, "cost " + CMD.actionsCost(H, list("LOADOUT", "melee")));
	// the swap
	check("swap", has(lo("melee"), "You change to loadout 'melee' (4 changes)") && sword.amWearingAt(W.WORN_WIELD) && helm.amWearingAt(W.WORN_HEAD)
		&& bow.amWearingAt(W.IN_INVENTORY) && hood.amWearingAt(W.IN_INVENTORY), "sword " + sword.rawWornCode() + " helm " + helm.rawWornCode());
	check("in-use", has(lo(), "melee: in use") && has(lo(), "ranged: 4 changes"), lo());
	check("wielded", H.fetchWieldedItem() == sword, "the engine does not see the sword wielded: " + H.fetchWieldedItem());
	// all or nothing: a helm that cannot be taken off stops the swap, and nothing changes
	var curse = CMClass.getAbility("Prop_Cursed"); curse.setMiscText("NOREMOVE"); helm.addNonUninvokableEffect(curse); helm.recoverPhyStats();
	var refusal = lo("ranged");
	check("all-or-nothing", has(refusal, "you cannot take off a fxa19 helm") && has(refusal, "Nothing was changed")
		&& sword.amWearingAt(W.WORN_WIELD) && helm.amWearingAt(W.WORN_HEAD) && bow.amWearingAt(W.IN_INVENTORY),
		refusal + " sword " + sword.rawWornCode() + " bow " + bow.rawWornCode());
	helm.delEffect(curse); helm.recoverPhyStats();
	// an item no longer carried
	H.delItem(hood); R.addItem(hood);
	check("missing", has(lo("ranged"), "a fxa19 hood is not with you") && sword.amWearingAt(W.WORN_WIELD), "swap went on without the hood");
	R.delItem(hood); H.addItem(hood);
	// kept in a property's text, so in the save
	var store = H.fetchEffect("Prop_Loadouts");
	check("saved-text", has(store.text(), "ranged") && store.isSavable() && !store.canBeUninvoked(), "the loadouts are not kept as a lasting effect");
	check("forget", has(lo("FORGET", "ranged"), "forgotten") && !has(lo(), "ranged"), "not forgotten");
} finally {
	keys.forEach(function (k, i) { P.setProperty(k, before[i]); });
	VS.setMob(null); H.setSession(null);
	made.forEach(function (m) { if (m.playerStats() != null) m.setPlayerStats(null); if (!m.amDestroyed()) m.destroy(); });
	R.destroy();
}
done();
