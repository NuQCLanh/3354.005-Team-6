# Spike S-01: Automated menu import from Dine on Campus

**Team 6, CS 3354.005, P2 section 8.** Throw-away prototype, not product code.

## Question
Can CometEats automatically read a full day's UTD menu (location, meal period, station,
dish, dietary tags) from Dine on Campus, with at least 95% of requests succeeding,
without anyone entering data by hand?

## Why this spike
The whole product depends on it: assumption A1, risk R3, open question Q1, and
requirements FR-01, FR-02 and FR-04.

## How to run
Needs Java 11 or newer (we ran it on Java 26). No libraries, no API keys, no passwords.

```
cd spikes/S-01
java MenuImportSpike.java          # 7 days, every UTD location
```

If the program can't find the UTD locations by itself, it prints how to copy a
location id from the browser. Then run `java MenuImportSpike.java 7 <id>`.

## What it measures
- every request's HTTP status, time (ms) and size, logged to `results.log`
- success rate, median and 95th-percentile response time
- dishes per menu, % of dishes with at least one dietary tag, list of distinct tag values
- how long a full one-day import takes (compare with the 6-hour import window in FR-02)

## Results
See `RESULTS.md` (written by the program) and the raw log in `results.log`.
