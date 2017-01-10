package com.statictomato.apps.rovercontroller;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothSerialService extends Service {

    /* The SPP (Serial Port Profile) UUID */
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final char START = 'S';
    public static final char END = 'E';

    // Member fields
    private Handler handler;
    private Binder binder;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private int state;

    // Constants that indicate the current connection state
    public static final int STATE_STANDBY = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device

    /*public BluetoothSerialService(Context context, Handler handler) {
        this.binder = new BluetoothSerialBinder();
        setState(STATE_STANDBY);
    }*/

    public BluetoothSerialService() {
        binder = new BluetoothSerialBinder();
    }

    public class BluetoothSerialBinder extends Binder {
        BluetoothSerialService getService() {
            return BluetoothSerialService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        if(handler != null) {
            handler = null;
        }
    }

    /**
     * Set the current state of the chat connection.
     */
    private synchronized void setState(int state) {
        this.state = state;
        // Give the new state to the Handler so the UI Activity can update
        if(handler != null) {
            handler.obtainMessage(MessageConstants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        }
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return state;
    }

    public void start() {
        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_STANDBY);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     */
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    /**
     * Write to the ConnectedThread
     */
    public synchronized void write(String data) {
        if (getState() != STATE_CONNECTED) {
            return;
        }
        connectedThread.write((START + data + END).getBytes());
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if(getState() == STATE_STANDBY) {
            handler.obtainMessage(MessageConstants.MESSAGE_TOAST,R.string.not_connected,-1).sendToTarget();
        } else {
            setState(STATE_STANDBY);
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     */
    private void manageConnectedSocket(BluetoothSocket socket, BluetoothDevice device) {

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Send the name of the connected device back to the UI Activity
        String name = device.getName();
        if(handler != null) {
            handler.obtainMessage(MessageConstants.MESSAGE_DEVICE_NAME, name).sendToTarget();
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        if(handler != null) {
            handler.obtainMessage(MessageConstants.MESSAGE_TOAST, R.string.connection_failed, -1).sendToTarget();
        }
        BluetoothSerialService.this.stop();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        if(handler != null) {
            handler.obtainMessage(MessageConstants.MESSAGE_TOAST, R.string.connection_lost, -1).sendToTarget();
        }
        BluetoothSerialService.this.stop();
    }

    /**
     * Bluetooth client
     *
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {

        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        private ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to socket
            // because socket is final.
            BluetoothSocket tmp = null;
            this.device = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                /* Ignore... */
            }
            socket = tmp;
            setState(STATE_CONNECTING);
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            // Always cancel discovery before connecting to remote device.
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    socket.close();
                } catch (IOException closeException) {
                    /* Ignore... */
                }
                connectionFailed();
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            manageConnectedSocket(socket,device);
        }

        // Closes the client socket and causes the thread to finish.
        private void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                /* Ignore... */
            }
        }
    }

    /**
     * Bluetooth connection
     *
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {

        private final BluetoothSocket socket;
        private final InputStream inStream;
        private final OutputStream outStream;
        private byte[] buffer; // buffer store for the stream

        private ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                /* Ignore... */
            }

            inStream = tmpIn;
            outStream = tmpOut;
            setState(STATE_CONNECTED);
        }

        public void run() {
            buffer = new byte[512];
            int i;
            byte c;
            String data;

            // Keep listening to the InputStream until an exception occurs.
            while (state == STATE_CONNECTED) {
                try {
                    if((byte) inStream.read() == START) {
                        c = 0;
                        i = 0;
                        while (c != END && i < 512) {
                            c = (byte) inStream.read();
                            buffer[i] = c;
                            ++i;
                        }
                        if(c == END) {
                            data = new String(buffer,0,i-1);
                            if(handler != null) {
                                handler.obtainMessage(MessageConstants.MESSAGE_READ, data).sendToTarget();
                            }
                        }

                    }
                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        private void write(byte[] bytes) {
            try {
                outStream.write(bytes);
                outStream.flush();
            } catch (IOException e) {
                connectionLost();
            }
        }

        // Call this method from the main activity to shut down the connection.
        private void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                /* Ignore... */
            }
        }
    }

}
