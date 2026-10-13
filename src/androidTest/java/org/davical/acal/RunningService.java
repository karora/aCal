package org.davical.acal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.davical.acal.service.aCalService;

/**
 * aCal's service, bound as the app's screens bind it.  It is what makes the
 * managers and runs the jobs that write changes away, so anything that saves
 * needs it running.
 */
public final class RunningService implements ServiceConnection {

	private final Context context;
	private final CountDownLatch connected = new CountDownLatch(1);

	private RunningService( Context context ) {
		this.context = context;
	}

	public static RunningService start( Context context ) throws InterruptedException {
		RunningService service = new RunningService(context);
		if ( !context.bindService(new Intent(context, aCalService.class), service, Context.BIND_AUTO_CREATE) )
			throw new IllegalStateException("Could not bind to the service");
		if ( !service.connected.await(30, TimeUnit.SECONDS) )
			throw new IllegalStateException("The service did not start");
		return service;
	}

	public void stop() {
		context.unbindService(this);
	}

	@Override
	public void onServiceConnected( ComponentName name, IBinder binder ) {
		connected.countDown();
	}

	@Override
	public void onServiceDisconnected( ComponentName name ) {
	}
}
