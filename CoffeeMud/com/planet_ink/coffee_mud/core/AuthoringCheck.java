package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.mozilla.javascript.Context;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Races.interfaces.Race;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Read-only validation of live authoring assets and their references. Never assigns IDs or runs scripts. */
public final class AuthoringCheck
{
    private AuthoringCheck() {}
    public static List<String> checkList(final String key,final String value)
    {
        final List<String> out=new ArrayList<String>();
        if(value.isEmpty()) {out.add(key+" is empty; remove it to use the fallback");return out;}
        final int expected=key.equals("HEALTH_CHART")||key.equals("HEALTHCHART")?11:key.equals("WEAPON_HIT_DESCS")||key.equals("WEAPON_MISS_DESCS")?6:0;
        if(expected>0&&Vocabulary.choices(value).length!=expected) out.add(key+" needs "+expected+" comma-separated entries");
        if(key.equals("DAMAGE_WORDS")&&value.indexOf(';')>=0)
            for(final String row:Scenery.statements(value))
            {
                final List<String> cells=CMParms.parseCommas(row,false);
                if(cells.size()<2||!cells.get(0).trim().equalsIgnoreCase("ALL")&&!Arrays.asList(Weapon.TYPE_DESCS).contains(cells.get(0).trim().toUpperCase())) out.add("DAMAGE_WORDS row needs a weapon damage type or ALL followed by severity words: "+row);
            }
        return out;
    }
    private static void add(final List<WorldCheck.Finding> out,final String part,final String where,final String error)
    { out.add(new WorldCheck.Finding(WorldCheck.Severity.ERROR,part,where+": "+error)); }
    private static int lineOf(final String text,final int offset)
    {int line=1;for(int i=0;i<Math.min(offset,text.length());i++)if(text.charAt(i)=='\n')line++;return line;}
    private static int keyLine(final String text,final String key)
    {final Matcher m=Pattern.compile("(?m)^\\s*"+Pattern.quote(key)+"\\s*[=:]").matcher(text);return m.find()?lineOf(text,m.start()):1;}
    private static Properties properties(final String path,final String part,final List<WorldCheck.Finding> out)
    {
        final Properties p=new Properties();
        try {p.load(new StringReader(Vocabulary.read(path)));}
        catch(final IOException|IllegalArgumentException e) {add(out,part,"world/"+path+":1","invalid properties: "+e.getMessage());}
        return p;
    }
    private static void text(final List<WorldCheck.Finding> out,final String part,final String path,final String value)
    {for(final String error:TextCheck.checkText(value))add(out,part,path,error);}
    private static void dictionaries(final String path,final List<WorldCheck.Finding> out)
    {
        final Set<String> words=new HashSet<String>();int line=0;
        for(final String raw:Vocabulary.read(path).split("\\r?\\n"))
        {
            line++;final String t=raw.trim();if(t.isEmpty()||t.startsWith("#"))continue;
            final int at=t.indexOf('=');
            if(at<1||t.substring(at+1).trim().isEmpty())add(out,"VOCAB","world/"+path+":"+line,"dictionary line needs WORD=translation");
            else if(!words.add(t.substring(0,at).trim().toUpperCase()))add(out,"VOCAB","world/"+path+":"+line,"duplicate dictionary word "+t.substring(0,at));
        }
    }
    private static void languages(final List<WorldCheck.Finding> out)
    {
        final Set<String> files=new HashSet<String>();
        for(final Enumeration<Ability> e=CMClass.abilities();e.hasMoreElements();)
        {
            final Ability a=e.nextElement();if(!(a instanceof Language)||!a.isGeneric())continue;
            final String path=a.ID(),file=a.getStat("WORDFILE");
            if(!file.isEmpty())
            {
                final String name=file.startsWith("vocab/")?file:"vocab/languages/"+file;
                if(Vocabulary.file(name)==null||!Vocabulary.file(name).isFile())add(out,"VOCAB",path+" WORDFILE","missing file world/"+name);
                else if(files.add(name))dictionaries(name,out);
            }
            for(final String entry:CMParms.parseCommas(a.getStat("INTERPRETS"),true))
            {
                final Matcher m=Pattern.compile("([^()]+)(?:\\((\\d+)\\))?").matcher(entry.trim());
                if(!m.matches()||Languages.language(m.group(1).trim())==null||m.group(2)!=null&&Double.parseDouble(m.group(2))>100)add(out,"VOCAB",path+" INTERPRETS","expected language or language(0..100): "+entry);
            }
            final String script=a.getStat("TRANSLATESCRIPT");
            if(!script.isEmpty())
            {
                final Context cx=JSRuntime.enter(JSRuntime.limitSeconds());
                try {cx.compileString(script,path+" TRANSLATESCRIPT",1,null);}
                catch(final RuntimeException ex){add(out,"VOCAB",path+" TRANSLATESCRIPT",ex.getMessage());}
                finally {Context.exit();}
                if(!CMSecurity.isApprovedJScript(new StringBuffer(script)))out.add(new WorldCheck.Finding(WorldCheck.Severity.WARNING,"VOCAB",path+": TRANSLATESCRIPT is not approved; dictionaries are used until approval"));
            }
        }
    }
    private static void vocabulary(final List<WorldCheck.Finding> out)
    {
        languages(out);
        final String glossary="vocab/glossary.ini";int line=0;
        for(final String raw:Vocabulary.read(glossary).split("\\r?\\n"))
        {
            line++;final String t=raw.trim();if(t.isEmpty()||t.startsWith("#")||t.startsWith(";")||t.toUpperCase().startsWith("NOT:"))continue;
            final int at=t.indexOf('=');final String where="world/"+glossary+":"+line;
            if(at<1){add(out,"VOCAB",where,"expected term=replacement or SCOPE=ALL|HELP|SCORE");continue;}
            final String scope=t.regionMatches(true,0,"SCOPE=",0,6)?t.substring(6).trim():Scenery.option(t.substring(at+1),"SCOPE","ALL");
            if(!Arrays.asList("ALL","HELP","SCORE").contains(scope.toUpperCase()))add(out,"VOCAB",where,"unknown glossary scope "+scope);
            for(final String error:AuthoringOptions.errors(t))add(out,"VOCAB",where,error);
        }
        for(final String path:WorldAssets.paths())if(path.startsWith("vocab/")&&path.endsWith(".ini")&&!path.equals(glossary))
        {
            final Properties p=properties(path,"VOCAB",out);final String body=Vocabulary.read(path);
            for(final String key:p.stringPropertyNames())
            {
                final String where="world/"+path+":"+keyLine(body,key);
                if(path.startsWith("vocab/help/"))text(out,"VOCAB",where,p.getProperty(key));
                else for(final String error:checkListIfKnown(key,p.getProperty(key)))add(out,"VOCAB",where,error);
            }
        }
    }
    private static List<String> checkListIfKnown(final String key,final String value)
    {return AuthoringSchema.keys("Prop_LocalLists").contains(key)&&!key.equals("LOAD")?checkList(key,value):Collections.<String>emptyList();}
    private static void lore(final List<WorldCheck.Finding> out)
    {
        final Map<String,String> keys=new HashMap<String,String>();
        for(final String path:WorldAssets.paths())if(path.startsWith("lore/")&&path.endsWith(".ini"))
        {
            final String body=Vocabulary.read(path);final Properties p=properties(path,"LORE",out);
            for(final String key:p.stringPropertyNames())
            {
                final String where="world/"+path+":"+keyLine(body,key),old=keys.put(key.toUpperCase(),where);
                if(old!=null)add(out,"LORE",where,"duplicate lore ID "+key+" also defined at "+old);
                final LoreBook.Entry entry=new LoreBook.Entry(key,p.getProperty(key));
                final String file=entry.title.startsWith("FILE=")?entry.title.substring(5):entry.text.startsWith("FILE=")?entry.text.substring(5):"";
                if(!file.isEmpty())
                {
                    final File root=Vocabulary.file("lore"),f=Vocabulary.file("lore/"+file);
                    if(f==null||!f.toPath().startsWith(root.toPath())||!f.isFile())add(out,"LORE",where,"missing lore file "+file);
                    else text(out,"LORE","world/lore/"+file+":1",Vocabulary.read("lore/"+file));
                }
                else text(out,"LORE",where,entry.text);
            }
        }
    }
    private static List<Physical> hosts(final Area only)
    {
        final Set<Physical> seen=Collections.newSetFromMap(new IdentityHashMap<Physical,Boolean>());
        for(final Enumeration<Room> e=only==null?CMLib.map().rooms():only.getProperMap();e.hasMoreElements();)
        {
            final Room r=e.nextElement();seen.add(r);seen.add(r.getArea());
            for(int d=0;d<Directions.NUM_DIRECTIONS();d++)if(r.getRawExit(d)!=null)seen.add(r.getRawExit(d));
            for(final Enumeration<Item> i=r.items();i.hasMoreElements();)seen.add(i.nextElement());
            for(final Enumeration<MOB> m=r.inhabitants();m.hasMoreElements();){final MOB mob=m.nextElement();if(!mob.isMonster())continue;seen.add(mob);for(final Enumeration<Item> i=mob.items();i.hasMoreElements();)seen.add(i.nextElement());}
        }
        if(only==null)
        {
            for(final String n:CMLib.catalog().getCatalogMobNames()){final MOB m=CMLib.catalog().getCatalogMob(n);if(m!=null)seen.add(m);}
            for(final String n:CMLib.catalog().getCatalogItemNames()){final Item i=CMLib.catalog().getCatalogItem(n);if(i!=null)seen.add(i);}
        }
        return new ArrayList<Physical>(seen);
    }
    private static String domain(final String id)
    {if(id.contains("Language")||id.equals("Prop_Monolingual")||id.equals("Prop_LocalLists")||id.equals("Prop_WeaponMessages"))return "VOCAB";if(id.equals("Prop_Lore"))return "LORE";if(id.equals("Prop_Journey")||id.equals("Prop_Waypoint")||id.equals("Prop_Port")||id.equals("Prop_Road")||id.equals("Prop_TravelTime")||id.equals("Prop_HelmRestrict"))return "ROUTES";return "TEMPLATES";}
    private static void route(final List<WorldCheck.Finding> out,final String where,final String xml)
    {
        final Matcher stops=Pattern.compile("(?is)<STOP(\\d+)>\\s*([^<]+)\\s*</STOP\\1>").matcher(xml);int count=0;
        while(stops.find()){count++;final String id=stops.group(2).trim();if(CMLib.map().getRoom(id)==null)add(out,"ROUTES",where+":"+lineOf(xml,stops.start()),"no route stop room "+id);}
        if(count<2)add(out,"ROUTES",where+":1","route needs at least two STOPn rooms");
    }
    public static List<WorldCheck.Finding> run(final String part,final Area only)
    {
        final List<WorldCheck.Finding> out=new ArrayList<WorldCheck.Finding>();
        if(only==null)
        {
            if(part.equals("VOCAB"))vocabulary(out);
            if(part.equals("LORE"))lore(out);
            if(part.equals("TEMPLATES"))
            {
                for(final String setting:new String[]{"ROOMHEADER","ROOMFOOTER"})text(out,part,"world.ini "+setting,CMProps.instance().getStr(setting));
                if(Vocabulary.file("score.txt").isFile())text(out,part,"world/score.txt:1",Vocabulary.read("score.txt"));
                for(final String path:WorldAssets.paths())if(path.startsWith("text/"))text(out,part,"world/"+path+":1",Vocabulary.read(path));
                for(final Enumeration<Race> e=CMClass.races();e.hasMoreElements();){final Race race=e.nextElement();if(race.isGeneric()&&!race.getStat("HEALTHCHART").isEmpty())for(final String error:checkList("HEALTHCHART",race.getStat("HEALTHCHART")))add(out,part,race.ID(),error);}
            }
            if(part.equals("ROUTES"))
            {
                final File dir=Vocabulary.file("routes");final File[] files=dir==null?null:dir.listFiles((d,n)->n.endsWith(".xml"));
                if(files!=null)for(final File file:files)route(out,"world/routes/"+file.getName(),Vocabulary.read("routes/"+file.getName()));
            }
        }
        final Map<String,String> ids=new HashMap<String,String>(),names=new HashMap<String,String>();
        for(final Physical host:hosts(only))
        {
            final Room r=CMLib.map().roomLocation(host);final String where=(r==null?host.Name():CMLib.map().getExtendedRoomID(r)+" / "+host.Name());
            for(final Enumeration<Ability> e=host.effects();e.hasMoreElements();)
            {
                final Ability a=e.nextElement();if(!a.isSavable()||a.canBeUninvoked())continue;
                if(part.equals(domain(a.ID())))
                {
                    for(final String error:AuthoringSchema.check(a.ID(),a.text()))add(out,part,where+" / "+a.ID(),error);
                    if(!AuthoringSchema.keys(a.ID()).isEmpty())for(final String key:PropCheck.unknownKeys(a.ID(),a.text()))add(out,part,where+" / "+a.ID(),"unknown option "+key);
                }
                if(part.equals("IDS")&&(!a.ID().equals("Prop_Waypoint")||r!=null))
                {
                    final String id=Scenery.option(a.text(),"ID","");if(id.isEmpty()||!AuthoringSchema.keys(a.ID()).contains("ALIASES"))continue;
                    final String network=a.ID().equals("Prop_Waypoint")?Waypoints.network(a).toUpperCase()+":":"";
                    final String old=ids.put(a.ID()+":"+network+id.toUpperCase(),where);
                    if(old!=null)out.add(new WorldCheck.Finding(a.ID().equals("Prop_Waypoint")?WorldCheck.Severity.ERROR:WorldCheck.Severity.WARNING,part,where+": duplicate "+a.ID()+" ID "+id+" at "+old+"; copied sources share saved state"));
                }
                if(part.equals("ROUTES")&&a.ID().equals("Prop_Waypoint")&&r!=null)
                {
                    final String name=Waypoints.network(a)+":"+Scenery.option(a.text(),"NAME",host.Name()),old=names.put(name.toUpperCase(),where);
                    if(old!=null)add(out,part,where,"ambiguous waypoint name "+name+" also at "+old);
                }
            }
            if(part.equals("ROUTES")&&host instanceof PhysicalAgent)for(final Enumeration<Behavior> e=((PhysicalAgent)host).behaviors();e.hasMoreElements();)
            {
                final Behavior b=e.nextElement();
                if(b.ID().equals("TransportLine"))for(final String stop:Scenery.statements(Scenery.option(b.getParms(),"STOPS","")))if(!stop.isEmpty()&&Travel.destination(stop,null)==null)add(out,part,where+" / TransportLine","no stop room or port "+stop);
                if(b.ID().equals("MovingRoom"))
                {
                    final String route=Scenery.option(b.getParms(),"ROUTE","");
                    if(route.startsWith("<"))route(out,where+" / MovingRoom",route);
                    else if(!route.isEmpty()){final String path=route.startsWith("routes/")?route:"routes/"+route;if(Vocabulary.file(path)==null||!Vocabulary.file(path).isFile())add(out,part,where+" / MovingRoom","missing route file "+path);}
                }
            }
        }
        return out;
    }
}
