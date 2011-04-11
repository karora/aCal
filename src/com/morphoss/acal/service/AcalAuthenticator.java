package com.morphoss.acal.service;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.morphoss.acal.activity.serverconfig.AddServerList;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.providers.DavResources;
import com.morphoss.acal.providers.Servers;

/**
 * Authenticator service that returns a subclass of AbstractAccountAuthenticator in onBind()
 */
public class AcalAuthenticator extends Service {
	private static final String				TAG						= "AcalAuthenticator";
	private static StaticAuthenticatorImplementation	realAuthenticator	= null;

	public static final String SERVER_ID = "server_id";
	public static final String COLLECTION_ID = "collection_id";
	public static final String USERNAME = "username";
	
	
	public AcalAuthenticator() {
		super();
		Log.d(TAG,"AcalAuthenticator was created");
	}

	public IBinder onBind(Intent intent) {
		IBinder ret = null;
		if (intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT))
			ret = getAuthenticator().getIBinder();
		Log.d(TAG,"onBind was called");
		return ret;
	}

	private StaticAuthenticatorImplementation getAuthenticator() {
		if (realAuthenticator == null) realAuthenticator = new StaticAuthenticatorImplementation(this);
		Log.d(TAG,"StaticAuthenticatorImplementation was created");
		return realAuthenticator;
	}

	private static class StaticAuthenticatorImplementation extends AbstractAccountAuthenticator {
		private Context	context;

		public StaticAuthenticatorImplementation(Context context) {
			super(context);
			this.context = context;
			Log.d(TAG,"addAccount was called");
		}

		/*
		 * The user has requested to add a new account to the system. We return an intent that will launch our
		 * login screen if the user has not logged in yet, otherwise our activity will just pass the user's
		 * credentials on to the account manager.
		 */
		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
					String[] requiredFeatures, Bundle options) throws NetworkErrorException {
			Bundle reply = new Bundle();

			Log.d(TAG,"addAccount was called");
			Intent i = new Intent(context, AddServerList.class);
			i.setAction("com.morphoss.acal.activity.serverconfig.AddServerList.ACTION_CREATE");
			i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
			reply.putParcelable(AccountManager.KEY_INTENT, i);

			return reply;
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
			Log.d(TAG,"confirmCredentials was called");
			return null;
		}

		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
			Log.d(TAG,"editProperties was called");
			return null;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType,
					Bundle options) throws NetworkErrorException {
			Log.d(TAG,"getAuthToken was called");
			return null;
		}

		@Override
		public String getAuthTokenLabel(String authTokenType) {
			Log.d(TAG,"getAuthTokenLabel was called");
			return null;
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
					throws NetworkErrorException {
			Log.d(TAG,"hasFeatures was called");
			return null;
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType,
					Bundle options) {
			Log.d(TAG,"updateCredentials was called");
			return null;
		}
	}
}
