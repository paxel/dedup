package io.github.paxel.ingress;

import com.beust.jcommander.JCommander;

public class Ingressor {
    public static void main(String[] args) {

        Ingressor ingress = new Ingressor();
        final IngressConfig cfg = new IngressConfig();
        final JCommander.Builder addObject = JCommander.newBuilder()
                .addObject(cfg);
        final JCommander build = addObject.build();
        build.parse(args);


        if (cfg.isHelp()) {
            build.usage();
            System.exit(2);
        }

    }
}
