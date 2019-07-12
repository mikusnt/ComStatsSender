/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mist.comstatssender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import org.slf4j.LoggerFactory;

/**
 *
 * @author MS-1
 */
public class SimpleProperties {
    private final Level DEFAULT_LOGGER_LVL = Level.WARN; 
    Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(this.getClass().getName());
    static final Dimension SCREEN_SIZE = Toolkit.getDefaultToolkit().getScreenSize();
    private String portName;
    public static final String DEFAULT_PORT_NAME = "COM1";
    private final String portNameProperty = "portName";
    
    private int lightLVL;
    public static final int DEFAULT_LIGHT = 4;
    private final String lightProperty = "light";
    
    private int refreshTime;
    public static final int DEFAULT_REFRESH_TIME = 200;
    private final String refreshTimeProperty = "refreshTime";
    
    private int voltInertiaRatio;
    public static final int DEFAULT_VIRP = 4;
    private final String voltInertiaRatioProperty = "voltInertiaRatio";
    
    private Boolean notifyInStartup;
    public static final Boolean DEFAULT_NOTIFY_STARTUP = false;
    private final String notifyInStartupProperty = "notifyInStartup";
    
    private Boolean notifyInBackground;
    public static final Boolean DEFAULT_NOTIFY_BACKGROUND = true;
    private final String notifyInBackgroundProperty = "notifyInBackground";
    
    private Boolean runOnStartup;
    public static final Boolean DEFAULT_RUN_ON_STARTUP = true;
    private final String runOnStartupProperty = "runOnStartup";
    
    private Boolean doneMinimizedInfo;
    public static final Boolean DEFAULT_DONE_MINIMIZED_INFO = false;
    private final String doneMinimizedInfoProperty = "doneMinimizedInfo";
    
    private int framePositionX;
    public static final int DEFAULT_FRAME_POSITION_X = (SCREEN_SIZE.width / 2) - 150;
    private final String framePositionXProperty = "framePositionX";
    
    private int framePositionY;
    public static final int DEFAULT_FRAME_POSITION_Y = (SCREEN_SIZE.height / 2) - 75;
    private final String framePositionYProperty = "framePositionY";
    
    private final String filename;
    
    public SimpleProperties(String filename) {
        logger.setLevel(DEFAULT_LOGGER_LVL);
        this.filename = filename;
        File f = new File(filename);
        createDefaultProperies(); 
    }
    
    public SimpleProperties(String filename, Level lvl) {
        this(filename);
        logger.setLevel(Level.INFO);
        logger.info("Created object with filename: {} and logger level: {}",
            filename,
            DEFAULT_LOGGER_LVL.levelStr);
    }
    
    private void createDefaultProperies() {
        portName = DEFAULT_PORT_NAME;
        lightLVL = DEFAULT_LIGHT;
        refreshTime = DEFAULT_REFRESH_TIME;
        voltInertiaRatio = DEFAULT_VIRP;
        notifyInStartup = DEFAULT_NOTIFY_STARTUP;
        notifyInBackground = DEFAULT_NOTIFY_BACKGROUND;
        runOnStartup = DEFAULT_RUN_ON_STARTUP;
        doneMinimizedInfo = DEFAULT_DONE_MINIMIZED_INFO;
        framePositionX = DEFAULT_FRAME_POSITION_X;
        framePositionY = DEFAULT_FRAME_POSITION_Y;
        
        logger.info("Loaded default properties");
    }
    
    private void saveProperties() {
        try (OutputStream output = new FileOutputStream(filename)) {
            Properties prop = new Properties();
            prop.setProperty(portNameProperty, portName);
            prop.setProperty(lightProperty, String.valueOf(lightLVL));
            prop.setProperty(refreshTimeProperty, String.valueOf(refreshTime));
            prop.setProperty(voltInertiaRatioProperty, String.valueOf(getVoltIntertiaRatio()));
            prop.setProperty(notifyInStartupProperty, String.valueOf(getNotifyInStartup()));
            prop.setProperty(notifyInBackgroundProperty, String.valueOf(getNotifyInBackground()));
            prop.setProperty(runOnStartupProperty, String.valueOf(getRunOnStartup()));
            prop.setProperty(doneMinimizedInfoProperty, String.valueOf(getDoneMinimizedInfo()));
            prop.setProperty(framePositionXProperty, String.valueOf(framePositionX));
            prop.setProperty(framePositionYProperty, String.valueOf(framePositionY));
            prop.store(output, null);
            logger.info("Wrote properties to file");

        } catch (IOException io) {
            logger.error("Exception: {} on write properties to file {}", io.getMessage(), filename);
        }
    }
    
    public void writeProperties(
            String portName, 
            int lightLVL, 
            int refreshTime, 
            int voltIntertiaRatio,
            boolean notifyInStartup,
            boolean notifyInBackground,
            boolean runOnStartup,
            boolean doneMinimizedInfo,
            Point framePosition) {
        this.portName = portName;
        this.lightLVL = lightLVL;
        this.refreshTime = refreshTime;
        this.voltInertiaRatio = voltIntertiaRatio;
        this.notifyInStartup = notifyInStartup;
        this.notifyInBackground = notifyInBackground;
        this.runOnStartup = runOnStartup;
        this.doneMinimizedInfo = doneMinimizedInfo;
        this.framePositionX = framePosition.x;
        this.framePositionY = framePosition.y;
        logger.info("Loaded external properties to local variables");
        saveProperties();  
    }
    public void readProperties() {
        try (InputStream input = new FileInputStream(filename)) {
            Properties prop = new Properties();
            prop.load(input);
            String temp;
            
            temp = prop.getProperty(portNameProperty);
            portName = temp != null ? temp : DEFAULT_PORT_NAME;
            
            temp = prop.getProperty(lightProperty);
            lightLVL = temp != null ? Integer.parseInt(temp) : DEFAULT_LIGHT;
            
            temp = prop.getProperty(refreshTimeProperty);
            refreshTime = temp != null ? Integer.parseInt(temp) : DEFAULT_REFRESH_TIME;
            
            temp = prop.getProperty(voltInertiaRatioProperty);
            voltInertiaRatio = temp != null ? Integer.parseInt(temp) : DEFAULT_VIRP; 
            
            temp = prop.getProperty(notifyInStartupProperty);
            notifyInStartup = temp != null ? temp.equals("true") : DEFAULT_NOTIFY_STARTUP;
            
            temp = prop.getProperty(notifyInBackgroundProperty);
            notifyInBackground = temp != null ? temp.equals("true") : DEFAULT_NOTIFY_BACKGROUND;
            
            temp = prop.getProperty(runOnStartupProperty);
            runOnStartup = temp != null ? temp.equals("true") : DEFAULT_RUN_ON_STARTUP;
            
            temp = prop.getProperty(doneMinimizedInfoProperty);
            doneMinimizedInfo = temp != null ? temp.equals("true") : DEFAULT_DONE_MINIMIZED_INFO;
            
            temp = prop.getProperty(framePositionXProperty);
            framePositionX = temp != null ? Integer.parseInt(temp) : DEFAULT_FRAME_POSITION_X; 
            
            temp = prop.getProperty(framePositionYProperty);
            framePositionY = temp != null ? Integer.parseInt(temp) : DEFAULT_FRAME_POSITION_Y; 
            
            logger.info("read all properties from file");
        } catch (IOException ex) {
            logger.error("Exception {} on read properties from file {}", ex.getMessage(), filename);
        }

    }

    /**
     * @return the portName
     */
    public String getPortName() {
        logger.trace("returned port name: {}", portName);
        return portName;
    }

    /**
     * @return the light
     */
    public int getLight() {
        logger.trace("returned light lvl: {}", lightLVL);
        return lightLVL;
    }

    /**
     * @return the refreshTime
     */
    public int getRefreshTime() {
        logger.trace("returned refresh time in ms: {}", refreshTime);
        return refreshTime;
    }

    /**
     * @return the voltInertiaRatio
     */
    public int getVoltIntertiaRatio() {
        logger.trace("returned voltometer inertial ratio: {}", voltInertiaRatio);
        return voltInertiaRatio;
    }   

    /**
     * @return the notifyInStartup
     */
    public Boolean getNotifyInStartup() {
        logger.trace("returned notify in startup status: {}", notifyInStartup);
        return notifyInStartup;
    }

    /**
     * @return the notifyInBackground
     */
    public Boolean getNotifyInBackground() {
        logger.trace("returned notify in background status: {}", notifyInBackground);
        return notifyInBackground;
    }
    
    /**
     * @return the runOnStartup
     */
    public Boolean getRunOnStartup() {
        logger.trace("returned run in startup status: {}", runOnStartup);
        return runOnStartup;
    }
    /**
     * @return the doneMinimizedInfo
     */
    public Boolean getDoneMinimizedInfo() {
        logger.trace("returned done minimized info status: {}", doneMinimizedInfo);
        return doneMinimizedInfo;
    }
    
    public Point getFramePosition() {
        return new Point(framePositionX, framePositionY);
    }
}
