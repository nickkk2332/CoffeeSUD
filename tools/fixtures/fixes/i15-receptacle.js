// I15: receptacles: Prop_LimitedContents LOCKIN, LOCKMSG, CONSUME, ONPUT, ONFULL, ONEMPTY (actions; a semicolon in
// quotes no longer splits the text), the consumed tally kept in the text.
FIXFILE = "i15-receptacle";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi15pip");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function box(name, parms) {
	var C = CMClass.getItem("GenContainer"); C.setName(name); C.setCapacity(500); C.setDoorsNLocks(false, true, false, false, false, false);
	CMLib.flags().setGettable(C, false); R.addItem(C);
	var P = CMClass.getAbility("Prop_LimitedContents"); P.setMiscText(parms); C.addNonUninvokableEffect(P);
	return { C: C, P: P };
}
function gem(name, tag) { var I = CMClass.getItem("GenItem"); I.setName(name); if (tag) I.addTag(tag); V.addItem(I); return I; }
// the dragon statue's eyes
var st = box("a dragon statue", "2 -TAGS +DRAGONEYE;0;MSG=\"It does not fit.\";LOCKIN=true;LOCKMSG=\"The eye is set fast.\""
	+ ";ONPUT=\"setvar FIXTEST FIXI15P $n\";ONFULL=\"setvar FIXTEST FIXI15F full; setvar FIXTEST FIXI15G two\"");
["FIXI15P", "FIXI15F", "FIXI15G", "FIXI15E", "FIXI15O"].forEach(clearVar);
var e1 = gem("a red fixi15 eye", "DRAGONEYE"), e2 = gem("a green fixi15 eye", "DRAGONEYE"), rock = gem("a fixi15 rock", null);
out();
var putRock = CMLib.commands().postPut(V, st.C, rock, false);
var said = out();
CMLib.commands().postPut(V, st.C, e1, false);
var one = gvar("FIXI15F");
CMLib.commands().postPut(V, st.C, e2, false);
check("filter", (!putRock) && has(said, "It does not fit.") && rock.container() == null, "rock put " + putRock + " '" + said + "'");
check("onput", gvar("FIXI15P") == "Fixi15pip", "ONPUT '" + gvar("FIXI15P") + "'");
check("onfull", one == "" && gvar("FIXI15F") == "full" && gvar("FIXI15G") == "two", "after one '" + one + "' after two '" + gvar("FIXI15F") + "' '" + gvar("FIXI15G") + "'");
out();
var got = CMLib.commands().postGet(V, st.C, e1, false);
said = out();
check("lockin", (!got) && e1.container() == st.C && has(said, "The eye is set fast."), "got " + got + " '" + said + "'");
// an offering bowl: CONSUME, the tally kept
var bowl = box("an offering bowl", "3 -TAGS +FIXI15COIN;0;CONSUME=true;ONFULL=\"setvar FIXTEST FIXI15O done\"");
var coins = [gem("a fixi15 coin", "FIXI15COIN"), gem("a fixi15 coin", "FIXI15COIN"), gem("a fixi15 coin", "FIXI15COIN")];
CMLib.commands().postPut(V, bowl.C, coins[0], false);
CMLib.commands().postPut(V, bowl.C, coins[1], false);
var kept = s(bowl.P.text());
CMLib.commands().postPut(V, bowl.C, coins[2], false);
check("consume", coins[0].amDestroyed() && bowl.C.getContents().size() == 0 && has(kept, "TALLY=2") && gvar("FIXI15O") == "done", "gone " + coins[0].amDestroyed() + " text " + kept + " full '" + gvar("FIXI15O") + "'");
var copy = CMClass.getAbility("Prop_LimitedContents"); copy.setMiscText(bowl.P.text());
check("tally", has(copy.text(), "TALLY=3") && has(copy.text(), "CONSUME=true"), "copy " + copy.text());
// a shelf: ONEMPTY
var shelf = box("a fixi15 shelf", "5;ONEMPTY=\"setvar FIXTEST FIXI15E empty\"");
var book = gem("a fixi15 book");
CMLib.commands().postPut(V, shelf.C, book, false);
CMLib.commands().postGet(V, shelf.C, book, false);
check("onempty", gvar("FIXI15E") == "empty" && book.container() == null && V.isMine(book), "ONEMPTY '" + gvar("FIXI15E") + "'");
check("help", has(CMLib.help().getHelpText("PROP_LIMITEDCONTENTS", M, true), "ONFULL"), "no help");

V.setPlayerStats(null); V.destroy();
[st.C, bowl.C, shelf.C].forEach(function (c) { c.destroy(); });
done();
