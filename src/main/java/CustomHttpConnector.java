import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.extras.ImpatientHttpConnector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Kevin Ng
 *
 * An implementation of the interface org.kohsuke.github.HttpConnector
 * that allows us to supply a customized timeout values. We extend
 * ImpatientHttpConnector so as to not reinvent the wheel...
 */
public class CustomHttpConnector extends ImpatientHttpConnector {

    // Define a "base" HttpConnector
    // Source: https://github.com/kohsuke/github-api/blob/master/src/main/java/org/kohsuke/github/HttpConnector.java
    private static final HttpConnector base = new HttpConnector() {
        @Override
        public HttpURLConnection connect(URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }
    };

    // Constructor takes a custom connect timeout and read timeout values.
    public CustomHttpConnector(int connectTimeout, int readTimeout) {
        super(base, connectTimeout, readTimeout);
    }

    // Constructor takes a custom timout value that is the same for both connection and read timouts.
    public CustomHttpConnector(int timeout) {
        super(base, timeout, timeout);
    }
}
