package com.statictomato.apps.rovercontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ControlStickView.ControlStickListener {

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CONNECT_BT = 2;

    private static final int PERIODIC_DELAY = 100;

    private TextView textInfoLeft;
    private TextView textInfoRight;

    private int leftSpeed;
    private int leftDirection;
    private int rightSpeed;
    private int rightDirection;

    private String connectedDeviceName;

    private MessageHandler handler;

    private BluetoothSerialService bluetoothSerialService;

    private final BluetoothAdapter adapter;

    private final Object key = new Object();

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            BluetoothSerialService.BluetoothSerialBinder binder = (BluetoothSerialService.BluetoothSerialBinder) iBinder;
            bluetoothSerialService = binder.getService();
            bluetoothSerialService.setHandler(handler);
            bluetoothSerialService.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothSerialService = null;
        }
    };

    private Runnable periodic = new Runnable() {
        @Override
        public void run() {
            sendData();
            handler.postDelayed(periodic,PERIODIC_DELAY);
        }
    };


    public MainActivity() {
        adapter = BluetoothAdapter.getDefaultAdapter();

        handler = new MessageHandler();

        bluetoothSerialService = null;

        leftSpeed = 0;
        leftDirection = 0;
        rightSpeed = 0;
        rightDirection = 0;
    }

    /**
     * Updates the status on the action bar.
     */
    private void setStatus(int resId) {
        final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     */
    private void setStatus(CharSequence subTitle) {
        final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    private void startPeriodicSending() {
        leftSpeed = 0;
        leftDirection = 0;
        rightSpeed = 0;
        rightDirection = 0;

        periodic.run();
    }

    private void stopPeriodicSending() {
        handler.removeCallbacks(periodic);
    }

    private void sendData() {
        String data = "";
        synchronized (key) {
            data += leftDirection + "," + leftSpeed
                    + "," + rightDirection + "," + rightSpeed;
        }
        if(bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
            bluetoothSerialService.write(data);
        } else {
            stopPeriodicSending();
        }
    }

    private void writeToScreen(String text) {
        String[] s = text.split(",");
        try {
            textInfoLeft.setText("Direction: " + s[0] + ", Speed: " + String.format(Locale.ENGLISH, "%03d", Integer.parseInt(s[1])));
            textInfoRight.setText("Direction: " + s[2] + ", Speed: " + String.format(Locale.ENGLISH, "%03d", Integer.parseInt(s[3])));
        } catch (Exception e) {
            /* Ignore... */
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textInfoLeft = (TextView) findViewById(R.id.text_info_left);
        textInfoRight = (TextView) findViewById(R.id.text_info_right);

        /* If the adapter is null, then Bluetooth is not supported */
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG).show();
            finish();
        } else if(!adapter.isEnabled()) { /* Request to enable bluetooth */
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        Intent intent = new Intent(this,BluetoothSerialService.class);
        if(!bindService(intent,connection, Context.BIND_AUTO_CREATE)) {
            Toast.makeText(this, R.string.service_not_available, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bluetooth,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.disconnect: {
                stopPeriodicSending();
                bluetoothSerialService.stop();
                textInfoLeft.setText(R.string.default_left_text);
                textInfoRight.setText(R.string.default_right_text);
                break;
            }
            case R.id.connect: {
                Intent intent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(intent, REQUEST_CONNECT_BT);
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT && resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
            finish();
        } else if (requestCode == REQUEST_CONNECT_BT && resultCode == RESULT_OK) {
            // Get the device MAC address
            String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
            // Get the BluetoothDevice object
            BluetoothDevice device = adapter.getRemoteDevice(address);
            // Attempt to connect to the device
            bluetoothSerialService.connect(device);
        }
    }

    @Override
    public void onControlStickMoved(float percentX, float percentY, int source) {
        if(bluetoothSerialService.getState() != BluetoothSerialService.STATE_CONNECTED) {
            return;
        }
        synchronized (key) {
            if (source == R.id.left_stick) {
                if (percentY < 0) {
                    leftSpeed = (int) (-255 * percentY);
                    leftDirection = 0;
                } else {
                    leftSpeed = (int) (255 * percentY);
                    leftDirection = 1;
                }
            } else if (source == R.id.right_stick) {
                if (percentY < 0) {
                    rightSpeed = (int) (-255 * percentY);
                    rightDirection = 0;
                } else {
                    rightSpeed = (int) (255 * percentY);
                    rightDirection = 1;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /* Disconnect from service */
        if(bluetoothSerialService != null) {
            unbindService(connection);
            bluetoothSerialService = null;
        }
    }

    private class MessageHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MessageConstants.MESSAGE_STATE_CHANGE: {
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_STANDBY: {
                            setStatus(R.string.title_not_connected);
                            stopPeriodicSending();
                            break;
                        } case BluetoothSerialService.STATE_CONNECTING: {
                            setStatus(R.string.title_connecting);
                            break;
                        }
                        case BluetoothSerialService.STATE_CONNECTED: {
                            String s = getString(R.string.title_connected_to, connectedDeviceName);
                            setStatus(s);
                            startPeriodicSending();
                            break;
                        }
                    }
                    break;
                }
                case MessageConstants.MESSAGE_READ: {
                    writeToScreen((String) msg.obj);
                    break;
                }
                case MessageConstants.MESSAGE_WRITE: {
                    /* Ignored */
                    break;
                }
                case MessageConstants.MESSAGE_DEVICE_NAME: {
                    connectedDeviceName = (String) msg.obj;
                    break;
                }
                case MessageConstants.MESSAGE_TOAST: {
                    Toast.makeText(MainActivity.this, msg.arg1, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }
}
