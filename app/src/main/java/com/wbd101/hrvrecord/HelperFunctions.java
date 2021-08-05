package com.wbd101.hrvrecord;

public class HelperFunctions {
        /**
     * Convert bytes into hex string.
     */
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        if ((bytes == null) || (bytes.length <= 0)) {
            return "";
        }

        char[] hexChars = new char[bytes.length * 3 - 1];

        for (int j=0; j<bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            if (j < bytes.length - 1) {
                hexChars[j * 3 + 2] = 0x20;           // hard coded space
            }
        }

        return new String(hexChars);
    }
    public static String bytestoformat(byte[] bytes){
        String number="";
        for(int j = 2; j<bytes.length-1; j++){
            int result=0;
            if(j%2!=0 ) continue;
            result = (bytes[j] & 0xff) |
                    ((bytes[j+1] & 0xff) << 8);
            if(j<bytes.length-7) // bytes 2 to 14 are unsigned
                number = number + Integer.toString(result)+' ';
            else{ // bytes 15 onwards carries signed value
                short signed = (short) result;
                number = number +Short.toString(signed)+' ';
            }
        }
        return number;
    }
    public static boolean checksum(byte[] value){
        int checksum;
        String all_values = bytesToHex(value);
        String[] all_values_arr = all_values.split(" ");
        checksum = Integer.parseInt(all_values_arr[1], 16);
        String[] sensor_values = bytestoformat(value).split(" ");
        int xor = 0;
        for (int i = 0; i<all_values_arr.length;i++){
            if(i<2) continue;
            xor ^= Integer.parseInt(all_values_arr[i], 16);
        }
        if(xor == checksum) return true;

        return false;
    }

}
