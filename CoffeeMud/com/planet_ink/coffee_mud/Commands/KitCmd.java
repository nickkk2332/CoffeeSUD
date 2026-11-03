package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * KIT name [target] (DT16): applies a kit, a loadout kept with the world as
 * world/kits/NAME.kit, to a character or a follower here (yourself by
 * default).  TESTCHAR makes a profile character from one.  A kit's lines,
 * with # for comments:
 * <pre>
 * race Dwarf          class Fighter        gender F        alignment good
 * level 12            gold 500             room Crypt#1
 * item Longsword      item catalog:Mithril Axe      (worn if it can be)
 * ability Skill_Dodge 75                   (with its proficiency)
 * faction Merchants 2500                   var ACT 3   (a global script variable of the character)
 * </pre>
 */
public class KitCmd extends StdCommand
{
	public KitCmd()
	{
	}

	private final String[] access = I(new String[] { "KIT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** a kit, as read */
	public static final class Kit
	{
		/** its name */
		public String						name;
		/** each setting: word and value, in order */
		public final List<String[]>			lines		= new ArrayList<String[]>();
		/** what could not be read */
		public final List<String>			problems	= new ArrayList<String>();

		/**
		 * The value of a setting given once (race, class, level...).
		 * @param word the setting
		 * @return its last value, or null
		 */
		public String get(final String word)
		{
			String v = null;
			for(final String[] l : lines)
			{
				if(l[0].equals(word))
					v = l[1];
			}
			return v;
		}
	}

	private static final Set<String> WORDS = new HashSet<String>(Arrays.asList(new String[] {
		"RACE", "CLASS", "GENDER", "ALIGNMENT", "LEVEL", "GOLD", "ROOM", "ITEM", "ABILITY", "FACTION", "VAR",
		"STARTING", "CHOOSE", "TITLE", "WEAR" })); // P7: a starting kit's

	/**
	 * The file a kit is kept in.
	 * @param name the kit
	 * @return world/kits/NAME.kit, or null without a world folder
	 */
	public static File kitFile(final String name)
	{
		final File world = WorldFiles.worldDir();
		return (world == null) ? null : new File(new File(world, "kits"), name.trim() + ".kit");
	}

	/**
	 * Reads a kit.
	 * @param name its name
	 * @return the kit, or null if there is no such file
	 */
	public static Kit readKit(final String name)
	{
		final File F = kitFile(name);
		if((F == null) || (!F.isFile()))
			return null;
		final Kit K = new Kit();
		K.name = name;
		try
		{
			int n = 0;
			for(final String raw : new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8).split("\\r?\\n"))
			{
				n++;
				final String line = raw.trim();
				if((line.length() == 0) || line.startsWith("#"))
					continue;
				final int x = line.indexOf(' ');
				final String word = ((x < 0) ? line : line.substring(0, x)).toUpperCase();
				final String value = (x < 0) ? "" : line.substring(x + 1).trim();
				if(!WORDS.contains(word))
					K.problems.add(name + ".kit:" + n + ": '" + word.toLowerCase() + "' is not a kit setting");
				else
				if(value.length() == 0)
					K.problems.add(name + ".kit:" + n + ": " + word.toLowerCase() + " of nothing");
				else
					K.lines.add(new String[] { word, value, "" + n });
			}
		}
		catch(final java.io.IOException e)
		{
			K.problems.add(name + ".kit: " + e.getMessage());
		}
		return K;
	}


	/**
	 * Gives a character its kit's race, class, gender and alignment, as a new
	 * character starts with them, before its level.
	 * @param M the character
	 * @param K the kit
	 * @param fresh true for a character being made, false for one in play
	 * @param said what was done and what could not be, added to
	 */
	public static void applyMaking(final MOB M, final Kit K, final boolean fresh, final List<String> said)
	{
		final String race = K.get("RACE");
		if(race != null)
		{
			Race R = CMClass.getRace(race);
			if(R == null)
				R = CMClass.findRace(race);
			if(R == null)
				said.add("no race '" + race + "'");
			else
			{
				M.baseCharStats().setMyRace(R);
				if(fresh)
					R.startRacing(M, false);
			}
		}
		final String gender = K.get("GENDER");
		if(gender != null)
			M.baseCharStats().setStat(CharStats.STAT_GENDER, Character.toUpperCase(gender.charAt(0)));
		final String align = K.get("ALIGNMENT");
		if(align != null)
		{
			final Faction.Align A = (Faction.Align)CMath.s_valueOf(Faction.Align.class, align.toUpperCase());
			if(A == null)
				said.add("no alignment '" + align + "'");
			else
				CMLib.factions().setAlignment(M, A);
		}
		final String cls = K.get("CLASS");
		if(cls != null)
		{
			CharClass C = CMClass.getCharClass(cls);
			if(C == null)
				C = CMClass.findCharClass(cls);
			if(C == null)
				said.add("no class '" + cls + "'");
			else
			{
				M.baseCharStats().setCurrentClass(C);
				if(M.baseCharStats().getClassLevel(C) < 1)
					M.baseCharStats().setClassLevel(C, 1);
				if(fresh)
					C.startCharacter(M, false, false);
			}
		}
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
	}

	/**
	 * Levels a character up to a level, as CHARGEN does: by the experience
	 * each level needs.
	 * @param M the character
	 * @param level the level
	 */
	public static void levelTo(final MOB M, final int level)
	{
		int guard = 0;
		while((M.basePhyStats().level() < level) && (guard++ < 1000))
		{
			if(M.getExpNeededLevel() == Integer.MAX_VALUE)
				CMLib.leveler().level(M);
			else
				CMLib.leveler().postExperience(M, "COMMAND:KIT", null, null, M.getExpNeededLevel() + 1, true);
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
		}
	}

	/**
	 * Gives a character the rest of its kit: level, items, gold, abilities,
	 * factions, variables and room.
	 * @param M the character
	 * @param K the kit
	 * @param fresh true for a character being made (the room is its start room)
	 * @param said what was done and what could not be, added to
	 */
	public static void applyGear(final MOB M, final Kit K, final boolean fresh, final List<String> said)
	{
		final String level = K.get("LEVEL");
		if(level != null)
		{
			if(!CMath.isInteger(level))
				said.add("level '" + level + "' is no number");
			else
				levelTo(M, CMath.s_int(level));
		}
		for(final String[] l : K.lines)
		{
			final String word = l[0];
			final String v = l[1];
			if(word.equals("ITEM"))
			{
				// P7: a count may come first; a plain catalog name is found too; WEAR NO keeps them off
				int count = 1;
				String spec = v;
				final int sp = v.indexOf(' ');
				if((sp > 0) && CMath.isInteger(v.substring(0, sp)))
				{
					count = Math.max(1, Math.min(100, CMath.s_int(v.substring(0, sp))));
					spec = v.substring(sp + 1).trim();
				}
				final String wear = K.get("WEAR");
				for(int c = 0; c < count; c++)
				{
					final Item I = StartingKits.item(spec);
					if(I == null)
					{
						said.add("no item '" + spec + "'");
						break;
					}
					M.addItem(I);
					if((wear == null) || (!wear.toUpperCase().startsWith("N")))
						I.wearIfPossible(M);
				}
			}
			else
			if(word.equals("GOLD"))
			{
				if(CMath.isNumber(v))
					CMLib.beanCounter().setMoney(M, CMath.s_double(v));
				else
					said.add("gold '" + v + "' is no number");
			}
			else
			if(word.equals("ABILITY"))
			{
				final String[] p = v.split("\\s+");
				final Ability A = CMClass.getAbility(p[0]);
				if(A == null)
					said.add("no ability '" + p[0] + "'");
				else
				{
					Ability mine = M.fetchAbility(A.ID());
					if(mine == null)
					{
						mine = (Ability)A.copyOf();
						M.addAbility(mine);
					}
					mine.setProficiency((p.length > 1) && CMath.isInteger(p[1]) ? CMath.s_int(p[1]) : 100);
					mine.autoInvocation(M, false);
				}
			}
			else
			if(word.equals("FACTION"))
			{
				final String[] p = v.split("\\s+");
				final Faction F = CMLib.factions().getFaction(p[0]);
				if((F == null) || (p.length < 2) || (!CMath.isInteger(p[1])))
					said.add("faction '" + v + "': " + ((F == null) ? "no such faction" : "no value"));
				else
					M.addFaction(F.factionID(), CMath.s_int(p[1]));
			}
			else
			if(word.equals("VAR"))
			{
				final int x = v.indexOf(' ');
				if(x < 0)
					said.add("var '" + v + "' has no value");
				else
				{
					final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
					S.setVar(M.Name(), v.substring(0, x).trim().toUpperCase(), v.substring(x + 1).trim());
				}
			}
		}
		final String room = K.get("ROOM");
		if(room != null)
		{
			final Room R = CMLib.map().getRoom(room);
			if(R == null)
				said.add("no room '" + room + "'");
			else
			if(fresh)
			{
				M.setStartRoom(R);
				M.setLocation(R);
			}
			else
				R.bringMobHere(M, false);
		}
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
		M.resetToMaxState();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("KIT [NAME] ([CHARACTER OR FOLLOWER]): kits are world/kits/NAME.kit."));
			return false;
		}
		final Kit K = readKit(commands.get(1));
		if(K == null)
		{
			mob.tell(L("There is no kit '@x1' (world/kits/@x1.kit).", commands.get(1)));
			return false;
		}
		MOB M = mob;
		if(commands.size() > 2)
		{
			final String who = CMParms.combine(commands, 2);
			M = (mob.location() == null) ? null : mob.location().fetchInhabitant(who);
			if(M == null)
				M = Party.findInWorld(mob, who);
			if(M == null)
			{
				mob.tell(L("There is no '@x1' here.", who));
				return false;
			}
		}
		final List<String> said = new ArrayList<String>(K.problems);
		applyMaking(M, K, false, said);
		applyGear(M, K, false, said);
		mob.tell(L("@x1 has the kit @x2.", M.Name(), K.name) + (said.isEmpty() ? "" : ("  " + L("But: @x1.", String.join("; ", said)))));
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
