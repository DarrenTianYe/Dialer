package com.android.dialer.dialpad;

import com.android.dialer.R;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class DialMatchFragment extends Fragment implements OnItemClickListener {
	//private static final String TAG = "DialMatchFragment";
	//private static final boolean DBG = true;
  //  private DialMatchAdapter mAdapter;
    private View mView;
  //  protected ListView mListView;
  //  private ContactListEmptyView mEmptyView;
    protected Context mContext;
  //  private HandlerThread mHandlerThread;
   // private AsyncHandler mAsyncHandler;
  //  private Handler mHandler = new Handler();
  //  private Matching mMatch;
//
//	private class AsyncHandler extends Handler {
//		private ContentResolver cr;
//		public AsyncHandler(Looper looper, ContentResolver resolver) {
//			super(looper);
//			cr = resolver;
//		}
//
//		@Override
//		public void handleMessage(Message msg) {
//			long start = 0;
////			if (DBG) {
////				start = System.currentTimeMillis();
////			}
////			String query = msg.obj != null ? String.valueOf(msg.obj) : null;
////			// query call log
////			
////			Log.d(TAG, "query>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>.=="+query);
////			
////			Uri uri = Uri.parse("content://call_log/callsgroup").buildUpon()
////					.appendQueryParameter(Calls.ALLOW_VOICEMAILS_PARAM_KEY, "true").build();
////			Cursor cursor;
////			if (TextUtils.isEmpty(query)) {
////				cursor = cr.query(uri, PhoneQuery.PROJECTION_CALL_LOG,
////						String.format("(%s IS NULL)", Calls.CACHED_NAME), null, Calls.NUMBER);
////			} else {
////				cursor = cr.query(uri, PhoneQuery.PROJECTION_CALL_LOG,
////						String.format("(%s IS NULL) AND (%s like ? )", Calls.CACHED_NAME,Calls.NUMBER),
////						new String[] { "%" + query + "%" }, Calls.NUMBER);
////			}
////			if (start != 0) {
////				long now = System.currentTimeMillis();
////				Log.d(TAG, "query call log cost : " + (now - start));
////				start = now;
////			}
////
////			cursor = mMatch.dealWithCursor(Matching.QUERY_CALLLOG, cursor, query);
////
////			if (start != 0) {
////				long now = System.currentTimeMillis();
////				Log.d(TAG, "merage call log cursor cost : " + (now - start));
////				start = now;
////			}
////			runQueryCompleteOnUiThread(Matching.QUERY_CALLLOG, cursor);
////
////			// query contact
////			Builder builder;
////			if (TextUtils.isEmpty(query)) {
////				builder = Phone.CONTENT_URI.buildUpon();
////				builder.appendPath("");
////			} else {
////				builder = Uri.parse("content://call_log/calls/contacts").buildUpon();
////				builder.appendPath(query); // Builder will encode the query
////			}
////			uri = builder.appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").build();
////			cursor = cr.query(uri, PhoneQuery.PROJECTION_ALTERNATIVE, null, null, Phone.SORT_KEY_ALTERNATIVE);
////			cursor = mMatch.dealWithCursor(Matching.QUERY_CONTACTS,cursor, query);
////			runQueryCompleteOnUiThread(Matching.QUERY_CONTACTS, cursor);
//		}
//    }

//    private void runQueryCompleteOnUiThread(final int partition, final Cursor cursor){
//		mHandler.post(new Runnable() {
//			public void run() {
//				if (partition == Matching.QUERY_CONTACTS) {
//					mAdapter.changeCursor(0, cursor);
//				} else if (partition == Matching.QUERY_CALLLOG) {
//					mAdapter.changeCursor(1, cursor);
//				}
//			}
//		});
//    }

//	protected void startQuery(String filter, long delay) {
//		mAsyncHandler.removeMessages(Matching.QUERY_ALL_START);
//		Message msg = mAsyncHandler.obtainMessage(Matching.QUERY_ALL_START, filter);
//		msg.sendToTarget();
//	}

//	ContentObserver mContactsObserver = new ContentObserver(mAsyncHandler) {
//		@Override
//        public void onChange(boolean selfChange) {
//			super.onChange(selfChange);
//			startQuery(null, 0);
//		}
//	};

//	ContentObserver mCallLogObserver = new ContentObserver (mAsyncHandler) {
//		@Override
//        public void onChange(boolean selfChange) {
//			super.onChange(selfChange);
//			startQuery(null, 0);
//		}
//	};

	protected View inflateView(LayoutInflater inflater, ViewGroup container) {
		return inflater.inflate(R.layout.contact_list_content, null);
	}

	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mContext = activity;
	}

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
       onCreateView(inflater, container);
//		mHandlerThread = new HandlerThread("AsyncThread");
//		mHandlerThread.start();
//		mAsyncHandler = new AsyncHandler(mHandlerThread.getLooper(),
//				getActivity().getContentResolver());
	//	mMatch = new Matching();

       // mAdapter = new DialMatchAdapter(getActivity());
       /// mAdapter.setParent(this);
      //  mListView.setAdapter(mAdapter);
      //  mListView.setOnItemClickListener(this);

//        mContext.getContentResolver().registerContentObserver(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, true,mContactsObserver);
//        mContext.getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,true, mCallLogObserver);
        return mView;
    }

    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        mView = inflateView(inflater, container);

     //   mListView = (ListView)mView.findViewById(android.R.id.list);
//        if (mListView == null) {
//            throw new RuntimeException(
//                    "Your content must have a ListView whose id attribute is " +
//                    "'android.R.id.list'");
//        }
//
//        View emptyView = mView.findViewById(com.android.internal.R.id.empty);
//        if (emptyView != null) {
//            mListView.setEmptyView(emptyView);
//            if (emptyView instanceof ContactListEmptyView) {
//                mEmptyView = (ContactListEmptyView)emptyView;
//            }
//        }

    }

	@Override
	public void onDestroyView() {
		super.onDestroyView();
//		if (mAsyncHandler != null) {
//			mAsyncHandler.removeMessages(Matching.QUERY_ALL_START);
//		}
//		if (mHandlerThread != null) {
//			mHandlerThread.quit();
//			mHandlerThread = null;
//		}
//		mContext.getContentResolver().unregisterContentObserver(mContactsObserver);
//		mContext.getContentResolver().unregisterContentObserver(mCallLogObserver);
	}

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

    }
}
