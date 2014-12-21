package com.tepidpond.tum.PlateTectonics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Lithosphere {
	private static final float SQRDMD_ROUGHNESS = 0.5f;
	private static final float CONTINENTAL_BASE = 1.0f;
	private static final float OCEANIC_BASE =     0.1f;
	private static final float RESTART_ENERGY_RATIO = 0.15f;
	private static final float RESTART_SPEED_LIMIT = 2.0f;
	private static final int NO_COLLISION_TIME_LIMIT = 10;
	private static final int MAX_GENERATIONS = 600;
	
	private float heightMap[];  // denotes height of terrain of tiles
	private float indexMap[];   // denotes plate ownership of tiles. Is a float because of saveHeightmap.
	private Plate plates[];
	private Stack<CollisionDetails> subductions[];
	private Stack<CollisionDetails> collisions[];
	
	private int mapArea;
	private int mapSize;
	private int numPlates;
	private Random rand;
	
	private float peakKineticEnergy = 0.0f;
	private int lastCollisionCount = 0;
	private int generations = 0;
	private int erosionPeriod = 1;
	private float foldingRatio = 0.5f;
	
	public float[] getHeightmap() {
		return heightMap;
	}
	
	public int getMapSize() {
		return mapSize;
	}
	
	// default mapSize = 512. Must be power of 2.
	public Lithosphere(int mapSize, float percentSeaTiles, int erosion_period, float folding_ratio,
			int aggr_ratio_abs, float aggr_ratio_rel, int num_cycles, int _numPlates, long seed) {

		subductions = (Stack<CollisionDetails>[]) new Object[numPlates];
		collisions = (Stack<CollisionDetails>[]) new Object[numPlates];

		this.foldingRatio = folding_ratio;
		this.erosionPeriod = erosion_period;
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
		if (!SquareDiamond.SqrDmd(tmpHeightMap, mapSize + 1, 1.0f, SQRDMD_ROUGHNESS, seed)) {
			// Error unable to generate height map.
		}
		
		Util.normalizeHeightMap(tmpHeightMap);
		float seaLevel = getSeaLevel(tmpHeightMap, percentSeaTiles, 5);
		separateLandAndSea(tmpHeightMap, seaLevel);
		
		this.heightMap = new float[(int)Math.pow(mapSize, 2)];
		this.indexMap = new float[(int)Math.pow(mapSize, 2)];
		Arrays.fill(indexMap, numPlates + 1);
		
		for (int i = 0; i < mapSize; i++)
			System.arraycopy(tmpHeightMap, i * (mapSize + 1), this.heightMap, i * mapSize, mapSize);
		Util.saveHeightmap(this.heightMap, mapSize, "scalped");
		
		PlateArea[] plates = createPlates();
		growPlates(plates);
		Util.saveHeightmap(this.indexMap, mapSize, "plates");
		this.plates = extractPlates(plates);
	}
	
	public boolean Update() {
		if (checkForStaticWorld()) return false;
		
		moveAndErodePlates();
		int oceanicCollisions = 0, continentalCollisions = 0;
		Arrays.fill(heightMap, 0);
		Arrays.fill(indexMap, 255);
		int ageMap[] = new int[mapArea];
		
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			Plate p = plates[activePlate];
			int X0 = p.getLeft();
			int X1 = p.getLeft() + p.getWidth();
			int Y0 = p.getTop();
			int Y1 = p.getTop() + p.getHeight();
			float[] hmPlate = p.getHeightmap();			
			int[] agePlate = p.getTimestampMap();
			
			for (int y = Y0; y < Y1; y++) for (int x = X0; x < X1; x++) {
				int collisionFlag = collectCollisions(ageMap, activePlate, x, y);
				if ((collisionFlag & 1) != 0) continentalCollisions++;
				if ((collisionFlag & 2) != 0) oceanicCollisions++;
			}
		}
		
		if (continentalCollisions == 0) lastCollisionCount++; else lastCollisionCount = 0;
		
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			for (CollisionDetails cd: subductions[activePlate]) {
				plates[activePlate].addCrustBySubduction(
						cd.getX(), cd.getY(), cd.getCrust(), generations,
						plates[cd.getIndex()].getVelocityX(), plates[cd.getIndex()].getVelocityY());
			}
			subductions[activePlate].clear();
		}
		
		Util.saveHeightmap(heightMap, mapSize, "upd" + Integer.toString(generations++));
		return true;
	}
	
	private void moveAndErodePlates() {
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			plates[activePlate].resetSegments();	// reset collision segments
			if (erosionPeriod > 0 && generations % erosionPeriod == 0)
				plates[activePlate].erode(CONTINENTAL_BASE);
			
			plates[activePlate].move();
		}
	}
	
	private boolean checkForStaticWorld() {
		float totalVelocity = 0;
		float totalKineticEnergy = 0;
		
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			totalVelocity += plates[activePlate].getVelocity();
			totalKineticEnergy += plates[activePlate].getMomentum();
		}
		if (totalKineticEnergy > peakKineticEnergy)
			peakKineticEnergy = totalKineticEnergy;
		if (totalVelocity < RESTART_SPEED_LIMIT ||
			totalKineticEnergy / peakKineticEnergy < RESTART_ENERGY_RATIO ||
			lastCollisionCount > NO_COLLISION_TIME_LIMIT ||
			generations > MAX_GENERATIONS)
			return true;
		
		return false;
	}
	
	private int collectCollisions(int[] ageMap, int activePlate, int x, int y) {
		int collisionFlags = 0;
		Plate p = plates[activePlate];
		float hmPlate[] = p.getHeightmap();
		int agePlate[] = p.getTimestampMap();
		
		int plateTile = Util.getTile(x - p.getLeft(), y - p.getTop(), p.getWidth());
		int mapTile = Util.getTile(x, y, mapSize);
		
		// No crust at location.
		if (hmPlate[plateTile] < (2 * Float.MIN_NORMAL))
			return collisionFlags;
		
		if (indexMap[mapTile] >= numPlates) {
			heightMap[mapTile] = hmPlate[plateTile];
			indexMap[mapTile] = activePlate;
			ageMap[mapTile] = agePlate[plateTile];
			
			return collisionFlags;
		}
		
		// DO NOT ACCEPT HEIGHT EQUALITY! Equality leads to subduction
		// of shore that 's barely above sea level. It's a lot less
		// serious problem to treat very shallow waters as continent...
		boolean prev_is_oceanic = heightMap[mapTile] < CONTINENTAL_BASE;
		boolean this_is_oceanic = hmPlate[plateTile] < CONTINENTAL_BASE;

		int prev_timestamp = plates[(int) indexMap[mapTile]].getCrustTimestamp(x, y);
		int this_timestamp = ageMap[mapTile];
		boolean prev_is_bouyant = (heightMap[mapTile] > hmPlate[plateTile]) |
			(heightMap[mapTile] + 2 * Float.MIN_NORMAL > hmPlate[plateTile]) &
			 (heightMap[mapTile] < 2 * Float.MIN_NORMAL + hmPlate[plateTile]) &
			 (prev_timestamp >= this_timestamp);

		// Handle subduction of oceanic crust as special case.
		if (this_is_oceanic & prev_is_bouyant)
		{
			// This plate will be the subducting one.
			// The level of effect that subduction has
			// is directly related to the amount of water
			// on top of the subducting plate.
			float sediment = OCEANIC_BASE * (CONTINENTAL_BASE - hmPlate[plateTile]) / CONTINENTAL_BASE;

			// Save collision to the receiving plate's list.
			subductions[(int) indexMap[mapTile]].Push(
					new CollisionDetails(activePlate, x, y, sediment));
			collisionFlags |= 2;	// oceanic

			// Remove subducted oceanic lithosphere from plate.
			// This is crucial for
			// a) having correct amount of colliding crust (below)
			// b) protecting subducted locations from receiving
			//    crust from other subductions/collisions.
			plates[activePlate].setCrust(x, y, hmPlate[plateTile] - OCEANIC_BASE, this_timestamp);

			if (hmPlate[plateTile] <= 0)
				return collisionFlags; // Nothing more to collide.
		} else if (prev_is_oceanic) {
			float sediment = OCEANIC_BASE * (CONTINENTAL_BASE - heightMap[mapTile]) / CONTINENTAL_BASE;

			subductions[(int) indexMap[mapTile]].Push(
					new CollisionDetails(activePlate, x, y, sediment));
			collisionFlags |= 2;	// oceanic

			plates[activePlate].setCrust(x, y, heightMap[mapTile] - OCEANIC_BASE, this_timestamp);

			heightMap[mapTile] -= OCEANIC_BASE;

			if (heightMap[mapTile] <= 0)
			{
				indexMap[mapTile] = activePlate;
				heightMap[mapTile] = hmPlate[plateTile];
				ageMap[mapTile] = agePlate[plateTile];

				return collisionFlags;
			}
		}
		
		// Record collisions to both plates. This also creates
		// continent segment at the collided location to plates.
		int this_area = plates[activePlate].addCollision(x, y);
		int prev_area = plates[(int) indexMap[mapTile]].addCollision(x, y);
		
		if (this_area < prev_area) {
			CollisionDetails cd = new CollisionDetails((int) indexMap[mapTile], x, y, hmPlate[plateTile] * foldingRatio);

				// Give some...
			heightMap[mapTile] += cd.getCrust();
			plates[(int) indexMap[mapTile]].setCrust(x, y, heightMap[mapTile], agePlate[plateTile]);

			// And take some.
			plates[activePlate].setCrust(x, y, hmPlate[plateTile] * (1.0f - foldingRatio), agePlate[plateTile]);

			// Add collision to the earlier plate's list.
			collisions[activePlate].Push(cd);
			collisionFlags |= 1;	// continental
		} else {
			CollisionDetails cd = new CollisionDetails(activePlate, x, y, heightMap[mapTile] * foldingRatio);
			plates[activePlate].setCrust(x, y, hmPlate[plateTile] + cd.getCrust(), ageMap[mapTile]);
			plates[(int) indexMap[mapTile]].setCrust(x, y, heightMap[mapTile] * (1.0f - foldingRatio), ageMap[mapTile]);
			collisions[(int) indexMap[mapTile]].Push(cd);
			collisionFlags |= 1;	// continental

			// Give the location to the larger plate.
			heightMap[mapTile] = hmPlate[plateTile];
			heightMap[mapTile] = activePlate;
			ageMap[mapTile] = agePlate[plateTile];
		}
		return collisionFlags;
	}
	
	private PlateArea[] createPlates() {
		ArrayList<Integer> plateCenters = new ArrayList<Integer>();
		int mapTile;
		for (int i = 0; i < numPlates; i++) {
			do {
				mapTile = rand.nextInt(mapArea);
			} while (plateCenters.contains(mapTile));
			plateCenters.add(mapTile);
		}
		PlateArea[] plates = new PlateArea[numPlates];
		for (int i = 0; i < plateCenters.size(); i++) {
			plates[i] = new PlateArea(plateCenters.get(i), mapSize);
		}
		
		return plates;
	}
	
	private void growPlates(PlateArea[] plates) {
		Arrays.fill(indexMap, numPlates * 2);	// initialize terrain-ownership map
		
		int maxBorder = 1;
		while (maxBorder > 0) {
			maxBorder = 0;
			for (int activePlate = 0; activePlate < numPlates; activePlate++) {
				if (plates[activePlate].borderSize()== 0)
					continue;	// the plate has grown as far as possible in all directions.

				maxBorder = Math.max(maxBorder, plates[activePlate].borderSize());
				
				// choose random location on plate 
				int plateBorderElement = rand.nextInt(plates[activePlate].borderSize());
				int mapTile = plates[activePlate].getBorder(plateBorderElement);
				
				int x = Util.getX(mapTile, mapSize);
				int y = Util.getY(mapTile, mapSize);
				
				// in the 4 cardinal directions, clamp at border.
				int tileN, tileS, tileW, tileE;
				tileN = Util.getTile(x, Math.max(y - 1, 0), mapSize);
				tileS = Util.getTile(x, Math.min(y + 1, mapSize - 1), mapSize);
				tileW = Util.getTile(Math.max(x - 1, 0), y, mapSize);
				tileE = Util.getTile(Math.min(x + 1, mapSize - 1), y, mapSize);
				
				// If the N/S/E/W tile is un-owned, claim it for the active plate
				// and add it to that plate's border.
				if (indexMap[tileN] >= numPlates) {
					indexMap[tileN] = activePlate;
					plates[activePlate].pushBorder(tileN);
				}
				if (indexMap[tileS] >= numPlates) {
					indexMap[tileS] = activePlate;
					plates[activePlate].pushBorder(tileS);
				}
				if (indexMap[tileW] >= numPlates) {
					indexMap[tileW] = activePlate;
					plates[activePlate].pushBorder(tileW);
				}
				if (indexMap[tileE] >= numPlates) {
					indexMap[tileE] = activePlate;
					plates[activePlate].pushBorder(tileE);
				}
				
				// Overwrite processed point in border with last item from border
				plates[activePlate].setBorder(plateBorderElement, plates[activePlate].lastBorder());
				plates[activePlate].popBorder();
			}
		}
	}
	
	private Plate[] extractPlates(PlateArea[] plateAreas) {
		Plate[] plates = new Plate[numPlates];
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			int x0 = plateAreas[activePlate].x0;
			int x1 = plateAreas[activePlate].x1;
			int y0 = plateAreas[activePlate].y0;
			int y1 = plateAreas[activePlate].y1;
			float[] plateHM = new float[(x1 - x0) * (y1 - y0)];
			int i = 0;
			for (int y = plateAreas[activePlate].y0; y < plateAreas[activePlate].y1; y++) {
				for (int x = plateAreas[activePlate].x0; x < plateAreas[activePlate].x1; x++) {
					int k = Util.getTile(x, y, mapSize);
					if (indexMap[k] == activePlate) {
						plateHM[i++] = heightMap[k];
					} else {
						plateHM[i++] = 0;
					}
				}
			}
			
			Util.saveHeightmap(plateHM, x1 - x0, y1 - y0, "plate" + Integer.toString(activePlate));
			plates[activePlate] = new Plate(plateHM, x1 - x0, x0, y0, activePlate, mapSize, rand);
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
	
	// Calculate height of sea giving desired sea/continent ratio
	private static float getSeaLevel(float heightMap[], float percentSeaTiles, int maxIterations) {
		if (percentSeaTiles >= 1.0f) return 1.0f;	// all sea
		if (percentSeaTiles <= 0.0f) return 0.0f;	// all land
		
		int mapArea = heightMap.length;
		float seaThreshold = 0.5f;				// start middle
		int maxLandTiles = (int)(mapArea * (1.0f - percentSeaTiles));
		int maxSeaTiles = (int)(mapArea * percentSeaTiles);
		for(int i = 1; i <= maxIterations; i++) {
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
