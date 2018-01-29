/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.eclipse.sdk;

import com.google.cloud.tools.eclipse.sdk.internal.CloudSdkPreferences;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.ui.console.MessageConsoleStream;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

public class CloudSdkManager {

  private static final String OPTION_MANAGED_CLOUD_SDK =
      "com.google.cloud.tools.eclipse.sdk/enable.managed.cloud.sdk";

  // To be able to write tests for the managed Cloud SDK feature, which is disabled at the moment.
  @VisibleForTesting
  public static boolean forceManagedSdkFeature;

  public static boolean isManagedSdkFeatureEnabled() {
    if (forceManagedSdkFeature) {
      return true;
    }

    BundleContext context = FrameworkUtil.getBundle(CloudSdkManager.class).getBundleContext();
    DebugOptions debugOptions = context.getService(context.getServiceReference(DebugOptions.class));
    if (debugOptions != null) {
      return debugOptions.getBooleanOption(OPTION_MANAGED_CLOUD_SDK, false);
    }
    return false;
  }

  private static Object modifyLock = new Object();
  private static int suspenderCounts = 0;
  private static boolean isModifyingSdk = false;

  /**
   * Suspends potential SDK auto-install or auto-update temporarily. This is to use the Cloud SDK
   * safely by preventing modifying the SDK while using the SDK. If an install or update has already
   * started, blocks callers until the install or update is complete. Callers must call {@code
   * CloudSdkManager#allowModifyingSdk} eventually to lift the suspension.
   *
   * Any callers that intend to use {@code CloudSdk} must always call this before staring work, even
   * if the Cloud SDK preferences are configured not to auto-managed the SDK.
   *
   * @see CloudSdkManager#allowModifyingSdk
   */
  public static void suspendModifyingSdk() throws InterruptedException {
    synchronized (modifyLock) {
      while (isModifyingSdk) {
        modifyLock.wait();
      }
      suspenderCounts++;
    }
  }

  /**
   * Allows SDK auto-install or auto-update temporarily suspended by {@code
   * CloudSdkManager#suspendModifyingSdk}.
   *
   * @see CloudSdkManager#suspendModifyingSdk
   */
  public static void allowModifyingSdk() {
    synchronized (modifyLock) {
      Preconditions.checkState(suspenderCounts > 0);
      suspenderCounts--;
      modifyLock.notifyAll();
    }
  }

  /**
   * Installs the managed Cloud SDK, if the preferences are configured to auto-managed the SDK.
   * Blocks callers 1) if the managed SDK is being installed or updated concurrently by others; and
   * 2) until the installation is complete.
   *
   * @param consoleStream stream to which the install output is written
   */
  public static void installManagedSdk(MessageConsoleStream consoleStream)
      throws InterruptedException {
    if (isManagedSdkFeatureEnabled()) {
      if (CloudSdkPreferences.isAutoManaging()) {
        synchronized (modifyLock) {
          try {
            while (isModifyingSdk || suspenderCounts > 0) {
              modifyLock.wait();
            }
            isModifyingSdk = true;
            // TODO: start installing SDK synchronously if not found.
            
          } finally {
            isModifyingSdk = false;
            modifyLock.notifyAll();
          }
        }
      }
    }
  }

  public static void installManagedSdkAsync() {
    if (isManagedSdkFeatureEnabled()) {
      if (CloudSdkPreferences.isAutoManaging()) {
        // Job installJob = new Job();
        // installJob.schedule();
      }
    }
  }

  public static void updateManagedSdkAsync() {
    if (isManagedSdkFeatureEnabled()) {
      if (CloudSdkPreferences.isAutoManaging()) {
        // Job udpateJob = new Job();
        // updateJob.schedule();
      }
    }
  }

  /**
   * Performs a one-time setup of preferences for the Managed Cloud SDK feature if it has never been
   * set up.
   */
  public static void setUpInitialPreferences() {
    // TODO(chanseok): to be implemented.
  }
}
