package com.rlcraft.furnacexp;

import com.rlcraft.furnacexp.asm.FurnaceXpHooks;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = FurnaceXpFixMod.MODID, name = FurnaceXpFixMod.NAME, version = FurnaceXpFixMod.VERSION, acceptableRemoteVersions = "*")
public class FurnaceXpFixMod {
    public static final String MODID = "rlcraftfurnacefix";
    public static final String NAME = "RLCraft Furnace XP Fix";
    public static final String VERSION = "1.0.2";

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FurnaceFixDebugCommand());
    }

    private static class FurnaceFixDebugCommand extends CommandBase {
        @Override
        public String getName() {
            return "furnacefix";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/furnacefix debug";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            if (args.length == 1 && "debug".equalsIgnoreCase(args[0])) {
                boolean enabled = FurnaceXpHooks.toggleDebug();
                sender.sendMessage(new TextComponentString("FurnaceFix debug " + (enabled ? "enabled" : "disabled") + "."));
                return;
            }
            sender.sendMessage(new TextComponentString(getUsage(sender)));
        }
    }
}
