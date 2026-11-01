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
	 * WORLD MAP (B10, core/AreaMap): the rooms around the builder (RADIUS n,
	 * 5 if not given) or the whole area (AREA), numbered and listed with
	 * IDS, marked with FLAGS; HTML=file writes the area's map as a page to
	 * world/maps/.
	 * @param mob the builder
	 * @param words the words after MAP
	 * @return the map, or what was written
	 */
	public static String worldMap(final MOB mob, final List<String> words)
	{
		if((!SaveGame.isBuilderMode()) && (!SaveGame.isDevMode()) && (!CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS)))
			return "WORLD MAP is for builder mode or a DEV=1 game.";
		final Room here = mob.location();
		if((here == null) || (here.getArea() == null))
			return "You are nowhere to map.";
		boolean whole = false;
		boolean ids = false;
		boolean flags = false;
		int radius = 5;
		String html = null;
		for(int i = 0; i < words.size(); i++)
		{
			final String w = words.get(i).toUpperCase();
			if(w.equals("AREA"))
				whole = true;
			else
			if(w.equals("IDS"))
				ids = true;
			else
			if(w.equals("FLAGS"))
				flags = true;
			else
			if(w.equals("RADIUS") && (i + 1 < words.size()) && CMath.isInteger(words.get(i + 1)))
				radius = CMath.s_int(words.get(++i));
			else
			if(CMath.isInteger(w))
				radius = CMath.s_int(w);
			else
			if(w.startsWith("HTML="))
				html = words.get(i).substring(5).trim();
			else
				return "WORLD MAP (RADIUS [N]) (AREA) (IDS) (FLAGS) (HTML=[FILE]): '" + words.get(i) + "' is none of those.";
		}
		final Area A = here.getArea();
		if(html != null)
		{
			String name = new java.io.File(html).getName();
			if(name.length() == 0)
				name = A.Name();
			if(!name.toLowerCase().endsWith(".html"))
				name = name + ".html";
			final java.io.File dir = new java.io.File(WorldFiles.worldDir(), "maps");
			final java.io.File F = new java.io.File(dir, name);
			try
			{
				dir.mkdirs();
				java.nio.file.Files.write(F.toPath(), AreaMap.html(AreaMap.layout(here, A, -1), A).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
			catch(final java.io.IOException e)
			{
				return F.getPath() + ": " + e.getMessage();
			}
			return "The map of " + A.Name() + " is written to " + F.getPath() + ".";
		}
		return AreaMap.ascii(here, AreaMap.layout(here, A, whole ? -1 : Math.max(0, radius)), A, ids, flags);
	}

	/**
	 * WORLD DIG and WORLD PATH (B9, core/DigMode).
	 * @param mob the builder
	 * @param sub DIG or PATH
	 * @param words the words after it
	 */
	public static void worldDig(final MOB mob, final String sub, final List<String> words)
	{
		final Map<String, String> a = DigMode.args(words);
		final boolean twoWay = !"ONE-WAY".equalsIgnoreCase(a.get("LINK")) && !"ONEWAY".equalsIgnoreCase(a.get("LINK"));
		final String rest = a.get("").toUpperCase();
		if(sub.equals("PATH"))
		{
			mob.tell(DigMode.path(mob, a.get(""), a.get("TYPE"), twoWay, a.get("NAME"), a.get("DESC")));
			return;
		}
		if(rest.startsWith("OFF"))
		{
			DigMode.off(mob);
			mob.tell("WORLD DIG is off.");
		}
		else
		if(rest.startsWith("ON"))
		{
			DigMode.on(mob, a.get("TYPE"), twoWay);
			mob.tell(DigMode.describe(mob));
		}
		else
			mob.tell(DigMode.describe(mob) + "  WORLD DIG ON (TYPE=[LOCALE/TEMPLATE]) (LINK=TWO-WAY/ONE-WAY), WORLD DIG OFF");
	}

	/** the save a normal game's WORLD RELOAD is kept in while the world loads again */
	public static final String RELOAD_SLOT = "world-reload";

	private static final String RELOAD_USAGE = "WORLD RELOAD [AREA] [NAME] (FORCE), WORLD RELOAD SCRIPT [FILE]/ALL (RESET), "
		+ "WORLD RELOAD QUEST [NAME], WORLD RELOAD QUESTS, WORLD RELOAD CATALOG, WORLD RELOAD CHANGED";

	/**
	 * WORLD RELOAD (B5): world files loaded again into the running world.  In
	 * a normal game, it is saved and restored, which builds the world from
	 * the files again.
	 * @param mob the builder
	 * @param words the words after RELOAD
	 * @return what was done
	 */
	public static String worldReload(final MOB mob, final List<String> words)
	{
		if(!SaveGame.isBuilderMode())
		{
			if((!SaveGame.isDevMode()) && (!CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS)))
				return "WORLD RELOAD is for builder mode or a DEV=1 game.";
			if(!SaveGame.isAvailable())
				return "This game cannot be saved, so WORLD RELOAD cannot keep it while the world loads again.";
			final String problem = SaveGame.save(mob, RELOAD_SLOT);
			if(problem != null)
				return problem;
			final String again = SaveGame.restore(RELOAD_SLOT);
			return (again != null) ? again : ("The game is saved as '" + RELOAD_SLOT + "' and restored, with the world as its files have it now.");
		}
		if(words.isEmpty())
			return RELOAD_USAGE;
		final boolean force = words.get(words.size() - 1).equalsIgnoreCase("FORCE");
		if(force)
			words.remove(words.size() - 1);
		if(words.isEmpty())
			return RELOAD_USAGE;
		final String what = words.get(0).toUpperCase();
		final String rest = CMParms.combine(words, 1).trim();
		if(what.equals("SCRIPT") || what.equals("SCRIPTS"))
		{
			final boolean reset = (words.size() > 1) && words.get(words.size() - 1).equalsIgnoreCase("RESET");
			String file = reset ? CMParms.combine(words.subList(0, words.size() - 1), 1).trim() : rest;
			if(file.equalsIgnoreCase("ALL") || (file.length() == 0))
				file = null;
			return reloadScripts(file, reset);
		}
		if(what.equals("QUESTS"))
		{
			final StringBuilder str = new StringBuilder();
			for(int q = 0; q < CMLib.quests().numQuests(); q++)
			{
				final Quest Q = CMLib.quests().fetchQuest(q);
				if((Q != null) && (WorldFiles.questScriptIn(WorldFiles.questPathFor(Q.name())) != null))
					str.append((str.length() > 0) ? "\n\r" : "").append(reloadQuest(Q));
			}
			return (str.length() == 0) ? "No quest has a file in world/quests." : str.toString();
		}
		if(what.equals("QUEST"))
		{
			final Quest Q = CMLib.quests().fetchQuest(rest);
			return (Q == null) ? ("There is no quest '" + rest + "'.") : reloadQuest(Q);
		}
		if(what.equals("CATALOG"))
		{
			final String problem = WorldFiles.reloadCatalog();
			return (problem == null) ? "The catalog is loaded again from its file." : ("The catalog: " + problem);
		}
		if(what.equals("CHANGED"))
			return reloadChanged(mob, force);
		final String name = what.equals("AREA") ? rest : CMParms.combine(words, 0).trim();
		Area A = (name.length() == 0) ? mob.location().getArea() : CMLib.map().getArea(name);
		if(A == null)
			A = CMLib.map().findArea(name);
		if(A == null)
		{
			// an area by its file's name, as WORLD DIFF takes it
			final String path = WorldFiles.findPath(name, null);
			final String areaName = (path == null) ? null : WorldFiles.areaNameOf(path);
			A = (areaName == null) ? null : CMLib.map().getArea(areaName);
		}
		if(A == null)
			return "There is no area '" + name + "'.";
		return reloadArea(mob, A, force);
	}

	/** a room outside an area, for those in it to wait in while it loads again */
	private static Room waitingRoom(final MOB mob, final Area A)
	{
		final Room start = (mob == null) ? null : mob.getStartRoom();
		if((start != null) && (start.getArea() != A))
			return start;
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R != null) && (R.getArea() != A) && (!CMath.bset(R.getArea().flags(), Area.FLAG_INSTANCE_CHILD)))
				return R;
		}
		return null;
	}

	/**
	 * WORLD RELOAD AREA (B5): an area loaded again from its file.  Those
	 * playing in it, and their followers, wait outside it and come back;
	 * exits into it from other areas are linked again.  Rooms of it with
	 * unsaved mobs or items (B4) are refused unless forced.
	 * @param mob the builder
	 * @param A the area
	 * @param force true to drop unsaved changes
	 * @return what was done
	 */
	public static String reloadArea(final MOB mob, final Area A, final boolean force)
	{
		final String path = WorldFiles.areaPathFor(A);
		if(!new java.io.File(WorldFiles.worldDir(), path).isFile())
			return "There is no " + path + " to load " + A.Name() + " from.";
		if(!force)
		{
			int unsaved = 0;
			for(final Room R : Unsaved.rooms())
			{
				if(R.getArea() == A)
					unsaved++;
			}
			if(unsaved > 0)
				return unsaved + " rooms of " + A.Name() + " have unsaved mobs or items, which WORLD RELOAD drops: SAVE CHANGED first, or add FORCE.";
		}
		final Room wait = waitingRoom(mob, A);
		if(wait == null)
			return "There is no room outside " + A.Name() + " to wait in while it loads.";
		// the exits into it from elsewhere, by room ID
		final List<String[]> links = new ArrayList<String[]>();
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || (R.getArea() == A))
				continue;
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room to = R.rawDoors()[d];
				if((to != null) && (to.getArea() == A))
					links.add(new String[] { CMLib.map().getExtendedRoomID(R), "" + d, CMLib.map().getExtendedRoomID(to) });
			}
		}
		// the characters in it, and their followers, wait outside
		final List<MOB> moved = new ArrayList<MOB>();
		final List<String> where = new ArrayList<String>();
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			for(final MOB M : new ArrayList<MOB>(new XVector<MOB>(R.inhabitants())))
			{
				final MOB lead = (M == null) ? null : M.getGroupLeader();
				if((M != null) && ((M.playerStats() != null) || ((lead != null) && (lead.playerStats() != null))))
				{
					moved.add(M);
					where.add(CMLib.map().getExtendedRoomID(R));
				}
			}
		}
		for(final MOB M : moved)
			wait.bringMobHere(M, false);
		final String name = A.Name();
		CMLib.map().obliterateMapArea(A);
		Unsaved.clear();
		final String problem = WorldFiles.loadAreaFile(path);
		int lost = 0;
		for(final String[] link : links)
		{
			final Room R = CMLib.map().getRoom(link[0]);
			final Room to = CMLib.map().getRoom(link[2]);
			if(R == null)
				continue;
			R.rawDoors()[CMath.s_int(link[1])] = to;
			if(to == null)
				lost++;
		}
		final Area A2 = CMLib.map().getArea(name);
		final Room first = ((A2 == null) || (!A2.getProperMap().hasMoreElements())) ? null : A2.getProperMap().nextElement();
		for(int i = 0; i < moved.size(); i++)
		{
			Room back = CMLib.map().getRoom(where.get(i));
			if(back == null)
				back = first;
			if(back != null)
				back.bringMobHere(moved.get(i), false);
		}
		final StringBuilder str = new StringBuilder();
		if(A2 == null)
			str.append(path).append(" holds no area ").append(name).append(" now");
		else
			str.append(name).append(" is loaded again from ").append(path);
		if(problem != null)
			str.append(": ").append(problem);
		if(lost > 0)
			str.append("; ").append(lost).append(" exits from other areas lead to rooms it no longer has");
		return str.append(".").toString();
	}

	/**
	 * WORLD RELOAD SCRIPT (B5): the parsed scripts forgotten, and those that
	 * name the file (or all) read again, their triggers let run again with
	 * RESET.
	 * @param file the file, as LOAD= names it, or null for all
	 * @param reset true to let triggers that ran run again
	 * @return what was done
	 */
	public static String reloadScripts(final String file, final boolean reset)
	{
		for(final Iterator<String> k = Resources.findResourceKeys("PARSEDPRG: "); k.hasNext();)
		{
			final String key = k.next();
			if(key.startsWith("PARSEDPRG: "))
				Resources.removeResource(key);
		}
		final String want = (file == null) ? null : file.toUpperCase();
		int n = 0;
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			hosts.add(a.nextElement());
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			hosts.add(R);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if(M == null)
					continue;
				hosts.add(M);
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
					hosts.add(i.nextElement());
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				hosts.add(i.nextElement());
		}
		for(final PhysicalAgent P : hosts)
		{
			if(P == null)
				continue;
			for(final com.planet_ink.coffee_mud.Common.DefaultScriptingEngine S : ScriptCmd.engines(P))
			{
				final String script = S.getScript();
				if((want != null) && ((script == null) || (!script.toUpperCase().contains(want))))
					continue;
				S.setScript(script);
				if(reset)
					S.resetTriggers("ALL");
				n++;
			}
		}
		return n + " scripts" + ((file == null) ? "" : (" that name " + file)) + " are read again" + (reset ? ", their triggers reset." : ".");
	}

	/**
	 * WORLD RELOAD QUEST (B5): a quest stopped, its script read again from its
	 * world file and the files it loads, and started again at the step it was
	 * at, its variables kept.
	 * @param Q the quest
	 * @return what was done
	 */
	public static String reloadQuest(final Quest Q)
	{
		final String path = WorldFiles.questPathFor(Q.name());
		final String script = WorldFiles.questScriptIn(path);
		if(script == null)
			return "There is no " + path + " for the quest " + Q.name() + ".";
		final boolean wasRunning = Q.running();
		final int step = CMath.s_int("" + Q.getStat("STEPNUM"));
		for(final Iterator<String> k = Resources.findResourceKeys("PARSEDPRG: "); k.hasNext();)
		{
			final String key = k.next();
			if(key.startsWith("PARSEDPRG: "))
				Resources.removeResource(key);
		}
		final String scopeKey = "VARSCOPE-" + Q.name().toUpperCase().trim();
		final Object scopeVars = Resources.getResource(scopeKey);
		final Map<String, String> questVars = new TreeMap<String, String>(Q.getQuestVars());
		if(wasRunning)
			Q.stopQuest();
		if(CMLib.quests().fetchQuest(Q.name()) != Q)
			CMLib.quests().addQuest(Q);
		if((scopeVars != null) && (Resources.getResource(scopeKey) == null))
			Resources.submitResource(scopeKey, scopeVars);
		if(!Q.setScript(script, true))
			return "The quest " + Q.name() + " could not be read from " + path + "; see QUESTINFO for its errors.";
		CMLib.quests().save();
		WorldFiles.markLoaded(path);
		if(wasRunning)
		{
			Q.startQuest();
			if(step > 1)
				Q.setQuestStep(step);
			for(final String var : questVars.keySet())
				Q.setQuestVar(var, questVars.get(var));
			return "The quest " + Q.name() + " is read again from " + path + " and started at step " + Math.max(1, step) + ".";
		}
		return "The quest " + Q.name() + " is read again from " + path + ".";
	}

	/**
	 * WORLD RELOAD CHANGED (B5): every file WORLD STATUS lists as changed
	 * outside the game, loaded again as its kind is.
	 * @param mob the builder
	 * @param force true to drop unsaved changes in the areas
	 * @return what was done
	 */
	public static String reloadChanged(final MOB mob, final boolean force)
	{
		final Map<String, String> changed = WorldFiles.changedOutside();
		if(changed.isEmpty())
			return "No world file was changed outside the game.";
		final StringBuilder str = new StringBuilder();
		for(final Map.Entry<String, String> e : changed.entrySet())
		{
			final String path = e.getKey();
			String did;
			if(e.getValue().equals("deleted"))
				did = path + " was deleted: start the game again to leave it out, or SAVE FORCE to write it back.";
			else
			if((path.startsWith("areas/") || path.startsWith("dev/")) && path.endsWith(".cmare"))
			{
				final String name = WorldFiles.areaNameOf(path);
				final Area A = (name == null) ? null : CMLib.map().getArea(name);
				if(A != null)
					did = reloadArea(mob, A, force);
				else
				{
					final String problem = WorldFiles.loadAreaFile(path);
					did = (problem == null) ? (name + " is loaded from " + path + ".") : (path + ": " + problem + ".");
				}
			}
			else
			if(path.startsWith("quests/"))
			{
				Quest found = null;
				for(int q = 0; q < CMLib.quests().numQuests(); q++)
				{
					final Quest Q = CMLib.quests().fetchQuest(q);
					if((Q != null) && WorldFiles.questPathFor(Q.name()).equals(path))
						found = Q;
				}
				did = (found != null) ? reloadQuest(found) : (path + " is a quest the game does not have: CREATE QUEST LOAD=, or start the game again.");
			}
			else
			if(path.equals("catalog.cmare"))
			{
				final String problem = WorldFiles.reloadCatalog();
				did = (problem == null) ? "The catalog is loaded again from its file." : ("The catalog: " + problem);
			}
			else
				did = path + " is not loaded again while the game runs: start the game again for it.";
			str.append((str.length() > 0) ? "\n\r" : "").append(did);
		}
		return str.toString();
	}

	/** a file, as WORLD BACKUPS and RESTOREFILE take it: one the world has, or one that has backups */
	private static String backupPath(final String what)
	{
		final String path = WorldFiles.findPath(what, null);
		if((path != null) && (!WorldFiles.backupsOf(path).isEmpty()))
			return path;
		String w = what.trim().replace('\\', '/').toLowerCase();
		for(final String p : WorldFiles.backedUp())
		{
			final String name = p.substring(p.lastIndexOf('/') + 1).toLowerCase();
			final int dot = name.indexOf('.');
			if(p.equalsIgnoreCase(w) || name.equals(w) || ((dot > 0) && name.substring(0, dot).equals(w)))
				return p;
		}
		return path;
	}

	/**
	 * WORLD BACKUPS (B6): the files the export wrote over or deleted, with
	 * their backups, or one file's.
	 * @param what a file, or empty for all
	 * @return the list
	 */
	public static String worldBackups(final String what)
	{
		if(!SaveGame.isBuilderMode())
			return "WORLD BACKUPS is for builder mode, where SAVE writes the world files.";
		if(what.length() == 0)
		{
			final List<String> paths = WorldFiles.backedUp();
			if(paths.isEmpty())
				return "No world file has been written over or deleted by SAVE, so there are no backups.";
			final StringBuilder str = new StringBuilder("World files with backups in world/" + WorldFiles.BACKUP_DIR + " (WORLD BACKUPS FILE lists them):");
			for(final String p : paths)
			{
				final List<String> stamps = WorldFiles.backupsOf(p);
				str.append("\n\r  ").append(p).append(": ").append(stamps.size()).append(", the newest ").append(stamps.get(stamps.size() - 1));
			}
			return str.toString();
		}
		final String path = backupPath(what);
		if(path == null)
			return "There is no world file '" + what + "'.";
		final List<String> stamps = WorldFiles.backupsOf(path);
		if(stamps.isEmpty())
			return path + " has no backups.";
		final StringBuilder str = new StringBuilder("Backups of " + path + ", oldest first (WORLD RESTOREFILE " + what + " TIME puts one back):");
		for(final String s : stamps)
			str.append("\n\r  ").append(s);
		return str.toString();
	}

	/**
	 * WORLD RESTOREFILE (B6): a file put back as a backup has it, the newest
	 * or the one of the time given.
	 * @param words the file, then the time if any
	 * @return what was done
	 */
	public static String worldRestoreFile(final List<String> words)
	{
		if(!SaveGame.isBuilderMode())
			return "WORLD RESTOREFILE is for builder mode, where SAVE writes the world files.";
		if(words.isEmpty())
			return "WORLD RESTOREFILE [FILE] ([TIME]): a file put back as its backup has it.  WORLD BACKUPS lists them.";
		String stamp = null;
		if((words.size() > 1) && words.get(words.size() - 1).matches("\\d{8}(-\\d{0,6}(-\\d{0,3})?)?"))
			stamp = words.remove(words.size() - 1);
		final String what = CMParms.combine(words, 0).trim();
		final String path = backupPath(what);
		if(path == null)
			return "There is no world file '" + what + "'.";
		final String problem = WorldFiles.restoreBackup(path, stamp);
		if(problem != null)
			return problem + ".";
		final List<String> stamps = WorldFiles.backupsOf(path);
		String which = (stamp == null) ? stamps.get(stamps.size() - 1) : stamp;
		for(final String s : stamps)
		{
			if((stamp != null) && s.startsWith(stamp))
				which = s;
		}
		return path + " is put back as it was at " + which + ".  It is changed outside the game now: WORLD RELOAD loads it into the game, "
			+ "and SAVE leaves it as it is (SAVE FORCE writes over it).";
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
		// B9: WORLD DIG and WORLD PATH (BUILD is the Building skill outside a playtest)
		if(commands.get(1).equalsIgnoreCase("DIG") || commands.get(1).equalsIgnoreCase("PATH"))
		{
			if(!SaveGame.isBuilderMode())
			{
				mob.tell(L("WORLD DIG and WORLD PATH make rooms, so they are for builder mode."));
				return false;
			}
			worldDig(mob, commands.get(1).toUpperCase(), commands.subList(2, commands.size()));
			return false;
		}
		// B10: WORLD MAP (MAP is Skill_Map's word)
		if(commands.get(1).equalsIgnoreCase("MAP"))
		{
			mob.tell(worldMap(mob, commands.subList(2, commands.size())));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("RELOAD"))
		{
			mob.tell(worldReload(mob, new ArrayList<String>(commands.subList(2, commands.size()))));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("BACKUPS"))
		{
			mob.tell(worldBackups(CMParms.combine(commands, 2).trim()));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("RESTOREFILE"))
		{
			mob.tell(worldRestoreFile(new ArrayList<String>(commands.subList(2, commands.size()))));
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
			mob.tell(L("WORLD shows the world settings, WORLD PROBLEMS what did not load from the world files, WORLD CHECK what is wrong in the world, WORLD STATUS what SAVE would write and what was changed outside the game, WORLD DIG and WORLD PATH make rooms fast, WORLD MAP draws them, WORLD RELOAD loads files again, WORLD BACKUPS and RESTOREFILE the files SAVE wrote over, WORLD DIFF a file against what SAVE would write, WORLD DEV the areas for building and testing only, and WORLD SET KEY=VALUE changes a setting in builder mode."));
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
