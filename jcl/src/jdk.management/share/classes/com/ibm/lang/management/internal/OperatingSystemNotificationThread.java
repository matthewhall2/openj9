/*[INCLUDE-IF Sidecar18-SE]*/
/*
 * Copyright IBM Corp. and others 2005
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
package com.ibm.lang.management.internal;

/*[IF JAVA_SPEC_VERSION < 24]*/
import java.security.PrivilegedAction;
/*[ENDIF] JAVA_SPEC_VERSION < 24 */
import javax.management.Notification;

import com.ibm.lang.management.AvailableProcessorsNotificationInfo;
import com.ibm.lang.management.ProcessingCapacityNotificationInfo;
import com.ibm.lang.management.TotalPhysicalMemoryNotificationInfo;

/**
 * A thread that monitors and dispatches notifications for changes in
 * the number of CPUs, processing capacity, and total physical memory.
 *
 * @since 1.5
 */
final class OperatingSystemNotificationThread implements Runnable {

	private final ExtendedOperatingSystemMXBeanImpl osBean;

	OperatingSystemNotificationThread(ExtendedOperatingSystemMXBeanImpl osBean) {
		super();
		this.osBean = osBean;
	}

	/**
	 * Register a shutdown handler that will signal this thread to terminate,
	 * then enter the native that services an internal notification queue.
	 */
	@Override
	/*[IF (17 <= JAVA_SPEC_VERSION) & (JAVA_SPEC_VERSION < 24)]*/
	@SuppressWarnings("removal")
	/*[ENDIF] (17 <= JAVA_SPEC_VERSION) & (JAVA_SPEC_VERSION < 24) */
	public void run() {
		Thread myShutdownNotifier = new OperatingSystemNotificationThreadShutdown(Thread.currentThread());

		try {
			/*[IF JAVA_SPEC_VERSION >= 24]*/
			Runtime.getRuntime().addShutdownHook(myShutdownNotifier);
			/*[ELSE] JAVA_SPEC_VERSION >= 24 */
			java.security.AccessController.doPrivileged(new PrivilegedAction<Void>() {
				@Override
				public Void run() {
					Runtime.getRuntime().addShutdownHook(myShutdownNotifier);
					return null;
				}
			});
			/*[ENDIF] JAVA_SPEC_VERSION >= 24 */
		} catch (IllegalStateException e) {
			/* if by chance we are already shutting down when we try to
			 * register the shutdown hook, allow this thread to terminate
			 * silently
			 */
			return;
		}

		processNotificationLoop();
	}

	/**
	 * Registers a signal handler for SIGRECONFIG, then processes notifications
	 * on an internal VM queue until a shutdown request is received.
	 */
	private native void processNotificationLoop();

	private void dispatchNotificationHelper(int type, long data, long sequenceNumber) {
		if (type == 1) {
			// #CPUs changed
			AvailableProcessorsNotificationInfo info = new AvailableProcessorsNotificationInfo((int) data);
			Notification n = new Notification(AvailableProcessorsNotificationInfo.AVAILABLE_PROCESSORS_CHANGE, "java.lang:type=OperatingSystem", sequenceNumber); //$NON-NLS-1$
			n.setUserData(AvailableProcessorsNotificationInfoUtil.toCompositeData(info));
			osBean.sendNotification(n);
		} else if (type == 2) {
			// processing capacity changed
			ProcessingCapacityNotificationInfo info = new ProcessingCapacityNotificationInfo((int) data);
			Notification n = new Notification(ProcessingCapacityNotificationInfo.PROCESSING_CAPACITY_CHANGE, "java.lang:type=OperatingSystem", sequenceNumber); //$NON-NLS-1$
			n.setUserData(ProcessingCapacityNotificationInfoUtil.toCompositeData(info));
			osBean.sendNotification(n);
		} else if (type == 3) {
			// total physical memory changed
			TotalPhysicalMemoryNotificationInfo info = new TotalPhysicalMemoryNotificationInfo(data);
			Notification n = new Notification(TotalPhysicalMemoryNotificationInfo.TOTAL_PHYSICAL_MEMORY_CHANGE, "java.lang:type=OperatingSystem", sequenceNumber); //$NON-NLS-1$
			n.setUserData(TotalPhysicalMemoryNotificationInfoUtil.toCompositeData(info));
			osBean.sendNotification(n);
		}
	}

}
