// B7: the effects editor: =ID and a number edit in place, -ID removes, ?P lists what fits, and a
// second copy is offered Prop_MultiEffects.
FIXFILE = "b7-effects";
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
S.initialize(null, "MEMORY");
var B = newMob("GenMob", R);
B.setName("Fixb7builder");
B.setSavable(false);
B.setSession(S);
S.setMob(B);
var mark = 0;
function out() { var all = "" + field(S, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function edit(I, lines) { for (var i = 0; i < lines.length; i++) S.setFakeInput(lines[i]); CMLib.genEd().genAffects(B, I, 1, 1); return out(); }
function text(I, id) { var A = I.fetchEffect(id); return (A == null) ? null : ("" + A.text()); }

var I = CMClass.getItem("GenItem");
I.setName("a fixb7 ring");
var W = CMClass.getAbility("Prop_WearAdjuster");
W.setMiscText("str+2");
I.addNonUninvokableEffect(W);
var said = edit(I, ["=Prop_WearAdjuster", "str+3"]);
check("edit-id", text(I, "Prop_WearAdjuster") == "str+3" && has(said, "1.Prop_WearAdjuster(str+2)") && has(said, "Prop_WearAdjuster changed."), text(I, "Prop_WearAdjuster") + " / " + said);
said = edit(I, ["1", "str+4"]);
check("edit-number", text(I, "Prop_WearAdjuster") == "str+4", "" + text(I, "Prop_WearAdjuster"));
said = edit(I, ["=1", ""]);
check("edit-keep", text(I, "Prop_WearAdjuster") == "str+4", "" + text(I, "Prop_WearAdjuster"));
said = edit(I, ["-Prop_WearAdjuster"]);
check("remove", text(I, "Prop_WearAdjuster") == null && has(said, "Prop_WearAdjuster removed."), said);
said = edit(I, ["-Prop_WearAdjuster"]);
check("remove-none", has(said, "There is no effect 'Prop_WearAdjuster' here."), said);
said = edit(I, ["Prop_WearAdjuster", "dex+1"]);
check("add", text(I, "Prop_WearAdjuster") == "dex+1" && has(said, "Prop_WearAdjuster added."), said);
// a second copy: wrapped with the first in Prop_MultiEffects
said = edit(I, ["Prop_WearAdjuster", "Y", "con+1"]);
var multi = text(I, "Prop_MultiEffects");
check("double", text(I, "Prop_WearAdjuster") == null && multi != null && has(multi, "dex+1") && has(multi, "con+1")
	&& has(said, "Prop_WearAdjuster and the second copy are in Prop_MultiEffects now."), multi + " / " + said);
said = edit(I, ["Prop_WearAdjuster", "int+1"]);
check("add-plain", text(I, "Prop_WearAdjuster") == "int+1", "" + text(I, "Prop_WearAdjuster"));
said = edit(I, ["Prop_WearAdjuster"]);
check("double-again", has(said, "and so is a Prop_MultiEffects") && text(I, "Prop_WearAdjuster") == "int+1", said);
// ?P: properties that can affect it, each with a line of help
said = edit(I, ["?P"]);
check("fit", has(said, "Properties and effects that can affect a fixb7 ring:") && has(said, "Prop_WearAdjuster") && !has(said, "Spell_Fireball"), said.substring(0, 300));
I.destroy();
B.setSession(null);
B.destroy();
done();
