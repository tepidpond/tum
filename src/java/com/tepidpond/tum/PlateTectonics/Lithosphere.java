package com.tepidpond.tum.PlateTectonics;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Lithosphere {
	private static final float SQRDMD_ROUGHNESS = 0.5f;
	public static final float CONTINENTAL_BASE = 1.0f;
	private static final float OCEANIC_BASE =     0.1f;
	private static final float RESTART_ENERGY_RATIO = 0.15f;
	private static final float RESTART_SPEED_LIMIT = 2.0f;
	private static final int NO_COLLISION_TIME_LIMIT = 10;
	private static final int MAX_GENERATIONS = 600;
	private static final boolean REGENERATE_CRUST = true;
	private static final float BUOYANCY_BONUS = 3.0f;
	private static final int MAX_BUOYANCY_AGE = 20;
	
	private float worldMap[];  // denotes height of terrain of tiles
	private int worldPlates[];   // denotes plate ownership of tiles.
	private Plate plates[];
	private ArrayList<Stack<CollisionDetails>> subductions;
	private ArrayList<Stack<CollisionDetails>> collisions;
	
	private int worldSurface;
	private int worldSize;
	private int numPlates;
	private Random rand;
	
	private float peakKineticEnergy = 0.0f;
	private int generationsSinceCollision = 0;
	private int maxCycles = 0;	// unlimited
	private int numCycles = 0;	// number of times plate system has restarted
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

		this.maxCycles = num_cycles;
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
		subductions = new ArrayList<Stack<CollisionDetails>>(numPlates);	
		collisions = new ArrayList<Stack<CollisionDetails>>(numPlates);

		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			subductions.add(activePlate, new Stack<CollisionDetails>());
			collisions.add(activePlate, new Stack<CollisionDetails>());
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
		this.worldPlates = new int[(int)Math.pow(mapSize, 2)];
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
	
	public void Update() {
		if (checkForStaticWorld()) {
			restart();
			return;
		}

		moveAndErodePlates();
		int continentalCollisions = 0;
		int worldPlatesOld[] = new int[worldPlates.length];
		System.arraycopy(worldPlates, 0, worldPlatesOld, 0, worldPlates.length);
		Arrays.fill(worldMap, 0);
		Arrays.fill(worldPlates, Integer.MAX_VALUE);
		int worldAgeMap[] = new int[worldSurface];
		
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
						worldAgeMap[worldTile] = plateAge[plateTile];
					} else {					
						continentalCollisions += collectCollisions(worldAgeMap, activePlate, xMod, yMod, plateTile, worldTile);
					}
				}
			} // for y... { for x ...
		}
		
		if (continentalCollisions == 0) generationsSinceCollision++; else generationsSinceCollision = 0;
		processSubductions();
		processCollisions();
		regenerateCrust(worldPlatesOld, worldAgeMap);

		addSeaFloorUplift(worldAgeMap);
		
		generations++;
		
		Util.displayHeightmap(0, this.getHeightmap(), this.getMapSize(), this.getMapSize(), "Terrain");		
	}
	
	private void restart() {
		if (++numCycles <= maxCycles || maxCycles == 0) {
			generations = 0;
			// Copy plates to world map.
			int worldAge[] = new int[worldSurface];
			Arrays.fill(worldMap, 0);
			for (int activePlate = 0; activePlate < numPlates; activePlate++) {
				int x0 = plates[activePlate].getLeft(),
					y0 = plates[activePlate].getTop(),
					x1 = x0 + plates[activePlate].getWidth(),
					y1 = y0 + plates[activePlate].getHeight();
				float[] plateMap = plates[activePlate].getHeightmap();
				int[] plateAge = plates[activePlate].getTimestampMap();
				for (int y = y0, tile = 0; y < y1; y++) {
					for (int x = x0; x < x1; x++, tile++) {
						worldMap[(y % worldSize) * worldSize + x % worldSize] += plateMap[tile];
						worldAge[(y % worldSize) * worldSize + x % worldSize] = plateAge[tile];
					}
				}
			}
			
			// Create new plates if there are cycles remaining
			plates = new Plate[numPlates];
			if (numCycles < maxCycles || maxCycles == 0) {
				PlateArea[] plates = createPlates();
				growPlates(plates);
				this.plates = extractPlates(plates);
			} else {
				numPlates = 0;
				addSeaFloorUplift(worldAge);
				
				int noiseArea = (int) Math.pow(worldSize + 1, 2.0);
				float[] tmp = new float[noiseArea];
				Arrays.fill(tmp, 0);
				SquareDiamond.SqrDmd(tmp, worldSize + 1, 1.0f, SQRDMD_ROUGHNESS, rand.nextInt());
				Util.normalizeHeightMap(tmp);
				
				float[] tmp2 = new float[worldSurface];
				for (int i = 0; i < worldSize; i++)
					System.arraycopy(tmp, i * (worldSize + 1), tmp2, i * worldSize, worldSize);
				
				for (int i = 0; i < worldSurface; i++) {
					
					if (worldMap[i] > CONTINENTAL_BASE) {
						worldMap[i] += tmp2[i] * 2;
					} else {
						worldMap[i] = 0.8f * worldMap[i] + 0.2f * tmp2[i] * CONTINENTAL_BASE;
					}
				}
			}
		}
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
			totalVelocity += plates[activePlate].Velocity;
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
		boolean prev_is_bouyant = (worldMap[worldTile] > plateMap[plateTile]) ||
			(Math.abs(worldMap[worldTile] - plateMap[plateTile]) < 2 * Util.FLT_EPSILON &&
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
			subductions.get(worldPlates[worldTile]).Push(
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

			subductions.get(activePlate).Push(
					new CollisionDetails(worldPlates[worldTile], worldX, worldY, sediment));

			plates[worldPlates[worldTile]].setCrust(worldX, worldY, worldMap[worldTile] - OCEANIC_BASE, prev_timestamp);
			worldMap[worldTile] -= OCEANIC_BASE;

			if (worldMap[worldTile] <= 0)
			{
				worldPlates[worldTile] = activePlate;
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
			CollisionDetails cd = new CollisionDetails(worldPlates[worldTile], worldX, worldY, plateMap[plateTile] * foldingRatio);

				// Give some...
			worldMap[worldTile] += cd.getCrust();
			assert(!Float.isNaN(worldMap[worldTile]));
			plates[(int) worldPlates[worldTile]].setCrust(worldX, worldY, worldMap[worldTile], plateAge[plateTile]);

			// And take some.
			plates[activePlate].setCrust(worldX, worldY, plateMap[plateTile] * (1.0f - foldingRatio), plateAge[plateTile]);

			// Add collision to the earlier plate's list.
			collisions.get(activePlate).Push(cd);
		} else {
			CollisionDetails cd = new CollisionDetails(activePlate, worldX, worldY, worldMap[worldTile] * foldingRatio);
			plates[activePlate].setCrust(worldX, worldY, plateMap[plateTile] + cd.getCrust(), ageMap[worldTile]);
			plates[(int) worldPlates[worldTile]].setCrust(worldX, worldY, worldMap[worldTile] * (1.0f - foldingRatio), ageMap[worldTile]);
			collisions.get((int) worldPlates[worldTile]).Push(cd);

			// Give the location to the larger plate.
			assert(!Float.isNaN(plateMap[plateTile]));
			worldMap[worldTile] = plateMap[plateTile];
			worldPlates[worldTile] = activePlate;
			ageMap[worldTile] = plateAge[plateTile];
		}
		return 1;
	}
	
	private void processSubductions() {
		// Process all subductions
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			for (CollisionDetails cd: subductions.get(activePlate)) {
				plates[activePlate].addCrustBySubduction(
					cd.getX(), cd.getY(), cd.getCrust(), generations,
					plates[cd.getIndex()].vX, plates[cd.getIndex()].vY);
			}
			subductions.get(activePlate).clear();
		}
	}
	
	private void processCollisions() {
		for (int activePlate = 0; activePlate < numPlates; activePlate++) {
			for (CollisionDetails cd: collisions.get(activePlate)) {
				plates[activePlate].applyFriction(cd.getCrust());
				plates[cd.getIndex()].applyFriction(cd.getCrust());
				
				CollisionStatistic csPlateA = plates[activePlate].getCollisionInfo(cd.getX(), cd.getY());
				CollisionStatistic csPlateB = plates[cd.getIndex()].getCollisionInfo(cd.getX(), cd.getY());
				int collisionCount = Math.min(csPlateA.Collisions, csPlateB.Collisions);
				float collisionRatio = Math.max(csPlateA.CollidedRatio, csPlateB.CollidedRatio);
				
				if ((collisionCount > aggr_ratio_abs) | (collisionRatio > aggr_ratio_rel)) {
					float amount = plates[activePlate].aggregateCrust(plates[cd.getIndex()], cd.getX(), cd.getY());
					plates[cd.getIndex()].collide(plates[activePlate], cd.getX(), cd.getY(), amount);
				}
			}
			collisions.get(activePlate).clear();
		}
	}
	
	private void regenerateCrust(int[] worldPlatesOld, int[] worldAgeMap) {
		if (REGENERATE_CRUST) {
			for (int y = 0; y < worldSize; y++) {
				for (int x = 0; x < worldSize; x++) {
					int worldTile = Util.getTile(x, y, worldSize);
					if (worldPlates[worldTile] >= numPlates) {
						assert worldPlatesOld[worldTile] < numPlates: "Previous index map tile has no owner!";
						worldPlates[worldTile] = worldPlatesOld[worldTile];
						worldAgeMap[worldTile] = generations;
						worldMap[worldTile] = OCEANIC_BASE * BUOYANCY_BONUS;
						plates[(int) worldPlates[worldTile]].setCrust(x, y, OCEANIC_BASE, generations);
					}
				}
			}
		}
	}
	
	/**
	 * Adds some "virginity buoyancy" to all pixels for a visual boost.
	 * @param worldAgeMap
	 */
	private void addSeaFloorUplift(int[] worldAgeMap) {
		if (BUOYANCY_BONUS > 0) {
			for (int worldTile = 0; worldTile < worldMap.length; worldTile++) {
				// If it has been not more than MAX_BUOYANCY_AGE generations since the sea floor
				// was created from magma, increase the height by a decreasing amount.
				float buoyancyRatio = (MAX_BUOYANCY_AGE - (generations - worldAgeMap[worldTile])) / (float)MAX_BUOYANCY_AGE;
				if (buoyancyRatio > 0 && worldMap[worldTile] < CONTINENTAL_BASE)
					worldMap[worldTile] += buoyancyRatio * BUOYANCY_BONUS * OCEANIC_BASE; 
			}
		}
	}
	
	private PlateArea[] createPlates() {
		ArrayList<Integer> plateCenters = new ArrayList<Integer>();
		int worldTile;
		for (int i = 0; i < numPlates; i++) {
			// This loop ensures no sharing of plate centers.
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
				
				// in the 4 cardinal directions, allow wrapping.
				Stack<Integer> tiles = new Stack<Integer>();
				tiles.Push(Util.getTile(x, (y + worldSize - 1) % worldSize, worldSize));
				tiles.Push(Util.getTile(x, (y + 1) % worldSize, worldSize));
				tiles.Push(Util.getTile((x + worldSize - 1) % worldSize, y, worldSize));
				tiles.Push(Util.getTile((x + 1) % worldSize, y, worldSize));
				
				// If the N/S/E/W tile is un-owned, claim it for the active plate
				// and add it to that plate's border.
				for (int tile: tiles) {
					if (worldPlates[tile] >= numPlates) {
						worldPlates[tile] = activePlate;
						plates[activePlate].pushBorder(tile);
					}
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
				heightMap[i] = OCEANIC_BASE;			
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
