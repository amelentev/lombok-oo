package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

public class OOLower extends Lower {

	public static OOLower hook(Context context) {
		context.put(lowerKey, (Lower)null);
		return new OOLower(context);
	}

	protected TreeMaker make;
	protected OOLower(Context context) {
		super(context);
		make = TreeMaker.instance(context);
	}

	@Override
	public void visitBinary(JCBinary tree) {
		if (tree.operator instanceof Symbol.OperatorSymbol) {
			Symbol.OperatorSymbol os = (Symbol.OperatorSymbol) tree.operator;
			if (os.opcode == ByteCodes.error+1) {
				Symbol.MethodSymbol ms = (Symbol.MethodSymbol) os.owner;
				tree.lhs = translate(tree.lhs);
				tree.rhs = translate(tree.rhs);
				JCFieldAccess meth = make.Select(tree.lhs, ms.name);
				meth.type = ms.type;
				meth.sym = ms;
				JCMethodInvocation r = make.Apply(null, meth, List.of(tree.rhs))
					.setType(tree.type);
				result = translate(r);
				return;
			}
		}
		super.visitBinary(tree);
	}
}
