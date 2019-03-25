package bq_standard.handlers;

import bq_standard.client.gui.GuiBQSConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

public class ConfigGuiFactory implements IModGuiFactory
{
	@Override
	public void initialize(Minecraft minecraftInstance)
	{
	}
    
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass()
    {
        return GuiBQSConfig.class;
    }
    
    @Override
	public Set<RuntimeOptionCategoryElement> runtimeGuiCategories()
	{
		return null;
	}
    
    @Override
    @Deprecated
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element)
    {
        return null;
    }
}
