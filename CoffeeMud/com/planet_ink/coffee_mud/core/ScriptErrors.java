package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.SaveGame;

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
 * Script errors (DT10), MOBPROG and JavaScript both, already logged: kept for
 * SCRIPTERRORS, and shown or not.  In builder mode and a DEV=1 game the
 * builder is told each one.  In a normal game the ini's SCRIPTERRORS decides:
 * LOG (the default) only logs them, NOTICE tells each player once that a
 * script went wrong, SHOW shows each to the room it happened in.
 */
public final class ScriptErrors
{
	/** how many are kept */
	public static final int					KEEP		= 200;

	private static final LinkedList<String>	recent		= new LinkedList<String>();
	/** the sessions told, under NOTICE */
	private static final Map<Session, Boolean>	noticed	= Collections.synchronizedMap(new WeakHashMap<Session, Boolean>());

	private ScriptErrors()
	{
	}

	/** what is done with an error in a normal game */
	public enum Mode
	{
		LOG, NOTICE, SHOW
	}

	/**
	 * What a normal game does with an error: the ini's SCRIPTERRORS.
	 * @return the mode
	 */
	public static Mode mode()
	{
		final Mode M = (Mode)CMath.s_valueOf(Mode.class, CMProps.instance().getStr("SCRIPTERRORS", "LOG").trim().toUpperCase());
		return (M == null) ? Mode.LOG : M;
	}

	/**
	 * Keeps an error, and shows it as the game is set to.
	 * @param error the error, as logged
	 * @param R the room it happened in, or null
	 */
	public static void report(final String error, final Room R)
	{
		synchronized(recent)
		{
			recent.addLast(CMLib.time().date2String(System.currentTimeMillis()) + "  " + error);
			while(recent.size() > KEEP)
				recent.removeFirst();
		}
		try
		{
			if(SaveGame.isBuilderMode() || SaveGame.isDevMode())
			{
				for(final Session S : CMLib.sessions().allIterable())
				{
					final MOB M = S.mob();
					if(M != null)
						M.tell("^RScript error: " + error + "^?");
				}
				return;
			}
			switch(mode())
			{
			case SHOW:
				if(R != null)
					R.showHappens(CMMsg.MSG_OK_VISUAL, "Script error: " + error);
				break;
			case NOTICE:
				for(final Session S : CMLib.sessions().allIterable())
				{
					if((S.mob() != null) && (noticed.put(S, Boolean.TRUE) == null))
						S.mob().tell("^w(A script in this world went wrong; the log has what.)^N");
				}
				break;
			default:
				break;
			}
		}
		catch(final RuntimeException e)
		{
			// an error that cannot be shown is still kept and logged
		}
	}

	/**
	 * The errors kept, oldest first, those holding a text if one is given.
	 * @param max the most to return
	 * @param holding text each must hold, or null
	 * @return the errors
	 */
	public static List<String> recent(final int max, final String holding)
	{
		final List<String> out = new ArrayList<String>();
		synchronized(recent)
		{
			for(final String s : recent)
			{
				if((holding == null) || (holding.length() == 0) || s.toUpperCase().contains(holding.toUpperCase()))
					out.add(s);
			}
		}
		while(out.size() > max)
			out.remove(0);
		return out;
	}

	/**
	 * Forgets the errors kept.
	 */
	public static void clear()
	{
		synchronized(recent)
		{
			recent.clear();
		}
	}
}
