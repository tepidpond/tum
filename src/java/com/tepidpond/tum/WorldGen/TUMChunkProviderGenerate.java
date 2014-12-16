package com.tepidpond.tum.WorldGen;

import java.util.Arrays;
import java.util.Random;

import com.tepidpond.tum.PlateTectonics.Lithosphere;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraftforge.event.terraingen.WorldTypeEvent.BiomeSize;

public class TUMChunkProviderGenerate extends ChunkProviderGenerate {
	private World worldObj;
	private Lithosphere lithos;
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
			
	    	lithos = new Lithosphere(512, 0.82f, 3, 0.5f, 5, 0.5f, 5, 9, seed);
	}
	
	@Override
	public Chunk provideChunk(int chunkX, int chunkZ)
	{
		this.rand.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
		
		Arrays.fill(idsTop, null);
		Arrays.fill(idsBig, null);
		Arrays.fill(metaBig, (byte)0);
		
		this.generateTerrainHigh(chunkX, chunkZ, idsTop);
		replaceBlocksForBiomeHigh(chunkX, chunkZ, idsTop, rand, idsBig, metaBig);
		
		Chunk chunk = new Chunk(this.worldObj, idsBig, metaBig, chunkX, chunkZ);
		
		chunk.generateSkylightMap();
		return chunk;
	}

	private void replaceBlocksForBiomeHigh(int chunkX, int chunkZ, Block[] idsTop, Random rand, Block[] idsBig, byte[] metaBig) {
		int worldHeight = 256;
		int indexOffset = 128;
		
		for (int x = 0; x<16; x++)
		{
			for (int z = 0; z<16; z++)
			{
				int arrayIndex = x + z * 16;
				for (int height = 127; height >= 0; height--)
				{
					int indexBig = arrayIndex * worldHeight + height + indexOffset;
					int indexTop = arrayIndex * 128 + height;
					idsBig[indexBig] = idsTop[indexTop];
				}
			}
		}
		
	}

	public void generateTerrainHigh(int chunkX, int chunkZ, Block[] idsTop)
	{
		// TODO:  And God said, “Let the water under the sky be gathered to
		// one place, and let dry ground appear.” And it was so. 10 God called
		// the dry ground “land,” and the gathered waters he called “seas.”
		// And God saw that it was good. 
		
		// Define herein what blocks are water, what blocks are air, and what blocks
		// are dry ground.
		int seaLevel = 16;

		int arrayYHeight = 128;
		for (int x=0; x<4; x++)
		{
			for (int z=0; z<4; z++)
			{
				for (int y =0; y<16; y++)
				{
					for (int i = 0; i<8; i++)
					{
						for (int j=0; j<4; j++)
						{
							int index = j + x*4 << 11 | 0 + z*4 << 7 | y*8 + i;

							for (int k=0; k<4; k++)
							{
								if (y*8+i < seaLevel)
									idsTop[index+k*arrayYHeight] = Blocks.stone;
								else
									idsTop[index+k*arrayYHeight] = Blocks.air;
							}
						}
					}
				}
			}
		}
	}
}
