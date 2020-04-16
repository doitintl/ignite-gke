package com.doitIntl.igniteTest;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

public class IgniteTestClientOptions extends OptionsBase {

    @Option(
            name = "help",
            abbrev = 'h',
            help = "Prints usage info.",
            defaultValue = "false"
    )
    public boolean help;

    @Option(
            name = "host",
            abbrev = 'o',
            help = "The Ignite server host.",
            category = "server settings",
            defaultValue = ""
    )
    public String host;

    @Option(
            name = "port",
            abbrev = 'p',
            help = "The server port.",
            category = "server settings",
            defaultValue = "10800"
    )
    public int port;

    @Option(
            name = "lowerbound",
            abbrev = 'l',
            help = "The lower bound of the integer key range.",
            category = "test setup",
            defaultValue = "1"
    )
    public int lowerBound;

    @Option(
            name = "upperbound",
            abbrev = 'u',
            help = "The upper bound of the integer key range. Must be set for random key selection. If less than lower bound or left blank, upper bound will be lower bound+count-1 and each key will be selected in that range will be selected exactly once.",
            category = "test setup",
            defaultValue = "-1"
    )
    public int upperBound;

    @Option(
            name = "count",
            abbrev = 'c',
            help = "The count of objects to be processed.",
            category = "test setup",
            defaultValue = "100"
    )
    public int count;

    @Option(
            name = "name",
            abbrev = 'n',
            help = "The name of the cache.",
            category = "server settings",
            defaultValue = "test-cache"
    )
    public String name;

    @Option(
            name = "sockets",
            abbrev = 's',
            help = "Parallel socket count.",
            category = "test setup",
            defaultValue = "3"
    )
    public int sockets;

    @Option(
            name = "get",
            help = "Perform GET requests.",
            defaultValue = "false"
    )
    public boolean get;

    @Option(
            name = "put",
            help = "Perform PUT requests.",
            defaultValue = "false"
    )
    public boolean put;
}
