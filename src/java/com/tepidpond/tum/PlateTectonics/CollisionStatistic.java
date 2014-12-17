package com.tepidpond.tum.PlateTectonics;

public class CollisionStatistic {
	public int Collisions = 0;
	public float CollidedRatio = 0.0f;
	public CollisionStatistic(int count, float ratio) {
		this.Collisions = count;
		this.CollidedRatio = ratio;
	}
}
