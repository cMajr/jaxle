package jaxle;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

class DeadlineInputStream extends FilterInputStream {
    private final Socket socket;
    private final long deadline;

    DeadlineInputStream(Socket socket, long timeoutMs) throws IOException {
        super(socket.getInputStream());
        this.socket = socket;
        this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    }

    private void applyDeadline() throws IOException {
        long now = System.nanoTime();
        long remainingNanos = deadline - now;

        if (remainingNanos <= 0) {
            throw new SocketTimeoutException("request timed out");
        }
        
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
        socket.setSoTimeout(Math.max(1, (int) remainingMillis));
    }

    @Override
    public int read() throws IOException {
        applyDeadline();
        return super.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        applyDeadline();
        return super.read(b, off, len);
    }
}
