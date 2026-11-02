package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
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
 * W7: named versions of a room, declared in one place.  Its parameters are
 * clauses with a semicolon between (not inside quotes): a state,
 * STATE=name with TITLE=, DESC=, ATMOSPHERE= (a resource, as WATER or AIR),
 * CLIMATE= (WET, COLD, WINDY, HOT, DRY, with commas), ADD="Prop_RoomDark();
 * WaterCurrents(east)" (effects and behaviours it has while in the state),
 * BLOCK=north,down (ways out it closes), OPEN=up (doors it opens) and
 * ENTERMSG=; a transition, TO=name with FROM=names, WHEN="mask" (checked on
 * the room each tick, AFTER= ticks in a row), TICKS=n (after that many in
 * the state) and ONVAR="[host] name==value" (a script variable); START=name
 * the first state.  On an area it changes all its rooms together (ROOMS= a
 * list of room IDs for some).  MPROOMSTATE and ROOMSTATE() set and read it;
 * a save keeps it.
 */
public class RoomStates extends StdBehavior
{
	@Override
	public String ID()
	{
		return "RoomStates";
	}

	@Override
	public String name()
	{
		return "a room's states";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ROOMS | Behavior.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "changing as things happen";
	}

	/** a named version of the room */
	protected static final class State
	{
		String				name;
		String				title		= null;
		String				desc		= null;
		int					atmosphere	= -2;
		int					climate		= -2;
		List<String[]>		adds		= new ArrayList<String[]>();
		Set<Integer>		block		= new HashSet<Integer>();
		Set<Integer>		open		= new HashSet<Integer>();
		String				enterMsg	= "";
	}

	/** a way from one state to another */
	protected static final class Trans
	{
		String		to;
		Set<String>	from	= null;
		String		when	= null;
		int			after	= 0;
		int			ticks	= 0;
		String		onvar	= null;
		int			count	= 0;
	}

	protected Map<String, State>		states		= new LinkedHashMap<String, State>();
	protected List<Trans>				trans		= new ArrayList<Trans>();
	protected String					start		= null;
	protected List<String>				roomIDs		= null;
	protected String					current		= null;
	protected int						ticksIn		= 0;
	protected boolean					applied		= false;
	/** what each room was before any state, and what a state added to it */
	protected final Map<Room, String[]>		originals	= new WeakHashMap<Room, String[]>();
	protected final Map<Room, List<CMObject>>	added		= new WeakHashMap<Room, List<CMObject>>();

	/**
	 * The parameters' clauses: a semicolon between, not inside quotes.
	 * @param text the text
	 * @return the clauses
	 */
	public static List<String> clauses(final String text)
	{
		final List<String> list = new ArrayList<String>();
		final StringBuilder b = new StringBuilder();
		boolean q = false;
		for(final char c : text.toCharArray())
		{
			if(c == '"')
				q = !q;
			if((c == ';') && (!q))
			{
				if(b.toString().trim().length() > 0)
					list.add(b.toString().trim());
				b.setLength(0);
			}
			else
				b.append(c);
		}
		if(b.toString().trim().length() > 0)
			list.add(b.toString().trim());
		return list;
	}

	private static Set<Integer> dirs(final String list)
	{
		final Set<Integer> out = new HashSet<Integer>();
		if(list == null)
			return out;
		for(final String d : CMParms.parseCommas(list, true))
		{
			final int code = CMLib.directions().getGoodDirectionCode(d);
			if(code >= 0)
				out.add(Integer.valueOf(code));
		}
		return out;
	}

	@Override
	public void setParms(final String parameters)
	{
		super.setParms(parameters);
		states = new LinkedHashMap<String, State>();
		trans = new ArrayList<Trans>();
		start = null;
		roomIDs = null;
		for(final String clause : clauses(parameters))
		{
			final Map<String, String> P = CMParms.parseEQParms(clause);
			if(P.containsKey("STATE"))
			{
				final State S = new State();
				S.name = P.get("STATE").trim().toLowerCase();
				S.title = P.get("TITLE");
				S.desc = P.get("DESC");
				if(P.containsKey("ATMOSPHERE"))
				{
					final String a = P.get("ATMOSPHERE").trim().toUpperCase();
					S.atmosphere = (a.equals("AIR") || a.equals("NORMAL") || a.equals("DEFAULT")) ? -1 : RawMaterial.CODES.FIND_IgnoreCase(a);
					if(a.equals("WATER") && (S.atmosphere < 0))
						S.atmosphere = RawMaterial.RESOURCE_FRESHWATER;
				}
				if(P.containsKey("CLIMATE"))
				{
					int mask = 0;
					for(final String c : CMParms.parseCommas(P.get("CLIMATE").toUpperCase(), true))
					{
						for(int i = 1; i < Places.CLIMATE_DESCS.length; i++)
						{
							if(Places.CLIMATE_DESCS[i].equalsIgnoreCase(c))
								mask |= (int)CMath.pow(2, i - 1);
						}
					}
					S.climate = mask;
				}
				if(P.containsKey("ADD"))
				{
					for(final String a : P.get("ADD").split(";"))
					{
						final String t = a.trim();
						if(t.length() == 0)
							continue;
						final int x = t.indexOf('(');
						if((x > 0) && t.endsWith(")"))
							S.adds.add(new String[] { t.substring(0, x).trim(), t.substring(x + 1, t.length() - 1).trim() });
						else
							S.adds.add(new String[] { t, "" });
					}
				}
				S.block = dirs(P.get("BLOCK"));
				S.open = dirs(P.get("OPEN"));
				S.enterMsg = P.containsKey("ENTERMSG") ? P.get("ENTERMSG") : "";
				states.put(S.name, S);
			}
			else
			if(P.containsKey("TO"))
			{
				final Trans T = new Trans();
				T.to = P.get("TO").trim().toLowerCase();
				if(P.containsKey("FROM"))
				{
					T.from = new HashSet<String>();
					for(final String f : CMParms.parseCommas(P.get("FROM").toLowerCase(), true))
						T.from.add(f);
				}
				T.when = (P.containsKey("WHEN") && (P.get("WHEN").trim().length() > 0)) ? P.get("WHEN") : null;
				T.after = P.containsKey("AFTER") ? CMath.s_int(P.get("AFTER")) : 0;
				T.ticks = P.containsKey("TICKS") ? CMath.s_int(P.get("TICKS")) : 0;
				T.onvar = (P.containsKey("ONVAR") && (P.get("ONVAR").trim().length() > 0)) ? P.get("ONVAR") : null;
				trans.add(T);
			}
			if(P.containsKey("START"))
				start = P.get("START").trim().toLowerCase();
			if(P.containsKey("ROOMS"))
				roomIDs = CMParms.parseCommas(P.get("ROOMS"), true);
		}
		if((start == null) && (states.size() > 0))
			start = states.keySet().iterator().next();
		if((current == null) || (!states.containsKey(current)))
		{
			current = start;
			ticksIn = 0;
			applied = false;
		}
	}

	/**
	 * The rooms it changes: the room it is on, or its area's (ROOMS= some).
	 * @return the rooms
	 */
	protected List<Room> rooms()
	{
		final List<Room> out = new ArrayList<Room>();
		final Environmental host = hostEnv();
		if(host instanceof Room)
			out.add((Room)host);
		else
		if(host instanceof Area)
		{
			if(roomIDs != null)
			{
				for(final String id : roomIDs)
				{
					final Room R = CMLib.map().getRoom(id);
					if(R != null)
						out.add(R);
				}
			}
			else
			{
				for(final Enumeration<Room> r = ((Area)host).getProperMap(); r.hasMoreElements();)
				{
					final Room R = r.nextElement();
					if(R != null)
						out.add(R);
				}
			}
		}
		return out;
	}

	protected PhysicalAgent hostP = null;

	protected Environmental hostEnv()
	{
		return hostP;
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		hostP = forMe;
	}

	/** @return the state it is in, or "" */
	public String currentState()
	{
		return (current == null) ? "" : current;
	}

	/**
	 * Changes the rooms to a state now.
	 * @param name the state
	 * @return false if there is no such state
	 */
	public boolean changeState(final String name)
	{
		final String n = (name == null) ? "" : name.trim().toLowerCase();
		if(!states.containsKey(n))
			return false;
		if(applied)
			unapply();
		current = n;
		ticksIn = 0;
		for(final Trans T : trans)
			T.count = 0;
		apply();
		return true;
	}

	protected void unapply()
	{
		for(final Room R : rooms())
		{
			final List<CMObject> list = added.remove(R);
			if(list == null)
				continue;
			for(final CMObject O : list)
			{
				if(O instanceof Behavior)
					R.delBehavior((Behavior)O);
				else
				if(O instanceof Ability)
				{
					((Ability)O).unInvoke();
					R.delEffect((Ability)O);
				}
			}
			R.recoverPhyStats();
		}
		applied = false;
	}

	protected void apply()
	{
		final State S = (current == null) ? null : states.get(current);
		if(S == null)
			return;
		for(final Room R : rooms())
		{
			String[] o = originals.get(R);
			if(o == null)
			{
				o = new String[] { R.displayText(), R.description(), "" + R.getAtmosphereCode(), "" + R.getClimateTypeCode() };
				originals.put(R, o);
			}
			R.setDisplayText((S.title != null) ? S.title : o[0]);
			R.setDescription((S.desc != null) ? S.desc : o[1]);
			R.setAtmosphere((S.atmosphere != -2) ? S.atmosphere : CMath.s_int(o[2]));
			R.setClimateType((S.climate != -2) ? S.climate : CMath.s_int(o[3]));
			final List<CMObject> list = new ArrayList<CMObject>();
			for(final String[] a : S.adds)
			{
				final Behavior B = CMClass.getBehavior(a[0]);
				if(B != null)
				{
					B.setParms(a[1]);
					R.addBehavior(B);
					list.add(B);
					continue;
				}
				final Ability A = CMClass.getAbility(a[0]);
				if(A != null)
				{
					A.setMiscText(a[1]);
					R.addNonUninvokableEffect(A);
					list.add(A);
				}
				else
					Log.errOut("RoomStates", "No behaviour or ability " + a[0] + " to add in state " + S.name + ".");
			}
			added.put(R, list);
			for(final Integer d : S.open)
				Doors.change(R, CMLib.directions().getDirectionName(d.intValue()), "OPEN", "");
			R.recoverPhyStats();
		}
		applied = true;
	}

	/**
	 * Whether a mask holds for the place: checked on someone standing there.
	 * @param mask the mask
	 * @return true if it passes
	 */
	protected boolean holds(final String mask)
	{
		final List<Room> rooms = rooms();
		if(rooms.isEmpty())
			return false;
		final MOB F = CMLib.map().getFactoryMOB(rooms.get(0));
		try
		{
			return CMLib.masking().maskCheck(mask, F, true);
		}
		finally
		{
			F.destroy();
		}
	}

	/**
	 * ONVAR="[host] name op value": a script variable compared.
	 * @param onvar the words
	 * @return true if it holds
	 */
	protected boolean varHolds(final String onvar)
	{
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(?:(\\S+)\\s+)?(\\S+?)\\s*(==|!=|>=|<=|>|<|=)\\s*(.*)$").matcher(onvar);
		if(!m.find())
			return false;
		final String host = (m.group(1) == null) ? "*" : m.group(1);
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		final String have = (S == null) ? "" : S.getVar(host, m.group(2)).trim();
		final String want = m.group(4).trim();
		final String op = m.group(3);
		final boolean nums = CMath.isNumber(have) && CMath.isNumber(want);
		final int c = nums ? Double.compare(CMath.s_double(have), CMath.s_double(want)) : have.compareToIgnoreCase(want);
		if(op.equals("==") || op.equals("="))
			return c == 0;
		if(op.equals("!="))
			return c != 0;
		if(op.equals(">"))
			return c > 0;
		if(op.equals("<"))
			return c < 0;
		if(op.equals(">="))
			return c >= 0;
		return c <= 0;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(current == null)
			return true;
		if(!applied)
			apply();
		ticksIn++;
		for(final Trans T : trans)
		{
			if(T.to.equals(current) || ((T.from != null) && (!T.from.contains(current))) || (!states.containsKey(T.to)))
				continue;
			boolean ok = true;
			if((T.ticks > 0) && (ticksIn < T.ticks))
				ok = false;
			if(T.when != null)
			{
				if(holds(T.when))
					T.count++;
				else
					T.count = 0;
				if(T.count < Math.max(1, T.after))
					ok = false;
			}
			else
			if((T.after > 0) && (ticksIn < T.after))
				ok = false;
			if(ok && (T.onvar != null) && (!varHolds(T.onvar)))
				ok = false;
			if(ok)
			{
				changeState(T.to);
				break;
			}
		}
		return true;
	}

	protected boolean mine(final Room R)
	{
		if(R == null)
			return false;
		for(final Room M : rooms())
		{
			if(M == R)
				return true;
		}
		return false;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		final State S = (current == null) ? null : states.get(current);
		if((S != null)
		&& (S.block.size() > 0)
		&& ((msg.targetMinor() == CMMsg.TYP_LEAVE) || (msg.targetMinor() == CMMsg.TYP_FLEE))
		&& (msg.target() instanceof Room)
		&& mine((Room)msg.target()))
		{
			// the way out: the room whose way back is the message's exit
			final Room R = (Room)msg.target();
			for(final Integer d : S.block)
			{
				final Room to = R.getRoomInDir(d.intValue());
				if((to != null) && (msg.tool() != null) && (to.getRawExit(Directions.getOpDirectionCode(d.intValue())) == msg.tool()))
				{
					msg.source().tell(L("The way @x1 is blocked.", CMLib.directions().getDirectionName(d.intValue()).toLowerCase()));
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		final State S = (current == null) ? null : states.get(current);
		if((S != null)
		&& (S.enterMsg.length() > 0)
		&& (msg.targetMinor() == CMMsg.TYP_ENTER)
		&& (msg.target() instanceof Room)
		&& mine((Room)msg.target())
		&& (!msg.source().isMonster() || msg.source().isPlayer()))
		{
			final MOB M = msg.source();
			msg.addTrailerRunnable(new Runnable()
			{
				@Override
				public void run()
				{
					M.tell(S.enterMsg);
				}
			});
		}
	}

	@Override
	protected String getRunState()
	{
		return (current == null) ? "" : ("<RSTATE NAME=\"" + current + "\" TICKS=\"" + ticksIn + "\" />");
	}

	@Override
	protected void setRunState(final String xml)
	{
		final List<com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag> tags = CMLib.xml().parseAllXML(xml);
		for(final com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag tag : tags)
		{
			if(tag.tag().equalsIgnoreCase("RSTATE") && states.containsKey(tag.getParmValue("NAME")))
			{
				if(applied)
					unapply();
				current = tag.getParmValue("NAME");
				ticksIn = CMath.s_int(tag.getParmValue("TICKS"));
				apply();
			}
		}
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("ROOMSTATE")) // W7: for ROOMSTATE() and MPROOMSTATE
			return currentState();
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(code.equalsIgnoreCase("ROOMSTATE"))
		{
			changeState(val);
			return;
		}
		super.setStat(code, val);
	}
}
