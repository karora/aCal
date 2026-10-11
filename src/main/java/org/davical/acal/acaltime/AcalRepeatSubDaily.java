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

package org.davical.acal.acaltime;

import java.util.List;

import android.content.Context;

import org.davical.acal.R;
import org.davical.acal.acaltime.AcalRepeatRule.RRuleFreqType;

/**
 * The frequencies shorter than a day: HOURLY, MINUTELY and SECONDLY.  They differ only
 * in the length of the step, and in which of BYHOUR, BYMINUTE and BYSECOND add
 * instances and which only choose among them.
 */

public class AcalRepeatSubDaily extends AcalRepeatRuleParser {

	/** No more steps than this are taken looking for the next instance. */
	private static final int	MOST_EMPTY_SETS	= 100000;

	private final int	secondsInStep;

	protected AcalRepeatSubDaily(String rRuleValue, RRuleFreqType f) {
		super(rRuleValue,f);
		switch ( f ) {
			case HOURLY:	secondsInStep = AcalDateTime.SECONDS_IN_HOUR;	break;
			case MINUTELY:	secondsInStep = AcalDateTime.SECONDS_IN_MINUTE;	break;
			case SECONDLY:	secondsInStep = 1;								break;
			default:
				throw new IllegalArgumentException(f + " is not a frequency shorter than a day.");
		}
	}


	@Override
	public String getFrequencyName() {
		return frequency.toString();
	}

	@Override
	public String getPrettyFrequencyName(Context cx) {
		int one, several;
		switch ( frequency ) {
			case HOURLY:	one = R.string.EveryHour;	several = R.string.EveryNHours;		break;
			case MINUTELY:	one = R.string.EveryMinute;	several = R.string.EveryNMinutes;	break;
			default:		one = R.string.EverySecond;	several = R.string.EveryNSeconds;	break;
		}
		if ( interval != 1 ) return String.format(cx.getString(several), Integer.toString(interval));
		return cx.getString(one);
	}

	/** The steps are of time that passes, so one across a change of daylight saving is no longer or shorter. */
	@Override
	protected void nextFrequency() {
		currentBase.addSeconds((long) interval * secondsInStep);
	}

	/**
	 * A rule such as "every hour, on Mondays" has six days of steps with nothing in them
	 * between one instance and the next, so many more empty sets are allowed for than at
	 * the longer frequencies: a year of them, up to a limit on the work done.
	 */
	@Override
	protected int mostEmptySets() {
		long stepsInAYear = (366L * AcalDateTime.SECONDS_IN_DAY) / ((long) interval * secondsInStep);
		return (int) Math.min(MOST_EMPTY_SETS, stepsInAYear + 1);
	}


	@Override
	public List<AcalDateTime> buildSet() {
		startNewSet();

		if ( bymonth    != null ) limitByMonth();
		if ( byyearday  != null ) limitBy(AcalDateTime.DAY_OF_YEAR, byyearday);
		if ( bymonthday != null ) limitByMonthDay();
		if ( byday      != null ) limitByDay();

		if ( byhour     != null ) limitBy(AcalDateTime.HOUR, byhour);

		if ( byminute   != null ) {
			if ( frequency == RRuleFreqType.HOURLY ) expandByMinute();
			else limitBy(AcalDateTime.MINUTE, byminute);
		}
		if ( bysecond   != null ) {
			if ( frequency == RRuleFreqType.SECONDLY ) limitBy(AcalDateTime.SECOND, bysecond);
			else expandBySecond();
		}

		if ( bysetpos   != null ) limitBySetPos();

		return currentSet;
	}

}
