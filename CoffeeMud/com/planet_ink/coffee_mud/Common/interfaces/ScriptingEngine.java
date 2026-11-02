package com.planet_ink.coffee_mud.Common.interfaces;
import com.planet_ink.coffee_mud.core.exceptions.ScriptParseException;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.Scriptable;
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
   Copyright 2005-2025 Bo Zimmerman

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
 * The interface for the main CoffeeMud scripting engine, which implements
 * a scripting engine descended from the old mud codebases of the 90's
 * usually called MOBPROG.  Its main features include easy to understand
 * event-oriented triggers, making all mud commands implicit scripting
 * commands.  It also includes methods for embedding javascript.
 *
 * @see com.planet_ink.coffee_mud.Behaviors.Scriptable
 */
public interface ScriptingEngine extends CMCommon, Tickable, MsgListener
{
	/**
	 * Executes a script in response to an event
	 * The scripts are formatted as a SubScript class.  Each
	 * row consists of the String command, and a parsed String[]
	 * array as dimension 2.
	 *
	 * The script to execute must be pushed onto the context
	 * before calling this.
	 *
	 * @see ScriptingEngine.MPContext
	 *
	 * @param ctx the script event context scope
	 * @return N/A
	 */
	public String execute(final MPContext ctx);

	/**
	 * Uses this scripting engines variable parsing system to replace
	 * any script variables $XXXX with their script determined values.
	 * This is a powerful mechanism for getting at the script functions
	 * in order to access stat data about specific objects, do math, etc.
	 *
	 * @see ScriptingEngine.MPContext
	 *
	 * @param ctx the script event context scope
	 * @param varifyable the string to parse
	 *
	 * @return N/A
	 */
	public String varify(MPContext ctx, String varifyable);

	/**
	 * Forces any queued event responses to be immediately
	 * executed.
	 * @see ScriptingEngine#SPECIAL_NUM_OBJECTS
	 * @param objects null, or a twelve (12) object array for local variables
	 */
	public void dequeResponses(Object[] objects);

	/**
	 * Creates a mob from the Tickable object sent, possibly saving it
	 * locally to this object for use later.  If the object is a mob,
	 * it returns the mob.  Otherwise, it makes a fake one.
	 * @param ticking the scripted object to make a fake mob out of
	 * @return a mob from a tickable
	 */
	public MOB getMakeMOB(Tickable ticking);

	/**
	 * Receives a string for evaluation by the eval function, and stores
	 * it as the first element in the given 2 dimensional string array.
	 * @param evaluable the eval expression
	 * @return EVAL the 1 dimensional array to hold the compiled eval
	 * @throws ScriptParseException a parse error
	 */
	public String[] parseEval(String evaluable) throws ScriptParseException;

	/**
	 * Evaluates a scripting function.  Is called by the execute command
	 * to resolve IF, WHILE, and similar expressions that utilize the MOBPROG
	 * functions.  The expressions are passed in as a String array stored
	 * in a single string array entry (for replacement) in element 0.
	 *
	 * @see ScriptingEngine.MPContext
	 *
	 * @param ctx the script event context scope
	 * @param eval the pre-parsed expression
	 * @param startEval while line to start evaluating on.
	 * @return true if the expression is true, false otherwise.
	 */
	public boolean eval(MPContext ctx, String[][] eval, int startEval);

	/**
	 * Evaluates one of the boolean functions as a string
	 * variable expression, which gives different and
	 * informative results.  See the Green Table in the
	 * Scripting Guide.
	 *
	 * @see ScriptingEngine.MPContext
	 *
	 * @param ctx the script event context scope
	 * @param evaluable the function expression
	 * @return the results of the function expression
	 */
	public String functify(MPContext ctx, String evaluable);

	/**
	 * Called a func with the given name, sending the given parms, and returning its
	 * return value, if any, or null.  Does the same thing as MPCAlLFUNC
	 *
	 * @see ScriptingEngine.MPContext
	 *
	 * @param named the name of the FUNCTION_PROG to call
	 * @param parms parameters to send as $g
	 * @param ctx the script event context scope
	 *
	 * @return the return code, or null if function isn't found
	 */
	public String callFunc(final String named, final String parms, MPContext ctx);


	/**
	 * Returns whether the script has a function with the given name.
	 * @param named the FUNCTION_PROG to look for
	 * @return true if it is found, false otherwise
	 */
	public boolean isFunc(final String named);

	/**
	 * Calling this method forces this script to look for a trigger
	 * dealing with the end of a quest (QUEST_TIME_PROG * -2).
	 * @param hostObj the scripted object
	 * @param mob a mob representation of the host object
	 * @param quest the name of the quest being ended
	 * @return true if a quest ending trigger was found and run
	 */
	public boolean endQuest(PhysicalAgent hostObj, MOB mob, String quest);

	/**
	 * Calling this method forces this script to look for a trigger
	 * dealing with the end of a quest step (QUEST_TIME_PROG * -1).
	 * @param hostObj the scripted object
	 * @param mob a mob representation of the host object
	 * @param quest the name of the quest being ended
	 * @return true if a quest stepping trigger was found and run
	 */
	public boolean stepQuest(PhysicalAgent hostObj, MOB mob, String quest);

	/**
	 * Returns the script or load command(s).
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setScript(String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#externalFiles()
	 * @return the script or load command(s)
	 */
	public String getScript();

	/**
	 * Returns the hey used to cache the script or load commands in here.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getScript()
	 * @return the key to the script or load command(s)
	 */
	public String getScriptResourceKey();

	/**
	 * Sets the script or load command(s).
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getScript()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#externalFiles()
	 * @param newParms the script or load command(s)
	 */
	public void setScript(String newParms);

	/**
	 * If the script is a load command, this will return the
	 * list of loaded script files referenced by the load command
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getScript()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setScript(String)
	 * @return a list of loaded script files.
	 */
	public List<String> externalFiles();

	/**
	 * If this script is associated with a particular quest, this
	 * method is called to register that quest name or object.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#defaultQuestName()
	 * @param questName the quest associated with this script
	 */
	public void registerDefaultQuest(Object questName);

	/**
	 * If this script is associated with a particular quest, this
	 * method is called to return that quest name.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#registerDefaultQuest(Object)
	 * @return the quest associated with this script, if any
	 */
	public String defaultQuestName();

	/**
	 * Sets the scope of any variables defined within the script.  Although the scope
	 * is somewhat modified if this script is quest-bound, it is usually honored.
	 * Valid scopes include: "" for global, "*" for local, or a shared named scope.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVarScope()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVar(String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getLocalVarXML()
	 * @param scope the scope of variables
	 */
	public void setVarScope(String scope);

	/**
	 * Returns the scope of any variables defined within the script.  Although the scope
	 * is somewhat modified if this script is quest-bound, it is usually honored.
	 * Valid scopes include: "" for global, "*" for local, or a shared named scope.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVarScope(String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVar(String, String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setLocalVarXML(String)
	 * @return the scope of variables
	 */
	public String getVarScope();

	/**
	 * If the variable scope of this script is local, this will return all the variables
	 * and values defined as an xml document for easy storage.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVarScope(String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVar(String, String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setLocalVarXML(String)
	 * @return the local variable values as xml
	 */
	public String getLocalVarXML();

	/**
	 * If the variable scope of this script is local, this will set all the variables
	 * and values defined from a passed in xml document.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVarScope()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVar(String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getLocalVarXML()
	 * @param xml the local variable values as xml
	 */
	public void setLocalVarXML(String xml);

	/**
	 * Returns whether this script is a temporary attributed of the scripted object,
	 * or a permanent on that should be saved with the object.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setSavable(boolean)
	 * @return whether this script is a saveable attribute of the scripted object
	 */
	public boolean isSavable();

	/**
	 * Sets whether this script is a temporary attributed of the scripted object,
	 * or a permanent on that should be saved with the object.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#isSavable()
	 * @param truefalse true if this script is a saveable attribute of the scripted object
	 */
	public void setSavable(boolean truefalse);

	/**
	 * Returns the value of one of the internal variables, determined by the scope
	 * of the script, the context of the variable, and the name of the variable.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVarScope(String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVarScope()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVar(String, String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#isVar(String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getLocalVarXML()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setLocalVarXML(String)
	 * @param context the context of the variable, usually a mob or object name
	 * @param variable the name of the variable
	 * @return the value of the variable
	 */
	public String getVar(String context, String variable);

	/**
	 * Returns whether an internal variables, determined by the scope
	 * of the script, the context of the variable, and the name of the variable, is defined.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVarScope(String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVarScope()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVar(String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVar(String, String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getLocalVarXML()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setLocalVarXML(String)
	 * @param context the context of the variable, usually a mob or object name
	 * @param variable the name of the variable
	 * @return true if the variable has been set in the past, false otherwise
	 */
	public boolean isVar(String context, String variable);

	/**
	 * Sets the value of one of the internal variables, determined by the scope
	 * of the script, the context of the variable, and the name of the variable.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setVarScope(String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVarScope()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getVar(String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#isVar(String, String)
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getLocalVarXML()
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setLocalVarXML(String)
	 * @param context the context of the variable, usually a mob or object name
	 * @param variable the name of the variable
	 * @param value the value of the variable
	 */
	public void setVar(String context, String variable, String value);

	/**
	 * If the script contains any javascript, calling this method will
	 * pre-approve the scripts so that they bypass any approval
	 * requirements.
	 */
	public void preApproveScripts();

	/**
	 * The engine that runs the scripts: this one, or the one a wrapper (the
	 * Scriptable behaviour) hands its work to.  SCRIPT reaches the triggers
	 * through it.
	 * @return the engine
	 */
	public default ScriptingEngine baseEngine()
	{
		return this;
	}

	/**
	 * Wrapper class for a standard parsed line of script,
	 * which includes the full line of text, the parsed line,
	 * and an optional helper object of any sort
	 *
	 * @author Bo Zimmerman
	 *
	 */
	public static class ScriptLn extends Triad<String,String[],Object>
	{
		private static final long serialVersionUID = -3327511702060317043L;

		public ScriptLn(final String frst, final String[] scnd, final Object thrd)
		{
			super(frst.trim(), scnd, thrd);
		}

	}

	/**
	 * Wrapper class for a subscript, which consists of a trigger
	 * and its requisite code.
	 *
	 * @author Bo Zimmerman
	 *
	 */
	public static abstract class SubScript extends ArrayList<ScriptLn>
	{
		private static final long serialVersionUID = 6156845948200028480L;

		/**
		 * Returns the trigger code for this entire script
		 *
		 * @return the trigger code
		 */
		public abstract int getTriggerCode();

		/**
		 * Returns the trigger args for this entire script
		 *
		 * @return the trigger args
		 */
		public abstract String[] getTriggerArgs();

		/**
		 * Returns the trigger line for this entire script
		 * The trigger word itself should be normalized
		 * to uppercase.
		 *
		 * @return the trigger line
		 */
		public abstract String getTriggerLine();

		/**
		 * The cleaned bits of the trigger, uppercased.
		 * @return the cleaned bits of the trigger, uppercased.
		 */
		public abstract String[] getTriggerBits();

		/**
		 * Sets a special flags on this script, to let the
		 * executor know how to handle it.
		 * @param flag the flag to set
		 */
		public abstract void setFlag(String flag);

		/**
		 * Checks a special flags on this script, to let the
		 * executor know how to handle it.
		 * @param flag the flag to look for
		 * @return true if the flag was set.
		 */
		public abstract boolean isFlagSet(String flag);
	}

	/** The number of local variables associated with an execution of a script */
	public static final int SPECIAL_NUM_OBJECTS=12;
	/** The index into the local variables array for a random pc */
	public static final int SPECIAL_RANDPC=10;
	/** The index into the local variables array for a random pc or mob  */
	public static final int SPECIAL_RANDANYONE=11;
	/** The index into the local variables array for a random items shop price from shophas  */
	public static final int SPECIAL_9SHOPHASPRICE=9;

	/** String list of all valid trigger keywords */
	public static final String[] progs=
	{
		"GREET_PROG", //1
		"ALL_GREET_PROG", //2
		"SPEECH_PROG", //3
		"GIVE_PROG", //4
		"RAND_PROG", //5
		"ONCE_PROG", //6
		"FIGHT_PROG", //7
		"ENTRY_PROG", //8
		"EXIT_PROG", //9
		"DEATH_PROG", //10
		"HITPRCNT_PROG", //11
		"MASK_PROG", //12
		"QUEST_TIME_PROG", // 13
		"TIME_PROG", // 14
		"DAY_PROG", // 15
		"DELAY_PROG", // 16
		"FUNCTION_PROG", // 17
		"ACT_PROG", // 18
		"BRIBE_PROG", // 19
		"GET_PROG", // 20
		"PUT_PROG", // 21
		"DROP_PROG", // 22
		"WEAR_PROG", // 23
		"REMOVE_PROG", // 24
		"CONSUME_PROG", // 25
		"DAMAGE_PROG", // 26
		"BUY_PROG", // 27
		"SELL_PROG", // 28
		"LOGIN_PROG", // 29
		"LOGOFF_PROG", // 30
		"REGMASK_PROG", // 31
		"LEVEL_PROG", // 32
		"CHANNEL_PROG", // 33
		"OPEN_PROG", // 34
		"CLOSE_PROG", // 35
		"LOCK_PROG", // 36
		"UNLOCK_PROG", // 37
		"SOCIAL_PROG", // 38
		"LOOK_PROG", // 39
		"LLOOK_PROG", // 40
		"EXECMSG_PROG", // 41
		"CNCLMSG_PROG", // 42
		"IMASK_PROG", // 43
		"KILL_PROG", //44
		"ARRIVE_PROG", //45
		"PUTTING_PROG", // 46
		"SPEAK_PROG", // 47
		"GIVING_PROG", // 48
		"CAST_PROG", //49
		"CASTING_PROG", //50
		"CMDFAIL_PROG", //51
		"GROUP_GREET_PROG", //52
		"DROPPING_PROG", // 53
		"GETTING_PROG", // 54
		"RIDE_PROG", // 55
		"RIDING_PROG", // 56
		"WEARING_PROG", // 57
		XMLLibrary.FILE_XML_BOUNDARY.toUpperCase().substring(0,XMLLibrary.FILE_XML_BOUNDARY.indexOf(' ')), // 58
		"DISMOUNT_PROG", // 59
		"REMOVING_PROG", // 60
		"SKILL_PROG", // 61
		"DISCOVER_PROG", // 62
		"WEATHER_PROG", // 63
		"MOON_PROG", // 64
		"QUEST_PROG", // 65
		"GAME_PROG", // 66
		"TURN_PROG", // 67
		"VERB_PROG", // 68
		"PARTY_PROG", // 69
		"SEASON_PROG", // 70
		"MONTH_PROG", // 71
		"YEAR_PROG", // 72
		"WEEKDAY_PROG", // 73
		"CLAN_PROG", // 74
		"CRAFT_PROG", // 75
		"RECIPE_PROG", // 76
		"FACTION_PROG", // 77
		"COMBAT_PROG", // 78
		"AREA_ENTER_PROG", // 79
		"AREA_LEAVE_PROG", // 80
		"BOARD_PROG", // 81
		"DISEMBARK_PROG", // 82
		"SAIL_PROG", // 83
		"VESSEL_ARRIVE_PROG", // 84
		"VESSEL_GREET_PROG", // 85
		"DOCK_PROG", // 86
		"READ_PROG", // 87
		"GENERATED_PROG", // 88
		"FIRSTGREET_PROG", // 89
		"REGREET_PROG", // 90
		"EVERY_PROG", // 91
		"VAR_PROG", // 92
		"AFFECT_PROG", // 93
		"DESTROY_PROG", // 94
		"DECAY_PROG", // 95
		"ACHIEVE_PROG", // 96
		"SIGNAL_PROG", // 97
		"QUESTFAIL_PROG", // 98
	};

	/** String list of all valid mobprog functions for logical expressions or string functions */
	public static final String[] funcs=
	{
		"RAND", //1
		"HAS", //2
		"WORN", //3
		"ISNPC", //4
		"ISPC", //5
		"ISGOOD", //6
		"ISNAME", //7
		"ISEVIL", //8
		"ISNEUTRAL", //9
		"ISFIGHT", //10
		"ISIMMORT", //11
		"ISCHARMED", //12
		"STAT", //13
		"AFFECTED", //14
		"ISFOLLOW", //15
		"HITPRCNT", //16
		"INROOM", //17
		"SEX", //18
		"POSITION", //19
		"LEVEL", //20
		"CLASS", //21
		"BASECLASS", //22
		"RACE", //23
		"RACECAT", //24
		"GOLDAMT", //25
		"OBJTYPE", // 26
		"VAR", // 27
		"QUESTWINNER", //28
		"QUESTMOB", // 29
		"QUESTOBJ", // 30
		"ISQUESTMOBALIVE", // 31
		"NUMMOBSINAREA", // 32
		"NUMMOBS", // 33
		"NUMRACESINAREA", // 34
		"NUMRACES", // 35
		"ISHERE", // 36
		"INLOCALE", // 37
		"ISTIME", // 38
		"ISDAY", // 39
		"NUMBER", // 40
		"EVAL", // 41
		"RANDNUM", // 42
		"ROOMMOB", // 43
		"ROOMITEM", // 44
		"NUMMOBSROOM", // 45
		"NUMITEMSROOM", // 46
		"MOBITEM", // 47
		"NUMITEMSMOB", // 48
		"HASTATTOO", // 49
		"ISSEASON", // 50
		"ISWEATHER", // 51
		"GSTAT", // 52
		"INCONTAINER", //53
		"ISALIVE", // 54
		"ISPKILL", // 55
		"NAME", // 56
		"ISMOON", // 57
		"ISABLE", // 58
		"ISOPEN", // 59
		"ISLOCKED", // 60
		"STRIN", // 61
		"CALLFUNC", // 62
		"NUMPCSROOM", // 63
		"DEITY", // 64
		"CLAN", // 65
		"CLANRANK", // 66
		"HASTITLE", // 67
		"CLANDATA", // 68
		"ISBEHAVE", // 69
		"IPADDRESS", // 70
		"RAND0NUM", // 71
		"FACTION", //72
		"ISSERVANT", // 73
		"HASNUM", // 74
		"CURRENCY", // 75
		"VALUE", // 76
		"EXPLORED", // 77
		"EXP", // 78
		"NUMPCSAREA", // 79
		"QUESTPOINTS", // 80
		"TRAINS", // 81
		"PRACS", // 82
		"QVAR", // 83
		"MATH", // 84
		"ISLIKE", //85
		"STRCONTAINS", //86
		"ISBIRTHDAY", //87
		"MOOD", //88
		"ISRECALL", //89
		"INAREA", //90
		"DATETIME", //91
		"ISODD", // 92
		"QUESTSCRIPTED", //93
		"QUESTROOM", // 94
		"ISSPEAKING", // 95
		"ISCONTENT", // 96
		"WORNON", // 97
		"CLANQUALIFIES", // 98
		"HASACCTATTOO", // 99
		"SHOPITEM", // 100
		"NUMITEMSSHOP", // 101
		"SHOPHAS", // 102
		"ISMONTH", // 103
		"ISYEAR", // 104
		"ISRLHOUR", // 105
		"ISRLDAY", // 106
		"ISRLMONTH", // 107
		"ISRLYEAR", // 108
		"HASTATTOOTIME", // 109
		"ISHOUR", // 110
		"ITEMCOUNT", // 111
		"CANSEE", // 112
		"CANHEAR", // 113
		"QUESTAREA", // 114
		"EXPERTISE", // 115
		"ROOMPC", // 116
		"AREAPC", //117
		"HASTAG", //118
		"FACTIONVALUE", //119
		"FACTIONBAND", //120
		"VARNAMES", //121
		"ISDEV", //122
		"ISBUILDER", //123
		"ISTURNBASED", //124
		"ISPAUSED", //125
		"SAVESLOT", //126
		"GAMETIME", //127
		"TURNS", //128
		"PARTYSIZE", //129
		"INPARTY", //130
		"PARTYLEADER", //131
		"PARTY", //132
		"ISWEEKDAY", //133
		"GENVAR", //134
		"MEMORY", //135
		"LISTJOIN", //136
		"LISTHAS", //137
		"LISTCOUNT", //138
		"LISTGET", //139
		"LISTFIND", //140
		"LISTPICK", //141
		"ELAPSED", //142
		"WEEKDAY", //143
		"TICKS", //144
		"UPPER", //145
		"LOWER", //146
		"CAP", //147
		"LEN", //148
		"SUBSTR", //149
		"REPLACE", //150
		"TRIM", //151
		"PADRIGHT", //152
		"ANAME", //153
		"PLURAL", //154
		"STRSTARTS", //155
		"STRENDS", //156
		"STRPOS", //157
		"DICE", //158
		"PICK", //159
		"WPICK", //160
		"ROLLNORMAL", //161
		"CLAMP", //162
		"ABS", //163
		"NUMTAGGED", //164
		"TAGGED", //165
		"TAGS", //166
		"CATALOG", //167
		"CATALOGCOUNT", //168
		"STATE", //169
		"MSG", //170
		"ISPLAYED", //171
		"ARG", //172
		"JS", //173
		"JS$", //174
		"QOBJECTIVE", //175
		"QUESTSTATUS", //176
		"QUESTOUTCOME", //177
	};

	/** String list of all valid mobprog commands */
	public static final String[] methods=
	{
		"MPASOUND", //1
		"MPECHO", //2
		"MPSLAY", //3
		"MPJUNK", //4
		"MPMLOAD", //5
		"MPOLOAD", //6
		"MPECHOAT", //7
		"MPECHOAROUND", //8
		"MPCAST", //9
		"MPKILL", //10
		"MPEXP", //11
		"MPPURGE", //12
		"MPUNAFFECT", //13
		"MPGOTO", //14
		"MPAT", //15
		"MPSET", //16
		"MPTRANSFER", //17
		"MPFORCE", //18
		"IF", //19
		"MPSETVAR", //20
		"MPENDQUEST",//21
		"MPQUESTWIN", //22
		"MPSTARTQUEST", //23
		"MPCALLFUNC", // 24
		"MPBEACON", // 25
		"MPALARM", // 26
		"MPWHILE", // 27
		"MPDAMAGE", // 28
		"MPTRACKTO", // 29
		"MPAFFECT", // 30
		"MPBEHAVE", // 31
		"MPUNBEHAVE",  //32
		"MPTATTOO", // 33
		"BREAK", // 34
		"MPGSET", // 35
		"MPSAVEVAR", // 36
		"MPENABLE", // 37
		"MPDISABLE", // 38
		"MPLOADVAR", // 39
		"MPM2I2M", // 40
		"MPOLOADROOM", // 41
		"MPHIDE", // 42
		"MPUNHIDE", // 43
		"MPOPEN", // 44
		"MPCLOSE", // 45
		"MPLOCK", // 46
		"MPUNLOCK", // 47
		"RETURN", // 48
		"MPTITLE", // 49
		"BREAK", // 50
		"MPSETCLANDATA", // 51
		"MPPLAYERCLASS", // 52
		"MPWALKTO", // 53
		"MPFACTION", //54
		"MPNOTRIGGER", // 55
		"MPSTOP", // 56
		"<SCRIPT>", // 57
		"MPRESET", // 58
		"MPQUESTPOINTS", // 59
		"MPTRAINS", // 60
		"MPPRACS", // 61
		"FOR", // 62
		"MPARGSET", // 63
		"MPLOADQUESTOBJ", // 64
		"MPQSET", // 65
		"MPLOG", // 66
		"MPCHANNEL", // 67
		"MPUNLOADSCRIPT", //68
		"MPSTEPQUEST", //69
		"SWITCH", //70
		"MPREJUV", //71
		"MPSCRIPT", //72
		"MPSETINTERNAL", // 73
		"MPPROMPT", // 74
		"MPCONFIRM", // 75
		"MPCHOOSE", // 76
		"MPMONEY", // 77
		"MPHEAL", // 78
		"MPPOSSESS", // 79
		"MPSPEAK", // 80
		"MPSETCLAN", // 81
		"MPRLOAD", // 82
		"MPACCTATTOO", // 83
		"MPOLOADSHOP", //84
		"MPMLOADSHOP", //85
		"MPRPEXP", //86
		"MEA", //87
		"MER", //88
		"MPCASTEXT", //89
		"MPLINK", //90
		"MPUNLINK", //91
		"MPPUT", //92
		"ENDIF", //93 JUST for catching errors...
		"ENDSWITCH", //94 JUST for catching errors...
		"NEXT", //95 JUST for catching errors...
		"CASE", //96 JUST for catching errors...
		"DEFAULT", //97 JUST for catching errors...
		"MPHIT", //98
		"MPACHIEVE", //99
		"MPACCUSE", //100
		"MPLLM", // 101
		"MPDEBUG", // 102
		"MPLIST", // 103
		"MPTAG", // 104
		"MPSTATE", // 105
		"MPMSG", // 106
		"MPSIGNAL", // 107
		"MPWAIT", // 108
		"WHILE", // 109
		"ENDWHILE", // 110
		"CONTINUE", // 111
		"MPLOCAL", // 112
		"ELSEIF", // 113
		"MPJSFILE", // 114
		"MPQOBJECTIVE", // 115
		"MPQJOURNAL", // 116
		"MPQUESTFAIL", // 117
	};

	/**
	 * Class to hold the complete script event
	 * context, with all its event scope variables.
	 *
	 * @author Bo Zimmerman
	 *
	 */
	public static class MPContext implements Cloneable
	{
		public PhysicalAgent scripted;
		public MOB monster;
		public MOB source;
		public Environmental target;
		public Item primaryItem;
		public Item secondaryItem;
		public String msg;
		public Object[] tmp;
		public SubScript script = null;
		public int line = 1;
		public MPContext parent = null;
		/** SC5: the message a CNCLMSG_PROG or EXECMSG_PROG reacts to (for EXECMSG_PROG, a copy) */
		public CMMsg cmsg = null;
		/** SC7: a body FOR or MPWHILE runs again and again, which MPWAIT cannot pause */
		public boolean repeats = false;
		/** SC17: the named locals (MPLOCAL, a function's parameters), $(name), shared by the blocks of one run */
		public java.util.Map<String,String> locals = new java.util.TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);

		/**
		 * MPContext constructor
		 *
		 * @param scripted the object that is scripted
		 * @param monster a mob representation of the scripted object
		 * @param source the source of the event
		 * @param target the target of the event
		 * @param primaryItem an item involved in the event
		 * @param secondaryItem a second item involved in the event
		 * @param msg a string message associated with the event
		 * @param tmp null, or initialized local variables
		 */
		public MPContext(final PhysicalAgent scripted, final MOB monster, final MOB source,
				final Environmental target, final Item primaryItem, final Item secondaryItem,
				final String msg, final Object[] tmp)
		{
			this.scripted=scripted;
			this.monster=monster;
			this.source=source;
			this.target=target;
			this.primaryItem=primaryItem;
			this.secondaryItem=secondaryItem;
			this.msg=msg;
			if((tmp == null)||(tmp.length!=ScriptingEngine.SPECIAL_NUM_OBJECTS))
			{
				this.tmp = new Object[ScriptingEngine.SPECIAL_NUM_OBJECTS];
				if(tmp != null)
				{
					for(int i=0;i<tmp.length && i<ScriptingEngine.SPECIAL_NUM_OBJECTS;i++)
						this.tmp[i] = tmp[i];
				}
			}
			else
				this.tmp = tmp;
		}

		public MPContext(final MPContext old)
		{
			this(old.scripted, old.monster, old.source, old.target, old.primaryItem, old.secondaryItem, old.msg, old.tmp);
			this.cmsg = old.cmsg;
			this.locals.putAll(old.locals);
		}

		public MPContext push(final SubScript script)
		{
			if(this.script==null)
			{
				this.script = script;
				return this;
			}
			MPContext copy = this;
			try { copy = (MPContext)super.clone(); } catch (final CloneNotSupportedException e) {}
			copy.script = script;
			copy.line = 1;
			return copy;
		}

		public MPContext pop()
		{
			if(parent == null)
				return this;
			return parent;
		}
	}

	/**
	 * Tells the engine the host its scripts are on, when they are put on it.
	 * Scripts that react to world-wide events, such as logins and level
	 * changes, need to know their host before any such event happens.
	 * @param host the object the scripts are on
	 */
	public default void registerHost(final PhysicalAgent host)
	{
	}

	/**
	 * An event that is not a message, such as a skill learned or a room first
	 * visited (core.ScriptEvents), for this engine's scripts with its trigger:
	 * each one's trigger line, split by bits as the engine splits trigger
	 * lines, that the test accepts is queued to run, as a message's trigger
	 * is.
	 * @param host the object the scripts are on
	 * @param triggerCode the trigger, its place in progs plus one
	 * @param source who it happened to: $n
	 * @param target $t, or null for the host
	 * @param item $o, or null for the host if it is an item
	 * @param msg $g
	 * @param args $0 to $8, or null
	 * @param bits how the trigger line is split: c a word, C upper case, r the rest
	 * @param test which trigger lines the event fits
	 */
	public default void eventTrigger(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test)
	{
	}

	/**
	 * As {@link #eventTrigger}, the scripts that fit queued to run after so
	 * many ticks (SC6's delayed signals).
	 * @param host the object the scripts are on
	 * @param triggerCode the trigger
	 * @param source $n
	 * @param target $t, or null for the host
	 * @param item $o, or null
	 * @param msg $g
	 * @param args $0 to $8, or null
	 * @param bits how the trigger line is split
	 * @param test which trigger lines the event fits
	 * @param ticks how many ticks until they run, 1 or more
	 */
	public default void eventTrigger(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test, final int ticks)
	{
		eventTrigger(host, triggerCode, source, target, item, msg, args, bits, test);
	}

	/**
	 * SC6: calls a FUNCTION_PROG of this engine's scripts on a host, as
	 * MPCALLFUNC does, for another host's script (MPCALLFUNC target func).
	 * @param host the object the scripts are on
	 * @param function the function's name
	 * @param args its arguments, $g
	 * @param source $n
	 * @return what it returned, "" if nothing, or null if there is no such function
	 */
	public default String callFunction(final PhysicalAgent host, final String function, final String args, final MOB source)
	{
		return null;
	}

	/**
	 * As {@link #eventTrigger}, but the scripts that fit run at once, not
	 * queued, and one that returns CANCEL (RETURN CANCEL) refuses the event,
	 * as CNCLMSG_PROG refuses a message: GAME_PROG SAVE (ST6).
	 * @param host the object the scripts are on
	 * @param triggerCode the trigger
	 * @param source $n
	 * @param target $t, or null for the host
	 * @param item $o, or null
	 * @param msg $g
	 * @param args $0 to $8, or null
	 * @param bits how the trigger line is split
	 * @param test which trigger lines the event fits
	 * @return false if a script refused it
	 */
	public default boolean eventCheck(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test)
	{
		return true;
	}

	/**
	 * As {@link #eventTrigger}, but the first script that fits runs at once,
	 * and what it returns is returned: VERB_PROG (ST7), where CONTINUE hands
	 * the command back.
	 * @param host the object the scripts are on
	 * @param triggerCode the trigger
	 * @param source $n
	 * @param target $t, or null for the host
	 * @param item $o, or null
	 * @param msg $g
	 * @param args $0 to $8, or null
	 * @param bits how the trigger line is split
	 * @param test which trigger lines the event fits
	 * @return null if no script fit, or what the one that ran returned ("" for nothing)
	 */
	public default String eventRun(final PhysicalAgent host, final int triggerCode, final MOB source, final Environmental target, final Item item, final String msg, final Object[] args, final String bits, final java.util.function.Predicate<String[]> test)
	{
		return null;
	}

	/**
	 * Returns what this engine has done that its script alone cannot tell,
	 * for a saved game: which ONCE_PROGs and QUEST_TIME_PROGs have already
	 * run.  It is tied to the script as it is now, so that a changed script
	 * does not take back state that no longer fits it.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#setStateXML(String)
	 * @return the state as XML, or "" if there is none
	 */
	public default String getStateXML()
	{
		return "";
	}

	/**
	 * Puts back the state written by {@link #getStateXML()}, once the script
	 * is read, unless the script has changed since.
	 * @see com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine#getStateXML()
	 * @param xml the state as XML
	 */
	public default void setStateXML(final String xml)
	{
	}
}
