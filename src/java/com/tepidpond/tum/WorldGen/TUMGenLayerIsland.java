package com.tepidpond.tum.WorldGen;

import net.minecraft.world.gen.layer.IntCache;

import com.tepidpond.tum.WorldGen.GenLayers.TUMGenLayer;

public class TUMGenLayerIsland extends TUMGenLayer {

	public TUMGenLayerIsland(long Seed) {
		super(Seed);
	}

	@Override
	public int[] getInts(int chunkX, int chunkZ, int maxX, int maxZ) {
		int ints[] = IntCache.getIntCache(maxX * maxZ);
		for (int z=0; z<maxZ; z++)
		{
			for (int x=0; x<maxX; x++)
			{
				this.initChunkSeed(chunkX + x, chunkZ + z);
				ints[x+z*maxZ] = this.nextInt(4) == 0 ? 1 : 0;
			}
		}
		if (chunkX > -maxX && chunkX <= 0 && chunkZ > -maxZ && chunkZ <= 0)
			ints[-chunkX + -chunkZ * maxZ] = 1;
		
		return ints;
	}

}
