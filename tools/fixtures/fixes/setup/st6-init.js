// world/js/init.js for the played ST6 test
function onBoot() { java.lang.System.setProperty("fixst6boot", "yes"); }
function onNewGame(p) { p.tell("FIXJS new game, booted " + java.lang.System.getProperty("fixst6boot")); }
function onRestore(p, slot) { p.tell("FIXJS restored " + slot); }
function onBeforeSave(slot) { return slot != "jsrefuse"; }
function onPlayerEnter(p) { p.tell("FIXJS enter " + p.Name()); }
