package com.tepidpond.tum.WorldGen;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

public class TUMProvider extends WorldProvider {
	@Override
	public String getDimensionName() {
		return "TUM.Default";
	}
	
	@Override
	public IChunkProvider createChunkGenerator()
	{
		return new TUMChunkProviderGenerate(worldObj, worldObj.getSeed(), worldObj.getWorldInfo().isMapFeaturesEnabled());
	}
}
