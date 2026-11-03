// CR12: CRAFTXP (a percent of a craft's XP value) and GATHERXP (XP a unit gathered) for every player character, and Prop_CraftXP (PCT, GATHER, SKILLS, MASK) on the character, an item, the room or the area.
FIXFILE = "cr12-craftxp";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
var P = CMProps.instance(), MsgC = CM.Common.interfaces.CMMsg;
var X = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr12smith");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var smith = CMClass.getAbility("Blacksmithing"), cook = CMClass.getAbility("Cooking"), mine = CMClass.getAbility("Mining"), fight = CMClass.getAbility("Skill_Dirt");
var things = [];
function ingot() { var I = CMClass.getItem("GenItem"); I.setName("a fixcr12 ingot"); things.push(I); return I; }
// a craft of value v, or a find of n units, as the skills send them
function made(A, v) { var m = CMClass.getMsg(V, ingot(), A, MsgC.TYP_ITEMGENERATED | MsgC.MASK_ALWAYS, null); m.setValue(v); var x0 = V.getExperience(); V.location().send(V, m); return V.getExperience() - x0; }
function found(A, n) { var m = CMClass.getMsg(V, ingot(), A, MsgC.TYP_ITEMSGENERATED | MsgC.MASK_ALWAYS, null); m.setValue(n); var x0 = V.getExperience(); V.location().send(V, m); return V.getExperience() - x0; }
var props = [];
function prop(where, text) { var A = CMClass.getAbility("Prop_CraftXP"); A.setMiscText(text); where.addNonUninvokableEffect(A); props.push([where, A]); return A; }
function unprop() { props.forEach(function (p) { p[0].delEffect(p[1]); }); props = []; }
try {
	check("off", made(smith, 200) == 0 && found(mine, 5) == 0 && s(P.getStr("CRAFTXP", "")) == "", "");
	P.setProperty("CRAFTXP", "25"); P.setProperty("GATHERXP", "2");
	var r1 = [made(smith, 200), made(cook, 40), found(mine, 5), made(fight, 200), found(smith, 5)];
	check("options", r1.join() == "50,10,10,0,0", r1.join());
	// a monster earns none
	var ps = V.playerStats(); V.setPlayerStats(null);
	var r2; try { r2 = made(smith, 200); } finally { V.setPlayerStats(ps); }
	check("players only", r2 == 0, "" + r2);
	P.remove("CRAFTXP"); P.remove("GATHERXP");
	// on the character, an item carried, the room, the area
	prop(V, "PCT=50 SKILLS=Blacksmithing");
	var r3 = [made(smith, 200), made(cook, 200)];
	unprop();
	var charm = CMClass.getItem("GenItem"); charm.setName("a fixcr12 charm"); V.addItem(charm); things.push(charm);
	prop(charm, "GATHER=3");
	var r4 = found(mine, 4);
	unprop();
	prop(X, "PCT=10 GATHER=1");
	prop(X.getArea(), "PCT=20 MASK=\"-LEVEL +>5\"");
	var r5 = [made(smith, 100), found(mine, 2)];
	unprop();
	prop(X.getArea(), "PCT=20 MASK=\"-LEVEL +>50\"");
	var r6 = made(smith, 100);
	unprop();
	check("prop", r3.join() == "100,0" && r4 == 12 && r5.join() == "30,2" && r6 == 0, r3.join() + " / " + r4 + " / " + r5.join() + " / " + r6);
	// with CRAFTXP, a real craft's own value
	P.setProperty("CRAFTXP", "100");
	var A = CMClass.getAbility("Carpentry"); A.setProficiency(100); V.addAbility(A); A = V.fetchAbility("Carpentry");
	var chest = CMClass.getItem("GenItem"); chest.setName("a fixcr12 chest"); chest.basePhyStats().setLevel(10); chest.recoverPhyStats(); things.push(chest);
	setField(A, "buildingI", chest);
	setField(A, "lastBaseDuration", java.lang.Integer.valueOf(20));
	var x0 = V.getExperience();
	try { call(A, "dropAWinner", [V, chest]); } finally { P.remove("CRAFTXP"); }
	check("a craft", V.getExperience() == x0 + 80, "xp " + x0 + " -> " + V.getExperience());
	check("help", has(CMLib.help().getHelpText("PROP_CRAFTXP", M, true), "GATHERXP"), "no help");
} finally {
	unprop();
	P.remove("CRAFTXP"); P.remove("GATHERXP");
	things.forEach(function (I) { I.destroy(); });
	V.setPlayerStats(null); V.destroy();
}
done();
