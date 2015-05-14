/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.dialpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.CallLog.Calls;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.util.PhoneNumberFormatter;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.DialtactsActivity.ViewPagerVisibilityListener;
import com.android.dialer.R;
import com.android.dialer.SpecialCharSequenceMgr;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.MsmsConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.common.HapticFeedback;

import neolink.telephony.PrivateIntents;
import neolink.telephony.PrivateManager;
import neolink.telephony.PrivateMode;

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class DialpadFragment extends DialMatchFragment implements
		View.OnLongClickListener, View.OnKeyListener,
		AdapterView.OnItemClickListener, TextWatcher,
		PopupMenu.OnMenuItemClickListener, OnClickListener,
		ViewPagerVisibilityListener {
	// private static final String TAG = DialpadFragment.class.getSimpleName();
	private static final String TAG = "DialpadFragment";

	private static final String EMPTY_NUMBER = "";

	/** The length of DTMF tones in milliseconds */
	private static final int TONE_LENGTH_MS = 150;

	/** The DTMF tone volume relative to other sounds in the stream */
	private static final int TONE_RELATIVE_VOLUME = 80;
	private static final int QUERY_OUTGOING_CALL = 1;
	public int mShowFlag = 0;
	private HandlerThread mHandler;
	private QueryHandler queryCallLogHandler;
	/**
	 * Stream type used to play the DTMF tones off call, and mapped to the
	 * volume control keys
	 */
	private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_MUSIC;
	private static final String SHARED_PREFERENCES_NAME = "fast_dial_numbers";
	private static final String SHARED_PREFERENCES_NAME0 = "fast_dial_numbers0";
	public static final String EXTRA_IS_VIDEOCALL = "android.phone.extra.IS_VIDEOCALL";

	private SharedPreferences mFsatDialSharedPreferences;
	private SharedPreferences mPDTFsatDialSharedPreferences;
	private SharedPreferences mMPTFsatDialSharedPreferences;
	protected static final String PDT_FAST_DIAL_NUM_PROVIDER = "pdt_fast_dial_num_provider";
	protected static final String MPT_FAST_DIAL_NUM_PROVIDER = "mpt_fast_dial_num_provider";
	protected static final String FAST_DIAL_NUM = "fast_dial_num_";
	public static int MODE = Context.MODE_WORLD_READABLE
			+ Context.MODE_WORLD_WRITEABLE;
	private  PrivateManager sInstance;
	public static final int CALL_STATE_IDLE = 1;

	public interface Listener {
		public void onSearchButtonPressed();
	}

	/**
	 * View (usually FrameLayout) containing mDigits field. This can be null, in
	 * which mDigits isn't enclosed by the container.
	 */
	private View mDigitsContainer;
	private EditText mDigits;
	private View mMainDial;

	private View mDelete;
	private ToneGenerator mToneGenerator;
	private Object mToneGeneratorLock = new Object();
	private View mDialpad;
	private View mAdditionalButtonsRow;
	private SlidingDrawer mDialerDrawer;
	private ImageView mShowView;

	private View mVideoButton;
	private Listener mListener;

	public View mCard1DialButton;
	//public View mCard2DialButton;
	public View mPDialButton;
	private ListView mDialpadChooser;
	private DialpadChooserAdapter mDialpadChooserAdapter;
	private String mFastPhoneNumber1 = "";
	private String mFastPhoneNumber2 = "";
	private String oldStr = "";

	public static int mCallDefault = -1; // default mode
	private static int mCallCard1 = 0; // call by card1
	private static int mCallCard2 = 1; // call by card2
	private static int mCallPrivatePhone = 2; // call by PrivatePhone
	public static int mCallPrivatePhoneByPTT = 3; // call by pdt key
	private static int mCallEmergency = 4;
	private static int mCallBroadCast = 5;
	public static int CONTEXT_MODE = Context.MODE_WORLD_READABLE
			+ Context.MODE_WORLD_WRITEABLE;
	/**
	 * Regular expression prohibiting manual phone call. Can be empty, which
	 * means "no rule".
	 */
	private String mProhibitedPhoneNumberRegexp;

	private boolean mShowOptionsMenu;

	private double yStart;
	private double xStart;
	private double yEnd;
	private double xEnd;
	private boolean isCan;
	// Last number dialed, retrieved asynchronously from the call DB
	// in onCreate. This number is displayed when the user hits the
	// send key and cleared in onPause.
	private String mLastNumberDialed = EMPTY_NUMBER;
	private String mLastVideoNumberDialed = EMPTY_NUMBER;

	// determines if we want to playback local DTMF tones.
	private boolean mDTMFToneEnabled;

	// Vibration (haptic feedback) for dialer key presses.
	private HapticFeedback mHaptic = new HapticFeedback();

	/** Identifier for the "Add Call" intent extra. */
	static final String ADD_CALL_MODE_KEY = "add_call_mode";

	/**
	 * Identifier for intent extra for sending an empty Flash message for CDMA
	 * networks. This message is used by the network to simulate a press/depress
	 * of the "hookswitch" of a landline phone. Aka "empty flash".
	 * 
	 * TODO: Using an intent extra to tell the phone to send this flash is a
	 * temporary measure. To be replaced with an ITelephony call in the future.
	 * TODO: Keep in sync with the string defined in
	 * OutgoingCallBroadcaster.java in Phone app until this is replaced with the
	 * ITelephony API.
	 */
	static final String EXTRA_SEND_EMPTY_FLASH = "com.android.phone.extra.SEND_EMPTY_FLASH";

	private String mCurrentCountryIso;

	private class QueryHandler extends Handler {

		QueryHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case QUERY_OUTGOING_CALL:
				queryLastOutgoingCall();
				Activity activity = getActivity();
				if (activity != null) {
					activity.runOnUiThread(new Runnable() {
						public void run() {
							updateDialAndDeleteButtonEnabledState();
						}
					});
				}
				break;
			}
		}

	}

	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		/**
		 * Listen for phone state changes so that we can take down the
		 * "dialpad chooser" if the phone becomes idle while the chooser UI is
		 * visible.
		 */
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			// Log.i(TAG, "PhoneStateListener.onCallStateChanged: "
			// + state + ", '" + incomingNumber + "'");
			Log.d(TAG, "state==" + state + "incomingNumber==" + incomingNumber
					+ "phoneIsInUse()==" + phoneIsInUse()
					+ "phoneAndSimIsInUse()==" + phoneAndSimIsInUse());
			
			int sim_1_status = -1;
			int sim_2_status = -1;
			
			Log.d(TAG, "TelephonyManager.isMultiSim()=="+TelephonyManager.isMultiSim());
			if (TelephonyManager.isMultiSim()) {
				for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
					// SPRD: modify for bug282643
					
					Log.d(TAG, "TelephonyManager.getDefault(i).getSimState()=="+TelephonyManager.getDefault(i).getSimState());
					if (TelephonyManager.getDefault(i).getSimState() == TelephonyManager.SIM_STATE_READY) {

						if (i == 0) {
							sim_1_status = TelephonyManager.getDefault(0)
									.getSimState();
						} else {

							sim_2_status = TelephonyManager.getDefault(1)
									.getSimState();
						}

					}
				}
			} else {
				Log.d(TAG, "TelephonyManager.getDefault(i).getSimState()=="
						+ TelephonyManager.getDefault(0).getSimState());
				//if (TelephonyManager.getDefault().getSimState() != TelephonyManager.SIM_STATE_READY) {
					sim_1_status = TelephonyManager.getDefault(0).getSimState();

					if (sim_1_status == TelephonyManager.SIM_STATE_READY) {
						mCard1DialButton
								.setBackgroundResource(R.drawable.new_private_dialbutton_pressed);
					} else {
						mCard1DialButton
								.setBackgroundResource(R.drawable.new_private_dial_privatecall_disable_bg);
					}

				//}
			}
//            Log.d(TAG, "sim_1_status=="+sim_1_status+"sim_2_status=="+sim_2_status);
//			
//			if (!phoneIsInUse()) {
//				mDigits.setHint(null);
//			}
//			if (phoneAndSimIsInUse() == 0) {
////				mCard2DialButton.setEnabled(false);
////				mCard2DialButton
////						.setBackgroundResource(R.drawable.new_private_dial_privatecall_disable_bg);
//
//			} else if (phoneAndSimIsInUse() == 1) {
//				mCard1DialButton.setEnabled(false);
//				mCard1DialButton
//						.setBackgroundResource(R.drawable.new_private_dial_privatecall_disable_bg);
//			} else {
//
//				//if (TelephonyManager.getDefault(0).getSimState() == TelephonyManager.SIM_STATE_READY) {
//					mCard1DialButton.setEnabled(true);
//					mCard1DialButton.setClickable(true);
//					mCard1DialButton
//							.setBackgroundResource(R.drawable.new_private_dialbutton_pressed);
//				//}
//				//if (TelephonyManager.getDefault(1).getSimState() == TelephonyManager.SIM_STATE_READY) {
////					mCard2DialButton.setEnabled(true);
////					mCard2DialButton.setClickable(true);
////					mCard2DialButton
////							.setBackgroundResource(R.drawable.new_private_dialbutton_pressed);
//				//}
//			}
//			if ((state == TelephonyManager.CALL_STATE_IDLE)
//					&& dialpadChooserVisible()) {
				// Log.i(TAG,
				// "Call ended with dialpad chooser visible!  Taking it down...");
				// Note there's a race condition in the UI here: the
				// dialpad chooser could conceivably disappear (on its
				// own) at the exact moment the user was trying to select
				// one of the choices, which would be confusing. (But at
				// least that's better than leaving the dialpad chooser
				// onscreen, but useless...)
/*				if (!phoneIsInUse()) {
					showDialpadChooser(false);
				}*/
			//}
		}
	};

	private boolean mWasEmptyBeforeTextChange;
	/**
	 * This field is set to true while processing an incoming DIAL intent, in
	 * order to make sure that SpecialCharSequenceMgr actions can be triggered
	 * by user input but *not* by a tel: URI passed by some other app. It will
	 * be set to false when all digits are cleared.
	 */
	private boolean mDigitsFilledByIntent;

	private int mCurrentMode;
	private static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
	}

	public void onTextChanged(CharSequence input, int start, int before,
			int changeCount) {
		if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
			final Activity activity = getActivity();
			if (activity != null) {
				activity.invalidateOptionsMenu();
			}
		}

		// DTMF Tones do not need to be played here any longer -
		// the DTMF dialer handles that functionality now.
	}

	public void afterTextChanged(Editable input) {
		// When DTMF dialpad buttons are being pressed, we delay
		// SpecialCharSequencMgr sequence,
		// since some of SpecialCharSequenceMgr's behavior is too abrupt for the
		// "touch-down"
		// behavior.
		if (getActivity() != null
				&& !mDigitsFilledByIntent
				&& SpecialCharSequenceMgr.handleChars(getActivity(),
						input.toString(), mDigits)) {
			// A special sequence was entered, clear the digits
			mDigits.getText().clear();
		}
		String newStr = mDigits.getText().toString().replaceAll(" ", "");
		// if(!(TextUtils.equals(oldStr,newStr))){
		// oldStr = newStr;
		// startQuery(newStr ,0);
		// }
		if (isDigitsEmpty()) {
			mDigitsFilledByIntent = false;
			mDigits.setCursorVisible(false);
		}

		updateDialAndDeleteButtonEnabledState();
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		//mCurrentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());
		mCurrentMode = Settings.Secure.getInt(getActivity()
				.getContentResolver(), Settings.Secure.PRIVATE_PHONE_MODE,
				PrivateMode.MODE_UNKNOWN);
		try {
			mHaptic.init(
					getActivity(),
					getResources().getBoolean(
							R.bool.config_enable_dialer_key_vibration));
		} catch (Resources.NotFoundException nfe) {
			Log.e(TAG, "Vibrate control bool missing.", nfe);
		}
		mHandler = new HandlerThread("searchCallLog");
		mHandler.start();
		queryCallLogHandler = new QueryHandler(mHandler.getLooper());
		setHasOptionsMenu(true);

		mProhibitedPhoneNumberRegexp = getResources().getString(
				R.string.config_prohibited_phone_number_regexp);

		if (state != null) {
			mDigitsFilledByIntent = state
					.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
		}

		sInstance = new PrivateManager(getActivity());
	}

	protected View inflateView(LayoutInflater inflater, ViewGroup container) {
		return inflater.inflate(R.layout.dialpad_list_fragment, null);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedState) {
		View fragmentView = super.onCreateView(inflater, container, savedState);

		// Load up the resources for the text field.
		Resources r = getResources();

		mDigitsContainer = fragmentView.findViewById(R.id.digits_container);
		mDigits = (EditText) fragmentView.findViewById(R.id.digits);
		mDigits.setSingleLine();
		mDigits.setKeyListener(DialerKeyListener.getInstance());
		mDigits.setOnClickListener(this);
		mDigits.setOnKeyListener(this);
		mDigits.setOnLongClickListener(this);
		mDigits.addTextChangedListener(this);

		// if(mListView != null){
		// mListView.setOnScrollListener(new ListView.OnScrollListener() {
		// @Override
		// public void onScrollStateChanged(AbsListView view, int scrollState) {
		// if (mDialerDrawer.isOpened()){
		// mDialerDrawer.animateClose();
		// mShowView.setImageResource(R.drawable.ablepad);
		// }
		// }
		// @Override
		// public void onScroll(AbsListView view, int firstVisibleItem,
		// int visibleItemCount, int totalItemCount) {
		//
		// }
		// }
		// );
		// }else {
		// Log.d(TAG, "not found listview ");
		// }
		PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(),
				mDigits);

		// Soft menu button should appear only when there's no hardware menu
		// button.
		final View overflowMenuButton = fragmentView
				.findViewById(R.id.overflow_menu);
		if (overflowMenuButton != null) {
			if (ViewConfiguration.get(getActivity()).hasPermanentMenuKey()) {
				overflowMenuButton.setVisibility(View.GONE);
			} else {
				overflowMenuButton.setOnClickListener(this);
			}
		}
		// Check for the presence of the keypad
		View oneButton = fragmentView.findViewById(R.id.one);
		if (oneButton != null) {
			setupKeypad(fragmentView);
		}
		mMainDial = fragmentView.findViewById(R.id.showdial);
		mAdditionalButtonsRow = fragmentView
				.findViewById(R.id.dialpadAdditionalButtons);

		mVideoButton = mAdditionalButtonsRow.findViewById(R.id.videoButton);

		if (mVideoButton != null) {
//			if (PhoneCapabilityTester.isVtelIntentRegistered(mContext)) {
//				mVideoButton.setOnClickListener(this);
//			} else {
//				View divider = mAdditionalButtonsRow
//						.findViewById(R.id.after_dialButton_divider);
//				if (divider != null) {
//					divider.setVisibility(View.GONE);
//				}
//				mVideoButton.setVisibility(View.GONE);
//			}
		}
		// Check whether we should show the onscreen "Dial" button.
		mCard1DialButton = mAdditionalButtonsRow.findViewById(R.id.dialButton);
//		mCard2DialButton = mAdditionalButtonsRow
//				.findViewById(R.id.dial_2_Button);
		mPDialButton = mAdditionalButtonsRow
				.findViewById(R.id.privateDial_Button);
		//mPDialButton.setOnLongClickListener(this);
		if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
			mCard1DialButton.setOnClickListener(this);
			//mCard2DialButton.setOnClickListener(this);
			mPDialButton.setOnClickListener(this);
			int phone_1_Status =0; /*TelephonyManager.getDefault(0).getSimState();*/
			int phone_2_Status = 1;/*TelephonyManager.getDefault(1).getSimState();*/

			if (phone_1_Status == TelephonyManager.SIM_STATE_READY) {
				mCard1DialButton.setEnabled(true);
				mCard1DialButton
						.setBackgroundResource(R.drawable.new_private_dialbutton_pressed);

			}
//			if (phone_2_Status == TelephonyManager.SIM_STATE_READY) {
//				mCard2DialButton.setEnabled(true);
//				mCard2DialButton
//						.setBackgroundResource(R.drawable.new_private_dialbutton_pressed);
//			}
//			if (mCurrentMode == PrivateMode.MODE_MPT1327_ANALOG_NORMAL) {
//				mPDialButton
//						.setBackgroundResource(R.drawable.new_private_dial_privatecall_disable_bg);
//			} else {
//
//				mPDialButton.setEnabled(true);
//				mPDialButton
//						.setBackgroundResource(R.drawable.new_private_dialbutton_pressed);
//			}

		} else {
			mCard1DialButton.setVisibility(View.GONE); // It's VISIBLE by
														// default
			//mCard2DialButton.setVisibility(View.GONE);

			mCard1DialButton = null;
			//mCard2DialButton = null;
			mPDialButton = null;
		}

		mDelete = fragmentView.findViewById(R.id.show_delete_button);
		mDelete.setOnClickListener(this);
		mDelete.setOnLongClickListener(this);

		mDialpad = fragmentView.findViewById(R.id.dialpad_container); // This is
																		// null
																		// in
																		// landscape
																		// mode.

		// In landscape we put the keyboard in phone mode.
		if (null == mDialpad) {
			mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
		} else {
			mDigits.setCursorVisible(false);
		}

		// Set up the "dialpad chooser" UI; see showDialpadChooser().
		mDialpadChooser = (ListView) fragmentView
				.findViewById(R.id.dialpadChooser);
		mDialpadChooser.setOnItemClickListener(this);

		configureScreenFromIntent(getActivity().getIntent());

		return fragmentView;
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		queryCallLogHandler.removeMessages(QUERY_OUTGOING_CALL);
		if (mHandler != null) {
			mHandler.quit();
		}
	}

	private boolean isLayoutReady() {
		return mDigits != null;
	}

	public EditText getDigitsWidget() {
		return mDigits;
	}

	/**
	 * @return true when {@link #mDigits} is actually filled by the Intent.
	 */
	private boolean fillDigitsIfNecessary(Intent intent) {
		final String action = intent.getAction();
		if (Intent.ACTION_DIAL.equals(action)
				|| Intent.ACTION_VIEW.equals(action)) {
			Uri uri = intent.getData();
			if (uri != null) {
				if ("tel".equals(uri.getScheme())) {
					// Put the requested number into the input area
					String data = uri.getSchemeSpecificPart();
					// Remember it is filled via Intent.
					mDigitsFilledByIntent = true;
					setFormattedDigits(data, null);
					return true;
				} else {
					String type = intent.getType();
					if (People.CONTENT_ITEM_TYPE.equals(type)
							|| Phones.CONTENT_ITEM_TYPE.equals(type)) {
						// Query the phone number
						Cursor c = getActivity().getContentResolver().query(
								intent.getData(),
								new String[] { PhonesColumns.NUMBER,
										PhonesColumns.NUMBER_KEY }, null, null,
								null);
						if (c != null) {
							try {
								if (c.moveToFirst()) {
									// Remember it is filled via Intent.
									mDigitsFilledByIntent = true;
									// Put the number into the input area
									setFormattedDigits(c.getString(0),
											c.getString(1));
									return true;
								}
							} finally {
								c.close();
							}
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * @see #showDialpadChooser(boolean)
	 */
	private static boolean needToShowDialpadChooser(Intent intent,
			boolean isAddCallMode) {
		final String action = intent.getAction();

		boolean needToShowDialpadChooser = false;

		if (Intent.ACTION_DIAL.equals(action)
				|| Intent.ACTION_VIEW.equals(action)) {
			Uri uri = intent.getData();
			if (uri == null) {
				// ACTION_DIAL or ACTION_VIEW with no data.
				// This behaves basically like ACTION_MAIN: If there's
				// already an active call, bring up an intermediate UI to
				// make the user confirm what they really want to do.
				// Be sure *not* to show the dialpad chooser if this is an
				// explicit "Add call" action, though.
				if (!isAddCallMode && phoneIsInUse()) {
					needToShowDialpadChooser = true;
				}
			}
		} else if (Intent.ACTION_MAIN.equals(action)) {
			// The MAIN action means we're bringing up a blank dialer
			// (e.g. by selecting the Home shortcut, or tabbing over from
			// Contacts or Call log.)
			//
			// At this point, IF there's already an active call, there's a
			// good chance that the user got here accidentally (but really
			// wanted the in-call dialpad instead). So we bring up an
			// intermediate UI to make the user confirm what they really
			// want to do.
			if (phoneIsInUse()) {
				// Log.i(TAG,
				// "resolveIntent(): phone is in use; showing dialpad chooser!");
				needToShowDialpadChooser = true;
			}
		}

		return needToShowDialpadChooser;
	}

	private static boolean isAddCallMode(Intent intent) {
		final String action = intent.getAction();
		if (Intent.ACTION_DIAL.equals(action)
				|| Intent.ACTION_VIEW.equals(action)) {
			// see if we are "adding a call" from the InCallScreen; false by
			// default.
			return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
		} else {
			return false;
		}
	}

	/**
	 * Checks the given Intent and changes dialpad's UI state. For example, if
	 * the Intent requires the screen to enter "Add Call" mode, this method will
	 * show correct UI for the mode.
	 */
	public void configureScreenFromIntent(Intent intent) {
		if (!isLayoutReady()) {
			// This happens typically when parent's Activity#onNewIntent() is
			// called while
			// Fragment#onCreateView() isn't called yet, and thus we cannot
			// configure Views at
			// this point. onViewCreate() should call this method after
			// preparing layouts, so
			// just ignore this call now.
			Log.i(TAG,
					"Screen configuration is requested before onCreateView() is called. Ignored");
			return;
		}

		boolean needToShowDialpadChooser = false;

		final boolean isAddCallMode = isAddCallMode(intent);
		if (!isAddCallMode) {
			final boolean digitsFilled = fillDigitsIfNecessary(intent);
			if (!digitsFilled) {
				needToShowDialpadChooser = needToShowDialpadChooser(intent,
						isAddCallMode);
			}
		}
		showDialpadChooser(needToShowDialpadChooser);
	}

	/**
	 * Sets formatted digits to digits field.
	 */
	private void setFormattedDigits(String data, String normalizedNumber) {
		// strip the non-dialable numbers out of the data string.
		String dialString = PhoneNumberUtils.extractNetworkPortion(data);
		dialString = PhoneNumberUtils.formatNumber(dialString,
				normalizedNumber, mCurrentCountryIso);
		if (!TextUtils.isEmpty(dialString)) {
			Editable digits = mDigits.getText();
			digits.replace(0, digits.length(), dialString);
			// for some reason this isn't getting called in the digits.replace
			// call above..
			// but in any case, this will make sure the background drawable
			// looks right
			afterTextChanged(digits);
		}
	}

	private void setupKeypad(View fragmentView) {
		// Setup the listeners for the buttons
		View view = fragmentView.findViewById(R.id.one);
		view.setOnClickListener(this);
		view.setOnLongClickListener(this);

		fragmentView.findViewById(R.id.two).setOnClickListener(this);
		fragmentView.findViewById(R.id.three).setOnClickListener(this);
		fragmentView.findViewById(R.id.four).setOnClickListener(this);
		fragmentView.findViewById(R.id.five).setOnClickListener(this);
		fragmentView.findViewById(R.id.six).setOnClickListener(this);
		fragmentView.findViewById(R.id.seven).setOnClickListener(this);
		fragmentView.findViewById(R.id.eight).setOnClickListener(this);
		fragmentView.findViewById(R.id.nine).setOnClickListener(this);
		fragmentView.findViewById(R.id.star).setOnClickListener(this);

		view = fragmentView.findViewById(R.id.zero);
		view.setOnClickListener(this);
		view.setOnLongClickListener(this);

		fragmentView.findViewById(R.id.two).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.three).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.four).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.five).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.six).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.seven).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.eight).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.nine).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.star).setOnLongClickListener(this);
		fragmentView.findViewById(R.id.pound).setOnLongClickListener(this);

		fragmentView.findViewById(R.id.pound).setOnClickListener(this);

		mDialerDrawer = (SlidingDrawer) fragmentView
				.findViewById(R.id.dialer_container);
		mDialerDrawer.animateOpen();
		// mShowView = (ImageView)fragmentView.findViewById(R.id.showview);
		// mShowView.setVisibility(View.GONE);
		// mShowView.setOnClickListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();

		mCurrentMode = Settings.Secure.getInt(getActivity()
				.getContentResolver(), Settings.Secure.PRIVATE_PHONE_MODE,
				PrivateMode.MODE_UNKNOWN);
		Log.d(TAG, "onresume methos is excuted");
		// Query the last dailed number(voice & video).
		queryCallLogHandler.sendEmptyMessage(QUERY_OUTGOING_CALL);
		// Query the last dialed number. Do it first because hitting
		// the DB is 'slow'. This call is asynchronous.
		// retrieve the DTMF tone play back setting.
		mDTMFToneEnabled = Settings.System.getInt(getActivity()
				.getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING,
				1) == 1;

		// Retrieve the haptic feedback setting.
		mHaptic.checkSystemSetting();

		// if the mToneGenerator creation fails, just continue without it. It is
		// a local audio signal, and is not as important as the dtmf tone
		// itself.
		synchronized (mToneGeneratorLock) {
			if (mToneGenerator == null) {
				try {
					// we want the user to be able to control the volume of the
					// dial tones
					// outside of a call, so we use the stream type that is also
					// mapped to the
					// volume control keys for this activity
					mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE,
							TONE_RELATIVE_VOLUME);
					getActivity().setVolumeControlStream(DIAL_TONE_STREAM_TYPE);
				} catch (RuntimeException e) {
					Log.w(TAG,
							"Exception caught while creating local tone generator: "
									+ e);
					mToneGenerator = null;
				}
			}
		}

		Activity parent = getActivity();
		if (parent instanceof DialtactsActivity) {
			// See if we were invoked with a DIAL intent. If we were, fill in
			// the appropriate
			// digits in the dialer field.
			fillDigitsIfNecessary(parent.getIntent());
		}

		// While we're in the foreground, listen for phone state changes,
		// purely so that we can take down the "dialpad chooser" if the
		// phone becomes idle while the chooser UI is visible.
		TelephonyManager telephonyManager = (TelephonyManager) getActivity()
				.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(mPhoneStateListener,
				PhoneStateListener.LISTEN_CALL_STATE);

		// Potentially show hint text in the mDigits field when the user
		// hasn't typed any digits yet. (If there's already an active call,
		// this hint text will remind the user that he's about to add a new
		// call.)
		//
		// TODO: consider adding better UI for the case where *both* lines
		// are currently in use. (Right now we let the user try to add
		// another call, but that call is guaranteed to fail. Perhaps the
		// entire dialer UI should be disabled instead.)
		if (phoneIsInUse()) {
			mDigits.setHint(R.string.dialerDialpadHintText);
		} else {
			// Common case; no hint necessary.
			mDigits.setHint(null);

			// Also, a sanity-check: the "dialpad chooser" UI should NEVER
			// be visible if the phone is idle!
			showDialpadChooser(false);
		}

		updateDialAndDeleteButtonEnabledState();

		// if (mDialerDrawer.isOpened() == false) {
		// mDialerDrawer.animateOpen();
		// mShowView.setImageResource(R.drawable.new_dialpad_downup_side_pressed);
		// }
	}

	@Override
	public void onPause() {
		super.onPause();

		// Stop listening for phone state changes.
		TelephonyManager telephonyManager = (TelephonyManager) getActivity()
				.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(mPhoneStateListener,
				PhoneStateListener.LISTEN_NONE);

		synchronized (mToneGeneratorLock) {
			if (mToneGenerator != null) {
				mToneGenerator.release();
				mToneGenerator = null;
			}
		}
		// TODO: I wonder if we should not check if the AsyncTask that
		// lookup the last dialed number has completed.
		mLastNumberDialed = EMPTY_NUMBER; // Since we are going to query again,
											// free stale number.
		mLastVideoNumberDialed = EMPTY_NUMBER;
	}

	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, mDigitsFilledByIntent);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (mShowOptionsMenu
				&& ViewConfiguration.get(getActivity()).hasPermanentMenuKey()
				&& isLayoutReady() && mDialpadChooser != null) {
			inflater.inflate(R.menu.dialpad_options, menu);
		}
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		// Hardware menu key should be available and Views should already be
		// ready.
		if (mShowOptionsMenu
				&& ViewConfiguration.get(getActivity()).hasPermanentMenuKey()
				&& isLayoutReady() && mDialpadChooser != null) {
			setupMenuItems(menu);
		}
	}

	private void setupMenuItems(Menu menu) {
		final MenuItem callSettingsMenuItem = menu
				.findItem(R.id.menu_call_settings_dialpad);
		final MenuItem menu_private_settings_dialpad = menu
				.findItem(R.id.menu_private_settings_dialpad);
		final MenuItem addToContactMenuItem = menu
				.findItem(R.id.menu_add_contacts);
		final MenuItem twoSecPauseMenuItem = menu.findItem(R.id.menu_2s_pause);
		final MenuItem waitMenuItem = menu.findItem(R.id.menu_add_wait);

		// Check if all the menu items are inflated correctly. As a shortcut, we
		// assume all menu
		// items are ready if the first item is non-null.
		if (callSettingsMenuItem == null) {
			return;
		}
		if (menu_private_settings_dialpad == null) {
			return;
		}

		final Activity activity = getActivity();
		if (activity != null
				&& ViewConfiguration.get(activity).hasPermanentMenuKey()) {
			// Call settings should be available via its parent Activity.
			callSettingsMenuItem.setVisible(false);
			menu_private_settings_dialpad.setVisible(false);
		} else {
			callSettingsMenuItem.setVisible(true);
			menu_private_settings_dialpad.setVisible(true);
			// callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
			callSettingsMenuItem.setIntent(DialtactsActivity
					.getMobileSimChooseIntent());
			menu_private_settings_dialpad.setIntent(DialtactsActivity
					.getPrivatePhoneSettingsIntent());
		}

		// We show "add to contacts", "2sec pause", and "add wait" menus only
		// when the user is
		// seeing usual dialpads and has typed at least one digit.
		// We never show a menu if the "choose dialpad" UI is up.
		if (dialpadChooserVisible() || isDigitsEmpty()) {
			addToContactMenuItem.setVisible(false);
			twoSecPauseMenuItem.setVisible(false);
			waitMenuItem.setVisible(false);
		} else {
			final CharSequence digits = mDigits.getText();
			Log.d(TAG, "tianxuhongmDigits.getText()" + mDigits.getText());

			// Put the current digits string into an intent
			addToContactMenuItem.setIntent(getAddToContactIntent(digits));
			addToContactMenuItem.setVisible(true);

			// Check out whether to show Pause & Wait option menu items
			int selectionStart;
			int selectionEnd;
			String strDigits = digits.toString();
			if (mShowFlag == 1)
				mDigits.setSelection(mDigits.length());
			selectionStart = mDigits.getSelectionStart();
			selectionEnd = mDigits.getSelectionEnd();
			if (selectionStart != -1) {
				if (selectionStart > selectionEnd) {
					// swap it as we want start to be less then end
					int tmp = selectionStart;
					selectionStart = selectionEnd;
					selectionEnd = tmp;
				}
				if (selectionStart != 0) {
					// Pause can be visible if cursor is not in the begining
					twoSecPauseMenuItem.setVisible(true);

					// For Wait to be visible set of condition to meet
					waitMenuItem.setVisible(showWait(selectionStart,
							selectionEnd, strDigits));
				} else {
					// cursor in the beginning both pause and wait to be
					// invisible
					twoSecPauseMenuItem.setVisible(false);
					waitMenuItem.setVisible(false);
				}
			} else {
				twoSecPauseMenuItem.setVisible(true);

				// cursor is not selected so assume new digit is added to the
				// end
				int strLength = strDigits.length();
				waitMenuItem.setVisible(showWait(strLength, strLength,
						strDigits));
			}
		}
	}

	private static Intent getAddToContactIntent(CharSequence digits) {
		final Intent intent = new Intent(Intent.ACTION_INSERT,
				Contacts.CONTENT_URI);
		// final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
		intent.putExtra(Insert.PHONE, digits);
		// intent.setType(People.CONTENT_ITEM_TYPE);
		intent.putExtra("phone_only", true);
		return intent;
	}

	private void keyPressed(int keyCode) {
		mHaptic.vibrate();
		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
		mDigits.onKeyDown(keyCode, event);

		// If the cursor is at the end of the text we hide it.KEYCODE_BACK
		final int length = mDigits.length();
		if (length == mDigits.getSelectionStart()
				&& length == mDigits.getSelectionEnd()) {
			mDigits.setCursorVisible(false);
		}
	}

	public boolean onKey(View view, int keyCode, KeyEvent event) {
		Log.d(TAG, "onKey keyCode:" + keyCode + " KeyEvent:" + event);
		if (keyCode == KeyEvent.KEYCODE_CALL
				&& event.getAction() == KeyEvent.ACTION_DOWN
				&& event.getRepeatCount() == 0) {
			if (UtilsClick.isFastDoubleClick()) {
				return true;
			}
			dialButtonPressed(mCallDefault);
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BACK
				&& event.getAction() == KeyEvent.ACTION_DOWN) {
			mDigits.getText().clear();
		}
		if (keyCode == KeyEvent.KEYCODE_PTT
				&& event.getAction() == KeyEvent.ACTION_DOWN
				&& event.getRepeatCount() == 0) {

			if (UtilsClick.isFastDoubleClick()) {
				return true;
			}
			dialButtonPressed(mCallPrivatePhoneByPTT);
			return true;
		}
		switch (view.getId()) {
		case R.id.digits:
			if (keyCode == KeyEvent.KEYCODE_ENTER) {
				dialButtonPressed(mCallDefault);
				return true;
			}
			break;
		}
		return false;
	}

	public boolean dispatchKeyEvent(KeyEvent event) {

		Log.d(TAG, "dispatchKeyEvent" + event);

		return false;
	}

	@Override
	public void onClick(View view) {
		Log.d(TAG, "view+view" + view);
		switch (view.getId()) {
		case R.id.one: {
			playTone(ToneGenerator.TONE_DTMF_1);
			keyPressed(KeyEvent.KEYCODE_1);
			return;
		}
		case R.id.two: {
			playTone(ToneGenerator.TONE_DTMF_2);
			keyPressed(KeyEvent.KEYCODE_2);
			return;
		}
		case R.id.three: {
			playTone(ToneGenerator.TONE_DTMF_3);
			keyPressed(KeyEvent.KEYCODE_3);
			return;
		}
		case R.id.four: {
			playTone(ToneGenerator.TONE_DTMF_4);
			keyPressed(KeyEvent.KEYCODE_4);
			return;
		}
		case R.id.five: {
			playTone(ToneGenerator.TONE_DTMF_5);
			keyPressed(KeyEvent.KEYCODE_5);
			return;
		}
		case R.id.six: {
			playTone(ToneGenerator.TONE_DTMF_6);
			keyPressed(KeyEvent.KEYCODE_6);
			return;
		}
		case R.id.seven: {
			playTone(ToneGenerator.TONE_DTMF_7);
			keyPressed(KeyEvent.KEYCODE_7);
			return;
		}
		case R.id.eight: {
			playTone(ToneGenerator.TONE_DTMF_8);
			keyPressed(KeyEvent.KEYCODE_8);
			return;
		}
		case R.id.nine: {
			playTone(ToneGenerator.TONE_DTMF_9);
			keyPressed(KeyEvent.KEYCODE_9);
			return;
		}
		case R.id.zero: {
			playTone(ToneGenerator.TONE_DTMF_0);
			keyPressed(KeyEvent.KEYCODE_0);
			return;
		}
		case R.id.pound: {
			playTone(ToneGenerator.TONE_DTMF_P);
			keyPressed(KeyEvent.KEYCODE_POUND);

			return;
		}
		case R.id.star: {
			playTone(ToneGenerator.TONE_DTMF_S);
			keyPressed(KeyEvent.KEYCODE_STAR);
			return;
		}
		case R.id.show_delete_button: {
			keyPressed(KeyEvent.KEYCODE_DEL);
			return;
		}
		case R.id.dialButton: {
			mHaptic.vibrate(); // Vibrate here too, just like we do for the
								// regular keys

			dialButtonPressed(mCallCard1);
			return;
		}
//		case R.id.dial_2_Button: {
//			mHaptic.vibrate(); // Vibrate here too, just like we do for the
//								// regular keys
//
//			dialButtonPressed(mCallCard2);
//			return;
//		}
		case R.id.privateDial_Button: {
			mHaptic.vibrate(); // Vibrate here too, just like we do for the
								// regular keys

			dialButtonPressed(mCallPrivatePhone);
			return;
		}
		case R.id.videoButton: {
			mHaptic.vibrate();
			videoButtonPressed();
			// if (mListener != null) {
			// mListener.onSearchButtonPressed();
			// }
			return;
		}
		case R.id.digits: {
			if (!isDigitsEmpty()) {
				mDigits.setCursorVisible(true);
			}
			// if(!mDialerDrawer.isOpened()){
			// mDialerDrawer.animateOpen();
			// mShowView.setImageResource(R.drawable.new_dialpad_downup_side_pressed);
			// }
			return;
		}
		case R.id.overflow_menu: {
			Log.d(TAG, "view+view overflow_menu" + R.id.overflow_menu);
			PopupMenu popup = constructPopupMenu(view);
			if (popup != null) {
				popup.show();
			}
			return;
		}
		// case R.id.showview: {
		// Log.d(TAG, "view+view.showview"+R.id.showview);
		// if(mDialerDrawer.isOpened()){
		// mDialerDrawer.animateClose();
		// mShowView.setImageResource(R.drawable.new_dialpad_upsidedown_pressed);
		// }else {
		// mDialerDrawer.animateOpen();
		// mShowView.setImageResource(R.drawable.new_dialpad_downup_side_pressed);
		// }
		// return;
		// }
		}
	}

	private void dialVideo(String mNumber) {
		Uri uri;
		if (TextUtils.isEmpty(mNumber))
			return;
		uri = Uri.fromParts("tel", mNumber, null);
		Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
		intent.putExtra(EXTRA_IS_VIDEOCALL, true);
		startActivity(intent);
		return;
	}

	private PopupMenu constructPopupMenu(View anchorView) {
		final Context context = getActivity();
		if (context == null) {
			return null;
		}
		final PopupMenu popupMenu = new PopupMenu(context, anchorView);
		final Menu menu = popupMenu.getMenu();
		popupMenu.inflate(R.menu.dialpad_options);
		popupMenu.setOnMenuItemClickListener(this);
		setupMenuItems(menu);
		return popupMenu;
	}

	public boolean onLongClick(View view) {
		final Editable digits = mDigits.getText();

		try {
			Context c = getActivity().createPackageContext("com.neolink.phone",
					Context.CONTEXT_IGNORE_SECURITY);
			mPDTFsatDialSharedPreferences = c.getSharedPreferences(
					PDT_FAST_DIAL_NUM_PROVIDER,
					getActivity().MODE_WORLD_READABLE
							| getActivity().MODE_MULTI_PROCESS);
			mMPTFsatDialSharedPreferences = c.getSharedPreferences(
					MPT_FAST_DIAL_NUM_PROVIDER,
					getActivity().MODE_WORLD_READABLE
							| getActivity().MODE_MULTI_PROCESS);
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int id = view.getId();
		if (mCurrentMode == PrivateMode.MODE_PDT_DIGITAL_TRUNKING) {
			mFsatDialSharedPreferences = mPDTFsatDialSharedPreferences;
		}
		if (mCurrentMode == PrivateMode.MODE_MPT1327_ANALOG_TRUNKING) {
			mFsatDialSharedPreferences = mMPTFsatDialSharedPreferences;
		}
		switch (id) {
		case R.id.show_delete_button: {
			digits.clear();
			// TODO: The framework forgets to clear the pressed
			// status of disabled button. Until this is fixed,
			// clear manually the pressed status. b/2133127
			mDelete.setPressed(false);
			return true;
		}
		case R.id.one: {
			fastCall(1);
			return true;
		}
		case R.id.two: {
			fastCall(2);
			return true;
		}
		case R.id.three: {
			fastCall(3);
			return true;
		}
		case R.id.four: {
			fastCall(4);
			return true;
		}
		case R.id.five: {
			fastCall(5);
			return true;
		}
		case R.id.six: {
			fastCall(6);
			return true;
		}
		case R.id.seven: {
			fastCall(7);
			return true;
		}
		case R.id.eight: {
			fastCall(8);
			return true;
		}
		case R.id.nine: {
			fastCall(9);
			return true;
		}
		case R.id.zero: {
			keyPressed(KeyEvent.KEYCODE_PLUS);
			return true;
		}
		case R.id.star: {
			keyPWPressed(KeyEvent.KEYCODE_P);
			return true;
		}
		case R.id.pound: {
			keyPWPressed(KeyEvent.KEYCODE_W);
			return true;
		}
		case R.id.digits: {
			// Right now EditText does not show the "paste" option when cursor
			// is not visible.
			// To show that, make the cursor visible, and return false, letting
			// the EditText
			// show the option by itself.
			mDigits.setCursorVisible(true);
			return false;
		}
		case R.id.privateDial_Button: {

			if (mCurrentMode == PrivateMode.MODE_MPT1327_ANALOG_NORMAL
					|| mCurrentMode == PrivateMode.MODE_PDT_DIGITAL_NORMAL) {

				return false;
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(R.string.dial_priority_title);
			builder.setSingleChoiceItems(R.array.call_priority_entries, 0,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							if (which == 0) {
								dialButtonPressed(mCallPrivatePhone);
							} else {
								privateSpecialCall(which);
							}
							dialog.dismiss();
						}
					});
			builder.create().show();

			return true;
		}
		}
		return false;
	}

	public void keyPWPressed(int keyCode) {
		final CharSequence digits = mDigits.getText();
		boolean keyPPressed = false;
		boolean keyWPressed = false;
		int selectionStart;
		int selectionEnd;
		String strDigits = digits.toString();
		if (mShowFlag == 1)
			mDigits.setSelection(mDigits.length());
		selectionStart = mDigits.getSelectionStart();
		selectionEnd = mDigits.getSelectionEnd();
		if (selectionStart != -1) {
			if (selectionStart > selectionEnd) {
				// swap it as we want start to be less then end
				int tmp = selectionStart;
				selectionStart = selectionEnd;
				selectionEnd = tmp;
			}
			if (selectionStart != 0) {
				// Pause can be visible if cursor is not in the begining
				keyPPressed = true;

				// For Wait to be visible set of condition to meet
				keyWPressed = showWait(selectionStart, selectionEnd, strDigits);
			} else {
				/**
				 * modify by xuhong.tian, when Log.d(TAG,
				 * "selectionStart=="+selectionStart
				 * +"selectionEnd="+selectionEnd); selectionStart=0 and
				 * selectionEnd=0 ,keypress =false; 2014,6,30
				 */
				// cursor in the beginning both pause and wait to be invisible
				// keyPPressed = false;
				// keyWPressed = false;
				keyPPressed = true;
				keyWPressed = true;
			}
		} else {
			keyPPressed = true;

			// cursor is not selected so assume new digit is added to the end
			int strLength = strDigits.length();
			keyWPressed = showWait(strLength, strLength, strDigits);
		}

		if ((keyCode == KeyEvent.KEYCODE_P) && (keyPPressed)) {
			updateDialString(",");
		}
		if ((keyCode == KeyEvent.KEYCODE_W) && (keyWPressed)) {
			updateDialString(";");
		}
	}

	public void callVoicemail() {
		startActivity(newVoicemailIntent());
		mDigits.getText().clear(); // TODO: Fix bug 1745781
		getActivity().finish();
	}

	public static class ErrorDialogFragment extends DialogFragment {
		private int mTitleResId;
		private Integer mMessageResId; // can be null

		private static final String ARG_TITLE_RES_ID = "argTitleResId";
		private static final String ARG_MESSAGE_RES_ID = "argMessageResId";

		public static ErrorDialogFragment newInstance(int titleResId) {
			return newInstanceInter(titleResId, null);
		}

		public static ErrorDialogFragment newInstance(int titleResId,
				int messageResId) {
			return newInstanceInter(titleResId, messageResId);
		}

		private static ErrorDialogFragment newInstanceInter(int titleResId,
				Integer messageResId) {
			final ErrorDialogFragment fragment = new ErrorDialogFragment();
			final Bundle args = new Bundle();
			args.putInt(ARG_TITLE_RES_ID, titleResId);
			if (messageResId != null) {
				args.putInt(ARG_MESSAGE_RES_ID, messageResId);
			}
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);

			if (getArguments().containsKey(ARG_MESSAGE_RES_ID)) {
				mMessageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
			}
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(mTitleResId).setPositiveButton(
					android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dismiss();
						}
					});
			if (mMessageResId != null) {
				builder.setMessage(mMessageResId);
			}
			return builder.create();
		}
	}

	/**
	 * In most cases, when the dial button is pressed, there is a number in
	 * digits area. Pack it in the intent, start the outgoing call broadcast as
	 * a separate task and finish this activity.
	 * 
	 * When there is no digit and the phone is CDMA and off hook, we're sending
	 * a blank flash for CDMA. CDMA networks use Flash messages when special
	 * processing needs to be done, mainly for 3-way or call waiting scenarios.
	 * Presumably, here we're in a special 3-way scenario where the network
	 * needs a blank flash before being able to add the new participant. (This
	 * is not the case with all 3-way calls, just certain CDMA infrastructures.)
	 * 
	 * Otherwise, there is no digit, display the last dialed number. Don't
	 * finish since the user may want to edit it. The user needs to press the
	 * dial button again, to dial it (general case described above).
	 */
	public void dialButtonPressed(int card) {
		if (UtilsClick.isFastDoubleClick()) {
			return;
		}
		if (mCurrentMode == PrivateMode.MODE_MPT1327_ANALOG_NORMAL
				&& card == mCallPrivatePhone) {
			Toast.makeText(getActivity(),
					getString(R.string.current_mode_unavailable),
					Toast.LENGTH_SHORT).show();
			return;
		}

		Log.d(TAG, "dialButtonPressed.........inter...........");
		if (isDigitsEmpty()) { // No number entered.
			Log.d(TAG,
					"dialButtonPressed.........inter........isDigitsEmpty...");

			if (card == mCallPrivatePhoneByPTT || card == mCallPrivatePhone) {

				if (mCurrentMode != PrivateMode.MODE_MPT1327_ANALOG_NORMAL) {

					if (card == mCallPrivatePhone || card == mCallPrivatePhoneByPTT) {
						Toast.makeText(getActivity(),
								getString(R.string.private_call_number_empty),
								Toast.LENGTH_SHORT).show();
					} else if (sInstance.getCurrentCallState() == CALL_STATE_IDLE) {
						Toast.makeText(getActivity(),
								getString(R.string.private_call_number_empty),
								Toast.LENGTH_SHORT).show();
					}
				}
				Log.d(TAG, "has returned");
				return;
			}
			if (phoneIsCdma() && phoneIsOffhook()) {
				// This is really CDMA specific. On GSM is it possible
				// to be off hook and wanted to add a 3rd party using
				// the redial feature.
				startActivity(newFlashIntent());
				Log.d(TAG, "dialButtonPressed.........startActivity...........");
			} else {
				if (!TextUtils.isEmpty(mLastNumberDialed)) {
					// Recall the last number dialed.
					mDigits.setText(mLastNumberDialed);

					// ...and move the cursor to the end of the digits string,
					// so you'll be able to delete digits using the Delete
					// button (just as if you had typed the number manually.)
					//
					// Note we use mDigits.getText().length() here, not
					// mLastNumberDialed.length(), since the EditText widget now
					// contains a *formatted* version of mLastNumberDialed (due
					// to
					// mTextWatcher) and its length may have changed.
					mDigits.setSelection(mDigits.getText().length());
				} else {
					// There's no "last number dialed" or the
					// background query is still running. There's
					// nothing useful for the Dial button to do in
					// this case. Note: with a soft dial button, this
					// can never happens since the dial button is
					// disabled under these conditons.

					Toast.makeText(getActivity(),
							getString(R.string.private_call_number_empty),
							Toast.LENGTH_SHORT).show();

					playTone(ToneGenerator.TONE_PROP_NACK);

					return;
				}
			}

		} else {

			final String number = mDigits.getText().toString();
			Log.d(TAG, "dialButtonPressed" + isDigitsEmpty() + number);

			// "persist.radio.otaspdial" is a temporary hack needed for one
			// carrier's automated
			// test equipment.
			// TODO: clean it up.
			if (number != null
					&& !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
					&& number.matches(mProhibitedPhoneNumberRegexp)
					&& (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
				Log.i(TAG,
						"The phone number is prohibited explicitly by a rule.");
				if (getActivity() != null) {
					DialogFragment dialogFragment = ErrorDialogFragment
							.newInstance(R.string.dialog_phone_call_prohibited_title);
					dialogFragment.show(getFragmentManager(),
							"phone_prohibited_dialog");
				}

				// Clear the digits just in case.
				mDigits.getText().clear();
			} else {
				final Intent intent = newDialNumberIntent(number);
				if (getActivity() instanceof DialtactsActivity) {
					intent.putExtra(DialtactsActivity.EXTRA_CALL_ORIGIN,
							DialtactsActivity.CALL_ORIGIN_DIALTACTS);
					intent.putExtra("use_card", card);
				}

				if (card == mCallPrivatePhone || card == mCallPrivatePhoneByPTT) {
					Intent privateintent = new Intent(
							PrivateIntents.ACTION_PRIVATE_PHONE);
					if (TextUtils.isEmpty(mDigits.getText())) {
						if (card == mCallPrivatePhoneByPTT
								&& mCurrentMode != PrivateMode.MODE_MPT1327_ANALOG_NORMAL) {

							// if(sInstance.getCurrentCallState() ==
							// CALL_STATE_IDLE){
							Toast.makeText(
									getActivity(),
									getString(R.string.private_call_number_empty),
									Toast.LENGTH_SHORT).show();
							// }

						}
						return;
					}
					if (card == mCallPrivatePhoneByPTT) {
						privateintent
								.putExtra("PrivateCallByContactsPTT", true);
					}
					String forMatNumber = mDigits.getText().toString()
							.replace(" ", "");
/*					if (!isNumeric(forMatNumber)) {

						mDigits.getText().clear();
						Toast.makeText(getActivity(),
								getString(R.string.input_number_error),
								Toast.LENGTH_SHORT).show();
						return;
					}
					if (forMatNumber.length() > 8 || forMatNumber.length() == 8) {
						forMatNumber = forMatNumber.substring(0, 8);
					}*/
					Log.d(TAG, "forMateNumber=="+forMatNumber);
					// if (sInstance.getCurrentCallState() == CALL_STATE_IDLE) {
					privateintent.putExtra(PrivateIntents.EXTRA_CONTACT_NAME,
							"test");
					privateintent.putExtra(PrivateIntents.EXTRA_CONTACT_NUMBER,forMatNumber
							);
					Log.d(TAG, "dialButtonPressed..mDigits.getText()."
							+ mDigits.getText());

					startActivity(privateintent);
					// }
				} else {

					if (phoneAndSimIsInUse() == 0 && card == 1) {

						Toast.makeText(getActivity(),
								getString(R.string.the_other_call_processing),
								Toast.LENGTH_SHORT).show();
						return;
					} else if (phoneAndSimIsInUse() == 1 && card == 0) {

						Toast.makeText(getActivity(),
								getString(R.string.the_other_call_processing),
								Toast.LENGTH_SHORT).show();
						return;
					} else {
						startActivity(intent);
						getActivity().finish();
					}
				}
				mDigits.getText().clear(); // TODO: Fix bug 1745781
				Log.d(TAG, "dialButtonPressed..............." + isDigitsEmpty()
						+ number);
			}
			Log.d(TAG, "dialButtonPressed" + isDigitsEmpty());
			// while(true){

			Log.d(TAG, " TelephonyManager.getPhoneCount();()==="
					+ TelephonyManager.getPhoneCount() + "isMultiSim"
					+ TelephonyManager.getDefault(0));
			// ;//}
			
			Log.d(TAG, "xxxxxgetDefaultPhoneIdxxxx"+TelephonyManager.getDefault(0).getCallState());
			Log.d(TAG, "xxxTelephonyManager.getDefault().getCallState()xxx"+TelephonyManager.getDefault().getCallState());
		}
	}

	private int privateSpecialCall(int CallType) {

		Log.d(TAG, "privateSpecialCall.type==" + CallType);
		Intent privateintent = new Intent(PrivateIntents.ACTION_PRIVATE_PHONE);
		// privateintent.putExtra(PrivateIntents.EXTRA_CONTACT_NAME, "test");
		privateintent.putExtra(PrivateIntents.EXTRA_CONTACT_NUMBER, mDigits
				.getText().toString().replace(" ", ""));
		if (CallType == 1 || CallType == 2 || CallType == 3) { // call priority
			privateintent.putExtra("privateSpecialCall", CallType);
		} else if (CallType == mCallEmergency) {
			privateintent.putExtra("privateSpecialCall", CallType);
		} else if (CallType == mCallBroadCast) {
			privateintent.putExtra("privateSpecialCall", CallType);
		}
		startActivity(privateintent);
		mDigits.getText().clear(); 
		return 0;

	}

	public void videoButtonPressed() {
		if (isDigitsEmpty()) {
			if (!TextUtils.isEmpty(mLastVideoNumberDialed)) {
				// Recall the last number dialed.
				mDigits.setText(mLastVideoNumberDialed);
				mDigits.setSelection(mDigits.getText().length());
			} else {
				playTone(ToneGenerator.TONE_PROP_NACK);
			}
		} else {
			dialVideo(mDigits.getText().toString());
		}
	}

	/**
	 * Plays the specified tone for TONE_LENGTH_MS milliseconds.
	 * 
	 * The tone is played locally, using the audio stream for phone calls. Tones
	 * are played only if the "Audible touch tones" user preference is checked,
	 * and are NOT played if the device is in silent mode.
	 * 
	 * @param tone
	 *            a tone code from {@link ToneGenerator}
	 */
	void playTone(int tone) {
		// if local tone playback is disabled, just return.
		if (!mDTMFToneEnabled) {
			return;
		}

		// Also do nothing if the phone is in silent mode.
		// We need to re-check the ringer mode for *every* playTone()
		// call, rather than keeping a local flag that's updated in
		// onResume(), since it's possible to toggle silent mode without
		// leaving the current activity (via the ENDCALL-longpress menu.)
		AudioManager audioManager = (AudioManager) getActivity()
				.getSystemService(Context.AUDIO_SERVICE);
		int ringerMode = audioManager.getRingerMode();
		if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
				|| (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
			return;
		}

		synchronized (mToneGeneratorLock) {
			if (mToneGenerator == null) {
				Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
				return;
			}

			// Start the new tone (will stop any playing tone)
			mToneGenerator.startTone(tone, TONE_LENGTH_MS);
		}
	}

	/**
	 * Brings up the "dialpad chooser" UI in place of the usual Dialer elements
	 * (the textfield/button and the dialpad underneath).
	 * 
	 * We show this UI if the user brings up the Dialer while a call is already
	 * in progress, since there's a good chance we got here accidentally (and
	 * the user really wanted the in-call dialpad instead). So in this situation
	 * we display an intermediate UI that lets the user explicitly choose
	 * between the in-call dialpad ("Use touch tone
	 * keypad") and the regular Dialer ("Add call").  (Or, the option "Return to
	 * call in progress" just goes back to the in-call UI with no dialpad at
	 * all.)
	 * 
	 * @param enabled
	 *            If true, show the "dialpad chooser" instead of the regular
	 *            Dialer UI
	 */
	private void showDialpadChooser(boolean enabled) {
		// Check if onCreateView() is already called by checking one of View
		// objects.
/*		if (!isLayoutReady()) {
			return;
		}

		if (enabled) {
			// Log.i(TAG, "Showing dialpad chooser!");
			if (mDigitsContainer != null) {
				mDigitsContainer.setVisibility(View.GONE);
			} else {
				// mDigits is not enclosed by the container. Make the digits
				// field itself gone.
				mDigits.setVisibility(View.GONE);
			}
			if (mDialpad != null)
				mDialpad.setVisibility(View.GONE);
			mAdditionalButtonsRow.setVisibility(View.GONE);
			if (mMainDial != null)
				mMainDial.setVisibility(View.GONE);
			mDialpadChooser.setVisibility(View.VISIBLE);

			// Instantiate the DialpadChooserAdapter and hook it up to the
			// ListView. We do this only once.
			if (mDialpadChooserAdapter == null) {
				mDialpadChooserAdapter = new DialpadChooserAdapter(
						getActivity());
			}
			mDialpadChooser.setAdapter(mDialpadChooserAdapter);
		} else {
			// Log.i(TAG, "Displaying normal Dialer UI.");
			if (mDigitsContainer != null) {
				mDigitsContainer.setVisibility(View.VISIBLE);
			} else {
				mDigits.setVisibility(View.VISIBLE);
			}
			if (mDialpad != null)
				mDialpad.setVisibility(View.VISIBLE);
			mAdditionalButtonsRow.setVisibility(View.VISIBLE);
			if (mMainDial != null)
				mMainDial.setVisibility(View.VISIBLE);
			mDialpadChooser.setVisibility(View.GONE);
		}*/
	}

	/**
	 * @return true if we're currently showing the "dialpad chooser" UI.
	 */
	private boolean dialpadChooserVisible() {
		return mDialpadChooser.getVisibility() == View.VISIBLE;
	}

	/**
	 * Simple list adapter, binding to an icon + text label for each item in the
	 * "dialpad chooser" list.
	 */
	private static class DialpadChooserAdapter extends BaseAdapter {
		private LayoutInflater mInflater;

		// Simple struct for a single "choice" item.
		static class ChoiceItem {
			String text;
			Bitmap icon;
			int id;

			public ChoiceItem(String s, Bitmap b, int i) {
				text = s;
				icon = b;
				id = i;
			}
		}

		// IDs for the possible "choices":
		static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
		static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
		static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

		private static final int NUM_ITEMS = 3;
		private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

		public DialpadChooserAdapter(Context context) {
			// Cache the LayoutInflate to avoid asking for a new one each time.
			mInflater = LayoutInflater.from(context);

			// Initialize the possible choices.
			// TODO: could this be specified entirely in XML?

			// - "Use touch tone keypad"
			mChoiceItems[0] = new ChoiceItem(
					context.getString(R.string.dialer_useDtmfDialpad),
					BitmapFactory.decodeResource(context.getResources(),
							R.drawable.ic_dialer_fork_tt_keypad),
					DIALPAD_CHOICE_USE_DTMF_DIALPAD);

			// - "Return to call in progress"
			mChoiceItems[1] = new ChoiceItem(
					context.getString(R.string.dialer_returnToInCallScreen),
					BitmapFactory.decodeResource(context.getResources(),
							R.drawable.ic_dialer_fork_current_call),
					DIALPAD_CHOICE_RETURN_TO_CALL);

			// - "Add call"
			mChoiceItems[2] = new ChoiceItem(
					context.getString(R.string.dialer_addAnotherCall),
					BitmapFactory.decodeResource(context.getResources(),
							R.drawable.ic_dialer_fork_add_call),
					DIALPAD_CHOICE_ADD_NEW_CALL);
		}

		public int getCount() {
			return NUM_ITEMS;
		}

		/**
		 * Return the ChoiceItem for a given position.
		 */
		public Object getItem(int position) {
			return mChoiceItems[position];
		}

		/**
		 * Return a unique ID for each possible choice.
		 */
		public long getItemId(int position) {
			return position;
		}

		/**
		 * Make a view for each row.
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			// When convertView is non-null, we can reuse it (there's no need
			// to reinflate it.)
			if (convertView == null) {
				convertView = mInflater.inflate(
						R.layout.dialpad_chooser_list_item, null);
			}

			TextView text = (TextView) convertView.findViewById(R.id.text);
			text.setText(mChoiceItems[position].text);

			ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
			icon.setImageBitmap(mChoiceItems[position].icon);

			return convertView;
		}
	}

	/**
	 * Handle clicks from the dialpad chooser.
	 */
	public void onItemClick(AdapterView parent, View v, int position, long id) {
		if (v instanceof ContactListItemView) {
			ContactListItemView showView = (ContactListItemView) v;
			Uri uri = (Uri) showView.getTag();
			String authority = "";
			if (uri != null) {
				authority = uri.getAuthority();
				if ("com.android.contacts".equals(authority)) {
					final Intent viewContactIntent = new Intent(
							Intent.ACTION_VIEW, uri);
					startActivity(viewContactIntent);
				} else if ("call_log".equals(authority)) {
					final Intent viewCallDetailIntent = new Intent(
							"android.intent.action.VIEW");
					viewCallDetailIntent.setDataAndType(uri,
							"vnd.android.cursor.item/calls");
					startActivity(viewCallDetailIntent);
				}
			} else {
				Log.w(TAG, "Item at " + position
						+ " was long clicked before adapter is ready. Ignoring");
			}
			Log.d(TAG, "onItemClick method authority is : " + authority);
			return;
		}

		DialpadChooserAdapter.ChoiceItem item = (DialpadChooserAdapter.ChoiceItem) parent
				.getItemAtPosition(position);
		if (null == item)
			return;
		int itemId = item.id;
		switch (itemId) {
		case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
			// Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
			// Fire off an intent to go back to the in-call UI
			// with the dialpad visible.
			returnToInCallScreen(true);
			break;

		case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
			// Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
			// Fire off an intent to go back to the in-call UI
			// (with the dialpad hidden).
			returnToInCallScreen(false);
			break;

		case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
			// Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
			// Ok, guess the user really did want to be here (in the
			// regular Dialer) after all. Bring back the normal Dialer UI.
			showDialpadChooser(false);
			break;

		default:
			Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
			break;
		}
	}

	/**
	 * Returns to the in-call UI (where there's presumably a call in progress)
	 * in response to the user selecting "use touch tone keypad" or
	 * "return to call" from the dialpad chooser.
	 */
	private void returnToInCallScreen(boolean showDialpad) {
		try {
			ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
					.checkService("phone"));
			if (phone != null)
				phone.showCallScreenWithDialpad(showDialpad);
		} catch (RemoteException e) {
			Log.w(TAG, "phone.showCallScreenWithDialpad() failed", e);
		}

		// Finally, finish() ourselves so that we don't stay on the
		// activity stack.
		// Note that we do this whether or not the showCallScreenWithDialpad()
		// call above had any effect or not! (That call is a no-op if the
		// phone is idle, which can happen if the current call ends while
		// the dialpad chooser is up. In this case we can't show the
		// InCallScreen, and there's no point staying here in the Dialer,
		// so we just take the user back where he came from...)
		getActivity().finish();
	}

	/**
	 * @return true if the phone is "in use", meaning that at least one line is
	 *         active (ie. off hook or ringing or dialing).we can get the state
	 *         about sim0 and sim1
	 */
	public static int phoneAndSimIsInUse() {
		//if (PhoneFactory.isMultiSim()) {

			boolean phoneInUse0 = false;
			try {
				ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
						.checkService("phone0"));
				if (phone != null)
					phoneInUse0 = !phone.isIdle();
			} catch (RemoteException e) {
				Log.w(TAG, "phone0.isIdle() failed", e);
			}
			boolean phoneInUse1 = false;
			try {
				ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
						.checkService("phone1"));
				if (phone != null)
					phoneInUse1 = !phone.isIdle();
			} catch (RemoteException e) {
				Log.w(TAG, "phone1.isIdle() failed", e);
			}

			if (phoneInUse0) {
				return 0;
			} else if (phoneInUse1) {
				return 1;
			}
		//	return -1;
		//} else {
			//Log.d(TAG, "it is not suppport multisim");
			return -1;
		//}
	}

	/**
	 * @return true if the phone is "in use", meaning that at least one line is
	 *         active (ie. off hook or ringing or dialing).
	 */
	public static boolean phoneIsInUse() {
		boolean phoneInUse = false;
		try {
			ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
					.checkService("phone"));
			if (phone != null)
				phoneInUse = !phone.isIdle();
		} catch (RemoteException e) {
			Log.w(TAG, "phone.isIdle() failed", e);
		}
		return phoneInUse;
	}

	/**
	 * @return true if the phone is a CDMA phone type
	 */
	private boolean phoneIsCdma() {
		boolean isCdma = false;
		try {
			ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
					.checkService("phone"));
			if (phone != null) {
				isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
			}
		} catch (RemoteException e) {
			Log.w(TAG, "phone.getActivePhoneType() failed", e);
		}
		return isCdma;
	}

	/**
	 * @return true if the phone state is OFFHOOK
	 */
	private boolean phoneIsOffhook() {
		boolean phoneOffhook = false;
		try {
			ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
					.checkService("phone"));
			if (phone != null)
				phoneOffhook = phone.isOffhook();
		} catch (RemoteException e) {
			Log.w(TAG, "phone.isOffhook() failed", e);
		}
		return phoneOffhook;
	}

	/**
	 * Returns true whenever any one of the options from the menu is selected.
	 * Code changes to support dialpad options
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_2s_pause:
			updateDialString(",");
			return true;
		case R.id.menu_add_wait:
			updateDialString(";");
			return true;
		case R.id.menu_private_settings_dialpad:
			Intent privateCallSetting = DialtactsActivity
					.getPrivatePhoneSettingsIntent();
			startActivity(privateCallSetting);
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		return onOptionsItemSelected(item);
	}

	/**
	 * Updates the dial string (mDigits) after inserting a Pause character (,)
	 * or Wait character (;).
	 */
	private void updateDialString(String newDigits) {
		int selectionStart;
		int selectionEnd;

		// SpannableStringBuilder editable_text = new
		// SpannableStringBuilder(mDigits.getText());
		int anchor = mDigits.getSelectionStart();
		int point = mDigits.getSelectionEnd();

		selectionStart = Math.min(anchor, point);
		selectionEnd = Math.max(anchor, point);

		Editable digits = mDigits.getText();
		if (selectionStart != -1) {
			if (selectionStart == selectionEnd) {
				// then there is no selection. So insert the pause at this
				// position and update the mDigits.
				digits.replace(selectionStart, selectionStart, newDigits);
			} else {
				digits.replace(selectionStart, selectionEnd, newDigits);
				// Unselect: back to a regular cursor, just pass the character
				// inserted.
				mDigits.setSelection(selectionStart + 1);
			}
		} else {
			int len = mDigits.length();
			digits.replace(len, len, newDigits);
		}
	}

	/**
	 * Update the enabledness of the "Dial" and "Backspace" buttons if
	 * applicable.
	 */
	private void updateDialAndDeleteButtonEnabledState() {
		final boolean digitsNotEmpty = !isDigitsEmpty();

		if (mCard1DialButton != null) {
			// On CDMA phones, if we're already on a call, we *always*
			// enable the Dial button (since you can press it without
			// entering any digits to send an empty flash.)
			if (phoneIsCdma() && phoneIsOffhook()) {
				mCard1DialButton.setEnabled(true);
			} else {
				// Common case: GSM, or CDMA but not on a call.
				// Enable the Dial button if some digits have
				// been entered, or if there is a last dialed number
				// that could be redialed.
				/*
				 * mCard1DialButton.setEnabled(digitsNotEmpty ||
				 * !TextUtils.isEmpty(mLastNumberDialed));
				 */
				if (mVideoButton != null)
					mVideoButton.setEnabled(digitsNotEmpty
							|| !TextUtils.isEmpty(mLastVideoNumberDialed));
			}
		}
//		if (mCard2DialButton != null) {
//			// On CDMA phones, if we're already on a call, we *always*
//			// enable the Dial button (since you can press it without
//			// entering any digits to send an empty flash.)
//			if (phoneIsCdma() && phoneIsOffhook()) {
//				mCard2DialButton.setEnabled(true);
//			} else {
//				// Common case: GSM, or CDMA but not on a call.
//				// Enable the Dial button if some digits have
//				// been entered, or if there is a last dialed number
//				// that could be redialed.
//				/*
//				 * mCard2DialButton.setEnabled(digitsNotEmpty ||
//				 * !TextUtils.isEmpty(mLastNumberDialed));
//				 */
//				if (mVideoButton != null)
//					mVideoButton.setEnabled(digitsNotEmpty
//							|| !TextUtils.isEmpty(mLastVideoNumberDialed));
//			}
//		}
		mDelete.setEnabled(digitsNotEmpty);
	}

	/**
	 * Check if voicemail is enabled/accessible.
	 * 
	 * @return true if voicemail is enabled and accessibly. Note that this can
	 *         be false "temporarily" after the app boot.
	 * @see TelephonyManager#getVoiceMailNumber()
	 */
	private boolean isVoicemailAvailable() {
		try {
			for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
				if (TelephonyManager.getDefault(i).getVoiceMailNumber() != null) {
					return true;
				}
				;
			}
		} catch (SecurityException se) {
			// Possibly no READ_PHONE_STATE privilege.
			Log.w(TAG,
					"SecurityException is thrown. Maybe privilege isn't sufficient.");
		}
		return false;
	}

	/**
	 * This function return true if Wait menu item can be shown otherwise
	 * returns false. Assumes the passed string is non-empty and the 0th index
	 * check is not required.
	 */
	private static boolean showWait(int start, int end, String digits) {
		if (start == end) {
			// visible false in this case
			if (start > digits.length())
				return false;

			// preceding char is ';', so visible should be false
			if (digits.charAt(start - 1) == ';')
				return false;

			// next char is ';', so visible should be false
			if ((digits.length() > start) && (digits.charAt(start) == ';'))
				return false;
		} else {
			// visible false in this case
			if (start > digits.length() || end > digits.length())
				return false;

			// In this case we need to just check for ';' preceding to start
			// or next to end
			if (digits.charAt(start - 1) == ';')
				return false;
		}
		return true;
	}

	/**
	 * @return true if the widget with the phone number digits is empty.
	 */
	private boolean isDigitsEmpty() {
		return mDigits.length() == 0;
	}

	private void queryLastOutgoingCall() {
		Context context = getActivity();

		if (null != context) {
			mLastNumberDialed = Calls.getLastOutgoingCall(context, 0);
			mLastVideoNumberDialed = Calls.getLastOutgoingCall(context, 1);
		}
	}

	// Helpers for the call intents.
	private Intent newVoicemailIntent() {
		final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
				Uri.fromParts("voicemail", EMPTY_NUMBER, null));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return intent;
	}

	private Intent newFlashIntent() {
		final Intent intent = newDialNumberIntent(EMPTY_NUMBER);
		intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
		return intent;
	}

	private Intent newDialNumberIntent(String number) {
		final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
				Uri.fromParts("tel", number, null));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("Call_card", 1);
		return intent;
	}

	public void setListener(Listener listener) {
		mListener = listener;
	}

	@Override
	public void onVisibilityChanged(boolean visible) {
		mShowOptionsMenu = visible;
	}

	private void fastCall(int key) {
		mDigits.getText().clear();
		try {
			String number = mFsatDialSharedPreferences.getString(FAST_DIAL_NUM
					+ String.valueOf(key), "");
			Log.d(TAG, "number==" + number);
			mDigits.setText(number);
			if (number.equals("")) {
				Toast toast = Toast.makeText(getActivity(),
						getText(R.string.no_fast_dial), Toast.LENGTH_SHORT);
				toast.setGravity(Gravity.CENTER, 0, -150);
				toast.show();
				return;
			} else {
				mHaptic.vibrate(); // Vibrate here too, just like we do for the
									// regular keys
				dialButtonPressed(mCallPrivatePhone);
			}
		} catch (Exception e) {
		}
	}

	android.content.DialogInterface.OnClickListener myListenser = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case 0:
				Intent intent = newDialNumberIntent(mFastPhoneNumber1);
				mHaptic.vibrate();
				intent.putExtra(MsmsConstants.NOT_NEED_SIMCARD_SELECTION, true);
				intent.putExtra(MsmsConstants.SUBSCRIPTION_KEY,
						MsmsConstants.SUB1);
				startActivity(intent);
				mDigits.getText().clear();
				getActivity().finish();
				break;
			case 1:
				mHaptic.vibrate();
				Intent intent1 = newDialNumberIntent(mFastPhoneNumber2);
				intent1.putExtra(MsmsConstants.NOT_NEED_SIMCARD_SELECTION, true);
				intent1.putExtra(MsmsConstants.SUBSCRIPTION_KEY,
						MsmsConstants.SUB2);
				startActivity(intent1);
				mDigits.getText().clear();
				getActivity().finish();
				break;
			default:
				mHaptic.vibrate();
				break;
			}
		}
	};

	public boolean onTouch(View v, MotionEvent event) {
		final int action = event.getAction();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			yStart = event.getY();
			xStart = event.getX();
			isCan = false;
			break;
		case MotionEvent.ACTION_UP:
			yEnd = event.getY();
			xEnd = event.getX();
			isCan = true;
			break;
		}

		// double angle = Math.atan((yEnd - yStart)/Math.abs(xEnd - xStart));
		// if (angle > 0.5f && mDialerDrawer.isOpened() && isCan && (yEnd -
		// yStart) > 100.0f){
		// mDialerDrawer.animateClose();
		// mShowView.setImageResource(R.drawable.new_dialpad_upsidedown_pressed);
		// }
		return false;
	}

	// public void onStart() {
	// super.onStart();
	// oldStr = mDigits.getText().toString().replaceAll(" ","");
	// startQuery(oldStr, 0);
	// }

	public void onStop() {
		super.onStop();
	}

	// @Override
	// protected void startQuery(String filter, long delay) {
	// if(filter == null){
	// filter = mDigits.getText().toString().replaceAll(" ","");
	// }
	// super.startQuery(filter, 0);
	// }

	public static class UtilsClick {
		private static long lastClickTime;

		public static boolean isFastDoubleClick() {
			long time = System.currentTimeMillis();
			long timeD = time - lastClickTime;
			if (0 < timeD && timeD < 1000) {
				return true;
			}
			lastClickTime = time;
			return false;
		}
	}
	
	public  boolean isNumeric(String str){
	    Pattern pattern = Pattern.compile("[0-9]*");
	    return pattern.matcher(str).matches();   
	 } 
}
