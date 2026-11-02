package com.planet_ink.coffee_mud.Common;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor.Expire;
import com.planet_ink.coffee_mud.core.exceptions.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMClass.CMObjectType;
import com.planet_ink.coffee_mud.core.CMSecurity.DbgFlag;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.CMSecurity.SecFlag;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.Area.State;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.PrideStats.PrideStat;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.SubScript;
import com.planet_ink.coffee_mud.Common.interfaces.Session.InputCallback;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock.TimePeriod;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary.Achievement;
import com.planet_ink.coffee_mud.Libraries.interfaces.ChannelsLibrary.CMChannel;
import com.planet_ink.coffee_mud.Libraries.interfaces.DatabaseEngine.PlayerData;
import com.planet_ink.coffee_mud.Libraries.interfaces.ProtocolLibrary.LLMSession;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import org.mozilla.javascript.*;
import java.util.Hashtable; // not Rhino's

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class DefaultScriptingEngine implements ScriptingEngine
{
	@Override
	public String ID()
	{
		return "DefaultScriptingEngine";
	}

	@Override
	public String name()
	{
		return "Default Scripting Engine";
	}

	protected static final Map<String,Integer>	funcH	= new HashMap<String,Integer>();
	protected static final Map<String,Integer>	methH	= new HashMap<String,Integer>();
	protected static final Map<String,Integer>	progH	= new HashMap<String,Integer>();
	protected static final Map<String,Integer>	gstatH	= new HashMap<String,Integer>();

	protected static final Map<String, AtomicInteger>	counterCache= Collections.synchronizedMap(new HashMap<String, AtomicInteger>());
	protected static final Map<String, Pattern>			patterns	= Collections.synchronizedMap(new HashMap<String, Pattern>());

	protected boolean 				noDelay			 = CMSecurity.isDisabled(CMSecurity.DisFlag.SCRIPTABLEDELAY);

	protected String				scope			 = "";
	protected int					tickStatus		 = Tickable.STATUS_NOT;
	protected boolean				isSavable		 = true;
	protected boolean				alwaysTriggers	 = false;
	protected boolean				approvedScripts	 = false;
	protected boolean				multiTriggers	 = false;
	/** SC4: MPSETINTERNAL VARHOST INSTANCE: a bare $ code's variables are kept on the object */
	protected boolean				varHostInstance	 = false;

	protected MOB					lastToHurtMe	 = null;
	protected volatile Room			lastKnownLocation= null;
	protected Room					homeKnownLocation= null;
	protected Tickable				altStatusTickable= null;
	protected List<Integer>			oncesDone		 = Collections.synchronizedList(new ArrayList<Integer>());
	protected Map<Integer,int[]>	delayTargetTimes = Collections.synchronizedMap(new HashMap<Integer,int[]>());
	/** ST24: each EVERY_PROG's next time, in its area clock's hours, kept in the save */
	protected Map<Integer,Long>	everyNext = Collections.synchronizedMap(new HashMap<Integer,Long>());
	protected Map<Integer,Integer>	lastTimeProgsDone= Collections.synchronizedMap(new HashMap<Integer,Integer>());
	protected Map<Integer,Integer>	lastDayProgsDone = Collections.synchronizedMap(new HashMap<Integer,Integer>());
	protected Set<Integer>			registeredEvents = new HashSet<Integer>();
	protected volatile Environmental globalsHost	 = null;
	protected Map<Integer,Long>		noTrigger		 = Collections.synchronizedMap(new HashMap<Integer,Long>());
	protected MOB					backupMOB		 = null;
	protected CMMsg					lastMsg			 = null;
	protected Resources				resources		 = null;
	protected Environmental			lastLoaded		 = null;
	protected String				myScript		 = "";
	protected String				defaultQuestName = "";
	protected Quest					questCacheObj	 = null;
	protected String				scriptKey		 = null;
	protected boolean				runInPassiveAreas= true;
	protected boolean				debugBadScripts	 = false;
	protected List<ScriptableResponse>que			 = Collections.synchronizedList(new ArrayList<ScriptableResponse>());
	protected final AtomicInteger	recurseCounter	 = new AtomicInteger();
	protected volatile Object		cachedRef		 = null;
	protected boolean				runWithoutPCs	 = true;
	protected LLMSession			llmSession		 = null;
	protected Map<String,String>	miscArgVars		 = new Hashtable<String,String>(1);
	protected volatile String		pendingState	 = null;
	private final Object			stateLock		 = new Object();

	protected final PrioritizingLimitedMap<String,Room> roomFinder=new PrioritizingLimitedMap<String,Room>(5,30*60000L,60*60000L,20);

	public DefaultScriptingEngine()
	{
		super();
		//CMClass.bumpCounter(this,CMClass.CMObjectType.COMMON);//removed for mem & perf
		debugBadScripts=CMSecurity.isDebugging(CMSecurity.DbgFlag.BADSCRIPTS);
		Resources.instance();
		resources = globalResources();
	}

	public static enum Signs
	{
		EQUL("=","=="),
		/** Index and equate for &gt;= */
		GTEQ(">=","=>"),
		/** Index and equate for &gt; */
		GRAT(">"),
		/** Index and equate for &lt; */
		LEST("<"),
		/** Index and equate for &lt;= */
		LTEQ("<=","=<"),
		/** Index and equate for != */
		NTEQ("!=", "<>"),
		/** Index and equate for != */
		IN(".in.",".IN.",".In.")
		;
		private final String[] signs;
		private static final Map<String, Signs> signMap = new HashMap<String, Signs>();
		private Signs(final String... sign)
		{
			signs = sign;
		}
		public static Signs getSign(final String str)
		{
			if(signMap.size()==0)
			{
				for (final Signs s : Signs.values())
				{
					for (final String sign : s.signs)
						signMap.put(sign, s);
				}
			}
			return signMap.get(str);
		}
	}

	/**
	 * Connector enum for logical connectors
	 */
	public static enum Connector
	{
		/** index and equate for logical connector AND*/
		AND,
		/** index and equate for logical connector OR */
		OR,
		/** index and equate for logical connector NOT */
		NOT,
		/** index and equate for logical connector ANDNOT */
		ANDNOT,
		/** index and equate for logical connector ORNOT */
		ORNOT
		;
		public static Connector getConnector(final String str)
		{
			if(str == null) return null;
			return (Connector)CMath.s_valueOf(Connector.class, str.toUpperCase().trim());
		}
		public static boolean isConnector(final String str)
		{
			return getConnector(str) != null;
		}
		/** A table to describe what happens when connectors are found sequentially (and and not or not and and, etc) */
		public final static Connector[][] Map=
		{
			{Connector.AND,Connector.OR,Connector.ANDNOT,Connector.AND,Connector.ORNOT}, //and
			{Connector.OR,Connector.OR,Connector.ORNOT,Connector.ORNOT,Connector.ORNOT}, //or
			{Connector.ANDNOT,Connector.ORNOT,Connector.AND,Connector.AND,Connector.OR}, //not
			{Connector.ANDNOT,Connector.ORNOT,Connector.AND,Connector.AND,Connector.ORNOT}, //andnot
			{Connector.ORNOT,Connector.ORNOT,Connector.OR,Connector.ORNOT,Connector.ORNOT}, //ornot
		};
	}

	/**
	 * An object that holds the information about an event until it is
	 * time to execute its associated script.
	 * @author Bo Zimmerman
	 */
	private static class ScriptableResponse
	{
		/** The trigger code being executed */
		public int			triggerCode	= 0;
		/** The number of ticks to delay before triggering */
		private int			tickDelay	= 0;
		/** (host) the object being scripted */
		public MPContext	ctx;
		/** (script) the actual script to execute for this event */
		public SubScript	scr;
		/** the hash code for this whole thing */
		private Integer		hashCode	= null;
		/** when this event was queued */
		private final long	queuedAt	= GameTime.now();

		/**
		 * Create an event response object
		 * @param triggerCode the triggerCode for the event
		 * @param ctx the context variables
		 * @param script the actual script to execute for this event
		 * @param ticks how many ticks to wait before executing the script
		 */
		public ScriptableResponse(final int triggerCode, final MPContext ctx, final SubScript script, final int ticks)
		{
			this.triggerCode = triggerCode;
			this.ctx = ctx;
			scr=script;
			tickDelay=ticks;
		}

		/**
		 * Decrements the internal tick counter and returns true if
		 * the tick counter has dropped to or below 0
		 * @return true if its time to execute
		 */
		public boolean checkTimeToExecute()
		{
			return ((--tickDelay) <= 0);
		}

		/**
		 * Return a description of this event, typically for debugging.
		 * @return a description of this event, typically for debugging.
		 */
		@Override
		public String toString()
		{
			final StringBuilder str=new StringBuilder("");
			str.append("tc:").append(triggerCode).append("/");
			str.append("h:").append(ctx.scripted==null?"null":ctx.scripted.name()).append("/");
			str.append("s:").append(ctx.source==null?"null":ctx.source.name()).append("/");
			str.append("t:").append(ctx.target==null?"null":ctx.target.name()).append("/");
			str.append("m:").append(ctx.monster==null?"null":ctx.monster.name()).append("/");
			str.append("pi:").append(ctx.primaryItem==null?"null":ctx.primaryItem.name()).append("/");
			str.append("si:").append(ctx.secondaryItem==null?"null":ctx.secondaryItem.name()).append("/");
			str.append("sc:").append(scr==null?"null":scr.toString()).append("/");
			str.append("td:").append(tickDelay).append("/");
			str.append("m:").append(ctx.msg).append("");
			return str.toString();
		}

		@Override
		public int hashCode()
		{
			if(this.hashCode != null)
				return this.hashCode.intValue();
			int hc = triggerCode;
			hc ^= (ctx.scripted == null)?0:ctx.scripted.hashCode();
			hc ^= (ctx.source == null)?0:ctx.source.hashCode();
			hc ^= (ctx.target == null)?0:ctx.target.hashCode();
			hc ^= (ctx.monster == null)?0:ctx.monster.hashCode();
			hc ^= (ctx.primaryItem == null)?0:ctx.primaryItem.hashCode();
			hc ^= (ctx.secondaryItem == null)?0:ctx.secondaryItem.hashCode();
			hc ^= (scr == null)?0:scr.hashCode();
			hc ^= (ctx.msg == null)?0:ctx.msg.hashCode();
			hashCode = Integer.valueOf(hc);
			return hc;
		}
	}

	private static class SubScriptImpl extends SubScript
	{
		private static final long serialVersionUID = 9212002543627348955L;

		public SubScriptImpl(final SubScript parent)
		{
			super();
			this.parent = parent;
		}

		private Integer				triggerCode	= null;
		private String				triggerLine	= null;
		private String[]			triggerBits	= null;
		private final SubScript		parent;
		private final Set<String>	flags		= Collections.synchronizedSet(new TreeSet<String>());

		@Override
		public boolean add(final ScriptLn line)
		{
			if(line == null)
				Log.errOut("Tried to add NULL line!",new Exception());
			return super.add(line);
		}

		@Override
		public int getTriggerCode()
		{
			if(triggerCode != null)
				return triggerCode.intValue();
			if(size()==0)
				return 0;
			final Triad<String,String[],Object> t=get(0);
			final String trigger=t.first;
			final String[] ttrigger=t.second;
			if((ttrigger!=null)&&(ttrigger.length>0))
				triggerCode=progH.get(ttrigger[0]);
			else
			{
				final int x=trigger.indexOf(' ');
				if(x<0)
				{
					final String utrigger=trigger.toUpperCase().trim();
					t.first=utrigger.trim();
					triggerCode=progH.get(utrigger);
				}
				else
				{
					final String utrigger=trigger.substring(0,x).toUpperCase().trim();
					t.first=(utrigger+t.first.substring(x)).trim();
					triggerCode=progH.get(utrigger);
				}
			}
			if(triggerCode==null)
				triggerCode=Integer.valueOf(0);
			return triggerCode.intValue();
		}

		@Override
		public String getTriggerLine()
		{
			if(triggerLine != null)
				return triggerLine;
			if(triggerCode == null)
				getTriggerCode();
			if(triggerLine == null)
			{
				if(size()>0)
					triggerLine = get(0).first.toUpperCase().trim();
				else
					triggerLine="";
			}
			return triggerLine;
		}

		@Override
		public String[] getTriggerBits()
		{
			if(triggerBits != null)
				return triggerBits;
			triggerBits=CMParms.getCleanBits(getTriggerLine());
			return triggerBits;
		}

		@Override
		public String[] getTriggerArgs()
		{
			if(size()>0)
				return get(0).second;
			return null;
		}

		@Override
		public void setFlag(final String flag)
		{
			if(flag == null)
				return;
			flags.add(flag.toUpperCase().trim());
			if(parent != null)
				parent.setFlag(flag);
		}

		@Override
		public boolean isFlagSet(final String flag)
		{
			if(flag == null)
				return false;
			return flags.contains(flag.toUpperCase().trim());
		}
	}

	@Override
	public boolean isSavable()
	{
		return isSavable;
	}

	@Override
	public void setSavable(final boolean truefalse)
	{
		isSavable = truefalse;
	}

	@Override
	public String defaultQuestName()
	{
		return defaultQuestName;
	}

	protected Quest defaultQuest()
	{
		final Quest questCacheObj;
		final String defaultQuestName;
		synchronized(this)
		{
			questCacheObj=this.questCacheObj;
			defaultQuestName=this.defaultQuestName;
		}
		if(defaultQuestName.length()==0)
			return null;
		if((questCacheObj!=null)
		&&(questCacheObj.name().equals(defaultQuestName)))
			return questCacheObj;
		this.questCacheObj=null;
		return CMLib.quests().fetchQuest(defaultQuestName);
	}

	@Override
	public void setVarScope(final String newScope)
	{
		if((newScope==null)||(newScope.trim().length()==0)||newScope.equalsIgnoreCase("GLOBAL"))
		{
			// the global scope is the store every unscoped script uses
			// (it once fell through to a separate store named VARSCOPE-)
			scope="";
			resources = globalResources();
			return;
		}
		else
			scope=newScope.toUpperCase().trim();
		if(scope.equalsIgnoreCase("*")||scope.equals("INDIVIDUAL"))
			resources = Resources.newResources();
		else
		{
			resources=(Resources)Resources.getResource("VARSCOPE-"+scope);
			if(resources==null)
			{
				resources = Resources.newResources();
				Resources.submitResource("VARSCOPE-"+scope,resources);
			}
			if(cachedRef==this)
				bumpUpCache(scope);
		}
	}

	@Override
	public String getVarScope()
	{
		return scope;
	}

	@Override
	public void preApproveScripts()
	{
		this.approvedScripts = true;
	}

	@Override
	public String getLocalVarXML()
	{
		if((scope==null)||(scope.length()==0))
			return "";
		final StringBuffer str=new StringBuffer("");
		for(final Iterator<String> k = resources._findResourceKeys("SCRIPTVAR-");k.hasNext();)
		{
			final String key=k.next();
			if(key.startsWith("SCRIPTVAR-"))
			{
				str.append("<"+key.substring(10)+">");
				@SuppressWarnings("unchecked")
				final Map<String,String> H=(Map<String,String>)resources._getResource(key);
				for(final String vn : H.keySet())
				{
					final String val=H.get(vn);
					str.append("<"+vn+">"+CMLib.xml().parseOutAngleBrackets(val)+"</"+vn+">");
				}
				str.append("</"+key.substring(10)+">");
			}
		}
		return str.toString();
	}

	@Override
	public void setLocalVarXML(final String xml)
	{
		for(final Iterator<String> k = Resources.findResourceKeys("SCRIPTVAR-");k.hasNext();)
		{
			final String key=k.next();
			if(key.startsWith("SCRIPTVAR-"))
				resources._removeResource(key);
		}
		final List<XMLTag> V=CMLib.xml().parseAllXML(xml);
		for(int v=0;v<V.size();v++)
		{
			final XMLTag piece=V.get(v);
			if((piece.contents()!=null)&&(piece.contents().size()>0))
			{
				final String kkey="SCRIPTVAR-"+piece.tag();
				final Map<String,String> H=Collections.synchronizedMap(new HashMap<String,String>());
				for(int c=0;c<piece.contents().size();c++)
				{
					final XMLTag piece2=piece.contents().get(c);
					H.put(piece2.tag(),piece2.value());
				}
				resources._submitResource(kkey,H);
			}
		}
	}

	/**
	 * Q3: a quest's name as a script gives it: * for the script's own.
	 * @param word the word
	 * @return the quest's name
	 */
	private String questNameFor(final String word)
	{
		final Quest Q=getQuest(word);
		if(Q!=null)
			return Q.name();
		if(word.equals("*")&&(defaultQuestName.length()>0))
			return defaultQuestName;
		final Quest Q2=CMLib.quests().fetchQuest(word);
		return (Q2!=null)?Q2.name():word;
	}

	private Quest getQuest(final String named)
	{
		if((defaultQuestName.length()>0)
		&&(named.equals("*")||named.equalsIgnoreCase(defaultQuestName)))
			return defaultQuest();

		Quest questCacheObj;
		synchronized(this)
		{
			questCacheObj=this.questCacheObj;
		}
		if((questCacheObj!=null)
		&&(questCacheObj.name().equals(named))
		&&(questCacheObj.running()))
			return questCacheObj;

		Quest Q=null;
		for(int i=0;i<CMLib.quests().numQuests();i++)
		{
			try
			{
				Q = CMLib.quests().fetchQuest(i);
			}
			catch (final Exception e)
			{
			}
			if(Q!=null)
			{
				if(Q.name().equalsIgnoreCase(named))
				{
					if(Q.running())
						return Q;
				}
			}
		}
		synchronized(this)
		{
			questCacheObj=this.questCacheObj;
		}
		if((questCacheObj!=null)
		&&(questCacheObj.name().equals(named)))
			return questCacheObj;
		return CMLib.quests().fetchQuest(named);
	}

	@Override
	public int getTickStatus()
	{
		final Tickable T=altStatusTickable;
		if(T!=null)
			return T.getTickStatus();
		return tickStatus;
	}

	@Override
	public void registerDefaultQuest(final Object quest)
	{
		final String qName;
		if(quest instanceof String)
			qName=(String)quest;
		else
		if(quest instanceof CMObject)
			qName=((Quest)quest).name();
		else
			return;

		if(quest instanceof Quest)
			this.questCacheObj=(Quest)quest;

		if((qName==null)||(qName.trim().length()==0))
		{
			questCacheObj=null;
			defaultQuestName="";
		}
		else
		{
			defaultQuestName=qName.trim();
			if(cachedRef==this)
				bumpUpCache(defaultQuestName);
			if((questCacheObj!=null)
			&&(!defaultQuestName.equalsIgnoreCase(questCacheObj.name())))
				questCacheObj=null;
		}
	}

	@Override
	public CMObject newInstance()
	{
		try
		{
			return this.getClass().getDeclaredConstructor().newInstance();
		}
		catch(final Exception e)
		{
			Log.errOut(ID(),e);
		}
		return new DefaultScriptingEngine();
	}

	@Override
	public CMObject copyOf()
	{
		try
		{
			final DefaultScriptingEngine S=(DefaultScriptingEngine)this.clone();
			//CMClass.bumpCounter(S,CMClass.CMObjectType.COMMON);//removed for mem & perf
			S.reset();
			S.pendingState=null; // a copy starts fresh, as its run state does
			return S;
		}
		catch(final CloneNotSupportedException e)
		{
			return new DefaultScriptingEngine();
		}
	}

	/*
	protected void finalize()
	{
		CMClass.unbumpCounter(this, CMClass.CMObjectType.COMMON);
	}// removed for mem & perf
	*/

	/*
	 * c=clean bit, r=pastbitclean, p=pastbit, s=remaining clean bits, t=trigger
	 */
	protected String[] parseBits(final MPContext context, final String instructions)
	{
		final String line=context.script.get(context.line).first;
		final String[] newLine=parseBits(line,instructions);
		context.script.get(context.line).second=newLine;
		return newLine;
	}

	protected String[] parseBits(final SubScript script, final int i, final String instructions)
	{
		final String line=script.get(i).first;
		final String[] newLine=parseBits(line,instructions);
		script.get(i).second=newLine;
		return newLine;
	}

	protected String[] parseSpecial3PartEval(final String[][] eval, final int t)
	{
		String[] tt=eval[0];
		final String funcParms=tt[t];
		final String[] tryTT=parseBits(funcParms,"ccr");
		if(Signs.getSign(tryTT[1])!=null)
			tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
		else
		{
			String[] parsed=null;
			if(CMParms.cleanBit(funcParms).equals(funcParms))
				parsed=parseBits("'"+funcParms+"' . .","cr");
			else
				parsed=parseBits(funcParms+" . .","cr");
			tt=CMParms.insertStringArray(tt,parsed,t);
			eval[0]=tt;
		}
		return tt;
	}

	/**
	 * Given an unparsed mobprog script line, and instructions on how to parse
	 * the line is given, this will return the parsed version of the line.
	 * The instruction codes are:  c=clean bit, r=pastbitclean, p=pastbit,
	 * s=remaining clean bits, t=trigger.  Capital letters are the
	 * UPPERCASED versions.
	 *
	 * @param line the unparsed mobprog script line
	 * @param instructions the instructions on how to parse
	 * @return the parsed row
	 */
	protected String[] parseBits(String line, final String instructions)
	{
		String[] newLine=new String[instructions.length()];
		for(int i=0;i<instructions.length();i++)
		{
			switch(instructions.charAt(i))
			{
			case 'c':
				newLine[i] = CMParms.getCleanBit(line, i);
				break;
			case 'C':
				newLine[i] = CMParms.getCleanBit(line, i).toUpperCase().trim();
				break;
			case 'r':
				newLine[i] = CMParms.getPastBitClean(line, i - 1);
				break;
			case 'R':
				newLine[i] = CMParms.getPastBitClean(line, i - 1).toUpperCase().trim();
				break;
			case 'p':
				newLine[i] = CMParms.getPastBit(line, i - 1);
				break;
			case 'P':
				newLine[i] = CMParms.getPastBit(line, i - 1).toUpperCase().trim();
				break;
			case 'S':
				line = line.toUpperCase();
				//$FALL-THROUGH$
			case 's':
			{
				final String s = CMParms.getPastBit(line, i - 1);
				final int numBits = CMParms.numBits(s);
				final String[] newNewLine = new String[newLine.length - 1 + numBits];
				for (int x = 0; x < i; x++)
					newNewLine[x] = newLine[x];
				for (int x = 0; x < numBits; x++)
					newNewLine[i + x] = CMParms.getCleanBit(s, i - 1);
				newLine = newNewLine;
				i = instructions.length();
				break;
			}
			case 'T':
				line = line.toUpperCase();
				//$FALL-THROUGH$
			case 't':
			{
				final String s = CMParms.getPastBit(line, i - 1);
				final String[] newNewLine;
				if (CMParms.getCleanBit(s, 0).equalsIgnoreCase("P"))
				{
					newNewLine = new String[newLine.length + 1];
					for (int x = 0; x < i; x++)
						newNewLine[x] = newLine[x];
					newNewLine[i] = "P";
					newNewLine[i + 1] = CMParms.getPastBitClean(s, 0);
				}
				else
				{

					final int numNewBits = (s.trim().length() == 0) ? 1 : CMParms.numBits(s);
					newNewLine = new String[newLine.length - 1 + numNewBits];
					for (int x = 0; x < i; x++)
						newNewLine[x] = newLine[x];
					for (int x = 0; x < numNewBits; x++)
						newNewLine[i + x] = CMParms.getCleanBit(s, x);
				}
				newLine = newNewLine;
				i = instructions.length();
				break;
			}
			}
		}
		return newLine;
	}

	/**
	 * Given a String grid where the first column is the unparsed lines of a mobprog
	 * script, and the line number to parse is given, and instructions on how to parse
	 * the line is given, this will replace the row for that line with the parsed
	 * version of the line.  The instruction codes are:  c=clean bit,
	 * r=pastbitclean, p=pastbit, s=remaining clean bits, t=trigger.  Capital letters
	 * are the UPPERCASED versions.
	 *
	 * @param oldBits column 0 is the rows of the mobprog, with at least one unparsed
	 * @param start the row to parse
	 * @param instructions the instructions on how to parse
	 * @return the parsed row
	 */
	protected String[] parseBits(final String[][] oldBits, final int start, final String instructions)
	{
		final String[] tt=oldBits[0];
		final String parseMe=tt[start];
		final String[] parsed=parseBits(parseMe,instructions);
		if(parsed.length==1)
		{
			tt[start]=parsed[0];
			return tt;
		}
		final String[] newLine=CMParms.insertStringArray(tt,parsed,start);
		oldBits[0]=newLine;
		return newLine;
	}

	protected boolean stepEndQuest(final PhysicalAgent hostObj, final MOB mob, final String quest, final int reqCode)
	{
		boolean found=false;
		if(mob!=null)
		{
			final List<SubScript> scripts=getScripts(hostObj);
			if(!mob.amDead())
				confirmLastKnownLocation(mob,mob);
			for(int v=0;v<scripts.size();v++)
			{
				final SubScript script=scripts.get(v);
				if(script.size()>0)
				{
					if((script.getTriggerCode()==13) //questtimeprog quest_time_prog
					&&(!oncesDone.contains(Integer.valueOf(v))))
					{
						String[] tt=script.getTriggerArgs();
						if(tt==null)
							tt=parseBits(script,0,"CCC");
						if((tt!=null)
						&&((tt[1].equals(quest)||(tt[1].equals("*")))))
						{
							final int tmCode = CMath.s_int(tt[2]);
							if((tmCode == reqCode)
							||((reqCode<-1)&&(tmCode<-1)))
							{
								oncesDone.add(Integer.valueOf(v));
								execute(new MPContext(hostObj,mob,mob,mob,null,null,null, null).push(script));
								found = true;
							}
						}
					}
				}
			}
		}
		return found;
	}

	@Override
	public boolean endQuest(final PhysicalAgent hostObj, final MOB mob, final String quest)
	{
		return stepEndQuest(hostObj, mob, quest, -2);
	}

	@Override
	public boolean stepQuest(final PhysicalAgent hostObj, final MOB mob, final String quest)
	{
		return stepEndQuest(hostObj, mob, quest, -1);
	}

	@Override
	public List<String> externalFiles()
	{
		final List<String> xmlfiles=Collections.synchronizedList(new ArrayList<String>());
		parseLoads(null, getScript(), 0, xmlfiles, null);
		return xmlfiles;
	}

	protected String getVarHost(final Environmental E, String rawHost, final MPContext ctx)
	{
		// SC4: @code is the object itself, its variables kept on it
		if((rawHost.length()>1)&&(rawHost.charAt(0)=='@'))
		{
			final Environmental O=getArgumentItem(rawHost.substring(1),ctx);
			if(O instanceof Room)
				return CMLib.map().getExtendedRoomID((Room)O);
			if(O instanceof Area)
				return O.Name();
			if(O instanceof Physical)
				return ObjectVars.hostOf((Physical)O);
		}
		// MPSETINTERNAL VARHOST INSTANCE: a bare $ code's are kept on the object too
		if(varHostInstance&&rawHost.startsWith("$")&&((E instanceof MOB)||(E instanceof Item)))
			return ObjectVars.hostOf((Physical)E);
		if(!rawHost.equals("*"))
		{
			if(E==null)
				rawHost=varify(ctx,rawHost);
			else
			if(E instanceof Room)
				rawHost=CMLib.map().getExtendedRoomID((Room)E);
			else
				rawHost=E.Name();
		}
		return rawHost;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean isVar(final String host, String var)
	{
		if(ObjectVars.isHost(host)) // SC4
		{
			final Map<String,String> O=ObjectVars.vars(host);
			return (O!=null)&&O.containsKey(var.toUpperCase());
		}
		if(host.equalsIgnoreCase("*"))
		{
			String val=null;
			Map<String,String> H=null;
			String key=null;
			var=var.toUpperCase();
			for(final Iterator<String> k = resources._findResourceKeys("SCRIPTVAR-");k.hasNext();)
			{
				key=k.next();
				if(key.startsWith("SCRIPTVAR-"))
				{
					H=(Map<String,String>)resources._getResource(key);
					val=H.get(var);
					if(val!=null)
						return true;
				}
			}
			return false;
		}
		final Map<String,String> H=(Map<String,String>)resources._getResource("SCRIPTVAR-"+host);
		String val=null;
		if(H!=null)
			val=H.get(var.toUpperCase());
		return (val!=null);
	}

	public String getVar(final Environmental E, final String rawHost, final String var, final MPContext ctx)
	{
		return getVar(getVarHost(E,rawHost,ctx),var);
	}

	protected Resources globalResources()
	{
		if(!Resources.isResource("VARSCOPE-*GLOBAL*"))
			Resources.submitResource("VARSCOPE-*GLOBAL*", Resources.newResources());
		return (Resources)Resources.getResource("VARSCOPE-*GLOBAL*");
	}

	@Override
	public String getVar(final String host, final String var)
	{
		if(ObjectVars.isHost(host)) // SC4: kept on the object
			return ObjectVars.get(host,var);
		String varVal = getVar(resources,host,var,null);
		if(varVal != null)
			return varVal;
		if((this.defaultQuestName!=null)&&(this.defaultQuestName.length()>0))
		{
			final Resources questResources=(Resources)Resources.getResource("VARSCOPE-"+this.defaultQuestName);
			if((questResources != null)&&(resources!=questResources))
			{
				varVal = getVar(questResources,host,var,null);
				if(varVal != null)
					return varVal;
			}
		}
		final Resources globalResources = globalResources();
		if(resources == globalResources)
			return "";
		return getVar(globalResources,host,var,"");
	}

	@SuppressWarnings("unchecked")
	public String getVar(final Resources resources, final String host, String var, final String defaultVal)
	{
		if(host.equalsIgnoreCase("*"))
		{
			if(var.equals("COFFEEMUD_SYSTEM_INTERNAL_NONFILENAME_SCRIPT"))
			{
				final StringBuffer str=new StringBuffer("");
				parseLoads(null,getScript(),0,null,str);
				return str.toString();
			}
			String val=null;
			Map<String,String> H=null;
			String key=null;
			var=var.toUpperCase();
			for(final Iterator<String> k = resources._findResourceKeys("SCRIPTVAR-");k.hasNext();)
			{
				key=k.next();
				if(key.startsWith("SCRIPTVAR-"))
				{
					H=(Map<String,String>)resources._getResource(key);
					val=H.get(var);
					if(val!=null)
						return val;
				}
			}
			return defaultVal;
		}
		Resources.instance();
		final Map<String,String> H=(Map<String,String>)resources._getResource("SCRIPTVAR-"+host);
		String val=null;
		if(H!=null)
			val=H.get(var.toUpperCase());
		else
		if((defaultQuestName!=null)
		&&(defaultQuestName.length()>0))
		{
			final MOB M=CMLib.players().getPlayerAllHosts(host);
			if(M!=null)
			{
				for(final Enumeration<ScriptingEngine> e=M.scripts();e.hasMoreElements();)
				{
					final ScriptingEngine SE=e.nextElement();
					if((SE!=null)
					&&(SE!=this)
					&&(defaultQuestName.equalsIgnoreCase(SE.defaultQuestName()))
					&&(SE.isVar(host,var)))
						return SE.getVar(host,var);
				}
			}
		}
		if(val==null)
			return defaultVal;
		return val;
	}

	private StringBuffer getResourceFileData(final Environmental scripted, final String named)
	{
		// JS5: world:path is a file of the world folder, as LOAD=world:scripts/guard.script
		if(named.toLowerCase().startsWith("world:"))
		{
			final String path=named.substring(6).trim().replace('\\','/');
			final CMFile WF=new CMFile("/world/"+path,null,CMFile.FLAG_LOGERRORS);
			if((path.indexOf("..")<0)&&WF.exists()&&WF.canRead())
				return WF.text();
			this.logError(scripted, "LOAD", "NOREAD", named);
			return new StringBuffer();
		}
		final Quest Q=getQuest("*");
		if(Q!=null)
			return Q.getResourceFileData(named, true);
		final CMFile F = new CMFile(Resources.makeFileResourceName(named),null,CMFile.FLAG_LOGERRORS);
		if((F.exists())&&(F.canRead()))
			return F.text();
		@SuppressWarnings("unchecked")
		final List<SubScript> script=(List<SubScript>)Resources.getResource(getScriptResourceKey());
		if((script!=null)&&(script.size()>0))
		{
			for(int i=0;i<script.size();i++)
			{
				if((script.get(i).getTriggerCode()==58) // the <?xml section
				&&(script.get(i).size()>0)
				&&(script.get(i).get(0).third instanceof List))
				{
					@SuppressWarnings("unchecked")
					final List<XMLTag> tags = (List<XMLTag>)script.get(i).get(0).third;
					for(final XMLTag tag : tags)
					{
						if(tag.tag().equalsIgnoreCase("FILE")
						&&(named.equalsIgnoreCase(CMLib.xml().getValFromPieces(tag.contents(), "NAME", ""))))
							return new StringBuffer(CMLib.xml().getValFromPieces(tag.contents(), "DATA", ""));
					}
				}
			}
		}
		this.logError(scripted, "LOAD", "NOREAD", named);
		return new StringBuffer();
	}

	@Override
	public String getScript()
	{
		return myScript;
	}

	public void reset()
	{
		que = Collections.synchronizedList(new ArrayList<ScriptableResponse>());
		lastToHurtMe = null;
		lastKnownLocation= null;
		homeKnownLocation= null;
		altStatusTickable= null;
		oncesDone = Collections.synchronizedList(new ArrayList<Integer>());
		delayTargetTimes = Collections.synchronizedMap(new HashMap<Integer,int[]>());
		everyNext = Collections.synchronizedMap(new HashMap<Integer,Long>());
		lastTimeProgsDone= Collections.synchronizedMap(new HashMap<Integer,Integer>());
		lastDayProgsDone = Collections.synchronizedMap(new HashMap<Integer,Integer>());
		registeredEvents = new HashSet<Integer>();
		globalsHost = null;
		noTrigger = Collections.synchronizedMap(new HashMap<Integer,Long>());
		backupMOB = null;
		lastMsg = null;
		bumpUpCache();
	}

	@Override
	public void setScript(String newParms)
	{
		newParms=swapOutsideScripts(newParms,'\'','`');
		if(newParms.startsWith("+"))
		{
			final String superParms=getScript();
			Resources.removeResource(getScriptResourceKey());
			newParms=superParms+";"+newParms.substring(1);
		}
		myScript=newParms;
		loadArgs=null; // SC13: read again from the new text
		if(myScript.length()>100)
			scriptKey="PARSEDPRG: "+myScript.substring(0,100)+myScript.length()+myScript.hashCode();
		else
			scriptKey="PARSEDPRG: "+myScript;
		reset();
	}

	/**
	 * Replaces one character with another, except inside &lt;SCRIPT&gt; blocks.
	 * Scripts are kept with their apostrophes as backticks, and turned back
	 * when they are parsed; JavaScript keeps both as written, so that its
	 * template strings, which use backticks, survive.
	 * @param text the script text
	 * @param from the character to replace
	 * @param to what to replace it with
	 * @return the text
	 */
	protected static String swapOutsideScripts(final String text, final char from, final char to)
	{
		if(text.indexOf(from)<0)
			return text;
		final String upper=text.toUpperCase();
		final StringBuilder str=new StringBuilder(text.length());
		boolean inScript=false;
		for(int i=0;i<text.length();i++)
		{
			final char c=text.charAt(i);
			if(c=='<')
			{
				if((!inScript)&&upper.startsWith("<SCRIPT>",i))
					inScript=true;
				else
				if(inScript&&upper.startsWith("</SCRIPT>",i))
					inScript=false;
			}
			str.append(((c==from)&&(!inScript))?to:c);
		}
		return str.toString();
	}

	public boolean isFreeToBeTriggered(final Tickable affecting)
	{
		final boolean free;
		if(alwaysTriggers)
			free=CMLib.flags().canActAtAll(affecting);
		else
			free=CMLib.flags().canFreelyBehaveNormal(affecting);
		if((!free)&&ScriptTrace.isOn())
		{
			final Object host=ScriptTrace.handled();
			if((host!=null)&&ScriptTrace.traces(host,0))
				ScriptTrace.tell(host,"busy (asleep, fighting, bound or the like), so its triggers wait"+(alwaysTriggers?"":" (MPSETINTERNAL ACTIVETRIGGER TRUE lets them run anyway)"));
		}
		return free;
	}

	/**
	 * Rolls a trigger's chance, as a percentage, and tells SCRIPTTRACE (DT18).
	 * @param host the host
	 * @param script the trigger
	 * @param prcnt its chance
	 * @return true if it runs
	 */
	protected boolean chance(final PhysicalAgent host, final SubScript script, final int prcnt)
	{
		final int roll=CMLib.dice().rollPercentage();
		final boolean yes=roll<=prcnt; // 100 is always
		if(ScriptTrace.isOn()&&ScriptTrace.traces(host,script.getTriggerCode()))
			ScriptTrace.tell(host,script.getTriggerLine()+": rolled "+roll+" of "+prcnt+", "+(yes?"runs":"skipped"));
		return yes;
	}

	/** SC13: the LOAD= arguments of this engine's script, with its files' ARGDEFAULT values */
	protected volatile Map<String,String> loadArgs = null;

	/** N7: a generic behaviour's parameters, PARM(name), or null */
	protected volatile Map<String,String> parmValues = null;

	@Override
	public void setParmValues(final Map<String,String> values)
	{
		parmValues=values;
	}

	/** N10: the triggers whose queued responses reply to a player */
	protected static final Set<Integer> REPLY_TRIGGERS=new HashSet<Integer>();
	static
	{
		for(final String name : new String[]{"SPEECH_PROG","GIVE_PROG","BRIBE_PROG","SOCIAL_PROG","TALK_PROG"})
		{
			final int x=CMParms.indexOf(progs,name);
			if(x>=0)
				REPLY_TRIGGERS.add(Integer.valueOf(x+1));
		}
	}

	@Override
	public int runRepliesNow()
	{
		int n=0;
		for(int q=0;q<que.size();q++) // the oldest first
		{
			final ScriptableResponse SB;
			try
			{
				SB=que.get(q);
			}
			catch(final IndexOutOfBoundsException x)
			{
				break;
			}
			if((SB==null)||(!REPLY_TRIGGERS.contains(Integer.valueOf(SB.triggerCode))))
				continue;
			que.remove(q);
			q--;
			SB.ctx.script=SB.scr;
			SB.ctx.line=1;
			execute(SB.ctx);
			n++;
		}
		return n;
	}

	/**
	 * N7: a parameter: the generic behaviour's, else LOAD='s (SC13).
	 * @param E the scripted thing
	 * @param name its name
	 * @return its value, or null
	 */
	protected String parmValue(final Environmental E, final String name)
	{
		final Map<String,String> p=parmValues;
		final String v=(p!=null)?p.get(name):null;
		return (v!=null)?v:loadArgs(E).get(name);
	}

	/**
	 * SC13: a LOAD= line's file and its arguments: LOAD=file KEY=value...
	 * @param words what follows LOAD=
	 * @return the file, then the arguments' words
	 */
	protected static String[] loadFileAndArgs(final String words)
	{
		final String w=words.trim();
		int x=0;
		while((x<w.length())&&(!Character.isWhitespace(w.charAt(x))))
			x++;
		return new String[]{w.substring(0,x),w.substring(x).trim()};
	}

	/**
	 * SC13: a script file's text without its ARGDEFAULT lines.
	 * @param text the file's text
	 * @return the text to parse
	 */
	protected static String withoutArgDefaults(final String text)
	{
		if(text.toUpperCase().indexOf("ARGDEFAULT")<0)
			return text;
		final StringBuilder str=new StringBuilder("");
		for(final String line : text.split("\n",-1))
		{
			if(!line.trim().toUpperCase().startsWith("ARGDEFAULT "))
				str.append(line).append('\n');
		}
		return str.toString();
	}

	/**
	 * SC13: the arguments LOAD= gave this engine's script (LOAD=file
	 * KEY=value...), then the defaults the files' ARGDEFAULT KEY=value lines
	 * give for the rest.
	 * @param E the scripted thing
	 * @return the arguments, by name
	 */
	protected Map<String,String> loadArgs(final Environmental E)
	{
		Map<String,String> args=loadArgs;
		if(args==null)
		{
			args=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
			collectLoadArgs(E,getScript(),args,0);
			loadArgs=args;
		}
		return args;
	}

	private void collectLoadArgs(final Environmental E, final String text, final Map<String,String> args, final int depth)
	{
		if((depth>10)||(text==null))
			return;
		String parse=text;
		int y;
		while((y=parse.toUpperCase().indexOf("LOAD="))>=0)
		{
			int z=parse.indexOf('~',y);
			while((z>0)&&(parse.charAt(z-1)=='\\'))
				z=parse.indexOf('~',z+1);
			final String[] fa=loadFileAndArgs((z>0)?parse.substring(y+5,z):parse.substring(y+5));
			parse=(z>0)?parse.substring(z+1):"";
			for(final Map.Entry<String,String> e : CMParms.parseEQParms(fa[1]).entrySet())
				args.putIfAbsent(e.getKey(),e.getValue());
			if(fa[0].length()==0)
				continue;
			final String file=getResourceFileData(E,fa[0]).toString();
			for(final String line : file.split("\n"))
			{
				final String l=line.trim();
				if(l.toUpperCase().startsWith("ARGDEFAULT "))
				{
					for(final Map.Entry<String,String> e : CMParms.parseEQParms(l.substring(11)).entrySet())
						args.putIfAbsent(e.getKey(),e.getValue());
				}
			}
			collectLoadArgs(E,file,args,depth+1);
		}
	}

	protected String parseLoads(final Environmental E, final String text, final int depth,
								final List<String> filenames, final StringBuffer nonFilenameScript)
	{
		final StringBuffer results=new StringBuffer("");
		String parse=text;
		if(depth>10) return "";  // no including off to infinity
		String p=null;
		while(parse.length()>0)
		{
			final int y=parse.toUpperCase().indexOf("LOAD=");
			if(y>=0)
			{
				p=parse.substring(0,y).trim();
				if((!p.endsWith(";"))
				&&(!p.endsWith("\n"))
				&&(!p.endsWith("~"))
				&&(!p.endsWith("\r"))
				&&(p.length()>0))
				{
					if(nonFilenameScript!=null)
						nonFilenameScript.append(parse.substring(0,y+1));
					results.append(parse.substring(0,y+1));
					parse=parse.substring(y+1);
					continue;
				}
				results.append(p+"\n");
				int z=parse.indexOf('~',y);
				while((z>0)&&(parse.charAt(z-1)=='\\'))
					z=parse.indexOf('~',z+1);
				if(z>0)
				{
					final String filename=loadFileAndArgs(parse.substring(y+5,z).trim())[0]; // SC13: LOAD=file KEY=value...
					parse=parse.substring(z+1);
					if((filenames!=null)&&(!filenames.contains(filename)))
						filenames.add(filename);
					results.append(parseLoads(E,withoutArgDefaults(getResourceFileData(E,filename).toString()),depth+1,filenames,null));
				}
				else
				{
					final String filename=loadFileAndArgs(parse.substring(y+5).trim())[0]; // SC13
					if((filenames!=null)&&(!filenames.contains(filename)))
						filenames.add(filename);
					results.append(parseLoads(E,withoutArgDefaults(getResourceFileData(E,filename).toString()),depth+1,filenames,null));
					break;
				}
			}
			else
			{
				if(nonFilenameScript!=null)
					nonFilenameScript.append(parse);
				results.append(parse);
				break;
			}
		}
		return results.toString();
	}

	protected void buildHashes()
	{
		if(funcH.size()==0)
		{
			synchronized(funcH)
			{
				if(funcH.size()==0)
				{
					for(int i=0;i<funcs.length;i++)
						funcH.put(funcs[i],Integer.valueOf(i+1));
					for(int i=0;i<methods.length;i++)
						methH.put(methods[i],Integer.valueOf(i+1));
					for(int i=0;i<progs.length;i++)
						progH.put(progs[i],Integer.valueOf(i+1));
					for(int i=0;i<progs.length;i++)
						methH.put(progs[i],Integer.valueOf(Integer.MIN_VALUE));
				}
			}
		}
	}

	protected static final String deEscapeParseChars = ";~<#\\";

	protected static void addSubScriptLine(final SubScript ss, String s)
	{
		s=CMStrings.deEscape(s, deEscapeParseChars);
		if(s.trim().length()>0)
			ss.add(new ScriptLn(s,null,null));
	}

	/**
	 * SC17: ELSEIF c (or ELSE IF c) reads as ELSE, then IF c, with an ENDIF
	 * more at the chain's own ENDIF.
	 * @param script the lines of one trigger
	 */
	protected static void expandElseIf(final SubScript script)
	{
		boolean has=false;
		for(final ScriptLn ln : script)
		{
			final String w=CMParms.getCleanBit(ln.first,0).toUpperCase();
			if(w.equals("ELSEIF")||(w.equals("ELSE")&&CMParms.getCleanBit(ln.first,1).equalsIgnoreCase("IF")))
				has=true;
		}
		if(!has)
			return;
		final List<ScriptLn> out=new ArrayList<ScriptLn>(script.size()+4);
		final Deque<int[]> open=new ArrayDeque<int[]>();
		boolean inJs=false;
		for(int i=0;i<script.size();i++)
		{
			final ScriptLn ln=script.get(i);
			final String w=(i==0)?"":CMParms.getCleanBit(ln.first,0).toUpperCase();
			if(w.equals("<SCRIPT>"))
				inJs=true;
			else
			if(w.equals("</SCRIPT>"))
				inJs=false;
			if((!inJs)&&(i>0))
			{
				if(w.equals("IF"))
					open.push(new int[]{0});
				else
				if((w.equals("ELSEIF")||(w.equals("ELSE")&&CMParms.getCleanBit(ln.first,1).equalsIgnoreCase("IF")))&&(!open.isEmpty()))
				{
					final String cond=w.equals("ELSEIF")?CMParms.getPastBit(ln.first,0):CMParms.getPastBit(ln.first,1);
					out.add(new ScriptLn("ELSE",null,null));
					out.add(new ScriptLn("IF "+cond.trim(),null,null));
					open.peek()[0]++;
					continue;
				}
				else
				if(w.equals("ENDIF")&&(!open.isEmpty()))
				{
					out.add(ln);
					final int owed=open.pop()[0];
					for(int e=0;e<owed;e++)
						out.add(new ScriptLn("ENDIF",null,null));
					continue;
				}
			}
			out.add(ln);
		}
		script.clear();
		script.addAll(out);
	}

	protected List<SubScript> parseScripts(final Environmental E, String text)
	{
		buildHashes();
		while((text.length()>0)
		&&(Character.isWhitespace(text.charAt(0))))
			text=text.substring(1);
		boolean staticSet=false;
		if((text.length()>10)
		&&(text.substring(0,10).toUpperCase().startsWith("STATIC=")))
		{
			staticSet=true;
			text=text.substring(7);
		}
		text=parseLoads(E,text,0,null,null);
		if(staticSet)
		{
			text=swapOutsideScripts(text,'\'','`');
			myScript=text;
			reset();
		}
		String xml = "";
		final int xmlEnd = text.indexOf(XMLLibrary.FILE_XML_BOUNDARY);
		if(xmlEnd > 0)
		{
			xml = text.substring(xmlEnd+XMLLibrary.FILE_XML_BOUNDARY.length());
			text = text.substring(0,xmlEnd);
		}
		int state = 0; // 0=normal, 2=incomment, 3=inscript
		int lastLine=0;
		final List<SubScript> parsedScript=Collections.synchronizedList(new ArrayList<SubScript>(3));
		SubScript currentScript = new SubScriptImpl(null);
		parsedScript.add(currentScript);
		for(int i=0;i<text.length();i++)
		{
			final char c = text.charAt(i);
			switch(c)
			{
			case '\n': case '\r':
				if(state == 0)
					addSubScriptLine(currentScript, text.substring(lastLine,i));
				else
				if(state == 2)
					state=0;
				if(state != 3)
					lastLine=i+1;
				break;
			case '#':
				if((state==0)
				&&((i==0)||(text.charAt(i-1)!='\\'))
				&&(text.substring(lastLine,i).trim().length()==0))
				{
					state=2; // in a comment;
					lastLine=i+1; // this wont end up mattering...
				}
				break;
			case ';':
				if((state==0)
				&&((i==0)||(text.charAt(i-1)!='\\')))
				{
					addSubScriptLine(currentScript, text.substring(lastLine,i));
					lastLine=i+1;
				}
				break;
			case '~':
				if((state == 0)
				&&((i==0)||(text.charAt(i-1)!='\\')))
				{
					addSubScriptLine(currentScript, text.substring(lastLine,i));
					lastLine=i+1;
					currentScript = new SubScriptImpl(null);
					parsedScript.add(currentScript);
				}
				break;
			case '<':
				if((i<text.length()-9)
				&&(Character.isLetter(text.charAt(i+1))
					||((text.charAt(i+1)=='/'))&&Character.isLetter(text.charAt(i+2))))
				{
					final String uppAhead=text.substring(i,i+9).toUpperCase();
					if((state==0)
					&&uppAhead.startsWith("<SCRIPT>")
					&&((i==0)||(text.charAt(i-1)!='\\')))
					{
						addSubScriptLine(currentScript, text.substring(lastLine,i));
						addSubScriptLine(currentScript, "<SCRIPT>");
						i+=7; // the 8th char is coming later
						lastLine=i+1;
						state=3;
					}
					else
					if((state==3)
					&&uppAhead.startsWith("</SCRIPT>")
					&&((i==0)||(text.charAt(i-1)!='\\')))
					{
						addSubScriptLine(currentScript, text.substring(lastLine,i));
						addSubScriptLine(currentScript, "</SCRIPT>");
						i+=8; // 9th is coming later
						lastLine=i+1;
						state=0;
					}
				}
				break;
			}
		}
		addSubScriptLine(currentScript, text.substring(lastLine,text.length()));
		if(currentScript.size()==0)
			parsedScript.remove(parsedScript.size()-1);
		for(final SubScript S : parsedScript)
			expandElseIf(S); // SC17
		if(xml.length()>0)
		{
			final SubScript xmlS = new SubScriptImpl(null);
			final List<XMLTag> tags = CMLib.xml().parseAllXML(xml);
			xmlS.add(new ScriptLn(XMLLibrary.FILE_XML_BOUNDARY,null,tags));
			parsedScript.add(xmlS);
		}
		return parsedScript;
	}

	/**
	 * Returns the rooms of the area the script last knew it was in, or none.
	 * @return the rooms
	 */
	@SuppressWarnings("unchecked")
	protected Enumeration<Room> areaRooms()
	{
		final Room R=lastKnownLocation;
		if((R==null)||(R.getArea()==null))
			return EmptyEnumeration.INSTANCE;
		return R.getArea().getProperMap();
	}

	protected Room getRoom(final String thisName, final Room imHere)
	{
		if(thisName.length()==0)
			return null;
		Room roomR=null;
		if(roomFinder.containsKey(thisName))
		{
			roomR=roomFinder.get(thisName);
			if((roomR!=null)
			&&(!roomR.amDestroyed()))
				return roomR;
			roomR=null;
		}
		if(imHere!=null)
		{
			if(imHere.roomID().equalsIgnoreCase(thisName))
				roomR=imHere;
			else
			if((thisName.startsWith("#"))&&(CMath.isLong(thisName.substring(1))))
				roomR=CMLib.map().getRoom(imHere.getArea().Name()+thisName);
			else
			if(Character.isDigit(thisName.charAt(0))&&(CMath.isLong(thisName)))
				roomR=CMLib.map().getRoom(imHere.getArea().Name()+"#"+thisName);
			if(roomR!=null)
			{
				roomFinder.put(thisName, roomR);
				return roomR;
			}
		}
		roomR=CMLib.map().getRoom(thisName);
		if((roomR!=null)&&(roomR.roomID().equalsIgnoreCase(thisName)))
		{
			if(CMath.bset(roomR.getArea().flags(),Area.FLAG_INSTANCE_PARENT)
			&&(imHere!=null)
			&&(CMath.bset(imHere.getArea().flags(),Area.FLAG_INSTANCE_CHILD))
			&&(imHere.getArea().Name().endsWith("_"+roomR.getArea().Name()))
			&&(thisName.indexOf('#')>=0))
			{
				final Room otherRoom=CMLib.map().getRoom(imHere.getArea().Name()+thisName.substring(thisName.indexOf('#')));
				if((otherRoom!=null)&&(otherRoom.roomID().endsWith(thisName)))
					roomR=otherRoom;
			}
			roomFinder.put(thisName, roomR);
			return roomR;
		}

		final MOB pM = CMLib.players().getPlayer(thisName);
		if((pM != null)&&(CMLib.flags().isInTheGame(pM, true)))
			return pM.location();
		List<Room> rooms=new ArrayList<Room>(1);
		if((imHere!=null)&&(imHere.getArea()!=null))
			rooms=CMLib.hunt().findAreaRoomsLiberally(null, imHere.getArea(), thisName, "RIEPM",100);
		if(rooms.size()==0)
		{
			if(debugBadScripts)
				Log.debugOut("ScriptingEngine","World room search called for: "+thisName);
			rooms=CMLib.hunt().findWorldRoomsLiberally(null,thisName, "RIEPM",100,2000);
		}
		if(rooms.size()>0) // this could be people, or items, so no caching.
			return rooms.get(CMLib.dice().roll(1,rooms.size(),-1));

		// ... and this is indeterminate, so no caching here either.
		final int x=thisName.indexOf('@');
		if(x>0)
		{
			Room R=CMLib.map().getRoom(thisName.substring(x+1));
			if((R==null)||(R==imHere))
			{
				final Area A=CMLib.map().getArea(thisName.substring(x+1));
				R=(A!=null)?A.getRandomMetroRoom():null;
			}
			if((R!=null)&&(R!=imHere))
				roomR=getRoom(thisName.substring(0,x),R);
		}
		return roomR;
	}

	protected String getScriptFiles()
	{
		String scriptFiles=CMParms.toListString(externalFiles());
		if((scriptFiles == null)||(scriptFiles.trim().length()==0))
			scriptFiles=CMStrings.limit(this.getScript(),80);
		if((scriptFiles == null)||(scriptFiles.trim().length()==0))
			Log.errOut(new Exception("Scripting Error"));
		if((scriptFiles == null)||(scriptFiles.trim().length()==0))
			scriptFiles=getScriptResourceKey();
		return scriptFiles;
	}


	protected void logError(final MPContext context, final String cmdName, final String errType, final String errMsg)
	{
		final Environmental scripted = context.scripted;
		String where="";
		if((context.script!=null)&&(scripted instanceof PhysicalAgent))
			where=scriptLocation((PhysicalAgent)scripted,context.script,context.line);
		logError(scripted,cmdName,errType,errMsg,where);
	}

	protected void logError(final Environmental scripted, final String cmdName, final String errType, final String errMsg)
	{
		logError(scripted,cmdName,errType,errMsg,"");
	}

	/**
	 * Logs a script error, as "file:line COMMAND Type: what (host @ room)", and
	 * hands it to {@link ScriptErrors}, which keeps it for SCRIPTERRORS and
	 * shows it to the builder, or as a normal game's SCRIPTERRORS says (not,
	 * by default, to the player).
	 * @param scripted the host, or null
	 * @param cmdName the command or function
	 * @param errType the kind of error
	 * @param errMsg what went wrong
	 * @param where the file and line, or ""
	 */
	protected void logError(final Environmental scripted, final String cmdName, final String errType, final String errMsg, final String where)
	{
		final String at=((where==null)||(where.length()==0))?"":(where+" ");
		if(scripted!=null)
		{
			final Room R=CMLib.map().roomLocation(scripted);
			final String line=at+cmdName+" "+errType+": "+errMsg+" ("+scripted.name()+" @ "+CMLib.map().getDescriptiveExtendedRoomID(R)
							+((at.length()>0)?"":(", "+getScriptFiles()))+")";
			Log.errOut("Scripting",line);
			ScriptErrors.report(line,R);
		}
		else
		{
			final String line;
			if((cmdName!=null)&&(cmdName.equalsIgnoreCase("LOAD")))
				line=at+cmdName+" "+errType+": "+errMsg;
			else
				line=at+cmdName+" "+errType+": "+errMsg+" ("+CMParms.toListString(externalFiles())+")";
			Log.errOut("Scripting",line);
			ScriptErrors.report(line,null);
		}
	}

	protected boolean simpleEvalStr(final Environmental scripted, final String arg1, final String arg2, final String cmp, final String cmdName)
	{
		int x=arg1.compareToIgnoreCase(arg2);
		final Signs sign = Signs.getSign(cmp);
		if(sign==null)
		{
			logError(scripted,cmdName,"Syntax",arg1+" "+cmp+" "+arg2);
			return false;
		}
		switch(sign)
		{
		case EQUL:
			return (x == 0);
		case GTEQ:
			return (x == 0) || (x > 0);
		case LTEQ:
			return (x == 0) || (x < 0);
		case GRAT:
			return (x > 0);
		case LEST:
			return (x < 0);
		case NTEQ:
			return (x != 0);
		case IN:
		{
			x= arg2.indexOf(arg1);
			if(x<0)
				return false;
			return ((x==0)||(!Character.isLetterOrDigit(arg2.charAt(x-1))))
					&&((x>=arg2.length()-arg1.length())||(!Character.isLetterOrDigit(arg2.charAt(x+arg1.length()))));
		}
		default:
			return (x == 0);
		}
	}

	protected boolean simpleEval(final Environmental scripted, final String arg1, final String arg2, final String cmp, final String cmdName)
	{
		long val1=CMath.s_long(arg1.trim());
		final long val2=CMath.s_long(arg2.trim());
		final Signs sign=Signs.getSign(cmp);
		if(sign==null)
		{
			logError(scripted,cmdName,"NOSIGN",val1+" "+cmp+" "+val2);
			return false;
		}
		switch(sign)
		{
		case EQUL:
			return (val1 == val2);
		case GTEQ:
			return val1 >= val2;
		case LTEQ:
			return val1 <= val2;
		case GRAT:
			return (val1 > val2);
		case LEST:
			return (val1 < val2);
		case NTEQ:
			return (val1 != val2);
		case IN:
		{
			val1 = arg2.indexOf(arg1);
			if(val1<0)
				return false;
			return ((val1==0)||(!Character.isLetterOrDigit(arg2.charAt((int)val1-1))))
					&&((val1>=arg2.length()-arg1.length())||(!Character.isLetterOrDigit(arg2.charAt((int)val1+arg1.length()))));

		}
		default:
			return (val1 == val2);
		}
	}

	protected boolean simpleExpressionEval(final Environmental scripted, final String arg1, final String arg2, final String cmp, final String cmdName)
	{
		final double val1=CMath.s_parseMathExpression(arg1.trim());
		final double val2=CMath.s_parseMathExpression(arg2.trim());
		final Signs sign = Signs.getSign(cmp);
		if(sign==null)
		{
			logError(scripted,cmdName,"Syntax",val1+" "+cmp+" "+val2);
			return false;
		}
		switch(sign)
		{
		case EQUL:
			return (val1 == val2);
		case GTEQ:
			return val1 >= val2;
		case LTEQ:
			return val1 <= val2;
		case GRAT:
			return (val1 > val2);
		case LEST:
			return (val1 < val2);
		case NTEQ:
			return (val1 != val2);
		case IN:
			return (""+val2).indexOf(""+val1) >=0;
		default:
			return (val1 == val2);
		}
	}

	protected List<MOB> loadMobsFromFile(final Environmental scripted, String filename)
	{
		filename=filename.trim();
		@SuppressWarnings("unchecked")
		List<MOB> monsters=(List<MOB>)Resources.getResource("RANDOMMONSTERS-"+filename);
		if(monsters!=null)
			return monsters;
		final StringBuffer buf=getResourceFileData(scripted,filename);
		String thangName="null";
		final Room R=CMLib.map().roomLocation(scripted);
		if(R!=null)
			thangName=scripted.name()+" at "+CMLib.map().getExtendedRoomID((Room)scripted);
		else
		if(scripted!=null)
			thangName=scripted.name();
		if((buf==null)||(buf.length()<20))
		{
			logError(scripted,"XMLLOAD","?","Unknown XML file: '"+filename+"' in "+thangName);
			return null;
		}
		final List<XMLTag> xml=CMLib.xml().parseAllXML(buf.toString());
		monsters=Collections.synchronizedList(new ArrayList<MOB>());
		if(xml!=null)
		{
			if(CMLib.xml().getContentsFromPieces(xml,"MOBS")!=null)
			{
				final String error=CMLib.coffeeMaker().addMOBsFromXML(xml,monsters,null);
				if(error.length()>0)
				{
					logError(scripted,"XMLLOAD","?","Error in XML file: '"+filename+"'");
					return null;
				}
				if(monsters.size()<=0)
				{
					logError(scripted,"XMLLOAD","?","Empty XML file: '"+filename+"'");
					return null;
				}
				Resources.submitResource("RANDOMMONSTERS-"+filename,monsters);
				for(final Object O : monsters)
				{
					if(O instanceof MOB)
						CMLib.threads().unTickAll((MOB)O);
				}
			}
			else
			{
				logError(scripted,"XMLLOAD","?","No MOBs in XML file: '"+filename+"' in "+thangName);
				return null;
			}
		}
		else
		{
			logError(scripted,"XMLLOAD","?","Invalid XML file: '"+filename+"' in "+thangName);
			return null;
		}
		return monsters;
	}

	protected List<MOB> generateMobsFromFile(final Environmental scripted, String filename, final String tagName, final String rest)
	{
		filename=filename.trim();
		@SuppressWarnings("unchecked")
		List<MOB> monsters=(List<MOB>)Resources.getResource("RANDOMGENMONSTERS-"+filename+"."+tagName+"-"+rest);
		if(monsters!=null)
			return monsters;
		final StringBuffer buf=getResourceFileData(scripted,filename);
		String thangName="null";
		final Room R=CMLib.map().roomLocation(scripted);
		if(R!=null)
			thangName=scripted.name()+" at "+CMLib.map().getExtendedRoomID((Room)scripted);
		else
		if(scripted!=null)
			thangName=scripted.name();
		if((buf==null)||(buf.length()<20))
		{
			logError(scripted,"XMLLOAD","?","Unknown XML file: '"+filename+"' in "+thangName);
			return null;
		}
		final List<XMLTag> xml=CMLib.xml().parseAllXML(buf.toString());
		monsters=Collections.synchronizedList(new ArrayList<MOB>());
		if(xml!=null)
		{
			if(CMLib.xml().getContentsFromPieces(xml,"AREADATA")!=null)
			{
				final Map<String,Object> definedIDs = Collections.synchronizedMap(new HashMap<String,Object>());
				final Map<String,String> eqParms=new HashMap<String,String>();
				eqParms.putAll(CMParms.parseEQParms(rest.trim()));
				final String idName=tagName.toUpperCase();
				try
				{
					CMLib.percolator().buildDefinedIDSet(xml,definedIDs, new TreeSet<String>());
					if((!(definedIDs.get(idName) instanceof XMLTag))
					||(!((XMLTag)definedIDs.get(idName)).tag().equalsIgnoreCase("MOB")))
					{
						logError(scripted,"XMLLOAD","?","Non-MOB tag '"+idName+"' for XML file: '"+filename+"' in "+thangName);
						return null;
					}
					final XMLTag piece=(XMLTag)definedIDs.get(idName);
					definedIDs.putAll(eqParms);
					try
					{
						CMLib.percolator().checkRequirements(piece, definedIDs);
					}
					catch(final CMException cme)
					{
						logError(scripted,"XMLLOAD","?","Required ids for "+idName+" were missing for XML file: '"+filename+"' in "+thangName+": "+cme.getMessage());
						return null;
					}
					CMLib.percolator().preDefineReward(piece, definedIDs);
					CMLib.percolator().defineReward(piece,definedIDs);
					monsters.addAll(CMLib.percolator().findMobs(piece, definedIDs));
					CMLib.percolator().postProcess(definedIDs);
					if(monsters.size()<=0)
					{
						logError(scripted,"XMLLOAD","?","Empty XML file: '"+filename+"'");
						return null;
					}
					Resources.submitResource("RANDOMGENMONSTERS-"+filename+"."+tagName+"-"+rest,monsters);
				}
				catch(final CMException cex)
				{
					logError(scripted,"XMLLOAD","?","Unable to generate "+idName+" from XML file: '"+filename+"' in "+thangName+": "+cex.getMessage());
					return null;
				}
			}
			else
			{
				logError(scripted,"XMLLOAD","?","Invalid GEN XML file: '"+filename+"' in "+thangName);
				return null;
			}
		}
		else
		{
			logError(scripted,"XMLLOAD","?","Empty or Invalid XML file: '"+filename+"' in "+thangName);
			return null;
		}
		return monsters;
	}

	protected List<Item> loadItemsFromFile(final Environmental scripted, String filename)
	{
		filename=filename.trim();
		@SuppressWarnings("unchecked")
		List<Item> items=(List<Item>)Resources.getResource("RANDOMITEMS-"+filename);
		if(items!=null)
			return items;
		final StringBuffer buf=getResourceFileData(scripted,filename);
		String thangName="null";
		final Room R=CMLib.map().roomLocation(scripted);
		if(R!=null)
			thangName=scripted.name()+" at "+CMLib.map().getExtendedRoomID((Room)scripted);
		else
		if(scripted!=null)
			thangName=scripted.name();
		if((buf==null)||(buf.length()<20))
		{
			logError(scripted,"XMLLOAD","?","Unknown XML file: '"+filename+"' in "+thangName);
			return null;
		}
		items=Collections.synchronizedList(new ArrayList<Item>());
		final List<XMLTag> xml=CMLib.xml().parseAllXML(buf.toString());
		if(xml!=null)
		{
			if(CMLib.xml().getContentsFromPieces(xml,"ITEMS")!=null)
			{
				final String error=CMLib.coffeeMaker().addItemsFromXML(buf.toString(),items,null);
				if(error.length()>0)
				{
					logError(scripted,"XMLLOAD","?","Error in XML file: '"+filename+"' in "+thangName);
					return null;
				}
				if(items.size()<=0)
				{
					logError(scripted,"XMLLOAD","?","Empty XML file: '"+filename+"'");
					return null;
				}
				for(final Object O : items)
				{
					if(O instanceof Item)
						CMLib.threads().deleteTick((Item)O, -1);
				}
				Resources.submitResource("RANDOMITEMS-"+filename,items);
			}
			else
			{
				logError(scripted,"XMLLOAD","?","No ITEMS in XML file: '"+filename+"' in "+thangName);
				return null;
			}
		}
		else
		{
			logError(scripted,"XMLLOAD","?","Empty or invalid XML file: '"+filename+"' in "+thangName);
			return null;
		}
		return items;
	}

	protected List<Item> generateItemsFromFile(final Environmental scripted, String filename, final String tagName, final String rest)
	{
		filename=filename.trim();
		@SuppressWarnings("unchecked")
		List<Item> items=(List<Item>)Resources.getResource("RANDOMGENITEMS-"+filename+"."+tagName+"-"+rest);
		if(items!=null)
			return items;
		final StringBuffer buf=getResourceFileData(scripted,filename);
		String thangName="null";
		final Room R=CMLib.map().roomLocation(scripted);
		if(R!=null)
			thangName=scripted.name()+" at "+CMLib.map().getExtendedRoomID((Room)scripted);
		else
		if(scripted!=null)
			thangName=scripted.name();
		if((buf==null)||(buf.length()<20))
		{
			logError(scripted,"XMLLOAD","?","Unknown XML file: '"+filename+"' in "+thangName);
			return null;
		}
		items=Collections.synchronizedList(new ArrayList<Item>());
		final List<XMLTag> xml=CMLib.xml().parseAllXML(buf.toString());
		if(xml!=null)
		{
			if(CMLib.xml().getContentsFromPieces(xml,"AREADATA")!=null)
			{
				final Map<String,Object> definedIDs = Collections.synchronizedMap(new HashMap<String,Object>());
				final Map<String,String> eqParms=new HashMap<String,String>();
				eqParms.putAll(CMParms.parseEQParms(rest.trim()));
				final String idName=tagName.toUpperCase();
				try
				{
					CMLib.percolator().buildDefinedIDSet(xml,definedIDs, new TreeSet<String>());
					if((!(definedIDs.get(idName) instanceof XMLTag))
					||(!((XMLTag)definedIDs.get(idName)).tag().equalsIgnoreCase("ITEM")))
					{
						logError(scripted,"XMLLOAD","?","Non-ITEM tag '"+idName+"' for XML file: '"+filename+"' in "+thangName);
						return null;
					}
					final XMLTag piece=(XMLTag)definedIDs.get(idName);
					definedIDs.putAll(eqParms);
					try
					{
						CMLib.percolator().checkRequirements(piece, definedIDs);
					}
					catch(final CMException cme)
					{
						logError(scripted,"XMLLOAD","?","Required ids for "+idName+" were missing for XML file: '"+filename+"' in "+thangName+": "+cme.getMessage());
						return null;
					}
					CMLib.percolator().preDefineReward(piece, definedIDs);
					CMLib.percolator().defineReward(piece,definedIDs);
					items.addAll(CMLib.percolator().findItems(piece, definedIDs));
					CMLib.percolator().postProcess(definedIDs);
					if(items.size()<=0)
					{
						logError(scripted,"XMLLOAD","?","Empty XML file: '"+filename+"' in "+thangName);
						return null;
					}
					Resources.submitResource("RANDOMGENITEMS-"+filename+"."+tagName+"-"+rest,items);
				}
				catch(final CMException cex)
				{
					logError(scripted,"XMLLOAD","?","Unable to generate "+idName+" from XML file: '"+filename+"' in "+thangName+": "+cex.getMessage());
					return null;
				}
			}
			else
			{
				logError(scripted,"XMLLOAD","?","Not a GEN XML file: '"+filename+"' in "+thangName);
				return null;
			}
		}
		else
		{
			logError(scripted,"XMLLOAD","?","Empty or Invalid XML file: '"+filename+"' in "+thangName);
			return null;
		}
		return items;
	}

	@SuppressWarnings("unchecked")
	/** SC3: true when the last CATALOG source read was CATALOG FORCE */
	protected boolean catalogForce=false;

	/**
	 * SC3: a loader's copy of what it found: a catalog entry's copy is counted
	 * by the catalog, and none is made past the entry's cap unless the
	 * CATALOG FORCE form asked.
	 * @param E what the loader found
	 * @return the copy, or null
	 */
	protected Physical loaderCopy(final Environmental E)
	{
		if(!(E instanceof Physical))
			return null;
		final Physical P=(Physical)E;
		if(CMLib.catalog().getCatalogObj(P)==P)
			return ScriptLib.catalogCopy(P,catalogForce);
		return (Physical)P.copyOf();
	}

	protected Environmental findSomethingCalledThis(final String thisName, final MPContext ctx, final MOB meMOB, final Room imHere, List<Environmental> OBJS, final boolean mob)
	{
		if(thisName.length()==0)
			return null;
		if(thisName.toUpperCase().trim().startsWith("CATALOG "))
		{
			// SC3: the catalog's entry, once for each copy asked; loaderCopy counts and caps them
			final ScriptLib.CatalogLoad L=ScriptLib.catalogLoad(thisName,mob);
			final Physical P=ScriptLib.catalogEntry(L.name,mob);
			catalogForce=L.force;
			for(int c=0;(P!=null)&&(c<L.count);c++)
				OBJS.add(P);
			return (OBJS.size()>0)?OBJS.get(0):null;
		}
		Environmental thing=null;
		Environmental areaThing=null;
		if(thisName.toUpperCase().trim().startsWith("FROMFILE "))
		{
			try
			{
				List<? extends Environmental> V=null;
				if(mob)
					V=loadMobsFromFile(null,CMParms.getCleanBit(thisName,1));
				else
					V=loadItemsFromFile(null,CMParms.getCleanBit(thisName,1));
				if(V!=null)
				{
					final String name=CMParms.getPastBitClean(thisName,1);
					if(name.equalsIgnoreCase("ALL"))
						OBJS.addAll(V); // SC3: was a new list the caller never saw
					else
					if(name.equalsIgnoreCase("ANY"))
					{
						if(V.size()>0)
							areaThing=V.get(CMLib.dice().roll(1,V.size(),-1));
					}
					else
					{
						areaThing=CMLib.english().fetchEnvironmental(V,name,true);
						if(areaThing==null)
							areaThing=CMLib.english().fetchEnvironmental(V,name,false);
					}
				}
			}
			catch(final Exception e)
			{
			}
		}
		else
		if(thisName.toUpperCase().trim().startsWith("FROMGENFILE "))
		{
			try
			{
				List<? extends Environmental> V=null;
				final String filename=CMParms.getCleanBit(thisName, 1);
				final String name=CMParms.getCleanBit(thisName, 2);
				final String tagName=CMParms.getCleanBit(thisName, 3);
				final String theRest=CMParms.getPastBitClean(thisName,3);
				if(mob)
					V=generateMobsFromFile(null,filename, tagName, theRest);
				else
					V=generateItemsFromFile(null,filename, tagName, theRest);
				if(V!=null)
				{
					if(name.equalsIgnoreCase("ALL"))
						OBJS.addAll(V); // SC3: was a new list the caller never saw
					else
					if(name.equalsIgnoreCase("ANY"))
					{
						if(V.size()>0)
							areaThing=V.get(CMLib.dice().roll(1,V.size(),-1));
					}
					else
					{
						areaThing=CMLib.english().fetchEnvironmental(V,name,true);
						if(areaThing==null)
							areaThing=CMLib.english().fetchEnvironmental(V,name,false);
					}
				}
			}
			catch(final Exception e)
			{
			}
		}
		else
		{
			if(!mob)
				areaThing=(meMOB!=null)?meMOB.findItem(thisName):null;
			try
			{
				if (areaThing == null)
				{
					if(mob)
					{
						if((meMOB!=null)&&(meMOB.name().equalsIgnoreCase(thisName)))
							areaThing=meMOB;
						else
						if((ctx.source!=null)&&(ctx.source.name().equalsIgnoreCase(thisName)))
							areaThing=ctx.source;
						else
						if((ctx.target instanceof MOB)&&ctx.target.name().equalsIgnoreCase(thisName))
							areaThing=ctx.target;
						else
						if ((ctx.scripted instanceof MOB) && ctx.scripted.name().equalsIgnoreCase(thisName))
							areaThing = ctx.scripted;
						else
						if ((this.lastKnownLocation != null) && (this.lastKnownLocation.fetchInhabitant(thisName)!=null))
							areaThing = this.lastKnownLocation.fetchInhabitant(thisName);
					}
					else
					{
						if((ctx.target instanceof Item)&&ctx.target.name().equalsIgnoreCase(thisName))
							areaThing=ctx.target;
						else
						if ((ctx.scripted instanceof Item) && ctx.scripted.name().equalsIgnoreCase(thisName))
							areaThing = ctx.scripted;
						else
						if ((this.lastKnownLocation != null) && (this.lastKnownLocation.fetchInhabitant(thisName)!=null))
							areaThing = this.lastKnownLocation.fetchInhabitant(thisName);
						else
						if((meMOB!=null)&&(meMOB.name().equalsIgnoreCase(thisName)))
							areaThing=meMOB;
						else
						if((ctx.source!=null)&&(ctx.source.name().equalsIgnoreCase(thisName)))
							areaThing=ctx.source;
					}
				}
				if(areaThing==null)
				{
					final Area A=imHere.getArea();
					final List<Environmental> all=Collections.synchronizedList(new ArrayList<Environmental>());
					if(mob)
					{
						all.addAll(CMLib.hunt().findInhabitants(A.getProperMap(),null,thisName,100));
						if(all.size()==0)
							all.addAll(CMLib.hunt().findShopStock(A.getProperMap(), null, thisName,100));
						for(int a=all.size()-1;a>=0;a--)
						{
							if(!(all.get(a) instanceof MOB))
								all.remove(a);
						}
						if(all.size()>0)
							areaThing=all.get(CMLib.dice().roll(1,all.size(),-1));
						else
						{
							all.addAll(CMLib.hunt().findInhabitantsFavorExact(CMLib.map().rooms(),null,thisName,false,100));
							if(all.size()==0)
								all.addAll(CMLib.hunt().findShopStock(CMLib.map().rooms(), null, thisName,100));
							for(int a=all.size()-1;a>=0;a--)
							{
								if(!(all.get(a) instanceof MOB))
									all.remove(a);
							}
							if(all.size()>0)
								thing=all.get(CMLib.dice().roll(1,all.size(),-1));
						}
					}
					if(all.size()==0)
					{
						all.addAll(CMLib.hunt().findRoomItems(A.getProperMap(), null,thisName,true,100));
						if(all.size()==0)
							all.addAll(CMLib.hunt().findInventory(A.getProperMap(), null,thisName,100));
						if(all.size()==0)
							all.addAll(CMLib.hunt().findShopStock(A.getProperMap(), null,thisName,100));
						if(all.size()>0)
							areaThing=all.get(CMLib.dice().roll(1,all.size(),-1));
						else
						{
							all.addAll(CMLib.hunt().findRoomItems(CMLib.map().rooms(), null,thisName,true,100));
							if(all.size()==0)
								all.addAll(CMLib.hunt().findInventory(CMLib.map().rooms(), null,thisName,100));
							if(all.size()==0)
								all.addAll(CMLib.hunt().findShopStock(CMLib.map().rooms(), null,thisName,100));
							if(all.size()>0)
								thing=all.get(CMLib.dice().roll(1,all.size(),-1));
						}
					}
				}
			}
			catch(final NoSuchElementException nse)
			{
			}
		}
		if(areaThing!=null)
			OBJS.add(areaThing);
		else
		if(thing!=null)
			OBJS.add(thing);
		if(OBJS.size()>0)
			return OBJS.get(0);
		return null;
	}

	protected PhysicalAgent getArgumentMOB(final String str, final MPContext ctx)
	{

		final MPContext ctx2;
		if(ctx.scripted != ctx.monster)
		{
			ctx2 = ctx.push(ctx.script);
			ctx2.scripted=ctx2.monster;
		}
		else
			ctx2 = ctx;
		return getArgumentItem(str, ctx2);
	}

	protected PhysicalAgent getArgumentItem(String str, final MPContext ctx)
	{
		if(str.length()<2)
			return null;
		if(str.charAt(0)=='$')
		{
			if(Character.isDigit(str.charAt(1)))
			{
				Object O=ctx.tmp[CMath.s_int(Character.toString(str.charAt(1)))];
				if(O instanceof PhysicalAgent)
					return (PhysicalAgent)O;
				else
				if((O instanceof List)&&(str.length()>3)&&(str.charAt(2)=='.'))
				{
					final List<?> V=(List<?>)O;
					String back=str.substring(2);
					if(back.charAt(1)=='$')
						back=varify(ctx,back);
					if((back.length()>1)&&Character.isDigit(back.charAt(1)))
					{
						int x=1;
						while((x<back.length())&&(Character.isDigit(back.charAt(x))))
							x++;
						final int y=CMath.s_int(back.substring(1,x).trim());
						if((V.size()>0)&&(y>=0))
						{
							if(y>=V.size())
								return null;
							O=V.get(y);
							if(O instanceof PhysicalAgent)
								return (PhysicalAgent)O;
						}
						str=O.toString(); // will fall through
					}
				}
				else
				if(O!=null)
					str=O.toString(); // will fall through
				else
					return null;
			}
			else
			switch(str.charAt(1))
			{
				case 'a':
					return (lastKnownLocation != null) ? lastKnownLocation.getArea() : null;
				case 'B':
				case 'b':
					return (lastLoaded instanceof PhysicalAgent) ? (PhysicalAgent) lastLoaded : null;
				case 'N':
				case 'n':
					return ((ctx.source == backupMOB) && (backupMOB != null) && (ctx.monster != ctx.scripted)) ? ctx.scripted : ctx.source;
				case 'U': // SC8: the character at the terminal
				case 'u':
					return terminalMob(ctx);
				case 'I':
				case 'i':
				case 'Q':
				case 'q':
					return ctx.scripted;
				case 'T':
				case 't':
					return ((ctx.target == backupMOB) && (backupMOB != null) && (ctx.monster != ctx.scripted))
							? ctx.scripted : (ctx.target instanceof PhysicalAgent) ? (PhysicalAgent) ctx.target : null;
				case 'O':
				case 'o':
					return ctx.primaryItem;
				case 'P':
				case 'p':
					return ctx.secondaryItem;
				case 'd':
				case 'D':
					return lastKnownLocation;
				case 'F':
				case 'f':
					if ((ctx.monster != null) && (ctx.monster.amFollowing() != null))
						return ctx.monster.amFollowing();
					return null;
				case 'r':
				case 'R':
					return getRandPC(ctx.monster, ctx.tmp, lastKnownLocation);
				case 'c':
				case 'C':
					return getRandAnyone(ctx.monster, ctx.tmp, lastKnownLocation);
				case 'w':
					return ctx.primaryItem != null ? ctx.primaryItem.owner() : null;
				case 'W':
					return ctx.secondaryItem != null ? ctx.secondaryItem.owner() : null;
				case 'x':
				case 'X':
					if (lastKnownLocation != null)
					{
						if ((str.length() > 2) && (CMLib.directions().getGoodDirectionCode("" + str.charAt(2)) >= 0))
							return lastKnownLocation.getExitInDir(CMLib.directions().getGoodDirectionCode("" + str.charAt(2)));
						int i = 0;
						Exit E = null;
						while (((++i) < 100) && (E == null))
							E = lastKnownLocation.getExitInDir(CMLib.dice().roll(1, Directions.NUM_DIRECTIONS(), -1));
						if(E!=null)
						return E;
					}
					return null;
				case '[':
				{
					final int x = str.substring(2).indexOf(']');
					if (x >= 0)
					{
						String mid = str.substring(2).substring(0, x);
						final int y = mid.indexOf(' ');
						if (y > 0)
						{
							final int num = CMath.s_int(mid.substring(0, y).trim());
							mid = mid.substring(y + 1).trim();
							final Quest Q = getQuest(mid);
							if (Q != null)
								return Q.getQuestItem(num);
						}
					}
					break;
				}
				case '{':
				{
					final int x = str.substring(2).indexOf('}');
					if (x >= 0)
					{
						String mid = str.substring(2).substring(0, x).trim();
						final int y = mid.indexOf(' ');
						if (y > 0)
						{
							final int num = CMath.s_int(mid.substring(0, y).trim());
							mid = mid.substring(y + 1).trim();
							final Quest Q = getQuest(mid);
							if (Q != null)
							{
								final MOB M=Q.getQuestMob(num);
								return M;
							}
						}
					}
					break;
				}
			}
		}
		str=varify(ctx,str);
		Environmental E=null;
		if(str.indexOf('#')>0)
			E=CMLib.map().getRoom(str);
		if((lastKnownLocation!=null) && (E==null))
		{
			E=lastKnownLocation.fetchFromRoomFavorMOBs(null,str);
			if(E==null)
			{
				E=lastKnownLocation.fetchFromMOBRoomFavorsItems(ctx.monster,null,str,Wearable.FILTER_ANY);
				if(E==null)
					E=lastKnownLocation.findItem(str);
			}
		}
		if((E == null)
		&&(ctx.monster!=null))
			E=ctx.monster.findItem(str);
		if(E == null)
		{
			E=CMLib.players().getPlayerAllHosts(str);
			if((E == null)
			&&(ctx.source != null))
				E=ctx.source.findItem(str);
		}
		if(E instanceof PhysicalAgent)
			return (PhysicalAgent)E;
		return null;
	}

	private String makeNamedString(final Object O)
	{
		if(O instanceof List)
			return makeParsableString((List<?>)O);
		else
		if(O instanceof Room)
			return ((Room)O).displayText(null);
		else
		if(O instanceof Environmental)
			return ((Environmental)O).Name();
		else
		if(O!=null)
			return O.toString();
		return "";
	}

	private String makeParsableString(final List<?> V)
	{
		if((V==null)||(V.size()==0))
			return "";
		if(V.get(0) instanceof String)
			return CMParms.combineQuoted(V,0);
		final StringBuffer ret=new StringBuffer("");
		String S=null;
		for(int v=0;v<V.size();v++)
		{
			S=makeNamedString(V.get(v)).trim();
			if(S.length()==0)
				ret.append("? ");
			else
			if(S.indexOf(' ')>=0)
				ret.append("\""+S+"\" ");
			else
				ret.append(S+" ");
		}
		return ret.toString();
	}

	protected void confirmLastKnownLocation(final MOB monster, final MOB source)
	{
		if((monster!=null)&&(monster.location()!=null))
			lastKnownLocation = monster.location();
		if((source!=null)&&(lastKnownLocation==null))
			lastKnownLocation = source.location();
		if(homeKnownLocation == null)
			homeKnownLocation = lastKnownLocation;
	}

	@Override
	public String varify(final MPContext ctx, final String varifyableStr)
	{
		int t=varifyableStr.indexOf('$');
		if(t<0)
			return varifyableStr;
		confirmLastKnownLocation(ctx.monster,ctx.source);
		MOB randMOB=null;
		final StringBuilder vchrs = new StringBuilder(varifyableStr);
		while((t>=0)&&(t<vchrs.length()-1))
		{
			if((t>0)&&(vchrs.charAt(t-1)=='\\'))
			{
				t=vchrs.indexOf("$",t+1);
				continue;
			}
			int replLen = 2;
			final char c=vchrs.charAt(t+1);
			String middle="";
			switch(c)
			{
				case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
					middle=makeNamedString(ctx.tmp[CMath.s_int(Character.toString(c))]);
					break;
				case '$':
					middle="";
					t=t+1;
					vchrs.deleteCharAt(t);
					replLen=0;
					break;
				case '@':
					if ((t < vchrs.length() - 2)
					&& (Character.isLetter(vchrs.charAt(t + 2))||Character.isDigit(vchrs.charAt(t + 2))))
					{
						final Environmental E = getArgumentItem("$" + vchrs.charAt(t + 2), ctx);
						middle = (E == null) ? "null" : "" + E;
						replLen++;
					}
					break;
				case 'a':
					if (lastKnownLocation != null)
						middle = lastKnownLocation.getArea().name();
					break;
				// case 'a':
				case 'A':
					// unnecessary, since, in coffeemud, this is part of the
					// name
					break;
				case 'b':
					middle = lastLoaded != null ? lastLoaded.name() : "";
					break;
				case 'B':
					middle = lastLoaded != null ? lastLoaded.displayText() : "";
					break;
				case 'c':
				case 'C':
					randMOB = getRandAnyone(ctx.monster, ctx.tmp, lastKnownLocation);
					if (randMOB != null)
						middle = randMOB.name();
					break;
				case 'd':
					middle = (lastKnownLocation != null) ? lastKnownLocation.displayText(ctx.monster) : "";
					break;
				case 'D':
					middle = (lastKnownLocation != null) ? lastKnownLocation.description(ctx.monster) : "";
					break;
				case 'e':
					if (ctx.source != null)
						middle = ctx.source.charStats().heshe();
					break;
				case 'E':
					if ((ctx.target != null) && (ctx.target instanceof MOB))
						middle = ((MOB) ctx.target).charStats().heshe();
					break;
				case 'f':
					if ((ctx.monster != null) && (ctx.monster.amFollowing() != null))
						middle = ctx.monster.amFollowing().name();
					break;
				case 'F':
					if ((ctx.monster != null) && (ctx.monster.amFollowing() != null))
						middle = ctx.monster.amFollowing().charStats().heshe();
					break;
				case 'g':
					middle = ((ctx.msg == null) ? "" : ctx.msg.toLowerCase());
					break;
				case 'G':
					middle = ((ctx.msg == null) ? "" : ctx.msg);
					break;
				case 'h':
					if (ctx.monster != null)
						middle = ctx.monster.charStats().himher();
					break;
				case 'H':
					randMOB = getRandPC(ctx.monster, ctx.tmp, lastKnownLocation);
					if (randMOB != null)
						middle = randMOB.charStats().himher();
					break;
				case 'i':
					if (ctx.monster != null)
						middle = ctx.monster.name();
					break;
				case 'I':
					if (ctx.monster != null)
						middle = ctx.monster.displayText();
					break;
				case 'j':
					if (ctx.monster != null)
						middle = ctx.monster.charStats().heshe();
					break;
				case 'J':
					randMOB = getRandPC(ctx.monster, ctx.tmp, lastKnownLocation);
					if (randMOB != null)
						middle = randMOB.charStats().heshe();
					break;
				case 'k':
					if (ctx.monster != null)
						middle = ctx.monster.charStats().hisher();
					break;
				case 'K':
					randMOB = getRandPC(ctx.monster, ctx.tmp, lastKnownLocation);
					if (randMOB != null)
						middle = randMOB.charStats().hisher();
					break;
				case 'l':
					if (lastKnownLocation != null)
					{
						final StringBuffer str = new StringBuffer("");
						for (int i = 0; i < lastKnownLocation.numInhabitants(); i++)
						{
							final MOB M = lastKnownLocation.fetchInhabitant(i);
							if ((M != null) && (M != ctx.monster) && (CMLib.flags().canBeSeenBy(M, ctx.monster)))
								str.append("\"" + M.name() + "\" ");
						}
						middle = str.toString();
					}
					break;
				case 'L':
					if (lastKnownLocation != null)
					{
						final StringBuffer str = new StringBuffer("");
						for (int i = 0; i < lastKnownLocation.numItems(); i++)
						{
							final Item I = lastKnownLocation.getItem(i);
							if ((I != null) && (I.container() == null) && (CMLib.flags().canBeSeenBy(I, ctx.monster)))
								str.append("\"" + I.name() + "\" ");
						}
						middle = str.toString();
					}
					break;
				case 'm':
					if (ctx.source != null)
						middle = ctx.source.charStats().hisher();
					break;
				case 'M':
					if ((ctx.target != null) && (ctx.target instanceof MOB))
						middle = ((MOB)ctx.target).charStats().hisher();
					break;
				case 'n':
				case 'N':
					if (ctx.source != null)
						middle = ctx.source.name();
					break;
				case 'u': // SC8: the character at the terminal
				case 'U':
				{
					final MOB U = terminalMob(ctx);
					if (U != null)
						middle = U.name();
					break;
				}
				case 'o':
				case 'O':
					if (ctx.primaryItem != null)
						middle = ctx.primaryItem.name();
					break;
				case 'p':
				case 'P':
					if (ctx.secondaryItem != null)
						middle = ctx.secondaryItem.name();
					break;
				case 'q':
					if (ctx.scripted != null)
						middle = ctx.scripted.name();
					break;
				case 'Q':
					if (ctx.scripted != null)
						middle = ctx.scripted.displayText();
					break;
				case 'r':
				case 'R':
					randMOB = getRandPC(ctx.monster, ctx.tmp, lastKnownLocation);
					if (randMOB != null)
						middle = randMOB.name();
					break;
				case 's':
					if (ctx.source != null)
						middle = ctx.source.charStats().himher();
					break;
				case 'S':
					if ((ctx.target != null) && (ctx.target instanceof MOB))
						middle = ((MOB) ctx.target).charStats().himher();
					break;
				case 't':
				case 'T':
					if (ctx.target != null)
						middle = ctx.target.name();
					break;
				case 'w':
					middle = ctx.primaryItem != null ? ctx.primaryItem.owner().Name() : middle;
					break;
				case 'W':
					middle = ctx.secondaryItem != null ? ctx.secondaryItem.owner().Name() : middle;
					break;
				case 'x':
				case 'X':
					if (lastKnownLocation != null)
					{
						middle = "";
						Exit E = null;
						int dir = -1;
						if ((t < vchrs.length() - 2) && (CMLib.directions().getGoodDirectionCode("" + vchrs.charAt(t + 2)) >= 0))
						{
							dir = CMLib.directions().getGoodDirectionCode("" + vchrs.charAt(t + 2));
							E = lastKnownLocation.getExitInDir(dir);
							vchrs.deleteCharAt(t+2);
						}
						else
						{
							int i = 0;
							while (((++i) < 100) && (E == null))
							{
								dir = CMLib.dice().roll(1, Directions.NUM_DIRECTIONS(), -1);
								E = lastKnownLocation.getExitInDir(dir);
							}
						}
						if ((dir >= 0) && (E != null))
						{
							if (c == 'x')
								middle = CMLib.directions().getDirectionName(dir);
							else
								middle = E.name();
						}
					}
					break;
				case 'y':
					if (ctx.source != null)
						middle = ctx.source.charStats().sirmadam();
					break;
				case 'Y':
					if ((ctx.target != null) && (ctx.target instanceof MOB))
						middle = ((MOB)ctx.target).charStats().sirmadam();
					break;
				case '(': // SC17: $(name), a named local
				{
					middle = "";
					final int x = vchrs.indexOf(")",t+2);
					if (x > t)
					{
						final String val = ctx.locals.get(vchrs.substring(t+2, x).trim());
						middle = (val == null) ? "" : val;
						replLen = x - t + 1;
					}
					break;
				}
				case '<':
				{
					final int x = vchrs.indexOf(">",t+2);
					if (x > t)
					{
						String mid = vchrs.substring(t+2, x);
						final int y = mid.indexOf(' ');
						Environmental E = null;
						String arg1 = "";
						if (y >= 0)
						{
							arg1 = mid.substring(0, y).trim();
							E = getArgumentItem(arg1, ctx);
							mid = mid.substring(y + 1).trim();
						}
						if (arg1.length() > 0)
							middle = getVar(E, arg1, mid, ctx);
						replLen=x-t+1;
					}
					break;
				}
				case '[':
				{
					middle = "";
					final int x = vchrs.indexOf("]",t+2);
					if (x > t)
					{
						String mid = vchrs.substring(t+2, x);
						final int y = mid.indexOf(' ');
						if (y > 0)
						{
							final int num = CMath.s_int(mid.substring(0, y).trim());
							mid = mid.substring(y + 1).trim();
							final Quest Q = getQuest(mid);
							if (Q != null)
								middle = Q.getQuestItemName(num);
						}
						replLen=x-t+1;
					}
					break;
				}
				case '{':
				{
					middle = "";
					final int x = vchrs.indexOf("}",t+2);
					if (x > t)
					{
						String mid = vchrs.substring(t+2, x);
						final int y = mid.indexOf(' ');
						if (y > 0)
						{
							final int num = CMath.s_int(mid.substring(0, y).trim());
							mid = mid.substring(y + 1).trim();
							final Quest Q = getQuest(mid);
							if (Q != null)
								middle = Q.getQuestMobName(num);
						}
						replLen=x-t+1;
					}
					break;
				}
				case '%':
				{
					middle = "";
					final int x = vchrs.indexOf("%",t+2);
					if (x > t)
					{
						middle = functify(ctx, vchrs.substring(t+2, x).trim());
						replLen=x-t+1;
					}
					break;
				}
			}
			if((t+replLen<vchrs.length()-1)
			&&(vchrs.charAt(t+replLen)=='.'))
			{
				if(vchrs.charAt(t+replLen+1)=='$')
					vchrs.replace(t+replLen+1, vchrs.length(), varify(ctx,vchrs.substring(t+replLen+1, vchrs.length())));
				final String vrest = vchrs.substring(t+replLen);
				if(vrest.startsWith(".LENGTH#"))
				{
					middle=""+CMParms.parse(middle).size();
					replLen += 8;
				}
				else
				if(vrest.startsWith(".DECOMMA#"))
				{
					middle=""+CMParms.combineQuoted(CMParms.parseCommas(middle,false),0);
					replLen += 9;
				}
				else
				if(vrest.startsWith(".DESEMICOLON#"))
				{
					middle=""+CMParms.combineQuoted(CMParms.parseSemicolons(middle,false),0);
					replLen += 13;
				}
				else
				if((t+replLen<vchrs.length()-1)
				&&(Character.isDigit(vchrs.charAt(t+replLen+1))))
				{
					final int digStartOffset=replLen+1;
					replLen+=2;
					while((t+replLen<vchrs.length())
					&&(Character.isDigit(vchrs.charAt(t+replLen))))
						replLen++;
					final int y=CMath.s_int(vchrs.substring(t+digStartOffset,t+replLen).trim());
					final boolean rest=vchrs.substring(t+replLen).startsWith("..");
					if(rest)
						replLen+=2;
					final List<String> V=CMParms.parse(middle);
					if((V.size()>0)&&(y>=0))
					{
						if(y>=V.size())
							middle="";
						else
						if(rest)
							middle=CMParms.combine(V,y);
						else
							middle=V.get(y);
					}
				}
			}
			if((middle.length()>0)&&(middle.indexOf('$')>=0))
				middle=CMStrings.replaceAll(middle, "$"+c, "_"+c); // prevent recursion!
			vchrs.replace(t, t+replLen, middle);
			t=vchrs.indexOf("$",t);
		}
		return CMStrings.deEscape(vchrs.toString());
	}

	protected PairList<String,String> getScriptVarSet(final String mobname, final String varname)
	{
		final PairList<String,String> set=new PairVector<String,String>();
		if(mobname.equals("*"))
		{
			for(final Iterator<String> k = resources._findResourceKeys("SCRIPTVAR-");k.hasNext();)
			{
				final String key=k.next();
				if(key.startsWith("SCRIPTVAR-"))
				{
					@SuppressWarnings("unchecked")
					final Map<String,?> H=(Map<String,?>)resources._getResource(key);
					if(varname.equals("*"))
					{
						if(H!=null)
						{
							for(final String vn : H.keySet())
								set.add(key.substring(10),vn);
						}
					}
					else
						set.add(key.substring(10),varname);
				}
			}
		}
		else
		{
			@SuppressWarnings("unchecked")
			final Map<String,?> H=ObjectVars.isHost(mobname)?ObjectVars.vars(mobname):(Map<String,?>)resources._getResource("SCRIPTVAR-"+mobname); // SC4
			if(varname.equals("*"))
			{
				if(H!=null)
				{
					for(final String vn : H.keySet())
						set.add(mobname,vn);
				}
			}
			else
				set.add(mobname,varname);
		}
		return set;
	}

	protected String getStatValue(final Environmental E, final String arg2)
	{
		boolean found=false;
		String val="";
		final String uarg2=arg2.toUpperCase().trim();
		for(int i=0;i<E.getStatCodes().length;i++)
		{
			if(E.getStatCodes()[i].equals(uarg2))
			{
				val=E.getStat(uarg2);
				found=true;
				break;
			}
		}
		if((!found)&&(E instanceof MOB))
		{
			final MOB M=(MOB)E;
			for(final int i : CharStats.CODES.ALLCODES())
			{
				if(CharStats.CODES.NAME(i).equals(uarg2)||CharStats.CODES.DESC(i).equals(uarg2))
				{
					val=""+M.charStats().getStat(CharStats.CODES.NAME(i)); //yes, this is right
					found=true;
					break;
				}
			}
			if(!found)
			{
				final int dex=CMParms.indexOf(M.curState().getStatCodes(), uarg2);
				if(dex>=0)
				{
					val=M.curState().getStat(M.curState().getStatCodes()[dex]);
					found=true;
				}
			}
			if(!found)
			{
				final int dex=CMParms.indexOf(M.phyStats().getStatCodes(), uarg2);
				if(dex>=0)
				{
					val=M.phyStats().getStat(M.phyStats().getStatCodes()[dex]);
					found=true;
				}
			}
			if((!found)&&(M.playerStats()!=null))
			{
				final int dex=CMParms.indexOf(M.playerStats().getStatCodes(), uarg2);
				if(dex>=0)
				{
					val=M.playerStats().getStat(M.playerStats().getStatCodes()[dex]);
					found=true;
				}
			}
			if((!found)&&(uarg2.startsWith("BASE")))
			{
				final int dex=CMParms.indexOf(M.baseState().getStatCodes(), uarg2.substring(4));
				if(dex>=0)
				{
					val=M.baseState().getStat(M.baseState().getStatCodes()[dex]);
					found=true;
				}
			}
			if((!found)&&(uarg2.startsWith("MAX")))
			{
				final int dex=CMParms.indexOf(M.maxState().getStatCodes(), uarg2.substring(3));
				if(dex>=0)
				{
					val=M.maxState().getStat(M.maxState().getStatCodes()[dex]);
					found=true;
				}
			}
			if((!found)&&(uarg2.equals("STINK")))
			{
				found=true;
				val=CMath.toPct(M.playerStats().getHygiene()/PlayerStats.HYGIENE_DELIMIT);
			}
			if((!found)
			&&(CMath.s_valueOf(GenericBuilder.GenMOBBonusFakeStats.class,uarg2)!=null))
			{
				found=true;
				val=CMLib.coffeeMaker().getAnyGenStat(M, uarg2);
			}
		}

		if((!found)
		&&(E instanceof Item)
		&&(CMath.s_valueOf(GenericBuilder.GenItemBonusFakeStats.class,uarg2)!=null))
		{
			found=true;
			val=CMLib.coffeeMaker().getAnyGenStat(E, uarg2);
		}

		if((!found)
		&&(E instanceof Physical)
		&&(CMath.s_valueOf(GenericBuilder.GenPhysBonusFakeStats.class,uarg2)!=null))
		{
			found=true;
			val=CMLib.coffeeMaker().getAnyGenStat(E, uarg2);
		}
		if((!found)&&(E instanceof PhysicalAgent)) // N3: one of its behaviours' stats, as STAT($i SCHEDULE)
		{
			for(final Enumeration<Behavior> b=((PhysicalAgent)E).behaviors();b.hasMoreElements()&&(!found);)
			{
				final Behavior B=b.nextElement();
				if((B!=null)&&(CMParms.indexOf(B.getStatCodes(),uarg2)>=0))
				{
					val=B.getStat(uarg2);
					found=true;
				}
			}
		}
		if(!found)
			return null;
		return val;
	}

	protected String getGStatValue(final Environmental E, String arg2)
	{
		if(E==null)
			return null;
		boolean found=false;
		String val="";
		for(int i=0;i<E.getStatCodes().length;i++)
		{
			if(E.getStatCodes()[i].equalsIgnoreCase(arg2))
			{
				val=E.getStat(arg2);
				found=true; break;
			}
		}
		if(!found)
		if(E instanceof MOB)
		{
			arg2=arg2.toUpperCase().trim();
			final GenericBuilder.GenMOBCode element = (GenericBuilder.GenMOBCode)CMath.s_valueOf(GenericBuilder.GenMOBCode.class,arg2);
			if(element != null)
			{
				val=CMLib.coffeeMaker().getGenMobStat((MOB)E,element.name());
				found=true;
			}
			if(!found)
			{
				final MOB M=(MOB)E;
				for(final int i : CharStats.CODES.ALLCODES())
				{
					if(CharStats.CODES.NAME(i).equals(arg2)||CharStats.CODES.DESC(i).equals(arg2))
					{
						val=""+M.charStats().getStat(CharStats.CODES.NAME(i));
						found=true;
						break;
					}
				}
				if(!found)
				{
					final int dex=CMParms.indexOf(M.curState().getStatCodes(), arg2);
					if(dex>=0)
					{
						val=M.curState().getStat(M.curState().getStatCodes()[dex]);
						found=true;
					}
				}
				if(!found)
				{
					final int dex=CMParms.indexOf(M.phyStats().getStatCodes(), arg2);
					if(dex>=0)
					{
						val=M.phyStats().getStat(M.phyStats().getStatCodes()[dex]);
						found=true;
					}
				}
				if((!found)&&(M.playerStats()!=null))
				{
					final int dex=CMParms.indexOf(M.playerStats().getStatCodes(), arg2);
					if(dex>=0)
					{
						val=M.playerStats().getStat(M.playerStats().getStatCodes()[dex]);
						found=true;
					}
					else
					if(arg2.equalsIgnoreCase("ACCOUNT") && (M.playerStats().getAccount()!=null))
					{
						val = M.playerStats().getAccount().getAccountName();
						found=true;
					}
				}
				if((!found)&&(arg2.startsWith("BASE")))
				{
					final String arg4=arg2.substring(4);
					final int dex=CMParms.indexOf(M.baseState().getStatCodes(), arg4);
					if(dex>=0)
					{
						val=M.baseState().getStat(M.baseState().getStatCodes()[dex]);
						found=true;
					}
					if(!found)
					{
						for(final int i : CharStats.CODES.ALLCODES())
						{
							if(CharStats.CODES.NAME(i).equals(arg4)||CharStats.CODES.DESC(i).equals(arg4))
							{
								val=""+M.baseCharStats().getStat(CharStats.CODES.NAME(i));
								found=true;
								break;
							}
						}
					}
				}
				if((!found)&&(arg2.startsWith("MAX")))
				{
					final String arg4=arg2.substring(3);
					final int dex=CMParms.indexOf(M.maxState().getStatCodes(), arg4);
					if(dex>=0)
					{
						val=M.maxState().getStat(M.maxState().getStatCodes()[dex]);
						found=true;
					}
				}
				if((!found)&&(arg2.equals("STINK")))
				{
					found=true;
					val=CMath.toPct(M.playerStats().getHygiene()/PlayerStats.HYGIENE_DELIMIT);
				}
			}
		}
		else
		if(E instanceof Item)
		{
			final GenericBuilder.GenItemCode code = (GenericBuilder.GenItemCode)CMath.s_valueOf(GenericBuilder.GenItemCode.class, arg2.toUpperCase().trim());
			if(code != null)
			{
				val=CMLib.coffeeMaker().getGenItemStat((Item)E,code.name());
				found=true;
			}
		}
		if((!found)
		&&(E instanceof Physical))
		{
			if(CMLib.coffeeMaker().isAnyGenStat(E, arg2))
				return CMLib.coffeeMaker().getAnyGenStat(E, arg2);
			if((!found)&&(arg2.startsWith("BASE")))
			{
				final String arg4=arg2.substring(4);
				for(int i=0;i<((Physical)E).basePhyStats().getStatCodes().length;i++)
				{
					if(((Physical)E).basePhyStats().getStatCodes()[i].equals(arg4))
					{
						val=((Physical)E).basePhyStats().getStat(((Physical)E).basePhyStats().getStatCodes()[i]);
						found=true;
						break;
					}
				}
			}
		}
		if(found)
			return val;
		return null;
	}

	protected List<Ability> getQuestAbilities()
	{
		final List<Ability> able=new ArrayList<Ability>();
		final Quest Q=defaultQuest();
		if(Q!=null)
		{
			final Ability A=(Ability)Q.getDesignatedObject("ABILITY");
			if(A!=null)
				able.add(A);
			@SuppressWarnings("unchecked")
			final
			List<Ability> grp=(List<Ability>)Q.getDesignatedObject("ABILITYGROUP");
			if(grp != null)
				able.addAll(grp);
			final Object O=Q.getDesignatedObject("TOOL");
			if(O instanceof Ability)
				able.add((Ability)O);
			@SuppressWarnings("unchecked")
			final
			List<Object> grp2=(List<Object>)Q.getDesignatedObject("TOOLGROUP");
			if(grp2 != null)
			{
				for(final Object O1 : grp2)
				{
					if(O1 instanceof Ability)
						able.add((Ability)O1);
				}
			}
		}
		return able;
	}

	protected Ability getAbility(final String arg)
	{
		if((arg==null)||(arg.length()==0))
			return null;
		Ability A;
		A=CMClass.getAbility(arg);
		if(A!=null)
			return A;
		for(final Ability A1 : getQuestAbilities())
		{
			if(A1.ID().equalsIgnoreCase(arg))
				return (Ability)A1.copyOf();
		}
		return null;
	}

	protected Ability findAbility(final String arg)
	{
		if((arg==null)||(arg.length()==0))
			return null;
		Ability A;
		A=CMClass.findAbility(arg);
		if(A!=null)
			return A;
		final List<Ability> ableV=getQuestAbilities();
		A=(Ability)CMLib.english().fetchEnvironmental(ableV,arg,true);
		if(A==null)
			A=(Ability)CMLib.english().fetchEnvironmental(ableV,arg,false);
		if(A!=null)
			return (Ability)A.copyOf();
		return null;
	}

	protected static final boolean safeResetCheck()
	{
		final java.lang.StackTraceElement[] stackTrace=Thread.currentThread().getStackTrace();
		int count = 0;
		for (final StackTraceElement element : stackTrace)
			if(("safeResetRoom".equalsIgnoreCase(element.getMethodName()))
			||("safeResetArea".equalsIgnoreCase(element.getMethodName())))
				count ++;
		return count < 2;
	}

	protected static final void safeResetRoom(final Room room)
	{
		if(safeResetCheck() && (room != null))
			CMLib.map().resetRoom(room, true);
	}

	protected static final void safeResetArea(final Area area)
	{
		if(safeResetCheck() && (area != null))
			CMLib.map().resetArea(area);
	}

	@Override
	public void setVar(final String baseName, String key, String val)
	{
		final PairList<String,String> vars=getScriptVarSet(baseName,key);
		for(int v=0;v<vars.size();v++)
		{
			final String name=vars.elementAtFirst(v);
			key=vars.elementAtSecond(v).toUpperCase();
			@SuppressWarnings("unchecked")
			Map<String,String> H=ObjectVars.isHost(name)?ObjectVars.vars(name):(Map<String,String>)resources._getResource("SCRIPTVAR-"+name); // SC4: an object's own
			if((H==null)&&ObjectVars.isHost(name))
				continue; // the object is gone
			if((H==null)
			&&(defaultQuestName!=null)
			&&(defaultQuestName.length()>0))
			{
				final MOB M=CMLib.players().getPlayerAllHosts(name);
				if(M!=null)
				{
					for(final Enumeration<ScriptingEngine> e=M.scripts();e.hasMoreElements();)
					{
						final ScriptingEngine SE=e.nextElement();
						if((SE!=null)
						&&(SE!=this)
						&&(defaultQuestName.equalsIgnoreCase(SE.defaultQuestName()))
						&&(SE.isVar(name,key)))
						{
							SE.setVar(name,key,val);
							return;
						}
					}
				}
			}
			if(H==null)
			{
				if(val.length()==0)
					continue;

				H=Collections.synchronizedMap(new HashMap<String,String>());
				resources._submitResource("SCRIPTVAR-"+name,H);
				if(H==null)
					continue;
			}
			if(val.length()>0)
			{
				switch(val.charAt(0))
				{
				case '=': // SC11: =value is the value as it is, -5 or *x
					val=val.substring(1);
					break;
				case '%': // SC11: %N, the remainder
				case '^': // ^N, the power
				{
					final String num=H.get(key);
					if(CMath.isNumber(val.substring(1).trim()))
					{
						final long have=CMath.s_long((num==null)?"0":num.trim());
						final long by=CMath.s_long(val.substring(1).trim());
						if(val.charAt(0)=='%')
							val=(by==0)?Long.toString(have):Long.toString(have%by);
						else
							val=Long.toString((long)Math.pow(have,by));
					}
					break;
				}
				case '+':
					if(val.equals("++"))
					{
						String num=H.get(key);
						if(num==null)
							num="0";
						val=Long.toString(CMath.s_long(num.trim())+1);
					}
					else
					{
						String num=H.get(key);
						// add via +number form
						if(CMath.isNumber(val.substring(1).trim()))
						{
							if(num==null)
								num="0";
							val=val.substring(1);
							final long amount=CMath.s_long(val.trim());
							val=Long.toString(CMath.s_long(num.trim())+amount);
						}
						else
						if((num!=null)&&(CMath.isNumber(num)))
							val=num;
					}
					break;
				case '-':
					if(val.equals("--"))
					{
						String num=H.get(key);
						if(num==null)
							num="0";
						val=Long.toString(CMath.s_long(num.trim())-1);
					}
					else
					{
						// subtract -number form
						String num=H.get(key);
						if(CMath.isNumber(val.substring(1).trim()))
						{
							val=val.substring(1);
							final long amount=CMath.s_long(val.trim());
							if(num==null)
								num="0";
							val=Long.toString(CMath.s_long(num.trim())-amount);
						}
						else
						if((num!=null)&&(CMath.isNumber(num)))
							val=num;
					}
					break;
				case '*':
				{
					// multiply via *number form
					String num=H.get(key);
					if(CMath.isNumber(val.substring(1).trim()))
					{
						val=val.substring(1);
						final long amount=CMath.s_long(val.trim());
						if(num==null)
							num="0";
						val=Long.toString(CMath.s_long(num.trim())*amount);
					}
					else
					if((num!=null)&&(CMath.isNumber(num)))
						val=num;
					break;
				}
				case '/':
				{
					// divide /number form
					String num=H.get(key);
					if(CMath.isNumber(val.substring(1).trim()))
					{
						val=val.substring(1);
						final long amount=CMath.s_long(val.trim());
						if(num==null)
							num="0";
						if(amount==0)
							Log.errOut("Scripting","Scripting SetVar error: Division by 0: "+name+"/"+key+"="+val);
						else
							val=Long.toString(CMath.s_long(num.trim())/amount);
					}
					else
					if((num!=null)&&(CMath.isNumber(num)))
						val=num;
					break;
				}
				default:
					break;
				}
			}
			final String oldVal=H.get(key);
			if(H.containsKey(key))
				H.remove(key);
			if(val.trim().length()>0)
				H.put(key,val);
			if(H.size()==0)
				resources._removeResource("SCRIPTVAR-"+name);
			ScriptVarWatch.changed(scope, ObjectVars.describe(name), key, val.trim(), varChangeSource());
			ScriptEvents.varChanged(scope, name, key, oldVal, val.trim()); // ST25: VAR_PROG
			if(ScriptTrace.isOn())
				ScriptTrace.varChanged(ObjectVars.describe(name), key, val.trim());
		}
	}

	/**
	 * Where this engine's scripts came from, for VARS WATCH: their files, if
	 * loaded from any, and the room the engine last ran in.
	 * @return a short description
	 */
	protected String varChangeSource()
	{
		final StringBuilder str=new StringBuilder("");
		try
		{
			final List<String> files=externalFiles();
			str.append(files.isEmpty()?"an inline script":String.join(", ",files));
		}
		catch(final Exception e)
		{
			str.append("a script");
		}
		final Room R=lastKnownLocation;
		if(R!=null)
			str.append(" in ").append(CMLib.map().getExtendedRoomID(R));
		return str.toString();
	}

	/**
	 * The names of the variables a host has in this engine's scope, those
	 * starting with the given prefix, for VARNAMES.
	 * @param host the host, or * for all
	 * @param prefix the start of the names, or "" for all
	 * @return the names, sorted, separated by spaces
	 */
	protected String varNames(final String host, final String prefix)
	{
		final TreeSet<String> names=new TreeSet<String>();
		final String up=prefix.toUpperCase();
		final PairList<String,String> set=getScriptVarSet(host,"*");
		for(int i=0;i<set.size();i++)
		{
			final String n=set.elementAtSecond(i).toUpperCase();
			if(n.startsWith(up))
				names.add(n);
		}
		return String.join(" ",names);
	}

	@Override
	public String[] parseEval(final String evaluable) throws ScriptParseException
	{
		final int STATE_MAIN=0;
		final int STATE_INFUNCTION=1;
		final int STATE_INFUNCQUOTE=2;
		final int STATE_POSTFUNCTION=3;
		final int STATE_POSTFUNCEVAL=4;
		final int STATE_POSTFUNCQUOTE=5;
		final int STATE_MAYFUNCTION=6;

		buildHashes();
		final List<String> V=new ArrayList<String>();
		if((evaluable==null)||(evaluable.trim().length()==0))
			return new String[]{};
		final char[] evalC=evaluable.toCharArray();
		int state=0;
		int dex=0;
		char lastQuote='\0';
		String s=null;
		int depth=0;
		int fdepth=0;
		for(int c=0;c<evalC.length;c++)
		{
			switch(state)
			{
			case STATE_MAIN:
			{
				if(Character.isWhitespace(evalC[c]))
				{
					s=new String(evalC,dex,c-dex).trim();
					if(s.length()>0)
					{
						s=s.toUpperCase();
						V.add(s);
						dex=c+1;
						if(funcH.containsKey(s))
							state=STATE_MAYFUNCTION;
						else
						if(!Connector.isConnector(s))
							throw new ScriptParseException("Unknown keyword: "+s);
					}
				}
				else
				if(Character.isLetter(evalC[c])
				||(Character.isDigit(evalC[c])
					&&(c>0)&&Character.isLetter(evalC[c-1])
					&&(c<evalC.length-1)
					&&Character.isLetter(evalC[c+1])))
				{ /* move along */
				}
				else
				{
					switch(evalC[c])
					{
					case '!':
					{
						if(c==evalC.length-1)
							throw new ScriptParseException("Bad Syntax on last !");
						V.add("NOT");
						dex=c+1;
						break;
					}
					case '(':
					{
						s=new String(evalC,dex,c-dex).trim();
						if(s.length()>0)
						{
							s=s.toUpperCase();
							V.add(s);
							V.add("(");
							dex=c+1;
							if(funcH.containsKey(s))
							{
								state=STATE_INFUNCTION;
								fdepth=0;
							}
							else
							if(Connector.isConnector(s))
								state=STATE_MAIN;
							else
								throw new ScriptParseException("Unknown keyword: "+s);
						}
						else
						{
							V.add("(");
							depth++;
							dex=c+1;
						}
						break;
					}
					case ')':
						s=new String(evalC,dex,c-dex).trim();
						if(s.length()>0)
							throw new ScriptParseException("Bad syntax before ) at: "+s);
						if(depth==0)
							throw new ScriptParseException("Unmatched ) character");
						V.add(")");
						depth--;
						dex=c+1;
						break;
					default:
						throw new ScriptParseException("Unknown character at: "+new String(evalC,dex,c-dex+1).trim()+": "+evaluable);
					}
				}
				break;
			}
			case STATE_MAYFUNCTION:
			{
				if(evalC[c]=='(')
				{
					V.add("(");
					dex=c+1;
					state=STATE_INFUNCTION;
					fdepth=0;
				}
				else
				if(!Character.isWhitespace(evalC[c]))
					throw new ScriptParseException("Expected ( at "+evalC[c]+": "+evaluable);
				break;
			}
			case STATE_POSTFUNCTION:
			{
				if(!Character.isWhitespace(evalC[c]))
				{
					switch(evalC[c])
					{
					case '=':
					case '>':
					case '<':
					case '!':
					{
						if(c==evalC.length-1)
							throw new ScriptParseException("Bad Syntax on last "+evalC[c]);
						if(!Character.isWhitespace(evalC[c+1]))
						{
							s=new String(evalC,c,2);
							if((Signs.getSign(s)==null)&&(evalC[c]!='!'))
								s=""+evalC[c];
						}
						else
							s=""+evalC[c];
						if(Signs.getSign(s)==null)
						{
							c=dex-1;
							state=STATE_MAIN;
							break;
						}
						V.add(s);
						dex=c+(s.length());
						c=c+(s.length()-1);
						state=STATE_POSTFUNCEVAL;
						break;
					}
					default:
						c=dex-1;
						state=STATE_MAIN;
						break;
					}
				}
				break;
			}
			case STATE_INFUNCTION:
			{
				if(evalC[c]==')')
				{
					if(fdepth<=0)
					{
						V.add(new String(evalC,dex,c-dex));
						V.add(")");
						dex=c+1;
						state=STATE_POSTFUNCTION;
					}
					else
						fdepth--;
				}
				else
				if((evalC[c]=='\'')||(evalC[c]=='`'))
				{
					lastQuote=evalC[c];
					state=STATE_INFUNCQUOTE;
				}
				else
				if((evalC[c]=='('))
					fdepth++;
				break;
			}
			case STATE_INFUNCQUOTE:
			{
				if((evalC[c]==lastQuote)
				&&((c==evalC.length-1)
					||((!Character.isLetter(evalC[c-1]))
						||(!Character.isLetter(evalC[c+1])))))
					state=STATE_INFUNCTION;
				break;
			}
			case STATE_POSTFUNCQUOTE:
			{
				if(evalC[c]==lastQuote)
				{
					if((V.size()>2)
					&&(Signs.getSign(V.get(V.size()-1))!=null)
					&&(V.get(V.size()-2).equals(")")))
					{
						final String sign=V.get(V.size()-1);
						V.remove(V.size()-1);
						V.remove(V.size()-1);
						final String prev=V.get(V.size()-1);
						if(prev.equals("("))
							s=sign+" "+new String(evalC,dex+1,c-dex);
						else
						{
							V.remove(V.size()-1);
							s=prev+" "+sign+" "+new String(evalC,dex+1,c-dex);
						}
						V.add(s);
						V.add(")");
						dex=c+1;
						state=STATE_MAIN;
					}
					else
						throw new ScriptParseException("Bad postfunc Eval somewhere");
				}
				break;
			}
			case STATE_POSTFUNCEVAL:
			{
				if(Character.isWhitespace(evalC[c]))
				{
					s=new String(evalC,dex,c-dex).trim();
					if(s.length()>0)
					{
						if((V.size()>1)
						&&(Signs.getSign(V.get(V.size()-1))!=null)
						&&(V.get(V.size()-2).equals(")")))
						{
							final String sign=V.get(V.size()-1);
							V.remove(V.size()-1);
							V.remove(V.size()-1);
							final String prev=V.get(V.size()-1);
							if(prev.equals("("))
								s=sign+" "+new String(evalC,dex+1,c-dex);
							else
							{
								V.remove(V.size()-1);
								s=prev+" "+sign+" "+new String(evalC,dex+1,c-dex);
							}
							V.add(s);
							V.add(")");
							dex=c+1;
							state=STATE_MAIN;
						}
						else
							throw new ScriptParseException("Bad postfunc Eval somewhere");
					}
				}
				else
				if(Character.isLetterOrDigit(evalC[c]))
				{ /* move along */
				}
				else
				if((evalC[c]=='\'')||(evalC[c]=='`'))
				{
					s=new String(evalC,dex,c-dex).trim();
					if(s.length()==0)
					{
						lastQuote=evalC[c];
						state=STATE_POSTFUNCQUOTE;
					}
				}
				break;
			}
			}
		}
		if((state==STATE_POSTFUNCQUOTE)
		||(state==STATE_INFUNCQUOTE))
			throw new ScriptParseException("Unclosed "+lastQuote+" in "+evaluable);
		if(depth>0)
			throw new ScriptParseException("Unclosed (  in "+evaluable);
		return CMParms.toStringArray(V);
	}

	public void pushEvalBoolean(final List<Object> stack, boolean trueFalse)
	{
		if(stack.size()>0)
		{
			final Object O=stack.get(stack.size()-1);
			if(O instanceof Connector)
			{
				final Connector connector=(Connector)O;
				stack.remove(stack.size()-1);
				if((stack.size()>0)
				&&((stack.get(stack.size()-1) instanceof Boolean)))
				{
					final boolean preTrueFalse=((Boolean)stack.get(stack.size()-1)).booleanValue();
					stack.remove(stack.size()-1);
					switch(connector)
					{
					case AND:
						trueFalse = preTrueFalse && trueFalse;
						break;
					case OR:
						trueFalse = preTrueFalse || trueFalse;
						break;
					case ANDNOT:
						trueFalse = preTrueFalse && (!trueFalse);
						break;
					case NOT:
					case ORNOT:
						trueFalse = preTrueFalse || (!trueFalse);
						break;
					}
				}
				else
				switch(connector)
				{
					case ANDNOT:
					case NOT:
					case ORNOT:
						trueFalse = !trueFalse;
						break;
					default:
						break;
				}
			}
			else
			if(O instanceof Boolean)
			{
				final boolean preTrueFalse=((Boolean)stack.get(stack.size()-1)).booleanValue();
				stack.remove(stack.size()-1);
				trueFalse=preTrueFalse&&trueFalse;
			}
		}
		stack.add(trueFalse?Boolean.TRUE:Boolean.FALSE);
	}

	/**
	 * Returns the index, in the given string List, of the given string, starting
	 * from the given index.  If the string to search for contains more than one
	 * "word", where a word is defined in space-delimited terms respecting double-quotes,
	 * then it will return the index at which all the words in the parsed search string
	 * are found in the given string list.
	 * @param V the string list to search in
	 * @param str the string to search for
	 * @param start the index to start at (0 is good)
	 * @return the index at which the search string was found in the string list, or -1
	 */
	private static int strIndex(final List<String> V, final String str, final int start)
	{
		int x=(start == 0)?V.indexOf(str):V.subList(start, V.size()).indexOf(str);
		if(x>=0)
			return start + x;
		final List<String> V2=CMParms.parse(str);
		if(V2.size()==0)
			return -1;
		x=(start == 0)?V.indexOf(V2.get(0)):V.subList(start, V.size()).indexOf(V2.get(0));
		if(x>=0)
			x+=start;
		boolean found=false;
		while((x>=0)&&((x+V2.size())<=V.size())&&(!found))
		{
			found=true;
			for(int v2=1;v2<V2.size();v2++)
			{
				if(!V.get(x+v2).equals(V2.get(v2)))
				{
					found=false;
					break;
				}
			}
			if(!found)
			{
				final int y=V.subList(x+1, V.size()).indexOf(V2.get(0));
				x=(y>=0)?y+x+1:-1;
			}
		}
		if(found)
			return x;
		return -1;
	}

	/**
	 * Weird method.  Accepts a string list, a combiner (see below), a string buffer to search for,
	 * and a previously found index. The stringbuffer is always cleared during this call.
	 * If the stringbuffer was empty, the previous found index is returned. Otherwise:
	 * If the combiner is '&', the first index of the given stringbuffer in the string list is returned (or -1).
	 * If the combiner is '|', the previous index is returned if it was found, otherwise the first index
	 * of the given stringbuffer in the string list is returned (or -1).
	 * If the combiner is '&gt;', then the previous index is returned if it was not found (-1), otherwise the
	 * next highest found stringbuffer since the last string list search is returned, or (-1) if no more found.
	 * If the combiner is '&lt;', then the previous index is returned if it was not found (-1), otherwise the
	 * first found stringbuffer index is returned if it is lower than the previously found index.
	 * Other combiners return -1.
	 * @param V the string list to search
	 * @param combiner the combiner, either &,|,&lt;,or &gt;.
	 * @param buf the stringbuffer to search for, which is always cleared
	 * @param lastIndex the previously found index
	 * @return the result of the search
	 */
	private static int stringContains(final List<String> V, final char combiner, final StringBuffer buf, int lastIndex)
	{
		final String str=buf.toString().trim();
		if(str.length()==0)
			return lastIndex;
		buf.setLength(0);
		switch (combiner)
		{
		case '&':
			lastIndex = strIndex(V, str, 0);
			return lastIndex;
		case '|':
			if (lastIndex >= 0)
				return lastIndex;
			return strIndex(V, str, 0);
		case '>':
			if (lastIndex < 0)
				return lastIndex;
			return strIndex(V, str, lastIndex + 1);
		case '<':
		{
			if (lastIndex < 0)
				return lastIndex;
			final int newIndex = strIndex(V, str, 0);
			if (newIndex < lastIndex)
				return newIndex;
			return -1;
		}
		}
		return -1;
	}

	/**
	 * Main workhorse of the stringcontains mobprog function.
	 * @param V parsed string to search
	 * @param str the coded search function
	 * @param index a 1-dim array of the index in the coded search str to start the search at
	 * @param depth the number of close parenthesis to expect
	 * @return the last index in the coded search function evaluated
	 */
	private static int stringContains(final List<String> V, final char[] str, final int[] index, final int depth)
	{
		final StringBuffer buf=new StringBuffer("");
		int lastIndex=0;
		boolean quoteMode=false;
		char combiner='&';
		for(int i=index[0];i<str.length;i++)
		{
			switch(str[i])
			{
			case ')':
				if((depth>0)&&(!quoteMode))
				{
					index[0]=i;
					return stringContains(V,combiner,buf,lastIndex);
				}
				buf.append(str[i]);
				break;
			case ' ':
				buf.append(str[i]);
				break;
			case '&':
			case '|':
			case '>':
			case '<':
				if(quoteMode)
					buf.append(str[i]);
				else
				{
					lastIndex=stringContains(V,combiner,buf,lastIndex);
					combiner=str[i];
				}
				break;
			case '(':
				if(!quoteMode)
				{
					lastIndex=stringContains(V,combiner,buf,lastIndex);
					index[0]=i+1;
					final int newIndex=stringContains(V,str,index,depth+1);
					i=index[0];
					switch(combiner)
					{
					case '&':
						if((lastIndex<0)||(newIndex<0))
							lastIndex=-1;
						break;
					case '|':
						if(newIndex>=0)
							lastIndex=newIndex;
						break;
					case '>':
						if(newIndex<=lastIndex)
							lastIndex=-1;
						else
							lastIndex=newIndex;
						break;
					case '<':
						if((newIndex<0)||(newIndex>=lastIndex))
							lastIndex=-1;
						else
							lastIndex=newIndex;
						break;
					}
				}
				else
					buf.append(str[i]);
				break;
			case '\"':
				quoteMode=(!quoteMode);
				break;
			case '\\':
				if(i<str.length-1)
				{
					buf.append(str[i+1]);
					i++;
				}
				break;
			default:
				if(Character.isLetter(str[i]))
					buf.append(Character.toLowerCase(str[i]));
				else
					buf.append(str[i]);
				break;
			}
		}
		return stringContains(V,combiner,buf,lastIndex);
	}

	/**
	 * As the name implies, this is the implementation of the stringcontains mobprog function
	 * @param str1 the string to search in
	 * @param str2 the coded search expression
	 * @return the index of the found string in the first string
	 */
	protected final static int stringContainsFunctionImpl(final String str1, final String str2)
	{
		final StringBuffer buf1=new StringBuffer(str1.toLowerCase());
		for(int i=buf1.length()-1;i>=0;i--)
		{
			switch(buf1.charAt(i))
			{
			case ' ':
			case '\"':
			case '`':
				break;
			case '\'':
				buf1.setCharAt(i, '`');
				break;
			default:
				if(!Character.isLetterOrDigit(buf1.charAt(i)))
					buf1.setCharAt(i,' ');
				break;
			}
		}
		final StringBuffer buf2=new StringBuffer(str2.toLowerCase());
		for(int i=buf2.length()-1;i>=0;i--)
		{
			switch(buf2.charAt(i))
			{
			case ' ':
			case '\"':
			case '`':
				break;
			case '\'':
				buf2.setCharAt(i, '`');
				break;
			case ')': case '(': case '|': case '>': case '<':
				// these are the actual control codes for strcontains
				break;
			default:
				if(!Character.isLetterOrDigit(buf2.charAt(i)))
					buf2.setCharAt(i,' ');
				break;
			}
		}
		final List<String> V=CMParms.parse(buf1.toString());
		return stringContains(V,buf2.toString().toCharArray(),new int[]{0},0);
	}

	@Override
	public boolean eval(final MPContext ctx, final String[][] eval, final int startEval)
	{
		String[] tt=eval[0];
		final List<Object> stack=new ArrayList<Object>();
		for(int t=startEval;t<tt.length;t++)
		{
			final String s = tt[t];
			if(s.equals("("))
				stack.add(s);
			else
			if(s.equals(")"))
			{
				if(stack.size()>0)
				{
					if((!(stack.get(stack.size()-1) instanceof Boolean))
					||(stack.size()==1)
					||(!(stack.get(stack.size()-2)).equals("(")))
					{
						logError(ctx,"EVALCOND","SYNTAX",") Format error: "+CMParms.toListString(tt));
						return false;
					}
					final boolean b=((Boolean)stack.get(stack.size()-1)).booleanValue();
					stack.remove(stack.size()-1);
					stack.remove(stack.size()-1);
					pushEvalBoolean(stack,b);
				}
			}
			else
			if(Connector.isConnector(s))
			{
				Connector curr=Connector.getConnector(s);
				if((stack.size()>0)
				&&(stack.get(stack.size()-1) instanceof Connector))
				{
					final int old=((Connector)stack.get(stack.size()-1)).ordinal();
					stack.remove(stack.size()-1);
					curr=Connector.Map[old][curr.ordinal()];
				}
				stack.add(curr);
			}
			else
			if(funcH.containsKey(s))
			{
				final Integer funcCode=funcH.get(s);
				if((t==tt.length-1)
				||(!tt[t+1].equals("(")))
				{
					logError(ctx,"GENERAL","SYNTAX","No ( for fuction "+s+": "+CMParms.toListString(tt));
					return false;
				}
				t+=2;
				int tlen=0;
				while(((t+tlen)<tt.length)&&(!tt[t+tlen].equals(")")))
					tlen++;
				if((t+tlen)==tt.length)
				{
					logError(ctx,"GENERAL","SYNTAX","No ) for fuction "+tt[t-1]+": "+CMParms.toListString(tt));
					return false;
				}
				tickStatus=Tickable.STATUS_MISC+funcCode.intValue();
				boolean returnable=false;
				// SC1 and on: the newer functions as tests: true, or a value compared
				if(funcCode.intValue()>=EXTRA_FUNCS_FROM)
				{
					final StringBuilder inner=new StringBuilder();
					for(int i=0;i<tlen;i++)
						inner.append((i>0)?" ":"").append(tt[t+i]);
					returnable=extraTest(ctx,s,inner.toString());
				}
				else
				switch(funcCode.intValue())
				{
				case 1: // rand
				{
					String num=CMParms.cleanBit(tt[t+0]);
					if(num.endsWith("%"))
						num=num.substring(0,num.length()-1);
					final int arg=CMath.s_int(num);
					if(CMLib.dice().rollPercentage()<arg)
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 2: // has
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"HAS","Syntax","'"+eval[0][t]+"' in "+CMParms.toListString(eval[0]));
						return returnable;
					}
					if(E==null)
						returnable=false;
					else
					{
						if((E instanceof MOB)
						&&(((MOB)E).findItem(arg2)!=null))
							returnable = true;
						else
						if((E instanceof Room)
						&&(((Room)E).findItem(arg2)!=null))
							returnable=true;
						else
						{
							final Environmental E2=getArgumentItem(arg2,ctx);
							if(E instanceof MOB)
							{
								if(E2!=null)
									returnable=((MOB)E).isMine(E2);
								else
									returnable=(((MOB)E).findItem(arg2)!=null);
							}
							else
							if(E instanceof Item)
								returnable=CMLib.english().containsString(E.name(),arg2);
							else
							if(E instanceof Room)
							{
								if(E2 instanceof Item)
									returnable=((Room)E).isContent((Item)E2);
								else
									returnable=(((Room)E).findItem(null,arg2)!=null);
							}
							else
								returnable=false;
						}
					}
					break;
				}
				case 74: // hasnum
				{
					if (tlen == 1)
						tt = parseBits(eval, t, "cccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String item=varify(ctx,tt[t+1]);
					final String cmp=tt[t+2];
					final String value=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if((value.length()==0)||(item.length()==0)||(cmp.length()==0))
					{
						logError(ctx,"HASNUM","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					Item I=null;
					int num=0;
					if(E==null)
						returnable=false;
					else
					if(E instanceof MOB)
					{
						final MOB M=(MOB)E;
						for(int i=0;i<M.numItems();i++)
						{
							I=M.getItem(i);
							if(I==null)
								break;
							if((item.equalsIgnoreCase("all"))
							||(CMLib.english().containsString(I.Name(),item)))
								num++;
						}
						returnable=simpleEval(ctx.scripted,""+num,value,cmp,"HASNUM");
					}
					else
					if(E instanceof Item)
					{
						num=CMLib.english().containsString(E.name(),item)?1:0;
						returnable=simpleEval(ctx.scripted,""+num,value,cmp,"HASNUM");
					}
					else
					if(E instanceof Room)
					{
						final Room R=(Room)E;
						for(int i=0;i<R.numItems();i++)
						{
							I=R.getItem(i);
							if(I==null)
								break;
							if((item.equalsIgnoreCase("all"))
							||(CMLib.english().containsString(I.Name(),item)))
								num++;
						}
						returnable=simpleEval(ctx.scripted,""+num,value,cmp,"HASNUM");
					}
					else
						returnable=false;
					break;
				}
				case 67: // hastitle
				{
					if (tlen == 1)
						tt = parseBits(eval, t, "cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"HASTITLE","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(E instanceof MOB)
					{
						final MOB M=(MOB)E;
						returnable=(M.playerStats()!=null)&&(M.playerStats().getTitles().contains(arg2));
					}
					else
						returnable=false;
					break;
				}
				case 3: // worn
				{
					if (tlen == 1)
						tt = parseBits(eval, t, "cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"WORN","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(E==null)
						returnable=false;
					else
					if(E instanceof MOB)
						returnable=(((MOB)E).fetchItem(null,Wearable.FILTER_WORNONLY,arg2)!=null);
					else
					if(E instanceof Item)
						returnable=(CMLib.english().containsString(E.name(),arg2)&&(!((Item)E).amWearingAt(Wearable.IN_INVENTORY)));
					else
						returnable=false;
					break;
				}
				case 4: // isnpc
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
						returnable=!((MOB)E).isPlayer();
					break;
				}
				case 87: // isbirthday
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final MOB mob=(MOB)E;
						if(mob.playerStats()==null)
							 returnable=false;
						else
						{
							Room startRoom=mob.getStartRoom();
							if(startRoom == null)
								startRoom=CMLib.login().getDefaultStartRoom(mob);
							if(startRoom == null)
								startRoom=mob.location();
							final TimeClock C=CMLib.time().localClock(startRoom);
							final int month=C.getMonth();
							final int day=C.getDayOfMonth();
							int[] birthdayIndex = mob.playerStats().getBirthday();
							if(birthdayIndex.length<PlayerStats.BIRTHDEX_COUNT)
								birthdayIndex=Arrays.copyOf(birthdayIndex, PlayerStats.BIRTHDEX_COUNT);
							final int bday=birthdayIndex[PlayerStats.BIRTHDEX_DAY];
							final int bmonth=birthdayIndex[PlayerStats.BIRTHDEX_MONTH];
							if((C.getYear()==birthdayIndex[PlayerStats.BIRTHDEX_LASTYEARCELEBRATED])
							&&((month==bmonth)&&(day==bday)))
								returnable=true;
							else
								returnable=false;
						}
					}
					break;
				}
				case 5: // ispc
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
						returnable=((MOB)E).isPlayer();
					break;
				}
				case 6: // isgood
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Physical P=getArgumentItem(arg1,ctx);
					if(P==null)
						returnable=false;
					else
						returnable=CMLib.flags().isGood(P);
					break;
				}
				case 8: // isevil
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Physical P=getArgumentItem(arg1,ctx);
					if(P==null)
						returnable=false;
					else
						returnable=CMLib.flags().isEvil(P);
					break;
				}
				case 9: // isneutral
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Physical P=getArgumentItem(arg1,ctx);
					if(P==null)
						returnable=false;
					else
						returnable=CMLib.flags().isNeutral(P);
					break;
				}
				case 54: // isalive
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E!=null)&&((E instanceof MOB))&&(!((MOB)E).amDead()))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 58: // isable
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E!=null)&&((E instanceof MOB))&&(!((MOB)E).amDead()))
					{
						final ExpertiseLibrary X=(ExpertiseLibrary)CMLib.expertises().findDefinition(arg2,true);
						if(X!=null)
							returnable=((MOB)E).fetchExpertise(X.ID())!=null;
						else
							returnable=((MOB)E).findAbility(arg2)!=null;
					}
					else
						returnable=false;
					break;
				}
				case 112: // cansee
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final PhysicalAgent MP=getArgumentMOB(arg1,ctx);
					if(!(MP instanceof MOB))
						returnable=false;
					else
					{
						final MOB M=(MOB)MP;
						final Physical P=getArgumentItem(arg2,ctx);
						if(P==null)
							returnable=false;
						else
							returnable=CMLib.flags().canBeSeenBy(P, M);
					}
					break;
				}
				case 113: // canhear
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final PhysicalAgent MP=getArgumentMOB(arg1,ctx);
					if(!(MP instanceof MOB))
						returnable=false;
					else
					{
						final MOB M=(MOB)MP;
						final Physical P=getArgumentItem(arg2,ctx);
						if(P==null)
							returnable=false;
						else
							returnable=CMLib.flags().canBeHeardMovingBy(P, M);
					}
					break;
				}
				case 59: // isopen
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final int dir=CMLib.directions().getGoodDirectionCode(arg1);
					returnable=false;
					if(dir<0)
					{
						final Environmental E=getArgumentItem(arg1,ctx);
						if((E!=null)&&(E instanceof Container))
							returnable=((Container)E).isOpen();
						else
						if((E!=null)&&(E instanceof Exit))
							returnable=((Exit)E).isOpen();
					}
					else
					if(lastKnownLocation != null)
					{
						final Exit E=lastKnownLocation.getExitInDir(dir);
						if(E!=null)
							returnable= E.isOpen();
					}
					break;
				}
				case 60: // islocked
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final int dir=CMLib.directions().getGoodDirectionCode(arg1);
					returnable=false;
					if(dir<0)
					{
						final Environmental E=getArgumentItem(arg1,ctx);
						if((E!=null)&&(E instanceof Container))
							returnable=((Container)E).isLocked();
						else
						if((E!=null)&&(E instanceof Exit))
							returnable=((Exit)E).isLocked();
					}
					else
					if(lastKnownLocation!=null)
					{
						final Exit E=lastKnownLocation.getExitInDir(dir);
						if(E!=null)
							returnable= E.isLocked();
					}
					break;
				}
				case 10: // isfight
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
						returnable=((MOB)E).isInCombat();
					break;
				}
				case 11: // isimmort
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
						returnable=CMSecurity.isAllowed(((MOB)E),lastKnownLocation,CMSecurity.SecFlag.IMMORT);
					break;
				}
				case 12: // ischarmed
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Physical E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
						returnable=CMLib.flags().flaggedAffects(E,Ability.FLAG_CHARMING).size()>0;
					break;
				}
				case 15: // isfollow
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					if(((MOB)E).amFollowing()==null)
						returnable=false;
					else
					if(((MOB)E).amFollowing().location()!=lastKnownLocation)
						returnable=false;
					else
						returnable=true;
					break;
				}
				case 73: // isservant
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB))||(lastKnownLocation==null))
						returnable=false;
					else
					if((((MOB)E).getLiegeID()==null)||(((MOB)E).getLiegeID().length()==0))
						returnable=false;
					else
					if(lastKnownLocation.fetchInhabitant("$"+((MOB)E).getLiegeID()+"$")==null)
						returnable=false;
					else
						returnable=true;
					break;
				}
				case 95: // isspeaking
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB))||(lastKnownLocation==null))
						returnable=false;
					else
					{
						final MOB TM=(MOB)E;
						final Language L=CMLib.utensils().getLanguageSpoken(TM);
						if((L!=null)
						&&(!L.ID().equalsIgnoreCase("Common"))
						&&(L.ID().equalsIgnoreCase(arg2)||L.Name().equalsIgnoreCase(arg2)||arg2.equalsIgnoreCase("any")))
							returnable=true;
						else
						if(arg2.equalsIgnoreCase("common")||arg2.equalsIgnoreCase("none"))
							returnable=(L==null)||L.ID().equalsIgnoreCase("Common"); // SC9: only when it speaks no other
						else
							returnable=false;
					}
					break;
				}
				case 55: // ispkill
				{
					final String arg1=CMParms.cleanBit(tt[t+0]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					if(((MOB)E).isAttributeSet(MOB.Attrib.PLAYERKILL))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 7: // isname
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if(E==null)
						returnable=false;
					else
						returnable=CMLib.english().containsString(E.name(),arg2);
					break;
				}
				case 56: // name
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if(E==null)
						returnable=false;
					else
						returnable=simpleEvalStr(ctx.scripted,E.Name(),arg3,arg2,"NAME");
					break;
				}
				case 75: // currency
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if(E==null)
						returnable=false;
					else
						returnable=simpleEvalStr(ctx.scripted,CMLib.beanCounter().getCurrency(E),arg3,arg2,"CURRENCY");
					break;
				}
				case 61: // strin
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					String arg2;
					if(tt[t+1].equals("$$r"))
						arg2=CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(ctx.monster));
					else
						arg2=varify(ctx,tt[t+1]);
					if(arg2.startsWith("*"))
					{
						if(arg2.endsWith("*")&&(!arg2.endsWith("\\*")))
							returnable = arg1.toUpperCase().indexOf(CMStrings.replaceAll(arg2.substring(1,arg2.length()-1).toUpperCase(), "\\*", "*"))>=0;
						else
							returnable = arg1.toUpperCase().endsWith(CMStrings.replaceAll(arg2.substring(1).toUpperCase(), "\\*", "*"));
					}
					else
					if(arg2.endsWith("*")&&(!arg2.endsWith("\\*")))
						returnable = arg1.toUpperCase().startsWith(CMStrings.replaceAll(arg2.substring(0,arg2.length()-1).toUpperCase(), "\\*", "*"));
					else
					{
						arg2 = CMStrings.replaceAll(arg2, "\\*", "*");
						returnable=CMParms.parse(arg1.toUpperCase()).contains(arg2.toUpperCase());
					}
					break;
				}
				case 62: // callfunc
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					String found=null;
					final SubScript foundScript = findFunc(ctx.scripted, arg1);
					if(foundScript != null)
					{
						final String varg2=varify(ctx,arg2);
						final MPContext ctx2 = ctx.push(foundScript);
						ctx2.msg=varg2;
						found= execute(ctx2);
						if(found==null)
							found="";
						final String resp=found.trim().toLowerCase();
						if(resp.equals("cancel"))
							returnable=false;
						else
							returnable=resp.length()!=0;
					}
					else
					{
						// JS11: the host's own JScriptable
						found=(ctx.scripted instanceof PhysicalAgent)?ScriptEvents.jsFunction((PhysicalAgent)ctx.scripted,arg1,varify(ctx,arg2),(ctx.source!=null)?ctx.source:ctx.monster):null;
						if(found==null)
						{
							logError(ctx,"CALLFUNC","Unknown","Function: "+arg1);
							returnable = false;
						}
						else
						{
							final String resp=found.trim().toLowerCase();
							returnable=(!resp.equals("cancel"))&&(resp.length()!=0);
						}
					}
					break;
				}
				case 14: // affected
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					String arg2=varify(ctx,tt[t+1]);
					final Physical P=getArgumentItem(arg1,ctx);
					if(P==null)
						returnable=false;
					else
					if(arg2.equalsIgnoreCase("*"))
					{
						if(P instanceof MOB)
							returnable=((MOB)P).personalEffects().hasMoreElements();
						else
							returnable=(P.numEffects() > 0);
					}
					else
					{
						final Ability A=findAbility(arg2);
						if((A==null)||(arg2==null)||(arg2.length()==0))
							logError(ctx,"AFFECTED","RunTime",arg2+" is not a valid ability name.");
						else
						if(A!=null)
							arg2=A.ID();
						returnable=(P.fetchEffect(arg2)!=null);
					}
					break;
				}
				case 69: // isbehave
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					String arg2=varify(ctx,tt[t+1]);
					final PhysicalAgent P=getArgumentItem(arg1,ctx);
					if(P==null)
						returnable=false;
					else
					if(arg2.equalsIgnoreCase("*"))
						returnable=(P.numBehaviors() > 0);
					else
					{
						final Behavior B=CMClass.findBehavior(arg2);
						if(B!=null)
							arg2=B.ID();
						returnable=(P.fetchBehavior(arg2)!=null);
					}
					break;
				}
				case 70: // ipaddress
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((E==null)||(!(E instanceof MOB))||(((MOB)E).isMonster()))
						returnable=false;
					else
						returnable=simpleEvalStr(ctx.scripted,((MOB)E).session().getAddress(),arg3,arg2,"ADDRESS");
					break;
				}
				case 28: // questwinner
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentMOB(tt[t+0],ctx);
					if(E!=null)
						arg1=E.Name();
					if(arg2.equalsIgnoreCase("previous"))
					{
						returnable=true;
						final String quest=defaultQuestName();
						if((quest!=null)
						&&(quest.length()>0))
						{
							ScriptingEngine prevE=null;
							final List<ScriptingEngine> list=new LinkedList<ScriptingEngine>();
							for(final Enumeration<ScriptingEngine> e = ctx.scripted.scripts();e.hasMoreElements();)
								list.add(e.nextElement());
							for(final Enumeration<Behavior> b=ctx.scripted.behaviors();b.hasMoreElements();)
							{
								final Behavior B=b.nextElement();
								if(B instanceof ScriptingEngine)
									list.add((ScriptingEngine)B);
							}
							for(final ScriptingEngine engine : list)
							{
								if((engine!=null)
								&&(engine.defaultQuestName()!=null)
								&&(engine.defaultQuestName().length()>0))
								{
									if(engine == this)
									{
										if(prevE != null)
										{
											final Quest Q=getQuest(prevE.defaultQuestName());
											if(Q != null)
												returnable=Q.wasWinner(arg1);
										}
										break;
									}
									prevE=engine;
								}
							}
						}
					}
					else
					{
						final Quest Q=getQuest(arg2);
						if(Q==null)
							returnable=false;
						else
							returnable=Q.wasWinner(arg1);
					}
					break;
				}
				case 93: // questscripted
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final PhysicalAgent E=getArgumentMOB(tt[t+0],ctx);
					final String arg2=tt[t+1];
					if(arg2.equalsIgnoreCase("ALL"))
					{
						returnable=false;
						for(final Enumeration<Quest> q=CMLib.quests().enumQuests();q.hasMoreElements();)
						{
							final Quest Q=q.nextElement();
							if(Q.running())
							{
								if(E!=null)
								{
									for(final Enumeration<ScriptingEngine> e=E.scripts();e.hasMoreElements();)
									{
										final ScriptingEngine SE=e.nextElement();
										if((SE!=null)&&(SE.defaultQuestName().equalsIgnoreCase(Q.name())))
											returnable=true;
									}
									for(final Enumeration<Behavior> b=E.behaviors();b.hasMoreElements();)
									{
										final Behavior B=b.nextElement();
										if(B instanceof ScriptingEngine)
										{
											final ScriptingEngine SE=(ScriptingEngine)B;
											if((SE.defaultQuestName().equalsIgnoreCase(Q.name())))
												returnable=true;
										}
									}
								}
							}
						}
					}
					else
					{
						final Quest Q=getQuest(arg2);
						returnable=false;
						if((Q!=null)&&(E!=null))
						{
							for(final Enumeration<ScriptingEngine> e=E.scripts();e.hasMoreElements();)
							{
								final ScriptingEngine SE=e.nextElement();
								if((SE!=null)&&(SE.defaultQuestName().equalsIgnoreCase(Q.name())))
									returnable=true;
							}
							for(final Enumeration<Behavior> b=E.behaviors();b.hasMoreElements();)
							{
								final Behavior B=b.nextElement();
								if(B instanceof ScriptingEngine)
								{
									final ScriptingEngine SE=(ScriptingEngine)B;
									if((SE.defaultQuestName().equalsIgnoreCase(Q.name())))
										returnable=true;
								}
							}
						}
					}
					break;
				}
				case 94: // questroom
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					final Quest Q=getQuest(arg2);
					if(Q==null)
						returnable=false;
					else
					if((arg1.indexOf('#')>=0)||(CMath.isNumber(arg1))||(!tt[t+0].startsWith("$")))
						returnable=(Q.getQuestRoomIndex(arg1)>=0);
					else
					{
						final Room lastLastKnownLocation=lastKnownLocation;
						lastKnownLocation=null;
						Object O;
						try
						{
							O=getArgumentMOB(tt[t+0],ctx);
							if(O==null)
								O=getArgumentItem(tt[t+0],ctx);
						}
						finally
						{
							lastKnownLocation=lastLastKnownLocation;
						}
						if(!(O instanceof Environmental))
							returnable=(Q.getQuestRoomIndex(arg1)>=0);
						else
						{
							final Room R=CMLib.map().roomLocation((Environmental)O);
							if(R!=null)
								returnable=(Q.getQuestRoomIndex(CMLib.map().getExtendedRoomID(R))>=0);
							else
								returnable=(Q.getQuestRoomIndex(arg1)>=0);
						}
					}
					break;
				}
				case 114: // questarea
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					final Quest Q=getQuest(arg2);
					if(Q==null)
						returnable=false;
					else
					{
						int num=1;
						Environmental E=Q.getQuestRoom(num);
						returnable = false;
						final Area parent=CMLib.map().getArea(arg1);
						if(parent == null)
							logError(ctx,"QUESTAREA","NoArea",CMParms.combine(tt,t));
						else
						while(E!=null)
						{
							final Area A=CMLib.map().areaLocation(E);
							if((A==parent)||(parent.isChild(A))||(A.isChild(parent)))
							{
								returnable=true;
								break;
							}
							num++;
							E=Q.getQuestRoom(num);
						}
					}
					break;
				}
				case 29: // questmob
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					final PhysicalAgent E=getArgumentMOB(tt[t+0],ctx);
					if(arg2.equalsIgnoreCase("ALL"))
					{
						returnable=false;
						for(final Enumeration<Quest> q=CMLib.quests().enumQuests();q.hasMoreElements();)
						{
							final Quest Q=q.nextElement();
							if(Q.running())
							{
								if(E!=null)
								{
									if(Q.isObjectInUse(E))
									{
										returnable=true;
										break;
									}
								}
								else
								if(Q.getQuestMobIndex(arg1)>=0)
								{
									returnable=true;
									break;
								}
							}
						}
					}
					else
					{
						final Quest Q=getQuest(arg2);
						if(Q==null)
							returnable=false;
						else
							returnable=(Q.getQuestMobIndex(arg1)>=0);
					}
					break;
				}
				case 31: // isquestmobalive
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					if(arg2.equalsIgnoreCase("ALL"))
					{
						returnable=false;
						for(final Enumeration<Quest> q=CMLib.quests().enumQuests();q.hasMoreElements();)
						{
							final Quest Q=q.nextElement();
							if(Q.running())
							{
								MOB M=null;
								if(CMath.s_int(arg1.trim())>0)
									M=Q.getQuestMob(CMath.s_int(arg1.trim()));
								else
									M=Q.getQuestMob(Q.getQuestMobIndex(arg1));
								if(M!=null)
								{
									returnable=(!M.amDead()) && (!M.amDestroyed());
									break;
								}
							}
						}
					}
					else
					{
						final Quest Q=getQuest(arg2);
						if(Q==null)
							returnable=false;
						else
						{
							MOB M=null;
							if(CMath.s_int(arg1.trim())>0)
								M=Q.getQuestMob(CMath.s_int(arg1.trim()));
							else
								M=Q.getQuestMob(Q.getQuestMobIndex(arg1));
							if(M==null)
								returnable=false;
							else
								returnable=(!M.amDead()) && (!M.amDestroyed());
						}
					}
					break;
				}
				case 111: // itemcount
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final Environmental E=getArgumentItem(arg1,ctx);
					final String arg3=varify(ctx,tt[t+2]);
					if(arg2.length()==0)
					{
						logError(ctx,"ITEMCOUNT","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(E==null)
						returnable=false;
					else
					{
						int num=0;
						if(E instanceof Armor)
							num=((Item)E).numberOfItems();
						else
						if(E instanceof RawMaterial)
							num=((Item)E).phyStats().weight();
						else
						if(E instanceof Container)
						{
							num++;
							for(final Item I : ((Container)E).getContents())
								num+=I.numberOfItems();
						}
						else
						if(E instanceof Item)
							num=((Item)E).numberOfItems();
						else
						if(E instanceof MOB)
						{
							for(final Enumeration<Item> i=((MOB)E).items();i.hasMoreElements();)
								num += i.nextElement().numberOfItems();
						}
						else
						if(E instanceof Room)
						{
							for(final Enumeration<Item> i=((Room)E).items();i.hasMoreElements();)
								num += i.nextElement().numberOfItems();
						}
						else
						if(E instanceof Area)
						{
							for(final Enumeration<Room> r=((Area)E).getFilledCompleteMap();r.hasMoreElements();)
							{
								for(final Enumeration<Item> i=r.nextElement().items();i.hasMoreElements();)
									num += i.nextElement().numberOfItems();
							}
						}
						else
							returnable=false;
						returnable=simpleEval(ctx.scripted,""+num,arg3,arg2,"ITEMCOUNT");
					}
					break;
				}
				case 32: // nummobsinarea
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					String arg1=varify(ctx,tt[t+0]).toUpperCase();
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					int num=0;
					MaskingLibrary.CompiledZMask MASK=null;
					if((arg1.toUpperCase().startsWith("MASK")&&(arg1.substring(4).trim().startsWith("="))))
					{
						arg1=arg1.substring(4).trim();
						arg1=arg1.substring(1).trim();
						MASK=CMLib.masking().maskCompile(arg1);
					}
					for(final Enumeration<Room> e=areaRooms();e.hasMoreElements();)
					{
						final Room R=e.nextElement();
						if(arg1.equals("*"))
							num+=R.numInhabitants();
						else
						{
							for(int m=0;m<R.numInhabitants();m++)
							{
								final MOB M=R.fetchInhabitant(m);
								if(M==null)
									continue;
								if(MASK!=null)
								{
									if(CMLib.masking().maskCheck(MASK,M,true))
										num++;
								}
								else
								if(CMLib.english().containsString(M.name(),arg1))
									num++;
							}
						}
					}
					returnable=simpleEval(ctx.scripted,""+num,arg3,arg2,"NUMMOBSINAREA");
					break;
				}
				case 33: // nummobs
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					String arg1=varify(ctx,tt[t+0]).toUpperCase();
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					int num=0;
					MaskingLibrary.CompiledZMask MASK=null;
					if((arg1.toUpperCase().startsWith("MASK")&&(arg1.substring(4).trim().startsWith("="))))
					{
						arg1=arg1.substring(4).trim();
						arg1=arg1.substring(1).trim();
						MASK=CMLib.masking().maskCompile(arg1);
					}
					try
					{
						for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
						{
							final Room R=e.nextElement();
							for(int m=0;m<R.numInhabitants();m++)
							{
								final MOB M=R.fetchInhabitant(m);
								if(M==null)
									continue;
								if(MASK!=null)
								{
									if(CMLib.masking().maskCheck(MASK,M,true))
										num++;
								}
								else
								if(CMLib.english().containsString(M.name(),arg1))
									num++;
							}
						}
					}
					catch (final NoSuchElementException nse)
					{
					}
					returnable=simpleEval(ctx.scripted,""+num,arg3,arg2,"NUMMOBS");
					break;
				}
				case 34: // numracesinarea
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]).toUpperCase();
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					int num=0;
					Room R=null;
					MOB M=null;
					for(final Enumeration<Room> e=areaRooms();e.hasMoreElements();)
					{
						R=e.nextElement();
						for(int m=0;m<R.numInhabitants();m++)
						{
							M=R.fetchInhabitant(m);
							if((M!=null)&&(M.charStats().raceName().equalsIgnoreCase(arg1)))
								num++;
						}
					}
					returnable=simpleEval(ctx.scripted,""+num,arg3,arg2,"NUMRACESINAREA");
					break;
				}
				case 35: // numraces
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]).toUpperCase();
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					int num=0;
					try
					{
						for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
						{
							final Room R=e.nextElement();
							for(int m=0;m<R.numInhabitants();m++)
							{
								final MOB M=R.fetchInhabitant(m);
								if((M!=null)&&(M.charStats().raceName().equalsIgnoreCase(arg1)))
									num++;
							}
						}
					}
					catch (final NoSuchElementException nse)
					{
					}
					returnable=simpleEval(ctx.scripted,""+num,arg3,arg2,"NUMRACES");
					break;
				}
				case 30: // questobj
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					final PhysicalAgent E=getArgumentItem(tt[t+0],ctx);
					if(arg2.equalsIgnoreCase("ALL"))
					{
						returnable=false;
						for(final Enumeration<Quest> q=CMLib.quests().enumQuests();q.hasMoreElements();)
						{
							final Quest Q=q.nextElement();
							if(Q.running())
							{
								if(E!=null)
								{
									if(Q.isObjectInUse(E))
									{
										returnable=true;
										break;
									}
								}
								else
								if(Q.getQuestItemIndex(arg1)>=0)
								{
									returnable=true;
									break;
								}
							}
						}
					}
					else
					{
						final Quest Q=getQuest(arg2);
						if(Q==null)
							returnable=false;
						else
							returnable=(Q.getQuestItemIndex(arg1)>=0);
					}
					break;
				}
				case 85: // islike
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if(E==null)
						returnable=false;
					else
						returnable=CMLib.masking().maskCheck(arg2, E,false);
					break;
				}
				case 86: // strcontains
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					returnable=stringContainsFunctionImpl(arg1,arg2)>=0;
					break;
				}
				case 92: // isodd
				{
					final String val=varify(ctx,CMParms.cleanBit(tt[t+0])).trim();
					boolean isodd = false;
					if( CMath.isLong( val ) )
					{
						isodd = (CMath.s_long(val) %2 == 1);
					}
					returnable = isodd;
					break;
				}
				case 16: // hitprcnt
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"HITPRCNT","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final double hitPctD=CMath.div(((MOB)E).curState().getHitPoints(),((MOB)E).maxState().getHitPoints());
						final int val1=(int)Math.round(hitPctD*100.0);
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"HITPRCNT");
					}
					break;
				}
				case 50: // isseason
				{
					String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					returnable=false;
					if(ctx.monster.location()!=null)
					{
						arg1=arg1.toUpperCase();
						for(final TimeClock.Season season : TimeClock.Season.values())
						{
							if(season.toString().startsWith(arg1.toUpperCase())
							&&(ctx.monster.location().getArea().getTimeObj().getSeasonCode()==season))
							{
								returnable=true;
								break;
							}
						}
					}
					break;
				}
				case 51: // isweather
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					returnable=false;
					if(ctx.monster.location()!=null)
					for(int a=0;a<Climate.WEATHER_DESCS.length;a++)
					{
						if((Climate.WEATHER_DESCS[a]).startsWith(arg1.toUpperCase())
						&&(ctx.monster.location().getArea().getClimateObj().weatherType(ctx.monster.location())==a))
						{
							returnable = true;
							break;
						}
					}
					break;
				}
				case 57: // ismoon
				{
					String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					returnable=false;
					if(ctx.monster.location()!=null)
					{
						if(arg1.length()==0)
							returnable=ctx.monster.location().getArea().getClimateObj().canSeeTheStars(ctx.monster.location());
						else
						{
							arg1=arg1.toUpperCase();
							for(final TimeClock.MoonPhase phase : TimeClock.MoonPhase.values())
							{
								if(phase.toString().startsWith(arg1)
								&&(ctx.monster.location().getArea().getTimeObj().getMoonPhase(ctx.monster.location())==phase))
								{
									returnable=true;
									break;
								}
							}
						}
					}
					break;
				}
				case 110: // ishour
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0])).toLowerCase().trim();
					if(ctx.monster.location()==null)
						returnable=false;
					else
					if((ctx.monster.location().getArea().getTimeObj().getHourOfDay()==CMath.s_int(arg1)))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 38: // istime
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0])).toLowerCase().trim();
					if(ctx.monster.location()==null)
						returnable=false;
					else
					if(("daytime").startsWith(arg1)
					&&(ctx.monster.location().getArea().getTimeObj().getTODCode()==TimeClock.TimeOfDay.DAY))
						returnable=true;
					else
					if(("dawn").startsWith(arg1)
					&&(ctx.monster.location().getArea().getTimeObj().getTODCode()==TimeClock.TimeOfDay.DAWN))
						returnable=true;
					else
					if(("dusk").startsWith(arg1)
					&&(ctx.monster.location().getArea().getTimeObj().getTODCode()==TimeClock.TimeOfDay.DUSK))
						returnable=true;
					else
					if(("nighttime").startsWith(arg1)
					&&(ctx.monster.location().getArea().getTimeObj().getTODCode()==TimeClock.TimeOfDay.NIGHT))
						returnable=true;
					else
					if((!CMath.isInteger(arg1))
					&&(ctx.monster.location().getArea().getTimeObj().getTODCode()==CMath.s_valueOf(TimeClock.TimeOfDay.class, arg1.toUpperCase().trim())))
						returnable=true;
					else
					if((ctx.monster.location().getArea().getTimeObj().getHourOfDay()==CMath.s_int(arg1)))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 39: // isday
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if((ctx.monster.location()!=null)
					&&(ctx.monster.location().getArea().getTimeObj().getDayOfMonth()==CMath.s_int(arg1.trim())))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 103: // ismonth
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if((ctx.monster.location()!=null)
					&&(ctx.monster.location().getArea().getTimeObj().getMonth()==CMath.s_int(arg1.trim())))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 104: // isyear
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if((ctx.monster.location()!=null)
					&&(ctx.monster.location().getArea().getTimeObj().getYear()==CMath.s_int(arg1.trim())))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 105: // isrlhour
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == CMath.s_int(arg1.trim()))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 106: // isrlday
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if(Calendar.getInstance().get(Calendar.DATE) == CMath.s_int(arg1.trim()))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 107: // isrlmonth
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if(Calendar.getInstance().get(Calendar.MONTH)+1 == CMath.s_int(arg1.trim()))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 108: // isrlyear
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if(Calendar.getInstance().get(Calendar.YEAR) == CMath.s_int(arg1.trim()))
						returnable=true;
					else
						returnable=false;
					break;
				}
				case 45: // nummobsroom
				{
					if(tlen==1)
					{
						if(CMParms.numBits(tt[t+0])>2)
							tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
						else
							tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					}
					int num=0;
					int startbit=0;
					if(lastKnownLocation!=null)
					{
						num=lastKnownLocation.numInhabitants();
						if(Signs.getSign(tt[t+1])!=null)
						{
							String name=varify(ctx,tt[t+0]);
							startbit++;
							if(!name.equalsIgnoreCase("*"))
							{
								num=0;
								MaskingLibrary.CompiledZMask MASK=null;
								if((name.toUpperCase().startsWith("MASK")&&(name.substring(4).trim().startsWith("="))))
								{
									final boolean usePreCompiled = (name.equals(tt[t+0]));
									name=name.substring(4).trim();
									name=name.substring(1).trim();
									MASK=usePreCompiled?CMLib.masking().getPreCompiledMask(name): CMLib.masking().maskCompile(name);
								}
								for(int i=0;i<lastKnownLocation.numInhabitants();i++)
								{
									final MOB M=lastKnownLocation.fetchInhabitant(i);
									if(M==null)
										continue;
									if(MASK!=null)
									{
										if(CMLib.masking().maskCheck(MASK,M,true))
											num++;
									}
									else
									if(CMLib.english().containsString(M.Name(),name)
									||CMLib.english().containsString(M.displayText(),name))
										num++;
								}
							}
						}
					}
					else
					if(Signs.getSign(tt[t+0])==null)
					{
						logError(ctx,"NUMMOBSROOM","Syntax","No SIGN found: "+CMParms.combine(tt,t,t+tlen));
						return returnable;
					}

					final String comp=varify(ctx,tt[t+startbit]);
					final String arg2=varify(ctx,tt[t+startbit+1]);
					if(lastKnownLocation!=null)
						returnable=simpleEval(ctx.scripted,""+num,arg2,comp,"NUMMOBSROOM");
					break;
				}
				case 63: // numpcsroom
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final Room R=lastKnownLocation;
					if(R!=null)
					{
						int num=0;
						for(final Enumeration<MOB> m=R.inhabitants();m.hasMoreElements();)
						{
							final MOB M=m.nextElement();
							if((M!=null)&&(!M.isMonster()))
								num++;
						}
						returnable=simpleEval(ctx.scripted,""+num,arg2,arg1,"NUMPCSROOM");
					}
					break;
				}
				case 79: // numpcsarea
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final Area A=(lastKnownLocation!=null)?lastKnownLocation.getArea():null;
					if(A!=null)
					{
						int num=0;
						for(final Session S : CMLib.sessions().localOnlineIterable())
						{
							final Room mR = S.mob().location();
							final Area mA = (mR!=null)?mR.getArea():null;
							if(mA==A)
								num++;
						}
						returnable=simpleEval(ctx.scripted,""+num,arg2,arg1,"NUMPCSAREA");
					}
					break;
				}
				case 117: // areapc
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					if(lastKnownLocation!=null)
					{
						final Area A=(lastKnownLocation!=null)?lastKnownLocation.getArea():null;
						if(A!=null)
						{
							final String arg1t = arg1.trim();
							final int whichI = CMath.isInteger(arg1t)?CMath.s_int(arg1t): -1;
							returnable=false;
							int num=0;
							for(final Session S : CMLib.sessions().localOnlineIterable())
							{
								final MOB M = S.mob();
								final Room mR = (M!=null)?M.location():null;
								final Area mA = (mR!=null)?mR.getArea():null;
								if((mA==A)
								&&(mR!=null)
								&&(M!=null))
								{
									num++;
									if((whichI == num)
									||((whichI<0)&&(mR.fetchInhabitants(arg1t).contains(M))))
									{
										returnable=(CMLib.english().containsString(M.name(),arg2)
												||CMLib.english().containsString(M.Name(),arg2)
												||CMLib.english().containsString(M.displayText(),arg2));
										break;
									}
								}
							}
						}
					}
					break;
				}
				case 116: // roompc
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					Environmental which=null;
					final Room R = lastKnownLocation;
					if(R!=null)
					{
						final String arg1t = arg1.trim();
						final int whichI = CMath.isInteger(arg1t)?CMath.s_int(arg1t): -1;
						int num=0;
						for(final Enumeration<MOB> m= R.inhabitants();m.hasMoreElements();)
						{
							final MOB M=m.nextElement();
							if((M!=null)&&(!M.isMonster()))
							{
								num++;
								if((whichI == num)
								||((whichI<0)&&(R.fetchInhabitants(arg1t).contains(M))))
								{
									which = M;
									break;
								}
							}
						}
					}
					if(which==null)
						returnable=false;
					else
						returnable=(CMLib.english().containsString(which.name(),arg2)
									||CMLib.english().containsString(which.Name(),arg2)
									||CMLib.english().containsString(which.displayText(),arg2));
					break;
				}
				case 115: // expertise
				{
					// mob ability type > 10
					if(tlen==1)
						tt=parseBits(eval,t,"ccccr"); /* tt[t+0] */
					final Physical P=this.getArgumentMOB(tt[t+0], ctx);
					final MOB M;
					if(P instanceof MOB)
						M=(MOB)P;
					else
					{
						returnable=false;
						logError(ctx,"EXPERTISE","Unknown MOB",tt[t+0]);
						return returnable;
					}
					final String arg2=varify(ctx,tt[t+1]);
					Ability A=M.fetchAbility(arg2);
					if(A==null)
						A=getAbility(arg2);
					if(A==null)
					{
						returnable=false;
						logError(ctx,"EXPERTISE","Unknown Ability on MOB '"+M.name()+"'",tt[t+1]);
						return returnable;
					}
					final String arg3=varify(ctx,tt[t+2]);
					final ExpertiseLibrary.XType experFlag = (ExpertiseLibrary.XType)CMath.s_valueOf(ExpertiseLibrary.XType.class, arg3.toUpperCase().trim());
					if(experFlag == null)
					{
						returnable=false;
						logError(ctx,"EXPERTISE","Unknown Exper Flag",tt[t+2]);
						return returnable;
					}
					final int num=CMLib.expertises().getExpertiseLevelCached(M, A.ID(), experFlag);
					final String arg4=varify(ctx,tt[t+3]);
					final String arg5=varify(ctx,tt[t+4]);
					if(lastKnownLocation!=null)
					{
						returnable=simpleEval(ctx.scripted,""+num,arg5,arg4,"EXPERTISE");
					}
					break;
				}
				case 77: // explored
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String whom=varify(ctx,tt[t+0]);
					final String where=varify(ctx,tt[t+1]);
					final String cmp=varify(ctx,tt[t+2]);
					final String arg2=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentMOB(whom,ctx);
					if((E==null)||(!(E instanceof MOB)))
					{
						logError(ctx,"EXPLORED","Unknown Code",whom);
						return returnable;
					}
					final MOB M=(MOB)E;
					final PlayerStats pStats = M.playerStats();
					if(pStats == null)
					{
						//logError(ctx,"EXPLORED","Not a player",whom);
						return returnable;
					}
					Area A=null;
					int pct=0;
					if(!where.equalsIgnoreCase("world"))
					{
						A=CMLib.map().getArea(where);
						if((A==null)
						&&(CMLib.map().getRoom(where)==null)
						&&(!where.startsWith("#")))
						{
							final Environmental E2=getArgumentItem(where,ctx);
							if((E2 != null)
							&&(!(E2 instanceof Room)))
								A=CMLib.map().areaLocation(E2);
						}
						if(A!=null)
							pct=pStats.percentVisited(M,A);
						else
						{
							final Room P=getRoom(where, lastKnownLocation);
							if(P==null)
							{
								logError(ctx,"EXPLORED","Unknown Area",where);
								return returnable;
							}
							pct = pStats.hasVisited(P) ? 100 : 0;
						}
					}
					else
						pct=pStats.percentVisited(M,A);
					returnable=simpleEval(ctx.scripted,""+pct,arg2,cmp,"EXPLORED");
					break;
				}
				case 72: // faction
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String whom=varify(ctx,tt[t+0]);
					final String arg1=varify(ctx,tt[t+1]);
					final String cmp=varify(ctx,tt[t+2]);
					final String arg2=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentMOB(whom,ctx);
					Faction F=CMLib.factions().getFaction(arg1);
					if(F == null)
						F=CMLib.factions().getFactionByName(arg1);
					if(F == null)
						F=CMLib.factions().getFactionByRangeCodeName(arg1);
					if((E==null)||(!(E instanceof MOB)))
					{
						logError(ctx,"FACTION","Unknown Code",whom);
						return returnable;
					}
					if(F==null)
					{
						logError(ctx,"FACTION","Unknown Faction",arg1);
						return returnable;
					}
					final MOB M=(MOB)E;
					String value=null;
					if(!M.hasFaction(F.factionID()))
						value="";
					else
					{
						final int myfac=M.fetchFaction(F.factionID());
						if(CMath.isNumber(arg2.trim()))
							value=Integer.toString(myfac);
						else
						{
							final Faction.FRange FR=CMLib.factions().getRange(F.factionID(),myfac);
							if(FR==null)
								value="";
							else
								value=FR.name();
						}
					}
					if(lastKnownLocation!=null)
						returnable=simpleEval(ctx.scripted,value,arg2,cmp,"FACTION");
					break;
				}
				case 46: // numitemsroom
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					int ct=0;
					if(lastKnownLocation!=null)
					{
						for(int i=0;i<lastKnownLocation.numItems();i++)
						{
							final Item I=lastKnownLocation.getItem(i);
							if((I!=null)&&(I.container()==null))
								ct++;
						}
					}
					returnable=simpleEval(ctx.scripted,""+ct,arg2,arg1,"NUMITEMSROOM");
					break;
				}
				case 47: //mobitem
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final String arg3=varify(ctx,tt[t+2]);
					MOB M=null;
					if(lastKnownLocation!=null)
					{
						if(CMath.isInteger(arg1.trim()))
							M=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
						else
							M=lastKnownLocation.fetchInhabitant(arg1.trim());
					}
					Item which=null;
					int ct=1;
					if(M!=null)
					{
						for(int i=0;i<M.numItems();i++)
						{
							final Item I=M.getItem(i);
							if((I!=null)&&(I.container()==null))
							{
								if(ct==CMath.s_int(arg2.trim()))
								{
									which = I;
									break;
								}
								ct++;
							}
						}
					}
					if(which==null)
						returnable=false;
					else
						returnable=(CMLib.english().containsString(which.name(),arg3)
									||CMLib.english().containsString(which.Name(),arg3)
									||CMLib.english().containsString(which.displayText(),arg3));
					break;
				}
				case 49: // hastattoo
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"HASTATTOO","Syntax",CMParms.combine(tt,t));
						break;
					}
					else
					if((E!=null)&&(E instanceof MOB))
						returnable=(((MOB)E).findTattoo(arg2)!=null);
					else
						returnable=false;
					break;
				}
				case 123: // isbuilder
					returnable=com.planet_ink.coffee_mud.core.save.SaveGame.isBuilderMode();
					break;
				case 124: // isturnbased
					returnable=GameTime.isTurnBased();
					break;
				case 125: // ispaused
					returnable=GameTime.isPaused();
					break;
				case 126: // saveslot
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String slot=com.planet_ink.coffee_mud.core.save.SaveGame.currentSlot();
					returnable=simpleEvalStr(ctx.scripted,(slot==null)?"":slot,varify(ctx,tt[t+1]),varify(ctx,tt[t+0]),"SAVESLOT");
					break;
				}
				case 129: // partysize
				case 131: // partyleader
				case 132: // party
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final Environmental E=getArgumentMOB(tt[t+0],ctx);
					final List<MOB> group=ScriptEvents.group((E instanceof MOB)?(MOB)E:null);
					final String cmp=varify(ctx,tt[t+1]);
					final String arg2=varify(ctx,tt[t+2]);
					if(funcCode.intValue()==129)
						returnable=simpleEval(ctx.scripted,""+group.size(),arg2,cmp,"PARTYSIZE");
					else
					if(funcCode.intValue()==131)
						returnable=simpleEvalStr(ctx.scripted,group.isEmpty()?"":group.get(0).Name(),arg2,cmp,"PARTYLEADER");
					else
					{
						final StringBuilder names=new StringBuilder();
						for(final MOB G : group)
							names.append((names.length()>0)?" ":"").append(G.Name());
						returnable=simpleEvalStr(ctx.scripted,names.toString(),arg2,cmp,"PARTY");
					}
					break;
				}
				case 135: // memory (ST21): MEMORY(npc who field cmp value)
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccccr"); /* tt[t+0] */
					final String value=memoryText(ctx,tt[t+0],tt[t+1],tt[t+2]);
					final String cmp=varify(ctx,tt[t+3]);
					final String arg2=varify(ctx,tt[t+4]);
					if(CMath.isNumber(value)&&CMath.isNumber(arg2))
						returnable=simpleEval(ctx.scripted,value,arg2,cmp,"MEMORY");
					else
						returnable=simpleEvalStr(ctx.scripted,value,arg2,cmp,"MEMORY");
					break;
				}
				case 134: // genvar (ST20)
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					returnable=simpleEvalStr(ctx.scripted,ScriptEvents.genVar(varify(ctx,tt[t+0]),varify(ctx,tt[t+1])),varify(ctx,tt[t+3]),varify(ctx,tt[t+2]),"GENVAR");
					break;
				}
				case 133: // isweekday
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0])).trim();
					final Room wR=(ctx.monster==null)?null:ctx.monster.location();
					final String day=(wR==null)?"":ScriptEvents.weekday(wR.getArea().getTimeObj());
					returnable=(day.length()>0)&&day.equalsIgnoreCase(arg1);
					break;
				}
				case 130: // inparty
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final boolean oneArg=(tt[t+1]==null)||(tt[t+1].trim().length()==0); // SC8: INPARTY(who), the player's party
					final Environmental E1=oneArg?terminalMob(ctx):getArgumentMOB(tt[t+0],ctx);
					final Environmental E2=getArgumentMOB(tt[t+(oneArg?0:1)],ctx);
					returnable=(E1 instanceof MOB)&&(E2 instanceof MOB)&&ScriptEvents.group((MOB)E1).contains(E2);
					break;
				}
				case 127: // gametime
				case 128: // turns
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final long val=(funcCode.intValue()==127)?GameTime.now():ScriptEvents.turns();
					returnable=simpleEval(ctx.scripted,""+val,varify(ctx,tt[t+1]),varify(ctx,tt[t+0]),(funcCode.intValue()==127)?"GAMETIME":"TURNS");
					break;
				}
				case 122: // isdev
					returnable=com.planet_ink.coffee_mud.core.save.SaveGame.isDevMode();
					break;
				case 121: // varnames
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String host=varify(ctx,tt[t+0]);
					final String prefix=varify(ctx,tt[t+1]);
					final String cmp=varify(ctx,tt[t+2]);
					final String arg2=varify(ctx,tt[t+3]);
					returnable=simpleEvalStr(ctx.scripted,varNames(host,prefix),arg2,cmp,"VARNAMES");
					break;
				}
				case 119: // factionvalue
				case 120: // factionband
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String whom=varify(ctx,tt[t+0]);
					final String fname=varify(ctx,tt[t+1]);
					final String cmp=varify(ctx,tt[t+2]);
					final String arg2=varify(ctx,tt[t+3]);
					final String value=factionText(ctx,whom,fname,funcCode.intValue()==119);
					if(value==null)
						return returnable;
					if(funcCode.intValue()==119)
						returnable=simpleEval(ctx.scripted,value,arg2,cmp,"FACTIONVALUE");
					else
						returnable=simpleEvalStr(ctx.scripted,value,arg2,cmp,"FACTIONBAND");
					break;
				}
				case 118: // hastag
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"HASTAG","Syntax",CMParms.combine(tt,t));
						break;
					}
					else
					if((E!=null)&&(E instanceof Taggable))
						returnable=((Taggable)E).hasTag(arg2);
					else
						returnable=false;
					break;
				}
				case 109: // hastattootime
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final String cmp=tt[t+2];
					final String arg3=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"HASTATTOO","Syntax",CMParms.combine(tt,t));
						break;
					}
					else
					if((E!=null)&&(E instanceof MOB))
					{
						final Tattoo T=((MOB)E).findTattoo(arg2);
						if(T==null)
							returnable=false;
						else
							returnable=simpleEval(ctx.scripted,""+T.getTickDown(),arg3,cmp,"ISTATTOOTIME");
					}
					else
						returnable=false;
					break;
				}
				case 99: // hasacctattoo
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"HASACCTATTOO","Syntax",CMParms.combine(tt,t));
						break;
					}
					else
					if((E!=null)&&(E instanceof MOB)&&(((MOB)E).playerStats()!=null)&&(((MOB)E).playerStats().getAccount()!=null))
						returnable=((MOB)E).playerStats().getAccount().findTattoo(varify(ctx,arg2).trim())!=null; // SC9: $ codes too
					else
						returnable=false;
					break;
				}
				case 48: // numitemsmob
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final String arg3=varify(ctx,tt[t+2]);
					MOB which=null;
					if(lastKnownLocation!=null)
					{
						if(CMath.isInteger(arg1.trim()))
							which=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
						else
							which=lastKnownLocation.fetchInhabitant(arg1);
					}
					int ct=0;
					if(which!=null)
					{
						for(int i=0;i<which.numItems();i++)
						{
							final Item I=which.getItem(i);
							if((I!=null)&&(I.container()==null))
								ct++;
						}
					}
					returnable=simpleEval(ctx.scripted,""+ct,arg3,arg2,"NUMITEMSMOB");
					break;
				}
				case 101: // numitemsshop
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final String arg3=varify(ctx,tt[t+2]);
					PhysicalAgent which=null;
					if(lastKnownLocation!=null)
					{
						if(CMath.isInteger(arg1.trim()))
							which=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
						else
							which=lastKnownLocation.fetchInhabitant(arg1);
						if(which == null)
							which=this.getArgumentItem(tt[t+0], ctx);
						if(which == null)
							which=this.getArgumentMOB(tt[t+0], ctx);
					}
					int ct=0;
					if(which!=null)
					{
						ShopKeeper shopHere = CMLib.coffeeShops().getShopKeeper(which);
						if((shopHere == null)&&(ctx.scripted instanceof Item))
							shopHere=CMLib.coffeeShops().getShopKeeper(((Item)which).owner());
						if((shopHere == null)&&(ctx.scripted instanceof MOB))
							shopHere=CMLib.coffeeShops().getShopKeeper(((MOB)which).location());
						if(shopHere == null)
							shopHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
						if(shopHere!=null)
						{
							final CoffeeShop shop = shopHere.getShop();
							if(shop != null)
							{
								for(final Iterator<Environmental> i=shop.getStoreInventory();i.hasNext();i.next())
								{
									ct++;
								}
							}
						}
					}
					returnable=simpleEval(ctx.scripted,""+ct,arg3,arg2,"NUMITEMSSHOP");
					break;
				}
				case 100: // shopitem
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					final String arg3=varify(ctx,tt[t+2]);
					PhysicalAgent where=null;
					if(lastKnownLocation!=null)
					{
						if(CMath.isInteger(arg1.trim()))
							where=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
						else
							where=lastKnownLocation.fetchInhabitant(arg1.trim());
						if(where == null)
							where=this.getArgumentItem(tt[t+0], ctx);
						if(where == null)
							where=this.getArgumentMOB(tt[t+0], ctx);
					}
					Environmental which=null;
					int ct=1;
					if(where!=null)
					{
						ShopKeeper shopHere = CMLib.coffeeShops().getShopKeeper(where);
						if((shopHere == null)&&(ctx.scripted instanceof Item))
							shopHere=CMLib.coffeeShops().getShopKeeper(((Item)where).owner());
						if((shopHere == null)&&(ctx.scripted instanceof MOB))
							shopHere=CMLib.coffeeShops().getShopKeeper(((MOB)where).location());
						if(shopHere == null)
							shopHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
						if(shopHere!=null)
						{
							final CoffeeShop shop = shopHere.getShop();
							if(shop != null)
							{
								for(final Iterator<Environmental> i=shop.getStoreInventory();i.hasNext();)
								{
									final Environmental E=i.next();
									if(ct==CMath.s_int(arg2.trim()))
									{
										which = E;
										break;
									}
									ct++;
								}
							}
						}
						if(which==null)
							returnable=false;
						else
						{
							returnable=(CMLib.english().containsString(which.name(),arg3)
										||CMLib.english().containsString(which.Name(),arg3)
										||CMLib.english().containsString(which.displayText(),arg3));
							if(returnable)
								setShopPrice(shopHere,which,ctx.tmp);
						}
					}
					else
						returnable=false;
					break;
				}
				case 102: // shophas
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					PhysicalAgent where=null;
					if(lastKnownLocation!=null)
					{
						if(CMath.isInteger(arg1.trim()))
							where=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
						else
							where=lastKnownLocation.fetchInhabitant(arg1.trim());
						if(where == null)
							where=this.getArgumentItem(tt[t+0], ctx);
						if(where == null)
							where=this.getArgumentMOB(tt[t+0], ctx);
					}
					returnable=false;
					if(where!=null)
					{
						ShopKeeper shopHere = CMLib.coffeeShops().getShopKeeper(where);
						if((shopHere == null)&&(ctx.scripted instanceof Item))
							shopHere=CMLib.coffeeShops().getShopKeeper(((Item)where).owner());
						if((shopHere == null)&&(ctx.scripted instanceof MOB))
							shopHere=CMLib.coffeeShops().getShopKeeper(((MOB)where).location());
						if(shopHere == null)
							shopHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
						if(shopHere!=null)
						{
							final CoffeeShop shop = shopHere.getShop();
							if(shop != null)
							{
								final Environmental E=shop.getStock(arg2.trim(), null);
								returnable = (E!=null);
								if(returnable)
									setShopPrice(shopHere,E,ctx.tmp);
							}
						}
					}
					break;
				}
				case 43: // roommob
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					Environmental which=null;
					if(lastKnownLocation!=null)
					{
						if(CMath.isInteger(arg1.trim()))
							which=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
						else
							which=lastKnownLocation.fetchInhabitant(arg1.trim());
					}
					if(which==null)
						returnable=false;
					else
						returnable=(CMLib.english().containsString(which.name(),arg2)
									||CMLib.english().containsString(which.Name(),arg2)
									||CMLib.english().containsString(which.displayText(),arg2));
					break;
				}
				case 44: // roomitem
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=varify(ctx,tt[t+1]);
					Environmental which=null;
					int ct=1;
					if(lastKnownLocation!=null)
					{
						for(int i=0;i<lastKnownLocation.numItems();i++)
						{
							final Item I=lastKnownLocation.getItem(i);
							if((I!=null)&&(I.container()==null))
							{
								if(ct==CMath.s_int(arg1.trim()))
								{
									which = I;
									break;
								}
								ct++;
							}
						}
					}
					if(which==null)
						returnable=false;
					else
					{
						returnable=(CMLib.english().containsString(which.name(),arg2)
									||CMLib.english().containsString(which.Name(),arg2)
									||CMLib.english().containsString(which.displayText(),arg2));
					}
					break;
				}
				case 36: // ishere
				{
					final String arg1=varify(ctx,CMParms.cleanBit(tt[t+0]));
					if((arg1.length()>0)&&(lastKnownLocation!=null))
					{
						returnable=((lastKnownLocation.findItem(arg1)!=null)
								||(lastKnownLocation.fetchInhabitant(arg1)!=null));
					}
					else
						returnable=false;
					break;
				}
				case 17: // inroom
				{
					if(tlen==1)
						tt=parseSpecial3PartEval(eval,t);
					String comp="==";
					Environmental E=ctx.monster;
					String arg2;
					if(Signs.getSign(tt[t+1])!=null)
					{
						E=getArgumentItem(tt[t+0],ctx);
						comp=tt[t+1];
						arg2=varify(ctx,tt[t+2]);
					}
					else
						arg2=varify(ctx,tt[t+0]);
					final Room mobLocR=CMLib.map().roomLocation(E);
					Room R=null;
					if(arg2.equalsIgnoreCase("BEACON"))
						R=ctx.monster.getStartRoom();
					else
					if(arg2.equalsIgnoreCase("MYPROPERTY"))
					{
						if(CMLib.law().doesOwnThisLand(ctx.monster, mobLocR))
							R=mobLocR;
					}
					else
					if(arg2.equalsIgnoreCase("ANYPROPERTY"))
					{
						if(CMLib.law().getLandTitle(mobLocR)!=null)
							R=mobLocR;
					}
					else
					if(arg2.equalsIgnoreCase("ANYSPECIAL"))
					{
						if((ctx.monster.getStartRoom()==mobLocR)
						||(CMLib.law().getLandTitle(mobLocR)!=null))
							R=mobLocR;
					}
					else
					{
						if(arg2.startsWith("$"))
							R=CMLib.map().roomLocation(getArgumentItem(arg2,ctx));
						if(R==null)
							R=getRoom(arg2,lastKnownLocation);
					}
					if(E==null)
						returnable=false;
					else
					{
						if((R==null)&&((arg2.length()==0)||(mobLocR==null)))
							returnable=true;
						else
						if((R==null)||(mobLocR==null))
							returnable=false;
						else
							returnable=simpleEvalStr(ctx.scripted,CMLib.map().getExtendedRoomID(mobLocR),CMLib.map().getExtendedRoomID(R),comp,"INROOM");
					}
					break;
				}
				case 90: // inarea
				{
					if(tlen==1)
						tt=parseSpecial3PartEval(eval,t);
					String comp="==";
					Environmental E=ctx.monster;
					String arg3;
					if(Signs.getSign(tt[t+1])!=null)
					{
						E=getArgumentItem(tt[t+0],ctx);
						comp=tt[t+1];
						arg3=varify(ctx,tt[t+2]);
					}
					else
						arg3=varify(ctx,tt[t+0]);
					Room R=null;
					if(arg3.startsWith("$"))
						R=CMLib.map().roomLocation(this.getArgumentItem(arg3,ctx));
					if(R==null)
					{
						try
						{
							final String lnAstr=(lastKnownLocation!=null)?lastKnownLocation.getArea().Name():null;
							if((lnAstr!=null)&&(lnAstr.equalsIgnoreCase(arg3)))
								R=lastKnownLocation;
							if(R==null)
							{
								for(final Enumeration<Area> a=CMLib.map().areas();a.hasMoreElements();)
								{
									final Area A=a.nextElement();
									if((A!=null)&&(A.Name().equalsIgnoreCase(arg3)))
									{
										if((lnAstr!=null)
										&&(lnAstr.equals(A.Name())))
											R=lastKnownLocation;
										else
										if(!A.isProperlyEmpty())
											R=A.getRandomProperRoom();
									}
								}
							}
							if(R==null)
							{
								for(final Enumeration<Area> a=CMLib.map().areas();a.hasMoreElements();)
								{
									final Area A=a.nextElement();
									if((A!=null)&&(CMLib.english().containsString(A.Name(),arg3)))
									{
										if((lnAstr!=null)
										&&(lnAstr.equals(A.Name())))
											R=lastKnownLocation;
										else
										if(!A.isProperlyEmpty())
											R=A.getRandomProperRoom();
									}
								}
							}
						}
						catch (final NoSuchElementException nse)
						{
						}
					}
					if(R==null)
						R=getRoom(arg3,lastKnownLocation);
					if((R!=null)
					&&(CMath.bset(R.getArea().flags(),Area.FLAG_INSTANCE_PARENT))
					&&(lastKnownLocation!=null)
					&&(lastKnownLocation.getArea()!=R.getArea())
					&&(CMath.bset(lastKnownLocation.getArea().flags(),Area.FLAG_INSTANCE_CHILD))
					&&(CMLib.map().getModelArea(lastKnownLocation.getArea())==R.getArea()))
						R=lastKnownLocation;

					if(E==null)
						returnable=false;
					else
					{
						final Room R2=CMLib.map().roomLocation(E);
						if((R==null)&&((arg3.length()==0)||(R2==null)))
							returnable=true;
						else
						if((R==null)||(R2==null))
							returnable=false;
						else
							returnable=simpleEvalStr(ctx.scripted,R2.getArea().Name(),R.getArea().Name(),comp,"INAREA");
					}
					break;
				}
				case 89: // isrecall
				{
					if(tlen==1)
						tt=parseSpecial3PartEval(eval,t);
					String comp="==";
					Environmental E=ctx.monster;
					String arg2;
					if(Signs.getSign(tt[t+1])!=null)
					{
						E=getArgumentItem(tt[t+0],ctx);
						comp=tt[t+1];
						arg2=varify(ctx,tt[t+2]);
					}
					else
						arg2=varify(ctx,tt[t+0]);
					Room R=null;
					if(arg2.startsWith("$"))
						R=CMLib.map().getStartRoom(this.getArgumentItem(arg2,ctx));
					if(R==null)
						R=getRoom(arg2,lastKnownLocation);
					if(E==null)
						returnable=false;
					else
					{
						final Room R2=CMLib.map().getStartRoom(E);
						if((R==null)&&((arg2.length()==0)||(R2==null)))
							returnable=true;
						else
						if((R==null)||(R2==null))
							returnable=false;
						else
							returnable=simpleEvalStr(ctx.scripted,CMLib.map().getExtendedRoomID(R2),CMLib.map().getExtendedRoomID(R),comp,"ISRECALL");
					}
					break;
				}
				case 37: // inlocale
				{
					if(tlen==1)
					{
						if(CMParms.numBits(tt[t+0])>1)
							tt=parseBits(eval,t,"cr"); /* tt[t+0] */
						else
						{
							final int numBits=2;
							String[] parsed=null;
							if(CMParms.cleanBit(tt[t+0]).equals(tt[t+0]))
								parsed=parseBits("'"+tt[t+0]+"'"+CMStrings.repeat(" .",numBits-1),"cr");
							else
								parsed=parseBits(tt[t+0]+CMStrings.repeat(" .",numBits-1),"cr");
							tt=CMParms.insertStringArray(tt,parsed,t);
							eval[0]=tt;
						}
					}
					String arg2=null;
					Environmental E=ctx.monster;
					if(tt[t+1].equals("."))
						arg2=varify(ctx,tt[t+0]);
					else
					{
						E=getArgumentItem(tt[t+0],ctx);
						arg2=varify(ctx,tt[t+1]);
					}
					if(E==null)
						returnable=false;
					else
					if(arg2.length()==0)
						returnable=true;
					else
					{
						final Room R=CMLib.map().roomLocation(E);
						final String uarg2=arg2.toUpperCase().trim();
						if(R==null)
							returnable=false;
						else
						if((CMClass.classID(R).toUpperCase().indexOf(uarg2)>=0)
						||((R.domainType()<Room.INDOORS)
							&&(uarg2.startsWith("OUTDOOR")||(Room.DOMAIN_OUTDOOR_DESCS[R.domainType()].indexOf(uarg2)>=0)))
						||((R.domainType()>=Room.INDOORS)
							&&(uarg2.startsWith("INDOOR")||(Room.DOMAIN_INDOORS_DESCS[R.domainType()&~Room.INDOORS].indexOf(uarg2)>=0))))
							returnable=true;
						else
							returnable=false;
					}
					break;
				}
				case 18: // sex
				{
					if(tlen==1)
						tt=parseBits(eval,t,"CcR"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					String arg3=tt[t+2];
					if(CMath.isNumber(arg3.trim()))
					{
						switch(CMath.s_int(arg3.trim()))
						{
						case 0:
							arg3 = "NEUTER";
							break;
						case 1:
							arg3 = "MALE";
							break;
						case 2:
							arg3 = "FEMALE";
							break;
						}
					}
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"SEX","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final String sex=(""+((char)((MOB)E).charStats().getStat(CharStats.STAT_GENDER))).toUpperCase();
						if(arg2.equals("=="))
							returnable=arg3.startsWith(sex);
						else
						if(arg2.equals("!="))
							returnable=!arg3.startsWith(sex);
						else
						{
							logError(ctx,"SEX","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 91: // datetime
				{
					if(tlen==1)
						tt=parseBits(eval,t,"Ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=tt[t+2];
					final TimePeriod timePeriod = TimePeriod.get(arg1);
					if(timePeriod == null)
						logError(ctx,"DATETIME","Syntax","Unknown arg: "+arg1+" for "+ctx.scripted.name());
					else
					{
						final Area A =CMLib.map().areaLocation(ctx.scripted);
						if(A!=null)
						{
							final String val=""+A.getTimeObj().get(timePeriod);
							returnable=simpleEval(ctx.scripted,val,arg3,arg2,"DATETIME");
						}
					}
					break;
				}
				case 13: // stat
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=tt[t+2];
					final String arg4=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"STAT","Syntax",CMParms.combine(tt,t));
						break;
					}
					if(E==null)
						returnable=false;
					else
					{
						final String val=getStatValue(E,arg2);
						if(val==null)
						{
							logError(ctx,"STAT","Syntax","Unknown stat: "+arg2+" for "+E.name());
							break;
						}

						if(arg3.equals("=="))
							returnable=val.equalsIgnoreCase(arg4);
						else
						if(arg3.equals("!="))
							returnable=!val.equalsIgnoreCase(arg4);
						else
							returnable=simpleEval(ctx.scripted,val,arg4,arg3,"STAT");
					}
					break;
				}
				case 52: // gstat
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=tt[t+2];
					final String arg4=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"GSTAT","Syntax",CMParms.combine(tt,t));
						break;
					}
					if(E==null)
						returnable=false;
					else
					{
						final String val=getGStatValue(E,arg2);
						if(val==null)
						{
							logError(ctx,"GSTAT","Syntax","Unknown stat: "+arg2+" for "+E.name());
							break;
						}

						if(arg3.equals("=="))
							returnable=val.equalsIgnoreCase(arg4);
						else
						if(arg3.equals("!="))
							returnable=!val.equalsIgnoreCase(arg4);
						else
							returnable=simpleEval(ctx.scripted,val,arg4,arg3,"GSTAT");
					}
					break;
				}
				case 19: // position
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=tt[t+2];
					final Physical P=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"POSITION","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(P==null)
						returnable=false;
					else
					{
						String sex="STANDING";
						if(CMLib.flags().isSleeping(P))
							sex="SLEEPING";
						else
						if(CMLib.flags().isSitting(P))
							sex="SITTING";
						if(arg2.equals("=="))
							returnable=sex.startsWith(arg3);
						else
						if(arg2.equals("!="))
							returnable=!sex.startsWith(arg3);
						else
						{
							logError(ctx,"POSITION","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 20: // level
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Physical P=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"LEVEL","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(P==null)
						returnable=false;
					else
					{
						final int val1=P.phyStats().level();
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"LEVEL");
					}
					break;
				}
				case 80: // questpoints
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"QUESTPOINTS","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final int val1=((MOB)E).getQuestPoint();
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"QUESTPOINTS");
					}
					break;
				}
				case 83: // qvar
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final String arg3=tt[t+2];
					final String arg4=varify(ctx,tt[t+3]);
					final Quest Q=getQuest(arg1);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"QVAR","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(Q==null)
						returnable=false;
					else
						returnable=simpleEvalStr(ctx.scripted,Q.getStat(arg2),arg4,arg3,"QVAR");
					break;
				}
				case 84: // math
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=varify(ctx,tt[t+0]);
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					if(!CMath.isMathExpression(arg1))
					{
						logError(ctx,"MATH","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(!CMath.isMathExpression(arg3))
					{
						logError(ctx,"MATH","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					returnable=simpleExpressionEval(ctx.scripted,arg1,arg3,arg2,"MATH");
					break;
				}
				case 81: // trains
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"TRAINS","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final int val1=((MOB)E).getTrains();
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"TRAINS");
					}
					break;
				}
				case 82: // pracs
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"PRACS","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final int val1=((MOB)E).getPractices();
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"PRACS");
					}
					break;
				}
				case 66: // clanrank
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"CLANRANK","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(!(E instanceof MOB))
						returnable=false;
					else
					{
						int val1=-1;
						Clan C=CMLib.clans().findRivalrousClan((MOB)E);
						if(C==null)
							C=((MOB)E).clans().iterator().hasNext()?((MOB)E).clans().iterator().next().first:null;
						if(C!=null)
							val1=((MOB)E).getClanRole(C.clanID()).second.intValue();
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"CLANRANK");
					}
					break;
				}
				case 64: // deity
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"DEITY","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final String sex=((MOB)E).charStats().getWorshipCharID();
						if(arg2.equals("=="))
							returnable=sex.equalsIgnoreCase(arg3);
						else
						if(arg2.equals("!="))
							returnable=!sex.equalsIgnoreCase(arg3);
						else
						{
							logError(ctx,"DEITY","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 68: // clandata
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=tt[t+2];
					final String arg4=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"CLANDATA","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					String clanID=null;
					if((E!=null)&&(E instanceof MOB))
					{
						Clan C=CMLib.clans().findRivalrousClan((MOB)E);
						if(C==null)
							C=((MOB)E).clans().iterator().hasNext()?((MOB)E).clans().iterator().next().first:null;
						if(C!=null)
							clanID=C.clanID();
					}
					else
					{
						clanID=varify(ctx,arg1);
						if((ctx.scripted instanceof MOB)
						&&(CMLib.clans().getClanAnyHost(clanID)==null))
						{
							final List<Pair<Clan,Integer>> Cs=CMLib.clans().getClansByCategory((MOB)ctx.scripted, clanID);
							if((Cs!=null)&&(Cs.size()>0))
								clanID=Cs.get(0).first.clanID();
						}
					}
					final Clan C=CMLib.clans().findClan(clanID);
					if(C!=null)
					{
						if(!C.isStat(arg2))
							logError(ctx,"CLANDATA","RunTime",arg2+" is not a valid clan variable.");
						else
						{
							final String whichVal=C.getStat(arg2).trim();
							if(CMath.isNumber(whichVal)&&CMath.isNumber(arg4.trim()))
								returnable=simpleEval(ctx.scripted,whichVal,arg4,arg3,"CLANDATA");
							else
								returnable=simpleEvalStr(ctx.scripted,whichVal,arg4,arg3,"CLANDATA");
						}
					}
					break;
				}
				case 98: // clanqualifies
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=varify(ctx,tt[t+1]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"CLANQUALIFIES","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					final Clan C=CMLib.clans().findClan(arg2);
					if((C!=null)&&(E instanceof MOB))
					{
						// SC9: may apply, as CLANAPPLY checks (it was never true)
						final MOB mob=(MOB)E;
						returnable=(mob.getClanRole(C.clanID())==null);
						if(returnable
						&&C.isOnlyFamilyApplicants()
						&&(!CMLib.clans().isFamilyOfMembership(mob,C.getMemberList())))
							returnable=false;
						if(returnable
						&&(CMLib.clans().getClansByCategory(mob, C.getCategory()).size()>=CMProps.getMaxClansThisCategory(C.getCategory())))
							returnable=false;
						if(returnable && (!CMLib.masking().maskCheck(C.getBasicRequirementMask(), mob, true)))
							returnable=false;
					}
					else
					{
						logError(ctx,"CLANQUALIFIES","Unknown clan "+arg2+" or "+arg1+" is not a mob",CMParms.combine(tt,t));
						return returnable;
					}
					break;
				}
				case 65: // clan
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"CLAN","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						String clanID="";
						Clan C=CMLib.clans().findRivalrousClan((MOB)E);
						if(C==null)
							C=((MOB)E).clans().iterator().hasNext()?((MOB)E).clans().iterator().next().first:null;
						if(C!=null)
							clanID=C.clanID();
						if(arg2.equals("=="))
							returnable=clanID.equalsIgnoreCase(arg3);
						else
						if(arg2.equals("!="))
							returnable=!clanID.equalsIgnoreCase(arg3);
						else
						if(arg2.equalsIgnoreCase("IN"))
						{
							C = CMLib.clans().findClan(arg3);
							if(C != null)
								returnable=((MOB)E).getClanRole(C.clanID())!=null;
							else
								returnable=false;
						}
						else
						if(arg2.equalsIgnoreCase("NOTIN"))
						{
							C = CMLib.clans().findClan(arg3);
							if(C != null)
								returnable=((MOB)E).getClanRole(C.clanID())==null;
							else
								returnable=true;
						}
						else
						{
							logError(ctx,"CLAN","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 88: // mood
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Physical P=getArgumentMOB(arg1,ctx);
					if(arg2.length()==0)
					{
						logError(ctx,"MOOD","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((P==null)||(!(P instanceof MOB)))
						returnable=false;
					else
					{
						final Ability moodA=P.fetchEffect("Mood");
						if(moodA!=null)
						{
							final String moodName=moodA.text();
							if(arg2.equals("=="))
								returnable=moodName.equalsIgnoreCase(arg3);
							else
							if(arg2.equals("!="))
								returnable=!moodName.equalsIgnoreCase(arg3);
							else
							{
								logError(ctx,"MOOD","Syntax",CMParms.combine(tt,t));
								return returnable;
							}
						}
					}
					break;
				}
				case 21: // class
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"CLASS","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final String sex=((MOB)E).charStats().displayClassName().toUpperCase();
						if(arg2.equals("=="))
							returnable=sex.startsWith(arg3);
						else
						if(arg2.equals("!="))
							returnable=!sex.startsWith(arg3);
						else
						{
							logError(ctx,"CLASS","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 22: // baseclass
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"CLASS","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final String sex=((MOB)E).charStats().getCurrentClass().baseClass().toUpperCase();
						if(arg2.equals("=="))
							returnable=sex.startsWith(arg3);
						else
						if(arg2.equals("!="))
							returnable=!sex.startsWith(arg3);
						else
						{
							logError(ctx,"CLASS","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 23: // race
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"RACE","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final String sex=((MOB)E).charStats().raceName().toUpperCase();
						if(arg2.equals("=="))
							returnable=sex.startsWith(arg3);
						else
						if(arg2.equals("!="))
							returnable=!sex.startsWith(arg3);
						else
						{
							logError(ctx,"RACE","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 24: //racecat
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"RACECAT","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final String sex=((MOB)E).charStats().getMyRace().racialCategory().toUpperCase();
						if(arg2.equals("=="))
							returnable=sex.startsWith(arg3);
						else
						if(arg2.equals("!="))
							returnable=!sex.startsWith(arg3);
						else
						{
							logError(ctx,"RACECAT","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 25: // goldamt
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"GOLDAMT","Syntax",CMParms.combine(tt,t));
						break;
					}
					if(E==null)
						returnable=false;
					else
					{
						int val1=0;
						if(E instanceof MOB)
							val1=(int)Math.round(CMLib.beanCounter().getTotalAbsoluteValue((MOB)E,CMLib.beanCounter().getCurrency(ctx.scripted)));
						else
						if(E instanceof Coins)
							val1=(int)Math.round(((Coins)E).getTotalValue());
						else
						if(E instanceof Item)
							val1=((Item)E).value();
						else
						{
							logError(ctx,"GOLDAMT","Syntax",CMParms.combine(tt,t));
							return returnable;
						}

						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"GOLDAMT");
					}
					break;
				}
				case 78: // exp
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentMOB(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"EXP","Syntax",CMParms.combine(tt,t));
						break;
					}
					if((E==null)||(!(E instanceof MOB)))
						returnable=false;
					else
					{
						final int val1=((MOB)E).getExperience();
						returnable=simpleEval(ctx.scripted,""+val1,arg3,arg2,"EXP");
					}
					break;
				}
				case 76: // value
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final Environmental E=getArgumentItem(arg1,ctx);
					final String arg2=varify(ctx,tt[t+1]);
					final String arg3=tt[t+2];
					final String arg4=varify(ctx,tt[t+3]);
					if((arg2.length()==0)||(arg3.length()==0)||(arg4.length()==0))
					{
						logError(ctx,"VALUE","Syntax",CMParms.combine(tt,t));
						break;
					}
					if(!CMLib.beanCounter().getAllCurrencies().contains(arg2.toUpperCase()))
					{
						logError(ctx,"VALUE","Syntax",arg2+" is not a valid designated currency.");
						break;
					}
					if(E==null)
						returnable=false;
					else
					{
						int val1=0;
						if(E instanceof MOB)
							val1=(int)Math.round(CMLib.beanCounter().getTotalAbsoluteValue((MOB)E,arg2.toUpperCase()));
						else
						if(E instanceof Coins)
						{
							if(CMLib.beanCounter().isCurrencyMatch(((Coins)E).getCurrency(),arg2))
								val1=(int)Math.round(((Coins)E).getTotalValue());
						}
						else
						if(E instanceof Item)
							val1=((Item)E).value();
						else
						{
							logError(ctx,"VALUE","Syntax",CMParms.combine(tt,t));
							return returnable;
						}

						returnable=simpleEval(ctx.scripted,""+val1,arg4,arg3,"GOLDAMT");
					}
					break;
				}
				case 26: // objtype
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccR"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=varify(ctx,tt[t+2]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"OBJTYPE","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(E==null)
						returnable=false;
					else
					{
						final String sex=CMClass.classID(E).toUpperCase();
						if(arg2.equals("=="))
							returnable=sex.indexOf(arg3)>=0;
						else
						if(arg2.equals("!="))
							returnable=sex.indexOf(arg3)<0;
						else
						{
							logError(ctx,"OBJTYPE","Syntax",CMParms.combine(tt,t));
							return returnable;
						}
					}
					break;
				}
				case 27: // var
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cCcr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final String arg2=tt[t+1];
					final String arg3=tt[t+2];
					final String arg4=varify(ctx,tt[t+3]);
					final Environmental E=getArgumentItem(arg1,ctx);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"VAR","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					final String val=getVar(E,arg1,arg2,ctx);
					if(CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTVARS))
						Log.debugOut("(VAR "+arg1+" "+arg2 +"["+val+"] "+arg3+" "+arg4);
					if(arg3.equals("==")||arg3.equals("="))
						returnable=val.equals(arg4);
					else
					if(arg3.equals("!=")||(arg3.equals("<>")))
						returnable=!val.equals(arg4);
					else
						returnable=simpleEval(E, val, arg4, arg3, "VAR");
					break;
				}
				case 41: // eval
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String val=varify(ctx,tt[t+0]);
					final String cmp=tt[t+1];
					final String arg4=varify(ctx,tt[t+2]);
					if(cmp.length()==0)
					{
						logError(ctx,"EVAL","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					if(cmp.equals("==")||cmp.equals("="))
						returnable=val.equals(arg4);
					else
					if(cmp.equals("!=")||(cmp.equals("<>")))
						returnable=!val.equals(arg4);
					else
						returnable=simpleEval(ctx.scripted, val, arg4, cmp, "EVAL");
					break;
				}
				case 40: // number
				{
					final String val=varify(ctx,CMParms.cleanBit(tt[t+0])).trim();
					boolean isnumber=(val.length()>0);
					for(int i=0;i<val.length()&&(!CMath.isNumber(val));i++) // SC11: signs and decimals are numbers
					{
						if(!Character.isDigit(val.charAt(i)))
						{
							isnumber = false;
							break;
						}
					}
					returnable=isnumber;
					break;
				}
				case 42: // randnum
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1s=varify(ctx,tt[t+0]).toUpperCase().trim();
					int arg1=0;
					if(CMath.isMathExpression(arg1s.trim()))
						arg1=CMath.s_parseIntExpression(arg1s.trim());
					else
						arg1=CMParms.parse(arg1s.trim()).size();
					final String arg2=tt[t+1];
					final String arg3s=varify(ctx,tt[t+2]).trim();
					int arg3=0;
					if(CMath.isMathExpression(arg3s.trim()))
						arg3=CMath.s_parseIntExpression(arg3s.trim());
					else
						arg3=CMParms.parse(arg3s.trim()).size();
					arg1=CMLib.dice().roll(1,arg1,0);
					returnable=simpleEval(ctx.scripted,""+arg1,""+arg3,arg2,"RANDNUM");
					break;
				}
				case 71: // rand0num
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1s=varify(ctx,tt[t+0]).toUpperCase().trim();
					int arg1=0;
					if(CMath.isMathExpression(arg1s))
						arg1=CMath.s_parseIntExpression(arg1s);
					else
						arg1=CMParms.parse(arg1s).size();
					final String arg2=tt[t+1];
					final String arg3s=varify(ctx,tt[t+2]).trim();
					int arg3=0;
					if(CMath.isMathExpression(arg3s))
						arg3=CMath.s_parseIntExpression(arg3s);
					else
						arg3=CMParms.parse(arg3s).size();
					arg1=CMLib.dice().roll(1,arg1,-1);
					returnable=simpleEval(ctx.scripted,""+arg1,""+arg3,arg2,"RAND0NUM");
					break;
				}
				case 53: // incontainer
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final Environmental E=getArgumentItem(arg1,ctx);
					final String arg2=tt[t+1];
					final Environmental E2=getArgumentItem(arg2,ctx);
					if(E==null)
						returnable=false;
					else
					if(E instanceof MOB)
					{
						if(arg2.length()==0)
							returnable=(((MOB)E).riding()==null);
						else
						if(E2!=null)
							returnable=(((MOB)E).riding()==E2);
						else
							returnable=false;
					}
					else
					if(E instanceof Item)
					{
						if(arg2.length()==0)
							returnable=(((Item)E).container()==null);
						else
						if(E2!=null)
							returnable=(((Item)E).container()==E2);
						else
							returnable=false;
					}
					else
						returnable=false;
					break;
				}
				case 96: // iscontents
				{
					if(tlen==1)
						tt=parseBits(eval,t,"cr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final Environmental E=getArgumentItem(arg1,ctx);
					final String arg2=varify(ctx, tt[t+1]);
					returnable=false;
					if(E==null)
						returnable=false;
					else
					if(E instanceof Rideable)
					{
						if(arg2.length()==0)
							returnable=((Rideable)E).numRiders()==0;
						else
							returnable=CMLib.english().fetchEnvironmental(((Rideable)E).riders(), arg2, false)!=null;
					}
					if((E instanceof Container)
					&&(!returnable))
					{
						if(arg2.length()==0)
							returnable=!((Container)E).hasContent();
						else
							returnable=CMLib.english().fetchEnvironmental(((Container)E).getDeepContents(), arg2, false)!=null;
					}
					break;
				}
				case 97: // wornon
				{
					if(tlen==1)
						tt=parseBits(eval,t,"ccr"); /* tt[t+0] */
					final String arg1=tt[t+0];
					final PhysicalAgent E=getArgumentMOB(arg1,ctx);
					final String arg2=varify(ctx, tt[t+1]);
					final String arg3=varify(ctx, tt[t+2]);
					if((arg2.length()==0)||(arg3.length()==0))
					{
						logError(ctx,"WORNON","Syntax",CMParms.combine(tt,t));
						return returnable;
					}
					final int wornLoc = CMParms.indexOf(Wearable.CODES.NAMESUP(), arg2.toUpperCase().trim());
					returnable=false;
					if(wornLoc<0)
						logError(ctx,"EVAL","BAD WORNON LOCATION",arg2);
					else
					if(E instanceof MOB)
					{
						final List<Item> items=((MOB)E).fetchWornItems(Wearable.CODES.GET(wornLoc),(short)-2048,(short)0);
						if((items.size()==0)&&(arg3.length()==0))
							returnable=true;
						else
							returnable = CMLib.english().fetchEnvironmental(items, arg3, false)!=null;
					}
					break;
				}
				default:
					logError(ctx,"EVAL","UNKNOWN",CMParms.toListString(tt));
					return false;
				}
				pushEvalBoolean(stack,returnable);
				while((t<tt.length)&&(!tt[t].equals(")")))
					t++;
			}
			else
			{
				logError(ctx,"EVAL","SYNTAX","BAD CONJUCTOR "+tt[t]+": "+CMParms.toListString(tt));
				return false;
			}
		}
		if((stack.size()!=1)
		||(!(stack.get(0) instanceof Boolean)))
		{
			logError(ctx,"EVAL","SYNTAX","Unmatched (: "+CMParms.toListString(tt));
			return false;
		}
		return ((Boolean)stack.get(0)).booleanValue();
	}

	protected void setShopPrice(final ShopKeeper shopHere, final Environmental E, final Object[] tmp)
	{
		if(shopHere instanceof MOB)
		{
			final ShopKeeper.ShopPrice price = CMLib.coffeeShops().sellingPrice((MOB)shopHere, null, E, shopHere, shopHere.getShop(), true);
			if(price.experiencePrice>0)
				tmp[SPECIAL_9SHOPHASPRICE] = price.experiencePrice+"xp";
			else
			if(price.questPointPrice>0)
				tmp[SPECIAL_9SHOPHASPRICE] = price.questPointPrice+"qp";
			else
				tmp[SPECIAL_9SHOPHASPRICE] = CMLib.beanCounter().abbreviatedPrice((MOB)shopHere,price.absoluteGoldPrice);
		}
	}

	/**
	 * A mob's standing with a faction, as FACTIONVALUE and FACTIONBAND give it:
	 * the number, or the code of the band it is in.
	 * @param ctx the script context
	 * @param whom who, as a script names a mob
	 * @param fname the faction's ID, name or a band code of it
	 * @param number true for the number, false for the band code
	 * @return the value, "" if the mob has no standing, null on an error
	 */
	protected String factionText(final MPContext ctx, final String whom, final String fname, final boolean number)
	{
		final Environmental E=getArgumentMOB(whom,ctx);
		Faction F=CMLib.factions().getFaction(fname);
		if(F == null)
			F=CMLib.factions().getFactionByName(fname);
		if(F == null)
			F=CMLib.factions().getFactionByRangeCodeName(fname);
		if(!(E instanceof MOB))
		{
			logError(ctx,number?"FACTIONVALUE":"FACTIONBAND","Unknown Code",whom);
			return null;
		}
		if(F==null)
		{
			logError(ctx,number?"FACTIONVALUE":"FACTIONBAND","Unknown Faction",fname);
			return null;
		}
		final MOB M=(MOB)E;
		if(!M.hasFaction(F.factionID()))
			return "";
		final int value=M.fetchFaction(F.factionID());
		if(number)
			return Integer.toString(value);
		final Faction.FRange FR=CMLib.factions().getRange(F.factionID(),value);
		return (FR==null)?"":FR.codeName();
	}

	/**
	 * The text form of a function that is a condition: "true" or "false",
	 * as the condition FUNC(parms) evaluates.
	 * @param ctx the script context
	 * @param func the function name
	 * @param funcParms what is between its parentheses
	 * @return "true" or "false", or "" if the condition does not parse
	 */
	protected String conditionText(final MPContext ctx, final String func, final String funcParms)
	{
		try
		{
			final String[] condition=parseEval(func+"("+funcParms+")");
			return eval(ctx,new String[][] { condition },0) ? "true" : "false";
		}
		catch(final ScriptParseException e)
		{
			logError(ctx,func,"Syntax",e.getMessage());
			return "";
		}
	}

	/**
	 * SC17: where the ( at open is closed, so that a call may hold $(name)
	 * or (a+b); the first ) after it if none matches.
	 * @param s the text
	 * @param open where the ( is, or -1
	 * @return where its ) is, or -1
	 */
	protected static int closingParen(final String s, final int open)
	{
		if(open<0)
			return -1;
		for(int i=open+1,d=0;i<s.length();i++)
		{
			final char c=s.charAt(i);
			if(c=='(')
				d++;
			else
			if(c==')')
			{
				if(d==0)
					return i;
				d--;
			}
		}
		return s.indexOf(')',open);
	}

	@Override
	public String functify(final MPContext ctx, final String evaluable)
	{
		if(evaluable.length()==0)
			return "";
		final StringBuffer results = new StringBuffer("");
		final int y=evaluable.indexOf('(');
		final int z=closingParen(evaluable,y); // SC17
		final String preFab=(y>=0)?evaluable.substring(0,y).toUpperCase().trim():"";
		Integer funcCode=funcH.get(preFab);
		if(funcCode==null)
			funcCode=Integer.valueOf(0);
		if((y<0)||(z<y))
		{
			logError(ctx,"()","Syntax",evaluable);
			return "";
		}
		else
		{
			tickStatus=Tickable.STATUS_MISC2+funcCode.intValue();
			final String funcParms=evaluable.substring(y+1,z).trim();
			// SC1 and on: the newer functions, in one place
			if(funcCode.intValue()>=EXTRA_FUNCS_FROM)
				return extraValue(ctx,preFab,funcParms);
			switch(funcCode.intValue())
			{
			case 1: // rand
			{
				results.append(CMLib.dice().rollPercentage());
				break;
			}
			case 2: // has
			{
				final String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				final Environmental E=getArgumentItem(arg1,ctx);
				ArrayList<Item> choices=new ArrayList<Item>();
				if(E==null)
					choices=new ArrayList<Item>();
				else
				if(E instanceof MOB)
				{
					for(int i=0;i<((MOB)E).numItems();i++)
					{
						final Item I=((MOB)E).getItem(i);
						if((I!=null)&&(I.amWearingAt(Wearable.IN_INVENTORY))&&(I.container()==null))
							choices.add(I);
					}
				}
				else
				if(E instanceof Item)
				{
					if(E instanceof Container)
						choices.addAll(((Container)E).getDeepContents());
					else
						choices.add((Item)E);
				}
				else
				if(E instanceof Room)
				{
					for(int i=0;i<((Room)E).numItems();i++)
					{
						final Item I=((Room)E).getItem(i);
						if((I!=null)&&(I.container()==null))
							choices.add(I);
					}
				}
				if(choices.size()>0)
					results.append(choices.get(CMLib.dice().roll(1,choices.size(),-1)).name());
				break;
			}
			case 74: // hasnum
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String item=varify(ctx,CMParms.getCleanBit(funcParms,1));
				final Environmental E=getArgumentItem(arg1,ctx);
				if((item.length()==0)||(E==null))
					logError(ctx,"HASNUM","Syntax",funcParms);
				else
				{
					Item I=null;
					int num=0;
					if(E instanceof MOB)
					{
						final MOB M=(MOB)E;
						for(int i=0;i<M.numItems();i++)
						{
							I=M.getItem(i);
							if(I==null)
								break;
							if((item.equalsIgnoreCase("all"))
							||(CMLib.english().containsString(I.Name(),item)))
								num++;
						}
						results.append(""+num);
					}
					else
					if(E instanceof Item)
					{
						num=CMLib.english().containsString(E.name(),item)?1:0;
						results.append(""+num);
					}
					else
					if(E instanceof Room)
					{
						final Room R=(Room)E;
						for(int i=0;i<R.numItems();i++)
						{
							I=R.getItem(i);
							if(I==null)
								break;
							if((item.equalsIgnoreCase("all"))
							||(CMLib.english().containsString(I.Name(),item)))
								num++;
						}
						results.append(""+num);
					}
				}
				break;
			}
			case 3: // worn
			{
				final String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				final Environmental E=getArgumentItem(arg1,ctx);
				ArrayList<Item> choices=new ArrayList<Item>();
				if(E==null)
					choices=new ArrayList<Item>();
				else
				if(E instanceof MOB)
				{
					for(int i=0;i<((MOB)E).numItems();i++)
					{
						final Item I=((MOB)E).getItem(i);
						if((I!=null)&&(!I.amWearingAt(Wearable.IN_INVENTORY))&&(I.container()==null))
							choices.add(I);
					}
				}
				else
				if((E instanceof Item)&&(!(((Item)E).amWearingAt(Wearable.IN_INVENTORY))))
				{
					if(E instanceof Container)
						choices.addAll(((Container)E).getDeepContents());
					else
						choices.add((Item)E);
				}
				if(choices.size()>0)
					results.append(choices.get(CMLib.dice().roll(1,choices.size(),-1)).name());
				break;
			}
			case 4: // isnpc
			case 5: // ispc
				results.append(conditionText(ctx,preFab,funcParms));
				break;
			case 87: // isbirthday
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)
				&&(E instanceof MOB)
				&&(((MOB)E).playerStats()!=null)
				&&(((MOB)E).playerStats().getBirthday()!=null))
				{
					final MOB mob=(MOB)E;
					Room startRoom=mob.getStartRoom();
					if(startRoom == null)
						startRoom=CMLib.login().getDefaultStartRoom(mob);
					if(startRoom == null)
						startRoom=mob.location();
					final TimeClock C=CMLib.time().localClock(startRoom);
					final int day=C.getDayOfMonth();
					final int month=C.getMonth();
					int year=C.getYear();
					int[] birthdayIndex = mob.playerStats().getBirthday();
					if(birthdayIndex.length<PlayerStats.BIRTHDEX_COUNT)
						birthdayIndex=Arrays.copyOf(birthdayIndex, PlayerStats.BIRTHDEX_COUNT);
					final int bday=birthdayIndex[PlayerStats.BIRTHDEX_DAY];
					final int bmonth=birthdayIndex[PlayerStats.BIRTHDEX_MONTH];
					if((month>bmonth)||((month==bmonth)&&(day>bday)))
						year++;

					final StringBuffer timeDesc=new StringBuffer("");
					if(C.getDaysInWeek()>0)
					{
						long x=((long)year)*((long)C.getMonthsInYear())*C.getDaysInMonth();
						x=x+((long)(bmonth-1))*((long)C.getDaysInMonth());
						x=x+bmonth;
						timeDesc.append(C.getWeekNames()[(int)(x%C.getDaysInWeek())]+", ");
					}
					timeDesc.append("the "+bday+CMath.numAppendage(bday));
					timeDesc.append(" day of "+C.getMonthNames()[bmonth-1]);
					if(C.getYearNames().length>0)
						timeDesc.append(", "+CMStrings.replaceAll(C.getYearNames()[year%C.getYearNames().length],"#",""+year));
					results.append(timeDesc.toString());
				}
				break;
			}
			case 6: // isgood
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&((E instanceof MOB)))
				{
					final Faction.FRange FR=CMLib.factions().getRange(CMLib.factions().getAlignmentID(),((MOB)E).fetchFaction(CMLib.factions().getAlignmentID()));
					if(FR!=null)
						results.append(FR.name());
					else
						results.append(((MOB)E).fetchFaction(CMLib.factions().getAlignmentID()));
				}
				break;
			}
			case 8: // isevil
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&((E instanceof MOB)))
					results.append(CMLib.flags().getAlignmentName(E).toLowerCase());
				break;
			}
			case 9: // isneutral
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&((E instanceof MOB)))
					results.append(((MOB)E).fetchFaction(CMLib.factions().getAlignmentID()));
				break;
			}
			case 11: // isimmort
				results.append(conditionText(ctx,preFab,funcParms));
				break;
			case 54: // isalive
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&((E instanceof MOB))&&(!((MOB)E).amDead()))
					results.append(((MOB)E).healthText(null));
				else
				if(E!=null)
					results.append(E.name()+" is dead.");
				break;
			}
			case 58: // isable
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final Environmental E=getArgumentMOB(arg1,ctx);
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				if((E!=null)&&((E instanceof MOB))&&(!((MOB)E).amDead()))
				{
					final ExpertiseLibrary X=(ExpertiseLibrary)CMLib.expertises().findDefinition(arg2,true);
					if(X!=null)
					{
						final Pair<String,Integer> s=((MOB)E).fetchExpertise(X.ID());
						if(s!=null)
							results.append(s.getKey()+((s.getValue()!=null)?s.getValue().toString():""));
					}
					else
					{
						final Ability A=((MOB)E).findAbility(arg2);
						if(A!=null)
							results.append(""+A.proficiency());
					}
				}
				break;
			}
			case 59: // isopen
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final int dir=CMLib.directions().getGoodDirectionCode(arg1);
				boolean returnable=false;
				if(dir<0)
				{
					final Environmental E=getArgumentItem(arg1,ctx);
					if((E!=null)&&(E instanceof Container))
						returnable=((Container)E).isOpen();
					else
					if((E!=null)&&(E instanceof Exit))
						returnable=((Exit)E).isOpen();
				}
				else
				if(lastKnownLocation!=null)
				{
					final Exit E=lastKnownLocation.getExitInDir(dir);
					if(E!=null)
						returnable= E.isOpen();
				}
				results.append(""+returnable);
				break;
			}
			case 60: // islocked
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final int dir=CMLib.directions().getGoodDirectionCode(arg1);
				if(dir<0)
				{
					final Environmental E=getArgumentItem(arg1,ctx);
					if((E!=null)&&(E instanceof Container))
						results.append(((Container)E).keyName());
					else
					if((E!=null)&&(E instanceof Exit))
						results.append(((Exit)E).keyName());
				}
				else
				if(lastKnownLocation!=null)
				{
					final Exit E=lastKnownLocation.getExitInDir(dir);
					if(E!=null)
						results.append(E.keyName());
				}
				break;
			}
			case 62: // callfunc
			{
				final String arg1=varify(ctx,CMParms.getCleanBit(funcParms,0));
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				final SubScript foundFunc = this.findFunc(ctx.scripted, arg1);
				if(foundFunc != null)
				{
					final String varg2=varify(ctx,arg2);
					final MPContext ctx2 = ctx.push(foundFunc);
					ctx2.msg = varg2;
					String found= execute(ctx2);
					if(found==null)
						found="";
					results.append(found);
				}
				else
				{
					// SC6: CALLFUNC(target func args)
					final String found=callOn(ctx,CMParms.getCleanBit(funcParms,0),CMParms.getPastBitClean(funcParms,0));
					if(found==null)
						logError(ctx,"CALLFUNC","Unknown","Function: "+arg1);
					else
						results.append(found);
				}
				break;
			}
			case 61: // strin
			{
				final String arg1=varify(ctx,CMParms.getCleanBit(funcParms,0));
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				final List<String> V=CMParms.parse(arg1.toUpperCase());
				results.append(V.indexOf(arg2.toUpperCase()));
				break;
			}
			case 55: // ispkill
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Physical E=getArgumentMOB(arg1,ctx);
				if((E==null)||(!(E instanceof MOB)))
					results.append("false");
				else
				if(((MOB)E).isAttributeSet(MOB.Attrib.PLAYERKILL))
					results.append("true");
				else
					results.append("false");
				break;
			}
			case 10: // isfight
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&((E instanceof MOB))&&(((MOB)E).isInCombat()))
					results.append(((MOB)E).getVictim().name());
				break;
			}
			case 12: // ischarmed
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Physical E=getArgumentMOB(arg1,ctx);
				if(E!=null)
				{
					final List<Ability> V=CMLib.flags().flaggedAffects(E,Ability.FLAG_CHARMING);
					for(int v=0;v<V.size();v++)
						results.append((V.get(v).name())+" ");
				}
				break;
			}
			case 15: // isfollow
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB)&&(((MOB)E).amFollowing()!=null)
				&&(((MOB)E).amFollowing().location()==lastKnownLocation))
					results.append(((MOB)E).amFollowing().name());
				break;
			}
			case 73: // isservant
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB)&&(((MOB)E).getLiegeID()!=null)&&(((MOB)E).getLiegeID().length()>0))
					results.append(((MOB)E).getLiegeID());
				break;
			}
			case 95: // isspeaking
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
				{
					final MOB TM=(MOB)E;
					final Language L=CMLib.utensils().getLanguageSpoken(TM);
					if(L!=null)
						results.append(L.Name());
					else
						results.append("Common");
				}
				break;
			}
			case 56: // name
			case 7: // isname
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
					results.append(E.name());
				break;
			}
			case 75: // currency
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
					results.append(CMLib.beanCounter().getCurrency(E));
				break;
			}
			case 14: // affected
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if((E instanceof Physical)&&(((Physical)E).numEffects()>0))
					results.append(((Physical)E).effects().nextElement().name());
				break;
			}
			case 69: // isbehave
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final PhysicalAgent E=getArgumentItem(arg1,ctx);
				if(E!=null)
				{
					for(final Enumeration<Behavior> e=E.behaviors();e.hasMoreElements();)
					{
						final Behavior B=e.nextElement();
						if(B!=null)
							results.append(B.ID()+" ");
					}
				}
				break;
			}
			case 70: // ipaddress
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB)&&(!((MOB)E).isMonster()))
					results.append(((MOB)E).session().getAddress());
				break;
			}
			case 28: // questwinner
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB)&&(!((MOB)E).isMonster()))
				{
					for(int q=0;q<CMLib.quests().numQuests();q++)
					{
						final Quest Q=CMLib.quests().fetchQuest(q);
						if((Q!=null)&&(Q.wasWinner(E.Name())))
							results.append(Q.name()+" ");
					}
				}
				break;
			}
			case 93: // questscripted
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final PhysicalAgent E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB)&&(!((MOB)E).isMonster()))
				{
					for(final Enumeration<ScriptingEngine> e=E.scripts();e.hasMoreElements();)
					{
						final ScriptingEngine SE=e.nextElement();
						if((SE!=null)
						&&(SE.defaultQuestName()!=null)
						&&(SE.defaultQuestName().length()>0))
						{
							final Quest Q=getQuest(SE.defaultQuestName());
							if(Q!=null)
								results.append(Q.name()+" ");
							else
								results.append(SE.defaultQuestName()+" ");
						}
					}
					for(final Enumeration<Behavior> e=E.behaviors();e.hasMoreElements();)
					{
						final Behavior B=e.nextElement();
						if(B instanceof ScriptingEngine)
						{
							final ScriptingEngine SE=(ScriptingEngine)B;
							if((SE.defaultQuestName()!=null)
							&&(SE.defaultQuestName().length()>0))
							{
								final Quest Q=getQuest(SE.defaultQuestName());
								if(Q!=null)
									results.append(Q.name()+" ");
								else
									results.append(SE.defaultQuestName()+" ");
							}
						}
					}
				}
				break;
			}
			case 30: // questobj
			{
				String questName=CMParms.cleanBit(funcParms);
				questName=varify(ctx,questName);
				if(questName.equals("*") && (this.defaultQuestName()!=null) && (this.defaultQuestName().length()>0))
					questName = this.defaultQuestName();
				final Quest Q=getQuest(questName);
				if(Q==null)
				{
					logError(ctx,"QUESTOBJ","Unknown","Quest: "+questName);
					break;
				}
				final StringBuffer list=new StringBuffer("");
				int num=1;
				Environmental E=Q.getQuestItem(num);
				while(E!=null)
				{
					if(E.Name().indexOf(' ')>=0)
						list.append("\""+E.Name()+"\" ");
					else
						list.append(E.Name()+" ");
					num++;
					E=Q.getQuestItem(num);
				}
				results.append(list.toString().trim());
				break;
			}
			case 94: // questroom
			{
				String questName=CMParms.cleanBit(funcParms);
				questName=varify(ctx,questName);
				final Quest Q=getQuest(questName);
				if(Q==null)
				{
					logError(ctx,"QUESTROOM","Unknown","Quest: "+questName);
					break;
				}
				final StringBuffer list=new StringBuffer("");
				int num=1;
				Environmental E=Q.getQuestRoom(num);
				while(E!=null)
				{
					final String roomID=CMLib.map().getExtendedRoomID((Room)E);
					if(roomID.indexOf(' ')>=0)
						list.append("\""+roomID+"\" ");
					else
						list.append(roomID+" ");
					num++;
					E=Q.getQuestRoom(num);
				}
				results.append(list.toString().trim());
				break;
			}
			case 114: // questarea
			{
				String questName=CMParms.cleanBit(funcParms);
				questName=varify(ctx,questName);
				final Quest Q=getQuest(questName);
				if(Q==null)
				{
					logError(ctx,"QUESTOBJ","Unknown","Quest: "+questName);
					break;
				}
				final StringBuffer list=new StringBuffer("");
				int num=1;
				Environmental E=Q.getQuestRoom(num);
				while(E!=null)
				{
					final String areaName=CMLib.map().areaLocation(E).name();
					if(list.indexOf(areaName)<0)
					{
						if(areaName.indexOf(' ')>=0)
							list.append("\""+areaName+"\" ");
						else
							list.append(areaName+" ");
					}
					num++;
					E=Q.getQuestRoom(num);
				}
				results.append(list.toString().trim());
				break;
			}
			case 29: // questmob
			{
				String questName=CMParms.cleanBit(funcParms);
				questName=varify(ctx,questName);
				if(questName.equals("*") && (this.defaultQuestName()!=null) && (this.defaultQuestName().length()>0))
					questName=this.defaultQuestName();
				final Quest Q=getQuest(questName);
				if(Q==null)
				{
					logError(ctx,"QUESTOBJ","Unknown","Quest: "+questName);
					break;
				}
				final StringBuffer list=new StringBuffer("");
				int num=1;
				Environmental E=Q.getQuestMob(num);
				while(E!=null)
				{
					if(E.Name().indexOf(' ')>=0)
						list.append("\""+E.Name()+"\" ");
					else
						list.append(E.Name()+" ");
					num++;
					E=Q.getQuestMob(num);
				}
				results.append(list.toString().trim());
				break;
			}
			case 31: // isquestmobalive
			{
				String questName=CMParms.cleanBit(funcParms);
				questName=varify(ctx,questName);
				final Quest Q=getQuest(questName);
				if(Q==null)
				{
					logError(ctx,"QUESTOBJ","Unknown","Quest: "+questName);
					break;
				}
				final StringBuffer list=new StringBuffer("");
				int num=1;
				MOB E=Q.getQuestMob(num);
				while(E!=null)
				{
					if(CMLib.flags().isInTheGame(E,true))
					{
						if(E.Name().indexOf(' ')>=0)
							list.append("\""+E.Name()+"\" ");
						else
							list.append(E.Name()+" ");
					}
					num++;
					E=Q.getQuestMob(num);
				}
				results.append(list.toString().trim());
				break;
			}
			case 49: // hastattoo
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
				{
					for(final Enumeration<Tattoo> t = ((MOB)E).tattoos();t.hasMoreElements();)
						results.append(t.nextElement().ID()).append(" ");
				}
				break;
			}
			case 119: // factionvalue
			case 120: // factionband
			{
				final String whom=CMParms.getCleanBit(funcParms,0);
				final String fname=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				final String value=factionText(ctx,whom,fname,funcCode.intValue()==119);
				if(value!=null)
					results.append(value);
				break;
			}
			case 123: // isbuilder
				results.append(com.planet_ink.coffee_mud.core.save.SaveGame.isBuilderMode()?"true":"false");
				break;
			case 124: // isturnbased
				results.append(GameTime.isTurnBased()?"true":"false");
				break;
			case 125: // ispaused
				results.append(GameTime.isPaused()?"true":"false");
				break;
			case 126: // saveslot
			{
				final String slot=com.planet_ink.coffee_mud.core.save.SaveGame.currentSlot();
				results.append((slot==null)?"":slot);
				break;
			}
			case 127: // gametime
			{
				// SC2: GAMETIME(HOURS|DAYS|MONTHS|YEARS) counts from the area clock's year 0; GAMETIME() is game milliseconds
				final String unit=varify(ctx,funcParms).trim();
				if(unit.length()==0)
					results.append(GameTime.now());
				else
				{
					final Area gA=CMLib.map().areaLocation(ctx.scripted);
					final TimeClock gC=(gA!=null)?gA.getTimeObj():CMLib.time().globalClock();
					final String u=unit.toUpperCase();
					final long per=u.startsWith("YEAR")?ScriptEvents.everyHours(gC.getMonthsInYear(),"MONTHS",gC):ScriptEvents.everyHours(1,u,gC);
					results.append(ScriptEvents.clockHours(gC)/per);
				}
				break;
			}
			case 128: // turns
				results.append(ScriptEvents.turns());
				break;
			case 129: // partysize
			case 131: // partyleader
			case 132: // party
			{
				final Environmental E=getArgumentMOB(CMParms.getCleanBit(funcParms,0),ctx);
				final List<MOB> group=ScriptEvents.group((E instanceof MOB)?(MOB)E:null);
				if(funcCode.intValue()==129)
					results.append(group.size());
				else
				if(funcCode.intValue()==131)
					results.append(group.isEmpty()?"":group.get(0).Name());
				else
				{
					final StringBuilder names=new StringBuilder();
					for(final MOB G : group)
						names.append((names.length()>0)?" ":"").append(G.Name());
					results.append(names);
				}
				break;
			}
			case 135: // memory (ST21)
				results.append(memoryText(ctx,CMParms.getCleanBit(funcParms,0),CMParms.getCleanBit(funcParms,1),CMParms.getCleanBit(funcParms,2)));
				break;
			case 134: // genvar (ST20)
				results.append(ScriptEvents.genVar(varify(ctx,CMParms.getCleanBit(funcParms,0)),varify(ctx,CMParms.getPastBitClean(funcParms,0))));
				break;
			case 133: // isweekday
			{
				final Room wR=(ctx.monster==null)?null:ctx.monster.location();
				final String day=(wR==null)?"":ScriptEvents.weekday(wR.getArea().getTimeObj());
				results.append(((day.length()>0)&&day.equalsIgnoreCase(varify(ctx,funcParms).trim()))?"true":"false");
				break;
			}
			case 130: // inparty
			{
				final boolean oneArg=CMParms.getPastBitClean(funcParms,0).trim().length()==0; // SC8: INPARTY(who)
				final Environmental E1=oneArg?terminalMob(ctx):getArgumentMOB(CMParms.getCleanBit(funcParms,0),ctx);
				final Environmental E2=getArgumentMOB(oneArg?CMParms.getCleanBit(funcParms,0):CMParms.getPastBitClean(funcParms,0),ctx);
				results.append(((E1 instanceof MOB)&&(E2 instanceof MOB)&&ScriptEvents.group((MOB)E1).contains(E2))?"true":"false");
				break;
			}
			case 122: // isdev
				results.append(com.planet_ink.coffee_mud.core.save.SaveGame.isDevMode()?"true":"false");
				break;
			case 121: // varnames
			{
				final String host=varify(ctx,CMParms.getCleanBit(funcParms,0));
				final String prefix=varify(ctx,CMParms.getPastBitClean(funcParms,0)).trim();
				results.append(varNames(host.length()==0?"*":host,prefix));
				break;
			}
			case 118: // hastag
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof Taggable))
					results.append(CMParms.combineQuoted(new XArrayList<String>(((Taggable)E).tags()), 0));
				break;
			}
			case 109: // hastattootime
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
				{
					final Tattoo T=((MOB)E).findTattoo(arg2);
					if(T!=null)
						results.append(T.getTickDown());
				}
				break;
			}
			case 99: // hasacctattoo
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB)&&(((MOB)E).playerStats()!=null)&&(((MOB)E).playerStats().getAccount()!=null))
				{
					for(final Enumeration<Tattoo> t = ((MOB)E).playerStats().getAccount().tattoos();t.hasMoreElements();)
						results.append(t.nextElement().ID()).append(" ");
				}
				break;
			}
			case 32: // nummobsinarea
			{
				String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				int num=0;
				MaskingLibrary.CompiledZMask compiledMask=null;
				if((arg1.toUpperCase().startsWith("MASK")&&(arg1.substring(4).trim().startsWith("="))))
				{
					arg1=arg1.substring(4).trim();
					arg1=arg1.substring(1).trim();
					compiledMask=CMLib.masking().getPreCompiledMask(arg1);
				}
				for(final Enumeration<Room> e=areaRooms();e.hasMoreElements();)
				{
					final Room R=e.nextElement();
					if(arg1.equals("*"))
						num+=R.numInhabitants();
					else
					{
						for(int m=0;m<R.numInhabitants();m++)
						{
							final MOB M=R.fetchInhabitant(m);
							if(M==null)
								continue;
							if(compiledMask!=null)
							{
								if(CMLib.masking().maskCheck(compiledMask,M,true))
									num++;
							}
							else
							if(CMLib.english().containsString(M.name(),arg1))
								num++;
						}
					}
				}
				results.append(num);
				break;
			}
			case 33: // nummobs
			{
				int num=0;
				String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				MaskingLibrary.CompiledZMask MASK=null;
				if((arg1.toUpperCase().startsWith("MASK")&&(arg1.substring(4).trim().startsWith("="))))
				{
					arg1=arg1.substring(4).trim();
					arg1=arg1.substring(1).trim();
					MASK=CMLib.masking().maskCompile(arg1);
				}
				try
				{
					for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
					{
						final Room R=e.nextElement();
						for(int m=0;m<R.numInhabitants();m++)
						{
							final MOB M=R.fetchInhabitant(m);
							if(M==null)
								continue;
							if(MASK!=null)
							{
								if(CMLib.masking().maskCheck(MASK,M,true))
									num++;
							}
							else
							if(CMLib.english().containsString(M.name(),arg1))
								num++;
						}
					}
				}
				catch(final NoSuchElementException nse)
				{
				}
				results.append(num);
				break;
			}
			case 34: // numracesinarea
			{
				int num=0;
				final String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				Room R=null;
				MOB M=null;
				for(final Enumeration<Room> e=areaRooms();e.hasMoreElements();)
				{
					R=e.nextElement();
					for(int m=0;m<R.numInhabitants();m++)
					{
						M=R.fetchInhabitant(m);
						if((M!=null)&&(M.charStats().raceName().equalsIgnoreCase(arg1)))
							num++;
					}
				}
				results.append(num);
				break;
			}
			case 35: // numraces
			{
				int num=0;
				final String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				Room R=null;
				MOB M=null;
				try
				{
					for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
					{
						R=e.nextElement();
						for(int m=0;m<R.numInhabitants();m++)
						{
							M=R.fetchInhabitant(m);
							if((M!=null)&&(M.charStats().raceName().equalsIgnoreCase(arg1)))
								num++;
						}
					}
				}
				catch (final NoSuchElementException nse)
				{
				}
				results.append(num);
				break;
			}
			case 112: // cansee
			case 113: // canhear
			{
				// SC9: true or false, as the tests
				final PhysicalAgent MP=getArgumentMOB(CMParms.getCleanBit(funcParms,0),ctx);
				final Physical P=getArgumentItem(CMParms.getPastBitClean(funcParms,0),ctx);
				if((MP instanceof MOB)&&(P!=null))
				{
					final boolean can=(funcCode.intValue()==112)?CMLib.flags().canBeSeenBy(P,(MOB)MP):CMLib.flags().canBeHeardMovingBy(P,(MOB)MP);
					results.append(can?"true":"false");
				}
				else
					results.append("false");
				break;
			}
			case 111: // itemcount
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if(E!=null)
				{
					int num=0;
					if(E instanceof Armor)
						num=((Item)E).numberOfItems();
					else
					if(E instanceof RawMaterial)
						num=((Item)E).phyStats().weight();
					else
					if(E instanceof Container)
					{
						num++;
						for(final Item I : ((Container)E).getContents())
							num+=I.numberOfItems();
					}
					else
					if(E instanceof Item)
						num=((Item)E).numberOfItems();
					else
					if(E instanceof MOB)
					{
						for(final Enumeration<Item> i=((MOB)E).items();i.hasMoreElements();)
							num += i.nextElement().numberOfItems();
					}
					else
					if(E instanceof Room)
					{
						for(final Enumeration<Item> i=((Room)E).items();i.hasMoreElements();)
							num += i.nextElement().numberOfItems();
					}
					else
					if(E instanceof Area)
					{
						for(final Enumeration<Room> r=((Area)E).getFilledCompleteMap();r.hasMoreElements();)
						{
							for(final Enumeration<Item> i=r.nextElement().items();i.hasMoreElements();)
								num += i.nextElement().numberOfItems();
						}
					}
					results.append(""+num);
				}
				break;
			}
			case 16: // hitprcnt
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
				{
					final double hitPctD=CMath.div(((MOB)E).curState().getHitPoints(),((MOB)E).maxState().getHitPoints());
					final int val1=(int)Math.round(hitPctD*100.0);
					results.append(val1);
				}
				break;
			}
			case 50: // isseason
			{
				if(ctx.monster.location()!=null)
					results.append(ctx.monster.location().getArea().getTimeObj().getSeasonCode().toString());
				break;
			}
			case 51: // isweather
			{
				if(ctx.monster.location()!=null)
					results.append(Climate.WEATHER_DESCS[ctx.monster.location().getArea().getClimateObj().weatherType(ctx.monster.location())]);
				break;
			}
			case 57: // ismoon
			{
				if(ctx.monster.location()!=null)
					results.append(ctx.monster.location().getArea().getTimeObj().getMoonPhase(ctx.monster.location()).toString());
				break;
			}
			case 38: // istime
			{
				if(lastKnownLocation!=null)
					results.append(lastKnownLocation.getArea().getTimeObj().getTODCode().getDesc().toLowerCase());
				break;
			}
			case 110: // ishour
			{
				if(lastKnownLocation!=null)
					results.append(lastKnownLocation.getArea().getTimeObj().getHourOfDay());
				break;
			}
			case 103: // ismonth
			{
				if(lastKnownLocation!=null)
					results.append(lastKnownLocation.getArea().getTimeObj().getMonth());
				break;
			}
			case 104: // isyear
			{
				if(lastKnownLocation!=null)
					results.append(lastKnownLocation.getArea().getTimeObj().getYear());
				break;
			}
			case 105: // isrlhour
			{
				if(lastKnownLocation!=null)
					results.append(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
				break;
			}
			case 106: // isrlday
			{
				if(lastKnownLocation!=null)
					results.append(Calendar.getInstance().get(Calendar.DATE));
				break;
			}
			case 107: // isrlmonth
			{
				if(lastKnownLocation!=null)
					results.append(Calendar.getInstance().get(Calendar.MONTH)+1); // SC9: from 1, as the test
				break;
			}
			case 108: // isrlyear
			{
				if(lastKnownLocation!=null)
					results.append(Calendar.getInstance().get(Calendar.YEAR));
				break;
			}
			case 39: // isday
			{
				if(lastKnownLocation!=null)
					results.append(""+lastKnownLocation.getArea().getTimeObj().getDayOfMonth());
				break;
			}
			case 43: // roommob
			{
				final String clean=CMParms.cleanBit(funcParms);
				final String arg1=varify(ctx,clean);
				Environmental which=null;
				if(lastKnownLocation!=null)
				{
					if(CMath.isInteger(arg1.trim()))
						which=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
					else
					{
						final Environmental E=this.getArgumentItem(clean, ctx);
						if(E!=null)
							which=E;
						else
							which=lastKnownLocation.fetchInhabitant(arg1.trim());
					}
					if(which!=null)
					{
						final List<MOB> list=new ArrayList<MOB>();
						for(int i=0;i<lastKnownLocation.numInhabitants();i++)
						{
							final MOB M=lastKnownLocation.fetchInhabitant(i);
							if(M!=null)
								list.add(M);
						}
						results.append(CMLib.english().getContextName(list,which));
					}
				}
				break;
			}
			case 116: // roompc
			{
				final String clean=CMParms.cleanBit(funcParms);
				final String arg1=varify(ctx,clean);
				Environmental which=null;
				final Room R=lastKnownLocation;
				if(R!=null)
				{
					final String arg1t = arg1.trim();
					if(CMath.isInteger(arg1t))
					{
						final int whichI = CMath.s_int(arg1t);
						int num=0;
						for(int i=0;i<R.numInhabitants();i++)
						{
							final MOB M=R.fetchInhabitant(i);
							if((M!=null)&&(!M.isMonster()))
							{
								num++;
								if(num == whichI)
								{
									which=M;
									break;
								}
							}
						}
					}
					else
					{
						final Environmental E=getArgumentMOB(clean, ctx);
						if(E instanceof MOB)
							which=E;
						else
							which=R.fetchInhabitant(arg1t);
						if((which instanceof MOB)&&(((MOB)which).isMonster()))
							which=null;
					}
					if(which!=null)
					{
						final List<MOB> list=new ArrayList<MOB>();
						for(int i=0;i<R.numInhabitants();i++)
						{
							final MOB M=R.fetchInhabitant(i);
							if(M!=null)
								list.add(M); // ALL of them are necc to get the proper context name
						}
						results.append(CMLib.english().getContextName(list,which));
					}
				}
				break;
			}
			case 117: // areapc
			{
				final String clean=CMParms.cleanBit(funcParms);
				final String arg1=varify(ctx,clean);
				final Area A=(lastKnownLocation!=null)?lastKnownLocation.getArea():null;
				if(A!=null)
				{
					final String arg1t = arg1.trim();
					final int whichI = CMath.isInteger(arg1t)?CMath.s_int(arg1t): -1;
					int num=0;
					for(final Session S : CMLib.sessions().localOnlineIterable())
					{
						final MOB M = S.mob();
						final Room mR = (M!=null)?M.location():null;
						final Area mA = (mR!=null)?mR.getArea():null;
						if((mA==A)
						&&(mR!=null)
						&&(M!=null))
						{
							num++;
							if((whichI == num)
							||((whichI<0)&&(mR.fetchInhabitants(arg1t).contains(M))))
							{
								results.append(M.Name());
								break;
							}
						}
					}
				}
				break;
			}
			case 44: // roomitem
			{
				final String clean=CMParms.cleanBit(funcParms);
				final String arg1=varify(ctx,clean);
				Environmental which=null;
				if(!CMath.isInteger(arg1))
					which=this.getArgumentItem(clean, ctx);
				int ct=1;
				if(lastKnownLocation!=null)
				{
					final List<Item> list=new ArrayList<Item>();
					if(which == null)
					{
						for(int i=0;i<lastKnownLocation.numItems();i++)
						{
							final Item I=lastKnownLocation.getItem(i);
							if((I!=null)&&(I.container()==null))
							{
								list.add(I);
								if(ct==CMath.s_int(arg1.trim()))
								{
									which = I;
									break;
								}
								ct++;
							}
						}
					}
					if(which!=null)
						results.append(CMLib.english().getContextName(list,which));
				}
				break;
			}
			case 45: // nummobsroom
			{
				int num=0;
				if(lastKnownLocation!=null)
				{
					num=lastKnownLocation.numInhabitants();
					String name=varify(ctx,CMParms.cleanBit(funcParms));
					if((name.length()>0)&&(!name.equalsIgnoreCase("*")))
					{
						num=0;
						MaskingLibrary.CompiledZMask MASK=null;
						if((name.toUpperCase().startsWith("MASK")&&(name.substring(4).trim().startsWith("="))))
						{
							name=name.substring(4).trim();
							name=name.substring(1).trim();
							MASK=CMLib.masking().maskCompile(name);
						}
						for(int i=0;i<lastKnownLocation.numInhabitants();i++)
						{
							final MOB M=lastKnownLocation.fetchInhabitant(i);
							if(M==null)
								continue;
							if(MASK!=null)
							{
								if(CMLib.masking().maskCheck(MASK,M,true))
									num++;
							}
							else
							if(CMLib.english().containsString(M.Name(),name)
							||CMLib.english().containsString(M.displayText(),name))
								num++;
						}
					}
				}
				results.append(""+num);
				break;
			}
			case 63: // numpcsroom
			{
				final Room R=lastKnownLocation;
				if(R!=null)
				{
					int num=0;
					for(final Enumeration<MOB> m=R.inhabitants();m.hasMoreElements();)
					{
						final MOB M=m.nextElement();
						if((M!=null)&&(!M.isMonster()))
							num++;
					}
					results.append(""+num);
				}
				break;
			}
			case 115: // expertise
			{
				// mob ability type > 10
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final Physical P=getArgumentMOB(arg1,ctx);
				final MOB M;
				if(P instanceof MOB)
				{
					M=(MOB)P;
					final String arg2=varify(ctx,CMParms.getCleanBit(funcParms,1));
					Ability A=M.fetchAbility(arg2);
					if(A==null)
						A=getAbility(arg2);
					if(A!=null)
					{
						final String arg3=varify(ctx,CMParms.getPastBitClean(funcParms,1));
						final ExpertiseLibrary.XType experFlag = (ExpertiseLibrary.XType)CMath.s_valueOf(ExpertiseLibrary.XType.class, arg3.toUpperCase().trim());
						if(experFlag != null)
							results.append(""+CMLib.expertises().getExpertiseLevelCached(M, A.ID(), experFlag));
					}
				}
				break;
			}
			case 79: // numpcsarea
			{
				if(lastKnownLocation!=null)
				{
					int num=0;
					for(final Session S : CMLib.sessions().localOnlineIterable())
					{
						if((S.mob().location()!=null)
						&&(S.mob().location().getArea()==lastKnownLocation.getArea()))
							num++;
					}
					results.append(""+num);
				}
				break;
			}
			case 77: // explored
			{
				final String whom=varify(ctx,CMParms.getCleanBit(funcParms,0));
				final String where=varify(ctx,CMParms.getCleanBit(funcParms,1));
				final Environmental E=getArgumentMOB(whom,ctx);
				if(E instanceof MOB)
				{
					Area A=null;
					if(!where.equalsIgnoreCase("world"))
					{
						A=CMLib.map().getArea(where);
						if(A==null)
						{
							final Environmental E2=getArgumentItem(where,ctx);
							if(E2!=null)
								A=CMLib.map().areaLocation(E2);
						}
					}
					if((lastKnownLocation!=null)
					&&((A!=null)||(where.equalsIgnoreCase("world"))))
					{
						int pct=0;
						final MOB M=(MOB)E;
						if(M.playerStats()!=null)
							pct=M.playerStats().percentVisited(M,A);
						results.append(""+pct);
					}
				}
				break;
			}
			case 72: // faction
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBit(funcParms,0);
				final Environmental E=getArgumentMOB(arg1,ctx);
				final Faction F=CMLib.factions().getFaction(arg2);
				if(F==null)
					logError(ctx,"FACTION","Unknown Faction",arg1);
				else
				if((E!=null)&&(E instanceof MOB)&&(((MOB)E).hasFaction(F.factionID())))
				{
					final int value=((MOB)E).fetchFaction(F.factionID());
					final Faction.FRange FR=CMLib.factions().getRange(F.factionID(),value);
					if(FR!=null)
						results.append(FR.name());
				}
				break;
			}
			case 46: // numitemsroom
			{
				int ct=0;
				if(lastKnownLocation!=null)
				{
					for(int i=0;i<lastKnownLocation.numItems();i++)
					{
						final Item I=lastKnownLocation.getItem(i);
						if((I!=null)&&(I.container()==null))
							ct++;
					}
				}
				results.append(""+ct);
				break;
			}
			case 47: //mobitem
			{
				final String arg1=varify(ctx,CMParms.getCleanBit(funcParms,0));
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				MOB M=null;
				if(lastKnownLocation!=null)
				{
					if(CMath.isInteger(arg1.trim()))
						M=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
					else
						M=lastKnownLocation.fetchInhabitant(arg1.trim());
				}
				Item which=null;
				int ct=1;
				if(M!=null)
				{
					for(int i=0;i<M.numItems();i++)
					{
						final Item I=M.getItem(i);
						if((I!=null)&&(I.container()==null))
						{
							if(ct==CMath.s_int(arg2.trim()))
							{
								which = I;
								break;
							}
							ct++;
						}
					}
				}
				if(which!=null)
					results.append(which.name());
				break;
			}
			case 100: // shopitem
			{
				final String arg1raw=CMParms.getCleanBit(funcParms,0);
				final String arg1=varify(ctx,arg1raw);
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				PhysicalAgent where=null;
				if(lastKnownLocation!=null)
				{
					if(CMath.isInteger(arg1.trim()))
						where=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
					else
						where=lastKnownLocation.fetchInhabitant(arg1.trim());
					if(where == null)
						where=this.getArgumentItem(arg1raw, ctx);
					if(where == null)
						where=this.getArgumentMOB(arg1raw, ctx);
				}
				Environmental which=null;
				int ct=1;
				if(where!=null)
				{
					ShopKeeper shopHere = CMLib.coffeeShops().getShopKeeper(where);
					if((shopHere == null)&&(ctx.scripted instanceof Item))
						shopHere=CMLib.coffeeShops().getShopKeeper(((Item)where).owner());
					if((shopHere == null)&&(ctx.scripted instanceof MOB))
						shopHere=CMLib.coffeeShops().getShopKeeper(((MOB)where).location());
					if(shopHere == null)
						shopHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
					if(shopHere!=null)
					{
						final CoffeeShop shop = shopHere.getShop();
						if(shop != null)
						{
							for(final Iterator<Environmental> i=shop.getStoreInventory();i.hasNext();ct++)
							{
								final Environmental E=i.next();
								if(ct==CMath.s_int(arg2.trim()))
								{
									which = E;
									setShopPrice(shopHere,E,ctx.tmp);
									break;
								}
							}
						}
					}
				}
				if(which!=null)
					results.append(which.name());
				break;
			}
			case 101: // numitemsshop
			{
				final String arg1raw = CMParms.cleanBit(funcParms);
				final String arg1=varify(ctx,arg1raw);
				PhysicalAgent which=null;
				if(lastKnownLocation!=null)
				{
					if(CMath.isInteger(arg1.trim()))
						which=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
					else
						which=lastKnownLocation.fetchInhabitant(arg1);
					if(which == null)
						which=this.getArgumentItem(arg1raw, ctx);
					if(which == null)
						which=this.getArgumentMOB(arg1raw, ctx);
				}
				int ct=0;
				if(which!=null)
				{
					ShopKeeper shopHere = CMLib.coffeeShops().getShopKeeper(which);
					if((shopHere == null)&&(ctx.scripted instanceof Item))
						shopHere=CMLib.coffeeShops().getShopKeeper(((Item)which).owner());
					if((shopHere == null)&&(ctx.scripted instanceof MOB))
						shopHere=CMLib.coffeeShops().getShopKeeper(((MOB)which).location());
					if(shopHere == null)
						shopHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
					if(shopHere!=null)
					{
						final CoffeeShop shop = shopHere.getShop();
						if(shop != null)
						{
							for(final Iterator<Environmental> i=shop.getStoreInventory();i.hasNext();i.next())
							{
								ct++;
							}
						}
					}
				}
				results.append(""+ct);
				break;
			}
			case 102: // shophas
			{
				final String arg1raw=CMParms.cleanBit(funcParms);
				final String arg1=varify(ctx,arg1raw);
				PhysicalAgent where=null;
				if(lastKnownLocation!=null)
				{
					if(CMath.isInteger(arg1.trim()))
						where=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
					else
						where=lastKnownLocation.fetchInhabitant(arg1.trim());
					if(where == null)
						where=this.getArgumentItem(arg1raw, ctx);
					if(where == null)
						where=this.getArgumentMOB(arg1raw, ctx);
				}
				if(where!=null)
				{
					ShopKeeper shopHere = CMLib.coffeeShops().getShopKeeper(where);
					if((shopHere == null)&&(ctx.scripted instanceof Item))
						shopHere=CMLib.coffeeShops().getShopKeeper(((Item)where).owner());
					if((shopHere == null)&&(ctx.scripted instanceof MOB))
						shopHere=CMLib.coffeeShops().getShopKeeper(((MOB)where).location());
					if(shopHere == null)
						shopHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
					if(shopHere!=null)
					{
						final CoffeeShop shop = shopHere.getShop();
						if(shop != null)
						{
							int ct=0;
							for(final Iterator<Environmental> i=shop.getStoreInventory();i.hasNext();i.next())
								ct++;
							final int which=CMLib.dice().roll(1, ct, -1);
							ct=0;
							for(final Iterator<Environmental> i=shop.getStoreInventory();i.hasNext();ct++)
							{
								final Environmental E=i.next();
								if(which == ct)
								{
									results.append(E.Name());
									setShopPrice(shopHere,E,ctx.tmp);
									break;
								}
							}
						}
					}
				}
				break;
			}
			case 48: // numitemsmob
			{
				final String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				MOB which=null;
				if(lastKnownLocation!=null)
				{
					if(CMath.isInteger(arg1.trim()))
						which=lastKnownLocation.fetchInhabitant(CMath.s_int(arg1.trim())-1);
					else
						which=lastKnownLocation.fetchInhabitant(arg1);
				}
				int ct=0;
				if(which!=null)
				{
					for(int i=0;i<which.numItems();i++)
					{
						final Item I=which.getItem(i);
						if((I!=null)&&(I.container()==null))
							ct++;
					}
				}
				results.append(""+ct);
				break;
			}
			case 36: // ishere
			{
				if(lastKnownLocation!=null)
					results.append(lastKnownLocation.getArea().name());
				break;
			}
			case 17: // inroom
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if((E==null)||arg1.length()==0)
					results.append(CMLib.map().getExtendedRoomID(lastKnownLocation));
				else
					results.append(CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(E)));
				break;
			}
			case 90: // inarea
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if((E==null)||arg1.length()==0)
					results.append(lastKnownLocation==null?"Nowhere":lastKnownLocation.getArea().Name());
				else
				{
					final Room R=CMLib.map().roomLocation(E);
					results.append(R==null?"Nowhere":R.getArea().Name());
				}
				break;
			}
			case 89: // isrecall
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
					results.append(CMLib.map().getExtendedRoomID(CMLib.map().getStartRoom(E)));
				break;
			}
			case 37: // inlocale
			{
				final String parms=varify(ctx,CMParms.cleanBit(funcParms));
				if(parms.trim().length()==0)
				{
					if(lastKnownLocation!=null)
						results.append(lastKnownLocation.name());
				}
				else
				{
					final Environmental E=getArgumentItem(parms,ctx);
					if(E!=null)
					{
						final Room R=CMLib.map().roomLocation(E);
						if(R!=null)
							results.append(R.name());
					}
				}
				break;
			}
			case 18: // sex
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
					results.append(((MOB)E).charStats().genderName());
				break;
			}
			case 91: // datetime
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				// SC2: STAMP (the clock's hours since its year 0), DAYOFYEAR, WEEKDAY
				if(arg1.equalsIgnoreCase("STAMP")||arg1.equalsIgnoreCase("DAYOFYEAR")||arg1.equalsIgnoreCase("WEEKDAY"))
				{
					final Area dA=CMLib.map().areaLocation(ctx.scripted);
					final TimeClock dC=(dA!=null)?dA.getTimeObj():CMLib.time().globalClock();
					if(arg1.equalsIgnoreCase("STAMP"))
						results.append(ScriptEvents.clockHours(dC));
					else
					if(arg1.equalsIgnoreCase("WEEKDAY"))
						results.append(ScriptEvents.weekday(dC));
					else
						results.append((dC.getMonth()*dC.getDaysInMonth())+dC.getDayOfMonth());
					break;
				}
				final TimePeriod period = TimePeriod.get(arg1.toUpperCase().trim());
				if(period == null)
					logError(ctx,"DATETIME","Syntax","Unknown arg: "+arg1+" for "+ctx.scripted.name());
				else
				{
					final Area A = CMLib.map().areaLocation(ctx.scripted);
					if(A!=null)
						results.append(A.getTimeObj().get(period));
				}
				break;
			}
			case 13: // stat
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBitClean(funcParms,0);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
				{
					final String val=getStatValue(E,arg2);
					if(val==null)
					{
						logError(ctx,"STAT","Syntax","Unknown stat: "+arg2+" for "+E.name());
						break;
					}
					results.append(val);
					break;
				}
				break;
			}
			case 52: // gstat
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBitClean(funcParms,0);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
				{
					final String val=getGStatValue(E,arg2);
					if(val==null)
					{
						logError(ctx,"GSTAT","Syntax","Unknown stat: "+arg2+" for "+E.name());
						break;
					}
					results.append(val);
					break;
				}
				break;
			}
			case 19: // position
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Physical P=getArgumentItem(arg1,ctx);
				if(P!=null)
				{
					final String sex;
					if(CMLib.flags().isSleeping(P))
						sex="SLEEPING";
					else
					if(CMLib.flags().isSitting(P))
						sex="SITTING";
					else
						sex="STANDING";
					results.append(sex);
					break;
				}
				break;
			}
			case 20: // level
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Physical P=getArgumentItem(arg1,ctx);
				if(P!=null)
					results.append(P.phyStats().level());
				break;
			}
			case 80: // questpoints
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if(E instanceof MOB)
					results.append(((MOB)E).getQuestPoint());
				break;
			}
			case 83: // qvar
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				if((arg1.length()!=0)&&(arg2.length()!=0))
				{
					final Quest Q=getQuest(arg1);
					if(Q!=null)
						results.append(Q.getStat(arg2));
				}
				break;
			}
			case 84: // math
			{
				final String arg1=varify(ctx,CMParms.cleanBit(funcParms));
				results.append(""+Math.round(CMath.s_parseMathExpression(arg1)));
				break;
			}
			case 85: // islike
			{
				final String arg1=CMParms.cleanBit(funcParms);
				results.append(CMLib.masking().maskDesc(arg1));
				break;
			}
			case 86: // strcontains
				results.append(conditionText(ctx,preFab,funcParms));
				break;
			case 81: // trains
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if(E instanceof MOB)
					results.append(((MOB)E).getTrains());
				break;
			}
			case 92: // isodd
			{
				final String val=varify(ctx ,CMParms.cleanBit(funcParms)).trim();
				boolean isodd = false;
				if( CMath.isLong( val ) )
				{
					isodd = (CMath.s_long(val) %2 == 1);
				}
				if( isodd )
				{
					results.append( CMath.s_long( val.trim() ) );
				}
				break;
			}
			case 82: // pracs
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if(E instanceof MOB)
					results.append(((MOB)E).getPractices());
				break;
			}
			case 68: // clandata
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBitClean(funcParms,0);
				final Environmental E=getArgumentItem(arg1,ctx);
				String clanID=null;
				if((E!=null)&&(E instanceof MOB))
				{
					Clan C=CMLib.clans().findRivalrousClan((MOB)E);
					if(C==null)
						C=((MOB)E).clans().iterator().hasNext()?((MOB)E).clans().iterator().next().first:null;
					if(C!=null)
						clanID=C.clanID();
				}
				else
					clanID=varify(ctx,arg1);
				final Clan C=CMLib.clans().findClan(clanID);
				if(C!=null)
				{
					if(!C.isStat(arg2))
						logError(ctx,"CLANDATA","RunTime",arg2+" is not a valid clan variable.");
					else
						results.append(C.getStat(arg2));
				}
				break;
			}
			case 98: // clanqualifies
			{
				final String arg1=CMParms.cleanBit(funcParms);
				Clan C=CMLib.clans().getClan(arg1);
				if(C==null)
					C=CMLib.clans().findClan(arg1);
				if(C!=null)
				{
					if(C.getAcceptanceSettings().length()>0)
						results.append(CMLib.masking().maskDesc(C.getAcceptanceSettings()));
					if(C.getBasicRequirementMask().length()>0)
						results.append(CMLib.masking().maskDesc(C.getBasicRequirementMask()));
					if(C.isOnlyFamilyApplicants())
						results.append("Must belong to the family.");
					final int total=CMProps.getMaxClansThisCategory(C.getCategory());
					if(C.getCategory().length()>0)
						results.append("May belong to only "+total+" "+C.getCategory()+" clan. ");
					else
						results.append("May belong to only "+total+" standard clan. ");
				}
				break;
			}
			case 67: // hastitle
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=varify(ctx,CMParms.getPastBitClean(funcParms,0));
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((arg2.length()>0)&&(E instanceof MOB)&&(((MOB)E).playerStats()!=null))
				{
					final MOB M=(MOB)E;
					results.append(M.playerStats().getActiveTitle());
				}
				break;
			}
			case 66: // clanrank
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				Clan C=null;
				if(E instanceof MOB)
				{
					C=CMLib.clans().findRivalrousClan((MOB)E);
					if(C==null)
						C=((MOB)E).clans().iterator().hasNext()?((MOB)E).clans().iterator().next().first:null;
				}
				else
					C=CMLib.clans().findClan(varify(ctx,arg1));
				if(C!=null)
				{
					final Pair<Clan,Integer> p=((MOB)E).getClanRole(C.clanID());
					if(p!=null)
						results.append(p.second.toString());
				}
				break;
			}
			case 21: // class
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
					results.append(((MOB)E).charStats().displayClassName());
				break;
			}
			case 64: // deity
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
				{
					final String name=((MOB)E).charStats().getWorshipCharID();
					results.append(name);
				}
				break;
			}
			case 65: // clan
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
				{
					Clan C=CMLib.clans().findRivalrousClan((MOB)E);
					if(C==null)
						C=((MOB)E).clans().iterator().hasNext()?((MOB)E).clans().iterator().next().first:null;
					if(C!=null)
						results.append(C.clanID());
				}
				break;
			}
			case 88: // mood
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if(E instanceof MOB)
				{
					final Ability moodA=((MOB)E).fetchEffect("Mood");
					if(moodA!=null)
						results.append(CMStrings.capitalizeAndLower(moodA.text()));
				}
				break;
			}
			case 22: // baseclass
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
					results.append(((MOB)E).charStats().getCurrentClass().baseClass());
				break;
			}
			case 23: // race
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
					results.append(((MOB)E).charStats().raceName());
				break;
			}
			case 24: //racecat
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if((E!=null)&&(E instanceof MOB))
					results.append(((MOB)E).charStats().getMyRace().racialCategory());
				break;
			}
			case 25: // goldamt
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E==null)
					results.append(false);
				else
				{
					int val1=0;
					if(E instanceof MOB)
						val1=(int)Math.round(CMLib.beanCounter().getTotalAbsoluteValue((MOB)E,CMLib.beanCounter().getCurrency(ctx.scripted)));
					else
					if(E instanceof Coins)
						val1=(int)Math.round(((Coins)E).getTotalValue());
					else
					if(E instanceof Item)
						val1=((Item)E).value();
					else
					{
						logError(ctx,"GOLDAMT","Syntax",funcParms);
						return results.toString();
					}
					results.append(val1);
				}
				break;
			}
			case 78: // exp
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentMOB(arg1,ctx);
				if(E==null)
					results.append(false);
				else
				{
					int val1=0;
					if(E instanceof MOB)
						val1=((MOB)E).getExperience();
					results.append(val1);
				}
				break;
			}
			case 76: // value
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBitClean(funcParms,0);
				if(!CMLib.beanCounter().getAllCurrencies().contains(arg2.toUpperCase()))
				{
					logError(ctx,"VALUE","Syntax",arg2+" is not a valid designated currency.");
					return results.toString();
				}
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E==null)
					results.append(false);
				else
				{
					int val1=0;
					if(E instanceof MOB)
						val1=(int)Math.round(CMLib.beanCounter().getTotalAbsoluteValue((MOB)E,arg2));
					else
					if(E instanceof Coins)
					{
						if(CMLib.beanCounter().isCurrencyMatch(((Coins)E).getCurrency(),arg2))
							val1=(int)Math.round(((Coins)E).getTotalValue());
					}
					else
					if(E instanceof Item)
						val1=((Item)E).value();
					else
					{
						logError(ctx,"GOLDAMT","Syntax",funcParms);
						return results.toString();
					}
					results.append(val1);
				}
				break;
			}
			case 26: // objtype
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
				{
					final String sex=CMClass.classID(E).toLowerCase();
					results.append(sex);
				}
				break;
			}
			case 53: // incontainer
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E!=null)
				{
					if(E instanceof MOB)
					{
						if(((MOB)E).riding()!=null)
							results.append(((MOB)E).riding().Name());
					}
					else
					if(E instanceof Item)
					{
						if(((Item)E).riding()!=null)
							results.append(((Item)E).riding().Name());
						else
						if(((Item)E).container()!=null)
							results.append(((Item)E).container().Name());
						else
						if(E instanceof Container)
						{
							final List<Item> V=((Container)E).getDeepContents();
							for(int v=0;v<V.size();v++)
								results.append("\""+V.get(v).Name()+"\" ");
						}
					}
				}
				break;
			}
			case 27: // var
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBitClean(funcParms,0).toUpperCase();
				final Environmental E=getArgumentItem(arg1,ctx);
				final String val=getVar(E,arg1,arg2,ctx);
				results.append(val);
				break;
			}
			case 41: // eval
				results.append(conditionText(ctx,preFab,funcParms));
				break;
			case 40: // number
			{
				final String val=varify(ctx,CMParms.cleanBit(funcParms)).trim();
				boolean isnumber=(val.length()>0);
				for(int i=0;i<val.length()&&(!CMath.isNumber(val));i++) // SC11: signs and decimals are numbers
				{
					if(!Character.isDigit(val.charAt(i)))
					{
						isnumber = false;
						break;
					}
				}
				if(isnumber)
					results.append(CMath.s_long(val.trim()));
				break;
			}
			case 42: // randnum
			{
				final String arg1String=varify(ctx,CMParms.cleanBit(funcParms)).toUpperCase();
				int arg1=0;
				if(CMath.isMathExpression(arg1String))
					arg1=CMath.s_parseIntExpression(arg1String.trim());
				else
					arg1=CMParms.parse(arg1String.trim()).size();
				results.append(CMLib.dice().roll(1,arg1,0));
				break;
			}
			case 71: // rand0num
			{
				final String arg1String=varify(ctx,CMParms.cleanBit(funcParms)).toUpperCase();
				int arg1=0;
				if(CMath.isMathExpression(arg1String))
					arg1=CMath.s_parseIntExpression(arg1String.trim());
				else
					arg1=CMParms.parse(arg1String.trim()).size();
				results.append(CMLib.dice().roll(1,arg1,-1));
				break;
			}
			case 96: // iscontents
			{
				final String arg1=CMParms.cleanBit(funcParms);
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E instanceof Rideable)
				{
					for(final Enumeration<Rider> r=((Rideable)E).riders();r.hasMoreElements();)
						results.append(CMParms.quoteIfNecessary(r.nextElement().name())).append(" ");
				}
				if(E instanceof Container)
				{
					for (final Item item : ((Container)E).getDeepContents())
						results.append(CMParms.quoteIfNecessary(item.name())).append(" ");
				}
				break;
			}
			case 97: // wornon
			{
				final String arg1=CMParms.getCleanBit(funcParms,0);
				final String arg2=CMParms.getPastBitClean(funcParms,0).toUpperCase();
				final PhysicalAgent E=getArgumentMOB(arg1,ctx);
				int wornLoc=-1;
				if(arg2.length()>0)
					wornLoc = CMParms.indexOf(Wearable.CODES.NAMESUP(), arg2.toUpperCase().trim());
				if(wornLoc<0)
					logError(ctx,"EVAL","BAD WORNON LOCATION",arg2);
				else
				if(E instanceof MOB)
				{
					final List<Item> items=((MOB)E).fetchWornItems(Wearable.CODES.GET(wornLoc),(short)-2048,(short)0);
					for(final Item item : items)
						results.append(CMParms.quoteIfNecessary(item.name())).append(" ");
				}
				break;
			}
			default:
				logError(ctx,"Unknown Val",preFab,evaluable);
				return results.toString();
			}
		}
		return results.toString();
	}

	protected MOB getRandPC(final MOB monster, final Object[] tmp, final Room room)
	{
		if((tmp[SPECIAL_RANDPC]==null)||(tmp[SPECIAL_RANDPC]==monster))
		{
			MOB M=null;
			if(room!=null)
			{
				final List<MOB> choices = new ArrayList<MOB>();
				for(int p=0;p<room.numInhabitants();p++)
				{
					M=room.fetchInhabitant(p);
					if((!M.isMonster())&&(M!=monster))
					{
						final HashSet<MOB> seen=new HashSet<MOB>();
						while((M.amFollowing()!=null)
						&&(!M.amFollowing().isMonster())
						&&(!seen.contains(M)))
						{
							seen.add(M);
							M=M.amFollowing();
						}
						choices.add(M);
					}
				}
				if(choices.size() > 0)
					tmp[SPECIAL_RANDPC] = choices.get(CMLib.dice().roll(1,choices.size(),-1));
			}
		}
		return (MOB)tmp[SPECIAL_RANDPC];
	}

	protected MOB getRandAnyone(final MOB monster, final Object[] tmp, final Room room)
	{
		if((tmp[SPECIAL_RANDANYONE]==null)||(tmp[SPECIAL_RANDANYONE]==monster))
		{
			MOB M=null;
			if(room!=null)
			{
				final List<MOB> choices = new ArrayList<MOB>();
				for(int p=0;p<room.numInhabitants();p++)
				{
					M=room.fetchInhabitant(p);
					if(M!=monster)
					{
						final HashSet<MOB> seen=new HashSet<MOB>();
						while((M.amFollowing()!=null)&&(!M.amFollowing().isMonster())&&(!seen.contains(M)))
						{
							seen.add(M);
							M=M.amFollowing();
						}
						choices.add(M);
					}
				}
				if(choices.size() > 0)
					tmp[SPECIAL_RANDANYONE] = choices.get(CMLib.dice().roll(1,choices.size(),-1));
			}
		}
		return (MOB)tmp[SPECIAL_RANDANYONE];
	}

	protected SubScript findFunc(final Environmental scripted, String named)
	{
		if(named==null)
			return null;
		named=named.toUpperCase();
		final List<SubScript> scripts=getScripts(scripted);
		for(int v=0;v<scripts.size();v++)
		{
			final SubScript script2=scripts.get(v);
			if(script2.getTriggerCode()==17) // function_prog
			{
				final String[] triggerBits=script2.getTriggerBits();
				if((triggerBits.length>1)
				&&(triggerBits[1].equals(named)))
					return script2;
			}
		}
		final String namedProg=named+"_PROG";
		for(int v=0;v<scripts.size();v++)
		{
			final SubScript script2=scripts.get(v);
			if(script2.getTriggerCode()!=17) // function_prog
			{
				final String[] trigger=script2.getTriggerBits();
				if(trigger.length>0)
				{
					if(trigger[0].equals(named)||trigger[0].equals(namedProg))
						return script2;
				}
			}
		}
		return null;
	}

	@Override
	public boolean isFunc(final String named)
	{
		return findFunc(null, named) != null;
	}

	@Override
	public String callFunc(final String named, final String parms, MPContext ctx)
	{
		final SubScript script2 = findFunc(ctx.scripted,named);
		if(script2 != null)
		{
			ctx = ctx.push(script2);
			ctx.msg = varify(ctx,ctx.msg);
			return execute(ctx);
		}
		return null;
	}

	@Override
	public String execute(final MPContext ctx)
	{
		// SCRIPTPROF and SCRIPTSLOW= (DT34): a trigger's whole run, timed
		if(!ScriptProf.wanted())
			return executeTraced(ctx);
		final boolean outer = ScriptProf.enter();
		final long t0 = System.nanoTime();
		try
		{
			return executeTraced(ctx);
		}
		finally
		{
			ScriptProf.leave();
			if(outer && (ctx.script != null) && (ctx.scripted != null))
			{
				final long ns = System.nanoTime() - t0;
				final String where = (ctx.scripted instanceof PhysicalAgent) ? scriptLocation((PhysicalAgent)ctx.scripted, ctx.script, 0) : "";
				final Room R = CMLib.map().roomLocation(ctx.scripted);
				ScriptProf.record(((where.length() > 0) ? (where + " ") : "") + ctx.script.getTriggerLine(),
					ctx.scripted.name() + " @ " + ((R == null) ? "?" : CMLib.map().getExtendedRoomID(R)),
					((R == null) || (R.getArea() == null)) ? "" : R.getArea().Name(), ns);
			}
		}
	}

	protected String executeTraced(final MPContext ctx)
	{
		if(!ScriptTrace.isOn())
			return executeBody(ctx);
		// SCRIPTTRACE (DT18): the lines a traced host runs, and the variables they change
		final Object was=ScriptTrace.beginRun(ctx.scripted);
		try
		{
			if((ctx.script!=null)&&(ctx.line<=1)&&ScriptTrace.traces(ctx.scripted,ctx.script.getTriggerCode()))
				ScriptTrace.tell(ctx.scripted,"runs "+ctx.script.getTriggerLine());
			return executeBody(ctx);
		}
		finally
		{
			ScriptTrace.endRun(was);
		}
	}

	/** SC7: the bodies running in this engine on this thread, outermost first, for MPWAIT */
	protected final ThreadLocal<ArrayList<MPContext>> frames = ThreadLocal.withInitial(ArrayList::new);

	/** SC17: what CONTINUE returns, to end a loop's pass */
	protected static final String LOOP_CONTINUE = "\u0000CONTINUE";

	/** SC7: what MPWAIT returns, to end the bodies it paused */
	protected static final String WAITED = "\u0000MPWAIT";

	protected String executeBody(final MPContext ctx)
	{
		// SC17: FUNCTION_PROG name A B: $(A) and $(B) are the words of the call, the last the rest
		if((ctx.line<=1)&&(ctx.script!=null)&&(ctx.script.getTriggerCode()==17))
		{
			final String[] tb=ctx.script.getTriggerBits();
			if(tb.length>2)
			{
				ctx.locals=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
				final List<String> words=CMParms.parse((ctx.msg==null)?"":ctx.msg);
				for(int i=2;i<tb.length;i++)
				{
					final int w=i-2;
					final String val=(w>=words.size())?"":((i==tb.length-1)?String.join(" ",words.subList(w,words.size())):words.get(w));
					ctx.locals.put(tb[i],val);
				}
			}
		}
		final ArrayList<MPContext> fs=frames.get();
		fs.add(ctx);
		try
		{
			return executeLines(ctx);
		}
		finally
		{
			fs.remove(fs.size()-1);
		}
	}

	/**
	 * SC7: what is left of a body MPWAIT paused: its lines after the wait,
	 * then those after each block it is in, back to the trigger's body.
	 * @param ctx the waiting body's context
	 * @param first a line to run first (MPWAIT UNTIL's, again), or null
	 * @return the lines to run later, or null if it cannot pause here
	 */
	protected SubScript waitContinuation(final MPContext ctx, final String first)
	{
		final List<MPContext> fs=frames.get();
		if(fs.isEmpty()||(fs.get(fs.size()-1)!=ctx)||(ctx.script==null))
			return null;
		int top=fs.size()-1;
		while((top>0)&&(fs.get(top-1).tmp==ctx.tmp))
			top--;
		for(int i=top;i<fs.size();i++)
		{
			final MPContext f=fs.get(i);
			if(f.repeats||(f.script==null)||(!Objects.equals(f.msg,ctx.msg))||((i>top)&&(f.script.getTriggerCode()!=0)))
			{
				logError(ctx,"MPWAIT","RunTime","MPWAIT cannot pause a loop (FOR, MPWHILE) or a function another line called, so it does not wait");
				return null;
			}
		}
		final SubScript body=fs.get(top).script;
		final SubScriptImpl cont=new SubScriptImpl(null);
		cont.add(new ScriptLn((body.size()>0)?body.get(0).first:"",null,null));
		if(first!=null)
			cont.add(new ScriptLn(first,null,null));
		for(int i=fs.size()-1;i>=top;i--)
		{
			final MPContext f=fs.get(i);
			for(int l=f.line+1;l<f.script.size();l++)
				cont.add(new ScriptLn(f.script.get(l).first,null,null));
		}
		cont.setFlag("MPWAIT");
		return cont;
	}

	protected String executeLines(final MPContext ctx)
	{
		final SubScript script = ctx.script;
		tickStatus=Tickable.STATUS_START;
		String s=null;
		String[] tt=null;
		String cmd=null;
		final boolean traceDebugging=CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTTRACE);
		if(traceDebugging && ctx.line == 1 && script.size()>0 && script.getTriggerLine().length()>0)
			Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": * EXECUTE: "+script.getTriggerLine());
		for(;ctx.line<script.size();ctx.line++)
		{
			s=script.get(ctx.line).first;
			tt=script.get(ctx.line).second;
			if(tt!=null)
				cmd=tt[0];
			else
				cmd=CMParms.getCleanBit(s,0).toUpperCase();
			if(cmd.length()==0)
				continue;
			if(traceDebugging)
				Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": "+ctx.line+": "+s);
			if(ScriptTrace.isOn()&&ScriptTrace.traces(ctx.scripted,script.getTriggerCode()))
				ScriptTrace.tell(ctx.scripted,"  "+ctx.line+": "+s);

			Integer methCode=methH.get(cmd);
			if((methCode==null)&&(cmd.startsWith("MP")))
			{
				// a prefix of one command stands for it; of none or of several, it is an error
				final List<String> could=new ArrayList<String>(2);
				for(int i=0;i<methods.length;i++)
				{
					if(methods[i].startsWith(cmd))
					{
						if(methCode==null)
							methCode=Integer.valueOf(i+1); // offset by 1, always
						could.add(methods[i]);
					}
				}
				if(methCode==null)
				{
					logError(ctx,cmd,"Syntax","unknown command "+cmd);
					continue; // an MP word is never run as a game command
				}
				if(could.size()>1)
				{
					logError(ctx,cmd,"Syntax","ambiguous command "+cmd+": "+CMParms.toListString(could));
					continue;
				}
			}
			if(methCode==null)
				methCode=Integer.valueOf(0);
			tickStatus=Tickable.STATUS_MISC3+methCode.intValue();
			// SC1 and on: the newer commands, in one place; one that returns something ends the body (MPWAIT)
			if(methCode.intValue()>=EXTRA_METHODS_FROM)
			{
				final String resp=extraCommand(ctx,cmd,s);
				if(resp!=null)
				{
					tickStatus=Tickable.STATUS_END;
					return resp;
				}
				continue;
			}
			switch(methCode.intValue())
			{
			case 57: // <SCRIPT>
			{
				if(tt==null)
					tt=parseBits(ctx,"C");

				final ScriptLn ln = script.get(ctx.line);
				final StringBuffer jscript;
				if(ln.third instanceof Object[])
				{
					final Object[] obj = (Object[])ln.third;
					jscript=(StringBuffer)obj[0];
					ctx.line += ((Integer)obj[1]).intValue();
				}
				else
				{
					jscript=new StringBuffer("");
					final int oldsi = ctx.line;
					while((++ctx.line)<script.size())
					{
						s=script.get(ctx.line).first;
						tt=script.get(ctx.line).second;
						if(tt!=null)
							cmd=tt[0];
						else
							cmd=CMParms.getCleanBit(s,0).toUpperCase();
						if(cmd.equals("</SCRIPT>"))
						{
							if(tt==null)
								tt=parseBits(ctx,"C");
							break;
						}
						jscript.append(s+"\n");
					}
					ln.third = new Object[] {jscript, Integer.valueOf(ctx.line-oldsi)};
				}
				if(this.approvedScripts || CMSecurity.isApprovedJScript(jscript))
				{
					final int trig=script.getTriggerCode();
					// the file and line of <SCRIPT>, so that a JavaScript error's line is the file's
					final String fileAt=(ctx.scripted instanceof PhysicalAgent)?scriptLocation((PhysicalAgent)ctx.scripted,script,ctx.line-jsLines(ln)):"";
					final int colon=fileAt.lastIndexOf(':');
					final int firstLine=((colon>0)&&CMath.isInteger(fileAt.substring(colon+1)))?(CMath.s_int(fileAt.substring(colon+1))+1):1;
					final String where="'"+ctx.scripted.Name()+"' @ "+CMLib.map().getDescriptiveExtendedRoomID(lastKnownLocation)
						+((trig>0)&&(trig<=progs.length)?(" "+progs[trig-1]):"")+" <SCRIPT>"
						+((colon>0)?(" in "+fileAt.substring(0,colon)):"");
					final Context cx = Context.enter();
					try
					{
						final JScriptEvent scope = jsEventScope(cx, ctx);
						// one line ending each, as the file has, so that Rhino's lines are the file's
						final Object jsr=cx.evaluateString(scope, jscript.toString().replaceAll("\\r\\n|\\n\\r|\\r","\n"),where, firstLine, null);
						if(jsCancelled(scope,jsr)) // JS15: cancel(), or a last value of "cancel", as RETURN CANCEL
						{
							tickStatus=Tickable.STATUS_END;
							return "CANCEL";
						}
					}
					catch(final Exception e)
					{
						JSRuntime.report(where, e);
					}
					finally
					{
						Context.exit();
					}
				}
				else
				if(CMProps.getIntVar(CMProps.Int.JSCRIPTS)==CMSecurity.JSCRIPT_REQ_APPROVAL)
				{
					if(lastKnownLocation!=null)
						lastKnownLocation.showHappens(CMMsg.MSG_OK_ACTION,L("A Javascript was not authorized.  Contact an Admin to use MODIFY JSCRIPT to authorize this script."));
				}
				break;
			}
			case 19: // if
			{
				final ScriptLn curLine;
				if(tt==null)
				{
					try
					{
						final String[] ttParms=parseEval(s.substring(2));
						tt=new String[ttParms.length+1];
						tt[0]="IF";
						for(int i=0;i<ttParms.length;i++)
							tt[i+1]=ttParms[i];
						script.get(ctx.line).second=tt;
						script.get(ctx.line).third=new Triad<SubScript,SubScript,Integer>(null,null,null);
					}
					catch(final Exception e)
					{
						logError(ctx,"IF","Syntax",e.getMessage());
						tickStatus=Tickable.STATUS_END;
						return null;
					}
				}

				final String[][] eval={tt};
				final boolean condition=eval(ctx,eval,1);
				if(eval[0]!=tt)
				{
					tt=eval[0];
					script.get(ctx.line).second=tt;
				}
				boolean foundendif=false;
				final Object triad = script.get(ctx.line).third;
				final Triad<SubScript,SubScript,Integer> parsedBlocks;
				SubScript subScript;
				if(triad==null)
				{
					Log.errOut("Null parsed blocks in "+s);
					parsedBlocks = new Triad<SubScript,SubScript,Integer>(null,null,null);
					script.get(ctx.line).third=parsedBlocks;
					subScript=null;
				}
				else
				if(!(triad instanceof Triad))
				{
					// when casting here fails, its because IF is NOW at this line, but something else used to be? like MPCALLFUNC...
					// could this be a threadding issue? nah, contexts are always new, right?
					logError(ctx,"IF","RUNTIME","Non-Triad!");
					throw new IllegalArgumentException("Non-Triad (see above)");
				}
				else
				{
					@SuppressWarnings("unchecked")
					final Triad<SubScript,SubScript,Integer> pb = parsedBlocks = (Triad<SubScript,SubScript,Integer>)triad;
					if(parsedBlocks.third != null)
					{
						if(condition)
							subScript=parsedBlocks.first;
						else
							subScript=parsedBlocks.second;
						ctx.line=parsedBlocks.third.intValue();
						ctx.line--; // because we want to be pointing at the ENDIF with ctx.line++ happens below.
						foundendif=true;
					}
					else
						subScript=null;
				}
				int depth=0;
				boolean ignoreUntilEndScript=false;
				ctx.line++;
				boolean positiveCondition=true;
				while((ctx.line<script.size())
				&&(!foundendif))
				{
					final ScriptLn ln = script.get(ctx.line);
					if(ln==null)
					{
						ctx.line++;
						continue;
					}
					s=ln.first;
					tt=ln.second;
					if(tt!=null)
						cmd=tt[0];
					else
						cmd=CMParms.getCleanBit(s,0).toUpperCase();
					if(cmd.equals("ENDIF")&&(depth==0)&&(!ignoreUntilEndScript))
					{
						if(tt==null)
							tt=parseBits(ctx,"C");
						parsedBlocks.third=Integer.valueOf(ctx.line);
						foundendif=true;
						break;
					}
					else
					if(cmd.equals("ELSE")&&(depth==0)&&(!ignoreUntilEndScript))
					{
						positiveCondition=false;
						if(s.substring(4).trim().length()>0)
							logError(ctx,"ELSE","Syntax"," Decorated ELSE is now illegal!!");
						else
						if(tt==null)
							tt=parseBits(ctx,"C");
					}
					else
					{
						if(!ignoreUntilEndScript)
						{
							if(cmd.equals("IF"))
								depth++;
							else
							if(cmd.equals("ENDIF"))
							{
								if(tt==null)
									tt=parseBits(ctx,"C");
								depth--;
							}
						}
						if(cmd.equals("<SCRIPT>"))
						{
							if(tt==null)
								tt=parseBits(ctx,"C");
							ignoreUntilEndScript=true;
						}
						else
						if(cmd.equals("</SCRIPT>"))
						{
							if(tt==null)
								tt=parseBits(ctx,"C");
							ignoreUntilEndScript=false;
						}
						if(positiveCondition)
						{
							if(parsedBlocks.first==null)
							{
								parsedBlocks.first=new SubScriptImpl(script);
								parsedBlocks.first.add(new ScriptLn("",null,null));
							}
							if(condition)
								subScript=parsedBlocks.first;
							parsedBlocks.first.add(script.get(ctx.line));
						}
						else
						{
							if(parsedBlocks.second==null)
							{
								parsedBlocks.second=new SubScriptImpl(script);
								parsedBlocks.second.add(new ScriptLn("",null,null));
							}
							if(!condition)
								subScript=parsedBlocks.second;
							parsedBlocks.second.add(script.get(ctx.line));
						}
					}
					ctx.line++;
				}
				if(!foundendif)
				{
					logError(ctx,"IF","Syntax"," Without ENDIF!");
					tickStatus=Tickable.STATUS_END;
					return null;
				}
				if((subScript != null)
				&&(subScript.size()>1)
				&&(subScript != script))
				{
					final String response=execute(ctx.push(subScript));
					if(response!=null)
					{
						tickStatus=Tickable.STATUS_END;
						return response;
					}
				}
				break;
			}
			case 93: //	"ENDIF", //93 JUST for catching errors...
				logError(ctx,"ENDIF","Syntax"," Without IF ("+ctx.line+")!");
				break;
			case 94: //"ENDSWITCH", //94 JUST for catching errors...
				logError(ctx,"ENDSWITCH","Syntax"," Without SWITCH ("+ctx.line+")!");
				break;
			case 95: //"NEXT", //95 JUST for catching errors...
				logError(ctx,"NEXT","Syntax"," Without FOR ("+ctx.line+")!");
				break;
			case 96: //"CASE" //96 JUST for catching errors...
				logError(ctx,"CASE","Syntax"," Without SWITCH ("+ctx.line+")!");
				break;
			case 97: //"DEFAULT" //97 JUST for catching errors...
				logError(ctx,"DEFAULT","Syntax"," Without SWITCH ("+ctx.line+")!");
				break;
			case 70: // switch
			{
				if(tt==null)
				{
					tt=parseBits(ctx,"Cr");
					if(script.get(ctx.line).third==null)
						script.get(ctx.line).third=Collections.synchronizedMap(new HashMap<String,Integer>());
				}
				@SuppressWarnings("unchecked")
				Map<String,Integer> skipSwitchMap=(Map<String,Integer>)script.get(ctx.line).third;
				if(skipSwitchMap==null)
				{
					skipSwitchMap=Collections.synchronizedMap(new HashMap<String,Integer>());
					script.get(ctx.line).third=skipSwitchMap;
				}
				final String var=varify(ctx,tt[1]).trim();
				final SubScript subScript=new SubScriptImpl(script);
				subScript.add(new ScriptLn("",null,null));
				int depth=0;
				boolean foundEndSwitch=false;
				boolean ignoreUntilEndScript=false;
				boolean inCase=false;
				boolean matchedCase=false;
				ctx.line++;
				String s2=null;
				if(skipSwitchMap.size()>0)
				{
					if(skipSwitchMap.containsKey(var.toUpperCase()))
					{
						inCase=true;
						matchedCase=true;
						ctx.line=skipSwitchMap.get(var.toUpperCase()).intValue();
						ctx.line++;
					}
					else
					if(skipSwitchMap.containsKey("$FIRSTVAR"))  // first variable case
					{
						ctx.line=skipSwitchMap.get("$FIRSTVAR").intValue();
					}
					else
					if(skipSwitchMap.containsKey("$DEFAULT")) // the "default" case
					{
						inCase=true;
						matchedCase=true;
						ctx.line=skipSwitchMap.get("$DEFAULT").intValue();
						ctx.line++;
					}
					else
					if(skipSwitchMap.containsKey("$ENDSWITCH")) // the "endswitch" case
					{
						foundEndSwitch=true;
						ctx.line=skipSwitchMap.get("$ENDSWITCH").intValue();
					}
				}
				while((ctx.line<script.size())
				&&(!foundEndSwitch))
				{
					s=script.get(ctx.line).first;
					tt=script.get(ctx.line).second;
					if(tt!=null)
						cmd=tt[0];
					else
						cmd=CMParms.getCleanBit(s,0).toUpperCase();
					if((!ignoreUntilEndScript) && cmd.equals("ENDSWITCH")&&(depth==0))
					{
						if(tt==null)
						{
							tt=parseBits(ctx,"C");
							skipSwitchMap.put("$ENDSWITCH", Integer.valueOf(ctx.line));
						}
						foundEndSwitch=true;
						break;
					}
					else
					if((!ignoreUntilEndScript) && cmd.equals("CASE")&&(depth==0))
					{
						if(tt==null)
						{
							tt=parseBits(ctx,"Ccr");
							if(tt[1].indexOf('$')>=0)
							{
								if(!skipSwitchMap.containsKey("$FIRSTVAR"))
									skipSwitchMap.put("$FIRSTVAR", Integer.valueOf(ctx.line));
							}
							else
							if(!skipSwitchMap.containsKey(tt[1].toUpperCase()))
								skipSwitchMap.put(tt[1].toUpperCase(), Integer.valueOf(ctx.line));
						}
						if(matchedCase
						&&inCase
						&&(skipSwitchMap.containsKey("$ENDSWITCH"))) // we're done
						{
							foundEndSwitch=true;
							ctx.line=skipSwitchMap.get("$ENDSWITCH").intValue();
							break; // this is important, otherwise ctx.line will get increment and screw stuff up
						}
						else
						{
							s2=varify(ctx,tt[1]).trim();
							inCase=var.equalsIgnoreCase(s2);
							matchedCase=matchedCase||inCase;
						}
					}
					else
					if((!ignoreUntilEndScript) && cmd.equals("DEFAULT")&&(depth==0))
					{
						if(tt==null)
						{
							tt=parseBits(ctx,"C");
							if(!skipSwitchMap.containsKey("$DEFAULT"))
								skipSwitchMap.put("$DEFAULT", Integer.valueOf(ctx.line));
						}
						if(matchedCase
						&&inCase
						&&(skipSwitchMap.containsKey("$ENDSWITCH"))) // we're done
						{
							foundEndSwitch=true;
							ctx.line=skipSwitchMap.get("$ENDSWITCH").intValue();
							break; // this is important, otherwise ctx.line will get increment and screw stuff up
						}
						else
							inCase=!matchedCase;
					}
					else
					{
						if(inCase)
						{
							final ScriptLn line = script.get(ctx.line);
							if(line != null)
								subScript.add(line);
						}
						if(cmd.equals("<SCRIPT>"))
						{
							if(tt==null)
								tt=parseBits(ctx,"C");
							ignoreUntilEndScript=true;
						}
						else
						if(cmd.equals("</SCRIPT>"))
						{
							if(tt==null)
								tt=parseBits(ctx,"C");
							ignoreUntilEndScript=false;
						}
						else
						if(!ignoreUntilEndScript)
						{
							if(cmd.equals("SWITCH"))
							{
								if(tt==null)
								{
									tt=parseBits(ctx,"Cr");
									if(script.get(ctx.line).third==null)
										script.get(ctx.line).third=Collections.synchronizedMap(new HashMap<String,Integer>());
								}
								depth++;
							}
							else
							if(cmd.equals("ENDSWITCH"))
							{
								if(tt==null)
									tt=parseBits(ctx,"C");
								depth--;
							}
						}
					}
					ctx.line++;
				}
				if(!foundEndSwitch)
				{
					logError(ctx,"SWITCH","Syntax"," Without ENDSWITCH!");
					tickStatus=Tickable.STATUS_END;
					return null;
				}
				if(subScript.size()>1)
				{
					final String response=execute(ctx.push(subScript));
					if(response!=null)
					{
						tickStatus=Tickable.STATUS_END;
						return response;
					}
				}
				break;
			}
			case 62: // for x = 1 to 100, for x in, for, next
			{
				if(tt==null)
				{
					tt=parseBits(ctx,"CcccCr");
					if(tt[2].equalsIgnoreCase("in"))
						tt=parseBits(ctx,"CcCr");
				}
				final String varStr=tt[1];
				if((varStr.length()!=2)||(varStr.charAt(0)!='$')||(!Character.isDigit(varStr.charAt(1))))
				{
					logError(ctx,"FOR","Syntax","'"+varStr+"' is not a tmp var $1, $2..");
					tickStatus=Tickable.STATUS_END;
					return null;
				}
				final int whichVar=CMath.s_int(Character.toString(varStr.charAt(1)));
				if((ctx.tmp[whichVar] instanceof String)
				&&(((String)ctx.tmp[whichVar]).length()>0)
				&&(CMath.isInteger(((String)ctx.tmp[whichVar]).trim())))
				{
					logError(ctx,"FOR","Syntax","'"+whichVar+"' is already in use! Use a different one!");
					tickStatus=Tickable.STATUS_END;
					return null;
				}
				final int fromInt;
				int toInt;
				int toAdd=0;
				List<Object> inList = null;
				if(tt.length==4) // for in
				{
					if((tt[3].indexOf(':')==6)
					&&(tt[3].substring(0, 6).toLowerCase().equals("select")))
					{
						try
						{
							final List<Map<String,Object>> m = CMLib.percolator().doMQLSelectObjects(ctx.scripted, tt[3]);
							inList=new ArrayList<Object>(m.size());
							for (final Map<String, Object> map : m)
							{
								if(m.size()==0)
									continue;
								final CMObject O = (CMObject) map.get(map.keySet().iterator().next());
								if (O != null)
									inList.add(O);
							}
						}
						catch (final MQLException e)
						{
							logError(ctx,"FOR","Bad MQL",e.getMessage()+": "+tt[3]);
							tickStatus=Tickable.STATUS_END;
							return null;
						}
					}
					else
					if(tt[3].toUpperCase().startsWith("LIST ")||tt[3].equalsIgnoreCase("LIST"))
						inList = new ArrayList<Object>(ScriptLib.listOf(varify(ctx,tt[3].substring(4).trim()))); // SC1: put in first, then split
					else
					if(tt[3].startsWith("#"))
						inList = new XArrayList<Object>(CMLib.map().getTaggedObjects(tt[3].substring(1)));
					else
					{
						final List<String> objs = CMParms.parseCommas(tt[3], false);
						inList=new ArrayList<Object>(objs.size());
						for (int o = 0; o < objs.size(); o++)
							inList.add(varify(ctx, objs.get(o)));
					}
					fromInt=0;
					toInt=inList.size()-1;
				}
				else
				{
					if(tt[5].length()==0)
					{
						logError(ctx,"FOR","Syntax","5 parms required!");
						tickStatus=Tickable.STATUS_END;
						return null;
					}
					if(!tt[2].equals("="))
					{
						logError(ctx,"FOR","Syntax","'"+s+"' is illegal for syntax!");
						tickStatus=Tickable.STATUS_END;
						return null;
					}
					if(tt[4].equals("TO<"))
						toAdd=-1;
					else
					if(tt[4].equals("TO>"))
						toAdd=1;
					else
					if(!tt[4].equals("TO"))
					{
						logError(ctx,"FOR","Syntax","'"+s+"' is illegal for syntax!");
						tickStatus=Tickable.STATUS_END;
						return null;
					}
					final String from=varify(ctx,tt[3]).trim();
					final String to=varify(ctx,tt[5]).trim();
					if((!CMath.isInteger(from))||(!CMath.isInteger(to)))
					{
						logError(ctx,"FOR","Syntax","'"+from+"-"+to+"' is illegal range!");
						tickStatus=Tickable.STATUS_END;
						return null;
					}
					fromInt=CMath.s_int(from);
					toInt=CMath.s_int(to);
				}
				final ScriptLn forLine = script.get(ctx.line);
				final SubScript subScript;
				if(forLine.third instanceof Pair)
				{
					@SuppressWarnings("unchecked")
					final Pair<SubScript,Integer> p = (Pair<SubScript,Integer>)forLine.third;
					subScript = p.first;
					ctx.line = p.second.intValue();
				}
				else
				{
					subScript=new SubScriptImpl(script);
					subScript.add(new ScriptLn("",null,null));
					int depth=0;
					boolean foundnext=false;
					boolean ignoreUntilEndScript=false;
					ctx.line++;
					while(ctx.line<script.size())
					{
						final ScriptLn line = script.get(ctx.line);
						if(line == null)
						{
							logError(ctx,"FOR","Runtime"," NULL LINE # "+ctx.line+"/"+script.size()+"!");
							continue;
						}
						s=line.first;
						tt=line.second;
						if(tt!=null)
							cmd=tt[0];
						else
							cmd=CMParms.getCleanBit(s,0).toUpperCase();
						if((!ignoreUntilEndScript) && cmd.equals("NEXT")&&(depth==0))
						{
							if(tt==null)
								tt=parseBits(ctx,"C");
							foundnext=true;
							break;
						}
						else
						{
							if(cmd.equals("<SCRIPT>"))
							{
								if(tt==null)
									tt=parseBits(ctx,"C");
								ignoreUntilEndScript=true;
							}
							else
							if(cmd.equals("</SCRIPT>"))
							{
								if(tt==null)
									tt=parseBits(ctx,"C");
								ignoreUntilEndScript=false;
							}
							else
							if(!ignoreUntilEndScript)
							{
								if(cmd.equals("FOR"))
								{
									if(tt==null)
										tt=parseBits(ctx,"CcccCr");
									depth++;
								}
								else
								if(cmd.equals("NEXT"))
								{
									if(tt==null)
										tt=parseBits(ctx,"C");
									depth--;
								}
							}
							subScript.add(line);
						}
						ctx.line++;
					}
					if(!foundnext)
					{
						logError(ctx,"FOR","Syntax"," Without NEXT!");
						tickStatus=Tickable.STATUS_END;
						return null;
					}
					forLine.third = new Pair<SubScript,Integer>(subScript,Integer.valueOf(ctx.line));
				}
				if(subScript.size()>1)
				{
					//source.tell(L("Starting @x1",conditionStr));
					//for(int v=0;v<V.size();v++)
					//  source.tell(L("Statement @x1",((String)V.elementAt(v))));
					final int increment=(toInt>=fromInt)?1:-1;
					String response=null;
					if(((fromInt>=0)||(inList==null))
					&&((toInt>=0)||(inList==null))
					&&(((increment>0)&&(fromInt<=(toInt+toAdd)))
						||((increment<0)&&(fromInt>=(toInt+toAdd)))))
					{
						toInt+=toAdd;
						final long tm=System.currentTimeMillis()+(10 * 1000);
						for(int forLoop=fromInt;forLoop!=toInt;forLoop+=increment)
						{
							if ((inList != null) && (forLoop < inList.size()))
								ctx.tmp[whichVar] = inList.get(forLoop);
							else
								ctx.tmp[whichVar]=""+forLoop;
							final MPContext newContext = ctx.push(subScript);
							newContext.repeats=true; // SC7
							response=execute(newContext);
							if(LOOP_CONTINUE.equals(response)) // SC17
								response=null;
							if(response!=null)
								break;
							if((System.currentTimeMillis()>tm) || (ctx.scripted.amDestroyed()))
							{
								logError(ctx,"FOR","Runtime","For loop ("+fromInt+"-"+toInt+"/"+increment+") violates 10 second rule: " +s);
								break;
							}
						}
						if ((inList != null) && (toInt < inList.size()))
							ctx.tmp[whichVar] = inList.get(toInt);
						else
							ctx.tmp[whichVar]=""+toInt;
						if(response == null)
						{
							final MPContext newContext = ctx.push(subScript);
							newContext.repeats=true; // SC7
							response=execute(newContext);
							if(LOOP_CONTINUE.equals(response)) // SC17
								response=null;
						}
						else
						if(response.equalsIgnoreCase("break"))
							response=null;
					}
					if(response!=null)
					{
						tickStatus=Tickable.STATUS_END;
						return response;
					}
					ctx.tmp[whichVar]=null;
				}
				break;
			}
			case 50: // break;
				if(tt==null)
					tt=parseBits(ctx,"C");
				tickStatus=Tickable.STATUS_END;
				return "BREAK";
			case 1: // mpasound
			{
				if(tt==null)
					tt=parseBits(ctx,"Cp");
				final String echo=varify(ctx,tt[1]);
				//lastKnownLocation.showSource(monster,null,CMMsg.MSG_OK_ACTION,echo);
				for(int d=Directions.NUM_DIRECTIONS()-1;d>=0;d--)
				{
					final Room R2=lastKnownLocation.getRoomInDir(d);
					final Exit E2=lastKnownLocation.getExitInDir(d);
					if((R2!=null)&&(E2!=null)&&(E2.isOpen()))
						R2.showOthers(ctx.monster,null,null,CMMsg.MSG_OK_ACTION,echo);
				}
				break;
			}
			case 4: // mpjunk
			{
				if(tt==null)
					tt=parseBits(ctx,"CR");
				if(tt[1].equals("ALL") && (ctx.monster!=null))
				{
					ctx.monster.delAllItems(true);
				}
				else
				{
					Item I=null;
					if(tt[1].indexOf('$')>=0)
					{
						final Environmental E=getArgumentItem(tt[1],ctx);
						if(E instanceof Item)
							I=(Item)E;
					}
					if((I==null)&&(ctx.monster!=null))
						I=ctx.monster.findItem(varify(ctx,tt[1]));
					if((I==null)&&(ctx.scripted instanceof Room))
						I=((Room)ctx.scripted).findItem(varify(ctx,tt[1]));
					if(I == null)
					{
						final Environmental E=getArgumentItem(tt[1],ctx);
						if(E instanceof Item)
							I=(Item)E;
					}
					if(I!=null)
					{
						final ItemPossessor p = I.owner();
						ScriptEvents.destroyReason("JUNKED"); // ST27
						I.destroy();
						ScriptEvents.destroyReason(null);
						if(p != null)
							p.recoverPhyStats();
					}
				}
				break;
			}
			case 2: // mpecho
			{
				if(tt==null)
					tt=parseBits(ctx,"Cp");
				int msgType = CMMsg.MSG_OK_ACTION;
				final Room R = lastKnownLocation;
				if(R!=null)
				{
					String str = varify(ctx,tt[1]);
					if(str.startsWith("NOSOUND "))
					{
						msgType = CMMsg.MSG_OK_VISUAL;
						str=str.substring(8);
					}
					R.show(ctx.monster,null,msgType,str);
				}
				break;
			}
			case 13: // mpunaffect
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				String which=tt[2];
				if(newTarget!=null)
				if(which.equalsIgnoreCase("all")||(which.length()==0))
				{
					for(int a=newTarget.numEffects()-1;a>=0;a--)
					{
						final Ability A=newTarget.fetchEffect(a);
						if(A!=null)
							A.unInvoke();
					}
				}
				else
				{
					final Ability A2=findAbility(which);
					if(A2!=null)
						which=A2.ID();
					final Ability A=newTarget.fetchEffect(which);
					if(A!=null)
					{
						if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster()))
							Log.sysOut("Scripting",newTarget.Name()+" was MPUNAFFECTED by "+A.Name());
						A.unInvoke();
						if(newTarget.fetchEffect(which)==A)
							newTarget.delEffect(A);
					}
				}
				break;
			}
			case 3: // mpslay
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget!=null)&&(newTarget instanceof MOB))
					CMLib.combat().postDeath(ctx.monster,(MOB)newTarget,null);
				break;
			}
			case 73: // mpsetinternal
			{
				if(tt==null)
					tt=parseBits(ctx,"CCr");
				final String arg2=tt[1].toUpperCase().trim();
				final String arg3=varify(ctx,tt[2]);
				if(arg2.equals("SCOPE"))
					setVarScope(arg3);
				else
				if(arg2.equals("CANCELWAITS")) // SC7: drops the bodies MPWAIT paused
				{
					for(final ScriptableResponse SB : new XVector<ScriptableResponse>(que))
					{
						if((SB!=null)&&(SB.scr!=null)&&SB.scr.isFlagSet("MPWAIT")&&(SB.ctx!=ctx))
							que.remove(SB);
					}
				}
				else
				if(arg2.equals("VARHOST")) // SC4: INSTANCE or NAME
					varHostInstance=arg3.trim().equalsIgnoreCase("INSTANCE");
				else
				if(arg2.equals("NODELAY"))
					noDelay=CMath.s_bool(arg3);
				else
				if(arg2.equals("ACTIVETRIGGER")||arg2.equals("ACTIVETRIGGERS"))
					alwaysTriggers=CMath.s_bool(arg3);
				else
				if(arg2.equals("MULTITRIGGER")||arg2.equals("MULTITRIGGERS"))
					multiTriggers=CMath.s_bool(arg3);
				else
				if(arg2.equals("DEFAULTQUEST"))
					registerDefaultQuest(arg3);
				else
				if(arg2.equals("SAVABLE"))
					setSavable(CMath.s_bool(arg3));
				else
				if(arg2.equals("PASSIVE"))
					this.runInPassiveAreas = CMath.s_bool(arg3);
				else
				if(arg2.equals("NEEDPC"))
					this.runWithoutPCs = !CMath.s_bool(arg3);
				else
				if(arg2.equals("TRACE"))
				{
					// the builder sees this host's script traced (SCRIPTTRACE, DT18)
					if(CMath.s_bool(arg3))
						ScriptTrace.startFromScript(ctx.scripted);
					else
					if(ScriptTrace.traces(ctx.scripted,0))
						ScriptTrace.stop();
				}
				else
				if(arg2.startsWith("LLM"))
					miscArgVars.put(arg2, arg3);
				else
					logError(ctx,"MPSETINTERNAL","Syntax","Unknown stat: "+arg2);
				break;
			}
			case 74: // mpprompt
			{
				if(tt==null)
					tt=parseBits(ctx,"CCCr");
				script.setFlag("SPAWN");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String var=varify(ctx,tt[2]);
				final String promptStr=varify(ctx,tt[3]);
				if((newTarget!=null)&&(newTarget instanceof MOB)&&(promptSession((MOB)newTarget)!=null))
				{
					try
					{
						final String value=promptSession((MOB)newTarget).prompt(promptFor((MOB)newTarget,promptStr),360000);
						if(CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTVARS))
							Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": SETVAR: "+newTarget.Name()+"("+var+")="+value+"<");
						setVar(newTarget.Name(),var,value);
					}
					catch(final Exception e)
					{
						return "";
					}
				}
				break;
			}
			case 75: // mpconfirm
			{
				if(tt==null)
					tt=parseBits(ctx,"CCCCr");
				script.setFlag("SPAWN");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String var=varify(ctx,tt[2]);
				final String defaultVal=varify(ctx,tt[3]);
				final String promptStr=varify(ctx,tt[4]);
				if((newTarget!=null)&&(newTarget instanceof MOB)&&(promptSession((MOB)newTarget)!=null))
				{
					try
					{
						final String value=promptSession((MOB)newTarget).confirm(promptFor((MOB)newTarget,promptStr),defaultVal,60000)?"Y":"N";
						if(CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTVARS))
							Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": SETVAR: "+newTarget.Name()+"("+var+")="+value+"<");
						setVar(newTarget.Name(),var,value);
					}
					catch(final Exception e)
					{
						return "";
					}
				}
				break;
			}
			case 76: // mpchoose
			{
				if(tt==null)
					tt=parseBits(ctx,"CCCCCr");
				script.setFlag("SPAWN");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String var=varify(ctx,tt[2]);
				final String choices=varify(ctx,tt[3]);
				final String defaultVal=varify(ctx,tt[4]);
				final String promptStr=varify(ctx,tt[5]);
				if((newTarget!=null)&&(newTarget instanceof MOB)&&(promptSession((MOB)newTarget)!=null))
				{
					try
					{
						final String value=promptSession((MOB)newTarget).choose(promptFor((MOB)newTarget,promptStr),choices,defaultVal,60000);
						if(CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTVARS))
							Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": SETVAR: "+newTarget.Name()+"("+var+")="+value+"<");
						setVar(newTarget.Name(),var,value);
					}
					catch(final Exception e)
					{
						return "";
					}
				}
				break;
			}
			case 16: // mpset
			{
				if(tt==null)
					tt=parseBits(ctx,"CCcr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				final String arg2=tt[2].toUpperCase().trim();
				String arg3=varify(ctx,tt[3]);
				if(newTarget!=null)
				{
					if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster()))
						Log.sysOut("Scripting",newTarget.Name()+" has "+arg2+" MPSETTED to "+arg3);
					boolean found=false;
					for(int i=0;i<newTarget.getStatCodes().length;i++)
					{
						if(newTarget.getStatCodes()[i].equals(arg2))
						{
							if(arg3.equals("++"))
								arg3=""+(CMath.s_int(newTarget.getStat(newTarget.getStatCodes()[i]))+1);
							if(arg3.equals("--"))
								arg3=""+(CMath.s_int(newTarget.getStat(newTarget.getStatCodes()[i]))-1);
							newTarget.setStat(arg2,arg3);
							found=true;
							break;
						}
					}
					if((!found)&&(newTarget instanceof MOB))
					{
						final MOB M=(MOB)newTarget;
						for(final int i : CharStats.CODES.ALLCODES())
						{
							if(CharStats.CODES.NAME(i).equals(arg2)||CharStats.CODES.DESC(i).equals(arg2))
							{
								if(arg3.equals("++"))
									arg3=""+(M.baseCharStats().getStat(i)+1);
								if(arg3.equals("--"))
									arg3=""+(M.baseCharStats().getStat(i)-1);
								M.baseCharStats().setStat(i,CMath.s_int(arg3.trim()));
								M.recoverCharStats();
								if(arg2.equalsIgnoreCase("RACE"))
									M.charStats().getMyRace().startRacing(M,false);
								found=true;
								break;
							}
						}
						if(!found)
						{
							final int dex=CMParms.indexOf(M.curState().getStatCodes(), arg2);
							if(dex>=0)
							{
								if(arg3.equals("++"))
									arg3=""+(CMath.s_int(M.curState().getStat(M.curState().getStatCodes()[dex]))+1);
								if(arg3.equals("--"))
									arg3=""+(CMath.s_int(M.curState().getStat(M.curState().getStatCodes()[dex]))-1);
								M.curState().setStat(arg2,arg3);
								found=true;
							}
						}
						if(!found)
						{
							final int dex=CMParms.indexOf(M.basePhyStats().getStatCodes(), arg2);
							if(dex>=0)
							{
								if(arg3.equals("++"))
									arg3=""+(CMath.s_int(M.basePhyStats().getStat(M.basePhyStats().getStatCodes()[dex]))+1);
								if(arg3.equals("--"))
									arg3=""+(CMath.s_int(M.basePhyStats().getStat(M.basePhyStats().getStatCodes()[dex]))-1);
								M.basePhyStats().setStat(arg2,arg3);
								found=true;
							}
						}
						if((!found)&&(M.playerStats()!=null))
						{
							final int dex=CMParms.indexOf(M.playerStats().getStatCodes(), arg2);
							if(dex>=0)
							{
								if(arg3.equals("++"))
									arg3=""+(CMath.s_int(M.playerStats().getStat(M.playerStats().getStatCodes()[dex]))+1);
								if(arg3.equals("--"))
									arg3=""+(CMath.s_int(M.playerStats().getStat(M.playerStats().getStatCodes()[dex]))-1);
								M.playerStats().setStat(arg2,arg3);
								found=true;
							}
						}
						if((!found)&&(arg2.startsWith("BASE")))
						{
							final int dex=CMParms.indexOf(M.baseState().getStatCodes(), arg2.substring(4));
							if(dex>=0)
							{
								if(arg3.equals("++"))
									arg3=""+(CMath.s_int(M.baseState().getStat(M.baseState().getStatCodes()[dex]))+1);
								if(arg3.equals("--"))
									arg3=""+(CMath.s_int(M.baseState().getStat(M.baseState().getStatCodes()[dex]))-1);
								M.baseState().setStat(arg2.substring(4),arg3);
								found=true;
							}
						}
						if((!found)&&(arg2.startsWith("MAX")))
						{
							final int dex=CMParms.indexOf(M.curState().getStatCodes(), arg2.substring(3));
							if(dex>=0)
							{
								if(arg3.equals("++"))
									arg3=""+(CMath.s_int(M.maxState().getStat(M.maxState().getStatCodes()[dex]))+1);
								if(arg3.equals("--"))
									arg3=""+(CMath.s_int(M.maxState().getStat(M.maxState().getStatCodes()[dex]))-1);
								M.maxState().setStat(arg2.substring(3),arg3);
								found=true;
							}
						}
						if((!found)&&(arg2.equals("STINK")))
						{
							found=true;
							if(M.playerStats()!=null)
								M.playerStats().setHygiene(Math.round(CMath.s_pct(arg3)*PlayerStats.HYGIENE_DELIMIT));
						}

						if((!found)
						&&(CMath.s_valueOf(GenericBuilder.GenMOBBonusFakeStats.class,arg2)!=null))
						{
							found=true;
							CMLib.coffeeMaker().setAnyGenStat(M, arg2, arg3);
						}
					}

					if((!found)
					&&(newTarget instanceof Item))
					{
						if((!found)
						&&(CMath.s_valueOf(GenericBuilder.GenItemBonusFakeStats.class,arg2)!=null))
						{
							found=true;
							CMLib.coffeeMaker().setAnyGenStat(newTarget, arg2, arg3);
						}
					}

					if((!found)
					&&(CMath.s_valueOf(GenericBuilder.GenPhysBonusFakeStats.class,arg2)!=null))
					{
						found=true;
						CMLib.coffeeMaker().setAnyGenStat(newTarget, arg2, arg3);
					}

					if(!found)
					{
						logError(ctx,"MPSET","Syntax","Unknown stat: "+arg2+" for "+newTarget.Name());
						break;
					}
					if(newTarget instanceof MOB)
						((MOB)newTarget).recoverCharStats();
					newTarget.recoverPhyStats();
					if(newTarget instanceof MOB)
					{
						((MOB)newTarget).recoverMaxState();
						if(arg2.equals("RACE"))
						{
							final Race R = ((MOB)newTarget).baseCharStats().getMyRace();
							R.startRacing((MOB)newTarget, false);
						}
						else
						if(arg2.equals("LEVEL"))
						{
							CMLib.leveler().fillOutMOB(((MOB)newTarget),newTarget.basePhyStats().level());
							((MOB)newTarget).recoverMaxState();
							((MOB)newTarget).recoverCharStats();
							newTarget.recoverPhyStats();
							((MOB)newTarget).resetToMaxState();
						}
					}
				}
				break;
			}
			case 63: // mpargset
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final String arg1=tt[1];
				final String arg2=tt[2];
				if((arg1.length()!=2)||(!arg1.startsWith("$")))
				{
					logError(ctx,"MPARGSET","Syntax","Mangled argument var: "+arg1+" for "+ctx.scripted.Name());
					break;
				}

				Object O=null;
				if(arg2.startsWith("\"") && arg2.endsWith("\"")&&(arg2.length()>1))
					O=varify(ctx,arg2.substring(1,arg2.length()-1));
				else
				{
					O=getArgumentMOB(arg2,ctx);
					if(O==null)
						O=getArgumentItem(arg2,ctx);
					if((O==null)
					&&((arg2.length()!=2)
						||(arg2.charAt(1)=='g')
						||(!arg2.startsWith("$"))
						||((!Character.isDigit(arg2.charAt(1)))
							&&(!Character.isLetter(arg2.charAt(1))))))
						O=varify(ctx,arg2);
				}
				final char c=arg1.charAt(1);
				if(Character.isDigit(c))
				{
					if((O instanceof String)&&(((String)O).equalsIgnoreCase("null")))
						O=null;
					ctx.tmp[CMath.s_int(Character.toString(c))]=O;
				}
				else
				{
					switch(arg1.charAt(1))
					{
					case 'N':
					case 'n':
						if (O instanceof MOB)
							ctx.source = (MOB) O;
						break;
					case 'B':
					case 'b':
						if (O instanceof Environmental)
							lastLoaded = (Environmental) O;
						break;
					case 'I':
					case 'i':
						if (O instanceof PhysicalAgent)
							ctx.scripted = (PhysicalAgent) O;
						if (O instanceof MOB)
							ctx.monster = (MOB) O;
						break;
					case 'Q':
					case 'q':
						break;
					case 'T':
					case 't':
						if (O instanceof Environmental)
							ctx.target = (Environmental) O;
						break;
					case 'O':
					case 'o':
						if (O instanceof Item)
							ctx.primaryItem = (Item) O;
						break;
					case 'P':
					case 'p':
						if (O instanceof Item)
							ctx.secondaryItem = (Item) O;
						break;
					case 'd':
					case 'D':
						if (O instanceof Room)
							lastKnownLocation = (Room) O;
						break;
					case 'g':
					case 'G':
						if (O instanceof String)
							ctx.msg = (String) O;
						else
						if((O instanceof Room)
						&&((((Room)O).roomID().length()>0)
							|| (!"".equals(CMLib.map().getExtendedRoomID((Room)O)))))
							ctx.msg = CMLib.map().getExtendedRoomID((Room)O);
						else
						if(O instanceof CMObject)
							ctx.msg=((CMObject)O).name();
						else
							ctx.msg=""+O;
						break;
					default:
						logError(ctx, "MPARGSET", "Syntax", "Invalid argument var: " + arg1 + " for " + ctx.scripted.Name());
						break;
					}
				}
				break;
			}
			case 35: // mpgset
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				final String arg2=tt[2].toUpperCase().trim();
				String arg3=varify(ctx,tt[3]);
				if(newTarget!=null)
				{
					boolean found=false;
					for(int i=0;i<newTarget.getStatCodes().length;i++)
					{
						if(newTarget.getStatCodes()[i].equals(arg2))
						{
							if(arg3.equals("++"))
								arg3=""+(CMath.s_int(newTarget.getStat(newTarget.getStatCodes()[i]))+1);
							if(arg3.equals("--"))
								arg3=""+(CMath.s_int(newTarget.getStat(newTarget.getStatCodes()[i]))-1);
							newTarget.setStat(newTarget.getStatCodes()[i],arg3);
							found=true;
							break;
						}
					}
					if(!found)
					{
						if(newTarget instanceof MOB)
						{
							final GenericBuilder.GenMOBCode element = (GenericBuilder.GenMOBCode)CMath.s_valueOf(GenericBuilder.GenMOBCode.class,arg2);
							if(element != null)
							{
								if(arg3.equals("++"))
									arg3=""+(CMath.s_int(CMLib.coffeeMaker().getGenMobStat((MOB)newTarget,element.name()))+1);
								if(arg3.equals("--"))
									arg3=""+(CMath.s_int(CMLib.coffeeMaker().getGenMobStat((MOB)newTarget,element.name()))-1);
								CMLib.coffeeMaker().setGenMobStat((MOB)newTarget,element.name(),arg3);
								found=true;
							}
							if(!found)
							{
								final MOB M=(MOB)newTarget;
								for(final int i : CharStats.CODES.ALLCODES())
								{
									if(CharStats.CODES.NAME(i).equals(arg2)||CharStats.CODES.DESC(i).equals(arg2))
									{
										if(arg3.equals("++"))
											arg3=""+(M.baseCharStats().getStat(i)+1);
										if(arg3.equals("--"))
											arg3=""+(M.baseCharStats().getStat(i)-1);
										if((arg3.length()==1)&&(Character.isLetter(arg3.charAt(0))))
											M.baseCharStats().setStat(i,arg3.charAt(0));
										else
											M.baseCharStats().setStat(i,CMath.s_int(arg3.trim()));
										M.recoverCharStats();
										if(arg2.equals("RACE"))
											M.charStats().getMyRace().startRacing(M,false);
										found=true;
										break;
									}
								}
								if(!found)
								{
									final int dex=CMParms.indexOf(M.curState().getStatCodes(), arg2);
									if(dex>=0)
									{
										if(arg3.equals("++"))
											arg3=""+(CMath.s_int(M.curState().getStat(M.curState().getStatCodes()[dex]))+1);
										if(arg3.equals("--"))
											arg3=""+(CMath.s_int(M.curState().getStat(M.curState().getStatCodes()[dex]))-1);
										M.curState().setStat(arg2,arg3);
										found=true;
									}
								}
								if(!found)
								{
									final int dex=CMParms.indexOf(M.basePhyStats().getStatCodes(), arg2);
									if(dex>=0)
									{
										if(arg3.equals("++"))
											arg3=""+(CMath.s_int(M.basePhyStats().getStat(M.basePhyStats().getStatCodes()[dex]))+1);
										if(arg3.equals("--"))
											arg3=""+(CMath.s_int(M.basePhyStats().getStat(M.basePhyStats().getStatCodes()[dex]))-1);
										M.basePhyStats().setStat(arg2,arg3);
										found=true;
									}
								}
								if((!found)&&(M.playerStats()!=null))
								{
									final int dex=CMParms.indexOf(M.playerStats().getStatCodes(), arg2);
									if(dex>=0)
									{
										if(arg3.equals("++"))
											arg3=""+(CMath.s_int(M.playerStats().getStat(M.playerStats().getStatCodes()[dex]))+1);
										if(arg3.equals("--"))
											arg3=""+(CMath.s_int(M.playerStats().getStat(M.playerStats().getStatCodes()[dex]))-1);
										M.playerStats().setStat(arg2,arg3);
										found=true;
									}
								}
								if((!found)&&(arg2.startsWith("BASE")))
								{
									final String arg4=arg2.substring(4);
									final int dex=CMParms.indexOf(M.baseState().getStatCodes(), arg4);
									if(dex>=0)
									{
										if(arg3.equals("++"))
											arg3=""+(CMath.s_int(M.baseState().getStat(M.baseState().getStatCodes()[dex]))+1);
										if(arg3.equals("--"))
											arg3=""+(CMath.s_int(M.baseState().getStat(M.baseState().getStatCodes()[dex]))-1);
										M.baseState().setStat(arg4,arg3);
										found=true;
									}
								}
								if((!found)&&(arg2.startsWith("MAX")))
								{
									final String arg4=arg2.substring(3);
									final int dex=CMParms.indexOf(M.maxState().getStatCodes(), arg4);
									if(dex>=0)
									{
										if(arg3.equals("++"))
											arg3=""+(CMath.s_int(M.baseState().getStat(M.maxState().getStatCodes()[dex]))+1);
										if(arg3.equals("--"))
											arg3=""+(CMath.s_int(M.baseState().getStat(M.maxState().getStatCodes()[dex]))-1);
										M.maxState().setStat(arg4,arg3);
										found=true;
									}
								}
								if((!found)&&(arg2.equals("STINK")))
								{
									found=true;
									if(M.playerStats()!=null)
										M.playerStats().setHygiene(Math.round(CMath.s_pct(arg3)*PlayerStats.HYGIENE_DELIMIT));
								}
							}
						}
					}
					else
					if(newTarget instanceof Item)
					{

						final GenericBuilder.GenItemCode element = (GenericBuilder.GenItemCode)CMath.s_valueOf(GenericBuilder.GenItemCode.class,arg2);
						if(element != null)
						{
							if(arg3.equals("++"))
								arg3=""+(CMath.s_int(CMLib.coffeeMaker().getGenItemStat((Item)newTarget,element.name()))+1);
							if(arg3.equals("--"))
								arg3=""+(CMath.s_int(CMLib.coffeeMaker().getGenItemStat((Item)newTarget,element.name()))-1);
							CMLib.coffeeMaker().setGenItemStat((Item)newTarget,element.name(),arg3);
							found=true;
						}
					}
					if((!found)
					&&(newTarget instanceof Physical))
					{
						if(CMLib.coffeeMaker().isAnyGenStat(newTarget, arg2))
						{
							if(arg3.equals("++"))
								arg3=""+(CMath.s_int(CMLib.coffeeMaker().getAnyGenStat(newTarget,arg2))+1);
							if(arg3.equals("--"))
								arg3=""+(CMath.s_int(CMLib.coffeeMaker().getAnyGenStat(newTarget,arg2))-1);
							CMLib.coffeeMaker().setAnyGenStat(newTarget, arg2, arg3);
							found=true;
						}
					}
					if(!found)
					{
						logError(ctx,"MPGSET","Syntax","Unknown stat: "+arg2+" for "+newTarget.Name());
						break;
					}
					else
					if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster()))
						Log.sysOut("Scripting",newTarget.Name()+" has "+arg2+" MPGSETTED to "+arg3);
					if(newTarget instanceof MOB)
						((MOB)newTarget).recoverCharStats();
					newTarget.recoverPhyStats();
					if(newTarget instanceof MOB)
					{
						((MOB)newTarget).recoverMaxState();
						if(arg2.equals("LEVEL"))
						{
							CMLib.leveler().fillOutMOB(((MOB)newTarget),newTarget.basePhyStats().level());
							((MOB)newTarget).recoverMaxState();
							((MOB)newTarget).recoverCharStats();
							newTarget.recoverPhyStats();
							((MOB)newTarget).resetToMaxState();
						}
					}
				}
				break;
			}
			case 11: // mpexp
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				final String amtStr=varify(ctx,tt[2]).trim();
				int t=CMath.s_int(amtStr);
				if(newTarget instanceof MOB)
				{
					final MOB targM = (MOB)newTarget;
					if((amtStr.endsWith("%"))
					&&(targM.getExpNeededLevel()<Integer.MAX_VALUE))
					{
						final int baseLevel=newTarget.basePhyStats().level();
						final int lastLevelExpNeeded=(baseLevel<=1)?0:CMLib.leveler().getLevelExperience(targM, baseLevel-1);
						final int thisLevelExpNeeded=CMLib.leveler().getLevelExperience(targM, baseLevel);
						final double pct = CMath.s_pct(amtStr);
						t=(int)Math.round(CMath.mul(thisLevelExpNeeded-lastLevelExpNeeded,pct));
						if((t>thisLevelExpNeeded)&&(pct<1))
							t=thisLevelExpNeeded;
					}
					if(t!=0)
						CMLib.leveler().postExperience((MOB)newTarget,"SCRIPT:"+defaultQuestName,null,null,t, false);
				}
				break;
			}
			case 86: // mprpexp
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				final String amtStr=varify(ctx,tt[2]).trim();
				int t=CMath.s_int(amtStr);
				if((newTarget!=null)&&(newTarget instanceof MOB))
				{
					if((amtStr.endsWith("%"))
					&&(((MOB)newTarget).getExpNeededLevel()<Integer.MAX_VALUE))
					{
						final int baseLevel=newTarget.basePhyStats().level();
						final int lastLevelExpNeeded=(baseLevel<=1)?0:CMLib.leveler().getLevelExperience((MOB)newTarget, baseLevel-1);
						final int thisLevelExpNeeded=CMLib.leveler().getLevelExperience((MOB)newTarget, baseLevel);
						t=(int)Math.round(CMath.mul(thisLevelExpNeeded-lastLevelExpNeeded,
											CMath.div(CMath.s_int(amtStr.substring(0,amtStr.length()-1)),100.0)));
					}
					if(t!=0)
						CMLib.leveler().postRPExperience((MOB)newTarget,"SCRIPT:"+defaultQuestName,null,null,t, false);
				}
				break;
			}
			case 77: // mpmoney
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				Environmental newTarget=getArgumentMOB(tt[1],ctx);
				if(newTarget==null)
					newTarget=getArgumentItem(tt[1],ctx);
				String amtStr=tt[2];
				amtStr=varify(ctx,amtStr).trim();
				final boolean plus=!amtStr.startsWith("-");
				if(amtStr.startsWith("+")||amtStr.startsWith("-"))
					amtStr=amtStr.substring(1).trim();
				final String currency = CMLib.english().parseNumPossibleGoldCurrency(ctx.source, amtStr);
				final long amt = CMLib.english().parseNumPossibleGold(ctx.source, amtStr);
				final double denomination = CMLib.english().parseNumPossibleGoldDenomination(ctx.source, currency, amtStr);
				Container container = null;
				if(newTarget instanceof Item)
				{
					container = (newTarget instanceof Container)?(Container)newTarget:null;
					newTarget = ((Item)newTarget).owner();
				}
				if(newTarget instanceof MOB)
				{
					if(plus)
						CMLib.beanCounter().giveSomeoneMoney((MOB)newTarget, currency, amt * denomination);
					else
						CMLib.beanCounter().subtractMoney((MOB)newTarget, currency, amt * denomination);
				}
				else
				{
					if(!(newTarget instanceof Room))
						newTarget=lastKnownLocation;
					if(plus)
						CMLib.beanCounter().dropMoney((Room)newTarget, container, currency, amt * denomination);
					else
						CMLib.beanCounter().removeMoney((Room)newTarget, container, currency, amt * denomination);
				}
				break;
			}
			case 59: // mpquestpoints
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String val=varify(ctx,tt[2]).trim();
				if(newTarget instanceof MOB)
				{
					if(CMath.isNumber(val))
					{
						final int ival=CMath.s_int(val);
						final int aval=ival-((MOB)newTarget).getQuestPoint();
						((MOB)newTarget).setQuestPoint(CMath.s_int(val));
						if(aval>0)
							CMLib.players().bumpPrideStat((MOB)newTarget,PrideStat.QUESTPOINTS_EARNED, aval);
					}
					else
					if(val.startsWith("++")&&(CMath.isNumber(val.substring(2).trim())))
					{
						((MOB)newTarget).setQuestPoint(((MOB)newTarget).getQuestPoint()+CMath.s_int(val.substring(2).trim()));
						CMLib.players().bumpPrideStat((MOB)newTarget,PrideStat.QUESTPOINTS_EARNED, CMath.s_int(val.substring(2).trim()));
					}
					else
					if(val.startsWith("--")&&(CMath.isNumber(val.substring(2).trim())))
						((MOB)newTarget).setQuestPoint(((MOB)newTarget).getQuestPoint()-CMath.s_int(val.substring(2).trim()));
					else
						logError(ctx,"QUESTPOINTS","Syntax","Bad syntax "+val+" for "+ctx.scripted.Name());
				}
				break;
			}
			case 65: // MPQSET
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final String qstr=varify(ctx,tt[1]);
				final String var=varify(ctx,tt[2]);
				final PhysicalAgent obj=getArgumentItem(tt[3],ctx);
				String val=varify(ctx,tt[3]);
				final Quest Q=getQuest(qstr);
				if(Q==null)
					logError(ctx,"MPQSET","Syntax","Unknown quest "+qstr+" for "+ctx.scripted.Name());
				else
				if(var.equalsIgnoreCase("QUESTOBJ"))
				{
					if(obj==null)
						logError(ctx,"MPQSET","Syntax","Unknown object "+tt[3]+" for "+ctx.scripted.Name());
					else
					{
						obj.basePhyStats().setDisposition(obj.basePhyStats().disposition()|PhyStats.IS_UNSAVABLE);
						obj.recoverPhyStats();
						Q.runtimeRegisterObject(obj);
					}
				}
				else
				if(var.equalsIgnoreCase("STATISTICS")&&(val.equalsIgnoreCase("ACCEPTED")))
					CMLib.coffeeTables().bump(Q,CoffeeTableRow.STAT_QUESTACCEPTED);
				else
				if(var.equalsIgnoreCase("STATISTICS")&&(val.equalsIgnoreCase("SUCCESS")||val.equalsIgnoreCase("WON")))
					CMLib.coffeeTables().bump(Q,CoffeeTableRow.STAT_QUESTSUCCESS);
				else
				if(var.equalsIgnoreCase("STATISTICS")&&(val.equalsIgnoreCase("FAILED")))
					CMLib.coffeeTables().bump(Q,CoffeeTableRow.STAT_QUESTFAILED);
				else
				{
					if(val.equals("++"))
						val=""+(CMath.s_int(Q.getStat(var))+1);
					if(val.equals("--"))
						val=""+(CMath.s_int(Q.getStat(var))-1);
					Q.setStat(var,val);
				}
				break;
			}
			case 66: // MPLOG
			{
				if(tt==null)
					tt=parseBits(ctx,"CCcr");
				final String type=tt[1];
				final String head=varify(ctx,tt[2]);
				final String val=varify(ctx,tt[3]);
				if(type.startsWith("E"))
					Log.errOut("Script","["+head+"] "+val);
				else
				if(type.startsWith("I")||type.startsWith("S"))
					Log.infoOut("Script","["+head+"] "+val);
				else
				if(type.startsWith("D"))
					Log.debugOut("Script","["+head+"] "+val);
				else
					logError(ctx,"MPLOG","Syntax","Unknown log type "+type+" for "+ctx.scripted.Name());
				break;
			}
			case 67: // MPCHANNEL
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				String channel=varify(ctx,tt[1]);
				final boolean sysmsg=channel.startsWith("!");
				if(sysmsg)
					channel=channel.substring(1);
				final String val=varify(ctx,tt[2]);
				if(CMLib.channels().getChannelCodeNumber(channel)<0)
					logError(ctx,"MPCHANNEL","Syntax","Unknown channel "+channel+" for "+ctx.scripted.Name());
				else
					CMLib.commands().postChannel(ctx.monster,channel,val,sysmsg);
				break;
			}
			case 68: // MPUNLOADSCRIPT
			{
				if(tt==null)
					tt=parseBits(ctx,"Cc");
				String scriptname=varify(ctx,tt[1]);
				if(!new CMFile(Resources.makeFileResourceName(scriptname),null,CMFile.FLAG_FORCEALLOW).exists())
					logError(ctx,"MPUNLOADSCRIPT","Runtime","File does not exist: "+Resources.makeFileResourceName(scriptname));
				else
				{
					final ArrayList<String> delThese=new ArrayList<String>();
					scriptname=scriptname.toUpperCase().trim();
					final String parmname=scriptname;
					for(final Iterator<String> k = Resources.findResourceKeys(parmname);k.hasNext();)
					{
						final String key=k.next();
						if(key.startsWith("PARSEDPRG: ")&&(key.toUpperCase().endsWith(parmname)))
						{
							delThese.add(key);
						}
					}
					for(int i=0;i<delThese.size();i++)
						Resources.removeResource(delThese.get(i));
				}

				break;
			}
			case 60: // MPTRAINS
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String val=varify(ctx,tt[2]).trim();
				if(newTarget instanceof MOB)
				{
					if(CMath.isNumber(val))
						((MOB)newTarget).setTrains(CMath.s_int(val));
					else
					if(val.startsWith("++")&&(CMath.isNumber(val.substring(2).trim())))
						((MOB)newTarget).setTrains(((MOB)newTarget).getTrains()+CMath.s_int(val.substring(2).trim()));
					else
					if(val.startsWith("--")&&(CMath.isNumber(val.substring(2).trim())))
						((MOB)newTarget).setTrains(((MOB)newTarget).getTrains()-CMath.s_int(val.substring(2).trim()));
					else
						logError(ctx,"TRAINS","Syntax","Bad syntax "+val+" for "+ctx.scripted.Name());
				}
				break;
			}
			case 61: // mppracs
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String val=varify(ctx,tt[2]).trim();
				if(newTarget instanceof MOB)
				{
					if(CMath.isNumber(val))
						((MOB)newTarget).setPractices(CMath.s_int(val));
					else
					if(val.startsWith("++")&&(CMath.isNumber(val.substring(2).trim())))
						((MOB)newTarget).setPractices(((MOB)newTarget).getPractices()+CMath.s_int(val.substring(2).trim()));
					else
					if(val.startsWith("--")&&(CMath.isNumber(val.substring(2).trim())))
						((MOB)newTarget).setPractices(((MOB)newTarget).getPractices()-CMath.s_int(val.substring(2).trim()));
					else
						logError(ctx,"PRACS","Syntax","Bad syntax "+val+" for "+ctx.scripted.Name());
				}
				break;
			}
			case 5: // mpmload
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String name=varify(ctx,tt[1]);
				final ArrayList<Environmental> Ms=new ArrayList<Environmental>();
				MOB m=CMClass.getMOB(name);
				if(m!=null)
					Ms.add(m);
				if(lastKnownLocation!=null)
				{
					if(Ms.size()==0)
						findSomethingCalledThis(name,ctx,ctx.monster,lastKnownLocation,Ms,true);
					for(int i=0;i<Ms.size();i++)
					{
						if(Ms.get(i) instanceof MOB)
						{
							final MOB baseM = (MOB)Ms.get(i);
							m=(MOB)loaderCopy(baseM); // SC3: a catalog copy is counted and capped
							if(m==null)
								continue;
							if((baseM.location() != null)
							&&(baseM.location().isInhabitant(baseM)))
								m.setStartRoom(null); // the copy is not a resident there
							m.setMiscText(m.text());
							m.recoverPhyStats();
							m.recoverCharStats();
							m.resetToMaxState();
							m.bringToLife(lastKnownLocation,true);
							lastLoaded=m;
						}
					}
				}
				break;
			}
			case 6: // mpoload
			{
				//if not mob
				Physical addHere;
				if(ctx.scripted instanceof MOB)
					addHere=ctx.monster;
				else
				if(ctx.scripted instanceof Item)
					addHere=((Item)ctx.scripted).owner();
				else
				if(ctx.scripted instanceof Room)
					addHere=ctx.scripted;
				else
					addHere=lastKnownLocation;
				if(addHere!=null)
				{
					if(tt==null)
						tt=parseBits(ctx,"Cr");
					this.lastLoaded = null;
					String name = null;
					Container container=null;
					final int earlyContIndeX = tt[1].toUpperCase().lastIndexOf(" INTO ");
					if((earlyContIndeX>0)&&(tt[1].substring(earlyContIndeX+6).indexOf("$")>=0))
					{
						final Physical P= this.getArgumentItem(tt[1].substring(earlyContIndeX+6).trim(), ctx);
						if(P instanceof Container)
						{
							name = varify(ctx,tt[1].substring(0,earlyContIndeX).trim());
							container=(Container)P;
						}
					}
					if (name == null)
						name=varify(ctx,tt[1]);
					final int containerIndex=name.toUpperCase().indexOf(" INTO ");
					if((containerIndex>=0)&&(container == null))
					{
						final ArrayList<Environmental> containers=new ArrayList<Environmental>();
						findSomethingCalledThis(name.substring(containerIndex+6).trim(),ctx,ctx.monster,lastKnownLocation,containers,false);
						for(int c=0;c<containers.size();c++)
						{
							if((containers.get(c) instanceof Container)
							&&(((Container)containers.get(c)).capacity()>0))
							{
								container=(Container)containers.get(c);
								name=name.substring(0,containerIndex).trim();
								break;
							}
						}
					}
					final long coins=CMLib.english().parseNumPossibleGold(null,name);
					if(coins>0)
					{
						final String currency=CMLib.english().parseNumPossibleGoldCurrency(ctx.scripted,name);
						final double denom=CMLib.english().parseNumPossibleGoldDenomination(ctx.scripted,currency,name);
						final Coins C=CMLib.beanCounter().makeCurrency(currency,denom,coins);
						lastLoaded=(Item)C.copyOf();
						if(addHere instanceof MOB)
							((MOB)addHere).addItem(C);
						else
						if(addHere instanceof Room)
							((Room)addHere).addItem(C, Expire.Monster_EQ);
						C.putCoinsBack();
					}
					else
					if(lastKnownLocation!=null)
					{
						final ArrayList<Environmental> Is=new ArrayList<Environmental>();
						Item m=CMClass.getItem(name);
						if(m!=null)
							Is.add(m);
						else
							findSomethingCalledThis(name,ctx,ctx.monster,lastKnownLocation,Is,false);
						for(int i=0;i<Is.size();i++)
						{
							if(Is.get(i) instanceof Item)
							{
								m=(Item)Is.get(i);
								if((m!=null)
								&&(!(m instanceof ArchonOnly)))
								{
									m=(Item)loaderCopy(m); // SC3
									if(m==null)
										continue;
									m.recoverPhyStats();
									m.setContainer(container);
									if(container instanceof MOB)
										container.owner().addItem(m);
									else
									if(container instanceof Room)
										container.owner().addItem(m,ItemPossessor.Expire.Player_Drop);
									else
									if(addHere instanceof MOB)
										((MOB)addHere).addItem(m);
									else
									if(addHere instanceof Room)
										((Room)addHere).addItem(m, Expire.Monster_EQ);
									if(CMLib.threads().isSuspended(m, -1))
										CMLib.threads().resumeTicking(m, -1);
									lastLoaded=m;
								}
							}
						}
						if(addHere instanceof MOB)
						{
							((MOB)addHere).recoverCharStats();
							((MOB)addHere).recoverMaxState();
						}
						addHere.recoverPhyStats();
						lastKnownLocation.recoverRoomStats();
					}
				}
				break;
			}
			case 41: // mpoloadroom
			{
				final Room putRoom = lastKnownLocation;
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				String name = null;
				Container container=null;
				final int earlyContIndeX = tt[1].toUpperCase().lastIndexOf(" INTO ");
				if((earlyContIndeX>0)&&(tt[1].substring(earlyContIndeX+6).indexOf("$")>=0))
				{
					final Physical P= this.getArgumentItem(tt[1].substring(earlyContIndeX+6).trim(), ctx);
					if(P instanceof Container)
					{
						name = varify(ctx,tt[1].substring(0,earlyContIndeX).trim());
						container=(Container)P;
					}
				}
				if(name == null)
					name=varify(ctx,tt[1]);
				if(putRoom!=null)
				{
					final ArrayList<Environmental> Is=new ArrayList<Environmental>();
					final int containerIndex=name.toUpperCase().indexOf(" INTO ");
					if((containerIndex>=0)&&(container == null))
					{
						final ArrayList<Environmental> containers=new ArrayList<Environmental>();
						findSomethingCalledThis(name.substring(containerIndex+6).trim(),ctx,null,putRoom,containers,false);
						for(int c=0;c<containers.size();c++)
						{
							if(containers.get(c) instanceof Container)
							{
								final Container C = (Container)containers.get(c);
								if((C.owner()==putRoom)
								&&(C.capacity()>0))
								{
									container=(Container)containers.get(c);
									name=name.substring(0,containerIndex).trim();
									break;
								}
							}
						}
					}
					final long coins=CMLib.english().parseNumPossibleGold(null,name);
					if(coins>0)
					{
						final String currency=CMLib.english().parseNumPossibleGoldCurrency(ctx.monster,name);
						final double denom=CMLib.english().parseNumPossibleGoldDenomination(ctx.monster,currency,name);
						final Coins C=CMLib.beanCounter().makeCurrency(currency,denom,coins);
						Is.add(C);
					}
					else
					{
						final Item I=CMClass.getItem(name);
						if(I!=null)
						{
							if(I==container)
								container=null;
							Is.add(I);
						}
						else
						{
							findSomethingCalledThis(name,ctx,ctx.monster,putRoom,Is,false);
							if((container!=null)
							&&(Is.size()==0))
							{
								findSomethingCalledThis(tt[1],ctx,ctx.monster,putRoom,Is,false);
								if(Is.size()>0)
									container=null;
							}
						}
					}
					for(int i=0;i<Is.size();i++)
					{
						if(Is.get(i) instanceof Item)
						{
							Item I=(Item)Is.get(i);
							if((I!=null)
							&&(!(I instanceof ArchonOnly)))
							{
								I=(Item)loaderCopy(I); // SC3
								if(I==null)
									continue;
								I.recoverPhyStats();
								synchronized(putRoom)
								{
									// does this really do anything?  is it optimized away?
								}
								putRoom.addItem(I,ItemPossessor.Expire.Monster_EQ);
								I.setContainer(container);
								if(I instanceof Coins)
									((Coins)I).putCoinsBack();
								if(I instanceof RawMaterial)
									((RawMaterial)I).rebundle();
								if(!putRoom.isContent(I))
									putRoom.addItem(I,ItemPossessor.Expire.Monster_EQ);
								I.setOwner(putRoom);
								if(CMLib.threads().isSuspended(I, -1))
									CMLib.threads().resumeTicking(I, -1);
								lastLoaded=I;
							}
						}
					}
					lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 84: // mpoloadshop
			{
				ShopKeeper addHere = CMLib.coffeeShops().getShopKeeper(ctx.scripted);
				if((addHere == null)&&(ctx.scripted instanceof Item))
					addHere=CMLib.coffeeShops().getShopKeeper(((Item)ctx.scripted).owner());
				if((addHere == null)&&(ctx.scripted instanceof MOB))
					addHere=CMLib.coffeeShops().getShopKeeper(((MOB)ctx.scripted).location());
				if(addHere == null)
					addHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
				if(addHere!=null)
				{
					if(tt==null)
						tt=parseBits(ctx,"Cr");
					String name=varify(ctx,tt[1]);
					if(lastKnownLocation!=null)
					{
						final ArrayList<Environmental> Is=new ArrayList<Environmental>();
						int price=-1;
						if((price = name.indexOf(" PRICE="))>=0)
						{
							final String rest = name.substring(price+7).trim();
							name=name.substring(0,price).trim();
							if(CMath.isInteger(rest))
								price=CMath.s_int(rest);
						}
						Item I=CMClass.getItem(name);
						if(I!=null)
							Is.add(I);
						else
							findSomethingCalledThis(name,ctx,ctx.monster,lastKnownLocation,Is,false);
						for(int i=0;i<Is.size();i++)
						{
							if(Is.get(i) instanceof Item)
							{
								I=(Item)Is.get(i);
								if((I!=null)
								&&(!(I instanceof ArchonOnly)))
								{
									I=(Item)loaderCopy(I); // SC3: the cap holds for stock too
									if(I==null)
										continue;
									I.recoverPhyStats();
									final CoffeeShop shop = addHere.getShop();
									if(shop != null)
									{
										final Environmental E=shop.addStoreInventory(I,1,price);
										if(E!=null)
											setShopPrice(addHere, E, ctx.tmp);
									}
									I.destroy();
								}
							}
						}
						lastKnownLocation.recoverRoomStats();
					}
				}
				break;
			}
			case 85: // mpmloadshop
			{
				ShopKeeper addHere = CMLib.coffeeShops().getShopKeeper(ctx.scripted);
				if((addHere == null)&&(ctx.scripted instanceof Item))
					addHere=CMLib.coffeeShops().getShopKeeper(((Item)ctx.scripted).owner());
				if((addHere == null)&&(ctx.scripted instanceof MOB))
					addHere=CMLib.coffeeShops().getShopKeeper(((MOB)ctx.scripted).location());
				if(addHere == null)
					addHere=CMLib.coffeeShops().getShopKeeper(lastKnownLocation);
				if(addHere!=null)
				{
					if(tt==null)
						tt=parseBits(ctx,"Cr");
					this.lastLoaded = null;
					String name=varify(ctx,tt[1]);
					int price=-1;
					if((price = name.indexOf(" PRICE="))>=0)
					{
						final String rest = name.substring(price+7).trim();
						name=name.substring(0,price).trim();
						if(CMath.isInteger(rest))
							price=CMath.s_int(rest);
					}
					final ArrayList<Environmental> Ms=new ArrayList<Environmental>();
					MOB m=CMClass.getMOB(name);
					if(m!=null)
						Ms.add(m);
					if(lastKnownLocation!=null)
					{
						if(Ms.size()==0)
							findSomethingCalledThis(name,ctx,ctx.monster,lastKnownLocation,Ms,true);
						for(int i=0;i<Ms.size();i++)
						{
							if(Ms.get(i) instanceof MOB)
							{
								m=(MOB)loaderCopy(Ms.get(i)); // SC3
								if(m==null)
									continue;
								m.text();
								m.recoverPhyStats();
								m.recoverCharStats();
								m.resetToMaxState();
								final CoffeeShop shop = addHere.getShop();
								if(shop != null)
								{
									final Environmental E=shop.addStoreInventory(m,1,price);
									if(E!=null)
										setShopPrice(addHere, E, ctx.tmp);
								}
								m.destroy();
							}
						}
					}
				}
				break;
			}
			case 42: // mphide
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				if(newTarget!=null)
				{
					newTarget.basePhyStats().setDisposition(newTarget.basePhyStats().disposition()|PhyStats.IS_NOT_SEEN);
					newTarget.recoverPhyStats();
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 58: // mpreset
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String arg=varify(ctx,tt[1]);
				if(arg.equalsIgnoreCase("area"))
				{
					if(lastKnownLocation!=null)
						CMLib.map().resetArea(lastKnownLocation.getArea());
				}
				else
				if(arg.equalsIgnoreCase("room"))
				{
					if(lastKnownLocation!=null)
						safeResetRoom(lastKnownLocation);
				}
				else
				if(arg.trim().length()>0)
				{
					final Room R=CMLib.map().getRoom(arg);
					if(R!=null)
						safeResetRoom(R);
					else
					{
						final Area A=CMLib.map().findArea(arg);
						if(A!=null)
							safeResetArea(A);
						else
						{
							final Physical newTarget=getArgumentItem(arg,ctx);
							if(newTarget == null)
								logError(ctx,"MPRESET","Syntax","Unknown location or item: "+arg+" for "+ctx.scripted.Name());
							else
							if(newTarget instanceof Item)
							{
								final Item I=(Item)newTarget;
								I.setContainer(null);
								if(I.subjectToWearAndTear())
									I.setUsesRemaining(100);
								I.recoverPhyStats();
							}
							else
							if(newTarget instanceof MOB)
							{
								final MOB M=(MOB)newTarget;
								M.resetToMaxState();
								M.recoverMaxState();
								M.recoverCharStats();
								M.recoverPhyStats();
							}
						}
					}
				}
				break;
			}
			case 71: // mprejuv
			{
				if(tt==null)
				{
					final String rest=CMParms.getPastBitClean(s,1);
					if(rest.equals("item")||rest.equals("items"))
						tt=parseBits(ctx,"Ccr");
					else
					if(rest.equals("mob")||rest.equals("mobs"))
						tt=parseBits(ctx,"Ccr");
					else
						tt=parseBits(ctx,"Cr");
				}
				final String next=varify(ctx,tt[1]);
				String rest="";
				if(tt.length>2)
					rest=varify(ctx,tt[2]);
				int tickID=-1;
				if(rest.equalsIgnoreCase("item")||rest.equalsIgnoreCase("items"))
					tickID=Tickable.TICKID_ROOM_ITEM_REJUV;
				else
				if(rest.equalsIgnoreCase("mob")||rest.equalsIgnoreCase("mobs"))
					tickID=Tickable.TICKID_MOB;
				if(next.equalsIgnoreCase("area"))
				{
					if(lastKnownLocation!=null)
						for(final Enumeration<Room> e=lastKnownLocation.getArea().getProperMap();e.hasMoreElements();)
							CMLib.threads().rejuv(e.nextElement(),tickID);
				}
				else
				if(next.equalsIgnoreCase("room"))
				{
					if(lastKnownLocation!=null)
						CMLib.threads().rejuv(lastKnownLocation,tickID);
				}
				else
				if(next.trim().length()>0)
				{
					final Room R=CMLib.map().getRoom(next);
					if(R!=null)
						CMLib.threads().rejuv(R,tickID);
					else
					{
						final Area A=CMLib.map().findArea(next);
						if(A!=null)
						{
							for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
								CMLib.threads().rejuv(e.nextElement(),tickID);
						}
						else
							logError(ctx,"MPREJUV","Syntax","Unknown location: "+next+" for "+ctx.scripted.Name());
					}
				}
				break;
			}
			case 56: // mpstop
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final List<MOB> V=new ArrayList<MOB>();
				final String who=tt[1];
				if(who.equalsIgnoreCase("all"))
				{
					for(int i=0;i<lastKnownLocation.numInhabitants();i++)
						V.add(lastKnownLocation.fetchInhabitant(i));
				}
				else
				{
					final Environmental newTarget=getArgumentItem(who,ctx);
					if(newTarget instanceof MOB)
						V.add((MOB)newTarget);
				}
				for(int v=0;v<V.size();v++)
				{
					final Environmental newTarget=V.get(v);
					if(newTarget instanceof MOB)
					{
						final MOB mob=(MOB)newTarget;
						Ability A=null;
						for(int a=mob.numEffects()-1;a>=0;a--)
						{
							A=mob.fetchEffect(a);
							if((A!=null)
							&&((A.classificationCode()&Ability.ALL_ACODES)==Ability.ACODE_COMMON_SKILL)
							&&(A.canBeUninvoked())
							&&(!A.isAutoInvoked()))
								A.unInvoke();
						}
						mob.makePeace(false);
						if(lastKnownLocation!=null)
							lastKnownLocation.recoverRoomStats();
					}
				}
				break;
			}
			case 43: // mpunhide
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget!=null)&&(CMath.bset(newTarget.basePhyStats().disposition(),PhyStats.IS_NOT_SEEN)))
				{
					newTarget.basePhyStats().setDisposition(newTarget.basePhyStats().disposition()-PhyStats.IS_NOT_SEEN);
					newTarget.recoverPhyStats();
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 44: // mpopen
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget instanceof Exit)&&(((Exit)newTarget).hasADoor()))
				{
					final Exit E=(Exit)newTarget;
					E.setDoorsNLocks(E.hasADoor(),true,E.defaultsClosed(),E.hasALock(),false,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				else
				if((newTarget instanceof Container)&&(((Container)newTarget).hasADoor()))
				{
					final Container E=(Container)newTarget;
					E.setDoorsNLocks(E.hasADoor(),true,E.defaultsClosed(),E.hasALock(),false,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 45: // mpclose
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget instanceof Exit)
				&&(((Exit)newTarget).hasADoor())
				&&(((Exit)newTarget).isOpen()))
				{
					final Exit E=(Exit)newTarget;
					E.setDoorsNLocks(E.hasADoor(),false,E.defaultsClosed(),E.hasALock(),false,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				else
				if((newTarget instanceof Container)
				&&(((Container)newTarget).hasADoor())
				&&(((Container)newTarget).isOpen()))
				{
					final Container E=(Container)newTarget;
					E.setDoorsNLocks(E.hasADoor(),false,E.defaultsClosed(),E.hasALock(),false,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 46: // mplock
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget instanceof Exit)
				&&(((Exit)newTarget).hasALock()))
				{
					final Exit E=(Exit)newTarget;
					E.setDoorsNLocks(E.hasADoor(),false,E.defaultsClosed(),E.hasALock(),true,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				else
				if((newTarget instanceof Container)
				&&(((Container)newTarget).hasALock()))
				{
					final Container E=(Container)newTarget;
					E.setDoorsNLocks(E.hasADoor(),false,E.defaultsClosed(),E.hasALock(),true,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 47: // mpunlock
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget instanceof Exit)
				&&(((Exit)newTarget).isLocked()))
				{
					final Exit E=(Exit)newTarget;
					E.setDoorsNLocks(E.hasADoor(),false,E.defaultsClosed(),E.hasALock(),false,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				else
				if((newTarget instanceof Container)
				&&(((Container)newTarget).isLocked()))
				{
					final Container E=(Container)newTarget;
					E.setDoorsNLocks(E.hasADoor(),false,E.defaultsClosed(),E.hasALock(),false,E.defaultsLocked());
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 48: // return
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				tickStatus=Tickable.STATUS_END;
				return varify(ctx,tt[1]);
			case 87: // mea
			case 7: // mpechoat
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccp");
				String parm=tt[1];
				int msgType = CMMsg.MSG_OK_ACTION;
				String str = varify(ctx,tt[2]);
				if(str.startsWith("NOSOUND "))
				{
					msgType = CMMsg.MSG_OK_VISUAL;
					str=str.substring(8);
				}
				Room lastR = lastKnownLocation;
				if((parm.equalsIgnoreCase("world"))
				&&(lastR!=null))
				{
					if(lastR.numInhabitants()==0)
						lastR.showSource(ctx.monster,null,msgType,str);
					for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
					{
						final Room R=e.nextElement();
						if(R.numInhabitants()>0)
							R.showOthers(ctx.monster,null,msgType,str);
					}
				}
				else
				if(parm.equalsIgnoreCase("area")
				&&(lastR!=null))
				{
					if(lastR.numInhabitants()==0)
						lastR.showSource(ctx.monster,null,msgType,str);
					for(final Enumeration<Room> e=lastR.getArea().getProperMap();e.hasMoreElements();)
					{
						final Room R=e.nextElement();
						if(R.numInhabitants()>0)
							R.showOthers(ctx.monster,null,msgType,str);
					}
				}
				else
				{
					final Environmental newTarget = getArgumentMOB(parm,ctx);
					lastR=CMLib.map().roomLocation(newTarget);
					if((newTarget instanceof MOB)
					&&(lastR!=null))
					{
						if(newTarget==ctx.monster)
							lastR.showSource(ctx.monster,null,null,msgType,str);
						else
						{
							lastR.show(ctx.monster,newTarget,null,msgType,null,msgType,str,CMMsg.NO_EFFECT,null);
							tellTerminal(ctx,(MOB)newTarget,str); // SC8: a background member's goes to the terminal
						}
					}
					else
					{
						parm = this.varify(ctx, parm);
						if(parm.trim().length()>0)
						{
							Room R = CMLib.map().getRoom(parm);
							if(R!=null)
								R.show(ctx.monster,null,msgType,str);
							else
							{
								final Area A = CMLib.map().findArea(parm);
								if(A != null)
								{
									if((lastR!=null)
									&&((lastR.numInhabitants()==0)||(!A.inMyMetroArea(lastR.getArea()))))
										lastR.showSource(ctx.monster,null,msgType,str);
									for(final Enumeration<Room> e=A.getMetroMap();e.hasMoreElements();)
									{
										R=e.nextElement();
										if(R.numInhabitants()>0)
											R.showOthers(ctx.monster,null,msgType,str);
									}
								}
							}
						}
					}
				}
				break;
			}
			case 88: // mer
			case 8: // mpechoaround
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccp");
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				final Room lastR=CMLib.map().roomLocation(newTarget);
				if((newTarget!=null)&&(newTarget instanceof MOB)&&(lastR!=null))
				{
					int msgType = CMMsg.MSG_OK_ACTION;
					String str = varify(ctx,tt[2]);
					if(str.startsWith("NOSOUND "))
					{
						msgType = CMMsg.MSG_OK_VISUAL;
						str=str.substring(8);
					}
					lastR.showOthers((MOB)newTarget,null,msgType,str);
				}
				break;
			}
			case 9: // mpcast
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final String cast=varify(ctx,tt[1]);
				final Physical newTarget=getArgumentItem(tt[2],ctx);
				Ability A=null;
				if(cast!=null)
					A=findAbility(cast);
				if((A==null)||(cast==null)||(cast.length()==0))
					logError(ctx,"MPCAST","RunTime",cast+" is not a valid ability name.");
				else
				if((newTarget!=null)||(tt[2].length()==0))
				{
					A.setProficiency(100);
					if((ctx.monster != ctx.scripted)
					&&(ctx.monster!=null))
						ctx.monster.resetToMaxState();
					A.invoke(ctx.monster,newTarget,false,0);
				}
				break;
			}
			case 89: // mpcastext
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final String cast=varify(ctx,tt[1]);
				final Physical newTarget=getArgumentItem(tt[2],ctx);
				final String args=varify(ctx,tt[3]);
				Ability A=null;
				if(cast!=null)
					A=findAbility(cast);
				if((A==null)||(cast==null)||(cast.length()==0))
					logError(ctx,"MPCASTEXT","RunTime",cast+" is not a valid ability name.");
				else
				if((newTarget!=null)||(tt[2].length()==0))
				{
					A.setProficiency(100);
					final List<String> commands = CMParms.parse(args);
					if((ctx.monster != ctx.scripted)
					&&(ctx.monster!=null))
						ctx.monster.resetToMaxState();
					A.invoke(ctx.monster, commands, newTarget, false, 0);
				}
				break;
			}
			case 30: // mpaffect
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccp");
				final String cast=varify(ctx,tt[1]);
				final Physical newTarget=getArgumentItem(tt[2],ctx);
				String m2=varify(ctx,tt[3]);
				Ability A=null;
				boolean alwaysAffect=false;
				if(cast!=null)
				{
					A=findAbility(cast);
					if((A==null)&&(cast.toLowerCase().startsWith("property:")))
					{
						A=findAbility(cast.substring(9));
						alwaysAffect=true;
					}
				}
				if((A==null)||(cast==null)||(cast.length()==0))
					logError(ctx,"MPAFFECT","RunTime",cast+" is not a valid ability name.");
				else
				if(newTarget!=null)
				{
					if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster()))
						Log.sysOut("Scripting",newTarget.Name()+" was MPAFFECTED by "+A.Name());
					int ticks = -1;
					if((m2.length()>0)&&(Character.isDigit(m2.charAt(0))))
					{
						int i=1;
						while((i<m2.length())&&(Character.isDigit(m2.charAt(i))))
							i++;
						if((i<=m2.length()-5)
						&&(Character.toUpperCase(m2.charAt(i))=='T')
						&&((m2+" ").substring(i,i+6).toUpperCase().equals("TICKS ")))
						{
							ticks=CMath.s_int(m2.substring(0,i));
							m2=((m2+" ").substring(i+6)).trim();
						}
					}
					A.setMiscText(m2);
					if(((A.classificationCode()&Ability.ALL_ACODES)==Ability.ACODE_PROPERTY) || (alwaysAffect))
					{
						if(alwaysAffect)
							A.setInvoker(ctx.monster);
						if(ticks < 0)
							newTarget.addNonUninvokableEffect(A);
						else
						{
							Ability tA=newTarget.fetchEffect("TemporaryAffects");
							if(tA==null)
							{
								tA=CMClass.getAbility("TemporaryAffects");
								if(newTarget instanceof MOB)
									newTarget.addEffect(tA);
								else
									tA.startTickDown(ctx.monster, newTarget, 9999);
							}
							tA.makeLongLasting();
							tA.setMiscText("+"+A.ID()+" "+ticks+" "+m2);
						}
						newTarget.recoverPhyStats();
						if(newTarget instanceof MOB)
						{
							((MOB)newTarget).recoverCharStats();
							((MOB)newTarget).recoverMaxState();
						}
					}
					else
					{
						if((ctx.monster != ctx.scripted)
						&&(ctx.monster!=null))
							ctx.monster.resetToMaxState();
						A.invoke(ctx.monster,CMParms.parse(m2),newTarget,true,0);
						if(ticks > 0)
						{
							final Ability afA = newTarget.fetchEffect(A.ID());
							if(afA!=null)
								afA.setExpirationDate(GameTime.now()+(CMProps.getTickMillis() * ticks));
						}
					}
				}
				break;
			}
			case 80: // mpspeak
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final String language=varify(ctx,tt[2]);
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				final Ability A=getAbility(language);
				if((A instanceof Language)&&(newTarget instanceof MOB))
				{
					A.setProficiency(100);
					A.autoInvocation((MOB)newTarget, false);
					final Ability langA=((MOB)newTarget).fetchEffect(A.ID());
					if(langA!=null)
					{
						if(((MOB)newTarget).isMonster())
							langA.setProficiency(100);
						langA.invoke((MOB)newTarget,CMParms.parse(""),(MOB)newTarget,false,0);
					}
					else
						A.invoke((MOB)newTarget,CMParms.parse(""),(MOB)newTarget,false,0);
				}
				break;
			}
			case 81: // mpsetclan
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final PhysicalAgent newTarget=getArgumentMOB(tt[1],ctx);
				final String clan=varify(ctx,tt[2]);
				final String roleStr=varify(ctx,tt[3]);
				Clan C=CMLib.clans().getClan(clan);
				if(C==null)
					C=CMLib.clans().findClan(clan);
				if((newTarget instanceof MOB)&&(C!=null))
				{
					int role=Integer.MIN_VALUE;
					if(CMath.isInteger(roleStr))
						role=CMath.s_int(roleStr);
					else
					for(int i=0;i<C.getRolesList().length;i++)
					{
						if(roleStr.equalsIgnoreCase(C.getRolesList()[i]))
							role=i;
					}
					if(role!=Integer.MIN_VALUE)
					{
						if(((MOB)newTarget).isPlayer())
							C.addMember((MOB)newTarget, role);
						else
							((MOB)newTarget).setClan(C.clanID(), role);
					}
				}
				break;
			}
			case 31: // mpbehave
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccp");
				String cast=varify(ctx,tt[1]);
				final PhysicalAgent newTarget=getArgumentItem(tt[2],ctx);
				final String m2=varify(ctx,tt[3]);
				Behavior B=null;
				final Behavior B2=(cast==null)?null:CMClass.findBehavior(cast);
				if(B2!=null)
					cast=B2.ID();
				if((cast!=null)&&(newTarget!=null))
				{
					B=newTarget.fetchBehavior(cast);
					if(B==null)
						B=CMClass.getBehavior(cast);
				}
				if((newTarget!=null)&&(B!=null))
				{
					if(newTarget.fetchBehavior(B.ID())==null)
					{
						B.setParms(m2);
						if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster()))
							Log.sysOut("Scripting",newTarget.Name()+" was MPBEHAVED with "+B.name());
						newTarget.addBehavior(B);
						if((defaultQuestName()!=null)&&(defaultQuestName().length()>0))
							B.registerDefaultQuest(defaultQuestName());
					}
				}
				break;
			}
			case 72: // mpscript
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccp");
				final PhysicalAgent newTarget=getArgumentItem(tt[1],ctx);
				String m2=varify(ctx,tt[2]);
				boolean proceed=true;
				boolean savable=false;
				boolean execute=false;
				boolean delete=false;
				boolean statica = false;
				String scope=getVarScope();
				while(proceed)
				{
					proceed=false;
					if(m2.toUpperCase().startsWith("SAVABLE "))
					{
						savable=true;
						m2=m2.substring(8).trim();
						proceed=true;
					}
					else
					if(m2.toUpperCase().startsWith("STATIC "))
					{
						statica=true;
						m2=m2.substring(7).trim();
						proceed=true;
					}
					else
					if(m2.toUpperCase().startsWith("DELETE "))
					{
						delete=true;
						m2=m2.substring(7).trim();
						proceed=true;
					}
					else
					if(m2.toUpperCase().startsWith("EXECUTE "))
					{
						execute=true;
						delete=true;
						m2=m2.substring(8).trim();
						proceed=true;
					}
					else
					if(m2.toUpperCase().startsWith("GLOBAL "))
					{
						scope="";
						proceed=true;
						m2=m2.substring(6).trim();
					}
					else
					if(m2.toUpperCase().startsWith("INDIVIDUAL "))
					{
						scope="*";
						proceed=true;
						m2=m2.substring(10).trim();
					}
					else
					if(m2.startsWith("* "))
					{
						scope="*";
						proceed=true;
						m2=m2.substring(2).trim();
					}
				}
				if((newTarget!=null)&&(m2.length()>0))
				{
					if(delete && (!execute))
					{
						String m3 = m2.trim();
						if(m3.equals("*"))
							m3 = getScript().trim();
						final ScriptingEngine B = (ScriptingEngine)newTarget.fetchBehavior("Scriptable");
						if((B != null) && (B.getScript().trim().equals(m3)))
							newTarget.delBehavior((Behavior)B);
						for(final Enumeration<ScriptingEngine> s1= newTarget.scripts(); s1.hasMoreElements();)
						{
							final ScriptingEngine S=s1.nextElement();
							if(S.getScript().trim().equalsIgnoreCase(m3))
							{
								//Log.sysOut("Scripting",newTarget.Name()+" was DE-MPSCRIPTED: "+defaultQuestName);
								newTarget.delScript(S);
								break;
							}
						}
					}
					else
					{
						//if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster()))
						//	Log.sysOut("Scripting",newTarget.Name()+" was MPSCRIPTED: "+defaultQuestName);
						final ScriptingEngine S=(ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
						S.setSavable(savable);
						S.setVarScope(scope);
						if(m2.trim().equals("*"))
							S.setScript(getScript());
						else
						{
							if(statica)
								m2=this.parseLoads(newTarget, m2, 0, null, null);
							S.setScript(m2);
						}
						if((this.questCacheObj!=null)
						&&(defaultQuest()==this.questCacheObj))
							S.registerDefaultQuest(defaultQuest());
						else
						if((defaultQuestName()!=null)&&(defaultQuestName().length()>0))
							S.registerDefaultQuest(defaultQuestName());
						newTarget.addScript(S);
						if(execute)
						{
							S.tick(newTarget,Tickable.TICKID_MOB);
							for(int i=0;i<5;i++)
								S.dequeResponses(null);
							{
								try
								{
									final Method M = S.getClass().getDeclaredMethod("getScripts", Environmental.class);
									@SuppressWarnings("unchecked")
									final List<SubScript> scripts = (List<SubScript>) M.invoke(S, newTarget);
									for(final SubScript scr : scripts)
										if(scr.isFlagSet("SPAWN"))
											script.setFlag("SPAWN");
								}
								catch(final Exception e)
								{
									Log.errOut(e);
								}
							}
						}
						if(delete)
							newTarget.delScript(S);
					}
				}
				break;
			}
			case 32: // mpunbehave
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				String cast=varify(ctx,tt[1]);
				final PhysicalAgent newTarget=getArgumentItem(tt[2],ctx);
				if((newTarget!=null)&&(cast!=null))
				{
					Behavior B=CMClass.findBehavior(cast);
					if(B!=null)
						cast=B.ID();
					B=newTarget.fetchBehavior(cast);
					if(B!=null)
						newTarget.delBehavior(B);
					if((newTarget instanceof MOB)&&(!((MOB)newTarget).isMonster())&&(B!=null))
						Log.sysOut("Scripting",newTarget.Name()+" was MPUNBEHAVED with "+B.name());
				}
				break;
			}
			case 33: // mptattoo
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				String tattooName=varify(ctx,tt[2]);
				if((newTarget!=null)&&(tattooName.length()>0)&&(newTarget instanceof MOB))
				{
					final MOB themob=(MOB)newTarget;
					final boolean tattooMinus=tattooName.startsWith("-");
					if(tattooMinus)
						tattooName=tattooName.substring(1);
					final Tattoo pT=((Tattoo)CMClass.getCommon("DefaultTattoo")).parse(tattooName);
					final Tattoo T=themob.findTattoo(pT.getTattooName());
					if(T!=null)
					{
						if(tattooMinus)
							themob.delTattoo(T);
					}
					else
					if(!tattooMinus)
						themob.addTattoo(pT);
				}
				break;
			}
			case 82: // mprload
				// listed, but never had an implementation: an error, rather
				// than running "MPRLOAD" as a game command
				logError(ctx,"MPRLOAD","Syntax","MPRLOAD is not a command.");
				break;
			case 83: // mpacctattoo
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				String tattooName=varify(ctx,tt[2]);
				if((newTarget!=null)
				&&(tattooName.length()>0)
				&&(newTarget instanceof MOB)
				&&(((MOB)newTarget).playerStats()!=null)
				&&(((MOB)newTarget).playerStats().getAccount()!=null))
				{
					final Tattooable themob=((MOB)newTarget).playerStats().getAccount();
					final boolean tattooMinus=tattooName.startsWith("-");
					if(tattooMinus)
						tattooName=tattooName.substring(1);
					final Tattoo pT=((Tattoo)CMClass.getCommon("DefaultTattoo")).parse(tattooName);
					final Tattoo T=themob.findTattoo(pT.getTattooName());
					if(T!=null)
					{
						if(tattooMinus)
							themob.delTattoo(T);
					}
					else
					if(!tattooMinus)
						themob.addTattoo(pT);
				}
				break;
			}
			case 92: // mpput
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Physical newTarget=getArgumentItem(tt[1],ctx);
				if(newTarget!=null)
				{
					if(tt[2].equalsIgnoreCase("NULL")||tt[2].equalsIgnoreCase("NONE"))
					{
						if(newTarget instanceof Item)
							((Item)newTarget).setContainer(null);
						if(newTarget instanceof Rider)
							((Rider)newTarget).setRiding(null);
					}
					else
					{
						final Physical newContainer=getArgumentItem(tt[2],ctx);
						if(newContainer!=null)
						{
							if(newTarget instanceof Item)
							{
								if(newContainer instanceof Container)
								{
									if(((Container) newContainer).owner() != ((Item)newTarget).owner())
									{
										if(((Container) newContainer).owner() instanceof Room)
											((Container) newContainer).owner().moveItemTo((Item)newTarget);
										else
										if(((Container) newContainer).owner() instanceof MOB)
											((Container) newContainer).owner().moveItemTo((Item)newTarget);

									}
									((Item)newTarget).setContainer((Container)newContainer);
								}
								else
								if(newContainer instanceof Room)
									((Room) newContainer).moveItemTo((Item)newTarget);
								else
								if(newContainer instanceof MOB)
									((MOB) newContainer).moveItemTo((Item)newTarget);
							}
							else
							if(newTarget instanceof Rider)
							{
								if(newContainer instanceof Rideable)
									((Rider)newTarget).setRiding((Rideable)newContainer);
							}
						}
					}
					newTarget.recoverPhyStats();
					if(lastKnownLocation!=null)
						lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 55: // mpnotrigger
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final String trigger=varify(ctx,tt[1]);
				final String time=varify(ctx,tt[2]);
				int triggerCode=-1;
				for(int i=0;i<progs.length;i++)
				{
					if(trigger.equalsIgnoreCase(progs[i]))
						triggerCode=i+1;
				}
				if(triggerCode<=0)
					logError(ctx,"MPNOTRIGGER","RunTime",trigger+" is not a valid trigger name.");
				else
				if(!CMath.isInteger(time.trim()))
					logError(ctx,"MPNOTRIGGER","RunTime",time+" is not a valid milisecond time.");
				else
				{
					noTrigger.remove(Integer.valueOf(triggerCode));
					noTrigger.put(Integer.valueOf(triggerCode),Long.valueOf(GameTime.now()+CMath.s_long(time.trim())));
					for(int q=que.size()-1;q>=0;q--)
					{
						ScriptableResponse SB=null;
						try
						{
							SB=que.get(q);
						}
						catch(final ArrayIndexOutOfBoundsException x)
						{
							continue;
						}
						if(SB.triggerCode == triggerCode)
							que.remove(q);
					}
				}
				break;
			}
			case 54: // mpfaction
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				final String faction=varify(ctx,tt[2]);
				String range=varify(ctx,tt[3]).trim();
				if(newTarget instanceof MOB)
				{
					Faction F=CMLib.factions().getFaction(faction);
					if(F == null)
					{
						F=CMLib.factions().getFactionByName(faction);
						if(F != null)
							tt[2]=F.factionID();
						else
							logError(ctx,"MPFACTION","RunTime","Faction '"+faction+"' is unknown.");
					}
					if(F!=null)
					{
						final MOB themob=(MOB)newTarget;
						int curFaction = themob.fetchFaction(F.factionID());
						if((curFaction == Integer.MAX_VALUE)||(curFaction == Integer.MIN_VALUE))
							curFaction = F.findDefault(themob);
						if((range.startsWith("--"))&&(CMath.isInteger(range.substring(2).trim())))
						{
							final int amt=CMath.s_int(range.substring(2).trim());
							if(amt < 0)
								themob.tell(L("You gain @x1 faction with @x2.",""+(-amt),F.name()));
							else
								themob.tell(L("You lose @x1 faction with @x2.",""+amt,F.name()));
							range=""+(curFaction-amt);
						}
						else
						if((range.startsWith("+"))&&(CMath.isInteger(range.substring(1).trim())))
						{
							final int amt=CMath.s_int(range.substring(1).trim());
							if(amt < 0)
								themob.tell(L("You lose @x1 faction with @x2.",""+(-amt),F.name()));
							else
								themob.tell(L("You gain @x1 faction with @x2.",""+amt,F.name()));
							range=""+(curFaction+amt);
						}
						else
						if(CMath.isInteger(range))
							themob.tell(L("Your faction with @x1 is now @x2.",F.name(),""+CMath.s_int(range.trim())));

						if(CMath.isInteger(range))
							themob.addFaction(F.factionID(),CMath.s_int(range.trim()));
						else
						{
							// a band of the faction named, by its name or its code
							Faction.FRange FR=null;
							for(final Enumeration<Faction.FRange> e=F.ranges();e.hasMoreElements();)
							{
								final Faction.FRange FR2=e.nextElement();
								if(FR2.name().equalsIgnoreCase(range)
								||FR2.codeName().equalsIgnoreCase(range))
								{
									FR = FR2;
									break;
								}
							}
							if(FR==null)
								logError(ctx,"MPFACTION","RunTime",range+" is not a valid range for "+F.name()+".");
							else
							{
								themob.tell(L("Your faction with @x1 is now @x2.",F.name(),FR.name()));
								themob.addFaction(F.factionID(),FR.low()+((FR.high()-FR.low())/2));
							}
						}
					}
				}
				break;
			}
			case 49: // mptitle
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				String titleStr=varify(ctx, tt[2]);
				if((newTarget!=null)&&(titleStr.length()>0)&&(newTarget instanceof MOB))
				{
					final MOB themob=(MOB)newTarget;
					final boolean tattooMinus=titleStr.startsWith("-");
					if(tattooMinus)
						titleStr=titleStr.substring(1);
					if(themob.playerStats()!=null)
					{
						if(themob.playerStats().getTitles().contains(titleStr))
						{
							if(tattooMinus)
								themob.playerStats().delTitle(titleStr);
						}
						else
						if(!tattooMinus)
							themob.playerStats().addTitle(titleStr); // re-add moves it UP!
					}
				}
				break;
			}
			case 10: // mpkill
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				if((newTarget!=null)
				&&(newTarget instanceof MOB)
				&&(ctx.monster!=null))
					ctx.monster.setVictim((MOB)newTarget);
				break;
			}
			case 98: // mphit
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				if((newTarget!=null)
				&&(newTarget instanceof MOB)
				&&(ctx.monster!=null))
				{
					final Room R=((MOB)newTarget).location();
					final Map<MOB,MOB> preAttackMap = new HashMap<MOB,MOB>();
					for(final Enumeration<MOB> r=R.inhabitants();r.hasMoreElements();)
					{
						final MOB M=r.nextElement();
						preAttackMap.put(M, M.getVictim());
					}
					CMLib.combat().postAttack(ctx.monster, (MOB)newTarget, ctx.monster.fetchWieldedItem());
					for(final MOB M : preAttackMap.keySet())
						M.setVictim(preAttackMap.get(M));
				}
				break;
			}
			case 51: // mpsetclandata
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				String clanID=null;
				if((newTarget!=null)&&(newTarget instanceof MOB))
				{
					Clan C=CMLib.clans().findRivalrousClan((MOB)newTarget);
					if(C==null)
						C=((MOB)newTarget).clans().iterator().hasNext()?((MOB)newTarget).clans().iterator().next().first:null;
					if(C!=null)
						clanID=C.clanID();
				}
				else
					clanID=varify(ctx,tt[1]);
				final String clanvar=tt[2];
				final String clanval=varify(ctx,tt[3]);
				final Clan C=CMLib.clans().getClan(clanID);
				if(C!=null)
				{
					if(!C.isStat(clanvar))
						logError(ctx,"MPSETCLANDATA","RunTime",clanvar+" is not a valid clan variable.");
					else
					{
						C.setStat(clanvar,clanval.trim());
						if(C.getStat(clanvar).equalsIgnoreCase(clanval))
							C.update();
					}
				}
				break;
			}
			case 52: // mpplayerclass
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				if((newTarget!=null)&&(newTarget instanceof MOB))
				{
					final List<String> V=CMParms.parse(tt[2]);
					for(int i=0;i<V.size();i++)
					{
						if(CMath.isInteger(V.get(i).trim()))
							((MOB)newTarget).baseCharStats().setClassLevel(((MOB)newTarget).baseCharStats().getCurrentClass(),CMath.s_int(V.get(i).trim()));
						else
						{
							final CharClass C=CMClass.findCharClass(V.get(i));
							if((C!=null)&&(C.availabilityCode()!=0))
								((MOB)newTarget).baseCharStats().setCurrentClass(C);
						}
					}
					((MOB)newTarget).recoverCharStats();
				}
				break;
			}
			case 12: // mppurge
			{
				if(lastKnownLocation!=null)
				{
					int flag=0;
					if(tt==null)
					{
						final String s2=CMParms.getCleanBit(s,1).toLowerCase();
						if(s2.equals("room"))
							tt=parseBits(ctx,"Ccr");
						else
						if(s2.equals("my"))
							tt=parseBits(ctx,"Ccr");
						else
							tt=parseBits(ctx,"Cr");
					}
					String s2=tt[1];
					if(s2.equalsIgnoreCase("room"))
					{
						flag=1;
						s2=tt[2];
					}
					else
					if(s2.equalsIgnoreCase("my"))
					{
						flag=2;
						s2=tt[2];
					}
					Environmental E=null;
					if(s2.equalsIgnoreCase("self")||s2.equalsIgnoreCase("me"))
						E=ctx.scripted;
					else
					if(flag==1)
					{
						final String vs2=varify(ctx,s2);
						E=lastKnownLocation.fetchFromRoomFavorItems(null,vs2);
					}
					else
					if(flag==2)
					{
						if(ctx.monster!=null)
						{
							final String vs2=varify(ctx,s2);
							E=ctx.monster.findItem(vs2);
						}
					}
					else
					if(E==null)
						E=getArgumentItem(s2,ctx);
					if(E!=null)
					{
						if(E instanceof MOB)
						{
							if(((MOB)E).isPlayer())
							{
								if(((MOB)E).getStartRoom()!=null)
									((MOB)E).getStartRoom().bringMobHere((MOB)E,false);
								if(((MOB)E).session() != null)
									((MOB)E).session().stopSession(true,false,false, false);
							}
							else
							if(((MOB)E).getStartRoom()!=null)
								((MOB)E).killMeDead(false);
							else
								E.destroy();
						}
						else
						if(E instanceof Item)
						{
							final ItemPossessor oE=((Item)E).owner();
							E.destroy();
							if(oE!=null)
								oE.recoverPhyStats();
						}
					}
					lastKnownLocation.recoverRoomStats();
				}
				break;
			}
			case 14: // mpgoto
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String roomID=tt[1].trim();
				if((roomID.length()>0)&&(lastKnownLocation!=null))
				{
					Room goHere=null;
					if(roomID.startsWith("$"))
						goHere=CMLib.map().roomLocation(this.getArgumentItem(roomID,ctx));
					if(goHere==null)
						goHere=getRoom(varify(ctx,roomID),lastKnownLocation);
					if(goHere!=null)
					{
						if(ctx.scripted instanceof MOB)
							goHere.bringMobHere((MOB)ctx.scripted,true);
						else
						if(ctx.scripted instanceof Item)
							goHere.moveItemTo((Item)ctx.scripted,ItemPossessor.Expire.Player_Drop,ItemPossessor.Move.Followers);
						else
						{
							goHere.bringMobHere(ctx.monster,true);
							if((!(ctx.scripted instanceof MOB))
							&&(!ctx.monster.isPlayer()))
								goHere.delInhabitant(ctx.monster);
						}
						if(CMLib.map().roomLocation(ctx.scripted)==goHere)
							lastKnownLocation=goHere;
					}
				}
				break;
			}
			case 15: // mpat
				if(lastKnownLocation!=null)
				{
					if(tt==null)
						tt=parseBits(ctx,"Ccp");
					final Room lastPlace=lastKnownLocation;
					final String roomName=tt[1];
					if(roomName.length()>0)
					{
						final String doWhat=tt[2].trim();
						Room goHere=null;
						if(roomName.startsWith("$"))
							goHere=CMLib.map().roomLocation(this.getArgumentItem(roomName,ctx));
						if(goHere==null)
							goHere=getRoom(varify(ctx,roomName),lastKnownLocation);
						if(goHere!=null)
						{
							final SubScript subScript=new SubScriptImpl(script);
							subScript.add(new ScriptLn("",null,null));
							subScript.add(new ScriptLn(doWhat,null,null));
							if(goHere == lastPlace)
								execute(ctx.push(subScript));
							else
							{
								goHere.bringMobHere(ctx.monster,true);
								lastKnownLocation=goHere;
								execute(ctx.push(subScript));
								lastKnownLocation=lastPlace;
								lastPlace.bringMobHere(ctx.monster,true);
								if(!(ctx.scripted instanceof MOB))
								{
									goHere.delInhabitant(ctx.monster);
									lastPlace.delInhabitant(ctx.monster);
								}
							}
						}
					}
				}
				break;
			case 17: // mptransfer
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				String mobName=tt[1];
				String roomName=tt[2].trim();
				Room newRoom=null;
				if(roomName.startsWith("$"))
					newRoom=CMLib.map().roomLocation(this.getArgumentItem(roomName,ctx));
				if((roomName.length()==0)&&(lastKnownLocation!=null))
					roomName=lastKnownLocation.roomID();
				if(roomName.length()>0)
				{
					if(newRoom==null)
						newRoom=getRoom(varify(ctx,roomName),lastKnownLocation);
					if(newRoom!=null)
					{
						final ArrayList<Environmental> V=new ArrayList<Environmental>();
						if(mobName.startsWith("$"))
						{
							final Environmental E=getArgumentItem(mobName,ctx);
							if(E!=null)
								V.add(E);
						}
						if(V.size()==0)
						{
							mobName=varify(ctx,mobName);
							if(mobName.equalsIgnoreCase("all"))
							{
								if(lastKnownLocation!=null)
								{
									for(int x=0;x<lastKnownLocation.numInhabitants();x++)
									{
										final MOB m=lastKnownLocation.fetchInhabitant(x);
										if((m!=null)&&(m!=ctx.monster)&&(!V.contains(m)))
											V.add(m);
									}
								}
							}
							else
							{
								MOB findOne=null;
								Area A=null;
								if(findOne==null)
								{
									if(lastKnownLocation!=null)
									{
										findOne=lastKnownLocation.fetchInhabitant(mobName);
										A=lastKnownLocation.getArea();
										if((findOne!=null)&&(findOne!=ctx.monster))
											V.add(findOne);
									}
								}
								if(findOne==null)
								{
									findOne=CMLib.players().getPlayerAllHosts(mobName);
									if((findOne!=null)&&(!CMLib.flags().isInTheGame(findOne,true)))
										findOne=null;
									if((findOne!=null)&&(findOne!=ctx.monster))
										V.add(findOne);
								}
								if((findOne==null)&&(A!=null))
								{
									for(final Enumeration<Room> r=A.getProperMap();r.hasMoreElements();)
									{
										final Room R=r.nextElement();
										findOne=R.fetchInhabitant(mobName);
										if((findOne!=null)&&(findOne!=ctx.monster))
											V.add(findOne);
									}
								}
							}
						}
						for(int v=0;v<V.size();v++)
						{
							if(V.get(v) instanceof MOB)
							{
								final MOB mob=(MOB)V.get(v);
								final Set<MOB> H=mob.getGroupMembers(new HashSet<MOB>());
								for (final Object element : H)
								{
									final MOB M=(MOB)element;
									if((!V.contains(M))&&(M.location()==mob.location()))
										V.add(M);
								}
							}
						}
						for(int v=0;v<V.size();v++)
						{
							if(V.get(v) instanceof MOB)
							{
								final MOB follower=(MOB)V.get(v);
								final Room thisRoom=follower.location();
								final int dispmask=(PhyStats.IS_SLEEPING | PhyStats.IS_SITTING);
								final int dispo1 = follower.basePhyStats().disposition() &  dispmask;
								final int dispo2 = follower.phyStats().disposition() &  dispmask;
								follower.basePhyStats().setDisposition(follower.basePhyStats().disposition() & (~dispmask));
								follower.phyStats().setDisposition(follower.phyStats().disposition() & (~dispmask));
								// scripting guide calls for NO text -- empty is probably req tho
								final CMMsg enterMsg=CMClass.getMsg(follower,newRoom,null,CMMsg.MSG_ENTER|CMMsg.MASK_ALWAYS,null,CMMsg.MSG_ENTER,null,CMMsg.MSG_ENTER," "+CMLib.protocol().msp("appear.wav",10));
								final CMMsg leaveMsg=CMClass.getMsg(follower,thisRoom,null,CMMsg.MSG_LEAVE|CMMsg.MASK_ALWAYS," ");
								if((thisRoom!=null)
								&&thisRoom.okMessage(follower,leaveMsg)
								&&newRoom.okMessage(follower,enterMsg))
								{
									final boolean alreadyHere = follower.location()==(Room)enterMsg.target();
									if(follower.isInCombat())
									{
										CMLib.commands().postFlee(follower,("NOWHERE"));
										follower.makePeace(true);
									}
									thisRoom.send(follower,leaveMsg);
									if(!alreadyHere)
										((Room)enterMsg.target()).bringMobHere(follower,false);
									((Room)enterMsg.target()).send(follower,enterMsg);
									follower.basePhyStats().setDisposition(follower.basePhyStats().disposition() | dispo1);
									follower.phyStats().setDisposition(follower.phyStats().disposition() | dispo2);
									if(!CMLib.flags().isSleeping(follower)
									&&(!alreadyHere))
									{
										follower.tell(CMLib.lang().L("\n\r\n\r"));
										CMLib.commands().postLook(follower,true);
									}
								}
								else
								{
									follower.basePhyStats().setDisposition(follower.basePhyStats().disposition() | dispo1);
									follower.phyStats().setDisposition(follower.phyStats().disposition() | dispo2);
								}
							}
							else
							if((V.get(v) instanceof Item)
							&&(newRoom!=CMLib.map().roomLocation(V.get(v))))
								newRoom.moveItemTo((Item)V.get(v),ItemPossessor.Expire.Player_Drop,ItemPossessor.Move.Followers);
							if((V.get(v)==ctx.scripted)
							&&(ctx.scripted instanceof Physical)
							&&(newRoom.isHere(ctx.scripted)))
								lastKnownLocation=newRoom;
						}
					}
				}
				break;
			}
			case 25: // mpbeacon
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final String roomName=tt[1];
				Room newRoom=null;
				if((roomName.length()>0)&&(lastKnownLocation!=null))
				{
					final String beacon=varify(ctx,tt[2]);
					if(roomName.startsWith("$"))
						newRoom=CMLib.map().roomLocation(this.getArgumentItem(roomName,ctx));
					if(newRoom==null)
						newRoom=getRoom(varify(ctx,roomName),lastKnownLocation);
					if(newRoom == null)
						logError(ctx,"MPBEACON","RunTime",tt[1]+" is not a room.");
					else
					if(lastKnownLocation!=null)
					{
						final List<MOB> V=new ArrayList<MOB>();
						if(beacon.equalsIgnoreCase("all"))
						{
							for(int x=0;x<lastKnownLocation.numInhabitants();x++)
							{
								final MOB m=lastKnownLocation.fetchInhabitant(x);
								if((m!=null)&&(m!=ctx.monster)&&(!m.isMonster())&&(!V.contains(m)))
									V.add(m);
							}
						}
						else
						{
							final MOB findOne=lastKnownLocation.fetchInhabitant(beacon);
							if((findOne!=null)&&(findOne!=ctx.monster)&&(!findOne.isMonster()))
								V.add(findOne);
						}
						for(int v=0;v<V.size();v++)
						{
							final MOB follower=V.get(v);
							if(!follower.isMonster())
								follower.setStartRoom(newRoom);
						}
					}
				}
				break;
			}
			case 18: // mpforce
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccp");
				final PhysicalAgent newTarget;
				if(tt[1].equalsIgnoreCase("$GOD"))
					newTarget=CMLib.map().deity();
				else
					newTarget=getArgumentMOB(tt[1],ctx);
				final String force=varify(ctx,tt[2]).trim();
				if(newTarget!=null)
				{
					final SubScript vscript=new SubScriptImpl(script);
					vscript.add(new ScriptLn("FUNCTION_PROG MPFORCE_"+System.currentTimeMillis()+Math.random(),null,null));
					vscript.add(new ScriptLn(force,null,null));
					// this can not be permanently parsed because it is variable
					final MPContext ctx2 = ctx.push(vscript);
					ctx2.monster = getMakeMOB(newTarget);
					ctx2.scripted = ctx2.monster;
					execute(ctx2);
				}
				break;
			}
			case 79: // mppossess
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final PhysicalAgent newSource=getArgumentMOB(tt[1],ctx);
				final PhysicalAgent newTarget=getArgumentMOB(tt[2],ctx);
				if((!(newSource instanceof MOB))||(((MOB)newSource).isMonster()))
					logError(ctx,"MPPOSSESS","RunTime",tt[1]+" is not a player.");
				else
				if((!(newTarget instanceof MOB))||(!((MOB)newTarget).isMonster())||CMSecurity.isASysOp((MOB)newTarget))
					logError(ctx,"MPPOSSESS","RunTime",tt[2]+" is not a mob.");
				else
				{
					final MOB mobM=(MOB)newSource;
					final MOB targetM=(MOB)newTarget;
					final Session S=mobM.session();
					S.setMob(targetM);
					targetM.setSession(S);
					targetM.setSoulMate(mobM);
					mobM.setSession(null);
				}
				break;
			}
			case 20: // mpsetvar
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				String which=tt[1];
				final Environmental E=getArgumentItem(which,ctx);
				final String arg2=varify(ctx,tt[2]);
				String arg3=varify(ctx,tt[3]);
				// SC2: NOW is the area clock's stamp, for ELAPSED()
				if(arg3.trim().equalsIgnoreCase("NOW"))
				{
					final Area nA=CMLib.map().areaLocation(ctx.scripted);
					arg3=""+ScriptEvents.clockHours((nA!=null)?nA.getTimeObj():CMLib.time().globalClock());
				}
				which=getVarHost(E,which,ctx); // SC4: through getVarHost, for @ hosts
				if((which.length()>0)&&(arg2.length()>0))
				{
					if(CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTVARS))
						Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": SETVAR: "+which+"("+arg2+")="+arg3+"<");
					setVar(which,arg2,arg3);
				}
				break;
			}
			case 101: // mpllm
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				script.setFlag("SPAWN");
				String which=tt[1];
				final Environmental E=getArgumentItem(which,ctx);
				final String arg2=varify(ctx,tt[2]);
				String arg3=varify(ctx,tt[3]);
				if(!which.equals("*"))
				{
					if(E==null)
						which=varify(ctx,which);
					else
					if(E instanceof Room)
						which=CMLib.map().getExtendedRoomID((Room)E);
					else
						which=E.Name();
				}
				if((which.length()>0)&&(arg2.length()>0))
				{
					if(this.llmSession == null)
					{
						int maxMsgs = 5;
						String prompt = arg3;
						if(this.miscArgVars.containsKey("LLMMAXMSGS"))
							maxMsgs = CMath.s_int(this.miscArgVars.get("LLMMAXMSGS"));
						if(this.miscArgVars.containsKey("LLMPROMPT"))
							prompt = this.miscArgVars.get("LLMPROMPT").toString();
						else
							arg3="";
						this.llmSession = CMLib.protocol().createLLMSession(prompt, Integer.valueOf(maxMsgs));
					}
					if(this.llmSession == null)
						logError(ctx,"MPLLM","RunTime","Could not create LLM Session.");
					else
					if(arg3.length()>0)
						setVar(which,arg2,llmSession.chat(arg3));
					else
						setVar(which,arg2,arg3);
				}
				break;
			}
			case 102: // MPDEBUG
			{
				// builder mode and DEV=1 only: in a normal game a line left in does nothing
				if(!DevLog.debugEnabled())
					break;
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String val=varify(ctx,tt[1]);
				String where="";
				if((ctx.script!=null)&&(ctx.scripted instanceof PhysicalAgent))
					where=scriptLocation((PhysicalAgent)ctx.scripted,ctx.script,ctx.line);
				DevLog.debug(debugHost(ctx.scripted)+((where.length()>0)?(" "+where):""),val);
				break;
			}
			case 36: // mpsavevar
			{
				if(tt==null)
					tt=parseBits(ctx,"CcR");
				String which=tt[1];
				String arg2=tt[2];
				final Environmental E=getArgumentItem(which,ctx);
				which=getVarHost(E,which,ctx);
				if((which.length()>0)&&(arg2.length()>0))
				{
					DatabaseEngine db= CMLib.database();
					if((E instanceof MOB) && ((MOB)E).isPlayer())
					{
						final int threadId = CMLib.players().getPlayerThreadId((MOB)E);
						if(threadId >=0)
							db = CMLib.get(threadId)._database();
					}
					final PairList<String,String> vars=getScriptVarSet(which,arg2);
					for(int v=0;v<vars.size();v++)
					{
						which=vars.elementAtFirst(v);
						arg2=vars.elementAtSecond(v).toUpperCase();
						@SuppressWarnings("unchecked")
						final Map<String,String> H=(Map<String,String>)resources._getResource("SCRIPTVAR-"+which);
						String val="";
						if(H!=null)
						{
							val=H.get(arg2);
							if(val==null)
								val="";
						}
						if(val.length()>0)
							db.DBReCreatePlayerData(which,"SCRIPTABLEVARS",which.toUpperCase()+"_SCRIPTABLEVARS_"+arg2,val);
						else
							db.DBDeletePlayerData(which,"SCRIPTABLEVARS",which.toUpperCase()+"_SCRIPTABLEVARS_"+arg2);
					}
				}
				break;
			}
			case 39: // mploadvar
			{
				if(tt==null)
					tt=parseBits(ctx,"CcR");
				String which=tt[1];
				final String arg2=tt[2];
				final Environmental E=getArgumentItem(which,ctx);
				if(arg2.length()>0)
				{
					List<PlayerData> V=null;
					which=getVarHost(E,which,ctx);
					DatabaseEngine db= CMLib.database();
					if((E instanceof MOB) && ((MOB)E).isPlayer())
					{
						final int threadId = CMLib.players().getPlayerThreadId((MOB)E);
						if(threadId >=0)
							db = CMLib.get(threadId)._database();
					}
					if(arg2.equals("*"))
						V=db.DBReadPlayerData(which,"SCRIPTABLEVARS");
					else
						V=db.DBReadPlayerData(which,"SCRIPTABLEVARS",which.toUpperCase()+"_SCRIPTABLEVARS_"+arg2);
					if((V!=null)&&(V.size()>0))
					for(int v=0;v<V.size();v++)
					{
						final DatabaseEngine.PlayerData VAR=V.get(v);
						String varName=VAR.key();
						if(varName.startsWith(which.toUpperCase()+"_SCRIPTABLEVARS_"))
							varName=varName.substring((which+"_SCRIPTABLEVARS_").length());
						if(CMSecurity.isDebugging(CMSecurity.DbgFlag.SCRIPTVARS))
							Log.debugOut(CMStrings.padRight(ctx.scripted.Name(), 15)+": SETVAR: "+which+"("+varName+")="+VAR.xml()+"<");
						setVar(which,varName,VAR.xml());
					}
				}
				break;
			}
			case 40: // MPM2I2M
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final String arg1=tt[1];
				final Environmental E=getArgumentItem(arg1,ctx);
				if(E instanceof MOB)
				{
					final String arg2=tt[2];
					final String arg3=tt[3];
					final CagedAnimal caged=(CagedAnimal)CMClass.getItem("GenCaged");
					if(caged!=null)
					{
						caged.basePhyStats().setAbility(1);
						caged.recoverPhyStats();
					}
					if((caged!=null)&&caged.cageMe((MOB)E)&&(lastKnownLocation!=null))
					{
						if((arg2.length()>0)&&(!arg2.equalsIgnoreCase(tt[1])))
							caged.setName(arg2);
						if((arg3.length()>0)&&(!arg3.equalsIgnoreCase(tt[1])))
							caged.setDisplayText(arg3);
						lastKnownLocation.addItem(caged,ItemPossessor.Expire.Player_Drop);
						((MOB)E).killMeDead(false);
					}
				}
				else
				if(E instanceof CagedAnimal)
				{
					final MOB M=((CagedAnimal)E).unCageMe();
					if((M!=null)&&(lastKnownLocation!=null))
					{
						M.bringToLife(lastKnownLocation,true);
						E.destroy();
					}
				}
				else
					logError(ctx,"MPM2I2M","RunTime",arg1+" is not a mob or a caged item.");
				break;
			}
			case 28: // mpdamage
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String arg2=varify(ctx,tt[2]);
				final String arg3=varify(ctx,tt[3]);
				final String arg4=varify(ctx,tt[4]).toUpperCase();
				if((newTarget!=null)&&(arg2.length()>0))
				{
					// each option word: ME, KILL (or MEKILL), and a damage type
					boolean me=false;
					boolean kill=false;
					for(final String s4 : arg4.split(" "))
					{
						if(s4.equals("MEKILL"))
						{
							me=true;
							kill=true;
						}
						else
						if(s4.equals("ME"))
							me=true;
						else
						if(s4.equals("KILL"))
							kill=true;
					}
					if(newTarget instanceof MOB)
					{
						final MOB deadM=(MOB)newTarget;
						MOB killerM=(MOB)newTarget;
						int damageType = CMMsg.TYP_CAST_SPELL;
						for(final String s4 : arg4.split(" "))
						{
							if((s4.length()==0)||s4.equals("MEKILL")||s4.equals("ME")||s4.equals("KILL"))
								continue;
							else
							{
								for(int i4=0;i4<CharStats.CODES.CMMSGMAP().length;i4++)
								{
									final int code4=CharStats.CODES.CMMSGMAP()[i4];
									if((code4>0)&&(CMMsg.TYPE_DESCS[code4&CMMsg.MINOR_MASK].equals(s4)))
									{
										damageType=code4;
										break;
									}
								}
							}
						}
						if(me)
							killerM=ctx.monster;
						final int min=CMath.s_int(arg2.trim());
						int max=CMath.s_int(arg3.trim());
						if(max<min)
							max=min;
						if(min>0)
						{
							int dmg=(max==min)?min:CMLib.dice().roll(1,max-min,min);
							if((dmg>=deadM.curState().getHitPoints())
							&&(!kill))
								dmg=deadM.curState().getHitPoints()-1;
							if(dmg>0)
								CMLib.combat().postDamage(killerM,deadM,null,dmg,CMMsg.MASK_ALWAYS|damageType,-1,null);
						}
					}
					else
					if(newTarget instanceof Item)
					{
						final Item E=(Item)newTarget;
						final int min=CMath.s_int(arg2.trim());
						int max=CMath.s_int(arg3.trim());
						if(max<min)
							max=min;
						if(min>0)
						{
							int dmg=(max==min)?min:CMLib.dice().roll(1,max-min,min);
							boolean destroy=false;
							if(E.subjectToWearAndTear())
							{
								if((dmg>=E.usesRemaining())&&(!kill))
									dmg=E.usesRemaining()-1;
								if(dmg>0)
									E.setUsesRemaining(E.usesRemaining()-dmg);
								if(E.usesRemaining()<=0)
									destroy=true;
							}
							else
							if(kill)
								destroy=true;
							if(destroy)
							{
								if(lastKnownLocation!=null)
									lastKnownLocation.showHappens(CMMsg.MSG_OK_VISUAL,L("@x1 is destroyed!",E.name()));
								E.destroy();
							}
						}
					}
				}
				break;
			}
			case 78: // mpheal
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final Environmental newTarget=getArgumentItem(tt[1],ctx);
				final String arg2=varify(ctx,tt[2]);
				final String arg3=varify(ctx,tt[3]);
				if((newTarget!=null)&&(arg2.length()>0))
				{
					if(newTarget instanceof MOB)
					{
						final MOB healedM=(MOB)newTarget;
						final MOB healerM=(MOB)newTarget;
						final int min=CMath.s_int(arg2.trim());
						int max=CMath.s_int(arg3.trim());
						if(max<min)
							max=min;
						if(min>0)
						{
							final int amt=(max==min)?min:CMLib.dice().roll(1,max-min,min);
							if(amt>0)
								CMLib.combat().postHealing(healerM,healedM,null,amt,CMMsg.MASK_ALWAYS|CMMsg.TYP_CAST_SPELL,null);
						}
					}
					else
					if(newTarget instanceof Item)
					{
						final Item E=(Item)newTarget;
						final int min=CMath.s_int(arg2.trim());
						int max=CMath.s_int(arg3.trim());
						if(max<min)
							max=min;
						if(min>0)
						{
							final int amt=(max==min)?min:CMLib.dice().roll(1,max-min,min);
							if(E.subjectToWearAndTear())
							{
								E.setUsesRemaining(E.usesRemaining()+amt);
								if(E.usesRemaining()>100)
									E.setUsesRemaining(100);
							}
						}
					}
				}
				break;
			}
			case 90: // mplink
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final String dirWord=varify(ctx,tt[1]);
				final int dir=CMLib.directions().getGoodDirectionCode(dirWord);
				if(dir < 0)
					logError(ctx,"MPLINK","RunTime",dirWord+" is not a valid direction.");
				else
				{
					final String roomID = varify(ctx,tt[2]);
					final Room startR=(homeKnownLocation!=null)?homeKnownLocation:CMLib.map().getStartRoom(ctx.scripted);
					final Room R=this.getRoom(roomID, lastKnownLocation);
					if((R==null)||(lastKnownLocation==null)||(R.getArea()!=lastKnownLocation.getArea()))
						logError(ctx,"MPLINK","RunTime",roomID+" is not a target room.");
					else
					if((startR!=null)&&(startR.getArea()!=lastKnownLocation.getArea()))
						logError(ctx,"MPLINK","RunTime","mplink from "+roomID+" is an illegal source room.");
					else
					{
						String exitID=varify(ctx,tt[3]);
						String nameArg=null;
						final int x=exitID.indexOf(' ');
						if(x>0)
						{
							nameArg=exitID.substring(x+1).trim();
							exitID=exitID.substring(0,x);
						}
						final Exit E=CMClass.getExit(exitID);
						if(E==null)
							logError(ctx,"MPLINK","RunTime",exitID+" is not a exit class.");
						else
						if((lastKnownLocation.rawDoors()[dir]==null)
						&&(lastKnownLocation.getRawExit(dir)==null))
						{
							lastKnownLocation.rawDoors()[dir]=R;
							lastKnownLocation.setRawExit(dir, E);
							E.basePhyStats().setSensesMask(E.basePhyStats().sensesMask()|PhyStats.SENSE_ITEMNOWISH);
							E.setSavable(false);
							E.recoverPhyStats();
							if(nameArg!=null)
								E.setName(nameArg);
						}
					}
				}
				break;
			}
			case 91: // mpunlink
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String dirWord=varify(ctx,tt[1]);
				final int dir=CMLib.directions().getGoodDirectionCode(dirWord);
				if(dir < 0)
					logError(ctx,"MPLINK","RunTime",dirWord+" is not a valid direction.");
				else
				if((lastKnownLocation==null)
				||((lastKnownLocation.rawDoors()[dir]!=null)&&(lastKnownLocation.rawDoors()[dir].getArea()!=lastKnownLocation.getArea())))
					logError(ctx,"MPLINK","RunTime",dirWord+" is a non-in-area direction.");
				else
				if((lastKnownLocation.getRawExit(dir)!=null)
				&&(lastKnownLocation.getRawExit(dir).isSavable()
					||(!CMath.bset(lastKnownLocation.getRawExit(dir).basePhyStats().sensesMask(), PhyStats.SENSE_ITEMNOWISH))))
					logError(ctx,"MPLINK","RunTime",dirWord+" is not a legal unlinkable exit.");
				else
				{
					lastKnownLocation.setRawExit(dir, null);
					lastKnownLocation.rawDoors()[dir]=null;
				}
				break;
			}
			case 29: // mptrackto
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String arg1=varify(ctx,tt[1]);
				final Ability A=CMClass.getAbility("Skill_Track");
				if(A!=null)
				{
					if((ctx.monster != ctx.scripted)
					&&(ctx.monster!=null))
						ctx.monster.resetToMaxState();
					if(A.text().length()>0 && A.text().equalsIgnoreCase(arg1))
					{
						// already on the move
					}
					else
					{
						altStatusTickable=A;
						A.invoke(ctx.monster,CMParms.parse(arg1),null,true,0);
						altStatusTickable=null;
					}
				}
				break;
			}
			case 53: // mpwalkto
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String arg1=varify(ctx,tt[1]);
				final Ability A=CMClass.getAbility("Skill_Track");
				if(A!=null)
				{
					altStatusTickable=A;
					if((ctx.monster != ctx.scripted)
					&&(ctx.monster!=null))
						ctx.monster.resetToMaxState();
					A.invoke(ctx.monster,CMParms.parse(arg1+" LANDONLY"),null,true,0);
					altStatusTickable=null;
				}
				break;
			}
			case 21: //MPENDQUEST
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final PhysicalAgent newTarget;
				final String q=varify(ctx,tt[1].trim());
				final Quest Q=getQuest(q);
				if(Q!=null)
				{
					final CMMsg stopMsg=CMClass.getMsg(ctx.monster,null,null,CMMsg.NO_EFFECT,null,CMMsg.TYP_ENDQUEST,Q.name(),CMMsg.NO_EFFECT,null);
					CMLib.map().sendGlobalMessage(ctx.monster, CMMsg.TYP_ENDQUEST, stopMsg);
					Q.stopQuest();
					CMLib.coffeeTables().bump(Q,CoffeeTableRow.STAT_QUESTSTOP);
				}
				else
				if((tt[1].length()>0)
				&&(defaultQuestName!=null)
				&&(defaultQuestName.length()>0)
				&&((newTarget=getArgumentMOB(tt[1].trim(),ctx))!=null))
				{
					for(int i=newTarget.numScripts()-1;i>=0;i--)
					{
						final ScriptingEngine S=newTarget.fetchScript(i);
						if((S!=null)
						&&(S.defaultQuestName()!=null)
						&&(S.defaultQuestName().equalsIgnoreCase(defaultQuestName)))
						{
							S.stepQuest(newTarget, (newTarget instanceof MOB)?((MOB)newTarget):ctx.monster, defaultQuestName);
							newTarget.delScript(S);
							S.endQuest(newTarget, (newTarget instanceof MOB)?((MOB)newTarget):ctx.monster, defaultQuestName);
						}
					}
				}
				else
				if(q.length()>0)
				{
					boolean foundOne=false;
					for(int i=ctx.scripted.numScripts()-1;i>=0;i--)
					{
						final ScriptingEngine S=ctx.scripted.fetchScript(i);
						if((S!=null)
						&&(S.defaultQuestName()!=null)
						&&(S.defaultQuestName().equalsIgnoreCase(q)))
						{
							S.stepQuest(ctx.scripted, ctx.monster, S.defaultQuestName());
							foundOne=true;
							S.endQuest(ctx.scripted, ctx.monster, S.defaultQuestName());
							ctx.scripted.delScript(S);
						}
					}
					if((!foundOne)
					&&((defaultQuestName==null)||(!defaultQuestName.equalsIgnoreCase(q))))
						logError(ctx,"MPENDQUEST","Unknown","Quest: "+s);
				}
				break;
			}
			case 69: // MPSTEPQUEST
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				String qName=varify(ctx,tt[1].trim());
				final Quest Q;
				final int x=qName.lastIndexOf(" +");
				final int y=qName.lastIndexOf(" =");
				int skipNum=1;
				int setNum=-1;
				if((x>0)
				&&(CMath.isInteger(qName.substring(x+2).trim())))
				{
					skipNum=CMath.s_int(qName.substring(x+2).trim());
					qName=qName.substring(0,x).trim();
				}
				else
				if((y>0)
				&&(CMath.isInteger(qName.substring(y+2).trim())))
				{
					setNum=CMath.s_int(qName.substring(y+2).trim());
					qName=qName.substring(0,y).trim();
				}
				String setLabel=null; // Q6: MPSTEPQUEST quest =label
				if((setNum<0)&&(y>0)&&(qName.substring(y+2).trim().length()>0)&&(qName.substring(y+2).trim().indexOf(' ')<0))
				{
					setLabel=qName.substring(y+2).trim();
					qName=qName.substring(0,y).trim();
				}
				Q=getQuest(qName);
				if((Q!=null)&&(setLabel!=null))
				{
					setNum=CMath.s_int(Q.getStat("STEPOF:"+setLabel));
					if(setNum<=0)
					{
						logError(ctx,"MPSTEPQUEST","Unknown","Step: "+setLabel+" of quest "+Q.name());
						break;
					}
				}
				if(Q!=null)
				{
					if(setNum>0)
					{
						Q.setQuestStep(setNum);
					}
					else
					{
						for(int i=0;i<skipNum;i++)
							Q.stepQuest();
					}
				}
				else
					logError(ctx,"MPSTEPQUEST","Unknown","Quest: "+qName);
				break;
			}
			case 23: //MPSTARTQUEST
			{
				if(tt==null)
					tt=parseBits(ctx,"Cr");
				final String qName=varify(ctx,tt[1].trim());
				final Quest Q=getQuest(qName);
				if(Q!=null)
					Q.startQuest();
				else
					logError(ctx,"MPSTARTQUEST","Unknown","Quest: "+s);
				break;
			}
			case 64: //MPLOADQUESTOBJ
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccr");
				final String questName=varify(ctx,tt[1].trim());
				final Quest Q=getQuest(questName);
				if(Q==null)
				{
					logError(ctx,"MPLOADQUESTOBJ","Unknown","Quest: "+questName);
					break;
				}
				final Object O=Q.getDesignatedObject(varify(ctx,tt[2]));
				if(O==null)
				{
					logError(ctx,"MPLOADQUESTOBJ","Unknown","Unknown var "+tt[2]+" for Quest: "+questName);
					break;
				}
				final String varArg=tt[3];
				if((varArg.length()!=2)||(!varArg.startsWith("$")))
				{
					logError(ctx,"MPLOADQUESTOBJ","Syntax","Invalid argument var: "+varArg+" for "+ctx.scripted.Name());
					break;
				}

				final char c=varArg.charAt(1);
				if(Character.isDigit(c))
					ctx.tmp[CMath.s_int(Character.toString(c))]=O;
				else
				switch(c)
				{
				case 'N':
				case 'n':
					if (O instanceof MOB)
						ctx.source = (MOB) O;
					break;
				case 'I':
				case 'i':
					if (O instanceof PhysicalAgent)
						ctx.scripted = (PhysicalAgent) O;
					if (O instanceof MOB)
						ctx.monster = (MOB) O;
					break;
				case 'Q':
				case 'q':
					break;
				case 'B':
				case 'b':
					if (O instanceof Environmental)
						lastLoaded = (Environmental) O;
					break;
				case 'T':
				case 't':
					if (O instanceof Environmental)
						ctx.target = (Environmental) O;
					break;
				case 'O':
				case 'o':
					if (O instanceof Item)
						ctx.primaryItem = (Item) O;
					break;
				case 'P':
				case 'p':
					if (O instanceof Item)
						ctx.secondaryItem = (Item) O;
					break;
				case 'd':
				case 'D':
					if (O instanceof Room)
						lastKnownLocation = (Room) O;
					break;
				default:
					logError(ctx, "MPLOADQUESTOBJ", "Syntax", "Invalid argument var: " + varArg + " for " + ctx.scripted.Name());
					break;
				}
				break;
			}
			case 22: //MPQUESTWIN
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				String whoName=varify(ctx,tt[1]);
				// Q4: MPQUESTWIN who quest OUTCOME word
				String questPart=tt[2];
				String outcome=null;
				final String padded=" "+questPart;
				final java.util.regex.Matcher om=java.util.regex.Pattern.compile("(?i)\\s+OUTCOME\\s+(\\S+)").matcher(padded);
				if(om.find())
				{
					outcome=varify(ctx,om.group(1));
					questPart=(padded.substring(0,om.start())+" "+padded.substring(om.end())).trim();
				}
				// Q10: MPQUESTWIN who quest REWARD: the quest's SET REWARDS too
				boolean reward=false;
				final java.util.regex.Matcher rm=java.util.regex.Pattern.compile("(?i)\\s+REWARD\\s*$").matcher(" "+questPart);
				if(rm.find())
				{
					reward=true;
					questPart=(" "+questPart).substring(0,rm.start()).trim();
				}
				MOB M=null;
				if(lastKnownLocation!=null)
					M=lastKnownLocation.fetchInhabitant(whoName);
				if(M==null)
					M=CMLib.players().getPlayerAllHosts(whoName);
				if(M!=null)
					whoName=M.Name();
				if(whoName.length()>0)
				{
					final Quest Q=getQuest(questPart);
					if(Q!=null)
					{
						if(M!=null)
						{
							CMLib.achievements().possiblyBumpAchievement(M, AchievementLibrary.Event.QUESTOR, 1, Q);
							final CMMsg winMsg=CMClass.getMsg(M,null,null,CMMsg.NO_EFFECT,null,CMMsg.TYP_WINQUEST,Q.name(),CMMsg.NO_EFFECT,null);
							CMLib.map().sendGlobalMessage(M, CMMsg.TYP_WINQUEST, winMsg);
						}
						final List<MOB> party=(M!=null)?QuestSharing.partyOn(Q,M):new ArrayList<MOB>(); // Q12: SET SHARED PARTY
						Q.declareWinner(whoName);
						if(outcome!=null) // Q4
							QuestRecords.setOutcome(Q.name(),whoName,outcome);
						if(reward&&(M!=null)) // Q10
							QuestRewards.give(M,"QUEST:"+Q.name(),Q.getStat("REWARDS"));
						CMLib.players().bumpPrideStat(M,PrideStats.PrideStat.QUESTS_COMPLETED, 1);
						for(final MOB P : party) // Q12: the party members on it win with them
						{
							CMLib.achievements().possiblyBumpAchievement(P, AchievementLibrary.Event.QUESTOR, 1, Q);
							Q.declareWinner(P.Name());
							if(outcome!=null)
								QuestRecords.setOutcome(Q.name(),P.Name(),outcome);
							if(reward)
								QuestRewards.give(P,"QUEST:"+Q.name(),Q.getStat("REWARDS"));
							CMLib.players().bumpPrideStat(P,PrideStats.PrideStat.QUESTS_COMPLETED, 1);
						}
					}
					else
						logError(ctx,"MPQUESTWIN","Unknown","Quest: "+s);
				}
				break;
			}
			case 24: // MPCALLFUNC
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				if(script.get(ctx.line).third instanceof SubScript)
				{
					final MPContext ctx2 = ctx.push((SubScript)script.get(ctx.line).third);
					ctx2.msg =varify(ctx,tt[2].trim());
					execute(ctx2);
				}
				else
				{
					final String named=tt[1];
					final String parms=tt[2].trim();
					final SubScript script2 = findFunc(ctx.scripted,named);
					if(script2 != null)
					{
						script.get(ctx.line).third=script2;
						final MPContext ctx2 = ctx.push(script2);
						ctx2.msg = varify(ctx,parms);
						execute(ctx2);
					}
					else
					if(callOn(ctx,named,parms)==null) // SC6: MPCALLFUNC target func args
						logError(ctx,"MPCALLFUNC","Unknown","Function: "+named);
				}
				break;
			}
			case 27: // MPWHILE
			{
				if(tt==null)
				{
					final ArrayList<String> V=new ArrayList<String>();
					V.add("MPWHILE");
					String conditionStr=(s.substring(7).trim());
					if(!conditionStr.startsWith("("))
					{
						logError(ctx,"MPWHILE","Syntax"," NO Starting (: "+s);
						break;
					}
					conditionStr=conditionStr.substring(1).trim();
					int x=-1;
					int depth=0;
					for(int i=0;i<conditionStr.length();i++)
					{
						if(conditionStr.charAt(i)=='(')
							depth++;
						else
						if((conditionStr.charAt(i)==')')&&((--depth)<0))
						{
							x=i;
							break;
						}
					}
					if(x<0)
					{
						logError(ctx,"MPWHILE","Syntax"," no closing ')': "+s);
						break;
					}
					final String DO=conditionStr.substring(x+1).trim();
					conditionStr=conditionStr.substring(0,x);
					try
					{
						final String[] EVAL=parseEval(conditionStr);
						V.add("(");
						V.addAll(Arrays.asList(EVAL));
						V.add(")");
						V.add(DO);
						tt=CMParms.toStringArray(V);
						script.get(ctx.line).second=tt;
					}
					catch(final Exception e)
					{
						logError(ctx,"MPWHILE","Syntax",e.getMessage());
						break;
					}
					if(tt==null)
						return null;
				}
				int evalEnd=2;
				int depth=0;
				while((evalEnd<tt.length)&&((!tt[evalEnd].equals(")"))||(depth>0)))
				{
					if(tt[evalEnd].equals("("))
						depth++;
					else
					if(tt[evalEnd].equals(")"))
						depth--;
					evalEnd++;
				}
				if(evalEnd==tt.length)
				{
					logError(ctx,"MPWHILE","Syntax"," no closing ')': "+s);
					break;
				}
				final String[] EVAL=new String[evalEnd-2];
				for(int y=2;y<evalEnd;y++)
					EVAL[y-2]=tt[y];
				String DO=tt[evalEnd+1];
				String[] DOT=null;
				final int doLen=(tt.length-evalEnd)-1;
				if(doLen>1)
				{
					DOT=new String[doLen];
					for(int y=0;y<DOT.length;y++)
					{
						DOT[y]=tt[evalEnd+y+1];
						if(y>0)
							DO+=" "+tt[evalEnd+y+1];
					}
				}
				final String[][] EVALO={EVAL};
				final SubScript vscript=new SubScriptImpl(script);
				vscript.add(new ScriptLn("FUNCTION_PROG MPWHILE_"+Math.random(),null,null));
				vscript.add(new ScriptLn(DO,DOT,null));
				final long time=System.currentTimeMillis();
				while((eval(ctx,EVALO,0))
				&&((System.currentTimeMillis()-time)<4000)
				&&(!ctx.scripted.amDestroyed()))
				{
					final MPContext wctx=ctx.push(vscript);
					wctx.repeats=true; // SC7
					execute(wctx);
				}
				// cut off at its cap: said so (DT34), as it went unseen before
				if((System.currentTimeMillis()-time)>=4000)
					logError(ctx,"MPWHILE","RunTime","cut off after 4 seconds, its condition still true");
				if(vscript.get(1).second!=DOT)
				{
					final int oldDotLen=(DOT==null)?1:DOT.length;
					final String[] newDOT=vscript.get(1).second;
					final String[] newTT=new String[tt.length-oldDotLen+newDOT.length];
					int end=0;
					for(end=0;end<tt.length-oldDotLen;end++)
						newTT[end]=tt[end];
					for(int y=0;y<newDOT.length;y++)
						newTT[end+y]=newDOT[y];
					tt=newTT;
					script.get(ctx.line).second=tt;
				}
				if(EVALO[0]!=EVAL)
				{
					final List<String> lazyV=new ArrayList<String>();
					lazyV.add("MPWHILE");
					lazyV.add("(");
					final String[] newEVAL=EVALO[0];
					for (final String element : newEVAL)
						lazyV.add(element);
					for(int i=evalEnd;i<tt.length;i++)
						lazyV.add(tt[i]);
					tt=CMParms.toStringArray(lazyV);
					script.get(ctx.line).second=tt;
				}
				if((System.currentTimeMillis()-time)>=4000)
				{
					logError(ctx,"MPWHILE","RunTime","4 second limit exceeded: "+s);
					break;
				}
				break;
			}
			case 26: // MPALARM
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccp");
				String time=varify(ctx,tt[1]);
				String parms=tt[2].trim();
				String alarmName=null;
				if(time.toUpperCase().startsWith("NAME=")) // C5: MPALARM NAME=name ticks command
				{
					alarmName=time.substring(5).trim().toUpperCase();
					final int sp=parms.indexOf(' ');
					time=varify(ctx,(sp<0)?parms:parms.substring(0,sp));
					parms=(sp<0)?"":parms.substring(sp+1).trim();
					if(alarmName.length()==0)
					{
						logError(ctx,"MPALARM","Syntax","No name after NAME=");
						break;
					}
				}
				if(CMath.s_int(time.trim())<=0)
				{
					logError(ctx,"MPALARM","Syntax","Bad time "+time);
					break;
				}
				if(parms.length()==0)
				{
					logError(ctx,"MPALARM","Syntax","No command!");
					break;
				}
				final SubScript vscript=new SubScriptImpl(script);
				if(alarmName!=null)
				{
					cancelAlarm(alarmName); // one of a name: a new one replaces it
					vscript.add(new ScriptLn(Telegraph.NAMED_ALARM+alarmName,null,null));
				}
				else
					vscript.add(new ScriptLn("FUNCTION_PROG ALARM_"+time+Math.random(),null,null));
				vscript.add(new ScriptLn(parms,null,null));
				prequeResponse(-1,ctx,vscript,CMath.s_int(time.trim()));
				break;
			}
			case 99: // MPACHIEVE
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccp");
				final PhysicalAgent M = getArgumentMOB(tt[1], ctx);
				if((!(M instanceof MOB))||(((MOB)M).isMonster()))
				{
					logError(ctx,"MPACHIEVE","RunTime",tt[1]+" is not a player.");
					break;
				}
				final String achieveID=varify(ctx,tt[2]);
				final String amt=varify(ctx,tt[3]);
				boolean grant = false;
				if("".equalsIgnoreCase(amt)
				&& (ctx.scripted instanceof MOB)
				&& (CMSecurity.isAllowedEverywhere((MOB)ctx.scripted, SecFlag.CMDPLAYERS)))
					grant=true;
				else
				if((amt.length()==0)||(!CMath.isInteger(amt)))
				{
					logError(ctx,"MPACHIEVE","Syntax","Not 'grant' or an amount '"+amt+"'");
					break;
				}
				final Achievement A = CMLib.achievements().getAchievement(achieveID);
				if((achieveID.length()==0)||(A==null))
				{
					logError(ctx,"MPACHIEVE","RunTime","No Achievement '"+achieveID+"'");
					break;
				}
				if(grant)
					CMLib.achievements().grantAchievement((MOB)M, A);
				else
					CMLib.achievements().bumpAchievement((MOB)M, A, CMath.s_int(amt), new Object[0]);
				break;
			}
			case 100: // MPACCUSE
			{
				if(tt==null)
					tt=parseBits(ctx,"Cccp");
				final PhysicalAgent PA = getArgumentMOB(tt[1], ctx);
				if(!(PA instanceof MOB))
				{
					logError(ctx,"MPACCUSE","RunTime",tt[1]+" is not found.");
					break;
				}
				PhysicalAgent WA = null;
				if(tt[2].trim().length()>0)
				{
					WA = getArgumentMOB(tt[2], ctx);
					if(!(WA instanceof MOB))
					{
						logError(ctx,"MPACCUSE","RunTime",tt[2]+" is not found.");
						break;
					}
				}
				final MOB M = (MOB)PA;
				final MOB WM = (WA != null)?(MOB)WA:null;
				if(lastKnownLocation==null)
					confirmLastKnownLocation(ctx.monster,ctx.source);
				final Room locR = lastKnownLocation;
				if(locR == null)
					break;
				final LegalBehavior B = CMLib.law().getLegalBehavior(locR);
				if(B == null)
				{
					logError(ctx,"MPACCUSE","RunTime","No law at "+CMLib.map().getExtendedRoomID(locR));
					break;
				}
				final Area A = CMLib.law().getLegalObject(locR);
				String crimeID=varify(ctx,tt[3]);
				if(CMParms.numBits(crimeID)==1)
				{
					crimeID = CMParms.cleanBit(crimeID);
					B.accuse(A, M, WM, new String[] {crimeID});
				}
				else
				if(CMParms.numBits(crimeID)==Law.BIT_NUMBITS)
				{
					final String[] newBits=CMParms.getCleanBits(crimeID);
					B.accuse(A, M, WM, newBits);
				}
				else
				{
					logError(ctx,"MPACCUSE","RunTime","Wrong arguments.");
					break;
				}
				break;
			}
			case 37: // mpenable
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccccp");
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				final String cast=varify(ctx,tt[2]);
				String p2=varify(ctx,tt[3]);
				final String m2=varify(ctx,tt[4]);
				Ability A=null;
				if(cast!=null)
				{
					if(newTarget instanceof MOB)
						A=((MOB)newTarget).fetchAbility(cast);
					if(A==null)
						A=getAbility(cast);
					if(A==null)
					{
						final ExpertiseLibrary.ExpertiseDefinition D=CMLib.expertises().findDefinition(cast,false);
						if(D==null)
							logError(ctx,"MPENABLE","Syntax","Unknown skill/expertise: "+cast);
						else
						if((newTarget!=null)&&(newTarget instanceof MOB))
							((MOB)newTarget).addExpertise(D.ID());
					}
				}
				if((newTarget!=null)
				&&(A!=null)
				&&(newTarget instanceof MOB))
				{
					if(!((MOB)newTarget).isMonster())
						Log.sysOut("Scripting",newTarget.Name()+" was MPENABLED with "+A.Name());
					if(p2.trim().startsWith("++"))
						p2=""+(CMath.s_int(p2.trim().substring(2))+A.proficiency());
					else
					if(p2.trim().startsWith("--"))
						p2=""+(A.proficiency()-CMath.s_int(p2.trim().substring(2)));
					A.setProficiency(CMath.s_int(p2.trim()));
					A.setMiscText(m2);
					if(((MOB)newTarget).fetchAbility(A.ID())==null)
					{
						((MOB)newTarget).addAbility(A);
						A.autoInvocation((MOB)newTarget, false);
					}
				}
				break;
			}
			case 38: // mpdisable
			{
				if(tt==null)
					tt=parseBits(ctx,"Ccr");
				final Environmental newTarget=getArgumentMOB(tt[1],ctx);
				final String cast=varify(ctx,tt[2]);
				if((newTarget!=null)&&(newTarget instanceof MOB))
				{
					final Ability A=((MOB)newTarget).findAbility(cast);
					if(A!=null)
						((MOB)newTarget).delAbility(A);
					if((!((MOB)newTarget).isMonster())&&(A!=null))
						Log.sysOut("Scripting",newTarget.Name()+" was MPDISABLED with "+A.Name());
					final ExpertiseLibrary.ExpertiseDefinition D=CMLib.expertises().findDefinition(cast,false);
					if((newTarget instanceof MOB)&&(D!=null))
						((MOB)newTarget).delExpertise(D.ID());
				}
				break;
			}
			case Integer.MIN_VALUE:
				logError(ctx,cmd.toUpperCase(),"Syntax","Unexpected prog start -- missing '~'?");
				break;
			default:
				if(cmd.length()>0)
				{
					final List<String> V=CMParms.parse(varify(ctx,s));
					if((V.size()>0)
					&&(ctx.monster!=null))
						ctx.monster.doCommand(V,MUDCmdProcessor.METAFLAG_MPFORCED);
				}
				break;
			}
			//if((ctx.parent!=null)&&(ctx.line==script.size()-1))
			//	ctx = ctx.pop();
		}
		tickStatus=Tickable.STATUS_END;
		return null;
	}

	protected static final List<SubScript> empty=new ReadOnlyVector<SubScript>();

	@Override
	public String getScriptResourceKey()
	{
		if(scriptKey == null)
			return ""+this;
		return scriptKey;
	}

	public void bumpUpCache(final String key)
	{
		if((key != null)
		&& (key.length()>0)
		&& (!key.equals("*")))
		{
			synchronized(counterCache)
			{
				if(!counterCache.containsKey(key))
					counterCache.put(key, new AtomicInteger(0));
				counterCache.get(key).addAndGet(1);
			}
		}
	}

	public void bumpUpCache()
	{
		synchronized(this)
		{
			final Object ref=cachedRef;
			if((ref != this)
			&&(this.scriptKey!=null)
			&&(this.scriptKey.length()>0))
			{
				bumpUpCache(getScriptResourceKey());
				bumpUpCache(scope);
				bumpUpCache(defaultQuestName);
				cachedRef=this;
			}
		}
	}

	public boolean bumpDownCache(final String key)
	{
		if((key != null)
		&& (key.length()>0)
		&& (!key.equals("*")))
		{
			if((key != null)
			&&(key.length()>0)
			&&(!key.equals("*")))
			{
				synchronized(counterCache)
				{
					if(counterCache.containsKey(key))
					{
						if(counterCache.get(key).addAndGet(-1) <= 0)
						{
							counterCache.remove(key);
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	protected void bumpDownCache()
	{
		synchronized(this)
		{
			final Object ref=cachedRef;
			if(ref == this)
			{
				if(bumpDownCache(getScriptResourceKey()))
				{
					for(final Resources R : Resources.all())
						R._removeResource(getScriptResourceKey());
				}
				if(bumpDownCache(scope))
				{
					for(final Resources R : Resources.all())
						R._removeResource("VARSCOPE-"+scope.toUpperCase().trim());
				}
				if(bumpDownCache(defaultQuestName))
				{
					for(final Resources R : Resources.all())
						R._removeResource("VARSCOPE-"+defaultQuestName.toUpperCase().trim());
				}
			}
		}
	}

	@Override
	protected void finalize() throws Throwable
	{
		bumpDownCache();
		super.finalize();
	}

	protected List<SubScript> getScripts(final Environmental scriptedE)
	{
		if(CMSecurity.isDisabled(CMSecurity.DisFlag.SCRIPTABLE)||CMSecurity.isDisabled(CMSecurity.DisFlag.SCRIPTING))
			return empty;
		@SuppressWarnings("unchecked")
		List<SubScript> scripts=(List<SubScript>)Resources.getResource(getScriptResourceKey());
		if(scripts==null)
		{
			delayTargetTimes.clear();
			everyNext.clear();
			lastTimeProgsDone.clear();
			lastDayProgsDone.clear();
			oncesDone.clear();
			String scr=getScript();
			scr=swapOutsideScripts(scr,'`','\'');
			scripts=parseScripts(scriptedE,scr);
			if(CMSecurity.isDebugging(DbgFlag.SCRIPTTRACE))
				Log.debugOut("Scripting engine parse "+scripts.size()+" scripts");
			Resources.submitResource(getScriptResourceKey(),scripts);
		}
		if(pendingState!=null)
		{
			// taken once, though two threads may read the script at once
			final String state;
			synchronized(stateLock)
			{
				state=pendingState;
				pendingState=null;
			}
			if(state!=null)
				applyState(scriptedE,scripts,state);
		}
		if(scriptedE!=globalsHost)
			registerGlobalTriggers(scriptedE,scripts);
		return scripts;
	}

	/**
	 * Returns a hash of the parsed script, which saved state is tied to.
	 * @param scripts the parsed script
	 * @return the hash
	 */
	protected String scriptHash(final List<SubScript> scripts)
	{
		final StringBuilder all=new StringBuilder();
		for(final SubScript script : scripts)
		{
			for(final ScriptLn line : script)
				all.append(line.first).append('\n');
			all.append("~\n");
		}
		return Integer.toHexString(all.toString().hashCode());
	}

	/**
	 * Returns whether a script runs once each time the game loads rather than
	 * once a game: ONCE_PROG BOOT.
	 * @param script the script
	 * @return true if it runs once a load
	 */
	protected boolean isOncePerLoad(final SubScript script)
	{
		if(script.getTriggerCode()!=6) // once_prog
			return false;
		final List<String> words=CMParms.parse(script.getTriggerLine());
		return (words.size()>1)&&(words.get(1).equalsIgnoreCase("BOOT"));
	}

	private static void appendNumbers(final StringBuilder str, final String tag, final Map<Integer,?> map)
	{
		final StringBuilder part=new StringBuilder("");
		synchronized(map)
		{
			for(final Map.Entry<Integer,?> e : map.entrySet())
			{
				final Object v=e.getValue();
				final long n=(v instanceof int[])?((int[])v)[0]:((Number)v).longValue();
				part.append((part.length()>0)?",":"").append(e.getKey()).append(':').append(n);
			}
		}
		if(part.length()>0)
			str.append('<').append(tag).append('>').append(part).append("</").append(tag).append('>');
	}

	private static String nameOf(final Environmental E)
	{
		return (E==null)?"":CMLib.xml().parseOutAngleBrackets(E.Name());
	}

	@Override
	public String getStateXML()
	{
		final String state=pendingState;
		if(state!=null)
			return state; // restored, and not yet read: still as it was saved
		@SuppressWarnings("unchecked")
		final List<SubScript> scripts=(List<SubScript>)Resources.getResource(getScriptResourceKey());
		if(scripts==null)
			return "";
		final XMLLibrary xml=CMLib.xml();
		final StringBuilder str=new StringBuilder("");
		// which ONCE_PROGs and QUEST_TIME_PROGs have run, but ONCE_PROG BOOT
		final StringBuilder once=new StringBuilder("");
		for(final Integer I : new XVector<Integer>(oncesDone))
		{
			final int i=I.intValue();
			if((i>=0)&&(i<scripts.size())&&isOncePerLoad(scripts.get(i)))
				continue;
			once.append((once.length()>0)?",":"").append(i);
		}
		if(once.length()>0)
			str.append("<ONCE>").append(once).append("</ONCE>");
		// the hour each TIME_PROG and the day each DAY_PROG last ran, and the
		// ticks left on each DELAY_PROG
		appendNumbers(str,"TIME",lastTimeProgsDone);
		appendNumbers(str,"DAY",lastDayProgsDone);
		appendNumbers(str,"DELAY",delayTargetTimes);
		// ST24: each EVERY_PROG's next time
		final StringBuilder every=new StringBuilder("");
		synchronized(everyNext)
		{
			for(final Map.Entry<Integer,Long> e : everyNext.entrySet())
				every.append((every.length()>0)?",":"").append(e.getKey()).append(':').append(e.getValue());
		}
		if(every.length()>0)
			str.append("<EVERY>").append(every).append("</EVERY>");
		// MPNOTRIGGER: game time left on each
		final StringBuilder notrig=new StringBuilder("");
		synchronized(noTrigger)
		{
			for(final Map.Entry<Integer,Long> e : noTrigger.entrySet())
			{
				final long left=e.getValue().longValue()-GameTime.now();
				if(left>0)
					notrig.append((notrig.length()>0)?",":"").append(e.getKey()).append(':').append(left);
			}
		}
		if(notrig.length()>0)
			str.append("<NOTRIG>").append(notrig).append("</NOTRIG>");
		// what is queued to run: MPALARMs and delayed responses
		final StringBuilder q=new StringBuilder("");
		for(final ScriptableResponse SB : new XVector<ScriptableResponse>(que))
		{
			if((SB==null)||(SB.scr==null)||(SB.ctx==null))
				continue;
			int index=-1;
			for(int i=0;i<scripts.size();i++)
			{
				if(scripts.get(i)==SB.scr)
					index=i;
			}
			q.append("<Q T=\"").append(SB.triggerCode).append("\" D=\"").append(Math.max(0,SB.tickDelay))
			 .append(SB.scr.isFlagSet("MPWAIT")?"\" W=\"1":"") // SC7: a paused body
			 .append("\" I=\"").append(index).append("\">");
			if(index<0)
			{
				q.append("<LINES>");
				for(final ScriptLn line : SB.scr)
					q.append("<L>").append(xml.parseOutAngleBrackets(line.first)).append("</L>");
				q.append("</LINES>");
			}
			q.append(xml.convertXMLtoTag("SRC",nameOf(SB.ctx.source)));
			q.append(xml.convertXMLtoTag("MON",nameOf(SB.ctx.monster)));
			q.append(xml.convertXMLtoTag("TGT",nameOf(SB.ctx.target)));
			q.append(xml.convertXMLtoTag("PI",nameOf(SB.ctx.primaryItem)));
			q.append(xml.convertXMLtoTag("SI",nameOf(SB.ctx.secondaryItem)));
			q.append(xml.convertXMLtoTag("MSG",xml.parseOutAngleBrackets((SB.ctx.msg==null)?"":SB.ctx.msg)));
			if(SB.scr.isFlagSet("MPWAIT")) // SC7: its $0-$9 that are words
			{
				for(int i=0;i<10;i++)
				{
					if(SB.ctx.tmp[i] instanceof String)
						q.append("<T").append(i).append('>').append(xml.parseOutAngleBrackets((String)SB.ctx.tmp[i])).append("</T").append(i).append('>');
				}
			}
			q.append("</Q>");
		}
		if(q.length()>0)
			str.append("<QUE>").append(q).append("</QUE>");
		// the variable scope a script set for itself (MPSETINTERNAL SCOPE)
		if(scope.length()>0)
			str.append(xml.convertXMLtoTag("SCOPE",xml.parseOutAngleBrackets(scope)));
		if(varHostInstance) // SC4
			str.append("<VARHOST>INSTANCE</VARHOST>");
		// the script's own variables, when it has its own scope (INDIVIDUAL)
		if((resources!=null)&&(scope.equals("*")||scope.equals("INDIVIDUAL")))
		{
			final StringBuilder vars=new StringBuilder("");
			for(final Iterator<String> k=resources._findResourceKeys("SCRIPTVAR-");k.hasNext();)
			{
				final String key=k.next();
				if(!key.startsWith("SCRIPTVAR-"))
					continue;
				@SuppressWarnings("unchecked")
				final Map<String,String> H=(Map<String,String>)resources._getResource(key);
				if(H==null)
					continue;
				for(final Map.Entry<String,String> e : new XVector<Map.Entry<String,String>>(H.entrySet()))
				{
					vars.append("<V>").append(xml.convertXMLtoTag("H",xml.parseOutAngleBrackets(key.substring(10))))
						.append(xml.convertXMLtoTag("N",xml.parseOutAngleBrackets(e.getKey())))
						.append(xml.convertXMLtoTag("X",xml.parseOutAngleBrackets(e.getValue())))
						.append("</V>");
				}
			}
			if(vars.length()>0)
				str.append("<VARS>").append(vars).append("</VARS>");
		}
		if(str.length()==0)
			return "";
		return "<HASH>"+scriptHash(scripts)+"</HASH>"+str.toString();
	}

	@Override
	public void setStateXML(final String xml)
	{
		synchronized(stateLock)
		{
			pendingState=((xml==null)||(xml.trim().length()==0))?null:xml;
		}
	}

	/**
	 * Puts back saved state, if it was saved with the same script.
	 * @param host the scripted host
	 * @param scripts the parsed script
	 * @param xml the state, as getStateXML wrote it
	 */
	protected void applyState(final Environmental host, final List<SubScript> scripts, final String xml)
	{
		final XMLLibrary xmlLib=CMLib.xml();
		final List<XMLTag> tags=xmlLib.parseAllXML(xml);
		if(!scriptHash(scripts).equals(xmlLib.getValFromPieces(tags, "HASH")))
		{
			if(CMSecurity.isDebugging(DbgFlag.SCRIPTTRACE))
				Log.debugOut("Scripting engine: the script changed since its state was saved, so the state was dropped.");
			return;
		}
		final String onces=xmlLib.getValFromPieces(tags, "ONCE");
		if(onces.length()>0)
		{
			for(final String num : CMParms.parseCommas(onces,true))
			{
				final Integer I=Integer.valueOf(CMath.s_int(num));
				if((I.intValue()>=0)&&(I.intValue()<scripts.size())&&(!oncesDone.contains(I)))
					oncesDone.add(I);
			}
		}
		for(final String pair : CMParms.parseCommas(xmlLib.getValFromPieces(tags, "TIME"),true))
		{
			final int x=pair.indexOf(':');
			if(x>0)
				lastTimeProgsDone.put(Integer.valueOf(CMath.s_int(pair.substring(0,x))),Integer.valueOf(CMath.s_int(pair.substring(x+1))));
		}
		for(final String pair : CMParms.parseCommas(xmlLib.getValFromPieces(tags, "DAY"),true))
		{
			final int x=pair.indexOf(':');
			if(x>0)
				lastDayProgsDone.put(Integer.valueOf(CMath.s_int(pair.substring(0,x))),Integer.valueOf(CMath.s_int(pair.substring(x+1))));
		}
		for(final String pair : CMParms.parseCommas(xmlLib.getValFromPieces(tags, "DELAY"),true))
		{
			final int x=pair.indexOf(':');
			if(x>0)
				delayTargetTimes.put(Integer.valueOf(CMath.s_int(pair.substring(0,x))),new int[]{CMath.s_int(pair.substring(x+1))});
		}
		for(final String pair : CMParms.parseCommas(xmlLib.getValFromPieces(tags, "EVERY"),true))
		{
			final int x=pair.indexOf(':');
			if(x>0)
				everyNext.put(Integer.valueOf(CMath.s_int(pair.substring(0,x))),Long.valueOf(CMath.s_long(pair.substring(x+1))));
		}
		for(final String pair : CMParms.parseCommas(xmlLib.getValFromPieces(tags, "NOTRIG"),true))
		{
			final int x=pair.indexOf(':');
			if(x>0)
				noTrigger.put(Integer.valueOf(CMath.s_int(pair.substring(0,x))),Long.valueOf(GameTime.now()+CMath.s_long(pair.substring(x+1))));
		}
		final XMLTag queued=xmlLib.getPieceFromPieces(tags, "QUE");
		if((queued!=null)&&(host instanceof PhysicalAgent))
		{
			for(final XMLTag q : queued.contents())
			{
				final int index=CMath.s_int(q.getParmValue("I"));
				SubScript scr=null;
				if((index>=0)&&(index<scripts.size()))
					scr=scripts.get(index);
				else
				{
					final XMLTag lines=q.getPieceFromPieces("LINES");
					if(lines!=null)
					{
						scr=new SubScriptImpl(null);
						for(final XMLTag line : lines.contents())
							scr.add(new ScriptLn(xmlLib.restoreAngleBrackets(line.value()),null,null));
					}
				}
				if((scr==null)||(scr.size()==0))
					continue;
				final MOB monster=getMakeMOB(host);
				final MPContext ctx=new MPContext((PhysicalAgent)host,
					(monster==null)?findSavedMOB(host,q.getValFromPieces("MON")):monster,
					findSavedMOB(host,q.getValFromPieces("SRC")),
					findSavedThing(host,q.getValFromPieces("TGT")),
					findSavedItem(host,q.getValFromPieces("PI")),
					findSavedItem(host,q.getValFromPieces("SI")),
					xmlLib.restoreAngleBrackets(q.getValFromPieces("MSG")), null);
				if("1".equals(q.getParmValue("W"))) // SC7: a paused body, and its $0-$9
				{
					scr.setFlag("MPWAIT");
					for(int i=0;i<10;i++)
					{
						final XMLTag tv=q.getPieceFromPieces("T"+i);
						if(tv!=null)
							ctx.tmp[i]=xmlLib.restoreAngleBrackets(tv.value());
					}
				}
				que.add(new ScriptableResponse(CMath.s_int(q.getParmValue("T")),ctx,scr,Math.max(1,CMath.s_int(q.getParmValue("D")))));
			}
		}
		final String savedScope=xmlLib.restoreAngleBrackets(xmlLib.getValFromPieces(tags, "SCOPE"));
		if((savedScope.length()>0)&&(!savedScope.equals(scope)))
			setVarScope(savedScope);
		varHostInstance="INSTANCE".equalsIgnoreCase(xmlLib.getValFromPieces(tags,"VARHOST")); // SC4
		final XMLTag vars=xmlLib.getPieceFromPieces(tags, "VARS");
		if((vars!=null)&&(resources!=null)&&(scope.equals("*")||scope.equals("INDIVIDUAL")))
		{
			for(final XMLTag v : vars.contents())
			{
				final String key="SCRIPTVAR-"+xmlLib.restoreAngleBrackets(v.getValFromPieces("H"));
				@SuppressWarnings("unchecked")
				Map<String,String> H=(Map<String,String>)resources._getResource(key);
				if(H==null)
				{
					H=Collections.synchronizedMap(new HashMap<String,String>());
					resources._submitResource(key,H);
				}
				H.put(xmlLib.restoreAngleBrackets(v.getValFromPieces("N")),xmlLib.restoreAngleBrackets(v.getValFromPieces("X")));
			}
		}
	}

	/**
	 * Finds a mob a queued response named when it was saved: in the host's
	 * room, else a player of that name.
	 * @param host the scripted host
	 * @param name the name, as saved
	 * @return the mob, or null
	 */
	protected MOB findSavedMOB(final Environmental host, final String name)
	{
		final String n=CMLib.xml().restoreAngleBrackets(name);
		if(n.length()==0)
			return null;
		final Room R=CMLib.map().roomLocation(host);
		if(R!=null)
		{
			for(int i=0;i<R.numInhabitants();i++)
			{
				final MOB M=R.fetchInhabitant(i);
				if((M!=null)&&(M.Name().equals(n)))
					return M;
			}
		}
		return CMLib.players().getPlayer(n);
	}

	/**
	 * Finds an item a queued response named when it was saved: carried by
	 * the host, else in its room.
	 * @param host the scripted host
	 * @param name the name, as saved
	 * @return the item, or null
	 */
	protected Item findSavedItem(final Environmental host, final String name)
	{
		final String n=CMLib.xml().restoreAngleBrackets(name);
		if(n.length()==0)
			return null;
		if(host instanceof MOB)
		{
			final Item I=((MOB)host).findItem(null,n);
			if(I!=null)
				return I;
		}
		final Room R=CMLib.map().roomLocation(host);
		return (R==null)?null:R.findItem(null,n);
	}

	/**
	 * Finds what a queued response targeted when it was saved: a mob, else an
	 * item, else the host if it has that name.
	 * @param host the scripted host
	 * @param name the name, as saved
	 * @return the target, or null
	 */
	protected Environmental findSavedThing(final Environmental host, final String name)
	{
		final MOB M=findSavedMOB(host,name);
		if(M!=null)
			return M;
		final Item I=findSavedItem(host,name);
		if(I!=null)
			return I;
		return ((host!=null)&&(host.Name().equals(CMLib.xml().restoreAngleBrackets(name))))?host:null;
	}

	@Override
	public void registerHost(final PhysicalAgent host)
	{
		if(host!=null)
			getScripts(host);
	}

	/**
	 * Registers the host of scripts that react to world-wide events (logins,
	 * quits, level changes, channel messages) as a global handler for them,
	 * the first time the scripts meet their host, so that they hear the
	 * first such event wherever it happens, and after a restore.
	 * @param host the scripted host
	 * @param scripts its parsed scripts
	 */
	protected void registerGlobalTriggers(final Environmental host, final List<SubScript> scripts)
	{
		if(!(host instanceof PhysicalAgent))
			return;
		synchronized(registeredEvents)
		{
			if(host==globalsHost)
				return;
			if(globalsHost!=null)
				registeredEvents.clear();
			globalsHost=host;
			for(final SubScript script : scripts)
			{
				ScriptEvents.uses(script.getTriggerCode());
				ScriptEvents.register((PhysicalAgent)host,script.getTriggerCode());
				final int category;
				switch(script.getTriggerCode())
				{
				case 29: // login_prog
					category=CMMsg.TYP_LOGIN;
					break;
				case 30: // logoff_prog
					category=CMMsg.TYP_QUIT;
					break;
				case 32: // level_prog
					category=CMMsg.TYP_LEVEL;
					break;
				case 33: // channel_prog
					category=CMMsg.TYP_CHANNEL;
					break;
				case 10: // death_prog @GLOBAL (ST12)
				case 44: // kill_prog @GLOBAL
					if(!isGlobalLine(script.get(0).first))
						continue;
					category=CMMsg.TYP_DEATH;
					break;
				case 41: // execmsg_prog @GLOBAL (ST12)
				{
					if(!isGlobalLine(script.get(0).first))
						continue;
					final Integer I=CMMsg.Desc.getMSGTYPE_DESCS().get(CMParms.getCleanBit(script.get(0).first,2).toUpperCase());
					if(I==null)
						continue;
					category=I.intValue();
					break;
				}
				default:
					continue;
				}
				if(registeredEvents.add(Integer.valueOf(category)))
					CMLib.map().addGlobalHandler((PhysicalAgent)host,category);
			}
		}
	}

	/** ST12: the last message heard by @GLOBAL lines, and which of them heard it */
	protected CMMsg lastGlobalMsg = null;
	protected final Set<SubScript> globalHeard = Collections.newSetFromMap(new IdentityHashMap<SubScript,Boolean>());

	/**
	 * ST12: whether a trigger line starts with @GLOBAL.
	 * @param line the trigger line
	 * @return true if so
	 */
	protected static boolean isGlobalLine(final String line)
	{
		return (line!=null)&&CMParms.getCleanBit(line,1).equalsIgnoreCase("@GLOBAL");
	}

	/**
	 * ST12: a trigger line without its @GLOBAL.
	 * @param line the trigger line
	 * @return the line
	 */
	protected static String stripGlobal(final String line)
	{
		final int x=line.toUpperCase().indexOf("@GLOBAL");
		return (x<0)?line:(line.substring(0,x)+line.substring(x+7));
	}

	/**
	 * ST12: whether an @GLOBAL line hears a message for the first time: a
	 * host in the room it happens in hears it twice, from the room and the
	 * world.
	 * @param msg the message
	 * @param script the trigger line's script
	 * @return true the first time
	 */
	protected boolean firstGlobalHearing(final CMMsg msg, final SubScript script)
	{
		synchronized(globalHeard)
		{
			if(msg!=lastGlobalMsg)
			{
				lastGlobalMsg=msg;
				globalHeard.clear();
			}
			return globalHeard.add(script);
		}
	}

	/**
	 * ST12: whether an @GLOBAL DEATH_PROG or KILL_PROG line's mask fits.
	 * @param line the trigger line
	 * @param M the one the mask is on
	 * @return true if it fits, or there is no mask
	 */
	protected boolean globalMaskOk(final String line, final MOB M)
	{
		final String mask=CMParms.getPastBitClean(line,1).trim();
		return (mask.length()==0)||((M!=null)&&CMLib.masking().maskCheck(mask,M,true));
	}

	/** ST15: whether the host's fight was on at the last tick, and with whom */
	protected boolean combatWas = false;
	protected MOB combatWith = null;

	/**
	 * ST15: how a host's fight changed since its last tick: START, END (both
	 * sides alive), or null.  A mob's own fight, an item's owner's, or any
	 * fight in a room.
	 * @param host the host
	 * @return START, END or null
	 */
	protected String combatChange(final PhysicalAgent host)
	{
		boolean now=false;
		MOB foe=null;
		MOB me=null;
		if(host instanceof MOB)
			me=(MOB)host;
		else
		if((host instanceof Item)&&(((Item)host).owner() instanceof MOB))
			me=(MOB)((Item)host).owner();
		if(me!=null)
		{
			now=me.isInCombat();
			foe=me.getVictim();
		}
		else
		if(host instanceof Room)
		{
			for(final Enumeration<MOB> m=((Room)host).inhabitants();m.hasMoreElements();)
			{
				final MOB M=m.nextElement();
				if((M!=null)&&M.isInCombat())
				{
					now=true;
					foe=M;
					break;
				}
			}
		}
		else
			return null;
		String event=null;
		if(now&&(!combatWas))
			event="START";
		else
		if((!now)&&combatWas
		&&(combatWith!=null)&&(!combatWith.amDead())&&(!combatWith.amDestroyed())
		&&((me==null)||(!me.amDead())))
			event="END";
		combatWas=now;
		if(foe!=null)
			combatWith=foe;
		return event;
	}

	/**
	 * ST15: whether a COMBAT_PROG trigger line fits.
	 * @param t the trigger line: COMBAT_PROG, START, END, FLEE or ANY, a mask on the opponent
	 * @param kind what happened
	 * @param foe the opponent
	 * @return true if it fits
	 */
	protected boolean combatMatches(final String[] t, final String kind, final MOB foe)
	{
		if(t.length<2)
			return false;
		if((!t[1].equals("ANY"))&&(!t[1].equals(kind)))
			return false;
		final String mask=(t.length>2)?t[2].trim():"";
		return (mask.length()==0)||((foe!=null)&&CMLib.masking().maskCheck(mask,foe,true));
	}

	/**
	 * ST21: MEMORY(npc who MET|COUNT|LASTSEEN|DAYSSINCE|LASTTOPIC).
	 * @param ctx the script's context
	 * @param npcArg the NPC
	 * @param whoArg the character
	 * @param fieldArg which
	 * @return the value
	 */
	protected String memoryText(final MPContext ctx, final String npcArg, final String whoArg, final String fieldArg)
	{
		final Environmental npc=getArgumentMOB(npcArg,ctx);
		final Environmental who=getArgumentMOB(whoArg,ctx);
		if(!(npc instanceof MOB))
			return "";
		final String name=(who!=null)?who.Name():varify(ctx,whoArg);
		final long[] rec=ScriptEvents.memory((MOB)npc,name);
		final String field=varify(ctx,fieldArg).toUpperCase().trim();
		switch(field)
		{
		case "MET":
			return (rec[0]>0)?"true":"false";
		case "COUNT":
			return ""+rec[0];
		case "LASTSEEN":
			return ""+rec[2];
		case "DAYSSINCE":
			return (rec[0]==0)?"-1":(""+((GameTime.now()-rec[2])/Math.max(1,ScriptEvents.gameDayMillis())));
		case "LASTTOPIC":
			return ScriptEvents.lastTopic((MOB)npc,name);
		default:
			return "";
		}
	}

	/** SC1 and on: the functions from this code on go through extraValue and extraTest */
	protected static final int EXTRA_FUNCS_FROM = 136;
	/** SC1 and on: the commands from this code on go through extraCommand */
	protected static final int EXTRA_METHODS_FROM = 103;

	/**
	 * Splits a varified text at its last word (quotes keep words together).
	 * @param text the text
	 * @return the text before it, and the word
	 */
	/**
	 * SC5: a message type's name, as CNCLMSG_PROG and EXECMSG_PROG take them.
	 * @param code the type
	 * @return its name
	 */
	protected static String msgTypeName(final int code)
	{
		return ((code>=0)&&(code<CMMsg.TYPE_DESCS.length))?CMMsg.TYPE_DESCS[code]:(""+code);
	}

	/**
	 * SC8: the character at the terminal, the one the player is playing now:
	 * the terminal of the source's player, or the game's only one.
	 * @param ctx the script's context
	 * @return the character, or null
	 */
	protected MOB terminalMob(final MPContext ctx)
	{
		Session T=null;
		if((ctx.source!=null)&&(ctx.source.playerStats()!=null))
			T=Party.terminalOf(ctx.source);
		if(T==null)
		{
			for(final Session S : CMLib.sessions().allIterable())
			{
				if((S!=null)&&(!Party.isPartySession(S))&&(S.mob()!=null))
				{
					T=S;
					break;
				}
			}
		}
		return (T==null)?null:T.mob();
	}

	/**
	 * SC8: whether a character's own session is the silent one of a party
	 * member in the background, with no window of its own.
	 * @param M the character
	 * @return true if what is said to it must go to the terminal
	 */
	protected static boolean inBackground(final MOB M)
	{
		return (M!=null)&&Party.isBackground(M)&&(!Party.isWindowed(M));
	}

	/**
	 * SC8: where a prompt to a character goes: its session, or the terminal's
	 * for a party member in the background.
	 * @param M the character
	 * @return the session, or null
	 */
	protected static Session promptSession(final MOB M)
	{
		if(inBackground(M))
		{
			final Session T=Party.terminalOf(M);
			if(T!=null)
				return T;
		}
		return (M==null)?null:M.session();
	}

	/**
	 * SC8: a prompt's text, named for the member when it goes to the terminal.
	 * @param M the character it is for
	 * @param prompt the text
	 * @return the text to show
	 */
	protected static String promptFor(final MOB M, final String prompt)
	{
		return inBackground(M)?("["+M.Name()+"] "+prompt):prompt;
	}

	/**
	 * SC8: MPECHOAT to a party member in the background: the terminal sees
	 * it too, named for the member.
	 * @param ctx the script's context
	 * @param M the member
	 * @param str what was said
	 */
	protected void tellTerminal(final MPContext ctx, final MOB M, final String str)
	{
		if(!inBackground(M))
			return;
		final Session T=Party.terminalOf(M);
		if((T!=null)&&(T.mob()!=null)&&(T.mob()!=M))
			T.mob().tell(ctx.monster,M,null,"["+M.Name()+"] "+str);
	}

	protected static String[] lastWord(final String text)
	{
		final String t=text.trim();
		if(t.length()==0)
			return new String[]{"",""};
		int end=t.length();
		int start;
		if((t.charAt(end-1)=='\'')||(t.charAt(end-1)=='`')||(t.charAt(end-1)=='"'))
		{
			final char q=t.charAt(end-1);
			start=t.lastIndexOf(q,end-2);
			if(start<0)
				start=0;
			return new String[]{t.substring(0,start).trim(),t.substring(start+1,end-1)};
		}
		start=t.lastIndexOf(' ');
		return new String[]{(start<0)?"":t.substring(0,start).trim(),t.substring(start+1)};
	}

	/**
	 * The newer functions' values (SC1 and on): $%NAME(parms)%.
	 * @param ctx the script's context
	 * @param name the function
	 * @param parms what is in its parentheses, not yet varified
	 * @return its value
	 */
	protected String extraValue(final MPContext ctx, final String name, final String parms)
	{
		final String v=varify(ctx,parms).trim();
		final TimeClock clock;
		{
			final Area A=CMLib.map().areaLocation(ctx.scripted);
			clock=(A!=null)?A.getTimeObj():CMLib.time().globalClock();
		}
		switch(name.toUpperCase())
		{
		case "LISTJOIN":
		{
			final String[] p=lastWord(v);
			return String.join(p[1],ScriptLib.listOf(p[0]));
		}
		case "LISTHAS":
		case "LISTFIND":
		{
			final String[] p=lastWord(v);
			final List<String> L=ScriptLib.listOf(p[0]);
			for(int i=0;i<L.size();i++)
			{
				if(L.get(i).equalsIgnoreCase(p[1]))
					return name.equalsIgnoreCase("LISTHAS")?"true":(""+i);
			}
			return name.equalsIgnoreCase("LISTHAS")?"false":"-1";
		}
		case "LISTCOUNT":
			return ""+ScriptLib.listOf(v).size();
		case "LISTGET":
		{
			final String[] p=lastWord(v);
			final List<String> L=ScriptLib.listOf(p[0]);
			final int i=CMath.s_int(p[1]);
			return ((i>=0)&&(i<L.size()))?L.get(i):"";
		}
		case "LISTPICK":
		{
			final List<String> L=ScriptLib.listOf(v);
			return L.isEmpty()?"":L.get(CMLib.dice().roll(1,L.size(),-1));
		}
		case "ELAPSED": // SC2: ELAPSED(stamp HOURS|DAYS|WEEKS|MONTHS): the time since a stamp
		{
			final String[] p=lastWord(v);
			final boolean unitGiven=!CMath.isNumber(p[1]);
			final long stamp=CMath.s_long(unitGiven?p[0]:p[1]);
			final long per=ScriptEvents.everyHours(1,unitGiven?p[1]:"HOURS",clock);
			return ""+((ScriptEvents.clockHours(clock)-stamp)/per);
		}
		case "WEEKDAY": // SC2
			return ScriptEvents.weekday(clock);
		case "TICKS": // SC2: TICKS(3 DAYS): ticks in so much game time, for timed tattoos
		{
			final String[] p=lastWord(v);
			final boolean unitGiven=!CMath.isNumber(p[1]);
			final long n=CMath.s_long(unitGiven?p[0]:p[1]);
			final long hours=n*ScriptEvents.everyHours(1,unitGiven?p[1]:"HOURS",clock);
			return ""+((hours*CMProps.getMillisPerMudHour())/Math.max(1,CMProps.getTickMillis()));
		}
		case "UPPER": // SC10
			return v.toUpperCase();
		case "LOWER":
			return v.toLowerCase();
		case "CAP":
			return CMStrings.capitalizeFirstLetter(v);
		case "LEN":
			return ""+v.length();
		case "TRIM":
			return v.trim();
		case "SUBSTR": // SUBSTR(s start [len]), from 0
		{
			String[] p=lastWord(v);
			String s=p[0];
			int start;
			int len=-1;
			final String[] q=lastWord(p[0]);
			if(CMath.isInteger(q[1])&&CMath.isInteger(p[1]))
			{
				s=q[0];
				start=CMath.s_int(q[1]);
				len=CMath.s_int(p[1]);
			}
			else
				start=CMath.s_int(p[1]);
			start=Math.max(0,Math.min(start,s.length()));
			final int end=(len<0)?s.length():Math.min(s.length(),start+len);
			return s.substring(start,end);
		}
		case "REPLACE": // REPLACE(s 'old' 'new')
		{
			final String[] p=lastWord(v);
			final String[] q=lastWord(p[0]);
			return (q[1].length()==0)?q[0]:q[0].replace(q[1],p[1]);
		}
		case "PADRIGHT": // PADRIGHT(s n)
		{
			final String[] p=lastWord(v);
			return CMStrings.padRight(p[0],CMath.s_int(p[1]));
		}
		case "ANAME":
			return CMLib.english().startWithAorAn(v);
		case "PLURAL": // PLURAL(s [n])
		{
			final String[] p=lastWord(v);
			if(CMath.isInteger(p[1]))
				return (CMath.s_int(p[1])==1)?p[0]:CMLib.english().makePlural(p[0]);
			return CMLib.english().makePlural(v);
		}
		case "STRSTARTS":
		case "STRENDS":
		case "STRPOS":
		{
			final String[] p=lastWord(v);
			final String s=p[0].toLowerCase();
			final String x=p[1].toLowerCase();
			if(name.equalsIgnoreCase("STRPOS"))
				return ""+s.indexOf(x);
			return (name.equalsIgnoreCase("STRSTARTS")?s.startsWith(x):s.endsWith(x))?"true":"false";
		}
		case "DICE": // SC11: DICE(3d6+2)
			return ""+ScriptLib.dice(v);
		case "PICK":
		{
			final List<String> L=CMParms.parse(v);
			return L.isEmpty()?"":L.get(CMLib.dice().roll(1,L.size(),-1));
		}
		case "WPICK":
			return ScriptLib.weightedPick(v);
		case "ROLLNORMAL": // ROLLNORMAL(number die modifier)
		{
			final List<String> L=CMParms.parse(v);
			if(L.size()<2)
				return "0";
			return ""+CMLib.dice().rollNormalDistribution(CMath.s_int(L.get(0)),CMath.s_int(L.get(1)),(L.size()>2)?CMath.s_int(L.get(2)):0);
		}
		case "CLAMP": // CLAMP(v lo hi)
		{
			final List<String> L=CMParms.parse(v);
			if(L.size()<3)
				return v;
			final double x=Math.max(CMath.s_double(L.get(1)),Math.min(CMath.s_double(L.get(2)),CMath.s_double(L.get(0))));
			return (x==Math.rint(x))?(""+(long)x):(""+x);
		}
		case "ABS":
		{
			final double x=Math.abs(CMath.s_double(v));
			return (x==Math.rint(x))?(""+(long)x):(""+x);
		}
		case "NUMTAGGED": // SC12
		case "TAGGED":
		{
			int n=0;
			for(final Enumeration<Taggable> e=CMLib.map().getTaggedObjects(v);e.hasMoreElements();)
			{
				if(e.nextElement()!=null)
					n++;
			}
			return name.equalsIgnoreCase("TAGGED")?((n>0)?"true":"false"):(""+n);
		}
		case "TAGS": // TAGS(target), as HASTAG(target) gives them
		{
			final Environmental E=getArgumentItem(parms.trim(),ctx);
			if(!(E instanceof Taggable))
				return "";
			final List<String> tags=new ArrayList<String>();
			for(final Enumeration<String> e=((Taggable)E).tags();e.hasMoreElements();)
				tags.add(e.nextElement());
			return ScriptLib.listText(tags);
		}
		case "CATALOG": // SC3: CATALOG(name): a catalog mob or item so called, and a copy may be made
		case "CATALOGCOUNT": // CATALOGCOUNT(name): its copies in the world
		{
			Physical P=ScriptLib.catalogEntry(v,true);
			if(P==null)
				P=ScriptLib.catalogEntry(v,false);
			if(name.equalsIgnoreCase("CATALOG"))
				return ((P!=null)&&(!ScriptLib.catalogFull(P)))?"true":"false";
			return ""+ScriptLib.catalogCount(P);
		}
		case "STATE": // SC4: STATE(thing name): the value kept on that one object
		{
			final Environmental O=getArgumentItem(CMParms.getCleanBit(parms,0),ctx);
			final String var=varify(ctx,CMParms.getPastBitClean(parms,0)).trim();
			if(!(O instanceof Physical)||(var.length()==0))
				return "";
			return ObjectVars.get(ObjectVars.hostOf((Physical)O),var);
		}
		case "MSG": // SC5: MSG(TYPE|VALUE|SOURCE|TARGET|TOOL|TOOLID|STEXT|TTEXT|OTEXT|SMINOR|TMINOR|OMINOR)
		{
			final CMMsg m=ctx.cmsg;
			if(m==null)
				return "";
			switch(v.toUpperCase())
			{
			case "TYPE":
				return msgTypeName((m.targetMinor()!=0)?m.targetMinor():(m.sourceMinor()!=0)?m.sourceMinor():m.othersMinor());
			case "VALUE":
				return ""+m.value();
			case "SOURCE":
				return (m.source()==null)?"":m.source().Name();
			case "TARGET":
				return (m.target()==null)?"":m.target().Name();
			case "TOOL":
				return (m.tool()==null)?"":m.tool().Name();
			case "TOOLID":
				return (m.tool()==null)?"":m.tool().ID();
			case "STEXT":
				return (m.sourceMessage()==null)?"":m.sourceMessage();
			case "TTEXT":
				return (m.targetMessage()==null)?"":m.targetMessage();
			case "OTEXT":
				return (m.othersMessage()==null)?"":m.othersMessage();
			case "SMINOR":
				return msgTypeName(m.sourceMinor());
			case "TMINOR":
				return msgTypeName(m.targetMinor());
			case "OMINOR":
				return msgTypeName(m.othersMinor());
			default:
				logError(ctx,"MSG","Syntax","unknown part "+v);
				return "";
			}
		}
		case "ISPLAYED": // SC8: ISPLAYED(who): the character at the terminal, or one with a window of its own
		{
			final Environmental W=getArgumentMOB(parms.trim(),ctx);
			if(!(W instanceof MOB))
				return "false";
			return ((W==terminalMob(ctx))||Party.isWindowed((MOB)W))?"true":"false";
		}
		case "PHASE": // C3: PHASE(who): its BossPhases' phase, from 1, or 0 for none
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			if(W instanceof MOB)
			{
				for(final Enumeration<Behavior> b=((MOB)W).behaviors();b.hasMoreElements();)
				{
					final Behavior B=b.nextElement();
					if((B!=null)&&B.ID().equals("BossPhases"))
						return B.getStat("PHASE");
				}
			}
			return "0";
		}
		case "ALARM": // C5: ALARM(who name): the ticks before its named alarm goes off, 0 for none
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			final String an=varify(ctx,CMParms.getCleanBit(parms,1)).trim();
			if(!(W instanceof PhysicalAgent)||(an.length()==0))
				return "0";
			return ""+Math.max(0,Telegraph.ticksLeft((PhysicalAgent)W,an));
		}
		case "THREAT": // C18: THREAT(npc who): their threat to it
		{
			final Environmental N=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(parms,1),ctx);
			if((!(N instanceof MOB))||(!(W instanceof MOB)))
				return "0";
			return ""+Threat.get((MOB)N,(MOB)W);
		}
		case "TOPTHREAT": // C18: TOPTHREAT(npc): the one here with the most threat to it, or nothing
		case "WEAKEST": // C18: WEAKEST(npc): the enemy here with the least hit points left, in percent
		{
			final Environmental N=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			if(!(N instanceof MOB))
				return "";
			final MOB M=name.equalsIgnoreCase("TOPTHREAT")?Threat.top((MOB)N):Threat.weakest((MOB)N);
			return (M==null)?"":M.Name();
		}
		case "OPPONENT": // C18: OPPONENT(npc 'MASK=zapper'): the first enemy here the mask fits
		{
			final Environmental N=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			String mask=varify(ctx,CMParms.getPastBit(parms,0)).trim();
			while((mask.length()>1)&&("'`\"".indexOf(mask.charAt(0))>=0)&&("'`\"".indexOf(mask.charAt(mask.length()-1))>=0))
				mask=mask.substring(1,mask.length()-1).trim(); // quoted ('...', which scripts may hand over as `...`)
			if(mask.toUpperCase().startsWith("MASK="))
				mask=mask.substring(5).trim();
			if(!(N instanceof MOB))
				return "";
			final MOB M=Threat.opponent((MOB)N,mask);
			return (M==null)?"":M.Name();
		}
		case "RANK": // C15: RANK(who): its Prop_MobRank's rank, else NORMAL
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			return (W instanceof MOB)?MobRanks.rankOf((MOB)W):"NORMAL";
		}
		case "CHECK": // N29: CHECK(who STAT|skill difficulty [modifier]): d20 plus the stat's or skill's bonus, against the difficulty
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			if(!(W instanceof MOB))
				return "false";
			final MOB who=(MOB)W;
			final String what=varify(ctx,CMParms.getCleanBit(parms,1)).trim();
			final int difficulty=CMath.s_int(varify(ctx,CMParms.getCleanBit(parms,2)).trim());
			final String modText=varify(ctx,CMParms.getPastBitClean(parms,2)).trim();
			int modifier=0;
			if(modText.length()>0)
			{
				try
				{
					modifier=(int)Math.round(CMath.parseMathExpression(modText.replace("+"," +").trim()));
				}
				catch(final Exception e)
				{
					logError(ctx,"CHECK","Syntax","the modifier is not a number: "+modText);
				}
			}
			int bonus=0;
			String label=what;
			int stat=-1;
			for(final int c : CharStats.CODES.BASECODES())
			{
				final String nm=CharStats.CODES.NAME(c);
				if(nm.equalsIgnoreCase(what)||CharStats.CODES.SHORTNAME(c).equalsIgnoreCase(what)
				||((what.length()>=3)&&nm.toUpperCase().startsWith(what.toUpperCase())))
				{
					stat=c; // CHA, CH or CHARISMA
					break;
				}
			}
			if(stat>=0)
			{
				bonus=(who.charStats().getStat(stat)-10)/2;
				label=CMStrings.capitalizeAndLower(CharStats.CODES.NAME(stat));
			}
			else
			{
				final Ability A=who.fetchAbility(what);
				if(A!=null)
				{
					bonus=A.proficiency()/10;
					label=A.name();
				}
			}
			final int roll=CMLib.dice().roll(1,20,0);
			final int total=roll+bonus+modifier;
			final boolean ok=total>=difficulty;
			setVar(who.Name(),"CHECKMARGIN","="+(total-difficulty));
			final String show=CMProps.instance().getStr("CHECKROLLS").trim().toUpperCase();
			if(show.equals("FULL"))
				who.tell(L("[@x1 check: @x2 (d20 @x3, @x4, @x5, against @x6)]",label,ok?L("success"):L("failure"),""+roll,
					((bonus>=0)?"+":"")+bonus,((modifier>=0)?"+":"")+modifier,""+difficulty));
			else
			if(!show.equals("OFF"))
				who.tell(L("[@x1 check: @x2]",label,ok?L("success"):L("failure")));
			return ok?"true":"false";
		}
		case "AFFINITY": // N15: AFFINITY(npc who [BAND]): the NPC's opinion of them, a number or its band
		{
			final Environmental N=getArgumentMOB(CMParms.getCleanBit(parms,0),ctx);
			if(!(N instanceof MOB))
				return "0";
			final String w=CMParms.getCleanBit(parms,1);
			final Environmental W=getArgumentMOB(w,ctx);
			final long val=NpcAffinity.get((MOB)N,(W instanceof MOB)?W.Name():varify(ctx,w));
			return CMParms.getCleanBit(parms,2).equalsIgnoreCase("BAND")?NpcAffinity.band(val):(""+val);
		}
		case "ARG": // SC13: ARG(name): what LOAD=file NAME=value gave the script, or the file's ARGDEFAULT
		case "PARM": // N7: PARM(name): a generic behaviour's parameter, or its default
		{
			final String a=parmValue(ctx.scripted,v);
			return (a==null)?"":a;
		}
		case "QUESTWINS": // Q5: QUESTWINS(who quest): how many times they won it
		case "QUESTWINAGE": // QUESTWINAGE(who quest): game hours since they last won it, or -1
		{
			final List<String> w=CMParms.parse(parms.trim());
			if(w.size()<2)
				return "0";
			final Environmental W=getArgumentMOB(w.get(0),ctx);
			final String who=(W instanceof MOB)?W.Name():varify(ctx,w.get(0));
			final String quest=questNameFor(varify(ctx,CMParms.combine(w,1)));
			if(name.equalsIgnoreCase("QUESTWINS"))
				return ""+QuestRecords.wins(quest,who);
			return ""+QuestRecords.winAgeHours(quest,who);
		}
		case "QUESTSTATUS": // Q4: QUESTSTATUS(who quest): ACTIVE, WON, FAILED, ABANDONED, EXPIRED or ""
		case "QUESTOUTCOME": // QUESTOUTCOME(who quest): the outcome word, or why it failed
		{
			final List<String> w=CMParms.parse(parms.trim());
			if(w.size()<2)
				return "";
			final Environmental W=getArgumentMOB(w.get(0),ctx);
			final String who=(W instanceof MOB)?W.Name():varify(ctx,w.get(0));
			final String quest=questNameFor(varify(ctx,CMParms.combine(w,1)));
			if(name.equalsIgnoreCase("QUESTSTATUS"))
				return QuestRecords.status(quest,(W instanceof MOB)?(MOB)W:null,who);
			return QuestRecords.outcome(quest,who);
		}
		case "QOBJECTIVE": // Q3: QOBJECTIVE(who quest id): the count, DONE or FAILED, or ""
		{
			final List<String> w=CMParms.parse(parms.trim());
			if(w.size()<3)
				return "";
			final Environmental W=getArgumentMOB(w.get(0),ctx);
			if(!(W instanceof MOB))
				return "";
			return QuestJournal.value((MOB)W,questNameFor(varify(ctx,w.get(1))),varify(ctx,w.get(2)));
		}
		case "JS": // JS15: JS(expr), JS$(expr): a JavaScript expression's value, as text
		case "JS$":
			return jsText(jsExpr(ctx,v));
		default: // the newer functions end here
			logError(ctx,name,"Syntax","unknown function "+name);
			return "";
		}
	}

	/**
	 * The newer functions as tests (SC1 and on): a value compared, as
	 * LISTCOUNT(list > 2), or true or false, as LISTHAS(list word).
	 * @param ctx the script's context
	 * @param name the function
	 * @param inner what is in its parentheses
	 * @return the test's result
	 */
	protected boolean extraTest(final MPContext ctx, final String name, final String inner)
	{
		if(name.equalsIgnoreCase("JS")||name.equalsIgnoreCase("JS$")) // JS15: IF JS(expr): true when it is truthy
		{
			final Object r=jsExpr(ctx,varify(ctx,inner).trim());
			return (r!=null)&&Context.toBoolean(r);
		}
		final List<String> words=CMParms.parse(inner);
		final String[] c=ScriptLib.splitCompare(words);
		if(c==null)
		{
			final String r=extraValue(ctx,name,inner).trim();
			if(name.equalsIgnoreCase("STATE")||name.equalsIgnoreCase("ARG")||name.equalsIgnoreCase("PARM")) // SC4, SC13, N7: set, and not 0 or false
				return (r.length()>0)&&(!r.equals("0"))&&(!r.equalsIgnoreCase("false"));
			return "true".equalsIgnoreCase(r);
		}
		final int at=inner.indexOf(" "+c[1]+" ");
		final String left=(at>=0)?inner.substring(0,at):c[0];
		final String right=(at>=0)?inner.substring(at+c[1].length()+2):c[2];
		return ScriptLib.compare(extraValue(ctx,name,left),c[1],varify(ctx,right));
	}

	/** JS15: the scope kept for this engine's JS() and JS$() */
	protected JScriptEvent jsExprScope = null;

	/**
	 * JS15: a <SCRIPT>'s scope: the MOBPROG bridge, host(), source() and the
	 * rest, cancel(), and what every JavaScript scope has.
	 * @param cx the context
	 * @param ctx the script's context
	 * @return the scope
	 */
	protected JScriptEvent jsEventScope(final Context cx, final MPContext ctx)
	{
		final JScriptEvent scope = new JScriptEvent(this,ctx);
		scope.initStandardObjects(cx, false);
		JSScope.prepare(cx, scope, ctx.scripted); // JS3, JS6: require(), cm, include()
		final String[] names = {
			"host",
			"source",
			"target",
			"monster",
			"item",
			"item1",
			"item2",
			"message",
			"objs",
			"getVar",
			"setVar",
			"toJavaString",
			"getCMType",
			"debug",
			"cancel",
			"msg", // JS17
			"room",
			"findItem",
			"door",
			"signal",
			"state",
			"parm" // N7
		};
		scope.defineFunctionProperties(names, JScriptEvent.class, ScriptableObject.DONTENUM);
		return scope;
	}

	/**
	 * @param scope a <SCRIPT>'s scope
	 * @param result what its last statement gave
	 * @return true if it called cancel(), or ended with the value "cancel"
	 */
	protected static boolean jsCancelled(final JScriptEvent scope, final Object result)
	{
		return scope.cancelled || ((result instanceof CharSequence) && result.toString().trim().equalsIgnoreCase("cancel"));
	}

	/**
	 * JS15: JS(expr) and JS$(expr): a JavaScript expression, in a scope kept
	 * by this engine, with the <SCRIPT> functions for this call's host,
	 * source and target.  Approved as a <SCRIPT> is.
	 * @param ctx the script's context
	 * @param expr the expression
	 * @return its value, a JavaScript one, or null if it failed
	 */
	protected Object jsExpr(final MPContext ctx, final String expr)
	{
		if((expr==null)||(expr.length()==0))
			return null;
		if(!(this.approvedScripts || CMSecurity.isApprovedJScript(new StringBuffer(expr))))
		{
			logError(ctx,"JS","Security","not approved: "+expr);
			return null;
		}
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			if(jsExprScope == null)
				jsExprScope = jsEventScope(cx, ctx);
			else
				jsExprScope.ctx = ctx;
			jsExprScope.cancelled = false;
			return cx.evaluateString(jsExprScope, expr, "JS()", 1, null);
		}
		catch(final Exception e)
		{
			JSRuntime.report("JS(" + expr + ") on '" + ((ctx.scripted==null)?"?":ctx.scripted.Name()) + "'", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * @param r a JavaScript value, or null
	 * @return it as text: whole numbers without a point, nothing for null or undefined
	 */
	protected static String jsText(final Object r)
	{
		if((r == null) || (r instanceof org.mozilla.javascript.Undefined))
			return "";
		if(r instanceof Number)
		{
			final double d = ((Number)r).doubleValue();
			if((d == Math.rint(d)) && (!Double.isInfinite(d)) && (Math.abs(d) < 1e15))
				return Long.toString((long)d);
		}
		if(r instanceof org.mozilla.javascript.Wrapper)
			return String.valueOf(((org.mozilla.javascript.Wrapper)r).unwrap());
		return Context.toString(r);
	}

	/**
	 * JS15: MPJSFILE: a JavaScript file run as a <SCRIPT> is, with args the
	 * rest of the line; cancel() in it, or a last value of "cancel", ends the
	 * prog with CANCEL.
	 * @param ctx the script's context
	 * @param file the file, under resources/ or world:path
	 * @param args the rest of the line
	 * @return CANCEL, or null to go on
	 */
	protected String runJSFile(final MPContext ctx, final String file, final String args)
	{
		final String text=JSScope.fileText(file);
		if(text==null)
		{
			logError(ctx,"MPJSFILE","Syntax","no such file: "+file);
			return null;
		}
		if(!(this.approvedScripts || CMSecurity.isApprovedJScript(new StringBuffer(text))))
		{
			logError(ctx,"MPJSFILE","Security","not approved: "+file);
			return null;
		}
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Script S = JSScope.compileFile(cx, file);
			final JScriptEvent scope = jsEventScope(cx, ctx);
			ScriptableObject.putProperty(scope, "args", args);
			final Object r = (S == null) ? null : S.exec(cx, scope);
			return jsCancelled(scope, r) ? "CANCEL" : null;
		}
		catch(final Exception e)
		{
			JSRuntime.report("MPJSFILE " + file + " on '" + ((ctx.scripted==null)?"?":ctx.scripted.Name()) + "'", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * The host a command's first word names, as MPSETVAR finds it.
	 * @param ctx the script's context
	 * @param which the word
	 * @return the host's name for variables
	 */
	protected String varHostFor(final MPContext ctx, final String which)
	{
		return getVarHost(getArgumentItem(which,ctx),which,ctx);
	}

	/**
	 * The newer commands (SC1 and on).
	 * @param ctx the script's context
	 * @param cmd the command's word
	 * @param line the line
	 * @return null to go on to the next line, or what ends the body
	 */
	protected String extraCommand(final MPContext ctx, final String cmd, final String line)
	{
		switch(cmd)
		{
		case "MPLIST": // SC1: MPLIST host var ADD|ADDUNIQUE|DEL|CLEAR|SHUFFLE|SORT words
		{
			final String which=varHostFor(ctx,CMParms.getCleanBit(line,1));
			final String var=varify(ctx,CMParms.getCleanBit(line,2));
			final String op=CMParms.getCleanBit(line,3);
			final String words=varify(ctx,CMParms.getPastBitClean(line,3));
			final String now=ScriptLib.listChange(getVar(which,var),op,words);
			if(now==null)
				logError(ctx,"MPLIST","Syntax","unknown change "+op+": "+line);
			else
				setVar(which,var,listValue(now));
			return null;
		}
		case "MPQOBJECTIVE": // Q3: MPQOBJECTIVE who quest ADD id target text | SET|BUMP id (n) | DONE|FAIL|HIDE|SHOW id | CLEAR (id)
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			if(!(W instanceof MOB))
			{
				logError(ctx,"MPQOBJECTIVE","RunTime","no such character: "+line);
				return null;
			}
			final String quest=questNameFor(varify(ctx,CMParms.getCleanBit(line,2)));
			final String err=QuestJournal.objective((MOB)W,quest,varify(ctx,CMParms.getCleanBit(line,3)),varify(ctx,CMParms.getPastBitClean(line,3)));
			if(err!=null)
				logError(ctx,"MPQOBJECTIVE","Syntax",err+": "+line);
			return null;
		}
		case "MPQJOURNAL": // Q3: MPQJOURNAL who quest text: an entry, dated on the game calendar
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			if(!(W instanceof MOB))
			{
				logError(ctx,"MPQJOURNAL","RunTime","no such character: "+line);
				return null;
			}
			QuestJournal.journal((MOB)W,questNameFor(varify(ctx,CMParms.getCleanBit(line,2))),varify(ctx,CMParms.getPastBitClean(line,2)));
			return null;
		}
		case "MPALARMCANCEL": // C5: MPALARMCANCEL name: a named alarm (MPALARM NAME=) goes, from all the host's scripts
		{
			final String name=varify(ctx,CMParms.getCleanBit(line,1)).trim();
			if(name.length()==0)
			{
				logError(ctx,"MPALARMCANCEL","Syntax","MPALARMCANCEL name: "+line);
				return null;
			}
			cancelAlarm(name);
			Telegraph.cancel(ctx.scripted,name);
			if(ctx.scripted instanceof MOB)
			{
				final Telegraph.Effect T=Telegraph.find((MOB)ctx.scripted,name);
				if(T!=null)
					T.telegraphDone();
			}
			return null;
		}
		case "MPTELEGRAPH": // C5: MPTELEGRAPH [NAME=name] ticks "warning" command [INTERRUPT=..] [AVOID=..] [INTERRUPTMSG=".."]
		{
			String rest=line.trim();
			rest=(rest.indexOf(' ')<0)?"":rest.substring(rest.indexOf(' ')+1).trim();
			if(rest.toUpperCase().startsWith("FIRE "))
			{
				// its alarm going off: MPTELEGRAPH FIRE name command
				rest=rest.substring(5).trim();
				final int sp=rest.indexOf(' ');
				if(sp>0)
					telegraphHit(ctx,rest.substring(0,sp).trim(),rest.substring(sp+1).trim());
				return null;
			}
			final MOB mob=(ctx.scripted instanceof MOB)?(MOB)ctx.scripted:null;
			if((mob==null)||(mob.location()==null))
			{
				logError(ctx,"MPTELEGRAPH","RunTime","only a mob in a room can wind up an attack: "+line);
				return null;
			}
			final StringBuilder opts=new StringBuilder("");
			final java.util.regex.Pattern opt=java.util.regex.Pattern.compile("(?i)\\s(INTERRUPT|AVOID|INTERRUPTMSG)=(\"[^\"]*\"|\\S+)\\s*$");
			for(java.util.regex.Matcher m=opt.matcher(rest);m.find();m=opt.matcher(rest))
			{
				opts.append(' ').append(m.group(1).toUpperCase()).append('=').append(m.group(2));
				rest=rest.substring(0,m.start()).trim();
			}
			String name="TELEGRAPH";
			if(rest.toUpperCase().startsWith("NAME="))
			{
				final int sp=rest.indexOf(' ');
				name=((sp<0)?rest.substring(5):rest.substring(5,sp)).trim().toUpperCase();
				rest=(sp<0)?"":rest.substring(sp+1).trim();
			}
			final int sp=rest.indexOf(' ');
			final int ticks=CMath.s_int(varify(ctx,(sp<0)?rest:rest.substring(0,sp)).trim());
			rest=(sp<0)?"":rest.substring(sp+1).trim();
			String warning;
			if(rest.startsWith("\""))
			{
				final int q=rest.indexOf('"',1);
				warning=(q<0)?rest.substring(1):rest.substring(1,q);
				rest=(q<0)?"":rest.substring(q+1).trim();
			}
			else
			{
				final int sp2=rest.indexOf(' ');
				warning=(sp2<0)?rest:rest.substring(0,sp2);
				rest=(sp2<0)?"":rest.substring(sp2+1).trim();
			}
			if((ticks<=0)||(rest.length()==0)||(name.length()==0))
			{
				logError(ctx,"MPTELEGRAPH","Syntax","MPTELEGRAPH [NAME=name] ticks \"warning\" command [INTERRUPT=..] [AVOID=..]: "+line);
				return null;
			}
			warning=varify(ctx,warning);
			final Telegraph.Effect old=Telegraph.find(mob,name);
			if(old!=null)
				old.telegraphDone();
			final Ability A=CMClass.getAbility("Prop_Telegraph");
			if(A==null)
				return null;
			A.setMiscText("NAME="+name+" TICKS="+ticks+" WARNING=\""+warning.replace("\"","\\\"")+"\""+opts);
			mob.addEffect(A);
			if(A instanceof Telegraph.Effect)
				((Telegraph.Effect)A).telegraphWarn();
			Telegraph.cancel(mob,name);
			final SubScript vscript=new SubScriptImpl(ctx.script);
			vscript.add(new ScriptLn(Telegraph.NAMED_ALARM+name,null,null));
			vscript.add(new ScriptLn("MPTELEGRAPH FIRE "+name+" "+rest,null,null));
			prequeResponse(-1,ctx,vscript,ticks);
			return null;
		}
		case "MPTHREAT": // C18: MPTHREAT npc who +n|-n|=n|*x: their threat to it
		{
			final Environmental N=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,2),ctx);
			final String amt=varify(ctx,CMParms.getPastBitClean(line,2)).trim();
			if((!(N instanceof MOB))||(!(W instanceof MOB))||(amt.length()==0))
			{
				logError(ctx,"MPTHREAT","Syntax","MPTHREAT npc who +n|-n|=n|*x: "+line);
				return null;
			}
			final MOB npc=(MOB)N;
			final MOB who=(MOB)W;
			if(amt.startsWith("="))
				Threat.set(npc,who,CMath.s_int(amt.substring(1).trim()));
			else
			if(amt.startsWith("*"))
				Threat.set(npc,who,(int)Math.round(Threat.get(npc,who)*CMath.s_double(amt.substring(1).trim())));
			else
				Threat.add(npc,who,CMath.s_int(amt.replace("+","")));
			return null;
		}
		case "MPAFFINITY": // N15: MPAFFINITY npc who +n|-n|=n: the NPC's opinion of them
		{
			final Environmental N=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			final String w=CMParms.getCleanBit(line,2);
			final Environmental W=getArgumentMOB(w,ctx);
			final String amt=varify(ctx,CMParms.getPastBitClean(line,2)).trim();
			if((!(N instanceof MOB))||(amt.length()==0))
			{
				logError(ctx,"MPAFFINITY","Syntax","MPAFFINITY npc who +n|-n|=n: "+line);
				return null;
			}
			final String who=(W instanceof MOB)?W.Name():varify(ctx,w);
			if(amt.startsWith("="))
				NpcAffinity.set((MOB)N,who,CMath.s_long(amt.substring(1).trim()));
			else
				NpcAffinity.change((MOB)N,who,CMath.s_long(amt.replace("+","")));
			return null;
		}
		case "MPMENU": // N2: MPMENU who FUNCTION "prompt" key:"label"[IF cond] ... [default]: numbered choices, not blocking
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			final String func=varify(ctx,CMParms.getCleanBit(line,2)).toUpperCase().trim();
			if((!(W instanceof MOB))||(func.length()==0))
			{
				logError(ctx,"MPMENU","RunTime","no such character, or no function: "+line);
				return null;
			}
			if(findFunc(ctx.scripted,func)==null)
			{
				logError(ctx,"MPMENU","Syntax","no FUNCTION_PROG "+func+": "+line);
				return null;
			}
			final ScriptMenus.Menu menu;
			try
			{
				menu=ScriptMenus.parse(CMParms.getPastBit(line,2));
			}
			catch(final IllegalArgumentException e)
			{
				logError(ctx,"MPMENU","Syntax",e.getMessage()+": "+line);
				return null;
			}
			menu.prompt=varify(ctx,menu.prompt);
			menu.dflt=varify(ctx,menu.dflt);
			for(final Iterator<ScriptMenus.Option> o=menu.options.iterator();o.hasNext();)
			{
				final ScriptMenus.Option O=o.next();
				if(O.cond.length()>0)
				{
					boolean shown;
					try
					{
						shown=eval(ctx,new String[][]{parseEval(O.cond)},0);
					}
					catch(final ScriptParseException e)
					{
						logError(ctx,"MPMENU","Syntax","IF "+O.cond+": "+e.getMessage());
						shown=false;
					}
					if(!shown)
					{
						o.remove();
						continue;
					}
				}
				O.key=varify(ctx,O.key);
				O.label=varify(ctx,O.label);
			}
			menu.who=(MOB)W;
			menu.engine=this;
			menu.func=func;
			menu.ctx=new MPContext(ctx);
			final Session S=promptSession((MOB)W);
			if(inBackground((MOB)W))
				menu.prompt=promptFor((MOB)W,menu.prompt);
			ScriptMenus.open(menu,S);
			return null;
		}
		case "MPQTIMER": // Q11: MPQTIMER who quest <game time>|OFF: their own deadline for it
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			final String quest=questNameFor(varify(ctx,CMParms.getCleanBit(line,2)));
			if(!(W instanceof MOB))
			{
				logError(ctx,"MPQTIMER","RunTime","no such character: "+line);
				return null;
			}
			final String wrong=QuestTimers.set((MOB)W,quest,varify(ctx,CMParms.getPastBitClean(line,2)));
			if(wrong!=null)
				logError(ctx,"MPQTIMER","Syntax",wrong+": "+line);
			return null;
		}
		case "MPREWARD": // Q10: MPREWARD who "awards": a reward with one message
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			if(!(W instanceof MOB))
			{
				logError(ctx,"MPREWARD","RunTime","no such character: "+line);
				return null;
			}
			QuestRewards.give((MOB)W,"SCRIPT:"+((defaultQuestName!=null)?defaultQuestName:""),varify(ctx,CMParms.getPastBit(line,1)).trim());
			return null;
		}
		case "MPQUESTFAIL": // Q4: MPQUESTFAIL who quest (reason): fails it for them, as MPENDQUEST who ends it
		{
			final Environmental W=getArgumentMOB(CMParms.getCleanBit(line,1),ctx);
			final Quest Q=CMLib.quests().fetchQuest(questNameFor(varify(ctx,CMParms.getCleanBit(line,2))));
			if((!(W instanceof MOB))||(Q==null))
			{
				logError(ctx,"MPQUESTFAIL","RunTime","no such character or quest: "+line);
				return null;
			}
			QuestRecords.fail(Q,(MOB)W,QuestRecords.FAILED,varify(ctx,CMParms.getPastBitClean(line,2)));
			return null;
		}
		case "MPJSFILE": // JS15: MPJSFILE file args: a JavaScript file run as a <SCRIPT> is, with args
		{
			final String file=varify(ctx,CMParms.getCleanBit(line,1));
			return runJSFile(ctx,file,varify(ctx,CMParms.getPastBitClean(line,1)));
		}
		case "MPTAG": // SC12: MPTAG target ADD|DEL|TOGGLE tag
		{
			final Environmental E=getArgumentItem(CMParms.getCleanBit(line,1),ctx);
			final String op=CMParms.getCleanBit(line,2).toUpperCase();
			final String tag=varify(ctx,CMParms.getPastBitClean(line,2)).trim();
			if(!(E instanceof Taggable)||(tag.length()==0))
			{
				logError(ctx,"MPTAG","Syntax","no target or tag: "+line);
				return null;
			}
			final Taggable T=(Taggable)E;
			if(op.equals("ADD")||(op.equals("TOGGLE")&&(!T.hasTag(tag))))
				T.addTag(tag);
			else
			if(op.equals("DEL")||op.equals("TOGGLE"))
				T.delTag(tag);
			else
				logError(ctx,"MPTAG","Syntax","ADD, DEL or TOGGLE? "+line);
			return null;
		}
		case "MPSTATE": // SC4: MPSTATE thing name value|CLEAR: a value kept on that one object
		{
			final Environmental O=getArgumentItem(CMParms.getCleanBit(line,1),ctx);
			final String var=varify(ctx,CMParms.getCleanBit(line,2)).trim();
			String val=varify(ctx,CMParms.getPastBitClean(line,2));
			if(!(O instanceof Physical)||(O instanceof Room)||(var.length()==0))
			{
				logError(ctx,"MPSTATE","Syntax","no such thing, or no name: "+line);
				return null;
			}
			if(val.trim().equalsIgnoreCase("CLEAR"))
				val="";
			setVar(ObjectVars.hostOf((Physical)O),var,val);
			return null;
		}
		case "MPMSG": // SC5: MPMSG VALUE n|+n|-n|*x|/x, MPMSG TEXT S|T|O text|NULL, MPMSG TARGET thing
		{
			final CMMsg m=ctx.cmsg;
			final String what=CMParms.getCleanBit(line,1).toUpperCase();
			if(m==null)
			{
				logError(ctx,"MPMSG","RunTime","there is no message here: MPMSG works in CNCLMSG_PROG");
				return null;
			}
			switch(what)
			{
			case "VALUE":
			{
				final String x=varify(ctx,CMParms.getPastBitClean(line,1)).trim();
				final double by=(x.length()>1)?CMath.s_double(x.substring(1).trim()):0.0;
				if(x.startsWith("*"))
					m.setValue((int)Math.round(m.value()*by));
				else
				if(x.startsWith("/"))
					m.setValue((by==0.0)?m.value():(int)Math.round(m.value()/by));
				else
				if(x.startsWith("+"))
					m.setValue(m.value()+(int)Math.round(by));
				else
				if(x.startsWith("-"))
					m.setValue(m.value()-(int)Math.round(by));
				else
					m.setValue(CMath.s_int(x.startsWith("=")?x.substring(1).trim():x));
				break;
			}
			case "TEXT":
			{
				final String which=CMParms.getCleanBit(line,2).toUpperCase();
				final String text=varify(ctx,CMParms.getPastBitClean(line,2));
				final String to=text.trim().equalsIgnoreCase("NULL")?null:text;
				if(which.startsWith("S"))
					m.setSourceMessage(to);
				else
				if(which.startsWith("T"))
					m.setTargetMessage(to);
				else
				if(which.startsWith("O"))
					m.setOthersMessage(to);
				else
					logError(ctx,"MPMSG","Syntax","TEXT takes S, T or O: "+line);
				break;
			}
			case "TARGET":
			{
				final Environmental E=getArgumentItem(CMParms.getPastBitClean(line,1).trim(),ctx);
				if(E==null)
					logError(ctx,"MPMSG","RunTime","no such target: "+line);
				else
					m.setTarget(E);
				break;
			}
			default:
				logError(ctx,"MPMSG","Syntax","MPMSG takes VALUE, TEXT or TARGET: "+line);
				break;
			}
			return null;
		}
		case "MPSIGNAL": // SC6: MPSIGNAL target name [args] [DELAY ticks]
		{
			final String raw=CMParms.getCleanBit(line,1);
			final String name=varify(ctx,CMParms.getCleanBit(line,2)).trim();
			String args=varify(ctx,CMParms.getPastBitClean(line,2)).trim();
			int ticks=1;
			final List<String> w=CMParms.parse(args);
			if((w.size()>=2)&&w.get(w.size()-2).equalsIgnoreCase("DELAY")&&CMath.isInteger(w.get(w.size()-1)))
			{
				ticks=Math.max(1,CMath.s_int(w.get(w.size()-1)));
				args=String.join(" ",w.subList(0,w.size()-2));
			}
			final String up=raw.toUpperCase();
			Environmental thing=null;
			if((!up.equals("ROOM"))&&(!up.equals("AREA"))&&(!up.equals("WORLD"))&&(!up.startsWith("TAG:"))&&(raw.indexOf(':')<0)&&(raw.indexOf('#')<0))
				thing=getArgumentItem(raw,ctx);
			final List<PhysicalAgent> hosts=ScriptEvents.signalHosts(varify(ctx,raw),ctx.scripted,thing);
			if(name.length()==0)
				logError(ctx,"MPSIGNAL","Syntax","no signal name: "+line);
			else
			if(hosts.isEmpty())
				logError(ctx,"MPSIGNAL","RunTime","no one to hear it: "+line);
			else
				ScriptEvents.signal(hosts,name,args,(ctx.source!=null)?ctx.source:ctx.monster,ctx.scripted,ticks);
			return null;
		}
		case "MPWAIT": // SC7: MPWAIT ticks | MPWAIT n HOURS|DAYS|WEEKS|MONTHS | MPWAIT UNTIL (condition) [max ticks]
		{
			if(ctx.repeats)
			{
				logError(ctx,"MPWAIT","RunTime","MPWAIT cannot pause a loop (FOR, MPWHILE), so it does not wait");
				return null;
			}
			final String rest=CMParms.getPastBit(line,0).trim();
			String again=null;
			int ticks;
			if(rest.toUpperCase().startsWith("UNTIL"))
			{
				final String c=rest.substring(5).trim();
				int close=-1;
				for(int i=0,depth=0;(i<c.length())&&(close<0);i++)
				{
					if(c.charAt(i)=='(')
						depth++;
					else
					if((c.charAt(i)==')')&&((--depth)==0))
						close=i;
				}
				if((!c.startsWith("("))||(close<0))
				{
					logError(ctx,"MPWAIT","Syntax","MPWAIT UNTIL (condition) [max ticks]: "+line);
					return null;
				}
				final String cond=c.substring(1,close).trim();
				final String after=varify(ctx,c.substring(close+1)).trim();
				final int max=CMath.isInteger(after)?CMath.s_int(after):1000;
				boolean met;
				try
				{
					final String[][] EVAL={parseEval(cond)};
					met=eval(ctx,EVAL,0);
				}
				catch(final Exception e)
				{
					logError(ctx,"MPWAIT","Syntax",e.getMessage());
					return null;
				}
				if(met||(max<=0))
					return null; // goes on now
				again="MPWAIT UNTIL ("+cond+") "+(max-1);
				ticks=1;
			}
			else
			{
				final List<String> w=CMParms.parse(varify(ctx,rest));
				final long n=w.isEmpty()?1:CMath.s_long(w.get(0));
				if(w.size()>1)
				{
					final Area A=CMLib.map().areaLocation(ctx.scripted);
					final TimeClock clock=(A!=null)?A.getTimeObj():CMLib.time().globalClock();
					final long hours=n*ScriptEvents.everyHours(1,w.get(1),clock);
					ticks=(int)Math.min(Integer.MAX_VALUE,(hours*CMProps.getMillisPerMudHour())/Math.max(1,CMProps.getTickMillis()));
				}
				else
					ticks=(int)Math.min(Integer.MAX_VALUE,n);
				ticks=Math.max(1,ticks);
			}
			final SubScript cont=waitContinuation(ctx,again);
			if(cont==null)
				return null; // it cannot pause here: said why, and goes on
			final MPContext later=new MPContext(ctx.scripted,ctx.monster,ctx.source,ctx.target,ctx.primaryItem,ctx.secondaryItem,ctx.msg,ctx.tmp.clone());
			later.cmsg=ctx.cmsg;
			later.locals.putAll(ctx.locals); // SC17
			que.add(new ScriptableResponse(cont.getTriggerCode(),later,cont,ticks));
			return WAITED;
		}
		case "WHILE": // SC17: WHILE (condition) [max passes] ... ENDWHILE
		{
			final SubScript script=ctx.script;
			final ScriptLn ln=script.get(ctx.line);
			Object[] parsed=((ln.third instanceof Object[])&&(((Object[])ln.third).length==3)&&"WHILE".equals(((Object[])ln.third)[0]))?(Object[])ln.third:null;
			if(parsed==null)
			{
				int depth=0;
				int end=-1;
				for(int l=ctx.line+1;(l<script.size())&&(end<0);l++)
				{
					final String w=CMParms.getCleanBit(script.get(l).first,0).toUpperCase();
					if(w.equals("WHILE"))
						depth++;
					else
					if(w.equals("ENDWHILE"))
					{
						if(depth==0)
							end=l;
						else
							depth--;
					}
				}
				if(end<0)
				{
					logError(ctx,"WHILE","Syntax","WHILE without ENDWHILE: "+line);
					ctx.line=script.size();
					return null;
				}
				final SubScriptImpl body=new SubScriptImpl(script);
				body.add(new ScriptLn("",null,null));
				for(int l=ctx.line+1;l<end;l++)
					body.add(script.get(l));
				parsed=new Object[]{"WHILE",body,Integer.valueOf(end)};
				ln.third=parsed;
			}
			String cond=CMParms.getPastBit(line,0).trim();
			int max=1000;
			if(cond.startsWith("("))
			{
				int close=-1;
				for(int i=0,d=0;(i<cond.length())&&(close<0);i++)
				{
					if(cond.charAt(i)=='(')
						d++;
					else
					if((cond.charAt(i)==')')&&((--d)==0))
						close=i;
				}
				if(close>0)
				{
					final String after=varify(ctx,cond.substring(close+1)).trim();
					if(CMath.isInteger(after))
						max=CMath.s_int(after);
					cond=cond.substring(1,close).trim();
				}
			}
			String resp=null;
			int passes=0;
			try
			{
				final String[][] EVAL={parseEval(cond)};
				while(eval(ctx,EVAL,0)&&(!ctx.scripted.amDestroyed()))
				{
					if((++passes)>max)
					{
						logError(ctx,"WHILE","RunTime","stopped after "+max+" passes: "+line);
						break;
					}
					final MPContext wctx=ctx.push((SubScript)parsed[1]);
					wctx.repeats=true;
					resp=execute(wctx);
					if(LOOP_CONTINUE.equals(resp))
						resp=null;
					if(resp!=null)
						break;
				}
			}
			catch(final Exception e)
			{
				logError(ctx,"WHILE","Syntax",e.getMessage());
			}
			if((resp!=null)&&resp.equalsIgnoreCase("BREAK"))
				resp=null;
			ctx.line=((Integer)parsed[2]).intValue(); // at its ENDWHILE
			return resp;
		}
		case "ENDWHILE":
			logError(ctx,"ENDWHILE","Syntax","ENDWHILE without WHILE");
			return null;
		case "ELSEIF":
			logError(ctx,"ELSEIF","Syntax","ELSEIF without IF");
			return null;
		case "CONTINUE": // SC17: the next pass of the loop
			return LOOP_CONTINUE;
		case "MPLOCAL": // SC17: MPLOCAL name value: $(name) while the body runs
		{
			final String name=varify(ctx,CMParms.getCleanBit(line,1)).trim();
			if(name.length()==0)
				logError(ctx,"MPLOCAL","Syntax","MPLOCAL name value: "+line);
			else
				ctx.locals.put(name,varify(ctx,CMParms.getPastBitClean(line,1)));
			return null;
		}
		default: // the newer commands end here
			logError(ctx,cmd,"Syntax","unknown command "+cmd);
			return null;
		}
	}

	/**
	 * A list's text as a value for setVar, which would otherwise read a
	 * leading +, -, * or / as arithmetic.
	 * @param list the list
	 * @return the value
	 */
	protected String listValue(final String list)
	{
		return "="+list; // SC11's literal form
	}

	/**
	 * SC5: a message trigger's =S|T|O STAT=VALUE filter: the value may have *
	 * wildcards, and =S|T|O MASK=... is a zapper mask on the source, target or
	 * tool.
	 * @param msg the message
	 * @param t the trigger's words
	 * @param q where the = is in t[3]
	 * @param val the stat's value
	 * @return true if the filter fits
	 */
	protected boolean msgFilterMatch(final CMMsg msg, final String[] t, final int q, final String val)
	{
		final String stat=t[3].substring(0,q);
		if(stat.equalsIgnoreCase("MASK"))
		{
			final StringBuilder mask=new StringBuilder(t[3].substring(q+1));
			for(int i=4;i<t.length;i++)
				mask.append(' ').append(t[i]);
			final char c=Character.toUpperCase(t[2].charAt(1));
			final Environmental E=(c=='S')?msg.source():(c=='T')?msg.target():(c=='O')?msg.tool():null;
			return (E!=null)&&CMLib.masking().maskCheck(mask.toString(),E,true);
		}
		final String patt=t[3].substring(q+1);
		if(patt.indexOf('*')<0)
			return match(val.trim(),patt);
		final StringBuilder re=new StringBuilder("(?i)");
		for(final String part : patt.split("\\*",-1))
			re.append((re.length()>4)?".*":"").append(java.util.regex.Pattern.quote(part));
		return val.trim().matches(re.toString());
	}

	protected boolean match(final String str, final String patt)
	{
		if(patt.trim().equalsIgnoreCase("ALL"))
			return true;
		if(patt.length()==0)
			return true;
		if(str.length()==0)
			return false;
		if(str.equalsIgnoreCase(patt))
			return true;
		return false;
	}

	private Item makeCheapItem(final Environmental E)
	{
		Item product=null;
		if(E instanceof Item)
			product=(Item)E;
		else
		{
			product=CMClass.getItem("StdItem");
			product.setName(E.Name());
			product.setDisplayText(E.displayText());
			product.setDescription(E.description());
			if(E instanceof Physical)
				product.setBasePhyStats((PhyStats)((Physical)E).basePhyStats().copyOf());
			product.recoverPhyStats();
		}
		return product;
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		if((!(host instanceof PhysicalAgent))||(msg.source()==null)||(recurseCounter.get()>2))
			return true;

		try
		{
			// atomic recurse counter
			recurseCounter.addAndGet(1);

			final PhysicalAgent affecting = (PhysicalAgent)host;
			if(ScriptTrace.isOn())
				ScriptTrace.handling(affecting);

			final List<SubScript> scripts=getScripts(host);
			SubScript script=null;
			boolean tryIt=false;
			String[] t=null;
			int triggerCode=0;
			String str=null;
			for(int v=0;v<scripts.size();v++)
			{
				tryIt=false;
				script=scripts.get(v);
				if(script.size()<1)
					continue;

				t=script.getTriggerArgs();
				triggerCode=script.getTriggerCode();
				switch(triggerCode)
				{
				case 87: // read_prog (ST19): before the reading, so RETURN CANCEL keeps the usual text back
					if((msg.targetMinor()==CMMsg.TYP_READ)&&canTrigger(87)&&(msg.target() instanceof Item)
					&&((!(affecting instanceof Item))||(msg.target()==affecting)))
					{
						if(t==null)
							t=parseBits(script,0,"Cr");
						if((t!=null)&&ScriptEvents.readMatches((t.length>1)?t[1]:"",(Item)msg.target()))
						{
							final MOB rmonster=getMakeMOB(affecting);
							if(lastKnownLocation==null)
								confirmLastKnownLocation(rmonster,msg.source());
							if((rmonster==null)||(rmonster.amDead())||(lastKnownLocation==null))
								break;
							final Item rI=(Item)msg.target();
							final Item rDefault=(affecting instanceof Item)?(Item)affecting:null;
							final String resp=execute(new MPContext(affecting,rmonster,msg.source(),rI,rI,rDefault,rI.Name(),null).push(script));
							if((resp!=null)&&(resp.equalsIgnoreCase("CANCEL")))
								return false;
						}
					}
					break;
				case 51: // cmdfail_prog
					if(canTrigger(51)
					&&(msg.targetMessage()!=null))
					{
						if(t==null)
							t=parseBits(script,0,"CCT");
						if(t!=null)
						{
							final String command=t[1];
							final int x=msg.targetMessage().indexOf(' ');
							final String userCmd=((x<0)?msg.targetMessage():msg.targetMessage().substring(0,x)).trim().toUpperCase(); // SC9: alone too
							if((userCmd.length()>0)&&command.toUpperCase().startsWith(userCmd))
							{
								str=(x<0)?"":msg.targetMessage().substring(x).trim().toUpperCase();
								if(str == null)
									break;
								if((t[2].length()==0)||(t[2].equals("ALL")))
									tryIt=true;
								else
								if((t[2].equals("P"))&&(t.length>3))
								{
									if(match(str.trim(),t[3]))
										tryIt=true;
								}
								else
								for(int i=2;i<t.length;i++)
								{
									if((" "+str+" ").indexOf(" "+t[i]+" ")>=0) // SC9: the first and last words too
									{
										str=(t[i].trim()+" "+str.trim()).trim();
										tryIt=true;
										break;
									}
								}
								if(tryIt)
								{
									final MOB monster=getMakeMOB(affecting);
									if(lastKnownLocation==null)
										confirmLastKnownLocation(monster,msg.source());
									if((monster==null)||(monster.amDead())||(lastKnownLocation==null))
										return true;
									final Item defaultItem=(affecting instanceof Item)?(Item)affecting:null;
									Item Tool=null;
									if(msg.tool() instanceof Item)
										Tool=(Item)msg.tool();
									if(Tool==null)
										Tool=defaultItem;
									String resp=null;
									if(msg.target() instanceof MOB)
										resp=execute(new MPContext(affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str, null).push(script));
									else
									if(msg.target() instanceof Item)
										resp=execute(new MPContext(affecting,monster,msg.source(),msg.target(),Tool,(Item)msg.target(),str, null).push(script));
									else
										resp=execute(new MPContext(affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str, null).push(script));
									if((resp!=null)&&(resp.equalsIgnoreCase("CANCEL")))
										return false;
								}
							}
						}
					}
					break;
				case 42: // cnclmsg_prog
					if(canTrigger(42))
					{
						if(t==null)
							t=parseBits(script,0,"CCT");
						if(t!=null)
						{
							final String command=t[1];
							boolean chk=false;
							final int x=command.indexOf('=');
							if(x>0)
							{
								chk=true;
								boolean minorOnly = false;
								boolean majorOnly = false;
								for(int i=0;i<x;i++)
								{
									switch(command.charAt(i))
									{
									case 'S':
										if(majorOnly)
											chk=chk&&msg.isSourceMajor(command.substring(x+1));
										else
										if(minorOnly)
											chk=chk&&msg.isSourceMinor(command.substring(x+1));
										else
											chk=chk&&msg.isSource(command.substring(x+1));
										break;
									case 'T':
										if(majorOnly)
											chk=chk&&msg.isTargetMajor(command.substring(x+1));
										else
										if(minorOnly)
											chk=chk&&msg.isTargetMinor(command.substring(x+1));
										else
											chk=chk&&msg.isTarget(command.substring(x+1));
										break;
									case 'O':
										if(majorOnly)
											chk=chk&&msg.isOthersMajor(command.substring(x+1));
										else
										if(minorOnly)
											chk=chk&&msg.isOthersMinor(command.substring(x+1));
										else
											chk=chk&&msg.isOthers(command.substring(x+1));
										break;
									case '<':
										minorOnly=true;
										majorOnly=false;
										break;
									case '>':
										majorOnly=true;
										minorOnly=false;
										break;
									case '?':
										majorOnly=false;
										minorOnly=false;
										break;
									default:
										chk=false;
										break;
									}
								}
							}
							else
							if(command.startsWith(">"))
							{
								final String cmd=command.substring(1);
								chk=msg.isSourceMajor(cmd)||msg.isTargetMajor(cmd)||msg.isOthersMajor(cmd);
							}
							else
							if(command.startsWith("<"))
							{
								final String cmd=command.substring(1);
								chk=msg.isSourceMinor(cmd)||msg.isTargetMinor(cmd)||msg.isOthersMinor(cmd);
							}
							else
							if(command.startsWith("?"))
							{
								final String cmd=command.substring(1);
								chk=msg.isSource(cmd)||msg.isTarget(cmd)||msg.isOthers(cmd);
							}
							else
								chk=msg.isSource(command)||msg.isTarget(command)||msg.isOthers(command);
							if(chk)
							{
								str="";
								final Session S = msg.source().session();
								if((S!=null)
								&&(S.getHistory().size()>0))
									str=" "+CMParms.combine(S.getHistory().getLast(),0).toUpperCase()+" ";
								if((t[2].length()==0)||(t[2].equals("ALL")))
									tryIt=true;
								else
								if((t[2].equals("P"))&&(t.length>3))
								{
									if(match(str.trim(),t[3]))
										tryIt=true;
								}
								else
								if(t[2].startsWith("=")
								&&(t[2].length()==2)
								&&(t.length>3)&&(t[3].indexOf('=')>0))
								{
									final int q = t[3].indexOf('=');
									String val;
									switch(t[2].charAt(1))
									{
									case 's': case 'S':
										val=((msg.source()==null)?"":
											CMLib.coffeeMaker().getAnyGenStat(msg.source(), t[3].substring(0,q)));
										break;
									case 't': case 'T':
										val=((msg.target()==null)?"":
											CMLib.coffeeMaker().getAnyGenStat(msg.target(), t[3].substring(0,q)));
										break;
									case 'o': case 'O':
										val=((msg.tool()==null)?"":
											CMLib.coffeeMaker().getAnyGenStat(msg.tool(), t[3].substring(0,q)));
										break;
									default:
										val="";
										break;
									}
									if(msgFilterMatch(msg,t,q,val))
										tryIt=true;
								}
								else
								for(int i=2;i<t.length;i++)
								{
									if(str.indexOf(" "+t[i]+" ")>=0)
									{
										str=(t[i].trim()+" "+str.trim()).trim();
										tryIt=true;
										break;
									}
								}
							}
						}
					}
					break;
				}
				if(tryIt)
				{
					final MOB monster=getMakeMOB(affecting);
					if(lastKnownLocation==null)
						confirmLastKnownLocation(monster, msg.source());
					if((monster==null)||(monster.amDead())||(lastKnownLocation==null))
						return true;
					final Item defaultItem=(affecting instanceof Item)?(Item)affecting:null;
					Item Tool=null;
					if(msg.tool() instanceof Item)
						Tool=(Item)msg.tool();
					if(Tool==null)
						Tool=defaultItem;
					String resp=null;
					final Item secondItem=(msg.target() instanceof Item)?(Item)msg.target():defaultItem;
					final MPContext ctx = new MPContext(affecting,monster,msg.source(),msg.target(),Tool,secondItem,str, null);
					ctx.tmp[0] = msg;
					ctx.cmsg = msg; // SC5: MSG() reads it, MPMSG changes it
					resp=execute(ctx.push(script));
					if((resp!=null)&&(resp.equalsIgnoreCase("CANCEL")))
						return false;
				}
			}
		}
		finally
		{
			recurseCounter.addAndGet(-1);
		}
		return true;
	}

	protected String standardTriggerCheck(final SubScript script, String[] t, final Environmental E,
										  final PhysicalAgent scripted, final MOB monster,
										  final MOB source, final Environmental target,
										  final Item primaryItem, final Item secondaryItem,
										  final Object[] tmp)
	{
		if(E==null)
			return null;
		final boolean[] dollarChecks;
		if(t==null)
		{
			t=parseBits(script,0,"CT");
			dollarChecks=new boolean[t.length];
			for(int i=1;i<t.length;i++)
				dollarChecks[i] = t[i].indexOf('$')>=0;
			script.get(0).third=dollarChecks;
		}
		else
			dollarChecks=(boolean[])script.get(0).third;
		final String eNameUpp=E.Name().toUpperCase();
		final String ID=E.ID().toUpperCase();
		if((t[1].length()==0)
		||(t[1].equals("ALL"))
		||(t[1].equals("P")
			&&(t.length==3)
			&&((t[2].equalsIgnoreCase(eNameUpp))
				||(t[2].equalsIgnoreCase("ALL")))))
			return t[1];
		MPContext ctx = null;
		for(int i=1;i<t.length;i++)
		{
			final String word;
			if (dollarChecks[i])
			{
				if(ctx == null)
					ctx = new MPContext(scripted, monster, source, target, primaryItem, secondaryItem, eNameUpp, tmp);
				word=this.varify(ctx, t[i]);
			}
			else
				word=t[i];
			if(word.equals("P") && (i < t.length-1))
			{
				final String arg;
				if (dollarChecks[i+1])
				{
					if(ctx == null)
						ctx = new MPContext(scripted, monster, source, target, primaryItem, secondaryItem, eNameUpp, tmp);
					arg=this.varify(ctx, t[i+1]);
				}
				else
					arg=t[i+1];
				if( arg.equalsIgnoreCase(eNameUpp)
				|| arg.equalsIgnoreCase(ID)
				|| arg.equalsIgnoreCase("ALL"))
					return word;
				i++;
			}
			else
			if(((" "+eNameUpp+" ").indexOf(" "+word+" ")>=0)
			||(ID.equalsIgnoreCase(word))
			||(word.equalsIgnoreCase("ALL")))
				return word;
		}
		return null;

	}

	protected int getTriggerPercent(final String t1, final Physical P)
	{
		if(t1.length()==0)
			return 100;
		if(Character.isDigit(t1.charAt(0)))
			return CMath.s_int(t1);
		else
		if(t1.equalsIgnoreCase("ALL")||t1.equalsIgnoreCase("P ALL"))
			return 101;
		else
		if(t1.startsWith("P ")||t1.startsWith("p "))
			return CMLib.masking().maskCheck(t1.substring(2), P, true)?101:0;
		else
			return CMLib.masking().maskCheck(t1, P, false)?101:0;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		if((!(host instanceof PhysicalAgent))||(msg.source()==null)||(recurseCounter.get() > 2))
			return;

		try
		{
			// atomic recurse counter
			recurseCounter.addAndGet(1);

			final PhysicalAgent affecting = (PhysicalAgent)host;
			if(ScriptTrace.isOn())
				ScriptTrace.handling(affecting);

			final MOB monster=getMakeMOB(affecting);

			if(lastKnownLocation==null)
				confirmLastKnownLocation(monster, msg.source());
			if((monster==null)||(monster.amDead())||(lastKnownLocation==null))
				return;

			final Item defaultItem=(affecting instanceof Item)?(Item)affecting:null;
			MOB eventMob=monster;
			if((defaultItem!=null)&&(defaultItem.owner() instanceof MOB))
				eventMob=(MOB)defaultItem.owner();

			final List<SubScript> scripts=getScripts(host);

			// ST21: a character's arrival, as GREET_PROG sees it, is a meeting the NPC remembers
			long[] metBefore=null;
			if((affecting instanceof MOB)&&(msg.targetMinor()==CMMsg.TYP_ENTER)&&msg.amITarget(lastKnownLocation)
			&&(!msg.amISource(monster))&&(msg.source().playerStats()!=null)
			&&CMLib.flags().canSenseEnteringLeaving(msg.source(),(MOB)affecting))
				metBefore=ScriptEvents.met((MOB)affecting,msg.source(),msg);

			if(msg.amITarget(eventMob)
			&&(!msg.amISource(monster))
			&&(msg.targetMinor()==CMMsg.TYP_DAMAGE)
			&&(msg.source()!=monster))
				lastToHurtMe=msg.source();
			SubScript script=null;
			String[] t=null;
			for(int v=0;v<scripts.size();v++)
			{
				script=scripts.get(v);
				if(script.size()<1)
					continue;

				t=script.getTriggerArgs();
				final int triggerCode=script.getTriggerCode();
				int targetMinorTrigger=-1;
				switch(triggerCode)
				{
				case 1: // greet_prog
					if((msg.targetMinor()==CMMsg.TYP_ENTER)
					&&(msg.amITarget(lastKnownLocation)||(affecting instanceof Area))
					&&(!msg.amISource(eventMob))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&canTrigger(1)
					&&((!(affecting instanceof MOB))||CMLib.flags().canSenseEnteringLeaving(msg.source(),(MOB)affecting)))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 78: // combat_prog FLEE (ST15)
					if((msg.sourceMinor()==CMMsg.TYP_FLEE)&&canTrigger(78))
					{
						final MOB foe=msg.source();
						final boolean mine=(!(affecting instanceof MOB)&&!(defaultItem!=null))
							||(foe.getVictim()==eventMob)||(eventMob.getVictim()==foe);
						if(mine)
						{
							if(t==null)
								t=parseBits(script,0,"CCr");
							if((t!=null)&&combatMatches(t,"FLEE",foe))
							{
								enqueResponse(triggerCode,affecting,monster,foe,foe,defaultItem,null,"FLEE",script,1,t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 75: // craft_prog (ST13)
					if((msg.targetMinor()==CMMsg.TYP_ITEMGENERATED)&&canTrigger(75)
					&&(msg.target() instanceof Item)&&(msg.tool() instanceof Ability))
					{
						if(t==null)
							t=parseBits(script,0,"CCcr");
						if((t!=null)&&ScriptEvents.craftMatches(t,(Ability)msg.tool(),(Item)msg.target()))
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,((Ability)msg.tool()).ID(),script,1,t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 76: // recipe_prog (ST13)
					if((msg.targetMinor()==CMMsg.TYP_RECIPELEARNED)&&canTrigger(76)
					&&(msg.target() instanceof Item)&&(msg.tool() instanceof Ability))
					{
						if(t==null)
							t=parseBits(script,0,"CCr");
						if((t!=null)&&ScriptEvents.recipeMatches(t,(Ability)msg.tool(),(Item)msg.target()))
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,((Ability)msg.tool()).ID(),script,1,t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 89: // firstgreet_prog (ST21)
				case 90: // regreet_prog
					if((metBefore!=null)&&canTrigger(triggerCode)&&isFreeToBeTriggered(monster))
					{
						if(t==null)
							t=parseBits(script,0,(triggerCode==89)?"CR":"CCR");
						if(t==null)
							break;
						final boolean fits;
						final String pct;
						if(triggerCode==89)
						{
							fits=(metBefore[0]==0);
							pct=t[1];
						}
						else
						{
							final long days=CMath.s_long(t[1]);
							fits=(metBefore[0]>0)&&((GameTime.now()-metBefore[2])>=(days*ScriptEvents.gameDayMillis()));
							pct=(t.length>2)?t[2]:"";
						}
						if(fits&&chance(affecting,script,(pct.trim().length()==0)?100:getTriggerPercent(pct,msg.source())))
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1,t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 45: // arrive_prog
					if(((msg.targetMinor()==CMMsg.TYP_ENTER)||(msg.sourceMinor()==CMMsg.TYP_LIFE))
					&&(msg.amISource(eventMob))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&canTrigger(45))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], eventMob);
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								if((host instanceof Item)
								&&(((Item)host).owner() instanceof MOB)
								&&(msg.source()==((Item)host).owner()))
								{
									// this is to prevent excessive queing when a player is running full throttle with a scripted item
									// that pays attention to where it is.
									for(int i=que.size()-1;i>=0;i--)
									{
										try
										{
											if(que.get(i).scr == script)
												que.remove(i);
										}
										catch(final Exception e)
										{
										}
									}
								}
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1, t);
								if(!multiTriggers)
									return;
						}
						}
					}
					break;
				case 2: // all_greet_prog
					if((msg.targetMinor()==CMMsg.TYP_ENTER)&&canTrigger(2)
					&&(!msg.amISource(eventMob))
					&&(msg.amITarget(lastKnownLocation)||(affecting instanceof Area))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&((!(affecting instanceof MOB)) ||CMLib.flags().canActAtAll(monster)))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 52: // group_greet_prog
					if((msg.targetMinor()==CMMsg.TYP_ENTER)&&canTrigger(52)&&canTrigger(-52)
					&&(msg.amITarget(lastKnownLocation))
					&&(!msg.amISource(eventMob))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&((!(affecting instanceof MOB)) ||CMLib.flags().canActAtAll(monster)))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								noTrigger.put(Integer.valueOf(-52),Long.valueOf(GameTime.now()+CMProps.getTickMillis()));
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 47: // speak_prog
					if(((msg.sourceMinor()==CMMsg.TYP_SPEAK)||(msg.targetMinor()==CMMsg.TYP_SPEAK))
					&&canTrigger(47)
					&&(msg.amISource(monster)||(!(affecting instanceof MOB)))
					&&(!msg.othersMajor(CMMsg.MASK_CHANNEL))
					&&((msg.sourceMessage()!=null)
					   ||((msg.target()==monster)&&(msg.targetMessage()!=null)&&(msg.tool()==null)))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						if(t==null)
						{
							t=parseBits(script,0,"CT");
							for(int i=0;i<t.length;i++)
							{
								if(t[i]!=null)
									t[i]=t[i].replace('`', '\'');
							}
						}
						String str=null;
						if(msg.sourceMessage() != null)
							str=CMStrings.getSayFromMessage(msg.sourceMessage().toUpperCase());
						else
						if(msg.targetMessage() != null)
							str=CMStrings.getSayFromMessage(msg.targetMessage().toUpperCase());
						else
						if(msg.othersMessage() != null)
							str=CMStrings.getSayFromMessage(msg.othersMessage().toUpperCase());
						if(str != null)
						{
							str=(" "+str.replace('`', '\'')+" ").toUpperCase();
							str=CMStrings.removeColors(str);
							str=CMStrings.replaceAll(str,"\n\r"," ");
							if((t[1].length()==0)||(t[1].equals("ALL")))
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,str,script,1, t);
								if(!multiTriggers)
									return;
							}
							else
							if((t[1].equals("P"))&&(t.length>2))
							{
								if(match(str.trim(),t[2]))
								{
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,str,script,1, t);
									if(!multiTriggers)
										return;
								}
							}
							else
							for(int i=1;i<t.length;i++)
							{
								final int x=str.indexOf(" "+t[i]+" ");
								if(x>=0)
								{
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,str.substring(x).trim(),script,1, t);
									if(!multiTriggers)
										return;
								}
							}
						}
					}
					break;
				case 3: // speech_prog
					if(((msg.sourceMinor()==CMMsg.TYP_SPEAK)||(msg.targetMinor()==CMMsg.TYP_SPEAK))
					&&canTrigger(3)
					&&(!msg.amISource(monster))
					&&(!msg.othersMajor(CMMsg.MASK_CHANNEL))
					&&(((msg.othersMessage()!=null)
						&&((msg.tool()==null)
						  ||(!(msg.tool() instanceof Ability))
						  ||((((Ability)msg.tool()).classificationCode()&Ability.ALL_ACODES)!=Ability.ACODE_LANGUAGE)))
					  ||((msg.target()==monster)
						&&(msg.targetMessage()!=null)
						&&(msg.tool()==null)))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						if(t==null)
						{
							t=parseBits(script,0,"CT");
							for(int i=0;i<t.length;i++)
							{
								if(t[i]!=null)
									t[i]=t[i].replace('`', '\'');
							}
						}
						String str=null;
						if(msg.othersMessage() != null)
							str=CMStrings.getSayFromMessage(msg.othersMessage().toUpperCase());
						else
						if(msg.targetMessage() != null)
							str=CMStrings.getSayFromMessage(msg.targetMessage().toUpperCase());
						if(str != null)
						{
							str=(" "+str.replace('`', '\'')+" ").toUpperCase();
							str=CMStrings.removeColors(str);
							str=CMStrings.replaceAll(str,"\n\r"," ");
							if((t[1].length()==0)||(t[1].equals("ALL")))
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,str,script,1, t);
								if(!multiTriggers)
									return;
							}
							else
							if((t[1].equals("P"))&&(t.length>2))
							{
								if(match(str.trim(),t[2]))
								{
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,str,script,1, t);
									if(!multiTriggers)
										return;
								}
							}
							else
							for(int i=1;i<t.length;i++)
							{
								final int x=str.indexOf(" "+t[i]+" ");
								if(x>=0)
								{
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,str.substring(x).trim(),script,1, t);
									if(!multiTriggers)
										return;
								}
							}
						}
					}
					break;
				case 4: // give_prog
					if((msg.targetMinor()==CMMsg.TYP_GIVE)
					&&canTrigger(4)
					&&((msg.amITarget(monster))
						||(msg.tool()==affecting)
						||(affecting instanceof Room)
						||(affecting instanceof Area))
					&&(!msg.amISource(monster))
					&&(msg.tool() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.tool(), affecting,monster,msg.source(),monster,(Item)msg.tool(),defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),monster,(Item)msg.tool(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 48: // giving_prog
					if((msg.targetMinor()==CMMsg.TYP_GIVE)
					&&canTrigger(48)
					&&((msg.amISource(monster))
						||(msg.tool()==affecting)
						||(affecting instanceof Room)
						||(affecting instanceof Area))
					&&(msg.tool() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.tool(),affecting,monster,msg.source(),msg.target(),(Item)msg.tool(),defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.tool(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 49: // cast_prog
					if((msg.tool() instanceof Ability)
					&&canTrigger(49)
					&&((msg.amITarget(monster))
						||(affecting instanceof Room)
						||(affecting instanceof Area))
					&&(!msg.amISource(monster))
					&&(msg.sourceMinor()!=CMMsg.TYP_TEACH)
					&&(msg.sourceMinor()!=CMMsg.TYP_ITEMSGENERATED)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.tool(), affecting,monster,msg.source(),monster,defaultItem,null,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 50: // casting_prog
					if((msg.tool() instanceof Ability)
					&&canTrigger(50)
					&&((msg.amISource(monster))
						||(affecting instanceof Room)
						||(affecting instanceof Area))
					&&(msg.sourceMinor()!=CMMsg.TYP_TEACH)
					&&(msg.sourceMinor()!=CMMsg.TYP_ITEMSGENERATED)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.tool(),affecting,monster,msg.source(),msg.target(),null,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 40: // llook_prog
					if((msg.targetMinor()==CMMsg.TYP_EXAMINE)&&canTrigger(40)
					&&(!msg.amISource(monster))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),defaultItem,null,t);
						if(check!=null)
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,null,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 41: // execmsg_prog
					if(canTrigger(41))
					{
						if(t==null)
							t=parseBits(script,0,"CCT");
						if(t!=null)
						{
							// ST12: EXECMSG_PROG @GLOBAL type ..., heard once wherever it happens
							if((t.length>1)&&t[1].equals("@GLOBAL"))
							{
								if(!firstGlobalHearing(msg,script))
									break;
								t=parseBits(stripGlobal(script.get(0).first),"CCT");
							}
							final String command=t[1];
							boolean chk=false;
							final int x=command.indexOf('=');
							if(x>0)
							{
								chk=true;
								boolean minorOnly = false;
								boolean majorOnly = false;
								for(int i=0;i<x;i++)
								{
									switch(command.charAt(i))
									{
										case 'S':
											if(majorOnly)
												chk=chk&&msg.isSourceMajor(command.substring(x+1));
											else
											if(minorOnly)
												chk=chk&&msg.isSourceMinor(command.substring(x+1));
											else
												chk=chk&&msg.isSource(command.substring(x+1));
											break;
										case 'T':
											if(majorOnly)
												chk=chk&&msg.isTargetMajor(command.substring(x+1));
											else
											if(minorOnly)
												chk=chk&&msg.isTargetMinor(command.substring(x+1));
											else
												chk=chk&&msg.isTarget(command.substring(x+1));
											break;
										case 'O':
											if(majorOnly)
												chk=chk&&msg.isOthersMajor(command.substring(x+1));
											else
											if(minorOnly)
												chk=chk&&msg.isOthersMinor(command.substring(x+1));
											else
												chk=chk&&msg.isOthers(command.substring(x+1));
											break;
										case '<':
											minorOnly=true;
											majorOnly=false;
											break;
										case '>':
											majorOnly=true;
											minorOnly=false;
											break;
										case '?':
											majorOnly=false;
											minorOnly=false;
											break;
										default:
											chk=false;
											break;
									}
								}
							}
							else
							if(command.startsWith(">"))
							{
								final String cmd=command.substring(1);
								chk=msg.isSourceMajor(cmd)||msg.isTargetMajor(cmd)||msg.isOthersMajor(cmd);
							}
							else
							if(command.startsWith("<"))
							{
								final String cmd=command.substring(1);
								chk=msg.isSourceMinor(cmd)||msg.isTargetMinor(cmd)||msg.isOthersMinor(cmd);
							}
							else
							if(command.startsWith("?"))
							{
								final String cmd=command.substring(1);
								chk=msg.isSource(cmd)||msg.isTarget(cmd)||msg.isOthers(cmd);
							}
							else
								chk=msg.isSource(command)||msg.isTarget(command)||msg.isOthers(command);
							if(chk)
							{
								String str="";
								final Session S = msg.source().session();
								if((S!=null)
								&&(S.getHistory().size()>0))
									str=" "+CMParms.combine(S.getHistory().getLast(),0).toUpperCase()+" ";
								boolean doIt=false;
								if((t[2].length()==0)||(t[2].equals("ALL")))
									doIt=true;
								else
								if((t[2].equals("P"))&&(t.length>3))
								{
									if(match(str.trim(),t[3]))
										doIt=true;
								}
								else
								if(t[2].startsWith("=")
								&&(t[2].length()==2)
								&&(t.length>3)&&(t[3].indexOf('=')>0))
								{
									final int q = t[3].indexOf('=');
									String val;
									switch(t[2].charAt(1))
									{
									case 's': case 'S':
										val=((msg.source()==null)?"":
											CMLib.coffeeMaker().getAnyGenStat(msg.source(), t[3].substring(0,q)));
										break;
									case 't': case 'T':
										val=((msg.target()==null)?"":
											CMLib.coffeeMaker().getAnyGenStat(msg.target(), t[3].substring(0,q)));
										break;
									case 'o': case 'O':
										val=((msg.tool()==null)?"":
											CMLib.coffeeMaker().getAnyGenStat(msg.tool(), t[3].substring(0,q)));
										break;
									default:
										val="";
										break;
									}
									if(msgFilterMatch(msg,t,q,val))
										doIt=true;
								}
								else
								for(int i=2;i<t.length;i++)
								{
									if(str.indexOf(" "+t[i]+" ")>=0)
									{
										str=(t[i].trim()+" "+str.trim()).trim();
										doIt=true;
										break;
									}
								}
								if(doIt)
								{
									Item Tool=null;
									if(msg.tool() instanceof Item)
										Tool=(Item)msg.tool();
									if(Tool==null)
										Tool=defaultItem;
									queuedMsg=(CMMsg)msg.copyOf(); // SC5: MSG() reads a copy, as messages are reused
									if(msg.target() instanceof MOB)
										enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
									else
									if(msg.target() instanceof Item)
										enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,(Item)msg.target(),str,script,1, t);
									else
										enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
									queuedMsg=null;
									if(!multiTriggers)
										return;
								}
							}
						}
					}
					break;
				case 39: // look_prog
					if((msg.targetMinor()==CMMsg.TYP_LOOK)&&canTrigger(39)
					&&(!msg.amISource(monster))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),defaultItem,defaultItem,t);
						if(check!=null)
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 20: // get_prog
					if((msg.targetMinor()==CMMsg.TYP_GET)&&canTrigger(20)
					&&(msg.amITarget(affecting)
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||(affecting instanceof MOB))
					&&(!msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						Item checkInE=(Item)msg.target();
						if((msg.tool() instanceof Item)
						&&(((Item)msg.tool()).container()==msg.target()))
							checkInE=(Item)msg.tool();
						final String check=standardTriggerCheck(script,t,checkInE,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 54: // getting_prog
					if((msg.targetMinor()==CMMsg.TYP_GET)&&canTrigger(20)&&canTrigger(54)
					&&(msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						Item checkInE=(Item)msg.target();
						if((msg.tool() instanceof Item)
						&&(((Item)msg.tool()).container()==msg.target()))
							checkInE=(Item)msg.tool();
						final String check=standardTriggerCheck(script,t,checkInE,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 57: // wearing_prog
					if(((msg.targetMinor()==CMMsg.TYP_WEAR)
						||(msg.targetMinor()==CMMsg.TYP_HOLD)
						||(msg.targetMinor()==CMMsg.TYP_WIELD))
					&&canTrigger(57)&&canTrigger(23)
					&&(msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						Item checkInE=(Item)msg.target();
						if((msg.tool() instanceof Item)
						&&(((Item)msg.tool()).container()==msg.target()))
							checkInE=(Item)msg.tool();
						final String check=standardTriggerCheck(script,t,checkInE,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 60: // removing_prog
					if((msg.targetMinor()==CMMsg.TYP_REMOVE)
					&&canTrigger(60)&&canTrigger(24)
					&&(msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final Item checkInE=(Item)msg.target();
						final String check=standardTriggerCheck(script,t,checkInE,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),checkInE,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 56: // riding_prog
					if((msg.targetMinor()==CMMsg.TYP_MOUNT)
					&&canTrigger(56)
					&&(msg.amISource(monster))
					&&(msg.target() instanceof Rideable)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final Environmental checkInE=msg.target();
						final String check=standardTriggerCheck(script,t,checkInE,affecting,monster,msg.source(),msg.target(),defaultItem,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 55: // ride_prog
				case 59: // dismount_prog
					// anyone mounting or dismounting the host, or, for a room,
					// area or mob, any rideable there
					if((msg.targetMinor()==((triggerCode==55)?CMMsg.TYP_MOUNT:CMMsg.TYP_DISMOUNT))
					&&canTrigger(triggerCode)
					&&((msg.amITarget(affecting))
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||(affecting instanceof MOB))
					&&(!msg.amISource(monster))
					&&(msg.target() instanceof Rideable)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final Environmental checkInE=msg.target();
						final String check=standardTriggerCheck(script,t,checkInE,affecting,monster,msg.source(),msg.target(),defaultItem,defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),defaultItem,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 22: // drop_prog
					if((msg.targetMinor()==CMMsg.TYP_DROP)&&canTrigger(22)
					&&((msg.amITarget(affecting))
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||(affecting instanceof MOB))
					&&(!msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							if(msg.target() instanceof Coins)
								execute(new MPContext(affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.target().copyOf(),check, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 53: // dropping_prog
					if((msg.targetMinor()==CMMsg.TYP_DROP)&&canTrigger(22)
					&&(msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							if(msg.target() instanceof Coins)
								execute(new MPContext(affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.target().copyOf(),check, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 24: // remove_prog
					if((msg.targetMinor()==CMMsg.TYP_REMOVE)&&canTrigger(24)
					&&((msg.amITarget(affecting))
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||(affecting instanceof MOB))
					&&(!msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,t);
						if(check!=null)
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 34: // open_prog
					if(targetMinorTrigger<0)
						targetMinorTrigger=CMMsg.TYP_OPEN;
					//$FALL-THROUGH$
				case 35: // close_prog
					if(targetMinorTrigger<0)
						targetMinorTrigger=CMMsg.TYP_CLOSE;
					//$FALL-THROUGH$
				case 36: // lock_prog
					if(targetMinorTrigger<0)
						targetMinorTrigger=CMMsg.TYP_LOCK;
					//$FALL-THROUGH$
				case 37: // unlock_prog
				{
					if(targetMinorTrigger<0)
						targetMinorTrigger=CMMsg.TYP_UNLOCK;
					if((msg.targetMinor()==targetMinorTrigger)&&canTrigger(triggerCode)
					&&((msg.amITarget(affecting))
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||(affecting instanceof MOB))
					&&(!msg.amISource(monster))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final Item I=(msg.target() instanceof Item)?(Item)msg.target():defaultItem;
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),I,defaultItem,t);
						if(check!=null)
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),I,defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				}
				case 25: // consume_prog
					if(((msg.targetMinor()==CMMsg.TYP_EAT)||(msg.targetMinor()==CMMsg.TYP_DRINK))
					&&((msg.amITarget(affecting))||(affecting instanceof Room)||(affecting instanceof Area)||(affecting instanceof MOB))
					&&(!msg.amISource(monster))&&canTrigger(25)
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,t);
						if(check!=null)
						{
							if((msg.target() == affecting)
							&&(affecting instanceof Food))
								execute(new MPContext(affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.target().copyOf(),check, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 21: // put_prog
					if((msg.targetMinor()==CMMsg.TYP_PUT)&&canTrigger(21)
					&&((msg.amITarget(affecting))
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||(affecting instanceof MOB))
					&&(msg.tool() instanceof Item)
					&&(!msg.amISource(monster))
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.tool(),t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							if((msg.tool() instanceof Coins)&&(((Item)msg.target()).owner() instanceof Room))
								execute(new MPContext(affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.target().copyOf(),check, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.tool(),check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 46: // putting_prog
					if((msg.targetMinor()==CMMsg.TYP_PUT)&&canTrigger(46)
					&&((msg.tool()==affecting)
						||(affecting instanceof Room)
						||(affecting instanceof Area)
						||((affecting instanceof MOB)&&(msg.source()==affecting)))
					&&(msg.tool() instanceof Item)
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.tool(),affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.tool(),t);
						if(check!=null)
						{
							if(lastMsg==msg)
								break;
							lastMsg=msg;
							if((msg.tool() instanceof Coins)&&(((Item)msg.target()).owner() instanceof Room))
								execute(new MPContext(affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.target().copyOf(),check, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),(Item)msg.target(),(Item)msg.tool(),check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 27: // buy_prog
					if((msg.targetMinor()==CMMsg.TYP_BUY)&&canTrigger(27)
					&&((!(affecting instanceof ShopKeeper))
						||msg.amITarget(affecting))
					&&(!msg.amISource(monster))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final Item chItem = (msg.tool() instanceof Item)?((Item)msg.tool()):null;
						final String check=standardTriggerCheck(script,t,msg.tool(),affecting,monster,msg.source(),monster,chItem,chItem,t);
						if(check!=null)
						{
							final Item product=makeCheapItem(msg.tool());
							if((product instanceof Coins)
							&&(product.owner() instanceof Room))
								execute(new MPContext(affecting,monster,msg.source(),monster,product,(Item)product.copyOf(),check, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,product,product,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 28: // sell_prog
					if((msg.targetMinor()==CMMsg.TYP_SELL)&&canTrigger(28)
					&&((msg.amITarget(affecting))||(!(affecting instanceof ShopKeeper)))
					&&(!msg.amISource(monster))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final Item chItem = (msg.tool() instanceof Item)?((Item)msg.tool()):null;
						final String check=standardTriggerCheck(script,t,msg.tool(),affecting,monster,msg.source(),monster,chItem,chItem,t);
						if(check!=null)
						{
							final Item product=makeCheapItem(msg.tool());
							if((product instanceof Coins)
							&&(product.owner() instanceof Room))
								execute(new MPContext(affecting,monster,msg.source(),monster,product,(Item)product.copyOf(),null, null).push(script));
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,product,product,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 23: // wear_prog
					if(((msg.targetMinor()==CMMsg.TYP_WEAR)
						||(msg.targetMinor()==CMMsg.TYP_HOLD)
						||(msg.targetMinor()==CMMsg.TYP_WIELD))
					&&((msg.amITarget(affecting))||(affecting instanceof Room)||(affecting instanceof Area)||(affecting instanceof MOB))
					&&(!msg.amISource(monster))&&canTrigger(23)
					&&(msg.target() instanceof Item)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						final String check=standardTriggerCheck(script,t,msg.target(),affecting,monster,msg.source(),monster,(Item)msg.target(),defaultItem,t);
						if(check!=null)
						{
							enqueResponse(triggerCode,affecting,monster,msg.source(),monster,(Item)msg.target(),defaultItem,check,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 19: // bribe_prog
					if((msg.targetMinor()==CMMsg.TYP_GIVE)
					&&(msg.amITarget(eventMob)||(!(affecting instanceof MOB)))
					&&(!msg.amISource(monster))&&canTrigger(19)
					&&(msg.tool() instanceof Coins)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							if(t[1].startsWith("ANY")||t[1].startsWith("ALL"))
								t[1]=t[1].trim();
							else
							if(!CMLib.beanCounter().isCurrencyMatch(((Coins)msg.tool()).getCurrency(),CMLib.beanCounter().getCurrency(monster)))
								break;
							double d=0.0;
							if(CMath.isDouble(t[1]))
								d=CMath.s_double(t[1]);
							else
								d=CMath.s_int(t[1]);
							if((((Coins)msg.tool()).getTotalValue()>=d)
							||(t[1].equals("ALL"))
							||(t[1].equals("ANY")))
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,(Item)msg.tool(),defaultItem,null,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 8: // entry_prog
					if((msg.targetMinor()==CMMsg.TYP_ENTER)&&canTrigger(8)
					&&(msg.amISource(eventMob)
						||(msg.target()==affecting)
						||(msg.tool()==affecting)
						||(affecting instanceof Item))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								final List<ScriptableResponse> V=new XVector<ScriptableResponse>(que);
								ScriptableResponse SB=null;
								String roomID=null;
								if(msg.target()!=null)
									roomID=CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(msg.target()));
								for(int q=0;q<V.size();q++)
								{
									SB=V.get(q);
									if((SB.scr==script)&&(SB.ctx.source==msg.source()))
									{
										if(que.remove(SB))
											execute(SB.ctx.push(SB.scr));
										break;
									}
								}
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,roomID,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 9: // exit_prog
					if((msg.targetMinor()==CMMsg.TYP_LEAVE)&&canTrigger(9)
					&&(msg.amITarget(lastKnownLocation))
					&&(!msg.amISource(eventMob))
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster)))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								final List<ScriptableResponse> V=new XVector<ScriptableResponse>(que);
								ScriptableResponse SB=null;
								String roomID=null;
								if(msg.target()!=null)
									roomID=CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(msg.target()));
								for(int q=0;q<V.size();q++)
								{
									SB=V.get(q);
									if((SB.scr==script)&&(SB.ctx.source==msg.source()))
									{
										if(que.remove(SB))
											execute(SB.ctx.push(SB.scr));
										break;
									}
								}
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,roomID,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 10: // death_prog
					if((msg.sourceMinor()==CMMsg.TYP_DEATH)&&canTrigger(10)
					&&(msg.amISource(eventMob)||(!(affecting instanceof MOB))||isGlobalLine(script.get(0).first)))
					{
						if(t==null)
							t=parseBits(script,0,"C");
						// ST12: DEATH_PROG @GLOBAL [mask on the dead]: any death, heard once
						if(isGlobalLine(script.get(0).first)
						&&((!firstGlobalHearing(msg,script))||(!globalMaskOk(script.get(0).first,msg.source()))))
							break;
						final MOB ded=msg.source();
						MOB src=lastToHurtMe;
						if(msg.tool() instanceof MOB)
							src=(MOB)msg.tool();
						if((src==null)||(src.location()!=monster.location()))
							src=ded;
						execute(new MPContext(affecting,ded,src,ded,defaultItem,null,null, null).push(script));
						if(!multiTriggers)
							return;
					}
					break;
				case 44: // kill_prog
					if((msg.sourceMinor()==CMMsg.TYP_DEATH)&&canTrigger(44)
					&&((msg.tool()==affecting)||(!(affecting instanceof MOB))||isGlobalLine(script.get(0).first)))
					{
						if(t==null)
							t=parseBits(script,0,"C");
						// ST12: KILL_PROG @GLOBAL [mask on the killer]: any kill, heard once
						if(isGlobalLine(script.get(0).first)
						&&((!firstGlobalHearing(msg,script))||(!(msg.tool() instanceof MOB))||(!globalMaskOk(script.get(0).first,(MOB)msg.tool()))))
							break;
						final MOB ded=msg.source();
						MOB src=lastToHurtMe;
						if(msg.tool() instanceof MOB)
							src=(MOB)msg.tool();
						if((src==null)||(src.location()!=monster.location()))
							src=ded;
						execute(new MPContext(affecting,ded,src,ded,defaultItem,null,null, null).push(script));
						if(!multiTriggers)
							return;
					}
					break;
				case 26: // damage_prog
					if((msg.targetMinor()==CMMsg.TYP_DAMAGE)&&canTrigger(26)
					&&(msg.amITarget(eventMob)||(msg.tool()==affecting)))
					{
						if(t==null)
							t=parseBits(script,0,"C");
						Item I=null;
						if(msg.tool() instanceof Item)
							I=(Item)msg.tool();
						execute(new MPContext(affecting,eventMob,msg.source(),msg.target(),defaultItem,I,""+msg.value(), null).push(script));
						if(!multiTriggers)
							return;
					}
					break;
				case 29: // login_prog
					if(!registeredEvents.contains(Integer.valueOf(CMMsg.TYP_LOGIN)))
					{
						CMLib.map().addGlobalHandler(affecting,CMMsg.TYP_LOGIN);
						registeredEvents.add(Integer.valueOf(CMMsg.TYP_LOGIN));
					}
					if((msg.sourceMinor()==CMMsg.TYP_LOGIN)&&canTrigger(29)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&(!CMLib.flags().isCloaked(msg.source())))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 32: // level_prog
					if(!registeredEvents.contains(Integer.valueOf(CMMsg.TYP_LEVEL)))
					{
						CMLib.map().addGlobalHandler(affecting,CMMsg.TYP_LEVEL);
						registeredEvents.add(Integer.valueOf(CMMsg.TYP_LEVEL));
					}
					if((msg.sourceMinor()==CMMsg.TYP_LEVEL)&&canTrigger(32)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&(msg.value() != msg.source().basePhyStats().level())
					&&(!CMLib.flags().isCloaked(msg.source())))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							// ST17: [GAIN|LOSS|ANY] [pct|mask], GAIN when not said; heard once, from the room or the world
							final boolean gain=msg.value()>msg.source().basePhyStats().level();
							String kind="GAIN";
							String rest=t[1];
							final String first=CMParms.getCleanBit(t[1],0).toUpperCase();
							if(first.equals("GAIN")||first.equals("LOSS")||first.equals("ANY"))
							{
								kind=first;
								rest=CMParms.getPastBitClean(t[1],0);
							}
							if((kind.equals("GAIN")&&(!gain))||(kind.equals("LOSS")&&gain)||(!firstGlobalHearing(msg,script)))
								break;
							final int prcnt=(rest.trim().length()==0)?100:this.getTriggerPercent(rest, msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								// $g the new level; $0 to $9 the trigger's words, as SC14 has them
								final MPContext ctx = new MPContext(affecting,monster,msg.source(),monster,defaultItem,null,""+msg.value(), null);
								for(int i=0;i<t.length && (i<ctx.tmp.length);i++)
									ctx.tmp[i]=t[i];
								execute(ctx.push(script));
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 30: // logoff_prog
					if((msg.sourceMinor()==CMMsg.TYP_QUIT)&&canTrigger(30)
					&&((!(affecting instanceof MOB)) || isFreeToBeTriggered(monster))
					&&((!CMLib.flags().isCloaked(msg.source()))||(!CMSecurity.isASysOp(msg.source()))))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=this.getTriggerPercent(t[1], msg.source());
							if(chance(affecting,script,prcnt)) // 100 is always
							{
								enqueResponse(triggerCode,affecting,monster,msg.source(),monster,defaultItem,null,null,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 12: // mask_prog
				{
					if(!canTrigger(12))
						break;
				}
				//$FALL-THROUGH$
				case 18: // act_prog
					if((msg.amISource(monster))
					||((triggerCode==18)&&(!canTrigger(18))))
						break;
				//$FALL-THROUGH$
				case 43: // imask_prog
					if((triggerCode!=43)||(msg.amISource(monster)&&canTrigger(43)))
					{
						if(t==null)
						{
							t=parseBits(script,0,"CT");
							for(int i=1;i<t.length;i++)
								t[i]=CMLib.english().stripPunctuation(CMStrings.removeColors(t[i]));
						}
						boolean doIt=false;
						String str=msg.othersMessage();
						if(str==null)
							str=msg.targetMessage();
						if(str==null)
							str=msg.sourceMessage();
						if(str==null)
							break;
						str=CMLib.coffeeFilter().fullOutFilter(null,monster,msg.source(),msg.target(),msg.tool(),str,false);
						str=CMLib.english().stripPunctuation(CMStrings.removeColors(str));
						str=" "+CMStrings.replaceAll(str,"\n\r"," ").toUpperCase().trim()+" ";
						if((t[1].length()==0)||(t[1].equals("ALL")))
							doIt=true;
						else
						if((t[1].equals("P"))&&(t.length>2))
						{
							if(match(str.trim(),t[2]))
								doIt=true;
						}
						else
						for(int i=1;i<t.length;i++)
						{
							if(str.indexOf(" "+t[i]+" ")>=0)
							{
								str=t[i];
								doIt=true;
								break;
							}
						}
						if(doIt)
						{
							Item Tool=null;
							if(msg.tool() instanceof Item)
								Tool=(Item)msg.tool();
							if(Tool==null)
								Tool=defaultItem;
							if(msg.target() instanceof MOB)
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
							else
							if(msg.target() instanceof Item)
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,(Item)msg.target(),str,script,1, t);
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 38: // social_prog
					if(!msg.amISource(monster)
					&&canTrigger(38)
					&&(msg.tool() instanceof Social))
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						// ST22: [EXACT] [ME] name
						if((t!=null)
						&&ScriptEvents.socialMatches(t[1],msg.tool().Name(),msg.target()==monster))
						{
							final Item Tool=defaultItem;
							if(msg.target() instanceof MOB)
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,msg.tool().Name(),script,1, t);
							else
							if(msg.target() instanceof Item)
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,(Item)msg.target(),msg.tool().Name(),script,1, t);
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,msg.tool().Name(),script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				case 33: // channel_prog
					if(!registeredEvents.contains(Integer.valueOf(CMMsg.TYP_CHANNEL)))
					{
						CMLib.map().addGlobalHandler(affecting,CMMsg.TYP_CHANNEL);
						registeredEvents.add(Integer.valueOf(CMMsg.TYP_CHANNEL));
					}
					if(!msg.amISource(monster)
					&&(msg.othersMajor(CMMsg.MASK_CHANNEL))
					&&canTrigger(33))
					{
						if(t==null)
							t=parseBits(script,0,"CCT");
						boolean doIt=false;
						if(t!=null)
						{
							final String channel=t[1];
							final int channelInt=msg.othersMinor()-CMMsg.TYP_CHANNEL;
							String str=null;
							final CMChannel officialChannel=CMLib.channels().getChannel(channelInt);
							if(officialChannel==null)
								Log.errOut("Script","Unknown channel for code '"+channelInt+"': "+msg.othersMessage());
							else
							if(channel.equalsIgnoreCase(officialChannel.name()))
							{
								str=msg.sourceMessage();
								if(str==null)
									str=msg.othersMessage();
								if(str==null)
									str=msg.targetMessage();
								if(str==null)
									break;
								str=CMLib.coffeeFilter().fullOutFilter(null,monster,msg.source(),msg.target(),msg.tool(),str,false).toUpperCase().trim();
								int dex=str.indexOf("["+channel+"]");
								if(dex>0)
									str=str.substring(dex+2+channel.length()).trim();
								else
								{
									dex=str.indexOf('\'');
									final int edex=str.lastIndexOf('\'');
									if(edex>dex)
										str=str.substring(dex+1,edex);
								}
								str=" "+CMStrings.removeColors(str)+" ";
								str=CMStrings.replaceAll(str,"\n\r"," ");
								if((t[2].length()==0)||(t[2].equals("ALL")))
									doIt=true;
								else
								if(t[2].equals("P")&&(t.length>2))
								{
									if(match(str.trim(),t[3]))
										doIt=true;
								}
								else
								for(int i=2;i<t.length;i++)
								{
									if(str.indexOf(" "+t[i]+" ")>=0)
									{
										str=t[i];
										doIt=true;
										break;
									}
								}
							}
							if(doIt)
							{
								Item Tool=null;
								if(msg.tool() instanceof Item)
									Tool=(Item)msg.tool();
								if(Tool==null)
									Tool=defaultItem;
								if(msg.target() instanceof MOB)
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
								else
								if(msg.target() instanceof Item)
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,(Item)msg.target(),str,script,1, t);
								else
									enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
								if(!multiTriggers)
									return;
							}
						}
					}
					break;
				case 31: // regmask_prog
					if(!msg.amISource(monster)&&canTrigger(31))
					{
						boolean doIt=false;
						String str=msg.othersMessage();
						if(str==null)
							str=msg.targetMessage();
						if(str==null)
							str=msg.sourceMessage();
						if(str==null)
							break;
						str=CMLib.coffeeFilter().fullOutFilter(null,monster,msg.source(),msg.target(),msg.tool(),str,false);
						if(t==null)
							t=parseBits(script,0,"Cp");
						if(t!=null)
						{
							if(CMParms.getCleanBit(t[1],0).equalsIgnoreCase("p"))
								doIt=str.trim().equals(t[1].substring(1).trim());
							else
							{
								Pattern P=patterns.get(t[1]);
								if(P==null)
								{
									P=Pattern.compile(t[1], Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
									patterns.put(t[1],P);
								}
								final Matcher M=P.matcher(str);
								doIt=M.find();
								if(doIt)
									str=str.substring(M.start()).trim();
							}
						}
						if(doIt)
						{
							Item Tool=null;
							if(msg.tool() instanceof Item)
								Tool=(Item)msg.tool();
							if(Tool==null)
								Tool=defaultItem;
							if(msg.target() instanceof MOB)
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
							else
							if(msg.target() instanceof Item)
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,(Item)msg.target(),str,script,1, t);
							else
								enqueResponse(triggerCode,affecting,monster,msg.source(),msg.target(),Tool,defaultItem,str,script,1, t);
							if(!multiTriggers)
								return;
						}
					}
					break;
				}
			}
		}
		finally
		{
			recurseCounter.addAndGet(-1);
		}
	}

	@Override
	public MOB getMakeMOB(final Tickable ticking)
	{
		MOB mob=null;
		if(ticking instanceof MOB)
		{
			mob=(MOB)ticking;
			if(!mob.amDead())
				lastKnownLocation=mob.location();
		}
		else
		if(ticking instanceof Environmental)
		{
			final Room R=CMLib.map().roomLocation((Environmental)ticking);
			if(R!=null)
				lastKnownLocation=R;

			if((backupMOB==null)
			||(backupMOB.amDestroyed())
			||(backupMOB.amDead()))
			{
				backupMOB=CMClass.getMOB("StdMOB");
				if(backupMOB!=null)
				{
					backupMOB.setName(ticking.name());
					backupMOB.setDisplayText(L("@x1 is here.",ticking.name()));
					backupMOB.setDescription("");
					backupMOB.setAgeMinutes(-1);
					mob=backupMOB;
					if(backupMOB.location()!=lastKnownLocation)
						backupMOB.setLocation(lastKnownLocation);
				}
			}
			else
			{
				backupMOB.setAgeMinutes(-1);
				mob=backupMOB;
				if(backupMOB.location()!=lastKnownLocation)
				{
					backupMOB.setLocation(lastKnownLocation);
					backupMOB.setName(ticking.name());
					backupMOB.setDisplayText(L("@x1 is here.",ticking.name()));
				}
			}
		}
		return mob;
	}

	@Override
	public boolean eventCheck(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test)
	{
		if((host==null)||(source==null)||(recurseCounter.get() > 2)||(!canTrigger(triggerCode)))
			return true;
		try
		{
			recurseCounter.addAndGet(1);
			final MOB monster=getMakeMOB(host);
			if(lastKnownLocation==null)
				confirmLastKnownLocation(monster, source);
			if((lastKnownLocation==null)&&(host instanceof Area))
			{
				for(final Enumeration<Room> r=((Area)host).getProperMap();r.hasMoreElements()&&(lastKnownLocation==null);)
					lastKnownLocation=r.nextElement();
			}
			if((monster==null)||(monster.amDead())||(lastKnownLocation==null))
				return true;
			final Item defaultItem=(host instanceof Item)?(Item)host:null;
			for(final SubScript script : getScripts(host))
			{
				if((script.size()<1)||(script.getTriggerCode()!=triggerCode))
					continue;
				String[] t=script.getTriggerArgs();
				if(t==null)
					t=parseBits(script,0,bits);
				if((t!=null)&&test.test(t))
				{
					final MPContext ctx=new MPContext(host,monster,source,(target!=null)?target:monster,(item!=null)?item:defaultItem,defaultItem,msg,args);
					final String resp=execute(ctx.push(script));
					if((resp!=null)&&(resp.equalsIgnoreCase("CANCEL")))
						return false;
				}
			}
			return true;
		}
		finally
		{
			recurseCounter.addAndGet(-1);
		}
	}

	@Override
	public List<String[]> triggerLines(final PhysicalAgent host, final int triggerCode, final String bits)
	{
		final List<String[]> lines=new ArrayList<String[]>();
		if(host==null)
			return lines;
		for(final SubScript script : getScripts(host))
		{
			if((script.size()<1)||(script.getTriggerCode()!=triggerCode))
				continue;
			String[] t=script.getTriggerArgs();
			if(t==null)
				t=parseBits(script,0,bits);
			if(t!=null)
				lines.add(t);
		}
		return lines;
	}

	@Override
	public String eventRun(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test)
	{
		if((host==null)||(source==null)||(recurseCounter.get() > 2)||(!canTrigger(triggerCode)))
			return null;
		try
		{
			recurseCounter.addAndGet(1);
			final MOB monster=getMakeMOB(host);
			if(lastKnownLocation==null)
				confirmLastKnownLocation(monster, source);
			if((lastKnownLocation==null)&&(host instanceof Area))
			{
				for(final Enumeration<Room> r=((Area)host).getProperMap();r.hasMoreElements()&&(lastKnownLocation==null);)
					lastKnownLocation=r.nextElement();
			}
			if((monster==null)||(monster.amDead())||(lastKnownLocation==null))
				return null;
			if((host instanceof MOB)&&(!isFreeToBeTriggered(monster)))
				return null;
			final Item defaultItem=(host instanceof Item)?(Item)host:null;
			for(final SubScript script : getScripts(host))
			{
				if((script.size()<1)||(script.getTriggerCode()!=triggerCode))
					continue;
				String[] t=script.getTriggerArgs();
				if(t==null)
					t=parseBits(script,0,bits);
				if((t!=null)&&test.test(t))
				{
					final MPContext ctx=new MPContext(host,monster,source,(target!=null)?target:monster,(item!=null)?item:defaultItem,defaultItem,msg,args);
					final String resp=execute(ctx.push(script));
					return (resp==null)?"":resp;
				}
			}
			return null;
		}
		finally
		{
			recurseCounter.addAndGet(-1);
		}
	}

	@Override
	public void eventTrigger(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test)
	{
		eventTrigger(host,triggerCode,source,target,item,msg,args,bits,test,1);
	}

	@Override
	public void eventTrigger(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test, final int ticks)
	{
		if((host==null)||(source==null)||(recurseCounter.get() > 2)||(!canTrigger(triggerCode)))
			return;
		try
		{
			recurseCounter.addAndGet(1);
			if(ScriptTrace.isOn())
				ScriptTrace.handling(host);
			final MOB monster=getMakeMOB(host);
			if(lastKnownLocation==null)
				confirmLastKnownLocation(monster, source);
			if((lastKnownLocation==null)&&(host instanceof Area))
			{
				// an area hearing an event from no room: one of its own rooms
				for(final Enumeration<Room> r=((Area)host).getProperMap();r.hasMoreElements()&&(lastKnownLocation==null);)
					lastKnownLocation=r.nextElement();
			}
			if((monster==null)||(monster.amDead())||(lastKnownLocation==null))
				return;
			if((host instanceof MOB)&&(!isFreeToBeTriggered(monster)))
				return;
			final Item defaultItem=(host instanceof Item)?(Item)host:null;
			// ST12: bits starting with @ are for hosts away from the event: only @GLOBAL lines
			final boolean globalPass=bits.startsWith("@");
			final String split=globalPass?bits.substring(1):bits;
			for(final SubScript script : getScripts(host))
			{
				if((script.size()<1)||(script.getTriggerCode()!=triggerCode))
					continue;
				final boolean globalLine=isGlobalLine(script.get(0).first);
				if(globalPass&&(!globalLine))
					continue;
				String[] t=globalLine?parseBits(stripGlobal(script.get(0).first),split):script.getTriggerArgs();
				if(t==null)
					t=parseBits(script,0,split);
				if((t!=null)&&test.test(t))
				{
					if(args==null)
						enqueResponse(triggerCode,host,monster,source,(target!=null)?target:monster,(item!=null)?item:defaultItem,defaultItem,msg,script,Math.max(1,ticks),t);
					else
					if(CMProps.isState(CMProps.HostState.RUNNING))
					{
						// $0 and on: the event's own values
						final MPContext ctx=new MPContext(host,monster,source,(target!=null)?target:monster,(item!=null)?item:defaultItem,defaultItem,msg,args);
						if(noDelay)
							execute(ctx.push(script));
						else
						{
							final ScriptableResponse resp=new ScriptableResponse(triggerCode,ctx,script,Math.max(1,ticks));
							dupCheckClear(resp,t);
							que.add(resp);
						}
					}
					if(!multiTriggers)
						return;
				}
			}
		}
		finally
		{
			recurseCounter.addAndGet(-1);
		}
	}

	protected boolean canTrigger(final int triggerCode)
	{
		final Long L=noTrigger.get(Integer.valueOf(triggerCode));
		if(L==null)
			return true;
		if(GameTime.now()<L.longValue())
		{
			final Object host=ScriptTrace.handled();
			if((host!=null)&&ScriptTrace.traces(host,triggerCode)&&(triggerCode>0)&&(triggerCode<=progs.length))
				ScriptTrace.tell(host,progs[triggerCode-1]+": off (MPNOTRIGGER or SCRIPT DISABLE), skipped");
			return false;
		}
		noTrigger.remove(Integer.valueOf(triggerCode));
		return true;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		// a restored game's scripts wait until the world is as it was saved,
		// so that a ONCE_PROG that already ran does not run again first
		if(SaveGame.isWorldPending())
			return true;
		final MOB mob;
		synchronized(this) // supposedly this will cause a sync between cpus of the object
		{
			mob=getMakeMOB(ticking);
		}
		if((mob==null)||(lastKnownLocation==null))
		{
			altStatusTickable=null;
			return true;
		}
		final PhysicalAgent affecting=(ticking instanceof PhysicalAgent)?((PhysicalAgent)ticking):null;
		if(ScriptTrace.isOn())
			ScriptTrace.handling(affecting);
		final Item defaultItem=(ticking instanceof Item)?(Item)ticking:null;
		final List<SubScript> scripts=getScripts(affecting);

		if(!runInPassiveAreas)
		{
			final Area A=CMLib.map().areaLocation(ticking);
			if((A!=null)&&(A.getAreaState() != Area.State.ACTIVE))
			{
				if(this.que.size()>0)
					this.que.clear();
				return true;
			}
		}
		if(!runWithoutPCs)
		{
			if((lastKnownLocation !=null)
			&&(lastKnownLocation.numPCInhabitants()==0))
			{
				dequeResponses(null, true);
				return true;
			}
		}
		if(defaultItem != null)
		{
			final ItemPossessor poss=defaultItem.owner();
			if(poss instanceof MOB)
			{
				final Room R=((MOB)poss).location();
				if((R==null)||(R.numInhabitants()==0))
				{
					altStatusTickable=null;
					return true;
				}
			}
		}

		int triggerCode=-1;
		String[] t=null;
		// ST15: COMBAT_PROG's START and END, from the fight as it was at the last tick
		String combatEvent=null;
		for(final SubScript s : scripts)
		{
			if(s.getTriggerCode()==78)
			{
				combatEvent=combatChange(affecting);
				break;
			}
		}
		for(int thisScriptIndex=0;thisScriptIndex<scripts.size();thisScriptIndex++)
		{
			final SubScript script=scripts.get(thisScriptIndex);
			if(script.size()<2)
				continue;
			t=script.getTriggerArgs();
			triggerCode=script.getTriggerCode();
			tickStatus=Tickable.STATUS_SCRIPT+triggerCode;
			switch(triggerCode)
			{
			case 0:
				logError(affecting, "?", "BAD_TRIGGER", script.get(0).first+"' is not a valid trigger.");
				if(thisScriptIndex<scripts.size())
				{
					scripts.remove(thisScriptIndex);
					thisScriptIndex--;
				}
				break;
			case 91: // every_prog (ST24): every N HOURS|DAYS|WEEKS|MONTHS of the area's clock, [CATCHUP]
				if((!mob.amDead())&&canTrigger(91)&&(mob.location()!=null))
				{
					if(t==null)
						t=parseBits(script,0,"CCr");
					if(t==null)
						break;
					final TimeClock eC=mob.location().getArea().getTimeObj();
					final String rest=(t.length>2)?t[2]:"";
					String unit=CMParms.getCleanBit(rest,0);
					if(unit.equalsIgnoreCase("CATCHUP"))
						unit="HOURS";
					final boolean catchup=rest.toUpperCase().indexOf("CATCHUP")>=0;
					final long every=ScriptEvents.everyHours(Math.max(1,CMath.s_long(t[1])),unit,eC);
					final long now=ScriptEvents.clockHours(eC);
					final Integer key=Integer.valueOf(thisScriptIndex);
					final Long next=everyNext.get(key);
					if(next==null)
						everyNext.put(key,Long.valueOf(now+every));
					else
					if(now>=next.longValue())
					{
						final long missed=1+((now-next.longValue())/every);
						everyNext.put(key,Long.valueOf(next.longValue()+(missed*every)));
						execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,""+(catchup?missed:1),null).push(script));
					}
				}
				break;
			case 78: // combat_prog START and END (ST15)
				if((combatEvent!=null)&&canTrigger(78))
				{
					if(t==null)
						t=parseBits(script,0,"CCr");
					if((t!=null)&&combatMatches(t,combatEvent,combatWith))
						execute(new MPContext(affecting,mob,(combatWith!=null)?combatWith:mob,(combatWith!=null)?combatWith:mob,defaultItem,null,combatEvent,null).push(script));
				}
				break;
			case 5: // rand_Prog
				if((!mob.amDead())&&canTrigger(5))
				{
					if(t==null)
						t=parseBits(script,0,"CR");
					// the same for any percentage: the tick has already skipped
					// areas that are not active, unless the script runs in them
					if(t!=null)
					{
						final int prcnt=CMath.s_int(t[1]);
						if(chance(affecting,script,prcnt)) // 100 is always
							execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,null, null).push(script));
					}
				}
				break;
			case 16: // delay_prog
				if((!mob.amDead())&&canTrigger(16))
				{
					final Integer thisScriptIndexI=Integer.valueOf(thisScriptIndex);
					synchronized(delayTargetTimes)
					{
						final int[] delayProgCounter;
						if(delayTargetTimes.containsKey(thisScriptIndexI))
							delayProgCounter=delayTargetTimes.get(thisScriptIndexI);
						else
						{
							if(t==null)
								t=parseBits(script,0,"CCR");
							if(t!=null)
							{
								final int low=CMath.s_int(t[1]);
								int high=CMath.s_int(t[2]);
								if(high<low)
									high=low;
								final int targetTick=CMLib.dice().roll(1,high-low+1,low-1);
								delayProgCounter =new int[]{targetTick};
								delayTargetTimes.put(thisScriptIndexI,delayProgCounter);
							}
							else
								delayProgCounter =new int[]{0};
						}
						if(--delayProgCounter[0]<=0)
						{
							delayTargetTimes.remove(thisScriptIndexI);
							execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,null, null).push(script));
						}
					}
				}
				break;
			case 7: // fight_Prog
				if((mob.isInCombat())&&(!mob.amDead())&&canTrigger(7))
				{
					if(t==null)
						t=parseBits(script,0,"CR");
					if(t!=null)
					{
						final int prcnt=this.getTriggerPercent(t[1], mob);
						if(chance(affecting,script,prcnt)) // 100 is always
							execute(new MPContext(affecting,mob,mob.getVictim(),mob,defaultItem,null,null, null).push(script));
					}
				}
				else
				if((ticking instanceof Item)
				&&canTrigger(7)
				&&(((Item)ticking).owner() instanceof MOB)
				&&(((MOB)((Item)ticking).owner()).isInCombat()))
				{
					if(t==null)
						t=parseBits(script,0,"CR");
					if(t!=null)
					{
						final int prcnt=this.getTriggerPercent(t[1], (((Item)ticking).owner()));
						if(chance(affecting,script,prcnt)) // 100 is always
						{
							final MOB M=(MOB)((Item)ticking).owner();
							if(!M.amDead())
								execute(new MPContext(affecting,mob,M,mob.getVictim(),defaultItem,null,null, null).push(script));
						}
					}
				}
				break;
			case 11: // hitprcnt_prog
				if((mob.isInCombat())&&(!mob.amDead())&&canTrigger(11))
				{
					if(t==null)
						t=parseBits(script,0,"CR");
					if(t!=null)
					{
						final int prcnt=CMath.s_int(t[1]);
						final int floor=(int)Math.round(CMath.mul(CMath.div(prcnt,100.0),mob.maxState().getHitPoints()));
						if(mob.curState().getHitPoints()<=floor)
							execute(new MPContext(affecting,mob,mob.getVictim(),mob,defaultItem,null,null, null).push(script));
					}
				}
				else
				if((ticking instanceof Item)
				&&canTrigger(11)
				&&(((Item)ticking).owner() instanceof MOB)
				&&(((MOB)((Item)ticking).owner()).isInCombat()))
				{
					final MOB M=(MOB)((Item)ticking).owner();
					if(!M.amDead())
					{
						if(t==null)
							t=parseBits(script,0,"CR");
						if(t!=null)
						{
							final int prcnt=CMath.s_int(t[1]);
							final int floor=(int)Math.round(CMath.mul(CMath.div(prcnt,100.0),M.maxState().getHitPoints()));
							if(M.curState().getHitPoints()<=floor)
								execute(new MPContext(affecting,mob,M,mob.getVictim(),defaultItem,null,null, null).push(script));
						}
					}
				}
				break;
			case 6: // once_prog
				if(!oncesDone.contains(Integer.valueOf(thisScriptIndex))&&canTrigger(6))
				{
					if(t==null)
						t=parseBits(script,0,"C");
					oncesDone.add(Integer.valueOf(thisScriptIndex));
					execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,null, null).push(script));
				}
				break;
			case 14: // time_prog
				if((mob.location()!=null)
				&&canTrigger(14)
				&&(!mob.amDead()))
				{
					if(t==null)
						t=parseBits(script,0,"CT");
					int lastTimeProgDone=-1;
					if(lastTimeProgsDone.containsKey(Integer.valueOf(thisScriptIndex)))
						lastTimeProgDone=lastTimeProgsDone.get(Integer.valueOf(thisScriptIndex)).intValue();
					final int time=mob.location().getArea().getTimeObj().getHourOfDay();
					if((t!=null)&&(lastTimeProgDone!=time))
					{
						boolean done=false;
						for(int i=1;i<t.length;i++)
						{
							// ST11: DAWN, DAY, DUSK, NIGHT, and ranges with steps
							if(ScriptEvents.hourMatches(t[i],time,mob.location().getArea().getTimeObj()))
							{
								done=true;
								execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,""+time, null).push(script));
								lastTimeProgsDone.remove(Integer.valueOf(thisScriptIndex));
								lastTimeProgsDone.put(Integer.valueOf(thisScriptIndex),Integer.valueOf(time));
								break;
							}
						}
						if(!done)
							lastTimeProgsDone.remove(Integer.valueOf(thisScriptIndex));
					}
				}
				break;
			case 15: // day_prog
				if((mob.location()!=null)&&canTrigger(15)
				&&(!mob.amDead()))
				{
					if(t==null)
						t=parseBits(script,0,"CT");
					int lastDayProgDone=-1;
					if(lastDayProgsDone.containsKey(Integer.valueOf(thisScriptIndex)))
						lastDayProgDone=lastDayProgsDone.get(Integer.valueOf(thisScriptIndex)).intValue();
					final int day=mob.location().getArea().getTimeObj().getDayOfMonth();
					if((t!=null)&&(lastDayProgDone!=day))
					{
						boolean done=false;
						for(int i=1;i<t.length;i++)
						{
							if(ScriptEvents.rangeMatches(t[i],day)) // ST11: ranges too
							{
								done=true;
								execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,null, null).push(script));
								lastDayProgsDone.remove(Integer.valueOf(thisScriptIndex));
								lastDayProgsDone.put(Integer.valueOf(thisScriptIndex),Integer.valueOf(day));
								break;
							}
						}
						if(!done)
							lastDayProgsDone.remove(Integer.valueOf(thisScriptIndex));
					}
				}
				break;
			case 13: // quest_time_prog
				if(!oncesDone.contains(Integer.valueOf(thisScriptIndex))&&canTrigger(13))
				{
					if(t==null)
						t=parseBits(script,0,"CCC");
					if(t!=null)
					{
						final Quest Q=getQuest(t[1]);
						if((Q!=null)
						&&(Q.running())
						&&(!Q.stopping())
						&&(Q.duration()!=0))
						{
							final int time=CMath.s_int(t[2]);
							if(time>=Q.minsRemaining())
							{
								oncesDone.add(Integer.valueOf(thisScriptIndex));
								execute(new MPContext(affecting,mob,mob,mob,defaultItem,null,null, null).push(script));
							}
						}
					}
				}
				break;
			default:
				break;
			}
		}
		tickStatus=Tickable.STATUS_SCRIPT+100;
		dequeResponses(null, true);
		altStatusTickable=null;
		return true;
	}

	@Override
	public void initializeClass()
	{
	}

	@Override
	public int compareTo(final CMObject o)
	{
		return CMClass.classID(this).compareToIgnoreCase(CMClass.classID(o));
	}

	protected void dupCheckClear(final ScriptableResponse resp, final String[] triggerStr)
	{
		if(que.size()>5)
		{
			int max = 25;
			if(resp.ctx.scripted instanceof Area)
				max = 150;
			final int hc = resp.hashCode();
			ScriptableResponse SB=null;
			for(int q=que.size()-1; q >= 0; q--)
			{
				try
				{
					SB = que.get(q);
					if((SB != null)
					&&(hc == SB.hashCode()))
						que.remove(SB);
				}
				catch(final IndexOutOfBoundsException x)
				{
					continue;
				}
			}
			if(que.size()>max)
			{
				if(triggerStr == null)
					this.logError(resp.ctx.scripted, "UNK", "SYS", "Attempt to pre-que more than "+max+" events).");
				else
					this.logError(resp.ctx.scripted, "UNK", "SYS", "Attempt to enque more than "+max+" events (last was "+CMParms.toListString(triggerStr)+" ).");
				final StringBuilder rpt=new StringBuilder("Queue Log:\n\r");
				for(int q=que.size()-1; q >= 0; q--)
				{
					try
					{
						SB = que.get(q);
						if(SB != null)
						{
							rpt.append(CMStrings.padRight(""+q,2)+") "+SB.triggerCode)
								.append(", src="+((SB.ctx.source==null)?"null":SB.ctx.source.name()))
								.append(", when="+new SimpleDateFormat("yyyyMMdd.HHmm.ss").format(Long.valueOf(SB.queuedAt)))
								.append("\n\r");
						}
					}
					catch(final IndexOutOfBoundsException x)
					{
						continue;
					}
				}
				//Log.debugOut(rpt.toString());
				que.clear();
			}
		}
	}

	/** SC5: the copy of its message EXECMSG_PROG hands to enqueResponse */
	protected CMMsg queuedMsg = null;

	public void enqueResponse(final int triggerCode,
							  final PhysicalAgent scripted,
							  final MOB monster,
							  final MOB source,
							  final Environmental target,
							  final Item primaryItem,
							  final Item secondaryItem,
							  final String msg,
							  final SubScript script,
							  final int ticks,
							  final String[] triggerStr)
	{
		if(!CMProps.isState(CMProps.HostState.RUNNING))
			return;
		final MPContext ctx = new MPContext(scripted,monster,source,target,primaryItem,secondaryItem,msg, null);
		ctx.cmsg = queuedMsg; // SC5: EXECMSG_PROG's copy of its message
		if(!CMProps.isState(CMProps.HostState.RUNNING))
			return;
		if(noDelay)
			execute(ctx.push(script));
		else
		{
			final ScriptableResponse resp = new ScriptableResponse(triggerCode,ctx,script,ticks);
			dupCheckClear(resp,triggerStr);
			que.add(resp);
		}
	}

	/**
	 * C5: the name of a named alarm (MPALARM NAME=), or null.
	 * @param SB something queued
	 * @return its name, or null
	 */
	protected static String alarmNameOf(final ScriptableResponse SB)
	{
		if((SB==null)||(SB.triggerCode>=0)||(SB.scr==null)||(SB.scr.size()==0)||(SB.scr.get(0)==null))
			return null;
		final String first=SB.scr.get(0).first;
		if((first==null)||(!first.startsWith(Telegraph.NAMED_ALARM)))
			return null;
		return first.substring(Telegraph.NAMED_ALARM.length()).trim();
	}

	@Override
	public int alarmTicksLeft(final String name)
	{
		for(final ScriptableResponse SB : new XVector<ScriptableResponse>(que))
		{
			final String n=alarmNameOf(SB);
			if((n!=null)&&n.equalsIgnoreCase(name.trim()))
				return Math.max(0,SB.tickDelay);
		}
		return -1;
	}

	@Override
	public boolean cancelAlarm(final String name)
	{
		boolean any=false;
		for(final ScriptableResponse SB : new XVector<ScriptableResponse>(que))
		{
			final String n=alarmNameOf(SB);
			if((n!=null)&&n.equalsIgnoreCase(name.trim()))
			{
				que.remove(SB);
				any=true;
			}
		}
		return any;
	}

	/**
	 * C5: a telegraph's end: its command, once for each of those it hits, $n
	 * each of them.
	 * @param ctx the alarm's context
	 * @param name the telegraph's name
	 * @param cmd the command
	 */
	protected void telegraphHit(final MPContext ctx, final String name, final String cmd)
	{
		if(!(ctx.scripted instanceof MOB))
			return;
		final MOB mob=(MOB)ctx.scripted;
		final Telegraph.Effect T=Telegraph.find(mob,name);
		if(T==null)
			return; // interrupted
		final List<MOB> targets=T.telegraphTargets();
		T.telegraphDone();
		for(final MOB M : targets)
		{
			final MPContext hit=new MPContext(ctx);
			hit.source=M;
			hit.target=M;
			final SubScript s=new SubScriptImpl(ctx.script);
			s.add(new ScriptLn("FUNCTION_PROG TELEGRAPH_"+name,null,null));
			s.add(new ScriptLn(cmd,null,null));
			hit.script=s;
			hit.line=1;
			execute(hit);
		}
	}

	public void prequeResponse(final int triggerCode, final MPContext ctx, final SubScript script, final int ticks)
	{
		if(!CMProps.isState(CMProps.HostState.RUNNING))
			return;
		final ScriptableResponse resp = new ScriptableResponse(triggerCode,ctx,script,ticks);
		dupCheckClear(resp,null);
		que.add(0,resp);
	}

	@Override
	public void dequeResponses(final Object[] objects)
	{
		dequeResponses(objects,false);
	}

	protected void dequeResponses(final Object[] objects, final boolean spawnOk)
	{
		try
		{
			tickStatus=Tickable.STATUS_SCRIPT+100;
			for(int q=que.size()-1;q>=0;q--)
			{
				ScriptableResponse SB=null;
				try
				{
					SB=que.get(q);
					if((SB != null)&&(alarmNameOf(SB)!=null)
					&&(SB.ctx!=null)&&(SB.ctx.scripted instanceof MOB)
					&&(!Telegraph.canAct((MOB)SB.ctx.scripted)))
					{
						que.remove(SB); // C5: a named alarm of one dead, stunned or knocked out
						continue;
					}
					if((SB != null)
					&& (SB.checkTimeToExecute()))
					{
						if (objects != null)
						{
							for(int i=0;i<objects.length && i<SB.ctx.tmp.length;i++)
								SB.ctx.tmp[i] = objects[i];
 						}
						SB.ctx.script = SB.scr;
						SB.ctx.line = 1;
						if(spawnOk && SB.scr.isFlagSet("SPAWN"))
						{
							final MPContext context = SB.ctx;
							CMLib.threads().executeRunnable(new Runnable()
							{
								public MPContext ctx = context;
								@Override
								public void run()
								{
									execute(ctx);
								}
							});
						}
						else
							execute(SB.ctx);
						que.remove(SB);
					}
				}
				catch(final IndexOutOfBoundsException x)
				{
					continue;
				}
			}
			if((noTrigger.size()>0)&&(noTrigger.containsKey(Integer.valueOf(-52))))
				noTrigger.remove(Integer.valueOf(-52));
		}
		catch (final Exception e)
		{
			Log.errOut("DefaultScriptingEngine", e);
		}
	}

	/** how long SCRIPT DISABLE turns a trigger off: until SCRIPT ENABLE, in practice */
	protected static final long DISABLED_MILLIS = 100L * 365L * 24L * 60L * 60L * 1000L;

	/**
	 * The trigger code of a trigger's name, with or without _PROG.
	 * @param name the name, as SPEECH or SPEECH_PROG
	 * @return the code, or 0 if it is no trigger
	 */
	public static int triggerCodeOf(final String name)
	{
		if(name==null)
			return 0;
		final String up=name.trim().toUpperCase();
		for(int i=0;i<progs.length;i++)
		{
			if(progs[i].equals(up)||progs[i].equals(up+"_PROG"))
				return i+1;
		}
		return 0;
	}

	private static String normalLine(final String s)
	{
		return s.replace('`','\'').replaceAll("\\s+"," ").trim().toUpperCase();
	}

	/**
	 * The texts a host's scripts are written in: the inline script, then each
	 * LOADed file, each as its lines.
	 * @param host the host
	 * @return the texts, each with its name ("inline" or the file)
	 */
	protected List<Pair<String,String[]>> sourceTexts(final PhysicalAgent host)
	{
		final List<Pair<String,String[]>> texts=new ArrayList<Pair<String,String[]>>();
		final String inline=getScript();
		texts.add(new Pair<String,String[]>("inline",(inline==null)?new String[0]:inline.split("\n")));
		for(final String f : externalFiles())
			texts.add(new Pair<String,String[]>(f,getResourceFileData(host,f).toString().split("\n")));
		return texts;
	}

	/** finds text in lines from a line and column: the line and the column after it, or null */
	private static int[] findFrom(final String[] lines, final int[] pos, final String want)
	{
		for(int l=pos[0];l<lines.length;l++)
		{
			final int x=normalLine(lines[l]).indexOf(want,(l==pos[0])?pos[1]:0);
			if(x>=0)
				return new int[]{l,x+want.length()};
		}
		return null;
	}

	/**
	 * Where each trigger is written, in the order parsed: for each, the text
	 * (an index into texts), the line and the column after it, or null.
	 * @param scripts the triggers
	 * @param texts the texts, as sourceTexts gives them
	 * @return the places
	 */
	protected int[][] triggerPositions(final List<SubScript> scripts, final List<Pair<String,String[]>> texts)
	{
		final int[][] from=new int[texts.size()][2];
		final int[][] at=new int[scripts.size()][];
		for(int v=0;v<scripts.size();v++)
		{
			final String want=normalLine(scripts.get(v).getTriggerLine());
			for(int t=0;(t<texts.size())&&(at[v]==null)&&(want.length()>0);t++)
			{
				final int[] p=findFrom(texts.get(t).second,from[t],want);
				if(p!=null)
				{
					from[t][0]=p[0];
					from[t][1]=p[1];
					at[v]=new int[]{t,p[0],p[1]};
				}
			}
		}
		return at;
	}

	/** how many lines a parsed &lt;SCRIPT&gt; block skips, once it ran */
	private static int jsLines(final ScriptLn ln)
	{
		if((ln!=null)&&(ln.third instanceof Object[])&&(((Object[])ln.third).length>1)&&(((Object[])ln.third)[1] instanceof Integer))
			return ((Integer)((Object[])ln.third)[1]).intValue();
		return 0;
	}

	/**
	 * A host as MPDEBUG's and debug()'s lines name it: name@room.
	 * @param host the host
	 * @return the words
	 */
	public static String debugHost(final Environmental host)
	{
		final Room R=(host==null)?null:CMLib.map().roomLocation(host);
		return ((host==null)?"?":host.name())+"@"+((R==null)?"?":CMLib.map().getExtendedRoomID(R));
	}

	/**
	 * Where a line of a host's script is written, as file:line ("inline:n"
	 * for the host's own script), following LOAD=.
	 * @param host the host
	 * @param script the trigger the line is in
	 * @param lineIndex the line's index in it (0 is the trigger)
	 * @return the place, or "" if it is not found (a script made as it ran)
	 */
	public String scriptLocation(final PhysicalAgent host, final SubScript script, final int lineIndex)
	{
		try
		{
			final List<SubScript> scripts=getScripts(host);
			int v=-1;
			for(int i=0;i<scripts.size();i++)
			{
				if(scripts.get(i)==script)
				{
					v=i;
					break;
				}
			}
			if(v<0)
				return "";
			final List<Pair<String,String[]>> texts=sourceTexts(host);
			final int[] at=triggerPositions(scripts,texts)[v];
			if(at==null)
				return "";
			final String[] lines=texts.get(at[0]).second;
			int[] pos=new int[]{at[1],at[2]};
			int found=at[1];
			for(int i=1;(i<=lineIndex)&&(i<script.size());i++)
			{
				// a line may be several (a <SCRIPT> body): found by its first, passed by all
				final String[] pieces=script.get(i).first.split("\\r\\n|\\n\\r|\\n|\\r");
				final String want=normalLine(pieces[0]);
				if(want.length()==0)
					continue;
				final int[] p=findFrom(lines,pos,want);
				if(p==null)
					break;
				found=p[0];
				pos=(pieces.length>1)?new int[]{Math.min(lines.length-1,p[0]+pieces.length-1)+1,0}:p;
			}
			return texts.get(at[0]).first+":"+(found+1);
		}
		catch(final RuntimeException e)
		{
			return "";
		}
	}

	/**
	 * SCRIPT LIST: the host's triggers, one a line, numbered, with the file and
	 * line each is written at, and [off] for one disabled, [done] for a
	 * ONCE_PROG that ran.
	 * @param host the host
	 * @return the lines
	 */
	public List<String> triggerList(final PhysicalAgent host)
	{
		final List<SubScript> scripts=getScripts(host);
		final List<Pair<String,String[]>> texts=sourceTexts(host);
		final int[][] at=triggerPositions(scripts,texts);
		final List<String> out=new ArrayList<String>();
		for(int v=0;v<scripts.size();v++)
		{
			final SubScript script=scripts.get(v);
			final String trig=script.getTriggerLine();
			final String where=(at[v]==null)?"":(texts.get(at[v][0]).first+":"+(at[v][1]+1));
			final int code=script.getTriggerCode();
			final StringBuilder line=new StringBuilder("").append(v+1).append(". ").append(trig);
			if(where.length()>0)
				line.append("  (").append(where).append(")");
			final Long off=noTrigger.get(Integer.valueOf(code));
			if((off!=null)&&(off.longValue()>GameTime.now()))
				line.append(" [off]");
			if((code==6)&&(oncesDone.contains(Integer.valueOf(v))))
				line.append(" [done]");
			out.add(line.toString());
		}
		return out;
	}

	/**
	 * Whether a trigger answers to what SCRIPT FIRE was given: one of its
	 * words, its whole argument, or its P pattern.
	 */
	private boolean triggerAnswers(final SubScript script, final String want)
	{
		final String[] bits=script.getTriggerBits();
		if(bits.length<2)
			return false;
		if(bits[1].equalsIgnoreCase("P")&&(bits.length>2))
			return match(want,CMParms.combine(Arrays.asList(bits),2));
		final String all=" "+normalLine(CMParms.combine(Arrays.asList(bits),1))+" ";
		if(all.contains(" "+want+" "))
			return true;
		for(final String w : CMParms.parse(want))
		{
			for(int i=1;i<bits.length;i++)
			{
				if(bits[i].equalsIgnoreCase(w))
					return true;
			}
		}
		return false;
	}

	/**
	 * SCRIPT FIRE: runs the host's triggers of one kind now, as if the source
	 * had caused them, whatever their own conditions and odds, and even if
	 * disabled.  Given words, only the triggers that answer to them run
	 * (SPEECH hello, TIME_PROG 5), and the words are the message ($g).
	 * @param host the host
	 * @param trigger the trigger, as SPEECH or SPEECH_PROG
	 * @param args the words, or ""
	 * @param source who causes it ($n), or null for the host
	 * @param item the item it is about ($o), or null
	 * @return how many ran, or -1 if there is no such trigger
	 */
	public int fireTrigger(final PhysicalAgent host, final String trigger, final String args, final MOB source, final Item item)
	{
		final int code=triggerCodeOf(trigger);
		if(code<=0)
			return -1;
		final MOB monster=getMakeMOB(host);
		final String words=(args==null)?"":args.trim();
		final String want=normalLine(words);
		final List<SubScript> scripts=getScripts(host);
		int n=0;
		for(int v=0;v<scripts.size();v++)
		{
			final SubScript script=scripts.get(v);
			if(script.getTriggerCode()!=code)
				continue;
			if((want.length()>0)&&(!triggerAnswers(script,want)))
				continue;
			final MPContext ctx=new MPContext(host,monster,(source!=null)?source:monster,monster,item,null,words,null);
			execute(ctx.push(script));
			n++;
		}
		return n;
	}

	/**
	 * SCRIPT CALL: runs one of the host's FUNCTION_PROGs.
	 * @param host the host
	 * @param function its name
	 * @param args its arguments ($g)
	 * @param source who calls it ($n), or null for the host
	 * @return what it returned (RETURN), or null if there is no such function
	 */
	/**
	 * SC6: MPCALLFUNC and CALLFUNC's other form: the first word is a thing
	 * with scripts, the second the function there, the rest its arguments.
	 * @param ctx the script's context
	 * @param thing the thing's code or name
	 * @param rest the function's name and its arguments
	 * @return what the function returned, or null if there is none
	 */
	protected String callOn(final MPContext ctx, final String thing, final String rest)
	{
		final Environmental E=getArgumentItem(thing,ctx);
		if(!(E instanceof PhysicalAgent))
		{
			// JS11: no such thing: a function of the host's own JScriptable
			if(ctx.scripted instanceof PhysicalAgent)
				return ScriptEvents.jsFunction((PhysicalAgent)ctx.scripted,thing,varify(ctx,rest),(ctx.source!=null)?ctx.source:ctx.monster);
			return null;
		}
		final String func=CMParms.getCleanBit(rest,0);
		final String args=varify(ctx,CMParms.getPastBitClean(rest,0));
		return ScriptEvents.callFunction((PhysicalAgent)E,func,args,(ctx.source!=null)?ctx.source:ctx.monster);
	}

	@Override
	public String callFunction(final PhysicalAgent host, final String function, final String args, final MOB source)
	{
		if(findFunc(host,function)==null)
			return null;
		final MOB monster=getMakeMOB(host);
		final String words=(args==null)?"":args.trim();
		final MPContext ctx=new MPContext(host,monster,(source!=null)?source:monster,monster,null,null,words,null);
		final String r=callFunc(function,words,ctx);
		return (r==null)?"":r;
	}

	/**
	 * SCRIPT RESET: forgets that triggers ran, so that they run again.
	 * @param what ONCE (ONCE_PROGs), DELAY (DELAY_PROG waits), TIME (TIME_PROGs
	 *        and DAY_PROGs that ran this hour or day), or ALL
	 * @return the triggers it was for, or "" if what is none of these
	 */
	public String resetTriggers(final String what)
	{
		final String w=(what==null)?"ALL":what.trim().toUpperCase();
		final List<String> done=new ArrayList<String>();
		if(w.equals("ONCE")||w.equals("ALL"))
		{
			oncesDone.clear();
			done.add("ONCE_PROG");
		}
		if(w.equals("DELAY")||w.equals("ALL"))
		{
			delayTargetTimes.clear();
			done.add("DELAY_PROG");
		}
		if(w.equals("TIME")||w.equals("ALL"))
		{
			lastTimeProgsDone.clear();
			lastDayProgsDone.clear();
			done.add("TIME_PROG");
			done.add("DAY_PROG");
		}
		return String.join(", ",done);
	}

	/**
	 * SCRIPT DISABLE and ENABLE: turns triggers off (as MPNOTRIGGER does, until
	 * turned on again) or on.
	 * @param host the host
	 * @param trigger the trigger, or null for all the host has
	 * @param on true to turn on, false to turn off
	 * @return the triggers changed, or null if there is no such trigger
	 */
	public List<String> enableTrigger(final PhysicalAgent host, final String trigger, final boolean on)
	{
		final Set<Integer> codes=new TreeSet<Integer>();
		if((trigger==null)||(trigger.trim().length()==0))
		{
			for(final SubScript script : getScripts(host))
			{
				if(script.getTriggerCode()>0)
					codes.add(Integer.valueOf(script.getTriggerCode()));
			}
		}
		else
		{
			final int code=triggerCodeOf(trigger);
			if(code<=0)
				return null;
			codes.add(Integer.valueOf(code));
		}
		final List<String> names=new ArrayList<String>();
		for(final Integer code : codes)
		{
			if(on)
				noTrigger.remove(code);
			else
			{
				noTrigger.put(code,Long.valueOf(GameTime.now()+DISABLED_MILLIS));
				synchronized(que)
				{
					for(final Iterator<ScriptableResponse> i=que.iterator();i.hasNext();)
					{
						if(i.next().triggerCode==code.intValue())
							i.remove();
					}
				}
			}
			names.add(progs[code.intValue()-1]);
		}
		return names;
	}

	/**
	 * SCRIPT ALARMS LIST: what is queued to run, MPALARMs and triggers waiting
	 * their tick, one a line.
	 * @return the lines
	 */
	public List<String> pendingResponses()
	{
		final List<String> out=new ArrayList<String>();
		for(final ScriptableResponse SB : new XVector<ScriptableResponse>(que))
		{
			final int code=SB.triggerCode;
			final String an=alarmNameOf(SB); // C5
			final String name=(code<0)?((an!=null)?("MPALARM "+an):"MPALARM"):((code>0)&&(code<=progs.length))?progs[code-1]:("#"+code);
			final StringBuilder line=new StringBuilder(name).append(" in ").append(Math.max(0,SB.tickDelay)).append(" ticks");
			if((SB.ctx!=null)&&(SB.ctx.source!=null))
				line.append(", for ").append(SB.ctx.source.Name());
			if((SB.scr!=null)&&(SB.scr.size()>1))
				line.append(": ").append(SB.scr.get(1).first);
			out.add(line.toString());
		}
		return out;
	}

	/**
	 * SCRIPT ALARMS CLEAR: forgets what is queued to run.
	 * @return how many there were
	 */
	public int clearPendingResponses()
	{
		final int n=que.size();
		que.clear();
		return n;
	}

	public String L(final String str, final String ... xs)
	{
		return CMLib.lang().fullSessionTranslation(getClass(), str, xs);
	}

	protected static class JScriptEvent extends org.mozilla.javascript.ImporterTopLevel // JS6: importPackage
	{
		@Override
		public String getClassName()
		{
			return "JScriptEvent";
		}

		static final long				serialVersionUID	= 43;

		MPContext						ctx; // JS15: set again for each JS() of the engine's kept scope
		List<String>					scr;
		final DefaultScriptingEngine	c;

		/** JS15: cancel() was called */
		boolean                            cancelled    = false;

		/** JS15: cancel(): the prog returns CANCEL, as RETURN CANCEL, so that CNCLMSG_PROG refuses its message */
		public void cancel()
		{
			cancelled = true;
		}

		/** @return JS17: the message of a message trigger (CNCLMSG_PROG, EXECMSG_PROG and the like), or null */
		public CMMsg msg()
		{
			return ctx.cmsg;
		}

		/** JS17: runs one MOBPROG line for the script's host */
		protected String line(final String text)
		{
			final SubScript DV=new SubScriptImpl(null);
			DV.add(new ScriptLn("JS_PROG",null,null));
			DV.add(new ScriptLn(text,null,null));
			return c.execute(ctx.push(DV));
		}

		/**
		 * JS17
		 * @param id a room's ID
		 * @return the room, or null
		 */
		public Room room(final String id)
		{
			return (id == null) ? null : CMLib.map().getRoom(id);
		}

		/**
		 * JS17
		 * @param where a room or its ID, a mob, or a container
		 * @param name an item's name
		 * @return the first item of that name there, or null
		 */
		public Item findItem(final Object where, final String name)
		{
			Object w = (where instanceof org.mozilla.javascript.Wrapper) ? ((org.mozilla.javascript.Wrapper)where).unwrap() : where;
			if(w instanceof CharSequence)
				w = CMLib.map().getRoom(w.toString());
			if(name == null)
				return null;
			if(w instanceof Room)
				return ((Room)w).findItem(null, name);
			if(w instanceof MOB)
				return ((MOB)w).findItem(null, name);
			if(w instanceof Container)
			{
				for(final Item I : ((Container)w).getContents())
				{
					if(CMLib.english().containsString(I.name(), name))
						return I;
				}
			}
			return null;
		}

		/**
		 * JS17: a door, both sides, from anywhere, without a message (I2's
		 * MPDOOR will add messages).
		 * @param roomId the room's ID
		 * @param dir the exit's direction
		 * @param op OPEN, CLOSE, LOCK or UNLOCK
		 * @return false if there is no such door, or no lock to lock
		 */
		public boolean door(final String roomId, final String dir, final String op)
		{
			final Room R = (roomId == null) ? null : CMLib.map().getRoom(roomId);
			final int d = (dir == null) ? -1 : CMLib.directions().getGoodDirectionCode(dir);
			if((R == null) || (d < 0))
				return false;
			final Exit E = R.getRawExit(d);
			if((E == null) || (!E.hasADoor()))
				return false;
			final String o = (op == null) ? "" : op.trim().toUpperCase();
			final boolean open = o.equals("OPEN") || (o.equals("UNLOCK") && E.isOpen());
			final boolean locked = o.equals("LOCK");
			if((locked || o.equals("UNLOCK")) && (!E.hasALock()))
				return false;
			if(!(o.equals("OPEN") || o.equals("CLOSE") || locked || o.equals("UNLOCK")))
				return false;
			final Room R2 = R.getRoomInDir(d);
			final Exit E2 = (R2 == null) ? null : R2.getRawExit(Directions.getOpDirectionCode(d));
			for(final Exit X : new Exit[] { E, E2 })
			{
				if((X != null) && X.hasADoor())
					X.setDoorsNLocks(true, open && !locked, X.defaultsClosed(), X.hasALock(), locked && X.hasALock(), X.defaultsLocked());
			}
			return true;
		}

		/**
		 * JS17: MPSIGNAL with these words (SC6).
		 * @param words the signal's name and the rest, as MPSIGNAL takes them
		 */
		public void signal(final String words)
		{
			line("MPSIGNAL " + ((words == null) ? "" : words));
		}

		/**
		 * JS17: SC4's state of one object: read with two arguments, set with three.
		 * @param thing the object, as a script names it ($i, $n, a name)
		 * @param name the state's name
		 * @param value the value to set, or nothing to read
		 * @return the value
		 */
		public String state(final String thing, final String name, final Object value)
		{
			if((value == null) || (value instanceof org.mozilla.javascript.Undefined))
				return c.functify(ctx, "STATE(" + thing + " " + name + ")");
			line("MPSTATE " + thing + " " + name + " " + Context.toString(value));
			return Context.toString(value);
		}

		/**
		 * N7: parm('price'): a generic behaviour's parameter, as PARM(price)
		 * @param name its name
		 * @return its value, or ""
		 */
		public String parm(final String name)
		{
			final String v=c.parmValue(ctx.scripted,name);
			return (v==null)?"":v;
		}

		public Environmental host()
		{
			return ctx.scripted;
		}

		public MOB source()
		{
			return ctx.source;
		}

		public Environmental target()
		{
			return ctx.target;
		}

		public MOB monster()
		{
			return ctx.monster;
		}

		public Item item()
		{
			return ctx.primaryItem;
		}

		public Item item2()
		{
			return ctx.secondaryItem;
		}

		public Item item1()
		{
			return ctx.primaryItem;
		}

		public Object[] objs()
		{
			return ctx.tmp;
		}

		public String message()
		{
			return ctx.msg;
		}

		public void setVar(final String host, final String var, final String value)
		{
			c.setVar(host,var.toUpperCase(),value);
		}

		public String getVar(final String host, final String var)
		{
			return c.getVar(host, var);
		}

		public String toJavaString(final Object O)
		{
			return Context.toString(O);
		}

		/**
		 * debug(x): x to the developer log with its host, room and line
		 * (DT24), in builder mode and DEV=1 games only.
		 * @param O what to write
		 */
		public void debug(final Object O)
		{
			JSRuntime.debug(debugHost(ctx.scripted), O);
		}

		public String getCMType(final Object O)
		{
			if(O == null)
				return "null";
			final CMObjectType typ = CMClass.getObjectType(O);
			if(typ == null)
				return "unknown";
			return typ.name().toLowerCase();
		}

		@Override
		public Object get(final String name, final Scriptable start)
		{
			if (super.has(name, start))
				return super.get(name, start);
			if (methH.containsKey(name) || funcH.containsKey(name)
			|| (name.endsWith("$")&&(funcH.containsKey(name.substring(0,name.length()-1)))))
			{
				return new Function()
				{
					@Override
					public Object call(final Context cx, final Scriptable scope, final Scriptable thisObj, final Object[] args)
					{
						if(methH.containsKey(name))
						{
							final StringBuilder strb=new StringBuilder(name);
							if(args.length==1)
								strb.append(" ").append(String.valueOf(args[0]));
							else
							for(int i=0;i<args.length;i++)
							{
								if(i==args.length-1)
									strb.append(" ").append(String.valueOf(args[i]));
								else
									strb.append(" ").append("'"+String.valueOf(args[i])+"'");
							}
							final SubScript DV=new SubScriptImpl(null);
							DV.add(new ScriptLn("JS_PROG",null,null));
							DV.add(new ScriptLn(strb.toString(),null,null));
							return c.execute(ctx.push(DV));
						}
						if(name.endsWith("$"))
						{
							final StringBuilder strb=new StringBuilder(name.substring(0,name.length()-1)).append("(");
							if(args.length==1)
								strb.append(" ").append(String.valueOf(args[0]));
							else
							for(int i=0;i<args.length;i++)
							{
								if(i==args.length-1)
									strb.append(" ").append(String.valueOf(args[i]));
								else
									strb.append(" ").append("'"+String.valueOf(args[i])+"'");
							}
							strb.append(" ) ");
							return c.functify(ctx,strb.toString());
						}
						final String[] sargs=new String[args.length+3];
						sargs[0]=name;
						sargs[1]="(";
						for(int i=0;i<args.length;i++)
							sargs[i+2]=String.valueOf(args[i]);
						sargs[sargs.length-1]=")";
						final String[][] eval={sargs};
						return Boolean.valueOf(c.eval(ctx,eval,0));
					}

					@Override
					public void delete(final String arg0)
					{
					}

					@Override
					public void delete(final int arg0)
					{
					}

					@Override
					public Object get(final String arg0, final Scriptable arg1)
					{
						return null;
					}

					@Override
					public Object get(final int arg0, final Scriptable arg1)
					{
						return null;
					}

					@Override
					public String getClassName()
					{
						return null;
					}

					@Override
					public Object getDefaultValue(final Class<?> arg0)
					{
						return null;
					}

					@Override
					public Object[] getIds()
					{
						return null;
					}

					@Override
					public Scriptable getParentScope()
					{
						return null;
					}

					@Override
					public Scriptable getPrototype()
					{
						return null;
					}

					@Override
					public boolean has(final String arg0, final Scriptable arg1)
					{
						return false;
					}

					@Override
					public boolean has(final int arg0, final Scriptable arg1)
					{
						return false;
					}

					@Override
					public boolean hasInstance(final Scriptable arg0)
					{
						return false;
					}

					@Override
					public void put(final String arg0, final Scriptable arg1, final Object arg2)
					{
					}

					@Override
					public void put(final int arg0, final Scriptable arg1, final Object arg2)
					{
					}

					@Override
					public void setParentScope(final Scriptable arg0)
					{
					}

					@Override
					public void setPrototype(final Scriptable arg0)
					{
					}

					@Override
					public Scriptable construct(final Context arg0, final Scriptable arg1, final Object[] arg2)
					{
						return null;
					}
				};
			}
			return super.get(name, start);
		}

		public void executeEvent(final SubScript script, final int lineNum)
		{
			final MPContext ctx = this.ctx.push(script);
			ctx.line = lineNum;
			c.execute(ctx);
		}

		public JScriptEvent(final DefaultScriptingEngine scrpt, final MPContext ctx)
		{
			c=scrpt;
			this.ctx = ctx;
		}
	}
}
