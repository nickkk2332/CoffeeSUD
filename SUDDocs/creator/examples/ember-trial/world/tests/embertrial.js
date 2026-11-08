// Structural authoring contracts, separate from check.py's ordinary-player tour.
var CM = Packages.com.planet_ink.coffee_mud;
test('all seven authored rooms and their return paths exist', function() {
    for (var i = 0; i < 7; i++) assert(cm.room('EmberTrial#' + i) != null, 'missing room ' + i);
    var D = CM.core.Directions;
    assert.equal(String(cm.room('EmberTrial#6').getRoomInDir(D.SOUTH).roomID()),'EmberTrial#5');
    assert.equal(String(cm.room('EmberTrial#1').getRoomInDir(D.WEST).roomID()),'EmberTrial#0');
});
test('compositions and both reused profile presets validate', function() {
    assert(CM.core.AbilityComposition.check().errors.isEmpty(), 'composition schema');
    assert(CM.core.CombatProfiles.check().errors.isEmpty(), 'profile schema');
});
test('every creature script resolves through the resource overlay', function() {
    for (var i = 0; i < 7; i++) {
        var r = cm.room('EmberTrial#' + i);
        for (var n = 0; n < r.numInhabitants(); n++) {
            var problems = CM.core.ScriptCheck.checkHost(r.fetchInhabitant(n),null);
            assert(problems.isEmpty(),String(problems));
        }
    }
});
test('all authored stat properties validate without RNG or ID assignment', function() {
    var rooms = new java.util.ArrayList();
    for (var i = 0; i < 7; i++) rooms.add(cm.room('EmberTrial#' + i));
    var before = CM.core.CMath.getRandState();
    assert(CM.core.CombatProfiles.checkProperties(rooms).isEmpty(),'invalid stats');
    assert(before.equals(CM.core.CMath.getRandState()),'validation consumed randomness');
});
