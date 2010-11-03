package com.sun.tools.javac.comp;

import java.util.HashMap;
import java.util.Map;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.OperatorSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import static com.sun.tools.javac.code.Kinds.ERR;

public class OOResolve extends Resolve {

	public static OOResolve hook(Context context) {
		context.put(resolveKey, (Resolve)null);
		return new OOResolve(context);
	}

	protected OOResolve(Context context) {
		super(context);
	}

	@Override
	Symbol findMethod(Env<AttrContext> env, Type site, Name name, List<Type> argtypes, 
			List<Type> typeargtypes, boolean allowBoxing, boolean useVarargs, boolean operator) {
		Symbol bestSoFar = super.findMethod(env, site, name, argtypes, typeargtypes, allowBoxing, useVarargs, operator);
		if (bestSoFar.kind >= ERR && operator) { // try operator overloading
			String opname = null;
			List<Type> args = List.nil();
			if (argtypes.size() == 2) {
				opname = binaryOperators.get(name.toString());
				args = List.of(argtypes.get(1));
			} else if (argtypes.size() == 1)
				opname = unaryOperators.get(name.toString());
			if (opname != null) {
				Symbol method = findMethod(env, argtypes.get(0), names.fromString(opname), args, null, false, false, false);
				if (method.kind == Kinds.MTH) {
					bestSoFar = new OperatorSymbol(method.name, method.type, ByteCodes.error+1, method);
					if ("compareTo".equals(opname)) { // change result type to boolean if </>
						MethodType oldmt = (MethodType) method.type;
						bestSoFar.type = new Type.MethodType(oldmt.argtypes, syms.booleanType, oldmt.thrown, oldmt.tsym);
					}
				}
			}
		}
		return bestSoFar;
	}

	protected Map<String, String> binaryOperators = new HashMap<String, String>();
	protected Map<String, String> unaryOperators = new HashMap<String, String>();
	{
		Map<String, String> m = binaryOperators;
		m.put("+", "add");
		m.put("-", "substract");
		m.put("*", "multiply");
		m.put("/", "divide");
		m.put("%", "remainder");
		m.put("&", "and");
		m.put("|", "or");
		m.put("^", "xor");
		m.put("<<", "shiftLeft");
		m.put(">>", "shiftRight");
		m.put("<", "compareTo");
		m.put(">", "compareTo");
		m.put("<=", "compareTo");
		m.put(">=", "compareTo");
		m = unaryOperators;
		m.put("-", "negate");
		m.put("~", "not");
	}
}
