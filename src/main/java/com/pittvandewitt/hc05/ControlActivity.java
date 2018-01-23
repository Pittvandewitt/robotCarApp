package com.pittvandewitt.hc05;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;

public class ControlActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, BluetoothStateCallback {

    private static final String device = "HC-05";
    private static final UUID BT_UUID = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothSocket bluetoothSocket;
    private static ConnectTask connectTask;
    private static WriterThread writerThread;
    private String lastCommand = "";
    private int previousProgress = 5;
    private TextView connectionState;
    /**
     * This class will process some events which are called from android itself, we use it to
     * establish connections automatically.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    // the bluetooth was turned off, so we stop any running connection tasks
                    if (ControlActivity.connectTask != null && connectTask.getStatus() == AsyncTask.Status.RUNNING) {
                        ControlActivity.connectTask.cancel(true);
                    }
                    Context context1 = getApplicationContext();
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast1 = Toast.makeText(context1, R.string.bluetooth_off, duration);
                    toast1.show();
                    // enable the bluetooth again, and wait till it is turned on
                    bluetoothAdapter.enable();
                    while (!bluetoothAdapter.isEnabled()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    Context context2 = getApplicationContext();
                    Toast toast2 = Toast.makeText(context2, R.string.bluetooth_on, duration);
                    toast2.show();
                    // try to connect again with the device
                    ControlActivity.connectTask = new ConnectTask();
                    ControlActivity.connectTask.execute();
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                // this event is useful if the user has paired the device for the first time
                if (ControlActivity.connectTask == null || ControlActivity.connectTask.getStatus() == AsyncTask.Status.FINISHED) {
                    Context context3 = getApplicationContext();
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context3, R.string.bluetooth_reconnect, duration);
                    toast.show();
                    // reconnect since the app is doing nothing at this moment
                    ControlActivity.connectTask = new ConnectTask();
                    ControlActivity.connectTask.execute();
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                // if the connection gets lost, we have to reconnect again
                Context context4 = getApplicationContext();
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context4, R.string.connection_lost, duration);
                toast.show();
                connectionState.setText(getString(R.string.connection_lost));
                if (ControlActivity.connectTask != null && connectTask.getStatus() == AsyncTask.Status.RUNNING) {
                    ControlActivity.connectTask.cancel(true);
                }
                ControlActivity.connectTask = new ConnectTask();
                ControlActivity.connectTask.execute();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        drawer.openDrawer(GravityCompat.START);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // assign the widgets specified in the layout xml file
        this.connectionState = findViewById(R.id.connection_state);
        ButtonClick left = findViewById(R.id.left);
        left.setOnTouchListener((view, mEvent) -> {
            view.performClick();
            if (mEvent.getAction() == MotionEvent.ACTION_DOWN) {
                ControlActivity.this.sendCommand("d");
            } else if (mEvent.getAction() == MotionEvent.ACTION_UP) {
                sendCommand(lastCommand);
            }
            return false;
        });
        ButtonClick right = findViewById(R.id.right);
        right.setOnTouchListener((view, mEvent) -> {
            view.performClick();
            if (mEvent.getAction() == MotionEvent.ACTION_DOWN) {
                ControlActivity.this.sendCommand("a");
            } else if (mEvent.getAction() == MotionEvent.ACTION_UP) {
                sendCommand(lastCommand);
            }
            return false;
        });
        VerticalSeekBar sensitivity = findViewById(R.id.remote_seek_bar);
        sensitivity.setProgress(5);
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > 5) {
                    if (previousProgress < progress) {
                        for (int i = previousProgress; i < progress; i++) {
                            ControlActivity.this.sendCommand("+");
                        }
                    }
                    if (previousProgress > progress) {
                        for (int i = previousProgress; i > progress; i--) {
                            ControlActivity.this.sendCommand("-");
                        }
                    }
                    ControlActivity.this.sendCommand("w");
                    lastCommand = "w";
                }
                if (progress < 5) {
                    if (previousProgress > progress) {
                        for (int i = progress; i < previousProgress; i++) {
                            ControlActivity.this.sendCommand("+");
                        }
                    }
                    if (previousProgress < progress) {
                        for (int i = progress; i > previousProgress; i--) {
                            ControlActivity.this.sendCommand("-");
                        }
                    }
                    ControlActivity.this.sendCommand("s");
                    lastCommand = "s";
                }
                if (progress == 5) {
                    ControlActivity.this.sendCommand("z");
                    lastCommand = "z";
                }
                previousProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        VerticalSeekBar speed = findViewById(R.id.speed_seek_bar);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (previousProgress < progress) {
                    for (int i = previousProgress; i < progress; i++) {
                        ControlActivity.this.sendCommand("+");
                    }
                    ControlActivity.this.sendCommand("w");
                }
                if (previousProgress > progress) {
                    for (int i = previousProgress; i > progress; i--) {
                        ControlActivity.this.sendCommand("-");
                    }
                    ControlActivity.this.sendCommand("w");
                }
                if (progress == 0) {
                    ControlActivity.this.sendCommand("z");
                }
                previousProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });


        hideEverything();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
        Snackbar mySnackBar = Snackbar.make(findViewById(R.id.activity_control),
                R.string.command_not_sent, Snackbar.LENGTH_LONG);
        mySnackBar.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.close) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }


    private void hideEverything() {
        Button left = findViewById(R.id.left);
        left.setVisibility(View.INVISIBLE);
        Button right = findViewById(R.id.right);
        right.setVisibility(View.INVISIBLE);
        TextView up = findViewById(R.id.up);
        up.setVisibility(View.INVISIBLE);
        TextView down = findViewById(R.id.down);
        down.setVisibility(View.INVISIBLE);
        TextView speed = findViewById(R.id.sensitivity);
        speed.setVisibility(View.INVISIBLE);
        VerticalSeekBar remoteSeekBar = findViewById(R.id.remote_seek_bar);
        remoteSeekBar.setVisibility(View.INVISIBLE);
        VerticalSeekBar speedSeekBar = findViewById(R.id.speed_seek_bar);
        speedSeekBar.setVisibility(View.INVISIBLE);
    }

    private void hideItems() {
        Button left = findViewById(R.id.left);
        left.setVisibility(View.INVISIBLE);
        Button right = findViewById(R.id.right);
        right.setVisibility(View.INVISIBLE);
        TextView up = findViewById(R.id.up);
        up.setVisibility(View.INVISIBLE);
        TextView down = findViewById(R.id.down);
        down.setVisibility(View.INVISIBLE);
        TextView speed = findViewById(R.id.sensitivity);
        speed.setVisibility(View.VISIBLE);
        VerticalSeekBar remoteSeekBar = findViewById(R.id.remote_seek_bar);
        remoteSeekBar.setVisibility(View.INVISIBLE);
        VerticalSeekBar speedSeekBar = findViewById(R.id.speed_seek_bar);
        speedSeekBar.setVisibility(View.VISIBLE);
    }

    private void showItems() {
        Button left = findViewById(R.id.left);
        left.setVisibility(View.VISIBLE);
        Button right = findViewById(R.id.right);
        right.setVisibility(View.VISIBLE);
        TextView up = findViewById(R.id.up);
        up.setVisibility(View.VISIBLE);
        TextView down = findViewById(R.id.down);
        down.setVisibility(View.VISIBLE);
        TextView speed = findViewById(R.id.sensitivity);
        speed.setVisibility(View.INVISIBLE);
        VerticalSeekBar remoteSeekBar = findViewById(R.id.remote_seek_bar);
        remoteSeekBar.setVisibility(View.VISIBLE);
        VerticalSeekBar speedSeekBar = findViewById(R.id.speed_seek_bar);
        speedSeekBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.autonomous) {
            ControlActivity.this.sendCommand("x");
            ControlActivity.this.sendCommand("1");
            Snackbar mySnackBar = Snackbar.make(findViewById(R.id.activity_control),
                    R.string.autonomous, Snackbar.LENGTH_SHORT);
            mySnackBar.show();
            hideItems();
        } else if (id == R.id.remote) {
            ControlActivity.this.sendCommand("x");
            ControlActivity.this.sendCommand("2");
            Snackbar mySnackBar = Snackbar.make(findViewById(R.id.activity_control),
                    R.string.remote, Snackbar.LENGTH_SHORT);
            mySnackBar.show();
            showItems();
        } else if (id == R.id.line) {
            ControlActivity.this.sendCommand("x");
            ControlActivity.this.sendCommand("3");
            Snackbar mySnackBar = Snackbar.make(findViewById(R.id.activity_control),
                    R.string.line_tracker, Snackbar.LENGTH_SHORT);
            mySnackBar.show();
            hideItems();
        } else if (id == R.id.slave) {
            ControlActivity.this.sendCommand("x");
            ControlActivity.this.sendCommand("4");
            Snackbar mySnackBar = Snackbar.make(findViewById(R.id.activity_control),
                    R.string.slave, Snackbar.LENGTH_SHORT);
            mySnackBar.show();
            hideItems();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        ControlActivity.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // register these receivers, we need them for setting up connections automatically
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));

        // try to connect
        ControlActivity.connectTask = new ConnectTask();
        ControlActivity.connectTask.execute();
        ControlActivity.this.sendCommand("x");
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mReceiver);
        super.onStop();
    }

    /**
     * Send a command to the writerThread, and the command will be processed there.
     * Note that the thread must be running, otherwise the message is not sent, nor saved.
     *
     * @param command the command we want to send
     */
    private void sendCommand(String command) {
        String strCommandTry =
                getResources().getString(R.string.command_send_try, command);
        String strCommandError =
                getResources().getString(R.string.command_not_sent, command);
        if (ControlActivity.writerThread != null) {
            ControlActivity.this.connectionState.setText(strCommandTry);
            ControlActivity.writerThread.queueSend(command.getBytes());
        } else {
            ControlActivity.this.connectionState.setText(strCommandError);
        }
    }

    /**
     * When the writerThread failed writing, this method is called.
     *
     * @param e the exception message
     */
    @Override
    public void onWriteFailure(final String e) {
        this.runOnUiThread(() -> {
            String strReconnect =
                    getResources().getString(R.string.command_not_sent, e);
            connectionState.setText(strReconnect);
            if (ControlActivity.connectTask != null && connectTask.getStatus() == AsyncTask.Status.RUNNING) {
                ControlActivity.connectTask.cancel(true);
            }
            ControlActivity.connectTask = new ConnectTask();
            ControlActivity.connectTask.execute();
        });
    }

    /**
     * When the writerThread writes successful on the socket, this method is called.
     *
     * @param command the already sent command
     */
    @Override
    public void onWriteSuccess(final String command) {
        this.runOnUiThread(() -> {
            String strSent = getResources().getString(R.string.command_sent, command);
            connectionState.setText(strSent);
        });
    }

    /**
     * Closes any running writerThread, and starts a new one
     */
    private void restartWriterThread() {
        if (ControlActivity.writerThread != null) {
            ControlActivity.writerThread.interrupt();
            ControlActivity.writerThread.setRunning(false);
            ControlActivity.writerThread = null;
        }
        ControlActivity.writerThread = new WriterThread(ControlActivity.this, ControlActivity.bluetoothSocket);
        ControlActivity.writerThread.start();
    }

    /**
     * This class is the main logic for setting up a connection and opening a socket
     */
    class ConnectTask extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            String strConnectionTry =
                    getResources().getString(R.string.connection_try) + " " + ControlActivity.device + "...";
            Context context = getApplicationContext();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, strConnectionTry, duration);
            toast.show();
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                // we need to enable the bluetooth first in order to make this app working
                if (!bluetoothAdapter.isEnabled()) {
                    publishProgress("bluetooth was not enabled, enabling...");
                    bluetoothAdapter.enable();
                    // turning on bluetooth takes some time
                    while (!bluetoothAdapter.isEnabled()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    publishProgress("bluetooth turned on");
                }
                // here we are going to check if the device was paired in android, if not the user will be prompt to do so.
                String address = null;
                for (BluetoothDevice d : bluetoothAdapter.getBondedDevices()) {
                    if (ControlActivity.device.equals(d.getName())) {
                        address = d.getAddress();
                        break;
                    }
                }
                if (address == null) {
                    return ControlActivity.device + " " + getString(R.string.never_paired);
                }
                // we have a mac address, now we try to open a socket
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                publishProgress("creating socket...");
                ControlActivity.bluetoothSocket = device.createRfcommSocketToServiceRecord(ControlActivity.BT_UUID);
                publishProgress("canceling discovery...");
                // we cancel discovery for other devices, since it will speed up the connection
                ControlActivity.bluetoothAdapter.cancelDiscovery();
                publishProgress((R.string.connection_try) + " " + device + " with address " + address);
                // try to connect to the bluetooth device, if unsuccessful, an exception will be thrown
                ControlActivity.bluetoothSocket.connect();
                // start the writerThread
                restartWriterThread();
                return "connected, writer thread is running";
            } catch (IOException e) {
                try {
                    // try to close the socket, since we can have only one
                    ControlActivity.bluetoothSocket.close();
                } catch (IOException e1) {
                    return "failure due " + e.getMessage() + ", closing socket did not work.";
                }
                return "failure due " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            connectionState.setText(s);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            connectionState.setText(values[0]);
        }
    }
}
