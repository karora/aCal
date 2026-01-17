package com.morphoss.acal.database.resourcesmanager.requesttypes;

import android.util.Log;

import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.service.CallbackExecutor;


public abstract class ReadOnlyResourceRequestWithResponse<E> implements	ReadOnlyResourceRequest {

	//The CallBack
	private ResourceResponseListener<E> callBack = null;
	private int priority = 5;
	private boolean processed = false;

	public static final int MAX_PRIORITY = 10;
	public static final int MIN_PRIORITY = 1;
	/**
	 * Mandatory constructor - stores the callBack to notify when posting response. CallBack can be null if requester doesn't care about
	 * response;
	 * @param callBack
	 */
	protected ReadOnlyResourceRequestWithResponse(ResourceResponseListener<E> callBack ){
		this.callBack = callBack;
	}

	public ReadOnlyResourceRequestWithResponse(ResourceResponseListener<E> callBack, int priority)  {
		this(callBack);
		if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) throw new IllegalArgumentException("Priority out of bounds"+priority);
		this.priority = priority;
	}

	@Override
	public boolean isProcessed() { return this.processed; }
	@Override
	public synchronized void setProcessed() { this.processed = true; }


	/**
	 * Called by child classes to send response to the callback. Sends response on its own Thread so will usually return immediately.
	 * Beware of Race conditions when sending multiple requests - callbacks may come back in an arbitrary order.
	 * @param response
	 */
	protected void postResponse(final ResourceResponse<E> response) {
		if (callBack == null) return;
		CallbackExecutor.execute(() -> {
			ReadOnlyResourceRequestWithResponse.this.setProcessed();
			try {
			    callBack.resourceResponse(response);
			}
			catch( Exception e) {
			    Log.w("aCal ReadOnlyResourceRequestWithResponse",
			            "Request class is: "+this.getClass().getSimpleName()
			            +", Response class is: "+response.getClass().getName()
			            +" Exception:\n"+Log.getStackTraceString(e));
			}
		});
	}

	@Override
	public int compareTo(ReadOnlyResourceRequest r) {
		return this.priority - r.priority();
	}

	@Override
	public int priority() {
		return this.priority;
	}
}
