package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.io.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
public final class Presentation
{
	private Presentation()
	{
	}
	public static String line(final MOB who,final Environmental host,final String text)
	{
		String out=StoryText.render(who,host,text);
		if(out.indexOf('%')>=0)
			out=CMLib.utensils().buildPrompt(who,out);
		return out.replaceFirst("^[\\r\\n]+","");
	}
	public static String score(final MOB who)
	{
		final File f=new File(WorldFiles.worldDir(),"score.txt");
		if(!f.isFile())
			return "";
		final StringBuilder out=new StringBuilder();
		for(final String l:LoreBook.readFile(WorldFiles.worldDir(),"score.txt").split("\\r?\\n"))
		{
			final String rendered=line(who,who,l);
			if(!CMStrings.removeColors(rendered).trim().isEmpty())
				out.append(rendered).append("\n\r");
		}
		return out.toString();
	}
	public static String dark(final MOB who,final Room room)
	{
		if(!CMLib.flags().isInDark(room)||!ViewRules.allowed(room,who))
			return "";
		final Ability a=room.fetchEffect("Prop_DarkDescription");
		if(a!=null)
			return StoryText.render(who,room,Scenery.option(a.text(),"TITLE","Somewhere dark"))+"\n\r"
				+StoryText.render(who,room,Scenery.option(a.text(),"DESC",""))+"\n\r";
		if(room.description().startsWith("<VARIES>")&&room.description().contains("<DARK>"))
			return StoryText.render(who,room,room.description())+"\n\r";
		return "";
	}
}
