package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Single;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.Player.LocalNameGenerator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jfrogy
 */
@CommandAlias("SimulateNameRoll")
public class RandomNameBulkTestCommand extends BaseCommand {

    @Default
    @CommandPermission("specialization.rollallnames")
    public void onRollAllNames(Player sender, @Single int totalRolls) {
        try {
            LocalNameGenerator generator = new LocalNameGenerator(null);
            List<String> results = new ArrayList<>();

            for (int i = 0; i < totalRolls; i++) {
                try {
                    String name = generator.nextName(); // generates a new name just like /reroll
                    results.add(name);
                } catch (Exception e) {
                    sender.sendMessage("§cStopped early: ran out of unique names at " + i + " rolls.");
                    break;
                }
            }

            File output = new File(Specialization.getInstance().getDataFolder(), "RolledNames.txt");
            Files.createDirectories(output.getParentFile().toPath());
            Files.write(output.toPath(), results);

            sender.sendMessage("§aRolled " + results.size() + " names and saved to " + output.getName());
        } catch (IOException e) {
            sender.sendMessage("§cFailed to roll names: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
