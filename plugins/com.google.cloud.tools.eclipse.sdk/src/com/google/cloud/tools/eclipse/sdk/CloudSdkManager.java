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
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.cloud.tools.managedcloudsdk.ManagedCloudSdk;
import com.google.cloud.tools.managedcloudsdk.ManagedSdkVerificationException;
import com.google.cloud.tools.managedcloudsdk.ManagedSdkVersionMismatchException;
import com.google.cloud.tools.managedcloudsdk.UnsupportedOsException;
import com.google.cloud.tools.managedcloudsdk.command.CommandExecutionException;
import com.google.cloud.tools.managedcloudsdk.command.CommandExitException;
import com.google.cloud.tools.managedcloudsdk.components.SdkComponent;
import com.google.cloud.tools.managedcloudsdk.components.SdkComponentInstaller;
import com.google.cloud.tools.managedcloudsdk.install.SdkInstaller;
import com.google.cloud.tools.managedcloudsdk.install.SdkInstallerException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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
  private static int preventerCounts = 0;
  private static boolean isModifyingSdk = false;

  /**
   * Prevents potential future SDK auto-install or auto-update temporarily. This is to use the Cloud
   * SDK safely by preventing modifying the SDK while using the SDK. If an install or update has
   * already started, blocks callers until the install or update is complete. Callers must call
   * {@code CloudSdkManager#allowModifyingSdk} eventually to lift the suspension. Any callers that
   * intend to use {@code CloudSdk} must always call this before staring work, even if the Cloud SDK
   * preferences are configured not to auto-manage the SDK.
   *
   * Must not be called from the UI thread because the method will block if there is an on-going
   * install/update.
   *
   * @see CloudSdkManager#allowModifyingSdk
   */
  public static void preventModifyingSdk() throws InterruptedException {
    synchronized (modifyLock) {
      while (isModifyingSdk) {
        modifyLock.wait();
      }
      preventerCounts++;
    }
  }

  /**
   * Allows future SDK auto-install or auto-update temporarily prevented by {@code
   * CloudSdkManager#preventModifyingSdk}. This is a non-blocking call.
   *
   * @see CloudSdkManager#preventModifyingSdk
   */
  public static void allowModifyingSdk() {
    // Fire a job as an optimization that makes this method non-blocking; there is no need to wait
    // until it goes into a modifiable state.
    Job releaseJob = new Job("Lifting SDK modification prevention...") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        synchronized (modifyLock) {
          Preconditions.checkState(preventerCounts > 0);
          preventerCounts--;
          modifyLock.notifyAll();
        }
        return Status.OK_STATUS;
      }};
    releaseJob.schedule();
  }

  /**
   * Installs the managed Cloud SDK, if the preferences are configured to auto-managed the SDK.
   * Blocks callers 1) if the managed SDK is being installed or updated concurrently by others; and
   * 2) until the installation is complete.
   *
   * @param consoleStream stream to which the install output is written
   */
  public static void installManagedSdk(MessageConsoleStream consoleStream)
      throws InterruptedException, CoreException {
    if (isManagedSdkFeatureEnabled()) {
      if (CloudSdkPreferences.isAutoManaging()) {
        synchronized (modifyLock) {
          try {
            while (isModifyingSdk || preventerCounts > 0) {
              modifyLock.wait();
            }
            isModifyingSdk = true;

            doCoreInstall(consoleStream);
          } finally {
            isModifyingSdk = false;
            modifyLock.notifyAll();
          }
        }
      }
    }
  }

  private static void doCoreInstall(MessageConsoleStream consoleStream)
      throws InterruptedException, CoreException {
    try {
      ManagedCloudSdk managedSdk = ManagedCloudSdk.newManagedSdk();
      if (!managedSdk.isInstalled()) {
        SdkInstaller installer = managedSdk.newInstaller();
        installer.install(new MessageConsoleWriterListener(consoleStream));
      }

      if (!managedSdk.hasComponent(SdkComponent.APP_ENGINE_JAVA)) {
        SdkComponentInstaller componentInstaller = managedSdk.newComponentInstaller();
        componentInstaller.installComponent(
        SdkComponent.APP_ENGINE_JAVA, new MessageConsoleWriterListener(consoleStream));
      }
    } catch (IOException | ManagedSdkVerificationException | SdkInstallerException |
        CommandExecutionException | CommandExitException e) {
      throw new CoreException(
          StatusUtil.error(CloudSdkManager.class, "Failed to install the Google Cloud SDK.", e));
    } catch (UnsupportedOsException e) {
      throw new RuntimeException("Cloud Tools for Eclipse supports Windows, Linux, and Mac only.");
    } catch (ManagedSdkVersionMismatchException e) {
      throw new RuntimeException("This is never thrown because we always use LATEST.");
    }
  }

  public static void installManagedSdkAsync() {
    if (isManagedSdkFeatureEnabled()) {
      if (CloudSdkPreferences.isAutoManaging()) {
        // Job installJob = new InstallJob();
        // installJob.schedule();
      }
    }
  }

  public static void updateManagedSdkAsync() {
    if (isManagedSdkFeatureEnabled()) {
      if (CloudSdkPreferences.isAutoManaging()) {
        // Job udpateJob = new UpdateJob();
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
