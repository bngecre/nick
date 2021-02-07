package me.bungeecore.nick;

import lombok.Getter;
import me.bungeecore.nick.commands.NickCommand;
import me.bungeecore.nick.handler.NickHandler;
import me.bungeecore.lycore.service.IServiceProvider;
import me.bungeecore.lycore.service.ServiceProvider;
import me.bungeecore.nick.utils.*;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class NickPlugin extends JavaPlugin implements IServiceProvider {

    private NickPlugin nickPlugin;
    private NickClient nickClient;

    @Override
    public void onLoad() {
        ServiceProvider.registerService(NickPlugin.class, nickPlugin = this);
        ServiceProvider.registerService(GameProfileBuilder.class, new GameProfileBuilder(nickPlugin));

        NickHandler nickHandler = new NickHandler();
        ServiceProvider.registerService(NickHandler.class, nickHandler);
        nickClient = nickHandler.getNickClient();
    }

    @Override
    public void onEnable() {
        this.init();
    }

    private void init() {
        getCommand("nick").setExecutor(new NickCommand());
    }
}
