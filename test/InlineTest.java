import lombok.Inline;
import lombok.core.PrintAST;

@PrintAST
public class InlineTest {
	static int inlineMethod() {
		System.out.println("Hello from inlineMethod ");
		new Exception().printStackTrace(System.out);
		return 2;
	}

	public static void main(String[] args) {
		System.out.println("standard call");
		int v0 = inlineMethod();
		System.out.printf("v0 = %d\n", v0);

		System.out.println("Before inline call");
		@Inline
		int v = inlineMethod();
		System.out.printf("After inline call. v=%d\n", v);
	}
}

/*
Expected output:
$ javac InlineTest.java -cp ../dist/lombok.jar
$ java InlineTest
standard call
Hello from inlineMethod 
java.lang.Exception
	at InlineTest.inlineMethod(InlineTest.java:8)
	at InlineTest.main(InlineTest.java:14)
v0 = 2
Before inline call
Hello from inlineMethod 
java.lang.Exception
	at InlineTest.main(InlineTest.java:8)
After inline call. v=2
*/
