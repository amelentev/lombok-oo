package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.OperatorSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

public class OOLower extends Lower {

	public static OOLower hook(Context context) {
		context.put(lowerKey, (Lower)null);
		return new OOLower(context);
	}

	protected TreeMaker make;
	protected Symtab syms;
	protected Resolve rs;
	protected OOLower(Context context) {
		super(context);
		make = TreeMaker.instance(context);
		syms = Symtab.instance(context);
		rs = Resolve.instance(context);
	}

	@Override
	public void visitBinary(JCBinary tree) {
		if (tree.operator instanceof OperatorSymbol) {
			OperatorSymbol os = (OperatorSymbol) tree.operator;
			if (os.opcode == ByteCodes.error+1) { // if operator overloading?
				MethodSymbol ms = (MethodSymbol) os.owner;
				// construct method invocation ast
				JCFieldAccess meth = make.at(tree).Select(tree.lhs, ms.name);
				meth.type = ms.type;
				meth.sym = ms;
				result = make.Apply(null, meth, List.of(tree.rhs))
						.setType( ((MethodType)ms.type).restype ); // tree.type may be != ms.type.restype. see below
				if (ms.name.contentEquals("compareTo")) {
					// rewrite to `left.compareTo(right) </> 0`
					JCLiteral zero = make.Literal(0);
					int tag = getTag(tree);
					JCBinary r = make.Binary(tag, (JCExpression) result, zero);
					r.type = syms.booleanType;
					r.operator = rs.resolveBinaryOperator(tree, tag, attrEnv, result.type, zero.type);
					result = r;
				}
				result = translate(result);
				return;
			}
		}
		super.visitBinary(tree);
	}

	@Override
	public void visitUnary(JCUnary tree) {
		if (tree.operator instanceof OperatorSymbol) {
			// similar to #visitBinary
			OperatorSymbol os = (OperatorSymbol) tree.operator;
			if (os.opcode == ByteCodes.error+1) {
				MethodSymbol ms = (MethodSymbol) os.owner;
				JCFieldAccess meth = make.Select(tree.arg, ms.name);
				meth.type = ms.type;
				meth.sym = ms;
				result = make.Apply(null, meth, List.<JCExpression>nil())
							.setType(tree.type);
				result = translate(result);
				return;
			}
		}
		super.visitUnary(tree);
	}

	static int getTag(JCTree t) {
		try {
			return JCTree.class.getField("tag").getInt(t); // SunJDK
		} catch (Exception e) {
			return t.getTag(); // OpenJDK
		}
	}
}
