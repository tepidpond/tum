package com.tepidpond.tum.WorldGen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeCache;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.layer.GenLayer;

public class TUMWorldChunkManager extends WorldChunkManager {
	protected World worldObj;
	public long seed = 0;
	
	protected GenLayer genBiomes;
	protected GenLayer biomeIndexLayer;
	protected BiomeCache biomeCache;
	protected List biomesToSpawnIn;
	
	public TUMWorldChunkManager() {
		super();
		biomeCache = new BiomeCache(this);
		this.biomesToSpawnIn = new ArrayList();
		this.biomesToSpawnIn.add(TUMBiome.plains);
	}
	
	public TUMWorldChunkManager(World world)
	{
		this(world.getSeed(), world.getWorldInfo().getTerrainType());
		worldObj = world;
	}

	public TUMWorldChunkManager(long Seed, WorldType terrainType) {
		this();
		seed = Seed;
		
		GenLayer[] layers;
		layers = GenLayer.initializeAllBiomeGenerators(Seed, TUMWorldType.DEFAULT, "");
		this.genBiomes = new TUMGenLayerIsland(Seed);
		this.biomeIndexLayer = new TUMGenLayerIsland(Seed);
	}
	
	@Override
	public BlockPos findBiomePosition(int x, int z, int radius, List biomeList, Random rand)
	{
		// TODO: Probably do something here.
		return new BlockPos(x, 0, z); 
	}
	
	@Override
	public float[] getRainfall(float[] listToReuse, int x, int z, int width, int length)
	{
		// TODO: Definitely something here.
		return new float[width*length];
	}
	
	@Override
	public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] biome, int xOrigin, int zOrigin, int width, int length, boolean cacheFlag)
	{
		if (biome == null || biome.length < width*length)
			biome = new TUMBiome[width*length];
		
		// Only cache when dealing with full/aligned chunks
		if (cacheFlag && width==16 && length==16 && (xOrigin & 15)==0 && (zOrigin & 15)==0)
		{
			BiomeGenBase[] cache = this.biomeCache.getCachedBiomes(xOrigin, zOrigin);
			System.arraycopy(cache, 0, biome, 0, width * length);
		} else {
			// Get data from PRNG
			int[] ints = this.biomeIndexLayer.getInts(xOrigin, zOrigin, width, length);
			for(int z = 0; z < width; z++) {
				for (int x = 0; x < length; x++) {
					int id = ints[z * width + x] != -1 ? ints[z * width + x] : 0;
					biome[z * width + x] = TUMBiome.plains;
				}
			}
		}
		return biome;
	}
	
	@Override
	public boolean areBiomesViable(int x, int z, int radius, List allowableBiomes)
	{
		// TODO: Definitely something here.
		return true;
	}
}
