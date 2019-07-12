/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mist.comstatssender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import com.fazecast.jSerialComm.SerialPort;

//import org.slf4j.LoggerFactory;
public class COMSender {
    protected final Level DEFAULT_LOGGER_LVL = Level.WARN; 
    protected Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(this.getClass().getName());
    
    protected final SerialPort PORT;
    protected final byte START_BIT = (byte)0xFF;
    protected boolean opened;
    
    public COMSender(SerialPort port) {
        this.PORT = port;
        if (port != null) {
            port.setComPortParameters(9600, 8, 1, 0);
            port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
            opened = port.openPort();
        } else {
            opened = false;
        }
        logger.setLevel(DEFAULT_LOGGER_LVL);
    }
    public COMSender(SerialPort port, Level lvl) {
        this(port);
        logger.setLevel(Level.INFO);
        logger.info("Created object with logs on level: {} and port: {}, opened with result: {}", 
                lvl.levelStr,
                port != null ? port.getSystemPortName() : "null",
                opened);
        logger.setLevel(lvl);
    }
    public boolean isOpened() {
        logger.trace("Returned open port status: {}", String.valueOf(opened));
        return opened;
    }
    
    public boolean close() {
        logger.setLevel(Level.INFO);
        if (opened) {
            boolean result = PORT.closePort();
            opened = !result;
            logger.info("Closed port {} with result: {}", PORT.getSystemPortName(), result);
            return result;
        } else {
            logger.warn("Tried to close port {} but port not opened", PORT.getSystemPortName());
            return false;
        }
    }
    /**
     * @return the PORT
     */
    public SerialPort getPort() {
        logger.trace("Returned port object");
        return PORT;
    }
    
    public boolean send(int cpuUsage, int ramUsage, int lightLVL, int voltMoveRatio) {
        byte[] buffer = { START_BIT, (byte)cpuUsage, (byte)ramUsage, (byte)lightLVL, (byte)voltMoveRatio };
        if (!opened) {
            logger.warn("Tried to send bytes but port {} not opened", PORT.getSystemPortName());
            return false;
        }
        try {
            opened = PORT.isOpen();
            if (PORT.writeBytes(buffer, buffer.length) != buffer.length) {
                logger.error("Number of sent bytes not equals input bytes");
                opened = PORT.isOpen();
                return false;
            }
        } catch (Exception e) {
            logger.error("Exception: ", e.getMessage());
            return false;
        }
        logger.info("Sent {} bytes to port: {}", buffer.length, PORT.getSystemPortName());
        return true;
    }
}
