package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.ItemModifierRules.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.threads.ServiceEngine;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Adapter over existing affix definitions, generation, item identity and combat-stat sources. */
public final class ItemModifiers
{
	private ItemModifiers()
	{
	}
	public static final String MODULE=ItemModifierRules.MODULE, PROPERTY="Prop_ItemModifiers";
	private static Library library;
	private static File root;
	public static synchronized void reset()
	{
		library=null;
		root=null;
	}
	/** The family cannot run now (module off, definitions failing CHECK, off the game thread). Nothing was spent: a loot table keeps its once-only roll. */
	public static final class Unavailable extends IOException
	{
		public Unavailable(final String message)
		{
			super(message);
		}
	}
	public static boolean unavailable(final Throwable failure)
	{
		for(Throwable t=failure;t!=null;t=t.getCause())
			if(t instanceof Unavailable)
				return true;
		return false;
	}
	public static void require()throws IOException
	{
		if(!OptionalModules.enabled(MODULE))
			throw new Unavailable("items.modifiers is inactive in this campaign");
	}
	public static int limit()throws IOException
	{
		require();
		return ((Number)OptionalModules.setting(MODULE,"max_modifiers")).intValue();
	}
	/** Item mutations run on the game thread. Before it starts, the boot loading the world is the only thread, and generates spawn loot. */
	public static void gameThread()throws IOException
	{
		if(!ServiceEngine.isGameThread()&&!booting())
			throw new Unavailable("item mutations must run on the game thread");
	}
	private static boolean booting()
	{
		final CMProps host=CMProps.instance(com.planet_ink.coffee_mud.core.interfaces.MudHost.MAIN_HOST);
		return CMProps.isSinglePlayer()&&!ServiceEngine.hasGameThread()&&host!=null
			&&(host._isState(CMProps.HostState.BOOTING)||host._isState(CMProps.HostState.LOADINGMAP)||host._isState(CMProps.HostState.STARTING));
	}
	/** The world or this campaign opts in. Otherwise a legacy affixes.xml keeps its old reading, and CHECK only warns. */
	public static boolean inUse()
	{
		if(OptionalModules.enabled(MODULE))
			return true;
		final File world=WorldFiles.worldDir();
		return world!=null&&new File(world,"itemmods").exists()||OptionalModules.configuredPlan().enabled.contains(MODULE);
	}
	public static Library check()
	{
		final File world=WorldFiles.worldDir(),file=new File(world,"affixes.xml");
		final List<JSONObject> records=new ArrayList<JSONObject>();
		String error=null;
		if(file.exists()&&(Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()>262144))
		{
			if(inUse())
				error="affixes.xml must be a regular world file, at most 256 KiB";
		}
		else if(file.exists())
			for(final Affixes.Affix a:Affixes.read())
				if(a.tracked!=null)
					records.add(a.tracked);
		final Library lib=ItemModifierRules.load(world,records);
		if(error!=null)
			lib.errors.add(error);
		for(final Definition d:lib.definitions.values())
			if(d.kind.equals("operation")&&RawMaterial.CODES.FIND_CaseSensitive(d.string("material"))<0)
				lib.errors.add(d.key()+": unknown registered material "+d.string("material"));
		for(final Definition d:lib.definitions.values())
			if(d.kind.equals("catalyst")&&RawMaterial.CODES.FIND_CaseSensitive(d.string("material"))<0)
				lib.errors.add(d.key()+": unknown registered material "+d.string("material"));
		// I09: a material's traits are of one registered resource, and one definition (its latest version) says them
		final Map<String,String> traitsOf=new HashMap<String,String>();
		for(final Definition d:lib.definitions.values())
			if(d.kind.equals("material"))
			{
				if(RawMaterial.CODES.FIND_CaseSensitive(d.string("material"))<0)
					lib.errors.add(d.key()+": unknown registered material "+d.string("material"));
				final String other=traitsOf.put(d.string("material"),d.id);
				if(other!=null&&!other.equals(d.id))
					lib.errors.add(d.key()+": "+d.string("material")+" has its traits from "+other+" already");
			}
		for(final Definition d:lib.definitions.values())
			if(d.kind.equals("recipe"))
				for(final Object st:(Object[])d.raw.get("stages"))
					// I04: a staged recipe's materials
					if(RawMaterial.CODES.FIND_CaseSensitive((String)((JSONObject)st).get("material"))<0)
						lib.errors.add(d.key()+": unknown registered material "+((JSONObject)st).get("material"));
		return lib;
	}
	public static synchronized Library definitions()throws IOException
	{
		if(library==null||!WorldFiles.worldDir().equals(root))
		{
			library=check();
			root=WorldFiles.worldDir();
		}
		if(!library.errors.isEmpty())
			throw new Unavailable(library.report());
		return library;
	}
	public static synchronized String reload()throws IOException
	{
		final Library next=check();
		if(!next.errors.isEmpty())
			throw new IOException(next.report());
		library=next;
		root=WorldFiles.worldDir();
		Affixes.reload();
		WorldFiles.acceptItemModifierChanges();
		return next.report()+" Existing items retain their saved rolls and pins.";
	}
	public static String list()throws IOException
	{
		final StringBuilder out=new StringBuilder();
		for(final Definition d:definitions().definitions.values())
			out.append(d.kind).append(' ').append(d.key()).append(" - ").append(d.name).append('\n');
		return out.toString();
	}
	public static boolean isManaged(final Item item)
	{
		return item!=null&&item.fetchEffect(PROPERTY)!=null;
	}
	static Ability property(final Item item)throws IOException
	{
		final Ability p=item==null?null:item.fetchEffect(PROPERTY);
		if(p==null||!p.getClass().getName().equals("com.planet_ink.coffee_mud.Abilities.Properties."+PROPERTY))
			throw new IOException("item has no supported structured modifier property");
		return p;
	}
	public static JSONObject data(final Item item)throws IOException
	{
		return bound(item,property(item).text());
	}
	/** The record only counts on its own item: a generic copy shares its INSTANCEID, but is not the item that ID finds. */
	public static JSONObject bound(final Item item,final String text)throws IOException
	{
		final JSONObject data=ItemModifierRules.parse(text);
		if(!data.get("item").equals(InstanceIds.PREFIX+InstanceIds.existing(item))||InstanceIds.find((String)data.get("item"))!=item)
			throw new IOException("modifier record is bound to another item; managed copies need ItemModifiers.copyOf");
		if(!data.get("item_kind").equals(Affixes.kindOf(item)))
			throw new IOException("item kind differs from its modifier record");
		return data;
	}
	private static final Roller DICE=(min,max)->min+CMLib.dice().roll(1,max-min+1,-1);
	static Roller dice()
	{
		return DICE;
	}
	private static String uid()
	{
		return UUID.randomUUID().toString();
	}
	static void store(final Item item,final JSONObject data)throws IOException
	{
		property(item).setMiscText(ModuleDefinitions.json(ItemModifierRules.checked(data)));
	}
	static JSONObject names(final Item item,final JSONObject next)throws IOException
	{
		final String before=(String)next.get("rendered_name"),display=(String)next.get("rendered_display");
		// A deliberate external rename remains an override, independent of modifier identity.
		final boolean nameOverride=Boolean.TRUE.equals(next.get("name_override"))
			||!item.Name().equals(before),displayOverride=Boolean.TRUE.equals(next.get("display_override"))||!item.displayText().equals(display);
		final String name=nameOverride?item.Name():ItemModifierRules.renderedName(next);
		final String baseDisplay=(String)next.get("base_display"),baseName=(String)next.get("base_name");
		final int at=baseDisplay.toLowerCase(Locale.ROOT).indexOf(baseName.toLowerCase(Locale.ROOT));
		final String shown=displayOverride?item.displayText():at<0?baseDisplay
			:baseDisplay.substring(0,at)+name+baseDisplay.substring(at+baseName.length());
		next.put("name_override",Boolean.valueOf(nameOverride));
		next.put("display_override",Boolean.valueOf(displayOverride));
		next.put("rendered_name",name);
		next.put("rendered_display",shown);
		return ItemModifierRules.checked(next);
	}
	static void install(final Item item,final JSONObject data)throws IOException
	{
		store(item,data);
		item.setName((String)data.get("rendered_name"));
		item.setDisplayText((String)data.get("rendered_display"));
	}
	public static void initialize(final Item item,final String baseID,final int count,final String source)throws IOException
	{
		initialize(item,baseID,count,source,Bias.NONE);
	}
	/** The same, with a recipe's catalysts shaping the drawn modifiers (I06). */
	public static void initialize(final Item item,final String baseID,final int count,final String source,final Bias bias)throws IOException
	{
		gameThread();
		require();
		if(item==null||item.owner()!=null||item.amDestroyed()||isManaged(item))
			throw new IOException("MODBASE needs a fresh unowned item");
		if(!Arrays.asList("GenArmor","GenWeapon","GenItem","GenAmmunition").contains(item.ID()))
			throw new IOException("first modifier family supports standard generic equipment/items only");
		if(baseID.regionMatches(true,0,"POLICY=",0,7))
		{
			ItemLoot.initialize(item,baseID.substring(7).trim(),source,bias);
			return;
		}
		// I02: a loot policy decides rarity and modifiers
		final Definition base=definitions().find(baseID,0,"base");
		if(!base.string("item_kind").equals(Affixes.kindOf(item)))
			throw new IOException("MODBASE kind does not match template");
		final JSONObject data=ItemModifierRules.record(base,InstanceIds.reference(item),item.Name(),item.displayText(),source);
		final Ability p=CMClass.getAbility(PROPERTY);
		p.setMiscText(ModuleDefinitions.json(data));
		item.addNonUninvokableEffect(p);
		try
		{
			addGenerated(item,count,bias);
		}
		catch(final IOException e)
		{
			item.delEffect(p);
			throw e;
		}
	}
	/**
	 * I17: makes a fresh unowned item a tracked item of a base with modifiers
	 * (and veils) already drawn, as an offering's chosen option: each gets a
	 * new identity of the item's own.
	 */
	public static void initializeWith(final Item item,final Definition base,final List<JSONObject> mods,final List<JSONObject> veils,final String source)throws IOException
	{
		gameThread();
		require();
		if(item==null||item.owner()!=null||item.amDestroyed()||isManaged(item))
			throw new IOException("a chosen option needs a fresh unowned item");
		if(!base.string("item_kind").equals(Affixes.kindOf(item)))
			throw new IOException("base "+base.key()+" is for "+base.string("item_kind")+", the catalog item is "+Affixes.kindOf(item));
		final JSONObject data=ItemModifierRules.record(base,InstanceIds.reference(item),item.Name(),item.displayText(),source);
		final List<JSONObject> own=new ArrayList<JSONObject>(),ownVeils=new ArrayList<JSONObject>();
		for(final JSONObject m:mods)
		{
			final JSONObject c=ModuleDefinitions.copy(m);
			c.put("uid",uid());
			own.add(c);
		}
		for(final JSONObject v:veils)
		{
			final JSONObject c=ModuleDefinitions.copy(v);
			c.put("uid",uid());
			ownVeils.add(c);
		}
		data.put("modifiers",own.toArray());
		final Ability p=CMClass.getAbility(PROPERTY);
		p.setMiscText(ModuleDefinitions.json(ItemModifierRules.checked(ItemModifierRules.withVeils(data,ownVeils))));
		item.addNonUninvokableEffect(p);
		try
		{
			install(item,names(item,data(item)));
		}
		catch(final IOException|RuntimeException e)
		{
			item.delEffect(p);
			throw e;
		}
	}
	/** Generation-only addition through the old AFFIXES consumers. Owned objects use transactions. */
	public static int addGenerated(final Item item,final int count)throws IOException
	{
		return addGenerated(item,count,Bias.NONE);
	}
	/** The same, shaped by a recipe's catalysts (I06); with none, exactly the draws it always made. */
	public static int addGenerated(final Item item,final int count,final Bias bias)throws IOException
	{
		gameThread();
		require();
		if(item.owner()!=null||item.amDestroyed())
			throw new IOException("generated affixes may only be added before placement");
		final JSONObject data=data(item);
		if(((Number)data.get("revision")).longValue()!=0)
			throw new IOException("crafted items cannot use generation-only addition");
		final Library lib=definitions();
		final Definition base=lib.pinned(data.get("base"),"base");
		final List<JSONObject> mods=ItemModifierRules.modifiers(data);
		final List<Definition> pool=new ArrayList<Definition>();
		for(final String id:base.list("pool"))
			pool.add(lib.find(id,0,"affix"));
		final int count2=Math.max(0,Math.min(Math.min(ItemModifierRules.MAX_MODS,Math.max(0,count)),limit()-ItemModifierRules.slots(data)));
		final List<JSONObject> drawn=ItemModifierRules.draw(data,pool,bias,item.basePhyStats().level(),count2,ItemModifierRules.OPEN,
			()->CMLib.dice().getRandomizer().nextDouble(),DICE,ItemModifiers::uid);
		mods.addAll(drawn);
		data.put("modifiers",mods.toArray());
		install(item,names(item,data));
		return drawn.size();
	}
	/** Explicit managed clone: preserve rolls, assign a new item and per-item modifier identities. */
	public static Item copyOf(final Item original)throws IOException
	{
		gameThread();
		require();
		final JSONObject data=data(original);
		final Item copy=(Item)original.copyOf();
		copy.setOwner(null);
		copy.setContainer(null);
		copy.unWear();
		InstanceIds.restore(copy,uid());
		data.put("item",InstanceIds.reference(copy));
		data.put("revision",Long.valueOf(0));
		final List<JSONObject> mods=ItemModifierRules.modifiers(data);
		for(final JSONObject m:mods)
			m.put("uid",uid());
		data.put("modifiers",mods.toArray());
		// I07: a copy's veils, and their options, are its own
		final List<JSONObject> veils=ItemModifierRules.veils(data);
		for(final JSONObject v:veils)
		{
			v.put("uid",uid());
			if(v.containsKey("options"))
				for(final Object o:(Object[])v.get("options"))
					((JSONObject)o).put("uid",uid());
		}
		store(copy,ItemModifierRules.withVeils(data,veils));
		return copy;
	}
	public static String inspect(final Item item)throws IOException
	{
		final JSONObject data=data(item);
		final List<JSONObject> mods=ItemModifierRules.modifiers(data);
		final StringBuilder out=new StringBuilder(item.Name()+"\nBase: "+((JSONObject)data.get("base")).get("id")+"; revision "+data.get("revision")
			+"; capacity "+data.get("prefixes")+" prefix / "+data.get("suffixes")+" suffix\nOrigin: "+data.get("source"));
		if(!OptionalModules.enabled(MODULE))
			out.append("\nModifier bonuses/crafting suspended: module inactive.");
		for(int i=0;i<mods.size();i++)
		{
			final JSONObject m=mods.get(i),pin=(JSONObject)m.get("pin");
			out.append("\n").append(i+1).append(". ").append(m.get("name")).append(" ").append(m.get("position")).append(" tier ")
				.append(m.get("tier")).append(Boolean.TRUE.equals(m.get("protected"))?" [protected]":"").append(" - ")
				.append(ModuleDefinitions.json((JSONObject)m.get("values"))).append("; source ").append(pin.get("id")).append(" v")
				.append(pin.get("version")).append("; group ").append(m.get("group"));
		}
		// I09: the material's traits and the quality, each with what reads it (inspection goes on when the definitions cannot be read)
		try
		{
			final Library lib=definitions();
			final Definition base=lib.pinned(data.get("base"),"base");
			for(final String line:ItemModifierRules.explain(data,base,materialOf(item),RawMaterial.CODES.NAME(item.material())))
				out.append("\n").append(line);
			for(int i=0;i<mods.size();i++)
			{
				final int pct=ItemModifierRules.qualityPercent(base,mods.get(i),ItemModifierRules.quality(data));
				if(pct>0)
					out.append("\n  modifier ").append(i+1).append(" counts ").append(ModuleDefinitions.json(ItemModifierRules.counted(base,mods.get(i),ItemModifierRules.quality(data))))
						.append(" (quality +").append(pct).append("%)");
			}
		}
		catch(final IOException e)
		{
			out.append("\nMaterial and quality: not shown (").append(e.getMessage()).append(')');
		}
		// I07: veiled modifiers, and a revealed one's options
		final List<JSONObject> veils=ItemModifierRules.veils(data);
		for(int i=0;i<veils.size();i++)
		{
			final JSONObject v=veils.get(i);
			out.append("\nVeiled ").append(i+1).append(". a veiled ").append(v.get("position")).append(" (").append(v.get("source")).append(")");
			if(!v.containsKey("options"))
				out.append(": not yet revealed; it gives nothing until it is");
			else
			{
				final Object[] options=(Object[])v.get("options");
				out.append(": revealed; CHOICES PICK \"").append(item.Name()).append("\" [number] settles one of:");
				for(int k=0;k<options.length;k++)
				{
					final JSONObject m=(JSONObject)options[k];
					out.append("\n   ").append(k+1).append(") ").append(m.get("name")).append(" tier ").append(m.get("tier")).append(" - ")
						.append(ModuleDefinitions.json((JSONObject)m.get("values"))).append("; group ").append(m.get("group"));
				}
			}
		}
		return out.toString();
	}
	/** I09: the material definition of an item's resource, its latest version, or null */
	public static Definition materialOf(final Item item)throws IOException
	{
		if(item==null)
			return null;
		final String name=RawMaterial.CODES.NAME(item.material());
		Definition best=null;
		for(final Definition d:definitions().definitions.values())
			if(d.kind.equals("material")&&d.string("material").equals(name)&&(best==null||d.version>best.version))
				best=d;
		return best;
	}
	/** I06: the first pick of a recipe's product (its base's or its policy's) at a level, shaped by catalysts */
	public static List<Odds> firstPick(final String baseOrPolicy,final int level,final Bias bias)throws IOException
	{
		if(baseOrPolicy.regionMatches(true,0,"POLICY=",0,7))
			return ItemLoot.firstPick(baseOrPolicy.substring(7).trim(),level,bias);
		final Library lib=definitions();
		final Definition base=lib.find(baseOrPolicy,0,"base");
		final List<Definition> pool=new ArrayList<Definition>();
		for(final String id:base.list("pool"))
			pool.add(lib.find(id,0,"affix"));
		return ItemModifierRules.odds(ItemModifierRules.record(base,"@instance:00000000-0000-0000-0000-000000000000","an item","An item lies here.","preview"),
			pool,bias,true,ItemModifierRules.OPEN,0,level);
	}
	public static List<String> checkItems(final Collection<Room> rooms,final Library lib)
	{
		final Set<Item> seen=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());
		final List<String> errors=new ArrayList<String>();
		for(final Room r:rooms)
		{
			for(final Enumeration<Item> e=r.items();e.hasMoreElements();)
				seen.add(e.nextElement());
			for(final Enumeration<MOB> m=r.inhabitants();m.hasMoreElements();)
				for(final Enumeration<Item> i=m.nextElement().items();i.hasMoreElements();)
					seen.add(i.nextElement());
		}
		for(final Item i:seen)
			if(isManaged(i))
				try
				{
					final JSONObject d=data(i);
					ItemModifierRules.validateSources(d,lib);
					if(OptionalModules.enabled(MODULE)&&ItemModifierRules.slots(d)>limit())
						throw new IOException("more modifiers than this campaign's limit of "+limit()+"; its bonuses are withheld");
				}
				catch(final IOException e)
				{
					errors.add(i.Name()+": "+e.getMessage());
				}
		return errors;
	}
}
