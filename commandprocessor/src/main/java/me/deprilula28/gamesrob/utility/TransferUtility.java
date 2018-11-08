package me.deprilula28.gamesrob.utility;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.function.Consumer;

@AllArgsConstructor
public class TransferUtility extends Thread {
    public static final int UPDATE_MESSAGE_PERIOD = 4;

    public static TransferUtility download(URLConnection from, OutputStream to, Consumer<TransferUtility> step,
                                      Consumer<TransferUtility> callback, Consumer<Exception> errorCallback) throws Exception {
        return new TransferUtility(from.getInputStream(), to, from.getContentLength(), step, callback, errorCallback,
                0, System.currentTimeMillis());
    }

    public static TransferUtility download(InputStream from, OutputStream to, long contentSize, Consumer<TransferUtility> step,
                                      Consumer<TransferUtility> callback, Consumer<Exception> errorCallback) throws Exception {
        return new TransferUtility(from, to, contentSize, step, callback, errorCallback, 0, System.currentTimeMillis());
    }

    private InputStream input;
    private OutputStream output;
    @Getter private long contentSize;

    private Consumer<TransferUtility> step;
    private Consumer<TransferUtility> callback;
    private Consumer<Exception> errorCallback;

    @Getter private long downloaded;
    private long lastUpdate;

    {
        setName("Data Transferer Thread");
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[2048];

            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                downloaded += read;

                if (System.currentTimeMillis() - lastUpdate >= UPDATE_MESSAGE_PERIOD * 1000) {
                    lastUpdate = System.currentTimeMillis();
                    step.accept(this);
                }
            }
            callback.accept(this);
        } catch (Exception e) {
            errorCallback.accept(e);
        }
    }
}
