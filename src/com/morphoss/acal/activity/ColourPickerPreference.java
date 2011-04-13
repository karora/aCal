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

import android.content.Context;
import android.content.DialogInterface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.morphoss.acal.activity.ColourPickerDialog.OnColourPickerListener;

public class ColourPickerPreference extends DialogPreference  {
	public static final String TAG = "aCal ColourPickerPreference";

	private Context context;
	private ColourPickerDialog dialog;
	private int color;
	
	public ColourPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}
	
	@Override
	public void onDialogClosed(boolean positiveResult) {
		this.dialog = null;
	}
	
	public int getColor() {
		return this.color;
	}
	
	public void setColor(int color) {
		this.color = color;
	}
	
	protected void showDialog() {
		dialog.show();
		
	}
	
	@Override
	public View getView(View convertView, ViewGroup parent) {
		View v = super.getView(convertView, parent);
		TextView tv = (TextView) v.findViewById(android.R.id.title);
		tv.setTextColor(this.color);
		return v;
	}
	
	
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case -1: {
				ColourPickerPreference.this.color = this.dialog.warnaBaru;
				ColourPickerPreference.this.callChangeListener(this.dialog.warnaBaru);
				break;
			}
		}
	}
	
	protected View onCreateDialogView() {
	dialog =  new ColourPickerDialog(this.context,color, new OnColourPickerListener() {

			@Override
			public void onCancel(ColourPickerDialog dialog) {
				ColourPickerPreference.this.callChangeListener(color);				
			}

			@Override
			public void onOk(ColourPickerDialog dialog, int color) {
				ColourPickerPreference.this.color = color;
				ColourPickerPreference.this.callChangeListener(color);
			}
			
		});
		return dialog.primaryView;
	}
}
