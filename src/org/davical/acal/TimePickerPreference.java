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
 * The original (Licence Free) code for this class was downloaded from http://www.ebessette.com/d/TimePickerPreference
 *
 */

package org.davical.acal;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

/**
 * A preference type that allows a user to choose a time (HH:MM).
 * Shows a standard Android TimePickerDialog on click.
 */
public class TimePickerPreference extends Preference {

	private static final String VALIDATION_EXPRESSION = "[0-2]?[0-9]:[0-5]?[0-9]";
	private static final String FALLBACK_DEFAULT = "12:00";

	private String currentValue = FALLBACK_DEFAULT;

	public TimePickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TimePickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		String v = a.getString(index);
		return v != null ? v : FALLBACK_DEFAULT;
	}

	@Override
	protected void onSetInitialValue(Object defaultValue) {
		String fallback = defaultValue instanceof String ? (String) defaultValue : FALLBACK_DEFAULT;
		currentValue = getPersistedString(fallback);
	}

	@Override
	protected void onClick() {
		int h = getHour();
		int m = getMinute();
		boolean is24 = PreferenceManager.getDefaultSharedPreferences(getContext())
				.getBoolean(getContext().getString(R.string.prefTwelveTwentyfour), false);

		new TimePickerDialog(getContext(),
				(view, hour, minute) -> {
					currentValue = String.format("%02d:%02d", hour, minute);
					persistString(currentValue);
					callChangeListener(currentValue);
					notifyChanged();
				},
				(h >= 0 && h < 24) ? h : 9,
				(m >= 0 && m < 60) ? m : 0,
				is24).show();
	}

	private int getHour() {
		if (currentValue == null || !currentValue.matches(VALIDATION_EXPRESSION)) return -1;
		return Integer.parseInt(currentValue.split(":")[0]);
	}

	private int getMinute() {
		if (currentValue == null || !currentValue.matches(VALIDATION_EXPRESSION)) return -1;
		return Integer.parseInt(currentValue.split(":")[1]);
	}
}
