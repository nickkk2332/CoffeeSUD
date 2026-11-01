// S6: the world's parser rules run before the stock ones: the world's
// "fixhop" means look, and the stock "it puts X in Y" still means put
FIXFILE = "s6l-parse";
function pre(words) {
	var L = new java.util.ArrayList();
	for (var i = 0; i < words.length; i++) L.add(words[i]);
	return String(CMLib.lang().preCommandParser(L));
}
var hop = pre(["fixhop"]);
check("world-rule", hop.indexOf("look") >= 0, "fixhop became " + hop);
var stock = pre(["it", "puts", "x", "in", "y"]);
check("stock-rule", (stock.indexOf("put") >= 0) && (stock.indexOf("from") >= 0), "it puts became " + stock);
done();
