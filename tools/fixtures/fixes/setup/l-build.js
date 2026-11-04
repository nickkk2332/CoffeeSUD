FIXFILE="l-build";
var area=CMClass.getAreaType("StdArea");area.setName("FixLanguageSave");CMLib.map().addArea(area);CMLib.database().DBCreateArea(area);
var r=CMClass.getLocale("StoneRoom");r.setRoomID(area.Name()+"#0");r.setArea(area);r.setDisplayText("The language school");area.addProperRoom(r);
var immersion=CMClass.getAbility("Prop_LanguageImmersion");immersion.setMiscText("LANGUAGE=FixSaveLanguage HEAR=1 READ=2 CHANCE=100% START=7 MAX=11 GRANTAFTER=3");r.addNonUninvokableEffect(immersion);CMLib.database().DBCreateRoom(r);CMLib.database().DBUpdateRoom(r);
var L=CMClass.getAbility("GenLanguage").copyOf();L.setStat("CLASS","FixSaveLanguage");L.setStat("NAME","Fix Save Language");L.setStat("WORDFILE","fixsaved.txt");L.setStat("SUFFIXES","S:en");L.setStat("WRITTENNAME","Saved Runes");L.setStat("INTERPRETS","Elvish(40)");CMLib.database().DBCreateAbility(L.ID(),"GenLanguage",L.getStat("ALLXML"));
CM.core.Vocabulary.write("js/init.js","function onPlayerEnter(p){p.playerStats().getSetSecurityFlags('JSCRIPTS;FS:resources/_fixes');}\n");
CM.core.Vocabulary.write("vocab/languages/fixsaved.txt","HELLO=saal\nGATE=esh\n");CM.core.Vocabulary.write("vocab/help/fixsaved.ini","FIXSAVEDLANGUAGE=FIX_SAVED_LANGUAGE_HELP\n");
check("authored language",String(L.translate(L.ID(),"hello"))=="saal","dictionary missing");check("immersion room",r.fetchEffect("Prop_LanguageImmersion")!=null,"property missing");done();
