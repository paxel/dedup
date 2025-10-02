package paxel.dedup;

import com.beust.jcommander.IParametersValidator;
import com.beust.jcommander.ParameterException;

import java.util.Map;

public class RepoParameterValidation implements IParametersValidator {


    @Override
    public void validate(Map<String, Object> map) throws ParameterException {
        boolean all = map.containsKey("-a");
        boolean repo = map.containsKey("-R");
        if (all && repo)
            throw new ParameterException("Either use -a or -R bt not both");
    }
}
