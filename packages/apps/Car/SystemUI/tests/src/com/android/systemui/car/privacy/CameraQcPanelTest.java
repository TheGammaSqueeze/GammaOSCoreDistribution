/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.privacy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.CameraPrivacyChipViewController;
import com.android.systemui.privacy.PrivacyDialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CameraQcPanelTest extends SysuiTestCase {
    private static final String APP_LABEL_ACTIVE = "active";
    private static final String APP_LABEL_INACTIVE = "inactive";
    private static final String PACKAGE_NAME = "package";

    private CameraQcPanel mCameraQcPanel;
    private String mPhoneCallTitle;
    private String mCameraOnTitleText;
    private String mCameraOffTitleText;
    private Drawable mTestDrawable;

    @Mock
    private CameraPrivacyElementsProviderImpl mCameraPrivacyElementsProvider;
    @Mock
    private CameraPrivacyChipViewController mCameraSensorInfoProvider;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(/* testClass= */ this);

        when(mPackageManager.getApplicationInfoAsUser(eq(PACKAGE_NAME), anyInt(), eq(0)))
                .thenReturn(mApplicationInfo);
        mContext.setMockPackageManager(mPackageManager);

        mCameraQcPanel = new CameraQcPanel(mContext);
        mCameraQcPanel.setControllers(mCameraSensorInfoProvider, mCameraPrivacyElementsProvider);

        mPhoneCallTitle = mContext.getString(R.string.ongoing_privacy_dialog_phonecall);
        mCameraOnTitleText = mContext.getString(R.string.privacy_chip_use_sensor,
                mCameraQcPanel.getSensorName());
        mCameraOffTitleText = mContext.getString(R.string.privacy_chip_off_content,
                mCameraQcPanel.getSensorNameWithFirstLetterCapitalized());
        mTestDrawable = mContext.getDrawable(R.drawable.privacy_chip_active_background_pill);
    }

    @Test
    public void testGetQCItem_cameraDisabled_noPrivacyItems_returnsOnlyCameraOffRow() {
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(false);
        List<PrivacyDialog.PrivacyElement> elements = Collections.emptyList();
        when(mCameraPrivacyElementsProvider.getPrivacyElements()).thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(1);
        assertThat(list.getRows().get(0).getTitle()).isEqualTo(mCameraOffTitleText);
    }

    @Test
    public void testGetQCItem_cameraEnabled_noPrivacyItems_returnsOnlyCameraOffRow() {
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements = Collections.emptyList();
        when(mCameraPrivacyElementsProvider.getPrivacyElements()).thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(1);
        assertThat(list.getRows().get(0).getTitle()).isEqualTo(mCameraOnTitleText);
    }

    @Test
    public void testGetQCItem_cameraEnabled_onlyOneActivePrivacyItem_firstRowCameraEnabled() {
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements =
                List.of(getPrivacyElement(/* active=*/ true, /* phoneCall= */ false));
        when(mCameraPrivacyElementsProvider.getPrivacyElements()).thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(2);
        assertThat(list.getRows().get(0).getTitle()).isEqualTo(mCameraOnTitleText);
    }

    @Test
    public void testGetQCItem_cameraEnabled_onlyOneActivePrivacyItem_secondRowActiveApp() {
        String expectedTitle = mContext.getString(R.string.privacy_chip_app_using_sensor_suffix,
                APP_LABEL_ACTIVE, mCameraQcPanel.getSensorShortName());
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements =
                List.of(getPrivacyElement(/* active=*/ true, /* phoneCall= */ false));
        when(mCameraPrivacyElementsProvider.getPrivacyElements()).thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(2);
        assertThat(list.getRows().get(1).getTitle()).isEqualTo(expectedTitle);
    }

    @Test
    public void
            testGetQCItem_cameraDisabled_onlyOneInactivePhonePrivacyItem_firstRowCameraDisabled() {
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(false);
        List<PrivacyDialog.PrivacyElement> elements =
                List.of(getPrivacyElement(/* active=*/ false, /* phoneCall= */ true));
        when(mCameraPrivacyElementsProvider.getPrivacyElements()).thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(2);
        assertThat(list.getRows().get(0).getTitle()).isEqualTo(mCameraOffTitleText);
    }

    @Test
    public void
            testGetQCItem_cameraDisabled_onlyOneInactivePhonePrivacyItem_secondRowInactiveApp() {
        String expectedTitle =
                mContext.getString(R.string.privacy_chip_app_recently_used_sensor_suffix,
                        mPhoneCallTitle, mCameraQcPanel.getSensorShortName());
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(false);
        List<PrivacyDialog.PrivacyElement> elements =
                List.of(getPrivacyElement(/* active=*/ false, /* phoneCall= */ true));
        when(mCameraPrivacyElementsProvider.getPrivacyElements()).thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(2);
        assertThat(list.getRows().get(1).getTitle()).isEqualTo(expectedTitle);
    }

    @Test
    public void testGetQCItem_cameraEnabled_multiplePrivacyItems_firstRowCameraEnabled() {
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements = new ArrayList<>();
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ true));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ true));
        when(mCameraPrivacyElementsProvider.getPrivacyElements())
                .thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(4);
        assertThat(list.getRows().get(0).getTitle()).isEqualTo(mCameraOnTitleText);
    }

    @Test
    public void testGetQCItem_cameraEnabled_multiplePrivacyItems_secondRowActiveApp() {
        String expectedTitle = mContext.getString(R.string.privacy_chip_app_using_sensor_suffix,
                APP_LABEL_ACTIVE, mCameraQcPanel.getSensorShortName());
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements = new ArrayList<>();
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ true));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ true));
        when(mCameraPrivacyElementsProvider.getPrivacyElements())
                .thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(4);
        assertThat(list.getRows().get(1).getTitle()).isEqualTo(expectedTitle);
    }

    @Test
    public void testGetQCItem_cameraEnabled_multiplePrivacyItems_thirdRowActivePhone() {
        String expectedTitle = mContext.getString(R.string.privacy_chip_app_using_sensor_suffix,
                mPhoneCallTitle, mCameraQcPanel.getSensorShortName());
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements = new ArrayList<>();
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ true));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ true));
        when(mCameraPrivacyElementsProvider.getPrivacyElements())
                .thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(4);
        assertThat(list.getRows().get(2).getTitle()).isEqualTo(expectedTitle);
    }

    @Test
    public void testGetQCItem_cameraEnabled_multiplePrivacyItems_fourthRowInactiveApps() {
        String expectedTitle = mContext
                .getString(R.string.privacy_chip_apps_recently_used_sensor_suffix,
                        mPhoneCallTitle, 2, mCameraQcPanel.getSensorShortName());
        when(mCameraSensorInfoProvider.isSensorEnabled()).thenReturn(true);
        List<PrivacyDialog.PrivacyElement> elements = new ArrayList<>();
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ true));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ false, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ false));
        elements.add(getPrivacyElement(/* active=*/ true, /* phoneCall= */ true));
        when(mCameraPrivacyElementsProvider.getPrivacyElements())
                .thenReturn(elements);

        QCList list = getQCList();

        assertThat(list.getRows().size()).isEqualTo(4);
        assertThat(list.getRows().get(3).getTitle()).isEqualTo(expectedTitle);
    }

    private QCList getQCList() {
        QCItem item = mCameraQcPanel.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        return (QCList) item;
    }

    private PrivacyDialog.PrivacyElement getPrivacyElement(boolean active, boolean phoneCall) {
        PrivacyDialog.PrivacyElement element = mock(PrivacyDialog.PrivacyElement.class);
        when(mApplicationInfo.loadSafeLabel(any(), anyFloat(), anyInt()))
                .thenReturn(active ? APP_LABEL_ACTIVE : APP_LABEL_INACTIVE);
        when(mApplicationInfo.loadUnbadgedIcon(eq(mPackageManager)))
                .thenReturn(mTestDrawable);
        when(element.getPackageName()).thenReturn(PACKAGE_NAME);
        when(element.getUserId()).thenReturn(0);
        when(element.getPhoneCall()).thenReturn(phoneCall);
        when(element.getActive()).thenReturn(active);
        return element;
    }
}
