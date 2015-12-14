package edu.columbia.cs.psl.phosphor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import sun.misc.VM;
import edu.columbia.cs.psl.phosphor.runtime.ArrayHelper;
import edu.columbia.cs.psl.phosphor.runtime.PreAllocHelper;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.runtime.TaintSentinel;
import edu.columbia.cs.psl.phosphor.runtime.UninstrumentedTaintSentinel;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedReturnHolderWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedReturnHolderWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArray;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArrayWithObjTag;

public class TaintUtils {
	static Object lock = new Object();

//	static
//	{
//		prealloc.set(PreAllocHelper.createPreallocReturnArray());
//	}
	public static final boolean TAINT_THROUGH_SERIALIZATION = false;
	public static final boolean OPT_PURE_METHODS = false;

	public static final boolean OPT_IGNORE_EXTRA_TAINTS = true;

	public static final boolean OPT_USE_STACK_ONLY = false; //avoid using LVs where possible if true
	
	public static final int RAW_INSN = 201;
	public static final int NO_TAINT_STORE_INSN = 202;
	public static final int IGNORE_EVERYTHING = 203;
	public static final int NO_TAINT_UNBOX = 204;
	public static final int DONT_LOAD_TAINT = 205;
	public static final int GENERATETAINT = 206;

	public static final int NEXTLOAD_IS_TAINTED = 207;
	public static final int NEXTLOAD_IS_NOT_TAINTED = 208;
	public static final int NEVER_AUTOBOX = 209;
	public static final int ALWAYS_AUTOBOX = 210;
	public static final int ALWAYS_BOX_JUMP = 211;
	public static final int ALWAYS_UNBOX_JUMP = 212;
	public static final int IS_TMP_STORE = 213;
	
	public static final int BRANCH_START = 214;
	public static final int BRANCH_END = 215;
	public static final int FORCE_CTRL_STORE = 216;

	public static final int FOLLOWED_BY_FRAME = 217;
	public static final int CUSTOM_SIGNAL_1 = 218;
	public static final int CUSTOM_SIGNAL_2 = 219;
	public static final int CUSTOM_SIGNAL_3 = 220;
	
	public static final String TAINT_FIELD = "PHOSPHOR_TAG";
//	public static final String HAS_TAINT_FIELD = "INVIVO_IS_TAINTED";
//	public static final String IS_TAINT_SEATCHING_FIELD = "INVIVO_IS_TAINT_SEARCHING";

	public static final String METHOD_SUFFIX = "$$PHOSPHORTAGGED";
	public static final boolean DEBUG_ALL = false;
	public static final boolean DEBUG_DUPSWAP = false || DEBUG_ALL;
	public static final boolean DEBUG_FRAMES = false || DEBUG_ALL;
	public static final boolean DEBUG_FIELDS = false || DEBUG_ALL;
	public static final boolean DEBUG_LOCAL = false || DEBUG_ALL;
	public static final boolean DEBUG_CALLS = false || DEBUG_ALL;
	public static final boolean DEBUG_OPT = false;
	public static final boolean DEBUG_PURE = false;

	public static boolean PREALLOC_RETURN_ARRAY = true;
	public static final int PREALLOC_BOOLEAN=0;
	public static final int PREALLOC_BYTE=1;
	public static final int PREALLOC_CHAR=2;
	public static final int PREALLOC_DOUBLE=3;
	public static final int PREALLOC_FLOAT=4;
	public static final int PREALLOC_INT=5;
	public static final int PREALLOC_LONG=6;
	public static final int PREALLOC_SHORT=7;
	public static final int PREALLOC_BOOLEANARRAY=8;
	public static final int PREALLOC_BYTEARRAY=9;
	public static final int PREALLOC_CHARARRAY=10;
	public static final int PREALLOC_DOUBLEARRAY=11;
	public static final int PREALLOC_FLOATARRAY=12;
	public static final int PREALLOC_INTARRAY=13;
	public static final int PREALLOC_LONGARRAY=14;
	public static final int PREALLOC_SHORTARRAY=15;
	
	public static final boolean ADD_BASIC_ARRAY_CONSTRAINTS = true;
	public static final boolean ADD_HEAVYWEIGHT_ARRAY_TAINTS = ADD_BASIC_ARRAY_CONSTRAINTS || true;

	public static int nextTaint = 0;
	public static int nextTaintPHOSPHOR_TAG = 0;

	public static int nextMethodId = 0;


	public static final int MAX_CONCURRENT_BRANCHES = 500;

	public static final String STR_LDC_WRAPPER = "INVIVO_LDC_STR";

	public static final int UNCONSTRAINED_NEW_STRING = 4;

	public static final boolean VERIFY_CLASS_GENERATION = true;

	public static final String METHOD_SUFFIX_UNINST = "$$PHOSPHORUNTAGGED";

	public static final int getPreAllocArrayIdxForType(Type t)
	{
		switch(t.getSort())
		{
		case Type.BOOLEAN:
			return PREALLOC_BOOLEAN;
		case Type.BYTE:
			return PREALLOC_BYTE;
		case Type.CHAR:
			return PREALLOC_CHAR;
		case Type.DOUBLE:
			return PREALLOC_DOUBLE;
		case Type.FLOAT:
			return PREALLOC_FLOAT;
		case Type.INT:
			return PREALLOC_INT;
		case Type.LONG:
			return PREALLOC_LONG;
		case Type.SHORT:
			return PREALLOC_SHORT;
		case Type.ARRAY:
			switch (t.getElementType().getSort()) {
			case Type.BOOLEAN:
				return PREALLOC_BOOLEANARRAY;
			case Type.BYTE:
				return PREALLOC_BYTEARRAY;
			case Type.CHAR:
				return PREALLOC_CHARARRAY;
			case Type.DOUBLE:
				return PREALLOC_DOUBLEARRAY;
			case Type.FLOAT:
				return PREALLOC_FLOATARRAY;
			case Type.INT:
				return PREALLOC_INTARRAY;
			case Type.LONG:
				return PREALLOC_LONGARRAY;
			case Type.SHORT:
				return PREALLOC_SHORTARRAY;
			}
		}
		return -1;
	}
	
	/*
	 * Start: Conversion of method signature from doop format to bytecode format
	 */
	
	private static Map<String, String> typeToSymbol;

	static void initDoopConversion() {
		doopTypeToSymbolinited =  true;
		typeToSymbol = new HashMap<String, String>();

		typeToSymbol.put("byte", "B");
		typeToSymbol.put("char", "C");
		typeToSymbol.put("double", "D");
		typeToSymbol.put("float", "F");
		typeToSymbol.put("int", "I");
		typeToSymbol.put("long", "J");
		typeToSymbol.put("short", "S");
		typeToSymbol.put("void", "V");
		typeToSymbol.put("boolean", "Z");
	}
	static boolean doopTypeToSymbolinited;
	private static final String processSingleType(String in)
	{
		if(in.equals("byte"))
			return "B";
		else if(in.equals("char"))
			return "C";
		else if(in.equals("double"))
			return "D";
		else if(in.equals("float"))
			return "F";
		else if(in.equals("int"))
			return "I";
		else if(in.equals("long"))
			return "J";
		else if(in.equals("short"))
			return "S";
		else if(in.equals("void"))
			return "V";
		else if(in.equals("boolean"))
			return "Z";
		return "L"+in.replace('.', '/')+";";
	}
	private static String processType(String type) {
		StringBuffer typeBuffer = new StringBuffer();
		type = type.trim();
		int firstBracket = type.indexOf('[');
		if(firstBracket >= 0) {
			for(int i = firstBracket; i < type.length();i+=2)
				typeBuffer.append("[");
			type = type.substring(0,firstBracket);
			typeBuffer.append(processSingleType(type));
		}
		else
			typeBuffer.append(processSingleType(type));
		return typeBuffer.toString();
	}
	
	private static String processReverse(String type) {
		if(!doopTypeToSymbolinited)
			initDoopConversion();
		type = type.trim();
		if(type.length() == 1)  {
			for(String s : typeToSymbol.keySet()) 
				if(typeToSymbol.get(s).equals(type))
					return s;
			throw new IllegalArgumentException("Invalid type string");
		}
			
		if(type.startsWith("[")) {
			// is an array
			int idx = 0;
			String suffix = "";
			while(type.charAt(idx) == '[') {
				idx++;
				suffix = suffix+"[]";
			}
			return processReverse(type.substring(idx))+suffix;
			
		} else {
			type = type.replaceAll("/", ".");
			type = type.substring(1, type.length()-1); //remove L and ;
			return type;
		}
	}
	public static void main(String[] args) {
		System.out.println(getMethodDesc("<java.lang.Runtime: java.lang.Process[][][] exec(java.lang.String,java.lang.String[],java.io.File)>"));
	}
	//<java.lang.Runtime: java.lang.Process[][][] exec(java.lang.String,java.lang.String[],java.io.File)>
	public static MethodDescriptor getMethodDesc(String signature) {
		// get return type
		char[] chars = signature.toCharArray();
		String[] parts = signature.split(": ");
		int idxOfColon = signature.indexOf(':');
		String temp = signature.substring(idxOfColon+2);
		int nameStart = temp.indexOf(' ')+1;
		int nameEnd = temp.indexOf('(');
		String owner = signature.substring(1,idxOfColon).replace('.', '/');
		String name = temp.substring(nameStart,nameEnd);
		
		String returnTypeSymbol = processType(temp.substring(0, temp.indexOf(" ")).trim());
		 
		// get args list
		temp = temp.substring(nameEnd+1,temp.length()-2);
		StringBuffer argsBuffer = new StringBuffer();
	
		argsBuffer.append("(");
		if(temp != null && !temp.isEmpty()) {
			for(String arg : temp.split(",")) 
				argsBuffer.append(processType(arg.trim()));
		}
		argsBuffer.append(")");
	
		argsBuffer.append(returnTypeSymbol);
		return new MethodDescriptor(name, owner, argsBuffer.toString());
	}
	
	public static String getMethodDesc(MethodDescriptor desc) {
		String owner = desc.getOwner().replaceAll("/", ".");
		String methodName = desc.getName();
		String returnType = desc.getDesc().substring(desc.getDesc().indexOf(")")+1);
		String actualReturnType = processReverse(returnType);
		String args = desc.getDesc().substring(desc.getDesc().indexOf("(")+1, desc.getDesc().indexOf(")"));
		boolean noargs = (args.length() == 0);
		int idx = 0;
		List<String> arguments = new ArrayList<String>(); 
		while(args.length() > 0) {
			idx = 0;
			if(args.charAt(idx) == 'L') {
				arguments.add(processReverse(args.substring(idx, args.indexOf(";")+1)));
				idx=args.indexOf(";")+1;
			} else if(args.charAt(idx) == '[') {
				while(args.charAt(idx) == '[') 
					idx++;
				if(args.charAt(idx) == 'L') {
					arguments.add(processReverse(args.substring(0,args.indexOf(";")+1)));
					idx=args.indexOf(";")+1; 
				} else {
					arguments.add(processReverse(args.substring(0,idx+1)));
					idx=idx+1;
				}
			} else {
				arguments.add(processReverse(args.charAt(idx)+""));
				idx=idx+1;
			}
			args = args.substring(idx);
		}
		StringBuffer buf = new StringBuffer();
		buf.append("<").append(owner).append(": ").append(actualReturnType).append(" ").append(methodName).append("(");
		for(String s : arguments)
			buf.append(s).append(",");
		if(!noargs)
			buf.setLength(buf.length()-1);
		buf.append(")>");
		return buf.toString();
	}
	
	public static void writeToFile(File file, String content) {
		FileOutputStream fop = null;
		
		try {
			fop = new FileOutputStream(file); 
			if (!file.exists()) 
				file.createNewFile();		 
			byte[] contentInBytes = content.getBytes();
 			fop.write(contentInBytes);
			fop.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * End: Conversion of method signature from doop format to bytecode format
	 */
	
	public static boolean isPreAllocReturnType(String methodDescriptor)
	{
		Type retType = Type.getReturnType(methodDescriptor);
		if(TaintUtils.PREALLOC_RETURN_ARRAY)
			return true;
		if(retType.getSort() == Type.OBJECT || retType.getSort() == Type.VOID)
			return false;
		if(retType.getSort() == Type.ARRAY && retType.getElementType().getSort() != Type.OBJECT && retType.getDimensions() == 1)
			return true;
		else if(retType.getSort() == Type.ARRAY)
			return false;
		return true;
	}
	public static boolean isNotRealArg(Type t)
	{
		if(t.equals(Type.getType(TaintSentinel.class)))
			return true;
		if(t.equals(Type.getType(UninstrumentedTaintSentinel.class)))
			return true;
		if(t.getInternalName().startsWith("edu/columbia/cs/psl/"))
		{
			try {
				Class c = Class.forName(t.getInternalName().replace("/", "."));
				if(TaintedPrimitiveWithIntTag.class.isAssignableFrom(c) || TaintedPrimitiveArrayWithIntTag.class.isAssignableFrom(c))
					return true;
			} catch (ClassNotFoundException e) {
			}
		}
		return false;
	}
	public static boolean arrayHasTaints(int[] a) {
		for (int i : a)
			if (i != 0)
				return true;
		return false;
	}

	public static boolean arrayHasTaints(int[][] a) {
		for (int[] j : a)
			for (int i : j)
				if (i != 0)
					return true;
		return false;
	}

	public static boolean arrayHasTaints(int[][][] a) {
		for (int[][] k : a)
			for (int[] j : k)
				for (int i : j)
					if (i != 0)
						return true;
		return false;
	}


	public static int getTaintInt(Object obj) {
		if(obj == null)
			return 0;
		if (obj instanceof TaintedWithIntTag) {
			return ((TaintedWithIntTag) obj).getPHOSPHOR_TAG();
		}
		else if(obj instanceof int[])
		{
			int ret = 0;
			for(int i : ((int[])obj))
			{
				ret |=i;
			}
			return ret;
		}
		else if(obj instanceof Object[])
		{
			int ret = 0;
			for(Object o : ((Object[]) obj))
				ret |= getTaintInt(o);
			return ret;
		}
		else if(obj instanceof MultiDTaintedArrayWithIntTag)
		{
			int ret = 0;
			for(int i : ((MultiDTaintedArrayWithIntTag) obj).taint)
				ret |= i;
			return ret;
		}
//		if(BoxedPrimitiveStoreWithIntTags.tags.containsKey(obj))
//			return BoxedPrimitiveStoreWithIntTags.tags.get(obj);
		return 0;
	}

	public static Object getTaintObj(Object obj) {
		if(obj == null || Taint.IGNORE_TAINTING)
			return null;
		if (obj instanceof TaintedWithObjTag) {
			return ((TaintedWithObjTag) obj).getPHOSPHOR_TAG();
		}
		else if(ArrayHelper.engaged == 1)
		{
			return ArrayHelper.getTag(obj);
		}
		else if(obj instanceof Taint[])
		{
			Taint ret = new Taint();
			for(Taint t: ((Taint[]) obj))
			{
				if(t != null)
					ret.addDependency(t);
			}
			if(ret.hasNoDependencies())
				return null;
			return ret;
		}
		else if(obj instanceof MultiDTaintedArrayWithObjTag)
		{
			Taint ret = new Taint();
			for(Object t: ((MultiDTaintedArrayWithObjTag) obj).taint)
			{
				if(t != null)
					ret.addDependency((Taint) t);
			}
			if(ret.hasNoDependencies())
				return null;
			return ret;
		}
		else if(obj instanceof Object[])
		{
			Taint ret = new Taint();
			for(Object o : (Object[]) obj)
			{
				Taint t = (Taint) getTaintObj(o);
				if(t != null)
					ret.addDependency(t);
			}
			if(ret.hasNoDependencies())
				return null;
			return ret;
		}
//		if(BoxedPrimitiveStoreWithObjTags.tags.containsKey(obj))
//			return BoxedPrimitiveStoreWithObjTags.tags.get(obj);
		return null;
	}

	
	public static int[][] create2DTaintArray(Object in, int[][] ar) {
		for (int i = 0; i < Array.getLength(in); i++) {
			Object entry = Array.get(in, i);
			if (entry != null)
				ar[i] = new int[Array.getLength(entry)];
		}
		return ar;
	}

	public static int[][][] create3DTaintArray(Object in, int[][][] ar) {
		for (int i = 0; i < Array.getLength(in); i++) {
			Object entry = Array.get(in, i);
			if (entry != null) {
				ar[i] = new int[Array.getLength(entry)][];
				for (int j = 0; j < Array.getLength(entry); j++) {
					Object e = Array.get(entry, j);
					if (e != null)
						ar[i][j] = new int[Array.getLength(e)];
				}
			}
		}
		return ar;
	}

	public static void generateMultiDTaintArray(Object in, Object taintRef) {
		//Precondition is that taintArrayRef is an array with the same number of dimensions as obj, with each allocated.
		for (int i = 0; i < Array.getLength(in); i++) {
			Object entry = Array.get(in, i);
			Class<?> clazz = entry.getClass();
			if (clazz.isArray()) {
				//Multi-D array
				int innerDims = Array.getLength(entry);
				Array.set(taintRef, i, Array.newInstance(Integer.TYPE, innerDims));
			}
		}
	}


	public static boolean OKtoDebug = false;
	public static int OKtoDebugPHOSPHOR_TAG;

	public static void arraycopy(Object src, int srcPosTaint, int srcPos, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		try {
			if (!src.getClass().isArray() && !dest.getClass().isArray()) {
				System.arraycopy(((MultiDTaintedArrayWithIntTag) src).getVal(), srcPos, ((MultiDTaintedArrayWithIntTag) dest).getVal(), destPos, length);
				System.arraycopy(((MultiDTaintedArrayWithIntTag) src).taint, srcPos, ((MultiDTaintedArrayWithIntTag) dest).taint, destPos, length);
			} else if (!dest.getClass().isArray()) {
				//src is a regular array, dest is multidtaintedarraywithinttag
				System.arraycopy(src, srcPos, ((MultiDTaintedArrayWithIntTag) dest).getVal(), destPos, length);
			} else {
				System.arraycopy(src, srcPos, dest, destPos, length);
			}
		} catch (ArrayStoreException ex) {
			System.out.println("Src " + src);
			System.out.println(((Object[]) src)[0]);
			System.out.println("Dest " + dest);
			ex.printStackTrace();
			throw ex;
		}
	}
	public static void arraycopy(Object src, int srcPosTaint, int srcPos, Object dest, int destPosTaint, int destPos, int lengthTaint, int length, TaintedReturnHolderWithIntTag[] preqlloc) {
		try {
			if (!src.getClass().isArray() && !dest.getClass().isArray()) {
				System.arraycopy(((MultiDTaintedArrayWithIntTag) src).getVal(), srcPos, ((MultiDTaintedArrayWithIntTag) dest).getVal(), destPos, length);
				System.arraycopy(((MultiDTaintedArrayWithIntTag) src).taint, srcPos, ((MultiDTaintedArrayWithIntTag) dest).taint, destPos, length);
			} else if (!dest.getClass().isArray()) {
				//src is a regular array, dest is multidtaintedarraywithinttag
				System.arraycopy(src, srcPos, ((MultiDTaintedArrayWithIntTag) dest).getVal(), destPos, length);
			} else {
				System.arraycopy(src, srcPos, dest, destPos, length);
			}
		} catch (ArrayStoreException ex) {
			System.out.println("Src " + src);
			System.out.println(((Object[]) src)[0]);
			System.out.println("Dest " + dest);
			ex.printStackTrace();
			throw ex;
		}
	}
	public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
		try{
		if(src instanceof MultiDTaintedArrayWithIntTag)
		{
			System.arraycopy(((MultiDTaintedArrayWithIntTag)src).getVal(), srcPos, ((MultiDTaintedArrayWithIntTag)dest).getVal(), destPos, length);
			System.arraycopy(((MultiDTaintedArrayWithIntTag)src).taint, srcPos, ((MultiDTaintedArrayWithIntTag)dest).taint, destPos, length);
		}
//		else if(!dest.getClass().isArray())
//		{
//			//src is a regular array, dest is multidtaintedarraywithinttag
//			System.arraycopy(src, srcPos, ((MultiDTaintedArrayWithIntTag)dest).getVal(), destPos, length);
//		}
//		else
			System.arraycopy(src, srcPos, dest, destPos, length);
		}
		catch(ArrayStoreException ex)
		{
			System.out.println("Src " + src);
			System.out.println(((Object[])src)[0]);
			System.out.println("Dest " + dest);
			ex.printStackTrace();
			throw ex;
		}
	}
	
	public static void arraycopy(Object src, Object srcPosTaint, int srcPos, Object dest, Object destPosTaint, int destPos, Object lengthTaint, int length) {
		if(!src.getClass().isArray() && !dest.getClass().isArray())
		{
			System.arraycopy(((MultiDTaintedArrayWithObjTag)src).getVal(), srcPos, ((MultiDTaintedArrayWithObjTag)dest).getVal(), destPos, length);
			if(!Configuration.SINGLE_TAG_PER_ARRAY)
				System.arraycopy(((MultiDTaintedArrayWithObjTag)src).taint, srcPos, ((MultiDTaintedArrayWithObjTag)dest).taint, destPos, length);
		}
		else if(!dest.getClass().isArray())
		{
			System.arraycopy(src, srcPos, ((MultiDTaintedArrayWithObjTag)dest).getVal(), destPos, length);
		}
		else
			System.arraycopy(src, srcPos, dest, destPos, length);
	}
	public static void arraycopy(Object src, Object srcPosTaint, int srcPos, Object dest, Object destPosTaint, int destPos, Object lengthTaint, int length, Object[] prealloc) {
		if(!src.getClass().isArray() && !dest.getClass().isArray())
		{
			System.arraycopy(((MultiDTaintedArrayWithObjTag)src).getVal(), srcPos, ((MultiDTaintedArrayWithObjTag)dest).getVal(), destPos, length);
			if(!Configuration.SINGLE_TAG_PER_ARRAY)
				System.arraycopy(((MultiDTaintedArrayWithObjTag)src).taint, srcPos, ((MultiDTaintedArrayWithObjTag)dest).taint, destPos, length);
		}
		else if(!dest.getClass().isArray())
		{
			System.arraycopy(src, srcPos, ((MultiDTaintedArrayWithObjTag)dest).getVal(), destPos, length);
		}
		else
			System.arraycopy(src, srcPos, dest, destPos, length);
	}


	public static void arraycopyVM(Object src, int srcPosTaint, int srcPos, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		if(!src.getClass().isArray())
		{
			VMSystem.arraycopy0(((MultiDTaintedArrayWithIntTag)src).getVal(), srcPos, ((MultiDTaintedArrayWithIntTag)dest).getVal(), destPos, length);
			VMSystem.arraycopy0(((MultiDTaintedArrayWithIntTag)src).taint, srcPos, ((MultiDTaintedArrayWithIntTag)dest).taint, destPos, length);
		}
		else
			VMSystem.arraycopy0(src, srcPos, dest, destPos, length);
	}
	
	public static void arraycopy(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		throw new ArrayStoreException("Can't copy from src with taint to dest w/o taint!");
	}

	public static void arraycopy(Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		throw new ArrayStoreException("Can't copy from src w/ no taint to dest w/ taint!!");
	}

	public static boolean weakHashMapInitialized = false;

	public static void arraycopy(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (!Configuration.SINGLE_TAG_PER_ARRAY && 
				VM.booted && srcTaint != null && destTaint != null) {
			if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}
	public static void arraycopy(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length, TaintedReturnHolderWithIntTag[] prealloc) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (VM.booted && srcTaint != null && destTaint != null) {
			if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}
	public static void arraycopy(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length, TaintedReturnHolderWithObjTag[] prealloc) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (!Configuration.SINGLE_TAG_PER_ARRAY && (VM.booted) && srcTaint != null && destTaint != null) {
			if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}

	public static void arraycopyControlTrack(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (VM.booted && srcTaint != null && destTaint != null) {
			if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}
	
	public static void arraycopy(Object srcTaint, Object src, Object srcPosTaint, int srcPos, Object destTaint, Object dest, Object destPosTaint, int destPos, Object lengthTaint, int length, Object[] prealloc) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (VM.booted && 
				srcTaint != null && destTaint != null) {
			if(Configuration.SINGLE_TAG_PER_ARRAY || (destTaint instanceof Taint))
			{
				Taint _srcT = (Taint) srcTaint;
				Taint _destT = (Taint) destTaint;
				if(_srcT.lbl != Taint.EMPTY)
				{
					_destT.lbl = null;
					_destT.addDependency(_srcT);
				}
			}
			else if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}
	public static void arraycopy(Object srcTaint, Object src, Object srcPosTaint, int srcPos, Object destTaint, Object dest, Object destPosTaint, int destPos, Object lengthTaint, int length) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (VM.isBooted$$PHOSPHORTAGGED(new TaintedBooleanWithObjTag()).val && srcTaint != null && destTaint != null) {
			if(Configuration.SINGLE_TAG_PER_ARRAY || (destTaint instanceof Taint))
			{
				Taint _srcT = (Taint) srcTaint;
				Taint _destT = (Taint) destTaint;
				if(_srcT.lbl != Taint.EMPTY)
				{
					_destT.lbl = null;
					_destT.addDependency(_srcT);
				}
			}
			else if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}
	public static void arraycopyControlTrack(Object srcTaint, Object src, Object srcPosTaint, int srcPos, Object destTaint, Object dest, Object destPosTaint, int destPos, Object lengthTaint, int length) {
		System.arraycopy(src, srcPos, dest, destPos, length);
		if (!Configuration.SINGLE_TAG_PER_ARRAY && VM.isBooted$$PHOSPHORTAGGED(new ControlTaintTagStack(), new TaintedBooleanWithObjTag()).val && srcTaint != null && destTaint != null) {
			if (srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
				System.arraycopy(srcTaint, srcPos, destTaint, destPos, length);
		}
	}
	
	public static void arraycopyVM(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
		VMSystem.arraycopy0(src, srcPos, dest, destPos, length);
		
//		if (VM.isBooted$$PHOSPHORTAGGED(new TaintedBoolean()).val && srcTaint != null && destTaint != null) {
//			if(srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
//		System.out.println(src);
//		System.out.println(srcTaint);
		if(srcTaint != null && destTaint != null && srcTaint.getClass() == destTaint.getClass())
			VMSystem.arraycopy0(srcTaint, srcPos, destTaint, destPos, length);
//		}

	}

//	public static void arraycopyHarmony(Object src, int srcPosTaint, int srcPos, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
//		if(!src.getClass().isArray())
//		{
//			VMMemoryManager.arrayCopy(((MultiDTaintedArray)src).getVal(), srcPos, ((MultiDTaintedArray)dest).getVal(), destPos, length);
//			VMMemoryManager.arrayCopy(((MultiDTaintedArray)src).taint, srcPos, ((MultiDTaintedArray)dest).taint, destPos, length);
//		}
//		else
//		{
//			VMMemoryManager.arrayCopy(src, srcPos, dest, destPos, length);
//		}
////		dest = src;
//	}
//	public static void arraycopyHarmony(Object srcTaint, Object src, int srcPosTaint, int srcPos, Object destTaint, Object dest, int destPosTaint, int destPos, int lengthTaint, int length) {
////		System.err.println("OK");
//		VMMemoryManager.arrayCopy(src, srcPos, dest, destPos, length);
////		System.arraycopy(src, srcPos, dest, destPos, length);
////		dest = src;
////		if (VM.isBooted$$PHOSPHORTAGGED(new TaintedBoolean()).val && srcTaint != null && destTaint != null) {
////			if(srcPos == 0 && length <= Array.getLength(destTaint) && length <= Array.getLength(srcTaint))
////		System.out.println(src);
////		System.out.println(srcTaint);
//		if(srcTaint != null && destTaint != null && srcTaint.getClass() == destTaint.getClass())
//			VMMemoryManager.arrayCopy(srcTaint, srcPos, destTaint, destPos, length);
////		}
//
//	}
	public static Object getShadowTaintTypeForFrame(String typeDesc) {
		Type t = Type.getType(typeDesc);
		if (t.getSort() == Type.OBJECT || t.getSort() == Type.VOID)
			return null;
		if(t.getSort() == Type.ARRAY && t.getDimensions() > 1)
			return null;
		if (t.getSort() == Type.ARRAY && t.getElementType().getSort() != Type.OBJECT)
			return Configuration.TAINT_TAG_ARRAY_STACK_TYPE;
		if (t.getSort() == Type.ARRAY)
			return null;
		return Configuration.TAINT_TAG_STACK_TYPE;
	}
	public static String getShadowTaintType(String typeDesc) {
		Type t = Type.getType(typeDesc);
		if (t.getSort() == Type.OBJECT || t.getSort() == Type.VOID)
			return null;
		if (t.getSort() == Type.ARRAY && t.getDimensions() > 1)
			return null;
		if (t.getSort() == Type.ARRAY && t.getElementType().getSort() != Type.OBJECT)
			if (Configuration.SINGLE_TAG_PER_ARRAY)
				return Configuration.TAINT_TAG_DESC;
			else
				return Configuration.TAINT_TAG_ARRAYDESC;
		if (t.getSort() == Type.ARRAY)
			return null;
		return Configuration.TAINT_TAG_DESC;
	}

	public static Type getContainerReturnType(String originalReturnType) {
		return getContainerReturnType(Type.getType(originalReturnType));
	}

	public static Type getContainerReturnType(Type originalReturnType) {
		if(!Configuration.MULTI_TAINTING)
		{
			switch (originalReturnType.getSort()) {
			case Type.BYTE:
				return Type.getType(TaintedByteWithIntTag.class);
			case Type.BOOLEAN:
				return Type.getType(TaintedBooleanWithIntTag.class);
			case Type.CHAR:
				return Type.getType(TaintedCharWithIntTag.class);
			case Type.DOUBLE:
				return Type.getType(TaintedDoubleWithIntTag.class);
			case Type.FLOAT:
				return Type.getType(TaintedFloatWithIntTag.class);
			case Type.INT:
				return Type.getType(TaintedIntWithIntTag.class);
			case Type.LONG:
				return Type.getType(TaintedLongWithIntTag.class);
			case Type.SHORT:
				return Type.getType(TaintedShortWithIntTag.class);
			case Type.ARRAY:
				if (originalReturnType.getDimensions() > 1)
				{
					
					switch (originalReturnType.getElementType().getSort()) {
					case Type.BYTE:
					case Type.BOOLEAN:
					case Type.CHAR:
					case Type.DOUBLE:
					case Type.FLOAT:
					case Type.INT:
					case Type.LONG:
					case Type.SHORT:
						return MultiDTaintedArrayWithIntTag.getTypeForType(originalReturnType);
					case Type.OBJECT:
						return originalReturnType;
					}
				}
				switch (originalReturnType.getElementType().getSort()) {
				case Type.OBJECT:
					return originalReturnType;
				case Type.BYTE:
					return Type.getType(TaintedByteArrayWithIntTag.class);
				case Type.BOOLEAN:
					return Type.getType(TaintedBooleanArrayWithIntTag.class);
				case Type.CHAR:
					return Type.getType(TaintedCharArrayWithIntTag.class);
				case Type.DOUBLE:
					return Type.getType(TaintedDoubleArrayWithIntTag.class);
				case Type.FLOAT:
					return Type.getType(TaintedFloatArrayWithIntTag.class);
				case Type.INT:
					return Type.getType(TaintedIntArrayWithIntTag.class);
				case Type.LONG:
					return Type.getType(TaintedLongArrayWithIntTag.class);
				case Type.SHORT:
					return Type.getType(TaintedShortArrayWithIntTag.class);
				default:
					return Type.getType("[" + getContainerReturnType(originalReturnType.getElementType()).getDescriptor());
				}
				
			default:
				return originalReturnType;
			}
		}
		else
		{
			switch (originalReturnType.getSort()) {
			case Type.BYTE:
				return Type.getType(TaintedByteWithObjTag.class);
			case Type.BOOLEAN:
				return Type.getType(TaintedBooleanWithObjTag.class);
			case Type.CHAR:
				return Type.getType(TaintedCharWithObjTag.class);
			case Type.DOUBLE:
				return Type.getType(TaintedDoubleWithObjTag.class);
			case Type.FLOAT:
				return Type.getType(TaintedFloatWithObjTag.class);
			case Type.INT:
				return Type.getType(TaintedIntWithObjTag.class);
			case Type.LONG:
				return Type.getType(TaintedLongWithObjTag.class);
			case Type.SHORT:
				return Type.getType(TaintedShortWithObjTag.class);
			case Type.ARRAY:
				if (originalReturnType.getDimensions() > 1)
				{
					
					switch (originalReturnType.getElementType().getSort()) {
					case Type.BYTE:
					case Type.BOOLEAN:
					case Type.CHAR:
					case Type.DOUBLE:
					case Type.FLOAT:
					case Type.INT:
					case Type.LONG:
					case Type.SHORT:
						return MultiDTaintedArrayWithObjTag.getTypeForType(originalReturnType);
					case Type.OBJECT:
						return originalReturnType;
					}
				}
				if (Configuration.SINGLE_TAG_PER_ARRAY) {
					switch (originalReturnType.getElementType().getSort()) {
					case Type.OBJECT:
						return originalReturnType;
					case Type.BYTE:
						return Type.getType(TaintedByteArrayWithSingleObjTag.class);
					case Type.BOOLEAN:
						return Type.getType(TaintedBooleanArrayWithSingleObjTag.class);
					case Type.CHAR:
						return Type.getType(TaintedCharArrayWithSingleObjTag.class);
					case Type.DOUBLE:
						return Type.getType(TaintedDoubleArrayWithSingleObjTag.class);
					case Type.FLOAT:
						return Type.getType(TaintedFloatArrayWithSingleObjTag.class);
					case Type.INT:
						return Type.getType(TaintedIntArrayWithSingleObjTag.class);
					case Type.LONG:
						return Type.getType(TaintedLongArrayWithSingleObjTag.class);
					case Type.SHORT:
						return Type.getType(TaintedShortArrayWithSingleObjTag.class);
					default:
						return Type.getType("[" + getContainerReturnType(originalReturnType.getElementType()).getDescriptor());
					}
				} else {
					switch (originalReturnType.getElementType().getSort()) {
					case Type.OBJECT:
						return originalReturnType;
					case Type.BYTE:
						return Type.getType(TaintedByteArrayWithObjTag.class);
					case Type.BOOLEAN:
						return Type.getType(TaintedBooleanArrayWithObjTag.class);
					case Type.CHAR:
						return Type.getType(TaintedCharArrayWithObjTag.class);
					case Type.DOUBLE:
						return Type.getType(TaintedDoubleArrayWithObjTag.class);
					case Type.FLOAT:
						return Type.getType(TaintedFloatArrayWithObjTag.class);
					case Type.INT:
						return Type.getType(TaintedIntArrayWithObjTag.class);
					case Type.LONG:
						return Type.getType(TaintedLongArrayWithObjTag.class);
					case Type.SHORT:
						return Type.getType(TaintedShortArrayWithObjTag.class);
					default:
						return Type.getType("[" + getContainerReturnType(originalReturnType.getElementType()).getDescriptor());
					}
				}
			default:
				return originalReturnType;
			}
		}
	}

	public static String remapMethodDesc(String desc) {
		String r = "(";
		for (Type t : Type.getArgumentTypes(desc)) {
			if (t.getSort() == Type.ARRAY) {
				if (t.getElementType().getSort() != Type.OBJECT && t.getDimensions() == 1)
					r += getShadowTaintType(t.getDescriptor());
			} else if (t.getSort() != Type.OBJECT) {
				r += getShadowTaintType(t.getDescriptor());
			}
			if(t.getSort() == Type.ARRAY && t.getElementType().getSort()!= Type.OBJECT && t.getDimensions() > 1)
			{
				r += MultiDTaintedArray.getTypeForType(t);
			}
			else
				r += t;
		}
		if(Configuration.IMPLICIT_TRACKING)
			r += Type.getDescriptor(ControlTaintTagStack.class);
		r += ")" + getContainerReturnType(Type.getReturnType(desc)).getDescriptor();
		return r;
	}

	public static String remapMethodDescForUninst(String desc) {
		String r = "(";
		for (Type t : Type.getArgumentTypes(desc)) {
			if(t.getSort() == Type.ARRAY && t.getElementType().getSort()!= Type.OBJECT && t.getDimensions() > 1)
			{
				r += MultiDTaintedArray.getTypeForType(t);
			}
			else
				r += t;
		}
		if(TaintUtils.PREALLOC_RETURN_ARRAY)
			r += Configuration.TAINTED_RETURN_HOLDER_DESC;
		Type ret = Type.getReturnType(desc);
		if(ret.getSort() == Type.ARRAY && ret.getDimensions() > 1 && ret.getElementType().getSort() != Type.OBJECT)
			r += ")"+MultiDTaintedArrayWithIntTag.getTypeForType(ret).getDescriptor();
		else
		r += ")" + ret.getDescriptor();
		return r;
	}
	
	public static Object getStackTypeForType(Type t)
	{
		switch(t.getSort())
		{
		case Type.ARRAY:
		case Type.OBJECT:
			return t.getInternalName();
		case Type.BYTE:
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.SHORT:
		case Type.INT:
			return Opcodes.INTEGER;
		case Type.DOUBLE:
			return Opcodes.DOUBLE;
		case Type.FLOAT:
			return Opcodes.FLOAT;
		case Type.LONG:
			return Opcodes.LONG;

			default:
				throw new IllegalArgumentException("Got: "+t);
		}
	}


	public static Object[] newTaintArray(int len)
	{
		return (Object[]) Array.newInstance(Configuration.TAINT_TAG_OBJ_CLASS, len);
	}
	private static <T> T shallowClone(T obj)
	{
		try{
			Method m =  obj.getClass().getDeclaredMethod("clone");
			m.setAccessible(true);
			return (T) m.invoke(obj);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}
	public static <T extends Enum<T>> T enumValueOf(Class<T> enumType, String name) {
		T ret = Enum.valueOf(enumType, name);
		if (((Object)name) instanceof TaintedWithIntTag) {
			int tag = ((TaintedWithIntTag) ((Object)name)).getPHOSPHOR_TAG();
			if (tag != 0) {
				ret = shallowClone(ret);
				((TaintedWithIntTag) ret).setPHOSPHOR_TAG(tag);
			}
		} else if (((Object)name) instanceof TaintedWithObjTag) {
			Object tag = ((TaintedWithObjTag) ((Object)name)).getPHOSPHOR_TAG();
			if (tag != null) {
				ret = shallowClone(ret);
				((TaintedWithObjTag) ret).setPHOSPHOR_TAG(tag);
			}
		}
		return ret;
	}
	public static <T extends Enum<T>> T enumValueOf(Class<T> enumType, String name, Object[] prealloc) {
		return enumValueOf(enumType, name);
	}
	public static <T extends Enum<T>> T enumValueOf(Class<T> enumType, String name, ControlTaintTagStack ctrl) {
		T ret = Enum.valueOf(enumType, name);
		Taint tag = (Taint) ((TaintedWithObjTag) ((Object)name)).getPHOSPHOR_TAG();
		tag = Taint.combineTags(tag, ctrl);
		if (tag != null && !(tag.getLabel() == null && tag.hasNoDependencies())) {
			ret = shallowClone(ret);
			((TaintedWithObjTag) ret).setPHOSPHOR_TAG(tag);
		}
		return ret;
	}

	public static Object ensureUnboxed(Object o)
	{
		if(o instanceof MultiDTaintedArrayWithIntTag)
			return ((MultiDTaintedArrayWithIntTag) o).getVal();
		else if(o instanceof MultiDTaintedArrayWithObjTag)
			return ((MultiDTaintedArrayWithObjTag) o).getVal();
		else if(o instanceof Enum<?>)
			return ((Enum) o).valueOf(((Enum) o).getDeclaringClass(), ((Enum) o).name());
		return o;
	}
}
