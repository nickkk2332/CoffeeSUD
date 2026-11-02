package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Coins;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
 * N15: one NPC's (or one village's) opinion of each character, a number:
 * HOSTILE below -50, COLD below 0, NEUTRAL, FRIENDLY from 25, DEVOTED from
 * 75.  Prop_Affinity on the NPC, or on its area for the whole village,
 * says what moves it (gifts, talk, quests won, attacks, theft, killings,
 * bribes), how fast it fades toward 0 (DECAY a game day), what it does to
 * prices (SHOPFACTOR) and below which band the NPC will not trade
 * (REFUSE).  The values are the game's script variables
 * AFFINITY_[CHARACTER] of the NPC (or the area), so a save keeps them;
 * AFFINITY(npc who) and MPAFFINITY read and change them.
 */
public final class NpcAffinity
{
	private NpcAffinity()
	{
	}

	/** the property's ID */
	public static final String		ID		= "Prop_Affinity";

	/** the bands, lowest first */
	public static final String[]	BANDS	= { "HOSTILE", "COLD", "NEUTRAL", "FRIENDLY", "DEVOTED" };

	/** a rule: what, an item or mob mask or word or quest, how much */
	public static final class Rule
	{
		/** GIFT, TALK, QUESTWIN, ATTACKED, STOLE, KILLED */
		public String	kind	= "";
		/** what it is about, or "" */
		public String	about	= "";
		/** how much */
		public long		by		= 0;
	}

	/** what a Prop_Affinity says */
	public static final class Rules
	{
		/** where a character starts */
		public long					start		= 0;
		/** how much it fades toward 0 a game day */
		public long					decay		= 0;
		/** a bribe: points for each gold */
		public double				bribe		= 0;
		/** the rules */
		public final List<Rule>		rules		= new ArrayList<Rule>();
		/** price multipliers by band */
		public final Map<String, Double>	shop	= new HashMap<String, Double>();
		/** the band at or below which it will not trade, -1 for none */
		public int					refuse		= -1;
		/** what it says when it will not */
		public String				refuseMsg	= "I won't deal with you.";
	}

	private static final Map<String, Rules>	parsed	= new Hashtable<String, Rules>();
	private static ScriptingEngine			vars	= null;

	private static ScriptingEngine vars()
	{
		if(vars == null)
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setSavable(false);
			S.setVarScope("");
			vars = S;
		}
		return vars;
	}

	/**
	 * @param v a value
	 * @return its band
	 */
	public static String band(final long v)
	{
		if(v < -50)
			return "HOSTILE";
		if(v < 0)
			return "COLD";
		if(v < 25)
			return "NEUTRAL";
		if(v < 75)
			return "FRIENDLY";
		return "DEVOTED";
	}

	/**
	 * @param name a band's name
	 * @return its place, lowest 0, or -1
	 */
	public static int bandIndex(final String name)
	{
		return CMParms.indexOf(BANDS, (name == null) ? "" : name.trim().toUpperCase());
	}

	/**
	 * @param text a Prop_Affinity's text
	 * @return what it says
	 */
	public static Rules rules(final String text)
	{
		final String t = (text == null) ? "" : text;
		Rules R = parsed.get(t);
		if(R != null)
			return R;
		R = new Rules();
		int i = 0;
		while(i < t.length())
		{
			while((i < t.length()) && Character.isWhitespace(t.charAt(i)))
				i++;
			int e = i;
			while((e < t.length()) && (Character.isLetterOrDigit(t.charAt(e)) || (t.charAt(e) == '_')))
				e++;
			final String name = t.substring(i, e).toUpperCase();
			i = e;
			String about = "";
			if((i < t.length()) && (t.charAt(i) == '('))
			{
				int depth = 0;
				e = i;
				for(; e < t.length(); e++)
				{
					if(t.charAt(e) == '(')
						depth++;
					else
					if((t.charAt(e) == ')') && ((--depth) == 0))
						break;
				}
				about = t.substring(i + 1, Math.min(e, t.length())).trim();
				i = Math.min(e + 1, t.length());
			}
			if((i >= t.length()) || (t.charAt(i) != '='))
			{
				i = Math.max(i + 1, e);
				continue;
			}
			i++;
			String value;
			if((i < t.length()) && (t.charAt(i) == '"'))
			{
				e = t.indexOf('"', i + 1);
				if(e < 0)
					e = t.length();
				value = t.substring(i + 1, e);
				i = Math.min(e + 1, t.length());
			}
			else
			{
				e = i;
				while((e < t.length()) && (!Character.isWhitespace(t.charAt(e))))
					e++;
				value = t.substring(i, e);
				i = e;
			}
			if(name.equals("START"))
				R.start = CMath.s_long(value.replace("+", ""));
			else
			if(name.equals("DECAY"))
				R.decay = Math.abs(CMath.s_long(value.replace("+", "")));
			else
			if(name.equals("BRIBE"))
				R.bribe = CMath.s_double(value.replace("+", ""));
			else
			if(name.equals("REFUSE"))
				R.refuse = bandIndex(value);
			else
			if(name.equals("REFUSEMSG"))
				R.refuseMsg = value;
			else
			if(name.equals("SHOPFACTOR"))
			{
				for(final String p : CMParms.parseCommas(value, true))
				{
					final int x = p.indexOf(':');
					if((x > 0) && (bandIndex(p.substring(0, x)) >= 0))
						R.shop.put(p.substring(0, x).trim().toUpperCase(), Double.valueOf(CMath.s_double(p.substring(x + 1).trim())));
				}
			}
			else
			if(name.equals("GIFT") || name.equals("TALK") || name.equals("QUESTWIN") || name.equals("ATTACKED")
			|| name.equals("STOLE") || name.equals("CAUGHT_STEALING") || name.equals("KILLED"))
			{
				final Rule r = new Rule();
				r.kind = name.equals("CAUGHT_STEALING") ? "STOLE" : name;
				r.about = about;
				r.by = CMath.s_long(value.replace("+", ""));
				R.rules.add(r);
			}
		}
		parsed.put(t, R);
		return R;
	}

	/**
	 * @param npc an NPC
	 * @return the Prop_Affinity whose view it holds: its own, else its area's, or null
	 */
	public static Ability propOf(final MOB npc)
	{
		if(npc == null)
			return null;
		final Ability own = npc.fetchEffect(ID);
		if(own != null)
			return own;
		final Area A = CMLib.map().areaLocation(npc);
		return (A == null) ? null : A.fetchEffect(ID);
	}

	/**
	 * @param npc an NPC
	 * @return the name its values are kept under: its own, or its area's if the area holds the view
	 */
	public static String holderOf(final MOB npc)
	{
		if((npc.fetchEffect(ID) == null))
		{
			final Area A = CMLib.map().areaLocation(npc);
			if((A != null) && (A.fetchEffect(ID) != null))
				return A.Name();
		}
		return npc.Name();
	}

	private static String key(final String who)
	{
		return who.toUpperCase().replace(' ', '_');
	}

	private static long dayMillis()
	{
		return Math.max(1, CMProps.getMillisPerMudHour() * CMLib.time().globalClock().getHoursInDay());
	}

	/**
	 * An NPC's opinion of a character, faded and with their quests won counted.
	 * @param npc the NPC
	 * @param who the character's name
	 * @return the value
	 */
	public static long get(final MOB npc, final String who)
	{
		if((npc == null) || (who == null))
			return 0;
		final Ability P = propOf(npc);
		final Rules R = rules((P == null) ? "" : P.text());
		return get(holderOf(npc), who, R);
	}

	private static long get(final String holder, final String who, final Rules R)
	{
		final ScriptingEngine S = vars();
		final String k = key(who);
		final String raw = S.getVar(holder, "AFFINITY_" + k);
		long v = (raw.length() == 0) ? R.start : CMath.s_long(raw);
		boolean changed = (raw.length() == 0) && (R.start != 0);
		// fading toward 0
		final long now = GameTime.now();
		final String atRaw = S.getVar(holder, "AFFINITYAT_" + k);
		long at = (atRaw.length() == 0) ? now : CMath.s_long(atRaw);
		if((R.decay > 0) && (v != 0))
		{
			final long days = (now - at) / dayMillis();
			if(days > 0)
			{
				final long fade = Math.min(Math.abs(v), days * R.decay);
				v += (v > 0) ? -fade : fade;
				at += days * dayMillis();
				changed = true;
			}
		}
		else
			at = now;
		// quests won since last counted
		for(final Rule r : R.rules)
		{
			if(!r.kind.equals("QUESTWIN") || (r.about.length() == 0))
				continue;
			final String qk = "AFFINITYQ_" + k + "_" + key(r.about);
			final int wins = QuestRecords.wins(r.about, who);
			final int counted = CMath.s_int(S.getVar(holder, qk));
			if(wins > counted)
			{
				v += r.by * (wins - counted);
				S.setVar(holder, qk, "=" + wins); // "=": as it is, not added (SC11)
				changed = true;
			}
		}
		if(changed || (atRaw.length() == 0))
		{
			S.setVar(holder, "AFFINITY_" + k, "=" + v); // "=": as it is, not added (SC11)
			S.setVar(holder, "AFFINITYAT_" + k, "=" + at); // "=": as it is, not added (SC11)
		}
		return v;
	}

	/**
	 * Changes an NPC's opinion of a character.
	 * @param npc the NPC
	 * @param who the character's name
	 * @param by how much, + or -
	 * @return the new value
	 */
	public static long change(final MOB npc, final String who, final long by)
	{
		final long v = get(npc, who) + by;
		set(npc, who, v);
		return v;
	}

	/**
	 * Sets an NPC's opinion of a character.
	 * @param npc the NPC
	 * @param who the character's name
	 * @param v the value
	 */
	public static void set(final MOB npc, final String who, final long v)
	{
		if((npc == null) || (who == null))
			return;
		get(npc, who); // fade and count first
		final String holder = holderOf(npc);
		vars().setVar(holder, "AFFINITY_" + key(who), "=" + v); // "=": as it is, not added (SC11)
		vars().setVar(holder, "AFFINITYAT_" + key(who), "=" + GameTime.now());
	}

	/**
	 * Whether a character may do this rule's thing again now: once a game
	 * hour for an attack, once a game day for a word.
	 * @param npc the NPC
	 * @param who the character
	 * @param what a name for it
	 * @param every how long between, in game milliseconds
	 * @return true if it counts now
	 */
	private static boolean counts(final MOB npc, final String who, final String what, final long every)
	{
		final String holder = holderOf(npc);
		final String k = "AFFINITYLAST_" + key(who) + "_" + key(what);
		final String last = vars().getVar(holder, k);
		final long now = GameTime.now();
		if((last.length() > 0) && ((now - CMath.s_long(last)) < every))
			return false;
		vars().setVar(holder, k, "=" + now); // "=": as it is, not added (SC11)
		return true;
	}

	private static boolean fits(final String about, final Environmental E)
	{
		if(about.length() == 0)
			return true;
		if(about.startsWith("+") || about.startsWith("-"))
			return CMLib.masking().maskCheck(about, E, true);
		return CMLib.english().containsString(E.Name(), about);
	}

	/**
	 * Something a character did to an NPC (or near it) that its rules count.
	 * @param npc the NPC
	 * @param who the character
	 * @param kind GIFT, TALK, ATTACKED, STOLE or KILLED
	 * @param what the item given, the words said, the mob killed, or null
	 * @return how much it changed
	 */
	public static long event(final MOB npc, final MOB who, final String kind, final Object what)
	{
		final Ability P = propOf(npc);
		if((P == null) || (who == null) || (who.playerStats() == null))
			return 0;
		final Rules R = rules(P.text());
		long by = 0;
		if(kind.equals("GIFT") && (what instanceof Coins))
			by = Math.round(R.bribe * ((Coins)what).getTotalValue());
		else
		for(final Rule r : R.rules)
		{
			if(!r.kind.equals(kind))
				continue;
			if(kind.equals("GIFT") && (what instanceof Item) && fits(r.about, (Item)what))
			{
				by = r.by;
				break;
			}
			if(kind.equals("KILLED") && (what instanceof MOB) && fits(r.about, (MOB)what))
			{
				by = r.by;
				break;
			}
			if(kind.equals("TALK") && (what instanceof String)
			&& (" " + ((String)what).toUpperCase().replaceAll("[^A-Z0-9]+", " ") + " ").indexOf(" " + r.about.toUpperCase().trim() + " ") >= 0)
			{
				if(counts(npc, who.Name(), "TALK " + r.about, dayMillis()))
					by = r.by;
				break;
			}
			if(kind.equals("ATTACKED"))
			{
				if(counts(npc, who.Name(), "ATTACKED", CMProps.getMillisPerMudHour()))
					by = r.by;
				break;
			}
			if(kind.equals("STOLE"))
			{
				by = r.by;
				break;
			}
		}
		if(by != 0)
			change(npc, who.Name(), by);
		return by;
	}

	/**
	 * N9's TALK: a topic talked of, for TALK(word) rules.
	 * @param npc the NPC
	 * @param who the character
	 * @param topic the topic
	 */
	public static void heard(final MOB npc, final MOB who, final String topic)
	{
		if((topic != null) && (topic.trim().length() > 0))
			event(npc, who, "TALK", topic);
	}

	/**
	 * A shop's price multiplier for a customer, by its SHOPFACTOR.
	 * @param shop the shopkeeper
	 * @param customer the customer
	 * @return the multiplier, 1 for none
	 */
	public static double shopFactor(final MOB shop, final MOB customer)
	{
		if((shop == null) || (customer == null) || (customer.playerStats() == null))
			return 1.0;
		final Ability P = propOf(shop);
		if(P == null)
			return 1.0;
		final Rules R = rules(P.text());
		if(R.shop.isEmpty())
			return 1.0;
		final Double f = R.shop.get(band(get(shop, customer.Name())));
		return (f == null) ? 1.0 : f.doubleValue();
	}

	/**
	 * @param npc an NPC
	 * @param who a character
	 * @return true if it will not trade with them (REFUSE)
	 */
	public static boolean refuses(final MOB npc, final MOB who)
	{
		final Ability P = propOf(npc);
		if((P == null) || (who == null) || (who.playerStats() == null))
			return false;
		final Rules R = rules(P.text());
		return (R.refuse >= 0) && (bandIndex(band(get(npc, who.Name()))) <= R.refuse);
	}

	/**
	 * @param npc an NPC
	 * @return what it says when it will not trade
	 */
	public static String refuseMessage(final MOB npc)
	{
		final Ability P = propOf(npc);
		return rules((P == null) ? "" : P.text()).refuseMsg;
	}
}
