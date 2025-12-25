package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Player.LocalNameGenerator;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Jfrogy
 */
@CommandAlias("setnameoption|nameoptions|namechoice")
//No permissions needed here. Handles all exceptions afaik.
//@CommandPermission("specialization.setnameoption")
public class NameChoiceCommand extends BaseCommand {

    private final LocalNameGenerator nameGenerator;
    private final NamespacedKey PERMANENT_NAME_KEY = new NamespacedKey(Specialization.getInstance(), "permanent_name");

    public NameChoiceCommand(LocalNameGenerator generator) {
        this.nameGenerator = generator;
    }

    @Subcommand("confirm")
    @Description("Confirm a temporary name choice")
    @Syntax("/setnameoption confirm <chosenName>")
    public void confirm(Player sender, String chosenName) {
        UUID uuid = sender.getUniqueId();
        LocalNameGenerator.TempNameData data = nameGenerator.tempNames.get(uuid);
        if (!isNameValid(sender, chosenName, data)) return;

        // Update CustomPlayer object
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore
                .getInstance()
                .getCustomPlayerManager()
                .getCustomPlayer(uuid);

        Component newName = Component.text(chosenName)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        customPlayer.setName(newName);

        Player bukkitPlayer = Bukkit.getPlayer(uuid);
        if (bukkitPlayer != null) {
            Specialization.getInstance().applyCustomName(bukkitPlayer, newName);
            // Mark in PDC
            nameGenerator.confirmNameChoice(sender.getUniqueId(), chosenName);
            // simulate writing and sealing a name
            sender.playSound(sender.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.UI, 0.8f, 1.2f);
            sender.playSound(sender.getLocation(), Sound.ITEM_BOOK_PUT, SoundCategory.UI, 0.5f, 1f);
            PlayerUtil.message(sender,
                    Component.text("Your name is now: ")
                            .append(newName.color(NamedTextColor.GOLD))
            );
        }
    }


    @Subcommand("help|list")
    @Description("List your available temporary name options")
    @Syntax("/setnameoption help")
    public void listOptions(Player sender) {
        UUID uuid = sender.getUniqueId();
        LocalNameGenerator.TempNameData data = nameGenerator.tempNames.get(uuid);

        if (data == null || data.options.isEmpty()) {
            PlayerUtil.message(sender, "§cYou have no temporary name options available.");
            return;
        }


        PlayerUtil.message(sender, Component.text("Your temporary name options:", NamedTextColor.AQUA));

        // Show initial name + temp options
        List<String> allOptions = new ArrayList<>();
        allOptions.add(data.initialName);
        allOptions.addAll(data.options);

        for (String name : allOptions) {
            Component clickable = Component.text(name, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            Component.text("Click to choose this name")))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/setnameoption " + name));
            PlayerUtil.message(sender, clickable);
        }
    }


    @Default
    @Description("Choose one of your temporary name options")
    @Syntax("/setnameoption <tempName>")
    public void choose(Player sender, String tempName) {

        UUID uuid = sender.getUniqueId();
        LocalNameGenerator.TempNameData data = nameGenerator.tempNames.get(uuid);
        if (!isNameValid(sender, tempName, data)) return;

        // Suggest the confirm command as a clickable chat component
        Component confirmPopup = Component.text(" [Confirm & Apply]", NamedTextColor.GREEN)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("Permanently set name to: " + tempName)))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                        "/setnameoption confirm " + tempName));

        PlayerUtil.message(sender,
                Component.text("Are you sure? ", NamedTextColor.WHITE)
                        .append(Component.text(tempName, NamedTextColor.GOLD))
                        .append(Component.text(" →", NamedTextColor.AQUA))
                        .append(confirmPopup)
        );
    }


    //helper
    private boolean isNameValid(Player sender, String chosenName, LocalNameGenerator.TempNameData data) {
        if (chosenName == null || chosenName.isEmpty()) {
            PlayerUtil.message(sender, "§cYou must specify a name to confirm.");
            return false;
        }

        // Use the helper from LocalNameGenerator
        if (!nameGenerator.canSelectTempName(sender)) {
            PlayerUtil.message(sender, "The time to pick a name option has §cexpired");
            return false;
        }


        if (data == null) {
            PlayerUtil.message(sender, "§cYou have no temporary name options.");
            return false;
        }

        if (!data.options.contains(chosenName) && !chosenName.equals(data.initialName)) {
            PlayerUtil.message(sender, "§cYou can only confirm one of your temporary options.");
            return false;
        }

        // Check if the player already has a permanent name
        Byte permanent = sender.getPersistentDataContainer().get(PERMANENT_NAME_KEY, PersistentDataType.BYTE);
        if (permanent != null && permanent == (byte) 1) {
            PlayerUtil.message(sender, "§cYou already have a permanent name");
            return false;
        }

        return true;
    }
}
