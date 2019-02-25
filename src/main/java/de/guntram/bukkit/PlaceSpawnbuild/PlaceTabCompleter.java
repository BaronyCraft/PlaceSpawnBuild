/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.guntram.bukkit.PlaceSpawnbuild;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

/**
 *
 * @author gbl
 */
public class PlaceTabCompleter implements TabCompleter {

    Plugin plugin;
    
    PlaceTabCompleter(Plugin plugin) {
        this.plugin=plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions=new ArrayList<>();
        if (args.length == 1) {
            File schematicsDir=plugin.getDataFolder();
            schematicsDir.mkdirs();
            String[] filenames = schematicsDir.list();
            if (filenames != null) {
                for (String filename: filenames) {
                    if (StringUtil.startsWithIgnoreCase(filename, args[0]) && filename.endsWith(".schematic")) {
                        completions.add(filename);
                    }
                }
            }
        }
        
        else if (args.length == 2) {
            for (World world: Bukkit.getWorlds()) {
                String worldname=world.getName();
                if (StringUtil.startsWithIgnoreCase(worldname, args[1])) {
                    completions.add(worldname);
                }
            }
        }
        return completions;
    }
}
