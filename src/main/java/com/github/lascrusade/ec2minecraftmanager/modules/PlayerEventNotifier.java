package com.github.lascrusade.ec2minecraftmanager.modules;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public record PlayerEventNotifier(Logger logger) {

  public void unknownServerEntryDenied(Player player) {
    this.logger.warn("Player " + player.getUsername() + " attempts to connect unregistered server");
    player.sendMessage(Component.text("§4You are §cnot allowed §4to enter this server"));
  }

  public void unconfiguredServerEntryDenied(Player player, String serverName) {
    this.logger.warn("Player " + player.getUsername() + " attempts to connect non-configurable server " + serverName);
    player.sendMessage(Component.text("§4You are §cnot allowed §4to enter this server"));
  }

  public void serverEntryPermissionDenied(Player player, String serverName) {
    this.logger.warn("Player " + player.getUsername() + " dont have permission to connect to server " + serverName);
    player.sendMessage(Component.text("§4You are §cnot allowed §4to enter this server"));
  }

  public void playerConnectingToServer(Player player, String serverName) {
    player.sendMessage(Component.text("§7Connecting §2" + player.getUsername() + " §7to §2" + serverName + "..."));
  }

  public void startingServerOS(Player player, String serverName) {
    player.sendMessage(Component.text("§7Starting server §6" + serverName + " §7...."));
    player.sendMessage(Component.text("§7Please wait for §61 min §7for server to start"));
  }

  public void playerEntryAttemptOnServerStartup(Player player) {
    player.sendMessage(Component.text("§7Please wait for §640 secs §7for server to start"));
  }

}
