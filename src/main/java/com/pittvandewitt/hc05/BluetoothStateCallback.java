package com.pittvandewitt.hc05;

interface BluetoothStateCallback {

    void onWriteFailure(String e);

    void onWriteSuccess(String command);
}
