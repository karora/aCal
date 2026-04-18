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
 * Based (with some changes) on:
 * 	http://code.google.com/p/android-color-picker/
 * which is available under the Apache 2 license.
 */

package com.morphoss.acal.activity;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.morphoss.acal.activity.ColourPickerDialog.OnColourPickerListener;

public class ColourPickerPreference extends Preference {
	public static final String TAG = "aCal ColourPickerPreference";

	private static final int FALLBACK_DEFAULT = 0xFF808080;

	private int colour = FALLBACK_DEFAULT;

	public ColourPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ColourPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInteger(index, FALLBACK_DEFAULT);
	}

	@Override
	protected void onSetInitialValue(Object defaultValue) {
		int fallback = defaultValue instanceof Integer ? (Integer) defaultValue : FALLBACK_DEFAULT;
		colour = getPersistedInt(fallback);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		TextView title = (TextView) holder.findViewById(android.R.id.title);
		if (title != null) {
			title.setTextColor(colour);
		}
	}

	public int getColour() {
		return this.colour;
	}

	public void setColor(int colour) {
		this.colour = colour;
		persistInt(colour);
		notifyChanged();
	}

	@Override
	protected void onClick() {
		ColourPickerDialog dialog = new ColourPickerDialog(getContext(), colour, new OnColourPickerListener() {
			@Override
			public void onCancel(ColourPickerDialog dialog) {
				callChangeListener(colour);
			}

			@Override
			public void onOk(ColourPickerDialog dialog, int color) {
				colour = color;
				persistInt(colour);
				callChangeListener(color);
				notifyChanged();
			}
		});
		dialog.show();
	}
}
