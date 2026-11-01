package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Commands.KitCmd.Kit;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * TESTCHAR name (KIT=kit) (PARTY) (DT16), in builder mode or a DEV=1 game:
 * makes a character of the profile with no questions, from a kit
 * ({@link KitCmd}): a human apprentice of level 1 with average stats unless
 * the kit says otherwise, levelled as CHARGEN levels, starting where you are
 * (or the kit's room).  PARTY brings them into the world, in the party.
 */
public class TestCharCmd extends StdCommand
{
	public TestCharCmd()
	{
	}

	private final String[] access = I(new String[] { "TESTCHAR" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Makes a character of a profile from a kit.
	 * @param acct the profile
	 * @param name the character's name
	 * @param K the kit, or null for none
	 * @param start where they start
	 * @param said what could not be done, added to
	 * @return the character, saved, not in the world
	 */
	public static MOB make(final PlayerAccount acct, final String name, final Kit K, final Room start, final List<String> said)
	{
		final MOB M = CMClass.getMOB("StdMOB");
		M.setName(name);
		final PlayerStats ps = (PlayerStats)CMClass.getCommon("DefaultPlayerStats");
		M.setPlayerStats(ps);
		ps.setAccount(acct);
		CMLib.factions().setAlignment(M, Faction.Align.NEUTRAL);
		M.baseCharStats().setMyRace(CMClass.getRace("Human"));
		M.baseCharStats().setStat(CharStats.STAT_GENDER, 'M');
		for(final int i : CharStats.CODES.BASECODES())
			M.baseCharStats().setStat(i, 10);
		CharClass C = CMClass.getCharClass("Apprentice");
		if(C == null)
			C = CMClass.getCharClass("StdCharClass");
		M.baseCharStats().setCurrentClass(C);
		M.baseCharStats().setClassLevel(C, 1);
		M.basePhyStats().setLevel(1);
		M.basePhyStats().setArmor(100);
		M.baseState().setHitPoints(20);
		M.baseState().setMovement(100);
		M.baseState().setMana(100);
		M.setStartRoom(start);
		M.setLocation(start);
		final Kit kit = (K != null) ? K : new Kit();
		final boolean ownRace = kit.get("RACE") != null;
		final boolean ownClass = kit.get("CLASS") != null;
		KitCmd.applyMaking(M, kit, true, said);
		if(!ownRace)
			M.baseCharStats().getMyRace().startRacing(M, false);
		if(!ownClass)
			M.baseCharStats().getCurrentClass().startCharacter(M, false, false);
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
		KitCmd.applyGear(M, kit, true, said);
		M.setDisplayText(CMLib.lang().L("@x1 is here.", name));
		CMLib.database().DBCreateCharacter(M);
		CMLib.players().addPlayer(M);
		if(acct != null)
		{
			acct.addNewPlayer(M);
			CMLib.database().DBUpdateAccount(acct);
		}
		CMLib.database().DBUpdatePlayer(M);
		return M;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("TESTCHAR [NAME] (KIT=[KIT]) (PARTY)"));
			return false;
		}
		final String name = CMStrings.capitalizeAndLower(commands.get(1));
		Kit K = null;
		boolean party = false;
		for(int i = 2; i < commands.size(); i++)
		{
			final String w = commands.get(i);
			if(w.toUpperCase().startsWith("KIT="))
			{
				K = KitCmd.readKit(w.substring(4));
				if(K == null)
				{
					mob.tell(L("There is no kit '@x1' (world/kits/@x1.kit).", w.substring(4)));
					return false;
				}
			}
			else
			if(w.equalsIgnoreCase("PARTY"))
				party = true;
			else
			{
				mob.tell(L("TESTCHAR [NAME] (KIT=[KIT]) (PARTY)"));
				return false;
			}
		}
		if((CMLib.players().getLoadPlayer(name) != null) || (CMLib.database().DBUserSearch(name) != null))
		{
			mob.tell(L("There is a character called @x1 already.", name));
			return false;
		}
		final PlayerAccount acct = Party.profileOf(mob);
		final List<String> said = new ArrayList<String>();
		if(K != null)
			said.addAll(K.problems);
		final MOB M = make(acct, name, K, mob.location(), said);
		final String but = said.isEmpty() ? "" : ("  " + L("But: @x1.", String.join("; ", said)));
		if(party)
		{
			mob.tell(L("@x1 is made, a level @x2 @x3 @x4.", name, "" + M.basePhyStats().level(),
					M.baseCharStats().getMyRace().name(), M.baseCharStats().getCurrentClass().name()) + but);
			// as PARTY ADD does: into the world and the party
			final Command C = CMClass.getCommand("PartyCmd");
			if(C != null)
				C.execute(mob, new XVector<String>("PARTY", "ADD", name), metaFlags);
		}
		else
			mob.tell(L("@x1 is made, a level @x2 @x3 @x4, resting in the profile (PARTY ADD @x1 brings them in).", name, "" + M.basePhyStats().level(),
					M.baseCharStats().getMyRace().name(), M.baseCharStats().getCurrentClass().name()) + but);
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode();
	}
}
