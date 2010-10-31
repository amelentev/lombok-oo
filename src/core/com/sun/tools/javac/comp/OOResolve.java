package com.sun.tools.javac.comp;

import java.util.HashMap;
import java.util.Map;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.OperatorSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

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
		Symbol bestSoFar = super.findMethod(env, site, name, argtypes, typeargtypes, allowBoxing,
				useVarargs, operator);
		if (bestSoFar.kind >= Kinds.ERR && operator && argtypes.size()==2) {
			Name opname = getOperatorOverloadName(name);
			if (opname != null) {
				Symbol method = findMethod(env, argtypes.get(0), opname, List.of(argtypes.get(1)),
						null, false, false, false);
				if (method.kind == Kinds.MTH)
					bestSoFar = new OperatorSymbol(method.name, method.type, ByteCodes.error+1, method);
			}
		}
		return bestSoFar;
	}

	protected Map<String, String> operator2Method = new HashMap<String, String>();
	{
		String[] s = {	"+", "add",
						"-", "subtract",
						"*", "multiply",
						"/", "divide",
						"%", "remainder",
						"&", "and",
						"|", "or",
						"^", "xor",
						"<<", "shiftLeft",
						">>", "shiftRight", };
		for (int i = 0; i < s.length; i += 2)
			operator2Method.put(s[i], s[i + 1]);
	}
	protected Name getOperatorOverloadName(Name name) {
		String res = operator2Method.get(name.toString());
		return (res != null)
				? names.fromString(res)
				: null;
	}
}
