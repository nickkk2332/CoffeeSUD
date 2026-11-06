package com.planet_ink.coffee_mud.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** World vocabulary, scoped list lookups and whole-word terminology. */
public final class Vocabulary
{
 private Vocabulary() {}
 public static synchronized void clear(){glossaryChecked=Long.MIN_VALUE;glossaryStamp=Long.MIN_VALUE;glossaryPath="";rules=Collections.emptyList();}
 public static File file(final String name) {
  try {final File root=WorldFiles.worldDir().getCanonicalFile(),f=new File(root,name.replace('\\','/')).getCanonicalFile();return f.toPath().startsWith(root.toPath())?f:null;}
  catch(final IOException e){return null;}
 }
 public static String read(final String name) {final File f=file(name);if(f==null||!f.isFile())return "";try{final String text=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);return text.startsWith("\uFEFF")?text.substring(1):text;}catch(final IOException e){Log.errOut("Vocabulary",e.getMessage());return "";}}
 public static Properties properties(final String name) {
  final Properties p=new Properties();try{p.load(new StringReader(read(name)));}catch(final IOException|IllegalArgumentException e){Log.errOut("Vocabulary",e.getMessage());}return p;
 }
 public static boolean write(final String name,final String text) {
  final File f=file(name);if(f==null)return false;try{Files.createDirectories(f.toPath().getParent());Files.write(f.toPath(),text.getBytes(StandardCharsets.UTF_8));glossaryChecked=Long.MIN_VALUE;return true;}catch(final IOException e){Log.errOut("Vocabulary",e.getMessage());return false;}
 }
 private static final ThreadLocal<MOB> combatant=new ThreadLocal<MOB>();
 public static String hit(final Weapon weapon,final MOB source,final int damage){final MOB old=combatant.get();combatant.set(source);try{return weapon.hitString(damage);}finally{if(old==null)combatant.remove();else combatant.set(old);}}
 public static String miss(final Weapon weapon,final MOB source){final MOB old=combatant.get();combatant.set(source);try{return weapon.missString();}finally{if(old==null)combatant.remove();else combatant.set(old);}}
 public static String damageMessage(final Physical host,final String text,final int type,final int amount){if(text==null)return null;final String word=damageWord(host,type,amount);if(word==null)return text;return text.replace("<DAMAGES>",word.replace("(s)","")).replace("<DAMAGE->",word.replace("(s)","")).replace("<DAMAGE>",word);}
 public static String magicWord(final Physical host,final String seed,final String fallback){final String raw=local(host,"MAGIC_WORDS");if(raw==null)return fallback;final List<String> words=CMParms.parseCommas(raw,true);return words.isEmpty()?fallback:words.get(Math.floorMod(seed.hashCode(),words.size()));}
 public static String local(final Physical context,final String key) {
  if(context==null)return null;
  final List<Physical> hosts=new ArrayList<Physical>();
  if(combatant.get()!=null)hosts.add(combatant.get());
  if(context instanceof MOB)hosts.add(context);
  else if(context instanceof Item&&((Item)context).owner() instanceof MOB)hosts.add((MOB)((Item)context).owner());
  final Room located=context instanceof Room?(Room)context:CMLib.map().roomLocation(context);
  final Room room=located==null&&combatant.get()!=null?combatant.get().location():located;
  if(room!=null)hosts.add(room);
  final Area area=context instanceof Area?(Area)context:room==null?null:room.getArea();
  final Set<Area> seen=Collections.newSetFromMap(new IdentityHashMap<Area,Boolean>());
  final Queue<Area> areas=new ArrayDeque<Area>();if(area!=null)areas.add(area);
  while(!areas.isEmpty()&&seen.size()<100){final Area a=areas.remove();if(!seen.add(a))continue;hosts.add(a);for(final Enumeration<Area> e=a.getParents();e.hasMoreElements();)areas.add(e.nextElement());}
  for(final Physical host:hosts){final Ability a=host.fetchEffect("Prop_LocalLists");if(a==null)continue;
   final String inline=Scenery.option(a.text(),key,null);if(inline!=null)return inline;
   final String load=Scenery.option(a.text(),"LOAD","");if(!load.isEmpty()){final Properties p=properties(load.startsWith("vocab/")?load:"vocab/"+load);if(p.containsKey(key))return p.getProperty(key);}
  }
  return null;
 }
 public static Object[][] choices(final String value) {
  final List<String> rows=CMParms.parseCommas(value,false);final Object[][] out=new Object[rows.size()][];
  for(int i=0;i<out.length;i++)out[i]=CMParms.toStringArray(CMParms.parseAny(rows.get(i),'|',false));return out;
 }
 public static Object[][][] grid(final String value) {
  final List<String> rows=CMParms.parseSemicolons(value,true);final Object[][][] out=new Object[rows.size()][][];for(int i=0;i<out.length;i++)out[i]=choices(rows.get(i));return out;
 }
 public static String pick(final String value,final int index) {
  final Object[][] rows=choices(value);if(rows.length==0)return "";final Object[] row=rows[Math.max(0,Math.min(rows.length-1,index))];return row.length==0?"":(String)CMLib.dice().pick(row);
 }
 public static String choice(final CMProps.ListFile key,final int index,final Physical context) {final String local=local(context,key.name());return local==null?CMProps.getListFileChoiceFromIndexedList(key,index):pick(local,index);}
 public static String damageWord(final Physical context,final int type,final int damage) {
  String raw=context==null?null:context.fetchEffect("Prop_WeaponMessages")==null?null:Scenery.option(context.fetchEffect("Prop_WeaponMessages").text(),"DAMAGEWORDS",null);
  if(raw==null)raw=local(context,"DAMAGE_WORDS");if(raw==null)return null;
  final String first=raw.split(",",2)[0].trim();
  boolean grouped=raw.indexOf(';')>=0||first.equalsIgnoreCase("ALL");
  for(final String kind:Weapon.TYPE_DESCS)if(first.equalsIgnoreCase(kind))grouped=true;
  if(!grouped)return pick(raw,severity(damage));
  final Object[][][] rows=grid(raw);
  final String specific=Weapon.TYPE_DESCS[Math.max(0,Math.min(type,Weapon.TYPE_DESCS.length-1))];
  for(final String kind:new String[]{specific,"ALL"})
   for(final Object[][] row:rows)
    if(row.length>1&&row[0].length>0&&row[0][0].toString().equalsIgnoreCase(kind))
     return (String)CMLib.dice().pick(row[Math.min(row.length-1,severity(damage)+1)]);
  return null;
 }
 private static int severity(final int damage){final int[] t=CMProps.getListFileIntList(CMProps.ListFile.DAMAGE_WORDS_THRESHOLDS);for(int i=0;i<t.length;i++)if(damage<=t[i])return i;return Math.max(0,t.length-1);}
 private static int attackIndex(final int type,final int classification){if(classification==Weapon.CLASS_RANGED)return type==Weapon.TYPE_LASERING?5:0;if(classification==Weapon.CLASS_THROWN)return type==Weapon.TYPE_LASERING?5:1;switch(type){case Weapon.TYPE_BASHING:case Weapon.TYPE_SLASHING:return 2;case Weapon.TYPE_PIERCING:return 4;case Weapon.TYPE_SHOOT:return 0;case Weapon.TYPE_LASERING:return 5;default:return 3;}}
 public static String weapon(final Weapon weapon,final boolean hit,final int damage) {
  final Ability a=weapon.fetchEffect("Prop_WeaponMessages");String value=a==null?null:Scenery.option(a.text(),hit?"HIT":"MISS",null);
  if(value==null){final String raw=local(weapon,hit?"WEAPON_HIT_DESCS":"WEAPON_MISS_DESCS");if(raw!=null){final int index=attackIndex(weapon.weaponDamageType(),weapon.weaponClassification());value=pick(raw,index);}}
  if(value==null)return null;final String[] lines=value.split("\\|");value=lines[CMLib.dice().roll(1,lines.length,-1)].replace("<TOOLNAME>",weapon.name());
  final String word=damageWord(weapon,weapon.weaponDamageType(),damage);if(word!=null)value=value.replace("<DAMAGE>",word);return value;
 }
 public static String corpse(final MOB dead,final Environmental cause,final Integer damageType) {
  String raw=dead.charStats().getMyRace().isGeneric()?dead.charStats().getMyRace().getStat("CORPSEBLURBS"):"";
  if(raw.isEmpty())raw=local(dead,"CORPSE_BLURBS");if(raw==null||raw.isEmpty())return null;
  final List<String> keys=new ArrayList<String>();if(cause!=null)keys.add(cause.ID());if(cause instanceof Weapon){final Weapon w=(Weapon)cause;keys.add(Weapon.TYPE_DESCS[w.weaponDamageType()]);keys.add(Weapon.CLASS_DESCS[w.weaponClassification()]);}if(damageType!=null&&damageType.intValue()>=0&&damageType.intValue()<Weapon.TYPE_DESCS.length)keys.add(Weapon.TYPE_DESCS[damageType.intValue()]);keys.add("ALL");
  for(final String key:keys)for(final String line:Scenery.statements(raw)){final int at=line.indexOf('=');if(at>0&&line.substring(0,at).trim().equalsIgnoreCase(key)){final String[] pool=StoryText.unquote(line.substring(at+1).trim()).split("\\|");return pool[CMLib.dice().roll(1,pool.length,-1)];}}
  for(final Object[][] row:grid(raw))if(row.length>1&&keys.stream().anyMatch(k->k.equalsIgnoreCase(row[0][0].toString())))return (String)CMLib.dice().pick(row[CMLib.dice().roll(1,row.length-1,0)]);return null;
 }
 private static final class Rule {Pattern pattern;String replacement,scope;boolean fin,plural;List<String> except=new ArrayList<String>();}
 private static volatile long glossaryStamp=Long.MIN_VALUE;private static volatile String glossaryPath="";private static volatile List<Rule> rules=Collections.emptyList();
 private static volatile long glossaryChecked=Long.MIN_VALUE;
 private static synchronized List<Rule> glossaryRules() {
  // L() and every printed line come here: the file is looked at again at most once a second (write() and clear() at once)
  final long now=System.currentTimeMillis();if(now-glossaryChecked>=0&&now-glossaryChecked<1000)return rules;glossaryChecked=now;
  final File f=file("vocab/glossary.ini");final long stamp=f==null||!f.isFile()?0:f.lastModified();final String path=f==null?"":f.getPath()+":"+f.length();if(stamp==glossaryStamp&&path.equals(glossaryPath))return rules;
  final List<Rule> next=new ArrayList<Rule>();String scope="ALL";List<String> exceptions=new ArrayList<String>();
  for(final String line:read("vocab/glossary.ini").split("\\r?\\n")){final String t=line.trim();if(t.isEmpty()||t.startsWith("#")||t.startsWith(";"))continue;if(t.toUpperCase().startsWith("NOT:")){exceptions=CMParms.parseCommas(t.substring(4),true);continue;}if(t.toUpperCase().startsWith("SCOPE=")){scope=t.substring(6).trim().toUpperCase();continue;}final int at=t.indexOf('=');if(at<1)continue;
   String term=t.substring(0,at).trim();final Rule r=new Rule();r.fin=term.toUpperCase().startsWith("FINAL ");if(r.fin)term=term.substring(6).trim();r.plural=term.endsWith("(s)");if(r.plural)term=term.substring(0,term.length()-3);
   final List<String> parts=Scenery.statements(t.substring(at+1));r.replacement=StoryText.unquote(parts.get(0));r.scope=Scenery.option(t.substring(at+1),"SCOPE",scope).toUpperCase();r.fin=r.fin||Scenery.flag(t.substring(at+1),"FINAL");r.except=new ArrayList<String>(exceptions);for(final String part:parts)if(part.toUpperCase().startsWith("NOT:"))r.except.addAll(CMParms.parseCommas(part.substring(4),true));
   // a term may follow a colour code (^H) or an ANSI sequence, whose last character is a letter
   r.pattern=Pattern.compile("(?iu)(?:(?<=\\^.)|(?<=\u001b\\[[0-9;]{1,12}m)|(?<![\\p{L}\\p{N}_]))"+Pattern.quote(term).replace(" ","\\E\\s+\\Q")+(r.plural?"(?:\\(s\\)|s)?":"")+"(?![\\p{L}\\p{N}_])");next.add(r);
  }
  rules=next;glossaryStamp=stamp;glossaryPath=path;return rules;
 }
 public static String glossary(final String text,final String scope,final boolean fin) {
  return applyGlossary(text,scope,fin,false);
 }
 private static String applyGlossary(final String text,final String scope,final boolean fin,final boolean exactScope){
  if(text==null||text.isEmpty())return text;String out=text;
  for(final Rule r:glossaryRules()){if(r.fin!=fin||(exactScope?!r.scope.equalsIgnoreCase(scope):!r.scope.equals("ALL")&&!r.scope.equalsIgnoreCase(scope)))continue;final Matcher m=r.pattern.matcher(out);final StringBuffer result=new StringBuffer();
   while(m.find()){boolean except=false;for(final String name:r.except){final Matcher ex=Pattern.compile(Pattern.quote(name),Pattern.CASE_INSENSITIVE).matcher(out);while(ex.find())if(m.start()<ex.end()&&m.end()>ex.start())except=true;}
    // Do not alter markup attributes or color codes.
    final int open=out.lastIndexOf("^<",m.start()),close=out.lastIndexOf("^>",m.start());if(open>close)except=true;
    // nor inside a tag: an MXP link's command, or an engine placeholder such as <S-NAME>
    final int lt=out.lastIndexOf('<',m.start()),gt=out.lastIndexOf('>',m.start());if(lt>gt)except=true;
    String replacement=r.plural?r.replacement.replace("(s)",""):r.replacement;if(r.plural){if(m.group().endsWith("(s)"))replacement+="(s)";else if(m.group().toLowerCase().endsWith("s"))replacement+="s";}
    replacement=caseLike(m.group(),replacement);m.appendReplacement(result,Matcher.quoteReplacement(except?m.group():replacement));
   }m.appendTail(result);out=result.toString();
  }return out;
 }
 public static String scopedOutput(final String text,final String scope){String out=glossary(text,scope,false);
  return scopedFinal(out,scope);}
 /** As scopedOutput, for text whose lines have been through L() already, which gave them the ALL rules: only the scope's own. */
 public static String scopedOnly(final String text,final String scope){return scopedFinal(applyGlossary(text,scope,false,true),scope);}
 private static String scopedFinal(final String text,final String scope){return applyGlossary(text,scope,true,true);}
 public static String caseLike(final String source,final String target){if(source.equals(source.toUpperCase()))return target.toUpperCase();if(!source.isEmpty()&&Character.isUpperCase(source.charAt(0))){if(source.matches("(?:[A-Z][a-z]*(?:\\s+|$))+")){final StringBuilder out=new StringBuilder();boolean upper=true;for(final char c:target.toCharArray()){out.append(upper?Character.toUpperCase(c):c);upper=Character.isWhitespace(c);}return out.toString();}return target.isEmpty()?target:Character.toUpperCase(target.charAt(0))+target.substring(1);}return target;}
}
