package de.urr4.rp.roleplayer.application;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WavFileWriterTest {

    @Test
    void convertsDiscordBigEndianPcmToLittleEndianWavData() {
        // JDA's CombinedAudio/UserAudio#getAudioData(double) returns 16-bit
        // signed BIG_ENDIAN PCM samples (see JDA javadoc). Two sample values,
        // written big-endian, as raw Discord audio would arrive:
        short sampleOne = 0x1234;
        short sampleTwo = (short) 0xABCD;
        byte[] bigEndianPcm = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                .putShort(sampleOne).putShort(sampleTwo).array();

        byte[] wavBytes = WavFileWriter.pcm16Stereo48kHz(bigEndianPcm);

        // WAV's canonical PCM format requires little-endian samples in the
        // data chunk (after the fixed 44-byte header) - verify the bytes were
        // swapped, not passed through as-is.
        byte[] expectedLittleEndianData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(sampleOne).putShort(sampleTwo).array();
        byte[] actualData = new byte[4];
        System.arraycopy(wavBytes, 44, actualData, 0, 4);
        assertArrayEquals(expectedLittleEndianData, actualData);
    }

    @Test
    void writesCanonicalWavHeaderForStereo48kHz16Bit() {
        byte[] wavBytes = WavFileWriter.pcm16Stereo48kHz(new byte[]{1, 2, 3, 4});

        ByteBuffer header = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wavBytes, 0, 4));
        assertEquals("WAVE", new String(wavBytes, 8, 4));
        assertEquals(1, header.getShort(20)); // PCM format code
        assertEquals(2, header.getShort(22)); // channels
        assertEquals(48_000, header.getInt(24)); // sample rate
        assertEquals(16, header.getShort(34)); // bits per sample
        assertEquals(4, header.getInt(40)); // data size
    }

    @Test
    void handlesEmptyAndNullInput() {
        assertEquals(44, WavFileWriter.pcm16Stereo48kHz(new byte[0]).length);
        assertEquals(44, WavFileWriter.pcm16Stereo48kHz(null).length);
    }

    @Test
    void dropsTrailingOddByteRatherThanCorruptingLastSample() {
        byte[] wavBytes = WavFileWriter.pcm16Stereo48kHz(new byte[]{1, 2, 3});

        assertEquals(46, wavBytes.length); // 44-byte header + 2 usable bytes
    }
}
