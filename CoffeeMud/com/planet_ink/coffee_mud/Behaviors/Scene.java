package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.exceptions.ScriptParseException;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.MPContext;
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
 * N28: a scene NPCs play out for a player character to overhear, on a room
 * or an NPC.  Its parameters are statements, between ; or one to a line in
 * FILE=path: ACTOR name=mask (a zapper mask, or words of the NPC's name;
 * SELF is the NPC it is on), then lines "name: say ...", "name: emote ...",
 * "name: any command", ECHO text, PAUSE n (ticks of silence) and DO
 * MOBPROG line ($n the one it plays for).  IF=condition (a script
 * condition, $n the character) and WHEN=, TOD=, HOURS= hold it back;
 * ONCE (the default) plays it to each character once, REPEAT=n again after
 * n game days; DELAY=n ticks between lines; NAME= names it (for the
 * SCENE_name variable on each character who heard it to the end).
 * INTERRUPTIBLE: a character who speaks to an actor stops it, and the
 * actors turn to them; it plays again when they come back.  A line a tick:
 * a turn in turn-based time.
 */
public class Scene extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Scene";
	}

	@Override
	public String name()
	{
		return "a scene for players to overhear";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS | Behavior.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "playing out a scene";
	}

	/** one statement of the scene */
	protected static final class Step
	{
		/** ACT, ECHO, PAUSE or DO */
		String	kind	= "ACT";
		/** the actor, for ACT */
		String	actor	= "";
		/** the words, the command or the MOBPROG line */
		String	text	= "";
		/** PAUSE's ticks */
		int		n		= 0;
	}

	protected final Map<String, String>	actors		= new LinkedHashMap<String, String>();
	protected final List<Step>			steps		= new ArrayList<Step>();
	protected String					sceneName	= "";
	protected String					cond		= null;
	protected String[]					condEval	= null;
	protected int						repeatDays	= -1;
	protected boolean					interrupt	= false;
	protected int						delay		= 1;
	protected final List<String>		problems	= new ArrayList<String>();

	// while it plays
	protected int						at			= -1;
	protected int						wait		= 0;
	protected String					audience	= "";
	protected final Map<String, MOB>	cast		= new HashMap<String, MOB>();
	/** those who stopped it: not played to again until they leave */
	protected final Set<String>			stoppedBy	= new HashSet<String>();

	private static ScriptingEngine		acts		= null;

	private static ScriptingEngine acts()
	{
		if(acts == null)
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setSavable(false);
			S.setVarScope("");
			acts = S;
		}
		return acts;
	}

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		actors.clear();
		steps.clear();
		problems.clear();
		sceneName = "";
		cond = null;
		condEval = null;
		repeatDays = -1;
		interrupt = false;
		delay = 1;
		at = -1;
		wait = 0;
		cast.clear();
		for(final String s : statements((newParms == null) ? "" : newParms))
			read(s);
		if(sceneName.length() == 0)
			sceneName = "S" + Integer.toHexString(((newParms == null) ? "" : newParms).hashCode()).toUpperCase();
	}

	/** the parameters as statements, FILE= read in */
	protected List<String> statements(final String parms)
	{
		final List<String> list = new ArrayList<String>();
		for(final String line : parms.split("\\r?\\n"))
		{
			for(final String s : line.split(";"))
			{
				final String t = s.trim();
				if((t.length() == 0) || t.startsWith("#"))
					continue;
				if(t.toUpperCase().startsWith("FILE="))
				{
					final StringBuffer text = Resources.getFileResource(t.substring(5).trim(), true);
					if(text == null)
					{
						problems.add("the file " + t.substring(5).trim() + " cannot be read");
						continue;
					}
					for(final String l : text.toString().split("\\r?\\n"))
					{
						if((l.trim().length() > 0) && (!l.trim().startsWith("#")))
							list.add(l.trim());
					}
					continue;
				}
				list.add(t);
			}
		}
		return list;
	}

	/** one statement */
	protected void read(final String s)
	{
		final String up = s.toUpperCase();
		if(up.startsWith("ACTOR "))
		{
			final String rest = s.substring(6).trim();
			final int x = rest.indexOf('=');
			final String name = ((x < 0) ? rest : rest.substring(0, x)).trim().toUpperCase();
			final String mask = (x < 0) ? name : unquote(rest.substring(x + 1).trim());
			if(name.length() > 0)
				actors.put(name, mask);
			return;
		}
		if(up.startsWith("NAME="))
		{
			sceneName = s.substring(5).trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
			return;
		}
		if(up.startsWith("IF="))
		{
			cond = unquote(s.substring(3).trim());
			return;
		}
		if(up.startsWith("REPEAT="))
		{
			repeatDays = Math.max(1, CMath.s_int(s.substring(7).trim()));
			return;
		}
		if(up.equals("ONCE"))
		{
			repeatDays = -1;
			return;
		}
		if(up.equals("INTERRUPTIBLE"))
		{
			interrupt = true;
			return;
		}
		if(up.startsWith("DELAY="))
		{
			delay = Math.max(1, CMath.s_int(s.substring(6).trim()));
			return;
		}
		if(up.startsWith("WHEN=") || up.startsWith("TOD=") || up.startsWith("HOURS="))
			return; // N1's gate, read from the parameters as a whole
		final Step S = new Step();
		if(up.startsWith("PAUSE"))
		{
			S.kind = "PAUSE";
			S.n = Math.max(1, CMath.s_int(s.substring(5).trim()));
			steps.add(S);
			return;
		}
		if(up.startsWith("ECHO "))
		{
			S.kind = "ECHO";
			S.text = s.substring(5).trim();
			steps.add(S);
			return;
		}
		if(up.startsWith("DO "))
		{
			S.kind = "DO";
			S.text = s.substring(3).trim();
			steps.add(S);
			return;
		}
		final int x = s.indexOf(':');
		if(x > 0)
		{
			final String who = s.substring(0, x).trim().toUpperCase();
			if(actors.containsKey(who) || who.equals("SELF"))
			{
				S.actor = who;
				S.text = s.substring(x + 1).trim();
				steps.add(S);
				return;
			}
			problems.add("'" + who + "' is not an ACTOR: " + s);
			return;
		}
		problems.add("not understood: " + s);
	}

	protected static String unquote(final String s)
	{
		if((s.length() > 1) && s.startsWith("\"") && s.endsWith("\"") && (s.indexOf('"', 1) == s.length() - 1))
			return s.substring(1, s.length() - 1);
		return s;
	}

	/**
	 * @return what is wrong with its parameters, for the checks
	 */
	public List<String> problems()
	{
		return problems;
	}

	/** where it plays */
	protected static Room roomOf(final Tickable host)
	{
		if(host instanceof Room)
			return (Room)host;
		if(host instanceof MOB)
			return ((MOB)host).location();
		return null;
	}

	/** the actors, found in the room; false if one is missing or busy */
	protected boolean findCast(final Tickable host, final Room R)
	{
		cast.clear();
		final Set<MOB> taken = new HashSet<MOB>();
		if(host instanceof MOB)
		{
			cast.put("SELF", (MOB)host);
			taken.add((MOB)host);
		}
		for(final Map.Entry<String, String> e : actors.entrySet())
		{
			MOB found = null;
			final String mask = e.getValue();
			final boolean zap = mask.startsWith("+") || mask.startsWith("-");
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || taken.contains(M) || (M.playerStats() != null) || (!M.isMonster()))
					continue;
				if(zap ? CMLib.masking().maskCheck(mask, M, true) : CMLib.english().containsString(M.Name(), mask))
				{
					found = M;
					break;
				}
			}
			if(found == null)
				return false;
			taken.add(found);
			cast.put(e.getKey(), found);
		}
		for(final MOB M : cast.values())
		{
			if(!ready(M, R))
				return false;
		}
		return true;
	}

	/** whether an actor can go on: here, alive, awake, not fighting */
	protected static boolean ready(final MOB M, final Room R)
	{
		return (M != null) && (!M.amDestroyed()) && (!M.amDead()) && (M.location() == R) && (!M.isInCombat()) && (!CMLib.flags().isSleeping(M));
	}

	/** the day number on the room's clock */
	protected static long dayOf(final Room R)
	{
		final TimeClock C = (R != null) ? CMLib.time().localClock(R) : CMLib.time().globalClock();
		if(C == null)
			return 0;
		return (((long)C.getYear() * C.getMonthsInYear()) + C.getMonth()) * C.getDaysInMonth() + C.getDayOfMonth();
	}

	/** the variable that says when a character heard it */
	public String seenVar()
	{
		return "SCENE_" + sceneName;
	}

	/** whether it may play to a character now */
	protected boolean due(final MOB P, final Room R)
	{
		if(stoppedBy.contains(P.Name()))
			return false;
		final String was = acts().getVar(P.Name(), seenVar());
		if((was == null) || (was.trim().length() == 0))
			return true;
		if(repeatDays < 0)
			return false;
		return dayOf(R) - CMath.s_long(was.trim()) >= repeatDays;
	}

	/** its condition, $n the character, $i the first actor */
	protected boolean condOk(final MOB P)
	{
		if((cond == null) || (cond.trim().length() == 0))
			return true;
		final MOB host = firstActor(P);
		try
		{
			if(condEval == null)
				condEval = acts().parseEval(cond);
			return acts().eval(new MPContext(host, host, P, P, null, null, "", null), new String[][] { condEval }, 0);
		}
		catch(final ScriptParseException e)
		{
			Log.errOut("Scene", "IF=" + cond + " does not parse: " + e.getMessage());
			cond = null;
			return false;
		}
	}

	protected MOB firstActor(final MOB fallback)
	{
		for(final MOB M : cast.values())
			return M;
		return fallback;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((ticking instanceof MOB) && (tickID != Tickable.TICKID_MOB))
			return true;
		final Room R = roomOf(ticking);
		if((R == null) || steps.isEmpty())
			return true;
		// those who stopped it may hear it again once they have gone
		for(final Iterator<String> i = stoppedBy.iterator(); i.hasNext();)
		{
			final MOB M = R.fetchInhabitantExact(i.next());
			if((M == null) || (M.playerStats() == null))
				i.remove();
		}
		if(at < 0)
		{
			tryStart(ticking, R);
			return true;
		}
		play(ticking, R);
		return true;
	}

	/** idle: begins it for the first character here it is due to */
	protected void tryStart(final Tickable host, final Room R)
	{
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB P = m.nextElement();
			if((P == null) || (P.playerStats() == null) || (P == host) || P.amDead())
				continue;
			if(!due(P, R))
				continue;
			if(!findCast(host, R))
				return;
			if(!condOk(P))
				continue;
			audience = P.Name();
			at = 0;
			wait = 0;
			play(host, R);
			return;
		}
	}

	/** stops it; done, those here have heard it */
	protected void stop(final Room R, final boolean done)
	{
		if(done && (R != null))
		{
			final long day = dayOf(R);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB P = m.nextElement();
				if((P != null) && (P.playerStats() != null))
					acts().setVar(P.Name(), seenVar(), "=" + day); // "=": as it is (SC11)
			}
		}
		at = -1;
		wait = 0;
		audience = "";
		cast.clear();
	}

	/** one tick of it playing */
	protected void play(final Tickable host, final Room R)
	{
		final MOB P = R.fetchInhabitantExact(audience);
		if((P == null) || (P.playerStats() == null))
		{
			stop(R, false); // nobody left to hear it
			return;
		}
		if(cast.isEmpty() && (!findCast(host, R)))
		{
			stop(R, false);
			return;
		}
		for(final MOB M : cast.values())
		{
			if(!ready(M, R))
			{
				stop(R, false); // an actor gone, or fighting
				return;
			}
		}
		if(wait > 0)
		{
			wait--;
			return;
		}
		if(at >= steps.size())
		{
			stop(R, true);
			return;
		}
		final Step S = steps.get(at++);
		wait = delay - 1;
		if(S.kind.equals("PAUSE"))
			wait = S.n - 1;
		else
		if(S.kind.equals("ECHO"))
			R.showHappens(CMMsg.MSG_OK_VISUAL, fill(S.text, P));
		else
		if(S.kind.equals("DO"))
		{
			final MOB actor = firstActor(P);
			synchronized(acts())
			{
				acts().setScript("FUNCTION_PROG SCENE_DO\n" + S.text + "\n~\n");
				acts().callFunction(actor, "SCENE_DO", "", P);
			}
		}
		else
		{
			final MOB actor = cast.get(S.actor);
			if(actor != null)
				actor.doCommand(command(fill(S.text, P)), MUDCmdProcessor.METAFLAG_FORCED);
		}
		if((at >= steps.size()) && (wait <= 0))
			stop(R, true);
	}

	protected static String fill(final String text, final MOB P)
	{
		return text.replace("$n", P.name());
	}

	/** a line as a command: SAY and EMOTE take the rest as it is */
	protected static List<String> command(final String text)
	{
		final String up = text.toUpperCase();
		for(final String verb : new String[] { "SAY", "EMOTE", "YELL" })
		{
			if(up.startsWith(verb + " "))
				return new XVector<String>(verb, text.substring(verb.length() + 1).trim());
		}
		return CMParms.parse(text);
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host, msg);
		if((at < 0) || (!interrupt) || (msg.source() == null) || (msg.source().playerStats() == null))
			return;
		if((msg.sourceMinor() != CMMsg.TYP_SPEAK) && (msg.targetMinor() != CMMsg.TYP_SPEAK))
			return;
		if(!(msg.target() instanceof MOB) || (!cast.containsValue(msg.target())))
			return;
		// spoken to: they stop and turn to the speaker, after the words
		final MOB P = msg.source();
		final Room R = P.location();
		for(final MOB M : cast.values())
		{
			if(M.location() == R)
				msg.addTrailerMsg(CMClass.getMsg(M, P, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> stop(s) and turn(s) to <T-NAMESELF>.")));
		}
		stoppedBy.add(P.Name());
		stop(R, false);
	}

	@Override
	protected String debugState()
	{
		final StringBuilder str = new StringBuilder((at < 0) ? "idle" : ("playing step " + at + " of " + steps.size() + " for " + audience));
		str.append("; scene ").append(sceneName).append(", ").append(actors.size()).append(" actors, ").append(steps.size()).append(" steps");
		if(!stoppedBy.isEmpty())
			str.append("; stopped by ").append(String.join(", ", stoppedBy));
		if(!problems.isEmpty())
			str.append("; problems: ").append(String.join("; ", problems));
		return str.toString();
	}

	@Override
	protected String getRunState()
	{
		if(at < 0)
			return "";
		return "<SCENE AT=\"" + at + "\" WAIT=\"" + wait + "\" FOR=\"" + CMLib.xml().parseOutAngleBrackets(audience) + "\" />";
	}

	@Override
	protected void setRunState(final String xml)
	{
		if((xml == null) || (xml.trim().length() == 0))
			return;
		final List<XMLLibrary.XMLTag> V = CMLib.xml().parseAllXML(xml);
		if((V == null) || V.isEmpty())
			return;
		final XMLLibrary.XMLTag tag = V.get(0);
		at = CMath.s_int(tag.getParmValue("AT"));
		wait = CMath.s_int(tag.getParmValue("WAIT"));
		audience = CMLib.xml().restoreAngleBrackets(tag.getParmValue("FOR"));
		cast.clear(); // found again on the next tick
	}
}
