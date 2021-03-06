/*
 * Copyright 2016 Google Inc.
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

package com.google.cloud.tools.eclipse.sdk.ui.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.eclipse.sdk.CloudSdkManager;
import com.google.cloud.tools.eclipse.sdk.internal.CloudSdkPreferences;
import com.google.cloud.tools.eclipse.test.util.MockSdkGenerator;
import com.google.cloud.tools.eclipse.test.util.ui.CompositeUtil;
import com.google.cloud.tools.eclipse.test.util.ui.ShellTestResource;
import java.nio.file.Path;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCheckBox;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Tests for CloudSdkPreferenceArea.
 */
@RunWith(MockitoJUnitRunner.class)
public class CloudSdkPreferenceAreaTest {

  @Rule public ShellTestResource shellResource = new ShellTestResource();
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock
  private IPreferenceStore preferences;

  private CloudSdkPreferenceArea area;
  private Shell shell;

  private Button chooseSdk;
  private Text sdkLocation;
  private Label sdkVersion;

  @After
  public void tearDown() {
    CloudSdkManager.forceManagedSdkFeature = false;
  }

  @Test
  public void testNonExistentPath() {
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("/non-existent");
    createPreferenceArea();

    assertFalse(area.getStatus().isOK());
    assertEquals(IStatus.ERROR, area.getStatus().getSeverity());
    assertEquals("SDK version: No SDK found", sdkVersion.getText());
  }

  @Test
  public void testVersion() throws Exception {
    CloudSdkManager.forceManagedSdkFeature = true;

    Path mockSdk = MockSdkGenerator.createMockSdk();
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn(mockSdk.toString());
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT)).thenReturn("MANUAL");

    createPreferenceArea();
    assertEquals("SDK version: 184.0.0", sdkVersion.getText());

    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT)).thenReturn("AUTOMATIC");
    area.load();
    assertEquals("SDK version: No SDK found", sdkVersion.getText());
  }

  @Test
  public void testInvalidPath() {
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH))
        .thenReturn(tempFolder.getRoot().getAbsolutePath());
    createPreferenceArea();
    assertEquals(IStatus.WARNING, area.getStatus().getSeverity());

    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("");
    area.load();
    assertTrue(area.getStatus().isOK());
  }

  // TODO(chanseok): can become "@Before setUp()" once we remove the managed SDK debug feature flag.
  private void createPreferenceArea() {
    shell = shellResource.getShell();
    area = new CloudSdkPreferenceArea();
    area.setPreferenceStore(preferences);
    area.createContents(shell);
    area.load();

    chooseSdk = CompositeUtil.findButton(shell, "Choose SDK");
    sdkLocation = CompositeUtil.findControlAfterLabel(shell, Text.class, "&SDK location:");
    sdkVersion = CompositeUtil.findLabel(shell, "SDK version:");
  }

  @Test
  public void testUi_noSdkManagementFeature() {
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("");
    createPreferenceArea();

    assertNull(chooseSdk);
    assertNotNull(sdkLocation);
    assertNotNull(sdkVersion);
    assertTrue(sdkLocation.isEnabled());
  }

  @Test
  public void testUi_sdkManagementFeature() {
    CloudSdkManager.forceManagedSdkFeature = true;
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("");
    createPreferenceArea();

    assertNotNull(chooseSdk);
    assertNotNull(sdkLocation);
    assertNotNull(sdkVersion);
  }

  @Test
  public void testControlStates_automaticSdk() {
    CloudSdkManager.forceManagedSdkFeature = true;
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT)).thenReturn("AUTOMATIC");
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("");
    createPreferenceArea();

    assertFalse(chooseSdk.getSelection());
    assertFalse(sdkLocation.isEnabled());
  }

  @Test
  public void testControlStates_manualSdk() {
    CloudSdkManager.forceManagedSdkFeature = true;
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT)).thenReturn("MANUAL");
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("");
    createPreferenceArea();

    assertTrue(chooseSdk.getSelection());
    assertTrue(sdkLocation.isEnabled());
  }

  @Test
  public void testPerformApply_preferencesSaved() {
    CloudSdkManager.forceManagedSdkFeature = true;
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT)).thenReturn("AUTOMATIC");
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH)).thenReturn("");
    createPreferenceArea();

    assertFalse(chooseSdk.getSelection());
    chooseSdk.setSelection(true);
    area.performApply();

    verify(preferences).putValue(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT, "MANUAL");
  }

  @Test
  public void testValidationStatus_switchManagementOption() {
    CloudSdkManager.forceManagedSdkFeature = true;
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_MANAGEMENT)).thenReturn("MANUAL");
    when(preferences.getString(CloudSdkPreferences.CLOUD_SDK_PATH))
        .thenReturn("/non-existing/directory");
    createPreferenceArea();

    assertTrue(chooseSdk.getSelection());
    assertEquals(IStatus.ERROR, area.getStatus().getSeverity());

    new SWTBotCheckBox(chooseSdk).click();
    assertTrue(area.getStatus().isOK());

    new SWTBotCheckBox(chooseSdk).click();
    assertEquals(IStatus.ERROR, area.getStatus().getSeverity());
  }
}
