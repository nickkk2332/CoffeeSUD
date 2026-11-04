FIXFILE="l-play";
var r=CMLib.map().getRoom("FixLanguageSave#0");r.bringMobHere(M,false);
var L=CMClass.getAbility("FixSaveLanguage");check("custom definition imported",L!=null&&String(L.writtenName())=="Saved Runes","custom language missing");check("dictionary in game",L!=null&&String(L.translate(L.ID(),"hello"))=="saal","world dictionary missing");
check("language help in game",String(CMLib.help().getHelpFile().getProperty("FIXSAVEDLANGUAGE"))=="FIX_SAVED_LANGUAGE_HELP","world help missing");
CM.core.Languages.expose(M,"FixSaveLanguage","READ",r);CM.core.Languages.expose(M,"FixSaveLanguage","READ",r);
check("before learning threshold",M.fetchAbility("FixSaveLanguage")==null,"granted early");
CM.core.Languages.learn(M,"Elvish","37",true);check("known proficiency before save",M.fetchAbility("Elvish").proficiency()==37&&M.fetchEffect("Elvish").proficiency()==37,"not synchronized");
check("saved exposures before grant",String(CM.core.StoryRecords.read(M)).indexOf("languageExposure")>=0,"exposures absent");done();
