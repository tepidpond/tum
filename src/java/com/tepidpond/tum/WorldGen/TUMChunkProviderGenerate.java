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

import com.tepidpond.tum.PlateTectonics.Lithosphere;
import com.tepidpond.tum.PlateTectonics.Util;

public class TUMChunkProviderGenerate extends ChunkProviderGenerate {
    private static final Logger logger = LogManager.getLogger();

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
			data.setHeightMap(Util.normalizeHeightMapCopy(lithos.getHeightmap()), lithos.getMapSize());
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
	
	private float quadInterpolate(float[] map, int mapSideLength, float X, float Y) {
		int mapOriginX = (int)Math.floor(X); while (mapOriginX < 0) mapOriginX += mapSideLength; mapOriginX %= mapSideLength;
		int mapOriginY = (int)Math.floor(Y); while (mapOriginY < 0) mapOriginY += mapSideLength; mapOriginY %= mapSideLength;
		float mapOffsetX = (float) (X - Math.floor(X));
		float mapOffsetY = (float) (Y - Math.floor(Y));
		int x0 = mapOriginX, x1 = mapOriginX, y0 = mapOriginY, y1 = mapOriginY;
		if (mapOffsetX > mapOriginX) {
			x1 = Math.min(mapOriginX + 1, mapSideLength - 1);
		} else if (mapOffsetX < 0) {
			mapOffsetX = -mapOffsetX;
			x1 = Math.max(mapOriginX - 1, 0);
		}
		if (mapOffsetY > 0) {
			y1 = Math.min(mapOriginY + 1, mapSideLength - 1);
		} else if (mapOffsetY < 0) {
			mapOffsetY = -mapOffsetY;
			y1 = Math.max(mapOriginY - 1, 0);
		}
		int a = y0 * mapSideLength + x0;
		int b = y0 * mapSideLength + x1;
		int c = y1 * mapSideLength + x0;
		int d = y1 * mapSideLength + y1;
		
		// quadratic interpolation: a + (b-a)x + (c-a)y + (a-b-c+d)xy
		return map[a] + (map[b] - map[a]) * mapOffsetX + (map[c] - map[a]) * mapOffsetY +
				(map[a] - map[b] - map[c] + map[d]) * mapOffsetX * mapOffsetY;
	}
	
	private void generateTerrain(int chunkX, int chunkZ, Block[] idsTop, Random rand, Block[] idsBig, byte[] metaBig) {
		int worldHeight = 256;
		int indexOffset = 128;
		
		float[] hm = data.getHeightMap();
		float vals[][] = new float[3][3];
		int hmSourceX = (chunkX + data.getMapSize() / 2) % data.getMapSize();
		int hmSourceZ = (chunkZ + data.getMapSize() / 2) % data.getMapSize();
		if (hmSourceX < 0 || hmSourceZ < 0) return;		// empty chunks outside generation border.

		for (int z = 0; z < 3; z++) {
			for (int x = 0; x < 3; x++) {
				int hmX = hmSourceX + x - 1;
				int hmZ = hmSourceZ + z - 1;
				if (hmX < 0) hmX = 0; if (hmX >= data.getMapSize()) hmX = data.getMapSize() - 1;
				if (hmZ < 0) hmZ = 0; if (hmZ >= data.getMapSize()) hmZ = data.getMapSize() - 1;
				int tileIndex = hmZ * data.getMapSize() + hmX;

				vals[x][z] = hm[tileIndex];
			}
		}

		for (int x = 0; x<16; x++)
		{
			for (int z = 0; z<16; z++)
			{
				int sample = (int) (128f * quadInterpolate(vals, (x - 8f) / 16f, (z - 8f) / 16f));
				
				int arrayIndex = x + z * 16;
				for (int height = 127; height >= 0; height--)
				{
					int indexBig = x * 16 * 256 | z * 256 | height;

					if (sample > height)
						idsBig[indexBig] = Blocks.stone;
					else
						idsBig[indexBig] = Blocks.air;
				}
			}
		}
		
	}
}
