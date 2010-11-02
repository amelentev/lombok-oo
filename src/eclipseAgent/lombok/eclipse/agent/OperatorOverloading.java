package lombok.eclipse.agent;

import static org.eclipse.jdt.internal.compiler.ast.ASTNode.*;
import static org.eclipse.jdt.internal.compiler.ast.OperatorExpression.OperatorSignatures;
import static org.eclipse.jdt.internal.compiler.ast.OperatorIds.*;

import java.util.HashMap;
import java.util.Map;

import lombok.patcher.*;
import lombok.patcher.scripts.ScriptBuilder;

import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.impl.BooleanConstant;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.*;

public class OperatorOverloading {

	final static String fieldOverloadMethod = "$overloadMethod";

	static void patch(ScriptManager sm) {
		String	BinaryExpression = "org.eclipse.jdt.internal.compiler.ast.BinaryExpression",
				UnaryExpression = "org.eclipse.jdt.internal.compiler.ast.UnaryExpression",
				OperatorExpression = "org.eclipse.jdt.internal.compiler.ast.OperatorExpression",
				TypeBinding = "org.eclipse.jdt.internal.compiler.lookup.TypeBinding",
				BlockScope = "org.eclipse.jdt.internal.compiler.lookup.BlockScope",
				CodeStream = "org.eclipse.jdt.internal.compiler.codegen.CodeStream";
		sm.addScript(ScriptBuilder.addField()
				.targetClass(BinaryExpression)
				.fieldName(fieldOverloadMethod)
				.fieldType("Lorg/eclipse/jdt/internal/compiler/ast/MessageSend;")
				.setPublic()
				.build()
		);
		sm.addScript(ScriptBuilder.addField()
				.targetClass(UnaryExpression)
				.fieldName(fieldOverloadMethod)
				.fieldType("Lorg/eclipse/jdt/internal/compiler/ast/MessageSend;")
				.setPublic()
				.build()
		);
		sm.addScript(ScriptBuilder.addField()
				.targetClass("org.eclipse.jdt.internal.compiler.ast.CombinedBinaryExpression")
				.fieldName(fieldOverloadMethod)
				.fieldType("Lorg/eclipse/jdt/internal/compiler/ast/MessageSend;")
				.setPublic()
				.build()
		);
		sm.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget(BinaryExpression, "resolveType"))
				.decisionMethod(trueHook)
				.valueMethod(new Hook(OperatorOverloading.class.getName(), "binaryResolveType", 
						TypeBinding, BinaryExpression, BlockScope))
				.request(StackRequest.THIS, StackRequest.PARAM1)
				.transplant()
				.build()
		);
		sm.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget(UnaryExpression, "resolveType"))
				.decisionMethod(trueHook)
				.valueMethod(new Hook(OperatorOverloading.class.getName(), "unaryResolveType", 
						TypeBinding, UnaryExpression, BlockScope))
				.request(StackRequest.THIS, StackRequest.PARAM1)
				.transplant()
				.build()
		);
		Hook generateCodeHook = new Hook(OperatorOverloading.class.getName(), "generateCode",
				"boolean", OperatorExpression, BlockScope, CodeStream, "boolean");
		sm.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget(BinaryExpression, "generateCode"))
				.decisionMethod(generateCodeHook)
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2, StackRequest.PARAM3)
				.transplant()
				.build()
		);
		sm.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget(UnaryExpression, "generateCode"))
				.decisionMethod(generateCodeHook)
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2, StackRequest.PARAM3)
				.transplant()
				.build()
		);
	}

	static Hook trueHook = new Hook(OperatorOverloading.class.getName(), "alwaysTrue", "boolean");
	public static boolean alwaysTrue() {
		return true;
	}

	@SuppressWarnings("serial")
	public static Map<String, String> binaryOperators = new HashMap<String, String>() {{
		put("+", "add");
		put("-", "substract");
		put("*", "multiply");
		put("/", "divide");
		put("%", "remainder");
		put("&", "and");
		put("|", "or");
		put("^", "xor");
		put("<<", "shiftLeft");
		put(">>", "shiftRight");
		put("<", "compareTo");
		put(">", "compareTo");
		put("<=", "compareTo");
		put(">=", "compareTo");
	}};
	@SuppressWarnings("serial")
	public static Map<String, String> unaryOperators = new HashMap<String, String>() {{
		put("-", "negate");
		put("~", "not");
	}};

	public static boolean generateCode(OperatorExpression that, BlockScope currentScope, CodeStream codeStream, boolean valueRequired) throws Exception {
		try {
			MessageSend overloadMethod = (MessageSend) that.getClass().getDeclaredField(fieldOverloadMethod).get(that);
			if (overloadMethod != null) {
				overloadMethod.generateCode(currentScope, codeStream, valueRequired);
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false; //that.generateCode(currentScope, codeStream, valueRequired);
	}

	public static TypeBinding overloadBinaryOperator(BinaryExpression that, BlockScope scope) throws Exception {
		// try operator overloading
		String op = that.operatorToString();
		String method = binaryOperators.get(op);
		if (method != null) {
			// find method
			MessageSend ms = new MessageSend();
			ms.receiver = that.left;
			ms.selector = method.toCharArray();
			ms.arguments = new Expression[]{that.right};
			ms.actualReceiverType = that.left.resolvedType;
			ms.binding = scope.getMethod(that.left.resolvedType, ms.selector, new TypeBinding[]{that.right.resolvedType}, ms);
			if (ms.binding != null) { // found
				ms.resolvedType = ms.binding.returnType;
				ms.constant = Constant.NotAConstant;
				ms.sourceStart = that.sourceStart;
				ms.sourceEnd = that.sourceEnd;
				that.constant = Constant.NotAConstant;
				if ("compareTo".equals(method)) {
					// rewrite to `left.compareTo(right) </> 0`
					that.left = ms;
					that.right = new IntLiteral("0".toCharArray(), that.sourceStart, that.sourceEnd, 0);
					that.right.resolve(scope);
					int leftTypeID = that.left.resolvedType.id;
					int rightTypeID = that.right.resolvedType.id;
					if (leftTypeID == rightTypeID) { // if compareTo really returns int
						// resolve rest info about `left </> 0`
						int operator = (that.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT;
						int operatorSignature = OperatorExpression.OperatorSignatures[operator][(leftTypeID << 4) + rightTypeID];
						that.left.computeConversion(scope, TypeBinding.wellKnownType(scope, (operatorSignature >>> 16) & 0x0000F), that.left.resolvedType);
						that.right.computeConversion(scope, TypeBinding.wellKnownType(scope, (operatorSignature >>> 8) & 0x0000F), that.right.resolvedType);
						that.bits |= operatorSignature & 0xF;
						that.computeConstant(scope, leftTypeID, rightTypeID);
						return that.resolvedType = TypeBinding.BOOLEAN;
					}
				} else {
					// that.overloadMethod = ms;
					try {
						that.getClass().getField(fieldOverloadMethod).set(that, ms);
						return that.resolvedType = ms.resolvedType = ms.binding.returnType;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return null;
	}

	public static TypeBinding overloadUnaryOperator(UnaryExpression that, BlockScope scope) throws Exception {
		// similar to #overloadBinaryOperator
		String method = unaryOperators.get(that.operatorToString());
		if (method != null) {
			// find method
			MessageSend ms = new MessageSend();
			ms.receiver = that.expression;
			ms.selector = method.toCharArray();
			ms.arguments = new Expression[0];
			ms.actualReceiverType = that.expression.resolvedType;
			ms.constant = Constant.NotAConstant;
			ms.binding = scope.getMethod(that.expression.resolvedType, ms.selector, new TypeBinding[0], ms);
			if (ms.binding != null) {
				try {
					// that.overloadMethod = ms;
					that.getClass().getField(fieldOverloadMethod).set(that, ms);
					that.constant = Constant.NotAConstant;
					return that.resolvedType = ms.resolvedType = ms.binding.returnType;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	// Nothing important below
	public static TypeBinding binaryResolveType(BinaryExpression that, BlockScope scope) throws Exception {
		// copy from BinaryExpression#resolveType with call to #overloadOperator in proper place
		boolean leftIsCast, rightIsCast;
		if ((leftIsCast = that.left instanceof CastExpression) == true) that.left.bits |= DisableUnnecessaryCastCheck; // will check later on
		TypeBinding leftType = that.left.resolveType(scope);

		if ((rightIsCast = that.right instanceof CastExpression) == true) that.right.bits |= DisableUnnecessaryCastCheck; // will check later on
		TypeBinding rightType = that.right.resolveType(scope);

		// use the id of the type to navigate into the table
		if (leftType == null || rightType == null) {
			that.constant = Constant.NotAConstant;
			return null;
		}

		int leftTypeID = leftType.id;
		int rightTypeID = rightType.id;

		// autoboxing support
		boolean use15specifics = scope.compilerOptions().sourceLevel >= ClassFileConstants.JDK1_5;
		if (use15specifics) {
			if (!leftType.isBaseType() && rightTypeID != TypeIds.T_JavaLangString && rightTypeID != TypeIds.T_null) {
				leftTypeID = scope.environment().computeBoxingType(leftType).id;
			}
			if (!rightType.isBaseType() && leftTypeID != TypeIds.T_JavaLangString && leftTypeID != TypeIds.T_null) {
				rightTypeID = scope.environment().computeBoxingType(rightType).id;
			}
		}
		if (leftTypeID > 15
			|| rightTypeID > 15) { // must convert String + Object || Object + String
			if (leftTypeID == TypeIds.T_JavaLangString) {
				rightTypeID = TypeIds.T_JavaLangObject;
			} else if (rightTypeID == TypeIds.T_JavaLangString) {
				leftTypeID = TypeIds.T_JavaLangObject;
			} else {
				TypeBinding res = overloadBinaryOperator(that, scope);
				if (res != null)
					return res;
				that.constant = Constant.NotAConstant;
				scope.problemReporter().invalidOperator(that, leftType, rightType);
				return null;
			}
		}
		if (((that.bits & OperatorMASK) >> OperatorSHIFT) == OperatorIds.PLUS) {
			if (leftTypeID == TypeIds.T_JavaLangString) {
				that.left.computeConversion(scope, leftType, leftType);
				if (rightType.isArrayType() && ((ArrayBinding) rightType).elementsType() == TypeBinding.CHAR) {
					scope.problemReporter().signalNoImplicitStringConversionForCharArrayExpression(that.right);
				}
			}
			if (rightTypeID == TypeIds.T_JavaLangString) {
				that.right.computeConversion(scope, rightType, rightType);
				if (leftType.isArrayType() && ((ArrayBinding) leftType).elementsType() == TypeBinding.CHAR) {
					scope.problemReporter().signalNoImplicitStringConversionForCharArrayExpression(that.left);
				}
			}
		}

		// the code is an int
		// (cast)  left   Op (cast)  right --> result
		//  0000   0000       0000   0000      0000
		//  <<16   <<12       <<8    <<4       <<0

		// Don't test for result = 0. If it is zero, some more work is done.
		// On the one hand when it is not zero (correct code) we avoid doing the test
		int operator = (that.bits & OperatorMASK) >> OperatorSHIFT;
		int operatorSignature = OperatorExpression.OperatorSignatures[operator][(leftTypeID << 4) + rightTypeID];

		that.left.computeConversion(scope, TypeBinding.wellKnownType(scope, (operatorSignature >>> 16) & 0x0000F), leftType);
		that.right.computeConversion(scope, TypeBinding.wellKnownType(scope, (operatorSignature >>> 8) & 0x0000F), rightType);
		that.bits |= operatorSignature & 0xF;
		switch (operatorSignature & 0xF) { // record the current ReturnTypeID
			// only switch on possible result type.....
			case T_boolean :
				that.resolvedType = TypeBinding.BOOLEAN;
				break;
			case T_byte :
				that.resolvedType = TypeBinding.BYTE;
				break;
			case T_char :
				that.resolvedType = TypeBinding.CHAR;
				break;
			case T_double :
				that.resolvedType = TypeBinding.DOUBLE;
				break;
			case T_float :
				that.resolvedType = TypeBinding.FLOAT;
				break;
			case T_int :
				that.resolvedType = TypeBinding.INT;
				break;
			case T_long :
				that.resolvedType = TypeBinding.LONG;
				break;
			case T_JavaLangString :
				that.resolvedType = scope.getJavaLangString();
				break;
			default : //error........
				that.constant = Constant.NotAConstant;
				scope.problemReporter().invalidOperator(that, leftType, rightType);
				return null;
		}

		// check need for operand cast
		if (leftIsCast || rightIsCast) {
			CastExpression.checkNeedForArgumentCasts(scope, operator, operatorSignature, that.left, leftTypeID, leftIsCast, that.right, rightTypeID, rightIsCast);
		}
		// compute the constant when valid
		that.computeConstant(scope, leftTypeID, rightTypeID);
		return that.resolvedType;
	}

	public static TypeBinding unaryResolveType(UnaryExpression that, BlockScope scope) throws Exception {
		// copy from UnaryExpression#resolveType with call to #overloadOperator in proper place
		boolean expressionIsCast;
		if ((expressionIsCast = that.expression instanceof CastExpression) == true) that.expression.bits |= DisableUnnecessaryCastCheck; // will check later on
		TypeBinding expressionType = that.expression.resolveType(scope);
		if (expressionType == null) {
			that.constant = Constant.NotAConstant;
			return null;
		}
		int expressionTypeID = expressionType.id;
		// autoboxing support
		boolean use15specifics = scope.compilerOptions().sourceLevel >= ClassFileConstants.JDK1_5;
		if (use15specifics) {
			if (!expressionType.isBaseType()) {
				expressionTypeID = scope.environment().computeBoxingType(expressionType).id;
			}
		}
		if (expressionTypeID > 15) {
			TypeBinding res = overloadUnaryOperator(that, scope);
			if (res != null)
				return res;
			that.constant = Constant.NotAConstant;
			scope.problemReporter().invalidOperator(that, expressionType);
			return null;
		}

		int tableId;
		switch ((that.bits & OperatorMASK) >> OperatorSHIFT) {
			case NOT :
				tableId = AND_AND;
				break;
			case TWIDDLE :
				tableId = LEFT_SHIFT;
				break;
			default :
				tableId = MINUS;
		} //+ and - cases

		// the code is an int
		// (cast)  left   Op (cast)  rigth --> result
		//  0000   0000       0000   0000      0000
		//  <<16   <<12       <<8    <<4       <<0
		int operatorSignature = OperatorSignatures[tableId][(expressionTypeID << 4) + expressionTypeID];
		that.expression.computeConversion(scope, TypeBinding.wellKnownType(scope, (operatorSignature >>> 16) & 0x0000F), expressionType);
		that.bits |= operatorSignature & 0xF;
		switch (operatorSignature & 0xF) { // only switch on possible result type.....
			case T_boolean :
				that.resolvedType = TypeBinding.BOOLEAN;
				break;
			case T_byte :
				that.resolvedType = TypeBinding.BYTE;
				break;
			case T_char :
				that.resolvedType = TypeBinding.CHAR;
				break;
			case T_double :
				that.resolvedType = TypeBinding.DOUBLE;
				break;
			case T_float :
				that.resolvedType = TypeBinding.FLOAT;
				break;
			case T_int :
				that.resolvedType = TypeBinding.INT;
				break;
			case T_long :
				that.resolvedType = TypeBinding.LONG;
				break;
			default : //error........
				that.constant = Constant.NotAConstant;
				if (expressionTypeID != T_undefined)
					scope.problemReporter().invalidOperator(that, expressionType);
				return null;
		}
		// compute the constant when valid
		if (that.expression.constant != Constant.NotAConstant) {
			that.constant =
				Constant.computeConstantOperation(
					that.expression.constant,
					expressionTypeID,
					(that.bits & OperatorMASK) >> OperatorSHIFT);
		} else {
			that.constant = Constant.NotAConstant;
			if (((that.bits & OperatorMASK) >> OperatorSHIFT) == NOT) {
				Constant cst = that.expression.optimizedBooleanConstant();
				if (cst != Constant.NotAConstant)
					that.optimizedBooleanConstant = BooleanConstant.fromValue(!cst.booleanValue());
			}
		}
		if (expressionIsCast) {
			// check need for operand cast
			CastExpression.checkNeedForArgumentCast(scope, tableId, operatorSignature, that.expression, expressionTypeID);
		}
		return that.resolvedType;
	}
}
