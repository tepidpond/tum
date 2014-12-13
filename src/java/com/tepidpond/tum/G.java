package com.tepidpond.tum;

public class G {
	public static final String ModID = "TUM";
	public static final String ModName = "The Unknown Mod";
	
	// see http://en.wikipedia.org/wiki/Linear_congruential_generator
	public static final long LCG_Mult_A = 6364136223846793005L;
	public static final long LCG_Inc_C = 1442695040888963407L;
	
	public G() {
		// TODO Auto-generated constructor stub
	}
	
	public static final int getTile(int x, int y, int mapSize) {
		return (y * mapSize + x);
	}
	public static final int getX(int mapTile, int mapSize) {
		return mapTile - (mapTile % mapSize);
	}
	public static final int getY(int mapTile, int mapSize) {
		return mapTile / mapSize;
	}
}
