// I21: key codes as lists (a;b) and master keys (jail.*), keyrings at any depth; Prop_KeyRules CONSUME BREAK RETURN
// MASK; Prop_LockRules PICK=NEVER|LEVEL:n KEYS= RELOCK= MSG=.
FIXFILE = "i21-keys";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var CT = CM.Items.interfaces.Container;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi21Jail");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi21Jail#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var HALL = room(0, "Fixi21 cell block"), C1 = room(1, "Fixi21 cell one"), C2 = room(2, "Fixi21 cell two"), VAULT = room(3, "Fixi21 vault"), OSS = room(4, "Fixi21 ossuary"), GUARD = room(5, "Fixi21 guard room");
function door(a, d, b, key) {
	var e1 = CMClass.getExit("GenExit"), e2 = CMClass.getExit("GenExit");
	[e1, e2].forEach(function (e) { e.setName("a barred door"); e.setDoorsNLocks(true, false, true, true, true, true); e.setKeyName(key); });
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
	return e1;
}
var D1 = door(HALL, Dir.NORTH, C1, "jail.1"), D2 = door(HALL, Dir.SOUTH, C2, "jail.2"), DV = door(HALL, Dir.EAST, VAULT, "vault"), DO = door(HALL, Dir.WEST, OSS, "ossuary"), DG = door(HALL, Dir.UP, GUARD, "guard");
var V = CMClass.getMOB("GenMob"); V.setName("Fixi21pip"); V.basePhyStats().setLevel(1); V.recoverPhyStats(); V.bringToLife(HALL, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function unlock(E) { out(); var m = CMClass.getMsg(V, E, null, CMMsg.MSG_UNLOCK, "<S-NAME> unlock(s) <T-NAME>."); if (HALL.okMessage(V, m)) { HALL.send(V, m); } return out(); }
function key(name, code, where) { var k = CMClass.getItem("GenKey"); k.setName(name); k.setKey(code); V.addItem(k); if (where) k.setContainer(where); return k; }
// the master key, on a keyring in a pack
var pack = CMClass.getItem("GenContainer"); pack.setName("a fixi21 pack"); pack.setCapacity(100); V.addItem(pack);
var ring = CMClass.getItem("GenContainer"); ring.setName("a fixi21 keyring"); ring.setCapacity(10); ring.setContainTypes(CT.CONTAIN_KEYS); V.addItem(ring); ring.setContainer(pack);
var master = key("the jailer's key", "jail.*", ring);
unlock(D1); unlock(D2);
check("master", (!D1.isLocked()) && (!D2.isLocked()), "cell one " + D1.isLocked() + " cell two " + D2.isLocked());
// a list
var both = key("a fixi21 twin key", "crypt;vault", null);
unlock(DV);
check("list", !DV.isLocked(), "vault still locked");
// used up
var bone = key("a cursed bone key", "ossuary", null);
var KR = CMClass.getAbility("Prop_KeyRules"); KR.setMiscText("CONSUME=true"); bone.addNonUninvokableEffect(KR);
var said = unlock(DO);
check("consume", (!DO.isLocked()) && bone.amDestroyed() && has(said, "crumble"), "locked " + DO.isLocked() + " key gone " + bone.amDestroyed() + " '" + said + "'");
// a key not for everyone; a lock no thief picks, that takes another key and locks itself again
var gk = key("a guard's key", "guard", null);
var KM = CMClass.getAbility("Prop_KeyRules"); KM.setMiscText("MASK=\"-NAMES +Fixi21guard\" MSG=\"The key will not turn for a prisoner.\""); gk.addNonUninvokableEffect(KM);
said = unlock(DG);
check("mask", DG.isLocked() && has(said, "will not turn for a prisoner"), "locked " + DG.isLocked() + " '" + said + "'");
var LR = CMClass.getAbility("Prop_LockRules"); LR.setMiscText("PICK=NEVER KEYS=spare RELOCK=2 MSG=\"The lock has no keyhole to pick.\""); DG.addNonUninvokableEffect(LR);
out();
var pick = CMClass.getMsg(V, DG, CMClass.getAbility("Thief_Pick"), CMMsg.MSG_DELICATE_SMALL_HANDS_ACT, CMMsg.MSG_OK_VISUAL, CMMsg.MSG_OK_VISUAL, null);
var picked = HALL.okMessage(V, pick);
said = out();
check("pick", (!picked) && has(said, "no keyhole to pick"), "picked " + picked + " '" + said + "'");
var spare = key("a spare fixi21 key", "spare", null);
unlock(DG);
var opened = !DG.isLocked();
LR.tick(LR, TPS); LR.tick(LR, TPS);
check("relock", opened && DG.isLocked(), "opened " + opened + " now " + DG.isLocked());
var LL = CMClass.getAbility("Prop_LockRules"); LL.setMiscText("PICK=LEVEL:30"); D1.addNonUninvokableEffect(LL);
var pick2 = CMClass.getMsg(V, D1, CMClass.getAbility("Thief_Pick"), CMMsg.MSG_DELICATE_SMALL_HANDS_ACT, CMMsg.MSG_OK_VISUAL, CMMsg.MSG_OK_VISUAL, null);
check("level", !HALL.okMessage(V, pick2), "a level 1 thief picked a level 30 lock");
check("help", has(CMLib.help().getHelpText("PROP_KEYRULES", M, true), "CONSUME") && has(CMLib.help().getHelpText("PROP_LOCKRULES", M, true), "PICK=NEVER"), "no help");

V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
done();
