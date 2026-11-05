package com.planet_ink.coffee_mud.Abilities.Properties;

import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** One saved passage on the leader; alarms are rebuilt from remaining time. */
public class Prop_JourneyState extends StdAbility implements Travel.Passage
{
    @Override public String ID() { return "Prop_JourneyState"; }
    @Override public String name() { return "Travelling"; }
    @Override public String displayText() { return "(Travelling)"; }
    @Override protected int canAffectCode() { return CAN_MOBS; }
    private String definition="", origin="", sourceName="", destination="", transit="";
    private String hostRoom="", sourceType="", sourceRef="";
    private int sourceDirection=-1;
    private transient PhysicalAgent sourceHost;
    private List<String> names=new ArrayList<String>();
    private List<Receipt> receipts=new ArrayList<Receipt>();
    private transient Map<String,MOB> boundPassengers=new HashMap<String,MOB>();
    private Set<String> deadSeats=new HashSet<String>();
    private Map<String,String> playerNames=new HashMap<String,String>();
    private boolean returnPending=false;
    private static final class Receipt
    {
        String seat, player, currency; double amount;
        Receipt(final String seat,final String player,final String currency,final double amount)
        {this.seat=seat;this.player=player;this.currency=currency;this.amount=amount;}
    }
    private double hours=0, elapsed=0;
    private long due=0, remaining=0, generation=0, pausedAt=0;
    private boolean waiting=false, manual=false, armed=false;
	private static final ThreadLocal<Set<MOB>> starting=new ThreadLocal<Set<MOB>>() {
		@Override protected Set<MOB> initialValue() { return new HashSet<MOB>(); }
	};

    public static String begin(final MOB who, final Physical source, final String definition)
    {
        return Travel.beginJourney(who,source,definition);
    }
    @Override public String depart(final MOB who, final Physical source, final String definition)
    {
        if(who==null) return "No traveller was specified.";
        final MOB leader=who.getGroupLeader().location()==who.location()?who.getGroupLeader():who;
        if(!starting.get().add(leader)) return null;
        try { return beginInternal(who,source,definition); }
        finally { starting.get().remove(leader); }
    }
    private static String beginInternal(final MOB who, final Physical source, final String definition)
    {
        if(who==null||who.location()==null) return "You cannot begin a journey here.";
        final MOB leader=who.getGroupLeader().location()==who.location()?who.getGroupLeader():who;
        if(leader.fetchEffect("Prop_JourneyState")!=null) return "Your party is already travelling.";
        if(GameTime.isAdvancing()) return "Wait for the current passage of time to finish.";
        final Room to=Travel.destination(Travel.option(definition,"DEST",""),who);
        if(to==null) return "The journey has no valid destination.";
        final double duration=AuthoringOptions.number(definition,"DURATION",0);
        if(duration<=0||!Double.isFinite(duration)) return "The journey needs a positive DURATION in game hours.";
        final String via=Travel.option(definition,"VIA","");
        final Room through=via.length()==0?leader.location():Travel.destination(via,null);
        if(through==null) return "The journey has no valid transit room.";
        final List<MOB> party=Travel.party(leader,leader.location());
        for(final MOB M : party) if(M.isInCombat()) return "Finish the fight before departing.";
        final double fare=Math.max(0,AuthoringOptions.number(definition,"FARE",0));
        final boolean each=Travel.option(definition,"PARTY","LEADER").equalsIgnoreCase("EACH");
        final boolean perHead=AuthoringOptions.bool(definition,"PERHEAD",true);
        final Map<MOB,Double> charges=Travel.fares(leader,party,fare,perHead,each,0,0);
        final Map<MOB,String> currencies=new IdentityHashMap<MOB,String>();
        final Room from=leader.location();
        if(!Travel.transfer(leader,party,through,true,() -> {
            for(final MOB payer:charges.keySet())currencies.put(payer,CMLib.beanCounter().getCurrency(payer));
            return Travel.fare(leader,party,fare,perHead,each,0,0,true);
        })) return "The party cannot depart.";
        final Prop_JourneyState state=new Prop_JourneyState();
        state.definition=definition; state.origin=CMLib.map().getExtendedRoomID(from);
        state.sourceName=source==null?"":source.Name();
        state.sourceHost=source instanceof PhysicalAgent?(PhysicalAgent)source:null;
        state.sourceRef=InstanceIds.reference(source);
        state.sourceType=source instanceof Exit?"EXIT":source instanceof Room?"ROOM":source instanceof MOB?"MOB":"ITEM";
        Room scriptRoom=source instanceof Room?(Room)source:CMLib.map().roomLocation(source);
        if(source instanceof Exit)
        {
            if(from!=null) state.sourceDirection=CMLib.map().getExitDir(from,(Exit)source);
            if(state.sourceDirection>=0) scriptRoom=from;
            else for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
            {
                final Room candidate=e.nextElement();
                final int d=CMLib.map().getExitDir(candidate,(Exit)source);
                if(d>=0) { scriptRoom=candidate; state.sourceDirection=d; break; }
            }
        }
        state.hostRoom=CMLib.map().getExtendedRoomID(scriptRoom==null?from:scriptRoom);
        state.destination=CMLib.map().getExtendedRoomID(to); state.transit=CMLib.map().getExtendedRoomID(through);
        state.hours=duration; state.remaining=Math.max(1,Math.round(Math.min(1,duration)*CMProps.getMillisPerMudHour()));
        final String trip=java.util.UUID.randomUUID().toString();
        for(final MOB M : party)
        {
            final Ability previous=M.fetchEffect("Prop_JourneyPassenger");
            if(previous!=null) M.delEffect(previous);
            final Ability marker=CMClass.getAbility("Prop_JourneyPassenger");
            final String seat=trip+"_"+state.names.size();
            marker.setMiscText(seat); marker.startTickDown(leader,M,Integer.MAX_VALUE); state.names.add(seat);
            state.boundPassengers.put(seat,M);
            if(M.isPlayer())state.playerNames.put(seat,M.Name());
            final double paid=charges.containsKey(M)?charges.get(M).doubleValue():0;
            if(paid>0) state.receipts.add(new Receipt(seat,M.isPlayer()?M.Name():"",currencies.get(M),paid));
        }
        state.startTickDown(leader,leader,Integer.MAX_VALUE);
        state.event("START");
        state.message("LEAVEMSG");
        state.arm(true);
        return null;
    }

    private MOB leader() { return affected instanceof MOB?(MOB)affected:null; }
    private List<MOB> passengers()
    {
        final List<MOB> result=new ArrayList<MOB>();
        final MOB M=leader();
        if(M==null||M.location()==null) return result;
        for(final String name : names)
            for(final Enumeration<MOB> e=M.location().inhabitants();e.hasMoreElements();)
            {
                final MOB member=e.nextElement();
                final Ability marker=member.fetchEffect("Prop_JourneyPassenger");
                if(marker!=null&&marker.text().equals(name)) { result.add(member); break; }
            }
        return result;
    }
    private Map<String,MOB> locatedPassengers()
    {
        final Map<String,MOB> found=new HashMap<String,MOB>(boundPassengers);
        final Set<MOB> all=Collections.newSetFromMap(new IdentityHashMap<MOB,Boolean>());
        for(final Enumeration<MOB> e=CMLib.map().worldMobs();e.hasMoreElements();) all.add(e.nextElement());
        for(final Enumeration<MOB> e=CMLib.players().players();e.hasMoreElements();) all.add(e.nextElement());
        all.addAll(passengers());
        for(final MOB M:all)
        {
            final Ability marker=M.fetchEffect("Prop_JourneyPassenger");
            if(marker!=null&&(names.contains(marker.text())||receipts.stream().anyMatch(r->r.seat.equals(marker.text())))) found.put(marker.text(),M);
        }
        return found;
    }
    /** Reconcile seats before advancing or retrying arrival. No names are used as passenger identities. */
    private boolean reconcile()
    {
        final Map<String,MOB> found=locatedPassengers();
        for(final String seat:new ArrayList<String>(names))
        {
            final MOB member=found.get(seat);
            final boolean dead=member==null?deadSeats.contains(seat):member.amDead()||member.amDestroyed();
            final Ability marker=member==null?null:member.fetchEffect("Prop_JourneyPassenger");
            final boolean missing=member==null||member.location()!=leader().location()||marker==null||!marker.text().equals(seat);
            final boolean disconnected=member!=null&&member.isPlayer()&&member.session()==null;
            if(!dead&&!missing&&!disconnected) continue;
            final String policy=Travel.option(definition,dead?"DEAD":missing?"MISSING":"DISCONNECTED",dead?"CONTINUE":missing?"WAIT":"TRAVEL").toUpperCase();
            if(policy.equals("TRAVEL")&&!dead&&!missing) continue;
            if(policy.equals("CONTINUE"))
            {
                names.remove(seat);
                clearPassengerMarker(seat,member);
                continue;
            }
            if(policy.equals("CANCEL")) {cancelJourney();return false;}
            if(policy.equals("RETURN")) {finishCancellation(true);return false;}
            if(policy.equals("RESCUE")&&member!=null&&!dead&&!member.isInCombat()&&marker!=null&&marker.text().equals(seat)
            &&Travel.transfer(leader(),Collections.singletonList(member),leader().location(),true)) continue;
            pauseJourney(true);
            leader().tell("A travelling companion is "+(dead?"dead":missing?"missing":"disconnected")+". This journey's "+(dead?"DEAD":missing?"MISSING":"DISCONNECTED")+" policy is "+policy+". JOURNEY RESUME retries recovery; JOURNEY CANCEL ends the trip.");
            return false;
        }
        return true;
    }
    private PhysicalAgent host()
    {
        if(sourceHost!=null&&!sourceHost.amDestroyed()) return sourceHost;
        if(!sourceRef.isEmpty())
        {
            final Environmental found=InstanceIds.find(sourceRef);
            return found instanceof PhysicalAgent?(PhysicalAgent)found:null;
        }
        final Room R=CMLib.map().getRoom(hostRoom.length()==0?origin:hostRoom);
        if(R!=null)
        {
            if(sourceType.equals("EXIT")&&sourceDirection>=0) return R.getRawExit(sourceDirection);
            if(sourceType.equals("ROOM")) return R;
            if(sourceName.isEmpty())return R;
            final Environmental found=InstanceIds.legacy(R,sourceName,sourceType.equals("MOB")?MOB.class:sourceType.equals("ITEM")?Item.class:PhysicalAgent.class);
            return found instanceof PhysicalAgent?(PhysicalAgent)found:null;
        }
        return leader()==null?null:leader().location();
    }
    private void event(final String phase)
    {
        final MOB M=leader();
        if(M==null) return;
        final int code=Arrays.asList(ScriptingEngine.progs).indexOf("JOURNEY_PROG")+1;
        final List<PhysicalAgent> hosts=ScriptEvents.hosts(M.location());
        final PhysicalAgent H=host(); if(H!=null&&!hosts.contains(H)) hosts.add(H);
        ScriptEvents.fireAround(hosts,code,M,H,phase,new Object[]{""+elapsed,destination},"Ccr",
            t -> t.length<2||t[1].equalsIgnoreCase("ALL")||t[1].equalsIgnoreCase(phase));
    }
    private void message(final String key)
    {
        final String text=Travel.option(definition,key,"");
        if(text.length()>0) for(final MOB M : passengers()) M.tell(text);
    }
    private boolean fighting()
    {
        for(final MOB M : passengers()) if(M.isInCombat()) return true;
        return false;
    }
    private void arm(final boolean advance)
    {
        if(leader()==null||waiting||armed||hours<=0) return;
        armed=true; due=GameTime.now()+Math.max(1,remaining);
        final long token=++generation;
        CMLib.threads().scheduleRunnable(() -> { if(token==generation&&leader()!=null&&leader().fetchEffect(ID())==this) hour(); },Math.max(1,remaining));
        if(advance&&Travel.hasPlayer(leader())) Travel.elapse(Math.max(1,remaining));
    }
    public void pauseJourney(final boolean explicit)
    {
        if(!waiting) remaining=Math.max(1,due-GameTime.now());
        waiting=true; manual=explicit; pausedAt=GameTime.now(); armed=false; generation++;
        GameTime.cancelAdvance();
    }
    public String resumeJourney()
    {
        if(leader()==null)return "The journey has ended.";
        if(returnPending){finishCancellation(true);return leader()==null?"The journey has ended.":"The return remains blocked.";}
        if(fighting()) return "Finish the encounter before continuing.";
        if(!reconcile()) return leader()==null?"The journey has ended.":"The journey remains paused.";
        waiting=false; manual=false;
        if(hours<=0) arrive(); else arm(true);
        return "Your journey continues.";
    }
    public void cancelJourney()
    {
        finishCancellation(returnPending||Travel.option(definition,"CANCEL","HERE").equalsIgnoreCase("RETURN"));
    }
    private void finishCancellation(final boolean returnHome)
    {
        final MOB M=leader();
        if(M==null) return;
        if(returnHome&&!M.amDead())
        {
            final List<MOB> group=passengers();
            if(!Travel.transfer(M,group,CMLib.map().getRoom(origin),true))
            {returnPending=true;pauseJourney(true);M.tell("The party cannot return to the origin yet. JOURNEY CANCEL retries the return.");return;}
        }
        generation++; armed=false;
        event("CANCEL"); refund(); clearMarkers();
        M.tell(returnHome?"Your journey has ended at its origin.":"Your journey has ended here."); M.delEffect(this);
        setAffectedOne(null); GameTime.cancelAdvance();
    }
    private void refund()
    {
        final String mode=Travel.option(definition,"REFUND","NONE");
        if(mode.equalsIgnoreCase("NONE")) return;
        final double fraction=mode.equalsIgnoreCase("UNUSED")?(hours+elapsed>0?hours/(hours+elapsed):0):1;
        final Map<String,MOB> found=locatedPassengers();
        for(final Receipt r:receipts)
        {
            MOB payer=found.get(r.seat); boolean offline=false;
            if((payer==null||payer.amDestroyed())&&!r.player.isEmpty()) {payer=CMLib.players().getLoadPlayer(r.player);offline=payer!=null&&CMLib.players().getPlayer(r.player)!=payer;}
            if(payer==null) continue;
            final String token=StoryRecords.token("FARE_REFUND_",r.seat);
            if(payer.findTattoo(token)==null)
            {
                CMLib.beanCounter().addMoney(payer,r.currency,r.amount*fraction); payer.addTattoo(token);
                if(offline)
                {
                    final Ability marker=payer.fetchEffect("Prop_JourneyPassenger");
                    if(marker!=null&&marker.text().equals(r.seat))payer.delEffect(marker);
                    CMLib.database().DBUpdatePlayer(payer);
                }
            }
        }
        receipts.clear();
    }
    private void arrive()
    {
        if(leader()==null||!reconcile()) return;
        final MOB M=leader(); final List<MOB> group=passengers();
        if(group.size()!=names.size()||!Travel.transfer(M,group,CMLib.map().getRoom(destination),true))
        { waiting=true; manual=true; M.tell("The party cannot arrive yet. JOURNEY RESUME retries arrival."); return; }
        message("ARRIVEMSG"); event("ARRIVE"); generation++; clearMarkers(); M.delEffect(this); setAffectedOne(null);
    }
    private void clearMarkers()
    {
        for(final Enumeration<MOB> e=CMLib.map().worldMobs();e.hasMoreElements();)
        {
            final MOB M=e.nextElement(); final Ability marker=M.fetchEffect("Prop_JourneyPassenger");
            if(marker!=null&&names.contains(marker.text())) M.delEffect(marker);
        }
        for(final Enumeration<MOB> e=CMLib.players().players();e.hasMoreElements();)
        {
            final MOB M=e.nextElement(); final Ability marker=M.fetchEffect("Prop_JourneyPassenger");
            if(marker!=null&&names.contains(marker.text())) M.delEffect(marker);
        }
        for(final String seat:playerNames.keySet())clearPassengerMarker(seat,boundPassengers.get(seat));
    }
    private void clearPassengerMarker(final String seat,final MOB known)
    {
        MOB member=known;boolean offline=false;
        if((member==null||member.amDestroyed())&&playerNames.containsKey(seat))
        {
            member=CMLib.players().getLoadPlayer(playerNames.get(seat));
            offline=member!=null&&CMLib.players().getPlayer(playerNames.get(seat))!=member;
        }
        if(member==null)return;
        final Ability marker=member.fetchEffect("Prop_JourneyPassenger");
        if(marker!=null&&marker.text().equals(seat))
        {member.delEffect(marker);if(offline)CMLib.database().DBUpdatePlayer(member);}
    }
    public void hour()
    {
        if(leader()==null||waiting||!reconcile()) return;
        if(fighting()) { pauseJourney(false); return; }
        armed=false;
        final double leg=Math.min(1,hours); hours=Math.max(0,hours-leg); elapsed+=leg;
        remaining=Math.max(1,Math.round(Math.min(1,hours)*CMProps.getMillisPerMudHour()));
        event("HOUR");
        boolean encounter=false;
        for(final String entry : CMParms.parseSemicolons(Travel.option(definition,"EVENTS",""),true))
        {
            final int colon=entry.indexOf(':');
            if(colon<=0) continue;
            final double chance=CMath.s_double(entry.substring(0,colon).replace("%",""));
            if(CMLib.dice().rollPercentage()>chance) continue;
            final String action=entry.substring(colon+1).trim();
            if(action.toUpperCase().startsWith("LOAD MOB ")) Encounters.spawnSpec(action.substring(9).trim(),leader().location(),leader());
            else if(action.toUpperCase().startsWith("CATALOG "))
                Encounters.roll(Encounters.table(action.substring(8).trim()),leader().location(),leader(),host(),100);
            else manual="PAUSE".equalsIgnoreCase(ScriptEvents.callFunction(host(),action,""+elapsed,leader()));
            encounter=true; break;
        }
        if(encounter||fighting())
        {
            waiting=true; pausedAt=GameTime.now(); generation++;
            GameTime.cancelAdvance();
        }
        else if(hours<=0) arrive();
        else arm(true);
    }
    @Override public boolean tick(final Tickable ticking, final int id)
    {
        if(!super.tick(ticking,id)) return false;
        if(leader()==null) return false;
        if(leader().amDead()) {finishCancellation(false);return false;}
        if(leader().location()!=CMLib.map().getRoom(transit)) {finishCancellation(false);return false;}
        if(!waiting&&fighting()) pauseJourney(false);
        else if(waiting&&!manual&&!fighting()&&GameTime.now()-pausedAt>=CMProps.getTickMillis()) resumeJourney();
        else if(!waiting&&!armed) arm(true);
        return true;
    }
    public String status() { return hours+" game hours remain to "+Travel.label(destination)+(waiting?" (paused)":"")+". JOURNEY RESUME or CANCEL."; }
    @Override public String text()
    {
        final com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary xml=CMLib.xml();
        final StringBuilder out=new StringBuilder("<JOURNEY>");
        out.append(xml.convertXMLtoTag("DEFINITION",xml.parseOutAngleBrackets(definition)));
        out.append(xml.convertXMLtoTag("ORIGIN",origin)).append(xml.convertXMLtoTag("SOURCE",xml.parseOutAngleBrackets(sourceName)));
        out.append(xml.convertXMLtoTag("HOSTROOM",hostRoom)).append(xml.convertXMLtoTag("SOURCETYPE",sourceType)).append(xml.convertXMLtoTag("EXITDIR",sourceDirection));
        out.append(xml.convertXMLtoTag("SOURCEREF",sourceRef));
        out.append(xml.convertXMLtoTag("DEST",destination)).append(xml.convertXMLtoTag("VIA",transit));
        out.append(xml.convertXMLtoTag("LEFT",""+hours)).append(xml.convertXMLtoTag("ELAPSED",""+elapsed));
        out.append(xml.convertXMLtoTag("REMAINING",armed?Math.max(1,due-GameTime.now()):remaining));
        out.append(xml.convertXMLtoTag("WAITING",waiting)).append(xml.convertXMLtoTag("MANUAL",manual));
        out.append(xml.convertXMLtoTag("RETURNPENDING",returnPending));
        for(final String name : names) out.append(xml.convertXMLtoTag("MEMBER",xml.parseOutAngleBrackets(name)));
        for(final Map.Entry<String,String> p:playerNames.entrySet())out.append("<PLAYERSEAT>").append(xml.convertXMLtoTag("SEAT",p.getKey())).append(xml.convertXMLtoTag("NAME",xml.parseOutAngleBrackets(p.getValue()))).append("</PLAYERSEAT>");
        for(final String seat:names)
        {
            final MOB member=boundPassengers.get(seat);
            if(member==null?deadSeats.contains(seat):member.amDead()||member.amDestroyed())out.append(xml.convertXMLtoTag("DEADSEAT",seat));
        }
        for(final Receipt r:receipts)
            out.append("<RECEIPT>").append(xml.convertXMLtoTag("SEAT",r.seat)).append(xml.convertXMLtoTag("PLAYER",xml.parseOutAngleBrackets(r.player))).append(xml.convertXMLtoTag("CURRENCY",xml.parseOutAngleBrackets(r.currency))).append(xml.convertXMLtoTag("AMOUNT",""+r.amount)).append("</RECEIPT>");
        return out.append("</JOURNEY>").toString();
    }
    @Override public void setMiscText(final String text)
    {
        final List<XMLTag> roots=CMLib.xml().parseAllXML(text);
        if(roots.isEmpty()) return;
        final XMLTag root=roots.get(0);
        definition=root.getValFromPieces("DEFINITION"); origin=root.getValFromPieces("ORIGIN"); sourceName=root.getValFromPieces("SOURCE");
        sourceHost=null; hostRoom=root.getValFromPieces("HOSTROOM"); sourceType=root.getValFromPieces("SOURCETYPE");
        sourceRef=root.getValFromPieces("SOURCEREF");
        sourceDirection=root.getPieceFromPieces("EXITDIR")==null?-1:CMath.s_int(root.getValFromPieces("EXITDIR"));
        destination=root.getValFromPieces("DEST"); transit=root.getValFromPieces("VIA");
        hours=CMath.s_double(root.getValFromPieces("LEFT")); elapsed=CMath.s_double(root.getValFromPieces("ELAPSED"));
        remaining=CMath.s_long(root.getValFromPieces("REMAINING")); waiting=CMath.s_bool(root.getValFromPieces("WAITING")); manual=CMath.s_bool(root.getValFromPieces("MANUAL"));
        returnPending=CMath.s_bool(root.getValFromPieces("RETURNPENDING"));
        boundPassengers=new HashMap<String,MOB>();deadSeats=new HashSet<String>();for(final XMLTag t:root.contents())if(t.tag().equals("DEADSEAT"))deadSeats.add(t.value());
        names=new ArrayList<String>(); for(final XMLTag t : root.contents()) if(t.tag().equals("MEMBER")) names.add(CMLib.xml().restoreAngleBrackets(t.value()));
        playerNames=new HashMap<String,String>();for(final XMLTag t:root.contents())if(t.tag().equals("PLAYERSEAT"))playerNames.put(t.getValFromPieces("SEAT"),CMLib.xml().restoreAngleBrackets(t.getValFromPieces("NAME")));
        receipts=new ArrayList<Receipt>();for(final XMLTag t:root.contents()) if(t.tag().equals("RECEIPT"))
            receipts.add(new Receipt(t.getValFromPieces("SEAT"),CMLib.xml().restoreAngleBrackets(t.getValFromPieces("PLAYER")),CMLib.xml().restoreAngleBrackets(t.getValFromPieces("CURRENCY")),CMath.s_double(t.getValFromPieces("AMOUNT"))));
        definition=CMLib.xml().restoreAngleBrackets(definition); sourceName=CMLib.xml().restoreAngleBrackets(sourceName);
        armed=false; generation++; pausedAt=GameTime.now();
    }
    @Override public CMObject copyOf()
    {
        final Prop_JourneyState A=(Prop_JourneyState)super.copyOf(); A.names=new ArrayList<String>(names); A.receipts=new ArrayList<Receipt>(receipts); A.boundPassengers=new HashMap<String,MOB>(boundPassengers);A.deadSeats=new HashSet<String>(deadSeats);A.playerNames=new HashMap<String,String>(playerNames); A.armed=false; A.remaining=armed?Math.max(1,due-GameTime.now()):remaining; return A;
    }
}
