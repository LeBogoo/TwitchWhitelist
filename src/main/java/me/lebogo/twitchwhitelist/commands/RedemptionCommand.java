package me.lebogo.twitchwhitelist.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.lebogo.twitchwhitelist.TwitchWhitelisting;
import me.lebogo.twitchwhitelist.WhitelistingStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RedemptionCommand implements BasicCommand {

    private final WhitelistingStore whitelistingStore;

    public RedemptionCommand(WhitelistingStore whitelistingStore) {
        this.whitelistingStore = whitelistingStore;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        Entity executor = commandSourceStack.getExecutor();


        // check permissions
        if (!executor.hasPermission("twitchwhitelist.redemption")) {
            executor.sendMessage(Component.text("You do not have permission to use this command").style(Style.style(TextColor.color(0xFB5454))));
            return;
        }

        if (args.length < 2) {
            executor.sendMessage("Usage: /redemption <twitchUsername> <show|remove>");
            return;
        }

        String twitchUsername = args[0];
        String action = args[1];

        TwitchWhitelisting whitelisting = whitelistingStore.getWhitelistings().stream()
                .filter(w -> w.twitchUsername().equalsIgnoreCase(twitchUsername))
                .findFirst()
                .orElse(null);

        if (whitelisting == null) {
            executor.sendMessage(Component.text("No whitelisting found for " + twitchUsername).style(Style.style(TextColor.color(0xFB5454))));
            return;
        }

        if (action.equalsIgnoreCase("show")) {
            executor.sendMessage(Component.text("Whitelisting for " + twitchUsername).style(Style.style(TextColor.color(0x54FB54))));
            executor.sendMessage(Component.text("Minecraft Username: " + whitelisting.minecraftUsername()).style(Style.style(TextColor.color(0x54FB54))));
            executor.sendMessage(Component.text("Whitelist Date: " + whitelisting.whitelistDate()).style(Style.style(TextColor.color(0x54FB54))));
            executor.sendMessage(Component.text("Is Java: " + whitelisting.isJava()).style(Style.style(TextColor.color(0x54FB54))));
        } else if (action.equalsIgnoreCase("remove")) {
            whitelistingStore.removeWhitelisting(whitelisting);
            whitelistingStore.save();
            executor.sendMessage(Component.text("Whitelisting for " + twitchUsername + " removed").style(Style.style(TextColor.color(0x54FB54))));
        } else {
            executor.sendMessage(Component.text("Invalid action: " + action).style(Style.style(TextColor.color(0xFB5454))));
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
        Entity executor = commandSourceStack.getExecutor();

        if (!executor.hasPermission("twitchwhitelist.redemption")) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();

        executor.getServer().getLogger().info("args: " + args.length);

        if (args.length <= 1) {
            for (TwitchWhitelisting whitelisting : whitelistingStore.getWhitelistings()) {
                if (args.length == 0) {
                    suggestions.add(whitelisting.twitchUsername());
                } else {
                    if (whitelisting.twitchUsername().startsWith(args[0])) {
                        suggestions.add(whitelisting.twitchUsername());
                    }
                }
            }
        } else if (args.length == 2) {
            List<String> options = List.of("show", "remove");
            for (String option : options) {
                if (option.startsWith(args[1])) {
                    suggestions.add(option);
                }
            }
        }

        return suggestions;
    }
}
