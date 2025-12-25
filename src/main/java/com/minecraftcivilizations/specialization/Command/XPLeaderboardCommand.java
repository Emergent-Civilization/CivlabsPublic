package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jfrogy
 */

/** @warning currently disabled due to load player method overriding players xp during runtime **/


@CommandAlias("xpleaderboard|xplb")
@CommandPermission("civlabs.xpleaderboard")
public class XPLeaderboardCommand extends BaseCommand {

    // --- Default: overall XP leaderboard ---
    @Default
    public void showOverallLeaderboard(Player sender) {
        sender.sendMessage(Component.text("testo", NamedTextColor.GREEN));
        if (!sender.hasPermission("civlabs.xpleaderboard")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return;
        }

        List<CustomPlayer> customPlayers = getOnlineCustomPlayers();
        if (customPlayers.isEmpty()) {
            sender.sendMessage(Component.text("No players online to display XP leaderboard.", NamedTextColor.RED));
            return;
        }

        customPlayers.sort(Comparator.comparingDouble(CustomPlayer::getTotalXp).reversed());

        Component message = buildLeaderboard("§6§lXP Leaderboard (Total XP)", customPlayers,
                cp -> {
                    long totalXp = Math.round(cp.getTotalXp());
                    SkillType topSkill = Arrays.stream(SkillType.values())
                            .max(Comparator.comparingDouble(skill -> cp.getSkill(skill).getXp()))
                            .orElse(null);

                    String skillInfo = "";
                    if (topSkill != null) {
                        String tier = cp.getSkillLevelEnumByXpOnly(topSkill).name();
                        skillInfo = topSkill.name() + " (" + tier + ")";
                    }

                    return totalXp + " XP - Top Skill: " + skillInfo;
                }, null);

        sender.sendMessage(message); // Send only to the command sender
    }

    // --- Per-class leaderboard ---
    @Subcommand("class")
    @CommandCompletion("@classes")
    public void showClassLeaderboard(Player sender, @NotNull String className) {
        if (!sender.hasPermission("civlabs.xpleaderboard")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return;
        }

        SkillType type;
        try {
            type = SkillType.valueOf(className.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid class name: " + className, NamedTextColor.RED));
            return;
        }

        List<CustomPlayer> customPlayers = getOnlineCustomPlayers().stream()
                .sorted(Comparator.comparingDouble(cp -> -cp.getSkill(type).getXp()))
                .collect(Collectors.toList());

        if (customPlayers.isEmpty()) {
            sender.sendMessage(Component.text("No players online for class " + type.name(), NamedTextColor.RED));
            return;
        }

        Component message = buildLeaderboard("XP Leaderboard (" + type.name() + ")", customPlayers,
                cp -> {
                    long xp = Math.round(cp.getSkill(type).getXp());
                    String tier = cp.getSkillLevelEnum(type).name();
                    return xp + " XP (" + tier + ")";
                }, type);

        sender.sendMessage(message);
    }

    // --- All-classes breakdown ---
    @Subcommand("allclasses")
    public void showAllClassesLeaderboard(Player sender) {
        if (!sender.hasPermission("civlabs.xpleaderboard")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return;
        }

        for (SkillType type : SkillType.values()) {
            List<CustomPlayer> classPlayers = getOnlineCustomPlayers().stream()
                    .sorted(Comparator.comparingDouble(cp -> -cp.getSkill(type).getXp()))
                    .collect(Collectors.toList());

            if (!classPlayers.isEmpty()) {
                Component message = buildLeaderboard("XP Leaderboard (" + type.name() + ")", classPlayers,
                        cp -> {
                            long xp = Math.round(cp.getSkill(type).getXp());
                            String tier = cp.getSkillLevelEnum(type).name();
                            return xp + " XP (" + tier + ")";
                        }, type);
                sender.sendMessage(message);
            }
        }
    }

    // --- Utility Methods ---
    private List<CustomPlayer> getOnlineCustomPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .map(p -> MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().load(p.getUniqueId()))
                .filter(Objects::nonNull)
                .filter(cp -> cp instanceof CustomPlayer)
                .map(cp -> (CustomPlayer) cp)
                .collect(Collectors.toList());
    }

    private NamedTextColor getClassColor(SkillType type) {
        if (type == null) return NamedTextColor.GOLD;
        return switch (type.name().toUpperCase()) {
            case "GUARDSMAN" -> NamedTextColor.RED;
            case "HEALER" -> NamedTextColor.LIGHT_PURPLE;
            case "BUILDER" -> NamedTextColor.YELLOW;
            case "BLACKSMITH" -> NamedTextColor.DARK_GRAY;
            case "LIBRARIAN" -> NamedTextColor.BLUE;
            case "MINER" -> NamedTextColor.WHITE;
            default -> NamedTextColor.GREEN;
        };
    }

    private Component buildLeaderboard(String title, List<CustomPlayer> players,
                                       java.util.function.Function<CustomPlayer, String> valueFormatter,
                                       SkillType classType) {

        NamedTextColor titleColor = classType != null ? getClassColor(classType) : NamedTextColor.GOLD;
        Component header = Component.text("§l" + title + "\n", titleColor);

        List<Component> lines = new ArrayList<>();
        int rank = 1;
        for (CustomPlayer cp : players) {
            Player player = Bukkit.getPlayer(cp.getUuid());
            if (player == null) continue;

            Component tpButton = Component.text("[TP]", NamedTextColor.AQUA)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to " + player.getName(), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand("/tp " + player.getName()));

            NamedTextColor lineColor = classType != null ? getClassColor(classType) : NamedTextColor.WHITE;

            Component line = Component.text(rank + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(player.getName(), lineColor))
                    .append(Component.text(" - " + valueFormatter.apply(cp) + " ", NamedTextColor.WHITE))
                    .append(tpButton)
                    .append(Component.text("\n"));
            lines.add(line);

            rank++;
            if (rank > 10) break;
        }

        return header.append(Component.join(JoinConfiguration.noSeparators(), lines));
    }
}
