package com.tepidpond.tum.PlateTectonics;

import java.awt.Rectangle;
import java.util.Vector;

public class PlateArea {
	public Vector<Integer> border = new Vector(2048);
	public int x0, y0, x1, y1;
	public int rgt() { return x1; }
	public int lft() { return x0; }
	public int btm() { return y1; }
	public int top() { return y0; }
	public int wdt() { return x1 - x0; }
	public int hgt() { return y1 - y0; }	
	public PlateArea(int x, int y, int w, int h) {
		x0 = x; x1 = x0 + w;
		y0 = y; y1 = y0 + h;
	}
}
