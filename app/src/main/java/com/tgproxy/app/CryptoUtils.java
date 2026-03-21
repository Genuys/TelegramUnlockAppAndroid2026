package com.tgproxy.app;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class CryptoUtils {

    public static byte[] aesCtrKeystream(byte[] key, byte[] iv, int len) {
        SICBlockCipher cipher = new SICBlockCipher(new AESEngine());
        cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] input = new byte[len];
        byte[] output = new byte[len];
        cipher.processBytes(input, 0, len, output, 0);
        return output;
    }

    public static int[] dcFromInit(byte[] data) {
        try {
            byte[] key = Arrays.copyOfRange(data, 8, 40);
            byte[] iv = Arrays.copyOfRange(data, 40, 56);
            byte[] ks = aesCtrKeystream(key, iv, 64);
            byte[] plain = new byte[8];
            for (int i = 0; i < 8; i++) {
                plain[i] = (byte) (data[56 + i] ^ ks[56 + i]);
            }
            ByteBuffer bb = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN);
            int proto = bb.getInt();
            short dcRaw = bb.getShort();
            if (proto == 0xEFEFEFEF || proto == 0xEEEEEEEE || proto == 0xDDDDDDDD) {
                int dc = Math.abs(dcRaw);
                if (dc >= 1 && dc <= 5) {
                    return new int[]{dc, dcRaw < 0 ? 1 : 0};
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static byte[] patchDc(byte[] data, int dc) {
        if (data.length < 64) return data;
        ByteBuffer ndBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        ndBuf.putShort((short) dc);
        byte[] nd = ndBuf.array();
        try {
            byte[] key = Arrays.copyOfRange(data, 8, 40);
            byte[] iv = Arrays.copyOfRange(data, 40, 56);
            byte[] ks = aesCtrKeystream(key, iv, 64);
            byte[] patched = Arrays.copyOf(data, data.length);
            patched[60] = (byte) (ks[60] ^ nd[0]);
            patched[61] = (byte) (ks[61] ^ nd[1]);
            return patched;
        } catch (Exception e) {
            return data;
        }
    }

    private CryptoUtils() {
    }
}
