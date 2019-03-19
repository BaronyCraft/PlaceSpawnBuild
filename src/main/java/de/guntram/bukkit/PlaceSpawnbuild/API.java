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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        return build(clipboard, world, x, z, dy, null);
    }
        
    public int build(Clipboard clipboard, World world, int x, int z, int dy, BuildResultProcessor processor) {
        int absminx, absmaxx, absminy, absmaxy, absminz, absmaxz;
        
        int y=(world.getMaxHeight()-1);
        if (world.getEnvironment() == World.Environment.NETHER) {
            y=126;
        }
        BlockVector3 dimensions = clipboard.getDimensions();
        x-=dimensions.getBlockX()/2;
        z-=dimensions.getBlockZ()/2;
        absminx=x;
        absminz=z;
        absmaxx=absminx+dimensions.getBlockX()-1;
        absmaxz=absminz+dimensions.getBlockZ()-1;
        

        // go down from world height to empty space; mainly for nether
        while (y>0 && !isCubeEmpty(world, x, dimensions.getBlockX(), y, 1, z, dimensions.getBlockZ())) {
            y--;
        }
        
        // now find the highest elevation that has a non-air block, account for moving the structure down
        while (y>0 && y>dy && isCubeEmpty(world, x, dimensions.getBlockX(), y, 1, z, dimensions.getBlockZ())) {
            y--;
        }
        if (y<=dy || y<=0) {
            // We can't find ANY air block. In that case, fall back to something that should work in most cases
            y=64;
        } else {
            y++;    // up 1 to be where space was empty
            y-=dy;

        }
        // double check to not break bedrock
        if (y<=0) {
            y=1;
        }
        absminy=y;
        absmaxy=y+dimensions.getBlockY()-1;
        
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
                boolean isOutgoingStair=(addx!=0 || addz!=0);
                for (dy=-1;y+dy>0;dy--) {
                    int blockx=x+dx-dy*addx;
                    int blockz=z+dz-dy*addz;
                    Block toPaste=world.getBlockAt(blockx, y+dy, blockz);
                    mat=toPaste.getType();
                    if (!shouldBeReplaced(mat))
                        break;
                    if (blockx < absminx) { absminx=blockx; }
                    if (blockz < absminz) { absminz=blockz; }
                    if (blockx > absmaxx) { absmaxx=blockx; }
                    if (blockz > absmaxz) { absmaxz=blockz; }
                    if (y+dy   < absminy) { absminy=y+dy;   }

                    toPaste.setBlockData(toCopy);
                    if (isOutgoingStair) {
                        // If we placed a stair, place anything above it, like rails, as well
                        int yUp=1;
                        while (yUp<dimensions.getBlockY()) {
                            Block blockAboveStair=world.getBlockAt(x+dx, y+yUp, z+dz);
                            if (blockAboveStair.getType().isEmpty())
                                break;
                            world.getBlockAt(x+dx-dy*addx, y+dy+yUp, z+dz-dy*addz).setBlockData(blockAboveStair.getBlockData());
                            yUp++;
                        }
                        while (yUp<dimensions.getBlockX()) {
                            world.getBlockAt(x+dx-dy*addx, y+dy+yUp, z+dz-dy*addz).setType(Material.AIR);
                            yUp++;
                        }
                    }
                }
            }
        }
        
        // Now that we know the maximum size of the area we've been building in, place stairs again; this time,
        // don't stop at full blocks (but don't break them either).
        
        int sx, sz, ex, ez;
        sx=x; ex=x+dimensions.getBlockX()-1;
        sz=z; ez=z+dimensions.getBlockZ()-1;
        forcePlaceStairs(world, sx, sx, y, sz+1, ez-1, -1,  0, sx-absminx+1);
        forcePlaceStairs(world, ex, ex, y, sz+1, ez-1,  1,  0, absmaxx-ex+1);
        forcePlaceStairs(world, sx+1, ex-1, y, sz, sz,  0, -1, sz-absminz+1);
        forcePlaceStairs(world, sx+1, ex-1, y, ez, ez,  0,  1, absmaxz-ez+1);
        
        if (processor!=null) {
            processor.processBuildResult(world, absminx, absmaxx, absminy, absmaxy, absminz, absmaxz);
        }
        return y;
    }
    
    private void forcePlaceStairs(World world, int fromx, int tox, int y, int fromz, int toz, int dx, int dz, int count) {
        // System.out.println("forcing stair from X="+fromx+"/"+tox+"  Z="+fromz+"/"+toz+" direction x="+dx+"/"+dz+" y="+y+" out by "+count+" blocks");
        for (int x=fromx; x<=tox; x++) {
            for (int z=fromz; z<=toz; z++) {
                Block sourceBlock=world.getBlockAt(x, y, z);
                for (int i=1; i<count; i++) {
                    Block targetBlock = world.getBlockAt(x+i*dx, y-i, z+i*dz);
                    Material targetMat=targetBlock.getType();
                    if (shouldBeReplaced(targetMat)) {
                        // System.out.println("\tat "+targetBlock.getLocation()+": material "+targetMat.toString()+"replacing with"+sourceBlock.getBlockData());
                        targetBlock.setBlockData(sourceBlock.getBlockData());
                        // make sure it's walkable
                        int yUp=1;
                        while(yUp<=3) {
                            Block blockAboveStair=world.getBlockAt(x, y+yUp, z);
                            if (blockAboveStair.getType().isEmpty())
                                break;
                            world.getBlockAt(x+i*dx, y-i+yUp, z+i*dz).setBlockData(world.getBlockAt(x, y+yUp, z).getBlockData());
                            yUp++;
                        }
                        while (yUp<=3) {
                            world.getBlockAt(x+i*dx, y-i+yUp, z+i*dz).setType(Material.AIR);
                            yUp++;
                        }
                    } else {
                        // System.out.println("\tat "+targetBlock.getLocation()+": material "+targetMat.toString()+"NOT replacing");
                    }
                }
            }
        }
    }
    
    private boolean shouldBeReplaced(Material mat) {
        return
            mat.isEmpty()
        ||  (!mat.isSolid() && mat!=Material.WATER && mat!=Material.LAVA)
        ||  mat.toString().toLowerCase().endsWith("log")
        ||  mat.toString().toLowerCase().endsWith("leaves")
        ;
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
