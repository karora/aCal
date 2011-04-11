/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.activity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.dataservice.CalendarDataService;
import com.morphoss.acal.dataservice.DataRequest;
import com.morphoss.acal.dataservice.DataRequestCallBack;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.davacal.AcalEventAction;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.widget.AcalViewFlipper;

/**
 * <h1>Month View</h1>
 * 
 * <h3>This is the activity that is started when aCal is run and will likely be
 * the most used interface in the program.</h3>
 * 
 * <p>
 * This view is split into 3 sections:
 * </p>
 * <ul>
 * <li>Month View - A grid view controlled by a View Flipper displaying all the
 * days of a calendar month.</li>
 * <li>Event View - A grid view controlled by a View Flipper displaying all the
 * events for the current selected day</li>
 * <li>Buttons - A set of buttons at the bottom of the screen</li>
 * </ul>
 * <p>
 * As well as this, there is a menu accessible through the menu button.
 * </p>
 * 
 * <p>
 * Each of the view flippers listens to gestures, Side swipes on either will
 * result in the content of the flipper moving forward or back. Content for the
 * flippers is provided by Adapter classes that contain the data the view is
 * representing.
 * </p>
 * 
 * <p>
 * At any time there are 2 important pieces of information that make up this
 * views state: The currently selected day, which is highlighted when visible in
 * the month view and determines which events are visible in the event list. The
 * other is the currently displayed date, which determines which month we are
 * looking at in the month view. This state information is written to and read
 * from file when the view loses and gains focus.
 * </p>
 * 
 * 
 * @author Morphoss Ltd
 * @license GPL v3 or later
 * 
 */
public class MonthView extends Activity implements OnGestureListener,
		OnTouchListener, OnClickListener {

	public static final String TAG = "aCal MonthView";

	/** The file that we save state information to */
	public static final String STATE_FILE = "/data/data/com.morphoss.acal/monthview.dat";

	/* Fields relating to the Month View: */

	/** The flipper for the month view */
	private AcalViewFlipper gridViewFlipper;
	/** The root view containing the GridView Object for the Month View */
	private View gridRoot;
	/** The GridView object that displays the month */
	private GridView gridView;
	/** The TextView that displays which month we are looking at */
	private TextView monthTitle;

	/* Fields relating to the Event View */

	/** The flipper for the Event View */
	private AcalViewFlipper listViewFlipper;
	/** The root view containing the GridView Object for the Event View */
	private View listRoot;
	/** The GridView object that displays the Event View */
	private GridView eventList;
	/** The TextView that displays which day we are looking at */
	private TextView eventListTitle;
	/** The current event list adapter */
	private EventListAdapter eventListAdapter;

	/* Fields relating to state */

	/** The month that our Month View should display */
	private AcalDateTime displayedMonth;
	/** The day that our Event View should display */
	private AcalDateTime selectedDate;

	/* Fields relating to buttons */
	public static final int TODAY = 0;
	public static final int YEAR = 2;
	public static final int ADD = 3;

	/* Fields Relating to Gesture Detection */
	private GestureDetector gestureDetector;
	private double consumedX;
	private double consumedY;
	private static final int maxAngleDev = 30;
	private static final int minDistance = 60;

	/* Fields relating to calendar data */
	private DataRequest dataRequest = null;
	private boolean isBound = false;
	private EventCache eventCache;

	/* Fields relating to Intent Results */
	public static final int PICK_MONTH_FROM_YEAR_VIEW = 0;
	public static final int PICK_TODAY_FROM_EVENT_VIEW = 1;

	// Animations
	Animation leftIn = null;
	Animation leftOut = null;
	Animation rightIn = null;
	Animation rightOut = null;

	/********************************************************
	 * Activity Overrides *
	 ********************************************************/

	/**
	 * <p>
	 * Called when Activity is first created. Initialises all appropriate fields
	 * and Constructs the Views for display.
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.month_view);

		// make sure aCalService is running
		this.startService(new Intent(this, aCalService.class));

		gestureDetector = new GestureDetector(this);

		// Create Grid Views
		createGridView(true);
		createListView(true);

		// Set up buttons
		this.setupButton(R.id.month_today_button, TODAY, getString(R.string.Today));
		this.setupButton(R.id.month_year_button, YEAR, getString(R.string.Year));
		this.setupButton(R.id.month_add_button, ADD, "+");

		AcalDateTime currentDate = new AcalDateTime();
		selectedDate = currentDate;
		displayedMonth = currentDate;
		changeSelectedDate(currentDate);
		changeDisplayedMonth(currentDate);

		listViewFlipper.setAnimationCacheEnabled(true);
		leftIn = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
		leftOut = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
		rightIn = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
		rightOut = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
	}

	private void connectToService() {
		try {
			if (this.isBound)
				return;
			Intent intent = new Intent(this, CalendarDataService.class);
			Bundle b = new Bundle();
			b.putInt(CalendarDataService.BIND_KEY,
					CalendarDataService.BIND_DATA_REQUEST);
			intent.putExtras(b);
			this.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		} catch (Exception e) {
			Log.e(TAG, "Error connecting to service: " + e.getMessage());
		}
	}

	/**
	 * <p>
	 * Called when Activity regains focus. Try's to load the saved State.
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	public void onResume() {
		super.onResume();
		if (this.eventCache != null) this.eventCache.flushCache();
		loadState();
		connectToService();
	}

	/*
	 * private void loadRecentDates() { final Object data =
	 * getLastNonConfigurationInstance();
	 * 
	 * // The activity is starting for the first time, load the state from our
	 * file if (data == null) { //Set the current state to today by default
	 * AcalDateTime currentDate = AcalDateTime.getInstance(); selectedDate =
	 * currentDate; displayedMonth = currentDate;
	 * changeSelectedDate(currentDate); changeDisplayedMonth(currentDate); }
	 * else { // The activity was destroyed/created automatically, use the
	 * date/month // from earlier. AcalDateTime[] dates = new AcalDateTime[2];
	 * System.arraycopy((AcalDateTime[]) data, 0, dates, 0, 2);
	 * changeSelectedDate(dates[0]); changeDisplayedMonth(dates[1]); } }
	 * 
	 * @Override public Object onRetainNonConfigurationInstance() { final
	 * AcalDateTime[] saveDates = new AcalDateTime[2]; saveDates[0] =
	 * this.selectedDate; saveDates[1] = this.displayedMonth; return saveDates;
	 * }
	 */

	/**
	 * <p>
	 * Called when activity loses focus or is closed. Try's to save the current
	 * State
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		super.onPause();
		// Save state
		if (Constants.LOG_DEBUG)	Log.d(TAG, "Writing month view state to file.");
		ObjectOutputStream outputStream = null;
		try {
			outputStream = new ObjectOutputStream(new FileOutputStream(
					STATE_FILE));
			outputStream.writeObject(this.selectedDate);
			outputStream.writeObject(this.displayedMonth);
		} catch (FileNotFoundException ex) {
			Log.w(TAG,
					"Error saving MonthView State - File Not Found: "
							+ ex.getMessage());
		} catch (IOException ex) {
			Log.w(TAG,
					"Error saving MonthView State - IO Error: "
							+ ex.getMessage());
		} finally {
			// Close the ObjectOutputStream
			try {
				if (outputStream != null) {
					outputStream.flush();
					outputStream.close();
				}
			} catch (IOException ex) {
				Log.w(TAG,
						"Error closing MonthView file - IO Error: "
								+ ex.getMessage());
			}
		}
		if (isBound) {
			try {
				if (dataRequest != null)
					dataRequest.unregisterCallback(mCallback);
				this.unbindService(mConnection);
				this.isBound = false;
				this.eventCache = null;
				dataRequest = null;
			} catch (RemoteException re) {

			}
		}
	}

	/****************************************************
	 * Private Methods *
	 ****************************************************/

	/**
	 * <p>
	 * Helper method for setting up buttons
	 * </p>
	 * @param buttonLabel 
	 */
	private void setupButton(int id, int val, String buttonLabel) {
		Button myButton = (Button) this.findViewById(id);
		if (myButton == null) {
			Log.e(TAG, "Cannot find button '" + id + "' by ID, to set value '"
					+ val + "'");
			Log.i(TAG, Log.getStackTraceString(new Exception()));
		} else {
			myButton.setText(buttonLabel);
			myButton.setOnClickListener(this);
			myButton.setTag(val);
		}
	}

	/**
	 * <p>
	 * Creates a new GridView object based on this Activities current state. The
	 * GridView created Will display this Activities MonthView
	 * </p>
	 * 
	 * @param addParent
	 *            <p>
	 *            Whether or not to set the ViewFlipper as the new GridView's
	 *            Parent. if set to false the caller is contracted to add a
	 *            parent to the GridView.
	 *            </p>
	 */
	private void createGridView(boolean addParent) {

		try {
			LayoutInflater inflater = (LayoutInflater) this
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			// Get grid flipper and add month grid
			gridViewFlipper = (AcalViewFlipper) findViewById(R.id.month_grid_flipper);
			gridViewFlipper.setAnimationCacheEnabled(true);

			// Add parent if directed to do so
			if (addParent)
				gridRoot = inflater.inflate(R.layout.month_grid_view,
						gridViewFlipper);
			else
				gridRoot = inflater.inflate(R.layout.month_grid_view, null);

			// Title
			monthTitle = (TextView) gridRoot
					.findViewById(R.id.month_grid_title);
			// Grid
			gridView = (GridView) gridRoot
					.findViewById(R.id.month_default_gridview);
			gridView.setSelector(R.drawable.no_border);
			gridView.setOnTouchListener(this);
		} catch (Exception e) {
			Log.e(TAG, "Error occured creating gridview: " + e.getMessage());
		}
	}

	/**
	 * <p>
	 * Creates a new GridView object based on this Activities current state. The
	 * GridView created will display this Activities ListView
	 * </p>
	 * 
	 * @param addParent
	 *            <p>
	 *            Whether or not to set the ViewFlipper as the new GridView's
	 *            Parent. if set to false the caller is contracted to add a
	 *            parent to the GridView.
	 *            </p>
	 */
	private void createListView(boolean addParent) {
		try {
			LayoutInflater inflater = (LayoutInflater) this
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			// Get List Flipper and add list
			listViewFlipper = (AcalViewFlipper) findViewById(R.id.month_list_flipper);
			listViewFlipper.setAnimationCacheEnabled(true);

			// Add parent if directed to do so
			if (addParent)
				listRoot = inflater.inflate(R.layout.month_list_view,
						listViewFlipper);
			else
				listRoot = inflater.inflate(R.layout.month_list_view, null);

			// Title
			eventListTitle = (TextView) listRoot
					.findViewById(R.id.month_list_title);
			// List
			eventList = (GridView) listRoot
					.findViewById(R.id.month_default_list);
			eventList.setSelector(R.drawable.no_border);
			eventList.setOnTouchListener(this);

		} catch (Exception e) {
			Log.e(TAG, "Error occured creating listview: " + e.getMessage());
		}
	}

	/**
	 * <p>
	 * Attempts to load state information from file. If successful, updates this
	 * Activities state with the loaded state.
	 * </p>
	 */
	private void loadState() {
		if (Constants.LOG_DEBUG) Log.d(TAG, "Loading month view state to file.");
		ObjectInputStream inputStream = null;
		Object sd = null;
		Object dm = null;
		try {
			File f = new File(STATE_FILE);
			if (!f.exists()) {
				// File does not exist.
				if (Constants.LOG_DEBUG) Log.d(TAG, "No state file to load.");
				return;
			}
			inputStream = new ObjectInputStream(new FileInputStream(STATE_FILE));
			sd = inputStream.readObject();
			dm = inputStream.readObject();
		} catch (ClassNotFoundException ex) {
			Log.w(TAG,
					"Error loading MonthView State - Incomplete data: "
							+ ex.getMessage());
		} catch (FileNotFoundException ex) {
			if (Constants.LOG_DEBUG) Log.d(TAG, "Error loading MonthView State - File Not Found: "
						+ ex.getMessage());
		} catch (IOException ex) {
			Log.w(TAG,
					"Error loading MonthView State - IO Error: "
							+ ex.getMessage());
		} finally {
			// Close the ObjectOutputStream
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (IOException ex) {
				Log.w(TAG,
						"Error closing MonthView file - IO Error: "
								+ ex.getMessage());
			}
		}
		if (sd != null && sd instanceof AcalDateTime) {
			this.changeSelectedDate((AcalDateTime) sd);
		}
		if (dm != null && dm instanceof AcalDateTime) {
			this.changeDisplayedMonth((AcalDateTime) dm);
		}
	}

	/**
	 * <p>
	 * Flips the current month view either forward or back depending on flip
	 * amount parameter.
	 * </p>
	 * 
	 * @param flipAmount
	 *            <p>
	 *            The number of months to move forward(Positive) or
	 *            back(negative). Setting to 0 may cause unexpected behaviour.
	 *            </p>
	 */
	private void flipMonth(int flipAmount) {
		try {
			int cur = gridViewFlipper.getDisplayedChild();
			createGridView(false); // We will attach the parent ourselves.
			AcalDateTime newDate = (AcalDateTime) displayedMonth.clone();

			// Handle year change
			newDate.set(AcalDateTime.DAY_OF_MONTH, 1);
			int newMonth = newDate.get(AcalDateTime.MONTH) + flipAmount;
			int newYear = newDate.get(AcalDateTime.YEAR);
			while (newMonth > AcalDateTime.DECEMBER) {
				newMonth -= 12;
				newYear++;
			}
			while (newMonth < AcalDateTime.JANUARY) {
				newMonth += 12;
				newYear--;
			}
			// Set newDate values
			newDate.set(AcalDateTime.YEAR, newYear);
			newDate.set(AcalDateTime.MONTH, newMonth);

			// Change this Activities state
			changeDisplayedMonth(newDate);

			// Set the new views parent. We need to ensure that the view does
			// not already have one.
			if (gridView.getParent() == gridViewFlipper)
				gridViewFlipper.removeView(gridView);
			gridViewFlipper.addView(gridRoot, cur + 1);

			// Make sure View responds to gestures
			gridView.setFocusableInTouchMode(true);
		} catch (Exception e) {
			Log.e(TAG, "Error occured in flipMonth: " + e.getMessage());
		}
	}

	/**
	 * <p>
	 * Flips the current Event view either forward or back depending on flip
	 * amount parameter.
	 * </p>
	 * 
	 * @param flipAmount
	 *            <p>
	 *            The number of days to move forward(Positive) or
	 *            back(negative). Setting to 0 may cause unexpected behaviour.
	 *            </p>
	 */
	private void flipDay() {
		try {
			int cur = gridViewFlipper.getDisplayedChild();
			createListView(false);
			listViewFlipper.addView(listRoot, cur + 1);
		} catch (Exception e) {
			Log.e(TAG, "Error occured in flipDay: " + e.getMessage());
		}
	}

	/**
	 * <p>
	 * Called when user has selected 'Settings' from menu. Starts Settings
	 * Activity.
	 * </p>
	 */
	private void settings() {
		Intent settingsIntent = new Intent();
		settingsIntent.setClassName("com.morphoss.acal",
				"com.morphoss.acal.activity.Settings");
		this.startActivity(settingsIntent);
	}

	/**
	 * <p>
	 * Responsible for nice animation when ViewFlipper changes from one View to
	 * the Next.
	 * </p>
	 * 
	 * @param objectTouched
	 *            <p>
	 *            The Object that was 'swiped'
	 *            </p>
	 * @param left
	 *            <p>
	 *            Indicates swipe direction. If true, swipe left, else swipe
	 *            right.
	 *            </p>
	 * 
	 * @return <p>
	 *         Swipe success. False if object passed is not 'swipable'
	 *         </p>
	 */
	private boolean swipe(Object objectTouched, boolean left) {
		if (objectTouched == null)
			return false;
		else if (objectTouched == gridView) {
			if (left) {
				gridViewFlipper.setInAnimation(leftIn);
				gridViewFlipper.setOutAnimation(leftOut);
				flipMonth(1);
			} else {
				gridViewFlipper.setInAnimation(rightIn);
				gridViewFlipper.setOutAnimation(rightOut);
				flipMonth(-1);
			}
			int cur = gridViewFlipper.getDisplayedChild();
			gridViewFlipper.showNext();
			gridViewFlipper.removeViewAt(cur);
			return true;

		} else if (objectTouched == eventList) {
			int curMonth = selectedDate.get(AcalDateTime.MONTH);
			int dispMonth = displayedMonth.get(AcalDateTime.MONTH);
			AcalDateTime newDate = null;
			listViewFlipper.setFlipInterval(0);
			if (left) {
				listViewFlipper.setInAnimation(leftIn);
				listViewFlipper.setOutAnimation(leftOut);
				newDate = AcalDateTime.addDays(selectedDate, 1);
				flipDay();
			} else {
				listViewFlipper.setInAnimation(rightIn);
				listViewFlipper.setOutAnimation(rightOut);
				newDate = AcalDateTime.addDays(selectedDate, -1);
				flipDay();

			}
			int cur = listViewFlipper.getDisplayedChild();
			listViewFlipper.showNext();
			listViewFlipper.removeViewAt(cur);
			changeSelectedDate(newDate);
			if (eventList.getParent() == listViewFlipper)
				listViewFlipper.removeView(eventList);
			eventList.setFocusableInTouchMode(true);

			// Did the month change?
			if ((curMonth == dispMonth)
					&& (curMonth != selectedDate.get(AcalDateTime.MONTH))) {
				// Flip month as well
				swipe(gridView, left);
			}
			return true;
		}
		return false;
	}

	/**
	 * <p>
	 * Determines what object was under the 'finger' of the user when they
	 * started a gesture.
	 * </p>
	 * 
	 * @param x
	 *            <p>
	 *            The X co-ordinate of the press.
	 *            </p>
	 * @param y
	 *            <p>
	 *            The Y co-ordinate of the press.
	 *            </p>
	 * @return <p>
	 *         The object beneath the press, or null if none.
	 *         </p>
	 */
	private Object getTouchedObject(double x, double y) {
		int[] lvc = new int[2];
		this.eventList.getLocationOnScreen(lvc);
		int lvh = this.eventList.getHeight();
		int lvw = this.eventList.getWidth();
		if ((x >= lvc[0]) && (x <= lvc[0] + lvw) && (y >= lvc[1])
				&& (y <= lvc[1] + lvh))
			return this.eventList;

		int[] gvc = new int[2];
		this.gridView.getLocationOnScreen(gvc);
		int gvh = this.gridView.getHeight();
		int gvw = this.gridView.getWidth();
		if ((x >= gvc[0]) && (x <= gvc[0] + gvw) && (y >= gvc[1])
				&& (y <= gvc[1] + gvh))
			return this.gridView;

		return null;
	}

	/****************************************************
	 * Public Methods *
	 ****************************************************/

	/**
	 * <p>
	 * Changes the displayed month to the month represented by the provided
	 * calendar.
	 * </p>
	 */
	public void changeDisplayedMonth(AcalDateTime calendar) {
		this.displayedMonth = calendar;
		this.monthTitle.setText(AcalDateTime.fmtMonthYear(calendar));
		if (AcalDateTime.isWithinMonth(selectedDate, displayedMonth))
			this.gridView.setAdapter(new MonthAdapter(this, selectedDate,
					selectedDate));
		else
			this.gridView.setAdapter(new MonthAdapter(this, displayedMonth,
					selectedDate));
		this.gridView.refreshDrawableState();
	}

	/**
	 * <p>
	 * Changes the selected date to the date represented by the provided
	 * calendar.
	 * </p>
	 */
	@SuppressWarnings("unchecked")
	public void changeSelectedDate(AcalDateTime c) {

		this.selectedDate = c;
		this.eventListTitle.setText(AcalDateTime.fmtDayMonthYear(c));
		this.eventListAdapter = new EventListAdapter(this,
				selectedDate.clone());
		this.eventList.setAdapter(eventListAdapter);
		this.eventList.refreshDrawableState();
		if (AcalDateTime.isWithinMonth(selectedDate, displayedMonth)) {
			this.gridView.setAdapter(new MonthAdapter(this, displayedMonth
					.clone(), selectedDate.clone()));
			this.gridView.refreshDrawableState();
		} else {
			((MonthAdapter) this.gridView.getAdapter())
					.updateSelectedDay(selectedDate);
			this.gridView.refreshDrawableState();
		}
	}

	private void dataServiceInitialise(boolean state) {
		if (eventCache != null) eventCache.flushCache();
		if (!state)
			changeSelectedDate(this.selectedDate);
	}


	/**
	 * Methods for managing event structure
	 */
	public ArrayList<AcalEvent> getEventsForDay(AcalDateTime day) {
		if (eventCache == null) return new ArrayList<AcalEvent>();
		return eventCache.getEventsForDay(day);
	}

	public int getNumberEventsForDay(AcalDateTime day) {
		if (eventCache == null) return 0;
		return eventCache.getNumberEventsForDay(day);
	}

	public AcalEvent getNthEventForDay(AcalDateTime day, int n) {
		if (eventCache == null) return null;
		return eventCache.getNthEventForDay(day, n);
	}

	public void deleteSingleEvent(AcalDateTime day, int n) {
		if (eventCache == null) return;
		AcalEvent ae = eventCache.getNthEventForDay(day, n);
		AcalEventAction action = new AcalEventAction(ae);
		action.setAction(AcalEventAction.ACTION_DELETE_SINGLE);
		try {
			this.dataRequest.eventChanged(action);
			eventCache.deleteEvent(day, n);
		} catch (RemoteException e) {
			Log.e(TAG,"Error deleting event: "+e);
			
		}
		this.changeSelectedDate(this.selectedDate);
	}
	public void deleteAllEvent(AcalDateTime day, int n) {
		if (eventCache == null) return;
		AcalEvent ae = eventCache.getNthEventForDay(day, n);
		AcalEventAction action = new AcalEventAction(ae);
		action.setAction(AcalEventAction.ACTION_DELETE_ALL);
		try {
			this.dataRequest.eventChanged(action);
			eventCache.deleteEvent(day, n);
		} catch (RemoteException e) {
			Log.e(TAG,"Error deleting event: "+e);
		}
		this.changeSelectedDate(this.selectedDate);
	}
	public void deleteFutureEvent(AcalDateTime day, int n) {
		if (eventCache == null) return;
		AcalEvent ae = eventCache.getNthEventForDay(day, n);
		AcalEventAction action = new AcalEventAction(ae);
		action.setAction(AcalEventAction.ACTION_DELETE_ALL_FUTURE);
		try {
			this.dataRequest.eventChanged(action);
			eventCache.deleteEvent(day, n);
		} catch (RemoteException e) {
			Log.e(TAG,"Error deleting event: "+e);
			
		}
		this.changeSelectedDate(this.selectedDate);
	}

	/********************************************************************
	 * Implemented Interface Overrides *
	 ********************************************************************/

	/**
	 * <p>
	 * Responsible for handling the menu button push.
	 * </p>
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_options_menu, menu);
		return true;
	}

	/**
	 * <p>
	 * Called when user has touched the screen. Handled by our Gesture Detector.
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (Constants.debugMonthView && Constants.LOG_VERBOSE)	Log.v(TAG, "onTouchEvent called at (" + event.getRawX() + ","
					+ event.getRawY() + ")");
		return gestureDetector.onTouchEvent(event);
	}

	/**
	 * <p>
	 * Called when user has touched the screen. Handled by our Gesture Detector.
	 * </p>
	 * 
	 * (non-Javadoc)
	 * 
	 * @see android.view.View.OnTouchListener#onTouch(android.view.View,
	 *      android.view.MotionEvent)
	 */
	@Override
	public boolean onTouch(View view, MotionEvent touch) {
		if (Constants.debugMonthView && Constants.LOG_VERBOSE) 		Log.v(TAG, "onTouch called with touch at (" + touch.getRawX() + ","
					+ touch.getRawY() + ") touching view " + view.getId());
		return this.gestureDetector.onTouchEvent(touch);
	}

	/**
	 * <p>
	 * Called when the user selects an option from the options menu. Determines
	 * what (if any) Activity should start.
	 * </p>
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.settingsMenuItem:
			settings();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * <p>
	 * The main handler for Gestures in this activity. At this time we are only
	 * interested in scroll gestures. Determines what object the gesture
	 * occurred on, whether the gesture is suitable to respond to, and finally,
	 * kicks of an appropriate response.
	 * </p>
	 */
	@Override
	public boolean onScroll(MotionEvent start, MotionEvent current, float dx,
			float dy) {
		try {
			// We can't work with null objects
			if (start == null || current == null)
				return false;

			// Calculate all values required to identify if we need to react
			double startX = start.getRawX();
			double startY = start.getRawY();
			double distX = current.getRawX() - startX;
			double distY = current.getRawY() - startY;
			double totalDist = Math.sqrt(Math.pow(distX, 2)
					+ Math.pow(distY, 2));
			double angle = 180 + ((Math.atan2(distY, distX)) * (180.0 / Math.PI));
			Object scrolledOn = getTouchedObject(startX, startY);
			boolean isHorizontal = false;
			boolean isVertical = false;
			if ((angle > 360 - maxAngleDev || angle < 0 + maxAngleDev)
					|| (angle > 180 - maxAngleDev && angle < 180 + maxAngleDev)) {
				isHorizontal = true;
			} else if ((angle > 90 - maxAngleDev && angle < 90 + maxAngleDev)
					|| (angle > 270 - maxAngleDev && angle < 270 + maxAngleDev)) {
				isVertical = true;
			}

			// Report calculations
			if (Constants.debugMonthView && Constants.LOG_DEBUG)	Log.d(TAG, "onScroll called with onDown at (" + startX + ","
						+ startY + ") " + "and with distance (" + distX + ","
						+ distY + "), " + "angle is" + angle);

			// Some conditions that work out if we are interested in this event.
			if ((consumedX == startX && consumedY == startY) || // We've already
																// consumed the
																// event
					(totalDist < minDistance) || // Not a long enough swipe
					(scrolledOn == null) || // Nothing underneath touch of
											// interest
					(!isHorizontal && !isVertical) // Direction is not of
													// intrest
			) {
				if (Constants.debugMonthView && Constants.LOG_DEBUG)Log.d(TAG, "onScroll ignored.");
				return false;
			}

			long startFlip = System.currentTimeMillis();
			if (Constants.debugMonthView && Constants.LOG_DEBUG)Log.d(TAG, "Valid onScroll detected.");

			// If we are here, we have a valid scroll event to process
			if (gridView != null && scrolledOn == gridView) {
				if (isHorizontal) {
					if (distX > 0)
						swipe(gridView, false);
					else
						swipe(gridView, true);
					consumedX = startX;
					consumedY = startY;
					return true;
				} else if (isVertical) {
					return false;
				}
			} else if (eventList != null && scrolledOn == eventList) {
				if (isHorizontal) {
					this.eventListAdapter.setClickEnabled(false);
					if (distX > 0)
						swipe(eventList, false);
					else
						swipe(eventList, true);
					consumedX = startX;
					consumedY = startY;
					if (Constants.debugMonthView && Constants.LOG_DEBUG) Log.d(TAG,
								"Scroll took ."
										+ (System.currentTimeMillis() - startFlip)
										+ "ms to process.");
					return true;
				}
			}
		} catch (Exception e) {
			Log.e(TAG,
					"Unknown error occurred processing scroll event: "
							+ e.getMessage() + Log.getStackTraceString(e));
		}
		return false;
	}

	/**
	 * <p>
	 * Handles button Clicks
	 * </p>
	 */
	@Override
	public void onClick(View clickedView) {
		int button = (int) ((Integer) clickedView.getTag());
		switch (button) {
		case TODAY:
			AcalDateTime cal = new AcalDateTime();

			if (cal.getEpochDay() == this.selectedDate.getEpochDay()) {
				this.gridViewFlipper.setAnimation(AnimationUtils.loadAnimation(
						this, R.anim.shake));
				this.listViewFlipper.setAnimation(AnimationUtils.loadAnimation(
						this, R.anim.shake));
			}
			this.changeSelectedDate(cal);
			this.changeDisplayedMonth(cal);

			break;
		case ADD:
			Bundle bundle = new Bundle();
			bundle.putParcelable("DATE", this.selectedDate);
			Intent eventEditIntent = new Intent(this, EventEdit.class);
			eventEditIntent.putExtras(bundle);
			this.startActivity(eventEditIntent);
			break;
		case YEAR:
			bundle = new Bundle();
			bundle.putInt("StartYear", selectedDate.getYear());
			Intent yearIntent = new Intent(this, YearView.class);
			yearIntent.putExtras(bundle);
			this.startActivityForResult(yearIntent, PICK_MONTH_FROM_YEAR_VIEW);
			break;
		default:
			Log.w(TAG, "Unrecognised button was pushed in MonthView.");
		}
	}

	/************************************************************************
	 * Service Connection management *
	 ************************************************************************/

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			dataRequest = DataRequest.Stub.asInterface(service);
			eventCache = new EventCache(dataRequest);
			try {
				dataRequest.registerCallback(mCallback);
				
			} catch (RemoteException re) {

			}
			isBound = true;
			changeSelectedDate(selectedDate);
		}

		public void onServiceDisconnected(ComponentName className) {
			dataRequest = null;
			eventCache = null;
			isBound = false;
		}
	};

	/**
	 * This implementation is used to receive callbacks from the remote service.
	 */
	private DataRequestCallBack mCallback = new DataRequestCallBack.Stub() {
		/**
		 * This is called by the remote service regularly to tell us about new
		 * values. Note that IPC calls are dispatched through a thread pool
		 * running in each process, so the code executing here will NOT be
		 * running in our main thread like most other things -- so, to update
		 * the UI, we need to use a Handler to hop over there.
		 */
		public void statusChanged(int type, boolean value) {
			mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, type,
					(value ? 1 : 0)));
		}
	};

	private static final int BUMP_MSG = 1;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			int type = msg.arg1;
			int val = msg.arg2;
			switch (type) {
			case CalendarDataService.UPDATE:
				MonthView.this.dataServiceInitialise(false);
				break;
			}

		}

	};

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return this.eventListAdapter.contextClick(item);
	}


	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ( resultCode == RESULT_OK ) {
			switch ( requestCode ) {
				case PICK_MONTH_FROM_YEAR_VIEW:
					if (data.hasExtra("selectedDate")) {
						try {
							AcalDateTime month = (AcalDateTime) data.getParcelableExtra("selectedDate");
							this.changeDisplayedMonth(month);
						} catch (Exception e) {
							Log.w(TAG, "Error getting month back from year view: "+e);
						}
					}
					break;
				case PICK_TODAY_FROM_EVENT_VIEW:
					try {
						AcalDateTime chosenDate = (AcalDateTime) data.getParcelableExtra("selectedDate");
						this.changeDisplayedMonth(chosenDate);
						this.changeSelectedDate(chosenDate);
					} catch (Exception e) {
						Log.w(TAG, "Error getting month back from year view: "+e);
					}
			}
			// Save state
			if (Constants.LOG_DEBUG) Log.d(TAG, "Writing month view state to file.");
			ObjectOutputStream outputStream = null;
			try {
				outputStream = new ObjectOutputStream(new FileOutputStream(
						STATE_FILE));
				outputStream.writeObject(this.selectedDate);
				outputStream.writeObject(this.displayedMonth);
			} catch (FileNotFoundException ex) {
				Log.w(TAG,
						"Error saving MonthView State - File Not Found: "
								+ ex.getMessage());
			} catch (IOException ex) {
				Log.w(TAG,
						"Error saving MonthView State - IO Error: "
								+ ex.getMessage());
			} finally {
				// Close the ObjectOutputStream
				try {
					if (outputStream != null) {
						outputStream.flush();
						outputStream.close();
					}
				} catch (IOException ex) {
					Log.w(TAG, "Error closing MonthView file - IO Error: "
							+ ex.getMessage());
				}
			}
		}
	}

	/************************************************************************
	 * Required Overrides that aren't used *
	 ************************************************************************/

	@Override
	public boolean onDown(MotionEvent downEvent) {
		return false;
	}

	@Override
	public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		return false;
	}

	@Override
	public void onLongPress(MotionEvent downEvent) {
	}

	@Override
	public void onShowPress(MotionEvent downEvent) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent upEvent) {
		return false;
	}
}
