package io.terrakube.api.plugin.state.model.state;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import io.terrakube.api.plugin.state.model.generic.Resource;

@Getter
@Setter
@ToString
public class RunData {
    Resource data;
}