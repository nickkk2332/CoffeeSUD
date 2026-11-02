package com.planet_ink.coffee_mud.Common;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
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
public class DefaultClimate implements Climate
{
	@Override
	public String ID()
	{
		return "DefaultClimate";
	}

	@Override
	public String name()
	{
		return "Climate Object";
	}

	protected int	tickStatus	= Tickable.STATUS_NOT;

	@Override
	public int getTickStatus()
	{
		return tickStatus;
	}

	protected int	currentWeather	= WEATHER_CLEAR;
	protected int	nextWeather		= WEATHER_CLEAR;
	protected int	weatherTicker	= WEATHER_TICK_DOWN;
	/** W12: the weather a hold keeps, or -1, and the game time it ends */
	protected int	holdCode		= -1;
	protected long	holdUntil		= 0;
	/** W20: the game time the weather last changed (a climate's HOLD), and whether a change is forced */
	protected long		lastChange	= 0;
	protected boolean	forcing		= false;

	@Override
	public void holdWeather(final int weatherCode, final long untilMillis)
	{
		holdCode = ((weatherCode >= 0) && (weatherCode < Climate.NUM_WEATHER)) ? weatherCode : -1;
		holdUntil = (holdCode < 0) ? 0 : untilMillis;
	}

	@Override
	public int heldWeather()
	{
		return holdCode;
	}

	@Override
	public long heldUntil()
	{
		return holdUntil;
	}

	@Override
	public int[] weatherChances(final Area A, final int from)
	{
		// create a seasonal CHANCE graph
		int[] seasonal=new int[seasonalWeather.length];
		seasonal=addMaskAndReturn(seasonalWeather,seasonal);
		final int derivedClimate=A.getClimateType();
		if((derivedClimate&Places.CLIMASK_COLD)>0)
			seasonal=addMaskAndReturn(seasonal,cold);

		if((derivedClimate&Places.CLIMASK_HOT)>0)
			seasonal=addMaskAndReturn(seasonal,hot);

		if((derivedClimate&Places.CLIMASK_DRY)>0)
			seasonal=addMaskAndReturn(seasonal,dry);

		if((derivedClimate&Places.CLIMASK_WET)>0)
			seasonal=addMaskAndReturn(seasonal,wet);

		if((derivedClimate&Places.CLIMASK_WINDY)>0)
			seasonal=addMaskAndReturn(seasonal,windy);
		final int f=Math.abs(from) % Climate.NUM_WEATHER;
		// W20: a named climate's weights, transitions and allowed weathers in place of the built-in ones
		final ClimateProfiles.Profile P=ClimateProfiles.of(A);
		final int sIdx=A.getTimeObj().getSeasonCode().ordinal();
		final int[] own=((P!=null)&&(sIdx<P.seasons.length))?P.seasons[sIdx]:null;
		final int[] weatherChanceChart = new int[Climate.NUM_WEATHER];
		for(int g=0;g<Climate.NUM_WEATHER;g++)
		{
			final int at=(sIdx*Climate.NUM_WEATHER)+g;
			// take the base chance for a seasonal weather occurrence (rain in winter, etc); a climate's own, with the area's climate bits added
			final int seasonalNum=(own!=null)?(own[g]+(seasonal[at]-seasonalWeather[at])):seasonal[at];
			// find the chance of changing from what it will be, to some new condition.
			final Integer over=(P!=null)?P.transitions[f][g]:null;
			final int changeNum=(over!=null)?over.intValue():changeMap[(f*Climate.NUM_WEATHER)+g];
			// add them together to find the chance of a particular change in a particular season
			// to a particular condition.
			weatherChanceChart[g] = ((P!=null)&&(!P.allows(g)))?0:(seasonalNum+changeNum);
		}
		return weatherChanceChart;
	}

	@Override
	public CMObject newInstance()
	{
		try
		{
			return getClass().getDeclaredConstructor().newInstance();
		}
		catch (final Exception e)
		{
			return new DefaultClimate();
		}
	}

	@Override
	public void initializeClass()
	{
	}

	@Override
	public CMObject copyOf()
	{
		try
		{
			final Object O=this.clone();
			return (CMObject)O;
		}
		catch(final CloneNotSupportedException e)
		{
			return new DefaultClimate();
		}
	}

	@Override
	public int nextWeatherType(final Room room)
	{
		if(room==null)
			return nextWeather;
		if(!CMLib.map().hasASky(room))
			return Climate.WEATHER_CLEAR;
		return nextWeather;
	}

	@Override
	public String nextWeatherDescription(final Room room)
	{
		if(!CMLib.map().hasASky(room))
			return CMLib.lang().L(getClass(), "You can't tell much about the weather from here.");
		return getNextWeatherDescription(room.getArea());
	}

	@Override
	public String getNextWeatherDescription(final Area A)
	{
		return theWeatherDescription(A,nextWeather);
	}

	protected final static
	int[] seasonalWeather= {
		/*  		-   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC  FG*/
		/*SPRING*/  40, 20, 10, 14,  5,  1,  0,  5,  0,  0,  0,  0,  5, 0,
		/*SUMMER*/  31, 20, 5,  10, 12,  0,  0, 20,  0,  0,  1,  1,  0, 0,
		/*FALL*/	37, 10, 15, 15, 10,  5,  2,  5,  2,  1,  0,  0, 10, 0,
		/*WINTER*/  32, 15, 11,  4,  2,  7,  3,  0,  3,  3,  0,  0, 20, 0,
	};

	protected final static
	int[] cold= {
		/*  		-   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC  FG*/
		/*SPRING*/  -5, -5,  5,-10,  0,  5,  0, -5,  5,  0,  0,  0,  10,-5,
		/*SUMMER*/   5,  1,  5,  0,  0,  1,  1,-20,  1,  1,  0,  0,  5, -5,
		/*FALL*/	 0,  0,  1, -5,  0,  1,  1, -5,  1,  1,  0,  0,  5, -5,
		/*WINTER*/ -15,  0,  0, -4, -2,  5,  2,  0,  2,  2,  0,  0,  10,-5,
	};
	protected final static
	int[] hot= {
		/*  		-   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC  FG*/
		/*SPRING*/   5,  5, -5, 10,  0, -5,  0,  5, -5,  0,  0,  0, -10,  0,
		/*SUMMER*/  -5, -1, -5,  0,  0, -1, -1, 20, -1, -1,  0,  0, -5,   0,
		/*FALL*/	 0,  0, -1,  5,  0, -1, -1,  5, -1, -1,  0,  0, -5,   0,
		/*WINTER*/  15,  0,  0,  4,  2, -5, -2,  0, -2, -2,  0,  0, -10,  0,
	};
	protected final static
	int[] dry= {
		/*  		-   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC  FG*/
		/*SPRING*/  10,-15,  0,  0,  0,  0,  0,  2,  0,  0,  0,  3,  0,-30,
		/*SUMMER*/  10,-22,  0,  0,  0,  0,  0,  0,  0,  0,  6,  6,  0,-30,
		/*FALL*/	10,-15,  0,  0,  0,  0,  0,  2,  0,  0,  0,  3,  0,-30,
		/*WINTER*/  10,-15,  0,  0,  0,  0,  0,  2,  0,  0,  0,  3,  0,-30,
	};
	protected final static
	int[] wet= {
		/*  		-   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC  FG*/
		/*SPRING*/ -10, 15,  0,  0,  0,  0,  0,  0,  0,  0,  0, -3, -2, 15,
		/*SUMMER*/ -10, 22,  0,  0,  0,  0,  0,  0,  0,  0, -6, -6,  0, 22,
		/*FALL*/   -10, 15,  0,  0,  0,  0,  0,  0,  0,  0,  0, -3, -2, 15,
		/*WINTER*/ -10, 15,  0,  0,  0,  0,  0,  0,  0,  0,  0, -3,  2, 15,
	};
	protected final static
	int[] windy= {
		/*  		-   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC  FG*/
		/*SPRING*/ -10,  0, 10,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,-15,
		/*SUMMER*/ -10,  0, 11,  0,  0,  0,  0, -2,  0,  0,  0,  1,  0,-15,
		/*FALL*/   -10,  0, 10,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,-15,
		/*WINTER*/ -10, -2, 10,  0,  0,  0,  0,  0,  0,  0,  0,  0,  2,-15,
	};
	protected final static
	int[] changeMap= {
	/*					 -    CL   WD   RA   TH   SN   HA   HE   SL   BL   DU   DR   WC   FG*/
	/*CLEAR*/			85,    0,   0,-100,-100,-100,-100,   0,-100,-100,   0, -20,   0, -75,
	/*CLOUDY*/			 0,   75,   0,   0,   0,   0,   0,   0,   0,   0,-100,-100,   0, -20,
	/*WINDY*/			 0,    0,  25,-100,-100,-100,-100,-100,-100,-100,   1,   0,   0, -75,
	/*RAIN*/			-5,    5,   0,  50,   5, -20,   0,-100, -20,-100,-100,-100,   0, -100,
	/*THUNDERSTORM*/	-5,   10,   5,   5,  35,-100,   0,   0,   0,-100,-100,-100,   0, -100,
	/*SNOW*/			-5,    5,   0,-100,-100,  35,-100,-100,-100,   5,-100,-100,   5, -100,
	/*HAIL*/			-5,    5,   0,  -8,  -8,-100,  10,-100,   0,-100,-100,-100,   5, -100,
	/*HEAT*/			 0,    0,   0,  -8,  -8,-100,-100,  50,-100,-100,   0,   1,-100, -100,
	/*SLEET*/			-5,    5,   0,  -8,  -8,   0,   0,   0,  10,   0,-100,   0,   5, -100,
	/*BLIZZ*/			-5,    5,   0,-100,-100,   5,-100,-100,-100,  15,-100,   0,  10, -100,
	/*DUST*/			-5,  -10,  20,-100,-100,-100,-100,   0,-100,-100,  15,   0,   0, -100,
	/*DROUGHT*/		   -15,  -15,   0,-100,-100,-100,-100,   0,-100,-100,   1,  85,   0, -100,
	/*WINTER*/			 0,    0,   0,   0,-100,-100,-100,-100,-100,-100,-100,  -5,  85, -50,
	/*FOG*/				 0,    0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0, -20,
	};

	@Override
	public void setNextWeatherType(final int weatherCode)
	{
		if(nextWeather >= Climate.NUM_WEATHER)
		{
			Log.errOut("Attempt to set weather to "+nextWeather+".");
			Log.errOut("Climate",new Exception());
			currentWeather = Climate.WEATHER_CLEAR;
		}
		nextWeather = weatherCode;
	}

	@Override
	public void setCurrentWeatherType(final int weatherCode)
	{
		if(weatherCode >= Climate.NUM_WEATHER)
		{
			Log.errOut("Attempt to set weather to "+weatherCode+".");
			Log.errOut("Climate",new Exception());
			currentWeather = Climate.WEATHER_CLEAR;
		}
		currentWeather = weatherCode;
	}

	@Override
	public int weatherType(final Room room)
	{
		if(room==null)
			return currentWeather;
		if(!CMLib.map().hasASky(room))
			return Climate.WEATHER_CLEAR;
		return currentWeather;
	}

	@Override
	public String weatherDescription(final Room room)
	{
		if(!CMLib.map().hasASky(room))
			return CMProps.getListFileChoiceFromIndexedList(CMProps.ListFile.WEATHER_NONE, 0);
		final String own=SkyText.roomOnly(room,"WEATHER_"+SkyText.weatherKey(currentWeather)); // W13: the room's own
		if(own!=null)
			return own;
		return getWeatherDescription(room.getArea());
	}

	@Override
	public boolean canSeeTheMoon(final Room room, final Ability butNotA)
	{
		if(canSeeTheStars(room))
			return true;
		final List<Ability> V=CMLib.flags().domainAffects(room,Ability.DOMAIN_MOONSUMMONING);
		for(int v=0;v<V.size();v++)
		{
			if(V.get(v)!=butNotA)
				return true;
		}
		return false;
	}

	@Override
	public boolean canSeeTheStars(final Room room)
	{
		if(((room.getArea().getTimeObj().getTODCode()!=TimeClock.TimeOfDay.NIGHT)
				&&(room.getArea().getTimeObj().getTODCode()!=TimeClock.TimeOfDay.DUSK))
		||(!CMLib.map().hasASky(room)))
			return false;
		switch(weatherType(room))
		{
		case Climate.WEATHER_BLIZZARD:
		case Climate.WEATHER_HAIL:
		case Climate.WEATHER_SLEET:
		case Climate.WEATHER_SNOW:
		case Climate.WEATHER_RAIN:
		case Climate.WEATHER_THUNDERSTORM:
		case Climate.WEATHER_CLOUDY:
		case Climate.WEATHER_FOG:
		case Climate.WEATHER_DUSTSTORM:
			return false;
		default:
			return true;
		}
	}

	@Override
	public boolean canSeeTheSun(final Room room)
	{
		if(room==null)
			return false;

		final Area A=room.getArea();
		if((CMLib.flags().flaggedAffects(room,Ability.FLAG_SUNSUMMONING).size()>0)
		||(A==null))
			return true;

		if(((A.getTimeObj().getTODCode()!=TimeClock.TimeOfDay.DAY)
			&&(A.getTimeObj().getTODCode()!=TimeClock.TimeOfDay.DAWN))
		||(!CMLib.map().hasASky(room))
		||(CMLib.flags().isInDark(room)))
			return false;

		switch(weatherType(room))
		{
		case Climate.WEATHER_FOG:
			if(A.getTimeObj().getTODCode()==TimeClock.TimeOfDay.DAWN)
				return false;
			return true;
		case Climate.WEATHER_BLIZZARD:
		case Climate.WEATHER_HAIL:
		case Climate.WEATHER_SLEET:
		case Climate.WEATHER_SNOW:
		case Climate.WEATHER_RAIN:
		case Climate.WEATHER_THUNDERSTORM:
		case Climate.WEATHER_CLOUDY:
		case Climate.WEATHER_DUSTSTORM:
			return false;
		default:
			return true;
		}
	}

	protected String getWeatherStop(final int weatherCode)
	{
		if((weatherCode>=0)&&(weatherCode<Climate.NUM_WEATHER))
			return CMProps.getListFileChoiceFromIndexedList(CMProps.ListFile.WEATHER_ENDS, weatherCode);
		return "";
	}

	@Override
	public void forceWeatherTick(final Area A)
	{
		weatherTicker=1;
		forcing=true; // W20: a forced change does not wait for a climate's HOLD
		try
		{
			weatherTick(A);
		}
		finally
		{
			forcing=false;
		}
	}

	/**
	 * W20: a weather's ending line: the area's climate's STOP_ line, else lists.ini's.
	 * @param A the area
	 * @param weatherCode the weather that ends
	 * @return the line
	 */
	protected String weatherStop(final Area A, final int weatherCode)
	{
		final String sky=SkyText.line(null,A,"STOP_"+SkyText.weatherKey(weatherCode)); // W13: Prop_SkyText first
		if(sky!=null)
			return sky;
		final ClimateProfiles.Profile P=ClimateProfiles.of(A);
		final String own=((P!=null)&&(weatherCode>=0)&&(weatherCode<Climate.NUM_WEATHER))?ClimateProfiles.pick(P.stops[weatherCode]):null;
		return (own!=null)?own:getWeatherStop(weatherCode);
	}

	public int[] addMaskAndReturn(final int[] one, final int[] two)
	{
		if(one.length!=two.length)
			return one;
		final int[] returnable=new int[one.length];
		for(int o=0;o<one.length;o++)
			returnable[o]=one[o]+two[o];
		return returnable;
	}

	/** W13: how the last change of weather was told: 1 its description, 2 its ending, 3 both */
	protected int lastSayMode = 0;

	/**
	 * W13: a change of weather as a room with its own Prop_SkyText lines tells it.
	 * @param R the room
	 * @param oldWeather the weather that ended
	 * @param say what the area is told
	 * @return what this room is told
	 */
	protected String roomSay(final Room R, final int oldWeather, final String say)
	{
		final String stop=SkyText.roomOnly(R,"STOP_"+SkyText.weatherKey(oldWeather));
		final String desc=SkyText.roomOnly(R,"WEATHER_"+SkyText.weatherKey(currentWeather));
		if((stop==null)&&(desc==null))
			return say;
		final String s=(stop!=null)?stop:weatherStop(R.getArea(),oldWeather);
		final String d=(desc!=null)?desc:getWeatherDescription(R.getArea());
		switch(lastSayMode)
		{
		case 1:
			return d;
		case 2:
			return s;
		case 3:
			return (s+" "+d).trim();
		default:
			return say;
		}
	}

	protected void sayToEveryoneInArea(final Area A, final int oldWeather, final String say)
	{
		for(final Enumeration<Room> r=A.getProperMap();r.hasMoreElements();)
		{
			final Room R=r.nextElement();
			if(CMLib.map().hasASky(R))
			{
				final String roomSay=roomSay(R,oldWeather,say);
				if((roomSay==null)||(roomSay.trim().length()==0))
					continue;
				for(int i=0;i<R.numInhabitants();i++)
				{
					final MOB mob=R.fetchInhabitant(i);
					if((mob!=null)
					&&(!mob.isMonster())
					&&(CMLib.flags().canSee(mob)||(currentWeather!=oldWeather)))
						mob.tell(roomSay);
				}
			}
		}
		for(final Enumeration<Boardable> s =CMLib.map().ships();s.hasMoreElements();)
		{
			final Boardable ship = s.nextElement();
			if((ship != null) && (A == CMLib.map().areaLocation(ship.getBoardableItem())))
			{
				final Area inA=ship.getArea();
				if(inA!=null)
					sayToEveryoneInArea(inA,oldWeather,say);
			}
		}
	}

	public void weatherTick(final Area A)
	{
		if((CMSecurity.isDisabled(CMSecurity.DisFlag.WEATHER))
		||(CMath.bset(A.getClimateType(), Places.CLIMASK_VOID)))
		{
			currentWeather = Climate.WEATHER_CLEAR;
			return;
		}
		if((--weatherTicker)<=0)
		{
			if((currentWeather < 0)
			||(currentWeather >= Climate.NUM_WEATHER))
				currentWeather = Climate.WEATHER_CLEAR;
			// W12: a hold (MPWEATHER ... FOR) brings each change back to what it holds
			final boolean held=(holdCode>=0)&&(GameTime.now()<holdUntil);
			if(!held)
				holdCode=-1;
			else
				nextWeather=holdCode;
			// W20: a named climate's HOLD: a weather lasts that many game hours before it changes on its own
			final ClimateProfiles.Profile profile=ClimateProfiles.of(A);
			if((profile!=null)&&(profile.holdHours>0)&&(!forcing)&&(!held)&&(lastChange>0)
			&&((GameTime.now()-lastChange)<(profile.holdHours*CMProps.getMillisPerMudHour())))
			{
				weatherTicker=WEATHER_TICK_DOWN;
				return;
			}

			// reset the weather ticker!
			weatherTicker=WEATHER_TICK_DOWN;

			String say=null;
			int goodWeatherTotal=0;
			nextWeather = Math.abs(nextWeather) % Climate.WEATHER_DESCS.length;
			int possibleNextWeather=nextWeather;
			final int[] weatherChanceChart = weatherChances(A,nextWeather); // W12: the seasonal chart, for WEATHER FORECAST too
			for(final int chance : weatherChanceChart)
			{
				// total all the change chances, negative means NO chance of this change
				if(chance>0)
					goodWeatherTotal+=chance;
			}

			// some sort of debugging commentary
			/*StringBuffer buf=new StringBuffer(name()+"/"+(A.getTimeObj().getSeasonCode().toString())+"/"+Climate.WEATHER_DESCS[nextWeather]+"->");
			for(int g=0;g<Climate.NUM_WEATHER;g++)
			{
				int seasonalNum=seasonal[(A.getTimeObj().getSeasonCode()*Climate.NUM_WEATHER)+g];
				int changeNum=changeMap[(nextWeather*Climate.NUM_WEATHER)+g];
				int chance=seasonalNum+changeNum;
				//if(chance>0) buf.append(Climate.WEATHER_DESCS[g]+"="+chance+"("+seasonalNum+"+"+changeNum+"), ");
			}*/

			// roll a number from this to that.  Like the lottery, whosever number gets rolled wins!
			final int newGoodWeatherNum=CMLib.dice().roll(1,goodWeatherTotal,-1);

			// now, determine the winner!
			int tempWeatherTotal=0;
			for(int g=0;g<Climate.NUM_WEATHER;g++)
			{
				final int chance=weatherChanceChart[g];
				if(chance>0)
				{
					tempWeatherTotal+=chance;
					if(newGoodWeatherNum<tempWeatherTotal)
					{
						possibleNextWeather=g;
						break;
					}
				}
			}

			// remember your olde weather
			final int oldWeather=currentWeather;
			if(!CMSecurity.isDisabled(CMSecurity.DisFlag.WEATHERCHANGES))
			{
				if((profile!=null)&&(!profile.allows(nextWeather))&&(!held)) // W20: a weather the climate has not
					nextWeather=possibleNextWeather;
				currentWeather=nextWeather;
				nextWeather=possibleNextWeather;
				if(held) // W12
					nextWeather=holdCode;
			}
			if(oldWeather!=currentWeather)
				lastChange=GameTime.now(); // W20
			if(oldWeather!=currentWeather)
			{
				// ST4: WEATHERCHANGE and WEATHER_PROG
				ScriptEvents.weatherChanged(A,oldWeather,currentWeather,nextWeather);
				// 0=say nothing;
				// 1=say weatherdescription only
				// 2=say stop word only
				// 3=say stop word, then weatherdescription
				/*					 -   CL  WD  RA  TH  SN  HA  HE  SL  BL  DU  DR  WC FO*/
				final int[] sayMap=	{
				/*CLEAR*/			 0,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,
				/*CLOUDY*/			 2,  0,  3,  1,  1,  1,  1,  3,  1,  1,  3,  3,  3,  3,
				/*WINDY*/			 2,  1,  0,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,
				/*RAIN*/			 2,  2,  2,  0,  1,  1,  1,  3,  1,  1,  3,  3,  3,  3,
				/*THUNDERSTORM*/	 2,  2,  2,  3,  0,  3,  3,  3,  3,  3,  3,  3,  3,  3,
				/*SNOW*/			 2,  2,  3,  3,  3,  0,  3,  3,  3,  1,  3,  3,  2,  2,
				/*HAIL*/			 2,  2,  3,  3,  3,  3,  0,  3,  3,  1,  3,  3,  2,  2,
				/*HEAT*/			 2,  3,  3,  3,  3,  3,  3,  0,  3,  3,  1,  1,  3,  3,
				/*SLEET*/			 2,  2,  3,  3,  3,  3,  3,  3,  0,  3,  3,  3,  2,  2,
				/*BLIZZ*/			 2,  2,  3,  3,  3,  3,  3,  3,  3,  0,  3,  3,  2,  2,
				/*DUST*/			 2,  3,  2,  3,  3,  3,  3,  3,  3,  3,  0,  3,  3,  3,
				/*DROUGHT*/  		 2,  3,  3,  3,  3,  3,  3,  2,  3,  3,  1,  0,  3,  3,
				/*WINTER*/			 2,  3,  3,  3,  3,  1,  1,  3,  1,  1,  1,  1,  0,  1,
				/*FOGGY*/			 2,  0,  3,  1,  1,  1,  1,  3,  1,  1,  3,  3,  3,  0,
									};
				final String stopWord=weatherStop(A,oldWeather); // W20: the climate's own
				switch(sayMap[(oldWeather*Climate.NUM_WEATHER)+currentWeather])
				{
				case 0:
					break; // say=null break;
				case 1:
					say = getWeatherDescription(A);
					break;
				case 2:
					say = stopWord;
					break;
				case 3:
					say = stopWord + " " + getWeatherDescription(A);
					break;
				}
				lastSayMode=sayMap[(oldWeather*Climate.NUM_WEATHER)+currentWeather]; // W13: for a room's own lines
			}

			if((say!=null)&&!CMSecurity.isDisabled(CMSecurity.DisFlag.WEATHERNOTIFIES))
			{
				sayToEveryoneInArea(A,oldWeather,say);
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(ticking instanceof Area)
		{
			final Area A=(Area)ticking;
			tickStatus=Tickable.STATUS_WEATHER;
			weatherTick(A);
		}
		tickStatus=Tickable.STATUS_NOT;
		return true;
	}

	protected String theWeatherDescription(final Area A, final int weather)
	{
		final StringBuffer desc=new StringBuffer("");
		if((weather<0)||(weather>=Climate.NUM_WEATHER))
			return "";
		final int derivedClimate=A.getClimateType();
		if((derivedClimate&Places.CLIMASK_VOID)>0)
			return "";
		// W13: the area's Prop_SkyText first
		final String sky=SkyText.line(null,A,"WEATHER_"+SkyText.weatherKey(weather));
		if(sky!=null)
			return sky;
		// W20: the area's climate's TEXT_ line, in place of lists.ini's
		final ClimateProfiles.Profile P=ClimateProfiles.of(A);
		final String own=(P!=null)?ClimateProfiles.pick(P.texts[weather]):null;
		if(own!=null)
			return own;
		final int listFileOrd = CMProps.ListFile.WEATHER_CLEAR.ordinal() + weather;
		final CMProps.ListFile listFileEnum = CMProps.ListFile.values()[listFileOrd];
		final String prefix;
		//#    NORMAL, WET, COLD (WINTER), HOT (SUMMER), DRY
		if(((derivedClimate&Places.CLIMASK_COLD)>0)||(A.getTimeObj().getSeasonCode()==TimeClock.Season.WINTER))
			prefix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 2);
		else
		if(((derivedClimate&Places.CLIMASK_HOT)>0)||(A.getTimeObj().getSeasonCode()==TimeClock.Season.SUMMER))
			prefix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 3);
		else
		if((derivedClimate&Places.CLIMASK_WET)>0)
			prefix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 1);
		else
		if((derivedClimate&Places.CLIMASK_DRY)>0)
			prefix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 4);
		else
			prefix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 0);
		final String suffix;
		if((derivedClimate&Places.CLIMASK_WINDY)>0)
			suffix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 5);
		else
			suffix=CMProps.getListFileChoiceFromIndexedList(listFileEnum, 6);
		desc.append((suffix.trim().length()>0) ? prefix + " " + suffix : prefix);
		switch(weather)
		{
		case Climate.WEATHER_HAIL:
			desc.append(CMLib.protocol().msp("hail.wav", 10));
			break;
		case Climate.WEATHER_HEAT_WAVE:
			break;
		case Climate.WEATHER_WINTER_COLD:
			break;
		case Climate.WEATHER_DROUGHT:
			break;
		case Climate.WEATHER_CLOUDY:
			break;
		case Climate.WEATHER_FOG:
			break;
		case Climate.WEATHER_THUNDERSTORM:
			break;
		case Climate.WEATHER_DUSTSTORM:
			desc.append(CMLib.protocol().msp("windy.wav", 10));
			break;
		case Climate.WEATHER_BLIZZARD:
			desc.append(CMLib.protocol().msp("blizzard.wav", 10));
			break;
		case Climate.WEATHER_CLEAR:
			break;
		case Climate.WEATHER_RAIN:
			desc.append(CMLib.protocol().msp("rainlong.wav", 10));
			break;
		case Climate.WEATHER_SNOW:
			break;
		case Climate.WEATHER_SLEET:
			desc.append(CMLib.protocol().msp("rain.wav", 10));
			break;
		case Climate.WEATHER_WINDY:
			desc.append(CMLib.protocol().msp("wind.wav", 10));
			break;
		}
		return "^J"+desc.toString()+"^?";
	}

	@Override
	public String getWeatherDescription(final Area A)
	{
		return theWeatherDescription(A,currentWeather);
	}

	@Override
	public int adjustWaterConsumption(final int base, final Room room)
	{
		if(((room!=null)&&(room.domainType()&Room.INDOORS)==(Room.INDOORS)))
			return base;
		switch(currentWeather)
		{
		case Climate.WEATHER_DROUGHT:
			return base*4;
		case Climate.WEATHER_DUSTSTORM:
			return base*3;
		case Climate.WEATHER_HEAT_WAVE:
			return base*2;
		case Climate.WEATHER_RAIN:
		case Climate.WEATHER_THUNDERSTORM:
			return (int)Math.round(Math.floor(CMath.div(base,2)));
		case Climate.WEATHER_BLIZZARD:
		case Climate.WEATHER_CLEAR:
		case Climate.WEATHER_CLOUDY:
		case Climate.WEATHER_HAIL:
		case Climate.WEATHER_WINDY:
		case Climate.WEATHER_WINTER_COLD:
		case Climate.WEATHER_FOG:
			break;
		}
		return base;
	}

	@Override
	public int adjustMovement(final int base, final Room room)
	{
		if(((room!=null)&&(room.domainType()&Room.INDOORS)==(Room.INDOORS)))
			return base;
		switch(currentWeather)
		{
		case Climate.WEATHER_THUNDERSTORM:
			return base*2;
		case Climate.WEATHER_HAIL:
			return base*2;
		case Climate.WEATHER_DUSTSTORM:
			return base*3;
		case Climate.WEATHER_BLIZZARD:
			return base*4;
		}
		return base;
	}

	@Override
	public int compareTo(final CMObject o)
	{
		return CMClass.classID(this).compareToIgnoreCase(CMClass.classID(o));
	}
}
