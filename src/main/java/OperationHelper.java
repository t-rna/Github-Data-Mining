import org.kohsuke.github.HttpException;

import java.io.FileNotFoundException;

/**
 * @author Kevin Ng
 *
 * A try-catch-retry implementation based on the solution found at:
 *      https://stackoverflow.com/a/13240586
 */
public class OperationHelper {

    private static int baseWait = 1000;     // wait in milliseconds

    public static <T> T doWithRetry(int maxAttempts, Operation<T> operation) throws Throwable {

        int attempt = 0;

        while (true) {
            try {
                return operation.executeWithResult();
            } catch (Throwable cause) {

                // the above operation keeps throwing exceptions and exceeds the retry limit
                if (attempt == maxAttempts)
                    throw new RetriesExceededException(cause);

                if (operation.handleException(cause) == Operation.Status.RETHROW)
                    throw cause;

                if (operation.handleException(cause) == Operation.Status.HANDLED_NO_RETRY)
                    break;  // you've handled the exception in your overriding method and don't want to retry the operation

                else {
                    attempt ++;
                    try {
                        int waitTime = baseWait * (attempt * attempt);  // scaling delay duration
                        System.out.println("Waiting " + waitTime/1000 + " seconds before retrying...");
                        Thread.sleep(waitTime);
                    } catch (InterruptedException i) {
                        // If this thread is interrupted for some reason, terminate the program
                        i.printStackTrace();
                        System.exit(-1);
                    }
                }
            }
        }

        return null;    // null is returned on a HANDLED_NO_RETRY
    }
}
