package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CMLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2008-2025 Bo Zimmerman

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
public class Generate extends StdCommand
{
	public Generate()
	{
	}

	enum PercObjectType
	{
		STRING,
		AREA,
		MOB,
		ROOM,
		ITEM,
		QUEST,
		SCRIPT
	}

	private final String[]	access	= I(new String[] { "GENERATE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	public void createNewPlace(final MOB mob, final Room oldR, final Room R, final int direction, final boolean save)
	{
		if(R.roomID().length()==0)
		{
			R.setArea(oldR.getArea());
			R.setRoomID(oldR.getArea().getNewRoomID(oldR, direction));
		}
		Exit E=R.getExitInDir(Directions.getOpDirectionCode(direction));
		if(E==null)
			E = CMClass.getExit("Open");
		oldR.setRawExit(direction, E);
		oldR.rawDoors()[direction]=R;
		final int opDir=Directions.getOpDirectionCode(direction);
		if(R.getRoomInDir(opDir)!=null)
			mob.tell(L("An error has caused the following exit to be one-way."));
		else
		{
			R.setRawExit(opDir, E);
			R.rawDoors()[opDir]=oldR;
		}
		if(save)
			CMLib.database().DBUpdateExits(oldR);
		final String dirName=CMLib.directions().getDirectionName(direction, CMLib.flags().getDirType(R));
		oldR.showHappens(CMMsg.MSG_OK_VISUAL,L("A new place materializes to the @x1",dirName));
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		// GENERATE CHECK file: the file's mistakes, generating nothing (DT14)
		if((commands.size()>2)&&(commands.get(1).equalsIgnoreCase("CHECK")))
		{
			final String path=CMParms.combine(commands,2);
			final List<String> problems=com.planet_ink.coffee_mud.core.GenerateCheck.check(path);
			if(problems.isEmpty())
				mob.tell(L("GENERATE CHECK @x1: no problems found.",path));
			else
				mob.tell(L("GENERATE CHECK @x1: @x2 problems:",path,""+problems.size())+"\n\r"+String.join("\n\r",problems));
			return false;
		}
		// B12: GENERATE ... SANDBOX: in the sandbox, NOSAVE, an area it makes unsavable
		if((commands.size()>3)&&commands.get(commands.size()-1).equalsIgnoreCase("SANDBOX"))
		{
			final List<String> words=new ArrayList<String>(commands.subList(0,commands.size()-1));
			if(!words.get(1).equalsIgnoreCase("NOSAVE"))
				words.add(1,"NOSAVE");
			mob.tell(Sandbox.generate(mob,this,words,metaFlags));
			return false;
		}
		SaveGame.warnAboutBuilding(mob);
		boolean save=true;
		final List<String> origCommands = new XVector<String>(commands);
		if(commands.size()>1)
		{
			if(commands.get(1).equalsIgnoreCase("nosave"))
			{
				save=false;
				commands.remove(1);
			}
		}
		// G9: GENERATE FILL roomtemplate [FROM file] [VAR=VALUE ...] ROOM|AREA [KEEPTEXT] [SEED=n]: on rooms that are there
		if((commands.size()>2)&&commands.get(1).equalsIgnoreCase("FILL"))
		{
			final String fillId=commands.get(2);
			String fillFile="randareas/example.xml";
			boolean wholeArea=false;
			boolean keepText=false;
			String fillSeed=null;
			final Map<String,String> fillVars=new LinkedHashMap<String,String>();
			for(int i=3;i<commands.size();i++)
			{
				final String w=commands.get(i);
				if(w.equalsIgnoreCase("FROM")&&(i+1<commands.size()))
					fillFile=commands.get(++i);
				else
				if(w.equalsIgnoreCase("AREA"))
					wholeArea=true;
				else
				if(w.equalsIgnoreCase("ROOM"))
					wholeArea=false;
				else
				if(w.equalsIgnoreCase("KEEPTEXT"))
					keepText=true;
				else
				{
					final int x=w.indexOf('=');
					if(x>0)
					{
						final String k=w.substring(0,x).trim();
						final String v=w.substring(x+1).trim();
						if(k.equalsIgnoreCase("SEED"))
							fillSeed=v;
						else
							fillVars.put(k,v);
					}
				}
			}
			mob.tell(GenFill.fill(mob,fillFile,fillId,fillVars,wholeArea,keepText,fillSeed,save));
			return false;
		}
		if(commands.size()<3)
		{
			mob.tell(L("Generate what? Try GENERATE [TYPE] [ID] (FROM [DATA_FILE_PATH]) ([VAR=VALUE]..) [DIRECTION], or GENERATE CHECK [DATA_FILE_PATH]"));
			return false;
		}
		final String finalLog = mob.Name()+" called generate command with parms: " + CMParms.combine(commands, 1);
		CMFile file = null;
		String fromPath = "randareas/example.xml"; // G13: for GENSOURCE
		if((commands.size()>3)&&commands.get(3).equalsIgnoreCase("FROM"))
		{
			fromPath = commands.get(4);
			file = new CMFile(Resources.buildResourcePath(commands.get(4)),mob);
			commands.remove(3);
			commands.remove(3);
		}
		else
			file = new CMFile(Resources.buildResourcePath("randareas/example.xml"),mob);
		if(!file.canRead())
		{
			mob.tell(L("Random data file '@x1' not found.  Aborting.",file.getCanonicalPath()));
			return false;
		}
		final StringBuffer xml = file.textUnformatted();
		final List<XMLTag> xmlRoot = CMLib.xml().parseAllXML(xml);
		final Hashtable<String,Object> definedIDs = new Hashtable<String,Object>();
		definedIDs.putAll(CMParms.parseEQParms(commands,3,commands.size()));
		CMLib.percolator().buildDefinedIDSet(xmlRoot,definedIDs, new XTreeSet<String>(definedIDs.keys()));
		final String typeName = commands.get(1);
		String objectType = typeName.toUpperCase().trim();
		PercObjectType codeI=(PercObjectType)CMath.s_valueOf(PercObjectType.class, objectType.toUpperCase());
		if(codeI==null)
		{
			for(final PercObjectType keyobj : PercObjectType.values())
			{
				final String key = keyobj.name();
				if(key.startsWith(typeName.toUpperCase().trim()))
				{
					objectType = key;
					codeI=(PercObjectType)CMath.s_valueOf(PercObjectType.class, key);
				}
			}
			if(codeI==null)
			{
				mob.tell(L("'@x1' is an unknown object type.  Try: @x2",typeName,CMParms.toListString(PercObjectType.values())));
				return false;
			}
		}
		int direction=-1;
		boolean unlinked=false; // G13: GENERATE AREA ... [DIR] UNLINKED: built with no gate, for LINK later
		if((codeI==PercObjectType.AREA)&&(commands.size()>3)&&commands.get(commands.size()-1).equalsIgnoreCase("UNLINKED"))
		{
			unlinked=true;
			commands.remove(commands.size()-1);
			if(CMLib.directions().getGoodDirectionCode(commands.get(commands.size()-1))<0)
				commands.add("north");
		}
		if((codeI==PercObjectType.AREA)||(codeI==PercObjectType.ROOM))
		{
			final String possDir=commands.remove(commands.size()-1);
			direction = CMLib.directions().getGoodDirectionCode(possDir);
			if(direction<0)
			{
				mob.tell(L("When creating an area or room, the LAST parameter to this command must be a direction to link to this room by."));
				return false;
			}
			if((!unlinked)&&(mob.location().getRoomInDir(direction)!=null))
			{
				final String dirName=CMLib.directions().getDirectionName(direction, CMLib.flags().getInDirType(mob));
				mob.tell(L("A room already exists in direction @x1. Action aborted.",dirName));
				return false;
			}
		}
		PhysicalAgent target=null;
		if(codeI==PercObjectType.SCRIPT)
		{
			final String possTarget=commands.remove(commands.size()-1);
			target = mob.location().fetchFromMOBRoomFavorsMOBs(mob, null, possTarget, Filterer.ANYTHING);
			if(target == null)
			{
				mob.tell(L("When creating an script, the last argument must be the object to apply it to."));
				return false;
			}
		}
		final String idName = commands.get(2).toUpperCase().trim();
		if((!(definedIDs.get(idName) instanceof XMLTag))
		||(!((XMLTag)definedIDs.get(idName)).tag().equalsIgnoreCase(objectType)))
		{
			if(idName.equals("ALL"))
			{
				final int cmdIndex = origCommands.indexOf(commands.get(2));
				if(cmdIndex < 0)
				{
					mob.tell(L("Unable to generate all. :("));
					return false;
				}
				else
				{
					for(final Enumeration<String> keys=definedIDs.keys();keys.hasMoreElements();)
					{
						final String key=keys.nextElement();
						if((definedIDs.get(key) instanceof XMLTag)
						&&(((XMLTag)definedIDs.get(key)).tag().equalsIgnoreCase(objectType)))
						{
							final List<String> newCmds = new XVector<String>(origCommands);
							newCmds.set(cmdIndex, key);
							this.execute(mob, newCmds, metaFlags);
						}
					}
					return true;
				}
			}
			else
			{
				if(!idName.equalsIgnoreCase("LIST"))
					mob.tell(L("The @x1 id '@x2' has not been defined in the data file.",objectType,idName));

				final StringBuffer foundIDs=new StringBuffer("");
				for(final PercObjectType pType : PercObjectType.values())
				{
					final String tKey = pType.name();
					foundIDs.append("^H"+tKey+"^N: \n\r");
					final Vector<String> xmlTagsV=new Vector<String>();
					for(final Enumeration<String> keys=definedIDs.keys();keys.hasMoreElements();)
					{
						final String key=keys.nextElement();
						if((definedIDs.get(key) instanceof XMLTag)
						&&(((XMLTag)definedIDs.get(key)).tag().equalsIgnoreCase(tKey)))
							xmlTagsV.add(key.toLowerCase());
					}
					foundIDs.append(CMParms.toListString(xmlTagsV)+"\n\r");
				}
				mob.tell(L("Found ids include: \n\r@x1",foundIDs.toString()));
				return false;
			}
		}

		final XMLTag piece=(XMLTag)definedIDs.get(idName);
		definedIDs.putAll(CMParms.parseEQParms(commands,3,commands.size()));
		try
		{
			CMLib.percolator().checkRequirements(piece, definedIDs);
		}
		catch(final CMException cme)
		{
			mob.tell(L("Required ids for @x1 were missing: @x2",idName,cme.getMessage()));
			return false;
		}
		// G1: SEED=n builds the same thing each time; with none, a new seed, told after
		final Object seedGiven=definedIDs.remove("SEED");
		final Map<String,String> givenVars=CMParms.parseEQParms(commands,3,commands.size()); // G13: for GENSOURCE
		final long seed=GenSeed.pick((seedGiven==null)?null:seedGiven.toString(),null);
		definedIDs.put("GEN_SEED",""+seed);
		GenSeed.context(definedIDs);
		final Long seedBefore=GenSeed.begin(seed);
		final Vector<Object> V = new Vector<Object>();
		try
		{
			switch(codeI)
			{
			case STRING:
			{
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				final String s=CMLib.percolator().findString("STRING", piece, definedIDs);
				if(s!=null)
					V.add(s);
				break;
			}
			case SCRIPT:
			{
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				final String s=CMLib.percolator().findString("SCRIPT", piece, definedIDs);
				if(s!=null)
					V.add(s);
				break;
			}
			case AREA:
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				definedIDs.put("ROOMTAG_NODEGATEEXIT", CMLib.directions().getDirectionName(Directions.getOpDirectionCode(direction)));
				definedIDs.put("ROOMTAG_GATEEXITROOM", mob.location());
				final Area A=CMLib.percolator().findArea(piece, definedIDs, direction);
				if(A!=null)
					V.add(A);
				break;
			case MOB:
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				V.addAll(CMLib.percolator().findMobs(piece, definedIDs));
				break;
			case ROOM:
			{
				final Exit[] exits=new Exit[Directions.NUM_DIRECTIONS()];
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				definedIDs.put("ROOMTAG_NODEGATEEXIT", CMLib.directions().getDirectionName(Directions.getOpDirectionCode(direction)));
				definedIDs.put("ROOMTAG_GATEEXITROOM", mob.location());
				final Room R=CMLib.percolator().buildRoom(mob.location().getArea(), piece, definedIDs, exits, direction);
				if(R!=null)
					V.add(R);
				break;
			}
			case ITEM:
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				V.addAll(CMLib.percolator().findItems(piece, definedIDs));
				break;
			case QUEST:
			{
				CMLib.percolator().preDefineReward(piece, definedIDs);
				CMLib.percolator().defineReward(piece,definedIDs);
				final String s=CMLib.percolator().buildQuestScript(piece, definedIDs, null);
				if(s!=null)
					V.add(s);
				break;
			}
			default:
				break;
			}
			if(V.size()==0)
				mob.tell(L("Nothing generated."));
			else
			for(int v=0;v<V.size();v++)
			{
				if(V.get(v) instanceof MOB)
				{
					((MOB)V.get(v)).bringToLife(mob.location(),true);
					mob.location().showHappens(CMMsg.MSG_OK_VISUAL,L("@x1 appears.",((MOB)V.get(v)).name()));
					CMLib.percolator().postProcess(definedIDs);
					if(!GenSource.backticks()) // G13: GEN_BACKTICKS=false, real apostrophes
						GenSource.untick(V.get(v));
					Log.sysOut("Generate",mob.Name()+" generated mob "+((MOB)V.get(v)).name());
				}
				else
				if(V.get(v) instanceof Item)
				{
					mob.location().addItem((Item)V.get(v));
					mob.location().showHappens(CMMsg.MSG_OK_VISUAL,L("@x1 appears.",((Item)V.get(v)).name()));
					CMLib.percolator().postProcess(definedIDs);
					if(!GenSource.backticks()) // G13
						GenSource.untick(V.get(v));
					Log.sysOut("Generate",mob.Name()+" generated item "+((Item)V.get(v)).name());
				}
				else
				if(V.get(v) instanceof String)
				{
					CMLib.percolator().postProcess(definedIDs);
					if(codeI==PercObjectType.QUEST)
					{
						if((!definedIDs.containsKey("QUEST_ID"))
						||(!(definedIDs.get("QUEST_ID") instanceof String)))
						{
							mob.tell(L("Unable to create your quest because a quest_id was not generated"));
							return false;
						}
						final String name=(String)definedIDs.get("QUEST_ID");
						final Quest Q=(Quest)CMClass.getCommon("DefaultQuest");
						Q.setScript((String)V.get(0),true);
						if((Q.name().trim().length()==0)||(Q.duration()<0))
						{
							mob.tell(L("Unable to create your quest.  Please consult the log."));
							return false;
						}
						final Quest badQ=CMLib.quests().fetchQuest(name);
						if(badQ!=null)
						{
							mob.tell(L("Unable to create your quest.  One of that name already exists!"));
							return false;
						}
						mob.tell(L("Generated quest '@x1'",Q.name()));
						Log.sysOut("Generate",mob.Name()+" created quest '"+Q.name()+"'");
						CMLib.quests().addQuest(Q);
						if(!Q.running())
						{
							if(!Q.startQuest())
							{
								CMLib.quests().delQuest(Q);
								mob.tell(L("Unable to start quest '@x1' Perhaps the problem was logged?",Q.name()));
								mob.tell((String)V.get(0));
								return false;
							}
						}
						CMLib.quests().save();
					}
					else
					if(codeI==PercObjectType.SCRIPT)
					{
						final ScriptingEngine engE = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
						engE.setScript((String)V.get(v));
						if(target != null)
							target.addScript(engE);
					}
					else
						mob.tell((String)V.get(v));
				}
				else
				if(V.get(v) instanceof Room)
				{
					final Room R=(Room)V.get(v);
					createNewPlace(mob,mob.location(),R,direction,save);
					CMLib.percolator().postProcess(definedIDs);
					if(!GenSource.backticks()) // G13
						GenSource.untick(R);
					if(save)
					{
						CMLib.database().DBCreateRoom(R);
						CMLib.database().DBUpdateExits(R);
						CMLib.database().DBUpdateItems(R);
						CMLib.database().DBUpdateMOBs(R);
					}
					Log.sysOut("Generate",mob.Name()+" generated room "+R.roomID());
					ScriptEvents.generated(null,R,idName,definedIDs); // ST20
				}
				else
				if(V.get(v) instanceof Area)
				{
					final Area A=(Area)V.get(v);
					A.addBlurbFlag("GENSEED "+seed); // G1: the seed it was built with
					GenSource.record(A,fromPath,idName,seed,givenVars); // G13: how it was made, for REGENERATE
					CMLib.map().addArea(A);
					CMLib.map().registerWorldObjectLoaded(A, null, A);
					if(save)
						CMLib.database().DBCreateArea(A);
					Room R=A.getRoom(A.Name()+"#0");
					if(R==null)
						R=A.getFilledProperMap().nextElement();
					if(unlinked)
						mob.tell(L("'@x1' is built unlinked: LINK @x2 [DIRECTION] from the room it should join.",A.Name(),R.roomID()));
					else
						createNewPlace(mob,mob.location(),R,direction,save);
					CMLib.percolator().postProcess(definedIDs);
					if(!GenSource.backticks()) // G13
						GenSource.untick(A);
					if(save)
					{
						mob.tell(L("Saving remaining rooms for area '@x1'...",A.name()));
						for(final Enumeration<Room> e=A.getFilledProperMap();e.hasMoreElements();)
						{
							R=e.nextElement();
							CMLib.database().DBCreateRoom(R);
							CMLib.database().DBUpdateExits(R);
							CMLib.database().DBUpdateItems(R);
							CMLib.database().DBUpdateMOBs(R);
						}
						mob.tell(L("Done saving remaining rooms for area '@x1'",A.name()));
					}
					Log.sysOut("Generate",mob.Name()+" generated area "+A.name());
					ScriptEvents.generated(A,null,idName,definedIDs); // ST20
				}
			}
		}
		catch(final CMException cex)
		{
			mob.tell(L("Unable to fully generate: @x1",cex.getMessage()));
			if(CMSecurity.isDebugging(CMSecurity.DbgFlag.MUDPERCOLATOR))
				Log.debugOut("Generate",cex);
			return false;
		}
		finally
		{
			GenSeed.end(seedBefore); // G1: the game's own rolls go on as before
		}
		if(seedGiven==null)
			mob.tell(L("Seed: @x1 (SEED=@x1 builds the same again).",""+seed));
		Log.sysOut("Generate",finalLog);
		return true;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDAREAS);
	}
}
