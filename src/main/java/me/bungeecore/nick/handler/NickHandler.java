package me.bungeecore.nick.handler;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import lombok.Getter;
import lombok.SneakyThrows;
import me.bungeecore.lyperms.handler.TabHandler;
import me.bungeecore.nick.NickClient;
import me.bungeecore.nick.NickPlugin;
import me.bungeecore.lycore.service.IServiceProvider;
import me.bungeecore.lycore.service.ServiceProvider;
import me.bungeecore.nick.utils.GameProfileBuilder;
import me.bungeecore.nickserver.model.nick.Nick;
import me.bungeecore.nickserver.model.nick.NickPlayer;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class NickHandler implements IServiceProvider {

    private final NickPlugin nickPlugin = ServiceProvider.getService(NickPlugin.class);
    private final TabHandler tabHandler = ServiceProvider.getService(TabHandler.class);
    private final GameProfileBuilder gameProfileBuilder = ServiceProvider.getService(GameProfileBuilder.class);

    private List<Player> nicking;
    private List<Player> nickedPlayers;
    @Getter
    private NickClient nickClient;

    public NickHandler() {
        this.nicking = new ArrayList<>();
        this.nickedPlayers = new ArrayList<>();
        this.nickClient = new NickClient();

        this.addNickPacketListener();
    }

    public boolean nick(final Player player) {
        boolean isNicked = ResponseHandler.handleResponse(nickClient.getNickApi().isNicked(player.getUniqueId()));
        if (isNicked) return false;

        ResponseHandler.handleResponse(this.nickClient.getNickApi().nick(player.getUniqueId()));

        PacketPlayOutPlayerInfo removeTab = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, ((CraftPlayer) player).getHandle());
        sendPacketToOnePlayer(removeTab, player);

        PacketPlayOutPlayerInfo addTab = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, ((CraftPlayer) player).getHandle());
        sendPacketToOnePlayer(addTab, player);

        new BukkitRunnable() {
            @Override
            public void run() {
                tabHandler.setPrefix(player);
            }
        }.runTaskLater(nickPlugin, 20);
        return true;
    }

    public boolean unnick(final Player player) throws IOException {
        GameProfile gameProfile = gameProfileBuilder.fetch(player.getUniqueId());
        CraftPlayer oldCraftPlayer = (CraftPlayer) player;

        if (!gameProfile.equals(oldCraftPlayer.getHandle().getProfile())) {
            ResponseHandler.handleResponse(nickClient.getNickApi().unnick(player.getUniqueId()));

            Bukkit.getOnlinePlayers().forEach(players -> {
                players.hidePlayer(player);
            });
            setGameProfile(player, gameProfile);

            CraftPlayer craftPlayer = (CraftPlayer) player;
            Bukkit.getScheduler().runTaskLater(nickPlugin, () -> {
                PacketPlayOutPlayerInfo addTab = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, craftPlayer.getHandle());
                sendPacket(addTab, player);

                PacketPlayOutNamedEntitySpawn spawn = new PacketPlayOutNamedEntitySpawn(craftPlayer.getHandle());
                sendPacket(spawn, player);

                nickedPlayers.remove(player);
                Bukkit.getOnlinePlayers().forEach(players -> {
                    players.showPlayer(player);
                });
            }, 20);
            return true;
        }
        return false;
    }

    private void addNickPacketListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this.nickPlugin, PacketType.Play.Server.PLAYER_INFO) {
            @SneakyThrows
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacket().getPlayerInfoAction().read(0) != EnumWrappers.PlayerInfoAction.ADD_PLAYER) return;

                Player player = event.getPlayer();
                if (Bukkit.getPluginManager().getPlugin("Lobby") != null) {
                    return;
                }

                if (nicking.contains(player)) return;

                boolean isNicked = ResponseHandler.handleResponse(nickClient.getNickApi().isNicked(player.getUniqueId()));
                if (!isNicked) {
                    return;
                }

                final NickPlayer nickPlayer = ResponseHandler.handleResponse(nickClient.getNickApi().getNickPlayer(player.getUniqueId()));
                final Nick nick = ResponseHandler.handleResponse(nickClient.getNickApi().getNickname(nickPlayer.getNickUUID()));

                if (nickedPlayers.contains(player)) return;

                Bukkit.getOnlinePlayers().forEach(players -> {
                    players.hidePlayer(player);
                });

                nicking.add(player);

                GameProfile gameProfile = new GameProfile(nick.getUuid(), nick.getName());
                gameProfile.getProperties().put("textures", new Property("textures", nick.getValue(), nick.getTexture()));

                setGameProfile(player, gameProfile);
                CraftPlayer craftPlayer = (CraftPlayer) player;

                Bukkit.getScheduler().runTaskLater(nickPlugin, () -> {
                    PacketPlayOutPlayerInfo removeTab = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, craftPlayer.getHandle());
                    sendPacketToOnePlayer(removeTab, player);

                    PacketPlayOutPlayerInfo addTab = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, craftPlayer.getHandle());
                    sendPacket(addTab, player);

                    PacketPlayOutNamedEntitySpawn spawn = new PacketPlayOutNamedEntitySpawn(craftPlayer.getHandle());
                    sendPacket(spawn, player);

                    Bukkit.getOnlinePlayers().forEach(players -> {
                        players.showPlayer(player);
                    });

                    nicking.remove(player);
                    nickedPlayers.add(player);
                }, 20);
            }
        });
    }

    public void sendPacket(final Packet<?> packet, final Player player) {
        Bukkit.getOnlinePlayers().forEach(players -> {
            if (!player.equals(players)) {
                ((CraftPlayer) players).getHandle().playerConnection.sendPacket(packet);
            }
        });
    }

    public void sendPacketToOnePlayer(final Packet<?> packet, final Player player) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    private void setGameProfile(final Player player, final GameProfile gameProfile) {
        try {
            Field f = EntityHuman.class.getDeclaredField("bH");
            f.setAccessible(true);

            Field modifierField = Field.class.getDeclaredField("modifiers");
            modifierField.setAccessible(true);
            modifierField.setInt(f, f.getModifiers() & ~Modifier.FINAL);

            f.set(((CraftPlayer) player).getHandle(), gameProfile);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}