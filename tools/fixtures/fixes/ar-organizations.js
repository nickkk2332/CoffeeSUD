// A seat occupied by another same-name NPC still counts against the rank cap.
FIXFILE='ar-organizations';
var area=CMClass.getAreaType('StdArea');area.setName('ArRankSeats');CMLib.map().addArea(area);
var r=CMClass.getLocale('StdRoom');r.setRoomID('ArRankSeats#0');r.setArea(area);area.addProperRoom(r);
var clan=CMClass.getCommon('DefaultClan');clan.setName('ArSeatGuild');clan.setGovernmentID(0);CMLib.clans().addClan(clan);
var gov=clan.getGovernment().copyOf(),positions=gov.getPositions(),high=positions[0],low=positions[0],n1=newMob('GenMob',r),n2=newMob('GenMob',r);
function setfield(o,name,v){for(var c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,v);return;}catch(e){}throw 'missing '+name;}
try {
 for(var i=0;i<positions.length;i++){if(positions[i].getRank()<high.getRank())high=positions[i];if(positions[i].getRank()>low.getRank())low=positions[i];positions[i].setInnerMaskStr('');}
 high.setMax(1);gov.setAutoRole(low.getRoleID());gov.setAcceptPos(low.getRoleID());gov.setAutoPromoteBy(CM.Common.interfaces.Clan.AutoPromoteFlag.MERIT);setfield(clan,'government',new java.lang.Integer(-1));setfield(clan,'govt',gov);
 n1.setName('a nameless guard');n2.setName('a nameless guard');n1.setClan(clan.clanID(),high.getRoleID());n2.setClan(clan.clanID(),low.getRoleID());
 CM.core.Organizations.merit(n2,clan);
 check('same-name incumbent fills capped seat',n2.getClanRole(clan.clanID()).second.intValue()!=high.getRoleID(),'second identical guard took occupied rank');
 CM.core.Organizations.merit(n1,clan);check('candidate excludes only itself',n1.getClanRole(clan.clanID()).second.intValue()==high.getRoleID(),'incumbent lost own seat');
 n1.setClan(clan.clanID(),-1);CM.core.Organizations.merit(n2,clan);check('vacated seat becomes available',n2.getClanRole(clan.clanID()).second.intValue()==high.getRoleID(),'vacated rank unavailable');
} finally {n1.destroy();n2.destroy();CMLib.clans().removeClan(clan);CMLib.map().delArea(area);area.destroy();}
done();
