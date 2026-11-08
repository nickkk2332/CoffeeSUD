// The branching expedition: a generated crypt (its layout, maze, tree, branch, clump or cave, drawn from the chart's seed) behind a chart-sealed stair west of camp,
// and a chart and a lantern at camp. The expedition completes when every foe inside is defeated.
var CM = Packages.com.planet_ink.coffee_mud, CC = CM.core.CMClass, CL = CM.core.CMLib, D = CM.core.Directions;
if (!cm.isBuilder()) throw new Error('Use the builder deployment.');
var camp = CL.map().getRoom('EmberTrial#0');
var A = CC.getAreaType('StdAutoGenInstance'); A.setName('Branching Depths'); CL.map().addArea(A); CL.database().DBCreateArea(A);
A.setStat('GENERATIONFILEPATH', 'randareas/crypt.xml'); A.setStat('OTHERVARS', 'AREA_ID=crypt_dungeon areasize=5 theme=crypt');
var stair = CC.getLocale('StoneRoom'); stair.setRoomID('Branching Depths#0'); stair.setArea(A);
stair.setDisplayText('A chart-sealed stair'); stair.setDescription('Worn steps go down into the branching depths. Only an opened chart leads on.');
CL.database().DBCreateRoom(stair);
camp.setRawExit(D.WEST, CC.getExit('Open')); camp.rawDoors()[D.WEST] = stair;
stair.setRawExit(D.EAST, CC.getExit('Open')); stair.rawDoors()[D.EAST] = camp;
CL.database().DBUpdateExits(camp); CL.database().DBUpdateExits(stair); CL.database().DBUpdateArea(A.Name(), A);
var chart = CC.getItem('GenItem'); chart.setName('a branching chart'); chart.setDisplayText('A branching chart lies weighted by a stone.');
var P = CC.getAbility('Prop_ExpeditionChart'); P.setMiscText('EXPEDITION=branch.depths SEED=0 TIER=1'); chart.addNonUninvokableEffect(P);
camp.addItem(chart);
// the crypt's rooms are dark: a lantern at camp, lit by holding it
var lantern = CC.getItem('Lantern'); lantern.setDisplayText('A hooded lantern hangs from a hook by the stair.'); camp.addItem(lantern);
CL.database().DBUpdateItems(camp);
mob().tell('BRANCH BUILT: the Branching Depths west of camp; a branching chart and a hooded lantern at camp');
