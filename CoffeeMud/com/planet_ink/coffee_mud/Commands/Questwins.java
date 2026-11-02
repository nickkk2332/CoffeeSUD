package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.MPContext;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2006-2025 Bo Zimmerman

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
public class Questwins extends StdCommand
{
	public Questwins()
	{
	}

	private final String[]	access	= I(new String[] { "QUESTS", "QUESTWINS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private final Comparator<Quest> questNameSorter = new Comparator<Quest>()
	{
		@Override
		public int compare(final Quest o1, final Quest o2)
		{
			if(o1 == null)
				return (o2==null) ? 0 : -1;
			else
			if(o2 == null)
				return 1;
			else
			{
				final String name1=o1.displayName().trim().length()>0?o1.displayName():o1.name();
				final String name2=o2.displayName().trim().length()>0?o2.displayName():o2.name();
				return name1.compareTo(name2);
			}
		}

	};

	protected String getQuestsWonList(final String mobName, final String pronoun)
	{
		final ArrayList<Quest> qVec=new ArrayList<Quest>();
		for(int q=0;q<CMLib.quests().numQuests();q++)
		{
			final Quest Q=CMLib.quests().fetchQuest(q);
			if(Q.wasWinner(mobName))
			{
				if(!qVec.contains(Q))
					qVec.add(Q);
			}
		}
		Collections.sort(qVec,questNameSorter);
		final StringBuffer msg=new StringBuffer(L("^HQuests @x1 listed as having won:^?^N\n\r",pronoun));
		for(final Quest Q : qVec)
		{
			final String name=Q.displayName().trim().length()>0?Q.displayName():Q.name();
			final String time = CMLib.time().date2String(Q.whenLastWon(mobName).longValue());
			final QuestRecords.Record R=QuestRecords.get(Q.name(),mobName); // Q4: the outcome
			final List<String> how=new ArrayList<String>(); // Q4, Q5: the outcome, and the wins
			if((R!=null)&&R.status.equals(QuestRecords.WON)&&(R.outcome.length()>0))
				how.add(R.outcome);
			if((R!=null)&&(R.wins>1))
				how.add(L("@x1 wins",""+R.wins));
			final String outcome=how.isEmpty()?"":(" ("+String.join(", ",how)+")");
			msg.append(CMStrings.padRight(time,20)+name+outcome+"^N\n\r");
		}
		return msg.toString();
	}

	/**
	 * Q4: the quests a character failed, dropped or let run out.
	 * @param mobName the character's name
	 * @param pronoun "you are", or "Name is"
	 * @return the list
	 */
	protected String getQuestsFailedList(final String mobName, final String pronoun)
	{
		final StringBuffer msg=new StringBuffer(L("^HQuests @x1 listed as having failed:^?^N\n\r",pronoun));
		for(final Map.Entry<String,QuestRecords.Record> e : QuestRecords.of(mobName).entrySet())
		{
			final QuestRecords.Record R=e.getValue();
			if(!(R.status.equals(QuestRecords.FAILED)||R.status.equals(QuestRecords.ABANDONED)||R.status.equals(QuestRecords.EXPIRED)))
				continue;
			final Quest Q=CMLib.quests().fetchQuest(e.getKey());
			final String name=((Q!=null)&&(Q.displayName().trim().length()>0))?Q.displayName():e.getKey();
			final String why=(R.outcome.length()>0)?R.outcome:R.status;
			msg.append(CMStrings.padRight(CMLib.time().date2String(R.when),20)+name+" ("+why.toLowerCase()+")^N\n\r");
		}
		return msg.toString();
	}

	public String getQuestsDoingList(final String mobName, final String pronoun)
	{
		final List<Quest> qVec=getQuestsDoingList(mobName);
		final MOB mob=CMLib.players().getPlayerAllHosts(mobName);
		final StringBuffer msg=new StringBuffer(L("^HQuests @x1 listed as having accepted:^?^N\n\r",pronoun));
		// Q3: grouped by CATEGORY, main first, each with its objectives and time left
		boolean grouped=false;
		for(final Quest Q : qVec)
			grouped=grouped||(categoryOf(Q).length()>0);
		String lastCat=null;
		for(int i=0;i<qVec.size();i++)
		{
			final Quest Q=qVec.get(i);
			final String cat=categoryOf(Q);
			if(grouped&&(!cat.equals(lastCat)))
			{
				msg.append("^H").append((cat.length()>0)?CMStrings.capitalizeAndLower(cat):L("Other")).append("^N\n\r");
				lastCat=cat;
			}
			final String name=Q.displayName().trim().length()>0?Q.displayName():Q.name();
			msg.append((i+1)+") "+(name)+timeLeftOf(mob,Q)+"^N\n\r");
			if(mob!=null)
			{
				for(final String line : objectiveLines(mob,Q))
					msg.append("     ").append(line).append("^N\n\r");
			}
		}
		return msg.toString();
	}

	/**
	 * Q3: a quest's category, as SET CATEGORY gave it.
	 * @param Q the quest
	 * @return the category, lower case, or ""
	 */
	protected static String categoryOf(final Quest Q)
	{
		final String c=Q.getStat("CATEGORY");
		return (c==null)?"":c.trim().toLowerCase();
	}

	/** Q3: main first, then the other categories, then none, each by name */
	private final Comparator<Quest> questCategorySorter = new Comparator<Quest>()
	{
		private int rank(final Quest Q)
		{
			final String c=categoryOf(Q);
			return c.equals("main")?0:(c.length()>0)?1:2;
		}

		@Override
		public int compare(final Quest o1, final Quest o2)
		{
			if((o1==null)||(o2==null))
				return questNameSorter.compare(o1,o2);
			final int r=rank(o1)-rank(o2);
			if(r!=0)
				return r;
			final int c=categoryOf(o1).compareTo(categoryOf(o2));
			return (c!=0)?c:questNameSorter.compare(o1,o2);
		}
	};

	/**
	 * Q3: the time a running quest has left, on the game clock; Q11: or the
	 * character's own deadline for it (MPQTIMER), if that is sooner.
	 * @param mob the character, or null
	 * @param Q the quest
	 * @return " (time left: ...)", or ""
	 */
	protected String timeLeftOf(final MOB mob, final Quest Q)
	{
		final long ticks=ticksLeftOf(mob,Q);
		if(ticks<0)
			return "";
		return " ("+L("time left: @x1",QuestJournal.timeLeft(ticks))+")";
	}

	/**
	 * Q3, Q11: the ticks a quest has left for a character: its duration's,
	 * or their own deadline's (MPQTIMER), the sooner.
	 * @param mob the character, or null
	 * @param Q the quest
	 * @return the ticks, or -1 for no limit
	 */
	protected long ticksLeftOf(final MOB mob, final Quest Q)
	{
		long ticks=((!Q.running())||(Q.duration()<=0)||(Q.ticksRemaining()<=0))?-1:Q.ticksRemaining();
		final long own=(mob==null)?-1:QuestTimers.ticksLeft(mob,Q.name());
		if((own>=0)&&((ticks<0)||(own<ticks)))
			ticks=own;
		return ticks;
	}

	/**
	 * Q3: a character's objectives for a quest: the journal's, and those
	 * the quest's script sets as OBJECTIVEn and OBJECTIVEnDONE.
	 * @param mob the character
	 * @param Q the quest
	 * @return the lines, [x] or [ ] and the text
	 */
	protected List<String> objectiveLines(final MOB mob, final Quest Q)
	{
		final List<String> lines=QuestJournal.objectiveLines(mob,Q.name());
		for(final Enumeration<ScriptingEngine> e=mob.scripts();e.hasMoreElements();)
		{
			final ScriptingEngine SE=e.nextElement();
			if((SE==null)||(!SE.defaultQuestName().equalsIgnoreCase(Q.name())))
				continue;
			for(int n=1;n<=20;n++)
			{
				final String text=SE.getVar("*","OBJECTIVE"+n);
				if((text==null)||(text.trim().length()==0))
					continue;
				final String done=SE.getVar("*","OBJECTIVE"+n+"DONE");
				final boolean isDone=(done!=null)&&(done.trim().length()>0)&&(!done.trim().equals("0"))&&(!done.trim().equalsIgnoreCase("false"));
				lines.add((isDone?"[x] ":"[ ] ")+text.trim());
			}
		}
		return lines;
	}

	public List<Quest> getQuestsDoingList(final String mobName)
	{
		final Vector<Quest> qVec = new Vector<Quest>();
		final MOB mob = CMLib.players().getPlayerAllHosts(mobName);
		if(mob != null)
		{
			final List<Quest> qQVec=CMLib.quests().getPlayerPersistentQuests(mob);
			final Set<String> namesDone = new HashSet<String>();
			for(final Quest Q : qQVec)
			{
				final String name=Q.displayName().trim().length()>0?Q.displayName():Q.name();
				if(!namesDone.contains(name))
				{
					namesDone.add(name);
					qVec.add(Q);
				}
			}
			Collections.sort(qVec, questCategorySorter); // Q3: by category, main first
		}
		return qVec;
	}

	public Quest findScriptedQuest(final String mobName, final String rest)
	{
		final List<Quest> quests = getQuestsDoingList(mobName);
		if(CMath.isInteger(rest))
		{
			final int x=CMath.s_int(rest)-1;
			if((x>=0)&&(x<quests.size()))
				return quests.get(x);
		}
		return findQuest(quests, rest);
	}

	public Quest findQuest(final List<Quest> fromList, final String rest)
	{
		Quest Q=null;
		if(CMath.isInteger(rest))
		{
			final int x=CMath.s_int(rest)-1;
			if((x>=0)&&(x<fromList.size()))
				return fromList.get(x);
		}
		final String lowerRest = rest.toLowerCase();
		for(final Iterator<Quest> q=fromList.iterator();q.hasNext();)
		{
			final Quest Q2=q.next();
			if((Q2!=null)
			&&(Q2.displayName()!=null)
			&&(Q2.displayName().length()>0)
			&&(Q2.displayName().toLowerCase().startsWith(lowerRest)))
			{
				Q=Q2;
				break;
			}
		}
		if(Q==null)
		{
			for(final Iterator<Quest> q=fromList.iterator();q.hasNext();)
			{
				final Quest Q2=q.next();
				if((Q2!=null)
				&&(Q2.displayName()!=null)
				&&(Q2.displayName().length()>0)
				&&(CMLib.english().containsString(Q2.displayName().toUpperCase(), rest)))
				{
					Q=Q2;
					break;
				}
			}
		}
		if(Q==null)
		{
			for(final Iterator<Quest> q=fromList.iterator();q.hasNext();)
			{
				final Quest Q2=q.next();
				if((Q2!=null)
				&&(Q2.name().toLowerCase().startsWith(lowerRest)))
				{
					Q=Q2;
					break;
				}
			}
		}
		if(Q==null)
		{
			for(final Iterator<Quest> q=fromList.iterator();q.hasNext();)
			{
				final Quest Q2=q.next();
				if((Q2!=null)
				&&(CMLib.english().containsString(Q2.name().toUpperCase(), rest)))
				{
					Q=Q2;
					break;
				}
			}
		}
		if(Q==null)
			Q=CMLib.quests().findQuest(rest);
		return Q;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.get(0).toUpperCase().startsWith("QUESTW"))
			commands.add("WON");

		if((commands.size()>1)&&(commands.get(commands.size()-1).equalsIgnoreCase("WON")))
		{
			final String msg=this.getQuestsWonList(mob.Name(), L("you are"));
			if(!mob.isMonster())
				mob.tell(msg);
		}
		else
		if((commands.size()==2)&&(commands.get(1).equalsIgnoreCase("OFFERED"))) // Q7
		{
			final List<Quest> offered=QuestRecords.offeredTo(mob);
			if(offered.size()==0)
				mob.tell(L("No quest offered to you is open to you now."));
			else
			{
				final StringBuilder msg=new StringBuilder(L("^HQuests offered to you, and open to you still:^?^N\n\r"));
				for(int i=0;i<offered.size();i++)
				{
					final Quest Q=offered.get(i);
					msg.append((i+1)+") "+((Q.displayName().trim().length()>0)?Q.displayName():Q.name())+"^N\n\r");
				}
				msg.append(L("^HEnter QUESTS ACCEPT [NUMBER] to take one on.^N"));
				mob.tell(msg.toString());
			}
		}
		else
		if((commands.size()>2)&&(commands.get(1).equalsIgnoreCase("ACCEPT"))) // Q7
		{
			final String rest=CMParms.combine(commands,2);
			final Quest Q=findQuest(QuestRecords.offeredTo(mob),rest);
			if((Q==null)||(!QuestRecords.offeredTo(mob).contains(Q)))
			{
				mob.tell(L("No quest offered to you and open to you is called '@x1'.  Enter QUESTS OFFERED for a list.",rest));
				return false;
			}
			Q.acceptQuest(mob);
			if(QuestMarkers.accepted(mob,Q))
				mob.tell(L("You are now on the quest '@x1'.",(Q.displayName().trim().length()>0)?Q.displayName():Q.name()));
			else
				mob.tell(L("You could not take on the quest '@x1'.",(Q.displayName().trim().length()>0)?Q.displayName():Q.name()));
		}
		else
		if((commands.size()==2)&&(commands.get(1).equalsIgnoreCase("FAILED"))) // Q4
		{
			if(!mob.isMonster())
				mob.tell(getQuestsFailedList(mob.Name(), L("you are")));
		}
		else
		if((commands.size()>2)&&(commands.get(1).equalsIgnoreCase("DROP")))
		{
			ScriptingEngine foundS=null;
			for(final Enumeration<ScriptingEngine> e=mob.scripts();e.hasMoreElements();)
			{
				final ScriptingEngine SE=e.nextElement();
				if(SE!=null)
				{
					if((SE.defaultQuestName().length()>0)
					&&(CMLib.quests().findQuest(SE.defaultQuestName())==null))
						foundS=SE;
				}
			}
			if(foundS!=null)
			{
				if(foundS.isFunc("DO_DROP"))
				{
					final PhysicalAgent P=mob;
					final MOB M=mob;
					foundS.callFunc("DO_DROP", mob.Name(), new MPContext(P, M, mob, null, null, null, mob.Name(), null));
				}
				foundS.stepQuest(mob, mob, foundS.defaultQuestName());
				foundS.endQuest(mob, mob, foundS.defaultQuestName());
				mob.delScript(foundS);
			}
			foundS=null;

			final String rest=CMParms.combine(commands,2);
			final Quest Q=findScriptedQuest(mob.Name(), rest);
			if(Q==null)
			{
				mob.tell(L("There is no such quest as '@x1'.",rest));
				return false;
			}
			for(final Enumeration<ScriptingEngine> e=mob.scripts();e.hasMoreElements();)
			{
				final ScriptingEngine SE=e.nextElement();
				if(SE!=null)
				{
					if((SE.defaultQuestName().length()>0)
					&&(SE.defaultQuestName().equalsIgnoreCase(Q.name())))
						foundS=SE;
				}
			}
			if(foundS==null)
			{
				mob.tell(L("You have not accepted a quest called '@x1'.  Enter QUESTS for a list.",rest));
				return false;
			}
			if((!mob.isMonster()&&(mob.session().confirm(L("Drop the quest '@x1', are you sure (y/N)?",Q.name()),"N"))))
			{
				CMLib.coffeeTables().bump(Q,CoffeeTableRow.STAT_QUESTDROPPED);
				QuestRecords.ended(Q.name(),mob.Name(),QuestRecords.ABANDONED,"DROPPED"); // Q4
				foundS.stepQuest(mob, mob, foundS.defaultQuestName());
				foundS.endQuest(mob, mob, foundS.defaultQuestName());
				mob.delScript(foundS);
				ScriptEvents.quest(Q,"END",mob,"DROPPED");
				mob.tell(L("Quest dropped."));
				return false;
			}
		}
		else
		if((commands.size()==2)&&(commands.get(1).equalsIgnoreCase("LOG"))) // Q3: the journal's latest entries
		{
			final List<String> entries=QuestJournal.entries(mob,null,20);
			if(entries.size()==0)
				mob.tell(L("Your quest journal has nothing in it yet."));
			else
				mob.tell(L("^HYour quest journal, the latest last:^N\n\r")+String.join("^N\n\r",entries)+"^N");
		}
		else
		if(commands.size()==1)
		{
			final String msg=getQuestsDoingList(mob.Name(), L("you are"));
			if(!mob.isMonster())
				mob.tell(L("@x1\n\r^HEnter QUEST [QUEST NAME] for more information.^N^.",msg.toString()));

		}
		else
		{
			final boolean admin=CMSecurity.isAllowed(mob, mob.location(), CMSecurity.SecFlag.CMDQUESTS)
								||CMSecurity.isAllowed(mob, mob.location(), CMSecurity.SecFlag.CMDPLAYERS);
			final String rest=CMParms.combine(commands,1);
			if(admin)
			{
				if(CMLib.players().playerExistsAllHosts(rest))
				{
					final String name = CMStrings.capitalizeAndLower(rest);
					String msg=getQuestsWonList(name, name+" is");
					msg += "\n\r";
					msg += getQuestsDoingList(name, name+" is");
					if(!mob.isMonster())
						mob.tell(msg);
					return false;
				}
			}
			Quest Q=findScriptedQuest(mob.Name(), rest);
			if(Q==null)
				Q=findQuest(new XVector<Quest>(CMLib.quests().enumQuests()),rest);
			if(Q==null)
			{
				if(admin)
					mob.tell(L("There is no such quest or player as '@x1'.",rest));
				else
					mob.tell(L("There is no such quest as '@x1'.",rest));
				return false;
			}
			ScriptingEngine foundS=null;
			for(final Enumeration<ScriptingEngine> e=mob.scripts();e.hasMoreElements();)
			{
				final ScriptingEngine SE=e.nextElement();
				if(SE==null)
					continue;
				if((SE.defaultQuestName().length()>0)
				&&(SE.defaultQuestName().equalsIgnoreCase(Q.name())))
					foundS=SE;
			}
			MOB carrier=mob; // Q12: a SET SHARED PROFILE quest another of the profile's characters carries
			if((foundS==null)&&QuestSharing.isProfile(Q.name()))
			{
				for(final MOB P : QuestSharing.profileMates(mob))
				{
					for(final Enumeration<ScriptingEngine> e=P.scripts();e.hasMoreElements()&&(foundS==null);)
					{
						final ScriptingEngine SE=e.nextElement();
						if((SE!=null)&&(SE.defaultQuestName().length()>0)&&SE.defaultQuestName().equalsIgnoreCase(Q.name()))
						{
							foundS=SE;
							carrier=P;
						}
					}
					if(foundS!=null)
						break;
				}
			}
			if(foundS==null)
			{
				if(admin)
				{
					final Map<String,Long> winners = Q.getWinners();
					final StringBuffer msg=new StringBuffer(L("^HWinners of Quest '@x1':^?^N\n\r",Q.name()));
					for(final String name : winners.keySet())
					{
						final String time = CMLib.time().date2String(Q.whenLastWon(name).longValue());
						msg.append(CMStrings.padRight(time,20)+name+"^N\n\r");
					}
					if(mob.session()!=null)
						mob.session().colorOnlyPrintln(msg.toString());
					return true;
				}
				else
				{
					mob.tell(L("You have not accepted a quest called '@x1'.  Enter QUESTS for a list.",rest));
					return false;
				}
			}
			String name=Q.displayName().trim().length()>0?Q.displayName():Q.name();
			if(!Q.name().equals(name))
				name+=" ("+Q.name()+")";
			if(carrier!=mob) // Q12
				name+=" ("+L("carried by @x1",carrier.Name())+")";
			mob.tell(L("^HQuest Information: ^w@x1^N",name));
			String instructions = Q.instructions();
			if((instructions==null)||(instructions.length()==0))
				instructions=foundS.getVar("*","INSTRUCTIONS");
			if((instructions==null)||(instructions.length()==0))
				instructions=Q.isStat("INSTRUCTIONS")?Q.getStat("INSTRUCTIONS"):null;
			if((instructions==null)||(instructions.length()==0))
				instructions=L("No further information available.");
			String timeRemaining=foundS.getVar("*","TIME_REMAINING");
			if((timeRemaining!=null)&&(timeRemaining.length()>0))
			{
				final String timeRemainingType=foundS.getVar("*","TIME_REMAINING_TYPE");
				if(((timeRemainingType.equalsIgnoreCase("TICKS")||(timeRemainingType.length()==0))
				&&(CMath.isInteger(timeRemaining))))
					timeRemaining=QuestJournal.timeLeft(CMath.s_int(timeRemaining)); // Q3: on the game clock
				else
				if(timeRemainingType.length()>0)
					timeRemaining+=" "+timeRemainingType;
			}
			if(QuestTimers.ticksLeft(mob,Q.name())>=0) // Q11: their own deadline, over a hand-set one
				timeRemaining=QuestJournal.timeLeft(ticksLeftOf(mob,Q));
			final String progress=foundS.getVar("*","PROGRESS");
			mob.tell("^w"+instructions+"^N");
			if((timeRemaining!=null)&&(timeRemaining.length()>0))
				mob.tell(L("\n\r^yTime Remaining: ^w@x1^N",timeRemaining));
			if((progress!=null)&&(progress.length()>0))
				mob.tell(L("\n\r^yProgress: ^w@x1^N",progress));
			if(Q.getStat("REWARDS").length()>0) // Q10
				mob.tell(L("\n\r^yReward: ^w@x1^N",QuestRewards.describe(Q.getStat("REWARDS"))));
			if(((timeRemaining==null)||(timeRemaining.length()==0))&&(ticksLeftOf(mob,Q)>=0)) // Q3
				mob.tell(L("\n\r^yTime Remaining: ^w@x1^N",QuestJournal.timeLeft(ticksLeftOf(mob,Q))));
			final List<String> objectives=objectiveLines(carrier,Q);
			if(objectives.size()>0)
				mob.tell(L("\n\r^yObjectives:^N\n\r")+"  "+String.join("^N\n\r  ",objectives)+"^N");
			final List<String> history=QuestJournal.entries(mob,Q.name(),50);
			if(history.size()>0)
				mob.tell(L("\n\r^yHistory:^N\n\r")+"  "+String.join("^N\n\r  ",history)+"^N");
			final List<String> carried=new ArrayList<String>(); // Q8: what is bound to it
			for(final Item I : QuestItems.of(mob,Q.name()))
				carried.add(I.name(mob));
			if(carried.size()>0)
				mob.tell(L("\n\r^yQuest items: ^w@x1^N",String.join(", ",carried)));
		}
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

}
