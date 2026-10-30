package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.Session.InputCallback;
import com.planet_ink.coffee_mud.Common.interfaces.Session.SessionPing;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2004-2025 Bo Zimmerman

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
public class Quit extends StdCommand
{
	public Quit()
	{
	}

	private final String[] access=I(new String[]{"QUIT","QUI","Q"});
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	public static void dispossess(final MOB mob, final boolean force)
	{
		if(mob.soulMate()==null)
		{
			mob.tell(CMLib.lang().L("Huh?"));
			return;
		}
		final CMMsg msg=CMClass.getMsg(mob, CMMsg.MSG_DISPOSSESS, CMLib.lang().L("^H<S-YOUPOSS> spirit has returned to <S-YOUPOSS> body...\n\r\n\r^N"));
		final Room room=mob.location();
		if((room==null)||(room.okMessage(mob, msg))||force)
		{
			if(room!=null)
				room.send(mob, msg);
			mob.dispossess(true);
		}
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(mob.soulMate()!=null)
			dispossess(mob,CMParms.combine(commands).endsWith("!"));
		else
		if(!mob.isMonster())
		{
			final Session session=mob.session();
			if(session!=null)
			{
				if((session.getLastPKFight()>0)
				&&((System.currentTimeMillis()-session.getLastPKFight())<(5*60*1000))
				&&(!CMSecurity.isASysOp(mob)))
				{
					mob.tell(L("You must wait a few more minutes before you are allowed to quit."));
					return false;
				}
				if((!CMLib.masking().maskCheck(CMProps.getVar(CMProps.Str.LOGOUTMASK), mob, true))
				&&(!CMSecurity.isASysOp(mob)))
				{
					mob.tell(L("You are not permitted to quit at this time."));
					mob.tell(L("Quitting requires: ")+CMLib.masking().maskDesc(CMProps.getVar(CMProps.Str.LOGOUTMASK), false));
					return false;
				}
				if(SaveGame.isAvailable())
				{
					promptSaveAndQuit(mob, session);
					return false;
				}
				session.prompt(new InputCallback(InputCallback.Type.CONFIRM, "N", 30000)
				{
					@Override
					public void showPrompt()
					{
						session.promptPrint(L("\n\rQuit -- are you sure (y/N)?"));
					}

					@Override
					public void timedOut()
					{
					}

					@Override
					public void callBack()
					{
						if(this.confirmed)
						{
							final CMMsg msg=CMClass.getMsg(mob,null,CMMsg.MSG_QUIT,null);
							final Room R=mob.location();
							if((R!=null)&&(R.okMessage(mob,msg)))
							{
								CMLib.map().sendGlobalMessage(mob,CMMsg.TYP_QUIT, msg);
								mob.tell(L("Bye!"));
								session.stopSession(true,false, false, false); // this should call prelogout and later loginlogoutthread to cause msg SEND
								CMLib.commands().monitorGlobalMessage(R, msg);
							}
						}
					}
				});
			}
		}
		return false;
	}

	/**
	 * In a single-player game that can be saved, asks whether to save before
	 * quitting, since a game that is not saved is lost.
	 * @param mob the player
	 * @param session their session
	 */
	protected void promptSaveAndQuit(final MOB mob, final Session session)
	{
		final String slot=SaveGame.defaultSlot(mob);
		session.prompt(new InputCallback(InputCallback.Type.CHOOSE, "Y", "YNC", 0)
		{
			@Override
			public void showPrompt()
			{
				session.promptPrint(L("\n\rQuit -- save the game as '@x1' first (Y/n), or cancel (c)? ",slot));
			}

			@Override
			public void timedOut()
			{
			}

			@Override
			public void callBack()
			{
				final String answer=this.input.trim().toUpperCase();
				if(answer.startsWith("C"))
					return;
				if(!answer.startsWith("N"))
				{
					final String problem=SaveGame.save(mob, slot);
					if(problem!=null)
					{
						mob.tell(problem);
						mob.tell(L("You have not quit."));
						return;
					}
					mob.tell(L("Game saved as '@x1'.",slot));
				}
				final CMMsg msg=CMClass.getMsg(mob,null,CMMsg.MSG_QUIT,null);
				final Room R=mob.location();
				if((R!=null)&&(R.okMessage(mob,msg)))
				{
					CMLib.map().sendGlobalMessage(mob,CMMsg.TYP_QUIT, msg);
					mob.tell(L("Bye!"));
					session.stopSession(true,false, false, false);
					CMLib.commands().monitorGlobalMessage(R, msg);
				}
			}
		});
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandCombatActionCost(ID());
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandActionCost(ID());
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

}
