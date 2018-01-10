public class RetriesExceededException extends Exception {

    public RetriesExceededException(Throwable cause) {
        super("Operation has exceeded maximum number of retries.", cause);
    }
}
