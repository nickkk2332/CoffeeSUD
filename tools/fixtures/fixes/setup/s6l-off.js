// S6: with #STOCK OFF in the world's section, only the world's rules run
FIXFILE = "s6l-off";
function pre(words) {
	var L = new java.util.ArrayList();
	for (var i = 0; i < words.length; i++) L.add(words[i]);
	return String(CMLib.lang().preCommandParser(L));
}
var hop = pre(["fixhop"]);
check("world-rule", hop.indexOf("look") >= 0, "fixhop became " + hop);
var stock = pre(["it", "puts", "x", "in", "y"]);
check("stock-off", stock.indexOf("from") < 0, "it puts became " + stock);
done();
