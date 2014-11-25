package edu.virginia.dtc.G4DevKit;

public abstract interface IReceiverComm
{
  public abstract byte[] sendReceiverMessageForResponse(byte[] paramArrayOfByte)
    throws Exception;
}

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.IReceiverComm
 * JD-Core Version:    0.6.0
 */