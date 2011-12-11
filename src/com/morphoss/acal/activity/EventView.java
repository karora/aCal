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

import java.util.List;
import java.util.TimeZone;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.AcalEvent;
import com.morphoss.acal.davacal.SimpleAcalEvent;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.service.aCalService;

public class EventView extends AcalActivity implements OnGestureListener, OnTouchListener, OnClickListener{

	public static final String TAG = "aCal EventView";
	public static final int TODAY = 0;
	public static final int EDIT = 1;
	public static final int ADD = 2;
	public static final int SHOW_ON_MAP = 3;
	
	public static final int EDIT_EVENT = 0;
	public static final int EDIT_ADD = 0;
	
	
	public static final String SIMPLE_ACAL_EVENT_KEY = "SimpleAcalEvent";
	public static final String ACAL_EVENT_KEY = "AcalEvent";
	public static final String RESOURCE_ID_KEY = "resourceid";
	public static final String DTSTART_KEY = "start";
	
	private AcalEvent event = null;
	private SimpleAcalEvent sae = null;
	
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.event_view);
		
		//Ensure service is actually running
		this.startService(new Intent(this, aCalService.class));
		//gestureDetector = new GestureDetector(this);

		//Set up buttons
		this.setupButton(R.id.event_today_button, TODAY);
		this.setupButton(R.id.event_edit_button, EDIT);
		this.setupButton(R.id.event_add_button, ADD);
		
		Bundle b = this.getIntent().getExtras();
		try {
			long start = -1;
			int rid = -1;
			
			if (b.containsKey(ACAL_EVENT_KEY)) this.event = b.getParcelable(ACAL_EVENT_KEY);
			if (b.containsKey(SIMPLE_ACAL_EVENT_KEY)) this.sae = b.getParcelable(SIMPLE_ACAL_EVENT_KEY);
			if (b.containsKey(RESOURCE_ID_KEY)) rid = b.getInt(RESOURCE_ID_KEY);
			if (b.containsKey(DTSTART_KEY)) start = b.getLong(DTSTART_KEY);
			
			if (this.event == null && this.sae == null && rid >= 0 && start >=0) {
				AcalDateTime dtStart = AcalDateTime.localTimeFromMillis(start,false);
				this.event = AcalEvent.fromDatabase(this, rid, dtStart);
				dtStart = event.getStart();
				if ( dtStart.isFloating() ) {
					// All this weird screwing around because we don't pass floating into the widget table
					// which we will fix properly after the current refactoring
					dtStart.addSeconds( (TimeZone.getDefault().getOffset(dtStart.getMillis()) / 1000) );
					event = AcalEvent.fromDatabase(this, rid, dtStart);
				}
				this.sae = SimpleAcalEvent.getSimpleEvent(this.event);
			}
			else if (this.event != null && this.sae == null) this.sae = SimpleAcalEvent.getSimpleEvent(this.event);
			else if (this.sae != null && this.event == null) this.event = sae.getAcalEvent(this);
			else {
				if (Constants.LOG_DEBUG)Log.d(TAG, "Calling activity has not provided required data.");
				this.finish();
				return;
			}
			this.populateLayout();
		}
		catch (Exception e) {
			if (Constants.LOG_DEBUG)Log.d(TAG, "Error getting data from caller: "+e.getMessage());
		}
		
		Button map = (Button) this.findViewById(R.id.EventFindOnMapButton);
		map.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				String loc = event.getLocation();
				//replace whitespaces with '+'
				loc.replace("\\s", "+");
				Uri target = Uri.parse("geo:0,0?q="+loc);
				startActivity(new Intent(android.content.Intent.ACTION_VIEW, target)); 
				//start map view
				return;

			}
		});
		
	}
	
	private void populateLayout() {
		AcalDateTime start = event.getStart();
		String title = event.getSummary();
		String location = event.getLocation();
		String description = event.getDescription();
		StringBuilder alarms = new StringBuilder();
		List<AcalAlarm> alarmList = event.getAlarms();
		for (AcalAlarm alarm : alarmList) {
			if ( alarms.length() > 0 ) alarms.append('\n');
			alarms.append(alarm.toPrettyString());
		}
		
		String repetition = event.getRepetition();
		int colour = event.getColour();
		LinearLayout sidebar = (LinearLayout)this.findViewById(R.id.EventViewColourBar);
		LinearLayout sidebarBottom = (LinearLayout)this.findViewById(R.id.EventViewColourBarBottom);;
		sidebar.setBackgroundColor(colour);
		sidebarBottom.setBackgroundColor(colour);
		
		TextView name = (TextView) this.findViewById(R.id.EventName);
		name.setText(title);
		name.setTextColor(colour);
		
		AcalDateTime viewDate = new AcalDateTime().applyLocalTimeZone().setDaySecond(0);
		TextView time = (TextView) this.findViewById(R.id.EventTimeContent);
		time.setText(event.getTimeText(viewDate, viewDate.clone().addDays(1),
					prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), false)));
		time.setTextColor(colour);

		TextView locationView = (TextView) this.findViewById(R.id.EventLocationContent);
		if ( location != null && ! location.equals("") ) {
			locationView.setText(location);
		}
		else {
			RelativeLayout locationLayout = (RelativeLayout) this.findViewById(R.id.EventLocationLayout);
			locationLayout.setVisibility(View.GONE);
		}

		TextView notesView = (TextView) this.findViewById(R.id.EventNotesContent);
		if ( description != null && ! description.equals("") ) {
			notesView.setText(description);
		}
		else {
			RelativeLayout notesLayout = (RelativeLayout) this.findViewById(R.id.EventNotesLayout);
			notesLayout.setVisibility(View.GONE);
		}
		
		TextView alarmsView = (TextView) this.findViewById(R.id.EventAlarmsContent);
		if ( alarms != null && ! alarms.equals("") ) {
			alarmsView.setText(alarms);
			if ( !event.getAlarmEnabled() ) {
				TextView alarmsWarning = (TextView) this.findViewById(R.id.CalendarAlarmsDisabled);
				alarmsWarning.setVisibility(View.VISIBLE);
			}
		}
		else {
			RelativeLayout alarmsLayout = (RelativeLayout) this.findViewById(R.id.EventAlarmsLayout);
			alarmsLayout.setVisibility(View.GONE);
		}

		
		ContentValues[] activeCollections = DavCollections.getCollections( getContentResolver(), DavCollections.INCLUDE_EVENTS );
		RelativeLayout collectionLayout = (RelativeLayout) this.findViewById(R.id.EventCollectionLayout);
		if ( activeCollections.length < 2 ) {
			collectionLayout.setVisibility(View.GONE);
		}
		else {
			TextView collectionText = (TextView) this.findViewById(R.id.EventCollectionContent);
			collectionText.setTextColor(colour);
			int i=0;
			while( i<activeCollections.length && event.getCollectionId() != activeCollections[i].getAsInteger(DavCollections._ID)) i++;
			if ( i<activeCollections.length )
				collectionText.setText(activeCollections[i].getAsString(DavCollections.DISPLAYNAME));
			else
				collectionLayout.setVisibility(View.GONE);
		}
		TextView repeatsView = (TextView) this.findViewById(R.id.EventRepeatsContent);
		AcalRepeatRule RRule = new AcalRepeatRule(start, repetition); 
		String rr = RRule.repeatRule.toPrettyString(this);
		if (rr == null || rr.equals("")) rr = getString(R.string.OnlyOnce);
		repeatsView.setText(rr);
		
		
	}
	
	private void setupButton(int id, int val) {
		Button myButton = (Button) this.findViewById(id);
		myButton.setOnClickListener(this);
		myButton.setTag(val);
		AcalTheme.setContainerFromTheme(myButton, AcalTheme.BUTTON);
	}

	@Override
	public boolean onDown(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onLongPress(MotionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onShowPress(MotionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onSingleTapUp(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onTouch(View arg0, MotionEvent arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onClick(View arg0) {
		int button = (int)((Integer)arg0.getTag());
		switch (button) {
			case EDIT: {
				//start event activity
				Bundle bundle = new Bundle();
				sae.operation = SimpleAcalEvent.EVENT_OPERATION_EDIT;
				bundle.putParcelable("SimpleAcalEvent", sae);
				Intent eventEditIntent = new Intent(this, EventEdit.class);
				eventEditIntent.putExtras(bundle);
				this.startActivityForResult(eventEditIntent,EDIT_EVENT);
				break;
			}
			case ADD: {
				Bundle bundle = new Bundle();
				bundle.putParcelable("DATE", event.getStart());
				if ( event.getStart().isDate() )
					bundle.putBoolean("ALLDAY", true);
				else
					bundle.putInt("TIME", event.getStart().applyLocalTimeZone().getDaySecond());
				Intent eventEditIntent = new Intent(this, EventEdit.class);
				eventEditIntent.putExtras(bundle);
				this.startActivityForResult(eventEditIntent,EDIT_ADD);
				break;
			}
			case TODAY: {
				AcalDateTime selectedDate = new AcalDateTime();
				Intent res = new Intent();
				res.putExtra("selectedDate", (Parcelable) selectedDate);
				this.setResult(RESULT_OK, res);
				this.finish();
				break;
			}
		}
		
	}
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == EDIT_EVENT && resultCode == RESULT_OK) {
			Bundle b = data.getExtras();
			SimpleAcalEvent tmpSae = (SimpleAcalEvent) b.getParcelable(EventEdit.resultSimpleAcalEvent);
			if ( tmpSae != null ) sae = tmpSae;
			this.event = (AcalEvent) b.getParcelable(EventEdit.resultAcalEvent);
			populateLayout();
    	}
    	else if (requestCode == EDIT_ADD && resultCode == RESULT_OK) {
			Intent res = new Intent();
			this.setResult(RESULT_OK, res);
			this.finish();
    	}
    }
	
}
