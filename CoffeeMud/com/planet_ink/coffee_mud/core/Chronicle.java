package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
/** The saved, dated personal chronicle. IDs survive deletion and truncation. */
public final class Chronicle
{
 private Chronicle() {}
 public static void add(final MOB who, final String category, final String text, final Environmental source)
 {
  if(who==null||who.playerStats()==null||text==null||text.trim().isEmpty()) return;
  synchronized(who) {
   final MiniJSON.JSONObject data=StoryRecords.read(who), entries=StoryRecords.object(data,"journal"), e=new MiniJSON.JSONObject();
   final long id=StoryRecords.number(data,"journalSeq")+1;
   data.put("journalSeq",Long.valueOf(id)); e.put("id",Long.valueOf(id)); e.put("category",category.isEmpty()?"Notes":category);
   e.put("text",text); e.put("date",CMLib.time().localClock(who).getShortTimeDescription()); e.put("when",Long.valueOf(GameTime.now()));
   e.put("source",source==null?"":source instanceof com.planet_ink.coffee_mud.Locales.interfaces.Room?((com.planet_ink.coffee_mud.Locales.interfaces.Room)source).displayText(who):source.Name()); e.put("room",who.location()==null?"":who.location().displayText(who));
   entries.put(Long.toString(id),e);
   if(entries.size()>300) { long oldest=Long.MAX_VALUE; for(final String k:entries.keySet()) oldest=Math.min(oldest,CMath.s_long(k)); entries.remove(Long.toString(oldest)); }
   StoryRecords.write(who,data);
  }
 }
 public static void auto(final MOB who, final String kind, final String text, final Environmental source)
 {
  for(final String k:CMProps.instance().getStr("JOURNALAUTO").split(",")) if(k.trim().equalsIgnoreCase(kind)) { add(who,kind,text,source); return; }
 }
 public static String show(final MOB who, final String filter, final boolean search)
 {
  final MiniJSON.JSONObject entries=StoryRecords.object(StoryRecords.read(who),"journal");
  final List<String> keys=new ArrayList<String>(entries.keySet()); keys.sort((a,b)->Long.compare(CMath.s_long(b),CMath.s_long(a)));
  final StringBuilder out=new StringBuilder();
  for(final String key:keys) {
   final MiniJSON.JSONObject e=(MiniJSON.JSONObject)entries.get(key);
   final String text=StoryRecords.string(e,"text"),cat=StoryRecords.string(e,"category");
   if(!filter.isEmpty()&&!(search?(text+" "+cat+" "+StoryRecords.string(e,"source")).toLowerCase().contains(filter.toLowerCase()):cat.equalsIgnoreCase(filter))) continue;
   out.append("#").append(key).append(" [").append(cat).append("] ").append(StoryRecords.string(e,"date")).append(" — ").append(text);
   final String source=StoryRecords.string(e,"source"),room=StoryRecords.string(e,"room");
   if(!source.isEmpty()||!room.isEmpty()) out.append(" (").append(source).append(source.isEmpty()||room.isEmpty()?"":"; ").append(room).append(")");
   out.append("\n\r");
  }
  return out.length()==0?"No journal entries match.":out.toString();
 }
 public static boolean delete(final MOB who, final String id) {
  synchronized(who) { final MiniJSON.JSONObject d=StoryRecords.read(who); final boolean found=StoryRecords.object(d,"journal").remove(id)!=null; if(found) StoryRecords.write(who,d); return found; }
 }
}
