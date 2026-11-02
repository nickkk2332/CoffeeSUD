// I4: Prop_When PROPS (abilities and behaviours) or ID/PARMS, WHEN, CHECK, EVERY, CHANCE, COOLDOWN, ONCE, ONSTART,
// ONEND, ELSEMSG.
FIXFILE = "i4-when";
var Dir = CM.core.Directions;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom(), OUT = scratchRoom();
var V = newMob("GenMob", OUT); V.setName("Fixi4pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function enter(Rm) { out(); var m = CMClass.getMsg(V, Rm, null, CMMsg.MSG_ENTER, null); Rm.bringMobHere(V, false); Rm.send(V, m); return out(); }
var P = CMClass.getAbility("Prop_When");
P.setMiscText("PROPS=\"Prop_Verbs(VERBS=howl MSG=\\\"You howl at the moon.\\\");Emoter(chance=0 howls)\" CHECK=ROOMPLAYERS COOLDOWN=2 "
	+ "ONSTART=\"setvar FIXTEST FIXI4S on\" ONEND=\"setvar FIXTEST FIXI4E off\" ELSEMSG=\"The wood is quiet.\"");
R.addNonUninvokableEffect(P);
["FIXI4S", "FIXI4E"].forEach(clearVar);
P.tick(P, TPS);
var off = (!P.isOn()) && R.fetchEffect("Prop_Verbs") == null;
var said = enter(R);
P.tick(P, TPS);
check("on", off && P.isOn() && R.fetchEffect("Prop_Verbs") != null && R.fetchBehavior("Emoter") != null && gvar("FIXI4S") == "on" && has(said, "The wood is quiet."),
	"off " + off + " on " + P.isOn() + " '" + said + "'");
OUT.bringMobHere(V, false);
P.tick(P, TPS);
check("off", (!P.isOn()) && R.fetchEffect("Prop_Verbs") == null && R.fetchBehavior("Emoter") == null && gvar("FIXI4E") == "off", "on " + P.isOn());
enter(R);
P.tick(P, TPS);
var cooling = P.isOn();
P.tick(P, TPS); P.tick(P, TPS);
check("cooldown", (!cooling) && P.isOn(), "cooling " + cooling + " now " + P.isOn());
// a mask that does not hold, CHANCE=0, ONCE=WORLD
var P2 = CMClass.getAbility("Prop_When"); P2.setMiscText("ID=Prop_Verbs PARMS=\"VERBS=sing\" WHEN=\"-NAMES +Fixi4nobody\" CHECK=ROOMPLAYERS");
var R2 = scratchRoom(); R2.addNonUninvokableEffect(P2); R2.bringMobHere(V, false); P2.tick(P2, TPS);
var P3 = CMClass.getAbility("Prop_When"); P3.setMiscText("ID=Prop_Verbs PARMS=\"VERBS=sing\" CHANCE=0 CHECK=ROOMPLAYERS");
R2.addNonUninvokableEffect(P3); P3.tick(P3, TPS);
check("mask", (!P2.isOn()) && (!P3.isOn()), "mask " + P2.isOn() + " chance " + P3.isOn());
var P4 = CMClass.getAbility("Prop_When"); P4.setMiscText("ID=Prop_Verbs PARMS=\"VERBS=sing\" ONCE=WORLD CHECK=ROOMPLAYERS");
var R3 = scratchRoom(); R3.addNonUninvokableEffect(P4); R3.bringMobHere(V, false); P4.tick(P4, TPS);
var first = P4.isOn();
OUT.bringMobHere(V, false); P4.tick(P4, TPS); R3.bringMobHere(V, false); P4.tick(P4, TPS);
check("once", first && (!P4.isOn()) && has(P4.text(), "SPENT=true"), "first " + first + " again " + P4.isOn() + " " + P4.text());
// on an item: OWNER
var amulet = CMClass.getItem("GenItem"); amulet.setName("a fixi4 amulet");
var P5 = CMClass.getAbility("Prop_When"); P5.setMiscText("ID=Prop_Verbs PARMS=\"VERBS=glow\" CHECK=OWNER WHEN=\"-NAMES +Fixi4pip\""); amulet.addNonUninvokableEffect(P5);
OUT.addItem(amulet); P5.tick(P5, TPS);
var lying = P5.isOn();
V.addItem(amulet); P5.tick(P5, TPS);
check("owner", (!lying) && P5.isOn() && amulet.fetchEffect("Prop_Verbs") != null, "lying " + lying + " carried " + P5.isOn());
check("help", has(CMLib.help().getHelpText("PROP_WHEN", M, true), "ROOMPLAYERS"), "no help");

V.setPlayerStats(null); V.destroy();
done();
