package edu.virginia.dtc.btleUsb;

class Crc16
{

  private static short[] m_crc16Table = { 
    0, 4129, 8258, 12387, 16516, 20645, 24774, 28903, 
    -32504, -28375, -24246, -20117, 
    -15988, -11859, -7730, -3601, 4657, 
    528, 12915, 8786, 21173, 17044, 29431, 25302, 
    -27847, -31976, -19589, -23718, 
    -11331, -15460, -3073, -7202, 9314, 
    13379, 1056, 5121, 25830, 29895, 17572, 21637, 
    -23190, -19125, -31448, -27383, 
    -6674, -2609, -14932, -10867, 13907, 
    9842, 5649, 1584, 30423, 26358, 22165, 18100, 
    -18597, -22662, -26855, -30920, 
    -2081, -6146, -10339, -14404, 18628, 
    22757, 26758, 30887, 2112, 6241, 10242, 14371, 
    -13876, -9747, -5746, -1617, 
    -30392, -26263, -22262, -18133, 23285, 
    19156, 31415, 27286, 6769, 2640, 14899, 10770, 
    -9219, -13348, -1089, -5218, 
    -25735, -29864, -17605, -21734, 27814, 
    31879, 19684, 23749, 11298, 15363, 3168, 7233, 
    -4690, -625, -12820, -8755, 
    -21206, -17141, -29336, -25271, 32407, 
    28342, 24277, 20212, 15891, 11826, 7761, 3696, 
    -97, -4162, -8227, -12292, 
    -16613, -20678, -24743, -28808, 
    -28280, -32343, -20022, -24085, 
    -12020, -16083, -3762, -7825, 4224, 
    161, 12482, 8419, 20484, 16421, 28742, 24679, 
    -31815, -27752, -23557, -19494, 
    -15555, -11492, -7297, -3234, 689, 
    4752, 8947, 13010, 16949, 21012, 25207, 29270, 
    -18966, -23093, -27224, -31351, 
    -2706, -6833, -10964, -15091, 13538, 
    9411, 5280, 1153, 29798, 25671, 21540, 17413, 
    -22565, -18438, -30823, -26696, 
    -6305, -2178, -14563, -10436, 9939, 
    14066, 1681, 5808, 26199, 30326, 17941, 22068, 
    -9908, -13971, -1778, -5841, 
    -26168, -30231, -18038, -22101, 22596, 
    18533, 30726, 26663, 6336, 2273, 14466, 10403, 
    -13443, -9380, -5313, -1250, 
    -29703, -25640, -21573, -17510, 19061, 
    23124, 27191, 31254, 2801, 6864, 10931, 14994, 
    -722, -4849, -8852, -12979, 
    -16982, -21109, -25112, -29239, 31782, 
    27655, 23652, 19525, 15522, 11395, 7392, 3265, 
    -4321, -194, -12451, -8324, 
    -20581, -16454, -28711, -24584, 28183, 
    32310, 20053, 24180, 11923, 16050, 3793, 7920 };

  static short CalculateCrc16(byte[] buf, int start, int end)
  {
    short crc = 0;

    for (int counter = start; counter < end; counter++)
    {
      crc = (short)(crc << 8 ^ m_crc16Table[((crc >> 8 ^ buf[counter]) & 0xFF)]);
    }

    return crc;
  }

  static void StoreBytes(short value, byte[] targetData, int targetOffset)
  {
    targetData[(targetOffset + 0)] = ((byte)(value >> 0 & 0xFF));
    targetData[(targetOffset + 1)] = ((byte)(value >> 8 & 0xFF));
  }

  public static void main(String[] args) {
//    byte[] packet = {1, 7, 0, 16, 10, 69, 89};
//    byte[] packet = {1, 7, 0, 16, 4, 0, 0};
//    byte[] packet = {1, 12, 0, 17, 4, (byte)250, 4, 0, 0, 1, 0, 0};
    short crc = CalculateCrc16(packet, 0, packet.length - 2);
    
    StoreBytes(crc, packet, packet.length - 2);
    
    for(int i=0; i < packet.length; i++)
      System.out.println((int)(packet[i] & 0xff));
    
  }
  
  static byte[] packet = {
      (byte)1,
      (byte)22,
      (byte)2,
      (byte)1,
      (byte)28,
      (byte)189,
      (byte)0,
      (byte)0,
      (byte)38,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)4,
      (byte)2,
      (byte)250,
      (byte)4,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)0,
      (byte)220,
      (byte)149,
      (byte)2,
      (byte)192,
      (byte)103,
      (byte)8,
      (byte)254,
      (byte)193,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)252,
      (byte)99,
      (byte)46,
      (byte)193,
      (byte)103,
      (byte)8,
      (byte)42,
      (byte)195,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)226,
      (byte)94,
      (byte)90,
      (byte)194,
      (byte)103,
      (byte)8,
      (byte)86,
      (byte)196,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)179,
      (byte)27,
      (byte)134,
      (byte)195,
      (byte)103,
      (byte)8,
      (byte)130,
      (byte)197,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)117,
      (byte)218,
      (byte)178,
      (byte)196,
      (byte)103,
      (byte)8,
      (byte)174,
      (byte)198,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)242,
      (byte)154,
      (byte)222,
      (byte)197,
      (byte)103,
      (byte)8,
      (byte)218,
      (byte)199,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)214,
      (byte)232,
      (byte)10,
      (byte)199,
      (byte)103,
      (byte)8,
      (byte)6,
      (byte)201,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)255,
      (byte)108,
      (byte)54,
      (byte)200,
      (byte)103,
      (byte)8,
      (byte)50,
      (byte)202,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)107,
      (byte)96,
      (byte)98,
      (byte)201,
      (byte)103,
      (byte)8,
      (byte)94,
      (byte)203,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)80,
      (byte)235,
      (byte)142,
      (byte)202,
      (byte)103,
      (byte)8,
      (byte)138,
      (byte)204,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)186,
      (byte)134,
      (byte)186,
      (byte)203,
      (byte)103,
      (byte)8,
      (byte)182,
      (byte)205,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)185,
      (byte)9,
      (byte)230,
      (byte)204,
      (byte)103,
      (byte)8,
      (byte)226,
      (byte)206,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)211,
      (byte)18,
      (byte)18,
      (byte)206,
      (byte)103,
      (byte)8,
      (byte)14,
      (byte)208,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)137,
      (byte)127,
      (byte)62,
      (byte)207,
      (byte)103,
      (byte)8,
      (byte)58,
      (byte)209,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)148,
      (byte)148,
      (byte)106,
      (byte)208,
      (byte)103,
      (byte)8,
      (byte)102,
      (byte)210,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)0,
      (byte)96,
      (byte)150,
      (byte)209,
      (byte)103,
      (byte)8,
      (byte)146,
      (byte)211,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)74,
      (byte)101,
      (byte)196,
      (byte)210,
      (byte)103,
      (byte)8,
      (byte)192,
      (byte)212,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)235,
      (byte)39,
      (byte)238,
      (byte)211,
      (byte)103,
      (byte)8,
      (byte)234,
      (byte)213,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)241,
      (byte)56,
      (byte)26,
      (byte)213,
      (byte)103,
      (byte)8,
      (byte)22,
      (byte)215,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)130,
      (byte)222,
      (byte)70,
      (byte)214,
      (byte)103,
      (byte)8,
      (byte)66,
      (byte)216,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)62,
      (byte)99,
      (byte)114,
      (byte)215,
      (byte)103,
      (byte)8,
      (byte)110,
      (byte)217,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)70,
      (byte)219,
      (byte)158,
      (byte)216,
      (byte)103,
      (byte)8,
      (byte)154,
      (byte)218,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)133,
      (byte)57,
      (byte)202,
      (byte)217,
      (byte)103,
      (byte)8,
      (byte)198,
      (byte)219,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)51,
      (byte)235,
      (byte)246,
      (byte)218,
      (byte)103,
      (byte)8,
      (byte)242,
      (byte)220,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)120,
      (byte)6,
      (byte)34,
      (byte)220,
      (byte)103,
      (byte)8,
      (byte)30,
      (byte)222,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)10,
      (byte)125,
      (byte)78,
      (byte)221,
      (byte)103,
      (byte)8,
      (byte)74,
      (byte)223,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)216,
      (byte)97,
      (byte)122,
      (byte)222,
      (byte)103,
      (byte)8,
      (byte)118,
      (byte)224,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)126,
      (byte)159,
      (byte)166,
      (byte)223,
      (byte)103,
      (byte)8,
      (byte)162,
      (byte)225,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)184,
      (byte)94,
      (byte)210,
      (byte)224,
      (byte)103,
      (byte)8,
      (byte)206,
      (byte)226,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)214,
      (byte)96,
      (byte)254,
      (byte)225,
      (byte)103,
      (byte)8,
      (byte)250,
      (byte)227,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)203,
      (byte)139,
      (byte)42,
      (byte)227,
      (byte)103,
      (byte)8,
      (byte)38,
      (byte)229,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)160,
      (byte)2,
      (byte)86,
      (byte)228,
      (byte)103,
      (byte)8,
      (byte)82,
      (byte)230,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)70,
      (byte)221,
      (byte)130,
      (byte)229,
      (byte)103,
      (byte)8,
      (byte)126,
      (byte)231,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)59,
      (byte)2,
      (byte)174,
      (byte)230,
      (byte)103,
      (byte)8,
      (byte)170,
      (byte)232,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)236,
      (byte)175,
      (byte)218,
      (byte)231,
      (byte)103,
      (byte)8,
      (byte)214,
      (byte)233,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)214,
      (byte)185,
      (byte)6,
      (byte)233,
      (byte)103,
      (byte)8,
      (byte)2,
      (byte)235,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)65,
      (byte)224,
      (byte)50,
      (byte)234,
      (byte)103,
      (byte)8,
      (byte)46,
      (byte)236,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)82,
      (byte)11,
      (byte)94,
      (byte)235,
      (byte)103,
      (byte)8,
      (byte)90,
      (byte)237,
      (byte)102,
      (byte)8,
      (byte)132,
      (byte)0,
      (byte)20,
      (byte)118,
      (byte)121,
      (byte)255,
      (byte)255,
      (byte)255,
      (byte)255,
      (byte)255,
      (byte)255,
      (byte)233,
      (byte)173,
      0,
      0,
  };

}