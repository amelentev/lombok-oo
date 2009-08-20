package lombok.javac.disableCheckedExceptions;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CheckForThrownExceptionTransformer implements ClassFileTransformer {
	@Override public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if ( className.equals("com/sun/tools/javac/comp/Check") ) return transformCheck(classfileBuffer);
		return null;
	}
	
	private byte[] transformCheck(byte[] classfileBuffer) {
		ClassReader reader = new ClassReader(classfileBuffer);
		ClassWriter writer = new ClassWriter(reader, 0);
		
		ClassAdapter adapter = new CheckAdapter(writer);
		reader.accept(adapter, 0);
		return writer.toByteArray();
	}
	
	private static class CheckAdapter extends ClassAdapter {
		CheckAdapter(ClassVisitor cv) {
			super(cv);
		}
		
		@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			MethodVisitor writerVisitor = super.visitMethod(access, name, desc, signature, exceptions);
			if ( !name.equals("isUnchecked") ) return writerVisitor;
			if ( !desc.equals("(Lcom/sun/tools/javac/code/Symbol$ClassSymbol;)Z") ) return writerVisitor;
			
			return new IsUncheckedRewriter(writerVisitor);
		}
	}
	
	private static class IsUncheckedRewriter extends MethodAdapter {
		public IsUncheckedRewriter(MethodVisitor mv) {
			super(mv);
		}
		
		@Override public void visitEnd() {
			super.visitInsn(Opcodes.ICONST_1);
			super.visitInsn(Opcodes.IRETURN);
			super.visitMaxs(1, 2);
			super.visitEnd();
		}
		
		@Override public void visitFieldInsn(int arg0, String arg1, String arg2, String arg3) {}
		@Override public void visitFrame(int arg0, int arg1, Object[] arg2, int arg3, Object[] arg4) {}
		@Override public void visitIincInsn(int arg0, int arg1) {}
		@Override public void visitInsn(int arg0) {}
		@Override public void visitIntInsn(int arg0, int arg1) {}
		@Override public void visitJumpInsn(int arg0, Label arg1) {}
		@Override public void visitLabel(Label arg0) {}
		@Override public void visitLdcInsn(Object arg0) {}
		@Override public void visitLineNumber(int arg0, Label arg1) {}
		@Override public void visitLocalVariable(String arg0, String arg1, String arg2, Label arg3, Label arg4, int arg5) {}
		@Override public void visitLookupSwitchInsn(Label arg0, int[] arg1, Label[] arg2) {}
		@Override public void visitMaxs(int arg0, int arg1) {}
		@Override public void visitMethodInsn(int arg0, String arg1, String arg2, String arg3) {}
		@Override public void visitMultiANewArrayInsn(String arg0, int arg1) {}
		@Override public void visitTableSwitchInsn(int arg0, int arg1, Label arg2, Label[] arg3) {}
		@Override public void visitTryCatchBlock(Label arg0, Label arg1, Label arg2, String arg3) {}
		@Override public void visitTypeInsn(int arg0, String arg1) {}
		@Override public void visitVarInsn(int arg0, int arg1) {}
	}
}
