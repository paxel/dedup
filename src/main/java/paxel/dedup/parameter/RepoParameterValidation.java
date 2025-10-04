package paxel.dedup.parameter;

import com.beust.jcommander.IParametersValidator;
import com.beust.jcommander.ParameterException;

import java.util.Map;

public class RepoParameterValidation implements IParametersValidator {


    @Override
    public void validate(Map<String, Object> map) throws ParameterException {
        boolean all = map.get("-a") != null || map.get("--all") != null;
        boolean repo = map.get("-R") != null;
        if (all && repo)
            throw new ParameterException("Either use -a/--all or -R bt not both");
    }
}
