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
	private Plate plates[];
	
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
		this.plates = extractPlates(plates);
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
				int xLeft, xRight, yTop, yBottom;
				int tileN, tileS, tileW, tileE;
				if (x > 0) xLeft = x-1; else xLeft = mapSize-1;
				if (x < mapSize - 1) xRight = x+1; else xRight = 0;
				if (y > 0) yTop = y-1; else yTop = mapSize-1;
				if (y < mapSize - 1) yBottom = y+1; else yBottom = 0;
				
				tileN = yTop * mapSize + x;
				tileS = yBottom * mapSize + x;
				tileW = y * mapSize + xLeft;
				tileE = y * mapSize + xRight;
				
				if (indexMap[tileN] >= numPlates) {
					indexMap[tileN] = i;
					plates[i].border.addElement(tileN);
					if (plates[i].top() == ((yTop + 1) & (mapSize - 1))) {
						plates[i].y0 = yTop;
					}
				}
				if (indexMap[tileS] >= numPlates) {
					indexMap[tileS] = i;
					plates[i].border.addElement(tileS);
					if (plates[i].btm() == ((yBottom + 1) & (mapSize - 1))) {
						plates[i].y1 = yBottom;
					}
				}
				if (indexMap[tileW] >= numPlates) {
					indexMap[tileW] = i;
					plates[i].border.addElement(tileW);
					if (plates[i].lft() == ((xLeft + 1) & (mapSize - 1))) {
						plates[i].x0 = xLeft;
					}
				}
				if (indexMap[tileE] >= numPlates) {
					indexMap[tileE] = i;
					plates[i].border.addElement(tileE);
					if (plates[i].rgt() == ((xRight + 1) & (mapSize - 1))) {
						plates[i].x1 = xRight;
					}
				}
				
				plates[i].border.setElementAt(plates[i].border.lastElement(), j);
				plates[i].border.remove(plates[i].border.size()-1);
			}
		}
	}
	
	private Plate[] extractPlates(PlateArea[] plateAreas) {
		Plate[] plates = new Plate[numPlates];
		for (int i = 0; i < numPlates; i++) {
			int x0 = plateAreas[i].lft();
			int x1 = 1 + x0 + plateAreas[i].wdt();
			int y0 = plateAreas[i].top();
			int y1 = 1 + y0 + plateAreas[i].hgt();
			int width = x1 - x0;
			int height = y1 - y0;
			float[] plateHM = new float[width * height];
			for (int y = y0, j = 0; y < y1; y++) {
				for (int x = x0; x < x1; x++, j++) {
					int k = (y & (mapSize - 1)) * mapSize + 
							(x & (mapSize - 1));
					if (indexMap[k] == i) {
						plateHM[j++] = heightMap[k];
					}
				}
			}
			
			plates[i] = new Plate(plateHM, width, height, x0, y0, i, mapSize);
		}		
		return plates;
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
