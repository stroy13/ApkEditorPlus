package com.gmail.heagoo.common;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

/** Framework preference screens must use the same palette as the rest of the app. */
public class ThemedPreferenceActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EditorTheme.apply(this);
        super.onCreate(savedInstanceState);
        stylePreferenceList();
    }

    /** Give legacy preference screens the same inset, card-based surfaces as the home screen. */
    private void stylePreferenceList() {
        final ListView list = getListView();
        if (list == null) return;
        final int cardBackground = themedAttribute("labCardBackground");
        final int homeBackground = themedAttribute("labHomeBackground");
        final int accent = themedAttribute("labAccent");
        if (homeBackground != 0) list.setBackgroundColor(homeBackground);
        int horizontal = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
        list.setPadding(horizontal, horizontal / 2, horizontal, horizontal);
        list.setClipToPadding(false);
        list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setDividerHeight((int) (7 * getResources().getDisplayMetrics().density + 0.5f));
        list.setSelector(android.R.color.transparent);
        AbsListView.OnScrollListener styling = new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) { }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (list.getAdapter() == null) return;
                for (int i = 0; i < visible; i++) {
                    View row = list.getChildAt(i);
                    int position = first + i;
                    Object item = position < list.getAdapter().getCount() ? list.getAdapter().getItem(position) : null;
                    if (item instanceof PreferenceCategory) {
                        row.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        TextView title = row.findViewById(android.R.id.title);
                        if (title != null && accent != 0) {
                            title.setTextColor(accent);
                            title.setTextSize(12);
                            title.setLetterSpacing(0.04f);
                        }
                    } else if (item instanceof Preference && cardBackground != 0) {
                        row.setBackgroundResource(cardBackground);
                    }
                }
            }
        };
        list.setOnScrollListener(styling);
        list.post(() -> {
            if (list.getAdapter() != null)
                styling.onScroll(list, list.getFirstVisiblePosition(), list.getChildCount(), list.getAdapter().getCount());
        });
    }

    private int themedAttribute(String name) {
        int id = getResources().getIdentifier(name, "attr", getPackageName());
        if (id == 0) return 0;
        android.util.TypedValue value = new android.util.TypedValue();
        return getTheme().resolveAttribute(id, value, true)
                ? (value.resourceId != 0 ? value.resourceId : value.data) : 0;
    }
}
