package com.tepidpond.tum.PlateTectonics;

import java.util.Arrays;
import java.util.Random;
import java.util.Vector;

import org.lwjgl.util.vector.Vector4f;

import scala.collection.generic.BitOperations.Int;

public class Plate {
	private static final float DEFORMATION_WEIGHT = 5f;
	private static final float INITIAL_SPEED = 1.0f;
	private static final float CONT_BASE = 1.0f;
	
	private int activeContinentID;
	private Vector<CollisionSegment> collisionSegments = new Vector<CollisionSegment>();
	
	// Height of terrain on the plate.
	private float[] heightMap;
	// Age of the crust on the plate.
	private int[] timestampMap;
	// Which collision segment is responsible for the given tile.
	private int[] segmentOwnerMap;
	// Dimensions and locations of plate in world coordinates
	private int left, top, width, height;
	// Size of world map
	private int mapSize;
	
	// Amount of crust that constitutes the plate.
	private float M;
	// Center of mass of the plate in world coordinates
	private float R_x, R_y;
	// Components of plate's velocity. vX and vY are components of a unit vector, velocity is the magnitude
	private float velocity, vX, vY;
	// Components of plate's acceleration
	private float dX, dY;
	
	// Used for random off-setting in subduction routine and setting up initial direction
	private Random rand;
	
	float getMomentum()  {return M * velocity;}
	int  getLeft()       {return left;}
	int getTop()         {return top;}
	int getHeight()      {return height;}
	int getWidth()       {return width;}
	float getVelocity()  {return velocity;}
	float getVelocityX() {return vX;}
	float getVelocityY() {return vY;}
	Boolean isEmpty()    {return M<=0;} 

	public Plate(float[] plateData, int plateMapWidth, int xOrigin, int yOrigin, int plateAge, int mapSize, Random rand) {
		if (plateData.length < 1) return;
		
		// Save basic pre-defined data.
		this.heightMap = new float[plateData.length];
		this.timestampMap = new int[plateData.length];
		this.segmentOwnerMap = new int[plateData.length];
		Arrays.fill(segmentOwnerMap, 255);
		this.left = xOrigin;
		this.top = yOrigin;
		this.width = plateMapWidth;
		this.height = plateData.length / plateMapWidth;
		this.rand = rand;
		this.mapSize = mapSize;
		
		// Establish initial velocity and direction.
		double angle = 2 * Math.PI * rand.nextDouble();
		this.velocity = 1;
		this.vX = (float)Math.cos(angle) * INITIAL_SPEED;
		this.vY = (float)Math.sin(angle) * INITIAL_SPEED;
		// Intended for random circular motion of plate. Unused.
		//this.alpha = -rand.nextInt(1) * Math.PI * 0.01 * rand.nextFloat();
		
		// Clone heightMap data, calculate center of mass and total mass.
		int tileIndex = 0; float activeTile = 0.0f; 
		System.arraycopy(plateData, 0, heightMap, 0, plateData.length);
		for(int x = 0; x<width; x++) {
			for (int y=0; y<height; y++) {
				tileIndex = y * width + x;
				activeTile = heightMap[tileIndex];
				R_x += x * activeTile;
				R_y += y * activeTile;
				M += activeTile;
				
				if (activeTile > 0.0f)
					this.timestampMap[tileIndex] = plateAge;
			}
		}
		
		// Normalize center of mass.
		R_x /= M;
		R_y /= M;
	}
	
	/**
	 * Increment collision counter of the continent at given location.
	 * @param x X coordinate of collision point on world map.
	 * @param y Y coordinate of collision point on world map.
	 * @return Surface area of the collided continent (HACK!)
	 */
	int addCollision(int x, int y) {
		int tile = getMapIndex(x, y);
		int xLocal = getOffsetX(x);
		int yLocal = getOffsetY(y);
		int newSegment = this.segmentOwnerMap[tile];
		if (newSegment >= collisionSegments.size())
			newSegment = createSegment(xLocal, yLocal);
		
		collisionSegments.elementAt(newSegment).Collisions++;
		return collisionSegments.elementAt(newSegment).Area;
	}
	

	/**
	 * Add crust to plate as result of continental collision.
	 * @param x X coordinate of location of new crust on world map.
	 * @param y Y coordinate of location of new crust on world map.
	 * @param amount Amount of crust to add. (units?)
	 * @param creationTime Time of creation of new crust.
	 */
	void addCrustByCollision(int x, int y, float amount, int creationTime) {
		setCrust(x, y, getCrust(x, y) + amount, creationTime);
		int tile = getMapIndex(x, y);
		int xLocal = getOffsetX(x);
		int yLocal = getOffsetY(y);
	}
	
	/**
	 * Simulates subduction of oceanic plate under this plate.
	 * 
	 * Subduction is simulated by calculating the distance on surface
	 * that subducting sediment will travel under the plate until the
	 * subducting slab has reached certain depth where the heat triggers
	 * the melting and uprising of molten magma.
	 * 
	 * @param x X coordinate of origin of subduction on world map.
	 * @param y Y coordinate of origin of subduction on world map.
	 * @param amount Amount of sediment that subducts.
	 * @param creationTime Time of creation of new crust.
	 * @param dX X direction of the subducting plate.
	 * @param dY Y direction of the subducting plate.
	 */
	void addCrustBySubduction(int x, int y, float amount, int creationTime, float dX, float dY) {
		int localX = getOffsetX(x), localY = getOffsetY(y);
		
		float dotProduct = vX * dX + vY * dX;
		if (dotProduct > 0) {
			dX -= vX;
			dY -= vY;
		}
		
		float offset = 3.0f * (float)Math.pow(rand.nextFloat(), 3.0D) * (2 * rand.nextInt(1) - 1);
		dX = 10 * dX + offset;
		dY = 10 * dY + offset;
		
		localX += dX;
		localY += dY;
		
		if (width == mapSize) x &= width - 1;
		if (height == mapSize) x &= height - 1;
		
		int mapTile = y * width + x;
		if (mapTile < width * height && heightMap[mapTile] > 0) {
			creationTime = (timestampMap[mapTile] + creationTime)/2;
			if (amount > 0)
				timestampMap[mapTile] = creationTime;
			else
				timestampMap[mapTile] = 0;
			
			heightMap[mapTile] += amount;
			M += amount;
		}
	}
	
	/**
	 * Add continental crust from this plate onto another plate.
	 * 
	 * Aggregation of two continents is the event where the collided
	 * pieces of crust fuse together at the point of collision. It is
	 * crucial to merge not only the collided pieces of crust but also
	 * the entire continent that's part of the colliding tad of crust.
	 * However, because one plate can contain many islands and pieces
	 * of continents, the merging must be done WITHOUT merging the entire
	 * plate and all those continental pieces that have NOTHING to do with
	 * the collision in question.
	 * 
	 * @param plate Destination plate receiving the crust
	 * @param worldX X coordinate of collision point on world map.
	 * @param worldY Y coordinate of collision point on world map.
	 * @return Amount of crust added to destination plate.
	 */
	float aggregateCrust(Plate plate, int worldX, int worldY) {
		int mapTile = getMapIndex(worldX, worldY);
		int localX = getOffsetX(worldX);
		int localY = getOffsetY(worldY);
		
		int segmentID = segmentOwnerMap[mapTile];
		CollisionSegment segment = collisionSegments.elementAt(segmentID); 
		if (segment.Area == 0)
			return 0;	// Ignore empty continents.
		
		plate.selectCollisionSegment(worldX, worldY);
		worldX += mapSize; worldY += mapSize;
		
		float M_old = M;
		for (int iY = segment.Y0; iY < segment.Y1; iY++) {
			for (int iX = segment.X0; iX < segment.X1; iX++) {
				int activeTile = iY * width + iX;
				if (segmentOwnerMap[activeTile] == segmentID && heightMap[activeTile] > 0) {
					plate.addCrustByCollision(worldX + localX - iX, worldY + localY - iY, heightMap[activeTile], timestampMap[activeTile]);
					
					M -= heightMap[activeTile];
					heightMap[activeTile] = 0;
				}
			}
		}
		
		segment.Area = 0;
		return M_old - M;
	}
	
	/**
	 * Decrease the speed of plate relative to total mass.
	 * 
	 * Decreases the speed of plate due to friction occurring when two
	 * plates collide. The amount of reduction depends on the amount of
	 * mass that causes friction (i.e. has collided) compared to the
	 * total mass of the plate. Thus big chunk of crust colliding into a
	 * small plate will halt it but have little effect on a huge plate.
	 * 
	 * @param deformingMass Amount of mass deformed in collision.
	 */
	void applyFriction(float deformingMass) {
		if (deformingMass > 0) {
			float dV = DEFORMATION_WEIGHT * deformingMass / M;

			if (dV > velocity) dV = velocity;
			velocity -= dV;
		}
	}
	
	/**
	 * Collides two plates according to Newton's laws of motion.
	 * 
	 * The velocity and direction of both plates are updated using
	 * impulse forces following the collision according to Newton's laws
	 * of motion. Deformations are not applied but energy consumed by the
	 * deformation process is taken away from plate's momentum.
	 * 
	 * @param plate Plate to test against.
	 * @param worldX X coordinate of collision point on world map.
	 * @param worldY Y coordinate of collision point on world map.
	 * @param collidingMass Amount of colliding mass from source plate.
	 */
	void collide(Plate plate, int worldX, int worldY, float collidingMass) {
		float coefficientRestitution = 0.0f;
		
		int plateA_X = this.getOffsetX(worldX), plateA_Y = this.getOffsetY(worldY);
		int plateB_X = plate.getOffsetX(worldX), plateB_Y = plate.getOffsetY(worldY);
		int plateA_Tile = this.getMapIndex(worldX, worldY);
		int plateB_Tile = plate.getMapIndex(worldX, worldY);

		float plateA_dX = plateA_X - R_x;
		float plateA_dY = plateA_Y - R_y;
		float plateB_dX = plateB_X - plate.R_x;
		float plateB_dY = plateB_Y - plate.R_y;
		float collision_X = plateA_dX - plateB_dX;
		float collision_Y = plateA_dY - plateB_dY;
		
		float magnitude = (float)Math.sqrt(collision_X * collision_X + collision_Y * collision_Y);
		if (magnitude <= 0)
			return;	// no relative motion between plates.
		
		collision_X /= magnitude; collision_Y /= magnitude;	// normalize collision vector
		float relative_X = vX - plate.vX, relative_Y = vY - plate.vY;	// find relative velocity vector
		float dotProduct = relative_X * collision_X + relative_Y * collision_Y;
		
		if (dotProduct <= 0)
			return;	// plates moving away from each other.
		
		float denominatorOfImpulse = (float)Math.pow(magnitude, 2.0f) * (1.0f/M + 1.0f/collidingMass);
		
		// force of impulse
		float J = -(1 + coefficientRestitution) * dotProduct / denominatorOfImpulse;
		
		// Finally apply an acceleration;
		dX += collision_X * J / M;
		dY += collision_Y * J / M;
		plate.dX -= collision_X * J / (collidingMass + plate.M);
		plate.dY -= collision_Y * J / (collidingMass + plate.M);
	}
	
	/**
	 * Apply plate wide erosion algorithm.
	 * 
	 * Plate's total mass and the center of mass are updated.
	 * 
	 * @param lowerBound Sets limit below which there's no erosion. (Is this height limit? Mass?)
	 */
	void erode(float lowerBound) {
		float newHeightmap[] = new float[width * height];
		Arrays.fill(newHeightmap, 0);
		M = R_x = R_y = 0;
		
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int mapTile = y * width + x;
				M += heightMap[mapTile];
				newHeightmap[mapTile] += heightMap[mapTile];
				
				// Update R (center of mass)
				R_x += x * heightMap[mapTile];
				R_y += y * heightMap[mapTile];
				if (heightMap[mapTile] < lowerBound)
					continue;	// eroded too far already, no more
				
				int mapTileN = Math.max(0, (y - 1)) * width + x;
				int mapTileS = Math.min(height - 1, (y + 1)) * width + x;
				int mapTileW = y * width + Math.max(0, x - 1);
				int mapTileE = y * width + Math.min(width - 1, x + 1);
				
				float heightN = 0, heightS = 0, heightW = 0, heightE = 0;
				if (y > 0)          heightN = heightMap[mapTileN];
				if (y < height - 1) heightS = heightMap[mapTileS];
				if (x > 0)          heightW = heightMap[mapTileW];
				if (x < width - 1)  heightE = heightMap[mapTileE];
				if (heightN + heightS + heightW + heightE == 0)
					continue;	// no neighbors
				
				float diffN = heightMap[mapTile] - heightN;
				float diffS = heightMap[mapTile] - heightS;
				float diffW = heightMap[mapTile] - heightW;
				float diffE = heightMap[mapTile] - heightE;
				float minDiff = Math.min(Math.min(diffN, diffS), Math.min(diffW, diffE));
				float diffSum = (heightN > 0 ? (diffN - minDiff) : 0.0f) + 
								(heightS > 0 ? (diffS - minDiff) : 0.0f) + 
								(heightW > 0 ? (diffW - minDiff) : 0.0f) + 
								(heightE > 0 ? (diffE - minDiff) : 0.0f);
				
				if (diffSum < minDiff) {
					newHeightmap[mapTileN] += (heightN > 0)?(diffN - minDiff):0;
					newHeightmap[mapTileS] += (heightS > 0)?(diffS - minDiff):0;
					newHeightmap[mapTileW] += (heightW > 0)?(diffW - minDiff):0;
					newHeightmap[mapTileE] += (heightE > 0)?(diffE - minDiff):0;
					newHeightmap[mapTile] -= diffSum;
					minDiff -= diffSum;
					minDiff /= 1 + (heightN > 0?1:0) + (heightS > 0?1:0) +
					               (heightW > 0?1:0) + (heightE > 0?1:0);
					
					newHeightmap[mapTileN] += (heightN > 0)?(minDiff):0;
					newHeightmap[mapTileS] += (heightS > 0)?(minDiff):0;
					newHeightmap[mapTileW] += (heightW > 0)?(minDiff):0;
					newHeightmap[mapTileE] += (heightE > 0)?(minDiff):0;
				} else {
					// Remove the erodable crust from the tile
					newHeightmap[mapTile] -= minDiff;
					float crustToShare = minDiff / diffSum;
					// And spread it over the four neighbors.
					newHeightmap[mapTileN] += crustToShare * (heightN > 0?diffN - minDiff:0);
					newHeightmap[mapTileS] += crustToShare * (heightS > 0?diffS - minDiff:0);
					newHeightmap[mapTileW] += crustToShare * (heightW > 0?diffW - minDiff:0);
					newHeightmap[mapTileE] += crustToShare * (heightE > 0?diffE - minDiff:0);
				}
			}
		}
		// Save new eroded heights
		heightMap = newHeightmap;
		// Normalize center of mass
		if (M > 0) {
			R_x /= M;
			R_y /= M;
		}
	}
	
	/**
	 * Retrieve collision statistics of continent at given location.
	 * @param worldX X coordinate of collision point on world map.
	 * @param worldY Y coordinate of collision point on world map.
	 * @return Instance of collision statistic class containing percentage
	 *         of area collided and number of collisions
	 */
	CollisionStatistic getCollisionInfo(int worldX, int worldY) {
		int localX = getOffsetX(worldX), localY = getOffsetY(worldY);
		int mapTile = getMapIndex(worldX, worldY);
		int segmentID = segmentOwnerMap[mapTile];
		CollisionSegment segment = collisionSegments.elementAt(segmentID);
		
		return new CollisionStatistic(segment.Collisions, segment.Collisions / (1.0f + segment.Area));		
	}
	
	/**
	 * Retrieve the surface area of continent lying at desired location.
	 * 
	 * @param worldX X coordinate of collision point on world map.
	 * @param worldY Y coordinate of collision point on world map.
	 * @return Area of continent at desired location or 0 if none.
	 */
	int getContinentArea(int worldX, int worldY) {
		int mapTile = getMapIndex(worldX, worldY);
		return collisionSegments.elementAt(mapTile).Area;
	}
	
	/**
	 * Get the amount of plate's crustal material at some location.
	 * 
	 * @param x X coordinate on world map.
	 * @param y Y coordinate on world map.
	 * @return Amount of crust at requested location.
	 */
	float getCrust(int x, int y) {
		int tileLocal = getMapIndex(x, y);
		if (tileLocal<0 || tileLocal > timestampMap.length) return 0;
		
		return heightMap[tileLocal];
	}
	
	/**
	 * Get the timestamp of plate's crustal material at some location.
	 * 
	 * @param x X coordinate on world map.
	 * @param y Y coordinate on world map.
	 * @return Timestamp of creation of crust at the location or 0 if no crust.
	 */
	int getCrustTimestamp(int x, int y) {
		int tileLocal = getMapIndex(x, y);
		if (tileLocal<0 || tileLocal > timestampMap.length) return 0;
		
		return timestampMap[tileLocal];
	}
	
	/**
	 * Get plate's data.
	 * @return heightMap data
	 */
	float[] getHeightmap() {
		return this.heightMap;
	}
	
	/**
	 * Get plate's data.
	 * @return Time of creation data.
	 */
	int[] getTimestampMap() {
		return this.timestampMap;
	}
	
	/**
	 * Moves plate along its trajectory.
	 */
	void move() {
		updateVelocity();
		updatePosition();
	}
	private void updateVelocity() {
		vX += dX; dX = 0;
		vY += dY; dY = 0;
		
		float len = (float)Math.sqrt(vX * vX + vY * vY);
		vX /= len;
		vY /= len;
		velocity += len - 1.0;
		if (velocity<0) velocity = 0;
	}
	private void updatePosition() {
		float leftTmp = vX * velocity + left;
		float topTmp = vY * velocity + top;
		
		// Wrap-around positions into torus-shaped world.
		while (leftTmp < 0)       leftTmp += mapSize;
		while (topTmp  < 0)       topTmp  += mapSize;
		while (leftTmp > mapSize) leftTmp -= mapSize;
		while (topTmp  > mapSize) topTmp  -= mapSize;
		
		left = (int)leftTmp;
		top = (int)topTmp;
	}
	
	/**
	 * Clear any earlier continental crust partitions.
	 * 
	 * Plate has internal bookkeeping of distinct areas of continental
	 * crust for more realistic collision response. However as the number
	 * of collisions that plate experiences grows, so does the bookkeeping
	 * of a continent become more and more inaccurate. Finally it results
	 * in striking artifacts that cannot be overlooked.
	 * 
	 * To alleviate this problem without the need of per iteration
	 * recalculations plate supplies caller a method to reset its
	 * bookkeeping and start clean.
	 */
	void resetSegments() {
		collisionSegments.removeAllElements();
		Arrays.fill(segmentOwnerMap, 255);
	}
	
	/**
	 * Remember the currently processed continent's segment number.
	 * 
	 * @param x X coordinate of origin of collision on world map.
	 * @param y Y coordinate of origin of collision on world map.
	 */
	void selectCollisionSegment(int x, int y) {
		int mapTile = getMapIndex(x, y);
		activeContinentID = segmentOwnerMap[mapTile];
	}

	/**
	 * Set the amount of plate's crustal material at some location.
	 * 
	 * If the amount of crust to be set is negative, it'll be set to zero.
	 * 
	 * @param worldX X coordinate of desired location on the world map.
	 * @param worldY Y coordinate of desired location on the world map.
	 * @param amount Amount of material at the given location.
	 * @param timeStamp Time of creation of new crust.
	 */
	void setCrust(int worldX, int worldY, float amount, int timeStamp) {
		if (amount < 0) amount = 0;	//negative mass is unlikely
		
		int plateTile = getMapIndex(worldX, worldY);
		
		if (!worldTileIsOnPlate(worldX, worldY)) {
			// Bounds of this plate
			int bounds[] = new int[] {left, top, left + width - 1, top + height - 1};

			worldX &= mapSize - 1; worldY &= mapSize - 1;	// Just to be safe

			// Distance from each edge for the new crust piece.
			int dist[] = new int[] {
				(bounds[0] - worldX),
				(bounds[1] - worldY),
				-(bounds[2] - worldX) + (worldX < bounds[0] ? mapSize : 0),
				-(bounds[3] - worldY) + (worldY < bounds[1] ? mapSize : 0)
			};
			// Negative distances are not valid.
			for(int i = 0; i < 4; i++) if (dist[i] < 0) dist[i] = mapSize;
			int dist2[] = new int[4];
			if (dist[0] >= dist[2] || dist[0] >= mapSize) dist2[0] = 0; else dist2[0] = dist[0];
			if (dist[1] >= dist[3] || dist[1] >= mapSize) dist2[1] = 0; else dist2[1] = dist[1];
			if (dist[2] >  dist[0] || dist[2] >= mapSize) dist2[2] = 0; else dist2[2] = dist[2];
			if (dist[3] >  dist[1] || dist[3] >= mapSize) dist2[3] = 0; else dist2[3] = dist[3];
						
			// Add new tile to nearest plate border

			
			// Clamp new plate size to world map size
			if (width + dist2[0] + dist2[2] > mapSize) {
				dist2[0] = 0;
				dist2[2] = mapSize - width;
			}
			if (height + dist2[1] + dist2[3] > mapSize) {
				dist2[1] = 0;
				dist2[3] = mapSize - height;
			}
			
			// Update plate bounds based on distance
			int oldWidth  = width, oldHeight = height;
			left   -= dist2[0]; if (left < 0) left += mapSize;
			width += dist2[0] + dist2[2];
			top    -= dist2[1]; if (top  < 0) top  += mapSize;
			height += dist2[1] + dist2[3];
			
			System.out.printf("%dx%d + [%d,%d] + [%d,%d] = %dx%d\n", oldWidth, oldHeight, dist2[0], dist2[1], dist2[2], dist2[3], width, height);
			
			// Reallocate plate data storage
			float[] newHeightmap = new float[width * height];
			int[] newSegmentOwnerMap = new int[width * height];
			int[] newTimestampMap = new int[width * height];
			
			// Copy existing data over
			for (int row = 0; row < oldHeight; row++) {
				int posDest = (int) ((dist2[1] + row) * width + dist2[0]);
				int posSrc = row * oldWidth;
				
				if (oldWidth>width)
					System.out.println("Panic!");
				System.arraycopy(heightMap, posSrc, newHeightmap, posDest, oldWidth);
				System.arraycopy(segmentOwnerMap, posSrc, newSegmentOwnerMap, posDest, oldWidth);
				System.arraycopy(timestampMap, posSrc, newTimestampMap, posDest, oldWidth);
			}
			
			// Replace the old(now invalid) storage
			heightMap = newHeightmap;
			segmentOwnerMap = newSegmentOwnerMap;
			timestampMap = newTimestampMap;
			
			// Shift collision segment local coordinates
			for (CollisionSegment seg:collisionSegments) {
				seg.X0 += dist2[0];
				seg.X1 += dist2[0];
				seg.Y0 += dist2[1];
				seg.Y1 += dist2[1];
			}
			plateTile = getMapIndex(worldX, worldY);
			if (plateTile >= heightMap.length) {
				System.out.println("Panic! Tile outside of map after resize!" + Integer.toString(heightMap.length) + "<=" + Integer.toString(plateTile));
			}
		}
		if (plateTile < heightMap.length) {
			if (amount > 0 && heightMap[plateTile] > 0) {
				timestampMap[plateTile] += timeStamp;
				timestampMap[plateTile] /= 2;
			} else if (amount > 0) {
				timestampMap[plateTile] = timeStamp;
			}
			// Update mass
			M -= heightMap[plateTile];
			heightMap[plateTile] = amount;
			M += heightMap[plateTile];
		}
	}
	
	/**
	 * Separate a continent at (X, Y) to its own partition.
	 * 
	 * Method analyzes the pixels 4-ways adjacent at the given location
	 * and labels all connected continental points with the same segment ID.
	 * 
	 * @param localX X coordinate on the local map.
	 * @param localY Y coordinate on the local map.
	 * @return ID of created segment on success, otherwise -1.
	 */
	private int createSegment(int localX, int localY) {
		int origin_index = localY * width + localX;
		int newSegmentID = collisionSegments.size();
		
		// This tile already belongs to a collision segment
		if (segmentOwnerMap[origin_index] < newSegmentID)
			return segmentOwnerMap[origin_index];

		// Is a neighboring tile part of an existing collision segment?
		int adjSegmentID = checkNeighboringSegment(localX, localY);
		if (adjSegmentID < newSegmentID)
			return adjSegmentID;
				
		segmentOwnerMap[origin_index] = newSegmentID;
		CollisionSegment newSegment = new CollisionSegment(localX, localY, localX, localY, 1);
		
		Stack<Integer> border = new Stack<Integer>();
		border.Push(origin_index);
		while (!border.IsEmpty()) {
			// choose random location on border 
			int borderIndex = rand.nextInt(border.size());
			int mapTile = border.Peek(borderIndex);
			
			int x = Util.getX(mapTile, width);
			int y = Util.getY(mapTile, width);
			
			// in the 4 cardinal directions, clamp at border.
			int tileN, tileS, tileW, tileE;
			tileN = Util.getTile(x, Math.max(y - 1, 0), width);
			tileS = Util.getTile(x, Math.min(y + 1, height - 1), width);
			tileW = Util.getTile(Math.max(x - 1, 0), y, width);
			tileE = Util.getTile(Math.min(x + 1, width - 1), y, width);
			
			// If the N/S/E/W tile is un-owned, claim it for the active segment
			// and add it to the border.
			if (segmentOwnerMap[tileN] > newSegmentID && heightMap[tileN] >= CONT_BASE) {
				segmentOwnerMap[tileN] = newSegmentID;
				border.Push(tileN);
				newSegment.Area++;
				newSegment.UpdateBoundsToInclude(Util.getX(tileN, width), Util.getY(tileN, width));
			}
			if (segmentOwnerMap[tileS] > newSegmentID && heightMap[tileS] >= CONT_BASE) {
				segmentOwnerMap[tileS] = newSegmentID;
				border.Push(tileS);
				newSegment.Area++;
				newSegment.UpdateBoundsToInclude(Util.getX(tileS, width), Util.getY(tileS, width));
			}
			if (segmentOwnerMap[tileW] > newSegmentID && heightMap[tileW] >= CONT_BASE) {
				segmentOwnerMap[tileW] = newSegmentID;
				border.Push(tileW);
				newSegment.Area++;
				newSegment.UpdateBoundsToInclude(Util.getX(tileW, width), Util.getY(tileW, width));
			}
			if (segmentOwnerMap[tileE] > newSegmentID && heightMap[tileE] >= CONT_BASE) {
				segmentOwnerMap[tileE] = newSegmentID;
				border.Push(tileE);
				newSegment.Area++;
				newSegment.UpdateBoundsToInclude(Util.getX(tileE, width), Util.getY(tileE, width));
			}
			
			// Overwrite processed point in border with last item from border
			border.set(borderIndex, border.Peek());
			border.Pop();
		}
		
		collisionSegments.addElement(newSegment);		
		return newSegmentID;
	}
	
	private int checkNeighboringSegment(int localX, int localY) {
		int origin_index = localY * width + localX;

		int newSegmentID = collisionSegments.size();
		int adjTileSegmentID = newSegmentID;
		if ((localX > 0) &&
			heightMap[origin_index-1] >= CONT_BASE &&
			segmentOwnerMap[origin_index-1] < newSegmentID) {
			adjTileSegmentID = segmentOwnerMap[origin_index - 1];
		} else if ((localX < width - 1) &&
				heightMap[origin_index+1] >= CONT_BASE &&
				segmentOwnerMap[origin_index+1] < newSegmentID) {
				adjTileSegmentID = segmentOwnerMap[origin_index + 1];
		} else if ((localY > 0) &&
				heightMap[origin_index - width] >= CONT_BASE &&
				segmentOwnerMap[origin_index - width] < newSegmentID) {
				adjTileSegmentID = segmentOwnerMap[origin_index - width];
		} else if ((localY < height - 1) &&
				heightMap[origin_index + width] >= CONT_BASE &&
				segmentOwnerMap[origin_index + width] < newSegmentID) {
				adjTileSegmentID = segmentOwnerMap[origin_index + width];
		}
		if (adjTileSegmentID < newSegmentID) {
			// A neighbor exists, this tile should be added to it instead
			segmentOwnerMap[origin_index] = adjTileSegmentID;
			CollisionSegment segment = collisionSegments.elementAt(adjTileSegmentID);
			segment.Area++;
			if (localX > segment.X0) segment.X0 = localX;
			if (localX > segment.X1) segment.X1 = localX;
			if (localY < segment.Y0) segment.Y0 = localY;
			if (localY < segment.Y1) segment.Y1 = localY;
		}		
		return adjTileSegmentID;
	}

	/**
	 * Translate world coordinates into offset within plate's height map.
	 * 
	 * @param x X coordinate on world map.
	 * @param y Y coordinate on world map.
	 * @return Index into local heightmap.
	 */
	private int getMapIndex(int x, int y) {
		return (getOffsetY(y) * width + getOffsetX(x));
	}
	private int getOffsetX(int x) {
		x &= mapSize - 1;           // scale within map dimensions
		if (x < this.left) x += mapSize; // wrap around world edge if necessary
		x -= this.left;
		return x;
	}
	private int getOffsetY(int y) {
		y &= mapSize - 1;           // scale within map dimensions
		if (y < this.top) y += mapSize;  // wrap around world edge if necessary
		y -= this.top;
		return y;
	}
	private boolean worldTileIsOnPlate(int worldX, int worldY) {
		if (worldX < 0 || worldY < 0) return false;
		worldX &= mapSize - 1; worldY &= mapSize - 1;
		
		if ((worldX < this.left) ||
			(worldY < this.top) ||
			(worldX - this.left) >= this.width ||
			(worldY - this.top) >= this.height) return false;
		return true;
	}
}
