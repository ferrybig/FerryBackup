/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferry.bukkit.plugins;

import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author Fernando
 */
public abstract class PluginBase extends JavaPlugin
{
	protected String name;
	protected String loggerTag;
	protected Logger log;

	@Override
	public void onLoad()
	{
		this.loggerTag = "[" + (this.name = this.getDescription().getName()) + "] ";
		this.log = this.getLogger();
	}

	@Override
	public void onDisable()
	{
		logInfo("Stopped");
	}

	@Override
	public void onEnable()
	{

		if (this instanceof Listener)
		{
			logInfo("Registering events");
			this.getServer().getPluginManager().registerEvents((Listener) this, this);
		}
		logInfo("Started");

	}

	public void logInfo(String msg)
	{
		log.info(msg);
	}

	protected void logWarning(String msg)
	{
		log.warning(msg);
	}
	protected void sendMessage(Player player, String msg)
	{
		player.sendMessage(ChatColor.GOLD + "[" + ChatColor.GREEN + name + ChatColor.GOLD + "] " + ChatColor.WHITE + msg);
	}

}
