package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Live, hand-authored assets: tracked with the world, never generated or deleted by SAVE. */
public final class WorldAssets
{
	private WorldAssets()
	{
	}
	public static final List<String> DIRECTORIES=Collections
		.unmodifiableList(Arrays.asList("vocab","lore","text","resources","kits","encounters","classes","modules","compositions","combat",
		"itemmods"));
	public static final List<String> FILES=Collections
		.unmodifiableList(Arrays.asList("score.txt","ranks.ini","crops.txt","resources.txt","materials.txt",
		"creation.ini","origins.ini","perks.ini","combinations.txt","itemsets.txt","affixes.xml"));
	/**
	 * The optional modules' sources, and the random-enchantment affix file they share: each
	 * subsystem pins what a save uses itself (module fingerprints, composition, combat and item
	 * rule versions), so these stay out of the world version a save records and the content
	 * cache's checksum. A world that never uses modules versions as it did before them.
	 */
	public static final List<String> VERSIONED_APART=Collections
		.unmodifiableList(Arrays.asList("modules/","compositions/","combat/","itemmods/","affixes.xml"));
	public static boolean versionedApart(final String path)
	{
		for(final String prefix:VERSIONED_APART)
			if(prefix.endsWith("/")?path.startsWith(prefix):path.equals(prefix))
				return true;
		return false;
	}
	public static boolean contains(final String path)
	{
		if(FILES.contains(path))
			return true;
		for(final String dir:DIRECTORIES)
			if(path.startsWith(dir+"/"))
				return true;
		return false;
	}
	/** These readers use live files or the caches cleared by reload(). Other resources need their own loaders. */
	public static boolean reloadable(final String path)
	{
		return path.startsWith("vocab/")||path.startsWith("lore/")||path.startsWith("text/")||path.startsWith("kits/")||path.startsWith("encounters/")
			||Arrays.asList("score.txt","creation.ini","origins.ini","perks.ini","combinations.txt","itemsets.txt").contains(path);
	}
	/** An explicit script reload acknowledges only the dependencies it actually reads. */
	public static void scriptsLoaded(final Collection<String> files)
	{
		for(final String file:files)
		{
			String path=Resources.makeFileResourceName(file);
			if(path.startsWith("::"))
				continue;
			if(path.startsWith("//"))
				path=path.substring(2);
			final File resource=Vocabulary.file(path);
			if(resource!=null&&resource.isFile())
				WorldFiles.markLoaded(path);
		}
	}
	public static List<String> paths()
	{
		final List<String> paths=new ArrayList<String>();
		final Path root=WorldFiles.worldDir().toPath();
		for(final String dir:DIRECTORIES)
		{
			if(!Files.isDirectory(root.resolve(dir)))
				continue;
			try(final Stream<Path> files=Files.walk(root.resolve(dir)))
			{
				files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
					.forEach(p->paths.add(root.relativize(p).toString().replace(File.separatorChar,'/')));
			}
			catch(final IOException e)
			{
				Log.errOut("WorldAssets",dir+": "+e.getMessage());
			}
		}
		for(final String file:FILES)
			if(Files.isRegularFile(root.resolve(file)))
				paths.add(file);
		Collections.sort(paths);
		return paths;
	}
	public static void reload()
	{
		Vocabulary.clear();
		LanguageLexicon.clear();
		CreationSteps.clear();
		Perks.clear();
		Combinations.clear();
		Encounters.forget();
		com.planet_ink.coffee_mud.Abilities.Properties.Prop_ItemSet.clearDefinitions();
		CMLib.help().unloadHelpFile(null);
		CMLib.socials().unloadSocials();
		PropCheck.clearHelpKeys();
		WorldFiles.acceptAssetChanges();
	}
}
