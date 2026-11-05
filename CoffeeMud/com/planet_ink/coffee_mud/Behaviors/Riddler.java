package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import java.util.*;

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
 * I17: a riddler, on a mob or an item.  RIDDLE="..." with ANSWERS="mountain|
 * mountains" (and RIDDLE1..9 with ANSWERS1..9 for more; RANDOM=true picks
 * one, else the first); case, articles and punctuation do not count, and
 * FUZZY=1 forgives a letter.  ASK=GREET,LOOK,COMMAND: it asks one who comes
 * in, who looks at it, or who types RIDDLE; the answer is said (SAY) or
 * typed (ANSWER text); FUZZY counts two letters swapped as one.  TRIES=3; ONRIGHT=, ONWRONG= (each wrong answer) and
 * ONOUT= (the last) are actions, as Prop_Switch's, $n who answered;
 * RIGHTMSG= and WRONGMSG= what they are told.  ONCE=CHARACTER (each solves
 * it once: a tattoo), SAVE (once a game: kept in its parameters) or NONE.
 */
public class Riddler extends StdBehavior implements VerbTaker
{
	@Override
	public String ID()
	{
		return "Riddler";
	}

	@Override
	public String name()
	{
		return "a riddler";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS | Behavior.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "asking riddles";
	}

	protected final List<String>		questions	= new ArrayList<String>();
	protected final List<List<String>>	answers		= new ArrayList<List<String>>();
	protected boolean					random		= false;
	protected int						fuzzy		= 0;
	protected Set<String>				ask			= new HashSet<String>();
	protected int						tries		= 3;
	protected String					once		= "CHARACTER";
	protected boolean					solved		= false;
	protected String					base		= "";
	protected Map<String, String>		parms		= new HashMap<String, String>();
	protected final Map<MOB, int[]>		asked		= new WeakHashMap<MOB, int[]>();
	protected PhysicalAgent				host		= null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String t = (newParms == null) ? "" : newParms;
		parms = CMParms.parseExactParms(t);
		questions.clear();
		answers.clear();
		for(int i = 0; i <= 9; i++)
		{
			final String k = (i == 0) ? "" : ("" + i);
			final String q = CMParms.getParmStr(parms, "RIDDLE" + k, "").trim();
			if(q.length() == 0)
				continue;
			questions.add(q);
			final List<String> a = new ArrayList<String>();
			for(final String w : CMParms.getParmStr(parms, "ANSWERS" + k, CMParms.getParmStr(parms, "ANSWER" + k, "")).split("\\|"))
			{
				final String n = norm(w);
				if(n.length() > 0)
					a.add(n);
			}
			answers.add(a);
		}
		random = CMParms.getParmBool(parms, "RANDOM", false);
		fuzzy = Math.max(0, CMParms.getParmInt(parms, "FUZZY", 0));
		ask.clear();
		for(final String w : CMParms.parseCommas(CMParms.getParmStr(parms, "ASK", "GREET,LOOK,COMMAND").toUpperCase(), true))
			ask.add(w.trim());
		tries = Math.max(1, CMParms.getParmInt(parms, "TRIES", 3));
		once = CMParms.getParmStr(parms, "ONCE", "CHARACTER").toUpperCase();
		solved = CMParms.getParmBool(parms, "SOLVED", false);
		base = t.replaceAll("(?i)\\s*\\bSOLVED=\\S*", "").trim();
		ScriptEvents.propVerbsSeen();
	}

	@Override
	public String getParms()
	{
		return solved ? (base + " SOLVED=true") : base;
	}

	/**
	 * An answer as it is compared: lower case, no punctuation, no a, an or the.
	 * @param s the answer
	 * @return the same, plain
	 */
	public static String norm(final String s)
	{
		String t = (s == null) ? "" : s.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
		t = t.replaceAll("^(a|an|the) ", "");
		return t;
	}

	/** letters to change, add, take away or swap (two side by side) to make one into the other */
	protected static int distance(final String a, final String b)
	{
		final int[][] d = new int[a.length() + 1][b.length() + 1];
		for(int i = 0; i <= a.length(); i++)
			d[i][0] = i;
		for(int j = 0; j <= b.length(); j++)
			d[0][j] = j;
		for(int i = 1; i <= a.length(); i++)
		{
			for(int j = 1; j <= b.length(); j++)
			{
				final int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if((i > 1) && (j > 1) && (a.charAt(i - 1) == b.charAt(j - 2)) && (a.charAt(i - 2) == b.charAt(j - 1)))
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
			}
		}
		return d[a.length()][b.length()];
	}

	/**
	 * Whether an answer is right for a riddle.
	 * @param n the riddle
	 * @param said the answer
	 * @return true if it is
	 */
	public boolean right(final int n, final String said)
	{
		if((n < 0) || (n >= answers.size()))
			return false;
		final String s = norm(said);
		for(final String a : answers.get(n))
		{
			if(s.equals(a) || ((fuzzy > 0) && (distance(s, a) <= fuzzy)))
				return true;
		}
		return false;
	}

	protected String tattoo()
	{
		return "RIDDLED:" + ((host != null) ? host.Name().toUpperCase().replace(' ', '_') : "?");
	}

	/**
	 * @param M one
	 * @return whether they have done with it
	 */
	public boolean done(final MOB M)
	{
		if(once.equals("SAVE"))
			return solved;
		if(once.equals("CHARACTER"))
			return M.findTattoo(tattoo()) != null;
		return false;
	}

	protected Room room()
	{
		return CMLib.map().roomLocation(host);
	}

	protected void say(final MOB to, final String text)
	{
		final Room R = room();
		if(R == null)
			return;
		if(host instanceof MOB)
			R.show((MOB)host, to, CMMsg.MSG_SPEAK, "<S-NAME> say(s) to <T-NAMESELF> '" + text + "'");
		else
			to.tell(CMStrings.capitalizeFirstLetter(host.name()) + " asks: '" + text + "'");
	}

	/**
	 * Asks one a riddle.
	 * @param M one
	 * @return true if it asked
	 */
	public boolean askOne(final MOB M)
	{
		if((M == null) || (M == host) || questions.isEmpty() || done(M) || (!GameDifficulty.playerSide(M)))
			return false;
		int[] st = asked.get(M);
		if(st == null)
		{
			st = new int[] { random ? CMLib.dice().roll(1, questions.size(), -1) : 0, tries };
			asked.put(M, st);
		}
		say(M, questions.get(st[0]));
		return true;
	}

	/**
	 * One answers.
	 * @param M one
	 * @param said what they said
	 * @return true if it was an answer to it
	 */
	public boolean answer(final MOB M, final String said)
	{
		final int[] st = asked.get(M);
		if(st == null)
			return false;
		final PhysicalAgent at = (host != null) ? host : room();
		if(right(st[0], said))
		{
			asked.remove(M);
			if(once.equals("SAVE"))
				solved = true;
			else
			if(once.equals("CHARACTER"))
				M.addTattoo(tattoo());
			M.tell(CMParms.getParmStr(parms, "RIGHTMSG", CMLib.lang().L("That is right.")));
			Actions.run(at, M, CMParms.getParmStr(parms, "ONRIGHT", ""));
			return true;
		}
		st[1]--;
		M.tell(CMParms.getParmStr(parms, "WRONGMSG", CMLib.lang().L("That is not right.")));
		Actions.run(at, M, CMParms.getParmStr(parms, "ONWRONG", ""));
		if(st[1] <= 0)
		{
			asked.remove(M);
			Actions.run(at, M, CMParms.getParmStr(parms, "ONOUT", ""));
		}
		return true;
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		host = forMe;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(myHost instanceof PhysicalAgent)
			host = (PhysicalAgent)myHost;
		final MOB src = msg.source();
		if((host == null) || (src == null) || (src == host))
			return;
		final Room R = room();
		if(ask.contains("GREET") && (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == R) && (R != null))
			askOne(src);
		else
		if(ask.contains("LOOK") && (msg.target() == host) && ((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE)))
			askOne(src);
		else
		if((msg.sourceMinor() == CMMsg.TYP_SPEAK) && (msg.sourceMessage() != null) && asked.containsKey(src) && (src.location() == R)
		&& ((msg.target() == null) || (msg.target() == host)))
		{
			final String said = host instanceof MOB?Languages.speechText((MOB)host,msg):CMStrings.getSayFromMessage(msg.sourceMessage());
			if((said != null) && (said.trim().length() > 0))
				answer(src, said);
		}
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent at, final String verb, final String rest)
	{
		if(at != host)
			return false;
		final String v = verb.toLowerCase();
		if(v.equals("answer") && asked.containsKey(mob) && (rest.trim().length() > 0))
			return answer(mob, rest);
		if(v.equals("riddle") && ask.contains("COMMAND") && ((rest.trim().length() == 0) || ScriptEvents.names(at, rest.trim())))
		{
			if(done(mob))
			{
				mob.tell(CMLib.lang().L("@x1 has no more riddles for you.", CMStrings.capitalizeFirstLetter(at.name())));
				return true;
			}
			return askOne(mob);
		}
		return false;
	}
}
