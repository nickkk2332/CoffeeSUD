package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
/** World-authored codex definitions; unlocks belong to characters or their profile. */
public final class LoreBook
{
	private LoreBook()
	{
	}
	public static final class Entry
	{
		public String key,title,category,text;
		public Entry(final String k,final String value)
		{
			key=k.toUpperCase();
			final String[] p=value.split("\\|",3);
			title=p[0];
			category=p.length>1?p[1]:"Lore";
			text=p.length>2?p[2]:"";
		}
	}
	public static Map<String,Entry> entries()
	{
		final Map<String,Entry> out=new TreeMap<String,Entry>();
		final File dir=new File(WorldFiles.worldDir(),"lore");
		final File[] files=dir.listFiles((d,n)->n.toLowerCase().endsWith(".ini"));
		if(files==null)
			return out;
		Arrays.sort(files);
		for(final File f:files)
			try(final Reader r=Files.newBufferedReader(f.toPath(),StandardCharsets.UTF_8))
			{
				final Properties p=new Properties();
				p.load(r);
				for(final String key:p.stringPropertyNames())
				{
					final Entry e=new Entry(key,p.getProperty(key));
					if(e.title.startsWith("FILE="))
					{
						e.text=readFile(dir,e.title.substring(5));
						e.title=e.key;
						e.category="Lore";
					}
					if(e.text.startsWith("FILE="))
						e.text=readFile(dir,e.text.substring(5));
					out.put(e.key,e);
				}
			}
			catch(final IOException|IllegalArgumentException ex)
			{
				Log.errOut("LoreBook",ex.getMessage());
			}
		return out;
	}
	public static String readFile(final File root,final String name)
	{
		try
		{
			final File file=new File(root,name).getCanonicalFile();
			if(!file.toPath().startsWith(root.getCanonicalFile().toPath()))
				return "";
			return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
		}
		catch(final IOException e)
		{
			Log.errOut("StoryFile",e.getMessage());
			return "";
		}
	}
	public static boolean known(final MOB who,final String key)
	{
		if(who==null)
			return false;
		final PlayerAccount account=Party.profileOf(who);
		final String k=key.toUpperCase();
		return who.findTattoo("LORE_"+k)!=null||who.findTattoo("LORE:"+k)!=null||(account!=null&&account.findTattoo("LORE_"+k)!=null);
	}
	public static boolean unlock(final MOB who,final String key,final boolean profile,final Environmental source)
	{
		if(who==null||key.trim().isEmpty())
			return false;
		final String k=key.trim().toUpperCase();
		final PlayerAccount account=Party.profileOf(who);
		final Tattooable holder=profile&&account!=null?account:who;
		final boolean fresh=holder.findTattoo("LORE_"+k)==null;
		holder.addTattoo("LORE_"+k);
		if(fresh)
		{
			final Entry e=entries().get(k);
			Chronicle.auto(who,"LORE","Learned "+(e==null?k:e.title)+".",source);
		}
		return fresh;
	}
	public static boolean unread(final MOB who,final String key)
	{
		return !StoryRecords.object(StoryRecords.read(who),"loreRead").containsKey(key.toUpperCase());
	}
	public static String show(final MOB who,final String topic)
	{
		final Map<String,Entry> entries=entries();
		final Map<String,Integer> categories=new TreeMap<String,Integer>();
		final StringBuilder out=new StringBuilder();
		for(final Entry e:entries.values())
			if(known(who,e.key))
			{
				categories.put(e.category,Integer.valueOf(categories.getOrDefault(e.category,Integer.valueOf(0)).intValue()+1));
				if(e.key.equalsIgnoreCase(topic)||e.title.equalsIgnoreCase(topic))
				{
					synchronized(who)
					{
						final MiniJSON.JSONObject d=StoryRecords.read(who);
						StoryRecords.object(d,"loreRead").put(e.key,Boolean.TRUE);
						StoryRecords.write(who,d);
					}
					return "^{LORE}"+e.title+"^N ["+e.category+"]\n\r"+StoryText.render(who,who,e.text);
				}
				if(e.category.equalsIgnoreCase(topic)||(topic.equalsIgnoreCase("NEW")&&unread(who,e.key)))
					out.append(unread(who,e.key)?"* ":"  ").append(e.key).append(": ").append(e.title).append("\n\r");
			}
		if(topic.isEmpty())
		{
			for(final Map.Entry<String,Integer> e:categories.entrySet())
				out.append(e.getKey()).append(" (").append(e.getValue()).append(")\n\r");
		}
		return out.length()==0?"No unlocked lore matches.":out.toString();
	}
	/** Optional help metadata is never displayed as markup. */
	public static String help(final MOB viewer,final String text)
	{
		if(text==null)
			return null;
		final Matcher m=Pattern.compile("<(MASK|LOCKED|CATEGORY)=([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(text);
		String mask="",locked="";
		final StringBuffer out=new StringBuffer();
		while(m.find())
		{
			if(m.group(1).equalsIgnoreCase("MASK"))
				mask=m.group(2);
			if(m.group(1).equalsIgnoreCase("LOCKED"))
				locked=m.group(2);
			m.appendReplacement(out,"");
		}
		m.appendTail(out);
		if(!mask.isEmpty()&&(viewer==null||!CMLib.masking().maskCheck(mask,viewer,true)))
			return locked.isEmpty()?null:locked;
		return out.toString();
	}
}
