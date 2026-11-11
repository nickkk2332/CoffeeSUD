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
 * P11: CULTURE (one's culture and what it gave), CULTURE LIST, CULTURE ADOPT
 * [culture] (only those whose ADOPT is COMMAND) and CULTURE LEAVE.
 */
public class CultureCmd extends StdCommand
{
	private final String[] access=I(new String[]{"CULTURE"});
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
		final String w=(words.size()>1)?words.get(1).toUpperCase(Locale.ROOT):"";
		try
		{
			if(w.equals("LIST"))
				mob.tell(Cultures.show(mob,true));
			else
			if(w.equals("ADOPT")&&(words.size()>2))
				mob.tell(Cultures.adopt(mob,CMParms.combine(words,2),"command"));
			else
			if(w.equals("LEAVE"))
				mob.tell(Cultures.leave(mob,"command"));
			else
				mob.tell(Cultures.show(mob,false));
		}
		catch(final java.io.IOException e)
		{
			mob.tell(L("Culture: @x1.",e.getMessage()));
			if(w.equals("LEAVE"))
				Provenance.refused(mob,Cultures.FAMILY,"CULTURE LEAVE",e.getMessage(),"a culture that cannot be left is left only by the world's own scripts");
		}
		return false;
	}
}
