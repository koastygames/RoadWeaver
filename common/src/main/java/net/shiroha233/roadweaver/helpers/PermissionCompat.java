package net.shiroha233.roadweaver.helpers;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

public final class PermissionCompat {
    private PermissionCompat() {
    }

    public static boolean hasCommandLevel2(CommandSourceStack source) {
        return source != null && hasCommandLevel2(source.permissions());
    }

    public static boolean hasCommandLevel2(ServerPlayer player) {
        return player != null && hasCommandLevel2(player.permissions());
    }

    public static boolean hasCommandLevel2(LocalPlayer player) {
        return player != null && hasCommandLevel2(player.permissions());
    }

    public static boolean hasCommandLevel2(PermissionSet permissions) {
        return permissions != null && permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
