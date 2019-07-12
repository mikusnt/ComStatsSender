/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mist.comstatssender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sun.management.OperatingSystemMXBean;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

/**
 *
 * @author MS-1
 */
public class PlatformFactory {
    private File folder;
    public enum Platform {
        Windows,
        Linux,
        MacOS,
        Other
    }
    private String getautostart() {
        return System.getProperty("java.io.tmpdir").replace("Local\\Temp\\", "Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup");
    }
    
    private Platform platform;
    private Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(this.getClass().getName());
    public PlatformFactory() {
        platform = loadOperatingSystem();
        if (platform == Platform.Windows) {
            folder = new File(getautostart());
        }
        
    }
    
    public PlatformFactory(Level lvl) {
        this();
        logger.setLevel(Level.INFO);
        logger.info("Created object, detected OS: {}", platform);
        logger.setLevel(lvl);
    }
    private String getOperatingSystemString() {
        String os = System.getProperty("os.name");
        //System.out.println("Using System Property: " + os);
        return os;
    }
    
    private Platform loadOperatingSystem() {
        String system = getOperatingSystemString().toLowerCase();
        Platform platform = Platform.Other;
        if ((system.contains("mac")) || (system.contains("darwin"))) {
            platform = Platform.MacOS;
        } else if (system.contains("win")) {
            platform = Platform.Windows;
        } else if (system.contains("nux")) {
            platform = Platform.Linux;
        }
        return platform;
    }
    
    public Platform getOperatingSystem() {
        return platform;
    }
    
    public double getCPULoad() {
        OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
           .getOperatingSystemMXBean();
        if (platform != Platform.Other) {
            return osBean.getProcessCpuLoad();
        }
        return 0;
    }  
    
    public double getRamLoad() {
        OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
           .getOperatingSystemMXBean();
        
        if (platform == Platform.Windows) {
            return (double)(osBean.getTotalPhysicalMemorySize() - osBean.getFreePhysicalMemorySize()) / osBean.getTotalPhysicalMemorySize();
        }
        if ((platform == Platform.Linux) || (platform == Platform.MacOS)) {
            return 0;
            //return (double)(osBean.getTotalPhysicalMemorySize() - osBean.getFreePhysicalMemorySize() + osBean.getCommittedVirtualMemorySize()) / osBean.getTotalPhysicalMemorySize();
        }
        
        return 0;
    }
    
    public void setRunInStartup(boolean inStartup, String filename) {
        if (platform == Platform.Windows) {
            File startupFile = new File(folder, filename);
            if (!inStartup) {
                if (startupFile.exists()) {
                    boolean removed = startupFile.delete();
                    logger.info("Removed startup shortcut with name: {} with status: {}", startupFile.getName(), removed);
                } else {
                    logger.info("Tried delete startup shortcut with name: {} but file not exists", startupFile.getName());
                }
            } else {
                File toCopy = new File(filename);
                if (toCopy.exists()) {
                    if (!startupFile.exists()) {
                        try {
                            Path path = Files.copy(toCopy.toPath(), startupFile.toPath());
                            logger.info("Copied shortcut with name: {} to: {}", toCopy.getName(), path);
                        } catch (IOException e) {}
                        
                    } else {
                        logger.info("Tried copy shortcut {} to startup location but startup file exists", toCopy.getName());
                    }
                } else {
                    logger.info("Tried copy file {} to startup location but not exists", toCopy.getAbsolutePath());
                }
            }
        }
    }
}
