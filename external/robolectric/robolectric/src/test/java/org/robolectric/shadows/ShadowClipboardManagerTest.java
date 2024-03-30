package org.robolectric.shadows;

import static android.content.ClipboardManager.OnPrimaryClipChangedListener;
import static android.os.Build.VERSION_CODES.P;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
public class ShadowClipboardManagerTest {

  private ClipboardManager clipboardManager;

  @Before public void setUp() throws Exception {
    clipboardManager =
        (ClipboardManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
  }

  @Test
  public void shouldStoreText() {
    clipboardManager.setText("BLARG!!!");
    assertThat(clipboardManager.getText().toString()).isEqualTo("BLARG!!!");
  }

  @Test
  public void shouldNotHaveTextIfTextIsNull() {
    clipboardManager.setText(null);
    assertThat(clipboardManager.hasText()).isFalse();
  }

  @Test
  public void shouldNotHaveTextIfTextIsEmpty() {
    clipboardManager.setText("");
    assertThat(clipboardManager.hasText()).isFalse();
  }

  @Test
  public void shouldHaveTextIfEmptyString() {
    clipboardManager.setText(" ");
    assertThat(clipboardManager.hasText()).isTrue();
  }

  @Test
  public void shouldHaveTextIfString() {
    clipboardManager.setText("BLARG");
    assertThat(clipboardManager.hasText()).isTrue();
  }

  @Test
  public void shouldStorePrimaryClip() {
    ClipData clip = ClipData.newPlainText(null, "BLARG?");
    clipboardManager.setPrimaryClip(clip);
    assertThat(clipboardManager.getPrimaryClip()).isEqualTo(clip);
  }

  @Test
  public void shouldNotHaveTextIfPrimaryClipIsNull() {
    clipboardManager.setPrimaryClip(null);
    assertThat(clipboardManager.hasText()).isFalse();
  }

  @Test
  public void shouldNotHaveTextIfPrimaryClipIsEmpty() {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, ""));
    assertThat(clipboardManager.hasText()).isFalse();
  }

  @Test
  public void shouldHaveTextIfEmptyPrimaryClip() {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, " "));
    assertThat(clipboardManager.hasText()).isTrue();
  }

  @Test
  public void shouldHaveTextIfPrimaryClip() {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, "BLARG?"));
    assertThat(clipboardManager.hasText()).isTrue();
  }

  @Test
  public void shouldHavePrimaryClipIfText() {
    clipboardManager.setText("BLARG?");
    assertThat(clipboardManager.hasPrimaryClip()).isTrue();
  }

  @Test
  public void shouldFireListeners() {
    OnPrimaryClipChangedListener listener = mock(OnPrimaryClipChangedListener.class);
    clipboardManager.addPrimaryClipChangedListener(listener);
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, "BLARG?"));
    verify(listener).onPrimaryClipChanged();

    clipboardManager.removePrimaryClipChangedListener(listener);
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, "BLARG?"));
    verifyNoMoreInteractions(listener);
  }

  @Test
  @Config(minSdk = P)
  public void shouldClearPrimaryClip() {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, "BLARG?"));
    clipboardManager.clearPrimaryClip();

    assertThat(clipboardManager.hasText()).isFalse();
    assertThat(clipboardManager.hasPrimaryClip()).isFalse();
  }

  @Test
  @Config(minSdk = P)
  public void shouldClearPrimaryClipAndFireListeners() {
    OnPrimaryClipChangedListener listener = mock(OnPrimaryClipChangedListener.class);
    clipboardManager.addPrimaryClipChangedListener(listener);
    clipboardManager.clearPrimaryClip();

    verify(listener).onPrimaryClipChanged();
  }
}
