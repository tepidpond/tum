package com.tepidpond.tum.PlateTectonics;

public class CollisionDetails {
	private int plateIndex;
	private int worldX, worldY;
	private float amountCrust;
	
	public CollisionDetails(int plateIndex, int worldX, int worldY, float amountCrust) {
		this.plateIndex = plateIndex;
		this.worldX = worldX;
		this.worldY = worldY;
		this.amountCrust = amountCrust;
	}
	
	public float getCrust() {return amountCrust;}
	public int getIndex() { return plateIndex; }
	public int getX() { return worldX; }
	public int getY() { return worldY; }

}
