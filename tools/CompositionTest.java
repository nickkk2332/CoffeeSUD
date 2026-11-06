import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/** Modifier/schema properties, independent of a running campaign. */
public final class CompositionTest
{
    private static int checks;
    private static void check(final boolean ok, final String what)
    { if(!ok) throw new AssertionError(what); checks++; System.out.println("ok " + what); }
    private static void near(final double actual, final double expected, final String what)
    { check(Math.abs(actual - expected) < .000001, what + " (" + actual + ")"); }
    private interface Checked { void run() throws Exception; }
    private static void refuses(final String what, final Checked action) throws Exception
    { boolean caught = false; try { action.run(); } catch(final IOException e) { caught = true; } check(caught, what); }
    private static JSONObject obj(final String raw) throws IOException { return ModuleDefinitions.parse(raw); }
    private static Definition def(final String raw) throws IOException { return new Definition(obj(raw), "test"); }
    private static final String SKILL = "{\"schema\":1,\"kind\":\"skill\",\"id\":\"test.hit\",\"version\":1,\"name\":\"Test hit\",\"tags\":[\"attack\",\"projectile\"],\"damage\":{\"physical\":100},\"stats\":{\"mana\":10}}";
    private static Definition support(final String mods) throws IOException
    { return def("{\"schema\":1,\"kind\":\"support\",\"id\":\"test.support\",\"version\":1,\"name\":\"Test support\",\"modifiers\":" + mods + "}"); }
    private static Compiled compile(final String mods) throws IOException { return CompositionDefinitions.compile(def(SKILL), Arrays.asList(support(mods))); }
    private static void erase(final Path root) throws IOException
    { try(java.util.stream.Stream<Path> paths = Files.walk(root)) { for(final Path p : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p); } }
    public static void main(final String[] args) throws Exception
    {
        final Library examples = CompositionDefinitions.load(new File(args[0]));
        check(examples.errors.isEmpty(), examples.report()); check(examples.definitions.size() == 4, "all four example versions load");
        final Definition ember = examples.latest("example.ember", "skill"), chain = examples.latest("example.chain", "support"), burning = examples.latest("example.burning", "support"), power = examples.latest("example.power", "support");
        final Compiled all = CompositionDefinitions.compile(ember, Arrays.asList(chain, burning, power));
        near(all.value("damage.fire"), 14.4, "more modifiers multiply"); near(all.value("mana"), 15, "flat cost precedes multipliers");
        near(all.value("actions"), 1.25, "timing support"); near(all.value("chains"), 2, "targeting support"); near(all.value("burn_chance"), 75, "chance cap");
        check(all.describe().contains("example.power v1") && all.describe().contains("Unconditional result"), "inspection includes source and result");
        final List<List<Definition>> permutations = Arrays.asList(Arrays.asList(chain,burning,power),Arrays.asList(chain,power,burning),Arrays.asList(power,chain,burning),Arrays.asList(power,burning,chain),Arrays.asList(burning,chain,power),Arrays.asList(burning,power,chain));
        for(int i = 0; i < permutations.size(); i++) near(CompositionDefinitions.compile(ember, permutations.get(i)).value("mana"), 15, "support order invariant " + i);
        near(CompositionDefinitions.compile(ember, Arrays.asList(chain,power)).value("mana"), 11.25, "removal recomputes from base");
        near(CompositionDefinitions.compile(ember, Collections.<Definition>emptyList()).value("damage.fire"), 12, "removing all restores base");
        final JSONObject pricedSkill = obj(SKILL);
        final JSONObject prices = (JSONObject)pricedSkill.get("stats"); prices.put("mana", Long.valueOf(100)); prices.put("cooldown", Long.valueOf(100));
        final Compiled priced = CompositionDefinitions.compile(new Definition(pricedSkill,"test"), Arrays.asList(support("[{\"stat\":\"mana\",\"op\":\"increased\",\"value\":10},{\"stat\":\"cooldown\",\"op\":\"increased\",\"value\":10}]")));
        check(Math.ceil(priced.value("mana")) == 110, "decimal percentage does not overcharge an integer mana cost");
        check(Math.ceil(priced.value("cooldown")) == 110, "decimal percentage does not add a cooldown tick");
        check(compile("[{\"stat\":\"burn_chance\",\"op\":\"increased\",\"value\":-90}]").value("burn_chance") == 10, "decimal reduction preserves exact chance threshold");
        final StringBuilder fractions = new StringBuilder("[");
        for(int i=0;i<10;i++) fractions.append("{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":9.9},");
        fractions.append("{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":1}]");
        final Compiled exactConversion = compile(fractions.toString());
        check(exactConversion.value("damage.physical") == 0 && exactConversion.value("damage.fire") == 100, "fractional conversions totaling 100 percent conserve damage");
        refuses("duplicate group rejected", () -> CompositionDefinitions.compile(ember, Arrays.asList(chain,chain)));
        final JSONObject changedGroup = obj("{\"schema\":1,\"kind\":\"support\",\"id\":\"example.chain\",\"version\":2,\"name\":\"New chain\",\"group\":\"other\"}");
        refuses("two versions of one support cannot bypass groups", () -> CompositionDefinitions.compile(ember, Arrays.asList(chain,new Definition(changedGroup,"test"))));
        changedGroup.put("id", "test.other"); changedGroup.put("group", "chain");
        refuses("different supports in one group cannot stack", () -> CompositionDefinitions.compile(ember, Arrays.asList(chain,new Definition(changedGroup,"test"))));
        refuses("incompatible support rejected", () -> CompositionDefinitions.compile(def(SKILL), Arrays.asList(burning)));
        refuses("four supports rejected", () -> CompositionDefinitions.compile(ember, Arrays.asList(chain,burning,power,chain)));
        near(compile("[{\"stat\":\"damage\",\"op\":\"increased\",\"value\":20},{\"stat\":\"damage.physical\",\"op\":\"increased\",\"value\":30}]").value("damage.physical"), 150, "increased bonuses add across global and channel");
        final Compiled conversion = compile("[{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":50},{\"stat\":\"damage.fire\",\"op\":\"increased\",\"value\":100},{\"op\":\"convert\",\"from\":\"fire\",\"to\":\"cold\",\"value\":100}]");
        near(conversion.value("damage.physical"), 50, "conversion removes source pool"); near(conversion.value("damage.fire"), 100, "destination scales after conversion"); near(conversion.value("damage.cold"), 0, "simultaneous conversion does not recursively convert new damage");
        refuses("overconversion rejected", () -> compile("[{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"fire\",\"value\":60},{\"op\":\"convert\",\"from\":\"physical\",\"to\":\"cold\",\"value\":60}]"));
        final Compiled condition = compile("[{\"stat\":\"damage\",\"op\":\"more\",\"value\":100,\"condition\":\"target_wounded\"},{\"stat\":\"damage\",\"op\":\"more\",\"value\":100,\"when_tags\":[\"fire\"]}]");
        near(condition.value("damage.physical"), 100, "inactive conditions do not contribute");
        near(condition.values(Collections.singleton("target_wounded")).get("damage.physical"), 200, "dynamic condition applied per target");
        near(compile("[{\"stat\":\"damage\",\"op\":\"more\",\"value\":200},{\"stat\":\"damage\",\"op\":\"cap\",\"value\":80}]").value("damage.physical"), 80, "cap applies last");
        near(compile("[{\"stat\":\"chains\",\"op\":\"flat\",\"value\":100000}]").value("chains"), 8, "hard chain safety cap");
        near(compile("[{\"stat\":\"actions\",\"op\":\"more\",\"value\":-100}]").value("actions"), .25, "timing safety floor");
        near(compile("[{\"stat\":\"damage.physical\",\"op\":\"flat\",\"value\":-1000}]").value("damage.physical"), 0, "negative pools clamped");
        final Compiled floored = compile("[{\"stat\":\"damage.physical\",\"op\":\"flat\",\"value\":-97},{\"stat\":\"damage\",\"op\":\"floor\",\"value\":5},{\"stat\":\"damage.fire\",\"op\":\"floor\",\"value\":5}]");
        check(floored.value("damage.physical") == 5 && floored.value("damage.fire") == 0 && floored.value("damage.cold") == 0 && floored.value("damage.electric") == 0 && floored.value("damage.acid") == 0,
            "damage floors raise dealt types and add none");
        refuses("contradictory floor/cap rejected", () -> compile("[{\"stat\":\"mana\",\"op\":\"floor\",\"value\":20},{\"stat\":\"mana\",\"op\":\"cap\",\"value\":10}]"));
        refuses("conditional timing rejected", () -> support("[{\"stat\":\"actions\",\"op\":\"more\",\"value\":50,\"condition\":\"target_wounded\"}]"));
        refuses("typo stat rejected", () -> support("[{\"stat\":\"damge\",\"op\":\"more\",\"value\":50}]"));
        refuses("unknown op rejected", () -> support("[{\"stat\":\"mana\",\"op\":\"multiply\",\"value\":50}]"));
        refuses("untyped flat damage rejected", () -> support("[{\"stat\":\"damage\",\"op\":\"flat\",\"value\":50}]"));
        refuses("conversion to itself rejected", () -> support("[{\"op\":\"convert\",\"from\":\"fire\",\"to\":\"fire\",\"value\":50}]"));
        refuses("unknown target condition rejected", () -> support("[{\"stat\":\"damage\",\"op\":\"more\",\"value\":50,\"condition\":\"any_script\"}]"));
        refuses("negative cost rejected", () -> def(SKILL.replace("\"mana\":10", "\"mana\":-1")));
        refuses("both attack and spell rejected", () -> def(SKILL.replace("\"projectile\"", "\"spell\"")));
        refuses("support cannot change attack/spell family", () -> def("{\"schema\":1,\"kind\":\"support\",\"id\":\"bad\",\"version\":1,\"name\":\"Bad\",\"adds_tags\":[\"spell\"]}"));
        refuses("duplicate JSON keys rejected", () -> def(SKILL.replace("\"schema\":1", "\"schema\":1,\"schema\":1")));
        refuses("unsupported schema rejected", () -> def(SKILL.replace("\"schema\":1", "\"schema\":2")));
        refuses("message injection in title rejected", () -> def(SKILL.replace("Test hit", "<T-NAME>")));
        final JSONObject pin = CompositionDefinitions.pin(ember);
        check(examples.pinned(pin, "skill") == ember, "pin resolves exact version/hash");
        pin.put("hash", "wrong"); refuses("edited same version pin suspends", () -> examples.pinned(pin,"skill"));
        pin.put("version", Long.valueOf(99)); refuses("missing version pin suspends", () -> examples.pinned(pin,"skill"));
        final Path scratch = Files.createTempDirectory("sud-composition-test-");
        try
        {
            check(CompositionDefinitions.load(scratch.toFile()).definitions.isEmpty(), "empty legacy world has no skills");
            final Path dir = Files.createDirectory(scratch.resolve("compositions"));
            Files.write(dir.resolve("test.hit.v1.json"), SKILL.getBytes("UTF-8"));
            Files.write(dir.resolve("test.hit.v2.json"), SKILL.replace("\"version\":1", "\"version\":2").getBytes("UTF-8"));
            final Library versions = CompositionDefinitions.load(scratch.toFile());
            check(versions.errors.isEmpty() && versions.latest("test.hit","skill").version == 2 && versions.pinned(CompositionDefinitions.pin(def(SKILL)),"skill").version == 1, "side-by-side versions preserve older pin");
            Files.write(dir.resolve("wrong.v1.json"), SKILL.getBytes("UTF-8"));
            check(!CompositionDefinitions.load(scratch.toFile()).errors.isEmpty(), "filename/identity mismatch diagnosed");
            Files.delete(dir.resolve("wrong.v1.json"));
            Files.createSymbolicLink(dir.resolve("linked.v1.json"), dir.resolve("test.hit.v1.json"));
            check(!CompositionDefinitions.load(scratch.toFile()).errors.isEmpty(), "symlink definitions rejected");
            Files.delete(dir.resolve("linked.v1.json"));
            final Path actual = Files.move(dir, scratch.resolve("actual-compositions"));
            Files.createSymbolicLink(dir, actual);
            check(!CompositionDefinitions.load(scratch.toFile()).errors.isEmpty(), "symlink source directory rejected before untracked files can load");
        }
        finally { erase(scratch); }
        System.out.println("CompositionTest passed: " + checks + " checks");
    }
}
