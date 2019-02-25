/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.guntram.bukkit.PlaceSpawnbuild;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

/**
 *
 * @author gbl
 */
public class API {
    
    private static API instance;
    
    private void API() {
    }
    
    public static API getInstance() {
        if (instance==null) {
            instance=new API();
        }
        return instance;
    }
    
    public int build(File schemaFile, World world, int x, int z, int dy) throws IOException {
        ClipboardFormat format=ClipboardFormats.findByFile(schemaFile);
        if (format == null) {
            throw new IOException("Cannot find format of "+schemaFile.getAbsolutePath());
        }

        Clipboard clipboard;
        clipboard=format.getReader(new BufferedInputStream(new FileInputStream(schemaFile))).read();
        return build(clipboard, world, x, z, dy);
    }
    
    public int build(Clipboard clipboard, World world, int x, int z, int dy) {
        int y=(world.getMaxHeight()-1);
        if (world.getEnvironment() == World.Environment.NETHER) {
            y=126;
        }
        BlockVector3 dimensions = clipboard.getDimensions();
        x-=dimensions.getBlockX()/2;
        z-=dimensions.getBlockZ()/2;

        // go down from world height to empty space; mainly for nether
        while (y>0 && !isCubeEmpty(world, x, dimensions.getBlockX(), y, 1, z, dimensions.getBlockZ())) {
            y--;
        }
        
        // now find the highest elevation that has a non-air block, account for moving the structure down
        while (y>0 && y>dy && isCubeEmpty(world, x, dimensions.getBlockX(), y, 1, z, dimensions.getBlockZ())) {
            y--;
        }
        y++;    // up 1 to be where space was empty
        if (y<=dy) {
            // We can't find ANY air block. In that case, fall back to something that should work in most cases
            y=64;
        }
        y-=dy;
        if (y<=0) {
            // don't break the lowest layer of bedrock
            y=1;
        }
        
        int maxBlocks=dimensions.getBlockX()*dimensions.getBlockY()*dimensions.getBlockZ();
        clipboard.setOrigin(clipboard.getRegion().getMinimumPoint());

        ClipboardHolder holder=new ClipboardHolder(clipboard);
        BlockVector3 target=BlockVector3.at(x, y, z);
        BukkitWorld bworld=new BukkitWorld(world);
        EditSession session=WorldEdit.getInstance().getEditSessionFactory().getEditSession(bworld, maxBlocks);
        Operation operation = holder.createPaste(session)
                .to(target)
                .build();
        try {
            Operations.completeLegacy(operation);
            Operations.completeLegacy(session.commit());
        } catch (MaxChangedBlocksException ex) {
            System.err.println(ex);
            return -1;
        }
        
        // now replace air blocks below the structure with blocks from the lowest layer of the structure
        for (int dx=0; dx<dimensions.getBlockX(); dx++) {
            for (int dz=0; dz<dimensions.getBlockZ(); dz++) {
                int addx=0;
                int addz=0;
                // If we're at the outside of the build, and the lowest block of the original structure is a stair ...
                Block bottomBlock = world.getBlockAt(x+dx, y, z+dz);
                BlockData toCopy = bottomBlock.getBlockData();
                Material mat=bottomBlock.getType();
                // There is no isStair() method, so this is probably the best 
                // thing we can do except enumerating them which isn't exactly
                // future-proof.
                if (mat.toString().toLowerCase().contains("stair")) {
                    if      (dx==0 && dz>0 && dz<dimensions.getBlockZ()-1)                        { addx = -1; }
                    else if (dx==dimensions.getBlockX()-1 && dz>0 && dz<dimensions.getBlockZ()-1) { addx =  1; }
                    else if (dz==0 && dx>0 && dx<dimensions.getBlockX()-1)                        { addz = -1; }
                    else if (dz==dimensions.getBlockZ()-1 && dx>0 && dx<dimensions.getBlockX()-1) { addz =  1; }
                    else if (dx==0 || dz==0 || dx==dimensions.getBlockX()-1 || dz==dimensions.getBlockZ()-1) {
                        // don't repeat stairs at the edge
                        continue;
                    }
                }
                boolean isStair=(addx!=0 || addz!=0);
                for (dy=-1;y+dy>0;dy--) {
                    Block toPaste=world.getBlockAt(x+dx-dy*addx, y+dy, z+dz-dy*addz);
                    mat=toPaste.getType();
                    if (!mat.isEmpty())
                        break;
                    toPaste.setBlockData(toCopy);
                    // If we placed a stair, place anything above it, like rails, as well
                    for (int yUp=1; isStair && yUp<10; yUp++) {
                        Block blockAboveStair=world.getBlockAt(x+dx, y+yUp, z+dz);
                        if (blockAboveStair.getType().isEmpty())
                            break;
                        world.getBlockAt(x+dx-dy*addx, y+dy+yUp, z+dz-dy*addz).setBlockData(blockAboveStair.getBlockData());
                    }
                }
            }
        }
        return y;
    }
    
    private boolean isCubeEmpty(World world, int minx, int sizex, int miny, int sizey, int minz, int sizez) {
        for (int y=miny; y<miny+sizey; y++) {
            for (int x=minx; x<minx+sizex; x++) {
                for (int z=minz; z<minz+sizez; z++) {
                    if (!(world.getBlockAt(x, y, z).getType().isEmpty())) {
                        // System.out.println("isCubeEmpty returns false because "+x+"/"+y+"/"+z+" is "+world.getBlockAt(x, y, z));
                        return false;
                    }
                }
            }
        }
        // System.out.println("isCubeEmpty("+world.getName()+", "+minx+", "+sizex+", "+miny+", "+sizey+", "+minz+","+sizez+") returns true");
        return true;
    }
}
