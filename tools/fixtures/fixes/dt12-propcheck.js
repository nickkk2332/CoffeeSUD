// DT12: PROPCHECK reads each effect's text again and says what would do nothing or not what was meant.
FIXFILE = "dt12-propcheck";
var PC = CM.core.PropCheck;
var cmd = CMClass.getCommand("PropCheckCmd");
function has(list, part) { return ("" + list).indexOf(part) >= 0; }
var R = M.location();
var item = CMClass.getItem("GenItem");
item.setName("a fixes checked chest");
R.addItem(item);
function eff(id, text, on) { var A = CMClass.getAbility(id); A.setMiscText(text); on.addNonUninvokableEffect(A); return A; }
eff("Prop_HaveSpellCast", "Spell_Blindnes;Spell_Light", item);
eff("Prop_AddDamage", "5 FLURBLE", item);
eff("Prop_Transporter", "NoSuchArea#55", item);
eff("Prop_ReqEntry", "-race +elf", item);
var cap = eff("Prop_ReqCapacity", "people=2 peeple=3", R);
var got = "" + PC.checkRoom(R);
var at = CMLib.map().getExtendedRoomID(R) + " / a fixes checked chest / ";
check("spell", has(got, at + "Prop_HaveSpellCast: unknown ability Spell_Blindnes") && !has(got, "unknown ability Spell_Light"), got);
check("logged", has(got, at + "Prop_AddDamage: Unknown weapon type/attack: FLURBLE"), got);
check("room-id", has(got, at + "Prop_Transporter: no room NoSuchArea#55"), got);
check("cannot-affect", has(got, at + "Prop_ReqEntry: cannot affect GenItem"), got);
check("key", has(got, "Prop_ReqCapacity: unknown key PEEPLE (AHELP PROP_REQCAPACITY)") && !has(got, "unknown key PEOPLE"), got);

// the command
var B = newMob("GenMob", R);
B.setName("Fixpropreader");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var L = new java.util.ArrayList(); L.add("PROPCHECK"); L.add("chest");
cmd.execute(B, L, 0);
check("cmd", has("" + field(BS, "bout").toString(), "PROPCHECK a fixes checked chest: 4 problems:"), "" + field(BS, "bout").toString());
B.setSession(null);
B.destroy();
R.delEffect(cap);
item.destroy();
// the shipped world
var all = PC.checkAll();
check("world", all.size() == 0, all.size() + " problems: " + all);
done();
