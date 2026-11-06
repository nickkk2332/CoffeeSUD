import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.core.save.SaveSlots;

/** Standalone storage regression: no engine boot or real campaign directories. */
public final class SaveStorageTest
{
    private static int checks;
    private static void check(final boolean ok, final String what)
    {
        if(!ok) throw new AssertionError(what);
        checks++;
        System.out.println("ok " + what);
    }
    private static Process child(final String action, final File dir) throws Exception
    {
        return new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java").getPath(), "-cp",
            System.getProperty("java.class.path"), "SaveStorageTest", action, dir.getPath()).redirectErrorStream(true).start();
    }
    private static String output(final Process process) throws Exception
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final byte[] data = new byte[1024]; int n;
        while((n = process.getInputStream().read(data)) >= 0) bytes.write(data,0,n);
        process.waitFor();
        return bytes.toString("UTF-8");
    }
    private static Properties header(final String generation)
    {
        final Properties h = new Properties(); h.setProperty("version","1"); h.setProperty("character","StorageTester"); h.setProperty("generation", generation);
        return h;
    }
    private static void write(final File root, final File db, final String slot, final String generation) throws Exception
    {
        final Map<String,String> files = new HashMap<String,String>(); files.put("world.xml","<WORLD VERSION=\"1\"/>\n");
        SaveSlots.write(root, slot, db, header(generation), files);
    }
    private static void copyTree(final Path source, final Path target) throws Exception
    {
        try(java.util.stream.Stream<Path> paths=Files.walk(source))
        {
            for(final Path path : (Iterable<Path>)paths::iterator)
            {
                final Path to=target.resolve(source.relativize(path));
                if(Files.isDirectory(path)) Files.createDirectories(to); else Files.copy(path,to);
            }
        }
    }
    private static void erase(final Path root) throws Exception
    {
        if(!Files.exists(root)) return;
        try(java.util.stream.Stream<Path> paths=Files.walk(root))
        {
            for(final Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }
    public static void main(final String[] args) throws Exception
    {
        if(args.length == 2)
        {
            try { SaveSlots.acquire(new File(args[1])); }
            catch(final IOException e) { System.out.println("BLOCKED: "+e.getMessage()); System.exit(23); }
            System.out.println("OWNED"); System.out.flush();
            if(args[0].equals("--hold")) System.in.read();
            return;
        }
        final Path scratch=Files.createTempDirectory("sud-storage-test-");
        final File root=scratch.resolve("saves").toFile(),db=scratch.resolve("db").toFile(); db.mkdirs();
        Files.write(new File(db,"fakedb.schema").toPath(),"test schema\n".getBytes("UTF-8"));
        try
        {
            SaveSlots.acquire(root);
            final Process blocked=child("--acquire",root); final String rejected=output(blocked);
            check(blocked.exitValue()==23 && rejected.contains("Another game"),"second process refused before cleanup");
            final File first=SaveSlots.prepareWorkingDatabase(db,root,null,new String[0]);
            final Path marker=first.toPath().resolve("active-marker"); Files.write(marker,new byte[]{1});
            final File second=SaveSlots.prepareWorkingDatabase(db,root,null,new String[0]);
            check(Files.exists(marker)&&second.isDirectory(),"another working copy preserves active state");
            final File abandoned=new File(root,".play-abandoned"); abandoned.mkdir();
            SaveSlots.deleteWorkingDatabases(root);
            check(!abandoned.exists()&&Files.exists(marker),"cleanup removes only abandoned working copies");
            final File alias=new File(root,"../saves");SaveSlots.deleteWorkingDatabases(alias);
            check(Files.exists(marker),"canonical folder aliases preserve active working copies");
            SaveSlots.releaseWorkingDatabase(first); SaveSlots.releaseWorkingDatabase(second);
            check(!first.exists()&&!second.exists(),"closed working copies are released");
            write(root,db,"hero","one");
            Files.move(new File(root,"hero").toPath(),new File(root,".old-hero").toPath());
            check(SaveSlots.find(root,"hero")!=null,"interrupted replacement recovers before listing");
            check("one".equals(SaveSlots.readHeader(root,"hero").getProperty("generation")),"recovery retains old complete generation");
            write(root,db,"hero","two");
            check(new File(root,".old-hero/.complete").isFile(),"last complete generation retained");
            final File stray=new File(root,"hero/.DS_Store"); Files.write(stray.toPath(),"desktop".getBytes("UTF-8"));
            SaveSlots.recover(root);
            check("two".equals(SaveSlots.readHeader(root,"hero").getProperty("generation"))&&Arrays.stream(root.list()).noneMatch(s->s.startsWith(".damaged-hero-")),"a stray file does not roll a complete save back");
            stray.delete();
            write(root,db,"crash","a"); write(root,db,"crash","b");
            copyTree(new File(root,"crash").toPath(),new File(root,".new-crash").toPath());
            try(java.util.stream.Stream<Path> walk=Files.walk(new File(root,"crash").toPath())) { walk.sorted(Comparator.reverseOrder()).forEach(p->p.toFile().delete()); }
            SaveSlots.recover(root);
            check("b".equals(SaveSlots.readHeader(root,"crash").getProperty("generation")),"a crash between the renames keeps the newer complete generation");
            Files.write(new File(root,"hero/world.xml").toPath(),"truncated".getBytes("UTF-8"));
            SaveSlots.recover(root);
            check("one".equals(SaveSlots.readHeader(root,"hero").getProperty("generation")),"damaged public generation falls back to valid backup");
            check(Arrays.stream(root.list()).anyMatch(s->s.startsWith(".damaged-hero-")),"damaged generation preserved for inspection");
            copyTree(new File(root,"hero").toPath(),new File(root,".new-firstsave").toPath());
            check(SaveSlots.find(root,"firstsave")!=null,"complete first save recovered from pending directory");
            final File incomplete=new File(root,".new-incomplete"); new File(incomplete,"db").mkdirs();
            try(OutputStream out=new FileOutputStream(new File(incomplete,SaveSlots.HEADER_FILE))) { header("bad").store(out,""); }
            Files.write(new File(incomplete,"world.xml").toPath(),"<WORLD/>".getBytes("UTF-8"));
            check(SaveSlots.find(root,"incomplete")==null,"unfinished new snapshot is never promoted");
            final Map<String,String> files=new HashMap<String,String>(); files.put("world.xml","<WORLD VERSION=\"1\"/>");
            final Properties future=header("future");future.setProperty("version","999");SaveSlots.write(root,"future",db,future,files);
            boolean refused=false;try {SaveSlots.validateFormat(root,"future");}catch(final IOException e){refused=e.getMessage().contains("Unsupported save format");}
            check(refused,"unsupported save format refused before restore");
            files.put("world.xml","<WORLD VERSION=\"999\"/>");SaveSlots.write(root,"futureworld",db,header("futureworld"),files);
            refused=false;try {SaveSlots.validateFormat(root,"futureworld");}catch(final IOException e){refused=e.getMessage().contains("Unsupported world snapshot");}
            check(refused,"unsupported world format refused before restore");
            files.put("world.xml","<WORLD VERSION=\"1\"/>");
            final Properties modular=header("modular");modular.setProperty("version","2");modular.setProperty("moduleformat","1");
            SaveSlots.write(root,"missingmodule",db,modular,files);
            refused=false;try {SaveSlots.validateFormat(root,"missingmodule");}catch(final IOException e){refused=e.getMessage().contains("module metadata");}
            check(refused,"module-aware format refuses missing payload");
            files.put("modules.json","{\"schema\":1,\"modules\":[]}");SaveSlots.write(root,"modular",db,modular,files);
            SaveSlots.validateFormat(root,"modular");check(true,"module-aware format accepts complete metadata");
            modular.remove("moduleformat");SaveSlots.write(root,"missingmarker",db,modular,files);
            refused=false;try {SaveSlots.validateFormat(root,"missingmarker");}catch(final IOException e){refused=e.getMessage().contains("module metadata");}
            check(refused,"module-aware format refuses missing marker");

            check(SaveSlots.delete(root,"hero")&&!new File(root,".old-hero").exists(),"deleting a save also removes recovery generations");
            SaveSlots.release(root);
            final Process holder=child("--hold",root);
            final BufferedReader reader=new BufferedReader(new InputStreamReader(holder.getInputStream(),"UTF-8"));
            String owned=reader.readLine(); while((owned!=null)&&owned.startsWith("Picked up ")) owned=reader.readLine(); // a JVM banner (JAVA_TOOL_OPTIONS) comes first on some machines
            check("OWNED".equals(owned),"new process acquires released folder");
            holder.destroyForcibly();holder.waitFor();
            SaveSlots.acquire(root);
            check(new File(root,".owner.lock").exists(),"OS releases crashed owner; stale lock file is harmless");
            System.out.println("PASS "+checks+" storage checks");
        }
        finally { SaveSlots.release(root);erase(scratch); }
    }
}
