package edu.virginia.dtc.BTLE_Tandem;

import android.bluetooth.BluetoothGattCharacteristic;

public class BTLE_Info {

	public static String charPerm(int perm)
	{
		String permStr = "";
		
		if((perm & BluetoothGattCharacteristic.PERMISSION_READ ) == BluetoothGattCharacteristic.PERMISSION_READ)
			permStr += "PERMISSION READ - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED ) == BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)
			permStr += "PERMISSION READ ENCRYPT - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM ) == BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM)
			permStr += "PERMISSION READ ENCRYPT MITM - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_WRITE ) == BluetoothGattCharacteristic.PERMISSION_WRITE)
			permStr += "PERMISSION WRITE - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED) == BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED)
			permStr += "PERMISSION WRITE ENCRYPT - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM ) == BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM)
			permStr += "PERMISSION WRITE ENCRYPT MITM - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED) == BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED)
			permStr += "PERMISSION WRITE SIGNED - ";
		if((perm & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM ) == BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM)
			permStr += "PERMISSION WRITE SIGNED MITM - ";
		
		return permStr;
	}
	
	public static String charProp(int prop)
	{
		String propStr = "";
		
		if((prop & BluetoothGattCharacteristic.PROPERTY_BROADCAST) == BluetoothGattCharacteristic.PROPERTY_BROADCAST)
			propStr = "Broadcast - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) == BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS)
			propStr = "Extended Props - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE)
			propStr = "Indicate - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == BluetoothGattCharacteristic.PROPERTY_NOTIFY)
			propStr = "Notify - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_READ) == BluetoothGattCharacteristic.PROPERTY_READ)
			propStr = "Read - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) == BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE)
			propStr = "Signed Write - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_WRITE) == BluetoothGattCharacteristic.PROPERTY_WRITE)
			propStr = "Write - ";
		if((prop & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
			propStr = "Write No Response - ";
		
		return propStr;
	}
}
