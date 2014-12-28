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
    	Lithosphere lithos = new Lithosphere(512, 0.65f, 60, 0.001f, 5000, 0.1f, 2, 10, 0);
    	for (int i=0; i<256; i++) {
    		lithos.Update();
    	}
    	System.exit(0);
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
