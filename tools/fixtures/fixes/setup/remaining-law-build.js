FIXFILE='remaining-law-build';
function command(id,words){var c=new java.util.ArrayList();words.forEach(function(w){c.add(w);});CMClass.getCommand(id).execute(M,c,0);}
command('TestCharCmd',['TESTCHAR','RemainingParty','PARTY']);
var companion=CM.core.Party.findInWorld(M,'RemainingParty');
command('PartyCmd',['PARTY','MORTAL','RemainingParty']);
check('party member mortal',companion!=null && CMSecurity.isPlaytesting(companion),'mortal mode missing');
command('PartyCmd',['PARTY','ARCHON','RemainingParty']);
check('party member archon',companion!=null && !CMSecurity.isPlaytesting(companion) && CMSecurity.isASysOp(companion),'archon mode missing');
command('PlaytestCmd',['PLAYTEST','ON','PARTY=RemainingParty','DIFFICULTY=EASY']);
check('preset applies to both party members',CMSecurity.isPlaytesting(M) && CMSecurity.isPlaytesting(companion),'party powers differ');
command('PlaytestCmd',['PLAYTEST','OFF']);
check('party powers restored',!CMSecurity.isPlaytesting(M) && !CMSecurity.isPlaytesting(companion),'party mode left behind');
command('PartyCmd',['PARTY','REST','RemainingParty']);
var A=M.location().getArea();if(A.fetchBehavior('Arrest')==null)A.addBehavior(CMClass.getBehavior('Arrest'));
var words=new java.util.ArrayList();['LAW','SET','SALESTAX=7.25'].forEach(function(w){words.add(w);});CMClass.getCommand('LawCmd').execute(M,words,0);
check('custom law updated',(''+CMLib.law().getLegalBehavior(M.location()).legalInfo(A).getInternalStr('SALESTAX'))=='7.25','tax not applied');
check('custom law file written',WorldFiles.lawFile(A.Name()).isFile(),'law file missing');
done();
