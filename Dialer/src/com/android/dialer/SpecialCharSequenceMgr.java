/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.dialer;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.sprd.contacts.common.dialog.MobileSimChooserDialog;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyIntents;
import com.sprd.dialer.SprdUtils;
import com.sprd.phone.common.utils.OperatorUtils;

/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * Note the Phone app also handles these sequences too (in a couple of
 * relatively obscure places in the UI), so there's a separate version of
 * this class under apps/Phone.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";

    private static final String MMI_IMEI_DISPLAY = "*#06#";
    private static final String MMI_REGULATORY_INFO_DISPLAY = "*#07#";

    /* SPRD: add for DM & CMCC test setting @{ */
    private static final String DM_SETTING = "#*4560#";
    private static final String CMCCTEST_AGPS_CONFIG = "*#612345#";
    private static final String CMCCTEST_AGPS_LOG_SHOW = "*#812345#";
    /* @} */

    /**
     * Remembers the previous {@link QueryHandler} and cancel the operation when needed, to
     * prevent possible crash.
     *
     * QueryHandler may call {@link ProgressDialog#dismiss()} when the screen is already gone,
     * which will cause the app crash. This variable enables the class to prevent the crash
     * on {@link #cleanup()}.
     *
     * TODO: Remove this and replace it (and {@link #cleanup()}) with better implementation.
     * One complication is that we have SpecialCharSequenceMgr in Phone package too, which has
     * *slightly* different implementation. Note that Phone package doesn't have this problem,
     * so the class on Phone side doesn't have this functionality.
     * Fundamental fix would be to have one shared implementation and resolve this corner case more
     * gracefully.
     */
    private static QueryHandler sPreviousAdnQueryHandler;

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    public static boolean handleChars(Context context, String input, EditText textField) {
        return handleChars(context, input, false, textField);
    }

    static boolean handleChars(Context context, String input) {
        return handleChars(context, input, false, null);
    }

    static boolean handleChars(Context context, String input, boolean useSystemWindow,
            EditText textField) {

        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);

        if (handleIMEIDisplay(context, dialString, useSystemWindow)
                || handleRegulatoryInfoDisplay(context, dialString)
                || handlePinEntry(context, dialString)
                || handleAdnEntry(context, dialString, textField)
                /* SPRD: add for DM & CMCC test setting @{ */
                || handleDmCode(context, dialString)
                || handleAgpsCfg(context, dialString)
                || handleAgpsLogShow(context, dialString)
                /* @} */
                || handleSecretCode(context, dialString)) {
            return true;
        }

        return false;
    }

    /**
     * Cleanup everything around this class. Must be run inside the main thread.
     *
     * This should be called when the screen becomes background.
     */
    public static void cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "cleanup() is called outside the main thread");
            return;
        }

        if (sPreviousAdnQueryHandler != null) {
            sPreviousAdnQueryHandler.cancel();
            sPreviousAdnQueryHandler = null;
        }
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
       if(len < 8 && len > 4 && input.startsWith("*#") && input.endsWith("#*")) {
            Intent intent = new Intent(TelephonyIntents.SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(2, len - 2)));
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(intent);
            return true;
        }else if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            Intent intent = new Intent(TelephonyIntents.SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            // SPRD: For bug 302798, set the broadcast foreground.
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */

        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null
                || !TelephonyCapabilities.supportsAdn(telephonyManager.getCurrentPhoneType())) {
            return false;
        }

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                int index = Integer.parseInt(input.substring(0, len-1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                QueryHandler handler = new QueryHandler (context.getContentResolver());

                // create the cookie object
                SimContactQueryCookie sc = new SimContactQueryCookie(index - 1, handler,
                        ADN_QUERY_TOKEN);

                // setup the cookie fields
                sc.contactNum = index - 1;
                sc.setTextField(textField);

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
                sc.progressDialog.setTitle(R.string.simContacts_title);
                sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                // display the progress dialog
                sc.progressDialog.show();

                // run the query.
                handler.startQuery(ADN_QUERY_TOKEN, sc, sc.getSimUri(),
                        new String[]{ADN_PHONE_NUMBER_COLUMN_NAME}, null, null, null);

                if (sPreviousAdnQueryHandler != null) {
                    // It is harmless to call cancel() even after the handler's gone.
                    sPreviousAdnQueryHandler.cancel();
                }
                sPreviousAdnQueryHandler = handler;
                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    static boolean handlePinEntry(Context context, String input) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            try {
                /*
                * SPRD:BUG260504 add Multi-sim Pin Entry
                * @orig
                *     return ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                *         .handlePinMmi(input);
                * @{
                */
                if (TelephonyManager.isMultiSim()) {
                    return handleMultiSimPinEntry(context, input);
                } else {
                    return ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                    .handlePinMmi(input);
                }
                /*
                * @}
                */
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                return false;
            }
        }
        return false;
    }

    static boolean handleIMEIDisplay(Context context, String input, boolean useSystemWindow) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null && input.equals(MMI_IMEI_DISPLAY)) {
            int phoneType = telephonyManager.getCurrentPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                showIMEIPanel(context, useSystemWindow, telephonyManager);
                return true;
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                showMEIDPanel(context, useSystemWindow, telephonyManager);
                return true;
            }
        }

        return false;
    }

    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals(MMI_REGULATORY_INFO_DISPLAY)) {
            Log.d(TAG, "handleRegulatoryInfoDisplay() sending intent to settings app");
            ComponentName regInfoDisplayActivity = new ComponentName(
                    "com.android.settings", "com.android.settings.RegulatoryInfoDisplayActivity");
            Intent showRegInfoIntent = new Intent("android.settings.SHOW_REGULATORY_INFO");
            showRegInfoIntent.setComponent(regInfoDisplayActivity);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }
            return true;
        }
        return false;
    }

    /**
     * SPRD: Modify this method for display IMEI for multi-sim mode. @{
     */
    // TODO: Combine showIMEIPanel() and showMEIDPanel() into a single
    // generic "showDeviceIdPanel()" method, like in the apps/Phone
    // version of SpecialCharSequenceMgr.java.  (This will require moving
    // the phone app's TelephonyCapabilities.getDeviceIdLabel() method
    // into the telephony framework, though.)

    private static void showIMEIPanel(Context context, boolean useSystemWindow,
            TelephonyManager telephonyManager) {
        StringBuffer imeiBuffer = new StringBuffer();
        if (OperatorUtils.IS_CMCC) {
            imeiBuffer.append(TelephonyManager.getDefault().getDeviceId());
        } else {
            int phoneCnt = TelephonyManager.getPhoneCount();
            if (phoneCnt == 1) {
                imeiBuffer.append(TelephonyManager.getDefault().getDeviceId());
            } else {
                for (int i = 0; i < phoneCnt; i++) {
                    if (i != 0) {
                        imeiBuffer.append("\n");
                    }
                    imeiBuffer.append("IMEI");
                    imeiBuffer.append((i + 1));
                    imeiBuffer.append("\n");
                    imeiBuffer.append(((TelephonyManager)context.getSystemService(TelephonyManager
                            .getServiceName(Context.TELEPHONY_SERVICE, i))).getDeviceId());
                }
            }
        }
        String imeiStr = imeiBuffer.toString();

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.imei)
                .setMessage(imeiStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
    }
    /** @} */

    private static void showMEIDPanel(Context context, boolean useSystemWindow,
            TelephonyManager telephonyManager) {
        String meidStr = telephonyManager.getDeviceId();

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.meid)
                .setMessage(meidStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
    }

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactNum;
        // SPRD: which sim card we will to query 0 or 1
        public int simIndex;
        // SPRD: how many contacts in last sim card
        public int simCount;
        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
        }
        // SPRD: Add query contacts for multi sim
        public Uri getSimUri() {
            String uri = "content://icc/" + getPathName("adn", simIndex);
            return Uri.parse(uri);
        }
        // SPRD: Add query contacts for multi sim
        private static String getPathName(String path, int phoneId) {
            if (TelephonyManager.isMultiSim()) {
                if (phoneId == TelephonyManager.getPhoneCount()) {
                    return path;
                }
                return phoneId + "/" + path;
            } else {
                return path;
            }
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link #handleAdnEntry}.
     */
    private static class QueryHandler extends NoNullCursorAsyncQueryHandler {

        private boolean mCanceled;

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onNotNullableQueryComplete(int token, Object cookie, Cursor c) {
            sPreviousAdnQueryHandler = null;
            if (mCanceled) {
                return;
            }

            SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

            // close the progress dialog.
            sc.progressDialog.dismiss();

            // get the EditText to update or see if the request was cancelled.
            EditText text = sc.getTextField();

            // if the textview is valid, and the cursor is valid and postionable
            // on the Nth number, then we update the text field and display a
            // toast indicating the caller name.
            Context context = sc.progressDialog.getContext();
            if ((c != null) && (text != null)){
                sc.contactNum = sc.contactNum - sc.simCount;
                if (c.moveToPosition(sc.contactNum)) {
                    String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                    String number = c.getString(c
                            .getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));
                    if (number == null) {
                        Toast.makeText(context, context.getString(R.string.no_available_number),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // fill the text in.
                    text.getText().replace(0, 0, number);

                    // display the name as a toast
                    name = context.getString(R.string.menu_callNumber, name);
                    Toast.makeText(context, name, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                sc.simCount = c.getCount();
            }
            if (sc.simIndex < TelephonyManager.getPhoneCount() - 1) {
                sc.simIndex++;
                startQuery(ADN_QUERY_TOKEN, sc, sc.getSimUri(), new String[] {
                    ADN_PHONE_NUMBER_COLUMN_NAME
                }, null, null, null);
            } else if(sc.simIndex == TelephonyManager.getPhoneCount() - 1){
                Toast.makeText(context, context.getString(R.string.no_available_number), Toast.LENGTH_SHORT).show();
            }
        }

        public void cancel() {
            mCanceled = true;
            // Ask AsyncQueryHandler to cancel the whole request. This will fails when the
            // query already started.
            cancelOperation(ADN_QUERY_TOKEN);
        }
    }

    /**
     * SPRD:
     * add for DM & CMCC test setting
     * @{
     */
    static boolean handleDmCode(Context context, String input) {
        if(input.equals(DM_SETTING))
        {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName("com.spreadtrum.dm", "com.spreadtrum.dm.DmDebugMenu");
            if(context.getPackageManager().resolveActivity(intent, 0) == null) {
                Log.w("dm code","com.spreadtrum.dm is not exist");
                return false;
            }
            else    {
                context.startActivity(intent);
                return true;
            }
        }

        return false;
    }

    static private boolean handleAgpsCfg(Context context,String input) {
        if (input.equals(CMCCTEST_AGPS_CONFIG)) {
            try{
                Intent i = new Intent();
                i.setAction("android.intent.action.MAIN");
                i.setClassName("com.android.settings", "com.sprd.settings.LocationGpsConfig");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }catch(Exception ex){
                Log.e(TAG, ex.toString());
            }
            return true;
        }
        return false;
    }

    static private boolean handleAgpsLogShow(Context context,String input) {
        if (input.equals(CMCCTEST_AGPS_LOG_SHOW)) {
            try{
                Intent i = new Intent();
                i.setAction("android.intent.action.MAIN");
                i.setClassName("com.android.settings", "com.sprd.settings.LocationAgpsLogShow");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }catch(Exception ex){
                Log.e(TAG, ex.toString());
            }
            return true;
        }
        return false;
    }

    private static boolean handleMultiSimPinEntry(Context context, String input) {
        int phoneCount = TelephonyManager.getPhoneCount();
        String[] sims = new String[phoneCount];
        final int[] simId = new int[phoneCount];
        int activeCount = 0;
        for (int i = 0; i < phoneCount; i++) {
            if (TelephonyManager.getDefault(i).hasIccCard()) {
                sims[activeCount] = "SIM" + (i + 1);
                simId[activeCount] = i;
                activeCount++;
            }
        }
        if (activeCount == 1) {
            int id = simId[0];
            try {
                return ITelephony.Stub.asInterface(
                        ServiceManager.getService(TelephonyManager.getServiceName("phone", id)))
                        .handlePinMmi(input);
            } catch (Exception e) {
                // TODO: handle exception
                Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                return false;
            }

        } else if (activeCount > 1) {
            final String inputString = input;
            if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                MobileSimChooserDialog mobileSimChooserDialog = new MobileSimChooserDialog(context);
                mobileSimChooserDialog
                        .setListener(new MobileSimChooserDialog.OnSimPickedListener() {
                            public void onSimPicked(int phoneId) {
                                if (phoneId == -1) {
                                    return;
                                }
                                try {
                                    ITelephony.Stub.asInterface(
                                            ServiceManager.getService(TelephonyManager
                                                    .getServiceName("phone", phoneId)))
                                            .handlePinMmi(inputString);
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                                }

                            }
                        });
                mobileSimChooserDialog.setCancelable(false);
                mobileSimChooserDialog.show();
            } else {
                android.content.DialogInterface.OnClickListener onClickListenser = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int id = simId[which];
                        try {
                            ITelephony.Stub.asInterface(
                                    ServiceManager.getService(TelephonyManager.getServiceName(
                                            "phone", id)))
                                    .handlePinMmi(inputString);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                        }

                    }
                };
                new AlertDialog.Builder(context).setTitle(R.string.select_card)
                        .setItems(sims, onClickListenser).show();
            }
            return true;
        }
        return false;
    }
    /** @} */
}
