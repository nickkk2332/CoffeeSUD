package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Pure structured modifier schemas and transformations. No world mutation or implicit random calls. */
public final class ItemModifierRules
{
	private ItemModifierRules()
	{
	}
	public static final String MODULE = "items.modifiers";
	public static final int MAX_MODS = 6, MAX_TEXT = 32768;
	public interface Roller
	{
		int roll(int minimum, int maximum);
	}
	private static int integer(final Object value, final String where, final int min, final int max) throws IOException
	{
		if(!(value instanceof Long || value instanceof Integer))
			throw new IOException(where + " must be an integer");
		final long n = ((Number)value).longValue();
		if(n < min || n > max)
			throw new IOException(where + " outside " + min + ".." + max);
		return (int)n;
	}
	private static String text(final Object raw, final String where) throws IOException
	{
		final String s = ModuleDefinitions.string(raw,where);
		if(s.isEmpty() || s.length()>160 || s.matches("(?s).*[\\p{Cntrl}^<>].*"))
			throw new IOException(where + " must be 1..160 plain text characters");
		return s;
	}
	private static List<String> ids(final Object raw, final String where, final int max) throws IOException
	{
		final List<String> out = new ArrayList<String>();
		for(final Object entry : ModuleDefinitions.array(raw,where))
		{
			final String id=ModuleDefinitions.id(entry);
			if(out.contains(id))
				throw new IOException(where+": duplicate "+id);
			out.add(id);
		}
		if(out.size()>max)
			throw new IOException(where+": at most "+max);
		return Collections.unmodifiableList(out);
	}
	public static String hash(final JSONObject value) throws IOException
	{
		try
		{
			final StringBuilder out=new StringBuilder();
			for(final byte b:MessageDigest.getInstance("SHA-256").digest(ModuleDefinitions.json(value).getBytes(StandardCharsets.UTF_8)))
				out.append(String.format("%02x",Integer.valueOf(b&255)));
			return out.toString();
		}
		catch(final Exception e)
		{
			throw new IOException(e);
		}
	}
	public static JSONObject pin(final Definition d)
	{
		final JSONObject p=new JSONObject();
		p.put("id",d.id);
		p.put("version",Long.valueOf(d.version));
		p.put("hash",d.hash);
		return p;
	}
	public static JSONObject checkPin(final Object raw) throws IOException
	{
		final JSONObject p=ModuleDefinitions.object(raw,"definition pin");
		ModuleDefinitions.keys(p,"pin","id","version","hash");
		ModuleDefinitions.id(p.get("id"));
		ModuleDefinitions.integer(p.get("version"),"pin version");
		if(!ModuleDefinitions.string(p.get("hash"),"pin hash").matches("[0-9a-f]{64}"))
			throw new IOException("invalid pin hash");
		return ModuleDefinitions.copy(p);
	}
	public static final class Definition
	{
		public final String id,kind,name,hash;
		public final int version;
		public final JSONObject raw;
		public Definition(final JSONObject obj) throws IOException
		{
			if(ModuleDefinitions.integer(obj.get("schema"),"item schema")!=1)
				throw new IOException("unsupported item schema");
			id=ModuleDefinitions.id(obj.get("id"));
			version=ModuleDefinitions.integer(obj.get("version"),"version");
			kind=ModuleDefinitions.string(obj.get("kind"),"kind");
			name=text(obj.get("name"),"name");
			raw=ModuleDefinitions.copy(obj);
			if(kind.equals("base"))
			{
				ModuleDefinitions.keys(obj,"modifier base","schema","kind","id","version","name","item_kind","prefixes","suffixes","tags","pool",
					"successor");
				if(obj.containsKey("successor")&&ModuleDefinitions.id(obj.get("successor")).equals(id))
					throw new IOException("a base cannot be its own successor");
				if(!Arrays.asList("armor","weapon","ammo","item").contains(obj.get("item_kind")))
					throw new IOException("unknown item_kind");
				final int pre=integer(obj.get("prefixes"),"prefixes",0,3),suf=integer(obj.get("suffixes"),"suffixes",0,3);
				if(pre+suf<1)
					throw new IOException("base needs modifier capacity");
				ids(obj.get("tags"),"base tags",16);
				if(ids(obj.get("pool"),"affix pool",32).isEmpty())
					throw new IOException("empty affix pool");
			}
			else if(kind.equals("affix"))
			{
				ModuleDefinitions.keys(obj,"tracked affix","schema","kind","id","version","name","position","tier","group","tags","kinds",
					"minimum_level","maximum_level","weight","power","cursed","effects","rolls");
				if(!Arrays.asList("prefix","suffix").contains(obj.get("position")))
					throw new IOException("unknown affix position");
				integer(obj.get("tier"),"tier",1,100);
				ModuleDefinitions.id(obj.get("group"));
				ids(obj.get("tags"),"affix tags",16);
				final List<String> kinds=ids(obj.get("kinds"),"affix kinds",5);
				if(kinds.isEmpty()||!Arrays.asList("armor","weapon","ammo","item","any").containsAll(kinds))
					throw new IOException("invalid affix kinds");
				final int lo=integer(obj.get("minimum_level"),"minimum level",1,100000);
				integer(obj.get("maximum_level"),"maximum level",lo,100000);
				if(!(obj.get("weight") instanceof Number)||!Double.isFinite(((Number)obj.get("weight")).doubleValue())
					||((Number)obj.get("weight")).doubleValue()<=0||((Number)obj.get("weight")).doubleValue()>1000000)
					throw new IOException("weight must be 0..1000000, exclusive of zero");
				if(integer(obj.get("power"),"tracked power",0,0)!=0||!Boolean.FALSE.equals(obj.get("cursed"))
					||ModuleDefinitions.array(obj.get("effects"),"effects").length!=0)
					throw new IOException("tracked numeric affixes require power=0, no curse and no AFFECT entries");
				final JSONObject rolls=ModuleDefinitions.object(obj.get("rolls"),"rolls");
				if(rolls.isEmpty()||rolls.size()>8)
					throw new IOException("affix needs 1..8 numeric rolls");
				for(final String stat:rolls.keySet())
				{
					final JSONObject r=ModuleDefinitions.object(rolls.get(stat),"roll");
					ModuleDefinitions.keys(r,"roll","min","max");
					final int min=integer(r.get("min"),"minimum roll",-1000000,1000000),max=integer(r.get("max"),"maximum roll",min,1000000);
					final JSONObject a=new JSONObject(),b=new JSONObject();
					a.put(stat,Long.valueOf(min));
					b.put(stat,Long.valueOf(max));
					stats(a);
					stats(b);
				}
			}
			else if(kind.equals("operation"))
			{
				ModuleDefinitions.keys(obj,"craft operation","schema","kind","id","version","name","action","station","material","units","item_tags",
					"modifier_tags","affixes","exclude_tags","weights","guarantee","catalysts");
				if(!Arrays.asList("add","remove","reroll","protect","unprotect","replace","upgrade","imbue","reforge").contains(obj.get("action")))
					throw new IOException("unknown operation action");
				// I06: only the operations that draw their modifiers take a distribution and catalysts
				final boolean draws=DRAWS.contains(obj.get("action"));
				if(!draws&&(obj.containsKey("exclude_tags")||obj.containsKey("weights")||obj.containsKey("guarantee")||obj.containsKey("catalysts")))
					throw new IOException("exclude_tags, weights, guarantee and catalysts belong to operations that draw (imbue, reforge)");
				Bias.of(obj,"operation "+id);
				if(obj.containsKey("catalysts"))
					ids(obj.get("catalysts"),"catalysts",8);
				ModuleDefinitions.id(obj.get("station"));
				final String material=ModuleDefinitions.string(obj.get("material"),"material");
				if(!material.matches("[A-Z][A-Z0-9 _-]{0,79}"))
					throw new IOException("material must be an uppercase resource name");
				integer(obj.get("units"),"material units",1,1000);
				ids(obj.get("item_tags"),"item tags",16);
				ids(obj.get("modifier_tags"),"modifier tags",16);
				ids(obj.get("affixes"),"allowed affixes",32);
			}
			else if(kind.equals("recipe"))
			{
				// I04: a staged production recipe: a work piece, stages settled one at a time, then the result
				ModuleDefinitions.keys(obj,"staged recipe","schema","kind","id","version","name","station","piece","stages","result","catalysts");
				if(obj.containsKey("catalysts"))
					ids(obj.get("catalysts"),"catalysts",8);
				ModuleDefinitions.id(obj.get("station"));
				text(obj.get("piece"),"piece");
				final Object[] stages=ModuleDefinitions.array(obj.get("stages"),"stages");
				if(stages.length<1||stages.length>6)
					throw new IOException("a recipe has 1..6 stages");
				for(final Object st:stages)
				{
					final JSONObject stage=ModuleDefinitions.object(st,"stage");
					ModuleDefinitions.keys(stage,"stage","name","material","units");
					text(stage.get("name"),"stage name");
					if(!ModuleDefinitions.string(stage.get("material"),"stage material").matches("[A-Z][A-Z0-9 _-]{0,79}"))
						throw new IOException("stage material must be an uppercase resource name");
					integer(stage.get("units"),"stage units",1,1000);
				}
				final JSONObject result=ModuleDefinitions.object(obj.get("result"),"result");
				ModuleDefinitions.keys(result,"result","catalog","base","modifiers","policy");
				text(result.get("catalog"),"result catalog item");
				if(result.containsKey("policy")==result.containsKey("base"))
					throw new IOException("a recipe result names a base (with modifiers) or a loot policy");
				if(result.containsKey("base"))
				{
					ModuleDefinitions.id(result.get("base"));
					integer(result.get("modifiers"),"result modifiers",0,MAX_MODS);
					if(obj.containsKey("catalysts")&&integer(result.get("modifiers"),"result modifiers",0,MAX_MODS)==0)
						throw new IOException("catalysts shape the modifiers a product draws; this result draws none");
				}
				else
				{
					ModuleDefinitions.id(result.get("policy"));
					if(result.containsKey("modifiers"))
						throw new IOException("a policy result's rarity decides its modifiers");
				}
			}
			else if(kind.equals("catalyst"))
			{
				// I06: an ingredient named at PREPARE that shapes what a craft draws, paid with its costs
				ModuleDefinitions.keys(obj,"catalyst","schema","kind","id","version","name","material","units","exclude_tags","weights","guarantee");
				if(!ModuleDefinitions.string(obj.get("material"),"material").matches("[A-Z][A-Z0-9 _-]{0,79}"))
					throw new IOException("material must be an uppercase resource name");
				integer(obj.get("units"),"material units",1,1000);
				if(Bias.of(obj,"catalyst "+id).none())
					throw new IOException("a catalyst excludes tags, weights tags or guarantees a family");
			}
			else
				throw new IOException("unknown item definition kind");
			hash=hash(raw);
		}
		public String key()
		{
			return id+".v"+version;
		}
		public String string(final String field)
		{
			return (String)raw.get(field);
		}
		public int number(final String field)
		{
			return ((Number)raw.get(field)).intValue();
		}
		public List<String> list(final String field) throws IOException
		{
			return ids(raw.get(field),field,32);
		}
		/** a list field that may be left out */
		public List<String> optional(final String field) throws IOException
		{
			return raw.containsKey(field)?ids(raw.get(field),field,32):Collections.<String>emptyList();
		}
	}
	/** I06: the operations that draw their modifiers */
	public static final List<String> DRAWS=Collections.unmodifiableList(Arrays.asList("imbue","reforge"));
	/**
	 * I06: what shapes a draw: tags that leave affixes out, multipliers on the
	 * weights of affixes with a tag (all that apply multiply together), and a
	 * family (a tag) that the first modifier drawn must have.  An operation's own
	 * fields and each catalyst's add up; each part remembers where it came from,
	 * for the preview.
	 */
	public static final class Bias
	{
		public static final Bias NONE=new Bias();
		public final Map<String,String> excluded=new TreeMap<String,String>();
		public final Map<String,Double> weights=new TreeMap<String,Double>();
		public final Map<String,List<String>> weightedBy=new TreeMap<String,List<String>>();
		public String guarantee="", guaranteedBy="";
		public final List<String> sources=new ArrayList<String>();

		/** a definition's exclude_tags, weights and guarantee */
		public static Bias of(final JSONObject raw,final String source) throws IOException
		{
			final Bias b=new Bias();
			if(raw.containsKey("exclude_tags"))
				for(final String t:ids(raw.get("exclude_tags"),"exclude_tags",16))
					b.excluded.put(t,source);
			if(raw.containsKey("weights"))
			{
				final JSONObject w=ModuleDefinitions.object(raw.get("weights"),"weights");
				if(w.size()>16)
					throw new IOException("weights: at most 16 tags");
				for(final String tag:w.keySet())
				{
					ModuleDefinitions.id(tag);
					final Object v=w.get(tag);
					if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue())||((Number)v).doubleValue()<0.01||((Number)v).doubleValue()>100)
						throw new IOException("weights: "+tag+" must be a multiplier from 0.01 to 100 (exclude_tags leaves a tag out)");
					b.weights.put(tag,Double.valueOf(((Number)v).doubleValue()));
					b.weightedBy.put(tag,new ArrayList<String>(Arrays.asList(source+" x"+fmt(((Number)v).doubleValue()))));
				}
			}
			if(raw.containsKey("guarantee"))
			{
				b.guarantee=ModuleDefinitions.id(raw.get("guarantee"));
				b.guaranteedBy=source;
				if(b.excluded.containsKey(b.guarantee))
					throw new IOException("guarantee "+b.guarantee+" is also an excluded tag");
			}
			if(!b.none())
				b.sources.add(source);
			return b;
		}

		/** @return whether it shapes nothing */
		public boolean none()
		{
			return excluded.isEmpty()&&weights.isEmpty()&&guarantee.isEmpty();
		}

		/** this and another together; two different guarantees cannot both hold, and a family cannot be excluded */
		public Bias plus(final Bias o) throws IOException
		{
			final Bias b=new Bias();
			b.excluded.putAll(excluded);
			for(final Map.Entry<String,String> e:o.excluded.entrySet())
				if(!b.excluded.containsKey(e.getKey()))
					b.excluded.put(e.getKey(),e.getValue());
			for(final String t:weights.keySet())
			{
				b.weights.put(t,weights.get(t));
				b.weightedBy.put(t,new ArrayList<String>(weightedBy.get(t)));
			}
			for(final String t:o.weights.keySet())
			{
				b.weights.put(t,Double.valueOf((b.weights.containsKey(t)?b.weights.get(t).doubleValue():1.0)*o.weights.get(t).doubleValue()));
				if(!b.weightedBy.containsKey(t))
					b.weightedBy.put(t,new ArrayList<String>());
				b.weightedBy.get(t).addAll(o.weightedBy.get(t));
			}
			b.guarantee=guarantee;
			b.guaranteedBy=guaranteedBy;
			if(o.guarantee.length()>0)
			{
				if((guarantee.length()>0)&&!guarantee.equals(o.guarantee))
					throw new IOException("two guarantees cannot both hold: "+guarantee+" ("+guaranteedBy+") and "+o.guarantee+" ("+o.guaranteedBy+")");
				b.guarantee=o.guarantee;
				b.guaranteedBy=guarantee.length()>0?guaranteedBy+", "+o.guaranteedBy:o.guaranteedBy;
			}
			if(b.guarantee.length()>0&&b.excluded.containsKey(b.guarantee))
				throw new IOException("the guaranteed family "+b.guarantee+" ("+b.guaranteedBy+") is excluded by "+b.excluded.get(b.guarantee));
			b.sources.addAll(sources);
			for(final String s:o.sources)
				if(!b.sources.contains(s))
					b.sources.add(s);
			return b;
		}

		/** why an affix is left out, or "" */
		public String excludes(final Definition a) throws IOException
		{
			for(final String t:a.list("tags"))
				if(excluded.containsKey(t))
					return "tag "+t+" is excluded by "+excluded.get(t);
			return "";
		}

		/** the product of the multipliers for the affix's tags */
		public double multiplier(final Definition a) throws IOException
		{
			double m=1;
			for(final String t:a.list("tags"))
				if(weights.containsKey(t))
					m*=weights.get(t).doubleValue();
			return m;
		}

		/** the multipliers that apply, in words, or "" */
		public String why(final Definition a) throws IOException
		{
			final List<String> out=new ArrayList<String>();
			for(final String t:a.list("tags"))
				if(weights.containsKey(t))
					out.add(t+" "+String.join(" and ",weightedBy.get(t)));
			return String.join("; ",out);
		}

		/** the rules in words, each with where it came from */
		public String describe()
		{
			return words(true);
		}

		/** the rules in words alone */
		public String rules()
		{
			return words(false);
		}

		private String words(final boolean sourced)
		{
			if(none())
				return "no exclusions, multipliers or guarantee";
			final List<String> out=new ArrayList<String>();
			for(final Map.Entry<String,String> e:excluded.entrySet())
				out.add("excludes "+e.getKey()+(sourced?" ("+e.getValue()+")":""));
			for(final String t:weights.keySet())
			{
				// one source: its name; several: each with its own multiplier
				final List<String> by=weightedBy.get(t);
				out.add(t+" x"+fmt(weights.get(t).doubleValue())+(!sourced?"":" ("+(by.size()==1?by.get(0).replaceAll(" x[0-9.]+$",""):String.join(", ",by))+")"));
			}
			if(guarantee.length()>0)
				out.add("the first modifier drawn is of the "+guarantee+" family"+(sourced?" ("+guaranteedBy+")":""));
			return String.join("; ",out);
		}
	}
	static String fmt(final double d)
	{
		return (d==Math.rint(d))?Long.toString((long)d):String.format(Locale.ROOT,"%.2f",Double.valueOf(d)).replaceAll("0+$","");
	}
	/** I06: a further rule a draw keeps (a loot policy's tiers, tags and budget), given the tiers already used; "" when the affix may be drawn */
	public interface Filter
	{
		String barred(Definition affix,JSONObject record,int used) throws IOException;
	}
	public static final Filter OPEN=(a,r,u)->"";
	/** I06: one affix's place in a draw: its weight after multipliers, or why it is out */
	public static final class Odds
	{
		public final Definition affix;
		public final double weight;
		public final String why, multipliers;
		Odds(final Definition affix,final double weight,final String why,final String multipliers)
		{
			this.affix=affix;
			this.weight=weight;
			this.why=why;
			this.multipliers=multipliers;
		}
	}
	/**
	 * I06: each candidate's weight in the next pick, or why it cannot be drawn:
	 * the item (kind, level, group, capacity), the further filter, an excluded
	 * tag, and for the first pick the guaranteed family.
	 */
	public static List<Odds> odds(final JSONObject record,final List<Definition> pool,final Bias bias,final boolean first,final Filter filter,final int used,
		final int level) throws IOException
	{
		final List<Odds> out=new ArrayList<Odds>();
		for(final Definition a:pool)
		{
			String why=ineligible(record,a,level);
			if(why.isEmpty())
				why=filter.barred(a,record,used);
			if(why.isEmpty())
				why=bias.excludes(a);
			if(why.isEmpty()&&first&&(bias.guarantee.length()>0)&&!a.list("tags").contains(bias.guarantee))
				why="not of the guaranteed "+bias.guarantee+" family ("+bias.guaranteedBy+")";
			final double w=((Number)a.raw.get("weight")).doubleValue();
			out.add(new Odds(a,why.isEmpty()?w*bias.multiplier(a):0,why,bias.why(a)));
		}
		return out;
	}
	/** I06: the total weight of the affixes that can be drawn */
	public static double total(final List<Odds> odds)
	{
		double t=0;
		for(final Odds o:odds)
			if(o.why.isEmpty())
				t+=o.weight;
		return t;
	}
	/** I06: the odds in words: each affix's chance, or why it cannot be drawn */
	public static String describe(final List<Odds> odds)
	{
		final double t=total(odds);
		final StringBuilder s=new StringBuilder();
		for(final Odds o:odds)
		{
			s.append("\n  ").append(o.affix.id).append(" (").append(o.affix.name).append(", ").append(o.affix.string("position")).append(" tier ")
				.append(o.affix.number("tier")).append("): ");
			if(o.why.isEmpty())
				s.append(String.format(Locale.ROOT,"%.1f%%",Double.valueOf(100.0*o.weight/t))).append(o.multipliers.isEmpty()?"":" (weight "+fmt(((Number)o.affix.raw.get("weight")).doubleValue())+", "+o.multipliers+")");
			else
				s.append("cannot be drawn: ").append(o.why);
		}
		return s.toString();
	}
	/**
	 * I06: draws up to count modifiers for a record, one at a time by weight,
	 * each pick leaving out the groups and slots the ones before it took.  The
	 * first pick must be of the guaranteed family; if none can be, nothing is
	 * drawn and the draw is refused.  For each pick, one unit draw chooses the
	 * affix, then the dice roll its values.
	 */
	public static List<JSONObject> draw(final JSONObject record,final List<Definition> pool,final Bias bias,final int level,final int count,
		final Filter filter,final java.util.function.DoubleSupplier unit,final Roller dice,final java.util.function.Supplier<String> uids) throws IOException
	{
		final JSONObject rec=ModuleDefinitions.copy(record);
		final List<JSONObject> mods=modifiers(rec), drawn=new ArrayList<JSONObject>();
		int used=0;
		for(int n=0;n<count;n++)
		{
			final List<Odds> odds=odds(rec,pool,bias,n==0,filter,used,level);
			final double total=total(odds);
			if(total<=0)
			{
				if((n==0)&&(bias.guarantee.length()>0))
					throw new IOException("no affix of the guaranteed "+bias.guarantee+" family ("+bias.guaranteedBy+") can be drawn for this item");
				break;
			}
			double at=unit.getAsDouble()*total;
			Definition chosen=null;
			for(final Odds o:odds)
				if(o.why.isEmpty())
				{
					chosen=o.affix;
					at-=o.weight;
					if(at<0)
						break;
				}
			final JSONObject m=rolled(chosen,uids.get(),dice);
			drawn.add(m);
			mods.add(m);
			rec.put("modifiers",mods.toArray());
			used+=chosen.number("tier");
		}
		return drawn;
	}
	public static final class Library
	{
		public final Map<String,Definition> definitions=new TreeMap<String,Definition>();
		public final List<String> errors=new ArrayList<String>();
		public Definition find(final String id,final int version,final String kind)throws IOException
		{
			Definition found=null;
			for(final Definition d:definitions.values())
				if(d.id.equals(id)&&d.kind.equals(kind)&&(version==0||version==d.version)&&(found==null||d.version>found.version))
					found=d;
			if(found==null)
				throw new IOException("unavailable "+kind+" "+id+" v"+version);
			return found;
		}
		public Definition pinned(final Object raw,final String kind)throws IOException
		{
			final JSONObject p=checkPin(raw);
			final Definition d=find((String)p.get("id"),((Number)p.get("version")).intValue(),kind);
			if(!d.hash.equals(p.get("hash")))
				throw new IOException(d.key()+" changed without a new version; crafting suspended");
			return d;
		}
		public String report()
		{
			return errors.isEmpty()?"Item modifier definitions valid ("+definitions.size()+" versions).":String.join("\n",errors);
		}
		private void add(final JSONObject obj,final String source)throws IOException
		{
			final Definition d=new Definition(obj);
			if(definitions.put(d.key(),d)!=null)
				throw new IOException(source+": duplicate definition "+d.key());
		}
	}
	public static Library load(final File world,final Collection<JSONObject> affixes)
	{
		final Library lib=new Library();
		for(final JSONObject a:affixes)
			try
			{
				lib.add(a,"affixes.xml");
			}
			catch(final IOException e)
			{
				lib.errors.add("affixes.xml: "+e.getMessage());
			}
		final File dir=new File(world,"itemmods");
		if(Files.isSymbolicLink(dir.toPath())||dir.exists()&&!dir.isDirectory())
		{
			lib.errors.add("itemmods/: must be a regular directory");
			return lib;
		}
		final File[] files=dir.listFiles(f->f.getName().endsWith(".json"));
		if(files!=null)
		{
			Arrays.sort(files,Comparator.comparing(File::getName));
			if(files.length>128)
				lib.errors.add("itemmods/: at most 128 files");
			for(int i=0;i<Math.min(128,files.length);i++)
				try
				{
					final File f=files[i];
					if(!f.isFile()||Files.isSymbolicLink(f.toPath())||!f.getCanonicalPath().startsWith(world.getCanonicalPath()+File.separator)
						||f.length()>65536)
						throw new IOException("must be a regular file inside world, at most 64 KiB");
					final JSONObject obj=ModuleDefinitions.parse(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
					final Definition d=new Definition(obj);
					if(d.kind.equals("affix"))
						throw new IOException("tracked affixes belong in the existing affixes.xml");
					if(!f.getName().equals(d.key()+".json"))
						throw new IOException("filename must be "+d.key()+".json");
					lib.add(obj,f.getName());
				}
				catch(final Exception e)
				{
					lib.errors.add(files[i].getName()+": "+e.getMessage());
				}
		}
		for(final Definition d:lib.definitions.values())
			try
			{
				if(d.kind.equals("base"))
					for(final String id:d.list("pool"))
						lib.find(id,0,"affix");
				if(d.kind.equals("operation"))
					for(final String id:d.list("affixes"))
						lib.find(id,0,"affix");
				if(d.kind.equals("base")&&d.raw.containsKey("successor"))
				{
					final Definition next=lib.find(d.string("successor"),0,"base");
					if(!next.string("item_kind").equals(d.string("item_kind")))
						throw new IOException("successor "+next.key()+" is for "+next.string("item_kind")+", not "+d.string("item_kind"));
				}
				if(d.kind.equals("operation")||d.kind.equals("recipe"))
					for(final String id:d.optional("catalysts"))
						lib.find(id,0,"catalyst");
				if(d.kind.equals("recipe")&&((JSONObject)d.raw.get("result")).containsKey("base"))
					lib.find((String)((JSONObject)d.raw.get("result")).get("base"),0,"base");
			}
			catch(final IOException e)
			{
				lib.errors.add(d.key()+": "+e.getMessage());
			}
		return lib;
	}
	/** Turn flat stat paths into the existing combat-stat delta schema. */
	public static CombatRules.Stats stats(final JSONObject values)throws IOException
	{
		final JSONObject out=new JSONObject();
		out.put("schema",Long.valueOf(1));
		for(final String name:values.keySet())
		{
			final String[] parts=name.split("\\.");
			if(parts.length==1&&CombatRules.SCALARS.contains(name))
				out.put(name,values.get(name));
			else if(parts.length==2&&(parts[0].equals("resistance")||parts[0].equals("penetration")))
			{
				JSONObject group=(JSONObject)out.get(parts[0]);
				if(group==null)
				{
					group=new JSONObject();
					out.put(parts[0],group);
				}
				group.put(parts[1],values.get(name));
			}
			else
				throw new IOException("unsupported modifier stat "+name);
		}
		// No schema/immune injection through a stat key.
		if(values.containsKey("schema")||values.containsKey("immune"))
			throw new IOException("only numeric stat paths are rollable");
		return CombatRules.bonuses(ModuleDefinitions.json(out));
	}
	public static JSONObject rolled(final Definition affix,final String uid,final Roller dice)throws IOException
	{
		if(!affix.kind.equals("affix"))
			throw new IOException("not an affix");
		final JSONObject values=new JSONObject(),rolls=(JSONObject)affix.raw.get("rolls");
		for(final String key:rolls.keySet())
		{
			final JSONObject r=(JSONObject)rolls.get(key);
			final int lo=((Number)r.get("min")).intValue(),hi=((Number)r.get("max")).intValue();
			final int n=lo==hi?lo:dice.roll(lo,hi);
			if(n<lo||n>hi)
				throw new IOException("roller returned a value outside its range");
			values.put(key,Long.valueOf(n));
		}
		final JSONObject mod=new JSONObject();
		mod.put("uid",uid);
		mod.put("pin",pin(affix));
		mod.put("name",affix.name);
		mod.put("position",affix.raw.get("position"));
		mod.put("tier",affix.raw.get("tier"));
		mod.put("group",affix.raw.get("group"));
		mod.put("tags",affix.raw.get("tags"));
		mod.put("values",values);
		mod.put("protected",Boolean.FALSE);
		return mod;
	}
	public static JSONObject record(final Definition base,final String instance,final String name,final String display,
		final String source)throws IOException
	{
		if(!base.kind.equals("base"))
			throw new IOException("not a base");
		final JSONObject out=new JSONObject();
		out.put("schema",Long.valueOf(1));
		out.put("item",instance);
		out.put("base",pin(base));
		out.put("revision",Long.valueOf(0));
		out.put("item_kind",base.raw.get("item_kind"));
		out.put("prefixes",base.raw.get("prefixes"));
		out.put("suffixes",base.raw.get("suffixes"));
		out.put("tags",base.raw.get("tags"));
		out.put("source",source);
		out.put("base_name",name);
		out.put("rendered_name",name);
		out.put("base_display",display);
		out.put("rendered_display",display);
		out.put("name_override",Boolean.FALSE);
		out.put("display_override",Boolean.FALSE);
		out.put("modifiers",new Object[0]);
		return checked(out);
	}
	public static JSONObject checked(final JSONObject raw)throws IOException
	{
		ModuleDefinitions.keys(raw,"item modifier record","schema","item","base","revision","item_kind","prefixes","suffixes","tags","source",
			"base_name","rendered_name","base_display","rendered_display","name_override","display_override","modifiers");
		if(ModuleDefinitions.integer(raw.get("schema"),"record schema")!=1)
			throw new IOException("unsupported modifier record schema");
		if(!ModuleDefinitions.string(raw.get("item"),"item reference").matches("@instance:[0-9a-f-]{36}"))
			throw new IOException("invalid bound item reference");
		checkPin(raw.get("base"));
		integer(raw.get("revision"),"revision",0,1000000000);
		if(!Arrays.asList("armor","weapon","ammo","item").contains(raw.get("item_kind")))
			throw new IOException("invalid item kind");
		final int pre=integer(raw.get("prefixes"),"prefix capacity",0,3),suf=integer(raw.get("suffixes"),"suffix capacity",0,3);
		if(pre+suf<1)
			throw new IOException("base needs modifier capacity");
		ids(raw.get("tags"),"item tags",16);
		for(final String field:Arrays.asList("source","base_name","rendered_name","base_display","rendered_display"))
			if(ModuleDefinitions.string(raw.get(field),field).length()>(field.startsWith("rendered_")?2048:500))
				throw new IOException(field+" too long");
		if(!(raw.get("name_override") instanceof Boolean)||!(raw.get("display_override") instanceof Boolean))
			throw new IOException("display override flags must be boolean");
		final Object[] mods=ModuleDefinitions.array(raw.get("modifiers"),"modifiers");
		if(mods.length>MAX_MODS)
			throw new IOException("at most six modifiers");
		final Set<String> uids=new HashSet<String>(),groups=new HashSet<String>();
		int prefixes=0,suffixes=0;
		for(final Object entry:mods)
		{
			final JSONObject m=ModuleDefinitions.object(entry,"modifier");
			ModuleDefinitions.keys(m,"modifier","uid","pin","name","position","tier","group","tags","values","protected");
			final String uid=ModuleDefinitions.string(m.get("uid"),"modifier UID");
			if(!uid.matches("[0-9a-f-]{36}")||!uids.add(uid))
				throw new IOException("invalid/duplicate modifier UID");
			checkPin(m.get("pin"));
			text(m.get("name"),"modifier name");
			integer(m.get("tier"),"modifier tier",1,100);
			if(!groups.add(ModuleDefinitions.id(m.get("group"))))
				throw new IOException("exclusive modifier group repeated");
			ids(m.get("tags"),"modifier tags",16);
			if("prefix".equals(m.get("position")))
				prefixes++;
			else if("suffix".equals(m.get("position")))
				suffixes++;
			else
				throw new IOException("invalid modifier position");
			if(!(m.get("protected") instanceof Boolean))
				throw new IOException("protected must be boolean");
			final JSONObject values=ModuleDefinitions.object(m.get("values"),"rolled values");
			if(values.isEmpty()||values.size()>8)
				throw new IOException("invalid roll count");
			for(final String key:values.keySet())
				integer(values.get(key),"rolled value",-1000000,1000000);
			stats(values);
		}
		if(prefixes>pre||suffixes>suf)
			throw new IOException("modifier capacity exceeded");
		if(ModuleDefinitions.json(raw).length()>MAX_TEXT)
			throw new IOException("modifier record exceeds 32 KiB");
		return ModuleDefinitions.copy(raw);
	}
	public static JSONObject parse(final String text)throws IOException
	{
		if(text.length()>MAX_TEXT)
			throw new IOException("modifier record exceeds 32 KiB");
		return checked(ModuleDefinitions.parse(text));
	}
	public static List<JSONObject> modifiers(final JSONObject record)throws IOException
	{
		final List<JSONObject> out=new ArrayList<JSONObject>();
		for(final Object m:ModuleDefinitions.array(record.get("modifiers"),"modifiers"))
			out.add(ModuleDefinitions.copy((JSONObject)m));
		return out;
	}
	public static boolean fits(final JSONObject record,final Definition affix,final int itemLevel)throws IOException
	{
		return ineligible(record,affix,itemLevel).isEmpty();
	}
	public static String ineligible(final JSONObject record,final Definition affix,final int itemLevel)throws IOException
	{
		if(!affix.kind.equals("affix"))
			return "not an affix";
		if(!affix.list("kinds").contains("any")&&!affix.list("kinds").contains(record.get("item_kind")))
			return "item kind does not match";
		if(itemLevel<affix.number("minimum_level")||itemLevel>affix.number("maximum_level"))
			return "item level outside "+affix.number("minimum_level")+".."+affix.number("maximum_level");
		int count=0;
		for(final JSONObject m:modifiers(record))
		{
			if(m.get("group").equals(affix.raw.get("group")))
				return "exclusive group "+affix.string("group")+" already present: "+m.get("name")
					+(Boolean.TRUE.equals(m.get("protected"))?" (protected)":"");
			if(m.get("position").equals(affix.raw.get("position")))
				count++;
		}
		return count<((Number)record.get(affix.string("position").equals("prefix")?"prefixes":"suffixes")).intValue()?""
			:affix.string("position")+" capacity is full";
	}
	/** Diagnose persisted contract/value drift without requiring a particular operation. */
	public static void validateSources(final JSONObject record,final Library lib)throws IOException
	{
		checked(record);
		final Definition baseContract=lib.pinned(record.get("base"),"base");
		for(final String field:Arrays.asList("item_kind","prefixes","suffixes"))
			if(!record.get(field).equals(baseContract.raw.get(field)))
				throw new IOException("base contract differs from its pin");
		if(!ids(record.get("tags"),"item tags",16).equals(baseContract.list("tags")))
			throw new IOException("base tags differ from their pin");
		for(final JSONObject m:modifiers(record))
		{
			final Definition a=lib.pinned(m.get("pin"),"affix");
			if(!baseContract.list("pool").contains(a.id)||!a.list("kinds").contains("any")&&!a.list("kinds").contains(record.get("item_kind")))
				throw new IOException("saved modifier is outside its base pool or item kind");
			if(!m.get("group").equals(a.raw.get("group"))||!m.get("position").equals(a.raw.get("position"))||!m.get("tier").equals(a.raw.get("tier"))
				||!m.get("name").equals(a.name)||!ids(m.get("tags"),"modifier tags",16).equals(a.list("tags")))
				throw new IOException("modifier contract differs from its pin");
			final JSONObject values=(JSONObject)m.get("values"),rolls=(JSONObject)a.raw.get("rolls");
			if(!values.keySet().equals(rolls.keySet()))
				throw new IOException("roll fields differ from definition");
			for(final String key:values.keySet())
			{
				final JSONObject range=(JSONObject)rolls.get(key);
				integer(values.get(key),key,((Number)range.get("min")).intValue(),((Number)range.get("max")).intValue());
			}
		}
	}
	/** I04 replace: the selector is the modifier's UID, then the affix that takes its place. */
	public static String[] replacement(final String selector)throws IOException
	{
		final int at=selector.indexOf('>');
		if(at<1||at==selector.length()-1)
			throw new IOException("replace names a modifier and the affix to put in its place");
		return new String[]{selector.substring(0,at),selector.substring(at+1)};
	}
	private static void addable(final JSONObject record,final Definition op,final Definition a,final Library lib,final int level)throws IOException
	{
		final Definition base=lib.pinned(record.get("base"),"base");
		if(!base.list("pool").contains(a.id)||!op.list("affixes").isEmpty()&&!op.list("affixes").contains(a.id))
			throw new IOException("affix "+a.id+" is outside this base/operation pool");
		final String reason=ineligible(record,a,level);
		if(!reason.isEmpty())
			throw new IOException(reason);
		if(!a.list("tags").containsAll(op.list("modifier_tags")))
			throw new IOException("affix "+a.id+" lacks required modifier tags "+op.list("modifier_tags"));
	}
	/** The modifier an operation targets, explained when it cannot be: gone, missing a tag, protected or not. */
	private static JSONObject target(final JSONObject record,final Definition op,final String uid)throws IOException
	{
		JSONObject found=null;
		for(final JSONObject m:modifiers(record))
			if(m.get("uid").equals(uid))
				found=m;
		if(found==null)
			throw new IOException("modifier no longer exists");
		if(!ids(found.get("tags"),"modifier tags",16).containsAll(op.list("modifier_tags")))
			throw new IOException("modifier "+found.get("name")+" lacks required tags "+op.list("modifier_tags"));
		final boolean locked=Boolean.TRUE.equals(found.get("protected"));
		final String action=op.string("action");
		if(locked&&!action.equals("unprotect"))
			throw new IOException("modifier is protected: "+found.get("name")+", group "+found.get("group")+"; unprotect it first");
		if(!locked&&action.equals("unprotect"))
			throw new IOException("modifier is not protected: "+found.get("name"));
		return found;
	}
	private static JSONObject without(final JSONObject record,final String uid)throws IOException
	{
		final JSONObject next=ModuleDefinitions.copy(record);
		final List<JSONObject> mods=modifiers(next);
		mods.removeIf(m->m.get("uid").equals(uid));
		next.put("modifiers",mods.toArray());
		return next;
	}
	/** I04 upgrade: the base's declared successor, which must hold every modifier the item has. */
	public static Definition successor(final JSONObject record,final Library lib)throws IOException
	{
		final Definition base=lib.pinned(record.get("base"),"base");
		if(!base.raw.containsKey("successor"))
			throw new IOException(base.key()+" declares no successor to upgrade to");
		final Definition next=lib.find(base.string("successor"),0,"base");
		if(!next.string("item_kind").equals(record.get("item_kind")))
			throw new IOException("successor "+next.key()+" is for another item kind");
		int pre=0,suf=0;
		for(final JSONObject m:modifiers(record))
		{
			final Definition a=lib.pinned(m.get("pin"),"affix");
			if(!next.list("pool").contains(a.id))
				throw new IOException("successor "+next.key()+" cannot hold "+m.get("name")+" ("+a.id+")");
			if(m.get("position").equals("prefix"))
				pre++;
			else
				suf++;
		}
		if(pre>next.number("prefixes")||suf>next.number("suffixes"))
			throw new IOException("successor "+next.key()+" has room for "+next.number("prefixes")+" prefix / "+next.number("suffixes")
				+" suffix, fewer than the item has");
		return next;
	}
	/** I06: an operation's or recipe's own distribution with the catalysts named for it */
	public static Bias bias(final Definition craft,final List<Definition> catalysts)throws IOException
	{
		Bias b=Bias.of(craft.raw,craft.kind+" "+craft.id);
		for(final Definition c:catalysts)
		{
			if(!c.kind.equals("catalyst"))
				throw new IOException(c.id+" is not a catalyst");
			if(!craft.optional("catalysts").contains(c.id))
				throw new IOException(craft.name+" takes "+(craft.optional("catalysts").isEmpty()?"no catalysts":"only the catalysts "+craft.optional("catalysts"))+", not "+c.id);
			b=b.plus(Bias.of(c.raw,"catalyst "+c.id));
		}
		return b;
	}
	/** I06: what an operation draws among: its base's pool, every affix, so that the preview can say why each is left out */
	public static List<Definition> drawPool(final JSONObject record,final Library lib)throws IOException
	{
		final List<Definition> out=new ArrayList<Definition>();
		for(final String id:lib.pinned(record.get("base"),"base").list("pool"))
			out.add(lib.find(id,0,"affix"));
		return out;
	}
	/** I06: an operation's own limits on its draw: its allow-list and its required modifier tags */
	public static Filter limits(final Definition op)throws IOException
	{
		final List<String> allowed=op.list("affixes"),tags=op.list("modifier_tags");
		return (a,r,u)->!allowed.isEmpty()&&!allowed.contains(a.id)?"outside this operation's affixes":!a.list("tags").containsAll(tags)
			?"lacks the operation's required tags "+tags:"";
	}
	/** I06 reforge: the record with only its protected modifiers left, and how many go */
	public static JSONObject reforged(final JSONObject record)throws IOException
	{
		final JSONObject next=ModuleDefinitions.copy(record);
		final List<JSONObject> kept=new ArrayList<JSONObject>();
		for(final JSONObject m:modifiers(record))
			if(Boolean.TRUE.equals(m.get("protected")))
				kept.add(m);
		next.put("modifiers",kept.toArray());
		return next;
	}
	/** I06: the record an operation's first pick draws for (an imbue's item; a reforge's protected modifiers) */
	public static JSONObject drawsFor(final JSONObject record,final Definition op)throws IOException
	{
		return op.string("action").equals("reforge")?reforged(record):record;
	}
	/** I06: the first pick's odds of an operation that draws */
	public static List<Odds> firstPick(final JSONObject record,final Definition op,final Library lib,final int level,final Bias bias)throws IOException
	{
		return odds(drawsFor(record,op),drawPool(record,lib),bias,true,limits(op),0,level);
	}
	/** Eligibility only. It must run before payment and before calling any roller. */
	public static void validate(final JSONObject record,final Definition op,final String selector,final Library lib,final int level)throws IOException
	{
		validate(record,op,selector,lib,level,bias(op,Collections.<Definition>emptyList()));
	}
	/** Eligibility only, with the distribution (the operation's and its catalysts'). It must run before payment and before calling any roller. */
	public static void validate(final JSONObject record,final Definition op,final String selector,final Library lib,final int level,final Bias bias)throws IOException
	{
		validateSources(record,lib);
		if(!op.kind.equals("operation"))
			throw new IOException("not an operation");
		if(((Number)record.get("revision")).longValue()>=1000000000)
			throw new IOException("modifier revision counter is full");
		if(!ids(record.get("tags"),"item tags",16).containsAll(op.list("item_tags")))
			throw new IOException("item lacks required tags "+op.list("item_tags"));
		final String action=op.string("action");
		if(action.equals("add"))
			addable(record,op,lib.find(selector,0,"affix"),lib,level);
		else if(action.equals("replace"))
		{
			final String[] r=replacement(selector);
			target(record,op,r[0]);
			addable(without(record,r[0]),op,lib.find(r[1],0,"affix"),lib,level);
		}
		else if(action.equals("upgrade"))
			successor(record,lib);
		else if(DRAWS.contains(action))
		{
			if(action.equals("reforge")&&modifiers(reforged(record)).size()==modifiers(record).size())
				throw new IOException(modifiers(record).isEmpty()?"the item has no modifiers to reforge":"every modifier is protected; nothing would be reforged");
			final List<Odds> first=firstPick(record,op,lib,level,bias);
			if(total(first)<=0)
				throw new IOException((bias.guarantee.length()>0?"no affix of the guaranteed "+bias.guarantee+" family ("+bias.guaranteedBy+") can be drawn"
					:"no affix can be drawn")+" for this item:"+describe(first));
		}
		else
			target(record,op,selector);
	}
	/** I06: a unit draw from the dice, for operations that draw */
	static java.util.function.DoubleSupplier unit(final Roller dice)
	{
		return ()->dice.roll(0,(1<<30)-1)/(double)(1<<30);
	}
	public static JSONObject transform(final JSONObject original,final Definition op,final String selector,final Library lib,final int level,
		final String addedUID,final Roller dice)throws IOException
	{
		return transform(original,op,selector,lib,level,addedUID,dice,bias(op,Collections.<Definition>emptyList()));
	}
	public static JSONObject transform(final JSONObject original,final Definition op,final String selector,final Library lib,final int level,
		final String addedUID,final Roller dice,final Bias bias)throws IOException
	{
		validate(original,op,selector,lib,level,bias);
		final JSONObject next=ModuleDefinitions.copy(original);
		final List<JSONObject> mods=modifiers(next);
		final String action=op.string("action");
		if(DRAWS.contains(action))
		{
			// I06: imbue draws one more; reforge draws again as many as were not protected
			final JSONObject from=drawsFor(original,op);
			final int count=action.equals("imbue")?1:mods.size()-modifiers(from).size();
			final String[] first={addedUID};
			final List<JSONObject> drawn=draw(from,drawPool(original,lib),bias,level,count,limits(op),unit(dice),dice,()->{
				final String u=first[0]!=null?first[0]:UUID.randomUUID().toString();
				first[0]=null;
				return u;
			});
			mods.clear();
			mods.addAll(modifiers(from));
			mods.addAll(drawn);
		}
		else if(action.equals("add"))
			mods.add(rolled(lib.find(selector,0,"affix"),addedUID,dice));
		else if(action.equals("upgrade"))
		{
			final Definition up=successor(original,lib);
			next.put("base",pin(up));
			next.put("prefixes",up.raw.get("prefixes"));
			next.put("suffixes",up.raw.get("suffixes"));
			next.put("tags",up.raw.get("tags"));
		}
		else
		{
			final String uid=action.equals("replace")?replacement(selector)[0]:selector;
			for(int i=0;i<mods.size();i++)
				if(mods.get(i).get("uid").equals(uid))
				{
					final JSONObject old=mods.get(i);
					if(action.equals("remove"))
						mods.remove(i);
					else if(action.equals("replace"))
						mods.set(i,rolled(lib.find(replacement(selector)[1],0,"affix"),addedUID,dice));
					// removed and added in one settlement
					else if(action.equals("reroll"))
						mods.set(i,rolled(lib.pinned(old.get("pin"),"affix"),selector,dice));
					else
						old.put("protected",Boolean.valueOf(action.equals("protect")));
					break;
				}
		}
		next.put("revision",Long.valueOf(((Number)original.get("revision")).longValue()+1));
		next.put("modifiers",mods.toArray());
		return checked(next);
	}
	public static String renderedName(final JSONObject record)throws IOException
	{
		String name=(String)record.get("base_name");
		for(final JSONObject m:modifiers(record))
		{
			final String label=(String)m.get("name");
			if(m.get("position").equals("prefix"))
			{
				final int space=name.indexOf(' ');
				final String article=space<0?"":name.substring(0,space+1);
				name=Arrays.asList("a ","an ","the ","some ").contains(article.toLowerCase())?article+label+" "+name.substring(space+1)
					:label+" "+name;
			}
			else
				name+=" "+(label.toLowerCase().startsWith("of ")?label:"of "+label);
		}
		return name;
	}
}
