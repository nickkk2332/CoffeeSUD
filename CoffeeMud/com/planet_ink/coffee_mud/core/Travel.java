package com.planet_ink.coffee_mud.core;

import java.util.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Shared time, routing, passenger and fare rules for authored transport. */
public final class Travel
{
    private Travel() {}
    private static final java.util.concurrent.atomic.AtomicLong interiorIDs=new java.util.concurrent.atomic.AtomicLong();
    public static String newInteriorName(final String name)
    {
        String base=CMStrings.removeColors(name).replaceAll("[^A-Za-z0-9_]","_");
        if(base.length()>40) base=base.substring(0,40);
        String next;
        do { next=base+"_v"+interiorIDs.incrementAndGet(); }
        while(CMLib.map().getArea(next)!=null||vessel(next)!=null);
        return next;
    }

    /** Runtime ability state kept in saves, separately from authored parameters. */
    public interface Stateful
    {
        String travelState();
        void restoreTravelState(String xml);
    }
    public interface InPlaceScaling extends Stateful
    {
        boolean isInPlace();
        void rebuildInPlace(Room room, MOB leader);
    }
    public interface Helm
    {
        boolean allowed(MOB mob, Boardable ship);
    }
    /** Entry work runs after veto checks and payment, before rooms receive ENTER. */
    public interface CommittedEntry { void entering(Environmental host, MOB who, Room from, Room to); }
    public interface BoardingReceipt { void disembarked(MOB who); }
    public static void entering(final MOB who, final Room from, final Room to)
    {
        if(who==null||to==null||from==to) return;
        final PhysicalAgent[] hosts={to.getArea(),to};
        for(final PhysicalAgent host : hosts)
        {
            if(host==null) continue;
            for(final Enumeration<com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior> e=host.behaviors();e.hasMoreElements();)
            {
                final Object hook=e.nextElement();
                if(hook instanceof CommittedEntry) ((CommittedEntry)hook).entering(host,who,from,to);
            }
            for(final Enumeration<Ability> e=host.effects();e.hasMoreElements();)
            {
                final Ability hook=e.nextElement();
                if(hook instanceof CommittedEntry) ((CommittedEntry)hook).entering(host,who,from,to);
            }
        }
    }
    public static void disembarked(final MOB who, final Boardable vessel)
    {
        for(final PhysicalAgent host : ScriptEvents.aboard(vessel.getBoardableItem()))
            for(final Enumeration<Ability> e=host.effects();e.hasMoreElements();)
            {
                final Ability hook=e.nextElement();
                if(hook instanceof BoardingReceipt) ((BoardingReceipt)hook).disembarked(who);
            }
    }
    public interface PassageSource { String begin(MOB who); }
    public interface StableService { String stable(MOB attendant, MOB customer, String request); }
    public interface Passage
    {
        String depart(MOB who, Physical source, String definition);
        String status();
        String resumeJourney();
        void cancelJourney();
    }
    public interface PacedVehicle { boolean travelReady(); }
    public interface Timetable { String timetable(); }
    public interface Line extends Timetable { long arrivals(); boolean isMoving(); }
    public interface MovingRoute extends Timetable
    {
        void refreshOverlay();
        void clearOverlay();
        boolean isAttached();
    }
    private static final Map<MovingRoute,Boolean> movingRoutes=Collections.synchronizedMap(new WeakHashMap<MovingRoute,Boolean>());
    public static void registerRoute(final MovingRoute route) { movingRoutes.put(route,Boolean.TRUE); }
    public static void unregisterRoute(final MovingRoute route) { route.clearOverlay(); movingRoutes.remove(route); }
    public static void restoreRoutes()
    {
        synchronized(movingRoutes)
        {
            for(final MovingRoute route : new ArrayList<MovingRoute>(movingRoutes.keySet()))
                if(route.isAttached()) route.refreshOverlay(); else unregisterRoute(route);
        }
    }
    public static void clearRoutes()
    {
        synchronized(movingRoutes) { for(final MovingRoute route : movingRoutes.keySet()) route.clearOverlay(); movingRoutes.clear(); }
        clearLinks();
    }
    public static String beginJourney(final MOB who, final Physical source, final String definition)
    {
        final Ability A=CMClass.getAbility("Prop_JourneyState");
        return A instanceof Passage?((Passage)A).depart(who,source,definition):"Journey state is unavailable.";
    }

    private static final class Link
    {
        final Object owner; final Room room; final Exit exit;
        Link(final Object owner, final Room room, final Exit exit) { this.owner=owner; this.room=room; this.exit=exit; }
    }
    private static final Map<Room,Map<Integer,Link>> links=Collections.synchronizedMap(new WeakHashMap<Room,Map<Integer,Link>>());
    public static boolean temporaryLink(final Object owner, final Room from, final int direction, final Room to, final Exit exit)
    {
        if(from==null||to==null||direction<0||direction>=Directions.NUM_DIRECTIONS()||from.rawDoors()[direction]!=null) return false;
        synchronized(links)
        {
            Map<Integer,Link> map=links.get(from);
            if(map==null) { map=new HashMap<Integer,Link>(); links.put(from,map); }
            final Link old=map.get(Integer.valueOf(direction));
            if(old!=null&&old.owner!=owner) return false;
            map.put(Integer.valueOf(direction),new Link(owner,to,exit)); return true;
        }
    }
    public static Room temporaryDoor(final Room from, final int direction)
    {
        synchronized(links)
        {
            final Map<Integer,Link> map=links.get(from); final Link link=map==null?null:map.get(Integer.valueOf(direction));
            return link==null?null:link.room;
        }
    }
    public static Exit temporaryExit(final Room from, final int direction)
    {
        synchronized(links)
        {
            final Map<Integer,Link> map=links.get(from); final Link link=map==null?null:map.get(Integer.valueOf(direction));
            return link==null?null:link.exit;
        }
    }
    public static void unlink(final Object owner)
    {
        synchronized(links) { for(final Map<Integer,Link> map : links.values()) map.values().removeIf(link -> link.owner==owner); }
    }
    public static void clearLinks() { links.clear(); }

    public static String option(final String text, final String key, final String fallback)
    {
        return CMParms.getParmStr(text,key,fallback);
    }

    public static Ability property(final Physical object, final String id)
    {
        return object==null?null:object.fetchEffect(id);
    }

    public static List<MOB> party(final MOB who, final Room at)
    {
        final List<MOB> result=new ArrayList<MOB>();
        if(who==null) return result;
        final MOB leader=who.getGroupLeader();
        for(final MOB M : leader.getGroupMembers(new LinkedHashSet<MOB>()))
            if(M.location()==at&&!M.amDead()&&!M.amDestroyed()) result.add(M);
        if(!result.contains(who)&&who.location()==at) result.add(who);
        for(final MOB M : new ArrayList<MOB>(result))
            if(M.riding() instanceof MOB)
            {
                final MOB mount=(MOB)M.riding();
                if(mount.location()==at&&!result.contains(mount)) result.add(mount);
            }
        return result;
    }

    public static boolean hasPlayer(final Environmental object)
    {
        if(object instanceof MOB&&((MOB)object).isPlayer()) return true;
        if(object instanceof Rideable)
            for(final Enumeration<Rider> e=((Rideable)object).riders();e.hasMoreElements();)
                if(hasPlayer(e.nextElement())) return true;
        if(object instanceof Boardable)
        {
            final Area A=((Boardable)object).getArea();
            if(A!=null) for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
                if(e.nextElement().numPCInhabitants()>0) return true;
        }
        return false;
    }

    /** Advance through the scheduler; never jump over weather, hunger or scripts. */
    public static void elapse(final long ms)
    {
        if(ms<=0||!CMProps.isSinglePlayer()) return;
        if(GameTime.isAdvancing()) { GameTime.requestAdvance(ms); return; }
        final boolean running=!GameTime.isStopped();
        if(running) GameTime.pause();
        GameTime.requestAdvance(ms,null,() -> { if(running) GameTime.resume(); });
    }

    public static long stepMillis(final Environmental traveler, final Room from, final Room to, final Exit exit)
    {
        Ability A=property(exit,"Prop_TravelTime");
        if(A==null) A=property(to,"Prop_TravelTime");
        if(A==null&&to!=null) A=property(to.getArea(),"Prop_TravelTime");
        if(A==null) A=property(from,"Prop_TravelTime");
        if(A==null&&from!=null) A=property(from.getArea(),"Prop_TravelTime");
        if(A==null) return 0;
        double minutes=Math.max(0,CMParms.getParmDouble(A.text(),"TIME",0));
        if(traveler instanceof Item&&traveler instanceof Rideable)
            minutes*=Math.max(0,CMParms.getParmDouble(A.text(),"VEHICLE",0.7));
        else if(traveler instanceof MOB&&((MOB)traveler).riding()!=null)
            minutes*=Math.max(0,CMParms.getParmDouble(A.text(),((MOB)traveler).riding() instanceof Item?"VEHICLE":"MOUNTED",((MOB)traveler).riding() instanceof Item?0.7:0.5));
        else if(traveler instanceof MOB&&traveler instanceof Rideable&&((Rideable)traveler).numRiders()>0)
            minutes*=Math.max(0,CMParms.getParmDouble(A.text(),"MOUNTED",0.5));
        final TimeClock C=CMLib.time().localClock(to==null?from:to);
        if(C!=null&&C.getTODCode()==TimeClock.TimeOfDay.NIGHT) minutes*=Math.max(0,CMParms.getParmDouble(A.text(),"NIGHT",1.5));
        final Ability road=road(to);
        if(road!=null) minutes*=Math.max(0,CMParms.getParmDouble(road.text(),"TIME",1));
        return Math.round(minutes*CMProps.getMillisPerMudHour()/60.0);
    }

    public static void moved(final Environmental traveler, final Room from, final Room to, final Exit exit)
    {
        if(from!=to&&hasPlayer(traveler))
            elapse(stepMillis(traveler,from,to,exit));
    }

    public static Ability road(final Room R)
    {
        if(R==null) return null;
        final Ability A=property(R,"Prop_Road");
        return A==null?property(R.getArea(),"Prop_Road"):A;
    }

    public static boolean drivable(final Room R, final Item vehicle)
    {
        final Ability A=road(R);
        if(A!=null)
        {
            for(final String name : CMParms.parseCommas(option(A.text(),"NOTFOR",""),true))
                if(vehicle!=null&&(name.equalsIgnoreCase(vehicle.ID())||CMLib.english().containsString(vehicle.Name(),name))) return false;
            return true;
        }
        return CMLib.flags().isDrivableRoom(R);
    }

    public static Boardable vessel(final String name)
    {
        Boardable found=null;
        for(final Enumeration<Boardable> e=CMLib.map().ships();e.hasMoreElements();)
        {
            final Boardable S=e.nextElement();
            if(S==null) continue;
            if(S.Name().equalsIgnoreCase(name)||S.getArea().Name().equalsIgnoreCase(name)) return S;
            if(found==null&&CMLib.english().containsString(S.Name(),name)) found=S;
        }
        return found;
    }

    public static boolean knownPort(final Room R, final MOB who)
    {
        final Ability P=property(R,"Prop_Port");
        if(P==null) return false;
        if(who==null||CMSecurity.isAllowed(who,who.location(),CMSecurity.SecFlag.CMDROOMS)) return true;
        final String mask=Organizations.parameterMask(option(P.text(),"KNOWNMASK",""));
        return CMLib.flags().canAccess(who,R)&&(mask.length()>0?CMLib.masking().maskCheck(mask,who,true):!CMParms.getParmBool(P.text(),"HIDDEN",false));
    }

    public static String label(final String destination)
    {
        final Room R=destination(destination,null);
        if(R==null) return "an unknown stop";
        final Ability P=property(R,"Prop_Port");
        return P==null?R.displayText():option(P.text(),"NAME",R.displayText());
    }

    public static Room destination(final String name, final MOB who)
    {
        final Room exact=CMLib.map().getRoom(name);
        if(exact!=null) return who==null||(CMLib.flags().canAccess(who,exact)&&(property(exact,"Prop_Port")==null||knownPort(exact,who)))?exact:null;
        if(who!=null&&(CMath.isInteger(name)||name.toUpperCase().startsWith("CHART ")))
        {
            final int index=CMath.s_int(name.toUpperCase().startsWith("CHART ")?name.substring(6).trim():name)-1;
            final Ability chart=who.fetchAbility("Skill_SeaCharting");
            final List<String> points=chart==null?Collections.<String>emptyList():CMParms.parseSemicolons(chart.text(),true);
            return index>=0&&index<points.size()?destination(points.get(index),who):null;
        }
        boolean namedPort=false;
        for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
        {
            final Room R=e.nextElement();
            final Ability P=property(R,"Prop_Port");
            if(P!=null&&option(P.text(),"NAME",R.displayText()).equalsIgnoreCase(name))
            { namedPort=true; if(knownPort(R,who)) return R; }
        }
        if(namedPort) return null;
        final Area A=CMLib.map().findArea(name);
        if(A!=null) for(final Enumeration<Room> e=A.getProperMap();e.hasMoreElements();)
        {
            final Room R=e.nextElement();
            if(who==null||CMLib.flags().canAccess(who,R)) return R;
        }
        return null;
    }

    private static boolean terrain(final Room R, final Rideable vehicle)
    {
        if(vehicle==null) return true;
        final Rideable.Basis basis=vehicle instanceof NavigableItem?((NavigableItem)vehicle).navBasis():vehicle.rideBasis();
        if(basis==Rideable.Basis.WATER_BASED) return CMLib.flags().isWateryRoom(R)||R.domainType()==Room.DOMAIN_OUTDOORS_SEAPORT;
        if(basis==Rideable.Basis.LAND_BASED||basis==Rideable.Basis.WAGON) return drivable(R,vehicle instanceof Item?(Item)vehicle:null);
        if(basis==Rideable.Basis.AIR_FLYING) return (R.domainType()&Room.INDOORS)==0;
        return true;
    }

    /** Breadth-first route over open, traversable exits. Null means unreachable. */
    public static List<Integer> route(final Room from, final Room to, final Rideable vehicle)
    {
        if(from==null||to==null) return null;
        final Map<Room,Room> previous=new IdentityHashMap<Room,Room>();
        final Map<Room,Integer> direction=new IdentityHashMap<Room,Integer>();
        final Deque<Room> queue=new ArrayDeque<Room>();
        previous.put(from,null); queue.add(from);
        while(!queue.isEmpty()&&previous.size()<=10000)
        {
            final Room R=queue.remove();
            if(R==to)
            {
                final LinkedList<Integer> path=new LinkedList<Integer>();
                for(Room at=to;previous.get(at)!=null;at=previous.get(at)) path.addFirst(direction.get(at));
                return path;
            }
            for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
            {
                final Room next=R.getRoomInDir(d); final Exit E=R.getExitInDir(d);
                if(next!=null&&E!=null&&E.isOpen()&&!previous.containsKey(next)&&terrain(next,vehicle))
                { previous.put(next,R); direction.put(next,Integer.valueOf(d)); queue.add(next); }
            }
        }
        return null;
    }

    public static String sail(final NavigableItem ship, final String target, final MOB who)
    {
        final List<Integer> course;
        if(target.toUpperCase().startsWith("TO "))
        {
            final Room R=destination(target.substring(3).trim(),who);
            if(R==null) return "Unknown or inaccessible destination.";
            course=route(CMLib.map().roomLocation(ship),R,ship);
            if(course==null) return "There is no navigable route to that destination.";
        }
        else
        {
            course=new ArrayList<Integer>();
            for(final String word : CMParms.parse(target))
            {
                final int d=CMLib.directions().getGoodDirectionCode(word);
                if(d<0) return "Unknown direction: "+word;
                course.add(Integer.valueOf(d));
            }
        }
        if(course.isEmpty()) return "The vessel is already there.";
        course.add(Integer.valueOf(-1)); ship.setCurrentCourse(course); ship.setAnchorDown(false);
        return null;
    }

    public static String value(final String function, final Environmental object)
    {
        if(function.equals("RIDING")) return object instanceof Rider&&((Rider)object).riding()!=null?((Rider)object).riding().Name():"";
        if(function.equals("ABOARD"))
        {
            final Area A=CMLib.map().areaLocation(object);
            return A instanceof Boardable?((Boardable)A).getBoardableItem().Name():"";
        }
        if(function.equals("RIDERS")) return object instanceof Rideable?""+((Rideable)object).numRiders():"0";
        if(function.equals("VESSELROOM")) return CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(object));
        if(function.equals("ISDOCKED")) return ""+(object instanceof Boardable&&((Boardable)object).getIsDocked()!=null);
        if(function.equals("SPEED")) return object instanceof NavigableItem?""+((NavigableItem)object).getMaxSpeed():"0";
        if(function.equals("HULL")) return object instanceof Item?""+((Item)object).usesRemaining():"0";
        if(function.equals("ISANCHORED")) return ""+(object instanceof NavigableItem&&((NavigableItem)object).isAnchorDown());
        if(function.equals("COURSE")&&object instanceof NavigableItem)
        {
            final List<String> names=new ArrayList<String>();
            for(final Integer d : ((NavigableItem)object).getCurrentCourse())
                if(d.intValue()>=0) names.add(CMLib.directions().getDirectionName(d.intValue()&255));
            return String.join(" ",names);
        }
        return "";
    }

    public static String hitch(final Rider item, final Rideable mount)
    {
        if(item==null) return "No such item to hitch.";
        if(mount==null) { item.setRiding(null); return null; }
        if(CMLib.map().roomLocation(item)!=CMLib.map().roomLocation(mount)) return "They must be in the same room.";
        final Set<Rideable> seen=Collections.newSetFromMap(new IdentityHashMap<Rideable,Boolean>());
        for(Rideable R=mount;R!=null;R=R.riding())
            if(R==item||!seen.add(R)) return "Hitching would form a riding loop.";
        item.setRiding(mount);
        return null;
    }

    /** Validate every passenger before moving any of them. */
    public static boolean transfer(final MOB leader, final List<MOB> passengers, final Room to, final boolean look)
    {
        return transfer(leader,passengers,to,look,() -> true);
    }

    public static boolean transfer(final MOB leader, final List<MOB> passengers, final Room to, final boolean look,
                                   final java.util.function.BooleanSupplier commit)
    {
        if(to==null||passengers.isEmpty()) return false;
        final List<CMMsg> messages=new ArrayList<CMMsg>();
        final List<CMMsg> leaves=new ArrayList<CMMsg>();
        for(final MOB M : passengers)
        {
            final CMMsg msg=CMClass.getMsg(M,to,null,CMMsg.MSG_ENTER,null);
            if(!CMLib.flags().canAccess(M,to)||!M.okMessage(M,msg)||!to.okMessage(M,msg)) return false;
            final CMMsg leave=CMClass.getMsg(M,M.location(),null,CMMsg.MSG_LEAVE,null);
            if(M.location()!=null&&M.location()!=to&&!M.location().okMessage(M,leave)) return false;
            leaves.add(leave);
            messages.add(msg);
        }
        if(!commit.getAsBoolean()) return false;
        entering(leader,leader.location(),to);
        for(int i=0;i<passengers.size();i++)
        {
            final MOB M=passengers.get(i); final Room from=M.location();
            if(from==to) continue;
            if(from!=null) from.send(M,leaves.get(i));
            to.bringMobHere(M,false); to.send(M,messages.get(i)); ScriptEvents.moved(M,from,to);
            if(look) CMLib.commands().postLook(M,true);
        }
        return true;
    }

    /** One fare convention: leader pays the group, or each character pays their share. */
    public static boolean fare(final MOB leader, final List<MOB> passengers, final double price, final boolean perHead,
                               final boolean each, final double pets, final double cargo, final boolean collect)
    {
        final Map<MOB,Double> charges=new LinkedHashMap<MOB,Double>();
        double total=0;
        for(final MOB M : passengers)
        {
            double amount=perHead?price:0;
            if(!M.isPlayer()) amount=pets;
            amount+=cargo*Math.ceil(Math.max(0,M.phyStats().weight()-M.basePhyStats().weight())/100.0);
            if(each&&M.isPlayer()) charges.put(M,Double.valueOf(Math.max(0,amount)));
            else total+=amount;
        }
        if(!perHead) total+=price;
        if(total>0||!each) charges.put(leader,Double.valueOf(total+(charges.containsKey(leader)?charges.get(leader).doubleValue():0)));
        for(final Map.Entry<MOB,Double> entry : charges.entrySet())
            if(CMLib.beanCounter().getTotalAbsoluteNativeValue(entry.getKey())<entry.getValue().doubleValue())
            { leader.tell(entry.getKey().Name()+" cannot afford the fare."); return false; }
        if(collect) for(final Map.Entry<MOB,Double> entry : charges.entrySet()) CMLib.beanCounter().subtractMoney(entry.getKey(),entry.getValue().doubleValue());
        return true;
    }
}
