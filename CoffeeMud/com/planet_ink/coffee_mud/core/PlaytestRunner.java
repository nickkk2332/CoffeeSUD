package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

/** Developer test runs use a separate campaign and report back on the game thread. */
public final class PlaytestRunner
{
    private PlaytestRunner() {}
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static String start(final MOB viewer, final String requested)
    {
        if(!com.planet_ink.coffee_mud.core.save.SaveGame.isBuilderMode()
        && !com.planet_ink.coffee_mud.core.save.SaveGame.isDevMode())
            return "Playtest files run in builder mode or a DEV=1 game.";
        final File tests;
        final File selected;
        final File runner;
        try
        {
            tests = new File(WorldFiles.worldDir(), "tests").getCanonicalFile();
            selected = requested.trim().isEmpty() ? tests : new File(tests, requested.trim()).getCanonicalFile();
            if(!selected.toPath().startsWith(tests.toPath()) || !selected.exists())
                return "No test under world/tests: " + requested;
            if(selected.isFile() && !selected.getName().matches(".*\\.(play|test|js)$"))
                return "Test files end in .play, .test or .js.";
            runner = new File("../tools/playtest.py").getCanonicalFile();
            if(!runner.isFile()) return "The developer test runner tools/playtest.py is missing.";
        }
        catch(final IOException e) { return "Cannot read tests: " + e.getMessage(); }
        if(!running.compareAndSet(false, true)) return "A world test is already running.";
        final Thread worker = new Thread(() -> {
            String result;
            try
            {
                final String python=System.getProperty("sud.python",System.getProperty("os.name","").toLowerCase().contains("win")?"python":"python3");
                final ProcessBuilder builder = new ProcessBuilder(python, runner.getPath(), selected.getPath());
                builder.environment().put("WORLD", WorldFiles.worldDir().getCanonicalPath());
                builder.redirectErrorStream(true);
                final File output = File.createTempFile("sud-world-test-", ".log");
                builder.redirectOutput(output);
                final Process child = builder.start();
                if(!child.waitFor(10, java.util.concurrent.TimeUnit.MINUTES))
                { child.destroyForcibly(); result = "WORLD TEST timed out. Log: " + output; }
                else
                {
                    final StringBuilder text = new StringBuilder();
                    try(final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(output), "UTF-8")))
                    {
                        String line;
                        while((line = reader.readLine()) != null && text.length() < 24000) text.append(line).append("\n\r");
                    }
                    result = "WORLD TEST " + (child.exitValue() == 0 ? "PASS" : "FAIL") + "\n\r" + text + "Log: " + output;
                }
            }
            catch(final Exception e) { result = "WORLD TEST failed to start: " + e.getMessage(); }
            finally { running.set(false); }
            final String report = result;
            CMLib.threads().executeRunnable(() -> { if(!viewer.amDestroyed()) viewer.tell(report); });
        }, "World-test");
        worker.setDaemon(true);
        worker.start();
        return "WORLD TEST started in a disposable campaign: " + selected.getName() + ". Results will appear here.";
    }
}
