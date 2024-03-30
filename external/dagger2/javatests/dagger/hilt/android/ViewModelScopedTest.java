/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.android;

import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.scopes.ViewModelScoped;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class ViewModelScopedTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testViewModelScopeInFragment() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment =
                (TestFragment) activity.getSupportFragmentManager().findFragmentByTag("tag");
            assertThat(fragment.vm.one.bar).isEqualTo(fragment.vm.two.bar);
          });
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestActivity extends Hilt_ViewModelScopedTest_TestActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      if (savedInstanceState == null) {
        Fragment f =
            getSupportFragmentManager()
                .getFragmentFactory()
                .instantiate(TestFragment.class.getClassLoader(), TestFragment.class.getName());
        getSupportFragmentManager().beginTransaction().add(0, f, "tag").commitNow();
      }
    }
  }

  @AndroidEntryPoint(Fragment.class)
  public static class TestFragment extends Hilt_ViewModelScopedTest_TestFragment {
    MyViewModel vm;

    @Override
    public void onCreate(@Nullable Bundle bundle) {
      super.onCreate(bundle);
      vm = new ViewModelProvider(this).get(MyViewModel.class);
    }
  }

  @HiltViewModel
  static class MyViewModel extends ViewModel {

    final DependsOnBarOne one;
    final DependsOnBarTwo two;

    @Inject
    MyViewModel(DependsOnBarOne one, DependsOnBarTwo two) {
      this.one = one;
      this.two = two;
    }
  }

  @ViewModelScoped
  static class Bar {
    @Inject
    Bar() {}
  }

  static class DependsOnBarOne {
    final Bar bar;

    @Inject
    DependsOnBarOne(Bar bar) {
      this.bar = bar;
    }
  }

  static class DependsOnBarTwo {
    final Bar bar;

    @Inject
    DependsOnBarTwo(Bar bar) {
      this.bar = bar;
    }
  }
}
