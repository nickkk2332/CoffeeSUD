// I8: Prop_Invokable WORD, VERB, SPELLS (;LEVEL=), TARGET, CHARGES (LEFT kept), RECHARGE=TICKS=n, COOLDOWN, REQUIRE,
// MASK, MSG, EMPTYMSG; IDENTIFY's text.
FIXFILE = "i8-invokable";
var W = CM.Items.interfaces.Wearable;
var SE = CM.core.ScriptEvents;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi8pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function say(t) { out(); CMLib.commands().postSay(V, null, t, false, false); return out(); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
var ring = CMClass.getItem("GenItem"); ring.setName("the fixi8 ember ring"); ring.setRawLogicalAnd(false); ring.setRawProperLocationBitmap(W.WORN_LEFT_FINGER); V.addItem(ring);
var P = CMClass.getAbility("Prop_Invokable");
P.setMiscText("WORD=Ignis SPELLS=\"Spell_Shield;LEVEL=12\" TARGET=SELF CHARGES=2 RECHARGE=TICKS=3 REQUIRE=WORN MSG=\"<T-NAME> blazes with light!\" EMPTYMSG=\"The ring is cold.\"");
ring.addNonUninvokableEffect(P);
var said = say("Ignis!");
var unworn = V.fetchEffect("Spell_Shield") == null && P.left() == 2;
ring.wearAt(W.WORN_LEFT_FINGER);
said = say("Ignis!");
check("word", unworn && V.fetchEffect("Spell_Shield") != null && P.left() == 1 && has(said, "blazes with light") && has(P.text(), "LEFT=1"), "unworn " + unworn + " left " + P.left() + " '" + said + "'");
var sh = V.fetchEffect("Spell_Shield"); if (sh != null) sh.unInvoke();
say("Ignis");
said = say("Ignis");
check("empty", P.left() == 0 && has(said, "The ring is cold."), "left " + P.left() + " '" + said + "'");
P.tick(P, TPS); P.tick(P, TPS); P.tick(P, TPS);
check("recharge", P.left() == 2, "left " + P.left());
check("identify", has(P.accountForYourself(), "2 of 2 charges") && has(P.accountForYourself(), "Spell_Shield"), s(P.accountForYourself()));
// INVOKE, a cooldown, a mask
var wand = CMClass.getItem("GenItem"); wand.setName("a fixi8 twig wand"); V.addItem(wand);
var PW = CMClass.getAbility("Prop_Invokable"); PW.setMiscText("SPELLS=\"Spell_Shield\" COOLDOWN=2"); wand.addNonUninvokableEffect(PW);
var r1 = cmd("invoke wand");
var first = V.fetchEffect("Spell_Shield") != null;
var r2 = cmd("invoke wand");
PW.tick(PW, TPS); PW.tick(PW, TPS);
var r3 = cmd("invoke wand");
check("verb", r1.took && first && has(r2.said, "not ready") && r3.took && (!has(r3.said, "not ready")), "first " + first + " '" + r2.said + "' '" + r3.said + "'");
var orb = CMClass.getItem("GenItem"); orb.setName("a fixi8 priest's orb"); V.addItem(orb);
var PO = CMClass.getAbility("Prop_Invokable"); PO.setMiscText("SPELLS=\"Spell_Shield\" MASK=\"-NAMES +Fixi8priest\""); orb.addNonUninvokableEffect(PO);
check("mask", has(cmd("invoke orb").said, "does nothing for you"), "the mask let them");
check("help", has(CMLib.help().getHelpText("PROP_INVOKABLE", M, true), "RECHARGE"), "no help");

V.setPlayerStats(null); V.destroy();
done();
