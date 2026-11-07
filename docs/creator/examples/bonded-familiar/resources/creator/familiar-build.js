// The bonded familiar: a charm that calls an ember wisp on a kill, and moths to practise on in the training stones.
var CM = Packages.com.planet_ink.coffee_mud, CC = CM.core.CMClass, CL = CM.core.CMLib;
if (!cm.isBuilder()) throw new Error('Use the builder deployment.');
var camp = CL.map().getRoom('EmberTrial#0'), yard = CL.map().getRoom('EmberTrial#1');
var wisp = CC.getMOB('GenMob'); wisp.setName('an ember wisp'); wisp.setDisplayText('An ember wisp drifts here.');
wisp.basePhyStats().setLevel(1); wisp.recoverPhyStats(); wisp.text(); CL.catalog().addCatalog(wisp); wisp.destroy();
CL.database().DBUpdateMOB('CATALOG_MOBS', CL.catalog().getCatalogMob('an ember wisp')); // as CATALOG EDIT saves its data
var charm = CC.getItem('GenArmor'); charm.setName("a familiar's charm"); charm.setDisplayText("A familiar's charm glints in the ash.");
charm.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_NECK); charm.basePhyStats().setWeight(0); charm.recoverPhyStats();
var grant = CC.getAbility('Prop_Triggers'); grant.setMiscText('TRIGGERS=familiar.call'); charm.addNonUninvokableEffect(grant);
camp.addItem(charm);
for (var i = 0; i < 2; i++) {
	var moth = CC.getMOB('GenMob'); moth.setName('a cinder moth'); moth.setDisplayText('A cinder moth flutters here.');
	moth.basePhyStats().setLevel(1); moth.basePhyStats().setRejuv(CM.Common.interfaces.PhyStats.NO_REJUV); moth.recoverPhyStats();
	moth.setStartRoom(yard); moth.bringToLife(yard, true); moth.baseState().setHitPoints(2); moth.recoverMaxState(); moth.resetToMaxState();
}
CL.database().DBUpdateItems(camp); CL.database().DBUpdateMOBs(yard);
mob().tell("FAMILIAR BUILT: a familiar's charm at camp; two cinder moths in the training stones; an ember wisp in the catalog");
