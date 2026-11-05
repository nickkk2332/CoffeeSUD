import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.core.save.SaveSlots;
public class SaveDurabilityProbe {
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory("sud-audit-save-");
  File db=root.resolve("working").toFile();db.mkdirs();
  Properties h=new Properties();h.setProperty("version","1");h.setProperty("character","AuditHero");
  Map<String,String> f=new HashMap<String,String>();f.put("world.xml","<WORLD/>\n");
  SaveSlots.write(root.toFile(),"hero",db,h,f);
  System.out.println("listed_before="+SaveSlots.list(root.toFile()).size());
  File hero=root.resolve("hero").toFile(),old=root.resolve(".old-hero").toFile();
  if(!hero.renameTo(old))throw new IOException("Cannot simulate interrupted replacement");
  System.out.println("old_complete="+new File(old,SaveSlots.HEADER_FILE).isFile());
  System.out.println("listed_after_interrupted_replacement="+SaveSlots.list(root.toFile()).size());
  System.out.println("found_after_interrupted_replacement="+(SaveSlots.find(root.toFile(),"hero")!=null));
  File source=root.resolve("content").toFile();source.mkdirs();
  Files.write(new File(source,"fakedb.schema").toPath(),"audit schema\n".getBytes("UTF-8"));
  File first=SaveSlots.prepareWorkingDatabase(source,root.resolve("concurrent").toFile(),null,new String[0]);
  Path marker=first.toPath().resolve("active-marker");Files.write(marker,"live game\n".getBytes("UTF-8"));
  File second=SaveSlots.prepareWorkingDatabase(source,root.resolve("concurrent").toFile(),null,new String[0]);
  System.out.println("second_start_deletes_first_working_state="+!Files.exists(marker));
  System.out.println("second_working_created="+second.isDirectory());
  System.out.println("scratch="+root);
 }
}
