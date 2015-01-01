package com.tepidpond.tum.PlateTectonics;

import java.lang.reflect.Array;
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
	private static final boolean REGENERATE_CRUST = true;
	private static final float BUOYANCY_BONUS = 3.0f;
	private static final int MAX_BUOYANCY_AGE = 20;
	
	private float worldMap[];  // denotes height of terrain of tiles
	private float worldPlates[];   // denotes plate ownership of tiles. Is a float because of saveHeightmap.
	private Plate plates[];
	private Stack<CollisionDetails> subductions[];
	private Stack<CollisionDetails> collisions[];
	
	private int worldSurface;
	private int worldSize;
	private int numPlates;
	private Random rand;
	
	private float peakKineticEnergy = 0.0f;
	private int generationsSinceCollision = 0;
	private int generations = 0;
	private int erosionPeriod = 1;
	private float foldingRatio = 0.5f;
	private float aggr_ratio_abs = 0.5f;
	private float aggr_ratio_rel = 0.5f;
	
	public float[] getHeightmap() { return worldMap; }
	
	public int getMapSize() { return worldSize; }
	
	// default mapSize = 512. Must be power of 2.
	public Lithosphere(int mapSize, float percentSeaTiles, int erosion_period, float folding_ratio,
			int aggr_ratio_abs, float aggr_ratio_rel, int num_cycles, int _numPlates, long seed) {


		this.aggr_ratio_abs = aggr_ratio_abs;
		this.aggr_ratio_rel = aggr_ratio_rel;
		this.foldingRatio = folding_ratio;
		this.erosionPeriod = erosion_period;
		this.worldSurface = (int) Math.pow(mapSize, 2);
		this.worldSize = mapSize;
		if (_numPlates > worldSurface)
			numPlates = worldSurface;
		else
			numPlates = _numPlates;
		this.rand = new Random();
		rand.setSeed(seed);
		
		// Setup for collision storage for update. This is complex because
		// "Cannot create generic array of ..." error otherwise.
		subductions = (Stack<CollisionDetails>[]) new Stack[numPlates];	
		collisions = (Stack<CollisionDetails>[]) new Stack[numPlates];
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			subductions[activePlate] = new Stack<CollisionDetails>();
			collisions[activePlate] = new Stack<CollisionDetails>();
		}

		float tmpWorldMap[] = new float[(int) Math.pow(mapSize + 1, 2)];
		
		// Generate initial fractal map
		if (!SquareDiamond.SqrDmd(tmpWorldMap, mapSize + 1, 1.0f, SQRDMD_ROUGHNESS, seed)) {
			// Error unable to generate height map.
		}
		
		Util.normalizeHeightMap(tmpWorldMap);
		float seaLevel = getSeaLevel(tmpWorldMap, percentSeaTiles, 5);
		separateLandAndSea(tmpWorldMap, seaLevel);
		
		this.worldMap = new float[(int)Math.pow(mapSize, 2)];
		this.worldPlates = new float[(int)Math.pow(mapSize, 2)];
		Arrays.fill(worldPlates, numPlates);
		
		for (int i = 0; i < mapSize; i++)
			System.arraycopy(tmpWorldMap, i * (mapSize + 1), this.worldMap, i * mapSize, mapSize);
		
		PlateArea[] plates = createPlates();
		growPlates(plates);
		this.plates = extractPlates(plates);
		// Prevent any initial buoyancy from altering first heightmap.
		generations = numPlates + MAX_BUOYANCY_AGE;
		generationsSinceCollision = 0;
	}
	
	public boolean Update() {
		if (checkForStaticWorld()) return false;

		//Util.saveHeightmap(indexMap, mapSize, "p" + Integer.toString(generations));
		Util.saveHeightmap(worldMap, worldSize, "t" + Integer.toString(generations));
		
		moveAndErodePlates();
		int continentalCollisions = 0;
		float prevIndexMap[] = new float[worldPlates.length];
		System.arraycopy(worldPlates, 0, prevIndexMap, 0, worldPlates.length);
		Arrays.fill(worldMap, 0);
		Arrays.fill(worldPlates, Integer.MAX_VALUE);
		int ageMap[] = new int[worldSurface];
		
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			Plate p = plates[activePlate];
			int X0 = p.getLeft();
			int Y0 = p.getTop();
			int X1 = X0 + p.getWidth();
			int Y1 = Y0 + p.getHeight();
			float[] plateMap = p.getHeightmap();			
			int[] plateAge = p.getTimestampMap();
			
			for (int y = Y0, plateTile = 0; y < Y1; y++) for (int x = X0; x < X1; x++, plateTile++) {
				int xMod = x % worldSize, yMod = y % worldSize;
				int worldTile = yMod * worldSize + xMod;
				
				// Does this plate have crust here?
				if (plateMap[plateTile] > 2 * Util.FLT_EPSILON) {
					if (worldPlates[worldTile] >= numPlates) {	// No one here yet?
						// activePlate becomes the owner of this tile if it's the first
						// plate to have crust on it.
						worldMap[worldTile] = plateMap[plateTile];
						worldPlates[worldTile] = activePlate;
						ageMap[worldTile] = plateAge[plateTile];
					} else {					
						continentalCollisions += collectCollisions(ageMap, activePlate, xMod, yMod, plateTile, worldTile);
					}
				}
			} // for y... { for x ...
		}
		
		if (continentalCollisions == 0) generationsSinceCollision++; else generationsSinceCollision = 0;
		processSubductions();
		processCollisions();
		regenerateCrust(prevIndexMap, ageMap);

		addSeaFloorUplift(ageMap);
		
		generations++;
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
			generationsSinceCollision > NO_COLLISION_TIME_LIMIT ||
			generations > MAX_GENERATIONS)
			return true;
		
		return false;
	}
	
	private int collectCollisions(int[] ageMap, int activePlate, int worldX, int worldY, int plateTile, int worldTile) {
		Plate p = plates[activePlate];
		float plateMap[] = p.getHeightmap();
		int plateAge[] = p.getTimestampMap();
		
		// DO NOT ACCEPT HEIGHT EQUALITY! Equality leads to subduction
		// of shore that 's barely above sea level. It's a lot less
		// serious problem to treat very shallow waters as continent...
		boolean prev_is_oceanic = worldMap[worldTile] < CONTINENTAL_BASE;
		boolean this_is_oceanic = plateMap[plateTile] < CONTINENTAL_BASE;

		int prev_timestamp = plates[(int) worldPlates[worldTile]].getCrustTimestamp(worldX, worldY);
		int this_timestamp = plateAge[plateTile];
		boolean prev_is_bouyant = (worldMap[worldTile] > plateMap[plateTile]) |
			(Math.abs(worldMap[worldTile] - plateMap[plateTile]) < 2 * Util.FLT_EPSILON &
			 prev_timestamp >= this_timestamp);

		// Handle subduction of oceanic crust as special case.
		if (this_is_oceanic & prev_is_bouyant)
		{
			// This plate will be the subducting one.
			// The level of effect that subduction has
			// is directly related to the amount of water
			// on top of the subducting plate.
			float sediment = OCEANIC_BASE * (CONTINENTAL_BASE - plateMap[plateTile]) / CONTINENTAL_BASE;

			// Save collision to the receiving plate's list.
			subductions[(int) worldPlates[worldTile]].Push(
					new CollisionDetails(activePlate, worldX, worldY, sediment));

			// Remove subducted oceanic lithosphere from plate.
			// This is crucial for
			// a) having correct amount of colliding crust (below)
			// b) protecting subducted locations from receiving
			//    crust from other subductions/collisions.
			plates[activePlate].setCrust(worldX, worldY, plateMap[plateTile] - OCEANIC_BASE, this_timestamp);

			if (plateMap[plateTile] <= 0)
				return 0;
		} else if (prev_is_oceanic) {
			float sediment = OCEANIC_BASE * (CONTINENTAL_BASE - worldMap[worldTile]) / CONTINENTAL_BASE;

			subductions[(int) worldPlates[worldTile]].Push(
					new CollisionDetails(activePlate, worldX, worldY, sediment));

			plates[activePlate].setCrust(worldX, worldY, worldMap[worldTile] - OCEANIC_BASE, this_timestamp);

			worldMap[worldTile] -= OCEANIC_BASE;

			if (worldMap[worldTile] <= 0)
			{
				worldPlates[worldTile] = activePlate;
				assert(!Float.isNaN(plateMap[plateTile]));
				worldMap[worldTile] = plateMap[plateTile];
				ageMap[worldTile] = plateAge[plateTile];

				return 0;
			}
		}
		
		// Record collisions to both plates. This also creates
		// continent segment at the collided location to plates.
		int this_area = plates[activePlate].addCollision(worldX, worldY);
		int prev_area = plates[(int) worldPlates[worldTile]].addCollision(worldX, worldY);
		
		if (this_area < prev_area) {
			CollisionDetails cd = new CollisionDetails((int) worldPlates[worldTile], worldX, worldY, plateMap[plateTile] * foldingRatio);

				// Give some...
			worldMap[worldTile] += cd.getCrust();
			assert(!Float.isNaN(worldMap[worldTile]));
			plates[(int) worldPlates[worldTile]].setCrust(worldX, worldY, worldMap[worldTile], plateAge[plateTile]);

			// And take some.
			plates[activePlate].setCrust(worldX, worldY, plateMap[plateTile] * (1.0f - foldingRatio), plateAge[plateTile]);

			// Add collision to the earlier plate's list.
			collisions[activePlate].Push(cd);
		} else {
			CollisionDetails cd = new CollisionDetails(activePlate, worldX, worldY, worldMap[worldTile] * foldingRatio);
			plates[activePlate].setCrust(worldX, worldY, plateMap[plateTile] + cd.getCrust(), ageMap[worldTile]);
			plates[(int) worldPlates[worldTile]].setCrust(worldX, worldY, worldMap[worldTile] * (1.0f - foldingRatio), ageMap[worldTile]);
			collisions[(int) worldPlates[worldTile]].Push(cd);

			// Give the location to the larger plate.
			assert(!Float.isNaN(plateMap[plateTile]));
			worldMap[worldTile] = plateMap[plateTile];
			worldMap[worldTile] = activePlate;
			ageMap[worldTile] = plateAge[plateTile];
		}
		return 1;
	}
	
	private void processSubductions() {
		// Process all subductions
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			for (CollisionDetails cd: subductions[activePlate]) {
				plates[activePlate].addCrustBySubduction(
					cd.getX(), cd.getY(), cd.getCrust(), generations,
					plates[cd.getIndex()].getVelocityX(), plates[cd.getIndex()].getVelocityY());
			}
			subductions[activePlate].clear();
		}
	}
	
	private void processCollisions() {
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			for (CollisionDetails cd: collisions[activePlate]) {
				plates[activePlate].applyFriction(cd.getCrust());
				plates[cd.getIndex()].applyFriction(cd.getCrust());
				
				CollisionStatistic csPlateA = plates[activePlate].getCollisionInfo(cd.getX(), cd.getY());
				CollisionStatistic csPlateB = plates[cd.getIndex()].getCollisionInfo(cd.getX(), cd.getY());
				int collisionCount = Math.min(csPlateA.Collisions, csPlateB.Collisions);
				float collisionRatio = Math.max(csPlateA.CollidedRatio, csPlateB.CollidedRatio);
				
				if ((collisionCount > aggr_ratio_abs) | (collisionRatio > aggr_ratio_rel)) {
					plates[cd.getIndex()].collide(plates[activePlate], cd.getX(), cd.getY(), 
						plates[activePlate].aggregateCrust(plates[cd.getIndex()], cd.getX(), cd.getY()));
				}
			}
			collisions[activePlate].clear();
		}
	}
	
	private void regenerateCrust(float[] prevIndexMap, int[] ageMap) {
		if (REGENERATE_CRUST) {
			for (int y = 0; y < worldSize; y++) {
				for (int x = 0; x < worldSize; x++) {
					int worldTile = Util.getTile(x, y, worldSize);
					if (worldPlates[worldTile] >= numPlates) {
						worldPlates[worldTile] = prevIndexMap[worldTile];
						ageMap[worldTile] = generations;
						worldMap[worldTile] = OCEANIC_BASE * BUOYANCY_BONUS;
						assert(!Float.isNaN(worldMap[worldTile]));
						plates[(int) worldPlates[worldTile]].setCrust(x, y, OCEANIC_BASE, generations);
					}
				}
			}
		}
	}
	
	/**
	 * Adds some "virginity buoyancy" to all pixels for a visual boost.
	 * @param ageMap
	 */
	private void addSeaFloorUplift(int[] ageMap) {
		if (BUOYANCY_BONUS > 0) {
			for (int worldTile = 0; worldTile < worldMap.length; worldTile++) {
				int crustAge = MAX_BUOYANCY_AGE - (generations - ageMap[worldTile]);
				// If it has been not more than MAX_BUOYANCY_AGE generations since the sea floor
				// was created from magma, increase the height by a decreasing amount.
				if (crustAge <= MAX_BUOYANCY_AGE && worldMap[worldTile] < CONTINENTAL_BASE)
					worldMap[worldTile] += crustAge * BUOYANCY_BONUS * OCEANIC_BASE * (1.0f / MAX_BUOYANCY_AGE); 
			}
		}
	}
	
	private PlateArea[] createPlates() {
		ArrayList<Integer> plateCenters = new ArrayList<Integer>();
		int worldTile;
		for (int i = 0; i < numPlates; i++) {
			do {
				worldTile = rand.nextInt(worldSurface);
			} while (plateCenters.contains(worldTile));
			plateCenters.add(worldTile);
		}
		PlateArea[] plates = new PlateArea[numPlates];
		for (int i = 0; i < plateCenters.size(); i++) {
			plates[i] = new PlateArea(plateCenters.get(i), worldSize);
		}
		
		return plates;
	}
	
	private void growPlates(PlateArea[] plates) {
		Arrays.fill(worldPlates, numPlates);	// initialize terrain-ownership map
		
		int maxBorder = 1;
		int iterations = 0;
		while (maxBorder > 0) {
			maxBorder = 0;
			for (int activePlate = 0; activePlate < numPlates; activePlate++) {
				if (plates[activePlate].borderSize()== 0)
					continue;	// the plate has grown as far as possible in all directions.

				maxBorder = Math.max(maxBorder, plates[activePlate].borderSize());
				
				// choose random location on plate 
				int plateBorderElement = rand.nextInt(plates[activePlate].borderSize());
				int worldTile = plates[activePlate].getBorder(plateBorderElement);
				
				int x = Util.getX(worldTile, worldSize);
				int y = Util.getY(worldTile, worldSize);
				
				// in the 4 cardinal directions, clamp at border.
				int tileN, tileS, tileW, tileE;
				tileN = Util.getTile(x, Math.max(y - 1, 0), worldSize);
				tileS = Util.getTile(x, Math.min(y + 1, worldSize - 1), worldSize);
				tileW = Util.getTile(Math.max(x - 1, 0), y, worldSize);
				tileE = Util.getTile(Math.min(x + 1, worldSize - 1), y, worldSize);
				
				// If the N/S/E/W tile is un-owned, claim it for the active plate
				// and add it to that plate's border.
				if (worldPlates[tileN] >= numPlates) {
					worldPlates[tileN] = activePlate;
					plates[activePlate].pushBorder(tileN);
				}
				if (worldPlates[tileS] >= numPlates) {
					worldPlates[tileS] = activePlate;
					plates[activePlate].pushBorder(tileS);
				}
				if (worldPlates[tileW] >= numPlates) {
					worldPlates[tileW] = activePlate;
					plates[activePlate].pushBorder(tileW);
				}
				if (worldPlates[tileE] >= numPlates) {
					worldPlates[tileE] = activePlate;
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
			int plateWdt = x1 - x0 + 1;
			int plateHgt = y1 - y0 + 1;

			float[] plateHM = new float[plateWdt * plateHgt];
			for (int localY = 0; localY < plateHgt; localY++) {
				for (int localX = 0; localX < plateWdt; localX++) {
					int worldTile = Util.getTile(localX + x0, localY + y0, worldSize);
					int plateTile = Util.getTile(localX, localY, plateWdt, plateHgt);
					if (worldPlates[worldTile] == activePlate) {
						assert(!Float.isNaN(worldMap[worldTile]));
						plateHM[plateTile] = worldMap[worldTile];
					} else {
						plateHM[plateTile] = 0;
					}
				}
			}
			
			plates[activePlate] = new Plate(plateHM, plateWdt, x0, y0, activePlate, worldSize, rand);
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
