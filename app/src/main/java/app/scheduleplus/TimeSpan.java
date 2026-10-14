/*
 * Adapted from android-week-view model/TimeSpan.kt.
 * Copyright (c) 2015-2020 Tobias Schürg
 * MIT License; full text in research/android-week-view-LICENSE.txt.
 * Source commit 7c2dd7bfcdf99b587b3b5f7647dc84b0587e2597.
 * Changes: Java port; only the validated interval and duration factory retained.
 */
package app.scheduleplus;

import java.time.Duration;
import java.time.LocalTime;

public final class TimeSpan {
    public final LocalTime start, endExclusive;
    public TimeSpan(LocalTime start, LocalTime endExclusive) {
        if(!start.isBefore(endExclusive)) throw new IllegalArgumentException("每节课的结束时间必须晚于开始时间");
        this.start=start;this.endExclusive=endExclusive;
    }
    public Duration duration(){return Duration.between(start,endExclusive);}
    public static TimeSpan of(LocalTime start,Duration duration){
        if(duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("课程时长必须大于 0");
        if(duration.compareTo(Duration.ofDays(1))>=0) throw new IllegalArgumentException("课程时间不能跨越午夜");
        LocalTime end=start.plus(duration);
        if(!end.isAfter(start)) throw new IllegalArgumentException("课程时间不能跨越午夜");
        return new TimeSpan(start,end);
    }
}
