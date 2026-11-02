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
 * C20: an arena of waves, on a room.  WAVE1=, WAVE2=... each
 * "catalog:name*dice+name*dice" (catalog: may be left out; a class name
 * works too) or encounter:table (a roll of a C9 table).  NEXT=CLEAR (the
 * default: when a wave is dead) or TICKS=n; START=ENTER (the party walks in)
 * or CALL (MPWAVE START); LOCKEXITS=true keeps the party in while it runs;
 * ONWIN=function (a FUNCTION_PROG of the room's scripts, $n the party's
 * leader) or UNLOCK; ONLOSE=RESET, or SPARRING(:room ID) for a defeat that
 * is not death (Prop_SparringRoom while it runs); REWARD= an item for the
 * winner; RESETAFTER=n ticks before it can start again after a win.
 * WAVE_PROG n, ARENAWIN_PROG and ARENALOSE_PROG run on the room's scripts
 * and its NPCs'.  The wave and state are kept in a saved game.
 */
public class WaveSpawner extends StdBehavior
{
	@Override
	public String ID()
	{
		return "WaveSpawner";
	}

	@Override
	public String name()
	{
		return "an arena of waves";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "an arena of waves";
	}

	/** the tag on a wave's monsters */
	public static final String TAG = "WAVE";

	protected final List<String>	waves		= new ArrayList<String>();
	protected int					nextTicks	= 0;
	protected boolean				startOnEnter= true;
	protected boolean				lockExits	= false;
	protected String				onWin		= "UNLOCK";
	protected boolean				sparring	= false;
	protected String				sparRoom	= "";
	protected String				reward		= "";
	protected int					resetAfter	= 0;

	// the run
	protected String				state		= "IDLE";
	protected int					wave		= 0;
	protected int					waveTicks	= 0;
	protected int					resetDown	= 0;
	protected boolean				startNext	= false;
	protected MOB					starter		= null;
	protected final List<MOB>		current		= new ArrayList<MOB>();
	protected boolean				recollect	= false;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final Map<String, String> q = CMParms.parseExactParms((newParms == null) ? "" : newParms);
		waves.clear();
		for(int i = 1; q.containsKey("WAVE" + i); i++)
			waves.add(CMParms.getParmStr(q, "WAVE" + i, "").trim());
		final String n = CMParms.getParmStr(q, "NEXT", "CLEAR").trim().toUpperCase();
		nextTicks = n.startsWith("TICKS") ? Math.max(1, CMath.s_int(n.replaceAll("[^0-9]", ""))) : 0;
		if((nextTicks == 0) && q.containsKey("TICKS") && n.equals("TICKS"))
			nextTicks = Math.max(1, CMParms.getParmInt(q, "TICKS", 1));
		startOnEnter = !CMParms.getParmStr(q, "START", "ENTER").trim().equalsIgnoreCase("CALL");
		lockExits = CMParms.getParmBool(q, "LOCKEXITS", false);
		onWin = CMParms.getParmStr(q, "ONWIN", "UNLOCK").trim();
		final String l = CMParms.getParmStr(q, "ONLOSE", "RESET").trim();
		sparring = l.toUpperCase().startsWith("SPARRING");
		sparRoom = (sparring && (l.indexOf(':') > 0)) ? l.substring(l.indexOf(':') + 1).trim() : "";
		reward = CMParms.getParmStr(q, "REWARD", "").trim();
		resetAfter = Math.max(0, CMParms.getParmInt(q, "RESETAFTER", 0));
	}

	/** the player's side in the arena */
	protected static List<MOB> party(final Room R)
	{
		final List<MOB> list = new ArrayList<MOB>();
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (!M.amDead()) && GameDifficulty.playerSide(M))
				list.add(M);
		}
		return list;
	}

	/**
	 * Makes one wave's monsters in the room.
	 * @param spec the wave: catalog:name*dice+..., or encounter:table
	 * @param R the room
	 * @param foe one of the party, or null
	 * @return the monsters
	 */
	public static List<MOB> spawn(final String spec, final Room R, final MOB foe)
	{
		final List<MOB> made = Encounters.spawnSpec(spec, R, foe);
		for(final MOB M : made)
		{
			M.addTag(TAG);
			if((foe != null) && (foe.location() == R) && (M.location() == R))
				CMLib.combat().postAttack(M, foe, M.fetchWieldedItem());
		}
		return made;
	}

	/** the next wave, or the win */
	protected void nextWave(final Room R)
	{
		final List<MOB> party = party(R);
		final MOB foe = party.isEmpty() ? starter : party.get(CMLib.dice().roll(1, party.size(), -1));
		if(wave >= waves.size())
		{
			win(R, foe);
			return;
		}
		wave++;
		waveTicks = 0;
		current.addAll(spawn(waves.get(wave - 1), R, foe));
		ScriptEvents.wave(R, wave, foe);
	}

	protected void start(final Room R, final MOB who)
	{
		state = "RUNNING";
		wave = 0;
		starter = who;
		current.clear();
		if(sparring && (R.fetchEffect("Prop_SparringRoom") == null))
		{
			final Ability A = CMClass.getAbility("Prop_SparringRoom");
			if(A != null)
			{
				A.setMiscText(sparRoom);
				A.setSavable(false);
				R.addNonUninvokableEffect(A);
			}
		}
		nextWave(R);
	}

	/** what is left of a wave goes; the room is as before */
	protected void clearUp(final Room R)
	{
		for(final MOB M : new ArrayList<MOB>(current))
		{
			if((!M.amDestroyed()) && (!M.amDead()))
			{
				if(M.location() != null)
					M.location().show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> leave(s) the arena."));
				M.destroy();
			}
		}
		current.clear();
		final Ability A = R.fetchEffect("Prop_SparringRoom");
		if(sparring && (A != null) && (!A.isSavable()))
			R.delEffect(A);
	}

	protected void win(final Room R, final MOB who)
	{
		clearUp(R);
		state = "WON";
		resetDown = resetAfter;
		final List<MOB> party = party(R);
		final MOB leader = (who != null) && (who.location() == R) ? who : (party.isEmpty() ? null : party.get(0));
		if(reward.length() > 0)
		{
			final Physical P = Encounters.make(reward, false);
			if(P instanceof Item)
			{
				final Item I = (Item)P;
				if(leader != null)
				{
					leader.addItem(I);
					leader.tell(CMLib.lang().L("You receive @x1.", I.name(leader)));
				}
				else
					R.addItem(I, ItemPossessor.Expire.Player_Drop);
			}
		}
		if((onWin.length() > 0) && (!onWin.equalsIgnoreCase("UNLOCK")))
		{
			for(final ScriptingEngine S : ScriptEvents.engines(R))
				S.callFunction(R, onWin, "", leader);
		}
		ScriptEvents.arenaWin(R, leader);
	}

	protected void lose(final Room R)
	{
		clearUp(R);
		ScriptEvents.arenaLose(R, starter);
		state = "IDLE";
		wave = 0;
		starter = null;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		// LOCKEXITS: the party stays in while the waves come
		if(lockExits && state.equals("RUNNING") && (myHost instanceof Room)
		&& (msg.source() != null) && (msg.source().location() == myHost) && GameDifficulty.playerSide(msg.source())
		&& (((msg.targetMinor() == CMMsg.TYP_LEAVE) && (msg.target() == myHost)) || (msg.sourceMinor() == CMMsg.TYP_FLEE)))
		{
			msg.source().tell(CMLib.lang().L("The way out is barred until the fight is done."));
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(startOnEnter && state.equals("IDLE") && (myHost instanceof Room)
		&& (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == myHost)
		&& (msg.source() != null) && GameDifficulty.playerSide(msg.source()) && (waves.size() > 0))
		{
			startNext = true; // on its next tick
			starter = msg.source();
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(!(ticking instanceof Room))
			return true;
		final Room R = (Room)ticking;
		lastHost = R;
		if(recollect)
		{
			// back from a saved game: the wave's monsters are those tagged here
			recollect = false;
			current.clear();
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.hasTag(TAG))
					current.add(M);
			}
		}
		if(state.equals("WON"))
		{
			if((resetAfter > 0) && (--resetDown <= 0))
			{
				state = "IDLE";
				wave = 0;
			}
			return true;
		}
		if(state.equals("IDLE"))
		{
			if(startNext)
			{
				startNext = false;
				if((starter != null) && (starter.location() == R))
					start(R, starter);
			}
			return true;
		}
		// running
		for(final Iterator<MOB> i = current.iterator(); i.hasNext();)
		{
			final MOB M = i.next();
			if(M.amDestroyed() || M.amDead() || (M.location() == null))
				i.remove();
		}
		if(party(R).isEmpty())
		{
			lose(R);
			return true;
		}
		waveTicks++;
		if(current.isEmpty() || ((nextTicks > 0) && (waveTicks >= nextTicks) && (wave < waves.size())))
			nextWave(R);
		return true;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("WAVE"))
			return "" + wave;
		if(code.equalsIgnoreCase("ARENASTATE"))
			return state;
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(code.equalsIgnoreCase("WAVECOMMAND"))
		{
			// MPWAVE START|NEXT|STOP|RESET
			final Room R = CMLib.map().roomLocation(host());
			final String w = (val == null) ? "" : val.trim().toUpperCase();
			if(R == null)
				return;
			if(w.equals("START") && state.equals("IDLE"))
			{
				final List<MOB> party = party(R);
				start(R, party.isEmpty() ? null : party.get(0));
			}
			else
			if(w.equals("NEXT") && state.equals("RUNNING"))
				nextWave(R);
			else
			if(w.equals("STOP") && state.equals("RUNNING"))
				lose(R);
			else
			if(w.equals("RESET"))
			{
				clearUp(R);
				state = "IDLE";
				wave = 0;
			}
			return;
		}
		super.setStat(code, val);
	}

	/** the room it is on, as the last tick or message saw it */
	protected Environmental host()
	{
		return lastHost;
	}

	protected Environmental	lastHost	= null;

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		lastHost = forMe;
	}

	@Override
	protected String getRunState()
	{
		if(state.equals("IDLE") && (wave == 0))
			return "";
		return "<WAVES STATE=\"" + state + "\" WAVE=\"" + wave + "\" T=\"" + waveTicks + "\" R=\"" + resetDown + "\" />";
	}

	@Override
	protected void setRunState(final String xml)
	{
		if((xml == null) || (xml.trim().length() == 0))
			return;
		final List<XMLLibrary.XMLTag> V = CMLib.xml().parseAllXML(xml);
		if((V == null) || V.isEmpty())
			return;
		state = V.get(0).getParmValue("STATE");
		if((state == null) || (state.length() == 0))
			state = "IDLE";
		wave = CMath.s_int(V.get(0).getParmValue("WAVE"));
		waveTicks = CMath.s_int(V.get(0).getParmValue("T"));
		resetDown = CMath.s_int(V.get(0).getParmValue("R"));
		recollect = true;
	}
}
