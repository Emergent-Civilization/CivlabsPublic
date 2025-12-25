package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.minecraftcivilizations.specialization.Listener.Player.LocalChat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * allows you to run commands or send messages as another player or all players.
 * @author Jfrogy
 */
@CommandAlias("sudo")
@CommandPermission("sudo.admin")
@Description("Force a player or everyone to run a command or send a message.")
public class SudoChatCommand extends BaseCommand {

    private final LocalChat localChat;

    public SudoChatCommand(LocalChat localChat) {
        this.localChat = localChat;
    }

    // --- /sudo <player|@a> <message...> ---
    @Default
    @Syntax("<player|@a> <message...>")
    @CommandCompletion("@players @nothing")
    public void onSudo(CommandSender sender, String targetArg, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /sudo <player|@a> <message...>");
            return;
        }

        Collection<Player> targets = resolveTargets(targetArg);
        if (targets.isEmpty()) {
            sender.sendMessage("§cNo valid targets found for: " + targetArg);
            return;
        }

        // If first word is "bubble", use LocalChat for remaining text
        boolean isBubble = args[0].equalsIgnoreCase("bubble");
        String message = String.join(" ", isBubble ? Arrays.copyOfRange(args, 1, args.length) : args);

        if (isBubble && message.isEmpty()) {
            sender.sendMessage("§cUsage: /sudo <player|@a> bubble <message...>");
            return;
        }

        for (Player target : targets) {
            if (isBubble) {
               target.sendMessage(message);
            } else {
                target.chat(message);
            }
        }

        sender.sendMessage("§aExecuted sudo for " + targets.size() + " player(s).");
    }

    private Collection<Player> resolveTargets(String arg) {
        if (arg.equalsIgnoreCase("@a")) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }
        Player p = Bukkit.getPlayerExact(arg);
        return (p != null) ? Collections.singleton(p) : Collections.emptyList();
    }


    private void executeAs(Player target, String message) {
        if (message.startsWith("/")) {
            // command mode: strip leading / and dispatch
            String command = message.substring(1);
            Bukkit.dispatchCommand(target, command);
        } else {
            // chat mode
            target.chat(message);
        }
    }
}
