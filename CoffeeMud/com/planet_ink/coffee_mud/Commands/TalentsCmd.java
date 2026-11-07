package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.*;
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
 * P15: TALENTS, a character's latent talents (core/Talents): each awakened,
 * dormant, or what it still waits for.  It takes no game time and decides
 * nothing.
 */
public class TalentsCmd extends StdCommand
{
	private final String[] access=I(new String[]{"TALENTS"});
	@Override public String[] getAccessWords()
	{
		return access;
	}
	@Override public boolean canBeOrdered()
	{
		return false;
	}
	@Override public double actionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public double combatActionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public boolean execute(final MOB mob,final List<String> words,final int flags)throws java.io.IOException
	{
		mob.tell(Talents.list(mob));
		return false;
	}
}
