// Copyright 2013 Google Inc. All Rights Reserved.

package com.android.dialer;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.extensions.ExtensionsFactory;
import neolink.telephony.PrivateManager;
import neolink.telephony.PrivateMode;

public class DialerApplication extends Application {

    /* SPRD: @{ */
    private ContactPhotoManager mContactPhotoManager;
    /* @} */
    
    /**
     * add by xuhong.tian
     */
    private static DialerApplication sDialApplicaion;
	public int mMode=PrivateMode.MODE_UNKNOWN;
	private  PrivateManager mManager;

    @Override
    public void onCreate() {
        super.onCreate();
        ExtensionsFactory.init(getApplicationContext());
        sDialApplicaion = this;
		  mManager=new PrivateManager(this);
		  mMode=mManager.getMode();
        IntentFilter filter = new IntentFilter();  
        filter.addAction("com.neolink.modechange");
        registerReceiver(modeChangedRecever, filter); 
    }

    /* SPRD: @{ */
    @Override
    public Object getSystemService(String name) {
        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                registerComponentCallbacks(mContactPhotoManager);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }
        return super.getSystemService(name);
    }

    private static int sSupportVoiceSearch = -1;
    public static boolean isSupportVoiceSearch(Context context) {
        if (sSupportVoiceSearch == -1) {
            TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            boolean support = tm.isSupportApplication(TelephonyManager.TYPE_VOICE_SEARCH);
            sSupportVoiceSearch = support ? 1 : 0;
        }
        return sSupportVoiceSearch == 1;
    }
    /* @} */

    /**
     * add by xuhong.tian get contacts context methods
     * @return
     */
	public static DialerApplication getApplication() {
		return sDialApplicaion;
	}
	
	   /**
     * add by xuhong.tian cancel the reveiver.
     * @return
     */
    @Override
	public void onTerminate() {
		// TODO Auto-generated method stub
		super.onTerminate();	
		 unregisterReceiver(modeChangedRecever); 
	}

	/**
     * add by xuhong.tian get contacts BroadcastReceiver
     * @return
     */
	private BroadcastReceiver modeChangedRecever = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			mMode=mManager.getMode();
		}

	}; 
}
