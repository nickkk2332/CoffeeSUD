package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMSecurity.SecFlag;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.Properties.Prop_DevCheats;
import com.planet_ink.coffee_mud.Abilities.Properties.Prop_DevCheats.Cheat;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * GOD [cheat|power|ALL ...] [ON|OFF]: a developer's cheats (see
 * {@link Prop_DevCheats}), in a DEV=1 game or builder mode.  GOD alone lists
 * them.  A cheat's name turns it on or off (or over, with neither); a sysop
 * power's name (IMMORT, GOTO...) with OFF suspends that power for the
 * character, and with ON gives it back.  ALL is every cheat; ALL OFF also
 * turns off what the sysop powers give, to play as a mortal would.  GOD RESET
 * forgets them all.
 */
public class GodCmd extends StdCommand
{
	public GodCmd()
	{
	}

	private final String[] access = I(new String[] { "GOD" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** the cheats that a sysop power gives (or partly gives) when not set */
	private static boolean hasPower(final Cheat C)
	{
		return C.power.length() > 0;
	}

	/**
	 * The list GOD alone gives.
	 * @param mob the character
	 * @return the list
	 */
	public static String listing(final MOB mob)
	{
		final Prop_DevCheats P = Prop_DevCheats.of(mob);
		final StringBuilder str = new StringBuilder("^HGOD^N: your cheats (GOD [CHEAT|POWER|ALL] [ON|OFF], GOD RESET)\n\r");
		for(final Cheat C : Cheat.values())
		{
			final Boolean set = Prop_DevCheats.state(mob, C);
			final boolean now = Prop_DevCheats.inEffect(mob, C);
			final String how;
			if(set != null)
				how = set.booleanValue() ? "on" : "off";
			else
			if(now)
				how = "on (" + C.power + ")";
			else
				how = "off";
			str.append(CMStrings.padRight(C.name(), 9)).append(" ").append(CMStrings.padRight(how, 22)).append(" ").append(C.what).append("\n\r");
		}
		if(P != null)
		{
			final Set<SecFlag> sus = P.suspendedPowers();
			if(!sus.isEmpty())
			{
				final List<String> names = new ArrayList<String>();
				for(final SecFlag F : sus)
					names.add(F.name());
				str.append("Suspended powers: ").append(String.join(" ", names)).append("\n\r");
			}
		}
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(listing(mob));
			return false;
		}
		final List<String> words = new ArrayList<String>(commands.subList(1, commands.size()));
		Prop_DevCheats P = Prop_DevCheats.of(mob);
		if((words.size() == 1) && (words.get(0).equalsIgnoreCase("RESET")))
		{
			if(P != null)
				mob.delEffect(P);
			mob.recoverPhyStats();
			mob.tell(L("Your cheats are gone, and your powers are as they were."));
			return false;
		}
		Boolean value = null;
		final String last = words.get(words.size() - 1).toUpperCase();
		if(last.equals("ON") || last.equals("OFF"))
		{
			value = Boolean.valueOf(last.equals("ON"));
			words.remove(words.size() - 1);
		}
		if(words.isEmpty())
		{
			mob.tell(L("GOD what?  @x1", listing(mob)));
			return false;
		}
		// the words as they stand: cheat names, -cheat names and -powers
		final List<String> text = new ArrayList<String>();
		if(P != null)
			text.addAll(CMParms.parse(P.text()));
		final List<Object> targets = new ArrayList<Object>();
		for(final String w : words)
		{
			if(w.equalsIgnoreCase("ALL"))
			{
				targets.addAll(Arrays.asList(Cheat.values()));
				continue;
			}
			final Object O = Prop_DevCheats.wordOf(w);
			if(O == null)
			{
				mob.tell(L("'@x1' is neither a cheat (@x2) nor a power.", w, CMParms.toListString(Cheat.values())));
				return false;
			}
			targets.add(O);
		}
		final List<String> said = new ArrayList<String>();
		for(final Object O : targets)
		{
			final String name = (O instanceof Cheat) ? ((Cheat)O).name() : ((SecFlag)O).name();
			removeWord(text, name);
			if(O instanceof Cheat)
			{
				final Cheat C = (Cheat)O;
				final boolean on = (value != null) ? value.booleanValue() : !Prop_DevCheats.inEffect(mob, C);
				if(on)
					text.add(name);
				else
				if(hasPower(C))
					text.add("-" + name);
				said.add(name + (on ? " on" : " off"));
			}
			else
			{
				final boolean suspendedNow = (P != null) && P.suspendedPowers().contains(O);
				final boolean suspend = (value != null) ? !value.booleanValue() : !suspendedNow;
				if(suspend)
					text.add("-" + name);
				said.add(name + (suspend ? " suspended" : " given back"));
			}
		}
		if(text.isEmpty())
		{
			if(P != null)
				mob.delEffect(P);
		}
		else
		{
			if(P == null)
			{
				P = (Prop_DevCheats)CMClass.getAbility("Prop_DevCheats");
				mob.addNonUninvokableEffect(P);
			}
			P.setMiscText(String.join(" ", text));
		}
		mob.recoverPhyStats();
		mob.tell(L("GOD: @x1.", String.join(", ", said)));
		return false;
	}

	private static void removeWord(final List<String> text, final String name)
	{
		for(final Iterator<String> i = text.iterator(); i.hasNext();)
		{
			final String w = i.next();
			if(w.equalsIgnoreCase(name) || w.equalsIgnoreCase("-" + name))
				i.remove();
		}
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return SaveGame.isDevMode() || SaveGame.isBuilderMode();
	}
}
