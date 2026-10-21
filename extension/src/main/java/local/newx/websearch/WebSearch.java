package local.newx.websearch;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import kotlin.jvm.functions.Function1;

/** Receives X's selected-text callback without reading or changing the system clipboard. */
public final class WebSearch implements Function1<String, Object> {
    private final Context context;

    private WebSearch(Context context) {
        this.context = context;
    }

    public static Function1<String, Object> create(View view) {
        return new WebSearch(view.getContext());
    }

    public static MenuItem addMenuItem(Menu menu, int group, int id, int order) {
        Resources resources = Resources.getSystem();
        int labelId = resources.getIdentifier("websearch", "string", "android");
        CharSequence label = labelId == 0 ? "Web search" : resources.getText(labelId);
        return menu.add(group, id, order, label);
    }

    @Override
    public Object invoke(String selectedText) {
        if (selectedText == null || selectedText.trim().isEmpty()) return null;
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, selectedText)
            .putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // Starting directly avoids Android's package-visibility filtering of resolveActivity.
            context.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(context,
                "No app can open web searches. Install or enable a browser or search app.",
                Toast.LENGTH_LONG).show();
        } catch (SecurityException exception) {
            Toast.makeText(context,
                "Android blocked the web search. Check your default search app and its restrictions.",
                Toast.LENGTH_LONG).show();
        }
        // X discards Function1's return value. Avoid Unit.INSTANCE, which R8 renames in X.
        return null;
    }
}
