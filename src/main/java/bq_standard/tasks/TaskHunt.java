package bq_standard.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.cache.CapabilityProviderQuestCache;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import bq_standard.client.gui.editors.tasks.GuiEditTaskHunt;
import bq_standard.client.gui.tasks.PanelTaskHunt;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.factory.FactoryTaskHunt;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.util.*;

public class TaskHunt implements ITask
{
	private final Set<UUID> completeUsers = new TreeSet<>();
	private final HashMap<UUID, Integer> userProgress = new HashMap<>();
	public String idName = "minecraft:zombie";
	public String damageType = "";
	public int required = 1;
	public boolean ignoreNBT = true;
	public boolean subtypes = true;
	
	/**
	 * NBT representation of the intended target. Used only for NBT comparison checks
	 */
	public NBTTagCompound targetTags = new NBTTagCompound();
	
	@Override
	public ResourceLocation getFactoryID()
	{
		return FactoryTaskHunt.INSTANCE.getRegistryName();
	}
	
	@Override
	public boolean isComplete(UUID uuid)
	{
		return completeUsers.contains(uuid);
	}
	
	@Override
	public void setComplete(UUID uuid)
	{
		completeUsers.add(uuid);
	}
	
	@Override
	public String getUnlocalisedName()
	{
		return BQ_Standard.MODID + ".task.hunt";
	}
	
	@Override
	public void detect(EntityPlayer player, IQuest quest)
	{
	    UUID playerID = QuestingAPI.getQuestingUUID(player);
	    
		if(isComplete(playerID)) return;
		
		int progress = getUsersProgress(playerID);
		
		if(progress >= required) setComplete(playerID);
	}
	
	public void onKilledByPlayer(DBEntry<IQuest> quest, EntityPlayer player, EntityLivingBase entity, DamageSource source)
	{
		UUID playerID = QuestingAPI.getQuestingUUID(player);
        QuestCache qc = player.getCapability(CapabilityProviderQuestCache.CAP_QUEST_CACHE, null);
		
		if(entity == null || this.isComplete(playerID)) return;
		
		if(damageType.length() > 0 && (source == null || !damageType.equalsIgnoreCase(source.damageType))) return;
		
		int progress = getUsersProgress(playerID);
		
		Class<? extends Entity> subject = entity.getClass();
		ResourceLocation targetID = new ResourceLocation(idName);
		Class<? extends Entity> target = EntityList.getClass(targetID);
		ResourceLocation subjectID = EntityList.getKey(subject);
		
		if(subjectID == null || target == null)
		{
			return; // Missing necessary data
		} else if(subtypes && !target.isAssignableFrom(subject))
		{
			return; // This is not the intended target or sub-type
		} else if(!subtypes && !subjectID.equals(targetID))
		{
			return; // This isn't the exact target required
		}
		
		NBTTagCompound subjectTags = new NBTTagCompound();
		entity.writeToNBTOptional(subjectTags);
		if(!ignoreNBT && !ItemComparison.CompareNBTTag(targetTags, subjectTags, true))
		{
			return;
		}
		
		setUserProgress(playerID, progress + 1);
		if(qc != null) qc.markQuestDirty(quest.getID());
		
		detect(player, quest.getValue());
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound json)
	{
		json.setString("target", idName);
		json.setInteger("required", required);
		json.setBoolean("subtypes", subtypes);
		json.setBoolean("ignoreNBT", ignoreNBT);
		json.setTag("targetNBT", targetTags);
		json.setString("damageType", damageType);
		
		return json;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound json)
	{
		idName = json.getString("target");
		required = json.getInteger("required");
		subtypes = json.getBoolean("subtypes");
		ignoreNBT = json.getBoolean("ignoreNBT");
		targetTags = json.getCompoundTag("targetNBT");
		damageType = json.getString("damageType");
	}
	
	@Override
	public void readProgressFromNBT(NBTTagCompound nbt, boolean merge)
	{
		if(!merge)
        {
            completeUsers.clear();
            userProgress.clear();
        }
		
		NBTTagList cList = nbt.getTagList("completeUsers", 8);
		for(int i = 0; i < cList.tagCount(); i++)
		{
			try
			{
				completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
			} catch(Exception e)
			{
				BQ_Standard.logger.log(Level.ERROR, "Unable to load UUID for task", e);
			}
		}
		
		NBTTagList pList = nbt.getTagList("userProgress", 10);
		for(int n = 0; n < pList.tagCount(); n++)
		{
			try
			{
                NBTTagCompound pTag = pList.getCompoundTagAt(n);
                UUID uuid = UUID.fromString(pTag.getString("uuid"));
                userProgress.put(uuid, pTag.getInteger("value"));
			} catch(Exception e)
			{
				BQ_Standard.logger.log(Level.ERROR, "Unable to load user progress for task", e);
			}
		}
	}
	
	@Override
	public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users)
	{
		NBTTagList jArray = new NBTTagList();
		NBTTagList progArray = new NBTTagList();
		
		if(users != null)
        {
            users.forEach((uuid) -> {
                if(completeUsers.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));
                
                Integer data = userProgress.get(uuid);
                if(data != null)
                {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    pJson.setInteger("value", data);
                    progArray.appendTag(pJson);
                }
            });
        } else
        {
            completeUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));
            
            userProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
			    pJson.setString("uuid", uuid.toString());
                pJson.setInteger("value", data);
                progArray.appendTag(pJson);
            });
        }
		
		nbt.setTag("completeUsers", jArray);
		nbt.setTag("userProgress", progArray);
		
		return nbt;
	}
	
	/**
	 * Returns a new editor screen for this Reward type to edit the given data
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public GuiScreen getTaskEditor(GuiScreen parent, IQuest quest)
	{
	    return new GuiEditTaskHunt(parent, quest, this);
	}

	@Override
	public void resetUser(@Nullable UUID uuid)
	{
	    if(uuid == null)
        {
            completeUsers.clear();
            userProgress.clear();
        } else
        {
            completeUsers.remove(uuid);
            userProgress.remove(uuid);
        }
	}
 
	@Override
	@SideOnly(Side.CLIENT)
	public IGuiPanel getTaskGui(IGuiRect rect, IQuest quest)
	{
	    return new PanelTaskHunt(rect, this);
	}
	
	private void setUserProgress(UUID uuid, int progress)
	{
		userProgress.put(uuid, progress);
	}
	
	public int getUsersProgress(UUID uuid)
	{
        Integer n = userProgress.get(uuid);
        return n == null? 0 : n;
	}
}
