public class HelloWorld {
  public static void main(String[] args) {
      forLoopMethod(5);
      whileLoopMethod(4);
      forLoopMethod(3);
  }

  public static void forLoopMethod(int x) {
      for (int i = 0; i < x; i++) {
          System.out.println("Hello, World from loopMethod(): " + i);
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

