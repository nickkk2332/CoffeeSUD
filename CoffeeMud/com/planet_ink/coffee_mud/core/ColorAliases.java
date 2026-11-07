package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.ColorLibrary;
/** Named colours expand before the normal colour and wrapping filters. */
public final class ColorAliases
{
	private ColorAliases()
	{
	}
	private static final Pattern ALIAS=Pattern.compile("\\^\\{([A-Za-z0-9_]+)\\}");
	public static Map<String,String> definitions(final MOB who)
	{
		final Map<String,String> map=new TreeMap<String,String>();
		for(final String pair:CMProps.instance().getStr("COLORALIASES").split(","))
		{
			final int x=pair.indexOf('=');
			if(x>0)
				map.put(pair.substring(0,x).trim().toUpperCase(),pair.substring(x+1).trim());
		}
		if(who!=null)
			for(final Map.Entry<String,Object> e:StoryRecords.object(StoryRecords.read(who),"colors").entrySet())
				map.put(e.getKey(),e.getValue().toString());
		return map;
	}
	public static String code(final String value)
	{
		final StringBuilder out=new StringBuilder();
		for(final String part:value.split("\\+"))
		{
			final String p=part.trim();
			if(p.matches("\\^[A-Za-z!_/.*]")||p.matches("\\^?##[A-Fa-f0-9]{6}"))
				out.append(p.startsWith("^")?p:"^"+p);
			else if(p.length()==1&&Character.isLetter(p.charAt(0)))
				out.append('^').append(p);
			else if(p.equalsIgnoreCase("BOLD"))
				out.append("^!");
			else if(p.equalsIgnoreCase("UNDERLINE"))
				out.append("^_");
			else if(p.equalsIgnoreCase("ITALICS"))
				out.append("^/");
			else
			{
				final ColorLibrary.Color c=(ColorLibrary.Color)CMath.s_valueOf(ColorLibrary.Color.class,p.toUpperCase());
				if(c!=null)
					out.append('^').append(c.getCodeChar());
			}
		}
		return out.toString();
	}
	public static String expand(final String text,final MOB who)
	{
		if(text==null||text.indexOf("^{")<0)
			return text;
		final Map<String,String> map=definitions(who);
		final Matcher m=ALIAS.matcher(text);
		final StringBuffer out=new StringBuffer();
		while(m.find())
			m.appendReplacement(out,Matcher.quoteReplacement(code(map.getOrDefault(m.group(1).toUpperCase(),""))));
		m.appendTail(out);
		return out.toString();
	}
	public static String strip(final String text)
	{
		return text==null?"":ALIAS.matcher(text).replaceAll("");
	}
	public static boolean command(final MOB who,final List<String> words)
	{
		if(words.size()<2)
			return false;
		final String name=words.get(1).toUpperCase();
		final Map<String,String> defs=definitions(who);
		if(name.equals("ALIASES"))
		{
			final StringBuilder out=new StringBuilder("Colour aliases:\n\r");
			for(final Map.Entry<String,String> e:defs.entrySet())
				out.append(e.getKey()).append(" = ").append(e.getValue()).append("\n\r");
			who.tell(out.toString());
			return true;
		}
		if(words.size()<3||!defs.containsKey(name))
			return false;
		final String value=CMParms.combine(words,2);
		if(!value.equalsIgnoreCase("DEFAULT")&&code(value).isEmpty())
		{
			who.tell("Unknown colour. Use a colour name, ^r, or ##RRGGBB.");
			return true;
		}
		synchronized(who)
		{
			final MiniJSON.JSONObject data=StoryRecords.read(who),colors=StoryRecords.object(data,"colors");
			if(value.equalsIgnoreCase("DEFAULT"))
				colors.remove(name);
			else
				colors.put(name,value);
			StoryRecords.write(who,data);
		}
		who.tell("Colour alias updated.");
		return true;
	}
}
