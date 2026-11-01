// DT17: PREVIEW reads a room's <VARIES> text under chosen conditions, changing nothing; CHECKTEXT finds what would print as text.
FIXFILE = "dt17-text";
var TC = CM.core.TextCheck;
var cmd = CMClass.getCommand("TextToolsCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
function words(s) { var L = new java.util.ArrayList(); if (s.length > 0) { var w = s.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); } return L; }
function preview(R, s) { return "" + call(cmd, "preview", [M, R, words(s)]); }

var R = scratchRoom();
var title = "<VARIES><NIGHT>A dark square</NIGHT><DAY>A sunny square</DAY><DAWN>A grey square</DAWN><DUSK>A red square</DUSK></VARIES>";
var desc = "<VARIES>The square.<WINTER> Snow covers it.</WINTER><SUMMER> It bakes.</SUMMER><RAIN> Rain falls.</RAIN>"
	+ "<FROMNORTH> You came down the north road.</FROMNORTH><FROMUNKNOWN> You came from nowhere known.</FROMUNKNOWN>"
	+ "<VISITED> You know it well.</VISITED></VARIES>";
R.setDisplayText(title);
R.setDescription(desc);
var hour = CMLib.time().globalClock().getHourOfDay();

check("night", has(preview(R, "NIGHT"), "A dark square") && !has(preview(R, "NIGHT"), "sunny"), preview(R, "NIGHT"));
check("day", has(preview(R, "DAY"), "A sunny square"), preview(R, "DAY"));
check("winter", has(preview(R, "NIGHT WINTER SNOW"), "Snow covers it.") && !has(preview(R, "NIGHT WINTER"), "It bakes."), preview(R, "NIGHT WINTER"));
check("rain", has(preview(R, "RAIN"), "Rain falls.") && !has(preview(R, "CLEAR"), "Rain falls."), preview(R, "RAIN"));
check("from", has(preview(R, "FROM north"), "north road") && !has(preview(R, "FROM south"), "north road"), preview(R, "FROM north"));
check("fromunknown", has(preview(R, "FROM unknown"), "from nowhere known"), preview(R, "FROM unknown"));
check("visited", has(preview(R, "VISITED"), "know it well") && !has(preview(R, "FIRSTVISIT"), "know it well"), preview(R, "VISITED"));
check("said", has(preview(R, "NIGHT WINTER"), "PREVIEW night, winter:"), preview(R, "NIGHT WINTER"));
check("bad", has(preview(R, "TUESDAY"), "'TUESDAY' is no condition"), preview(R, "TUESDAY"));
check("unchanged", ("" + R.displayText()) == title && CMLib.time().globalClock().getHourOfDay() == hour, "the room or the clock changed");

// CHECKTEXT
function problems(t) { return "" + TC.checkText(t); }
check("ok", problems(title) == "[]" && problems(desc) == "[]", problems(title) + problems(desc));
check("not-a-tag", has(problems("<VARIES><NIGTH>x</NIGTH></VARIES>"), "<NIGTH> is not a tag"), problems("<VARIES><NIGTH>x</NIGTH></VARIES>"));
check("lowercase", has(problems("<VARIES><night>dark</night></VARIES>"), "<night>: write it <NIGHT>"), problems("<VARIES><night>dark</night></VARIES>"));
check("open", has(problems("<VARIES><NIGHT>dark</VARIES>"), "<NIGHT> is not closed"), problems("<VARIES><NIGHT>dark</VARIES>"));
check("close", has(problems("<VARIES>dark</NIGHT></VARIES>"), "</NIGHT> closes no <NIGHT>"), problems("<VARIES>dark</NIGHT></VARIES>"));
check("no-varies", has(problems("<NIGHT>dark</NIGHT>"), "does not start with <VARIES>"), problems("<NIGHT>dark</NIGHT>"));
check("colour", has(problems("A ^rred^N and ^ggreen square"), "the colour ^g is not closed") && problems("A ^rred^N square") == "[]", problems("A ^rred^N and ^ggreen square"));
check("mask", has(problems("<VARIES><MASK -LEVEL +>5>big</MASK></VARIES>"), "the mask has a '>' in it"), problems("<VARIES><MASK -LEVEL +>5>big</MASK></VARIES>"));

// the command, on the room the builder is in
var R0 = M.location();
var oldTitle = R0.displayText();
R0.setDisplayText("<VARIES><night>A fixes night</night></VARIES>");
var B = newMob("GenMob", R0);
B.setName("Fixtextreader");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
cmd.execute(B, words("CHECKTEXT"), 0);
check("cmd", has("" + field(BS, "bout").toString(), "title: <night>: write it <NIGHT>"), "" + field(BS, "bout").toString());
R0.setDisplayText(oldTitle);
B.setSession(null);
B.destroy();
done();
