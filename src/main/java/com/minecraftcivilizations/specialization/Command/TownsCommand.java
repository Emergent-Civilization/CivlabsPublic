package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import com.minecraftcivilizations.specialization.Distance.Town;
import com.minecraftcivilizations.specialization.Distance.TownManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Redisnged and optimized. (used to scan entire world and load all chunks for towns and get all beds causing large lag spikes)
 * @author Jfrogy
 */

@CommandAlias("towns")
public class TownsCommand extends BaseCommand {

    @Default
    @CommandPermission("towndetector.list")
    public void onTowns(CommandSender sender) {
        sender.sendRichMessage("<green>=== Detected Towns ===");
        if (TownManager.getTowns().isEmpty()) {
            sender.sendRichMessage("<yellow>No towns detected yet.");
        } else {
            // Get current online players for analytics
            List<CustomPlayer> allPlayers = Bukkit.getOnlinePlayers().stream()
                    .map(CoreUtil::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            
            for (int i = 0; i < TownManager.getTowns().size(); i++) {
                Town town = TownManager.getTowns().get(i);
                sender.sendRichMessage("<aqua>Town " + (i + 1) + ": <white>" +
                        town.getBedCount() + " beds at " +
                        TownManager.formatLocation(town.getCenterLocation()));
                
                // Get town players for analytics
                List<CustomPlayer> townPlayers = getTownPlayers(town, allPlayers);
                
                if (!townPlayers.isEmpty()) {
                    sender.sendRichMessage("<gray>  Population: <white>" + townPlayers.size() + " players");
                    
                    // Display class population
                    sender.sendRichMessage("<gray>  Class Population:");
                    Map<SkillType, Integer> classPopulation = getSkillPopularity(townPlayers);
                    for (SkillType skillType : SkillType.values()) {
                        int count = classPopulation.getOrDefault(skillType, 0);
                        if (count > 0) {
                            sender.sendRichMessage("<gray>    " + getDisplayName(skillType) + ": <white>" + count + " players");
                        }
                    }
                    
                    // Display mastery percentages
                    sender.sendRichMessage("<gray>  Average Mastery %:");
                    Map<SkillType, Double> masteryPercentages = getTownClassMasteryPercentages(townPlayers);
                    for (SkillType skillType : SkillType.values()) {
                        double mastery = masteryPercentages.getOrDefault(skillType, 0.0);
                        if (mastery > 0.0) {
                            sender.sendRichMessage("<gray>    " + getDisplayName(skillType) + ": <white>" + mastery + "%");
                        }
                    }
                } else {
                    sender.sendRichMessage("<gray>  No online players in this town");
                }
                
                sender.sendRichMessage(""); // Empty line for spacing
            }
        }
    }
    
    @Subcommand("scan")
    @CommandPermission("towndetector.admin")
    public void onForceScan(CommandSender sender) {
        sender.sendRichMessage("<yellow>Starting town scan...");
        
        // Run the scan asynchronously to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Specialization")), () -> {
            TownManager.scanAllPlayersForTownsAsync();

            // Send results back on main thread
            Bukkit.getScheduler().runTask(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Specialization")), () -> {
                int townCount = TownManager.getTowns().size();
                sender.sendRichMessage("<green>Town scan complete! Found " + townCount + " towns.");
                
                if (townCount > 0) {
                    sender.sendRichMessage("<gray>Use '/towns' to view detected towns.");
                }
            });
        });
    }
    
    private List<CustomPlayer> getTownPlayers(Town town, List<CustomPlayer> allPlayers) {
        return allPlayers.stream()
                .filter(player -> {
                    Player bukkitPlayer = Bukkit.getPlayer(player.getUuid());
                    if (bukkitPlayer == null) return false;
                    
                    // Try multiple methods to get player location for town association
                    Location playerLocation = null;
                    
                    // Method 1: Check spawn location from TownManager
                    Location playerSpawn = TownManager.getPlayerSpawnLocations().get(player.getUuid());
                    if (playerSpawn != null) {
                        playerLocation = playerSpawn;
                    } else {
                        // Method 2: Check bed spawn location
                        playerSpawn = bukkitPlayer.getBedSpawnLocation();
                        if (playerSpawn != null) {
                            playerLocation = playerSpawn;
                        } else {
                            // Method 3: Use current location as fallback
                            playerLocation = bukkitPlayer.getLocation();
                        }
                    }
                    
                    if (playerLocation == null) return false;
                    
                    double distance = playerLocation.distance(town.getCenterLocation());
                    return distance <= 150; // TOWN_RADIUS from TownManager
                })
                .toList();
    }
    
    private Map<SkillType, Integer> getSkillPopularity(List<CustomPlayer> players) {
        Map<SkillType, Integer> skillCounts = new java.util.HashMap<>();
        
        // Initialize all skill types with 0
        for (SkillType skillType : SkillType.values()) {
            skillCounts.put(skillType, 0);
        }
        
        // Count players for each skill type based on their highest skill
        for (CustomPlayer player : players) {
            SkillType highestSkill = null;
            int highestLevel = 0;
            
            // Find the player's highest skill level
            for (SkillType skillType : SkillType.values()) {
                int level = player.getSkillLevel(skillType);
                if (level > highestLevel) {
                    highestLevel = level;
                    highestSkill = skillType;
                }
            }
            
            // Count this player for their highest skill (if they have any skill levels)
            if (highestSkill != null && highestLevel > 0) {
                skillCounts.merge(highestSkill, 1, Integer::sum);
            }
        }
        
        return skillCounts;
    }
    
    private Map<SkillType, Double> getTownClassMasteryPercentages(List<CustomPlayer> townPlayers) {
        Map<SkillType, Double> masteryPercentages = new java.util.HashMap<>();
        
        // Initialize all skill types with 0.0
        for (SkillType skillType : SkillType.values()) {
            masteryPercentages.put(skillType, 0.0);
        }
        
        // Calculate average mastery percentage for each skill type
        for (SkillType skillType : SkillType.values()) {
            List<Double> skillMasteryPercentages = townPlayers.stream()
                    .mapToDouble(player -> player.getPercentOfTotal(skillType))
                    .boxed()
                    .toList();
            
            if (!skillMasteryPercentages.isEmpty()) {
                double averageMastery = skillMasteryPercentages.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                masteryPercentages.put(skillType, Math.round(averageMastery * 100.0) / 100.0); // Round to 2 decimal places
            }
        }
        
        return masteryPercentages;
    }
    
    private String getDisplayName(SkillType skillType) {
        return skillType.name().substring(0, 1).toUpperCase() + 
               skillType.name().toLowerCase().substring(1);
    }

}
