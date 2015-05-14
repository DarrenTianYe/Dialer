/*
 * SPRD: create
 */
package com.sprd.dialer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

import com.android.dialer.DialerApplication;
import com.android.dialer.R;
import neolink.telephony.PrivateContactContract;
import neolink.telephony.PrivateIntents;
import neolink.telephony.PrivateMode;

/**
 * Dialog that show which sim call log to view
 */
public class CallLogSetting extends DialogFragment {
    private static final String TAG = "ViewSetting";
    private static final int sOFFSET = 1;

    public static final String SHOW_TYPE = "which_sim_to_view";
    public static final int TYPE_ALL = -1;
    public static final int TYPE_PRIVATE = -2;

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager) {
        CallLogSetting dialog = new CallLogSetting();
        dialog.show(fragmentManager, TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity().getApplicationContext();

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                int type = which - sOFFSET;
                setCallLogShowType(context,type);
                dialog.dismiss();
            }
        };

        CharSequence[] items = getItems(context);
        int checkedItem = getCallLogShowType(context) + sOFFSET;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setSingleChoiceItems(items, checkedItem, listener);
        return builder.setCancelable(true).create();
    }
    
    private CharSequence[] getItems(Context context){
        int size = TelephonyManager.getPhoneCount();
        CharSequence[] items = new CharSequence[size + sOFFSET];
        items[0] = context.getString(R.string.item_all_calls);
		if (DialerApplication.getApplication().mMode == PrivateMode.MODE_MPT1327_ANALOG_NORMAL) {
		} else if (DialerApplication.getApplication().mMode == PrivateMode.MODE_MPT1327_ANALOG_TRUNKING) {
			
			 items[1] = context.getString(R.string.item_mpt_calls);
			
		} else if (DialerApplication.getApplication().mMode == PrivateMode.MODE_PDT_DIGITAL_TRUNKING) {
			
			 items[1] = context.getString(R.string.item_pdt_calls);

		} else if (DialerApplication.getApplication().mMode == PrivateMode.MODE_PDT_DIGITAL_NORMAL) {
		}
        for (int i = 1; i < size; i++) {
            int index = i + sOFFSET;
            items[index] = context.getString(R.string.item_sim_calls, index);
        }
        return items;
    }

    public static int getCallLogShowType(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int type = prefs.getInt(SHOW_TYPE, TYPE_ALL);
        return type;
    }

    public static void setCallLogShowType(Context context, int which) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt(SHOW_TYPE, which);
        ed.apply();
    }
}
