/*
 * Copyright (C) 2026 Andrew McMillan
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

package org.davical.acal.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.davical.acal.AcalTheme;
import org.davical.acal.R;
import org.davical.acal.acaltime.AcalDateTime;
import org.davical.acal.acaltime.AcalRepeatRule;
import org.davical.acal.acaltime.AcalRepeatRule.RRuleFreqType;
import org.davical.acal.acaltime.AcalRepeatRuleDay;
import org.davical.acal.acaltime.AcalRepeatRuleParser;

/**
 * <p>Structured editor for an RRULE, launched from the "Custom…" entry of the repeat
 * choice list in EventEdit / TodoEdit.  Handles FREQ, INTERVAL, BYDAY (with ordinals,
 * so "1st and 3rd Tuesday" or "last Monday"), BYMONTHDAY, BYMONTH, COUNT and UNTIL
 * directly; anything beyond that (BYSETPOS, BYWEEKNO, …) is edited as a raw RRULE,
 * which is also available as an advanced mode.  A live preview shows the next few
 * occurrences of the rule as it is edited.</p>
 */
public class RepeatRuleEdit extends AcalAppCompatActivity {

	public static final String TAG = "aCal RepeatRuleEdit";

	public static final String EXTRA_RRULE = "RepeatRule";
	public static final String EXTRA_DTSTART = "DtStart";

	private static final int PREVIEW_INSTANCES = 4;

	/** Weekday order used throughout: AcalDateTime.MONDAY (0) … SUNDAY (6). */
	private static final String[] RRULE_DAYS = { "MO", "TU", "WE", "TH", "FR", "SA", "SU" };
	private static final String[] RRULE_FREQ = { "DAILY", "WEEKLY", "MONTHLY", "YEARLY" };
	/** Values behind the ordinal spinner entries; 0 means "every such weekday". */
	private static final int[] ORDINAL_VALUES = { 1, 2, 3, 4, 5, -1, -2, 0 };

	private AcalDateTime dtStart;

	private EditText intervalText;
	private Spinner freqSpinner;
	private LinearLayout weeklySection;
	private LinearLayout weekdayToggles;
	private LinearLayout yearlySection;
	private GridLayout monthToggles;
	private LinearLayout monthlySection;
	private RadioButton monthlyOnStartDay;
	private RadioButton monthlyOnDays;
	private RadioButton monthlyOnThe;
	private EditText monthDaysText;
	private LinearLayout bydayRows;
	private Button addBydayButton;
	private RadioButton endsNever;
	private RadioButton endsOnDate;
	private RadioButton endsAfter;
	private Button endsDateButton;
	private EditText endsCountText;
	private CheckBox rawCheckbox;
	private TextView advancedNotice;
	private EditText rawRuleText;
	private TextView ruleSummary;
	private TextView previewText;

	private AcalDateTime untilDate = null;
	private String[] ordinalNames;
	private String[] weekdayNames;

	/** Guard so programmatic changes to the controls don't recurse through listeners. */
	private boolean updating = false;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.repeat_rule_edit);

		toolbar = findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setBackgroundColor(AcalTheme.getToolbarColour());
			setSupportActionBar(toolbar);
			if (getSupportActionBar() != null) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(true);
				getSupportActionBar().setTitle(R.string.RepeatRuleTitle);
			}
			toolbar.setNavigationOnClickListener(v -> finish());
		}

		String startString = getIntent().getStringExtra(EXTRA_DTSTART);
		try {
			dtStart = AcalDateTime.fromString(startString);
		}
		catch (Exception e) {
			dtStart = new AcalDateTime();
		}
		if (dtStart == null) dtStart = new AcalDateTime();

		findViews();
		buildStaticControls();

		String rrule = getIntent().getStringExtra(EXTRA_RRULE);
		AcalRepeatRuleParser parsed = null;
		if (rrule != null && !rrule.equals("")) {
			try {
				parsed = AcalRepeatRuleParser.parseRepeatRule(rrule);
			}
			catch (Exception e) {
				Log.i(TAG, "Could not parse existing RRULE '" + rrule + "': " + e.getMessage());
			}
		}

		updating = true;
		setDefaults();
		if (parsed == null) {
			if (rrule != null && !rrule.equals("")) {
				// Unparseable: preserve it as raw text rather than destroying it.
				enterRawMode(rrule, true);
			}
		}
		else if (isRepresentable(parsed)) {
			populateFromRule(parsed);
		}
		else {
			enterRawMode(parsed.toString(), true);
		}
		updating = false;

		onFrequencyChanged();
		updatePreview();
	}

	private void findViews() {
		intervalText = findViewById(R.id.RepeatInterval);
		freqSpinner = findViewById(R.id.RepeatFreqSpinner);
		weeklySection = findViewById(R.id.WeeklySection);
		weekdayToggles = findViewById(R.id.WeekdayToggles);
		yearlySection = findViewById(R.id.YearlySection);
		monthToggles = findViewById(R.id.MonthToggles);
		monthlySection = findViewById(R.id.MonthlySection);
		monthlyOnStartDay = findViewById(R.id.MonthlyOnStartDay);
		monthlyOnDays = findViewById(R.id.MonthlyOnDays);
		monthlyOnThe = findViewById(R.id.MonthlyOnThe);
		monthDaysText = findViewById(R.id.MonthDaysText);
		bydayRows = findViewById(R.id.BydayRows);
		addBydayButton = findViewById(R.id.AddBydayRow);
		endsNever = findViewById(R.id.EndsNever);
		endsOnDate = findViewById(R.id.EndsOnDate);
		endsAfter = findViewById(R.id.EndsAfter);
		endsDateButton = findViewById(R.id.EndsDateButton);
		endsCountText = findViewById(R.id.EndsCount);
		rawCheckbox = findViewById(R.id.RawRuleCheckbox);
		advancedNotice = findViewById(R.id.AdvancedNotice);
		rawRuleText = findViewById(R.id.RawRuleText);
		ruleSummary = findViewById(R.id.RuleSummary);
		previewText = findViewById(R.id.PreviewText);

		Button set = findViewById(R.id.RepeatRuleSetButton);
		Button cancel = findViewById(R.id.RepeatRuleCancelButton);
		AcalTheme.setContainerFromTheme(set, AcalTheme.BUTTON);
		AcalTheme.setContainerFromTheme(cancel, AcalTheme.BUTTON);
		set.setOnClickListener(v -> applyRule());
		cancel.setOnClickListener(v -> finish());
	}

	private void buildStaticControls() {
		String[] shortWeekdays = new DateFormatSymbols().getShortWeekdays();
		String[] longWeekdays = new DateFormatSymbols().getWeekdays();
		String[] shortMonths = new DateFormatSymbols().getShortMonths();

		weekdayNames = new String[7];
		for (int wDay = 0; wDay < 7; wDay++) {
			int calDay = calendarDay(wDay);
			weekdayNames[wDay] = longWeekdays[calDay];
			ToggleButton tb = new ToggleButton(this);
			tb.setTextOn(shortWeekdays[calDay]);
			tb.setTextOff(shortWeekdays[calDay]);
			tb.setChecked(false);
			tb.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			tb.setOnCheckedChangeListener((b, c) -> updatePreview());
			weekdayToggles.addView(tb);
		}

		for (int m = 0; m < 12; m++) {
			ToggleButton tb = new ToggleButton(this);
			tb.setTextOn(shortMonths[m]);
			tb.setTextOff(shortMonths[m]);
			tb.setChecked(false);
			GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
					GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
			lp.width = 0;
			tb.setLayoutParams(lp);
			tb.setOnCheckedChangeListener((b, c) -> updatePreview());
			monthToggles.addView(tb);
		}

		ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
				new String[] { getString(R.string.UnitDays), getString(R.string.UnitWeeks),
						getString(R.string.UnitMonths), getString(R.string.UnitYears) });
		freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		freqSpinner.setAdapter(freqAdapter);
		freqSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				onFrequencyChanged();
				updatePreview();
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) { }
		});

		ordinalNames = new String[] {
				"1" + AcalDateTime.getSuffix(1), "2" + AcalDateTime.getSuffix(2),
				"3" + AcalDateTime.getSuffix(3), "4" + AcalDateTime.getSuffix(4),
				"5" + AcalDateTime.getSuffix(5),
				getString(R.string.OrdinalLast), getString(R.string.OrdinalSecondToLast),
				getString(R.string.OrdinalEvery)
		};

		monthlyOnStartDay.setText(String.format(getString(R.string.MonthlyOnStartDay),
				dtStart.getMonthDay() + AcalDateTime.getSuffix(dtStart.getMonthDay())));

		View.OnClickListener monthlyModeListener = v -> {
			if (updating) return;
			setMonthlyMode((RadioButton) v);
			updatePreview();
		};
		monthlyOnStartDay.setOnClickListener(monthlyModeListener);
		monthlyOnDays.setOnClickListener(monthlyModeListener);
		monthlyOnThe.setOnClickListener(monthlyModeListener);

		addBydayButton.setOnClickListener(v -> {
			addBydayRow(monthWeekOrdinalIndex(), dtStart.getWeekDay());
			updatePreview();
		});

		View.OnClickListener endsModeListener = v -> {
			if (updating) return;
			setEndsMode((RadioButton) v);
			updatePreview();
		};
		endsNever.setOnClickListener(endsModeListener);
		endsOnDate.setOnClickListener(endsModeListener);
		endsAfter.setOnClickListener(endsModeListener);

		endsDateButton.setOnClickListener(v -> pickUntilDate());

		TextWatcher previewWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override
			public void afterTextChanged(Editable s) {
				if (!updating) updatePreview();
			}
		};
		intervalText.addTextChangedListener(previewWatcher);
		monthDaysText.addTextChangedListener(previewWatcher);
		endsCountText.addTextChangedListener(previewWatcher);
		rawRuleText.addTextChangedListener(previewWatcher);

		rawCheckbox.setOnCheckedChangeListener((b, checked) -> {
			if (updating) return;
			if (checked) {
				String rule = buildRuleFromControls();
				enterRawMode(rule == null ? "FREQ=WEEKLY" : rule, false);
			}
			else {
				leaveRawMode();
			}
			updatePreview();
		});
	}

	/** Default state: repeat weekly on the start day, no end. */
	private void setDefaults() {
		freqSpinner.setSelection(1);
		intervalText.setText("1");
		((ToggleButton) weekdayToggles.getChildAt(dtStart.getWeekDay())).setChecked(true);
		setMonthlyMode(monthlyOnStartDay);
		setEndsMode(endsNever);
		untilDate = dtStart.clone();
		untilDate.addDays(365);
		showUntilDate();
	}

	/** AcalDateTime weekday (MO=0..SU=6) to java.util.Calendar weekday (SU=1..SA=7). */
	private static int calendarDay(int wDay) {
		return (wDay == 6 ? Calendar.SUNDAY : wDay + 2);
	}

	/** The ordinal spinner index for "the Nth <weekday> of the month" of dtStart. */
	private int monthWeekOrdinalIndex() {
		int week = dtStart.getMonthWeek();
		return (week >= 1 && week <= 5 ? week - 1 : 0);
	}

	private void setMonthlyMode(RadioButton selected) {
		monthlyOnStartDay.setChecked(selected == monthlyOnStartDay);
		monthlyOnDays.setChecked(selected == monthlyOnDays);
		monthlyOnThe.setChecked(selected == monthlyOnThe);
		monthDaysText.setVisibility(selected == monthlyOnDays ? View.VISIBLE : View.GONE);
		bydayRows.setVisibility(selected == monthlyOnThe ? View.VISIBLE : View.GONE);
		addBydayButton.setVisibility(selected == monthlyOnThe ? View.VISIBLE : View.GONE);
		if (selected == monthlyOnThe && bydayRows.getChildCount() == 0) {
			addBydayRow(monthWeekOrdinalIndex(), dtStart.getWeekDay());
		}
		if (selected == monthlyOnDays && monthDaysText.getText().toString().trim().equals("")) {
			monthDaysText.setText(Integer.toString(dtStart.getMonthDay()));
		}
	}

	private void setEndsMode(RadioButton selected) {
		endsNever.setChecked(selected == endsNever);
		endsOnDate.setChecked(selected == endsOnDate);
		endsAfter.setChecked(selected == endsAfter);
	}

	private void onFrequencyChanged() {
		int freq = freqSpinner.getSelectedItemPosition();
		weeklySection.setVisibility(freq == 1 ? View.VISIBLE : View.GONE);
		monthlySection.setVisibility(freq == 2 || freq == 3 ? View.VISIBLE : View.GONE);
		yearlySection.setVisibility(freq == 3 ? View.VISIBLE : View.GONE);
	}

	private void addBydayRow(int ordinalIndex, int weekdayIndex) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);

		Spinner ordinal = new Spinner(this);
		ArrayAdapter<String> ordinalAdapter = new ArrayAdapter<>(this,
				android.R.layout.simple_spinner_item, ordinalNames);
		ordinalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		ordinal.setAdapter(ordinalAdapter);
		ordinal.setSelection(ordinalIndex);

		Spinner weekday = new Spinner(this);
		ArrayAdapter<String> weekdayAdapter = new ArrayAdapter<>(this,
				android.R.layout.simple_spinner_item, weekdayNames);
		weekdayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		weekday.setAdapter(weekdayAdapter);
		weekday.setSelection(weekdayIndex);

		AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (!updating) updatePreview();
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) { }
		};
		ordinal.setOnItemSelectedListener(listener);
		weekday.setOnItemSelectedListener(listener);

		ImageButton remove = new ImageButton(this);
		remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
		remove.setBackgroundResource(0);
		remove.setOnClickListener(v -> {
			bydayRows.removeView(row);
			updatePreview();
		});

		row.addView(ordinal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
		row.addView(weekday, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
		row.addView(remove, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		bydayRows.addView(row);
	}

	private void pickUntilDate() {
		AcalDateTime shown = (untilDate == null ? dtStart : untilDate);
		DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
			untilDate = new AcalDateTime(year, month + 1, day, 0, 0, 0, null);
			showUntilDate();
			setEndsMode(endsOnDate);
			updatePreview();
		}, shown.getYear(), shown.getMonth() - 1, shown.getMonthDay());
		dialog.show();
	}

	private void showUntilDate() {
		if (untilDate == null) return;
		endsDateButton.setText(DateUtils.formatDateTime(this, untilDate.getMillis(),
				DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
	}

	/**
	 * Whether the parsed rule can be shown faithfully in the structured controls.
	 * If not, it is edited as a raw RRULE so nothing gets silently dropped.
	 */
	private boolean isRepresentable(AcalRepeatRuleParser p) {
		if (p.bysecond != null || p.byminute != null || p.byhour != null
				|| p.byyearday != null || p.byweekno != null || p.bysetpos != null) return false;
		if (p.wkst != null && p.wkst.wDay != AcalDateTime.MONDAY) return false;
		if (p.getCount() != -1 && p.getUntil() != null) return false;
		switch (p.getFrequency()) {
			case DAILY:
				return p.byday == null && p.bymonthday == null && p.bymonth == null;
			case WEEKLY:
				if (p.bymonthday != null || p.bymonth != null) return false;
				if (p.byday != null) {
					for (AcalRepeatRuleDay d : p.byday) if (d.setPos != 0) return false;
				}
				return true;
			case MONTHLY:
				if (p.bymonth != null) return false;
				// fall through
			case YEARLY:
				if (p.byday != null && p.bymonthday != null) return false;
				if (p.byday != null) {
					for (AcalRepeatRuleDay d : p.byday) {
						if (ordinalIndexOf(d.setPos) < 0) return false;
					}
				}
				return true;
		}
		return false;
	}

	private static int ordinalIndexOf(int setPos) {
		for (int i = 0; i < ORDINAL_VALUES.length; i++) {
			if (ORDINAL_VALUES[i] == setPos) return i;
		}
		return -1;
	}

	private void populateFromRule(AcalRepeatRuleParser p) {
		switch (p.getFrequency()) {
			case DAILY:		freqSpinner.setSelection(0);	break;
			case WEEKLY:	freqSpinner.setSelection(1);	break;
			case MONTHLY:	freqSpinner.setSelection(2);	break;
			case YEARLY:	freqSpinner.setSelection(3);	break;
		}
		intervalText.setText(Integer.toString(p.interval));

		if (p.getFrequency() == RRuleFreqType.WEEKLY && p.byday != null) {
			for (int i = 0; i < 7; i++) ((ToggleButton) weekdayToggles.getChildAt(i)).setChecked(false);
			for (AcalRepeatRuleDay d : p.byday)
				((ToggleButton) weekdayToggles.getChildAt(d.wDay)).setChecked(true);
		}

		if (p.getFrequency() == RRuleFreqType.YEARLY && p.bymonth != null) {
			for (int m : p.bymonth) {
				if (m >= 1 && m <= 12) ((ToggleButton) monthToggles.getChildAt(m - 1)).setChecked(true);
			}
		}

		if (p.getFrequency() == RRuleFreqType.MONTHLY || p.getFrequency() == RRuleFreqType.YEARLY) {
			if (p.bymonthday != null) {
				StringBuilder days = new StringBuilder();
				for (int d : p.bymonthday) {
					if (days.length() > 0) days.append(",");
					days.append(d);
				}
				monthDaysText.setText(days.toString());
				setMonthlyMode(monthlyOnDays);
			}
			else if (p.byday != null) {
				for (AcalRepeatRuleDay d : p.byday) {
					addBydayRow(ordinalIndexOf(d.setPos), d.wDay);
				}
				setMonthlyMode(monthlyOnThe);
			}
			else {
				setMonthlyMode(monthlyOnStartDay);
			}
		}

		if (p.getCount() != -1) {
			endsCountText.setText(Integer.toString(p.getCount()));
			setEndsMode(endsAfter);
		}
		else if (p.getUntil() != null) {
			untilDate = p.getUntil();
			showUntilDate();
			setEndsMode(endsOnDate);
		}
		else {
			setEndsMode(endsNever);
		}
	}

	private void enterRawMode(String rule, boolean becauseUnrepresentable) {
		boolean wasUpdating = updating;
		updating = true;
		rawCheckbox.setChecked(true);
		rawRuleText.setText(rule);
		rawRuleText.setVisibility(View.VISIBLE);
		advancedNotice.setVisibility(becauseUnrepresentable ? View.VISIBLE : View.GONE);
		setStructuredEnabled(false);
		updating = wasUpdating;
	}

	private void leaveRawMode() {
		String rule = rawRuleText.getText().toString().trim();
		AcalRepeatRuleParser parsed = null;
		try {
			parsed = AcalRepeatRuleParser.parseRepeatRule(rule);
		}
		catch (Exception e) { }
		if (parsed == null || !isRepresentable(parsed)) {
			Toast.makeText(this, getString(R.string.AdvancedRuleNotice), Toast.LENGTH_LONG).show();
			updating = true;
			rawCheckbox.setChecked(true);
			updating = false;
			return;
		}
		updating = true;
		rawRuleText.setVisibility(View.GONE);
		advancedNotice.setVisibility(View.GONE);
		setStructuredEnabled(true);
		clearStructuredControls();
		populateFromRule(parsed);
		updating = false;
		onFrequencyChanged();
	}

	private void clearStructuredControls() {
		for (int i = 0; i < 7; i++) ((ToggleButton) weekdayToggles.getChildAt(i)).setChecked(false);
		for (int i = 0; i < 12; i++) ((ToggleButton) monthToggles.getChildAt(i)).setChecked(false);
		bydayRows.removeAllViews();
		monthDaysText.setText("");
		setMonthlyMode(monthlyOnStartDay);
	}

	private void setStructuredEnabled(boolean enabled) {
		setEnabledRecursive(findViewById(R.id.RepeatRuleForm), enabled);
		// These stay usable regardless of mode.
		rawCheckbox.setEnabled(true);
		rawRuleText.setEnabled(true);
		ruleSummary.setEnabled(true);
		previewText.setEnabled(true);
	}

	private void setEnabledRecursive(View v, boolean enabled) {
		v.setEnabled(enabled);
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) setEnabledRecursive(g.getChildAt(i), enabled);
		}
	}

	/**
	 * Build the RRULE from the structured controls.
	 * @return the rule, or null if the current inputs are not valid.
	 */
	private String buildRuleFromControls() {
		int freq = freqSpinner.getSelectedItemPosition();
		if (freq < 0 || freq > 3) return null;
		StringBuilder rule = new StringBuilder("FREQ=" + RRULE_FREQ[freq]);

		int interval;
		try {
			interval = Integer.parseInt(intervalText.getText().toString().trim());
		}
		catch (NumberFormatException e) {
			interval = 1;
		}
		if (interval < 1) interval = 1;
		if (interval > 1) rule.append(";INTERVAL=").append(interval);

		if (freq == 1) {  // WEEKLY
			StringBuilder days = new StringBuilder();
			for (int i = 0; i < 7; i++) {
				if (((ToggleButton) weekdayToggles.getChildAt(i)).isChecked()) {
					if (days.length() > 0) days.append(",");
					days.append(RRULE_DAYS[i]);
				}
			}
			if (days.length() > 0) rule.append(";BYDAY=").append(days);
		}

		if (freq == 3) {  // YEARLY
			StringBuilder months = new StringBuilder();
			for (int i = 0; i < 12; i++) {
				if (((ToggleButton) monthToggles.getChildAt(i)).isChecked()) {
					if (months.length() > 0) months.append(",");
					months.append(i + 1);
				}
			}
			if (months.length() > 0) rule.append(";BYMONTH=").append(months);
		}

		if (freq == 2 || freq == 3) {  // MONTHLY / YEARLY
			if (monthlyOnDays.isChecked()) {
				String monthDays = parseMonthDays(monthDaysText.getText().toString());
				if (monthDays == null) return null;
				rule.append(";BYMONTHDAY=").append(monthDays);
			}
			else if (monthlyOnThe.isChecked()) {
				StringBuilder days = new StringBuilder();
				for (int i = 0; i < bydayRows.getChildCount(); i++) {
					LinearLayout row = (LinearLayout) bydayRows.getChildAt(i);
					int ordinal = ORDINAL_VALUES[((Spinner) row.getChildAt(0)).getSelectedItemPosition()];
					int weekday = ((Spinner) row.getChildAt(1)).getSelectedItemPosition();
					if (days.length() > 0) days.append(",");
					if (ordinal != 0) days.append(ordinal);
					days.append(RRULE_DAYS[weekday]);
				}
				if (days.length() == 0) return null;
				rule.append(";BYDAY=").append(days);
			}
		}

		if (endsAfter.isChecked()) {
			int count;
			try {
				count = Integer.parseInt(endsCountText.getText().toString().trim());
			}
			catch (NumberFormatException e) {
				return null;
			}
			if (count < 1) return null;
			rule.append(";COUNT=").append(count);
		}
		else if (endsOnDate.isChecked()) {
			if (untilDate == null) return null;
			rule.append(";UNTIL=").append(String.format("%04d%02d%02dT235959Z",
					(int) untilDate.getYear(), (int) untilDate.getMonth(), (int) untilDate.getMonthDay()));
		}

		return rule.toString();
	}

	/** Validate a comma-separated list of month days (1..31 or -31..-1). */
	private String parseMonthDays(String text) {
		String[] parts = text.split("[,\\s]+");
		StringBuilder result = new StringBuilder();
		for (String part : parts) {
			if (part.equals("")) continue;
			int day;
			try {
				day = Integer.parseInt(part);
			}
			catch (NumberFormatException e) {
				return null;
			}
			if (day == 0 || day > 31 || day < -31) return null;
			if (result.length() > 0) result.append(",");
			result.append(day);
		}
		return (result.length() == 0 ? null : result.toString());
	}

	private String currentRule() {
		if (rawCheckbox.isChecked()) {
			String raw = rawRuleText.getText().toString().trim();
			return (raw.equals("") ? null : raw);
		}
		return buildRuleFromControls();
	}

	private void updatePreview() {
		String rule = currentRule();
		if (rule == null) {
			ruleSummary.setText(getString(R.string.RepeatRuleInvalid));
			previewText.setText("");
			return;
		}
		try {
			AcalRepeatRuleParser parsed = AcalRepeatRuleParser.parseRepeatRule(rule);
			ruleSummary.setText(parsed.toPrettyString(this));

			AcalRepeatRule repeats = new AcalRepeatRule(dtStart.clone(), rule);
			List<String> occurrences = new ArrayList<>();
			while (occurrences.size() < PREVIEW_INSTANCES && repeats.hasNext()) {
				AcalDateTime instance = repeats.next();
				if (instance == null) break;
				occurrences.add(DateUtils.formatDateTime(this, instance.getMillis(),
						DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
								| DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL));
			}
			if (occurrences.isEmpty()) {
				previewText.setText(getString(R.string.NoOccurrences));
			}
			else {
				StringBuilder s = new StringBuilder(getString(R.string.NextOccurrences));
				for (String occurrence : occurrences) s.append("\n  ").append(occurrence);
				if (repeats.hasNext()) s.append("\n  …");
				previewText.setText(s.toString());
			}
		}
		catch (Exception e) {
			ruleSummary.setText(getString(R.string.RepeatRuleInvalid));
			previewText.setText("");
		}
	}

	private void applyRule() {
		String rule = currentRule();
		if (rule != null) {
			try {
				AcalRepeatRuleParser.parseRepeatRule(rule);
			}
			catch (Exception e) {
				rule = null;
			}
		}
		if (rule == null) {
			Toast.makeText(this, getString(R.string.RepeatRuleInvalid), Toast.LENGTH_LONG).show();
			return;
		}
		Intent result = new Intent();
		result.putExtra(EXTRA_RRULE, rule);
		setResult(RESULT_OK, result);
		finish();
	}
}
