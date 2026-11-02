package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C7: a checkpoint, on a room: NAME= what it is called; ON=ENTER (the
 * default), REST (sitting or sleeping there) or COMMAND:word (typing it);
 * then it is the respawn point of the party there (core/Checkpoints, for
 * DEATH=CHECKPOINT), HEAL=n% heals them, RESPAWNAREA=true brings the area's
 * dead mobs back, and SAVE=AUTO (or a slot) saves the game.
 */
public class Prop_Checkpoint extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Checkpoint";
	}

	@Override
	public String name()
	{
		return "A checkpoint";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "A place to wake, should you fall";
	}

	protected String	label		= "";
	protected String	on			= "ENTER";
	protected String	word		= "";
	protected int		heal		= 0;
	protected boolean	respawnArea	= false;
	protected String	save		= "";

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		label = CMParms.getParmStr(q, "NAME", "").trim();
		final String o = CMParms.getParmStr(q, "ON", "ENTER").trim();
		on = o.toUpperCase().startsWith("COMMAND:") ? "COMMAND" : o.toUpperCase();
		word = o.toUpperCase().startsWith("COMMAND:") ? o.substring(8).trim().toLowerCase() : "";
		heal = Math.max(0, CMath.s_int(CMParms.getParmStr(q, "HEAL", "0").replace("%", "").trim()));
		respawnArea = CMParms.getParmBool(q, "RESPAWNAREA", false);
		save = CMParms.getParmStr(q, "SAVE", "").trim();
	}

	/** the player's side in the room */
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
	 * The party there takes it as its respawn point.
	 * @param R the room
	 * @param who the one who did it
	 */
	public void reach(final Room R, final MOB who)
	{
		final List<MOB> party = party(R);
		if(!party.contains(who))
			party.add(0, who);
		final String called = (label.length() > 0) ? label : R.displayText(who);
		for(final MOB M : party)
		{
			Checkpoints.set(M, R);
			if(heal > 0)
			{
				M.curState().adjHitPoints((int)Math.round(M.maxState().getHitPoints() * heal / 100.0), M.maxState());
				M.curState().adjMana((int)Math.round(M.maxState().getMana() * heal / 100.0), M.maxState());
				M.curState().adjMovement((int)Math.round(M.maxState().getMovement() * heal / 100.0), M.maxState());
			}
			M.tell(CMLib.lang().L("^H@x1 is where you will wake, should you fall.^?", called));
		}
		if(respawnArea && (R.getArea() != null))
		{
			for(final Enumeration<Room> r = R.getArea().getProperMap(); r.hasMoreElements();)
				CMLib.threads().rejuv(r.nextElement(), Tickable.TICKID_MOB);
		}
		if(save.length() > 0)
		{
			final String problem = SaveGame.autoSave(who, save.equalsIgnoreCase("AUTO") ? "checkpoint" : save);
			if(problem != null)
				who.tell(problem);
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		// ON=COMMAND:pray
		if(on.equals("COMMAND") && (affected instanceof Room)
		&& (msg.sourceMinor() == CMMsg.TYP_HUH) && (msg.source() != null)
		&& (msg.source().location() == affected) && GameDifficulty.playerSide(msg.source())
		&& (msg.targetMessage() != null))
		{
			final List<String> w = CMParms.parse(msg.targetMessage());
			if((w.size() > 0) && w.get(0).equalsIgnoreCase(word))
			{
				final Room R = (Room)affected;
				R.show(msg.source(), null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> @x1(s).", word));
				reach(R, msg.source());
				return false;
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((!(affected instanceof Room)) || (msg.source() == null) || (!GameDifficulty.playerSide(msg.source())))
			return;
		final Room R = (Room)affected;
		if(on.equals("ENTER") && (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == R))
		{
			if(!Checkpoints.idOf(msg.source()).equalsIgnoreCase(CMLib.map().getExtendedRoomID(R)))
				reach(R, msg.source());
		}
		else
		if(on.equals("REST") && ((msg.sourceMinor() == CMMsg.TYP_SIT) || (msg.sourceMinor() == CMMsg.TYP_SLEEP))
		&& (msg.source().location() == R))
			reach(R, msg.source());
	}
}
