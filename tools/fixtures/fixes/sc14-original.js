// Run the original engine self-test with its documented shop and two-room setup.
FIXFILE='sc14-original';
var A=CMClass.getAreaType('StdArea');A.setName('UNKNOWNAREA');CMLib.map().addArea(A);
A.setTimeObj(CMLib.time().globalClock().copyOf());
var R=CMClass.getLocale('WoodRoom'), N=CMClass.getLocale('WoodRoom');
R.setRoomID('UNKNOWN1');N.setRoomID('UNKNOWN2');R.setArea(A);N.setArea(A);
R.rawDoors()[CM.core.Directions.NORTH]=N;R.setRawExit(CM.core.Directions.NORTH,CMClass.getExit('Open'));
N.rawDoors()[CM.core.Directions.SOUTH]=R;N.setRawExit(CM.core.Directions.SOUTH,CMClass.getExit('Open'));
var shoppy=CMClass.getMOB('GenShopkeeper');shoppy.setName('Shoppy');shoppy.bringToLife(R,true);
shoppy.getShop().addStoreInventory(CMClass.getWeapon('Dagger'),10,-1);shoppy.getShop().addStoreInventory(CMClass.getWeapon('Shortsword'),10,-1);
var S=scriptOn(shoppy,'LOAD=progs/scriptableTest.script');
var oldChannels=''+CMProps.instance().getStr('CHANNELS');
CMLib.channels().loadChannels('GOSSIP, '+oldChannels);
try {
 A.getTimeObj().setDayOfMonth(1);A.getTimeObj().setHourOfDay(1);
 for(var i=0;i<25;i++){shoppy.tick(shoppy,Tickable.TICKID_MOB);S.dequeResponses(null);}
 var errors=''+S.getVar('Shoppy','ERRORS'),success=''+S.getVar('Shoppy','SUCCESS');
 check('original self-test completed',errors.trim()=='NONE!',errors+' successes: '+success);
 check('original commands exercised',success.indexOf('MPSETVAR')>=0 && success.indexOf('MPWHILE')>=0 && success.indexOf('INAREA')>=0,success);
} finally {CMLib.map().obliterateMapArea(A);CMLib.channels().loadChannels(oldChannels);}
done();
