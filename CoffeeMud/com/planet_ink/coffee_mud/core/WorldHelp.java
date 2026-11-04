package com.planet_ink.coffee_mud.core;
import java.io.*;
import java.util.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Deterministic world help overlays, provenance and builder destinations. */
public final class WorldHelp {
 private WorldHelp() {}
 private static final Map<String,String> sources=new java.util.concurrent.ConcurrentHashMap<String,String>();
 public static void clear(){sources.clear();}
 public static void record(final Properties entries,final String file,final boolean arc){for(final String key:entries.stringPropertyNames())sources.put((arc?"ARC:":"")+key,file);}
 public static String source(final String key){final String id=key.toUpperCase().replace(' ','_');final String normal=sources.get(id);return normal==null?sources.get("ARC:"+id):normal;}
 public static String source(final String key,final com.planet_ink.coffee_mud.Libraries.interfaces.HelpLibrary.HelpSection section){
  final String id=key.toUpperCase().replace(' ','_'),normal=sources.get(id),arc=sources.get("ARC:"+id);
  switch(section){case ArchonOnly:return arc;case NormalOnly:return normal;case ArchonFirst:return arc==null?normal:arc;default:return normal==null?arc:normal;}
 }
 public static void merge(final Properties target,final boolean arc) {
  final Properties settings=Vocabulary.properties("vocab/help/settings.ini");final List<String> hidden=new ArrayList<String>(CMParms.parseCommas(settings.getProperty("HIDE",""),true));
  for(final String line:Vocabulary.read("vocab/help/hide.txt").split("\\r?\\n"))if(!line.trim().isEmpty()&&!line.trim().startsWith("#"))hidden.add(line.trim());
  for(String key:hidden){key=key.toUpperCase().replace(' ','_');if(key.startsWith("ARC:")){if(arc)target.remove(key.substring(4));}else target.remove(key);}
  final File dir=Vocabulary.file("vocab/help");final File[] files=dir==null?null:dir.listFiles((d,n)->n.toLowerCase().endsWith(".ini")&&!n.equalsIgnoreCase("settings.ini")&&(n.toLowerCase().startsWith("arc_")==arc));
  if(files==null)return;Arrays.sort(files,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));for(final File f:files){final Properties p=Vocabulary.properties("vocab/help/"+f.getName());target.putAll(p);record(p,"world/vocab/help/"+f.getName(),arc);}
 }
 public static String editPath(final boolean arc){final String name="vocab/help/"+(arc?"arc_world_help.ini":"world_help.ini");final File f=Vocabulary.file(name);if(f!=null)f.getParentFile().mkdirs();return "world/"+name;}
 public static String prepare(final String original,final String key,final boolean delete) {
  final boolean arc=original.toLowerCase().contains("arc_");final String path=editPath(arc),relative=path.substring(6);
  if(delete){final String token=(arc?"ARC:":"")+key.toUpperCase();final String hide=Vocabulary.read("vocab/help/hide.txt");if(!Arrays.asList(hide.split("\\r?\\n")).contains(token))Vocabulary.write("vocab/help/hide.txt",hide+(hide.endsWith("\n")||hide.isEmpty()?"":"\n")+token+"\n");}
  else if(!Vocabulary.properties(relative).containsKey(key)){final String old=(arc?CMLib.help().getArcHelpFile():CMLib.help().getHelpFile()).getProperty(key);if(old!=null){final String body=Vocabulary.read(relative);Vocabulary.write(relative,body+(body.endsWith("\n")||body.isEmpty()?"":"\n")+key+"="+old.replace("\\","\\\\").replace("\r","\\r").replace("\n","\\n")+"\n");}}
  return path;
 }
}
