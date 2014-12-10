package edu.virginia.dtc.btleUsb;

import java.io.IOException;
import java.util.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.btleUsbDebug.DebugLog;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

@TargetApi(18)
public class BLEGattCallback extends BluetoothGattCallback {

  // this is the special UUID for custom notification descriptors
  protected static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = 
      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

  private static final long DescWriteTimeout = 5;
  private static final long CharWriteTimeout = 10;

  private HashMap<String,BluetoothGattService> services = 
       new HashMap<String,BluetoothGattService>();
  
  private HashMap<String,BluetoothGattCharacteristic> characteristics = 
      new HashMap<String,BluetoothGattCharacteristic>();

  private LinkedBlockingQueue<byte[]> incomingMessages = new LinkedBlockingQueue<byte[]>();
  
  private int connectionState = 0;

  // to turn asynchronous called into synchronous calls
  private CountDownLatch connectionLatch;
  private CountDownLatch servicesLatch;

  private CountDownLatch readLatch = new CountDownLatch(1);
  private CountDownLatch writeLatch = new CountDownLatch(1);
  private CountDownLatch descriptorWriteLatch = new CountDownLatch(1);

  // shows that STM has booted up
  private CountDownLatch bootedLatch = new CountDownLatch(1);

  private Integer batteryLevel = null;
  private Integer powerAttached = null;
  private Integer deviceAttached = null;

  public BLEGattCallback() {
  }

  @Override
  public void onConnectionStateChange (BluetoothGatt gatt, int status, int newState) {
    connectionState = newState;

    DebugLog.println(DebugLog.DebugLevelInfo, "onConnectionStateChange status= " + status + " newstate= " + newState);
    if(newState == BluetoothProfile.STATE_CONNECTED)
      {
        DebugLog.println(DebugLog.DebugLevelDetail, "releasing countdown latch in connection");
        connectionLatch.countDown();
      }
  }
  
  @Override
  // New services discovered
  public void onServicesDiscovered(BluetoothGatt gatt, int status) {
    DebugLog.println(DebugLog.DebugLevelInfo, "onServicesDiscovered received: " + status + " for device " + gatt.getDevice().getName());

    // if successful, then save all of the characteristics
//    if (status == BluetoothGatt.GATT_SUCCESS) 
      {
        List<BluetoothGattService> gattServices = gatt.getServices();

        String uuid;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) 
          {
            uuid = gattService.getUuid().toString();
            services.put(uuid, gattService);
            DebugLog.println(DebugLog.DebugLevelDetail, "Service " + uuid + " found");

            List<BluetoothGattCharacteristic> gattCharacteristics =
                gattService.getCharacteristics();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) 
              {
                uuid = gattCharacteristic.getUuid().toString();
                characteristics.put(uuid, gattCharacteristic);
                int perm = gattCharacteristic.getPermissions();
                int prop = gattCharacteristic.getProperties();
                
                DebugLog.println(DebugLog.DebugLevelDetail, "characteristic " + uuid + " found " + 
                ArraysUtil.toString(gattCharacteristic.getValue()) + 
                " perm " + perm + " prop " + prop);
              }
          }
      }
    
    // tell the other blocking thread that we've got the info we need and it can proceed
    DebugLog.println(DebugLog.DebugLevelDetail, "releasing services discovered ");
    servicesLatch.countDown();
  }
  
  @Override
  public void onCharacteristicRead (BluetoothGatt gatt,
      BluetoothGattCharacteristic characteristic, int status) {

        DebugLog.println(DebugLog.DebugLevelDetail, "onCharRead " + characteristic.getUuid().toString() + "  " + status
            + " " + ArraysUtil.toString(characteristic.getValue()));
        
        String name = " " + gatt.getDevice().getName();
        String address = gatt.getDevice().getAddress();
        
        if(characteristic.getUuid().toString().equals(BLEUsbPort.BatteryLevelCharacteristicUuidString))
          {
            this.batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            DebugLog.println(DebugLog.DebugLevelInfo, name + " " + address + " battery level is " + batteryLevel);
          }
        else if(characteristic.getUuid().toString().equals(BLEUsbPort.SmartloopPowerCharUuidString))
          {
            this.powerAttached = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            DebugLog.println(DebugLog.DebugLevelInfo, name + " " + address + " power attached char is " + powerAttached);
          }
        else if(characteristic.getUuid().toString().equals(BLEUsbPort.SmartloopDevStsCharUuidString))
          {
            this.deviceAttached = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            DebugLog.println(DebugLog.DebugLevelInfo, name + " " + address + " device attached char is " + deviceAttached);
          }
        // else put other characteristics here

        readLatch.countDown();
  }
  
  @Override
  public void onCharacteristicWrite (BluetoothGatt gatt, 
      BluetoothGattCharacteristic characteristic, int status) {
  
      DebugLog.println(DebugLog.DebugLevelDetail, "onCharWrite " + characteristic.getUuid().toString() + "  " + status);
        
      writeLatch.countDown();
  }

  @Override
  public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    descriptorWriteLatch.countDown();
  }
  
  public void turnOnNotifications(BluetoothGatt bluetoothGatt) throws IOException {
    DebugLog.println(DebugLog.DebugLevelInfo, "turning on notifications Gatt Characteristics");
    for(BluetoothGattCharacteristic characteristic : characteristics.values())
      {
        if(characteristic.getUuid().toString().equals(BLEUsbPort.SmartloopControlPointUuidString) == false 
//           && characteristic.getUuid().toString().equals(BLEUsbPort.SmartloopDevStsCharUuidString) == false
            )
          continue;
        
        if((characteristic.getProperties() & 
            BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
          {
            if(connectionState != BluetoothProfile.STATE_CONNECTED)
              throw new IOException("tried to write descriptor to unconnected device");

            DebugLog.println(DebugLog.DebugLevelDetail, "turning on notifications for " + characteristic.getUuid());
            bluetoothGatt.setCharacteristicNotification(characteristic, true);
            
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
            boolean success1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            descriptorWriteLatch = new CountDownLatch(1);
            if(connectionState != BluetoothProfile.STATE_CONNECTED)
              throw new IOException("tried to write descriptor to unconnected device");
            boolean success = bluetoothGatt.writeDescriptor(descriptor);
            DebugLog.println(DebugLog.DebugLevelDetail, "enabling notifications for descriptor " + descriptor.getUuid() + " " + success1 + " " + success);
            try {
                if(descriptorWriteLatch.await(DescWriteTimeout, TimeUnit.SECONDS) == false)
                  {
                    DebugLog.println(DebugLog.DebugLevelInfo, "timed out waiting for onDescriptorWrite");
                    throw new IOException("timed out waiting for onDescriptorWrite");
                  }
              } catch (InterruptedException e) {}
          }
      }
  }

  public BluetoothGattCharacteristic getCharacteristic(
      String characteristicUuidString) {
        return characteristics.get(characteristicUuidString);
  }

  public boolean readCharacteristic(BluetoothGatt bluetoothGatt,
      BluetoothGattCharacteristic characteristic) throws IOException {
    // note not thread-safe!  cannot have multiple threads issuing write cmds to this
    // class
    
    if(connectionState != BluetoothProfile.STATE_CONNECTED)
      throw new IOException("tried to read characteristic to unconnected device");
    
    long millis = System.currentTimeMillis();
    readLatch = new CountDownLatch(1);
    bluetoothGatt.readCharacteristic(characteristic);
    try {
      if(readLatch.await(CharWriteTimeout, TimeUnit.SECONDS) == false)
        throw new IOException("timed out waiting for onCharacteristicRead");
    } catch (InterruptedException e) { }

    DebugLog.println(DebugLog.DebugLevelDetail, "char read in " + (System.currentTimeMillis() - millis) + " millis");
    
    return true;
  }

  public boolean writeCharacteristic(BluetoothGatt bluetoothGatt,
      BluetoothGattCharacteristic characteristic) throws IOException {
    // note not thread-safe!  cannot have multiple threads issuing write cmds to this
    // class
    
    if(connectionState != BluetoothProfile.STATE_CONNECTED)
      throw new IOException("tried to write characteristic to unconnected device");
    
    long millis = System.currentTimeMillis();
    writeLatch = new CountDownLatch(1);
    bluetoothGatt.writeCharacteristic(characteristic);
    try {
      if(writeLatch.await(CharWriteTimeout, TimeUnit.SECONDS) == false)
        throw new IOException("timed out waiting for onCharacteristicWrite");
    } catch (InterruptedException e) { }

    DebugLog.println(DebugLog.DebugLevelDetail, "char write in " + (System.currentTimeMillis() - millis) + " millis");
    
    return true;
  }
  
  public byte[] readIncomingMessage(int timeout) {
    byte[] msg = null;
    try {
      msg = incomingMessages.poll(timeout, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      // not sure why this would happen
    }
    
    return msg;
  }
  
  @Override
  public void onCharacteristicChanged (BluetoothGatt gatt, 
      BluetoothGattCharacteristic characteristic) {
    
    DebugLog.println(DebugLog.DebugLevelDetail, "char changed called " + characteristic.getUuid().toString() + "  " + ArraysUtil.toString(characteristic.getValue()));    
    if(characteristic.getUuid().toString().equals(BLEUsbPort.SmartloopControlPointUuidString))
        {
          DebugLog.println(DebugLog.DebugLevelDetail, "New Data msg " + ArraysUtil.toString(characteristic.getValue()));
          // new incoming message
          byte[] msg = new byte[characteristic.getValue().length];
          System.arraycopy(characteristic.getValue(), 0, msg, 0, msg.length);
          if(msg[0] == (byte)0x85)
            {
              DebugLog.println(DebugLog.DebugLevelDetail, "0x85 message received, not skipping...");
            }

          incomingMessages.add(msg);
        }
    else if(characteristic.getUuid().toString().equals(BLEUsbPort.SmartloopDevStsCharUuidString))
      {
        int millis = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        DebugLog.println(DebugLog.DebugLevelInfo, "connected for " + millis + " millis");
        bootedLatch.countDown();
      }
    else 
      {
        // take care of other characteristic changes here
      }
  }
  
  public void waitForBoot(int timeout) throws IOException {
    try {
      if(bootedLatch.await(timeout, TimeUnit.MILLISECONDS) == false)
        throw new IOException("timed out waiting for bootLatch");
    } catch (InterruptedException e) {
      DebugLog.println(DebugLog.DebugLevelInfo, "timed out waiting for STM boot");
      throw new IOException("Timeout waiting for STM boot");
    }
  }

  public int getConnectionState() {
    return connectionState;
  }

  public boolean isConnected() {
    return getConnectionState() == BluetoothProfile.STATE_CONNECTED;
  }
  
  public int getBatteryLevel() {
    if(batteryLevel == null)
      return -1;
    else
      return batteryLevel;
  }
  
  public int getPowerAttached() {
    if(powerAttached == null)
      return -1;
    else
      return powerAttached;
  }
  
  public int getDeviceAttached() {
    if(deviceAttached == null)
      return -1;
    else
      return deviceAttached;
  }
  
  public CountDownLatch newConnectionLatch() {
    connectionLatch = new CountDownLatch(1);

    return connectionLatch;
  }

  public CountDownLatch newServicesLatch() {
    servicesLatch = new CountDownLatch(1);

    return servicesLatch;
  }
}
