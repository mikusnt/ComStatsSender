/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mist.comstatssender;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 *
 * @author MS-1
 */
public class DisplayTrayIcon {
    private TrayIcon trayIcon;
    private final MainFrame MAIN_FRAME;
    private boolean isSupported;
    
    public DisplayTrayIcon(MainFrame frame, boolean withInfo) {
        MAIN_FRAME = frame;
        ShowTrayIcon(frame.getImage(), withInfo);
    }
    
    private void openMainFrame() {
        MAIN_FRAME.setVisible(true);
        MAIN_FRAME.setExtendedState(JFrame.NORMAL);
        setOpenEnabled(false);
    }
    
    /**
     * @param filename of icon
    */
    private void ShowTrayIcon(Image image, boolean withInfo) {
        if (!SystemTray.isSupported()) {
            isSupported = false;
            return;
        } else {
            isSupported = true;
        }
        
        final PopupMenu menu = new PopupMenu();
        
        try {
            trayIcon = new TrayIcon(image);
            trayIcon.setImageAutoSize(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error on open icon in tray app", MAIN_FRAME.getProgramName(), JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        final SystemTray tray = SystemTray.getSystemTray();
        
        MenuItem open = new MenuItem("Open");
        open.setEnabled(false);
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openMainFrame();
            }
        });
        MenuItem close = new MenuItem("Exit");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MAIN_FRAME.close();
                System.exit(0); 
            }
        });
        menu.add(open);
        menu.addSeparator();
        menu.add(close);
        
        trayIcon.setPopupMenu(menu);
        trayIcon.setToolTip(MAIN_FRAME.getProgramName());
        trayIcon.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openMainFrame();
            }
        });
        try {
            tray.add(trayIcon);
            if (withInfo) {
                display("App is minimized", TrayIcon.MessageType.NONE);
            }
        } catch(AWTException e) {
            
        }
        
    }
    
    public void display(String text, TrayIcon.MessageType messageType) {
        if (isSupported)
        trayIcon.displayMessage(MAIN_FRAME.getProgramName(), text, messageType);
    }
    
    /**
     @param value of enable Open menu item
     */
    public void setOpenEnabled(boolean value) {
        if (isSupported)
        trayIcon.getPopupMenu().getItem(0).setEnabled(value);
    }

    
    public boolean isSupported() {
        return isSupported;
    }
}


