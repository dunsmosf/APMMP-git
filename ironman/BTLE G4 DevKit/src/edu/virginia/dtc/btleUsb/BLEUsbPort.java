package edu.virginia.dtc.btleUsb;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.btleUsbDebug.DebugLog;


import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;


@TargetApi(18)
public class BLEUsbPort {

  public final static String SmartloopServiceUuidString      = "7efca949-eabc-a5a0-3e47-d13cb4ed16b4";
  public final static String SmartloopControlPointUuidString = "7efca94a-eabc-a5a0-3e47-d13cb4ed16b4";
  public final static String SmartloopPowerCharUuidString    = "7efca94b-eabc-a5a0-3e47-d13cb4ed16b4";
  public final static String SmartloopDevStsCharUuidString   = "7efca94c-eabc-a5a0-3e47-d13cb4ed16b4";

  public final static String DeviceInformationServiceUuidString          = BluetoothUtil.fullBluetoothSigUUID("180a");
  public final static String DeviceManufacturerCharacteristicUuidString  = BluetoothUtil.fullBluetoothSigUUID("2a29");
  public final static String BatteryServiceUuidString                    = BluetoothUtil.fullBluetoothSigUUID("180f");
  public final static String BatteryLevelCharacteristicUuidString        = BluetoothUtil.fullBluetoothSigUUID("2a19");

  public final static UUID SmartloopServiceUuid       = UUID.fromString(SmartloopServiceUuidString);
  public final static UUID SmartloopControlPointUuid  = UUID.fromString(SmartloopControlPointUuidString);
  public final static UUID SmartloopPowerCharUuid     = UUID.fromString(SmartloopPowerCharUuidString);
  public final static UUID SmartloopDevStsCharUuid    = UUID.fromString(SmartloopDevStsCharUuidString);

  public final UUID[] SmartloopUuids = {
      SmartloopServiceUuid,
//      SmartloopControlPointUuid,
//      SmartloopPowerCharUuid,
//      SmartloopDevStsCharUuid,
  };
  
  public static final byte RebootSTMCmd         = (byte)0x01;
  public static final byte RebootnRFCmd         = (byte)0x02;
  
  public static final byte RequestUSBDataCmd    = (byte)0x82;
  public static final byte SendUSBDataCmd       = (byte)0x83;

  public static final byte USBResponseLenCmd    = (byte)0x80;
  public static final byte USBResponseDataCmd   = (byte)0x81;

  public static final byte ClearBuffers  = 3;
  public static final byte Reboot        = 4;
  public static final byte EndCommunications = 5;
  
  private BluetoothUtil bluetoothUtil;
  
  private BLEGattCallback bleGattCallback;
  private BluetoothGatt bluetoothGatt;

  private BluetoothDevice device = null;
  private Context context;
  // the maximum size of the usb blocks that we should read in
  private int blockSize;
  // millis to wait for enumeration of device between discovering services and writing 
  // on control characteristic
  private long enumerateMillis;
  
  public static final int BytesPerMessage = 20;
  public static final int TimeoutSecs = 15;
  public static final int ReadWriteFinishDelay = 50;

  private static boolean attemptBluetoothCycling = false;
  
  public static void setAttemptBluetoothCycling(boolean newMode) {
    attemptBluetoothCycling = newMode;
  }
  
  public BLEUsbPort(Context context, int blockSize, long enumerateMillis) throws Exception {
    
    this.context = context;
    this.blockSize = blockSize;
    this.enumerateMillis = enumerateMillis;
    
    bluetoothUtil = new BluetoothUtil();
    bluetoothUtil.enableBluetooth();
  }
  
  public boolean findDevice(String name, String macAddress) throws IOException, InterruptedException { 
    // find device in already seen devices or scan for it if it's a new device
    device = null;

    if(device == null)
      {
        // scan for the device
        DebugLog.println(DebugLog.DebugLevelInfo, "Ble scanning for device " + name + " " + macAddress);
        device = bluetoothUtil.scanLeDevice(SmartloopUuids, name, macAddress);
    
        if(device == null)
          throw new IOException("Could not find BLE device " + name);
      }
    else
      DebugLog.println(DebugLog.DebugLevelInfo, "Found " + name + " in list of previously connected ble devices");

    
    Thread.sleep(500);
    
    return true;
  }
  
  public boolean connectToDevice() throws InterruptedException, IOException {
    bleGattCallback = new BLEGattCallback();

    // connect to smartloop board
    CountDownLatch connectLatch = bleGattCallback.newConnectionLatch();
    DebugLog.printDate(DebugLog.DebugLevelInfo);
    DebugLog.println(DebugLog.DebugLevelInfo, "Attempting Gatt Connect");
    bluetoothGatt = device.connectGatt(context, false, bleGattCallback);
    DebugLog.println(DebugLog.DebugLevelInfo, "Finished Gatt Connect");

    if(bleGattCallback.getConnectionState() != BluetoothProfile.STATE_CONNECTED && 
        connectLatch.await(TimeoutSecs, TimeUnit.SECONDS) == false)
      {
        bluetoothUtil.closeDownGatt(bluetoothGatt);
        throw new IOException("Could not open gatt connection");
      }
    
    return stillConnected();
  }
  
  private boolean stillConnected() {
    // hack method to make sure we don't get disconnected right after a connection/discover/turnOnNotificaitons
    // finishes
    try {
      Thread.sleep(25);
    } catch (InterruptedException e) {}

    boolean isConnected = bleGattCallback.isConnected();
    
    // put a msg in the log if it's dropped us
    if(isConnected == false)
      DebugLog.println(DebugLog.DebugLevelInfo, "stillconnected found that we are not connected");
    
    return isConnected;
  }

  public boolean discoverServices() {
    try {
    // discover services
    DebugLog.println(DebugLog.DebugLevelInfo, "Attempting Discover Services");
    CountDownLatch servicesLatch = bleGattCallback.newServicesLatch();
    bluetoothGatt.discoverServices();
    if(servicesLatch.await(TimeoutSecs, TimeUnit.SECONDS) == false)
      {
        bluetoothUtil.closeDownGatt(bluetoothGatt);
        throw new IOException("Could not discover services");
      }
    } catch (Exception e) {
      // close gatt connection if we had an exception after it was already opened
      endCommunications();
      return false;
    }
    
    return stillConnected();
  }
  
  public boolean turnOnNotifications() {
    try {
      // turn on the notifications for the notify characteristics
      bleGattCallback.turnOnNotifications(bluetoothGatt);
    
      Thread.sleep(enumerateMillis);

      // for now put this here so that we can see battery level in logs
      getBatteryLevel();
      getPowerAttached();
      getDeviceAttached();
    } catch (Exception e) {
      // close gatt connection if we had an exception after it was already opened
      endCommunications();
      return false;
    }
    //    bleGattCallback.waitForBoot(TimeoutSecs * 1000);

    return stillConnected();
	}
  
  public boolean robustConnect(String name, int attempts) {
    return robustConnect(name, null, attempts);
  }
  
  public boolean robustConnect(String name, String macAddress, int attempts) {
    // method to robustly connect to device.  If first attempt fails, then it will try
    // again, cycling bluetooth, if necessary
    for(int i=0; i < attempts; i++)
      {
        // allow for two attempts to connect
        if(i > 0)
          DebugLog.println(DebugLog.DebugLevelInfo, "loop stat robustconnect iteration " + i);
        
        try {
          findDevice(name, macAddress);
        } catch (Exception e){
          // device not in range
          DebugLog.println(DebugLog.DebugLevelInfo, "could not find ble device " + name + " address " + macAddress);
          if(attemptBluetoothCycling)
            bluetoothUtil.cycleBluetoothPower();
          continue;
        }

        try {
          connectToDevice();
        } catch (Exception e) {
          DebugLog.println(DebugLog.DebugLevelInfo, "could not connect to device");
          // clear devices in case this is a case of the bluetooth adapter being 
          // cycled and having a stale bluetoothDevice handle in our map 
          if(attemptBluetoothCycling)
            bluetoothUtil.cycleBluetoothPower();
          continue;
        }
        
        if(discoverServices() == false)
          {
            DebugLog.println(DebugLog.DebugLevelInfo, "discoverservices returned false, looping again");
            if(attemptBluetoothCycling)
              bluetoothUtil.cycleBluetoothPower();
            continue;
          }
        
        if(turnOnNotifications() == false)
          {
            DebugLog.println(DebugLog.DebugLevelInfo, "turnonnotifications returned false, looping again");
            // need to cycle bluetooth to fix this problem
            if(attemptBluetoothCycling)
              bluetoothUtil.cycleBluetoothPower();
            continue;
          }
        
        // made it here so we are good
        break;
      }
    
    if(bleGattCallback != null && bleGattCallback.isConnected())
      return true;
    else
      return false;
  }

  public int getBatteryLevel() throws InterruptedException, IOException {
    BluetoothGattCharacteristic charact = bleGattCallback.getCharacteristic(BatteryLevelCharacteristicUuidString);

    if(charact == null)
      {
        DebugLog.println(DebugLog.DebugLevelInfo, "could not find battery level characteristic");
        return -1;
      }

    DebugLog.println(DebugLog.DebugLevelDetail, "reading characteristic " + charact.getUuid().toString());
    bleGattCallback.readCharacteristic(bluetoothGatt, charact);

    return bleGattCallback.getBatteryLevel();
  }
  
  public int getPowerAttached() throws InterruptedException, IOException {
    BluetoothGattCharacteristic charact = bleGattCallback.getCharacteristic(SmartloopPowerCharUuidString);

    if(charact == null)
      {
        DebugLog.println(DebugLog.DebugLevelInfo, "could not find power attached characteristic");
        return -1;
      }

    DebugLog.println(DebugLog.DebugLevelDetail, "reading characteristic " + charact.getUuid().toString());
    bleGattCallback.readCharacteristic(bluetoothGatt, charact);

    return bleGattCallback.getPowerAttached();
  }
  
  public int getDeviceAttached() throws InterruptedException, IOException {
    BluetoothGattCharacteristic charact = bleGattCallback.getCharacteristic(SmartloopDevStsCharUuidString);

    if(charact == null)
      {
        DebugLog.println(DebugLog.DebugLevelInfo, "could not find device attached characteristic");
        return -1;
      }

    DebugLog.println(DebugLog.DebugLevelDetail, "reading characteristic " + charact.getUuid().toString());
    bleGattCallback.readCharacteristic(bluetoothGatt, charact);

    return bleGattCallback.getDeviceAttached();
  }
  
  public byte[] read(int count) throws IOException {
    try {
    
    DebugLog.println(DebugLog.DebugLevelDetail, "reading " + count + " bytes");
    byte[] ret;

    if(count <= blockSize)
      ret = readBlock(count);
    else
      {
        ret = new byte[count];
        
        for(int byteIdx = 0, bytesRead = 0 ; byteIdx < count; byteIdx += bytesRead)
          {
            bytesRead = Math.min(blockSize, count - byteIdx);
            byte[] bytes = readBlock(bytesRead);
            System.arraycopy(bytes, 0, ret, byteIdx, bytesRead);
          }
      }

    DebugLog.println(DebugLog.DebugLevelDetail, "read " + count + " bytes " + ArraysUtil.toString(ret));
    return ret;

    } catch(IOException e) {
      // if there is an IO exception, then cycle bluetooth power
      DebugLog.println(DebugLog.DebugLevelInfo, "IOException in read, cycling bluetooth");
      if(attemptBluetoothCycling)
        bluetoothUtil.cycleBluetoothPower();
      throw e;
    }

  }
    
  private static final int ReadHeaderLen = 3;
  
	private byte[] readBlock(int count) throws IOException {

	  // send request for data to be sent
	  byte[] requestUSBData = new byte[3];
	  requestUSBData[0] = RequestUSBDataCmd;
    write16BitBigEndian(requestUSBData, requestUSBData.length-2, count);
	  
	  sendMessage(requestUSBData);
	  
	  if(awaitUSBComplete() == false)
	    {
	      final String errmsg = "Failed to get usb done respond from board";
	      DebugLog.println(DebugLog.DebugLevelInfo, errmsg);
	      throw new IOException(errmsg);
	    }

	  // first need to know what the first packet will look like, 
	  // should be 0x80, count_msb, count_lsb
	  byte[] firstPacketHeader = new byte[ReadHeaderLen];
    firstPacketHeader[0] = USBResponseLenCmd;
	  write16BitBigEndian(firstPacketHeader, 1, count + 3);
	  
	  // sorted packet map
	  TreeMap<Integer, byte[]> packets = new TreeMap<Integer, byte[]>();
	  
	  boolean foundFirstPacket = false;
	  int bytesRead = 0;

	  // without sequence numbers is count + 3 due to the 0x81 byte and the CRC at end 
	  for(int byteCount = 0; byteCount < count + 3; byteCount += bytesRead)
	    {
	      byte[] msg = bleGattCallback.readIncomingMessage(TimeoutSecs);
	      
	      if(msg == null)
          {
            DebugLog.println(DebugLog.DebugLevelInfo, "Readblock msg is null!");
            throw new IOException("Time out waiting for incoming messages");
          }
        else
          DebugLog.println(DebugLog.DebugLevelDetail, "Readblock msg " + ArraysUtil.toString(msg));
          

        if(foundFirstPacket == false)
	        {
	          // see if this is the first packet
	          byte[] msgHeader = new byte[ReadHeaderLen];
	          System.arraycopy(msg, 0, msgHeader, 0, ReadHeaderLen);
	          if(Arrays.equals(firstPacketHeader, msgHeader))
	            {
	              foundFirstPacket = true;
	              byte[] msgBody = new byte[msg.length-ReadHeaderLen];
	              // if it's just the header, go on to next msg
	              if(msgBody.length == 0)
	                {
                    DebugLog.println(DebugLog.DebugLevelDetail, "Readblock continuing...");
	                  continue;
	                }
	              System.arraycopy(msg, ReadHeaderLen, msgBody, 0, msgBody.length);
	              // now set message equal to the body
	              msg = msgBody;
	            }
	        }
	      
	      // now msg should look like seq_num_msb, seq_num_lsb, databytes ...
        if(msg == null)
          DebugLog.println(DebugLog.DebugLevelInfo, "Readblock null msg!");

        int sequenceNumber = read16BitBigEndian(msg, 0);
	      bytesRead = msg.length - 2;
	      byte[] dataBytes = new byte[bytesRead];
	      System.arraycopy(msg, 2, dataBytes, 0, bytesRead);
	      packets.put(sequenceNumber, dataBytes);
	      DebugLog.println(DebugLog.DebugLevelDetail, 
	          "new packet " + sequenceNumber + "  " + ArraysUtil.toString(dataBytes));
	    }

	  // if we get here the packet map should hold the correctly sorted data with the CRC
	  // at the end.  Confirm here
	  int packetIdx = 0;
	  byte[] fullMsg = new byte[count + 3];
	  int byteIdx = 0;
	  for(Map.Entry<Integer, byte[]> entry : packets.entrySet())
	    {
	      int packetNumber = entry.getKey();
	      byte[] dataBytes = entry.getValue();
	      
	      // confirm correct packet ordering
	      if(packetNumber != packetIdx)
	        {
            String err = "Packet ordering error.  Expecting packet " + packetIdx + " but see packet " + packetNumber;
            DebugLog.println(DebugLog.DebugLevelInfo, err);
            throw new IOException(err);
	        }
	      else
	        packetIdx++;
	      
	      if(byteIdx + dataBytes.length > fullMsg.length)
	        {
	          String err = "Error: Data length too long.  Maximum " + fullMsg.length + " but see at least " + (byteIdx + dataBytes.length); 
	          DebugLog.println(DebugLog.DebugLevelInfo, err);
	          throw new IOException(err);
	        }
	      
	      System.arraycopy(dataBytes, 0, fullMsg, byteIdx, dataBytes.length);
	      byteIdx += dataBytes.length;
	    }

	  if(byteIdx != count + 3)
      {
        String err = "Incorrect full message length of " + byteIdx + ". Expecting " + (count + 3);
        DebugLog.println(DebugLog.DebugLevelInfo, err);
        throw new IOException(err);
      }
	  
	  if(fullMsg[0] != USBResponseDataCmd)
      {
        String err = "Missing 0x81 as first byte in usb data response";
        DebugLog.println(DebugLog.DebugLevelInfo, err);
        throw new IOException(err);
      }
	  
	  byte[] dataOnly = new byte[fullMsg.length - 3];
	  System.arraycopy(fullMsg, 1, dataOnly, 0, dataOnly.length);
	  int calcCrc = Crc16.CalculateCrc16(dataOnly, 0, dataOnly.length);

	  // need to read the crc as a little endian signed short
	  byte[] crcbytes = new byte[2];
    crcbytes[0] = fullMsg[fullMsg.length-2];
    crcbytes[1] = fullMsg[fullMsg.length-1];
	  
	  int msgCrc = (int)ByteBuffer.wrap(crcbytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
	  
	  if(msgCrc != calcCrc)
	    {
	      String err = "Crc error.  Calced = " + calcCrc + " does not match msg crc of " + msgCrc;
	      DebugLog.println(DebugLog.DebugLevelInfo, err);
	      throw new IOException(err);
	    }
	  
    DebugLog.println(DebugLog.DebugLevelDetail, "BLE Usb read " + Arrays.toString(dataOnly));

    // sleep to allow STM to process usb read command
    try {
      Thread.sleep(ReadWriteFinishDelay);
    } catch (InterruptedException e) {}
    // if we make it here, we're good and ready to read
    
    return dataOnly;
	}

  private static final byte[] usbDone0 = {(byte)0x85, (byte)0x00};
  private static final byte[] usbDone1 = {(byte)0x85, (byte)0x01};
	
	private boolean awaitUSBComplete() {
	  DebugLog.println(DebugLog.DebugLevelDetail, "Waiting for usb done response");
	  byte[] msg = bleGattCallback.readIncomingMessage(TimeoutSecs);
    if(Arrays.equals(msg, usbDone1) || Arrays.equals(msg, usbDone0))
      {
        DebugLog.println(DebugLog.DebugLevelDetail, "Received usb done response");
        return true;
      }
    else
      return false;
  }

  public void write(byte[] output) throws IOException {

    try {
	  byte[] wrappedOutput = new byte[output.length+3];
	  System.arraycopy(output, 0, wrappedOutput, 1, output.length);
	  wrappedOutput[0] = SendUSBDataCmd;
	  write16BitBigEndian(wrappedOutput, wrappedOutput.length-2, 0);
	  sendMessage(wrappedOutput);
    
    if(awaitUSBComplete() == false)
      {
        final String errmsg = "Failed to get usb done respond from board";
        DebugLog.println(DebugLog.DebugLevelInfo, errmsg);
        throw new IOException(errmsg);
      }

    DebugLog.println(DebugLog.DebugLevelDetail, "finished writing successfully");

    // sleep to allow STM to process usb write command
    try {
      Thread.sleep(ReadWriteFinishDelay);
    } catch (InterruptedException e) {}
    
    } catch(IOException e) {
      // if there is an IO exception, then cycle bluetooth power
      DebugLog.println(DebugLog.DebugLevelInfo, "IOException in read, cycling bluetooth");
      if(attemptBluetoothCycling)
        bluetoothUtil.cycleBluetoothPower();
      throw e;
    }
    // if we make it here, we're good and ready to read
	}

	public void endCommunications() {
    try {
      if(bluetoothGatt != null)
        {
          BluetoothUtil bluetoothUtil = new BluetoothUtil();
          bluetoothUtil.closeDownGatt(bluetoothGatt);
          bluetoothGatt = null;
        }
    } catch (Exception e) {
      DebugLog.println(DebugLog.DebugLevelInfo, e.getMessage());
    }
    
    DebugLog.printDate(DebugLog.DebugLevelInfo);
		DebugLog.println(DebugLog.DebugLevelInfo, "endCommunications() called, closed gatt connection");
	}

	public void sendStmRebootCommand() {
	  final byte[] cmd = {RebootSTMCmd};

	  try {
	    DebugLog.println(DebugLog.DebugLevelInfo, "Rebooting STM.\nBLE Usb writing " + ArraysUtil.toString(cmd));
	    sendMessage(cmd);
	  } catch (IOException e) {
	    DebugLog.println(DebugLog.DebugLevelInfo, "BLE Error sending reboot cmd");
	  }

	}

	public void sendNrfRebootCommand() {
    final byte[] nrfcmd = {RebootnRFCmd};

    try {
      DebugLog.println(DebugLog.DebugLevelInfo, "Rebooting nrf.\nBLE Usb writing " + ArraysUtil.toString(nrfcmd));
      sendMessage(nrfcmd);
    } catch (IOException e) {
      DebugLog.println(DebugLog.DebugLevelInfo, "BLE Error sending reboot cmd");
    }
	}
	
	private void sendMessage(byte[] message) throws IOException {
	  BluetoothGattCharacteristic control = 
	      bleGattCallback.getCharacteristic(SmartloopControlPointUuidString);
	  
	  if(control == null)
	    throw new IOException("No control point characteristic available");
	  
	  byte[] messageWithHeader = new byte[message.length + 3];
	  System.arraycopy(message, 0, messageWithHeader, 3, message.length);
	  write16BitBigEndian(messageWithHeader, 1, message.length);
	  
	  int bytesSent = 0;
	  for(int byteIdx = 0; byteIdx < messageWithHeader.length; byteIdx += bytesSent)
	    {
	      bytesSent = Math.min(BytesPerMessage, messageWithHeader.length - byteIdx);
	      byte[] packet = new byte[bytesSent];
	      System.arraycopy(messageWithHeader, byteIdx, packet, 0, bytesSent);
	      control.setValue(packet);
	      DebugLog.println(DebugLog.DebugLevelDetail, "writing packet " + ArraysUtil.toString(packet));
	      // unclear whether we need to wait to confirm that it was written before proceeding
        bleGattCallback.writeCharacteristic(bluetoothGatt, control);
//        try {
//          Thread.sleep(ReadWriteFinishDelay);
//        } catch (InterruptedException e) {}
	    }
	}
	
	private void write16BitBigEndian(byte[] array, int startIdx, int value) {
	  array[startIdx] = (byte)((value >>> 8) & 0xFF);
	  array[startIdx+1] = (byte)(value & 0xFF);
	}

	private int read16BitBigEndian(byte[] array, int startIdx) {
	  int value = ((int)array[startIdx] & 0xFF);
	  value = (value << 8) + (array[startIdx+1] & 0xFF);

	  return value;
	}

}
