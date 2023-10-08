package io.github.paxel.ingress;

import com.beust.jcommander.Parameter;

/**
 *
 */
public class IngressConfig {

    @Parameter()
    private String source;
    @Parameter()
    private String target;

    @Parameter(help = true)
    private boolean help;

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public boolean isHelp() {
        return help;
    }
}
