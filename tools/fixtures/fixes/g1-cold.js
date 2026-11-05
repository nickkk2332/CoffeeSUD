// Cold-process generation includes crafted NPC equipment as well as room content.
FIXFILE='g1-cold';
function s(v){return ''+v;}
var R=M.location(),dir=-1,GS=CM.core.GenSeed;
for(var d=0;d<CM.core.Directions.NUM_DIRECTIONS();d++)if(dir<0&&R.getRoomInDir(d)==null&&R.getRawExit(d)==null)dir=d;
function itemSnapshot(I){return [I.ID(),I.Name(),I.material(),I.baseGoldValue(),I.basePhyStats().level(),I.basePhyStats().damage(),I.basePhyStats().armor(),I.rawWornCode()].join(':');}
function make(seed){
 var before={};for(var e=CMLib.map().areas();e.hasMoreElements();)before[s(e.nextElement().Name())]=true;
 var words=['GENERATE','NOSAVE','AREA','maze_dungeon','FROM','randareas/forest.xml','areaname=ColdSeedWoods','areasize=6','level_range=3','aggrochance=0','theme=forest','SEED='+seed,s(CMLib.directions().getDirectionName(dir))],L=new java.util.ArrayList();words.forEach(function(w){L.add(w);});
 CMClass.getCommand('Generate').execute(M,L,0);
 var A=null;for(var e=CMLib.map().areas();e.hasMoreElements();){var a=e.nextElement();if(!before[s(a.Name())])A=a;}
 var rooms=[];
 try {
  if(A==null)throw Error('area did not generate');
  for(var e=A.getProperMap();e.hasMoreElements();){var room=e.nextElement(),parts=[s(room.roomID()),s(room.displayText()),s(room.description())];
   for(var i=0;i<room.numItems();i++)parts.push(itemSnapshot(room.getItem(i)));
   for(var i=0;i<room.numInhabitants();i++){var m=room.fetchInhabitant(i),gear=[];for(var j=0;j<m.numItems();j++)gear.push(itemSnapshot(m.getItem(j)));gear.sort();parts.push([m.ID(),m.Name(),m.basePhyStats().level(),m.basePhyStats().damage(),m.basePhyStats().armor(),gear.join('/')].join('|'));}
   rooms.push(parts.join('|'));
  }
  rooms.sort();return rooms.join('\n');
 } finally {R.rawDoors()[dir]=null;R.setRawExit(dir,null);if(A!=null)CMLib.map().obliterateMapArea(A);}
}
[370170,13,14,91827].forEach(function(seed){var a=make(seed),b=make(seed);check('cold seed '+seed,a==b,'first and second builds differ: '+a.substring(0,200)+' / '+b.substring(0,200));var bytes=new java.lang.String(a).getBytes('UTF-8'),digest=java.security.MessageDigest.getInstance('SHA-256').digest(bytes);M.tell('COLDSEED '+seed+' '+java.util.Base64.getEncoder().encodeToString(digest));});
var rolls=CMath.getRandState();
GS.unseeded(new java.util.function.Supplier({get:function(){return CMLib.dice().roll(1,100,0);}}));
check('setup preserves campaign dice',rolls.equals(CMath.getRandState()),'initialization advanced the campaign stream');
done();
