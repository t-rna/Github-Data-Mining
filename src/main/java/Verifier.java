import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import static java.lang.Thread.sleep;

/**
 * Created by kevin on 6/23/2017.
 */
public class Verifier {

    static File w1 = new File("data/workingset1.txt");
    static File w2 = new File("data/workingset2.txt");
    static File w3 = new File("data/workingset3.txt");
    static File wUS = new File("data/workingUsersSet.txt");
    static File wLog = new File("data/workingLog.txt");
    static File[] work = new File[] {w1, w2, w3, wUS, wLog};

    static File t1 = new File("data/testset1.txt");
    static File t2 = new File("data/testset2.txt");
    static File t3 = new File("data/testset3.txt");
    static File tUS = new File("data/testUsersSet.txt");
    static File tLog = new File("data/testLog.txt");
    static File[] test = new File[] {t1, t2, t3, tUS, tLog};

    public static void lineCompare(File file1, File file2) {

        int lineNumber = 0, matches = 0;
        LineIterator iter1 = null;
        LineIterator iter2 = null;

        try {
            iter1 = FileUtils.lineIterator(file1, "utf-8");
            iter2 = FileUtils.lineIterator(file2, "utf-8");

            System.out.println("Starting comparison of lines between " + file1.getName() + " and " + file2.getName());

            while (iter1.hasNext() && iter2.hasNext()) {
                lineNumber ++;
                String line1 = iter1.nextLine();
                String line2 = iter2.nextLine();

                if (line1.equals(line2)) {
                    //System.out.println("Line  " + lineNumber + "\tmatches");
                    matches ++;
                } else {
                    System.out.println("Line  " + lineNumber + "\tdoes not match ******************************************************");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (iter1 != null)
                iter1.close();
            if (iter2 != null)
                iter2.close();
            System.out.println(lineNumber + " lines compared, " + matches + " lines match");
            System.out.println("\n==============================================================================================================\n");
        }
    }

    public static void verifyFiles() {
        for (int i = 0; i < 5; i++)
            lineCompare(work[i], test[i]);
    }

    public static void clearFiles() {
        for (File f : work) {
            f.delete();
            try {
                if ( f.createNewFile() )
                    System.out.println(f.getName() + " cleared.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (File f : test) {
            f.delete();
            try {
                if ( f.createNewFile() )
                    System.out.println(f.getName() + " cleared.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {

        System.out.print("Run parameter: ");
        Scanner reader = new Scanner(System.in);
        char c = reader.next().trim().charAt(0);

        switch (c) {
            case 'v' : verifyFiles(); break;
            case 'c' : clearFiles(); break;
        }
    }
}
