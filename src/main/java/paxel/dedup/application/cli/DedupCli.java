package paxel.dedup.application.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import paxel.dedup.application.cli.parameter.CliParameter;
import paxel.dedup.application.cli.parameter.DiffCommand;
import paxel.dedup.application.cli.parameter.FilesCommand;
import paxel.dedup.application.cli.parameter.RepoCommand;
import paxel.dedup.infrastructure.adapter.in.web.UiServer;
import paxel.dedup.infrastructure.config.InfrastructureConfig;
import picocli.CommandLine;

import java.util.Iterator;


@Slf4j
public class DedupCli {
    public static void main(String[] args) {

        InfrastructureConfig infrastructureConfig = new InfrastructureConfig();
        CliParameter cliParameter = new CliParameter();
        infrastructureConfig.setCliParameter(cliParameter);

        CommandLine commandLine = new CommandLine(cliParameter)
                .addSubcommand(new DiffCommand(infrastructureConfig))
                .addSubcommand(new FilesCommand(infrastructureConfig))
                .addSubcommand(new RepoCommand(infrastructureConfig));

        CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
        if (parseResult.isUsageHelpRequested()) {
            commandLine.usage(System.out);
            System.exit(0);
        } else if (parseResult.isVersionHelpRequested()) {
            commandLine.printVersionHelp(System.out);
            System.exit(0);
        }

        if (cliParameter.isVerbose()) {
            Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            root.setLevel(Level.DEBUG);
            // Keep noisy third-party loggers at INFO to avoid unreadable debug spam
            ((Logger) LoggerFactory.getLogger("org.eclipse.jetty")).setLevel(Level.INFO);
            switchToVerbosePattern(root);
        }

        if (cliParameter.isUi()) {
            new UiServer(infrastructureConfig).start(8080);
            return;
        }

        if (parseResult.subcommand() == null) {
            commandLine.usage(System.out);
            System.exit(0);
        }

        int exitCode = commandLine.getExecutionStrategy().execute(parseResult);
        System.exit(exitCode);
    }

    private static void switchToVerbosePattern(Logger root) {
        LoggerContext context = root.getLoggerContext();
        String verbosePattern = "%date{HH:mm:ss.SSS} %-5level [%logger{36}] %msg%n";

        Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> it = root.iteratorForAppenders();
        while (it.hasNext()) {
            Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender = it.next();
            if (appender instanceof OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> osa) {
                PatternLayoutEncoder encoder = new PatternLayoutEncoder();
                encoder.setContext(context);
                encoder.setPattern(verbosePattern);
                encoder.start();
                osa.setEncoder(encoder);
            }
        }
    }
}
