// Run once, in the isolated builder deployment. This is JRUN JavaScript.
// Use normal editors for subsequent changes; do not rebuild over existing rooms.
var CM = Packages.com.planet_ink.coffee_mud;
var CC = CM.core.CMClass;
var CL = CM.core.CMLib;
var D = CM.core.Directions;
if (!cm.isBuilder()) throw new Error('Open the isolated world with BUILDER=1.');
if (cm.area('CreatorMuseum') != null) throw new Error('CreatorMuseum already exists; edit it or create a fresh example deployment.');

var museum = CC.getAreaType('StdArea');
museum.setName('CreatorMuseum');
CL.map().addArea(museum);
CL.database().DBCreateArea(museum);
var rooms = [];
var titles = ['Museum entrance', 'The coast gallery', 'The quiet shrine'];
for (var i = 0; i < 3; i++) {
    var r = CC.getLocale('StoneRoom');
    r.setRoomID('CreatorMuseum#' + i);
    r.setArea(museum);
    r.setDisplayText(titles[i]);
    r.setDescription('A small museum of the drowned coast.');
    museum.addProperRoom(r);
    CL.database().DBCreateRoom(r);
    rooms.push(r);
}
function link(from, direction, to, exit) {
    from.setRawDoor(direction, to);
    from.setRawExit(direction, exit);
}
function gate() {
    var exit = CC.getExit('GenExit');
    exit.setName('an iron gate');
    exit.setDoorsNLocks(true, false, true, false, false, false);
    return exit;
}
link(rooms[0], D.EAST, rooms[1], gate());
link(rooms[1], D.WEST, rooms[0], gate());
link(rooms[1], D.NORTH, rooms[2], CC.getExit('Open'));
link(rooms[2], D.SOUTH, rooms[1], CC.getExit('Open'));
function property(host, id, text) {
    var effect = CC.getAbility(id);
    if (effect == null) throw new Error('Missing property ' + id);
    effect.setMiscText(text);
    host.addNonUninvokableEffect(effect);
    return effect;
}

var lever = CC.getItem('GenItem');
lever.setName('a brass lever');
lever.setDisplayText('A brass lever stands beside the east gate.');
lever.setDescription('A sturdy lever with two positions.');
CL.flags().setGettable(lever, false);
rooms[0].addItem(lever);
property(lever, 'Prop_Switch', 'STATES="down,up" START=down MSG_up="The east gate grinds open." MSG_down="The east gate grinds shut." '
    + 'ACTION_up="door open CreatorMuseum#0 east QUIET; setvar WORLD MUSEUM_GATE 1" '
    + 'ACTION_down="door close CreatorMuseum#0 east QUIET; setvar WORLD MUSEUM_GATE 0"');
rooms[0].setDescription('<VARIES><IFVAR MUSEUM_GATE=1>The east gate stands open.</IFVAR><ELSE>The east gate is closed.</ELSE></VARIES> A guide waits beside a brass lever.');

var guide = CC.getMOB('GenMob');
guide.setName('the museum guide');
guide.setDisplayText('The museum guide tends a ledger.');
guide.setDescription('A patient keeper of local history.');
guide.setStartRoom(rooms[0]);
var script = CC.getBehavior('Scriptable');
script.setParms('LOAD=progs/museum-guide.script');
guide.addBehavior(script);
guide.bringToLife(rooms[0], true);

property(rooms[1], 'Prop_Details', 'inscription,runes=Nine carved waves surround a seal. ID=museum-waves SETVAR=MUSEUM_RUNES=1');
property(rooms[1], 'Prop_Senses', 'ID=museum-gallery SOUND="The distant surf echoes through the stone." CHANCE=100');
var tablet = CC.getItem('GenItem');
tablet.setName('a stone tablet');
tablet.setDisplayText('A stone tablet rests on a plinth.');
tablet.setDescription('An account of the drowned coast.');
CL.flags().setGettable(tablet, false);
CL.flags().setReadable(tablet, true);
tablet.setReadableText('The old coast is lost beneath the sea.');
property(tablet, 'Prop_Lore', 'ENTRY=COAST ON=READ SCOPE=CHAR MSG="You learn the history of the coast."');
rooms[1].addItem(tablet);
property(rooms[2], 'Prop_FirstEntry', 'ID=museum-shrine TEXT="A single candle burns before the sea-worn altar." ONCE=CHARACTER');

var language = CC.getAbility('GenLanguage').copyOf();
language.setStat('CLASS', 'MuseumSpeech');
language.setStat('NAME', 'Museum Speech');
language.setStat('WORDFILE', 'museum.txt');
language.setStat('SYLLABLES', 'sa,ta,ma/la,ri,ven');
language.setStat('SUFFIXES', 'S:en');
language.setStat('WRITTENNAME', 'Coastal Runes');
CL.database().DBCreateAbility(language.ID(), 'GenLanguage', language.getStat('ALLXML'));

for (var j = 0; j < rooms.length; j++) {
    CL.database().DBUpdateRoom(rooms[j]);
    CL.database().DBUpdateExits(rooms[j]);
    CL.database().DBUpdateMOBs(rooms[j]);
    CL.database().DBUpdateItems(rooms[j]);
}
builder.cmd('WORLD SET START=CreatorMuseum#0');
rooms[0].bringMobHere(mob(), false);
mob().tell('CREATOR MUSEUM BUILT: review the rooms, then SAVE to publish.');
