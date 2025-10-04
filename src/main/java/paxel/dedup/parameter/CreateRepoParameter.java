package paxel.dedup.parameter;

import lombok.Data;
import picocli.CommandLine.*;

@Data
public class CreateRepoParameter {


    @Parameters(index = "1", description = "The absolute path to the repo")
    private String path;

    @Parameters(index = "0", description = "name of the repo")
    private String name;

    @Option(names = {"--indices"}, description = "The number of index files of the repo")
    private int indices = 10;
}
