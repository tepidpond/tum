package com.tepidpond.tum;

import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;

import com.tepidpond.tum.PlateTectonics.Lithosphere;
import com.tepidpond.tum.PlateTectonics.Util;
import com.tepidpond.tum.WorldGen.TUMProvider;
import com.tepidpond.tum.WorldGen.TUMWorldType;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = TUM.MODID, version = TUM.VERSION)
public class TUM
{
    public static final String MODID = G.ModID;
    public static final String VERSION = "${version}";
    public static WorldType TUMWorldType = new TUMWorldType("TUMDefault");
    
    public static void main(String[] args) {
       	Lithosphere lithos = new Lithosphere(
   			512,	// world size
   			0.65f,	// land/sea ratio
   			60,		// generations between erosion calls
   			0.001f,	// folding ratio
   			5000,	// overlapping tiles causing aggregation
   			0.1f,	// overlapping tile ratio causing aggregation
   			2,		// number of plate recreation cycles
   			10,		// number of plates to create
   			0);		// creation seed
		Util.displayHeightmap(0, lithos.getHeightmap(), lithos.getMapSize(), lithos.getMapSize(), "Terrain");
		
		int mapScaled = 512;
		float scaleFactor = 1f/16f;
		float hm2[] = new float[mapScaled * mapScaled];
		for (int i = 0; i < hm2.length; i++) {
			float x = (i % mapScaled) * scaleFactor; float y = (i / mapScaled) * scaleFactor;
			hm2[i] = Util.quadInterpolate(lithos.getHeightmap(), lithos.getMapSize(), 128f+x, 128f+y);
		}
		Util.displayHeightmap(1, hm2, mapScaled, mapScaled, "Scaling test");
    }
    
    @EventHandler
    public void preInit(FMLInitializationEvent event)
    {
    	// This is only possible because of tfc_at.cfg and the build.gradle line that calls it.
    	WorldType.DEFAULT = new TUMWorldType("TUMDefault");

    	DimensionManager.unregisterDimension(-1);
    	DimensionManager.unregisterDimension(0);
    	DimensionManager.unregisterDimension(1);
    	DimensionManager.unregisterProviderType(-1);
    	DimensionManager.unregisterProviderType(0);
    	DimensionManager.unregisterProviderType(1);
    	
    	DimensionManager.registerProviderType(0, TUMProvider.class, true);
    	DimensionManager.registerDimension(0, 0);
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
    }
    
    @EventHandler
    public void load(FMLInitializationEvent event)
    {
    	MinecraftForge.EVENT_BUS.register(new FarTerrainRenderer(FMLClientHandler.instance().getClient().getMinecraft()));
    }
}
