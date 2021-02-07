package me.bungeecore.nick.commands;

import lombok.SneakyThrows;
import me.bungeecore.lycore.service.ServiceProvider;
import me.bungeecore.lyperms.handler.MessageHandler;
import me.bungeecore.nick.handler.NickHandler;
import me.bungeecore.nick.handler.ResponseHandler;
import me.bungeecore.nickserver.model.nick.Nick;
import me.bungeecore.nickserver.model.nick.NickPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NickCommand implements CommandExecutor {

    private final NickHandler nickHandler = ServiceProvider.getService(NickHandler.class);
    private final MessageHandler messageHandler = ServiceProvider.getService(MessageHandler.class);

    @SneakyThrows
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender instanceof Player) {
            Player player = (Player) commandSender;

            if (!player.hasPermission("network.command.nick.use")) {
                this.messageHandler.sendMessage(player, "§cDu hast keine Rechte auf diesen Befehl.", "§cYou have no rights to this command.");
                return true;
            }

            if (nickHandler.nick(player)) {
                final NickPlayer nickPlayer = ResponseHandler.handleResponse(nickHandler.getNickClient().getNickApi().getNickPlayer(player.getUniqueId()));
                final Nick nick = ResponseHandler.handleResponse(nickHandler.getNickClient().getNickApi().getNickname(nickPlayer.getNickUUID()));

                this.messageHandler.sendMessage(player, "§7Du bist jetzt als §a" + nick.getName() + " §7genickt.", "§7You are now nicked as §a" + nick.getName() + "§7.");
            } else {
                this.nickHandler.unnick(player);
                this.messageHandler.sendMessage(player,"§7Du wurdest entnickt.", "§7You have been unnicked.");
                return true;
            }
        }
        return false;
    }
}
