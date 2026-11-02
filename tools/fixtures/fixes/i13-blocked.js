// I13: Prop_BlockedExit NAME HP TOOL SKILL FIRE MSG CLEARMSG ONCLEAR RESPAWN; CLEAR/DIG/SMASH/BURN a direction or the
// blockage; blocked both ways (the far side's kept the same); LEFT= kept in the text.
FIXFILE = "i13-blocked";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var SE = CM.core.ScriptEvents;
var W = CM.Items.interfaces.Wearable;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi13Mine");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi13Mine#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var SHAFT = room(0, "Fixi13 shaft"), TUNNEL = room(1, "Fixi13 tunnel"), JUNGLE = room(2, "Fixi13 jungle"), RUIN = room(3, "Fixi13 ruin");
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
	return [e1, e2];
}
var mine = link(SHAFT, Dir.EAST, TUNNEL), path = link(JUNGLE, Dir.NORTH, RUIN);
var V = CMClass.getMOB("GenMob"); V.setName("Fixi13pip"); V.bringToLife(SHAFT, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
var P = CMClass.getAbility("Prop_BlockedExit");
P.setMiscText("NAME=\"a pile of rubble\" HP=12 TOOL=\"-TAGS +PICKAXE\" SKILL=STR CLEARMSG=\"The rubble gives way to a dark tunnel.\" ONCLEAR=\"setvar FIXTEST FIXI13C open\"");
mine[0].addNonUninvokableEffect(P);
var P2 = CMClass.getAbility("Prop_BlockedExit"); P2.setMiscText("NAME=\"a pile of rubble\" HP=12"); mine[1].addNonUninvokableEffect(P2);
clearVar("FIXI13C");
out();
CMLib.tracking().walk(V, Dir.EAST, false, false);
var said = out();
check("blocks", V.location() == SHAFT && has(said, "A pile of rubble blocks the way."), "in " + V.location() + " '" + said + "'");
var r = cmd("dig east");
check("tool", r.took && has(r.said, "the right tool") && s(P.getStat("LEFT")) == "12", "'" + r.said + "' left " + P.getStat("LEFT"));
var pick = CMClass.getItem("GenItem"); pick.setName("an iron pickaxe"); pick.addTag("PICKAXE"); pick.setRawLogicalAnd(false); pick.setRawProperLocationBitmap(W.WORN_HELD); V.addItem(pick); pick.wearAt(W.WORN_HELD);
r = cmd("dig east");
var after1 = CMath.s_int(P.getStat("LEFT"));
check("try", r.took && after1 < 12 && s(P2.getStat("LEFT")) == s(after1) && has(P.text(), "LEFT=" + after1), "left " + after1 + " far " + P2.getStat("LEFT") + " text " + P.text());
for (var i = 0; i < 20 && P.blocks(); i++) cmd("clear rubble");
check("cleared", (!P.blocks()) && (!P2.blocks()) && gvar("FIXI13C") == "open", "near " + P.blocks() + " far " + P2.blocks() + " '" + gvar("FIXI13C") + "'");
CMLib.tracking().walk(V, Dir.EAST, false, false);
check("through", V.location() == TUNNEL, "in " + V.location());
// vines that burn, and grow back
var PV = CMClass.getAbility("Prop_BlockedExit");
PV.setMiscText("NAME=\"a wall of vines\" HP=50 FIRE=true RESPAWN=2");
path[0].addNonUninvokableEffect(PV);
JUNGLE.bringMobHere(V, false);
r = cmd("burn north");
var noFlame = has(r.said, "flame in hand");
var torch = CMClass.getItem("GenLightSource"); torch.setName("a torch"); V.addItem(torch); torch.wearAt(W.WORN_HELD); torch.light(true);
r = cmd("burn vines");
var burned = !PV.blocks();
PV.tick(PV, TPS); PV.tick(PV, TPS);
check("burn", noFlame && burned && PV.blocks(), "no flame " + noFlame + " burned " + burned + " grown " + PV.blocks());
check("other", !cmd("dig west").took, "a line for another exit was taken");
check("help", has(CMLib.help().getHelpText("PROP_BLOCKEDEXIT", M, true), "RESPAWN"), "no help");

V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
done();
