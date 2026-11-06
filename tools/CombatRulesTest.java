import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CombatRules.*;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Compiled;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Definition;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/** Golden combat results and conversion equivalence properties, without a running game. */
public final class CombatRulesTest
{
    private static int checks;
    private static void check(final boolean value, final String name) { if(!value) throw new AssertionError(name); checks++; System.out.println("ok " + name); }
    private static void near(final double a, final double b, final String name) { check(Math.abs(a-b)<.00000001,name+" ("+a+")"); }
    private interface Task { void run() throws Exception; }
    private static void refuses(final String name, final Task task) throws Exception
    { boolean refused=false;try{task.run();}catch(final IOException e){refused=true;}check(refused,name); }
    private static JSONObject obj(final String raw) throws IOException { return ModuleDefinitions.parse(raw); }
    private static Profile profile(final String extra) throws IOException
    { return new Profile(obj("{\"schema\":1,\"id\":\"test.profile\",\"version\":1,\"title\":\"Test profile\""+extra+"}"),"test"); }
    private static Stats stats(final Profile p, final String delta) throws IOException
    { return CombatRules.combine(p.defaults,Arrays.asList(CombatRules.bonuses(delta))); }
    private static Compiled skill(final String damage, final String modifiers) throws IOException
    {
        final Definition base=new Definition(obj("{\"schema\":1,\"kind\":\"skill\",\"id\":\"test.skill\",\"version\":1,\"name\":\"Test skill\",\"tags\":[\"spell\",\"projectile\"],\"damage\":"+damage+"}"),"test");
        final Definition support=new Definition(obj("{\"schema\":1,\"kind\":\"support\",\"id\":\"test.support\",\"version\":1,\"name\":\"Test support\",\"modifiers\":"+modifiers+"}"),"test");
        return CompositionDefinitions.compile(base,Arrays.asList(support));
    }
    private static final class Rolls implements Roller
    {
        final int[] values; int at; final List<String> reasons=new ArrayList<String>();
        Rolls(final int...values){this.values=values;}
        public int roll(final String reason){reasons.add(reason);if(at>=values.length)throw new AssertionError("unexpected roll: "+reason);return values[at++];}
    }
    private static Map<String,Double> damage(final String type,final double amount)
    { final Map<String,Double> out=new LinkedHashMap<String,Double>();out.put(type,amount);return out; }
    private static void erase(final Path path) throws IOException
    {try(java.util.stream.Stream<Path> files=Files.walk(path)){for(final Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    public static void main(final String[] args) throws Exception
    {
        final Library examples=CombatRules.load(new File(args[0]));check(examples.errors.isEmpty(),examples.report());check(examples.profiles.size()==2,"two authored conversion presets");
        final Profile p=profile(""), block=profile(",\"block_cap\":100"), partial=profile(",\"block_cap\":100,\"block_reduction\":50");
        final Stats ordinary=stats(p,"{\"schema\":1}");
        final String conversion="[{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":100},{\"stat\":\"damage\",\"op\":\"increased\",\"value\":20},{\"stat\":\"damage.physical\",\"op\":\"increased\",\"value\":50},{\"stat\":\"damage.fire\",\"op\":\"increased\",\"value\":100}]";
        final Compiled converted=skill("{\"physical\":100}",conversion);
        final Offense finalType=CombatRules.offense(converted,Collections.<String>emptySet(),"final"), history=CombatRules.offense(converted,Collections.<String>emptySet(),"history");
        near(finalType.damage.get("fire"),220,"final type scaling golden");near(history.damage.get("fire"),270,"type history scaling golden");
        final Compiled mixed=skill("{\"physical\":100,\"fire\":100}",conversion);
        near(CombatRules.offense(mixed,Collections.<String>emptySet(),"history").damage.get("fire"),490,"history retains distinct packet origins after merging");
        near(CombatRules.offense(mixed,Collections.<String>emptySet(),"final").damage.get("fire"),440,"final scaling merges pools without source bonus");
        final Compiled noConversion=skill("{\"physical\":100}","[{\"stat\":\"damage\",\"op\":\"increased\",\"value\":20},{\"stat\":\"damage.physical\",\"op\":\"increased\",\"value\":50}]");
        near(CombatRules.offense(noConversion,Collections.<String>emptySet(),"history").damage.get("physical"),170,"an unchanged type is never scaled twice");
        final Compiled debt=skill("{\"physical\":100}","[{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":50},{\"stat\":\"damage.fire\",\"op\":\"flat\",\"value\":-25}]");
        near(CombatRules.offense(debt,Collections.<String>emptySet(),"history").damage.get("fire"),25,"negative destination flats offset incoming conversion");
        near(CombatRules.offense(debt,Collections.<String>emptySet(),"history").damage.get("physical"),50,"conversion preserves unconverted source");
        final Compiled capped=skill("{\"physical\":100,\"fire\":100}",conversion.substring(0,conversion.length()-1)+",{\"stat\":\"damage.fire\",\"op\":\"cap\",\"value\":80}]");
        near(CombatRules.offense(capped,Collections.<String>emptySet(),"history").damage.get("fire"),80,"cap once after packet aggregation");
        final Stats fireWard=stats(p,"{\"schema\":1,\"resistance\":{\"fire\":50}}");
        final Rolls none=new Rolls();
        Resolution r=CombatRules.resolve(p,damage("fire",100),ordinary,fireWard,true,false,none);
        check(r.damage.get("fire")==50&&none.at==0,"resistance is a deterministic reduction, no legacy save roll");
        r=CombatRules.resolve(p,damage("physical",100),ordinary,stats(p,"{\"schema\":1,\"armor\":500}"),true,false,new Rolls());
        check(r.damage.get("physical")==50,"armor golden: 500/(500+5*100)");
        r=CombatRules.resolve(p,damage("physical",100),ordinary,stats(p,"{\"schema\":1,\"armor\":500,\"resistance\":{\"physical\":50}}"),true,false,new Rolls());
        check(r.damage.get("physical")==25,"armor then resistance each apply once");
        r=CombatRules.resolve(p,damage("fire",100),stats(p,"{\"schema\":1,\"penetration\":{\"fire\":20}}"),stats(p,"{\"schema\":1,\"resistance\":{\"fire\":120}}"),true,false,new Rolls());
        check(r.damage.get("fire")==45,"resistance caps before penetration: 75 minus 20");
        r=CombatRules.resolve(p,damage("fire",100),ordinary,stats(p,"{\"schema\":1,\"resistance\":{\"fire\":-200}}"),true,false,new Rolls());
        check(r.damage.get("fire")==200,"negative resistance is bounded by profile floor");
        r=CombatRules.resolve(p,damage("fire",100),stats(p,"{\"schema\":1,\"penetration\":{\"fire\":100}}"),stats(p,"{\"schema\":1,\"resistance\":{\"fire\":-100}}"),true,false,new Rolls());
        check(r.damage.get("fire")==200,"penetration cannot exceed vulnerability floor");
        r=CombatRules.resolve(block,damage("fire",100),ordinary,stats(block,"{\"schema\":1,\"spell_block\":100}"),true,false,new Rolls());
        check(r.blocked&&!r.critical&&r.damage.get("fire")==0,"certain full spell block needs no dice or critical roll");
        r=CombatRules.resolve(partial,damage("fire",100),ordinary,stats(partial,"{\"schema\":1,\"spell_block\":100,\"resistance\":{\"fire\":50}}"),true,false,new Rolls());
        check(r.damage.get("fire")==25,"partial block follows mitigation");
        r=CombatRules.resolve(p,damage("fire",100),stats(p,"{\"schema\":1,\"crit_chance\":100,\"crit_multiplier\":50}"),fireWard,true,false,new Rolls());
        check(r.critical&&r.damage.get("fire")==100,"critical multiplier and resistance golden");
        r=CombatRules.resolve(p,damage("fire",100),ordinary,stats(p,"{\"schema\":1,\"immune\":[\"fire\"]}"),true,false,new Rolls());
        check(r.damage.get("fire")==0&&r.lines.toString().contains("immune"),"typed immunity rejects damage with an explanation");
        r=CombatRules.resolve(p,damage("fire",3),CombatRules.burnSource(20),fireWard,true,true,new Rolls());
        check(r.damage.get("fire")==2,"burn applies saved penetration once then rounds once");
        r=CombatRules.resolve(block,damage("fire",100),stats(block,"{\"schema\":1,\"crit_chance\":100}"),stats(block,"{\"schema\":1,\"spell_block\":100}"),true,true,new Rolls());
        check(!r.blocked&&!r.critical&&r.damage.get("fire")==100,"burn cannot block or critically hit");
        r=CombatRules.resolve(p,damage("fire",3),ordinary,stats(p,"{\"schema\":1,\"immune\":[\"burning\"]}"),true,true,new Rolls());
        check(r.damage.get("fire")==0,"ailment immunity rejects burn damage");
        final Stats evade=stats(p,"{\"schema\":1,\"evasion\":100}");
        final Rolls hitRoll=new Rolls(50);r=CombatRules.resolve(p,damage("physical",100),ordinary,evade,false,false,hitRoll);
        check(r.hit&&r.damage.get("physical")==100&&hitRoll.at==1,"hit roll includes exact threshold and rolls only once");
        final Rolls missRoll=new Rolls(51);r=CombatRules.resolve(p,damage("physical",100),ordinary,evade,false,false,missRoll);
        check(!r.hit&&r.damage.get("physical")==0&&missRoll.at==1,"miss short circuits block and criticals");
        final Rolls blockRoll=new Rolls(75);r=CombatRules.resolve(p,damage("fire",100),ordinary,stats(p,"{\"schema\":1,\"spell_block\":100}"),true,false,blockRoll);
        check(r.blocked&&blockRoll.at==1,"block cap is enforced before its roll");
        final Rolls critRoll=new Rolls(25);r=CombatRules.resolve(p,damage("fire",10),stats(p,"{\"schema\":1,\"crit_chance\":25}"),ordinary,true,false,critRoll);
        check(r.critical&&r.damage.get("fire")==15&&critRoll.at==1,"critical roll is shared by every typed component");
        final Rolls missCrit=new Rolls(26);r=CombatRules.resolve(p,damage("fire",10),stats(p,"{\"schema\":1,\"crit_chance\":25}"),ordinary,true,false,missCrit);
        check(!r.critical&&r.damage.get("fire")==10,"critical above threshold is rejected");
        r=CombatRules.resolve(p,damage("fire",10),stats(p,"{\"schema\":1,\"crit_chance\":0.999999}"),ordinary,true,false,new Rolls(1));
        check(!r.critical&&r.lines.toString().contains("0.999999%: false"),"fractional probability trace preserves the actual threshold");
        check(CombatRules.display(0.999999).equals("0.999999"),"stat display does not round a value across a percentile boundary");
        check(CombatRules.resolve(profile(",\"maximum_damage\":10"),damage("fire",100),ordinary,ordinary,true,false,new Rolls()).damage.get("fire")==10,"profile damage bound");
        near(CombatRules.combine(p.defaults,Arrays.asList(CombatRules.bonuses("{\"schema\":1,\"armor\":100}"),CombatRules.bonuses("{\"schema\":1,\"armor\":50}"))).get("armor"),150,"stat sources sum");
        near(CombatRules.combine(p.defaults,Arrays.asList(CombatRules.bonuses("{\"schema\":1,\"armor\":50}"))).get("armor"),50,"removal recomputes without stale stat mutation");
        refuses("malformed stat key rejected",()->CombatRules.bonuses("{\"schema\":1,\"armour\":10}"));
        refuses("unknown stat immunity rejected",()->CombatRules.bonuses("{\"schema\":1,\"immune\":[\"poison\"]}"));
        refuses("missing stat schema rejected",()->CombatRules.bonuses("{\"armor\":10}"));
        refuses("invalid stats schema rejected",()->CombatRules.bonuses("{\"schema\":2}"));
        refuses("out of range stat rejected",()->CombatRules.bonuses("{\"schema\":1,\"crit_chance\":1001}"));
        refuses("unknown damage type rejected",()->CombatRules.bonuses("{\"schema\":1,\"resistance\":{\"chaos\":10}}"));
        refuses("bad profile schema rejected",()->profile(",\"schema\":2"));
        refuses("unsupported conversion rejected",()->profile(",\"conversion\":\"sequential\""));
        refuses("zero armor constant rejected",()->profile(",\"armor\":{\"constant\":0}"));
        refuses("invalid hit clamp rejected",()->profile(",\"hit\":{\"minimum\":90,\"maximum\":20}"));
        refuses("out of range damage cap rejected",()->profile(",\"maximum_damage\":1000001"));
        refuses("unknown profile fields rejected",()->profile(",\"typo\":1"));
        refuses("invalid roller rejected",()->CombatRules.resolve(p,damage("physical",1),ordinary,evade,false,false,new Rolls(101)));
        final Random random=new Random(719);
        for(int i=0;i<60;i++)
        {
            final int phys=random.nextInt(100), fire=random.nextInt(20), convert=random.nextInt(101), flat=random.nextInt(61)-30, increase=random.nextInt(100)-50;
            final Compiled c=skill("{\"physical\":"+phys+",\"fire\":"+fire+"}","[{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":"+convert+"},{\"stat\":\"damage.fire\",\"op\":\"flat\",\"value\":"+flat+"},{\"stat\":\"damage\",\"op\":\"increased\",\"value\":"+increase+"}]");
            final Map<String,Double> legacy=c.values(Collections.<String>emptySet()), modern=CombatRules.offense(c,Collections.<String>emptySet(),"final").damage, historical=CombatRules.offense(c,Collections.<String>emptySet(),"history").damage;
            for(final String type:CombatRules.TYPES)
            {near(modern.get(type),legacy.get("damage."+type),"final conversion preserves stage 2 algebra "+i+"/"+type);near(historical.get(type),modern.get(type),"no type-specific bonuses means both models agree "+i+"/"+type);}
        }
        final Profile example=examples.find("example.final",1);final JSONObject pin=CombatRules.pin(example);
        check(examples.pinned(pin)==example,"profile pins resolve by version and hash");pin.put("hash","wrong");refuses("same-version edits suspend",()->examples.pinned(pin));pin.put("version",Long.valueOf(9));refuses("missing profile version suspends",()->examples.pinned(pin));
        final Path temp=Files.createTempDirectory("sud-combat-schema-");
        try
        {
            check(CombatRules.load(temp.toFile()).profiles.isEmpty(),"legacy world installs no combat profile");final Path dir=Files.createDirectory(temp.resolve("combat"));
            final String raw="{\"schema\":1,\"id\":\"test.file\",\"version\":1,\"title\":\"Test\"}";
            Files.write(dir.resolve("wrong.json"),raw.getBytes("UTF-8"));check(!CombatRules.load(temp.toFile()).errors.isEmpty(),"profile identity/filename enforced");Files.delete(dir.resolve("wrong.json"));
            Files.write(dir.resolve("test.file.v1.json"),raw.getBytes("UTF-8"));Files.write(dir.resolve("test.file.v2.json"),raw.replace("\"version\":1","\"version\":2").getBytes("UTF-8"));
            check(CombatRules.load(temp.toFile()).find("test.file",0).version==2,"explicit selection finds latest numeric version");
            Files.createSymbolicLink(dir.resolve("linked.v1.json"),dir.resolve("test.file.v1.json"));check(!CombatRules.load(temp.toFile()).errors.isEmpty(),"linked profiles rejected");Files.delete(dir.resolve("linked.v1.json"));
            final Path actual=Files.move(dir,temp.resolve("actual"));Files.createSymbolicLink(dir,actual);check(!CombatRules.load(temp.toFile()).errors.isEmpty(),"linked profile directory rejected");
        }finally{erase(temp);}
        final String moduleRaw=new String(Files.readAllBytes(new File(args[0],"modules/combat.profiles.json").toPath()),"UTF-8");
        final JSONObject manifest=obj(moduleRaw),settings=(JSONObject)manifest.get("settings"),setting=(JSONObject)settings.get("profile");
        check(examples.pinned(obj((String)setting.get("default")))==example,"shipped module pins the actual preset hash");
        System.out.println("CombatRulesTest passed: "+checks+" checks");
    }
}
