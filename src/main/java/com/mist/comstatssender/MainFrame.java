/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mist.comstatssender;

import com.fazecast.jSerialComm.SerialPort;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.awt.Color;
import java.util.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import static java.lang.Math.round;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
//import javax.swing.Timer;
import java.util.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.TrayIcon;
import java.util.TimerTask;
import javax.swing.JRootPane;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.net.URL;
import java.util.Arrays;
import org.slf4j.LoggerFactory;

/**
 *
 * @author MS-1
 */
public class MainFrame extends javax.swing.JFrame {
    final Level LOGGER_LVL = Level.INFO;
    Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(this.getClass().getName());
    
    PlatformFactory platformFactory = new PlatformFactory(LOGGER_LVL);
    
    DisplayTrayIcon trayIcon;
    SerialPort[] portList;
    COMSender comSender;
    boolean reloadCOMSender;
    boolean blockComChange;
    SimpleProperties simpleProps;
    final String PROPS_FILENAME = "properies.conf";
    Timer timer;
    final String CLOSE_TIMER_TICK = "closeTick";
    
    int cpuLoad, ramLoad;
    Average cpuLoadAvg = new Average(3);
    Average ramLoadAvg = new Average(3);
    int refreshTime = 250;
    final int voltInertialRatioMultiplier = 1;
    final int maxVoltIntertiaRatio = voltInertialRatioMultiplier * 10;
    int voltInertialRatio;
    static OneServer server = null;
    
    
    boolean timerIsWorking = true;
    final int refreshTimeMultiplier = 50;
    ProgramState actualState = ProgramState.NORMAL;
    final Color NORMAL_FONT_COLOR = new Color(0, 0, 0);
    final Color CALIBRATE_FONT_COLOR = Color.RED;
    static final String PROGRAM_NAME = "COM Stats Sender 1.0";
    
    boolean startup = true;
    int counterToWritePropsMs = 0; // > 0 - decrement, -1 - to write properties, 0 - stop
    final int WRITE_PROPS_MS = 2000;
    final String shortcutFilename = "COMStatsSender.lnk";
    boolean enableOptions = false;
    boolean doneMinimizedInfo = false;
    final Dimension DEFAULT_SIZE;
    
    public enum ProgramState {
        NORMAL,
        CALIBRATION,
        UNDEFINED
    }
    
    private static class PortDifference {
        public String portName;
        public DifferenceDirection difference;
        public enum DifferenceDirection { 
            ADD, 
            REMOVE
        }
        public PortDifference() {}
        public PortDifference(String portName, DifferenceDirection difference) {
            this.portName = portName;
            this.difference = difference;
        }      
    }
    
    private class Average {
        private double[] table;
        public Average(int avgLength) {
            table = new double[avgLength];
        }
        
        public void addValue(double value) {
            for(int i = 0; i < table.length - 1; i++) {
                table[i] = table[i + 1];
            }
            table[table.length - 1] = value;
        }
        
        public double getAverage() {
            return Arrays.stream(table).average().getAsDouble();
        }
    }
    
    /**
     * Creates new form MainFrame
     */
    public MainFrame() {
        initComponents();
        DEFAULT_SIZE = this.getSize();
        //System.out.println(PlatformFactory.getOperatingSystem());
        this.setTitle(PROGRAM_NAME);
        
        URL ImageURL = DisplayTrayIcon.class.getResource("ComStatsSender-PNG.png");
        //URL ImageURL = getClass().getResource(path);
        this.setIconImage(new ImageIcon(ImageURL, "").getImage());
        
        optionsChange(false);
        logger.info("Start to create main frame with logs on level: {}", LOGGER_LVL);
        logger.setLevel(LOGGER_LVL);
        
        
        simpleProps = new SimpleProperties(PROPS_FILENAME, LOGGER_LVL);
        simpleProps.readProperties();
        
        this.setLocation(simpleProps.getFramePosition());
        doneMinimizedInfo = simpleProps.getDoneMinimizedInfo();
        trayIcon = new DisplayTrayIcon(this, !doneMinimizedInfo);
        doneMinimizedInfo = true;
        
        comSender = new COMSender(SerialPort.getCommPort(simpleProps.getPortName()), Level.WARN);
        
        refreshTime = simpleProps.getRefreshTime();
        voltInertialRatio = simpleProps.getVoltIntertiaRatio();
        jSliderRatio.setValue(voltInertialRatio / voltInertialRatioMultiplier);
        jSliderDelay.setValue(refreshTime / refreshTimeMultiplier);
        jSliderLight.setValue(simpleProps.getLight());
        
        jCheckNotifyStartup.setSelected(simpleProps.getNotifyInStartup());
        jCheckNotifyBackground.setSelected(simpleProps.getNotifyInBackground());
        jCheckStartWin.setSelected(simpleProps.getRunOnStartup());
        
        
        setProgramState(ProgramState.NORMAL);
//        timer = new Timer(refreshTime, new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent evt) {
//                
//                timerTick();
//            }
//        });
        
        loadCOMs(simpleProps.getPortName());
        loadStats();

        startup = false;
        reloadTimer(refreshTime);
        platformFactory.setRunInStartup(jCheckStartWin.isSelected(), shortcutFilename);
    }
    
    private void reloadTimer(int tickTimeMs) {
        try {
            timer.cancel();
            timer.purge();
            timer = null;
        } catch (Exception e) {
            logger.info("Tried clear timer but timer is null");
        }
        timer = new Timer();
        TimerTask task = new TimerTask() {
        @Override
            public void run() {
                if (timerIsWorking == true) {
                    timerTick();
                }
            }
        };
        timer.schedule(task, 50, tickTimeMs);
        logger.info("Reload time of timer tick to {} ms", tickTimeMs);
    }
    
    private void timerTick() {
        //Refresh the panel
        logger.debug("timer ticked");
        
        //System.out.println(java.time.LocalTime.now());  
        if (counterToWritePropsMs > 0) {
            counterToWritePropsMs -= refreshTime;
            if (counterToWritePropsMs <= 0)
                counterToWritePropsMs = -1;
        }
        if (counterToWritePropsMs == -1) {
            writeProperties();
            counterToWritePropsMs = 0;
        }
        
        
        if (portList.length != SerialPort.getCommPorts().length) {
            loadCOMs();
        }
        if (reloadCOMSender) {
            comSender.close();
            timerIsWorking = false;
            if (jComboCOM.getSelectedIndex() >= 0) {
                
                comSender = new COMSender(portList[jComboCOM.getSelectedIndex()], Level.WARN);
                if (comSender.isOpened() == false) {
                    JOptionPane.showMessageDialog(null, "Error on open port event", PROGRAM_NAME, JOptionPane.WARNING_MESSAGE);
                }
            }
            //timer.start();
            timerIsWorking = true;
            reloadCOMSender = false;
        }
        loadStats();
        if (comSender.isOpened()) {
            comSender.send(cpuLoad, ramLoad, jSliderLight.getValue(), maxVoltIntertiaRatio + 1 - voltInertialRatio);
        }
       //jComboCOM.setForeground(NORMAL_FONT_COLOR);
    }
    
    private void writeAfterDelay() {
        if (!startup) {
            logger.info("Start counter to write properties to file");
            counterToWritePropsMs = WRITE_PROPS_MS;
        } else {
            logger.info("Tried to start counter to write properties to file but bloced by startup flag");
        }
    }
    
    private PortDifference[] getPortDifferences(SerialPort[] oldList, SerialPort[] newList) {
        List<PortDifference> differences = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        
        if (oldList != null) {
            for (SerialPort i: oldList) {
                map.merge(i.getSystemPortName(), 1,Integer::sum);
            }
        }
        if (newList != null) {
            for (SerialPort i: newList) {
                map.merge(i.getSystemPortName(), 2,Integer::sum);
            }
        }
        Map<String, Integer> treeMap = new TreeMap<>(map);
        
        for(String i: treeMap.keySet()) {
            if (treeMap.get(i) == 1)
                differences.add(new PortDifference(i, PortDifference.DifferenceDirection.REMOVE));
            if (treeMap.get(i) == 2)
                differences.add(new PortDifference(i, PortDifference.DifferenceDirection.ADD));
        }
        PortDifference[] finalDifferences = differences.toArray(new PortDifference[0]);
        logger.info("Loaded ports differences list with length: {}", finalDifferences.length);
        return finalDifferences; 
    }
    
    /* loading COM ports to ComboBox */
    private void loadCOMs(String nameToSelect) {
        SerialPort[] newPortList = SerialPort.getCommPorts();
        if (((startup == true) && jCheckNotifyStartup.isSelected()) || (jCheckNotifyBackground.isSelected() && (startup == false))) {
            PortDifference[] differences = getPortDifferences(portList, newPortList);
            String info = "";
            if (differences != null) {
                for (PortDifference i: differences) {
                    if (i.difference == PortDifference.DifferenceDirection.ADD) {
                        info += "Added: " + i.portName + "\n";
                    } else if (i.difference == PortDifference.DifferenceDirection.REMOVE) {
                        info += "Removed: " + i.portName + "\n";
                    }
                }
            }
            if (info.length() > 0) {}
                trayIcon.display(info, TrayIcon.MessageType.NONE);
        }
        
        blockComChange = true;
        portList = newPortList.clone();
        Object selected = (Object)nameToSelect;
        jComboCOM.removeAllItems();
        for (SerialPort i: portList) {
            jComboCOM.addItem(i.getSystemPortName());
        }
        blockComChange = false;
        if (selected != null) {
            jComboCOM.setSelectedItem(selected);
        } else jComboCOM.setSelectedItem(0);
        logger.info("Loaded new port list with length: {}", portList.length);
        changeSelectedCOM();
    }
    
    private void loadCOMs() {
        loadCOMs((String)jComboCOM.getSelectedItem());
    }
    
    /* writing cpuLoad and ramLoad from system to variables */
    private void loadStats() {
        if (actualState == ProgramState.NORMAL) {
            OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean();
            //cpuLoad =(int)round(osBean.getSystemCpuLoad() * 100);
            cpuLoadAvg.addValue(osBean.getSystemCpuLoad());
            cpuLoad = (int)(cpuLoadAvg.getAverage() * 100);
            ramLoadAvg.addValue(platformFactory.getRamLoad());
            ramLoad =(int)(ramLoadAvg.getAverage() * 100);
        } else {
            cpuLoad = 100;
            ramLoad = 100;
        }
        
        jTextCPU.setText(String.format("%d %%", cpuLoad));
        jTextRAM.setText(String.format("%d %%", ramLoad));
        logger.debug("Loaded stats CPU: {} %, RAM: {} % with program status {}", cpuLoad, ramLoad, actualState);
    }
    
    private void writeProperties() {
        String name = comSender != null ? comSender.getPort().getSystemPortName() : "";
        simpleProps.writeProperties(name, 
                jSliderLight.getValue(), 
                refreshTime,
                voltInertialRatio,
                jCheckNotifyStartup.isSelected(),
                jCheckNotifyBackground.isSelected(),
                jCheckStartWin.isSelected(),
                doneMinimizedInfo,
                this.getLocation());
        platformFactory.setRunInStartup(jCheckStartWin.isSelected(), shortcutFilename);
    }
    
    private void setProgramState(ProgramState state) {
        if ((state != actualState) && (state != ProgramState.UNDEFINED)) {
            actualState = state;
            switch(state) {
                case NORMAL: {
                    jTextCPU.setForeground(NORMAL_FONT_COLOR);
                    jTextRAM.setForeground(NORMAL_FONT_COLOR);
                    jButtonState.setForeground(NORMAL_FONT_COLOR);
                    jButtonState.setText("Calibrate ON");
                } break;
                case CALIBRATION: {
                    jTextCPU.setForeground(CALIBRATE_FONT_COLOR);
                    jTextRAM.setForeground(CALIBRATE_FONT_COLOR);
                    jButtonState.setForeground(CALIBRATE_FONT_COLOR);
                    jButtonState.setText("Calibrate OFF");
                } break;
            }
            logger.info("Changed program state to: {}", state);
        }
    }
    
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        trayIcon.setOpenEnabled(!visible);
    }
    
    public int getCPULoad() {
        logger.trace("returned cpu load: {}", cpuLoad);
        return cpuLoad;
    }
    
    public int getRAMLoad() {
        logger.trace("returned ram load: {}", ramLoad);
        return ramLoad;
    }
    
    public Image getImage() {
        return this.getIconImage();
    }
    
    public String getProgramName() {
        logger.trace("Returned program name: {}", PROGRAM_NAME);
        return PROGRAM_NAME;
    }
    
    /* on closing app event */
    public void close() { 
        //timer.stop();
        timerIsWorking = false;
        sendCOMNulls();
        comSender.close();
        writeProperties();
        logger.info("Close frame event");
        this.setVisible(false);
        this.dispose();
        System.exit(0);
    }
    
    
    
    private void reverseProgramState() {
        if ((actualState == ProgramState.CALIBRATION) || (actualState == ProgramState.UNDEFINED)) {
            setProgramState(ProgramState.NORMAL);
        } else {
            setProgramState(ProgramState.CALIBRATION);
        }
    }
    
    private void sendCOMNulls() {
        //timer.stop();
        timerIsWorking = false;
        cpuLoad = 0;
        ramLoad = 0;
        
        if (comSender != null) {
            comSender.send(cpuLoad, ramLoad, 0, maxVoltIntertiaRatio + 1 - voltInertialRatio);
        }
        //timer.start();
        timerIsWorking = true;
        logger.info("Sent empty values to port");
    }
    
    boolean comboAndObjectCOMDifference() {
        if ((!comSender.isOpened()) && (jComboCOM.getSelectedIndex() < 0))
            return false;
        return (((!comSender.isOpened()) && (jComboCOM.getSelectedIndex() >= 0)) 
                || (comSender.isOpened() && (jComboCOM.getSelectedIndex() < 0)) 
                || (!portList[jComboCOM.getSelectedIndex()].getSystemPortName().equals(comSender.getPort().getSystemPortName())));
    }
    private void changeSelectedCOM() {
        if ((!blockComChange) && (comboAndObjectCOMDifference())) {
            sendCOMNulls();
            comSender.close();
            if (jComboCOM.getSelectedIndex() != -1) {
                reloadCOMSender = true;
            }
            writeAfterDelay();
            logger.info("Commit to reload port");
        } else {
            logger.warn("Tried to change selected port, but option blocked by flag or no difference");
        }
    }
    
    private void optionsChange(boolean enableOptions) {
        this.enableOptions = enableOptions;
        if (this.enableOptions) {
            jButtonOptions.setText("Close options");
            this.setSize(DEFAULT_SIZE.width, DEFAULT_SIZE.height);
        } else {
            jButtonOptions.setText("Options");
            this.setSize(DEFAULT_SIZE.width, jPanelParams.getLocation().y + 114);

        }
        jPanelVoltRatio.setVisible(this.enableOptions);
        jPanelLightLvl.setVisible(this.enableOptions);
        jPanelRefreshT.setVisible(this.enableOptions);
        jPanelNotify.setVisible(this.enableOptions);
        jButtonState.setVisible(this.enableOptions);
        jCheckStartWin.setVisible(this.enableOptions);
        logger.info("Changed options state to: {}", enableOptions);
    }
    
    private Point getCenterOfScreen() {
        return new Point(0, 0);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jComboCOM = new javax.swing.JComboBox<>();
        jLabel1 = new javax.swing.JLabel();
        jPanelParams = new javax.swing.JPanel();
        jTextRAM = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jTextCPU = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jPanelRefreshT = new javax.swing.JPanel();
        jTextDelay = new javax.swing.JTextField();
        jSliderDelay = new javax.swing.JSlider();
        jButtonState = new javax.swing.JButton();
        jPanelVoltRatio = new javax.swing.JPanel();
        jTextRatio = new javax.swing.JTextField();
        jSliderRatio = new javax.swing.JSlider();
        jPanelNotify = new javax.swing.JPanel();
        jCheckNotifyStartup = new javax.swing.JCheckBox();
        jCheckNotifyBackground = new javax.swing.JCheckBox();
        jPanelLightLvl = new javax.swing.JPanel();
        jTextLight = new javax.swing.JTextField();
        jSliderLight = new javax.swing.JSlider();
        jButtonOptions = new javax.swing.JButton();
        jCheckStartWin = new javax.swing.JCheckBox();

        setTitle("COM Stats Sender 1.0");
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setFocusTraversalPolicyProvider(true);
        setLocation(new java.awt.Point(100, 100));
        setMinimumSize(new java.awt.Dimension(271, 144));
        setResizable(false);
        setSize(new java.awt.Dimension(0, 0));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowIconified(java.awt.event.WindowEvent evt) {
                formWindowIconified(evt);
            }
        });

        jComboCOM.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jComboCOM.setName(""); // NOI18N
        jComboCOM.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jComboCOMItemStateChanged(evt);
            }
        });
        jComboCOM.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboCOMActionPerformed(evt);
            }
        });
        jComboCOM.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                jComboCOMPropertyChange(evt);
            }
        });

        jLabel1.setText("Port:");

        jPanelParams.setBorder(javax.swing.BorderFactory.createTitledBorder("Params"));

        jTextRAM.setEditable(false);
        jTextRAM.setText("0 %");

        jLabel3.setText("RAM:");

        jTextCPU.setEditable(false);
        jTextCPU.setText("0 %");

        jLabel2.setText("CPU:");

        javax.swing.GroupLayout jPanelParamsLayout = new javax.swing.GroupLayout(jPanelParams);
        jPanelParams.setLayout(jPanelParamsLayout);
        jPanelParamsLayout.setHorizontalGroup(
            jPanelParamsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelParamsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextCPU, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextRAM, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelParamsLayout.setVerticalGroup(
            jPanelParamsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelParamsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelParamsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextCPU, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jTextRAM, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelRefreshT.setBorder(javax.swing.BorderFactory.createTitledBorder("Refresh time"));

        jTextDelay.setEditable(false);
        jTextDelay.setText("200 ms");

        jSliderDelay.setMajorTickSpacing(1);
        jSliderDelay.setMaximum(10);
        jSliderDelay.setMinimum(1);
        jSliderDelay.setMinorTickSpacing(1);
        jSliderDelay.setPaintTicks(true);
        jSliderDelay.setToolTipText("");
        jSliderDelay.setValue(4);
        jSliderDelay.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSliderDelayStateChanged(evt);
            }
        });
        jSliderDelay.addVetoableChangeListener(new java.beans.VetoableChangeListener() {
            public void vetoableChange(java.beans.PropertyChangeEvent evt)throws java.beans.PropertyVetoException {
                jSliderDelayVetoableChange(evt);
            }
        });

        javax.swing.GroupLayout jPanelRefreshTLayout = new javax.swing.GroupLayout(jPanelRefreshT);
        jPanelRefreshT.setLayout(jPanelRefreshTLayout);
        jPanelRefreshTLayout.setHorizontalGroup(
            jPanelRefreshTLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRefreshTLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSliderDelay, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextDelay, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelRefreshTLayout.setVerticalGroup(
            jPanelRefreshTLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRefreshTLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelRefreshTLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextDelay, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jSliderDelay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jButtonState.setText("Calibrate ON");
        jButtonState.setToolTipText("");
        jButtonState.setPreferredSize(new java.awt.Dimension(100, 23));
        jButtonState.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonStateActionPerformed(evt);
            }
        });

        jPanelVoltRatio.setBorder(javax.swing.BorderFactory.createTitledBorder("Voltmeters Inertia Ratio"));

        jTextRatio.setEditable(false);
        jTextRatio.setText("4");

        jSliderRatio.setMajorTickSpacing(1);
        jSliderRatio.setMaximum(10);
        jSliderRatio.setMinimum(1);
        jSliderRatio.setMinorTickSpacing(1);
        jSliderRatio.setPaintTicks(true);
        jSliderRatio.setToolTipText("");
        jSliderRatio.setValue(4);
        jSliderRatio.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSliderRatioStateChanged(evt);
            }
        });

        javax.swing.GroupLayout jPanelVoltRatioLayout = new javax.swing.GroupLayout(jPanelVoltRatio);
        jPanelVoltRatio.setLayout(jPanelVoltRatioLayout);
        jPanelVoltRatioLayout.setHorizontalGroup(
            jPanelVoltRatioLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelVoltRatioLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSliderRatio, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextRatio, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelVoltRatioLayout.setVerticalGroup(
            jPanelVoltRatioLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelVoltRatioLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelVoltRatioLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextRatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jSliderRatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelNotify.setBorder(javax.swing.BorderFactory.createTitledBorder("Notifications"));

        jCheckNotifyStartup.setText("on startup");
        jCheckNotifyStartup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckNotifyStartupActionPerformed(evt);
            }
        });

        jCheckNotifyBackground.setText("on background");
        jCheckNotifyBackground.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckNotifyBackgroundActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelNotifyLayout = new javax.swing.GroupLayout(jPanelNotify);
        jPanelNotify.setLayout(jPanelNotifyLayout);
        jPanelNotifyLayout.setHorizontalGroup(
            jPanelNotifyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelNotifyLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jCheckNotifyStartup)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jCheckNotifyBackground)
                .addContainerGap())
        );
        jPanelNotifyLayout.setVerticalGroup(
            jPanelNotifyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelNotifyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jCheckNotifyStartup)
                .addComponent(jCheckNotifyBackground))
        );

        jPanelLightLvl.setBorder(javax.swing.BorderFactory.createTitledBorder("Light Level"));

        jTextLight.setEditable(false);
        jTextLight.setText("4");

        jSliderLight.setMajorTickSpacing(1);
        jSliderLight.setMaximum(10);
        jSliderLight.setMinorTickSpacing(1);
        jSliderLight.setPaintTicks(true);
        jSliderLight.setToolTipText("");
        jSliderLight.setValue(4);
        jSliderLight.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSliderLightStateChanged(evt);
            }
        });

        javax.swing.GroupLayout jPanelLightLvlLayout = new javax.swing.GroupLayout(jPanelLightLvl);
        jPanelLightLvl.setLayout(jPanelLightLvlLayout);
        jPanelLightLvlLayout.setHorizontalGroup(
            jPanelLightLvlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLightLvlLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSliderLight, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextLight, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelLightLvlLayout.setVerticalGroup(
            jPanelLightLvlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLightLvlLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelLightLvlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextLight, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jSliderLight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jButtonOptions.setText("Options");
        jButtonOptions.setToolTipText("");
        jButtonOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonOptionsActionPerformed(evt);
            }
        });

        jCheckStartWin.setText("start with Windows");
        jCheckStartWin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckStartWinActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jCheckStartWin)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButtonState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboCOM, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(27, 27, 27)
                        .addComponent(jButtonOptions, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanelParams, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelNotify, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelRefreshT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelLightLvl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelVoltRatio, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jComboCOM, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonOptions))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelParams, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonState, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jCheckStartWin))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelNotify, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelRefreshT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelLightLvl, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelVoltRatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        getAccessibleContext().setAccessibleName("frame1");
        getAccessibleContext().setAccessibleDescription("");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jSliderDelayStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jSliderDelayStateChanged
        logger.info("jSliderDelay state changed event");
        refreshTime = jSliderDelay.getValue() * refreshTimeMultiplier;
        jTextDelay.setText(String.format("%d ms", refreshTime));
        //if (timer != null)
        //    timer.setDelay(refreshTime);
        reloadTimer(refreshTime);
        writeAfterDelay();
        logger.debug("END jSliderDelay state changed event");
    }//GEN-LAST:event_jSliderDelayStateChanged

    private void formWindowIconified(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowIconified
        if (trayIcon.isSupported()) {
            logger.info("Frame minimized event, app to tray");
            this.setVisible(false);
            trayIcon.setOpenEnabled(true);
        } else {
            logger.info("Frame minimized event, unsupported tray icon");
        }
        logger.debug("END Frame minimized event");
    }//GEN-LAST:event_formWindowIconified

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (trayIcon.isSupported()) {
            logger.info("Frame closing event, app to tray");
            this.setExtendedState(JFrame.ICONIFIED);
        } else {
            logger.info("Frame closing event, unsupported tray icon, closing app");
            close();
        }
        logger.debug("END Frame closing event");
    }//GEN-LAST:event_formWindowClosing

    private void jComboCOMActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboCOMActionPerformed
        logger.info("jComboCOM action performed event");
        changeSelectedCOM();
        logger.debug("END jComboCOM action performed event");
    }//GEN-LAST:event_jComboCOMActionPerformed

    private void jButtonStateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonStateActionPerformed
        logger.info("jButtonState click event");
        reverseProgramState();
        logger.debug("END jButtonState click event");
    }//GEN-LAST:event_jButtonStateActionPerformed

    private void jSliderRatioStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jSliderRatioStateChanged
        logger.info("jSliderRatio state changed event");
        voltInertialRatio = jSliderRatio.getValue() * voltInertialRatioMultiplier;
        jTextRatio.setText(String.valueOf(jSliderRatio.getValue()));
        writeAfterDelay();
        logger.debug("END jSliderRatio state changed event");
    }//GEN-LAST:event_jSliderRatioStateChanged

    private void jCheckNotifyStartupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckNotifyStartupActionPerformed
        logger.info("jCheckNotifyStatus action performed event");
        writeAfterDelay();
	}//GEN-LAST:event_jCheckNotifyStartupActionPerformed

    private void jCheckNotifyBackgroundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckNotifyBackgroundActionPerformed
        logger.info("jCheckNotifyBackground action performed event");
        writeAfterDelay();
    }//GEN-LAST:event_jCheckNotifyBackgroundActionPerformed

    private void jComboCOMPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_jComboCOMPropertyChange

    }//GEN-LAST:event_jComboCOMPropertyChange

    private void jComboCOMItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jComboCOMItemStateChanged

    }//GEN-LAST:event_jComboCOMItemStateChanged

    private void jSliderLightStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jSliderLightStateChanged
        logger.info("jSliderLight state changed event to value: {}", jSliderLight.getValue());
        jTextLight.setText(String.valueOf(jSliderLight.getValue()));
        writeAfterDelay();
        logger.debug("END jSliderLight state changed event");
    }//GEN-LAST:event_jSliderLightStateChanged

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
    
    }//GEN-LAST:event_formWindowClosed

    private void jSliderDelayVetoableChange(java.beans.PropertyChangeEvent evt)throws java.beans.PropertyVetoException {//GEN-FIRST:event_jSliderDelayVetoableChange
        // TODO add your handling code here:
    }//GEN-LAST:event_jSliderDelayVetoableChange

    private void jButtonOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonOptionsActionPerformed
        logger.info("jButtonOptions state changed event");
        optionsChange(!enableOptions);
    }//GEN-LAST:event_jButtonOptionsActionPerformed

    private void jCheckStartWinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckStartWinActionPerformed
        logger.info("jCheckStartWin action performed event");
        writeAfterDelay();
    }//GEN-LAST:event_jCheckStartWinActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {       
        try {
            for (UIManager.LookAndFeelInfo info: UIManager.getInstalledLookAndFeels()) {
                //System.out.println(info.getName());
                if ("Windows".equals(info.getName())) {
                    
                    UIManager.setLookAndFeel(info.getClassName());
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException e) {
        
        }
        
        try {
            Socket clientSocket = new Socket("localhost", OneServer.PORT);
            JOptionPane.showMessageDialog(null, "App already running", PROGRAM_NAME, JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }
        catch (IOException e) {
            server = new OneServer();
            server.start();
        }
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                MainFrame mainFrame = new MainFrame();
                mainFrame.setVisible(false);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonOptions;
    private javax.swing.JButton jButtonState;
    private javax.swing.JCheckBox jCheckNotifyBackground;
    private javax.swing.JCheckBox jCheckNotifyStartup;
    private javax.swing.JCheckBox jCheckStartWin;
    private javax.swing.JComboBox<String> jComboCOM;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanelLightLvl;
    private javax.swing.JPanel jPanelNotify;
    private javax.swing.JPanel jPanelParams;
    private javax.swing.JPanel jPanelRefreshT;
    private javax.swing.JPanel jPanelVoltRatio;
    private javax.swing.JSlider jSliderDelay;
    private javax.swing.JSlider jSliderLight;
    private javax.swing.JSlider jSliderRatio;
    private javax.swing.JTextField jTextCPU;
    private javax.swing.JTextField jTextDelay;
    private javax.swing.JTextField jTextLight;
    private javax.swing.JTextField jTextRAM;
    private javax.swing.JTextField jTextRatio;
    // End of variables declaration//GEN-END:variables
}
