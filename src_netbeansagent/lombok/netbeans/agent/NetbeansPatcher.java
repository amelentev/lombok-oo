package lombok.netbeans.agent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetbeansPatcher {
	private NetbeansPatcher() {}
	
	private static class Patcher implements ClassFileTransformer {
		@Override public byte[] transform(ClassLoader loader, String className,
				Class<?> classBeingRedefined,
				ProtectionDomain protectionDomain, byte[] classfileBuffer)
				throws IllegalClassFormatException {
			
			System.out.println("Classname: " + className);
			if ( NETBEANS_JAVAC_TASK_CLASS_NAME.equals(className) ) {
				try {
					return runTransform("lombok.netbeans.agent.NetbeansJavacTaskTransformer", classfileBuffer);
				} catch ( Throwable t ) {
					System.err.println("Wasn't able to patch netbeans' JavacTaskImpl class:");
					t.printStackTrace();
				}
			}
			
//			if ( ECLIPSE_CUD_CLASS_NAME.equals(className) ) {
//				try {
//					return runTransform("lombok.eclipse.agent.EclipseCUDTransformer", classfileBuffer);
//				} catch ( Throwable t ) {
//					System.err.println("Wasn't able to patch eclipse's CompilationUnitDeclaration class:");
//					t.printStackTrace();
//				}
//			}
			
			return null;
		}
	}
	
	private static byte[] runTransform(String className, byte[] classfileBuffer) throws Exception {
		Class<?> transformerClass = Class.forName(className);
		Constructor<?> constructor = transformerClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		Object instance = constructor.newInstance();
		Method m = transformerClass.getDeclaredMethod("transform", byte[].class);
		m.setAccessible(true);
		return (byte[])m.invoke(instance, classfileBuffer);
	}
	
//	static final String ECLIPSE_CUD_CLASS_NAME = "org/eclipse/jdt/internal/compiler/ast/CompilationUnitDeclaration";
	static final String NETBEANS_JAVAC_TASK_CLASS_NAME = "com/sun/tools/javac/api/JavacTaskImpl";
	
	public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception {
		registerPatcher(instrumentation, true);
		addLombokToSearchPaths(instrumentation);
	}
	
	private static void addLombokToSearchPaths(Instrumentation instrumentation) throws Exception {
		String path = findPathOfOurClassloader();
		instrumentation.appendToSystemClassLoaderSearch(new JarFile(path + "/lombok.jar"));
		instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(path + "/lombok.netbeans.agent.jar"));
		
	}
	
	private static String findPathOfOurClassloader() throws Exception {
		ClassLoader loader = NetbeansPatcher.class.getClassLoader();
		if ( loader == null ) loader = ClassLoader.getSystemClassLoader();
		
		URI uri = loader.getResource(NetbeansPatcher.class.getName().replace('.', '/') + ".class").toURI();
		Pattern p = Pattern.compile("^jar:file:([^\\!]+)\\!.*\\.class$");
		Matcher m = p.matcher(uri.toString());
		if ( !m.matches() ) return ".";
		String rawUri = m.group(1);
		return new File(URLDecoder.decode(rawUri, Charset.defaultCharset().name())).getParent();
	}
	
	public static void premain(String agentArgs, Instrumentation instrumentation) throws Exception {
		registerPatcher(instrumentation, false);
		addLombokToSearchPaths(instrumentation);
	}
	
	private static void registerPatcher(Instrumentation instrumentation, boolean transformExisting) throws IOException {
		instrumentation.addTransformer(new Patcher(), true);
		
		if ( transformExisting ) for ( Class<?> c : instrumentation.getAllLoadedClasses() ) {
			if ( c.getName().equals(NETBEANS_JAVAC_TASK_CLASS_NAME) ) {
				try {
					instrumentation.retransformClasses(c);
				} catch ( UnmodifiableClassException ex ) {
					throw new UnsupportedOperationException(
							"The netbeans JavacTaskImpl class is already loaded and cannot be modified. " +
							"You'll have to restart netbeans in order to use Lombok in netbeans.");
				}
			}
		}
	}
	
}
