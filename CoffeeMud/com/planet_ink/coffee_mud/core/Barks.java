package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
/**
 * N22: Sounder's bark table: entries EVENT[(arg)]: [CHANCE=n] [COOLDOWN=n]
 * [ONCEPERPLAYER] line | line ..., inline (between ;) or one to a line in
 * FILE=path.  A line: [weight] [{mask}] [SAY|EMOTE|SOUND] text, $n the one
 * it is about; lines are drawn by weight with the game's dice.  This class
 * reads the table and picks the line; Sounder notices the events.
 */
public final class Barks
{
	/** one line */
	public static final class Line
	{
        public Ambience.Line condition=null;
		/** its weight */
		public int		weight	= 1;
		/** a zapper mask on the one it is about, or null */
		public String	mask	= null;
		/** SAY, EMOTE or SOUND */
		public String	kind	= "EMOTE";
		/** the words */
		public String	text	= "";
	}

	/** one event's entry */
	public static final class Entry
	{
		/** the event: GREET, NIGHT, HURT, WIELD... */
		public String		event		= "";
		/** its argument: a mask, or HURT's percent, or "" */
		public String		arg			= "";
		/** the percent chance it barks at all */
		public int			chance		= 100;
		/** ticks before it barks again */
		public int			cooldown	= 0;
		/** each character once only */
		public boolean		oncePer		= false;
		/** its lines */
		public final List<Line>	lines	= new ArrayList<Line>();
	}

	/** the events the table knows */
	public static final String[]	EVENTS	= { "GREET", "GREET_FIRST", "KILL", "FLEE", "ALLY_DIES", "IDLE", "DAWN", "DAY", "DUSK", "NIGHT",
		"RAIN", "SNOW", "CLEAR", "HURT", "SEE_ITEM", "SEE_MOB", "SHOP_LIST", "SHOP_BUY", "BRIBED", "WIELD", "WEAR", "HOLD", "GET", "DROP", "FIGHT" };

	private static final Pattern	HEAD	= Pattern.compile("^\\s*([A-Za-z_]+)\\s*(?:\\((.*?)\\)|<\\s*(\\d+)%?)?\\s*:(.*)$", Pattern.DOTALL);

	private Barks()
	{
	}

	/**
	 * @param s an entry of Sounder's parameters
	 * @return true if it is a bark table entry (EVENT: ...), not an old one
	 */
	public static boolean isEntry(final String s)
	{
		final Matcher m = HEAD.matcher(s);
		return m.matches() && (CMParms.indexOf(EVENTS, m.group(1).toUpperCase()) >= 0);
	}

	/**
	 * Reads one entry.
	 * @param s EVENT[(arg)]: options lines
	 * @return the entry, or null
	 */
	public static Entry parse(final String s)
	{
		final Matcher m = HEAD.matcher(s);
		if(!m.matches())
			return null;
		final Entry E = new Entry();
		E.event = m.group(1).toUpperCase();
		if(CMParms.indexOf(EVENTS, E.event) < 0)
			return null;
		E.arg = (m.group(2) != null) ? m.group(2).trim() : ((m.group(3) != null) ? m.group(3).trim() : "");
		String rest = m.group(4).trim();
		// options before the lines
		while(true)
		{
			final String up = rest.toUpperCase();
			if(up.startsWith("CHANCE=") || up.startsWith("COOLDOWN="))
			{
				int e = 0;
				while((e < rest.length()) && (!Character.isWhitespace(rest.charAt(e))))
					e++;
				final String opt = rest.substring(0, e);
				final int v = CMath.s_int(opt.substring(opt.indexOf('=') + 1));
				if(up.startsWith("CHANCE="))
					E.chance = v;
				else
					E.cooldown = v;
				rest = rest.substring(e).trim();
				continue;
			}
			if(up.startsWith("ONCEPERPLAYER"))
			{
				E.oncePer = true;
				rest = rest.substring(13).trim();
				continue;
			}
			break;
		}
		for(final String part : rest.split("\\|"))
		{
			String t = part.trim();
			if(t.length() == 0)
				continue;
			final Line L = new Line();
			int e = 0;
			while((e < t.length()) && Character.isDigit(t.charAt(e)))
				e++;
			if((e > 0) && ((e == t.length()) || Character.isWhitespace(t.charAt(e))))
			{
				L.weight = Math.max(1, CMath.s_int(t.substring(0, e)));
				t = t.substring(e).trim();
			}
			if(t.startsWith("{"))
			{
				final int x = t.indexOf('}');
				if(x > 0)
				{
					L.mask = t.substring(1, x).trim();
					t = t.substring(x + 1).trim();
				}
			}
            L.condition=Ambience.parse(t);
            L.weight=L.condition.weight==1?L.weight:L.condition.weight;
            if(!L.condition.kind.isEmpty())L.kind=L.condition.kind.equals("SIGHT")?"EMOTE":L.condition.kind;
            t=L.condition.text;
			final String up = t.toUpperCase();
			if(up.startsWith("SAY "))
			{
				L.kind = "SAY";
				t = t.substring(4).trim();
			}
			else
			if(up.startsWith("EMOTE "))
			{
				L.kind = "EMOTE";
				t = t.substring(6).trim();
			}
			else
			if(up.startsWith("SOUND "))
			{
				L.kind = "SOUND";
				t = t.substring(6).trim();
			}
			else
			if(t.startsWith("\""))
			{
				L.kind = "SAY";
				t = t.substring(1).trim();
				if(t.endsWith("\""))
					t = t.substring(0, t.length() - 1);
			}
			else
			if(t.startsWith(":"))
			{
				L.kind = "EMOTE";
				t = t.substring(1).trim();
			}
			L.text = t;
			if(t.length() > 0)
				E.lines.add(L);
		}
		return E.lines.isEmpty() ? null : E;
	}

	/**
	 * Draws a line by weight, of those whose mask fits.
	 * @param E the entry
	 * @param about the one it is about, or null
	 * @return the line, or null
	 */
	public static Line pick(final Entry E, final Environmental about)
	{
        return pick(E,about,about);
    }
    public static Line pick(final Entry E,final Environmental about,final Environmental host)
    {
        final com.planet_ink.coffee_mud.Locales.interfaces.Room room=CMLib.map().roomLocation(host);
        final MOB viewer=about instanceof MOB?(MOB)about:host instanceof MOB?(MOB)host:null;
		final List<Line> fit = new ArrayList<Line>();
		int total = 0;
		for(final Line L : E.lines)
		{
            if(L.condition!=null&&!Ambience.fits(L.condition,viewer,host,room)) continue;
			if((L.mask != null) && (L.mask.length() > 0))
			{
				if((about == null) || (!CMLib.masking().maskCheck(L.mask, about, true)))
					continue;
			}
			fit.add(L);
			total += L.weight;
		}
		if(total <= 0)
			return null;
		int roll = CMLib.dice().roll(1, total, 0);
		for(final Line L : fit)
		{
			roll -= L.weight;
			if(roll <= 0)
				return L;
		}
		return fit.get(fit.size() - 1);
	}

	/**
	 * @param E an entry
	 * @param about the one it is about
	 * @return true if its argument (a mask) fits them, or it has none
	 */
	public static boolean argFits(final Entry E, final Environmental about)
	{
		if((E.arg.length() == 0) || E.event.equals("HURT"))
			return true;
		if(about == null)
			return false;
		if(E.arg.startsWith("+") || E.arg.startsWith("-"))
			return CMLib.masking().maskCheck(E.arg, about, true);
		return CMLib.english().containsString(about.Name(), E.arg);
	}

	/**
	 * @param line a line's words
	 * @param about the one it is about, or null
	 * @return $n as their name
	 */
	public static String fill(final String line, final MOB about)
	{
		return line.replace("$n", (about == null) ? "" : about.name()).replace("$N", (about == null) ? "" : about.name());
	}
}
