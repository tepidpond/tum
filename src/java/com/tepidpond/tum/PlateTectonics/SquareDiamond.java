package com.tepidpond.tum.PlateTectonics;

import com.tepidpond.tum.G;

import java.util.ArrayList;
import java.util.Random;

public class SquareDiamond {
	
	public static boolean SqrDmd(float[] map, int mapSize, float roughness, long seed) {
		int mapArea = (int)Math.pow(mapSize,  2.0f);
		if (mapArea > map.length) throw new IllegalArgumentException("mapSize must be the side length.");
		if (((mapSize - 1) & (mapSize - 2)) != 0) throw new IllegalArgumentException("mapSize must equal 2^x+1.");

		DiamondSquare(map, mapSize, new Random(seed), 0, 0, mapSize, mapSize, roughness, mapSize & ~1);
		return true;
	}
	
	public static void DiamondSquare(float[] map, int mapSize, Random rand, int x1, int y1, int x2, int y2, float range, int level) {
	    if (level < 1) return;

	    // diamonds
	    for (int i = x1 + level; i < x2; i += level) {
	        for (int j = y1 + level; j < y2; j += level) {
	            float avg = (map[G.getTile(i - level,  j - level, mapSize)] +
	            			map[G.getTile(i, j - level, mapSize)] +
	            			map[G.getTile(i - level, j, mapSize)] +
	            			map[G.getTile(i, j, mapSize)]) * 0.25f;
	            float e = map[G.getTile(i - level / 2, j - level / 2, mapSize)] = avg + rand.nextFloat() * range;
	        }
	    }

	    // squares
	    for (int i = x1 + 2 * level; i < x2; i += level) {
	        for (int j = y1 + 2 * level; j < y2; j += level) {
	            float a = map[G.getTile(i - level,  j - level, mapSize)];
	            float b = map[G.getTile(i, j - level, mapSize)];
	            float c = map[G.getTile(i - level, j, mapSize)];
	            float d = map[G.getTile(i, j, mapSize)];
	            float e = map[G.getTile(i - level / 2, j - level / 2, mapSize)];

	        	
	            float f = map[G.getTile(i - level, j - level / 2, mapSize)] = 
	            		(a + c + e + map[G.getTile(i - 3 * level / 2, j - level / 2, mapSize)]) / 4 + rand.nextFloat() * range;
	            float g = map[G.getTile(i - level / 2, j - level, mapSize)] = 
	            		(a + b + e + map[G.getTile(i - level / 2, j - 3 * level / 2, mapSize)]) / 4 + rand.nextFloat() * range;
	        }
	    }

	    DiamondSquare(map, mapSize, rand, x1, y1, x2, y2, range / 2, level / 2);
	}	
}
