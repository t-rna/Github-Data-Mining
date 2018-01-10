README
======

The constructed data sets can be found in the /data folder. This program requires Gradle build tool to bring in third party libraries and dependencies. The root directory for spawning the JVM must be the same directory as this README file, as the data set's file paths are defined relative to this. The executing main method resides in "Miner.java".


config.properties
-----------------

This file contains configurations of the program. Place your API authentication token here.


Dataset1
--------

This file contains the list of contributors who have successfully committed changes to the repository. The structure of this data set is as follows:

	[repository id]: [contributor 1] [contributor 2] ... [contributor N]


Dataset2
--------

This file contains the repository's miscellaneous information. The structure of this data set is as follows:

	[repository id]: [full name], [creation date], [description], [programming language], [stargazer count], [watcher count], [fork count]


Dataset3
--------

This file contains detailed user information. The structure of this data set is as follows:

	[user id]: [login name], [location], [followers count], [following count]


DiscoveredUsersSet
------------------

*** Please modify this with caution ***. This file contains the set of users discovered by the program. This file should be identical, in terms of ordering and user id's, to Dataset3. The data of this file is loaded into a map during program start up. Each contributor retrieved is compared against this set of users. If they don't exist, they are added, and an entry in Dataset3 is created for them. If they already exist, further processing is skipped.


ErrorLog
--------

This file contains errors that the program was able to log. It indicates which repository triggered the error and the error message. This should help with debugging.



