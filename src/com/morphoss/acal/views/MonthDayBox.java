/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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

package com.morphoss.acal.views;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;

public class MonthDayBox extends TextView {

	private List<AcalEvent> events;
	private boolean isToday = false;
	private Context context;
	
	public MonthDayBox(Context context) {
		super(context);
		this.context = context;
	}
	
	public MonthDayBox(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}
	public MonthDayBox(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
	}	
	
	public void setEvents(List<AcalEvent> events) {
		this.events = events;
	}
	
	@Override
	public void draw(Canvas arg0) {
		super.draw(arg0);
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		if ( isToday ) {
			float width = getWidth();
			float height = getHeight();
			int x = (int) (width/16f);
			int y = (int) (height/16f);
			if ( x < 1 ) x =1;
			if ( y < 1 ) y =1;
			if ( x < y ) x =y;
			if ( y < x ) y =x;
			p.setColor(0xffe77720);
			arg0.drawRect(0, 0, width, y, p);
			arg0.drawRect(0, 0, x, height, p);
			arg0.drawRect(width-x, 0, width, height, p);
			arg0.drawRect(0, height-y, width, height, p);
		}
		if (events != null && !events.isEmpty()) {
			//Get the range of hours for todays events (min = 9am -> 5pm)
			int startHour = 9;	int endHour = 17;
			for (AcalEvent e : events) {
				if (e.dtstart.isDate()) continue;
				int eh = AcalDateTime.addDuration(e.dtstart, e.duration).getHour();
				int sh = e.dtstart.getHour();
				if (eh == 0) eh = 24;
				if (eh > endHour) endHour = eh;
				if (sh < startHour) startHour = sh;
			}
			int numHours = endHour-startHour;
			
			float height = getHeight();
			int width = getWidth()/5;
			float hourHeight = height/numHours;
			for (AcalEvent e : events) {
				float stHour = e.dtstart.getHour();
				float finHour = AcalDateTime.addDuration(e.dtstart, e.duration).getHour();
				
				if (e.dtstart.isDate()) {
					stHour = 0;
					finHour = endHour-stHour;
				} else {
					//Ensure that startHour and EndHour are different and fix end hour of 0 to 24
					if (finHour >24 || finHour == 0) finHour = 24;
					if (stHour == finHour)
						if (stHour == 24) stHour--;
						else finHour++;
				
					//Shift down
					stHour-=startHour;
					finHour-=startHour;
				}
				//draw
				p.setColor((e.colour|0xff000000)-0x77000000);
				arg0.drawRect((isToday?getWidth()/15:0),stHour*hourHeight, width, finHour*hourHeight, p);
				
			}
			
		}
	}

	public void setToday() {
		isToday = true;
	}

	
}
