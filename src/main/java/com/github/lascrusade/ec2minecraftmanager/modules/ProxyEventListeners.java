package com.github.lascrusade.ec2minecraftmanager.modules;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

public class ProxyEventListeners {
  private final Logger logger;
  private final PlayerEventNotifier playerEventNotif;
  private final Map<String, MinecraftServer> servers;

  public ProxyEventListeners(Logger logger, Map<String, MinecraftServer> servers) {
    this.logger = logger;
    this.playerEventNotif = new PlayerEventNotifier(logger);
    this.servers = servers;
  }

  @Subscribe
  public void beforePlayerConnectToProxy(LoginEvent event) {
    Player player = event.getPlayer();
    this.logger.info(player.getUsername() + " logs in");
  }

  @Subscribe
  public void beforePlayerConnectToServer(ServerPreConnectEvent event) {
    Player player = event.getPlayer();
    Optional<RegisteredServer> serverInProxy = event.getResult().getServer();
    if (serverInProxy.isEmpty()) {
      this.playerEventNotif.unknownServerEntryDenied(player);
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      return;
    }
    String serverName = serverInProxy.get().getServerInfo().getName();
    MinecraftServer server = this.servers.get(serverName);
    if (server == null) {
      this.playerEventNotif.unconfiguredServerEntryDenied(player, serverName);
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      return;
    }
    if (!server.allowPlayer(player)) {
      this.playerEventNotif.serverEntryPermissionDenied(player, serverName);
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      return;
    }
    player.sendMessage(Component.text("§4YWAITINGGGG"));
    if (!server.isInstanceTurnedOn()) {
      this.playerEventNotif.startingServerOS(player, serverName);
      server.start();
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      return;
    } else if (!server.isOnline()) {
      this.playerEventNotif.playerEntryAttemptOnServerStartup(player);
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      return;
    }
    this.playerEventNotif.playerConnectingToServer(player, serverName);
  }
}
