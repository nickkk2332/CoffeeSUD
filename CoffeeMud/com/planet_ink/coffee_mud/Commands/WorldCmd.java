package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.save.WorldSettings;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * WORLD shows the world settings in effect (as LIST WORLDINI does), and in
 * builder mode WORLD SET KEY=VALUE writes one to the world's world.ini.
 */
public class WorldCmd extends StdCommand
{
	public WorldCmd()
	{
	}

	private final String[] access=I(new String[]{"WORLD"});

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Returns the world settings in effect: each key a world may set that is
	 * set, with its value and where it came from, then those not set.
	 * @return the listing
	 */
	public static String listing()
	{
		final StringBuilder str=new StringBuilder(CMLib.lang().L("World settings, which a world sets in @x1:\n\r", WorldSettings.FILE));
		final List<String> notSet=new ArrayList<String>();
		final Set<String> shown=new HashSet<String>();
		final List<String> keys=new ArrayList<String>();
		for(final String key : WorldSettings.keys())
		{
			if(!key.endsWith("*"))
				keys.add(key);
		}
		// the prefixed ones that are set, such as START_DWARF
		final List<String> prefixed=new ArrayList<String>();
		for(final Map.Entry<String,String> e : WorldSettings.worldValues().entrySet())
		{
			if(!keys.contains(e.getKey()))
				prefixed.add(e.getKey());
		}
		Collections.sort(prefixed);
		keys.addAll(prefixed);
		for(final String key : keys)
		{
			if(!shown.add(key))
				continue;
			final String value=CMProps.instance().getStr(key, "");
			final String from=WorldSettings.sourceOf(key);
			if((value.length()==0)&&(from.length()==0))
			{
				notSet.add(key);
				continue;
			}
			str.append("  ").append(CMStrings.padRight(key, 20)).append(" = ").append(value);
			if(from.length()>0)
				str.append("  (").append(from).append(")");
			str.append("\n\r");
		}
		if(notSet.size()>0)
			str.append(CMLib.lang().L("Not set: @x1\n\r", CMParms.toListString(notSet.iterator())));
		str.append(CMLib.lang().L("Also keys starting @x1.\n\r", CMParms.toListString(prefixesOf(WorldSettings.keys()).iterator())));
		str.append(CMLib.lang().L("Each takes effect when the game starts.  In builder mode, WORLD SET KEY=VALUE changes one in @x1.\n\r", WorldSettings.FILE));
		return str.toString();
	}

	private static List<String> prefixesOf(final List<String> keys)
	{
		final List<String> prefixes=new ArrayList<String>();
		for(final String key : keys)
		{
			if(key.endsWith("*"))
				prefixes.add(key.substring(0,key.length()-1));
		}
		return prefixes;
	}

	/**
	 * WORLD CHECK (AREA name) (LOG) (ONLY=part,part): the world's lint, by
	 * severity ({@link WorldCheck}).
	 * @param mob the one asking
	 * @param words the words after CHECK
	 */
	protected void worldCheck(final MOB mob, final List<String> words)
	{
		Area only=null;
		boolean log=false;
		final Set<String> parts=new TreeSet<String>();
		for(int i=0;i<words.size();i++)
		{
			final String w=words.get(i);
			if(w.equalsIgnoreCase("LOG"))
				log=true;
			else
			if(w.toUpperCase().startsWith("ONLY="))
			{
				for(final String p : w.substring(5).split(","))
				{
					final String part=p.trim().toUpperCase();
					if(!Arrays.asList(WorldCheck.PARTS).contains(part))
					{
						mob.tell(L("'@x1' is not a part of WORLD CHECK: @x2.",p,String.join(", ",WorldCheck.PARTS).toLowerCase()));
						return;
					}
					parts.add(part);
				}
			}
			else
			if(w.equalsIgnoreCase("AREA"))
			{
				final String name=(i<words.size()-1)?words.get(++i):"";
				only=(name.length()>0)?CMLib.map().getArea(name):((mob.location()==null)?null:mob.location().getArea());
				if(only==null)
				{
					mob.tell(L("There is no area '@x1'.",name));
					return;
				}
			}
			else
			{
				mob.tell(L("WORLD CHECK (AREA [NAME]) (LOG) (ONLY=@x1)",String.join(",",WorldCheck.PARTS).toLowerCase()));
				return;
			}
		}
		final List<String> lines=WorldCheck.report(WorldCheck.run(only,parts));
		if(log)
		{
			for(final String line : lines)
				Log.sysOut("WorldCheck",line);
		}
		mob.tell(String.join("\n\r",lines));
	}

	/**
	 * WORLD DEV (DT27): the dev areas, or, in builder mode, WORLD DEV area
	 * ON|OFF makes an area one (tagged DEV, written to world/dev on SAVE,
	 * loaded only in builder mode and DEV=1 games) or a shipped one again.
	 * @param mob the builder
	 * @param words the words after DEV
	 */
	protected void worldDev(final MOB mob, final List<String> words)
	{
		final String last = words.isEmpty() ? "" : words.get(words.size() - 1).toUpperCase();
		if(words.isEmpty() || ((!last.equals("ON")) && (!last.equals("OFF"))))
		{
			final StringBuilder str = new StringBuilder(L("Dev areas (world/dev, loaded only in builder mode and DEV=1):"));
			int n = 0;
			for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			{
				final Area A = a.nextElement();
				if(com.planet_ink.coffee_mud.core.save.WorldFiles.isDevArea(A))
				{
					str.append("\n\r  ").append(A.Name()).append(" (").append(A.numberOfProperIDedRooms()).append(" rooms)");
					n++;
				}
			}
			if(n == 0)
				str.append(L(" none."));
			str.append("\n\r").append(L("WORLD DEV [AREA] ON/OFF makes an area one, or a shipped one again, in builder mode."));
			mob.tell(str.toString());
			return;
		}
		if(!SaveGame.isBuilderMode())
		{
			mob.tell(L("WORLD DEV ON and OFF move an area's file, so they are for builder mode."));
			return;
		}
		final String name = CMParms.combine(words.subList(0, words.size() - 1), 0);
		final Area A = (name.length() == 0) ? mob.location().getArea() : CMLib.map().getArea(name);
		if(A == null)
		{
			mob.tell(L("There is no area '@x1'.", name));
			return;
		}
		if(last.equals("ON"))
			A.addTag(com.planet_ink.coffee_mud.core.save.WorldFiles.DEV_TAG);
		else
			A.delTag(com.planet_ink.coffee_mud.core.save.WorldFiles.DEV_TAG);
		CMLib.database().DBUpdateArea(A.Name(), A);
		mob.tell(last.equals("ON")
			? L("@x1 is a dev area: SAVE writes it to world/dev, and normal games leave it out.", A.Name())
			: L("@x1 is a shipped area again: SAVE writes it to world/areas.", A.Name()));
	}

	/**
	 * WORLD STATUS (B2): the world files changed outside the game, and what
	 * the next SAVE would write, delete and leave.
	 * @return the report
	 */
	public static String worldStatus()
	{
		if(!SaveGame.isBuilderMode())
			return "WORLD STATUS is for builder mode, where SAVE writes the world files.";
		final StringBuilder str = new StringBuilder();
		final Map<String, String> outside = WorldFiles.changedOutside();
		if(outside.isEmpty())
			str.append("No world file was changed outside the game since it was loaded or last written.");
		else
		{
			str.append("Changed outside the game since it was loaded or last written:");
			for(final Map.Entry<String, String> e : outside.entrySet())
				str.append("\n\r  ").append(e.getKey()).append(" (").append(e.getValue()).append(")");
		}
		final WorldFiles.ExportPlan plan = WorldFiles.plan();
		if(plan == null)
			return str.append("\n\rSAVE writes nothing, as the world did not load whole from its files (WORLD PROBLEMS).").toString();
		if(plan.writes.isEmpty() && plan.deletes.isEmpty() && plan.kept.isEmpty() && plan.heldBack.isEmpty())
			return str.append("\n\rSAVE would change no world file.").toString();
		if(plan.writes.size() > 0)
			str.append("\n\rSAVE would write: ").append(String.join(", ", plan.writes)).append(".");
		if(plan.deletes.size() > 0)
			str.append("\n\rSAVE would delete: ").append(String.join(", ", plan.deletes)).append(".");
		if(plan.kept.size() > 0)
			str.append("\n\rSAVE would leave, as they were changed outside the game: ").append(String.join(", ", plan.kept))
			   .append(".  What it would write goes beside each, as ").append(WorldFiles.BUILDER_EXT)
			   .append("; WORLD DIFF shows the difference, and SAVE FORCE writes over them.");
		if(plan.heldBack.size() > 0)
			str.append("\n\rSAVE would leave, as they loaded only in part: ").append(String.join(", ", plan.heldBack)).append(" (WORLD PROBLEMS).");
		return str.toString();
	}

	/**
	 * WORLD DIFF file (B2): a world file on disk against what the next SAVE
	 * would write to it.
	 * @param what the file, as the builder calls it
	 * @return the difference
	 */
	public static String worldDiff(final String what)
	{
		if(!SaveGame.isBuilderMode())
			return "WORLD DIFF is for builder mode, where SAVE writes the world files.";
		if(what.length() == 0)
			return "WORLD DIFF [FILE]: a world file, such as castle, areas/castle.cmare or quests/hunt.quest, against what SAVE would write.";
		final WorldFiles.ExportPlan plan = WorldFiles.plan();
		if(plan == null)
			return "SAVE writes nothing, as the world did not load whole from its files (WORLD PROBLEMS).";
		final String path = WorldFiles.findPath(what, plan);
		if(path == null)
			return "There is no world file '" + what + "'.";
		final java.io.File F = new java.io.File(WorldFiles.worldDir(), path);
		String disk = null;
		if(F.isFile())
		{
			try
			{
				disk = new String(java.nio.file.Files.readAllBytes(F.toPath()), java.nio.charset.StandardCharsets.UTF_8);
			}
			catch(final java.io.IOException e)
			{
				return path + ": " + e.getMessage();
			}
		}
		final String game = plan.texts.get(path);
		final boolean kept = plan.kept.contains(path);
		final String why = kept ? " (changed outside the game, so SAVE leaves it and puts this beside it, as " + path + WorldFiles.BUILDER_EXT + ")" : "";
		if((game == null) && (!plan.deletes.contains(path)) && (!kept))
			return path + ": SAVE would leave it as it is" + (plan.heldBack.contains(path) ? ", as it loaded only in part." : "; it is as the game has it.");
		if(game == null)
			return path + why + ": SAVE would delete it" + ((disk == null) ? ", but it is gone already." : (", all " + lines(disk).length + " lines."));
		if(disk == null)
			return path + why + ": not on disk; SAVE would write it, " + lines(game).length + " lines.";
		final List<String> diff = lineDiff(disk, game, 200);
		if(diff.isEmpty())
			return path + ": SAVE would write it as it is.";
		return path + why + ", - on disk, + as SAVE would write it:\n\r" + String.join("\n\r", diff);
	}

	private static String[] lines(final String text)
	{
		return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
	}

	private static String shown(final String line)
	{
		return (line.length() > 160) ? (line.substring(0, 157) + "...") : line;
	}

	/**
	 * A line diff (B2): each run of changed lines with the line it starts at,
	 * the old lines marked - and the new +.
	 * @param before the old text
	 * @param after the new text
	 * @param max the most lines to give
	 * @return the lines, none if the texts are the same
	 */
	public static List<String> lineDiff(final String before, final String after, final int max)
	{
		final String[] a = lines(before);
		final String[] b = lines(after);
		int pre = 0;
		while((pre < a.length) && (pre < b.length) && a[pre].equals(b[pre]))
			pre++;
		int suf = 0;
		while((suf < a.length - pre) && (suf < b.length - pre) && a[a.length - 1 - suf].equals(b[b.length - 1 - suf]))
			suf++;
		final int n = a.length - pre - suf;
		final int m = b.length - pre - suf;
		// each step: 0 the same, 1 an old line gone, 2 a new line; then its old and new line
		final List<int[]> ops = new ArrayList<int[]>();
		if(((long)(n + 1) * (long)(m + 1)) <= 4000000L)
		{
			final int[][] L = new int[n + 1][m + 1];
			for(int i = n - 1; i >= 0; i--)
			{
				for(int j = m - 1; j >= 0; j--)
					L[i][j] = a[pre + i].equals(b[pre + j]) ? (L[i + 1][j + 1] + 1) : Math.max(L[i + 1][j], L[i][j + 1]);
			}
			int i = 0, j = 0;
			while((i < n) || (j < m))
			{
				if((i < n) && (j < m) && a[pre + i].equals(b[pre + j]))
				{
					ops.add(new int[] { 0, i, j });
					i++;
					j++;
				}
				else
				if((i < n) && ((j == m) || (L[i + 1][j] >= L[i][j + 1])))
					ops.add(new int[] { 1, i++, j });
				else
					ops.add(new int[] { 2, i, j++ });
			}
		}
		else
		{
			// too long to match up: the middle, all of it, as changed
			for(int i = 0; i < n; i++)
				ops.add(new int[] { 1, i, 0 });
			for(int j = 0; j < m; j++)
				ops.add(new int[] { 2, n, j });
		}
		final List<String> out = new ArrayList<String>();
		int gone = 0, added = 0;
		boolean inRun = false;
		for(final int[] op : ops)
		{
			if(op[0] == 0)
			{
				inRun = false;
				continue;
			}
			if(op[0] == 1)
				gone++;
			else
				added++;
			if(out.size() >= max)
				continue;
			if(!inRun)
			{
				out.add("line " + (pre + op[1] + 1) + ":");
				inRun = true;
			}
			out.add((op[0] == 1) ? ("- " + shown(a[pre + op[1]])) : ("+ " + shown(b[pre + op[2]])));
		}
		if(out.size() >= max)
			out.add("... and more: " + gone + " lines gone and " + added + " new in all.");
		return out;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size()<2)||(commands.get(1).equalsIgnoreCase("LIST")))
		{
			mob.tell(listing());
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("PROBLEMS"))
		{
			final Map<String,List<String>> problems=com.planet_ink.coffee_mud.core.save.WorldFiles.problems();
			if(problems.isEmpty())
			{
				mob.tell(L("The world files loaded whole."));
				return false;
			}
			final StringBuilder str=new StringBuilder(L("These world files loaded only in part, so builder mode does not write them until they are fixed (and the world is loaded again), or SAVE FORCE writes them as the world has them:\n\r"));
			for(final String file : problems.keySet())
			{
				str.append(file).append(":\n\r");
				for(final String line : problems.get(file))
					str.append("  ").append(line).append("\n\r");
			}
			mob.tell(str.toString());
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("CHECK"))
		{
			if((!SaveGame.isBuilderMode()) && (!SaveGame.isDevMode()) && (!CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS)))
			{
				mob.tell(L("WORLD CHECK is for builder mode or a DEV=1 game."));
				return false;
			}
			worldCheck(mob, commands.subList(2, commands.size()));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("STATUS"))
		{
			mob.tell(worldStatus());
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("DIFF"))
		{
			mob.tell(worldDiff(CMParms.combine(commands, 2).trim()));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("DEV"))
		{
			worldDev(mob, commands.subList(2, commands.size()));
			return false;
		}
		if(!commands.get(1).equalsIgnoreCase("SET"))
		{
			mob.tell(L("WORLD shows the world settings, WORLD PROBLEMS what did not load from the world files, WORLD CHECK what is wrong in the world, WORLD STATUS what SAVE would write and what was changed outside the game, WORLD DIFF a file against what SAVE would write, WORLD DEV the areas for building and testing only, and WORLD SET KEY=VALUE changes a setting in builder mode."));
			return false;
		}
		if(!SaveGame.isBuilderMode())
		{
			mob.tell(L("WORLD SET changes the world's own files, so it is for builder mode: start CoffeeSUD with BUILDER=1."));
			return false;
		}
		if(!CMSecurity.isASysOp(mob))
		{
			mob.tell(L("You are not allowed to do that."));
			return false;
		}
		final String rest=CMParms.combine(commands,2).trim();
		final int x=rest.indexOf('=');
		if(x<=0)
		{
			mob.tell(L("WORLD SET KEY=VALUE, such as WORLD SET START=Emberfall#0.  A blank value takes the key out."));
			return false;
		}
		final String key=rest.substring(0,x).trim().toUpperCase();
		final String value=rest.substring(x+1).trim();
		if(!WorldSettings.isWorldKey(key))
		{
			mob.tell(L("@x1 is not a world setting: those are listed by WORLD, and the engine's own settings stay in coffeemud.ini.", key));
			return false;
		}
		WorldSettings.set(WorldFiles.worldDir(), key, value);
		if(value.length()==0)
			mob.tell(L("@x1 is taken out of @x2.  It takes effect the next time the game starts.", key, WorldSettings.FILE));
		else
			mob.tell(L("@x1=@x2 is set in @x3.  It takes effect the next time the game starts.", key, value, WorldSettings.FILE));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
