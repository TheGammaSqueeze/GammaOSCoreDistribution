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

package dagger.hilt.android.processor.internal;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.processor.AndroidCompilers.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GeneratorsTest {

  @Test
  public void copyConstructorParametersCopiesExternalNullables() {
    JavaFileObject baseActivity =
        JavaFileObjects.forSourceLines(
            "test.BaseActivity",
            "package test;",
            "",
            "import androidx.fragment.app.FragmentActivity;",
            "",
            "public abstract class BaseActivity extends FragmentActivity {",
            "  protected BaseActivity(",
            "      @androidx.annotation.Nullable String supportNullable,",
            "      @androidx.annotation.Nullable String androidxNullable,",
            "      @javax.annotation.Nullable String javaxNullable) { }",
            "}");
    JavaFileObject myActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {",
            "  public MyActivity(",
            "      String supportNullable,",
            "      String androidxNullable,",
            "      String javaxNullable) {",
            "    super(supportNullable, androidxNullable, javaxNullable);",
            "  }",
            "}");
    Compilation compilation = compiler().compile(baseActivity, myActivity);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyActivity")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyActivity",
                "package test;",
                "",
                "import androidx.annotation.Nullable;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ActivityGenerator\")",
                "abstract class Hilt_MyActivity extends BaseActivity implements",
                "    GeneratedComponentManagerHolder {",
                "  Hilt_MyActivity(",
                "      @Nullable String supportNullable,",
                "      @Nullable String androidxNullable,",
                "      @javax.annotation.Nullable String javaxNullable) {",
                "    super(supportNullable, androidxNullable, javaxNullable);",
                "    _initHiltInternal();",
                "  }",
                "}"));
  }

  @Test
  public void copyConstructorParametersConvertsAndroidInternalNullableToExternal() {
    // Relies on View(Context, AttributeSet), which has android-internal
    // @android.annotation.Nullable on AttributeSet.
    JavaFileObject myView =
        JavaFileObjects.forSourceLines(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(View.class)",
            "public class MyView extends Hilt_MyView {",
            "  public MyView(Context context, AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    Compilation compilation = compiler().compile(myView);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyView")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyView",
                "package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ViewGenerator\")",
                "abstract class Hilt_MyView extends View implements",
                "GeneratedComponentManagerHolder {",
                // The generated parameter names are copied from the base class. Since we only have
                // the jar and not the source for these base classes the parameter names are missing
                "  Hilt_MyView(Context arg0, @Nullable AttributeSet arg1) {",
                "    super(arg0, arg1);",
                "    inject();",
                "  }",
                "}"));
  }

  @Test
  public void copyTargetApiAnnotationActivity() {
    JavaFileObject myActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import androidx.fragment.app.FragmentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(FragmentActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {}");
    Compilation compilation = compiler().compile(myActivity);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyActivity")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyActivity",
                " package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ActivityGenerator\")",
                "@TargetApi(24)",
                "abstract class Hilt_MyActivity extends FragmentActivity ",
                "implements GeneratedComponentManagerHolder {",
                "}"));
  }

  @Test
  public void copyTargetApiAnnotationOverView() {
    JavaFileObject myView =
        JavaFileObjects.forSourceLines(
            "test.MyView",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.widget.LinearLayout;",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(LinearLayout.class)",
            "public class MyView extends Hilt_MyView {",
            " public MyView(Context context, AttributeSet attributeSet){",
            "   super(context, attributeSet);",
            " }",
            "",
            "}");
    Compilation compilation = compiler().compile(myView);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyView")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyView",
                "",
                "package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ViewGenerator\")",
                "@TargetApi(24)",
                "abstract class Hilt_MyView extends LinearLayout implements"
                    + " GeneratedComponentManagerHolder {",
                "}"));
  }

  @Test
  public void copyTargetApiAnnotationApplication() {
    JavaFileObject myApplication =
        JavaFileObjects.forSourceLines(
            "test.MyApplication",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@TargetApi(24)",
            "@HiltAndroidApp(Application.class)",
            "public class MyApplication extends Hilt_MyApplication {}");
    Compilation compilation = compiler().compile(myApplication);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyApplication")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyApplication",
                " package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ApplicationGenerator\")",
                "@TargetApi(24)",
                "abstract class Hilt_MyApplication extends Application implements"
                    + " GeneratedComponentManagerHolder {}"));
  }

  @Test
  public void copyTargetApiAnnotationFragment() {
    JavaFileObject myApplication =
        JavaFileObjects.forSourceLines(
            "test.MyFragment",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import androidx.fragment.app.Fragment;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(Fragment.class)",
            "public class MyFragment extends Hilt_MyFragment {}");
    Compilation compilation = compiler().compile(myApplication);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyFragment")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyFragment",
                "package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.FragmentGenerator\")",
                "@TargetApi(24)",
                "@SuppressWarnings(\"deprecation\")",
                "abstract class Hilt_MyFragment extends Fragment implements"
                    + " GeneratedComponentManagerHolder {}"));
  }

  @Test
  public void copyTargetApiBroadcastRecieverGenerator() {
    JavaFileObject myBroadcastReceiver =
        JavaFileObjects.forSourceLines(
            "test.MyBroadcastReceiver",
            "package test;",
            "",
            "import android.content.BroadcastReceiver;",
            "import android.annotation.TargetApi;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(BroadcastReceiver.class)",
            "public class MyBroadcastReceiver extends Hilt_MyBroadcastReceiver {}");
    Compilation compilation = compiler().compile(myBroadcastReceiver);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyBroadcastReceiver")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyBroadcastReceiver",
                "package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.BroadcastReceiverGenerator\")",
                "@TargetApi(24)",
                "abstract class Hilt_MyBroadcastReceiver extends BroadcastReceiver {}"));
  }

  @Test
  public void copyTargetApiServiceGenerator() {
    JavaFileObject myService =
        JavaFileObjects.forSourceLines(
            "test.MyService",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.content.Intent;",
            "import android.app.Service;",
            "import android.os.IBinder;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(Service.class)",
            "public class MyService extends Hilt_MyService {",
            "   @Override",
            "   public IBinder onBind(Intent intent){",
            "     return null;",
            "   }",
            "}");
    Compilation compilation = compiler().compile(myService);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/Hilt_MyService")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.Hilt_MyService",
                "package test;",
                "",
                "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ServiceGenerator\")",
                "@TargetApi(24)",
                "abstract class Hilt_MyService extends Service implements"
                    + " GeneratedComponentManagerHolder{}"));
  }
}
