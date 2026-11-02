package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMProps.ListFile;
import com.planet_ink.coffee_mud.core.CMProps.Str;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.core.exceptions.ScriptParseException;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.MPContext;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.ProtocolLibrary.LLMSession;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;
/*
   Copyright 2001-2025 Bo Zimmerman

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

import org.mozilla.javascript.JavaScriptException;

public class MudChat extends StdBehavior implements ChattyBehavior
{
	@Override
	public String ID()
	{
		return "MudChat";
	}

	@Override
	protected String getRunState()
	{
		// the groups a conversation switched to, for a while
		final StringBuilder str=new StringBuilder("");
		synchronized(this)
		{
			for(final Pair<ChattyGroup,Long> p : new XVector<Pair<ChattyGroup,Long>>(chatGroups))
			{
				if((p.second.longValue()==Long.MAX_VALUE)||(p.first.groupNames==null)||(p.first.groupNames.length==0))
					continue;
				str.append("<GROUP LEFT=\"").append(Math.max(1,p.second.longValue()-GameTime.now())).append("\">")
				   .append(CMLib.xml().parseOutAngleBrackets(p.first.groupNames[0])).append("</GROUP>");
			}
		}
		return str.toString();
	}

	@Override
	protected void setRunState(final String xml)
	{
		final ChattyGroup[] all=getChatGroups();
		for(final com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag tag : CMLib.xml().parseAllXML(xml))
		{
			if(!tag.tag().equalsIgnoreCase("GROUP"))
				continue;
			final String name=CMLib.xml().restoreAngleBrackets(tag.value());
			for(final ChattyGroup G : all)
			{
				if((G.groupNames!=null)&&CMParms.contains(G.groupNames,name)&&(!chatGroups.containsFirst(G)))
				{
					final long expire=GameTime.now()+CMath.s_long(tag.getParmValue("LEFT"));
					chatGroups.add(new Pair<ChattyGroup,Long>(G,Long.valueOf(expire)));
					if(expire<chatEntryExpire)
						chatEntryExpire=expire;
					currChatEntries=null;
					break;
				}
			}
		}
	}

	//----------------------------------------------
	// format: first group is general mob (no other
	// fit found).  All groups are chat groups.
	// each chat group includes a string describing
	// qualifying mobs followed by one or more chat
	// collections.
	// chat collection: first string is the pattern
	// match string
	// following strings are the proposed responses.
	//----------------------------------------------

	protected String		 chatDatFilename = defaultChatFile();

	/**
	 * Returns the chat file mobs use when their parameters name none:
	 * CHATDEFAULT (in coffeemud.ini or world.ini), chat.dat if it is not set,
	 * or "" for none, so that mobs chat only with what their parameters give.
	 * @return the file, or ""
	 */
	public static String defaultChatFile()
	{
		final CMProps props=CMProps.instance();
		final String s=(props==null)?"":props.getStr("CHATDEFAULT","").trim();
		if(s.length()==0)
			return "chat.dat";
		if(s.equalsIgnoreCase("NONE"))
			return "";
		return s;
	}
	protected String		 chatGroup		= "";

	protected final PairList <ChattyGroup, Long> chatGroups = new PairSVector<ChattyGroup, Long>();
	protected volatile long  chatEntryExpire = Long.MAX_VALUE;
	protected ChatEntryGroup currChatEntries = null;
	protected ChattyEntry[]	 addedChatEntries= new ChattyEntry[0];
	protected ChattyGroup	 baseChatGroup 	= null;
	protected String 		 myOldName 		= "";
	protected volatile MOB	 lastReactedToM	= null;
	protected volatile MOB	 lastRespondedToM= null;
	protected volatile MOB	 lastSpeakerM	= null;
	protected String		 lastThingSaid	= null;
	protected int			 tickDown		= 3;
	protected volatile int	 talkDown		= 0;
	protected Boolean		 llmSupported	= null;
	protected LLMSession	 llmSession		= null;
	protected volatile long	 llmLastUse		= 0;

	// responseQue is a qued set of commands to
	// run through the standard command processor,
	// on tick or more.
	protected SLinkedList<ChattyResponse>	responseQue		= new SLinkedList<ChattyResponse>();
	protected ScriptingEngine				scriptEngine	= null;
	protected volatile LLMSession			llm				= null;
	protected Map<String,String> 			addedChatVars 	= new Hashtable<String,String>();

	protected final static int	RESPONSE_DELAY		= 2;
	/** N10: DELAY=n, the ticks before a reply, RESPONSE_DELAY by default */
	protected int				responseDelay		= RESPONSE_DELAY;

	/** N10: its replies waiting, at once, oldest first */
	@Override
	public int repliesNow(final PhysicalAgent host)
	{
		if((!(host instanceof MOB))||responseQue.isEmpty()||CMSecurity.isDisabled(CMSecurity.DisFlag.MUDCHAT))
			return 0;
		final MOB mob=(MOB)host;
		final List<ChattyResponse> waiting=new ArrayList<ChattyResponse>(responseQue);
		responseQue.clear();
		int n=0;
		for(final ChattyResponse R : waiting)
		{
			if(R.combatFlag == mob.isInCombat())
			{
				respond(mob,R);
				n++;
			}
		}
		lastReactedToM=null;
		return n;
	}
	protected final static int	TALK_WAIT_DELAY		= 8;
	protected final static int	TALK_SWITCH_EXPIRE	= 360000;

	/**
	 * Enum for different match types
	 * @author Bo Zimmerman
	 *
	 */
	protected enum ChatMatchType
	{
		SAY,
		EMOTE,
		TEMOTE,
		RANDOM
	}

	/**
	 * Enum for connectors between matches/expressions
	 * @author Bo Zimmerman
	 *
	 */
	protected static enum ChatExpConn
	{
		END,
		AND,
		OR,
		ANDNOT
	}

	/**
	 * Flag for how to compare a match string with the user string
	 *
	 * @author Bo Zimmerman
	 *
	 */
	protected static enum ChatMatchFlag
	{
		EXACT,
		TOP,
		ZAPPER,
		INSTR
	}

	/**
	 * A specific string match, with modifiers
	 * @author Bo Zimmerman
	 *
	 */
	protected static class ChatMatch
	{
		public ChatMatch()
		{
		}
		public ChatMatchFlag flag = ChatMatchFlag.INSTR;
		public String str=null;
	}

	/**
	 * A match expression, composed of one or more matches and expressions
	 *
	 * @author Bo Zimmerman
	 *
	 */
	protected static class ChatExpression
	{
		public ChatExpression()
		{
		}
		public ChatMatchType	type	= null;
		public Set<String>		ones	= new HashSet<String>();
		public List<Pair<Object,ChatExpConn>> exp = new LinkedList<Pair<Object,ChatExpConn>>();
	}

	/**
	 * A group of valid entries, hashed for quick lookups
	 *
	 * @author Bo Zimmerman
	 *
	 */
	protected static class ChatEntryGroup
	{
		final Map<String,ChattyEntry[]> mapped = new Hashtable<String,ChattyEntry[]>();
		public ChatEntryGroup(final Collection<ChattyEntry> entries)
		{
			final Map<String,List<ChattyEntry>> ms = new HashMap<String,List<ChattyEntry>>();
			for(final ChattyEntry e : entries)
			{
				for(final String word : e.expression.ones)
				{
					if(!ms.containsKey(word))
						ms.put(word, new ArrayList<ChattyEntry>());
					ms.get(word).add(e);
				}
			}
			for(final String key : ms.keySet())
				mapped.put(key, ms.get(key).toArray(new ChattyEntry[0]));
		}
	}


	/**
	 * A response object representing something the chatty-one will
	 * definitely be saying soon.
	 * @author Bo Zimmerman
	 */
	protected static class ChattyResponse
	{
		public int				delay;
		public boolean			combatFlag;
		public List<String>		parsedCommand;
		/** N11: a MOBPROG line (&) to run in place of the command, or null */
		public String            script        = null;
		/** N11: whom it answers, the line's $n */
		public MOB                speaker        = null;

		public ChattyResponse(final List<String> cmd, final int responseDelay, final boolean combatFlag)
		{
			parsedCommand = cmd;
			delay = responseDelay;
			this.combatFlag=combatFlag;
		}
	}

	/**
	 * A test response is a possible response to an environmental event, such as
	 * someone speaking or acting.  It is only one possible response to one possible
	 * event, and is weighed against its neighbors for whether it is chosen.
	 * @author Bo Zimmerman
	 */
	protected static class ChattyTestResponse
	{
		public String[] responses;
		public int weight;
		public boolean combatFlag;
		public String switchDB = "";
		public ChattyGroup fromGroup;
		public ChattyTestResponse(final ChattyGroup group, final String resp, final boolean combatFlag)
		{
			fromGroup=group;
			weight=CMath.s_int(""+resp.charAt(0));
			this.combatFlag=combatFlag;
			responses=CMParms.parseSquiggleDelimited(resp.substring(1),true).toArray(new String[0]);
			for(int i=0;i<responses.length;i++)
			{
				final int x = responses[i].lastIndexOf("$@");
				if(x>0)
				{
					final String db = responses[i].substring(x+2).trim();
					switchDB = db;
					responses[i] = responses[i].substring(0,x);
				}
			}
		}
	}
	/**
	 * A chatty entry embodies a test for a particular environmental event, such as
	 * someone speaking or acting, and all possible responses to that event.
	 * @author Bo Zimmerman
	 */
	protected static class ChattyEntry
	{
		public ChatExpression		expression;
		public ChattyTestResponse[]	responses;
		public boolean				combatEntry	= false;
		public boolean				llm			= false;
		public boolean				antillm		= false;
		public int					orderNum	= 0;
		public ChattyGroup			fromGroup;
		/** N11: a ?guard line before it: a script condition, $t the speaker, or null */
		public String                guard        = null;
		/** N11: the guard, parsed, once */
		public String[]                guardEval    = null;

		public ChattyEntry(final ChatExpression expression, final boolean combat,
						   final boolean llm, final boolean antillm, final ChattyGroup group)
		{
			this.fromGroup=group;
			combatEntry = combat;
			this.llm = llm;
			this.antillm=antillm;
			this.expression = expression;
		}
		public ChattyEntry copyOf()
		{
			final ChattyEntry new1 = new ChattyEntry(expression,combatEntry,llm,antillm,fromGroup);
			new1.responses = responses;
			new1.guard = guard;
			new1.guardEval = guardEval;
			return new1;
		}
	}

	/**
	 * A chatty group is a collection of particular environmental event tests, and
	 * their possible responses.  It completely embodies a particular "chat behavior"
	 * for a particular kind of chatty mob.
	 * @author Bo Zimmerman
	 */
	protected static class ChattyGroup implements Cloneable
	{
		private static ChattyEntry[]			nothing		= new ChattyEntry[0];
		public String[]							groupNames;
		public MaskingLibrary.CompiledZMask[]	groupMasks;
		public ChattyEntry[]					entries	= nothing;
		public ChattyEntry[]					tickies	= nothing;
		public Map<String,String>				varOverride = new Hashtable<String,String>();
		public int								highestEntryNum = 0;

		public ChattyGroup(final String[] names, final MaskingLibrary.CompiledZMask[] masks)
		{
			groupNames = names;
			groupMasks = masks;
		}

		@Override
		public ChattyGroup clone()
		{
			try
			{
				final ChattyGroup g = (ChattyGroup) super.clone();
				return g;
			}
			catch (final Exception e)
			{
				return this;
			}
		}
	}

	private boolean isLLMSupported()
	{
		if(this.llmSupported == null)
			this.llmSupported = Boolean.valueOf(CMLib.protocol().isLLMInstalled());
		return this.llmSupported.booleanValue();
	}

	@Override
	public String accountForYourself()
	{
		if(lastThingSaid!=null)
			return "chattiness \""+lastThingSaid+"\"";
		else
			return "chattiness";
	}

	@Override
	public void setParms(String newParms)
	{
		// N10: DELAY=n, anywhere in it
		final String whole=newParms;
		final java.util.regex.Matcher dm=java.util.regex.Pattern.compile("(?i)(^|\\s)DELAY=(\\d+)(?=\\s|$)").matcher(newParms);
		final boolean hadDelay=dm.find();
		if(hadDelay)
		{
			responseDelay=Math.max(0,CMath.s_int(dm.group(2)));
			newParms=(newParms.substring(0,dm.start())+" "+newParms.substring(dm.end())).trim();
		}
		else
		if(!newParms.startsWith("+"))
			responseDelay=RESPONSE_DELAY;
		String bonusChatData;
		if(newParms.startsWith("+"))
			bonusChatData = newParms.substring(1);
		else
		{
			chatDatFilename = defaultChatFile();
			chatGroup = "";
			bonusChatData = "";
			final int fnDex = newParms.indexOf('=');
			if(fnDex >= 0)
			{
				chatDatFilename = newParms.substring(0,fnDex).trim();
				if(chatDatFilename.trim().length()==0)
					chatDatFilename = defaultChatFile();
				final String rest = newParms.substring(fnDex+1).trim();
				final int plusDex = rest.indexOf('+');
				if(plusDex >=0)
				{
					chatGroup=rest.substring(0,plusDex).trim();
					bonusChatData = rest.substring(plusDex+1).trim();
				}
				else
				if(rest.length()>0)
					chatGroup=rest;
			}
			else
			if(newParms.trim().length()>0)
				chatGroup = newParms.trim();
			super.setParms(newParms);
			addedChatVars.clear();
			addedChatEntries=new ChattyEntry[0];
		}
		if(bonusChatData.length()>0)
		{
			final StringBuffer rsc = new StringBuffer(CMStrings.replaceAll(bonusChatData,";","\n\r"));
			rsc.append("\n\r");
			final ChattyGroup[] addGroups=parseChatData(rsc);
			final ArrayList<ChattyEntry> newList=new ArrayList<ChattyEntry>(addedChatEntries.length);
			for(final ChattyEntry CE : addedChatEntries)
				newList.add(CE);
			for(final ChattyGroup CG : addGroups)
			{
				for(final ChattyEntry CE : CG.entries)
					newList.add(CE);
				addedChatVars.putAll(CG.varOverride);
			}
			addedChatEntries = newList.toArray(addedChatEntries);
		}
		responseQue=new SLinkedList<ChattyResponse>();
		baseChatGroup = null;
		currChatEntries=null;
		chatGroups.clear();
		this.tickDown=3;
		if(hadDelay && (!whole.startsWith("+")))
			super.setParms(whole); // kept with its DELAY=, for the save
	}

	@Override
	public String getLastThingSaid()
	{
		return lastThingSaid;
	}

	@Override
	public MOB getLastRespondedTo()
	{
		return lastRespondedToM;
	}

	protected static ChattyGroup newChattyGroup(final String name)
	{
		final char[] n = name.toCharArray();
		int last=0;
		char lookFor=' ';
		final ArrayList<String> names=new ArrayList<String>();
		final ArrayList<MaskingLibrary.CompiledZMask> masks=new ArrayList<MaskingLibrary.CompiledZMask>();
		for(int i=0;i<n.length;i++)
		{
			if(n[i]==lookFor)
			{
				final String s=name.substring(last,i).trim();
				last=i;
				if(s.length()>0)
				{
					if(lookFor=='/')
						masks.add(CMLib.masking().getPreCompiledMask(s));
					else
						names.add(s.toUpperCase());
				}
				if(lookFor=='/')
					lookFor=' ';
			}
			else
			if(n[i]=='/')
			{
				lookFor='/';
				last=i;
			}
		}
		final String s=name.substring(last,name.length()).trim();
		if(s.length()>0)
		{
			if(lookFor=='/')
				masks.add(CMLib.masking().maskCompile(s));
			else
				names.add(s.toUpperCase());
		}
		if((names.size()==0)&&(masks.size()==0))
			names.add("");
		return new ChattyGroup(names.toArray(new String[0]),masks.toArray(new MaskingLibrary.CompiledZMask[0]));
	}

	protected synchronized ChattyGroup[] getChatGroups()
	{
		if(this.chatDatFilename.length()==0)
			return new ChattyGroup[0];
		final ChattyGroup[] groups=unprotectedChatGroupLoad(this.chatDatFilename);
		if(myChatGeneration!=chatGeneration)
		{
			// a chat file was read again: what was worked out from it is stale
			myChatGeneration=chatGeneration;
			resetChat();
		}
		return groups;
	}

	/** bumped each time a chat file is read again (CHAT RELOAD, or an edit in builder mode) */
	protected static volatile int chatGeneration=0;
	/** the generation of the chat files this behaviour worked out its groups from */
	protected int myChatGeneration=0;
	/** each loaded chat file's time of change, and when it was last looked at, in builder mode */
	protected static final Map<String,long[]> chatFileStamps=new Hashtable<String,long[]>();

	/** the newest time of change of a chat file and its parts */
	protected static long chatFileTime(final String filename)
	{
		long newest=0;
		for(final CMFile F : CMFile.getExistingExtendedFiles(Resources.makeFileResourceName(filename), null, 0))
			newest=Math.max(newest,F.lastModified());
		return newest;
	}

	@Override
	public synchronized void resetChat()
	{
		baseChatGroup=null;
		myOldName="";
		currChatEntries=null;
		chatGroups.clear();
		chatEntryExpire=Long.MAX_VALUE;
	}

	/**
	 * Reads a chat file again (or every one, given null), for every NPC that
	 * talks from it (CHAT RELOAD).
	 * @param filename the file, or null for all
	 * @return how many cached files were dropped
	 */
	public static int reloadChat(final String filename)
	{
		int n=0;
		for(final Iterator<String> k=Resources.findResourceKeys("MUDCHAT GROUPS-");k.hasNext();)
		{
			final String key=k.next();
			if(key.startsWith("MUDCHAT GROUPS-")
			&&((filename==null)||key.equalsIgnoreCase("MUDCHAT GROUPS-"+filename.toLowerCase())))
			{
				Resources.removeResource(key);
				n++;
			}
		}
		chatGeneration++;
		return n;
	}

	protected static ChattyGroup[] unprotectedChatGroupLoad(final String filename)
	{
		ChattyGroup[] rsc=null;
		final String key = "MUDCHAT GROUPS-"+filename.toLowerCase();
		rsc=(ChattyGroup[])Resources.getResource(key);
		if((rsc!=null)&&(com.planet_ink.coffee_mud.core.save.SaveGame.isBuilderMode()))
		{
			// in builder mode a chat file edited since it was read is read again
			final long[] stamp=chatFileStamps.get(key);
			final long now=System.currentTimeMillis();
			if((stamp!=null)&&(now-stamp[1]>2000))
			{
				stamp[1]=now;
				if(chatFileTime(filename)!=stamp[0])
				{
					Resources.removeResource(key);
					chatGeneration++;
					rsc=null;
				}
			}
		}
		if(rsc!=null)
			return rsc;
		synchronized(CMClass.getSync(key))
		{
			rsc=(ChattyGroup[])Resources.getResource(key);
			if(rsc!=null)
				return rsc;
			rsc=loadChatData(filename);
			if(rsc.length == 0)
				Log.errOut("NO CHAT GROUPS ("+filename+")! OMG! NOT EVEN DEFAULTS!");
			Resources.submitResource(key,rsc);
			chatFileStamps.put(key,new long[] {chatFileTime(filename),System.currentTimeMillis()});
			return rsc;
		}
	}

	@Override
	public List<String> externalFiles()
	{
		final int x=parms.indexOf('=');
		if(x>0)
		{
			final Vector<String> xmlfiles=new Vector<String>();
			final String filename=parms.substring(0,x).trim();
			if(filename.length()>0)
				xmlfiles.addElement(filename.trim());
			return xmlfiles;
		}
		return null;
	}

	protected static ChattyGroup[] parseChatData(final StringBuffer rsc)
	{
		final ArrayList<ChattyGroup> chatGroups = new ArrayList<ChattyGroup>();
		ChattyGroup currentChatGroup=newChattyGroup("");
		final ArrayList<ChattyEntry> currentChatEntries = new ArrayList<ChattyEntry>();
		final ArrayList<ChattyEntry> tickyChatEntries = new ArrayList<ChattyEntry>();
		ChattyEntry currentChatEntry=null;
		final ArrayList<ChattyTestResponse> currentChatEntryResponses = new ArrayList<ChattyTestResponse>();

		ChattyGroup otherChatGroup;
		chatGroups.add(currentChatGroup);
		String pendingGuard=null; // N11
		String str=nextLine(rsc);
		while(str!=null)
		{
			if(str.length()>0)
			{
				boolean combat=false;
				boolean llm=false;
				boolean antillm=false;
				final char c=str.charAt(0);
				switch(c)
				{
				case '"':
					Log.sysOut("MudChat",str.substring(1));
					break;
				case '?': // N11: a guard for the next entry
					pendingGuard=str.substring(1).trim();
					if(pendingGuard.length()==0)
						pendingGuard=null;
					break;
				case '#':
					// nothing happened, move along
					break;
				case '*': case 'L': case '.':
				{
					while((str.length()>0) && ("*L.".indexOf(str.charAt(0))>=0))
					{
						llm = llm || str.startsWith("L");
						antillm = antillm || str.startsWith(".");
						combat = combat || str.startsWith("*");
						str=str.substring(1);
					}
					if((str.length()==0)||("([{<".indexOf(str.charAt(0))<0))
						break;
				}
				//$FALL-THROUGH$
				case '(':
				case '[':
				case '{':
				case '<':
					if(currentChatEntry!=null)
						currentChatEntry.responses = currentChatEntryResponses.toArray(new ChattyTestResponse[0]);
					currentChatEntryResponses.clear();
					try
					{
						final ChatExpression expression = parseExpression(str);
						currentChatEntry=new ChattyEntry(expression,combat,llm,antillm,currentChatGroup);
						currentChatEntry.orderNum=currentChatGroup.highestEntryNum++;
						currentChatEntry.guard=pendingGuard;
						pendingGuard=null;
						if(expression.type==ChatMatchType.RANDOM)
							tickyChatEntries.add(currentChatEntry);
						else
							currentChatEntries.add(currentChatEntry);
					}
					catch (final CMException e)
					{
						Log.debugOut("MudChat",e.getMessage());
						currentChatEntry=null;
					}
					break;
				case '$':
					if((str.length()>4)
					&&(str.charAt(1)=='{')
					&&(str.trim().endsWith("}"))
					&&((str.indexOf('=')>3)
						||((str.indexOf('+')>3)&&(str.indexOf('=')<0))))
					{
						str=str.trim();
						int x = str.indexOf('=');
						if(x<0)
							x=str.indexOf('+');
						final char op = str.charAt(x);
						final String var = str.substring(2,x).toUpperCase().trim();
						final String val = str.substring(x+1,str.length()-1); //notrim
						final Map<String,String> m = currentChatGroup.varOverride;
						if(!m.containsKey(var))
							m.put(var, val);
						else
						if(op == '=')
							m.put(var, val);
						else
							m.put(var, m.get(var)+val);
					}
					else
					{
						Log.warnOut("MudChat", "Malformed line " + str);
					}
					break;
				case '>':
					if(currentChatEntry!=null)
						currentChatEntry.responses = currentChatEntryResponses.toArray(new ChattyTestResponse[0]);
					currentChatGroup.entries = currentChatEntries.toArray(new ChattyEntry[0]);
					currentChatGroup.tickies = tickyChatEntries.toArray(new ChattyEntry[0]);
					currentChatEntries.clear();
					tickyChatEntries.clear();
					currentChatGroup=newChattyGroup(str.substring(1).trim());
					if(currentChatGroup == null)
						return null;
					chatGroups.add(currentChatGroup);
					currentChatEntry=null;
					break;
				case '@':
					{
						otherChatGroup=matchChatGroup(null,str.substring(1).trim(),chatGroups.toArray(new ChattyGroup[0]));
						if(otherChatGroup==null)
							otherChatGroup=chatGroups.get(0);
						if(otherChatGroup != currentChatGroup)
						{
							for(final String var : otherChatGroup.varOverride.keySet())
								if(!currentChatGroup.varOverride.containsKey(var))
									currentChatGroup.varOverride.put(var, otherChatGroup.varOverride.get(var));
							for(final ChattyEntry CE : otherChatGroup.entries)
							{
								final ChattyEntry copyCE = CE.copyOf();
								copyCE.fromGroup = currentChatGroup;
								copyCE.orderNum = currentChatGroup.highestEntryNum++;
								currentChatEntries.add(copyCE);
							}
							for(final ChattyEntry CE : otherChatGroup.tickies)
							{
								final ChattyEntry copyCE = CE.copyOf();
								copyCE.fromGroup = currentChatGroup;
								copyCE.orderNum = currentChatGroup.highestEntryNum++;
								tickyChatEntries.add(copyCE);
							}
						}
						break;
					}
				case '%':
					{
						final StringBuffer rsc2=new StringBuffer(Resources.getFileResource(str.substring(1).trim(),true).toString());
						if (rsc2.length() < 1)
						{
							Log.warnOut("MudChat", "Error reading resource " + str.substring(1).trim());
						}
						rsc.insert(0,rsc2.toString());
						break;
					}
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
					if(currentChatEntry!=null)
					{
						currentChatEntryResponses.add(new ChattyTestResponse(
								currentChatEntry.fromGroup, str,currentChatEntry.combatEntry));
					}
					break;
				}
			}
			str=nextLine(rsc);
		}
		if(currentChatEntry!=null)
			currentChatEntry.responses = currentChatEntryResponses.toArray(new ChattyTestResponse[0]);
		currentChatGroup.entries = currentChatEntries.toArray(new ChattyEntry[0]);
		currentChatGroup.tickies = tickyChatEntries.toArray(new ChattyEntry[0]);
		currentChatEntries.clear();
		tickyChatEntries.clear();
		return chatGroups.toArray(new ChattyGroup[0]);
	}

	protected static ChattyGroup[] loadChatData(final String resourceName)
	{
		final CMFile[] fileList = CMFile.getExistingExtendedFiles(Resources.makeFileResourceName(resourceName), null, 0);
		if(fileList.length>0)
		{
			final ArrayList<ChattyGroup> chatGroups = new ArrayList<ChattyGroup>();
			for(final CMFile F : fileList)
				chatGroups.addAll(Arrays.asList(parseChatData(F.text())));
			return chatGroups.toArray(new ChattyGroup[chatGroups.size()]);
		}
		else
		{
			Log.errOut("MudChat","Unable to load "+Resources.makeFileResourceName("behavior/"+resourceName)+" or "+Resources.makeFileResourceName(resourceName));
			return new ChattyGroup[0];
		}
	}

	public static String nextLine(final StringBuffer tsc)
	{
		String ret=null;
		int sr=-1;
		int se=-1;
		if((tsc!=null)&&(tsc.length()>0))
		{
			sr=-1;
			se=-1;
			for(int i=0;i<tsc.length()-1;i++)
			{
				if((tsc.charAt(i)=='\n')||(tsc.charAt(i)=='\r'))
				{
					sr=i;
					while((i<tsc.length())
					&&((tsc.charAt(i)=='\n')||(tsc.charAt(i)=='\r')))
					{
						i++;
						se=i;
					}
					break;
				}
			}
			if(sr<0)
			{
				ret=tsc.toString().trim();
				tsc.setLength(0);
				if(ret.length()==0)
					ret = null;
			}
			else
			{
				ret=tsc.substring(0,sr).trim();
				tsc.delete(0,se);
			}
		}
		return ret;

	}

	protected static ChattyGroup matchChatGroup(final MOB meM, String myName, final ChattyGroup[] chatGroups)
	{
		myName=myName.toUpperCase();
		if(myName.equals("DEFAULT"))
			return (chatGroups.length>0)?chatGroups[0]:null;
		for(final ChattyGroup CG : chatGroups)
		{
			if(CG.entries!=null)
			{
				for(final String name : CG.groupNames)
				{
					if(name.equalsIgnoreCase(myName))
						return CG;
				}
				if(meM != null)
				{
					for(final MaskingLibrary.CompiledZMask mask : CG.groupMasks)
					{
						if(CMLib.masking().maskCheck(mask, meM, true))
							return CG;
					}
				}
			}
		}
		return null;
	}

	protected ChattyGroup getMyBaseChatGroup(final MOB forMe, final ChattyGroup[] chatGroups)
	{
		if((baseChatGroup!=null)
		&&(myOldName.equals(forMe.Name())))
			return baseChatGroup;

		myOldName=forMe.Name();
		ChattyGroup matchedCG=matchChatGroup(forMe,this.chatGroup,chatGroups);
		if(matchedCG==null)
		{
			matchedCG=matchChatGroup(forMe,CMLib.english().removeArticleLead(CMStrings.removeColors(myOldName.toUpperCase())),chatGroups);
			if(matchedCG==null)
			{
				matchedCG=matchChatGroup(forMe,forMe.charStats().raceName(),chatGroups);
				if(matchedCG==null)
					matchedCG=matchChatGroup(forMe,forMe.charStats().getCurrentClass().name(),chatGroups);
			}
		}
		if(matchedCG!=null)
		{
			baseChatGroup =  matchedCG;
			return matchedCG;
		}
		if(chatGroups.length==0)
		{
			if(this.chatDatFilename.length()>0) // CHATDEFAULT=NONE has none, as meant
				Log.errOut("No Chat Group identified for "+forMe.Name()+"@"+
					CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(forMe)));
			baseChatGroup = new ChattyGroup(new String[0], new MaskingLibrary.CompiledZMask[0]);
		}
		else
			baseChatGroup =  chatGroups[0];
		return baseChatGroup;
	}

	protected PairList<ChattyGroup, Long> getChatGroups(final MOB forMe, final ChattyGroup[] chatGroups)
	{
		if((this.chatGroups != null)
		&&(this.chatGroups.size()>0) // N11
		&&(GameTime.now()<this.chatEntryExpire)
		&&(myOldName.equals(forMe.Name())))
			return this.chatGroups;
		long newExpire = Long.MAX_VALUE;
		if(this.chatGroups.size()>0)
		{
			final Iterator<Pair<ChattyGroup,Long>> i = this.chatGroups.iterator();
			this.chatGroups.clear();
			for(;i.hasNext();)
			{
				final Pair<ChattyGroup,Long> p = i.next();
				if(GameTime.now()<p.second.longValue())
				{
					this.chatGroups.add(p);
					if(p.second.longValue() < newExpire)
						newExpire = p.second.longValue();
				}
			}
		}
		if((!myOldName.equals(forMe.Name()))
		||(this.chatGroups.size()==0))
		{
			this.chatGroups.clear();
			final ChattyGroup chatGrp=getMyBaseChatGroup(forMe,chatGroups);
			this.chatGroups.add(chatGrp, Long.valueOf(Long.MAX_VALUE));
		}
		return this.chatGroups;
	}

	protected static Comparator<ChattyEntry> comparator = new Comparator<ChattyEntry>()
	{
		@Override
		public int compare(final ChattyEntry o1, final ChattyEntry o2)
		{
			if(o1.orderNum<o2.orderNum)
				return -1;
			return 1;
		}
	};

	protected List<ChattyEntry> getChatEntries(final String preMatcher, final MOB forMe, final ChattyGroup[] chatGroups)
	{
		final ChatEntryGroup allChatEntries = getChatEntries(forMe, chatGroups);
		final List<String> matchWords = CMParms.parseSpaces(CMStrings.removePunctuationStrict(preMatcher.toLowerCase().trim()),true);
		final Set<ChattyEntry> chatEntries = new HashSet<ChattyEntry>();
		for(final String word : matchWords)
			if(allChatEntries.mapped.containsKey(word))
				chatEntries.addAll(Arrays.asList(allChatEntries.mapped.get(word)));
		if(allChatEntries.mapped.containsKey(""))
			chatEntries.addAll(Arrays.asList(allChatEntries.mapped.get("")));
		final List<ChattyEntry> finalChatEntries = new XArrayList<ChattyEntry>(chatEntries);
		Collections.sort(finalChatEntries,comparator);
		return finalChatEntries;
	}

	protected ChatEntryGroup getChatEntries(final MOB forMe, final ChattyGroup[] chatGroups)
	{
		if((this.currChatEntries != null)
		&&(GameTime.now()<this.chatEntryExpire)
		&&(myOldName.equals(forMe.Name())))
			return this.currChatEntries;
		final XArrayList<ChattyEntry> newEntries = new XArrayList<ChattyEntry>();
		if(addedChatEntries != null)
			newEntries.addAll(addedChatEntries);
		final PairList<ChattyGroup, Long> currGroups = getChatGroups(forMe,chatGroups);
		final boolean isLLMActive = CMLib.protocol().isLLMInstalled();
		for(final Iterator<ChattyGroup> i=currGroups.firstIterator();i.hasNext();)
		{
			for(final ChattyEntry e : i.next().entries)
				if((e.llm && isLLMActive)
				||(e.antillm && !isLLMActive)
				||(!e.llm && !e.antillm))
					newEntries.add(e);
		}
		this.currChatEntries = new ChatEntryGroup(newEntries);
		return this.currChatEntries;
	}

	protected String getVarValue(final Modifiable M, final ChattyGroup grp, final String var)
	{
		String repl;
		if(this.addedChatVars.containsKey(var))
			return this.addedChatVars.get(var);
		if((grp != null)&&(grp.varOverride.containsKey(var)))
			return grp.varOverride.get(var);
		if(var.equals("THEMEDESC"))
		{
			if(M instanceof Environmental)
			{
				final Room R = CMLib.map().getStartRoom((Environmental)M);
				final int theme = (R!=null) ? R.getArea().getTheme() : CMProps.getIntVar(CMProps.Int.MUDTHEME);
				final List<String> l = new ArrayList<String>();
				if(CMath.bset(theme,Area.THEME_FANTASY))
					l.add("medieval fantasy");
				if(CMath.bset(theme,Area.THEME_HEROIC))
					l.add("modern day");
				if(CMath.bset(theme,Area.THEME_TECHNOLOGY))
					l.add("futuristic");
				repl = CMParms.toListString(l);
			}
			else
				repl="";
		}
		else
		if(var.equals("ROOMDESC"))
		{
			final Room R = CMLib.map().roomLocation((Environmental)M);
			if(R!=null)
			{
				if(M instanceof MOB)
					repl = R.displayText((MOB)M);
				else
					repl = R.displayText();
			}
			else
				repl = "";
		}
		else
		if(var.startsWith("PERSONALITY"))
		{
			int maxTraits = 2;
			final String[] parts = var.split(":");
			String override="";
			for(int x=1;x<parts.length;x++)
			{
				if(CMath.isInteger(parts[x]))
					maxTraits = Math.max(1,CMath.s_int(parts[x].trim()));
				else
					override=parts[x];
			}
			String ad = "";
			if(M instanceof MOB)
			{
				final MOB mob=(MOB)M;
				final String hashStr = mob.name() + CMLib.map().getExtendedRoomID(mob.getStartRoom());
				final int hash = hashStr.hashCode();
				final Random r = new Random(hash);
				final int numTraits = r.nextInt(maxTraits) + 1;
				final List<String> keys = new ArrayList<String>();
				if(override.length()>0)
					keys.add(override.toUpperCase().trim());
				else
				{
					if(CMLib.factions().isAlignmentLoaded(Faction.Align.CHAOTIC))
					{
						if(CMLib.flags().isChaotic(mob))
							keys.add("CHAOTIC");
						else
						if(CMLib.flags().isLawful(mob))
							keys.add("LAWFUL");
						else
							keys.add("MODERATE");
					}
					if(CMLib.factions().isAlignmentLoaded(Faction.Align.EVIL))
					{
						if(CMLib.flags().isEvil(mob))
							keys.add("EVIL");
						else
						if(CMLib.flags().isGood(mob))
							keys.add("GOOD");
						else
							keys.add("NEUTRAL");
					}
				}
				final Object[][][] lists = CMProps.getListFileGrid(ListFile.PERSONALITY_TRAITS);
				final List<Object[]> traits = new ArrayList<Object[]>();
				for (final Object[][] addOnList : lists)
				{
					if(keys.contains(((String)addOnList[0][0]).toUpperCase().trim()))
						traits.addAll(Arrays.asList(addOnList));
				}
				final List<String> chosen = new ArrayList<String>();
				for(int x=0;(x<numTraits)&&(traits.size()>0);x++)
				{
					final Object[] trait = traits.get(r.nextInt(traits.size()));
					if(!chosen.contains(trait[0]))
						chosen.add((String)trait[0]);
				}
				final Ability moodA=mob.fetchEffect("Mood");
				if(moodA!=null)
					chosen.add(moodA.text());
				ad = CMParms.toListString(chosen);
			}
			repl = ad.trim();
			if(repl.length()==0)
				repl="irrelevant";
		}
		else
			repl=CMLib.coffeeMaker().getAnyGenStat(M, var);
		return repl;
	}

	protected String mcFilter(final String finalCommand, final MOB source, final Environmental target,
							  final String rest, final String whole, final ChattyGroup grp)
	{
		final StringBuilder fc = new StringBuilder("");
		for(int i=0;i<finalCommand.length();i++)
		{
			final char c = finalCommand.charAt(i);
			if((c=='$')&&(i<finalCommand.length()-1))
			{
				final char c2=finalCommand.charAt(i+1);
				switch(c2)
				{
				case 'r':
					fc.append(rest);
					i++;
					break;
				case 'w':
					fc.append(whole);
					i++;
					break;
				case 't':
					fc.append(target.name());
					i++;
					break;
				case 'n':
					fc.append(source.name());
					i++;
					break;
				case '$':
					fc.append('$');
					i++;
					break;
				case '<':
				case '{':
				{
					final char endChar = c2=='<'?'>':'}';
					final int end = finalCommand.indexOf(endChar,i+2);
					if(end>0)
					{
						final String var = finalCommand.substring(i+2,end).toUpperCase().trim();
						final Modifiable M = (c2=='<')?target:source;
						final String repl = getVarValue(M,grp,var);
						if(repl.indexOf('$')>0)
							fc.append(mcFilter(repl,source,target,rest,whole,grp));
						else
							fc.append(repl);
						i=end;
					}
					else
						fc.append(c);
					break;
				}
				case '%':
				{
					final int y=finalCommand.indexOf('%',i+2);
					if(y>0)
					{
						if(scriptEngine == null)
						{
							scriptEngine=(ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
							scriptEngine.setSavable(false);
							scriptEngine.setVarScope("*");
						}
						final String var = finalCommand.substring(i,y+1);
						final String repl = scriptEngine.varify(
								new MPContext(source, source, source, target, null, null, "", null),var);
						fc.append(repl);
						i=y;
					}
					else
						fc.append(c);
					break;
				}
				default:
					fc.append(c);
					break;
				}
			}
			else
				fc.append(c);
		}
		return fc.toString();
	}

	protected synchronized LLMSession getLLMSession(final ChattyGroup grp, final MOB source)
	{
		if((this.llmSession == null)
		||((GameTime.now()-this.llmLastUse)>360000))
		{
			String prompt = grp.varOverride.get("LLMPROMPT");
			if(prompt == null)
				prompt="";
			else
				prompt = mcFilter(prompt.trim(),source,null,"","",grp);
			Integer mem = null;
			final String memstr = grp.varOverride.get("LLMMEM");
			if(CMath.isInteger(memstr))
				mem=Integer.valueOf(CMath.s_int(memstr));
			this.llmSession = CMLib.protocol().createLLMSession(prompt,mem);
		}
		this.llmLastUse=GameTime.now();
		return this.llmSession;
	}

	protected void queResponse(final List<Pair<ChattyTestResponse,String>> responses,
								final MOB source, final MOB target, final String wholeSay)
	{
		int total=0;
		for(final Pair<ChattyTestResponse,String> c : responses)
			total+=c.first.weight;
		if(total == 0)
			return;
		ChattyTestResponse selection=null;
		String rest=null;
		int select=CMLib.dice().roll(1,total,0);
		for(final Pair<ChattyTestResponse,String> c : responses)
		{
			select-=c.first.weight;
			if(select<=0)
			{
				selection=c.first;
				rest=c.second;
				break;
			}
		}

		if(selection!=null)
		{
			if(selection.switchDB.trim().equals("-")) // N11: $@-, back to the base group for this speaker
			{
				this.chatGroups.clear();
				this.currChatEntries = null;
				this.chatEntryExpire = Long.MAX_VALUE;
				keptGroup(source, target, null);
			}
			else
			if(selection.switchDB.length()>0)
			{
				final ChattyGroup[] allGroups = getChatGroups();
				final boolean kept = selection.switchDB.startsWith("!"); // N11: $@!group, until changed, and saved
				final String switchTo = kept ? selection.switchDB.substring(1).trim() : selection.switchDB;
				final boolean add = switchTo.startsWith("+");
				final String dbname = add?switchTo.substring(1).trim():switchTo;
				final ChattyGroup matchedCG=matchChatGroup(source, dbname, allGroups);
				if(matchedCG != null)
				{
					if(kept)
						keptGroup(source, target, dbname);
					final PairList<ChattyGroup,Long> list = this.getChatGroups(source, allGroups);
					if(kept && list.containsFirst(matchedCG) && (!add))
						list.clear();
					if(!list.containsFirst(matchedCG))
					{
						final long expire = kept ? Long.MAX_VALUE : (GameTime.now() + TALK_SWITCH_EXPIRE);
						if(!add)
							list.clear();
						list.add(new Pair<ChattyGroup, Long>(matchedCG,Long.valueOf(expire)));
						if(expire < this.chatEntryExpire)
							this.chatEntryExpire = expire;
						this.currChatEntries = null;
					}
				}
			}
			final boolean flag =selection.combatFlag;
			final ChattyGroup grp = selection.fromGroup;
			for(String finalCommand : selection.responses)
			{
				if(finalCommand.trim().length()==0)
					continue;
				switch(finalCommand.charAt(0))
				{
				case ':':
				{
					finalCommand = finalCommand.substring(1).trim();
					if(source!=null)
					{
						finalCommand=CMStrings.replaceAll(finalCommand," her "," "+source.charStats().hisher()+" ");
						finalCommand=CMStrings.replaceAll(finalCommand," his "," "+source.charStats().hisher()+" ");
					}
					finalCommand = "emote "+finalCommand;
					break;
				}
				case '!':
				{
					finalCommand=finalCommand.substring(1).trim();
					break;
				}
				case '"':
				{
					finalCommand="say \""+finalCommand.substring(1).trim()+"\"";
					break;
				}
				case '`':
				{
					if(!this.isLLMSupported())
					{
						finalCommand = null;
						break;
					}
					final String prompt = mcFilter(finalCommand.substring(1).trim(),source,target,rest,wholeSay,grp);
					CMLib.threads().executeRunnable(new Runnable()
					{
						final String promptStr = prompt;
						final long startTime = GameTime.now();
						final MOB M = source;
						final ChattyGroup cGroup = grp;
						final boolean combatFlag = flag;
						final MOB T = target;
						@Override
						public void run()
						{
							String str = promptStr;
							if(str.equalsIgnoreCase("reset")&&(CMSecurity.isAllowed(T, M.location(), CMSecurity.SecFlag.CMDMOBS)))
							{
								llmLastUse=0;
								str = L("Greetings");
							}
							final LLMSession session = getLLMSession(cGroup,M);
							if(session != null)
							{
								String response = session.chat(str);
								if(response.length()>2)
								{
									if(response.startsWith("\"") && response.endsWith("\""))
										response = response.substring(1,response.length()-1);
									else
									if(response.startsWith("'") && response.endsWith("'"))
										response = response.substring(1,response.length()-1);
								}
								final long delayWas = GameTime.now() - startTime;
								final long delayRemaining = (CMProps.getTickMillis()*responseDelay)-delayWas;
								final int ticksRemain = (int)Math.floor(delayRemaining / CMProps.getTickMillis());
								final List<String> words=new XVector<String>("say",response);
								responseQue.add(new ChattyResponse(words,ticksRemain,combatFlag));
							}
						}
					});
					finalCommand = null;
					break;
				}
				case '&': // N11: a MOBPROG line, the NPC its host and the speaker $n
				{
					final String line=finalCommand.substring(1).trim();
					if(line.length()>0)
					{
						final ChattyResponse R=new ChattyResponse(new XVector<String>("MUDCHAT",line),responseDelay,selection.combatFlag);
						R.script=line;
						R.speaker=target;
						responseQue.add(R);
					}
					finalCommand = null;
					break;
				}
				default:
					if(target!=null)
						finalCommand="sayto \""+target.name()+"\" "+finalCommand.trim();
					break;
				}
				if(finalCommand != null)
				{
					finalCommand = mcFilter(finalCommand,source,target,rest,wholeSay,grp);
					List<String> words=CMParms.parse(finalCommand);
					for(final ChattyResponse R : responseQue)
					{
						if(CMParms.combine(R.parsedCommand,1).equalsIgnoreCase(finalCommand))
						{
							words=null;
							break;
						}
					}
					if(words!=null)
						responseQue.add(new ChattyResponse(words,responseDelay,selection.combatFlag));
				}
			}
		}
	}

	protected static Pair<ChatMatchType, Character> getTypeAndCloser(final char openChar)
	{
		switch(openChar)
		{
		case '(':
			return new Pair<ChatMatchType, Character>(ChatMatchType.SAY, Character.valueOf(')'));
		case '[':
			return new Pair<ChatMatchType, Character>(ChatMatchType.TEMOTE, Character.valueOf(']'));
		case '{':
			return new Pair<ChatMatchType, Character>(ChatMatchType.EMOTE, Character.valueOf('}'));
		case '<':
			return new Pair<ChatMatchType, Character>(ChatMatchType.RANDOM, Character.valueOf('>'));
		}
		return null;
	}

	private enum MatchState
	{
		INSIDE_PAREN,
		INSIDE_EXP,
		POST_CONN,
		POST_PAREN
	}


	protected static ChatExpression parseExpression(String expression) throws CMException
	{
		if(expression == null)
			return null;
		expression=expression.trim();
		if(expression.length()==0)
			return null;
		final ChatExpression top = new ChatExpression();
		final char openChar=expression.charAt(0);
		Pair<ChatMatchType,Character> mtype = getTypeAndCloser(openChar);
		if(mtype==null)
			return null;
		top.type=mtype.first;
		final Stack<Character> closeStack = new Stack<Character>();
		closeStack.push(mtype.second);
		final Stack<ChatExpression> stack=new Stack<ChatExpression>();
		stack.push(top);
		final StringBuilder str=new StringBuilder("");
		ChatMatch match=new ChatMatch();
		MatchState state=MatchState.INSIDE_PAREN;
		for(int i=1;i<=expression.length();i++)
		{
			final char c=(i==expression.length())?'\0':expression.charAt(i);
			switch(c)
			{
			case '\\':
				if(i<expression.length()-1)
				{
					str.append(expression.charAt(i+1));
					i++;
				}
				else
					str.append("\\");
				break;
			case ')': case ']': case '}': case '>':
				if(closeStack.size()==0)
					throw new CMException("Parse error at "+i+": "+c+" not expected w/o open. ("+expression+")");
				if(c != closeStack.peek().charValue())
				{
					if(state==MatchState.POST_PAREN)
						throw new CMException("Parse error at "+i+": "+c+" not expected w/o connector.("+expression+")");
					state=MatchState.INSIDE_EXP;
					str.append(c);
					break;
				}
				closeStack.pop();
			//$FALL-THROUGH$
			case '\0':
			case '&': case '|': case '~':
			{
				if(stack.size()==0)
				{
					if(c!='\0')
						throw new CMException("Parse error at "+i+": "+c+" not expected w/o connector.("+expression+")");
					else
						break;
				}
				final ChatExpression cur=stack.peek();
				final Pair<Object,ChatExpConn> pair;
				if(state!=MatchState.POST_PAREN)
				{
					match.str = str.toString().toLowerCase().trim();
					// QuestChat's said-once code (words::code) is not a word to look for
					final int codeDex = match.str.lastIndexOf("::");
					final String words = CMStrings.removePunctuationStrict((codeDex>0)?match.str.substring(0,codeDex):match.str);
					if(words.indexOf(' ')<0)
						cur.ones.add(words);
					else
					{
						String longestWord ="";
						for(final String w : CMParms.parseSpaces(words, true))
							if(w.length()>longestWord.length())
								longestWord=w;
						if(longestWord.length()>0)
							cur.ones.add(longestWord);
					}
					pair=new Pair<Object,ChatExpConn>(match,ChatExpConn.END);
					cur.exp.add(pair);
				}
				else
				if(cur.exp.size()>0)
					pair=cur.exp.get(cur.exp.size()-1);
				else
					throw new CMException("Parse error at "+i+": "+c+" encountered in post-paren w/o sibling.("+expression+")");
				str.setLength(0);
				switch(c)
				{
				case '&':
					pair.second=ChatExpConn.AND;
					state=MatchState.POST_CONN;
					break;
				case '|':
					pair.second=ChatExpConn.OR;
					state=MatchState.POST_CONN;
					break;
				case '~':
					pair.second=ChatExpConn.ANDNOT;
					state=MatchState.POST_CONN;
					break;
				case '\0':
					break;
				default:
					state=MatchState.POST_PAREN;
					if(stack.size()>0)
						stack.pop();
					else
						throw new CMException("Parse error at "+i+": "+c+" encountered with empty stack.("+expression+")");
					break;
				}
				match=new ChatMatch();
				break;
			}
			case '[': case '{': case '<':
				if(c != openChar)
				{
					if(state==MatchState.POST_PAREN)
						throw new CMException("Parse error at "+i+": "+c+" not expected w/o connector.("+expression+")");
					state=MatchState.INSIDE_EXP;
					str.append(c);
					break;
				}
				//$FALL-THROUGH$
			case '(':
				if((state==MatchState.INSIDE_PAREN)
				||(state==MatchState.POST_CONN))
				{
					final ChatExpression cur=stack.peek();
					mtype=getTypeAndCloser(c);
					final ChatExpression next=new ChatExpression();
					cur.exp.add(new Pair<Object,ChatExpConn>(next,ChatExpConn.END));
					next.type=mtype.first;
					closeStack.push(mtype.second);
					stack.push(next);
					str.setLength(0);
				}
				else
				if(state==MatchState.INSIDE_EXP)
					str.append(c);
				else
					throw new CMException("Parse error at "+i+": "+c+" not expected w/o connector.("+expression+")");
				break;
			case '^':
			case '/':
			case '=':
				if((state==MatchState.INSIDE_PAREN)
				||(state==MatchState.POST_CONN))
				{
					state=MatchState.INSIDE_EXP;
					switch(c)
					{
					case '^':
						match.flag=ChatMatchFlag.TOP;
						break;
					case '=':
						match.flag=ChatMatchFlag.EXACT;
						break;
					case '/':
						match.flag=ChatMatchFlag.ZAPPER;
						break;
					}
					str.setLength(0);
				}
				else
				if(state==MatchState.INSIDE_EXP)
					str.append(c);
				else
					throw new CMException("Parse error at "+i+": "+c+" not expected w/o connector.("+expression+")");
				break;
			default:
				if(!Character.isWhitespace(c))
				{
					if(state==MatchState.POST_PAREN)
						throw new CMException("Parse error at "+i+": "+c+" not expected w/o connector.("+expression+")");
					state=MatchState.INSIDE_EXP;
				}
				str.append(c);
				break;
			}
		}
		return top;
	}

	protected boolean match(final MOB speaker, final ChatMatch match, final String message, final String[] rest)
	{
		switch(match.flag)
		{
		case EXACT:
		{
			if(match.str.equals(message))
			{
				rest[0]="";
				return true;
			}
			return false;
		}
		case INSTR:
		{
			final int x=message.indexOf(match.str);
			if(x<0)
				return false;
			if(match.str.length()==0)
				return true;
			if(((x==0)||(Character.isWhitespace(message.charAt(x-1))))
			&&(((x+match.str.length())==message.length())||(Character.isWhitespace(message.charAt(x+match.str.length())))))
			{
				rest[0]=message.substring(x+match.str.length());
				return true;
			}
			return false;
		}
		case TOP:
		{
			if(message.startsWith(match.str)
			&&((message.length()==match.str.length())||(Character.isWhitespace(message.charAt(match.str.length())))))
			{
				rest[0]=message.substring(match.str.length());
				return true;
			}
			return false;
		}
		case ZAPPER:
			return CMLib.masking().maskCheck(match.str,speaker,false);
		}
		return false;
	}

	protected boolean match(final MOB speaker, final ChatExpression expression, final int val)
	{
		// connectors as in the speech matcher below
		boolean rollingTruth=true;
		ChatExpConn conn = null;
		ChatExpConn nconn = null;
		for(final Pair<Object,ChatExpConn> p : expression.exp)
		{
			boolean thisTruth;
			if(p.first instanceof ChatExpression)
			{
				thisTruth=match(speaker,(ChatExpression)p.first,val);
				nconn=p.second;
			}
			else
			if(p.first instanceof ChatMatch)
			{
				final ChatMatch cm=(ChatMatch)p.first;
				if(cm.flag==ChatMatchFlag.ZAPPER)
					thisTruth=match(speaker,(ChatMatch)p.first,"",new String[1]);
				else // <30> is a 30% chance: the roll, 1 to 100, is 30 or less
					thisTruth=val<=CMath.s_int(cm.str.trim());
				nconn=p.second;
			}
			else
				continue;
			if(conn == null)
			{
				rollingTruth=thisTruth;
				conn=nconn;
				continue;
			}
			final ChatExpConn useConn=conn;
			conn=nconn;
			switch(useConn)
			{
			case AND:
				rollingTruth = rollingTruth && thisTruth;
				if(!rollingTruth)
					return false;
				break;
			case ANDNOT:
				rollingTruth = rollingTruth && !thisTruth;
				if(!rollingTruth)
					return false;
				break;
			case END:
				return rollingTruth;
			case OR:
				rollingTruth = rollingTruth || thisTruth;
				break;
			}
		}
		return rollingTruth;
	}


	protected boolean match(final MOB speaker, final ChatExpression expression, final String lowerStrNoPunc, final String[] rest)
	{
		boolean rollingTruth=true;
		ChatExpConn conn = null;
		ChatExpConn nconn = null;
		for(final Pair<Object,ChatExpConn> p : expression.exp)
		{
			boolean thisTruth;
			if(p.first instanceof ChatExpression)
			{
				thisTruth=match(speaker,(ChatExpression)p.first,lowerStrNoPunc,rest);
				nconn=p.second;
			}
			else
			if(p.first instanceof ChatMatch)
			{
				thisTruth=match(speaker,(ChatMatch)p.first,lowerStrNoPunc,rest);
				nconn=p.second;
			}
			else
				continue;
			if(conn == null)
				rollingTruth=thisTruth;
			else
			switch(conn)
			{
			case AND:
				rollingTruth = rollingTruth && thisTruth;
				if(!rollingTruth)
					return false;
				break;
			case ANDNOT:
				rollingTruth = rollingTruth && !thisTruth;
				if(!rollingTruth)
					return false;
				break;
			case END:
				return rollingTruth;
			case OR:
				rollingTruth = rollingTruth || thisTruth;
				break;
			}
			conn=nconn;
		}
		return rollingTruth;
	}

	/** an entry's pattern, written back from what was parsed, for CHATTEST */
	protected static String describe(final ChatExpression e)
	{
		final StringBuilder str=new StringBuilder("");
		for(final Pair<Object,ChatExpConn> p : e.exp)
		{
			if(p.first instanceof ChatMatch)
			{
				final ChatMatch m=(ChatMatch)p.first;
				switch(m.flag)
				{
				case EXACT:
					str.append('=').append(m.str);
					break;
				case TOP:
					str.append('^').append(m.str);
					break;
				case ZAPPER:
					str.append('{').append(m.str).append('}');
					break;
				default:
					str.append(m.str);
					break;
				}
			}
			else
			if(p.first instanceof ChatExpression)
				str.append('(').append(describe((ChatExpression)p.first)).append(')');
			switch(p.second)
			{
			case AND:
				str.append(" & ");
				break;
			case OR:
				str.append(" | ");
				break;
			case ANDNOT:
				str.append(" & !");
				break;
			default:
				break;
			}
		}
		return str.toString().trim();
	}

	/** a chat group's names, for CHATTEST */
	protected static String groupName(final ChattyGroup G)
	{
		if((G==null)||(G.groupNames==null)||(G.groupNames.length==0))
			return "(the default group)";
		return String.join("/",G.groupNames);
	}

	@Override
	public boolean answersTopic(final MOB npc, final MOB speaker, final String topic)
	{
		final String str=CMLib.english().stripEnglishPunctuation((topic==null)?"":topic).toLowerCase().trim();
		if(str.length()==0)
			return false;
		final ChatEntryGroup group=getChatEntries(npc,getChatGroups());
		final String[] rest=new String[1];
		// by the topic's own words, so that an entry for anything at all does not count
		for(final String word : CMParms.parseSpaces(CMStrings.removePunctuationStrict(str),true))
		{
			final ChattyEntry[] es=group.mapped.get(word);
			if((es==null)||(word.length()==0))
				continue;
			for(final ChattyEntry e : es)
			{
				if((e.expression.type==ChatMatchType.SAY)&&(!e.combatEntry)&&match(speaker,e.expression,str,rest))
					return true;
			}
		}
		return false;
	}

	@Override
	public List<String> chatTopics(final MOB npc)
	{
		final List<String> list=new ArrayList<String>();
		final List<ChattyEntry> entries=new ArrayList<ChattyEntry>(Arrays.asList(addedChatEntries));
		if((chatDatFilename.length()>0)&&(!chatDatFilename.equalsIgnoreCase(defaultChatFile())))
		{
			final PairList<ChattyGroup,Long> groups=getChatGroups(npc,getChatGroups());
			for(final Iterator<ChattyGroup> i=groups.firstIterator();i.hasNext();)
				entries.addAll(Arrays.asList(i.next().entries));
		}
		for(final ChattyEntry e : entries)
		{
			if((e.expression==null)||(e.expression.type!=ChatMatchType.SAY)||e.combatEntry)
				continue;
			final String w=firstWord(e.expression);
			if((w!=null)&&(!list.contains(w)))
				list.add(w);
		}
		return list;
	}

	/**
	 * N24: an expression's first word or phrase, not a mask, for a topic hint.
	 * @param e the expression
	 * @return the word, in lower case, or null
	 */
	protected static String firstWord(final ChatExpression e)
	{
		for(final Pair<Object,ChatExpConn> p : e.exp)
		{
			if(p.first instanceof ChatMatch)
			{
				final ChatMatch m=(ChatMatch)p.first;
				if((m.flag!=ChatMatchFlag.ZAPPER)&&(m.str!=null)&&(m.str.trim().length()>0))
					return m.str.trim().toLowerCase();
			}
			else
			if(p.first instanceof ChatExpression)
			{
				final String s=firstWord((ChatExpression)p.first);
				if(s!=null)
					return s;
			}
		}
		return null;
	}

	@Override
	public String chatTest(final MOB npc, final MOB speaker, final String sentence)
	{
		final StringBuilder out=new StringBuilder("");
		final String str=CMLib.english().stripEnglishPunctuation(sentence).toLowerCase().trim();
		out.append("CHATTEST ").append(npc.Name()).append(": '").append(sentence).append("' said by ").append(speaker.Name())
		   .append(", from ").append(chatDatFilename.length()==0?"no chat file":chatDatFilename).append("\n\r");
		final ChattyGroup[] all=getChatGroups();
		// the active groups first, as a reply works them out (they set the base group)
		final PairList<ChattyGroup,Long> active=getChatGroups(npc,all);
		out.append("Base group: ").append(groupName(getMyBaseChatGroup(npc,all))).append("\n\r");
		out.append("Active groups: ");
		for(int i=0;i<active.size();i++)
		{
			out.append(i>0?", ":"").append(groupName(active.getFirst(i)));
			final long until=active.getSecond(i).longValue();
			if((until>0)&&(until<Long.MAX_VALUE))
				out.append(" (switched to, for ").append(Math.max(0,(until-GameTime.now())/1000)).append(" more seconds)");
		}
		out.append("\n\r");
		// what would stop it answering at all
		final List<String> stops=new ArrayList<String>();
		if(CMSecurity.isDisabled(CMSecurity.DisFlag.MUDCHAT))
			stops.add("MudChat is disabled (DISABLE MUDCHAT)");
		if(!canActAtAll(npc))
			stops.add("it cannot act now (asleep, bound, not in the world...)");
		if(speaker.isMonster())
			stops.add("it answers players only");
		if(!CMLib.flags().canBeHeardSpeakingBy(speaker,npc))
			stops.add("it cannot hear "+speaker.Name());
		if(!CMLib.flags().canBeSeenBy(speaker,npc))
			stops.add("it cannot see "+speaker.Name());
		if(!CMLib.flags().canBeSeenBy(npc,speaker))
			stops.add(speaker.Name()+" cannot see it");
		if(lastReactedToM==speaker)
			stops.add("it answered "+speaker.Name()+" last: it answers again after someone else speaks or acts");
		final List<String> untargeted=new ArrayList<String>();
		if(talkDown>0)
			untargeted.add("another NPC spoke: it waits "+talkDown+" more ticks");
		if((speaker.location()!=null)&&(speaker.location().numPCInhabitants()>=3))
			untargeted.add("three or more players are here");
		if(speaker.location()!=npc.location())
			untargeted.add("it is not in the same room");
		if(stops.isEmpty())
			out.append("Nothing stops it answering.");
		else
			out.append("It would not answer: ").append(String.join("; ",stops)).append(".");
		if(!untargeted.isEmpty())
			out.append("  Said to the room, not to it, it would not answer either: ").append(String.join("; ",untargeted)).append(".");
		out.append("\n\r");
		// the entries the words lead to, in order, and how each matches
		final boolean combat=npc.isInCombat()||speaker.isInCombat();
		final List<ChattyEntry> entries=getChatEntries(str,npc,all);
		out.append("Entries the words lead to: ").append(entries.size()).append("\n\r");
		final String[] rest=new String[1];
		ChattyEntry matched=null;
		int n=0;
		for(final ChattyEntry entry : entries)
		{
			final ChatExpression expression=entry.expression;
			out.append("  ").append(++n).append(". [").append(groupName(entry.fromGroup)).append("] ")
			   .append(expression.type).append(" ").append(describe(expression)).append(": ");
			if(expression.type!=ChatMatchType.SAY)
				out.append("not for speech\n\r");
			else
			if(entry.combatEntry!=combat)
				out.append(entry.combatEntry?"for combat only\n\r":"not in combat\n\r");
			else
			if(matched!=null)
				out.append("not tried (an earlier entry matched)\n\r");
			else
			if(match(speaker,expression,str,rest))
			{
				matched=entry;
				out.append("MATCHES\n\r");
			}
			else
				out.append("no match\n\r");
		}
		if(matched==null)
			out.append("No entry matches: it would not answer.\n\r");
		else
		{
			int total=0;
			for(final ChattyTestResponse c : matched.responses)
				total+=c.weight;
			out.append("Replies (one is chosen by weight, of ").append(total).append("):\n\r");
			for(final ChattyTestResponse c : matched.responses)
			{
				final List<String> said=new ArrayList<String>();
				for(final String r : c.responses)
				{
					if(r.trim().length()>0)
						said.add(mcFilter(r,npc,speaker,(rest[0]==null)?"":rest[0],str,c.fromGroup));
				}
				out.append("  weight ").append(c.weight).append(": ").append(String.join(" ~ ",said))
				   .append(c.switchDB.length()>0?(" (switches to group "+c.switchDB+")"):"").append("\n\r");
			}
		}
		return out.toString();
	}

	/**
	 * Checks a chat file's text, as the parser reads it, for what it would
	 * skip or take otherwise than meant (CHATLINT): patterns that do not parse,
	 * malformed variable lines, links to groups not defined above them,
	 * replies with no pattern before them, includes that cannot be read, and
	 * patterns with no replies.
	 * @param text the file's text
	 * @return the problems, each starting with its line number
	 */
	public static List<String> lintChat(final String text)
	{
		final List<String> problems=new ArrayList<String>();
		final List<ChattyGroup> groups=new ArrayList<ChattyGroup>();
		groups.add(newChattyGroup(""));
		final String[] lines=text.replace("\r","").split("\n",-1);
		boolean inEntry=false;
		int entryLine=0;
		int entryReplies=0;
		String entryText="";
		for(int i=0;i<lines.length;i++)
		{
			final int lineNum=i+1;
			String str=lines[i].trim();
			if(str.length()==0)
				continue;
			char c=str.charAt(0);
			if((c=='*')||(c=='L')||(c=='.'))
			{
				while((str.length()>0)&&("*L.".indexOf(str.charAt(0))>=0))
					str=str.substring(1);
				if((str.length()==0)||("([{<".indexOf(str.charAt(0))<0))
					continue;
				c=str.charAt(0);
			}
			switch(c)
			{
			case '(':
			case '[':
			case '{':
			case '<':
				if(inEntry&&(entryReplies==0))
					problems.add("line "+entryLine+": the pattern "+entryText+" has no replies");
				try
				{
					parseExpression(str);
					inEntry=true;
					entryLine=lineNum;
					entryReplies=0;
					entryText=str;
				}
				catch(final CMException e)
				{
					problems.add("line "+lineNum+": the pattern does not parse ("+e.getMessage()+"): "+str);
					inEntry=false;
				}
				break;
			case '$':
				if(!((str.length()>4)
				&&(str.charAt(1)=='{')
				&&(str.endsWith("}"))
				&&((str.indexOf('=')>3)||((str.indexOf('+')>3)&&(str.indexOf('=')<0)))))
					problems.add("line "+lineNum+": a malformed variable line (${NAME=value} or ${NAME+value}): "+str);
				break;
			case '>':
				if(inEntry&&(entryReplies==0))
					problems.add("line "+entryLine+": the pattern "+entryText+" has no replies");
				inEntry=false;
				groups.add(newChattyGroup(str.substring(1).trim()));
				break;
			case '@':
				if(matchChatGroup(null,str.substring(1).trim(),groups.toArray(new ChattyGroup[0]))==null)
					problems.add("line "+lineNum+": "+str+" names no group defined above it, so it takes the first group's entries");
				break;
			case '%':
				if(Resources.getFileResource(str.substring(1).trim(),false).length()==0)
					problems.add("line "+lineNum+": the file "+str.substring(1).trim()+" cannot be read");
				break;
			case '0': case '1': case '2': case '3': case '4':
			case '5': case '6': case '7': case '8': case '9':
				if(!inEntry)
					problems.add("line "+lineNum+": a reply with no pattern before it: "+str);
				else
					entryReplies++;
				if(c=='0')
					problems.add("line "+lineNum+": a reply of weight 0 is never chosen: "+str);
				break;
			case '?':
				if(str.substring(1).trim().length()==0)
					problems.add("line "+lineNum+": a guard (?) with no condition");
				break;
			case '#':
			case '"':
				break;
			default:
				problems.add("line "+lineNum+": a line the parser skips: "+str);
				break;
			}
		}
		if(inEntry&&(entryReplies==0))
			problems.add("line "+entryLine+": the pattern "+entryText+" has no replies");
		return problems;
	}

	/** N11: the NPC, as its last message found it */
	protected MOB chatHost = null;
	/** N11: each speaker's group switches, by name, while another speaks */
	protected final Map<String,PairList<ChattyGroup,Long>> speakerGroups = new Hashtable<String,PairList<ChattyGroup,Long>>();
	/** N11: for guards, & lines and the kept switches: the game's own variables */
	protected ScriptingEngine actEngine = null;

	protected ScriptingEngine actEngine()
	{
		if(actEngine == null)
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setSavable(false);
			S.setVarScope("");
			actEngine = S;
		}
		return actEngine;
	}

	/** N11: the variable a kept switch ($@!) is saved in, on the NPC */
	protected static String keptKey(final MOB speaker)
	{
		return "MUDCHAT_GROUP_"+speaker.Name().toUpperCase().replace(' ','_');
	}

	/**
	 * N11: keeps (or, with null, forgets) the group a speaker was switched to with $@!.
	 * @param npc the NPC
	 * @param speaker the speaker
	 * @param group the group, or null
	 */
	protected void keptGroup(final MOB npc, final MOB speaker, final String group)
	{
		if((npc == null) || (speaker == null))
			return;
		actEngine().setVar(npc.Name(), keptKey(speaker), (group == null) ? "" : group);
		if(group == null)
			speakerGroups.remove(speaker.Name().toUpperCase());
	}

	/**
	 * N11: whether an entry's ?guard holds: a script condition, $t (and $n) the speaker, $i the NPC.
	 * @param entry the entry
	 * @param npc the NPC
	 * @param speaker the speaker
	 * @return true if it has none, or it holds
	 */
	protected boolean guardOk(final ChattyEntry entry, final MOB npc, final MOB speaker)
	{
		if((entry.guard == null) || (entry.guard.length() == 0))
			return true;
		final ScriptingEngine S = actEngine();
		try
		{
			String[] ev = entry.guardEval;
			if(ev == null)
			{
				ev = S.parseEval(entry.guard);
				entry.guardEval = ev;
			}
			return S.eval(new MPContext(npc, npc, speaker, speaker, null, null, "", null), new String[][] { ev }, 0);
		}
		catch(final ScriptParseException e)
		{
			Log.errOut("MudChat", "the guard ?" + entry.guard + " does not parse: " + e.getMessage());
			return false;
		}
	}

	/**
	 * N11: runs a reply: its command, or (&) its MOBPROG line, the NPC as the
	 * host and the speaker as $n.
	 * @param mob the NPC
	 * @param R the reply
	 */
	protected void respond(final MOB mob, final ChattyResponse R)
	{
		if(R.script == null)
		{
			mob.doCommand(R.parsedCommand,MUDCmdProcessor.METAFLAG_FORCED);
			return;
		}
		final ScriptingEngine S = actEngine();
		synchronized(S)
		{
			S.setScript("FUNCTION_PROG MUDCHAT_REPLY\n" + R.script + "\n~\n");
			S.callFunction(mob, "MUDCHAT_REPLY", "", ((R.speaker != null) && (!R.speaker.amDestroyed())) ? R.speaker : mob);
		}
	}

	protected void checkReactionReset(final MOB mob)
	{
		synchronized(this)
		{
			if(mob == this.lastSpeakerM)
			{
				if((this.chatEntryExpire < Long.MAX_VALUE)
				&&(this.chatEntryExpire < GameTime.now() + 60000))
					this.chatEntryExpire = this.chatEntryExpire + TALK_SWITCH_EXPIRE;
			}
			else
			{
				// N11: each speaker has their own switches: the last one's are kept, this one's put back
				if(chatHost!=null)
					getMyBaseChatGroup(chatHost,getChatGroups()); // its own name and base group first
				if(this.lastSpeakerM!=null)
				{
					final PairList<ChattyGroup,Long> was=new PairSVector<ChattyGroup,Long>();
					for(final Pair<ChattyGroup,Long> p : this.chatGroups)
						was.add(p);
					speakerGroups.put(this.lastSpeakerM.Name().toUpperCase(),was);
				}
				this.chatGroups.clear();
				this.currChatEntries = null;
				this.chatEntryExpire = Long.MAX_VALUE;
				final PairList<ChattyGroup,Long> mine=(mob==null)?null:speakerGroups.get(mob.Name().toUpperCase());
				if(mine!=null)
				{
					for(final Pair<ChattyGroup,Long> p : mine)
					{
						if(GameTime.now()<p.second.longValue())
						{
							this.chatGroups.add(p);
							if(p.second.longValue()<this.chatEntryExpire)
								this.chatEntryExpire=p.second.longValue();
						}
					}
				}
				else
				if(mob!=null)
				{
					// a switch kept ($@!) in a saved variable
					final MOB npc=chatHost;
					final String kept=(npc==null)?"":actEngine().getVar(npc.Name(),keptKey(mob));
					final ChattyGroup grp=(kept.length()>0)?matchChatGroup(npc,kept,getChatGroups()):null;
					if(grp!=null)
						this.chatGroups.add(grp,Long.valueOf(Long.MAX_VALUE));
				}
				this.lastSpeakerM = mob;
			}
		}
	}

	@Override
	public void executeMsg(final Environmental affecting, final CMMsg msg)
	{
		super.executeMsg(affecting,msg);

		if((!canActAtAll(affecting))
		||(CMSecurity.isDisabled(CMSecurity.DisFlag.MUDCHAT)))
			return;
		final MOB srcMob=msg.source();
		final MOB meMob=(MOB)affecting;
		chatHost=meMob; // N11
		if((msg.source()==meMob)
		&&(msg.sourceMinor()==CMMsg.TYP_SPEAK)
		&&(msg.othersMessage()!=null))
			lastThingSaid=CMStrings.getSayFromMessage(msg.othersMessage());
		else
		if((CMLib.flags().canBeHeardSpeakingBy(srcMob,meMob))
		&&(CMLib.flags().canBeSeenBy(srcMob,meMob))
		&&(CMLib.flags().canBeSeenBy(meMob,srcMob)))
		{
			List<Pair<ChattyTestResponse,String>> myResponses=null;
			final String rest[]=new String[1];
			final boolean combat=((meMob.isInCombat()))||(srcMob.isInCombat());

			String str=null;
			if((msg.targetMinor()==CMMsg.TYP_SPEAK)
			&&(!srcMob.isMonster())
			&&(msg.amITarget(meMob)
			   ||((msg.target()==null)
				  &&(srcMob.location()==meMob.location())
				  &&(talkDown<=0)
				  &&(srcMob.location().numPCInhabitants()<3)))
			&&(lastReactedToM!=msg.source())
			&&(msg.sourceMessage()!=null)
			&&(msg.targetMessage()!=null)
			&&((str=CMStrings.getSayFromMessage(msg.sourceMessage()))!=null))
			{
				checkReactionReset(msg.source());
				str=CMLib.english().stripEnglishPunctuation(str).toLowerCase().trim();
				for(final ChattyEntry entry : getChatEntries(str, meMob, getChatGroups()))
				{
					final ChatExpression expression=entry.expression;
					if((expression.type==ChatMatchType.SAY)
					&&(entry.combatEntry==combat))
					{
						if(match(srcMob,expression,str,rest)&&guardOk(entry,meMob,srcMob))
						{
							myResponses=new ArrayList<Pair<ChattyTestResponse,String>>();
							for(final ChattyTestResponse c : entry.responses)
								myResponses.add(new Pair<ChattyTestResponse,String>(c,rest[0]));
							break;
						}
					}
				}
			}
			else // dont interrupt another mob
			if((msg.sourceMinor()==CMMsg.TYP_SPEAK)
			&&(srcMob.isMonster()))  // this is another mob (not me) talking
			   talkDown=TALK_WAIT_DELAY;
			else // dont parse unless we are done waiting
			if((!srcMob.isMonster())
			&&(msg.sourceMinor()!=CMMsg.TYP_SPEAK)
			&&(talkDown<=0)
			&&(lastReactedToM!=msg.source()))
			{
				str=null;
				ChatMatchType matchType = ChatMatchType.EMOTE;
				if((msg.amITarget(meMob)
				&&(msg.targetMessage()!=null)))
				{
					str=CMLib.english().stripEnglishPunctuation(msg.targetMessage().toLowerCase()).trim();
					matchType = ChatMatchType.TEMOTE;
				}
				else
				if(msg.othersMessage()!=null)
				{
					matchType = ChatMatchType.EMOTE;
					str=CMLib.english().stripEnglishPunctuation(msg.othersMessage().toLowerCase()).trim();
				}
				if(str!=null)
				{
					checkReactionReset(msg.source());
					for(final ChattyEntry entry : getChatEntries(str, meMob, getChatGroups()))
					{
						final ChatExpression expression=entry.expression;
						if((expression.type==matchType)
						&&(entry.combatEntry==combat))
						{
							if(match(srcMob,expression,str,rest)&&guardOk(entry,meMob,srcMob))
							{
								myResponses=new ArrayList<Pair<ChattyTestResponse,String>>();
								for(final ChattyTestResponse c : entry.responses)
									myResponses.add(new Pair<ChattyTestResponse,String>(c,rest[0]));
								break;
							}
						}
					}
				}
			}

			if(myResponses!=null)
			{
				lastReactedToM=msg.source();
				lastRespondedToM=msg.source();
				queResponse(myResponses,meMob,srcMob,str);
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		super.tick(ticking,tickID);
		if((tickID==Tickable.TICKID_MOB)
		&&(ticking instanceof MOB)
		&&(!CMSecurity.isDisabled(CMSecurity.DisFlag.MUDCHAT)))
		{
			if(tickDown>0)
			{
				--tickDown;
				return true;
			}
			if(!canActAtAll(ticking))
			{
				responseQue.clear();
				return true;
			}
			if(talkDown>0)
				talkDown--;
			final PairList<ChattyGroup,Long> groups=getChatGroups((MOB)ticking, getChatGroups());
			for(final Iterator<ChattyGroup> i = groups.firstIterator(); i.hasNext();)
			{
				final ChattyGroup group = i.next();
				if(group.tickies.length>0)
				{
					final boolean combat = ((MOB)ticking).isInCombat();
					List<Pair<ChattyTestResponse,String>> myResponses=null;
					for(final ChattyEntry entry : group.tickies)
					{
						if((entry.combatEntry==combat)
						&&((!entry.llm)||(this.isLLMSupported()))
						&&((!entry.antillm)||(!this.isLLMSupported()))
						&&(this.match((MOB)ticking, entry.expression, CMLib.dice().rollPercentage()))
						&&guardOk(entry,(MOB)ticking,(MOB)ticking))
						{
							myResponses=new ArrayList<Pair<ChattyTestResponse,String>>();
							for(final ChattyTestResponse c : entry.responses)
								myResponses.add(new Pair<ChattyTestResponse,String>(c,""));
							break; // the first match, as for speech
						}
					}
					if(myResponses!=null)
						queResponse(myResponses,(MOB)ticking,(MOB)ticking,null);
				}
			}
			if(responseQue.size()==0)
				lastReactedToM=null;
			else
			{
				for(final Iterator<ChattyResponse> riter= responseQue.descendingIterator();riter.hasNext();)
				{
					final ChattyResponse R = riter.next();
					R.delay--;
					if(R.delay<=0)
					{
						responseQue.remove(R);
						if(R.combatFlag == ((MOB)ticking).isInCombat())
						{
							respond((MOB)ticking,R);
							lastReactedToM=null;
							// you've done one, so get out before doing another!
							break;
						}
					}
				}
			}
		}
		return true;
	}
}
