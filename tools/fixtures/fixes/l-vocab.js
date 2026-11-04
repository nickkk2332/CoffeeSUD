// L2, L3, L7-L9, L14: local vocabulary, creature/weapon text and world-owned content overlays.
FIXFILE="l-vocab";
var V=CM.core.Vocabulary, files={}, area=CMClass.getAreaType("StdArea"),parent=CMClass.getAreaType("StdArea");
area.setName("FixVocabularyArea");parent.setName("FixVocabularyParent");CMLib.map().addArea(area);CMLib.map().addArea(parent);area.addParent(parent);parent.addChild(area);
var R=CMClass.getLocale("StdRoom");R.setRoomID(area.Name()+"#0");R.setArea(area);area.addProperRoom(R);var N=newMob("GenMob",R);N.setName("FixVocabularyMob");
function put(path,text){if(files[path]===undefined)files[path]={had:V.file(path).isFile(),text:String(V.read(path))};V.write(path,text);}
function prop(h,id,text){var p=CMClass.getAbility(id);p.setMiscText(text);h.addNonUninvokableEffect(p);return p;}
try{
 put("vocab/glossary.ini","mana=aether\ngold coin(s)=crown(s)\nHit Points=vigor;SCOPE=SCORE\nspell=working;SCOPE=HELP\nFINAL spark=ember\nFINAL rune=sigil;SCOPE=HELP\narchon=warden;NOT: Archon Hall\n");
 var terms=String(V.glossary("Mana MANA mana manage gold coins gold coin(s) Archon Hall Archon","ALL",false));
 check("whole word and case",terms.indexOf("Aether AETHER aether manage")>=0,terms);
 check("plurals",terms.indexOf("crowns crown(s)")>=0,terms);
 check("exceptions",terms.indexOf("Archon Hall Warden")>=0,terms);
 check("help scope",String(V.scopedOutput("spell hit points rune","HELP"))=="working hit points sigil",V.scopedOutput("spell hit points rune","HELP"));
 check("score scope",String(V.scopedOutput("spell hit points","SCORE"))=="spell vigor","SCORE scope failed");
 check("final only",String(V.glossary("spark mana","ALL",false))=="spark aether"&&String(V.glossary("spark mana","ALL",true))=="ember mana","FINAL scope failed");
 check("markup intact",String(V.glossary('^<HELP mana^>mana^</HELP^>',"ALL",false))=='^<HELP mana^>aether^</HELP^>',"markup corrupted");
 var glossaryTime=V.file("vocab/glossary.ini").lastModified();V.write("vocab/glossary.ini","mana=concentration\n");V.file("vocab/glossary.ini").setLastModified(glossaryTime);
 check("glossary reload with preserved timestamp",String(V.glossary("mana","ALL",false))=="concentration","updated file remained cached");
 // Remove the glossary before checking stock output in the remainder of this game.
 V.file("vocab/glossary.ini")["delete"]();
 var pp=prop(parent,"Prop_LocalLists",'MAGIC_WORDS="parentword"'),ap=prop(area,"Prop_LocalLists",'MAGIC_WORDS="areaword"'),rp=prop(R,"Prop_LocalLists",'MAGIC_WORDS="roomword"'),mp=prop(N,"Prop_LocalLists",'MAGIC_WORDS="mobword"');
 check("mob precedence",String(V.local(N,"MAGIC_WORDS"))=="mobword","wrong mob priority");N.delEffect(mp);
 check("room precedence",String(V.local(N,"MAGIC_WORDS"))=="roomword","wrong room priority");R.delEffect(rp);
 check("area precedence",String(V.local(N,"MAGIC_WORDS"))=="areaword","wrong area priority");area.delEffect(ap);
 check("parent fallback",String(V.local(N,"MAGIC_WORDS"))=="parentword","parent ignored");parent.delEffect(pp);
 check("global fallback",V.local(N,"MAGIC_WORDS")==null,"local value leaked");
 put("vocab/fixlists.ini","TOD_CHANGE_INSIDE=dark one,dark two,dark three,dark four\nHEALTH_CHART=<MOB> zero,<MOB> one,<MOB> two,<MOB> three,<MOB> four,<MOB> five,<MOB> six,<MOB> seven,<MOB> eight,<MOB> nine,<MOB> perfect\nMAGIC_WORDS=moonword\nCORPSE_BLURBS=ALL=petals\n");
 rp=prop(R,"Prop_LocalLists","LOAD=vocab/fixlists.ini");
 check("loaded list",String(CMProps.getListFileChoiceFromIndexedList(CMProps.ListFile.TOD_CHANGE_INSIDE,2,N))=="dark three","LOAD/index failed");
 N.curState().setHitPoints(N.maxState().getHitPoints());check("local health chart",String(CMLib.combat().standardMobCondition(N,N)).indexOf("perfect")>=0,"health context ignored");
 var wand=CMClass.getItem("StdWand");N.addItem(wand);check("local magic word",String(wand.magicWord())=="moonword",wand.magicWord());wand.destroy();
 check("local corpse",String(V.corpse(N,null,null))=="petals","local corpse text ignored");
 var race=CMClass.getRace("GenRace").copyOf();race.setStat("ID","FixVocabularyRace");race.setStat("HEALTHCHART","<MOB> breaks,<MOB> leaks,<MOB> cracks,<MOB> sags,<MOB> chips,<MOB> dents,<MOB> rattles,<MOB> ticks,<MOB> shines,<MOB> gleams,<MOB> runs perfectly");race.setStat("CORPSEBLURBS","FIRE=molten gears;ALL=a heap of springs");N.baseCharStats().setMyRace(race);N.recoverCharStats();
 check("race health",String(race.healthText(N,N))=="FixVocabularyMob runs perfectly",race.healthText(N,N));
 check("race corpse wins",String(V.corpse(N,null,null))=="a heap of springs","race override ignored");
 var race2=CMClass.getRace("GenRace").copyOf();race2.setRacialParms(race.racialParms());check("race XML",String(race2.getStat("HEALTHCHART"))==String(race.getStat("HEALTHCHART"))&&String(race2.getStat("CORPSEBLURBS"))==String(race.getStat("CORPSEBLURBS")),"race vocabulary lost");
 var w=CMClass.getWeapon("GenWeapon");w.setName("Fixbrand");prop(w,"Prop_WeaponMessages",'HIT="<S-NAME> sear(s) <T-NAMESELF> with <TOOLNAME>: <DAMAGE>." MISS="<S-NAME> flicker(s) past <T-NAMESELF>." DAMAGEWORDS="singes,singes,singes,singes,singes,singes,singes,singes,singes"');
 check("weapon hit",String(w.hitString(5)).indexOf("Fixbrand: singes")>=0,w.hitString(5));check("weapon miss",String(w.missString()).indexOf("flicker(s)")>=0,w.missString());
 w.setWeaponDamageType(CM.Items.interfaces.Weapon.TYPE_BASHING);w.fetchEffect("Prop_WeaponMessages").setMiscText('DAMAGEWORDS="ALL,genericword;BASHING,specificword"');
 check("specific damage words before ALL",String(w.hitString(5)).indexOf("specificword")>=0,w.hitString(5));
 w.setWeaponDamageType(CM.Items.interfaces.Weapon.TYPE_PIERCING);check("ALL damage fallback",String(w.hitString(5)).indexOf("genericword")>=0,w.hitString(5));
 w.fetchEffect("Prop_WeaponMessages").setMiscText('DAMAGEWORDS="PIERCING,singlerowword"');check("single typed damage row",String(w.hitString(5)).indexOf("singlerowword")>=0,w.hitString(5));
 rp.setMiscText('LOAD=vocab/fixlists.ini DAMAGE_WORDS="ALL,genericword;BURNING,localburn"');
 var damageRecorder=scriptOn(R,"EXECMSG_PROG DAMAGE ALL\nmpsetvar FIXTEST FIXVDAMAGE $%MSG(OTEXT)%\n~\n"),victim=newMob("GenMob",R);clearVar("FIXVDAMAGE");
 CMLib.combat().postDamage(N,victim,null,1,CMMsg.TYP_DAMAGE|CMMsg.MASK_ALWAYS,CM.Items.interfaces.Weapon.TYPE_BURNING,"<S-NAME> <DAMAGE> <T-NAMESELF>.");runQueued(damageRecorder);
 check("ability damage uses local words",gvar("FIXVDAMAGE").toLowerCase().indexOf("localburn")>=0,gvar("FIXVDAMAGE"));R.delScript(damageRecorder);victim.destroy();N.makePeace(true);
 w.delEffect(w.fetchEffect("Prop_WeaponMessages"));mp=prop(N,"Prop_LocalLists",'WEAPON_HIT_DESCS="NATURAL,NATURAL,NATURAL,NATURAL,NATURAL,NATURAL"');
 check("unowned natural context",String(V.hit(w,N,5))=="NATURAL",V.hit(w,N,5));N.delEffect(mp);w.destroy();
 // World socials are a complete, predictable overlay; builder saves stay under vocab/.
 put("vocab/socials.ini","STOCK=NONE\n");put("vocab/socials.txt","ss\tFIXGREET\tYou greet the moon.\t<S-NAME> greet(s) the moon.\t\t\t\t\t\n");CMLib.socials().unloadSocials();
 check("world social",CMLib.socials().fetchSocial("FIXGREET",true)!=null,"world social not loaded");check("stock pruned",CMLib.socials().fetchSocial("SMILE",true)==null,"stock not pruned");
 CMLib.socials().save(M);check("builder social destination",V.file("vocab/socials.txt").isFile()&&String(V.read("vocab/socials.ini")).indexOf("NONE")>=0,"social editor destination");
 put("vocab/help/a_fix.ini","FIXVOCAB=First world text.\nSCORE=World score explanation.\n");put("vocab/help/z_fix.ini","FIXVOCAB=Last world text.\n");put("vocab/help/hide.txt","CLANS\n");CMLib.help().unloadHelpFile(null);
 check("world help sorted",String(CMLib.help().getHelpFile().getProperty("FIXVOCAB"))=="Last world text.","world help order");check("world help wins",String(CMLib.help().getHelpFile().getProperty("SCORE"))=="World score explanation.","stock overrode world");
 check("hidden help",CMLib.help().getHelpFile().getProperty("CLANS")==null,"hidden key present");check("help provenance",String(CM.core.WorldHelp.source("FIXVOCAB"))=="world/vocab/help/z_fix.ini",CM.core.WorldHelp.source("FIXVOCAB"));
 check("builder help destination",String(CM.core.WorldHelp.editPath(false))=="world/vocab/help/world_help.ini","help editor path");
}finally{
 for(var path in files){if(files[path].had)V.write(path,files[path].text);else V.file(path)["delete"]();}CMLib.socials().unloadSocials();CMLib.help().unloadHelpFile(null);
 N.destroy();R.destroy();CMLib.map().delArea(area);CMLib.map().delArea(parent);area.destroy();parent.destroy();
}
done();
