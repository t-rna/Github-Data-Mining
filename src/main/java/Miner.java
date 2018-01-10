import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.kohsuke.github.*;

import java.io.*;
import java.net.UnknownHostException;
import java.util.*;

/**
 * @author Kevin Ng
 *
 * DESCRIPTION
 *      Retrieves Github repository and user data from the REST APIv3 using the Github-API by Kohsuke Kawaguchi.
 *      Reference: https://github-api.kohsuke.org/
 *
 *
 * COMMENTS
 * 1a.  In some cases, "org.kohsuke.github.HttpException" is wrapped as an "java.lang.Error" before being thrown.
 *      Reason: Java iterators cannot throw checked exceptions (NoSuchElementException is an unchecked exception).
 *      References: https://github.com/kohsuke/github-api/issues/65
 *
 * 1b.  If you encounter HttpExceptions returning response code -1 due to Read Timeouts, increase the timeout value
 *      that is being supplied to the CustomHttpConnector.
 *
 * 2.   <GHRepository>.getOwner() returns an incomplete or fully populated User based on whether or not <Github>.isOffline() returns true
 *
 * 3.   Server Errors (Http Status Code 500 & 502) were, for the most part, resolved by restarting the program in
 *      early iterations which did not have robust error handling. The Operation and OperationHelper classes are
 *      designed to let you customize behaviour of the library calls which can throw these (and other) errors/exceptions.
 *
 *
 * OUTSTANDING ISSUES
 * 1.   Rarely, after the wait period expires (ie. rate limit has reset), the library appears to be miscalculateing
 *      the next rate-limit-reset time. Program ends up sleeping for another hour. Have not been able to figure out
 *      what exactly is causing this issue...
 *      Current Solution: Restart program manually if observed, or suffer the extra hour of wait time...
 *
 * 2.   Users are uniquely identified by both the id and login name pair. If a user deletes and remakes their account
 *      or if they change their login name, then the aforementioned pairing is changed. There are such cases within
 *      Dataset3 and DiscoveredUsersSet (ie. a user's login name appears twice). A fix/workaround is being examined...
 */
public final class Miner {

    private static final Miner INSTANCE = new Miner();
    private static final int MAX_RETRIES = 3;

    private String myToken;
    private int since;

    private MapSet<Integer, String> discoveredUsers = new MapSet<>();

    private final File file1 = new File("data/Dataset1.txt");
    private final File file2 = new File("data/Dataset2.txt");
    private final File file3 = new File("data/Dataset3.txt");
    private final File log = new File("data/ErrorLog.txt");
    private final File dUserSet = new File("data/DiscoveredUsersSet.txt");

    private PropertiesConfiguration config = null;

    private Miner() {
        fileCheck();
        loadConfigurations();
        loadUserSet();
    }

    /**
     * Returns the singleton instance of Miner.
     *
     * @return  Singleton instance of Miner
     */
    public static Miner getInstance() {
        return INSTANCE;
    }

    /**
     * Tells the Miner to begin execution.
     */
    public void run() {
        try {
            doMining();

            // Unforeseen problems are caught here...
        } catch (Exception e) {
            if (e.getCause() instanceof UnknownHostException)
                System.out.println("Unable to create Github connection. Please check network connectivity.");
            else {
                e.printStackTrace();
                System.out.println("Exception caught in run()");
            }
        } catch (Error err) {
            System.out.println("Error caught in run()");
            err.printStackTrace();
        }
    }

    /**
     * The core program's data retrieval logic.
     *
     * @throws IOException          Any unhandled IOExceptions
     * @throws InterruptedException Current thread is interrupted
     */
    private void doMining() throws IOException, InterruptedException {

        GitHub github = createGithub();

        System.out.println("****************************************************************************************");
        System.out.println("Start:\t" + github.rateLimit());
        System.out.println("****************************************************************************************");
        //int rateStart = github.rateLimit().remaining;   // DEBUG
        //int skippedForks = 0;

        String sinceStr = Integer.toString(since);
        Iterator<GHRepository> repoIter = github.listAllPublicRepositories(sinceStr).iterator();

        while ( repoIter.hasNext() ) {
        //for (int i = 0; i < 200; i++) {    // DEBUG

            GHRepository repo = repoIter.next();
            since = repo.getId();

            // Ignore repository if Fork since Forked repositories are (server-side) clones of existing repositories.
            if (!repo.isFork()) {

                try {
                    /*
                     * Access full repository information at the cost of an additional API call.
                     * Contains detailed information including programming language, creation date, etc...
                     */
                    GHRepository repoDetails = OperationHelper.doWithRetry(MAX_RETRIES, new Operation<GHRepository>() {
                        @Override
                        public GHRepository executeWithResult() throws IOException {
                            return github.getRepository(repo.getFullName());
                        }
                        @Override
                        public Status handleIOException(IOException e) {
                            logError(since + ": " + e.getMessage());
                            if (e.getMessage().contains("Repository access blocked")) {
                                return Status.HANDLED_NO_RETRY;     // don't retry; repoDetails is null, all the code below is skipped
                            } else return Status.RETRY;
                        }
                        @Override
                        public Status handleIteratorError(Error err) {/* this won't throw an error. */ return null; }
                    });

                    if (repoDetails != null) {

                        //--- Create the string entry for dataset1 ---//
                        StringBuilder ds1 = new StringBuilder();
                        ds1.append(since);
                        ds1.append(":");

                        /*
                         * Append repository owner as first entry of the contributor's list since contributor list may be empty.
                         * Reference:   https://help.github.com/articles/why-are-my-contributions-not-showing-up-on-my-profile/
                         * todo Can potentionally skip this (-1 api calls) if you check repoDetails.getOwnerName() against the user set
                         */
                        GHUser owner = OperationHelper.doWithRetry(MAX_RETRIES, new Operation<GHUser>() {
                            @Override
                            public GHUser executeWithResult() throws IOException {
                                return repoDetails.getOwner();
                            }
                            @Override
                            public Status handleIOException(IOException e) {
                                logError(since + ": " + e.getMessage());
                                return Status.RETRY;
                            }
                            @Override
                            public Status handleIteratorError(Error err) { /* this won't throw an error. */ return null; }
                        });

                        int ownerId = -1;   // outside of IF scope since FOR (below) requires an initialized reference-able variable.
                        if (owner != null) {
                            ownerId = owner.getId();
                            ds1.append(' ');
                            ds1.append(ownerId);

                            if (github.isOffline())
                                // an incompletely populated User object (Reason: Same check is performed by <GHRepository>.getOwner()...)
                                // "false" will treat owner as a Contributor User (will make another api call to get populated User object)
                                processUser(github, owner, false);
                            else
                                // an owner User is already returned fully populated. "true" means processUser() won't make another api call.
                                processUser(github, owner, true);
                        }

                        // Note: If the iterator fails, an ERROR will be thrown
                        // Note: Contributor objects are not fully populated User objects
                        Iterator<GHRepository.Contributor> contribIter = repoDetails.listContributors().iterator();

                        Boolean moreContribs = true;
                        while (moreContribs != null && moreContribs) {
                            moreContribs = OperationHelper.doWithRetry(MAX_RETRIES, new Operation<Boolean>() {
                                @Override
                                public Boolean executeWithResult() throws IOException {
                                    return contribIter.hasNext();
                                }
                                @Override
                                public Status handleIOException(IOException e) { /* this won't throw an exception. */ return null; }
                                @Override
                                public Status handleIteratorError(Error err) {
                                    logError(since + ": " + err.getMessage());
                                    if (err.getMessage().contains("The history or contributor list is too large to list contributors for this repository via the API"))
                                        return Status.HANDLED_NO_RETRY;
                                    else return Status.RETRY;
                                }
                            });

                            if (moreContribs != null && moreContribs) {
                                GHUser contribUser = contribIter.next();
                                int contribId = contribUser.getId();

                                // Include the contributor user if they aren't the repo owner (owner included above)
                                if (contribId != ownerId) {
                                    try {
                                        // This requires its own try-catch block since we don't want a single processUser() failure
                                        // to cause other all other subsequent users to not be processed. (ie. the exception would
                                        // be thrown up to the next highest try-catch block which will skip the repo entirely)
                                        processUser(github, contribUser, false);
                                        ds1.append(' ');
                                        ds1.append(contribId);
                                    } catch (IOException | RetriesExceededException e) {
                                        logError(since + ": " + e.getMessage() + ": Retrieving user " + contribId + " failed.");
                                    }
                                }
                            }
                        }
                        ds1.append('\n');
                        //--- end of making dataset1 string ---//

                        //--- Create the string entry for dataset2 ---//
                        StringBuilder ds2 = new StringBuilder();
                        ds2.append(since);
                        ds2.append(": \"");
                        ds2.append(repoDetails.getFullName());
                        ds2.append("\", \"");
                        ds2.append(repoDetails.getCreatedAt().toString());
                        ds2.append("\", \"");

                        String details = repoDetails.getDescription();
                        if (details != null)
                            // Strip special characters from description...
                            details = details.replaceAll("\\n|\\r|\\r\\n", "");

                        ds2.append(details);
                        ds2.append("\", \"");
                        ds2.append(repoDetails.getLanguage());
                        ds2.append("\", ");
                        ds2.append(repoDetails.getStargazersCount());
                        ds2.append(", ");
                        ds2.append(repoDetails.getWatchers());
                        ds2.append(", ");
                        ds2.append(repoDetails.getForks());
                        ds2.append('\n');
                        //--- end of making dataset2 string ---//

                        System.out.print("(" + github.rateLimit().remaining + ") " + ds1.toString());
                        FileUtils.writeStringToFile(file1, ds1.toString(), "utf-8", true);
                        FileUtils.writeStringToFile(file2, ds2.toString(), "utf-8", true);
                    }
                    saveSince();

                } catch (Throwable e) {
                    since = repo.getId();   // update 'since' value even if repo retrieval fails, then skip it

                    if (e instanceof RetriesExceededException) {
                        if (e.getCause().getMessage().contains("Not Found") || e.getCause().getMessage().contains("Server Error")) {
                            // 404, 500, 502 errors not resolvable with retrying are skipped after limit is reached...
                            logError(since + ": " + e.getMessage() + ": Could not resolve problem. SKIPPED.");
                            saveSince();

                            // todo if RetriesExceeded caused by Http -1 from library, terminate program because most probably connection issue.
                        }
                    } else {
                        logError(since + ": " + e.getMessage() + ": PROGRAM TERMINATED. Please Debug.");
                        e.printStackTrace();
                        System.exit(-5);
                        // don't save the offending repository's id.
                    }
                }

            } else {
                // If repo is a Fork, update 'since' value, but skip it...
                saveSince();
                //skippedForks++; // DEBUG
            }
        }

        //int rateEnd = github.rateLimit().remaining; // DEBUG
        System.out.println("****************************************************************************************");
        System.out.println("End:\t" + github.rateLimit());
        System.out.println("Since:\t" + since);
        //System.out.println("Calls:\t" + (rateStart - rateEnd));
        //System.out.println("Forks:\t" + skippedForks);
        System.out.println("****************************************************************************************");
    }

    /**
     * Helper method constructors the Github object; the entry point to the API.
     *
     * @return                  Github object
     * @throws IOException      If creating the Github object fails
     */
    private GitHub createGithub() throws IOException {

        HttpConnector conn = new CustomHttpConnector(90000);
        CustomRateLimitHandler rate = new CustomRateLimitHandler();

        return new GitHubBuilder()
                .withConnector(conn)
                .withOAuthToken(myToken)
                .withAbuseLimitHandler(AbuseLimitHandler.WAIT)
                .withRateLimitHandler(rate)
                .build();
    }

    /**
     * Helper method checks to see if the referenced User object exists in the set.
     * Then gets detailed user information, constructs, and saves the line entry
     * for Dataset3. A newly discovered User is saved to the DiscoveredUsersSet.
     *
     * @param github        The current Github object instance
     * @param ref           Reference to the GHUser object to be processed
     * @param isOwner       Whether or not the GHUser reference is an owner of current repository
     * @throws Throwable    Will either be an IOException or HttpException but due to how Operation is implemented,
     *                      this must be declared as Throwable.
     */
    private void processUser(GitHub github, GHUser ref, boolean isOwner) throws Throwable {
        // Should only retrieve a User for processing if they haven't been seen before (ie. not in the discovered users set)
        if ( discoveredUsers.put(ref.getId(), ref.getLogin()) ) {
            GHUser user;

            if (isOwner)
                user = ref;
            else {
                String login = ref.getLogin();

                user = OperationHelper.doWithRetry(MAX_RETRIES, new Operation<GHUser>() {
                    @Override
                    public GHUser executeWithResult() throws IOException {
                        return github.getUser(login);
                    }
                    @Override
                    public Status handleIOException(IOException e) {
                        logError(since + ": " + e.getMessage());
                        return Status.RETRY;
                    }
                    @Override
                    public Status handleIteratorError(Error err) {/* this won't throw an error. */ return null; }
                });
            }

            // User should be null if above fails, so skip if user couldn't be retrieved...
            if (user != null) {

                // Save newly discovered user to file. Note: Set file always needs to be loaded on program start...
                String newUser = user.getId() + "," + user.getLogin() + '\n';
                FileUtils.writeStringToFile(dUserSet, newUser, "utf-8", true);

                //--- Create and save the string entry for dataset3... ---//
                StringBuilder ds3 = new StringBuilder();
                ds3.append(user.getId());
                ds3.append(": \"");
                ds3.append(user.getLogin());
                ds3.append("\", \"");

                // Need to strip special characters from Location since users can apparently enter a custom string for it...
                // See offending example: https://api.github.com/users/tomvangoethem
                String location = user.getLocation();
                if (location != null)
                    location = location.replaceAll("\\n|\\r|\\r\\n", "");

                ds3.append(location);
                ds3.append("\", ");
                ds3.append(user.getFollowersCount());
                ds3.append(", ");
                ds3.append(user.getFollowingCount());
                ds3.append('\n');
                FileUtils.writeStringToFile(file3, ds3.toString(), "utf-8", true);
            }
        }
        // Else -> no User data processed; the data already exists
    }

    /**
     * Helper method creates a new PropertiesConfiguration from the specified properties file.
     * Loads the 'since' value and the oauth token for this application.
     */
    private void loadConfigurations() {
        try {
            // create a new PropertiesConfiguration object...
            config = new PropertiesConfiguration("config.properties");

            // set 'since' value
            since = config.getInt("since");

            // set authentication token
            myToken = config.getString("token");

            if (myToken == null || myToken.equals(""))
                throw new ConfigurationException("No authentication token found.");

        } catch (ConfigurationException e) {
            System.out.println("Error Loading config.properties: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Helper method saves the 'since' value to the program's configuration file.
     */
    private void saveSince() {
        try {
            if (config != null) {
                config.setProperty("since", since);
                config.save();
            } else
                throw new ConfigurationException("config reference is null");
        } catch (ConfigurationException e) {
            System.out.println("Error Saving config.properties: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Helper method loads the set of discovered users from file into a Map.
     */
    private void loadUserSet() {
        int count = 0;
        LineIterator iter = null;

        try {
            iter = FileUtils.lineIterator(dUserSet, "utf-8");

            while (iter.hasNext()) {
                String line = iter.nextLine();
                String[] lineKV = line.split(",");

                if (!discoveredUsers.put(Integer.parseInt(lineKV[0]), lineKV[1]))
                    throw new IOException("MapSet.put returned False on a new insertion set..."); // cautionary code. This shouldn't ever trigger.
                count ++;
            }
        } catch (IOException e) {
            System.out.println("Error Loading DiscoveredUsersSet");
            e.printStackTrace();
            System.exit(-1);
        } finally {
            if (iter != null)
                iter.close();
            System.out.println("Loaded " + count + " Users into Discovered Set...");
        }
    }

    /**
     * Helper method checks if all File dependencies are working. If the specified file
     * does not exist, it will attempt to create one.
     */
    private void fileCheck() {
        // all file dependencies should be present here
        File[] files = {file1, file2, file3, log, dUserSet};

        for (File f : files) {
            if (f.exists()) {
                if (f.canRead() && f.canWrite())
                    System.out.println(f.getName() + ": File check OKAY");
                else {
                    // Only relevant for Linux systems?
                    System.out.println(f.getName() + ": Please check read/write permissions");
                    System.exit(-1);
                }
            } else {
                System.out.print(f.getName() + ": File NOT FOUND... creating a new file... ");
                try {
                    if ( f.createNewFile() )
                        System.out.print("done!\n");
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * Helper method prints the provided error message to standard output and
     * writes the error message to the error log file.
     *
     * @param errorMessage    String of the error message
     */
    private void logError(String errorMessage) {
        System.out.print(errorMessage + '\n');

        try {
            FileUtils.writeStringToFile(log, errorMessage + '\n', "utf-8", true);
        } catch (IOException e) {
            System.out.println("Could not write to Log.");
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /*
        Main method
    */
    public static void main(String[] args) {
        Miner.getInstance().run();
    }
}
