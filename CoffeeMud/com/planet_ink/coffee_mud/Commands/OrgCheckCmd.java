package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * DT37, in builder mode: ORGCHECK [faction file|CLANS|GOVERNMENTS|ALL] checks
 * faction files, the world's clan references and the governments' entry
 * scripts ({@link OrgCheck}).
 */
public class OrgCheckCmd extends StdCommand
{
	public OrgCheckCmd()
	{
	}

	private final String[] access = I(new String[] { "ORGCHECK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * The report.
	 * @param what a faction file, CLANS, GOVERNMENTS or ALL
	 * @return the report
	 */
	public static String report(final String what)
	{
		final List<String> problems;
		final String which;
		if(what.equalsIgnoreCase("ALL") || (what.length() == 0))
		{
			problems = OrgCheck.checkAll();
			which = "the factions, clans and governments";
		}
		else
		if(what.equalsIgnoreCase("CLANS"))
		{
			problems = OrgCheck.checkWorldClans();
			which = "the world's clans";
		}
		else
		if(what.equalsIgnoreCase("GOVERNMENTS"))
		{
			problems = OrgCheck.checkGovernments();
			which = "the governments";
		}
		else
		{
			File F = null;
			for(final File G : OrgCheck.factionFiles())
			{
				if(G.getName().equalsIgnoreCase(what) || G.getName().equalsIgnoreCase(what + ".ini"))
					F = G;
			}
			if(F == null)
			{
				final File G = new File(what.startsWith("/") ? what.substring(1) : what);
				if(G.isFile())
					F = G;
			}
			if(F == null)
				return "There is no faction file '" + what + "'.";
			try
			{
				problems = OrgCheck.checkFaction(F.getName(), new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8));
			}
			catch(final java.io.IOException e)
			{
				return F.getPath() + ": " + e.getMessage();
			}
			which = F.getName();
		}
		final StringBuilder str = new StringBuilder("ORGCHECK " + which + ": " + problems.size() + " problems.");
		for(final String p : problems)
			str.append("\n\r  ").append(p);
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		mob.tell(report(CMParms.combine(commands, 1).trim()));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDFACTIONS);
	}
}
