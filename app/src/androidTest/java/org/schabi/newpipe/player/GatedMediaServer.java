package org.schabi.newpipe.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A loopback media response gate: the real player buffers until the test releases the bytes. */
final class GatedMediaServer implements AutoCloseable {
    private final byte[] media;
    private final ServerSocket server;
    private final ExecutorService requests = Executors.newCachedThreadPool();
    private final CountDownLatch requested = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    GatedMediaServer(final byte[] media) throws IOException {
        this.media = media;
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        requests.execute(() -> {
            while (!server.isClosed()) {
                try {
                    final Socket socket = server.accept();
                    requests.execute(() -> respond(socket));
                } catch (final IOException closed) {
                    return;
                }
            }
        });
    }

    String url() {
        return "http://127.0.0.1:" + server.getLocalPort() + "/fixture.mp4";
    }

    boolean awaitRequest() throws InterruptedException {
        return requested.await(10, TimeUnit.SECONDS);
    }

    void release() {
        released.countDown();
    }

    private void respond(final Socket socket) {
        try (Socket connection = socket) {
            connection.setSoTimeout(10000);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.US_ASCII));
            reader.readLine();
            int first = 0;
            int last = media.length - 1;
            boolean range = false;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Range: bytes=", 0, 13)) {
                    final String[] ends = line.substring(13).trim().split("-", -1);
                    first = Integer.parseInt(ends[0]);
                    if (ends.length > 1 && !ends[1].isEmpty()) {
                        last = Math.min(last, Integer.parseInt(ends[1]));
                    }
                    range = true;
                }
            }
            requested.countDown();
            released.await();
            final OutputStream output = connection.getOutputStream();
            final String headers = "HTTP/1.1 " + (range ? "206 Partial Content" : "200 OK")
                    + "\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\n"
                    + "Content-Length: " + (last - first + 1) + "\r\n"
                    + (range ? "Content-Range: bytes " + first + "-" + last + "/"
                    + media.length + "\r\n" : "") + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(media, first, last - first + 1);
        } catch (final IOException ignored) {
            // Mode changes cancel old loads; a disconnected request is expected.
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        release();
        server.close();
        requests.shutdownNow();
    }
}
