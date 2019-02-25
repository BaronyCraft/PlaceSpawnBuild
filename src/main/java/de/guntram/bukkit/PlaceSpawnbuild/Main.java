package de.guntram.bukkit.PlaceSpawnbuild;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {
    
    public static Main instance;
    
    @Override 
    public void onEnable() {
        if (instance==null)
            instance=this;
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("placespawnbuild").setTabCompleter(new PlaceTabCompleter(this));
    }
    
    @Override
    public void onDisable() {
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName=command.getName();
        Location targetLocation;
        if (commandName.equals("placespawnbuild")) {

            if (args.length != 4 && args.length != 5) {
                return false;           // bukkit will display usage info
            }

            if (!(sender.hasPermission("placespawnbuild.place"))) {
                sender.sendMessage("You can't do that");
                return true;
            }
            
            File schemaFile=new File(this.getDataFolder(), args[0]);
            if (!(schemaFile.exists())) {
                sender.sendMessage("Schema file "+schemaFile.getAbsolutePath()+" doesn't exist");
                return true;
            }

            World world=Bukkit.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage("That world doesn't exist");
                return true;
            }

            ClipboardFormat format=ClipboardFormats.findByFile(schemaFile);
            if (format == null) {
                sender.sendMessage("Cannot find format of "+schemaFile.getAbsolutePath());
                return true;
            }
            
            Clipboard clipboard;
            try {
                ClipboardReader reader=format.getReader(new BufferedInputStream(new FileInputStream(schemaFile)));
                clipboard=reader.read();
                reader.close();
            } catch (IOException ex) {
                sender.sendMessage("Loading of "+schemaFile.getAbsolutePath()+" failed");
                return true;
            }

            int x, z, dy;
            try {
                x=Integer.parseInt(args[2]);
                z=Integer.parseInt(args[3]);
                if (args.length==5) {
                    dy=Integer.parseInt(args[4]);
                } else {
                    dy=0;
                }
            } catch (NumberFormatException ex) {
                sender.sendMessage("coordinate arguments must be integers");
                return true;
            }

            int yPos = API.getInstance().build(clipboard, world, x, z, dy);
            sender.sendMessage(schemaFile.getName()+" has been placed at "+x+"/"+z+" height "+yPos);
            return true;
        }
        return false;
    }
}
