// G17: JavaScript in random-area templates: ${js:expr} in text, CONDITION="js:..." and VALIDATE="js:...", with defined, obj, test(), cm and require(); merged js: conditions; GENERATE CHECK compiles them; JSCRIPTS=0 turns it off.
FIXFILE = "g17-js";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function write(F, t) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
var T = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg17.xml");
var TB = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg17bad.xml");
var L = new java.io.File(new java.io.File(new java.io.File(WorldFiles.worldDir(), "js"), "lib"), "fixg17names.js");
var GC = CM.core.GenCalls, GS = CM.core.GenSeed, F = "randareas/fixg17.xml";
function vars(o) { var V = new java.util.HashMap(); for (var k in o) V.put(k, "" + o[k]); return V; }
function str(id, o) { return s(GC.string(F, id, (o == null) ? null : vars(o), null, M.location())); }
try {
	write(L, "var made = 0;\nexports.dwarven = function(n) { made++; var a = ['Thr', 'Bor', 'Dur']; var b = ['ain', 'grum', 'in']; var s = ''; for (var i = 0; i < n; i++) s += (i == 0 ? a : b)[i % 3]; return s; };\nexports.made = function() { return made; };\n");
	CM.core.JSScope.reload();
	write(T, '<AREADATA>'
		+ '<STRING id="fixg17_a">${js:\'a\'+\'b\'+(1+2)} and ${js:[1,2,3].map(function(x){return x*2;}).join(\'-\')}</STRING>'
		+ '<STRING id="fixg17_req">${js:require(\'fixg17names\').made()} ${js:require(\'fixg17names\').dwarven(2)}</STRING>'
		+ '<STRING id="fixg17_def">${js:defined.fixlevel*2} ${js:defined.FixName} ${js:typeof defined.fixnothing}</STRING>'
		+ '<STRING id="fixg17_set">$fixset ${js:defined.fixset=\'zz\',\'x\'}</STRING>'
		+ '<STRING id="fixg17_tag">${js:defined.fixg17_inner}</STRING><STRING id="fixg17_inner">inner</STRING>'
		+ '<STRING id="fixg17_cond" select="any-1"><STRING condition="js:defined.fixlevel &gt; 5">big</STRING><STRING condition="js:defined.fixlevel &lt;= 5">small</STRING></STRING>'
		+ '<MOB id="fixg17_val" select="all"><MOB class="GenMob" level="1" name="a fixg17 seven" display="x" validate="js:defined.fixlevel == 7 &amp;&amp; test(\'$fixlevel = 7\')" />'
		+ '<MOB class="GenMob" level="1" name="a fixg17 other" display="x" validate="js:!(defined.fixlevel == 7)" /></MOB>'
		+ '<STRING id="fixg17_off" select="all"><STRING condition="js:true">yes </STRING><STRING>always</STRING></STRING>'
		+ '<STRING id="fixg17_store" select="all"><STRING condition="js:!cm.store(\'world\').fixg17burnt">bridge </STRING><STRING>road</STRING></STRING>'
		+ '<STRING id="fixg17_seed">${js:Math.floor(Math.random()*1000000000)}</STRING>'
		+ '<STRING id="fixg17_err">${js:fixg17nosuch.thing}</STRING>'
		+ '<STRING id="fixg17_m" merge="true" select="any-1" condition="js:defined.fixlevel == 7"><STRING>merged</STRING></STRING>'
		+ '<STRING id="fixg17_m" merge="true" select="any-1" condition="$fixlevel = 7" />'
		+ '<STRING id="fixg17_mp" select="any-1"><STRING insert="fixg17_m" /><STRING condition="js:defined.fixlevel != 7">notseven</STRING></STRING>'
		+ '<MOB id="fixg17_mob" class="GenMob" level="1" name="${js:\'a fixg17 \'+obj.ID().toLowerCase()}" display="A fixg17 mob is here." />'
		+ '</AREADATA>');
	check("text", str("fixg17_a") == "ab3 and 2-4-6", str("fixg17_a"));
	var r = str("fixg17_req");
	check("require", r == "1 Thrgrum", r); // filled from the last: a run's scope keeps the library's state
	var d = str("fixg17_def", { FIXLEVEL: 7, FIXNAME: "Bob" });
	check("defined", d == "14 Bob undefined", d);
	var st = str("fixg17_set");
	check("set", st == "zz x", st);
	var tg = str("fixg17_tag");
	check("a string's id", tg == "inner", tg);
	var c7 = str("fixg17_cond", { FIXLEVEL: 7 }), c3 = str("fixg17_cond", { FIXLEVEL: 3 });
	check("condition", c7 == "big" && c3 == "small", c7 + " / " + c3);
	function mobNames(id, o) { var ms = GC.mobs(F, id, (o == null) ? null : vars(o), null, M.location()), n = []; for (var i = 0; i < ms.size(); i++) { n.push(s(ms.get(i).Name())); ms.get(i).destroy(); } return n.join(","); }
	var v7 = mobNames("fixg17_val", { FIXLEVEL: 7 }), v3 = mobNames("fixg17_val", { FIXLEVEL: 3 });
	check("validate", v7 == "a fixg17 seven" && v3 == "a fixg17 other", v7 + " / " + v3);
	var W = cm.store('world');
	delete W.fixg17burnt;
	var b1 = str("fixg17_store");
	W.fixg17burnt = true;
	var b2 = str("fixg17_store");
	delete W.fixg17burnt;
	check("cm.store", b1.replace(/\s+/g, " ").trim() == "bridge road" && b2.trim() == "road", b1 + " / " + b2);
	var sb = GS.begin(1717), s1 = str("fixg17_seed"); GS.end(sb);
	sb = GS.begin(1717); var s2 = str("fixg17_seed"); GS.end(sb);
	sb = GS.begin(1718); var s3 = str("fixg17_seed"); GS.end(sb);
	check("seeded", s1 == s2 && s1 != s3 && /^\d+$/.test(s1), s1 + " " + s2 + " " + s3);
	var err = "";
	try { str("fixg17_err"); } catch (x) { err = s(x); }
	var errs = s(CM.core.JSRuntime.recentErrors(50));
	check("error", has(err, "fixg17nosuch") && has(err, "template STRING fixg17_err") && has(errs, "fixg17_err"), err);
	var P = GC.prepare(F, "fixg17_m", "STRING", null, null, M.location());
	var mc = s(P.piece.getParmValue("CONDITION"));
	var XL = CMLib.xml();
	check("xml escapes", s(XL.restoreAngleBrackets("a &amp;&amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos; &foo &qu")) == "a && b < c > d \"e\" 'f' &foo &qu", s(XL.restoreAngleBrackets("a &amp;&amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos; &foo &qu")));
	var m7 = str("fixg17_mp", { FIXLEVEL: 7 }), m3 = str("fixg17_mp", { FIXLEVEL: 3 });
	check("merged", has(mc, "js:(defined.fixlevel == 7)&&(test(") && m7 == "merged" && m3 == "notseven", mc + " / " + m7 + " / " + m3);
	var mobs = GC.mobs(F, "fixg17_mob", null, null, M.location());
	var mn = (mobs.size() > 0) ? s(mobs.get(0).Name()) : "none";
	for (var i = 0; i < mobs.size(); i++) mobs.get(i).destroy();
	check("obj", mn == "a fixg17 genmob", mn);
	// GENERATE CHECK compiles js: conditions and ${js:...}
	write(TB, '<AREADATA><STRING id="fixg17_b1" condition="js:defined.(">x</STRING><STRING id="fixg17_b2">${js:1+}</STRING><STRING id="fixg17_b3" condition="js:1 &lt; 2">${js:\'ok\'}</STRING></AREADATA>');
	var probs = s(CM.core.GenerateCheck.check("randareas/fixg17bad.xml"));
	check("generate check", has(probs, "fixg17_b1") && has(probs, "CONDITION js:defined.(") && has(probs, "fixg17_b2") && has(probs, "${js:1+}") && !has(probs, "fixg17_b3"), probs);
	// JSCRIPTS=0: nothing runs
	var was = CMProps.getIntVar(CMProps.Int.JSCRIPTS);
	CMProps.setIntVar(CMProps.Int.JSCRIPTS, 0);
	var off1, off2;
	try { off1 = str("fixg17_a"); off2 = str("fixg17_off"); }
	finally { CMProps.setIntVar(CMProps.Int.JSCRIPTS, was); }
	check("off", off1.trim() == "and" && off2.trim() == "always", off1 + " / " + off2);
} finally {
	T["delete"](); TB["delete"](); L["delete"]();
}
done();
