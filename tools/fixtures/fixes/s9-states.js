// S9: each behaviour's STATE, read with getStat and put back with setStat on
// a fresh one, as a save and a restore do
FIXFILE = "s9-states";
var Pair = CM.core.collections.Pair;
var GameTime = CM.core.GameTime;
function setField(obj, name, value) {
	for (var c = obj.getClass(); c != null; c = c.getSuperclass()) {
		try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, value); return; } catch (e) { }
	}
	throw "no field " + name;
}
function fresh(id, parms, host) {
	var B = CMClass.getBehavior(id);
	if (host != null) host.addBehavior(B);
	B.setParms(parms);
	return B;
}
var R = M.location();

// Hireling: who it works for, and for how long
var H = fresh("Hireling", "PRICE=10 MINUTES=60", null);
setField(H, "workingFor", "Hirer");
setField(H, "onTheJobUntil", java.lang.Long.valueOf(GameTime.now() + 3600000));
field(H, "partials").put("Hirer", java.lang.Double.valueOf(5.0));
var H2 = fresh("Hireling", "PRICE=10 MINUTES=60", null);
H2.setStat("STATE", H.getStat("STATE"));
var left = field(H2, "onTheJobUntil") - GameTime.now();
check("hireling", field(H2, "workingFor") == "Hirer" && left > 3500000 && left <= 3600000 && field(H2, "partials").get("Hirer") == 5.0,
	"for '" + field(H2, "workingFor") + "', " + left + " ms left");

// Aggressive: who paid a bribe, how much, and for how long
var A = fresh("Aggressive", "BRIBE=10,100,200", null);
field(A, "bribe").fifth.put("Briber", new Pair(java.lang.Double.valueOf(12.0), java.lang.Long.valueOf(GameTime.now() + 60000)));
var A2 = fresh("Aggressive", "BRIBE=10,100,200", null);
A2.setStat("STATE", A.getStat("STATE"));
var paid = field(A2, "bribe").fifth.get("Briber");
check("aggressive", paid != null && paid.first == 12.0 && (paid.second - GameTime.now()) > 50000, "bribe " + paid);

// BribeGateGuard: balances not kept in the journal
var G = fresh("BribeGateGuard", "price=5 gates=FixGate", null);
var bal = new java.util.Hashtable();
bal.put("Payer", java.lang.Double.valueOf(7.0));
var gateKey = call(G, "gates", []);
field(G, "notTheJournal").put(gateKey, bal);
var gstate = G.getStat("STATE");
field(G, "notTheJournal").remove(gateKey);
var G2 = fresh("BribeGateGuard", "price=5 gates=FixGate", null);
G2.setStat("STATE", gstate);
var back = field(G2, "notTheJournal").get(gateKey);
check("bribegateguard", back != null && back.get("Payer") == 7.0, "state " + gstate);

// Patroller: where on its route it is
var P = fresh("Patroller", "north;south;north", null);
setField(P, "step", java.lang.Integer.valueOf(2));
var P2 = fresh("Patroller", "north;south;north", null);
P2.setStat("STATE", P.getStat("STATE"));
check("patroller", field(P2, "step") == 2, "step " + field(P2, "step"));

// Decay: running, with the ticks left in its parameters
var D = fresh("Decay", "remain=50", null);
setField(D, "activated", java.lang.Boolean.TRUE);
setField(D, "tickDown", java.lang.Integer.valueOf(17));
var D2 = fresh("Decay", D.getParms(), null);
D2.setStat("STATE", D.getStat("STATE"));
check("decay", field(D2, "activated") == true && field(D2, "tickDown") == 17, "activated " + field(D2, "activated") + ", " + field(D2, "tickDown") + " left");

// Emoter: ticks until it stops
var E = fresh("Emoter", "expires=100;burps loudly", null);
setField(E, "expires", java.lang.Integer.valueOf(40));
var E2 = fresh("Emoter", "expires=100;burps loudly", null);
E2.setStat("STATE", E.getStat("STATE"));
check("emoter", field(E2, "expires") == 40, "expires " + field(E2, "expires"));

// Concierge: a trip quoted and not yet paid for, back when the asker comes
var Ch = newMob("GenMob", R);
Ch.setName("a guide");
var C = fresh("Concierge", "", Ch);
field(C, "destinations").add(M, otherRoom(), java.lang.Double.valueOf(25.0), CMLib.tracking().newFlags());
var cstate = C.getStat("STATE");
Ch.delBehavior(C);
var C2 = fresh("Concierge", "", Ch);
C2.setStat("STATE", cstate);
call(C2, "resumeSavedTrips", [M]);
var dest = field(C2, "destinations");
check("concierge", dest.size() == 1 && dest.getFirst(0) == M && dest.getThird(0) == 25.0, "state " + cstate);
Ch.destroy();

// Nanny: its charges, found again in the room
var Nm = newMob("GenMob", R);
Nm.setName("a nanny");
var baby = newMob("GenMob", R);
baby.setName("a fix baby");
var N = fresh("Nanny", "", Nm);
N.setStat("STATE", "<CHARGE N=\"a fix baby\" M=\"Mommy\" AGO=\"60000\" /><PAID M=\"Mommy\">3.0</PAID>");
call(N, "resumeSavedDropOffs", [R]);
var nstate = N.getStat("STATE");
check("nanny", nstate.indexOf("a fix baby") >= 0 && nstate.indexOf("Mommy") >= 0, "state " + nstate);
Nm.destroy();
baby.destroy();

// MOBEater: what is in its stomach
var Eater = newMob("GenMob", R);
Eater.setName("a big fish");
var ME = fresh("MOBEater", "", Eater);
var stomach = call(ME, "getStomach", []);
var ring = CMClass.getItem("GenItem");
ring.setName("a swallowed ring");
stomach.addItem(ring);
var mstate = ME.getStat("STATE");
Eater.delBehavior(ME);
var ME2 = fresh("MOBEater", "", Eater);
ME2.setStat("STATE", mstate);
var st2 = call(ME2, "getStomach", []);
check("mobeater", st2.numItems() == 1 && st2.getItem(0).Name() == "a swallowed ring", "stomach has " + st2.numItems());
Eater.destroy();

// MudChat: a group a conversation switched to, for a while
var Talker = newMob("GenMob", R);
Talker.setName("a talker");
var MC = fresh("MudChat", "", Talker);
var groups = call(MC, "getChatGroups", []);
if (groups.length > 1) {
	field(MC, "chatGroups").add(new Pair(groups[1], java.lang.Long.valueOf(GameTime.now() + 30000)));
	var tstate = MC.getStat("STATE");
	Talker.delBehavior(MC);
	var MC2 = fresh("MudChat", "", Talker);
	MC2.setStat("STATE", tstate);
	check("mudchat", field(MC2, "chatGroups").containsFirst(groups[1]), "state " + tstate);
} else
	fail("mudchat", "chat.dat has no groups");
Talker.destroy();
done();
