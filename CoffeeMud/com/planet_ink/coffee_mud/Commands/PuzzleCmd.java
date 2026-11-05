package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.Properties.Prop_Switch;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * DT33: inspect and operate the implemented switch, pressure-plate and signal
 * primitives, and check the world's puzzle references. Generic controllers
 * remain a separate deferred feature.
 */
public class PuzzleCmd extends StdCommand
{
	public PuzzleCmd()
	{
	}

	private final String[] access = I(new String[] { "PUZZLE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * PUZZLE CHECK's report.
	 * @param A the area, or null for the world
	 * @return the report
	 */
	public static String report(final Area A)
	{
		final List<String> problems = (A == null) ? PuzzleCheck.checkAll() : PuzzleCheck.checkArea(A);
		final StringBuilder str = new StringBuilder("PUZZLE CHECK " + ((A == null) ? "the world" : A.Name()) + ": " + problems.size() + " problems.");
		for(final String p : problems)
			str.append("\n\r  ").append(p);
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String verb=commands.size()<2 ? "LIST" : commands.get(1).toUpperCase();
		if(verb.equals("SIGNAL"))
		{
			if(commands.size()<4) { mob.tell(L("PUZZLE SIGNAL [ROOM|AREA|WORLD|TARGET] [NAME] ([ARGUMENTS])")); return false; }
			final List<PhysicalAgent> hosts=ScriptEvents.signalHosts(commands.get(2),mob,mob);
			ScriptEvents.signal(hosts,commands.get(3),CMParms.combine(commands,4),mob,mob,0);
			mob.tell(L("Signal @x1 sent to @x2 hosts.",commands.get(3),""+hosts.size()));
			return false;
		}
		if(verb.equals("SET") || verb.equals("RESET"))
		{
			final int end=commands.size()-(verb.equals("SET")?1:0);
			if(end<3) { mob.tell(L("PUZZLE SET [TARGET] [STATE], or PUZZLE RESET [TARGET]")); return false; }
			final Environmental target=Scenery.target(mob,CMParms.combine(commands.subList(2,end),0));
			final Ability effect=target instanceof Physical ? ((Physical)target).fetchEffect("Prop_Switch") : null;
			if(!(effect instanceof Prop_Switch)) { mob.tell(L("That target has no switch state.")); return false; }
			final Prop_Switch change=(Prop_Switch)effect;
			if(verb.equals("RESET")) change.reset(mob);
			else
			{
				final String state=commands.get(commands.size()-1).toLowerCase();
				final String states=CMParms.getParmStr(CMParms.parseExactParms(change.text()),"STATES","down,up");
				if(!Prop_Switch.statesOf(states).contains(state)) { mob.tell(L("Unknown switch state: @x1",state)); return false; }
				change.moveTo(mob,state,"set");
			}
			mob.tell(L("@x1 state: @x2",target.Name(),change.state()));
			return false;
		}
		if(verb.equals("LIST"))
		{
			final Area area=commands.size()>2 ? CMLib.map().getArea(CMParms.combine(commands,2)) : mob.location().getArea();
			if(area==null) { mob.tell(L("No such area.")); return false; }
			final Set<Physical> seen=Collections.newSetFromMap(new IdentityHashMap<Physical,Boolean>());
			final StringBuilder text=new StringBuilder("Puzzle wiring in "+area.Name()+":");
			seen.add(area);
			for(final Enumeration<Room> r=area.getProperMap();r.hasMoreElements();)
			{
				final Room room=r.nextElement();seen.add(room);
				for(int d=0;d<Directions.NUM_DIRECTIONS();d++)if(room.getExitInDir(d)!=null)seen.add(room.getExitInDir(d));
				for(final Enumeration<Item> e=room.items();e.hasMoreElements();)seen.add(e.nextElement());
				for(final Enumeration<MOB> e=room.inhabitants();e.hasMoreElements();)
				{
					final MOB m=e.nextElement();seen.add(m);
					for(final Enumeration<Item> i=m.items();i.hasMoreElements();)seen.add(i.nextElement());
				}
			}
			final List<String> lines=new ArrayList<String>();
			for(final Physical host:seen)for(final Enumeration<Ability> e=host.effects();e.hasMoreElements();)
			{
				final Ability property=e.nextElement();
				if(property.ID().equals("Prop_Switch") || property.ID().equals("Prop_PressurePlate") || property.ID().equals("Prop_BlockedExit"))
					lines.add(host.Name()+" @ "+CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(host))+": "+property.ID()+" "+property.text());
			}
			for(final Physical host:seen)if(host instanceof PhysicalAgent)
			{
				for(final com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine script:ScriptEvents.engines((PhysicalAgent)host))
					for(final String line:ScriptCheck.withLoads(script.getScript()).split("\r?\n"))
						if(line.trim().matches("(?i)(SIGNAL|SWITCH|PRESSURE)_PROG.*"))lines.add(host.Name()+": "+line.trim());
			}
			Collections.sort(lines);for(final String line:lines)text.append("\n\r").append(line);
			mob.tell(text.toString());return false;
		}
		if(!verb.equals("CHECK"))
		{
			mob.tell(L("PUZZLE LIST ([AREA]), CHECK (AREA [NAME]), SET [TARGET] [STATE], RESET [TARGET], SIGNAL [SCOPE] [NAME] ([ARGS])"));
			return false;
		}
		Area A = null;
		if((commands.size() > 2) && commands.get(2).equalsIgnoreCase("AREA"))
		{
			final String name = CMParms.combine(commands, 3);
			A = (name.length() == 0) ? ((mob.location() == null) ? null : mob.location().getArea()) : CMLib.map().getArea(name);
			if(A == null)
			{
				mob.tell(L("There is no area '@x1'.", name));
				return false;
			}
		}
		mob.tell(report(A));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
