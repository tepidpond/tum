package com.tepidpond.tum.WorldGen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeCache;
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
	}
}
