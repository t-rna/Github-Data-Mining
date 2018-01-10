import org.kohsuke.github.HttpException;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Kevin Ng
 *
 * A try-catch-retry implementation based on the solution found at:
 *      https://stackoverflow.com/a/13240586
 *
 * This class is meant to wrap Github API library calls that would throw
 * an IOException on a failed network request. Uses OperationHelper class
 * to attempt retrying the call. Retrying solves most of these network failures...
 */
public abstract class Operation<T> {

    public enum Status {
        RETRY, HANDLED_NO_RETRY, RETHROW
    }

    /**
     * You must implement this by wrapping the Github API library call.
     */
    abstract public T executeWithResult() throws IOException;

    /**
     * We use Throwable here to catch both Exceptions and Errors.
     * Can't override this method. Delegates to a specific method based on what the 'cause' is.
     */
    public final Status handleException(Throwable cause) {
        if (cause instanceof IOException)
            return handleIOException((IOException) cause);
        else if (cause instanceof Error)
            return handleIteratorError((Error) cause);
        else
            return Status.RETHROW;  // some other error we haven't come across, let caller handle it
    }

    /*
        Default handling methods. You must define how these problems are handled.

        Note: FileNotFoundException and HttpException are the predominant IOExceptions encountered thusfar.
              Since they can be treated in the same manner (usually a retry fixes the problem), we can
              catch the superclass IOException rather than have a handler for each specific subclass.
     */
    public abstract Status handleIOException(IOException e);
    public abstract Status handleIteratorError(Error err);
}
