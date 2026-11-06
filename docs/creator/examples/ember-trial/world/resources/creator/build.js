// Builder recipe. Run once in a new isolated deployment, then publish with SAVE.
var CM = Packages.com.planet_ink.coffee_mud;
var CC = CM.core.CMClass, CL = CM.core.CMLib, D = CM.core.Directions;
if (!cm.isBuilder()) throw new Error('Use the separate builder deployment.');
if (cm.area('EmberTrial') != null) throw new Error('EmberTrial already exists; edit it or make a fresh deployment.');

var area = CC.getAreaType('StdArea'); area.setName('EmberTrial');
CL.map().addArea(area); CL.database().DBCreateArea(area);
var titles = ['The pilgrims camp', 'The training stones', 'The cinder road',
              'The abandoned armory', 'The slag gate', 'The furnace hall', 'The heart vault'];
var descriptions = [
    'A keeper shelters beside a cooling brazier. East lies a safe training yard; north, the cinder road. SAY lessons or use TRIAL HELP. TRIAL REST restores your resources here. Recover the cooling heart from the vault beyond the furnace.',
    'Three bound effigies wait here: ward effigy resists fire, armored effigy resists physical hits, and blocking effigy can block spells and cannot burn. COMBATRESULT explains actual results. TRIAL RESET restores the effigies; TRIAL LEAVE returns you to camp.',
    'Two ash creatures guard the road. Chaining can strike both. The armory lies east; the slag gate lies north. Clear both creatures to proceed.',
    'Four surviving pieces of equipment lie among the racks. The two mantles share one wear slot: choose fire resistance or armor. The signet gives fire penetration; the bracers give critical chance. GET, WEAR, REMOVE and COMBATRESULT STATS expose the tradeoffs.',
    'A heavily armored sentinel guards the north passage. Fire avoids its physical armor. Its Slag hammer is a composed physical attack, so your own profile armor can help.',
    'The Furnace Warden waits before the vault. Its fire resistance rewards penetration; its Cinder spit rewards your fire resistance. FLEE south if you need to recover at camp.',
    'The cooling heart rests in a cradle of dark glass. TRIAL CLAIM completes the expedition and grants one keepsake per character. Save your campaign and restore it to keep your victory.'
];
var rooms = [];
for (var i = 0; i < titles.length; i++) {
    var r = CC.getLocale('StoneRoom'); r.setRoomID('EmberTrial#' + i); r.setArea(area);
    r.setDisplayText(titles[i]); r.setDescription(descriptions[i]); area.addProperRoom(r);
    CL.database().DBCreateRoom(r); rooms.push(r);
}
function join(a, direction, b, back) {
    rooms[a].setRawDoor(direction, rooms[b]); rooms[a].setRawExit(direction, CC.getExit('Open'));
    rooms[b].setRawDoor(back, rooms[a]); rooms[b].setRawExit(back, CC.getExit('Open'));
}
join(0,D.EAST,1,D.WEST); join(0,D.NORTH,2,D.SOUTH); join(2,D.EAST,3,D.WEST);
join(2,D.NORTH,4,D.SOUTH); join(4,D.NORTH,5,D.SOUTH); join(5,D.NORTH,6,D.SOUTH);
function stats(host, data) {
    data.schema = 1; var p = CC.getAbility('Prop_CombatStats');
    p.setMiscText(JSON.stringify(data)); host.addNonUninvokableEffect(p);
}
function mobAt(index, name, tag, hp, data, script) {
    var m = CC.getMOB('GenMob'); m.setName(name); m.setDisplayText(name + ' waits here.');
    m.setDescription('A creature of the abandoned furnace.'); m.addTattoo(tag);
    m.basePhyStats().setLevel(2); m.basePhyStats().setDamage(1);
    m.basePhyStats().setAttackAdjustment(-20); m.basePhyStats().setRejuv(CM.Common.interfaces.PhyStats.NO_REJUV);
    m.setStartRoom(rooms[index]);
    stats(m,data);
    // These adjustments live in the exported creature definition. Direct
    // baseState HP edits alone do not survive generic MOB imports/restores.
    var resources = CC.getAbility('Prop_Adjuster');
    resources.setMiscText('hitpoints+' + (hp - 10) + ' mana+1000');
    m.addNonUninvokableEffect(resources);
    if (script) {
        m.addTattoo('EMBERTRIAL_ENEMY'); var s = CC.getBehavior('Scriptable');
        s.setParms('LOAD=progs/' + script + '.script'); m.addBehavior(s);
        var skill = CC.getAbility('Skill_Compose'); skill.setProficiency(100); m.addAbility(skill);
    } else {
        m.basePhyStats().setSensesMask(CM.Common.interfaces.PhyStats.CAN_NOT_MOVE | CM.Common.interfaces.PhyStats.CAN_NOT_SPEAK);
    }
    m.recoverPhyStats(); m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState();
    m.bringToLife(rooms[index],true); return m;
}
mobAt(1,'ward effigy','EMBERTRIAL_DUMMY',1000,{resistance:{fire:50}},null);
mobAt(1,'armored effigy','EMBERTRIAL_DUMMY',1000,{armor:200},null);
mobAt(1,'blocking effigy','EMBERTRIAL_DUMMY',1000,{spell_block:50,immune:['burning']},null);
mobAt(2,'Ashling Red','EMBERTRIAL_RED',42,{},'embertrial-scorch');
mobAt(2,'Ashling Blue','EMBERTRIAL_BLUE',42,{},'embertrial-scorch');
mobAt(4,'Slag Sentinel','EMBERTRIAL_SENTINEL',52,{armor:150},'embertrial-slam');
mobAt(5,'Furnace Warden','EMBERTRIAL_WARDEN',100,{resistance:{fire:60}},'embertrial-scorch');

function equipment(name, slot, data, description) {
    var item = CC.getItem('GenArmor'); item.setName(name); item.setDisplayText(name + ' lies on a rack.');
    item.setDescription(description); item.setRawProperLocationBitmap(slot);
    item.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_COTTON);
    item.basePhyStats().setArmor(0); item.basePhyStats().setWeight(1); item.recoverPhyStats();
    stats(item,data); rooms[3].addItem(item);
}
var W = CM.Items.interfaces.Wearable;
equipment('an ember mantle',W.WORN_ABOUT_BODY,{resistance:{fire:50}},'Worn: +50 fire resistance. Shares a slot with the quilted mantle.');
equipment('a quilted mantle',W.WORN_ABOUT_BODY,{armor:120},'Worn: +120 profile armor, for physical composed hits.');
equipment('a piercing signet',W.WORN_RIGHT_FINGER,{penetration:{fire:20}},'Worn: +20 fire penetration.');
equipment('critical bracers',W.WORN_ARMS,{crit_chance:25,crit_multiplier:50},'Worn: +25 critical chance, +50 critical multiplier points.');

// Stage 5 reuses the ordinary OPEN loot-table flow and the existing workstation tag.
var coffer = CC.getItem('GenContainer'); coffer.setName('a supply coffer');
coffer.setDisplayText('A supply coffer rests beside an ember forge.');
coffer.setDescription('OPEN the coffer, then GET trial mantle coffer and GET cinder iron coffer. ITEMMODS trial mantle explains the generated modifiers.');
coffer.setDoorsNLocks(true,false,true,false,false,false); CL.flags().setGettable(coffer,false);
var loot = CC.getAbility('Prop_LootTable');
loot.setMiscText('FILE=randareas/embertrial-loot.xml ID=embertrial_supply ON=OPEN ROLLS=1');
coffer.addNonUninvokableEffect(loot); rooms[0].addItem(coffer);
var lootGuard = CC.getAbility('Prop_JScript');
lootGuard.setMiscText('SCRIPT=world:js/props/itemmods-loot-guard.js');
coffer.addNonUninvokableEffect(lootGuard);
var forge = CC.getItem('GenItem'); forge.setName('an ember forge');
forge.setDisplayText('An ember forge glows beside the supply coffer.');
forge.setDescription('MODCRAFT LIST shows the operations. PREVIEW or PREPARE an operation on an unworn item and modifier number, then COMMIT the request. Only COMMIT rolls and spends cinder iron.');
CL.flags().setGettable(forge,false);var station = CC.getAbility('Prop_Workstation');
station.setMiscText('TAG=ember-forge');forge.addNonUninvokableEffect(station);rooms[0].addItem(forge);

var guide = CC.getMOB('GenMob'); guide.setName('the ember keeper');
guide.setDisplayText('The ember keeper tends the cooling brazier.'); guide.setStartRoom(rooms[0]);
guide.basePhyStats().setRejuv(CM.Common.interfaces.PhyStats.NO_REJUV);
var speech = CC.getBehavior('Scriptable'); speech.setParms('LOAD=progs/embertrial-guide.script');
guide.addBehavior(speech); guide.bringToLife(rooms[0],true);
for (var j = 0; j < rooms.length; j++) {
    CL.database().DBUpdateRoom(rooms[j]); CL.database().DBUpdateExits(rooms[j]);
    CL.database().DBUpdateMOBs(rooms[j]); CL.database().DBUpdateItems(rooms[j]);
}
rooms[0].bringMobHere(mob(),false);
mob().tell('EMBER TRIAL BUILT: seven rooms, seven combat actors, four equipment choices. SAVE publishes this world.');
