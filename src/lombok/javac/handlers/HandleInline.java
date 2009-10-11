package lombok.javac.handlers;

import lombok.Inline;
import lombok.javac.JavacAST;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacAST.Node;

import org.mangosdk.spi.ProviderFor;

import com.sun.source.tree.ReturnTree;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleInline implements JavacAnnotationHandler<Inline> {
	public boolean handle(lombok.core.AnnotationValues<Inline> ann, JCAnnotation ast, Node annotationNode) {
		final JCVariableDecl decl = (JCVariableDecl)annotationNode.up().get();
		if ( decl.init == null ) {
			annotationNode.addError("@Inline variable declarations need to be initialized.");
			return true;
		}
		JCMethodInvocation methodInv = (JCMethodInvocation) decl.init;
		if (methodInv.args.size()>0) {
			annotationNode.addError("@Inline method with >0 args not supported yet.");
			return true;
		}
		decl.init = null;
		
		Node ancestor = annotationNode.up().directUp();
		JCTree blockNode = ancestor.get();
		
		final List<JCStatement> statements;
		if ( blockNode instanceof JCBlock ) {
			statements = ((JCBlock)blockNode).stats;
		} else if ( blockNode instanceof JCCase ) {
			statements = ((JCCase)blockNode).stats;
		} else if ( blockNode instanceof JCMethodDecl ) {
			statements = ((JCMethodDecl)blockNode).body.stats;
		} else {
			annotationNode.addError("@Inline is legal only on a local variable declaration inside a block.");
			return true;
		}
		
		List<JCStatement> before = List.nil();
		List<JCStatement> after = List.nil();
		boolean seenDeclaration = false;
		for ( JCStatement statement : statements ) {
			if ( !seenDeclaration ) {
				if ( statement == decl )
					seenDeclaration = true;
				else
					before = before.append(statement);
			} else after = after.append(statement);
		}
		if ( !seenDeclaration ) {
			annotationNode.addError("LOMBOK BUG: Can't find this local variable declaration inside its parent.");
			return true;
		}
		
		JCMethodDecl methodDecl = findMethod(methodInv.meth.toString().trim(), annotationNode);
		final TreeMaker tm = annotationNode.getTreeMaker();
		JCTree inlineBlock = methodDecl.body.accept(new TreeCopier<Void>(tm) {
			@Override
			public JCTree visitReturn(ReturnTree node, Void p) {
				// replace "return var" by "decl = var"
				return tm.Assignment(new VarSymbol(0, decl.name, decl.type, null), (JCExpression) node.getExpression());
			}
		}, null);
		
		List<JCStatement> newblock = before.append(decl).append((JCStatement) inlineBlock).appendList(after);
		if ( blockNode instanceof JCBlock ) {
			((JCBlock)blockNode).stats = newblock;
		} else if ( blockNode instanceof JCCase ) {
			((JCCase)blockNode).stats = newblock;
		} else if ( blockNode instanceof JCMethodDecl ) {
			((JCMethodDecl)blockNode).body.stats = newblock;
		} else throw new AssertionError("Should not get here");
		
		ancestor.rebuild();
		return true;
	};
	
	static JCMethodDecl findMethod(String methodName, JavacAST.Node node) {
		while ( node != null && !(node.get() instanceof JCClassDecl) )
			node = node.up();
		if ( node != null && node.get() instanceof JCClassDecl )
			for ( JCTree def : ((JCClassDecl)node.get()).defs )
				if ( def instanceof JCMethodDecl )
					if ( ((JCMethodDecl)def).name.contentEquals(methodName) )
						return (JCMethodDecl) def;
		return null;
	}
}
