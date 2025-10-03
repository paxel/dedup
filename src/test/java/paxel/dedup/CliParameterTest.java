package paxel.dedup;

import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import paxel.lib.Result;

import java.text.ParseException;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

@Parameters(parametersValidators = {RepoParameterValidation.class})
class CliParameterTest {

    @Test
    void parseHelp() {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(new String[]{"-h"});
        assertThat(parse.isSuccess()).isTrue();
        assertThat(parse.value()).isNull();
    }

    @Test
    void parseVerbose() {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(new String[]{"-v"});
        assertThat(parse.isSuccess()).isTrue();
        assertThat(parse.value()).isNotNull()
                .extracting(CliParameter::isVerbose, as(InstanceOfAssertFactories.BOOLEAN))
                .isTrue();
    }

    @Test
    void parseRepos() {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(new String[]{"-R", "one", "-R", "two"});
        assertThat(parse.isSuccess()).isTrue();
        assertThat(parse.value()).isNotNull()
                .extracting(CliParameter::getRepos, as(InstanceOfAssertFactories.LIST))
                .contains("one", "two");
    }

    @Test
    void parseRepo() {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(new String[]{"-R", "one,two"});
        assertThat(parse.isSuccess()).isTrue();
        assertThat(parse.value()).isNotNull()
                .extracting(CliParameter::getRepos, as(InstanceOfAssertFactories.LIST))
                .contains("one", "two");
    }


    @Test
    void failRepoAndAll() {
        Result<CliParameter, ParameterException> parse = CliParameter.parse(new String[]{"-a", "-R", "two"});
        assertThat(parse.hasFailed()).isTrue();
        assertThat(parse.error()).isNotNull()
                .extracting(Exception::getMessage, as(STRING))
                .isEqualTo("Either use -a/--all or -R bt not both");
    }


}