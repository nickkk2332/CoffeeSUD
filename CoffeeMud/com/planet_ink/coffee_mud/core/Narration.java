package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.io.*;
import java.util.*;
import java.util.function.Function;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Paragraph paging uses session callbacks; no game or input thread waits. */
public final class Narration {
 private Narration() {}
 private static final Map<Session,Deque<String>> pending=new IdentityHashMap<Session,Deque<String>>();
 private static boolean resume=false;
 public static String section(final String file) {
  final int at=file.indexOf('#');final String name=at<0?file:file.substring(0,at),key=at<0?"":file.substring(at+1);
  final String text=LoreBook.readFile(new File(WorldFiles.worldDir(),"text"),name);
  final StringBuilder out=new StringBuilder();boolean active=key.isEmpty();
  for(final String line:text.split("\\r?\\n")) {if(line.matches("\\[[^]]+\\]")){if(key.isEmpty())continue;active=line.substring(1,line.length()-1).equalsIgnoreCase(key);continue;}if(active)out.append(line).append('\n');}return out.toString().trim();
 }
 public static void show(final MOB who,final Environmental host,final String text,final boolean pause,final String style) {
  if(who==null||who.session()==null||who.session().isStopped())return;
  final String rendered=StoryText.render(who,host,text);final String prefix=style.isEmpty()?"":"^{"+style+"}";
  if(!pause||who.session().ID().equals("PartySession")||who.session().ID().equals("WindowSession")&&!CharacterWindow.canOpen()){who.tell(prefix+rendered+"^N");return;}
  final Deque<String> pages=new ArrayDeque<String>();for(final String p:rendered.split("\\n\\s*\\n"))if(!p.trim().isEmpty())pages.add(prefix+p+"^N");
  if(pages.isEmpty())return;final Session session=who.session();
  session.prompt((Session.InputCallback)null);
  synchronized(pending){if(pending.isEmpty()){resume=CMProps.isSinglePlayer()&&!GameTime.isTurnBased()&&!GameTime.isPaused();if(resume)GameTime.pause();}pending.put(session,pages);}
  next(session);
 }
 public static boolean waiting(final Session session){synchronized(pending){return pending.containsKey(session);}}
 public static void cancel(final Session session){synchronized(pending){pending.remove(session);if(pending.isEmpty()&&resume){resume=false;GameTime.resume();}}}
 public static void next(final Session session) {
  final String page;final boolean more;
  synchronized(pending){final Deque<String> p=pending.get(session);if(p==null)return;page=p.poll();more=!p.isEmpty();}
  if(page!=null&&session.mob()!=null){session.mob().tell(page);Ambience.activity(session.mob());}
  if(!more){cancel(session);return;}
  session.prompt(new Session.InputCallback(Session.InputCallback.Type.PROMPT,"",0){
   @Override public void showPrompt(){session.promptPrint("[Enter to continue] ");}
   @Override public void timedOut(){cancel(session);}
   @Override public void callBack(){next(session);}
  });
 }
}
