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
	private ItemCrafting()
	{
	}
	private static volatile boolean critical=false;
	private static final ThreadLocal<Boolean> busy=new ThreadLocal<Boolean>();
	public static boolean inCommit()
	{
		return critical;
	}
	private static void settling()throws IOException
	{
		ItemModifiers.gameThread();
		ItemModifiers.require();
		if(Boolean.TRUE.equals(busy.get()))
			throw new IOException("item transaction observer cannot start another transaction");
	}
	private static void writable()throws IOException
	{
		settling();
		if(!Boolean.TRUE.equals(OptionalModules.setting(ItemModifiers.MODULE,"allow_crafting")))
			throw new IOException("crafting is disabled by this campaign");
	}
	private static JSONObject ledger(final MOB who)throws IOException
	{
		final JSONObject state=OptionalModules.get(ItemModifiers.MODULE,OptionalModules.character(who));
		if(number(state,"settled")>number(state,"serial"))
			throw new IOException("invalid craft settlement counter");
		final JSONObject receipts=field(state,"receipts"),pending=field(state,"pending");
		for(final String key:receipts.keySet())
		{
			try
			{
				final long id=Long.parseLong(key);
				if(!key.equals(Long.toString(id))||id<1||id>number(state,"settled")||!(receipts.get(key) instanceof JSONObject))
					throw new IOException("invalid craft receipt index");
			}
			catch(final NumberFormatException e)
			{
				throw new IOException("invalid craft receipt index");
			}
		}
		if(!pending.isEmpty())
		{
			ModuleDefinitions.keys(pending,"craft request","serial","actor","item","operation","selector","item_hash","room","costs","affix","catalysts");
			final int id=ModuleDefinitions.integer(pending.get("serial"),"request serial");
			if(id!=number(state,"serial")||id<=number(state,"settled"))
				throw new IOException("pending craft counter differs from ledger");
			for(final String key:Arrays.asList("actor","item","selector","item_hash","room"))
				ModuleDefinitions.string(pending.get(key),key);
			ItemModifierRules.checkPin(pending.get("operation"));
			ModuleDefinitions.object(pending.get("costs"),"material sources");
		}
		return state;
	}
	private static JSONObject field(final JSONObject ledger,final String name)throws IOException
	{
		final String s=(String)ledger.get(name);
		return s.isEmpty()?new JSONObject():ModuleDefinitions.parse(s);
	}
	private static long number(final JSONObject o,final String name)
	{
		return ((Number)o.get(name)).longValue();
	}
	private static void save(final MOB who,final JSONObject data)throws IOException
	{
		OptionalModules.put(ItemModifiers.MODULE,OptionalModules.character(who),data);
	}
	private static String fingerprint(final Item item)throws IOException
	{
		final JSONObject d=new JSONObject();
		d.put("class",item.ID());
		d.put("text",item.text());
		d.put("weight",Long.valueOf(item.basePhyStats().weight()));
		d.put("value",Long.valueOf(item.baseGoldValue()));
		d.put("material",Long.valueOf(item.material()));
		d.put("uses",Long.valueOf(item.usesRemaining()));
		d.put("level",Long.valueOf(item.basePhyStats().level()));
		return ItemModifierRules.hash(d);
	}
	/** a pile that pays part of a cost; I07 offerings and reveals pay through it too */
	static final class Cost
	{
		final Item item;
		final int amount,weight,value;
		final String name,display,description;
		Cost(final Item item,final int amount)
		{
			this.item=item;
			this.amount=amount;
			weight=item.basePhyStats().weight();
			value=item.baseGoldValue();
			name=item.Name();
			display=item.displayText();
			description=item.description();
		}
		/** What is left of a partly spent pile. A name that shows its weight ("an 8# iron ore bundle") is made again for the new weight. */
		void spend()
		{
			item.basePhyStats().setWeight(weight-amount);
			item.setBaseValue((int)(((long)value*(weight-amount))/weight));
			if(name.matches("(?s).*\\b"+weight+"#.*"))
				CMLib.materials().adjustResourceName(item);
			item.recoverPhyStats();
		}
		void restore()
		{
			item.basePhyStats().setWeight(weight);
			item.setBaseValue(value);
			item.setName(name);
			item.setDisplayText(display);
			item.setDescription(description);
			item.recoverPhyStats();
		}
	}
	private static final class Plan
	{
		final Item item;
		final JSONObject data;
		final Definition op;
		final String selector;
		final List<Cost> costs;
		final int stage;
		/** I06: the catalysts named, the distribution they and the craft make, and the units of each material paid */
		final List<Definition> catalysts;
		final Bias bias;
		final Map<String,Integer> needs;
		Plan(final Item item,final JSONObject data,final Definition op,final String selector,final List<Cost> costs,final int stage,
			final List<Definition> catalysts,final Bias bias,final Map<String,Integer> needs)
		{
			this.item=item;
			this.data=data;
			this.op=op;
			this.selector=selector;
			this.costs=costs;
			this.stage=stage;
			this.catalysts=catalysts;
			this.bias=bias;
			this.needs=needs;
		}
		/** what it pays, in words */
		String paid()
		{
			final List<String> out=new ArrayList<String>();
			for(final Map.Entry<String,Integer> e:needs.entrySet())
				out.add(e.getValue()+" "+e.getKey());
			return String.join(" and ",out);
		}
		String cost()
		{
			final List<String> out=new ArrayList<String>();
			for(final Map.Entry<String,Integer> e:needs.entrySet())
				out.add(e.getValue()+" weight unit(s) of plain "+e.getKey());
			final List<String> names=new ArrayList<String>();
			for(final Definition c:catalysts)
				names.add(c.name+" ("+c.id+": "+c.number("units")+" "+c.string("material")+")");
			return String.join(" and ",out)+(names.isEmpty()?"":", with the catalyst(s) "+String.join(", ",names));
		}
		/** the plan in words, its item named as the viewer knows it (A23) */
		String describe(final MOB viewer)
		{
			if(stage>=0)
			{
				final JSONObject st=stageOf(op,stage);
				return op.name+" / stage "+(stage+1)+" of "+stages(op).length+" ("+st.get("name")+") on "+item.name(viewer)+"; cost "+cost()
					+" at "+op.string("station")
					+(stage==stages(op).length-1?"; this last stage makes "+((JSONObject)op.raw.get("result")).get("catalog"):"")
					+". No inputs spent or random outcomes drawn.";
			}
			return op.name+" / "+op.string("action")+" on "+item.name(viewer)+"; cost "+cost()
				+" at "+op.string("station")+". No inputs spent or random outcomes drawn.";
		}
	}
	/** I06: "[selector] WITH [catalyst], [catalyst]": the selector, and the catalysts by ID (at most three, each once) */
	static String[] withSplit(final String input)
	{
		final java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:^|\\s)WITH\\s+").matcher(input);
		if(!m.find())
			return new String[]{input.trim(),""};
		return new String[]{input.substring(0,m.start()).trim(),input.substring(m.end()).trim()};
	}
	private static List<Definition> catalysts(final String names)throws IOException
	{
		final List<Definition> out=new ArrayList<Definition>();
		if(names.trim().isEmpty())
			return out;
		for(final String w:names.trim().split("[,\\s]+"))
		{
			if(w.isEmpty()||w.equalsIgnoreCase("and"))
				continue;
			final String id=ModuleDefinitions.id(w.toLowerCase(Locale.ROOT));
			final Definition c;
			try
			{
				c=ItemModifiers.definitions().find(id,0,"catalyst");
			}
			catch(final IOException e)
			{
				throw new IOException("no catalyst "+id);
			}
			for(final Definition d:out)
				if(d.id.equals(c.id))
					throw new IOException("name each catalyst once: "+id);
			out.add(c);
		}
		if(out.size()>3)
			throw new IOException("at most three catalysts in one craft");
		return out;
	}
	/** the units of each material: the craft's own, then each catalyst's */
	private static Map<String,Integer> needs(final String material,final int units,final List<Definition> catalysts)
	{
		final Map<String,Integer> out=new LinkedHashMap<String,Integer>();
		out.put(material,Integer.valueOf(units));
		for(final Definition c:catalysts)
			out.put(c.string("material"),Integer.valueOf((out.containsKey(c.string("material"))?out.get(c.string("material")).intValue():0)+c.number("units")));
		return out;
	}
	static List<Cost> payAll(final MOB who,final Item item,final Map<String,Integer> needs)throws IOException
	{
		final List<Cost> out=new ArrayList<Cost>();
		for(final Map.Entry<String,Integer> e:needs.entrySet())
			out.addAll(pay(who,item,e.getKey(),e.getValue().intValue()));
		return out;
	}
	static Object[] stages(final Definition recipe)
	{
		return (Object[])recipe.raw.get("stages");
	}
	static JSONObject stageOf(final Definition recipe,final int n)
	{
		return (JSONObject)stages(recipe)[n];
	}
	/** An operation or a staged recipe, by ID: the latest installed version. */
	private static Definition craftDef(final String id)throws IOException
	{
		final Library lib=ItemModifiers.definitions();
		try
		{
			return lib.find(id,0,"operation");
		}
		catch(final IOException e)
		{
			try
			{
				return lib.find(id,0,"recipe");
			}
			catch(final IOException f)
			{
				throw new IOException("no operation or recipe "+id);
			}
		}
	}
	private static Definition pinnedDef(final Object pin)throws IOException
	{
		final JSONObject p=ItemModifierRules.checkPin(pin);
		final Definition d=ItemModifiers.definitions().definitions.get(p.get("id")+".v"+p.get("version"));
		if(d==null)
			throw new IOException("unavailable definition "+p.get("id")+" v"+p.get("version"));
		return ItemModifiers.definitions().pinned(pin,d.kind);
	}
	/** The material piles that pay a cost, smallest set from the top of the inventory, or a refusal. */
	static List<Cost> pay(final MOB who,final Item item,final String materialName,final int units)throws IOException
	{
		final int material=RawMaterial.CODES.FIND_CaseSensitive(materialName);
		if(material<0)
			throw new IOException("unknown material "+materialName);
		int remaining=units;
		final List<Cost> costs=new ArrayList<Cost>();
		for(final Enumeration<Item> e=who.items();e.hasMoreElements()&&remaining>0;)
		{
			final Item i=e.nextElement();
			if(i!=item&&plain(who,i,material))
			{
				final int used=Math.min(remaining,i.basePhyStats().weight());
				costs.add(new Cost(i,used));
				remaining-=used;
			}
		}
		if(remaining>0)
			throw new IOException("need "+units+" weight unit(s) of plain "+materialName+" in your inventory");
		if(costs.size()>8)
			throw new IOException("combine materials into at most eight plain piles first");
		return costs;
	}
	static void custody(final MOB who,final Item item)throws IOException
	{
		if(who==null||who.amDead()||who.amDestroyed()||who.location()==null||who.isInCombat())
			throw new IOException("craft outside combat, alive in a room");
		if(item==null||item.amDestroyed()||item.owner()!=who||item.container()!=null||item.rawWornCode()!=Wearable.IN_INVENTORY)
			throw new IOException("craft an unworn, directly carried item you own");
	}
	private static boolean plain(final MOB who,final Item item,final int material)
	{
		if(item==null||item.amDestroyed()||!item.ID().equals("GenResource")||!(item instanceof RawMaterial)||item.material()!=material
			||item.owner()!=who||item.container()!=null||item.rawWornCode()!=Wearable.IN_INVENTORY||item.basePhyStats().weight()<1
			||item.numBehaviors()>0||item.numScripts()>0)
			return false;
		for(final Enumeration<Ability> e=item.effects();e.hasMoreElements();)
			if(!e.nextElement().ID().equals("ExtraData"))
				return false;
		return true;
	}
	private static Plan plan(final MOB who,final Item item,final Definition op,final String selector,final List<Definition> catalysts)throws IOException
	{
		ItemModifiers.require();
		if(!Boolean.TRUE.equals(OptionalModules.setting(ItemModifiers.MODULE,"allow_crafting")))
			throw new IOException("crafting is disabled by this campaign");
		custody(who,item);
		if(!Workstations.has(who.location(),op.string("station")))
			throw new IOException("required workstation: "+op.string("station"));
		final JSONObject data=ItemModifiers.data(item);
		final Bias bias=ItemModifierRules.bias(op,catalysts);
		ItemModifierRules.validate(data,op,selector,ItemModifiers.definitions(),item.basePhyStats().level(),bias);
		if((op.string("action").equals("add")||op.string("action").equals("imbue"))&&ItemModifierRules.slots(data)>=ItemModifiers.limit())
			throw new IOException("campaign modifier limit reached");
		final Map<String,Integer> needs=needs(op.string("material"),op.number("units"),catalysts);
		return new Plan(item,data,op,selector,payAll(who,item,needs),-1,catalysts,bias,needs);
	}
	/** I04: the next stage of a staged recipe on its work piece. */
	private static Plan planStage(final MOB who,final Item piece,final Definition recipe,final List<Definition> catalysts)throws IOException
	{
		ItemModifiers.require();
		if(!Boolean.TRUE.equals(OptionalModules.setting(ItemModifiers.MODULE,"allow_crafting")))
			throw new IOException("crafting is disabled by this campaign");
		custody(who,piece);
		if(!Workstations.has(who.location(),recipe.string("station")))
			throw new IOException("required workstation: "+recipe.string("station"));
		final JSONObject order=workOrder(piece);
		final Definition pinned=pinnedDef(order.get("recipe"));
		if(!pinned.id.equals(recipe.id))
			throw new IOException(piece.name(who)+" is a work piece of "+pinned.id+", not "+recipe.id);
		final int stage=((Number)order.get("stage")).intValue();
		if(stage>=stages(pinned).length)
			throw new IOException(piece.name(who)+" has no stage left");
		final JSONObject st=stageOf(pinned,stage);
		final boolean last=stage==stages(pinned).length-1;
		if(!catalysts.isEmpty()&&!last)
			throw new IOException("catalysts go in at the last stage ("+stages(pinned).length+"), which draws the product");
		final Bias bias=ItemModifierRules.bias(pinned,catalysts);
		if(!catalysts.isEmpty()&&ItemModifierRules.total(productPick(pinned,bias))<=0)
			throw new IOException((bias.guarantee.length()>0?"no affix of the guaranteed "+bias.guarantee+" family ("+bias.guaranteedBy+") can be drawn"
				:"no affix can be drawn")+" for the product:"+ItemModifierRules.describe(productPick(pinned,bias)));
		final Map<String,Integer> needs=needs((String)st.get("material"),((Number)st.get("units")).intValue(),catalysts);
		return new Plan(piece,order,pinned,"stage:"+stage,payAll(who,piece,needs),stage,catalysts,bias,needs);
	}
	/** I06: the product's first pick: its base's or its policy's, at the catalog item's level */
	private static List<Odds> productPick(final Definition recipe,final Bias bias)throws IOException
	{
		final JSONObject r=(JSONObject)recipe.raw.get("result");
		final Item proto=CMLib.catalog().getCatalogItem((String)r.get("catalog"));
		if(proto==null)
			throw new IOException("no catalog item '"+r.get("catalog")+"' for the result");
		return ItemModifiers.firstPick(r.containsKey("policy")?"POLICY="+r.get("policy"):(String)r.get("base"),proto.basePhyStats().level(),bias);
	}
	/** A work piece's order: its recipe pin and the stage it is at. */
	static JSONObject workOrder(final Item piece)throws IOException
	{
		final Ability p=piece==null?null:piece.fetchEffect("Prop_WorkOrder");
		if(p==null)
			throw new IOException((piece==null?"that":piece.Name())+" is not a work piece");
		final JSONObject o=ModuleDefinitions.parse(p.text());
		ModuleDefinitions.keys(o,"work order","schema","recipe","stage");
		ItemModifierRules.checkPin(o.get("recipe"));
		if(!(o.get("stage") instanceof Long||o.get("stage") instanceof Integer)||((Number)o.get("stage")).longValue()<0
			||((Number)o.get("stage")).longValue()>6)
			throw new IOException("invalid work order stage");
		return o;
	}
	private static String selector(final JSONObject data,final Definition op,final String input)throws IOException
	{
		final String action=op.string("action");
		if(action.equals("add"))
			return ModuleDefinitions.id(input.trim());
		if(action.equals("upgrade")||action.equals("refine")||ItemModifierRules.DRAWS.contains(action))
		{
			if(input.trim().length()>0)
				throw new IOException(action+" takes no modifier or affix; name catalysts after WITH");
			return "-";
		}
		final String[] words=input.trim().split("\\s+");
		if(action.equals("replace")&&words.length!=2)
			throw new IOException("replace takes a modifier number shown by ITEMMODS and the affix ID to put in its place");
		final int index;
		try
		{
			index=Integer.parseInt(words[0]);
		}
		catch(final NumberFormatException e)
		{
			throw new IOException("choose a modifier number shown by ITEMMODS");
		}
		final List<JSONObject> mods=ItemModifierRules.modifiers(data);
		if(index<1||index>mods.size())
			throw new IOException("no such modifier number");
		final String uid=(String)mods.get(index-1).get("uid");
		return action.equals("replace")?uid+">"+ModuleDefinitions.id(words[1]):uid;
	}
	public static String preview(final MOB who,final String operation,final Item item,final String input)throws IOException
	{
		final Definition op=craftDef(operation);
		final String[] split=withSplit(input);
		final List<Definition> cats=catalysts(split[1]);
		if(op.kind.equals("recipe"))
		{
			final Plan stage=planStage(who,item,op,cats);
			final JSONObject r=(JSONObject)stage.op.raw.get("result");
			if(stage.stage<stages(stage.op).length-1||!r.containsKey("policy")&&((Number)r.get("modifiers")).intValue()==0)
				return stage.describe(who);
			// I06: the last stage's product: what its first modifier can be
			return stage.describe(who)+"\nDistribution: "+stage.bias.describe()+(stage.op.optional("catalysts").isEmpty()?""
				:"; catalysts this recipe takes: "+String.join(", ",stage.op.optional("catalysts")))+"\nThe product's first modifier:"
				+ItemModifierRules.describe(productPick(stage.op,stage.bias))
				+(r.containsKey("policy")?"\nIts rarity decides how many modifiers it gets; a rarity that gives none gives none.":"");
		}
		final String choice=selector(ItemModifiers.data(item),op,split[0]);
		final Plan plan=plan(who,item,op,choice,cats);
		final String action=op.string("action");
		if(ItemModifierRules.DRAWS.contains(action))
		{
			// I06: the player's preview: each affix's chance in the first pick, or why it cannot be drawn
			final int n=action.equals("imbue")?1:ItemModifierRules.modifiers(plan.data).size()
				-ItemModifierRules.modifiers(ItemModifierRules.reforged(plan.data)).size();
			return plan.describe(who)+"\nDistribution: "+plan.bias.describe()+(op.optional("catalysts").isEmpty()?""
				:"; catalysts this operation takes: "+String.join(", ",op.optional("catalysts")))+"\n"
				+(action.equals("imbue")?"The modifier drawn:":"Reforge draws "+n+" modifier(s) in place of the unprotected ones; the first:")
				+ItemModifierRules.describe(ItemModifierRules.firstPick(plan.data,op,ItemModifiers.definitions(),item.basePhyStats().level(),plan.bias))
				+(n>1?"\nLater picks leave out the groups and slots already taken.":"");
		}
		String ranges="";
		if(action.equals("add"))
			ranges=ModuleDefinitions.json((JSONObject)ItemModifiers.definitions().find(choice,0,"affix").raw.get("rolls"));
		else if(action.equals("replace"))
			ranges=ModuleDefinitions.json((JSONObject)ItemModifiers.definitions().find(ItemModifierRules.replacement(choice)[1],0,"affix").raw
				.get("rolls"));
		else if(action.equals("reroll"))
			for(final JSONObject m:ItemModifierRules.modifiers(plan.data))
				if(m.get("uid").equals(choice))
					ranges=ModuleDefinitions.json((JSONObject)ItemModifiers.definitions().pinned(m.get("pin"),"affix").raw.get("rolls"));
		if(action.equals("refine"))
		{
			// I09: what the next tier raises
			final Definition base=ItemModifiers.definitions().pinned(plan.data.get("base"),"base");
			final JSONObject q=(JSONObject)base.raw.get("quality");
			final List<String> tags=new ArrayList<String>();
			for(final Object t:ModuleDefinitions.array(q.get("tags"),"quality tags"))
				tags.add(String.valueOf(t));
			return plan.describe(who)+"\nRefines "+item.name(who)+" to quality tier "+(ItemModifierRules.quality(plan.data)+1)+" of "+q.get("max")+": modifiers tagged "
				+String.join(" or ",tags)+" count "+((ItemModifierRules.quality(plan.data)+1)*((Number)q.get("percent")).intValue())
				+"% more. The rolls kept on the item do not change.";
		}
		if(action.equals("upgrade"))
			return plan.describe(who)+"\nUpgrades to "+ItemModifierRules.successor(plan.data,ItemModifiers.definitions()).key()
				+", keeping every modifier and its rolls.";
		return plan.describe(who)+(ranges.isEmpty()?"":"\nRoll ranges: "+ranges);
	}
	public static String operations()throws IOException
	{
		ItemModifiers.require();
		final Library lib=ItemModifiers.definitions();
		final StringBuilder out=new StringBuilder();
		for(final Definition op:lib.definitions.values())
			if(op.kind.equals("operation")&&lib.find(op.id,0,"operation")==op)
				out.append(op.id).append(" - ").append(op.name).append(" (").append(op.string("action")).append("); ").append(op.number("units"))
					.append(' ').append(op.string("material")).append("; station ").append(op.string("station"))
					.append(op.optional("catalysts").isEmpty()?"":"; catalysts "+String.join(", ",op.optional("catalysts"))).append('\n');
		for(final Definition r:lib.definitions.values())
			if(r.kind.equals("recipe")&&lib.find(r.id,0,"recipe")==r)
			{
				out.append(r.id).append(" - ").append(r.name).append(" (staged recipe; MODCRAFT BEGIN ").append(r.id).append("); station ")
					.append(r.string("station")).append(':');
				for(int i=0;i<stages(r).length;i++)
				{
					final JSONObject st=stageOf(r,i);
					out.append(i==0?" ":", ").append(i+1).append(". ").append(st.get("name")).append(" (").append(st.get("units")).append(' ')
						.append(st.get("material")).append(')');
				}
				out.append("; makes ").append(((JSONObject)r.raw.get("result")).get("catalog"))
					.append(r.optional("catalysts").isEmpty()?"":"; catalysts at the last stage "+String.join(", ",r.optional("catalysts"))).append('\n');
			}
		for(final Definition c:lib.definitions.values())
			if(c.kind.equals("catalyst")&&lib.find(c.id,0,"catalyst")==c)
				out.append("catalyst ").append(c.id).append(" - ").append(c.name).append("; ").append(c.number("units")).append(' ').append(c.string("material"))
					.append("; ").append(Bias.of(c.raw,c.id).rules()).append('\n');
		return out.length()==0?"No modifier crafting operations are installed.":out.toString();
	}
	public static String pool(final Item item)throws IOException
	{
		return pool(item,null);
	}
	/** MODCRAFT POOL: the affixes an item's base offers, the item named as the viewer knows it (A23; no viewer, its real name) */
	public static String pool(final Item item,final MOB viewer)throws IOException
	{
		ItemModifiers.require();
		final JSONObject data=ItemModifiers.data(item);
		final Library lib=ItemModifiers.definitions();
		final Definition base=lib.pinned(data.get("base"),"base");
		final StringBuilder out=new StringBuilder("Affix pool for "+((viewer==null)?item.Name():item.name(viewer))
			+"; PREVIEW also checks the chosen operation, station and materials.\n");
		for(final String id:base.list("pool"))
		{
			final Definition a=lib.find(id,0,"affix");
			String reason=ItemModifierRules.ineligible(data,a,item.basePhyStats().level());
			if(reason.isEmpty()&&ItemModifierRules.modifiers(data).size()>=ItemModifiers.limit())
				reason="campaign modifier limit reached";
			out.append(id).append(" - ").append(a.name).append("; ").append(a.string("position")).append(" tier ").append(a.number("tier"))
				.append("; group ").append(a.string("group")).append("; ").append(reason.isEmpty()?"eligible for addition":reason).append("; rolls ")
				.append(ModuleDefinitions.json((JSONObject)a.raw.get("rolls"))).append('\n');
		}
		return out.toString();
	}
	private static JSONObject costs(final List<Cost> costs,final boolean identify)throws IOException
	{
		final JSONObject result=new JSONObject();
		for(final Cost c:costs)
		{
			final String id=identify?InstanceIds.reference(c.item):InstanceIds.PREFIX+InstanceIds.existing(c.item);
			final JSONObject row=new JSONObject();
			row.put("units",Long.valueOf(c.amount));
			row.put("hash",fingerprint(c.item));
			result.put(id,row);
		}
		return result;
	}
	public static String prepare(final MOB who,final String operation,final Item item,final String input)throws IOException
	{
		writable();
		final Definition op=craftDef(operation);
		final boolean staged=op.kind.equals("recipe");
		final String[] split=withSplit(input);
		final List<Definition> cats=catalysts(split[1]);
		final String choice=staged?null:selector(ItemModifiers.data(item),op,split[0]);
		final Plan plan=staged?planStage(who,item,op,cats):plan(who,item,op,choice,cats);
		final JSONObject state=ledger(who);
		if(!((String)state.get("pending")).isEmpty())
			throw new IOException("a request is pending; commit or cancel it first");
		final long serial=number(state,"serial");
		if(serial>=1000000000)
			throw new IOException("craft request counter is full");
		final JSONObject pending=new JSONObject();
		pending.put("serial",Long.valueOf(serial+1));
		pending.put("actor",InstanceIds.reference(who));
		pending.put("item",staged?InstanceIds.reference(item):plan.data.get("item"));
		pending.put("operation",ItemModifierRules.pin(plan.op));
		pending.put("selector",plan.selector);
		pending.put("item_hash",fingerprint(item));
		pending.put("room",InstanceIds.reference(who.location()));
		pending.put("costs",costs(plan.costs,true));
		final String added=staged?null:op.string("action").equals("add")?choice:op.string("action").equals("replace")
			?ItemModifierRules.replacement(choice)[1]:null;
		if(added!=null)
			pending.put("affix",ItemModifierRules.pin(ItemModifiers.definitions().find(added,0,"affix")));
		if(!cats.isEmpty())
			pending.put("catalysts",pins(cats));
		final String encoded=ModuleDefinitions.json(pending);
		if(encoded.length()>4096)
			throw new IOException("request exceeds storage limit");
		state.put("serial",Long.valueOf(serial+1));
		state.put("pending",encoded);
		save(who,state);
		ModuleStats.count("items","crafts","prepared");
		return "Prepared craft #"+(serial+1)+": "+plan.describe(who)+"\nMODCRAFT COMMIT "+(serial+1)+" settles it; MODCRAFT CANCEL "+(serial+1)
			+" discards it. Preparation has no random outcome to shop around for.";
	}
	private static Object[] pins(final List<Definition> defs)
	{
		final List<Object> out=new ArrayList<Object>();
		for(final Definition d:defs)
			out.add(ItemModifierRules.pin(d));
		return out.toArray();
	}
	/** I06: a request's catalysts, by their pins */
	private static List<Definition> pinnedCatalysts(final JSONObject pending)throws IOException
	{
		final List<Definition> out=new ArrayList<Definition>();
		if(pending.containsKey("catalysts"))
			for(final Object pin:ModuleDefinitions.array(pending.get("catalysts"),"catalysts"))
				out.add(pinnedDef(pin));
		return out;
	}
	public static String status(final MOB who)throws IOException
	{
		ItemModifiers.require();
		if(InstanceIds.existing(who).isEmpty())
			return "No pending item craft or receipts."+pieces(who);
		final JSONObject state=ledger(who);
		final JSONObject pending=field(state,"pending");
		return pieces(who).isEmpty()?statusLine(state,pending):statusLine(state,pending)+pieces(who);
	}
	/** I04: the work pieces a character carries, each with its recipe and the stage it waits for. */
	private static String pieces(final MOB who)
	{
		final StringBuilder out=new StringBuilder();
		for(final Enumeration<Item> e=who.items();e.hasMoreElements();)
		{
			final Item I=e.nextElement();
			if(I.fetchEffect("Prop_WorkOrder")==null)
				continue;
			try
			{
				final JSONObject o=workOrder(I);
				final Definition r=pinnedDef(o.get("recipe"));
				final int k=((Number)o.get("stage")).intValue();
				out.append("\nWork piece ").append(I.name(who)).append(": ").append(r.name).append(", ").append(k).append(" of ")
					.append(stages(r).length).append(" stage(s) done");
				if(k<stages(r).length)
				{
					final JSONObject st=stageOf(r,k);
					out.append("; next ").append(st.get("name")).append(" (").append(st.get("units")).append(' ').append(st.get("material"))
						.append(" at ").append(r.string("station")).append(')');
				}
			}
			catch(final IOException x)
			{
				out.append("\nWork piece ").append(I.name(who)).append(": ").append(x.getMessage());
			}
		}
		return out.toString();
	}
	private static String statusLine(final JSONObject state,final JSONObject pending)
	{
		return pending.isEmpty()?"No pending item craft. Last request: "+state.get("serial")+"; MODCRAFT RECEIPT [number] inspects retained receipts."
			:"Pending craft #"+pending.get("serial")+": "+((JSONObject)pending.get("operation")).get("id")
			+". Commit rechecks custody, item state, material piles, station and pins; it may refuse a stale request.";
	}
	public static String receipt(final MOB who,final long serial)throws IOException
	{
		ItemModifiers.require();
		final JSONObject state=ledger(who),receipts=field(state,"receipts");
		final Object value=receipts.get(Long.toString(serial));
		if(cancelled(value))
			return "Request #"+serial+" was cancelled; it rolled and spent nothing.";
		return value instanceof JSONObject?ModuleDefinitions.json((JSONObject)value):serial>0&&serial<=number(state,"settled")
			?"Request #"+serial+" is settled/cancelled; its detailed receipt is no longer retained.":"No committed receipt for request #"+serial+".";
	}
	/** The last cancellation is noted among the receipts (one note at a time), so that a later COMMIT of it says so. */
	private static boolean cancelled(final Object receipt)
	{
		return receipt instanceof JSONObject&&Boolean.TRUE.equals(((JSONObject)receipt).get("cancelled"));
	}
	/** At most eight receipts in 4,096 characters, the oldest dropped first; the newest is always kept. */
	private static String pruned(final JSONObject receipts,final long newest)throws IOException
	{
		while(receipts.size()>8||ModuleDefinitions.json(receipts).length()>4096)
		{
			long oldest=Long.MAX_VALUE;
			for(final String key:receipts.keySet())
				oldest=Math.min(oldest,Long.parseLong(key));
			if(oldest==newest)
				throw new IOException("craft receipt too large");
			receipts.remove(Long.toString(oldest));
		}
		return ModuleDefinitions.json(receipts);
	}
	public static String cancel(final MOB who,final long requested)throws IOException
	{
		// Cancelling rolls and spends nothing, so it stays open while allow_crafting is false. A suspended module's state cannot be written: the request waits.
		if(!OptionalModules.enabled(ItemModifiers.MODULE))
			throw new IOException("items.modifiers is inactive in this campaign; a pending request keeps its saved state, and CANCEL works again once the module is active");
		settling();
		final JSONObject state=ledger(who),pending=field(state,"pending");
		if(pending.isEmpty())
			return "No pending item craft.";
		final long serial=number(pending,"serial");
		if(requested!=0&&requested!=serial)
			throw new IOException("that request is not pending");
		final JSONObject receipts=field(state,"receipts"),note=new JSONObject();
		for(final String key:new ArrayList<String>(receipts.keySet()))
			if(cancelled(receipts.get(key)))
				receipts.remove(key);
		note.put("cancelled",Boolean.TRUE);
		receipts.put(Long.toString(serial),note);
		state.put("receipts",pruned(receipts,serial));
		state.put("pending","");
		state.put("settled",Long.valueOf(serial));
		save(who,state);
		ModuleStats.count("items","crafts","cancelled");
		return "Cancelled craft #"+serial+"; no random rolls or costs.";
	}
	/** Commits a request; Q12 counts it and Q05 notes it, settled or refused (with the way to recover). */
	public static String commit(final MOB who,final long requested)throws IOException
	{
		try
		{
			final String said=commitChecked(who,requested);
			if(said.startsWith("Committed"))
			{
				ModuleStats.count("items","crafts","committed");
				Provenance.event(who,ItemModifiers.MODULE,"craft",said.length()>160?said.substring(0,160):said);
			}
			return said;
		}
		catch(final IOException e)
		{
			ModuleStats.count("items","crafts","refused");
			Provenance.refused(who,ItemModifiers.MODULE,"craft request",e.getMessage(),
				"MODCRAFT STATUS shows the request; MODCRAFT CANCEL it and PREPARE again; nothing was spent or drawn");
			throw e;
		}
	}
	private static String commitChecked(final MOB who,final long requested)throws IOException
	{
		ItemModifiers.gameThread();
		ItemModifiers.require();
		final JSONObject state=ledger(who),pending=field(state,"pending");
		final long serial=requested==0?(pending.isEmpty()?number(state,"serial"):number(pending,"serial")):requested;
		if(serial>0&&serial<=number(state,"settled"))
			return cancelled(field(state,"receipts").get(Long.toString(serial)))
				?"Craft #"+serial+" was cancelled, so it cannot be committed; it rolled and spent nothing. PREPARE a new request instead."
				:"Already settled craft #"+serial+". "+receipt(who,serial);
		writable();
		if(pending.isEmpty()||number(pending,"serial")!=serial)
			throw new IOException("no such pending request");
		ModuleDefinitions.keys(pending,"craft request","serial","actor","item","operation","selector","item_hash","room","costs","affix","catalysts");
		if(!pending.get("actor").equals(InstanceIds.reference(who))||!pending.get("room").equals(InstanceIds.reference(who.location())))
			throw new IOException("craft actor or room changed");
		final Object found=InstanceIds.find((String)pending.get("item"));
		if(!(found instanceof Item))
			throw new IOException("craft item is unavailable");
		final Item item=(Item)found;
		final Definition op=pinnedDef(pending.get("operation"));
		final List<Definition> cats=pinnedCatalysts(pending);
		if(op.kind.equals("recipe"))
			return commitStage(who,state,pending,serial,item,op,cats);
		final Plan plan=plan(who,item,op,(String)pending.get("selector"),cats);
		if(!pending.get("item_hash").equals(fingerprint(item))
			||!ModuleDefinitions.json((JSONObject)pending.get("costs")).equals(ModuleDefinitions.json(costs(plan.costs,false))))
			throw new IOException("item or material piles changed; cancel and prepare again");
		final String added=op.string("action").equals("add")?plan.selector:op.string("action").equals("replace")
			?ItemModifierRules.replacement(plan.selector)[1]:null;
		if(added!=null&&!ModuleDefinitions.json(ItemModifierRules.checkPin(pending.get("affix")))
			.equals(ModuleDefinitions.json(ItemModifierRules.pin(ItemModifiers.definitions().find(added,0,"affix")))))
			throw new IOException("available affix version changed; prepare again");
		final Long rng=CMath.getRandState();
		final String oldText=ItemModifiers.property(item).text(),oldName=item.Name(),oldDisplay=item.displayText();
		final JSONObject next,receipt;
		final JSEvents.ItemOutbox box;
		try
		{
			next=ItemModifiers.names(item,
				ItemModifierRules.transform(plan.data,op,plan.selector,ItemModifiers.definitions(),item.basePhyStats().level(),
				UUID.randomUUID().toString(),ItemModifiers.dice(),plan.bias));
			receipt=new JSONObject();
			receipt.put("request",Long.valueOf(serial));
			receipt.put("item",pending.get("item"));
			receipt.put("operation",pending.get("operation"));
			receipt.put("before",pending.get("item_hash"));
			receipt.put("after_modifiers",ItemModifierRules.hash(next));
			receipt.put("revision",next.get("revision"));
			receipt.put("material",op.raw.get("material"));
			receipt.put("units",op.raw.get("units"));
			receipt.put("inputs",pending.get("costs"));
			if(pending.containsKey("catalysts"))
				receipt.put("catalysts",pending.get("catalysts"));
			final JSONObject receipts=field(state,"receipts");
			receipts.put(Long.toString(serial),receipt);
			state.put("receipts",pruned(receipts,serial));
			state.put("pending","");
			state.put("settled",Long.valueOf(serial));
			box=JSEvents.deferItemEvents();
		}
		catch(final IOException|RuntimeException e)
		{
			CMath.setRandState(rng.longValue());
			throw e;
		}
		// I06: what a drawing operation drew, for the message
		final List<String> drew=new ArrayList<String>();
		if(ItemModifierRules.DRAWS.contains(op.string("action")))
		{
			final Set<String> had=new HashSet<String>();
			for(final JSONObject m:ItemModifierRules.modifiers(plan.data))
				had.add((String)m.get("uid"));
			for(final JSONObject m:ItemModifierRules.modifiers(next))
				if(!had.contains(m.get("uid")))
					drew.add(m.get("name")+" ("+((JSONObject)m.get("pin")).get("id")+")");
		}
		boolean committed=false;
		busy.set(Boolean.TRUE);
		critical=true;
		try
		{
			for(final Cost c:plan.costs)
				if(c.amount<c.weight)
					c.spend();
			ItemModifiers.install(item,next);
			for(final Cost c:plan.costs)
				if(c.amount==c.weight)
				{
					who.delItem(c.item);
					c.item.setOwner(null);
				}
			save(who,state);
			committed=true;
		}
		catch(final IOException|RuntimeException e)
		{
			ItemModifiers.property(item).setMiscText(oldText);
			item.setName(oldName);
			item.setDisplayText(oldDisplay);
			for(final Cost c:plan.costs)
			{
				c.restore();
				if(c.item.owner()!=who)
					who.addItem(c.item);
			}
			CMath.setRandState(rng.longValue());
			throw e;
		}
		finally
		{
			critical=false;
			if(!committed)
			{
				box.finish(false);
				busy.remove();
			}
		}
		try
		{
			// Consumed objects are destroyed before advisory listeners can see them.
			for(final Cost c:plan.costs)
				if(c.amount==c.weight)
					try
					{
						c.item.destroy();
					}
					catch(final RuntimeException e)
					{
						Log.errOut("ItemCrafting",e);
					}
			box.finish(true);
			JSEvents.fire("itemcrafted",who,item,ModuleDefinitions.json(receipt));
			ProgressionTracks.crafted(who,InstanceIds.id(who)+"#"+serial);
			// P01: one settled craft, once
		}
		finally
		{
			busy.remove();
		}
		return "Committed craft #"+serial+": "+op.name+"; consumed "+plan.paid()+"; item revision "
			+next.get("revision")+(ItemModifierRules.DRAWS.contains(op.string("action"))?"; drew "+(drew.isEmpty()?"nothing":String.join(", ",drew)):"")
			+". Repeating this request cannot consume or apply it again.";
	}
	/** I04: MODCRAFT BEGIN [recipe]: a work piece for a staged recipe, set out at its station. It spends and draws nothing. */
	public static String begin(final MOB who,final String recipeID)throws IOException
	{
		writable();
		final Definition r=ItemModifiers.definitions().find(recipeID,0,"recipe");
		if(who==null||who.amDead()||who.location()==null||who.isInCombat())
			throw new IOException("begin outside combat, alive in a room");
		if(!Workstations.has(who.location(),r.string("station")))
			throw new IOException("required workstation: "+r.string("station"));
		final Item piece=CMClass.getItem("GenItem");
		piece.setName(r.string("piece"));
		piece.setDisplayText(r.string("piece")+" lies here.");
		piece.basePhyStats().setWeight(1);
		piece.recoverPhyStats();
		final JSONObject order=new JSONObject();
		order.put("schema",Long.valueOf(1));
		order.put("recipe",ItemModifierRules.pin(r));
		order.put("stage",Long.valueOf(0));
		final Ability p=CMClass.getAbility("Prop_WorkOrder");
		p.setMiscText(ModuleDefinitions.json(order));
		piece.addNonUninvokableEffect(p);
		InstanceIds.id(piece);
		who.addItem(piece);
		return "You set out "+piece.Name()+" for "+r.name+": "+stages(r).length+" stage(s). MODCRAFT PREVIEW "+r.id+" \""+piece.Name()
			+"\" shows the next; PREPARE and COMMIT settle each, one at a time.";
	}
	/** One stage of a staged recipe, settled once: its materials spent, the piece's stage advanced, and after the last the product made. */
	private static String commitStage(final MOB who,final JSONObject state,final JSONObject pending,final long serial,final Item piece,
		final Definition recipe,final List<Definition> cats)throws IOException
	{
		final Plan plan=planStage(who,piece,recipe,cats);
		if(!pending.get("selector").equals(plan.selector))
			throw new IOException(piece.name(who)+" is at another stage now; cancel and prepare again");
		if(!pending.get("item_hash").equals(fingerprint(piece))
			||!ModuleDefinitions.json((JSONObject)pending.get("costs")).equals(ModuleDefinitions.json(costs(plan.costs,false))))
			throw new IOException("work piece or material piles changed; cancel and prepare again");
		final boolean last=plan.stage==stages(plan.op).length-1;
		final JSONObject st=stageOf(plan.op,plan.stage);
		final Long rng=CMath.getRandState();
		final Ability order=piece.fetchEffect("Prop_WorkOrder");
		final String oldOrder=order.text();
		Item result=null;
		final JSONObject receipt;
		final JSEvents.ItemOutbox box;
		try
		{
			if(last)
				result=product(plan.op,plan.bias);
			// the only random draws of a recipe: the product's modifiers, at its last stage
			receipt=new JSONObject();
			receipt.put("request",Long.valueOf(serial));
			receipt.put("item",pending.get("item"));
			receipt.put("operation",pending.get("operation"));
			receipt.put("stage",Long.valueOf(plan.stage+1));
			receipt.put("before",pending.get("item_hash"));
			receipt.put("material",st.get("material"));
			receipt.put("units",st.get("units"));
			receipt.put("inputs",pending.get("costs"));
			if(pending.containsKey("catalysts"))
				receipt.put("catalysts",pending.get("catalysts"));
			if(result!=null)
				receipt.put("result",InstanceIds.reference(result));
			final JSONObject receipts=field(state,"receipts");
			receipts.put(Long.toString(serial),receipt);
			state.put("receipts",pruned(receipts,serial));
			state.put("pending","");
			state.put("settled",Long.valueOf(serial));
			box=JSEvents.deferItemEvents();
		}
		catch(final IOException|RuntimeException e)
		{
			if(result!=null)
				result.destroy();
			CMath.setRandState(rng.longValue());
			throw e;
		}
		boolean committed=false;
		busy.set(Boolean.TRUE);
		critical=true;
		try
		{
			for(final Cost c:plan.costs)
				if(c.amount<c.weight)
					c.spend();
			if(last)
			{
				who.delItem(piece);
				who.addItem(result);
			}
			else
			{
				final JSONObject next=ModuleDefinitions.copy(plan.data);
				next.put("stage",Long.valueOf(plan.stage+1));
				order.setMiscText(ModuleDefinitions.json(next));
			}
			for(final Cost c:plan.costs)
				if(c.amount==c.weight)
				{
					who.delItem(c.item);
					c.item.setOwner(null);
				}
			save(who,state);
			committed=true;
		}
		catch(final IOException|RuntimeException e)
		{
			order.setMiscText(oldOrder);
			if(last)
			{
				who.delItem(result);
				result.destroy();
				if(piece.owner()!=who)
					who.addItem(piece);
			}
			for(final Cost c:plan.costs)
			{
				c.restore();
				if(c.item.owner()!=who)
					who.addItem(c.item);
			}
			CMath.setRandState(rng.longValue());
			throw e;
		}
		finally
		{
			critical=false;
			if(!committed)
			{
				box.finish(false);
				busy.remove();
			}
		}
		try
		{
			for(final Cost c:plan.costs)
				if(c.amount==c.weight)
					try
					{
						c.item.destroy();
					}
					catch(final RuntimeException e)
					{
						Log.errOut("ItemCrafting",e);
					}
			if(last)
				try
				{
					piece.destroy();
				}
				catch(final RuntimeException e)
				{
					Log.errOut("ItemCrafting",e);
				}
			box.finish(true);
			JSEvents.fire("itemcrafted",who,last?result:piece,ModuleDefinitions.json(receipt));
			if(last)
				ProgressionTracks.crafted(who,InstanceIds.id(who)+"#"+serial);
			// P01: a finished product, once
		}
		finally
		{
			busy.remove();
		}
		return "Committed craft #"+serial+": "+plan.op.name+", stage "+(plan.stage+1)+" of "+stages(plan.op).length+" ("+st.get("name")+"); consumed "
			+plan.paid()+". "
			+(last?result.name(who)+" is finished.":piece.name(who)+" waits for stage "+(plan.stage+2)+".")
			+" Repeating this request cannot consume or apply it again.";
	}
	/** A recipe's finished product: its catalog item, made with its base's modifiers or by its loot policy. */
	private static Item product(final Definition recipe,final Bias bias)throws IOException
	{
		final JSONObject r=(JSONObject)recipe.raw.get("result");
		final Item proto=CMLib.catalog().getCatalogItem((String)r.get("catalog"));
		if(proto==null)
			throw new IOException("no catalog item '"+r.get("catalog")+"' for the result");
		final Item made=(Item)proto.copyOf();
		made.setOwner(null);
		made.setContainer(null);
		made.unWear();
		try
		{
			ItemModifiers.initialize(made,r.containsKey("policy")?"POLICY="+r.get("policy"):(String)r.get("base"),r.containsKey("modifiers")
				?((Number)r.get("modifiers")).intValue():0,"recipe:"+recipe.key(),bias);
		}
		catch(final IOException|RuntimeException e)
		{
			made.destroy();
			throw e;
		}
		return made;
	}
}
