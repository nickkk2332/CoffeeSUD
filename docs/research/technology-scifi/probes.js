// Audit observations, not regression assertions. Run only in a disposable
// BUILDER=1 world with JRUN; run-probes.py prepares one. No external network.
var M = mob();
var CM = Packages.com.planet_ink.coffee_mud;
var CMClass = CM.core.CMClass;
var CMLib = CM.core.CMLib;
function emit(id, value) { M.tell("TECHOBS " + id + " " + value); }
function method(obj, name, n) {
    for (var c = obj.getClass(); c !== null; c = c.getSuperclass()) {
        var methods = c.getDeclaredMethods();
        for (var i = 0; i < methods.length; i++) {
            if (String(methods[i].getName()) === name && methods[i].getParameterCount() === n) {
                methods[i].setAccessible(true);
                return methods[i];
            }
        }
    }
    throw "No method " + name;
}
function call(obj, name, args) { return method(obj, name, args.length).invoke(obj, args); }
function field(obj, name) {
    for (var c = obj.getClass(); c !== null; c = c.getSuperclass()) {
        try { var f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (e) { }
    }
    throw "No field " + name;
}
function rootError(e) {
    var x = e.javaException || e;
    while (x.getCause && x.getCause() !== null) x = x.getCause();
    return x.getClass ? String(x.getClass().getSimpleName()) : String(x);
}
function observe(id, fn) {
    try { emit(id, fn()); } catch (e) { emit(id, "ERROR " + rootError(e)); }
}
function coords(x, y, z) {
    var a = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, 3);
    a[0] = x; a[1] = y; a[2] = z;
    return new CM.core.collections.Coord3D(a);
}
var ids = ["GenFuellessGenerator", "GenCompGenerator", "GenSpaceDrive"];
for (var i = 0; i < ids.length; i++) {
    (function (id) { observe("F01", function () {
        var item = CMClass.getItem(id);
        try {
            item.setPowerCapacity(1000); item.setPowerRemaining(250);
            item.setStat("POWERREM", "40");
            return id + " cap=" + item.powerCapacity() + " rem=" + item.powerRemaining();
        } finally { item.destroy(); }
    }); })(ids[i]);
}
observe("F02", function () {
    var item = CMClass.getItem("GenComputerConsole");
    try { item.setStat("RECHRATE", "0.5"); return "recharge=" + item.getRechargeRate(); }
    finally { item.destroy(); }
});
observe("F03", function () {
    var item = CMClass.getItem("StdShipGravityGen");
    var other = CMClass.getItem("StdShipGravityGen");
    try { return "type=" + item.getTechType() + " sameAs=" + item.sameAs(other); }
    finally { item.destroy(); other.destroy(); }
});
observe("F04", function () {
    var target = CMClass.getMOB("GenMob");
    var scan = CMClass.getItem("HealthScanProgram");
    try {
        target.setLocation(M.location());
        target.maxState().setHitPoints(100); target.curState().setHitPoints(50);
        var report = String(scan.getScanMsg(M, target));
        return report.match(/health:[^\r\n]*/i)[0];
    } finally { target.destroy(); scan.destroy(); }
});
for (var i = 0; i < 2; i++) {
    (function (id) { observe("F05", function () {
        var item = CMClass.getItem(id);
        try { return id + " provides=" + java.util.Arrays.toString(call(item, "getProvidedServices", [])); }
        finally { item.destroy(); }
    }); })(["DatabaseProgram", "ShipSensorProgram"][i]);
}
observe("F06", function () {
    var db = CMClass.getItem("DatabaseProgram");
    var json = new CM.core.MiniJSON().parseObject('{"NAME":"Beacon","CUSTOM":"signal"}');
    try { return String(call(db, "getSpaceObjectDataResults", ["Beacon", coords(1, 2, 3), json])); }
    finally { db.destroy(); }
});
observe("F10", function () {
    var screen = CMClass.getItem("GenShipViewScreen");
    var asteroid = CMClass.getItem("Asteroid");
    try {
        asteroid.setName("audit asteroid");
        var converter = call(screen, "getSensedObjectConverter", []);
        var proxy = converter.convert(asteroid);
        return "contact=" + proxy.Name() + " original=" + asteroid.Name();
    } finally { screen.destroy(); asteroid.destroy(); }
});
observe("F11", function () {
    var shield = CMClass.getItem("StdShipShieldGenerator");
    var errors = 0;
    try {
        shield.setPermittedNumDirections(3);
        for (var i = 0; i < 32; i++) {
            field(shield, "currCoverage").set(shield, null);
            try { call(shield, "getCurrentCoveredDirections", []); }
            catch (e) { if (rootError(e).indexOf("IndexOutOfBounds") >= 0) errors++; else throw e; }
        }
        return "arc-index-errors=" + errors + "/32";
    } finally { shield.destroy(); }
});
observe("F17", function () {
    var chaser = CMClass.getItem("Asteroid");
    var runner = CMClass.getItem("Asteroid");
    try {
        chaser.setCoords(coords(10, 0, 0)); runner.setCoords(coords(0, 0, 0));
        chaser.setRadius(1); runner.setRadius(1);
        runner.setDirection(new CM.core.collections.Dir3D(0, Math.PI / 2));
        runner.setSpeed(1);
        return "reachable-intercept=" + CMLib.space().calculateIntercept(chaser, runner, 2, 10);
    } finally { chaser.destroy(); runner.destroy(); }
});
observe("F22", function () {
    var asteroid = CMClass.getItem("Asteroid");
    try {
        asteroid.setName("audit save-warning asteroid");
        CMLib.space().addObjectToSpace(asteroid, coords(1000000, 2000000, 3000000));
        var exported = CM.core.save.WorldFiles.spaceItems().contains(asteroid);
        var warning = String(CM.core.save.WorldState.notKept(M)).indexOf("not saved where") >= 0;
        return "exported=" + exported + " warns-unsaved=" + warning;
    } finally { CMLib.space().delObjectInSpace(asteroid); asteroid.destroy(); }
});
observe("F21", function () {
    var nav = CMClass.getItem("ShipNavProgram");
    var restored = CMClass.getItem("ShipNavProgram");
    try {
        field(nav, "courseSet").setBoolean(nav, true);
        restored.setMiscText(nav.text());
        return "courseSet=" + field(nav, "courseSet").getBoolean(nav)
            + " roundtrip=" + field(restored, "courseSet").getBoolean(restored);
    } finally { nav.destroy(); restored.destroy(); }
});
emit("DONE", "completed");
