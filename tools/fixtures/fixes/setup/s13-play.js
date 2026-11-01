// S13, in play: a sentinel in the room north, to be saved there
FIXFILE = "s13-play";
var R = otherRoom();
var S = newMob("GenMob", R);
S.setName("a fix sentinel");
S.setDisplayText("A fix sentinel stands guard here.");
check("placed", S.location() == R, "not placed");
done();
