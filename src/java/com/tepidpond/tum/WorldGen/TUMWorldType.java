package com.tepidpond.tum.WorldGen;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

public class TUMWorldType extends WorldType {

	public TUMWorldType(String name) {
		super(name);
	}

	public BiomeGenBase[] getBiomesForWorldType()
	{
		return null;
	}
	
	@Override
	public WorldChunkManager getChunkManager(World world)
	{
		return new TUMWorldChunkManager(world);
	}
	
	@Override
	public IChunkProvider getChunkGenerator(World world, String generatorOptions)
	{
		return new TUMChunkProviderGenerate(world,  world.getSeed(),  world.getWorldInfo().isMapFeaturesEnabled());
	}
	
	@Override
	public int getMinimumSpawnHeight(World world)
	{
		return 64;
	}
	
	@Override
	public double getHorizon(World world)
	{
		return 63;
	}
}
