package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.io.*;
import java.util.*;
import java.util.function.Function;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
/** Paragraph paging uses session callbacks; no game or input thread waits. */
public final class Narration
{
	private Narration()
	{
	}
	private static final Map<Session,Deque<String>> pending=new IdentityHashMap<Session,Deque<String>>();
	private static boolean resume=false;
	private static final List<XMLTag> restored=new ArrayList<XMLTag>();
	public static String section(final String file)
	{
		final int at=file.indexOf('#');
		final String name=at<0?file:file.substring(0,at),key=at<0?"":file.substring(at+1);
		final String text=LoreBook.readFile(new File(WorldFiles.worldDir(),"text"),name);
		final StringBuilder out=new StringBuilder();
		boolean active=key.isEmpty();
		for(final String line:text.split("\\r?\\n"))
		{
			if(line.matches("\\[[^]]+\\]"))
			{
				if(key.isEmpty())
					continue;
				active=line.substring(1,line.length()-1).equalsIgnoreCase(key);
				continue;
			}
			if(active)
				out.append(line).append('\n');
		}
		return out.toString().trim();
	}
	public static void show(final MOB who,final Environmental host,final String text,final boolean pause,final String style)
	{
		if(who==null||who.session()==null||who.session().isStopped())
			return;
		final String rendered=StoryText.render(who,host,text);
		final String prefix=style.isEmpty()?"":"^{"+style+"}";
		if(!pause||who.session().ID().equals("PartySession")||who.session().ID().equals("WindowSession")&&!CharacterWindow.canOpen())
		{
			who.tell(prefix+rendered+"^N");
			return;
		}
		final Deque<String> pages=new ArrayDeque<String>();
		for(final String p:rendered.split("\\n\\s*\\n"))
			if(!p.trim().isEmpty())
				pages.add(prefix+p+"^N");
		if(pages.isEmpty())
			return;
		final Session session=who.session();
		ScriptMenus.cancel(who);
		session.prompt((Session.InputCallback)null);
		synchronized(pending)
		{
			if(pending.isEmpty())
			{
				resume=CMProps.isSinglePlayer()&&!GameTime.isTurnBased()&&!GameTime.isPaused();
				if(resume)
					GameTime.pause();
			}
			pending.put(session,pages);
		}
		next(session);
	}
	public static boolean waiting(final Session session)
	{
		synchronized(pending)
		{
			return pending.containsKey(session);
		}
	}
	public static void cancel(final Session session)
	{
		synchronized(pending)
		{
			pending.remove(session);
			if(pending.isEmpty()&&restored.isEmpty()&&resume)
			{
				resume=false;
				GameTime.resume();
			}
		}
	}
	public static void clear()
	{
		synchronized(pending)
		{
			for(final Session session:pending.keySet())
				session.prompt((Session.InputCallback)null);
			pending.clear();
			restored.clear();
			if(resume)
				GameTime.resume();
			resume=false;
		}
	}
	public static void next(final Session session)
	{
		final String page;
		final boolean more;
		synchronized(pending)
		{
			final Deque<String> p=pending.get(session);
			if(p==null)
				return;
			page=p.poll();
			more=!p.isEmpty();
		}
		if(page!=null&&session.mob()!=null)
		{
			session.mob().tell(page);
			Ambience.activity(session.mob());
		}
		if(!more)
		{
			cancel(session);
			return;
		}
		session.prompt(new Session.InputCallback(Session.InputCallback.Type.PROMPT,"",0)
		{
			@Override public void showPrompt()
			{
				session.promptPrint("[Enter to continue] ");
			}
			@Override public void timedOut()
			{
				cancel(session);
			}
			@Override public void callBack()
			{
				next(session);
			}
		});
	}
	public static String toXML()
	{
		synchronized(pending)
		{
			final StringBuilder out=new StringBuilder("<NARRATION RESUME=\"").append(resume).append("\">");
			for(final Map.Entry<Session,Deque<String>> entry:pending.entrySet())
			{
				if(entry.getKey().mob()==null)
					continue;
				out.append("<QUEUE>").append(CMLib.xml().convertXMLtoTag("WHO",InstanceIds.reference(entry.getKey().mob())));
				for(final String page:entry.getValue())
					out.append(CMLib.xml().convertXMLtoTag("PAGE",CMLib.xml().parseOutAngleBrackets(page)));
				out.append("</QUEUE>");
			}
			for(final XMLTag queue:restored)
				out.append(queue.toString());
			return out.append("</NARRATION>").toString();
		}
	}
	public static void fromXML(final XMLTag tag)
	{
		synchronized(pending)
		{
			for(final Session session:pending.keySet())
				session.prompt((Session.InputCallback)null);
			pending.clear();
			restored.clear();
			resume=tag!=null&&CMath.s_bool(tag.getParmValue("RESUME"));
			if(tag!=null)
				restored.addAll(tag.contents());
		}
	}
	public static void resumeSaved()
	{
		synchronized(pending)
		{
			for(final XMLTag queue:new ArrayList<XMLTag>(restored))
			{
				final Environmental who=InstanceIds.find(queue.getValFromPieces("WHO"));
				if(!(who instanceof MOB)||((MOB)who).session()==null)
					continue;
				final Session session=((MOB)who).session();
				final Deque<String> pages=new ArrayDeque<String>();
				for(final XMLTag page:queue.contents())
					if(page.tag().equals("PAGE"))
						pages.add(CMLib.xml().restoreAngleBrackets(page.value()));
				restored.remove(queue);
				if(pages.isEmpty())
					continue;
				if(session.ID().equals("PartySession")||session.ID().equals("WindowSession")&&!CharacterWindow.canOpen())
				{
					final Session terminal=Party.terminalOf((MOB)who);
					if(terminal!=null&&terminal.mob()!=null)
						for(final String page:pages)
							terminal.mob().tell("["+who.Name()+"] "+page);
					continue;
				}
				pending.put(session,pages);
				if(resume&&!GameTime.isTurnBased())
					GameTime.pause();
				session.prompt(new Session.InputCallback(Session.InputCallback.Type.PROMPT,"",0)
				{
					@Override public void showPrompt()
					{
						session.promptPrint("[Enter to continue] ");
					}
					@Override public void timedOut()
					{
						cancel(session);
					}
					@Override public void callBack()
					{
						next(session);
					}
				});
			}
			if(pending.isEmpty()&&restored.isEmpty()&&resume)
			{
				resume=false;
				GameTime.resume();
			}
		}
	}
}
