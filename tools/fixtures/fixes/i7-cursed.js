// I7: Prop_Cursed LEVEL HIDDEN NOREMOVE NODROP PENALTY REVEAL BREAK BREAKMASK ONBREAK; Remove Curse (Prayer_Bless's
// endLowerCurses) lifts it only at LEVEL or more, penalties and all.
FIXFILE = "i7-cursed";
var W = CM.Items.interfaces.Wearable;
var PB = CM.Abilities.Prayers.Prayer_Bless;
var CS = CM.Common.interfaces.CharStats;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi7pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function ring(name, curse) {
	var I = CMClass.getItem("GenItem"); I.setName(name); I.setRawLogicalAnd(false); I.setRawProperLocationBitmap(W.WORN_LEFT_FINGER); V.addItem(I);
	var P = CMClass.getAbility("Prop_Cursed"); P.setMiscText(curse); I.addNonUninvokableEffect(P);
	return { I: I, P: P };
}
function wear(I) { out(); var m = CMClass.getMsg(V, I, null, CMMsg.MSG_WEAR, "<S-NAME> put(s) on <T-NAME>."); R.send(V, m); I.wearAt(W.WORN_LEFT_FINGER); V.recoverCharStats(); return out(); }
function priest(level) { var p = CMClass.getMOB("GenMob"); p.setName("Fixi7priest"); p.basePhyStats().setLevel(level); p.recoverPhyStats(); p.bringToLife(R, true); return p; }
function removeCurse(p, I) {
	var RC = CMClass.getAbility("Prayer_RemoveCurse");
	R.send(p, CMClass.getMsg(p, I, RC, CMMsg.MSG_CAST_VERBAL_SPELL, null));
	PB.endLowerCurses(I, p.phyStats().level());
	I.recoverPhyStats(); V.recoverCharStats();
}
var wis0 = V.charStats().getStat(CS.STAT_WISDOM);
var A = ring("a fixi7 knight's ring", "LEVEL=15 HIDDEN NOREMOVE PENALTY=\"Prop_WearAdjuster(wis-3)\" REVEAL=\"The ring tightens on your finger!\"");
var before = CMLib.flags().isRemovable(A.I) && A.I.fetchEffect("Prop_WearAdjuster") == null;
var said = wear(A.I);
var wis1 = V.charStats().getStat(CS.STAT_WISDOM);
check("reveal", before && (!CMLib.flags().isRemovable(A.I)) && has(said, "The ring tightens on your finger!") && wis1 == wis0 - 3 && has(A.P.text(), "SHOWN=true"),
	"before " + before + " removable " + CMLib.flags().isRemovable(A.I) + " wis " + wis0 + "->" + wis1 + " '" + said + "'");
check("domain", CM.Abilities.Prayers.Prayer_Bless.isCursed(A.I), "Remove Curse would not see it");
var weak = priest(5);
removeCurse(weak, A.I);
var held = A.I.fetchEffect("Prop_Cursed") != null && (!CMLib.flags().isRemovable(A.I));
var strong = priest(20);
removeCurse(strong, A.I);
check("lift", held && A.I.fetchEffect("Prop_Cursed") == null && CMLib.flags().isRemovable(A.I) && A.I.fetchEffect("Prop_WearAdjuster") == null
	&& V.charStats().getStat(CS.STAT_WISDOM) == wis0, "held " + held + " cursed " + (A.I.fetchEffect("Prop_Cursed") != null) + " wis " + V.charStats().getStat(CS.STAT_WISDOM));
// a deed that breaks it, and the ring crumbles
var B = ring("a fixi7 bone ring", "NODROP BREAK=\"SAY:i confess\" ONBREAK=CRUMBLE");
var nodrop = !CMLib.flags().isDroppable(B.I);
CMLib.commands().postSay(V, null, "I confess my sins", false, false);
check("break", nodrop && B.I.amDestroyed(), "nodrop " + nodrop + " gone " + B.I.amDestroyed());
check("help", has(CMLib.help().getHelpText("PROP_CURSED", M, true), "ONBREAK"), "no help");

V.setPlayerStats(null); V.destroy(); weak.destroy(); strong.destroy();
done();
