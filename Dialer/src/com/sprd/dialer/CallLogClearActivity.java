/*
 * SPRD: create
 */

package com.sprd.dialer;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.dialer.DialerApplication;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.google.common.collect.Lists;
import android.sim.Sim;
import android.sim.SimManager;
import neolink.telephony.PrivateIntents;
import neolink.telephony.PrivateMode;

public class CallLogClearActivity extends ListActivity implements CallLogClearColumn {
    private static final String TAG = "CallsDeleteFragment";
    private static final boolean DBG = true;
    private static final Executor sDefaultExecute = Executors.newCachedThreadPool();
    private static final String[] EMPTY_ARRAY = new String[0];

    /** SPRD: filter call log by call log type (outgoing? incoming? missed?) */
    private int mCallType = CallLogQueryHandler.CALL_TYPE_ALL;
    /** SPRD: filter call log by phoneId */
    private int mShowType = CallLogSetting.TYPE_ALL;
    private HashMap<Long, Long> mSelectId;
    private CallLogClearAdapter mAdapter;
    private CheckBox selectAllChcekbox;
    private View listDividerView;
    private TextView checkboxText;

    protected static final int MENU_OK = Menu.FIRST + 1;
    protected static final int MENU_CANCLE = Menu.FIRST;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            setContentView(R.layout.calls_delete_activity_uui);
        } else {
            setContentView(R.layout.calls_delete_activity);
        }

        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            checkboxText = (TextView) findViewById(R.id.checkbox_title);
            checkboxText.setText(R.string.select_all_sprd);
            selectAllChcekbox = (CheckBox) findViewById(R.id.checkbox_select_all);
            listDividerView = findViewById(R.id.listDivider);
            selectAllChcekbox.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    // TODO Auto-generated method stub
                    if (selectAllChcekbox.isChecked()) {
                        AsyncThread selectThread = new AsyncThread();
                        selectThread.execute(true);
                        checkboxText.setText(R.string.unselect_all);
                    } else {
                        AsyncThread unSelectThread = new AsyncThread();
                        unSelectThread.execute(false);
                        checkboxText.setText(R.string.select_all_sprd);
                    }
                }
            });
        }

        mShowType = CallLogSetting.getCallLogShowType(this);
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            mAdapter = new CallLogClearAdapter(this, R.layout.delete_calls_list_child_item_uui,
                    null);
        } else {
            mAdapter = new CallLogClearAdapter(this, R.layout.delete_calls_list_child_item, null);
        }

        mSelectId = new HashMap<Long, Long>();

        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);

        AsyncQueryThread thread = new AsyncQueryThread(getApplicationContext(), mShowType);
        thread.executeOnExecutor(sDefaultExecute);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            refreshAllCheckbox();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
            mAdapter = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!SprdUtils.UNIVERSE_UI_SUPPORT) {
            final MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.call_log_delete_options, menu);
        } else {
            super.onCreateOptionsMenu(menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            menu.clear();
            if (mSelectId.size() > 0) {
                menu.add(0, MENU_CANCLE, 0, R.string.cancel).setShowAsAction(
                        MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(0, MENU_OK, 0, R.string.doneButton).setShowAsAction(
                        MenuItem.SHOW_AS_ACTION_ALWAYS);
            } else {
                menu.add(0, MENU_CANCLE, 0, R.string.cancel).setShowAsAction(
                        MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        } else {
            final MenuItem delete_all = menu.findItem(R.id.delete_all);
            final MenuItem delete_select = menu.findItem(R.id.delete_selected);
            final MenuItem selectAll = menu.findItem(R.id.select_all);
            final MenuItem unselectAll = menu.findItem(R.id.unselect_all);
            int listCount = getListAdapter() == null ? 0 : getListAdapter().getCount();
            int selectCount = mSelectId.size();
            Log.d(TAG, "listCount = " + listCount + ", selectCount = " + selectCount);
            delete_all.setVisible(listCount > 0);
            delete_select.setVisible(selectCount > 0 && listCount > 0);
            selectAll.setVisible(listCount > 0);
            // SPRD: modify unselectAll setVisible 149 }
            unselectAll.setVisible(listCount > 0);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case MENU_OK:
                int listCount = getListAdapter() == null ? 0 : getListAdapter().getCount();
                int selectCount = mSelectId.size();
                if (selectCount == listCount && listCount > 0) {
                    Runnable runAll = getClearAllRunnable(getApplicationContext());
                    CallLogClearDialog.show(this, runAll, true);
                } else {
                    Runnable runSelect = getClearSelectCallLog(getApplicationContext());
                    CallLogClearDialog.show(this, runSelect, false);
                }
                break;
            case MENU_CANCLE:
                finish();
                break;
            case R.id.delete_all:
                Runnable runAll = getClearAllRunnable(getApplicationContext());
                CallLogClearDialog.show(this, runAll, true);
                return true;
            case R.id.delete_selected:
                Runnable runSelect = getClearSelectCallLog(getApplicationContext());
                CallLogClearDialog.show(this, runSelect, false);
                return true;
            case R.id.select_all:
                AsyncThread selectThread = new AsyncThread();
                selectThread.execute(true);
                return true;
            case R.id.unselect_all:
                AsyncThread unSelectThread = new AsyncThread();
                unSelectThread.execute(false);
                return true;
        }
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            invalidateOptionsMenu();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        CheckBox box = (CheckBox) v.findViewById(R.id.call_icon);
        box.setChecked(!box.isChecked());
        boolean checked = box.isChecked();
        Log.d(TAG, "position = " + position + ", id = " + id + ", checked = " + checked);
        if (checked) {
            mSelectId.put(id, id);
        } else {
            mSelectId.remove(id);
        }
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            refreshAllCheckbox();
        }
    }

    private void refreshAllCheckbox(){
            int listCount = getListAdapter() == null ? 0 : getListAdapter().getCount();
            int selectCount = mSelectId.size();
            Log.d(TAG, "listCount = " + listCount + ", selectCount = " + selectCount);
            if (selectCount < listCount && listCount > 0) {
                checkboxText.setText(R.string.select_all_sprd);
                selectAllChcekbox.setChecked(false);
            } else if (selectCount == listCount && listCount > 0) {
                checkboxText.setText(R.string.unselect_all);
                selectAllChcekbox.setChecked(true);
            }
            if(selectCount == 0){
                checkboxText.setText(R.string.select_all_sprd);
                selectAllChcekbox.setChecked(false);
            }
            invalidateOptionsMenu();
    }

    private final class CallLogClearAdapter extends ResourceCursorAdapter {
        public CallLogClearAdapter(Context context, int layout, Cursor cursor) {
            super(context, layout, cursor,FLAG_REGISTER_CONTENT_OBSERVER);
        }

        @Override
        public void bindView(View view, Context context, Cursor c) {
            ImageView iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            TextView line1View = (TextView) view.findViewById(R.id.line1);
            // TextView labelView = (TextView) view.findViewById(R.id.label);
            TextView numberView = (TextView) view.findViewById(R.id.number);
            TextView dateView = (TextView) view.findViewById(R.id.date);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.call_icon);
            checkBox.setFocusable(false);
            checkBox.setClickable(false);

            final long id = c.getLong(ID_COLUMN_INDEX);
            long date = c.getLong(DATE_COLUMN_INDEX);

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = number;
            String name = c.getString(CALLER_NAME_COLUMN_INDEX);
            int mode = c.getInt(CALL_MODE);
            Log.d(TAG, "mode=="+mode);
            if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                TextView simView = (TextView) view.findViewById(R.id.sim);
                SimManager simManager = SimManager.get(mContext);
                final String iccId = c.getString(SIM_NAME_INDEX);
                Sim sim = simManager.getSimByIccId(iccId);
                if (sim != null) {
                    String simName = sim.getName();
                    if (simName != null && !simName.isEmpty()) {

                        String str = sim.getName();
                        int index = str.indexOf(simName);
                        SpannableStringBuilder style = new SpannableStringBuilder(str);

                        boolean isActiveSim = false;
                        Sim[] sims = simManager.getSims();
                        if (sims != null) {
                            for (int i = 0; i < sims.length; i++) {
                                if (sims != null && iccId != null
                                        && iccId.equals(sims[i].getIccId()))
                                {
                                    isActiveSim = true;
                                }
                            }
                            if (isActiveSim) {
                                style.setSpan(new ForegroundColorSpan(sim.getColor()), index, index
                                        + simName.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                                if (simView != null)
                                    simView.setVisibility(View.VISIBLE);
                                    simView.setText(style);
                            } else {
                                style.setSpan(new ForegroundColorSpan(Color.GRAY), index, index
                                        + simName.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                                if (simView != null)
                                    simView.setVisibility(View.VISIBLE);
                                    simView.setText(style);
                            }
                        }
                    }
                }else{
                    if (simView != null){
                        simView.setVisibility(View.GONE);
                    }
                }
            } else {
                ImageView simView = (ImageView) view.findViewById(R.id.sim);
                int phoneId = 0;
                if (null != c) {
                    phoneId = c.getInt(SIM_COLUMN_INDEX);
                }
				if (mode == Calls.PUBLIC_CALL_PDT_FLAG) {
					simView.setBackgroundResource(R.drawable.ic_list_pdt);
				} else {

					int sourceId = SourceUtils
							.getSimDrawableFromPhoneId(phoneId);
					simView.setBackgroundResource(sourceId);
				}
            }

            if (!TextUtils.isEmpty(name)) {
                line1View.setText(name);
                numberView.setText(formattedNumber);
            } else {
                /* SPRD: modify for number unknown @{ */
                if (!TextUtils.isEmpty(number)) {
                    line1View.setText(number);
                } else {
                    line1View.setText(R.string.unknown);
                }
                /* @} */
                numberView.setVisibility(View.GONE);
            }

            if (iconView != null) {
                int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
                Drawable drawable = getResources().getDrawable(
                        SourceUtils.getDrawableFromCallType(type));
                iconView.setImageDrawable(drawable);
            }

            CharSequence  dateText;
            if(date <= System.currentTimeMillis()){
                dateText =
                        DateUtils.getRelativeTimeSpanString(date,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_NUMERIC_DATE);
            }else{
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                dateText = dateFormat.format(date);
            }

            dateView.setText(dateText);

            if (mSelectId.containsKey(id)) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }

            /*
             * checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener()
             * { public void onCheckedChanged(CompoundButton buttonView, boolean
             * isChecked) { if (isChecked) { mSelectId.put(id, id); } else {
             * mSelectId.remove(id); } } });
             */
        }

        @Override
        protected void onContentChanged() {
            super.onContentChanged();
            AsyncQueryThread thread = new AsyncQueryThread(getApplicationContext(), mShowType);
            thread.executeOnExecutor(sDefaultExecute);
        }
    }

    private class AsyncQueryThread extends AsyncTask<Void, Void, Cursor> {
        private int aShowType = CallLogSetting.TYPE_ALL;
        private int aCallType = CallLogQueryHandler.CALL_TYPE_ALL;
        private Context aContext;

        public AsyncQueryThread(Context context, int showType) {
            this(context, showType, CallLogQueryHandler.CALL_TYPE_ALL);
        }

        public AsyncQueryThread(Context context, int showType, int callType) {
            aContext = context;
            aShowType = showType;
            aCallType = callType;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            StringBuffer where = new StringBuffer();
            List<String> args = Lists.newArrayList();
            if (aShowType > CallLogSetting.TYPE_ALL) {
                where.append(String.format("(%s = ?)", Calls.PHONE_ID));
                args.add(Integer.toString(aShowType));
            }
            if (aCallType > CallLogQueryHandler.CALL_TYPE_ALL) {
                if (where.length() > 0) {
                    where.append(" AND ");
                }
                where.append(String.format("(%s = ?)", Calls.TYPE));
                args.add(Integer.toString(aCallType));
            }
        	if (DialerApplication.getApplication().mMode == PrivateMode.MODE_PDT_DIGITAL_NORMAL) {
				where.append(String.format("(%s = ?)", Calls.PHONE_ID));
				args.add("0");
        	}   
            ContentResolver cr = aContext.getContentResolver();
            final String selection = where.length() > 0 ? where.toString() : null;
            final String[] selectionArgs = args.toArray(EMPTY_ARRAY);
            Cursor c = cr.query(Calls.CONTENT_URI, CALL_LOG_PROJECTION, selection, selectionArgs,
                    "_id desc");
            return c;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            if (mAdapter != null) {
                mAdapter.changeCursor(result);
                invalidateOptionsMenu();
                if (mAdapter.isEmpty()) {
                    String emptyText = getString(R.string.recentCalls_empty);
                    ((TextView) (getListView().getEmptyView())).setText(emptyText);
                    if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                        checkboxText.setVisibility(View.GONE);
                        selectAllChcekbox.setVisibility(View.GONE);
                        listDividerView.setVisibility(View.GONE);
                    }
                }
            } else {
                result.close();
            }
        }
    };

    private class AsyncThread extends AsyncTask<Boolean, Void, Void> {
        @Override
        protected Void doInBackground(Boolean... params) {
            ListAdapter adapter = getListAdapter();
            int count = adapter.getCount();
            for (int i = 0; i < count; i++) {
                long id = adapter.getItemId(i);
                if (params[0] == true) {
                    mSelectId.put(id, id);
                } else {
                    mSelectId.remove(id);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            ListAdapter listAdapter = getListAdapter();
            if (listAdapter != null) {
                CallLogClearAdapter adapter = (CallLogClearAdapter) listAdapter;
                adapter.notifyDataSetChanged();
                if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                    invalidateOptionsMenu();
                }
            }
        }
    }

    private Runnable getClearAllRunnable(final Context context) {
        Runnable run = new Runnable() {
            public void run() {
                ContentResolver cr = context.getContentResolver();
                StringBuffer where = new StringBuffer();
                List<String> args = Lists.newArrayList();
                if (mCallType > CallLogQueryHandler.CALL_TYPE_ALL) {
                    where.append(String.format("(%s = ?)", Calls.TYPE));
                    args.add(Integer.toString(mCallType));
                }
                if (mShowType > CallLogSetting.TYPE_ALL) {
                    if (where.length() > 0) {
                        where.append(" AND ");
                    }
                    where.append(String.format("(%s = ?)", Calls.PHONE_ID));
                    args.add(Integer.toString(mShowType));
                }
                String deleteWhere = where.length() > 0 ? where.toString() : null;
                String[] selectionArgs = deleteWhere == null ? null : args.toArray(EMPTY_ARRAY);
                cr.delete(Calls.CONTENT_URI, deleteWhere, selectionArgs);
                if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                    finish();
                }
            }
        };
        return run;
    }

    private Runnable getClearSelectCallLog(final Context context) {
        Runnable run = new Runnable() {
            public void run() {
                ContentResolver cr = context.getContentResolver();
                StringBuffer where = new StringBuffer();
                where.append("calls._id in (");
                Set<Entry<Long, Long>> set = mSelectId.entrySet();
                Iterator<Entry<Long, Long>> iterator = set.iterator();
                boolean first = true;
                while (iterator.hasNext()) {
                    if (!first) {
                        where.append(",");
                    }
                    first = false;
                    Entry<Long, Long> entry = iterator.next();
                    long id = entry.getKey().longValue();
                    where.append(Long.toString(id));
                }
                where.append(")");
                cr.delete(Calls.CONTENT_URI, where.toString(), null);
                if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                    finish();
                }
            }
        };
        return run;
    }
}
