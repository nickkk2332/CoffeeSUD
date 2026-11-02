package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * I21: rules for a lock, on an exit or a container.  PICK=NEVER (no thief
 * picks it) or PICK=LEVEL:30 (only one of that level or more); KEYS=a;b the
 * key codes it takes besides its own; RELOCK=n ticks after it is unlocked,
 * it shuts and locks again; MSG= what a thief is told.
 */
public class Prop_LockRules extends Property
{
	@Override
	public String ID()
	{
		return "Prop_LockRules";
	}

	@Override
	public String name()
	{
		return "Lock rules";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS | Ability.CAN_EXITS;
	}

	@Override
	public String accountForYourself()
	{
		return "A lock with rules";
	}

	protected boolean	never		= false;
	protected int		pickLevel	= 0;
	protected int		relock		= 0;
	protected int		relockDown	= 0;
	protected String	msgText		= "";
	protected Room		openedIn	= null;
	protected boolean	ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		final String p = CMParms.getParmStr(q, "PICK", "").trim().toUpperCase();
		never = p.equals("NEVER");
		pickLevel = p.startsWith("LEVEL:") ? CMath.s_int(p.substring(6)) : 0;
		relock = Math.max(0, CMParms.getParmInt(q, "RELOCK", 0));
		msgText = CMParms.getParmStr(q, "MSG", "This lock cannot be picked.");
	}

	/** whether a message is a picking of this lock */
	protected boolean picking(final CMMsg msg)
	{
		if((msg.target() != affected) || (!(msg.tool() instanceof Ability)))
			return false;
		final Ability A = (Ability)msg.tool();
		return ((A.classificationCode() & Ability.ALL_ACODES) == Ability.ACODE_THIEF_SKILL)
			|| A.ID().toUpperCase().indexOf("PICK") >= 0;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(picking(msg) && (never || (msg.source().phyStats().level() < pickLevel)))
		{
			msg.source().tell(msgText);
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((relock > 0) && (msg.target() == affected) && (msg.targetMinor() == CMMsg.TYP_UNLOCK) && (msg.source() != null))
		{
			openedIn = msg.source().location();
			relockDown = relock;
			if(!ticking)
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((relockDown > 0) && (--relockDown <= 0))
		{
			this.ticking = false;
			if((openedIn != null) && (affected != null))
				Doors.change(openedIn, affected, "LOCK", CMLib.lang().L("<T-NAME> swings shut and locks."));
			return false;
		}
		if(relockDown <= 0)
			this.ticking = false;
		return relockDown > 0;
	}
}
