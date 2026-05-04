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
    private static final double MAX_REASONABLE_XP_PER_ITEM = 10.0D;
    private static final double RESET_XP_PER_ITEM = 0.1D;

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
            applyMutation(furnace, "onFurnaceDecrStack", xp, extracted.getCount());
        }
    }

    public static void onFurnaceRemoveStackFromSlot(TileEntityFurnace furnace, int index, ItemStack extracted) {
        if (furnace != null && extracted != null && !extracted.isEmpty() && index == 2) {
            LOGGER.info("Furnace XP mutation hook=onFurnaceRemoveStackFromSlot forwarding count={} @ {}", extracted.getCount(), furnace.getPos());
        }
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

        double maxReasonableStoredXp = Math.max(0, loadedAutoSmeltedItems) * MAX_REASONABLE_XP_PER_ITEM;
        if (loadedStoredXp > maxReasonableStoredXp) {
            double resetValue = Math.max(0, loadedAutoSmeltedItems) * RESET_XP_PER_ITEM;
            String warning = String.format("Furnace XP NBT sanity issue: raw=%.2f, autoSmeltedItems=%d, maxAllowed=%.2f @ %s. Resetting stored XP to %.2f.",
                    loadedStoredXp, loadedAutoSmeltedItems, maxReasonableStoredXp, pos, resetValue);
            LOGGER.warn(warning);
            sendWorldDebugMessage(furnace.getWorld(), warning);
            loadedStoredXp = resetValue;
        }

        double oldXp = getStoredXp(furnace);
        int oldItems = getAutoSmeltedItems(furnace);
        setStoredXp(furnace, loadedStoredXp);
        setAutoSmeltedItems(furnace, loadedAutoSmeltedItems);
        logMutation("onReadFromNbt", furnace, oldXp, loadedStoredXp, oldItems, loadedAutoSmeltedItems);
    }

    private static void sendWorldDebugMessage(World world, String message) {
        if (world == null || world.isRemote || message == null || message.isEmpty()) {
            return;
        }
        for (EntityPlayer player : world.playerEntities) {
            if (player != null) {
                player.sendMessage(new TextComponentString(message));
            }
        }
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
        logMutation("onWriteToNbt(snapshot)", furnace, stored, stored, autoSmeltedItems, autoSmeltedItems);
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
        int autoSmeltedItems = getAutoSmeltedItems(furnace);
        double maxReasonableStoredXp = Math.max(0, autoSmeltedItems) * MAX_REASONABLE_XP_PER_ITEM;
        if (rawStoredXp > maxReasonableStoredXp) {
            double corrected = Math.max(0, autoSmeltedItems) * RESET_XP_PER_ITEM;
            String warning = String.format("Furnace XP payout sanity issue: raw=%.2f, autoSmeltedItems=%d, maxAllowed=%.2f @ %s. Using %.2f for payout.",
                    rawStoredXp, autoSmeltedItems, maxReasonableStoredXp, furnace != null ? furnace.getPos() : null, corrected);
            LOGGER.warn(warning);
            sendWorldDebugMessage(world, warning);
            rawStoredXp = corrected;
            setStoredXp(furnace, corrected);
        }
        int payoutXp = toVanillaExperience(rawStoredXp);
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
            double old = getStoredXp(furnace);
            Field field = furnace.getClass().getField(FIELD_NAME);
            double next = Math.max(0.0D, value);
            field.setDouble(furnace, next);
            LOGGER.info("Furnace XP direct set hook=setStoredXp oldXp={} deltaXp={} newXp={} oldItems={} deltaItems=0 newItems={} @ {}",
                    old, next - old, next, getAutoSmeltedItems(furnace), getAutoSmeltedItems(furnace), furnace.getPos());
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
            int old = getAutoSmeltedItems(furnace);
            Field field = furnace.getClass().getField(FIELD_AUTO_ITEMS);
            int next = Math.max(0, value);
            field.setInt(furnace, next);
            LOGGER.info("Furnace XP direct set hook=setAutoSmeltedItems oldXp={} deltaXp=0 newXp={} oldItems={} deltaItems={} newItems={} @ {}",
                    getStoredXp(furnace), getStoredXp(furnace), old, (next - old), next, furnace.getPos());
        } catch (Throwable ignored) {
        }
    }

    private static double drainStoredXp(TileEntityFurnace furnace) {
        double oldXp = getStoredXp(furnace);
        int oldItems = getAutoSmeltedItems(furnace);
        double value = oldXp;
        setStoredXp(furnace, 0.0D);
        setAutoSmeltedItems(furnace, 0);
        logMutation("drainStoredXp", furnace, oldXp, 0.0D, oldItems, 0);
        return value;
    }

    private static void applyMutation(TileEntityFurnace furnace, String hook, double xpDelta, int itemDelta) {
        double oldXp = getStoredXp(furnace);
        int oldItems = getAutoSmeltedItems(furnace);
        double newXp = Math.max(0.0D, oldXp + xpDelta);
        int newItems = Math.max(0, oldItems + itemDelta);
        setStoredXp(furnace, newXp);
        setAutoSmeltedItems(furnace, newItems);
        logMutation(hook, furnace, oldXp, newXp, oldItems, newItems);
        checkRuntimeSanity(hook, furnace, newXp, newItems);
    }

    private static void checkRuntimeSanity(String hook, TileEntityFurnace furnace, double storedXp, int autoSmeltedItems) {
        double max = Math.max(0, autoSmeltedItems) * MAX_REASONABLE_XP_PER_ITEM;
        if (storedXp > max) {
            String msg = String.format("Furnace XP runtime sanity issue hook=%s: raw=%.2f, autoSmeltedItems=%d, maxAllowed=%.2f @ %s",
                    hook, storedXp, autoSmeltedItems, max, furnace != null ? furnace.getPos() : null);
            LOGGER.warn(msg);
            sendWorldDebugMessage(furnace != null ? furnace.getWorld() : null, msg);
        }
    }

    private static void logMutation(String hook, TileEntityFurnace furnace, double oldXp, double newXp, int oldItems, int newItems) {
        LOGGER.info("Furnace XP mutation hook={} oldXp={} deltaXp={} newXp={} oldItems={} deltaItems={} newItems={} @ {}",
                hook, oldXp, newXp - oldXp, newXp, oldItems, newItems - oldItems, newItems, furnace != null ? furnace.getPos() : null);
    }
}
