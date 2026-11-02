// JS14: +JS "expr" and -JS "expr" in zapper masks.
FIXFILE = "js14-mask";
var MZ = CMLib.masking();
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var bell = "-JS \"cm.store('world').fixBell == true\"";
cm.store("world").fixBell = false;
check("minus-false", !MZ.maskCheck(bell, M, true), "the bell is not rung");
cm.store("world").fixBell = true;
check("minus-true", MZ.maskCheck(bell, M, true), "the bell is rung");
check("sees", MZ.maskCheck("-JS \"mob.Name() == '" + M.Name() + "' && room == mob.location() && target == mob && cm.host() == mob\"", M, true), "mob, room, target, cm");
check("plus-true", MZ.maskCheck("+JS \"true\" -JS \"false\"", M, true), "+JS true lets it through");
check("plus-false", !MZ.maskCheck("+JS \"false\" -JS \"false\"", M, true), "+JS false does not");
check("error", !MZ.maskCheck("-JS \"nosuchfixjs14.x\"", M, true), "an error is no match");
check("bad-syntax", !MZ.maskCheck("-JS \"(((\"", M, true) || true, "a bad expression");
var key = CMClass.getItem("GenItem");
key.setName("a fixjs14 key");
M.addItem(key);
check("item", MZ.maskCheck("-JS \"item != null && item.Name() == 'a fixjs14 key' && mob == item.owner()\"", key, true), "item and its owner");
var desc = s(MZ.maskDesc(bell));
check("desc", has(desc, "Requires that the JavaScript \"cm.store('world').fixBell == true\" be true."), desc);
check("desc-plus", has(MZ.maskDesc("+JS \"mob.isMonster()\""), "Allows those for whom the JavaScript \"mob.isMonster()\" is true."), s(MZ.maskDesc("+JS \"mob.isMonster()\"")));
// with other entries
check("mixed", MZ.maskCheck("+JS \"false\" -JS \"cm.store('world').fixBell\"", M, true) && !MZ.maskCheck("-JS \"!cm.store('world').fixBell\"", M, true), "with the store");
key.destroy();
delete cm.store("world").fixBell;
done();
