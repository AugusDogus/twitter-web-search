package local.newx.websearch;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenu;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class WebSearchTest {
    @Test
    public void searchesSelectedTextUnchangedWithoutHardcodingAProvider() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            String selection = "Kotlin & Compose \"selection\" 日本語 🔎";
            WebSearch.create(new View(activity)).invoke(selection);
            Intent intent = shadowOf(activity).getNextStartedActivity();
            assertEquals(Intent.ACTION_WEB_SEARCH, intent.getAction());
            assertEquals(selection, intent.getStringExtra(SearchManager.QUERY));
            assertNull(intent.getPackage());
            assertNull(intent.getComponent());
            assertNull(intent.getData());
        }
    }

    @Test
    public void emptySelectionDoesNotLaunchAnActivity() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            var callback = WebSearch.create(new View(activity));
            callback.invoke(null);
            callback.invoke(" \n\t");
            assertNull(shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void replacementPreservesMenuIdentityAndOrder() {
        Menu menu = new RoboMenu(RuntimeEnvironment.getApplication());
        var item = WebSearch.addMenuItem(menu, 2, 1234, 1);
        assertEquals(2, item.getGroupId());
        assertEquals(1234, item.getItemId());
        assertEquals(1, item.getOrder());
        // Android 15's English framework resource capitalizes both words.
        assertEquals("Web Search", item.getTitle().toString());
    }

    @Test
    public void missingSearchAppReportsHowToRecover() {
        var context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public void startActivity(Intent intent) {
                throw new ActivityNotFoundException();
            }
        };
        WebSearch.create(new View(context)).invoke("selected words");
        assertTrue(ShadowToast.getTextOfLatestToast().contains("Install or enable"));
    }
}
