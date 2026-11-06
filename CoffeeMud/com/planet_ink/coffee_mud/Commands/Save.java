package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.interfaces.TickableGroup.LocalType;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.PlayerLibrary.ThinPlayer;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.io.IOException;
import java.util.*;

/*
   Copyright 2004-2025 Bo Zimmerman

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
public class Save extends StdCommand
{
	public Save()
	{
	}

	private final String[] access=I(new String[]{"SAVE"});
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private int numSavableInhabitants(final Room room)
	{
		int ct=0;
		for(int i=0;i<room.numInhabitants();i++)
		{
			final MOB M=room.fetchInhabitant(i);
			if((M!=null)
			&&(M.isSavable())
			&&((M.amFollowing()==null)||(!M.amFollowing().isPlayer())))
				ct++;
		}
		return ct;
	}

	private int numSavableItems(final Room room)
	{
		int ct=0;
		for(int i=0;i<room.numItems();i++)
		{
			final Item I=room.getItem(i);
			if((I!=null)&&(I.isSavable()))
				ct++;
		}
		return ct;
	}

	/**
	 * The mobs SAVE ROOM HOME (B1) saves into a room: those whose start room it
	 * is, wherever they stand, and none from elsewhere.
	 * @param room the room
	 * @param skipped filled with the mobs standing here whose home is elsewhere
	 * @return the mobs
	 */
	public static List<MOB> homeMobs(final Room room, final List<String> skipped)
	{
		final List<MOB> mine = new ArrayList<MOB>();
		for(final Enumeration<MOB> m = room.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (!M.isSavable()))
				continue;
			final Room home = M.getStartRoom();
			if((home == null) || (home == room))
				mine.add(M);
			else
				skipped.add(M.name() + " (home " + CMLib.map().getExtendedRoomID(home) + ")");
		}
		// this room's own that have wandered: saved here, not lost
		for(final Enumeration<Room> r = room.getArea().getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || (R == room))
				continue;
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.isSavable() && (M.getStartRoom() == room) && (!mine.contains(M)))
					mine.add(M);
			}
		}
		return mine;
	}

	/**
	 * SAVE ROOM HOME (B1): the room's items as SAVE ROOM saves them, and each
	 * mob into its own start room: this room's own wherever they stand,
	 * those whose home is elsewhere left out and told of.
	 * @param mob the builder
	 * @param room the room
	 * @return true if saved
	 */
	public boolean saveRoomHome(final MOB mob, Room room)
	{
		synchronized(CMClass.getSync("SYNC"+room.roomID()))
		{
			room=CMLib.map().getRoom(room);
			CMLib.threads().clearDebri(room,LocalType.ITEMS_ONLY);
			final List<String> skipped = new ArrayList<String>();
			final List<MOB> mine = homeMobs(room, skipped);
			CMLib.database().DBUpdateItems(room);
			CMLib.threads().rejuv(room, Tickable.TICKID_ROOM_ITEM_REJUV);
			room.startItemRejuv();
			CMLib.database().DBUpdateTheseMOBs(room, mine);
			Unsaved.saved(room); // B4
			if((mob != null) && (skipped.size() > 0))
				mob.tell(L("Saved @x1 mobs as this room's; skipped: @x2.", "" + mine.size(), String.join(", ", skipped)));
			return true;
		}
	}

	/**
	 * SAVE CHANGED (B4): a room's mobs and items saved, as SAVE ROOM does.
	 * @param mob the builder, or null
	 * @param room the room
	 */
	public void saveChangedRoom(final MOB mob, final Room room)
	{
		if(SaveGame.isBuilderMode())
			saveRoomHome(mob, room);
		else
		{
			try
			{
				clearSaveAndRestart(mob, room, LocalType.MOBS_OR_ITEMS, true);
			}
			catch(final IOException e)
			{
				Log.errOut("Save", e);
			}
		}
	}

	public boolean clearSaveAndRestart(final MOB mob, Room room, final LocalType taskCode, final boolean noPrompt) throws IOException
	{
		synchronized(CMClass.getSync("SYNC"+room.roomID()))
		{
			room=CMLib.map().getRoom(room);
			if(taskCode == LocalType.MOBS_OR_ITEMS)
				Unsaved.saved(room); // B4
			CMLib.threads().clearDebri(room,LocalType.MOBS_OR_ITEMS);
			if((!noPrompt)
			&&(mob!=null)
			&&(mob.session()!=null))
			{
				final StringBuilder promptStr = new StringBuilder();
				final int[] counts = CMLib.database().DBCountRoomMobsItems(room.roomID());
				int mobCountDiff = 0;
				if((taskCode == LocalType.MOBS_OR_ITEMS) || (taskCode == LocalType.MOBS_ONLY))
					mobCountDiff= counts[0] - this.numSavableInhabitants(room);
				int itemCountDiff = 0;
				if((taskCode == LocalType.MOBS_OR_ITEMS) || (taskCode == LocalType.ITEMS_ONLY))
					itemCountDiff= counts[1] - this.numSavableItems(room);

				if(mobCountDiff < 0)
					promptStr.append(L("add @x1 mob(s) ",""+(-mobCountDiff)));
				else
				if(mobCountDiff > 0)
					promptStr.append(L("delete @x1 mob(s) ",""+(mobCountDiff)));

				if((itemCountDiff != 0) && (mobCountDiff != 0))
					promptStr.append(L("and "));

				if(itemCountDiff < 0)
					promptStr.append(L("add @x1 item(s) ",""+(-itemCountDiff)));
				else
				if(itemCountDiff > 0)
					promptStr.append(L("delete @x1 item(s) ",""+(itemCountDiff)));

				if(promptStr.length() > 0)
				{
					if((!mob.session().confirm(L("Saving @x1 will @x2. Are you sure (Y/n)?",
						CMLib.map().getExtendedRoomID(room),promptStr.toString()), "Y"))
					||(mob.session().isStopped()))
					{
						return false;
					}
				}
			}

			if((taskCode == LocalType.MOBS_OR_ITEMS) || (taskCode == LocalType.ITEMS_ONLY))
			{
				CMLib.database().DBUpdateItems(room);
				CMLib.threads().rejuv(room, Tickable.TICKID_ROOM_ITEM_REJUV);
				room.startItemRejuv();
			}
			if((taskCode == LocalType.MOBS_OR_ITEMS) || (taskCode == LocalType.MOBS_ONLY))
				CMLib.database().DBUpdateMOBs(room);
			return true;
		}
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(CMProps.isSinglePlayer())
		{
			// B4: SAVE CHANGED saves the rooms building commands changed, then the files
			if(SaveGame.isBuilderMode() && (commands.size()>1) && commands.get(1).equalsIgnoreCase("CHANGED") && builderAllowed(mob))
			{
				final int n=Unsaved.saveAll(mob);
				if(n==0)
					mob.tell(L("No room has unsaved mobs or items."));
				else
					mob.tell(L("Saved the mobs and items of @x1.",(n==1)?L("1 room"):L("@x1 rooms",""+n)));
				reportWorldFiles(mob, WorldFiles.exportAll());
				return false;
			}
			if(SaveGame.isBuilderMode() && (!isBuilderSave(commands)) && builderAllowed(mob))
			{
				// SAVE FORCE writes the files that loaded only in part too
				final boolean force=(commands.size()>1)&&commands.get(1).equalsIgnoreCase("FORCE");
				reportWorldFiles(mob, WorldFiles.exportAll(force));
				return false;
			}
			if(!isBuilderSave(commands))
			{
				final String slot=(commands.size()>1)?CMParms.combine(commands,1).trim():null;
				final String problem=SaveGame.save(mob, slot);
				if(problem!=null)
					mob.tell(problem);
				else
				{
					final String pointMsg=SaveGame.savePointMessage(mob);
					if(pointMsg.length()>0)
						mob.tell(pointMsg);
					mob.tell(L("Game saved as '@x1'.",SaveGame.currentSlot()));
					for(final String part : com.planet_ink.coffee_mud.core.save.WorldState.partlyKeptActivities())
						mob.tell(L("Kept in part: @x1 under way.  A restore starts those parts afresh.",part));
				}
				return false;
			}
			if(!builderAllowed(mob))
			{
				mob.tell(L("You are not allowed to do that."));
				return false;
			}
			SaveGame.warnAboutBuilding(mob);
		}
		final boolean result=builderSave(mob, commands);
		if(CMProps.isSinglePlayer() && SaveGame.isBuilderMode())
			reportWorldFiles(mob, WorldFiles.exportAll());
		return result;
	}

	/**
	 * In builder mode, tells the builder what writing the world files did.
	 * @param mob the builder
	 * @param changed what {@link WorldFiles#exportAll()} returned
	 */
	protected void reportWorldFiles(final MOB mob, final int changed)
	{
		if(changed < 0)
			mob.tell(L("The world files were not written.  If the world did not load whole from them, fix them and start again."));
		else
		if(changed == 0)
			mob.tell(L("The world files are up to date."));
		else
			mob.tell(L("The world files are written: @x1 changed.",""+changed));
		final List<String> held=WorldFiles.heldBack();
		if(held.size()>0)
			mob.tell(L("Not written, as they loaded only in part: @x1.  WORLD PROBLEMS says why; SAVE FORCE writes them as the world has them.",String.join(", ",held)));
		// B2: files changed outside the game are not written over
		final List<String> outside=WorldFiles.keptOutside();
		if(outside.size()>0)
			mob.tell(L("Not written, as they were changed outside the game: @x1.  What SAVE would write is beside each, as @x2, and WORLD DIFF shows the difference.  Start the game again to load the files as they are, or SAVE FORCE writes over them.",String.join(", ",outside),WorldFiles.BUILDER_EXT));
		// B4: what the world files do not have, as it is not saved to its rooms
		final int unsaved=Unsaved.count();
		if(unsaved>0)
			mob.tell(L("Unsaved mobs or items in @x1, which the world files do not have: @x2.  SAVE CHANGED saves them (LIST UNSAVED lists them).",
				(unsaved==1)?L("1 room"):L("@x1 rooms",""+unsaved),Unsaved.summary(5)));
		// what a check of the world's scripts finds (DT11)
		int scriptProblems=0;
		for(final String p : ScriptCheck.checkAll())
			scriptProblems+=ScriptCheck.isWarning(p)?0:1;
		if(scriptProblems>0)
			mob.tell(L("SCRIPTCHECK ALL finds @x1 problems in the world's scripts.",""+scriptProblems));
	}

	/**
	 * The builder's SAVE: writes rooms, areas, players and the like to the
	 * database.
	 * @param mob the builder
	 * @param commands the command words
	 * @return as for execute
	 * @throws java.io.IOException as for execute
	 */
	protected boolean builderSave(final MOB mob, final List<String> commands)
		throws java.io.IOException
	{
		if((commands.size()==1)&&CMSecurity.isSaveFlag(CMSecurity.SaveFlag.NOPLAYERS))
		{
			if(!mob.isMonster())
			{
				CMLib.database().DBUpdatePlayer(mob);
				CMLib.database().DBUpdateFollowers(mob);
				mob.tell(L("Your player record has been updated."));
			}
			return false;
		}

		mob.location().show(mob,null,CMMsg.MSG_OK_VISUAL,L("^S<S-NAME> wave(s) <S-HIS-HER> arms...^?"));
		String firstCommand="";
		String lastCommand = "";
		// SAVE ROOM HOME (B1), the default in builder mode, and SAVE ROOM ALL, as before
		boolean home = SaveGame.isBuilderMode();
		if((commands.size()>2)
		&&(commands.get(commands.size()-1).equalsIgnoreCase("HOME")||commands.get(commands.size()-1).equalsIgnoreCase("ALL"))
		&&(commands.get(commands.size()-2).equalsIgnoreCase("ROOM")))
		{
			home = commands.get(commands.size()-1).equalsIgnoreCase("HOME");
			commands.remove(commands.size()-1);
		}
		if(commands.size()>1)
		{
			firstCommand=commands.get(1).toUpperCase();
			lastCommand=commands.get(commands.size()-1).toUpperCase();
		}

		if(lastCommand.equals("USERS")||lastCommand.equals("PLAYERS")||lastCommand.equals("CHARACTERS"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDROOMS))
			{
				mob.tell(L("You are not allowed to save players."));
				return false;
			}
			for(final Session S : CMLib.sessions().allIterable())
			{
				final MOB M=S.mob();
				if(M!=null)
				{
					CMLib.database().DBUpdatePlayer(M);
					CMLib.database().DBUpdateFollowers(M);
				}
			}
			mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes everyone.\n\r"));
		}
		else
		if(lastCommand.equals("GRACES"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDRACES))
			{
				mob.tell(L("You are not allowed to save races."));
				return false;
			}
			final DatabaseEngine dbE=CMLib.database();
			final PlayerLibrary pLib=CMLib.players();
			for(final String name : pLib.getPlayerLists())
			{
				final ThinPlayer T = pLib.getThinPlayer(name);
				if(T!=null)
					dbE.registerRaceUsed(CMClass.getRace(T.race()));
			}
			for(final MOB M : CMLib.database().DBScanFollowers(null))
			{
				dbE.registerRaceUsed(M.charStats().getMyRace());
				M.destroy();
			}
			final int x=CMLib.database().updateAllRaceDates();
			mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of accounting permeates @x1 races.\n\r",""+x));
		}
		else
		if(lastCommand.equals("ITEMS"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDROOMS))
			{
				mob.tell(L("You are not allowed to save the mobs here."));
				return false;
			}
			if(firstCommand.equals("AREA"))
			{
				if((mob.session()!=null)&&(mob.session().confirm(L("Doing this assumes every item in every room in this area is correctly placed.  Are you sure (N/y)?"),"N")))
				{
					final Area A=mob.location().getArea();
					boolean saved = false;
					for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
						saved = clearSaveAndRestart(mob,e.nextElement(),LocalType.ITEMS_ONLY, true) || saved;
					if(saved)
						mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes the area.\n\r"));
				}
				else
					return false;
			}
			else
			if(firstCommand.equalsIgnoreCase("REWORLD"))
			{
				if((mob.session()!=null)
				&&(mob.session().confirm(L("This will load, and then re-save ever item in the world, for no reason.  Are you sure (N/y)?"),"N")))
				{
					for(final Enumeration<Room> r = CMLib.map().rooms();r.hasMoreElements();)
					{
						Room R=r.nextElement();
						if((R==null)||(R.roomID()==null)||(R.roomID().length()==0))
							continue;
						if(!CMSecurity.isAllowed(mob,R,CMSecurity.SecFlag.GMODIFY))
							continue;
						synchronized(CMClass.getSync("SYNC"+R.roomID()))
						{
							R=CMLib.map().getRoom(R);
							if((mob.session()==null)
							||(mob.session().isStopped())
							||(R==null)
							||(R.getArea()==null))
								continue;
							final Area A=R.getArea();
							R=CMLib.coffeeMaker().makeNewRoomContent(R,false);
							if(R==null)
								continue;
							final Area.State oldFlag=A.getAreaState();
							A.setAreaState(Area.State.FROZEN);
							A.setAreaState(oldFlag);
							if(R.numItems()>0)
							{
								final ArrayList<Item> items=new ArrayList<Item>(R.numItems());
								for(final Enumeration<Item> i=R.items();i.hasMoreElements();)
									items.add(i.nextElement());
								CMLib.database().DBUpdateTheseItems(R, items);
							}
							R.destroy();
						}
					}
				}
			}
			else
			{
				if(clearSaveAndRestart(mob,mob.location(),LocalType.ITEMS_ONLY, false))
					mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes the room.\n\r"));
			}
			Resources.removeResource("HELP_"+mob.location().getArea().Name().toUpperCase());
		}
		else
		if(lastCommand.equals("ROOM"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDROOMS))
			{
				mob.tell(L("You are not allowed to save the contents here."));
				return false;
			}
			if(firstCommand.equals("AREA"))
			{
				if((mob.session()!=null)
				&&(mob.session().confirm(L("Doing this assumes every mob and item in every room in this area is correctly placed.  Are you sure (N/y)?"),"N")))
				{
					final Area A=mob.location().getArea();
					boolean saved = false;
					for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
					{
						final Room R=e.nextElement();
						saved = (home ? saveRoomHome(mob,R) : clearSaveAndRestart(mob,R,LocalType.MOBS_OR_ITEMS, true)) || saved;
					}
					if(saved)
						mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes the area.\n\r"));
				}
				else
					return false;
			}
			else
			{
				CMLib.database().DBUpdateExits(mob.location());
				if(home ? saveRoomHome(mob,mob.location()) : clearSaveAndRestart(mob,mob.location(),LocalType.MOBS_OR_ITEMS, false))
					mob.location().show(mob,null,CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes the room.\n\r"));
			}
			Resources.removeResource("HELP_"+mob.location().getArea().Name().toUpperCase());
		}
		else
		if(lastCommand.equals("MOBS"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDROOMS))
			{
				mob.tell(L("You are not allowed to save the mobs here."));
				return false;
			}
			if(firstCommand.equals("AREA"))
			{
				if((mob.session()!=null)&&(mob.session().confirm(L("Doing this assumes every mob in every room in this area is correctly placed.  Are you sure (N/y)?"),"N")))
				{
					final Area A=mob.location().getArea();
					boolean saved = false;
					for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
						saved = clearSaveAndRestart(mob,e.nextElement(),LocalType.MOBS_ONLY, true) || saved;
					if(saved)
						mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes the area.\n\r"));
				}
				else
					return false;

			}
			else
			{
				if(clearSaveAndRestart(mob,mob.location(),LocalType.MOBS_ONLY, false))
					mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes the room.\n\r"));
			}
			Resources.removeResource("HELP_"+mob.location().getArea().Name().toUpperCase());
		}
		else
		if(firstCommand.equals("QUESTS")||lastCommand.equals("QUESTS"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDQUESTS))
			{
				mob.tell(L("You are not allowed to save the contents here."));
				return false;
			}
			CMLib.quests().save();
			mob.tell(L("Quest list saved."));
		}
		else
		if(firstCommand.equals("FACTION"))
		{
			if(!CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDFACTIONS))
			{
				mob.tell(L("You are not allowed to save factions."));
				return false;
			}
			final String factionID = (commands.size()>2) ? CMParms.combine(commands,2) : "";
			final Faction F=CMLib.factions().getFaction(factionID);
			if(F==null)
			{
				mob.tell(L("No such faction '@x1'.",factionID));
				return false;
			}
			else
			{
				F.setInternalFlags(CMath.unsetb(F.getInternalFlags(), Faction.IFLAG_NEVERSAVE));
				CMLib.factions().resaveFaction(F);
				mob.tell(L("Faction @x1 saved.",F.factionID()));
			}
		}
		else
		if(firstCommand.equals("USER")||firstCommand.equals("PLAYER")||firstCommand.equals("CHARACTER")||firstCommand.equals("CHAR"))
		{
			final MOB M=CMLib.players().getPlayer(lastCommand); //omg stay this t-group
			if(M==null)
			{
				mob.tell(L("No user named @x1",lastCommand));
				return false;
			}
			CMLib.players().savePlayer(M);
			mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes '@x1'.\n\r",M.name()));
		}
		else
		if(CMLib.players().getPlayer(firstCommand)!=null) //omg stay this t-group
		{
			final MOB M=CMLib.players().getPlayer(firstCommand);
			CMLib.players().savePlayer(M);
			mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes '@x1'.\n\r",M.name()));
		}
		else
		if(CMLib.players().getPlayer(lastCommand)!=null) //omg stay this t-group
		{
			final MOB M=CMLib.players().getPlayer(lastCommand);
			CMLib.players().savePlayer(M);
			mob.location().showHappens(CMMsg.MSG_OK_ACTION,L("A feeling of permanency envelopes '@x1'.\n\r",M.name()));
		}
		else
		{
			mob.tell(
				L("\n\rYou cannot save '@x1'. However, you might try ITEMS, USERS, [PLAYERNAME], FACTION, QUESTS, MOBS, or ROOM.",firstCommand));
		}
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

	/** the words that make SAVE the builder's, which writes the world, not a saved game */
	private static final Set<String> BUILDER_WORDS=new XHashSet<String>(new String[] {
		"USERS","PLAYERS","CHARACTERS","USER","PLAYER","CHARACTER","CHAR",
		"GRACES","ITEMS","AREA","REWORLD","ROOM","MOBS","QUESTS","FACTION"
	});

	/**
	 * Returns whether these words ask for the builder's SAVE, which writes
	 * rooms, areas, players and the like to the database, rather than for a
	 * saved game.
	 * @param commands the command words
	 * @return true for the builder's SAVE
	 */
	protected boolean isBuilderSave(final List<String> commands)
	{
		if(commands.size()<2)
			return false;
		return BUILDER_WORDS.contains(commands.get(1).toUpperCase())
			|| BUILDER_WORDS.contains(commands.get(commands.size()-1).toUpperCase());
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMProps.isSinglePlayer() || builderAllowed(mob);
	}

	protected boolean builderAllowed(final MOB mob)
	{
		return CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDROOMS)
			 ||CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDPLAYERS)
			 ||CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDFACTIONS)
			 ||CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.CMDQUESTS)
			 ||CMSecurity.isSaveFlag(CMSecurity.SaveFlag.NOPLAYERS);
	}

}
