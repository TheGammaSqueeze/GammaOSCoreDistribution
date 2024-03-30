# Sample Filter

Sometimes we want to report samples only for selected processes, threads, libraries, or time
ranges. To filter samples, we can pass filter options to the report commands or scripts.


## filter file format

To filter samples based on time ranges, simpleperf accepts a filter file when reporting. The filter
file is in text format, containing a list of lines. Each line is a filter command.

```
filter_command1 command_args
filter_command2 command_args
...
```

### clock command

```
CLOCK <clock_name>
```

Set the clock used to generate timestamps in the filter file. Supported clocks are: `monotonic`,
`realtime`. By default it is monotonic. The clock here should be the same as the clock used in
profile data, which is set by `--clockid` in simpleperf record command.

### global time filter commands

```
GLOBAL_BEGIN <begin_timestamp>
GLOBAL_END <end_timestamp>
```

The nearest pair of GLOBAL_BEGIN and GLOBAL_END commands makes a time range. When these commands
are used, only samples in the time ranges are reported. Timestamps are 64-bit integers in
nanoseconds.

```
GLOBAL_BEGIN 1000
GLOBAL_END 2000
GLOBAL_BEGIN 3000
GLOBAL_BEGIN 4000
```

For the example above, samples in time ranges [1000, 2000) and [3000, 4000) are reported.

### process time filter commands

```
PROCESS_BEGIN <pid> <begin_timestamp>
PROCESS_END <pid> <end_timestamp>
```

The nearest pair of PROCESS_BEGIN and PROCESS_END commands for the same process makes a time
range. When these commands are used, each process has a list of time ranges, and only samples
in the time ranges are reported.

```
PROCESS_BEGIN 1 1000
PROCESS_BEGIN 2 2000
PROCESS_END 1 3000
PROCESS_END 2 4000
```

For the example above, process 1 samples in time range [1000, 3000) and process 2 samples in time
range [2000, 4000) are reported.

### thread time filter commands

```
THREAD_BEGIN <tid> <begin_timestamp>
THREAD_END <tid> <end_timestamp>
```

The nearest pair of THREAD_BEGIN and THREAD_END commands for the same thread makes a time
range. When these commands are used, each thread has a list of time ranges, and only samples in the
time ranges are reported.

```
THREAD_BEGIN 1 1000
THREAD_BEGIN 2 2000
THREAD_END 1 3000
THREAD_END 2 4000
```

For the example above, thread 1 samples in time range [1000, 3000) and thread 2 samples in time
range [2000, 4000) are reported.
