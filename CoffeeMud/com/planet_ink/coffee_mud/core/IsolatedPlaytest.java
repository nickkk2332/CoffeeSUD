package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Disposable, separate-process campaigns. The builder process and its world remain available. */
public final class IsolatedPlaytest
{
    private IsolatedPlaytest() { }
    public static final class Prepared
    {
        public final File directory;
        public final List<String> command;
        private Prepared(final File directory, final List<String> command) { this.directory=directory;this.command=Collections.unmodifiableList(command); }
        public String commandLine()
        {
            final StringBuilder out=new StringBuilder();
            for(final String arg:command)
            {
                out.append(out.length()==0?"":" ");
                if(File.separatorChar=='\\')out.append('"').append(arg.replace("\"","\\\"")).append('"');
                else out.append('\'').append(arg.replace("'","'\"'\"'")).append('\'');
            }
            return out.toString();
        }
    }
    public static Prepared prepare(final MOB builder) throws IOException
    {
        final File root=Files.createTempDirectory("sud-playtest-").toFile();
        final File world=new File(root,"world"),db=new File(root,"db"),saves=new File(root,"saves");
        try
        {
            copyTree(WorldFiles.worldDir().toPath(),world.toPath());
            final WorldFiles.ExportPlan plan=WorldFiles.plan();
            if(plan==null) throw new IOException("the world did not load whole, so a copy of it cannot be made; see WORLD STATUS");
            // Apply the preview to the copy, including live unsaved builder definitions.
            for(final Map.Entry<String,String> text:plan.texts.entrySet())
            {
                final Path target=world.toPath().resolve(text.getKey()).normalize();
                if(!target.startsWith(world.toPath())) throw new IOException("Invalid export path "+text.getKey());
                Files.createDirectories(target.getParent());Files.write(target,text.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            for(final String path:plan.deletes) Files.deleteIfExists(world.toPath().resolve(path));
            db.mkdirs();new File(root,"logs").mkdirs();
            final File source=SaveGame.databaseDir();
            Files.copy(new File(source,"fakedb.schema").toPath(),new File(db,"fakedb.schema").toPath());
            final String why=SaveGame.exportPlaytest(builder,saves);
            if(why!=null) throw new IOException(why);
            SaveSlots.release(saves);
            final List<String> command=new ArrayList<String>();
            command.add(new File(System.getProperty("java.home"),"bin/java").getPath());
            command.add("-Dsud.isolated.playtest=true");command.add("-Dsud.log.prefix="+new File(root,"logs/mud").getAbsolutePath());
            command.add("-cp");
            final List<String> paths=new ArrayList<String>();
            for(final String path:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) paths.add(new File(path).getAbsolutePath());
            command.add(String.join(File.pathSeparator,paths));command.add("com.planet_ink.coffee_mud.application.SUD");
            command.add("BUILDER=0");command.add("DEV=0");command.add("RESTORE=playtest");command.add("WINDOWS=0");
            command.add("CHARSETINPUT="+CMProps.getVar(CMProps.Str.CHARSETINPUT));command.add("CHARSETOUTPUT="+CMProps.getVar(CMProps.Str.CHARSETOUTPUT));
            command.add("WORLDDIR="+world.getAbsolutePath());command.add("SAVEDIR="+saves.getAbsolutePath());command.add("DBSERVICE=jdbc:fakedb:"+db.getAbsolutePath());
            return new Prepared(root,command);
        }
        catch(final IOException | RuntimeException e) { SaveSlots.release(saves);deleteTree(root.toPath());throw e; }
    }
    public static String launch(final MOB builder) throws IOException
    {
        final Prepared prepared=prepare(builder);
        if(!CharacterWindow.canOpen())
            return "Isolated playtest prepared at "+prepared.directory+". Run this in another terminal from "+new File(".").getCanonicalPath()+":\n\r"+prepared.commandLine()
                +"\n\rThe builder world is unchanged. Remove the temporary folder when finished.";
        final Process process=new ProcessBuilder(prepared.command).redirectErrorStream(true).start();
        final CharacterWindow window=CharacterWindow.open("Isolated playtest — close to discard", CMProps.getVar(CMProps.Str.CHARSETINPUT),CMProps.getVar(CMProps.Str.CHARSETOUTPUT),()->process.destroy());
        if(window==null) { process.destroy();throw new IOException("Unable to open the playtest window; temporary campaign: "+prepared.directory); }
        final Thread output=new Thread(()->{
            try(InputStream in=process.getInputStream()) { final byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0)window.output().write(buffer,0,n); }
            catch(final IOException e) { }
            finally {try {process.waitFor();deleteTree(prepared.directory.toPath());}catch(final Exception e) {Log.warnOut("Playtest",e.getMessage());}window.close();}
        },"playtest-output");output.setDaemon(true);output.start();
        final Thread input=new Thread(()->{
            try(OutputStream out=process.getOutputStream()) {final InputStream in=window.input();while(process.isAlive()&&!window.isClosed()){int b;while((b=in.read())>=0)out.write(b);out.flush();Thread.sleep(25);}}
            catch(final IOException | InterruptedException e) {process.destroy();}
        },"playtest-input");input.setDaemon(true);input.start();
        return "An isolated mortal playtest opened in another window. Close it to discard all test changes and continue building here.";
    }
    private static void copyTree(final Path source, final Path target) throws IOException
    {
        try(java.util.stream.Stream<Path> paths=Files.walk(source))
        {
            for(final Path path:(Iterable<Path>)paths::iterator)
            {
                final Path to=target.resolve(source.relativize(path));
                if(Files.isDirectory(path))Files.createDirectories(to);else Files.copy(path,to);
            }
        }
    }
    public static void deleteTree(final Path root) throws IOException
    {
        if(!Files.exists(root))return;
        try(java.util.stream.Stream<Path> paths=Files.walk(root))
        {for(final Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}
    }
}
