package com.tepidpond.tum.PlateTectonics;

import java.awt.Rectangle;
import java.util.Vector;

public class PlateArea {
	public Vector border = new Vector(2048);
	public Rectangle boundingBox = new Rectangle();
	public double rgt() { return boundingBox.getMaxX(); }
	public double lft() { return boundingBox.getMinX(); }
	public double btm() { return boundingBox.getMaxY(); }
	public double top() { return boundingBox.getMinY(); }
	public double wdt() { return boundingBox.getWidth(); }
	public double hgt() { return boundingBox.getHeight(); }	
	public PlateArea(int x, int y, int w, int h) {
		boundingBox = new Rectangle(x, y, w, h);
	}
}
