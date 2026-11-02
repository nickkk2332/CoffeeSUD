package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * N20: an NPC escorted to a place.  Its leader is the one it follows when
 * the escort begins (a quest's MPFORCE $i follow $n, say).  DEST=room id,
 * MODE=FOLLOW (it follows its leader, the default) or LEAD (it walks the
 * way itself, a step a tick, and the party follows it); WAITFOR=LEADER:
 * while its leader is not with it, it does not go on, and after WAITCALL=n
 * ticks (3) it calls WAITMSG ("Wait for me!"); PANIC=FLEE|HIDE|COWER when
 * attacked; AVOID=room id,room id: it will not go into those;
 * TALK=room:line (and TALK1= to TALK9=) a remark on coming there;
 * ONARRIVE=, ONDEATH= and, with TIMEOUT=n ticks, ONTIMEOUT=: MOBPROG
 * lines run with its leader as $n (say Thank you!;mpsetvar $n ESCORTED 1).
 * Its leader, arrival and time are kept in a saved game.
 */
public class Escortee extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Escortee";
	}

	@Override
	public String name()
	{
		return "an escorted traveller";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "being escorted";
	}

	protected String				dest		= "";
	protected boolean				lead		= false;
	protected boolean				waitFor		= false;
	protected int					waitCall	= 3;
	protected String				waitMsg		= "Wait for me!";
	protected String				panic		= "";
	protected final List<String>	avoid		= new ArrayList<String>();
	protected final List<String[]>	talk		= new ArrayList<String[]>();
	protected String				onArrive	= "";
	protected String				onDeath		= "";
	protected String				onTimeout	= "";
	protected int					timeout		= 0;

	// what it has built up
	protected String				leaderName	= "";
	protected MOB					leader		= null;
	protected boolean				done		= false;
	protected int					ticks		= 0;
	protected int					awayTicks	= 0;
	protected boolean				called		= false;
	protected Room					lastRoom	= null;
	protected final Set<String>		remarked	= new HashSet<String>();
	/** the panic message on its way, after an attack */
	protected String				panicking	= null;

	private static ScriptingEngine	acts		= null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		final Map<String, String> P = CMParms.parseEQParms(p);
		dest = P.containsKey("DEST") ? P.get("DEST").trim() : "";
		lead = P.containsKey("MODE") && P.get("MODE").trim().equalsIgnoreCase("LEAD");
		waitFor = P.containsKey("WAITFOR") && P.get("WAITFOR").trim().equalsIgnoreCase("LEADER");
		waitCall = CMParms.getParmInt(p, "WAITCALL", 3);
		waitMsg = P.containsKey("WAITMSG") ? P.get("WAITMSG") : "Wait for me!";
		panic = P.containsKey("PANIC") ? P.get("PANIC").trim().toUpperCase() : "";
		avoid.clear();
		if(P.containsKey("AVOID"))
			avoid.addAll(CMParms.parseCommas(P.get("AVOID"), true));
		talk.clear();
		for(final String k : new String[] { "TALK", "TALK1", "TALK2", "TALK3", "TALK4", "TALK5", "TALK6", "TALK7", "TALK8", "TALK9" })
		{
			final String t = P.get(k);
			if(t == null)
				continue;
			final int x = t.indexOf(':');
			if(x > 0)
				talk.add(new String[] { t.substring(0, x).trim(), t.substring(x + 1).trim() });
		}
		onArrive = P.containsKey("ONARRIVE") ? P.get("ONARRIVE") : "";
		onDeath = P.containsKey("ONDEATH") ? P.get("ONDEATH") : "";
		onTimeout = P.containsKey("ONTIMEOUT") ? P.get("ONTIMEOUT") : "";
		timeout = CMParms.getParmInt(p, "TIMEOUT", 0);
	}

	/** whether a room is the one an ID names: its ID, or its end */
	protected static boolean is(final Room R, final String id)
	{
		if((R == null) || (id == null) || (id.length() == 0))
			return false;
		final String rid = CMLib.map().getExtendedRoomID(R);
		return rid.equalsIgnoreCase(id) || rid.toUpperCase().endsWith(id.toUpperCase());
	}

	protected boolean avoided(final Room R)
	{
		for(final String a : avoid)
		{
			if(is(R, a))
				return true;
		}
		return false;
	}

	/** MOBPROG lines, the escortee their host, its leader $n */
	protected void act(final MOB mob, final String lines)
	{
		if((lines == null) || (lines.trim().length() == 0))
			return;
		if(acts == null)
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setSavable(false);
			S.setVarScope("");
			acts = S;
		}
		synchronized(acts)
		{
			acts.setScript("FUNCTION_PROG ESCORT_ACT\n" + lines + "\n~\n");
			acts.callFunction(mob, "ESCORT_ACT", "", (leader != null) ? leader : mob);
		}
	}

	/** its leader: the one it follows, or the one it was escorted by */
	protected MOB leaderOf(final MOB mob)
	{
		final MOB f = mob.amFollowing();
		if((f != null) && (f != mob))
		{
			leader = f;
			leaderName = f.Name();
		}
		if((leader == null) && (leaderName.length() > 0) && (mob.location() != null))
			leader = mob.location().fetchInhabitantExact(leaderName);
		if((leader != null) && (leader.amDestroyed() || leader.amDead()))
			leader = null;
		return leader;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)) || done)
			return true;
		final MOB mob = (MOB)ticking;
		final Room here = mob.location();
		if((here == null) || mob.amDead() || mob.isInCombat())
			return true;
		final MOB L = leaderOf(mob);
		if(L == null)
			return true; // not escorted yet
		if(lead && (mob.amFollowing() != null))
			mob.setFollowing(null); // it leads: the party follows it
		ticks++;
		// a remark on coming somewhere
		if(here != lastRoom)
		{
			lastRoom = here;
			for(final String[] t : talk)
			{
				if(is(here, t[0]) && remarked.add(t[0].toUpperCase()))
					CMLib.commands().postSay(mob, null, t[1], false, false);
			}
		}
		if(is(here, dest))
		{
			done = true;
			if(mob.amFollowing() != null)
				mob.setFollowing(null);
			act(mob, onArrive);
			return true;
		}
		if((timeout > 0) && (ticks >= timeout))
		{
			done = true;
			act(mob, onTimeout);
			return true;
		}
		final boolean withLeader = (L.location() == here);
		if(!withLeader)
		{
			awayTicks++;
			if((awayTicks >= waitCall) && (!called))
			{
				called = true;
				CMLib.commands().postSay(mob, null, waitMsg, false, false);
			}
			if(waitFor || (!lead))
				return true;
		}
		else
		{
			awayTicks = 0;
			called = false;
		}
		if(lead)
		{
			final Room to = CMLib.map().getRoom(dest);
			if(to == null)
				return true;
			final List<Room> trail = CMLib.tracking().findTrailToRoom(here, to, CMLib.tracking().newFlags(), 50);
			final int dir = ((trail == null) || trail.isEmpty()) ? -1 : CMLib.tracking().trackNextDirectionFromHere(trail, here, true);
			if(dir >= 0)
			{
				if(!CMLib.flags().isAliveAwakeMobile(mob, true))
					CMLib.commands().postStand(mob, true, false);
				CMLib.tracking().walk(mob, dir, false, false);
			}
		}
		return true;
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		if(!super.okMessage(host, msg))
			return false;
		// not into a room it avoids
		if((host instanceof MOB)
		&& (msg.source() == host)
		&& (msg.targetMinor() == CMMsg.TYP_ENTER)
		&& (msg.target() instanceof Room)
		&& (!done)
		&& avoided((Room)msg.target()))
		{
			CMLib.commands().postSay((MOB)host, null, L("I won't go there."), false, false);
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host, msg);
		if(!(host instanceof MOB))
			return;
		final MOB mob = (MOB)host;
		if((msg.source() == mob) && ((msg.sourceMinor() == CMMsg.TYP_DEATH) || (msg.othersMinor() == CMMsg.TYP_DEATH)))
		{
			if(!done)
			{
				done = true;
				leaderOf(mob);
				act(mob, onDeath);
			}
			return;
		}
		// its panic, once the attack is done: a message of its own after it
		if((panicking != null) && (msg.source() == mob) && (msg.sourceMinor() == CMMsg.TYP_OK_ACTION)
		&& (msg.othersMessage() != null) && msg.othersMessage().equals(panicking))
		{
			panicking = null;
			if(panic.equals("FLEE"))
				CMLib.commands().postFlee(mob, "");
			else
				mob.makePeace(true);
			return;
		}
		if((msg.target() == mob)
		&& (msg.source() != mob)
		&& CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS)
		&& (panic.length() > 0)
		&& (!done)
		&& (panicking == null))
		{
			if(panic.equals("FLEE"))
				panicking = L("<S-NAME> panic(s)!");
			else
			if(panic.equals("HIDE"))
				panicking = L("<S-NAME> dive(s) for cover!");
			else
				panicking = L("<S-NAME> cower(s), begging for mercy!");
			msg.addTrailerMsg(CMClass.getMsg(mob, null, null, CMMsg.MSG_OK_ACTION, panicking));
		}
	}

	@Override
	protected String getRunState()
	{
		final StringBuilder str = new StringBuilder("<ESCORT");
		str.append(" LEADER=\"").append(CMLib.xml().parseOutAngleBrackets(leaderName)).append("\"");
		str.append(" DONE=\"").append(done).append("\"");
		str.append(" TICKS=\"").append(ticks).append("\"");
		str.append(" SAID=\"").append(CMLib.xml().parseOutAngleBrackets(CMParms.toListString(remarked))).append("\"");
		return str.append(" />").toString();
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
		leaderName = CMLib.xml().restoreAngleBrackets(tag.getParmValue("LEADER"));
		leader = null;
		done = CMath.s_bool(tag.getParmValue("DONE"));
		ticks = CMath.s_int(tag.getParmValue("TICKS"));
		remarked.clear();
		for(final String s : CMParms.parseCommas(CMLib.xml().restoreAngleBrackets(tag.getParmValue("SAID")), true))
			remarked.add(s.toUpperCase());
	}

	@Override
	protected String debugState()
	{
		return super.debugState() + "; " + (done ? "done" : ("to " + dest + ((leaderName.length() > 0) ? (" with " + leaderName) : ", not yet escorted") + ", " + ticks + " ticks"));
	}
}
