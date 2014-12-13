package com.tepidpond.tum.PlateTectonics;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import scala.Array;

public class Lithosphere {
	private static final float SQRDMD_ROUGHNESS = 0.5f;
	private static final float CONTINENTAL_BASE = 1.0f;
	private static final float OCEANIC_BASE =     0.1f;
	
	private float heightMap[];	// denotes height of terrain of tiles
	private float indexMap[];	// denotes plate ownership of tiles
	private int mapArea;
	private int mapSize;
	private int numPlates;
	private Random rand;
	
	// default mapSize = 512. Must be power of 2.
	public Lithosphere(int mapSize, float percentSeaTiles, int erosion_period, float folding_ratio,
			int aggr_ratio_abs, float aggr_ratio_rel, int num_cycles, int _numPlates, long seed) {
		
		this.mapArea = (int) Math.pow(mapSize + 1, 2);
		this.mapSize = mapSize;
		if (_numPlates > mapArea)
			numPlates = mapArea;
		else
			numPlates = _numPlates;
		this.rand = new Random();
		rand.setSeed(seed);
		
		float tmpHeightMap[] = new float[mapArea];
		
		// Generate initial fractal map
		if (!SquareDiamond.SqrDmd(tmpHeightMap, mapSize + 1, SQRDMD_ROUGHNESS)) {
			// Error unable to generate height map.
		}
		
		scaleHeightMap(tmpHeightMap, 0.0f, 1.0f);
		float seaLevel = getSeaLevel(tmpHeightMap, percentSeaTiles, 5);
		separateLandAndSea(tmpHeightMap, seaLevel);
		
		this.heightMap = new float[(int)Math.pow(mapSize, 2)];
		this.indexMap = new float[(int)Math.pow(mapSize, 2)];
		Arrays.fill(indexMap, numPlates + 1);
		
		for (int i = 0; i < mapSize; i++)
			System.arraycopy(tmpHeightMap, i * (mapSize + 1), this.heightMap, 0, mapSize);
		
		PlateArea[] plates = createPlates();
		growPlates(plates);
	}
	
	private PlateArea[] createPlates() {
		ArrayList<Integer> plateCenters = new ArrayList<Integer>();
		int mapTile;
		for (int i = 0; i < numPlates; i++) {
			do {
				mapTile = rand.nextInt(mapArea);
			} while (!plateCenters.contains(mapTile));
			plateCenters.add(mapTile);
		}
		PlateArea[] plates = new PlateArea[numPlates];
		for (int i = 0; i < plateCenters.size(); i++) {
			mapTile = plateCenters.get(i);
			int x = mapTile - (mapTile % mapSize);
			int y = mapTile / mapSize;
			plates[i] = new PlateArea(x, y, x, y);
			plates[i].border.addElement(mapTile);
		}
		
		return plates;
	}
	
	private void growPlates(PlateArea[] plates) {
		Arrays.fill(indexMap, numPlates + 1);	// initialize terrain-ownership map
		
		int maxBorder = 1;
		int i;
		while (maxBorder > 0) {
			for (maxBorder = i = 0; i < numPlates; i++) {
				int N = plates[i].border.size();
				if (maxBorder < N) maxBorder = N;
				if (N == 0) continue;
				
				int j = rand.nextInt(N);
				int mapTile = plates[i].border.get(j);	// choose random location on plate
				
				int x = mapTile - (mapTile % mapSize);
				int y = mapTile / mapSize;
				int lft, rgt, top, btm;
				int tileN, tileS, tileW, tileE;
				if (x > 0) lft = x-1; else lft = mapSize-1;
				if (x < mapSize - 1) rgt = x+1; else rgt = 0;
				if (y > 0) top = y-1; else top = mapSize-1;
				if (y < mapSize - 1) btm = y+1; else btm = 0;
				
				tileN = top * mapSize + x;
				tileS = btm * mapSize + x;
				tileW = y * mapSize + lft;
				tileE = y * mapSize + rgt;
			}
		}
	}
	
	private static void separateLandAndSea(float heightMap[], float seaLevel) {
		for (int i = 0; i < heightMap.length; i++) {
			if (heightMap[i] > seaLevel)
				heightMap[i] += CONTINENTAL_BASE;
			else
				heightMap[i] += OCEANIC_BASE;			
		}
	}
	
	// force values in the heightMap into a specified range.
	private static void scaleHeightMap(float heightMap[], float min, float max) {
		int mapArea = heightMap.length;
		float minHeight = heightMap[0], maxHeight = heightMap[0];
		for (int i = 1; i < mapArea; i++) {
			if (heightMap[i] < minHeight) minHeight = heightMap[i];
			if (heightMap[i] > maxHeight) maxHeight = heightMap[i];
		}
		
		float scaleFactor = maxHeight - minHeight;
		if (min != 0.0f) minHeight -= min;
		if (min != 0.0f || max != 1.0f) scaleFactor /= (max - min);

		for (int i = 0; i < mapArea; i++) {
			heightMap[i] = (heightMap[i] - minHeight) / scaleFactor;
		}
	}
	
	// Calculate height of sea giving desired sea/continent ratio
	private static float getSeaLevel(float heightMap[], float percentSeaTiles, int maxAttempts) {
		if (percentSeaTiles >= 1.0f) return 1.0f;	// all sea
		if (percentSeaTiles <= 0.0f) return 0.0f;	// all land
		
		int mapArea = (int)Math.pow(heightMap.length, 2.0f);
		float seaThreshold = 0.5f;				// start middle
		int maxLandTiles = (int)(mapArea * (1.0f - percentSeaTiles));
		int maxSeaTiles = (int)(mapArea * percentSeaTiles);
		for(int i = 1; i <= maxAttempts; i++) {
			int landTiles = 0, seaTiles = 0;
			for (int j = 0; j < mapArea; j++) {
				if (heightMap[j] > seaThreshold)
					landTiles++;
				else
					seaTiles++;
				// Bail out early if we count too many of either type
				if (landTiles > maxLandTiles || seaTiles > maxSeaTiles) break;
			}
			if (seaTiles > maxSeaTiles) {
				seaThreshold -= Math.pow(0.25f, (float)i);
			} else {
				seaThreshold += Math.pow(0.25f, (float)i);
			}
		}
		
		return seaThreshold;
	}
}
