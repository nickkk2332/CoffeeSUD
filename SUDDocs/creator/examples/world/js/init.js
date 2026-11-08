// Code registrations are recreated at boot; only JSON data belongs in stores.
function onBoot() {
    cm.command('museumbell', {help: 'MUSEUMBELL: ring the museum bell.', cost: 1}, function (who, args) {
        if (String(who.location().getArea().Name()) !== 'CreatorMuseum') {
            cm.tell(who, 'There is no museum bell here.');
            return;
        }
        var data = cm.store('world');
        data.museumBells = (data.museumBells || 0) + 1;
        cm.tell(who, 'Museum bell count: ' + data.museumBells);
    });
    cm.command('museumstatus', {help: 'MUSEUMSTATUS: inspect the example story state.', cost: 0}, function (who, args) {
        cm.tell(who, 'Museum bell count: ' + (cm.store('world').museumBells || 0));
    });
}
