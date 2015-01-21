package com.tepidpond.tum.WorldGen;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderGenerate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tepidpond.tum.G;
import com.tepidpond.tum.PlateTectonics.Lithosphere;
import com.tepidpond.tum.PlateTectonics.Util;

public class TUMChunkProviderGenerate extends ChunkProviderGenerate {
    private static final Logger logger = LogManager.getLogger(G.ModID + ".WorldGen");

	private World worldObj;
	private Lithosphere lithos;
	private TUMPerWorldData data;
	private Random rand;
	
	private Block[] idsTop;
	private Block[] idsBig;
	private byte[] metaBig;
	
	public TUMChunkProviderGenerate(World world, long seed, boolean par4) {
		super(world, seed, par4);
		worldObj = world;
		rand = new Random(seed);
		
		this.idsTop = new Block[32768];
		this.idsBig = new Block[16*16*256];
		this.metaBig = new byte[16*16*256];
		
		data = TUMPerWorldData.get(world);
		if (!data.isHeightMapGenerated()) {
			logger.info("Pre-generating heightMap...");
			lithos = new Lithosphere(
					data.getMapSize(),
					data.getLandSeaRatio(),
					data.getErosionPeriod(),
					data.getFoldingRatio(),
					data.getAggrRatioAbs(),
					data.getAggrRatioRel(),
					data.getMaxCycles(),
					data.getNumPlates(),
					worldObj.getSeed());
			
			for (int i = 0; i < data.getMaxGens(); i++) {
				lithos.Update();
				// TODO: Some updating of something here is essential
			}
			logger.info("...done");
						
			// Save the normalized output.
			data.setHeightMap(lithos.getHeightmap(), lithos.getMapSize());
			data.markDirty();
		}
	}
	
	@Override
	public Chunk provideChunk(int chunkX, int chunkZ)
	{
		this.rand.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
		
		Arrays.fill(idsTop, null);
		Arrays.fill(idsBig, null);
		Arrays.fill(metaBig, (byte)0);
		
		generateTerrain(chunkX, chunkZ, idsTop, rand, idsBig, metaBig);
		
		Chunk chunk = new Chunk(this.worldObj, idsBig, metaBig, chunkX, chunkZ);
		
		chunk.generateSkylightMap();
		return chunk;
	}	
	
	private void generateTerrain(int chunkX, int chunkZ, Block[] idsTop, Random rand, Block[] idsBig, byte[] metaBig) {
		int worldHeight = 256;
		float scaleFactor = 1f / 4f;
		float min = TUMPerWorldData.get(worldObj).getHeightMapMin();
		float max = TUMPerWorldData.get(worldObj).getHeightMapMax();
		// We leave 4 blocks out because the base of the world should be consistent.
		float heightScale = (worldHeight - 4f) / (max - min);

		float[] hm = data.getHeightMap();
		for (int x = 0; x<16; x++)
		{
			float xCoord = ((chunkX * 16) + x);
			for (int z = 0; z<16; z++)
			{
				float zCoord = ((chunkZ * 16) + z);
				float sample = Util.quadInterpolate(hm, data.getMapSize(), xCoord * scaleFactor, zCoord * scaleFactor);
				sample -= min; sample *= heightScale; sample += 4f;
				
				int arrayIndex = (x << 4 | z) * worldHeight;
				idsBig[arrayIndex] = Blocks.bedrock;
				for (int height = 1; height < worldHeight; height++)
				{
					arrayIndex++;
					if (sample > height)
						idsBig[arrayIndex] = Blocks.stone;
					else
						idsBig[arrayIndex] = Blocks.air;
				}
			}
		}
		
	}
}
