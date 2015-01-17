package com.tepidpond.tum;

import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;

import com.tepidpond.tum.PlateTectonics.Lithosphere;
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
    	for (int i=0; i<6000; i++) {
    		lithos.Update();
    	}
    }
    
    @EventHandler
    public void preInit(FMLInitializationEvent event)
    {
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
