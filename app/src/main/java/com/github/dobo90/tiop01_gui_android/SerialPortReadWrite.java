package com.github.dobo90.tiop01_gui_android;

import com.felhr.usbserial.CDCSerialDevice;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

public final class SerialPortReadWrite {
    private static final String TAG = BuildConfig.APPLICATION_ID + ".SerialPortReader";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static MainActivity activity;

    private CDCSerialDevice serialDevice;

    public SerialPortReadWrite(CDCSerialDevice serialDevice) {
        this.serialDevice = serialDevice;
    }

    public int read(byte[] buffer) {
        int bytesRead = 0;

        do {
            bytesRead = serialDevice.syncRead(buffer, 0);
        } while (bytesRead == 0);

        return bytesRead;

    }

    public int write(byte[] buffer) {
        return serialDevice.syncWrite(buffer, 0);
    }

    public static void setActivity(MainActivity activity) {
        SerialPortReadWrite.activity = activity;
    }

    public static SerialPortReadWrite open() {
        UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        UsbDevice device = null;

        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getVendorId() == 0x303a && d.getProductId() == 0x4001) {
                device = d;
            }
        }

        if (device == null) {
            Log.e(TAG, "Device not found");
            return null;
        }

        if (!usbManager.hasPermission(device)) {
            Boolean gotPermission = askAndWaitForPermission(usbManager, device);

            if (!gotPermission) {
                Log.e(TAG, "Failed to get premission");
                return null;
            }
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open connection");
            return null;
        }

        CDCSerialDevice port = new CDCSerialDevice(device, connection);
        if (!port.syncOpen()) {
            Log.e(TAG, "Failed to open port");
            return null;
        }

        port.setBaudRate(921600);

        return new SerialPortReadWrite(port);
    }

    private static Boolean askAndWaitForPermission(UsbManager usbManager, UsbDevice device) {
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
        usbManager.requestPermission(device, permissionIntent);

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
