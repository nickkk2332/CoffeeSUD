import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.ItemModifierRules.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/** Pure acceptance checks for selective edits, protection, source identity and versioned contracts. */
public final class ItemModifierRulesTest
{
    private static int count;
    private static void ok(boolean condition,String name){if(!condition)throw new AssertionError(name);count++;System.out.println("ok "+name);}
    private interface Task{void run()throws Exception;}
    private static void refuses(String name,Task task)throws Exception{boolean refused=false;try{task.run();}catch(IOException e){refused=true;}ok(refused,name);}
    private static JSONObject obj(String json)throws IOException{return ModuleDefinitions.parse(json);}
    /** Runs a task as the game's main host would: CMLib.dice() belongs to thread group '0'. */
    private static void inGame(Task task)throws Exception
    {
        final Exception[] failed={null};final Error[] broken={null};
        final Thread t=new Thread(new ThreadGroup("0-item-rules"),()->{try{task.run();}catch(Exception e){failed[0]=e;}catch(Error e){broken[0]=e;}});t.start();t.join();
        if(broken[0]!=null)throw broken[0];if(failed[0]!=null)throw failed[0];
    }
    private static boolean diceReady;
    /** The roller crafting and generation really use (ItemModifiers.DICE), drawing from the game's dice. */
    private static Roller production()throws Exception
    {
        if(!diceReady){CMProps.instance();CMLib.registerLibrary(new com.planet_ink.coffee_mud.Libraries.Dice());diceReady=true;}
        final java.lang.reflect.Method dice=ItemModifiers.class.getDeclaredMethod("dice");dice.setAccessible(true);return (Roller)dice.invoke(null);
    }
    private static final String UID="12345678-1234-1234-1234-123456789012",UID2="12345678-1234-1234-1234-123456789013";
    private static JSONObject affix(String id,String group,String position,int version)throws IOException
    {return obj("{\"schema\":1,\"kind\":\"affix\",\"id\":\""+id+"\",\"version\":"+version+",\"name\":\"Ward\",\"position\":\""+position+"\",\"tier\":1,\"group\":\""+group+"\",\"tags\":[\"defense\"],\"kinds\":[\"armor\"],\"minimum_level\":1,\"maximum_level\":99,\"weight\":10,\"power\":0,\"cursed\":false,\"effects\":[],\"rolls\":{\"resistance.fire\":{\"min\":10,\"max\":20}}}");}
    private static JSONObject op(String action)throws IOException
    {return obj("{\"schema\":1,\"kind\":\"operation\",\"id\":\"test."+action+"\",\"version\":1,\"name\":\"Test\",\"action\":\""+action+"\",\"station\":\"forge\",\"material\":\"IRON\",\"units\":1,\"item_tags\":[\"cloth\"],\"modifier_tags\":[],\"affixes\":[]}");}
    public static void main(String[] args)throws Exception
    {
        final Path root=Files.createTempDirectory("sud-item-rules-");final Path dir=Files.createDirectory(root.resolve("itemmods"));
        final JSONObject base=obj("{\"schema\":1,\"kind\":\"base\",\"id\":\"test.mantle\",\"version\":1,\"name\":\"Mantle\",\"item_kind\":\"armor\",\"prefixes\":2,\"suffixes\":1,\"tags\":[\"cloth\"],\"pool\":[\"test.ward\",\"test.other\",\"test.suffix\"]}");
        Files.write(dir.resolve("test.mantle.v1.json"),ModuleDefinitions.json(base).getBytes("UTF-8"));
        for(String action:Arrays.asList("reroll","add","remove","protect","unprotect"))Files.write(dir.resolve("test."+action+".v1.json"),ModuleDefinitions.json(op(action)).getBytes("UTF-8"));
        final List<JSONObject> affixes=Arrays.asList(affix("test.ward","fire","prefix",1),affix("test.other","fire","prefix",1),affix("test.suffix","other","suffix",1));
        Library lib=ItemModifierRules.load(root.toFile(),affixes);ok(lib.errors.isEmpty(),"valid bases, operations and existing-affix descriptors");
        Definition b=lib.find("test.mantle",0,"base"),ward=lib.find("test.ward",0,"affix"),suffix=lib.find("test.suffix",0,"affix");
        JSONObject record=ItemModifierRules.record(b,"@instance:"+UID,"a mantle","a mantle lies here.","test");
        final int[] rolls={0};Roller high=(min,max)->{rolls[0]++;return max;};Roller low=(min,max)->{rolls[0]++;return min;};
        JSONObject first=ItemModifierRules.rolled(ward,UID,high);record.put("modifiers",new Object[]{first,ItemModifierRules.rolled(suffix,UID2,low)});record=ItemModifierRules.checked(record);
        ok(ItemModifierRules.modifiers(record).size()==2,"two independently stored modifiers");
        ok(((Number)((JSONObject)first.get("values")).get("resistance.fire")).intValue()==20,"a roller's value is stored as the roll");
        inGame(()->{
            final Roller dice=production();final Map<String,int[]> seen=new LinkedHashMap<String,int[]>();
            for(int[] range:new int[][]{{10,12},{-3,-2},{0,1},{-1000000,1000000}})
            {
                int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;
                for(int i=0;i<4000;i++){final int n=dice.roll(range[0],range[1]);lo=Math.min(lo,n);hi=Math.max(hi,n);}
                seen.put(range[0]+".."+range[1],new int[]{lo,hi});
            }
            boolean inside=true;for(Map.Entry<String,int[]> e:seen.entrySet()){final String[] r=e.getKey().split("\\.\\.");inside&=e.getValue()[0]>=Integer.parseInt(r[0])&&e.getValue()[1]<=Integer.parseInt(r[1]);}
            ok(inside,"production roller DICE stays inside its inclusive bounds");
            ok(seen.get("10..12")[0]==10&&seen.get("10..12")[1]==12&&seen.get("-3..-2")[0]==-3&&seen.get("-3..-2")[1]==-2&&seen.get("0..1")[0]==0&&seen.get("0..1")[1]==1,"production roller DICE reaches both endpoints (inclusive minimum and maximum)");
        });
        final String before=ModuleDefinitions.json(record);final int randomBefore=rolls[0];
        ItemModifierRules.validate(record,lib.find("test.reroll",0,"operation"),UID,lib,1);
        ok(rolls[0]==randomBefore&&ModuleDefinitions.json(record).equals(before),"eligibility preview neither mutates nor rolls");
        JSONObject next=ItemModifierRules.transform(record,lib.find("test.reroll",0,"operation"),UID,lib,1,UID2,low);
        ok(ModuleDefinitions.json(record).equals(before),"transform preserves its input snapshot");
        ok(ModuleDefinitions.json(ItemModifierRules.modifiers(next).get(1)).equals(ModuleDefinitions.json(ItemModifierRules.modifiers(record).get(1))),"reroll preserves the other modifier exactly");
        ok(ItemModifierRules.modifiers(next).get(0).get("uid").equals(UID),"reroll retains modifier identity");
        ok(((Number)next.get("revision")).intValue()==1,"one edit increments one revision");
        JSONObject locked=ItemModifierRules.transform(next,lib.find("test.protect",0,"operation"),UID,lib,1,UID2,low);
        final JSONObject lockedFinal=locked;final Library library=lib;final int draws=rolls[0];
        refuses("protected modifier cannot reroll",()->ItemModifierRules.transform(lockedFinal,library.find("test.reroll",0,"operation"),UID,library,1,UID2,high));
        refuses("protected modifier cannot be removed",()->ItemModifierRules.transform(lockedFinal,library.find("test.remove",0,"operation"),UID,library,1,UID2,high));
        ok(rolls[0]==draws,"rejected protection paths consume no dice");
        JSONObject unlocked=ItemModifierRules.transform(locked,lib.find("test.unprotect",0,"operation"),UID,lib,1,UID2,high);
        ok(!Boolean.TRUE.equals(ItemModifierRules.modifiers(unlocked).get(0).get("protected")),"explicit unprotect operation");
        final JSONObject original=record;
        refuses("exclusive groups apply even with a spare prefix slot",()->ItemModifierRules.validate(original,library.find("test.add",0,"operation"),"test.other",library,1));
        JSONObject removed=ItemModifierRules.transform(unlocked,lib.find("test.remove",0,"operation"),UID,lib,1,UID2,high);
        ok(ItemModifierRules.modifiers(removed).size()==1&&ItemModifierRules.modifiers(removed).get(0).get("uid").equals(UID2),"remove preserves the surviving modifier");
        JSONObject added=ItemModifierRules.transform(removed,lib.find("test.add",0,"operation"),"test.other",lib,1,UID,high);
        ok(ItemModifierRules.modifiers(added).size()==2,"add reuses the eligible freed group");
        ok(ModuleDefinitions.json(ItemModifierRules.parse(ModuleDefinitions.json(added))).equals(ModuleDefinitions.json(added)),"record save/restore roundtrip");
        final List<CombatRules.Stats> sources=Arrays.asList(ItemModifierRules.stats((JSONObject)ItemModifierRules.modifiers(added).get(0).get("values")),ItemModifierRules.stats((JSONObject)ItemModifierRules.modifiers(added).get(1).get("values")));
        CombatRules.Stats summed=CombatRules.sumBonuses(sources);ok(summed.get("resistance.fire")==30&&summed.get("crit_multiplier")==0,"summing modifier deltas introduces no absolute critical baseline");
        final JSONObject invalid=ModuleDefinitions.copy(added);((JSONObject)((Object[])invalid.get("modifiers"))[0]).put("position",null);
        refuses("malformed record fails with a validation error",()->ItemModifierRules.checked(invalid));
        final JSONObject noCapacity=ModuleDefinitions.copy(added);noCapacity.put("prefixes",Long.valueOf(0));noCapacity.put("suffixes",Long.valueOf(0));noCapacity.put("modifiers",new Object[0]);refuses("records cannot erase all base capacity",()->ItemModifierRules.checked(noCapacity));
        final JSONObject taggedOperation=op("add");taggedOperation.put("modifier_tags",new Object[]{"missing"});final JSONObject available=removed;
        refuses("add checks required modifier tags on the new affix",()->ItemModifierRules.validate(available,new Definition(taggedOperation),"test.other",library,1));
        final JSONObject corruptValues=ModuleDefinitions.copy(added);((JSONObject)((JSONObject)((Object[])corruptValues.get("modifiers"))[0]).get("values")).put("resistance.fire",Long.valueOf(99));
        refuses("source validation catches out-of-definition saved rolls",()->ItemModifierRules.validateSources(corruptValues,library));
        final JSONObject corruptSource=ModuleDefinitions.copy(added);((JSONObject)((Object[])corruptSource.get("modifiers"))[0]).put("tier",Long.valueOf(99));
        refuses("source validation catches a forged tier with a valid pin",()->ItemModifierRules.validateSources(corruptSource,library));
        final JSONObject multiwordMaterial=op("reroll");multiwordMaterial.put("material","WHITE GOLD");new Definition(multiwordMaterial);ok(true,"native multiword material names are accepted");
        final JSONObject duplicate=ModuleDefinitions.copy(added);duplicate.put("modifiers",new Object[]{first,first});refuses("duplicate modifier identities/groups rejected",()->ItemModifierRules.checked(duplicate));
        refuses("schema cannot be injected as a stat path",()->ItemModifierRules.stats(obj("{\"schema\":2}")));
        refuses("mixed nested/scalar resistance paths rejected",()->ItemModifierRules.stats(obj("{\"resistance\":2,\"resistance.fire\":10}")));
        refuses("invalid roller output rejected",()->ItemModifierRules.rolled(ward,UID,(min,max)->max+1));
        JSONObject constant=affix("test.fixed","fixed","prefix",1);constant.put("rolls",obj("{\"armor\":{\"min\":20,\"max\":20}}"));
        ItemModifierRules.rolled(new Definition(constant),UID,(min,max)->{throw new AssertionError("constant roll consumed dice");});ok(true,"fixed numeric modifiers do not roll");
        final List<JSONObject> changed=new ArrayList<JSONObject>(affixes);JSONObject edited=affix("test.ward","fire","prefix",1);edited.put("name","Edited");changed.set(0,edited);
        final Library drift=ItemModifierRules.load(root.toFile(),changed);refuses("same-version XML affix edits invalidate a pin",()->drift.pinned(ItemModifierRules.pin(ward),"affix"));
        changed.add(affix("test.ward","fire","prefix",2));final Library newer=ItemModifierRules.load(root.toFile(),changed);ok(newer.find("test.ward",0,"affix").version==2,"new creation explicitly finds latest affix version");
        final JSONObject pinnedBase=ItemModifierRules.pin(b);ok(lib.pinned(pinnedBase,"base").version==1,"base version pins are retained");
        JSONObject bad=affix("test.bad","bad","prefix",1);bad.put("effects",new Object[]{"arbitrary"});final JSONObject badEffects=bad;refuses("tracked family rejects unowned arbitrary effects",()->new Definition(badEffects));
        bad=affix("test.bad","bad","prefix",1);bad.put("weight",Double.NaN);final JSONObject badWeight=bad;refuses("nonfinite weights rejected",()->new Definition(badWeight));
        inGame(()->{
            final Roller dice=production();final Set<Integer> values=new TreeSet<Integer>();boolean untouched=true;
            for(int i=0;i<400;i++)
            {
                final JSONObject r=ItemModifierRules.transform(original,library.find("test.reroll",0,"operation"),UID,library,1,UID2,dice);
                values.add(Integer.valueOf(((Number)((JSONObject)ItemModifierRules.modifiers(r).get(0).get("values")).get("resistance.fire")).intValue()));
                untouched&=ModuleDefinitions.json(ItemModifierRules.modifiers(r).get(1)).equals(ModuleDefinitions.json(ItemModifierRules.modifiers(original).get(1)));
            }
            ok(values.size()==11&&values.iterator().next()==10&&((TreeSet<Integer>)values).last()==20&&untouched,"400 rerolls with the production roller cover exactly 10..20 and leave the unselected source byte-equivalent "+values);
        });
        // I06: targeted draws. Affixes of several tags, a distribution from the operation and catalysts, and a guaranteed family.
        final Definition fire=new Definition(obj(ModuleDefinitions.json(affix("test.fire","fire","prefix",1)).replace("[\"defense\"]","[\"offense\",\"elemental\"]")));
        final Definition guard=new Definition(obj(ModuleDefinitions.json(affix("test.guard","guard","prefix",1)).replace("[\"defense\"]","[\"defense\"]")));
        final Definition edge=new Definition(obj(ModuleDefinitions.json(affix("test.edge","edge","suffix",1)).replace("[\"defense\"]","[\"offense\"]")));
        final List<Definition> pool=Arrays.asList(fire,guard,edge);
        final JSONObject blank=ItemModifierRules.record(b,"@instance:"+UID,"a mantle","a mantle lies here.","test");
        final Bias coal=Bias.of(obj("{\"weights\":{\"offense\":4}}"),"coal"),flux=Bias.of(obj("{\"weights\":{\"offense\":2,\"elemental\":3}}"),"flux");
        final Bias both=coal.plus(flux);
        ok(both.multiplier(fire)==24&&both.multiplier(edge)==8&&both.multiplier(guard)==1,"multipliers for an affix's tags multiply together, from every source");
        final Bias amber=Bias.of(obj("{\"guarantee\":\"defense\"}"),"amber"),pearl=Bias.of(obj("{\"guarantee\":\"offense\"}"),"pearl");
        refuses("two different guarantees cannot both hold",()->amber.plus(pearl));
        ok(amber.plus(Bias.of(obj("{\"guarantee\":\"defense\"}"),"amber2")).guarantee.equals("defense"),"the same guarantee twice is one guarantee");
        refuses("a guaranteed family cannot be excluded",()->amber.plus(Bias.of(obj("{\"exclude_tags\":[\"defense\"]}"),"ash")));
        refuses("a multiplier of zero is refused (exclude_tags leaves a tag out)",()->Bias.of(obj("{\"weights\":{\"offense\":0}}"),"x"));
        final List<Odds> plain=ItemModifierRules.odds(blank,pool,Bias.NONE,true,ItemModifierRules.OPEN,0,1);
        ok(ItemModifierRules.total(plain)==30&&plain.get(0).weight==10,"with no distribution an affix's weight is its own");
        final List<Odds> ashed=ItemModifierRules.odds(blank,pool,Bias.of(obj("{\"exclude_tags\":[\"offense\"]}"),"ash"),true,ItemModifierRules.OPEN,0,1);
        ok(ItemModifierRules.total(ashed)==10&&ashed.get(0).why.equals("tag offense is excluded by ash"),"an excluded tag leaves its affixes out, saying which source excluded it");
        final double[] units={0.0,0.3,0.6,0.99};final int[] at={0};
        boolean always=true;
        for(int i=0;i<40;i++)
        {
            final List<JSONObject> got=ItemModifierRules.draw(blank,pool,amber.plus(coal),1,2,ItemModifierRules.OPEN,()->units[(at[0]++)%units.length],(lo,hi)->lo,()->UUID.randomUUID().toString());
            always&=got.size()==2&&((Object[])((JSONObject)got.get(0)).get("tags"))[0].equals("defense");
        }
        ok(always,"the first modifier drawn is always of the guaranteed family, and later picks are drawn as usual");
        final JSONObject guarded=ModuleDefinitions.copy(blank);guarded.put("modifiers",new Object[]{ItemModifierRules.rolled(guard,UID,low)});
        refuses("a guarantee no affix can meet refuses the draw before anything is drawn",()->ItemModifierRules.draw(guarded,pool,amber,1,1,ItemModifierRules.OPEN,()->0.5,(lo,hi)->{throw new IllegalStateException("drew");},()->UID2));
        refuses("only drawing operations take a distribution",()->new Definition(obj(ModuleDefinitions.json(op("protect")).replace("\"affixes\":[]","\"affixes\":[],\"weights\":{\"offense\":2}"))));
        ok(new Definition(obj(ModuleDefinitions.json(op("imbue")).replace("\"affixes\":[]","\"affixes\":[],\"weights\":{\"offense\":2}"))).string("action").equals("imbue"),"an imbue operation takes weights");
        refuses("a catalyst shapes the draw somehow",()->new Definition(obj("{\"schema\":1,\"kind\":\"catalyst\",\"id\":\"test.dud\",\"version\":1,\"name\":\"Dud\",\"material\":\"COAL\",\"units\":1}")));
        // I09: a base's consumers. Quality raises only the modifiers of its tags, rounded half away from zero; a trait counts only where read.
        final Definition bracer=new Definition(obj("{\"schema\":1,\"kind\":\"base\",\"id\":\"test.bracer\",\"version\":1,\"name\":\"Bracer\",\"item_kind\":\"armor\",\"prefixes\":1,\"suffixes\":1,\"tags\":[\"metal\"],\"pool\":[\"test.ward\"],"
            +"\"quality\":{\"max\":3,\"tags\":[\"defense\"],\"percent\":10},\"traits\":{\"conductivity\":{\"stat\":\"penetration.electric\",\"per\":2}}}"));
        final JSONObject warded=ItemModifierRules.rolled(ward,UID,(lo,hi)->15),edged=ItemModifierRules.rolled(edge,UID2,(lo,hi)->15);
        ok(((Number)ItemModifierRules.counted(bracer,warded,1).get("resistance.fire")).intValue()==17&&((Number)ItemModifierRules.counted(bracer,warded,3).get("resistance.fire")).intValue()==20
            &&((Number)ItemModifierRules.counted(bracer,edged,3).get("resistance.fire")).intValue()==15,"quality raises its tags' modifiers only (15 at +10% is 17, half away from zero)");
        final Definition bronze=new Definition(obj("{\"schema\":1,\"kind\":\"material\",\"id\":\"test.bronze\",\"version\":1,\"name\":\"Bronze\",\"material\":\"BRONZE\",\"traits\":{\"conductivity\":3,\"density\":2}}"));
        ok(ModuleDefinitions.json(ItemModifierRules.traitStats(bracer,bronze)).equals("{\"penetration.electric\":6}")&&ItemModifierRules.traitStats(b,bronze).isEmpty(),"a trait gives per times its value where a base reads it, nothing elsewhere");
        ok(String.join("|",ItemModifierRules.explain(blank,bracer,bronze,"BRONZE")).contains("density 2: nothing on this item reads it"),"a trait nothing reads is said to do nothing");
        refuses("a trait consumer of zero is refused",()->new Definition(obj(ModuleDefinitions.json(bracer.raw).replace("\"per\":2","\"per\":0"))));
        refuses("a trait consumer's stat must be a combat stat",()->new Definition(obj(ModuleDefinitions.json(bracer.raw).replace("penetration.electric","luck"))));
        Files.write(dir.resolve("bad.v1.json"),"{}".getBytes("UTF-8"));ok(!ItemModifierRules.load(root.toFile(),affixes).errors.isEmpty(),"invalid files make a library unpublishable");Files.delete(dir.resolve("bad.v1.json"));
        Files.createSymbolicLink(dir.resolve("linked.v1.json"),dir.resolve("test.mantle.v1.json"));ok(!ItemModifierRules.load(root.toFile(),affixes).errors.isEmpty(),"linked definitions rejected");Files.delete(dir.resolve("linked.v1.json"));
        try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
        System.out.println("ItemModifierRulesTest passed: "+count+" checks");
    }
}
