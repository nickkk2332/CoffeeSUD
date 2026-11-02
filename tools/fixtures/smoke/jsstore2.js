// smoke: the store, after the restore (JS7)
var s = cm.store('world');
mob().tell('JSSTORE got ' + s.served + ' ' + s.orders[0].readyDay + ' ' + s.orders[0].who);
