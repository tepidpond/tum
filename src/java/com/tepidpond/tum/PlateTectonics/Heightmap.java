package com.tepidpond.tum.PlateTectonics;

import java.lang.reflect.Array;

public class Heightmap<T> {
	private int Xoffset, Yoffset;
	private int mapWidth, mapHeight;
	private int mapArea;
	private Heightmap parent;
	private T storage[];
	
	public Heightmap(Class<T> cls, Heightmap Parent, int parentXOrigin, int parentYOrigin, int mapWidth, int mapHeight) {
		if (Parent != null) {
			if (Parent.getParent() != null) {
				Parent = Parent.getParent();
				parentXOrigin += Parent.getX0();
				parentYOrigin += Parent.getY0();
			}
		}
			
		this.Xoffset = parentXOrigin;
		this.Yoffset = parentYOrigin;
		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;
		this.mapArea = mapWidth * mapHeight;
		this.storage = (T[])Array.newInstance(cls, mapArea);	
	}

	public int getX0() {return Xoffset;}
	public int getX1() {return Xoffset + mapWidth - 1;}
	public int getY0() {return Yoffset;}
	public int getY1() {return Yoffset + mapHeight - 1;}
	
	public int getArea() {return mapWidth * mapHeight;}
	public int getLocalX(int parentX) {return 0;}
	public int getLocalY(int parentX) {return 0;}
	public int getParentX(int localX) {return 0;}
	public int getParentY(int localY) {return 0;}
	public Heightmap<T> getParent() {return parent;}
}
