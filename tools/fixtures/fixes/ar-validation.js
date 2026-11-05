// WORLD CHECK exposes fatal definition errors without creating runtime discovery state.
FIXFILE='ar-validation';
var area=CMClass.getAreaType('StdArea');area.setName('ArValidation');CMLib.map().addArea(area);
var r=CMClass.getLocale('StdRoom');r.setRoomID('ArValidation#0');r.setArea(area);area.addProperRoom(r);
function ap(h,id,s){var a=CMClass.getAbility(id);a.setMiscText(s);h.addNonUninvokableEffect(a);return a;}
function has(x,s){return String(x).indexOf(s)>=0;}
var i=CMClass.getItem('GenItem'),j=CMClass.getItem('GenItem');i.setName('first ar marker');j.setName('second ar marker');r.addItem(i);r.addItem(j);
var V=CM.core.Vocabulary,path='routes/ar-invalid.xml',had=V.file(path).isFile(),old=String(V.read(path));
try {
 ap(i,'Prop_Waypoint','ID=AR_DUP NETWORK=AR NAME="First"');ap(j,'Prop_Waypoint','ID=AR_DUP NETWORK=AR NAME="Second"');ap(r,'Prop_Senses','TOUCH="CHANCE=0" CHANCE=101');
 ap(area,'Prop_LocalLists','HEALTH_CHART="one,two" LOAD=vocab/ar-missing.ini');
 V.write(path,'<AR_ROUTE>\n<STOPS><STOP1>ArValidation#0</STOP1><STOP2>MissingArStop#5</STOP2></STOPS>\n</AR_ROUTE>');
 var parts=new java.util.HashSet();['IDS','VOCAB','TEMPLATES','ROUTES'].forEach(function(p){parts.add(p);});var got=CM.core.WorldCheck.run(null,parts),errors=0;
 for(var e=got.iterator();e.hasNext();)if(e.next().severity==CM.core.WorldCheck.Severity.ERROR)errors++;
 check('new domains emit fatal errors',errors>=4,got);
 check('typed prose and range validated',has(got,'CHANCE must')&&!has(got,'unknown option CHANCE'),got);
 check('missing files distinct from fallback',has(got,'missing file vocab/ar-missing.ini')&&has(got,'HEALTH_CHART needs 11'),got);
 check('duplicate waypoint ID rejected',has(got,'duplicate Prop_Waypoint ID AR_DUP'),got);
 check('route stop has file and line',has(got,'world/routes/ar-invalid.xml:2')&&has(got,'MissingArStop#5'),got);
 check('lint does not assign saved identities',i.fetchEffect('Prop_AuthorIdentity')==null&&j.fetchEffect('Prop_AuthorIdentity')==null&&r.fetchEffect('Prop_AuthorIdentity')==null,'validator mutated sources');
} finally {if(had)V.write(path,old);else V.file(path)['delete()']();CMLib.map().delArea(area);area.destroy();}
done();
