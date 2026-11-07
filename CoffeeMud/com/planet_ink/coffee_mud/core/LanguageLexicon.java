package com.planet_ink.coffee_mud.core;
import java.util.*;
import org.mozilla.javascript.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Generic-language dictionaries and approved, bounded rule scripts. */
public final class LanguageLexicon
{
	private LanguageLexicon()
	{
	}
	private static final Map<String,Map<String,String>> dictionaries=new HashMap<String,Map<String,String>>();
	private static volatile long namesUntil=0;
	private static volatile Set<String> names=Collections.emptySet();
	private static synchronized Set<String> names()
	{
		if(System.currentTimeMillis()<namesUntil)
			return names;
		final Set<String> found=new HashSet<String>();
		for(final Enumeration<com.planet_ink.coffee_mud.Areas.interfaces.Area> e=CMLib.map().areas();e.hasMoreElements();)
			found.add(e.nextElement().Name().toLowerCase(java.util.Locale.ROOT));
		for(final Enumeration<com.planet_ink.coffee_mud.Locales.interfaces.Room> e=CMLib.map().rooms();e.hasMoreElements();)
		{
			final com.planet_ink.coffee_mud.Locales.interfaces.Room r=e.nextElement();
			for(final Enumeration<com.planet_ink.coffee_mud.MOBS.interfaces.MOB> m=r.inhabitants();m.hasMoreElements();)
				found.add(m.nextElement().Name().toLowerCase(java.util.Locale.ROOT));
		}
		for(final String name:CMLib.catalog().getCatalogMobNames())
			found.add(name.toLowerCase(java.util.Locale.ROOT));
		for(final String name:CMLib.catalog().getCatalogItemNames())
			found.add(name.toLowerCase(java.util.Locale.ROOT));
		names=Collections.unmodifiableSet(found);
		namesUntil=System.currentTimeMillis()+30000;
		return names;
	}
	private static final Map<String,Scriptable> scripts=new HashMap<String,Scriptable>();
	public static void clear()
	{
		synchronized(dictionaries)
		{
			dictionaries.clear();
		}
		synchronized(scripts)
		{
			scripts.clear();
		}
		namesUntil=0;
	}
	public static int interpretation(final String list,final String language)
	{
		for(final String part:list.split(","))
		{
			final String p=part.trim();
			final int at=p.indexOf('(');
			if((at<0?p:p.substring(0,at)).equalsIgnoreCase(language))
				return at<0?100:Math.max(0,Math.min(100,CMath.s_int(p.substring(at+1).replace(")",""))));
		}
		return 0;
	}
	public static String dictionary(final Language l,final String word)
	{
		final String file=l.getStat("WORDFILE");
		if(file.isEmpty())
			return null;
		final java.io.File f=Vocabulary.file(file.startsWith("vocab/")?file:"vocab/languages/"+file);
		if(f==null||!f.isFile())
			return null;
		final String key=f.getPath()+":"+f.lastModified()+":"+f.length();
		Map<String,String> words;
		synchronized(dictionaries)
		{
			words=dictionaries.get(key);
			if(words==null)
			{
				words=new HashMap<String,String>();
				for(final String line:Vocabulary.read(file.startsWith("vocab/")?file:"vocab/languages/"+file).split("\\r?\\n"))
				{
					final int at=line.indexOf('=');
					if(at>0&&!line.trim().startsWith("#"))
						words.put(line.substring(0,at).trim().toUpperCase(),line.substring(at+1).trim());
				}
				if(dictionaries.size()>100)
					dictionaries.clear();
				dictionaries.put(key,words);
			}
		}
		return words.get(word.toUpperCase());
	}
	public static boolean keep(final Language l,final String word)
	{
		for(final String s:CMParms.parseCommas(l.getStat("KEEPWORDS"),true))
			if(s.equalsIgnoreCase(word))
				return true;
		if(CMath.s_bool(l.getStat("KEEPNAMES"))&&!word.isEmpty())
		{
			if(Character.isUpperCase(word.charAt(0)))
				return true;
			if(names().contains(word.toLowerCase(java.util.Locale.ROOT)))
				return true;
		}
		return false;
	}
	public static String translate(final Language l,final String word)
	{
		if(keep(l,word))
			return word;
		final String scripted=script(l,"translate",word,l.ID());
		if(scripted!=null)
			return scripted;
		String result=l.translationHash(l.ID()).get(word.toUpperCase());
		if(result==null)
			result=dictionary(l,word);
		if(result!=null)
			return Vocabulary.caseLike(word,result);
		result=suffix(l,word);
		if(result!=null)
			return result;
		final String syllables=l.getStat("SYLLABLES");
		if(!syllables.isEmpty())
		{
			final String[] banks=syllables.split("/");
			final StringBuilder out=new StringBuilder();
			final int count=Math.max(1,Math.min(4,(word.length()+2)/3));
			for(int i=0;i<count;i++)
			{
				final List<String> bank=CMParms.parseCommas(banks[i%banks.length],true);
				if(!bank.isEmpty())
					out.append(bank.get(Math.floorMod((word.toLowerCase()+":"+i).hashCode(),bank.size())));
			}
			return Vocabulary.caseLike(word,out.toString());
		}
		return null;
	}
	public static String suffix(final Language l,final String word)
	{
		String result;
		for(final String pair:CMParms.parseCommas(l.getStat("SUFFIXES"),true))
		{
			final int at=pair.indexOf(':');
			if(at<1)
				continue;
			final String ending=pair.substring(0,at);
			if(word.length()>ending.length()&&word.toUpperCase().endsWith(ending.toUpperCase()))
			{
				String stem=word.substring(0,word.length()-ending.length());
				result=l.translationHash(l.ID()).get(stem.toUpperCase());
				if(result==null)
					result=dictionary(l,stem);
				if(result==null&&ending.equalsIgnoreCase("S")&&stem.toLowerCase().endsWith("ie"))
				{
					final String singular=stem.substring(0,stem.length()-2)+"y";
					result=l.translationHash(l.ID()).get(singular.toUpperCase());
					if(result==null)
						result=dictionary(l,singular);
				}
				if(result==null&&stem.endsWith("i"))
				{
					stem=stem.substring(0,stem.length()-1)+"y";
					result=dictionary(l,stem);
				}
				if(result!=null)
					return Vocabulary.caseLike(word,result+pair.substring(at+1));
			}
		}
		return null;
	}
	public static String source(final Language l,final String word)
	{
		if(keep(l,word))
			return "kept word or name";
		if(!l.getStat("TRANSLATESCRIPT").isEmpty()&&CMSecurity.isApprovedJScript(new StringBuffer(l.getStat("TRANSLATESCRIPT"))))
			return "translation script (or normal fallback)";
		if(l.translationHash(l.ID()).containsKey(word.toUpperCase()))
			return "dictionary";
		if(dictionary(l,word)!=null)
			return "dictionary file";
		if(suffix(l,word)!=null)
			return "dictionary root and suffix";
		if(!l.getStat("SYLLABLES").isEmpty())
			return "syllable banks";
		return null;
	}
	public static String script(final Language l,final String function,final Object... args)
	{
		final String body=l.getStat("TRANSLATESCRIPT");
		if(body.isEmpty()||!CMSecurity.isApprovedJScript(new StringBuffer(body)))
			return null;
		final String key=l.ID()+":"+body;
		final Context cx=JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			Scriptable scope;
			synchronized(scripts)
			{
				scope=scripts.get(key);
				if(scope==null)
				{
					scope=cx.initStandardObjects();
					cx.evaluateString(scope,body,l.ID()+" TRANSLATESCRIPT",1,null);
					if(scripts.size()>100)
						scripts.clear();
					scripts.put(key,scope);
				}
			}
			synchronized(scope)
			{
				final Object f=ScriptableObject.getProperty(scope,function);
				if(!(f instanceof Function))
					return null;
				final Object[] js=new Object[args.length];
				for(int i=0;i<args.length;i++)
					js[i]=Context.javaToJS(args[i],scope);
				final Object value=((Function)f).call(cx,scope,scope,js);
				return value==null||value instanceof Undefined?null:Context.toString(value);
			}
		}
		catch(final Exception e)
		{
			JSRuntime.report(l.ID()+" TRANSLATESCRIPT",e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}
}
