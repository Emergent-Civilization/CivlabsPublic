package com.minecraftcivilizations.specialization.Command;

import com.minecraftcivilizations.specialization.Player.LocalNameGenerator;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * allows players or admins to reroll their username or another player's username and set a custom username
 * @author Jfrogy
 */

@CommandAlias("rerollname|reroll")
@CommandPermission("specialization.rerollname")
public class RerollNameCommand extends BaseCommand {

    private final LocalNameGenerator nameGenerator;
    private final NamespacedKey rerollKey;
    private static final long MAX_REROLL_TIME_MS = 15 * 60 * 1000L; // 15 minutes

    public RerollNameCommand(LocalNameGenerator generator) {
        this.nameGenerator = generator;
        this.rerollKey = new NamespacedKey(MinecraftCivilizationsCore.getInstance(), "reroll_timestamp");
    }

    // ---------------- Default reroll ----------------
    @Default
    @CommandCompletion("@players")
    @Description("Reroll your own username or another player's username")
    @Syntax("[player]")
    public void reroll(Player sender, @Optional String targetName) {
        Player target = resolveTarget(sender, targetName);
        if (target == null) return;

        long now = System.currentTimeMillis();
        boolean isBypass = sender.isOp() || sender.hasPermission("specialization.rerollname.other");

        // Permission and limit checks
        if (target.equals(sender)) {
            if (!sender.hasPermission("specialization.rerollname.self") && !isBypass) {
                PlayerUtil.message(sender,"§cYou do not have permission to reroll your own name.");
                return;
            }

            Long timestamp = sender.getPersistentDataContainer().get(rerollKey, PersistentDataType.LONG);
            if (timestamp != null && !isBypass) {
                PlayerUtil.message(sender,"§cYou have already rerolled your name.");
                return;
            }

            if (System.currentTimeMillis() - sender.getFirstPlayed() > MAX_REROLL_TIME_MS && !isBypass) {
                PlayerUtil.message(sender,"§cYou can only reroll your username within the first 15 minutes of playtime.");
                return;
            }
        } else if (!sender.hasPermission("specialization.rerollname.other") && !isBypass) {
            PlayerUtil.message(sender,"§cYou do not have permission to reroll other players' names.");
            return;
        }

        try {
            String newNameStr = nameGenerator.nextName();
            if (target.equals(sender)) {
                PlayerUtil.message(sender,"§aSuccessfully rerolled §c" + target.getName() + "§6 -> §f" + newNameStr);
            } else {
                PlayerUtil.message(sender,"§aSuccessfully rerolled §c" + target.getName() + " §6 -> §f" + newNameStr);
                PlayerUtil.message(target,"§eYour username has been rerolled " + target.getName() + "§6 ->§f " + newNameStr);
            }

            applyName(sender, target, newNameStr, now);

        } catch (NoSuchElementException e) {
            PlayerUtil.message(sender,"§cCould not generate a new unique name. Try again later.");
        }
    }

    // ---------------- Subcommand: custom name ----------------
    @Subcommand("custom")
    @CommandCompletion("@players")
    @CommandPermission("specialization.rerollname.custom")
    @Description("Assign a custom username to a player")
    @Syntax("<player> <name>")
    public void custom(Player sender, String targetName, String desiredName) {
        Player target = resolveTarget(sender, targetName);
        if (target == null) return;

        // Replace spaces with underscores and remove invalid characters
        desiredName = desiredName.replace(" ", "_").replaceAll("[^a-zA-Z0-9_]", "");

        if (desiredName.length() > 16) {
            PlayerUtil.message(sender,"§cThe name cannot exceed 16 characters.");
            return;
        }

        if (desiredName.isEmpty()) {
            PlayerUtil.message(sender,"§cThe name must contain at least one valid character.");
            return;
        }

        long now = System.currentTimeMillis();
        PlayerUtil.message(sender,"§aSuccessfully set custom name §6" + target.getName() + " §6-> §f" + desiredName);
        applyName(sender, target, desiredName, now);

        if (!target.equals(sender)) {
            PlayerUtil.message(sender,"§aYour name has been set to: §f" + desiredName);
        }
    }





    // ---------------- Internal helper ----------------
    private void applyName(Player sender, Player target, String newNameStr, long now) {
        Component newName = Component.text(newNameStr)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore
                .getInstance()
                .getCustomPlayerManager()
                .getCustomPlayer(target.getUniqueId());

        customPlayer.setName(newName);
        Specialization.getInstance().applyCustomName(target, newName);

        if (target.equals(sender)) {
            sender.getPersistentDataContainer().set(rerollKey, PersistentDataType.LONG, now);
        }
    }

    // ---------------- Utility ----------------
    private Player resolveTarget(Player sender, String targetName) {
        if (targetName == null || targetName.isEmpty()) return sender;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            PlayerUtil.message(sender,"§cPlayer '" + targetName + "' not found.");
            return null;
        }
        return target;
    }
}
