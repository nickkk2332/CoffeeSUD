// N19: MudChat defects.
FIXFILE = "n19-mudchat";
var R = M.location();
var chatter = newMob("GenMob", R);
chatter.setName("a tavern drunk");
var B = CMClass.getBehavior("MudChat");
chatter.addBehavior(B);
var MC = B.getClass();
function parse(text) {
	return callTyped(null, MC, "parseChatData", ["StringBuffer"], [new java.lang.StringBuffer(text)]);
}
function tickMatch(entry, roll) {
	return callTyped(B, null, "match", ["MOB", "ChatExpression", "int"], [chatter, entry.expression, java.lang.Integer.valueOf(roll)]);
}

// <30> is a 30% chance: true for a roll of 10, false for 50
var groups = parse(">hiccups\n<30>\n9hic!\n<10|90>\n9burp\n");
var hic = groups[1].tickies[0];
check("tick-chance-low-roll", tickMatch(hic, 10) == true, "<30> missed a roll of 10");
check("tick-chance-high-roll", tickMatch(hic, 50) == false, "<30> fired on a roll of 50");
// | in a tick expression is OR
var burp = groups[1].tickies[1];
check("tick-or", tickMatch(burp, 50) == true, "<10|90> missed a roll of 50");

// an @ include gives the including group copies of the entries, as its own
groups = parse(">base\n(hello)\n9hi there\n>tavern\n@base\n");
var tavern = groups[2];
check("include-copies", tavern.entries.length == 1 && tavern.entries[0] != groups[1].entries[0], "the tavern shares the base group's own entry");
check("include-reparented", tavern.entries.length == 1 && tavern.entries[0].fromGroup == tavern, "the included entry belongs to its source group");

// ${PERSONALITY:3} picks up to three traits instead of throwing
var traits = null, err = "";
try { traits = callTyped(B, null, "getVarValue", ["Modifiable", "ChattyGroup", "String"], [chatter, null, "PERSONALITY:3"]); }
catch (e) { err = "" + e; }
check("personality-number", traits != null && ("" + traits).length > 0, "threw " + err);

// the speaker is remembered, so a conversation keeps its place
callTyped(B, null, "checkReactionReset", ["MOB"], [M]);
check("last-speaker", field(B, "lastSpeakerM") == M, "lastSpeakerM is " + field(B, "lastSpeakerM"));
chatter.destroy();
done();
