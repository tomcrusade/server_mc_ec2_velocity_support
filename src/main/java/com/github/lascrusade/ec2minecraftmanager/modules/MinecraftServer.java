package com.github.lascrusade.ec2minecraftmanager.modules;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Date;
import java.util.Objects;

public class MinecraftServer {
  private final String instanceId;
  private final long idleTimeout;
  private final String entryPermission;
  private final Logger logger;
  private final RegisteredServer velocityInstance;

  private Date lastInactivityTime = null;
  private boolean running = false;
  private boolean started = false;


  public MinecraftServer(
    String instanceId,
    long idleTimeout,
    String entryPermission,
    RegisteredServer velocityServerInstance,
    Logger logger
  ) {
    this.instanceId = instanceId;
    this.idleTimeout = idleTimeout;
    this.entryPermission = entryPermission;
    this.logger = logger;
    this.velocityInstance = velocityServerInstance;
  }

  public String getName() {
    return this.velocityInstance.getServerInfo().getName();
  }

  public String getAWSInstanceId() {
    return this.instanceId;
  }

  public String getIP() {
    return this.velocityInstance.getServerInfo().getAddress().getHostString();
  }

  public int getPort() {
    return this.velocityInstance.getServerInfo().getAddress().getPort();
  }

  public String getEntryPermissionName() {
    return this.entryPermission;
  }


  public boolean isInstanceTurnedOn() {
    return this.started;
  }

  public boolean isOnline() {
    return this.running;
  }

  public int getPlayerCount() {
    return this.velocityInstance.getPlayersConnected().size();
  }


  public void update() {
    try {
      this.updateStartedStatus();
      this.updateRunningStatus();
      this.updateInactivityTime();
      if (this.isIdleForTooLong()) {
        this.stop();
      }
    } catch (Exception e) {
      this.logger.error("Failed to check server because: " + e);
    }
  }

  private void updateStartedStatus() throws RuntimeException, IOException {
    String result = String.join(
      " ",
      Utilities.runBash(
        "aws ec2 describe-instance-status" +
          " --instance-ids=" + this.instanceId +
          " --query=InstanceStatuses[0].InstanceState.Name" +
          " --output=text"
      )
    );
    this.started = result.equals("running");
  }

  private void updateRunningStatus() throws RuntimeException {
    if (!this.started) {
      this.running = false;
      return;
    }
    String ip = this.getIP();
    int port = this.getPort();
    SocketAddress sockaddr = new InetSocketAddress(ip, port);
    Socket socket = null;
    try {
      socket = new Socket();
      socket.connect(sockaddr, 2000);
      this.running = true;
    } catch (IOException stex) {
      this.running = false;
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException e) {
          throw new RuntimeException("Cannot close MC server socket", e);
        }
      }
    }
  }

  private void updateInactivityTime() {
    if (this.getPlayerCount() > 0 || (!this.running && !this.started)) {
      this.lastInactivityTime = null;
    } else if (this.lastInactivityTime == null) {
      this.lastInactivityTime = new Date();
    }
  }

  private boolean isIdleForTooLong() {
    if (this.lastInactivityTime == null) return false;
    long inactivityTimeDiff = ((new Date()).getTime() - this.lastInactivityTime.getTime()) / 1000;
    return inactivityTimeDiff > this.idleTimeout;
  }


  public void start() {
    if (this.started) {
      this.logger.info("Instance already started");
      return;
    }
    try {
      this.logger.info("Starting instance");
      Utilities.runBash(
        "aws ec2 start-instances --instance-ids=" + this.instanceId +
          "; aws ec2 wait instance-running --instance-ids=" + this.instanceId
      );
      this.started = true;
    } catch (IOException | RuntimeException e) {
      this.logger.error("Failed to start server instance because: " + e);
    }
  }


  public void stop() {
    if (!this.started) {
      this.logger.info("Instance already shutted down");
      return;
    }
    try {
      this.logger.info("Shutting down instance");
      Utilities.runBash("aws ec2 stop-instances --instance-ids=" + this.instanceId);
      this.started = false;
      this.running = false;
    } catch (IOException | RuntimeException e) {
      this.logger.error("Failed to shut down server because: " + e);
    }
  }


  public boolean allowPlayer(Player player) {
    return !Objects.equals(this.entryPermission, "") || player.hasPermission(this.entryPermission);
  }
}
