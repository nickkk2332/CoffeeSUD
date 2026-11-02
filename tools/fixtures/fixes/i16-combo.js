// I16: Prop_ComboLock CODE SYMBOLS DIALS VERB SHOW TRIES/ONWRONG ONOPEN RELOCK MSG, CODE=RANDOM with VAR=;
// TURN DIAL n TO x, SET DIALS, ENTER (a keypad on a door, both sides).
FIXFILE = "i16-combo";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var SE = CM.core.ScriptEvents;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi16Lab");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi16Lab#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var LAB = room(0, "Fixi16 laboratory"), VAULT = room(1, "Fixi16 vault");
function door() { var e = CMClass.getExit("GenExit"); e.setName("a steel door"); e.setDoorsNLocks(true, false, true, true, true, true); return e; }
var D1 = door(), D2 = door();
LAB.rawDoors()[Dir.NORTH] = VAULT; LAB.setRawExit(Dir.NORTH, D1);
VAULT.rawDoors()[Dir.SOUTH] = LAB; VAULT.setRawExit(Dir.SOUTH, D2);
var V = CMClass.getMOB("GenMob"); V.setName("Fixi16pip"); V.bringToLife(LAB, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
var chest = CMClass.getItem("GenContainer"); chest.setName("an alchemist's chest"); chest.setCapacity(100);
chest.setDoorsNLocks(true, false, true, true, true, true); CMLib.flags().setGettable(chest, false); LAB.addItem(chest);
var P = CMClass.getAbility("Prop_ComboLock");
P.setMiscText("CODE=sun-star-moon SYMBOLS=\"sun,moon,star\" SHOW=true TRIES=2 ONWRONG=\"setvar FIXTEST FIXI16W zap\" ONOPEN=\"setvar FIXTEST FIXI16O $n\" RELOCK=2");
chest.addNonUninvokableEffect(P);
["FIXI16W", "FIXI16O"].forEach(clearVar);
out(); LAB.send(V, CMClass.getMsg(V, chest, null, CMMsg.MSG_LOOK, null));
check("show", has(out(), "The dials read sun-sun-sun."), "LOOK does not read the dials");
var r = cmd("turn dial 2 to star");
var bad = cmd("turn dial 4 to sun");
check("turn", r.took && s(P.getStat("READ")) == "sun-star-sun" && has(r.said, "The dials read sun-star-sun.") && has(bad.said, "The dials are 1 to 3"), "read " + P.getStat("READ") + " '" + bad.said + "'");
r = cmd("set dials moon moon moon");
var once = gvar("FIXI16W");
cmd("set dials star star star");
check("tries", has(r.said, "Nothing happens.") && once == "" && gvar("FIXI16W") == "zap" && chest.isLocked(), "after one '" + once + "' after two '" + gvar("FIXI16W") + "'");
r = cmd("set dials sun star moon");
check("open", chest.isOpen() && (!chest.isLocked()) && gvar("FIXI16O") == "Fixi16pip" && has(r.said, "clicks and swings open"), "open " + chest.isOpen() + " '" + r.said + "'");
check("kept", has(P.text(), "READ=sun-star-moon"), "text " + P.text());
P.tick(P, TPS); P.tick(P, TPS);
check("relock", chest.isLocked() && (!chest.isOpen()), "open " + chest.isOpen() + " locked " + chest.isLocked());
// a keypad on a door
var K = CMClass.getAbility("Prop_ComboLock"); K.setMiscText("CODE=739"); D1.addNonUninvokableEffect(K);
r = cmd("enter 111");
var shut = D1.isLocked();
r = cmd("enter 739");
check("keypad", shut && D1.isOpen() && D2.isOpen() && (!D2.isLocked()), "first " + shut + " near " + D1.isOpen() + " far " + D2.isOpen());
var other = cmd("enter the vault");
check("other", !other.took, "ENTER of something else was taken");
// a code rolled for each game
var RND = CMClass.getAbility("Prop_ComboLock"); RND.setMiscText("CODE=RANDOM DIALS=4 VAR=VAULT");
var code = s(RND.getStat("CODE"));
var again = CMClass.getAbility("Prop_ComboLock"); again.setMiscText(RND.text());
check("random", code.split("-").length == 4 && s(VARS.getVar("COMBO", "VAULT")) == code && s(again.getStat("CODE")) == code, "code " + code + " var " + VARS.getVar("COMBO", "VAULT") + " copy " + again.getStat("CODE"));
check("help", has(CMLib.help().getHelpText("PROP_COMBOLOCK", M, true), "SET DIALS"), "no help");

V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
done();
