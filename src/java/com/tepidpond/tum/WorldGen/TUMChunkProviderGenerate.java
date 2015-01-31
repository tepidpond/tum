package com.tepidpond.tum.WorldGen;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkProvider;
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
	
	public TUMChunkProviderGenerate(World world, long seed, boolean par4) {
		super(world, seed, par4, "");	// Blank sets ChunkProviderSettings to defaults
		worldObj = world;
		rand = new Random(seed);
	}
	
	private void loadPerWorldData() {
		data = TUMPerWorldData.get(worldObj);
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
		if (data == null) loadPerWorldData();

		this.rand.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
		
		ChunkPrimer cp = new ChunkPrimer();
		generateTerrain(chunkX, chunkZ, cp, rand);
		
		Chunk chunk = new Chunk(this.worldObj, cp, chunkX, chunkZ);
		
		chunk.generateSkylightMap();
		return chunk;
	}

	private float quadInterpolate(float[][] field, float X, float Y) {
		int x0 = 1, x1 = 1, y0 = 1, y1 = 1;
		if (X > 0) {
			x0 = 1; x1 = 2;
		} else if (X < 0) {
			X = -X;
			x0 = 1; x1 = 0;
		}
		if (Y > 0) {
			y0 = 1; y1 = 2;
		} else if (Y < 0) {
			Y = -Y;
			y0 = 1; y1 = 0;
		}
		
		// quadratic interpolation: a + (b-a)x + (c-a)y + (a-b-c+d)xy
		return field[x0][y0] +
		      (field[x1][y0] - field[x0][y0]) * X +
		      (field[x0][y1] - field[x0][y0]) * Y +
		      (field[x0][y0] - field[x1][y0] - field[x0][y1] + field[x1][y1]) * X * Y;
	}
	
	@Override
	public void populate(IChunkProvider icp, int chunkX, int chunkZ) {
		// TODO: Do something.
		return;	// Do nothing!
	}
	
	private void generateTerrain(int chunkX, int chunkZ, ChunkPrimer cp, Random rand) {
		int worldHeight = 256;
		float scaleFactor = 1f / 4f;
		float min = TUMPerWorldData.get(worldObj).getHeightMapMin();
		float max = TUMPerWorldData.get(worldObj).getHeightMapMax();
		float reservedBasement = 4f;
		float heightScale = (worldHeight - reservedBasement) / (max - min);
		int seaLevel = (int) ((Lithosphere.CONTINENTAL_BASE - min) * heightScale);

		float[] hm = data.getHeightMap();
		for (int x = 0; x<16; x++)
		{
			float xCoord = ((chunkX * 16) + x);
			for (int z = 0; z<16; z++)
			{
				float zCoord = ((chunkZ * 16) + z);
				float sample = Util.quadInterpolate(hm, data.getMapSize(), xCoord * scaleFactor, zCoord * scaleFactor);
				sample = (sample - min) * heightScale + reservedBasement;
				
				for (int height = 0; height < worldHeight; height++)
				{
					if (height < reservedBasement)
						cp.setBlockState(x, height, z, Blocks.bedrock.getDefaultState());
					else if (sample > height)
						cp.setBlockState(x, height, z, Blocks.stone.getDefaultState());
					else if (height < seaLevel)
						cp.setBlockState(x, height, z, Blocks.water.getDefaultState());
					else
						cp.setBlockState(x, height, z, Blocks.air.getDefaultState());
				}
			}
		}
		
	}
}
