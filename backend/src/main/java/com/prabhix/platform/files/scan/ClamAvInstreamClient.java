package com.prabhix.platform.files.scan;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Minimal clamd client for the {@code zINSTREAM} command.
 *
 * <p>Chunks are length-prefixed (big-endian uint32) and the stream is terminated with a
 * zero-length chunk. The daemon responds with {@code stream: OK} or {@code stream: … FOUND}.
 */
final class ClamAvInstreamClient {

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private ClamAvInstreamClient() {
    }

    static String scan(byte[] content,
                       String host,
                       int port,
                       int connectTimeoutMillis,
                       int scanTimeoutMillis,
                       int chunkSize) throws IOException {
        int effectiveChunk = Math.max(1024, chunkSize);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(scanTimeoutMillis);

            OutputStream out = socket.getOutputStream();
            out.write(INSTREAM_COMMAND);

            int offset = 0;
            while (offset < content.length) {
                int len = Math.min(effectiveChunk, content.length - offset);
                out.write(ByteBuffer.allocate(4).putInt(len).array());
                out.write(content, offset, len);
                offset += len;
            }
            out.write(new byte[4]);
            out.flush();

            return readResponse(socket.getInputStream());
        }
    }

    private static String readResponse(InputStream in) throws IOException {
        byte[] buffer = new byte[4096];
        int read = in.read(buffer);
        if (read <= 0) {
            throw new IOException("ClamAV returned an empty response");
        }
        return new String(buffer, 0, read, StandardCharsets.US_ASCII).trim();
    }
}
