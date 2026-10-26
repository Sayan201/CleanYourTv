from pathlib import Path

path = Path("wireless-debug-src/wirelessdebug/src/main/java/com/wirelessdebug/service/AdbClient.java")
source = path.read_text(encoding="utf-8")
old = """                } else if (p.arg0 == AUTH_RSAPUBLICKEY) {
                    throw new IllegalStateException("adb key not authorized on device");
                }"""
new = """                } else if (p.arg0 == AUTH_RSAPUBLICKEY) {
                    // The TV has rejected the signature and is asking for the host public key.
                    // Sending it causes adbd to display its normal authorization prompt.
                    writePacket("AUTH", AUTH_RSAPUBLICKEY, 0, adbPublicKeyPayload(key));
                }"""
if old not in source:
    raise SystemExit("Expected AUTH_RSAPUBLICKEY branch not found; refusing to patch.")
source = source.replace(old, new, 1)
marker = """    /** 构造 KeyManager：直接用 PEM key + 证书，忽略服务器 CA 列表过滤，强制发送客户端证书。 */"""
method = r'''    /** Serialize the RSA public key in the legacy ADB wire format and request device authorization. */
    private static byte[] adbPublicKeyPayload(PrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof java.security.interfaces.RSAPrivateCrtKey)) {
            throw new IllegalArgumentException("ADB authorization requires an RSA CRT private key");
        }
        java.security.interfaces.RSAPrivateCrtKey rsa =
                (java.security.interfaces.RSAPrivateCrtKey) privateKey;
        java.math.BigInteger n = rsa.getModulus();
        java.math.BigInteger exponent = rsa.getPublicExponent();
        int words = (n.bitLength() + 31) / 32;
        byte[] modulus = toLittleEndian(n, words * 4);
        java.math.BigInteger radix = java.math.BigInteger.ONE.shiftLeft(words * 32);
        java.math.BigInteger rr = radix.mod(n).multiply(radix.mod(n)).mod(n);
        byte[] rrBytes = toLittleEndian(rr, words * 4);
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 + words * 4 + words * 4 + 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.put(modulus);
        b.put(rrBytes);
        b.putInt(exponent.intValue());
        String encoded = android.util.Base64.encodeToString(b.array(), android.util.Base64.NO_WRAP);
        byte[] suffix = " CleanYourTV@Android\0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] text = encoded.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] payload = new byte[text.length + suffix.length];
        System.arraycopy(text, 0, payload, 0, text.length);
        System.arraycopy(suffix, 0, payload, text.length, suffix.length);
        return payload;
    }

    private static byte[] toLittleEndian(java.math.BigInteger value, int length) {
        byte[] big = value.toByteArray();
        int start = big.length > 1 && big[0] == 0 ? 1 : 0;
        int count = big.length - start;
        if (count > length) throw new IllegalArgumentException("RSA integer exceeds key size");
        byte[] little = new byte[length];
        for (int i = 0; i < count; i++) little[i] = big[big.length - 1 - i];
        return little;
    }

'''
if marker not in source:
    raise SystemExit("Insertion marker not found; refusing to patch.")
source = source.replace(marker, method + marker, 1)
path.write_text(source, encoding="utf-8")
print("Patched legacy ADB public-key authorization flow.")
