package com.github.lascrusade.ec2minecraftmanager.modules;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Utilities {

  public static Toml parseConfig(Path fromPath, Logger logger) {
    Toml output = new Toml();
    try {
      File file = new File(fromPath.toFile(), "config.toml");
      if (!file.getParentFile().exists()) {
        boolean result = file.getParentFile().mkdirs();
        if(!result) {
          logger.warn("Config folder already exists");
        }
      }
      if(!file.exists()) {
        Utilities.createNewConfigFile(file, logger);
      }
      return output.read(file);
    } catch (Exception e) {
      logger.error("Config file cannot be read because " + e);
    }
    return output;
  }

  private static void createNewConfigFile(File file, Logger logger) {
    try {
      Class<?> myClass = Class.forName("com.github.lascrusade.ec2minecraftmanager.modules.Utilities");
      InputStream input = myClass.getResourceAsStream("/" + file.getName());
      Throwable throwedError = null;
      try {
        if (input != null) {
          Files.copy(input, file.toPath());
        } else {
          boolean result = file.createNewFile();
          if(!result) {
            logger.warn("Config file already exists");
          }
        }
      } catch (Throwable e) {
        throwedError = e;
        logger.error("Failed to apply new config file because: " + e);
      } finally {
        if (input != null) {
          if (throwedError != null) {
            try {
              input.close();
            } catch (Throwable ex) {
              throwedError.addSuppressed(ex);
            }
          } else {
            input.close();
          }
        }
      }
    } catch (IOException | ClassNotFoundException e) {
      logger.error("Failed to get dummy config file because: " + e);
    }
  }


  public static List<String> runBash(String command) throws RuntimeException, IOException {
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command("bash", "-c", command);
    Process process = processBuilder.start();
    String errorMsg = Utilities.getCmdlineErrorMsg(process);
    if(!Objects.equals(errorMsg, "")) {
      throw new RuntimeException("Failed to execute shell command '" + command + "' because: " + errorMsg);
    }
    List<String> cmdOutputLines = new ArrayList<>();
    String cmdOutputLine;
    BufferedReader cmdOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
    while ((cmdOutputLine = cmdOutput.readLine()) != null) {
      cmdOutputLines.add(cmdOutputLine);
    }
    return cmdOutputLines;
  }

  private static String getCmdlineErrorMsg(Process process) {
    try {
      boolean isError = false;
      StringBuilder cmdOutputStringBuilder = new StringBuilder();
      String cmdErrLine;
      BufferedReader cmdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      while ((cmdErrLine = cmdError.readLine()) != null) {
        isError = true;
        cmdOutputStringBuilder.append("/n").append(cmdErrLine);
      }
      if(!isError) {
        return "";
      }
      return cmdOutputStringBuilder.toString();
    } catch (IOException e) {
      return e.toString();
    }
  }
}
