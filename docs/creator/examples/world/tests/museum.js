// These tests run against the published museum in a disposable builder campaign.
test('both sides of the gate begin closed', function () {
    var a = cm.room('CreatorMuseum#0');
    var b = cm.room('CreatorMuseum#1');
    assert(a != null && b != null, 'Build and SAVE the museum first.');
    var directions = Packages.com.planet_ink.coffee_mud.core.Directions;
    assert(!a.getRawExit(directions.EAST).isOpen() && !b.getRawExit(directions.WEST).isOpen(), 'gate open at new-game start');
});
test('a player command opens both sides and changes the description', function () {
    var r = cm.room('CreatorMuseum#0');
    r.bringMobHere(mob(), false);
    // Local verbs intercept queued player input; cm.cmd uses the forced path.
    var words = Packages.com.planet_ink.coffee_mud.core.CMParms.parse('pull lever');
    mob().enqueCommand(words, 0, 0);
    var directions = Packages.com.planet_ink.coffee_mud.core.Directions;
    assert(r.getRawExit(directions.EAST).isOpen(), 'east did not open');
    assert(cm.room('CreatorMuseum#1').getRawExit(directions.WEST).isOpen(), 'west did not open');
    assert(String(r.description(mob())).indexOf('stands open') >= 0, 'conditional text did not change');
});
test('world-owned language loads on fresh startup', function () {
    var language = Packages.com.planet_ink.coffee_mud.core.CMClass.getAbility('MuseumSpeech');
    assert(language != null, 'language was not exported');
    assert.equal(String(language.translate(language.ID(), 'hello')), 'sela');
});
test('fresh generation and literal percent operators', function () {
    var rumour = String(gen.string('randareas/museum.xml', 'museum_rumour', {}));
    assert(rumour.indexOf('drowned bells') >= 0 || rumour.indexOf('old keeper') >= 0, rumour);
    assert.equal(String(gen.string('randareas/museum.xml', 'museum_modulo', {})), '2');
});
test('new commands use durable JSON state', function () {
    var old = Number(cm.store('world').museumBells || 0);
    cm.cmd(mob(), 'museumbell');
    assert.equal(cm.store('world').museumBells, old + 1);
});

test('the file-backed conversation passes the script checker', function () {
    var guide = cm.findMob('museum guide', cm.room('CreatorMuseum#0'));
    assert(guide != null, 'missing guide');
    var problems = Packages.com.planet_ink.coffee_mud.core.ScriptCheck.checkHost(guide, null);
    assert(problems.isEmpty(), String(problems));
});
