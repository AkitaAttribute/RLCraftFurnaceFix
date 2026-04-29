package com.rlcraft.furnacexp.asm;

import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class FurnaceXpHooks {
    private static final String NBT_KEY = "rlcraftfurnacefix.stored_xp_precise";
    private static final Map<TileEntityFurnace, Double> STORED_XP = Collections.synchronizedMap(new WeakHashMap<TileEntityFurnace, Double>());
    private static final ThreadLocal<Integer> PLAYER_EXTRACT_DEPTH = new ThreadLocal<Integer>();

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

        if (Boolean.TRUE.equals(isPlayerExtractContext())) {
            return;
        }

        double xp = calculateSmeltingXp(extracted, extracted.getCount());
        if (xp > 0.0D) {
            addStoredXp(furnace, xp);
        }
    }

    private static boolean isPlayerExtractContext() {
        return PLAYER_EXTRACT_DEPTH.get() != null;
    }

    public static void onReadFromNbt(TileEntityFurnace furnace, NBTTagCompound nbt) {
        if (furnace == null || nbt == null) {
            return;
        }

        double stored = nbt.getDouble(NBT_KEY);
        if (stored > 0.0D) {
            STORED_XP.put(furnace, stored);
        } else {
            STORED_XP.remove(furnace);
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
    }

    public static void onOutputSlotCrafted(EntityPlayer player, IInventory inventory) {
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }

        if (!(inventory instanceof TileEntityFurnace)) {
            return;
        }

        TileEntityFurnace furnace = (TileEntityFurnace) inventory;
        payoutStoredXp(furnace, player.world, player.posX, player.posY + 0.5D, player.posZ);
    }

    public static void onFurnaceBroken(World world, BlockPos pos) {
        if (world == null || pos == null || world.isRemote) {
            return;
        }

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFurnace)) {
            return;
        }

        TileEntityFurnace furnace = (TileEntityFurnace) te;
        payoutStoredXp(furnace, world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static void payoutStoredXp(TileEntityFurnace furnace, World world, double x, double y, double z) {
        int stored = toVanillaExperience(drainStoredXp(furnace));
        while (stored > 0) {
            int split = EntityXPOrb.getXPSplit(stored);
            stored -= split;
            world.spawnEntity(new EntityXPOrb(world, x, y, z, split));
        }
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

    private static void addStoredXp(TileEntityFurnace furnace, double amount) {
        synchronized (STORED_XP) {
            double current = getStoredXp(furnace);
            STORED_XP.put(furnace, current + amount);
        }
    }

    private static double getStoredXp(TileEntityFurnace furnace) {
        Double value = STORED_XP.get(furnace);
        return value == null ? 0.0D : value;
    }

    private static double drainStoredXp(TileEntityFurnace furnace) {
        synchronized (STORED_XP) {
            double stored = getStoredXp(furnace);
            STORED_XP.remove(furnace);
            return stored;
        }
    }
}
