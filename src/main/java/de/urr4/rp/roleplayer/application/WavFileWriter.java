package de.urr4.rp.roleplayer.application;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavFileWriter {

    private static final short CHANNELS = 2;
    private static final int SAMPLE_RATE = 48_000;
    private static final short BITS_PER_SAMPLE = 16;
    private static final int HEADER_SIZE = 44;

    private WavFileWriter() {
    }

    public static byte[] pcm16Stereo48kHz(byte[] pcmBytes) {
        byte[] audio = pcmBytes == null ? new byte[0] : pcmBytes;
        int dataSize = audio.length;
        int byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8);
        short blockAlign = (short) (CHANNELS * (BITS_PER_SAMPLE / 8));

        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(36 + dataSize);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort(CHANNELS);
        header.putInt(SAMPLE_RATE);
        header.putInt(byteRate);
        header.putShort(blockAlign);
        header.putShort(BITS_PER_SAMPLE);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(dataSize);

        byte[] wavBytes = new byte[HEADER_SIZE + dataSize];
        System.arraycopy(header.array(), 0, wavBytes, 0, HEADER_SIZE);
        System.arraycopy(audio, 0, wavBytes, HEADER_SIZE, dataSize);
        return wavBytes;
    }
}
