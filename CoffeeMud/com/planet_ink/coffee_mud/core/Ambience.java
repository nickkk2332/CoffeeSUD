package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Common ambient predicates and reading/combat suppression. */
public final class Ambience {
 private Ambience() {}
 public interface Source {boolean listen(MOB viewer,PhysicalAgent host);}
 public static final class Line {
  public String text="",mask="",kind=""; public int weight=1;public boolean once=false;
  public final List<String[]> conditions=new ArrayList<String[]>();
 }
 private static final Map<MOB,Long> active=Collections.synchronizedMap(new WeakHashMap<MOB,Long>());
 public static void activity(final MOB who){if(who!=null)active.put(who,Long.valueOf(GameTime.now()));}
 public static boolean quiet(final Room r,final int ticks){if(ticks<=0)return false;for(final Enumeration<MOB> e=r.inhabitants();e.hasMoreElements();){final MOB m=e.nextElement();if(m.playerStats()==null)continue;final Long last=active.get(m);if(m.isInCombat()){activity(m);return true;}if(Narration.waiting(m.session())||last!=null&&GameTime.now()-last.longValue()<ticks*CMProps.getTickMillis())return true;}return false;}
 public static Line parse(final String input) {
  final Line line=new Line();String rest=input.trim();
  while(!rest.isEmpty()) {
   final Matcher m=Pattern.compile("^([^\\s=]+)(?:=(\"[^\"]*\"|[^\\s]+))?(?:\\s+|$)").matcher(rest);if(!m.find())break;
   // prefixes are written in capitals and end at the line's type: a line's own first word
   // ("Fog rolls in", "Once again...") is text, as it was before conditions existed
   if(!m.group(1).equals(m.group(1).toUpperCase()))break;
   final String key=m.group(1).toUpperCase(),value=m.group(2)==null?"":StoryText.unquote(m.group(2));int end=m.end();
   if(Arrays.asList("DAY","NIGHT","DAWN","DUSK","SPRING","SUMMER","FALL","WINTER","RAIN","SNOW","FOG","INDOORS","OUTDOORS").contains(key))line.conditions.add(new String[]{key,""});
   else if(key.equals("MASK"))line.mask=value;
   else if(key.equals("WEIGHT"))line.weight=Math.max(1,Math.min(10000,CMath.s_int(value)));
   else if(key.equals("ONCE"))line.once=true;
   else if(key.equals("IFVAR")) { final Matcher v=Pattern.compile("^(\\S+)(?:\\s+|$)").matcher(rest.substring(end));if(!v.find())break;line.conditions.add(new String[]{"IFVAR",v.group(1)});end+=v.end(); }
   else if(Arrays.asList("SOUND","NOISE","SMELL","AROMA","PUFF","SIGHT","VISUAL","SAY","EMOTE","SOCIAL").contains(key)) { line.kind=key; rest=rest.substring(end).trim(); break; }
   else break;
   rest=rest.substring(end).trim();
  }
  line.text=rest;return line;
 }
 public static String token(final Line line){return StoryRecords.token("AMBIENT_",line.mask+":"+line.text+":"+line.conditions.stream().map(x->x[0]+x[1]).reduce("",String::concat));}
 public static boolean fits(final Line line,final MOB viewer,final Environmental host,final Room room) {
  if(viewer==null&&(line.once||!line.mask.isEmpty()))return false;
  if(line.once&&viewer!=null&&viewer.findTattoo(token(line))!=null)return false;
  if(!line.mask.isEmpty()&&!CMLib.masking().maskCheck(line.mask,viewer,true))return false;
  for(final String[] c:line.conditions)if(!StoryText.test(viewer,host,room==null?null:room.getArea(),room,c[0],c[1]))return false;
  return true;
 }
 public static void heard(final Line line,final MOB viewer){if(line.once&&viewer!=null)viewer.addTattoo(token(line));}
}
