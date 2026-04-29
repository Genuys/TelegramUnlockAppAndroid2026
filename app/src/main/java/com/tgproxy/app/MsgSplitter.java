package com.tgproxy.app;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MsgSplitter {

    private final SICBlockCipher cipher;

    public MsgSplitter(byte[] initData) {
        byte[] key = Arrays.copyOfRange(initData, 8, 40);
        byte[] iv = Arrays.copyOfRange(initData, 40, 56);
        cipher = new SICBlockCipher(new AESEngine());
        cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] skip = new byte[64];
        cipher.processBytes(skip, 0, 64, skip, 0);
    }

    public List<byte[]> split(byte[] chunk) {
        byte[] plain = new byte[chunk.length];
        cipher.processBytes(chunk, 0, chunk.length, plain, 0);

        List<Integer> boundaries = new ArrayList<>();
        int pos = 0;
        while (pos < plain.length) {
            int first = plain[pos] & 0xFF;
            int msgLen;
            if (first == 0x7f) {
                if (pos + 4 > plain.length) break;
                int v = (plain[pos + 1] & 0xFF)
                        | ((plain[pos + 2] & 0xFF) << 8)
                        | ((plain[pos + 3] & 0xFF) << 16);
                msgLen = v * 4;
                pos += 4;
            } else {
                msgLen = first * 4;
                pos += 1;
            }
            if (msgLen == 0 || pos + msgLen > plain.length) break;
            pos += msgLen;
            boundaries.add(pos);
        }

        if (boundaries.size() <= 1) {
            List<byte[]> r = new ArrayList<>();
            r.add(chunk);
            return r;
        }

        List<byte[]> parts = new ArrayList<>();
        int prev = 0;
        for (int b : boundaries) {
            parts.add(Arrays.copyOfRange(chunk, prev, b));
            prev = b;
        }
        if (prev < chunk.length) {
            parts.add(Arrays.copyOfRange(chunk, prev, chunk.length));
        }
        return parts;
    }
}
