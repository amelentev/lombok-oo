package lombok.javac.disableCheckedExceptions;

import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class DynamicAgent extends AbstractProcessor {
	@Override public void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		
		try {
			loadYourself(processingEnv.getMessager());
		} catch ( Exception e ) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Could not disable checked exceptions: " + e);
			e.printStackTrace();
		}
	}
	
	@Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		return false;
	}
	
	public static void agentmain(String agentargs, Instrumentation instrumentation) throws Exception {
		instrumentation.addTransformer(new CheckForThrownExceptionTransformer(), true);
		instrumentation.retransformClasses(Class.forName("com.sun.tools.javac.comp.Check"));
	}
	
	public static void loadYourself(Messager messager) throws Exception {
		try {
			Class.forName("sun.instrument.InstrumentationImpl");
		} catch ( ClassNotFoundException e ) {
			messager.printMessage(Kind.WARNING, "checkedExceptionDisabler only works on sun javac 1.6. Sorry :(");
			return;
		}
		LibJVM libjvm = (LibJVM) Native.loadLibrary(LibJVM.class);
		PointerByReference vms = new PointerByReference();
		IntByReference found = new IntByReference();
		libjvm.JNI_GetCreatedJavaVMs(vms, 1, found);
		LibInstrument libinstrument = (LibInstrument)Native.loadLibrary(LibInstrument.class);
		Pointer vm = vms.getValue();
		libinstrument.Agent_OnAttach(vm, findPathOfOurJar(), null);
	}
	
	private static String findPathOfOurJar() throws Exception {
		URI uri = DynamicAgent.class.getResource("/" + DynamicAgent.class.getName().replace('.', '/') + ".class").toURI();
		Pattern p = Pattern.compile("^jar:file:([^\\!]+)\\!.*\\.class$");
		Matcher m = p.matcher(uri.toString());
		if ( !m.matches() ) return ".";
		String rawUri = m.group(1);
		return URLDecoder.decode(rawUri, Charset.defaultCharset().name());
	}
	
	public static interface LibInstrument extends Library {
		void Agent_OnAttach(Pointer vm, String name, Pointer Reserved);
	}
	
	public static interface LibJVM extends Library {
		int JNI_GetCreatedJavaVMs(PointerByReference vms, int  count, IntByReference found);
	}
}
