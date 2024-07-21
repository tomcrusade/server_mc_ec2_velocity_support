package com.github.lascrusade.ec2minecraftmanager;

import com.github.lascrusade.ec2minecraftmanager.modules.MinecraftServer;
import com.github.lascrusade.ec2minecraftmanager.modules.ProxyEventListeners;
import com.github.lascrusade.ec2minecraftmanager.modules.Utilities;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(
  id = "ec2minecraftmanager",
  name = "EC2MinecraftManager",
  version = "0.0.1",
  authors = { "LasCrusade" }
)
public class EC2MinecraftManager {
  private final ProxyServer proxyServer;
  private final Logger logger;
  private final HashMap<String, MinecraftServer> servers;

  private static final MinecraftChannelIdentifier EC2_MINECRAFT_MANAGER_CHANNEL =
    MinecraftChannelIdentifier.create("ec2minecraftmanager", "manager");
  private static final MinecraftChannelIdentifier BUNGEECORD_CHANNEL =
    MinecraftChannelIdentifier.create("bungecord", "main");

  @Inject
  public EC2MinecraftManager(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
    logger.info("Initiating plugin");
    this.proxyServer = proxyServer;
    this.logger = logger;

    Toml config = Utilities.parseConfig(dataDirectory, logger);
    boolean awsCliConfigSuccessful = this.configureAWSCli(config);

    if (awsCliConfigSuccessful) {
      HashMap<String,String> awsInstanceIds = this.getAWSInstanceIds();
      this.servers = getServers(awsInstanceIds, config);
    } else {
      this.servers = new HashMap<>();
    }
  }

  private boolean configureAWSCli(Toml config) {
    try {
      String region = null;
      String cliKey = null;
      String cliSecret = null;
      Toml awsConfig = config.getTable("awsEC2");
      if (awsConfig != null) {
        region = awsConfig.getString("region");
        cliKey = awsConfig.getString("cliKey");
        cliSecret = awsConfig.getString("cliSecret");
      }
      if(region == null || region.trim().isEmpty()) {
        this.logger.error("AWS EC2 region name empty");
        return false;
      }
      if(cliKey == null || cliKey.trim().isEmpty()) {
        this.logger.error("AWS EC2 cli key empty");
        return false;
      }
      if(cliSecret == null || cliSecret.trim().isEmpty()) {
        this.logger.error("AWS EC2 cli secret empty");
        return false;
      }
      Utilities.runBash(
        "aws configure set region " + region + ";" +
          "aws configure set aws_access_key_id " + cliKey + ";" +
          "aws configure set aws_secret_access_key " + cliSecret
      );
    } catch (IOException e) {
      this.logger.error("Failed to setup aws cli because: " + e);
      return false;
    }
    return true;
  }

  private HashMap<String, String> getAWSInstanceIds() {
    Collection<RegisteredServer> servers = this.proxyServer.getAllServers();
    HashMap<String,String> awsInstanceIds = new HashMap<>();
    try {
      StringJoiner ipAddresses = new StringJoiner(",");
      for (RegisteredServer server: servers) {
        ipAddresses.add(server.getServerInfo().getAddress().getHostString());
      }
      String resultStringified = String.join(
        " ",
        Utilities.runBash(
          "aws ec2 describe-instances " +
            "--filters Name=private-ip-address,Values=" + ipAddresses + " " +
            "--query=Reservations[].Instances[0].[PrivateIpAddress,InstanceId] " +
            "--output=text"
        )
      ).trim().replaceAll("[\\r\\n\\s]+", " ");
      String[] resultRows = resultStringified.split(" ");
      for(int index = 0; index < resultRows.length - 1; index += 2) {
        awsInstanceIds.put(resultRows[index], resultRows[index + 1]);
      }
    } catch (RuntimeException | IOException e) {
      this.logger.error("Failed to look for server AWS instance ID because: " + e);
    }
    return awsInstanceIds;
  }

  private HashMap<String, MinecraftServer> getServers(HashMap<String,String> awsInstanceIds, Toml config) {
    Collection<RegisteredServer> proxyServerInstances = this.proxyServer.getAllServers();
    HashMap<String, MinecraftServer> servers = new HashMap<>();
    for (RegisteredServer velocityServerInstance: proxyServerInstances) {
      String name = velocityServerInstance.getServerInfo().getName();
      Optional<MinecraftServer> result = this.getServer(velocityServerInstance, awsInstanceIds, config);
      if (result.isEmpty()) continue;
      servers.put(name, result.get());
    }
    return servers;
  }

  private Optional<MinecraftServer> getServer(
    RegisteredServer velocityServerInstance,
    HashMap<String,String> serverInstanceIds,
    Toml config
  ) {
    String name = velocityServerInstance.getServerInfo().getName();
    String ipAddress = velocityServerInstance.getServerInfo().getAddress().getHostString();
    String instanceId = serverInstanceIds.get(ipAddress);
    if (instanceId == null) {
      this.logger.error("Server with IP address " + ipAddress + " does not exists on AWS, skipping");
      return Optional.empty();
    }
    long serverIdleTimeout = 0;
    String entryPermission = null;
    Toml serversConfig = config.getTable("servers");
    if (serversConfig != null) {
      Toml serverConfig = serversConfig.getTable(name);
      if (serverConfig != null) {
        entryPermission = serverConfig.getString("permission");
        serverIdleTimeout = serverConfig.getLong("idleTimeout");
      }
    }
    if (serverIdleTimeout <= 0) {
      this.logger.warn("Server " + name + " timeout unknown, set the default timeout to 5 minutes");
      serverIdleTimeout = 300;
    }
    if (entryPermission == null || entryPermission.trim().isEmpty()) {
      this.logger.warn("Server " + name + " entry permission unknown. Be careful !! anyone can enter the server");
      entryPermission = "";
    }
    MinecraftServer server = new MinecraftServer(instanceId,
      serverIdleTimeout,
      entryPermission,
      velocityServerInstance,
      this.logger
    );
    return Optional.of(server);
  }


  @Subscribe(order = PostOrder.FIRST)
  public void onProxyInitialization(ProxyInitializeEvent event) {
    this.proxyServer.getScheduler()
      .buildTask(this, this::invalidateServer)
      .repeat(1L, TimeUnit.MINUTES)
      .schedule();
    this.proxyServer.getChannelRegistrar().register(EC2_MINECRAFT_MANAGER_CHANNEL);
    this.proxyServer.getChannelRegistrar().register(BUNGEECORD_CHANNEL);
    this.proxyServer.getEventManager().register(
      this,
      new ProxyEventListeners(this.logger, this.servers)
    );
  }

  private void invalidateServer() {
    StringJoiner output = new StringJoiner(" -- ");
    for (MinecraftServer server : this.servers.values()) {
      server.update();
      if (server.isInstanceTurnedOn()) {
        if (server.isOnline()) {
          output.add(String.format("%s has %s players", server.getName(), server.getPlayerCount()));
        } else {
          output.add(String.format("%s not started", server.getName()));
        }
      } else {
        output.add(String.format("%s shutted down", server.getName()));
      }
    }
    this.logger.info(output.toString());
  }

}
