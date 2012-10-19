/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferry.bukkit.plugins.ferrybackup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.ferry.bukkit.plugins.BukkitWorker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 *
 * @author Fernando
 */
public class BackupTask extends BukkitWorker<Boolean, String, FerryBackupPlugin>
{
	private final String[] worlds;

	public BackupTask(FerryBackupPlugin plugin, String[] worlds)
	{
		super(plugin);
		this.worlds = worlds;
	}

	@Override
	protected void done() // May use bukkit methodes here
	{
		try
		{
			if (!this.get())
			{
				// backup failed
			}
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}
		catch (ExecutionException ex)
		{
			ex.printStackTrace();
		}
	}

	@Override
	protected void process(List<String> chunks) // May use bukkit methodes here
	{
		for (String msg : chunks)
		{
			if (msg.startsWith("/"))
			{
				// Its meant to be an command
				Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), msg.substring(1));
			}
			else
			{
				Bukkit.broadcastMessage(ChatColor.GOLD + "[" + ChatColor.GREEN + "BackUp" + ChatColor.GOLD + "] " + ChatColor.WHITE + msg);
			}
		}
	}

	@Override
	protected Boolean doInBackground() throws Exception // Not safe to use bukkit methodes here
	{
		this.publish("Backup started", "/save-all", "/save-off");
		File backupStorage = new File("backup");
		try
		{
			Thread.sleep(1000); // waits some time to let the commands run
			for (String world : worlds)
			{
				this.publish(world + ": started");
				File worldBase = new File(Bukkit.getServer().getWorldContainer(), world);
				File worldFile = new File(worldBase, "level.dat");
				if (!worldFile.exists())
				{
					this.publish("World not found, abording backup");
					return false;
				}


				List<String> toBackup = new ArrayList<String>();
				// Looking whits files need to be included inside backup
				//<editor-fold defaultstate="collapsed" desc="Looking for the files">
				{
					toBackup.add("level.dat");
					// Search the region directory
					{
						File[] regionFiles = new File(worldBase, "region").listFiles(new FilenameFilter()
						{
							@Override
							public boolean accept(File dir, String name)
							{
								// accept only .mca
								return name.endsWith(".mca");
							}
						});
						if (regionFiles != null)
						{
							for (File region : regionFiles)
							{
								toBackup.add("region" + File.separator + region.getName());
							}
						}
					}
					// Search the nether region directory
					{
						File[] regionFiles = new File(new File(worldBase, "DIM-1"), "region").listFiles(new FilenameFilter()
						{
							@Override
							public boolean accept(File dir, String name)
							{
								// accept only .mca
								return name.endsWith(".mca");
							}
						});
						if (regionFiles != null)
						{
							for (File region : regionFiles)
							{
								toBackup.add("DIM-1" + File.separator + "region" + File.separator + region.getName());
							}
						}
					}
					// Search the end region directory
					{
						File[] regionFiles = new File(new File(worldBase, "DIM1"), "region").listFiles(new FilenameFilter()
						{
							@Override
							public boolean accept(File dir, String name)
							{
								// accept only .mca
								return name.endsWith(".mca");
							}
						});
						if (regionFiles != null)
						{
							for (File region : regionFiles)
							{
								toBackup.add("DIM1" + File.separator + "region" + File.separator + region.getName());
							}
						}
					}
					//search the player directory
					{
						File[] playerFiles = new File(worldBase, "players").listFiles(new FilenameFilter()
						{
							@Override
							public boolean accept(File dir, String name)
							{
								// accept only .mca
								return name.endsWith(".dat");
							}
						});
						if (playerFiles != null)
						{
							for (File region : playerFiles)
							{
								toBackup.add("players" + File.separator + region.getName());
							}
						}
					}
					//search the data directory
					{
						File[] data = new File(worldBase, "data").listFiles(new FilenameFilter()
						{
							@Override
							public boolean accept(File dir, String name)
							{
								// accept only .mca
								return name.endsWith(".dat");
							}
						});
						if (data != null)
						{
							for (File region : data)
							{
								toBackup.add("data" + File.separator + region.getName());
							}
						}
					}
				}
				//</editor-fold>

				//creating the place where files will get stored
				File backupStorageWorld = new File(backupStorage, world);
				backupStorageWorld.mkdirs();
				File zipFile = new File(backupStorageWorld, String.format("%1$tY-%1$tm-%1$td-%1$tH-%1$tM-%1$tS", new GregorianCalendar()) + ".zip"); // worldedit zip file format, http://wiki.sk89q.com/wiki/WorldEdit/Snapshots#Filenames


				long lastTimeNotice = System.currentTimeMillis();

				// Create a buffer for reading the files
				byte[] buf = new byte[4096]; // 4KB
				ZipOutputStream out = null;
				try
				{
					// Create the ZIP file
					out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

					// loop files
					for (int i = 0, lenght = toBackup.size(); i < lenght; i++)
					{
						String file = toBackup.get(i);
						FileInputStream in = null;
						try
						{
							in = new FileInputStream(new File(worldBase, file));

							// Add ZIP entry to output stream.
							out.putNextEntry(new ZipEntry(file));

							// Transfer bytes from the file to the ZIP file
							int len;
							while ((len = in.read(buf)) > 0)
							{
								out.write(buf, 0, len);
							}

							// Complete the entry
							out.closeEntry();
						}
						finally
						{
							if (in != null)
							{
								in.close();
							}
						}
						if (System.currentTimeMillis() - lastTimeNotice > 5000)//5 sec
						{
							float percentComplete = ((float) i) / lenght * 100;
							this.publish(world + ": " + percentComplete + "%");
							lastTimeNotice = System.currentTimeMillis();
						}
					}
				}
				finally
				{
					this.publish(world + ": completed");
					if (out != null)
					{
						out.close();
					}
				}
			}
		}
		finally
		{
			this.publish("/save-on", "Backup completed");
		}


		return true;
	}

	private void searchDirectoryForFiles(File toLook, String baseLink, List<String> input)
	{
		File[] regionFiles = new File(toLook, "region").listFiles();
		if (regionFiles != null)
		{
			for (File region : regionFiles)
			{
				if (region.isDirectory())
				{
					searchDirectoryForFiles(region, baseLink + region.getName() + File.separator, input);

				}
				else
				{
					input.add(baseLink + region.getName());
				}
			}
		}
	}
}
