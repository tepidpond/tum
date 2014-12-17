package com.tepidpond.tum.PlateTectonics;

public class CollisionSegment {
	public int X0, X1, Y0, Y1, Area, Collisions;
	public CollisionSegment(int x0, int y0, int x1, int y1, int area) {
		X0 = x0; Y0 = y0;
		X1 = x1; Y1 = y1;
		Area = area;
		Collisions = 0;
	}

}
