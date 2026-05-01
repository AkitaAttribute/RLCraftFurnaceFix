package com.rlcraft.furnacexp.asm;

import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

public final class FurnaceXpHooks {
    private static final String NBT_KEY = "rlcraftfurnacefix.stored_xp_precise";
    private static final String NBT_KEY_AUTO_ITEMS = "rlcraftfurnacefix.auto_smelted_items";
    private static final String FIELD_NAME = "rlcraftfurnacefix$storedXp";
    private static final String FIELD_AUTO_ITEMS = "rlcraftfurnacefix$autoSmeltedItems";
    private static final ThreadLocal<Integer> PLAYER_EXTRACT_DEPTH = new ThreadLocal<Integer>();
    private static final Logger LOGGER = LogManager.getLogger("RLCraftFurnaceFix");
    private static final double MAX_REASONABLE_XP_PER_ITEM = 0.1D;
    private static final double BASE_REASONABLE_XP_BUFFER = 100.0D;
    private static final double ABSOLUTE_STORED_XP_CEILING = 10000.0D;

    private FurnaceXpHooks() {
    }

    public static void beginPlayerExtract() {
        Integer depth = PLAYER_EXTRACT_DEPTH.get();
        PLAYER_EXTRACT_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void endPlayerExtract() {
        Integer depth = PLAYER_EXTRACT_DEPTH.get();
        if (depth == null || depth <= 1) {
            PLAYER_EXTRACT_DEPTH.remove();
        } else {
            PLAYER_EXTRACT_DEPTH.set(depth - 1);
        }
    }

    public static void onFurnaceDecrStack(TileEntityFurnace furnace, int index, ItemStack extracted) {
        if (furnace == null || furnace.getWorld() == null || furnace.getWorld().isRemote || index != 2 || extracted == null || extracted.isEmpty()) {
            return;
        }

        if (isPlayerExtractContext()) {
            return;
        }

        double xp = calculateSmeltingXp(extracted, extracted.getCount());
        if (xp > 0.0D) {
            setStoredXp(furnace, getStoredXp(furnace) + xp);
            setAutoSmeltedItems(furnace, getAutoSmeltedItems(furnace) + extracted.getCount());
        }
    }

    public static void onFurnaceRemoveStackFromSlot(TileEntityFurnace furnace, int index, ItemStack extracted) {
        onFurnaceDecrStack(furnace, index, extracted);
    }

    private static boolean isPlayerExtractContext() {
        return PLAYER_EXTRACT_DEPTH.get() != null;
    }

    public static void onReadFromNbt(TileEntityFurnace furnace, NBTTagCompound nbt) {
        if (furnace == null || nbt == null) {
            return;
        }
        double loadedStoredXp = nbt.getDouble(NBT_KEY);
        int loadedAutoSmeltedItems = nbt.getInteger(NBT_KEY_AUTO_ITEMS);
        BlockPos pos = furnace.getPos();
        LOGGER.info("Furnace XP NBT load: storedXp={}, autoSmeltedItems={} @ {}", loadedStoredXp, loadedAutoSmeltedItems, pos);

        double maxReasonableStoredXp = (Math.max(0, loadedAutoSmeltedItems) * MAX_REASONABLE_XP_PER_ITEM) + BASE_REASONABLE_XP_BUFFER;
        if (loadedStoredXp > ABSOLUTE_STORED_XP_CEILING || loadedStoredXp > maxReasonableStoredXp) {
            LOGGER.warn("Furnace XP NBT load sanity guard triggered: storedXp={} exceeds limit={} (autoSmeltedItems={}) @ {}. Resetting stored XP to 0.",
                    loadedStoredXp, Math.min(ABSOLUTE_STORED_XP_CEILING, maxReasonableStoredXp), loadedAutoSmeltedItems, pos);
            loadedStoredXp = 0.0D;
        }

        setStoredXp(furnace, loadedStoredXp);
        setAutoSmeltedItems(furnace, loadedAutoSmeltedItems);
    }

    public static void onWriteToNbt(TileEntityFurnace furnace, NBTTagCompound nbt) {
        if (furnace == null || nbt == null) {
            return;
        }

        double stored = getStoredXp(furnace);
        if (stored > 0.0D) {
            nbt.setDouble(NBT_KEY, stored);
        } else {
            nbt.removeTag(NBT_KEY);
        }

        int autoSmeltedItems = getAutoSmeltedItems(furnace);
        if (autoSmeltedItems > 0) {
            nbt.setInteger(NBT_KEY_AUTO_ITEMS, autoSmeltedItems);
        } else {
            nbt.removeTag(NBT_KEY_AUTO_ITEMS);
        }
    }

    public static void onOutputSlotCrafted(EntityPlayer player, IInventory inventory) {
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        if (!(inventory instanceof TileEntityFurnace)) {
            return;
        }
        payoutStoredXp((TileEntityFurnace) inventory, player, player.world, player.posX, player.posY + 0.5D, player.posZ);
    }

    private static void payoutStoredXp(TileEntityFurnace furnace, EntityPlayer player, World world, double x, double y, double z) {
        double rawStoredXp = getStoredXp(furnace);
        int payoutXp = toVanillaExperience(rawStoredXp);
        int autoSmeltedItems = getAutoSmeltedItems(furnace);
        String debugMessage = formatPayoutDebugMessage(rawStoredXp, payoutXp, autoSmeltedItems);
        if (player != null) {
            player.sendMessage(new TextComponentString(debugMessage));
        }

        BlockPos pos = furnace != null ? furnace.getPos() : null;
        LOGGER.info("{}{}", debugMessage, pos == null ? "" : " @ " + pos);

        int stored = toVanillaExperience(drainStoredXp(furnace));
        while (stored > 0) {
            int split = EntityXPOrb.getXPSplit(stored);
            stored -= split;
            world.spawnEntity(new EntityXPOrb(world, x, y, z, split));
        }
    }

    private static String formatPayoutDebugMessage(double rawStoredXp, int payoutXp, int autoSmeltedItems) {
        return String.format("Furnace XP Debug: raw=%.2f, payout=%d xp, approxLevels=%.2f, autoSmeltedItems=%d",
                rawStoredXp, payoutXp, approximateLevelFromTotalXp(payoutXp), autoSmeltedItems);
    }

    private static double approximateLevelFromTotalXp(int totalXp) {
        if (totalXp <= 0) {
            return 0.0D;
        }
        if (totalXp <= 352) {
            return (-6.0D + Math.sqrt(36.0D + (4.0D * totalXp))) / 2.0D;
        }
        if (totalXp <= 1507) {
            return (40.5D + Math.sqrt(40.5D * 40.5D - 10.0D * (360.0D - totalXp))) / 5.0D;
        }
        return (162.5D + Math.sqrt(162.5D * 162.5D - 18.0D * (2220.0D - totalXp))) / 9.0D;
    }

    private static double calculateSmeltingXp(ItemStack stack, int count) {
        float experience = FurnaceRecipes.instance().getSmeltingExperience(stack);
        if (experience <= 0.0F) {
            return 0.0D;
        }
        return count * (double) experience;
    }

    private static int toVanillaExperience(double stored) {
        if (stored <= 0.0D) {
            return 0;
        }
        int floor = MathHelper.floor(stored);
        if (floor < MathHelper.ceil(stored) && Math.random() < stored - floor) {
            floor++;
        }
        return floor;
    }

    private static double getStoredXp(TileEntityFurnace furnace) {
        try {
            return furnace.getClass().getField(FIELD_NAME).getDouble(furnace);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private static void setStoredXp(TileEntityFurnace furnace, double value) {
        try {
            Field field = furnace.getClass().getField(FIELD_NAME);
            field.setDouble(furnace, Math.max(0.0D, value));
        } catch (Throwable ignored) {
        }
    }

    private static int getAutoSmeltedItems(TileEntityFurnace furnace) {
        try {
            return furnace.getClass().getField(FIELD_AUTO_ITEMS).getInt(furnace);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void setAutoSmeltedItems(TileEntityFurnace furnace, int value) {
        try {
            Field field = furnace.getClass().getField(FIELD_AUTO_ITEMS);
            field.setInt(furnace, Math.max(0, value));
        } catch (Throwable ignored) {
        }
    }

    private static double drainStoredXp(TileEntityFurnace furnace) {
        double value = getStoredXp(furnace);
        setStoredXp(furnace, 0.0D);
        setAutoSmeltedItems(furnace, 0);
        return value;
    }
}
