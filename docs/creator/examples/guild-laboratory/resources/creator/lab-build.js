// The guild laboratory: a bench (the guild-lab station) at the Ember Trial's camp, and the mantle it makes.
var CM = Packages.com.planet_ink.coffee_mud, CC = CM.core.CMClass, CL = CM.core.CMLib;
if (!cm.isBuilder()) throw new Error('Use the builder deployment.');
var camp = CL.map().getRoom('EmberTrial#0');
var bench = CC.getItem('GenItem'); bench.setName('a laboratory bench'); bench.setDisplayText('A laboratory bench stands by the brazier.');
bench.basePhyStats().setWeight(500); CL.flags().setGettable(bench, false); bench.recoverPhyStats();
var station = CC.getAbility('Prop_Workstation'); station.setMiscText('TAG=guild-lab'); bench.addNonUninvokableEffect(station);
camp.addItem(bench);
var mantle = CC.getItem('GenArmor'); mantle.setName('a lab mantle'); mantle.setDisplayText('A lab mantle lies folded here.');
mantle.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_ABOUT_BODY); mantle.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_COTTON);
mantle.basePhyStats().setWeight(1); mantle.recoverPhyStats();
CL.catalog().addCatalog(mantle); mantle.destroy();
CL.database().DBUpdateItem('CATALOG_ITEMS', CL.catalog().getCatalogItem('a lab mantle')); // as CATALOG EDIT saves its data
CL.database().DBUpdateItems(camp);
mob().tell('LAB BUILT: a laboratory bench at camp; a lab mantle in the catalog');
