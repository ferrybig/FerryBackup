/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferry.bukkit.plugins.ferrybackup;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.ferry.bukkit.plugins.BukkitWorker;
import me.ferry.bukkit.plugins.PluginBase;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 *
 * @author Fernando
 */
public class FerryBackupPlugin extends PluginBase
{
	@Override
	public boolean onCommand(final CommandSender sender, Command command, String label, String[] args)
	{
		if (command.getName().equalsIgnoreCase("backup"))
		{
			List<World> worlds = Bukkit.getWorlds();
			List<String> strWorlds = new ArrayList<String>(worlds.size());
			for (World w : worlds)
			{
				strWorlds.add(w.getName());
			}
			BukkitWorker task = new BackupTask(this, strWorlds.toArray(new String[worlds.size()]));
			task.execute();
			return true;
		}
		return false;
	}
}
