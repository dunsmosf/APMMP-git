package edu.virginia.dtc.Bioharness_Driver;
import android.app.Activity;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import zephyr.android.BioHarnessBT.*;

public class NewConnectedListener extends ConnectListenerImpl
{
	private Handler _OldHandler;
	private Handler _aNewHandler; 
	final int GP_MSG_ID = 0x20;
	final int BREATHING_MSG_ID = 0x21;
	final int ECG_MSG_ID = 0x22;
	final int RtoR_MSG_ID = 0x24;
	final int ACCEL_100mg_MSG_ID = 0x2A;
	final int SUMMARY_MSG_ID = 0x2B;
	
	
	private int GP_HANDLER_ID = 0x20;
	/*
	private final int HEART_RATE = 0x100;
	private final int RESPIRATION_RATE = 0x101;
	private final int SKIN_TEMPERATURE = 0x102;
	private final int POSTURE = 0x103;
	private final int PEAK_ACCLERATION = 0x104;
	*/
		
	private final int GSR_b= 0x100;							
	private final int HRV_b=  0x101;
	private final int HR_b=  0x102;										
	private final int Posture_b=  0x103;										
	private final int ROG_b=  0x104;										
	private final int BatteryLevel_b=  0x105;										
	private final int BreathingRate_conf_b=  0x106;					
	private final int HeartRate_conf_b=  0x107;					
	private final int ROGstatus_b=  0x108;					
	private final int SeqNum_b=  0x109;					
	private final int System_conf_b=  0x110;					
	private final int VersionNumber_b=  0x111;					
	private final int Activity_b=  0x112;					
	private final int Voltage_b=  0x113;					
	private final int BreathingWaveAmplitude_b=  0x114;					
	private final int BreathingWaveAmplitudeNoise_b=  0x115;					
	private final int ECGAmplitude_b=  0x116;					
	private final int ECGNoise_b=  0x117;					
	private final int Lateral_AxisAccnMin_b=  0x118;					
	private final int Lateral_AxisAccnPeak_b=  0x119;					
	private final int MsofDay_b=  0x120;					
	private final int PeakAcceleration_b=  0x121;					
	private final int RespirationRate_b=  0x122;					
	private final int Sagittal_AxisAccnMin_b=  0x123;					
	private final int Sagittal_AxisAccnPeak_b=  0x124;					
	private final int Skintemperature_b=  0x125;					
	private final int Vertical_AxisAccnMin_b=  0x126;					
	private final int Vertical_AxisAccnPeak_b=  0x127;
	
	
	/*Creating the different Objects for different types of Packets*/
	private GeneralPacketInfo GPInfo = new GeneralPacketInfo();
	private ECGPacketInfo ECGInfoPacket = new ECGPacketInfo();
	private BreathingPacketInfo BreathingInfoPacket = new  BreathingPacketInfo();
	private RtoRPacketInfo RtoRInfoPacket = new RtoRPacketInfo();
	private AccelerometerPacketInfo AccInfoPacket = new AccelerometerPacketInfo();
	private SummaryPacketInfo SummaryInfoPacket = new SummaryPacketInfo();
	
	private PacketTypeRequest RqPacketType = new PacketTypeRequest();
	public NewConnectedListener(Handler handler,Handler _NewHandler) {
		super(handler, null);
		_OldHandler= handler;
		_aNewHandler = _NewHandler;

		// TODO Auto-generated constructor stub

	}
	public void Connected(ConnectedEvent<BTClient> eventArgs) {
		System.out.println(String.format("Connected to BioHarness %s.", eventArgs.getSource().getDevice().getName()));
		/*Use this object to enable or disable the different Packet types*/
		//RqPacketType.GP_ENABLE = true;
		//RqPacketType.BREATHING_ENABLE = true;
		//RqPacketType.LOGGING_ENABLE = true;
		//RqPacketType.ACCELEROMETER_ENABLE=true;
		//RqPacketType.ECG_ENABLE=true;
		//RqPacketType.RtoR_ENABLE=true;
		RqPacketType.SUMMARY_ENABLE=true;
		
		//Creates a new ZephyrProtocol object and passes it the BTComms object
		ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms(), RqPacketType);
		//ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms(), );
		_protocol.addZephyrPacketEventListener(new ZephyrPacketListener() {
			public void ReceivedPacket(ZephyrPacketEvent eventArgs) {
				ZephyrPacketArgs msg = eventArgs.getPacket();
				byte CRCFailStatus;
				byte RcvdBytes;
				
				
				
				CRCFailStatus = msg.getCRCStatus();
				RcvdBytes = msg.getNumRvcdBytes() ;
				int MsgID = msg.getMsgID();
				byte [] DataArray = msg.getBytes();	
				switch (MsgID)
				{

				case GP_MSG_ID:

					
					//***************Displaying the Heart Rate********************************
					/*
					int HRate =  GPInfo.GetHeartRate(DataArray);
					Message text1 = _aNewHandler.obtainMessage(HEART_RATE);
					Bundle b1 = new Bundle();
					b1.putString("HeartRate", String.valueOf(HRate));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Heart Rate is "+ HRate);

					//***************Displaying the Respiration Rate********************************
					double RespRate = GPInfo.GetRespirationRate(DataArray);
					
					text1 = _aNewHandler.obtainMessage(RESPIRATION_RATE);
					b1.putString("RespirationRate", String.valueOf(RespRate));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Respiration Rate is "+ RespRate);
					
					//***************Displaying the Skin Temperature*******************************
		

					double SkinTempDbl = GPInfo.GetSkinTemperature(DataArray);
					 text1 = _aNewHandler.obtainMessage(SKIN_TEMPERATURE);
					//Bundle b1 = new Bundle();
					b1.putString("SkinTemperature", String.valueOf(SkinTempDbl));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Skin Temperature is "+ SkinTempDbl);
					
					//***************Displaying the Posture******************************************					

					int PostureInt = GPInfo.GetPosture(DataArray);
					text1 = _aNewHandler.obtainMessage(POSTURE);
					b1.putString("Posture", String.valueOf(PostureInt));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Posture is "+ PostureInt);	
					//***************Displaying the Peak Acceleration******************************************
	
					double PeakAccDbl = GPInfo.GetPeakAcceleration(DataArray);
					text1 = _aNewHandler.obtainMessage(PEAK_ACCLERATION);
					b1.putString("PeakAcceleration", String.valueOf(PeakAccDbl));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					System.out.println("Peak Acceleration is "+ PeakAccDbl);	
					
					byte ROGStatus = GPInfo.GetROGStatus(DataArray);
					System.out.println("ROG Status is "+ ROGStatus);
					*/
					break;
				case BREATHING_MSG_ID:

					/*Do what you want. Printing Sequence Number for now*/
					/*
					Bundle b2 = new Bundle();
					double BreathingWaiveAmplitude = GPInfo.GetBreathingWaveAmplitude(DataArray);
					Message text2 = _aNewHandler.obtainMessage(BREATHING_MSG_ID);
					b2.putString("BreathingWaiveAmplitude", String.valueOf(BreathingWaiveAmplitude));
					text2.setData(b2);
					_aNewHandler.sendMessage(text2);
					
					
					double RespirationRate = GPInfo.GetRespirationRate(DataArray);
					text2 = _aNewHandler.obtainMessage(BREATHING_MSG_ID);
					b2.putString("RespirationRate", String.valueOf(RespirationRate));
					text2.setData(b2);
					_aNewHandler.sendMessage(text2);
					
					System.out.println("Breathing Packet Sequence Number is "+BreathingInfoPacket.GetSeqNum(DataArray));
					*/
					break;
				case ECG_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
					/*
					Bundle b3 = new Bundle();					
					double ECGamplitude = GPInfo.GetECGAmplitude(DataArray);
					Message text3 = _aNewHandler.obtainMessage(ECG_MSG_ID);
					b3.putString("ECGamplitude", String.valueOf(ECGamplitude));
					text3.setData(b3);
					_aNewHandler.sendMessage(text3);
					
					double ECGnoise = GPInfo.GetECGNoise(DataArray);
					text3 = _aNewHandler.obtainMessage(ECG_MSG_ID);
					b3.putString("ECGnoise", String.valueOf(ECGnoise));
					text3.setData(b3);
					_aNewHandler.sendMessage(text3);
					
					System.out.println("ECG Packet Sequence Number is "+ECGInfoPacket.GetSeqNum(DataArray));
					*/
					break;
				case RtoR_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
					
					System.out.println("R to R Packet Sequence Number is "+RtoRInfoPacket.GetSeqNum(DataArray));
					break;
				case ACCEL_100mg_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
					System.out.println("Accelerometry Packet Sequence Number is "+AccInfoPacket.GetSeqNum(DataArray));
					break;
				case SUMMARY_MSG_ID:
					/*Do what you want. Printing Sequence Number for now*/
					Message text2 = _aNewHandler.obtainMessage(GSR_b);
					Bundle b2 = new Bundle();
					
					
					int GSR =  SummaryInfoPacket.GetGSR(DataArray);
					//Message text2 = _aNewHandler.obtainMessage(GSR_b);
					//Bundle b2 = new Bundle();
					b2.putInt("GSR", GSR);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("GSR is "+ GSR);

					int HRV =  SummaryInfoPacket.GetHearRateVariability(DataArray);
					//text2 = _aNewHandler.obtainMessage(HRV_b);
					//b2 = new Bundle();
					b2.putInt("HRV",  HRV);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("HRV is "+ HRV);
					
					int HR =  SummaryInfoPacket.GetHeartRate(DataArray);
					//text2 = _aNewHandler.obtainMessage(HR_b);
					//b2 = new Bundle();
					b2.putInt("HR",  HR);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("HR is "+ HR);
					
					int Posture =  SummaryInfoPacket.GetPosture(DataArray);
					//text2 = _aNewHandler.obtainMessage(Posture_b);
					//b2 = new Bundle();
					b2.putInt("Posture",  Posture);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Posture is "+ Posture);
					
					int ROG =  SummaryInfoPacket.GetROGTime(DataArray);
					//text2 = _aNewHandler.obtainMessage(ROG_b);
					//b2 = new Bundle();
					b2.putInt("ROG",  ROG);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("ROG is "+ ROG);
					
					int BatteryLevel =  SummaryInfoPacket.GetBatteryLevel(DataArray);
					//text2 = _aNewHandler.obtainMessage(BatteryLevel_b);
					//b2 = new Bundle();
					b2.putInt("BatteryLevel",  BatteryLevel);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("BatteryLevel is "+ BatteryLevel);
					
					int BreathingRate_conf =  SummaryInfoPacket.GetBreathingRateConfidence(DataArray);
					//text2 = _aNewHandler.obtainMessage(BreathingRate_conf_b);
					//b2 = new Bundle();
					b2.putInt("BreathingRate_conf",  BreathingRate_conf);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("BreathingRate_conf is "+ BreathingRate_conf);
					
					int HeartRate_conf =  SummaryInfoPacket.GetHeartRateRateConfidence(DataArray);
					//text2 = _aNewHandler.obtainMessage(HeartRate_conf_b);
					//b2 = new Bundle();
					b2.putInt("HeartRate_conf",  HeartRate_conf);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("HeartRate_conf is "+ HeartRate_conf);
					
					int ROGstatus =  SummaryInfoPacket.GetROGStatus(DataArray);
					//text2 = _aNewHandler.obtainMessage(ROGstatus_b);
					//b2 = new Bundle();
					b2.putInt("ROGstatus",  ROGstatus);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("ROGstatus is "+ ROGstatus);
					
					int SeqNum =  SummaryInfoPacket.GetSeqNum(DataArray);
					//text2 = _aNewHandler.obtainMessage(SeqNum_b);
					//b2 = new Bundle();
					b2.putInt("SeqNum",  SeqNum);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("SeqNum is "+ SeqNum);
					
					int System_conf =  SummaryInfoPacket.GetSystemConfidence(DataArray);
					//text2 = _aNewHandler.obtainMessage(System_conf_b);
					//b2 = new Bundle();
					b2.putInt("System_conf",  System_conf);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("System_conf is "+ System_conf);
					
					int VersionNumber =  SummaryInfoPacket.GetVersionNumber(DataArray);
					//text2 = _aNewHandler.obtainMessage(VersionNumber_b);
					//b2 = new Bundle();
					b2.putInt("VersionNumber",  VersionNumber);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("VersionNumber is "+ VersionNumber);
					
					double Activity =  SummaryInfoPacket.GetActivity(DataArray);
					//text2 = _aNewHandler.obtainMessage(Activity_b);
					//b2 = new Bundle();
					b2.putDouble("Activity",  Activity);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Activity is "+ Activity);
					
					double Voltage =  SummaryInfoPacket.GetBatteryVoltage(DataArray);
					//text2 = _aNewHandler.obtainMessage(Voltage_b);
					//b2 = new Bundle();
					b2.putDouble("Voltage",  Voltage);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Voltage is "+ Voltage);
					
					double BreathingWaveAmplitude =  SummaryInfoPacket.GetBreathingWaveAmplitude(DataArray);
					//text2 = _aNewHandler.obtainMessage(BreathingWaveAmplitude_b);
					//b2 = new Bundle();
					b2.putDouble("BreathingWaveAmplitude",  BreathingWaveAmplitude);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("BreathingWaveAmplitude is "+ BreathingWaveAmplitude);
					
					double BreathingWaveAmplitudeNoise =  SummaryInfoPacket.GetBreathingWaveAmpNoise(DataArray);
					//text2 = _aNewHandler.obtainMessage(BreathingWaveAmplitudeNoise_b);
					//b2 = new Bundle();
					b2.putDouble("BreathingWaveAmplitudeNoise",  BreathingWaveAmplitudeNoise);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("BreathingWaveAmplitudeNoise is "+ BreathingWaveAmplitudeNoise);
					
					double ECGAmplitude =  SummaryInfoPacket.GetECGAmplitude(DataArray);
					//text2 = _aNewHandler.obtainMessage(ECGAmplitude_b);
					//b2 = new Bundle();
					b2.putDouble("ECGAmplitude",  ECGAmplitude);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("ECGAmplitude is "+ ECGAmplitude);
					
					double ECGNoise =  SummaryInfoPacket.GetECGNoise(DataArray);
					//text2 = _aNewHandler.obtainMessage(ECGNoise_b);
					//b2 = new Bundle();
					b2.putDouble("ECGNoise",  ECGNoise);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("ECGNoise is "+ ECGNoise);
					
					double Lateral_AxisAccnMin =  SummaryInfoPacket.GetLateral_AxisAccnMin(DataArray);
					//text2 = _aNewHandler.obtainMessage(Lateral_AxisAccnMin_b);
					//b2 = new Bundle();
					b2.putDouble("Lateral_AxisAccnMin",  Lateral_AxisAccnMin);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Lateral_AxisAccnMin is "+ Lateral_AxisAccnMin);
					
					double Lateral_AxisAccnPeak =  SummaryInfoPacket.GetLateral_AxisAccnPeak(DataArray);
					//text2 = _aNewHandler.obtainMessage(Lateral_AxisAccnPeak_b);
					//b2 = new Bundle();
					b2.putDouble("Lateral_AxisAccnPeak",  Lateral_AxisAccnPeak);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Lateral_AxisAccnPeak is "+ Lateral_AxisAccnPeak);
					
					double MsofDay =  SummaryInfoPacket.GetMsofDay(DataArray);
					//text2 = _aNewHandler.obtainMessage(MsofDay_b);
					//b2 = new Bundle();
					b2.putDouble("MsofDay",  MsofDay);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("MsofDay is "+ MsofDay);
					
					double PeakAcceleration =  SummaryInfoPacket.GetPeakAcceleration(DataArray);
					//text2 = _aNewHandler.obtainMessage(PeakAcceleration_b);
					//b2 = new Bundle();
					b2.putDouble("PeakAcceleration",  PeakAcceleration);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("PeakAcceleration is "+ PeakAcceleration);
					
					double RespirationRate =  SummaryInfoPacket.GetRespirationRate(DataArray);
					//text2 = _aNewHandler.obtainMessage(RespirationRate_b);
					//b2 = new Bundle();
					b2.putDouble("RespirationRate",  RespirationRate);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("RespirationRate is "+ RespirationRate);
					
					double Sagittal_AxisAccnMin =  SummaryInfoPacket.GetSagittal_AxisAccnMin(DataArray);
					//text2 = _aNewHandler.obtainMessage(Sagittal_AxisAccnMin_b);
					//b2 = new Bundle();
					b2.putDouble("Sagittal_AxisAccnMin",  Sagittal_AxisAccnMin);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Sagittal_AxisAccnMin is "+ Sagittal_AxisAccnMin);
					
					double Sagittal_AxisAccnPeak =  SummaryInfoPacket.GetSagittal_AxisAccnPeak(DataArray);
					//text2 = _aNewHandler.obtainMessage(Sagittal_AxisAccnPeak_b);
					//b2 = new Bundle();
					b2.putDouble("Sagittal_AxisAccnPeak",  Sagittal_AxisAccnPeak);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Sagittal_AxisAccnPeak is "+ Sagittal_AxisAccnPeak);
					
					double SkinTemperature =  SummaryInfoPacket.GetSkinTemperature(DataArray);
					//text2 = _aNewHandler.obtainMessage(Skintemperature_b);
					//b2 = new Bundle();
					b2.putDouble("SkinTemperature",  SkinTemperature);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("SkinTemperature is "+ SkinTemperature);
					
					double Vertical_AxisAccnMin =  SummaryInfoPacket.GetVertical_AxisAccnMin(DataArray);
					//text2 = _aNewHandler.obtainMessage(Vertical_AxisAccnMin_b);
					//b2 = new Bundle();
					b2.putDouble("Vertical_AxisAccnMin",  Vertical_AxisAccnMin);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Vertical_AxisAccnMin is "+ Vertical_AxisAccnMin);
					
					double Vertical_AxisAccnPeak =  SummaryInfoPacket.GetVertical_AxisAccnPeak(DataArray);
					//text2 = _aNewHandler.obtainMessage(Vertical_AxisAccnPeak_b);
					//b2 = new Bundle();
					b2.putDouble("Vertical_AxisAccnPeak",  Vertical_AxisAccnPeak);
					//text2.setData(b2);
					//_aNewHandler.sendMessage(text2);
					System.out.println("Vertical_AxisAccnPeak is "+ Vertical_AxisAccnPeak);
					
					
					text2.setData(b2);
					_aNewHandler.sendMessage(text2);
					break;
					
				}
			}
		});
	}
	
}