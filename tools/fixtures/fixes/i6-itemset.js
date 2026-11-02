// I6: Prop_ItemSet SET NAME COUNT TIERn PIECES ONMSG/OFFMSG MASK, tiers on one piece or in world/itemsets.txt, LOOK,
// SETCOUNT() and SET_PROG; ALLSET with mixed thresholds on Prop_WearAdjuster.
FIXFILE = "i6-itemset";
var W = CM.Items.interfaces.Wearable;
var CS = CM.Common.interfaces.CharStats;
var WF = CM.core.save.WorldFiles;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi6pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var slots = [W.WORN_HEAD, W.WORN_TORSO, W.WORN_ARMS, W.WORN_LEGS];
function piece(name, slot, parms) {
	var I = CMClass.getItem("GenItem"); I.setName(name); I.setRawLogicalAnd(false); I.setRawProperLocationBitmap(slot); V.addItem(I);
	var P = CMClass.getAbility("Prop_ItemSet"); P.setMiscText(parms); I.addNonUninvokableEffect(P);
	return { I: I, P: P, slot: slot };
}
var bones = [];
bones.push(piece("a fixi6 bone helm", slots[0], "SET=fixi6_bone NAME=\"Dragonbone Regalia\" PIECES=4 TIER2=\"Prop_WearAdjuster(con+2)\" TIER4=\"Prop_WearEnabler(Skill_Dodge)\" ONMSG=\"The bones hum ($t).\""));
bones.push(piece("a fixi6 bone mail", slots[1], "SET=fixi6_bone"));
bones.push(piece("a fixi6 bone bracers", slots[2], "SET=fixi6_bone"));
bones.push(piece("a fixi6 bone greaves", slots[3], "SET=fixi6_bone"));
scriptOn(bones[0].I, "SET_PROG fixi6_bone\nmpsetvar FIXTEST FIXI6S $0 $1\n~\n");
clearVar("FIXI6S");
function settle() { bones.forEach(function (b) { b.P.tick(b.P, TPS); }); V.recoverCharStats(); V.recoverPhyStats(); }
var con0 = V.charStats().getStat(CS.STAT_CONSTITUTION);
out();
bones[0].I.wearAt(slots[0]); settle();
var one = V.charStats().getStat(CS.STAT_CONSTITUTION);
bones[1].I.wearAt(slots[1]); settle();
var said = out();
var two = V.charStats().getStat(CS.STAT_CONSTITUTION);
check("tier2", one == con0 && two == con0 + 2 && has(said, "The bones hum (2).") && gvar("FIXI6S") == "2 ON", "con " + con0 + "/" + one + "/" + two + " '" + said + "' prog '" + gvar("FIXI6S") + "'");
bones[2].I.wearAt(slots[2]); bones[3].I.wearAt(slots[3]); settle();
var dodge = V.fetchAbility("Skill_Dodge") != null;
bones[3].I.unWear(); settle();
check("tier4", dodge && V.fetchAbility("Skill_Dodge") == null && V.charStats().getStat(CS.STAT_CONSTITUTION) == con0 + 2 && gvar("FIXI6S") == "4 OFF", "dodge " + dodge + " after " + (V.fetchAbility("Skill_Dodge") != null) + " prog '" + gvar("FIXI6S") + "'");
out(); R.send(V, CMClass.getMsg(V, bones[1].I, null, CMMsg.MSG_LOOK, null)); said = out();
check("look", has(said, "Set: Dragonbone Regalia, 3/4 worn.") && has(said, "2 pieces: active") && has(said, "4 pieces: inactive"), "'" + said + "'");
var fn = scriptOn(R, "FUNCTION_PROG C\nmpsetvar FIXTEST FIXI6C $%SETCOUNT($n fixi6_bone)%\n~\n");
fn.callFunction(R, "C", "", V);
check("setcount", gvar("FIXI6C") == "3", "'" + gvar("FIXI6C") + "'");
// tiers from world/itemsets.txt
var F = new java.io.File(WF.worldDir(), "itemsets.txt");
var before = F.isFile() ? new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8") : null;
java.nio.file.Files.write(F.toPath(), new java.lang.String("SET=fixi6_file NAME=\"Filed Set\" TIER1=\"Prop_WearAdjuster(wis+1)\"\n").getBytes("UTF-8"));
F.setLastModified(java.lang.System.currentTimeMillis() + 7000);
var ring = piece("a fixi6 filed ring", W.WORN_LEFT_FINGER, "SET=fixi6_file");
var wis0 = V.charStats().getStat(CS.STAT_WISDOM);
ring.I.wearAt(W.WORN_LEFT_FINGER); ring.P.tick(ring.P, TPS); V.recoverCharStats();
check("file", V.charStats().getStat(CS.STAT_WISDOM) == wis0 + 1, "wis " + wis0 + " -> " + V.charStats().getStat(CS.STAT_WISDOM));
if (before == null) F["delete()"](); else java.nio.file.Files.write(F.toPath(), before.getBytes("UTF-8"));
// ALLSET with mixed thresholds
function adj(name, slot, text) {
	var I = CMClass.getItem("GenItem"); I.setName(name); I.setRawLogicalAnd(false); I.setRawProperLocationBitmap(slot); V.addItem(I);
	var A = CMClass.getAbility("Prop_WearAdjuster"); A.setMiscText(text); I.addNonUninvokableEffect(A); I.wearAt(slot); return I;
}
bones.forEach(function (b) { b.I.unWear(); }); settle();
var str0 = V.charStats().getStat(CS.STAT_STRENGTH), dex0 = V.charStats().getStat(CS.STAT_DEXTERITY);
adj("a fixi6 mix glove", W.WORN_HANDS, "str+1 ALLSET=fixi6_mix-2");
adj("a fixi6 mix boot", W.WORN_FEET, "dex+1 ALLSET=fixi6_mix-3");
V.recoverCharStats();
check("allset", V.charStats().getStat(CS.STAT_STRENGTH) == str0 + 1 && V.charStats().getStat(CS.STAT_DEXTERITY) == dex0,
	"str " + str0 + "->" + V.charStats().getStat(CS.STAT_STRENGTH) + " dex " + dex0 + "->" + V.charStats().getStat(CS.STAT_DEXTERITY));
check("help", has(CMLib.help().getHelpText("PROP_ITEMSET", M, true), "TIER2") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "SET_PROG"), "no help");

V.setPlayerStats(null); V.destroy();
done();
