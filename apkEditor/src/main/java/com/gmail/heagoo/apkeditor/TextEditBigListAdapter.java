package com.gmail.heagoo.apkeditor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;
import com.gmail.heagoo.SelectionChangedListener;
import com.gmail.heagoo.apkeditor.base.R;
import com.gmail.heagoo.apkeditor.ui.EditTextRememberCursor;
import com.gmail.heagoo.apkeditor.ui.LayoutObListView;
import com.gmail.heagoo.neweditor.ColorTheme;
import com.gmail.heagoo.neweditor.Document;
import com.gmail.heagoo.neweditor.SyntaxSnapshot;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class TextEditBigListAdapter extends BaseAdapter {
    private int complexUnitSp;
    private Context ctx;
    private Document document;
    private float fontSize;
    private WeakReference<LayoutObListView> listRef;
    private WeakReference<SelectionChangedListener> selectionListenerRef;
    private TextWatcher textChangeListener;
    private int textColor = -1;
    private int backgroundColor = -14671580;
    private int gutterColor = -15000546;
    private int lineNumberColor = -8025197;
    private int dividerColor = -13289669;
    private int currentLineColor = -14079185;
    private int selectedLine = -1;
    private boolean bShowLineNumbers = true;
    private MyHandler handler = new MyHandler();
    private final Handler syntaxUi = new Handler(Looper.getMainLooper());
    private final ExecutorService syntaxWorker = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "big-editor-syntax");
            thread.setDaemon(true);
            return thread;
        }
    });
    private final AtomicInteger syntaxRevision = new AtomicInteger();
    private final Map<Integer, SyntaxSnapshot> highlightedLines = new LinkedHashMap<Integer, SyntaxSnapshot>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, SyntaxSnapshot> eldest) {
            return size() > 256;
        }
    };
    private final Set<Integer> highlightPending = new HashSet<>();
    private volatile boolean closed;
    private List<String> lines = new ArrayList<>();

    public TextEditBigListAdapter(Context ctx, LayoutObListView list) {
        this.ctx = ctx;
        this.listRef = new WeakReference<>(list);
    }

    private void invalidateSyntax() {
        this.syntaxRevision.incrementAndGet();
        this.highlightedLines.clear();
        this.highlightPending.clear();
    }

    private void requestSyntaxHighlight(final int position, final String source) {
        final Document doc = this.document;
        if (this.closed || doc == null || position < 0 || position >= this.lines.size()) {
            return;
        }
        SyntaxSnapshot cached = this.highlightedLines.get(Integer.valueOf(position));
        if (cached != null) {
            applySyntaxHighlight(position, cached);
            return;
        }
        final Integer key = Integer.valueOf(position);
        if (!this.highlightPending.add(key)) {
            return;
        }
        final int expected = this.syntaxRevision.get();
        this.syntaxWorker.execute(new Runnable() {
            @Override
            public void run() {
                SyntaxSnapshot parsed = null;
                try {
                    parsed = doc.syntaxSnapshot(source, new BooleanSupplier() {
                        @Override
                        public boolean getAsBoolean() {
                            return TextEditBigListAdapter.this.closed
                                    || TextEditBigListAdapter.this.syntaxRevision.get() != expected;
                        }
                    });
                } catch (RuntimeException ignored) {
                }
                final SyntaxSnapshot ready = parsed;
                TextEditBigListAdapter.this.syntaxUi.post(new Runnable() {
                    @Override
                    public void run() {
                        TextEditBigListAdapter.this.finishSyntaxHighlight(position, source, expected, ready);
                    }
                });
            }
        });
    }

    private void finishSyntaxHighlight(int position, String source, int expected,
                                       SyntaxSnapshot snapshot) {
        if (this.closed || this.syntaxRevision.get() != expected) {
            return;
        }
        this.highlightPending.remove(Integer.valueOf(position));
        if (snapshot == null || position >= this.lines.size()
                || !source.equals(this.lines.get(position))) {
            return;
        }
        this.highlightedLines.put(Integer.valueOf(position), snapshot);
        applySyntaxHighlight(position, snapshot);
    }

    private void applySyntaxHighlight(int position, SyntaxSnapshot snapshot) {
        LayoutObListView list = this.listRef.get();
        if (list == null) {
            return;
        }
        int first = list.getFirstVisiblePosition();
        for (int childIndex = 0; childIndex < list.getChildCount(); childIndex++) {
            if (first + childIndex != position) {
                continue;
            }
            View child = list.getChildAt(childIndex);
            Object tag = child.getTag();
            if (!(tag instanceof ViewHolder)) {
                return;
            }
            ViewHolder holder = (ViewHolder) tag;
            Object rowPosition = holder.et.getTag(R.id.text);
            if (!(rowPosition instanceof Integer)
                    || ((Integer) rowPosition).intValue() != position) {
                return;
            }
            String current = holder.et.getText().toString();
            if (position >= this.lines.size() || !current.equals(this.lines.get(position))) {
                return;
            }
            this.document.applySyntaxSnapshot(holder.et.getText(), snapshot);
            return;
        }
    }

    public void close() {
        this.closed = true;
        this.syntaxRevision.incrementAndGet();
        this.highlightPending.clear();
        this.syntaxUi.removeCallbacksAndMessages(null);
        this.syntaxWorker.shutdownNow();
    }

    public void setSelectionChangedListener(SelectionChangedListener selectionChangedListener) {
        this.selectionListenerRef = new WeakReference<>(selectionChangedListener);
    }

    @Override
    public int getCount() {
        return this.lines.size();
    }

    @Override
    public Object getItem(int position) {
        return this.lines.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void reclaimFocus(View v, long timestamp) {
        if (timestamp != -1 && System.currentTimeMillis() - timestamp < 250) {
            v.requestFocus();
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(this.ctx).inflate(R.layout.item_lined_text, (ViewGroup) null);
            viewHolder = new ViewHolder();
            viewHolder.root = convertView;
            viewHolder.lineTv = (TextView) convertView.findViewById(R.id.line_num);
            viewHolder.divider = convertView.findViewById(R.id.line_divider);
            viewHolder.et = convertView.findViewById(R.id.text);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        Object tag = viewHolder.et.getTag();
        if (tag != null) {
            viewHolder.et.removeTextChangedListener((CustomTextWatcher) tag);
        }
        CustomTextWatcher watcher = new CustomTextWatcher(position);
        viewHolder.et.addTextChangedListener(watcher);
        viewHolder.et.setTag(watcher);
        viewHolder.et.setTextSelectionListener(this.selectionListenerRef);
        String strNum = getNumString(position + 1);
        if (this.bShowLineNumbers) {
            viewHolder.lineTv.setVisibility(0);
            viewHolder.divider.setVisibility(0);
            viewHolder.lineTv.setText(strNum);
        } else {
            viewHolder.lineTv.setVisibility(8);
            viewHolder.divider.setVisibility(8);
        }
        String source = this.lines.get(position);
        viewHolder.et.setTextColor(this.textColor);
        viewHolder.et.setText(source);
        if (this.fontSize > 0.0f) {
            viewHolder.lineTv.setTextSize(this.complexUnitSp, this.fontSize);
            viewHolder.et.setTextSize(this.complexUnitSp, this.fontSize);
        }
        viewHolder.et.setTag(R.id.text, Integer.valueOf(position));
        viewHolder.et.setParent(this.ctx, this.listRef);
        if (this.document != null) {
            this.document.clearSyntaxHighlighting(viewHolder.et.getText(), 0,
                    viewHolder.et.getText().length());
            requestSyntaxHighlight(position, source);
        }
        viewHolder.et.selectionClickable();
        applyRowColors(viewHolder, position);
        viewHolder.et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public final void onFocusChange(View view, boolean z) {
                TextEditBigListAdapter.this.m322x7b561881(position, view, z);
            }
        });
        LayoutObListView lv = this.listRef.get();
        if (lv != null && lv.getSelPosition() == position) {
            viewHolder.et.setSelection(lv.getSelStart(), lv.getSelEnd());
            viewHolder.et.requestFocus();
            this.handler.setTargetView(viewHolder.et);
            this.handler.removeMessages(0);
            this.handler.sendEmptyMessageDelayed(0, 100L);
        }
        return convertView;
    }

    void m322x7b561881(int position, View v, boolean hasFocus) {
        if (hasFocus) {
            this.selectedLine = position;
            updateVisibleRows();
        }
    }

    private void applyRowColors(ViewHolder holder, int position) {
        boolean current = position == this.selectedLine;
        holder.root.setBackgroundColor(current ? this.currentLineColor : this.backgroundColor);
        holder.lineTv.setBackgroundColor(this.gutterColor);
        holder.lineTv.setTextColor(current ? this.textColor : this.lineNumberColor);
        holder.divider.setBackgroundColor(this.dividerColor);
    }

    private void updateVisibleRows() {
        LayoutObListView list = this.listRef.get();
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof ViewHolder) {
                ViewHolder holder = (ViewHolder) tag;
                Object position = holder.et.getTag(R.id.text);
                if (position instanceof Integer) {
                    applyRowColors(holder, ((Integer) position).intValue());
                }
            }
        }
    }

    private int getNumberDigits(int lines) {
        int nd = 1;
        while (lines >= 10) {
            lines /= 10;
            nd++;
        }
        return nd;
    }

    private String getPaddingString(int digits) {
        switch (digits) {
            case 1:
                return "0";
            case 2:
                return "00";
            case 3:
                return "000";
            case 4:
                return "0000";
            case 5:
                return "00000";
            case 6:
                return "000000";
            default:
                return "0000000";
        }
    }

    private String getNumString(int num) {
        int digits = getNumberDigits(this.lines.size());
        int cur = getNumberDigits(num);
        if (cur < digits) {
            return getPaddingString(digits - cur) + num;
        }
        return "" + num;
    }

    public void setTextSize(int complexUnitSp, float fontSize) {
        this.complexUnitSp = complexUnitSp;
        this.fontSize = fontSize;
    }

    public void refresh() {
        invalidateSyntax();
        notifyDataSetChanged();
    }

    public CharSequence getText() {
        StringBuilder sb = new StringBuilder();
        if (this.lines.size() > 0) {
            sb.append(this.lines.get(0));
            for (int i = 1; i < this.lines.size(); i++) {
                sb.append(Document.LINE_SEPARATOR_UNIX);
                sb.append(this.lines.get(i));
            }
        }
        return sb.toString();
    }

    public void setText(List<String> text) {
        this.selectedLine = -1;
        invalidateSyntax();
        this.lines = text == null ? new ArrayList<>() : text;
        LayoutObListView list = this.listRef.get();
        if (list != null) {
            list.resetSelection();
        }
    }

    public List<String> getTextLines() {
        return this.lines;
    }

    public void setDocument(Document document) {
        this.document = document;
        invalidateSyntax();
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public void setCodeTheme(ColorTheme theme) {
        this.backgroundColor = theme.getBackgroundColor();
        this.gutterColor = theme.getGutterColor();
        this.lineNumberColor = theme.getLineNumberColor();
        this.dividerColor = theme.getDividerColor();
        this.currentLineColor = theme.getCurrentLineColor();
        updateVisibleRows();
    }

    public void setShowLineNumbers(boolean bShow) {
        this.bShowLineNumbers = bShow;
    }

    public void setTextChangeListener(TextWatcher textChangeListener) {
        this.textChangeListener = textChangeListener;
    }

    static class SelectionRecord {
        long time = 0;
        int position = -1;
        int selStart = 0;
        int selEnd = 0;

        SelectionRecord() {
        }
    }

    static class ViewHolder {
        View divider;
        EditTextRememberCursor et;
        TextView lineTv;
        View root;

        ViewHolder() {
        }
    }

    class MyHandler extends Handler {
        View target;

        MyHandler() {
        }

        public void setTargetView(EditText target) {
            this.target = target;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    this.target.requestFocus();
                    break;
            }
        }
    }

    private class CustomTextWatcher implements TextWatcher {
        private boolean isSetInitialText = true;
        private int position;

        public CustomTextWatcher(int position) {
            this.position = position;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (this.isSetInitialText) {
                this.isSetInitialText = false;
                return;
            }
            if (this.position < TextEditBigListAdapter.this.lines.size()) {
                String updated = s.toString();
                TextEditBigListAdapter.this.lines.set(this.position, updated);
                TextEditBigListAdapter.this.invalidateSyntax();
                TextEditBigListAdapter.this.requestSyntaxHighlight(this.position, updated);
            }
            if (TextEditBigListAdapter.this.textChangeListener != null) {
                TextEditBigListAdapter.this.textChangeListener.afterTextChanged(s);
            }
        }
    }
}
