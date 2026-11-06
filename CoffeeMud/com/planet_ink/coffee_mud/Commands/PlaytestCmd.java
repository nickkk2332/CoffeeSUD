package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.Properties.Prop_Playtest;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
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
 * PLAYTEST [ON|OFF], in builder mode: the builder meets the world as a mortal.
 * While on, every sysop power is suspended (so aggressive mobs attack,
 * followers follow, dopplegangers scale and spawns spawn as for a player),
 * the prompt says (playtest), and a death takes the builder back, whole, to
 * where the playtest began ({@link Prop_Playtest}).  BUILD command runs a
 * command with the powers back ({@link BuildCmd}).  OFF makes the builder whole
 * and at peace.
 */
public class PlaytestCmd extends StdCommand
{
	public PlaytestCmd()
	{
	}

	private final String[] access = I(new String[] { "PLAYTEST" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Ability P = mob.fetchEffect("Prop_Playtest");
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		if(what.equals("RUN")) { mob.tell(PlaytestRunner.start(mob, CMParms.combine(commands, 2))); return false; }
		if(what.equals("GAME"))
		{
			// a reason it cannot start (no builder database, a refused script, a world not wholly loaded) is told, not shown as a game bug
			try { mob.tell(IsolatedPlaytest.launch(mob)); }
			catch(final java.io.IOException e) { mob.tell(L("PLAYTEST GAME could not start: @x1",""+e.getMessage())); }
			return false;
		}
		final boolean on = what.equals("ON") || (what.length() == 0 && (P == null));
		if((!what.equals("ON")) && (!what.equals("OFF")) && (what.length() > 0))
		{
			mob.tell(L("PLAYTEST ON (PARTY=names) (KIT=name) (DIFFICULTY=preset), OFF, GAME, or RUN [FILE]."));
			return false;
		}
		if(on)
		{
			if(P != null)
			{
				mob.tell(L("You are playtesting already.  PLAYTEST OFF ends it."));
				return false;
			}
			for(int i=2;i<commands.size();i++)if(commands.get(i).indexOf('=')<1)
			{ mob.tell(L("Use PARTY=names, KIT=name or DIFFICULTY=preset.")); return false; }
			final Map<String,String> opts = CMParms.parseEQParms(CMParms.combine(commands, 2));
			for(final String key : opts.keySet())
				if(!Arrays.asList("PARTY", "DIFFICULTY", "KIT").contains(key.toUpperCase()))
				{ mob.tell(L("Unknown PLAYTEST option: @x1", key)); return false; }
			final String difficulty = opts.get("DIFFICULTY");
			if(difficulty != null && GameDifficulty.find(difficulty) == null)
			{ mob.tell(L("Unknown difficulty: @x1", difficulty)); return false; }
			final String kitName = opts.get("KIT");
			final KitCmd.Kit kit = kitName == null ? null : KitCmd.readKit(kitName);
			if(kitName != null && (kit == null || !kit.problems.isEmpty()))
			{ mob.tell(L("Missing or invalid kit: @x1", kitName)); return false; }
			final List<String> names = CMParms.parseCommas(opts.containsKey("PARTY") ? opts.get("PARTY") : "", true);
			for(final String name : names)
			{
				final MOB member = CMLib.players().getLoadPlayer(name);
				if(member == null || Party.profileOf(member) != Party.profileOf(mob))
				{ mob.tell(L("No character in this profile: @x1", name)); return false; }
			}
			for(final String name : names)
				CMClass.getCommand("PartyCmd").execute(mob, Arrays.asList("PARTY", "ADD", name), metaFlags);
			if(difficulty != null) GameDifficulty.set(GameDifficulty.find(difficulty), mob.location());
			if(kit != null)
			{
				final List<String> problems = new ArrayList<String>();
				KitCmd.applyMaking(mob, kit, false, problems);
				KitCmd.applyGear(mob, kit, false, problems);
				for(final String problem : problems) mob.tell(problem);
			}
			for(final MOB member : Party.inOrder(Party.leaderOf(mob)))
			{
				if(member == mob || member.fetchEffect("Prop_Playtest") != null) continue;
				final Ability test = CMClass.getAbility("Prop_Playtest");
				test.setMiscText(CMLib.map().getExtendedRoomID(member.location()));
				member.addNonUninvokableEffect(test);
			}
			final Room R = mob.location();
			final Ability A = CMClass.getAbility("Prop_Playtest");
			A.setMiscText((R == null) ? "" : CMLib.map().getExtendedRoomID(R));
			mob.addNonUninvokableEffect(A);
			mob.recoverPhyStats();
			mob.tell(L("Playtest: the world now treats you as a mortal.  A death brings you back here.  "
					+ "BUILD [COMMAND] runs a command with your powers; PLAYTEST OFF ends it. This mode keeps changes; PLAYTEST GAME isolates and discards them."));
			return false;
		}
		if(P == null)
		{
			mob.tell(L("You are not playtesting."));
			return false;
		}
		for(final MOB member : Party.inOrder(Party.leaderOf(mob)))
		{
			final Ability test = member.fetchEffect("Prop_Playtest");
			if(test != null) member.delEffect(test);
			Prop_Playtest.makeWhole(member);
		}
		mob.delEffect(P);
		Prop_Playtest.makeWhole(mob);
		mob.tell(L("The playtest is over: your powers are back, and you are whole and at peace."));
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
		return SaveGame.isBuilderMode();
	}
}
