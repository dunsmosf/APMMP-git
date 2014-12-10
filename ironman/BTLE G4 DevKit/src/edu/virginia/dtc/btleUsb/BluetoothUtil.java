package edu.virginia.dtc.btleUsb;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import edu.virginia.dtc.btleUsbDebug.DebugLog;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothSocket;

public class BluetoothUtil {

  private BluetoothAdapter mBluetoothAdapter = null;

  public static String fullBluetoothSigUUID(String shortVersion) {
    return "0000" + shortVersion + "-0000-1000-8000-00805f9b34fb";
  }

  public BluetoothUtil() {
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  }

  // bluetooth classic socket creation (not used in BLE code)
  public BluetoothSocket setupBluetoothSocket(String bluetoothname, UUID uuid) throws Exception {

    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

    BluetoothDevice targetDevice = null;
    
    // If there are paired devices
    if (pairedDevices.size() > 0) {
      // Loop through paired devices
      for (BluetoothDevice device : pairedDevices) {
        DebugLog.println(DebugLog.DebugLevelInfo, "see bluetooth paired device " + device.getName());
        if(device.getName().equalsIgnoreCase(bluetoothname))
          {
            targetDevice = device;
            break;
          }
      }
    }
    
    if(targetDevice == null)
      throw new Exception("could not find paired device named " + bluetoothname);

    BluetoothSocket mmSocket = null;
    // Get a BluetoothSocket to connect with the given BluetoothDevice
    try {
      mmSocket = targetDevice.createRfcommSocketToServiceRecord(uuid);
    } catch (IOException e) { }
 
    // Cancel discovery because it will slow down the connection
    mBluetoothAdapter.cancelDiscovery();
 
    try {
      // Connect the device through the socket. This will block
      // until it succeeds or throws an exception
      mmSocket.connect();
    } catch (IOException connectException) {
      // Unable to connect; close the socket and get out
      try {
        mmSocket.close();
      } catch (IOException closeException) { }
      DebugLog.println(DebugLog.DebugLevelInfo, "Unable to connect to bluetooth socket.");
      throw new Exception("Unable to connect to bluetooth socket.");
//      return;
    }

    if(mmSocket == null)
      throw new Exception("could not open bluetooth socket");
    
    return mmSocket;
  }
  

  public void enableBluetooth() {
    // create bluetooth connection here
    if (mBluetoothAdapter.isEnabled() == false) {
      DebugLog.println(DebugLog.DebugLevelInfo, "turning bluetooth adapter on");
      mBluetoothAdapter.enable();

      for(int i=0; i < 20; i++)
        {
          if(mBluetoothAdapter.isEnabled() == true)
            break;
          else
            try {
              Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }
  }

  public void disableBluetooth() {
    // disable bluetooth connection here
    if (mBluetoothAdapter.isEnabled() == true) {
      DebugLog.println(DebugLog.DebugLevelInfo, "turning bluetooth adapter off");
      mBluetoothAdapter.disable();

      for(int i=0; i < 20; i++)
        {
          if(mBluetoothAdapter.isEnabled() == false)
            break;
          else
            try {
              Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }
  }

  public void cycleBluetoothPower() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {}

    disableBluetooth();

    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {}

    enableBluetooth();
  }
  
  // Stops scanning after 10 seconds.  We don't actually use the UUIDs currently.  Just 
  // find the name
  private static final long Scan_Period = 15;
  @TargetApi(18)
  public BluetoothDevice scanLeDevice(UUID[] uuids, String name, String macAddress) {
    
    BLEScanCallback bleScanCallback = new BLEScanCallback(name, macAddress);
    
//    mBluetoothAdapter.startLeScan(uuids, bleScanCallback);
    mBluetoothAdapter.startLeScan(bleScanCallback);
    bleScanCallback.waitUntilDeviceIsFound(Scan_Period);
    mBluetoothAdapter.stopLeScan(bleScanCallback);
    
    return bleScanCallback.getBluetoothDevice();
  }

  @TargetApi(18)
  public void closeDownGatt(BluetoothGatt bluetoothGatt) {
    if(bluetoothGatt != null)
      {
        bluetoothGatt.disconnect();
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {}
        bluetoothGatt.close();
      }
  }
  
}
