// I24: Prop_ItemGrowth GAIN=KILL|HIT|USE|WORNTICK, XP, KILLMASK, STAGES (NAME, PROPS), MSG, BIND=CHARACTER; kept in the text.
FIXFILE = "i24-growth";
var W = CM.Items.interfaces.Wearable;
var CS = CM.Common.interfaces.CharStats;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi24pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function foe(name, level) { var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setLevel(level); m.recoverPhyStats(); m.bringToLife(R, true); return m; }
function slay(killer, name, level) { var m = foe(name, level); killer.setVictim(m); CMLib.combat().postDeath(killer, m, null); killer.makePeace(true); }
var sword = CMClass.getWeapon("GenWeapon"); sword.setName("a rusty fixi24 sword"); V.addItem(sword); sword.wearAt(W.WORN_WIELD);
var P = CMClass.getAbility("Prop_ItemGrowth");
P.setMiscText("GAIN=KILL XP=\"@x1\" KILLMASK=\"-NAMES +Fixi24ghoul\" STAGES=\"5:NAME='a humming fixi24 blade' PROPS=Prop_WearAdjuster(str+1);12:NAME='Fixi24 Dawnbringer'\" "
	+ "MSG=\"$p shudders as it grows stronger!\" BIND=CHARACTER");
sword.addNonUninvokableEffect(P);
var str0 = V.charStats().getStat(CS.STAT_STRENGTH);
slay(V, "Fixi24rat", 3);
var rat = P.grown();
out();
slay(V, "Fixi24ghoul", 3); slay(V, "Fixi24ghoul", 3);
var said = out();
V.recoverCharStats();
check("stage", rat == 0 && P.grown() == 6 && P.stage() == 1 && s(sword.Name()) == "a humming fixi24 blade" && has(said, "shudders as it grows stronger")
	&& V.charStats().getStat(CS.STAT_STRENGTH) == str0 + 1, "rat " + rat + " grown " + P.grown() + " stage " + P.stage() + " name " + sword.Name() + " '" + said + "'");
check("kept", has(P.text(), "GROWN=6") && has(P.text(), "STAGE=1") && has(P.text(), "OWNER=\"Fixi24pip\""), s(P.text()));
var copy = CMClass.getAbility("Prop_ItemGrowth"); copy.setMiscText(P.text());
check("restore", copy.grown() == 6 && copy.stage() == 1 && has(copy.accountForYourself(), "stage 1, 6 of 12"), s(copy.accountForYourself()));
// bound: not for another
var thief = CMClass.getMOB("GenMob"); thief.setName("Fixi24thief"); thief.bringToLife(R, true);
thief.moveItemTo(sword); sword.wearAt(W.WORN_WIELD);
slay(thief, "Fixi24ghoul", 6);
check("bind", P.grown() == 6, "grew for another: " + P.grown());
// worn ticks
var amu = CMClass.getItem("GenItem"); amu.setName("a cold fixi24 amulet"); amu.setRawLogicalAnd(false); amu.setRawProperLocationBitmap(W.WORN_NECK); V.addItem(amu);
var PA = CMClass.getAbility("Prop_ItemGrowth"); PA.setMiscText("GAIN=WORNTICK STAGES=\"2:NAME='a warm fixi24 amulet'\""); amu.addNonUninvokableEffect(PA);
PA.tick(PA, TPS); PA.tick(PA, TPS);
var carried = PA.grown();
amu.wearAt(W.WORN_NECK); PA.tick(PA, TPS); PA.tick(PA, TPS);
check("worntick", carried == 0 && s(amu.Name()) == "a warm fixi24 amulet", "carried " + carried + " name " + amu.Name());
check("help", has(CMLib.help().getHelpText("PROP_ITEMGROWTH", M, true), "WORNTICK"), "no help");

V.setPlayerStats(null); V.destroy(); thief.destroy();
for (var i = R.numInhabitants() - 1; i >= 0; i--) R.fetchInhabitant(i).destroy();
done();
