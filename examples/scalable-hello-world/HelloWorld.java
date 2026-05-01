import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class HelloWorld {
    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Please provide a file path.");
            return;
        }

        String filePath = args[0];

        while (true) {
            try {
                exec(filePath);
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void exec(String filePath) {
        try {
            File file = new File(filePath);
            Scanner scanner = new Scanner(file);

            // Assuming there is only one integer in the file
            while (scanner.hasNext()) {
                if (scanner.hasNextInt()) {
                    int x = scanner.nextInt();
                    System.out.println("Number read from file: " + x);
                    forLoopMethod(x);
                    whileLoopMethod(x);
                    forLoopMethod(x);
                    break; // break after reading the first integer
                } else {
                    scanner.next(); // skip non-integer tokens
                }
            }

            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + filePath);
        }
    }

    public static void forLoopMethod(int x) {
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < x; j++) {
                System.out.println("Hello, World from forLoopMethod(): " + i + " " + j);
            }
        }
    }

    public static void whileLoopMethod(int x) {
        int i = 0;
        while (i < x) {
            System.out.println("Hello, World from whileLoopMethod(): " + i);
            i++;
        }
    }
}
