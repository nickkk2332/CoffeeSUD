package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** A shared indoor/outdoor map, always filtered through the viewer's knowledge. */
public final class PlayerMaps {
 private PlayerMaps() {}
 private static final ThreadLocal<MOB> reader=new ThreadLocal<MOB>();
 public static MOB reader(){return reader.get();}
 public static <T> T reading(final MOB who,final java.util.function.Supplier<T> action){final MOB old=reader.get();reader.set(who);try{return action.get();}finally{if(old==null)reader.remove();else reader.set(old);}}
 public static boolean mappable(final Room r,final MOB who) {
  if(r==null||CMath.bset(r.phyStats().sensesMask(),PhyStats.SENSE_ROOMUNMAPPABLE)||CMath.bset(r.getArea().phyStats().sensesMask(),PhyStats.SENSE_ROOMUNMAPPABLE)||!ViewRules.allowed(r,who)) return false;
  final Ability a=r.fetchEffect("Prop_MapGlyph"); return a==null||!Scenery.flag(a.text(),"HIDE");
 }
 public static boolean visited(final MOB who,final Room r){return who!=null&&who.playerStats()!=null&&who.playerStats().hasVisited(r);}
 public static boolean known(final MOB who,final Room r) {
  return who!=null&&known(who,r,StoryRecords.object(StoryRecords.read(who),"map"),CMProps.instance().getStr("MAPFOG"));
 }
 private static boolean known(final MOB who,final Room r,final MiniJSON.JSONObject map,final String fog) {
  return mappable(r,who)&&(r==who.location()||fog.equalsIgnoreCase("ALL")||visited(who,r)||!fog.equalsIgnoreCase("VISITED")&&map.containsKey(CMLib.map().getExtendedRoomID(r)));
 }
 private static void reveal(final MiniJSON.JSONObject map,final Room r,final String mark,final String label,final String color) {
  if(r==null)return;final String id=CMLib.map().getExtendedRoomID(r);if(id.isEmpty())return;
  final MiniJSON.JSONObject entry=StoryRecords.object(map,id);
  if(!mark.isEmpty()){entry.put("mark",mark.substring(0,1));entry.put("label",label);entry.put("color",color);}
 }
 public static void reveal(final MOB who,final Room r,final String mark,final String label,final String color) {
  if(who==null||who.playerStats()==null||r==null)return;
  synchronized(who){final MiniJSON.JSONObject data=StoryRecords.read(who);reveal(StoryRecords.object(data,"map"),r,mark,label,color);StoryRecords.write(who,data);}
 }
 public static void reveal(final MOB who,final String params) {
  if(who==null||who.playerStats()==null)return;
  synchronized(who){
   final MiniJSON.JSONObject data=StoryRecords.read(who),map=StoryRecords.object(data,"map");
   final String area=Scenery.option(params,"AREA","");
   if(!area.isEmpty()){final Area a=CMLib.map().findArea(area);if(a!=null)for(final Enumeration<Room> e=a.getProperMap();e.hasMoreElements();)reveal(map,e.nextElement(),"","","");}
   for(final String id:Scenery.option(params,"ROOMS","").split(",")) {
    final java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(.*#)([0-9]+)-([0-9]+)$").matcher(id.trim());
    if(m.matches()){final int start=CMath.s_int(m.group(2)),end=Math.min(start+10000,CMath.s_int(m.group(3)));for(int n=start;n<=end;n++)reveal(map,CMLib.map().getRoom(m.group(1)+n),"","","");}
    else reveal(map,CMLib.map().getRoom(id.trim()),"","","");
   }
   final java.util.regex.Matcher marks=java.util.regex.Pattern.compile("(?i)MARK=([^:;\\s]+):([^:;\\s]):(?:\"([^\"]*)\"|([^;\\s]+))").matcher(params);
   while(marks.find())reveal(map,CMLib.map().getRoom(marks.group(1)),marks.group(2),marks.group(3)==null?marks.group(4):marks.group(3),Scenery.option(params,"COLOR","r"));
   StoryRecords.write(who,data);
  }
 }
 public static int size(final MOB who) { final int saved=(int)StoryRecords.number(StoryRecords.read(who),"mapSize"); return saved>0?saved:Math.max(1,CMProps.getIntVar(CMProps.Int.AWARERANGE)/2); }
 public static boolean automatic(final MOB who) {return !who.isAttributeSet(MOB.Attrib.AUTOMAP)&&(CMProps.getIntVar(CMProps.Int.AWARERANGE)>0||!StoryRecords.string(StoryRecords.read(who),"mapSize").isEmpty());}
 private static final class Point {final Room room;final int x,y,z,d;Point(final Room r,final int xx,final int yy,final int zz,final int dd){room=r;x=xx;y=yy;z=zz;d=dd;}}
 public static String render(final MOB who,final Room start,final int requested) {
  if(who==null||start==null||!mappable(start,who)||!known(who,start)) return "No map is available here.";
  final int radius=Math.max(1,Math.min(12,requested)),width=radius*4+1;
  final String[][] grid=new String[width][width];final Map<String,String> legend=new LinkedHashMap<String,String>();
  final String fog=CMProps.instance().getStr("MAPFOG");
  final MiniJSON.JSONObject marks=StoryRecords.object(StoryRecords.read(who),"map"); final Queue<Point> todo=new ArrayDeque<Point>(); final Set<Room> seen=Collections.newSetFromMap(new IdentityHashMap<Room,Boolean>());
  todo.add(new Point(start,0,0,0,0));seen.add(start);int count=0;
  while(!todo.isEmpty()&&count++<1000) {
   final Point p=todo.remove(); final Ability a=p.room.fetchEffect("Prop_MapGlyph"); final String params=a==null?"":a.text();
   String glyph=Scenery.option(params,"CHAR",(p.room.domainType()&Room.INDOORS)!=0?"#":"o"),color=Scenery.option(params,"COLOR","w"),label=Scenery.option(params,"LABEL","");
   final Object marked=marks.get(CMLib.map().getExtendedRoomID(p.room));
   if(marked instanceof MiniJSON.JSONObject) { final MiniJSON.JSONObject mark=(MiniJSON.JSONObject)marked;if(!StoryRecords.string(mark,"mark").isEmpty()) {glyph=StoryRecords.string(mark,"mark");color=StoryRecords.string(mark,"color");label=StoryRecords.string(mark,"label");} }
   if(p.room==who.location()) {glyph="*";color="H";} else if(!visited(who,p.room)) color="k";
   if(glyph.isEmpty()) glyph="o"; final String symbol=(color.startsWith("^")?color:"^"+color)+glyph.substring(0,1)+"^N";
   if(p.z==0&&Math.abs(p.x)<=radius&&Math.abs(p.y)<=radius) grid[(p.y+radius)*2][(p.x+radius)*2]=symbol;
   if(!label.isEmpty()&&(Scenery.flag(params,"LEGEND")||marked instanceof MiniJSON.JSONObject)) legend.put(symbol,label);
   if(p.d>=radius) continue;
   for(int d=0;d<Directions.NUM_DIRECTIONS();d++) {
    final Room r=p.room.getRoomInDir(d); if(!known(who,r,marks,fog)||p.room.getExitInDir(d)==null||!ViewRules.allowed(p.room.getExitInDir(d),who)) continue;
    int dx=0,dy=0,dz=0;
    switch(d){case Directions.NORTH:dy=-1;break;case Directions.SOUTH:dy=1;break;case Directions.EAST:dx=1;break;case Directions.WEST:dx=-1;break;case Directions.NORTHEAST:dx=1;dy=-1;break;case Directions.NORTHWEST:dx=-1;dy=-1;break;case Directions.SOUTHEAST:dx=1;dy=1;break;case Directions.SOUTHWEST:dx=-1;dy=1;break;case Directions.UP:dz=1;break;case Directions.DOWN:dz=-1;break;default:continue;}
    if(dz!=0&&p.z==0) legend.put(dz>0?"^w<^N":"^w>^N",(dz>0?"Up: ":"Down: ")+r.displayText(who));
    final int x=p.x+dx,y=p.y+dy,z=p.z+dz;
    if(z==0&&p.z==0&&Math.abs(x)<=radius&&Math.abs(y)<=radius) grid[(p.y+radius)*2+dy][(p.x+radius)*2+dx]=dx==0?"|":dy==0?"-":dx==dy?"\\":"/";
    if(Math.abs(x)<=radius&&Math.abs(y)<=radius&&seen.add(r)) todo.add(new Point(r,x,y,z,p.d+1));
   }
  }
  final StringBuilder out=new StringBuilder();for(int y=0;y<width;y++) {boolean any=false;for(final String s:grid[y])if(s!=null)any=true;if(!any)continue;for(int x=0;x<width;x++)out.append(grid[y][x]==null?" ":grid[y][x]);out.append("\n\r");}
  out.append("* You; # indoors; o outdoors; dim = revealed\n\r");for(final Map.Entry<String,String> e:legend.entrySet())out.append(e.getKey()).append(" ").append(e.getValue()).append("\n\r");return out.toString();
 }
 public static Room inArea(final MOB who,final String name) {final Area a=CMLib.map().findArea(name);if(a==null)return null;for(final Enumeration<Room> e=a.getProperMap();e.hasMoreElements();){final Room r=e.nextElement();if(known(who,r))return r;}return null;}
}
