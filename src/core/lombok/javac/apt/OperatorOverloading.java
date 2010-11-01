package lombok.javac.apt;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import lombok.Lombok;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.OOLower;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

public class OperatorOverloading {

	public static void inject(JavacProcessingEnvironment pe) {
		JavaCompiler compiler = JavaCompiler.instance(pe.getContext());
		try {
			Field f = compiler.getClass().getDeclaredField("taskListener");
			f.setAccessible(true);
			final TaskListener oldTaskListener = (TaskListener) f.get(compiler);
			if (oldTaskListener instanceof WaitAnalyzeTaskListener)
				return;
			TaskListener newTaskListener = new WaitAnalyzeTaskListener(oldTaskListener, compiler);
			f.set(compiler, newTaskListener);
			pe.getContext().put(TaskListener.class, (TaskListener)null);
			pe.getContext().put(TaskListener.class, newTaskListener);
		} catch (Exception e) {
			Lombok.sneakyThrow(e);
		}
	}

	static class WaitAnalyzeTaskListener implements TaskListener {
		TaskListener oldTaskListener;
		JavaCompiler compiler;
		boolean done = false;
		public WaitAnalyzeTaskListener(TaskListener old, JavaCompiler compiler) {
			this.oldTaskListener = old;
			this.compiler = compiler;
		}
		@Override
		public void started(TaskEvent e) {
			if (oldTaskListener != null)
				oldTaskListener.started(e);
			if (e.getKind() == Kind.ANALYZE && !done) {
				patch(compiler);
				done = true;
			}
		}
		@Override
		public void finished(TaskEvent e) {
			if (oldTaskListener != null)
				oldTaskListener.finished(e);
		}
	}

	static void patch(JavaCompiler compiler) {
		try {
			Field f = JavaCompiler.class.getDeclaredField("delegateCompiler");
			f.setAccessible(true);
			compiler = (JavaCompiler) f.get(compiler);
			f = JavaCompiler.class.getDeclaredField("context");
			f.setAccessible(true);
			Context context = (com.sun.tools.javac.util.Context) f.get(compiler);

			// hack: load OOResolve to the same classloader as Resolve so OOResolve will be able to use and override default accessor members
			Class<?> resolveClass = reloadClass("com.sun.tools.javac.comp.OOResolve", Resolve.class.getClassLoader());
			//OOResolve resolve = OOResolve.hook(context);
			Object resolve = resolveClass.getDeclaredMethod("hook", Context.class).invoke(null, context);
			OOLower lower = OOLower.hook(context);

			f = JavaCompiler.class.getDeclaredField("attr");
			f.setAccessible(true);
			Attr attr = (Attr) f.get(compiler);

			f = Attr.class.getDeclaredField("rs");
			f.setAccessible(true);
			f.set(attr, resolve);

			f = JavaCompiler.class.getDeclaredField("lower");
			f.setAccessible(true);
			f.set(compiler, lower);
		} catch (Exception e) {
			Lombok.sneakyThrow(e);
		}
	}

	@SuppressWarnings("unchecked")
	/** add class claz to outClassLoader */
	static <T> Class<T> reloadClass(String claz, ClassLoader outClassLoader) throws Exception {
		try { // already loaded?
			return (Class<T>) outClassLoader.loadClass(claz);
		} catch (ClassNotFoundException e) {}
		String path = claz.replace('.', '/') + ".class";
		ClassLoader incl = OperatorOverloading.class.getClassLoader();
		InputStream is = incl.getResourceAsStream(path);
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[] {
				String.class, byte[].class, int.class, int.class });
		m.setAccessible(true);
		return (Class<T>) m.invoke(outClassLoader, claz, bytes, 0, bytes.length);
	}
}
