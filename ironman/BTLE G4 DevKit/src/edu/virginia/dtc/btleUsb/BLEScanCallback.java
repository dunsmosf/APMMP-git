package edu.virginia.dtc.btleUsb;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.btleUsbDebug.DebugLog;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

@TargetApi(18)
public class BLEScanCallback implements BluetoothAdapter.LeScanCallback {

  // this is the device's characteristic that we are looking for
  private String deviceName = null;
  private String deviceMacAddress = null;

  // release this semaphore to allow the 'blocking' call to proceed when we 
  // have found the device we are looking for
  private CountDownLatch scanLatch = new CountDownLatch(1);
  
  private BluetoothDevice bluetoothDevice = null;


  public BLEScanCallback(String deviceName, String deviceMacAddress) {
    this.deviceName = deviceName;
    this.deviceMacAddress = deviceMacAddress;
  }
  
  public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
    String curDeviceName = device.getName();
    String curDeviceAddress = device.getAddress();
    DebugLog.println(DebugLog.DebugLevelInfo, "See BLE device " + device.getName() + "  " + device.getAddress());
    DebugLog.println(DebugLog.DebugLevelDetail, ArraysUtil.toString(scanRecord));
    
    // if this is the device we are looking for, save it and release the semaphore 
    // causing the originating thread to progress
    if((deviceName == null || curDeviceName.equalsIgnoreCase(deviceName)) &&
        (deviceMacAddress == null || curDeviceAddress.equalsIgnoreCase(deviceMacAddress)))
      {
        this.bluetoothDevice = device;
        scanLatch.countDown();
      }
  }

  public boolean waitUntilDeviceIsFound(long seconds) {
    // wait for up to Scan_Period seconds.  If the device is found, then we will be
    // able to acquire the semaphore, allowing us to progress before the time limit is up
    boolean success = false;
    try {
      long start = System.currentTimeMillis();
      success = scanLatch.await(seconds, TimeUnit.SECONDS);
      long end = System.currentTimeMillis();
      if(success == true)
        DebugLog.println(DebugLog.DebugLevelInfo, "loop stat scan time " + (end - start));
      else
        DebugLog.println(DebugLog.DebugLevelInfo, "loop stat failed to connect scan time " + (end - start));
    } catch (InterruptedException e) {}
    
    return success;
  }
  
  public BluetoothDevice getBluetoothDevice() {
    return bluetoothDevice;
  }

}
