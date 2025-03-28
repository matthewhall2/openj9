/*[INCLUDE-IF JAVA_SPEC_VERSION >= 8]*/
/*
 * Copyright IBM Corp. and others 2009
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] https://openjdk.org/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 OR GPL-2.0-only WITH OpenJDK-assembly-exception-1.0
 */
package openj9.internal.tools.attach.target;

import com.ibm.oti.vm.VM;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
/*[IF JAVA_SPEC_VERSION < 24]*/
import java.security.AccessController;
import java.security.PrivilegedAction;
/*[ENDIF] JAVA_SPEC_VERSION < 24 */
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;

/*[IF JAVA_SPEC_VERSION >= 9]*/
import jdk.internal.vm.VMSupport;
/*[ELSE] JAVA_SPEC_VERSION >= 9 */
import sun.misc.VMSupport;
/*[ENDIF] JAVA_SPEC_VERSION >= 9 */
import static openj9.internal.tools.attach.target.IPC.LOCAL_CONNECTOR_ADDRESS;

/*[IF CRIU_SUPPORT]*/
import openj9.internal.criu.NotCheckpointSafe;
/*[ENDIF] CRIU_SUPPORT */

/**
 * This class handles established connections initiated by another VM
 *
 */
final class Attachment extends Thread implements Response {

	/**
	 * List of exceptions encountered
	 */
	private Exception lastError;
	private OutputStream responseStream;
	private Socket attacherSocket;
	private final int portNumber;
	private InputStream commandStream;
	private String attachError;
	private final AttachHandler handler;
	private final String key;
	private static final String START_REMOTE_MANAGEMENT_AGENT = "startRemoteManagementAgent"; //$NON-NLS-1$
	private static final String START_LOCAL_MANAGEMENT_AGENT = "startLocalManagementAgent"; //$NON-NLS-1$

	private static final class MethodRefsHolder {
		static Method startLocalManagementAgentMethod = null;
		static Method startRemoteManagementAgentMethod = null;
		static final Throwable managementAgentMethodThrowable;
		static {
			/*[IF JAVA_SPEC_VERSION < 24]*/
			managementAgentMethodThrowable = AccessController.doPrivileged((PrivilegedAction<Throwable>) () -> {
			/*[ENDIF] JAVA_SPEC_VERSION < 24 */
				String agentClassName =
						/*[IF JAVA_SPEC_VERSION >= 9]*/
						"jdk.internal.agent.Agent"; //$NON-NLS-1$
						/*[ELSE] JAVA_SPEC_VERSION >= 9
						"sun.management.Agent"; //$NON-NLS-1$
						/*[ENDIF] JAVA_SPEC_VERSION >= 9 */
				IPC.logMessage("Loading " + agentClassName); //$NON-NLS-1$
				Throwable mamtTemp = null;
				try {
					Class<?> agentClass = null;
					Class<?> startRemoteArgumentType = null;
					/*[IF JAVA_SPEC_VERSION >= 9]*/
					String jmaName = "jdk.management.agent"; //$NON-NLS-1$
					java.lang.Module jmaModule = jdk.internal.module.Modules.loadModule(jmaName);
					/* this should not happen because loadModule() should throw java.lang.module.FindException */
					if (null == jmaModule) {
						throw new ClassNotFoundException("Cannot load " + jmaName); //$NON-NLS-1$
					}
					/* This does not throw ClassNotFoundException. */
					agentClass = Class.forName(jmaModule, agentClassName);
					if (null == agentClass) {
						throw new ClassNotFoundException("Cannot load " + agentClassName); //$NON-NLS-1$
					}
					/*[ELSE] JAVA_SPEC_VERSION >= 9 */
					agentClass = Class.forName(agentClassName);
					/*[ENDIF] JAVA_SPEC_VERSION >= 9 */

					/*[IF (JAVA_SPEC_VERSION >= 9) | Sidecar18-SE-OpenJ9]*/
					startRemoteArgumentType = String.class;
					/*[ELSE] (JAVA_SPEC_VERSION >= 9) | Sidecar18-SE-OpenJ9 */
					startRemoteArgumentType = Properties.class;
					/*[ENDIF] (JAVA_SPEC_VERSION >= 9) | Sidecar18-SE-OpenJ9 */
					startLocalManagementAgentMethod = agentClass.getDeclaredMethod(START_LOCAL_MANAGEMENT_AGENT);
					startRemoteManagementAgentMethod = agentClass.getDeclaredMethod(START_REMOTE_MANAGEMENT_AGENT, startRemoteArgumentType);
					startLocalManagementAgentMethod.setAccessible(true);
					startRemoteManagementAgentMethod.setAccessible(true);
					IPC.logMessage("Loaded " + agentClassName); //$NON-NLS-1$
				} catch (Throwable e) {
					IPC.logMessage("Error loading " + agentClassName, e); //$NON-NLS-1$
					mamtTemp = e;
				}
			/*[IF JAVA_SPEC_VERSION >= 24]*/
				managementAgentMethodThrowable = mamtTemp;
			/*[ELSE] JAVA_SPEC_VERSION >= 24 */
				return mamtTemp;
			});
			/*[ENDIF] JAVA_SPEC_VERSION >= 24 */
		}
	}

	/**
	 * @param attachHandler main handler object for this VM
	 * @param portNumber    port on which to attach
	 * @param key           Security key to validate transaction
	 */
	Attachment(AttachHandler attachHandler, int portNumber, String key) {
		super("Attachment portNumber: " + portNumber); //$NON-NLS-1$
		this.portNumber = portNumber;
		this.key = key;
		this.handler = attachHandler;
		setDaemon(true);
	}

	/**
	 * Create an attachment with a socket connection to the attacher
	 *
	 * @param portNum
	 *            for socket to connect to attacher
	 * @return true if successfully connected
	 */
	boolean connectToAttacher(int portNum) {
		try {
			InetAddress localHost = InetAddress.getLoopbackAddress();
			attacherSocket = new Socket(localHost, portNum);
			IPC.logMessage("connectToAttacher localPort=",  attacherSocket.getLocalPort(), " remotePort=", Integer.toString(attacherSocket.getPort())); //$NON-NLS-1$//$NON-NLS-2$
			responseStream = attacherSocket.getOutputStream();
			commandStream = attacherSocket.getInputStream();
			AttachmentConnection.streamSend(responseStream, Response.CONNECTED + ' ' + key + ' ');
			return true;
		} catch (IOException e) {
			IPC.logMessage("connectToAttacher exception " + e.getMessage() + " " + e.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			closeQuietly(responseStream);
			closeQuietly(commandStream);
			closeQuietly(attacherSocket);
		} catch (Exception otherException) {
			IPC.logMessage("connectToAttacher exception ", otherException.toString()); //$NON-NLS-1$
		}
		return false;
	}

	private static void closeQuietly(Closeable stream) {
		if (null != stream) {
			try {
				stream.close();
			} catch (IOException e1) {
				// ignore
			}
		}
	}

	@Override
	public void run() {
		boolean terminate = false;
		IPC.logMessage("Attachment run"); //$NON-NLS-1$
		connectToAttacher(getPortNumber());
		while (!terminate && !isInterrupted()) {
			terminate = doCommand(commandStream, responseStream);
		}
		try {
			AttachmentConnection.streamSend(responseStream, Response.DETACHED);
			if (null != responseStream) {
				responseStream.close();
				responseStream = null;
			}
			if (null != commandStream) {
				commandStream.close();
				commandStream = null;
			}
		} catch (IOException e) {
			lastError = e;
		}
		if (null != handler) {
			handler.removeAttachment(this);
		}
	}

	/**
	 * @param cmdStream
	 *            channel for commands from attacher
	 * @param respStream
	 *            channel to acknowledge commands This is called by a native
	 *            function when requested by the attacher.
	 * @return true on error, detach command, or command socket closed
	 */
/*[IF CRIU_SUPPORT]*/
	@NotCheckpointSafe
/*[ENDIF] CRIU_SUPPORT */
	boolean doCommand(InputStream cmdStream, OutputStream respStream) {
		try {
			byte[] cmdBytes = AttachmentConnection.streamReceiveBytes(cmdStream, true);
			String cmd = AttachmentConnection.bytesToString(cmdBytes);
			IPC.logMessage("doCommand ", cmd); //$NON-NLS-1$

			if (null == cmd) {
				return true;
			}
			if (cmd.startsWith(Command.DETACH)) {
				return true;
			} else if (cmd.startsWith(Command.LOADAGENT)) {
				if (parseLoadAgent(cmd)) {
					AttachmentConnection.streamSend(respStream, Response.ACK);
				} else {
					AttachmentConnection.streamSend(respStream, Response.ERROR
							+ " " + attachError); //$NON-NLS-1$
				}
			} else if (cmd.startsWith(Command.GET_SYSTEM_PROPERTIES)) {
				Properties internalProperties = VM.internalGetProperties();
				String argumentString = String.join(" ", VM.getVMArgs()); //$NON-NLS-1$
				Properties newProperties = (Properties) internalProperties.clone();
				newProperties.put("sun.jvm.args", argumentString); //$NON-NLS-1$
				replyWithProperties(newProperties);
			} else if (cmd.startsWith(Command.GET_AGENT_PROPERTIES)) {
				replyWithProperties(AttachHandler.getAgentProperties());
			} else if (cmd.startsWith(Command.START_LOCAL_MANAGEMENT_AGENT)) {
				try {
					String serviceAddress = startLocalAgent();
					AttachmentConnection.streamSend(respStream, Response.ATTACH_RESULT + serviceAddress);
				} catch (IbmAttachOperationFailedException e) {
					AttachmentConnection.streamSend(respStream, String.format("%s: %s in startLocalManagementAgent: %s", //$NON-NLS-1$
							Response.ERROR, EXCEPTION_ATTACH_OPERATION_FAILED_EXCEPTION, e.toString()));
					return false;
				}
			} else if (cmd.startsWith(Command.START_MANAGEMENT_AGENT)) {
				/*
				 * Check if the Properties argument is embedded with the command.
				 * If so, it will be separated by a null byte
				 */
				int argsStart = 0;
				while ((argsStart < cmdBytes.length) && (0 != cmdBytes[argsStart])) {
					++argsStart;
				}
				++argsStart; /* skip over the null, if any */

				Properties agentProperties = null;
				if (argsStart < cmdBytes.length) {
					agentProperties = IPC.receiveProperties(new ByteArrayInputStream(cmdBytes, argsStart, cmdBytes.length - argsStart), false);
				} else {
					agentProperties = IPC.receiveProperties(cmdStream, true);
				}
				/*[PR 102391 properties may be appended to the command]*/
				IPC.logMessage("startAgent:" +  cmd); //$NON-NLS-1$
				if (startAgent(agentProperties)) {
					AttachmentConnection.streamSend(respStream, Response.ACK);
				} else {
					AttachmentConnection.streamSend(respStream, Response.ERROR + " " + attachError); //$NON-NLS-1$
				}
			} else if (cmd.startsWith(Command.ATTACH_DIAGNOSTICS_PREFIX)) {
				try {
					String diagnosticCommand = cmd.substring(Command.ATTACH_DIAGNOSTICS_PREFIX.length());
					replyWithProperties(DiagnosticUtils.executeDiagnosticCommand(diagnosticCommand));
				} catch (Exception e) {
					replyWithProperties(DiagnosticProperties.makeExceptionProperties(e));
				}
			} else {
				AttachmentConnection.streamSend(respStream, Response.ERROR
						+ " command invalid: " + cmd); //$NON-NLS-1$
			}
		} catch (IOException e) {
			IPC.logMessage("doCommand IOException ", e.toString()); //$NON-NLS-1$
			return true;
		} catch (Throwable e) {
			IPC.logMessage("doCommand exception ", e.toString()); //$NON-NLS-1$
			try {
				AttachmentConnection.streamSend(respStream, Response.ERROR
						+ " unexpected exception or error: " + e.toString()); //$NON-NLS-1$
			} catch (IOException e1) {
				IPC.logMessage("IOException sending error response" + e1.toString()); //$NON-NLS-1$
			}
			return true;
		}
		return false;
	}

	private void replyWithProperties(DiagnosticProperties props) throws IOException {
		replyWithProperties(props.toProperties());
	}

	private void replyWithProperties(Properties props) throws IOException {
		IPC.sendProperties(props, responseStream);
	}

	/**
	 * close socket and other cleanup
	 */
	synchronized void teardown() {
		try {
			if (null != responseStream) {
				responseStream.close();
			}
			if (null != attacherSocket) {
				attacherSocket.close();
			}
			if (null != commandStream) {
				commandStream.close();
			}
		} catch (IOException e) {
			lastError = e;
		}
	}

	/**
	 * parse and execute a loadAgent command. Set the error string if there is a
	 * problem
	 *
	 * @param cmd
	 *            command string of the form
	 *            "ATTACH_LOADAGENT(agentname,options)" or
	 *            "ATTACH_LOADAGENT(agentname)"
	 * @return true if no error
	 */
	private boolean parseLoadAgent(String cmd) {
		int openParenIndex = cmd.indexOf('(');
		int closeParenIndex = cmd.lastIndexOf(')');
		int commaIndex = cmd.indexOf(',');
		String optionString = ""; //$NON-NLS-1$
		boolean decorate = true;

		attachError = null;
		if (cmd.startsWith(Command.LOADAGENTPATH)) {
			decorate = false;
		}
		if ((openParenIndex < 0) || (closeParenIndex < 0)) {
			attachError = "syntax error"; //$NON-NLS-1$
			return false;
		}
		int agentNameEnd = (commaIndex < 0) ? closeParenIndex : commaIndex;
		String agentName = cmd.substring(openParenIndex + 1, agentNameEnd);
		if (agentName.length() < 1) {
			attachError = "invalid agent name"; //$NON-NLS-1$
			return false;
		}
		if (commaIndex > 0) {
			optionString = cmd.substring(commaIndex + 1, closeParenIndex);
		}
		attachError = loadAgentLibrary(agentName, optionString, decorate);
		if (attachError != null) {
			return false;
		}

		return true;
	}

	/**
	 *
	 * @param agentLibrary
	 *            name of the agent library
	 * @param options
	 *            arguments to the library's Agent_OnAttach function
	 * @param decorate
	 *            add prefixes and suffixes to the library name.
	 * @return null if successful, diagnostic string if error
	 */
	static String loadAgentLibrary(String agentLibrary, String options,
			boolean decorate) {
		IPC.logMessage("loadAgentLibrary " + agentLibrary + ':' + options + " decorate=" + decorate); //$NON-NLS-1$ //$NON-NLS-2$
		ClassLoader loader = java.lang.ClassLoader.getSystemClassLoader();
		int status = loadAgentLibraryImpl(true, loader, agentLibrary, options, decorate);
		if (0 != status) {
			if (-1 == status) {
				return Response.EXCEPTION_AGENT_LOAD_EXCEPTION + ' '
						+ agentLibrary + ':' + options;
			} else {
				return Response.EXCEPTION_AGENT_INITIALIZATION_EXCEPTION
						+ status;
			}
		}
		return null;
	}

	/**
	 * @param dummy
	 *            a dummy arg to ensure that current native method has the
	 *            ClassLoader instance as its second argument required by
	 *            jnimisc.cpp:getCurrentClassLoader()
	 * @param loader
	 *            the ClassLoader instance
	 * @param agentLibrary
	 *            name of the agent library
	 * @param options
	 *            arguments to the library's Agent_OnAttach function
	 * @param decorate
	 *            add prefixes and suffixes to the library name
	 * @return 0 if all went well
	 */
	private static native int loadAgentLibraryImpl(boolean dummy, ClassLoader loader, String agentLibrary,
			String options, boolean decorate);

	private int getPortNumber() {
		return portNumber;
	}

	/**
	 * Start a Java instrumentation agent.
	 * @param agentProperties arguments for the agent
	 * @return true if agent successfully launched
	 */
	private boolean startAgent(Properties agentProperties) {
		try {
			IPC.logMessage("startAgent"); //$NON-NLS-1$
			if (null != MethodRefsHolder.startRemoteManagementAgentMethod) {
				Object startArgument;
				/*[IF (JAVA_SPEC_VERSION >= 9) | Sidecar18-SE-OpenJ9]*/
				startArgument = agentProperties.entrySet().stream()
						.map(entry -> entry.getKey() + "=" + entry.getValue()) //$NON-NLS-1$
						.collect(java.util.stream.Collectors.joining(",")); //$NON-NLS-1$
				/*[ELSE] (JAVA_SPEC_VERSION >= 9) | Sidecar18-SE-OpenJ9 */
				startArgument = agentProperties;
				/*[ENDIF] (JAVA_SPEC_VERSION >= 9) | Sidecar18-SE-OpenJ9 */
				MethodRefsHolder.startRemoteManagementAgentMethod.invoke(null, startArgument);
				return true;
			}
			attachError = EXCEPTION_NO_SUCH_METHOD_EXCEPTION + ": " + START_REMOTE_MANAGEMENT_AGENT; //$NON-NLS-1$
		} catch (IllegalArgumentException e) {
			attachError = EXCEPTION_ILLEGAL_ARGUMENT_EXCEPTION + ": " + e.getMessage(); //$NON-NLS-1$
		} catch (IllegalAccessException e) {
			attachError = EXCEPTION_ILLEGAL_ACCESS_EXCEPTION + ": " + e.getMessage(); //$NON-NLS-1$
		} catch (InvocationTargetException e) {
			attachError = EXCEPTION_INVOCATION_TARGET_EXCEPTION + ": " + e.getMessage(); //$NON-NLS-1$
		}
		return false;
	}

	private static String startLocalAgent() throws IbmAttachOperationFailedException {
		IPC.logMessage("startLocalAgent"); //$NON-NLS-1$
		if (null != MethodRefsHolder.startLocalManagementAgentMethod) { /* forces initialization */
			try {
				MethodRefsHolder.startLocalManagementAgentMethod.invoke(null);
			} catch (Throwable exc) {
				IPC.logMessage("Exception starting management agent:", exc); //$NON-NLS-1$
				throw new IbmAttachOperationFailedException("startLocalManagementAgent error starting agent", exc); //$NON-NLS-1$
			}
		} else {
			Throwable exc = MethodRefsHolder.managementAgentMethodThrowable;
			String msg = "Target VM cannot access " + START_LOCAL_MANAGEMENT_AGENT; //$NON-NLS-1$
			IPC.logMessage(msg, exc);
			throw new IbmAttachOperationFailedException(msg, exc);
		}

		String addr = saveLocalConnectorAddress();
		if (Objects.isNull(addr)) {
			throw new IbmAttachOperationFailedException(
					"startLocalManagementAgent: " + LOCAL_CONNECTOR_ADDRESS + " not defined"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return addr;
	}

	static String saveLocalConnectorAddress() {
		Properties systemProperties = VM.internalGetProperties();
		String addr;
		synchronized (systemProperties) {
			addr = systemProperties.getProperty(LOCAL_CONNECTOR_ADDRESS);
			if (Objects.isNull(addr)) {
				addr = VMSupport.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS);
				if (!Objects.isNull(addr)) {
					systemProperties.setProperty(LOCAL_CONNECTOR_ADDRESS, addr);
				}
			}
		}
		if (Objects.isNull(addr)) {
			/* property should be set at this point */
			IPC.logMessage(LOCAL_CONNECTOR_ADDRESS + " not set"); //$NON-NLS-1$
			return null;
		}
		IPC.logMessage(LOCAL_CONNECTOR_ADDRESS + "=", addr); //$NON-NLS-1$
		return addr;
	}
}
