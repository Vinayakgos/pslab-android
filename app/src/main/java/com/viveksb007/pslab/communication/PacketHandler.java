package com.viveksb007.pslab.communication;

import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Created by viveksb007 on 28/3/17.
 */

public class PacketHandler {

    private static final String TAG = "PacketHandler";
    private final int BUFSIZE = 2000;
    private byte[] buffer = new byte[BUFSIZE];
    private boolean loadBurst, connected;
    int inputQueueSize = 0, BAUD = 1000000;
    private CommunicationHandler mCommunicationHandler = null;
    String version = "", expectedVersion = "CS";
    private CommandsProto mCommandsProto;
    private int timeout = 500, VERSION_STRING_LENGTH = 2;
    ByteBuffer burstBuffer = ByteBuffer.allocate(2000);

    public PacketHandler(int timeout, UsbManager usbManager) {
        this.loadBurst = false;
        this.connected = false;
        this.timeout = timeout;
        this.mCommandsProto = new CommandsProto();
        this.mCommunicationHandler = new CommunicationHandler(usbManager);
        connected = mCommunicationHandler.isConnected();
    }

    public String getVersion() throws IOException {
        try {
            sendByte(mCommandsProto.COMMON);
            sendByte(mCommandsProto.GET_VERSION);
            // Read "<PSLAB Version String>\n"
            mCommunicationHandler.read(buffer, VERSION_STRING_LENGTH + 1, timeout);
            version = new String(Arrays.copyOfRange(buffer, 0, VERSION_STRING_LENGTH), Charset.forName("UTF-8"));
        } catch (IOException e) {
            Log.e("Error in Communication", e.toString());
        }
        return version;
    }

    private void sendByte(int val) throws IOException {
        if (!connected) {
            throw new IOException("Device not connected");
        }
        if (!loadBurst) {
            try {
                mCommunicationHandler.write(new byte[]{(byte) (val & 0xff)}, timeout);
            } catch (IOException e) {
                Log.e("Error in sending byte", e.toString());
                e.printStackTrace();
            }
        } else {
            burstBuffer.put((byte) (val & 0xff));
        }
    }

    private void sendInt(int val) throws IOException {
        if (!connected) {
            throw new IOException("Device not connected");
        }
        if (!loadBurst) {
            try {
                mCommunicationHandler.write(new byte[]{(byte) (val & 0xff), (byte) ((val >> 8) & 0xff)}, timeout);
            } catch (IOException e) {
                Log.e("Error in sending int", e.toString());
                e.printStackTrace();
            }
        } else {
            burstBuffer.put(new byte[]{(byte) (val & 0xff), (byte) ((val >> 8) & 0xff)});
        }
    }

    private int getAcknowledgement() {
        /*
        fetches the response byte
        1 SUCCESS
        2 ARGUMENT_ERROR
        3 FAILED
        used as a handshake
        */
        if (loadBurst) {
            inputQueueSize++;
            return 1;
        } else {
            try {
                mCommunicationHandler.read(buffer, 1, timeout);
                return buffer[0];
            } catch (IOException e) {
                e.printStackTrace();
                return 3;
            }
        }
    }

    private byte getByte() {
        try {
            int numByteRead = mCommunicationHandler.read(buffer, 1, timeout);
            if (numByteRead == 1) {
                return buffer[0];
            } else {
                Log.e(TAG, "Error in reading byte");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private int getInt() {
        try {
            int numByteRead = mCommunicationHandler.read(buffer, 2, timeout);
            if (numByteRead == 2) {
                // Assuming MSB is read first
                return ((buffer[0] << 8) & 0xff00) | ((buffer[1] << 8) & 0xff);
            } else {
                Log.e(TAG, "Error in reading byte");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private long getLong() {
        try {
            int numByteRead = mCommunicationHandler.read(buffer, 4, timeout);
            if (numByteRead == 4) {
                // C++ has long of 4-bytes but in Java int has 4-bytes
                return ByteBuffer.wrap(Arrays.copyOfRange(buffer, 0, 4)).getInt();
            } else {
                Log.e(TAG, "Error in reading byte");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private boolean waitForData() {
        return false;
    }

    private byte[] sendBurst() {
        try {
            mCommunicationHandler.write(burstBuffer.array(), timeout);
            burstBuffer.clear();
            loadBurst = false;
            int bytesRead = mCommunicationHandler.read(buffer, inputQueueSize, timeout);
            inputQueueSize = 0;
            return Arrays.copyOfRange(buffer, 0, bytesRead);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[]{-1};
    }

}
