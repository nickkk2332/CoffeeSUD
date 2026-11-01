// S15, before the save: an airship, the Gull, with a captain, two sailors
// and a crate, its anchor up and on course east three times; and a ship of
// the player's own, the Wren, with one sailor
FIXFILE = "s15-play";
var Directions = CM.core.Directions;
var R = M.location();
var gull = CMClass.getItem("GenAirShip");
gull.setName("the Gull");
gull.setDisplayText("The airship Gull floats here.");
R.addItem(gull);
gull.dockHere(R);
CMLib.map().registerWorldObjectLoaded(R.getArea(), R, gull);
gull.rename("the Gull");
var deck = gull.getArea().getProperMap().nextElement();
function crew(room, name) {
	var m = newMob("GenMob", room);
	m.setName(name);
	m.setDisplayText(name + " is here.");
	m.setStartRoom(room);
	return m;
}
var captain = crew(deck, "a captain");
captain.curState().setHitPoints(7); // hurt, which the vessel's text does not keep
crew(deck, "a sailor");
crew(deck, "a sailor");
var crate = CMClass.getItem("GenItem");
crate.setName("a crate of fish");
crate.setDisplayText("A crate of fish sits here.");
deck.addItem(crate);
gull.setAnchorDown(false);
var course = new java.util.ArrayList();
for (var i = 0; i < 3; i++) course.add(java.lang.Integer.valueOf(Directions.EAST));
gull.setCurrentCourse(course);
check("gull", (deck.numInhabitants() == 3) && (deck.numItems() >= 1), deck.numInhabitants() + " aboard, " + deck.numItems() + " items");
check("course", (!gull.isAnchorDown()) && (gull.getCurrentCourse().size() > 0), "anchor " + gull.isAnchorDown() + ", course " + gull.getCurrentCourse());
var wren = CMClass.getItem("GenSailingShip");
wren.setName("the Wren");
wren.setDisplayText("The Wren is here.");
R.addItem(wren);
wren.dockHere(R);
CMLib.map().registerWorldObjectLoaded(R.getArea(), R, wren);
wren.rename("the Wren");
wren.setOwnerName(M.Name());
wren.setSavable(false);
M.playerStats().getExtItems().addItem(wren);
crew(wren.getArea().getProperMap().nextElement(), "a deckhand");
check("wren", M.playerStats().getExtItems().isContent(wren), "the Wren is not the player's");
done();
