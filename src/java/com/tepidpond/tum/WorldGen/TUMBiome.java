package com.tepidpond.tum.WorldGen;

import net.minecraft.world.biome.BiomeGenBase;

public class TUMBiome extends BiomeGenBase {
	public static final TUMBiome plains = new TUMBiome(1).setBiomeName("World");
	
	
	public TUMBiome(int par1)
	{
		super(par1);
	}
	
	public TUMBiome setBiomeName(String name)
	{
		return this;
	}
}
