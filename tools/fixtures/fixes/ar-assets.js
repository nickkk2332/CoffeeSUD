// Help precedence/deletion, complete asset tracking and file/line validation.
FIXFILE="ar-assets";
var V=CM.core.Vocabulary,WH=CM.core.WorldHelp,WF=CM.core.save.WorldFiles,WA=CM.core.WorldAssets;
var saved={},paths=['vocab/help/world_help.ini','vocab/help/arc_world_help.ini','vocab/help/zzz_ar.ini','vocab/help/arc_zzz_ar.ini','vocab/help/deleted.txt','vocab/help/hide.txt','vocab/glossary.ini','lore/ar.ini','score.txt','resources/ar-reload-test.txt'];
paths.forEach(function(path){saved[path]={exists:V.file(path).isFile(),body:String(V.read(path))};});
function has(x,s){return String(x).indexOf(s)>=0;}
function normal(){var p=new java.util.Properties();p.setProperty('AR_STOCK','stock');WH.merge(p,false);return p;}
try {
 V.write('vocab/help/zzz_ar.ini','AR_TOPIC=late file\nAR_STOCK=reintroduced\n');V.write('vocab/help/world_help.ini','AR_TOPIC=builder edit\n');V.write('vocab/help/hide.txt','AR_STOCK\n');
 var p=normal();check('builder overlay loads last',String(p.getProperty('AR_TOPIC'))=='builder edit',p);
 check('stock hides permit world reintroduction',String(p.getProperty('AR_STOCK'))=='reintroduced',p);
 check('help provenance matches winner',String(WH.source('AR_TOPIC')).endsWith('world_help.ini'),WH.source('AR_TOPIC'));
 WH.prepare('world/vocab/help/zzz_ar.ini','AR_TOPIC',true);p=normal();check('delete defeats all world definitions',p.getProperty('AR_TOPIC')==null,p);
 WH.deleted(false,'AR_TOPIC',false);check('recreate clears final deletion',String(normal().getProperty('AR_TOPIC'))=='builder edit',normal());
 V.write('vocab/help/arc_zzz_ar.ini','AR_TOPIC=arc late\n');V.write('vocab/help/arc_world_help.ini','AR_TOPIC=arc edit\n');WH.prepare('world/vocab/help/arc_zzz_ar.ini','AR_TOPIC',true);
 var arc=new java.util.Properties();WH.merge(arc,true);check('arc deletion separate from normal',arc.getProperty('AR_TOPIC')==null&&normal().getProperty('AR_TOPIC')!=null,arc);
 WA.reload();var before=String(WF.checksum());V.write('lore/ar.ini','AR_ENTRY=Test|Test|Text\n');var added=String(WF.checksum());
 check('lore additions change world checksum',before!=added,'untracked lore');check('asset additions reported',String(WF.changedOutside().get('lore/ar.ini'))=='added',WF.changedOutside());
 V.write('lore/ar.ini','AR_ENTRY=Test|Test|Changed\n');check('asset edits change world checksum',String(WF.checksum())!=added,'untracked edit');
 var plan=WF.plan();check('SAVE leaves author assets alone',!plan.deletes.contains('lore/ar.ini')&&!plan.writes.contains('lore/ar.ini'),'asset treated as generated');
 WA.reload();V.file('lore/ar.ini')['delete()']();check('asset deletions reported',String(WF.changedOutside().get('lore/ar.ini'))=='deleted',WF.changedOutside());
 V.write('score.txt','New presentation\n');check('score asset tracked',WF.fileSums().containsKey('score.txt'),'score absent');
 V.write('resources/ar-reload-test.txt','Subsystem definition\n');WA.reload();check('resource changes remain until subsystem reload',WF.changedOutside().containsKey('resources/ar-reload-test.txt'),'vocabulary reload falsely acknowledged resource definition');
 V.write('lore/ar.ini','AR_BAD=Broken|Test|FILE=missing-ar.txt\n');var errors=CM.core.AuthoringCheck.run('LORE',null);check('missing lore file has source line',has(errors,'world/lore/ar.ini:1')&&has(errors,'missing lore file'),errors);
 V.write('vocab/glossary.ini','bad line\nSCOPE=UNKNOWN\n');errors=CM.core.AuthoringCheck.run('VOCAB',null);check('glossary line diagnostics',has(errors,'world/vocab/glossary.ini:1')&&has(errors,'unknown glossary scope'),errors);
} finally {paths.forEach(function(path){if(saved[path].exists)V.write(path,saved[path].body);else V.file(path)['delete()']();});WA.reload();}
done();
