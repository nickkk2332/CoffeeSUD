package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import java.util.*;
import java.util.regex.Pattern;

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
 * N6: an NPC that remembers what each character did: MET (with FIRSTMET and
 * LASTMET, game dates), ATTACKED, HURT (damage), HEALED, GAVE (value),
 * STOLE, KILLEDKIN (of KIN="mask"), TALKED, and TRACK=NAME:regex (TRACK1=
 * to TRACK9= more), a regex on what the character is seen to do.  Each is a
 * script variable of the NPC, MEM_[FACT]_[NAME], read as
 * VAR($i MEM_ATTACKED_$n), and kept in a saved game.  FORGET=30d (or 12h, or
 * a game time) lets a fact fade that long after it last grew.  Reactions:
 * HOSTILEIF=cond (it attacks them on sight), GREETIF=cond with GREET="text"
 * ($n is their name), REFUSEIF=cond with REFUSE="text" (no buying,
 * selling or teaching); cond is FACT&gt;N, FACT&gt;=N, FACT=N, FACT&lt;N,
 * joined by &amp; and |.  SHARE=AREA, or a mask, tells the facts to the
 * other Memory NPCs of its area (that fit the mask).
 */
public class Memory extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Memory";
	}

	@Override
	public String name()
	{
		return "a memory of characters";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "remembering what each did";
	}

	/** a fact: its value and when it last grew, on the game clock */
	protected static final class Fact
	{
		long	value	= 0;
		long	at		= 0;
		String	text	= null;
	}

	/** character name (upper) to fact to its record */
	protected final Map<String, Map<String, Fact>>	known		= new TreeMap<String, Map<String, Fact>>();
	/** character name (upper) to the name as written */
	protected final Map<String, String>				names		= new TreeMap<String, String>();

	protected String								kin			= "";
	protected String								share		= "";
	protected long									forgetMs	= 0;
	protected String								hostileIf	= "";
	protected String								greetIf		= "";
	protected String								greet		= "";
	protected String								refuseIf	= "";
	protected String								refuse		= "";
	protected final Map<String, Pattern>			tracks		= new LinkedHashMap<String, Pattern>();
	protected final Set<String>						greeted		= new HashSet<String>();

	private static ScriptingEngine					vars		= null;
	/** whether the facts are script variables yet, after a restore */
	protected boolean								mirrored	= true;

	protected final static Set<Integer>	refused	= new XHashSet<Integer>(new Integer[] {
		Integer.valueOf(CMMsg.TYP_BUY), Integer.valueOf(CMMsg.TYP_SELL), Integer.valueOf(CMMsg.TYP_LIST),
		Integer.valueOf(CMMsg.TYP_VALUE), Integer.valueOf(CMMsg.TYP_BID), Integer.valueOf(CMMsg.TYP_VIEW) });

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		final Map<String, String> P = CMParms.parseEQParms(p);
		kin = P.containsKey("KIN") ? P.get("KIN").trim() : "";
		share = P.containsKey("SHARE") ? P.get("SHARE").trim() : "";
		hostileIf = P.containsKey("HOSTILEIF") ? P.get("HOSTILEIF").trim() : "";
		greetIf = P.containsKey("GREETIF") ? P.get("GREETIF").trim() : "";
		greet = P.containsKey("GREET") ? P.get("GREET") : "";
		refuseIf = P.containsKey("REFUSEIF") ? P.get("REFUSEIF").trim() : "";
		refuse = P.containsKey("REFUSE") ? P.get("REFUSE") : "";
		forgetMs = 0;
		final String forget = P.containsKey("FORGET") ? P.get("FORGET").trim().toLowerCase() : "";
		if(forget.length() > 0)
		{
			final long hour = CMProps.getMillisPerMudHour();
			final TimeClock C = CMLib.time().globalClock();
			final int perDay = ((C == null) || (C.getHoursInDay() <= 0)) ? 24 : C.getHoursInDay();
			if(forget.endsWith("d") && CMath.isInteger(forget.substring(0, forget.length() - 1)))
				forgetMs = CMath.s_long(forget.substring(0, forget.length() - 1)) * perDay * hour;
			else
			if(forget.endsWith("h") && CMath.isInteger(forget.substring(0, forget.length() - 1)))
				forgetMs = CMath.s_long(forget.substring(0, forget.length() - 1)) * hour;
			else
			{
				try
				{
					forgetMs = (long)CMLib.time().parseTickExpression(C, forget) * CMProps.getTickMillis();
				}
				catch(final CMException e)
				{
					Log.errOut("Memory", "FORGET=" + forget + ": " + e.getMessage());
				}
			}
		}
		tracks.clear();
		for(final Map.Entry<String, String> e : P.entrySet())
		{
			if(!e.getKey().toUpperCase().matches("TRACK[0-9]?"))
				continue;
			final String t = e.getValue();
			final int x = t.indexOf(':');
			if(x <= 0)
				continue;
			try
			{
				tracks.put(t.substring(0, x).trim().toUpperCase(), Pattern.compile(t.substring(x + 1), Pattern.CASE_INSENSITIVE));
			}
			catch(final RuntimeException ex)
			{
				Log.errOut("Memory", "TRACK " + t + ": " + ex.getMessage());
			}
		}
	}

	protected static synchronized ScriptingEngine vars()
	{
		if(vars == null)
			vars = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		return vars;
	}

	/** the script variable for a fact */
	protected void mirror(final MOB host, final String who, final String fact, final Fact F)
	{
		final ScriptingEngine S = vars();
		if((S == null) || (host == null))
			return;
		final String name = names.containsKey(who) ? names.get(who) : who;
		S.setVar(host.Name(), "MEM_" + fact + "_" + name, (F == null) ? "" : ((F.text != null) ? F.text : ("" + F.value)));
	}

	/**
	 * A fact grows (or, for dates, is set).
	 * @param host the NPC
	 * @param M the character
	 * @param fact the fact
	 * @param add how much it grows
	 * @param text for a date, its text, else null
	 * @param fromKin true if told by kin (not told on again)
	 */
	protected void learn(final MOB host, final MOB M, final String fact, final long add, final String text, final boolean fromKin)
	{
		learn(host, M.Name(), fact, add, text, fromKin);
	}

	protected void learn(final MOB host, final String whoName, final String fact, final long add, final String text, final boolean fromKin)
	{
		final String who = whoName.toUpperCase();
		names.put(who, whoName);
		Map<String, Fact> facts = known.get(who);
		if(facts == null)
		{
			facts = new TreeMap<String, Fact>();
			known.put(who, facts);
		}
		Fact F = facts.get(fact);
		if(F == null)
		{
			F = new Fact();
			facts.put(fact, F);
		}
		if(text != null)
			F.text = text;
		else
			F.value += add;
		F.at = GameTime.now();
		mirror(host, who, fact, F);
		if((!fromKin) && (share.length() > 0) && (text == null))
			tell(host, whoName, fact, add);
	}

	/** tells the other Memory NPCs of the area (that fit SHARE's mask) */
	protected void tell(final MOB host, final String whoName, final String fact, final long add)
	{
		final Room R = host.location();
		final Area A = (R == null) ? null : R.getArea();
		if(A == null)
			return;
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R2 = r.nextElement();
			if(R2 == null)
				continue;
			for(final Enumeration<MOB> m = R2.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (M == host) || (M.playerStats() != null))
					continue;
				if((!share.equalsIgnoreCase("AREA")) && (!CMLib.masking().maskCheck(share, M, true)))
					continue;
				for(final Enumeration<Behavior> b = M.behaviors(); b.hasMoreElements();)
				{
					final Behavior B = b.nextElement();
					if(B instanceof Memory)
						((Memory)B).learn(M, whoName, fact, add, null, true);
				}
			}
		}
	}

	/** a fact's value now, faded facts gone */
	protected long valueOf(final MOB host, final String whoName, final String fact)
	{
		final Map<String, Fact> facts = known.get(whoName.toUpperCase());
		final Fact F = (facts == null) ? null : facts.get(fact.toUpperCase());
		if(F == null)
			return 0;
		if((forgetMs > 0) && ((GameTime.now() - F.at) > forgetMs))
		{
			facts.remove(fact.toUpperCase());
			mirror(host, whoName.toUpperCase(), fact.toUpperCase(), null);
			return 0;
		}
		return F.value;
	}

	/**
	 * FACT>N, FACT>=N, FACT=N, FACT<N, FACT<=N, FACT!=N, joined by & and |.
	 * @param host the NPC
	 * @param who the character's name
	 * @param cond the condition
	 * @return whether it holds
	 */
	protected boolean holds(final MOB host, final String who, final String cond)
	{
		if((cond == null) || (cond.trim().length() == 0))
			return false;
		for(final String any : cond.split("\\|"))
		{
			boolean all = true;
			for(final String one : any.split("&"))
			{
				final java.util.regex.Matcher m = Pattern.compile("^\\s*([A-Za-z_0-9]+)\\s*(>=|<=|!=|>|<|=)\\s*(-?\\d+)\\s*$").matcher(one);
				if(!m.find())
				{
					all = false;
					break;
				}
				final long v = valueOf(host, who, m.group(1).toUpperCase());
				final long n = CMath.s_long(m.group(3));
				final String op = m.group(2);
				final boolean ok = op.equals(">") ? (v > n) : op.equals(">=") ? (v >= n) : op.equals("<") ? (v < n) : op.equals("<=") ? (v <= n)
					: op.equals("!=") ? (v != n) : (v == n);
				if(!ok)
				{
					all = false;
					break;
				}
			}
			if(all)
				return true;
		}
		return false;
	}

	protected static boolean character(final MOB M)
	{
		return (M != null) && (M.playerStats() != null);
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(!(myHost instanceof MOB))
			return;
		final MOB host = (MOB)myHost;
		final MOB src = msg.source();
		final Room here = host.location();
		if((here == null) || (src == null))
			return;
		// a kinsman killed here
		if(((msg.othersMinor() == CMMsg.TYP_DEATH) || (msg.sourceMinor() == CMMsg.TYP_DEATH))
		&& (src != host) && (msg.tool() instanceof MOB) && character((MOB)msg.tool())
		&& (kin.length() > 0) && CMLib.masking().maskCheck(kin, src, true))
		{
			learn(host, (MOB)msg.tool(), "KILLEDKIN", 1, null, false);
			return;
		}
		if((src == host) || (!character(src)))
			return;
		if((msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == here))
		{
			meet(host, src);
			return;
		}
		if(msg.target() == host)
		{
			if(msg.targetMinor() == CMMsg.TYP_DAMAGE)
				learn(host, src, "HURT", Math.max(0, msg.value()), null, false);
			else
			if(msg.targetMinor() == CMMsg.TYP_HEALING)
				learn(host, src, "HEALED", 1, null, false);
			else
			if((msg.targetMinor() == CMMsg.TYP_GIVE) && (msg.tool() instanceof Item))
				learn(host, src, "GAVE", Math.max(0, ((Item)msg.tool()).value()), null, false);
			else
			if((msg.targetMinor() == CMMsg.TYP_JUSTICE) && CMath.bset(msg.targetMajor(), CMMsg.MASK_DELICATE))
				learn(host, src, "STOLE", 1, null, false);
			else
			if(CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS) && (msg.targetMinor() != CMMsg.TYP_DAMAGE))
			{
				final Map<String, Fact> facts = known.get(src.Name().toUpperCase());
				final Fact was = (facts == null) ? null : facts.get("ATTACKED");
				if((was == null) || ((GameTime.now() - was.at) > (5L * CMProps.getTickMillis())) || (!host.isInCombat()))
					learn(host, src, "ATTACKED", 1, null, false); // one fight, not each blow
			}
		}
		if((msg.sourceMinor() == CMMsg.TYP_SPEAK) && (msg.target() == host || msg.target() == null))
			learn(host, src, "TALKED", 1, null, false);
		if(!tracks.isEmpty())
		{
			final String seen = (msg.othersMessage() != null) ? msg.othersMessage() : ((msg.targetMessage() != null) ? msg.targetMessage() : "");
			final String txt = CMStrings.removeColors(CMLib.coffeeFilter().fullOutFilter(null, host, src, msg.target(), msg.tool(), seen, false));
			for(final Map.Entry<String, Pattern> t : tracks.entrySet())
			{
				if(t.getValue().matcher(txt).find())
					learn(host, src, t.getKey(), 1, null, false);
			}
		}
	}

	/** a character comes in, or is here when it looks */
	protected void meet(final MOB host, final MOB M)
	{
		final TimeClock C = CMLib.time().localClock(host);
		final String date = (C == null) ? "" : C.getShortTimeDescription();
		if(valueOf(host, M.Name(), "MET") == 0)
			learn(host, M, "FIRSTMET", 0, date, false);
		learn(host, M, "MET", 1, null, false);
		learn(host, M, "LASTMET", 0, date, false);
		if((greet.length() > 0) && holds(host, M.Name(), greetIf) && (!greeted.contains(M.Name())))
		{
			greeted.add(M.Name());
			CMLib.commands().postSay(host, M, CMStrings.replaceAll(greet, "$n", M.Name()), false, false);
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((myHost instanceof MOB)
		&& (refuseIf.length() > 0)
		&& (msg.target() == myHost)
		&& character(msg.source())
		&& (refused.contains(Integer.valueOf(msg.targetMinor())) || (msg.sourceMinor() == CMMsg.TYP_TEACH) || (msg.targetMinor() == CMMsg.TYP_TEACH))
		&& holds((MOB)myHost, msg.source().Name(), refuseIf))
		{
			CMLib.commands().postSay((MOB)myHost, msg.source(), (refuse.length() > 0) ? refuse : L("I will not deal with you."), false, false);
			return false;
		}
		return true;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)))
			return true;
		final MOB host = (MOB)ticking;
		final Room here = host.location();
		if((here == null) || host.amDead())
			return true;
		if(!mirrored) // after a restore: the facts back as script variables
		{
			mirrored = true;
			for(final Map.Entry<String, Map<String, Fact>> e : known.entrySet())
				for(final Map.Entry<String, Fact> f : e.getValue().entrySet())
					mirror(host, e.getKey(), f.getKey(), f.getValue());
		}
		// those it has a grudge against, on sight
		if((hostileIf.length() > 0) && (!host.isInCombat()) && CMLib.flags().isAliveAwakeMobile(host, true))
		{
			for(final Enumeration<MOB> m = here.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if(character(M) && (M != host) && CMLib.flags().canBeSeenBy(M, host) && holds(host, M.Name(), hostileIf))
				{
					CMLib.combat().postAttack(host, M, host.fetchWieldedItem());
					break;
				}
			}
		}
		// greeting again once they have left
		for(final Iterator<String> g = greeted.iterator(); g.hasNext();)
		{
			if(here.fetchInhabitant(g.next()) == null)
				g.remove();
		}
		return true;
	}

	@Override
	protected String getRunState()
	{
		final StringBuilder str = new StringBuilder("");
		for(final Map.Entry<String, Map<String, Fact>> e : known.entrySet())
		{
			for(final Map.Entry<String, Fact> f : e.getValue().entrySet())
			{
				final Fact F = f.getValue();
				str.append("<F WHO=\"").append(CMLib.xml().parseOutAngleBrackets(names.containsKey(e.getKey()) ? names.get(e.getKey()) : e.getKey()))
					.append("\" FACT=\"").append(f.getKey()).append("\" V=\"").append(F.value).append("\" AT=\"").append(F.at).append("\"")
					.append((F.text != null) ? (" T=\"" + CMLib.xml().parseOutAngleBrackets(F.text) + "\"") : "").append(" />");
			}
		}
		return str.toString();
	}

	@Override
	protected void setRunState(final String xml)
	{
		known.clear();
		names.clear();
		mirrored = false;
		if((xml == null) || (xml.length() == 0))
			return;
		for(final com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag tag : CMLib.xml().parseAllXML(xml))
		{
			if(!tag.tag().equalsIgnoreCase("F"))
				continue;
			final String who = CMLib.xml().restoreAngleBrackets(tag.getParmValue("WHO"));
			final String fact = tag.getParmValue("FACT");
			if((who == null) || (fact == null))
				continue;
			names.put(who.toUpperCase(), who);
			Map<String, Fact> facts = known.get(who.toUpperCase());
			if(facts == null)
			{
				facts = new TreeMap<String, Fact>();
				known.put(who.toUpperCase(), facts);
			}
			final Fact F = new Fact();
			F.value = CMath.s_long(tag.getParmValue("V"));
			F.at = CMath.s_long(tag.getParmValue("AT"));
			final String t = tag.getParmValue("T");
			if(t != null)
				F.text = CMLib.xml().restoreAngleBrackets(t);
			facts.put(fact, F);
		}
	}

	private final static String[]	CODES	= { "AGE" };

	@Override
	public String[] getStatCodes()
	{
		final String[] sup = super.getStatCodes();
		final String[] all = Arrays.copyOf(sup, sup.length + CODES.length);
		System.arraycopy(CODES, 0, all, sup.length, CODES.length);
		return all;
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if("AGE".equalsIgnoreCase(code)) // for tests and builders: every fact older by n game hours
		{
			final long by = CMath.s_long(val) * CMProps.getMillisPerMudHour();
			for(final Map<String, Fact> facts : known.values())
				for(final Fact F : facts.values())
					F.at -= by;
			return;
		}
		super.setStat(code, val);
	}

	@Override
	protected String debugState()
	{
		final StringBuilder str = new StringBuilder(super.debugState());
		for(final Map.Entry<String, Map<String, Fact>> e : known.entrySet())
		{
			str.append("; ").append(names.containsKey(e.getKey()) ? names.get(e.getKey()) : e.getKey()).append(":");
			for(final Map.Entry<String, Fact> f : e.getValue().entrySet())
				str.append(" ").append(f.getKey()).append("=").append((f.getValue().text != null) ? f.getValue().text : ("" + f.getValue().value));
		}
		return str.toString();
	}
}
