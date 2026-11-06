package com.planet_ink.coffee_mud.core;
import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Shared comprehension for speech, writing, scripts and inline inscriptions. */
public final class Languages
{
 private Languages() {}
 public interface Scrambler {String scrambleAll(String language,String text,int amount);}
 public interface Visual {}
 public interface SpeechTranslator {void translateFor(MOB listener,CMMsg message,String words);}
 public static Language language(final String id){final Ability a=CMClass.getAbility(id);if(a instanceof Language)return (Language)a;final Ability found=CMClass.findAbility(id);return found instanceof Language?(Language)found:null;}
 public static String id(final String name){final Language l=language(name);return l==null?name:l.ID();}
 public static List<String> restricted(final MOB who) {
  final List<String> out=new ArrayList<String>();if(who==null)return out;
  final Ability mono=who.fetchEffect("Prop_Monolingual"),speaker=who.fetchEffect("Prop_LanguageSpeaker");
  if(mono!=null)for(final String s:CMParms.parseCommas(Scenery.option(mono.text(),"LANGUAGES","Common"),true))out.add(id(s));
  else if(speaker!=null&&Scenery.option(speaker.text(),"UNDERSTANDS","").equalsIgnoreCase("ONLY"))out.add(id(Scenery.option(speaker.text(),"LANGUAGE","Common")));
  return out;
 }
 private static int offered(final Physical host,final String id,final String words) {
  if(host==null)return 0;int best=0;
  for(final Enumeration<Ability> e=host.effects();e.hasMoreElements();){final Ability a=e.nextElement();if(a instanceof Language){final Language l=(Language)a;if(l.translatesLanguage(id,words))best=Math.max(best,l.getProficiency(id));}}
  return best;
 }
 public static int proficiency(final MOB who,final String name){return proficiency(who,name,null);}
 public static int proficiency(final MOB who,final String name,final String words) {
  if(who==null)return 0;final String id=id(name);final List<String> only=restricted(who);int best=0;
  if(id.equalsIgnoreCase("Common")&&only.isEmpty()||only.stream().anyMatch(s->s.equalsIgnoreCase(id)))best=100;
  best=Math.max(best,offered(who,id,words));
  for(final Enumeration<Ability> e=who.abilities();e.hasMoreElements();){final Ability a=e.nextElement();if(a instanceof Language&&((Language)a).translatesLanguage(id,words))best=Math.max(best,((Language)a).getProficiency(id));}
  best=Math.max(best,offered(who.location(),id,words));
  for(final Enumeration<Item> e=who.items();e.hasMoreElements();)best=Math.max(best,offered(e.nextElement(),id,words));
  return Math.max(0,Math.min(100,best));
 }
 public static String spoken(final MOB who){final Language l=who==null?null:CMLib.utensils().getLanguageSpoken(who);return l==null?"Common":l.ID();}
 public static String messageLanguage(final CMMsg msg){return msg.tool() instanceof Language?msg.tool().ID():"Common";}
 public static boolean understoodSpeech(final MOB listener,final CMMsg msg){return listener!=null&&msg.sourceMessage()!=null&&(listener==msg.source()||perceives(listener,msg)&&proficiency(listener,messageLanguage(msg),CMStrings.getSayFromMessage(msg.sourceMessage()))>0);}
 public static String speechText(final MOB listener,final CMMsg msg){if(!understoodSpeech(listener,msg))return null;final String text=CMStrings.getSayFromMessage(msg.sourceMessage());return text==null?null:render(listener,messageLanguage(msg),text);}
 private static boolean perceives(final MOB listener,final CMMsg message){return message.tool() instanceof Visual?CMLib.flags().canBeSeenBy(message.source(),listener):CMLib.flags().canBeHeardSpeakingBy(message.source(),listener);}
 private static final Pattern WORD=Pattern.compile("[\\p{L}]+(?:['’][\\p{L}]+)*");
 public static String render(final MOB viewer,final String language,final String text){return render(language,text,proficiency(viewer,language,text));}
 public static String render(final String name,final String text,final int proficiency) {
  if(proficiency>=100)return text;final Language l=name.equalsIgnoreCase("Common")?null:language(name);final String id=l==null?name:l.ID();
  if(proficiency<=0&&l instanceof Scrambler&&!(l instanceof Visual))return ((Scrambler)l).scrambleAll(id,text,text.length());
  final Matcher m=WORD.matcher(text);final StringBuffer out=new StringBuffer();final boolean[] markup=CMStrings.markMarkups(text);
  while(m.find()){String word=m.group();if((m.start()>=markup.length||!markup[m.start()])&&Math.floorMod((id+":"+word.toLowerCase()).hashCode(),100)>=proficiency)word=l==null||l instanceof Visual?word.replaceAll("[\\p{L}]","?"):l.translate(id,word);m.appendReplacement(out,Matcher.quoteReplacement(word));}m.appendTail(out);return out.toString();
 }
 public static String inline(final MOB viewer,final String text,final boolean learn) {
  if(text==null||text.toUpperCase(java.util.Locale.ROOT).indexOf("^<LANG")<0)return text;final Matcher m=Pattern.compile("\\^<LANG\\s+([^\\s^>]+)(.*?)\\^>(.*?)\\^</LANG\\^>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(text);final StringBuffer out=new StringBuffer();final Set<String> exposed=new HashSet<String>();
  while(m.find()){final String id=id(m.group(1)),body=m.group(3);final int p=proficiency(viewer,id,body);final String foreign=render(id,body,0),understood=render(id,body,p),note=Scenery.option(m.group(2),"NOTE","");String shown=p==0&&!note.isEmpty()?note+": "+foreign:foreign;
   if(p>0)shown+=" (you read: \""+understood+"\"; translated from "+(language(id)==null?id:language(id).writtenName())+")";
   m.appendReplacement(out,Matcher.quoteReplacement(shown));if(learn&&viewer!=null&&exposed.add(id))expose(viewer,id,"READ",viewer.location());
  }m.appendTail(out);return out.toString();
 }
 /**
  * Returns whether a translator the listener has already shows them its own "(translated from ...)"
  * line for this speech: Speak with Animals, and Prop_LangTranslator unless NOTRANSLATE (passive).
  * A second line from here would repeat it.
  */
 private static boolean translatedElsewhere(final MOB listener,final String id,final String words) {
  final List<Physical> hosts=new ArrayList<Physical>();hosts.add(listener);if(listener.location()!=null)hosts.add(listener.location());
  for(final Enumeration<Item> e=listener.items();e.hasMoreElements();)hosts.add(e.nextElement());
  for(final Physical host:hosts)for(final Enumeration<Ability> e=host.effects();e.hasMoreElements();){
   final Ability a=e.nextElement();if(!(a instanceof Language)||!((Language)a).translatesLanguage(id,words))continue;
   if(a.ID().equals("Chant_SpeakWithAnimals"))return true;
   if(a.ID().equals("Prop_LangTranslator")&&!a.text().toUpperCase().contains("NOTRANSLATE"))return true;
  }
  return false;
 }
 /** Translate exactly once per listener; original speech remains available to its owner. */
 public static void heard(final MOB listener,final CMMsg msg) {
  if(listener==msg.source()||(msg.sourceMinor()!=CMMsg.TYP_SPEAK&&msg.sourceMinor()!=CMMsg.TYP_TELL&&!msg.sourceMajor(CMMsg.MASK_CHANNEL))||msg.sourceMessage()==null||(msg.sourceMinor()==CMMsg.TYP_SPEAK&&!perceives(listener,msg)))return;
  if(msg.target()!=null&&msg.target()!=listener&&msg.othersCode()==CMMsg.NO_EFFECT)return;
  final String text=CMStrings.getSayFromMessage(msg.sourceMessage());if(text==null)return;
  final List<String> only=restricted(listener);if(!only.isEmpty()&&!only.contains("Common")&&spoken(listener).equalsIgnoreCase("Common"))ensureSpoken(listener,only.get(0));
  final String language=messageLanguage(msg);final int p=proficiency(listener,language,text);
  if(p==0){ScriptEvents.notUnderstood(listener,msg,language,render(language,text,0));final Ability mono=listener.fetchEffect("Prop_Monolingual");if(mono!=null){final String confused=Scenery.option(mono.text(),"CONFUSED","");if(!confused.isEmpty()&&listener.location()!=null)listener.location().show(listener,msg.source(),CMMsg.MSG_NOISE,confused);}}
  if(!language.equalsIgnoreCase("Common")&&p>0&&!translatedElsewhere(listener,language,text)){
   final Language spoken=language(language);
   final String words=render(language,text,p);
   if(spoken instanceof SpeechTranslator)((SpeechTranslator)spoken).translateFor(listener,msg,words);
   else{final String template=msg.target()==listener?msg.targetMessage():msg.othersMessage();if(template!=null){final String line=CMStrings.substituteSayInMessage(template,words)+" (translated from "+(spoken==null?language:spoken.name())+")";msg.addTrailerMsg(CMClass.getMsg(msg.source(),listener,null,CMMsg.NO_EFFECT,null,CMMsg.TYP_SPEAK|CMMsg.MASK_ALWAYS,line,CMMsg.NO_EFFECT,null));}}
  }
  expose(listener,language,"HEAR",msg.source());
 }
 public static void ensureSpoken(final MOB who,final String name) {
  final Language prototype=language(name);if(prototype==null)return;Ability a=who.fetchEffect(prototype.ID());if(a==null){a=(Ability)prototype.copyOf();a.setProficiency(100);a.setSavable(false);who.addNonUninvokableEffect(a);}
  for(final Enumeration<Ability> e=who.effects();e.hasMoreElements();){final Ability l=e.nextElement();if(l instanceof Language)((Language)l).setBeingSpoken(l.ID(),l==a);}
 }
 public static String learn(final MOB who,final String name,final String amount,final boolean force) {
  final Language prototype=language(name);if(who==null||prototype==null)return "Unknown language: "+name;
  Ability a=who.fetchAbility(prototype.ID());if(a==null&&!force&&CMLib.ableComponents().getSpecialSkillRemainder(who,prototype).languageSkills()<=0)return "You cannot learn another language.";
  final int old=a==null?0:a.proficiency();final int value=Math.max(0,Math.min(100,amount.startsWith("+")||amount.startsWith("-")?old+CMath.s_int(amount):CMath.s_int(amount)));
  if(a==null){a=(Ability)prototype.copyOf();a.setProficiency(value);who.addAbility(a);a.autoInvocation(who,false);}else a.setProficiency(value);
  Ability live=who.fetchEffect(a.ID());if(live==null){live=(Ability)a.copyOf();live.setSavable(false);who.addNonUninvokableEffect(live);}live.setProficiency(value);
  if(value>old)who.tell(CMLib.lang().L("Your grasp of @x1 improves.",prototype.name()));return null;
 }
 public static void sayIn(final MOB who,final String language,final String text) {
  final Map<Language,Boolean> old=new IdentityHashMap<Language,Boolean>();for(final Enumeration<Ability> e=who.effects();e.hasMoreElements();){final Ability a=e.nextElement();if(a instanceof Language)old.put((Language)a,Boolean.valueOf(((Language)a).beingSpoken(a.ID())));}
  final Language l=language(language);final Ability existing=l==null?null:who.fetchEffect(l.ID());
  try{if(language.equalsIgnoreCase("Common")){for(final Language a:old.keySet())a.setBeingSpoken(a.ID(),false);}else ensureSpoken(who,language);CMLib.commands().postSay(who,null,text,false,false);}
  finally{for(final Map.Entry<Language,Boolean> e:old.entrySet())e.getKey().setBeingSpoken(e.getKey().ID(),e.getValue().booleanValue());if(l!=null&&existing==null){final Ability added=who.fetchEffect(l.ID());if(added!=null)who.delEffect(added);}}
 }
 public static void expose(final MOB who,final String name,final String event,final Environmental source) {
  if(who==null||who.playerStats()==null)return;final String id=id(name);final List<Physical> hosts=new ArrayList<Physical>();hosts.add(who);if(source instanceof Physical)hosts.add((Physical)source);if(who.location()!=null){hosts.add(who.location());hosts.add(who.location().getArea());}for(final Enumeration<Item> e=who.items();e.hasMoreElements();)hosts.add(e.nextElement());
  final Set<Ability> seen=Collections.newSetFromMap(new IdentityHashMap<Ability,Boolean>());
  for(final Physical host:hosts){final Ability p=host.fetchEffect("Prop_LanguageImmersion");if(p==null||!seen.add(p))continue;final String spec=Scenery.option(p.text(),"LANGUAGE","ANY");if(!spec.equalsIgnoreCase("ANY")&&!id(spec).equalsIgnoreCase(id))continue;
   final int gain=CMath.s_int(Scenery.option(p.text(),event,"0"));if(gain<=0)continue;
   final Ability known=who.fetchAbility(id);final int max=CMath.s_int(Scenery.option(p.text(),"MAX","60"));if(known!=null&&known.proficiency()>=max)continue;
   final String legacy=StoryRecords.token("LANG_",host.ID()+":"+host.Name()+":"+(host instanceof Room?CMLib.map().getExtendedRoomID((Room)host):"")+":"+p.text()+":"+id);
   final List<String> keys=AuthorIds.keys("LANG_",host,"IMMERSION",p.text(),id,legacy,true);
   final long exposures; synchronized(who){final MiniJSON.JSONObject data=StoryRecords.read(who),counts=StoryRecords.object(data,"languageExposure");long previous=0;for(final String key:keys)previous=Math.max(previous,StoryRecords.number(counts,key));exposures=previous+1;counts.put(keys.get(0),Long.valueOf(exposures));StoryRecords.write(who,data);}
   if(CMLib.dice().rollPercentage()>CMath.s_int(Scenery.option(p.text(),"CHANCE","20").replace("%","")))continue;
   if(known==null){if(exposures<CMath.s_int(Scenery.option(p.text(),"GRANTAFTER","25")))continue;learn(who,id,""+Math.min(max,CMath.s_int(Scenery.option(p.text(),"START","5"))),false);}
   else learn(who,id,""+Math.min(max,known.proficiency()+gain),false);
   break;
  }
 }
}
