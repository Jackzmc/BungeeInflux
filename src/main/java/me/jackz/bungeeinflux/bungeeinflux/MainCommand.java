package me.jackz.bungeeinflux.bungeeinflux;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

public class MainCommand extends Command {
    BungeeInflux plugin;

    public MainCommand(BungeeInflux plugin) {
        super("bungeeinflux","bungeeinflux.command","friend");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(args.length == 0 || args[0].toLowerCase().equalsIgnoreCase("help")) {
            BaseComponent[] textComponent = new ComponentBuilder("§6BungeeInflux").append("\n&e/bungeeinflux reload&& - Reload the config.yml").create();
            sender.sendMessage(textComponent);
        }
        switch(args[0].toLowerCase()) {
            case "reload": {
                plugin.initialize();
                sender.sendMessage(new TextComponent("§aSuccessfully reloaded configuration."));
                break;
            }
            default:
                sender.sendMessage(new TextComponent("§cUnknown command, view help with /bungeeinflux help"));
        }
    }
}
