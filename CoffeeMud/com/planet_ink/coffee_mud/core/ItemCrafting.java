package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.util.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.ItemModifierRules.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Controlled game-thread item transactions. Preview/preparation never draw random values. */
public final class ItemCrafting
{
    private ItemCrafting() { }
    private static volatile boolean critical=false;
    private static final ThreadLocal<Boolean> busy=new ThreadLocal<Boolean>();
    public static boolean inCommit(){return critical;}
    private static void settling()throws IOException
    {ItemModifiers.gameThread();ItemModifiers.require();if(Boolean.TRUE.equals(busy.get()))throw new IOException("item transaction observer cannot start another transaction");}
    private static void writable()throws IOException
    {settling();if(!Boolean.TRUE.equals(OptionalModules.setting(ItemModifiers.MODULE,"allow_crafting")))throw new IOException("crafting is disabled by this campaign");}
    private static JSONObject ledger(final MOB who)throws IOException
    {
        final JSONObject state=OptionalModules.get(ItemModifiers.MODULE,OptionalModules.character(who));
        if(number(state,"settled")>number(state,"serial"))throw new IOException("invalid craft settlement counter");
        final JSONObject receipts=field(state,"receipts"),pending=field(state,"pending");
        for(final String key:receipts.keySet())
        {try{final long id=Long.parseLong(key);if(!key.equals(Long.toString(id))||id<1||id>number(state,"settled")||!(receipts.get(key) instanceof JSONObject))throw new IOException("invalid craft receipt index");}catch(final NumberFormatException e){throw new IOException("invalid craft receipt index");}}
        if(!pending.isEmpty())
        {
            ModuleDefinitions.keys(pending,"craft request","serial","actor","item","operation","selector","item_hash","room","costs","affix");
            final int id=ModuleDefinitions.integer(pending.get("serial"),"request serial");
            if(id!=number(state,"serial")||id<=number(state,"settled"))throw new IOException("pending craft counter differs from ledger");
            for(final String key:Arrays.asList("actor","item","selector","item_hash","room"))ModuleDefinitions.string(pending.get(key),key);
            ItemModifierRules.checkPin(pending.get("operation"));ModuleDefinitions.object(pending.get("costs"),"material sources");
        }
        return state;
    }
    private static JSONObject field(final JSONObject ledger,final String name)throws IOException
    {final String s=(String)ledger.get(name);return s.isEmpty()?new JSONObject():ModuleDefinitions.parse(s);}
    private static long number(final JSONObject o,final String name){return ((Number)o.get(name)).longValue();}
    private static void save(final MOB who,final JSONObject data)throws IOException{OptionalModules.put(ItemModifiers.MODULE,OptionalModules.character(who),data);}
    private static String fingerprint(final Item item)throws IOException
    {final JSONObject d=new JSONObject();d.put("class",item.ID());d.put("text",item.text());d.put("weight",Long.valueOf(item.basePhyStats().weight()));d.put("value",Long.valueOf(item.baseGoldValue()));d.put("material",Long.valueOf(item.material()));d.put("uses",Long.valueOf(item.usesRemaining()));d.put("level",Long.valueOf(item.basePhyStats().level()));return ItemModifierRules.hash(d);}
    private static final class Cost
    {
        final Item item;final int amount,weight,value;final String name,display,description;
        Cost(final Item item,final int amount){this.item=item;this.amount=amount;weight=item.basePhyStats().weight();value=item.baseGoldValue();name=item.Name();display=item.displayText();description=item.description();}
        /** What is left of a partly spent pile. A name that shows its weight ("an 8# iron ore bundle") is made again for the new weight. */
        void spend()
        {
            item.basePhyStats().setWeight(weight-amount);item.setBaseValue((int)(((long)value*(weight-amount))/weight));
            if(name.matches("(?s).*\\b"+weight+"#.*"))CMLib.materials().adjustResourceName(item);item.recoverPhyStats();
        }
        void restore(){item.basePhyStats().setWeight(weight);item.setBaseValue(value);item.setName(name);item.setDisplayText(display);item.setDescription(description);item.recoverPhyStats();}
    }
    private static final class Plan
    {
        final Item item;final JSONObject data;final Definition op;final String selector;final List<Cost> costs;
        Plan(final Item item,final JSONObject data,final Definition op,final String selector,final List<Cost> costs){this.item=item;this.data=data;this.op=op;this.selector=selector;this.costs=costs;}
        String describe(){return op.name+" / "+op.string("action")+" on "+item.Name()+"; cost "+op.number("units")+" weight unit(s) of plain "+op.string("material")+" at "+op.string("station")+". No inputs spent or random outcomes drawn.";}
    }
    private static void custody(final MOB who,final Item item)throws IOException
    {if(who==null||who.amDead()||who.amDestroyed()||who.location()==null||who.isInCombat())throw new IOException("craft outside combat, alive in a room");if(item==null||item.amDestroyed()||item.owner()!=who||item.container()!=null||item.rawWornCode()!=Wearable.IN_INVENTORY)throw new IOException("craft an unworn, directly carried item you own");}
    private static boolean plain(final MOB who,final Item item,final int material)
    {
        if(item==null||item.amDestroyed()||!item.ID().equals("GenResource")||!(item instanceof RawMaterial)||item.material()!=material||item.owner()!=who||item.container()!=null||item.rawWornCode()!=Wearable.IN_INVENTORY||item.basePhyStats().weight()<1||item.numBehaviors()>0||item.numScripts()>0)return false;
        for(final Enumeration<Ability> e=item.effects();e.hasMoreElements();)if(!e.nextElement().ID().equals("ExtraData"))return false;return true;
    }
    private static Plan plan(final MOB who,final Item item,final Definition op,final String selector)throws IOException
    {
        ItemModifiers.require();if(!Boolean.TRUE.equals(OptionalModules.setting(ItemModifiers.MODULE,"allow_crafting")))throw new IOException("crafting is disabled by this campaign");custody(who,item);if(!Workstations.has(who.location(),op.string("station")))throw new IOException("required workstation: "+op.string("station"));
        final JSONObject data=ItemModifiers.data(item);ItemModifierRules.validate(data,op,selector,ItemModifiers.definitions(),item.basePhyStats().level());
        if(op.string("action").equals("add")&&ItemModifierRules.modifiers(data).size()>=ItemModifiers.limit())throw new IOException("campaign modifier limit reached");
        final int material=RawMaterial.CODES.FIND_CaseSensitive(op.string("material"));if(material<0)throw new IOException("unknown material "+op.string("material"));
        int remaining=op.number("units");final List<Cost> costs=new ArrayList<Cost>();
        for(final Enumeration<Item> e=who.items();e.hasMoreElements()&&remaining>0;){final Item i=e.nextElement();if(i!=item&&plain(who,i,material)){final int used=Math.min(remaining,i.basePhyStats().weight());costs.add(new Cost(i,used));remaining-=used;}}
        if(remaining>0)throw new IOException("need "+op.number("units")+" weight unit(s) of plain "+op.string("material")+" in your inventory");if(costs.size()>8)throw new IOException("combine materials into at most eight plain piles first");return new Plan(item,data,op,selector,costs);
    }
    private static String selector(final JSONObject data,final Definition op,final String input)throws IOException
    {
        if(op.string("action").equals("add"))return ModuleDefinitions.id(input);
        final int index;try{index=Integer.parseInt(input);}catch(final NumberFormatException e){throw new IOException("choose a modifier number shown by ITEMMODS");}
        final List<JSONObject> mods=ItemModifierRules.modifiers(data);if(index<1||index>mods.size())throw new IOException("no such modifier number");return (String)mods.get(index-1).get("uid");
    }
    public static String preview(final MOB who,final String operation,final Item item,final String input)throws IOException
    {
        final Definition op=ItemModifiers.definitions().find(operation,0,"operation");final String choice=selector(ItemModifiers.data(item),op,input);final Plan plan=plan(who,item,op,choice);
        String ranges="";if(op.string("action").equals("add"))ranges=ModuleDefinitions.json((JSONObject)ItemModifiers.definitions().find(choice,0,"affix").raw.get("rolls"));
        else if(op.string("action").equals("reroll"))for(final JSONObject m:ItemModifierRules.modifiers(plan.data))if(m.get("uid").equals(choice))ranges=ModuleDefinitions.json((JSONObject)ItemModifiers.definitions().pinned(m.get("pin"),"affix").raw.get("rolls"));
        return plan.describe()+(ranges.isEmpty()?"":"\nRoll ranges: "+ranges);
    }
    public static String operations()throws IOException
    {
        ItemModifiers.require();final Library lib=ItemModifiers.definitions();final StringBuilder out=new StringBuilder();
        for(final Definition op:lib.definitions.values())if(op.kind.equals("operation")&&lib.find(op.id,0,"operation")==op)
            out.append(op.id).append(" - ").append(op.name).append(" (").append(op.string("action")).append("); ").append(op.number("units")).append(' ').append(op.string("material")).append("; station ").append(op.string("station")).append('\n');
        return out.length()==0?"No modifier crafting operations are installed.":out.toString();
    }
    public static String pool(final Item item)throws IOException
    {
        ItemModifiers.require();final JSONObject data=ItemModifiers.data(item);final Library lib=ItemModifiers.definitions();final Definition base=lib.pinned(data.get("base"),"base");
        final StringBuilder out=new StringBuilder("Affix pool for "+item.Name()+"; PREVIEW also checks the chosen operation, station and materials.\n");
        for(final String id:base.list("pool"))
        {
            final Definition a=lib.find(id,0,"affix");String reason=ItemModifierRules.ineligible(data,a,item.basePhyStats().level());
            if(reason.isEmpty()&&ItemModifierRules.modifiers(data).size()>=ItemModifiers.limit())reason="campaign modifier limit reached";
            out.append(id).append(" - ").append(a.name).append("; ").append(a.string("position")).append(" tier ").append(a.number("tier")).append("; group ").append(a.string("group")).append("; ").append(reason.isEmpty()?"eligible for addition":reason).append("; rolls ").append(ModuleDefinitions.json((JSONObject)a.raw.get("rolls"))).append('\n');
        }
        return out.toString();
    }
    private static JSONObject costs(final List<Cost> costs,final boolean identify)throws IOException
    {
        final JSONObject result=new JSONObject();
        for(final Cost c:costs){final String id=identify?InstanceIds.reference(c.item):InstanceIds.PREFIX+InstanceIds.existing(c.item);final JSONObject row=new JSONObject();row.put("units",Long.valueOf(c.amount));row.put("hash",fingerprint(c.item));result.put(id,row);}return result;
    }
    public static String prepare(final MOB who,final String operation,final Item item,final String input)throws IOException
    {
        writable();final Definition op=ItemModifiers.definitions().find(operation,0,"operation");final String choice=selector(ItemModifiers.data(item),op,input);final Plan plan=plan(who,item,op,choice);final JSONObject state=ledger(who);
        if(!((String)state.get("pending")).isEmpty())throw new IOException("a request is pending; commit or cancel it first");final long serial=number(state,"serial");if(serial>=1000000000)throw new IOException("craft request counter is full");
        final JSONObject pending=new JSONObject();pending.put("serial",Long.valueOf(serial+1));pending.put("actor",InstanceIds.reference(who));pending.put("item",plan.data.get("item"));pending.put("operation",ItemModifierRules.pin(op));pending.put("selector",choice);
        pending.put("item_hash",fingerprint(item));pending.put("room",InstanceIds.reference(who.location()));pending.put("costs",costs(plan.costs,true));
        if(op.string("action").equals("add"))pending.put("affix",ItemModifierRules.pin(ItemModifiers.definitions().find(choice,0,"affix")));
        final String encoded=ModuleDefinitions.json(pending);if(encoded.length()>4096)throw new IOException("request exceeds storage limit");state.put("serial",Long.valueOf(serial+1));state.put("pending",encoded);save(who,state);
        return "Prepared craft #"+(serial+1)+": "+plan.describe()+"\nMODCRAFT COMMIT "+(serial+1)+" settles it; MODCRAFT CANCEL "+(serial+1)+" discards it. Preparation has no random outcome to shop around for.";
    }
    public static String status(final MOB who)throws IOException
    {
        ItemModifiers.require();if(InstanceIds.existing(who).isEmpty())return "No pending item craft or receipts.";final JSONObject state=ledger(who);
        final JSONObject pending=field(state,"pending");return pending.isEmpty()?"No pending item craft. Last request: "+state.get("serial")+"; MODCRAFT RECEIPT [number] inspects retained receipts.":"Pending craft #"+pending.get("serial")+": "+((JSONObject)pending.get("operation")).get("id")+". Commit rechecks custody, item state, material piles, station and pins; it may refuse a stale request.";
    }
    public static String receipt(final MOB who,final long serial)throws IOException
    {
        ItemModifiers.require();final JSONObject state=ledger(who),receipts=field(state,"receipts");final Object value=receipts.get(Long.toString(serial));
        if(cancelled(value))return "Request #"+serial+" was cancelled; it rolled and spent nothing.";
        return value instanceof JSONObject?ModuleDefinitions.json((JSONObject)value):serial>0&&serial<=number(state,"settled")?"Request #"+serial+" is settled/cancelled; its detailed receipt is no longer retained.":"No committed receipt for request #"+serial+".";
    }
    /** The last cancellation is noted among the receipts (one note at a time), so that a later COMMIT of it says so. */
    private static boolean cancelled(final Object receipt){return receipt instanceof JSONObject&&Boolean.TRUE.equals(((JSONObject)receipt).get("cancelled"));}
    /** At most eight receipts in 4,096 characters, the oldest dropped first; the newest is always kept. */
    private static String pruned(final JSONObject receipts,final long newest)throws IOException
    {
        while(receipts.size()>8||ModuleDefinitions.json(receipts).length()>4096){long oldest=Long.MAX_VALUE;for(final String key:receipts.keySet())oldest=Math.min(oldest,Long.parseLong(key));if(oldest==newest)throw new IOException("craft receipt too large");receipts.remove(Long.toString(oldest));}
        return ModuleDefinitions.json(receipts);
    }
    public static String cancel(final MOB who,final long requested)throws IOException
    {
        // Cancelling rolls and spends nothing, so it stays open while allow_crafting is false. A suspended module's state cannot be written: the request waits.
        if(!OptionalModules.enabled(ItemModifiers.MODULE))throw new IOException("items.modifiers is inactive in this campaign; a pending request keeps its saved state, and CANCEL works again once the module is active");
        settling();final JSONObject state=ledger(who),pending=field(state,"pending");if(pending.isEmpty())return "No pending item craft.";final long serial=number(pending,"serial");if(requested!=0&&requested!=serial)throw new IOException("that request is not pending");
        final JSONObject receipts=field(state,"receipts"),note=new JSONObject();for(final String key:new ArrayList<String>(receipts.keySet()))if(cancelled(receipts.get(key)))receipts.remove(key);
        note.put("cancelled",Boolean.TRUE);receipts.put(Long.toString(serial),note);
        state.put("receipts",pruned(receipts,serial));state.put("pending","");state.put("settled",Long.valueOf(serial));save(who,state);return "Cancelled craft #"+serial+"; no random rolls or costs.";
    }
    public static String commit(final MOB who,final long requested)throws IOException
    {
        ItemModifiers.gameThread();ItemModifiers.require();final JSONObject state=ledger(who),pending=field(state,"pending");
        final long serial=requested==0?(pending.isEmpty()?number(state,"serial"):number(pending,"serial")):requested;
        if(serial>0&&serial<=number(state,"settled"))return cancelled(field(state,"receipts").get(Long.toString(serial)))?"Craft #"+serial+" was cancelled, so it cannot be committed; it rolled and spent nothing. PREPARE a new request instead.":"Already settled craft #"+serial+". "+receipt(who,serial);
        writable();if(pending.isEmpty()||number(pending,"serial")!=serial)throw new IOException("no such pending request");
        ModuleDefinitions.keys(pending,"craft request","serial","actor","item","operation","selector","item_hash","room","costs","affix");
        if(!pending.get("actor").equals(InstanceIds.reference(who))||!pending.get("room").equals(InstanceIds.reference(who.location())))throw new IOException("craft actor or room changed");
        final Object found=InstanceIds.find((String)pending.get("item"));if(!(found instanceof Item))throw new IOException("craft item is unavailable");final Item item=(Item)found;
        final Definition op=ItemModifiers.definitions().pinned(pending.get("operation"),"operation");final Plan plan=plan(who,item,op,(String)pending.get("selector"));
        if(!pending.get("item_hash").equals(fingerprint(item))||!ModuleDefinitions.json((JSONObject)pending.get("costs")).equals(ModuleDefinitions.json(costs(plan.costs,false))))throw new IOException("item or material piles changed; cancel and prepare again");
        if(op.string("action").equals("add")&&!ModuleDefinitions.json(ItemModifierRules.checkPin(pending.get("affix"))).equals(ModuleDefinitions.json(ItemModifierRules.pin(ItemModifiers.definitions().find(plan.selector,0,"affix")))))throw new IOException("available affix version changed; prepare again");
        final Long rng=CMath.getRandState();final String oldText=ItemModifiers.property(item).text(),oldName=item.Name(),oldDisplay=item.displayText();final JSONObject next,receipt;final JSEvents.ItemOutbox box;
        try
        {
            next=ItemModifiers.names(item,ItemModifierRules.transform(plan.data,op,plan.selector,ItemModifiers.definitions(),item.basePhyStats().level(),UUID.randomUUID().toString(),ItemModifiers.dice()));
            receipt=new JSONObject();receipt.put("request",Long.valueOf(serial));receipt.put("item",pending.get("item"));receipt.put("operation",pending.get("operation"));receipt.put("before",pending.get("item_hash"));receipt.put("after_modifiers",ItemModifierRules.hash(next));receipt.put("revision",next.get("revision"));receipt.put("material",op.raw.get("material"));receipt.put("units",op.raw.get("units"));receipt.put("inputs",pending.get("costs"));
            final JSONObject receipts=field(state,"receipts");receipts.put(Long.toString(serial),receipt);
            state.put("receipts",pruned(receipts,serial));state.put("pending","");state.put("settled",Long.valueOf(serial));box=JSEvents.deferItemEvents();
        }
        catch(final IOException|RuntimeException e){CMath.setRandState(rng.longValue());throw e;}
        boolean committed=false;busy.set(Boolean.TRUE);critical=true;
        try
        {
            for(final Cost c:plan.costs)if(c.amount<c.weight)c.spend();
            ItemModifiers.install(item,next);
            for(final Cost c:plan.costs)if(c.amount==c.weight){who.delItem(c.item);c.item.setOwner(null);}
            save(who,state);committed=true;
        }
        catch(final IOException|RuntimeException e)
        {
            ItemModifiers.property(item).setMiscText(oldText);item.setName(oldName);item.setDisplayText(oldDisplay);
            for(final Cost c:plan.costs){c.restore();if(c.item.owner()!=who)who.addItem(c.item);}
            CMath.setRandState(rng.longValue());throw e;
        }
        finally
        {
            critical=false;
            if(!committed){box.finish(false);busy.remove();}
        }
        try
        {
            // Consumed objects are destroyed before advisory listeners can see them.
            for(final Cost c:plan.costs)if(c.amount==c.weight)try{c.item.destroy();}catch(final RuntimeException e){Log.errOut("ItemCrafting",e);}
            box.finish(true);JSEvents.fire("itemcrafted",who,item,ModuleDefinitions.json(receipt));
        }
        finally{busy.remove();}
        return "Committed craft #"+serial+": "+op.name+"; consumed "+op.number("units")+" "+op.string("material")+"; item revision "+next.get("revision")+". Repeating this request cannot consume or apply it again.";
    }
}
