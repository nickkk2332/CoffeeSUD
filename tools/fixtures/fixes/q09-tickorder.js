// Things tick in the order they came into the game, not by their hash codes (which move between builds).
FIXFILE = "q09-tickorder";
var TO = CM.core.threads.TickOrder, TC = CM.core.threads.StdTickClient;
var made = [];
for (var i = 0; i < 40; i++) { var t = CMClass.getMOB("GenMob"); t.setName("fixorder " + i); made.push(new TC(t, 1, 0)); }
var set = new java.util.TreeSet();
for (var j = made.length - 1; j >= 0; j--) set.add(made[(j * 7) % made.length]); // added out of order
var got = [], inOrder = true, k = 0;
for (var e = set.iterator(); e.hasNext(); k++) { var c = e.next(); got.push("" + c.getClientObject().Name()); if (c != made[k]) inOrder = false; }
check("order-is-arrival", set.size() == 40 && inOrder, got.join(","));
var a = made[3].getClientObject();
check("order-kept", TO.of(a) == TO.of(a) && TO.of(a) < TO.of(made[4].getClientObject()), "the serial moved");
var later = CMClass.getMOB("GenMob");
check("order-newcomer-last", TO.of(later) > TO.of(made[39].getClientObject()), "a newcomer was put ahead of the others");
done();
