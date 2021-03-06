package com.tepidpond.tum.PlateTectonics;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import org.lwjgl.util.vector.Vector2f;

public class Plate {
	private class TileIndex {
		final int I;
		final int X;
		final int Y;
		public TileIndex(int X, int Y) {
			X = (X + mapSize) % mapSize;
			Y = (Y + mapSize) % mapSize;
			if (Y >= height || Y < 0 || X >= width || X < 0)
				throw new IllegalArgumentException("Coordinates outside of plate!");
			this.I = Y * width + X;
			this.X = X;
			this.Y = Y;
		}
		public TileIndex(int index) {
			this(index % width, index / width);
		}
	}
	
	private static final float DEFORMATION_WEIGHT = 5f;
	private static final float INITIAL_SPEED = 1.0f;
	
	private int activeContinentID;
	private Vector<CollisionSegment> collisionSegments = new Vector<CollisionSegment>();
	
	// Height of terrain on the plate.
	private float[] heightMap;
	// Age of the crust on the plate.
	private int[] timestampMap;
	// Which collision segment is responsible for the given tile.
	private int[] segmentOwnerMap;
	// Dimensions and locations of plate in world coordinates
	private float left, top;
	private int width, height;
	// Size of world map
	private int mapSize;
	
	// Amount of crust that constitutes the plate.
	private float M;
	private float invMass;
	// Center of mass of the plate in world coordinates
	private float R_x, R_y;
	// Components of plate's velocity. vX and vY are components of a unit vector, velocity is the magnitude
	float Velocity, vX, vY;
	// Components of plate's acceleration
	private float dX, dY;
	
	// Used for random off-setting in subduction routine and setting up initial direction
	private Random rand;
	
	float getMomentum()  {return M * Velocity;}
	int  getLeft()       {return (int)left;}
	int getTop()         {return (int)top;}
	int getHeight()      {return height;}
	int getWidth()       {return width;}
	Boolean isEmpty()    {return M<=0;} 
	
	public Plate(float[] plateData, int plateMapWidth, int xOrigin, int yOrigin, int plateAge, int mapSize, Random rand) {
		if (plateData.length < 1) return;
		
		this.width = plateMapWidth;
		this.height = plateData.length / plateMapWidth;
		this.mapSize = mapSize;
		this.M = 0;
		this.left = xOrigin;
		this.top = yOrigin;
		this.rand = rand;
		R_x = 0;
		R_y = 0;
		dX = dY = 0;
		
		int area = width * height;
		double angle = 2 * Math.PI * rand.nextDouble();
		
		// Save basic pre-defined data.
		this.heightMap = new float[area];
		this.timestampMap = new int[area];
		this.segmentOwnerMap = new int[area];
		
		// Establish initial velocity and direction.
		vX = (float)Math.cos(angle) * INITIAL_SPEED;
		vY = (float)Math.sin(angle) * INITIAL_SPEED;
		Velocity = 1.0f;
		
		// Intended for random circular motion of plate. Unused.
		//this.alpha = -rand.nextInt(1) * Math.PI * 0.01 * rand.nextFloat();
		Arrays.fill(segmentOwnerMap, Integer.MAX_VALUE);
		
		
		// Clone heightMap data, calculate center of mass and total mass.
		for (int i = 0; i < heightMap.length; i++) {
			TileIndex ti = new TileIndex(i);
			// Clone map data and count crust mass.
			M += heightMap[ti.I] = plateData[ti.I];

			// Calculate center coordinates weighted by mass.
			R_x += ti.X * heightMap[ti.I];
			R_y += ti.Y * heightMap[ti.I];

			// Set the age of ALL points in this plate to same
			// value. The right thing to do would be to simulate
			// the generation of new oceanic crust as it he plate
			// had been moving to its current direction until all
			// plate's (oceanic) crust receives an age.
			this.timestampMap[ti.I] = plateData[ti.I] > 0 ? plateAge : 0;
		}
		
		// Normalize center of mass.
		if (M > 0) {
			R_x /= M;
			R_y /= M;
			invMass = 1f / M;
		} else {
			invMass = 0f;
			R_x = 0;
			R_y = 0;
		}
	}
	
	/**
	 * Increment collision counter of the continent at given location.
	 * @param worldX X coordinate of collision point on world map.
	 * @param worldY Y coordinate of collision point on world map.
	 * @return Surface area of the collided continent (HACK!)
	 */
	int addCollision(int worldX, int worldY) {
		TileIndex tiColl = getLocalTile(worldX, worldY);
		
		int segment = segmentOwnerMap[tiColl.I];
		if (segment >= collisionSegments.size())
			segment = createSegment(tiColl);
		assert segment < collisionSegments.size(): "Could not create segment.";
		
		collisionSegments.get(segment).Collisions++;
		return collisionSegments.get(segment).Area;
	}
	

	/**
	 * Add crust to plate as result of continental collision.
	 * @param worldX X coordinate of location of new crust on world map.
	 * @param worldY Y coordinate of location of new crust on world map.
	 * @param amount Amount of crust to add. (units?)
	 * @param creationTime Time of creation of new crust.
	 */
	void addCrustByCollision(int worldX, int worldY, float amount, int creationTime) {
		worldX %= mapSize; worldY %= mapSize;
		setCrust(worldX, worldY, getCrust(worldX, worldY) + amount, creationTime);
		try {
			TileIndex tiColl = getLocalTile(worldX, worldY);

			segmentOwnerMap[tiColl.I] = activeContinentID;
			CollisionSegment seg = collisionSegments.get(activeContinentID);
			seg.Area++;
			
			seg.UpdateBoundsToInclude(tiColl.X, tiColl.Y);
		} catch (IllegalArgumentException e) {
			assert false: String.format("Aggregation went overboard! (%d, %d) in [%d,%d]-[%d,%d]", worldX, worldY, left, top, left + width - 1, top + height - 1);
		}
	}
	
	/**
	 * Simulates subduction of oceanic plate under this plate.
	 * 
	 * Subduction is simulated by calculating the distance on surface
	 * that subducting sediment will travel under the plate until the
	 * subducting slab has reached certain depth where the heat triggers
	 * the melting and uprising of molten magma.
	 * 
	 * @param worldX X coordinate of origin of subduction on world map.
	 * @param worldY Y coordinate of origin of subduction on world map.
	 * @param amount Amount of sediment that subducts.
	 * @param creationTime Time of creation of new crust.
	 * @param dX X direction of the subducting plate.
	 * @param dY Y direction of the subducting plate.
	 */
	void addCrustBySubduction(int worldX, int worldY, float amount, int creationTime, float dX, float dY) {
		try {
			TileIndex tiSubd = getLocalTile(worldX, worldY);
	
			float dotProduct = vX * dX + vY * dY;
			if (dotProduct > 0) {
				dX -= vX;
				dY -= vY;
			}
			
			// o = +- 3 * R^3
			float offset = (rand.nextBoolean() ? 1 : -1) * 3.0f * (float)Math.pow(rand.nextFloat(), 3.0);
			dX = 10 * dX + offset;
			dY = 10 * dY + offset;
			
			try {
				tiSubd = new TileIndex(tiSubd.X + (int)dX, tiSubd.Y + (int)dY);
			
				if (heightMap[tiSubd.I] > 0) {
					creationTime = (timestampMap[tiSubd.I] + creationTime)/2;
					timestampMap[tiSubd.I] = amount > 0 ? creationTime : 0;
				
					heightMap[tiSubd.I] += amount;
					M += amount;
					if (M > 0)
						invMass = 1f / M;
					else
						invMass = 0f;
				}
			} catch (IllegalArgumentException e) {
				// selected position is outside the plate. Ideally we'd try again.
			}
		} catch (IllegalArgumentException e) {
			// request position is outside the plate. This is a caller error.
			assert false: String.format("Subduction origin not on plate!\n%d, %d @ [%f, %f]x[%d, %d]\n",
					worldX, worldY, left, top, width, height);
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
		try {
			TileIndex tiAggr = getLocalTile(worldX, worldY);

			int segmentID = segmentOwnerMap[tiAggr.I];
			
			// This check forces the caller to do things in proper order!
			//
			// Usually continents collide at several locations simultaneously.
			// Thus if this segment that is being merged now is removed from
			// segmentation bookkeeping, then the next point of collision that is
			// processed during the same iteration step would cause the test
			// below to be true and system would experience a premature abort.
			//
			// Therefore, segmentation bookkeeping is left intact. It doesn't
			// cause significant problems because all crust is cleared and empty
			// points are not processed at all.		
			assert segmentID < collisionSegments.size(): String.format("Trying to aggregate without deforming first at (%d, %d).\n", worldX, worldY);
			CollisionSegment segment = collisionSegments.elementAt(segmentID);
			
			// One continent may have many points of collision. If one of them
			// causes continent to aggregate then all successive collisions and
			// attempts of aggregation would necessarily change nothing at all,
			// because the continent was removed from this plate earlier!		
			if (segment.Area == 0)
				return 0;	// Ignore empty continents.
			
			plate.selectCollisionSegment(worldX, worldY);
			
			/* System.out.printf("Aggregating segment [%d, %d]x[%d, %d] vs. [%d, %d]@[%d, %d]\n",
					segment.X0, segment.Y0, segment.X1, segment.Y1, width, height, localX, localY); */

			worldX += mapSize; worldY += mapSize;
			float M_old = M;
			
			for (int iY = segment.Y0; iY <= segment.Y1; iY++) {
				for (int iX = segment.X0; iX <= segment.X1; iX++) {
					TileIndex tiSeg = new TileIndex(iX, iY);
					
					if (segmentOwnerMap[tiSeg.I] == segmentID && heightMap[tiSeg.I] > 0) {
						// Add the crust to the other plate.
						plate.addCrustByCollision(tiSeg.X + (int)left, tiSeg.Y + (int)top, heightMap[tiSeg.I], timestampMap[tiSeg.I]);					

						// And remove it from this plate.
						M -= heightMap[tiSeg.I];
						heightMap[tiSeg.I] = 0;
					}
				}
			}

			segment.Area = 0;	// Mark segment as non-existent.
			return M_old - M;
		} catch (IllegalArgumentException e) {
			assert false: "Aggregating beyond plate limits!";
			return 0;			
		}
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
	 * @param deformedMass Amount of mass deformed in collision.
	 */
	void applyFriction(float deformedMass) {
		if (M > 0) {
			float dV = DEFORMATION_WEIGHT * deformedMass / M;
			
			Velocity -= dV;
			if (Velocity < 0) Velocity = 0; 
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
		
		// Calculate the normal to the curve/line at collision point.
		// The normal will point into plate B i.e. the "other" plate.
		//
		// Plates that wrap over world edges can mess the normal vector.
		// This could be solved by choosing the normal vector that points the
		// shortest path between mass centers but this causes problems when
		// plates are like heavy metal balls at a long rod and one plate's ball
		// collides at the further end of other plate's rod. Sure, this is
		// nearly never occurring situation but if we can easily do better then
		// why not do it?
		//
		// Better way is to select that normal vector that points along the
		// line that passes nearest the point of collision. Because point's
		// distance from line segment is relatively cumbersome to perform, the
		// vector is constructed as the sum of vectors <massCenterA, P> and
		// <P, massCenterB>. This solution works because collisions always
		// happen in the overlapping region of the two plates.

		assert worldTileIsOnPlate(worldX, worldY) && plate.worldTileIsOnPlate(worldX, worldY):
			String.format("@%d, %d: Out of colliding map's bounds!\n", worldX, worldY);
		
		Vector2f normal = Vector2f.add(				
				Vector2f.sub(
					new Vector2f(getLocalX(worldX), getLocalY(worldY)),
					new Vector2f(R_x, R_y),
					null),
				Vector2f.sub(
					new Vector2f(plate.R_x, plate.R_y),
					new Vector2f(plate.getLocalX(worldX), plate.getLocalY(worldY)),
					null),
				null);
		normal.normalise();

		// Compute relative velocity between plates at the collision point.
		// Because torque is not included, calc simplifies to v_ab = v_a - v_b.
		Vector2f relVelocity = Vector2f.sub(
				new Vector2f(vX, vY),
				new Vector2f(plate.vX, plate.vY),
				null);
		
		float dot = Vector2f.dot(relVelocity, normal);
		if (dot > 0) {
			//System.out.printf("n=%.2f, r=%.2f, %.2f, dot=%.4f\n", normalX, normalY, relVX, relVY, dotProduct);
			return;	// plates moving away from each other.
		}

		float J = -(1 + coefficientRestitution) * dot;
		J /= (invMass + 1f / collidingMass);
		
		Vector2f impulse = (Vector2f) normal.scale(J);

		dX += impulse.x * invMass;
		dY += impulse.y * invMass;
		plate.dX -= impulse.x * plate.invMass;
		plate.dY -= impulse.y * plate.invMass;
	}
	
	/**
	 * Apply plate wide erosion algorithm.
	 * 
	 * Plate's total mass and the center of mass are updated.
	 * 
	 * @param lowerBound Sets limit below which there's no erosion. (Is this height limit? Mass?)
	 */
	void erode(float lowerBound) {
		float tmp[] = new float[width * height];
		Arrays.fill(tmp, 0);
		M = R_x = R_y = 0;
		
		for (int i = 0; i < width * height; i++) {
			TileIndex ti = new TileIndex(i);
			M += heightMap[ti.I];
			tmp[ti.I] += heightMap[ti.I];
			
			// Update R (center of mass)
			R_x += ti.X * heightMap[ti.I];
			R_y += ti.Y * heightMap[ti.I];
			if (heightMap[ti.I] < lowerBound)
				continue;	// eroded too far already, no more
			
			// Collect tiles in the 4 cardinal directions with wrapping
			// when the plate is world-sized.
			Stack<TileIndex> tiles = new Stack<TileIndex>();
			if (width == mapSize || ti.X > 0)			// can go west
				tiles.Push(new TileIndex(ti.X - 1, ti.Y));
			if (width == mapSize || ti.X < width - 1)	// can go east
				tiles.Push(new TileIndex(ti.X + 1, ti.Y));
			if (height == mapSize || ti.Y > 0)			// can go north
				tiles.Push(new TileIndex(ti.X, ti.Y - 1));
			if (height == mapSize || ti.Y < height - 1)// can go south
				tiles.Push(new TileIndex(ti.X, ti.Y + 1));
			
			// Exclude tiles not part of the plate or taller than
			// the tile currently eroding.
			for (Iterator<TileIndex> iter = tiles.iterator(); iter.hasNext(); ) {
				TileIndex tiN = iter.next();
				if (heightMap[tiN.I] <= 0 || heightMap[tiN.I] > heightMap[ti.I])
					iter.remove();
			}
			
			// No tiles remain, either this tile has no neighbors or it is
			// the lowest part of its area.
			if (tiles.isEmpty())
				continue;
			
			// Find diff
			float minDiff = heightMap[ti.I];
			for (TileIndex tiN: tiles) {
				minDiff = Math.min(minDiff, heightMap[ti.I] - heightMap[tiN.I]);
			}
			float diffSum = 0;
			for (TileIndex tiN: tiles) {
				diffSum += heightMap[ti.I] - heightMap[tiN.I] - minDiff;
			}
			
			if (diffSum < minDiff) {
				// There's too much crust to erode nicely. So first
				// make all lower neighbors and this point equally
				// tall
				for (TileIndex tiN: tiles) {
					tmp[tiN.I] += heightMap[ti.I] - heightMap[tiN.I] - minDiff;
				}
				tmp[ti.I] -= minDiff;
				minDiff -= diffSum;
				minDiff /= 1 + tiles.size();
				// and then spread what's left equally among the lower
				// neighbors
				for (TileIndex tiN: tiles) {
					tmp[tiN.I] += minDiff;
				}
			} else if (diffSum > 0) {
				// Remove all crust from this location and make it as
				// tall as its tallest lower neighbor
				tmp[ti.I] -= minDiff;
				float unit = minDiff / diffSum;
				// and spread it evenly among all other lower neighbors
				for (TileIndex tiN: tiles) {
					tmp[tiN.I] += unit * (heightMap[ti.I] - heightMap[tiN.I] - minDiff);
				}
			}
		}
		// Save new eroded heights
		heightMap = tmp;

		// Normalize center of mass.
		if (M > 0) {
			R_x /= M;
			R_y /= M;
			invMass = 1f / M;
		} else {
			invMass = 0f;
			R_x = 0;
			R_y = 0;
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
		try {
			TileIndex tiColl = getLocalTile(worldX, worldY);
			int segID = segmentOwnerMap[tiColl.I];
			assert segID < collisionSegments.size(): "getCollisionInfo: no segment found!";
			
			CollisionSegment seg = collisionSegments.get(segID);				
			return new CollisionStatistic(seg.Collisions, seg.Collisions / (1.0f + seg.Area));		
		} catch (IllegalArgumentException e) {
			assert false: "getCollisionInfo: out of map bounds!";
			return null;
		}
	}
	  
	/**
	 * Retrieve the surface area of continent lying at desired location.
	 * 
	 * @param worldX X coordinate of collision point on world map.
	 * @param worldY Y coordinate of collision point on world map.
	 * @return Area of continent at desired location or 0 if none.
	 */
	int getContinentArea(int worldX, int worldY) {
		try {
			TileIndex tiCont = getLocalTile(worldX, worldY);
			assert segmentOwnerMap[tiCont.I] < collisionSegments.size(): "getContinentArea: no segment found!";  
			return collisionSegments.get(segmentOwnerMap[tiCont.I]).Area;
		} catch (IllegalArgumentException e) {
			assert false: "getContinentArea: out of map bounds!";
			return 0;
		}
	}
	
	/**
	 * Get the amount of plate's crustal material at some location.
	 * 
	 * @param worldX X coordinate on world map.
	 * @param worldY Y coordinate on world map.
	 * @return Amount of crust at requested location.
	 */
	float getCrust(int worldX, int worldY) {
		try {
			TileIndex tiCrust = getLocalTile(worldX, worldY);
			return heightMap[tiCrust.I];
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}
	
	/**
	 * Get the timestamp of plate's crustal material at some location.
	 * 
	 * @param worldX X coordinate on world map.
	 * @param worldY Y coordinate on world map.
	 * @return Timestamp of creation of crust at the location or 0 if no crust.
	 */
	int getCrustTimestamp(int worldX, int worldY) {
		try {
			TileIndex tiTime = getLocalTile(worldX, worldY);
			return timestampMap[tiTime.I];
		} catch (IllegalArgumentException e) {
			return 0;
		}
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
		// Apply any new impulses to the plate's trajectory.
		vX += dX; vY += dY;
		dX = dY = 0;

		// Force direction of plate to be unit vector.
		// Update velocity so that the distance of movement doesn't change.
		float len = (float)Math.sqrt(vX * vX + vY * vY);
		vX /= len;
		vY /= len;
		Velocity += len - 1.0f;
		if (Velocity < 0) Velocity = 0;	// Round negative values to zero.

		assert left >= 0 && left < mapSize && top >= 0 && top < mapSize: "Location coordinates out of world map bounds (PRE)!";
		
		left += vX * Velocity;
		if (left < 0) left += mapSize;
		if (left > mapSize) left -= mapSize;
		
		top += vY * Velocity;
		if (top < 0) top += mapSize;
		if (top > mapSize) top -= mapSize;

		assert left >= 0 && left < mapSize && top >= 0 && top < mapSize:
			String.format("Location coordinates out of world map bounds (POST)!\n%f, %f, %f; %f, %f\n",
					vX, vY, Velocity, left, top);
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
		Arrays.fill(segmentOwnerMap, Integer.MAX_VALUE);
		collisionSegments.clear();
	}
	
	/**
	 * Remember the currently processed continent's segment number.
	 * 
	 * @param worldX X coordinate of origin of collision on world map.
	 * @param worldY Y coordinate of origin of collision on world map.
	 */
	void selectCollisionSegment(int worldX, int worldY) {
		try {
			activeContinentID = segmentOwnerMap[getLocalTile(worldX, worldY).I];
			assert activeContinentID < collisionSegments.size():
				"Collision happened at unsegmented location!";
		} catch (IllegalArgumentException e) {
			assert false: "Collision segment cannot be set outside plate!";			
		}
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
		if (amount < 0)	// Do not accept negative values.
			amount = 0;
		
		if (!worldTileIsOnPlate(worldX, worldY)) {
			assert amount > 0 : "Extending plate for nothing!";
			
			int bound[] = new int[] {
				getLeft(), getTop(), (getLeft() + width - 1), (getTop() + height - 1)
			};
			worldX %= mapSize;
			worldY %= mapSize;	// just to be safe
			
			// Calculate distance of new point from plate edges.
			int distTmp[] = new int[] {
				bound[0] - worldX,
				bound[1] - worldY,
				worldX - bound[2] + (worldX < bound[0] ? mapSize : 0),	// dist from right side
				worldY - bound[3] + (worldY < bound[1] ? mapSize : 0) 	// dist from bottom side			
			};
			
			// Allow comparison of negative distances.
			for (int i = 0; i < distTmp.length; i++)
				distTmp[i] = distTmp[i] < 0 ? mapSize : distTmp[i];
			
			// Set larger of horizontal/vertical distance to zero.
			// A valid distance is NEVER larger than world's side's length!
			int dist[] = new int[] {
				distTmp[0] <  distTmp[2] && distTmp[0] < mapSize ? distTmp[0] : 0,
				distTmp[1] <  distTmp[3] && distTmp[1] < mapSize ? distTmp[1] : 0,
				distTmp[2] <= distTmp[0] && distTmp[2] < mapSize ? distTmp[2] : 0,
				distTmp[3] <= distTmp[1] && distTmp[3] < mapSize ? distTmp[3] : 0,
			};
			
			// Bounds changes forced to be in multiples of 8
			for (int i = 0; i < dist.length; i++) {
				dist[i] = ((dist[i] > 0 ? 1 : 0) + (dist[i] >> 3)) << 3;
			}
			
			// Prevent plate from growing larger than container.
			if (width + dist[0] + dist[2] > mapSize) {
				dist[0] = 0;
				dist[2] = mapSize - width;
			}
			if (height + dist[1] + dist[3] > mapSize) {
				dist[1] = 0;
				dist[3] = mapSize - height;
			}
			
			assert dist[0] + dist[1] + dist[2] + dist[3] > 0:
				String.format("Index out of bounds, but nowhere to grow!\n[%d, %d]x[%d, %d], [%d, %d]/[%d, %d]\n",
				(int)left, (int)top, (int)left+width, (int)top+height,
				worldX + (worldX<mapSize?mapSize:0), worldY+(worldY<mapSize?mapSize:0),
				worldX % mapSize, worldY % mapSize);
			
			int oldWidth = width, oldHeight = height;
			
			left -= dist[0];
			if (left < 0) left += mapSize;
			width += dist[0] + dist[2];
			
			top -= dist[1];
			if (top < 0) top += mapSize;
			height += dist[1] + dist[3];
			
			/* System.out.printf("%dx%d + [%d, %d] + [%d, %d] = %dx%d\n",
				oldWidth, oldHeight, dist[0], dist[1], dist[2], dist[3], width, height); */
			
			// Reallocate plate data storage
			float[] tmpHmap = new float[width * height];
			int[] tmpAmap = new int[width * height];
			int[] tmpSmap = new int[width * height];
			Arrays.fill(tmpHmap, 0f);
			Arrays.fill(tmpAmap, 0);
			Arrays.fill(tmpSmap, Integer.MAX_VALUE);
			
			// Copy existing data over
			for (int row = 0; row < oldHeight; row++) {
				int posDest = (int) ((dist[1] + row) * width + dist[0]);
				int posSrc = row * oldWidth;
				
				System.arraycopy(heightMap, posSrc, tmpHmap, posDest, oldWidth);
				System.arraycopy(segmentOwnerMap, posSrc, tmpSmap, posDest, oldWidth);
				System.arraycopy(timestampMap, posSrc, tmpAmap, posDest, oldWidth);
			}
			
			// Replace the old(now invalid) storage
			heightMap = tmpHmap;
			segmentOwnerMap = tmpSmap;
			timestampMap = tmpAmap;
			
			// Shift collision segment local coordinates
			for (CollisionSegment seg:collisionSegments) {
				seg.X0 += dist[0];
				seg.X1 += dist[0];
				seg.Y0 += dist[1];
				seg.Y1 += dist[1];
			}
			
			assert worldTileIsOnPlate(worldX, worldY):
				String.format("Index out of bounds after resize!\n[%d, %d]x[%d, %d], [%d, %d]/[%d, %d]\n",
						(int)left, (int)top, (int)left+width, (int)top+height, worldX, worldY, worldX % mapSize, worldY % mapSize);
		}
		
		TileIndex tiCrust = getLocalTile(worldX, worldY);
		if (amount > 0 && heightMap[tiCrust.I] > 0) {
			timestampMap[tiCrust.I] += timeStamp;
			timestampMap[tiCrust.I] /= 2;
		} else if (amount > 0) {
			timestampMap[tiCrust.I] = timeStamp;
		}
		// Update mass
		M -= heightMap[tiCrust.I];
		heightMap[tiCrust.I] = amount;
		M += amount;
		if (M > 0)
			invMass = 1f / M;
		else
			invMass = 0f;
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
	private int createSegment(TileIndex tiOrigin) {
		int newSegmentID = collisionSegments.size();
		
		// This tile already belongs to a collision segment
		if (segmentOwnerMap[tiOrigin.I] < newSegmentID)
			return segmentOwnerMap[tiOrigin.I];

		// Is a neighboring tile part of an existing collision segment?
		int adjSegmentID = checkNeighboringSegment(tiOrigin);
		if (adjSegmentID < newSegmentID)
			return adjSegmentID;
				
		segmentOwnerMap[tiOrigin.I] = newSegmentID;
		CollisionSegment newSegment = new CollisionSegment(tiOrigin.X, tiOrigin.Y, tiOrigin.X, tiOrigin.Y, 0);
		
		Stack<TileIndex> border = new Stack<TileIndex>();
		border.Push(tiOrigin);
		while (!border.IsEmpty()) {
			// choose random location on border 
			int borderIndex = rand.nextInt(border.size());
			TileIndex tiBord = border.Peek(borderIndex);
			
			Stack<TileIndex> tiles = new Stack<TileIndex>();
			if (width == mapSize || tiBord.X > 0)			// can go west
				tiles.Push(new TileIndex(tiBord.X - 1, tiBord.Y));
			if (width == mapSize || tiBord.X < width - 1)	// can go east
				tiles.Push(new TileIndex(tiBord.X + 1, tiBord.Y));
			if (height == mapSize || tiBord.Y > 0)			// can go north
				tiles.Push(new TileIndex(tiBord.X, tiBord.Y - 1));
			if (height == mapSize || tiBord.Y < height - 1)	// can go south
				tiles.Push(new TileIndex(tiBord.X, tiBord.Y + 1));
			
			for (TileIndex ti: tiles) {
				// If the N/S/E/W tile is un-owned, claim it for the active segment
				// and add it to the border.
				if (segmentOwnerMap[ti.I] > newSegmentID &&
					heightMap[ti.I] >= Lithosphere.CONTINENTAL_BASE) {
					border.Push(ti);
					newSegment.Area++;
					newSegment.UpdateBoundsToInclude(ti.X, ti.Y);
					segmentOwnerMap[ti.I] = newSegmentID;
				}
			}
			// Overwrite processed point in border with last item from border
			border.set(borderIndex, border.Peek());
			border.Pop();			
		}
		
		/* if (newSegment.Area > 0)
			System.out.printf("New segment created, ID %d. [%d,%d]-[%d,%d](%dx%d) @ (%d,%d).\n",
				newSegmentID,
				newSegment.X0, newSegment.Y0, newSegment.X1, newSegment.Y1, newSegment.getW(), newSegment.getH(), localX, localY); */
		
		collisionSegments.addElement(newSegment);		
		return newSegmentID;
	}
	
	private int checkNeighboringSegment(TileIndex tiOrigin) {
		int segNew = collisionSegments.size();
		int segNeighbor = segNew;

		Stack<TileIndex> tiles = new Stack<TileIndex>();
		if (width == mapSize || tiOrigin.X > 0)				// can go west
			tiles.Push(new TileIndex(tiOrigin.X - 1, tiOrigin.Y));
		if (width == mapSize || tiOrigin.X < width - 1)		// can go east
			tiles.Push(new TileIndex(tiOrigin.X + 1, tiOrigin.Y));
		if (height == mapSize || tiOrigin.Y > 0)			// can go north
			tiles.Push(new TileIndex(tiOrigin.X, tiOrigin.Y - 1));
		if (height == mapSize || tiOrigin.Y < height - 1)	// can go south
			tiles.Push(new TileIndex(tiOrigin.X, tiOrigin.Y + 1));
		
		for (TileIndex ti: tiles) {
			if (heightMap[ti.I] >= Lithosphere.CONTINENTAL_BASE &&
				segmentOwnerMap[ti.I] < segNew) {
				segNeighbor = segmentOwnerMap[ti.I];
				segmentOwnerMap[tiOrigin.I] = segNeighbor;
				collisionSegments.get(segNeighbor).Area++;
				collisionSegments.get(segNeighbor).UpdateBoundsToInclude(ti.X, ti.Y);
				break;
			}
		}
		return segNeighbor;
	}

	/**
	 * Translate world coordinates into offset within plate's height map.
	 * 
	 * @param x X coordinate on world map.
	 * @param y Y coordinate on world map.
	 * @return Index into local heightmap.
	 */
	public TileIndex getLocalTile(int worldX, int worldY) {		
		return new TileIndex(getLocalX(worldX), getLocalY(worldY));
	}
	public int getLocalX(int worldX) {
		worldX %= mapSize;                    // scale within map dimensions
		if (worldX < (int)left) worldX += mapSize; // wrap around world edge if necessary
		return worldX - (int)left;
	}
	public int getLocalY(int worldY) {
		worldY %= mapSize;                    // scale within map dimensions
		if (worldY < (int)top) worldY += mapSize;  // wrap around world edge if necessary
		return worldY - (int)top;
	}
	private boolean worldTileIsOnPlate(int worldX, int worldY) {
		try {
			TileIndex ti = getLocalTile(worldX, worldY);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
