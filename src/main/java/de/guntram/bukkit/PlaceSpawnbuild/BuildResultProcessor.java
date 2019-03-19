/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.guntram.bukkit.PlaceSpawnbuild;

import org.bukkit.World;

/**
 *
 * @author gbl
 */
public interface BuildResultProcessor {
    public void processBuildResult(World world, int minx, int maxx, int miny, int maxy, int minz, int maxz);
}
