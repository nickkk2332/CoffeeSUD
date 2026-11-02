// smoke: a JavaScript store, kept by the save (JS7)
var s = cm.store('world');
s.orders = [{who: 'Ann', item: 'longsword', readyDay: 12}];
s.served = 7;
mob().tell('JSSTORE set ' + JSON.stringify(s));
