package com.tepidpond.tum.PlateTectonics;

import java.lang.reflect.Array;

public class Heightmap<T> {
	private int Xoffset, Yoffset;
	private int mapWidth, mapHeight;
	private int mapArea;
	private Heightmap parent;
	private T storage[];
	
	public Heightmap(Class<T> cls, Heightmap Parent, int xOffset, int yOffset, int mapWidth, int mapHeight) {
		this.Xoffset = xOffset;
		this.Yoffset = yOffset;
		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;
		this.mapArea = mapWidth * mapHeight;
		this.storage = (T[])Array.newInstance(cls, mapArea);	
	}

	public int getX0() {return Xoffset;}
	public int getX1() {return Xoffset + mapWidth;}
	public int getY0() {return Yoffset;}
	public int getY1() {return Yoffset + mapHeight;}
	
	public int getArea() {return mapWidth * mapHeight;}

}
