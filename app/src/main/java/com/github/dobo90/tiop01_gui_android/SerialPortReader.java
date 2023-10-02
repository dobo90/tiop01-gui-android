package com.github.dobo90.tiop01_gui_android;

import java.io.IOException;
import java.util.List;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

public final class SerialPortReader {
    private static final String TAG = BuildConfig.APPLICATION_ID + ".SerialPortReader";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static MainActivity activity;

    private UsbSerialPort serialPort;

    public SerialPortReader(UsbSerialPort serialPort) {
        this.serialPort = serialPort;
    }

    public int read(final byte[] dest) {
        try {
            return serialPort.read(dest, 0);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static void setActivity(MainActivity activity) {
        SerialPortReader.activity = activity;
    }

    public static SerialPortReader openReader() {
        UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No driver available");
            return null;
        }

        UsbSerialDriver driver = availableDrivers.get(0);

        if (!usbManager.hasPermission(driver.getDevice())) {
            Boolean gotPermission = askAndWaitForPermission(usbManager, driver);

            if (!gotPermission) {
                Log.e(TAG, "Failed to get premission");
                return null;
            }
        }

        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
        if (connection == null) {
            Log.e(TAG, "Failed to open connection");
            return null;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.setDTR(true);
            port.setRTS(true);

            return new SerialPortReader(port);
        } catch (IOException e) {
            Log.e(TAG, "Failed to open port");
            e.printStackTrace();
            return null;
        }
    }

    private static Boolean askAndWaitForPermission(UsbManager usbManager, UsbSerialDriver driver) {
        final Boolean[] granted = { null };
        BroadcastReceiver usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                granted[0] = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            }
        };

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(INTENT_ACTION_GRANT_USB),
                flags);
        IntentFilter filter = new IntentFilter(INTENT_ACTION_GRANT_USB);
        activity.registerReceiver(usbReceiver, filter);
        usbManager.requestPermission(driver.getDevice(), permissionIntent);

        for (int i = 0; i < 600; i++) {
            if (granted[0] != null)
                break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (granted == null || granted[0] == null) {
            return false;
        }

        return true;
    }
}
