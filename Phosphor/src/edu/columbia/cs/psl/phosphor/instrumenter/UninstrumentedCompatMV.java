package edu.columbia.cs.psl.phosphor.instrumenter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.Instrumenter;
import edu.columbia.cs.psl.phosphor.MethodDescriptor;
import edu.columbia.cs.psl.phosphor.SelectiveInstrumentationManager;
import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.instrumenter.analyzer.NeverNullArgAnalyzerAdapter;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LocalVariableNode;

import edu.columbia.cs.psl.phosphor.runtime.ReflectionMasker;
import edu.columbia.cs.psl.phosphor.runtime.TaintSentinel;
import edu.columbia.cs.psl.phosphor.runtime.UninstrumentedTaintSentinel;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArray;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArrayWithObjTag;

public class UninstrumentedCompatMV extends TaintAdapter {
	private NeverNullArgAnalyzerAdapter analyzer;
	private boolean skipFrames;
	int preallocArg = 0;
	private MethodVisitor uninst;
	private String desc;
	public UninstrumentedCompatMV(int access, String className, String name, String desc, String signature, String[] exceptions, MethodVisitor mv, NeverNullArgAnalyzerAdapter analyzer,
			boolean skipFrames,MethodVisitor uninst) {
		super(access, className, name, desc, signature, exceptions, mv, analyzer);
		this.uninst=uninst;
		this.desc = desc;
		this.analyzer = analyzer;
		this.skipFrames = skipFrames;
		if((access & Opcodes.ACC_STATIC) == 0)
		{
			preallocArg++;
		}
		for(Type t:Type.getArgumentTypes(desc))
			preallocArg += t.getSize();
		if(name.equals("<init>"))
			preallocArg++;
//		System.out.println(name+desc);
	}

	@Override
	public void visitCode() {
		super.visitCode();
//		analyzer.locals.add("[Ljava/lang/Object;");
	}
	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
		Object[] newLocal = new Object[local.length];
		Object[] newStack = new Object[stack.length];
		for (int i = 0; i < local.length; i++) {
			if (local[i] instanceof String) {
				Type t = Type.getObjectType(((String) local[i]));
				if (t.getSort() == Type.ARRAY && t.getElementType().getSort() != Type.OBJECT && t.getDimensions() > 1)
					newLocal[i] = MultiDTaintedArray.getTypeForType(t).getInternalName();
				else
					newLocal[i] = local[i];
			} else
				newLocal[i] = local[i];
		}
		for (int i = 0; i < stack.length; i++) {
			if (stack[i] instanceof String) {
				Type t = Type.getObjectType(((String) stack[i]));
				if (t.getSort() == Type.ARRAY && t.getElementType().getSort() != Type.OBJECT && t.getDimensions() > 1)
					newStack[i] = MultiDTaintedArray.getTypeForType(t).getInternalName();
				else
					newStack[i] = stack[i];
			} else
				newStack[i] = stack[i];
		}
		super.visitFrame(type, nLocal, newLocal, nStack, newStack);
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		Type t = Type.getType(desc);
		if (t.getSort() == Type.ARRAY && t.getDimensions() > 1 && t.getElementType().getSort() != Type.OBJECT)
			desc = MultiDTaintedArray.getTypeForType(t).getDescriptor();
		switch (opcode) {
		case Opcodes.GETFIELD:
		case Opcodes.GETSTATIC:
			super.visitFieldInsn(opcode, owner, name, desc);
			break;
		case Opcodes.PUTFIELD:
		case Opcodes.PUTSTATIC:
			if (t.getSort() == Type.ARRAY && t.getDimensions() == 1 && t.getElementType().getSort() != Type.OBJECT) {
				//1d prim array - need to make sure that there is some taint here
				if (!Configuration.SINGLE_TAG_PER_ARRAY) {
					FrameNode fn = getCurrentFrameNode();
					fn.type = Opcodes.F_NEW;
					super.visitInsn(Opcodes.DUP);
					Label ok = new Label();
					super.visitJumpInsn(IFNULL, ok);
					if (opcode == Opcodes.PUTFIELD) {
						//O A
						super.visitInsn(DUP2);
						//O A O A
					} else
						super.visitInsn(Opcodes.DUP);
					super.visitInsn(Opcodes.ARRAYLENGTH);
					if (!Configuration.MULTI_TAINTING)
						super.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
					else
						super.visitTypeInsn(Opcodes.ANEWARRAY, Configuration.TAINT_TAG_INTERNAL_NAME);
					super.visitFieldInsn(opcode, owner, name + TaintUtils.TAINT_FIELD, Configuration.TAINT_TAG_ARRAYDESC);
					super.visitLabel(ok);
					if (!skipFrames)
						fn.accept(this);
				}
			}
			super.visitFieldInsn(opcode, owner, name, desc);
			break;
		}
	}

	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		Type arrayType = Type.getType(desc);
		Type origType = arrayType;
		boolean needToHackDims = false;
		int tmp = 0;
		if (arrayType.getElementType().getSort() != Type.OBJECT) {
			if (dims == arrayType.getDimensions()) {
				needToHackDims = true;
				dims--;
				tmp = lvs.getTmpLV(Type.INT_TYPE);
				super.visitVarInsn(Opcodes.ISTORE, tmp);
			}
			arrayType = MultiDTaintedArray.getTypeForType(arrayType);
			//Type.getType(MultiDTaintedArray.getClassForComponentType(arrayType.getElementType().getSort()));
			desc = arrayType.getInternalName();
		}
		if (dims == 1) {
			//It's possible that we dropped down to a 1D object type array
			super.visitTypeInsn(ANEWARRAY, arrayType.getElementType().getInternalName());
		} else
			super.visitMultiANewArrayInsn(desc, dims);
		if (needToHackDims) {
			super.visitInsn(DUP);
			super.visitVarInsn(ILOAD, tmp);
			lvs.freeTmpLV(tmp);
			super.visitIntInsn(BIPUSH, origType.getElementType().getSort());
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName((Configuration.MULTI_TAINTING ? MultiDTaintedArrayWithObjTag.class : MultiDTaintedArrayWithIntTag.class)), "initLastDim",
					"([Ljava/lang/Object;II)V", false);

		}
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		if (opcode == Opcodes.CHECKCAST) {
			if (analyzer.stack.size() > 0 && "java/lang/Object".equals(analyzer.stack.get(analyzer.stack.size() - 1)) && type.startsWith("[") && type.length() == 2) {
				super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "maybeUnbox", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			}
			Type t = Type.getType(type);
			if (t.getSort() == Type.ARRAY && t.getDimensions() > 1 && t.getElementType().getSort() != Type.OBJECT) {
				type = MultiDTaintedArray.getTypeForType(t).getInternalName();
			}
		} else if (opcode == Opcodes.ANEWARRAY) {
			Type t = Type.getType(type);
			if (t.getSort() == Type.ARRAY && t.getElementType().getDescriptor().length() == 1) {
				//e.g. [I for a 2 D array -> MultiDTaintedIntArray
				type = MultiDTaintedArray.getTypeForType(t).getInternalName();
			}
		} else if (opcode == Opcodes.INSTANCEOF) {
			Type t = Type.getType(type);
			if (t.getSort() == Type.ARRAY && t.getDimensions() > 1 && t.getElementType().getSort() != Type.OBJECT)
				type = MultiDTaintedArray.getTypeForType(t).getDescriptor();
			else if(t.getSort() == Type.ARRAY && t.getDimensions() == 1 && t.getElementType().getSort() != Type.OBJECT
					&& getTopOfStackObject().equals("java/lang/Object"))
			{
				type = MultiDTaintedArray.getTypeForType(t).getInternalName();
			}
		}
		super.visitTypeInsn(opcode, type);
	}

	@Override
	public void visitInsn(int opcode) {
		switch (opcode) {
		case Opcodes.ARETURN:
			Type onTop = getTopOfStackType();
			Type retType = Type.getReturnType(this.desc);
			if(retType.getDescriptor().equals("Ljava/lang/Object;") && onTop.getSort() == Type.ARRAY
					&& onTop.getDimensions() == 1 && onTop.getElementType().getSort() != Type.OBJECT)
			{
				registerTaintedArray(onTop.getDescriptor());
			}
			super.visitInsn(opcode);
			break;
		case Opcodes.AASTORE:
			Object arType = analyzer.stack.get(analyzer.stack.size() - 3);
			Type elType = getTopOfStackType();
			if (arType.equals("[Ljava/lang/Object;") && ((elType.getSort() == Type.ARRAY && elType.getElementType().getSort() != Type.OBJECT))) {
				super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			} else if (arType instanceof String &&
					((String) arType).contains("[Ledu/columbia/cs/psl/phosphor/struct/multid/MultiDTainted")) {
				super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			}
			super.visitInsn(opcode);
			break;
		case Opcodes.AALOAD:
			Object arrayType = analyzer.stack.get(analyzer.stack.size() - 2);
			Type t = getTypeForStackType(arrayType);
			if (t.getDimensions() == 1 && t.getElementType().getDescriptor().startsWith("Ledu/columbia/cs/psl/phosphor/struct/multid/MultiDTainted")) {
				//				System.out.println("it's a multi array in disguise!!!");
				super.visitInsn(opcode);
				try {
					super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "unbox1D", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
					super.visitTypeInsn(Opcodes.CHECKCAST, "[" + MultiDTaintedArray.getPrimitiveTypeForWrapper(Class.forName(t.getElementType().getInternalName().replace("/", "."))));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//				System.out.println("AALOAD " + analyzer.stack);

			} else
				super.visitInsn(opcode);
			break;
		case Opcodes.MONITORENTER:
		case Opcodes.MONITOREXIT:
//			if (getTopOfStackObject().equals("java/lang/Object")) {
//				//never allow monitor to occur on a multid type
//				super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "unbox1D", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
//			}
			super.visitInsn(opcode);
			break;
		case Opcodes.ARRAYLENGTH:
			Type onStack = getTopOfStackType();
			if (onStack.getSort() == Type.OBJECT) {
				Type underlying = MultiDTaintedArray.getPrimitiveTypeForWrapper(onStack.getInternalName());
				if (underlying != null) {
					super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "unbox1D", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
					super.visitTypeInsn(Opcodes.CHECKCAST, "[" + underlying.getDescriptor());
				}
			}
			super.visitInsn(opcode);

			break;
		default:
			super.visitInsn(opcode);
			break;
		}
	}

	void ensureBoxedAt(int n, Type t) {
		switch (n) {
		case 0:
			super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			super.visitTypeInsn(Opcodes.CHECKCAST, t.getInternalName());
			break;
		case 1:
			Object top = analyzer.stack.get(analyzer.stack.size() - 1);
			if (top == Opcodes.LONG || top == Opcodes.DOUBLE || top == Opcodes.TOP) {
				super.visitInsn(DUP2_X1);
				super.visitInsn(POP2);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
				super.visitTypeInsn(Opcodes.CHECKCAST, t.getInternalName());
				super.visitInsn(DUP_X2);
				super.visitInsn(POP);
			} else {
				super.visitInsn(SWAP);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
				super.visitTypeInsn(Opcodes.CHECKCAST, t.getInternalName());
				super.visitInsn(SWAP);
			}
			break;
		default:
			LocalVariableNode[] d = storeToLocals(n);

			super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			super.visitTypeInsn(Opcodes.CHECKCAST, t.getInternalName());
			for (int i = n - 1; i >= 0; i--) {
				loadLV(i, d);
			}
			freeLVs(d);

		}
	}
	HashSet<Integer> boxAtNextJump = new HashSet<Integer>();

	@Override
	public void visitVarInsn(int opcode, int var) {
		if (opcode == TaintUtils.ALWAYS_BOX_JUMP) {
			boxAtNextJump.add(var);
			return;
		}
		if (opcode == TaintUtils.ALWAYS_AUTOBOX && analyzer.locals.size() > var && analyzer.locals.get(var) instanceof String) {
			Type t = Type.getType((String) analyzer.locals.get(var));
			if (t.getSort() == Type.ARRAY && t.getElementType().getSort() != Type.OBJECT) {
				//				System.out.println("Restoring " + var + " to be boxed");
				super.visitVarInsn(ALOAD, var);
				registerTaintedArray(getTopOfStackType().getDescriptor());
				super.visitVarInsn(ASTORE, var);
			}
			return;
		}
		super.visitVarInsn(opcode, var);
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {	
		if (Configuration.WITH_UNBOX_ACMPEQ && (opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE)) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(TaintUtils.class), "ensureUnboxed", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			mv.visitInsn(SWAP);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(TaintUtils.class), "ensureUnboxed", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			mv.visitInsn(SWAP);
		}
		
		if (boxAtNextJump.size() > 0 && opcode != Opcodes.GOTO) {
			Label origDest = label;
			Label newDest = new Label();
			Label origFalseLoc = new Label();

			super.visitJumpInsn(opcode, newDest);
			FrameNode fn = getCurrentFrameNode();

			super.visitJumpInsn(GOTO, origFalseLoc);
			//			System.out.println("taint passing mv monkeying with jump");
			super.visitLabel(newDest);
			fn.accept(this);
			for (Integer var : boxAtNextJump) {
				super.visitVarInsn(ALOAD, var);
				registerTaintedArray(getTopOfStackType().getDescriptor());
				
				super.visitVarInsn(ASTORE, var);
			}
			super.visitJumpInsn(GOTO, origDest);
			super.visitLabel(origFalseLoc);
			fn.accept(this);
			boxAtNextJump.clear();
		}
		else
			super.visitJumpInsn(opcode, label);
	}
	
	public void registerTaintedArray(String descOfDest) {
		Type onStack = Type.getType(descOfDest);//getTopOfStackType();
		//TA A
		Type wrapperType = Type.getType(MultiDTaintedArray.getClassForComponentType(onStack.getElementType().getSort()));
//				System.out.println("zzzNEW " + wrapperType);
		Label isNull = new Label();
		FrameNode fn = getCurrentFrameNode();

//		if (!ignoreLoadingNextTaint&& !isIgnoreAllInstrumenting)
//			super.visitInsn(TaintUtils.IGNORE_EVERYTHING);
		super.visitInsn(DUP);
		super.visitJumpInsn(IFNULL, isNull);
//		if (!ignoreLoadingNextTaint&& !isIgnoreAllInstrumenting)
//			super.visitInsn(TaintUtils.IGNORE_EVERYTHING);
		super.visitTypeInsn(NEW, wrapperType.getInternalName());
		//A N
		super.visitInsn(DUP_X1);
		super.visitInsn(DUP_X1);
		super.visitInsn(POP);
		//N N A
		if (Configuration.SINGLE_TAG_PER_ARRAY) {
			super.visitInsn(Configuration.NULL_TAINT_LOAD_OPCODE);
			super.visitInsn(Opcodes.SWAP);
			if (!Configuration.MULTI_TAINTING)
				super.visitMethodInsn(INVOKESPECIAL, wrapperType.getInternalName(), "<init>", "(I" + onStack.getDescriptor() + ")V", false);
			else
				super.visitMethodInsn(INVOKESPECIAL, wrapperType.getInternalName(), "<init>", "(Ljava/lang/Object;" + onStack.getDescriptor() + ")V", false);

		} else {
			super.visitInsn(DUP);
			super.visitInsn(ARRAYLENGTH);
			if(Configuration.MULTI_TAINTING)
				super.visitTypeInsn(ANEWARRAY, Configuration.TAINT_TAG_INTERNAL_NAME);
			else
				super.visitIntInsn(NEWARRAY, Opcodes.T_INT);
			super.visitInsn(SWAP);
			if (!Configuration.MULTI_TAINTING)
				super.visitMethodInsn(INVOKESPECIAL, wrapperType.getInternalName(), "<init>", "([I" + onStack.getDescriptor() + ")V", false);
			else
				super.visitMethodInsn(INVOKESPECIAL, wrapperType.getInternalName(), "<init>", "([Ljava/lang/Object;" + onStack.getDescriptor() + ")V", false);
		}
		Label isDone = new Label();
		FrameNode fn2 = getCurrentFrameNode();
		super.visitJumpInsn(GOTO, isDone);
		super.visitLabel(isNull);
		acceptFn(fn);
		super.visitLabel(isDone);
		fn2.stack.set(fn2.stack.size()-1, "java/lang/Object");
		TaintAdapter.acceptFn(fn2,mv);
	}
		//A
	void storeTaintArrayAt(int n, String descAtDest) {
//		if (TaintUtils.DEBUG_DUPSWAP)
//			System.out.println(name + " POP AT " + n + " from " + analyzer.stack);
		switch (n) {
		case 0:
			Object top = analyzer.stack.get(analyzer.stack.size() - 1);
			if (top == Opcodes.LONG || top == Opcodes.DOUBLE || top == Opcodes.TOP)
				super.visitInsn(POP2);
			else
				super.visitInsn(POP);
			throw new IllegalStateException("Not supposed to ever pop the top like this");
			//			break;
		case 1:
			top = analyzer.stack.get(analyzer.stack.size() - 1);
			if (top == Opcodes.LONG || top == Opcodes.DOUBLE || top == Opcodes.TOP) {
				throw new IllegalStateException("Not supposed to ever pop the top like this");
			} else {
				Object second = analyzer.stack.get(analyzer.stack.size() - 2);
				if (second == Opcodes.LONG || second == Opcodes.DOUBLE || second == Opcodes.TOP) {
					throw new IllegalStateException("Not supposed to ever pop the top like this");
				} else {
					//V V
					registerTaintedArray(descAtDest);
				}
			}
			break;
		case 2:

		default:
			LocalVariableNode[] d = storeToLocals(n - 1);

			//						System.out.println("POST load the top " + n + ":" + analyzer.stack);
			registerTaintedArray(descAtDest);
			for (int i = n - 2; i >= 0; i--) {
				loadLV(i, d);
			}
			freeLVs(d);

		}
		if (TaintUtils.DEBUG_CALLS)
			System.out.println("POST POP AT " + n + ":" + analyzer.stack);

	}
	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
		if (Instrumenter.isIgnoredClass(owner)) {
			super.visitMethodInsn(opcode, owner, name, desc, itf);
			return;
		}
		//Stupid workaround for eclipse benchmark
		if (name.equals("getProperty") && className.equals("org/eclipse/jdt/core/tests/util/Util")) {
			owner = Type.getInternalName(ReflectionMasker.class);
			name = "getPropertyHideBootClasspath";
		}
		Type ownerType = Type.getObjectType(owner);
		if (opcode == INVOKEVIRTUAL && ownerType.getSort() == Type.ARRAY && ownerType.getElementType().getSort() != Type.OBJECT && ownerType.getDimensions() > 1) {
			//			System.out.println("got to change the owner on primitive array call from " +owner+" to " + MultiDTaintedArray.getTypeForType(ownerType));
			owner = MultiDTaintedArray.getTypeForType(ownerType).getInternalName();
		}
		Type origReturnType = Type.getReturnType(desc);
		boolean isCalledOnArrayType = false;
		if ((owner.equals("java/lang/System") || owner.equals("java/lang/VMSystem") || owner.equals("java/lang/VMMemoryManager")) && name.equals("arraycopy")
				&& !desc.equals("(Ljava/lang/Object;ILjava/lang/Object;IILjava/lang/DCompMarker;)V")) {
			owner = Type.getInternalName(TaintUtils.class);
			super.visitMethodInsn(opcode, owner, name, desc, itf);
			return;
		}
		if (Instrumenter.isIgnoredClass(owner) || Instrumenter.isIgnoredMethod(owner, name, desc)) {
			super.visitMethodInsn(opcode, owner, name, desc, itf);
			return;
		}
		if ((opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL) && analyzer.stack.size() > 0) {
			int argsize = 0;
			for (Type t : Type.getArgumentTypes(desc))
				argsize += t.getSize();
			//			System.out.println(name + desc + analyzer.stack);
			Object calledOn = analyzer.stack.get(analyzer.stack.size() - argsize - 1);
			if (calledOn instanceof String && ((String) calledOn).startsWith("[")) {
				//				System.out.println("Called on arraystack");
				isCalledOnArrayType = true;
			}
		}

		if (!isCalledOnArrayType && Configuration.WITH_SELECTIVE_INST && !owner.startsWith("[") && Instrumenter.isIgnoredMethodFromOurAnalysis(owner, name, desc)) {
			if (name.equals("<init>")) {
				super.visitInsn(Opcodes.ACONST_NULL);
				desc = desc.substring(0, desc.indexOf(')')) + Type.getDescriptor(UninstrumentedTaintSentinel.class) + ")" + desc.substring(desc.indexOf(')') + 1);
			} else
				name += TaintUtils.METHOD_SUFFIX_UNINST;
			desc = TaintUtils.remapMethodDescForUninst(desc);

			if(TaintUtils.PREALLOC_RETURN_ARRAY)
				analyzer.visitVarInsn(Opcodes.ALOAD, preallocArg);
//			System.out.println("Load prealloc arg "  + preallocArg +" -- "+ analyzer.locals.get(preallocArg)+" , now " +analyzer.stack);
			boolean ignoreNext = false;
			
			Type[] args = Type.getArgumentTypes(desc);
			Type[] argsInReverse = new Type[args.length];
			int argsSize = 0;
			for (int i = 0; i < args.length; i++) {
				argsInReverse[args.length - i - 1] = args[i];
				argsSize += args[i].getSize();
			}
			int i = 1;
			int n = 1;
//						System.out.println("12dw23 Calling "+owner+"."+name+desc + "with " + analyzer.stack);
			for (Type t : argsInReverse) {
				if (analyzer.stack.get(analyzer.stack.size() - i) == Opcodes.TOP)
					i++;

				//			if(ignoreNext)
				//				System.out.println("ignore next i");
				Type onStack = getTypeForStackType(analyzer.stack.get(analyzer.stack.size() - i));
				//System.out.println(name+ " ONStack: " + onStack + " and t = " + t);
				//System.out.println(ignoreNext + " and "+ analyzer.stack.get(analyzer.stack.size() - i) );
				if (t.getDescriptor().equals("Ljava/lang/Object;") && onStack.getSort() == Type.ARRAY && onStack.getElementType().getSort() != Type.OBJECT) {
					//There is an extra taint on the stack at this position
					if (TaintUtils.DEBUG_CALLS)
						System.err.println("removing taint array in call at " + n);
					storeTaintArrayAt(n, onStack.getDescriptor());
				}
				n++;
				i++;
			}
			
			super.visitMethodInsn(opcode, owner, name, desc, itf);
		} else if (!Instrumenter.isIgnoredClass(owner) && !owner.startsWith("[") && !isCalledOnArrayType) {
			//call into the instrumented version			
			boolean hasChangedDesc = false;

			if (desc.equals(TaintUtils.remapMethodDesc(desc)) && !TaintUtils.PREALLOC_RETURN_ARRAY) {
				//Calling an instrumented method possibly!
				Type[] args = Type.getArgumentTypes(desc);
				int argsSize = 0;
				for (int i = 0; i < args.length; i++) {
					argsSize += args[args.length - i - 1].getSize();
					//				if (TaintUtils.DEBUG_CALLS)
					//					System.out.println(i + ", " + analyzer.stack.get(analyzer.stack.size() - argsSize) + " " + args[args.length - i - 1]);
					//					System.out.println(analyzer.stack);
					//TODO optimize
					if (args[args.length - i - 1].getDescriptor().endsWith("java/lang/Object;")) {
						ensureBoxedAt(i, args[args.length - i - 1]);
					}
				}
			} else {
				hasChangedDesc = true;
				String newDesc = TaintUtils.remapMethodDesc(desc);
				Type[] args = Type.getArgumentTypes(desc);

				int[] argStorage = new int[args.length];
				for (int i = 0; i < args.length; i++) {
					Type t = args[args.length - i - 1];
					int lv = lvs.getTmpLV(t);
					super.visitVarInsn(t.getOpcode(Opcodes.ISTORE), lv);
					argStorage[args.length - i - 1] = lv;
				}
				for (int i = 0; i < args.length; i++) {
					Type t = args[i];
					if (t.getSort() == Type.OBJECT) {
						super.visitVarInsn(Opcodes.ALOAD, argStorage[i]);
						if (t.getDescriptor().equals("Ljava/lang/Object;")) {
							Type top = TaintAdapter.getTypeForStackType(analyzer.stack.get(analyzer.stack.size() - 1));
							if (top.getSort() == Type.ARRAY && top.getElementType().getSort() != Type.OBJECT && top.getDimensions() == 1) {
								//need to box!
								super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MultiDTaintedArray.class), "boxIfNecessary", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
							}
						}
					} else if (t.getSort() == Type.ARRAY) {
						if (t.getDimensions() == 1 && t.getElementType().getSort() != Type.OBJECT) {
							if (Configuration.SINGLE_TAG_PER_ARRAY) {
								super.visitInsn(Configuration.NULL_TAINT_LOAD_OPCODE);
							} else {
								FrameNode fn = getCurrentFrameNode();
								super.visitVarInsn(Opcodes.ALOAD, argStorage[i]);
								Label ok = new Label();
								Label isnull = new Label();
								super.visitJumpInsn(IFNULL, isnull);
								super.visitVarInsn(Opcodes.ALOAD, argStorage[i]);
								super.visitInsn(Opcodes.ARRAYLENGTH);
								if (Configuration.MULTI_TAINTING)
									super.visitTypeInsn(Opcodes.ANEWARRAY, Configuration.TAINT_TAG_INTERNAL_NAME);
								else
									super.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
								super.visitJumpInsn(Opcodes.GOTO, ok);
								super.visitLabel(isnull);
								fn.accept(this);
								super.visitInsn(Opcodes.ACONST_NULL);
								super.visitLabel(ok);
								fn.stack = new LinkedList(fn.stack);
								fn.stack.add(Configuration.TAINT_TAG_ARRAY_INTERNAL_NAME);
								fn.accept(this);
							}
							super.visitVarInsn(Opcodes.ALOAD, argStorage[i]);
						} else
							super.visitVarInsn(Opcodes.ALOAD, argStorage[i]);
					} else {
						super.visitInsn(Configuration.NULL_TAINT_LOAD_OPCODE);
						super.visitVarInsn(t.getOpcode(Opcodes.ILOAD), argStorage[i]);
					}
					lvs.freeTmpLV(argStorage[i]);
				}
				desc = newDesc;
			}
			Type newReturnType = Type.getReturnType(desc);
			if (name.equals("<init>") && hasChangedDesc) {
				super.visitInsn(Opcodes.ACONST_NULL);
				desc = desc.substring(0, desc.indexOf(')')) + Type.getDescriptor(TaintSentinel.class) + ")" + desc.substring(desc.indexOf(')') + 1);
			} else if(!TaintUtils.PREALLOC_RETURN_ARRAY) {
				if ((origReturnType.getSort() == Type.ARRAY && origReturnType.getDimensions() == 1 && origReturnType.getElementType().getSort() != Type.OBJECT)
						|| (origReturnType.getSort() != Type.ARRAY && origReturnType.getSort() != Type.OBJECT && origReturnType.getSort() != Type.VOID)) {
					desc = desc.substring(0, desc.indexOf(')')) + newReturnType.getDescriptor() + ")" + desc.substring(desc.indexOf(')') + 1);
					super.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(newReturnType));
					name += TaintUtils.METHOD_SUFFIX;
				} else if (hasChangedDesc)
					name += TaintUtils.METHOD_SUFFIX;
			}
			if(TaintUtils.PREALLOC_RETURN_ARRAY)
			{
				desc = desc.substring(0, desc.indexOf(')')) + Configuration.TAINTED_RETURN_HOLDER_DESC+")" + desc.substring(desc.indexOf(')') + 1);
				if(!name.equals("<init>"))
					name += TaintUtils.METHOD_SUFFIX;
				analyzer.visitVarInsn(Opcodes.ALOAD, preallocArg);
			}
			super.visitMethodInsn(opcode, owner, name, desc, itf);
			if (origReturnType.getSort() == Type.ARRAY && origReturnType.getDimensions() == 1 && origReturnType.getElementType().getSort() != Type.OBJECT) {
				//unbox array
				super.visitFieldInsn(GETFIELD, newReturnType.getInternalName(), "val", origReturnType.getDescriptor());
			} else if (origReturnType.getSort() != Type.ARRAY && origReturnType.getSort() != Type.OBJECT && origReturnType.getSort() != Type.VOID) {
				//unbox prim
				super.visitFieldInsn(GETFIELD, newReturnType.getInternalName(), "val", origReturnType.getDescriptor());
			}
		} else {
			if (!name.equals("clone") && !name.equals("equals"))
				System.out.println("Call UNTOUCHED" + owner + name + desc);

			super.visitMethodInsn(opcode, owner, name, desc, itf);
		}

	}

}
