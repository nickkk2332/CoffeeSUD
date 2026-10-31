// Shared by every fixes test: tools/fixes.sh puts it in front of each file.
// A test reports with check(name, condition, detail); the file ends with done().
var M = mob();
var CM = Packages.com.planet_ink.coffee_mud;
var CMClass = CM.core.CMClass;
var CMLib = CM.core.CMLib;
var CMath = CM.core.CMath;
var CMMsg = CM.Common.interfaces.CMMsg;
var Tickable = CM.core.interfaces.Tickable;
var FIXFILE = "?";
function ok(name) { M.tell("FIXTEST ok " + FIXFILE + "/" + name); }
function fail(name, why) { M.tell("FIXTEST FAIL " + FIXFILE + "/" + name + ": " + why); }
function check(name, cond, why) { if (cond) ok(name); else fail(name, why || "false"); }
function done() { M.tell("FIXTEST done " + FIXFILE); }
// a fresh room of the player's own, linked to nothing, for a test to use
function scratchRoom() {
	var R = CMClass.getLocale("StdRoom");
	R.setRoomID("");
	R.setArea(M.location().getArea());
	R.setDisplayText("Fixes test room");
	return R;
}
// a mob from its class, brought to life in room R
function newMob(cls, R) {
	var m = CMClass.getMOB(cls);
	m.basePhyStats().setLevel(5);
	m.recoverPhyStats();
	m.bringToLife(R, true);
	return m;
}
// a script engine on host H with the given script text
function scriptOn(H, text) {
	var S = CMClass.getCommon("DefaultScriptingEngine");
	S.setScript(text);
	H.addScript(S);
	return S;
}
var CMProps = CM.core.CMProps;
var CMSecurity = CM.core.CMSecurity;
var WorldFiles = CM.core.save.WorldFiles;
// script variables of the host FIXTEST, as MPSETVAR FIXTEST NAME VALUE sets them
var VARS = CMClass.getCommon("DefaultScriptingEngine");
function gvar(name) { return "" + VARS.getVar("FIXTEST", name); }
function clearVar(name) { VARS.setVar("FIXTEST", name, ""); }
// runs a script engine's queued responses, as its ticks would
function runQueued(S) { for (var i = 0; i < 4; i++) S.dequeResponses(null); }
// another room of the map, not the player's
function otherRoom() {
	var here = M.location();
	for (var e = here.getArea().getProperMap(); e.hasMoreElements();) {
		var R = e.nextElement();
		if (R != here) return R;
	}
	return null;
}
// runs a mob's scripts for one tick, as the game would (a RAND_PROG 100 runs)
function tickMob(m) { m.tick(m, Tickable.TICKID_MOB); }
// a mob in the player's room, with a script that runs on its next tick
function scriptedMob(name, script) {
	var m = newMob("GenMob", M.location());
	m.setName(name);
	scriptOn(m, script);
	return m;
}
// reads a field of obj, private or inherited
function field(obj, name) {
	for (var c = obj.getClass(); c != null; c = c.getSuperclass()) {
		try { var f = c.getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch (e) { }
	}
	throw "no field " + name;
}
// calls method name of obj (the first declared with that name and number of arguments)
function call(obj, name, args) {
	for (var c = obj.getClass(); c != null; c = c.getSuperclass()) {
		var ms = c.getDeclaredMethods();
		for (var i = 0; i < ms.length; i++) {
			if (ms[i].getName() == name && ms[i].getParameterCount() == args.length) {
				ms[i].setAccessible(true);
				return ms[i].invoke(obj, args);
			}
		}
	}
	throw "no method " + name;
}
// calls a method by name and parameter type names, static (obj null, cls given) or not
function callTyped(obj, cls, name, typeNames, args) {
	for (var c = (cls != null ? cls : obj.getClass()); c != null; c = c.getSuperclass()) {
		var ms = c.getDeclaredMethods();
		for (var i = 0; i < ms.length; i++) {
			if (ms[i].getName() != name) continue;
			var ps = ms[i].getParameterTypes();
			if (ps.length != typeNames.length) continue;
			var same = true;
			for (var j = 0; j < ps.length; j++) if (ps[j].getSimpleName() != typeNames[j]) same = false;
			if (!same) continue;
			ms[i].setAccessible(true);
			return ms[i].invoke(obj, args);
		}
	}
	throw "no method " + name + "(" + typeNames + ")";
}
