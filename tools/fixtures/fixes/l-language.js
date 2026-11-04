// L1, L4, L6, L10, L11, L13: the same knowledge drives dialogue, inscriptions and learning.
FIXFILE="l-language";
var Lang=CM.core.Languages, R=scratchRoom(), P=newMob("GenMob",R), N=newMob("GenMob",R);
P.setName("Fixlangplayer");P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));N.setName("Fixlangorc");
function prop(h,id,text){var a=CMClass.getAbility(id);a.setMiscText(text);h.addNonUninvokableEffect(a);return a;}
function num(x){return Number(x);}
function text(x){return String(x);}
function hear(words){var msg=CMClass.getMsg(P,N,null,CMMsg.MSG_SPEAK,"<S-NAME> say(s) '"+words+"'.");R.okMessage(P,msg);R.send(P,msg);runQueued(S);return msg;}
var S=scriptOn(N,"SPEECH_PROG hello\nmpsetvar FIXTEST FIXLSPEECH yes\n~\nNOTUNDERSTOOD_PROG Common p ???\nmpsetvar FIXTEST FIXLUNKNOWN $g\nmpsetvar FIXTEST FIXLGARBLED $0\nif isspeaking($n Common)\nmpsetvar FIXTEST FIXLSPOKE common\nendif\n~\nFUNCTION_PROG lesson\nmplearnlang $n Elvish 45 FORCE\nmplearnlang $n Elvish +15\nmpsetvar FIXTEST FIXLBOOL $%UNDERSTANDS($n Elvish)%\nmpsetvar FIXTEST FIXLNUM $%UNDERSTANDS$($n Elvish)%\nif understands($n Elvish)\nmpsetvar FIXTEST FIXLTEST yes\nendif\nmpenable $n Elvish ++5\nmpsayin Goblinese hello stranger\n~\n");
try {
 check("default Common",num(CMLib.utensils().languageProficiency(N,"Common"))==100,"default comprehension");
 var mono=prop(N,"Prop_Monolingual",'LANGUAGES=Orcish CONFUSED="<S-NAME> shrug(s)."');
 check("language selected on attachment",text(Lang.spoken(N))=="Orcish"&&N.fetchEffect("Orcish")!=null,Lang.spoken(N));
 var order=CMClass.getMsg(P,N,null,CMMsg.TYP_ORDER,"<S-NAME> order(s) 'discuss philosophy'.");
 var orderOK=N.fetchEffect("Orcish").okMessage(N,order);check("Common order blocked before speech",!orderOK||order.targetMinor()!=CMMsg.TYP_ORDER,"first interaction bypassed comprehension");
 check("strict Common",!CMLib.utensils().understands(N,"Common"),"Common bypassed restriction");
 check("declared language",num(Lang.proficiency(N,"Orcish"))==100,"own language missing");
 ["FIXLSPEECH","FIXLUNKNOWN","FIXLGARBLED","FIXLSPOKE"].forEach(clearVar);hear("hello outsider");
 check("speech blocked",gvar("FIXLSPEECH")=="","Common reached SPEECH_PROG");
 check("not understood trigger",gvar("FIXLUNKNOWN").toLowerCase()=="common",gvar("FIXLUNKNOWN"));
 check("garbled argument",gvar("FIXLGARBLED").indexOf("hello")<0&&gvar("FIXLGARBLED").length>0,gvar("FIXLGARBLED"));
 check("trigger language",gvar("FIXLSPOKE")=="common",gvar("FIXLSPOKE"));
 var anyScript=scriptOn(N,"NOTUNDERSTOOD_PROG\nmpsetvar FIXTEST FIXLANY yes\n~\n"),patternScript=scriptOn(N,"NOTUNDERSTOOD_PROG p ???\nmpsetvar FIXTEST FIXLPATTERN yes\n~\n");clearVar("FIXLANY");clearVar("FIXLPATTERN");hear("hello again");runQueued(anyScript);runQueued(patternScript);
 check("unqualified not understood trigger",gvar("FIXLANY")=="yes",gvar("FIXLANY"));check("pattern without a language",gvar("FIXLPATTERN")=="yes",gvar("FIXLPATTERN"));N.delScript(anyScript);N.delScript(patternScript);
 check("NPC replies in own language",text(Lang.spoken(N))=="Orcish",Lang.spoken(N));
 var translator=prop(N,"Prop_LangTranslator","100% Common NOTRANSLATE");
 check("translator restores Common",CMLib.utensils().understands(N,"Common"),"translator ignored");
 hear("hello interpreter");check("understood speech trigger",gvar("FIXLSPEECH")=="yes",gvar("FIXLSPEECH"));N.delEffect(translator);N.delEffect(mono);
 S.callFunction(N,"lesson","",P);
 check("learn stored",P.fetchAbility("Elvish")!=null&&num(P.fetchAbility("Elvish").proficiency())==65,"stored proficiency");
 check("enable sync",P.fetchEffect("Elvish")!=null&&num(P.fetchEffect("Elvish").proficiency())==65,"live proficiency");
 check("understands boolean",gvar("FIXLBOOL")=="true"&&gvar("FIXLTEST")=="yes",gvar("FIXLBOOL")+":"+gvar("FIXLTEST"));
 check("understands numeric",gvar("FIXLNUM")=="60",gvar("FIXLNUM"));
 check("sayin restores",text(Lang.spoken(N))=="Orcish"&&N.fetchEffect("Goblinese")==null,"temporary language leaked");
 var related=CMClass.getAbility("GenLanguage").copyOf();related.setStat("CLASS9","FixRelatedLanguage");related.setStat("INTERPRETS","Elvish(60),Sylvan(30)");related.setProficiency(50);N.addNonUninvokableEffect(related);
 check("partial related",num(Lang.proficiency(N,"Elvish"))==30&&num(Lang.proficiency(N,"Sylvan"))==15,Lang.proficiency(N,"Elvish"));
 N.delEffect(related);R.addNonUninvokableEffect(related);
 check("room interpreter",num(Lang.proficiency(N,"Elvish"))==30,"room effect ignored");R.delEffect(related);
 var charm=CMClass.getItem("GenItem");N.addItem(charm);charm.addNonUninvokableEffect(related);
 check("carried interpreter",num(Lang.proficiency(N,"Elvish"))==30,"item effect ignored");charm.destroy();
 // A normal READ uses the room interpreter even without an exact language effect.
 var parchment=CMClass.getItem("GenReadable");R.addItem(parchment);parchment.setReadableText("hello friend at the gate");var written=CMClass.getAbility("Elvish");parchment.addNonUninvokableEffect(written);
 var roomTranslator=prop(R,"Prop_LangTranslator","100% Elvish NOTRANSLATE");var readmsg=CMClass.getMsg(N,parchment,null,CMMsg.TYP_WASREAD,null,CMMsg.TYP_WASREAD,"hello friend at the gate",CMMsg.NO_EFFECT,null);
 check("reading accepts room interpreter",R.okMessage(N,readmsg)&&text(readmsg.sourceMessage()).indexOf("hello friend at the gate")>=0,text(readmsg.sourceMessage()));
 var tome=CMClass.getItem("GenTome");tome.setReadableText('<PAGE TITLE="Words" LANG=Elvish>hello friend at the gate</PAGE>');
 check("tome accepts room interpreter",text(tome.show(N,1)).indexOf("hello friend at the gate")>=0,text(tome.show(N,1)));tome.destroy();R.delEffect(roomTranslator);parchment.destroy();
 var tag='^<LANG Elvish NOTE="old runes"^>hello friend at the gate^</LANG^>';
 var unread=text(Lang.inline(N,tag,false));check("unknown inline",unread.indexOf("old runes")>=0&&unread.indexOf("hello friend")<0,unread);
 var coloredNote=text(Lang.inline(N,'^<lang\tElvish NOTE="^Rold runes^N"^>hello friend at the gate^</lang^>',false));check("colored inline note and whitespace",coloredNote.indexOf("old runes")>=0&&coloredNote.indexOf("hello friend")<0&&coloredNote.indexOf("^<lang")<0,coloredNote);
 Lang.learn(N,"Elvish","100",true);var read=text(Lang.inline(N,tag,false));
 var filtered=text(CMLib.coffeeFilter().fullOutFilter(null,N,N,null,null,tag,false));check("inline output filter",filtered.indexOf("hello friend at the gate")>=0&&filtered.indexOf("LANG Elvish")<0,filtered);
 check("known inline",read.indexOf('you read: "hello friend at the gate"')>=0&&read.indexOf("translated from")>=0,read);
 var partial=text(Lang.render("Elvish","the quick brown fox jumps over the lazy dog near a river with water",50));
 check("partial rendering",partial!="the quick brown fox jumps over the lazy dog near a river with water"&&partial==text(Lang.render("Elvish","the quick brown fox jumps over the lazy dog near a river with water",50)),partial);
 var imm=prop(R,"Prop_LanguageImmersion","LANGUAGE=Goblinese HEAR=2 READ=3 CHANCE=100% START=5 MAX=10 GRANTAFTER=2");
 Lang.expose(P,"Goblinese","HEAR",N);check("immersion waits",P.fetchAbility("Goblinese")==null,"granted early");
 Lang.expose(P,"Goblinese","READ",N);check("immersion grants",P.fetchAbility("Goblinese")!=null&&num(P.fetchEffect("Goblinese").proficiency())==5,"not granted");
 Lang.expose(P,"Goblinese","READ",N);Lang.expose(P,"Goblinese","READ",N);
 check("immersion cap",num(P.fetchAbility("Goblinese").proficiency())==10&&num(P.fetchEffect("Goblinese").proficiency())==10,"cap or synchronization failed");
 check("exposure saved",text(CM.core.StoryRecords.read(P)).indexOf("languageExposure")>=0,"counter not serialized");R.delEffect(imm);
 var carried=CMClass.getItem("GenItem");P.addItem(carried);prop(carried,"Prop_LanguageImmersion","LANGUAGE=Draconic READ=1 CHANCE=100% START=4 MAX=10 GRANTAFTER=2");
 var moved=scratchRoom();moved.setRoomID(R.getArea().Name()+"#99961");Lang.expose(P,"Draconic","READ",carried);moved.bringMobHere(P,false);Lang.expose(P,"Draconic","READ",carried);
 check("carried immersion follows movement",P.fetchAbility("Draconic")!=null&&num(P.fetchAbility("Draconic").proficiency())==4,"moving reset the exposure count");R.bringMobHere(P,false);carried.destroy();moved.destroy();
 var cap=CMProps.getIntVar(CMProps.Int.MAXLANGUAGES);CMProps.setIntVar(CMProps.Int.MAXLANGUAGES,1);
 try {check("language cap",Lang.learn(P,"Dwarven","10",false)!=null&&P.fetchAbility("Dwarven")==null,"cap bypassed");check("force",Lang.learn(P,"Dwarven","10",true)==null&&num(P.fetchEffect("Dwarven").proficiency())==10,"force failed");}finally{CMProps.setIntVar(CMProps.Int.MAXLANGUAGES,cap);}
 var observer=newMob("GenMob",R),PS=CM.Common.interfaces.PhyStats;observer.basePhyStats().setSensesMask(PS.CAN_SEE_DARK);observer.recoverPhyStats();
 ["Elvish","SignLanguage","Semaphore"].forEach(function(id){
  Lang.learn(P,id,"100",true);Lang.learn(N,id,"100",true);Lang.ensureSpoken(P,id);N.basePhyStats().setSensesMask(PS.CAN_SEE_DARK|(id=="Elvish"?0:PS.CAN_NOT_HEAR));N.recoverPhyStats();
  var speech=CMClass.getMsg(P,N,null,CMMsg.MSG_SPEAK,"<S-NAME> say(s) 'hello friend'."),allowed=R.okMessage(P,speech);if(allowed)Lang.heard(N,speech);
  var trailers=speech.trailerMsgs(),privateText="";if(trailers!=null)for(var ti=0;ti<trailers.size();ti++){var t=trailers.get(ti);if(t.target()==N&&t.othersCode()==CMMsg.NO_EFFECT)privateText+=text(t.targetMessage());}
  check(id+" translated speech",allowed&&privateText.indexOf("hello friend")>=0,privateText);
  var count=trailers==null?0:trailers.size();Lang.heard(observer,speech);check(id+" private translation",(speech.trailerMsgs()==null?0:speech.trailerMsgs().size())==count&&text(speech.othersMessage()).indexOf("hello friend")<0,"bystander received clear speech");
  var document=CMClass.getItem("GenReadable");R.addItem(document);document.setReadableText("hello friend");document.addNonUninvokableEffect(CMClass.getAbility(id));
  var unknown=CMClass.getMsg(observer,document,null,CMMsg.TYP_WASREAD,null,CMMsg.TYP_WASREAD,"hello friend",CMMsg.NO_EFFECT,null);R.okMessage(observer,unknown);
  check(id+" unknown writing",text(unknown.sourceMessage()).indexOf("hello friend")<0,text(unknown.sourceMessage()));
  var known=CMClass.getMsg(N,document,null,CMMsg.TYP_WASREAD,null,CMMsg.TYP_WASREAD,"hello friend",CMMsg.NO_EFFECT,null);R.okMessage(N,known);check(id+" known writing",text(known.sourceMessage()).indexOf("hello friend")>=0,text(known.sourceMessage()));document.destroy();
 });observer.destroy();

} finally {P.setPlayerStats(null);P.destroy();N.destroy();R.destroy();}
done();
