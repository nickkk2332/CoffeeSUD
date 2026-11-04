package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.Area.State;
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
   Copyright 2003-2025 Sky

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

public class MovingRoom extends ActiveTicker implements Travel.MovingRoute
{
	@Override
	public String ID()
	{
		return "MovingRoom";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ROOMS;
	}

	protected List<String>		listOfRooms		= new Vector<String>();
	protected List<String>		roomInfos		= new Vector<String>();
	protected List<List<String>>messageInfo		= new Vector<List<String>>();
	protected List<String>		mapInfo			= new Vector<String>();
	protected List<String>		stubs			= new Vector<String>();
	protected Set<Area>			areas			= new HashSet<Area>();
	protected String			xmlInfo			= "";
	private int					currentStop		= 0;
	private int					nextStop		= 0;
	private int					currentStatus	= 1;
	private boolean				isReversed		= false;

	private boolean initialized=false;
	private long due=0, departureHour=-1;
	private transient Room hostRoom;
	private transient Map<Room,Ability> views=new IdentityHashMap<Room,Ability>();
    public static void clearAll() { Travel.clearRoutes(); }
    public static void restoreAll() { Travel.restoreRoutes(); }
    @Override public boolean isAttached() { return hostRoom!=null&&hostRoom.fetchBehavior(ID())==this; }

	@Override public void startBehavior(final PhysicalAgent P)
	{
		super.startBehavior(P); if(P instanceof Room) hostRoom=(Room)P; Travel.registerRoute(this);
	}
	@Override public void endBehavior(final PhysicalAgent P)
	{
		Travel.unregisterRoute(this); super.endBehavior(P);
	}

	/** for a saved game: where on its route the room is */
	@Override
	protected String getRunState()
	{
		return "<STOP>"+currentStop+"</STOP><NEXT>"+nextStop+"</NEXT><STATUS>"+currentStatus+"</STATUS><REV>"+isReversed+"</REV><INIT>"+initialized+"</INIT><WAIT>"+Math.max(0,due-GameTime.now())+"</WAIT><HOUR>"+departureHour+"</HOUR>";
	}

	@Override
	protected void setRunState(final String xml)
	{
		final List<XMLLibrary.XMLTag> tags=CMLib.xml().parseAllXML(xml);
		if(CMLib.xml().getPieceFromPieces(tags, "STOP")==null)
			return;
		currentStop=CMLib.xml().getIntFromPieces(tags, "STOP");
		nextStop=CMLib.xml().getIntFromPieces(tags, "NEXT");
		currentStatus=CMLib.xml().getIntFromPieces(tags, "STATUS");
		isReversed=CMath.s_bool(CMLib.xml().getValFromPieces(tags, "REV"));
        initialized=CMLib.xml().getPieceFromPieces(tags,"INIT")==null||CMath.s_bool(CMLib.xml().getValFromPieces(tags,"INIT"));
        due=GameTime.now()+Math.max(1,CMLib.xml().getLongFromPieces(tags,"WAIT"));
        departureHour=CMLib.xml().getPieceFromPieces(tags,"HOUR")==null?-1:CMLib.xml().getLongFromPieces(tags,"HOUR");
        clearOverlay();
        if(hostRoom!=null) refreshOverlay();
	}

	//private static final int CODE0_TRAVELDIRECTION=0;
	//private static final int CODE0_DOORSDIRECTION=1;
	//private static final int CODE0_INSIDEARRIVEMSG=2;
	protected static final int CODE0_INSIDEDEPARTMSG=3;
	//private static final int CODE0_OUTSIDEARRIVEMSG=4;
	protected static final int CODE0_OUTSIDEDEPARTMSG=5;

	protected static final int CODE1_NORMALINSIDEOPEN=0;
	protected static final int CODE1_NORMALINSIDECLOSED=1;
	protected static final int CODE1_NORMALOUTSIDEOPEN=2;
	protected static final int CODE1_NORMALOUTSIDECLOSED=3;
	protected static final int CODE1_REVERSEINSIDEOPEN=4;
	protected static final int CODE1_REVERSEINSIDECLOSED=5;
	protected static final int CODE1_REVERSEOUTSIDEOPEN=6;
	protected static final int CODE1_REVERSEOUTSIDECLOSED=7;

	protected static final int CODE_NORMALBLOCK=0;
	protected static final int CODE_REVERSEBLOCK=1;
	protected static final int CODE_DESCRIPTIONBLOCK=2;

	public MovingRoom()
	{
		super();
		minTicks=10;maxTicks=30;chance=100;
		tickReset();
	}

	@Override
	public String accountForYourself()
	{
		return "rail mobility";
	}

	@Override
	public void setParms(final String newParms)
	{
        clearOverlay(); initialized=false; departureHour=-1;
        String route=CMParms.getParmStr(newParms,"ROUTE","");
        if(route.length()>0)
        {
            listOfRooms=new Vector<String>(); roomInfos=new Vector<String>(); messageInfo=new Vector<List<String>>(); mapInfo=new Vector<String>();
            String xml=route;
            if(!route.trim().startsWith("<"))
            {
                try
                {
                    final java.nio.file.Path root=com.planet_ink.coffee_mud.core.save.WorldFiles.worldDir().toPath().resolve("routes").toAbsolutePath().normalize();
                    final java.nio.file.Path file=root.resolve(route.startsWith("routes/")?route.substring(7):route).normalize();
                    xml=file.startsWith(root)?new String(java.nio.file.Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8):"";
                }
                catch(final java.io.IOException e) { Log.errOut(ID(),"Unable to read route "+route+": "+e.getMessage()); xml=""; }
            }
            final List<com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag> roots=CMLib.xml().parseAllXML(xml);
            final String id=CMParms.getParmStr(newParms,"LINE",roots.isEmpty()?"ROUTE":roots.get(0).tag());
            roomInfos.add(id); roomInfos.add(""+Math.max(1,CMParms.getParmInt(newParms,"WAIT",10))); roomInfos.add(""+Math.max(1,CMParms.getParmInt(newParms,"TRAVEL",10)));
            super.setParms(newParms); parms=newParms;
            if(!roots.isEmpty()) parseMovingXML(xml);
            return;
        }
        String myParms=newParms;
		listOfRooms=new Vector<String>();
		roomInfos=new Vector<String>();
		messageInfo=new Vector<List<String>>();
		mapInfo=new Vector<String>();
		stubs=new Vector<String>();
		areas=new HashSet<Area>();

		char c=';';
		int x=myParms.indexOf(c);
		if(x<0)
		{
			c='/';
			x=myParms.indexOf(c);
		}
		if(x>0)
		{
			final String parmText=myParms.substring(0,x);
			myParms=myParms.substring(x+1);
			super.setParms(parmText);
		}
		roomInfos=CMParms.parseSemicolons(myParms,false);
		parms=newParms;
		xmlInfo=loadInfo();
		if(roomInfos.size()>=3) parseMovingXML(xmlInfo);
	}

	protected static synchronized String loadInfo()
	{
		final StringBuffer str=new StringBuffer("");
		final String resourceName = "movingroom.xml";
		final CMFile //F=new CMFile(Resources.makeFileResourceName("behavior/"+resourceName),null,0);
		//if((!F.exists()) || (!F.canRead()))
			F=new CMFile(Resources.makeFileResourceName(resourceName),null,0);
		if((F.exists()) && (F.canRead()))
		{
			final List<String> V=Resources.getFileLineVector(F.text());
			for(int v=0;v<V.size();v++)
				str.append(V.get(v));
			final String theString = str.toString();
			return theString;
		}
		else
		{
			Log.errOut("MovingRoom","Unable to load "+Resources.makeFileResourceName("behavior/"+resourceName)+" or "+Resources.makeFileResourceName(resourceName));
			return "";
		}
	}

	protected void parseMovingXML(final String roomToParse)
	{
		final List<String> V = new Vector<String>();
		final String theFullBlock=CMLib.xml().returnXMLBlock(roomToParse, roomInfos.get(0).toString().toUpperCase());
		final String theStopsBlock=CMLib.xml().returnXMLBlock(theFullBlock, "STOPS");
		final String theNormalDirBlock=CMLib.xml().returnXMLBlock(theFullBlock, "NORMALDIRECTION");
		final String theReverseDirBlock=CMLib.xml().returnXMLBlock(theFullBlock, "REVERSEDIRECTION");
		final String theDescriptionsBlock=CMLib.xml().returnXMLBlock(theFullBlock, "ROOMDESCRIPTIONS");
		int x=1;
		String thisone=CMLib.xml().returnXMLValue(theStopsBlock, "STOP1");
		while (!"".equals(thisone))
		{
			++x;
			listOfRooms.add(thisone);
			thisone=CMLib.xml().returnXMLValue(theStopsBlock, "STOP"+x);
		}
		V.add(CMLib.xml().returnXMLValue(theNormalDirBlock, "TRAVELDIRECTION"));
		V.add(CMLib.xml().returnXMLValue(theNormalDirBlock, "DOORSDIRECTION"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDirBlock, "INSIDE"), "ARRIVALINFO"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDirBlock, "INSIDE"), "DEPARTINFO"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDirBlock, "OUTSIDE"), "ARRIVALINFO"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDirBlock, "OUTSIDE"), "DEPARTINFO"));
		messageInfo.add(new Vector<String>(V));
		V.clear();
		V.add(CMLib.xml().returnXMLValue(theReverseDirBlock, "TRAVELDIRECTION"));
		V.add(CMLib.xml().returnXMLValue(theReverseDirBlock, "DOORSDIRECTION"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDirBlock, "INSIDE"), "ARRIVALINFO"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDirBlock, "INSIDE"), "DEPARTINFO"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDirBlock, "OUTSIDE"), "ARRIVALINFO"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDirBlock, "OUTSIDE"), "DEPARTINFO"));
		messageInfo.add(new Vector<String>(V));
		V.clear();
		final String theNormalDescBlock = (CMLib.xml().returnXMLBlock(theDescriptionsBlock, "NORMALDIRECTION"));
		final String theReverseDescBlock = (CMLib.xml().returnXMLBlock(theDescriptionsBlock, "REVERSEDIRECTION"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDescBlock, "INSIDE"), "DOOROPENED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDescBlock, "INSIDE"), "DOORCLOSED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDescBlock, "OUTSIDE"), "DOOROPENED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theNormalDescBlock, "OUTSIDE"), "DOORCLOSED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDescBlock, "INSIDE"), "DOOROPENED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDescBlock, "INSIDE"), "DOORCLOSED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDescBlock, "OUTSIDE"), "DOOROPENED"));
		V.add(CMLib.xml().returnXMLValue(CMLib.xml().returnXMLBlock(theReverseDescBlock, "OUTSIDE"), "DOORCLOSED"));
		messageInfo.add(new Vector<String>(V));
		V.clear();
		mapInfo.add(CMLib.xml().returnXMLValue(theFullBlock, "ROOMPRINTNAME"));
		mapInfo.add(CMLib.xml().returnXMLValue(theFullBlock, "LINEPRINTNAME"));
		mapInfo.add(CMLib.xml().returnXMLValue(theFullBlock, "DISPLOC"));
	}

    protected String fixOutputString(final String incoming, final Room stopRoom)
    {
        if(messageInfo.size()<2) return incoming;
        final List<String> info=messageInfo.get(isReversed?CODE_REVERSEBLOCK:CODE_NORMALBLOCK);
        final int d=CMLib.directions().getGoodDirectionCode(info.get(1));
        return incoming.replace("$disproom",stopRoom==null?"":stopRoom.displayText())
            .replace("$traveldir",info.get(0)).replace("$inopendir",info.get(1))
            .replace("$outopendir",d<0?"":CMLib.directions().getDirectionName(Directions.getOpDirectionCode(d)));
    }
    public void clearOverlay()
    {
        Travel.unlink(this);
        if(views!=null) { for(final Map.Entry<Room,Ability> e : views.entrySet()) e.getKey().delEffect(e.getValue()); views.clear(); }
    }
    private void view(final Room R, final String text)
    {
        if(R==null||text.length()==0) return;
        final Ability A=CMClass.getAbility("Prop_TransitView"); A.setSavable(false); A.setMiscText(text); R.addNonUninvokableEffect(A); views.put(R,A);
    }
    private void scheduleStop()
    {
        due=GameTime.now()+Math.max(1,CMath.s_int(roomInfos.get(1)))*CMProps.getTickMillis();
        departureHour=-1;
        if(hostRoom==null) return;
        final TimeClock C=CMLib.time().localClock(hostRoom);
        final long now=ScriptEvents.clockHours(C), day=Math.max(1,C.getHoursInDay());
        long best=Long.MAX_VALUE;
        for(final String s : CMParms.parseCommas(CMParms.getParmStr(getParms(),"DEPART",""),true))
        {
            if(!CMath.isInteger(s)) continue;
            final int h=CMath.s_int(s); if(h<0||h>=day) continue;
            long at=now-(now%day)+h; if(at<=now) at+=day; best=Math.min(best,at);
        }
        if(best!=Long.MAX_VALUE) departureHour=best;
    }
    private void next()
    {
        if(currentStop<=0) isReversed=false;
        if(currentStop>=listOfRooms.size()-1) isReversed=true;
        nextStop=currentStop+(isReversed?-1:1);
    }
    public String timetable()
    {
        String line=mapInfo.size()>1&&mapInfo.get(1).length()>0?mapInfo.get(1):hostRoom==null?"Moving room":hostRoom.displayText();
        if(mapInfo.size()>0&&mapInfo.get(0).length()>0&&!mapInfo.get(0).equals(line)) line+=" ("+mapInfo.get(0)+")";
        if(listOfRooms.size()<2) return line+": no valid route.";
        return line+": "+(currentStatus==0?"at "+Travel.label(listOfRooms.get(Math.min(currentStop,listOfRooms.size()-1))):"travelling to "+Travel.label(listOfRooms.get(Math.min(nextStop,listOfRooms.size()-1))))
            +(currentStatus==0?departureHour>=0?", next departure hour "+(departureHour%Math.max(1,CMLib.time().localClock(hostRoom).getHoursInDay())):", departure in "+Math.max(0,(due-GameTime.now())/CMProps.getTickMillis())+" ticks":"");
    }
    public void refreshOverlay()
    {
        if(hostRoom==null||listOfRooms.size()<2||roomInfos.size()<3||messageInfo.size()<3) return;
        if(!initialized) { currentStop=0; currentStatus=0; next(); scheduleStop(); initialized=true; }
        currentStop=Math.max(0,Math.min(currentStop,listOfRooms.size()-1));
        nextStop=Math.max(0,Math.min(nextStop,listOfRooms.size()-1));
        clearOverlay();
        final List<String> descriptions=messageInfo.get(CODE_DESCRIPTIONBLOCK);
        final int base=isReversed?4:0;
        final Room current=CMLib.map().getRoom(listOfRooms.get(currentStop));
        final Room target=CMLib.map().getRoom(listOfRooms.get(nextStop));
        if(currentStatus==0&&current!=null)
        {
            final int d=CMLib.directions().getGoodDirectionCode(messageInfo.get(isReversed?CODE_REVERSEBLOCK:CODE_NORMALBLOCK).get(1));
            if(d>=0)
            {
                final Exit exit=CMClass.getExit("Open");
                if(Travel.temporaryLink(this,hostRoom,d,current,exit))
                {
                    if(!Travel.temporaryLink(this,current,Directions.getOpDirectionCode(d),hostRoom,exit)) Travel.unlink(this);
                }
            }
        }
        view(hostRoom,fixOutputString(descriptions.get(base+(currentStatus==0?0:1)),currentStatus==0?current:target));
        for(int i=0;i<listOfRooms.size();i++)
        {
            final Room R=CMLib.map().getRoom(listOfRooms.get(i));
            if(R==null||R==hostRoom) continue;
            String text=fixOutputString(descriptions.get(base+(currentStatus==0&&i==currentStop?2:3)),hostRoom);
            if(CMath.s_bool(CMParms.getParmStr(getParms(),"DISPLOC",mapInfo.size()>2?mapInfo.get(2):"false"))) text+=" "+timetable();
            view(R,text.trim());
            final String sign=CMParms.getParmStr(getParms(),"SIGN","");
            final Item board=sign.length()==0?null:R.findItem(null,sign);
            if(board!=null) board.setReadableText(timetable());
        }
    }
    @Override public boolean tick(final Tickable ticking, final int tickID)
    {
        if(!(ticking instanceof Room)||listOfRooms.size()<2||messageInfo.size()<3) return true;
        hostRoom=(Room)ticking;
        if(!initialized) refreshOverlay();
        final boolean ready=currentStatus==0&&departureHour>=0?ScriptEvents.clockHours(CMLib.time().localClock(hostRoom))>=departureHour:GameTime.now()>=due;
        if(!ready) return true;
        final List<String> info=messageInfo.get(isReversed?CODE_REVERSEBLOCK:CODE_NORMALBLOCK);
        final Room stopRoom=CMLib.map().getRoom(listOfRooms.get(currentStatus==0?currentStop:nextStop));
        if(stopRoom==null) return true;
        if(currentStatus==0)
        {
            hostRoom.showHappens(CMMsg.MSG_OK_ACTION,fixOutputString(info.get(CODE0_INSIDEDEPARTMSG),stopRoom));
            stopRoom.showHappens(CMMsg.MSG_OK_ACTION,fixOutputString(info.get(CODE0_OUTSIDEDEPARTMSG),stopRoom));
            currentStatus=1; departureHour=-1; due=GameTime.now()+Math.max(1,CMath.s_int(roomInfos.get(2)))*CMProps.getTickMillis();
        }
        else
        {
            hostRoom.showHappens(CMMsg.MSG_OK_ACTION,fixOutputString(info.get(2),stopRoom));
            stopRoom.showHappens(CMMsg.MSG_OK_ACTION,fixOutputString(info.get(4),stopRoom));
            currentStop=nextStop; currentStatus=0; next(); scheduleStop();
        }
        refreshOverlay();
        return true;
    }
    @Override public CMObject copyOf()
    {
        final MovingRoom B=new MovingRoom(); B.setParms(getParms()); return B;
    }
}
