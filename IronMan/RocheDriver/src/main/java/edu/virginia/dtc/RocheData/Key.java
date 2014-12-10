package edu.virginia.dtc.RocheData;

import javax.crypto.Cipher;

import edu.virginia.dtc.SysMan.Debug;


public class Key {

	private static final int KEYSIZE = 16;
	private static final int PIN_CODE_LENGTH = 10;
	private static final byte PIN_CODE_MASK = (byte) 0xFF;
	
	private static final String TAG = "Key";
	
	public byte[] generateKey(String strKey)
	{
		final String FUNC_TAG = "generateKey";

		byte[] pin = new byte[PIN_CODE_LENGTH];
		
		for(int i=0;i<strKey.length();i++)
			pin[i] = ((byte)(strKey.charAt(i)));		//Don't convert to decimal here
		
		String d = "";
		for(byte b:pin)
			d += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "generateKey >>> PIN: "+d);
		
		byte[] key = new byte[KEYSIZE];
		
		for(int i = 0; i<KEYSIZE; i++)
		{
			if(i < PIN_CODE_LENGTH)
			{
				key[i] = pin[i];
			}
			else
			{
				Debug.i(TAG, FUNC_TAG, "generateKey >>> XOR "+String.format("%02X", PIN_CODE_MASK)+" WITH "+String.format("%02X", pin[i-PIN_CODE_LENGTH]));
				key[i] = (byte) (PIN_CODE_MASK ^ pin[i - PIN_CODE_LENGTH]);
			}
		}
		
		d = "";
		for(byte b:key)
			d += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "generateKey >>> KEY: "+d);
		
		return key;
	}
}
