package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Viewer-specific text. Rendering never rewrites the author's text. */
public final class StoryText
{
    private StoryText() {}
    private static volatile ScriptingEngine variables;
    private static final Map<MOB,Room> firstEntries=Collections.synchronizedMap(new WeakHashMap<MOB,Room>());
    private static final ThreadLocal<Frame> current=new ThreadLocal<Frame>();
    private static final class Frame
    {
        final Map<MOB,Map<PhysicalAgent,Map<String,String>>> results=new IdentityHashMap<MOB,Map<PhysicalAgent,Map<String,String>>>();
        final Set<String> active=new HashSet<String>();
        int calls=0;
        final long deadline=System.nanoTime()+Math.max(1,JSRuntime.limitSeconds())*1000000000L;
    }
    /** whether each entering move is its mover's first visit, noted as the move was allowed */
    private static final Map<CMMsg,Boolean> FIRST_VISITS=Collections.synchronizedMap(new WeakHashMap<CMMsg,Boolean>());
    /**
     * Notes, as an entering move is allowed, whether it is a first visit.
     * @param msg the move
     * @param first true if the mover has not been there
     */
    public static void noteFirstVisit(final CMMsg msg, final boolean first)
    {
        FIRST_VISITS.put(msg,Boolean.valueOf(first)); // each pass: messages are reused
    }
    /**
     * Returns whether a move was its mover's first visit, as noted when it was allowed.
     * @param msg the move
     * @param otherwise the answer when nothing was noted
     * @return true for a first visit
     */
    public static boolean firstVisit(final CMMsg msg, final boolean otherwise)
    {
        final Boolean noted=FIRST_VISITS.remove(msg);
        return (noted==null)?otherwise:noted.booleanValue();
    }
    public static void arriving(final MOB who, final Room room, final boolean first)
    { if(first) firstEntries.put(who,room); else firstEntries.remove(who); }
    public static boolean first(final MOB who, final Room room)
    {
        final TextPreview preview=TextPreview.current();
        if(preview!=null&&preview.visited!=TextPreview.UNSET) return preview.visited==0;
        return who!=null&&firstEntries.get(who)==room;
    }
    public static String variable(final String host, final String key)
    {
        if(variables==null) { variables=(ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine"); variables.setVarScope(""); }
        final String value=variables.getVar(host,key.toUpperCase()); return value==null?"":value;
    }
    public static String questVariable(final String quest, final String key)
    {
        final ScriptingEngine engine=(ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
        engine.setSavable(false); engine.setVarScope(quest);
        return engine.getVar("*",key.toUpperCase());
    }
    public static void setVariable(final MOB who, final String assignment)
    {
        if(assignment==null||assignment.trim().isEmpty()) return;
        final int at=assignment.indexOf('=');
        if(variables==null) { variables=(ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine"); variables.setVarScope(""); }
        variables.setVar(who==null?"WORLD":who.Name(),at<0?assignment:assignment.substring(0,at),"="+(at<0?"1":assignment.substring(at+1)));
    }
    public static String render(final MOB viewer, final Environmental host, final String text)
    {
        if(text==null||text.isEmpty()) return text==null?"":text;
        final Room room=host instanceof Room?(Room)host:CMLib.map().roomLocation(host);
        final Room at=room==null&&viewer!=null?viewer.location():room;
        return render(viewer,host,at==null?host instanceof Area?(Area)host:null:at.getArea(),at,text);
    }
    public static String render(final MOB viewer, final Environmental host, final Area area, final Room room, final String text)
    {
        final boolean own=current.get()==null;
        if(own) current.set(new Frame());
        try
        {
            final String value=text==null?"":text;
            if(!value.startsWith("<VARIES>")) return calls(viewer,host,value);
            return parse(viewer,host,area,room,area==null?value.substring(8):Calendars.seasonBlocks(area,value.substring(8)),0);
        }
        finally { if(own) current.remove(); }
    }
    /** Share a CALL result across the title, description and details of one look. */
    public static <T> T look(final java.util.function.Supplier<T> action)
    {
        final Frame was=current.get();
        if(was!=null) return action.get();
        current.set(new Frame());
        try { return action.get(); }
        finally { if(was==null) current.remove(); else current.set(was); }
    }
    public static boolean withinBudget() { final Frame frame=current.get();return frame==null||frame.calls==0||System.nanoTime()<=frame.deadline; }
    private static String calls(final MOB viewer, final Environmental host, final String text)
    {
        final Matcher matcher=Pattern.compile("<CALL\\s+([^>]+)>",Pattern.CASE_INSENSITIVE).matcher(text);
        final StringBuffer out=new StringBuffer();
        while(matcher.find()) matcher.appendReplacement(out,Matcher.quoteReplacement(call(viewer,host,matcher.group(1))));
        matcher.appendTail(out); return out.toString();
    }
    private static String call(final MOB viewer, final Environmental host, final String command)
    {
        if(!(host instanceof PhysicalAgent)) return "";
        final Frame frame=current.get();
        if(frame==null) return render(viewer,host,"<CALL "+command+">");
        final PhysicalAgent source=(PhysicalAgent)host;
        Map<PhysicalAgent,Map<String,String>> hosts=frame.results.get(viewer);
        if(hosts==null) { hosts=new IdentityHashMap<PhysicalAgent,Map<String,String>>(); frame.results.put(viewer,hosts); }
        Map<String,String> values=hosts.get(source);
        if(values==null) { values=new HashMap<String,String>(); hosts.put(source,values); }
        if(values.containsKey(command)) return values.get(command);
        final String key=System.identityHashCode(viewer)+":"+System.identityHashCode(source)+":"+command;
        if(frame.calls>=32||!withinBudget()||!frame.active.add(key)) return "";
        frame.calls++;
        try
        {
            final int space=command.indexOf(' ');
            final String result=ScriptEvents.callFunction(source,space<0?command:command.substring(0,space),space<0?"":command.substring(space+1),viewer);
            final String value=result==null||!withinBudget()?"":result; values.put(command,value); return value;
        }
        finally { frame.active.remove(key); }
    }
    private static String parse(final MOB viewer, final Environmental host, final Area area, final Room room, final String text, final int depth)
    {
        if(depth>32) return "";
        final StringBuilder out=new StringBuilder(); boolean last=true; int pos=0,cursor=0;
        final Pattern tag=Pattern.compile("<([A-Za-z][A-Za-z0-9]*)(?:\\s+([^>]*))?>");
        final Matcher matcher=tag.matcher(text);
        while(matcher.find(pos))
        {
            final String name=matcher.group(1).toUpperCase(), args=matcher.group(2)==null?"":CMLib.xml().restoreAngleBrackets(matcher.group(2)).trim();
            if(name.equals("CALL")) { out.append(text,cursor,matcher.start()).append(call(viewer,host,args)); cursor=pos=matcher.end(); continue; }
            if(!known(name)) { pos=matcher.end(); continue; }
            // Preserve literal/MXP tags encountered before this conditional.
            final int start=matcher.start(), content=matcher.end();
            out.append(text,cursor,start);
            final Matcher close=Pattern.compile("</?"+Pattern.quote(name)+"(?:\\s+[^>]*)?>",Pattern.CASE_INSENSITIVE).matcher(text);
            close.region(content,text.length()); int nesting=1,end=text.length(),after=text.length();
            while(close.find())
            {
                if(text.charAt(close.start()+1)=='/') nesting--; else nesting++;
                if(nesting==0) { end=close.start(); after=close.end(); break; }
            }
            final boolean yes=name.equals("ELSE")?!last:name.equals("VARIES")?true:test(viewer,host,area,room,name,args);
            if(yes) out.append(parse(viewer,host,area,room,text.substring(content,end),depth+1));
            last=yes; cursor=pos=after;
            // Restart so skipped conditional content cannot be scanned twice.
            matcher.region(pos,text.length());
        }
        if(cursor<text.length()) out.append(text.substring(cursor));
        return out.toString().replace("</VARIES>","");
    }
    public static boolean known(final String name)
    {
        if(name.matches("MASK[0-9]*")) return true;
        if(CMath.s_valueOf(Room.VariationCode.class,name)!=null) return true;
        return Arrays.asList("FULLMOON","NEWMOON","WAXING","WANING","INCOMBAT","DARK","LIT","HOUR","MONTH","VAR","IFVAR","HOLIDAY","WANTED","QUESTWON","QUESTRUNNING","QUESTSTEP","PARTYTATTOO","FIRSTVISIT","INDOORS","OUTDOORS").contains(name);
    }
    public static boolean test(final MOB viewer, final Environmental host, Area area, Room room, final String name, final String args)
    {
        if(room==null&&viewer!=null) room=viewer.location();
        if(area==null&&room!=null) area=room.getArea();
        final TimeClock clock=area==null?CMLib.time().globalClock():area.getTimeObj();
        final TextPreview preview=TextPreview.current();
        final String key=name.toUpperCase();
        if(key.matches("MASK[0-9]*")) return viewer!=null&&CMLib.masking().maskCheck(args,viewer,true);
        if(key.equals("FIRSTVISIT")) return first(viewer,room);
        if(key.equals("INCOMBAT")) return viewer!=null&&viewer.isInCombat();
        if(key.equals("DARK")||key.equals("LIT")) { final boolean dark=room!=null&&CMLib.flags().isInDark(room); return key.equals("DARK")==dark; }
        if(key.equals("INDOORS")||key.equals("OUTDOORS")) return room!=null&&key.equals("INDOORS")==((room.domainType()&Room.INDOORS)!=0);
        if(key.equals("HOLIDAY")) return Holidays.on(unquote(args));
        if(key.equals("WANTED")) return viewer!=null&&CMLib.masking().maskCheck("+WANTED",viewer,true);
        if(key.equals("FULLMOON")||key.equals("NEWMOON")||key.equals("WAXING")||key.equals("WANING"))
        {
            final String phase=clock.getMoonPhase(room).name();
            return key.equals("FULLMOON")?phase.contains("FULL"):key.equals("NEWMOON")?phase.contains("NEW"):phase.startsWith(key.equals("WAXING")?"WAX":"WANE");
        }
        if(key.equals("HOUR")) return range(clock.getHourOfDay(),args);
        if(key.equals("MONTH")) return args.equalsIgnoreCase(clock.getMonthNames()[clock.getMonth()])||range(clock.getMonth()+1,args);
        if(key.equals("VAR")||key.equals("IFVAR")) return variableTest(args);
        if(key.equals("PARTYTATTOO"))
        { if(viewer!=null) for(final MOB M : viewer.getGroupMembers(new HashSet<MOB>())) if(M.findTattoo(unquote(args))!=null) return true; return false; }
        if(key.startsWith("QUEST"))
        {
            final List<String> words=CMParms.parse(args); if(words.isEmpty()) return false;
            final Quest quest=CMLib.quests().findQuest(unquote(words.get(0))); if(quest==null) return false;
            if(key.equals("QUESTWON")) return viewer!=null&&quest.wasWinner(viewer.Name());
            if(key.equals("QUESTRUNNING")) return quest.running();
            return words.size()>1&&quest.running()&&quest.stepNumber()==CMath.s_int(words.get(1));
        }
        final Room.VariationCode code=(Room.VariationCode)CMath.s_valueOf(Room.VariationCode.class,key);
        if(code==null) return false;
        switch(code.c)
        {
        case 'W': return (preview!=null&&preview.weather!=TextPreview.UNSET?preview.weather:area==null?Climate.WEATHER_CLEAR:area.getClimateObj().weatherType(null))==code.num; // the area's weather, as before: indoor text may speak of the rain outside
        case 'C': return (preview!=null&&preview.timeOfDay!=TextPreview.UNSET?preview.timeOfDay:clock.getTODCode().ordinal())==code.num;
        case 'S': return (preview!=null&&preview.season!=TextPreview.UNSET?preview.season:area==null?clock.getSeasonCode().ordinal():Calendars.season(area).counts.ordinal())==code.num;
        case 'T': return room!=null&&Tides.is(room,code.num==1?"HIGH":"LOW");
        case 'M': return viewer!=null&&CMath.bset(viewer.phyStats().disposition(),code.num);
        case 'V': return preview!=null&&preview.visited!=TextPreview.UNSET?preview.visited==1:viewer!=null&&viewer.playerStats()!=null&&viewer.playerStats().hasVisited(room);
        case 'F':
            if(preview!=null&&preview.from!=TextPreview.UNSET) return code.num<0?preview.from<0:preview.from==code.num;
            if(viewer==null||viewer.lastLocation()==null) return code.num<0;
            return code.num<0?CMLib.map().getRoomDir(viewer.lastLocation(),room)<0:viewer.lastLocation().getRoomInDir(Directions.getOpDirectionCode(code.num))==room;
        default: return true;
        }
    }
    public static String unquote(final String text) { return text.replaceAll("^[\"']|[\"']$",""); }
    private static boolean range(final int number, final String spec)
    {
        for(final String value : spec.split(","))
        { final String[] bounds=value.trim().split("-"); if(bounds.length==1&&CMath.isInteger(bounds[0])&&number==CMath.s_int(bounds[0])) return true; if(bounds.length==2&&number>=CMath.s_int(bounds[0])&&number<=CMath.s_int(bounds[1])) return true; }
        return false;
    }
    public static boolean variableTest(final String expression)
    {
        final Matcher m=Pattern.compile("^([A-Za-z0-9_.:-]+)\\s*(?:(==|!=|>=|<=|>|<|EQ|NE|GE|GT|LE|LT|=)\\s*(.*))?$",Pattern.CASE_INSENSITIVE).matcher(expression.trim());
        if(!m.matches()) return false;
        final String value=variable("*",m.group(1)), op=m.group(2);
        if(op==null) return !value.isEmpty()&&!value.equals("0")&&!value.equalsIgnoreCase("false");
        final String comparator=op.equals("=")||op.equalsIgnoreCase("EQ")?"==":op.equalsIgnoreCase("NE")?"!=":op.equalsIgnoreCase("GE")?">=":op.equalsIgnoreCase("LE")?"<=":op.equalsIgnoreCase("GT")?">":op.equalsIgnoreCase("LT")?"<":op;
        return ScriptLib.compare(value,comparator,unquote(m.group(3)));
    }
}
