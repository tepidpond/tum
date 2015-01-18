package com.tepidpond.tum;

import com.tepidpond.tum.PlateTectonics.Lithosphere;

public class G {
	public static final String ModID = "TUM";
	public static final String ModName = "The Unknown Mod";
	
	// see http://en.wikipedia.org/wiki/Linear_congruential_generator
	public static final long LCG_Mult_A = 6364136223846793005L;
	public static final long LCG_Inc_C = 1442695040888963407L;
	
	public class WorldGen {
		public static final int   DefaultMapSize = 512;
		public static final float DefaultLandSeaRatio = 0.65f;
		public static final int   DefaultErosionPeriod = 60;
		public static final float DefaultFoldingRatio = 0.001f;
		public static final int   DefaultAggrRatioAbs = 5000;
		public static final float DefaultAggrRatioRel = 0.1f;
		public static final int   DefaultMaxCycles = 2;
		public static final int   DefaultNumPlates = 10;
		public static final int   DefaultMaxGens = 600;
	}
}
