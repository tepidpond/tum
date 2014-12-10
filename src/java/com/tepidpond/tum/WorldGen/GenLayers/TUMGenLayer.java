package com.tepidpond.tum.WorldGen.GenLayers;

import com.tepidpond.tum.G;

import net.minecraft.world.gen.layer.GenLayer;

public abstract class TUMGenLayer extends GenLayer {
	protected long worldGenSeed;
	protected TUMGenLayer parent;
	protected long chunkSeed;
	protected long baseSeed;

	public TUMGenLayer(long Seed) {
		super(Seed);
		this.baseSeed = Seed;
		for (int k=0; k<3; k++) {
			this.baseSeed *= this.baseSeed * G.LCG_Mult_A + G.LCG_Inc_C;
			this.baseSeed += Seed;
		}
	}
	
	@Override
	public void initWorldGenSeed(long Seed)
	{
		this.worldGenSeed = Seed;
		if (this.parent!=null)
			parent.initWorldGenSeed(Seed);
		
		for (int k=0; k<3; k++) {
			worldGenSeed *= worldGenSeed * G.LCG_Mult_A + G.LCG_Inc_C;
			worldGenSeed += baseSeed;
		}
	}
	
	/**
	 * Initialize layer's current chunkSeed based on the local worldGenSeed and the (x,z) chunk coordinates.
	 */
	@Override
	public void initChunkSeed(long chunkX, long chunkZ)
	{
		chunkSeed = worldGenSeed;
		for (int k=0; k<2; k++) {
			chunkSeed *= chunkSeed * G.LCG_Mult_A + G.LCG_Inc_C;
			chunkSeed += chunkX;
			chunkSeed *= chunkSeed * G.LCG_Mult_A + G.LCG_Inc_C;
			chunkSeed += chunkZ;
		}
	}

	@Override
	protected int nextInt(int max)
	{
		int ret = (int)((this.chunkSeed >> 24) % max);
		if (ret < 0)
			ret += max;
		chunkSeed *= chunkSeed * G.LCG_Mult_A + G.LCG_Inc_C;
		chunkSeed += worldGenSeed;
		return ret;
	}
}
