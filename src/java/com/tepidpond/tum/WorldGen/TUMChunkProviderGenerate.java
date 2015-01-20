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
		
		// round to nearest int
		int mapOriginX = (int)Math.round(X);
		int mapOriginY = (int)Math.round(Y);
		// save fractional part for interpolating
		X -= mapOriginX; Y -= mapOriginY;
		// wrap into range (0,511) 
		mapOriginX %= mapSideLength; if (mapOriginX < 0) mapOriginX += mapSideLength;
		mapOriginY %= mapSideLength; if (mapOriginY < 0) mapOriginY += mapSideLength;

		if (Math.abs(X) < 1 / 64f && Math.abs(Y) < 1 / 64f)
			return map[Util.getTile(mapOriginX, mapOriginY, mapSideLength)];

		// select x/y coordinates for points a(x0, y0), b(x1, y0), c(x0, y1), d(x1, y1)
		// Wrapping at edges is most natural.
		int x0 = mapOriginX, x1 = mapOriginX, y0 = mapOriginY, y1 = mapOriginY;
		if (X > 0) {
			x1 = (mapOriginX + 1) % mapSideLength;
		} else if (X < 0) {
			x1 = mapOriginX == 0 ? mapSideLength - 1: mapOriginX - 1;
		}
		if (Y > 0) {
			y1 = (mapOriginY + 1) % mapSideLength;
		} else if (Y < 0) {
			x1 = mapOriginY == 0 ? mapSideLength - 1: mapOriginY - 1;
		}
		// X/Y values need to be positive for the below formula. Because pt a is defined
		// to be the origin this works, X/Y are then positive offsets away from the origin
		X = Math.abs(X); Y = Math.abs(Y);
		// Calc indices for the points in the heightMap.
		int a = Util.getTile(x0, y0, mapSideLength);
		int b = Util.getTile(x1, y0, mapSideLength);
		int c = Util.getTile(x0, y1, mapSideLength);
		int d = Util.getTile(x1, y1, mapSideLength);

		assert (mapOriginX >= 0 && mapOriginX < mapSideLength && mapOriginY >= 0 && mapOriginY < mapSideLength) &&
		       (X >= -1f && X <= 1f && Y >= -1f && Y <= 1f) &&
		       (a >= 0 && b >= 0 && c >= 0 && d >= 0) &&
		       (a < map.length && b < map.length && c < map.length && d < map.length):
		       "Impossibilities in quadInterpolate, failed validation.";
		       
		// quadratic interpolation: a + (b-a)x + (c-a)y + (a-b-c+d)xy
		return map[a] + (map[b] - map[a]) * X + (map[c] - map[a]) * Y +
				(map[a] - map[b] - map[c] + map[d]) * X * Y;
	}
	
	private void generateTerrain(int chunkX, int chunkZ, Block[] idsTop, Random rand, Block[] idsBig, byte[] metaBig) {
		int worldHeight = 256;
		float scaleFactor = 1f;
		
		float[] hm = data.getHeightMap();
		for (int x = 0; x<16; x++)
		{
			float xCoord = ((chunkX << 4) + x + 0.5f) / scaleFactor;
			for (int z = 0; z<16; z++)
			{
				float zCoord = ((chunkZ << 4) + z + 0.5f) / scaleFactor;
				float sample = quadInterpolate(hm, data.getMapSize(), xCoord, zCoord);
				int iSample = (int) (sample * 63 / Lithosphere.CONTINENTAL_BASE);
				
				int arrayIndex = (x << 4 | z) * worldHeight;
				idsBig[arrayIndex] = Blocks.bedrock;
				for (int height = 1; height < worldHeight; height++)
				{
					arrayIndex++;
					if (iSample > height)
						idsBig[arrayIndex] = Blocks.stone;
					else
						idsBig[arrayIndex] = Blocks.air;
				}
			}
		}
		
	}
}
