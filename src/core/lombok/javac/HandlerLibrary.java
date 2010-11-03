/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

import lombok.core.PrintAST;
import lombok.core.SpiLoadUtil;
import lombok.core.TypeLibrary;
import lombok.core.TypeResolver;
import lombok.core.AnnotationValues.AnnotationValueDecodeFail;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;

/**
 * This class tracks 'handlers' and knows how to invoke them for any given AST node.
 * 
 * This class can find the handlers (via SPI discovery) and will set up the given AST node, such as
 * building an AnnotationValues instance.
 */
public class HandlerLibrary {
	private final TypeLibrary typeLibrary = new TypeLibrary();
	private final Map<String, AnnotationHandlerContainer<?>> annotationHandlers = new HashMap<String, AnnotationHandlerContainer<?>>();
	private final Collection<JavacASTVisitor> visitorHandlers = new ArrayList<JavacASTVisitor>();
	private final Messager messager;
	private boolean skipPrintAST = true;
	
	/**
	 * Creates a new HandlerLibrary that will report any problems or errors to the provided messager.
	 * You probably want to use {@link #load(Messager)} instead.
	 */
	public HandlerLibrary(Messager messager) {
		this.messager = messager;
	}
	
	private static class AnnotationHandlerContainer<T extends Annotation> {
		private JavacAnnotationHandler<T> handler;
		private Class<T> annotationClass;
		
		AnnotationHandlerContainer(JavacAnnotationHandler<T> handler, Class<T> annotationClass) {
			this.handler = handler;
			this.annotationClass = annotationClass;
		}
		
		public boolean handle(final JavacNode node) {
			return handler.handle(Javac.createAnnotation(annotationClass, node), (JCAnnotation)node.get(), node);
		}
	}
	
	/**
	 * Creates a new HandlerLibrary that will report any problems or errors to the provided messager,
	 * then uses SPI discovery to load all annotation and visitor based handlers so that future calls
	 * to the handle methods will defer to these handlers.
	 */
	public static HandlerLibrary load(Messager messager) {
		HandlerLibrary library = new HandlerLibrary(messager);
		
		try {
			loadAnnotationHandlers(library);
			loadVisitorHandlers(library);
		} catch (IOException e) {
			System.err.println("Lombok isn't running due to misconfigured SPI files: " + e);
		}
		
		return library;
	}
	
	/** Uses SPI Discovery to find implementations of {@link JavacAnnotationHandler}. */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void loadAnnotationHandlers(HandlerLibrary lib) throws IOException {
		//No, that seemingly superfluous reference to JavacAnnotationHandler's classloader is not in fact superfluous!
		for (JavacAnnotationHandler handler : SpiLoadUtil.findServices(JavacAnnotationHandler.class, JavacAnnotationHandler.class.getClassLoader())) {
			Class<? extends Annotation> annotationClass =
				SpiLoadUtil.findAnnotationClass(handler.getClass(), JavacAnnotationHandler.class);
			AnnotationHandlerContainer<?> container = new AnnotationHandlerContainer(handler, annotationClass);
			if (lib.annotationHandlers.put(container.annotationClass.getName(), container) != null) {
				lib.javacWarning("Duplicate handlers for annotation type: " + container.annotationClass.getName());
			}
			lib.typeLibrary.addType(container.annotationClass.getName());
		}
	}
	
	/** Uses SPI Discovery to find implementations of {@link JavacASTVisitor}. */
	private static void loadVisitorHandlers(HandlerLibrary lib) throws IOException {
		//No, that seemingly superfluous reference to JavacASTVisitor's classloader is not in fact superfluous!
		for (JavacASTVisitor visitor : SpiLoadUtil.findServices(JavacASTVisitor.class, JavacASTVisitor.class.getClassLoader())) {
			lib.visitorHandlers.add(visitor);
		}
	}
	
	/** Generates a warning in the Messager that was used to initialize this HandlerLibrary. */
	public void javacWarning(String message) {
		javacWarning(message, null);
	}
	
	/** Generates a warning in the Messager that was used to initialize this HandlerLibrary. */
	public void javacWarning(String message, Throwable t) {
		messager.printMessage(Diagnostic.Kind.WARNING, message + (t == null ? "" : (": " + t)));
	}
	
	/** Generates an error in the Messager that was used to initialize this HandlerLibrary. */
	public void javacError(String message) {
		javacError(message, null);
	}
	
	/** Generates an error in the Messager that was used to initialize this HandlerLibrary. */
	public void javacError(String message, Throwable t) {
		messager.printMessage(Diagnostic.Kind.ERROR, message + (t == null ? "" : (": " + t)));
		if (t != null) t.printStackTrace();
	}
	
	/**
	 * Handles the provided annotation node by first finding a qualifying instance of
	 * {@link JavacAnnotationHandler} and if one exists, calling it with a freshly cooked up
	 * instance of {@link lombok.core.AnnotationValues}.
	 * 
	 * Note that depending on the printASTOnly flag, the {@link lombok.core.PrintAST} annotation
	 * will either be silently skipped, or everything that isn't {@code PrintAST} will be skipped.
	 * 
	 * The HandlerLibrary will attempt to guess if the given annotation node represents a lombok annotation.
	 * For example, if {@code lombok.*} is in the import list, then this method will guess that
	 * {@code Getter} refers to {@code lombok.Getter}, presuming that {@link lombok.javac.handlers.HandleGetter}
	 * has been loaded.
	 * 
	 * @param unit The Compilation Unit that contains the Annotation AST Node.
	 * @param node The Lombok AST Node representing the Annotation AST Node.
	 * @param annotation 'node.get()' - convenience parameter.
	 */
	public boolean handleAnnotation(JCCompilationUnit unit, JavacNode node, JCAnnotation annotation) {
		TypeResolver resolver = new TypeResolver(typeLibrary, node.getPackageDeclaration(), node.getImportStatements());
		String rawType = annotation.annotationType.toString();
		boolean handled = false;
		for (String fqn : resolver.findTypeMatches(node, rawType)) {
			boolean isPrintAST = fqn.equals(PrintAST.class.getName());
			if (isPrintAST == skipPrintAST) continue;
			AnnotationHandlerContainer<?> container = annotationHandlers.get(fqn);
			if (container == null) continue;
			
			try {
				handled |= container.handle(node);
			} catch (AnnotationValueDecodeFail fail) {
				fail.owner.setError(fail.getMessage(), fail.idx);
			} catch (Throwable t) {
				String sourceName = "(unknown).java";
				if (unit != null && unit.sourcefile != null) sourceName = unit.sourcefile.getName();
				javacError(String.format("Lombok annotation handler %s failed on " + sourceName, container.handler.getClass()), t);
			}
		}
		
		return handled;
	}
	
	/**
	 * Will call all registered {@link JavacASTVisitor} instances.
	 */
	public void callASTVisitors(JavacAST ast) {
		for (JavacASTVisitor visitor : visitorHandlers) try {
			if (!visitor.isResolutionBased()) ast.traverse(visitor);
		} catch (Throwable t) {
			javacError(String.format("Lombok visitor handler %s failed", visitor.getClass()), t);
		}
		
		for (JavacASTVisitor visitor : visitorHandlers) try {
			if (visitor.isResolutionBased()) ast.traverse(visitor);
		} catch (Throwable t) {
			javacError(String.format("Lombok visitor handler %s failed", visitor.getClass()), t);
		}
	}
	
	/**
	 * Lombok does not currently support triggering annotations in a specified order; the order is essentially
	 * random right now. This lack of order is particularly annoying for the {@code PrintAST} annotation,
	 * which is almost always intended to run last. Hence, this hack, which lets it in fact run last.
	 * 
	 * @see #skipAllButPrintAST()
	 */
	public void skipPrintAST() {
		skipPrintAST = true;
	}
	
	/** @see #skipPrintAST() */
	public void skipAllButPrintAST() {
		skipPrintAST = false;
	}
}
