import org.kohsuke.github.RateLimitHandler;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class CustomRateLimitHandler extends RateLimitHandler {

    /**
     * Implementation borrowed from Kohsuke's RateLimitHandler.WAIT. Modified to output information to System.out
     * Source: https://github.com/kohsuke/github-api/blob/master/src/main/java/org/kohsuke/github/RateLimitHandler.java
     */
    @Override
    public void onError(IOException e, HttpURLConnection uc) throws IOException {
        try {
            Date now = new Date();
            long waitTime = parseWaitTime(uc);

            long mins = TimeUnit.MILLISECONDS.toMinutes(waitTime);
            if (mins >= 1)
                // display pause time in minutes if more than 60sec left
                System.out.println("\n[" + now.toString() + "] Rate Limit exceeded, pausing for... " + mins + " minutes.\n");
            else {
                // display pause time in seconds if it's less than 60sec left
                long secs = TimeUnit.MILLISECONDS.toSeconds(waitTime);
                System.out.println("\n[" + now.toString() + "] Rate Limit exceeded, pausing for... " + secs + " seconds.\n");
            }

            Thread.sleep(waitTime);

        } catch (InterruptedException _) {
            throw (InterruptedIOException)new InterruptedIOException().initCause(e);
        }
    }

    /**
     * Source: https://github.com/kohsuke/github-api/blob/master/src/main/java/org/kohsuke/github/RateLimitHandler.java
     */
    private long parseWaitTime(HttpURLConnection uc) {
        String v = uc.getHeaderField("X-RateLimit-Reset");
        if (v==null)    return 10000;   // can't tell

        return Math.max(10000, Long.parseLong(v)*1000 - System.currentTimeMillis());
    }
}
