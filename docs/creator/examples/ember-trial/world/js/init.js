// World-owned example code. Nothing here is installed in the default world.
// Only JSON/typed module data saves; callbacks are registered again at boot.
var CM = Packages.com.planet_ink.coffee_mud;
var CC = CM.core.CMClass, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles;
var OM = CM.core.OptionalModules, MD = CM.core.ModuleDefinitions;
var MODULE = 'embertrial.adventure';
function active() { return OM.enabled(MODULE) && OM.enabled('combat.profiles'); }
function inside(who) { return who != null && who.location() != null && String(who.location().roomID()).indexOf('EmberTrial#') === 0; }
function read(owner) { return JSON.parse(String(MD.json(OM.get(MODULE,owner)))); }
function write(owner,data) { OM.put(MODULE,owner,MD.parse(JSON.stringify(data))); }
function story() { return read(OM.world()); }
function hero(who) { return read(OM.character(who)); }
function cleared() { return JSON.parse(story().cleared); }
function room(number) { return cm.room('EmberTrial#' + number); }
function setting(name) { return String(OM.setting(MODULE,name)) === 'true'; }
function tag(who,name) { return who.findTattoo('EMBERTRIAL_' + name) != null; }
function stopBurn(who) { var burn = who.fetchEffect('CompositionBurn'); if (burn != null) burn.unInvoke(); }

function initializeHero(who) {
    if (!active() || cm.isBuilder() || who == null || who.isMonster()) return;
    var data = hero(who); if (data.initialized) return;
    var skill = CC.getAbility('Skill_Compose'); skill.setProficiency(100); who.addAbility(skill);
    who.baseState().setHitPoints(140); who.baseState().setMana(180); who.baseState().setMovement(300);
    who.recoverMaxState(); who.resetToMaxState();
    AC.learn(who,'embertrial.ember'); AC.learn(who,'embertrial.blade'); AC.learn(who,'embertrial.transmute');
    data.initialized = true; write(OM.character(who),data);
    cm.tell(who,'THE EMBER TRIAL: recover the cooling heart. SAY lessons, or TRIAL HELP. Your three composed skills are ready; supports are your choice.');
}
function onNewGame(who) {
    if (!active() || cm.isBuilder()) return;
    initializeHero(who);
    var data = story(); if (data.initialized) return;
    // NPC loadouts belong to the campaign, not to the builder's source world.
    for (var i = 0; i < 7; i++) {
        var r = room(i);
        for (var n = 0; n < r.numInhabitants(); n++) {
            var m = r.fetchInhabitant(n);
            if (tag(m,'ENEMY')) AC.learn(m,tag(m,'SENTINEL') ? 'embertrial.slam' : 'embertrial.scorch');
        }
    }
    data.initialized = true; write(OM.world(),data);
}
function onPlayerEnter(who) { if (inside(who)) initializeHero(who); }
function onRestore(who,slot) {
    if (active()) cm.tell(who,'Ember Trial restored: progress, equipment and chosen supports are kept. TRIAL STATUS reviews the expedition.');
}
function status(who) {
    var done = cleared(), data = hero(who);
    cm.tell(who,'Ember Trial journal: ' + (done.RED && done.BLUE ? 'The cinder road is clear.' : 'Defeat both ash creatures on the cinder road.')
        + '\nSlag Sentinel: ' + (done.SENTINEL ? 'defeated' : 'guarding the furnace')
        + '\nFurnace Warden: ' + (done.WARDEN ? 'defeated' : 'guarding the heart')
        + '\nCooling heart: ' + (data.claimed ? 'claimed - expedition complete' : 'unclaimed')
        + '\nRescues: ' + data.rescues + '. Profile: ' + CP.current().key());
}
function helpText() {
    return 'TRIAL STATUS: your expedition journal. TRIAL REST: recover at camp, without resetting cooldowns.'
        + '\nTRIAL RESET: restore training effigies. TRIAL LEAVE: leave training combat for camp.'
        + '\nTRIAL RULES FINAL/HISTORY: choose a conversion preset at camp. TRIAL CLAIM: take the heart in the vault.'
        + '\nStart east: COMPOSE LINK embertrial.ember embertrial.burning; COMPOSE CAST embertrial.ember ward effigy; COMBATRESULT.'
        + '\nFor the road add embertrial.chain and embertrial.power. COMPOSE SHOW embertrial.ember explains the costs.'
        + '\nNorth: two ash creatures; east from them: equipment; then north past sentinel and warden to the heart.'
        + '\nUse GET, WEAR and REMOVE. COMBATRESULT STATS shows current defenses; WAIT 2 lets a cooldown/burn advance.'
        + '\nSAVE trial keeps your adventure. Lethal damage returns you to camp while safe_death is enabled.';
}
function help(who) { cm.tell(who,helpText()); }
function command(who,args) {
    if (!active() || !inside(who)) { cm.tell(who,'The Ember Trial module is not active here.'); return; }
    args = String(args).trim(); args = args ? args.split(/\s+/) : [];
    var sub = args.length ? String(args[0]).toLowerCase() : 'help';
    var here = String(who.location().roomID());
    if (sub === 'help') { help(who); return; }
    if (sub === 'status') { status(who); return; }
    if (sub === 'rest') {
        if (here !== 'EmberTrial#0' || who.isInCombat() || !setting('camp_rest')) { cm.tell(who,'Rest at the safe camp, outside combat.'); return; }
        stopBurn(who); who.resetToMaxState(); cm.tell(who,'The brazier restores your health, mana and movement. Cooldowns keep their normal clock.'); return;
    }
    if (sub === 'reset' || sub === 'leave') {
        if (here !== 'EmberTrial#1') { cm.tell(who,'Use that command in the training yard.'); return; }
        who.makePeace(true);
        for (var i = 0; i < room(1).numInhabitants(); i++) {
            var dummy = room(1).fetchInhabitant(i);
            if (tag(dummy,'DUMMY')) { dummy.makePeace(true); stopBurn(dummy); dummy.resetToMaxState(); }
        }
        if (sub === 'leave') room(0).bringMobHere(who,false);
        cm.tell(who,sub === 'leave' ? 'You leave the training stones and return to camp.' : 'The training effigies are restored.'); return;
    }
    if (sub === 'rules') {
        var choice = args.length > 1 ? String(args[1]).toLowerCase() : '';
        if (here !== 'EmberTrial#0' || who.isInCombat() || !setting('allow_profile_switch')) { cm.tell(who,'Choose rules at camp, outside combat.'); return; }
        if (choice !== 'final' && choice !== 'history') { cm.tell(who,'TRIAL RULES FINAL or TRIAL RULES HISTORY.'); return; }
        cm.tell(who,String(CP.use('example.' + choice,1))); return;
    }
    if (sub === 'claim') {
        if (here !== 'EmberTrial#6' || !cleared().WARDEN) { cm.tell(who,'The cooling heart lies in the vault beyond the Warden.'); return; }
        var data = hero(who); if (data.claimed) { cm.tell(who,'You have already claimed your cooling heart.'); return; }
        var heart = CC.getItem('GenItem'); heart.setName('the cooling heart'); heart.setDisplayText('A cooling heart glows softly.');
        heart.setDescription('A keepsake from the completed Ember Trial.'); heart.basePhyStats().setWeight(1); heart.recoverPhyStats();
        who.addItem(heart); data.claimed = true; write(OM.character(who),data);
        who.addTattoo('EMBERTRIAL_COMPLETE'); cm.tell(who,'EMBER TRIAL COMPLETE: you claim the cooling heart. SAVE trial preserves your victory.'); return;
    }
    help(who);
}
function onBoot() {
    cm.command('trial',{help:helpText(),cost:0},command);
    cm.on('beforeEnterRoom',function(who,to,from) {
        if (!active() || who == null || who.isMonster()) return;
        var id = String(to.roomID()); if (id !== 'EmberTrial#4' && id !== 'EmberTrial#5' && id !== 'EmberTrial#6') return;
        var done = cleared();
        if (id === 'EmberTrial#4' && !(done.RED && done.BLUE)) { cm.tell(who,'Clear both ash creatures before approaching the slag gate.'); return false; }
        if (id === 'EmberTrial#5' && !done.SENTINEL) { cm.tell(who,'The Slag Sentinel bars the furnace passage.'); return false; }
        if (id === 'EmberTrial#6' && !done.WARDEN) { cm.tell(who,'The Furnace Warden bars the vault.'); return false; }
    },'embertrial.gates');
    cm.on('death',function(victim,killer) {
        if (!active() || !tag(victim,'ENEMY') || killer == null || killer.isMonster()) return;
        var names = ['RED','BLUE','SENTINEL','WARDEN'], data = story(), done = JSON.parse(data.cleared);
        for (var i = 0; i < names.length; i++) if (tag(victim,names[i]) && !done[names[i]]) {
            done[names[i]] = true; cm.tell(killer,'Ember Trial journal updated: ' + String(victim.Name()) + ' defeated.');
        }
        data.cleared = JSON.stringify(done); write(OM.world(),data);
    },'embertrial.progress');
    cm.on('beforeDeath',function(victim,killer) {
        if (!active() || !inside(victim)) return;
        if (tag(victim,'DUMMY')) return false;
        if (victim.isMonster() || !setting('safe_death')) return;
        victim.makePeace(true); stopBurn(victim); room(0).bringMobHere(victim,false);
        var data = hero(victim); data.rescues++; write(OM.character(victim),data);
        cm.tell(victim,'The keeper pulls you back to camp, alive with your equipment. TRIAL REST restores your resources.');
        return false;
    },'embertrial.rescue');
}
