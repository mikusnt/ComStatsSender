/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mist.comstatssender;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author MS-1
 */
public class OneServer extends Thread {
    public static final int PORT = 5523;
    
   ServerSocket serverSocket = null;
   Socket clientSocket = null;

    @Override
   public void run() {
        try {
            // Create the server socket
            serverSocket = new ServerSocket(PORT, 1);
            while (true) {
                  // Wait for a connection
                  clientSocket = serverSocket.accept();
                  // System.out.println("*** Got a connection! ");
                  clientSocket.close();
             }
        } catch (IOException ioe) {
        }
    }
}
