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
 * N3: a daily routine over several places.  ';'-separated entries
 * <code>WHEN: PLACE [ACTIVITY...] [SAY text|EMOTE text]</code>; the first
 * whose WHEN holds is the one in force.
 * <ul>
 * <li>WHEN: DAWN, DAY, DUSK or NIGHT, or hours (2-5, or 7), with DAYS=a-b
 * (days of the month), SEASON=, WEATHER= or MASK="zapper mask" (on the
 * mob) after it, all of which must hold.</li>
 * <li>PLACE: a room ID, HOME or START (where the mob starts), or
 * ANY:"room mask" (a room of its area that fits).</li>
 * <li>ACTIVITY: SLEEP or SIT, each with an optional item to do it on; WANDER
 * n (rooms round the place); SHOP (open here, shut elsewhere, with
 * SHOPMSG="..."); WORK "emote|emote"; LOCK (the place's doors, while
 * there); HIDE.</li>
 * </ul>
 * It walks there (Mobile is held off on the way), and goes back to it
 * after a fight; when no player is in either area, it is put there at
 * once, so a restore, a time jump or a sleeping area finds it where its
 * routine says.  STAT($i SCHEDULE) is the entry in force.
 */
public class Schedule extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Schedule";
	}

	@Override
	public String name()
	{
		return "a daily routine";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public long flags()
	{
		return Behavior.FLAG_MOBILITY;
	}

	@Override
	public String accountForYourself()
	{
		return "keeping to a daily routine";
	}

	/** one entry of the routine */
	protected static final class Entry
	{
		String							raw			= "";
		Set<TimeClock.TimeOfDay>		tod			= null;
		int								from		= -1;
		int								to			= -1;
		int								dayFrom		= -1;
		int								dayTo		= -1;
		TimeClock.Season				season		= null;
		int								weather		= -1;
		String							mask		= null;
		String							place		= "";
		boolean							sleep		= false;
		boolean							sit			= false;
		String							furniture	= "";
		int								wander		= 0;
		boolean							shop		= false;
		String[]						work		= null;
		boolean							lock		= false;
		boolean							hide		= false;
		String							say			= null;
		String							emote		= null;
	}

	protected final List<Entry>	entries			= new ArrayList<Entry>();
	protected String			shopMsg			= null;
	protected boolean			anyShop			= false;

	protected Entry				inForce			= null;
	protected Room				dest			= null;
	protected boolean			arrived			= false;
	protected Room				lockedRoom		= null;
	protected int				idleTicks		= 0;

	private static final String[]	ACTS	= { "SLEEP", "SIT", "WANDER", "SHOP", "WORK", "LOCK", "HIDE", "SAY", "EMOTE" };

	protected final static Set<Integer>	shopCmds	= new XHashSet<Integer>(new Integer[] {
		Integer.valueOf(CMMsg.TYP_BUY), Integer.valueOf(CMMsg.TYP_BID), Integer.valueOf(CMMsg.TYP_SELL),
		Integer.valueOf(CMMsg.TYP_LIST), Integer.valueOf(CMMsg.TYP_VALUE), Integer.valueOf(CMMsg.TYP_DEPOSIT),
		Integer.valueOf(CMMsg.TYP_WITHDRAW), Integer.valueOf(CMMsg.TYP_BORROW), Integer.valueOf(CMMsg.TYP_VIEW) });

	/** splits on c, outside double quotes */
	protected static List<String> split(final String s, final char c)
	{
		final List<String> L = new ArrayList<String>();
		boolean q = false;
		int start = 0;
		for(int i = 0; i < s.length(); i++)
		{
			final char ch = s.charAt(i);
			if(ch == '"')
				q = !q;
			else
			if((ch == c) && (!q))
			{
				L.add(s.substring(start, i));
				start = i + 1;
			}
		}
		L.add(s.substring(start));
		return L;
	}

	/** words, with "quoted words" as one, their quotes taken off */
	protected static List<String> words(final String s)
	{
		final List<String> L = new ArrayList<String>();
		final StringBuilder w = new StringBuilder("");
		boolean q = false;
		boolean any = false;
		for(int i = 0; i < s.length(); i++)
		{
			final char ch = s.charAt(i);
			if(ch == '"')
			{
				q = !q;
				any = true;
			}
			else
			if(Character.isWhitespace(ch) && (!q))
			{
				if(any || (w.length() > 0))
					L.add(w.toString());
				w.setLength(0);
				any = false;
			}
			else
				w.append(ch);
		}
		if(any || (w.length() > 0))
			L.add(w.toString());
		return L;
	}

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		entries.clear();
		shopMsg = null;
		anyShop = false;
		inForce = null;
		dest = null;
		arrived = false;
		for(final String part : split((newParms == null) ? "" : newParms, ';'))
		{
			final String p = part.trim();
			if(p.length() == 0)
				continue;
			final int colon = p.indexOf(':');
			if((colon < 0) || (p.indexOf('=') >= 0 && p.indexOf('=') < colon && p.toUpperCase().startsWith("SHOPMSG")))
			{
				final Map<String, String> P = CMParms.parseEQParms(p);
				if(P.containsKey("SHOPMSG"))
					shopMsg = P.get("SHOPMSG");
				continue;
			}
			final Entry E = new Entry();
			E.raw = p;
			final List<String> when = words(p.substring(0, colon));
			if(when.isEmpty())
				continue;
			final String t = when.get(0).toUpperCase();
			final TimeClock.TimeOfDay tod = (TimeClock.TimeOfDay)CMath.s_valueOf(TimeClock.TimeOfDay.class, t);
			if(tod != null)
				E.tod = EnumSet.of(tod);
			else
			if(t.indexOf('-') > 0)
			{
				E.from = CMath.s_int(t.substring(0, t.indexOf('-')));
				E.to = CMath.s_int(t.substring(t.indexOf('-') + 1));
			}
			else
			if(CMath.isInteger(t))
				E.from = E.to = CMath.s_int(t);
			else
			{
				Log.errOut("Schedule", "'" + p + "': " + t + " is not DAWN, DAY, DUSK, NIGHT or hours");
				continue;
			}
			for(int i = 1; i < when.size(); i++)
			{
				final String q = when.get(i);
				final int eq = q.indexOf('=');
				if(eq < 0)
					continue;
				final String k = q.substring(0, eq).toUpperCase();
				final String v = q.substring(eq + 1).trim();
				if(k.equals("DAYS"))
				{
					final int x = v.indexOf('-');
					E.dayFrom = CMath.s_int((x > 0) ? v.substring(0, x) : v);
					E.dayTo = CMath.s_int((x > 0) ? v.substring(x + 1) : v);
				}
				else
				if(k.equals("SEASON"))
					E.season = (TimeClock.Season)CMath.s_valueOf(TimeClock.Season.class, v.toUpperCase());
				else
				if(k.equals("WEATHER"))
					E.weather = CMParms.indexOf(Climate.WEATHER_DESCS, v.toUpperCase());
				else
				if(k.equals("MASK"))
					E.mask = v;
			}
			final List<String> rest = words(p.substring(colon + 1));
			if(rest.isEmpty())
				continue;
			// the place runs to the first activity word, since room IDs may have spaces (Brenn Village#4)
			int first = 1;
			while((first < rest.size()) && (CMParms.indexOf(ACTS, rest.get(first).toUpperCase()) < 0))
				first++;
			E.place = CMParms.combine(rest, 0, first).trim();
			for(int i = first; i < rest.size(); i++)
			{
				final String w = rest.get(i).toUpperCase();
				final boolean nextIsArg = (i + 1 < rest.size()) && (CMParms.indexOf(ACTS, rest.get(i + 1).toUpperCase()) < 0);
				if(w.equals("SLEEP") || w.equals("SIT"))
				{
					if(w.equals("SLEEP"))
						E.sleep = true;
					else
						E.sit = true;
					if(nextIsArg)
						E.furniture = rest.get(++i);
				}
				else
				if(w.equals("WANDER"))
					E.wander = nextIsArg ? CMath.s_int(rest.get(++i)) : 1;
				else
				if(w.equals("SHOP"))
				{
					E.shop = true;
					anyShop = true;
				}
				else
				if(w.equals("WORK"))
				{
					if(nextIsArg)
						E.work = rest.get(++i).split("\\|");
				}
				else
				if(w.equals("LOCK"))
					E.lock = true;
				else
				if(w.equals("HIDE"))
					E.hide = true;
				else
				if(w.equals("SAY") || w.equals("EMOTE"))
				{
					final String text = CMParms.combine(rest, i + 1);
					if(w.equals("SAY"))
						E.say = text;
					else
						E.emote = text;
					break;
				}
			}
			entries.add(E);
		}
	}

	/**
	 * @param mob the mob
	 * @return the entry in force now, or null
	 */
	protected Entry current(final MOB mob)
	{
		final TimeClock C = CMLib.time().localClock(mob);
		if(C == null)
			return null;
		for(final Entry E : entries)
		{
			if((E.tod != null) && (!E.tod.contains(C.getTODCode())))
				continue;
			if(E.from >= 0)
			{
				final int h = C.getHourOfDay();
				final boolean in = (E.from <= E.to) ? ((h >= E.from) && (h <= E.to)) : ((h >= E.from) || (h <= E.to));
				if(!in)
					continue;
			}
			if(E.dayFrom >= 0)
			{
				final int d = C.getDayOfMonth();
				if((d < E.dayFrom) || (d > E.dayTo))
					continue;
			}
			if((E.season != null) && (C.getSeasonCode() != E.season))
				continue;
			if(E.weather >= 0)
			{
				final Room R = mob.location();
				final Area A = (R == null) ? null : R.getArea();
				if((A == null) || (A.getClimateObj().weatherType(R) != E.weather))
					continue;
			}
			if((E.mask != null) && (!CMLib.masking().maskCheck(E.mask, mob, true)))
				continue;
			return E;
		}
		return null;
	}

	/**
	 * @param E an entry
	 * @param mob the mob
	 * @return the room it names, or null
	 */
	protected Room placeOf(final Entry E, final MOB mob)
	{
		final String P = E.place;
		if(P.equalsIgnoreCase("HOME") || P.equalsIgnoreCase("START"))
			return mob.getStartRoom();
		if(P.toUpperCase().startsWith("ANY:"))
		{
			final String mask = P.substring(4).trim();
			final Room here = mob.location();
			final Area A = (here != null) ? here.getArea() : ((mob.getStartRoom() != null) ? mob.getStartRoom().getArea() : null);
			if(A == null)
				return null;
			final List<Room> fit = new ArrayList<Room>();
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if((R != null) && CMLib.masking().maskCheck(mask, R, true))
					fit.add(R);
			}
			if(fit.contains(here))
				return here;
			return fit.isEmpty() ? null : fit.get(CMLib.dice().roll(1, fit.size(), -1));
		}
		final Room R = CMLib.map().getRoom(P);
		if(R == null)
			Log.errOut("Schedule", mob.Name() + ": no room " + P);
		return R;
	}

	/** steps from a to b, or MAX_VALUE if more than max or no way */
	protected int distance(final Room a, final Room b, final int max)
	{
		if(a == b)
			return 0;
		final List<Room> t = CMLib.tracking().findTrailToRoom(a, b, CMLib.tracking().newFlags(), max);
		return ((t == null) || t.isEmpty()) ? Integer.MAX_VALUE : Math.max(1, t.size() - 1);
	}

	/** whether a player is in the area of either room */
	protected boolean playerNear(final Room a, final Room b)
	{
		final Area A1 = (a == null) ? null : a.getArea();
		final Area A2 = (b == null) ? null : b.getArea();
		for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			final Room R = (M == null) ? null : M.location();
			if((R != null) && ((R.getArea() == A1) || (R.getArea() == A2)))
				return true;
		}
		return false;
	}

	protected void doors(final MOB mob, final Room R, final boolean lock)
	{
		if(R == null)
			return;
		for(int d = Directions.NUM_DIRECTIONS() - 1; d >= 0; d--)
		{
			final Exit E = R.getExitInDir(d);
			final Room R2 = R.getRoomInDir(d);
			if((E == null) || (R2 == null) || (!E.hasADoor()) || (!E.hasALock()))
				continue;
			if(lock)
			{
				if(E.isOpen())
				{
					final CMMsg msg = CMClass.getMsg(mob, E, null, CMMsg.MSG_OK_VISUAL, CMMsg.MSG_CLOSE, CMMsg.MSG_OK_VISUAL,
						L("<S-NAME> @x1 <T-NAMESELF>.", ((E.closeWord().indexOf('(') > 0) ? E.closeWord() : (E.closeWord() + "(s)"))));
					CMLib.utensils().roomAffectFully(msg, R, d);
				}
				if(!E.isLocked())
				{
					final CMMsg msg = CMClass.getMsg(mob, E, null, CMMsg.MSG_OK_VISUAL, CMMsg.MSG_LOCK, CMMsg.MSG_OK_VISUAL,
						L("<S-NAME> lock(s) <T-NAMESELF><O-WITHNAME>."));
					CMLib.utensils().roomAffectFully(msg, R, d);
				}
			}
			else
			{
				if(E.isLocked() && (!E.defaultsLocked()))
				{
					final CMMsg msg = CMClass.getMsg(mob, E, null, CMMsg.MSG_OK_VISUAL, CMMsg.MSG_UNLOCK, CMMsg.MSG_OK_VISUAL,
						L("<S-NAME> unlock(s) <T-NAMESELF><O-WITHNAME>."));
					CMLib.utensils().roomAffectFully(msg, R, d);
				}
				if((!E.isOpen()) && (!E.defaultsClosed()))
				{
					final CMMsg msg = CMClass.getMsg(mob, E, null, CMMsg.MSG_OK_VISUAL, CMMsg.MSG_OPEN, CMMsg.MSG_OK_VISUAL,
						L("<S-NAME> @x1 <T-NAMESELF>.", ((E.openWord().indexOf('(') > 0) ? E.openWord() : (E.openWord() + "(s)"))));
					CMLib.utensils().roomAffectFully(msg, R, d);
				}
			}
		}
	}

	/** to sleep or sit, on the named furniture if it is here */
	protected void rest(final MOB mob, final Entry E)
	{
		final String verb = E.sleep ? "SLEEP" : "SIT";
		if(E.sleep ? CMLib.flags().isSleeping(mob) : CMLib.flags().isSitting(mob))
			return;
		final Room R = mob.location();
		if((E.furniture.length() > 0) && (R != null) && (R.findItem(null, E.furniture) != null))
			mob.doCommand(CMParms.parse(verb + " \"" + E.furniture + "\""), MUDCmdProcessor.METAFLAG_FORCED);
		if(E.sleep ? (!CMLib.flags().isSleeping(mob)) : (!CMLib.flags().isSitting(mob)))
			mob.doCommand(CMParms.parse(verb), MUDCmdProcessor.METAFLAG_FORCED);
	}

	protected void hidden(final MOB mob, final boolean hide)
	{
		final int disp = mob.basePhyStats().disposition();
		final int now = hide ? (disp | PhyStats.IS_HIDDEN) : (disp & ~PhyStats.IS_HIDDEN);
		if(now != disp)
		{
			mob.basePhyStats().setDisposition(now);
			mob.recoverPhyStats();
		}
	}

	/** leaving an entry: up, the doors as they were, seen */
	protected void leave(final MOB mob)
	{
		final Entry was = inForce;
		if((was != null) && (was.sleep || was.sit))
			CMLib.commands().postStand(mob, true, false);
		if((was != null) && was.hide)
			hidden(mob, false);
		if(lockedRoom != null)
		{
			final Room R = lockedRoom;
			lockedRoom = null;
			if(mob.location() == R)
				doors(mob, R, false);
			else
				doors(mob, R, false); // unlocks from where it is: the keeper has the key
		}
		arrived = false;
		dest = null;
		idleTicks = 0;
	}

	protected void arrive(final MOB mob, final Entry E)
	{
		arrived = true;
		idleTicks = 0;
		if(E.say != null)
			CMLib.commands().postSay(mob, null, E.say, false, false);
		if(E.emote != null)
			mob.location().show(mob, null, CMMsg.MSG_OK_ACTION, "<S-NAME> " + E.emote);
		if(E.lock)
		{
			doors(mob, mob.location(), true);
			lockedRoom = mob.location();
		}
		if(E.hide)
			hidden(mob, true);
		if(E.sleep || E.sit)
			rest(mob, E);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)) || entries.isEmpty())
			return true;
		final MOB mob = (MOB)ticking;
		final Room here = mob.location();
		if(mob.amDead() || (here == null) || mob.isInCombat() || (mob.fetchEffect("Alerted") != null)) // N5: an alarm comes first
			return true;
		final Entry E = current(mob);
		if(E != inForce)
		{
			leave(mob);
			inForce = E;
		}
		if(E == null)
			return true;
		if(dest == null)
			dest = placeOf(E, mob);
		if(dest == null)
			return true;
		final boolean roaming = arrived && (E.wander > 0) && (distance(here, dest, E.wander + 1) <= E.wander);
		if((here != dest) && (!roaming))
		{
			arrived = false;
			for(final Enumeration<Behavior> b = mob.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if(B instanceof MobileBehavior)
					((MobileBehavior)B).suspendMobility(2);
			}
			if(!playerNear(here, dest))
			{
				dest.bringMobHere(mob, true); // nobody to see: there at once
				arrive(mob, E);
				return true;
			}
			if(!CMLib.flags().isAliveAwakeMobile(mob, true))
				CMLib.commands().postStand(mob, true, false);
			final List<Room> trail = CMLib.tracking().findTrailToRoom(here, dest, CMLib.tracking().newFlags(), 50);
			final int dir = ((trail == null) || trail.isEmpty()) ? -1 : CMLib.tracking().trackNextDirectionFromHere(trail, here, true);
			if((dir < 0) || (!CMLib.tracking().walk(mob, dir, false, false)))
			{
				dest.bringMobHere(mob, true); // no way there: as though it went by a way the map does not show
				arrive(mob, E);
			}
			else
			if(mob.location() == dest)
				arrive(mob, E);
			return true;
		}
		if(!arrived)
		{
			arrive(mob, E);
			return true;
		}
		idleTicks++;
		if((E.sleep || E.sit) && (idleTicks % 10 == 0))
			rest(mob, E); // woken, or got up: back to it after a while
		if((E.wander > 0) && (CMLib.dice().rollPercentage() <= 10) && CMLib.flags().isAliveAwakeMobile(mob, true)
		&& (!E.sleep) && (!E.sit))
		{
			// WANDER n: now and then a step, staying within n rooms of the place
			final List<Integer> ways = new ArrayList<Integer>();
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room R2 = here.getRoomInDir(d);
				final Exit X = here.getExitInDir(d);
				if((R2 != null) && (X != null) && X.isOpen() && (distance(R2, dest, E.wander + 1) <= E.wander))
					ways.add(Integer.valueOf(d));
			}
			if(!ways.isEmpty())
				CMLib.tracking().walk(mob, ways.get(CMLib.dice().roll(1, ways.size(), -1)).intValue(), false, false);
			return true;
		}
		if((E.work != null) && (E.work.length > 0) && (CMLib.dice().rollPercentage() <= 15)
		&& (!CMLib.flags().isSleeping(mob)))
			here.show(mob, null, CMMsg.MSG_OK_ACTION, "<S-NAME> " + E.work[CMLib.dice().roll(1, E.work.length, -1)].trim());
		return true;
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		if(!super.okMessage(host, msg))
			return false;
		if(anyShop
		&& (host instanceof MOB)
		&& (msg.target() == host)
		&& shopCmds.contains(Integer.valueOf(msg.targetMinor())))
		{
			final MOB mob = (MOB)host;
			final Entry E = inForce;
			if((E == null) || (!E.shop) || (!arrived) || (mob.location() != dest))
			{
				if(CMLib.flags().isSleeping(mob))
					msg.source().tell(msg.source(), mob, null, L("<T-NAME> looks asleep."));
				else
					CMLib.commands().postSay(mob, msg.source(), (shopMsg != null) ? shopMsg : L("Sorry, I'm not open now.  Come back later."), false, false);
				return false;
			}
		}
		return true;
	}

	private final static String[]	CODES	= { "SCHEDULE" };

	@Override
	public String[] getStatCodes()
	{
		final String[] sup = super.getStatCodes();
		final String[] all = Arrays.copyOf(sup, sup.length + CODES.length);
		System.arraycopy(CODES, 0, all, sup.length, CODES.length);
		return all;
	}

	@Override
	public String getStat(final String code)
	{
		if("SCHEDULE".equalsIgnoreCase(code))
		{
			final Entry E = inForce;
			return (E == null) ? "" : E.raw;
		}
		return super.getStat(code);
	}

	@Override
	protected String getRunState()
	{
		return (lockedRoom == null) ? "" : ("<LOCKED>" + CMLib.map().getExtendedRoomID(lockedRoom) + "</LOCKED>");
	}

	@Override
	protected void setRunState(final String xml)
	{
		final int x = (xml == null) ? -1 : xml.indexOf("<LOCKED>");
		final int y = (xml == null) ? -1 : xml.indexOf("</LOCKED>");
		if((x >= 0) && (y > x))
			lockedRoom = CMLib.map().getRoom(xml.substring(x + 8, y));
	}

	@Override
	protected String debugState()
	{
		final Entry E = inForce;
		return super.debugState() + "; in force: " + ((E == null) ? "(no entry)" : E.raw)
			+ ((dest != null) ? ("; place " + CMLib.map().getExtendedRoomID(dest) + (arrived ? ", there" : ", on the way")) : "");
	}
}
