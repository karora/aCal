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

	/**
	 * No more steps than this are taken looking for the next instance.  Steps which
	 * cannot hold one are passed over many at a time (see nextFrequency()), so it takes
	 * about 1500 to get from one 29 February to the next, which is as far as a rule that
	 * makes any sense has to go.
	 */
	private static final int	MOST_EMPTY_SETS	= 5000;

	private final int	secondsInStep;

	/** The base date as a floating time, which is what the steps are made on. */
	private AcalDateTime	clock = null;

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

	/**
	 * The steps are made on the clock where the event is, as RFC 5545 has it, and not in
	 * time that has passed: every six hours from 3am is still at 3am after the clocks have
	 * changed.  So they are worked out on a floating time, which is then read in the zone
	 * of the event as DTSTART would be.
	 *
	 * A rule comes from whoever wrote the calendar, and "every second, but only at
	 * midnight" would be 86400 steps for each instance if each were looked at.  When the
	 * step arrived at is on a day the rule leaves out, the rest of that day is passed
	 * over at once, and so is the rest of an hour or a minute it leaves out.
	 */
	@Override
	protected void nextFrequency() {
		if ( clock == null ) clock = originalBase.clone().setTimeZone(null);
		clock.addSeconds(stepLength());
		if ( dayIsLeftOut(clock) )
			skipRestOf(AcalDateTime.SECONDS_IN_DAY);
		else if ( frequency != RRuleFreqType.HOURLY && byhour != null && !contains(byhour, clock.getHour()) )
			skipRestOf(AcalDateTime.SECONDS_IN_HOUR);
		else if ( frequency == RRuleFreqType.SECONDLY && byminute != null && !contains(byminute, clock.getMinute()) )
			skipRestOf(AcalDateTime.SECONDS_IN_MINUTE);
		currentBase = clock.clone().setTimeZone(originalBase.getTimeZoneId());
	}

	@Override
	public void resetBaseDate() {
		super.resetBaseDate();
		clock = null;
	}

	private long stepLength() {
		return (long) interval * secondsInStep;
	}

	/** Whether the parts of the rule that choose days leave out the day this is on, as buildSet() will find. */
	private boolean dayIsLeftOut( AcalDateTime c ) {
		if ( bymonth    != null && !contains(bymonth, c.get(AcalDateTime.MONTH)) ) return true;
		if ( byyearday  != null && !contains(byyearday, c.get(AcalDateTime.DAY_OF_YEAR)) ) return true;
		if ( bymonthday != null && !contains(bymonthday, c.get(AcalDateTime.DAY_OF_MONTH)) ) return true;
		if ( byday != null ) {
			int weekDay = c.get(AcalDateTime.DAY_OF_WEEK);
			for( AcalRepeatRuleDay day : byday ) {
				if ( day.wDay == weekDay ) return false;
			}
			return true;
		}
		return false;
	}

	private static boolean contains( int[] values, int wanted ) {
		for( int value : values ) {
			if ( value == wanted ) return true;
		}
		return false;
	}

	/**
	 * Moves the clock on to the last step of the day, hour or minute it is in, which is
	 * given as its length in seconds, so that the step after is the first of the next.
	 */
	private void skipRestOf( int unit ) {
		long steps = (unit - (clock.getDaySecond() % unit)) / stepLength();
		if ( steps > 0 ) clock.addSeconds(steps * stepLength());
	}

	/**
	 * A rule such as "every hour, on 29 February" has years of steps with nothing in them
	 * between one instance and the next, so more empty sets are allowed for than at the
	 * longer frequencies.
	 */
	@Override
	protected int mostEmptySets() {
		return MOST_EMPTY_SETS;
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
