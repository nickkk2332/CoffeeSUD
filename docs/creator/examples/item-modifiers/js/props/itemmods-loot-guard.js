// Keep optional tracked loot unspent while its required native module is off.
// Attach beside Prop_LootTable: SCRIPT=world:js/props/itemmods-loot-guard.js
var CM = Packages.com.planet_ink.coffee_mud;
function okMessage(host, msg) {
    if (msg.amITarget(host) && msg.targetMinor() === CM.Common.interfaces.CMMsg.TYP_OPEN
            && !CM.core.OptionalModules.enabled('items.modifiers')) {
        var loot = host.fetchEffect('Prop_LootTable');
        if (loot == null || String(loot.getStat('ROLLED')) === 'TRUE') return true;
        msg.source().tell('This coffer waits while the item-modifier module is inactive. Its loot remains unspent.');
        return false;
    }
    return true;
}
