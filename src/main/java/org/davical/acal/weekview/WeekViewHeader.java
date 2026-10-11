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
package org.davical.acal.weekview;

import java.text.SimpleDateFormat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.davical.acal.R;
import org.davical.acal.acaltime.AcalDateTime;

public class WeekViewHeader extends ImageView {
	
	private WeekViewActivity context; 
	private AcalDateTime date;
	
	/** Default Constructor */
	public WeekViewHeader(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity))
			throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	/** Default Constructor */
	public WeekViewHeader(Context context, AttributeSet attrs) {
		super(context,attrs);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity))
			throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
		this.date = this.context.getCurrentDate();
	}
	
	
	/** Default Constructor */
	public WeekViewHeader(Context context) {
		super(context);
		if (this.isInEditMode()) {
			return;
		}
		if (!(context instanceof WeekViewActivity)) 
			throw new IllegalStateException("Week View Started with invalid context.");
		this.context = (WeekViewActivity) context;
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (this.isInEditMode()) {
			Paint p = new Paint();
			p.setStyle(Paint.Style.FILL);
			p.setColor(Color.parseColor("#333333"));
			canvas.drawRect(0, 0, canvas.getWidth(), getHeight(), p);
			return;
		}
		date = this.context.getCurrentDate();
		AcalDateTime startDate = date.clone();
		startDate.addDays(-1);		//we start one day before the current date, current date should be first fully visible date
		int dayWidth = WeekViewActivity.DAY_WIDTH;
		
		int x = (int)(0-dayWidth+context.getScrollX()); int y = 0;
		
		int dayHeight = this.getHeight();
		int totalWidth = this.getWidth();
		AcalDateTime today = new AcalDateTime().applyLocalTimeZone();
		while(x<(totalWidth+dayWidth)) {		//continue until we have draw one past screen edge
			drawBox (x, y,dayWidth,dayHeight,canvas,startDate);
			if ( startDate.isSameDay(today) ) drawTodayBox(x, y, dayWidth, dayHeight, canvas);
			startDate.addDays(1);
			x+=dayWidth;
		}
		
		
	}

	/** Puts a box around the heading of today, in the colour the month view uses for it. */
	private void drawTodayBox(int x, int y, int w, int h, Canvas c) {
		int thickness = Math.max(2, Math.round(2 * getResources().getDisplayMetrics().density));
		Paint p = new Paint();
		p.setStyle(Paint.Style.FILL);
		p.setColor(ContextCompat.getColor(context, R.color.MonthDayTodayBox));
		c.drawRect(x, y, x + w, y + thickness, p);
		c.drawRect(x, y + h - thickness, x + w, y + h, p);
		c.drawRect(x, y, x + thickness, y + h, p);
		c.drawRect(x + w - thickness, y, x + w, y + h, p);
	}

	//TODO move to image cache
	private void drawBox(int x, int y, int w, int h, Canvas c, AcalDateTime day) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView title = ((TextView) v.findViewById(R.id.WV_header_day_box));
		title.setVisibility(View.VISIBLE);
		String formatString = "EEE\nMMM d";
		if (day.get(AcalDateTime.DAY_OF_WEEK) == WeekViewActivity.FIRST_DAY_OF_WEEK) {
			formatString+=" (w)";
		}
		SimpleDateFormat formatter = new SimpleDateFormat(formatString);
		title.setText(formatter.format(day.toJavaDate()));

		title.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
		title.layout(0, 0, w, h);
		Bitmap returnedBitmap = Bitmap.createBitmap(w, h,Bitmap.Config.ARGB_8888);
		Canvas tempCanvas = new Canvas(returnedBitmap);
		title.draw(tempCanvas);
		c.drawBitmap(returnedBitmap,x, y, new Paint());
	}
}
