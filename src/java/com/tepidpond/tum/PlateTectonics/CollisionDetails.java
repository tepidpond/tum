package com.tepidpond.tum.PlateTectonics;

public class CollisionDetails {
	private int plateIndex;
	private int worldX, worldY;
	private float amountCrust;
	
	public CollisionDetails(int plateIndex, int worldX, int worldY, float amountCrust) {
		assert amountCrust > 0: "Impossible collision.";
		
		this.plateIndex = plateIndex;
		this.worldX = worldX;
		this.worldY = worldY;
		this.amountCrust = amountCrust;
		//System.out.printf("Saving collision details for (%d,%d) on plate %d. Crust collided = %f.\n", worldX, worldY, plateIndex, amountCrust);
	}
	
	public float getCrust() {return amountCrust;}
	public int getIndex() { return plateIndex; }
	public int getX() { return worldX; }
	public int getY() { return worldY; }

}
