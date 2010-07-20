package org.dacapo.instrument;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.TreeMap;
import java.util.Set;

public class CallChainInstrument extends Instrument {

	private static final String   INSTRUMENT_PACKAGE     = "org/dacapo/instrument/";
	
	private static final String   LOG_INTERNAL_NAME      = "org/dacapo/instrument/Agent"; // Log
	private static final String   LOG_METHOD_NAME        = "logCallChain";
	private static final String   LOG_METHOD_SIGNATURE   = "()V"; 
	
	private static final String   LOG_INTERNAL_METHOD    = "$$" + LOG_METHOD_NAME;
	
	private int                   access      = 0;
	private ClassVisitor          cv          = null;
	private String                className   = null;
	private boolean               done        = false;
	private boolean               found       = false;
	
	public CallChainInstrument(ClassVisitor cv, TreeMap<String,Integer> methodToLargestLocal) {
		super(cv, methodToLargestLocal);
		this.cv = cv;
	}
	
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.access    = access;
		this.className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}
	
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (!done && instrument() && instrument(name,access)) {
			return new CallChainInstrumentMethod(access, name, desc, signature, exceptions, super.visitMethod(access,name,desc,signature,exceptions));
		} else {
			return super.visitMethod(access,name,desc,signature,exceptions);
		}
	}

	public void visitEnd() {
		if (!done && found) {
			Class logClass = Agent.class;
			done = true;
			try {
				GeneratorAdapter mg = new GeneratorAdapter(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, new Method(LOG_INTERNAL_METHOD, LOG_METHOD_SIGNATURE), LOG_METHOD_SIGNATURE, new Type[] {}, this);

				Label start = mg.mark();
				mg.invokeStatic(Type.getType(logClass), Method.getMethod(logClass.getMethod(LOG_METHOD_NAME)));
				mg.returnValue();
				Label end   = mg.mark();
				mg.catchException(start, end, Type.getType(Throwable.class));
				mg.returnValue();
				
				mg.endMethod();
			} catch (NoSuchMethodException nsme) {
				System.err.println("Unable to find Agent.rlogCallChain method");
				System.err.println("M:"+nsme);
				nsme.printStackTrace();
			}
		}
		
		super.visitEnd();
	}
	
	private boolean instrument() {
		return (access & Opcodes.ACC_INTERFACE) == 0 && !className.startsWith(INSTRUMENT_PACKAGE);
	}

	private boolean instrument(String name, int access) {
		return (access & Opcodes.ACC_ABSTRACT) == 0 && (access & Opcodes.ACC_BRIDGE) == 0 && (access & Opcodes.ACC_NATIVE) == 0;
	}
	
	private class CallChainInstrumentMethod extends AdviceAdapter {
		CallChainInstrumentMethod(int access, String name, String desc, String signature, String[] exceptions, MethodVisitor mv) {
			super(mv, access, name, desc);
		}
		
		protected void onMethodEnter() {
			if (done) return;

			found = true;
			
			super.visitMethodInsn(Opcodes.INVOKESTATIC, className, LOG_INTERNAL_METHOD, LOG_METHOD_SIGNATURE);
		}
	}
	
}
